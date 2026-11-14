package dev.fabricamper.generator

import dev.fabricamper.config.EntrypointSpec
import dev.fabricamper.config.MixinSpec
import dev.fabricamper.config.ModYamlModel
import java.nio.charset.StandardCharsets

/**
 * The single metadata mapping used by Loom's configuration-time snapshot and
 * the task output shipped at the root of the final mod JAR.
 *
 * Gradle-only values (group, archivesName, repositories and dependencies) are
 * intentionally excluded from the Fabric Loader v1 document.
 */
object MetadataMapper {
    @JvmStatic
    fun toJson(model: ModYamlModel): String {
        ValidateFabricAmper.validate(model)
        val mod = model.mod
        val fields = linkedMapOf<String, Any?>(
            "schemaVersion" to 1,
            "id" to mod.id,
            "version" to mod.version,
        )

        mod.name?.let { fields["name"] = it }
        mod.description?.let { fields["description"] = it }
        fields["environment"] = mod.environment
        mod.license?.let { fields["license"] = it }
        mod.icon?.let { fields["icon"] = it }
        if (mod.authors.isNotEmpty()) fields["authors"] = mod.authors
        if (mod.contributors.isNotEmpty()) fields["contributors"] = mod.contributors
        if (mod.contact.isNotEmpty()) fields["contact"] = mod.contact
        if (mod.provides.isNotEmpty()) fields["provides"] = mod.provides

        if (mod.entrypoints.isNotEmpty()) {
            fields["entrypoints"] = mod.entrypoints.mapValues { (_, values) ->
                values.map(::mapEntrypoint)
            }
        }
        if (mod.languageAdapters.isNotEmpty()) fields["languageAdapters"] = mod.languageAdapters
        if (mod.mixins.isNotEmpty()) fields["mixins"] = mod.mixins.map(::mapMixin)
        mod.accessWidener?.let { fields["accessWidener"] = it }

        fields["depends"] = resolvedDepends(model)
        if (mod.recommends.isNotEmpty()) fields["recommends"] = mod.recommends
        if (mod.suggests.isNotEmpty()) fields["suggests"] = mod.suggests
        if (mod.conflicts.isNotEmpty()) fields["conflicts"] = mod.conflicts
        if (mod.breaks.isNotEmpty()) fields["breaks"] = mod.breaks
        if (mod.custom.isNotEmpty()) fields["custom"] = mod.custom

        return DeterministicJson.encodeRoot(fields)
    }

    @JvmStatic
    fun toUtf8Bytes(model: ModYamlModel): ByteArray =
        toJson(model).toByteArray(StandardCharsets.UTF_8)

    /** Values from mod.depends always win; a missing key gets a profile default. */
    @JvmStatic
    fun resolvedDepends(model: ModYamlModel): Map<String, Any> {
        val result = linkedMapOf<String, Any>()
        result.putAll(model.mod.depends)
        for ((id, requirement) in generatedRequirements(model)) {
            result.putIfAbsent(id, requirement)
        }
        return result
    }

    /** Exact user/generated origin retained by the config decoder. */
    @JvmStatic
    fun dependencyOrigins(model: ModYamlModel): Map<String, String> =
        dependencyOrigins(model, model.authoredDependsKeys)

    /** Exact provenance when a strict YAML decoder supplies the authored keys. */
    @JvmStatic
    fun dependencyOrigins(model: ModYamlModel, authoredKeys: Set<String>): Map<String, String> =
        resolvedDepends(model).keys.sorted().associateWith { id ->
            if (id in authoredKeys) "mod.depends (user)" else "profile (generated)"
        }

    private fun generatedRequirements(model: ModYamlModel): Map<String, String> = buildMap {
        put("minecraft", model.minecraft.version)
        put("fabricloader", ">=${model.minecraft.loader}")
        put("java", ">=${model.settings.java.release}")
        model.minecraft.fabricApi?.let { put("fabric-api", ">=${it.substringBefore('+')}") }
        if (model.settings.kotlin.enabled) {
            put("fabric-language-kotlin", ">=${model.settings.kotlin.fabricLanguageKotlin.substringBefore('+')}")
        }
    }

    private fun mapEntrypoint(entrypoint: EntrypointSpec): Any {
        if (!entrypoint.objectForm && entrypoint.adapter == null) return entrypoint.value
        return linkedMapOf<String, String>().apply {
            entrypoint.adapter?.let { put("adapter", it) }
            put("value", entrypoint.value)
        }
    }

    private fun mapMixin(mixin: MixinSpec): Any {
        if (!mixin.objectForm && mixin.environment == null) return mixin.config
        return linkedMapOf<String, String>().apply {
            put("config", mixin.config)
            mixin.environment?.let { put("environment", it) }
        }
    }
}
