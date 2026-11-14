package dev.fabricamper.config

import java.nio.file.Path

/** Explicit non-lexical discrimination of Minecraft version families. */
enum class MinecraftProfile {
    NON_REMAPPING,
    REMAPPING;

    companion object {
        fun forVersion(version: String): MinecraftProfile {
            val pieces = version.split('.')
            val major = pieces.firstOrNull()?.toIntOrNull()
                ?: throw FabricAmperConfigException("FA003", "minecraft.version", "unsupported Minecraft version '$version'")
            val minor = pieces.getOrNull(1)?.toIntOrNull()
                ?: throw FabricAmperConfigException("FA003", "minecraft.version", "unsupported Minecraft version '$version'")
            // 26.1+ is Mojang-official (unobfuscated). Earlier versions use remapping.
            return if (major > 26 || (major == 26 && minor >= 1)) NON_REMAPPING else REMAPPING
        }
    }
}

/** Includes a stable FA-code and logical YAML path for Gradle diagnostics. */
class FabricAmperConfigException(
    val code: String,
    val yamlPath: String,
    val reason: String,
    val sourceFile: Path? = null,
    val line: Int? = null,
    val column: Int? = null,
    cause: Throwable? = null,
) : IllegalArgumentException(
    "FabricAmper $code ${sourceFile?.fileName ?: "mod.yaml"}:${line ?: "?"}:${column ?: "?"} $yamlPath: $reason",
    cause,
)
