---
name: fabric-amper
description: Use when creating, editing, validating, building, testing, or explicitly publishing a Fabric Amper consumer configured with settings.gradle.kts and mod.yaml/mod.yml, without a consumer build.gradle.kts or authored fabric.mod.json.
---

# Fabric Amper — AI Configuration and Operations Guide

> **Custom AI-facing guide, not an auto-discovered Agent Skill.** The requested filename is `FabricAmperSKILL.md`. Agent Skills tools conventionally discover a `<skill-directory>/SKILL.md` file with `name` and `description` frontmatter. This file is an explicitly referenced project guide; its heading below is not a claim that an agent will discover it automatically. See [Packaging note](#packaging-note) at the end.

## Mission and activation

Read this guide **when the user wants to create, change, debug, build, test, package, or explicitly publish a Fabric Amper consumer project**. Convert a precise user goal into the minimum valid `mod.yaml` or `mod.yml` edits, real source files, a safe Gradle command, and a verifiable result. Always distinguish documented syntax from a feature that has actually passed acceptance testing.

**Hard requirement:** a consumer has only `settings.gradle.kts` and exactly one `mod.yaml` or `mod.yml` as *author-maintained build configuration*. It still contains ordinary Java/Kotlin source files, resource files, and tests. Do **not** create a consumer `build.gradle`, `build.gradle.kts`, or manually authored `fabric.mod.json`. The developer applies only `dev.fabric-amper.settings`; the internal `dev.fabric-amper.project` is applied by the Settings plugin.

### Source-of-truth and conflict order

1. **The user's request and approved actions** determine intent; never infer permission to publish, accept a EULA, alter a real world, or reveal credentials.
2. `specs.md` defines the foundational product contract and G1–G30 gates. The implemented API/library and new `publish` extensions are additionally specified in the latest root `README.md`.
3. **Actual source** — `src/main/kotlin/dev/fabricamper/config/ModYamlDecoder.kt`, `ModYamlModel.kt`, `ModYamlValidator.kt`, `StrictYamlReader.kt`, plugins and tests — decides what is accepted *today*.
4. `fabric-amper.schema.json` (Draft-07) provides schema keys, types, enums, and editor guidance. It does **not** prove all cross-field constraints, resource existence, buildability, or publication readiness.
5. `QA_REPORT.md` records tested behavior and unresolved gates. `HUMAN.md` explains the same contract for people.

When documentation and implementation disagree, **inspect the current implementation and report the discrepancy** instead of silently inventing an alternate key or changing unrelated source. Record what was actually tested versus inferred.

## Non-negotiable invariants

- One YAML file in the consumer root: `mod.yaml` **or** `mod.yml`, never both. One YAML document, root mapping, UTF-8, no duplicate keys, aliases, anchors, merge keys, arbitrary custom tags, or unsupported extra properties.
- Required root keys: `product`, `mod` and `minecraft`. Required nested values: `mod.id`, `mod.version`, `minecraft.version` and `minecraft.loader`. Root `schema` is **optional**: absent selects the plugin's latest supported schema, **currently v1**; explicit `schema: 1` pins v1; explicit unknown schema numbers or wrong types **must fail** with FA003. Versions that look numeric **must be quoted strings**.
- Supported products: `fabric/mod` and `fabric/api`; `fabric/library` is an exact API/library alias, not a different publishing feature.
- All values in YAML are *configuration values*, not fragments of Gradle Kotlin DSL. Only real keys from `ModYamlDecoder` and the schema are legal; unknown keys trigger `FA003`.
- Never write `src/main/resources/fabric.mod.json` or `src/client/resources/fabric.mod.json`. Fabric Amper generates exactly one **root** `fabric.mod.json` in the production JAR. Never alter `build/generated/**` or `.gradle/fabric-amper/loom/**` mirrors by hand.
- Keep user code, existing Mod Loader dependencies/predicates, release notes, and assets intact unless the request clearly requires changing them. Do not downgrade versions or remove unrelated keys to make validation pass.
- Use fixed, known Maven coordinates and actual existing platform project identifiers. Do not fabricate Maven artifacts, published Yarn versions, Modrinth/CurseForge IDs, account credentials, or a public plugin repository.
- Do not embed secrets in YAML, Kotlin DSL, commands to be logged, sample output, tests, or commits. YAML publishing fields name **environment variables**, never token values.
- `gradle help`, `gradle tasks --all`, `gradle test` and `gradle build` are safe non-publication steps. Remote publication tasks are *side-effectful* and require an explicit user request and deployment prerequisites.
- Do not auto-accept Mojang's EULA, reuse a user's main Minecraft world in automated tests, or equate a successful Gradle build with runtime/IDE/shipping acceptance.

## Agent execution protocol

### Command precedence: prefer Fabric Amper's wrapper when supported

**When operating on Windows with an available complete Fabric Amper checkout, FIRST look for `fabric-amper.bat` and use it for any supported operation.** This is a binding execution-routing rule, not an optional shorthand: `help`, `version`, `docs`, `doctor` (including `--full`, `--paths`, opt-in `--fix`), `init` (including `--here`), `migrate` (including `-C`, `-D`, `--delete`, `--dry-run`), `tasks`, `validate`, `build`, `verify`, `test`, `clean`, `run`, `server`, `diagnose`, `explain`, `plan`, `port` and explicitly requested `publish` variants. Use `-C "consumer_path"` **before** most subcommands when necessary; `migrate` accepts `-C` / `--project` before **or after** the verb, and `doctor` also accepts `doctor -C ".\httpmod"` and `doctor --project ".\httpmod"`, with the path relative to the current terminal directory. The repeated form `doctor -C ".\httpmod" doctor` is supported. For an operation **not supported by the BAT wrapper** (such as a custom Gradle task, `validatePlugins`, a special TestKit filter, or direct producer-only publication), use its **original supported Gradle, Java, Kotlin, or other native command**. Do not claim the wrapper supports arbitrary Gradle tasks or invent a wrapper command.

**Release-version inspection:** `fabric-amper.bat` with **no arguments**, `fabric-amper.bat -V`, `fabric-amper.bat --version`, and `fabric-amper.bat version` all print one line, `v0.1.3`, and exit without launching Gradle or altering files. An empty BAT invocation is **not** `help`; use `fabric-amper.bat help` / `--help` to list commands. This confirms the local BAT's embedded version only, not JitPack or release-asset availability.

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
2. Check the YAML for duplicate keys, unknown keys, string-versus-number mismatches, resource paths, and contradictions (especially `mod.environment` versus main/client code).
3. Run the **non-upload** validation sequence using `fabric-amper.bat tasks` → `fabric-amper.bat build` (and `fabric-amper.bat test` for test changes) when the Windows wrapper exists. `help` here is the wrapper's own help; if a separate Gradle `help` task or special flags are needed, that operation is **not exposed by the wrapper** and must use an actually available native Gradle launcher or a verified consumer wrapper. Never fall back automatically to an unverified `./gradlew`.
4. Inspect the resulting production JAR and root Loader metadata; report both command results and any relevant negative tests. If Gradle fails, fix the *first causal diagnostic*, then repeat — do not simply retry external downloads indefinitely.
5. Report exact changed paths, configuration effect, commands actually run, tests genuinely observed, and remaining risk. Never state a task, IDE import, game boot, upload, or G gate passed when not observed.

## Intent → exact configuration routing

The **next table is the decision index**, not a substitute for the runnable recipes. Entries below `mod` or `settings` must go **inside those mappings**, not at the document root. Except where explicitly stated, all tasks run from the *consumer* directory.

| User asks for… | YAML change / actual file | Validation / caution |
| --- | --- | --- |
| A new ordinary Fabric mod | `product: fabric/mod`, `mod.id`, `mod.version`, `minecraft.version`, `minecraft.loader`; optional `schema: 1` | Create one YAML; `gradle help` then `gradle build` |
| An API/library mod for downstream projects | `product: fabric/api` (or alias `fabric/library`), required `mod.group`, optional `mod.archivesName` | `gradle build`; explicit local Maven publish when needed |
| A new display name/description/license | `mod.name`, `mod.description`, `mod.license` (string or list) | Inspect generated `fabric.mod.json` |
| A different JAR file basename | `mod.archivesName`, not `base.archivesName` or `artifactId` | Verify filename and Maven publication identity |
| A new mod ID/version | `mod.id` / `mod.version` | Update actual resource namespace and references if affected; inspect JAR JSON |
| Minecraft 26.3 with official mappings | `minecraft.version: "26.3"`, `minecraft.mappings: mojang`, `settings.java.release: 25`, fixed `minecraft.loader` | No Yarn; Java 25; non-remapping Loom profile |
| Minecraft 1.21.11 legacy | `minecraft.version: "1.21.11"`, `settings.java.release: 21`, supported `minecraft.mappings` | Verify all API/Loader versions and real legacy game compatibility; remap profile |
| Yarn mappings (legacy only) | `minecraft.mappings: {type: yarn, version: "VERIFIED_VERSION"}` | Never invent Yarn version; reject on 26.1+ (FA004) |
| Fabric API dependency | `minecraft.fabricApi: "EXACT_PUBLISHED_VERSION"` | Check version matches game and actual artifact availability |
| Kotlin code and adapter | `settings.kotlin.enabled: true` with pinned Kotlin settings; optional adapter entrypoint object | Real Kotlin sources; `gradle compileKotlin`/`build`; FA006 |
| Client-only mod | `mod.environment: client`; `mod.entrypoints.client` | Actual client code and side-safe Mixins; run isolated client |
| Shared mod with client-only source or dependency | `settings.loom.splitEnvironmentSourceSets: true`; `dependencies[].target: client` where needed | Place client code in `src/client/`; never include/export client-targeted deps |
| Loader-required installed mod | `mod.depends.<actual_mod_id>: ">=EXACT_VERSION"` (or OR list) | Runtime relationship; not a Maven download |
| Recommended/suggested/incompatible Loader mod | `mod.recommends` / `mod.suggests` / `mod.conflicts` / `mod.breaks` | Preserve genuine version predicates; inspect generated JSON |
| Maven library for compilation/runtime | `repositories` and `dependencies[].maven`, optional `kind` / `scope` | Verify fixed GAV and repository, resolve via `gradle build` |
| Mod hosted in Modrinth's Maven | `repositories: [modrinth]` plus `dependencies[].modrinth.project/version` and optional `kind: mod` | An artifact dependency, not a release-upload project ID |
| Exported public API type | `dependencies[].exported: true` with `scope: all` | API/library publication; verify downstream compile variant |
| Internal runtime-only helper | `dependencies[].scope: runtime-only` | Cannot also `exported` or `include` |
| Development compile-only helper | `dependencies[].scope: compile-only` | Not packaged or runtime-transitive |
| Nested JAR-in-JAR library | `dependencies[].include: true` and `scope: all` | Verify nested `META-INF/jars` and Loader `jars` metadata |
| JUnit tests | `test-dependencies` with a real fixed JUnit dependency | Create tests under `src/test/`; run `gradle test`; no export/client/include |
| Mixin configuration | `mod.mixins` string or `{config, environment}` entry | Create real file in resources, check side behavior |
| Modern Class Tweaker | `mod.accessWidener: "my_mod.classtweaker"` | Create file under `src/main/resources` with `classTweaker v1 official` header |
| Legacy Access Widener | `mod.accessWidener: "my_mod.accesswidener"` | Correct `accessWidener v1/v2 named/intermediary` header; legacy only |
| Icon | `mod.icon: "assets/my_mod/icon.png"` or positive-integer size map | Create real PNG in permitted source resources |
| Custom Loader metadata | `mod.custom` JSON-compatible mapping | Inspect generated JSON; no invented Gradle effects |
| Local Maven library publication | `product: fabric/api` and `mod.group` | `gradle publishMavenJavaPublicationToFabricAmperLocalRepository` (writes inside consumer `build/`) |
| Explicit remote Maven publication | `publish.maven.url` and optional `credentialsFromEnv: [USER_VAR, PASS_VAR]` | Requires `mod.group`; HTTPS only; explicit `gradle publishFabricAmperMaven` |
| Modrinth release | `publish.modrinth.projectId`, `tokenEnv` and verified release options | No platform upload until explicitly asked; task `publishFabricAmperModrinth` |
| CurseForge release | `publish.curseforge.projectId` (integer), `tokenEnv` and verified release options | Distinguish string tag names from integer version IDs |
| Same release metadata for Modrinth and CurseForge | `publish.common.releaseType`, optional `name`, `gameVersions`, `changelog` OR `changelogFile`, and `dependencies[]` with `slug` / optional `modrinthId` and `curseforgeId` / `type` | Only applies to destinations explicitly present; platform values override, relations can override or append |
| Legacy API remote Maven | `publishing.repository.url` and optional paired `usernameEnv`/`passwordEnv` | API/library only; do not also set `publish.maven` |
| Avoid authored metadata collision | Never add any source `fabric.mod.json`; `settings.resources.failOnExistingFabricModJson` stays true | FA008; plugin generates metadata |
| IDE sync | Use generated Gradle model; manual or automatic IntelliJ sync must be actually observed | Do not mark G6/G28 passed from `gradle help` |
| Game run | `gradle runClient` / `gradle runServer` in isolated environment | No real-world overwrites; EULA is for the user |
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
                useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.3")
            }
        }
    }
}

plugins {
    id("dev.fabric-amper.settings") version "0.1.3"
}
```

The Settings plugin is redirected to JitPack using the **intended** GitHub tag `v0.1.3`, while the public plugin ID is requested with version `0.1.3`. The old local `fabric-amper.bat setup` flow has been removed; a direct producer-local Gradle publication is still available separately for tests. Check that the `v0.1.3` tag exists and that JitPack actually serves its module before claiming the bootstrap is operational. Plugin repositories in `settings.gradle.kts` and artifact repositories in `mod.yaml` have separate jobs.

**Consumer `mod.yaml`**:

```yaml
schema: 1
product: fabric/mod
mod:
  id: example_mod
  version: "1.0.0+26.3"
  group: org.example
  archivesName: example-mod
  name: Example Mod
  environment: "*"
  entrypoints:
    main:
      - org.example.ExampleMod
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

On a successful build, inspect `build/libs/example-mod-1.0.0+26.3.jar` and the generated `build/generated/fabric-amper/resources/main/fabric.mod.json`. The production archive must have exactly one **root** `fabric.mod.json`, whose ID and version match the YAML. Do not claim the example is runnable if the plugin marker or game artifacts are inaccessible.

## Safe modification recipes

**Recipe convention:** every `yaml` block labeled **FRAGMENT** below is an *illustrative subsection* to merge under the correct existing keys; it is **not** a new complete `mod.yaml`. Never append a second `mod:`/`settings:`/`dependencies:` mapping or duplicate a key. Preserve all unrelated fields. When replacing an existing value, edit that value in place.

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

**Scope decision:**

| Desired semantics | `scope` | `exported` | `include` |
| --- | --- | --- | --- |
| Normal implementation dependency | `all` (default) | false | false |
| Public API type used by consumers | `all` | true | false unless independently justified |
| Required only to compile | `compile-only` | false | false |
| Required only when running | `runtime-only` | false | false |
| Bundle nested dependency JAR | `all` | false unless part of API | true |

For `target: client`, enable `settings.loom.splitEnvironmentSourceSets: true` and **never** add `exported: true` or `include: true`. Dependencies declared in Gradle do not automatically establish a valid Fabric Loader runtime `mod.depends` requirement.

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

Confirm that the real Modrinth project/version resolves to a Maven artifact and is compatible with the chosen game/Loader. **Do not** confuse `dependencies[].modrinth.project` with `publish.modrinth.projectId`. If Fabric Loader must require that installed mod, additionally set `mod.depends` with the **actual Loader mod ID**, not an inferred hosting-platform ID.

### Request: add or modify Loader-side requirements

**FRAGMENT — version predicates, not Maven coordinates:**

```yaml
mod:
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

### Request: use client code without server classloading

**FRAGMENT — for a *shared* mod with a client entrypoint:**

```yaml
mod:
  environment: "*"
  entrypoints:
    main:
      - org.example.ExampleMod
    client:
      - org.example.client.ExampleClient
settings:
  loom:
    splitEnvironmentSourceSets: true
dependencies:
  - maven: "org.example:real-client-only-helper:1.0.0"
    kind: library
    scope: compile-only
    target: client
```

Put `ExampleClient` under `src/client/java/org/example/client/`, **not** in common source code. This placeholder GAV is not a real package; replace it. For a *strictly client-only* mod, instead set `mod.environment: client` and keep client entrypoints and Mixins client-safe. `mod.environment` controls where the **whole mod** loads; `target: client` controls a **dependency/source set**. They are not synonyms.

### Request: enable Kotlin

Kotlin is **opt-in**. Preserve Java-only configurations with `enabled: false` (or omit the Kotlin mapping); do not automatically turn Kotlin on because the user mentions a Kotlin *dependency*.

**FRAGMENT — pinned Kotlin toolchain and an adapter entrypoint:**

```yaml
settings:
  kotlin:
    enabled: true
    pluginVersion: "2.4.21"
    languageVersion: "2.4"
    fabricLanguageKotlin: "1.14.2+kotlin.2.4.21"
mod:
  entrypoints:
    main:
      - adapter: kotlin
        value: org.example.ExampleKtMod
```

Create `src/main/kotlin/org/example/ExampleKtMod.kt` with a real valid Fabric initializer, or preserve the user's existing Java entrypoint instead of changing it to the Kotlin adapter for no reason. Entrypoint *string* syntax is also allowed; `{adapter, value}` objects require both fields. Do not declare `mod.languageAdapters.kotlin`; the installed Fabric Language Kotlin integration supplies it. Incorrect pinned versions or an adapter with Kotlin disabled fail with **FA006**. Kotlin disabled on a cold dependency cache does **not** currently guarantee no Kotlin Gradle plugin artifacts will be fetched.

### Request: add a Mixin

**FRAGMENT — a common Mixin and a strictly client-scoped Mixin:**

```yaml
mod:
  mixins:
    - example.mixins.json
    - config: example.client.mixins.json
      environment: client
```

Create `src/main/resources/example.mixins.json` for the common mixin and `src/main/resources/example.client.mixins.json` (or `src/client/resources/` with split source sets) for the client-specific one. The referenced file must exist, contain valid Mixin JSON, reference real Mixin classes, and be appropriate for its Loader side. If a resource lives *only* under `src/client/resources` while the mod also loads on a server, explicitly mark its Mixin `environment: client`. Do not assume a successful compile proves the Mixin applies in Minecraft.

### Request: add Class Tweaker or legacy Access Widener

The YAML property is **always** `mod.accessWidener`, even for modern Class Tweakers. **Do not** add the nonexistent property `mod.classTweaker`.

**FRAGMENT — Minecraft 26.3:**

```yaml
mod:
  accessWidener: example.classtweaker
```

Create `src/main/resources/example.classtweaker`. Its **first line must be exactly**:

```text
classTweaker v1 official
```

After the header, write real Class Tweaker directives that match the appropriate Fabric/Loom format; never fabricate directives when a target class or member is unknown. For the legacy Minecraft 1.21.11 remap profile use `mod.accessWidener: example.accesswidener` and a file whose first line matches `accessWidener v1 named`, `accessWidener v2 named`, `accessWidener v1 intermediary`, or `accessWidener v2 intermediary` as supported by the validator and remapping pipeline. The resource must reside under **`src/main/resources/`**, not only `src/client/resources/`. Verify `validateFabricAmper` and the archived resource.

### Request: add a single or multi-resolution icon

**FRAGMENT — choose *one* supported form:**

```yaml
mod:
  icon: assets/example_mod/icon.png
```

**Alternative FRAGMENT — size map:**

```yaml
mod:
  icon:
    "32": assets/example_mod/icon32.png
    "128": assets/example_mod/icon128.png
```

Actually create valid PNG files in `src/main/resources/assets/example_mod/` or a permitted client resource root when appropriate. A size map uses **positive-integer string keys**; do not use `small`/`large`. Resource path checks protect the project root; do not refer to outside files, symlink escapes, or absolute filesystem paths. No icon is generated from text by the Gradle plugin.

### Request: add custom Loader JSON metadata

**FRAGMENT — custom JSON-compatible values only:**

```yaml
mod:
  custom:
    example_mod:feature_flags:
      showOverlay: true
      maxConnections: 8
      displayStyle: compact
```

`mod.custom` is a Loader metadata mapping and does **not** run arbitrary code, install a Java library, or define a Gradle task. Preserve any existing namespaced keys. Do not add executable tags, aliases, cyclic values, or unsupported key/value types.

### Request: configure JUnit tests

**FRAGMENT — test-only dependency:**

```yaml
test-dependencies:
  - "org.junit.jupiter:junit-jupiter:6.0.0"
```

Create actual JUnit Jupiter test classes under `src/test/java/` or `src/test/kotlin/` as appropriate; then run `gradle test`. If the project has another verified JUnit version or test platform, preserve its intentional settings rather than overwriting them. No test dependency can be `exported`, `included`, or targeted to `client`. Avoid asserting `0 tests` is a successful discovery check: inspect the test reports.

## Complete API/library profile for Maven consumers

This is a **complete YAML document** illustrating *valid syntax*. The `dev.example` coordinates are illustrative unresolvable placeholders; replace them with **verified, reachable** dependencies before expecting a real build or local publication.

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

Do **not** infer that the sample IDs or compatibility tags are real. `common` may appear when one or both release targets are configured, but **does not create a destination by itself**; `common` with only `maven` fails FA003. `schema` may be omitted: this plugin selects its latest recognized **v1**; `schema: 1` explicitly pins v1 and any unsupported explicit version fails FA003.

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

**Legacy API/library-only remote Maven FRAGMENT** (choose instead of `publish.maven`, never alongside it):

```yaml
publishing:
  repository:
    url: https://maven.example.org/releases
    usernameEnv: FABRIC_MAVEN_USER
    passwordEnv: FABRIC_MAVEN_PASSWORD
```

The two legacy environment variable names are *both present or both omitted*. The legacy block is **not** supported for `product: fabric/mod`. Standard Gradle native publication may be configured as `publishMavenJavaPublicationToFabricAmperRemoteRepository` for this remote, but never fire it implicitly.

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

`publish.modrinth.dependencies[]` requires `projectId` and `type`, where `type` is `required`, `optional`, `incompatible`, or `embedded`. Do not derive a release-site project ID from `mod.depends` or Maven artifact coordinates. Provide **either** `changelog` inline **or** `changelogFile` (safe relative path), not both. Source `release-notes.md` actually must exist inside the consumer root.

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
schema: 1
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
- Confirm the archive contains **one and only one** root entry named `fabric.mod.json`, not zero, two duplicate ZIP entries, or only a nested copy.
- Parse that JSON; verify `id`, `version`, `environment`, `entrypoints`, `mixins`, `accessWidener`, dependency predicates, and `jars` if applicable.
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

# WRONG — no mod.classTweaker; the implemented key is mod.accessWidener.
mod:
  classTweaker: example.classtweaker

# WRONG — source and release metadata keys are not interchangeable.
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

Also forbidden: root `$schema:` (a comment annotation is acceptable), root `build:`/`plugins:`/`loomVersion:`, a second YAML document separated by `---`, a Gradle `build.gradle.kts` added "just for one dependency", file paths like `../outside/icon.png`, dynamic `latest.release` or `1.+` dependency versions, `settings.resources.failOnExistingFabricModJson: false`, `exported/include` on a client dependency, or `minecraft.mappings: yarn` for MC26.3.

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
| Modern Class Tweaker | `mod.accessWidener` + real modern header | Resource validation and archive check |
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
- [ ] YAML uses only documented schema-v1 fields, fixed versions, the correct product and matching game/Java/mappings profile.
- [ ] Referenced code, resources, repository coordinates, and platform identifiers genuinely exist or are explicitly marked placeholders.
- [ ] No secrets in files/output; no unauthorized publishing, `~/.m2` writes, world changes, EULA changes, or unintended network uploads.
- [ ] Actual commands, their exit status, test counts, JAR path, and root metadata check are recorded when executed.
- [ ] Any missing data, network failure, unrun runtime/IDE gate, and skipped test is called out honestly.
- [ ] The final message states the exact files changed and whether the task is *documentation/configuration only*, *build verified*, *runtime verified*, or *released*.

## Known acceptance boundaries

The producer has a historical Gradle 9.7.1 / JDK 25 clean-build baseline recorded in `QA_REPORT.md`: 46 suites, 247 tests, **207 PASS, 40 SKIP, 0 FAIL, 0 ERROR**. That producer result is **not blanket evidence** for new sample consumers, downstream users, IDE GUI sync, dedicated-server boot, external publishing, or every gate G1–G30. As of the documented baseline, the overall **shipping status remains NOT APPROVED**; dedicated-server full boot, IntelliJ GUI first import/manual+auto sync, full Minecraft 1.21.11 game runtime, and an authenticated native HTTPS Maven PUT with configuration-cache HIT are not all established. No actual outside Modrinth/CurseForge upload was authorized or performed. When status matters, read the current `QA_REPORT.md` rather than assuming earlier success persists.

## Windows command wrapper: fabric-amper.bat

The producer root contains [`fabric-amper.bat`](fabric-amper.bat), a **Windows CMD convenience wrapper** for existing Gradle tasks. It is not a YAML key, not part of the consumer's plugin configuration, and does not create a new publicly distributed plugin. If the user asks for Windows commands and the exact requested action is covered here, use the wrapper; for an unsupported or ambiguous operation read the actual batch implementation and [the compact skill](FabricAmperSKILL_SYN.md) or source first. **Never assume that calling the wrapper makes publishing or real game execution safe.**

**Distinguish producer commands from consumer commands:**

```bat
rem Run inside the Fabric Amper producer directory.
fabric-amper.bat doctor
rem The following is only for an explicitly requested toolchain/file repair:
fabric-amper.bat doctor --fix --dry-run
fabric-amper.bat init example_mod
rem Create within the current tools-only folder without replacing existing project files:
fabric-amper.bat init night_vision --here
fabric-amper.bat init example_api "C:\work\example_api" api

rem Convert an existing Fabric Gradle mod; review first, preserve originals by default:
fabric-amper.bat migrate -C "C:\work\legacy_mod" --dry-run
fabric-amper.bat migrate -C "C:\work\legacy_mod"

rem Call from any other directory; provide full script path and consumer root.
"C:\path\to\Fabric Amper\fabric-amper.bat" -C "C:\work\example_mod" build
```

`init <id> [new_folder] [mod|api]` creates a **new** target folder with `settings.gradle.kts`, `mod.yaml`, a local editor schema snapshot, optional VS Code YAML mapping and a real Java `ExampleMod.java`; it refuses an existing destination. `init <id> --here [mod|api]` instead initializes inside the current folder **only when no project files already exist**: BAT/skill/`.md`/`.gitignore`/existing non-project `.vscode/settings.json` are preserved, but any `mod.yaml`, `mod.yml`, `settings.gradle.kts`, `build.gradle(.kts)`, `src`, or conflicting schema path blocks initialization with no overwrite. Default placement without `--here` is a sibling of the checkout when launched there or a child of the current working directory elsewhere. The generated Settings script targets JitPack's tagged `com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.3` module via `resolutionStrategy` and does **not** require a local `setup` step. The API profile uses a placeholder `org.example` group to replace.

### Convert a legacy Fabric Gradle mod with `migrate` (v0.1.3)

Use `migrate` when the user has an **existing** Fabric mod with exactly one `build.gradle.kts` **or** Groovy `build.gradle`, and exactly one supported `src/main/resources/fabric.mod.json` (or `src/client/resources/fabric.mod.json`). This differs from `init`, which starts a new project, and `port`, which only prints a manual Minecraft version checklist. The migration helper lives at **`scripts/fabric-amper-migrate.ps1`**. If absent, normal `migrate` fetches the exact matching v0.1.3 GitHub Release asset (and the root schema if missing), verifies hard-coded SHA-256 values and only then executes the converter; manual PS1 installation is unnecessary. Both `migrate --dry-run` and `doctor --fix --dry-run` **never download or write files**; when the helper/schema is missing they show only the proposed download, not the actual conversion preview. Existing files with wrong hashes are rejected without overwriting or execution. The converter statically interprets a limited Gradle DSL subset and **never evaluates the original build script or starts Gradle**. It produces `mod.yaml` and `settings.gradle.kts` using the v0.1.3 JitPack plugin coordinates. A successful conversion alone does **not** establish that JitPack serves the tag, that the mod compiles, or that it runs.

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

**After migration:** Inspect the generated YAML (especially dependency `scope`, `kind`, `include`, `exported`, client split source sets, Kotlin entrypoints and mappings) and confirm the v0.1.3 JitPack artifact actually resolves. On Windows, use the established `doctor` → optional `doctor --fix --dry-run` → automatic `doctor --fix` → `doctor` recheck policy **only if a required toolchain is missing**. Then run `fabric-amper.bat -C "C:\work\legacy_mod" validate` and `build`, inspect the produced JAR for a single root `fabric.mod.json`, and perform separate client/server or IDE tests as required. A failed `validate`/`build` never justifies removing sources or silently modifying third-party Gradle logic. When a project falls outside the static converter's supported subset, report the exact blocker and give the user a manual conversion plan.

| User intent | Wrapper command | Actual effect |
| --- | --- | --- |
| Inspect usage and diagnostics | `help`, `version`, `docs`, `doctor`, `doctor --full`, `doctor --paths` | Local toolchain and optional path/JDK checks; no global cache deletion |
| Repair missing Windows toolchain | `doctor --fix` **automatically after `doctor` detects a missing required tool** | Preview first; without extra consent prompt, download exactly the versioned GitHub PS1 if missing, save in `scripts/` below BAT after pinned SHA-256, then try JDK25 via winget/Gradle via winget or official hash-verified ZIP and optional missing consumer files; recheck with `doctor` |
| Preview repair without changing anything | `doctor --fix --dry-run` | No download, installation or file/registry mutation; without a local PS1 **only the proposed download** is shown |
| Preview existing Fabric Gradle project conversion | `migrate --dry-run` or `migrate -C "C:\work\legacy_mod" --dry-run` | With verified local tools, parse and print YAML; if helper/schema is missing, only preview its download; never write or download |
| Convert an existing Fabric Gradle project | `migrate` or `-C "C:\work\legacy_mod" migrate` | Download/hash-check missing `scripts/` helper and schema, then write YAML/Settings and back up original build, settings and source `fabric.mod.json` in `gradle_backup/` |
| Convert and discard original build configuration | `migrate -D` or `migrate --delete` | Explicitly remove those original files only after staging and conversion; does not delete Java/Kotlin source or unrelated resources |
| Initialize in current tools-only folder | `init <id> --here [mod\|api]` | Preserve tools; hard fail on any existing consumer project file |
| Generate a new mod or API | `init <id> [folder] [mod|api]` | New starter only; no overwrite; no `build.gradle` or authored `fabric.mod.json` |
| List/test/validate | `tasks`, `test`, `validate`, `verify` | `tasks --all` / `test` / `validateFabricAmper` / `verifyFabricAmperJar` |
| Diagnose the consumer | `diagnose` / `explain` | `fabricAmperDoctor` toolchain summary / `fabricAmperExplain` independent dependency categories, no network lookups |
| Inspect a prospective release | `plan` | `fabricAmperReleasePlan`; verifies final JAR and prints its checksum + effective release metadata without uploading |
| Compare game profiles | `port 26.3` | `fabricAmperPortingReport -PfabricAmperPortTarget=26.3`; read-only manual migration checklist |
| Package and verify | `build` | `validateFabricAmper build verifyFabricAmperJar` |
| Remove local generated build outputs | `clean` | `gradle clean` for the chosen consumer |
| Start Minecraft client/server | `run` / `server` | `runClient` / `runServer`, game process and normal EULA safeguards apply |
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
