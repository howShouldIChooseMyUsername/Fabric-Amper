"""G11 strict resource diagnostic + configuration cache/output integrity probe.

Disposable settings-only consumer rooted under this project's analysis folder.
All measured Gradle commands are --offline, no game/HTTP/IDE/EULA operations.
"""

from __future__ import annotations

import hashlib
import json
import struct
import subprocess
import zlib
from pathlib import Path

root = Path(__file__).resolve().parents[2]
here = Path(__file__).resolve().parent
consumer = here / "consumer-v2"
repo = root / "analysis/final-verification-2026-11-13/build/g29-probe/maven-repo"
gradle = Path(
    r"C:\Users\user\.gradle\wrapper\dists\gradle-9.7.1-bin"
    r"\1w1c7tv4s851m17nbqdsro2tv\gradle-9.7.1\bin\gradle.bat"
)
assert gradle.is_file() and repo.is_dir()
assert not consumer.exists(), "Never overwrite an old consumer"
consumer.mkdir(parents=True)
(consumer / "settings.gradle.kts").write_text(
    "pluginManagement {\n"
    "    repositories {\n"
    f'        maven {{ url = uri("{repo.as_uri()}") }}\n'
    '        maven("https://maven.fabricmc.net/")\n'
    "        gradlePluginPortal()\n"
    "        mavenCentral()\n"
    "    }\n"
    "}\n"
    'plugins { id("dev.fabric-amper.settings") version "0.1.0" }\n',
    encoding="utf-8",
)


def png() -> bytes:
    def chunk(name: bytes, contents: bytes) -> bytes:
        return struct.pack(">I", len(contents)) + name + contents + struct.pack(
            ">I", zlib.crc32(name + contents) & 0xffffffff
        )
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(b"\x00\x33\x66\x99\xff"))
        + chunk(b"IEND", b"")
    )


basic = (root / "src/test/resources/integration/minimal-mod.yaml").read_text(encoding="utf-8")
valid = basic.replace(
    "  entrypoints:",
    "  mixins:\n    - example.mixins.json\n"
    "  accessWidener: example.classtweaker\n"
    "  icon: assets/testkit_mod/icon.png\n"
    "  entrypoints:",
)
yaml = consumer / "mod.yaml"
yaml.write_text(valid, encoding="utf-8")
assets = {
    "example.mixins.json": (root / "src/test/resources/integration/example.mixins.json").read_bytes(),
    "example.classtweaker": (
        "classTweaker v1 official\n"
        "accessible class net/minecraft/world/level/block/Blocks\n"
    ).encode(),
    "assets/testkit_mod/icon.png": png(),
}
for name, value in assets.items():
    file = consumer / "src/main/resources" / name
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_bytes(value)
logs: list[dict] = []


def invoke(label: str, *, expected: int = 0, code: str | None = None) -> str:
    cp = subprocess.run(
        [str(gradle), "help", "--offline", "--configuration-cache", "--console=plain", "--no-daemon"],
        cwd=consumer,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        encoding="utf-8", errors="replace",
    )
    data = cp.stdout
    log = here / (label + ".log")
    log.write_text(data, encoding="utf-8")
    hit = "Reusing configuration cache" in data
    print(label, "exit", cp.returncode, "hit", hit, "diag", code, flush=True)
    assert (cp.returncode == 0) == (expected == 0), data[-4500:]
    if code:
        assert ("FabricAmper " + code) in data, data[-4500:]
        assert not hit, label + ": rejected resources incorrectly reused a valid configuration cache"
        assert "BUILD SUCCESSFUL" not in data
    else:
        assert "BUILD SUCCESSFUL" in data
    logs.append({
        "label": label, "code": code, "cacheHit": hit,
        "sha256": hashlib.sha256(log.read_bytes()).hexdigest().upper(),
    })
    return data


def outputs() -> dict[str, bytes]:
    paths = [
        *sorted((consumer / ".gradle/fabric-amper/loom").rglob("*")),
        *sorted((consumer / "build").rglob("fabric.mod.json")),
        *sorted((consumer / "build/libs").glob("*.jar")),
    ]
    return {
        str(path.relative_to(consumer)).replace("\\", "/"): path.read_bytes()
        for path in paths if path.is_file()
    }


# Warm and create the baseline first; never include a new official Loom cache
# in any measured configuration-cache HIT/MISS assertion.
bootstrap = subprocess.run(
    [str(gradle), "build", "--offline", "--console=plain", "--no-daemon"],
    cwd=consumer, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    encoding="utf-8", errors="replace",
)
assert bootstrap.returncode == 0, bootstrap.stdout[-5000:]
(here / "00-bootstrap-build.log").write_text(bootstrap.stdout, encoding="utf-8")
baseline = outputs()
assert any(p.startswith("build/libs/") for p in baseline)
assert any("loom/" in p and p.endswith("fabric.mod.json") for p in baseline)
invoke("01-initial")
invoke("02-hit")
assert logs[-1]["cacheHit"]


def bad_case(name: str, mutate, restore, code: str) -> None:
    mutate()
    authored = yaml.read_bytes()
    current_sources = {
        k: (consumer / "src/main/resources" / k).read_bytes()
        for k in assets if (consumer / "src/main/resources" / k).is_file()
    }
    invoke("bad-" + name, expected=1, code=code)
    assert yaml.read_bytes() == authored, name + ": authored YAML unexpectedly modified"
    for key, value in current_sources.items():
        assert (consumer / "src/main/resources" / key).read_bytes() == value, name + ": source modified"
    assert outputs() == baseline, name + ": previously built generated/mirror/JAR outputs changed"
    restore()
    assert yaml.read_text(encoding="utf-8") == valid
    for key, value in assets.items():
        assert (consumer / "src/main/resources" / key).read_bytes() == value


def alter(path: Path, new: bytes, label: str, fa: str) -> None:
    old = path.read_bytes()
    bad_case(label, lambda: path.write_bytes(new), lambda: path.write_bytes(old), fa)


alter(consumer / "src/main/resources/example.classtweaker",
      b"classTweaker v1 named\n", "class-tweaker-header", "FA009")
# The schema validates a PNG *resource path and presence*, not ImageIO/PNG
# pixel integrity. A corrupt-but-present .png is deliberately not part of
# FA009; missing actual resource IS part of the contract.
icon_path = consumer / "src/main/resources/assets/testkit_mod/icon.png"
bad_case("missing-icon", lambda: icon_path.unlink(), lambda: icon_path.write_bytes(assets["assets/testkit_mod/icon.png"]), "FA009")
alter(yaml, valid.replace("example.mixins.json", "../outside.mixins.json").encode(),
      "resource-path-traversal", "FA009")
alter(yaml, (valid + "\ndependencies:\n"
             '  - "com.google.code.gson:gson:2.14.0": compile-only\n'
             '  - "com.google.code.gson:gson:2.14.0": include\n').encode(),
      "duplicate-dependency-scope", "FA012")

def create_alias() -> None:
    (consumer / "mod.yml").write_text(valid, encoding="utf-8")

def remove_alias() -> None:
    (consumer / "mod.yml").unlink()

bad_case("simultaneous-yaml-aliases", create_alias, remove_alias, "FA001")

source_json = consumer / "src/main/resources/fabric.mod.json"
bad_case(
    "source-fabric-json",
    lambda: source_json.write_text('{"id":"do_not_overwrite"}\n',encoding="utf-8"),
    lambda: source_json.unlink(),
    "FA008",
)

invoke("03-restored")
invoke("04-restored-hit")
assert logs[-1]["cacheHit"]
assert outputs() == baseline
assert not (consumer / "build.gradle.kts").exists()
assert not (consumer / "build.gradle").exists()
proof = {
    "gate": "G11/G30",
    "status": "PASS",
    "cases": 6,
    "checks": "FA001/FA008/FA009/FA012, source/YAML and generated/mirror/JAR bytes unchanged; cache rejection and recovery HIT",
    "baselineHashes": {key: hashlib.sha256(value).hexdigest().upper() for key,value in baseline.items()},
    "runs": logs,
}
path = root / "analysis/acceptance-evidence-2026-11-13/FOLLOWUP-G11-resource-FA001-FA008-FA009-FA012-CC-PASS.json"
path.write_text(json.dumps(proof,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print("PASS all 6 resource/metadata failure cases and CC recovery", flush=True)
