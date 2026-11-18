package dev.fabricamper.unit

import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.generator.FabricV11Projection
import dev.fabricamper.plugin.FabricAmperCommonConfigurer
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Root schema 1.1 dependency selectors are public YAML, not platform-specific aliases. */
class FabricAmperSchema11RootLoaderDependenciesTest {
    @TempDir lateinit var workspace: Path
    private var sequence = 0

    @Test
    fun `dual loader root accepts distinct versions of one artifact without cross-loading`() {
        val model = load(modern(dependencies = """
            - maven: "org.example:platform-api:1.4.0"
              loader: fabric
              scope: compile-only
            - maven: "org.example:platform-api:2.1.0"
              loader: neoforge
              scope: runtime-only
            - maven: "org.example:shared-api:3.0.0"
              kind: library
        """))

        assertEquals("1.1", model.schemaVersion)
        assertTrue(model.isMultiLoader)
        assertEquals(3, model.dependencies.size)
        val projectedFabric = FabricV11Projection.fromModel(model)
        assertEquals(
            setOf("org.example:platform-api:1.4.0", "org.example:shared-api:3.0.0"),
            projectedFabric.dependencies.map { it.coordinate }.toSet(),
            "Fabric metadata/Gradle projection must exclude the NeoForge-selected version",
        )
        assertTrue(projectedFabric.dependencies.none { it.coordinate == "org.example:platform-api:2.1.0" })

        val common = commonDependencies(model)
        assertEquals(setOf("org.example:shared-api:3.0.0"), common,
            "A platform-selected root coordinate must not enter :common")
    }

    @Test
    fun `omitted selector remains neutral on explicit and implicit schema 11`() {
        for (explicit in listOf(true, false)) {
            val yaml = modern(dependencies = """
                - "org.example:neutral-shorthand:1.0.0"
                - maven: "org.example:neutral-object:2.0.0"
                  scope: compile-only
            """).let { if (explicit) it else it.removePrefix("schema: \"1.1\"\n") }
            val model = load(yaml)
            assertEquals("1.1", model.schemaVersion)
            assertEquals(2, model.dependencies.size)
            assertEquals(
                setOf("org.example:neutral-shorthand:1.0.0", "org.example:neutral-object:2.0.0"),
                FabricV11Projection.fromModel(model).dependencies.map { it.coordinate }.toSet(),
            )
            assertEquals(
                setOf("org.example:neutral-shorthand:1.0.0", "org.example:neutral-object:2.0.0"),
                commonDependencies(model), "Neutral dependencies must remain usable from :common",
            )
        }
    }

    @Test
    fun `single loader retains neutral root dependencies and permits its own discriminator`() {
        for ((product, selector) in listOf("fabric/mod" to "fabric", "neoforge/mod" to "neoforge")) {
            val neutral = load(modern(product = product, dependencies = """
                - maven: "org.example:neutral:1.0.0"
            """))
            assertFalse(neutral.isMultiLoader)
            assertEquals(listOf("org.example:neutral:1.0.0"), neutral.dependencies.map { it.coordinate })

            val selected = load(modern(product = product, dependencies = """
                - maven: "org.example:selected:2.0.0"
                  loader: $selector
            """))
            assertEquals(listOf("org.example:selected:2.0.0"),
                selected.dependencies.map { it.coordinate }, "product: $product")
            if (selector == "fabric") {
                assertEquals(listOf("org.example:selected:2.0.0"),
                    FabricV11Projection.fromModel(selected).dependencies.map { it.coordinate })
            }
        }
    }

    @Test
    fun `selector cannot name an unknown or unselected loader`() {
        for (value in listOf("quilt", "Fabric", "both", "42", "[fabric, neoforge]", "null", "[]")) {
            val error = YamlFixtures.configError("FA003") {
                load(modern(dependencies = """
                    - maven: "org.example:wrong:1.0.0"
                      loader: $value
                """))
            }
            assertEquals("dependencies[0].loader", error.yamlPath, "selector: $value")
        }
        for ((product, selected) in listOf("fabric/mod" to "neoforge", "neoforge/mod" to "fabric")) {
            val error = YamlFixtures.configError("FA003") {
                load(modern(product = product, dependencies = """
                    - maven: "org.example:wrong-platform:1.0.0"
                      loader: $selected
                """))
            }
            assertEquals("dependencies[0].loader", error.yamlPath, "product: $product")
        }
    }

    @Test
    fun `loader selector belongs only to root schema 11 dependencies`() {
        for (key in listOf("fabric", "neoforge")) {
            val error = YamlFixtures.configError("FA003") {
                load(modern(platformDependency = key, dependencies = """
                    - maven: "org.example:neutral:1.0.0"
                """))
            }
            assertEquals("$key.gradleDependencies[0].loader", error.yamlPath)
        }
        val testDep = modern(dependencies = "[]").replace(
            "dependencies: []", "dependencies: []\ntest-dependencies:\n  - maven: \"org.example:test:1.0.0\"\n    loader: fabric",
        )
        val error = YamlFixtures.configError("FA003") { load(testDep) }
        assertEquals("test-dependencies[0].loader", error.yamlPath)
    }

    @Test
    fun `conflicting entries for the same platform and target remain FA012`() {
        for (selector in listOf("fabric", "neoforge")) {
            val error = YamlFixtures.configError("FA012") {
                load(modern(dependencies = """
                    - maven: "org.example:same:1.0.0"
                      loader: $selector
                      scope: all
                    - maven: "org.example:same:1.0.0"
                      loader: $selector
                      scope: compile-only
                """))
            }
            assertEquals("dependencies[1]", error.yamlPath, "loader: $selector")
        }
        val overlap = YamlFixtures.configError("FA012") {
            load(modern(dependencies = """
                - maven: "org.example:same:1.0.0"
                  scope: all
                - maven: "org.example:same:1.0.0"
                  loader: fabric
                  scope: compile-only
            """))
        }
        assertEquals("dependencies[1]", overlap.yamlPath,
            "An unscoped shared coordinate also belongs to Fabric and must conflict there")
    }

    @Test
    fun `root Fabric selector permits Fabric only include but neutral and NeoForge include fail`() {
        val accepted = load(modern(dependencies = """
            - maven: "org.example:fabric-embedded:1.0.0"
              loader: fabric
              include: true
        """))
        assertTrue(FabricV11Projection.fromModel(accepted).dependencies.single().include)
        assertTrue(commonDependencies(accepted).isEmpty(), "Fabric JIJ must stay out of :common")

        for (selector in listOf("", "  loader: neoforge\n")) {
            val error = YamlFixtures.configError("FA007") {
                load(modern(dependencies = """
                    - maven: "org.example:unsafe-embedded:1.0.0"
                """.trimIndent() + "\n" + selector + "  include: true"))
            }
            assertEquals("dependencies[0].include", error.yamlPath)
        }
    }

    @Test
    fun `Fabric client root selector needs independent split and rejects shared or NeoForge client`() {
        val dep = """
            - maven: "org.example:client:1.0.0"
              loader: fabric
              target: client
        """
        val independent = load(modern(split = true, dependencies = dep))
        val client = FabricV11Projection.fromModel(independent).dependencies.single()
        assertEquals("client", client.target)
        assertTrue(commonDependencies(independent).isEmpty())

        val unsupported = listOf(
            modern(split = false, dependencies = dep),
            modern(split = true, architectury = true, dependencies = dep),
            modern(split = true, dependencies = dep.replace("loader: fabric", "loader: neoforge")),
            modern(split = true, dependencies = dep.lines()
                .filterNot { it.trim() == "loader: fabric" }.joinToString("\n")),
        )
        unsupported.forEachIndexed { index, yaml ->
            val error = YamlFixtures.configError("FA014") { load(yaml) }
            assertEquals("dependencies[0].target", error.yamlPath, "case $index")
        }
    }

    @Test
    fun `schema 1 retains dependencies shorthand and refuses loader selector`() {
        val legacy = load(YamlFixtures.valid + """
            dependencies:
              - "org.example:one:1.0.0": compile-only
              - maven: "org.example:two:2.0.0"
                scope: runtime-only
        """.trimIndent() + "\n")
        assertEquals("1", legacy.schemaVersion)
        assertEquals(listOf("org.example:one:1.0.0", "org.example:two:2.0.0"),
            legacy.dependencies.map { it.coordinate })

        val error = YamlFixtures.configError("FA003") {
            load(YamlFixtures.valid + """
                dependencies:
                  - maven: "org.example:legacy:1.0.0"
                    loader: fabric
            """.trimIndent() + "\n")
        }
        assertEquals("dependencies[0].loader", error.yamlPath)
    }

    private fun load(yaml: String) = ModYamlLoader.load(
        YamlFixtures.write(workspace.resolve("case-${sequence++}"), yaml),
    )

    private fun commonDependencies(model: dev.fabricamper.config.ModYamlModel): Set<String> {
        val root = workspace.resolve("gradle-common-${sequence++}")
        Files.createDirectories(root)
        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        FabricAmperCommonConfigurer.configure(project, model)
        return project.configurations.flatMap { it.dependencies }.mapNotNull { dependency ->
            if (dependency.group == "org.example") {
                "${dependency.group}:${dependency.name}:${dependency.version}"
            } else null
        }.toSet()
    }

    private fun modern(
        product: String = "[fabric/mod, neoforge/mod]",
        dependencies: String = "[]",
        split: Boolean = false,
        architectury: Boolean = false,
        platformDependency: String? = null,
    ): String = buildString {
        appendLine("schema: \"1.1\"")
        appendLine("product: $product")
        appendLine("mod:")
        appendLine("  id: rootdeps")
        appendLine("  version: \"1.0.0+26.3\"")
        appendLine("  group: org.example")
        appendLine("  name: Root Dependency Test")
        appendLine("  license: MIT")
        appendLine("minecraft:")
        appendLine("  version: \"26.3\"")
        appendLine("settings:")
        appendLine("  java:")
        appendLine("    release: 25")
        if (product.contains("fabric/")) {
            appendLine("fabric:")
            appendLine("  loader: \"0.20.1\"")
            appendLine("  api: \"0.165.2+26.3\"")
            appendLine("  mappings: mojang")
            appendLine("  loom:")
            appendLine("    splitEnvironmentSourceSets: $split")
            if (platformDependency == "fabric") append(platformDependencyYaml("fabric"))
        }
        if (product.contains("neoforge/")) {
            appendLine("neoforge:")
            appendLine("  version: \"26.3.0.101\"")
            appendLine("  modLoader: javafml")
            if (platformDependency == "neoforge") append(platformDependencyYaml("neoforge"))
        }
        if (architectury) {
            appendLine("architectury:")
            appendLine("  enable: true")
        }
        appendLine("repositories: [mavenCentral, fabric, neoforge]")
        if (dependencies.trim() == "[]") {
            appendLine("dependencies: []")
        } else {
            appendLine("dependencies:")
            append(dependencies.trimIndent().prependIndent("  "))
            appendLine()
        }
    }

    private fun platformDependencyYaml(loader: String) = """
          gradleDependencies:
            - maven: "org.example:$loader-only:1.0.0"
              loader: $loader
    """.trimIndent().prependIndent("  ") + "\n"
}
