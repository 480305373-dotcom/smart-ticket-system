"""Read-only PostgreSQL demo verification; never prints credentials."""
from pathlib import Path
import os, re, json, subprocess, shutil

root = Path(__file__).resolve().parents[1]
env = os.environ.copy()
password = env.get('DB_PASSWORD') or env.get('SPRING_DATASOURCE_PASSWORD')
local = root / 'backend/config/application-local.properties'
if not password and local.exists():
    config = local.read_text(encoding='utf-8')
    match = re.search(r'^spring.datasource.password=(.*)$', config, re.M)
    if match:
        password = match[1].strip()
        if password.startswith('${DB_PASSWORD:'): password = password[len('${DB_PASSWORD:'):-1]
if not password: raise SystemExit('Set DB_PASSWORD before running database verification.')
env['PGPASSWORD'] = password
psql = shutil.which('psql') or r'C:\Program Files\PostgreSQL\18\bin\psql.exe'
url = env.get('SPRING_DATASOURCE_URL', 'jdbc:postgresql://localhost:5432/intelligent_ticket')
from urllib.parse import urlparse
parsed = urlparse(url.removeprefix('jdbc:'))
query = """SELECT json_build_object(
 'users',(SELECT count(*) FROM app_user),
 'tickets',(SELECT count(*) FROM ticket),
 'demoTickets',(SELECT count(*) FROM ticket t JOIN app_user u ON u.id=t.user_id WHERE u.username IN ('dev_zhangsan','dev_lisi','dev_wangwu')),
 'articles',(SELECT count(*) FROM knowledge_article),
 'comments',(SELECT count(*) FROM ticket_comment),
 'history',(SELECT count(*) FROM ticket_history),
 'seedMarkers',(SELECT count(*) FROM demo_seed WHERE seed_key='enterprise-demo-v1'),
 'bcryptUsers',(SELECT count(*) FROM app_user WHERE password_hash ~ '^\\$2[aby]\\$'),
 'priorities',(SELECT json_agg(DISTINCT priority) FROM ticket),
 'statuses',(SELECT json_agg(DISTINCT status) FROM ticket)
);"""
result = subprocess.run([psql,'-h',parsed.hostname,'-p',str(parsed.port or 5432),'-U',env.get('SPRING_DATASOURCE_USERNAME','postgres'),'-d',parsed.path.lstrip('/'),'-At','-v','ON_ERROR_STOP=1','-c',query], env=env, capture_output=True, text=True, check=True)
data = json.loads(result.stdout.strip())
assert data['demoTickets'] == 25, data
assert data['seedMarkers'] == 1, data
assert data['articles'] >= 12 and data['comments'] >= 26 and data['history'] >= 49, data
assert data['users'] == data['bcryptUsers'], data
assert sorted(data['priorities']) == [1,2,3,4,5], data
assert {'OPEN','IN_PROGRESS','CLOSED'}.issubset(data['statuses']), data
data['result'] = 'PASS'
(root/'docs/database-verification.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(data,ensure_ascii=False,indent=2))
