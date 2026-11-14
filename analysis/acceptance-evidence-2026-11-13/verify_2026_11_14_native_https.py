"""Read-only cross-check of the saved 2026-11-14 localhost HTTPS publication.

The first successful live probe already compared received HTTPS upload bytes
to local output during each pass. This verifier confirms the preserved server
events, build logs, hashes, configuration-cache verdict, and current producer
JAR without running Gradle, opening sockets or mutating project inputs.
"""

from __future__ import annotations

from collections import Counter
import hashlib
import json
from pathlib import Path
import zipfile


EVIDENCE = Path(__file__).resolve().parent
ROOT = EVIDENCE.parent.parent
RUN = ROOT / "analysis" / "local-https-maven-probe-2026-11-13" / "online-loopback-2026-11-14"
OUTPUT = EVIDENCE / "FOLLOWUP-2026-11-14-real-native-HTTPS-PUT-summary.json"
EXPECTED_PLUGIN = "13022B24ADE7C9809400FC6AEBE99E7751783A9DAE7EF8B8C53780FE70B698AD"
EXPECTED_SOURCES = "C3E69E8188B516F4F290588B3342F1D21BB24391658A75B4E26BEA3A5175B510"
EXPECTED_MANIFEST = "9B35DE9FA5E04CDD245705FB88F583CBAB59A58924C25393831961AE7D3BA06E"


def sha(blob: bytes) -> str:
    return hashlib.sha256(blob).hexdigest().upper()


def require(predicate: bool, message: str) -> None:
    if not predicate:
        raise AssertionError(message)


def main() -> None:
    require(ROOT.name == "Fabric Amper" and EVIDENCE.parent == ROOT / "analysis",
            "The verifier must run inside the approved Fabric Amper root")
    require(not OUTPUT.exists(), "Never overwrite a previously saved evidence index")
    raw_manifest = (RUN / "manifest.json").read_bytes()
    require(sha(raw_manifest) == EXPECTED_MANIFEST, "Live manifest changed after verification")
    manifest = json.loads(raw_manifest)
    require(manifest["status"] == "PASS_FOR_NATIVE_HTTPS_UPLOAD_ONLY", "Manifest status mismatch")
    require(manifest["target"] == "127.0.0.1 TLS SAN IP + Basic authentication",
            "Native Maven endpoint is not literal loopback HTTPS")
    require(manifest["proxyRejectedRequests"] == 0, "Gradle tried non-loopback proxy")
    require(manifest["externalModrinthCurseForgeUploads"] == 0, "External platform upload")
    require(len(manifest["uploads"]) == 2, "Expected two real Gradle publish passes")

    logs = []
    for index, stage in enumerate(("publish-01-with-cc", "publish-02-with-cc"), start=1):
        raw = (RUN / f"{stage}.log").read_bytes()
        output = raw.decode("utf-8")
        expected = manifest["uploads"][index - 1]
        require(expected["pass"] == index and expected["authenticatedSuccessfulPutCount"] == 25,
                "Unexpected authenticated PUT count")
        require(not expected["configurationCacheStored"] and
                not expected["configurationCacheReused"] and expected["configurationCacheDisabled"],
                "Gradle native configuration cache result differs from recorded facts")
        require("BUILD SUCCESSFUL" in output and
                "> Task :publishMavenJavaPublicationToFabricAmperRemoteRepository" in output,
                "Real native publish task did not complete")
        require("Configuration cache disabled because incompatible task was found." in output
                and "Reusing configuration cache" not in output,
                "Explicit native PasswordCredentials cache incompatibility not reproduced")
        require("--offline" not in output and "local-probe-" not in output,
                "Publish log may leak credentials or be from wrong offline run")
        logs.append({"path": (RUN / f"{stage}.log").relative_to(ROOT).as_posix(),
                     "sha256": sha(raw)})

    events = manifest["allHttpEvents"]
    put_events = [e for e in events if e["method"] == "PUT" and e["status"] == 201]
    require(len(put_events) == 50 and all(e["authenticated"] for e in put_events),
            "Expected 50 authenticated real HTTPS PUT 201 events")
    require(any(e["method"] == "GET" and e["status"] == 401 and not e["authenticated"]
                for e in events), "Basic 401 challenge not observed")
    paths = Counter(e["path"] for e in put_events)
    require(len(paths) == 25 and set(paths.values()) == {2},
            "Each Maven publication path must be uploaded exactly twice")
    for suffix in (".jar", "-sources.jar", ".pom", ".module"):
        relative = ("dev/fabricamper/localhttps/probe-api/0.1.0+26.3/"
                    "probe-api-0.1.0+26.3" + suffix)
        require(paths.get(relative) == 2, "Missing repeated primary publication: " + suffix)

    proxy = json.loads((RUN / "events-on-exit.json").read_text("utf-8"))
    require(proxy["denyProxyRequestMethods"] == [] and proxy["targetEvents"] == events,
            "Exit-time event evidence disagrees with successful live run")
    expected_local = {
        "jar": RUN / "settings-only-consumer" / "build" / "libs" / "probe-api-0.1.0+26.3.jar",
        "sources": RUN / "settings-only-consumer" / "build" / "libs" / "probe-api-0.1.0+26.3-sources.jar",
        "pom": RUN / "settings-only-consumer" / "build" / "publications" / "mavenJava" / "pom-default.xml",
        "module": RUN / "settings-only-consumer" / "build" / "publications" / "mavenJava" / "module.json",
    }
    for kind, filename in expected_local.items():
        require(sha(filename.read_bytes()) == manifest["sha256"][kind],
                "Local artifact has diverged from verified live upload: " + kind)
    with zipfile.ZipFile(expected_local["jar"]) as archive:
        require(archive.namelist().count("fabric.mod.json") == 1,
                "Uploaded JAR must have exactly one generated Fabric metadata JSON")
    consumer = RUN / "settings-only-consumer"
    require((consumer / "settings.gradle.kts").is_file() and
            (consumer / "mod.yaml").is_file() and not (consumer / "build.gradle.kts").exists()
            and not (consumer / "build.gradle").exists(), "Consumer no longer settings-only")

    producer_jar = ROOT / "build" / "libs" / "fabric-amper-0.1.0.jar"
    producer_sources = ROOT / "build" / "libs" / "fabric-amper-0.1.0-sources.jar"
    require(sha(producer_jar.read_bytes()) == EXPECTED_PLUGIN, "Producer JAR has changed")
    require(sha(producer_sources.read_bytes()) == EXPECTED_SOURCES, "Producer sources JAR changed")
    require("## 28." in (ROOT / "QA_REPORT.md").read_text("utf-8"), "QA §28 absent")
    result = {
        "status": "PASS_FOR_CHECKED_EVIDENCE_ONLY",
        "date": "2026-11-14",
        "nativeHttpsAuthenticatedPut": "PASS",
        "successfulPutCount": 50,
        "putPathsEachUploadedTwice": 25,
        "consumer": "settings.gradle.kts + mod.yaml; fabric/api",
        "verifiedLocalArtifactSha256": manifest["sha256"],
        "configCacheStoreCount": 0,
        "configCacheHitCount": 0,
        "nativeCredentialCC": "INCOMPATIBLE_WITH_GRADLE_9_7_1",
        "externalPlatformUploads": 0,
        "manifestSha256": EXPECTED_MANIFEST,
        "gradleLogs": logs,
        "producerMainJarSha256": EXPECTED_PLUGIN,
        "producerSourcesJarSha256": EXPECTED_SOURCES,
        "G1toG30Release": "NOT_APPROVED",
    }
    OUTPUT.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS: native HTTPS 50 PUT, same 25 paths twice; bytes, TLS/Basic, CC disabled verified")
    print("Evidence:", OUTPUT)


if __name__ == "__main__":
    main()
