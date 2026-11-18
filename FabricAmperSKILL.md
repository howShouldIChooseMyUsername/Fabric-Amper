---
name: fabric-amper
description: Use when creating, editing, validating, building, testing, or explicitly publishing a Fabric Amper schema 1.1 Fabric and/or NeoForge consumer configured with settings.gradle.kts and mod.yaml/mod.yml, without a consumer build.gradle.kts or authored Loader metadata.
---

# Fabric Amper — AI Configuration and Operations Guide

> **Custom AI-facing guide, not an auto-discovered Agent Skill.** The requested filename is `FabricAmperSKILL.md`. Agent Skills tools conventionally discover a `<skill-directory>/SKILL.md` file with `name` and `description` frontmatter. This file is an explicitly referenced project guide; its heading below is not a claim that an agent will discover it automatically. See [Packaging note](#packaging-note) at the end.

## Mission and activation

Read this guide **when the user wants to create, change, debug, build, test, package, or explicitly publish a Fabric Amper consumer project**. Convert a precise user goal into the minimum valid `mod.yaml` or `mod.yml` edits, real source files, a safe Gradle command, and a verifiable result. Always distinguish documented syntax from a feature that has actually passed acceptance testing.

**Hard requirement:** a consumer has only `settings.gradle.kts` and exactly one `mod.yaml` or `mod.yml` as *author-maintained build configuration*. It still contains ordinary Java/Kotlin source files, resource files, and tests. Do **not** create a consumer `build.gradle`, `build.gradle.kts`, or manually authored `fabric.mod.json`. The developer applies only `dev.fabric-amper.settings`; the internal `dev.fabric-amper.project` is applied by the Settings plugin.

### Source-of-truth and conflict order

1. **The user's request and approved actions** determine intent; never infer permission to publish, accept a EULA, alter a real world, or reveal credentials.
2. `specs.md` defines the foundational product contract, historical v1/G1–G30 gates, and schema 1.1 NeoForge/multi-loader (§12, especially §12.12). The implemented API/library and `publish` extensions are additionally specified in the latest `README.md`.
3. **Actual source** — `src/main/kotlin/dev/fabricamper/config/ModYamlDecoder.kt`, `ModYamlModel.kt`, `ModYamlValidator.kt`, `StrictYamlReader.kt`, plugins and tests — decides what is accepted *today*.
4. `fabric-amper.schema.json` (Draft-07) provides schema keys, types, enums, and editor guidance. It does **not** prove all cross-field constraints, resource existence, buildability, or publication readiness.
5. `QA_REPORT.md` records tested behavior and unresolved gates. `HUMAN.md` explains the same contract for people.

When documentation and implementation disagree, **inspect the current implementation and report the discrepancy** instead of silently inventing an alternate key or changing unrelated source. Record what was actually tested versus inferred.

## Non-negotiable invariants

- One YAML file in the consumer root: `mod.yaml` **or** `mod.yml`, never both. One YAML document, root mapping, UTF-8, no duplicate keys, aliases, anchors, merge keys, arbitrary custom tags, or unsupported extra properties.
- Required root keys: `product`, `mod` and `minecraft`; nested `mod.id`, `mod.version`, `minecraft.version` are mandatory. **The published v0.3.0 decoder defaults omitted `schema` to 1.1**, and explicit quoted `schema: "1.1"` also selects 1.1. Explicit integer `schema: 1` selects the original Fabric-only format, documented together in [Legacy schema 1](#legacy-schema-1-fabric-only-reference). Unquoted `schema: 1.1` is a YAML float and invalid. Use explicit `schema: "1.1"` in examples for clarity. Versions that look numeric **must be quoted strings**.
- Schema **1.1** accepts a `product` scalar `fabric/mod`, `fabric/api`, `neoforge/mod`, `neoforge/api` (with `fabric/library` as Fabric API alias), or an array with **at most one target per loader**, e.g. `[fabric/mod, neoforge/mod]` or hybrid API/mod. The selected loader requires its corresponding `fabric:` and/or `neoforge:` block. Shared `mod` and `minecraft.version` remain at root; Fabric Loader-specific properties belong to `fabric:`.
- With 1.1, Fabric-specific fields belong to `fabric.loader`, `fabric.api`, `fabric.mappings`, `fabric.entrypoints`, `fabric.loom.splitEnvironmentSourceSets`, `fabric.gradleDependencies`, etc.; NeoForge-specific fields belong to `neoforge.version`, `neoforge.modLoader`, `neoforge.dependencies`, `neoforge.gradleDependencies`, etc. Shared `settings.java` and `settings.kotlin` remain at root. See the dedicated legacy section for version 1 key locations.
- All values in YAML are *configuration values*, not fragments of Gradle Kotlin DSL. Only real keys from `ModYamlDecoder` and the schema are legal; unknown keys trigger `FA003`.
- Never write `src/main/resources/fabric.mod.json`, `src/client/resources/fabric.mod.json`, or authored `META-INF/neoforge.mods.toml`. In schema 1.1 each **Fabric** target JAR has exactly one **root** `fabric.mod.json` and zero NeoForge metadata; each **NeoForge** target JAR has `META-INF/neoforge.mods.toml` and no Fabric JSON. Never alter `build/generated/**` or `.gradle/fabric-amper/loom/**` mirrors by hand.
- Keep user code, existing Mod Loader dependencies/predicates, release notes, and assets intact unless the request clearly requires changing them. Do not downgrade versions or remove unrelated keys to make validation pass.
- Use fixed, known Maven coordinates and actual existing platform project identifiers. Do not fabricate Maven artifacts, published Yarn versions, Modrinth/CurseForge IDs, account credentials, or a public plugin repository.
- Do not embed secrets in YAML, Kotlin DSL, commands to be logged, sample output, tests, or commits. YAML publishing fields name **environment variables**, never token values.
- `gradle help`, `gradle tasks --all`, `gradle test` and `gradle build` are safe non-publication steps. Remote publication tasks are *side-effectful* and require an explicit user request and deployment prerequisites.
- Do not auto-accept Mojang's EULA, reuse a user's main Minecraft world in automated tests, or equate a successful Gradle build with runtime/IDE/shipping acceptance.

## Agent execution protocol

### Command precedence: prefer Fabric Amper's wrapper when supported

**When operating on Windows with an available complete Fabric Amper checkout, FIRST look for `fabric-amper.bat` and use it for any supported operation.** This is a binding execution-routing rule, not an optional shorthand: `help`, `version`, `docs`, `doctor` (including `--full`, `--paths`, opt-in `--fix`), `init` (including `--here`), `migrate` (including `-C`, `-D`, `--delete`, `--dry-run`), `tasks`, `validate`, `build`, `verify`, `test`, `clean`, `run`, `server`, `diagnose`, `explain`, `plan`, `port` and explicitly requested `publish` variants. Use `-C "consumer_path"` **before** most subcommands when necessary; `migrate` accepts `-C` / `--project` before **or after** the verb, and `doctor` also accepts `doctor -C ".\httpmod"` and `doctor --project ".\httpmod"`, with the path relative to the current terminal directory. The repeated form `doctor -C ".\httpmod" doctor` is supported. For an operation **not supported by the BAT wrapper** (such as a custom Gradle task, `validatePlugins`, a special TestKit filter, or direct producer-only publication), use its **original supported Gradle, Java, Kotlin, or other native command**. Do not claim the wrapper supports arbitrary Gradle tasks or invent a wrapper command.

**Release-version inspection:** The v0.3.1 release-target `fabric-amper.bat` with **no arguments**, `-V`, `--version`, or `version` should print `v0.3.1`, without Gradle or file changes. An empty BAT invocation is **not** `help`; use `fabric-amper.bat help` / `--help`. **The v0.3.0 GitHub prerelease, its nine SHA-256-verified assets, JitPack status `ok` and a successful public remote-plugin consumer build were independently verified on 2026-11-17.** Those results do not verify the new v0.3.1 tag/assets/JitPack. Inspect the exact BAT on the user's installation. The earlier publicly released v0.2.0 must not be described as supporting NeoForge schema 1.1.

**Never reflexively type `./gradlew` / `gradlew.bat`.** Only use a Gradle Wrapper when its files actually exist in the chosen project and are trusted/appropriate. Otherwise allow the BAT launcher to select a known compatible installed/cached Gradle, or use the native Gradle launcher for unsupported work. The presence of an unrelated `gradlew` in a parent or neighbor directory does not authorize using it. **Automatic repair policy:** when `doctor` confirms a required JDK/Gradle toolchain is missing or unusable, the AI agent should preview and then run `doctor --fix` **without asking for an additional consent step**, as part of the requested Fabric Amper development workflow. Do not expand this policy to unrelated installations, publication, accepting EULAs, or overwriting existing user files. The BAT first fetches the version-matched `fabric-amper-doctor-fix.ps1` from its GitHub Release into **`scripts/` below the BAT** if absent, then checks the pinned SHA-256 before executing. The `migrate` command independently fetches its own missing version-matched PS1 into **`scripts/`** and missing schema into the BAT root, each SHA-256 checked before conversion. An unexpected existing helper/schema is not overwritten or executed; a missing/stale asset, wrong hash or non-writable folder causes an error **before execution**. A checksum-keyed query/no-cache headers avoid stale replaced assets. Neither `doctor --fix --dry-run` nor `migrate --dry-run` downloads anything: **if a required local asset is missing, it only previews the download** rather than executing a partial inspection. `init --here` may preserve existing tool files but must refuse if project files already exist. Do not bypass the protection by renaming/deleting user files.

### Required Windows doctor → repair → recheck workflow

**Before a Windows task that depends on JDK/Gradle availability, run `fabric-amper.bat doctor` first and inspect its output and exit status.** If all required toolchains are available, continue the originally requested work without running `--fix`. If `doctor` reports a missing or unusable required JDK/Gradle toolchain, run `fabric-amper.bat doctor --fix --dry-run` to inspect the proposed repair. A missing PS1 means this preview displays **only the proposed GitHub Release helper download**; it is not proof that the machine's toolchains were checked.

**If a required toolchain is missing, do not skip the check or continue with a broken environment.** After the read-only preview, invoke `fabric-amper.bat doctor --fix` **automatically; do not ask the user to approve this Fabric Amper repair separately**. Inspect its result. This operation may download the version-matched helper, install required JDK/Gradle software, change user PATH/JAVA_HOME, and add only missing consumer bootstrap/editor files. Do not treat this as permission for unrelated changes, overwrites, external publication, or automatic EULA acceptance.

**After an automatic `--fix`, rerun `fabric-amper.bat doctor` and check that the needed JDK/Gradle is now available before resuming the original build/init/run task.** If an environment-variable change is not visible to the current terminal, open a new terminal and check again. If repair fails, the helper's checksum is wrong, or the second check still fails, report the exact blocker and stop the **dependent** operation; do not claim the environment is repaired, silently install another way, or retry indefinitely. `doctor` is a local toolchain check, **not** proof that JitPack, Minecraft runtime, or remote publishing works.

### Before editing

1. Locate the consumer root (the directory containing `settings.gradle.kts`) and identify whether you are acting on the **plugin producer** or on a **separate consumer**. Do not accidentally create sample consumer `mod.yaml` inside the producer.
2. Read `settings.gradle.kts`, exactly one existing YAML document if present, the relevant source tree, resources, requested Java/Kotlin entrypoints, and any reported Gradle errors. If both `mod.yaml` and `mod.yml` exist, stop the build attempt and resolve with the user rather than overwriting either.
3. Inspect the user's requested change, mapping it to the field table below. Separate **metadata**, **Gradle classpath**, **Loader runtime dependencies**, **source sets**, **publication**, and **actual code/assets**. They are distinct operations.
4. Confirm fixed Minecraft, Loader, Fabric API, mappings, Java/Kotlin versions, repository endpoints, Maven coordinates, and platform IDs *only where needed*. If genuinely unknown, leave the feature unconfigured or request the specific missing value; never guess.
5. Read the schema and decoder before adding an unfamiliar key. If existing YAML contains a surprising property, verify it rather than normalizing it blindly.

### After editing

1. Preserve the existing single YAML filename, indentation, field order where practical, comments, and unrelated settings. Do not rewrite a user's whole project when a single mapping entry solves the task.
2. Check the YAML for duplicate keys, unknown keys, string-versus-number mismatches, resource paths, and contradictions (especially `fabric.environment` versus main/client code in schema 1.1).
3. Run the **non-upload** validation sequence using `fabric-amper.bat tasks` → `fabric-amper.bat build` (and `fabric-amper.bat test` for test changes) when the Windows wrapper exists. `help` here is the wrapper's own help; if a separate Gradle `help` task or special flags are needed, that operation is **not exposed by the wrapper** and must use an actually available native Gradle launcher or a verified consumer wrapper. Never fall back automatically to an unverified `./gradlew`.
4. Inspect the resulting production JAR and root Loader metadata; report both command results and any relevant negative tests. If Gradle fails, fix the *first causal diagnostic*, then repeat — do not simply retry external downloads indefinitely.
5. Report exact changed paths, configuration effect, commands actually run, tests genuinely observed, and remaining risk. Never state a task, IDE import, game boot, upload, or G gate passed when not observed.

## Intent → exact configuration routing

The **next table is the decision index**, not a substitute for the runnable recipes. Entries below `mod` or `settings` must go **inside those mappings**, not at the document root. Except where explicitly stated, all tasks run from the *consumer* directory.

| User asks for… | YAML change / actual file | Validation / caution |
| --- | --- | --- |
| A new schema 1.1 Fabric mod | `product: fabric/mod`, `mod.id`, `mod.version`, `minecraft.version`, `fabric.loader`, `fabric.entrypoints`; optional `schema: "1.1"` for clarity | Omitted schema selects 1.1 in published v0.3.0; validate final build; `gradle help` then `gradle build` |
| A new schema 1.1 NeoForge mod | `product: neoforge/mod`, root shared `mod`/`minecraft.version`, `neoforge.version` and optional `neoforge.modLoader` | Real `@Mod` class, generated `META-INF/neoforge.mods.toml`; no Fabric entrypoint |
| Independent dual-loader mod (no Architectury) | `product: [fabric/mod, neoforge/mod]`, both loader blocks, `architectury.enable: false` | `:fabric` and `:neoforge` only; **no `:common` directory/project**; two isolated JARs |
| Dual loader with shared Architectury sources | Same product array, `architectury.enable: true` | `:common`, `:fabric`, `:neoforge`; split flag is **ignored with a notice** for dual+Architectury |
| Explicit legacy Fabric configuration | Follow [Legacy schema 1](#legacy-schema-1-fabric-only-reference) | Preserve existing consumers; do not silently upgrade them to 1.1 |
| An API/library mod for downstream projects | `product: fabric/api` (or alias `fabric/library`), required `mod.group`, optional `mod.archivesName` | `gradle build`; explicit local Maven publish when needed |
| A new display name/description/license | `mod.name`, `mod.description`, `mod.license` (string or list) | Inspect target-specific generated JSON/TOML |
| A different JAR file basename | `mod.archivesName`, not `base.archivesName` or `artifactId` | Verify filename and Maven publication identity |
| A new mod ID/version | `mod.id` / `mod.version` | Update actual resource namespace and references if affected; inspect JAR JSON |
| Minecraft 26.3 with official mappings | Schema 1.1: `minecraft.version: "26.3"`, `fabric.mappings: mojang`, `settings.java.release: 25`, `fabric.loader` and/or `neoforge.version` | No modern Yarn; Java 25; Fabric non-remap / NeoForge MDG |
| Minecraft 1.21.11 remap profile | `minecraft.version: "1.21.11"`, `settings.java.release: 21`, supported `fabric.mappings` | Verify all API/Loader versions and real game compatibility; remap profile |
| Yarn mappings (older Minecraft only) | `fabric.mappings: {type: yarn, version: "VERIFIED_VERSION"}` | Never invent Yarn version; reject on 26.1+ (FA004) |
| Fabric API dependency | `fabric.api: "EXACT_PUBLISHED_VERSION"` | Check version matches game and actual artifact availability |
| Kotlin code and adapter | `settings.kotlin.enabled: true` with pinned Kotlin settings; optional adapter entrypoint object | Real Kotlin sources; `gradle compileKotlin`/`build`; FA006 |
| Client-only Fabric mod | `fabric.environment: client` and `fabric.entrypoints.client` | Actual client code and side-safe Mixins; run isolated client |
| Shared mod with client-only source or dependency | `fabric.loom.splitEnvironmentSourceSets: true` | Fabric-only split or Fabric subproject of independent dual; never include/export client-targeted deps |
| Loader-required installed mod | `fabric.depends.<actual_mod_id>: ">=EXACT_VERSION"`; NeoForge uses `neoforge.dependencies[]` | Runtime relationship, not a Maven download |
| Recommended/suggested/incompatible Fabric mod | `fabric.recommends/suggests/conflicts/breaks` | Preserve genuine predicates; inspect Fabric JSON |
| Maven library for compilation/runtime | `repositories` and `dependencies[].maven`, optional `kind` / `scope` | Verify fixed GAV and repository, resolve via `gradle build` |
| A root Gradle dependency for just one selected loader (schema 1.1) | Add `loader: fabric` or `loader: neoforge` inside a root `dependencies[]` Maven/Modrinth **object** entry | Omit for shared behavior; selected loader must appear in `product`; invalid in schema 1, test and nested platform lists |
| Mod hosted in Modrinth's Maven | `repositories: [modrinth]` plus `dependencies[].modrinth.project/version` and optional `kind: mod` | An artifact dependency, not a release-upload project ID |
| Exported public API type | `dependencies[].exported: true` with `scope: all` | API/library publication; verify downstream compile variant |
| Internal runtime-only helper | `dependencies[].scope: runtime-only` | Cannot also `exported` or `include` |
| Development compile-only helper | `dependencies[].scope: compile-only` | Not packaged or runtime-transitive |
| Nested Fabric JAR-in-JAR library | For 1.1 dual use `fabric.gradleDependencies[].include: true` and `scope: all` | Verify Fabric `META-INF/jars` and no NeoForge JIJ contamination |
| JUnit tests | `test-dependencies` with a real fixed JUnit dependency | Create tests under `src/test/`; run `gradle test`; no export/client/include |
| Fabric Mixin configuration | `fabric.mixins` | Create real file in Fabric resources, check side behavior |
| Modern Fabric Class Tweaker | `fabric.accessWidener: "my_mod.classtweaker"` | Create file under Fabric resources with `classTweaker v1 official` header |
| Older Minecraft Access Widener | `fabric.accessWidener: "my_mod.accesswidener"` for supported remap profiles | Correct `accessWidener v1/v2 named/intermediary` header |
| Fabric icon | `fabric.icon` | Create real PNG in relevant platform resources |
| Custom Fabric Loader metadata | `fabric.custom` | Inspect generated JSON; no invented Gradle effects |
| Local Maven library publication | `product: fabric/api` and `mod.group` | `gradle publishMavenJavaPublicationToFabricAmperLocalRepository` (writes inside consumer `build/`) |
| Explicit remote Maven publication | `publish.maven.url` and optional `credentialsFromEnv: [USER_VAR, PASS_VAR]` | Requires `mod.group`; HTTPS only; explicit `gradle publishFabricAmperMaven` |
| Modrinth release | `publish.modrinth.projectId`, `tokenEnv` and verified release options | No platform upload until explicitly asked; task `publishFabricAmperModrinth` |
| CurseForge release | `publish.curseforge.projectId` (integer), `tokenEnv` and verified release options | Distinguish string tag names from integer version IDs |
| Same release metadata for Modrinth and CurseForge | `publish.common.releaseType`, optional `name`, `gameVersions`, `changelog` OR `changelogFile`, and `dependencies[]` with `slug` / optional `modrinthId` and `curseforgeId` / `type` | Only applies to destinations explicitly present; platform values override, relations can override or append |
| Existing API remote Maven legacy configuration | See [Legacy schema 1](#legacy-schema-1-fabric-only-reference) | API/library only; do not also set `publish.maven` |
| Avoid authored metadata collision | Never add source `fabric.mod.json` or `META-INF/neoforge.mods.toml`; `fabric.resources.failOnExistingFabricModJson` remains true | FA008 and platform-specific validation; plugin generates metadata |
| IDE sync | Use generated Gradle model; manual or automatic IntelliJ sync must be actually observed | Do not mark G6/G28 passed from `gradle help` |
| Game run | BAT `run` / `server` (dual requires exactly one of `--mod-fabric` or `--mod-neoforge`) | Single loader: original `runClient`/`runServer`; dual: `runFabricClient`/`runNeoForgeClient` and matching Server; **real runtime not verified** |
| Fix configuration cache | Correct deterministic YAML and mirror resources; retry safe non-publish tasks | A remote Maven upload + CC HIT is not established |

## Complete starter files: Java-only Minecraft 26.3

This is the **standalone, structurally valid baseline**, not a fragment. Use real source files and an available tagged JitPack module. A `plugins` block alone does not resolve the plugin from the Gradle Plugin Portal.

**Consumer `settings.gradle.kts`**:

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

plugins {
    id("dev.fabric-amper.settings") version "0.3.1"
}
```

**v0.3.1 release-target bootstrap:** the Settings plugin redirects to JitPack Git tag `v0.3.1` and requests Gradle plugin version `0.3.1`. Confirm the new tag, assets and JitPack consumer build once published. **Historical 2026-11-17 verification:** the v0.3.0 prerelease had nine SHA-256-verified assets and resolved in a fresh NeoForge + Architectury + Kotlin consumer (including `help`, validation, build and JAR verification). For offline develop TestKit, use the verified local build/bootstrap. The old local `fabric-amper.bat setup` flow has been removed. Plugin repositories in `settings.gradle.kts` and artifact repositories in `mod.yaml` have separate jobs.

**Consumer `mod.yaml`**:

```yaml
schema: "1.1"
product: fabric/mod
mod:
  id: example_mod
  version: "1.0.0+26.3"
  group: org.example
  archivesName: example-mod
  name: Example Mod
minecraft:
  version: "26.3"
settings:
  java:
    release: 25
    sourcesJar: true
  kotlin:
    enabled: false
fabric:
  loader: "0.20.1"
  mappings: mojang
  api: "0.165.2+26.3"
  environment: "*"
  entrypoints:
    main:
      - org.example.ExampleMod
  resources:
    failOnExistingFabricModJson: true
repositories:
  - fabric
  - mavenCentral
```

**Source file** `src/main/java/org/example/ExampleMod.java` (real code, not YAML):

```java
package org.example;

import net.fabricmc.api.ModInitializer;

public final class ExampleMod implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("Example Mod initialized");
    }
}
```

**Expected tree (author-maintained entries only):**

```text
my-fabric-mod/
├── settings.gradle.kts
├── mod.yaml
└── src/
    └── main/
        └── java/org/example/ExampleMod.java
```

**Check:**

```powershell
gradle help
gradle tasks --all
gradle build
```

On a successful **Fabric** build, inspect `build/libs/example-mod-1.0.0+26.3.jar` and the generated `fabric.mod.json`. The production archive must have exactly one **root** `fabric.mod.json`, whose ID and version match the YAML. Do not claim the example is runnable if the plugin marker or game artifacts are inaccessible.

### Schema 1.1 loader-specific examples and source layout

**The following is the published v0.3.0 prerelease contract, backed by source tests, selected consumer builds and an independent remote JitPack consumer build.** The decoder **defaults omitted `schema` to 1.1**, verified by regression tests. Use explicit `schema: "1.1"` in examples for clarity. The complete older Fabric key layout is covered separately in [Legacy schema 1](#legacy-schema-1-fabric-only-reference); do not combine old and new field locations.

**NeoForge-only Java** (complete minimal `mod.yaml`; use a real source `@Mod("example_mod")` class in `src/main/java`):

```yaml
schema: "1.1"
product: neoforge/mod
mod:
  id: example_mod
  version: "1.0.0+26.3"
  name: Example NeoForge Mod
  license: MIT
minecraft:
  version: "26.3"
settings:
  java:
    release: 25
neoforge:
  version: "26.3.0.101" # example candidate; verify actual compatible release
  modLoader: javafml
repositories: [mavenCentral]
```

**Dual-loader without Architectury** (complete YAML; create real independent `fabric/src/main/java` `ModInitializer` and `neoforge/src/main/java` `@Mod` sources):

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
  version: "26.3.0.101" # verify version with active NeoForge repository
repositories: [mavenCentral]
```

The independent dual product creates **`:fabric` and `:neoforge` only, without `:common`**. With `architectury.enable: true`, a **real `:common`** module is built and consumed by both platform projects; do not create it when false. Java is the default; `--kotlin` creates actual `.kt` sources and uses each platform's compatible runtime setup. `--splitEnvironmentSourceSets` applies to the Fabric side alone in independent dual, is **accepted but explicitly ignored** in dual+Architectury, and must be **rejected** in NeoForge-only; Fabric-only split remains supported. Generated output is **one Fabric JAR with only Fabric metadata** and/or **one NeoForge JAR with only NeoForge TOML metadata**, never cross-polluted. Dual Architectury, independent dual, hybrid API/mod and Fabric-only JIJ isolation have real develop TestKit build evidence, and a separate public v0.3.0 JitPack NeoForge+Architectury+Kotlin consumer build passed. **Actual Minecraft client/server runtime and IntelliJ GUI remain unverified.** Keep `product: [fabric/api, neoforge/mod]` and the reverse valid as independent publication types; do not assume remote Maven upload has passed.

## Safe modification recipes

**Recipe convention:** every `yaml` block labeled **FRAGMENT** below uses **schema 1.1** and is an illustrative subsection to merge under the correct existing keys; it is **not** a new complete `mod.yaml`. Never append a second `mod:`, `fabric:`, `neoforge:`, `settings:` or `dependencies:` mapping or duplicate a key. Preserve all unrelated fields. When replacing an existing value, edit that value in place. For an explicitly selected old schema, use only the consolidated [legacy reference](#legacy-schema-1-fabric-only-reference).

### Request: switch from a normal mod to a public API/library

1. Change root `product: fabric/mod` to `product: fabric/api` (or the `fabric/library` alias).
2. Supply the user's real `mod.group`; set `mod.archivesName` only if requested. Keep `mod.id` stable unless explicitly asked to change the Loader ID.
3. Put public contract types under a normal Java package and mark only true public dependencies as `exported: true`.
4. Build, then **explicitly** publish locally to the consumer's `build/fabric-amper/maven-repository`. Validate a downstream project against the published POM, Gradle Module Metadata, and resolved compile/runtime variants.

**FRAGMENT — merge into root and existing `mod`:**

```yaml
product: fabric/api
mod:
  id: library_mod
  version: "1.2.3"
  group: dev.example.library
  archivesName: library-contract
settings:
  java:
    release: 25
    sourcesJar: true
```

The fragment omits required `minecraft` and must be integrated with a complete document. Do **not** interpret the example group as a published artifact.

### Request: add a Maven dependency

First confirm the actual Maven group, artifact, **fixed version**, repository, mod-vs-library nature, source set, desired scope, and whether the user wants it embedded or exported. Never infer `include: true` merely because a library is required at runtime. Use these *alternative* declaration styles:

**FRAGMENT — short coordinate, shorthand, and full object:**

```yaml
repositories:
  - mavenCentral
dependencies:
  - "org.example:existing-lib:1.2.3"
  - "org.example:another-lib:2.0.0": compile-only
  - maven: "org.example:api-contract:3.1.0"
    kind: library
    scope: all
    target: main
    exported: true
```

All `org.example` coordinates in this fragment are **fictional placeholders**. Replace them with actually resolvable coordinates before building. A one-key dependency shorthand has its coordinate as the key and a scope as the value; accepted shorthand values are `all`, `compile-only`, `runtime-only`, `exported`, and `include`.

**FRAGMENT — loader-selected root object entries (schema 1.1):**

```yaml
dependencies:
  - "org.apache.commons:commons-lang3:3.17.0"  # common to selected loaders
  - maven: "org.example:fabric-helper:1.0.0"
    loader: fabric
  - maven: "org.example:neoforge-helper:1.0.0"
    loader: neoforge
```

An omitted `loader` shares the root dependency across selected targets; an explicit `loader` requires a matching selected target in `product`, including a single-loader product. The optional selector belongs **inside each root dependency object** and is restricted to `fabric` / `neoforge`. It cannot be used on a coordinate string or one-key shorthand, in `test-dependencies[]`, in `fabric.gradleDependencies[]` / `neoforge.gradleDependencies[]`, or with explicit legacy `schema: 1`. These `org.example` coordinates are illustrative.

**Scope decision:**

| Desired semantics | `scope` | `exported` | `include` |
| --- | --- | --- | --- |
| Normal implementation dependency | `all` (default) | false | false |
| Public API type used by consumers | `all` | true | false unless independently justified |
| Required only to compile | `compile-only` | false | false |
| Required only when running | `runtime-only` | false | false |
| Bundle nested dependency JAR | `all` | false unless part of API | true |

For `target: client`, enable `fabric.loom.splitEnvironmentSourceSets: true` and **never** add `exported: true` or `include: true`. A Gradle dependency does not automatically establish a Fabric Loader `fabric.depends` runtime requirement.

**FRAGMENT — loader-specific artifact dependencies for a schema 1.1 dual-loader consumer:**

```yaml
repositories:
  - mavenCentral
fabric:
  gradleDependencies:
    - maven: "org.apache.commons:commons-lang3:3.14.0"
      kind: library
      scope: all
      include: true
neoforge:
  gradleDependencies:
    - maven: "org.apache.commons:commons-lang3:3.14.0"
      kind: library
      scope: all
```

Merge these lists into existing `fabric:` and `neoforge:` blocks (which must also contain their required Loader versions). The Fabric declaration embeds its own helper JAR; NeoForge's separate declaration does not inherit that `include: true`. Root `dependencies[]` supports neutral shared artifacts and, for schema 1.1 object entries, an optional `loader` selector for a chosen backend. The nested platform lists already imply their loader and do not take a second selector. Repository arrays remain valid at the root in both schemas; schema 1.1 can also use loader-specific `fabric.repositories[]` and `neoforge.repositories[]` when needed. Verify each JAR's contents after building; this fragment alone does not prove runtime loading.

### Request: pull a mod dependency from Modrinth Maven

**FRAGMENT — this resolves an artifact; it does not upload a release:**

```yaml
repositories:
  - modrinth
dependencies:
  - modrinth:
      project: REAL_PROJECT_OR_SLUG
      version: REAL_FIXED_VERSION
    kind: mod
    scope: all
```

Confirm that the real Modrinth project/version resolves to a Maven artifact and is compatible with the chosen game/Loader. **Do not** confuse `dependencies[].modrinth.project` with `publish.modrinth.projectId`. If Fabric Loader must require that installed mod, additionally set `fabric.depends` with the **actual Loader mod ID**, not an inferred hosting-platform ID.

### Request: add or modify Loader-side requirements

**FRAGMENT — Fabric version predicates, not Maven coordinates:**

```yaml
fabric:
  depends:
    minecraft: ">=26.3"
    fabricloader: ">=0.20.1"
    another_mod:
      - ">=2.0.0 <3.0.0"
      - ">=4.0.0"
  recommends:
    optional_mod: ">=1.0.0"
  conflicts:
    incompatible_mod: "*"
```

Do not duplicate or overwrite a user's authored `depends.minecraft` or `depends.fabricloader`. Fabric Amper verifies specified predicates, including that the fixed configured Loader is compatible. OR arrays are Loader predicate alternatives; verify each term before accepting the array.

**Alternative FRAGMENT — NeoForge's Maven-style installed-mod version interval:**

```yaml
neoforge:
  dependencies:
    - modId: another_mod
      type: required
      versionRange: "[2.0,3.0)"
      side: BOTH
```

The NeoForge `dependencies[]` list is Loader metadata; it is different from `neoforge.gradleDependencies[]`, which resolves Gradle artifacts. Preserve each loader's actual mod ID and supported predicate syntax.

### Request: use client code without server classloading

**FRAGMENT — for a *shared Fabric* mod with a client entrypoint:**

```yaml
fabric:
  environment: "*"
  entrypoints:
    main:
      - org.example.ExampleMod
    client:
      - org.example.client.ExampleClient
  loom:
    splitEnvironmentSourceSets: true
  gradleDependencies:
    - maven: "org.example:real-client-only-helper:1.0.0"
      kind: library
      scope: compile-only
      target: client
```

Put `ExampleClient` under `src/client/java/org/example/client/` for a single Fabric product, or the Fabric subproject's corresponding client tree for independent dual loaders. This placeholder GAV is not a real package; replace it. For a *strictly client-only* Fabric mod, instead set `fabric.environment: client` and keep client entrypoints and Mixins client-safe. `fabric.environment` controls where the **whole Fabric mod** loads; `target: client` controls a **dependency/source set**.

### Request: enable Kotlin

Kotlin is **opt-in**. Preserve Java-only configurations with `enabled: false` (or omit the Kotlin mapping); do not automatically turn Kotlin on because the user mentions a Kotlin *dependency*.

**FRAGMENT — pinned Kotlin toolchain and an adapter entrypoint:**

```yaml
settings:
  kotlin:
    enabled: true
    pluginVersion: "2.4.21"
    languageVersion: "2.4"
fabric:
  kotlin:
    fabricLanguageKotlin: "1.14.2+kotlin.2.4.21"
  entrypoints:
    main:
      - adapter: kotlin
        value: org.example.ExampleKtMod
```

Create `src/main/kotlin/org/example/ExampleKtMod.kt` with a real valid Fabric initializer, or preserve the user's existing Java entrypoint instead of changing it to the Kotlin adapter for no reason. Entrypoint *string* syntax is also allowed; `{adapter, value}` objects require both fields. Do not redeclare `fabric.languageAdapters.kotlin`; the installed Fabric Language Kotlin integration supplies it. Incorrect pinned versions or an adapter with Kotlin disabled fail with **FA006**. Kotlin disabled on a cold dependency cache does **not** currently guarantee no Kotlin Gradle plugin artifacts will be fetched.

### Request: add a Mixin

**FRAGMENT — a common Mixin and a strictly client-scoped Mixin:**

```yaml
fabric:
  mixins:
    - example.mixins.json
    - config: example.client.mixins.json
      environment: client
```

Create `src/main/resources/example.mixins.json` for the common mixin and `src/main/resources/example.client.mixins.json` (or `src/client/resources/` with split source sets) for the client-specific one. The referenced file must exist, contain valid Mixin JSON, reference real Mixin classes, and be appropriate for its Loader side. If a resource lives *only* under `src/client/resources` while the mod also loads on a server, explicitly mark its Mixin `environment: client`. Do not assume a successful compile proves the Mixin applies in Minecraft.

### Request: add Class Tweaker or legacy Access Widener

For schema 1.1 the YAML property is **`fabric.accessWidener`**, even for modern Class Tweakers. There is no `fabric.classTweaker` key.

**FRAGMENT — Minecraft 26.3:**

```yaml
fabric:
  accessWidener: example.classtweaker
```

Create `src/main/resources/example.classtweaker`. Its **first line must be exactly**:

```text
classTweaker v1 official
```

After the header, write real Class Tweaker directives that match the appropriate Fabric/Loom format; never fabricate directives when a target class or member is unknown. For a supported older Minecraft remap profile, use `fabric.accessWidener: example.accesswidener` and a file whose first line matches `accessWidener v1 named`, `accessWidener v2 named`, `accessWidener v1 intermediary`, or `accessWidener v2 intermediary` as supported by the validator and remapping pipeline. The resource must reside under **`src/main/resources/`**, not only `src/client/resources/`. Verify `validateFabricAmper` and the archived resource.

### Request: add a single or multi-resolution icon

**FRAGMENT — choose *one* supported form:**

```yaml
fabric:
  icon: assets/example_mod/icon.png
```

**Alternative FRAGMENT — size map:**

```yaml
fabric:
  icon:
    "32": assets/example_mod/icon32.png
    "128": assets/example_mod/icon128.png
```

Actually create valid PNG files in `src/main/resources/assets/example_mod/` or a permitted client resource root when appropriate. A size map uses **positive-integer string keys**; do not use `small`/`large`. Resource path checks protect the project root; do not refer to outside files, symlink escapes, or absolute filesystem paths. No icon is generated from text by the Gradle plugin.

### Request: add custom Loader JSON metadata

**FRAGMENT — custom JSON-compatible values only:**

```yaml
fabric:
  custom:
    example_mod:feature_flags:
      showOverlay: true
      maxConnections: 8
      displayStyle: compact
```

`fabric.custom` is a Fabric Loader metadata mapping and does **not** run arbitrary code, install a Java library, or define a Gradle task. Preserve any existing namespaced keys. Do not add executable tags, aliases, cyclic values, or unsupported key/value types.

### Request: configure JUnit tests

**FRAGMENT — test-only dependency:**

```yaml
test-dependencies:
  - "org.junit.jupiter:junit-jupiter:6.0.0"
```

Create actual JUnit Jupiter test classes under `src/test/java/` or `src/test/kotlin/` as appropriate; then run `gradle test`. If the project has another verified JUnit version or test platform, preserve its intentional settings rather than overwriting them. No test dependency can be `exported`, `included`, or targeted to `client`. Avoid asserting `0 tests` is a successful discovery check: inspect the test reports.

## Complete API/library profile for Maven consumers

This is a complete **schema 1.1** Fabric API configuration. The `dev.example` Maven coordinates are illustrative, unresolvable placeholders; replace them with **verified, reachable** dependencies before expecting a real build or local publication.

```yaml
schema: "1.1"
product: fabric/api
mod:
  id: example_api
  version: "1.2.3"
  group: dev.example.fabric
  archivesName: example-api
  name: Example Fabric API
minecraft:
  version: "26.3"
settings:
  java:
    release: 25
    sourcesJar: true
  kotlin:
    enabled: false
fabric:
  loader: "0.20.1"
  mappings: mojang
  api: "0.165.2+26.3"
  environment: "*"
  entrypoints:
    main:
      - dev.example.api.ExampleApiMod
  loom:
    splitEnvironmentSourceSets: false
repositories:
  - fabric
  - mavenCentral
dependencies:
  - maven: "dev.example:public-contract:1.0.0"
    kind: library
    scope: all
    exported: true
  - maven: "dev.example:internal-helper:1.0.0"
    scope: all
  - maven: "dev.example:optional-compiler-annotations:1.0.0"
    scope: compile-only
test-dependencies:
  - "org.junit.jupiter:junit-jupiter:6.0.0"
```

Keep the same `settings.gradle.kts` bootstrap as the ordinary mod, and implement `dev.example.api.ExampleApiMod` in real source. The `fabric/library` alias has the same API behavior. The project automatically configures Java library/Maven publication behavior, not a new user-written `build.gradle.kts`.

**API publishing validation:**

```powershell
gradle build
gradle publishMavenJavaPublicationToFabricAmperLocalRepository
```

The second command publishes **inside the consumer project**, under `build/fabric-amper/maven-repository/`. Inspect JAR, source JAR (when enabled), POM, Gradle `.module`, and actual Maven coordinates:

- `groupId` = `mod.group`
- `artifactId` = `mod.archivesName`, or `mod.id` if no archive override
- `version` = `mod.version`

Verify a **separate downstream consumer** can compile against exported public API types, does not see deliberately private compile-only types as a public API, and resolves appropriate runtime dependencies. Do not mistake a local publication PASS for an authenticated remote HTTPS upload or runtime game PASS.

## Legacy schema 1 (Fabric-only reference)

**This is the single consolidated version 1 syntax reference in this guide.** Use it only when maintaining an existing Fabric consumer that explicitly contains integer `schema: 1`. New projects, recipes and API examples above use quoted `schema: "1.1"` or omit the schema field. The published v0.3.0 decoder deliberately keeps old configurations working: do not remove or change the integer selector without performing a field-by-field migration.

**Complete legacy Fabric API example (historical syntax, not the default for new work):**

```yaml
schema: 1
product: fabric/api
mod:
  id: example_api
  version: "1.2.3"
  group: dev.example.fabric
  archivesName: example-api
  name: Example Fabric API
  environment: "*"
  entrypoints:
    main:
      - dev.example.api.ExampleApiMod
  depends:
    minecraft: ">=26.3"
    fabricloader: ">=0.20.1"
  custom:
    example_api:feature_flags:
      enabled: true
minecraft:
  version: "26.3"
  mappings: mojang
  loader: "0.20.1"
  fabricApi: "0.165.2+26.3"
settings:
  java:
    release: 25
    sourcesJar: true
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
  - maven: "dev.example:public-contract:1.0.0"
    kind: library
    scope: all
    exported: true
test-dependencies:
  - "org.junit.jupiter:junit-jupiter:6.0.0"
```

This legacy example requires real Java source classes and resolvable dependencies; the `dev.example` coordinate is intentionally a placeholder. In schema 1, `mod` owns Fabric Loader metadata such as `environment`, `entrypoints`, `mixins`, `accessWidener`, `icon`, `custom`, `provides` and version-predicate maps `depends`/`recommends`/`suggests`/`conflicts`/`breaks`. `minecraft` owns `version`, `loader`, `mappings`, `fabricApi`; `settings` owns `java`, `kotlin`, `loom`, `resources`. A legacy Kotlin adapter uses `mod.entrypoints` and `settings.kotlin.fabricLanguageKotlin`. A legacy client split uses `settings.loom.splitEnvironmentSourceSets: true`, plus client entrypoints and any `dependencies[].target: client`. For older remap profiles the legacy `mod.accessWidener` references a valid `.accesswidener` rather than the modern `.classtweaker`.

**Optional legacy `mod:` resource fields** (partial fragment, not a standalone YAML document; create every referenced file before validation):

```yaml
mixins:
  - example.mixins.json
accessWidener: example.classtweaker
icon: assets/example_api/icon.png
```

**Repository and dependency shape:** both v1 and v1.1 use **root arrays** for `repositories`, `dependencies`, and `test-dependencies`. Version 1 supports `fabric`, `mavenCentral`, `modrinth` and HTTPS `{maven: URL}` repository items, and fixed Maven/Modrinth dependencies as a string, scoped shorthand or object with `kind`/`scope`/`target`/`exported`/`include`. Version 1.1 additionally allows namespaced loader dependency and repository lists (`fabric.gradleDependencies`, `neoforge.gradleDependencies` and matching `.repositories`); these cannot be pasted into a `schema: 1` document. Loader runtime requirements, Gradle artifact dependencies and release-site relations are different concepts in both versions.

**Older API-only remote Maven configuration** (merge this alternative block into an existing `schema: 1`, `product: fabric/api` document only when needed):

```yaml
publishing:
  repository:
    url: https://maven.example.org/releases
    usernameEnv: FABRIC_MAVEN_USER
    passwordEnv: FABRIC_MAVEN_PASSWORD
```

Both environment variable names must be supplied together or both omitted. This older API-only configuration cannot coexist with `publish.maven`, and it cannot publish a `fabric/mod`. A local publication or authenticated remote upload must still be deliberately requested and verified separately.

**Fabric-only BAT `init` is also legacy by design in v0.3.0.** With no loader flags **or** with `--mod-fabric` alone, it generates explicit `schema: 1` with the old Fabric field locations. These commands intentionally create legacy YAML, even when `--kotlin`, `--architectury`, `--splitEnvironmentSourceSets`, `api` or `--here` is included:

```bat
fabric-amper.bat init example_mod
fabric-amper.bat init example_mod --mod-fabric
fabric-amper.bat init test_mode "Test Mode" mod --kotlin
fabric-amper.bat init strict_mode "Strict Mode" mod --splitEnvironmentSourceSets
fabric-amper.bat init architectury_single "Architectury Single" mod --architectury
fabric-amper.bat init night_vision --here
fabric-amper.bat init example_api "C:\work\example_api" api
```

The hand-authored [schema 1.1 Fabric starter](#complete-starter-files-java-only-minecraft-263) and [schema 1.1 API/library profile](#complete-apilibrary-profile-for-maven-consumers) above are the copyable defaults for new 1.1 work. **There is no BAT Fabric-only 1.1 init switch in v0.3.0.** BAT `init` selects 1.1 when NeoForge is selected, either NeoForge-only or dual. If upgrading a Fabric-only BAT-generated consumer to 1.1, keep real code and deliberately move Loader-specific keys before changing the version selector; validate the result. Do not claim BAT emitted 1.1 because the installed decoder defaults *omitted* schema to 1.1.

## Explicit publishing: configuration vs authorization

Publishing configuration is inert during ordinary `help` and `build`. **The presence of `publish` in YAML is not authorization to upload**, and testing upload tasks may create publicly visible versions. Never run `gradle publish`, `gradle publishFabricAmper`, `gradle publishFabricAmperMaven`, `gradle publishFabricAmperModrinth`, `gradle publishFabricAmperCurseForge`, or an explicit remote Maven task merely to validate YAML. Use isolated, non-upload checks first.

### Route: common Modrinth + CurseForge release configuration

If the user wants to avoid duplicating release metadata between **both platform destinations**, put shared values under `publish.common`, then write each platform's **real** `projectId` and `tokenEnv` under `publish.modrinth` / `publish.curseforge`. Do not move Maven-specific credentials or platform IDs into `common`. The following is a **FRAGMENT** to merge under the existing single root `publish`:

```yaml
publish:
  common:
    releaseType: release
    name: Example Mod 1.0.0
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

Do **not** infer that the sample IDs or compatibility tags are real. `publish.common` is release metadata and **not** the multi-loader Architectury `:common` module: it may appear when one or both release targets are configured, but **does not create a destination by itself**; `publish.common` with only `maven` fails FA003. Published v0.3.0 defaults omitted `schema` to 1.1; explicit `schema: "1.1"` also selects 1.1. Never allow unquoted float `schema: 1.1`.

| Authored shared field | Effective Modrinth field | Effective CurseForge field |
| --- | --- | --- |
| `releaseType` | `releaseType` | `releaseType` |
| `name` | `versionName` | `displayName` |
| `gameVersions` (string list) | `gameVersions` | `gameVersionNames` string **tags**, never numeric `gameVersions` |
| `changelog` or `changelogFile` | Same notes | Same notes |
| `dependencies[]` | `dependencies[{projectId,type}]` | `relations[{slug,projectId?,type}]` |

Each common dependency has a **required** `slug` and `type` (`required`/`optional`/`incompatible`/`embedded`), plus optional `modrinthId` and `curseforgeId`. The Modrinth project ID is `modrinthId ?: slug`. The CurseForge relation uses `slug` and `curseforgeId` (positive **numeric string**, not an integer). Translate type:

| Shared type | Modrinth | CurseForge |
| --- | --- | --- |
| `required` | `required` | `requiredDependency` |
| `optional` | `optional` | `optionalDependency` |
| `incompatible` | `incompatible` | `incompatible` |
| `embedded` | `embedded` | `embeddedLibrary` |

Platform scalar keys override inherited scalars. A platform `changelog` or `changelogFile` **replaces the inherited notes source as a whole**. A platform `modrinth.dependencies` or `curseforge.relations` **appends new IDs** and **overrides matched inherited IDs** (effective Modrinth `projectId` / CurseForge `slug`); explicit empty `dependencies: []` or `relations: []` **clears** inherited relations. Duplicates within the same authored list are still invalid. `curseforge.gameVersions` (integer author-upload IDs) is strictly platform-specific and is never populated from common. Shared game version strings do **not** automatically add CurseForge `Fabric` / `Client` / `Server` tags; supply explicit `gameVersionNames` when needed.

Keep the user's existing explicit platform values and change only what they requested. Check resolved `model.publish.modrinth` / `model.publish.curseforge` and the **real** metadata JSON builders; verify releases' concrete IDs, tags and environment semantics before any upload. When platform versions/relations may not refer to the same real project, read the full source/Schema and ask for verified IDs instead of guessing.

### Route: remote Maven repository

**FRAGMENT — merge at root (requires a real `mod.group`):**

```yaml
publish:
  maven:
    url: https://maven.example.org/releases
    credentialsFromEnv: [FABRIC_MAVEN_USER, FABRIC_MAVEN_PASSWORD]
```

`credentialsFromEnv` is **exactly two distinct environment variable names** in order: username and password/token. Do **not** use `username`/`password`, literal tokens, `http://`, credentials embedded in a URL, URL query strings, or fragments. For a public anonymous repository, omit `credentialsFromEnv`; do not invent a username. Verify destination repository policy and Maven coordinate uniqueness before the user-authorized `gradle publishFabricAmperMaven`.

An older API-only `publishing.repository` alternative is documented in [Legacy schema 1](#legacy-schema-1-fabric-only-reference). Do not combine the two publication mechanisms. Native `publishMavenJavaPublicationToFabricAmperRemoteRepository` remains a remote operation and must never be fired implicitly.

**Known limitation:** on Gradle 9.7.1, an authenticated native `PublishToMavenRepository` task with `PasswordCredentials` was recorded as incompatible with configuration-cache reuse; `--offline` blocks even a loopback HTTPS PUT; `--dry-run` skips task actions and cannot verify credentials, TLS, or successful upload. Report these as **unverified/unsupported combinations**, not as a green acceptance gate.

### Route: Modrinth release

**FRAGMENT — release-site metadata (IDs and environment variable names shown are placeholders):**

```yaml
publish:
  modrinth:
    projectId: "REPLACE_WITH_REAL_MODRINTH_PROJECT"
    tokenEnv: MODRINTH_TOKEN
    releaseType: beta
    versionName: "Example Mod 1.0.0"
    versionNumber: "1.0.0+26.3"
    gameVersions: ["26.3"]
    loaders: [fabric]
    environment: client_and_server
    changelogFile: release-notes.md
    dependencies:
      - projectId: "REAL_DEPENDENCY_PROJECT"
        type: required
```

The placeholder `projectId` strings are **not actual live projects**: replace them before attempting a release. `releaseType` is `release`, `beta`, or `alpha`; `versionNumber` defaults to `mod.version`; `gameVersions` defaults to `[minecraft.version]`; `loaders` defaults to `[fabric]`. Explicit `environment` can be any one of these nine exact enum values:

```text
client_and_server
client_only
client_only_server_optional
singleplayer_only
server_only
server_only_client_optional
dedicated_server_only
client_or_server
client_or_server_prefers_both
```

When absent, environment is inferred from the Loader mod's `*`/`client`/`server` side as `client_and_server`/`client_only`/`server_only`. That inference cannot know whether optional client or server installation is desirable. Set the correct platform semantics explicitly when the real distribution differs.

`publish.modrinth.dependencies[]` requires `projectId` and `type`, where `type` is `required`, `optional`, `incompatible`, or `embedded`. Do not derive a release-site project ID from `fabric.depends` or Maven artifact coordinates. Provide **either** `changelog` inline **or** `changelogFile` (safe relative path), not both. Source `release-notes.md` actually must exist inside the consumer root.

### Route: CurseForge release

**FRAGMENT — existing CurseForge author project only (fictional ID):**

```yaml
publish:
  curseforge:
    projectId: 123456
    tokenEnv: CURSEFORGE_TOKEN
    releaseType: beta
    displayName: "Example Mod 1.0.0"
    gameVersionNames: ["26.3", "Fabric", "Client", "Server"]
    changelog: "Initial release"
    changelogType: markdown
    manualRelease: true
    relations:
      - slug: real-required-project-slug
        type: requiredDependency
```

`projectId` is a positive 32-bit **integer** of the existing CurseForge project, not a quoted string or Modrinth ID. If specified, `gameVersions` is a list of **positive integer upload-game-version IDs**, distinct from string `gameVersionNames` such as `"26.3"` and `Fabric`. Do not invent numeric IDs or assume a tag exists on the platform. `releaseType`: `release` / `beta` / `alpha`; `changelogType`: `text` / `markdown` / `html`; `manualRelease` is boolean. A relation requires a real `slug` and `type` from `embeddedLibrary`, `incompatible`, `optionalDependency`, `requiredDependency`, or `tool`; optional `projectId` inside a **relation** is a *numeric string*, unlike the main numeric project ID.

Do not derive CurseForge relations from Modrinth IDs, Fabric Loader mod IDs, or Maven coordinates. For `changelogFile`, apply the same no-escape rule; inline `changelog` and `changelogFile` are mutually exclusive.

### Publication task and side-effect matrix

| Command | What it can change | Permitted agent behavior |
| --- | --- | --- |
| `gradle help`, `gradle tasks --all` | Only local Gradle configuration state | Standard safe validation |
| `gradle build` / `gradle test` | Local build/test outputs | Normal non-upload verification |
| `gradle publishMavenJavaPublicationToFabricAmperLocalRepository` | Consumer's `build/fabric-amper/maven-repository` | Only for deliberate local API publication |
| `gradle publishToMavenLocal` | User-global `~/.m2` | Avoid by default; requires deliberate intent |
| `gradle publishFabricAmperMaven` | Explicit remote Maven destination | Requires explicit release instruction and credentials |
| `gradle publishFabricAmperModrinth` | Modrinth author project release | Requires explicit instruction, real project, token and review |
| `gradle publishFabricAmperCurseForge` | CurseForge author project release | Same; author upload API, not general read API |
| `gradle publishFabricAmper` | All configured new `publish` remote destinations | Avoid unless user intentionally requests all; partial success possible |
| `gradle publish` | Gradle standard Maven publication plus integrated selected platform tasks | **Not** side-effect equivalent to `publishFabricAmper`; especially beware API local publication |

**Release preflight (do not skip):**

1. Review the user's approval for **destination and version**. If upload is not explicitly requested, stop at non-upload validation.
2. Ensure the actual remote project exists, that the account has publish permissions, and that versions/tags/relations are verified for that platform. Check that the target release/version does not already exist.
3. Confirm fixed Minecraft/Loader/API versions, genuine game compatibility claims, Loader metadata, no forbidden duplicated resources, correct production JAR (non-remap `jar` or legacy `remapJar`).
4. Use real operating-system environment variables for secrets. Never print raw token values or capture the request body. Do not save tokens to checked-in `mod.yaml`, Gradle files, POM, logs, QA reports, or tool traces.
5. Start with the **single explicitly requested destination**. Do not use an all-target command for convenience; multi-platform partial success is not transactional and upload POSTs are not automatically idempotently retried.
6. Record actual HTTP/task outcome. Do not label skipped `--dry-run` actions, static metadata unit tests, or a missing-credentials negative test as a successful remote upload.

## Validation and repair loop

### A. YAML/schema static verification

Read `fabric-amper.schema.json` and the strict decoder. A YAML language server may be pointed at the schema using a comment, not an author data field:

```yaml
# yaml-language-server: $schema=./fabric-amper.schema.json
schema: "1.1"
```

The two lines above are a **FRAGMENT**, not a complete document. The schema file must actually be reachable from the consumer editor (copy or reference the repository's authoritative schema; do not assume it appears automatically in unrelated consumers).

Schema checks are useful but **not sufficient**: strict YAML parsing rejects duplicate keys/aliases/tags, semantic validation checks Modrinth/CurseForge fields and side safety, and the build verifies files and dependencies. Do not accept arbitrary YAML parsers' silent coercion of `26.3` into a number; quote game and version strings.

### B. Gradle safe phases

```powershell
gradle help
gradle tasks --all
gradle validateFabricAmper
gradle build
gradle test
gradle verifyFabricAmperJar
```

Task availability may depend on product profile and selected integration; confirm `tasks --all`. Start with `help` because Fabric Amper's Loom metadata mirror must already exist during configuration. If changing an icon, mixin JSON, Class Tweaker, or YAML file, re-run the validation rather than relying on a stale configuration-cache entry.

### C. Inspect production JAR and metadata

- Determine the production archive from `mod.archivesName`/`mod.id` and `mod.version`; do not mistake `-sources.jar`, `-dev.jar`, or `-javadoc.jar` for the release.
- For a **Fabric target**, confirm the archive contains **one and only one** root `fabric.mod.json`, no duplicate ZIP entry and no NeoForge TOML; for a **NeoForge target**, confirm `META-INF/neoforge.mods.toml` and no Fabric JSON.
- Parse the relevant Loader metadata; verify `id`, `version`, entrypoint classes, Mixins, Class Tweaker/access transformer paths, dependency predicates, and nested jars where supported.
- Check included referenced resource files and the expected entrypoint classes. For nested JARs, inspect actual embedded JAR bytes/Loader metadata — a declared `include: true` alone is not runtime proof.
- Check the generated build metadata and the content-addressed Loom mirror are consistent; do not edit the mirror manually.
- Where acceptance requires determinism, build twice from an unchanged consumer and compare artifact hashes. A one-time successful build does not establish byte-for-byte repeatability.

### D. Runtime/IDE checks must be labeled accurately

For `runClient` or `runServer`, isolate the game/run directory, inspect startup logs and actual Loader mod recognition. On servers, **never auto-edit `eula.txt`**. A log that reaches EULA refusal only proves a *partial boot*, not a started dedicated server. Real IntelliJ first import and manual/automatic sync require observed **GUI** results; a Gradle Tooling API model check cannot substitute. Legacy Minecraft 1.21.11 remap build tests are not evidence of full legacy client/server runtime.

### E. Repair steps after a failure

1. Capture the earliest meaningful `FA###` code, its YAML path and file location, or the first Gradle cause. Remove or redact secrets.
2. Classify: *syntax* → *schema/semantic rule* → *source/resource existence* → *artifact/repository resolution* → *classpath/plugin clash* → *Gradle/environment* → *runtime*.
3. Change only the failing field/resource/repository as justified; preserve user requirements. Re-evaluate when one correction changes a profile or scope.
4. Repeat `gradle help`, the smallest relevant task, `build`, and the reported artifact check. Record tests genuinely run and those not run.
5. Do not repeat remote publication or authenticated HTTP requests as a diagnostic loop. If an upload partially succeeded, inspect the remote release state with the user's permission before any new POST.

## FA001–FA015 diagnostic playbook

| Code | Identify | Correct without overreaching |
| --- | --- | --- |
| **FA001** | Missing or simultaneously present `mod.yaml` and `mod.yml` | Use exactly one root YAML; do not overwrite either without inspecting it |
| **FA002** | Strict YAML parse failure: duplicates, anchors, aliases, tags, multiple documents, invalid UTF-8 | Replace with ordinary UTF-8 mappings/sequences and unique keys; preserve intent |
| **FA003** | Unknown field, wrong type, missing required value, bad version, unsafe publish URL/credentials/path | Look up exact decoder/schema path; never guess a replacement key |
| **FA004** | Yarn with Minecraft 26.1+ | Select `mojang` for non-remapping 26.1+ |
| **FA005** | Invalid Java release for modern Minecraft | Set Java release 25 for MC26.3 and run a compatible JDK |
| **FA006** | Kotlin plugin/language/FLK version or adapter mismatch | Use pinned versions and true opt-in, no custom Kotlin adapter redeclaration |
| **FA007** | `include` or `exported` with invalid scope | Use `scope: all` or remove that flag after checking semantic need |
| **FA008** | An authored `fabric.mod.json` exists in a source resources root | Migrate metadata into YAML and delete *only after preserving authored data* |
| **FA009** | Bad mod ID, entrypoint, icon, Mixin or Class Tweaker resource/header | Fix the exact named metadata or source file, not generated copies |
| **FA010** | Loom/KGP classpath/plugin conflict | Retain only Settings plugin entry; remove manually applied incompatible plugins |
| **FA011** | Genuine missing Maven/Modrinth artifact | Check coordinate/repository availability; do not mislabel TLS/auth/offline failures as missing artifacts |
| **FA012** | Duplicate/conflicting dependency coordinate | Consolidate coordinate and intended scopes; do not drop public API requirements |
| **FA013** | Final JAR's root `fabric.mod.json` absent/duplicated | Inspect archive/resources; use plugin's generated metadata and JAR verifier |
| **FA014** | Client-targeted dependency or illegal client export/include | Enable split source sets, use correct client source tree/scope, never export/include it |
| **FA015** | Missing/inconsistent Loom metadata/resource mirror | Allow deterministic regeneration; investigate repeated damage or cache races |

**FA codes are not universal.** A TLS handshake problem, Gradle repository rate limit, unavailable game download, source compile error, Mixin runtime exception, platform HTTP 401, IDE sync failure, or Mojang EULA stop may be a *different* diagnostic. Report its actual cause without fabricating an FA code.

## Common wrong turns (negative examples)

These are deliberately **invalid or prohibited**; do not copy them into production:

```yaml
# WRONG — not a whole document; no key named minecraft.loomVersion.
minecraft:
  loomVersion: "1.18.4"

# WRONG — no fabric.classTweaker; the implemented key is fabric.accessWidener.
fabric:
  classTweaker: example.classtweaker

# WRONG — source and Loader dependency metadata keys are not interchangeable.
mod:
  dependencies:
    fabricloader: ">=0.20.1"

# WRONG — secrets are not allowed as literal publish.maven.username/password.
publish:
  maven:
    url: http://user:password@maven.example.org/releases
    username: user
    password: password
```

Also forbidden: root `$schema:` (a comment annotation is acceptable), root `build:`/`plugins:`/`loomVersion:`, a second YAML document separated by `---`, a Gradle `build.gradle.kts` added "just for one dependency", file paths like `../outside/icon.png`, dynamic `latest.release` or `1.+` dependency versions, `fabric.resources.failOnExistingFabricModJson: false`, `exported/include` on a client dependency, or `fabric.mappings: yarn` for MC26.3.

## Acceptance examples for agent self-review

Use these as **requirements-based scenarios**, not automatic claims of a PASS:

| Scenario | Required edit | Evidence needed |
| --- | --- | --- |
| Java-only modern mod | Baseline YAML + real `ExampleMod.java` | `help` and `build`; one generated JSON in JAR |
| Mod rename but same Loader ID | Only `mod.name` or `mod.archivesName` as requested | Correct name in metadata / archive; same `mod.id` |
| Public API library | `product: fabric/api`, real group, `exported` on public library dependency | Local Maven publish plus separate downstream compile/runtime |
| Kotlin entrypoint | Enabled pinned Kotlin and actual source | Kotlin task/compile and Loader adapter integrity |
| Side-safe client feature | `splitEnvironmentSourceSets` and `target: client` as appropriate | Actual client build/run; no accidental server classloading |
| Embedded helper | `scope: all`, `include: true` | Nested JAR and Loader metadata in archive; runtime where required |
| Modern Class Tweaker | `fabric.accessWidener` + real modern header | Resource validation and archive check |
| Duplicate YAML key | No ambiguous silent override | Expected **FA002** negative test |
| Unknown YAML field | No ignored field | Expected **FA003** negative test |
| Existing authored Loader JSON | Never silently replace it | Expected **FA008** negative test |
| Maven remote credential absence | YAML contains environment **names** only | Negative check, not a real remote upload PASS |
| Modrinth/CurseForge release planning | Real existing IDs, release fields, token env names | `help`/`build` and metadata review; no upload without authorization |
| Configuration-cache and mirror recovery | Repeated safe tasks, YAML/resource change and damaged mirror recovery | Explicit cache MISS/HIT and repaired metadata; not just one clean run |
| IDE import/runtime/server/EULA | Separate real environment checks | Evidence from GUI/logs; never infer from static syntax |

## Final reporting checklist

Before reporting completion, confirm:

- [ ] Consumer has `settings.gradle.kts` and exactly one `mod.yaml` or `mod.yml`, **no** consumer build script or authored Loader JSON.
- [ ] YAML uses keys matching **effective schema** (omitted or quoted 1.1 vs explicitly integer 1), fixed versions, correct target scalar/array and matching game/Java/mappings profile.
- [ ] Referenced code, resources, repository coordinates, and platform identifiers genuinely exist or are explicitly marked placeholders.
- [ ] No secrets in files/output; no unauthorized publishing, `~/.m2` writes, world changes, EULA changes, or unintended network uploads.
- [ ] Actual commands, their exit status, test counts, JAR path, and root metadata check are recorded when executed.
- [ ] Any missing data, network failure, unrun runtime/IDE gate, and skipped test is called out honestly.
- [ ] The final message states the exact files changed and whether the task is *documentation/configuration only*, *build verified*, *runtime verified*, or *released*.

## Known acceptance boundaries

The historical Gradle 9.7.1 / JDK 25 baseline in `QA_REPORT.md` was 46 suites / 247 tests (207 PASS, 40 SKIP). Earlier develop-worktree verification had **364 tests = 307 PASS, 57 SKIP**. The **v0.3.0 source candidate** passed `clean build validatePlugins --offline` with **371 tests = 314 PASS, 57 SKIP, 0 fail/error**, including new schema-default and Windows command dispatch regressions. Prior actual schema 1.1 NeoForge-only, dual independent/Architectury, Kotlin, hybrid API/mod and Fabric-only nested JIJ TestKit builds passed. The **public v0.3.0 GitHub prerelease** had nine SHA-256-verified assets, JitPack status `ok`, and a fresh NeoForge+Architectury+Kotlin remote-plugin consumer build PASS. **Cold-cache caveat:** initial `:common` JAR creation caused one-time *second* configuration-cache MISS; identical third run HIT and product-order stable JARs passed, so strict immediate cold second HIT is **NOT MET**. These results do **not** prove actual Minecraft client/server loading, IntelliJ GUI sync, authenticated remote publication, or all G1–G30/NF1–NF26 gates. Treat the published prerelease as a developer preview, not a full shipping approval. Read current `QA_REPORT.md` for exact evidence.

## Windows command wrapper: fabric-amper.bat

The producer root contains [`fabric-amper.bat`](fabric-amper.bat), a **Windows CMD convenience wrapper** for existing Gradle tasks. It is not a YAML key, not part of the consumer's plugin configuration, and does not create a new publicly distributed plugin. If the user asks for Windows commands and the exact requested action is covered here, use the wrapper; for an unsupported or ambiguous operation read the actual batch implementation and [the compact skill](FabricAmperSKILL_SYN.md) or source first. **Never assume that calling the wrapper makes publishing or real game execution safe.**

**Distinguish producer commands from consumer commands:**

```bat
rem Run inside the Fabric Amper producer directory.
fabric-amper.bat doctor
rem The following is only for an explicitly requested toolchain/file repair:
fabric-amper.bat doctor --fix --dry-run
rem Fabric-only init outputs schema 1; see the dedicated legacy section.
rem These NeoForge or dual-loader init commands output schema 1.1:
fabric-amper.bat init neo_mod "NeoForge Mod" mod --mod-neoforge
fabric-amper.bat init neo_kotlin "NeoForge Kotlin" mod --mod-neoforge --kotlin
fabric-amper.bat init neo_arch "NeoForge Architectury" mod --mod-neoforge --architectury
fabric-amper.bat init dual_mod "Dual Mod" mod --mod-fabric --mod-neoforge
fabric-amper.bat init dual_shared "Dual Architectury" mod --mod-fabric --mod-neoforge --architectury --kotlin
rem Create within the current tools-only folder without replacing existing project files:
fabric-amper.bat init night_vision --here --mod-neoforge
rem For a 1.1 Fabric-only mod/API, author the complete YAML profile above.

rem Convert an existing Fabric Gradle mod; review first, preserve originals by default:
fabric-amper.bat migrate -C "C:\work\legacy_mod" --dry-run
fabric-amper.bat migrate -C "C:\work\legacy_mod"

rem Call from any other directory; provide full script path and consumer root.
"C:\path\to\Fabric Amper\fabric-amper.bat" -C "C:\work\example_mod" build
"C:\path\to\Fabric Amper\fabric-amper.bat" -C "C:\work\dual_mod" run --mod-fabric
"C:\path\to\Fabric Amper\fabric-amper.bat" -C "C:\work\dual_mod" server --mod-neoforge
```

`init <id> [new_folder] [mod|api] [--mod-fabric] [--mod-neoforge] [--architectury] [--kotlin] [--splitEnvironmentSourceSets]` creates a new target folder with `settings.gradle.kts`, one `mod.yaml` and actual platform source files; `--here` is supported in an otherwise tools-only folder. **No loader flag or `--mod-fabric` alone selects Fabric-only and emits explicit legacy `schema: 1`. Selecting NeoForge (`--mod-neoforge`, alone or with `--mod-fabric`) emits schema 1.1.** A single loader writes a scalar `product`; dual writes an array. Without `--architectury`, dual creates **`fabric/` and `neoforge/` only (no `common/`)**; with it, generates **`common/`, `fabric/`, `neoforge/`**. Java is the default, `--kotlin` generates actual Kotlin sources and Gradle settings. NeoForge uses a real `@Mod` initializer, Fabric uses `ModInitializer`; do not invent a common Fabric class for NeoForge. Generated schema 1.1 YAML uses loader-specific `fabric:`/`neoforge:` blocks. **The published v0.3.0 decoder interprets missing `schema` as 1.1 (regression-tested); BAT Fabric-only init explicitly writes `schema: 1`.** Never add a consumer `build.gradle(.kts)` or hand-authored Loader metadata.

**Safe asset/bootstrap conditions:** when the BAT-side schema is missing, `init` downloads the SHA-256 pinned schema from the configured public source and rejects mismatches, unsafe pre-existing paths or overwrites. Verify the exact BAT pin/source rather than trusting an unverified local copy; the public v0.3.0 release asset hashes were checked independently on 2026-11-17, but v0.3.1 requires its own check. `--here` rejects existing YAML, Settings, build script, `src` or conflicting schema path. Generated YAML's `# yaml-language-server: $schema=./schemas/mod.schema.json` resolves relative to the new consumer YAML. v0.3.1 target generated Settings **use** JitPack `com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.3.1`; check new public availability before use. The API profile uses a placeholder `org.example` group to replace.

**Split source sets at creation:** `--splitEnvironmentSourceSets` creates Fabric `src/main` + `src/client` Java/Kotlin source/resource roots with the actual `ClientModInitializer`. With **independent dual** (`--mod-fabric --mod-neoforge`) it writes schema 1.1 `fabric.loom.splitEnvironmentSourceSets: true` / `fabric.entrypoints.client` for the Fabric subproject. Fabric-only init emits the explicit version 1 paths documented in the [legacy section](#legacy-schema-1-fabric-only-reference). The client initializer must not load on a dedicated server. For **dual with Architectury**, the BAT accepts and explicitly **ignores** split, leaving shared `:common` layout and no Fabric client split. For **NeoForge-only**, split is rejected rather than silently ignored. Without a split flag, no Fabric client source or client entrypoint is generated. Do not set the whole-mod environment to client merely because Fabric split was selected.

**Architectury starter (published v0.3.0 prerelease):** `--architectury` writes `architectury.enable: true` and may use the pinned default API version for Minecraft 26.3. In **single-loader Fabric**, it retains the Fabric Architectury API example with optional client split. In **single-loader NeoForge**, it generates a platform-specific Java/Kotlin `@Mod` example using NeoForge Architectury API; real BAT TestKit Java and Kotlin consumer builds passed. In **dual-loader**, Architectury **creates the genuine `:common` module** and both loader modules; its split flag is accepted but **ignored with a clear message**. With `--architectury` **absent**, dual-loader has no `:common` at all. Real TestKit verified dual Architectury Java/Kotlin/hybrid build configurations, and a separate published JitPack v0.3.0 NeoForge+Architectury+Kotlin consumer build passed; **actual Minecraft runtime and IntelliJ GUI remain unverified**.

### Convert a legacy Fabric Gradle mod with `migrate` (v0.3.1 target; legacy input)

Use `migrate` when the user has an **existing** Fabric mod with exactly one `build.gradle.kts` **or** Groovy `build.gradle`, and exactly one supported `src/main/resources/fabric.mod.json` (or `src/client/resources/fabric.mod.json`). This differs from `init`, which starts a new project, and `port`, which only prints a manual Minecraft version checklist. The helper is **`scripts/fabric-amper-migrate.ps1`**. If absent, a v0.3.1 BAT migration requests the **version-matched v0.3.1 GitHub Release asset**, verifies its pinned SHA-256 (and the schema if needed), and fails safely if fetch or checksums fail. The earlier v0.3.0 helper assets were published and independently hash-verified on 2026-11-17; do not assume the new v0.3.1 assets already exist. Both `migrate --dry-run` and `doctor --fix --dry-run` never download or write; missing helper/schema only previews an attempted download. Existing wrong-hash tools are rejected without overwrite or execution. The converter statically interprets a limited Gradle DSL subset and **never evaluates the old build script**. It generates `mod.yaml` and `settings.gradle.kts` referencing the target **v0.3.1 JitPack** module; conversion alone does **not** prove the converted mod compiles or runs.

```bat
rem Run these from the legacy project directory with the BAT on PATH or by full path:
fabric-amper.bat migrate --dry-run
fabric-amper.bat migrate

rem Run from elsewhere (a single project selector, before or after migrate):
"C:\path\to\Fabric Amper\fabric-amper.bat" migrate -C "C:\work\legacy_mod" --dry-run
"C:\path\to\Fabric Amper\fabric-amper.bat" -C "C:\work\legacy_mod" migrate
"C:\path\to\Fabric Amper\fabric-amper.bat" migrate --project "C:\work\legacy_mod"

rem Only if the user explicitly requests removal of the originals:
"C:\path\to\Fabric Amper\fabric-amper.bat" migrate -C "C:\work\legacy_mod" -D
"C:\path\to\Fabric Amper\fabric-amper.bat" migrate -C "C:\work\legacy_mod" --delete
rem Preview deletion semantics with no writes:
"C:\path\to\Fabric Amper\fabric-amper.bat" migrate -C "C:\work\legacy_mod" --delete --dry-run
```

**File effects:** `--dry-run` validates the supported inputs and prints proposed YAML **only if the signed-off local helper and schema are already available**; otherwise it previews the first missing download and stops, with zero writes/downloads. The default actual `migrate` may first download pinned tools into `scripts/` and the schema into the BAT directory; then it writes `mod.yaml` and new `settings.gradle.kts`, preserving the original `build.gradle[.kts]`, any prior `settings.gradle[.kts]`, and original source `fabric.mod.json` in a new `gradle_backup/` directory. `-D` is synonymous with `--delete`; it instead removes **only these original configuration/metadata files** after staged conversion. It does not delete Java/Kotlin sources, other resources, or `gradle.properties`. The helper tries to roll back moved files if conversion cannot commit; a rollback failure requires manual recovery, with the staging path reported. Never delete `gradle_backup/` or select `-D` automatically merely to make a clean build possible.

**Preconditions and limits:** Reject an existing `mod.yaml`/`mod.yml`, pre-existing `gradle_backup/`, zero or two build scripts, two Settings files, missing or multiple source `fabric.mod.json`, unsupported resource locations, and symlinks/junctions in input paths. Only Fabric Loader `schemaVersion: 1`, statically resolvable fixed Minecraft/Loader/API/Kotlin/mapping versions, supported `fabric-loom` flavor and known declarative Gradle blocks can be converted safely. Unknown plugins, custom Gradle tasks, dynamic properties/interpolation, arbitrary script logic, multi-project setups, nonstandard repositories, or metadata that cannot be translated without loss **fail for manual migration**; the helper does not guess an equivalent YAML field. The generated product is `fabric/mod`; API/library publishing and external publishing targets must be reviewed and configured separately when truly requested. No `fabric.mod.json` is authored after conversion—the Amper plugin generates it for the JAR.

**After migration:** Inspect whether the generated YAML is **explicit legacy `schema: 1`** or correctly namespaced 1.1 (never mix both); verify dependencies, client split, Kotlin entrypoints and mappings. The **public v0.3.0 JitPack artifact was successfully resolved in a new consumer**; still check connectivity and build the particular migrated mod before promising functionality. On Windows, apply the documented doctor/repair policy only if a required toolchain is missing. Then run `fabric-amper.bat -C "C:\work\legacy_mod" validate` and `build`, inspect the produced JAR for a single root `fabric.mod.json`, and perform separate game client/server or IDE tests when required. Failed validation never justifies deleting sources or changing third-party build logic silently.

| User intent | Wrapper command | Actual effect |
| --- | --- | --- |
| Inspect usage and diagnostics | `help`, `version`, `docs`, `doctor`, `doctor --full`, `doctor --paths` | Local toolchain and optional path/JDK checks; no global cache deletion |
| Repair missing Windows toolchain | `doctor --fix` **automatically after `doctor` detects a missing required tool** | Preview first; without extra consent prompt, download exactly the versioned GitHub PS1 if missing, save in `scripts/` below BAT after pinned SHA-256, then try JDK25 via winget/Gradle via winget or official hash-verified ZIP and optional missing consumer files; recheck with `doctor` |
| Preview repair without changing anything | `doctor --fix --dry-run` | No download, installation or file/registry mutation; without a local PS1 **only the proposed download** is shown |
| Preview existing Fabric Gradle project conversion | `migrate --dry-run` or `migrate -C "C:\work\legacy_mod" --dry-run` | With verified local tools, parse and print YAML; if helper/schema is missing, only preview its download; never write or download |
| Convert an existing Fabric Gradle project | `migrate` or `-C "C:\work\legacy_mod" migrate` | Download/hash-check missing `scripts/` helper and schema, then write YAML/Settings and back up original build, settings and source `fabric.mod.json` in `gradle_backup/` |
| Convert and discard original build configuration | `migrate -D` or `migrate --delete` | Explicitly remove those original files only after staging and conversion; does not delete Java/Kotlin source or unrelated resources |
| Initialize in current tools-only folder | `init <id> --here [mod\|api] [--mod-fabric] [--mod-neoforge] [--architectury] [--kotlin] [--splitEnvironmentSourceSets]` | Preserve existing tools; hard fail on existing consumer files |
| Generate Fabric, NeoForge or dual mod/API | `init <id> [folder] [mod|api] [--mod-fabric] [--mod-neoforge] [--architectury] [--kotlin] [--splitEnvironmentSourceSets]` | No flags or Fabric flag only = **explicit schema 1**; NeoForge selection = **schema 1.1**; dual Architectury on=shared common, off=no common; Java/Kotlin real sources |
| List/test/validate | `tasks`, `test`, `validate`, `verify` | `tasks --all` / `test` / `validateFabricAmper` / `verifyFabricAmperJar` |
| Diagnose the consumer | `diagnose` / `explain` | `fabricAmperDoctor` toolchain summary / `fabricAmperExplain` independent dependency categories, no network lookups |
| Inspect a prospective release | `plan` | `fabricAmperReleasePlan`; verifies final JAR and prints its checksum + effective release metadata without uploading |
| Compare game profiles | `port 26.3` | `fabricAmperPortingReport -PfabricAmperPortTarget=26.3`; read-only manual migration checklist |
| Package and verify | `build` | `validateFabricAmper build verifyFabricAmperJar` |
| Remove local generated build outputs | `clean` | `gradle clean` for the chosen consumer |
| Start Minecraft client/server | `run` / `server` with optional `--mod-fabric` or `--mod-neoforge` | Dual **requires exactly one selector** and maps to `runFabricClient`/`runNeoForgeClient` or `runFabricServer`/`runNeoForgeServer`; single-loader no flag stays `runClient`/`runServer`. BAT fake-Gradle smoke PASS only; **actual game NOT RUN** |
| Publish API to consumer-local Maven | `publish local` | `publishMavenJavaPublicationToFabricAmperLocalRepository` plus validation/build |
| Publish one remote target | `publish maven` / `modrinth` / `curseforge` | Exact matching `publishFabricAmper...` task only after typed `PUBLISH` confirmation |
| Publish all configured new remote targets | `publish all` | `publishFabricAmper` aggregator, **not** Gradle's broader `publish` |

For normal consumer operations, `-C "C:\path\to\consumer"` or `--project "C:\path\to\consumer"` generally appears **before** the command. `migrate` and `doctor` also accept the project selector **after** their names; `migrate` requires it at most once. For `doctor` compatibility, a single extra `doctor` after a project selector is ignored. Relative project paths use the current working directory; without a selector the wrapper uses that directory as consumer root. Existing consumer commands require one YAML, a settings script, and no `build.gradle(.kts)`/source `fabric.mod.json`; **`migrate` is specifically for the opposite starting state** (one legacy build script and one source `fabric.mod.json`). These preflight checks do not replace Gradle semantic validation. The launcher uses `FABRIC_AMPER_GRADLE`, a consumer `gradlew.bat`, `GRADLE_HOME`, `PATH` or a known cached compatible Gradle version. Run read-only `doctor` to diagnose; after it detects a missing required tool, preview and automatically run `doctor --fix` as described above, without a separate consent prompt. A child BAT/PowerShell process cannot change its parent's live environment: the fixer saves user settings and updates its own process only, and advises opening a new terminal if necessary.

**Remote publication protocol:** an explicit `publish maven|modrinth|curseforge|all` command shows the selected destination, asks the user to type exact `PUBLISH`, runs `validateFabricAmper build verifyFabricAmperJar`, then invokes the target task if validation succeeds. This confirmation is a second safeguard, **not** permission for an AI agent to initiate release tasks without the user's own explicit intent. Do not use `publish all` as a replacement for testing YAML or assume multi-platform uploads are transactional. `publish local` has no remote confirmation because it writes into the consumer's build directory; it still must be intentional. See [the human Windows quick guide](HUMAN_QuickGuide.md) and the detailed publishing section above.

### Route: read-only diagnostics, dependency explanation and release preview

When the user requests **an explanation, not a configuration change**, prefer these explicit tasks in the consumer directory:

```powershell
gradle fabricAmperDoctor
gradle fabricAmperExplain
gradle fabricAmperReleasePlan
gradle fabricAmperPortingReport -PfabricAmperPortTarget=26.3
```

**Decision logic:** For toolchain, mappings, Java or Windows-path uncertainty, invoke `fabricAmperDoctor` (or Windows wrapper `doctor --full` for local launcher/path hints). For confusion between Gradle artifact coordinates, Fabric Loader required-mod predicates and Modrinth/CurseForge release relationships, invoke `fabricAmperExplain`—**do not conflate the three**. When asked what would be released, invoke `fabricAmperReleasePlan`, which may build/verify the final JAR, but **does not upload or verify remote project IDs**. When asked about changing game versions, invoke `fabricAmperPortingReport` with a verified target; it produces a manual checklist and does **not** migrate Java/Kotlin source or Mixins. Unknown or unsupported targets are not silently accepted.

Never use these reports as substitutes for actual IntelliJ GUI import, client/server startup and ticks, GameTest, version compatibility proof, or authenticated remote publication. No new `mod.yaml` keys are required. If a report cannot answer a specific question, inspect [the human manual](HUMAN.md#read-only-developer-diagnostics-and-release-preview) and current source, then explicitly disclose the unknown instead of guessing.

## Packaging note

The name `FabricAmperSKILL.md` is **user-required** and kept exactly. It is an AI-oriented reference file that an agent can open explicitly. Under the [Agent Skills specification](https://agentskills.io/specification), auto-discovery generally expects `fabric-amper/SKILL.md` with frontmatter such as:

```yaml
---
name: fabric-amper
description: Use when creating, editing, validating, building, or explicitly publishing a Fabric Amper consumer configured by settings.gradle.kts and mod.yaml/mod.yml without a consumer build.gradle.kts or authored fabric.mod.json.
---
```

That is a **manifest illustration**, not a Fabric Amper `mod.yaml` document; equivalent frontmatter is already present at the top of this custom file. **Frontmatter alone does not cause automatic discovery.** No additional auto-discovery package is created here. If a user later requests packaging, place a concise version in a standard `SKILL.md` inside an appropriately named `fabric-amper` skill directory and provide only the relevant workflow/reference files. Never claim this custom root filename is automatically installed as a skill.

This guide draws its instruction design from the [Agent Skills specification](https://agentskills.io/specification), [Anthropic Agent Skills authoring guidance](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices), and [OpenAI skill-creator examples](https://github.com/openai/skills/blob/main/skills/.system/skill-creator/SKILL.md). For the actual project configuration, use the [human manual](HUMAN.md), [source README](README.md), [schema](fabric-amper.schema.json), [original acceptance spec](specs.md), and [QA evidence](QA_REPORT.md).
