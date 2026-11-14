package dev.fabricamper.generator

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import dev.fabricamper.util.failAmper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Inspects the finished distribution JAR, after Loom has added included jars.
 * No outputs are produced, and the task must depend on its archive producer.
 */
@DisableCachingByDefault(because = "Validates the finished JAR and has no output")
abstract class VerifyFabricAmperJar : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jarFile: RegularFileProperty

    @TaskAction
    fun verify() {
        verifyJar(jarFile.get().asFile.toPath())
    }

    companion object {
        // Prevent unbounded temporary disk use and decompression of malformed
        // archives while remaining well above typical Fabric include sizes.
        private const val MAX_NESTED_ARCHIVE_BYTES = 1024L * 1024 * 1024
        private const val MAX_NESTED_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024
        private const val MAX_NESTED_ENTRIES = 100_000

        /** Also callable without Gradle task execution for isolated ZIP fixtures. */
        @JvmStatic
        fun verifyJar(file: Path) {
            try {
                ZipFile(file.toFile()).use { zip ->
                    val counts = linkedMapOf<String, Int>()
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val item = entries.nextElement()
                        counts[item.name] = (counts[item.name] ?: 0) + 1
                    }

                    val rootCount = counts["fabric.mod.json"] ?: 0
                    if (rootCount != 1) {
                        failAmper(
                            "FA013", "fabric.mod.json",
                            "배포 JAR 루트에 fabric.mod.json이 정확히 1개 있어야 합니다 (현재 $rootCount 개): $file",
                        )
                    }

                    val root = zip.getEntry("fabric.mod.json")
                        ?: failAmper("FA013", "fabric.mod.json", "배포 JAR 루트의 메타데이터를 찾을 수 없습니다: $file")
                    if (root.isDirectory) {
                        failAmper("FA013", "fabric.mod.json", "메타데이터 경로가 파일이 아니라 디렉터리입니다: $file")
                    }
                    val decoder = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                    val metadata = InputStreamReader(zip.getInputStream(root), decoder).use { input ->
                        val reader = JsonReader(input)
                        reader.setStrictness(Strictness.STRICT)
                        try {
                            val parsed = JsonParser.parseReader(reader)
                            if (!parsed.isJsonObject) {
                                failAmper("FA013", "fabric.mod.json", "메타데이터 JSON의 루트가 객체가 아닙니다: $file")
                            }
                            if (reader.peek() != JsonToken.END_DOCUMENT) {
                                failAmper("FA013", "fabric.mod.json", "메타데이터 JSON 뒤에 추가 내용이 있습니다: $file")
                            }
                            parsed.asJsonObject
                        } catch (e: IOException) {
                            failAmper("FA013", "fabric.mod.json", "메타데이터 JSON을 읽지 못했습니다: $file (${e.message})")
                        }
                    }
                    validateRequiredRootMetadata(metadata)
                    verifyNestedJars(zip, metadata, counts, file)
                }
            } catch (e: IOException) {
                failAmper("FA013", "jar", "배포 JAR ZIP을 읽지 못했습니다: $file (${e.message})")
            } catch (e: com.google.gson.JsonParseException) {
                failAmper("FA013", "fabric.mod.json", "배포 JAR의 메타데이터 JSON이 올바르지 않습니다: ${e.message}")
            } catch (e: IllegalStateException) {
                failAmper("FA013", "fabric.mod.json", "배포 JAR의 메타데이터 구조가 올바르지 않습니다: ${e.message}")
            }
        }

        private fun validateRequiredRootMetadata(metadata: JsonObject) {
            val schema = metadata.get("schemaVersion")
            if (schema == null || !schema.isJsonPrimitive ||
                !schema.asJsonPrimitive.isNumber ||
                schema.asJsonPrimitive.asString.toIntOrNull() != 1
            ) {
                failAmper("FA013", "fabric.mod.json", "schemaVersion은 정수 1이어야 합니다.")
            }
            for (field in listOf("id", "version")) {
                val value = metadata.get(field)
                if (value == null || !value.isJsonPrimitive ||
                    !value.asJsonPrimitive.isString || value.asString.isBlank()
                ) {
                    failAmper("FA013", "fabric.mod.json", "$field 필드는 비어 있지 않은 문자열이어야 합니다.")
                }
            }
        }

        private fun verifyNestedJars(
            zip: ZipFile,
            metadata: JsonObject,
            counts: Map<String, Int>,
            file: Path,
        ) {
            val jarsField = metadata.get("jars") ?: return
            if (!jarsField.isJsonArray) {
                failAmper("FA013", "fabric.mod.json.jars", "중첩 JAR 목록은 배열이어야 합니다.")
            }
            val mentionedPaths = hashSetOf<String>()
            jarsField.asJsonArray.forEachIndexed { index, item ->
                val pathField = "fabric.mod.json.jars[$index].file"
                if (!item.isJsonObject) {
                    failAmper("FA013", pathField, "중첩 JAR 선언은 file을 포함하는 객체여야 합니다.")
                }
                val nested = item.asJsonObject.get("file")
                if (nested == null || !nested.isJsonPrimitive || !nested.asJsonPrimitive.isString) {
                    failAmper("FA013", pathField, "중첩 JAR 파일 경로가 문자열이 아닙니다.")
                }
                val name = nested.asString
                val segments = name.split('/')
                if (name.isBlank() || name.contains('\\') || name.contains('\u0000') ||
                    ':' in name || name.startsWith('/') ||
                    segments.any { it.isBlank() || it == "." || it == ".." }
                ) {
                    failAmper("FA013", pathField, "중첩 JAR 파일 경로가 잘못되었습니다: $name")
                }
                if (!mentionedPaths.add(name)) {
                    failAmper("FA013", pathField, "같은 중첩 JAR 경로가 두 번 선언되었습니다: $name")
                }
                if (counts[name] != 1) {
                    failAmper(
                        "FA013", pathField,
                        "선언된 중첩 JAR이 ZIP 안에 정확히 1개 있어야 합니다 (현재 ${counts[name] ?: 0} 개): $name ($file)",
                    )
                }
                val entry = zip.getEntry(name) ?: failAmper("FA013", pathField, "중첩 JAR 엔트리가 없습니다: $name")
                if (entry.isDirectory) {
                    failAmper("FA013", pathField, "중첩 JAR은 파일이어야 합니다: $name")
                }
                verifyNestedJarBytes(zip, entry, pathField)
            }
        }

        /**
         * ZipInputStream alone can accept local entries with no central directory
         * or EOCD. Use a size-bounded temporary file and ZipFile instead, then
         * consume every entry and check the declared uncompressed size and CRC.
         * No ZIP contents are extracted to the filesystem.
         */
        private fun verifyNestedJarBytes(zip: ZipFile, entry: ZipEntry, location: String) {
            if (entry.size < 0 || entry.size > MAX_NESTED_ARCHIVE_BYTES || entry.crc < 0) {
                failAmper("FA013", location, "중첩 JAR의 크기 또는 CRC가 올바르지 않습니다: ${entry.name}")
            }
            val temporary = try {
                Files.createTempFile("fabric-amper-nested-", ".jar")
            } catch (e: IOException) {
                failAmper("FA013", location, "중첩 JAR 임시 파일을 만들지 못했습니다: ${e.message}")
            }
            try {
                val checksum = CRC32()
                var copied = 0L
                zip.getInputStream(entry).use { input ->
                    Files.newOutputStream(temporary).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            if (copied > MAX_NESTED_ARCHIVE_BYTES) {
                                failAmper("FA013", location, "중첩 JAR 임시 파일 크기 제한을 초과했습니다: ${entry.name}")
                            }
                            output.write(buffer, 0, count)
                            checksum.update(buffer, 0, count)
                        }
                    }
                }
                if (copied != entry.size || checksum.value != entry.crc) {
                    failAmper("FA013", location, "중첩 JAR 상위 ZIP 엔트리의 크기 또는 CRC 불일치: ${entry.name}")
                }
                ZipFile(temporary.toFile()).use { nested ->
                    // ZipFile requires central-directory and EOCD records.
                    val names = hashSetOf<String>()
                    val members = nested.entries()
                    var entries = 0
                    var expanded = 0L
                    val buffer = ByteArray(8192)
                    while (members.hasMoreElements()) {
                        val member = members.nextElement()
                        entries++
                        if (entries > MAX_NESTED_ENTRIES) {
                            failAmper("FA013", location, "중첩 JAR ZIP 엔트리 개수 제한을 초과했습니다: ${entry.name}")
                        }
                        val name = member.name
                        if (!names.add(name) || !safeNestedMemberName(name) ||
                            member.size < 0 || member.crc < 0 ||
                            member.size > MAX_NESTED_EXPANDED_BYTES - expanded
                        ) {
                            failAmper("FA013", location, "중첩 JAR의 경로·크기·CRC가 올바르지 않습니다: $name")
                        }
                        val memberCrc = CRC32()
                        var decoded = 0L
                        nested.getInputStream(member).use { input ->
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                decoded += count
                                if (decoded > member.size ||
                                    decoded > MAX_NESTED_EXPANDED_BYTES - expanded
                                ) {
                                    failAmper("FA013", location, "중첩 JAR 압축 해제 크기 제한을 초과했습니다: $name")
                                }
                                memberCrc.update(buffer, 0, count)
                            }
                        }
                        if (decoded != member.size || memberCrc.value != member.crc) {
                            failAmper("FA013", location, "중첩 JAR 엔트리의 크기 또는 CRC가 일치하지 않습니다: $name")
                        }
                        expanded += decoded
                    }
                }
            } catch (e: IOException) {
                failAmper("FA013", location, "중첩 JAR 중앙 디렉터리 또는 데이터 검증 실패: ${entry.name} (${e.message})")
            } finally {
                try {
                    Files.deleteIfExists(temporary)
                } catch (e: IOException) {
                    failAmper("FA013", location, "중첩 JAR 검증용 임시 파일을 정리하지 못했습니다: ${e.message}")
                }
            }
        }

        private fun safeNestedMemberName(name: String): Boolean {
            if (name.isBlank() || name.startsWith('/') || '\\' in name ||
                '\u0000' in name || ':' in name
            ) return false
            val components = name.split('/')
            return components.withIndex().all { (index, part) ->
                part != "." && part != ".." &&
                    (part.isNotEmpty() || (index == components.lastIndex && name.endsWith('/')))
            }
        }
    }
}
