"""Quiesced two-database backups and isolated restore drills for smartticket-cluster.

Archives remain private under .runtime/backups. Verification never restores into
the source container and checks every public table, sequence and database ACL.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import secrets
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "smartticket-cluster"
COMPOSE = ["docker", "compose", "-p", "smartticket-cluster", "--env-file",
           str(ROOT / ".runtime/distributed.env"), "-f", str(ROOT / "compose.distributed.yaml")]
DATABASES = {"intelligent_ticket": "core", "smartticket_knowledge": "knowledge"}
IMAGE = "pgvector/pgvector:pg18"


def run(args, check=True, timeout=180):
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=timeout)
    if check and result.returncode:
        raise RuntimeError("Database operation failed (private details withheld): " + args[0])
    return result


def sql(container, database, query, user="cluster_admin", check=True):
    return run(["docker", "exec", container, "psql", "-X", "-v", "ON_ERROR_STOP=1",
                "-U", user, "-d", database, "-qAtc", query], check=check)


def quote(identifier):
    return '"' + identifier.replace('"', '""') + '"'


def snapshot(container, database, user="cluster_admin"):
    names = json.loads(sql(container, database,
        "SELECT COALESCE(json_agg(tablename ORDER BY tablename),'[]') "
        "FROM pg_tables WHERE schemaname='public'", user).stdout)
    tables = {}
    for name in names:
        statement = ("SET TIME ZONE 'UTC'; SELECT json_build_object('rows',count(*),"
                     "'contentMd5',md5(COALESCE(string_agg(md5(to_jsonb(item)::text),'' "
                     "ORDER BY md5(to_jsonb(item)::text)),''))) FROM public." + quote(name) + " AS item")
        tables[name] = json.loads(sql(container, database, statement, user).stdout)
    sequences = json.loads(sql(container, database,
        "SELECT COALESCE(json_agg(json_build_object('name',sequencename,'lastValue',last_value,"
        "'increment',increment_by,'start',start_value,'min',min_value,'max',max_value,'cycle',cycle) "
        "ORDER BY sequencename),'[]') FROM pg_sequences WHERE schemaname='public'", user).stdout)
    extensions = json.loads(sql(container, database,
        "SELECT json_agg(extname ORDER BY extname) FROM pg_extension", user).stdout)
    return {"tables": tables, "sequences": sequences, "extensions": extensions}


def wait_container(container, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        state = json.loads(run(["docker", "inspect", "--format", "{{json .State}}", container]).stdout)
        if state.get("Running") and state.get("Health", {}).get("Status") == "healthy":
            return
        time.sleep(2)
    raise RuntimeError("The original application instance did not recover: " + container[:12])


def validate_bundle(folder):
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("format") != 1 or set(manifest.get("databases", {})) != set(DATABASES):
        raise ValueError("Backup must contain both recognized service databases")
    for database in DATABASES:
        archive = folder / (database + ".dump")
        record = manifest["databases"][database]
        if hashlib.sha256(archive.read_bytes()).hexdigest() != record["sha256"]:
            raise ValueError("Backup checksum mismatch: " + database)
        if set(record["snapshot"]["extensions"]) - {"plpgsql", "vector", "hstore", "uuid-ossp"}:
            raise ValueError("This project restore tool does not recognize all backed-up extensions")
    return manifest


def backup():
    run_id = uuid.uuid4().hex
    source_ids = run(COMPOSE + ["ps", "-q", "postgres"]).stdout.splitlines()
    if len(source_ids) != 1:
        raise RuntimeError("Expected exactly one isolated source PostgreSQL container")
    source = source_ids[0]
    labels = json.loads(run(["docker", "inspect", "--format", "{{json .Config.Labels}}", source]).stdout)
    if labels.get("com.docker.compose.project") != PROJECT:
        raise RuntimeError("Source container does not belong to the isolated project")
    writers = run(COMPOSE + ["ps", "-q", "backend", "knowledge"]).stdout.splitlines()
    if not writers:
        raise RuntimeError("Start the application before a coordinated backup")
    destination = ROOT / ".runtime/backups" / ("formal" if PROJECT == "smartticket-formal" else "distributed") / (
        datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + run_id[:8])
    destination.mkdir(parents=True)
    temporary = [f"/tmp/smartticket-{run_id}-{database}.dump" for database in DATABASES]
    manifest = {"format": 1, "createdAt": datetime.now(timezone.utc).isoformat(),
                "sourceProject": PROJECT, "image": IMAGE,
                "consistency": "All running core and knowledge application writers stopped before both dumps",
                "databases": {}}
    try:
        print("Stopping only the isolated core and knowledge writers for a coordinated backup", flush=True)
        run(["docker", "stop", *writers], timeout=180)
        for (database, role), temp in zip(DATABASES.items(), temporary):
            before = snapshot(source, database)
            run(["docker", "exec", source, "pg_dump", "-U", "cluster_admin", "-d", database,
                 "-Fc", "--no-owner", "--no-acl", "--exclude-extension=vector", "-f", temp])
            run(["docker", "exec", source, "pg_restore", "--list", temp])
            archive = destination / (database + ".dump")
            run(["docker", "cp", source + ":" + temp, str(archive)])
            if snapshot(source, database) != before:
                raise AssertionError("Source changed while taking a coordinated backup")
            manifest["databases"][database] = {"role": role, "snapshot": before,
                "bytes": archive.stat().st_size, "sha256": hashlib.sha256(archive.read_bytes()).hexdigest()}
        (destination / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        validate_bundle(destination)
    finally:
        try:
            run(["docker", "exec", source, "rm", "-f", *temporary], check=False)
        finally:
            # Restart every previously running writer even if a different restart fails.
            failures = []
            for writer in writers:
                if run(["docker", "start", writer], check=False).returncode:
                    failures.append(writer[:12])
            if failures:
                raise RuntimeError("Original writer restart failed: " + ",".join(failures))
            for writer in writers:
                wait_container(writer)
            print("PASS: original application writers restored", flush=True)
    print("PASS: private two-database backup: " + str(destination), flush=True)
    return destination


def verify(folder, output):
    folder = folder.resolve()
    manifest = validate_bundle(folder)  # Reject a corrupt bundle before creating any container.
    run_id = uuid.uuid4().hex
    container = "smartticket-dual-restore-" + run_id[:12]
    started = time.perf_counter()
    checks = []
    created = False
    try:
        run(["docker", "run", "-d", "--rm", "--pull=never", "--name", container,
             "--network", "none", "--label", "smartticket.verification=distributed-restore",
             "--label", "smartticket.restore-run=" + run_id,
             "--tmpfs", "/var/lib/postgresql:rw,size=512m",
             "-e", "POSTGRES_USER=restore_admin", "-e", "POSTGRES_DB=postgres",
             "-e", "POSTGRES_PASSWORD=" + secrets.token_hex(24), IMAGE])
        created = True
        deadline = time.monotonic() + 90
        while run(["docker", "exec", container, "pg_isready", "-U", "restore_admin", "-d", "postgres"], check=False).returncode:
            if time.monotonic() >= deadline:
                raise RuntimeError("Isolated restore database did not start")
            time.sleep(1)
        for database, role in DATABASES.items():
            sql(container, "postgres", f"CREATE ROLE {role} LOGIN", "restore_admin")
            sql(container, "postgres", f"CREATE DATABASE {database} OWNER {role}", "restore_admin")
            sql(container, database, f"REVOKE CONNECT ON DATABASE {database} FROM PUBLIC; "
                f"GRANT CONNECT ON DATABASE {database} TO {role}", "restore_admin")
            if "vector" in manifest["databases"][database]["snapshot"]["extensions"]:
                sql(container, database, "CREATE EXTENSION IF NOT EXISTS vector", "restore_admin")
            run(["docker", "cp", str(folder / (database + ".dump")), container + ":/tmp/restore.dump"])
            run(["docker", "exec", container, "pg_restore", "-U", "restore_admin", "--role", role,
                 "-d", database, "--no-owner", "--no-acl", "--exit-on-error", "/tmp/restore.dump"])
            actual = snapshot(container, database, "restore_admin")
            if actual != manifest["databases"][database]["snapshot"]:
                raise AssertionError("Restored table contents or sequence state differ: " + database)
            # Verify the application role can read its restored objects.
            for name in actual["tables"]:
                sql(container, database, "SELECT 1 FROM public." + quote(name) + " LIMIT 1", role)
            checks.append({"database": database, "tables": len(actual["tables"]),
                           "rows": sum(value["rows"] for value in actual["tables"].values()),
                           "tableContentsAndSequencesMatch": True, "ownerRoleCanRead": True})
            print("PASS: restored contents, sequences and application permissions: " + database, flush=True)
        for role, forbidden in [("core", "smartticket_knowledge"), ("knowledge", "intelligent_ticket")]:
            if sql(container, forbidden, "SELECT 1", role, check=False).returncode == 0:
                raise AssertionError("Restored database boundaries were not enforced")
        report = {"result": "PASS", "createdAt": datetime.now(timezone.utc).isoformat(),
                  "method": "Quiesced two-database pg_dump + isolated pg_restore with application roles",
                  "network": "none", "sourceDatabasesModified": False,
                  "checks": checks, "crossDatabaseConnectionsDenied": True,
                  "restoreSeconds": round(time.perf_counter() - started, 2),
                  "scope": "Logical restore drill on one local Docker host; not PostgreSQL failover or a measured production RTO/RPO"}
    finally:
        if created:
            labels = json.loads(run(["docker", "inspect", "--format", "{{json .Config.Labels}}", container]).stdout)
            if labels.get("smartticket.restore-run") != run_id:
                raise RuntimeError("Refusing to stop a container not created by this restore drill")
            run(["docker", "stop", container])
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("PASS: both service databases recovered and the temporary environment removed", flush=True)


def main():
    global PROJECT, COMPOSE
    parser = argparse.ArgumentParser()
    parser.add_argument("--formal", action="store_true", help="Back up the independent formal project")
    sub = parser.add_subparsers(dest="action", required=True)
    create = sub.add_parser("backup")
    create.add_argument("--verify", action="store_true")
    create.add_argument("--output")
    restore = sub.add_parser("verify")
    restore.add_argument("--backup", required=True, type=Path)
    restore.add_argument("--output")
    args = parser.parse_args()
    if args.formal:
        PROJECT = "smartticket-formal"
        COMPOSE = ["docker", "compose", "-p", PROJECT, "--env-file", str(ROOT / ".runtime/formal.env"),
                   "-f", str(ROOT / "compose.distributed.yaml"), "-f", str(ROOT / "compose.formal.yaml")]
    output = ROOT / (args.output or ("docs/formal-restore-verification.json" if args.formal
                                    else "docs/distributed-restore-verification.json"))
    if args.action == "backup":
        folder = backup()
        if args.verify:
            verify(folder, output)
    else:
        verify(args.backup, output)


if __name__ == "__main__":
    main()
