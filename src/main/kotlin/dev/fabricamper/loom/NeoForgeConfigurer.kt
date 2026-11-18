package dev.fabricamper.loom

import dev.fabricamper.config.ModYamlModel
import net.neoforged.moddevgradle.dsl.NeoForgeExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

/**
 * The MDG path never applies Loom, declares Fabric Loader, or adds `minecraft`
 * and `mappings` dependencies by hand. MDG provisions the correct NeoForge
 * development runtime and launch tasks directly from the Settings plugin's
 * transitive plugin classpath.
 */
object NeoForgeConfigurer {
    const val PLUGIN_ID = "net.neoforged.moddev"
    const val BUNDLED_VERSION = "2.0.148"

    fun configure(project: Project, model: ModYamlModel) {
        val neo = model.neoforge
            ?: throw GradleException("FabricAmper FA003 neoforge: a NeoForge product requires a neoforge block")
        if (model.minecraft.version != "26.3" || model.settings.java.release != 25) {
            throw GradleException("FabricAmper FA005 neoforge: the pinned NeoForge 26.3 backend requires Minecraft 26.3 and Java 25")
        }
        if (Runtime.version().feature() < 25) {
            throw GradleException("FabricAmper FA005 neoforge: Gradle must run on Java 25 or newer")
        }
        val conflict = listOf(LoomPluginSelector.NON_REMAPPING_ID, LoomPluginSelector.REMAPPING_ID)
            .firstOrNull(project.pluginManager::hasPlugin)
        if (conflict != null || project.extensions.findByName("loom") != null ||
            project.pluginManager.hasPlugin(PLUGIN_ID) || project.extensions.findByName("neoForge") != null
        ) {
            throw GradleException(
                "FabricAmper FA010 neoforge: an existing Loom/ModDevGradle installation conflicts with the pinned MDG $BUNDLED_VERSION backend",
            )
        }
        try {
            project.pluginManager.apply(PLUGIN_ID)
        } catch (e: Exception) {
            throw GradleException(
                "FabricAmper FA010 neoforge: could not apply bundled ModDevGradle $BUNDLED_VERSION; " +
                    "check the Settings plugin implementation dependency and Gradle plugin resolution",
                e,
            )
        }
        // The official ModDevGradle MDK applies java-library explicitly. In
        // particular, `exported: true` needs its `api` bucket even for a mod.
        project.pluginManager.apply("java-library")

        project.version = model.mod.version
        model.mod.group?.let { project.group = it }
        project.extensions.getByType(BasePluginExtension::class.java)
            .archivesName.set((model.mod.archivesName ?: model.mod.id) +
                if (model.isMultiLoader) "-neoforge" else "")

        val java = project.extensions.getByType(JavaPluginExtension::class.java)
        java.toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        if (model.settings.java.sourcesJar) java.withSourcesJar()
        project.tasks.withType(JavaCompile::class.java).configureEach { task ->
            task.options.release.set(25)
            task.options.encoding = "UTF-8"
        }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val selectedDependencies = model.dependencies.filter { it.loader == null || it.loader == "neoforge" }
            .map { it.copy(loader = null) } + neo.gradleDependencies
        val clientRequested = selectedDependencies.any { it.target == "client" } ||
            project.layout.projectDirectory.dir("src/client").asFile.isDirectory
        val client = if (clientRequested) sourceSets.maybeCreate("client") else null

        // In MDG, setting the NeoForge version enables Minecraft artifacts and
        // its run model. Do this only after the Java plugin and source sets exist.
        val mdg = project.extensions.getByType(NeoForgeExtension::class.java)
        mdg.version = neo.version
        val modModel = mdg.mods.maybeCreate(model.mod.id)
        modModel.sourceSet(main)
        if (client != null) {
            // MDG documents this explicit association for additional source sets.
            // Client classes are compiled separately from the common main source.
            mdg.addModdingDependenciesTo(client)
            modModel.sourceSet(client)
            project.dependencies.add(client.implementationConfigurationName, project.files(main.output))
            project.tasks.named("jar", org.gradle.jvm.tasks.Jar::class.java).configure { jar ->
                jar.from(client.output)
            }
        }
        mdg.runs.maybeCreate("client").client()
        mdg.runs.maybeCreate("server").server()

        if (model.settings.kotlin.enabled) configureKotlin(project, model)

        // A settings-only consumer has no authored repositories block in
        // build.gradle(.kts). In particular the Kotlin stdlib we JarJar and
        // ordinary Maven dependencies cannot rely on the developer adding
        // mavenCentral() themselves. Explicitly configure both standard
        // repositories; do not import Fabric's repository or Loom configs.
        RepositoryConfigurer.configure(project, listOf(
            GradleRepositoryDeclaration.Named("neoforge"),
            GradleRepositoryDeclaration.Named("mavenCentral"),
        ) + (model.repositories + neo.repositories)
            .filterNot { it.kind == "fabric" }
            .map { if (it.kind == "maven") GradleRepositoryDeclaration.Maven(
                it.url ?: throw GradleException("FabricAmper FA003 repositories: missing HTTPS Maven URL"),
            ) else GradleRepositoryDeclaration.Named(it.kind) })
        NeoForgeDependencyConfigurer.configure(project, selectedDependencies, model.testDependencies)

        // A test-only JUnit engine is not an instruction to launch a Minecraft
        // world. Keep default Gradle test discovery and NeoForge game runs separate.
        project.tasks.withType(Test::class.java).configureEach { it.useJUnitPlatform() }
    }

    private fun configureKotlin(project: Project, model: ModYamlModel) {
        val requested = model.settings.kotlin
        if (requested.pluginVersion != JavaKotlinConfigurer.BUNDLED_KGP_VERSION ||
            requested.languageVersion != "2.4"
        ) {
            throw GradleException("FabricAmper FA006 settings.kotlin: NeoForge requires bundled Kotlin 2.4.21 / language 2.4")
        }
        if (project.pluginManager.hasPlugin("org.jetbrains.kotlin.jvm") ||
            project.pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")
        ) {
            throw GradleException("FabricAmper FA010 neoforge: Kotlin Gradle plugin already applied")
        }
        try {
            project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        } catch (e: Exception) {
            throw GradleException("FabricAmper FA010 neoforge: bundled Kotlin Gradle plugin could not be applied", e)
        }
        project.extensions.getByType(KotlinJvmProjectExtension::class.java).compilerOptions.apply {
            jvmTarget.set(JvmTarget.JVM_25)
            languageVersion.set(KotlinVersion.KOTLIN_2_4)
        }
        // NeoForge is JavaFML-based; Fabric Language Kotlin is NEVER installed.
        // KGP supplies stdlib to the development compile/runtime classpath but
        // a distributed NeoForge JAR must also carry the Kotlin runtime. Use
        // MDG's JarJar (not Fabric Loom's `include`) with an exact negotiated
        // version. This also covers Kotlin classes merged from :common.
        val kotlinRuntime = project.dependencies.create(
            "org.jetbrains.kotlin:kotlin-stdlib:${JavaKotlinConfigurer.BUNDLED_KGP_VERSION}",
        ) as ExternalModuleDependency
        kotlinRuntime.version { constraint ->
            constraint.strictly("[${JavaKotlinConfigurer.BUNDLED_KGP_VERSION}]")
            constraint.prefer(JavaKotlinConfigurer.BUNDLED_KGP_VERSION)
        }
        project.dependencies.add("jarJar", kotlinRuntime)
    }
}
