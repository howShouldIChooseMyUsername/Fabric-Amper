package dev.fabricamper.loom

import org.gradle.api.GradleException

/** Selects the Loom plugin from the actual Minecraft naming generation. */
object LoomPluginSelector {
    const val NON_REMAPPING_ID = "net.fabricmc.fabric-loom"
    const val REMAPPING_ID = "net.fabricmc.fabric-loom-remap"

    fun isNonRemapping(minecraftVersion: String): Boolean {
        val parts = minecraftVersion.trim().split('.')
        if (parts.size < 2 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) {
            throw GradleException("FabricAmper FA003 minecraft.version: unsupported Minecraft version '$minecraftVersion'")
        }
        val major = parts[0].toIntOrNull()
            ?: throw GradleException("FabricAmper FA003 minecraft.version: invalid major version")
        val minor = parts[1].toIntOrNull()
            ?: throw GradleException("FabricAmper FA003 minecraft.version: invalid minor version")
        return when {
            major >= 26 && (major > 26 || minor >= 1) -> true
            major == 1 -> false
            else -> throw GradleException("FabricAmper FA003 minecraft.version: unsupported Minecraft version '$minecraftVersion'")
        }
    }

    fun pluginId(minecraftVersion: String): String =
        if (isNonRemapping(minecraftVersion)) NON_REMAPPING_ID else REMAPPING_ID

    fun checkMappings(minecraftVersion: String, mappingType: String) {
        if (isNonRemapping(minecraftVersion) && mappingType != "mojang") {
            throw GradleException("FabricAmper FA004 minecraft.mappings: Minecraft $minecraftVersion uses official names; Yarn mappings are unsupported")
        }
    }
}
