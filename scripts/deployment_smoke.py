"""Verify the deployed Nginx, API and optional Prometheus/Grafana stack.

Reads only local private deployment credentials; reports never contain secrets.
"""
import argparse
import base64
import json
import os
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WEB = os.environ.get('SMART_TICKET_WEB', 'http://127.0.0.1:8088')
checks = []


def fetch(url, data=None, token=None, basic=None, expected=200, raw=False, extra_headers=None):
    headers = {'Content-Type': 'application/json'}
    headers.update(extra_headers or {})
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if basic:
        headers['Authorization'] = 'Basic ' + base64.b64encode(basic.encode()).decode()
    request = urllib.request.Request(url, data=json.dumps(data).encode() if data is not None else None, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            status, body = response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        status, body = error.code, error.read().decode()
    assert status == expected, (url, status, expected)
    return body if raw else json.loads(body)


def wait_for(description, predicate, timeout=180):
    deadline = time.monotonic() + timeout
    while True:
        try:
            if predicate():
                checks.append(description)
                return
        except (OSError, ValueError, AssertionError, KeyError):
            pass
        if time.monotonic() >= deadline:
            raise AssertionError('Timed out: ' + description)
        time.sleep(3)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--monitoring', action='store_true')
    args = parser.parse_args()
    private = dict(line.split('=', 1) for line in (ROOT / '.runtime/production.env').read_text(encoding='utf-8-sig').splitlines() if '=' in line)
    api = WEB + '/api'
    wait_for('Nginx and API readiness', lambda: fetch(api + '/actuator/health/readiness')['status'] == 'UP')
    assert 'id="app"' in fetch(WEB + '/dashboard', raw=True)
    checks.append('SPA deep link and production static page')
    assert fetch(api + '/system/public-config')['data']['registrationEnabled'] is False
    fetch(api + '/users/register', {'username': 'deployment_disabled', 'password': 'Demo@12345'}, expected=403)
    checks.append('Production self-registration is closed')
    admin = fetch(api + '/users/login', {'username': 'dev_admin1', 'password': 'Demo@12345'})['data']['token']
    user = fetch(api + '/users/login', {'username': 'dev_zhangsan', 'password': 'Demo@12345'})['data']['token']
    status = fetch(api + '/admin/system', token=admin)['data']
    assert all(status[key] == 'UP' for key in ('postgres', 'redis', 'rabbitmq'))
    checks.append('Real PostgreSQL, authenticated Redis and RabbitMQ')
    fetch(api + '/admin/system', token=user, expected=403)
    fetch(api + '/actuator/prometheus', expected=401)
    exporter = fetch(api + '/actuator/prometheus', basic='monitor:' + private['MONITOR_PASSWORD'], raw=True)
    assert 'jvm_memory_used_bytes' in exporter and 'smartticket_outbox_events' in exporter
    fetch(api + '/admin/system', basic='monitor:' + private['MONITOR_PASSWORD'], expected=401)
    checks.append('User/admin separation and dedicated monitor authentication')
    # Repeating a request ID must produce a single ticket and a delivered event.
    request_id = str(uuid.uuid4())
    title = 'Deployment verification ' + request_id[:8]
    payload = {'title': title, 'description': 'Deployment smoke test', 'priority': 3}
    ticket = fetch(api + '/tickets', payload, user, extra_headers={'X-Request-Id': request_id})['data']
    try:
        repeated = fetch(api + '/tickets', payload, user, extra_headers={'X-Request-Id': request_id})['data']
        assert repeated['id'] == ticket['id']
        wait_for('Outbox delivered and asynchronous audit recorded', lambda: any(row['targetId'] == ticket['id'] and row['action'] == 'CREATE_TICKET_ASYNC' for row in fetch(api + '/operation-logs?size=100', token=admin)['data']['records']))
        checks.append('Idempotent ticket creation through Nginx')
    finally:
        request = urllib.request.Request(api + '/tickets/' + str(ticket['id']), headers={'Authorization': 'Bearer ' + user}, method='DELETE')
        with urllib.request.urlopen(request, timeout=20) as response:
            assert response.status == 200
    if args.monitoring:
        prom = os.environ.get('PROMETHEUS_URL', 'http://127.0.0.1:9090')
        grafana = os.environ.get('GRAFANA_URL', 'http://127.0.0.1:3000')
        wait_for('Prometheus authenticated backend scrape is UP', lambda: any(t['labels'].get('job') == 'smart-ticket' and t['health'] == 'up' for t in fetch(prom + '/api/v1/targets')['data']['activeTargets']))
        metrics = fetch(prom + '/api/v1/query?query=jvm_memory_used_bytes')['data']['result']
        assert metrics
        credentials = 'admin:' + private['GRAFANA_PASSWORD']
        wait_for('Grafana database ready', lambda: fetch(grafana + '/api/health')['database'] == 'ok')
        dashboard = fetch(grafana + '/api/dashboards/uid/smart-ticket', basic=credentials)['dashboard']
        assert len(dashboard['panels']) == 4
        assert fetch(grafana + '/api/datasources/uid/prometheus/health', basic=credentials)['status'] == 'OK'
        checks.append('Provisioned Grafana dashboard and working Prometheus datasource')
    report = {'result': 'PASS', 'deployment': 'Docker + Nginx', 'monitoring': args.monitoring, 'checks': checks}
    (ROOT / 'docs/deployment-verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('PASS: ' + str(len(checks)) + ' deployed stack checks; monitoring=' + str(args.monitoring))


if __name__ == '__main__':
    main()
