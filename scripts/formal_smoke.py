"""Verify the local formal HTTPS deployment with real certificates and business data."""
import argparse
from datetime import datetime, timezone
import http.client
import json
from pathlib import Path
import secrets
import socket
import ssl
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ["docker", "compose", "-p", "smartticket-formal", "--env-file",
           str(ROOT / ".runtime/formal.env"), "-f", str(ROOT / "compose.distributed.yaml"),
           "-f", str(ROOT / "compose.formal.yaml")]
CHECKS = []


def command(args):
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=60)
    if result.returncode:
        raise RuntimeError("Formal deployment inspection failed; credentials were not logged")
    return result.stdout.strip()


def mark(name, passed=True):
    if not passed:
        raise AssertionError(name)
    CHECKS.append(name)
    print("PASS: " + name, flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-ai", action="store_true")
    parser.add_argument("--output", default="docs/formal-verification.json")
    args = parser.parse_args()
    private = dict(line.split("=", 1) for line in (ROOT / ".runtime/formal.env").read_text(
        encoding="utf-8-sig").splitlines() if "=" in line and not line.startswith("#"))
    context = ssl.create_default_context(cafile=str(ROOT / ".runtime/formal/tls/ca.crt"))
    base = "https://localhost:8443"

    def call(path, method="GET", body=None, token=None, expected=200):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(base + "/api" + path, method=method, headers=headers,
            data=None if body is None else json.dumps(body).encode())
        try:
            with urllib.request.urlopen(request, context=context, timeout=180) as response:
                status, payload = response.status, json.load(response)
        except urllib.error.HTTPError as error:
            status, payload = error.code, json.load(error)
        if status != expected:
            raise AssertionError(f"Unexpected response for {method} {path}: {status}, expected {expected}")
        return payload.get("data", payload)

    deadline = time.monotonic() + 240
    while True:
        try:
            if call("/actuator/health/readiness").get("status") == "UP":
                break
        except (OSError, ValueError, AssertionError):
            pass
        if time.monotonic() >= deadline:
            raise AssertionError("Formal HTTPS readiness did not recover")
        time.sleep(3)
    mark("HTTPS readiness verified with the local CA and hostname validation")
    for host in ("localhost", "127.0.0.1"):
        with socket.create_connection((host, 8443), timeout=10) as transport:
            with context.wrap_socket(transport, server_hostname=host) as connection:
                mark("Certificate SAN and TLS validated for " + host,
                     connection.version() in ("TLSv1.2", "TLSv1.3"))
    redirect = http.client.HTTPConnection("127.0.0.1", 8089, timeout=10)
    try:
        redirect.request("GET", "/login")
        response = redirect.getresponse()
        mark("HTTP redirects to the HTTPS login", response.status == 308 and
             response.getheader("Location") == "https://127.0.0.1:8443/login")
        response.read()
    finally:
        redirect.close()
    with urllib.request.urlopen(base + "/dashboard", context=context, timeout=20) as response:
        mark("Production SPA deep link and security response headers",
             'id="app"' in response.read().decode() and
             response.getheader("X-Content-Type-Options") == "nosniff" and
             response.getheader("X-Frame-Options") == "DENY")
    mark("Self-registration is closed", call("/system/public-config")["registrationEnabled"] is False)
    call("/users/register", "POST", {"username": "formal_forbidden", "password": "Demo@12345"}, expected=403)
    call("/users/login", "POST", {"username": "dev_admin1", "password": "Demo@12345"}, expected=401)
    mark("Public registration and development administrator login are rejected")
    for path in ("/internal/identity", "/v3/api-docs", "/swagger-ui/index.html"):
        call(path, expected=404)
    mark("Internal endpoints and development API documentation are not exposed")
    cores = command(COMPOSE + ["ps", "-q", "backend"]).splitlines()
    knowledge = command(COMPOSE + ["ps", "-q", "knowledge"]).splitlines()
    mark("Two core and two knowledge instances are running", len(cores) == len(knowledge) == 2)
    for core in cores:
        env = dict(row.split("=", 1) for row in json.loads(command([
            "docker", "inspect", "--format", "{{json .Config.Env}}", core])) if "=" in row)
        profiles = env["SPRING_PROFILES_ACTIVE"].split(",")
        mark("Production profile and normal rate limit on " + core[:12],
             "prod" in profiles and "dev" not in profiles and "enterprise" not in profiles and
             env.get("APP_RATE_LIMIT_PER_MINUTE") == "120")
    for service in ["postgres", "registry", *[f"redis-{i}" for i in range(6)],
                    *[f"rabbit-{i}" for i in range(3)]]:
        for container in command(COMPOSE + ["ps", "-q", service]).splitlines():
            bindings = json.loads(command(["docker", "inspect", "--format", "{{json .HostConfig.PortBindings}}", container]))
            if bindings:
                raise AssertionError("Middleware management port exposed by the formal project")
    mark("Database, Redis, RabbitMQ and registry have no host port bindings")
    seed_count = command(COMPOSE + ["exec", "-T", "postgres", "psql", "-U", "cluster_admin", "-d",
        "intelligent_ticket", "-Atc", "SELECT count(*) FROM app_user WHERE username LIKE 'dev_%'"])
    mark("Formal database contains no development seed accounts", seed_count == "0")
    admin = call("/users/login", "POST", {"username": private["ADMIN_USERNAME"],
                                           "password": private["ADMIN_PASSWORD"]})["token"]
    status = call("/admin/system", token=admin)
    mark("PostgreSQL, Redis Cluster and RabbitMQ are available",
         all(status[key] == "UP" for key in ("postgres", "redis", "rabbitmq")))
    tag = uuid.uuid4().hex[:12]
    username, password = "formalcheck_" + tag, secrets.token_urlsafe(18)
    user_id, article_id, ticket_id, admin_ticket_id = None, None, None, None
    ai = False
    try:
        call("/admin/users", "POST", {"username": username, "password": password}, admin)
        user_id = call("/admin/users?keyword=" + username, token=admin)["records"][0]["id"]
        user = call("/users/login", "POST", {"username": username, "password": password})["token"]
        call("/admin/users", token=user, expected=403)
        mark("Administrator creates a normal user and backend enforces role permissions")
        admin_ticket_id = call("/tickets", "POST", {"title": "Formal ownership check " + tag, "priority": 3}, admin)["id"]
        call("/tickets/" + str(admin_ticket_id), token=user, expected=403)
        mark("Cross-user ticket access is denied")
        article = {"title": "VPN认证失败排查 " + tag, "summary": "VPN认证错误的内部处理步骤",
                   "content": "VPN认证失败时，先检查网络连接及公司VPN客户端配置，再核对账号是否已获VPN授权。"
                              "确认用户名格式，尝试重新连接；仍失败时提交工单并附错误时间及错误码。禁止提供密码或验证码。",
                   "category": "VPN", "keywords": "VPN,认证失败,网络", "status": "DRAFT"}
        article_id = call("/knowledge", "POST", article, admin)["id"]
        call("/knowledge/" + str(article_id), token=user, expected=404)
        call("/knowledge/" + str(article_id), "PUT", {**article, "status": "PUBLISHED"}, admin)
        call("/knowledge/" + str(article_id), token=user)
        mark("Knowledge draft isolation and publication workflow")
        payload = {"title": "VPN认证失败 " + tag, "description": "Formal HTTPS workflow", "priority": 3}
        # Explicitly keep the same request ID for the two authenticated writes.
        def create_idempotently():
            req = urllib.request.Request(base + "/api/tickets", method="POST", data=json.dumps(payload).encode(),
                headers={"Content-Type": "application/json", "Authorization": "Bearer " + user,
                         "X-Request-Id": tag})
            with urllib.request.urlopen(req, context=context, timeout=20) as res:
                assert res.status == 200
                return json.load(res)["data"]["id"]
        ticket_id = create_idempotently()
        mark("Repeated ticket creation remains idempotent", create_idempotently() == ticket_id)
        call(f"/tickets/{ticket_id}/comments", "POST", {"content": "Supplementary details over HTTPS"}, user)
        for state in ("IN_PROGRESS", "CLOSED"):
            call(f"/tickets/{ticket_id}/status", "PATCH", {"status": state}, admin)
        history = call(f"/tickets/{ticket_id}/history", token=user)
        mark("Comments and complete transactional ticket lifecycle", len(history) == 3)
        deadline = time.monotonic() + 45
        while not any(row["targetId"] == ticket_id and row["action"] == "CREATE_TICKET_ASYNC"
                      for row in call("/operation-logs?size=100", token=admin)["records"]):
            if time.monotonic() > deadline:
                raise AssertionError("Formal audit event was not delivered")
            time.sleep(2)
        mark("Transactional Outbox reaches the quorum audit consumer")
        ai = call("/system/capabilities", token=user)["ai"]
        if args.require_ai and not ai:
            raise AssertionError("The complete formal acceptance requires AI to be enabled")
        if ai:
            call("/admin/ai/index", "POST", {}, admin)
            answer = call("/ai/assist", "POST", {"message": "VPN认证失败应该如何排查？请根据知识库简短回答并引用来源。"}, user)
            mark("Real RAG answer includes the currently published knowledge source",
                 bool(answer["answer"].strip()) and any(row["id"] == article_id for row in answer["sources"]))
        call("/users/logout", "POST", {}, user)
        call("/tickets/statistics", token=user, expected=401)
        mark("Logout invalidates the old token across the deployed replicas")
    finally:
        for identifier in (ticket_id, admin_ticket_id):
            if identifier is not None:
                call("/tickets/" + str(identifier), "DELETE", token=admin)
        if article_id is not None:
            call("/knowledge/" + str(article_id), "DELETE", token=admin)
            if ai:
                call("/admin/ai/index", "POST", {}, admin)
        if user_id is not None:
            call("/admin/users/" + str(user_id), "PATCH", {"role": "USER", "enabled": False}, admin)
    mark("Temporary tickets and article removed; validation account disabled with audit retained")
    report = {"result": "PASS", "createdAt": datetime.now(timezone.utc).isoformat(),
              "deployment": "Independent local prod + distributed + HTTPS",
              "url": base, "aiVerified": ai, "certificateVerification": "CA chain and hostname required",
              "checks": CHECKS, "scope": "One local Docker host; local CA is not automatically trusted by browsers. No public domain or cross-host availability claim."}
    (ROOT / args.output).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"PASS: {len(CHECKS)} formal HTTPS and business checks", flush=True)


if __name__ == "__main__":
    main()
