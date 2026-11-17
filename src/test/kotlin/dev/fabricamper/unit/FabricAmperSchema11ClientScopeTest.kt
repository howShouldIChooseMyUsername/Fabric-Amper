package dev.fabricamper.unit

import dev.fabricamper.config.ModYamlLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Direct schema 1.1 loader tests, no Gradle or network. */
class FabricAmperSchema11ClientScopeTest {
    @TempDir lateinit var workspace: Path

    @Test
    fun dualIndependentAllowsFabricClientDependency() {
        val model = load("fabric-client-allowed", dual(split = true, fabricClient = true))
        assertEquals("1.1", model.schemaVersion)
        assertEquals(listOf("fabric/mod", "neoforge/mod"), model.targets.map { it.product })
        assertTrue(model.isMultiLoader)
        assertFalse(model.architectury.enabled)
        assertTrue(model.fabric!!.loom.splitEnvironmentSourceSets)
        assertTrue(model.settings.loom.splitEnvironmentSourceSets)
        val dependency = model.fabric!!.gradleDependencies.single {
            it.coordinate == "org.example:lib:1.0"
        }
        assertEquals("library", dependency.kind)
        assertEquals("client", dependency.target)
        assertFalse(dependency.exported)
        assertFalse(dependency.include)
        assertTrue(model.neoforge!!.gradleDependencies.none {
            it.coordinate == "org.example:lib:1.0"
        }, "Fabric client dependency must not leak into NeoForge")
    }

    @Test
    fun dualRootClientDependencyIsFA014() {
        val error = YamlFixtures.configError("FA014") {
            load("root-client-rejected", dual(split = true, rootClient = true))
        }
        assertEquals("dependencies[0].target", error.yamlPath)
    }

    @Test
    fun dualNeoForgeClientDependencyIsFA014() {
        val error = YamlFixtures.configError("FA014") {
            load("neoforge-client-rejected", dual(split = true, neoforgeClient = true))
        }
        assertEquals("neoforge.gradleDependencies[0].target", error.yamlPath)
    }

    @Test
    fun dualFabricSpecificIncludeIsAllowedWithoutChangingNeoForgeDependencies() {
        val model = load("fabric-specific-include", dual(split = false, fabricInclude = true))
        val fabricDep = model.fabric!!.gradleDependencies.single {
            it.coordinate == "org.example:lib:1.0"
        }
        assertEquals("library", fabricDep.kind)
        assertEquals("main", fabricDep.target)
        assertTrue(fabricDep.include, "Fabric's Loom-managed include belongs exclusively to Fabric")
        assertTrue(model.neoforge!!.gradleDependencies.none { it.coordinate == fabricDep.coordinate },
            "An include declaration under fabric: must not become a NeoForge JarJar dependency")
    }

    @Test
    fun dualCommonIncludeIsRejectedWithFA007() {
        val error = YamlFixtures.configError("FA007") {
            load("dual-root-include", dual(split = true, rootInclude = true))
        }
        assertEquals("dependencies[0].include", error.yamlPath)
    }

    @Test
    fun dualNeoForgeIncludeIsRejectedWithFA007UntilJarJarIsVerified() {
        val error = YamlFixtures.configError("FA007") {
            load("dual-neoforge-include", dual(split = true, neoforgeInclude = true))
        }
        assertEquals("neoforge.gradleDependencies[0].include", error.yamlPath)
    }

    @Test
    fun dualArchitecturyIgnoresSplitAndRejectsFabricClientDependency() {
        val normalized = load("architectury-split-ignored", dual(
            split = true, architectury = true,
        ))
        assertTrue(normalized.architectury.enabled)
        assertFalse(normalized.fabric!!.loom.splitEnvironmentSourceSets)
        assertFalse(normalized.settings.loom.splitEnvironmentSourceSets)

        val error = YamlFixtures.configError("FA014") {
            load("architectury-client-rejected", dual(
                split = true, architectury = true, fabricClient = true,
            ))
        }
        assertEquals("fabric.gradleDependencies[0].target", error.yamlPath)
    }

    @Test
    fun legacySchemaOneStillAllowsClientDependencyOnlyWhenSplitIsEnabled() {
        val dependency = "dependencies:\n  - maven: org.example:lib:1.0\n    kind: library\n    target: client\n"
        val enabled = YamlFixtures.valid.replace(
            "settings:\n", "settings:\n  loom:\n    splitEnvironmentSourceSets: true\n",
        ) + dependency
        val model = load("legacy-split-enabled", enabled)
        assertEquals("1", model.schemaVersion)
        assertTrue(model.settings.loom.splitEnvironmentSourceSets)
        assertEquals("client", model.dependencies.single().target)

        val error = YamlFixtures.configError("FA014") {
            load("legacy-split-disabled", YamlFixtures.valid + dependency)
        }
        assertEquals("dependencies[0].target", error.yamlPath)
    }

    private fun load(name: String, yaml: String) =
        ModYamlLoader.load(YamlFixtures.write(workspace.resolve(name), yaml))

    private fun dual(
        split: Boolean,
        architectury: Boolean = false,
        fabricClient: Boolean = false,
        neoforgeClient: Boolean = false,
        rootClient: Boolean = false,
        fabricInclude: Boolean = false,
        neoforgeInclude: Boolean = false,
        rootInclude: Boolean = false,
    ): String = buildString {
        appendLine("schema: \"1.1\"")
        appendLine("product: [fabric/mod, neoforge/mod]")
        appendLine("mod:")
        appendLine("  id: client_scope")
        appendLine("  version: \"1.0.0+26.3\"")
        appendLine("  group: org.example")
        appendLine("  name: Client Scope")
        appendLine("  license: MIT")
        appendLine("minecraft:")
        appendLine("  version: \"26.3\"")
        appendLine("settings:")
        appendLine("  java:")
        appendLine("    release: 25")
        if (architectury) {
            appendLine("architectury:")
            appendLine("  enable: true")
        }
        appendLine("fabric:")
        appendLine("  loader: \"0.20.1\"")
        appendLine("  api: \"0.165.2+26.3\"")
        appendLine("  mappings: mojang")
        appendLine("  entrypoints:")
        appendLine("    main: [org.example.ExampleFabric]")
        appendLine("  loom:")
        appendLine("    splitEnvironmentSourceSets: $split")
        if (fabricClient || fabricInclude) {
            appendLine("  gradleDependencies:")
            appendLine("    - maven: org.example:lib:1.0")
            appendLine("      kind: library")
            if (fabricClient) appendLine("      target: client")
            if (fabricInclude) appendLine("      include: true")
        }
        appendLine("neoforge:")
        appendLine("  version: \"26.3.0.101\"")
        appendLine("  modLoader: javafml")
        if (neoforgeClient || neoforgeInclude) {
            appendLine("  gradleDependencies:")
            appendLine("    - maven: org.example:lib:1.0")
            appendLine("      kind: library")
            if (neoforgeClient) appendLine("      target: client")
            if (neoforgeInclude) appendLine("      include: true")
        }
        appendLine("repositories: [mavenCentral, fabric, neoforge]")
        if (rootClient || rootInclude) {
            appendLine("dependencies:")
            appendLine("  - maven: org.example:lib:1.0")
            appendLine("    kind: library")
            if (rootClient) appendLine("    target: client")
            if (rootInclude) appendLine("    include: true")
        }
    }
}
