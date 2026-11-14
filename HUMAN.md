# Fabric Amper — The Human Guide

**Build Fabric mods with one small Gradle bootstrap and one declarative YAML file.**

Fabric Amper is a **Gradle binary plugin**, not a Minecraft mod. Instead of hand-maintaining a project-specific `build.gradle.kts` and `fabric.mod.json`, you write `settings.gradle.kts` and **exactly one** `mod.yaml` or `mod.yml`. Fabric Amper selects and configures Fabric Loom, Java, optional Kotlin, dependencies, source sets, generated Fabric Loader metadata, packaging, and optional publishing tasks.

> **Project status — development / not approved for a complete release.** Version `0.1.0` has been built and exercised with isolated consumers, but it is **not claimed to be publicly available on the Gradle Plugin Portal**. Some acceptance gates still need evidence (notably IntelliJ GUI import/synchronization, historical runtime cases, and authenticated remote Maven publishing with configuration-cache reuse). See [Verification and known limits](#verification-and-known-limits) before relying on it for production releases.

This is the **user-facing** guide. For source-of-truth field constraints, see [`fabric-amper.schema.json`](fabric-amper.schema.json); for the actual implementation, see `src/main/kotlin/dev/fabricamper/config/`; for implementation acceptance, see [`specs.md`](specs.md) and [`QA_REPORT.md`](QA_REPORT.md). Agents generating configurations should also read [`FabricAmperSKILL.md`](FabricAmperSKILL.md). For the **single English, Windows-first quick start and feature cheat sheet**, see [`HUMAN_QuickGuide.md`](HUMAN_QuickGuide.md); the former `HUMAN_QuickStart.md` is retained only as a link redirect.

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
12. [Creating an API/library and publishing locally](#creating-an-apilibrary-and-publishing-locally)
13. [Explicit Maven, Modrinth, and CurseForge publishing](#explicit-maven-modrinth-and-curseforge-publishing)
14. [Complete field reference](#complete-field-reference)
15. [Troubleshooting and diagnostics](#troubleshooting-and-diagnostics)
16. [Verification and known limits](#verification-and-known-limits)
17. [Frequently asked questions](#frequently-asked-questions)
18. [References and contributing](#references-and-contributing)

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
| Fabric Amper | `0.1.0` | Local build and test artifact; no public Plugin Portal availability asserted |
| Minecraft | `26.3` | Non-remapping, Mojang official names |
| Java/JDK | **25** | Gradle itself must run on Java 25 or newer for Minecraft 26.3; YAML release must be 25 |
| Fabric Loader | `0.20.1` | Fixed version, not `latest.release` |
| Fabric API | `0.165.2+26.3` | Optional dependency in `minecraft.fabricApi` |
| Gradle | `9.7.1` used for the latest project validation | Other combinations require their own compatibility evidence |
| Kotlin (optional) | KGP `2.4.21`; language `2.4`; Fabric Language Kotlin `1.14.2+kotlin.2.4.21` | Must use the plugin's pinned compatible combination |
| Legacy profile | Minecraft `1.21.11` with Java **21** | Uses remapping Loom and a separate runtime acceptance path |

Use a project Gradle Wrapper when one is actually installed. If the consumer does not contain `gradlew` / `gradlew.bat`, run an installed Gradle executable instead. **The Fabric Amper producer repository itself does not currently supply a wrapper**; the examples below use `gradle` to mean an installed compatible Gradle launcher.

### How to obtain the plugin today

A release/published-plugin repository must supply both Fabric Amper's Gradle plugin marker and its binary. Because a public plugin release is **not confirmed**, do not expect the following plugin ID to resolve from the Plugin Portal by itself.

For a **local development demonstration**, build and publish the plugin marker into an isolated repository. From the **Fabric Amper producer project**:

```powershell
# Run from the Fabric Amper repository (not the new consumer).
gradle publishAllPublicationsToG29ProbeRepository -PfabricAmperProbeMavenRepo=true
```

This produces the project-local plugin repository at `build/g29-probe/maven-repo/`. It does **not** publish to Maven Central, Gradle Plugin Portal, or your user-wide Maven repository. Running `clean` in the producer can remove the repository; recreate it when required.

The quick start below assumes your new `my-fabric-mod/` is a **sibling** of the producer `Fabric Amper/` directory. If your folders are elsewhere, adjust the relative repository URI in `settings.gradle.kts`. When Fabric Amper is eventually distributed through a real repository, replace that local URI with the repository actually hosting its plugin marker and implementation.

## Five-minute quick start

The following is a minimal **Java-only, Minecraft 26.3** project. It needs no consumer `build.gradle`, no consumer `build.gradle.kts`, and no authored `fabric.mod.json`.

### 1. Make the folder structure

```text
parent/
├── Fabric Amper/                         # plugin producer, already checked out
│   └── build/g29-probe/maven-repo/        # local plugin repository (after publishing)
└── my-fabric-mod/                        # new consumer project
    ├── settings.gradle.kts
    ├── mod.yaml
    └── src/main/java/org/example/ExampleMod.java
```

### 2. Write `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        // Local marker + plugin binary from the sibling Fabric Amper project.
        maven { url = uri("../Fabric Amper/build/g29-probe/maven-repo") }
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("dev.fabric-amper.settings") version "0.1.0"
}
```

For a future public release, configure the **actual** published repository here. `pluginManagement.repositories` is for resolving plugins; `repositories` in `mod.yaml` is for resolving your mod's libraries. They are not interchangeable.

### 3. Write `mod.yaml`

```yaml
schema: 1
product: fabric/mod

mod:
  id: example_mod
  version: "1.0.0+26.3"
  group: org.example
  archivesName: example-mod
  name: "Example Mod"
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

repositories:
  - fabric
  - mavenCentral
```

Only `product`, `mod` (including `id` and `version`), and `minecraft` (including `version` and `loader`) are mandatory; the example spells out useful values. The optional `schema: 1` pins the document to schema v1; when absent, this plugin parses with its **latest supported schema**, currently v1. Unknown explicitly requested schema versions are errors, not silently upgraded. Versions such as `minecraft.version` remain **quoted strings** even if they resemble numbers. The `mod.entrypoints` class must exist in your source code.

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
│   │       ├── assets/example_mod/icon.png # if referenced from mod.icon
│   │       ├── example.mixins.json          # if referenced from mod.mixins
│   │       └── example.classtweaker         # if referenced from mod.accessWidener
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
schema: 1
product: fabric/mod
mod:
  id: example_mod
  version: "1.0.0+26.3"
  group: org.example
  archivesName: example-mod
minecraft:
  version: "26.3"
  mappings: mojang
  loader: "0.20.1"
  fabricApi: "0.165.2+26.3"
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

**Fabric Amper: the same fields inside `mod.yaml` instead:**

```yaml
mod:
  id: example_mod
  version: "1.0.0+26.3"
  name: Example Mod
  environment: "*"
  entrypoints:
    main:
      - org.example.ExampleMod
```

The YAML snippet is the `mod` **portion of a complete document**, not a standalone valid file. Fabric Amper combines it with required `product` and `minecraft` (plus optional `schema`) to generate metadata. Your Java entrypoint remains real source code. You must **remove the manually authored JSON** when adopting Fabric Amper.

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

Again, this is a **fragment**. In a real YAML file, also provide `minecraft`; `schema: 1` is optional. The API product configures the Java library and Maven publication automatically and provides a **project-local** Maven destination. No remote upload is implicit.

## YAML essentials and rules

- **Use two spaces for indentation.** Write ordinary mappings and lists, not executable Gradle/Kotlin snippets inside YAML.
- Use **exactly one** `mod.yaml` **or** `mod.yml`. Both at once, or neither, is **FA001**.
- Optionally set `schema: 1` to pin syntax v1. If omitted, the plugin selects its latest supported schema (**currently 1**). An unsupported explicit version fails FA003. `product` must be `fabric/mod`, `fabric/api`, or its exact alias `fabric/library`.
- Quote Minecraft, Loader, API, Maven, version-range, and numeric-looking version **strings**. `version: 26.3` may parse as a number and fail; use `version: "26.3"`.
- Use true YAML booleans `true` / `false`, not `yes` or quoted `"true"`.
- Fixed Gradle versions only. No `latest.release`, `1.+`, or `1.2.x` for `minecraft.loader` / `minecraft.fabricApi`.
- Do not use anchors, aliases, merge keys, arbitrary YAML tags, duplicate mapping keys, multiple documents, or malformed UTF-8. The strict reader rejects them rather than guessing.
- Unknown fields fail: `additionalProperties: false` is intentional in the public schema. Do not invent a `loomVersion` or `build.gradle` field.
- `mod.custom` is deliberately open for Fabric Loader custom **JSON-compatible** metadata. Its keys are *not* build configuration.
- Defaults shown in the JSON Schema are documentation. The **plugin** decides defaults; editors do not magically insert them.

### Required root keys (without optional `schema`)

```yaml
product: fabric/mod
mod:
  id: my_mod
  version: "1.0.0"
minecraft:
  version: "26.3"
  loader: "0.20.1"
```

This minimum validates the structural contract, but to produce a **useful mod** you normally add a real entrypoint and source file. Your game and Fabric tooling still have to be accessible.

## `mod` and Fabric Loader metadata

The `mod` mapping describes your published Fabric Loader identity and (where specified) Gradle archive identity.

| Field | Purpose / example | Notes |
| --- | --- | --- |
| `mod.id` | `my_mod` | Required; 2–64 chars; starts with lowercase ASCII; `a-z`, digits, `_`, `-` |
| `mod.version` | `"1.2.0+26.3"` | Required; nonblank, no whitespace; becomes Gradle project version and Loader version |
| `mod.group` | `org.example` | Maven group; **required** for `fabric/api` or `publish.maven` |
| `mod.archivesName` | `my-mod` | Output artifact name; defaults to `mod.id` |
| `mod.name` / `description` | Display metadata | Optional; description may contain readable prose |
| `mod.environment` | `"*"`, `client`, `server` | Defaults to `"*"` (both sides); side safety still matters |
| `mod.license` | `MIT` or `[MIT, Apache-2.0]` | String or list of strings |
| `mod.authors` / `contributors` | `[Alice]`, objects with `name` / `contact` | Real people are examples, not guessed project authors |
| `mod.contact` | `homepage`, `issues`, `email`, `sources` | String-valued metadata map |
| `mod.icon` | `assets/my_mod/icon.png` or size→path map | Must point to an existing PNG resource |
| `mod.provides` | `[old_mod_id]` | Other supplied mod IDs; no duplicates or self-reference |
| `mod.entrypoints` | `main` / `client` / `server` / custom entrypoint keys | Each key maps to a list of class names or adapter/value objects |
| `mod.languageAdapters` | Adapter ID → implementation class | Do not redefine the reserved Kotlin adapter |
| `mod.mixins` | List of config paths or environment objects | Reference real packaged configuration files |
| `mod.accessWidener` | `my_mod.classtweaker` (26.3) | Resource path; header/namespace/profile validated |
| `mod.depends` | `fabricloader: ">=0.20.1"` | **Loader runtime** version predicates, not Maven coordinates |
| `mod.recommends` / `suggests` | Optional Loader relationships | Predicate map, like depends |
| `mod.conflicts` / `breaks` | Incompatible Loader relationships | Predicate map, like depends |
| `mod.custom` | JSON-compatible custom metadata | Preserve underlying Fabric Loader extensions |

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

This is a `mod`-only reference fragment. Every icon, Mixin config, and Class Tweaker file **must actually exist** under an appropriate `src/main/resources/` or enabled `src/client/resources/` tree. The `accessWidener` is **main-resources only**. Fabric Amper validates resource paths and prevents traversal outside source roots. Source-set and side restrictions can reject a seemingly valid path.

For Minecraft **26.1+**, a Class Tweaker file uses the `.classtweaker` extension and the exact header `classTweaker v1 official`. For a **legacy remapping** version, use `.accesswidener` with a supported `accessWidener v1/v2 named/intermediary` header. The file must contain valid, version-appropriate declarations; the YAML does not replace its contents.

### Loader predicates are not Gradle dependencies

```yaml
mod:
  id: example_mod
  version: "1.0.0"
  depends:
    fabricloader: ">=0.20.1"
    another_mod: ">=2.0.0 <3.0.0"
  recommends:
    optional_mod:
      - ">=1.0.0"
      - "2.0.0"
```

Inside a predicate **string**, whitespace-separated requirements are an **AND**; a YAML list means **OR**. Fabric Amper fills missing `minecraft`, `fabricloader`, `java`, and configured API/Kotlin requirements in Loader metadata without silently overwriting an authored key. Explicit contradictions with selected toolchain versions are errors. An ordinary `dependencies` entry does **not** automatically become a global `mod.depends` requirement, especially for client-only libraries.

## Minecraft, Java, Kotlin, and source sets

### Minecraft `26.3` (non-remapping)

```yaml
minecraft:
  version: "26.3"
  mappings: mojang
  loader: "0.20.1"
  fabricApi: "0.165.2+26.3"
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
    fabricLanguageKotlin: "1.14.2+kotlin.2.4.21"
```

Fabric Amper configures the pinned Kotlin Gradle Plugin and Fabric Language Kotlin. With a Kotlin language adapter entrypoint:

```yaml
mod:
  id: example_mod
  version: "1.0.0"
  entrypoints:
    main:
      - adapter: kotlin
        value: org.example.KotlinEntrypoint
```

Use the correct real Kotlin source and entrypoint implementation. An explicit `adapter: kotlin` with `settings.kotlin.enabled: false` is invalid (FA006). The optional version keys are **pinned**; an arbitrary Kotlin plugin override can fail compatibility checks. Even when Kotlin is disabled, the currently packaged plugin may incur KGP-related static dependency downloads on a cold cache; this is a known optimization limitation.

### Split client/main source sets

```yaml
settings:
  loom:
    splitEnvironmentSourceSets: true
dependencies:
  - maven: "org.example:client-helper:1.0.0"
    target: client
    scope: compile-only
```

`target: client` is for dependencies used only by the separate `src/client/java` / `src/client/kotlin` code, **not** `mod.environment: client`. Split source sets are supported on the non-remapping profile; client dependencies cannot be `exported` or `include`. Maintain side-safe Mixin/entrypoint declarations: common/server code must not import or require client-only classes.

The artifact `org.example:client-helper:1.0.0` above is illustrative and must be replaced by a real library.

## Repositories and dependencies

There are three different dependency concepts in a Fabric project:

1. **Gradle dependencies** under top-level `dependencies` (and `test-dependencies`) determine which artifacts can be compiled against and are available at runtime.
2. **Fabric Loader dependencies** under `mod.depends`, `mod.recommends`, `mod.suggests`, `mod.conflicts`, and `mod.breaks` describe the requirements Fabric Loader checks **inside the game**.
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

All `org.example` coordinates in this guide are **illustrative, not checked as real published artifacts**. Use a real `group:artifact:fixed-version` available through your chosen repositories. Dynamic Gradle selectors and arbitrary version ranges are not supported for Maven coordinates.

### Dependency scope matrix

| YAML field | Values / default | What happens |
| --- | --- | --- |
| `maven` | Fixed Maven coordinate, mutually exclusive with `modrinth` | Resolves a normal Maven artifact |
| `modrinth` | `{project, version}`, mutually exclusive with `maven` | Resolves a fixed Modrinth Maven coordinate |
| `kind` | `library` or `mod`; default `library` for Maven, `mod` for Modrinth | Selects dependency/transform handling, especially legacy remapping |
| `scope` | `all` (default), `compile-only`, `runtime-only` | Gradle compile/runtime exposure |
| `target` | `main` (default), `client` | Source-set visibility; client requires the supported split profile |
| `exported` | `false` (default) or `true` | Expose a supported API/library dependency to downstream compile |
| `include` | `false` (default) or `true` | Add a Loom **nested JAR-in-JAR** in addition to a normal dependency |

When using `exported: true` or `include: true`, the `scope` must be `all`. They cannot be combined with `scope: compile-only` or `runtime-only`. With `target: client`, both `exported` and `include` must be false, and `settings.loom.splitEnvironmentSourceSets` must be enabled on a non-remapping Minecraft profile.

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

These recipes are **fragments** to merge into an otherwise valid YAML document. Do not paste multiple independent `mod:` or `settings:` mappings into one file; combine their contents under a single mapping instead.

### Create a strictly client-only mod

```yaml
mod:
  id: client_widgets
  version: "1.0.0+26.3"
  environment: client
  entrypoints:
    client: [org.example.ClientWidgets]
settings:
  loom:
    splitEnvironmentSourceSets: true
```

Use `src/client/java` for client implementation. `environment: client` tells Fabric Loader **not to load the entire mod on dedicated servers**. That is different from leaving the whole mod `"*"` and adding an optional client entrypoint to a two-sided mod.

### Add client-only Gradle classes but keep a common mod

```yaml
mod:
  environment: "*"
settings:
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
mod:
  mixins:
    - config: example.mixins.json
      environment: "*"
    - config: example.client.mixins.json
      environment: client
```

Create those actual JSON resource files. Put common mixins in `src/main/resources`. If a referenced config exists only under `src/client/resources`, use split client source sets and an appropriate client-only Mixin environment; the plugin refuses unsafe global-server references.

### Add a Class Tweaker (Minecraft 26.3)

```yaml
mod:
  accessWidener: example.classtweaker
```

Create `src/main/resources/example.classtweaker` with `classTweaker v1 official` as its exact first line and valid class/member declarations for your Minecraft version. Run `gradle validateAccessWidener` if available and `gradle build`. The YAML does not create the declarations.

### Add a mod icon

```yaml
mod:
  icon: "assets/example_mod/icon.png"
```

Or multiple PNG resolutions:

```yaml
mod:
  icon:
    "32": "assets/example_mod/icon_32.png"
    "128": "assets/example_mod/icon_128.png"
```

All referenced files must be real PNGs under the correct resources tree; the size mapping's keys are positive integer pixel sizes. The actual images must be supplied by the developer.

### Put advanced Loader metadata in one place

```yaml
mod:
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
fabric-amper.bat setup
fabric-amper.bat init example_mod
fabric-amper.bat init example_api "C:\work\example_api" api
```

`setup` runs `publishAllPublicationsToG29ProbeRepository -PfabricAmperProbeMavenRepo=true` in the producer and writes **only** to `build/g29-probe/maven-repo/`. `init <id> [new_folder] [mod|api]` generates a **new** consumer folder containing `settings.gradle.kts`, `mod.yaml`, `schemas/mod.schema.json` and `src/main/java/org/example/ExampleMod.java`; existing destinations are rejected. `mod.yaml` begins with the exact editor directive `# yaml-language-server: $schema=./schemas/mod.schema.json` and **omits `schema: 1`** so the plugin parses with the latest version it supports. The schema file is copied **byte-for-byte from the current producer's `fabric-amper.schema.json` each time `init` runs**; it is a local snapshot for autocomplete, not a file that magically updates existing projects when the plugin is upgraded. Without an explicit folder, `init` creates a sibling when run from the producer or a child of the current directory elsewhere. Its generated Settings script uses the **absolute local producer plugin repository path**: adjust that path if moving the consumer. API mode selects `product: fabric/api` and example `mod.group: org.example`.

From the **consumer directory**, use the batch file's full path (unless its directory is on `PATH`):

```bat
"C:\path\to\Fabric Amper\fabric-amper.bat" validate
"C:\path\to\Fabric Amper\fabric-amper.bat" build
"C:\path\to\Fabric Amper\fabric-amper.bat" run
```

Or select a consumer explicitly with `-C` or `--project` **before the subcommand**:

```bat
"C:\path\to\Fabric Amper\fabric-amper.bat" -C "C:\work\example_mod" build
```

| Wrapper subcommand | What it does |
| --- | --- |
| `help`, `version`, `docs` | Display usage/version/document paths |
| `doctor`, `doctor --full`, `doctor --paths` | Local Java/Gradle/producer checks; optional Windows path/JDK hints without cache deletion |
| `setup` | Publish the **producer plugin locally**, not a public release |
| `init <id> [folder] [mod|api]` | Create a new Java consumer; never overwrite existing destination |
| `tasks`, `validate` | Run `tasks --all`, `validateFabricAmper` |
| `diagnose`, `explain` | Run read-only consumer `fabricAmperDoctor` / `fabricAmperExplain` |
| `plan` | Build/verify the final JAR, then print `fabricAmperReleasePlan` metadata **without uploading** |
| `port 26.3` | Run `fabricAmperPortingReport -PfabricAmperPortTarget=26.3`; checklist only, no source rewrite |
| `build` | Run `validateFabricAmper build verifyFabricAmperJar` |
| `verify`, `test`, `clean` | Run `verifyFabricAmperJar`, `test`, `clean` respectively |
| `run`, `server` | Start `runClient` / `runServer` in the consumer development environment; server EULA remains the user's decision |
| `publish local` | Explicit local API Maven publication inside the consumer's `build/fabric-amper/maven-repository/` |
| `publish maven`, `modrinth`, `curseforge`, `all` | Confirm with typed `PUBLISH`, locally validate and build, then invoke the selected remote task |

Consumer tasks check that exactly one YAML exists and reject a consumer `build.gradle(.kts)` or author-maintained `fabric.mod.json`. **Remote publication remains a real, side-effectful action**: `PUBLISH` is only an accidental-invocation guard, not verification of credentials, IDs or version uniqueness. `publish all` means the new `publishFabricAmper` aggregator, **not** Gradle's broader `publish`; multi-site partial success is possible. Consult [the publishing section](#explicit-maven-modrinth-and-curseforge-publishing) before authorizing any upload. For a shorter command cheat sheet, see [HUMAN_QuickGuide.md](HUMAN_QuickGuide.md).

### Read-only developer diagnostics and release preview

The following **explicit Gradle tasks** are intentionally separate from `build`, `check`, and remote publishing; no new `mod.yaml` fields are necessary:

```powershell
gradle fabricAmperDoctor
gradle fabricAmperExplain
gradle fabricAmperPortingReport -PfabricAmperPortTarget=26.3
gradle fabricAmperReleasePlan
```

- **Doctor:** summarizes the selected Fabric profile, Java release, running JVM, selected Loom plugin ID, declared Fabric Loader/API and Kotlin/client settings. It labels unknown exact Loom compatibility rather than asserting a matrix PASS. A Windows path estimate is a heuristic, **not** proof that the IDE or Loom caches fit within a particular limit.
- **Explain:** displays authored Maven/Modrinth Gradle dependencies (scope/target/export/include), injected vs authored Fabric Loader `mod.depends`, and *separately* Modrinth/CurseForge release relationships. It does **not** download artifacts or guess platform project IDs.
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

The repository root contains [`fabric-amper.schema.json`](fabric-amper.schema.json), a **Draft-07 JSON Schema**. It covers known top-level sections and the current publish fields. For editors with the YAML Language Server, put a schema directive at the **top** of your consumer YAML **only if the schema file has actually been copied into that consumer directory**:

```yaml
# yaml-language-server: $schema=./fabric-amper.schema.json
schema: 1
product: fabric/mod
```

This is only a **header fragment**, not a complete valid `mod.yaml`: keep your real `mod:` and `minecraft:` mappings below it. Alternatively configure the YAML extension's `yaml.schemas` mapping or configure IntelliJ's **JSON Schema Mappings** for `mod.yaml`/`mod.yml` using the real schema location. Do not add a literal root YAML `$schema:` field: it is not part of the Fabric Amper schema. The schema is an **editor aid**, not the sole validation authority; path containment, cross-field constraints, and other runtime checks still happen in the plugin.

## Creating an API/library and publishing locally

Fabric Amper has three product identifiers:

| `product` | Meaning |
| --- | --- |
| `fabric/mod` | Ordinary Fabric mod; no automatic Java-library Maven publication unless explicitly configured by `publish.maven` |
| `fabric/api` | A real Fabric library mod; applies `java-library` and `maven-publish` and configures a local Maven publication |
| `fabric/library` | **Exact alias** of `fabric/api`, with the same publication behavior |

An API/library is still a **Fabric mod** and contains Loader metadata. It is not a plain Java-only JAR with Fabric stripped out.

### API producer example

```yaml
schema: 1
product: fabric/api
mod:
  id: example_api
  version: "1.0.0"
  group: org.example
  archivesName: example-api
  name: Example API
  environment: "*"
  entrypoints:
    main: [org.example.ExampleApi]
minecraft:
  version: "26.3"
  mappings: mojang
  loader: "0.20.1"
  fabricApi: "0.165.2+26.3"
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

The example project IDs are **not real release identifiers**. `releaseType` is `release`, `beta`, or `alpha` (default `release`). The names, fixed version number, supported Minecraft version strings, and loader list may be supplied explicitly or inherit from [`publish.common`](#share-release-fields-with-publishcommon). The `environment` enum is Modrinth-specific; `mod.environment: "*"` normally maps to `client_and_server`, `client` to `client_only`, and `server` to `server_only`, but authors must correct a different required-installation policy explicitly. `dependencies[]` is a list of `{projectId, type}` with types `required`, `optional`, `incompatible`, or `embedded`—these are **Modrinth release relationships**, not Gradle classpath dependencies.

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

The **schema v1** authoring syntax supports an optional **`publish.common`** mapping shared by explicitly configured Modrinth and CurseForge destinations. A platform's explicitly authored field **wins** over the inherited value. `common` by itself is *not* a destination; it must be paired with `modrinth` and/or `curseforge`. It never creates uploads, credentials or platform IDs. The existing `publish.maven` and legacy `publishing.repository` are independent and do **not** inherit this platform release metadata.

This is a **full YAML example**, excluding the required real Java initializer source. All IDs and the local `CHANGELOG.md` path must be replaced or created before any upload:

```yaml
product: fabric/mod
mod:
  id: example_mod
  version: "1.0.0+26.3"
  name: Example Mod
  entrypoints:
    main: [org.example.ExampleMod]
minecraft:
  version: "26.3"
  loader: "0.20.1"
  fabricApi: "0.165.2+26.3"
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

The full example **omits `schema` intentionally**: the plugin's latest internal schema (currently **1**) is selected. Add `schema: 1` at the root only when deliberately pinning v1. It does **not** auto-accept a future, unknown explicit version. The release-site project IDs are placeholders and `CHANGELOG.md` must exist in the consumer root. Nothing above uploads until a publish task is explicitly invoked.

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

**Important differences:** Modrinth `gameVersions` are game-version *strings*; CurseForge `gameVersionNames` are release-page *tags*, while CurseForge `gameVersions` contains integer author-upload IDs and is **never inferred** from common. `common.gameVersions` will not automatically add tags like `Fabric`; provide CurseForge `gameVersionNames` explicitly if required. Platform project IDs and `tokenEnv` are *always* specified within each destination. Do not assume Modrinth and CurseForge slugs/IDs correspond to the same project without checking; supply `modrinthId` and `curseforgeId` when they differ. Never infer relations from `mod.depends` or Maven coordinates.

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

This section covers the **author-facing schema v1** recognized by the current strict decoder. The accompanying [`fabric-amper.schema.json`](fabric-amper.schema.json) is the full Draft-07 definition, including enums and conditional rules. Cross-field constraints, actual resource existence, and runtime predicates are enforced by the plugin even when an editor does not flag them.

### Root document

| Key | Type | Required | Meaning |
| --- | --- | --- | --- |
| `schema` | Integer, exactly `1` | **No** | Optional explicit version pin; when absent, plugin selects latest internally supported version (currently 1) |
| `product` | `fabric/mod`, `fabric/api`, `fabric/library` | Yes | A normal mod or a Maven-publishable API/library mod |
| `mod` | Mapping | Yes | Fabric Loader identity, metadata, and archive identity |
| `minecraft` | Mapping | Yes | Fixed Minecraft, Loader, mappings, and optional API versions |
| `settings` | Mapping | No | Java, Kotlin, Loom, resources |
| `repositories` | List | No | Repository names or custom HTTPS Maven repositories used to resolve project dependencies |
| `dependencies` | List | No | Main/client compile/runtime dependencies, API exposure, JAR-in-JAR |
| `test-dependencies` | List | No | Test-only dependency coordinates and scopes |
| `publishing` | Mapping | No | Legacy API/library-only Maven remote configuration |
| `publish` | Mapping | No | New explicit Maven/Modrinth/CurseForge release destinations, with optional platform `common` |

There is **no** `build`, `gradle`, `plugins`, `fabric.mod.json`, or `loomVersion` top-level YAML property. Unknown keys are errors, not ignored extensions.

### `mod` metadata keys

| Key | Type / default | Notes |
| --- | --- | --- |
| `mod.id` | Required string | Lowercase letter first; 2–64 characters; lowercase ASCII, digits, underscore, hyphen |
| `mod.version` | Required nonblank string | Becomes the Gradle project version **and** Loader metadata version |
| `mod.group` | Optional Maven group | Required for `fabric/api`, `fabric/library`, or explicit `publish.maven` |
| `mod.archivesName` | Optional string; defaults to `mod.id` | JAR basename and Maven artifact ID; do not use filesystem traversal |
| `mod.name` | Optional nonblank string | Display name |
| `mod.description` | Optional string | Human-readable Loader description |
| `mod.environment` | `"*"` (default), `client`, `server` | Whole-mod Loader side; not the same as a dependency's source set |
| `mod.license` | String or string array | Loader license identifier(s) |
| `mod.authors` / `mod.contributors` | Arrays of name strings or `{name, contact}` maps | People metadata |
| `mod.contact` | String map | Recognizes `homepage`, `issues`, `email`; preserves other string-valued keys |
| `mod.icon` | PNG path or size-to-PNG-path map | Real files must exist under a valid resources root |
| `mod.provides` | List of mod IDs | No duplicates; do not claim the primary ID again |
| `mod.entrypoints` | Name → list of class strings or `{adapter, value}` | A list for each entrypoint name; object entries require both keys |
| `mod.languageAdapters` | Adapter ID → class | Do not redefine the built-in Kotlin adapter |
| `mod.mixins` | List of strings or `{config, environment}` | Resource paths; environment can be `"*"`, `client`, `server` |
| `mod.accessWidener` | Path string | `.classtweaker` for modern profile, `.accesswidener` in legacy remapping |
| `mod.depends` | Mod ID → predicate string or OR array | Required Loader mod relationships |
| `mod.recommends` / `mod.suggests` | Mod ID → predicate string or OR array | Optional Loader relationships |
| `mod.conflicts` / `mod.breaks` | Mod ID → predicate string or OR array | Incompatibility rules |
| `mod.custom` | JSON-compatible mapping | Fabric Loader's custom metadata; not Gradle configuration |

### `minecraft` keys

| Key | Type / default | Notes |
| --- | --- | --- |
| `minecraft.version` | **Required** string | Fixed version such as `"26.3"` or `"1.21.11"`; quote it |
| `minecraft.loader` | **Required** fixed version string | No `latest.release`, `1.+`, `1.2.x` selectors |
| `minecraft.fabricApi` | Optional fixed version string | Adds Fabric API to the appropriate Gradle configuration and Loader metadata |
| `minecraft.mappings` | `mojang` by default; or `{type, version}` | Mojang required for 26.1+; Yarn must include a compatible version for supported legacy profiles |

The Yarn object form is:

```yaml
minecraft:
  version: "1.21.11"
  loader: "0.20.1"
  mappings:
    type: yarn
    version: "YOUR_ACTUAL_PUBLISHED_YARN_VERSION"
```

The mapping version is deliberately a placeholder. Verify a genuinely published version for your exact Minecraft version before running Gradle.

### `settings` keys

| Key | Default | Constraints |
| --- | --- | --- |
| `settings.java.release` | `25` | Java target release, integer; Minecraft 26.1+ uses 25 |
| `settings.java.sourcesJar` | `true` | Generate a source JAR |
| `settings.kotlin.enabled` | `false` | Enable Kotlin compilation and Fabric Language Kotlin integration |
| `settings.kotlin.pluginVersion` | `"2.4.21"` | When enabled, must match bundled version |
| `settings.kotlin.languageVersion` | `"2.4"` | When enabled, must match pinned Kotlin language version |
| `settings.kotlin.fabricLanguageKotlin` | `"1.14.2+kotlin.2.4.21"` | When enabled, must match pinned FLK version |
| `settings.loom.splitEnvironmentSourceSets` | `false` | Add separate client source sets |
| `settings.resources.failOnExistingFabricModJson` | `true` | Must stay `true`; never replace an authored metadata file |

### Dependencies and repositories at a glance

| Key | Type | Meaning |
| --- | --- | --- |
| `repositories[]` | `fabric`, `mavenCentral`, `modrinth`, or `{maven: https-url}` | Dependency resolution sources |
| `dependencies[]` | Coordinate string, one-key shorthand, or full object | Main/client dependency |
| `dependencies[].maven` | `"group:artifact:fixed-version"` | Cannot coexist with `modrinth` in one dependency object |
| `dependencies[].modrinth.project` | String | Modrinth **dependency repository** project ID/slug |
| `dependencies[].modrinth.version` | String | Fixed published version or version ID |
| `dependencies[].kind` | `library` or `mod` | Defaults by source kind |
| `dependencies[].scope` | `all`, `compile-only`, `runtime-only` | Default `all` |
| `dependencies[].target` | `main` or `client` | Default `main`; client requires supported split |
| `dependencies[].exported` | Boolean | Default `false`; requires `scope: all` |
| `dependencies[].include` | Boolean | Default `false`; requires `scope: all` |
| `test-dependencies[]` | Similar coordinate/scope shapes | Never `exported`, `include`, or `target: client` |

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
| `publish.modrinth.environment` | Optional Modrinth environment enum | Inferred conservatively from `mod.environment` unless overridden |
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
| **FA004** | Yarn mappings with Minecraft 26.1+ | Use `minecraft.mappings: mojang` |
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

If Gradle cannot resolve `dev.fabric-amper.settings:0.1.0` **before** it reads `mod.yaml`, this is not necessarily a parser bug. Check that you first published the producer plugin into `build/g29-probe/maven-repo/`, that your consumer points `pluginManagement.repositories` to the **actual** repository at the correct relative path, and that the version matches. Public Gradle Plugin Portal installation is not established for this project.

### The YAML parses but the build rejects it

**Generic YAML validity is weaker than Fabric Amper validity.** YAML alias/anchor/tag support, duplicate keys, unknown root keys, null in typed fields, dynamic Gradle versions, incompatible scope flags, and unsafe source/resource paths are deliberately rejected. Start from the [quick start](#five-minute-quick-start), add one feature at a time, and inspect the reported code and YAML path.

### An icon, Mixin, or Class Tweaker is not found

YAML names **files**, not content. Verify case-sensitive resource paths and ensure the actual file exists inside the allowed source roots. Modern Class Tweakers must have the precise `classTweaker v1 official` header. A client-only resource cannot silently be treated as a common/server resource.

### Everything compiles, but the dedicated server fails

Compiling does not guarantee side safety. Check `mod.environment`, client-only entrypoints, client-only Mixin environments, split source sets, and `mod.depends` requirements. Avoid pulling client-only types into common classes. A server first run may stop for Minecraft EULA review; this is **separate** from Fabric Amper's build.

### A release task says credentials are missing

Check the **environment variable names**, not their secret values in the YAML. A real Maven publish requires both named variables if configured; Modrinth/CurseForge require tokens and valid pre-existing project IDs. A `--dry-run` can skip all task actions, so it is not an authentication or HTTP PUT test. Do not paste credentials into logs, `mod.yaml`, Gradle scripts, or GitHub issues.

## Verification and known limits

Current verification evidence is tracked in [`QA_REPORT.md`](QA_REPORT.md) and the per-gate evidence index under `analysis/acceptance-evidence-2026-11-13/`. Earlier historical test records remain in the report; the most recent **producer code** baseline below is distinct from later **schema and documentation** edits.

| Area | Recorded evidence / limitation |
| --- | --- |
| Gradle 9.7.1, JDK 25, `clean build validatePlugins --offline --no-build-cache --rerun-tasks` | **PASS** for the final recorded producer source baseline |
| JUnit results | **46 suites, 247 tests: 207 PASS, 40 SKIP, 0 FAIL, 0 ERROR** |
| Settings-only consumers, generated metadata and JAR checks | Selected real TestKit consumers **PASS** |
| YAML cache invalidation and damaged mirror/resource recovery | Selected consumer/parallel cache tests **PASS** |
| API/library real local Maven publication and downstream consumer | Selected real publication/compile tests **PASS** |
| Isolated modern client Loader main/client/tick and interface injection | Selected runtime tests **PASS** |
| IntelliJ GUI first import/manual and automatic synchronization | **Not fully observed/approved** |
| Full dedicated-server boot after an EULA is accepted | **Not established by the earlier EULA-refusal run** |
| Minecraft 1.21.11 full game runtime | **Not yet approved**; legacy remap and downstream compile separately verified |
| Authenticated native remote Maven HTTPS PUT and configuration-cache HIT **simultaneously** | **Not demonstrated**; Gradle native `PasswordCredentials` CC incompatibility recorded |
| External Modrinth/CurseForge actual release | **Not attempted**; no outside-platform upload authorized |

The **40 skipped tests are not passing tests**, and the combined G1–G30 shipping status is **NOT APPROVED**. The post-missing-credential-fix plugin binary from the 2026-11-13 producer build was recorded as `build/libs/fabric-amper-0.1.0.jar`, 315,122 bytes, SHA-256 `13022B24ADE7C9809400FC6AEBE99E7751783A9DAE7EF8B8C53780FE70B698AD`. This is a **historical binary identity**, not the current schema's checksum.

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

**Why do `dependencies` and `mod.depends` both exist?** Gradle dependencies control build classpaths and packaging; Loader dependencies control mod installation/runtime requirements. Neither is a mechanical substitute for the other.

**Does `gradle build` upload my release?** No. Use the specific explicit `publishFabricAmper...` task only when you intentionally wish to publish and have valid credentials/project metadata.

**Can I use a Modrinth project ID for CurseForge?** No. The two release platforms and Maven repository resolution use independent identifiers. Fabric Amper never invents cross-platform relationships.

**How do I get editor suggestions?** Newly created `fabric-amper.bat init` projects already include a `schemas/mod.schema.json` copy and the first-line comment `# yaml-language-server: $schema=./schemas/mod.schema.json`. For manually created projects, copy [`fabric-amper.schema.json`](fabric-amper.schema.json) into that location or configure IntelliJ JSON Schema Mapping. The comment is valid YAML; a literal `$schema:` YAML **data key** is not. The schema copy represents the producer version when the project was created; after upgrading Fabric Amper, deliberately refresh the local schema snapshot for updated editor completions.

**Can I change Minecraft from 26.3 to 1.21.11 by changing only one string?** Account for the Java release, remapping profile, access-widener format, mappings, and dependency/game compatibility. The plugin selects the profile, but it cannot promise that third-party mods or game code remain compatible.

**Does the plugin accept Minecraft's EULA for me?** No. Read the official EULA and make your own decision in the specific isolated server's `eula.txt`. Do not share a game server directory with automated probe builds.

**Can I paste a token directly into YAML for quick testing?** No. Use only the *name* of an environment variable in `tokenEnv` or `credentialsFromEnv`; keep the secret out of files, screenshots, commits, and logs.

## References and contributing

### Fabric Amper project sources

- [`README.md`](README.md) — current feature contract and development state.
- [`fabric-amper.schema.json`](fabric-amper.schema.json) — editor schema v1 (Draft-07), including supported `publish` keys.
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


