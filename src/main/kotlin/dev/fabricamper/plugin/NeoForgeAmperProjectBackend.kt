package dev.fabricamper.plugin

import dev.fabricamper.config.ModYamlModel
import dev.fabricamper.generator.GenerateNeoForgeModsToml
import dev.fabricamper.generator.NeoForgeMetadataMapper
import dev.fabricamper.generator.VerifyNeoForgeModJar
import dev.fabricamper.loom.FabricAmperPublishingConfigurer
import dev.fabricamper.loom.FabricAmperRemoteMavenRepository
import dev.fabricamper.loom.NeoForgeConfigurer
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFile
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

/** A separate, Loom-free ModDevGradle backend for a single selected project. */
internal object NeoForgeAmperProjectBackend {
    fun configure(project: Project, model: ModYamlModel, yamlInput: RegularFile) {
        if (model.targets.none { it.loader == "neoforge" }) {
            throw GradleException("FabricAmper FA016 product: NeoForge target was not selected")
        }
        NeoForgeMetadataMapper.validateNoAuthoredToml(model, project.projectDir.toPath())
        NeoForgeMetadataMapper.validateResources(model, project.projectDir.toPath())
        for (path in listOf("src/main/resources/fabric.mod.json", "src/client/resources/fabric.mod.json")) {
            if (project.layout.projectDirectory.file(path).asFile.exists()) {
                throw GradleException("FabricAmper FA008 $path: a NeoForge target cannot package Fabric Loader metadata")
            }
        }

        NeoForgeConfigurer.configure(project, model)
        val generate = project.tasks.register("generateNeoForgeModsToml", GenerateNeoForgeModsToml::class.java) { task ->
            task.group = "build"
            task.description = "Generate META-INF/neoforge.mods.toml from the single root YAML"
            task.yamlFile.set(yamlInput)
            task.targetId.set("neoforge")
            task.outputFile.set(
                project.layout.buildDirectory.file("generated/fabric-amper/resources/main/META-INF/neoforge.mods.toml"),
            )
        }
        project.tasks.named("processResources", ProcessResources::class.java).configure { task ->
            task.from(generate) { copy -> copy.into("META-INF") }
            task.duplicatesStrategy = DuplicatesStrategy.FAIL
        }
        // The decoder/validator has already consumed a tracked YAML input.
        // Validate again via the actual generator when invoked, without
        // introducing a Fabric-only ValidateFabricAmper task into MDG.
        val validate = project.tasks.register("validateFabricAmper") { task ->
            task.group = "verification"
            task.description = "Validate NeoForge metadata and referenced resources without running the game"
            task.dependsOn(generate)
        }
        val jar = project.tasks.named("jar", Jar::class.java)
        val verify = project.tasks.register("verifyFabricAmperJar", VerifyNeoForgeModJar::class.java) { task ->
            task.group = "verification"
            task.description = "Verify exactly one NeoForge TOML and no Fabric JSON in the finished JAR"
            task.jarFile.set(jar.flatMap { it.archiveFile })
            task.yamlFile.set(yamlInput)
        }
        jar.configure { it.finalizedBy(verify) }
        project.tasks.named("check").configure { it.dependsOn(validate, verify) }

        val remote = when {
            model.publish?.maven != null -> model.publish.maven.let { selected ->
                FabricAmperRemoteMavenRepository(
                    selected.url, selected.credentialsFromEnv?.getOrNull(0), selected.credentialsFromEnv?.getOrNull(1),
                )
            }
            model.publishing?.repository != null -> model.publishing.repository.let { selected ->
                FabricAmperRemoteMavenRepository(selected.url, selected.usernameEnv, selected.passwordEnv)
            }
            else -> null
        }
        val api = model.targets.any { it.loader == "neoforge" && it.kind == "api" }
        if (api || remote != null) {
            val group = model.mod.group
                ?: throw GradleException("FabricAmper FA003 mod.group: NeoForge API/Maven publication requires a group")
            val licenses = when (val license = model.mod.license) {
                is String -> listOf(license)
                is List<*> -> license.filterIsInstance<String>()
                else -> emptyList()
            }
            FabricAmperPublishingConfigurer.configure(
                project = project,
                groupId = group,
                artifactId = (model.mod.archivesName ?: model.mod.id) + if (model.isMultiLoader) "-neoforge" else "",
                version = model.mod.version,
                displayName = model.mod.name,
                description = model.mod.description,
                licenses = licenses,
                homepage = model.mod.contact["homepage"],
                remote = remote,
            )
            project.tasks.withType(PublishToMavenRepository::class.java).configureEach { it.dependsOn(verify) }
            project.tasks.withType(PublishToMavenLocal::class.java).configureEach { it.dependsOn(verify) }
        }
        val mavenTarget = if (remote != null) project.tasks.register("publishFabricAmperMaven") { task ->
            task.group = "publishing"
            task.dependsOn("publishMavenJavaPublicationToFabricAmperRemoteRepository")
        } else null
        val modrinthTarget = model.publish?.modrinth?.let { selected ->
            project.tasks.register("publishFabricAmperModrinth", FabricAmperPlatformUploadTask::class.java) { task ->
                task.group = "publishing"
                if (model.isMultiLoader) task.doFirst {
                    throw GradleException("FabricAmper FA003 publish.modrinth: multi-loader releases need one validated multi-file publication; refusing separate NeoForge upload")
                }
                task.platform.set("modrinth")
                task.consumerDirectory.set(project.layout.projectDirectory)
                task.metadataJson.set(FabricAmperPublishMetadata.modrinth(model, "neoforge"))
                task.defaultChangelog.set("Release ${model.mod.version}")
                selected.changelog?.let { task.changelog.set(it) }
                selected.changelogFile?.let { task.changelogFile.set(project.layout.projectDirectory.file(it)) }
                task.archiveFile.set(jar.flatMap { it.archiveFile })
                task.token.set(project.providers.environmentVariable(selected.tokenEnv))
                task.dependsOn(verify)
            }
        }
        val curseForgeTarget = model.publish?.curseforge?.let { selected ->
            project.tasks.register("publishFabricAmperCurseForge", FabricAmperPlatformUploadTask::class.java) { task ->
                task.group = "publishing"
                if (model.isMultiLoader) task.doFirst {
                    throw GradleException("FabricAmper FA003 publish.curseforge: multi-loader release relationships must be explicitly validated; refusing separate NeoForge upload")
                }
                task.platform.set("curseforge")
                task.consumerDirectory.set(project.layout.projectDirectory)
                task.curseForgeProjectId.set(selected.projectId)
                task.metadataJson.set(FabricAmperPublishMetadata.curseforge(model, "neoforge"))
                task.defaultChangelog.set("Release ${model.mod.version}")
                selected.changelog?.let { task.changelog.set(it) }
                selected.changelogFile?.let { task.changelogFile.set(project.layout.projectDirectory.file(it)) }
                task.archiveFile.set(jar.flatMap { it.archiveFile })
                task.token.set(project.providers.environmentVariable(selected.tokenEnv))
                task.dependsOn(verify)
            }
        }
        if (model.publish != null || remote != null) {
            val group = project.tasks.register("publishFabricAmper") { task ->
                task.group = "publishing"
                task.description = "Publish only explicitly selected NeoForge destinations"
                listOfNotNull(mavenTarget, modrinthTarget, curseForgeTarget).forEach(task::dependsOn)
            }
            if (model.publish != null) {
                if (api || remote != null) {
                    project.tasks.named("publish").configure { it.dependsOn(group) }
                } else {
                    project.tasks.register("publish") { it.group = "publishing"; it.dependsOn(group) }
                }
            }
        }
    }
}
