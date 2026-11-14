"""Real settings-only G11/G30 Gradle CC adversarial YAML verification.

Only touches its own analysis/ workspace. Published Fabric Amper 0.1.0 is
resolved from an existing local Maven repository. Strictly --offline for
measured Gradle invocations; no game/EULA, external HTTP or remote publishing.
"""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[2]
here = Path(__file__).resolve().parent
consumer = here / "consumer-v3"
gradle = Path(
    r"C:\Users\user\.gradle\wrapper\dists\gradle-9.7.1-bin"
    r"\1w1c7tv4s851m17nbqdsro2tv\gradle-9.7.1\bin\gradle.bat"
)
repo = root / "analysis" / "final-verification-2026-11-13" / "build" / "g29-probe" / "maven-repo"
assert gradle.is_file() and repo.is_dir()
assert not consumer.exists(), "New fixture only: never overwrite prior evidence"
consumer.mkdir(parents=True)

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
valid = (root / "src/test/resources/integration/minimal-mod.yaml").read_bytes()
(consumer / "settings.gradle.kts").write_text(settings, encoding="utf-8")
yaml = consumer / "mod.yaml"
yaml.write_bytes(valid)

results: list[dict] = []


def run(label: str, *, expected: int, fa: str | None = None) -> str:
    completed = subprocess.run(
        [str(gradle), "help", "--configuration-cache", "--offline", "--console=plain", "--no-daemon"],
        cwd=consumer,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    output = completed.stdout
    log = here / (label + ".log")
    log.write_text(output, encoding="utf-8")
    print(label, "EXIT", completed.returncode, "expected", expected, "fa", fa,
          "cache-hit", "Reusing configuration cache" in output, flush=True)
    if (completed.returncode == 0) != (expected == 0):
        print(output[-5000:], flush=True)
        raise AssertionError(f"{label}: incorrect exit; see {log}")
    if fa is not None:
        assert "Reusing configuration cache" not in output, f"{label}: stale CC cache REUSED"
        assert ("FabricAmper " + fa) in output, f"{label}: required diagnostic {fa} missing: {output[-2500:]}"
        # Malformed UTF-8 bytes, truly empty documents, and missing fields
        # legitimately have no SnakeYAML node mark. Never invent :1:1.
        if label not in {"bad-invalid-utf8", "bad-empty-document"}:
            assert re.search(r"(?m)(?:mod\.yaml|mod\.yml).*?:\d+:\d+|\bline\s+\d+", output), (
                f"{label}: missing real source line/column: {output[-2500:]}"
            )
    elif expected == 0:
        assert "BUILD SUCCESSFUL" in output
    results.append({
        "name": label,
        "exit": completed.returncode,
        "fa": fa,
        "cacheHit": "Reusing configuration cache" in output,
        "logSha256": hashlib.sha256(log.read_bytes()).hexdigest().upper(),
    })
    return output


def snap_outputs() -> dict[str, bytes]:
    paths = [
        consumer / "build" / "generated" / "fabric-amper" / "resources" / "main" / "fabric.mod.json",
        *sorted((consumer / ".gradle" / "fabric-amper" / "loom").rglob("fabric.mod.json")),
        *sorted((consumer / "build" / "libs").glob("*.jar")),
    ]
    return {str(p.relative_to(consumer)).replace("\\", "/"): p.read_bytes() for p in paths if p.is_file()}


# Fully materialize official Loom Minecraft metadata before measuring the
# configuration cache. A first-time help can create minecraft-merged*.jar
# *after* Gradle records its input snapshot, causing a legitimate cold MISS.
build = subprocess.run(
    [str(gradle), "build", "--offline", "--console=plain", "--no-daemon"],
    cwd=consumer,
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, encoding="utf-8", errors="replace",
)
assert build.returncode == 0, build.stdout[-5000:]
(here / "00-bootstrap-build.log").write_text(build.stdout, encoding="utf-8")
baseline = snap_outputs()
assert any("build/libs/" in x for x in baseline) and any("fabric.mod.json" in x for x in baseline)
run("00-initial", expected=0)
run("01-valid-hit", expected=0)
assert results[-1]["cacheHit"], "Valid YAML failed to reuse a warmed config cache"

decoded = valid.decode("utf-8")
cases = [
    ("duplicate-nested-key", decoded.replace("  id: testkit_mod\n", "  id: testkit_mod\n  id: duplicate\n").encode(), "FA002"),
    ("scalar-alias", decoded.replace("  id: testkit_mod\n", "  id: &x testkit_mod\n  name: *x\n").encode(), "FA002"),
    ("collection-tag", decoded.replace("  entrypoints:\n", "  provides: !!seq [alias_one]\n  entrypoints:\n").encode(), "FA002"),
    ("multiple-documents", (decoded + "\n---\nschema: 1\n").encode(), "FA002"),
    ("empty-document", b"", "FA002"),
    ("invalid-utf8", b"schema: 1\nproduct: fabric/mod\nmod:\n  id: \xc3(\n", "FA002"),
    ("number-minecraft-version", decoded.replace('  version: "26.3"\n', "  version: 26.3\n").encode(), "FA003"),
    ("unknown-nested-field", decoded.replace("  id: testkit_mod\n", "  id: testkit_mod\n  unknownNested: nope\n").encode(), "FA003"),
    ("explicit-merge-key", decoded.replace("  id: testkit_mod\n", "  <<: {id: altered}\n  id: testkit_mod\n").encode(), "FA002"),
    ("anchored-scalar", decoded.replace("  id: testkit_mod\n", "  id: &unused testkit_mod\n").encode(), "FA002"),
    ("over-deep-yaml", ("schema: 1\nmod:\n" + "".join("  " * i + f"k{i}:\n" for i in range(1, 39))).encode(), "FA002"),
]

for name, authored, code in cases:
    yaml.write_bytes(authored)
    assert yaml.read_bytes() == authored
    run("bad-" + name, expected=1, fa=code)
    assert yaml.read_bytes() == authored, name + ": YAML altered by diagnostic"
    after = snap_outputs()
    assert after == baseline, name + ": prior valid generated/mirror/JAR was modified"

yaml.write_bytes(valid)
run("recovery-restore-store", expected=0)
run("recovery-hit", expected=0)
assert results[-1]["cacheHit"], "Config cache did not HIT after recovery"
assert snap_outputs() == baseline, "Previously generated valid output changed on recovery"
assert yaml.read_bytes() == valid
assert not (consumer / "build.gradle").exists() and not (consumer / "build.gradle.kts").exists()
assert not (consumer / "src/main/resources/fabric.mod.json").exists()

manifest = {
    "gate": "G11/G30",
    "status": "PASS",
    "cases": len(cases),
    "checks": "Every hostile input expected FA002 or FA003, source real coordinates, CC invalidation, YAML + generated/mirror/JAR unchanged, recovery CC HIT",
    "consumer": "settings-only; published local plugin; no HTTP or game",
    "baselineFileSha256": {k: hashlib.sha256(v).hexdigest().upper() for k, v in baseline.items()},
    "runs": results,
}
dest = root / "analysis/acceptance-evidence-2026-11-13/FOLLOWUP-G11-G30-hostile-YAML-CC-preservation-PASS.json"
dest.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print(f"PASS {len(cases)} hostile YAML inputs + CC recovery; {dest}", flush=True)
