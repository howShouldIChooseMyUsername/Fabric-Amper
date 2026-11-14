package dev.fabricamper.loom

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

/** Java 25 defaults and optional Kotlin JVM / Fabric Language Kotlin integration. */
object JavaKotlinConfigurer {
    const val BUNDLED_KGP_VERSION = "2.4.21"

    fun configure(
        project: Project,
        minecraftVersion: String,
        javaRelease: Int,
        sourcesJar: Boolean,
        kotlinEnabled: Boolean,
        kotlinPluginVersion: String?,
        kotlinLanguageVersion: String?,
        fabricLanguageKotlinVersion: String?,
    ) {
        if (LoomPluginSelector.isNonRemapping(minecraftVersion) && javaRelease != 25) {
            throw GradleException("FabricAmper FA005 settings.java.release: Minecraft $minecraftVersion requires Java 25")
        }
        val java = project.extensions.getByType(JavaPluginExtension::class.java)
        java.toolchain.languageVersion.set(JavaLanguageVersion.of(javaRelease))
        if (sourcesJar) java.withSourcesJar()
        project.tasks.withType(JavaCompile::class.java).configureEach { compile ->
            compile.options.release.set(javaRelease)
        }
        val preexistingKotlinId = listOf(
            "org.jetbrains.kotlin.jvm",
            "kotlin",
            "org.jetbrains.kotlin.multiplatform",
            "org.jetbrains.kotlin.android",
        ).firstOrNull(project.pluginManager::hasPlugin)
        if (preexistingKotlinId != null) {
            throw GradleException(
                "FabricAmper FA010 Kotlin plugin conflict: '$preexistingKotlinId' was already applied. " +
                    "Fabric Amper must control the bundled Kotlin plugin, without external version overrides",
            )
        }
        if (!kotlinEnabled) return
        if (kotlinPluginVersion != null && kotlinPluginVersion != BUNDLED_KGP_VERSION) {
            throw GradleException("FabricAmper FA006 settings.kotlin.pluginVersion: expected bundled KGP $BUNDLED_KGP_VERSION")
        }
        if (fabricLanguageKotlinVersion.isNullOrBlank()) {
            throw GradleException("FabricAmper FA006 settings.kotlin.fabricLanguageKotlin: required when Kotlin is enabled")
        }
        try {
            project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        } catch (e: Exception) {
            throw GradleException("FabricAmper FA010 Kotlin Gradle plugin could not be applied from plugin classpath", e)
        }
        val kotlin = project.extensions.getByType(KotlinJvmProjectExtension::class.java)
        kotlin.compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaRelease.toString()))
        if (!kotlinLanguageVersion.isNullOrBlank()) {
            kotlin.compilerOptions.languageVersion.set(KotlinVersion.fromVersion(kotlinLanguageVersion))
        }
        project.dependencies.add(
            if (LoomPluginSelector.isNonRemapping(minecraftVersion)) "implementation" else "modImplementation",
            "net.fabricmc:fabric-language-kotlin:$fabricLanguageKotlinVersion",
        )
    }
}
