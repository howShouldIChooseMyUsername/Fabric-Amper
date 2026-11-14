package dev.fabricamper.plugin

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.fabricamper.config.ModYamlModel

/** Pure metadata preparation; sensitive tokens and changelog file bytes are absent. */
internal object FabricAmperPublishMetadata {
    fun modrinth(model: ModYamlModel): String {
        val selected = requireNotNull(model.publish?.modrinth)
        val metadata = JsonObject().apply {
            addProperty("project_id", selected.projectId)
            add("file_parts", strings(listOf("file")))
            addProperty("primary_file", "file")
            addProperty("name", selected.versionName ?: defaultDisplayName(model))
            addProperty("version_number", selected.versionNumber ?: model.mod.version)
            addProperty("version_type", selected.releaseType)
            add("game_versions", strings(selected.gameVersions ?: listOf(model.minecraft.version)))
            add("loaders", strings(selected.loaders ?: listOf("fabric")))
            addProperty("featured", false)
            addProperty("environment", selected.environment ?: when (model.mod.environment) {
                "client" -> "client_only"
                "server" -> "server_only"
                else -> "client_and_server"
            })
            add("dependencies", JsonArray().also { array ->
                selected.dependencies.forEach { relation ->
                    array.add(JsonObject().apply {
                        addProperty("project_id", relation.projectId)
                        addProperty("dependency_type", relation.type)
                    })
                }
            })
        }
        return metadata.toString()
    }

    fun curseforge(model: ModYamlModel): String {
        val selected = requireNotNull(model.publish?.curseforge)
        val names = selected.gameVersionNames ?: listOfNotNull(
            model.minecraft.version,
            "Fabric",
            when (model.mod.environment) { "server" -> null; else -> "Client" },
            when (model.mod.environment) { "client" -> null; else -> "Server" },
        )
        val metadata = JsonObject().apply {
            addProperty("releaseType", selected.releaseType)
            addProperty("changelogType", selected.changelogType)
            addProperty("displayName", selected.displayName ?: defaultDisplayName(model))
            add("gameVersionNames", strings(names.distinct()))
            selected.gameVersions?.let { ids ->
                add("gameVersions", JsonArray().also { array -> ids.forEach { value -> array.add(value) } })
            }
            addProperty("isMarkedForManualRelease", selected.manualRelease)
            if (selected.relations.isNotEmpty()) {
                add("relations", JsonObject().also { relations ->
                    relations.add("projects", JsonArray().also { array ->
                        selected.relations.forEach { relation ->
                            array.add(JsonObject().apply {
                                addProperty("slug", relation.slug)
                                relation.projectId?.let { addProperty("projectID", it) }
                                addProperty("type", relation.type)
                            })
                        }
                    })
                })
            }
        }
        return metadata.toString()
    }

    private fun defaultDisplayName(model: ModYamlModel): String =
        "${model.mod.name ?: model.mod.id} ${model.mod.version}"

    private fun strings(items: List<String>): JsonArray = JsonArray().also { result ->
        items.forEach(result::add)
    }
}
