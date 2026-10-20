"""Recreate only smartticket-cluster containers/network, retaining all data volumes."""
import argparse
import json
import time
import uuid
from pathlib import Path

from distributed_smoke import ROOT, compose, command, redis, request, eventually


def snapshot():
    result = {}
    for i in range(6):
        name = f'redis-{i}'
        container = compose('ps', '-q', name).stdout.strip()
        ip = command('docker', 'inspect', '-f', '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}', container).stdout.strip()
        own = next(line.split() for line in redis(name, 'cluster', 'nodes').splitlines() if 'myself' in line.split()[2])
        announced = own[1].split(':', 1)[0]
        if announced != ip:
            raise AssertionError(f'{name} advertises a stale container address')
        result[name] = {'id': own[0], 'ip': ip}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='.runtime/distributed-restart-verification.json')
    args = parser.parse_args()
    before = snapshot()
    key = 'smartticket:verification:restart:' + uuid.uuid4().hex
    value = uuid.uuid4().hex
    if redis('redis-0', '-c', 'SET', key, value, 'EX', '1800') != 'OK':
        raise AssertionError('Could not write the isolated persistence probe')
    time.sleep(2)
    restored = False
    def start():
        result = command('pwsh', '-NoProfile', '-File', str(ROOT/'scripts/start-distributed.ps1'), '-SkipBuild', '-SkipImageBuild', check=False, timeout=360)
        if result.returncode:
            print(result.stdout, flush=True)
            print(result.stderr, flush=True)
            raise RuntimeError('Dedicated cluster restart failed; startup diagnostics printed above')
    try:
        # No --volumes option: PostgreSQL, Redis and RabbitMQ retain their data.
        compose('down')
        start()
        restored = True
        after = snapshot()
        if {n: d['id'] for n,d in before.items()} != {n: d['id'] for n,d in after.items()}:
            raise AssertionError('Redis node identities changed; data volumes were not retained')
        if redis('redis-0', '-c', 'GET', key) != value:
            raise AssertionError('Redis persisted probe was lost during recreation')
        replicas = 0
        for i in range(6):
            info = redis(f'redis-{i}', 'info', 'replication')
            if 'role:slave' in info:
                replicas += 1
                if 'master_link_status:up' not in info:
                    raise AssertionError('A Redis replication link is still disconnected')
        if replicas != 3:
            raise AssertionError('Expected exactly three connected Redis replicas')
        base = 'http://127.0.0.1:8090/api'
        eventually(lambda: request(base, '/users/login', 'POST', body={'username':'dev_zhangsan','password':'Demo@12345'})[0] == 200, 120)
        changed = sum(before[n]['ip'] != after[n]['ip'] for n in before)
        Path(ROOT/args.output).write_text(json.dumps({'result':'PASS','nodeIdentitiesPreserved':6,
            'nodesWithChangedIp':changed,'allAdvertisedAddressesCurrent':True,'persistedValueRetained':True,
            'connectedReplicas':replicas,'httpLogin':200,'before':before,'after':after}, indent=2)+'\n', encoding='utf-8')
        print(f'PASS: persisted restart, 6 identities retained, {changed} addresses changed, 3 replicas connected, HTTP login succeeds')
    finally:
        if not restored:
            start()


if __name__ == '__main__':
    main()
