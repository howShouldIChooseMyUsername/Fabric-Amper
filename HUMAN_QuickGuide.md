# Fabric Amper — Quick Guide

**A quick start for Fabric, NeoForge, and dual-loader Minecraft mods.** Windows-first command recipes and a compact reference for the published v0.3.0 prerelease with schema 1.1. For the expanded guide, see [the Human Guide](HUMAN.md); for editor field types, see [the schema](fabric-amper.schema.json).

> **v0.3.0 Developer Preview — published prerelease (2026-11-17).** The BAT and generated Settings target **0.3.0**. Gradle 9.7.1 `clean build validatePlugins --offline` passed **371 tests (314 PASS, 57 SKIP)**. All nine GitHub release assets were redownloaded and SHA-256 verified; JitPack `v0.3.0` resolved and a fresh NeoForge + Architectury + Kotlin remote-plugin consumer passed `help`, validation, build and JAR verification. Schema 1.1 NeoForge and dual products also have selected TestKit builds, **not** complete Minecraft runtime/IntelliJ GUI/strict cold-cache approval. Dual BAT `build`/`validate`/`verify` dispatch was checked with fake Gradle. [QA_REPORT.md](QA_REPORT.md) records evidence.

## Contents

1. [The two-minute command path](#the-two-minute-command-path)
2. [Migrate an existing Kotlin/Groovy Fabric mod](#migrate-an-existing-kotlingroovy-fabric-mod)
3. [Minimal project: copy and run](#minimal-project-copy-and-run)
4. [Feature cheat sheet](#feature-cheat-sheet)
5. [Dependencies, side safety, and test recipes](#dependencies-side-safety-and-test-recipes)
6. [Shared Modrinth and CurseForge release settings](#shared-modrinth-and-curseforge-release-settings)
7. [Publishing and API/library projects](#publishing-and-apilibrary-projects)
8. [Windows command cheat sheet](#windows-command-cheat-sheet)
9. [Troubleshooting and next steps](#troubleshooting-and-next-steps)
10. [Historical schema 1 reference](#historical-schema-1-reference)

## v0.3.0 quick start — schema 1.1

**Schema policy for published v0.3.0:** omitted `schema` **defaults to 1.1** (implemented and regression-tested); `schema: "1.1"` explicitly opts into it; **`schema: 1` keeps the legacy Fabric-only grammar**. Do not use unquoted `schema: 1.1`. Use exactly one **`mod.yaml` or `mod.yml`**, not both. Place Fabric Loader keys in `fabric:` and NeoForge keys in `neoforge:`; common mod identity stays under `mod:`.

**Choose what to generate (Windows):**

```bat
fabric-amper.bat init examplemod                                   rem Fabric default
fabric-amper.bat init examplemod neomod mod --mod-neoforge          rem NeoForge only
fabric-amper.bat init examplemod sharedmod mod --mod-fabric --mod-neoforge --architectury
fabric-amper.bat init examplemod independent mod --mod-fabric --mod-neoforge --kotlin
fabric-amper.bat init examplemod separate_client mod --mod-fabric --mod-neoforge --splitEnvironmentSourceSets
fabric-amper.bat init examplemod --here --mod-neoforge --kotlin
```

All generated consumers have `settings.gradle.kts` and one YAML plus editor schema and **no consumer build scripts or manual Loader metadata**. Default language is **Java**; `--kotlin` creates real Kotlin source and sets `settings.kotlin.enabled: true`. `--mod-neoforge` alone selects NeoForge. Both loader flags create distinct `fabric/` and `neoforge/` modules; **only with `--architectury`** is an additional loader-neutral `common/` module created. Independent dual means no shared module/code. `--splitEnvironmentSourceSets` is Fabric-only: NeoForge-only rejects it; dual Architectury explicitly ignores it; dual independent applies it only to Fabric. These features are included in the v0.3.0 published prerelease; game runtime acceptance remains separate.

**Published v0.3.0 Settings bootstrap**, verified with a public JitPack remote-plugin consumer build:

```kotlin
pluginManagement {
    repositories {
        maven("https://jitpack.io")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "dev.fabric-amper.settings") {
                useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.3.0")
            }
        }
    }
}
plugins {
    id("dev.fabric-amper.settings") version "0.3.0"
}
```

**NeoForge-only YAML** (the explicit quoted schema is equivalent to omission on v0.3.0):

```yaml
schema: "1.1"
product: neoforge/mod
mod:
  id: examplemod
  version: "1.0.0+26.3"
  name: Example Mod
  license: MIT
minecraft:
  version: "26.3"
settings:
  java:
    release: 25
neoforge:
  version: "26.3.0.101"
repositories: [mavenCentral, neoforge]
```

Write a real Java/Kotlin `@Mod("examplemod")` class. The JAR contains generated **`META-INF/neoforge.mods.toml`** and **no `fabric.mod.json`**. NeoForge mod IDs use lowercase letters/digits/underscores; the common `mod.license` is mandatory.

**Fabric-only YAML** uses the same root identity but this loader-specific block; replace the NeoForge `product` and `neoforge` section (fragment, not a second whole document):

```yaml
product: fabric/mod
fabric:
  loader: "0.20.1"
  api: "0.165.2+26.3"
  mappings: mojang
  entrypoints:
    main: [org.example.ExampleFabric]
```

Write `ExampleFabric implements ModInitializer` under `src/main/java` (or Kotlin equivalent). The Fabric JAR contains **exactly one generated root `fabric.mod.json`** and no NeoForge TOML.

**Dual-loader YAML** uses the same shared root `mod/minecraft/settings` (and `license`), with `product: [fabric/mod, neoforge/mod]`, both loader blocks above, and optionally:

```yaml
architectury:
  enable: true
```

With Architectury, put shared code in `common/src/main/java|kotlin`, Fabric initialization in `fabric/src/main/java|kotlin` and NeoForge `@Mod` initialization in `neoforge/src/main/java|kotlin`. Without Architectury, only the two loader-specific trees exist. Dependencies in the root are neutral/common intent, while `fabric.gradleDependencies` and `neoforge.gradleDependencies` are loader-specific; `fabric.depends` and `neoforge.dependencies` are distinct runtime metadata. All three root artifact/repository collections use YAML **arrays** in both schema versions.

For loader-specific dependencies, merge this **schema 1.1 fragment** into an existing dual YAML file (replace the illustrative `other_mod` with a real NeoForge mod ID):

```yaml
fabric:
  depends:
    fabricloader: ">=0.20.1"
  gradleDependencies:
    - maven: "org.apache.commons:commons-lang3:3.14.0"
      scope: compile-only
neoforge:
  dependencies:
    - modId: other_mod
      type: optional
      versionRange: "[1.0,)"
      side: BOTH
  gradleDependencies:
    - maven: "org.apache.commons:commons-lang3:3.14.0"
      scope: compile-only
```

These are additions to the already existing `fabric:` and `neoforge:` sections, **not replacement blocks**: keep their required `loader` and `version`. `fabric.depends` is a mod-ID predicate **map**; `neoforge.dependencies` and both `gradleDependencies` collections are **arrays**. The Maven example is a compile-time classpath artifact and does not automatically declare a Loader runtime requirement.

**Build and run:**

```bat
cd sharedmod
gradle build
cd ..
fabric-amper.bat -C "sharedmod" run --mod-fabric
fabric-amper.bat -C "sharedmod" run --mod-neoforge
fabric-amper.bat -C "sharedmod" server --mod-fabric
fabric-amper.bat -C "sharedmod" server --mod-neoforge
```

For one loader, unqualified `run` / `server` map to `runClient` / `runServer`. For dual loaders, **exactly one selector is required**: `runFabricClient`, `runNeoForgeClient`, `runFabricServer` or `runNeoForgeServer`. Unselected, mismatched or arbitrary flags are rejected. **The current BAT `build`, `validate` and `verify` select both `:fabric` and `:neoforge` tasks for dual projects**, while single-loader commands use the original root tasks. The selector preflight and task routing have fake-Gradle tests. Build/TestKit success does **not** establish real client/server startup, IntelliJ GUI sync or immediate cold configuration-cache HIT; the `:common` JAR has previously triggered a one-time second-run CC miss. Minecraft EULA review remains a human action; Fabric Amper never auto-accepts it.

The rest of this guide uses **schema 1.1** for all new copyable YAML examples. For a previously generated Fabric-only schema 1 project, consult the single [historical schema 1 reference](#historical-schema-1-reference).

## The two-minute command path

**Check the launcher version:** `fabric-amper.bat` without arguments or with `-V` / `--version` prints its embedded version without Gradle. It **should print `v0.3.0` when the release files are synchronized**. An older value means the BAT is not the release-ready build; use `help` for command details.

**Prerequisites:** Windows, a compatible Gradle launcher (**9.7.1** is the project validation baseline), and **JDK 25** for the Minecraft **26.3** example. The Fabric Amper producer repository does not require a bundled Gradle Wrapper. The launcher finds installed/cached Gradle; **`doctor --fix` explicitly opts into repairing a missing JDK 25 or Gradle**, while `doctor --fix --dry-run` only previews the actions. From the checked-out **`Fabric Amper` producer folder**, run:

```bat
fabric-amper.bat doctor
fabric-amper.bat init quick_mod
fabric-amper.bat init test_mode "Test Mode" mod --kotlin
fabric-amper.bat init strict_mod "Strict Mod" mod --splitEnvironmentSourceSets
fabric-amper.bat init strict_kotlin "Strict Kotlin" mod --splitEnvironmentSourceSets --kotlin
fabric-amper.bat init arch_single "Architectury Single" mod --architectury
fabric-amper.bat init arch_split "Architectury Split" mod --architectury --splitEnvironmentSourceSets
fabric-amper.bat init arch_kotlin "Architectury Kotlin" mod --architectury --kotlin
cd ..\quick_mod
"..\Fabric Amper\fabric-amper.bat" build
```

**What happened?** `doctor` checks Java/Gradle locally; `init` creates `settings.gradle.kts`, one `mod.yaml`, a verified `schemas/mod.schema.json` snapshot and matching Java (or `--kotlin`) sources. Fabric-only projects use `ModInitializer`, while NeoForge uses `@Mod`. The current Fabric-only BAT starter still uses its [historical compatibility layout](#historical-schema-1-reference); the manually authored schema 1.1 example below illustrates the newer syntax. The standalone BAT may fetch a missing schema from GitHub `main` after comparing its pinned SHA-256; a stale BAT must not silently accept changed bytes. Published v0.3.0 Settings request JitPack `v0.3.0`; check the generated file when using an older local BAT. A clean `build` may require first-use network downloads. The YAML editor directive resolves to the consumer's local `schemas/mod.schema.json`. Existing project destinations are never overwritten.

**Fabric client split:** `--splitEnvironmentSourceSets` creates a Fabric client initializer in `src/client` (`fabric/src/client` for dual) and the matching `fabric.entrypoints.client` and `fabric.loom.splitEnvironmentSourceSets` in schema 1.1. `src/main` is shared code, not server-only. NeoForge-only split is refused and dual Architectury split is ignored with a diagnostic; independent dual split affects Fabric alone. The older Fabric starter syntax is documented in the [historical reference](#historical-schema-1-reference).

**Architectury:** `--architectury` enables the common YAML `architectury.enable: true`. On dual-loader projects, it generates **three** modules (`common/`, `fabric/` and `neoforge/`) with a shared `ExampleCommon` and two independent loader entrypoints. On single-loader projects it adds the matching platform Architectury API without `common/`. The default version is `22.0.5` for Minecraft 26.3; a fresh public v0.3.0 NeoForge + Architectury + Kotlin consumer built, while real game runtime remains unapproved.

Useful follow-ups from inside `quick_mod`:

```bat
"..\Fabric Amper\fabric-amper.bat" diagnose
"..\Fabric Amper\fabric-amper.bat" explain
"..\Fabric Amper\fabric-amper.bat" plan
```

`diagnose` reports local toolchains (not IDE or Minecraft runtime); `explain` reports declared/injected dependencies; `plan` verifies a **local** JAR and previews release metadata **without uploading**. `doctor --fix --dry-run` previews a repair without downloading. The opt-in `doctor --fix` may fetch its versioned `scripts/fabric-amper-doctor-fix.ps1` helper, verify a BAT-pinned SHA-256 and install missing JDK/Gradle components; first check that the helper matches the **actual installed launcher version**, and that the matching GitHub Release asset exists. It does not auto-change the parent CMD session's PATH.

**Single-BAT bootstrap for published v0.3.0:** Matching helper assets were included among the SHA-256-verified GitHub prerelease downloads. The versioned doctor helper URL is `.../releases/download/v0.3.0/fabric-amper-doctor-fix.ps1`; normal `migrate` may fetch its corresponding SHA-256-pinned PS1 and schema; `init` fetches a missing schema from GitHub `main` with its own exact hash check. Never overwrite or execute mismatched helpers. `doctor --fix --dry-run` and `migrate --dry-run` remain read-only, and a missing helper means they can preview only its download until the verified helper is present.

### Before you edit anything

- A consumer maintains **only `settings.gradle.kts` and exactly one `mod.yaml` or `mod.yml` for build configuration**. Do **not** add a consumer `build.gradle`, `build.gradle.kts`, or a manually authored `src/main/resources/fabric.mod.json` (or client equivalent). You **still write** actual Java/Kotlin classes, Mixins, icons and other assets.
- Root `schema` is **optional** only if you intend the latest grammar (**1.1 in v0.3.0**); set **`schema: 1`** for legacy snippets below. The v1.1 `init` templates that target NeoForge/dual currently write `schema: "1.1"` explicitly. `schemas/mod.schema.json` is an initialization snapshot, not a remote auto-updated schema. `fabricAmperDoctor` and related names are **Gradle tasks**, not YAML keys.
- Quote exact Minecraft, Loader, Fabric API and Maven version strings. Avoid YAML aliases/anchors, duplicate keys, unverified dependency coordinates and guessed platform IDs. The examples below are **demonstrations**, not guarantees that external packages or projects exist.
- `build` does **not** upload anything. `publish maven`, `publish modrinth`, `publish curseforge`, and `publish all` **can upload real releases**; review the exact destination, author permissions and credentials first. The wrapper asks for typed `PUBLISH` for these remote commands, but that prompt is not a substitute for explicit authorization.

## Migrate an existing Kotlin/Groovy Fabric mod

**Legacy Fabric-only migration:** use `migrate` when an existing project has one supported `build.gradle.kts` (Kotlin DSL) **or** `build.gradle` (Groovy DSL) and one Loader `fabric.mod.json`. The migration produces an **explicit `schema: 1`** config; it does **not** auto-port to NeoForge or the new namespaces and does not evaluate the old Gradle script.

You can start with **just `fabric-amper.bat`** in a writable tools folder. Normal `migrate` automatically downloads and SHA-256 verifies its matching **`scripts/fabric-amper-migrate.ps1`** and root schema from the versioned GitHub Release; no manual PS1 installation is needed. If this is a BAT-only installation, the **first `--dry-run` only previews the helper download without touching files**. To inspect the actual candidate YAML with `--dry-run`, use a complete checkout/Windows Tools ZIP or run a regular `migrate` once on a suitable test project to bootstrap the tools. To preview and then convert from the **existing mod's directory**:

```bat
"C:\tools\Fabric-Amper\fabric-amper.bat" migrate --dry-run
"C:\tools\Fabric-Amper\fabric-amper.bat" migrate
```

To target another folder or explicitly remove original build files **after** a successful conversion:

```bat
"C:\tools\Fabric-Amper\fabric-amper.bat" migrate -C "C:\mods\my-mod" --dry-run
"C:\tools\Fabric-Amper\fabric-amper.bat" -C "C:\mods\my-mod" migrate
"C:\tools\Fabric-Amper\fabric-amper.bat" migrate --project "C:\mods\my-mod" -D
"C:\tools\Fabric-Amper\fabric-amper.bat" migrate -C "C:\mods\my-mod" --delete --dry-run
```

| Mode | What happens to the old `build.gradle(.kts)`, original `fabric.mod.json` and any old `settings.gradle(.kts)` |
| --- | --- |
| `migrate --dry-run` | When the helper and schema are verified locally, display proposed `mod.yaml`; if missing, preview only the download. **No changes/downloads** |
| `migrate` | Download and verify any missing helper/schema, then create `mod.yaml` + `settings.gradle.kts` and back up originals in **`gradle_backup/`** |
| `migrate -D` / `migrate --delete` | Create the same outputs and remove originals on success; **no `gradle_backup/` copies** |
| `migrate --delete --dry-run` | Preview the delete policy without deleting anything |

For both DSLs, `migrate` preserves `src/`, Mixins, Class Tweakers and icons. The **published v0.3.0** generator requests its version-matched JitPack module and pins converted YAML to the older Fabric grammar (see [historical reference](#historical-schema-1-reference)); old BATs may pin earlier versions, so check actual generated Settings. It rejects existing YAML, `gradle_backup`, conflicting root scripts, ambiguous Loader JSON and unsupported/unsafe Gradle syntax. It does not rewrite custom tasks or guarantee compilation or gameplay. Prefer an ordinary backed-up migration; then review `mod.yaml`, run `validate` / `build`, and separately test runtime. See [the full migration guide](HUMAN.md#migrating-an-existing-fabric-gradle-mod-windows).

## Minimal project: copy and run

Skip this section if you used `init`; it already created the three authored files **and** the local schema file for editor completion. For a **manual** consumer, create the following structure (copy the editor schema from the Fabric Amper checkout):

```text
quick_mod/
    ├── settings.gradle.kts
    ├── mod.yaml
    ├── schemas/mod.schema.json              (copy from producer)
    └── src/main/java/org/example/ExampleMod.java
```

**`settings.gradle.kts`** — bootstrap the **single public** Settings plugin:

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
                useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.3.0")
            }
        }
    }
}
plugins {
    id("dev.fabric-amper.settings") version "0.3.0"
}
```

The example requests the **published v0.3.0 prerelease** from JitPack, which was verified with a fresh remote-plugin NeoForge + Architectury + Kotlin consumer. The version requested by `plugins` is `0.3.0` while the JitPack Git tag is `v0.3.0`. Do not separately apply `dev.fabric-amper.project` and do not use the removed `setup` workflow.

**`mod.yaml`** — a complete Java-only **schema 1.1** Fabric Minecraft 26.3 example. The two comments enable local schema completion; `schema: "1.1"` can also be omitted with v0.3.0:

```yaml
# yaml-language-server: $schema=./schemas/mod.schema.json
# $schema: ./schemas/mod.schema.json
schema: "1.1"
product: fabric/mod
mod:
  id: quick_mod
  version: "1.0.0+26.3"
  name: Quick Mod
minecraft:
  version: "26.3"
fabric:
  loader: "0.20.1"
  api: "0.165.2+26.3"
  mappings: mojang
  entrypoints:
    main: [org.example.ExampleMod]
settings:
  java:
    release: 25
  kotlin:
    enabled: false
repositories:
  - fabric
  - mavenCentral
```

When creating the project manually rather than using `init`, first create a `schemas` directory beside `mod.yaml` and copy the producer's current `fabric-amper.schema.json` to `schemas/mod.schema.json`. The first comment is for YAML Language Server editors; the second (`# $schema:`) is the IntelliJ IDEA local JSON Schema directive. Both are YAML **comments**, not Fabric Amper configuration keys. New `init` projects include both directives and the local schema copy automatically; existing projects must refresh the copy after a schema update.

**`src/main/java/org/example/ExampleMod.java`** — actual mod code, not generated metadata:

```java
package org.example;

import net.fabricmc.api.ModInitializer;

public final class ExampleMod implements ModInitializer {
    @Override public void onInitialize() {
        System.out.println("Quick Mod initialized");
    }
}
```

From the **consumer** directory, use an installed compatible Gradle:

```bat
gradle help
gradle tasks --all
gradle validateFabricAmper
gradle build
gradle verifyFabricAmperJar
```

The expected output is `build/libs/quick_mod-1.0.0+26.3.jar`. It must contain **exactly one root-level `fabric.mod.json`**, generated by Fabric Amper from `mod.yaml`, with the matching `id` and `version`. A successful command exit and archive verification establish a local build; they do **not** establish live game compatibility. Alternatively, from the consumer folder, use `"C:\path\to\Fabric Amper\fabric-amper.bat" build` when Gradle is not on `PATH` but the launcher can find it.

## Feature cheat sheet

**All YAML snippets after the complete example are FRAGMENTS.** Insert their fields into the existing `mod.yaml` structure. **Merge under existing top-level maps**; do not paste a second `mod:`, `settings:`, `repositories:`, or `publish:` block into the same file.

| I want to… | YAML location / value | What else I must do |
| --- | --- | --- |
| Name my mod | `mod.name: My Mod` | Give it a real unique `mod.id` and version |
| Add a PNG icon | `fabric.icon: assets/quick_mod/icon.png` | Put the PNG under `src/main/resources/assets/quick_mod/icon.png` |
| Add a Fabric entrypoint | `fabric.entrypoints.main` or `.client` | Implement the Java/Kotlin initializer class |
| Set a client-only mod | `fabric.environment: client` | Use client-safe entrypoints/Mixins; do not load client-only code on a dedicated server |
| Enable Kotlin | `settings.kotlin.enabled: true` | Use `src/main/kotlin`; fixed KGP `2.4.21` / Fabric Language Kotlin `1.14.2+kotlin.2.4.21` |
| Enable Architectury API (Minecraft 26.3) | `architectury.enable: true` | Adds the official Maven repo and platform API; dual targets also gain `common/`; default `22.0.5` |
| Split client source sets | `fabric.loom.splitEnvironmentSourceSets: true` | Use `src/client/java` and optionally `src/client/resources` |
| Add a client dependency | `dependencies[].target: client` | Requires split source sets; not allowed with `exported` or `include` |
| Register a Mixin | `fabric.mixins: [example.mixins.json]` | Add the actual config and mixin classes; side-qualify client-only configs |
| Change Minecraft access | `fabric.accessWidener: example.classtweaker` on 26.1+ | Actual file in `src/main/resources/`; header `classTweaker v1 official` |
| Target a legacy remap profile | `minecraft.version: "1.21.11"` and `settings.java.release: 21` | Change mappings/Loader/API to verified compatible legacy versions; use `.accesswidener` instead |
| Require another installed mod | `fabric.depends` | Use **Fabric Loader mod ID + version predicate**, not Maven coordinates |
| Add test libraries | `test-dependencies` | Write tests in `src/test/java` or `src/test/kotlin`; run `gradle test` |
| Build a reusable API/library | `product: fabric/api` (alias `fabric/library`) | Set **`mod.group`**; local Maven publication becomes available |
| Add release destinations | `publish.maven`, `.modrinth`, `.curseforge` | Configure only verified HTTPS repo/project IDs and environment-variable **names** |

The 26.3 default uses **non-remapping Loom**, Mojang names and **Java release 25**. Legacy 1.21.11 uses a **remapping** Loom path with Java 21; change more than the Minecraft version when porting. You can inspect a manual migration checklist without changing code or YAML with `fabric-amper.bat port 1.21.11` (or direct Gradle `fabricAmperPortingReport -PfabricAmperPortTarget=1.21.11`).

### Kotlin, Mixins and resources

For Kotlin, merge **under the existing `settings` block**:

```yaml
settings:
  kotlin:
    enabled: true
    pluginVersion: "2.4.21"
    languageVersion: "2.4"
fabric:
  kotlin:
    fabricLanguageKotlin: "1.14.2+kotlin.2.4.21"
```

If the complete starter already contains `settings.kotlin` or `fabric`, merge these fields into those mappings **rather than duplicating them**. For client-only classes, set `fabric.loom.splitEnvironmentSourceSets: true`, put code under `src/client/java` or `src/client/kotlin`, and define `fabric.entrypoints.client`. A Mixin config or icon must exist inside the allowed resources source set; a global/server Mixin cannot safely reference only a client resource. A `.classtweaker` on Minecraft 26.1+ and a legacy `.accesswidener` both belong under `src/main/resources` and require profile-correct headers.

### Architectury API in one switch (Minecraft 26.3)

Add this **top-level** section beside `settings`, not under `dependencies`:

```yaml
architectury:
  enable: true
  # version: "22.0.5"  # optional override
```

The v0.3.0 prerelease supplies Architectury API `22.0.5` by default for Minecraft 26.3; `version` overrides it with an explicit fixed version. `enabled: true` is also accepted as an alias, but do not write both `enable` and `enabled`. Fabric Amper automatically registers `https://maven.architectury.dev/`, the appropriate platform Architectury artifact, and for Fabric `fabric.depends.architectury`. If `fabric.api` is absent it selects `0.165.2+26.3`; an explicit Fabric API version is preserved. No `gradle.properties` or consumer `build.gradle(.kts)` is needed. Dual targets with Architectury create `common/`, `fabric/` and `neoforge/`; one loader uses its platform API without a `common/` project. The API is a separately installed runtime mod, not silently embedded with `include`. Other Minecraft profiles are rejected until explicitly supported. Reload IntelliJ's Gradle project after editing `mod.yaml` or `mod.yml`.

## Dependencies, side safety, and test recipes

**Three different dependency systems must not be conflated:**

| Place in YAML | Purpose | Identifier |
| --- | --- | --- |
| `dependencies` / `test-dependencies` | Gradle compilation/runtime/test artifacts | Fixed Maven `group:artifact:version` or explicit Modrinth artifact data |
| `fabric.depends` (and `recommends`, `suggests`, `breaks`, `conflicts`) | Constraints verified by Fabric Loader at game runtime | Loader **mod ID** and version predicate, e.g. `fabricloader: ">=0.20.1"` |
| `neoforge.dependencies[]` | NeoForge TOML runtime relationships | Separate array of NeoForge relationship objects, not Maven GAV |
| `publish.modrinth.dependencies` / `publish.curseforge.relations` | External release-page project relationships | Verified **platform project ID or slug**, not Maven GAV or Loader ID |

**Artifact dependency fragment** — replace fictional `org.example` coordinates with real published artifacts. These snippets belong inside a single existing `dependencies` list:

```yaml
dependencies:
  - maven: "org.example:real-api-artifact:1.2.3"
    scope: all
    exported: true
  - maven: "org.example:real-helper:2.0.0"
    scope: all
    include: true
  - maven: "org.example:compiler-annotations:1.0.0"
    scope: compile-only
```

| Option | What it does | Restriction |
| --- | --- | --- |
| `scope: all` | Available for compilation and runtime | Default |
| `scope: compile-only` | Compile classpath only | Cannot combine with `include` or `exported` |
| `scope: runtime-only` | Runtime classpath only | Cannot combine with `include` or `exported` |
| `exported: true` | Exposes an API/library dependency to downstream consumers | `scope: all`, target `main`; not a substitute for `fabric.depends` |
| `include: true` | Embeds a **non-transitive** JAR-in-JAR artifact | `scope: all`, target `main`; verify runtime loading separately |
| `target: client` | Client source-set dependency | Requires split source sets, non-remap profile, no `exported`/`include` |
| `kind: mod` / `library` | Selects the supported Gradle mod/library configuration | Choose based on what the artifact actually contains |

String shorthand, a one-key scope map (`"group:artifact:version": compile-only`), and typed `maven`/`modrinth` objects are supported. Fixed versions only: do not use `latest.release`, dynamic ranges, or Maven coordinates with guessed versions. `test-dependencies` follow analogous scopes but **cannot** use `target: client`, `exported: true`, or `include: true`. For a JUnit Jupiter sample and repository details, see [the complete Human Guide](HUMAN.md#repositories-and-dependencies).

### Loader relationships versus build dependencies

For a Fabric Loader installed-mod requirement, merge into the existing `fabric` mapping:

```yaml
fabric:
  depends:
    fabricloader: ">=0.20.1"
    minecraft: "26.3"
```

Fabric Amper injects missing core Loader requirements for Minecraft, Fabric Loader, Java, and optional Fabric API/Kotlin; it **does not overwrite explicit predicates**. Use `fabric-amper.bat explain` to see which relationships were authored versus injected. A YAML `fabric.depends` requirement does **not** download the referenced mod: add an actual Gradle artifact dependency separately when needed.

## Shared Modrinth and CurseForge release settings

`publish.common` prevents repeating a release title, game-version list, changelog and dependency relationships. The following is a **fragment**, not a second standalone YAML document. Add it to the **same** `mod.yaml` only after you have real project IDs and have checked the release metadata:

```yaml
publish:
  common:
    releaseType: release
    name: "Quick Mod 1.0.0"
    gameVersions: ["26.3"]
    changelogFile: CHANGELOG.md
    dependencies:
      - slug: fabric-api
        modrinthId: fabric-api
        curseforgeId: "306612"
        type: required
  modrinth:
    projectId: "REPLACE_WITH_REAL_MODRINTH_PROJECT"
    tokenEnv: MODRINTH_TOKEN
    loaders: [fabric]
  curseforge:
    projectId: 123456
    tokenEnv: CURSEFORGE_TOKEN
    gameVersionNames: ["26.3", "Fabric", "Client", "Server"]
```

This example uses **placeholder project IDs**. `changelogFile` must be an actual safe project-relative file (for example `CHANGELOG.md`) with no escaping symlinks. Do not put a token value in YAML: `tokenEnv` names a secret-bearing environment variable provided at execution time.

| Shared dependency `type` | Modrinth dependency type | CurseForge relation type |
| --- | --- | --- |
| `required` | `required` | `requiredDependency` |
| `optional` | `optional` | `optionalDependency` |
| `incompatible` | `incompatible` | `incompatible` |
| `embedded` | `embedded` | `embeddedLibrary` |

- `common.name` becomes the default Modrinth `versionName` and CurseForge `displayName`. `common.gameVersions` supplies Modrinth's **string** `gameVersions` and CurseForge's **string** `gameVersionNames`, unless overridden. CurseForge's separate `gameVersions` field is a list of **verified positive integer platform version IDs**, never inferred from `"26.3"`.
- `slug` is the CurseForge relation slug and fallback Modrinth project ID; optional `modrinthId` can override that fallback and optional `curseforgeId` is a **numeric string** (not a Maven coordinate). Project IDs across platforms are not assumed to match.
- Platform-specific fields **override** shared values. Platform `dependencies` / `relations` entries replace matching inherited identifiers and append new ones; an explicitly empty `[]` clears inherited relationships for that platform. A platform's `changelog` or `changelogFile` replaces the entire common changelog source. Inline `changelog` and `changelogFile` are mutually exclusive at each level.
- `publish.common` **cannot create an upload destination by itself**. Explicit `publish.modrinth` / `publish.curseforge` entries still need their own existing `projectId` and `tokenEnv`. `releaseType` must be `release`, `beta`, or `alpha`.

Preview the resolved JAR/release data with **`fabric-amper.bat plan`**. It does **not** test external credentials, confirm platform tags, create projects, or upload files.

## Publishing and API/library projects

Set `product: fabric/api` (or exact alias `fabric/library`) and a non-empty `mod.group` for a reusable library. The plugin creates a **consumer-project-local** Maven publication at `build/fabric-amper/maven-repository/`; the explicit local task is `publishMavenJavaPublicationToFabricAmperLocalRepository` (Windows shortcut: `fabric-amper.bat publish local`). The main artifact is the production JAR with one root `fabric.mod.json`; an optional sources JAR, POM and Gradle `.module` are also produced.

For an explicitly requested **remote Maven** destination, add this **fragment** to an existing YAML with `mod.group`:

```yaml
publish:
  maven:
    url: https://maven.pkg.github.com/OWNER/REPOSITORY
    credentialsFromEnv: [GITHUB_ACTOR, GITHUB_TOKEN]
```

`publish.maven.url` must be HTTPS without inline credentials, query or fragment. `credentialsFromEnv` is exactly **two different environment-variable names** (username first, password/token second), not actual secrets. A regular `fabric/mod` may use this new `publish.maven` when `mod.group` is present; it does **not** automatically enable the API product's library publishing defaults.

The **legacy API/library-only** equivalent is also supported (do **not** combine a configured legacy remote repository with `publish.maven`):

```yaml
publishing:
  repository:
    url: https://maven.example.org/releases
    usernameEnv: FABRIC_AMPER_MAVEN_USERNAME
    passwordEnv: FABRIC_AMPER_MAVEN_PASSWORD
```

**Publication safety:** `gradle build` and `fabric-amper.bat plan` do not upload. Remote publishing requires an **explicit** operation (`publishFabricAmperMaven`, `publishFabricAmperModrinth`, `publishFabricAmperCurseForge` or `publishFabricAmper`); `publish all` is **not** the same as Gradle's broader standard `publish` task. Multi-platform uploads are **not atomic**: one platform could succeed while another fails. Local validation cannot guarantee credentials, external project ownership or idempotency. Native remote Maven publishing with credentials has additional Gradle configuration-cache limits; see [publishing details and limitations](HUMAN.md#explicit-maven-modrinth-and-curseforge-publishing).

## Windows command cheat sheet

Run `fabric-amper.bat init` from the checkout or another working directory. For consumer operations, run the script from the **consumer** directory or pass `-C "C:\path\to\consumer"` **before** the command. **Exceptions:** `doctor -C ".\httpmod"` and `migrate -C ".\httpmod"` (or `--project`) also accept the folder after the subcommand. The redundant `doctor -C ".\httpmod" doctor` spelling remains supported. Relative paths use the current terminal directory. Use a full path to `fabric-amper.bat` if it is not on `PATH`.

| Wrapper command | Actual action / impact |
| --- | --- |
| *(no arguments)* / `-V` / `--version` / `version` | Current release-target BAT prints `v0.3.0` and exits without Gradle; this does not confirm public artifact availability |
| `help` / `--help` / `docs` | Show full usage or documentation locations (empty invocation now prints only the version) |
| `doctor` | Check available Java/Gradle and report the JitPack coordinate, without contacting the network or clearing caches |
| `doctor --paths` / `--full` | Estimate Windows path risks; `--full` also prints JDK/toolchain hints |
| `doctor --fix --dry-run` | **Read-only, no network**. If PS1 is missing, show only its GitHub URL; if verified PS1 exists, preview JDK25/Gradle and missing consumer Settings/VS Code files |
| `doctor --fix` | **Opt-in changes:** request the matching v0.3.0 PS1 from this BAT's Release URL if available and hash-verified, then inspect/repair missing JDK25/Gradle or consumer Settings/editor files |
| `init <mod_id> [folder] [mod\|api] [--mod-fabric] [--mod-neoforge] [--architectury] [--kotlin] [--splitEnvironmentSourceSets]` | Java default; Fabric, NeoForge or both; dual Architectury creates `common/`; independent dual does not; split Fabric-only |
| `init <mod_id> --here [mod\|api] [loader flags] [--architectury] [--kotlin] [--splitEnvironmentSourceSets]` | Same choices for an existing tools-only folder; rejects conflicting project sources/YAML/scripts |
| `migrate --dry-run` | Read-only preview: with verified `scripts/` helper/schema, show proposed YAML; if absent, only preview the required download |
| `migrate` / `migrate -C "folder"` | Auto-download/hash-check missing helper/schema, create `mod.yaml` and `settings.gradle.kts`, save original build script, old Settings file and Loader JSON in `gradle_backup/` |
| `migrate -D` / `migrate --delete` | Explicitly remove the original scripts/Loader JSON after successful conversion; combine with `--dry-run` to preview first |
| `tasks` | `gradle tasks --all` in the consumer |
| `validate` | `gradle validateFabricAmper` |
| `build` | Validate/build/verify the selected consumer; dual root delegates to both loader JAR builds (check concrete tasks with `tasks`) |
| `verify` / `test` / `clean` | Verify applicable loader JAR(s), run tests or clean; for direct Gradle use task names from `tasks --all` |
| `diagnose` | `fabricAmperDoctor`: local toolchain/project report, no upload |
| `explain` | `fabricAmperExplain`: Gradle artifacts vs Loader requirements, no remote resolution |
| `plan` | `fabricAmperReleasePlan`: builds/verifies local JAR and previews release metadata, **no upload** |
| `port 1.21.11` / `port 26.3` | `fabricAmperPortingReport`: manual migration checklist, does **not** port code |
| `run` / `server` | Single target: `runClient` / `runServer`; dual: require `--mod-fabric` or `--mod-neoforge`, mapping to `runFabricClient/Server` or `runNeoForgeClient/Server`; server EULA remains manual |
| `publish local` | Explicit API/library Maven publication inside consumer `build/`; **no remote upload** |
| `publish modrinth` / `curseforge` / `maven` | **Real remote upload** to the configured destination after typed confirmation and local verification |
| `publish all` | **Real remote upload** to all explicitly configured **new `publish` targets**; may partially succeed |

The wrapper performs its own preflight checks for a missing or duplicated YAML, forbidden consumer build scripts and authored Loader JSON. **`migrate` is a special pre-conversion command**: it must accept and inspect those original files before replacing them, and its PowerShell helper performs the migration preflight. For direct Gradle commands use the concrete tasks named above. `init --here` permits files like `fabric-amper.bat`, skill documentation and `.gitignore` but rejects any existing project configuration/source rather than overwriting it. A new starter also creates `.vscode/settings.json` unless one already exists, in which case editor settings remain untouched. For **unsupported** wrapper commands, use the original Gradle/Java tooling rather than inventing another batch subcommand or assuming `./gradlew` exists. Do **not** use `publish` as a way to check syntax; use `validate` or `build` instead. Server EULA acceptance, authentication, world files and personal Minecraft accounts are **never** handled automatically.

## Troubleshooting and next steps

| Symptom | Fast check |
| --- | --- |
| Settings plugin not found | Check the JitPack tag and coordinate in `pluginManagement.resolutionStrategy`, Internet access, JitPack build status, Gradle/JDK compatibility and repository availability |
| Missing/duplicate YAML (`FA001` / `FA002`) | Use exactly one `mod.yaml` or `mod.yml`; check UTF-8, aliases and duplicate keys |
| Invalid field, fixed version or publish URL (`FA003`) | Check [Draft-07 schema](fabric-amper.schema.json) and the project's strict Kotlin validator |
| Minecraft mapping / Java / Kotlin (`FA004`–`FA006`) | For 26.3 use Mojang mappings, Java **25**, pinned optional Kotlin versions |
| Source `fabric.mod.json` / duplicate JAR metadata (`FA008` / `FA013`) | Remove authored Loader JSON and run `verifyFabricAmperJar` |
| Mixin/icon/Class Tweaker / client side (`FA009` / `FA014`) | Verify relative paths, file headers, actual resources and split source sets |
| Artifact classification or conflicting declarations (`FA011` / `FA012`) | Check exact Maven repository/coordinate and repeated dependencies |
| Metadata mirror issue (`FA015`) | Inspect project-local mirror health and cache diagnostics; avoid global cache deletion |
| Very long Windows paths | `fabric-amper.bat doctor --paths` or `doctor --full`; consider a shorter **project** directory |
| Build passes but game/IDE fails | Test `runClient`, `runServer` (with personally reviewed EULA) or IntelliJ GUI separately |

**Where to go next:** [HUMAN.md](HUMAN.md) for the full human manual, [FabricAmperSKILL_SYN.md](FabricAmperSKILL_SYN.md) for concise AI agent instructions, [FabricAmperSKILL.md](FabricAmperSKILL.md) for detailed agent safeguards, [fabric-amper.schema.json](fabric-amper.schema.json) for allowed YAML keys, [specs.md](specs.md) for G1–G30 acceptance, and [QA_REPORT.md](QA_REPORT.md) for actual evidence and remaining gates.

## Historical schema 1 reference

The Windows `init` command with **Fabric alone** (with no loader flag or with `--mod-fabric`) still writes an explicit **`schema: 1`** starter for compatibility. The `migrate` command also generates schema 1 for an existing Fabric Gradle project. Keep the explicit pin when editing those projects. A new hand-written config should use the [schema 1.1 sample above](#minimal-project-copy-and-run); **omitting `schema` now selects 1.1**.

Here is the old **Fabric-only schema 1** layout, consolidated in one place:

```yaml
schema: 1
product: fabric/mod
mod:
  id: quick_mod
  version: "1.0.0+26.3"
  name: Quick Mod
  entrypoints:
    main: [org.example.ExampleMod]
  depends:
    fabricloader: ">=0.20.1"
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
  loom:
    splitEnvironmentSourceSets: false
repositories: [fabric, mavenCentral]
dependencies: []
test-dependencies: []
```

For schema 1.1, move `minecraft.loader`, `minecraft.fabricApi` and `minecraft.mappings` to `fabric.loader`, `fabric.api` and `fabric.mappings`. Move `mod.entrypoints`, `mod.depends`, `mod.mixins`, `mod.icon`, `mod.accessWidener` and other Fabric-only metadata to `fabric.*`; move `settings.loom` to `fabric.loom`, and `settings.kotlin.fabricLanguageKotlin` to `fabric.kotlin.fabricLanguageKotlin`. Shared identity (`mod.id`, `mod.version`, `mod.name`), `minecraft.version`, and shared Java/Kotlin settings stay where they are.

**`repositories`, `dependencies` and `test-dependencies` were already arrays in schema 1.** Schema 1.1 did not change these collections from maps to lists. `fabric.depends` remains a map of Fabric mod IDs to Loader version predicates; `neoforge.dependencies` is a separate **array** of NeoForge TOML runtime relationships. Per-loader Maven classpaths can additionally use `fabric.gradleDependencies[]` and `neoforge.gradleDependencies[]`. For all field mappings and upgrade considerations, see [the historical reference in the Human Guide](HUMAN.md#historical-schema-1-reference).
