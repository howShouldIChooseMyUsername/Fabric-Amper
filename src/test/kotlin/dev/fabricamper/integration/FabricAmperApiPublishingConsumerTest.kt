package dev.fabricamper.integration

import com.google.gson.JsonParser
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.readText

/**
 * README 2026-11-13 API/library product contract, exercised with real local
 * Maven publications and separate settings-only downstream Fabric consumers.
 *
 * Opt-in until the implementation and publishing variants are stabilized.
 * Synthetic dependencies are compiled into a JUnit-local Maven repository;
 * no remote publishing, Maven Local, Minecraft client/server or user data.
 * Minecraft/Loom toolchain artifacts can still be fetched from the declared
 * standard repositories when they are not already cached.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_API_PUBLISHING", matches = "true")
class FabricAmperApiPublishingConsumerTest {
    @TempDir lateinit var root: Path

    private val apiGroup = "dev.fabricamper.acceptance"
    private val apiArtifact = "api-contract"
    private val apiVersion = "1.2.3"
    private val apiGav = "$apiGroup:$apiArtifact:$apiVersion"

    private data class Dependency(val group: String, val artifact: String, val version: String, val scope: String)
    private data class Publication(
        val pomDependencies: List<Dependency>,
        val moduleApi: Set<String>,
        val moduleRuntime: Set<String>,
        val loaderMetadata: ByteArray,
        val jarEntries: Set<String>,
    )

    @Test
    fun `fabric api and library alias publish usable local Maven modules with honest transitive scopes`() {
        val fixture = TestKitFixture(root)
        val localFixtures = ApiPublishingLocalMavenFixture.create(root)
        val producerTemplate = fixture.resource("runtime/api-publishing-producer.yaml")
        val consumerTemplate = fixture.resource("runtime/api-publishing-consumer.yaml")
        val observed = linkedMapOf<String, Publication>()

        for ((product, label) in listOf("fabric/api" to "api", "fabric/library" to "library")) {
            val producerYaml = producerTemplate
                .replace("product: fabric/api", "product: $product")
            assertTrue(producerYaml.contains("product: $product"))
            val a = fixture.project("api-publish-$label-a", producerYaml)
            fixture.write(a, "settings.gradle.kts", settingsWithLocalMavenRepositories(localFixtures))
            installProducerJava(fixture, a)
            fixture.assertNoConsumerBuildScript(a)
            assertFalse(Files.exists(a.resolve("build/fabric-amper/maven-repository")),
                "Maven publishing cannot happen during plugin configuration or source creation")

            // This is the *local repository-specific* task, NOT `publish`,
            // `publishToMavenLocal` or any remote repository task.
            val publishing = scopedRun(fixture, a, "publishMavenJavaPublicationToFabricAmperLocalRepository")
            assertEquals(TaskOutcome.SUCCESS,
                publishing.task(":publishMavenJavaPublicationToFabricAmperLocalRepository")?.outcome,
                "API product must create and actually execute its own local publication:\n${publishing.output}")
            assertTrue(publishing.output.contains("BUILD SUCCESSFUL"), publishing.output)
            assertFalse(publishing.output.contains("publishMavenJavaPublicationToFabricAmperRemoteRepository"),
                "No remote repository publishing was requested")
            assertFalse(publishing.output.contains("publishToMavenLocal"),
                "The isolated test never permits publishing into a user's ~/.m2")
            fixture.assertNoConsumerBuildScript(a)

            val published = a.resolve("build/fabric-amper/maven-repository").toRealPath()
            assertTrue(published.startsWith(a.toRealPath()),
                "The publication must remain inside producer A's own build directory")
            val publication = verifyPublication(a, published, label)
            observed[product] = publication

            val consumerYaml = consumerTemplate
            // No direct dependency on public-token is authored in B: Java
            // compilation proves the published A API variant is transitive.
            assertFalse(consumerYaml.contains(ApiPublishingLocalMavenFixture.PUBLIC))
            val b = fixture.project("api-publish-$label-b", consumerYaml)
            fixture.write(b, "settings.gradle.kts", settingsWithLocalMavenRepositories(published, localFixtures))
            installConsumerJava(fixture, b)
            fixture.write(b, "api-publication-scopes.init.gradle", classpathProbeInitScript())
            fixture.assertNoConsumerBuildScript(b)
            val downstream = scopedRun(fixture, b,
                "compileJava", "probePublishedDependencyScopes", "--init-script",
                b.resolve("api-publication-scopes.init.gradle").toString())
            assertEquals(TaskOutcome.SUCCESS, downstream.task(":compileJava")?.outcome,
                "Independent consumer B must compile against published A and its exported public type:\n${downstream.output}")
            assertEquals(TaskOutcome.SUCCESS, downstream.task(":probePublishedDependencyScopes")?.outcome,
                downstream.output)
            assertTrue(Files.isRegularFile(
                b.resolve("build/classes/java/main/dev/fabricamper/acceptance/ConsumerEntry.class")))
            assertTrue(Files.isRegularFile(
                b.resolve("build/classes/java/main/dev/fabricamper/acceptance/PublishedApiUse.class")))
            assertPublishedDownstreamScopes(downstream, label)
            fixture.assertNoConsumerBuildScript(b)

            // A separate negative consumer cannot accidentally share compiled
            // classes with positive B. Internal implementation and compileOnly
            // classes must NOT become valid downstream Java imports.
            val forbidden = fixture.project("api-publish-$label-private-negative", consumerYaml)
            fixture.write(forbidden, "settings.gradle.kts", settingsWithLocalMavenRepositories(published, localFixtures))
            installConsumerJava(fixture, forbidden)
            fixture.write(forbidden,
                "src/main/java/dev/fabricamper/acceptance/ForbiddenInternalUse.java", """
                package dev.fabricamper.acceptance;
                import dev.fabricamper.testfixtures.InternalHelper;
                import dev.fabricamper.testfixtures.CompileMarker;
                public final class ForbiddenInternalUse {
                    @CompileMarker
                    public static Class<?> type() { return InternalHelper.class; }
                }
            """.trimIndent() + "\n")
            val negative = scopedFail(fixture, forbidden, "compileJava")
            assertEquals(TaskOutcome.FAILED, negative.task(":compileJava")?.outcome,
                "Expected a real compiler visibility failure, not configuration resolution failure:\n${negative.output}")
            assertTrue(negative.output.contains("ForbiddenInternalUse.java"), negative.output)
            assertTrue(negative.output.contains("dev.fabricamper.testfixtures"),
                "javac must reject internal/compile-only imports, not an unrelated source:\n${negative.output}")
            assertTrue(negative.output.contains("cannot find symbol") ||
                negative.output.contains("does not exist"), negative.output)
            fixture.assertNoConsumerBuildScript(forbidden)
        }

        val api = requireNotNull(observed["fabric/api"])
        val alias = requireNotNull(observed["fabric/library"])
        assertEquals(api.pomDependencies, alias.pomDependencies,
            "fabric/library must be a Maven scope/GAV-equivalent alias of fabric/api")
        assertEquals(api.moduleApi, alias.moduleApi,
            "API Gradle Module Metadata must not depend on which product spelling was used")
        assertEquals(api.moduleRuntime, alias.moduleRuntime,
            "Runtime Gradle Module Metadata must not depend on the alias spelling")
        assertArrayEquals(api.loaderMetadata, alias.loaderMetadata,
            "Loader metadata must not leak the original fabric/api or fabric/library spelling")
        assertEquals(api.jarEntries, alias.jarEntries,
            "Alias publications must package the same runtime classes and nested resources")
    }

    private fun installProducerJava(fixture: TestKitFixture, project: Path) {
        fixture.write(project,
            "src/main/java/dev/fabricamper/acceptance/PublishedApi.java", """
            package dev.fabricamper.acceptance;
            import dev.fabricamper.testfixtures.PublicToken;
            import dev.fabricamper.testfixtures.InternalHelper;
            import dev.fabricamper.testfixtures.NestedHelper;
            import dev.fabricamper.testfixtures.CompileMarker;
            import net.fabricmc.api.ModInitializer;

            @CompileMarker
            public final class PublishedApi implements ModInitializer {
                @Override public void onInitialize() {}
                public static PublicToken exportedToken() { return new PublicToken(); }
                public static String internalOnly() { return InternalHelper.value(); }
                public static String nestedLibrary() { return NestedHelper.value(); }
            }
        """.trimIndent() + "\n")
        assertFalse(Files.exists(project.resolve("src/main/resources/fabric.mod.json")))
        assertFalse(Files.exists(project.resolve("build.gradle")))
        assertFalse(Files.exists(project.resolve("build.gradle.kts")))
    }

    /**
     * The YAML custom-Maven contract permits HTTPS only. Add the tiny local
     * fixture repositories to each isolated Project instead: Loom must keep
     * its own project repositories for Minecraft, Mojang and remapped modules.
     * Never use PREFER_SETTINGS, which suppresses Loom's internal repositories.
     */
    private fun settingsWithLocalMavenRepositories(vararg local: Path): String {
        require(local.isNotEmpty())
        val entries = local.joinToString("\n") { path ->
            val root = path.toRealPath()
            "maven { url = uri(\"${root.toUri().toASCIIString()}\") }"
        }
        return """
            plugins { id("dev.fabric-amper.settings") }

            gradle.beforeProject {
                repositories {
                    $entries
                }
            }
        """.trimIndent() + "\n"
    }

    private fun installConsumerJava(fixture: TestKitFixture, project: Path) {
        fixture.write(project,
            "src/main/java/dev/fabricamper/acceptance/ConsumerEntry.java", """
            package dev.fabricamper.acceptance;
            import net.fabricmc.api.ModInitializer;
            public final class ConsumerEntry implements ModInitializer {
                @Override public void onInitialize() {}
            }
        """.trimIndent() + "\n")
        fixture.write(project,
            "src/main/java/dev/fabricamper/acceptance/PublishedApiUse.java", """
            package dev.fabricamper.acceptance;
            import dev.fabricamper.testfixtures.PublicToken;
            public final class PublishedApiUse {
                public static PublicToken directExportedType() {
                    return PublishedApi.exportedToken();
                }
            }
        """.trimIndent() + "\n")
    }

    private fun verifyPublication(project: Path, repository: Path, label: String): Publication {
        val gavDir = repository.resolve("${apiGroup.replace('.', '/')}/$apiArtifact/$apiVersion")
        assertTrue(Files.isDirectory(gavDir), "Incorrect $label Maven GAV directory: $gavDir")
        val jar = gavDir.resolve("$apiArtifact-$apiVersion.jar")
        val sources = gavDir.resolve("$apiArtifact-$apiVersion-sources.jar")
        val pom = gavDir.resolve("$apiArtifact-$apiVersion.pom")
        val module = gavDir.resolve("$apiArtifact-$apiVersion.module")
        for (file in listOf(jar, sources, pom, module)) {
            assertTrue(Files.isRegularFile(file), "Required $label publication artifact is missing: $file")
            assertTrue(Files.size(file) > 0, "Published artifact cannot be empty: $file")
        }
        val candidates = Files.list(gavDir).use { stream -> stream.toList() }
        assertEquals(1, candidates.count { it.fileName.toString() == jar.fileName.toString() },
            "Exactly one final Fabric distribution JAR must publish")
        assertEquals(1, candidates.count { it.fileName.toString() == sources.fileName.toString() },
            "Java component sourcesJar must not be manually published twice")
        assertEquals(1, candidates.count { it.fileName.toString() == pom.fileName.toString() })
        assertEquals(1, candidates.count { it.fileName.toString() == module.fileName.toString() })

        val dependencies = inspectPom(pom)
        assertEquals(4, dependencies.count { it.group == ApiPublishingLocalMavenFixture.GROUP },
            "Public/internal/runtime-only/included fixtures must each have one Maven dependency")
        val required = listOf(
            ApiPublishingLocalMavenFixture.PUBLIC to "compile",
            ApiPublishingLocalMavenFixture.INTERNAL to "runtime",
            ApiPublishingLocalMavenFixture.RUNTIME to "runtime",
            ApiPublishingLocalMavenFixture.NESTED to "runtime",
        )
        for ((artifact, scope) in required) {
            assertEquals(listOf(Dependency(ApiPublishingLocalMavenFixture.GROUP, artifact,
                ApiPublishingLocalMavenFixture.VERSION, scope)),
                dependencies.filter { it.group == ApiPublishingLocalMavenFixture.GROUP && it.artifact == artifact },
                "POM must contain exactly one $artifact with Maven scope $scope")
        }
        for (artifact in listOf(ApiPublishingLocalMavenFixture.COMPILE_ONLY,
            ApiPublishingLocalMavenFixture.TEST_ONLY)) {
            assertFalse(dependencies.any { it.group == ApiPublishingLocalMavenFixture.GROUP && it.artifact == artifact },
                "Compile-only and test-only must never leak into the published POM: $artifact")
        }

        val variants = inspectModule(module)
        val api = requireNotNull(variants.entries.singleOrNull { it.key.contains("apiElements", ignoreCase = true) }) {
            "Java-library publication must contain exactly one API variant: $variants"
        }.value
        val runtime = requireNotNull(variants.entries.singleOrNull { it.key.contains("runtimeElements", ignoreCase = true) }) {
            "Java-library publication must contain exactly one runtime variant: $variants"
        }.value
        val expectedApi = ApiPublishingLocalMavenFixture.gav(ApiPublishingLocalMavenFixture.PUBLIC)
        assertTrue(expectedApi in api, "Gradle API variant must export public-token: $api")
        val exportedFixtures = setOf(ApiPublishingLocalMavenFixture.PUBLIC,
            ApiPublishingLocalMavenFixture.INTERNAL, ApiPublishingLocalMavenFixture.RUNTIME,
            ApiPublishingLocalMavenFixture.NESTED)
        for (artifact in exportedFixtures) {
            val gav = ApiPublishingLocalMavenFixture.gav(artifact)
            assertEquals(artifact == ApiPublishingLocalMavenFixture.PUBLIC, gav in api,
                "Gradle API variant must not expose non-exported $artifact: $api")
            assertTrue(gav in runtime, "Gradle runtime variant must transitively include $artifact: $runtime")
        }
        for (artifact in listOf(ApiPublishingLocalMavenFixture.COMPILE_ONLY,
            ApiPublishingLocalMavenFixture.TEST_ONLY)) {
            val gav = ApiPublishingLocalMavenFixture.gav(artifact)
            assertFalse(gav in api || gav in runtime,
                "Compile/test-only $artifact must never be a published API/runtime requirement")
        }

        ZipFile(sources.toFile()).use { zip ->
            assertNotNull(zip.getEntry("dev/fabricamper/acceptance/PublishedApi.java"),
                "Sources JAR must contain the actual published library source")
        }
        val builtDistribution = project.resolve("build/libs/$apiArtifact-$apiVersion.jar")
        assertTrue(Files.isRegularFile(builtDistribution), "Final producer distribution JAR is missing")
        assertArrayEquals(Files.readAllBytes(builtDistribution), Files.readAllBytes(jar),
            "Maven must publish the final production Fabric JAR, not a dev/remap intermediate")
        val result = ZipFile(jar.toFile()).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            assertEquals(1, entries.count { it == "fabric.mod.json" },
                "Published API must have exactly one root Loader metadata entry")
            assertEquals(1, entries.count { it == "dev/fabricamper/acceptance/PublishedApi.class" })
            val bytes = zip.getInputStream(requireNotNull(zip.getEntry("fabric.mod.json")))
                .use { it.readAllBytes() }
            val json = JsonParser.parseString(String(bytes, StandardCharsets.UTF_8)).asJsonObject
            assertEquals("amper_api_acceptance", json.get("id").asString)
            assertEquals(apiVersion, json.get("version").asString)
            assertEquals("*", json.get("environment").asString)
            assertFalse(json.has("product"), "Loader JSON cannot include the fabric/library alias")
            assertTrue(json.getAsJsonObject("entrypoints").getAsJsonArray("main")
                .any { it.asString == "dev.fabricamper.acceptance.PublishedApi" })

            val nestedPaths = json.getAsJsonArray("jars")?.map { it.asJsonObject.get("file").asString }
                ?: emptyList()
            assertEquals(1, nestedPaths.size,
                "include:true must create exactly one nested Loader JIJ path: $nestedPaths")
            val nestedPath = nestedPaths.single()
            assertTrue(nestedPath.startsWith("META-INF/jars/"), nestedPath)
            assertEquals(1, entries.count { it == nestedPath },
                "The nested include archive must exist exactly once")
            val nestedBytes = zip.getInputStream(requireNotNull(zip.getEntry(nestedPath)))
                .use { it.readAllBytes() }
            val nestedEntries = mutableSetOf<String>()
            ZipInputStream(ByteArrayInputStream(nestedBytes)).use { nested ->
                while (true) {
                    val entry = nested.nextEntry ?: break
                    nestedEntries += entry.name
                    nested.closeEntry()
                }
            }
            assertTrue("dev/fabricamper/testfixtures/NestedHelper.class" in nestedEntries,
                "Included local fixture must be present inside the real nested JAR: $nestedEntries")
            for (file in listOf("dev/fabricamper/testfixtures/PublicToken.class",
                "dev/fabricamper/testfixtures/InternalHelper.class",
                "dev/fabricamper/testfixtures/CompileMarker.class")) {
                assertFalse(file in entries, "Non-included external $file must not be bundled directly")
            }
            Publication(dependencies, api, runtime, bytes, entries.toSet())
        }
        return result
    }

    private fun inspectPom(pom: Path): List<Dependency> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setXIncludeAware(false)
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(pom.toFile())
        val project = document.documentElement
        fun directChild(parent: Element, name: String): String? {
            val children = parent.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element && child.localName == name) return child.textContent.trim()
            }
            return null
        }
        assertEquals(apiGroup, directChild(project, "groupId"), "POM groupId must use mod.group")
        assertEquals(apiArtifact, directChild(project, "artifactId"), "POM artifactId must use archivesName")
        assertEquals(apiVersion, directChild(project, "version"), "POM version must use mod.version")
        val depsNode = project.getElementsByTagNameNS("*", "dependency")
        return (0 until depsNode.length).map { index ->
            val dep = depsNode.item(index) as Element
            Dependency(
                requireNotNull(directChild(dep, "groupId")),
                requireNotNull(directChild(dep, "artifactId")),
                requireNotNull(directChild(dep, "version")),
                directChild(dep, "scope") ?: "compile",
            )
        }
    }

    private fun inspectModule(file: Path): Map<String, Set<String>> {
        val module = JsonParser.parseString(file.readText(StandardCharsets.UTF_8)).asJsonObject
        val component = module.getAsJsonObject("component")
        assertEquals(apiGroup, component.get("group").asString)
        assertEquals(apiArtifact, component.get("module").asString)
        assertEquals(apiVersion, component.get("version").asString)
        val variants = module.getAsJsonArray("variants")
        assertTrue(variants.size() >= 2, "The real Java component must publish API and runtime variants")
        return variants.associate { element ->
            val variant = element.asJsonObject
            val name = variant.get("name").asString
            val deps = variant.getAsJsonArray("dependencies")?.map { entry ->
                val d = entry.asJsonObject
                val v = d.get("version")
                val version = if (v != null && v.isJsonObject) {
                    val obj = v.asJsonObject
                    (obj.get("requires") ?: obj.get("strictly") ?: obj.get("prefers")).asString
                } else v?.asString ?: ""
                "${d.get("group").asString}:${d.get("module").asString}:$version"
            }?.toSet() ?: emptySet()
            name to deps
        }
    }

    private fun classpathProbeInitScript() = """
        gradle.projectsEvaluated {
            rootProject.tasks.register('probePublishedDependencyScopes') {
                doLast {
                    ['compileClasspath', 'runtimeClasspath'].each { configuration ->
                        def artifacts = rootProject.configurations.getByName(configuration)
                            .resolvedConfiguration.resolvedArtifacts
                            .collect { artifact ->
                                artifact.moduleVersion.id.group + ':' + artifact.name + ':' + artifact.moduleVersion.id.version
                            }.toSet().sort()
                        println('AMPER_API_PUBLISHING_' + configuration.toUpperCase(java.util.Locale.ROOT) + '=' +
                            artifacts.join('|'))
                    }
                }
            }
        }
    """.trimIndent() + "\n"

    private fun assertPublishedDownstreamScopes(result: BuildResult, label: String) {
        fun observed(name: String): Set<String> {
            val prefix = "AMPER_API_PUBLISHING_$name="
            val matches = result.output.lineSequence().filter { it.startsWith(prefix) }.toList()
            assertEquals(1, matches.size, "$label must emit exactly one $name marker:\n${result.output}")
            return matches.single().removePrefix(prefix).split('|').filter(String::isNotBlank).toSet()
        }
        val compiled = observed("COMPILECLASSPATH")
        val runtime = observed("RUNTIMECLASSPATH")
        assertTrue(apiGav in compiled && apiGav in runtime,
            "Independent B must resolve published $apiGav in both classpaths")
        val publicToken = ApiPublishingLocalMavenFixture.gav(ApiPublishingLocalMavenFixture.PUBLIC)
        assertTrue(publicToken in compiled && publicToken in runtime,
            "A's exported:true API must cross the real published Maven boundary")
        for (artifact in listOf(ApiPublishingLocalMavenFixture.INTERNAL,
            ApiPublishingLocalMavenFixture.RUNTIME, ApiPublishingLocalMavenFixture.NESTED)) {
            val gav = ApiPublishingLocalMavenFixture.gav(artifact)
            assertFalse(gav in compiled, "Internal or runtime-only $gav leaked into B compileClasspath")
            assertTrue(gav in runtime, "Runtime dependency $gav was not transitive to B runtimeClasspath")
        }
        for (artifact in listOf(ApiPublishingLocalMavenFixture.COMPILE_ONLY,
            ApiPublishingLocalMavenFixture.TEST_ONLY)) {
            val gav = ApiPublishingLocalMavenFixture.gav(artifact)
            assertFalse(gav in compiled || gav in runtime,
                "A's compile-only/test dependency $gav must never reach B as transitive")
        }
    }

    private fun scopedRun(fixture: TestKitFixture, project: Path, vararg args: String): BuildResult =
        fixture.run(project, "--gradle-user-home", isolatedGradleHome().toString(), *args)

    private fun scopedFail(fixture: TestKitFixture, project: Path, vararg args: String): BuildResult =
        fixture.fail(project, "--gradle-user-home", isolatedGradleHome().toString(), *args)

    private fun isolatedGradleHome(): Path {
        val producer = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return Files.createDirectories(producer.resolve("build/fabric-amper-testkit-cache/api-publishing/gradle-home"))
    }
}
