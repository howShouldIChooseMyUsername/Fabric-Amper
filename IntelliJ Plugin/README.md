# Fabric Amper IntelliJ Support

An optional IntelliJ IDEA 2026.3 (263.x) plugin for Fabric Amper projects.
Once a project is linked to Gradle, editing its root `mod.yaml` or `mod.yml`
is treated as a Gradle settings-file change. The IDE handles its own reload
notification and sync policy; no generated Gradle scripts, polling, or
automatic reload requests are introduced.

## Install

1. Open IntelliJ IDEA 2026.3 → Settings → Plugins.
2. Open the gear menu → Install Plugin from Disk… and select
   `dist/fabric-amper-intellij-0.1.0.zip`. Restart the IDE if prompted.
3. Open/link a Fabric Amper Gradle project (its root `settings.gradle.kts`
   explicitly applies `id("dev.fabric-amper.settings")`).
4. Perform an initial Gradle sync so the IDE records its settings files.
5. Edit the root `mod.yaml` or `mod.yml`. With Gradle auto-reload set to
   external changes only (or manual), the IDE should offer its usual reload
   action. With automatic reload enabled for any changes, it may sync directly.

The plugin does not watch unrelated Gradle projects or nested YAML files.
Both aliases are tracked; Fabric Amper itself still requires exactly one
of the two names. Existing schema 1 consumers are also recognized because
the collector does not parse YAML.

## Build

Use JDK 25 and Gradle 9.x. This independent Java Gradle project compiles
against the actual locally installed IntelliJ SDK, not downloaded IDE jars.

```powershell
gradle -p "IntelliJ Plugin" buildPlugin --offline
# If IDEA is installed in a different directory:
gradle -p "IntelliJ Plugin" buildPlugin --offline "-PideaHome=C:/path/IntelliJ IDEA 2026.3"
```

The archive is created in `dist/`. You may set the `IDEA_HOME` environment
variable instead of `-PideaHome`. No SDK dependencies are included in the ZIP.

## API and compatibility

The shipped Gradle integration declares the extension point
`org.jetbrains.plugins.gradle.autoReloadSettingsCollector`, whose interface
is `org.jetbrains.plugins.gradle.service.project.GradleAutoReloadSettingsCollector`.
The actual 263 SDK's Gradle plugin ID is `com.intellij.gradle`.
The API returns filesystem paths for the existing Gradle auto-reload
tracker. It is an **internal** API, not a stable public contract. Compatibility
is limited to build family 263 and future IntelliJ versions require rechecking.

References:
- [JetBrains extension point list](https://plugins.jetbrains.com/docs/intellij/intellij-community-plugins-extension-point-list.html)
- [Plugin configuration](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html)
- [Gradle reload settings](https://www.jetbrains.com/help/idea/work-with-gradle-projects.html)

`collectorTest` executes the collector against the installed 263 SDK and
checks root scoping, both filename aliases, and extension registration.
A real IDE GUI smoke test is separate: after installation, modify a field
in the YAML, verify the native Gradle reload button appears (depending on
the IDE sync policy), click it, and confirm the project updates. Do not
interpret a successful SDK test as a completed GUI test.
