# Write mods, not build scripts

**Fabric Amper** is a YAML-driven Gradle plugin for **Fabric**, **NeoForge**, and **dual-loader** Minecraft mods. Keep the build configuration in `settings.gradle.kts` and one `mod.yaml` (or `mod.yml`). Write Java or Kotlin mod code—not hand-maintained Gradle build scripts or Loader manifest files.

[![](https://jitpack.io/v/howShouldIChooseMyUsername/Fabric-Amper.svg)](https://jitpack.io/#howShouldIChooseMyUsername/Fabric-Amper)

**v0.3.1 · Public Developer Preview prerelease (November 18, 2026)** — [Download release](https://github.com/howShouldIChooseMyUsername/Fabric-Amper/releases/tag/v0.3.1) · [Quick Guide](HUMAN_QuickGuide.md) · [Full Guide](HUMAN.md) · [AI Guide](FabricAmperSKILL.md) · [Schema](fabric-amper.schema.json)

## Why Fabric Amper?

- **Settings + YAML only.** No consumer-authored `build.gradle` or `build.gradle.kts`.
- **One configuration, multiple loaders.** Build independent Fabric and NeoForge JARs, optionally sharing code through Architectury's `:common` project.
- **Generated metadata.** Fabric Amper generates `fabric.mod.json` for Fabric or `META-INF/neoforge.mods.toml` for NeoForge. You write the actual entrypoint classes and assets.
- **Explicit dependencies.** Keep shared libraries together and select different Maven artifacts or versions for each loader when needed.
- **Java or Kotlin.** Supports loader-specific source trees, optional Fabric client split, validation, and reproducible build inputs.

Fabric Amper is **a Gradle plugin, not a Minecraft mod**. Do not place its plugin JAR in your Minecraft `mods/` folder.

## Quick start

**Tested baseline:** Minecraft `26.3`, Java `25`, Gradle `9.7.1`, Fabric Loader `0.20.1`, Fabric API `0.165.2+26.3`. NeoForge examples use the tested `26.3.0.101` coordinate. These are example/tested pins, not claims about the latest available game or Loader versions.

Create this structure:

```text
example-mod/
├── settings.gradle.kts
├── mod.yaml
└── src/main/java/org/example/ExampleMod.java
```

**`settings.gradle.kts`**

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

**`mod.yaml` — Fabric, schema 1.1**

```yaml
schema: "1.1"
product: fabric/mod
mod:
  id: example_mod
  version: "1.0.0+26.3"
  name: Example Mod
  license: MIT
minecraft:
  version: "26.3"
settings:
  java:
    release: 25
fabric:
  loader: "0.20.1"
  api: "0.165.2+26.3"
  mappings: mojang
  entrypoints:
    main: [org.example.ExampleMod]
repositories: [fabric, mavenCentral]
```

**`src/main/java/org/example/ExampleMod.java`**

```java
package org.example;

import net.fabricmc.api.ModInitializer;

public class ExampleMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // Your mod starts here.
    }
}
```

From the project directory, run:

```powershell
gradle help
gradle validateFabricAmper
gradle build
gradle verifyFabricAmperJar
```

The built mod JAR goes under `build/libs/`. The Fabric distribution contains one generated root `fabric.mod.json`. A real runtime test is still necessary to verify gameplay.

## Choose your loader

| Goal | `product` | Platform block | Output |
| --- | --- | --- | --- |
| Fabric mod | `fabric/mod` | `fabric:` | Fabric JAR and `fabric.mod.json` |
| NeoForge mod | `neoforge/mod` | `neoforge:` | NeoForge JAR and `neoforge.mods.toml` |
| Independent dual-loader | `[fabric/mod, neoforge/mod]` | Both | Two separate projects and JARs; no shared `common/` |
| Shared Architectury dual-loader | `[fabric/mod, neoforge/mod]` | Both + `architectury.enable: true` | `:common`, `:fabric`, `:neoforge`; separate distribution JARs |

For **NeoForge-only**, use the same `settings.gradle.kts` and this YAML instead:

```yaml
schema: "1.1"
product: neoforge/mod
mod:
  id: example_mod
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

Write a real Java/Kotlin class annotated with `@Mod("example_mod")`. The NeoForge output contains generated `META-INF/neoforge.mods.toml`, not Fabric's JSON.

For **dual-loader**, select `product: [fabric/mod, neoforge/mod]` and provide both loader blocks. With `architectury.enable: true`, place shared code in `common/src/main/java` (or Kotlin) and platform initialization in `fabric/` and `neoforge/`. Without Architectury, there is no shared `:common` module. Do not assume one universal JAR works in both loaders.

## Dependencies: shared versus loader-specific

**Schema 1.1 supports different dependency versions for Fabric and NeoForge.** Put dependencies in one root `dependencies` list and add `loader: fabric` or `loader: neoforge` when a dependency is loader-specific. Omitting `loader` keeps it shared. Schema 1.1 also supports the namespaced `fabric.gradleDependencies` and `neoforge.gradleDependencies` lists for platform-specific artifacts; those keys are not valid in historical schema 1.

For example, if *Mod A* publishes `1.0.1+26.3` for Fabric but `1.1.2+26.3` for NeoForge:

```yaml
# Fragment: add once at the root of your mod.yaml.
dependencies:
  - maven: "com.example:mod-a-fabric:1.0.1+26.3"
    loader: fabric
    kind: mod
    scope: all
  - maven: "com.example:mod-a-neoforge:1.1.2+26.3"
    loader: neoforge
    kind: mod
    scope: all
  - maven: "org.apache.commons:commons-lang3:3.14.0"
    scope: all
```

The coordinates above are **illustrative, not actual published artifacts**. Supply the correct repository and Maven or supported Modrinth coordinates. Fabric Amper does **not** automatically discover matching versions across loaders. The `loader` selector separates **requested dependencies**; Gradle may still choose a different resolved version due to an ordinary transitive version conflict, so check the platform's dependency report if an exact runtime version matters.

There are two distinct kinds of dependencies:

| Purpose | Fabric | NeoForge |
| --- | --- | --- |
| Gradle compilation/runtime artifact | `fabric.gradleDependencies[]` | `neoforge.gradleDependencies[]` |
| Loader-enforced mod requirement | `fabric.depends` (mod-ID → version-predicate map) | `neoforge.dependencies[]` (objects with `modId` and optional `versionRange`) |
| Platform-specific Maven repositories | `fabric.repositories[]` | `neoforge.repositories[]` |

Root `repositories[]`, `dependencies[]`, and `test-dependencies[]` are **arrays in both schema 1 and 1.1**; they did not change from objects to arrays in 1.1. The optional root `dependencies[].loader` discriminator is **new in v0.3.1 and schema 1.1**. Loader-omitted dependencies must remain neutral main libraries in a dual-loader build; a loader-specific mod dependency is applied only to its selected platform. Fabric Jar-in-Jar (`include: true`) is platform-specific and must not be assumed to work identically in NeoForge.

## Windows commands

The [v0.3.1 release](https://github.com/howShouldIChooseMyUsername/Fabric-Amper/releases/tag/v0.3.1) includes `fabric-amper.bat` and its SHA-256-pinned helpers.

```bat
fabric-amper.bat --version
fabric-amper.bat init my_mod
fabric-amper.bat init my_legacy_mod MyLegacyMod mod --legacy-schema1
fabric-amper.bat init my_neo MyNeo mod --mod-neoforge --kotlin
fabric-amper.bat init my_dual MyDual mod --mod-fabric --mod-neoforge --architectury
fabric-amper.bat -C "MyDual" build
fabric-amper.bat -C "MyDual" run --mod-fabric
fabric-amper.bat -C "MyDual" server --mod-neoforge
fabric-amper.bat -C "C:\path\to\legacy-mod" migrate --dry-run
fabric-amper.bat -C "MyDual" doctor
```

- **Every default `init`** (Fabric-only, NeoForge-only or dual-loader) generates explicit **`schema: "1.1"`** and the corresponding loader-specific YAML block(s). For an intentionally historical Fabric-only starter, pass `--legacy-schema1` to generate explicit integer `schema: 1` and the old field locations; NeoForge and dual-loader combinations reject that flag. The `migrate` command still pins schema 1 when converting existing Fabric Gradle projects.
- `--kotlin` creates Kotlin sources. `--architectury` enables a shared `:common` project for dual-loader mode. Fabric's `--splitEnvironmentSourceSets` is not a general NeoForge client-split feature.
- Dual-loader `run` / `server` requires **exactly one** `--mod-fabric` or `--mod-neoforge` selector; `build` validates and builds the selected project's loader outputs.
- `migrate` performs a conservative static conversion of an existing Fabric Gradle project. Preview with `--dry-run`; the normal path backs up originals under `gradle_backup/`. It does not port Java/Kotlin gameplay code automatically.
- Fabric Amper never accepts the Minecraft server EULA or supplies user authentication automatically.

See the [Quick Guide](HUMAN_QuickGuide.md) for detailed command combinations and safety checks.

## Schema versions

| YAML header | Interpretation |
| --- | --- |
| *(omitted)* | **Schema 1.1**, the v0.3.1 default |
| `schema: "1.1"` | Explicit schema 1.1 |
| `schema: 1` | Historical Fabric-only grammar |
| `schema: 1.1` | Invalid unquoted numeric value |

Schema 1.1 keeps shared identity in `mod:` and `minecraft.version` while placing Loader details in `fabric:` or `neoforge:`. Use only **one** of `mod.yaml` and `mod.yml`. The [JSON Schema](fabric-amper.schema.json) supports editor completion; application validation additionally checks semantic constraints and referenced resources.

### Historical schema 1 reference

For existing Fabric-only consumers, the older field locations remain available when explicitly selected:

```yaml
schema: 1
product: fabric/mod
mod:
  id: legacy_mod
  version: "1.0.0+26.3"
  entrypoints:
    main: [org.example.LegacyMod]
minecraft:
  version: "26.3"
  loader: "0.20.1"
  fabricApi: "0.165.2+26.3"
  mappings: mojang
settings:
  java:
    release: 25
repositories: [fabric, mavenCentral]
```

In schema 1, Fabric-specific Loader keys such as `mod.entrypoints` and `mod.depends` live under `mod`, and `minecraft.loader` is a root Minecraft subkey. **Do not paste this old layout into a schema 1.1 YAML.** Migration keeps a deliberate explicit schema 1 pin. See the [full legacy reference](HUMAN.md#historical-schema-1-reference).

## API modules and publishing

Select `product: fabric/api` or `neoforge/api` for a library/API profile. Set `mod.group` for Maven coordinates, optionally `mod.archivesName`, and `settings.java.sourcesJar: true` for a sources JAR. The `fabric/library` alias is equivalent to `fabric/api`. For a Fabric API's local Maven output:

```powershell
gradle build
gradle publishMavenJavaPublicationToFabricAmperLocalRepository
```

Remote Maven, Modrinth, and CurseForge publishing requires an explicitly configured destination and invocation. **`gradle build` never uploads a release.** See the [Human Guide](HUMAN.md#explicit-maven-modrinth-and-curseforge-publishing) for publish YAML, credential-environment-variable rules, limitations, and safe dry runs. Never put actual tokens or passwords into YAML.

## Verification and current limits

**v0.3.1 published and verified:** The GitHub prerelease contains nine assets, independently redownloaded and checked against all eight SHA-256 entries. JitPack `v0.3.1` reported status `ok`, Java 25 and all three expected modules, at the exact release-tag commit. A fresh **settings-only Fabric/Java consumer using public JitPack v0.3.1**, not a locally injected plugin, passed `help validateFabricAmper build verifyFabricAmperJar`, compiled a real initializer and packaged exactly one `fabric.mod.json`. The producer's Gradle 9.7.1/JDK 25 `clean build validatePlugins` passed **383 tests: 324 passed, 59 skipped, 0 failed**, and new local dual-loader isolation TestKit checks passed **2/2**.

**Prior release evidence:** v0.3.0 was independently published and verified on November 17, 2026, including a separate public NeoForge + Architectury + Kotlin consumer build. That is dated v0.3.0 evidence; the new **v0.3.1 public consumer smoke above is Fabric-only**, while loader isolation was checked in local dual-loader TestKit. Actual Minecraft client/server runtime, complete IntelliJ GUI Sync, all G1–G30/NF1–NF26 gates, and strict cold second-run configuration-cache reuse remain unapproved. **59 skipped tests are not passes.**

For dated test results, historical hashes, security checks and remaining gates, read [QA_REPORT.md](QA_REPORT.md) and [specs.md](specs.md). The README is a starting point, not the acceptance evidence archive.

---

**More documentation:** [Quick Guide](HUMAN_QuickGuide.md) · [Full Human Guide](HUMAN.md) · [AI Configuration Guide](FabricAmperSKILL.md) · [Compact AI Guide](FabricAmperSKILL_SYN.md) · [Schema](fabric-amper.schema.json) · [GitHub releases](https://github.com/howShouldIChooseMyUsername/Fabric-Amper/releases)
