"""Local-only G5 Java settings configuration-cache acceptance.

Only writes below this analysis folder. Uses an already published, local Fabric
Amper plugin and the default Gradle 9.7.1 binary; it never executes remote
publishing, a game, EULA acceptance, or any Modrinth/CurseForge API.
"""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
from pathlib import Path

here = Path(__file__).resolve().parent
project_root = here.parent.parent
consumer = here / "settings-only-consumer-v2"
repo = project_root / "analysis" / "final-verification-2026-11-13" / "build" / "g29-probe" / "maven-repo"
gradle = Path(
    r"C:\Users\user\.gradle\wrapper\dists\gradle-9.7.1-bin\1w1c7tv4s851m17nbqdsro2tv"
    r"\gradle-9.7.1\bin\gradle.bat"
)
assert repo.is_dir() and gradle.is_file(), "Prerequisite local repo or Gradle installation missing"
assert not consumer.exists(), "Never overwrite a previously generated consumer"
consumer.mkdir()

settings = (
    "pluginManagement {\n"
    "    repositories {\n"
    f'        maven {{ url = uri("{repo.as_uri()}") }}\n'
    '        maven("https://maven.fabricmc.net/")\n'
    "        gradlePluginPortal()\n"
    "        mavenCentral()\n"
    "    }\n"
    "}\n"
    'plugins { id("dev.fabric-amper.settings") version "0.1.0" }\n'
)
yaml = (project_root / "src" / "test" / "resources" / "integration" / "minimal-mod.yaml").read_text(
    encoding="utf-8"
)
assert "sourcesJar: true" in yaml and "release: 25" in yaml
(consumer / "settings.gradle.kts").write_text(settings, encoding="utf-8")
(consumer / "mod.yaml").write_text(yaml, encoding="utf-8")
assert not (consumer / "build.gradle.kts").exists() and not (consumer / "build.gradle").exists()

log_digests: dict[str, str] = {}


def invoke(stage: str, args: list[str]) -> str:
    result = subprocess.run(
        [str(gradle), *args, "--console=plain", "--no-daemon"],
        cwd=consumer,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    out = result.stdout
    log = here / (stage + ".log")
    log.write_text(out, encoding="utf-8")
    log_digests[stage] = hashlib.sha256(log.read_bytes()).hexdigest().upper()
    print(f"{stage}: EXIT {result.returncode}; cache HIT={('Reusing configuration cache' in out)}", flush=True)
    if result.returncode or "BUILD SUCCESSFUL" not in out:
        print(out[-5000:], flush=True)
        raise AssertionError(f"{stage}: Gradle task failed; see {log}")
    return out


# Warm official Fabric/Loom metadata AND task enumeration, which can materialize
# IDE model/Gradle-generated directories during the first invocation.
invoke("00-help-warm", ["help"])
invoke("00b-tasks-warm", ["tasks", "--all", "--offline"])
args = ["tasks", "--all", "--configuration-cache", "--offline"]
first = invoke("01-first-store", args)
assert "Reusing configuration cache" not in first
assert re.search(r"(?m)^sourcesJar(?:\s|$)", first), "Initial Java sourcesJar task missing"
second = invoke("02-second-hit", args)
assert "Reusing configuration cache" in second
assert re.search(r"(?m)^sourcesJar(?:\s|$)", second)

# This is a VALID settings.java option change, unlike using Java release 24
# with Minecraft 26.3, which correctly requires Java 25.
changed = yaml.replace("sourcesJar: true", "sourcesJar: false")
(consumer / "mod.yaml").write_text(changed, encoding="utf-8")
third = invoke("03-java-option-miss", args)
assert "Reusing configuration cache" not in third, "YAML Java settings change incorrectly reused cache"
assert re.search(r"(?m)^sourcesJar(?:\s|$)", third) is None, "sourcesJar remained enabled"
fourth = invoke("04-java-option-hit", args)
assert "Reusing configuration cache" in fourth
assert re.search(r"(?m)^sourcesJar(?:\s|$)", fourth) is None

(consumer / "mod.yaml").write_text(yaml, encoding="utf-8")
fifth = invoke("05-restored-option-miss", args)
assert "Reusing configuration cache" not in fifth
assert re.search(r"(?m)^sourcesJar(?:\s|$)", fifth)
sixth = invoke("06-restored-option-hit", args)
assert "Reusing configuration cache" in sixth
assert re.search(r"(?m)^sourcesJar(?:\s|$)", sixth)

assert (consumer / "mod.yaml").read_text(encoding="utf-8") == yaml
assert not (consumer / "build.gradle").exists() and not (consumer / "build.gradle.kts").exists()

proof = {
    "gate": "G5",
    "status": "PASS",
    "configuration": "Minecraft 26.3 / settings.java.release:25 / settings.java.sourcesJar true -> false -> true",
    "workflow": "warm; CC STORE; HIT; Java option MISS; HIT; revert MISS; HIT",
    "project": "settings-only consumer, no developer-authored build script or fabric.mod.json",
    "gradle": "Gradle 9.7.1; official deps may warm via help; measured 6 invocations strictly --offline",
    "logsSha256": log_digests,
}
dest = project_root / "analysis" / "acceptance-evidence-2026-11-13" / "FOLLOWUP-G5-java-setting-CC-6run-PASS.json"
dest.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"PASS: {dest}", flush=True)
