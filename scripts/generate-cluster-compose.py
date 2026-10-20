"""Render a deterministic, reviewable Compose file. JSON is valid YAML 1.2."""
import json
from pathlib import Path

root = Path(__file__).resolve().parent.parent
services, volumes = {}, {}
services['postgres'] = {
    'image': 'pgvector/pgvector:pg18', 'environment': {
        'POSTGRES_USER': 'cluster_admin', 'POSTGRES_PASSWORD': '${POSTGRES_PASSWORD:?required}',
        'CORE_DB_PASSWORD': '${CORE_DB_PASSWORD:?required}', 'KNOWLEDGE_DB_PASSWORD': '${KNOWLEDGE_DB_PASSWORD:?required}'},
    'volumes': ['postgres:/var/lib/postgresql', './docker/distributed/postgres-init.sh:/docker-entrypoint-initdb.d/init.sh:ro'],
    'healthcheck': {'test': ['CMD-SHELL', 'pg_isready -U cluster_admin -d postgres'], 'interval':'5s','retries':30}}
volumes['postgres']={}
services['registry']={'image':'smartticket/registry:local','build':{'context':'.','dockerfile':'docker/distributed/Service.Dockerfile','args':{'MODULE':'registry'}},
    'ports':['127.0.0.1:8501:8080'],'environment':{'JAVA_TOOL_OPTIONS':'-XX:ActiveProcessorCount=2'},'mem_limit':'384m',
    'healthcheck':{'test':['CMD-SHELL','wget -q --spider http://127.0.0.1:8080/actuator/health/readiness'],'interval':'5s','start_period':'30s','retries':30}}
for i in range(6):
    name=f'redis-{i}'
    services[name]={'image':'redis:7-alpine','hostname':name,'entrypoint':['sh','/opt/redis-start.sh'],
        'environment':{'REDIS_PASSWORD':'${REDIS_PASSWORD:?required}','NODE_NAME':name},
        'volumes':[f'{name}:/data','./docker/distributed/redis-start.sh:/opt/redis-start.sh:ro'],
        'healthcheck':{'test':['CMD-SHELL','REDISCLI_AUTH=$$REDIS_PASSWORD redis-cli ping | grep PONG'],'interval':'5s','retries':30}}
    volumes[name]={}
services['redis-init']={'image':'redis:7-alpine','restart':'no','entrypoint':['sh','/opt/init.sh'],
    'environment':{'REDIS_PASSWORD':'${REDIS_PASSWORD:?required}'},
    'volumes':['./docker/distributed/redis-init.sh:/opt/init.sh:ro'],
    'depends_on':{f'redis-{i}':{'condition':'service_healthy'} for i in range(6)}}
for i in range(3):
    name=f'rabbit-{i}'
    services[name]={'image':'rabbitmq:4-management','hostname':name,
        'environment':{'RABBITMQ_NODENAME':f'rabbit@{name}','RABBITMQ_ERLANG_COOKIE':'${RABBITMQ_COOKIE:?required}',
            'RABBITMQ_DEFAULT_USER':'smartticket','RABBITMQ_DEFAULT_PASS':'${RABBITMQ_PASSWORD:?required}',
            'RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS':'+S 2:2'},
        'volumes':[f'{name}:/var/lib/rabbitmq','./docker/distributed/rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf:ro'],
        'healthcheck':{'test':['CMD-SHELL',"rabbitmq-diagnostics -q ping && rabbitmqctl -q eval 'length(rabbit_nodes:list_running()).' | grep -q '^3$'"],'interval':'10s','timeout':'10s','retries':30}}
    volumes[name]={}

common={'EUREKA_URL':'http://registry:8080/eureka/','INTERNAL_SERVICE_KEY':'${INTERNAL_SERVICE_KEY:?required}',
        'TZ':'Asia/Shanghai','JAVA_TOOL_OPTIONS':'-Duser.timezone=Asia/Shanghai -XX:ActiveProcessorCount=2'}
health={'test':['CMD-SHELL','wget -q --spider http://127.0.0.1:8080/actuator/health/readiness'], 'interval':'10s','start_period':'90s','retries':30}
services['knowledge']={'image':'smartticket/knowledge-service:local','build':{'context':'.','dockerfile':'docker/distributed/Service.Dockerfile','args':{'MODULE':'knowledge-service'}},
    'environment':{**common,'KNOWLEDGE_DB_URL':'jdbc:postgresql://postgres:5432/smartticket_knowledge','KNOWLEDGE_DB_PASSWORD':'${KNOWLEDGE_DB_PASSWORD:?required}'},
    'depends_on':{'postgres':{'condition':'service_healthy'},'registry':{'condition':'service_healthy'}},'healthcheck':health,'mem_limit':'512m','stop_grace_period':'60s'}
services['backend']={'image':'smartticket/backend:local','build':{'context':'.','dockerfile':'docker/distributed/Service.Dockerfile','args':{'MODULE':'backend'}},
    'environment':{**common,'SPRING_PROFILES_ACTIVE':'enterprise,ai,distributed','SERVER_ADDRESS':'0.0.0.0',
        'SPRING_DATASOURCE_URL':'jdbc:postgresql://postgres:5432/intelligent_ticket','SPRING_DATASOURCE_USERNAME':'core','DB_PASSWORD':'${CORE_DB_PASSWORD:?required}',
        'JWT_SECRET':'${JWT_SECRET:?required}','SPRING_CONFIG_IMPORT':'optional:classpath:empty.properties',
        'REDIS_CLUSTER_NODES':','.join(f'redis-{i}:6379' for i in range(6)),'SPRING_DATA_REDIS_PASSWORD':'${REDIS_PASSWORD:?required}',
        'RABBITMQ_ADDRESSES':','.join(f'rabbit-{i}:5672' for i in range(3)),'SPRING_RABBITMQ_USERNAME':'smartticket','SPRING_RABBITMQ_PASSWORD':'${RABBITMQ_PASSWORD:?required}',
        'OLLAMA_URL':'http://host.docker.internal:11434','CORS_ORIGINS':'http://127.0.0.1:8090'},
    'extra_hosts':['host.docker.internal:host-gateway'],
    'depends_on':{'knowledge':{'condition':'service_healthy'},'redis-init':{'condition':'service_completed_successfully'},**{f'rabbit-{i}':{'condition':'service_healthy'} for i in range(3)}},
    'healthcheck':health,'mem_limit':'768m','stop_grace_period':'60s'}
services['gateway']={'image':'smartticket/gateway:local','build':{'context':'.','dockerfile':'docker/distributed/Service.Dockerfile','args':{'MODULE':'gateway'}},
    'environment':{k:v for k,v in common.items() if k!='INTERNAL_SERVICE_KEY'},'depends_on':{'backend':{'condition':'service_healthy'}},'healthcheck':health,'mem_limit':'384m'}
services['frontend']={'image':'smartticket/frontend:local','build':{'context':'.','dockerfile':'frontend/Dockerfile','target':'prebuilt'},
    'volumes':['./docker/distributed/nginx.conf:/etc/nginx/conf.d/default.conf:ro'],'ports':['127.0.0.1:8090:80'],
    'depends_on':{'gateway':{'condition':'service_healthy'}},'mem_limit':'64m'}
for name,service in services.items():
    if name!='redis-init': service['restart']='unless-stopped'
(root/'compose.distributed.yaml').write_text(json.dumps({'services':services,'volumes':volumes},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Rendered compose.distributed.yaml')
