package dev.fabricamper.loom

import dev.fabricamper.config.DependencySpec
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.tasks.testing.Test

/** Native ModDevGradle dependencies; no Loom mod* or Fabric include configurations. */
object NeoForgeDependencyConfigurer {
    fun configure(project: Project, main: List<DependencySpec>, tests: List<DependencySpec>) {
        main.forEachIndexed { index, dep ->
            requireValid(dep, "dependencies[$index]", test = false)
            val prefix = if (dep.target == "client") "client" else ""
            val name = when {
                dep.exported -> "api"
                dep.scope == "all" -> "implementation"
                dep.scope == "compile-only" -> "compileOnly"
                else -> "runtimeOnly"
            }.let { if (prefix.isEmpty()) it else prefix + it.replaceFirstChar(Char::uppercaseChar) }
            project.dependencies.add(name, dep.coordinate)
            if (dep.include) {
                // MDG JarJar negotiates library versions and produces NeoForge's
                // META-INF/jarjar/metadata.json, never Fabric's jars[] metadata.
                val version = dep.coordinate.substringAfterLast(':')
                val embedded = project.dependencies.create(dep.coordinate) as? ExternalModuleDependency
                    ?: throw GradleException("FabricAmper FA007 dependencies[$index]: Jar-in-Jar requires a Maven module")
                embedded.version { constraint ->
                    constraint.strictly("[$version]")
                    constraint.prefer(version)
                }
                project.dependencies.add("jarJar", embedded)
            }
        }
        tests.forEachIndexed { index, dep ->
            requireValid(dep, "test-dependencies[$index]", test = true)
            val name = when (dep.scope) {
                "all" -> "testImplementation"
                "compile-only" -> "testCompileOnly"
                else -> "testRuntimeOnly"
            }
            project.dependencies.add(name, dep.coordinate)
        }
        val jupiter = tests.firstOrNull { it.coordinate.startsWith("org.junit.jupiter:junit-jupiter:") }
        if (jupiter != null) {
            project.tasks.withType(Test::class.java).configureEach { it.useJUnitPlatform() }
            if (tests.none { it.coordinate.startsWith("org.junit.platform:junit-platform-launcher:") }) {
                project.dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:${jupiter.coordinate.substringAfterLast(':')}")
            }
        }
    }

    private fun requireValid(dep: DependencySpec, path: String, test: Boolean) {
        if (dep.kind !in setOf("mod", "library")) fail(path, "kind must be mod or library")
        if (dep.scope !in setOf("all", "compile-only", "runtime-only")) fail(path, "unsupported scope")
        if (dep.target !in setOf("main", "client")) fail(path, "unsupported source-set target")
        if (dep.exported && (dep.scope != "all" || dep.target == "client" || test)) {
            fail(path, "exported requires main scope=all and cannot be a test dependency")
        }
        if (dep.include && (dep.scope != "all" || dep.target == "client" || test)) {
            fail(path, "Jar-in-Jar include requires main scope=all outside tests")
        }
        if (test && dep.target != "main") fail(path, "test dependencies cannot target client")
    }

    private fun fail(path: String, message: String): Nothing =
        throw GradleException("FabricAmper FA007 $path: $message")
}
