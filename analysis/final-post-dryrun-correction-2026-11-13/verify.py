"""Final offline producer build + real local marker publish, with preserved evidence.

Use only after the localhost HTTPS remote-task production fix has been applied.
Never touches other projects, a global Maven repository or any remote service.
It refuses to replace an earlier run directory or archived report.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
EVIDENCE = ROOT / "analysis" / "acceptance-evidence-2026-11-13"
RUN = HERE / "run"
GRADLE = Path(
    r"C:\Users\user\.gradle\wrapper\dists\gradle-9.7.1-bin"
    r"\1w1c7tv4s851m17nbqdsro2tv\gradle-9.7.1\bin\gradle.bat"
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def execute(label: str, args: list[str]) -> str:
    cmd = [str(GRADLE), *args, "--offline", "--console=plain", "--no-daemon"]
    print(f"Running {label}: Gradle 9.7.1, offline", flush=True)
    process = subprocess.run(cmd, cwd=ROOT, stdout=subprocess.PIPE,
                             stderr=subprocess.STDOUT, encoding="utf-8",
                             errors="replace", timeout=900, check=False)
    log = RUN / (label + ".log")
    log.write_text(process.stdout, encoding="utf-8")
    require(process.returncode == 0 and "BUILD SUCCESSFUL" in process.stdout,
            f"{label} failed (exit={process.returncode}); see {log}")
    print(f"{label}: PASS", flush=True)
    return process.stdout


def main() -> None:
    require(GRADLE.is_file() and ROOT.name == "Fabric Amper" and HERE.parent == ROOT / "analysis",
            "Use only the approved Fabric Amper project and Gradle binary")
    require(not RUN.exists(), "Prior final audit exists; do not overwrite it")
    require(not (EVIDENCE / "FOLLOWUP-POST-REMOTE-DRYRUN-FINAL-JUnit-XML.zip").exists(),
            "JUnit XML archive already exists; do not overwrite history")
    RUN.mkdir(parents=True)

    build_log = execute("01-clean-build-validatePlugins", [
        "clean", "build", "validatePlugins", "--no-build-cache", "--rerun-tasks",
        "-Pkotlin.incremental=false",
    ])
    require("Task :validatePlugins" in build_log, "Gradle did not execute validatePlugins")
    xml_dir = ROOT / "build" / "test-results" / "test"
    suites = sorted(xml_dir.glob("TEST-*.xml"))
    require(len(suites) >= 40, "Expected the ordinary JUnit suite set, no selected opt-in tests")
    totals = dict(tests=0, skipped=0, failures=0, errors=0)
    for path in suites:
        parsed = ET.parse(path).getroot()
        require(parsed.tag == "testsuite", f"Unexpected JUnit XML root: {path.name}")
        for metric in totals:
            totals[metric] += int(parsed.attrib.get(metric, "0"))
    totals["passed"] = totals["tests"] - totals["skipped"] - totals["failures"] - totals["errors"]
    require(totals["failures"] == totals["errors"] == 0 and totals["passed"] > 0,
            "Default JUnit build is not clean")
    main_jar = ROOT / "build" / "libs" / "fabric-amper-0.1.0.jar"
    sources_jar = ROOT / "build" / "libs" / "fabric-amper-0.1.0-sources.jar"
    schema = ROOT / "fabric-amper.schema.json"
    for path in (main_jar, sources_jar, schema):
        require(path.is_file(), f"Final artifact missing: {path}")
    with zipfile.ZipFile(main_jar) as z:
        entries = z.namelist()
        require(len(entries) == len(set(entries)), "Duplicate plugin JAR entry")
        marker_root = "META-INF/gradle-plugins/"
        for marker in ("dev.fabric-amper.settings.properties", "dev.fabric-amper.project.properties"):
            require(entries.count(marker_root + marker) == 1, f"Plugin marker missing: {marker}")

    archive = EVIDENCE / "FOLLOWUP-POST-REMOTE-DRYRUN-FINAL-JUnit-XML.zip"
    with zipfile.ZipFile(archive, mode="x", compression=zipfile.ZIP_DEFLATED) as z:
        for path in suites:
            z.write(path, arcname=path.name)

    execute("02-publish-current-plugin-local-G29", [
        "publishAllPublicationsToG29ProbeRepository", "-PfabricAmperProbeMavenRepo=true",
    ])
    local_repo = ROOT / "build" / "g29-probe" / "maven-repo"
    base = local_repo / "dev" / "fabricamper" / "fabric-amper" / "0.1.0"
    published_main = base / "fabric-amper-0.1.0.jar"
    published_sources = base / "fabric-amper-0.1.0-sources.jar"
    require(published_main.is_file() and published_sources.is_file(), "Local binary publication missing")
    require(published_main.read_bytes() == main_jar.read_bytes() and
            published_sources.read_bytes() == sources_jar.read_bytes(),
            "Local G29 repo does not contain the freshly built plugin")
    for plugin_id in ("dev.fabric-amper.settings", "dev.fabric-amper.project"):
        marker_dir = local_repo / "dev" / "fabric-amper" / plugin_id.split(".")[-1] / f"{plugin_id}.gradle.plugin" / "0.1.0"
        require((marker_dir / f"{plugin_id}.gradle.plugin-0.1.0.pom").is_file(),
                f"No local plugin marker POM: {plugin_id}")

    result = {
        "status": "PASS",
        "producer": "Gradle 9.7.1; JDK25; offline; --no-build-cache; --rerun-tasks",
        "validationTask": "validatePlugins executed",
        "junitSuiteCount": len(suites),
        "junit": totals,
        "junitArchive": archive.relative_to(ROOT).as_posix(),
        "junitArchiveSha256": sha256(archive),
        "mainJar": {"size": main_jar.stat().st_size, "sha256": sha256(main_jar)},
        "sourcesJar": {"size": sources_jar.stat().st_size, "sha256": sha256(sources_jar)},
        "schema": {"size": schema.stat().st_size, "sha256": sha256(schema)},
        "uniquePluginEntries": len(entries),
        "localMavenByteEqual": True,
        "externalUploads": False,
        "executionLogs": ["01-clean-build-validatePlugins.log", "02-publish-current-plugin-local-G29.log"],
    }
    (RUN / "manifest.json").write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"PASS final JUnit: {len(suites)} suites, {totals}", flush=True)
    print(f"PASS current plugin SHA256: {result['mainJar']['sha256']}", flush=True)


if __name__ == "__main__":
    main()
