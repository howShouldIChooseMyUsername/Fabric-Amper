"""Verify preserved Fabric Amper 2026-11-13 evidence and write final manifest.

This is deliberately read-only except one new manifest file under analysis/.
Never include local test TLS private keys, truststore, or process credentials.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
DEST = HERE / "FOLLOWUP-2026-11-13-consolidated-final-manifest.json"
FINAL = ROOT / "analysis/final-post-dryrun-correction-2026-11-13/run/manifest.json"
HTTPS = ROOT / "analysis/local-https-maven-probe-2026-11-13/run"


def check(value: bool, why: str) -> None:
    if not value:
        raise AssertionError(why)


def file_evidence(relative: str) -> dict:
    path = ROOT / relative
    check(path.is_file(), f"Missing evidence: {relative}")
    content = path.read_bytes()
    return {"path": relative, "size": len(content),
            "sha256": hashlib.sha256(content).hexdigest().upper()}


def main() -> None:
    check(HERE.parent == ROOT / "analysis", "Must run under the project analysis directory")
    check(not DEST.exists(), "Final evidence manifest exists; never overwrite")
    final = json.loads(FINAL.read_text(encoding="utf-8"))
    main_jar = ROOT / "build/libs/fabric-amper-0.1.0.jar"
    sources_jar = ROOT / "build/libs/fabric-amper-0.1.0-sources.jar"
    for path, key in ((main_jar, "mainJar"), (sources_jar, "sourcesJar")):
        check(path.is_file(), f"Latest production artifact missing: {path}")
        check(path.stat().st_size == final[key]["size"], f"Artifact size drift: {key}")
        check(hashlib.sha256(path.read_bytes()).hexdigest().upper() == final[key]["sha256"],
              f"Artifact hash drift: {key}")
    check(final["status"] == "PASS" and final["localMavenByteEqual"] is True,
          "Final producer build/local Maven publication is not PASS")
    check(final["junit"] == {"tests": 247, "skipped": 40, "failures": 0,
                            "errors": 0, "passed": 207}, "Actual default JUnit totals differ")

    selected = (
        "analysis/acceptance-evidence-2026-11-13/FOLLOWUP-G20-FOUR-MIRROR-FILES-PARALLEL-CC-1PASS.xml",
        "analysis/acceptance-evidence-2026-11-13/FOLLOWUP-POST-REMOTE-DRYRUN-CC-RESTORED-1PASS.xml",
    )
    for relative in selected:
        xml = ET.parse(ROOT / relative).getroot()
        check(int(xml.attrib["tests"]) == 1 and int(xml.attrib["failures"]) == 0 and
              int(xml.attrib["skipped"]) == 0 and int(xml.attrib["errors"]) == 0,
              f"Selected TestKit result not 1 PASS: {relative}")
    failed = HERE / "FOLLOWUP-POST-HTTPS-REMOTE-CC-DRYRUN-REGRESSION-1FAIL.xml"
    fail_xml = ET.parse(failed).getroot()
    check(int(fail_xml.attrib["failures"]) == 1, "Historical dry-run CC regression FAIL missing")

    hostile_relative = "analysis/acceptance-evidence-2026-11-13/FOLLOWUP-G11-G30-hostile-YAML-CC-preservation-PASS.json"
    resource_relative = "analysis/acceptance-evidence-2026-11-13/FOLLOWUP-G11-resource-FA001-FA008-FA009-FA012-CC-PASS.json"
    hostile = json.loads((ROOT / hostile_relative).read_text(encoding="utf-8"))
    resource = json.loads((ROOT / resource_relative).read_text(encoding="utf-8"))
    check(hostile["status"] == resource["status"] == "PASS", "G11/G30 inputs not PASS")
    check(hostile["cases"] == 11 and resource["cases"] == 6, "Wrong adversarial-case totals")

    https_log = (HTTPS / "01-publish-cc-store.log").read_text(encoding="utf-8")
    server = json.loads((HTTPS / "server-events-on-exit.json").read_text(encoding="utf-8"))
    check("Cannot upload to 'https://127.0.0.1:" in https_log and "in offline mode." in https_log,
          "Native offline localhost upload blocker not reproduced")
    check("Configuration cache disabled because incompatible task was found" in https_log,
          "Native Gradle CC incompatible-task result not reproduced")
    check(server["loopbackOnly"] and server["authenticatedSuccessfulPutCount"] == 0,
          "The local server unexpectedly received an authenticated successful PUT")
    check(len(server["events"]) == 1 and server["events"][0]["status"] == 401,
          "Basic 401 challenge evidence differs")
    reports = sorted((HTTPS / "settings-only-consumer/build/reports/configuration-cache")
                     .rglob("configuration-cache-report.html"))
    check(reports, "Gradle native CC incompatibility report missing")
    check(any("Explicit credentials are unsupported with the Configuration Cache"
              in report.read_text(encoding="utf-8") for report in reports),
          "Native explicit credentials incompatibility reason not found")
    archive_path = ROOT / final["junitArchive"]
    check(hashlib.sha256(archive_path.read_bytes()).hexdigest().upper() ==
          final["junitArchiveSha256"], "Final JUnit archive hash drift")
    with zipfile.ZipFile(archive_path) as z:
        check(len(z.namelist()) == 46 and all(x.startswith("TEST-") for x in z.namelist()),
              "Final JUnit archive is not the expected 46 suites")

    files = [
        hostile_relative,
        resource_relative,
        *selected,
        "analysis/acceptance-evidence-2026-11-13/FOLLOWUP-POST-HTTPS-REMOTE-CC-DRYRUN-REGRESSION-1FAIL.xml",
        "analysis/final-post-dryrun-correction-2026-11-13/run/manifest.json",
        final["junitArchive"],
        "analysis/local-https-maven-probe-2026-11-13/run/01-publish-cc-store.log",
        "analysis/local-https-maven-probe-2026-11-13/run/server-events-on-exit.json",
        "README.md", "QA_REPORT.md", "fabric-amper.schema.json",
    ]
    check("## 26." in (ROOT / "QA_REPORT.md").read_text(encoding="utf-8"),
          "New QA §26 was not appended")
    manifest = {
        "date": "2026-11-13",
        "overallReleaseApproval": "NOT APPROVED",
        "producer": final,
        "confirmedSelected": {
            "G11G30HostileYamlCases": 11,
            "G11ResourcesDiagnosticCases": 6,
            "G20FourFileParallelRecoveryJunitPass": 1,
            "remoteMavenDryRunCcStoreHitJunitPass": 1,
        },
        "remoteMavenActualAction": {
            "tlsLoopbackBasic401Challenge": True,
            "nativePublishAttemptedOffline": True,
            "authenticatedSuccessfulPutCount": 0,
            "nativeOfflineHttpUploadBlocked": True,
            "explicitNativeCredentialsCcIncompatible": True,
            "authenticatedRemotePublishCcHit": "NOT VERIFIED",
        },
        "manualGates": {
            "intellijGuiImportAndSync": "NOT VERIFIED",
            "dedicatedServerCompleteStartupAfterEula": "NOT RUN",
            "legacyGameLoaderRuntime": "NOT RUN",
            "modrinthCurseforgeExternalUploads": 0,
        },
        "files": [file_evidence(name) for name in files],
    }
    DEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("PASS evidence integrity checks; release NOT APPROVED")
    print("Manifest:", DEST.relative_to(ROOT).as_posix())


if __name__ == "__main__":
    main()
