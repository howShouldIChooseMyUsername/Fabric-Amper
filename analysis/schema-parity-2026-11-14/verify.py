"""Offline regression checks for Fabric Amper's standalone Draft-07 schema.

The schema remains the editor/SchemaStore contract. This script checks selected
exact YAML DTO constraints against the official jsonschema evaluator without
changing the production sources or making any network requests.
"""

from __future__ import annotations

from copy import deepcopy
import json
from pathlib import Path

from jsonschema import Draft7Validator, FormatChecker


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_PATH = ROOT / "fabric-amper.schema.json"
schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
Draft7Validator.check_schema(schema)
validator = Draft7Validator(schema, format_checker=FormatChecker())
checks = 0


def valid(document: dict, label: str, expected: bool = True) -> None:
    global checks
    errors = list(validator.iter_errors(document))
    actual = not errors
    if actual != expected:
        details = [(list(e.absolute_path), e.message) for e in errors[:3]]
        raise AssertionError(f"{label}: expected valid={expected}, got {actual}: {details}")
    checks += 1


def changed(path: tuple[str, ...], value: object) -> dict:
    document = deepcopy(schema["examples"][1])
    field = document
    for key in path[:-1]:
        field = field[key]
    field[path[-1]] = value
    return document


def main() -> None:
    for index, document in enumerate(schema["examples"]):
        valid(document, f"original schema example {index}")

    for field in ("loader", "fabricApi"):
        for version in (
            "0.20.1", "0.165.2+26.3", "1.2.3-rc.1", "abc", "x1",
            "1.2+x", "1.2+foo.bar",
        ):
            valid(changed(("minecraft", field), version), f"{field} fixed {version}")
        for version in (
            "latest.release", "LATEST.release", "1.+", "1.2.x", "1.2.X",
            "x", "X", "1..2", "1.2.", "1.2+meta.", "1.2+meta..extra",
            "1.2+bad+meta", "1.2\n", "1.2.x.3",
        ):
            valid(changed(("minecraft", field), version),
                  f"{field} dynamic/malformed {version!r}", False)

    for path in (
        ("publish", "modrinth", "versionName"),
        ("publish", "curseforge", "displayName"),
    ):
        for value in ("Release Name", "A", "Versions — stable"):
            valid(changed(path, value), f"{path} valid {value!r}")
        for value in (" X", "X ", "X\tName", "X\nName", "X\r", "X\x7f", "X\x85", " "):
            valid(changed(path, value), f"{path} control/space {value!r}", False)

    for names, expected in (
        (["Fabric", "Client"], True),
        (["Fabric Client"], True),
        (["Fabric\tClient"], False),
        (["Fabric\nClient"], False),
        ([" Fabric"], False),
    ):
        valid(changed(("publish", "curseforge", "gameVersionNames"), names),
              f"CurseForge gameVersionNames {names!r}", expected)

    for value in (1, 2147483647):
        valid(changed(("publish", "curseforge", "projectId"), value),
              f"CurseForge projectId {value}")
        valid(changed(("publish", "curseforge", "gameVersions"), [value]),
              f"CurseForge gameVersions {[value]}")
    for value in (0, -1, 2147483648):
        valid(changed(("publish", "curseforge", "projectId"), value),
              f"CurseForge projectId {value}", False)
        valid(changed(("publish", "curseforge", "gameVersions"), [value]),
              f"CurseForge gameVersions {[value]}", False)

    valid(changed(("mod", "provides"), ["helper_a", "helper_b"]), "distinct Loader provides")
    valid(changed(("mod", "provides"), ["helper_a", "helper_a"]),
          "duplicate Loader provides", False)
    valid(changed(("publish", "modrinth", "changelog"), "First line\nSecond line"),
          "multiline changelog remains valid")
    valid(changed(("publish", "modrinth", "versionNumber"), "1.2.0+26.3"),
          "Modrinth explicit version number remains valid")
    valid(changed(("publish", "modrinth", "gameVersions"), ["26.3"]),
          "Modrinth gameVersions remain valid")
    valid(changed(("publish", "modrinth", "loaders"), ["fabric"]),
          "Modrinth loaders remain valid")
    valid(changed(("publish", "curseforge", "relations"),
                  [{"slug": "some-mod", "projectId": "1234", "type": "requiredDependency"}]),
          "CurseForge relation projectId remains valid")
    print(f"PASS: Draft-07 meta-schema, {checks} valid/invalid examples, no network")


if __name__ == "__main__":
    main()
