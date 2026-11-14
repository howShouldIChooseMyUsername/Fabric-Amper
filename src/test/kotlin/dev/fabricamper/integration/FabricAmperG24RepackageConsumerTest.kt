package dev.fabricamper.integration

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * Complements the existing G24 snapshot-on-help test with a SECOND actual
 * build. Changes to authored mixin/Class Tweaker/icon bytes must invalidate
 * the configuration-time resource snapshot and repack the final Fabric JAR.
 * No Minecraft process, user files or external upload is involved.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_G24_REPACKAGE", matches = "true")
class FabricAmperG24RepackageConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    @Test
    fun resourcesChangedRebuildProducesSecondRealJarAndMirrorSnapshot() {
        val f = TestKitFixture(suiteRoot)
        val mixinName = "example.mixins.json"
        val tweakerName = "example.classtweaker"
        val iconName = "assets/testkit_mod/icon.png"
        val baseYaml = f.resource("minimal-mod.yaml")
        val yaml = baseYaml.replace("  entrypoints:", """
            mixins:
              - $mixinName
            accessWidener: $tweakerName
            icon: $iconName
            entrypoints:
        """.trimIndent().prependIndent("  ").trimEnd())
        assertTrue(yaml.contains("  accessWidener: " + tweakerName),
            "The G24 fixture must register a real Class Tweaker resource")
        assertTrue(yaml.contains("  icon: " + iconName))
        val root = f.project("g24-repackage", yaml)
        f.write(root, "src/main/java/org/example/TestkitEntry.java", """
            package org.example;
            public final class TestkitEntry implements net.fabricmc.api.ModInitializer {
                @Override public void onInitialize() {}
            }
        """.trimIndent() + "\n")
        val originalMixin = f.resource("example.mixins.json")
        val updatedMixin = originalMixin.replace("\"required\": false", "\"required\": true")
        assertFalse(originalMixin == updatedMixin, "The second Mixin fixture must actually change")
        val originalTweaker = "classTweaker v1 official\n"
        val updatedTweaker = originalTweaker +
            "accessible class net/minecraft/world/level/block/Blocks\n"
        val initialIcon = TestKitFixture.onePixelPng
        val replacementIcon = ByteArrayOutputStream().use { stream ->
            val png = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
            png.setRGB(0, 0, 0xFF00AACC.toInt())
            png.setRGB(1, 0, 0xFFFFAA33.toInt())
            png.setRGB(0, 1, 0xFFAA55CC.toInt())
            png.setRGB(1, 1, 0xFF33CC77.toInt())
            check(ImageIO.write(png, "png", stream))
            stream.toByteArray()
        }
        assertFalse(initialIcon.contentEquals(replacementIcon), "Icon bytes must differ")
        assertNotNull(ImageIO.read(replacementIcon.inputStream()), "Replacement icon must be valid PNG")
        f.write(root, "src/main/resources/" + mixinName, originalMixin)
        f.write(root, "src/main/resources/" + tweakerName, originalTweaker)
        f.writeBytes(root, "src/main/resources/" + iconName, initialIcon)
        f.assertNoConsumerBuildScript(root)

        val first = f.run(root, "build")
        assertEquals(TaskOutcome.SUCCESS, first.task(":jar")?.outcome, first.output)
        assertTrue(first.output.contains("BUILD SUCCESSFUL"), first.output)
        val jar = f.jars(root).single()
        val firstJarBytes = Files.readAllBytes(jar)
        assertEntries(jar, originalMixin.toByteArray(StandardCharsets.UTF_8),
            originalTweaker.toByteArray(StandardCharsets.UTF_8), initialIcon, mixinName, tweakerName, iconName)
        val firstMirror = f.mirrorJson(root).single().parent
        assertArrayEquals(initialIcon, Files.readAllBytes(firstMirror.resolve(iconName)))
        assertEquals(originalMixin, Files.readString(firstMirror.resolve(mixinName)))
        assertEquals(originalTweaker, Files.readString(firstMirror.resolve(tweakerName)))
        val metadataBytes = Files.readAllBytes(f.generatedJson(root))

        f.write(root, "src/main/resources/" + mixinName, updatedMixin)
        f.write(root, "src/main/resources/" + tweakerName, updatedTweaker)
        f.writeBytes(root, "src/main/resources/" + iconName, replacementIcon)
        val second = f.run(root, "build")
        assertEquals(TaskOutcome.SUCCESS, second.task(":processResources")?.outcome,
            "Changing referenced resources must rerun resource processing:\n" + second.output)
        assertEquals(TaskOutcome.SUCCESS, second.task(":jar")?.outcome,
            "Changing resources must produce an actual second JAR, not reuse stale outputs:\n" + second.output)
        assertTrue(second.output.contains("BUILD SUCCESSFUL"), second.output)
        val rebuilt = f.jars(root).single()
        assertFalse(firstJarBytes.contentEquals(Files.readAllBytes(rebuilt)),
            "The second actual distribution JAR must differ after Mixin/Tweaker/icon changes")
        assertEntries(rebuilt, updatedMixin.toByteArray(StandardCharsets.UTF_8),
            updatedTweaker.toByteArray(StandardCharsets.UTF_8), replacementIcon, mixinName, tweakerName, iconName)
        assertArrayEquals(metadataBytes, Files.readAllBytes(f.generatedJson(root)),
            "Changing referenced resource BYTES must not mutate the authored Loader JSON paths")
        val newMirror = f.mirrorJson(root).singleOrNull { it.parent != firstMirror }
        assertNotNull(newMirror,
            "Resource byte edits must create a new content-addressed configuration-time snapshot")
        val snapshot = requireNotNull(newMirror).parent
        assertEquals(updatedMixin, Files.readString(snapshot.resolve(mixinName)))
        assertEquals(updatedTweaker, Files.readString(snapshot.resolve(tweakerName)))
        assertArrayEquals(replacementIcon, Files.readAllBytes(snapshot.resolve(iconName)))
        assertArrayEquals(metadataBytes, Files.readAllBytes(newMirror),
            "Loom mirror and generated metadata must remain byte-identical after snapshot invalidation")
        f.assertNoConsumerBuildScript(root)
    }

    private fun assertEntries(
        jar: Path, mixin: ByteArray, tweaker: ByteArray, icon: ByteArray,
        mixinPath: String, tweakerPath: String, iconPath: String,
    ) {
        ZipFile(jar.toFile()).use { zip ->
            for ((path, bytes) in listOf(
                mixinPath to mixin,
                tweakerPath to tweaker,
                iconPath to icon,
            )) {
                val matching = zip.entries().asSequence().filter { it.name == path }.toList()
                assertEquals(1, matching.size, "Exactly one real resource must ship: " + path)
                assertArrayEquals(bytes, zip.getInputStream(matching.single()).use { it.readAllBytes() },
                    "The final distribution contains stale resource bytes: " + path)
            }
            assertEquals(1, zip.entries().asSequence().count { it.name == "fabric.mod.json" },
                "Exactly one root Loader metadata JSON must ship")
            assertNotNull(zip.getEntry("org/example/TestkitEntry.class"),
                "Real compiled Java entrypoint must be present in both distributions")
        }
    }
}
