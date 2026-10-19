"""Build the checked-in Kubernetes resources from the same scripts as Compose."""
import json
from pathlib import Path

root=Path(__file__).resolve().parent.parent
out=root/'deploy/kubernetes'
out.mkdir(parents=True,exist_ok=True)
objects=[]
def resource(kind,name,spec=None,api='v1',**extra):
    obj={'apiVersion':api,'kind':kind,'metadata':{'name':name,'namespace':'smartticket'}}
    if spec is not None:obj['spec']=spec
    obj.update(extra);objects.append(obj);return obj
objects.append({'apiVersion':'v1','kind':'Namespace','metadata':{'name':'smartticket'}})
files={name:(root/'docker/distributed'/file).read_text() for name,file in {
    'postgres-init.sh':'postgres-init.sh','redis-start.sh':'redis-start.sh','redis-init.sh':'redis-init.sh','default.conf':'nginx.conf'}.items()}
files['rabbitmq.conf']=(root/'docker/distributed/rabbitmq.conf').read_text()
for i in range(3):files['rabbitmq.conf']=files['rabbitmq.conf'].replace(f'rabbit@rabbit-{i}',f'rabbit@rabbit-{i}.rabbit-nodes.smartticket.svc.cluster.local')
resource('ConfigMap','cluster-config',data=files)
def secret(key,name=None):return {'name':name or key,'valueFrom':{'secretKeyRef':{'name':'cluster-secrets','key':key}}}
def literal(name,value):return {'name':name,'value':str(value)}
def field(name,path):return {'name':name,'valueFrom':{'fieldRef':{'fieldPath':path}}}
def service(name,ports,selector=None,headless=False,nodeport=None):
    spec={'selector':{'app':selector or name},'ports':[{'name':n,'port':p,'targetPort':p} for n,p in ports]}
    if headless:spec.update(clusterIP='None',publishNotReadyAddresses=True)
    if nodeport:spec.update(type='NodePort');spec['ports'][0]['nodePort']=nodeport
    resource('Service',name,spec)
def container(name,image,env=None,command=None,ports=None,memory='256Mi',cpu='100m'):
    c={'name':name,'image':image,'imagePullPolicy':'IfNotPresent','resources':{'requests':{'cpu':cpu,'memory':'64Mi'},'limits':{'memory':memory}},
        'securityContext':{'allowPrivilegeEscalation':False,'capabilities':{'drop':['ALL']}}}
    if env:c['env']=env
    if command:c['command']=command
    if ports:c['ports']=[{'containerPort':p} for p in ports]
    return c
def stateful(name,replicas,c,headless,data_path,storage='1Gi',config_mounts=None):
    c['volumeMounts']=[{'name':'data','mountPath':data_path}]+(config_mounts or [])
    pod={'automountServiceAccountToken':False,'containers':[c],
        'volumes':[{'name':'config','configMap':{'name':'cluster-config','defaultMode':365}}],
        'terminationGracePeriodSeconds':40}
    if name in ('redis','rabbit'):
        pod['topologySpreadConstraints']=[{'maxSkew':1,'topologyKey':'kubernetes.io/hostname','whenUnsatisfiable':'ScheduleAnyway','labelSelector':{'matchLabels':{'app':name}}}]
    if name=='rabbit':
        pod['affinity']={'podAntiAffinity':{'requiredDuringSchedulingIgnoredDuringExecution':[{'labelSelector':{'matchLabels':{'app':'rabbit'}},'topologyKey':'kubernetes.io/hostname'}]}}
    resource('StatefulSet',name,{'serviceName':headless,'replicas':replicas,'podManagementPolicy':'Parallel',
        'selector':{'matchLabels':{'app':name}},'template':{'metadata':{'labels':{'app':name}},'spec':pod},
        'volumeClaimTemplates':[{'metadata':{'name':'data'},'spec':{'accessModes':['ReadWriteOnce'],'resources':{'requests':{'storage':storage}}}}]},api='apps/v1')
def mount(key,path):return {'name':'config','mountPath':path,'subPath':key,'readOnly':True}
def exec_probe(cmd):return {'exec':{'command':['sh','-ec',cmd]},'periodSeconds':5,'timeoutSeconds':3,'failureThreshold':12}
service('postgres',[('postgres',5432)])
pg=container('postgres','pgvector/pgvector:pg18',[literal('POSTGRES_USER','cluster_admin'),secret('POSTGRES_PASSWORD'),secret('CORE_DB_PASSWORD'),secret('KNOWLEDGE_DB_PASSWORD')],memory='512Mi',ports=[5432])
# Official PostgreSQL/Rabbit images must initialize ownership before dropping privileges.
pg.pop('securityContext')
pg['readinessProbe']=exec_probe('pg_isready -U cluster_admin -d postgres')
stateful('postgres',1,pg,'postgres','/var/lib/postgresql',config_mounts=[mount('postgres-init.sh','/docker-entrypoint-initdb.d/init.sh')])
service('redis-nodes',[('redis',6379),('bus',16379)],selector='redis',headless=True)
redis=container('redis','redis:7-alpine',[secret('REDIS_PASSWORD')],['sh','-ec','export NODE_NAME="$HOSTNAME.redis-nodes"; exec sh /opt/redis-start.sh'],memory='160Mi',cpu='50m',ports=[6379,16379])
redis.pop('securityContext');redis['readinessProbe']=exec_probe('REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping | grep -q PONG')
stateful('redis',6,redis,'redis-nodes','/data',config_mounts=[mount('redis-start.sh','/opt/redis-start.sh')])
init=container('init','redis:7-alpine',[secret('REDIS_PASSWORD'),literal('REDIS_DNS_SUFFIX','.redis-nodes')],['sh','/opt/redis-init.sh'],memory='96Mi',cpu='50m')
init['volumeMounts']=[mount('redis-init.sh','/opt/redis-init.sh')]
resource('Job','redis-cluster-init',{'backoffLimit':3,'template':{'spec':{'automountServiceAccountToken':False,'restartPolicy':'OnFailure','containers':[init],'volumes':[{'name':'config','configMap':{'name':'cluster-config'}}]}}},api='batch/v1')
service('rabbit-nodes',[('amqp',5672),('epmd',4369),('distribution',25672),('management',15672)],selector='rabbit',headless=True)
service('rabbit-ready',[('amqp',5672)],selector='rabbit')
rabbit=container('rabbit','rabbitmq:4-management',[field('POD_NAME','metadata.name'),literal('RABBITMQ_USE_LONGNAME','true'),
    literal('RABBITMQ_NODENAME','rabbit@$(POD_NAME).rabbit-nodes.smartticket.svc.cluster.local'),literal('RABBITMQ_DEFAULT_USER','smartticket'),
    secret('RABBITMQ_PASSWORD','RABBITMQ_DEFAULT_PASS'),secret('RABBITMQ_COOKIE','RABBITMQ_ERLANG_COOKIE'),literal('RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS','+S 2:2')],ports=[5672,4369,25672,15672],memory='512Mi')
rabbit.pop('securityContext');rabbit['readinessProbe']=exec_probe('rabbitmq-diagnostics -q ping')
rabbit['startupProbe']={**exec_probe('rabbitmq-diagnostics -q ping'),'failureThreshold':60}
stateful('rabbit',3,rabbit,'rabbit-nodes','/var/lib/rabbitmq',config_mounts=[mount('rabbitmq.conf','/etc/rabbitmq/rabbitmq.conf')])

def deployment(name,image,env,replicas=2,memory='512Mi',http_port=8080):
    service(name,[('http',http_port)])
    c=container(name,image,env,memory=memory,ports=[http_port])
    health='/health' if name=='frontend' else '/actuator/health/readiness'
    live='/health' if name=='frontend' else '/actuator/health/liveness'
    c.update(readinessProbe={'httpGet':{'path':health,'port':http_port},'periodSeconds':5,'timeoutSeconds':3},
             livenessProbe={'httpGet':{'path':live,'port':http_port},'periodSeconds':15,'failureThreshold':5,'timeoutSeconds':3},
             startupProbe={'httpGet':{'path':health,'port':http_port},'periodSeconds':5,'failureThreshold':90,'timeoutSeconds':3})
    pod={'automountServiceAccountToken':False,'terminationGracePeriodSeconds':40,'containers':[c],
         'topologySpreadConstraints':[{'maxSkew':1,'topologyKey':'kubernetes.io/hostname','whenUnsatisfiable':'ScheduleAnyway','labelSelector':{'matchLabels':{'app':name}}}]}
    if name=='frontend':
        c.pop('securityContext');c['volumeMounts']=[mount('default.conf','/etc/nginx/conf.d/default.conf')]
        pod['volumes']=[{'name':'config','configMap':{'name':'cluster-config'}}]
    if name=='backend':
        pod['initContainers']=[container('wait-infrastructure','redis:7-alpine',[secret('REDIS_PASSWORD')],['sh','-ec',
            'until wget -q --spider http://knowledge:8080/actuator/health/readiness; do sleep 3; done; '
            'until nc -z rabbit-ready 5672; do sleep 3; done; '
            'until REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli -h redis-0.redis-nodes cluster info | grep -q cluster_state:ok; do sleep 3; done'],memory='96Mi',cpu='50m')]
    resource('Deployment',name,{'replicas':replicas,'strategy':{'type':'RollingUpdate','rollingUpdate':{'maxSurge':1,'maxUnavailable':0}},
        'selector':{'matchLabels':{'app':name}},'template':{'metadata':{'labels':{'app':name}},'spec':pod}},api='apps/v1')
    resource('PodDisruptionBudget',name,{'minAvailable':1,'selector':{'matchLabels':{'app':name}}},api='policy/v1')
deployment('registry','smartticket/registry:local',[literal('JAVA_TOOL_OPTIONS','-XX:ActiveProcessorCount=2')],replicas=1,memory='384Mi')
common=[literal('EUREKA_URL','http://registry:8080/eureka/'),secret('INTERNAL_SERVICE_KEY'),literal('JAVA_TOOL_OPTIONS','-Duser.timezone=Asia/Shanghai -XX:ActiveProcessorCount=2')]
deployment('knowledge','smartticket/knowledge-service:local',common+[
    literal('KNOWLEDGE_DB_URL','jdbc:postgresql://postgres:5432/smartticket_knowledge'),secret('KNOWLEDGE_DB_PASSWORD')])
deployment('backend','smartticket/backend:local',common+[
    literal('SPRING_PROFILES_ACTIVE','enterprise,ai,distributed'),literal('SERVER_ADDRESS','0.0.0.0'),
    literal('SPRING_DATASOURCE_URL','jdbc:postgresql://postgres:5432/intelligent_ticket'),literal('SPRING_DATASOURCE_USERNAME','core'),
    secret('CORE_DB_PASSWORD','DB_PASSWORD'),secret('JWT_SECRET'),literal('SPRING_CONFIG_IMPORT','optional:classpath:empty.properties'),
    literal('REDIS_CLUSTER_NODES',','.join(f'redis-{i}.redis-nodes:6379' for i in range(6))),secret('REDIS_PASSWORD','SPRING_DATA_REDIS_PASSWORD'),
    literal('RABBITMQ_ADDRESSES',','.join(f'rabbit-{i}.rabbit-nodes:5672' for i in range(3))),literal('SPRING_RABBITMQ_USERNAME','smartticket'),
    secret('RABBITMQ_PASSWORD','SPRING_RABBITMQ_PASSWORD'),literal('OLLAMA_URL','http://ollama-external:11434'),
    literal('CORS_ORIGINS','http://127.0.0.1:8091')],memory='768Mi')
deployment('gateway','smartticket/gateway:local',[literal('EUREKA_URL','http://registry:8080/eureka/'),literal('JAVA_TOOL_OPTIONS','-XX:ActiveProcessorCount=2')],memory='384Mi')
deployment('frontend','smartticket/frontend:local',[],memory='96Mi',http_port=80)
# Public local demonstration entry point. All APIs behind it still pass through the gateway.
for obj in objects:
    if obj['kind']=='Service' and obj['metadata']['name']=='frontend':
        obj['spec']['type']='NodePort';obj['spec']['ports'][0]['nodePort']=30090
resource('PodDisruptionBudget','rabbit',{'minAvailable':2,'selector':{'matchLabels':{'app':'rabbit'}}},api='policy/v1')
resource('PodDisruptionBudget','redis',{'minAvailable':5,'selector':{'matchLabels':{'app':'redis'}}},api='policy/v1')
resource('Service','ollama-external',{'type':'ExternalName','externalName':'host.docker.internal','ports':[{'port':11434}]})
(out/'resources.json').write_text(json.dumps({'apiVersion':'v1','kind':'List','items':objects},indent=2)+'\n')
(out/'kustomization.yaml').write_text('resources:\n  - resources.json\n')
kind={'kind':'Cluster','apiVersion':'kind.x-k8s.io/v1alpha4','name':'smartticket',
    'nodes':[{'role':'control-plane','kubeadmConfigPatches':['kind: InitConfiguration\nnodeRegistration:\n  taints: []\n'],
              'extraPortMappings':[{'containerPort':30090,'hostPort':8091,'listenAddress':'127.0.0.1'}]},
             {'role':'worker'},{'role':'worker'}]}
(out/'kind.json').write_text(json.dumps(kind,indent=2)+'\n')
print(f'Rendered {len(objects)} Kubernetes resources; no secrets included')
