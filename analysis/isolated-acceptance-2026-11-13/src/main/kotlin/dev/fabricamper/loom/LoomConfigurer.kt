package dev.fabricamper.loom

import net.fabricmc.loom.LoomGradleExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

/** Wires the project metadata mirror before Loom evaluates local mod metadata. */
object LoomConfigurer {
    private const val DISABLE_OBFUSCATION_PROPERTY = "fabric.loom.disableObfuscation"

    fun configure(
        project: Project,
        minecraftVersion: String,
        modId: String,
        mirrorFile: File,
        splitEnvironmentSourceSets: Boolean,
        mappingsType: String = "mojang",
        yarnVersion: String? = null,
        accessWidener: String? = null,
    ) {
        LoomPluginSelector.checkMappings(minecraftVersion, mappingsType)
        val pluginId = LoomPluginSelector.pluginId(minecraftVersion)
        val nonRemapping = LoomPluginSelector.isNonRemapping(minecraftVersion)
        val preexistingLoomId = listOf(
            LoomPluginSelector.NON_REMAPPING_ID,
            LoomPluginSelector.REMAPPING_ID,
        ).firstOrNull(project.pluginManager::hasPlugin)
        if (preexistingLoomId != null || project.extensions.findByName("loom") != null) {
            throw GradleException(
                "FabricAmper FA010 Loom plugin conflict: an existing Loom installation " +
                    "('${preexistingLoomId ?: "loom extension"}') was detected before Fabric Amper applied '$pluginId'. " +
                    "Only the bundled Loom version/profile is supported",
            )
        }
        if (nonRemapping) {
            // Loom 1.17.x may construct its extension from the nested
            // 'fabric-loom' application before Gradle marks the outer
            // 'net.fabricmc.fabric-loom' marker as applied. Its fallback only
            // recognizes the literal String "true" project property.
            val existing = project.findProperty(DISABLE_OBFUSCATION_PROPERTY)
            when {
                existing == null -> project.extensions.extraProperties.set(DISABLE_OBFUSCATION_PROPERTY, "true")
                existing is String && existing.equals("true", ignoreCase = true) -> Unit
                else -> throw GradleException(
                    "FabricAmper FA010 $DISABLE_OBFUSCATION_PROPERTY: Minecraft $minecraftVersion " +
                        "requires the String value 'true' for non-remapping Loom; existing value '$existing' conflicts",
                )
            }
        } else {
            // The remapping plugin also reaches Loom's shared implementation.
            // An inherited property left over from a non-remapping build
            // would disable its mapping configurations despite the remap ID.
            val existing = project.findProperty(DISABLE_OBFUSCATION_PROPERTY)
            if (existing is String && existing.equals("true", ignoreCase = true)) {
                throw GradleException(
                    "FabricAmper FA010 $DISABLE_OBFUSCATION_PROPERTY: Minecraft $minecraftVersion " +
                        "uses remapping Loom; the existing value '$existing' would disable its mappings",
                )
            }
        }
        try {
            project.pluginManager.apply(pluginId)
        } catch (e: Exception) {
            throw GradleException(
                "FabricAmper FA010 Loom plugin '$pluginId' could not be applied. " +
                    "Check Fabric Amper's transitive plugin classpath and plugin repositories.", e,
            )
        }

        val loom = project.extensions.getByType(LoomGradleExtensionAPI::class.java)
        val internalLoom = loom as? LoomGradleExtension
            ?: throw GradleException("FabricAmper FA010 Loom extension cannot expose its remapping mode")
        if (internalLoom.disableObfuscation() != nonRemapping) {
            if (nonRemapping) {
                throw GradleException(
                    "FabricAmper FA010 Loom non-remapping mode was not enabled for Minecraft $minecraftVersion " +
                        "despite $DISABLE_OBFUSCATION_PROPERTY=true; check incompatible Loom/project-isolation settings",
                )
            }
            throw GradleException(
                "FabricAmper FA010 Loom remapping mode was disabled for Minecraft $minecraftVersion; " +
                    "check conflicting $DISABLE_OBFUSCATION_PROPERTY or plugin classpath settings",
            )
        }
        // The extension does not exist until Loom has been applied, but the mirror
        // must already exist before Loom's after-evaluation metadata lookup.
        loom.fabricModJsonPath.fileValue(mirrorFile)
        if (splitEnvironmentSourceSets) {
            loom.splitEnvironmentSourceSets()
        }
        val modSettings = loom.mods.maybeCreate(modId)
        modSettings.sourceSet("main")
        if (splitEnvironmentSourceSets) {
            modSettings.sourceSet("client")
        }

        if (accessWidener != null) {
            val source = project.layout.projectDirectory.file("src/main/resources/$accessWidener")
            if (!source.asFile.isFile) {
                throw GradleException("FabricAmper FA009 mod.accessWidener: resource '$accessWidener' does not exist")
            }
            loom.accessWidenerPath.set(source)
        }

        if (!LoomPluginSelector.isNonRemapping(minecraftVersion)) {
            val mappings = when (mappingsType) {
                "mojang" -> loom.officialMojangMappings()
                "yarn" -> {
                    val version = yarnVersion ?: throw GradleException("FabricAmper FA003 minecraft.mappings: Yarn version required")
                    "net.fabricmc:yarn:$version:v2"
                }
                else -> throw GradleException("FabricAmper FA003 minecraft.mappings: unknown mappings '$mappingsType'")
            }
            project.dependencies.add("mappings", mappings)
        }
    }
}
