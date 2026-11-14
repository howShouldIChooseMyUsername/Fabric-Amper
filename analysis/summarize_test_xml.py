"""Read-only snapshot of the current Gradle JUnit XML outputs.

This is an evidence aid, not a test invocation. It never deletes or rewrites
the files and does not infer which command created them.
"""

from datetime import datetime, timezone
from pathlib import Path
from xml.etree import ElementTree


root = Path(__file__).resolve().parent.parent
folder = root / "build" / "test-results" / "test"
totals = dict(tests=0, passed=0, skipped=0, failures=0, errors=0)
entries = sorted(folder.glob("TEST-*.xml"))
print("SNAPSHOT", datetime.now(timezone.utc).isoformat())
print("XML_DIR", folder)
print("SUITES", len(entries))
for path in entries:
    try:
        suite = ElementTree.parse(path).getroot()
        count = int(suite.attrib["tests"])
        skipped = int(suite.attrib.get("skipped", 0))
        failures = int(suite.attrib.get("failures", 0))
        errors = int(suite.attrib.get("errors", 0))
        passed = count - skipped - failures - errors
        assert passed >= 0
        totals["tests"] += count
        totals["passed"] += passed
        totals["skipped"] += skipped
        totals["failures"] += failures
        totals["errors"] += errors
        if failures or errors or "Publish" in path.name or "G9" in path.name:
            print("SUITE", path.name, f"{count=} {passed=} {skipped=} {failures=} {errors=}",
                  "timestamp=" + str(suite.attrib.get("timestamp")))
    except (OSError, ElementTree.ParseError, ValueError, KeyError, AssertionError) as exc:
        print("XML_INCOMPLETE", path.name, type(exc).__name__)
print("TOTAL", totals)
