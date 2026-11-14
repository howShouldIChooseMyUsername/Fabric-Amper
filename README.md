# Fabric Amper

**Build Fabric mods with a Settings plugin and YAML — without writing a consumer `build.gradle(.kts)` or `fabric.mod.json`.**

Fabric Amper is a **Gradle binary plugin**, not a Minecraft mod. It takes care of Fabric Loom, Minecraft/Loader configuration, Java and optional Kotlin, dependency scopes, generated Loader metadata, JAR verification, and opt-in publishing. You still write your mod's Java/Kotlin code, Mixins, resources, and tests.

> **Release status:** `v0.1.0` is a **development preview**, not a fully approved G1–G30 production release. Automated builds and selected settings-only consumer checks have passed, but IDE GUI synchronization, all runtime variants, and authenticated external publishing remain subject to the limitations in [QA_REPORT.md](QA_REPORT.md). **This plugin is not published to the Gradle Plugin Portal.**

## Why Fabric Amper?

| Standard Fabric Gradle project | Fabric Amper consumer |
| --- | --- |
| `settings.gradle.kts` + manually configured `build.gradle.kts` | `settings.gradle.kts` + `mod.yaml` (or `mod.yml`) |
| Maintain your own `fabric.mod.json` | Generated from the `mod` YAML section |
| Configure Loom, language plugins, and dependencies in Gradle DSL | Describe Minecraft, Java, optional Kotlin, repositories and dependencies in YAML |
| Hand-configure publishing and local Maven publication | Optional explicit Maven/Modrinth/CurseForge publication tasks |
| Write your own source code and assets | **Still write them**; Fabric Amper configures the build, not your mod's behavior |

The **only** consumer plugin entry point is `dev.fabric-amper.settings`. `dev.fabric-amper.project` is applied internally; consumers should not declare it.

## Quick start (Windows)

### Requirements

For the documented default example: **JDK 25**, a compatible **Gradle** installation (the project's tested baseline is **9.7.1**), **Minecraft 26.3**, **Fabric Loader 0.20.1**, and optionally **Fabric API 0.165.2+26.3**. A legacy Minecraft 1.21.11 profile uses Java 21 and a separate remapping build path. **This source repository does not include `gradlew.bat`.**

For the simplest setup, clone the repository, then open a terminal in the **producer** directory:

```bat
git clone https://github.com/howShouldIChooseMyUsername/Fabric-Amper.git
cd Fabric-Amper
fabric-amper.bat doctor
fabric-amper.bat setup
fabric-amper.bat init example_mod
cd ..\example_mod
"..\Fabric-Amper\fabric-amper.bat" build
```

- `doctor` checks your Java/Gradle launcher and local plugin marker.
- `setup` builds/publishes the **producer plugin and Gradle plugin markers into a local Maven repository** at `Fabric-Amper/build/g29-probe/maven-repo/`. It does not publish to a remote service.
- `init` creates a **new** consumer directory; it never overwrites an existing one. The generated project includes `settings.gradle.kts`, `mod.yaml`, `schemas/mod.schema.json`, and a minimal Java entrypoint.
- `build` validates YAML, compiles your mod, and verifies the resulting JAR. It does **not** upload anything.

The first line of generated `mod.yaml` is `# yaml-language-server: $schema=./schemas/mod.schema.json` for editor autocomplete. The local JSON Schema is copied from this producer at project creation. `init` deliberately **omits `schema: 1`** so the plugin's latest implemented YAML grammar is selected (currently v1); existing editor schema copies need manual refresh after future upgrades.

### What gets generated?

```text
example_mod/
├── settings.gradle.kts
├── mod.yaml
├── schemas/
│   └── mod.schema.json
└── src/main/java/org/example/ExampleMod.java
```

The generated `mod.yaml` has this structure (shown without the extra `group` and `sourcesJar` convenience values that `init` also includes):

```yaml
# yaml-language-server: $schema=./schemas/mod.schema.json
product: fabric/mod
mod:
  id: example_mod
  version: "1.0.0+26.3"
  name: Example Mod
  entrypoints:
    main: [org.example.ExampleMod]
minecraft:
  version: "26.3"
  mappings: mojang
  loader: "0.20.1"
  fabricApi: "0.165.2+26.3"
settings:
  java:
    release: 25
  kotlin:
    enabled: false
repositories:
  - fabric
  - mavenCentral
```

The consumer `settings.gradle.kts` applies only `id("dev.fabric-amper.settings") version "0.1.0"` within `plugins { ... }` and configures `pluginManagement.repositories` to find the **local plugin repository**, Fabric Maven and the public Gradle dependency sources. If you move a generated consumer, update its local repository path.

Run `gradle validateFabricAmper`, `gradle build` and `gradle verifyFabricAmperJar` directly if you prefer Gradle commands. A valid production JAR contains **one root-level generated `fabric.mod.json`**; it is not a Minecraft mod unless it also includes actual mod code.

See the [Quick Guide](HUMAN_QuickGuide.md) for complete copyable starter files and the feature cheat sheet.

## Downloading a release without building the plugin

On the [Releases page](https://github.com/howShouldIChooseMyUsername/Fabric-Amper/releases), the preview release includes:

| Asset | Purpose |
| --- | --- |
| `fabric-amper-0.1.0-maven-repository.zip` | **Recommended binary install:** plugin JAR, POM/Gradle module metadata, and both Settings/Project plugin marker POMs in Maven layout |
| `fabric-amper-0.1.0.jar` | Plugin binary for inspection or custom build integration; **not a Minecraft `mods/` JAR** |
| `fabric-amper-0.1.0-sources.jar` | Source archive for IDE inspection |
| `fabric-amper.bat` | Windows convenience script for a **full source checkout**; it is **not a standalone installer** |
| `fabric-amper.schema.json` | Latest schema snapshot for YAML editor completion |
| `SHA256SUMS.txt` | SHA-256 checksums of the uploaded release assets |

Extract the Maven repository ZIP. For a consumer that will resolve the binary from that directory (instead of using `setup`), use this `settings.gradle.kts` bootstrap and create your own `mod.yaml`/sources:

```kotlin
pluginManagement {
    repositories {
        // Change to the folder that contains the extracted maven-repo/ directory.
        maven { url = uri("C:/path/to/extracted/maven-repo") }
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("dev.fabric-amper.settings") version "0.1.0"
}
```

The repository ZIP includes **marker artifacts** required for plugin resolution. A loose plugin JAR by itself is not an equivalent substitute. Normal dependency resolution may still need Internet access; the ZIP does not bundle Minecraft, Loom, Fabric API, Kotlin Gradle Plugin, or other third-party dependencies. For automation scripts, clone the full source checkout and use the built-in `fabric-amper.bat` flow above.

## Everyday commands

Run `fabric-amper.bat` from the consumer folder, or pass `-C "C:\path\to\consumer"` before the command. If its directory is not on your `PATH`, call the script with its full path.

| Command | Purpose |
| --- | --- |
| `help`, `doctor`, `doctor --full`, `doctor --paths` | Usage, local toolchain checks, and Windows path diagnostics |
| `setup` / `init <mod_id> [folder] [mod\|api]` | Local producer publication / create a new mod or API project |
| `validate` / `build` / `verify` / `test` | Validate YAML, build and verify JAR, or run tests |
| `diagnose` / `explain` | Read-only toolchain and dependency explanations |
| `plan` / `port 1.21.11` | Inspect local release metadata / manual migration checklist; no upload or automatic porting |
| `run` / `server` | Start a Minecraft development client/server (server EULA remains your responsibility) |
| `publish local` | Explicit local API/library Maven publication under the consumer `build/` directory |
| `publish maven`, `publish modrinth`, `publish curseforge`, `publish all` | **Real external uploads** after confirmation and validation — never use them just to check syntax |

## Configuration and features

Fabric Amper supports `fabric/mod` and `fabric/api` (alias `fabric/library`). Use `settings.kotlin.enabled` for Kotlin, `settings.loom.splitEnvironmentSourceSets` and `target: client` for isolated client code, `mod.mixins` for Mixin configurations, and a profile-correct `mod.accessWidener` for Class Tweakers or legacy access wideners.

**Three dependency concepts are different:** `dependencies` / `test-dependencies` are build artifacts; `mod.depends` expresses Fabric Loader mod-ID requirements; `publish.modrinth.dependencies` / `publish.curseforge.relations` are release-site project relationships. Fabric Amper does not invent mappings between Maven coordinates, Loader IDs and external project IDs.

Optional `publish.common` shares release type/name/game-version strings/changelog and common dependencies across explicitly configured Modrinth/CurseForge targets. `publish.maven` uses an HTTPS repository and environment-variable **names**, not embedded credentials. Publishing is always an explicit operation, and multi-site uploads are not atomic.

For detailed recipes, field types, security limits and correct resource paths, use the [Quick Guide](HUMAN_QuickGuide.md), [Human Guide](HUMAN.md), and [Draft-07 JSON Schema](fabric-amper.schema.json).

## Documentation and development

- [Quick Guide](HUMAN_QuickGuide.md) — **first build + feature cheat sheet** in English.
- [Human Guide](HUMAN.md) — detailed workflow, YAML fields, publishing, and diagnostics.
- [JSON Schema](fabric-amper.schema.json) — editor autocomplete and static field constraints.
- [AI agent guide](FabricAmperSKILL.md) and [compact agent guide](FabricAmperSKILL_SYN.md) — guided configuration authoring.
- [Technical specification](specs.md) — required G1–G30 acceptance contract.
- [QA report](QA_REPORT.md) — past verification, unresolved gates, and explicit limitations.
- [Legacy README](legacy_README.md) — preserved detailed earlier README.

Developer verification from a clone:

```bat
gradle clean build validatePlugins
```

The producer Gradle build uses Java 25. Several integration/runtime tests are opt-in and may require additional Minecraft assets, installed Gradle/JDK combinations, local GUI access, or a dedicated test environment. A successful default build is **not** an unconditional shipping certification. No server EULA, account login or remote publishing is performed automatically.

## Release notes and limitations

`v0.1.0` is a **preview release** for developers evaluating the settings-only workflow. No GitHub Release substitutes for Gradle Plugin Portal publication, official Fabric Loader runtime verification for every profile, or testing an authenticated third-party upload. Please review the [QA report](QA_REPORT.md) before relying on Fabric Amper for public production releases.
