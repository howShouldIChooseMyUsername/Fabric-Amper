# Fabric Amper — v0.3.0 Public Prerelease (Fabric · NeoForge)

Fabric Amper is a **Gradle binary plugin** for developing Minecraft **Fabric mods, NeoForge mods, or both loaders** from `settings.gradle.kts` and exactly one `mod.yaml` or `mod.yml`. Consumer projects do not need a `build.gradle(.kts)`, a hand-authored `fabric.mod.json`, or a hand-authored `META-INF/neoforge.mods.toml`. Developers still write actual Java/Kotlin mod classes, Mixins, Class Tweakers/Access Transformers, icons, and other source files and resources. **Each loader produces its own distribution JAR and metadata; there is no single universal combined JAR.**

> **Current status — v0.3.0 GitHub prerelease published on 2026-11-17.** The tag and nine release assets were published; downloaded asset SHA-256 hashes matched. JitPack `v0.3.0` reported `ok` and a fresh **NeoForge + Architectury + Kotlin** consumer successfully resolved the remote plugin and ran `help` and `validateFabricAmper build verifyFabricAmperJar`. **Actual multi-loader Minecraft game runtime, IntelliJ GUI Sync, the full cold configuration-cache matrix, and complete G/NF acceptance gates remain unapproved.** The v0.1.0/0.1.3 JAR hashes and QA counts below are **dated historical evidence**, not results for v0.3.0 binaries. [Full human guide](HUMAN.md) · [Quick guide](HUMAN_QuickGuide.md) · [Full AI guide](FabricAmperSKILL.md) · [Compact AI guide](FabricAmperSKILL_SYN.md) · [QA history](QA_REPORT.md)

## What's new in v0.3.0 — schema 1.1, loader selection, separate JARs

| Root YAML `schema` | Meaning |
| --- | --- |
| **Omitted** | Defaults to **1.1**, with loader-specific `fabric:` / `neoforge:` namespaces |
| `schema: "1.1"` | Explicitly selects v1.1; recommended while migrating |
| `schema: 1` | Selects **historical Fabric-only v1**; see the [single reference section](#historical-schema-1-reference) |
| `schema: 1.1` | Invalid unquoted YAML number; use quoted `"1.1"` |

Schema 1.1 supports `fabric/mod`, `fabric/api`, `fabric/library` (API alias), `neoforge/mod`, `neoforge/api`, or a product array containing one target per loader. **Omitting the schema from an old v1 YAML file does not silently keep v1 behavior.** Explicitly write `schema: 1` when retaining the historical root Loader fields. If both YAML filename aliases exist, or neither exists, validation reports FA001. **Root `repositories`, `dependencies`, and `test-dependencies` are arrays in both v1 and v1.1**. New loader-specific `fabric.repositories`/`neoforge.repositories` and `fabric.gradleDependencies`/`neoforge.gradleDependencies` are also arrays; historical `mod.depends` and current `fabric.depends` are maps, while `neoforge.dependencies` is an array.

**`settings.gradle.kts` for v0.3.0** (independently verified using the public JitPack tag and a fresh consumer):

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

Public JitPack `v0.3.0` resolution and a new consumer build were actually verified, although network and external repository availability can vary. Apply **only** `dev.fabric-amper.settings` directly. Do not manually apply the internal Project plugin, Fabric Loom, or NeoForge ModDevGradle.

**Fabric-only schema 1.1 `mod.yaml`** (real example of the omitted-schema default):

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
fabric:
  loader: "0.20.1"
  api: "0.165.2+26.3"
  mappings: mojang
  entrypoints:
    main: [org.example.ExampleFabric]
repositories: [fabric, mavenCentral]
```

`org.example.ExampleFabric` must implement the actual Fabric `ModInitializer` interface. For a standalone NeoForge project, select `product: neoforge/mod`, replace the `fabric:` block with `neoforge:` containing `version: "26.3.0.101"` and `repositories: [neoforge]`, and use root `repositories: [mavenCentral]`. The project must contain a real `@Mod("examplemod")` entry point and `mod.license`. The Fabric JAR contains exactly one root `fabric.mod.json`; the NeoForge JAR contains one `META-INF/neoforge.mods.toml`, with no other loader's metadata. The NeoForge version is a **pinned illustration**, not a search for the latest release.

**Dual-loader schema 1.1 with an Architectury common module:**

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
  repositories: [neoforge]
repositories: [mavenCentral, fabric]
```

`architectury.enable: true` creates `:common`, `:fabric`, and `:neoforge`. Put shared code in `common/src/main/java` and loader-specific initialization in `fabric/src/main/java` and `neoforge/src/main/java`. **Omitting `architectury` or setting it to `false` creates only independent `:fabric` and `:neoforge` modules**, with no automatic code sharing. Distinguish root `dependencies` from loader-specific `fabric.gradleDependencies` / `neoforge.gradleDependencies` (Gradle classpath), and `fabric.depends` / `neoforge.dependencies` (Loader runtime metadata).

**Windows BAT initialization and loader-specific runs:**

```bat
fabric-amper.bat --version
rem Default Fabric/Java project:
fabric-amper.bat init examplemod
fabric-amper.bat init examplemod neo mod --mod-neoforge
fabric-amper.bat init examplemod dual mod --mod-fabric --mod-neoforge --architectury
fabric-amper.bat init examplemod independent mod --mod-fabric --mod-neoforge --kotlin
fabric-amper.bat init examplemod splitdual mod --mod-fabric --mod-neoforge --splitEnvironmentSourceSets
fabric-amper.bat -C "dual" build
fabric-amper.bat -C "dual" run --mod-fabric
fabric-amper.bat -C "dual" run --mod-neoforge
fabric-amper.bat -C "dual" server --mod-fabric
fabric-amper.bat -C "dual" server --mod-neoforge
```

No loader flag selects Fabric, only `--mod-neoforge` selects NeoForge, and both flags select dual. `--architectury` creates the dual common module; `--kotlin` generates Kotlin sources. `--splitEnvironmentSourceSets` is **Fabric-only** (rejected on NeoForge-only, applied only to Fabric in independent dual, ignored in Architectury dual). Dual `run` and `server` **require exactly one explicit loader choice**, dispatching to `runFabricClient`/`runNeoForgeClient` and `runFabricServer`/`runNeoForgeServer` respectively. These commands do not guarantee successful game runtime or automatically accept the Minecraft EULA.

**Windows `doctor` project-path options:** In addition to `fabric-amper.bat -C ".\httpmod" doctor`, use `fabric-amper.bat doctor -C ".\httpmod"` or the `--project` alias. For backward compatibility the extra trailing command in `doctor -C ".\httpmod" doctor` is also accepted. Relative paths resolve from the **current terminal directory**, not the BAT file directory. For other consumer commands, keep `-C` before the command.

## Migrating an existing Fabric Gradle mod to YAML — `migrate` (prepared for v0.3.0)

On Windows, this conservative migration command reads an existing **Kotlin DSL `build.gradle.kts` or Groovy DSL `build.gradle`** and **`src/main/resources/fabric.mod.json` or `src/client/resources/fabric.mod.json`**, then generates Fabric Amper `mod.yaml` and `settings.gradle.kts`. A **matching-version `fabric-amper-migrate.ps1`** must sit next to the BAT launcher. The migration code never evaluates the Gradle DSL; it recognizes only statically verifiable expressions.

```bat
rem From an existing Fabric Gradle project, selecting the v0.3.0 launcher:
"C:\tools\Fabric Amper\fabric-amper.bat" migrate --dry-run
"C:\tools\Fabric Amper\fabric-amper.bat" migrate

rem Select a project elsewhere (relative and absolute paths are supported):
"C:\tools\Fabric Amper\fabric-amper.bat" migrate -C ".\my-mod" --dry-run
"C:\tools\Fabric Amper\fabric-amper.bat" -C "D:\projects\my-mod" migrate

rem Explicitly request permanent deletion of originals after a successful conversion:
"C:\tools\Fabric Amper\fabric-amper.bat" migrate -C ".\my-mod" --delete
rem -D is an alias of --delete
```

By default, `migrate` **backs up the original Gradle scripts, previous Settings file (if present), and Loader JSON under `gradle_backup/`**. `--dry-run` shows static-analysis findings and proposed YAML without creating, changing, or deleting files. The optional `--delete` (`-D`) requests deletion of originals only after static conversion and output generation succeed; **that does not mean the converted project was built or run successfully**. Prefer backup mode if a restore might be needed. Existing `mod.yaml`/`mod.yml` or `gradle_backup/` makes the migration refuse to overwrite. Generated Settings uses the published **JitPack `v0.3.0` prerelease**. Verify builds and game runs after migration. Any migration output using historical Fabric root Loader keys **must explicitly emit `schema: 1`**, since the omitted-schema default now means v1.1.

Complex function calls, dynamic dependency expressions, arbitrary tasks/plugins, Gradle DSL requiring evaluation, and unsupported Loader metadata are **reported as errors or manual migration work** rather than guessed. This command does not rewrite Java/Kotlin/Mixin implementations or automatically port Minecraft versions. After migration, inspect sources, dependencies, and resources, and separately run `validate` → `build` → game/IDE checks. See [HUMAN.md](HUMAN.md) and [Quick Guide](HUMAN_QuickGuide.md) for conditions.
## Explicit publishing contract — verified Fabric history and schema 1.1 scope

> **Evidence boundary:** Most publishing acceptance results originated in earlier **Fabric schema 1** functionality. `publish.common`, local Maven publishing, independent-consumer task graphs, and platform upload `--dry-run` were tested; [QA §28](QA_REPORT.md) additionally confirmed **authenticated Fabric API Maven PUTs to `127.0.0.1` loopback HTTPS**. Those checks do not prove actual schema 1.1 NeoForge/dual remote publishing or external Modrinth/CurseForge transfer. Gradle 9.7.1 configuration-cache HIT for a real native remote publish task has also not been verified.

### What consumers declare

New consumers can use an optional top-level `publish:` block with schema 1.1 as illustrated below. Historical schema 1 publishing syntax, including the older `publishing.repository` option, is collected in the dedicated [historical schema 1 reference](#historical-schema-1-reference). `publish.maven` and `publishing.repository` cannot simultaneously configure remote publishing. Publishing each loader's v1.1 output must be supported by that loader's actual tasks and verification; historical standalone Fabric publishing results do not establish NeoForge success.

```yaml
schema: "1.1"
product: fabric/mod            # fabric/api and fabric/library also support publishing
mod:
  id: sample-mod
  version: "1.0.0+26.3"
  name: Sample Mod
  group: org.example           # required when publishing Maven artifacts
  license: MIT
minecraft:
  version: "26.3"
settings:
  java:
    release: 25
fabric:
  mappings: mojang
  loader: "0.20.1"
  api: "0.165.2+26.3"
  entrypoints:
    main: [org.example.SampleMod]
repositories: [fabric, mavenCentral]
publish:
  common:
    releaseType: release
    name: "Sample Mod 1.0.0+26.3"
    gameVersions: ["26.3"]    # default string gameVersionNames on CurseForge
    changelog: "Initial release"
  maven:
    url: https://maven.pkg.github.com/OWNER/REPOSITORY
    credentialsFromEnv: [GITHUB_ACTOR, GITHUB_TOKEN]
  modrinth:
    projectId: "AABBCCDD"     # existing Modrinth project ID or supported slug
    tokenEnv: MODRINTH_TOKEN
    loaders: [fabric]         # defaults to fabric
  curseforge:
    projectId: 123456         # existing numeric CurseForge project ID
    tokenEnv: CURSEFORGE_TOKEN
    gameVersionNames: ["26.3", "Fabric", "Client", "Server"]
```

The project IDs, Maven URL, token environment-variable names, and `org.example.SampleMod` are **illustrative placeholders**, not verified live publishing targets or a supplied entry-point implementation. You must use an existing authorized project and a creator token with the appropriate permissions. The plugin does not infer project creation, account access, or supported game versions. Changelog files must be regular files relative to the consumer project root; parent-directory traversal and symlink escape are prohibited. You cannot specify both a literal `changelog` and `changelogFile`.

**Schema version:** In v0.3.0, omitted `schema` defaults to 1.1, quoted `schema: "1.1"` explicitly selects 1.1, and `schema: 1` selects historical Fabric v1. Do not paste the historical root Loader fields into a configuration with an omitted schema. Unsupported schema versions and unquoted numeric `schema: 1.1` are rejected with FA003.

**Shared publishing layer:** `publish.common` fields `releaseType`, `name`, `gameVersions` (array of strings), `changelog`/`changelogFile`, and `dependencies` are inherited **only by explicitly declared Modrinth/CurseForge destinations**. `name` maps to Modrinth `versionName` and CurseForge `displayName`; `gameVersions` maps to Modrinth `gameVersions` and CurseForge **string** `gameVersionNames`. Numeric CurseForge version IDs are not inferred. Common dependency entries are `{slug, type, modrinthId?, curseforgeId?}` with types `required/optional/incompatible/embedded`, mapped to platform-specific relation types. A platform's matching ID/slug overrides the common entry; new platform entries are appended, and an explicit `[]` clears that platform's inherited list. Each destination always needs its own `projectId` and `tokenEnv`. `common` alone creates no upload destination. See [HUMAN.md](HUMAN.md#share-release-fields-with-publishcommon).

### Exact YAML fields and defaults

- `publish.maven` accepts only **HTTPS URLs** without query strings, fragments, or embedded username/password. `credentialsFromEnv: [USERNAME_VARIABLE, PASSWORD_VARIABLE]` requires exactly two environment-variable **names**. Their values are supplied **when the publish task runs**; omitting credentials attempts unauthenticated publication. Maven coordinates are `mod.group:(mod.archivesName ?: mod.id):mod.version`, requiring a valid `mod.group`. GitHub Packages and standard HTTPS Maven repositories are supported targets.
- `publish.modrinth` supports `projectId` (string), `tokenEnv` (environment-variable name), `releaseType` (`release|beta|alpha`, default `release`), `versionName` (default mod name + version), `versionNumber` (default `mod.version`), `gameVersions` (nonempty string array, default `[minecraft.version]`), `loaders` (default `[fabric]`), optional `environment` (one of the nine Modrinth installation-side enums), `changelog` or `changelogFile`, and optional `dependencies`. Dependencies are an array of `{projectId, type: required|optional|incompatible|embedded}` and are published **only when users explicitly specify platform IDs**. Neither Maven coordinates nor Loader dependencies imply an external Modrinth project ID.
- `publish.curseforge` supports `projectId` (positive integer), `tokenEnv`, `releaseType` (default `release`), `displayName` (default mod name + version), `gameVersionNames` (default `[minecraft.version, Fabric, Client, Server]` with the relevant side only for client/server environment), optional `gameVersions` (author-verified **integer** version IDs), `changelog` or `changelogFile`, `changelogType` (`text|markdown|html`, default `markdown`), `manualRelease` (default `false`), and optional `relations`. Relations use `{slug, type: embeddedLibrary|incompatible|optionalDependency|requiredDependency|tool}` and may specify a string `projectId` when exact resolution is required. The plugin does not guess how to repair invalid external platform IDs.
- The plugin can infer only the **platform, mod version, and distribution JAR** from the current product profile. Modrinth's default `environment` maps Loader-side environment values `*`/`client`/`server` (for example, schema 1.1 `fabric.environment` or historical schema 1 `mod.environment`) to `client_and_server`/`client_only`/`server_only`; Loader side eligibility alone does **not** establish where installation is required. Set `publish.modrinth.environment` explicitly when the actual mod's support differs. Cross-platform dependency IDs, version-tag availability, and multi-game compatibility are not inferred.
- Unknown `publish`/subsection keys, missing required values, invalid environment-variable names, path escapes, and ambiguous/duplicate Maven remotes are rejected early with the existing FA003 diagnostics. Raw tokens and passwords must not be written into YAML, POM, `.module`, Gradle logs, error messages, or verification evidence.

### Gradle tasks and execution-time safety boundaries

Reading YAML during Settings plugin **configuration never makes HTTP requests or checks token values**. Real publish tasks perform their required build and verification when explicitly invoked. A valid **Fabric** production JAR (non-remap `jar` for Minecraft 26.3 or legacy `remapJar`) must contain exactly one root `fabric.mod.json`. **NeoForge** must verify `META-INF/neoforge.mods.toml` in its own platform JAR. Fabric historical publishing tests do not establish a working NeoForge upload.

```powershell
gradle build                        # Build only; never upload
gradle publishFabricAmperModrinth    # Only Modrinth, if configured
gradle publishFabricAmperCurseForge  # Only CurseForge, if configured
gradle publishFabricAmperMaven       # Only remote Maven, if configured
gradle publishFabricAmper            # All explicit publish destinations
gradle publish                       # Standard Gradle Maven publish + selected platforms
```

`publishFabricAmper` aggregates **only destinations explicitly listed under the new `publish:`**. Gradle's standard `publish` also runs configured `maven-publish` tasks. In particular, even when only a platform was specified, standard `publish` on **`fabric/api` or `fabric/library`** writes a local Maven publication into the consumer's `build/fabric-amper/maven-repository`. This does not mean uploading the same remote task twice, but the two commands have different side effects. Use the specific `publishFabricAmper...` task for a single site. Multi-site publishing is **not a distributed transaction**; partial success is possible. POST requests are not automatically retried, to avoid duplicate versions, and existing published versions are not silently overwritten. HTTP 401/403, duplicate/validation errors, and 429/5xx display the status and platform error category without printing tokens, request bodies, or server response bodies.

**Modrinth/CurseForge** token environment variables are read with Gradle Providers when the explicit publish task executes. **Remote Maven is an exception:** Native Gradle `maven-publish` `PasswordCredentials` uses string properties. When the actual remote task is realized, environment-provider values are bound to credentials and a `doFirst` check rejects blanks with FA003. Normal `help`, `build`, and local publication do not realize this task and do not require remote credentials; `--dry-run` does no task actions or uploading and does not assign explicit credentials. Gradle 9.7.1 considers native `PublishToMavenRepository` tasks with explicit credentials **configuration-cache incompatible**, so real Maven remote publication does not promise CC STORE/HIT. `--offline` also blocks publishing to `127.0.0.1` HTTPS. The plugin does not change account logins, private Minecraft profiles, Maven user home, or server EULA.

### Actual HTTP request contract — no external Modrinth/CurseForge uploads performed

| Destination | Official endpoint and authentication | Multipart body or publication |
| --- | --- | --- |
| **Modrinth** | `POST https://api.modrinth.com/v2/version`, `Authorization: <token>`, distinct `User-Agent`; token needs version-creation permission | `data`: JSON (`project_id`, `file_parts: ["file"]`, `primary_file: "file"`, `name`, `version_number`, `version_type`, `loaders`, `game_versions`, default `featured: false`, `dependencies: []`, `changelog`, `environment`); `file`: actual production JAR |
| **CurseForge** | `POST https://minecraft.curseforge.com/api/projects/{projectId}/upload-file`, `X-Api-Token: <token>`; distinct from the read-only `api.curseforge.com/v1` API | `metadata`: JSON (`changelog`, `changelogType`, `displayName`, `releaseType`, `gameVersionNames`, optional integer `gameVersions`, `isMarkedForManualRelease`, explicitly authored `relations.projects`); `file`: actual production JAR |
| **Maven** | Gradle `maven-publish` with `MavenPublication.from(components["java"])`, HTTPS repository, and env-based `PasswordCredentials` | Production library JAR, optional sources JAR, correct POM and Gradle `.module`; no duplicate source/component publication |

The CurseForge creator Upload API accepts the **string** `releaseType` `release|beta|alpha`; this differs from numeric enums returned by the separate CurseForge lookup API. `gameVersionNames` are platform tag names, not guaranteed numeric version IDs, and no arbitrary fixed Fabric ID should be fabricated. Modrinth `file_parts` must exactly match multipart part names. The plugin does not automatically prefix tokens with `Bearer `.

**Official sources checked on 2026-11-13:** [Modrinth Create Version](https://docs.modrinth.com/api/operations/createversion/), [Modrinth API overview (token/User-Agent/rate limits)](https://docs.modrinth.com/api/), [CurseForge creator Upload API (updated 2026-09-27)](https://support.curseforge.com/support/solutions/articles/9000197321), [CurseForge project submission/review](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project), and [CurseForgeGradle](https://github.com/Darkhax/CurseForgeGradle). **Actual external Modrinth/CurseForge HTTP upload, token permissions, and review approval were not verified.** Earlier authenticated loopback Maven PUT results are separate.
## Fabric API/library publishing contract

> **Scope of recorded verification:** The local Maven publishing and downstream dependency TestKit PASS results originated with the historical Fabric API/library implementation. They do not by themselves verify native publishing and downstream consumption of schema 1.1 `neoforge/api` or both loaders. For new API products, use schema 1.1 and loader-specific namespaces; check the actual publishing and consumer evidence for each loader.

### Product selection and artifacts

- `product: fabric/mod` preserves the standard Fabric mod build and YAML compatibility; it does not automatically apply Maven publishing.
- `product: fabric/api` builds a **real Fabric library mod recognized by Fabric Loader**, with generated `fabric.mod.json`, entry-point/Mixin/Class Tweaker/resource validation, Loom and run tasks, plus automatic `java-library` and `maven-publish` application.
- `product: fabric/library` is a **complete alias** of `fabric/api`, normalized to the same API profile. Tasks, output JAR, POM, Gradle Module Metadata, and publishing behavior must match. The generated Loader JSON does not contain the product name.
- For every product, consumers author just `settings.gradle.kts` and exactly one `mod.yaml` or `mod.yml`. No consumer-authored `build.gradle(.kts)` or `fabric.mod.json` is required.
- API products use the Java 25/Minecraft 26.3 non-remap path by default. **Actual legacy Minecraft 1.21.11/Java 21 API publishing TestKit passed a byte-for-byte check of final `remapJar`/`remapSourcesJar` output in local Maven.** This does not prove legacy game runtime or remote Maven upload.
- Java source JARs follow `settings.java.sourcesJar: true` by default, with explicit `false` respected. Source publishing is recommended for downstream API consumers. Javadoc JAR, Kotlin, client/main split, and JAR-in-JAR follow their specific YAML settings rather than being enabled automatically.

### Minimal schema 1.1 API/library configuration

```yaml
# yaml-language-server: $schema=./fabric-amper.schema.json
schema: "1.1"
product: fabric/api             # fabric/library is an equivalent alias
mod:
  id: sample-api
  name: Sample API
  version: "1.0.0"
  group: org.example.fabric     # required for API Maven publishing
  archivesName: sample-api      # defaults to mod.id
  license: MIT
  description: Example Fabric library mod
minecraft:
  version: "26.3"
settings:
  java:
    release: 25
    sourcesJar: true
fabric:
  environment: "*"
  mappings: mojang
  loader: "0.20.1"
  api: "0.165.2+26.3"
  loom:
    splitEnvironmentSourceSets: false
  entrypoints:
    main:
      - org.example.api.SampleApiMod
repositories:
  - mavenCentral
dependencies:
  # Public API types propagate to downstream compile and runtime.
  - maven: "org.example:public-contract:1.2.3"
    exported: true
  # Internal implementation: transitive at runtime, not in compile API.
  - maven: "org.example:internal-helper:2.0.0"
    scope: all
  # Compile-only tooling is not a required Maven POM dependency.
  - maven: "org.example:compile-annotations:1.0.0"
    scope: compile-only
```

The `org.example` coordinates are **fictional syntax illustrations**. Substitute fixed versions available from a real Maven repository before building. Use the v0.3.0 Settings-plugin example above. `mod.group` cannot be inferred when absent from an API product; its Maven coordinates are `groupId = mod.group`, `artifactId = mod.archivesName ?: mod.id`, `version = mod.version`. Root `repositories`, `dependencies`, and `test-dependencies` are **arrays in both schema 1 and 1.1**. The loader-specific `fabric.repositories`/`neoforge.repositories` and `fabric.gradleDependencies`/`neoforge.gradleDependencies` are also arrays.

### Automatic Maven publishing

Reading an API product automatically configures consumer `java-library`, `maven-publish`, and the `mavenJava` publication. **Configuration and ordinary builds do not upload anything without an explicit publish task.** Ordinary `fabric/mod` projects do not get automatic API publishing. The published artifacts include the final runnable Fabric mod JAR (exactly one root `fabric.mod.json`), optional source JAR, `pom.xml`, and Gradle `.module`. Source JARs already attached to the Java component must not be added twice.

The default local repository is **inside the consumer project**, `build/fabric-amper/maven-repository/`, and its task is `publishMavenJavaPublicationToFabricAmperLocalRepository`. `publishToMavenLocal` writes to `~/.m2` only when explicitly requested. The historical `publishing.repository` configuration and its restrictions are collected in the single [schema 1 reference](#historical-schema-1-reference); new publishing uses the documented `publish.maven` contract. Gradle standard `publish` sends to configured Maven repositories, so verify the outputs with the individual local publish task before intentional remote publishing. `publishing.repository` alone does not upload to CurseForge or Modrinth, and Maven Central/Gradle Plugin Portal publication is not configured automatically.

### POM and dependency transitivity (implementation and independent-consumer acceptance criteria)

The authoritative publishing model is the Java library component together with actual Loom publishing variants. Prefer `from(components["java"])` to manually guessing POM XML. Generate both rich Gradle `.module` metadata and Maven POM, acknowledging that **Maven POM cannot fully encode every Gradle variant or client-side environment distinction**. For this feature, "correct transitive dependencies" means the following supported behavior:

| YAML dependency | API library Gradle configuration | Maven POM result | Downstream behavior |
| --- | --- | --- | --- |
| `scope: all`, `exported: true` | `api` (legacy mod dependencies: `modApi`) | `compile` | Public API types propagate to compile and runtime |
| `scope: all`, `exported: false` | `implementation` / `modImplementation` | `runtime` | Hidden from public compile API, available at runtime |
| `scope: runtime-only` | `runtimeOnly` / `modRuntimeOnly` | `runtime` | Runtime only |
| `scope: compile-only` | `compileOnly` / `modCompileOnly` | None | Not a required transitive consumer dependency |
| `target: client` | Separate client configuration | Absent from default main Java POM | Must not leak into server/common API |
| `test-dependencies` | Test configurations | None | Not included in published dependencies |
| `include: true` with valid `scope` | Selected configuration **and** Loom `include` | Once, according to scope | Loader nested JAR and Maven transitivity have different roles |

Only explicitly `exported: true` dependencies are published as public API; ordinary dependencies are not indiscriminately promoted to `api`. `include` controls **bundling**, not API export, and must neither duplicate POM entries nor unconditionally remove external transitivity. Inspect Minecraft, Fabric Loader, and Fabric API platform dependency scopes in real Java/Loom variants, and separately inspect actual Loader `depends`; do not silently convert every POM dependency to `compile`. Loader runtime requirements and Maven transitivity are independent metadata.

If an upstream Maven or Modrinth module has no POM or valid metadata, Fabric Amper **cannot infer missing transitive dependencies on its behalf**. In particular, do not assume a Modrinth Maven artifact has the same transitivity as its author's own Maven publication. Use independent Gradle/Maven consumers to check `api`, `implementation`, `runtime-only`, `compile-only`, and `include`, and inspect real POM/`.module`/JAR output for the `fabric/library` alias and legacy remap variants before calling those cases passed.

For example, a downstream Gradle mod can register the publishing repository and import `implementation("org.example.fabric:sample-api:1.0.0")`. It should compile against public types from `public-contract`, have no compile-API access to private types from `internal-helper`, and receive necessary runtime dependencies. **These are test-case placeholders; the example coordinates do not represent a published artifact.**

### IDE completion and SchemaStore submission

The root `fabric-amper.schema.json` describes **both schema 1 and schema 1.1**. An omitted `schema` selects current 1.1; explicit `schema: 1` selects the historical Fabric grammar. It distinguishes v1.1 NeoForge/dual loader products and namespaces. The schema uses JSON Schema **Draft-07** for broad IDE support. It provides completions and static hints, not a replacement for the strict parser/validator; `default` is an editor hint, not an automatically inserted plugin setting.

For VS Code's YAML extension, link the file using the example's `# yaml-language-server: $schema=./fabric-amper.schema.json` first-line comment or `yaml.schemas` configuration. **A relative schema URL works only if the schema was copied into the consumer project**; otherwise use a genuinely hosted schema URL. In IntelliJ IDEA, use JSON Schema Mappings to associate `mod.yaml`/`mod.yml` with the schema. Do **not** add a root `$schema:` YAML field, which the plugin does not allow. Avoid publishing an overly broad automatic SchemaStore association such as every global `mod.yaml`; let users choose schema URL/mapping explicitly. SchemaStore listing submission is a separate user action.

### References and validation scope

- Real owo-lib library build and metadata on a historical branch: [public Gradle configuration](https://github.com/wisp-forest/owo-lib/blob/braid-ui/build.gradle), [Loader metadata](https://github.com/wisp-forest/owo-lib/blob/braid-ui/src/main/resources/fabric.mod.json), and [consumer setup](https://docs.wispforest.io/owo/setup). `include api(...)`, `from components.java`, and `withSourcesJar()` were observed; this does not make them universal 26.3 requirements.
- [Fabric 26.3 example Gradle setup](https://github.com/FabricMC/fabric-example-mod/blob/26.3/build.gradle), [Fabric Loom documentation](https://docs.fabricmc.net/develop/loom/), and [GeckoLib 5 Fabric setup](https://wiki.geckolib.com/docs/geckolib5/setup/fabric/quick-reference/). GeckoLib multi-platform distribution is distinct from Maven publishing.
- [Gradle Java Library](https://docs.gradle.org/current/userguide/java_library_plugin.html), [Maven Publish](https://docs.gradle.org/current/userguide/publishing_maven.html), [Module Metadata](https://docs.gradle.org/current/userguide/publishing_gradle_module_metadata.html), [SchemaStore CONTRIBUTING](https://github.com/SchemaStore/schemastore/blob/master/CONTRIBUTING.md), and [YAML Language Server](https://github.com/redhat-developer/yaml-language-server/blob/main/README.md).

## Historical schema 1 reference

This is the **single reference section for the former Fabric-only YAML grammar**. It exists for older consumers, migration output, and dated publishing tests. The **current default is schema 1.1**; use the loader-specific examples above for new projects. Schema 1 must be selected explicitly with `schema: 1`. The root `repositories`, `dependencies`, and `test-dependencies` were **already arrays** in schema 1. Historical `mod.depends` was a map; in schema 1.1 that Fabric map is `fabric.depends`, while NeoForge's `neoforge.dependencies` is an array. These namespace changes must not be mistaken for an array migration of the root fields.

Historical standalone Fabric API/library example:

```yaml
schema: 1
product: fabric/api            # fabric/library is an equivalent alias
mod:
  id: sample-api
  name: Sample API
  version: "1.0.0"
  group: org.example.fabric
  archivesName: sample-api
  license: MIT
  environment: "*"
  entrypoints:
    main:
      - org.example.api.SampleApiMod
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
    sourcesJar: true
  loom:
    splitEnvironmentSourceSets: false
repositories:
  - mavenCentral
dependencies:
  - maven: "org.example:public-contract:1.2.3"
    exported: true
  - maven: "org.example:internal-helper:2.0.0"
    scope: all
test-dependencies: []
```

Historical `publish:` block on a Fabric `schema: 1` `fabric/mod` / `fabric/api` / `fabric/library` project (append to a valid historical root configuration):

```yaml
publish:
  common:
    releaseType: release
    name: "Sample Mod 1.0.0+26.3"
    gameVersions: ["26.3"]
    changelog: "Initial release"
  maven:
    url: https://maven.pkg.github.com/OWNER/REPOSITORY
    credentialsFromEnv: [GITHUB_ACTOR, GITHUB_TOKEN]
  modrinth:
    projectId: "AABBCCDD"
    tokenEnv: MODRINTH_TOKEN
    loaders: [fabric]
  curseforge:
    projectId: 123456
    tokenEnv: CURSEFORGE_TOKEN
    gameVersionNames: ["26.3", "Fabric", "Client", "Server"]
```

For historical API/library users, `publishing.repository` configures a legacy remote Maven target:

```yaml
publishing:
  repository:
    url: https://maven.example.org/releases
    usernameEnv: FABRIC_AMPER_MAVEN_USERNAME
    passwordEnv: FABRIC_AMPER_MAVEN_PASSWORD
```

**Do not specify both remote `publish.maven` and `publishing.repository`.** They conflict. The latter requires an API/library product; without `repository`, it publishes only to the local repository. HTTPS URLs must not contain user information, queries, or fragments. `usernameEnv` and `passwordEnv` are the **names** of secret-bearing environment variables, not literal secrets; missing values can work only for an anonymously accessible target. The explicit remote task is `publishMavenJavaPublicationToFabricAmperRemoteRepository`. Neither this legacy block nor `publish` creates a CurseForge/Modrinth project or publishes to Maven Central or Gradle Plugin Portal. Unknown keys or use on ordinary `fabric/mod` are configuration errors. To preserve configuration-cache behavior, the plugin does not eagerly resolve remote dependencies during application.

The historical block above was recorded for Fabric-only functionality. **It is not a template for current NeoForge or dual projects**, and the placeholder project IDs, Maven URL, and coordinates must be replaced before actual use.
## Environment and plugin — published v0.3.0 prerelease

- Plugin ID: `dev.fabric-amper.settings` (apply **only this one** directly in consumer Settings); internal Project plugin: `dev.fabric-amper.project`.
- **Distribution:** The `v0.3.0` GitHub prerelease is published. All nine assets were downloaded and SHA-256 verified; JitPack resolved remotely, and a new NeoForge + Architectury + Kotlin consumer built successfully. BAT `FA_VERSION=0.3.0`, generated Settings `v0.3.0` coordinates, and producer version were aligned. Subsequent documentation/source cleanup must not change already published assets or checksums.
- Example v1.1 versions: Minecraft **26.3**, Java **25**, Fabric Loader **0.20.1**, Fabric API **0.165.2+26.3**, NeoForge **26.3.0.101** (pinned example, not automatic latest-version discovery), and official Mojang class names. Verify JDK 25/Gradle and the requirements of each loader plugin.
- Historical **Fabric-only** verification: Gradle **9.7.1**, plus exact **Gradle 9.7.0 + Loom 1.18.4** and **Gradle 9.9.1 + Loom 1.19.4** combinations, built successfully. This is not acceptance of every v0.3.0 NeoForge/dual/Kotlin/game-runtime combination.

## Minimal consumer project — v0.3.0 / schema 1.1

Directory layout:

```text
my-fabric-mod/
├── settings.gradle.kts
├── mod.yaml
└── src/main/java/org/example/ExampleMod.java
```

Use the same v0.3.0 `settings.gradle.kts` configuration shown earlier. **The public JitPack `v0.3.0` artifact was independently resolved and used in a new consumer build.** A Git tag alone does not guarantee a working JitPack build; this tag was separately verified. It has not been published to the Gradle Plugin Portal.

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

For a temporary local Maven publication inside the project, run the following **from the Fabric Amper producer project**. It does not modify global Maven Local or unrelated projects.

```powershell
gradle publishAllPublicationsToG29ProbeRepository -PfabricAmperProbeMavenRepo=true
```

You can then add this repository **ahead of remote repositories** in a consumer's `pluginManagement.repositories`. Adjust the relative path for the actual consumer location. The producer's `clean` removes this temporary publication; publish it again when necessary.

```kotlin
maven { url = uri("../Fabric Amper/build/g29-probe/maven-repo") }
```

Minimal **v1.1 Fabric `mod.yaml`** (omitted `schema` defaults to 1.1):

```yaml
product: fabric/mod
mod:
  id: example_mod
  version: "1.0.0+26.3"
  group: org.example
  archivesName: example-mod
  name: "Example Mod"
minecraft:
  version: "26.3"
settings:
  java:
    release: 25
    sourcesJar: true
  kotlin:
    enabled: false
fabric:
  mappings: mojang
  loader: "0.20.1"
  api: "0.165.2+26.3"
  entrypoints:
    main:
      - org.example.ExampleMod
repositories:
  - fabric
  - mavenCentral
```

`src/main/java/org/example/ExampleMod.java`:

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

With the actual v0.3.0 plugin resolved and compatible JDK 25/Gradle installed, run `gradle help` and `gradle build` in the consumer directory and inspect its distribution JAR. For **this standalone Fabric example**, the expected path is `build/libs/example-mod-1.0.0+26.3.jar` with exactly one root `fabric.mod.json`. `gradle runClient` starts a separate game run; a successful build alone is not runtime acceptance. For dual projects, inspect each loader's submodule JAR and select `runFabricClient` or `runNeoForgeClient`. Do not author `fabric.mod.json` or `neoforge.mods.toml` manually. Having both `mod.yaml` and `mod.yml` produces FA001.

Use `runServer` only with a separate test server. The plugin does not automatically accept the Minecraft EULA or modify existing server worlds, accounts, authentication settings, or EULA state. Full dedicated-server startup acceptance is still pending.

### Opening the project in IntelliJ IDEA

Open the **consumer folder itself as a Gradle project** in IntelliJ IDEA, checking trust, JDK 25, and Gradle settings yourself. Do not create `build.gradle.kts`. In the Gradle tool window, inspect tasks such as `compileJava`, `generateFabricModJson`, and `runClient`, together with Java/Kotlin source sets and external dependencies. After editing `mod.yaml` or `mod.yml`, use **Reload All Gradle Projects** for a manual sync. Automatic sync depends on the IDE installation's Gradle auto-reload settings. **Actual first import, manual sync, and automatic sync have not each passed GUI acceptance tests.** Successful Tooling API IdeaProject/model reads do not establish GUI Sync success.

## Main schema features

- Schema 1.1 shares the common `mod` identity and `minecraft.version`, while separating **Fabric** `fabric.entrypoints` / `fabric.mixins` / `fabric.depends` from **NeoForge** `neoforge.mixins` / `neoforge.dependencies` / `neoforge.accessTransformers`. Explicit schema 1 retains its older fields, documented in the single [historical reference](#historical-schema-1-reference).
- `dependencies` supports Maven and Modrinth coordinates, `compile-only` / `runtime-only` / `exported` / `include` / `target: client`. **Fabric `include` JAR-in-JAR behavior cannot simply be generalized to NeoForge**; distinguish each platform's classpath and runtime metadata.
- Schema 1.1 `fabric.loom.splitEnvironmentSourceSets: true` applies only to Fabric. The corresponding option is rejected for NeoForge-only projects and affects only Fabric source sets in independent dual projects. The schema 1 location appears in the [historical reference](#historical-schema-1-reference).
- `settings.kotlin.enabled: true` selects pinned KGP and Fabric Language Kotlin combinations. FLK is a **Fabric runtime adapter**, not a substitute for a NeoForge Kotlin `@Mod` entry point. Download costs for KGP classpath dependencies can remain even with `enabled: false`.
- `architectury.enable: true` creates a `common/` module and Architectury API dependencies for both sides of a dual project. Omission or `false` creates independent loader modules. This capability is **not attributed to the old publicly distributed 0.1.3 binary**; a new NeoForge + Architectury + Kotlin consumer built with the published v0.3.0 plugin, while game runtime remains unverified.
- Fabric's configuration mirror under `.gradle/fabric-amper/loom/` must agree with the final `fabric.mod.json`. NeoForge generates its own `META-INF/neoforge.mods.toml` and follows separate resource/archive rules.

### Read-only diagnostics and release previews

In a valid settings-only consumer folder, explicitly invoke these Gradle tasks. They require no additional YAML keys and do **not** run automatically during `build` or `check`.

```powershell
gradle fabricAmperDoctor
gradle fabricAmperExplain
gradle fabricAmperReleasePlan
gradle fabricAmperPortingReport -PfabricAmperPortTarget=26.3
```

- `fabricAmperDoctor` reports Minecraft/Java/Gradle/Loom profiles, Kotlin and source-set settings, and **estimated** Windows path-length risks. It reports `UNKNOWN` when the installed Loom version cannot be established reliably and does not delete caches.
- `fabricAmperExplain` distinguishes Maven/Modrinth artifact `dependencies`, Loader dependencies (`fabric.depends` under v1.1), and release-site relations (`publish.common.dependencies`). It displays scopes, targets, include/export flags, and default versus authored Loader values without querying repositories.
- `fabricAmperReleasePlan` previews the real verified JAR's hash, Loader ID/version, and inherited/overridden platform metadata **offline**. It does not print secret tokens, changelog contents, or upload anything. External IDs and compatible tags still require independent confirmation.
- `fabricAmperPortingReport` produces a **manual checklist** for mapping, Java, and resource changes between the current and target versions; it does not automatically port source code or Mixins.

On Windows, use `fabric-amper.bat diagnose`, `explain`, `plan`, `port 26.3`, `doctor --full`, or `doctor --paths`. See [HUMAN.md](HUMAN.md#read-only-developer-diagnostics-and-release-preview) for usage and [PROBLEMS.md](PROBLEMS.md) for further ideas and unresolved limitations.
## Validation status and limitations

**Published v0.3.0 prerelease verification:** On Gradle 9.7.1/JDK 25, `clean build validatePlugins --offline` completed with **371 tests = 314 PASS + 57 SKIP, 0 FAIL/ERROR**. Unit tests, documentation YAML parsing, and Windows BAT dispatch tests passed. Selected consumer builds and TestKit runs covered standalone NeoForge and Fabric/NeoForge dual setups (shared Architectury or independent modules) in Java and Kotlin. **Nine published GitHub assets were downloaded and SHA-256 verified; JitPack `v0.3.0` resolved remotely; and a new NeoForge + Architectury + Kotlin consumer passed `help`, validation, build, and JAR verification.** Actual Minecraft client/server runtime, IntelliJ GUI Sync, a strict configuration-cache HIT on the second cold run, and all NF/G acceptance gates **remain unapproved**. Initial creation of the common JAR has caused an extra configuration-cache invalidation in some tests; a later warm HIT must not be confused with a cold second-run HIT. Consult [QA_REPORT.md](QA_REPORT.md) and [HUMAN.md](HUMAN.md) for exact gate evidence.

**The paragraphs below preserve dated historical Fabric-only v0.1.0/0.1.3 tests and publishing extensions.** Selected tests covered independent Fabric consumer Gradle builds, two G13 combinations, G25 native `.module` metadata, G20 parallel mirror recovery, official Loader version comparisons, real Loader invocation and library calls for Maven JAR-in-JAR, a real Modrinth client-only dependency compile, positive and negative Java compilation for YAML custom interface injection, G19 isolation and `FA010` conflict diagnosis for external Loom 1.18.4 versus bundled Loom 1.17.21, and G27 client-only Mixin/dependency isolation.

**Outstanding historical release criteria:** An actual first IntelliJ import and manual/automatic GUI Sync, some external TLS/Modrinth error classification and authenticated remote Maven cases, full dedicated-server boot after user EULA acceptance, legacy remap game runtime, optional deferral of Kotlin Gradle Plugin downloads when Kotlin is disabled, and parts of the G1–G30 matrix. A real external KGP 2.4.20 versus bundled KGP 2.4.21 conflict was verified as TestKit **`FA010` 1 PASS**. Newly isolated, uncached executions recorded **G15 main/client/game-tick 1 PASS** and **G17 real interface injection/default-method call/game-tick 1 PASS**, each with XML evidence. An earlier G17 runtime ended with Windows `0xC0000409` before the game tick and remains recorded as a **separate JUnit FAIL**; later success does not erase it or guarantee native reproducibility on other systems. A separate G27 `runServer` reached Fabric Loader and mod initialization but **did not finish server startup because EULA consent had not been given**. The tool did not accept `eula.txt` automatically.

**Actual API/library consumer verification:** Separate settings-and-YAML-only Fabric library A consumers, once as `fabric/api` and once as `fabric/library`, published to a real local Maven repository; independent consumer B compiled against the exposed API types transitively. POM `compile`/`runtime` scopes, Gradle `.module` API/runtime variants, non-transitivity of `compile-only`/test dependencies, JAR-in-JAR, exactly one root `fabric.mod.json` in the distribution JAR, source JAR, and alias equivalence were checked with **opt-in TestKit JUnit 1 PASS / 0 FAIL** and outer **BUILD SUCCESSFUL in 1m 4s**. The XML is preserved at `analysis/acceptance-evidence-2026-11-13/API-library-publishing-1PASS.xml`. Two initial failures originated in test-fixture repository priority and Kotlin DSL callback syntax; the fixture was corrected and the checks rerun rather than treating those failures as passes.

**Selected consumer verification after integrated publishing (as of 2026-11-13):** Safe `help`/`tasks`/actual `build` and platform `--dry-run` across `fabric/mod`, `fabric/api`, and `fabric/library`: **3 PASS**. Modrinth+CurseForge-only task graph for a `fabric/mod` without Maven: **1 PASS**. Actual local API Maven publishing with configuration cache `STORE → HIT → YAML edit MISS/STORE → HIT`: **1 PASS**. Separately preserved XML includes **1 PASS** each for legacy Java 21 API `remapJar`/`remapSourcesJar` publication and downstream compilation, G24 repackaging after Mixin/Class Tweaker/icon edits, API/library local Maven transitivity regression, and G5 configuration-cache regression. Filename security checks, legacy Maven credential-pair rules, and Draft-07 schema-equivalence unit regression also passed. **No external Modrinth/CurseForge API upload occurred**; local HTTPS remote Maven acceptance was examined separately in QA §28.

**Additional offline G5/G20/G30 acceptance:** An independent settings-only consumer changed **`settings.java.sourcesJar: true→false→true`**, yielding a **six-run PASS** for `tasks --all --configuration-cache --offline` with **STORE→HIT→MISS→HIT→MISS→HIT** and the expected `sourcesJar` task toggle. **G20 atomic recovery by two parallel GradleRunners after a damaged mirror/deleted icon: 1 PASS**. **G30 rename from `mod.yml` to `mod.yaml` and CC MISS→HIT: 1 PASS**. Evidence: `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-G5-java-setting-CC-6run-PASS.json`, `FOLLOWUP-RECHECK-FabricAmperParallelMirrorConsumerTest-1PASS.xml`, and `FOLLOWUP-RECHECK-FabricAmperExtendedConsumerTest-1PASS.xml`. These selected passes must not be added again to the default 246-test JUnit count below.

The original project's Gradle 9.7.1/JDK 25 `clean build validatePlugins --offline --no-daemon -Pkotlin.incremental=false` completed with **45 XML suites, 246 tests = 207 PASS / 0 FAIL / 0 ERROR / 39 SKIP**, `validatePlugins` PASS, and **BUILD SUCCESSFUL in 39s**. An isolated, source-hash-matched project snapshot at `analysis/final-verification-2026-11-13/` ran `clean build validatePlugins --offline --no-daemon --no-build-cache --rerun-tasks -Pkotlin.incremental=false`, with **all 11 tasks executed, BUILD SUCCESSFUL in 1m 38s**, and the same **207 PASS / 39 SKIP / 0 FAIL/ERROR**. Intermediate cache, fixture, and native failures are recorded separately in `QA_REPORT.md` §24. The **then-current** Gradle plugin `build/libs/fabric-amper-0.1.0.jar` was **314,704 bytes**, SHA-256 **`164CADA56DF0BEFBFB0AFABAE574F639A89795DFD86CA222AA8789F44C829F0A`**. Its source JAR was **67,202 bytes**, SHA-256 **`2032A41383A3534B2FF0B087586B968189C752D74029A545E95ACAA747F2B119`**, and SchemaStore `fabric-amper.schema.json` was **26,823 bytes**, SHA-256 **`D5FD84098F1FD6AB926D504F23C69EC991AD17CBCF127B4C14308672B1BC5233`**. Each plugin marker/class occurred exactly once, with zero duplicate ZIP entries. G29 locally republished Maven JAR and sources were byte-identical to the final outputs from that isolated snapshot.

This JAR is a **Gradle build plugin**, not a Minecraft mod installed in `mods/`. Users must submit any SchemaStore registration themselves. Full G1–G30 shipping approval under `specs.md` remained **`NOT APPROVED`** and **39 SKIP did not mean PASS**. Later selected checks included an actual external KGP conflict (G19), FA011 nested Gradle exception trees, isolated G13/G25 version combinations, new real Loader runtime passes for G10/G15/G17 alongside the earlier native G17 failure, G27 Loader initialization and EULA refusal, and remote Maven offline dry-run CC. Builds of Java/Kotlin/main/client in `analysis/ide-sync-probe/` and mod JAR plus server Loader detection in `analysis/server-eula-probe/` were observed, but **actual IDEA GUI first Sync and fully booted dedicated server after consent were not verified**. See `QA_REPORT.md` §24, `analysis/acceptance-evidence-2026-11-13/G1-G30-followup-gate-matrix.md`, `FOLLOWUP-FINAL-clean-no-cache-JUnit-XML.zip`, and `FOLLOWUP-FINAL-clean-no-cache-manifest.json`. **This paragraph is a historical §24–25 snapshot; later 2026-11-13 checks follow.**

### Additional acceptance and final artifacts on 2026-11-13 (QA §26)

**G11/G30:** Against an independent settings-only consumer, **11 hostile YAML inputs (FA002/FA003)** plus **six** Class Tweaker, missing-icon, path-traversal, dependency-conflict, dual-YAML-alias, and original-`fabric.mod.json` cases (FA001/FA008/FA009/FA012) were checked: **17 invalid inputs total**. All produced their expected diagnostics while preserving previous valid generated/mirror/distribution JAR and source bytes; configuration-cache invalidation and subsequent HIT after valid YAML recovery were observed. **G20:** After simultaneously corrupting JSON/Mixin/Class Tweaker mirrors and deleting the icon mirror, two parallel GradleRunners completed `help`/`generateFabricModJson --configuration-cache --offline` successfully. All four files recovered byte-for-byte, with no duplicate ZIP entries or leftover temporary files, and CC reuse, for **JUnit 1 PASS**. Atomic replacement applied to individual files, not a whole-directory transaction; this did not demonstrate IntelliJ GUI Sync.

**Real HTTP/CC limits for remote Maven:** A Python `ThreadingHTTPServer` fixture bound **only to `127.0.0.1`**. An HTTPS connection with a self-signed SAN-IP certificate and a dedicated JDK truststore, plus a Basic 401 challenge for unauthenticated GET, were verified. A post-serialization `task.repository` access error in the real Gradle 9.7.1 native publish task was identified and fixed. Subsequently, **`--offline` blocked even loopback HTTPS PUT**, and native `PublishToMavenRepository` with explicit credentials was found **incompatible with configuration cache**. There was no claim that authenticated native PUT and CC STORE→HIT succeeded together. Avoiding explicit credential assignment on `--dry-run --configuration-cache --offline` restored selected **JUnit 1 PASS / 0 FAIL**, **STORE→HIT**, with all tasks SKIPPED; the pre-fix **1 FAIL** XML was preserved. **External Modrinth/CurseForge uploads: zero**.

**Product build at QA §26:** Gradle **9.7.1/JDK 25**, `clean build validatePlugins --offline --no-build-cache --rerun-tasks -Pkotlin.incremental=false` PASS, default **46 XML suites / 247 tests = 207 PASS + 40 SKIP + 0 FAIL + 0 ERROR**. The then-current `build/libs/fabric-amper-0.1.0.jar` was **315,071 bytes**, SHA-256 **`2DC9F20238BED6284C0D80C898F47C13B49AC897DE41D819DBE8667274E00264`**; the sources JAR was **67,690 bytes**, SHA-256 **`E31E8461EA8F57A3FCBCEB04A2892BC6387D07DC29B696F63D677BE819320803`**; the schema SHA-256 was **`D5FD84098F1FD6AB926D504F23C69EC991AD17CBCF127B4C14308672B1BC5233`**. Actual G29 local Maven plugin markers and published JAR/sources were byte-identical to these outputs. Evidence: `analysis/final-post-dryrun-correction-2026-11-13/run/manifest.json`, `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-POST-REMOTE-DRYRUN-FINAL-JUnit-XML.zip`, `FOLLOWUP-POST-REMOTE-DRYRUN-CC-RESTORED-1PASS.xml`, `FOLLOWUP-G11-G30-hostile-YAML-CC-preservation-PASS.json`, `FOLLOWUP-G11-resource-FA001-FA008-FA009-FA012-CC-PASS.json`, and `FOLLOWUP-G20-FOUR-MIRROR-FILES-PARALLEL-CC-1PASS.xml`. **These are not v0.3.0 build results.** Actual first IntelliJ GUI import/manual and automatic Sync, fully booted dedicated server after explicit EULA acceptance, legacy game runtime, and authenticated native remote Maven+CC together remained separate requirements. **40 SKIP did not mean PASS.**

### Historical 2026-11-13 result: FA003 negative remote Maven acceptance and JAR (QA §27)

A further product defect was found and fixed after §26. With missing credential environment variables, actual `publishMavenJavaPublicationToFabricAmperRemoteRepository --offline` initially produced Gradle's `Value not set credentials.username` error before the plugin's `doFirst`. The fix assigns empty provisional values so that native remote-publish task credentials are never unset, then checks the original Gradle Provider environment values **before any transfer**, rejecting missing/blank values with explicit **FA003**. Provisional empty values are not accepted as working credentials.

A new independent settings-only `fabric/api` consumer passed **2/2 actual offline Gradle negative tests**: (1) with both credential environment variables absent, the native remote-publish task failed with `FabricAmper FA003 publish.maven` and preserved YAML; (2) an `http://127.0.0.1` URL was rejected at `help` configuration with `FA003 publish.maven.url` without producing a JAR. Each test correctly expected **exit code 1**; neither was a successful PUT. Evidence: `analysis/local-https-maven-probe-2026-11-13/negative-contract-v2-2026-11-13/manifest.json`, two step logs, and the initially failing fixture.

**Rebuild with the code of that date:** Gradle **9.7.1/JDK 25**, `clean build validatePlugins --offline --no-build-cache --rerun-tasks` **BUILD SUCCESSFUL**, **46 suites / 247 tests = 207 PASS + 40 SKIP + 0 FAIL + 0 ERROR**, `validatePlugins` PASS, and G29 local Maven artifacts/markers byte-identical to that date's outputs. The then-current `build/libs/fabric-amper-0.1.0.jar` was **315,122 bytes**, SHA-256 **`13022B24ADE7C9809400FC6AEBE99E7751783A9DAE7EF8B8C53780FE70B698AD`**; sources JAR **67,799 bytes**, SHA-256 **`C3E69E8188B516F4F290588B3342F1D21BB24391658A75B4E26BEA3A5175B510`**; schema **26,823 bytes**, SHA-256 **`D5FD84098F1FD6AB926D504F23C69EC991AD17CBCF127B4C14308672B1BC5233`**. A follow-up remote Maven `--dry-run --configuration-cache --offline` yielded actual **JUnit 1 PASS / 0 FAIL**, **STORE→HIT**, all tasks SKIPPED. **These are not v0.3.0 artifact hashes.**

Evidence for that build and its default JUnit XML: `analysis/final-post-missing-credential-fix-2026-11-13/run/manifest.json`, `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-POST-REMOTE-CREDENTIAL-NEGATIVE-FINAL-JUnit-XML.zip`, and selected dry-run XML `FOLLOWUP-POST-REMOTE-FA003-DRYRUN-CC-1PASS.xml`. `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-FINAL-2026-11-13-remaining-verification-index.json` cross-checks that date's JAR hashes, 17 G11/G30 cases, one combined G20 PASS, two remote Maven negative cases, and one dry-run PASS. **All JAR hashes/counts in §§24–28 are dated historical evidence, not hashes of the current v0.3.0 binary.**

**Still unapproved:** Actual first IntelliJ GUI import and manual/automatic Sync; full dedicated-server boot after the user decides on EULA acceptance; legacy Minecraft runtime; and **authenticated native HTTPS Maven PUT with configuration-cache HIT in the same test**. Gradle `--offline` blocks localhost HTTPS publishing, and native explicit `PasswordCredentials` in Gradle 9.7.1 is CC-incompatible. Actual Modrinth/CurseForge API uploads were **zero by user restriction**. **Full G1–G30 shipping approval: `NOT APPROVED`; 40 SKIP are not PASS.**

### 2026-11-14 follow-up: successful real native publishing over localhost HTTPS (QA §28)

An independent test worked around `--offline` blocking localhost HTTPS while restricting all external network access via a **deny-only localhost HTTP(S) proxy configuration** within the project. The real Gradle 9.7.1 `publishMavenJavaPublicationToFabricAmperRemoteRepository` task ran twice, both **BUILD SUCCESSFUL**. The test server observed **25 successful Basic-authenticated PUTs per run (50 total)**. Received main JAR, source JAR, POM, and Gradle module metadata bytes and SHA hashes matched local artifacts, and first/second-run hashes matched. GAV and exactly one `fabric.mod.json` at the JAR root were also checked. No external Modrinth/CurseForge API requests or uploads took place and no personal credentials were used.

**Critical distinction:** Actual native PUT **passed**, but Gradle 9.7.1 **disabled configuration cache for that task in both runs** because it used explicit Maven `PasswordCredentials`. Therefore **real publishing CC STORE→HIT was not met**, and the separately successful `--dry-run` CC check does not substitute for it. Evidence: `analysis/local-https-maven-probe-2026-11-13/online-loopback-2026-11-14/manifest.json`, both `publish-0*-with-cc.log` files, and `QA_REPORT.md` §28. The plugin main/source JAR hashes **at that time** were unchanged from §27. IntelliJ GUI, server startup after EULA acceptance, legacy game runtime, and remote publishing CC remained open, so **full shipping was `NOT APPROVED`**.

An independent post-test evidence reconciliation script, `analysis/acceptance-evidence-2026-11-13/verify_2026_11_14_native_https.py`, also **passed**; its summary is in `FOLLOWUP-2026-11-14-real-native-HTTPS-PUT-summary.json`.
