package dev.fabricamper.generator

import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.util.failAmper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/** Checks exactly one modern NeoForge TOML, with no Fabric Loader JSON. */
@DisableCachingByDefault(because = "Inspects a distribution JAR without producing an output")
abstract class VerifyNeoForgeModJar : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jarFile: RegularFileProperty

    /** Optional stronger byte-for-byte comparison with the YAML's metadata. */
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val yamlFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val expected = yamlFile.orNull?.asFile?.toPath()?.let { yaml ->
            NeoForgeMetadataMapper.toUtf8Bytes(ModYamlLoader.load(yaml))
        }
        verifyJar(jarFile.get().asFile.toPath(), expected)
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun verifyJar(path: Path, expected: ByteArray? = null) {
            try {
                ZipFile(path.toFile()).use { zip ->
                    val names = hashSetOf<String>()
                    var tomlCount = 0
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!names.add(entry.name)) {
                            failAmper("FA016", "jar", "ZIP 항목이 중복되었습니다: ${entry.name}")
                        }
                        if (entry.name == NeoForgeMetadataMapper.ENTRY_PATH) {
                            tomlCount++
                            if (entry.isDirectory)
                                failAmper("FA016", NeoForgeMetadataMapper.ENTRY_PATH, "TOML 위치가 디렉터리입니다.")
                        }
                        if (entry.name == "fabric.mod.json" || entry.name.endsWith("/fabric.mod.json")) {
                            failAmper("FA016", "fabric.mod.json", "NeoForge 배포 JAR에는 Fabric Loader JSON이 없어야 합니다.")
                        }
                        if (entry.name == "META-INF/mods.toml") {
                            failAmper("FA016", "META-INF/mods.toml", "현대 NeoForge JAR에는 구형 mods.toml을 함께 넣을 수 없습니다.")
                        }
                    }
                    if (tomlCount != 1)
                        failAmper("FA016", NeoForgeMetadataMapper.ENTRY_PATH,
                            "NeoForge 배포 JAR에는 생성 TOML이 정확히 하나 필요합니다 (현재 $tomlCount).")
                    val entry = zip.getEntry(NeoForgeMetadataMapper.ENTRY_PATH)
                        ?: failAmper("FA016", NeoForgeMetadataMapper.ENTRY_PATH, "생성 TOML을 찾을 수 없습니다.")
                    val bytes = zip.getInputStream(entry).use { it.readAllBytes() }
                    val decoder = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                    val text = try {
                        decoder.decode(ByteBuffer.wrap(bytes)).toString()
                    } catch (e: java.nio.charset.CharacterCodingException) {
                        failAmper("FA016", NeoForgeMetadataMapper.ENTRY_PATH, "TOML이 유효한 UTF-8이 아닙니다.")
                    }
                    if (!text.contains("[[mods]]") ||
                        !Regex("""(?m)^modId="[^"]+"$""").containsMatchIn(text) ||
                        !Regex("""(?m)^license="[^"]+"$""").containsMatchIn(text) ||
                        !Regex("""(?m)^version="[^"]+"$""").containsMatchIn(text) ||
                        !Regex("""(?m)^displayName="[^"]+"$""").containsMatchIn(text)
                    ) {
                        failAmper("FA016", NeoForgeMetadataMapper.ENTRY_PATH,
                            "NeoForge TOML 필수 license/modId/version/displayName을 확인하지 못했습니다.")
                    }
                    if (expected != null && !bytes.contentEquals(expected))
                        failAmper("FA016", NeoForgeMetadataMapper.ENTRY_PATH,
                            "최종 JAR TOML 바이트가 YAML 생성 결과와 다릅니다.")
                }
            } catch (error: IOException) {
                failAmper("FA016", "jar", "NeoForge 배포 ZIP을 읽을 수 없습니다: ${error.message}")
            }
        }
    }
}
