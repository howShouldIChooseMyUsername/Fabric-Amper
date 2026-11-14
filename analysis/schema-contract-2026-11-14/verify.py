#!/usr/bin/env python3
"""Offline Draft-07 smoke contract for the current Fabric Amper YAML schema.

Run from any directory with Python, PyYAML and jsonschema installed:
    python analysis/schema-contract-2026-11-14/verify.py

This checks editor/schema behavior. The actual Kotlin decoder/validator remains
authoritative for filesystem resources, Loom versions, Loader predicates and
cross-array uniqueness that Draft-07 cannot express without custom keywords.
"""

from copy import deepcopy
from pathlib import Path
import json
import re

import jsonschema
import yaml


ROOT = Path(__file__).resolve().parents[2]
SCHEMA = json.loads((ROOT / "fabric-amper.schema.json").read_text(encoding="utf-8"))
jsonschema.Draft7Validator.check_schema(SCHEMA)
VALIDATOR = jsonschema.Draft7Validator(SCHEMA, format_checker=jsonschema.FormatChecker())

BASE = {
    "product": "fabric/mod",
    "mod": {"id": "sample-mod", "version": "1.0.0+26.3"},
    "minecraft": {"version": "26.3", "loader": "0.20.1"},
}


def check(name, changes, expect_valid):
    instance = deepcopy(BASE)
    for path, value in changes.items():
        current = instance
        parts = path.split(".")
        for field in parts[:-1]:
            current = current.setdefault(field, {})
        if value is None:
            current.pop(parts[-1], None)
        else:
            current[parts[-1]] = value
    errors = list(VALIDATOR.iter_errors(instance))
    if bool(not errors) != expect_valid:
        error = errors[0].message if errors else "unexpectedly accepted"
        raise AssertionError(f"{name}: {error}")


CASES = [
    ("optional-schema-modern", {}, True),
    ("explicit-schema-v1", {"schema": 1}, True),
    ("reject-unknown-schema", {"schema": 2}, False),
    ("api-product-with-group", {"product": "fabric/api", "mod.group": "org.example"}, True),
    ("library-alias-with-group", {"product": "fabric/library", "mod.group": "org.example"}, True),
    ("api-product-needs-group", {"product": "fabric/api"}, False),
    ("modern-yarn-mappings-rejected", {"minecraft.mappings": {"type": "yarn", "version": "26.3+build.1"}}, False),
    ("modern-java-21-rejected", {"settings.java.release": 21}, False),
    ("modern-java-25-valid", {"settings.java.release": 25}, True),
    ("modern-yarn-26-1-rejected", {"minecraft.version": "26.1", "minecraft.mappings": {"type": "yarn", "version": "26.1+build.1"}}, False),
    ("modern-java-26-2-rejected", {"minecraft.version": "26.2", "settings.java.release": 21}, False),
    ("modern-java-27-1-rejected", {"minecraft.version": "27.1", "settings.java.release": 21}, False),
    ("modern-java-30-1-rejected", {"minecraft.version": "30.1", "settings.java.release": 21}, False),
    ("modern-java-100-1-rejected", {"minecraft.version": "100.1", "settings.java.release": 21}, False),
    ("modern-leading-zero-major-rejected", {"minecraft.version": "00026.1", "settings.java.release": 21}, False),
    ("modern-class-tweaker-valid", {"mod.accessWidener": "rules/changes.classtweaker"}, True),
    ("modern-legacy-access-widener-rejected", {"mod.accessWidener": "rules/changes.accesswidener"}, False),
    ("legacy-1-21-11-yarn-java-21-valid", {"minecraft.version": "1.21.11", "minecraft.mappings": {"type": "yarn", "version": "1.21.11+build.1"}, "settings.java.release": 21}, True),
    ("legacy-access-widener-valid", {"minecraft.version": "1.21.11", "mod.accessWidener": "rules/changes.accesswidener", "settings.java.release": 21}, True),
    ("legacy-class-tweaker-rejected", {"minecraft.version": "1.21.11", "mod.accessWidener": "rules/changes.classtweaker", "settings.java.release": 21}, False),
    ("legacy-26-0-java-21-valid", {"minecraft.version": "26.0", "settings.java.release": 21}, True),
    ("legacy-3-1-java-21-valid", {"minecraft.version": "3.1", "settings.java.release": 21}, True),
    ("legacy-10-1-java-21-valid", {"minecraft.version": "10.1", "settings.java.release": 21}, True),
    ("loader-version-dynamic-rejected", {"minecraft.loader": "latest.release"}, False),
    ("loader-mod-id-valid", {"mod.depends": {"fabric-api": ">=0.165.2"}}, True),
    ("loader-mod-id-invalid", {"mod.depends": {"BAD MOD ID": "*"}}, False),
    ("reserved-kotlin-language-adapter", {"mod.languageAdapters": {"kotlin": "my.KotlinAdapter"}}, False),
    ("custom-language-adapter-valid", {"mod.languageAdapters": {"custom-adapter": "my.Adapter"}}, True),
    ("repo-https-valid", {"repositories": [{"maven": "https://repo.example.org/artifacts"}]}, True),
    ("repo-query-currently-allowed", {"repositories": [{"maven": "https://repo.example.org/artifacts?hint=release"}]}, True),
    ("repo-userinfo-rejected", {"repositories": [{"maven": "https://user:password@repo.example.org/artifacts"}]}, False),
    ("repo-fragment-rejected", {"repositories": [{"maven": "https://repo.example.org/artifacts#fragment"}]}, False),
    ("repo-http-rejected", {"repositories": [{"maven": "http://repo.example.org/artifacts"}]}, False),
    ("too-long-icon-size-rejected", {"mod.icon": {"12345678901": "assets/icon.png"}}, False),
    ("icon-png-valid", {"mod.icon": "assets/icon.PNG"}, True),
    ("icon-non-png-rejected", {"mod.icon": "assets/icon.jpg"}, False),
    ("icon-traversal-rejected", {"mod.icon": "../icon.png"}, False),
    ("icon-map-non-png-rejected", {"mod.icon": {"64": "assets/icon.gif"}}, False),
    ("mixin-resource-path-valid", {"mod.mixins": [{"config": "configs/mixins.json", "environment": "client"}]}, True),
    ("mixin-traversal-rejected", {"mod.mixins": ["../mixins.json"]}, False),
    ("mod-duplicate-provides-rejected", {"mod.provides": ["alias-mod", "alias-mod"]}, False),
    ("publishing-on-regular-mod-rejected", {"publishing": {}}, False),
    ("publish-common-alone-rejected", {"publish": {"common": {"releaseType": "beta"}}}, False),
    ("publish-modrinth-valid", {"publish": {"modrinth": {"projectId": "ABCD1234", "tokenEnv": "MODRINTH_TOKEN"}}}, True),
    ("publish-modrinth-wrong-secret-field", {"publish": {"modrinth": {"projectId": "ABCD1234", "token": "hardcoded"}}}, False),
    ("publish-changelog-nested-file-valid", {"publish": {"modrinth": {"projectId": "ABCD1234", "tokenEnv": "MODRINTH_TOKEN", "changelogFile": "docs/CHANGELOG.md"}}}, True),
    ("publish-changelog-traversal-rejected", {"publish": {"modrinth": {"projectId": "ABCD1234", "tokenEnv": "MODRINTH_TOKEN", "changelogFile": "../CHANGELOG.md"}}}, False),
    ("publish-changelog-dot-segment-rejected", {"publish": {"modrinth": {"projectId": "ABCD1234", "tokenEnv": "MODRINTH_TOKEN", "changelogFile": "docs/./CHANGELOG.md"}}}, False),
    ("publish-changelog-backslash-rejected", {"publish": {"modrinth": {"projectId": "ABCD1234", "tokenEnv": "MODRINTH_TOKEN", "changelogFile": "docs\\CHANGELOG.md"}}}, False),
    ("publish-duplicate-common-relation-rejected", {"publish": {"common": {"dependencies": [{"slug": "fabric-api", "type": "required"}, {"slug": "fabric-api", "type": "required"}]}, "modrinth": {"projectId": "ABCD1234", "tokenEnv": "MODRINTH_TOKEN"}}}, False),
    ("publish-duplicate-modrinth-relation-rejected", {"publish": {"modrinth": {"projectId": "ABCD1234", "tokenEnv": "MODRINTH_TOKEN", "dependencies": [{"projectId": "fabric-api", "type": "required"}, {"projectId": "fabric-api", "type": "required"}]}}}, False),
    ("publish-duplicate-curseforge-relation-rejected", {"publish": {"curseforge": {"projectId": 123456, "tokenEnv": "CURSEFORGE_TOKEN", "relations": [{"slug": "fabric-api", "type": "requiredDependency"}, {"slug": "fabric-api", "type": "requiredDependency"}]}}}, False),
    ("publish-maven-requires-group", {"publish": {"maven": {"url": "https://repo.example.org/releases"}}}, False),
    ("publish-maven-with-group", {"publish": {"maven": {"url": "https://repo.example.org/releases", "credentialsFromEnv": ["MAVEN_USER", "MAVEN_PASSWORD"]}}, "mod.group": "org.example"}, True),
]


for name, overrides, accepted in CASES:
    check(name, overrides, accepted)

for index, example in enumerate(SCHEMA.get("examples", []), start=1):
    errors = list(VALIDATOR.iter_errors(example))
    if errors:
        raise AssertionError(f"Schema example {index}: {errors[0].message}")

# Only complete standalone YAML fences count. Fragmentary examples and
# explicitly illustrative anti-patterns are handled by the Kotlin doc test.
DOCUMENTS = [
    "README.md", "HUMAN.md", "HUMAN_QuickStart.md", "HUMAN_QuickGuide.md",
    "FabricAmperSKILL.md", "FabricAmperSKILL_SYN.md",
]
FENCE = re.compile(r"^```yaml[^\n]*\n(.*?)^```[ \t]*$", re.MULTILINE | re.DOTALL)
checked_documents = 0
for document in DOCUMENTS:
    raw = (ROOT / document).read_text(encoding="utf-8")
    for index, match in enumerate(FENCE.finditer(raw), start=1):
        snippet = match.group(1)
        if not all(re.search(rf"^{field}:", snippet, re.MULTILINE) for field in ("product", "mod", "minecraft")):
            continue
        instance = yaml.safe_load(snippet)
        errors = list(VALIDATOR.iter_errors(instance))
        if errors:
            raise AssertionError(f"{document} YAML fence {index}: {errors[0].message}")
        checked_documents += 1

if checked_documents < 7:
    raise AssertionError(f"Expected 7+ complete documentation YAML examples, got {checked_documents}")

print(f"PASS Draft-07 schema, {len(CASES)} positive/negative fixtures, "
      f"{len(SCHEMA.get('examples', []))} schema examples, "
      f"{checked_documents} complete manual YAML fences")
