# Fabric Amper — The Human Guide

**Build Fabric and NeoForge mods with one small Gradle bootstrap and one declarative YAML file.**

Fabric Amper is a **Gradle binary plugin**, not a Minecraft mod. Instead of hand-maintaining consumer `build.gradle(.kts)` and Loader metadata, you write `settings.gradle.kts` and **exactly one** `mod.yaml` or `mod.yml`. With schema **1.1**, it configures Fabric Loom, NeoForge ModDevGradle, or both, plus Java/optional Kotlin, dependencies, generated loader metadata, and independent platform JARs. Explicit `schema: 1` preserves the previous Fabric-only configuration language.

> **v0.3.1 prerelease target (2026-11-18); v0.3.0 historical verification (2026-11-17).** Current bootstrap examples below target **0.3.1**, whose new tag/assets/JitPack resolution require independent release verification. The published v0.3.0 prerelease had all nine GitHub assets redownloaded and SHA-256 checked; JitPack `v0.3.0` resolved successfully, and a fresh NeoForge + Architectury + Kotlin consumer passed real remote-plugin `help`, validation, build and JAR verification. Selected schema 1.1 NeoForge and dual-loader TestKit builds also passed. **Actual Minecraft client/server runtime, complete IntelliJ GUI sync, strict cold configuration-cache reuse and full shipping gates remain unapproved.** A version string alone does not prove remote availability, and neither release is established as a Gradle Plugin Portal publication. The BAT's dual `build`/`validate`/`verify` routing was verified with fake Gradle. See [Verification and known limits](#verification-and-known-limits).

This is the **user-facing** guide. For source-of-truth field constraints, see [`fabric-amper.schema.json`](fabric-amper.schema.json); for the actual implementation, see `src/main/kotlin/dev/fabricamper/config/`; for implementation acceptance, see [`specs.md`](specs.md) and [`QA_REPORT.md`](QA_REPORT.md). Agents generating configurations should also read [`FabricAmperSKILL.md`](FabricAmperSKILL.md). For the **single English, Windows-first quick start and feature cheat sheet**, see [`HUMAN_QuickGuide.md`](HUMAN_QuickGuide.md); the former `HUMAN_QuickStart.md` is retained only as a link redirect.

**Check the Windows launcher version without Gradle:** `fabric-amper.bat` (no arguments), `-V`, `--version` or `version` prints its embedded version. The v0.3.1 release-target BAT should print `v0.3.1`; an older value means the local copy is not the current target. Use `help` for usage. **Version output alone is not evidence of publication; GitHub release assets and JitPack v0.3.0 were separately verified on 2026-11-17, while v0.3.1 needs its own verification.**

## v0.3.1 target — schema 1.1 in practice

**Previously verified schema behavior.** GitHub `v0.3.0` and its SHA-256-verified assets were available and a fresh remote JitPack consumer built successfully on 2026-11-17. The v0.3.1 examples describe the upcoming release contract and need separate consumer verification. Treat Minecraft runtime, IDE sync and strict cold-cache behavior as separate acceptance requirements.

| Root YAML `schema` | Meaning in published v0.3.0 |
| --- | --- |
| **Omitted** | **Defaults to 1.1** (implemented and regression-tested); loader fields belong under `fabric:` / `neoforge:` |
| `schema: "1.1"` | Explicit schema 1.1; safest during transition |
| `schema: 1` | Explicit historical Fabric-only v1; field layout is documented in [one legacy reference](#historical-schema-1-reference) |
| `schema: 1.1` (unquoted) | Rejected YAML numeric scalar; use the quoted string |

Exactly one **`mod.yaml` or `mod.yml`** is allowed at the consumer root (both or neither: FA001). A v1.1 `product` can be `fabric/mod`, `neoforge/mod`, `fabric/api`, `neoforge/api`, or an array of one Fabric plus one NeoForge target; `fabric/library` is an alias for `fabric/api`. Root `mod` contains common identity, `minecraft.version` the game version, `settings` Java/Kotlin options, and `repositories` / `dependencies` shared build intent. In schema 1.1, an **object entry** inside root `dependencies[]` can optionally use `loader: fabric` or `loader: neoforge` to select one target; omission applies it to all selected targets. Fabric Loader/API, entrypoints, Mixins, predicates and Loom settings live under `fabric`; NeoForge version, Mixins, access transformers and TOML dependencies under `neoforge`. Never mix v1 root loader fields into a v1.1 document.

### Intended v0.3.1 Settings bootstrap

This v0.3.1 tag/plugin request is the release-target configuration; independent remote resolution remains to be verified. The **previous v0.3.0** tagged prerelease was verified through a fresh JitPack consumer. The Settings plugin remains the **single** user-applied plugin; no consumer `build.gradle(.kts)` and no manually authored loader JSON/TOML is needed:

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
                useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.3.1")
            }
        }
    }
}
plugins {
    id("dev.fabric-amper.settings") version "0.3.1"
}
```

The published v0.3.0 JitPack coordinate was resolved and a fresh NeoForge + Architectury + Kotlin consumer built successfully; that evidence does not establish v0.3.1 publication. New consumer builds still depend on tag/JitPack/network availability. Do not manually apply `dev.fabric-amper.project`, Fabric Loom or `net.neoforged.moddev` in consumer scripts.

### Fabric-only and NeoForge-only YAML

**Fabric 26.3, Java (schema intentionally omitted):**

```yaml
product: fabric/mod
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
  kotlin:
    enabled: false
fabric:
  loader: "0.20.1"
  api: "0.165.2+26.3"
  mappings: mojang
  entrypoints:
    main: [org.example.ExampleFabric]
repositories: [fabric, mavenCentral]
```

Create a real `org.example.ExampleFabric` implementing Fabric's `ModInitializer`. The generated Fabric JAR contains **one root `fabric.mod.json`** and no NeoForge TOML.

**NeoForge 26.3, Java (schema intentionally omitted):**

```yaml
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
  kotlin:
    enabled: false
neoforge:
  version: "26.3.0.101"
  modLoader: javafml
repositories: [mavenCentral, neoforge]
```

Create a real NeoForge `@Mod("examplemod")` class. NeoForge's generated file is **`META-INF/neoforge.mods.toml`** (one copy), including default `neoforge` and `minecraft` dependency ranges; **no Fabric JSON** is included. NeoForge mod IDs allow only lowercase letters, digits and underscores (2–64 chars), and `mod.license` must be a nonempty string. Fabric `ModInitializer` does not replace NeoForge `@Mod`.

### Dual loader: Architectury shared vs independent

```yaml
schema: "1.1"
product: [fabric/mod, neoforge/mod]
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
  kotlin:
    enabled: false
architectury:
  enable: true
fabric:
  loader: "0.20.1"
  api: "0.165.2+26.3"
  mappings: mojang
  entrypoints:
    main: [org.example.ExampleFabric]
neoforge:
  version: "26.3.0.101"
repositories: [mavenCentral, fabric, neoforge]
dependencies: []
```

With `architectury.enable: true`, the Settings plugin creates **`:common`, `:fabric`, `:neoforge`**. Write loader-neutral sources in `common/src/main/java`, a Fabric initializer in `fabric/src/main/java` and a NeoForge `@Mod` entrypoint in `neoforge/src/main/java`. The neutral module uses `dev.architectury:architectury`, and each loader uses the appropriate `architectury-fabric` or `architectury-neoforge`. **With Architectury false/omitted**, a dual `product` creates **`:fabric` and `:neoforge` only**, with *no* `common/` module or shared source translation.

```text
dualmod/
├── settings.gradle.kts
├── mod.yaml                        # or mod.yml
├── common/src/main/java/           # ONLY dual + Architectury
├── fabric/src/main/java/
├── fabric/src/main/resources/
└── neoforge/src/main/java/
```

For Kotlin, set `settings.kotlin.enabled: true` and use the corresponding `src/main/kotlin` paths. Kotlin Gradle Plugin 2.4.21 is pinned; **Fabric Language Kotlin is Fabric-only**, and a NeoForge Kotlin `@Mod` class has distinct runtime semantics. `fabric.loom.splitEnvironmentSourceSets` is a Fabric-specific option. A NeoForge-only split is rejected; dual independent split creates only `fabric/src/client`, while dual Architectury ignores the split flag with a diagnostic. Loader-specific compilation dependencies belong under `fabric.gradleDependencies` or `neoforge.gradleDependencies`; root `dependencies` are shared intent. `fabric.depends` and `neoforge.dependencies` declare **different loader runtime metadata**, not Maven classpaths. Fabric `include: true` JIJ cannot be assumed to work for NeoForge, where embedding is explicitly restricted.

### Windows initialization and explicit execution selection

```bat
fabric-amper.bat init examplemod                  rem Fabric by default
fabric-amper.bat init examplemod --here --mod-neoforge
fabric-amper.bat init examplemod dualmod mod --mod-fabric --mod-neoforge --architectury
fabric-amper.bat init examplemod independent mod --mod-fabric --mod-neoforge --kotlin
fabric-amper.bat init examplemod independent_split mod --mod-fabric --mod-neoforge --splitEnvironmentSourceSets

cd dualmod
gradle build
cd ..
fabric-amper.bat -C "dualmod" run --mod-fabric
fabric-amper.bat -C "dualmod" run --mod-neoforge
fabric-amper.bat -C "dualmod" server --mod-fabric
fabric-amper.bat -C "dualmod" server --mod-neoforge
```

`init` without loader flags defaults to Fabric; `--mod-neoforge` selects NeoForge alone; both loader flags select dual. `--architectury` adds the shared module only in dual mode; `--kotlin` creates Kotlin sources instead of Java, and `--here` initializes a compatible existing tools folder. NeoForge/dual BAT templates write `schema: "1.1"`; for the historic Fabric template's compatibility pin, see the [legacy reference](#historical-schema-1-reference). No starter writes consumer build scripts or manual loader manifests.

Single-loader `run` / `server` without flags use `runClient` / `runServer`. **Dual loaders require exactly one** `--mod-fabric` or `--mod-neoforge` on each run/server invocation; they map to `runFabricClient` / `runNeoForgeClient` and `runFabricServer` / `runNeoForgeServer`. Missing/mismatched loader selection, duplicate flags and arbitrary arguments are rejected. `gradle build` at the dual root builds both modules; check `gradle tasks --all` for available target tasks. The current BAT's `build`, `validate` and `verify` safely dispatch to both `:fabric` and `:neoforge` validations/verifications in dual mode (and keep root tasks for a single loader). This dispatch has fake-Gradle command-routing tests; actual consumer compilation and game runtime are separate checks. The server EULA must be reviewed manually; Fabric Amper never accepts it or handles authentication automatically.

**Verification boundary:** Selected NeoForge/dual Java/Kotlin/Architectury consumer builds and TestKit results are **PASS**, and the published v0.3.0 JitPack remote consumer was independently built. This does not prove actual Minecraft client/server startup or IDE GUI import/sync. Warm steady-state configuration-cache HIT has been observed; **cold second-run HIT was not consistently met** when `:common` first materialized its JAR. Full NF/G gates remain unapproved. See `QA_REPORT.md` and `specs.md` §12 for evidence boundaries.

The examples in the remainder of this guide use **schema 1.1**. Existing projects pinned to schema 1 can consult the single [historical schema 1 reference](#historical-schema-1-reference) at the end; do not mix the two grammars.

## Contents

1. [What changes, and what does not](#what-changes-and-what-does-not)
2. [Requirements and availability](#requirements-and-availability)
3. [Five-minute quick start](#five-minute-quick-start)
4. [Understanding the project layout](#understanding-the-project-layout)
5. [Traditional Gradle versus Fabric Amper](#traditional-gradle-versus-fabric-amper)
6. [YAML essentials and rules](#yaml-essentials-and-rules)
7. [`mod` and Fabric Loader metadata](#mod-and-fabric-loader-metadata)
8. [Minecraft, Java, Kotlin, and source sets](#minecraft-java-kotlin-and-source-sets)
9. [Repositories and dependencies](#repositories-and-dependencies)
10. [Common recipes](#common-recipes)
11. [Building, running, testing, and using an IDE](#building-running-testing-and-using-an-ide)
12. [Migrating an existing Fabric Gradle mod (Windows)](#migrating-an-existing-fabric-gradle-mod-windows)
13. [Creating an API/library and publishing locally](#creating-an-apilibrary-and-publishing-locally)
14. [Explicit Maven, Modrinth, and CurseForge publishing](#explicit-maven-modrinth-and-curseforge-publishing)
15. [Complete field reference](#complete-field-reference)
16. [Troubleshooting and diagnostics](#troubleshooting-and-diagnostics)
17. [Verification and known limits](#verification-and-known-limits)
18. [Frequently asked questions](#frequently-asked-questions)
19. [Historical schema 1 reference](#historical-schema-1-reference)
20. [References and contributing](#references-and-contributing)

## What changes, and what does not

**Fabric Amper handles build configuration and generated metadata.** You still write your mod's Java or Kotlin source code, Mixin configurations and classes, icons, resource files, and other actual game content. No build plugin can infer a working mod implementation from a YAML file alone.

| You normally maintain | With Fabric Amper |
| --- | --- |
| `settings.gradle.kts` plus a lengthy `build.gradle.kts` | `settings.gradle.kts` for plugin resolution; `mod.yaml` for project settings |
| Fabric Loom plugin selection, Minecraft/Loader/API versions, and Java configuration across Gradle scripts | `minecraft` and `settings` keys |
| `fabric.mod.json` authored and synchronized with the build version | `mod` keys; Fabric Amper generates the metadata |
| Maven/Modrinth repositories and Gradle dependency scopes in Kotlin DSL | `repositories`, `dependencies`, and `test-dependencies` |
| Hand-wired Maven publication for a library | `product: fabric/api`, optional `publishing` / `publish` |
| Your mod's Java/Kotlin/classes, Mixins, icons, textures, actual tests | **Still your responsibility** |

The **single entry plugin** is `dev.fabric-amper.settings`. Do not separately apply `dev.fabric-amper.project` in a consumer: it is an internal implementation plugin applied automatically.

## Requirements and availability

| Component | Current documented example | Notes |
| --- | --- | --- |
| Fabric Amper | **v0.3.0 published prerelease** | GitHub assets SHA-256 checked; public JitPack resolved in a fresh NeoForge+Architectury+Kotlin consumer |
| Minecraft | `26.3` | Non-remapping, Mojang official names |
| Java/JDK | **25** | Gradle itself must run on Java 25 or newer for Minecraft 26.3; YAML release must be 25 |
| Fabric Loader | `0.20.1` | Fixed version, not `latest.release` |
| Fabric API | `0.165.2+26.3` | Optional dependency in `fabric.api` |
| Gradle | `9.7.1` used for the latest project validation | Other combinations require their own compatibility evidence |
| Kotlin (optional) | KGP `2.4.21`; language `2.4`; Fabric Language Kotlin `1.14.2+kotlin.2.4.21` | Must use the plugin's pinned compatible combination |
| Legacy profile | Minecraft `1.21.11` with Java **21** | Uses remapping Loom and a separate runtime acceptance path |

Use a project Gradle Wrapper when one is actually installed. If the consumer does not contain `gradlew` / `gradlew.bat`, run an installed Gradle executable instead. **The Fabric Amper producer repository itself does not currently supply a wrapper**; the examples below use `gradle` to mean an installed compatible Gradle launcher.

### How to obtain the plugin today

A plugin repository normally supplies a Gradle marker and binary. **Fabric Amper is not published to the Gradle Plugin Portal**. For the **v0.3.1 release target**, request `com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.3.1` and plugin version `0.3.1`, then verify that the tag and JitPack artifact are available. **Historically, v0.3.0** was published on 2026-11-17; its JitPack status and POM were checked and a new NeoForge + Architectury + Kotlin consumer built successfully against that coordinate. Check the actual BAT/version in an old local checkout before using `init` or `migrate`.

For **local producer development / offline integration tests only**, a separate direct Gradle publication still exists. From the Fabric Amper source project:

```powershell
# Run from the Fabric Amper repository (not the new consumer).
gradle publishAllPublicationsToG29ProbeRepository -PfabricAmperProbeMavenRepo=true
```

This produces the project-local plugin repository at `build/g29-probe/maven-repo/`. It does **not** publish to Maven Central, Gradle Plugin Portal, or your user-wide Maven repository. **This is no longer the `fabric-amper.bat setup` workflow**: the batch `setup` command has been removed and `init` no longer refers to this local repository.

The quick start below uses JitPack and does **not** require a sibling producer installation or a manual `setup` step. For a complete Windows-generated starter, see `fabric-amper.bat init`.

## Five-minute quick start

The following is a minimal **Java-only, Minecraft 26.3** project. It needs no consumer `build.gradle`, no consumer `build.gradle.kts`, and no authored `fabric.mod.json`.

### 1. Make the folder structure

```text
my-fabric-mod/
├── settings.gradle.kts
├── mod.yaml
├── schemas/mod.schema.json               # optional local editor schema copy
└── src/main/java/org/example/ExampleMod.java
```

### 2. Write `settings.gradle.kts`

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

This bootstrap requests the **v0.3.1 target** JitPack tag with plugin version `0.3.1` (verify availability when released). `pluginManagement.repositories` resolves plugins, while `mod.yaml` `repositories` resolves mod libraries. A matching local launcher and network access are required.

### 3. Write `mod.yaml`

```yaml
schema: "1.1"
product: fabric/mod

mod:
  id: example_mod
  version: "1.0.0+26.3"
  group: org.example
  archivesName: example-mod
  name: "Example Mod"
minecraft:
  version: "26.3"
fabric:
  loader: "0.20.1"
  api: "0.165.2+26.3"
  mappings: mojang
  environment: "*"
  entrypoints:
    main:
      - org.example.ExampleMod

settings:
  java:
    release: 25
    sourcesJar: true
  kotlin:
    enabled: false

repositories:
  - fabric
  - mavenCentral
```

This is a complete **schema 1.1** Fabric example. You can omit the `schema` key in v0.3.0; the quoted value makes the version explicit. Loader configuration and metadata belong in `fabric`, while `mod` holds common identity. `repositories` is an array in **both** schema versions. Quote version strings and implement the entrypoint in real source. See [historical schema 1](#historical-schema-1-reference) to maintain older projects.

### 4. Write the Java entrypoint

File: `src/main/java/org/example/ExampleMod.java`

```java
package org.example;

import net.fabricmc.api.ModInitializer;

public final class ExampleMod implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("Example Mod initialized by Fabric Loader");
    }
}
```

### 5. Build and inspect the result

From **`my-fabric-mod/`**:

```powershell
gradle help
gradle tasks --all
gradle build
```

With a valid toolchain, available dependencies, and a successful build, the non-remapping Minecraft 26.3 distribution is:

```text
build/libs/example-mod-1.0.0+26.3.jar
```

The JAR must contain **exactly one** `fabric.mod.json` at its root, plus your compiled classes and resources. You did not write that JSON: Fabric Amper generated it from `mod.yaml`.

To launch the client in an **isolated development environment**, run `gradle runClient` only when you intend to start Minecraft. An interactive client needs the usual local graphics/game environment; a successful `gradle help` does not prove gameplay works.

## Understanding the project layout

```text
my-fabric-mod/
├── settings.gradle.kts                     # plugin bootstrap: YOU write this
├── mod.yaml                                # build + Loader metadata: YOU write this
├── src/
│   ├── main/
│   │   ├── java/org/example/ExampleMod.java
│   │   └── resources/
│   │       ├── assets/example_mod/icon.png # if referenced from fabric.icon
│   │       ├── example.mixins.json          # if referenced from fabric.mixins
│   │       └── example.classtweaker         # if referenced from fabric.accessWidener
│   ├── client/java/                         # optional split client source set
│   └── test/java/                           # optional tests
├── .gradle/fabric-amper/loom/               # GENERATED configuration-time mirror
│   └── <content-hash>/
│       ├── fabric.mod.json
│       └── <referenced resource mirrors>
└── build/
    ├── generated/fabric-amper/resources/main/fabric.mod.json
    ├── libs/
    └── fabric-amper/maven-repository/        # local publication if requested
```

The exact mirror subdirectories depend on source-resource content and can change between builds. **Do not edit generated `build/` or mirror `.gradle/` files by hand.** The canonical metadata is always your authored YAML plus referenced resources.

Do not create `src/main/resources/fabric.mod.json` or `src/client/resources/fabric.mod.json`. Fabric Amper rejects either case with **FA008** instead of overwriting your file.

## Traditional Gradle versus Fabric Amper

These comparisons show **the same author intent**, not a claim of mechanically identical generated Gradle scripts or independently tested third-party Loom versions. Fabric Amper owns plugin versions, task wiring, metadata mirroring, and packaging rules that a handwritten script would need to establish separately.

### Comparison A: Minecraft and Java setup

**Traditional `build.gradle.kts` (illustrative sketch):**

```kotlin
plugins {
    id("net.fabricmc.fabric-loom") version "<compatible-loom-version>"
    java
}

group = "org.example"
version = "1.0.0+26.3"

base { archivesName.set("example-mod") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

dependencies {
    minecraft("com.mojang:minecraft:26.3")
    implementation("net.fabricmc:fabric-loader:0.20.1")
    implementation("net.fabricmc.fabric-api:fabric-api:0.165.2+26.3")
}
```

**Fabric Amper `mod.yaml`:**

```yaml
schema: "1.1"
product: fabric/mod
mod:
  id: example_mod
  version: "1.0.0+26.3"
  group: org.example
  archivesName: example-mod
minecraft:
  version: "26.3"
fabric:
  mappings: mojang
  loader: "0.20.1"
  api: "0.165.2+26.3"
settings:
  java:
    release: 25
```

**What disappeared?** You no longer select the Loom plugin and create/version Gradle dependency configurations yourself. The required Java version and Maven-facing identity remain visible as values, not imperative build logic. **`settings.gradle.kts` is still necessary** to make Fabric Amper available to Gradle.

### Comparison B: Fabric Loader metadata

**Traditional `src/main/resources/fabric.mod.json` (authored manually):**

```json
{
  "schemaVersion": 1,
  "id": "example_mod",
  "version": "1.0.0+26.3",
  "name": "Example Mod",
  "environment": "*",
  "entrypoints": { "main": ["org.example.ExampleMod"] }
}
```

**Fabric Amper: the same Fabric Loader fields inside `fabric:` in schema 1.1:**

```yaml
mod:
  id: example_mod
  version: "1.0.0+26.3"
  name: Example Mod
fabric:
  environment: "*"
  entrypoints:
    main:
      - org.example.ExampleMod
```

The YAML snippet contains `mod` identity and a `fabric` metadata fragment, not a standalone valid file. Fabric Amper combines it with `product`, `minecraft` and `fabric.loader` (plus optional `schema`) to generate metadata. Your Java entrypoint remains real source code. You must **remove the manually authored JSON** when adopting Fabric Amper.

### Comparison C: dependency semantics

**Traditional Gradle DSL (conceptual Java-library dependency intent):**

```kotlin
dependencies {
    api("org.example:public-contract:1.2.3")
    implementation("org.example:internal-helper:2.0.0")
    compileOnly("org.example:annotations:1.0.0")
    runtimeOnly("org.example:runtime-helper:1.0.0")
}
```

**Fabric Amper `dependencies` (same intent for `fabric/api`):**

```yaml
dependencies:
  - maven: "org.example:public-contract:1.2.3"
    exported: true
  - maven: "org.example:internal-helper:2.0.0"
    scope: all
  - maven: "org.example:annotations:1.0.0"
    scope: compile-only
  - maven: "org.example:runtime-helper:1.0.0"
    scope: runtime-only
```

**Important:** These `org.example` dependency coordinates are fictional syntax examples. Choose real published coordinates and repository URLs before attempting a build. `exported` controls library API exposure; `include` is a **different** feature that embeds a JAR in the distribution.

### Comparison D: publishing a library

**Traditional Gradle Kotlin DSL (illustrative):**

```kotlin
plugins {
    `java-library`
    `maven-publish`
}
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
```

**Fabric Amper:**

```yaml
product: fabric/api
mod:
  id: example_api
  version: "1.0.0"
  group: org.example
```

Again, this is a **fragment**. In a real schema 1.1 YAML file, provide `minecraft.version` and `fabric.loader` as well. The API product configures the Java library and Maven publication automatically and provides a **project-local** Maven destination. No remote upload is implicit.

## YAML essentials and rules

- **Use two spaces for indentation.** Write ordinary mappings and lists, not executable Gradle/Kotlin snippets inside YAML.
- Use **exactly one** `mod.yaml` **or** `mod.yml`. Both at once, or neither, is **FA001**.
- In published v0.3.0, omitted `schema` selects **1.1**, and `schema: "1.1"` is its explicit form. Schema 1.1 supports Fabric, NeoForge and dual-loader arrays. Older projects must explicitly pin schema 1 and use the [historical syntax](#historical-schema-1-reference). Unsupported versions fail FA003.
- Quote Minecraft, Loader, API, Maven, version-range, and numeric-looking version **strings**. `version: 26.3` may parse as a number and fail; use `version: "26.3"`.
- Use true YAML booleans `true` / `false`, not `yes` or quoted `"true"`.
- Fixed Gradle versions only. No `latest.release`, `1.+`, or `1.2.x` for `fabric.loader` / `fabric.api` / `neoforge.version`.
- Do not use anchors, aliases, merge keys, arbitrary YAML tags, duplicate mapping keys, multiple documents, or malformed UTF-8. The strict reader rejects them rather than guessing.
- Unknown fields fail: `additionalProperties: false` is intentional in the public schema. Do not invent a `loomVersion` or `build.gradle` field.
- `fabric.custom` is deliberately open for Fabric Loader custom **JSON-compatible** metadata. Its keys are *not* build configuration.
- Defaults shown in the JSON Schema are documentation. The **plugin** decides defaults; editors do not magically insert them.

### Required root keys (schema 1.1)

```yaml
schema: "1.1"
product: fabric/mod
mod:
  id: my_mod
  version: "1.0.0"
minecraft:
  version: "26.3"
fabric:
  loader: "0.20.1"
```

This minimum illustrates the current Fabric schema 1.1 contract. To produce a **useful mod**, add `fabric.entrypoints.main` and a real source file. See [historical schema 1](#historical-schema-1-reference) for the previous field layout.

## `mod` and Fabric Loader metadata

The `mod` mapping describes the shared project identity and Gradle archive identity. Fabric Loader-specific metadata lives in the `fabric` mapping in schema 1.1.

| Field | Purpose / example | Notes |
| --- | --- | --- |
| `mod.id` | `my_mod` | Required; 2–64 chars; starts with lowercase ASCII; `a-z`, digits, `_`, `-` |
| `mod.version` | `"1.2.0+26.3"` | Required; nonblank, no whitespace; becomes Gradle project version and Loader version |
| `mod.group` | `org.example` | Maven group; **required** for `fabric/api` or `publish.maven` |
| `mod.archivesName` | `my-mod` | Output artifact name; defaults to `mod.id` |
| `mod.name` / `description` | Display metadata | Optional; description may contain readable prose |
| `fabric.environment` | `"*"`, `client`, `server` | Defaults to `"*"` (both sides); side safety still matters |
| `mod.license` | `MIT` or `[MIT, Apache-2.0]` | String or list of strings |
| `mod.authors` / `contributors` | `[Alice]`, objects with `name` / `contact` | Real people are examples, not guessed project authors |
| `mod.contact` | `homepage`, `issues`, `email`, `sources` | String-valued metadata map |
| `fabric.icon` | `assets/my_mod/icon.png` or size→path map | Must point to an existing PNG resource |
| `fabric.provides` | `[old_mod_id]` | Other supplied Fabric mod IDs; no duplicates or self-reference |
| `fabric.entrypoints` | `main` / `client` / `server` / custom entrypoint keys | Each key maps to a list of class names or adapter/value objects |
| `fabric.languageAdapters` | Adapter ID → implementation class | Do not redefine the reserved Kotlin adapter |
| `fabric.mixins` | List of config paths or environment objects | Reference real packaged configuration files |
| `fabric.accessWidener` | `my_mod.classtweaker` (26.3) | Resource path; header/namespace/profile validated |
| `fabric.depends` | `fabricloader: ">=0.20.1"` | **Loader runtime** version predicates, not Maven coordinates |
| `fabric.recommends` / `suggests` | Optional Fabric Loader relationships | Predicate map, like depends |
| `fabric.conflicts` / `breaks` | Incompatible Fabric Loader relationships | Predicate map, like depends |
| `fabric.custom` | JSON-compatible custom metadata | Preserve underlying Fabric Loader extensions |

### Entrypoints, people, icons, and Mixins

```yaml
mod:
  id: example_mod
  version: "1.0.0+26.3"
  authors:
    - name: "Example Developer"
      contact:
        homepage: "https://example.org"
  license: [MIT, Apache-2.0]
  contact:
    homepage: "https://example.org"
    issues: "https://example.org/issues"
fabric:
  icon:
    "32": "assets/example_mod/icon_32.png"
    "128": "assets/example_mod/icon_128.png"
  entrypoints:
    main: [org.example.ExampleMod]
    client:
      - adapter: default
        value: org.example.ExampleClient
  mixins:
    - config: example.mixins.json
      environment: "*"
    - config: example.client.mixins.json
      environment: client
  accessWidener: example.classtweaker
```

This fragment combines shared `mod` identity and loader-specific `fabric` metadata. Every icon, Mixin config, and Class Tweaker file **must actually exist** under an appropriate `src/main/resources/` or enabled `src/client/resources/` tree. The `accessWidener` is **main-resources only**. Fabric Amper validates resource paths and prevents traversal outside source roots. Source-set and side restrictions can reject a seemingly valid path.

For Minecraft **26.1+**, a Class Tweaker file uses the `.classtweaker` extension and the exact header `classTweaker v1 official`. For a **legacy remapping** version, use `.accesswidener` with a supported `accessWidener v1/v2 named/intermediary` header. The file must contain valid, version-appropriate declarations; the YAML does not replace its contents.

### Loader predicates are not Gradle dependencies

```yaml
fabric:
  depends:
    fabricloader: ">=0.20.1"
    another_mod: ">=2.0.0 <3.0.0"
  recommends:
    optional_mod:
      - ">=1.0.0"
      - "2.0.0"
```

Inside a predicate **string**, whitespace-separated requirements are an **AND**; a YAML list means **OR**. Fabric Amper fills missing `minecraft`, `fabricloader`, `java`, and configured API/Kotlin requirements in Loader metadata without silently overwriting an authored key. Explicit contradictions with selected toolchain versions are errors. An ordinary `dependencies` entry does **not** automatically become a global `fabric.depends` requirement, especially for client-only libraries.

## Minecraft, Java, Kotlin, and source sets

### Minecraft `26.3` (non-remapping)

```yaml
minecraft:
  version: "26.3"
fabric:
  mappings: mojang
  loader: "0.20.1"
  api: "0.165.2+26.3"
settings:
  java:
    release: 25
    sourcesJar: true
```

For Minecraft 26.1+, Fabric Amper selects the **non-remapping Loom** profile and official Mojang names. `mappings: yarn` is rejected (FA004). Do not add a Gradle `mappings` dependency or manually call `remapJar` as the release artifact for this profile; normal `jar` is the distribution task.

### Minecraft `1.21.11` (legacy remapping)

```yaml
minecraft:
  version: "1.21.11"
fabric:
  mappings: mojang
  loader: "0.20.1"
settings:
  java:
    release: 21
```

For this legacy profile, Fabric Amper selects remapping Loom and `remapJar` for the release artifact. Yarn is a legacy-only alternative when you explicitly provide a version:

```yaml
minecraft:
  version: "1.21.11"
fabric:
  mappings:
    type: yarn
    version: "YOUR_ACTUAL_PUBLISHED_YARN_VERSION"
  loader: "0.20.1"
settings:
  java:
    release: 21
```

These are profile examples, **not** a promise that every game/runtime combination was fully tested. The Yarn mapping version above is **a deliberate placeholder**, not a verified published coordinate. Replace it with a genuinely available mapping version compatible with the game and Loom before attempting a build.

### Optional Kotlin

Java-only projects are the default. To add Kotlin, opt in:

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

Fabric Amper configures the pinned Kotlin Gradle Plugin and Fabric Language Kotlin. With a Kotlin language adapter entrypoint:

```yaml
fabric:
  entrypoints:
    main:
      - adapter: kotlin
        value: org.example.KotlinEntrypoint
```

Use the correct real Kotlin source and entrypoint implementation. An explicit `adapter: kotlin` with `settings.kotlin.enabled: false` is invalid (FA006). The optional version keys are **pinned**; an arbitrary Kotlin plugin override can fail compatibility checks. Even when Kotlin is disabled, the currently packaged plugin may incur KGP-related static dependency downloads on a cold cache; this is a known optimization limitation.

### Split client/main source sets

```yaml
fabric:
  loom:
    splitEnvironmentSourceSets: true
dependencies:
  - maven: "org.example:client-helper:1.0.0"
    target: client
    scope: compile-only
```

`target: client` is for dependencies used only by the separate `src/client/java` / `src/client/kotlin` code, **not** `fabric.environment: client`. Split source sets are supported on the non-remapping profile; client dependencies cannot be `exported` or `include`. Maintain side-safe Mixin/entrypoint declarations: common/server code must not import or require client-only classes.

The artifact `org.example:client-helper:1.0.0` above is illustrative and must be replaced by a real library.

## Repositories and dependencies

There are three different dependency concepts in a Fabric project:

1. **Gradle dependencies** under top-level `dependencies` (and `test-dependencies`) determine which artifacts can be compiled against and are available at runtime.
2. **Fabric Loader dependencies** under `fabric.depends`, `fabric.recommends`, `fabric.suggests`, `fabric.conflicts`, and `fabric.breaks` describe the requirements Fabric Loader checks **inside the game**. NeoForge has a separate `neoforge.dependencies` **array** for TOML runtime relationships.
3. **Release-site relationships** under `publish.modrinth.dependencies` or `publish.curseforge.relations` tell a **specific publishing platform** how your uploaded release relates to other projects.

These are **three independent concepts**. For example, downloading a mod from Modrinth Maven does not automatically give Fabric Amper its Modrinth release-project ID, and a `target: client` Gradle dependency must not silently become a dedicated-server Loader requirement.

### Where Gradle resolves dependencies

```yaml
repositories:
  - fabric
  - mavenCentral
  - modrinth
  - maven: "https://maven.example.org/releases"
```

Supported repository names are `fabric`, `mavenCentral`, and `modrinth`. Custom Maven repositories use an object with exactly one `maven` key and an HTTPS URL. There is no arbitrary Groovy/Kotlin repository script embedded in the YAML.

**Important distinction:** These repositories resolve `dependencies`. They do **not** make the Fabric Amper **settings plugin** available in `settings.gradle.kts`. Plugin resolution is configured separately under `pluginManagement.repositories`.

### Dependency syntax: three shapes

The simple **fixed-coordinate string**:

```yaml
dependencies:
  - "org.apache.commons:commons-lang3:3.17.0"
```

The **one-key shorthand**, with a value from `all`, `compile-only`, `runtime-only`, `exported`, or `include`:

```yaml
dependencies:
  - "org.example:annotations:1.0.0": compile-only
  - "org.example:public-api:1.0.0": exported
  - "org.example:bundled-helper:1.0.0": include
```

The **explicit object**:

```yaml
dependencies:
  - maven: "org.example:some-helper:1.0.0"
    kind: library
    scope: all
    target: main
    exported: false
    include: false
```

**Schema 1.1 only — choose a backend on individual root object entries:**

```yaml
dependencies:
  - "org.apache.commons:commons-lang3:3.17.0"  # shared by all selected loaders
  - maven: "org.example:fabric-only-helper:1.0.0"
    loader: fabric
  - maven: "org.example:neoforge-only-helper:1.0.0"
    loader: neoforge
```

Here `loader` belongs to the **entry**, not the `dependencies` array or the root YAML. The object may select only a loader listed in `product`; omitting `loader` keeps shared behavior. Loader-specific `fabric.gradleDependencies[]` / `neoforge.gradleDependencies[]` remain available and already imply their backend. Neither those lists nor `test-dependencies[]` accept `loader`, and explicit `schema: 1` continues to reject it. The `org.example` artifacts are illustrative placeholders.

All `org.example` coordinates in this guide are **illustrative, not checked as real published artifacts**. Use a real `group:artifact:fixed-version` available through your chosen repositories. Dynamic Gradle selectors and arbitrary version ranges are not supported for Maven coordinates.

### Dependency scope matrix

| YAML field | Values / default | What happens |
| --- | --- | --- |
| `maven` | Fixed Maven coordinate, mutually exclusive with `modrinth` | Resolves a normal Maven artifact |
| `modrinth` | `{project, version}`, mutually exclusive with `maven` | Resolves a fixed Modrinth Maven coordinate |
| `kind` | `library` or `mod`; default `library` for Maven, `mod` for Modrinth | Selects dependency/transform handling, especially legacy remapping |
| `scope` | `all` (default), `compile-only`, `runtime-only` | Gradle compile/runtime exposure |
| `target` | `main` (default), `client` | Source-set visibility; client requires the supported split profile |
| `loader` | `fabric` or `neoforge` (optional on schema 1.1 root object entries) | Select one declared loader; omission shares across the selected targets |
| `exported` | `false` (default) or `true` | Expose a supported API/library dependency to downstream compile |
| `include` | `false` (default) or `true` | Add a Loom **nested JAR-in-JAR** in addition to a normal dependency |

When using `exported: true` or `include: true`, the `scope` must be `all`. They cannot be combined with `scope: compile-only` or `runtime-only`. With `target: client`, both `exported` and `include` must be false, and Fabric's split source sets must be enabled on a non-remapping Minecraft profile; NeoForge has no Fabric client source set. For dual loader projects, use `dependencies[].loader` for a single-loader object in the root list, or `fabric.gradleDependencies` / `neoforge.gradleDependencies` for entries nested under the matching loader. Fabric JAR-in-JAR behavior must not leak into NeoForge; these collections are arrays.

**JAR-in-JAR versus normal dependency:** `scope: all` means compile/runtime dependency. It does **not** package that JAR inside your distribution. `include: true` also includes a non-transitive nested JAR and the corresponding Loader `jars` metadata. This is useful for libraries that must ship with your mod but should not be confused with Maven transitivity or a Loader-side required-mod declaration.

### Modrinth as a dependency repository

```yaml
repositories:
  - modrinth
dependencies:
  - modrinth:
      project: "REPLACE_WITH_REAL_PROJECT_ID_OR_SLUG"
      version: "REPLACE_WITH_FIXED_PUBLISHED_VERSION"
    kind: mod
    scope: all
```

These strings are **placeholders**, not working project/version IDs. `dependencies[].modrinth.project` and `version` are used for **dependency resolution**, not for uploading your mod or claiming another author's project. You must choose a compatible artifact and verify it resolves.

### Testing dependencies

```yaml
test-dependencies:
  - "org.junit.jupiter:junit-jupiter:6.0.0"
```

Or use the explicit mapping:

```yaml
test-dependencies:
  - maven: "org.junit.jupiter:junit-jupiter:6.0.0"
    scope: all
```

`test-dependencies` support the same Maven/Modrinth coordinate concept and scope values but **cannot** be exported, included, or client-targeted. A supplied JUnit Jupiter dependency selects JUnit Platform/Jupiter integration. Tests live in ordinary `src/test/java` or enabled Kotlin test source sets, and are invoked with `gradle test`.

## Common recipes

These recipes are **schema 1.1 fragments** to merge into an otherwise valid YAML document. Do not paste multiple independent `fabric:`, `mod:` or `settings:` mappings into one file; combine their contents under a single mapping instead.

### Create a strictly client-only mod

```yaml
fabric:
  environment: client
  entrypoints:
    client: [org.example.ClientWidgets]
  loom:
    splitEnvironmentSourceSets: true
```

Use `src/client/java` for client implementation. `environment: client` tells Fabric Loader **not to load the entire mod on dedicated servers**. That is different from leaving the whole mod `"*"` and adding an optional client entrypoint to a two-sided mod.

### Add client-only Gradle classes but keep a common mod

```yaml
fabric:
  environment: "*"
  loom:
    splitEnvironmentSourceSets: true
dependencies:
  - maven: "org.example:client-ui:1.0.0"
    target: client
    scope: compile-only
```

Keep common initialization code in `src/main/java` and client-only imports in `src/client/java`. This does not imply the library is bundled. Check Loader `depends` separately; do not require a client-only mod globally on a dedicated server.

### Bundle a helper library inside your mod

```yaml
dependencies:
  - maven: "org.example:embedded-helper:1.0.0"
    kind: library
    scope: all
    include: true
```

Inspect the resulting JAR for the nested artifact and `jars[].file` metadata. The remote library still has to be published at a real fixed version. In legacy remapping profiles, let Loom's remapping publication tasks select the correct release artifact; do not distribute a dev-classifier JAR.

### Add Mixins with client/server boundaries

```yaml
fabric:
  mixins:
    - config: example.mixins.json
      environment: "*"
    - config: example.client.mixins.json
      environment: client
```

Create those actual JSON resource files. Put common mixins in `src/main/resources`. If a referenced config exists only under `src/client/resources`, use split client source sets and an appropriate client-only Mixin environment; the plugin refuses unsafe global-server references.

### Add a Class Tweaker (Minecraft 26.3)

```yaml
fabric:
  accessWidener: example.classtweaker
```

Create `src/main/resources/example.classtweaker` with `classTweaker v1 official` as its exact first line and valid class/member declarations for your Minecraft version. Run `gradle validateAccessWidener` if available and `gradle build`. The YAML does not create the declarations.

### Add a mod icon

```yaml
fabric:
  icon: "assets/example_mod/icon.png"
```

Or multiple PNG resolutions:

```yaml
fabric:
  icon:
    "32": "assets/example_mod/icon_32.png"
    "128": "assets/example_mod/icon_128.png"
```

All referenced files must be real PNGs under the correct resources tree; the size mapping's keys are positive integer pixel sizes. The actual images must be supplied by the developer.

### Put advanced Loader metadata in one place

```yaml
fabric:
  custom:
    "example:settings":
      enabled: true
      seed: 42
```

`custom` accepts JSON-compatible data, not executable YAML tags. For Loom-specific features such as injected interfaces, know the expected Fabric/Loom metadata shape and provide the corresponding compiled classes; adding a custom map alone does not synthesize interfaces.

## Building, running, testing, and using an IDE

### Frequently used tasks

Run these commands from the **consumer project directory** and replace `gradle` with its local Wrapper command if present:

| Command | Purpose | Network / side effects |
| --- | --- | --- |
| `gradle help` | Verify the settings-only Gradle project configures | May resolve missing plugin/artifacts on a cold cache; does not upload |
| `gradle tasks --all` | Inspect generated Gradle tasks | No publishing |
| `gradle compileJava` | Compile Java sources | May resolve dependencies |
| `gradle generateFabricModJson` | Generate Loader JSON from YAML | Local generated file only |
| `gradle validateFabricAmper` | Validate YAML and referenced resources | Local validation |
| `gradle validateAccessWidener` | Loom access-widener/class-tweaker validation when applicable | Local validation |
| `gradle test` | Run your consumer test suite | Local test execution |
| `gradle build` | Compile, test, package, validate the distribution | **No Modrinth/CurseForge/remote Maven upload** |
| `gradle verifyFabricAmperJar` | Check final archive/metadata integrity | Local verification |
| `gradle runClient` | Launch Minecraft client | **Interactive game process**; use isolated run directory |
| `gradle runServer` | Launch Minecraft dedicated server | **EULA and server data** are your responsibility |
| `gradle publishMavenJavaPublicationToFabricAmperLocalRepository` | Publish an API/Maven-enabled project locally | Writes only to consumer `build/` |

For Minecraft 26.3, the final distribution uses `jar`. For Minecraft 1.21.11 and earlier, the remapping profile uses `remapJar`. Do not assume the presence of a `build/libs` file proves Loader could initialize it on either game side; execute an isolated runtime when correctness requires it.

### Windows convenience commands: fabric-amper.bat

The repository root provides [`fabric-amper.bat`](fabric-amper.bat): a **Windows CMD wrapper for existing Gradle tasks**, not a different build system or publicly available installer. It does not replace the consumer's `settings.gradle.kts` / `mod.yaml` contract. Run with a compatible Java and Gradle installation. There is no producer `gradlew.bat`; the wrapper checks `FABRIC_AMPER_GRADLE`, a consumer wrapper, `GRADLE_HOME`, `PATH` and a locally cached Gradle 9.7.1 distribution. Use `doctor` to inspect the resolved environment.

From the **Fabric Amper producer directory**:

```bat
fabric-amper.bat doctor
fabric-amper.bat init example_mod
fabric-amper.bat init test_mode "Test Mode" mod --kotlin
fabric-amper.bat init strict_mod "Strict Mod" mod --splitEnvironmentSourceSets
fabric-amper.bat init strict_kotlin "Strict Kotlin" mod --kotlin --splitEnvironmentSourceSets
fabric-amper.bat init architectury_single "Architectury Single" mod --architectury
fabric-amper.bat init architectury_split "Architectury Split" mod --architectury --splitEnvironmentSourceSets
fabric-amper.bat init architectury_kotlin "Architectury Kotlin" mod --kotlin --architectury
fabric-amper.bat init example_api "C:\work\example_api" api
```

**Optional source-set isolation during `init`:** append `--splitEnvironmentSourceSets` to generate `fabric.loom.splitEnvironmentSourceSets: true` under `fabric:` for schema 1.1. Without it, source-set splitting remains disabled by default. The option supports `mod`, `api`, `--here`, Java and Kotlin. Combine it with `--kotlin` in **either order after the optional folder/profile arguments**; duplicated/misplaced switches are rejected. In addition to the common `src/main/java/org/example/ExampleMod.java` (or Kotlin `.kt`), split initialization creates `src/client/java/org/example/ExampleModClient.java` (or `src/client/kotlin/org/example/ExampleModClient.kt`) implementing `ClientModInitializer`, creates `src/main/resources` and `src/client/resources`, and registers `fabric.entrypoints.client: [org.example.ExampleModClient]` alongside `main`. Both the Java and Kotlin client examples compile in the split source set. `src/main` remains **common code**, not dedicated-server-only, and the flag does **not** set the entire mod's `fabric.environment` to `client`. Unflagged initialization creates neither a client source tree nor a client entrypoint. Older Fabric-only `init` output can still use schema 1: see the [historical reference](#historical-schema-1-reference).

**Architectury initialization (published v0.3.0 prerelease):** `--architectury` enables the common `architectury.enable: true` setting. For Fabric-only projects it integrates the Fabric API variant; NeoForge-only uses the NeoForge variant. Dual-target projects additionally generate `common/` with loader-neutral `ExampleCommon`, plus independent platform entrypoints under `fabric/` and `neoforge/`. Java or Kotlin sources are generated according to `--kotlin`. A fresh public v0.3.0 JitPack NeoForge + Architectury + Kotlin consumer built successfully; actual-game Architectury initialization still needs independent validation.

```yaml
fabric:
  loom:
    splitEnvironmentSourceSets: true
```

`init <id> [new_folder] [mod|api] [loader flags] [--kotlin] [--architectury] [--splitEnvironmentSourceSets]` creates `settings.gradle.kts`, `mod.yaml`, a local `schemas/mod.schema.json` snapshot, optional editor settings and a matching initializer. **Java is default**; `--kotlin` sets `settings.kotlin.enabled: true` and writes real Kotlin classes instead. NeoForge uses `@Mod`, not a Fabric `ModInitializer`. Flags follow the positional parameters and duplicated/misplaced flags are rejected. `--here` preserves unrelated tools but refuses conflicting YAML, Settings/build scripts or source trees; no overwrites. A missing `fabric-amper.schema.json` may be downloaded from GitHub `main` **only after matching the BAT's pinned SHA-256**; a changed or mismatched file is rejected. NeoForge/dual starters write explicit `schema: "1.1"`; Fabric-only BAT starters use their documented [compatibility layout](#historical-schema-1-reference), while manually written omitted-schema YAML is 1.1. v0.3.1 release-target generated Settings request JitPack `v0.3.1` (availability must be checked). The local editor schema is a snapshot, not automatically refreshed after plugin upgrades. `setup` was removed.

From the **consumer directory**, use the batch file's full path (unless its directory is on `PATH`):

```bat
"C:\path\to\Fabric Amper\fabric-amper.bat" validate
"C:\path\to\Fabric Amper\fabric-amper.bat" build
"C:\path\to\Fabric Amper\fabric-amper.bat" run
```

Or select a consumer explicitly with `-C` or `--project` **before the subcommand**. The `doctor` subcommand also accepts `doctor -C ".\httpmod"` / `doctor --project ".\httpmod"`; its path is relative to the current terminal directory. The duplicated form `doctor -C ".\httpmod" doctor` is tolerated:

```bat
"C:\path\to\Fabric Amper\fabric-amper.bat" -C "C:\work\example_mod" build
```

| Wrapper subcommand | What it does |
| --- | --- |
| `help`, `version`, `docs` | Display usage/version/document paths |
| `doctor`, `doctor --full`, `doctor --paths` | Local Java/Gradle checks plus configured JitPack coordinate (network availability not checked); optional Windows path/JDK hints |
| `doctor --fix --dry-run` | **No network or file/registry changes.** With a verified local PS1, preview JDK25/Gradle and missing consumer Settings/editor files; without one, show only the proposed PS1 download URL and stop |
| `doctor --fix` | **Explicit opt-in.** When missing, fetch the matching release's `fabric-amper-doctor-fix.ps1` into **`scripts/` beside the BAT**, verify its pinned SHA-256, then inspect/install missing JDK25/Gradle and repair missing consumer bootstrap/editor files |
| `init <id> [folder] [mod|api] [--mod-fabric] [--mod-neoforge] [--architectury] [--kotlin] [--splitEnvironmentSourceSets]` | Choose Fabric (default), NeoForge or both; Architectury adds `common/` to dual; Kotlin or Fabric split optional; no overwrite |
| `init <id> --here [mod|api] [loader flags] [--architectury] [--kotlin] [--splitEnvironmentSourceSets]` | Same choices in a compatible tools-only folder; refuse existing consumer scripts/YAML/sources |
| `migrate [--dry-run] [-D|--delete]` | Fetch and verify missing `scripts/fabric-amper-migrate.ps1` (and root schema if needed), then statically convert a supported Fabric mod; default preserves originals in `gradle_backup/`. `--dry-run` never downloads |
| `migrate -C "consumer_folder" [--dry-run] [-D|--delete]` | Select an existing mod from another directory; `-C`/`--project` also works before `migrate` |
| `tasks`, `validate` | Run `tasks --all` or the applicable platform validation(s) (`:fabric:validateFabricAmper` / `:neoforge:validateFabricAmper` for dual) |
| `diagnose`, `explain` | Run read-only consumer `fabricAmperDoctor` / `fabricAmperExplain` |
| `plan` | Build/verify the final JAR, then print `fabricAmperReleasePlan` metadata **without uploading** |
| `port 26.3` | Run `fabricAmperPortingReport -PfabricAmperPortTarget=26.3`; checklist only, no source rewrite |
| `build` | Validate the selected loader(s), run `build` and verify resulting loader JAR(s); dual dispatch includes both platforms |
| `verify`, `test`, `clean` | Verify the applicable loader JAR(s), run `test` or `clean` respectively |
| `run`, `server` | Single loader: `runClient` / `runServer`; dual: **require** `--mod-fabric` or `--mod-neoforge` to select matching client/server task; EULA remains user's decision |
| `publish local` | Explicit local API Maven publication inside the consumer's `build/fabric-amper/maven-repository/` |
| `publish maven`, `modrinth`, `curseforge`, `all` | Confirm with typed `PUBLISH`, locally validate and build, then invoke the selected remote task |

Consumer tasks check that exactly one YAML exists and reject a consumer `build.gradle(.kts)` or author-maintained `fabric.mod.json`. **Remote publication remains a real, side-effectful action**: `PUBLISH` is only an accidental-invocation guard, not verification of credentials, IDs or version uniqueness. `publish all` means the new `publishFabricAmper` aggregator, **not** Gradle's broader `publish`; multi-site partial success is possible. Consult [the publishing section](#explicit-maven-modrinth-and-curseforge-publishing) before authorizing any upload. For a shorter command cheat sheet, see [HUMAN_QuickGuide.md](HUMAN_QuickGuide.md).

### Migrating an existing Fabric Gradle mod (Windows)

The existing Windows `migrate` workflow is deliberately **Fabric v1 only**, converting a supported Groovy or Kotlin DSL project with one authored Fabric Loader JSON to explicit `schema: 1` and `settings.gradle.kts`. It preserves Java/Kotlin sources, Mixins and resources, but **does not automatically convert to schema 1.1/NeoForge**, run the old Gradle DSL, download dependencies, compile or launch Minecraft.

The Windows launcher can bootstrap the matching versioned `scripts/fabric-amper-migrate.ps1` helper and local schema from GitHub Releases if missing, and it verifies both against hashes embedded in that **same BAT** before execution. The v0.3.0 helper assets were published and hash-verified on 2026-11-17; the **new v0.3.1** BAT must use its own matching helper/schema hashes and its Release assets must be verified before download succeeds. Older BATs can still request older assets and plugin versions. Mismatched local files are rejected, not overwritten. `migrate --dry-run` is read-only; normal migration may create backups in `gradle_backup/`. From the old mod folder, for supported Kotlin or Groovy DSL:

```bat
"C:\tools\Fabric-Amper\fabric-amper.bat" migrate --dry-run
"C:\tools\Fabric-Amper\fabric-amper.bat" migrate
```

Or, without changing the current terminal directory:

```bat
"C:\tools\Fabric-Amper\fabric-amper.bat" migrate -C "C:\mods\my-old-mod" --dry-run
"C:\tools\Fabric-Amper\fabric-amper.bat" migrate --project "C:\mods\my-old-mod"
"C:\tools\Fabric-Amper\fabric-amper.bat" -C "C:\mods\my-old-mod" migrate
```

The `-C` / `--project` argument selects the **consumer directory** (paths are resolved against the terminal's current working directory), not the location of the BAT. Use it **either** before `migrate` **or** once among its options. Duplicate project selectors or duplicate flags are rejected. `migrate -C` does not create a new project; use `init` for new projects.

| Command | Result for the original Gradle/Loader files |
| --- | --- |
| `migrate --dry-run` | With locally verified helper and schema, validate and print candidate `mod.yaml`; if either is absent, **only preview its GitHub download**, without converting yet. **No file creation, deletion, movement, or download** |
| `migrate` | Auto-download/verify any missing helper and schema, then on successful conversion create `mod.yaml` + `settings.gradle.kts`, retaining original build script, optional old Settings file and `fabric.mod.json` in **`gradle_backup/`** |
| `migrate -D` or `migrate --delete` | Same conversion; after successful output creation remove the original build script, old Settings file and original Loader JSON instead of preserving them in `gradle_backup/` |
| `migrate -C "C:\mods\my-old-mod" --delete --dry-run` | Preview the **deletion mode**; still makes **no changes** |

For example, a recognized **Kotlin** `build.gradle.kts` can contain:

```kotlin
plugins { id("net.fabricmc.fabric-loom") version "1.17.21" }
version = "1.0.0+26.3"
dependencies {
    minecraft("com.mojang:minecraft:26.3")
    implementation("net.fabricmc:fabric-loader:0.20.1")
}
```

The same intent in **Groovy** `build.gradle` uses Groovy string/dependency syntax:

```groovy
plugins { id 'net.fabricmc.fabric-loom' version '1.17.21' }
version = '1.0.0+26.3'
dependencies {
    minecraft 'com.mojang:minecraft:26.3'
    implementation 'net.fabricmc:fabric-loader:0.20.1'
}
```

Both examples need an existing `src/main/resources/fabric.mod.json` with Fabric Loader `schemaVersion: 1`, matching mod ID/version and real referenced resources. They illustrate *recognized input syntax*, not complete tested migration projects. The converted YAML explicitly retains `schema: 1`, `product: fabric/mod`, old Loader fields and compatible Java/Kotlin settings. A **v0.3.1** release-target Settings script must request `dev.fabric-amper.settings:0.3.1` via `com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.3.1`. Verify the actual generated bytes and remote availability before building.

With the **default backup mode**, the result resembles:

```text
my-old-mod/
├── mod.yaml                              # generated
├── settings.gradle.kts                   # generated for Fabric Amper 0.3.1 prerelease target
├── gradle_backup/
│   ├── build.gradle.kts                  # or build.gradle
│   ├── settings.gradle.kts                # or settings.gradle; only if originally present
│   └── fabric.mod.json                    # old src/**/ Loader metadata
└── src/main/                              # original code and other resources retained
```

The converter refuses to overwrite an existing `gradle_backup` directory, `mod.yaml`, `mod.yml` or conflicting Settings/build scripts. It also rejects both Gradle DSL variants at once, missing/duplicate Loader JSON files, symlinks/junctions within scanned source paths, unexpected Gradle blocks/statements, non-literal expressions, unsupported plugins, dynamic dependency coordinates, unsafe paths and missing or incompatible resources. Recognized static `gradle.properties` variables may be substituted, but arbitrary Groovy/Kotlin expressions are **not** evaluated. Only a **supported single-root Fabric mod** is a candidate; custom tasks, complex multi-project builds, nonstandard plugins, `mavenLocal()`, custom repository/authentication logic, and unsupported source-set wiring require manual migration. For Kotlin projects, check the generated Kotlin/FLK settings and entrypoint adapters against the pinned plugin requirements before building.

Use `--dry-run` first, review the reported YAML fields and original files, then run ordinary `migrate` for a recoverable backup. The converter stages output and original-byte snapshots before moving files and attempts rollback after recoverable I/O failures; if rollback itself fails, follow the reported staging path for **manual recovery**. This is **not** a blanket guarantee against process termination or disk failure. No successful migration proves the new build compiles, produces identical artifacts, runs Mixins correctly, or starts the game.

After conversion, inspect `mod.yaml`, verify the original code/resources remain and the old Loader JSON is absent from `src/main/resources` or `src/client/resources`, then run the local, explicitly selected consumer validation:

```bat
"C:\tools\Fabric-Amper\fabric-amper.bat" -C "C:\mods\my-old-mod" validate
"C:\tools\Fabric-Amper\fabric-amper.bat" -C "C:\mods\my-old-mod" build
```

These **post-migration** commands do invoke Gradle and may need JDK 25, Gradle and network/cached dependencies. Test actual client/server behavior separately where applicable; review any server EULA yourself. Do not delete `gradle_backup/` until the converted mod has been fully checked. For compact commands, see [the Quick Guide](HUMAN_QuickGuide.md#windows-command-cheat-sheet).

### Windows `doctor --fix`: one-file GitHub Release bootstrap

The v0.3.1 release-target BAT requests the following version-matched helper URL when `doctor --fix` is explicitly invoked and its local helper is missing. The new release asset must exist and pass the BAT-pinned SHA-256 check before use; the independently verified helper publication described above belongs to **v0.3.0**, not v0.3.1:

```text
https://github.com/howShouldIChooseMyUsername/Fabric-Amper/releases/download/v0.3.1/fabric-amper-doctor-fix.ps1
```

The BAT obtains the doctor helper **only** when you explicitly run `doctor --fix` and the verified file is absent; the `v0.3.1` path derives from the release-target BAT's embedded version. The initial download uses a temporary file, pinned SHA-256 verification and a local `scripts/fabric-amper-doctor-fix.ps1` destination. A checksum-keyed request and cache-control headers reduce stale-asset risk; the **expected hash**, not server claims, is authoritative. The doctor helper itself is not a blanket JDK/Gradle download; any authorized repair can perform separate toolchain downloads. The `migrate` helper follows the same pinned policy.

If either helper is already present in **`scripts/`**, the BAT **does not download or overwrite it**: it verifies its hash before execution. A mismatched file, unavailable Release asset, failed checksum or unwritable BAT folder stops the requested operation without executing that helper. On a fresh BAT-only install, `doctor --fix --dry-run` and `migrate --dry-run` merely show missing-helper download plans and **never download**; rerun the normal action to bootstrap, then use `--dry-run` to preview actual migration. `migrate` bootstraps the schema and converter from the Release; `init` downloads a missing schema from the GitHub `main` branch with the same SHA-256 protection. Both still require the relevant toolchain and valid user project files for later tasks.

**Historical prerelease note:** The earlier `v0.1.2` and `v0.1.3` workflows are retained for context only. Their helper downloads, checksums and generated plugin versions do **not** represent the published `v0.3.0` artifacts. The verified v0.3.0 release evidence is dated 2026-11-17; match every helper to the **installed BAT version** and its own SHA-256-pinned GitHub Release assets, including v0.3.1 when available.

### Read-only developer diagnostics and release preview

The following **explicit Gradle tasks** are intentionally separate from `build`, `check`, and remote publishing; no new `mod.yaml` fields are necessary:

```powershell
gradle fabricAmperDoctor
gradle fabricAmperExplain
gradle fabricAmperPortingReport -PfabricAmperPortTarget=26.3
gradle fabricAmperReleasePlan
```

- **Doctor:** summarizes the selected Fabric profile, Java release, running JVM, selected Loom plugin ID, declared Fabric Loader/API and Kotlin/client settings. It labels unknown exact Loom compatibility rather than asserting a matrix PASS. A Windows path estimate is a heuristic, **not** proof that the IDE or Loom caches fit within a particular limit.
- **Explain:** displays authored Maven/Modrinth Gradle dependencies (scope/target/export/include), injected vs authored Fabric Loader `fabric.depends`, and *separately* Modrinth/CurseForge release relationships. It does **not** download artifacts or guess platform project IDs.
- **Porting report:** compares the current selected game profile with the requested target and suggests a **manual** change checklist. It does not modify Java/Kotlin sources, mapping-specific Mixins, YAML or resource files; compiler/game/runtime tests remain necessary.
- **Release plan:** depends on the final production JAR's verification and prints its SHA-256, size, exactly one root `fabric.mod.json` and effective Maven/Modrinth/CurseForge release data. It does **not** read token environment variables, show changelog bodies, access remote platforms, or upload anything. External project IDs/tags remain unverified. Expect local building/verification work if the JAR is not up to date.

Read the [source-linked improvement backlog](PROBLEMS.md) for remaining IDE GUI, Minecraft runtime, cold-cache Kotlin cost, Mixin transformation and real external upload limitations. **A diagnostic report is not equivalent to a full test pass or a release approval.**

### Running a server and the Minecraft EULA

Dedicated servers enforce Mojang's EULA separately from Fabric Amper. A first `runServer` may create an `eula.txt` in that run directory and refuse to complete startup until a person reviews and accepts the EULA. See the [official EULA](https://aka.ms/MinecraftEULA) before changing anything. Fabric Amper **never accepts it automatically**, and should not overwrite an existing server's world, account, or settings.

An isolated server test directory was previously observed with `eula=true` after user action, but **that is not by itself evidence of a successful complete server boot**. If you test server startup, check the actual Loader/game log rather than counting the EULA file as a runtime PASS.

### Configuration cache and editing YAML

Fabric Amper tracks the YAML and referenced resource contents to invalidate Gradle configuration cache when relevant inputs change. For a normal local build:

```powershell
gradle build --configuration-cache
gradle build --configuration-cache
```

The second run may reuse configuration if nothing material changed. Changing `mod.yaml`, removing a managed mirror, or editing referenced resources is designed to invalidate/recover correctly. Do **not** edit the generated mirror or replace `fabric.mod.json` manually.

Do not generalize configuration-cache support to authenticated **remote Maven publishing**. On the tested Gradle 9.7.1 native publishing path, explicitly configured `PasswordCredentials` prevent a real remote publish task from claiming configuration-cache STORE→HIT. A local `--dry-run` with skipped task actions is a separate, weaker test.

### IntelliJ IDEA

Open the **consumer root folder** as a Gradle project and select a JDK/Gradle environment compatible with its Minecraft profile. Do not create a `build.gradle.kts` simply to make the IDE happy. Look for the Gradle tasks, source sets, dependencies, and Loom run tasks. After editing `mod.yaml`, use **Reload All Gradle Projects** if automatic reloading is not enabled.

The project has **Gradle Tooling API/IDE-model evidence**, but full actual IntelliJ GUI **first import, manual synchronization, and automatic synchronization** have **not** all been signed off. Treat IDE import as a manually verifiable workflow, not a guaranteed green acceptance gate.

### Enable editor completion for YAML

The repository root contains [`fabric-amper.schema.json`](fabric-amper.schema.json), a **Draft-07 JSON Schema**. It covers known top-level sections and the current publish fields. Copy it to `schemas/mod.schema.json` inside the consumer directory, then put both editor directives at the **top** of your YAML (or run `init`, which generates them):

```yaml
# yaml-language-server: $schema=./schemas/mod.schema.json
# $schema: ./schemas/mod.schema.json
schema: "1.1"
product: fabric/mod
```

This is only a **header fragment**, not a complete valid `mod.yaml`: keep your real `mod:` and `minecraft:` mappings below it. The first comment is for YAML Language Server; IntelliJ IDEA recognizes the second `# $schema:` comment. Alternatively configure the YAML extension's `yaml.schemas` mapping or configure IntelliJ's **JSON Schema Mappings** for `mod.yaml`/`mod.yml` using the real schema location. Do not add a literal root YAML `$schema:` field: it is not part of the Fabric Amper schema. The schema is an **editor aid**, not the sole validation authority; path containment, cross-field constraints, and other runtime checks still happen in the plugin.

## Creating an API/library and publishing locally

Fabric Amper has three product identifiers:

| `product` | Meaning |
| --- | --- |
| `fabric/mod` | Ordinary Fabric mod; no automatic Java-library Maven publication unless explicitly configured by `publish.maven` |
| `fabric/api` | A real Fabric library mod; applies `java-library` and `maven-publish` and configures a local Maven publication |
| `fabric/library` | **Exact alias** of `fabric/api`, with the same publication behavior |
| `neoforge/mod` / `neoforge/api` | NeoForge mod or publishable API; NeoForge-specific metadata comes from `neoforge:` |

An API/library is still a **Fabric mod** and contains Loader metadata. It is not a plain Java-only JAR with Fabric stripped out.

### API producer example

```yaml
schema: "1.1"
product: fabric/api
mod:
  id: example_api
  version: "1.0.0"
  group: org.example
  archivesName: example-api
  name: Example API
minecraft:
  version: "26.3"
fabric:
  environment: "*"
  entrypoints:
    main: [org.example.ExampleApi]
  mappings: mojang
  loader: "0.20.1"
  api: "0.165.2+26.3"
settings:
  java:
    release: 25
    sourcesJar: true
repositories:
  - fabric
  - mavenCentral
```

Create `src/main/java/org/example/ExampleApi.java` implementing the declared entrypoint (or adjust the entrypoint to your real class). Then, from the API consumer root:

```powershell
gradle build
gradle publishMavenJavaPublicationToFabricAmperLocalRepository
```

The local Maven repository is:

```text
build/fabric-amper/maven-repository/
└── org/example/example-api/1.0.0/
    ├── example-api-1.0.0.jar
    ├── example-api-1.0.0-sources.jar   # if sourcesJar is enabled
    ├── example-api-1.0.0.pom
    └── example-api-1.0.0.module
```

`mod.group` becomes `groupId`, `mod.archivesName` (or `mod.id`) becomes `artifactId`, and `mod.version` becomes the Maven version. The plugin uses Loom/Java's actual outgoing components; it must publish the **final remapped distribution** on legacy targets, not an intermediary development JAR. A default API build is **not** an external publication.

### Downstream API dependencies

A second consumer can point its own `repositories` to the actual published repository and declare a fixed dependency:

```yaml
dependencies:
  - maven: "org.example:example-api:1.0.0"
    scope: all
```

The API product maps `exported: true` to a public compile-scope dependency for its Maven/Gradle consumers; `scope: all` without `exported` is an internal runtime-scope dependency to downstream consumers; `runtime-only` stays runtime; `compile-only` and `test-dependencies` are not published as required Maven transitive dependencies. `target: client` is not global public API. The Gradle `.module` file retains richer variant information than a plain Maven POM, and Fabric Amper cannot infer broken transitive metadata from an upstream artifact.

## Explicit Maven, Modrinth, and CurseForge publishing

**Publishing is opt-in and potentially irreversible.** Neither `gradle help` nor `gradle build` uploads your mod. Only explicitly invoking a configured publishing task can transfer artifacts. Platform IDs, tokens, version tags, and destination rights must come from the actual owner—do not guess them.

### Remote Maven release (new syntax)

```yaml
mod:
  id: example_mod
  version: "1.0.0+26.3"
  group: org.example
publish:
  maven:
    url: "https://maven.example.org/releases"
    credentialsFromEnv: [MAVEN_USERNAME, MAVEN_PASSWORD]
```

This is a **fragment**: the complete document also needs `product` and `minecraft`; root `schema` is optional. The `url` must be HTTPS without inline credentials, query, or fragment; `credentialsFromEnv` contains **exactly two distinct environment variable NAMES**, username first and password/token second. The values themselves are supplied by your process environment and must never appear in YAML, a committed script, or output examples.

Publish to this configured destination **only when you authorize the upload**, for example:

```powershell
gradle publishFabricAmperMaven
```

This is an actual remote publication task, **not** a dry-run demonstration. It requires network connectivity, correct account permissions and present credentials; do not append `--offline` expecting localhost or remote HTTPS PUTs to work. On Gradle 9.7.1, native explicit Maven `PasswordCredentials` are currently **not configuration-cache compatible for real publishing**. A `--dry-run --configuration-cache` STORE/HIT test only proves task-graph reuse with all actions skipped, not successful authenticated uploads.

**Legacy API/library-only alternative:** `publishing.repository` continues to support `url`, `usernameEnv`, and `passwordEnv` for `fabric/api` / `fabric/library`:

```yaml
publishing:
  repository:
    url: "https://maven.example.org/releases"
    usernameEnv: MAVEN_USERNAME
    passwordEnv: MAVEN_PASSWORD
```

Do not combine `publishing.repository` with `publish.maven`; both configure the same remote Maven destination and are mutually exclusive. For ordinary `fabric/mod`, use the newer `publish.maven`.

### Publish your mod on Modrinth

```yaml
publish:
  modrinth:
    projectId: "REPLACE_WITH_EXISTING_MODRINTH_PROJECT_ID"
    tokenEnv: MODRINTH_TOKEN
    releaseType: release
    versionName: "Example Mod 1.0.0"
    versionNumber: "1.0.0+26.3"
    gameVersions: ["26.3"]
    loaders: [fabric]
    environment: client_and_server
    changelog: |
      Initial release.
      Requires the compatible Fabric Loader.
    dependencies:
      - projectId: "REPLACE_WITH_REAL_DEPENDENCY_PROJECT_ID"
        type: required
```

The example project IDs are **not real release identifiers**. `releaseType` is `release`, `beta`, or `alpha` (default `release`). The names, fixed version number, supported Minecraft version strings, and loader list may be supplied explicitly or inherit from [`publish.common`](#share-release-fields-with-publishcommon). The `environment` enum is Modrinth-specific; `fabric.environment: "*"` normally maps to `client_and_server`, `client` to `client_only`, and `server` to `server_only`, but authors must correct a different required-installation policy explicitly. `dependencies[]` is a list of `{projectId, type}` with types `required`, `optional`, `incompatible`, or `embedded`—these are **Modrinth release relationships**, not Gradle classpath dependencies.

The explicit remote task is `gradle publishFabricAmperModrinth`. The implementation targets the Modrinth **Create Version** API, and tokens are read from the named environment variable only when the task executes. **No successful external Modrinth API upload has been claimed as acceptance evidence**.

### Publish your mod on CurseForge

```yaml
publish:
  curseforge:
    projectId: 123456
    tokenEnv: CURSEFORGE_TOKEN
    releaseType: release
    displayName: "Example Mod 1.0.0"
    gameVersionNames: ["26.3", "Fabric", "Client", "Server"]
    changelog: "Initial release."
    changelogType: markdown
    manualRelease: false
    relations:
      - slug: "REPLACE_WITH_REAL_PROJECT_SLUG"
        projectId: "1234567"
        type: requiredDependency
```

`projectId` at the release destination is a **positive integer** for an existing CurseForge project. `relations[].projectId`, when present, is a **numeric string**. Both IDs above are examples; check real projects before publishing. `gameVersionNames` are human-facing CurseForge tags, not numeric enum values. `publish.common.gameVersions` can provide default **string** tags here. Optional CurseForge-specific `gameVersions` is an array of *real positive integer upload-game-version IDs*, not strings like `"26.3"`:

```yaml
publish:
  curseforge:
    projectId: 123456
    tokenEnv: CURSEFORGE_TOKEN
    gameVersions: [12345]
```

That `12345` is a **placeholder**; supply an actual verified CurseForge author-upload version ID when opting into `gameVersions`. The permitted relation types are `embeddedLibrary`, `incompatible`, `optionalDependency`, `requiredDependency`, and `tool`. Changelog format is `text`, `markdown`, or `html`. Explicit remote task: `gradle publishFabricAmperCurseForge`. The author upload endpoint differs from the public CurseForge catalog API, and **no successful external CurseForge upload has been claimed**.

### Share release fields with `publish.common`

Schema **1.1** supports an optional **`publish.common`** mapping shared by explicitly configured Modrinth and CurseForge destinations. A platform's explicitly authored field **wins** over the inherited value. `common` by itself is *not* a destination; it must be paired with `modrinth` and/or `curseforge`. It never creates uploads, credentials or platform IDs. The existing `publish.maven` and legacy `publishing.repository` are independent and do **not** inherit this platform release metadata.

This is a **full schema 1.1 YAML example**, excluding the required real Java initializer source. All IDs and the local `CHANGELOG.md` path must be replaced or created before any upload:

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
  loader: "0.20.1"
  api: "0.165.2+26.3"
  entrypoints:
    main: [org.example.ExampleMod]
publish:
  common:
    releaseType: release
    name: "Example Mod 1.0.0"
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
    environment: client_and_server
  curseforge:
    projectId: 123456
    tokenEnv: CURSEFORGE_TOKEN
    gameVersionNames: ["26.3", "Fabric", "Client", "Server"]
    changelogType: markdown
```

The full example explicitly pins quoted schema 1.1, though omission would select the same grammar in v0.3.0. The release-site project IDs are placeholders and `CHANGELOG.md` must exist in the consumer root. Nothing above uploads until a publish task is explicitly invoked.

| Common key | Modrinth output | CurseForge output |
| --- | --- | --- |
| `releaseType` | `releaseType` | `releaseType` |
| `name` | `versionName` | `displayName` |
| `gameVersions` (strings) | `gameVersions` | `gameVersionNames` (**text tags**, not integer IDs) |
| `changelog` or `changelogFile` | Same logical release notes | Same logical release notes |
| `dependencies[]` | `dependencies[]` with project ID and Modrinth type | `relations[]` with slug, optional project ID and CurseForge type |

Common `dependencies[]` items use `slug` (CurseForge relation slug, and **fallback** Modrinth project identifier), optional `modrinthId`, optional `curseforgeId` (a **positive numeric string**), and `type` = `required`, `optional`, `incompatible`, or `embedded`. These types map deterministically:

| Common type | Modrinth type | CurseForge relation type |
| --- | --- | --- |
| `required` | `required` | `requiredDependency` |
| `optional` | `optional` | `optionalDependency` |
| `incompatible` | `incompatible` | `incompatible` |
| `embedded` | `embedded` | `embeddedLibrary` |

Platform-specific **`modrinth.dependencies[]`** or **`curseforge.relations[]`** can *override* inherited items by matching the effective Modrinth project ID or CurseForge slug and *append* new items. An explicit empty list (`dependencies: []` or `relations: []`) **clears inherited items** for that platform. Platform-specific `releaseType`, `versionName` / `displayName`, `gameVersions` / `gameVersionNames` also replace their corresponding shared field. Platform-specific `changelog` or `changelogFile` replaces the **entire inherited notes source** so the alternative common form does not cause an accidental conflict.

**Important differences:** Modrinth `gameVersions` are game-version *strings*; CurseForge `gameVersionNames` are release-page *tags*, while CurseForge `gameVersions` contains integer author-upload IDs and is **never inferred** from common. `common.gameVersions` will not automatically add tags like `Fabric`; provide CurseForge `gameVersionNames` explicitly if required. Platform project IDs and `tokenEnv` are *always* specified within each destination. Do not assume Modrinth and CurseForge slugs/IDs correspond to the same project without checking; supply `modrinthId` and `curseforgeId` when they differ. Never infer relations from `fabric.depends` or Maven coordinates.

### Release notes from a file instead of inline text

Both platforms support **one of** `changelog` **or** `changelogFile`, never both:

```yaml
publish:
  modrinth:
    projectId: "REAL_PROJECT_ID"
    tokenEnv: MODRINTH_TOKEN
    changelogFile: "release-notes.md"
```

Place `release-notes.md` **inside the consumer project root**. The plugin disallows absolute paths, `..` traversal, and symlink escapes. An inline `changelog` can contain multiple lines; multiline release notes are supported by the current schema.

### Which publish task should I run?

| Task | Upload scope / destination |
| --- | --- |
| `gradle build` | **Build only**; no remote publish |
| `gradle publishMavenJavaPublicationToFabricAmperLocalRepository` | Explicit project-local API/Maven publication under `build/` |
| `gradle publishFabricAmperMaven` | Explicit configured **remote Maven** |
| `gradle publishFabricAmperModrinth` | Explicit **Modrinth** release |
| `gradle publishFabricAmperCurseForge` | Explicit **CurseForge** release |
| `gradle publishFabricAmper` | All **explicitly configured** Fabric Amper remote destinations |
| `gradle publish` | Gradle's standard Maven publication tasks and, where integrated, selected platform publication tasks; **not** identical in side effects to `publishFabricAmper` |
| `gradle publishToMavenLocal` | Explicitly writes the user-wide Maven local repository; **avoid for isolated development** |

Do not run the all-target tasks to test YAML validity. They can create real releases, and partial success across several platforms is possible: there is **no cross-platform transaction** or automatic idempotent retry. Validate with `gradle help` / `build` and the resulting archive first. Every actual remote upload requires explicit approval/intent, correct credentials, and a compatible network environment.

## Complete field reference

This is the **schema 1.1** field reference for Fabric, NeoForge, and dual-loader consumers. The current [`fabric-amper.schema.json`](fabric-amper.schema.json) provides Draft-07 editor completion. Cross-field, real-resource and runtime constraints remain enforced by the plugin even when an editor misses them. Previous syntax appears only in the [historical schema 1 reference](#historical-schema-1-reference).

### Root document

| Key | Type | Required | Meaning |
| --- | --- | --- | --- |
| `schema` | Quoted string `"1.1"` | No | Omitting this key also selects schema 1.1 in v0.3.0 |
| `product` | Fabric/NeoForge `mod` or `api`, or one target per loader in an array | Yes | `fabric/library` aliases `fabric/api`; dual with Architectury creates `:common` |
| `mod` | Mapping | Yes | Shared project identity, metadata, and archive identity |
| `minecraft` | Mapping | Yes | Fixed Minecraft version; Loader versions are in platform sections |
| `settings` | Mapping | No | Shared Java/Kotlin options |
| `architectury` | Mapping | No | Platform API integration; dual shared module when enabled |
| `fabric` | Mapping | For Fabric target | Fabric Loader, Loom, Loader metadata, resources and dependency overrides |
| `neoforge` | Mapping | For NeoForge target | NeoForge version, ModDevGradle, Loader metadata and dependency overrides |
| `repositories` | List | No | Repository names or custom HTTPS Maven repositories used to resolve project dependencies |
| `dependencies` | List | No | Main/client compile/runtime dependencies, API exposure, JAR-in-JAR |
| `test-dependencies` | List | No | Test-only dependency coordinates and scopes |
| `publishing` | Mapping | No | Legacy API/library-only Maven remote configuration |
| `publish` | Mapping | No | New explicit Maven/Modrinth/CurseForge release destinations, with optional platform `common` |

There is **no** `build`, `gradle`, `plugins`, `fabric.mod.json`, or `loomVersion` top-level YAML property. Unknown keys are errors, not ignored extensions.

### Shared `mod` identity and platform metadata keys

| Key | Type / default | Notes |
| --- | --- | --- |
| `mod.id` | Required string | Fabric allows lowercase ASCII, digits, underscore and hyphen; NeoForge requires letters, digits and underscore only |
| `mod.version` | Required nonblank string | Becomes the Gradle project version **and** loader metadata version |
| `mod.group` | Optional Maven group | Required for `fabric/api`, `fabric/library`, or explicit `publish.maven` |
| `mod.archivesName` | Optional string; defaults to `mod.id` | JAR basename and Maven artifact ID; do not use filesystem traversal |
| `mod.name` | Optional nonblank string | Display name |
| `mod.description` | Optional string | Human-readable shared description |
| `mod.license` | Fabric: string or string array; NeoForge: nonblank string | NeoForge requires a single license string |
| `mod.authors` / `mod.contributors` | Arrays of name strings or `{name, contact}` maps | People metadata |
| `mod.contact` | String map | Recognizes `homepage`, `issues`, `email`; preserves other string-valued keys |
| `fabric.environment` | `"*"` (default), `client`, `server` | Fabric-only whole-mod side; not the same as a dependency's source set |
| `fabric.icon` | PNG path or size-to-PNG-path map | Real files must exist under a valid resources root |
| `fabric.provides` | List of Fabric mod IDs | No duplicates; do not claim the primary ID again |
| `fabric.entrypoints` | Name → list of class strings or `{adapter, value}` | A list for each entrypoint name; object entries require both keys |
| `fabric.languageAdapters` | Adapter ID → class | Do not redefine the built-in Kotlin adapter |
| `fabric.mixins` | List of strings or `{config, environment}` | Resource paths; environment can be `"*"`, `client`, `server` |
| `fabric.accessWidener` | Path string | `.classtweaker` for modern profile, `.accesswidener` in legacy remapping |
| `fabric.depends` | Mod ID → predicate string or OR array | Fabric Loader mod requirements; a mapping |
| `fabric.recommends` / `fabric.suggests` | Mod ID → predicate string or OR array | Optional Fabric Loader relationships |
| `fabric.conflicts` / `fabric.breaks` | Mod ID → predicate string or OR array | Fabric Loader incompatibility rules |
| `fabric.custom` | JSON-compatible mapping | Fabric Loader custom metadata; not Gradle configuration |
| `neoforge.dependencies[]` | Array of `{modId, type?, versionRange?, ordering?, side?, reason?}` | NeoForge TOML runtime metadata; `versionRange` is a Maven interval, not a Fabric predicate |
| `neoforge.mixins` / `neoforge.accessTransformers` | Resource-path arrays | Loader-specific config and access transformation resources |
| `neoforge.logoFile` | Image resource path | Used for NeoForge metadata |

### Minecraft version and loader-specific version keys

| Key | Type / default | Notes |
| --- | --- | --- |
| `minecraft.version` | **Required** string | Fixed version such as `"26.3"` or `"1.21.11"`; quote it |
| `fabric.loader` | **Required** fixed version string for Fabric products | No `latest.release`, `1.+`, `1.2.x` selectors |
| `fabric.api` | Optional fixed version string | Adds Fabric API to the appropriate Gradle configuration and Loader metadata |
| `fabric.mappings` | `mojang` by default; or `{type, version}` | Mojang required for 26.1+; Yarn must include a compatible version for supported legacy profiles |
| `neoforge.version` | **Required** fixed version string for NeoForge products | Example: `"26.3.0.101"` |
| `neoforge.modLoader` / `neoforge.loaderVersion` | `javafml` / Maven range | NeoForge runtime loader contract, default range `"[1,)"` |

The Yarn object form is:

```yaml
minecraft:
  version: "1.21.11"
fabric:
  loader: "0.20.1"
  mappings:
    type: yarn
    version: "YOUR_ACTUAL_PUBLISHED_YARN_VERSION"
```

The mapping version is deliberately a placeholder. Verify a genuinely published version for your exact Minecraft version before running Gradle.

### Shared `settings` and Fabric-specific settings

| Key | Default | Constraints |
| --- | --- | --- |
| `settings.java.release` | `25` | Java target release, integer; Minecraft 26.1+ uses 25 |
| `settings.java.sourcesJar` | `true` | Generate a source JAR |
| `settings.kotlin.enabled` | `false` | Enable Kotlin compilation; Fabric Language Kotlin is platform-specific |
| `settings.kotlin.pluginVersion` | `"2.4.21"` | When enabled, must match bundled version |
| `settings.kotlin.languageVersion` | `"2.4"` | When enabled, must match pinned Kotlin language version |
| `fabric.kotlin.fabricLanguageKotlin` | `"1.14.2+kotlin.2.4.21"` | Fabric-specific Language Kotlin version when Kotlin is enabled |
| `fabric.loom.splitEnvironmentSourceSets` | `false` | Add separate Fabric client source sets |
| `fabric.resources.failOnExistingFabricModJson` | `true` | Must stay `true`; never replace authored metadata |

### Dependencies and repositories at a glance

| Key | Type | Meaning |
| --- | --- | --- |
| `repositories[]` | `fabric`, `mavenCentral`, `modrinth`, or `{maven: https-url}` | Dependency resolution sources |
| `architectury.enable` / `architectury.enabled` | Boolean (false by default) | Enable matching platform API for MC 26.3; dual adds `:common`; choose one alias |
| `architectury.version` | Optional fixed string | Default `22.0.5` (verified 2026-11-16); explicit version wins; requires enable=true |
| `dependencies[]` | Coordinate string, one-key shorthand, or full object | Shared by selected loaders unless a schema 1.1 object specifies `loader` |
| `fabric.gradleDependencies[]` / `neoforge.gradleDependencies[]` | Same dependency array shape | Add artifacts only to the selected platform's Gradle classpath |
| `fabric.repositories[]` / `neoforge.repositories[]` | Repository arrays | Resolve artifacts for a particular loader |
| `dependencies[].maven` | `"group:artifact:fixed-version"` | Cannot coexist with `modrinth` in one dependency object |
| `dependencies[].modrinth.project` | String | Modrinth **dependency repository** project ID/slug |
| `dependencies[].modrinth.version` | String | Fixed published version or version ID |
| `dependencies[].kind` | `library` or `mod` | Defaults by source kind |
| `dependencies[].scope` | `all`, `compile-only`, `runtime-only` | Default `all` |
| `dependencies[].target` | `main` or `client` | Default `main`; client requires supported split |
| `dependencies[].loader` | `fabric` or `neoforge`; object entries only, schema 1.1 | Optional: route root artifact dependency to one selected loader; omitted means shared |
| `dependencies[].exported` | Boolean | Default `false`; requires `scope: all` |
| `dependencies[].include` | Boolean | Default `false`; requires `scope: all` |
| `test-dependencies[]` | Similar coordinate/scope shapes | Never `exported`, `include`, or `target: client` |

**Architectury API convenience:** add this **top-level** YAML section (omit `version` for the current verified default):

```yaml
architectury:
  enable: true
  # version: "22.0.5"
```

The parser and plugin automatically register `https://maven.architectury.dev/` and the appropriate Architectury platform artifact for Minecraft 26.3. Fabric receives `dev.architectury:architectury-fabric:<resolved-version>` and a matching `fabric.mod.json` Loader dependency; NeoForge uses the NeoForge artifact. The plugin also supplies Fabric API `0.165.2+26.3` when `fabric.api` was omitted; an explicit API version is preserved. The default is the pinned `22.0.5`, never a dynamic/latest selector. Incorrect versions fail during dependency resolution and duplicate manual Architectury artifacts are rejected. Dual Architectury projects create `:common`, `:fabric` and `:neoforge`; single-platform projects receive the matching API only. Architectury is not silently embedded in the output JAR. Unsupported Minecraft versions fail early. See [official 26.3 setup](https://docs.architectury.dev/api/getting-started/setup/).

### New publishing fields

The `publish` object may contain `common`, `maven`, `modrinth`, and/or `curseforge`. `common` supplies release fields **only** to an explicitly present Modrinth and/or CurseForge target. It is **never an instruction to upload during a build**: publication requires an explicitly invoked task.

| Key | Type / default | Meaning |
| --- | --- | --- |
| `publish.common.releaseType` | `release`, `beta`, `alpha` | Default release type shared by configured Modrinth/CurseForge |
| `publish.common.name` | Optional single-line string | Default `modrinth.versionName` and `curseforge.displayName` |
| `publish.common.gameVersions` | Optional nonempty unique **string** list | Default Modrinth `gameVersions` and CurseForge `gameVersionNames` tags; not CF integer `gameVersions` |
| `publish.common.changelog` / `changelogFile` | Inline text **or** safe project-relative path | Shared release notes, at most one; platform-specific notes replace this source |
| `publish.common.dependencies[]` | `{slug, type, modrinthId?, curseforgeId?}` | Shared project relationships: `slug`, optional Modrinth ID, optional numeric-string CF ID, type `required/optional/incompatible/embedded` |
| `publish.maven.url` | Required HTTPS URL | Remote Maven repository without embedded credentials, query, or fragment |
| `publish.maven.credentialsFromEnv` | Optional **exactly two** distinct environment-variable names | Username variable and password/token variable |
| `publish.modrinth.projectId` | Required string | Actual existing Modrinth project |
| `publish.modrinth.tokenEnv` | Required env-variable name | Author-token variable, **not** the secret |
| `publish.modrinth.releaseType` | `release` (default), `beta`, `alpha` | Release channel |
| `publish.modrinth.versionName` | Optional single-line text | Human name for the release |
| `publish.modrinth.versionNumber` | Optional fixed string | Defaults from the mod's version |
| `publish.modrinth.gameVersions` | Optional nonempty unique string list | Defaults to selected Minecraft version |
| `publish.modrinth.loaders` | Optional nonempty unique string list | Defaults to `[fabric]` |
| `publish.modrinth.environment` | Optional Modrinth environment enum | Inferred conservatively from `fabric.environment` for Fabric builds unless overridden |
| `publish.modrinth.changelog` / `changelogFile` | Inline text **or** safe relative file path | At most one of the two |
| `publish.modrinth.dependencies[]` | `{projectId, type}` | Release-site project relationships; types `required`, `optional`, `incompatible`, `embedded` |
| `publish.curseforge.projectId` | Required positive **integer** | Existing CurseForge project, at most 2,147,483,647 |
| `publish.curseforge.tokenEnv` | Required env-variable name | Author-token variable |
| `publish.curseforge.releaseType` | `release` (default), `beta`, `alpha` | Release channel |
| `publish.curseforge.displayName` | Optional single-line text | Human release display name |
| `publish.curseforge.gameVersionNames` | Optional nonempty unique string list | Display tags such as `"26.3"`, `Fabric`, `Client` |
| `publish.curseforge.gameVersions` | Optional nonempty unique positive **integer** list | Author-upload version **IDs**, not Minecraft strings |
| `publish.curseforge.changelog` / `changelogFile` | Inline text **or** safe relative file | At most one |
| `publish.curseforge.changelogType` | `markdown` (default), `text`, `html` | Release notes format |
| `publish.curseforge.manualRelease` | Boolean, default `false` | Release review behavior |
| `publish.curseforge.relations[]` | `{slug, type, projectId?}` | Relation types `embeddedLibrary`, `incompatible`, `optionalDependency`, `requiredDependency`, `tool`; optional `projectId` is a **numeric string** |

The nine accepted `publish.modrinth.environment` values are:

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

### Legacy API/library publishing fields

The older `publishing.repository` setting is available to `fabric/api` and `fabric/library` only:

| Key | Type | Meaning |
| --- | --- | --- |
| `publishing.repository.url` | HTTPS URL | Remote Maven destination |
| `publishing.repository.usernameEnv` | Env-var name | Username variable |
| `publishing.repository.passwordEnv` | Env-var name | Password/token variable |

The last two names must either **both** be supplied or **both** be absent. Do not combine this legacy repository with `publish.maven`. Prefer `publish.maven` for new configurations, especially `fabric/mod`.

## Troubleshooting and diagnostics

Fabric Amper prints diagnostic codes `FA001`–`FA015` for its own strict YAML, metadata, and wiring checks. A generic Gradle, network, Fabric Loader, or Java failure **may not** have an FA code. Where the parser can, it provides the file, line/column and YAML path; it does not silently ignore invalid input.

| Code | Typical cause | How to fix it |
| --- | --- | --- |
| **FA001** | No `mod.yaml`/`mod.yml`, or both files exist | Keep exactly one YAML configuration at the consumer root |
| **FA002** | Invalid YAML: duplicate keys, anchors/aliases, arbitrary tags, multi-document input, invalid UTF-8 | Use a single plain UTF-8 mapping document without aliases or merge keys |
| **FA003** | Unknown keys, invalid types, missing required values, invalid fixed version or publishing data | Match the exact reported YAML path to the schema/decoder; do not guess fields |
| **FA004** | Yarn mappings with Minecraft 26.1+ | Use `fabric.mappings: mojang` |
| **FA005** | Java release/JDK incompatible with selected modern Minecraft profile | For Minecraft 26.3 select `settings.java.release: 25` and run Gradle on JDK 25+ |
| **FA006** | Kotlin language adapter/version mismatch | Enable Kotlin with the bundled pinned plugin and FLK versions, or remove Kotlin adapter entrypoints |
| **FA007** | Unsupported `include`/`exported` with non-`all` scope | Set `scope: all` or remove the incompatible flag |
| **FA008** | Author-maintained `src/main/resources/fabric.mod.json` or `src/client/resources/fabric.mod.json` | Move supported metadata into `mod.yaml`; remove duplicate authored JSON |
| **FA009** | Invalid mod ID/entrypoint or missing/invalid Mixin, PNG, Class Tweaker resources | Fix the named metadata and the real resource path/contents |
| **FA010** | Conflicting or unresolved Loom/KGP plugin classpath | Apply only Fabric Amper's Settings plugin; resolve the specified marker/binary |
| **FA011** | A declared Maven/Modrinth artifact genuinely cannot be found | Confirm the fixed coordinate and configured resolution repositories; distinguish TLS/auth/offline failure |
| **FA012** | Conflicting declarations of one dependency coordinate | Consolidate repeated coordinates and their scopes |
| **FA013** | Final JAR contains zero or multiple root `fabric.mod.json` files | Check packaging and source resources; let the plugin verify exactly one |
| **FA014** | Client-targeted dependency with no supported split or illegal include/export | Use split source sets and a valid client scope/side |
| **FA015** | Configuration-time metadata/resource mirror missing or inconsistent | Allow regeneration; inspect errors when repeated cache recovery fails |

### Plugin marker cannot be resolved

If Gradle cannot resolve `dev.fabric-amper.settings:0.3.1` **before** it reads the YAML, this is not necessarily a parser bug. For the v0.3.1 target, check `pluginManagement.repositories` (including `https://jitpack.io`), the exact `resolutionStrategy.eachPlugin` redirect (`com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.3.1`), **whether that prerelease tag and JitPack artifact actually exist**, network and JDK compatibility. The separately verified **v0.3.0** JitPack consumer is historical evidence and cannot establish v0.3.1 availability. `fabric-amper.bat setup` was removed. The local G29 producer repository is only for isolated development. Gradle Plugin Portal publication is not established.

### The YAML parses but the build rejects it

**Generic YAML validity is weaker than Fabric Amper validity.** YAML alias/anchor/tag support, duplicate keys, unknown root keys, null in typed fields, dynamic Gradle versions, incompatible scope flags, and unsafe source/resource paths are deliberately rejected. Start from the [quick start](#five-minute-quick-start), add one feature at a time, and inspect the reported code and YAML path.

### An icon, Mixin, or Class Tweaker is not found

YAML names **files**, not content. Verify case-sensitive resource paths and ensure the actual file exists inside the allowed source roots. Modern Class Tweakers must have the precise `classTweaker v1 official` header. A client-only resource cannot silently be treated as a common/server resource.

### Everything compiles, but the dedicated server fails

Compiling does not guarantee side safety. Check `fabric.environment`, client-only entrypoints, client-only Mixin environments, split source sets, and `fabric.depends` requirements. Avoid pulling client-only types into common classes. A server first run may stop for Minecraft EULA review; this is **separate** from Fabric Amper's build.

### A release task says credentials are missing

Check the **environment variable names**, not their secret values in the YAML. A real Maven publish requires both named variables if configured; Modrinth/CurseForge require tokens and valid pre-existing project IDs. A `--dry-run` can skip all task actions, so it is not an authentication or HTTP PUT test. Do not paste credentials into logs, `mod.yaml`, Gradle scripts, or GitHub issues.

## Verification and known limits

Verification evidence is tracked in [`QA_REPORT.md`](QA_REPORT.md), [`specs.md`](specs.md) §12 and the earlier acceptance-evidence index under `analysis/acceptance-evidence-2026-11-13/`. Earlier Fabric-only and v0.3.0 candidate records are retained; the subsequent **published GitHub v0.3.0 prerelease / JitPack remote consumer PASS** is recorded separately from source and TestKit evidence, and does not imply Minecraft runtime approval.

| Area | Recorded evidence / limitation |
| --- | --- |
| Gradle 9.7.1, JDK 25, later develop `clean build validatePlugins --offline` | **PASS** recorded; not a v0.3.0 public install or full runtime validation |
| Producer JUnit: prior baseline and v0.3.0 candidate | Previous **364 total: 307 PASS, 57 SKIP**; latest `clean build validatePlugins --offline` **371 total: 314 PASS, 57 SKIP, 0 FAIL, 0 ERROR**, with BAT routing and schema-default regressions; not a proof of remote install or actual game startup |
| Schema 1.1 NeoForge standalone + Fabric/NeoForge dual Java/Kotlin consumer builds | Selected real TestKit **PASS**, including dual Architectury, API/mod and independent-loader combinations |
| Schema 1.1 two-loader `:common` cache behavior | Steady-state HIT and stable JARs **PASS**; strict cold second-run HIT **NOT MET** once when `:common` JAR was first created |
| Settings-only consumers, generated metadata and JAR checks | Selected real TestKit consumers **PASS** |
| YAML cache invalidation and damaged mirror/resource recovery | Selected consumer/parallel cache tests **PASS** |
| API/library real local Maven publication and downstream consumer | Selected real publication/compile tests **PASS** |
| Isolated modern client Loader main/client/tick and interface injection | Selected runtime tests **PASS** |
| IntelliJ GUI first import/manual and automatic synchronization | **Not fully observed/approved** |
| Full dedicated-server boot after an EULA is accepted | **Not established by the earlier EULA-refusal run** |
| Minecraft 1.21.11 full game runtime | **Not yet approved**; legacy remap and downstream compile separately verified |
| Authenticated native remote Maven HTTPS PUT and configuration-cache HIT **simultaneously** | **Not demonstrated**; Gradle native `PasswordCredentials` CC incompatibility recorded |
| External Modrinth/CurseForge actual release | **Not attempted**; no outside-platform upload authorized |

The **57 skipped tests are not passing tests**, and the combined Fabric G1–G30 / NeoForge NF shipping gates remain **NOT FULLY APPROVED**. The 2026-11-13 `build/libs/fabric-amper-0.1.0.jar` artifact (315,122 bytes, SHA-256 `13022B24ADE7C9809400FC6AEBE99E7751783A9DAE7EF8B8C53780FE70B698AD`) is **historical only**, not the current plugin or a v0.3.0 checksum. Public v0.3.0 JitPack resolution and remote consumer build passed separately; game runtime and IntelliJ GUI acceptance are still unapproved.

The separate updated `fabric-amper.schema.json` underwent Draft-07 and selected positive/negative regression checks. That validation is useful for editor completion, but the plugin is the final authority for side-safety, resource existence, real dependency resolution, and runtime Loader predicates.

### Important operational boundaries

- **Do not expect the plugin on the public Plugin Portal** until a public marker is independently confirmed. The local `G29` Maven repository is for development only.
- Disabling `settings.kotlin.enabled` does **not** currently guarantee zero download cost for KGP artifacts on a cold plugin classpath.
- A Gradle Tooling API model is not equivalent to an observed successful IntelliJ GUI import and reload.
- `gradle build` does not establish real Minecraft client/server or platform upload acceptance.
- The server EULA is a human legal decision; Fabric Amper does not and must not auto-accept it. A prior isolated `eula=true` observation alone does not prove full boot.
- An authenticated native remote Maven publish on Gradle 9.7.1 is not configuration-cache compatible in the tested setup; `--offline` also blocks loopback HTTPS publication. Avoid claiming `--dry-run` is a successful upload.
- The schema cannot express all runtime conditions (existing files, symlink containment, group/predicate consistency, and publication authorization).

## Frequently asked questions

**Do I need a consumer `build.gradle.kts`?** No. You need `settings.gradle.kts` to apply the single Settings plugin, and one `mod.yaml`/`mod.yml` for its content. The **Fabric Amper producer itself** has a build script because the plugin must be built; that is not a consumer requirement.

**Why is there still `settings.gradle.kts`?** Because Gradle must first resolve the Fabric Amper plugin. The YAML controls the Fabric project *after* that bootstrap, not Gradle's plugin-resolution engine.

**Can I keep my old `src/main/resources/fabric.mod.json`?** No. Migrate its relevant fields under `mod:`, then remove the authored JSON. Otherwise Fabric Amper deliberately rejects it with FA008 rather than overwriting or duplicating it.

**Can I rename `mod.yaml` to `mod.yml`?** Yes. Keep only one of them at the consumer root.

**Can the YAML contain Java/Kotlin/Mixin code?** No. It names entrypoint classes and resource paths; you still author actual source, Mixin files, Class Tweaker declarations, images, and assets.

**Is `fabric/library` different from `fabric/api`?** No. `fabric/library` is an exact alias for the API product, including its Java-library/Maven publication behavior. It still produces a Fabric mod JAR.

**Can a normal `fabric/mod` publish to Maven?** Yes, with an explicit `publish.maven` block and a nonempty `mod.group`. The older `publishing.repository` form is reserved for API/library products.

**Are `exported: true` and `include: true` interchangeable?** No. Exported dependencies appear in a library's public compilation surface; included dependencies are physically nested into the built mod JAR. A dependency may require careful handling of both, but they serve different purposes.

**Why do `dependencies` and `fabric.depends` both exist?** Gradle dependencies control build classpaths and packaging; Loader dependencies control mod installation/runtime requirements. NeoForge uses `neoforge.dependencies[]` for its loader metadata. None is a mechanical substitute for another.

**Does `gradle build` upload my release?** No. Use the specific explicit `publishFabricAmper...` task only when you intentionally wish to publish and have valid credentials/project metadata.

**Can I use a Modrinth project ID for CurseForge?** No. The two release platforms and Maven repository resolution use independent identifiers. Fabric Amper never invents cross-platform relationships.

**How do I get editor suggestions?** Newly created `fabric-amper.bat init` projects include `schemas/mod.schema.json` and both `# yaml-language-server: $schema=./schemas/mod.schema.json` and IntelliJ's `# $schema: ./schemas/mod.schema.json` comments. For manually created projects, copy [`fabric-amper.schema.json`](fabric-amper.schema.json) into that location or configure IntelliJ JSON Schema Mapping. These are YAML comments; a literal `$schema:` YAML **data key** is invalid. The schema copy represents the producer version when the project was created; after upgrading Fabric Amper, deliberately refresh the local schema snapshot for updated editor completions.

**Can I change Minecraft from 26.3 to 1.21.11 by changing only one string?** Account for the Java release, remapping profile, access-widener format, mappings, and dependency/game compatibility. The plugin selects the profile, but it cannot promise that third-party mods or game code remain compatible.

**Does the plugin accept Minecraft's EULA for me?** No. Read the official EULA and make your own decision in the specific isolated server's `eula.txt`. Do not share a game server directory with automated probe builds.

**Can I paste a token directly into YAML for quick testing?** No. Use only the *name* of an environment variable in `tokenEnv` or `credentialsFromEnv`; keep the secret out of files, screenshots, commits, and logs.

## Historical schema 1 reference

This is the **single legacy Fabric-only syntax reference** for existing consumers and the output of the Windows Fabric-only `init` and `migrate` compatibility workflows. The BAT `init` with Fabric alone (with no loader flag **or** explicit `--mod-fabric`) still generates **explicit `schema: 1`**; `migrate` also pins 1 so converted projects keep their original field layout. For a hand-written new project, use the [schema 1.1 quick start](#five-minute-quick-start). **Omitting `schema` in v0.3.0 selects 1.1**; it does not preserve the old layout.

This complete example is Fabric schema 1. Its declared Java initializer must exist in `src/main/java/org/example/ExampleMod.java` and implement `ModInitializer`:

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

The legacy keys map to schema 1.1 as follows; their values and resource requirements retain their own documented constraints:

| Explicit schema 1 | Schema 1.1 |
| --- | --- |
| `minecraft.loader`, `minecraft.fabricApi`, `minecraft.mappings` | `fabric.loader`, `fabric.api`, `fabric.mappings` |
| `mod.environment`, `mod.entrypoints`, `mod.languageAdapters` | `fabric.environment`, `fabric.entrypoints`, `fabric.languageAdapters` |
| `mod.icon`, `mod.mixins`, `mod.accessWidener`, `mod.provides` | `fabric.icon`, `fabric.mixins`, `fabric.accessWidener`, `fabric.provides` |
| `mod.depends`, `mod.recommends`, `mod.suggests`, `mod.conflicts`, `mod.breaks` | `fabric.depends`, `fabric.recommends`, `fabric.suggests`, `fabric.conflicts`, `fabric.breaks` |
| `mod.custom` | `fabric.custom` |
| `settings.loom.splitEnvironmentSourceSets`, `settings.resources.failOnExistingFabricModJson` | `fabric.loom.splitEnvironmentSourceSets`, `fabric.resources.failOnExistingFabricModJson` |
| `settings.kotlin.fabricLanguageKotlin` | `fabric.kotlin.fabricLanguageKotlin` (shared Kotlin enable/version stay under `settings.kotlin`) |
| `repositories`, `dependencies`, `test-dependencies` | **Same root arrays** in both versions; never changed from a map to a list in 1.1 |

Schema 1 supports only Fabric `product` strings (`fabric/mod`, `fabric/api`, `fabric/library`). It cannot use loader blocks, NeoForge products, or multi-loader `product` arrays. Version predicates for legacy `mod.depends` are still a **mod-ID → predicate mapping**; artifact dependencies are a separate **array** of Maven/Modrinth definitions. When upgrading, move Loader fields into `fabric`, keep the root arrays and common identity fields under `mod`, and validate with `gradle validateFabricAmper` before building. Only perform migration when prepared to update entrypoint/resource paths and retest the actual game.

## References and contributing

### Fabric Amper project sources

- [`README.md`](README.md) — current feature contract and development state.
- [`fabric-amper.schema.json`](fabric-amper.schema.json) — editor schema for v1 and v1.1 (Draft-07), including supported `publish` keys.
- [`FabricAmperSKILL.md`](FabricAmperSKILL.md) — companion AI-focused intent-to-YAML guide.
- [`specs.md`](specs.md) — original implementation contract and G1–G30 acceptance gates.
- [`QA_REPORT.md`](QA_REPORT.md) — recorded real builds, runtime results, and unresolved shipping gates.
- `src/main/kotlin/dev/fabricamper/config/ModYamlDecoder.kt` and `ModYamlValidator.kt` — implementation of strict YAML parsing and semantics.

### Official guidance and reference projects

The guide's user-focused structure follows [GitHub's documentation on what a README should explain](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-readmes): what the project does, why it is useful, how to get started, where to get help, and how to contribute. Examples were compared with [Gradle](https://github.com/gradle/gradle/blob/master/README.md), [Fabric Loom](https://github.com/FabricMC/fabric-loom), [uv](https://github.com/astral-sh/uv/blob/main/README.md), and [FastAPI](https://github.com/fastapi/fastapi/blob/master/README.md). The tutorial/how-to/reference separation also follows [Diátaxis](https://diataxis.fr/).

For technical details, use:

- [Fabric documentation](https://docs.fabricmc.net/) and [Loom tasks](https://docs.fabricmc.net/develop/loom/).
- [Fabric Loader `fabric.mod.json` reference](https://docs.fabricmc.net/develop/loader/fabric-mod-json).
- [Fabric Class Tweakers](https://docs.fabricmc.net/develop/class-tweakers/).
- [Gradle Maven Publish](https://docs.gradle.org/current/userguide/publishing_maven.html), [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html), and [Configuration Cache](https://docs.gradle.org/current/userguide/configuration_cache.html).
- [Modrinth Create Version API](https://docs.modrinth.com/api/operations/createversion/) and [CurseForge author upload support](https://support.curseforge.com/support/solutions/articles/9000197321).

For a change proposal, include the intended `mod.yaml`, the exact Gradle task, toolchain versions, the relevant FA diagnostic if any, and a minimal **settings-only** reproduction that does not modify anyone's real Minecraft data. Do not invent a license, support email, maintainer identity, or public install channel: none is asserted by this document. For the implementation's unresolved release requirements, refer to `QA_REPORT.md` rather than labeling a documentation example as tested.


