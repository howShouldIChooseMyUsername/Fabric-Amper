package dev.fabricamper.plugin

import dev.fabricamper.config.DependencySpec
import dev.fabricamper.config.FabricAmperConfigException
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.config.ModYamlModel
import dev.fabricamper.config.RepositorySpec
import dev.fabricamper.config.StrictYamlReader
import dev.fabricamper.generator.GenerateFabricModJson
import dev.fabricamper.generator.MetadataMapper
import dev.fabricamper.generator.ValidateFabricAmper
import dev.fabricamper.generator.VerifyFabricAmperJar
import dev.fabricamper.loom.DependencyConfigurer
import dev.fabricamper.loom.FabricAmperPublishingConfigurer
import dev.fabricamper.loom.FabricAmperRemoteMavenRepository
import dev.fabricamper.loom.GradleDependencyDeclaration
import dev.fabricamper.loom.GradleRepositoryDeclaration
import dev.fabricamper.loom.JavaKotlinConfigurer
import dev.fabricamper.loom.LoomConfigurer
import dev.fabricamper.loom.LoomMetadataMirror
import dev.fabricamper.loom.LoomPluginSelector
import dev.fabricamper.loom.RepositoryConfigurer
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File
import javax.inject.Inject

/** Root project configuration, entirely derived from mod.yaml. */
abstract class FabricAmperProjectPlugin : Plugin<Project> {
    @get:Inject
    abstract val flowScope: FlowScope

    @get:Inject
    abstract val flowProviders: FlowProviders

    override fun apply(target: Project) {
        if (target.path != ":") return
        val selectedYaml = selectYaml(target)
        // Project plugin and Settings plugin must both record an actual file read.
        val yamlBytes = target.providers.fileContents(
            target.layout.projectDirectory.file(selectedYaml.name),
        ).asBytes.get()
        val config = ModYamlLoader.loadBytes(selectedYaml.toPath(), yamlBytes)
        registerMissingArtifactDiagnostic(selectedYaml, yamlBytes, config)
        if (LoomPluginSelector.isNonRemapping(config.minecraft.version) && Runtime.version().feature() < 25) {
            throw GradleException(
                "FabricAmper FA005 settings.java.release: Minecraft ${config.minecraft.version} requires " +
                    "Gradle itself to run on Java 25 or newer; current JVM is ${Runtime.version().feature()}",
            )
        }
        rejectExistingMetadata(target)
        // Validate real source resources and the classTweaker header before Loom
        // can read a configuration-time metadata snapshot.
        ValidateFabricAmper.validateSourceResources(config)

        val metadata = MetadataMapper.toUtf8Bytes(config)
        val mirror = LoomMetadataMirror.create(target, metadata, referencedResources(target, config))

        // Loom's own after-evaluation callbacks will observe Project.version.
        target.version = config.mod.version
        if (config.mod.group != null) target.group = config.mod.group
        val game = config.minecraft
        LoomConfigurer.configure(
            project = target,
            minecraftVersion = game.version,
            modId = config.mod.id,
            mirrorFile = mirror,
            splitEnvironmentSourceSets = config.settings.loom.splitEnvironmentSourceSets,
            mappingsType = game.mappings.type,
            yarnVersion = game.mappings.version,
            accessWidener = config.mod.accessWidener,
        )
        target.extensions.getByType(BasePluginExtension::class.java)
            .archivesName.set(config.mod.archivesName ?: config.mod.id)

        // `fabric/library` is normalized to `fabric/api`. A regular fabric/mod
        // opts into Maven support only by authoring publish.maven. The older
        // publishing.repository setting remains API/library-only.
        val explicitMaven = config.publish?.maven
        val legacyMaven = config.publishing?.repository
        val remoteMaven = when {
            explicitMaven != null -> FabricAmperRemoteMavenRepository(
                url = explicitMaven.url,
                usernameEnv = explicitMaven.credentialsFromEnv?.getOrNull(0),
                passwordEnv = explicitMaven.credentialsFromEnv?.getOrNull(1),
            )
            legacyMaven != null -> FabricAmperRemoteMavenRepository(
                url = legacyMaven.url,
                usernameEnv = legacyMaven.usernameEnv,
                passwordEnv = legacyMaven.passwordEnv,
            )
            else -> null
        }
        val hasMavenPublication = config.product == "fabric/api" || remoteMaven != null
        if (hasMavenPublication) {
            val groupId = config.mod.group
                ?: throw GradleException("FabricAmper FA003 mod.group: an API library needs a Maven group")
            val licenses = when (val value = config.mod.license) {
                is String -> listOf(value)
                is List<*> -> value.filterIsInstance<String>()
                else -> emptyList()
            }
            FabricAmperPublishingConfigurer.configure(
                project = target,
                groupId = groupId,
                artifactId = config.mod.archivesName ?: config.mod.id,
                version = config.mod.version,
                displayName = config.mod.name,
                description = config.mod.description,
                licenses = licenses,
                homepage = config.mod.contact["homepage"],
                remote = remoteMaven,
            )
        }

        RepositoryConfigurer.configure(target, config.repositories.map(::repositoryDeclaration))
        target.dependencies.add("minecraft", "com.mojang:minecraft:${game.version}")
        val remapping = !LoomPluginSelector.isNonRemapping(game.version)
        target.dependencies.add(
            if (remapping) "modImplementation" else "implementation",
            "net.fabricmc:fabric-loader:${game.loader}",
        )
        if (game.fabricApi != null) {
            target.dependencies.add(
                if (remapping) "modImplementation" else "implementation",
                "net.fabricmc.fabric-api:fabric-api:${game.fabricApi}",
            )
        }
        val java = config.settings.java
        val kotlin = config.settings.kotlin
        JavaKotlinConfigurer.configure(
            target, game.version, java.release, java.sourcesJar,
            kotlin.enabled, kotlin.pluginVersion, kotlin.languageVersion, kotlin.fabricLanguageKotlin,
        )
        DependencyConfigurer.configure(
            target,
            game.version,
            config.dependencies.map(::dependencyDeclaration),
            config.testDependencies.map(::dependencyDeclaration),
        )

        val generate = target.tasks.register("generateFabricModJson", GenerateFabricModJson::class.java) { task ->
            task.yamlFile.set(target.layout.projectDirectory.file(selectedYaml.name))
            task.outputFile.set(target.layout.buildDirectory.file("generated/fabric-amper/resources/main/fabric.mod.json"))
        }
        val validate = target.tasks.register("validateFabricAmper", ValidateFabricAmper::class.java) { task ->
            task.yamlFile.set(target.layout.projectDirectory.file(selectedYaml.name))
        }
        val finalArchiveTask = if (remapping) "remapJar" else "jar"
        val archive = target.tasks.named(finalArchiveTask, Jar::class.java)
        val verifyArchive = target.tasks.register("verifyFabricAmperJar", VerifyFabricAmperJar::class.java) { task ->
            task.jarFile.set(archive.flatMap { it.archiveFile })
        }
        if (hasMavenPublication) {
            // jar.finalizedBy(verify) alone does not order verification before
            // the publishing task. Enforce the built distribution's ZIP/Loader
            // checks BEFORE any local, remote, or explicitly requested ~/.m2
            // publication. Loom owns the remap outgoing variant selection.
            target.tasks.withType(PublishToMavenRepository::class.java).configureEach { task ->
                task.dependsOn(verifyArchive)
            }
            target.tasks.withType(PublishToMavenLocal::class.java).configureEach { task ->
                task.dependsOn(verifyArchive)
            }
        }
        // The upload tasks exist only for destinations explicitly authored in
        // publish, never as hidden side effects of `build` or `check`.
        val mavenTask = if (remoteMaven != null) {
            target.tasks.register("publishFabricAmperMaven") { task ->
                task.group = "publishing"
                task.description = "Publish the verified Fabric mod to the configured remote Maven repository"
                task.dependsOn("publishMavenJavaPublicationToFabricAmperRemoteRepository")
            }
        } else null

        val modrinthTask = config.publish?.modrinth?.let { selected ->
            target.tasks.register("publishFabricAmperModrinth", FabricAmperPlatformUploadTask::class.java) { task ->
                task.group = "publishing"
                task.description = "Upload the verified production JAR to Modrinth"
                task.platform.set("modrinth")
                task.consumerDirectory.set(target.layout.projectDirectory)
                task.metadataJson.set(FabricAmperPublishMetadata.modrinth(config))
                task.defaultChangelog.set("Release ${config.mod.version}")
                selected.changelog?.let { task.changelog.set(it) }
                selected.changelogFile?.let { task.changelogFile.set(target.layout.projectDirectory.file(it)) }
                task.archiveFile.set(archive.flatMap { it.archiveFile })
                task.token.set(target.providers.environmentVariable(selected.tokenEnv))
                task.dependsOn(verifyArchive)
            }
        }
        val curseForgeTask = config.publish?.curseforge?.let { selected ->
            target.tasks.register("publishFabricAmperCurseForge", FabricAmperPlatformUploadTask::class.java) { task ->
                task.group = "publishing"
                task.description = "Upload the verified production JAR to CurseForge"
                task.platform.set("curseforge")
                task.consumerDirectory.set(target.layout.projectDirectory)
                task.curseForgeProjectId.set(selected.projectId)
                task.metadataJson.set(FabricAmperPublishMetadata.curseforge(config))
                task.defaultChangelog.set("Release ${config.mod.version}")
                selected.changelog?.let { task.changelog.set(it) }
                selected.changelogFile?.let { task.changelogFile.set(target.layout.projectDirectory.file(it)) }
                task.archiveFile.set(archive.flatMap { it.archiveFile })
                task.token.set(target.providers.environmentVariable(selected.tokenEnv))
                task.dependsOn(verifyArchive)
            }
        }
        if (config.publish != null || remoteMaven != null) {
            val allTargets = target.tasks.register("publishFabricAmper") { task ->
                task.group = "publishing"
                task.description = "Publish to the explicitly configured Fabric Amper destinations"
                listOfNotNull(mavenTask, modrinthTask, curseForgeTask).forEach { dependency ->
                    task.dependsOn(dependency)
                }
            }
            if (config.publish != null) {
                if (hasMavenPublication) {
                    target.tasks.named("publish").configure { task -> task.dependsOn(allTargets) }
                } else {
                    target.tasks.register("publish") { task ->
                        task.group = "publishing"
                        task.dependsOn(allTargets)
                    }
                }
            }
        }
        target.tasks.named("check").configure { task ->
            task.dependsOn(validate, verifyArchive)
        }
        // Direct `jar`/`remapJar` invocations must validate their produced ZIP,
        // not only `build` (which already depends on `check`).
        archive.configure { task -> task.finalizedBy(verifyArchive) }
        target.tasks.named("processResources", ProcessResources::class.java).configure { task ->
            task.from(generate)
            task.duplicatesStrategy = DuplicatesStrategy.FAIL
        }
    }

    private fun registerMissingArtifactDiagnostic(selectedYaml: File, yamlBytes: ByteArray, config: ModYamlModel) {
        if (config.dependencies.isEmpty() && config.testDependencies.isEmpty()) return
        // The normalized DTO does not carry source marks. Use exactly the
        // already tracked YAML bytes to recover locations without guessing.
        val marks = StrictYamlReader.readDocumentBytes(yamlBytes, selectedYaml.toPath()).fieldMarks
        val declarations = buildList {
            listOf("dependencies" to config.dependencies, "test-dependencies" to config.testDependencies)
                .forEach { (section, dependencies) ->
                    dependencies.forEachIndexed { index, dependency ->
                        val path = "$section[$index]"
                        val mark = marks[path] ?: marks[section]
                        add("$path\t${dependency.coordinate}\t${mark?.line ?: "?"}\t${mark?.column ?: "?"}")
                    }
                }
        }
        flowScope.always(FabricAmperMissingArtifactDiagnostic::class.java) { spec ->
            spec.parameters.buildResult.set(flowProviders.buildWorkResult)
            spec.parameters.yamlFileName.set(selectedYaml.name)
            spec.parameters.declarations.set(declarations)
        }
    }

    private fun selectYaml(project: Project): File {
        val directory = project.layout.projectDirectory.asFile
        val yaml = directory.resolve("mod.yaml")
        val yml = directory.resolve("mod.yml")
        if (yaml.isFile == yml.isFile) {
            throw FabricAmperConfigException("FA001", "mod.yaml", "exactly one of mod.yaml or mod.yml must exist", directory.toPath())
        }
        return if (yaml.isFile) yaml else yml
    }

    private fun rejectExistingMetadata(project: Project) {
        listOf("src/main/resources/fabric.mod.json", "src/client/resources/fabric.mod.json").forEach { path ->
            if (project.layout.projectDirectory.file(path).asFile.exists()) {
                throw GradleException("FabricAmper FA008 $path: an existing fabric.mod.json would collide with generated metadata")
            }
        }
    }

    private fun referencedResources(project: Project, config: ModYamlModel): Map<String, File> {
        val references = linkedSetOf<String>()
        config.mod.mixins.forEach { references += it.config }
        config.mod.accessWidener?.let(references::add)
        when (val icon = config.mod.icon) {
            is String -> references += icon
            is Map<*, *> -> icon.values.filterIsInstance<String>().forEach(references::add)
        }
        val resources = linkedMapOf<String, File>()
        references.forEach { relative ->
            val mainPath = "src/main/resources/$relative"
            val clientPath = "src/client/resources/$relative"
            val main = project.layout.projectDirectory.file(mainPath).asFile
            val sourcePath = if (main.isFile) mainPath else clientPath
            val source = project.layout.projectDirectory.file(sourcePath)
            // The metadata mirror is regenerated during configuration, so
            // source resource bytes must also be configuration-cache inputs.
            project.providers.fileContents(source).asBytes.get()
            resources[relative] = source.asFile
        }
        return resources
    }

    private fun repositoryDeclaration(spec: RepositorySpec): GradleRepositoryDeclaration =
        if (spec.kind == "maven") {
            GradleRepositoryDeclaration.Maven(spec.url ?: throw GradleException("FabricAmper FA003 repositories: Maven URL is missing"))
        } else {
            GradleRepositoryDeclaration.Named(spec.kind)
        }

    private fun dependencyDeclaration(spec: DependencySpec) = GradleDependencyDeclaration(
        coordinate = spec.coordinate,
        kind = spec.kind,
        scope = spec.scope,
        target = spec.target,
        exported = spec.exported,
        include = spec.include,
    )
}
