package dev.fabricamper.plugin

import dev.fabricamper.config.MinecraftProfile
import dev.fabricamper.config.ModYamlModel
import dev.fabricamper.loom.LoomPluginSelector
import java.nio.file.Path

/**
 * A deliberately pure diagnostic snapshot for a separately registered Gradle task.
 * All environment facts are supplied by the caller. No filesystem inspection,
 * dependency resolution, network calls, or global cache changes occur here.
 *
 * The report never prints the project path, credentials, publishing destinations,
 * contact information, or arbitrary configuration values.
 */
object FabricAmperDoctorReport {
    private const val SHA256_HEX_LENGTH = 64
    private const val WINDOWS_PATH_WARNING_LENGTH = 240
    private val safeToolVersion = Regex("^[0-9]+(?:\\.[0-9]+)*(?:_[0-9]+)?(?:[-+][A-Za-z0-9._-]+)?$")
    private val minecraftSuffix = Regex("^[0-9]+\\.[0-9]+(?:\\.[0-9]+)?$")

    /**
     * Renders a deterministic, human-readable local report. `loomVersion` is null
     * when the calling plugin cannot identify its actual bundled Loom version.
     * `projectRoot` should be the absolute consumer root; its text is never logged.
     */
    @JvmStatic
    fun render(
        model: ModYamlModel,
        gradleVersion: String,
        jvmVersion: String,
        projectRoot: Path,
        loomPluginId: String,
        loomVersion: String?,
        osName: String,
    ): String {
        val minecraft = model.minecraft
        val javaTarget = model.settings.java.release
        val modern = minecraft.profile == MinecraftProfile.NON_REMAPPING
        val expectedLoom = if (modern) LoomPluginSelector.NON_REMAPPING_ID else LoomPluginSelector.REMAPPING_ID
        val safeGradle = safeVersion(gradleVersion)
        val safeLoom = safeVersion(loomVersion)
        val safeJvm = safeVersion(jvmVersion)
        val jvmFeature = javaFeature(jvmVersion)
        val actualLoom = when (loomPluginId) {
            LoomPluginSelector.NON_REMAPPING_ID, LoomPluginSelector.REMAPPING_ID -> loomPluginId
            else -> "unrecognized"
        }

        val lines = mutableListOf(
            "Fabric Amper doctor — offline, read-only configuration snapshot",
            "Minecraft: ${minecraft.version} (profile: ${if (modern) "non-remapping" else "remapping"}; mappings: ${minecraft.mappings.type})",
            "Declared Fabric Loader: ${minecraft.loader}; declared Fabric API: ${minecraft.fabricApi ?: "not configured"}",
            "Gradle: $safeGradle; running JVM: $safeJvm; Java target: $javaTarget",
            "Selected Loom plugin: $actualLoom; Loom version: $safeLoom",
            "Kotlin: ${if (model.settings.kotlin.enabled) "enabled" else "disabled"}; client source sets: ${if (model.settings.loom.splitEnvironmentSourceSets) "split" else "combined"}",
        )

        if (loomPluginId != expectedLoom) {
            lines += "[MISMATCH] Loom plugin flavor: Minecraft ${minecraft.version} requires $expectedLoom; check the actual applied plugin."
        } else {
            lines += "[CHECK] Loom plugin flavor agrees with the selected Minecraft naming profile."
        }

        if (modern && minecraft.mappings.type != "mojang") {
            lines += "[MISMATCH] minecraft.mappings: Minecraft 26.1+ requires Mojang official names, not Yarn (FA004)."
        } else if (!modern && minecraft.mappings.type == "yarn" && minecraft.mappings.version.isNullOrBlank()) {
            lines += "[MISMATCH] minecraft.mappings.version: Yarn mappings require an explicit version (FA003)."
        }

        if (modern && javaTarget != 25) {
            lines += "[MISMATCH] settings.java.release: Minecraft 26.1+ requires Java target 25 (FA005)."
        }
        when {
            jvmFeature == null -> lines += "[UNKNOWN] Running JVM feature cannot be parsed; verify the JVM Gradle uses and settings.java.release."
            jvmFeature < javaTarget -> lines += "[MISMATCH] Running JVM feature $jvmFeature is older than configured Java target $javaTarget; select a matching Gradle JVM/toolchain."
            modern && jvmFeature < 25 -> lines += "[MISMATCH] Minecraft 26.1+ requires Gradle itself to run on Java 25 or newer (FA005)."
            else -> lines += "[CHECK] Running JVM feature $jvmFeature meets the configured Java target ($javaTarget)."
        }

        val apiSuffix = minecraft.fabricApi?.substringAfter('+', "")
        if (!apiSuffix.isNullOrEmpty() && minecraftSuffix.matches(apiSuffix) && apiSuffix != minecraft.version) {
            lines += "[MISMATCH] minecraft.fabricApi: its Minecraft-version suffix differs from minecraft.version; confirm the published API artifact."
        }

        if (model.settings.kotlin.enabled) {
            val kotlin = model.settings.kotlin
            if (kotlin.pluginVersion != "2.4.21" || kotlin.languageVersion != "2.4" ||
                kotlin.fabricLanguageKotlin != "1.14.2+kotlin.2.4.21"
            ) {
                lines += "[MISMATCH] settings.kotlin: selected Kotlin Gradle plugin, language and Fabric Language Kotlin versions must match the bundled pinned set (FA006)."
            } else {
                lines += "[CHECK] Kotlin versions match the currently pinned Amper configuration; Gradle/KGP interoperability was not probed."
            }
        } else {
            lines += "[INFO] Kotlin compilation is disabled; the current Amper plugin classpath may still download Kotlin Gradle plugin artifacts on a cold cache."
        }

        lines += compatibilityEvidence(
            gradleVersion = gradleVersion,
            loomVersion = loomVersion,
            minecraftVersion = minecraft.version,
            javaTarget = javaTarget,
            correctLoomFlavor = loomPluginId == expectedLoom,
        )
        lines += pathAssessment(projectRoot, osName)
        lines += "[UNKNOWN] Dependency availability, IDE GUI sync, game runtime, and third-party mod compatibility were not checked."
        lines += "No changes or cache cleanup were performed."
        return lines.joinToString("\n")
    }

    private fun compatibilityEvidence(
        gradleVersion: String,
        loomVersion: String?,
        minecraftVersion: String,
        javaTarget: Int,
        correctLoomFlavor: Boolean,
    ): String {
        // G25: a real published Loom 1.19.4 API variant was rejected by Gradle 9.7.0.
        if (gradleVersion == "9.7.0" && loomVersion == "1.19.4") {
            return "[MISMATCH] Gradle 9.7.0 + published Loom 1.19.4 failed the recorded G25 API-variant resolution check; choose a verified combination."
        }
        val verifiedConsumerPairs = setOf(
            "9.7.0" to "1.18.4",
            "9.7.1" to "1.17.21",
            "9.9.1" to "1.19.4",
        )
        if (correctLoomFlavor && minecraftVersion == "26.3" && javaTarget == 25 &&
            loomVersion != null && (gradleVersion to loomVersion) in verifiedConsumerPairs
        ) {
            return "[CHECKED] This Gradle/Loom pairing has recorded Minecraft 26.3/Java 25 consumer build evidence; it does not certify this project, IDE import, or runtime."
        }
        if (gradleVersion == "9.8.0" && loomVersion == "1.19.4") {
            return "[PARTIAL] Gradle 9.8.0 + Loom 1.19.4 passed a recorded API-variant resolution check only; full consumer compatibility is UNKNOWN."
        }
        return "[UNKNOWN] No exact consumer build verification is recorded for this Gradle/Loom/Minecraft/Java profile combination."
    }

    private fun pathAssessment(projectRoot: Path, osName: String): String {
        if (!osName.startsWith("windows", ignoreCase = true)) {
            return "[INFO] Windows-specific path length risk: not applicable to the supplied operating system."
        }
        if (!projectRoot.isAbsolute) {
            return "[UNKNOWN] Windows project root is relative; supply an absolute root for the path-length estimate."
        }
        val rootLength = projectRoot.normalize().toString().trimEnd('/', '\\').length
        // LoomMetadataMirror uses exactly a 64-character SHA-256 directory name.
        // This estimates only Amper's metadata mirror; Loom's separate cache
        // layout and third-party tool paths are not inspected or guessed.
        val estimatedMirrorLength = rootLength + "/.gradle/fabric-amper/loom/".length +
            SHA256_HEX_LENGTH + "/fabric.mod.json".length
        return if (estimatedMirrorLength >= WINDOWS_PATH_WARNING_LENGTH) {
            "[WARN] Windows path-length risk: estimated Amper metadata mirror is $estimatedMirrorLength characters (root: $rootLength); consider a shorter project-local path. Loom cache paths were not measured; do not clear global caches."
        } else {
            "[CHECK] Estimated Windows Amper metadata mirror is $estimatedMirrorLength characters (root: $rootLength); Loom cache and IDE path lengths remain unchecked."
        }
    }

    private fun safeVersion(value: String?): String =
        value?.takeIf { safeToolVersion.matches(it) } ?: "unknown"

    private fun javaFeature(version: String): Int? {
        val major = version.substringBefore('.').substringBefore('-').substringBefore('+')
        return if (major == "1") version.substringAfter("1.").substringBefore('.').toIntOrNull()
        else major.toIntOrNull()
    }
}
