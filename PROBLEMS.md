# Minecraft Mod Developer Problems — Fabric Amper Opportunity Map

> **Research snapshot:** 2026-11-14. This is a sourced product-research and prioritization document, **not** a claim that all listed features already exist or that the whole Fabric Amper project is ready for release. Four independent research tracks cover (1) builds and IDEs, (2) Minecraft version porting, (3) publishing, and (4) runtime validation. Findings combine public primary sources with a **separate assessment** of what Fabric Amper might do. Read [`specs.md`](specs.md), [`README.md`](README.md), [`HUMAN.md`](HUMAN.md), [`fabric-amper.schema.json`](fabric-amper.schema.json), and [`QA_REPORT.md`](QA_REPORT.md) for the actual contract and validation history.

## Why this document exists

Developing a Minecraft mod is not just writing Java or Kotlin. Developers must coordinate game versions, Java, mappings, Fabric Loom, Loader and API, Gradle variants, metadata, resources, test runs, IDE import, and different release platforms. **Fabric Amper's goal is to reduce repetitive, error-prone configuration without pretending a build tool can automatically fix gameplay code or guarantee a successful release.**

An upstream issue is **evidence that a problem exists**, not evidence that every developer experiences it. A platform guide is evidence of a required workflow, not proof that the workflow is universally difficult. Any automation suggestion below is an **engineering hypothesis** until it has a prototype, user testing, and acceptance evidence. The issue state can change after the research date; historical or closed issues remain examples, not claims of current upstream bugs.

### Status labels and priorities

| Label | Meaning |
| --- | --- |
| **Existing** | Current Fabric Amper code/documentation already provides a specific mechanism. It still needs the applicable tests to be considered proven. |
| **Partial** | Fabric Amper reduces one part of the burden, but significant workflow remains manual or a required validation gate is open. |
| **Candidate** | Not implemented as described. A possible addition with defined scope and acceptance tests. |
| **External / non-goal** | Depends on the game, IDE, Loom, other mod, upstream platform or human decision; Fabric Amper cannot promise a general fix. |
| **P0** | Prioritize before public release or as foundational correctness/diagnostics work. |
| **P1** | High-value next-stage developer-experience improvement, after core shipping gates. |
| **P2** | Useful optional follow-up requiring more study, costs or integrations. |
| **Research** | Evidence or a safe design is insufficient; first collect examples or run feasibility tests. |

Do not treat priority as a statistical user survey, an effort estimate or a fixed promise. It represents **product judgment** given the current Fabric-first scope.

## Present Fabric Amper baseline — avoid inventing solved problems

The following snapshot is grounded in the current project implementation and documentation, not just planned `specs.md` text:

| Capability / limit | Current position | Internal evidence |
| --- | --- | --- |
| Settings-only consumer | **Existing:** `dev.fabric-amper.settings` and exactly one `mod.yaml` / `mod.yml`; no authored consumer `build.gradle(.kts)` or `fabric.mod.json` | [README.md](README.md), `ModYamlLoader`, `ModYamlDecoder`, `FabricAmperProjectPlugin` |
| Strict validation / metadata | **Existing:** FA001–FA015 contract, root JAR `fabric.mod.json` verification, generated metadata and Loom mirror integrity | [specs.md](specs.md), [QA_REPORT.md](QA_REPORT.md) |
| Modern/legacy builds | **Existing with bounded verification:** 26.3 non-remap + Java 25; 1.21.11 legacy remap + Java 21; pinned Loom and Gradle combinations matter | [HUMAN.md](HUMAN.md), [QA_REPORT.md](QA_REPORT.md) |
| Dependency scopes | **Existing:** Maven and Modrinth Maven coordinates, `scope` / `target` / `exported` / `include` and API/library publication variants | [HUMAN.md](HUMAN.md), [README.md](README.md) |
| Loader requirements | **Existing:** `mod.depends` and other Loader relation maps are separate from Gradle dependency resolution | [fabric-amper.schema.json](fabric-amper.schema.json), `ModYamlValidator` |
| Optional Kotlin + client source sets | **Existing but bounded:** YAML controls for Kotlin and `splitEnvironmentSourceSets`; some cold-cache/classpath and real runtime concerns remain | [QA_REPORT.md](QA_REPORT.md), [README.md](README.md) |
| Platform release YAML | **Existing:** `publish.maven` / `publish.modrinth` / `publish.curseforge` and shared `publish.common` with explicit override/append semantics | [HUMAN.md](HUMAN.md), [fabric-amper.schema.json](fabric-amper.schema.json) |
| Schema selection | **Existing:** omitted `schema` selects the newest **implemented** schema version, currently **v1**; explicit `schema: 1` pins v1; unsupported explicit versions fail | `FabricAmperSchemaVersion`, `ModYamlDecoder` |
| Windows entry points | **Existing:** `fabric-amper.bat` for setup, init, doctor, validate, build, run, publish confirmation, etc. | [fabric-amper.bat](fabric-amper.bat), [HUMAN_QuickGuide.md](HUMAN_QuickGuide.md) |
| Remote release validation | **Partial:** isolated loopback HTTPS Maven authenticated PUT was demonstrated; native publish + configuration-cache HIT was **not**, and real external Modrinth/CurseForge publication was **not** | [QA_REPORT.md, sections 27–28](QA_REPORT.md) |
| Shipping gates | **Open:** actual IntelliJ GUI first-import/manual/auto sync, full dedicated-server runtime, legacy game runtime, and other release blockers must not be inferred from `BUILD SUCCESSFUL` | [QA_REPORT.md](QA_REPORT.md), [specs.md](specs.md) |

**Current baseline is not a replacement for upstream Loom.** Fabric Amper orchestrates and validates a supported Loom/Gradle profile; a broken upstream compiler, game API, IDE integration or third-party mod may need an upstream fix, an upgrade or a user-controlled workaround.

### Implementation follow-up (2026-11-14; distinct from the research snapshot)

The first safe, read-only developer-experience subset has **now been implemented** without adding YAML fields or relaxing the settings-only contract:

| Opportunity | Implemented entry point | Exact boundary |
| --- | --- | --- |
| B01/B04 | `fabricAmperDoctor`; Windows `fabric-amper.bat doctor --full` / `doctor --paths` | Reports configured Java/Gradle/Loom flavor, optional Kotlin, known compatibility evidence and estimated Windows path lengths. It does **not** repair or delete Gradle/Loom caches, inspect IntelliJ GUI, or prove an unknown Loom version. |
| D01 | `fabricAmperExplain`; Windows `explain` | Explains authored Gradle dependencies, test dependencies, Loader requirements and release-site relationships separately. It does **not** resolve an external dependency graph or assert that a Maven GAV is a Loader mod ID. |
| M01 | `fabricAmperPortingReport -PfabricAmperPortTarget=1.21.11`; Windows `port 1.21.11` | A manual migration checklist for specifically scoped 26.3 and 1.21.11 profiles, not automatic source/Mixin migration. Other valid Minecraft versions remain usable with normal tasks even if this optional report cannot compare them. |
| R02/R03 | `fabricAmperReleasePlan`; Windows `plan` | Locally verifies the selected production JAR, Loader ID/version and SHA-256; previews resolved platform metadata without reading secret environment values or uploading. Remote IDs, platform moderation and compatibility are **unverified**. |
| B05/R01 | `FabricAmperDocumentationContractUnitTest` | Checks complete YAML examples in the manuals through the actual strict YAML loader. Partial fragments are deliberately excluded, and independent schema/GUI checks remain separate. |

These additions have dedicated unit tests and an opt-in settings-only consumer TestKit. Successful Gradle compilation or unit tests alone do not close the existing G1–G30 IDE, EULA, remote API, native publication configuration-cache and other shipping blockers. See the subsequently appended [QA report](QA_REPORT.md) for dated actual run results.

## Fast opportunity index

Each ID identifies a **developer problem**, not a promised YAML key. Detailed evidence and possible responses follow.

| ID | Developer burden | Amper opportunity | State | Priority |
| --- | --- | --- | --- | --- |
| B01 | Gradle/Java/Loader/Loom version setup and compatibility | Compatibility diagnostics, actionable doctor and reproducible setup | Partial | P0 |
| B02 | IDE import, run configurations and configuration-cache behavior | IDE smoke suite and honest per-IDE compatibility reporting | Partial | P0 |
| B03 | Slow/corrupt caches and inscrutable resolution failures | Targeted cache diagnostics with **non-destructive** recovery guidance | Candidate | P1 |
| B04 | Windows path-length and toolchain location problems | Path-length check and portable short-cache guidance | Partial | P1 |
| B05 | Extra build metadata and source-set boilerplate | Settings-only YAML + generated Loader metadata | Existing | Maintain |
| D01 | Classpath vs installed-mod dependency mismatch | `fabricAmperExplainDependencies` / dependency origin report | Partial | P1 |
| D02 | Nested JAR and transitive dependency surprises | JIJ manifest + runtime graph audit | Partial | P1 |
| D03 | Maven/Modrinth project IDs, variants and repository failures | Fixed-version/dependency preflight and better diagnostics | Partial | P1 |
| M01 | Breaking Minecraft / Fabric mappings migrations | Explicit version-profile migration report, **not** automatic code rewrite | Partial | P1 |
| M02 | Class Tweaker, Access Widener, Mixin and namespace churn | Compile-time resource/namespace checks; targeted diagnostics | Partial | P1 |
| M03 | Maintaining several Minecraft targets | Declarative, isolated **build matrix** and compatibility manifest (research first) | Candidate | P2 |
| T01 | Client-only code crashes on dedicated server | Side-aware static checks + isolated real server smoke tests | Partial | P0 |
| T02 | Build passes but mod fails at runtime | Safe isolated GameTest/client/server verification recipes | Partial | P0 |
| T03 | Generated vs packaged metadata mismatch | Deterministic root metadata and ZIP integrity checking | Existing | Maintain |
| T04 | Hard-to-understand Mixin/runtime stack traces | Diagnostics with source/resource links, without pretending to repair code | Candidate | P1 |
| R01 | Duplicating Modrinth/CurseForge release fields | `publish.common` inheritance, override and relation mapping | Existing | Maintain |
| R02 | Platform-specific tags, IDs and unsupported claimed versions | Non-upload `releasePlan` + per-platform provenance checks | Partial | P0 |
| R03 | Tokens and unsafe remote publication commands | Env-var-only secrets, reviewable release plan, explicit user approval | Partial | P0 |
| R04 | Partial uploads and accidental repeated versions | Preflight/version collision check + explicit non-transactional resume report | Candidate | P1 |
| R05 | Repeatable publishing in CI | Optional generated pipeline template, dry-run metadata validation | Candidate | P2 |

## Track 1 — Gradle, environment setup and IDE friction

Research basis includes the [Fabric development-environment / Loom guide](https://docs.fabricmc.net/develop/loom/), [Fabric's 26.1 porting checklist](https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/porting/index.md) and the following specific **upstream issue reports**. Issue titles and reported combinations matter; the reports do **not** establish a universal current failure.

| Problem | Public observation / source | What Amper already does | Plausible additional improvement (not implemented) |
| --- | --- | --- | --- |
| **B01 — Version and plugin-mode traps** | [Loom #1585](https://github.com/FabricMC/fabric-loom/issues/1585) reported a no-mappings error while mixing the unobfuscated 26.1/26.2 setup with an incompatible Loom plugin route; [Loom #1541](https://github.com/FabricMC/fabric-loom/issues/1541) reports a similar migration confusion. | Chooses non-remapping profile for Minecraft 26.1+ and remapping profile for supported legacy versions, rather than asking users to choose `net.fabricmc.fabric-loom` vs `-remap` or add mappings manually. | **P0:** diagnostic that prints resolved Minecraft/Loader/API/Loom/Gradle/JDK combinations, why selected, and compatible upgrade steps. Never silently switch versions. |
| **B02 — IDE configuration-cache mismatch** | [Loom #1349](https://github.com/FabricMC/fabric-loom/issues/1349) describes an IntelliJ Gradle init-script listener conflicting with configuration cache; [Loom #1558](https://github.com/FabricMC/fabric-loom/issues/1558) describes a specific composite-build coroutine-agent case, later marked completed. | Configuration-cache and Tooling API behavior has tests; genuine IntelliJ **GUI** first import/manual/auto sync remains unverified. | **P0:** test IntelliJ GUI import+sync as its own gate; provide a known-issue lookup and optional non-mutating recommended settings. Do not claim to fix JetBrains/Gradle internals. |
| **B03 — Cache corruption / stale transformed artifacts** | [Fabric Loom guide: Resolving Issues](https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/loom/index.md) documents corrupted cache recovery; [Loom #1651](https://github.com/FabricMC/fabric-loom/issues/1651) reports an upstream remapped-cache key issue. | Own generated-metadata mirror is content-addressed and tested for deletion/damage recovery; that is **not** the entire Loom or Gradle cache. | **P1:** `doctor --cache` (proposed) to classify Amper mirror vs Loom cache vs dependency cache, print exact safe recovery command. No blanket automatic deletion of `~/.gradle`, IDE settings or user files. |
| **B04 — Windows path-length failures** | [Loom #1475](https://github.com/FabricMC/fabric-loom/issues/1475) gives a concrete long mapped-game-JAR path on Windows that an IDE cannot access. | Windows BAT resolves existing Gradle installations; Amper mirror uses its own shortened content-addressed path but cannot change every Loom JAR path. | **P1:** calculate worst-case derived path lengths, detect deeply nested consumer roots and recommend a short **project-specific** cache/root path; do not alter global long-path settings without user input. |
| **B05 — Composite/multi-project Gradle variants** | [Loom #562](https://github.com/FabricMC/fabric-loom/issues/562) documents an older, now-closed inter-project dependency workflow failure. [Loom #1477](https://github.com/FabricMC/fabric-loom/issues/1477) documents a later, closed mixed obfuscated/unobfuscated multi-project remap issue. | Designed primarily for settings-only single-consumer workflows, plus API publishing and real downstream Maven consumer tests. | **P2 / Research:** define a safe, **versioned** multi-module model only after collecting representative Fabric API/common/client/composite fixtures. Never promise arbitrary Gradle project dependencies work through today's `dependencies[]` syntax. |
| **B06 — Generated IDE launch configuration defects** | [Loom #1621](https://github.com/FabricMC/fabric-loom/issues/1621) reports VS Code-generated `launch.json` containing a field rejected by the Java debugger; [Loom #1346](https://github.com/FabricMC/fabric-loom/issues/1346) reports a missing client configuration for one source-set case. | Gradle `runClient` / `runServer` are available through Loom and the Windows wrapper. | **P1:** create an opt-in check for generated launch configuration shape, JDK and source-set presence for each supported IDE; prefer an upstream fix when the generator is wrong. |
| **B07 — Invisible Kotlin or Gradle download cost** | [Kotlin's Gradle compatibility document](https://kotlinlang.org/docs/gradle-configure-project.html) explicitly maps KGP compatibility to Gradle versions; Amper's own [README](README.md) records unnecessary KGP classpath cost when Kotlin is disabled. | `settings.kotlin.enabled` guards source/application behavior, but the producer's bundled plugin dependencies may still be downloaded. | **P1:** split a minimal Java-only artifact/variant from an opt-in Kotlin component, measure **cold-cache** requests and graph size, preserve settings-only bootstrap and diagnose unsupported Gradle/KGP combinations. |
| **B08 — Parallel configuration / cache lock collisions** | [Loom #1672](https://github.com/FabricMC/fabric-loom/issues/1672) reports an upstream cache lock not exclusive to concurrent processes (November 2026). | Has an atomic metadata mirror and parallel configuration tests, **not** a general fix for Loom's own cache locking. | **Research:** stress independent consumer processes with isolated caches; report the responsible layer and route upstream if Loom/Gradle owns the lock. Avoid placing broad global file locks around all builds. |

### Useful user-facing behavior for Track 1

**Now partially implemented:** `fabricAmperDoctor` (Gradle task) and `fabric-amper.bat doctor --full` provide bounded, non-mutating toolchain/path diagnostics as detailed in the implementation follow-up above. The richer per-artifact cache-origin diagnosis, reliably observed bundled Loom version and full IDE/environment compatibility matrix in the original proposal remain future work. No doctor action downloads a Gradle version or silently rewrites a user's settings script.

The strongest near-term benefit is **making diagnosis understandable**, not replacing the already mature Loom implementation. Run configurations, IDE debugger agents, mapping cache transforms and native Gradle task models are owned elsewhere.

## Track 1b — Dependencies, Loader metadata and classpath behavior

Three similarly named features routinely mean **different** things: Gradle `dependencies[]` selects artifacts for compilation/runtime, `mod.depends` declares required installed mod versions to Fabric Loader, and `publish.common.dependencies[]` describes Modrinth/CurseForge release-site relationships. The [Fabric Loader metadata guide](https://docs.fabricmc.net/develop/loader/fabric-mod-json), [Gradle Java Library guide](https://docs.gradle.org/current/userguide/java_library_plugin.html), and [Modrinth Maven information](https://support.modrinth.com/en/articles/8801191-modrinth-maven) describe different parts of this contract.

| Problem | Evidence / risk | Current Fabric Amper boundary | Opportunity |
| --- | --- | --- | --- |
| **D01 — A Maven dependency is not a Loader requirement** | The [Loader metadata specification](https://docs.fabricmc.net/develop/loader/fabric-mod-json) requires mod-ID-based runtime predicates, while [Gradle library dependencies](https://docs.gradle.org/current/userguide/java_library_plugin.html) are resolved through group/artifact/version and scopes. **Inference:** maintainers can compile successfully while declaring incompatible Loader requirements. | **Existing:** `mod.depends` and `dependencies` remain distinct, with defaults injected for Minecraft/Loader/Java/API. G26 tests predicate consistency for supported profiles. | **P1:** `explainDependencies` traces *which field supplies which requirement* and warns on missing known installed-mod IDs without blindly creating Loader constraints from arbitrary GAVs. |
| **D02 — Modrinth Maven may not carry all expected transitive metadata** | [Minotaur #87](https://github.com/modrinth/minotaur/issues/87) asks for improved Modrinth Maven metadata upload; [Gradle Module Metadata](https://docs.gradle.org/current/userguide/publishing_gradle_module_metadata.html) describes information richer than Maven POM. | **Existing:** Modrinth artifact coordinates can be resolved as dependencies; Amper's own API product publishes POM/`.module`. Neither repairs upstream metadata. | **P1:** report when a runtime library is absent from the resolved graph; show declared vs resolved dependencies, and recommend explicit version-pinned dependencies where real metadata warrants them. |
| **D03 — compile-only and runtime-only may bleed through IDE/Loom variants** | [Loom #1334](https://github.com/FabricMC/fabric-loom/issues/1334) reports runtime interface injection into compile scope; [Loom #1367](https://github.com/FabricMC/fabric-loom/issues/1367) reports a generated VS Code runtime classpath containing `modCompileOnly`. | **Existing:** `scope: all`, `compile-only` and `runtime-only` are modeled, but IDE-runner composition is upstream. | **P1:** per-source-set classpath report and run-task vs IDE-runner parity tests. Expose wrong dependency placement without promising to patch Loom. |
| **D04 — Bundled JARs still have Loader metadata** | [Loom #1637](https://github.com/FabricMC/fabric-loom/issues/1637) asks for more control over nested JAR metadata; Loader's [`jars` field](https://docs.fabricmc.net/develop/loader/fabric-mod-json) is distinct from compile-classpath scopes. | **Existing:** `include: true` creates a nested JAR and verifies root metadata; it does not guarantee arbitrary nested mod configuration is correct. | **P1:** audit duplicate nested IDs, compatible Loader ranges, nested JAR existence and source of every included dependency; require opt-in for any metadata transformation. |
| **D05 — Multi-project outgoing variant mismatch** | Historic [Loom #562](https://github.com/FabricMC/fabric-loom/issues/562) describes an inter-project `namedElements` resolution issue; [Loom #1477](https://github.com/FabricMC/fabric-loom/issues/1477) documents a specific mixed remap mode case later fixed upstream. | **Partial:** local API Maven producer/downstream consumer covered; arbitrary multi-project DSL unsupported. | **P2:** support a narrowly specified multi-module topology only after explicit compatibility and variant matrix tests. |
| **D06 — Error text hides whether the cause is missing artifact, auth, TLS or wrong variant** | [Fabric Loom troubleshooting](https://docs.fabricmc.net/develop/loom/) includes cache/dependency remedies. Amper [QA_REPORT.md](QA_REPORT.md) records FA011 diagnostic false-positive concerns. | **Partial:** FA011 targets genuinely missing declared artifacts; network/auth/variant errors must remain distinguishable. | **P0/P1:** structured failure classifier with declared coordinate, repository, relevant YAML line and safe next action; fail closed when the underlying cause is uncertain. |

Avoid a misleading one-click `include all dependencies` or `convert all Maven dependencies to mod.depends` command. Dependencies need **different scope and runtime semantics**, and any inferred Loader requirement risks preventing a correctly running mod from launching.

## Track 2 — Porting between Minecraft versions and mappings

Official [Fabric 26.1 porting instructions](https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/porting/index.md) explicitly describe changing build plugins, mappings, dependencies, Java target and archive tasks. That burden is real even if individual maintainers automate portions of it. The [official mappings migration guide](https://docs.fabricmc.net/develop/porting/mappings/index) and [Fabric's Minecraft 26.3 update notes](https://fabricmc.net/2026/09/15/263.html) cover **different** code migrations. Fabric Amper should distinguish **build-profile conversion** from **source-code porting**.

| Problem | Source evidence | Amper's valid contribution | Boundary / next step |
| --- | --- | --- | --- |
| **M01 — Remap → non-remap transition** | [Fabric porting to 26.1](https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/porting/index.md) calls out plugin ID, mapping entry, remap tasks and Gradle configurations; [Loom #1585](https://github.com/FabricMC/fabric-loom/issues/1585). | **Existing:** select appropriate Loom ID and `jar` vs `remapJar` from fixed `minecraft.version`; enforce non-Yarn modern mapping profile. | **P1:** `portingReport` displays **before vs after** build/metadata changes. Never suggest modern mappings work on old JAR dependencies. |
| **M02 — Java 21 → Java 25 toolchain** | Same [26.1 porting checklist](https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/porting/index.md); [Kotlin Gradle support matrix](https://kotlinlang.org/docs/gradle-configure-project.html). | **Existing:** verify fixed Java release for modern profile and fail with FA005 rather than compiling for the wrong bytecode target. | **P0:** doctor checks running JDK, compile release, Gradle toolchains and Kotlin compatibility separately; supply remediation without installing an unreviewed JDK. |
| **M03 — Mapping tools do not rewrite all mod code** | [Fabric mappings migration](https://docs.fabricmc.net/develop/porting/mappings/index) describes tooling limitations, including Kotlin/mixin constraints; [Fabric API 26.1 porting changes](https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/porting/fabric-api.md) describe API renames. | **Existing:** correct namespace/mapping selection. | **P2:** optional **read-only** migration checklist with source hit locations (names/imports) and links; require human review of rewritten code, Mixins, generics and generated sources. Do not promise automated source compatibility. |
| **M04 — Fabric API feature removal / API churn** | [Fabric's Minecraft 26.3 update](https://fabricmc.net/2026/09/15/263.html) documents version-specific Fabric API and Minecraft changes. | **Existing:** pins exact Fabric API and Loader versions in YAML and Loader requirements. | **P1:** opt-in version catalog of **source-linked** breaking-change advisories; show suspected API usages and compatibility review tasks. No fake automatic replacement of events or registration APIs. |
| **M05 — Class Tweaker / Access Widener namespace and headers** | [26.1 porting guide](https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/porting/index.md), plus [Loom #1619](https://github.com/FabricMC/fabric-loom/issues/1619) reporting a namespace remap case. | **Existing:** `mod.accessWidener`, validated file existence and mode-specific `classTweaker v1 official` vs legacy headers. | **P1:** clear diagnostic at header, namespace and resource line; optional dry-run namespace check in a migration report. Does not repair upstream Mixin/class-tweaker transformations. |
| **M06 — Mixins can fail after mappings or target changes** | [Loom #1504](https://github.com/FabricMC/fabric-loom/issues/1504) describes a remap limitation for missing Mixin target classes; [Loom #1369](https://github.com/FabricMC/fabric-loom/issues/1369) gives another remapping edge case. | **Partial:** resource path, side and file validity checks; generated Loader metadata includes required Mixin configs. | **P1:** validate config JSON grammar/declared target existence where resolvable; run isolated test game to detect unresolved injections. Warn that runtime transformation and third-party compatibility remain uncertain. |
| **M07 — Porting multiple supported versions** | [Fabric porting docs](https://docs.fabricmc.net/develop/porting/) and real [Loom mixed-version issue #1477](https://github.com/FabricMC/fabric-loom/issues/1477) illustrate the incompatibility surface. | **Existing only one selected Minecraft profile per YAML build**; legacy and modern profiles separately tested. | **P2 / Research:** allow a *separate*, isolated per-version test matrix before considering a single-file multi-target DSL. No shared mutable caches or magical multi-version artifact compatibility. |
| **M08 — Fabric ↔ NeoForge and other loaders** | Official [NeoForge 1.21.11 → 26.1 migration primer](https://docs.neoforged.net/primer/docs/26.1/) discusses another ecosystem, with distinct APIs and metadata. | **External / non-goal:** `product: fabric/mod` and `fabric/api` target Fabric Loader and Loom. | Do not add an invented `product: neoforge` alias without a dedicated loader implementation, metadata generation, tests and a new versioned schema contract. Research platform abstraction only if demand is real. |

### A safe porting assistant is a report, not a speculative rewrite engine

One possible command would compare **existing** and **requested** versions and emit a non-mutating report:

```text
[PROPOSED ONLY] fabricAmperPortingReport --target-minecraft=26.3
build profile: legacy remapping -> modern non-remapping
JDK requirement: 21 -> 25
metadata: check Class Tweaker header and Mixin namespaces
source: review imports, registries and API changes (not auto-fixed)
verification: compile, actual client, actual dedicated server, tests
```

This is **illustrative output, not a runnable task or accepted YAML field**. If eventually implemented, require a complete compatibility map, no unreviewed source edits, failure on unknown target versions, and separate consumer fixtures for each supported profile. Do not infer that a successful archive proves code-level porting complete.

## Track 3 — Publishing, compatibility metadata and release safety

Source types are different here. [Modrinth's Create Version API](https://docs.modrinth.com/api/operations/createversion/) and [CurseForge's author Upload API](https://support.curseforge.com/support/solutions/articles/9000197321) document **real platform requirements**, not necessarily end-user complaints. [Gradle Maven publishing](https://docs.gradle.org/current/userguide/publishing_maven.html) and [GitHub Actions' Gradle package guide](https://docs.github.com/en/actions/tutorials/publish-packages/publish-java-packages-with-gradle) document how release automation and credentials work. The table describes *friction inferred from those necessary workflows*, labeled accordingly.

| Problem | Primary evidence and developer impact | Existing coverage | Candidate improvement / constraint |
| --- | --- | --- | --- |
| **R01 — Duplicate release type, title, game versions and notes** | The [Modrinth version request](https://docs.modrinth.com/api/operations/createversion/) and [CurseForge upload request](https://support.curseforge.com/support/solutions/articles/9000197321) each need their own platform-shaped metadata. **Inference:** manually keeping both configurations synchronized invites copy/paste drift. | **Existing:** schema v1 `publish.common` shares `releaseType`, `name`, string `gameVersions`, notes and relationships, with explicit destination override. | **Maintain:** compile tests on inherited/overridden values, no duplicate arrays or secret values; preserve the independent platform schemas. |
| **R02 — String tags vs integer game-version IDs** | CurseForge's [upload API](https://support.curseforge.com/support/solutions/articles/9000197321) distinguishes `gameVersionNames` and `gameVersions` IDs, whereas [Modrinth](https://docs.modrinth.com/api/operations/createversion/) uses game-version strings. | **Partial:** `publish.common.gameVersions` maps to Modrinth strings and CurseForge **name tags**, never to CurseForge numeric IDs. | **P0:** offline `releasePlan` explicitly labels each resolved field, then optional **read-only, user-approved** lookups verify currently accepted tags/IDs. Fail closed on unknown mappings; no guessed numeric identifiers. |
| **R03 — Dependency relationships have different shapes** | [Modrinth Create Version](https://docs.modrinth.com/api/operations/createversion/) has version/project relationships with distinct `dependency_type`, while [CurseForge author upload](https://support.curseforge.com/support/solutions/articles/9000197321) uses relation slugs, project IDs and different type names. | **Existing:** `common.dependencies[]` maps required/optional/incompatible/embedded; platform-specific relationships override matching identifiers and append new ones; explicit `[]` clears inherited data. | **P1:** optional verify-only canonical project matching by **actual platform IDs**, detect incomplete mappings and show both resolved representations side-by-side. Do not infer identifiers from Loader mod IDs or Maven GAVs. |
| **R04 — Version collision and double publication** | Version creation on [Modrinth](https://docs.modrinth.com/api/operations/createversion/) and uploads on [CurseForge](https://support.curseforge.com/support/solutions/articles/9000197321) are separate network operations. **Risk inference:** retries and same-version releases can have conflicting outcomes. | **Partial:** explicit per-destination tasks, no implicit upload on build, BAT confirmation. No successful external Modrinth/CurseForge uploads established. | **P1:** preflight checks existing version when authorized; release manifest per platform, successful/failed/skipped targets and a manual resume plan. **No** blind automatic retry or promises of cross-site rollback. |
| **R05 — Credentials, scope and API limits** | [Modrinth API overview](https://docs.modrinth.com/api/) specifies User-Agent and rate limit policy; [CurseForge upload docs](https://support.curseforge.com/support/solutions/articles/9000197321) require a publishing token. | **Existing:** env-variable **names** in YAML, secrets resolved only for publish tasks, HTTPS Maven constraints; API upload failures classified without logging token data. | **P0:** redact all tokens, HTTP headers and response secrets in any `releasePlan` / log export; bounded timeouts and clear rate-limit/auth/validation diagnosis. Do not instruct users to put secrets in YAML or URL parameters. |
| **R06 — Local vs Maven Central vs platform publication** | [Gradle's Maven Publish guide](https://docs.gradle.org/current/userguide/publishing_maven.html) describes publications, repositories, components and task distinctions; [GitHub Actions guide](https://docs.github.com/en/actions/tutorials/publish-packages/publish-java-packages-with-gradle) describes CI package credentials. | **Existing:** `fabric/api` uses `maven-publish`, local project Maven and opt-in remote Maven; `publishFabricAmper` aggregator differs from Gradle's broader `publish`. | **P0:** a command preview should say **exactly** which tasks and services may receive artifacts; never choose `gradle publish` merely to validate syntax. |
| **R07 — Incorrect release artifact, wrong Loader metadata** | [Modrinth version API](https://docs.modrinth.com/api/operations/createversion/) accepts uploaded files; [Fabric Loader metadata](https://docs.fabricmc.net/develop/loader/fabric-mod-json) controls game-side interpretation. **Risk inference:** a valid ZIP can still be the wrong profile or untested game version. | **Existing:** check final `jar`/`remapJar` and one root `fabric.mod.json`; API/library Maven publishes actual variants. | **P0:** final **release bundle manifest**: mod ID/version/game/Java/Loader/mappings, JAR hash, nested JAR list, side and declared release tag; warn when claimed upload versions go beyond tested profile. |
| **R08 — Builds with configuration-cache but release task does not reuse it** | [Gradle configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html) is task/configuration sensitive. Amper [QA section 28](QA_REPORT.md) reports **real local HTTPS authenticated PUT success but native Maven publish configuration-cache DISABLED**, distinct from dry-run STORE/HIT. | **Partial:** local loopback transfer verified; native Gradle `PasswordCredentials` task and cache limitation remains. | **P1 / Research:** investigate upstream-compatible credential handling or an isolated publish adapter **without weakening credentials/security or bypassing Gradle semantics**. Test real HTTPS and cache replay separately; never equate dry-run with authenticated PUT. |
| **R09 — Platform review and moderation are not immediate** | [CurseForge project submission guidance](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project) describes project creation/submission workflow. **Inference:** uploading bytes does not mean the version is approved or visible to all users. | **External / non-goal:** Amper can report an API response but cannot guarantee human moderation, approval, download availability or platform discoverability. | **P2:** optional user-initiated status check with links to official project/release pages. Do not automatically open an account, create a project or manipulate moderation status. |
| **R10 — Reproducibility and CI provenance** | [GitHub Actions Gradle publication guide](https://docs.github.com/en/actions/tutorials/publish-packages/publish-java-packages-with-gradle) demonstrates multi-stage package jobs; [Gradle publishing docs](https://docs.gradle.org/current/userguide/publishing_maven.html) distinguish metadata and artifacts. | **Existing:** deterministic JAR/metadata tests and optional sources JAR; no first-party generated CI template established. | **P2:** generate a **reviewable** workflow sample with pinned JDK/Gradle/plugin, verification and manual deployment gate, checksums and secret names. Never commit token values or silently enable releases on pushes. |

### Proposed non-upload publication plan

The highest-value safe enhancement is a **preview of effective values after `publish.common` inheritance**, before touching either publishing platform:

```text
[ILLUSTRATIVE — NOT A CURRENT COMMAND]
fabricAmperReleasePlan

artifact: build/libs/example_mod-1.0.0+26.3.jar
jar_sha256: ...computed after successful build...
modrinth:
  target: explicit, token: environment variable name only
  version_type: release, game_versions: ["26.3"], loaders: ["fabric"]
curseforge:
  target: explicit, token: environment variable name only
  gameVersionNames: ["26.3", "Fabric", "Client", "Server"]
  gameVersions (numeric IDs): NOT SET
publish actions: NOT EXECUTED
```

For any future command, the default must be **offline/read-only** after local build. With an optional explicit read-only network flag, it may query **official current metadata** to verify IDs and release/version collisions, respecting auth, rate limits and privacy. A future `publish` command must still require the user to authorize actual external upload. `releasePlan` must never assert a remote ID exists when it has not queried or otherwise verified it.

## Track 4 — Runtime failures, tests, metadata and support

[Fabric's Automated Testing guide](https://docs.fabricmc.net/develop/automatic-testing), [Debugging Mods](https://docs.fabricmc.net/develop/debugging), and the [Loader metadata specification](https://docs.fabricmc.net/develop/loader/fabric-mod-json) describe distinct development responsibilities. Runtime reports show why packaging validation alone is insufficient.

| Problem | Report or official source | Existing mitigation | Additional opportunity and limitation |
| --- | --- | --- | --- |
| **T01 — Client code loaded on dedicated servers** | [Fabric API #1074](https://github.com/FabricMC/fabric-api/issues/1074), a historical report about keybinding code crashing a server; [FabricMC discussion #3475](https://github.com/orgs/FabricMC/discussions/3475) about client-side mod classes on server. | **Partial:** `mod.environment`, split client source set, `target: client`, client-target scope guards, side-safe Mixin resource checks. | **P0:** isolated `runServer` smoke gate, class/reference scan for client-only packages in common initializer, and an explicit side-safety report. Static analysis may flag false positives and cannot prove arbitrary runtime paths safe. |
| **T02 — Mod runs in dev but fails in packaged production** | [Loom #1504](https://github.com/FabricMC/fabric-loom/issues/1504) describes a Mixin remap failure where targets are absent from the classpath; [Loom #1355](https://github.com/FabricMC/fabric-loom/issues/1355) reports interface injection/refmap issues across subprojects. | **Partial:** final JAR verification and isolated consumer builds; not a proof of Mixin transformer success or production runtime. | **P0/P1:** run an isolated **final distribution JAR** under a minimally pinned Loader/game profile and assert expected initializer/runtime log. Separate failures of Loader, Mixin transformer and mod code. |
| **T03 — GameTest and regular JUnit require different setup** | [Official Fabric automated testing guide](https://docs.fabricmc.net/develop/automatic-testing) distinguishes unit test vs game-integration test setup. | **Partial:** `test-dependencies`, JUnit discovery and example Gradle tests; actual in-game GameTests still need source, runtime and test metadata. | **P1:** opt-in YAML test profiles and generated minimal runner wiring with deterministic test discovery; **never invent a test class or mark a skipped test PASS**. |
| **T04 — Reports often lack reproducible crash context** | [Fabric debugging guide](https://docs.fabricmc.net/develop/debugging) describes debugging and useful runtime information. | **Partial:** FA diagnostics have code/path and selected YAML line/column; failures after startup may be native game, JVM or Mixin errors. | **P1:** `diagnosticsBundle` with toolchain/version identifiers, **redacted** Gradle error snippets, JAR manifest and crash-log pointers; no automatic upload or copying private worlds, accounts, tokens, or full personal paths. |
| **T05 — Class Tweaker namespace failure at runtime** | [Loom #1619](https://github.com/FabricMC/fabric-loom/issues/1619) reports `named` vs `official` mismatch on dependent JAR remapping. | **Partial:** validate authored resource file header and platform profile; upstream dependency remap remains Loom-owned. | **P1:** scan packaged class-tweaker headers in **owned** and nested resources and point to relevant upstream issue when external dependency mutation is responsible; isolated runtime still required. |
| **T06 — Injected API/Gradle models disagree in IDE** | [Loom #1393](https://github.com/FabricMC/fabric-loom/issues/1393) reports missing injected interfaces during Gradle sync; [official interface-injection guide](https://github.com/FabricMC/fabric-docs/blob/main/develop/class-tweakers/interface-injection.md) documents IDE refresh and source generation. | **Partial:** schema and generated mirror exist before Loom configuration; Tooling API model tests are not full GUI sync. | **P0:** real IntelliJ import, `genSources`, client/common source display and manual+automatic reload acceptance. Don't replace real GUI evidence with a headless model test. |
| **T07 — Nested JAR classes, metadata and Loader dependencies diverge** | [Loom #1637](https://github.com/FabricMC/fabric-loom/issues/1637) requests control over the metadata of JARs embedded via JIJ; [Fabric Loader metadata](https://docs.fabricmc.net/develop/loader/fabric-mod-json) describes `jars` and `depends`. | **Partial:** `include: true` and final archive check; support for arbitrary embedded mod metadata customization is **not** established. | **P1:** dependency-bundle audit listing nested JAR, Loader ID/version, duplicate IDs and runtime-loader compatibility. Treat author-controlled metadata rewriting as a high-risk opt-in design, not default behavior. |
| **T08 — Build artifact stale after YAML or resource edits** | [Fabric Loom guide](https://docs.fabricmc.net/develop/loom/) explains cache interactions. Amper's [QA_REPORT.md](QA_REPORT.md) records mirror deletion/tamper and resource-snapshot tests. | **Existing:** content-addressed mirror, generated vs packaged JSON and deterministic JAR byte checks. | **Maintain/P0:** preserve configuration-cache 4-run regressions, simultaneous mirror tamper scenarios and exact resource snapshots whenever extending the DSL. |
| **T09 — Reproducing client, server and graphics-specific failures** | [Fabric test docs](https://docs.fabricmc.net/develop/automatic-testing) show environment-specific testing. [Fabric 26.2 release notes](https://fabricmc.net/2026/06/15/262.html) describe graphics backend transitions affecting rendering integrations. | **External / partial:** Gradle `runClient` / `runServer` exist, but a successful launch is not a visual/graphics correctness assertion. | **P2:** generate an **isolated** test run matrix and archive safe logs; never claim that headless runs verify GPU shaders, visuals, FPS, input devices or platform-specific drivers. |
| **T10 — Generic error text hides the actual YAML cause** | Official [Fabric Loom guide](https://docs.fabricmc.net/develop/loom/) discusses cache and Gradle failures; project [QA_REPORT.md](QA_REPORT.md) includes FA011 classifier investigations. | **Existing:** FA001–FA015 and source-line propagation for YAML errors, with known gaps in upstream error classification. | **P0/P1:** stable “what happened / YAML path / exact source / next action / relevant upstream docs” errors. Do **not** rewrite TLS/401/offline/variant conflicts as a false missing-artifact diagnosis. |

### Important testing boundary

The binary plugin can create Gradle tasks and check static resources, but it cannot **prove** that a mod will function on an arbitrary client's modpack, a user's existing world, a dedicated server with unknown plugins, or all graphics drivers. A credible release gate must report results by environment: **compile**, **archive integrity**, **client Loader startup**, **dedicated server Loader startup and ticks**, **GameTest**, **GUI/IDE**, and **remote publication**. Keep EULA acceptance and any account/world changes under human control.

## Additional narrow developer reports worth preserving

These are especially useful **regression fixture ideas**. They are not proof that every behavior still fails in the latest upstream release.

| Report | Specific burden and relevance | Candidate regression or UX enhancement |
| --- | --- | --- |
| [Loom #1367](https://github.com/FabricMC/fabric-loom/issues/1367) — VS Code launch classpath | Reported generated VS Code run config including `modCompileOnly` at runtime even though the Gradle task excludes it. | Compare Gradle `runClient` runtime classpath vs generated IDE launch classpath; preserve `scope: compile-only` semantics. Escalate underlying generator bug upstream. |
| [Loom #1334](https://github.com/FabricMC/fabric-loom/issues/1334) — injected interfaces from runtime scope | Reported compile-time injection of interfaces declared on the runtime scope, causing missing type errors. | `explainClasspaths` should show why each dependency appears in compile/runtime/client; add negative scope leakage TestKit. |
| [Loom #1537](https://github.com/FabricMC/fabric-loom/issues/1537) — legacy Java run argument files | Reported generated IntelliJ run configs using Java 9+ argfile behavior with Java 8 targets. | Separate JDK/platform compatibility from today's Java 25 baseline; never generalize a modern solution to unsupported Java 8 profiles. |
| [Minecraft Dogs Unleashed #253](https://github.com/grabartley/minecraft-dogs-unleashed/issues/253) — release CI artifacts | Project-maintainer issue describes failed automated Modrinth publishing, including artifact/version selection problems. | Deterministic **single selected production JAR** (no `-sources`/`-dev` accidental upload), non-`unspecified` version, preflight release manifest. |
| [Modrinth Minotaur #87](https://github.com/modrinth/minotaur/issues/87) — Maven metadata | Request highlights limitations of metadata/transitive behavior in Modrinth's artifact ecosystem. | Verify **our own API Maven** POM and Gradle `.module` downstream scopes; do not claim to repair upstream Modrinth Maven missing metadata. |
| [Modrinth Minotaur #23](https://github.com/modrinth/minotaur/issues/23) — several Modrinth projects | User asks for easier publication to multiple project IDs from one build. | **P2:** research multiple destination profiles after establishing safe single-target semantics, per-target checksum/release status and explicit approval. Currently one Modrinth destination per YAML. |
| [Fabric data-generation setup](https://docs.fabricmc.net/develop/data-generation/setup) | Official steps involve generated resources, source set setup and entrypoint/task wiring; generated JSON can be missed in package assembly (**risk inference**). | **P1:** optional `datagen` profile with generated output snapshots and duplicate resource detection, if compatible with settings-only authoring. Requires a dedicated test manifest and no overwrite of authored files. |
| [Fabric 26.2 release changes](https://fabricmc.net/2026/06/15/262.html) and [26.3 changes](https://fabricmc.net/2026/09/15/263.html) | Changing graphics/input/registry APIs can break functional mod behavior even when Java code compiles. | **P2:** version-specific source warnings and opt-in client visual tests. Do not promise shader or Vulkan correctness without real driver/GPU checks. |
| [Fabric API #5299](https://github.com/FabricMC/fabric-api/issues/5299) — compatibility labeling | Game-version availability reports can disagree with actual mod dependency compatibility (specific historical case). | **P1:** show exactly which project, dependency and version metadata were checked, and differentiate claimed compatibility from tested runtime. |

## Product roadmap proposed from this research

### Stage 0 — Finish the fundamental release blockers first (P0)

**These are completion tasks, not a new user-facing feature promise.** Do not postpone them solely to add more YAML keys:

1. **IDE GUI acceptance:** record IntelliJ real first import and manual/auto Gradle sync; inspect Java and optional Kotlin classpaths, generated `fabric.mod.json` mirror, `runClient` and source regeneration. Keep configuration-cache STORE/HIT evidence separate. Targets: **G6/G28** and [Loom #1349](https://github.com/FabricMC/fabric-loom/issues/1349).
2. **Isolated full dedicated-server boot:** user-controlled EULA acceptance, separate temporary world/settings, confirm Loader initializer and actual server tick; no changes to the user's real server or account. Targets: **G15/G27** and side-specific regressions.
3. **Legacy production startup:** verify a real 1.21.11 remapped artifact under supported Java 21 runtime; a successful legacy `remapJar` test is not game runtime proof. Targets: **G9/G15**.
4. **Real remote Maven credentials + configuration cache:** upstream Gradle native `PasswordCredentials` limitation remains even after local HTTPS authenticated PUT success; investigate a safe design or transparently mark the combination unsupported. Targets: [QA section 28](QA_REPORT.md).
5. **Release-readiness reporting:** assemble an exact status table from gated tests, not `BUILD SUCCESSFUL` alone. G1–G30 tests and 40 opt-in skipped tests must be differentiated from actual failures, TODOs and explicitly deferred risky uploads.

### Stage 1 — Small, high-leverage developer-experience tools (P0–P1)

| Proposed deliverable | Problem IDs | Narrow scope | Definition of done / regression tests |
| --- | --- | --- | --- |
| **A. `fabricAmperDoctor` + improved Windows `doctor`** | B01–B04, B07, M02 | Print resolved JDK, Gradle, Loom profile, KGP presence, Java release, selected YAML, repo paths, known environment pitfalls. Avoid network and auto-deleting caches. | Works under warm and cold caches, short/long Windows paths, optional Kotlin false/true, selected supported Gradle variants; actionable safe failure codes. |
| **B. `fabricAmperExplain` / `explainClasspaths`** | D01–D03, T07, T10 | Report every dependency's source, fixed GAV/project, kind, scope, target, exported/include, runtime Loader relation and nested JAR status. Separate inherited Loader defaults. | Tests cover mod/library, Maven/Modrinth, `scope` 3 forms, `target: client`, `exported`, `include`, KGP off, no resolution during ordinary help unless requested. |
| **C. `fabricAmperReleasePlan` (no upload)** | R01–R08 | Show effective post-`publish.common` Modrinth/CurseForge metadata, actual chosen JAR/hash/Loader data, tags/ID types and selected targets; redact all credentials. | No network by default; stable JSON/text, platform override/append/clear tests, test with fake IDs and no tokens, assert **zero HTTP** and zero global Maven writes. |
| **D. `fabricAmperCheckSides`** | T01, M06 | Warn on references to obvious client-only classes in common/server entrypoint paths; document limits. | Positive/negative client/common/server fixtures; avoid false positives on strings/reflection; actual server smoke remains separate required gate. |
| **E. `fabricAmperCheckResources`** | M05, M06, T05, T08 | Validate Mixin JSON resource existence, Class Tweaker headers, icons, split-resource paths, duplicate generated resource names and JAR output. | Mutation tests for missing/mislocated resources, duplicate ZIP entries and nested class-tweaker resource paths; deterministic byte and mirror checks remain PASS. |
| **F. Documentation contract guard** | B05, R01, R02 | Automatically check docs and examples against strict decoder and Draft-07 schema. Keep latest syntax, optional `schema`, `publish.common` and BAT commands synchronized. | All intended full YAML examples schema-valid, selected examples parse through actual Kotlin loader; no stale "schema required" claims or imaginary publish keys. |

The **documentation contract guard is immediately justified by a current repo inconsistency**: the root [README.md](README.md) still shows older `publish.modrinth`/`curseforge` duplicated release fields and always writes `schema: 1`, while the current decoder and [HUMAN.md](HUMAN.md) support `publish.common` and optional schema. The old README example is still *valid*, so this is a **documentation/visibility debt**, not evidence that an existing consumer project is broken. Update it when implementing the contract guard; this research task does not silently rewrite it.

### Stage 2 — Extended development workflows (P1)

| Feature candidate | Why | Guardrail |
| --- | --- | --- |
| **Porting report** | M01–M07: compare old/new supported game profile; show API changes and resource migration checklist with source links | Report first, never mass-rewrite source or guess third-party versions; human reviews incompatible API semantics |
| **JUnit + GameTest profile** | T02/T03: a JUnit PASS is not a Mixin/Loader integration PASS | Separate generated test-only metadata/source sets and reports; opt-in game launch, no automatic EULA acceptance |
| **Data generation profile** | Configuring recipes/models/tags/registries requires additional Loom/Fabric tasks and output wiring | Opt-in, reproducible, no overwrite of authored JSON, no fake client/server test PASS |
| **Redacted diagnostics bundle** | Developers need reproducible Mixin/loader/crash log context | Local, opt-in, clear file list; strip absolute personal paths, secrets and private account/world data; never upload by default |
| **Cache diagnosis, not cache destruction** | B03/B08: own mirror recovery does not repair Gradle/Loom caches | Explain whether cache is ours, Loom's or Gradle's; ask before `--refresh-dependencies`, never purge global `~/.gradle` |
| **Release receipt / retry explanation** | R04/R09: partial publication and moderation statuses | One receipt per actual platform response; do not auto-retry non-idempotent create-version requests or claim moderation approval |
| **Cold-cache Kotlin isolation** | B07: currently KGP artifacts may be downloaded with `settings.kotlin.enabled: false` | Compare clean caches under Kotlin off/on, verify true zero KGP resolution for off before claiming solved |

### Stage 3 — Larger ecosystem extensions (P2 / research)

1. **Single repo / multiple MC versions:** separate per-target build caches and artifacts; explicit compatibility matrix and independent Loader runtimes. First test an isolated multi-consumer reference project. Do not smuggle a dynamic `minecraft.version` selector into today's fixed-version schema.
2. **Multiple publishing projects of the same platform:** explicit target IDs, artifact/version naming rules, per-destination approval and receipts; no hidden fan-out. [Minotaur #23](https://github.com/modrinth/minotaur/issues/23) demonstrates actual interest, but it is not proof this is a high-frequency Amper use case.
3. **Generated CI templates:** optional GitHub Actions-style sample with pinned toolchains, artifact checksums, manual publication step, minimum token permissions and no release from untrusted pull requests. No new dependency on a service for normal local builds.
4. **Multi-loader support (NeoForge, etc.):** this is a **new product architecture**, not a small alias under `product`. Any future support needs its own metadata generator, dependencies, dev runtime, source sets, publishing semantics, versioned schema and comprehensive tests.

## Concrete acceptance tests for the highest-value proposals

| Test scenario | Expected observable result | Must **not** happen |
| --- | --- | --- |
| A consumer uses the current `mod.yaml` without `schema` | Latest supported v1 parsed, exactly the same effective model as explicit `schema: 1` | Unexpected upgrade to unsupported grammar or silent key coercion |
| A consumer has `publish.common` and two platform overrides | Preview shows *resolved* inherited values, matching-ID relation replacements, appended relations and explicit empty-array clear behavior | Platform IDs, credentials or numeric CurseForge version IDs invented |
| `releasePlan` in an offline CI workspace | Locally verified JAR/hash/Loader metadata and required input errors; no network task | Remote HTTP calls, reading secret values for output, mutating user-global Maven cache |
| `doctor` on a Windows path deeper than a known fragile limit | Identifies exact path and the owning cache layer, proposes a safe shorter workspace | Automatically deleting or moving global caches, altering Windows policy |
| Java-only consumer with Kotlin disabled in a pristine Gradle cache | No Kotlin plugin artifact download after **separate future fix** | Kotlin is nonetheless applied or pinned KGP silently changed |
| Common source imports a client-only Minecraft class and the mod targets server | Static warning pointing to class/path plus isolated dedicated-server launch result | Claiming 100% safety from heuristic static analysis |
| Mixin config references a missing resource or wrong Class Tweaker namespace | Precise YAML/resource path diagnostic and test failure | Rewriting third-party resource/JAR without explicit authorization |
| API library publishes locally and downstream imports it | Gradle `.module`/POM, sources, public/implementation/private scope and Loader metadata are consistent | Assuming Modrinth Maven's metadata is automatically the same |
| Explicit external publish is requested | Preflight target/version/artifact/permission plan, explicit action, redacted result per platform | Implicit upload on `help`/`build`, retry storm, cross-platform transaction claim |

In particular, new reports and tasks must be compatible with Gradle configuration-cache requirements and the **settings-only** consumer contract. Any new YAML key is a **schema/decoder/validator/test/docs change**, not a documentation-only invention.

## Constraints and non-goals worth writing into every design proposal

1. **Source-code behavior:** Fabric Amper cannot guarantee automatic migration of Java/Kotlin API calls, Mixin targets, networking protocols, graphics shaders, world-save formats, or gameplay logic after a Minecraft update.
2. **IDE and upstream plugins:** Fabric Loom, Gradle, IntelliJ, VS Code, Fabric Loader and third-party mods have independent bugs and compatibility ranges. Report exact versions and reproduce upstream problems, rather than masking them with unsafe global workarounds.
3. **Dependency truth:** a Maven artifact, Modrinth project ID and Fabric Loader mod ID are not interchangeable. `dependencies[]` controls Gradle classpaths; `mod.depends` controls Loader requirements; `publish.common.dependencies[]` declares release-site relationships.
4. **Safety and account ownership:** no secret in YAML; no automatic account setup, EULA acceptance, world changes, remote upload, moderation manipulation or destructive global cache cleanup.
5. **Packaging quality:** a single generated `fabric.mod.json`, deterministic JAR and a green Gradle build are **necessary but insufficient** for verified client/server gameplay or publication availability.
6. **One source of truth:** document fields supported **in the current decoder and schema**; `fabricAmperDoctor` and `fabricAmperReleasePlan` are now **Gradle task names, not `mod.yaml` keys**. `datagen` and `settings.testing` remain **proposals** and must not be copied into `mod.yaml` today.
7. **Release honesty:** actual authenticated loopback Maven PUT and dry-run cache reuse are distinct observations; real Modrinth/CurseForge external uploads, dedicated-server boot and IDE GUI gates cannot be inferred from other tests.

## Suggested first three new features — after the blocking gates

If capacity is limited, a focused sequence beats adding many speculative YAML switches:

1. **Explain and diagnose the existing build.** Implement `fabricAmperDoctor` + `fabricAmperExplain` for current version profiles and dependency scopes first. It can reduce troubleshooting costs without changing users' mod code or introducing remote side effects. Test cold/warm/no-network states, Windows paths and JVM variants. **Do not confuse this with G6/G28 real IntelliJ verification**, which still needs its own acceptance.
2. **Show the exact release before uploading it.** Build `fabricAmperReleasePlan` on the existing `publish.common`/platform DTOs, so a user can see what Modrinth and CurseForge would receive without supplying API credentials or making HTTP calls. Include the final JAR's hash and generated Loader metadata, explicitly annotate unknown/unchecked external IDs, and make omissions visible.
3. **Make side/runtime failures reproducible.** Extend the existing generated-JAR verifier with a side/classpath report and an isolated test harness. Validate Loader startup separately on actual client and dedicated server, preserving EULA and workspace boundaries; only after that explore GameTest convenience and Mixin failure explanations.

The first product milestone should be **one easily reproduced, fully explained problem resolved**, not a new feature count. Possible measures for a later usability study (not measured today) include time from clone to first successful consumer build, number of independent configuration choices, share of dependency errors with actionable source locations, percentage of previewed release fields that match generated requests, and number of misleading "build passed → game works" reports.

## Research methods, attribution and confidence

Four separate read-only research tracks contributed:

| Track | Public-source focus | How it was used |
| --- | --- | --- |
| **Subagent 1 — build & IDE** | Fabric Loom issue reports, Fabric setup guide, Gradle configuration-cache guidance and current Amper repo | Extract concrete Gradle/JDK/IDE/cache failure modes and feasible non-destructive diagnostics |
| **Subagent 2 — updates & migration** | Official Fabric 26.1/26.2/26.3 migration guides, mapping/API changes, Loom issues, Kotlin/Gradle compatibility, NeoForge boundaries | Distinguish automatic build profile selection from manual mod source migration |
| **Subagent 3 — release systems** | Official Modrinth / CurseForge upload APIs, Minotaur issues, Gradle Maven publishing, GitHub Actions release guidance | Reconcile different platform metadata/permissions with existing `publish.common`; define upload-safe preflight ideas |
| **Subagent 4 — runtime & QA** | Fabric Loader metadata, testing/debugging/datagen docs, Mixin/class-tweaker and side-specific issue reports | Identify failure classes which escape a green build and propose independent environment gates |

**Evidence hierarchy:** (1) direct upstream issue with observed steps/logs = evidence of that *reported* failure and affected versions; (2) official documentation = documented workflow/constraint; (3) Amper's own parser/schema/tests = evidence of current project behavior within the test scope; (4) a proposed improvement = **analysis**, not verified external demand. No prevalence percentages, number of hours lost, market-size claims or promised engineering estimates were derived from this research.

**How to evaluate each future item before shipping:**

1. Open the linked upstream issue/documentation and recheck **the precise version, state and scope**; closed issues can still justify regressions, but not a claim that upstream remains broken.
2. Write a minimal **settings-only** consumer that reproduces the relevant failure or warning without modifying a real user world, server or account.
3. Establish a before/after baseline: real Gradle task output, JAR metadata, Tooling API model, IDE GUI, Loader runtime or remote network **as appropriate**. One kind of PASS cannot replace another.
4. Implement the smallest safe change with stable FA diagnostics and exact schema/decoder/validator agreement. For API/platform data, distinguish **locally checked** values from **verified external** values.
5. Re-run affected G1–G30 gates, configuration cache, byte determinism, side isolation, Kotlin-off cold cache where relevant, and regression tests. Record failures and skipped/unsafe work honestly.
6. Improve [HUMAN.md](HUMAN.md), the merged English [HUMAN_QuickGuide.md](HUMAN_QuickGuide.md) (the former QuickStart now redirects there), [FabricAmperSKILL.md](FabricAmperSKILL.md), and [FabricAmperSKILL_SYN.md](FabricAmperSKILL_SYN.md) only when the new feature is **actually implemented**, keeping the shorter AI skill's stop-or-escalate rule accurate.

## Reference entry points

The **specific claim-level links are embedded directly in the tables above**; these entry points help maintainers return to authoritative sources without treating a list of links as proof:

- [Official Fabric documentation](https://docs.fabricmc.net/), particularly [Loom](https://docs.fabricmc.net/develop/loom/), [Loader metadata](https://docs.fabricmc.net/develop/loader/fabric-mod-json), [mappings migration](https://docs.fabricmc.net/develop/porting/mappings/), [automatic testing](https://docs.fabricmc.net/develop/automatic-testing), [debugging](https://docs.fabricmc.net/develop/debugging) and [datagen setup](https://docs.fabricmc.net/develop/data-generation/setup).
- [Fabric Loom issues](https://github.com/FabricMC/fabric-loom/issues) and [release notes](https://github.com/FabricMC/fabric-loom/releases), with issue-specific links above. See [Fabric API issues](https://github.com/FabricMC/fabric-api/issues) for game API examples.
- [Minecraft 26.1](https://fabricmc.net/2026/03/14/261.html), [26.2](https://fabricmc.net/2026/06/15/262.html) and [26.3](https://fabricmc.net/2026/09/15/263.html) Fabric release notes. Use exact-version documentation when porting older mods.
- [Gradle configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html), [Maven Publish](https://docs.gradle.org/current/userguide/publishing_maven.html), [Gradle Module Metadata](https://docs.gradle.org/current/userguide/publishing_gradle_module_metadata.html) and [Kotlin Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html).
- [Modrinth Create Version API](https://docs.modrinth.com/api/operations/createversion/), [Modrinth API overview](https://docs.modrinth.com/api/), [CurseForge Upload API](https://support.curseforge.com/support/solutions/articles/9000197321) and [CurseForge project submission guidance](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project).
- [Existing Fabric Amper's README](README.md), [specification](specs.md), [QA history](QA_REPORT.md) and [YAML schema v1](fabric-amper.schema.json). **The implemented scope wins over an aspirational proposal in this file.**

**Bottom line:** The biggest opportunity is **one reliable and explainable workflow from clean setup → valid metadata → real game tests → safe release preview**, with fewer hand-maintained configuration files. Fabric Amper already removes useful boilerplate. Future growth should emphasize *correctness, diagnostics, migration guidance, side-safe testing and controlled publishing* over pretending to replace Fabric Loom, the Minecraft API or release-platform policies.
