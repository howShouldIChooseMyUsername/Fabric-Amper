---
name: fabric-amper-syn
description: Compact decision guide for Fabric Amper schema 1.1 Fabric/NeoForge/dual-loader development, explicit legacy v1, safe validation, and FA diagnostics.
---

# Fabric Amper — Compact AI Skill (SYN)

## READ THIS FIRST — stop-or-escalate rule

**Use only this document and STOP reading** when the user's intent, the exact YAML keys and value types, target file paths, prerequisites, and safe verification steps are **unambiguous and fully covered below**. Apply the minimal edit; run the applicable safe checks; report results. Do **not** read the longer `FabricAmperSKILL.md` merely for reassurance, and do not needlessly expand the task.

**Command precedence (mandatory):** On Windows, **use an existing complete Fabric Amper checkout's `fabric-amper.bat` first for everything it actually supports**, including `doctor`, `init`, `migrate`, `tasks`, `validate`, `build`, `verify`, `test`, `clean`, `diagnose`, `explain`, `plan`, `port`, `run`, `server` and approved `publish` commands. Pass `-C "consumer"` before most subcommands; `migrate` allows `-C` / `--project` before or after its name, and `doctor` also accepts `doctor -C ".\httpmod"`, `doctor --project ".\httpmod"` and the redundant `doctor -C ".\httpmod" doctor`. Relative paths use the current terminal directory. For **unsupported** operations, retain the original Gradle/Java/Kotlin/native workflow; do not fabricate a BAT command. **Never assume `./gradlew` exists or is appropriate**: check the selected project's actual wrapper first. When read-only `doctor` detects missing required JDK/Gradle, the agent **automatically runs `doctor --fix` after preview without requesting separate approval**. It can install required software/add missing bootstrap files; it downloads the SHA-256 pinned `fabric-amper-doctor-fix.ps1` from the BAT version's GitHub Release into **`scripts/` below BAT** when missing. Normal `migrate` automatically downloads its missing version-matched `scripts/fabric-amper-migrate.ps1` and root schema, verifying both pinned SHA-256 values before conversion; no manual PS1 installation. Neither `doctor --fix --dry-run` nor `migrate --dry-run` downloads; without a local helper they only preview the missing fetch. `init --here` must not overwrite an existing project. This policy does not authorize unrelated installs, remote publishing, EULA acceptance, or overwriting existing files.

**BAT version check:** the published v0.3.1 BAT with no arguments, `-V`, `--version`, or `version` prints exactly `v0.3.1` (one line, exit 0) without Gradle or file changes. Use `help` / `--help` for usage: no arguments **does not** mean help. **v0.3.1 GitHub public prerelease published 2026-11-18:** exactly nine assets redownloaded and SHA-256 verified; JitPack `ok` for annotated tag commit `3f8d0f12427e05a089f8ee11d53ddf3e951b0bfd` (JDK 25, three modules, POM HTTP 200); fresh public **Fabric Java settings-only** consumer `help validateFabricAmper build verifyFabricAmperJar` PASS in **33 seconds** (actual compile, one `fabric.mod.json`). Producer **383 tests = 324 PASS + 59 SKIP + 0 FAIL**, `validatePlugins` PASS and dual local TestKit **2/2 PASS**. **v0.3.0 historical 2026-11-17:** nine hash-verified assets and a NeoForge+Architectury+Kotlin public JitPack consumer PASS. Neither record proves NeoForge public v0.3.1 consumer, actual Minecraft runtime, IDE sync or complete shipping gates.

**Mandatory Windows preflight sequence for JDK/Gradle-dependent tasks:** Run `fabric-amper.bat doctor` first. If required tools are present, continue. If JDK/Gradle is missing or unusable, run `doctor --fix --dry-run` (no downloads or modifications; without the PS1 it only previews the helper fetch), then **run `doctor --fix` on your own, without asking for consent**, as part of the existing development task. After repair, run `doctor` **again** (use a new terminal if PATH/JAVA_HOME changes have not propagated). Resume the original dependent work **only when the second check confirms the necessary tools**. On failure or hash mismatch, report the blocker and stop the dependent operation; no unrelated installation, silent workaround, automatic retries, or claim that `doctor` verifies JitPack/runtime/publishing. See [full workflow](FabricAmperSKILL.md#required-windows-doctor--repair--recheck-workflow).

**Read [FabricAmperSKILL.md](FabricAmperSKILL.md) before editing or answering** when **any** required detail is missing, ambiguous, contradictory, out of scope, or insufficiently documented here. Examples: a new/unknown YAML key, complex publish platform relationships, disputed Loader predicate grammar, non-obvious split-side safety, unusual mapping/toolchain, Gradle/classpath collisions, HTTP failure, ZIP/resource corruption, unresolved diagnostics, or different versions. For exact implementation truth, inspect `src/main/kotlin/dev/fabricamper/config/` and [fabric-amper.schema.json](fabric-amper.schema.json). Never guess just to avoid an escalation.

This file is a **custom root AI guide**; its filename is not the standardized auto-discovery `<skills-dir>/SKILL.md`. Frontmatter does not change that. The [full skill](FabricAmperSKILL.md) explains standard packaging.

## Strict invariants (always)

- Consumer authored build configuration = **`settings.gradle.kts` + exactly one `mod.yaml`/`mod.yml`**; developer still writes real source/resources/tests. Never create consumer `build.gradle(.kts)`, manually authored `fabric.mod.json`, or apply internal `dev.fabric-amper.project`.
- Required root `product`, `mod`, `minecraft`; shared nested `mod.id`, `mod.version`, `minecraft.version`. **Published v0.3.1 decoder: omitted `schema` defaults to 1.1** (first introduced in v0.3.0), and `schema: "1.1"` explicitly selects it. **Every BAT `init` writes quoted schema 1.1** for Fabric-only, NeoForge-only and dual projects; schema 1 initialization is unavailable. The decoder preserves the historical integer schema 1 format shown in [Legacy schema 1](#legacy-schema-1-fabric-only-reference) for existing Fabric YAML; `migrate` also remains schema 1. Unquoted `schema: 1.1` is invalid YAML float; unknown version/type → FA003.
- Schema 1.1 `product`: scalar `fabric/mod`, `fabric/api`, `neoforge/mod`, `neoforge/api` or array `[fabric/mod, neoforge/mod]`, `[fabric/api, neoforge/api]` and hybrid mod/API types. `fabric/library` aliases Fabric API. Exactly **one target per loader**; `fabric:` is **mandatory** when Fabric selected, `neoforge:` when NeoForge selected. Shared root `mod`, `minecraft.version`, `settings.java`, `settings.kotlin`, plus common `dependencies[]` entries where `loader` is omitted. An optional `loader: fabric` / `neoforge` **inside a root dependency object** directs that artifact to one declared target (single or dual). Loader-specific `fabric.loader/api/mappings/entrypoints/loom/gradleDependencies` and `neoforge.version/modLoader/dependencies/gradleDependencies` do not require another loader selector. The older root key placements are collected in [Legacy schema 1](#legacy-schema-1-fabric-only-reference).
- Strict UTF-8 single YAML mapping; no duplicate keys, anchors (`&`), aliases (`*`), merges (`<<`), unrecognized tags or fields. When editing, **merge** partial recipes into existing mappings — no repeated root `mod:` or `settings:`.
- `fabric.environment` is the Fabric Loader whole-mod side; `dependencies[].target` is a Gradle dependency source set. Gradle artifact dependencies, Fabric `fabric.depends` Loader relationships, NeoForge `neoforge.dependencies[]`, and `publish.modrinth.dependencies[]` are separate concepts.
- Only real fixed published dependency versions, verified Loader IDs, known Maven repositories and real Modrinth/CurseForge project IDs. No secrets or source-path escapes. Do not change an unrelated user's dependency or generated file.
- **Do not publish without explicit authorization.** `help`/`build`/`test` are not remote uploads; publish tasks are. Do not auto-accept EULA or use personal Minecraft worlds. Never claim a successful build proves GUI/runtime/remote publishing.
- **v0.3.0 Developer Preview — published prerelease:** the source candidate passed Gradle 9.7.1 `clean build validatePlugins --offline` with **371 total: 314 PASS, 57 SKIP, no failures**. Nine GitHub release assets were downloaded and hash-verified, JitPack `v0.3.0` resolved, and a fresh remote-plugin NeoForge+Architectury+Kotlin consumer passed build and JAR verification. Public v0.2.0 does **not** include NeoForge schema 1.1. TestKit and public JitPack consumer PASS do **not** imply actual Minecraft runtime, IntelliJ GUI sync or full shipping gates; see [QA_REPORT.md](QA_REPORT.md). A cold `:common` JAR materialization once caused second configuration-cache run MISS; identical third run HIT, so strict immediate second HIT **NOT MET**.

## Fast intent → YAML / file / check

Unless stated otherwise, edit the *existing* consumer `mod.yaml` (or `mod.yml`), not a Gradle script. The `Gradle` column refers to commands in that consumer folder.

| User intent | Exact YAML change | Required file / Gradle check |
| --- | --- | --- |
| New Fabric mod (schema 1.1 default) | `product: fabric/mod`; `mod.id`, `mod.version`, `minecraft.version`; `fabric.loader`, `fabric.entrypoints`; optional `schema: "1.1"` | `settings.gradle.kts` Settings plugin only; real `ModInitializer`, generated Fabric JSON |
| New NeoForge mod | `product: neoforge/mod`, root shared `mod/minecraft.version`, `neoforge.version`, optional `neoforge.modLoader` | Actual `@Mod` class, generated NeoForge TOML, no authored Fabric JSON |
| Dual independent loaders | `product: [fabric/mod, neoforge/mod]`, both blocks, `architectury.enable: false` | `:fabric` + `:neoforge`, **no `:common`**; separate JAR/metadata |
| Dual shared Architectury | Same dual array + `architectury.enable: true` | `:common`, `:fabric`, `:neoforge`; dual split argument **ignored with notice** |
| Explicit old Fabric v1 | Follow the single [legacy schema 1 reference](#legacy-schema-1-fabric-only-reference) | Preserve old keys/behavior, not auto-migrate |
| Public API/library | `product: fabric/api` (alias `fabric/library`); required `mod.group`; optional `mod.archivesName` | Verify JAR/POM and downstream consumers; explicit local Maven publish |
| Change display/version/JAR basename | `mod.name` / `mod.version` / `mod.archivesName` | Check generated Loader JSON and `build/libs/` |
| MC26.3 Java-only | `minecraft.version: "26.3"`; 1.1 `fabric.mappings: mojang` or `neoforge.version`; `settings.java.release: 25`; `settings.kotlin.enabled: false` | JDK25; Fabric non-remap / NeoForge MDG |
| MC1.21.11 legacy | `minecraft.version: "1.21.11"`; `settings.java.release: 21`; Mojang or real published Yarn mapping | Remap `remapJar`; legacy compatibility needs separate tests |
| Kotlin | `settings.kotlin.enabled: true`; pinned KGP `2.4.21` / language `2.4` / FLK `1.14.2+kotlin.2.4.21` | Actual `src/main/kotlin/`; `adapter: kotlin` only when needed |
| Client-only Fabric mod | `fabric.environment: client` and `fabric.entrypoints.client` | Real client initializer and side-safe resources |
| Common mod + client code/dependency | `fabric.loom.splitEnvironmentSourceSets: true` and Fabric `target: client` | Fabric split only; independent dual affects `:fabric`; dual+Arch ignores split |
| Loader installed-mod requirement | `fabric.depends.<real-mod-id>: ">=version"` or `neoforge.dependencies[]` | Inspect Loader metadata, not Maven resolution |
| Maven dependency | `repositories: [mavenCentral]` or verified `{maven: https-url}`; `dependencies: [{maven: "g:a:fixed", scope: all}]` | Confirm real GAV; `gradle build` |
| One loader-specific root dependency | `dependencies: [{maven: "g:a:fixed", loader: fabric}]` (or `neoforge`) in schema 1.1 | Only an object entry; loader must be in `product`. Omit selector for common sharing |
| Modrinth **Maven** dependency | `repositories: [modrinth]`; `dependencies[].modrinth: {project: REAL, version: REAL_FIXED}`; `kind: mod` | This is NOT `publish.modrinth.projectId` |
| Public API dependency | `dependencies[].scope: all` + `exported: true` | Check exported downstream compilation |
| Nested Fabric JAR | In dual 1.1 use `fabric.gradleDependencies[].scope: all` + `include: true` | Verify Fabric JIJ nested JAR and no NeoForge pollution; shared include must not contaminate other target |
| Compile/runtime only | `dependencies[].scope: compile-only` or `runtime-only` | No `exported/include` with either |
| Test dependency | `test-dependencies: ["org.junit.jupiter:junit-jupiter:6.0.0"]` | Real tests in `src/test/`; `gradle test` |
| Fabric Mixin | `fabric.mixins: [example.mixins.json]` or `{config, environment}` | Real JSON file in Fabric resources; verify side |
| Modern Fabric Class Tweaker | `fabric.accessWidener: example.classtweaker` | `src/main/resources/example.classtweaker`; first line `classTweaker v1 official` |
| Older Minecraft Access Widener | `fabric.accessWidener: example.accesswidener` on supported remap profiles | Valid `accessWidener v1/v2 named/intermediary` header |
| Fabric icon | `fabric.icon` | Actual PNG in the Fabric resource root |
| Custom Fabric metadata | `fabric.custom` | Inspect Fabric JAR's generated `fabric.mod.json` |
| Remote Maven | `mod.group` + `publish.maven.url: https://...`; optional `credentialsFromEnv: [USER_ENV, PASS_ENV]` | Validate, then only user-approved `publishFabricAmperMaven` |
| Modrinth release | `publish.modrinth.projectId` (string), `tokenEnv`, optional `releaseType`, `gameVersions` (string list), `loaders` (string list), `environment`, `dependencies[]` | Verified real IDs, explicit `publishFabricAmperModrinth` |
| CurseForge release | `publish.curseforge.projectId` (positive integer), `tokenEnv`, `gameVersionNames` (strings), optional `gameVersions` (integer IDs), `relations[]` | Verified real IDs, explicit `publishFabricAmperCurseForge` |
| Shared Modrinth + CurseForge release | `publish.common.releaseType` / `name` / `gameVersions` / `changelog( File)` / `dependencies[]`; keep `publish.modrinth` and `publish.curseforge` with platform-specific IDs/tokens | Merge defaults, explicit platform override, actual IDs; no automatic upload |
| Legacy API Maven | See [legacy schema 1](#legacy-schema-1-fabric-only-reference) | Only `fabric/api`, never alongside `publish.maven` |

**Dependency scopes:** `all` = compile+runtime; `compile-only` = compile; `runtime-only` = runtime. `exported: true` exposes public API; `include: true` nests a JAR. Both require `scope: all`. `target: client` additionally requires split source sets and forbids export/include.

## Schema snapshot — 1.1 defaults; explicit v1 remains compatible

| Parent | Accepted child keys / types |
| --- | --- |
| **root 1.1** | **optional** `schema: "1.1"` (omitted = 1.1 in the v0.3.1 decoder); `product` string/array; required `mod`, `minecraft`; required matching `fabric`/`neoforge` selected loader maps; optional `settings`, `architectury`, `repositories`, `dependencies`, `test-dependencies`, `publishing`, `publish` |
| `mod` in 1.1 | **shared only** `id`, `version`, `group`, `archivesName`, `name`, `description`, `license`, `authors`, `contributors`, `contact` |
| `minecraft` in 1.1 | `version` **only**; all mappings/Loader/API keys move to `fabric` or `neoforge` |
| `fabric` in 1.1 | `loader`, `api`, `mappings`, `environment`, `entrypoints`, `languageAdapters`, `mixins`, `accessWidener`, `provides`, `depends`, `recommends`, `suggests`, `conflicts`, `breaks`, `custom`, `icon`, `loom`, `kotlin`, `resources`, `gradleDependencies`, `repositories` |
| `neoforge` in 1.1 | `version` required; optional `modLoader`, `loaderVersion`, `dependencies`, `mixins`, `accessTransformers`, `logoFile`, `displayURL`, `issueTrackerURL`, `credits`, `updateJSONURL`, `modProperties`, `features`, `gradleDependencies`, `repositories` |
| Version 1 | Complete historical syntax, field locations and an example are in [Legacy schema 1](#legacy-schema-1-fabric-only-reference) |
| `settings.java` | `release` integer (25 for MC26.3), `sourcesJar` boolean |
| `settings.kotlin` | `enabled`, `pluginVersion`, `languageVersion`; Fabric Language Kotlin version at `fabric.kotlin.fabricLanguageKotlin` |
| `fabric.loom` | `splitEnvironmentSourceSets` boolean |
| `fabric.resources` | `failOnExistingFabricModJson: true` |
| `repositories[]` | `fabric`, `mavenCentral`, `modrinth`, `{maven: https-url}` |
| `dependencies[]` | GAV coordinate/scope shorthand or typed `maven`/`modrinth`; shared when selector omitted. Schema 1.1 **object** entry may use `loader: fabric` or `neoforge` for one selected backend. Loader-specific entries can also live in `fabric.gradleDependencies` / `neoforge.gradleDependencies`, where `loader` is forbidden. Fabric-only JIJ `include` must **not** leak into NeoForge |
| `dependencies[].loader` | Optional schema 1.1 root **object entry** only: `fabric` or `neoforge`; target must be selected in `product`. Invalid in explicit `schema: 1`, `test-dependencies[]` and nested platform `gradleDependencies[]` |
| `test-dependencies[]` | coordinate/scope without `target: client`, `exported`, `include` |
| `publish.maven` | `url` HTTPS; `credentialsFromEnv` = two distinct environment-variable **names** |
| `publish.common` | `releaseType`, `name`, string `gameVersions[]`, `changelog` OR `changelogFile`, `dependencies[{slug,type,modrinthId?,curseforgeId?}]` |
| `publish.modrinth` | `projectId`, `tokenEnv`, `releaseType`, `versionName`, `versionNumber`, `gameVersions`, `loaders`, `environment`, `changelog` **or** `changelogFile`, `dependencies[{projectId,type}]` |
| `publish.curseforge` | `projectId`, `tokenEnv`, `releaseType`, `displayName`, `gameVersionNames`, `gameVersions`, `changelog` **or** `changelogFile`, `changelogType`, `manualRelease`, `relations[{slug,type,projectId?}]` |
| Legacy API publication | See the dedicated [legacy schema 1 reference](#legacy-schema-1-fabric-only-reference) |

These are **schema 1.1 namespaced keys** unless explicitly marked legacy v1. Do not copy older field layouts into a missing-schema document; omission now selects 1.1 in the published version. If a required value/nuanced validation is not covered, **read the full skill and current decoder/schema** instead of inventing a key.

## Legacy schema 1 (Fabric-only reference)

**All historical schema 1 syntax is collected here.** Explicit integer `schema: 1` retains the previous Fabric-only decoder format for existing consumers. In v0.3.1, **every BAT `init`, including Fabric-only without flags or with `--mod-fabric`, generates explicit `schema: "1.1"`**; no legacy initializer exists. `migrate` still pins 1 for converted projects. Omitted `schema` selects **1.1** in hand-written YAML. Use the 1.1 Fabric starter below or BAT `init` for new work; use the legacy reference only to maintain an older consumer or inspect migration output.

**Complete old Fabric-only mod document (requires actual Java initializer/resource files):**

```yaml
schema: 1
product: fabric/mod
mod:
  id: example_mod
  version: "1.0.0+26.3"
  name: Example Mod
  environment: "*"
  entrypoints:
    main: [org.example.ExampleMod]
  depends:
    minecraft: ">=26.3"
    fabricloader: ">=0.20.1"
minecraft:
  version: "26.3"
  loader: "0.20.1"
  fabricApi: "0.165.2+26.3"
  mappings: mojang
settings:
  java:
    release: 25
  kotlin:
    enabled: false
  loom:
    splitEnvironmentSourceSets: false
  resources:
    failOnExistingFabricModJson: true
repositories:
  - fabric
  - mavenCentral
dependencies:
  - maven: "org.example:existing-library:1.0.0"
    kind: library
    scope: all
test-dependencies:
  - "org.junit.jupiter:junit-jupiter:6.0.0"
```

The `org.example` dependency and Java initializer above are illustrative and need real replacements. Version 1 stores Fabric metadata under `mod.environment/entrypoints/mixins/accessWidener/icon/custom/provides/depends/recommends/suggests/conflicts/breaks`, Loader versions and mappings under `minecraft.loader/fabricApi/mappings`, client split and resource settings under `settings.loom/resources`, and Fabric Language Kotlin adapter version under `settings.kotlin.fabricLanguageKotlin`. Its client dependency still uses `dependencies[].target: client` and requires the old split setting. Remap-era Minecraft uses a valid `.accesswidener` resource; Minecraft 26.1+ uses a `.classtweaker` resource with the correct header.

Optional schema 1 `mod:` resource fields (fragment; actual referenced resources are required before validation):

```yaml
mixins: [example.mixins.json]
accessWidener: example.classtweaker
icon: assets/example_mod/icon.png
```

**Version 1 repositories and dependencies are also arrays.** Root `repositories`, `dependencies`, and `test-dependencies` accept lists, including fixed GAV strings, scoped shorthand and supported object forms; version 1 does not accept the loader-namespaced 1.1 `fabric.gradleDependencies`, `neoforge.gradleDependencies` or `neoforge.repositories` keys. Version 1 repositories support `fabric`, `mavenCentral`, `modrinth`, and an HTTPS `{maven: URL}` object. Fabric Loader requirements are **not** Gradle dependency downloads.

For a version 1 **Fabric API** product, use `product: fabric/api` (or `fabric/library`), set a real `mod.group` and optionally `mod.archivesName`; older API-only remote Maven configuration uses this alternative fragment:

```yaml
publishing:
  repository:
    url: https://maven.example.org/releases
    usernameEnv: FABRIC_MAVEN_USER
    passwordEnv: FABRIC_MAVEN_PASSWORD
```

It must not be combined with the newer `publish.maven`. Both environment variable names are present or both absent, and publication remains an explicitly authorized action. For full legacy validation detail, see [Legacy schema 1 in the long guide](FabricAmperSKILL.md#legacy-schema-1-fabric-only-reference).

**Initialization is not a schema 1 migration path.** All new Fabric project creation commands write schema 1.1 under `fabric:`; use `migrate` only for the separately documented conversion of existing Gradle-based Fabric projects, which preserves schema 1 for compatibility.

## Shared release configuration — short safe decision path

Use **`publish.common`** only when the same values should apply to one or both explicitly listed platforms. Below is a **FRAGMENT** that must be merged into one root `publish:` object, **not** a second `publish:` section:

```yaml
publish:
  common:
    releaseType: release
    name: "Example 1.0.0"
    gameVersions: ["26.3"]
    changelogFile: CHANGELOG.md
    dependencies:
      - slug: fabric-api
        modrinthId: fabric-api
        curseforgeId: "306612"
        type: required
  modrinth:
    projectId: "REAL_MODRINTH_ID"
    tokenEnv: MODRINTH_TOKEN
  curseforge:
    projectId: 123456
    tokenEnv: CURSEFORGE_TOKEN
    gameVersionNames: ["26.3", "Fabric", "Client", "Server"]
```

| Shared → destination | Rule |
| --- | --- |
| `releaseType` / `name` | Modrinth `releaseType`/`versionName`; CF `releaseType`/`displayName` |
| `gameVersions` **strings** | Modrinth `gameVersions`; CurseForge `gameVersionNames` **tags**. CF numeric `gameVersions` never inferred |
| `changelog` OR `changelogFile` | Both inherit; either explicit platform notes key replaces the *whole* inherited source |
| `dependencies[]` `slug` | CF relation slug, Modrinth project ID fallback; optional `modrinthId` overrides MR; `curseforgeId` is positive numeric **string** |
| `type: required/optional/incompatible/embedded` | Modrinth unchanged; CF `requiredDependency/optionalDependency/incompatible/embeddedLibrary` |
| Platform's own dependency list | Matching MR project ID / CF slug **overrides**, new item **appends**, explicit `[]` **clears** inherited list |

**Never infer platform identity or ID correspondence.** `common` alone (or with Maven only) is not a publish target and is rejected. Both destinations still require their own `projectId` and `tokenEnv`. An unverified platform ID/tag, unusual relation or conflicting override requires [the full skill](FabricAmperSKILL.md#route-common-modrinth-curseforge-release-configuration). The example IDs are placeholders, not usable project IDs; no upload without user authorization.

## Two complete starter files (copy only for a new consumer)

`settings.gradle.kts` (JitPack-tagged bootstrap, no local producer Maven repository):

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
                useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.3.1")
            }
        }
    }
}
plugins { id("dev.fabric-amper.settings") version "0.3.1" }
```

**Published v0.3.1 prerelease bootstrap:** GitHub/JitPack tag `v0.3.1`, plugin request `0.3.1`. Exactly nine release assets passed independent SHA-256 redownload checks on 2026-11-18. JitPack returned `ok` for annotated tag commit `3f8d0f12427e05a089f8ee11d53ddf3e951b0bfd`, JDK 25 three modules and POM HTTP 200; a fresh Fabric Java settings-only remote consumer compiled and verified its JAR successfully in 33 seconds. **Dated v0.3.0 evidence (2026-11-17):** nine hash-matched release assets and an independently verified NeoForge+Architectury+Kotlin public JitPack consumer. Offline develop-worktree consumer tests may instead use a locally resolved plugin. The removed `fabric-amper.bat setup` command is not needed; no public Plugin Portal resolution is assumed. **Actual v0.3.1 public NeoForge consumer, game runtime and IDE sync remain outside the demonstrated scope.**

`mod.yaml` (complete **schema 1.1 Java Fabric-only** sample; create `src/main/java/org/example/ExampleMod.java` with an actual `ModInitializer`):

```yaml
schema: "1.1"
product: fabric/mod
mod:
  id: example_mod
  version: "1.0.0+26.3"
  name: Example Mod
minecraft:
  version: "26.3"
fabric:
  mappings: mojang
  loader: "0.20.1"
  api: "0.165.2+26.3"
  entrypoints:
    main:
      - org.example.ExampleMod
settings:
  java:
    release: 25
  kotlin:
    enabled: false
repositories:
  - fabric
  - mavenCentral
```

For new hand-authored schema 1.1 files, omitted `schema` is **interpreted as 1.1 by the published v0.3.1 decoder**; the explicit quoted form improves clarity. Fabric-only BAT `init` emits schema 1.1 even with `--mod-fabric`; no schema 1 initializer is supported. For **NeoForge-only**, change to `product: neoforge/mod`, keep shared `mod`, `minecraft.version`, `settings`, replace the entire `fabric:` block with `neoforge: {version: "VERIFIED_NEOFORGE_VERSION", modLoader: javafml}`, and provide a real Java/Kotlin `@Mod` source. **Never invent a NeoForge version**; the minimal block shown is a **fragment**, not a complete document.

**Dual 1.1 minimal complete YAML** (real platform-specific sources required; version strings must be verified):

```yaml
schema: "1.1"
product: [fabric/mod, neoforge/mod]
mod:
  id: example_mod
  version: "1.0.0+26.3"
  license: MIT
minecraft:
  version: "26.3"
settings:
  java:
    release: 25
architectury:
  enable: false
fabric:
  loader: "0.20.1"
  api: "0.165.2+26.3"
  mappings: mojang
  entrypoints:
    main: [org.example.ExampleFabricMod]
neoforge:
  version: "26.3.0.101" # example candidate; confirm available version
repositories: [mavenCentral]
```

This dual example creates **`fabric/` and `neoforge/` with no `common/`**. Setting `architectury.enable: true` instead creates `common/`, `fabric/`, `neoforge/`. In dual+Architectury only, Fabric split is **ignored with a notice**; in dual without Architectury split applies to Fabric only. **Public v0.3.0 JitPack resolution and a fresh NeoForge+Architectury+Kotlin consumer build passed; actual game launch and IntelliJ GUI have not been verified.** For automatic schema 1.1 dual initialization use `fabric-amper.bat init dual_mod "Dual Mod" mod --mod-fabric --mod-neoforge`. For a single Fabric 1.1 project, run `fabric-amper.bat init example_mod` or copy/edit the complete starter files above.

## Windows BAT routing — use the wrapper only when appropriate

The producer root contains [`fabric-amper.bat`](fabric-amper.bat). This is a **Windows CMD Gradle wrapper**, not a consumer YAML key or public installer. The next table is enough for common, unambiguous commands: **stop after following it**. If the workspace path, requested target, side effects, or task behavior is ambiguous, **read [FabricAmperSKILL.md](FabricAmperSKILL.md#windows-command-wrapper-fabric-amperbat)** before proceeding.

| Intent | Command / action | Boundary |
| --- | --- | --- |
| Show usage / installed tools | `fabric-amper.bat help` / `doctor` / `docs` / `version` | `doctor` checks Java/Gradle; it **does not** connect to JitPack |
| Additional local environment diagnosis | `doctor --full` / `doctor --paths` | Windows path/JDK hints; `--paths` does not execute Gradle |
| Preview fixes safely | `doctor --fix --dry-run` | No network, installation, user PATH change or file writes; **missing PS1 means preview the fetch only**, not toolchain inspection |
| Automatic repair after a missing-tool diagnosis | `doctor --fix` | After read-only preview, run without a separate approval question; fetch only the versioned GitHub PS1 into `scripts/` below BAT when missing, pin SHA-256 and reject mismatches/unwritable directory, then winget JDK25/Gradle or verified Gradle ZIP and missing Settings/editor files; recheck with `doctor` |
| Start new Java schema 1.1 Fabric mod | `init example_mod` or copy/edit the complete 1.1 starter above | BAT emits `schema: "1.1"`, `fabric:` Loader fields and a real Java initializer by default |
| Start new Kotlin schema 1.1 Fabric mod | Use 1.1 starter, add `settings.kotlin.enabled: true`, `fabric.kotlin.fabricLanguageKotlin`, and real Kotlin source | Update `fabric.entrypoints` for Kotlin adapter if needed; correct pinned versions required |
| Start schema 1.1 Fabric client split | `init strict_mod "Strict Mod" mod --splitEnvironmentSourceSets` or edit 1.1 `fabric.loom.splitEnvironmentSourceSets: true` and `fabric.entrypoints.client` | BAT generates the client initializer, source roots and schema 1.1 fields |
| Start NeoForge-only Java/Kotlin | `init neo_mod "Neo Mod" mod --mod-neoforge [--kotlin]` | Scalar `product: neoforge/mod`; `neoforge:` and `@Mod`; **Neo-only split option rejected**; actual develop BAT TestKit Java/Kotlin + Architectury cases passed |
| Start independent dual loaders | `init dual_mod "Dual Mod" mod --mod-fabric --mod-neoforge [--kotlin] [--splitEnvironmentSourceSets]` | `product` array, isolated `:fabric/:neoforge`; **no `:common`**; optional split applies Fabric only |
| Start dual Architectury shared project | `init dual_arch "Dual Arch" mod --mod-fabric --mod-neoforge --architectury [--kotlin]` | Real `:common`, `:fabric`, `:neoforge`; accepts `--splitEnvironmentSourceSets` but **ignores it with notice** |
| Start NeoForge-only Architectury | `init arch_mod "Architectury Mod" mod --mod-neoforge --architectury` | Generates schema 1.1 and real `@Mod` Java/Kotlin source; real develop TestKit and public NeoForge+Architectury+Kotlin consumer build PASS |
| Start in existing tools-only folder | `fabric-amper.bat init night_vision --here --mod-neoforge` | NeoForge-only schema 1.1; tools and notes preserved; any existing YAML/Settings/build script/src causes a hard error |
| Start new schema 1.1 Fabric API project | `init example_api "Example API" api` or copy the 1.1 starter with `product: fabric/api` | BAT generates schema 1.1 and placeholder `mod.group`; replace with real coordinates and API types before local Maven publishing |
| Preview a legacy Fabric Gradle conversion | `fabric-amper.bat migrate --dry-run` or `migrate -C "C:\work\legacy_mod" --dry-run` | With verified local helper/schema, parse Gradle/JSON and preview YAML; if either is missing, only preview download. Never writes/downloads |
| Convert a legacy Fabric Gradle project | `fabric-amper.bat migrate` or `migrate -C "C:\work\legacy_mod"` | Auto-download/hash-check missing `scripts/` converter and schema; then create YAML/Settings and back up original build/settings/metadata in `gradle_backup/` |
| Convert and delete originals, only when explicitly requested | `fabric-amper.bat migrate -D` or `migrate --delete` | Same conversion; remove original build/settings/`fabric.mod.json` only after staging; `--delete --dry-run` previews without writes |
| Select an existing consumer | `"C:\path\to\Fabric Amper\fabric-amper.bat" -C "C:\work\example_mod" build` | `-C` / `--project` **before** ordinary subcommands; `doctor` additionally supports them after its name; default consumer = current directory |
| Validate and build | `validate` / `build` / `verify` | `build` = `validateFabricAmper build verifyFabricAmperJar` |
| Diagnose consumer toolchain / deps | `diagnose` / `explain` | Offline Gradle `fabricAmperDoctor` / `fabricAmperExplain`; no remote artifact lookup |
| Preview future releases | `plan` | Gradle `fabricAmperReleasePlan`; checks final JAR and effective platform metadata, **never uploads** |
| Request game migration checklist | `port 26.3` | `fabricAmperPortingReport -PfabricAmperPortTarget=26.3`; no source/YAML mutation |
| Inspect and maintain | `tasks` / `test` / `clean` | Gradle tasks; `clean` removes local build outputs |
| Select game client/server | `run` / `server`; in dual **require** `run --mod-fabric`, `run --mod-neoforge`, `server --mod-fabric`, or `server --mod-neoforge` | Dual maps exactly to `runFabricClient`, `runNeoForgeClient`, `runFabricServer`, `runNeoForgeServer`; single-loader no flag keeps `runClient`/`runServer`. Wrong/extra/mismatched options **fail closed**; **fake Gradle smoke PASS, actual game NOT RUN** |
| Publish locally | `publish local` | API Maven publication in consumer `build/fabric-amper/maven-repository` |
| Publish remotely | `publish maven` / `modrinth` / `curseforge` / `all` | **Real upload** after typed `PUBLISH` and local preflight; explicit user authorization required |

For **existing Amper consumers**, the wrapper rejects missing/duplicate YAML, consumer `build.gradle(.kts)`, and authored Loader metadata. **`migrate` is for the pre-conversion state**: exactly one `build.gradle.kts` or Groovy `build.gradle`, one existing source `fabric.mod.json`, no YAML/`gradle_backup/`, and at most one old settings file. The static converter does **not** evaluate Gradle or guarantee that JitPack/runtime works. `-D`/`--delete` must be **explicitly requested**; prefer backups. Review generated YAML against the [legacy syntax reference](#legacy-schema-1-fabric-only-reference): old key locations require explicit integer `schema: 1`; omitted schema means 1.1 in the published decoder. The v0.3.0 prerelease migrator/schema assets and JitPack artifact were published and verified; still check current network and the converted project. Run `validate` then `build` after the user-authorized conversion. `publish all` invokes `publishFabricAmper`, not Gradle's broader `publish`. Do not publish just because configuration exists; an unrelated `gradlew.bat` is not a verified consumer wrapper. More examples: [HUMAN_QuickGuide.md](HUMAN_QuickGuide.md).

**Stop-or-escalate for reports:** If the user only needs current config/dependency facts and `diagnose`/`explain` answers them, stop here. If they ask about exact external project compatibility, a runtime crash, a new Minecraft port, Mixin semantics, or uncertain side effects, read [FabricAmperSKILL.md](FabricAmperSKILL.md#route-read-only-diagnostics-dependency-explanation-and-release-preview) and check real runtime/source evidence. A release preview with no HTTP **cannot** confirm platform IDs, authentication, moderation or that a published version exists.

## Safe validation path

1. Check one YAML, known keys/types, fixed versions, real resource/entrypoint paths, single Settings plugin, no authored Loader JSON, no secret values.
2. Run `gradle help`, `gradle tasks --all`, `gradle validateFabricAmper`, `gradle build`; run `gradle test` for tests and `gradle verifyFabricAmperJar` for archives.
3. Inspect **each target** final JAR: Fabric has **exactly one root `fabric.mod.json` and no NeoForge TOML**; NeoForge has **`META-INF/neoforge.mods.toml` and no Fabric JSON**. For dual verify two isolated JARs, matching IDs/resources, JIJ only in the intended target.
4. If a gate depends on game launch, IntelliJ GUI sync, remote HTTPS upload or configuration-cache reuse, run that **separate** check only when authorized, and do not infer success from a build.
5. On failure, use the next table; if code/context mismatches its listed cause, **read [FabricAmperSKILL.md](FabricAmperSKILL.md)** and the actual error trace.

## FA001–FA015 fast remediation

| Code | Key cause | Immediate fix |
| --- | --- | --- |
| **FA001** | Missing `mod.yaml`/`mod.yml` or **both exist** | Keep **exactly one** consumer-root YAML; preserve whichever user intends |
| **FA002** | Bad/hostile YAML, duplicate key, alias/anchor, tag, multi-doc, invalid UTF-8 | Single UTF-8 mapping; remove duplicates, `&`/`*`/`<<`/tags |
| **FA003** | Unknown key, wrong type, unquoted version, bad URL/credentials | Compare exact reported path to schema; quote `"26.3"`; fix key/type |
| **FA004** | Yarn selected for MC 26.1+ | `fabric.mappings: mojang` for 1.1; consult the legacy section for version 1 |
| **FA005** | Modern Minecraft with wrong Java release/JDK | MC26.3 → `settings.java.release: 25` and JDK25 |
| **FA006** | Kotlin adapter or pinned versions mismatch | `settings.kotlin.enabled: true` + KGP `2.4.21` / language `2.4` / FLK `1.14.2+kotlin.2.4.21`, else remove adapter |
| **FA007** | `exported/include` with `compile-only` or `runtime-only` | Use `scope: all` **if semantically correct**, else remove flag |
| **FA008** | Manually authored resource `fabric.mod.json` | Move supported metadata into YAML; remove authored JSON after preserving its content |
| **FA009** | Bad mod ID/entrypoint; missing/invalid PNG, Mixin, Class Tweaker | Fix actual path, valid metadata, header and resource file |
| **FA010** | Loom/KGP plugin classpath conflict | Use **only** `dev.fabric-amper.settings` in consumer; resolve compatible plugin marker |
| **FA011** | Missing declared Maven/Modrinth artifact | Correct real fixed GAV/project/version and repository; distinguish offline/TLS/auth failures |
| **FA012** | Duplicate/conflicting dependency coordinate | Merge repeated declarations and correct scope once |
| **FA013** | Final JAR has zero or duplicate **root** `fabric.mod.json` | Remove source JSON, correct packaging; rerun `verifyFabricAmperJar` |
| **FA014** | `target: client` without split or illegal `include/exported` | `fabric.loom.splitEnvironmentSourceSets: true` for 1.1; see legacy section for version 1; remove illegal flags |
| **FA015** | Loom mirror/generated resources inconsistent or damaged | Allow regeneration; inspect persistent cache/race errors |

**Do not confuse FA008 with FA014.** FA008 concerns authored `fabric.mod.json`; **client dependency source-set errors are FA014**. A generic TLS error, Gradle dependency variant error, Minecraft crash or failed HTTP publication may not have any FA code.

## Common anti-patterns — replace, do not patch around

| Wrong / unsupported | Correct |
| --- | --- |
| `minecraft.loomVersion` / root `loomVersion` | Loom version comes from the producer Settings plugin; no consumer key |
| `fabric.classTweaker` | Schema 1.1 `fabric.accessWidener`; actual `.classtweaker` |
| `mod.dependencies` | `dependencies[]` for shared Gradle; `fabric.gradleDependencies`/`neoforge.gradleDependencies` for loader-specific, `fabric.depends` or `neoforge.dependencies` for installed mod requirements |
| `settings.fabric.splitEnvironmentSourceSets` | Schema 1.1 `fabric.loom.splitEnvironmentSourceSets`; see legacy section for version 1 |
| `publish.maven.username/password` literal secrets | `publish.maven.credentialsFromEnv: [NAME1, NAME2]` |
| `product: fabric/mod` plus legacy `publishing.repository` | Use new `publish.maven` or switch to API product |
| `$schema: ...` YAML root data | YAML-language-server `#` comment or editor mapping |
| `mappings: yarn` on MC26.3 | `mappings: mojang` |
| `gradle publish` as a “dry test” | `gradle help`/`build`/`verifyFabricAmperJar` without upload |

### Minimal output contract for agents

Respond with **files edited**, exact YAML key/value changes, Gradle commands **actually run** and exits, whether a production JAR's root metadata was inspected, and unresolved runtime/IDE/publishing risks. Do not claim tests executed from documentation alone. **If the table resolves the request: finish here. If not: read [FabricAmperSKILL.md](FabricAmperSKILL.md), then [HUMAN.md](HUMAN.md) or source only as needed.**
