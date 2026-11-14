package dev.fabricamper.loom

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Loom reads local mod metadata while configuring the project, before any Gradle
 * task can run. This content-addressed mirror exists exclusively for that read.
 * The runtime JAR receives a separate task-produced copy of the metadata.
 */
object LoomMetadataMirror {
    fun create(project: Project, metadata: ByteArray, referenceResources: Map<String, File> = emptyMap()): File {
        val resources = referenceResources.map { (name, source) ->
            val relative = normalizedResourcePath(name)
            if (!source.isFile) {
                throw GradleException("FabricAmper FA009 mod resource: '$name' does not exist")
            }
            relative to Files.readAllBytes(source.toPath())
        }.sortedBy { it.first.toString() }

        val hash = MessageDigest.getInstance("SHA-256").apply {
            update(metadata.size.toBytes())
            update(metadata)
            resources.forEach { (relative, bytes) ->
                val pathBytes = relative.toString().replace('\\', '/').toByteArray(StandardCharsets.UTF_8)
                update(pathBytes.size.toBytes())
                update(pathBytes)
                update(bytes.size.toBytes())
                update(bytes)
            }
        }.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }

        val relativeRoot = ".gradle/fabric-amper/loom/$hash"
        val root = project.layout.projectDirectory.dir(relativeRoot).asFile.toPath()
        val mirror = root.resolve("fabric.mod.json")
        writeAtomically(mirror, metadata)
        resources.forEach { (relative, bytes) -> writeAtomically(root.resolve(relative), bytes) }

        // Record actual configuration-phase reads, after materialization, so
        // deletion or corruption of the mirror invalidates configuration cache.
        val read = project.providers.fileContents(
            project.layout.projectDirectory.file("$relativeRoot/fabric.mod.json"),
        ).asBytes.get()
        if (!read.contentEquals(metadata)) {
            throw GradleException("FabricAmper FA015 loom mirror: generated metadata differs from YAML")
        }
        resources.forEach { (relative, expected) ->
            val relativeName = relative.toString().replace('\\', '/')
            val observed = project.providers.fileContents(
                project.layout.projectDirectory.file("$relativeRoot/$relativeName"),
            ).asBytes.get()
            if (!observed.contentEquals(expected)) {
                throw GradleException("FabricAmper FA015 loom mirror: resource '$relativeName' differs from YAML")
            }
        }
        return mirror.toFile()
    }

    private fun writeAtomically(path: Path, bytes: ByteArray) {
        Files.createDirectories(path.parent)
        // Never probe the destination before creating it. Gradle 9.7.1 tracks
        // even Files.exists() as a configuration-cache input; observing the
        // first-run absence invalidates the very next configuration cache hit.
        // The hash-named snapshot can safely be replaced with identical bytes.
        val temporary = Files.createTempFile(path.parent, ".amper-", ".tmp")
        var moved = false
        try {
            Files.write(temporary, bytes)
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
            moved = true
        } finally {
            // Avoid observing the now-absent temp file on the success path.
            // On failure configuration cannot be cached; clean up the temp.
            if (!moved) Files.deleteIfExists(temporary)
        }
    }

    private fun normalizedResourcePath(name: String): Path {
        val path = Path.of(name.replace('\\', '/')).normalize()
        if (name.isBlank() || path.isAbsolute || path.toString() == "." || path.any { it.toString() == ".." } || ':' in name) {
            throw GradleException("FabricAmper FA009 mod resource: invalid relative resource path '$name'")
        }
        return path
    }

    private fun Int.toBytes(): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(this).array()
}
