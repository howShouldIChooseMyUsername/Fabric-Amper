---
name: fabric-amper-syn
description: Compact decision guide for Fabric Amper YAML-based Fabric mod development; use for unambiguous intent-to-field routing, safe validation, and FA001–FA015 corrections.
---

# Fabric Amper — Compact AI Skill (SYN)

## READ THIS FIRST — stop-or-escalate rule

**Use only this document and STOP reading** when the user's intent, the exact YAML keys and value types, target file paths, prerequisites, and safe verification steps are **unambiguous and fully covered below**. Apply the minimal edit; run the applicable safe checks; report results. Do **not** read the longer `FabricAmperSKILL.md` merely for reassurance, and do not needlessly expand the task.

**Command precedence (mandatory):** On Windows, **use an existing complete Fabric Amper checkout's `fabric-amper.bat` first for everything it actually supports**, including `doctor`, `init`, `migrate`, `tasks`, `validate`, `build`, `verify`, `test`, `clean`, `diagnose`, `explain`, `plan`, `port`, `run`, `server` and approved `publish` commands. Pass `-C "consumer"` before most subcommands; `migrate` allows `-C` / `--project` before or after its name, and `doctor` also accepts `doctor -C ".\httpmod"`, `doctor --project ".\httpmod"` and the redundant `doctor -C ".\httpmod" doctor`. Relative paths use the current terminal directory. For **unsupported** operations, retain the original Gradle/Java/Kotlin/native workflow; do not fabricate a BAT command. **Never assume `./gradlew` exists or is appropriate**: check the selected project's actual wrapper first. When read-only `doctor` detects missing required JDK/Gradle, the agent **automatically runs `doctor --fix` after preview without requesting separate approval**. It can install required software/add missing bootstrap files; it downloads the SHA-256 pinned `fabric-amper-doctor-fix.ps1` from the BAT version's GitHub Release into **`scripts/` below BAT** when missing. Normal `migrate` automatically downloads its missing version-matched `scripts/fabric-amper-migrate.ps1` and root schema, verifying both pinned SHA-256 values before conversion; no manual PS1 installation. Neither `doctor --fix --dry-run` nor `migrate --dry-run` downloads; without a local helper they only preview the missing fetch. `init --here` must not overwrite an existing project. This policy does not authorize unrelated installs, remote publishing, EULA acceptance, or overwriting existing files.

**BAT version check:** no arguments, `-V`, `--version`, or `version` prints exactly `v0.1.3` (one line, exit 0) without Gradle or file changes. Use `help` / `--help` for usage: no arguments **no longer** means help. The output describes the local launcher version, **not** a live release/JitPack availability check.

**Mandatory Windows preflight sequence for JDK/Gradle-dependent tasks:** Run `fabric-amper.bat doctor` first. If required tools are present, continue. If JDK/Gradle is missing or unusable, run `doctor --fix --dry-run` (no downloads or modifications; without the PS1 it only previews the helper fetch), then **run `doctor --fix` on your own, without asking for consent**, as part of the existing development task. After repair, run `doctor` **again** (use a new terminal if PATH/JAVA_HOME changes have not propagated). Resume the original dependent work **only when the second check confirms the necessary tools**. On failure or hash mismatch, report the blocker and stop the dependent operation; no unrelated installation, silent workaround, automatic retries, or claim that `doctor` verifies JitPack/runtime/publishing. See [full workflow](FabricAmperSKILL.md#required-windows-doctor--repair--recheck-workflow).

**Read [FabricAmperSKILL.md](FabricAmperSKILL.md) before editing or answering** when **any** required detail is missing, ambiguous, contradictory, out of scope, or insufficiently documented here. Examples: a new/unknown YAML key, complex publish platform relationships, disputed Loader predicate grammar, non-obvious split-side safety, unusual mapping/toolchain, Gradle/classpath collisions, HTTP failure, ZIP/resource corruption, unresolved diagnostics, or different versions. For exact implementation truth, inspect `src/main/kotlin/dev/fabricamper/config/` and [fabric-amper.schema.json](fabric-amper.schema.json). Never guess just to avoid an escalation.

This file is a **custom root AI guide**; its filename is not the standardized auto-discovery `<skills-dir>/SKILL.md`. Frontmatter does not change that. The [full skill](FabricAmperSKILL.md) explains standard packaging.

## Strict invariants (always)

- Consumer authored build configuration = **`settings.gradle.kts` + exactly one `mod.yaml`/`mod.yml`**; developer still writes real source/resources/tests. Never create consumer `build.gradle(.kts)`, manually authored `fabric.mod.json`, or apply internal `dev.fabric-amper.project`.
- Required root `product: fabric/mod` (or `fabric/api` / alias `fabric/library`), `mod` and `minecraft`; nested `mod.id`, `mod.version`, `minecraft.version`, `minecraft.loader`. `schema` is **optional**: omitted = plugin's latest supported version (**currently 1**); `schema: 1` pins it, unsupported explicit schema → FA003. Quote real version strings, e.g. `"26.3"`, `"1.0.0"`.
- Strict UTF-8 single YAML mapping; no duplicate keys, anchors (`&`), aliases (`*`), merges (`<<`), unrecognized tags or fields. When editing, **merge** partial recipes into existing mappings — no repeated root `mod:` or `settings:`.
- `mod.environment` is Loader's whole-mod side; `dependencies[].target` is a Gradle dependency source set. `dependencies` ≠ `mod.depends` ≠ `publish.modrinth.dependencies`.
- Only real fixed published dependency versions, verified Loader IDs, known Maven repositories and real Modrinth/CurseForge project IDs. No secrets or source-path escapes. Do not change an unrelated user's dependency or generated file.
- **Do not publish without explicit authorization.** `help`/`build`/`test` are not remote uploads; publish tasks are. Do not auto-accept EULA or use personal Minecraft worlds. Never claim a successful build proves GUI/runtime/remote publishing.
- Project `0.1.3` is **not shipping approved**, and public Plugin Portal availability is unconfirmed; see [QA_REPORT.md](QA_REPORT.md). Do not claim any unobserved task or G gate passed.

## Fast intent → YAML / file / check

Unless stated otherwise, edit the *existing* consumer `mod.yaml` (or `mod.yml`), not a Gradle script. The `Gradle` column refers to commands in that consumer folder.

| User intent | Exact YAML change | Required file / Gradle check |
| --- | --- | --- |
| New ordinary mod | `product: fabric/mod`; `mod.id`, `mod.version`; `minecraft.version`, `minecraft.loader`; optional `schema: 1` | `settings.gradle.kts` Settings plugin only; real Java initializer; `gradle help` → `build` |
| Public API/library | `product: fabric/api` (alias `fabric/library`); required `mod.group`; optional `mod.archivesName` | Verify JAR/POM and downstream consumers; explicit local Maven publish |
| Change display/version/JAR basename | `mod.name` / `mod.version` / `mod.archivesName` | Check generated Loader JSON and `build/libs/` |
| MC26.3 Java-only | `minecraft.version: "26.3"`; `minecraft.mappings: mojang`; `settings.java.release: 25`; `settings.kotlin.enabled: false` | JDK25, fixed Loader; non-remap `jar` |
| MC1.21.11 legacy | `minecraft.version: "1.21.11"`; `settings.java.release: 21`; Mojang or real published Yarn mapping | Remap `remapJar`; legacy compatibility needs separate tests |
| Kotlin | `settings.kotlin.enabled: true`; pinned KGP `2.4.21` / language `2.4` / FLK `1.14.2+kotlin.2.4.21` | Actual `src/main/kotlin/`; `adapter: kotlin` only when needed |
| Client-only mod | `mod.environment: client`; `mod.entrypoints.client` | Real client initializer and side-safe resources |
| Common mod + client code/dependency | `settings.loom.splitEnvironmentSourceSets: true`; dependency `target: client` | `src/client/java` / `src/client/resources`; never client `exported/include` |
| Loader installed-mod requirement | `mod.depends.<real-mod-id>: ">=version"` (or validated OR string array) | Inspect generated JSON; not Maven resolution |
| Maven dependency | `repositories: [mavenCentral]` or verified `{maven: https-url}`; `dependencies: [{maven: "g:a:fixed", scope: all}]` | Confirm real GAV; `gradle build` |
| Modrinth **Maven** dependency | `repositories: [modrinth]`; `dependencies[].modrinth: {project: REAL, version: REAL_FIXED}`; `kind: mod` | This is NOT `publish.modrinth.projectId` |
| Public API dependency | `dependencies[].scope: all` + `exported: true` | Check exported downstream compilation |
| Nested JAR | `dependencies[].scope: all` + `include: true` | Check embedded JAR + Loader `jars` |
| Compile/runtime only | `dependencies[].scope: compile-only` or `runtime-only` | No `exported/include` with either |
| Test dependency | `test-dependencies: ["org.junit.jupiter:junit-jupiter:6.0.0"]` | Real tests in `src/test/`; `gradle test` |
| Mixin | `mod.mixins: [example.mixins.json]` or `{config, environment}` | Real JSON file in resources; verify side |
| Modern Class Tweaker | `mod.accessWidener: example.classtweaker` | `src/main/resources/example.classtweaker`; first line `classTweaker v1 official` |
| Legacy Access Widener | `mod.accessWidener: example.accesswidener` | Valid legacy `accessWidener v1/v2 named/intermediary` header |
| Icon | `mod.icon: assets/id/icon.png` or `{"32": path, "128": path}` | Actual PNG in resource root |
| Custom Loader metadata | `mod.custom` JSON-compatible mapping | Inspect generated `fabric.mod.json` |
| Remote Maven | `mod.group` + `publish.maven.url: https://...`; optional `credentialsFromEnv: [USER_ENV, PASS_ENV]` | Validate, then only user-approved `publishFabricAmperMaven` |
| Modrinth release | `publish.modrinth.projectId` (string), `tokenEnv`, optional `releaseType`, `gameVersions` (string list), `loaders` (string list), `environment`, `dependencies[]` | Verified real IDs, explicit `publishFabricAmperModrinth` |
| CurseForge release | `publish.curseforge.projectId` (positive integer), `tokenEnv`, `gameVersionNames` (strings), optional `gameVersions` (integer IDs), `relations[]` | Verified real IDs, explicit `publishFabricAmperCurseForge` |
| Shared Modrinth + CurseForge release | `publish.common.releaseType` / `name` / `gameVersions` / `changelog( File)` / `dependencies[]`; keep `publish.modrinth` and `publish.curseforge` with platform-specific IDs/tokens | Merge defaults, explicit platform override, actual IDs; no automatic upload |
| Legacy API Maven | `publishing.repository.url` + optional paired `usernameEnv/passwordEnv` | Only `fabric/api`, never alongside `publish.maven` |

**Dependency scopes:** `all` = compile+runtime; `compile-only` = compile; `runtime-only` = runtime. `exported: true` exposes public API; `include: true` nests a JAR. Both require `scope: all`. `target: client` additionally requires split source sets and forbids export/include.

## Schema snapshot — only supported author keys

| Parent | Accepted child keys / types |
| --- | --- |
| **root** | **optional** `schema` integer 1; required `product` enum, `mod` map, `minecraft` map; optional `settings`, `repositories`, `dependencies`, `test-dependencies`, `publishing`, `publish` |
| `mod` | `id`, `version`, `group`, `archivesName`, `name`, `description`, `environment`, `license`, `authors`, `contributors`, `contact`, `icon`, `provides`, `entrypoints`, `languageAdapters`, `mixins`, `accessWidener`, `depends`, `recommends`, `suggests`, `conflicts`, `breaks`, `custom` |
| `minecraft` | `version`, `mappings`, `loader`, `fabricApi` |
| `settings.java` | `release` integer (25 for MC26.3), `sourcesJar` boolean |
| `settings.kotlin` | `enabled`, `pluginVersion`, `languageVersion`, `fabricLanguageKotlin` |
| `settings.loom` | `splitEnvironmentSourceSets` boolean |
| `settings.resources` | `failOnExistingFabricModJson: true` (must remain true) |
| `repositories[]` | `fabric`, `mavenCentral`, `modrinth`, `{maven: https-url}` |
| `dependencies[]` | GAV string, coordinate→scope shorthand, or `maven`/`modrinth` + `kind`, `scope`, `target`, `exported`, `include` |
| `test-dependencies[]` | coordinate/scope without `target: client`, `exported`, `include` |
| `publish.maven` | `url` HTTPS; `credentialsFromEnv` = two distinct environment-variable **names** |
| `publish.common` | `releaseType`, `name`, string `gameVersions[]`, `changelog` OR `changelogFile`, `dependencies[{slug,type,modrinthId?,curseforgeId?}]` |
| `publish.modrinth` | `projectId`, `tokenEnv`, `releaseType`, `versionName`, `versionNumber`, `gameVersions`, `loaders`, `environment`, `changelog` **or** `changelogFile`, `dependencies[{projectId,type}]` |
| `publish.curseforge` | `projectId`, `tokenEnv`, `releaseType`, `displayName`, `gameVersionNames`, `gameVersions`, `changelog` **or** `changelogFile`, `changelogType`, `manualRelease`, `relations[{slug,type,projectId?}]` |
| `publishing.repository` | `url`, optional **paired** `usernameEnv`/`passwordEnv` — legacy API only |

If a required value or nuanced validation is not covered by this table, **read the full skill and schema** rather than constructing an ad hoc key.

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
                useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.3")
            }
        }
    }
}
plugins { id("dev.fabric-amper.settings") version "0.1.3" }
```

The intended Git tag in the JitPack module is `v0.1.3` and the Gradle plugin request is `0.1.3`. The removed `fabric-amper.bat setup` command is not needed. Check that the tag exists and JitPack **actually serves** the tagged module before claiming the generated project builds; it is not published on the public Plugin Portal.

`mod.yaml` (complete **Java-only** sample; create `src/main/java/org/example/ExampleMod.java` with an actual `ModInitializer`):

```yaml
product: fabric/mod
mod:
  id: example_mod
  version: "1.0.0+26.3"
  name: Example Mod
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
  kotlin:
    enabled: false
repositories:
  - fabric
  - mavenCentral
```

For generation of all three files automatically on Windows, see [HUMAN_QuickGuide.md](HUMAN_QuickGuide.md) and `fabric-amper.bat init example_mod`.

## Windows BAT routing — use the wrapper only when appropriate

The producer root contains [`fabric-amper.bat`](fabric-amper.bat). This is a **Windows CMD Gradle wrapper**, not a consumer YAML key or public installer. The next table is enough for common, unambiguous commands: **stop after following it**. If the workspace path, requested target, side effects, or task behavior is ambiguous, **read [FabricAmperSKILL.md](FabricAmperSKILL.md#windows-command-wrapper-fabric-amperbat)** before proceeding.

| Intent | Command / action | Boundary |
| --- | --- | --- |
| Show usage / installed tools | `fabric-amper.bat help` / `doctor` / `docs` / `version` | `doctor` checks Java/Gradle; it **does not** connect to JitPack |
| Additional local environment diagnosis | `doctor --full` / `doctor --paths` | Windows path/JDK hints; `--paths` does not execute Gradle |
| Preview fixes safely | `doctor --fix --dry-run` | No network, installation, user PATH change or file writes; **missing PS1 means preview the fetch only**, not toolchain inspection |
| Automatic repair after a missing-tool diagnosis | `doctor --fix` | After read-only preview, run without a separate approval question; fetch only the versioned GitHub PS1 into `scripts/` below BAT when missing, pin SHA-256 and reject mismatches/unwritable directory, then winget JDK25/Gradle or verified Gradle ZIP and missing Settings/editor files; recheck with `doctor` |
| Start new Java mod | `fabric-amper.bat init example_mod` | New folder with Settings/YAML/Java source; download missing BAT-side schema from GitHub `main` with pinned SHA-256, then copy it to `schemas/mod.schema.json`. YAML `# yaml-language-server: $schema=./schemas/mod.schema.json` resolves inside the generated project. Never overwrite |
| Start new Kotlin mod | `fabric-amper.bat init test_mode "Test Mode" mod --kotlin` | Trailing `--kotlin` sets `settings.kotlin.enabled: true` and generates `src/main/kotlin/org/example/ExampleMod.kt` implementing `ModInitializer`; no Java duplicate, no consumer build script. Kotlin/FLK versions remain pinned; YAML entrypoint is `org.example.ExampleMod` |
| Start in existing tools-only folder | `fabric-amper.bat init night_vision --here` | Tools and notes preserved; any existing YAML/Settings/build script/src causes a hard error |
| Start new API project | `fabric-amper.bat init example_api "C:\work\example_api" api` | `fabric/api` and example `org.example` group; must customize |
| Preview a legacy Fabric Gradle conversion | `fabric-amper.bat migrate --dry-run` or `migrate -C "C:\work\legacy_mod" --dry-run` | With verified local helper/schema, parse Gradle/JSON and preview YAML; if either is missing, only preview download. Never writes/downloads |
| Convert a legacy Fabric Gradle project | `fabric-amper.bat migrate` or `migrate -C "C:\work\legacy_mod"` | Auto-download/hash-check missing `scripts/` converter and schema; then create YAML/Settings and back up original build/settings/metadata in `gradle_backup/` |
| Convert and delete originals, only when explicitly requested | `fabric-amper.bat migrate -D` or `migrate --delete` | Same conversion; remove original build/settings/`fabric.mod.json` only after staging; `--delete --dry-run` previews without writes |
| Select an existing consumer | `"C:\path\to\Fabric Amper\fabric-amper.bat" -C "C:\work\example_mod" build` | `-C` / `--project` **before** ordinary subcommands; `doctor` additionally supports them after its name; default consumer = current directory |
| Validate and build | `validate` / `build` / `verify` | `build` = `validateFabricAmper build verifyFabricAmperJar` |
| Diagnose consumer toolchain / deps | `diagnose` / `explain` | Offline Gradle `fabricAmperDoctor` / `fabricAmperExplain`; no remote artifact lookup |
| Preview future releases | `plan` | Gradle `fabricAmperReleasePlan`; checks final JAR and effective platform metadata, **never uploads** |
| Request game migration checklist | `port 26.3` | `fabricAmperPortingReport -PfabricAmperPortTarget=26.3`; no source/YAML mutation |
| Inspect and maintain | `tasks` / `test` / `clean` | Gradle tasks; `clean` removes local build outputs |
| Run game | `run` / `server` | Actually starts Minecraft; server EULA is user's choice |
| Publish locally | `publish local` | API Maven publication in consumer `build/fabric-amper/maven-repository` |
| Publish remotely | `publish maven` / `modrinth` / `curseforge` / `all` | **Real upload** after typed `PUBLISH` and local preflight; explicit user authorization required |

For **existing Amper consumers**, the wrapper rejects missing/duplicate YAML, consumer `build.gradle(.kts)`, and authored source `fabric.mod.json`. **`migrate` is specifically for the pre-conversion state**: exactly one `build.gradle.kts` or Groovy `build.gradle`, one `src/main/resources/fabric.mod.json` or `src/client/resources/fabric.mod.json`, no existing `mod.yaml`/`mod.yml` or `gradle_backup/`, and at most one old settings file. `migrate -C` / `--project` accepts the target before or after the verb, exactly once. The static PowerShell helper does not evaluate Gradle, migrate arbitrary plugins/tasks, or guarantee JitPack/download/runtime correctness; unsupported dynamic DSL, conflicting versions or unknown metadata fail for manual migration. The `-D` / `--delete` flag is **not** required; prefer default backups and never select deletion without an explicit user request. After conversion, review YAML, verify the v0.1.3 JitPack artifact exists and run `validate` then `build`. `publish all` invokes `publishFabricAmper`, not Gradle's broader `publish`. Always use the user's exact destination and **never** initiate remote publishing merely because a YAML configuration exists. The presence of an unrelated `gradlew.bat` in any neighboring folder is irrelevant; choose only an existing verified consumer wrapper or a supported native tool for operations the BAT does not expose. More examples: [HUMAN_QuickGuide.md](HUMAN_QuickGuide.md).

**Stop-or-escalate for reports:** If the user only needs current config/dependency facts and `diagnose`/`explain` answers them, stop here. If they ask about exact external project compatibility, a runtime crash, a new Minecraft port, Mixin semantics, or uncertain side effects, read [FabricAmperSKILL.md](FabricAmperSKILL.md#route-read-only-diagnostics-dependency-explanation-and-release-preview) and check real runtime/source evidence. A release preview with no HTTP **cannot** confirm platform IDs, authentication, moderation or that a published version exists.

## Safe validation path

1. Check one YAML, known keys/types, fixed versions, real resource/entrypoint paths, single Settings plugin, no authored Loader JSON, no secret values.
2. Run `gradle help`, `gradle tasks --all`, `gradle validateFabricAmper`, `gradle build`; run `gradle test` for tests and `gradle verifyFabricAmperJar` for archives.
3. Inspect `build/libs/<archivesName-or-id>-<version>.jar`: **exactly one root `fabric.mod.json`**, correct `id`/`version`, resource paths and nested JAR when applicable.
4. If a gate depends on game launch, IntelliJ GUI sync, remote HTTPS upload or configuration-cache reuse, run that **separate** check only when authorized, and do not infer success from a build.
5. On failure, use the next table; if code/context mismatches its listed cause, **read [FabricAmperSKILL.md](FabricAmperSKILL.md)** and the actual error trace.

## FA001–FA015 fast remediation

| Code | Key cause | Immediate fix |
| --- | --- | --- |
| **FA001** | Missing `mod.yaml`/`mod.yml` or **both exist** | Keep **exactly one** consumer-root YAML; preserve whichever user intends |
| **FA002** | Bad/hostile YAML, duplicate key, alias/anchor, tag, multi-doc, invalid UTF-8 | Single UTF-8 mapping; remove duplicates, `&`/`*`/`<<`/tags |
| **FA003** | Unknown key, wrong type, unquoted version, bad URL/credentials | Compare exact reported path to schema; quote `"26.3"`; fix key/type |
| **FA004** | Yarn selected for MC 26.1+ | `minecraft.mappings: mojang` |
| **FA005** | Modern Minecraft with wrong Java release/JDK | MC26.3 → `settings.java.release: 25` and JDK25 |
| **FA006** | Kotlin adapter or pinned versions mismatch | `settings.kotlin.enabled: true` + KGP `2.4.21` / language `2.4` / FLK `1.14.2+kotlin.2.4.21`, else remove adapter |
| **FA007** | `exported/include` with `compile-only` or `runtime-only` | Use `scope: all` **if semantically correct**, else remove flag |
| **FA008** | Manually authored resource `fabric.mod.json` | Move supported metadata into YAML; remove authored JSON after preserving its content |
| **FA009** | Bad mod ID/entrypoint; missing/invalid PNG, Mixin, Class Tweaker | Fix actual path, valid metadata, header and resource file |
| **FA010** | Loom/KGP plugin classpath conflict | Use **only** `dev.fabric-amper.settings` in consumer; resolve compatible plugin marker |
| **FA011** | Missing declared Maven/Modrinth artifact | Correct real fixed GAV/project/version and repository; distinguish offline/TLS/auth failures |
| **FA012** | Duplicate/conflicting dependency coordinate | Merge repeated declarations and correct scope once |
| **FA013** | Final JAR has zero or duplicate **root** `fabric.mod.json` | Remove source JSON, correct packaging; rerun `verifyFabricAmperJar` |
| **FA014** | `target: client` without split or illegal `include/exported` | Set `settings.loom.splitEnvironmentSourceSets: true`; remove illegal flags |
| **FA015** | Loom mirror/generated resources inconsistent or damaged | Allow regeneration; inspect persistent cache/race errors |

**Do not confuse FA008 with FA014.** FA008 concerns authored `fabric.mod.json`; **client dependency source-set errors are FA014**. A generic TLS error, Gradle dependency variant error, Minecraft crash or failed HTTP publication may not have any FA code.

## Common anti-patterns — replace, do not patch around

| Wrong / unsupported | Correct |
| --- | --- |
| `minecraft.loomVersion` / root `loomVersion` | Loom version comes from the producer Settings plugin; no consumer key |
| `mod.classTweaker` | `mod.accessWidener` + actual `.classtweaker` file |
| `mod.dependencies` | `dependencies[]` for Gradle; `mod.depends` for Loader |
| `settings.fabric.splitEnvironmentSourceSets` | `settings.loom.splitEnvironmentSourceSets` |
| `publish.maven.username/password` literal secrets | `publish.maven.credentialsFromEnv: [NAME1, NAME2]` |
| `product: fabric/mod` plus legacy `publishing.repository` | Use new `publish.maven` or switch to API product |
| `$schema: ...` YAML root data | YAML-language-server `#` comment or editor mapping |
| `mappings: yarn` on MC26.3 | `mappings: mojang` |
| `gradle publish` as a “dry test” | `gradle help`/`build`/`verifyFabricAmperJar` without upload |

### Minimal output contract for agents

Respond with **files edited**, exact YAML key/value changes, Gradle commands **actually run** and exits, whether a production JAR's root metadata was inspected, and unresolved runtime/IDE/publishing risks. Do not claim tests executed from documentation alone. **If the table resolves the request: finish here. If not: read [FabricAmperSKILL.md](FabricAmperSKILL.md), then [HUMAN.md](HUMAN.md) or source only as needed.**
