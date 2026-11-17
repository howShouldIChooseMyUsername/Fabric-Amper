package dev.fabricamper.unit

import dev.fabricamper.config.LoaderTargetSpec
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.config.ModYamlModel
import dev.fabricamper.config.NeoForgeDependencySpec
import dev.fabricamper.config.NeoForgeSpec
import dev.fabricamper.generator.NeoForgeMetadataMapper
import dev.fabricamper.generator.VerifyNeoForgeModJar
import dev.fabricamper.util.FabricAmperException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Pure v1.1 mapping and ZIP contracts; no Gradle/TestKit/game/network run. */
class NeoForgeModsMetadataUnitTest {
    @TempDir lateinit var project: Path

    private fun model(): ModYamlModel {
        val base = ModYamlLoader.load(YamlFixtures.write(project))
        return base.copy(
            schemaVersion = "1.1",
            product = "neoforge/mod",
            targets = listOf(LoaderTargetSpec("neoforge", "mod")),
            mod = base.mod.copy(
                id = "examplemod",
                name = "Example Mod",
                license = "MIT",
                description = "한글 \"quoted\"\nline two",
                authors = listOf("Alice", mapOf("name" to "Bob")),
            ),
            neoforge = NeoForgeSpec(version = "26.3.0.101"),
        )
    }

    @Test
    fun rendersModernTomlWithoutFabricLoaderFields() {
        val mod = model().copy(neoforge = NeoForgeSpec(
            version = "26.3.0.101",
            mixins = listOf("z.mixins.json", "a.mixins.json"),
            accessTransformers = listOf("META-INF/accesstransformer.cfg"),
            logoFile = "assets/examplemod/icon.png",
            displayURL = "https://example.org/mod",
            issueTrackerURL = "https://example.org/issues",
            credits = "Contributors",
            updateJSONURL = "https://example.org/update.json",
            dependencies = listOf(
                NeoForgeDependencySpec("jei", type = "optional", versionRange = "[1.0,2.0)",
                    ordering = "AFTER", side = "CLIENT", reason = "Recipe integration"),
            ),
            modProperties = linkedMapOf("zeta" to "z", "alpha" to "a"),
            features = linkedMapOf("javaVersion" to "[25,)"),
        ))
        val first = NeoForgeMetadataMapper.toUtf8Bytes(mod)
        assertArrayEquals(first, NeoForgeMetadataMapper.toUtf8Bytes(mod))
        val value = String(first, StandardCharsets.UTF_8)
        assertTrue(value.startsWith("modLoader=\"javafml\"\nloaderVersion=\"[1,)\"\nlicense=\"MIT\"\n"))
        assertTrue(value.contains("[[mods]]\nmodId=\"examplemod\"\nversion=\"1.0.0+26.3\""))
        assertTrue(value.contains("description=\"한글 \\\"quoted\\\"\\nline two\""))
        assertTrue(value.contains("authors=\"Alice, Bob\""))
        assertTrue(value.contains("logoFile=\"assets/examplemod/icon.png\""))
        assertTrue(value.indexOf("config=\"a.mixins.json\"") < value.indexOf("config=\"z.mixins.json\""))
        assertTrue(value.contains("[[accessTransformers]]\nfile=\"META-INF/accesstransformer.cfg\""))
        assertTrue(value.contains("[[dependencies.examplemod]]\nmodId=\"neoforge\"\ntype=\"required\"\nversionRange=\"[26.3.0.101,)\""))
        assertTrue(value.contains("[[dependencies.examplemod]]\nmodId=\"minecraft\"\ntype=\"required\"\nversionRange=\"[26.3]\""))
        assertTrue(value.contains("modId=\"jei\"\ntype=\"optional\"\nversionRange=\"[1.0,2.0)\"\nordering=\"AFTER\"\nside=\"CLIENT\""))
        assertTrue(value.contains("[modproperties.examplemod]\nalpha=\"a\"\nzeta=\"z\""))
        assertTrue(value.contains("[features.examplemod]\njavaVersion=\"[25,)\""))
        assertFalse(value.contains("fabricloader"))
        assertFalse(value.contains("fabric-api"))
        assertFalse(value.contains("fabric.mod.json"))
        assertEquals(1, value.split("[[mods]]").size - 1)
        assertEquals(1, value.split("license=").size - 1)
        assertTrue(value.endsWith("\n"))
        assertFalse(value.endsWith("\n\n"))
    }

    @Test
    fun preservesByteIdentityAcrossAuthorOrder() {
        val left = model().copy(neoforge = NeoForgeSpec(
            version = "26.3.0.101",
            dependencies = listOf(NeoForgeDependencySpec("zeta"), NeoForgeDependencySpec("alpha")),
            mixins = listOf("z.mixins.json", "a.mixins.json"),
            modProperties = linkedMapOf("z" to "last", "a" to "first"),
        ))
        val right = left.copy(neoforge = left.neoforge!!.copy(
            dependencies = left.neoforge!!.dependencies.reversed(),
            mixins = left.neoforge!!.mixins.reversed(),
            modProperties = linkedMapOf("a" to "first", "z" to "last"),
        ))
        assertArrayEquals(NeoForgeMetadataMapper.toUtf8Bytes(left),
            NeoForgeMetadataMapper.toUtf8Bytes(right))
    }

    @Test
    fun rejectsMissingLicenseMalformedIdAndPlatformRangeConflicts() {
        val model = model()
        assertEquals("FA016", assertThrows(FabricAmperException::class.java) {
            NeoForgeMetadataMapper.toToml(model.copy(mod = model.mod.copy(license = null)))
        }.diagnostic.code)
        assertEquals("FA016", assertThrows(FabricAmperException::class.java) {
            NeoForgeMetadataMapper.toToml(model.copy(mod = model.mod.copy(id = "not-valid")))
        }.diagnostic.code)
        assertEquals("FA016", assertThrows(FabricAmperException::class.java) {
            NeoForgeMetadataMapper.toToml(model.copy(neoforge = model.neoforge!!.copy(
                dependencies = listOf(NeoForgeDependencySpec("neoforge",
                    type = "incompatible", versionRange = "[26.3.0.101]")),
            )))
        }.diagnostic.code)
        assertEquals("FA016", assertThrows(FabricAmperException::class.java) {
            NeoForgeMetadataMapper.toToml(model.copy(neoforge = model.neoforge!!.copy(
                dependencies = listOf(NeoForgeDependencySpec("minecraft", versionRange = "[26.4,)")),
            )))
        }.diagnostic.code)
        assertEquals("FA016", assertThrows(FabricAmperException::class.java) {
            NeoForgeMetadataMapper.toToml(model.copy(neoforge = model.neoforge!!.copy(
                dependencies = listOf(NeoForgeDependencySpec("jei", versionRange = ">=2.0")),
            )))
        }.diagnostic.code)
    }

    @Test
    fun rejectsAuthoredTomlAndUnsafeResourceReferences() {
        val model = model()
        val file = project.resolve("src/main/resources/META-INF/neoforge.mods.toml")
        Files.createDirectories(file.parent)
        Files.writeString(file, "license=\"unknown\"\n")
        assertEquals("FA016", assertThrows(FabricAmperException::class.java) {
            NeoForgeMetadataMapper.validateNoAuthoredToml(model)
        }.diagnostic.code)
        Files.delete(file)
        val altered = model.copy(neoforge = model.neoforge!!.copy(
            mixins = listOf("../outside.mixins.json"),
        ))
        assertEquals("FA016", assertThrows(FabricAmperException::class.java) {
            NeoForgeMetadataMapper.toToml(altered)
        }.diagnostic.code)
        assertEquals("FA016", assertThrows(FabricAmperException::class.java) {
            NeoForgeMetadataMapper.validateResources(model.copy(
                neoforge = model.neoforge!!.copy(logoFile = "assets/icon.png"),
            ), project)
        }.diagnostic.code)
    }

    @Test
    fun verifiesOneTomlAndRejectsFabricJsonMissingTomlAndModifiedOutput() {
        val data = NeoForgeMetadataMapper.toUtf8Bytes(model())
        val good = jar("good.jar", mapOf(
            NeoForgeMetadataMapper.ENTRY_PATH to data,
            "org/example/Mod.class" to byteArrayOf(1, 2, 3),
        ))
        VerifyNeoForgeModJar.verifyJar(good, data)
        assertEquals("FA016", assertThrows(FabricAmperException::class.java) {
            VerifyNeoForgeModJar.verifyJar(good, "tampered".toByteArray())
        }.diagnostic.code)
        val mixed = jar("mixed.jar", mapOf(
            NeoForgeMetadataMapper.ENTRY_PATH to data,
            "fabric.mod.json" to "{}".toByteArray(),
        ))
        assertEquals("FA016", assertThrows(FabricAmperException::class.java) {
            VerifyNeoForgeModJar.verifyJar(mixed)
        }.diagnostic.code)
        val missing = jar("missing.jar", mapOf("org/example/Mod.class" to byteArrayOf(0)))
        assertEquals("FA016", assertThrows(FabricAmperException::class.java) {
            VerifyNeoForgeModJar.verifyJar(missing)
        }.diagnostic.code)
        val invalid = jar("invalid.jar", mapOf(
            NeoForgeMetadataMapper.ENTRY_PATH to byteArrayOf(0xc3.toByte(), 0x28),
        ))
        assertEquals("FA016", assertThrows(FabricAmperException::class.java) {
            VerifyNeoForgeModJar.verifyJar(invalid)
        }.diagnostic.code)
    }

    private fun jar(name: String, entries: Map<String, ByteArray>): Path {
        val file = project.resolve(name)
        ZipOutputStream(Files.newOutputStream(file)).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }
}
