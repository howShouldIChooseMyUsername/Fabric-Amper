"""One-shot, strictly offline negative credential/HTTPS scheme acceptance.

Uses the final local plugin marker, project-owned Gradle cache and disposable
settings-only consumers. No server or external network/API upload can occur.
All first-run evidence is retained; never overwrites a previous probe.
"""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess

import verify as fixture


HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
RUN = HERE / "negative-contract-v2-2026-11-13"


def check(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest().upper()


def gradle(stage: str, consumer: Path, env: dict[str, str], task: str) -> dict[str, object]:
    cmd = fixture.gradle_command(fixture.DEFAULT_GRADLE) + [
        task, "--offline", "--no-daemon", "--console=plain", "--no-build-cache"
    ]
    proc = subprocess.run(cmd, cwd=consumer, env=env, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, encoding="utf-8",
                          errors="replace", timeout=240, check=False)
    output = proc.stdout
    (RUN / f"{stage}.log").write_text(output, encoding="utf-8")
    check(proc.returncode != 0 and "BUILD FAILED" in output,
          f"{stage}: expected a real failing Gradle task, exit={proc.returncode}")
    check("--offline" in cmd and "BUILD SUCCESSFUL" not in output,
          f"{stage}: offline or failure boundary was not preserved")
    check("FabricAmper FA003" in output,
          f"{stage}: expected plugin's FA003 diagnostic, not generic Gradle failure")
    return {"status": "EXPECTED_FAILURE", "exit": proc.returncode,
            "fa": "FA003", "logSha256": sha(output.encode("utf-8")),
            "task": task, "offline": True}


def main() -> None:
    check(ROOT.name == "Fabric Amper" and HERE.parent == ROOT / "analysis",
          "The approved workspace is required")
    check(not RUN.exists(), "Probe evidence already exists; never overwrite")
    fixture.require_local_marker_repo()
    check(fixture.DEFAULT_GRADLE.is_file() and (ROOT / ".h" / "caches" / "modules-2").is_dir(),
          "Existing short offline Gradle cache and 9.7.1 installation are required")
    RUN.mkdir()
    env = dict(os.environ)
    for key in list(env):
        if any(fragment in key.upper() for fragment in
               ("TOKEN", "PASSWORD", "SECRET", "CREDENTIAL")):
            env.pop(key, None)
    for key in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "GRADLE_OPTS"):
        env.pop(key, None)
    env["GRADLE_USER_HOME"] = str(ROOT / ".h")
    env.pop("GRADLE_RO_DEP_CACHE", None)
    env.pop(fixture.USERNAME_VAR, None)
    env.pop(fixture.PASSWORD_VAR, None)

    missing = RUN / "missing-credentials"
    fixture.create_consumer(missing, "https://127.0.0.1:1/maven")
    authored = (missing / "mod.yaml").read_bytes()
    print("1/2: actual native Maven remote task, missing env names, offline", flush=True)
    result_missing = gradle("01-missing-credentials", missing, env, fixture.PUBLISH_TASK)
    log_missing = (RUN / "01-missing-credentials.log").read_text(encoding="utf-8")
    check("publish.maven" in log_missing and
          "both Maven credential environment variables must be set" in log_missing,
          "Missing remote credentials must fail in plugin's publish-specific FA003 action")
    check((missing / "mod.yaml").read_bytes() == authored,
          "Native credential failure unexpectedly changed authored YAML")
    result_missing["yamlPreservedSha256"] = sha(authored)

    invalid = RUN / "http-url-rejection"
    fixture.create_consumer(invalid, "http://127.0.0.1:1/maven")
    authored_invalid = (invalid / "mod.yaml").read_bytes()
    print("2/2: reject non-TLS Maven URL during configuration, offline", flush=True)
    result_http = gradle("02-http-url-rejection", invalid, env, "help")
    log_http = (RUN / "02-http-url-rejection.log").read_text(encoding="utf-8")
    check("publish.maven.url" in log_http or "publishing.repository.url" in log_http,
          "Expected precise Maven URL path in FA003 failure")
    check((invalid / "mod.yaml").read_bytes() == authored_invalid,
          "Non-HTTPS rejection modified authored YAML")
    check(not (invalid / "build" / "libs").exists(),
          "Invalid HTTPS URL should fail before packaging")
    result_http["yamlPreservedSha256"] = sha(authored_invalid)

    manifest = {"status": "PASS", "date": "2026-11-13",
                "scope": "real Gradle 9.7.1 native remote task and validator, offline; negative only",
                "cases": {"missingCredentials": result_missing,
                          "nonHttpsUrl": result_http},
                "consumer": "settings.gradle.kts + mod.yaml + Java entrypoint; fabric/api",
                "externalHttpAttempts": False, "authenticatedPutCount": 0,
                "positiveRemoteMavenAuth": "NOT_VERIFIED"}
    (RUN / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n",
                                         encoding="utf-8")
    print("PASS: 2 negative cases, exact FA003, --offline, authored YAML byte preserved", flush=True)


if __name__ == "__main__":
    main()
