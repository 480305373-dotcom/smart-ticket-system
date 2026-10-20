"""Measure real, authenticated read traffic through the isolated distributed gateway.

First prove the normal 120/minute limit rejects excess requests. Temporarily raise
only its configured threshold for measurement, then restore the normal Compose
configuration in finally. No business records are created or deleted.
"""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import http.client
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import threading
import time
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ["docker", "compose", "-p", "smartticket-cluster", "--env-file",
           str(ROOT / ".runtime/distributed.env"), "-f", str(ROOT / "compose.distributed.yaml")]
PATHS = ["/tickets/page?current=1&size=10", "/tickets/statistics",
         "/tickets/page?current=1&size=10", "/knowledge?current=1&size=10"]


def command(args, timeout=180):
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=timeout)
    if result.returncode:
        raise RuntimeError("Local deployment command failed; no credentials were logged: " + args[0])
    return result.stdout.strip()


def percentile(values, percent):
    if not values:
        return None
    return round(sorted(values)[max(0, math.ceil(len(values) * percent / 100) - 1)], 2)


def summarize(samples, elapsed):
    if not samples or elapsed <= 0:
        raise ValueError("An empty workload cannot establish a capacity baseline")
    statuses = Counter(str(row[0]) for row in samples)
    successful = [row[1] for row in samples if row[0] == 200]
    return {"requests": len(samples), "successful": len(successful),
            "seconds": round(elapsed, 3), "requestsPerSecond": round(len(samples) / elapsed, 2),
            "successfulRequestsPerSecond": round(len(successful) / elapsed, 2),
            "errorRate": round((len(samples) - len(successful)) / len(samples), 6),
            "statuses": dict(sorted(statuses.items())),
            "successLatencyMs": {"p50": percentile(successful, 50),
                                 "p95": percentile(successful, 95),
                                 "p99": percentile(successful, 99),
                                 "max": round(max(successful), 2) if successful else None},
            "allLatencyMs": {"p95": percentile([row[1] for row in samples], 95)},
            "instances": sorted({row[2] for row in samples if row[2]})}


class Client:
    def __init__(self, base, token=None):
        url = urlsplit(base)
        if url.scheme != "http" or url.hostname not in ("127.0.0.1", "localhost"):
            raise ValueError("This benchmark is restricted to the local isolated HTTP deployment")
        self.base_path, self.token = url.path.rstrip("/"), token
        self.connection = http.client.HTTPConnection(url.hostname, url.port, timeout=15)

    def fetch(self, path, body=None):
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        start = time.perf_counter()
        try:
            self.connection.request("POST" if body is not None else "GET", self.base_path + path,
                                    None if body is None else json.dumps(body), headers)
            response = self.connection.getresponse()
            raw = response.read()
            status, instance = response.status, response.getheader("X-SmartTicket-Instance", "")
            payload = json.loads(raw)
            if status == 200 and payload.get("code") != 200:
                status = "invalid_payload"
            return status, (time.perf_counter() - start) * 1000, instance, payload
        except (OSError, ValueError, http.client.HTTPException):
            self.connection.close()
            return "transport_error", (time.perf_counter() - start) * 1000, "", {}

    def close(self):
        self.connection.close()


def login(base, username):
    client = Client(base)
    try:
        result = client.fetch("/users/login", {"username": username,
                                                "password": os.environ.get("BENCHMARK_PASSWORD", "Demo@12345")})
        if result[0] != 200:
            raise RuntimeError("Benchmark login failed with status " + str(result[0]))
        return result[3]["data"]["token"]
    finally:
        client.close()


def wait_ready(base, timeout=240):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        client = Client(base)
        try:
            if client.fetch("/system/public-config")[0] == 200:
                return
        finally:
            client.close()
        time.sleep(2)
    raise RuntimeError("Gateway did not recover within the readiness window")


def resources():
    return [json.loads(line) for line in command([
        "docker", "stats", "--no-stream", "--format", "{{json .}}",
        *command(COMPOSE + ["ps", "-q"]).splitlines()]).splitlines()]


def workload(base, token, concurrency, duration):
    barrier = threading.Barrier(concurrency + 1)
    deadline = [0.0]

    def worker(index):
        client = Client(base, token)
        samples = []
        barrier.wait()
        try:
            while time.perf_counter() < deadline[0]:
                result = client.fetch(PATHS[(index + len(samples)) % len(PATHS)])
                samples.append(result[:3])
            return samples
        finally:
            client.close()

    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(worker, index) for index in range(concurrency)]
        start = time.perf_counter()
        deadline[0] = start + duration
        barrier.wait()
        samples = [row for future in futures for row in future.result()]
        elapsed = time.perf_counter() - start
    return summarize(samples, elapsed)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8090/api")
    parser.add_argument("--stages", default="1,4,8,16")
    parser.add_argument("--duration", type=int, default=20)
    parser.add_argument("--output", default="docs/capacity-verification.json")
    args = parser.parse_args()
    stages = [int(value) for value in args.stages.split(",")]
    if args.duration < 5 or not stages or any(value < 1 or value > 64 for value in stages):
        parser.error("Use stages between 1 and 64 workers and duration >= 5 seconds")
    Client(args.base).close()
    wait_ready(args.base)
    replicas = command(COMPOSE + ["ps", "-q", "backend"]).splitlines()
    if len(replicas) != 2:
        raise RuntimeError("Start the standard isolated deployment with two core replicas first")
    for replica in replicas:
        env = json.loads(command(["docker", "inspect", "--format", "{{json .Config.Env}}", replica]))
        if any(value.startswith("APP_RATE_LIMIT_PER_MINUTE=") for value in env):
            raise RuntimeError("Unexpected existing capacity override; restore the standard deployment first")
    guard = Client(args.base, login(args.base, "dev_lisi"))
    try:
        guard_statuses = Counter(str(guard.fetch("/tickets/statistics")[0]) for _ in range(135))
        if not guard_statuses.get("429"):
            # A fixed-minute boundary can occur during the first batch.
            guard_statuses.update(str(guard.fetch("/tickets/statistics")[0]) for _ in range(135))
    finally:
        guard.close()
    if not guard_statuses.get("429") or any(status not in ("200", "429") for status in guard_statuses):
        raise AssertionError("The normal rate-limit protection did not behave as expected")
    print("PASS: normal rate-limit protection returns HTTP 429", flush=True)
    report = {"result": "RUNNING", "createdAt": datetime.now(timezone.utc).isoformat(),
              "sourceCommit": command(["git", "rev-parse", "HEAD"]),
              "environment": {"client": "Python stdlib HTTP/1.1, persistent connection per worker",
                              "platform": platform.system(),
                              "docker": json.loads(command(["docker", "info", "--format",
                                                            '{"cpus":{{.NCPU}},"memoryBytes":{{.MemTotal}}}'])),
                              "otherProjectsMayShareResources": True,
                              "coreReplicas": 2, "knowledgeReplicas": 2},
              "workload": {"method": "closed-loop authenticated reads", "paths": PATHS,
                           "durationPerStageSeconds": args.duration, "dataset": json.loads(command(
                               COMPOSE + ["exec", "-T", "postgres", "psql", "-U", "cluster_admin",
                                          "-d", "intelligent_ticket", "-Atc",
                                          "SELECT json_build_object('users',(SELECT count(*) FROM app_user),"
                                          "'tickets',(SELECT count(*) FROM ticket))"]))},
              "normalRateLimit": {"perUserPerMinute": 120, "statuses": dict(guard_statuses)},
              "measurementRateLimit": {"perUserPerMinute": 120000,
                                       "authenticationAndRedisLimiterRemainEnabled": True},
              "criteria": {"maxErrorRate": 0.01, "maxP95Ms": 2000}, "stages": []}
    try:
        command(COMPOSE + ["-f", str(ROOT / "compose.capacity.yaml"), "up", "-d", "--no-build",
                           "--no-deps", "--scale", "backend=2", "backend"], timeout=240)
        wait_ready(args.base)
        # Wait for both replacement instances to be present in the discovery clients.
        token = login(args.base, os.environ.get("BENCHMARK_USER", "dev_zhangsan"))
        observed = set()
        deadline = time.monotonic() + 120
        probe = Client(args.base, token)
        try:
            while len(observed) < 2 and time.monotonic() < deadline:
                response = probe.fetch("/tickets/statistics")
                if response[0] == 200:
                    observed.add(response[2])
                time.sleep(0.2)
        finally:
            probe.close()
        if len(observed - {""}) != 2:
            raise AssertionError("Both core replicas must receive traffic before measurement")
        workload(args.base, token, 2, 5)
        for concurrency in stages:
            before = resources()
            result = {"concurrency": concurrency, **workload(args.base, token, concurrency, args.duration)}
            result["resourcesBefore"], result["resourcesAfter"] = before, resources()
            result["passed"] = (result["errorRate"] <= 0.01 and
                                (result["successLatencyMs"]["p95"] or float("inf")) <= 2000)
            report["stages"].append(result)
            print(json.dumps({key: result[key] for key in
                              ("concurrency", "successfulRequestsPerSecond", "errorRate", "successLatencyMs", "passed")}), flush=True)
    finally:
        command(COMPOSE + ["up", "-d", "--no-build", "--no-deps", "--scale", "backend=2", "backend"], timeout=240)
        wait_ready(args.base)
        for replica in command(COMPOSE + ["ps", "-q", "backend"]).splitlines():
            env = json.loads(command(["docker", "inspect", "--format", "{{json .Config.Env}}", replica]))
            if any(value.startswith("APP_RATE_LIMIT_PER_MINUTE=") for value in env):
                raise AssertionError("The temporary capacity override was not removed")
        report["standardConfigurationRestored"] = True
        report["result"] = "PASS" if len(report["stages"]) == len(stages) and all(x["passed"] for x in report["stages"]) else "FAIL"
        report["scope"] = ("Measured lower bound for this small dataset on one shared local Docker host; "
                           "not maximum capacity, sustained production capacity, write throughput or AI throughput. "
                           "429 is a rejected request and is never counted as a successful transaction.")
        output = ROOT / args.output
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if report["result"] != "PASS":
        raise AssertionError("Capacity acceptance thresholds failed; inspect the recorded errors and latency")
    print("PASS: capacity baseline recorded and normal configuration restored", flush=True)


if __name__ == "__main__":
    main()
