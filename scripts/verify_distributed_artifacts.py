"""Reject stale nested domain code before starting or packaging distributed mode."""
import io
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]


def verify():
    shared = ROOT / 'backend/target/backend-0.0.1-SNAPSHOT-library.jar'
    with ZipFile(shared) as library, ZipFile(ROOT / 'knowledge-service/target/knowledge-service-0.0.1-SNAPSHOT.jar') as service:
        nested = [n for n in service.namelist() if n.startswith('BOOT-INF/lib/backend-') and n.endswith('-library.jar')]
        if len(nested) != 1:
            raise RuntimeError('Knowledge service must contain exactly one shared domain library')
        with ZipFile(io.BytesIO(service.read(nested[0]))) as packaged:
            classes = [n for n in library.namelist() if n.endswith('.class')]
            if not classes:
                raise RuntimeError('Shared domain library contains no compiled classes')
            for name in classes:
                current = (ROOT / 'backend/target/classes' / name).read_bytes()
                if current != library.read(name) or current != packaged.read(name):
                    raise RuntimeError('Stale shared code in executable package: ' + name + '; rebuild the Maven reactor')
    print(f'PASS: {len(classes)} shared classes match the knowledge executable package')


if __name__ == '__main__':
    verify()
