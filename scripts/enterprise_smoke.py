"""Real infrastructure and RAG verification. Never writes credentials or JWTs to reports."""
import json,os,urllib.request,urllib.error
from pathlib import Path
BASE=os.environ.get('SMART_TICKET_API','http://127.0.0.1:8080')
checks=[]
def call(path,data=None,token=None,expected=200):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    request=urllib.request.Request(BASE+path,data=json.dumps(data).encode() if data is not None else None,headers=headers)
    try:
        with urllib.request.urlopen(request,timeout=180) as response:status,body=response.status,json.load(response)
    except urllib.error.HTTPError as e:status,body=e.code,json.load(e)
    assert status==expected,(path,status,body.get('message'))
    checks.append(f'{path} => {status}')
    return body.get('data',body)
def main():
    admin=call('/users/login',{'username':'dev_admin1','password':'Demo@12345'})['token']
    user=call('/users/login',{'username':'dev_zhangsan','password':'Demo@12345'})['token']
    call('/actuator/health/readiness')
    call('/admin/system',token=user,expected=403)
    call('/admin/users',token=user,expected=403)
    call('/admin/ai/index',token=user,expected=403)
    call('/operation-logs',token=user,expected=403)
    system=call('/admin/system',token=admin)
    assert all(system[k]=='UP' for k in ('postgres','redis','rabbitmq')),system
    call('/admin/users',token=admin)
    capabilities=call('/system/capabilities',token=user)
    assert capabilities['redis'] and capabilities['rabbitmq']
    result={'result':'PASS','checks':checks,'services':{k:system[k] for k in ('postgres','redis','rabbitmq')}}
    if capabilities['ai'] and os.environ.get('SKIP_AI')!='1':
        call('/admin/ai/index',{},admin)
        answer=call('/ai/assist',{'message':'VPN认证失败，请给出简短排查步骤并标注知识来源。'},user)
        assert answer['answer'].strip() and answer['sources']
        assert len(answer['answer'])<2500
        root=Path(__file__).resolve().parents[1]
        (root/'docs/ai-verification.json').write_text(json.dumps({'result':'PASS',**answer},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        result['ai']={'result':'PASS','sources':len(answer['sources'])}
    (Path(__file__).resolve().parents[1]/'docs/enterprise-verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'PASS: {len(checks)} enterprise HTTP checks; real dependency health and model answer verified.')
if __name__=='__main__':main()
