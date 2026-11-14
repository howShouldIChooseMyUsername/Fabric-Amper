"""Offline checks for schema v1 fallback and publish.common (no HTTP/Gradle)."""
from pathlib import Path
import json
import re
import zipfile
import yaml
from jsonschema import Draft7Validator

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = json.loads((ROOT / "fabric-amper.schema.json").read_text(encoding="utf-8"))
Draft7Validator.check_schema(SCHEMA)
V = Draft7Validator(SCHEMA)
base = {
    "product": "fabric/mod",
    "mod": {"id": "example_mod", "version": "1.0.0+26.3"},
    "minecraft": {"version": "26.3", "loader": "0.20.1"},
}
example = {
    **base,
    "publish": {
        "common": {
            "releaseType": "beta",
            "name": "Example Mod 1.0.0",
            "gameVersions": ["26.3", "26.3.1"],
            "changelogFile": "CHANGELOG.md",
            "dependencies": [
                {"slug": "fabric-api", "curseforgeId": "306612", "type": "required"}
            ],
        },
        "modrinth": {"projectId": "example-project", "tokenEnv": "MODRINTH_TOKEN"},
        "curseforge": {"projectId": 123456, "tokenEnv": "CURSEFORGE_TOKEN"},
    },
}
assert V.is_valid(base), list(V.iter_errors(base))
assert V.is_valid({**base, "schema": 1})
assert V.is_valid(example), list(V.iter_errors(example))
assert not V.is_valid({**base, "schema": 2})
assert not V.is_valid({**base, "schema": None})
assert not V.is_valid({**base, "publish": {"common": {"releaseType": "release"}}})
assert not V.is_valid({**base, "publish": {"common": {"releaseType": "release"},
                                        "maven": {"url": "https://maven.example.org/releases"}}})
assert not V.is_valid({**base, "publish": {"common": {"dependencies": [
    {"slug": "a", "curseforgeId": "-1", "type": "required"}]},
    "modrinth": {"projectId": "x", "tokenEnv": "MODRINTH_TOKEN"}}})

docs = ["README.md", "HUMAN.md", "HUMAN_QuickStart.md", "HUMAN_QuickGuide.md",
        "FabricAmperSKILL.md", "FabricAmperSKILL_SYN.md", "PROBLEMS.md"]
errors = []
full_count = 0
links_count = 0

def slug(text):
    text = re.sub(r"`([^`]+)`", r"\1", text)
    text = re.sub(r"[^\w -]", "", text.lower())
    return re.sub(r"\s+", "-", text).strip("-")

def headings(text):
    found = set()
    counts = {}
    for line in text.splitlines():
        match = re.match(r"^#{1,6} +(.+?)\s*#*\s*$", line)
        if match:
            key = slug(match.group(1))
            seq = counts.get(key, 0)
            counts[key] = seq + 1
            found.add(key if seq == 0 else f"{key}-{seq}")
    return found

for name in docs:
    body = (ROOT / name).read_text(encoding="utf-8")
    for url in re.findall(r"\]\(([^)\s]+)\)", body):
        if url.startswith(("https://", "http://", "mailto:")):
            continue
        links_count += 1
        target_name, sep, fragment = url.partition("#")
        dest = ROOT / (target_name or name)
        if not dest.exists():
            errors.append(f"{name}: missing link {url}")
        elif fragment and dest.suffix == ".md" and fragment not in headings(dest.read_text(encoding="utf-8")):
            errors.append(f"{name}: missing anchor {url}")
    for i, block in enumerate(re.findall(r"(?ms)^```yaml\s*\n(.*?)^```", body), 1):
        if "# WRONG" in block or block.lstrip().startswith("---"):
            continue
        try:
            obj = yaml.safe_load(block)
        except yaml.YAMLError as exc:
            errors.append(f"{name} YAML block {i}: parser {exc}")
            continue
        if isinstance(obj, dict) and {"mod", "minecraft", "product"}.issubset(obj):
            full_count += 1
            for exc in V.iter_errors(obj):
                errors.append(f"{name} YAML block {i}: {exc.message}")
    print(name, len(body.splitlines()), "lines")

print("schema: Draft-07 PASS, fallback/common negative cases PASS")
print("complete YAML blocks checked", full_count, "relative links checked", links_count)
for error in errors:
    print("ERROR", error)
if errors:
    raise SystemExit(1)

jar = ROOT / "analysis/publish-common-consumer-2026-11-14/build/libs/common_probe-1.0.0+26.3.jar"
if not jar.is_file():
    print("CONSUMER_JAR not available: run isolated consumer build to verify it")
else:
    with zipfile.ZipFile(jar) as archive:
        entries = [info for info in archive.infolist() if info.filename == "fabric.mod.json"]
        assert len(entries) == 1, f"root metadata count = {len(entries)}"
        meta = json.loads(archive.read("fabric.mod.json"))
        assert meta["id"] == "common_probe"
        assert meta["version"] == "1.0.0+26.3"
    print("ISOLATED_CONSUMER_JAR: exactly one root fabric.mod.json, id/version MATCH")
