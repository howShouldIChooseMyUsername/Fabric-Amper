package dev.fabricamper.loom

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/** A fully normalized dependency, independent of the original YAML shorthand. */
data class GradleDependencyDeclaration(
    val coordinate: String,
    val kind: String = "library",
    val scope: String = "all",
    val target: String = "main",
    val exported: Boolean = false,
    val include: Boolean = false,
)

object DependencyConfigurer {
    fun configure(project: Project, minecraftVersion: String, dependencies: List<GradleDependencyDeclaration>, testDependencies: List<GradleDependencyDeclaration>) {
        val remapping = !LoomPluginSelector.isNonRemapping(minecraftVersion)
        dependencies.forEach { dependency ->
            val configuration = configurationName(dependency, remapping, false)
            project.dependencies.add(configuration, dependency.coordinate)
            if (dependency.include) {
                project.dependencies.add("include", dependency.coordinate)
            }
        }

        testDependencies.forEach { dependency ->
            val configuration = configurationName(dependency, remapping, true)
            project.dependencies.add(configuration, dependency.coordinate)
        }
        val junitJupiter = testDependencies.firstOrNull {
            it.coordinate.startsWith("org.junit.jupiter:junit-jupiter:")
        }
        if (junitJupiter != null) {
            project.tasks.withType(Test::class.java).configureEach { task -> task.useJUnitPlatform() }
            if (testDependencies.none { it.coordinate.startsWith("org.junit.platform:junit-platform-launcher:") }) {
                val junitVersion = junitJupiter.coordinate.substringAfterLast(':')
                project.dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:$junitVersion")
            }
        }
    }

    fun configurationName(dependency: GradleDependencyDeclaration, remapping: Boolean, test: Boolean): String {
        if (dependency.kind !in setOf("mod", "library")) fail("kind", "unsupported '${dependency.kind}'")
        if (dependency.scope !in setOf("all", "compile-only", "runtime-only")) fail("scope", "unsupported '${dependency.scope}'")
        if (dependency.target !in setOf("main", "client")) fail("target", "unsupported '${dependency.target}'")
        if (dependency.exported && dependency.scope != "all") fail("exported", "requires scope=all")
        if (dependency.include && (dependency.scope != "all" || dependency.target == "client" || test)) {
            fail("include", "requires main scope=all outside test-dependencies")
        }
        if (dependency.target == "client" && (dependency.exported || test)) {
            fail("target", "client exported and test dependencies are unsupported")
        }
        if (remapping && dependency.target == "client") {
            fail("target", "client dependencies in remapping Loom require separate validation")
        }
        if (test) {
            return when (dependency.scope) {
                "all" -> "testImplementation"
                "compile-only" -> "testCompileOnly"
                else -> "testRuntimeOnly"
            }
        }
        val isRemappedMod = remapping && dependency.kind == "mod"
        val prefix = if (dependency.target == "client") "client" else if (isRemappedMod) "mod" else ""
        return when {
            dependency.exported && prefix == "" -> "api"
            dependency.exported && isRemappedMod -> "modApi"
            dependency.scope == "all" -> prefix + "Implementation"
            dependency.scope == "compile-only" -> prefix + "CompileOnly"
            else -> prefix + "RuntimeOnly"
        }.replaceFirstChar { if (prefix.isEmpty()) it.lowercaseChar() else it }
    }

    private fun fail(path: String, message: String): Nothing =
        throw GradleException("FabricAmper FA007 dependencies.$path: $message")
}
