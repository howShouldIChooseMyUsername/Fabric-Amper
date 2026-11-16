[![](https://jitpack.io/v/howShouldIChooseMyUsername/Fabric-Amper.svg)](https://jitpack.io/#howShouldIChooseMyUsername/Fabric-Amper)

# Fabric Amper

**Build Fabric mods with a Settings plugin and YAML — without writing a consumer `build.gradle(.kts)` or `fabric.mod.json`.**

Fabric Amper is a **Gradle binary plugin**, not a Minecraft mod. It takes care of Fabric Loom, Minecraft/Loader configuration, Java and optional Kotlin, dependency scopes, generated Loader metadata, JAR verification, and opt-in publishing. You still write your mod's Java/Kotlin code, Mixins, resources, and tests.

> **Release status:** `v0.2.0` is a **Developer Preview**, not a fully approved G1–G30 production release. New in v0.2.0: Minecraft 26.3 declarative Architectury API (`architectury.enable: true`), `init --architectury` Java/Kotlin starters (including split client/common sources), and IntelliJ YAML dependency/Architectury completion. It retains conservative Kotlin/Groovy migration, Windows environment repair and Settings-only builds. Producer tests and consumer builds are distinct from IDE GUI/Minecraft runtime gates; see [QA_REPORT.md](QA_REPORT.md) for unverified gates. **This plugin is not published to the Gradle Plugin Portal.**

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

For the simplest setup, clone the repository and open a terminal in the **Fabric Amper checkout**:

```bat
git clone https://github.com/howShouldIChooseMyUsername/Fabric-Amper.git
cd Fabric-Amper
fabric-amper.bat doctor
fabric-amper.bat init example_mod
cd ..\example_mod
"..\Fabric-Amper\fabric-amper.bat" build
```

- `doctor` checks your Java/Gradle launcher and reports the JitPack coordinate without contacting JitPack.
- `init` creates a **new** consumer directory. `init night_vision --here` also supports a current tools-only folder; any existing project YAML, Settings/build script, source tree or conflicting schema blocks it. Existing unrelated tools and notes are preserved. Starters include `.vscode/settings.json` YAML mapping when missing.
- `init arch_mod "Architectury Mod" mod --architectury` adds the Architectury Fabric API YAML switch and a real `Platform.getMinecraftVersion()` example. Combine `--architectury --splitEnvironmentSourceSets` for separate common and client examples; `--kotlin` creates equivalent Kotlin sources. All three optional flags may be ordered freely after positional arguments.
- `build` resolves the plugin from JitPack on first use, validates YAML, compiles your mod, and verifies the resulting JAR. It does **not** upload anything.

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

The generated consumer `settings.gradle.kts` requests `id("dev.fabric-amper.settings") version "0.2.0"` and uses `pluginManagement.resolutionStrategy` to resolve the tagged module `com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.2.0` from **JitPack**, alongside Fabric Maven and other plugin repositories. It does **not** depend on a local producer Maven repository, and no `setup` step is required.

Run `gradle validateFabricAmper`, `gradle build` and `gradle verifyFabricAmperJar` directly if you prefer Gradle commands. A valid production JAR contains **one root-level generated `fabric.mod.json`**; it is not a Minecraft mod unless it also includes actual mod code.

See the [Quick Guide](HUMAN_QuickGuide.md) for complete copyable starter files and the feature cheat sheet.

## Downloading a release without building the plugin

On the [Releases page](https://github.com/howShouldIChooseMyUsername/Fabric-Amper/releases), the preview release includes:

| Asset | Purpose |
| --- | --- |
| `fabric-amper-0.2.0-maven-repository.zip` | **Optional local binary install:** plugin JAR, POM/Gradle module metadata, and both Settings/Project plugin marker POMs in Maven layout |
| `fabric-amper-0.2.0.jar` | Plugin binary for inspection or custom build integration; **not a Minecraft `mods/` JAR** |
| `fabric-amper-0.2.0-sources.jar` | Source archive for IDE inspection |
| `fabric-amper.bat` | Windows convenience script; `doctor --fix` downloads the matching helper on first use. `migrate` requires the separate matching PowerShell script beside this BAT |
| `fabric-amper-migrate.ps1` | Standalone conservative migration engine for Kotlin/Groovy Gradle scripts and Loader JSON; no Gradle script execution |
| `fabric-amper-doctor-fix.ps1` | Single opt-in Windows repair helper; BAT downloads **only this file** from its matching version's GitHub Release into the BAT directory when absent and checks the pinned SHA-256 before execution |
| `fabric-amper-0.2.0-windows-tools.zip` | Optional BAT, both matching PowerShell helpers, schema and AI skills/guides, pre-bundled for extraction |
| `fabric-amper.schema.json` | Latest schema snapshot for YAML editor completion |
| `SHA256SUMS.txt` | SHA-256 checksums of the uploaded release assets |

**JitPack is the default installation route** used by `init`. For a manually authored consumer, this is the full Settings bootstrap:

```kotlin
pluginManagement {
    repositories {
        maven("https://jitpack.io")
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "dev.fabric-amper.settings") {
                useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.2.0")
            }
        }
    }
}
plugins {
    id("dev.fabric-amper.settings") version "0.2.0"
}
```

For an **optional local binary distribution**, extract the Maven repository ZIP and replace the JitPack repository and `resolutionStrategy` block with `maven { url = uri("C:/path/to/extracted/maven-repo") }` in `pluginManagement.repositories`. The ZIP includes **plugin marker artifacts**; a loose plugin JAR by itself is not equivalent. The ZIP does not bundle Minecraft, Loom, Fabric API, Kotlin Gradle Plugin, or other dependencies, so normal dependency resolution may still require Internet access. For the Windows convenience commands, use a full source checkout.

## Everyday commands

Run `fabric-amper.bat` from the consumer folder, or pass `-C "C:\path\to\consumer"` before the command. **For `doctor` and `migrate`**, `-C` / `--project` may also follow the command: `fabric-amper.bat doctor -C ".\httpmod"` or `fabric-amper.bat migrate -C ".\httpmod"`. The redundant trailing `doctor` in `doctor -C ".\httpmod" doctor` is accepted for compatibility. Relative paths are resolved from the **current terminal directory**, not the BAT location. If its directory is not on your `PATH`, call the script with its full path.

| Command | Purpose |
| --- | --- |
| `help`, `doctor`, `doctor --full`, `doctor --paths` | Usage, local toolchain checks, and Windows path diagnostics |
| `doctor --fix --dry-run` / `doctor --fix` | Read-only repair preview / missing JDK25 and Gradle repair plus missing consumer editor/Settings bootstrap; an AI agent may run `--fix` automatically after `doctor` finds a required tool missing |
| `init <mod_id> [folder] [mod\|api]` | Create a new mod or API project with JitPack plugin resolution; `setup` is no longer needed |
| `init <mod_id> --here [mod\|api]` | Initialize a tools-only directory without overwriting existing project files |
| `migrate [--dry-run] [-D\|--delete]` | Convert a supported existing Kotlin/Groovy Fabric Gradle project; preview without changes; default backup to `gradle_backup`, or delete originals after successful conversion |
| `validate` / `build` / `verify` / `test` | Validate YAML, build and verify JAR, or run tests |
| `diagnose` / `explain` | Read-only toolchain and dependency explanations |
| `plan` / `port 1.21.11` | Inspect local release metadata / manual migration checklist; no upload or automatic porting |
| `run` / `server` | Start a Minecraft development client/server (server EULA remains your responsibility) |
| `publish local` | Explicit local API/library Maven publication under the consumer `build/` directory |
| `publish maven`, `publish modrinth`, `publish curseforge`, `publish all` | **Real external uploads** after confirmation and validation — never use them just to check syntax |

**AI agent automatic toolchain recovery (Windows):** Before a task requiring JDK or Gradle, run `fabric-amper.bat doctor`. If the required tools are available, continue the task. If a required JDK/Gradle tool is missing or unusable, inspect `fabric-amper.bat doctor --fix --dry-run` and then **run `fabric-amper.bat doctor --fix` automatically without asking for a separate confirmation**. This automatic-repair policy applies only to Fabric Amper's missing-toolchain/bootstrap repair, **not** to publishing, game EULA acceptance, arbitrary software installation, overwriting existing project files, or other unrelated changes. The BAT downloads only its version-matched PowerShell helper if missing, verifies its pinned SHA-256 before execution, and stops on a hash mismatch or download failure. Re-run `doctor` after repair (reopen the terminal if PATH/JAVA_HOME changes require it); resume the original task only when its prerequisites are verified. If repair fails, report the blocker instead of retrying indefinitely. With no local helper, `--dry-run` previews only the helper download, not the actual toolchain repair.

### Migrate an existing Fabric Gradle mod

Place `fabric-amper.bat` and **the matching `fabric-amper-migrate.ps1`** side by side. From a classic Fabric project with one `build.gradle.kts` **or** Groovy `build.gradle` and one `src/main/resources/fabric.mod.json`, run:

```bat
C:\path\to\Fabric-Amper\fabric-amper.bat migrate --dry-run
C:\path\to\Fabric-Amper\fabric-amper.bat migrate
C:\path\to\Fabric-Amper\fabric-amper.bat migrate -C ".\another-mod" --delete
```

`migrate -D` is an alias for `--delete`; `-C`/`--project` can also go before `migrate`. **Without** a delete flag, original Gradle/Loader metadata (including existing Settings, if present) is saved in `gradle_backup/`; the source `fabric.mod.json` is moved out to avoid FA008. **With** a delete flag, originals are removed only after outputs are generated. Existing Java/Kotlin sources and other resources are not intentionally changed. The static analyzer refuses unsupported/dynamic build logic, unexpected metadata, ambiguous source layouts, conflicting outputs or an existing backup folder rather than silently discarding behavior. A successful conversion is **not** proof that the new consumer will build: review the generated YAML and run `validate`, `build`, and `verify` before relying on it. Keep your own backup before using `--delete`.

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

`v0.2.0` is a **Developer Preview** for Settings-only YAML projects, JitPack bootstrap, Minecraft 26.3 Architectury API opt-in, `init --architectury`, and conservative Gradle-to-YAML migration. Earlier [v0.1.3](https://github.com/howShouldIChooseMyUsername/Fabric-Amper/releases/tag/v0.1.3) and [v0.1.2](https://github.com/howShouldIChooseMyUsername/Fabric-Amper/releases/tag/v0.1.2) remain available. A GitHub Release does not substitute for Gradle Plugin Portal publication, complete Fabric Loader runtime verification, or testing authenticated third-party uploads. Review [QA_REPORT.md](QA_REPORT.md) for open gates.
