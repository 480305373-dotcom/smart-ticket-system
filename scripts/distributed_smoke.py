"""Real HTTP and cluster checks. Faults affect only smartticket-cluster containers.

Use --faults to stop/restart one instance, one Redis primary, and one Rabbit node.
No user database is deleted, and every stopped container is restarted in finally.
"""
import argparse
import concurrent.futures
import json
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
COMPOSE=['docker','compose','-p','smartticket-cluster','--env-file',str(ROOT/'.runtime/distributed.env'),'-f',str(ROOT/'compose.distributed.yaml')]
checks=[]
def command(*args,check=True,timeout=90):
    result=subprocess.run(list(args),cwd=ROOT,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout)
    if check and result.returncode:raise RuntimeError(f'Container command failed: {args[0:3]} (details kept local)')
    return result
def compose(*args,check=True):return command(*COMPOSE,*args,check=check)
def request(base,path,method='GET',token=None,body=None,headers=None):
    h={'Content-Type':'application/json',**(headers or {})}
    if token:h['Authorization']='Bearer '+token
    req=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=h,method=method)
    try:
        with urllib.request.urlopen(req,timeout=180) as res:return res.status,json.load(res),{k.lower():v for k,v in res.headers.items()}
    except urllib.error.HTTPError as e:
        raw=e.read()
        try:payload=json.loads(raw)
        except ValueError:payload={}
        return e.code,payload,{k.lower():v for k,v in e.headers.items()}
def record(name,passed,**details):
    checks.append({'name':name,'passed':bool(passed),**details})
    if not passed:
        print('FAIL: '+name+' '+json.dumps(details,ensure_ascii=False),flush=True)
        raise AssertionError(name)
    print('PASS: '+name,flush=True)
def eventually(fn,timeout=90):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        try:
            value=fn()
            if value:return value
        except (RuntimeError,urllib.error.URLError,TimeoutError):pass
        time.sleep(2)
    raise AssertionError('Readiness/failover did not complete within verification window')
def redis(node,*args):
    # Arguments contain keys/commands only; the password is expanded inside the container.
    import shlex
    script='export REDISCLI_AUTH="$REDIS_PASSWORD"; exec redis-cli --no-auth-warning '+shlex.join(args)
    return compose('exec','-T',node,'sh','-ec',script).stdout.strip()

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--base',default='http://127.0.0.1:8090/api');parser.add_argument('--faults',action='store_true');parser.add_argument('--http-only',action='store_true');parser.add_argument('--output',default='docs/distributed-verification.json');args=parser.parse_args()
    base=args.base.rstrip('/')
    eventually(lambda:request(base,'/system/public-config')[0]==200,180)
    def login(name):
        code,data,_=request(base,'/users/login','POST',body={'username':name,'password':'Demo@12345'})
        record('login '+name,code==200)
        return data['data']['token']
    user=login('dev_zhangsan');other=login('dev_lisi');admin=login('dev_admin1')
    # Registry updates and each client's cache converge independently. Wait using reads;
    # never retry a write that may already have committed in another service.
    for service,path in [('core','/tickets/statistics'),('knowledge','/knowledge')]:
        hosts=set()
        def observe():
            code,_,headers=request(base,path,token=user)
            if code==200 and headers.get('x-smartticket-instance'):
                hosts.add(headers['x-smartticket-instance'])
            return len(hosts)>=2
        eventually(observe)
        record(service+' traffic uses multiple registered instances',True,instances=sorted(hosts))
    record('internal identity is not exposed by gateway',request(base,'/internal/identity',token=admin)[0]==404)
    record('internal knowledge is not exposed by gateway',request(base,'/internal/knowledge/count','POST',admin,{'admin':True})[0]==404)
    code,_,_=request(base,'/admin/users',token=user)
    record('ordinary user cannot manage accounts',code==403)
    code,_,_=request(base,'/knowledge',headers={'X-User-Id':'1','X-User-Role':'ADMIN','X-Service-Key':'forged'})
    record('forged identity headers do not authenticate',code==401)
    tag='cluster-verification-'+uuid.uuid4().hex[:10]
    body={'title':'VPN故障 '+tag,'description':'隔离集群的自动验收工单','priority':3}
    key=uuid.uuid4().hex
    def create(_):return request(base,'/tickets','POST',user,body,{'X-Request-Id':key})
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:created=list(pool.map(create,range(6)))
    ids={r[1].get('data',{}).get('id') for r in created}
    record('cross-instance concurrent create is idempotent',all(r[0]==200 for r in created) and len(ids)==1 and None not in ids)
    ticket=ids.pop()
    code,_,_=request(base,f'/tickets/{ticket}',token=other)
    record('cross-user resource access remains forbidden',code==403,ticketId=ticket)
    record('same key with different payload rejected',request(base,'/tickets','POST',user,{**body,'title':'different'}, {'X-Request-Id':key})[0]==409)
    for state in ['IN_PROGRESS','CLOSED']:
        code,_,_=request(base,f'/tickets/{ticket}/status','PATCH',admin,{'status':state});record('state transition '+state,code==200)
    code,data,_=request(base,f'/tickets/{ticket}/history',token=user)
    record('transactional history has creation and both transitions',code==200 and len(data['data'])==3)
    article={'title':'知识服务验收 '+tag,'summary':'独立数据库测试','content':'仅用于自动验收，演示发布状态和服务拆分。','category':'验收','keywords':'验证,工单','status':'DRAFT'}
    code,data,headers=request(base,'/knowledge','POST',admin,article)
    record('knowledge write routed to independent service',code==200 and headers.get('x-smartticket-service')=='knowledge',status=code,service=headers.get('x-smartticket-service'),message=data.get('message'))
    article_id=data['data']['id']
    record('draft is hidden from user',request(base,f'/knowledge/{article_id}',token=user)[0]==404)
    record('publish independent article',request(base,f'/knowledge/{article_id}','PUT',admin,{**article,'status':'PUBLISHED'})[0]==200)
    record('published article is readable by user',request(base,f'/knowledge/{article_id}',token=user)[0]==200)
    record('ordinary user cannot write knowledge',request(base,'/knowledge','POST',user,article)[0]==403)
    code,data,_=request(base,'/tickets/statistics',token=user)
    record('statistics and remote knowledge count available',code==200 and data['data']['articleCount']>=1)
    code,data,_=request(base,f'/tickets/{ticket}/knowledge-recommendations',token=user)
    record('core obtains recommendations over service boundary',code==200 and isinstance(data['data'],list))
    record('temporary article cleanup',request(base,f'/knowledge/{article_id}','DELETE',admin)[0]==200)
    if not args.http_only:
        info=redis('redis-0','cluster','info')
        record('Redis Cluster covers all 16384 slots', 'cluster_state:ok' in info and 'cluster_slots_assigned:16384' in info)
        nodes=redis('redis-0','cluster','nodes').splitlines()
        record('Redis Cluster has 3 primaries and 3 replicas',len(nodes)==6 and sum('master' in n.split()[2] for n in nodes)==3 and sum('slave' in n.split()[2] for n in nodes)==3)
        sql='SELECT count(*) FROM knowledge_article'
        core_count=compose('exec','-T','postgres','psql','-U','cluster_admin','-d','intelligent_ticket','-Atc',sql).stdout.strip()
        knowledge_count=compose('exec','-T','postgres','psql','-U','cluster_admin','-d','smartticket_knowledge','-Atc',sql).stdout.strip()
        record('knowledge records exist only in owning database',int(core_count)==0 and int(knowledge_count)>=1,coreArticles=int(core_count),knowledgeArticles=int(knowledge_count))
        forbidden=compose('exec','-T','postgres','psql','-U','core','-d','smartticket_knowledge','-Atc','SELECT 1',check=False)
        record('core database role cannot connect to knowledge database',forbidden.returncode!=0)
        status=json.loads(compose('exec','-T','rabbit-0','rabbitmqctl','cluster_status','--formatter=json').stdout)
        record('RabbitMQ cluster has 3 running members',len(status['running_nodes'])==3)
        queue=json.loads(compose('exec','-T','rabbit-0','rabbitmqctl','list_queues','name','type','leader','members','online','--formatter=json').stdout)
        audit=next(q for q in queue if q['name']=='smartticket.ticket.audit.queue')
        record('audit queue is quorum with 3 replicas',audit['type']=='quorum' and len(audit['members'])==3 and len(audit['online'])==3)
        def audited():
            code,data,_=request(base,'/operation-logs?size=100',token=admin)
            return code==200 and any(x.get('targetId')==ticket and x.get('action')=='CREATE_TICKET_ASYNC' for x in data['data']['records'])
        eventually(audited)
        record('Outbox event consumed into audit log',True)
        if args.faults:
            victim=compose('ps','-q','knowledge').stdout.splitlines()[0]
            try:
                command('docker','stop',victim)
                eventually(lambda:all(request(base,'/knowledge',token=user)[0]==200 for _ in range(5)))
                record('service discovery removes stopped knowledge instance',all(request(base,'/knowledge',token=user)[0]==200 for _ in range(5)))
            finally:command('docker','start',victim)
            primary=next(n for n in nodes if n.split()[0]==redis('redis-0','cluster','myid')) if 'master' in next(n for n in nodes if 'myself' in n).split()[2] else next(n for n in nodes if 'master' in n.split()[2])
            primary_name=primary.split()[1].split(',')[-1]
            if primary_name not in [f'redis-{i}' for i in range(6)]:
                # Hostname field is present because cluster-preferred-endpoint-type=hostname.
                raise AssertionError('Unable to identify an isolated Redis primary safely')
            survivor=next(f'redis-{i}' for i in range(6) if f'redis-{i}'!=primary_name)
            before={n.split()[0] for n in nodes if 'master' in n.split()[2]}
            try:
                compose('stop',primary_name)
                def promoted():
                    now=redis(survivor,'cluster','nodes').splitlines()
                    return 'cluster_state:ok' in redis(survivor,'cluster','info') and any('master' in n.split()[2] and n.split()[0] not in before for n in now)
                eventually(promoted)
                eventually(lambda:request(base,'/tickets/statistics',token=user)[0]==200)
                record('Redis primary loss promotes replica and application recovers',True,stopped=primary_name)
            finally:compose('start',primary_name)
            leader=audit['leader'].split('@')[-1]
            if leader not in [f'rabbit-{i}' for i in range(3)]:raise AssertionError('Unexpected Rabbit leader identity')
            try:
                compose('stop',leader)
                new_body={**body,'title':'RabbitMQ故障恢复 '+tag}
                code,new,_=request(base,'/tickets','POST',user,new_body,{'X-Request-Id':uuid.uuid4().hex})
                record('ticket creation remains available with one Rabbit node stopped',code==200)
                new_id=new['data']['id']
                def consumed():
                    code,data,_=request(base,'/operation-logs?size=100',token=admin)
                    return code==200 and any(x.get('targetId')==new_id and x.get('action')=='CREATE_TICKET_ASYNC' for x in data['data']['records'])
                eventually(consumed)
                record('quorum queue still delivers after leader loss',True,ticketId=new_id,stopped=leader)
            finally:compose('start',leader)
    report={'result':'PASS','timestamp':time.strftime('%Y-%m-%d %H:%M:%S'),'base':base,'checks':checks,'faultsRequested':args.faults,'note':'Local isolated deployment; does not establish cross-host availability or production capacity.'}
    output=ROOT/args.output;output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(f'{len(checks)} checks passed. Report: {output.relative_to(ROOT)}')
if __name__=='__main__':
    try:main()
    except Exception as e:
        (ROOT/'.runtime/distributed-verification-failure.json').write_text(json.dumps({'result':'FAIL','checks':checks,'error':str(e)},ensure_ascii=False,indent=2),encoding='utf-8')
        raise
