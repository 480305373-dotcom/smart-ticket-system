"""Real HTTP integration tests. Requires the dev backend on localhost:8080.
Only deletes the temporary tickets/articles created by this run.
"""
import json, os, urllib.request, urllib.error, uuid

BASE = os.environ.get('SMART_TICKET_API', 'http://localhost:8080')
checks = []

def request(method, path, token=None, data=None, expected=200, extra_headers=None):
    headers = {'Content-Type': 'application/json'}
    headers.update(extra_headers or {})
    if token: headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path,
        data=json.dumps(data, ensure_ascii=False).encode('utf-8') if data is not None else None,
        headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            status, body = response.status, json.load(response)
    except urllib.error.HTTPError as e:
        status, body = e.code, json.load(e)
    assert status == expected, (method,path,status,body)
    assert body['code'] == expected, (path,body)
    checks.append(f'{method} {path} => {status}')
    return body.get('data')

def login(username):
    return request('POST','/users/login',data={'username':username,'password':'Demo@12345'})

def main():
    a, b, admin = login('dev_zhangsan'), login('dev_lisi'), login('dev_admin1')
    ta,tb,tm = a['token'], b['token'], admin['token']
    ids, article_ids = [], []
    unique = uuid.uuid4().hex[:8]
    try:
        request('GET','/tickets/page',expected=401)
        request('GET','/tickets/page','invalid.jwt',expected=401)
        request('POST','/users/login',data={'username':'dev_zhangsan','password':'incorrect'},expected=401)
        request('POST','/tickets',ta,{'title':'','priority':3},400)
        request('POST','/tickets',ta,{'title':'非法优先级','priority':6},400)
        request('POST','/tickets',ta,{'title':'优先级缺失'},400)
        request('GET','/tickets/page?size=1000',ta,expected=400)
        request('GET','/tickets/page?status=INVALID',ta,expected=400)
        request('GET','/tickets/page?priority=0',ta,expected=400)
        t=request('POST','/tickets',ta,{'title':'VPN认证失败 '+unique,'description':'VPN无法连接公司网络','priority':4,'status':'CLOSED','userId':b['userId'],'createdAt':'2000-01-01T00:00:00'})
        ids.append(t['id']); path=f"/tickets/{t['id']}"
        assert t['status']=='OPEN' and t['userId']==a['userId'] and not t['createdAt'].startswith('2000')
        request('GET',path,ta)
        request('GET',path,tb,expected=403)
        request('GET',path+'/comments',tb,expected=403)
        request('POST',path+'/comments',tb,{'content':'越权留言'},403)
        request('GET',path+'/history',tb,expected=403)
        request('GET',path+'/knowledge-recommendations',tb,expected=403)
        request('PUT',path,tb,{'title':'非法更新','priority':3},403)
        request('DELETE',path,tb,expected=403)
        request('GET',f"/users/{b['userId']}",ta,expected=403)
        request('GET','/admin/anything',ta,expected=403)
        request('PATCH',path+'/status',ta,{'status':'IN_PROGRESS'},403)
        request('PUT',path,ta,{'title':t['title'],'description':'试图改状态','priority':4,'status':'CLOSED'},403)
        updated=request('PUT',path,ta,{'title':t['title'],'description':'补充VPN认证失败现象','priority':5})
        assert updated['status']=='OPEN' and updated['createdAt']==t['createdAt']
        request('POST',path+'/comments',ta,{'content':'VPN一直提示认证失败。'})
        request('POST',path+'/comments',ta,{'content':'  '},400)
        recommendations=request('GET',path+'/knowledge-recommendations',ta)
        assert recommendations and all(k['status']=='PUBLISHED' for k in recommendations)
        page=request('GET','/tickets/page?priority=5&keyword='+urllib.parse.quote(unique),ta)
        assert page['total']==1 and page['records'][0]['userId']==a['userId']
        all_page=request('GET','/tickets/page?username=dev_zhangsan',tm)
        assert all(x['username']=='dev_zhangsan' for x in all_page['records'])
        stats=request('GET','/tickets/statistics',ta)
        own=request('GET','/tickets',ta)
        assert stats['total']==len(own) and all(x['userId']==a['userId'] for x in own)
        global_stats=request('GET','/tickets/statistics',tm)
        assert global_stats['total']>stats['total'] and global_stats['userCount']>=4
        handlers=request('GET','/users/handlers',tm)
        assert any(h['id']==admin['userId'] for h in handlers)
        assert all(set(h)=={'id','username'} for h in handlers)
        request('GET','/users/handlers',ta,expected=403)
        request('PATCH',path+'/assignee',ta,{'assigneeId':admin['userId']},403)
        request('PATCH',path+'/assignee',tm,{'assigneeId':b['userId']},400)
        assigned=request('PATCH',path+'/assignee',tm,{'assigneeId':admin['userId']})
        assert assigned['assigneeUsername']=='dev_admin1' and assigned['userId']==a['userId']
        request('PATCH',path+'/assignee',tm,{'assigneeId':admin['userId']})
        queue=request('GET','/tickets/page?assignedToMe=true',tm)
        assert any(x['id']==t['id'] for x in queue['records'])
        request('GET','/tickets/page?assignedToMe=true',ta,expected=403)
        assigned=request('PATCH',path+'/assignee',tm,{'assigneeId':None})
        assert assigned['assigneeId'] is None
        assignment_history=request('GET',path+'/history',ta)
        assert len([h for h in assignment_history if h['action']=='ASSIGN'])==2
        payload={'title':'重复提交验证 '+unique,'priority':3}
        request_headers={'X-Request-Id':'smoke-'+uuid.uuid4().hex}
        first=request('POST','/tickets',ta,payload,extra_headers=request_headers)
        ids.append(first['id'])
        replay=request('POST','/tickets',ta,payload,extra_headers=request_headers)
        assert replay['id']==first['id']
        assert len(request('GET',f"/tickets/{first['id']}/history",ta))==1
        request('POST','/tickets',ta,{**payload,'title':'不同内容'},409,extra_headers=request_headers)
        request('PATCH',path+'/status',tm,{'status':'CLOSED'},400)
        request('PATCH',path+'/status',tm,{'status':'IN_PROGRESS'})
        request('POST',path+'/comments',tm,{'content':'管理员已重新同步账号权限，请重新连接。'})
        request('PATCH',path+'/status',tm,{'status':'CLOSED'})
        history=request('GET',path+'/history',ta)
        assert [h['newStatus'] for h in history if h['action']!='ASSIGN']==['OPEN','IN_PROGRESS','CLOSED']
        assert len(request('GET',path+'/comments',ta))==2
        request('PATCH',path+'/status',tm,{'status':'OPEN'},400)
        request('PATCH',path+'/assignee',tm,{'assigneeId':admin['userId']},400)
        draft={'title':'VPN回归文章 '+unique,'summary':'真实HTTP验证摘要','content':'问题现象\n认证失败\n排查步骤\n核对账号与客户端配置。','category':'回归验证','keywords':'VPN,认证','status':'DRAFT'}
        request('POST','/knowledge',ta,draft,403)
        article=request('POST','/knowledge',tm,draft);article_ids.append(article['id']);ap=f"/knowledge/{article['id']}"
        request('GET',ap,ta,expected=404)
        request('PUT',ap,ta,draft,403)
        request('DELETE',ap,ta,expected=403)
        assert all(x['status']=='PUBLISHED' for x in request('GET','/knowledge',ta)['records'])
        request('GET','/knowledge?size=101',ta,expected=400)
        draft['status']='PUBLISHED';draft['title']+='已发布'
        request('PUT',ap,tm,draft)
        assert request('GET',ap,ta)['title']==draft['title']
        request('GET','/knowledge?category='+urllib.parse.quote('回归验证'),ta)
        request('GET','/knowledge/categories',ta)
        request('POST','/users/logout',tb,{})
        request('GET','/tickets/page',tb,expected=401)
        print(f'PASS: {len(checks)} real HTTP checks, user isolation, lifecycle, comments, history, knowledge, logout.')
    finally:
        for i in ids: request('DELETE',f'/tickets/{i}',tm)
        for i in article_ids: request('DELETE',f'/knowledge/{i}',tm)
        print('Temporary tickets/articles cleaned up; existing business records preserved.')
    output={'checks':len(checks),'result':'PASS','base':BASE,'requests':checks}
    from pathlib import Path
    destination=Path(os.environ.get('SMART_TICKET_REPORT',str(Path(__file__).resolve().parents[1]/'docs/api-verification.json')))
    destination.parent.mkdir(exist_ok=True)
    destination.write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__': main()
