"""Check final Fabric Amper evidence and write a non-overwriting completion index.

No Gradle, network, user profiles or other projects are accessed. The artifact
hashes and selected JUnit XML are checked against the present project build.
"""

from __future__ import annotations

from hashlib import sha256
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile


EVID = Path(__file__).resolve().parent
ROOT = EVID.parent.parent
FINAL = ROOT / "analysis" / "final-post-missing-credential-fix-2026-11-13" / "run" / "manifest.json"
NEGATIVE = ROOT / "analysis" / "local-https-maven-probe-2026-11-13" / "negative-contract-v2-2026-11-13" / "manifest.json"
OUT = EVID / "FOLLOWUP-FINAL-2026-11-13-remaining-verification-index.json"


def require(ok: bool, message: str) -> None:
    if not ok:
        raise AssertionError(message)


def digest(path: Path) -> str:
    return sha256(path.read_bytes()).hexdigest().upper()


def included(path: Path) -> dict[str, object]:
    require(path.is_file() and path.resolve().is_relative_to(ROOT.resolve()), f"Missing evidence: {path}")
    return {"path": path.relative_to(ROOT).as_posix(), "bytes": path.stat().st_size,
            "sha256": digest(path)}


def xml_exact_one_pass(name: str) -> dict[str, object]:
    path = EVID / name
    node = ET.parse(path).getroot()
    require(node.tag == "testsuite" and
            all(int(node.attrib.get(k, "0")) == v for k, v in
                (("tests", 1), ("skipped", 0), ("failures", 0), ("errors", 0))),
            f"Selected JUnit is not exactly 1 PASS: {name}")
    return included(path)


def main() -> None:
    require(ROOT.name == "Fabric Amper" and not OUT.exists(),
            "Wrong project or completion index already exists; preserve previous evidence")
    final = json.loads(FINAL.read_text(encoding="utf-8"))
    negative = json.loads(NEGATIVE.read_text(encoding="utf-8"))
    require(final.get("status") == "PASS" and final["junitSuiteCount"] == 46,
            "Current baseline not proven by final manifest")
    require(final["junit"] == {"tests": 247, "skipped": 40,
                               "failures": 0, "errors": 0, "passed": 207},
            "Unexpected baseline JUnit counts")
    require(negative.get("status") == "PASS" and
            all(value["status"] == "EXPECTED_FAILURE" and value["fa"] == "FA003"
                and value["exit"] != 0 and value["offline"]
                for value in negative["cases"].values()) and
            len(negative["cases"]) == 2,
            "Two real offline remote Maven negatives must be expected FA003 failures")

    actual_main = ROOT / "build" / "libs" / "fabric-amper-0.1.0.jar"
    actual_sources = ROOT / "build" / "libs" / "fabric-amper-0.1.0-sources.jar"
    actual_schema = ROOT / "fabric-amper.schema.json"
    for path, field in ((actual_main, "mainJar"), (actual_sources, "sourcesJar"),
                        (actual_schema, "schema")):
        require(path.stat().st_size == final[field]["size"] and
                digest(path) == final[field]["sha256"], f"Stale final artifact: {field}")
    with zipfile.ZipFile(actual_main) as z:
        names = z.namelist()
        require(len(names) == 123 and len(set(names)) == 123,
                "Plugin JAR entry count or duplicate changed")

    yaml = json.loads((EVID / "FOLLOWUP-G11-G30-hostile-YAML-CC-preservation-PASS.json")
                      .read_text(encoding="utf-8"))
    resources = json.loads((EVID / "FOLLOWUP-G11-resource-FA001-FA008-FA009-FA012-CC-PASS.json")
                           .read_text(encoding="utf-8"))
    require(yaml["status"] == resources["status"] == "PASS" and
            yaml["cases"] == 11 and resources["cases"] == 6,
            "Hostile YAML/resource evidence counts changed")
    require(final["junitArchiveSha256"] == digest(ROOT / final["junitArchive"]),
            "Default JUnit ZIP mutated")
    with zipfile.ZipFile(ROOT / final["junitArchive"]) as z:
        require(len([n for n in z.namelist() if n.endswith(".xml")]) == 46,
                "Missing default JUnit suites in frozen archive")

    selected = {
        "G20FourMirrorConcurrentRepair": xml_exact_one_pass(
            "FOLLOWUP-G20-FOUR-MIRROR-FILES-PARALLEL-CC-1PASS.xml"),
        "remoteMavenDryRunCCFinal": xml_exact_one_pass(
            "FOLLOWUP-POST-REMOTE-FA003-DRYRUN-CC-1PASS.xml"),
    }
    result = {
        "status": "PASS_FOR_CHECKED_EVIDENCE_ONLY",
        "currentDate": "2026-11-13",
        "product": "dev.fabric-amper.settings 0.1.0",
        "sourceRoot": "Fabric Amper",
        "baseline": final["junit"],
        "finalMainJar": included(actual_main),
        "finalSourcesJar": included(actual_sources),
        "schema": included(actual_schema),
        "frozenDefaultJUnitXml": included(ROOT / final["junitArchive"]),
        "selectedJUnit": selected,
        "adversarialInvalidInputCases": 17,
        "adversarialEvidence": [
            included(EVID / "FOLLOWUP-G11-G30-hostile-YAML-CC-preservation-PASS.json"),
            included(EVID / "FOLLOWUP-G11-resource-FA001-FA008-FA009-FA012-CC-PASS.json"),
        ],
        "offlineRemoteMavenNegativeCases": 2,
        "remoteMavenNegativeEvidence": included(NEGATIVE),
        "finalProducerManifest": included(FINAL),
        "externalPlatformUploads": 0,
        "nativeHttpsActualPut": "NOT_VERIFIED; Gradle --offline blocked, explicit PasswordCredentials disabled CC",
        "intelliJGUI": "NOT_VERIFIED; Tooling model only",
        "dedicatedServerFullBoot": "NOT_VERIFIED; EULA not accepted",
        "releaseStatus": "NOT_APPROVED",
        "skipIsPass": False,
    }
    OUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n",
                   encoding="utf-8")
    print("PASS final grounded completion index: 207 PASS / 40 SKIP / 0 FAIL, 17 input + 2 remote negatives")
    print(OUT.relative_to(ROOT).as_posix())


if __name__ == "__main__":
    main()
