"""Record successful build artifacts; called after all verify-product.ps1 checks pass."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
for module in ("backend", "knowledge-service"):
    reports = list((ROOT / module / "target/surefire-reports").glob("TEST-*.xml"))
    if not reports:
        raise RuntimeError("Missing backend test evidence: " + module)
    for path in reports:
        suite = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.attrib.get(key, 0))
assert totals["tests"] >= 43 and not any(totals[key] for key in ("failures", "errors", "skipped")), totals
files = [ROOT / module / "target" / (module + "-0.0.1-SNAPSHOT.jar")
         for module in ("backend", "knowledge-service", "gateway", "registry")]
files += [path for path in (ROOT / "frontend/dist").rglob("*") if path.is_file()]
assert (ROOT / "frontend/dist/index.html").is_file()
report = {"result": "PASS", "createdAt": datetime.now(timezone.utc).isoformat(),
          "backend": totals, "frontend": {"tests": "PASS", "build": "PASS"},
          "artifactsSha256": {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
                              for path in files}}
(ROOT / "docs/product-build-verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print("PASS: backend tests and four executable packages plus frontend assets recorded")
