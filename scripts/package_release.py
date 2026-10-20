"""Build a portable source + binaries release, excluding local secrets, backups and personal documents."""
from pathlib import Path
import argparse,subprocess,zipfile,hashlib,json,datetime
from verify_distributed_artifacts import verify

verify()

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--product',action='store_true',help='Require complete local formal product acceptance before packaging')
args=parser.parse_args()
evidence={}
if args.product:
    for name in ('product-build-verification.json','capacity-verification.json',
                 'distributed-restore-verification.json','formal-verification.json',
                 'formal-restore-verification.json','formal-ui-verification.json'):
        path=root/'docs'/name
        report=json.loads(path.read_text(encoding='utf-8-sig'))
        assert report.get('result')=='PASS','Product acceptance has not passed: '+name
        evidence[name]=hashlib.sha256(path.read_bytes()).hexdigest()
        if name=='product-build-verification.json':
            for artifact,expected in report['artifactsSha256'].items():
                assert hashlib.sha256((root/artifact).read_bytes()).hexdigest()==expected,'Build changed after acceptance: '+artifact
        if name=='formal-verification.json':
            assert report.get('aiVerified'),'Complete local product requires the real RAG acceptance'
names=subprocess.check_output(['git','-c',f'safe.directory={root.as_posix()}','ls-files','-z','--cached'],cwd=root).decode('utf-8').split('\0')
files={}
for name in filter(None,names):
    p=root/name
    if not p.is_file() or p.suffix.lower() in {'.docx','.doc','.pdf','.dump','.log','.pid'}:continue
    if name.startswith(('.runtime/','artifacts/','.git/','tmp/','output/')) or name=='.env':continue
    if name.startswith('backend/config/') and not name.endswith('.example'):continue
    files[name]=p
modules=['backend','knowledge-service','gateway','registry']
for module in modules:
    jar=root/f'{module}/target/{module}-0.0.1-SNAPSHOT.jar'
    assert jar.exists(),f'Build {module} before packaging the release.'
    files[f'{module}/target/{module}-0.0.1-SNAPSHOT.jar']=jar
assert (root/'frontend/dist/index.html').exists(),'Run the frontend build first.'
for p in (root/'frontend/dist').rglob('*'):
    if p.is_file():files[p.relative_to(root).as_posix()]=p
manifest={name:hashlib.sha256(p.read_bytes()).hexdigest() for name,p in sorted(files.items())}
destination=root/'artifacts/smart-ticket-system-complete.zip'
destination.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
    for name,p in sorted(files.items()):archive.write(p,name)
    archive.writestr('RELEASE-MANIFEST.json',json.dumps({'edition':'stable-local-formal' if args.product else 'enterprise-and-distributed','createdAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'acceptanceSha256':evidence,'sha256':manifest},indent=2))
with zipfile.ZipFile(destination) as archive:
    assert archive.testzip() is None,'Release ZIP integrity failure.'
    assert '.env' not in archive.namelist()
    assert not any(n.startswith('.runtime/') or n.endswith('.docx') for n in archive.namelist())
checksum=hashlib.sha256(destination.read_bytes()).hexdigest()
destination.with_suffix('.zip.sha256').write_text(checksum+'  '+destination.name+'\n',encoding='utf-8')
print(f'PASS: {destination.name}, {len(files)} files, {destination.stat().st_size/1024/1024:.1f} MiB, ZIP verified.')
