# Fabric Amper — Quick Guide

**A five-minute Quick Start and a feature-by-feature cheat sheet, in one place.** This guide is for Windows developers who want to start a Fabric mod immediately and then look up YAML keys or commands without reading the entire manual. For complete explanations, see [the Human Guide](HUMAN.md); for exact YAML types, see [the schema](fabric-amper.schema.json).

> **Status:** The current launcher and generated plugin reference are `0.1.3`, still a **development preview** and not an approved G1–G30 shipping artifact or Gradle Plugin Portal release. Generated JitPack coordinates must be checked for actual availability. [QA_REPORT.md](QA_REPORT.md) contains earlier verification evidence and limitations; skipped gates are not silently treated as passes.

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

## The two-minute command path

**Check the launcher version:** run `fabric-amper.bat` without arguments or `fabric-amper.bat -V` (also `--version` / `version`). Both print `v0.1.3` and exit without Gradle, downloads, or project changes. Use `fabric-amper.bat help` for the full command list; an empty invocation displays the version instead of help.

**Prerequisites:** Windows, a compatible Gradle launcher (**9.7.1** is the project validation baseline), and **JDK 25** for the Minecraft **26.3** example. The Fabric Amper producer repository does not require a bundled Gradle Wrapper. The launcher finds installed/cached Gradle; **`doctor --fix` explicitly opts into repairing a missing JDK 25 or Gradle**, while `doctor --fix --dry-run` only previews the actions. From the checked-out **`Fabric Amper` producer folder**, run:

```bat
fabric-amper.bat doctor
fabric-amper.bat init quick_mod
cd ..\quick_mod
"..\Fabric Amper\fabric-amper.bat" build
```

**What happened?** `doctor` checks local Java/Gradle without contacting JitPack. `init` generates a **new** consumer directory with `settings.gradle.kts`, `mod.yaml`, a local `schemas/mod.schema.json` schema snapshot, and a Java source file. **A standalone BAT now automatically fetches the missing root `fabric-amper.schema.json` from GitHub `main` (not a Release PS1), verifies its BAT-pinned SHA-256, and installs it beside the BAT before creating the project.** Existing incorrect schemas are refused, not overwritten. If `main` changes its schema bytes, an old BAT's pinned hash may reject the download until the BAT is updated. The Settings script requests `com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.3` from **JitPack**, not a local producer Maven repository. Check that the artifact is really available before building. `build` validates, builds and checks the JAR. Internet access is required on first uncached downloads. The first line of the generated YAML is `# yaml-language-server: $schema=./schemas/mod.schema.json` which resolves to **`<new-project>/schemas/mod.schema.json`**, not the BAT directory. If `quick_mod` already exists, `init` refuses to overwrite it. If you run `init` outside the checkout folder, it creates the new project under your current directory instead.

Useful follow-ups from inside `quick_mod`:

```bat
"..\Fabric Amper\fabric-amper.bat" diagnose
"..\Fabric Amper\fabric-amper.bat" explain
"..\Fabric Amper\fabric-amper.bat" plan
```

`diagnose` describes selected toolchains and compatibility **without claiming an IDE or runtime check**; `explain` shows the origins of dependencies; `plan` builds/verifies a **local** release JAR and previews resolved publishing metadata, including a checksum, **without uploading**. These commands may create normal Gradle build/cache files. Use `fabric-amper.bat doctor --full` for extra JDK/path diagnostics or `doctor --paths` for a local Windows path-length estimate. If a toolchain is missing, `doctor --fix --dry-run` previews a repair. **Only run `doctor --fix` when you want it to install missing software or create missing consumer bootstrap/editor files.** On first use, the BAT requests `fabric-amper-doctor-fix.ps1` from its v0.1.3 GitHub Release into the local **`scripts/` directory**, checks its pinned SHA-256, then starts the repair. Existing unexpected scripts are not overwritten/executed, and failed downloads abort before repairs. `--dry-run` never downloads anything; if the helper is missing, it previews the proposed download. Windows cannot let a child batch file change its parent terminal's live PATH; a new terminal may be needed afterward.

**Single-BAT bootstrap (0.1.3):** Running `doctor --fix` fetches its missing, version-matched PS1 from `.../releases/download/v0.1.3/fabric-amper-doctor-fix.ps1` into **`scripts/fabric-amper-doctor-fix.ps1`** below the BAT. Likewise, normal `migrate` fetches missing `scripts/fabric-amper-migrate.ps1` and (if absent) root `fabric-amper.schema.json` from the versioned Release. **`init` also auto-fetches the missing schema, specifically from GitHub `main`.** Each file is checked against a pinned SHA-256 before use; existing mismatched files are never executed or overwritten. Missing/blocked GitHub, a bad checksum or a non-writable folder aborts. **Both `doctor --fix --dry-run` and `migrate --dry-run` remain read-only**: if a required helper/schema is missing, only its planned download is shown, not the full repair/migration preview. First run the normal command to install a verified helper, then run `migrate --dry-run` to preview conversions on another project.

### Before you edit anything

- A consumer maintains **only `settings.gradle.kts` and exactly one `mod.yaml` or `mod.yml` for build configuration**. Do **not** add a consumer `build.gradle`, `build.gradle.kts`, or a manually authored `src/main/resources/fabric.mod.json` (or client equivalent). You **still write** actual Java/Kotlin classes, Mixins, icons and other assets.
- Root `schema` is **optional**: `init` intentionally **never pins `schema: 1`**, so the plugin selects its latest implemented version (**currently v1**). Other explicit versions are rejected. `schemas/mod.schema.json` is a **snapshot at initialization time**, not an automatically updating remote schema; regenerate or replace it when upgrading the plugin's YAML grammar in an existing project. Never invent YAML fields: `fabricAmperDoctor`, `fabricAmperExplain`, `fabricAmperReleasePlan` and `fabricAmperPortingReport` are **Gradle tasks**, not configuration keys.
- Quote exact Minecraft, Loader, Fabric API and Maven version strings. Avoid YAML aliases/anchors, duplicate keys, unverified dependency coordinates and guessed platform IDs. The examples below are **demonstrations**, not guarantees that external packages or projects exist.
- `build` does **not** upload anything. `publish maven`, `publish modrinth`, `publish curseforge`, and `publish all` **can upload real releases**; review the exact destination, author permissions and credentials first. The wrapper asks for typed `PUBLISH` for these remote commands, but that prompt is not a substitute for explicit authorization.

## Migrate an existing Kotlin/Groovy Fabric mod

In **0.1.3**, use `migrate` when you already have a Fabric project with **one** `build.gradle.kts` (Kotlin DSL) **or** `build.gradle` (Groovy DSL), and a Fabric Loader `src/main/resources/fabric.mod.json`. The old build must use supported static Gradle statements and Loader metadata schema version 1. Conversion reads files and parses recognized settings; it **does not execute the old Gradle script**.

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

For both DSLs, `migrate` preserves the rest of `src/`, including compiled source inputs, Mixins, Class Tweakers and icons. New `settings.gradle.kts` requests Fabric Amper `0.1.3` using `com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.3` on JitPack; check remote availability before you build. It rejects existing YAML, existing `gradle_backup`, multiple root build scripts, ambiguous Loader JSON, unsupported extra plugins/Gradle DSL, dynamic coordinates, unrecognized resource paths or inconsistent version/toolchain settings. It does **not** rewrite custom tasks, arbitrarily evaluate Groovy/Kotlin code or guarantee that the resulting mod compiles. For Kotlin mods, especially check the generated `settings.kotlin` and Fabric Language Kotlin requirements. Choose ordinary `migrate` to keep a backup, then review `mod.yaml`, run `validate` and `build` and test gameplay separately. The [full manual](HUMAN.md#migrating-an-existing-fabric-gradle-mod-windows) includes Kotlin/Groovy examples, exact limitations and post-migration checks.

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
                useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.3")
            }
        }
    }
}
plugins {
    id("dev.fabric-amper.settings") version "0.1.3"
}
```

The `resolutionStrategy` redirects only the public Settings plugin ID to the requested JitPack module. The `plugins` version is `0.1.3`, while the Git tag requested in the JitPack coordinate is `v0.1.3`. Availability still requires a matching published artifact. Do not apply `dev.fabric-amper.project` yourself. Unlike the previous local-bootstrap workflow, **no `setup` command is needed**.

**`mod.yaml`** — a complete, Java-only Minecraft 26.3 example (the optional `schema: 1` is intentionally omitted). The first-line editor directive refers to a file that must exist:

```yaml
# yaml-language-server: $schema=./schemas/mod.schema.json
product: fabric/mod
mod:
  id: quick_mod
  version: "1.0.0+26.3"
  name: Quick Mod
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

When creating the project manually rather than using `init`, first create a `schemas` directory beside `mod.yaml` and copy the producer's current `fabric-amper.schema.json` to `schemas/mod.schema.json`. The directive is a YAML **comment** for editors, not a Fabric Amper configuration key. A newly created `init` project includes the correct schema copy automatically; the comment does not cause schema updates in existing projects after a plugin upgrade.

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
| Add a PNG icon | `mod.icon: assets/quick_mod/icon.png` | Put the PNG under `src/main/resources/assets/quick_mod/icon.png` |
| Add a Fabric entrypoint | `mod.entrypoints.main` or `.client` | Implement the Java/Kotlin initializer class |
| Set a client-only mod | `mod.environment: client` | Use client-safe entrypoints/Mixins; do not load client-only code on a dedicated server |
| Enable Kotlin | `settings.kotlin.enabled: true` | Use `src/main/kotlin`; fixed KGP `2.4.21` / Fabric Language Kotlin `1.14.2+kotlin.2.4.21` |
| Split client source sets | `settings.loom.splitEnvironmentSourceSets: true` | Use `src/client/java` and optionally `src/client/resources` |
| Add a client dependency | `dependencies[].target: client` | Requires split source sets; not allowed with `exported` or `include` |
| Register a Mixin | `mod.mixins: [example.mixins.json]` | Add the actual config and mixin classes; side-qualify client-only configs |
| Change Minecraft access | `mod.accessWidener: example.classtweaker` on 26.1+ | Actual file in `src/main/resources/`; header `classTweaker v1 official` |
| Target a legacy remap profile | `minecraft.version: "1.21.11"` and `settings.java.release: 21` | Change mappings/Loader/API to verified compatible legacy versions; use `.accesswidener` instead |
| Require another installed mod | `mod.depends` | Use **Fabric Loader mod ID + version predicate**, not Maven coordinates |
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
    fabricLanguageKotlin: "1.14.2+kotlin.2.4.21"
```

If the complete starter already contains `settings.kotlin`, edit that section **rather than duplicating it**. For client-only classes, set `settings.loom.splitEnvironmentSourceSets: true`, put code under `src/client/java` or `src/client/kotlin`, and define the `client` entrypoint. A Mixin config or icon must exist inside the allowed resources source set; a global/server Mixin cannot safely reference only a client resource. A `.classtweaker` on Minecraft 26.1+ and a legacy `.accesswidener` both belong under `src/main/resources` and require profile-correct headers.

## Dependencies, side safety, and test recipes

**Three different dependency systems must not be conflated:**

| Place in YAML | Purpose | Identifier |
| --- | --- | --- |
| `dependencies` / `test-dependencies` | Gradle compilation/runtime/test artifacts | Fixed Maven `group:artifact:version` or explicit Modrinth artifact data |
| `mod.depends` (and `recommends`, `suggests`, `breaks`, `conflicts`) | Constraints verified by Fabric Loader at game runtime | Loader **mod ID** and version predicate, e.g. `fabricloader: ">=0.20.1"` |
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
| `exported: true` | Exposes an API/library dependency to downstream consumers | `scope: all`, target `main`; not a substitute for `mod.depends` |
| `include: true` | Embeds a **non-transitive** JAR-in-JAR artifact | `scope: all`, target `main`; verify runtime loading separately |
| `target: client` | Client source-set dependency | Requires split source sets, non-remap profile, no `exported`/`include` |
| `kind: mod` / `library` | Selects the supported Gradle mod/library configuration | Choose based on what the artifact actually contains |

String shorthand, a one-key scope map (`"group:artifact:version": compile-only`), and typed `maven`/`modrinth` objects are supported. Fixed versions only: do not use `latest.release`, dynamic ranges, or Maven coordinates with guessed versions. `test-dependencies` follow analogous scopes but **cannot** use `target: client`, `exported: true`, or `include: true`. For a JUnit Jupiter sample and repository details, see [the complete Human Guide](HUMAN.md#repositories-and-dependencies).

### Loader relationships versus build dependencies

For an installed-mod requirement, merge into `mod`:

```yaml
mod:
  depends:
    fabricloader: ">=0.20.1"
    minecraft: "26.3"
```

Fabric Amper injects missing core Loader requirements for Minecraft, Fabric Loader, Java, and optional Fabric API/Kotlin; it **does not overwrite explicit predicates**. Use `fabric-amper.bat explain` to see which relationships were authored versus injected. A YAML `mod.depends` requirement does **not** download the referenced mod: add an actual Gradle artifact dependency separately when needed.

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
| *(no arguments)* / `-V` / `--version` / `version` | Print `v0.1.3` and exit without calling Gradle or changing files |
| `help` / `--help` / `docs` | Show full usage or documentation locations (empty invocation now prints only the version) |
| `doctor` | Check available Java/Gradle and report the JitPack coordinate, without contacting the network or clearing caches |
| `doctor --paths` / `--full` | Estimate Windows path risks; `--full` also prints JDK/toolchain hints |
| `doctor --fix --dry-run` | **Read-only, no network**. If PS1 is missing, show only its GitHub URL; if verified PS1 exists, preview JDK25/Gradle and missing consumer Settings/VS Code files |
| `doctor --fix` | **Opt-in changes:** request the matching v0.1.3 PS1 from this BAT's Release URL if missing (must exist and match pinned SHA-256), then install missing JDK25/Gradle or fill missing consumer Settings/editor files |
| `init <mod_id> [folder] [mod\|api]` | Create a **new** settings-only Java consumer; never overwrite an existing folder |
| `init <mod_id> --here [mod\|api]` | Initialize in the current folder **even when tool files already exist**. Refuses any existing YAML/Settings/build script/`src`/schema conflict; never overwrites project files |
| `migrate --dry-run` | Read-only preview: with verified `scripts/` helper/schema, show proposed YAML; if absent, only preview the required download |
| `migrate` / `migrate -C "folder"` | Auto-download/hash-check missing helper/schema, create `mod.yaml` and `settings.gradle.kts`, save original build script, old Settings file and Loader JSON in `gradle_backup/` |
| `migrate -D` / `migrate --delete` | Explicitly remove the original scripts/Loader JSON after successful conversion; combine with `--dry-run` to preview first |
| `tasks` | `gradle tasks --all` in the consumer |
| `validate` | `gradle validateFabricAmper` |
| `build` | `gradle validateFabricAmper build verifyFabricAmperJar` |
| `verify` / `test` / `clean` | `verifyFabricAmperJar` / `test` / `clean` in the consumer |
| `diagnose` | `fabricAmperDoctor`: local toolchain/project report, no upload |
| `explain` | `fabricAmperExplain`: Gradle artifacts vs Loader requirements, no remote resolution |
| `plan` | `fabricAmperReleasePlan`: builds/verifies local JAR and previews release metadata, **no upload** |
| `port 1.21.11` / `port 26.3` | `fabricAmperPortingReport`: manual migration checklist, does **not** port code |
| `run` / `server` | `runClient` / `runServer`: **launches the game**; user reviews and accepts server EULA manually |
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
