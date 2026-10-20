"""Verify only the dedicated local smartticket Kubernetes cluster."""
import json
import subprocess
import time
import urllib.request
from pathlib import Path

from distributed_smoke import ROOT,request,eventually,command

CONFIG=ROOT/'.runtime/kubeconfig'
K=['kubectl','--kubeconfig',str(CONFIG),'-n','smartticket']
checks=[]
def kubectl(*args):return command(*K,*args,timeout=420).stdout
def get(kind):return json.loads(kubectl('get',kind,'-o','json'))['items']
def record(name,passed,**details):
    checks.append({'name':name,'passed':bool(passed),**details})
    if not passed:raise AssertionError(name)
    print('PASS: '+name,flush=True)
def main():
    context=command('kubectl','--kubeconfig',str(CONFIG),'config','current-context').stdout.strip()
    if context not in ('kind-smartticket','k3d-smartticket'):raise RuntimeError('Refusing to modify a different Kubernetes context')
    kubectl('wait','--for=condition=complete','job/redis-cluster-init','--timeout=240s')
    for name in ['registry','knowledge','backend','gateway','frontend']:
        kubectl('rollout','status','deployment/'+name,'--timeout=360s')
    record('dedicated Kubernetes nodes are ready',all(any(c['type']=='Ready' and c['status']=='True' for c in n['status']['conditions']) for n in get('nodes')))
    pods=get('pods')
    for app,count in [('redis',6),('rabbit',3),('backend',2),('knowledge',2),('gateway',2),('frontend',2)]:
        selected=[p for p in pods if p['metadata'].get('labels',{}).get('app')==app]
        record(app+' replicas ready',len(selected)==count and all(any(c['type']=='Ready' and c['status']=='True' for c in p['status'].get('conditions',[])) for p in selected),replicas=count)
    rabbit=[p for p in pods if p['metadata'].get('labels',{}).get('app')=='rabbit']
    record('Rabbit pods occupy three Kubernetes nodes',len({p['spec']['nodeName'] for p in rabbit})==3)
    claims=get('pvc')
    record('stateful volumes are bound',len(claims)==10 and all(c['status'].get('phase')=='Bound' for c in claims),count=len(claims))
    info=kubectl('exec','redis-0','--','sh','-ec','REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli cluster info')
    record('Kubernetes Redis Cluster has full slot coverage','cluster_state:ok' in info and 'cluster_slots_assigned:16384' in info)
    def quorum_ready():
        result=json.loads(kubectl('exec','rabbit-0','--','rabbitmqctl','list_queues','name','type','members','online','--formatter=json'))
        return any(q['name']=='smartticket.ticket.audit.queue' and q['type']=='quorum' and len(q['members'])==3 and len(q['online'])==3 for q in result)
    eventually(quorum_ready)
    record('Kubernetes quorum queue has 3 online members',True)
    # Full application checks also assert routing across both instances.
    workflow=command('python',str(ROOT/'scripts/distributed_smoke.py'),'--http-only','--base','http://127.0.0.1:8091/api','--output','docs/kubernetes-http-verification.json',check=False,timeout=180)
    print(workflow.stdout,flush=True)
    if workflow.returncode:
        print(workflow.stderr,flush=True)
        raise RuntimeError('Kubernetes HTTP verification failed; child diagnostic output printed above')
    record('full HTTP workflow passes through Kubernetes gateway',True)
    base='http://127.0.0.1:8091/api'
    _,session,_=request(base,'/users/login','POST',body={'username':'dev_zhangsan','password':'Demo@12345'})
    token=session['data']['token']
    victim=next(p['metadata']['name'] for p in get('pods') if p['metadata'].get('labels',{}).get('app')=='knowledge')
    kubectl('delete','pod',victim,'--wait=false')
    kubectl('rollout','status','deployment/knowledge','--timeout=180s')
    eventually(lambda:request(base,'/knowledge',token=token)[0]==200)
    record('deleted knowledge pod is automatically replaced',victim not in {p['metadata']['name'] for p in get('pods')})
    kubectl('rollout','restart','deployment/knowledge')
    deadline=time.monotonic()+180;successful=0
    while time.monotonic()<deadline:
        code,_,_=request(base,'/knowledge',token=token)
        if code!=200:raise AssertionError(f'Knowledge API failed during rolling update: HTTP {code}')
        successful+=1
        deployment=json.loads(kubectl('get','deployment/knowledge','-o','json'))
        status=deployment.get('status',{})
        if status.get('observedGeneration',0)>=deployment['metadata']['generation'] and status.get('updatedReplicas',0)==2 and status.get('readyReplicas',0)==2 and status.get('replicas',0)==2:break
        time.sleep(2)
    else:raise AssertionError('Rolling update timed out')
    record('rolling update retained HTTP availability',True,samples=successful)
    (ROOT/'docs/kubernetes-verification.json').write_text(json.dumps({'result':'PASS','context':context,'checks':checks,'scope':'Local Kubernetes nodes run on one Docker host; not a cross-host or zone failure exercise.'},ensure_ascii=False,indent=2),encoding='utf-8')
    print(f'{len(checks)} Kubernetes checks passed')
if __name__=='__main__':
    try:main()
    except Exception as e:
        (ROOT/'.runtime/kubernetes-verification-failure.json').write_text(json.dumps({'result':'FAIL','checks':checks,'error':str(e)},ensure_ascii=False,indent=2),encoding='utf-8')
        raise
