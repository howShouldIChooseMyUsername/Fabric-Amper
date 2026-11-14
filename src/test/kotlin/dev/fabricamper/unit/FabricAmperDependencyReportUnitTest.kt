package dev.fabricamper.unit

import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.plugin.FabricAmperDependencyReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Report tests use validated real YAML; generating the report never needs a Gradle Project. */
class FabricAmperDependencyReportUnitTest {
    @TempDir lateinit var project: Path

    private fun report(yaml: String): String =
        FabricAmperDependencyReport.render(ModYamlLoader.load(YamlFixtures.write(project, yaml)))

    @Test
    fun `shows every build declaration attribute and keeps test dependencies separate`() {
        val yaml = YamlFixtures.valid.replace(
            "settings:\n", "settings:\n  loom:\n    splitEnvironmentSourceSets: true\n",
        ) + """
            dependencies:
              - maven: "org.example:compile:1.0.0"
                kind: library
                scope: compile-only
              - maven: "org.example:runtime:2.0.0"
                scope: runtime-only
              - modrinth:
                  project: sodium
                  version: mc26.3-0.8.0
                target: client
                kind: mod
              - maven: "org.example:embedded:3.0.0"
                include: true
              - maven: "org.example:public:4.0.0"
                exported: true
            test-dependencies:
              - "org.junit.jupiter:junit-jupiter:6.0.0": all
              - "org.example:test-compile:1.0.0": compile-only
        """.trimIndent() + "\n"

        val text = report(yaml)
        val build = text.substringAfter("Gradle dependencies (dependencies; artifact coordinates):\n")
            .substringBefore("Gradle test dependencies (test-dependencies; artifact coordinates):\n")
        val tests = text.substringAfter("Gradle test dependencies (test-dependencies; artifact coordinates):\n")
            .substringBefore("Fabric Loader requirements (mod.depends; mod IDs and version predicates):\n")

        assertTrue(build.contains("[0] coordinate=\"org.example:compile:1.0.0\""), build)
        assertTrue(build.contains("kind=library, scope=compile-only, target=main, exported=false, include=false"), build)
        assertTrue(build.contains("[1] coordinate=\"org.example:runtime:2.0.0\""), build)
        assertTrue(build.contains("kind=library, scope=runtime-only, target=main, exported=false, include=false"), build)
        assertTrue(build.contains("[2] coordinate=\"maven.modrinth:sodium:mc26.3-0.8.0\""), build)
        assertTrue(build.contains("kind=mod, scope=all, target=client, exported=false, include=false"), build)
        assertTrue(build.contains("[3] coordinate=\"org.example:embedded:3.0.0\""), build)
        assertTrue(build.contains("kind=library, scope=all, target=main, exported=false, include=true"), build)
        assertTrue(build.contains("[4] coordinate=\"org.example:public:4.0.0\""), build)
        assertTrue(build.contains("kind=library, scope=all, target=main, exported=true, include=false"), build)

        assertFalse(build.contains("junit-jupiter"), build)
        assertTrue(tests.contains("[0] coordinate=\"org.junit.jupiter:junit-jupiter:6.0.0\""), tests)
        assertTrue(tests.contains("[1] coordinate=\"org.example:test-compile:1.0.0\""), tests)
        assertFalse(tests.contains("org.example:embedded"), tests)
    }

    @Test
    fun `marks authored Loader predicates distinctly from injected profile defaults`() {
        val yaml = YamlFixtures.mod(fields = """
            depends:
              minecraft: ["26.3", "26.3.1"]
              fabricloader: ">=0.20.0"
              helper_mod: ">=1.2.0"
        """).replace("settings:\n", "settings:\n  kotlin:\n    enabled: true\n")

        val text = report(yaml)
        val loader = text.substringAfter("Fabric Loader requirements (mod.depends; mod IDs and version predicates):\n")
            .substringBefore("Publish common relationships (publish.common.dependencies; release-site references):\n")

        assertTrue(loader.contains("minecraft: [\"26.3\", \"26.3.1\"]; origin=authored (mod.depends.minecraft)"), loader)
        assertTrue(loader.contains("fabricloader: \">=0.20.0\"; origin=authored (mod.depends.fabricloader)"), loader)
        assertTrue(loader.contains("helper_mod: \">=1.2.0\"; origin=authored (mod.depends.helper_mod)"), loader)
        assertTrue(loader.contains("java: \">=25\"; origin=generated (settings.java.release)"), loader)
        assertTrue(loader.contains("fabric-api: \">=0.165.2\"; origin=generated (minecraft.fabricApi)"), loader)
        assertTrue(loader.contains("fabric-language-kotlin: \">=1.14.2\"; origin=generated (settings.kotlin.fabricLanguageKotlin)"), loader)
        assertFalse(loader.contains("origin=generated (minecraft.loader)"), loader)

        val sortedIds = loader.lines().filter { it.startsWith("  ") }
            .map { it.trim().substringBefore(':') }
        assertEquals(sortedIds.sorted(), sortedIds, "Loader IDs should render in stable order")
    }

    @Test
    fun `reports common authored relationships and effective platform overrides without leaking credentials`() {
        val yaml = YamlFixtures.mod(fields = "group: org.example") + """
            dependencies:
              - "org.example:ordinary-lib:1.0.0"
            publish:
              common:
                dependencies:
                  - slug: fabric-api
                    modrinthId: mr-fabric
                    curseforgeId: "306612"
                    type: required
                  - slug: helper
                    type: optional
              maven:
                url: https://maven.example.org/releases
                credentialsFromEnv: [MAVEN_USER_ENV, MAVEN_PASSWORD_ENV]
              modrinth:
                projectId: author-modrinth-project
                tokenEnv: MODRINTH_TOKEN_ENV
                dependencies:
                  - projectId: mr-fabric
                    type: embedded
                  - projectId: extra-mr
                    type: incompatible
              curseforge:
                projectId: 98765
                tokenEnv: CURSEFORGE_TOKEN_ENV
                relations:
                  - slug: helper
                    type: tool
        """.trimIndent() + "\n"

        val text = report(yaml)
        val common = text.substringAfter("Publish common relationships (publish.common.dependencies; release-site references):\n")
            .substringBefore("Effective Modrinth release relationships (publish.modrinth.dependencies; project IDs):\n")
        val modrinth = text.substringAfter("Effective Modrinth release relationships (publish.modrinth.dependencies; project IDs):\n")
            .substringBefore("Effective CurseForge release relationships (publish.curseforge.relations; slugs/project IDs):\n")
        val curseforge = text.substringAfter("Effective CurseForge release relationships (publish.curseforge.relations; slugs/project IDs):\n")

        assertTrue(common.contains("slug=\"fabric-api\", type=required, modrinthId=\"mr-fabric\", curseforgeId=\"306612\""), common)
        assertTrue(common.contains("slug=\"helper\", type=optional, modrinthId=(not authored), curseforgeId=(not authored)"), common)
        assertTrue(modrinth.contains("projectId=\"helper\", type=optional"), modrinth)
        assertTrue(modrinth.contains("projectId=\"mr-fabric\", type=embedded"), modrinth)
        assertTrue(modrinth.contains("projectId=\"extra-mr\", type=incompatible"), modrinth)
        assertFalse(modrinth.contains("projectId=\"mr-fabric\", type=required"), modrinth)
        assertTrue(curseforge.contains("slug=\"fabric-api\", projectId=\"306612\", type=requiredDependency"), curseforge)
        assertTrue(curseforge.contains("slug=\"helper\", projectId=(not specified), type=tool"), curseforge)
        assertFalse(curseforge.contains("slug=\"helper\", projectId=(not specified), type=optionalDependency"), curseforge)
        assertTrue(text.contains("Maven artifact coordinates are not Fabric Loader mod IDs or release-site project IDs"), text)
        for (sensitive in listOf(
            "MAVEN_USER_ENV", "MAVEN_PASSWORD_ENV", "MODRINTH_TOKEN_ENV", "CURSEFORGE_TOKEN_ENV",
            "maven.example.org", "credentialsFromEnv", "author-modrinth-project",
        )) {
            assertFalse(text.contains(sensitive), "Unexpected publishing credentials or unrelated settings: $sensitive")
        }
    }

    @Test
    fun `empty and absent dependency sections have deterministic read only explanation`() {
        val model = ModYamlLoader.load(YamlFixtures.write(project, YamlFixtures.valid))
        val first = FabricAmperDependencyReport.render(model)
        val second = FabricAmperDependencyReport.render(model)
        assertEquals(first, second)
        assertTrue(first.contains("Gradle dependencies (dependencies; artifact coordinates):\n  (none)"), first)
        assertTrue(first.contains("Gradle test dependencies (test-dependencies; artifact coordinates):\n  (none)"), first)
        assertTrue(first.contains("Publish common relationships (publish.common.dependencies; release-site references):\n  (none)"), first)
        assertTrue(first.contains("Effective Modrinth release relationships (publish.modrinth.dependencies; project IDs):\n  (destination not configured)"), first)
        assertTrue(first.contains("Effective CurseForge release relationships (publish.curseforge.relations; slugs/project IDs):\n  (destination not configured)"), first)
        assertFalse(first.contains(project.toString()), "Report must not expose source file paths")
        assertTrue(first.endsWith("does not resolve artifacts or verify remote project identities.\n"), first)
    }
}
