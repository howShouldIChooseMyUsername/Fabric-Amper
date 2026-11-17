package dev.fabricamper.plugin

import dev.fabricamper.config.ArchitecturyDefaults
import dev.fabricamper.config.ModYamlModel
import dev.fabricamper.loom.GradleRepositoryDeclaration
import dev.fabricamper.loom.RepositoryConfigurer
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.Locale
import java.util.zip.ZipFile

/**
 * A real, independent `:common` Java/Kotlin artifact. Neither loader plugin is
 * applied in this project. Loader projects depend on its Gradle variant and
 * merge its compiled outputs into their own distributable jars, so the
 * resulting Fabric and NeoForge mods are self-contained.
 */
internal object FabricAmperCommonConfigurer {
    fun configure(project: Project, model: ModYamlModel) {
        project.pluginManager.apply("java-library")
        project.group = model.mod.group ?: "dev.fabricamper.generated"
        project.version = model.mod.version
        project.extensions.getByType(BasePluginExtension::class.java).archivesName
            .set("${model.mod.archivesName ?: model.mod.id}-common")
        val java = project.extensions.getByType(JavaPluginExtension::class.java)
        java.toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        if (model.settings.java.sourcesJar) java.withSourcesJar()
        project.tasks.withType(JavaCompile::class.java).configureEach { task ->
            task.options.release.set(25)
            task.options.encoding = "UTF-8"
        }
        if (model.settings.kotlin.enabled) {
            if (model.settings.kotlin.pluginVersion != "2.4.21" ||
                model.settings.kotlin.languageVersion != "2.4"
            ) {
                throw GradleException("FabricAmper FA006 common: bundled Kotlin 2.4.21 is required")
            }
            project.pluginManager.apply("org.jetbrains.kotlin.jvm")
            project.extensions.getByType(KotlinJvmProjectExtension::class.java).compilerOptions.apply {
                jvmTarget.set(JvmTarget.JVM_25)
                languageVersion.set(KotlinVersion.KOTLIN_2_4)
            }
        }
        // :common consumes only neutral libraries. Root repositories may
        // contain loader-specific Maven aliases for the separate :fabric and
        // :neoforge projects; never add those repositories to :common.
        // Keep Maven Central even when omitted from the YAML, and retain the
        // shared Modrinth exclusivity/HTTPS credential checks in the existing
        // RepositoryConfigurer rather than configuring unguarded Maven URLs.
        val commonRepositories = buildList<GradleRepositoryDeclaration> {
            add(GradleRepositoryDeclaration.Named("mavenCentral"))
            model.repositories.forEach { repository ->
                when (repository.kind) {
                    "fabric", "neoforge" -> Unit
                    "mavenCentral", "modrinth" -> add(GradleRepositoryDeclaration.Named(repository.kind))
                    "maven" -> add(GradleRepositoryDeclaration.Maven(
                        repository.url ?: throw GradleException(
                            "FabricAmper FA003 repositories: custom Maven repository URL is missing",
                        ),
                    ))
                    else -> throw GradleException(
                        "FabricAmper FA003 repositories: unsupported common repository '${repository.kind}'",
                    )
                }
            }
        }
        RepositoryConfigurer.configure(project, commonRepositories)
        if (model.architectury.enabled) {
            project.repositories.maven { repo ->
                repo.name = "ArchitecturyCommon"
                repo.url = project.uri(ArchitecturyDefaults.MAVEN_URL)
            }
            val version = model.architectury.version ?: ArchitecturyDefaults.VERSION
            project.dependencies.add("api", "dev.architectury:architectury:$version")
        }
        // Common-neutral dependencies can compile against public contracts;
        // loader-specific declarations belong exclusively to their projects.
        model.dependencies.forEach { dependency ->
            if (dependency.kind != "library" || dependency.target != "main" || dependency.include) {
                throw GradleException("FabricAmper FA007 dependencies: common project requires main, unembedded libraries")
            }
            project.dependencies.add(
                when (dependency.scope) {
                    "compile-only" -> "compileOnly"
                    "runtime-only" -> "runtimeOnly"
                    else -> if (dependency.exported) "api" else "implementation"
                },
                dependency.coordinate,
            )
        }
    }

    fun linkToLoader(project: Project) {
        // Project dependency by path is safe before :common has been evaluated
        // and avoids passing an actual cross-project Project into isolation.
        val common = project.dependencies.project(mapOf("path" to ":common"))
        project.dependencies.add("implementation", common)
        val commonArchive = project.configurations.create("fabricAmperCommonArchive") { cfg ->
            cfg.isCanBeResolved = true
            cfg.isCanBeConsumed = false
            // Only unpack :common's own JAR, never its transitive Architectury
            // API or other dependencies. Those remain normal classpath entries.
            cfg.isTransitive = false
            cfg.description = "Only the common module JAR to merge into the selected loader's artifact"
            cfg.attributes.attribute(
                Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME),
            )
            cfg.attributes.attribute(
                Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.LIBRARY),
            )
            cfg.attributes.attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                project.objects.named(LibraryElements::class.java, LibraryElements.JAR),
            )
            cfg.attributes.attribute(
                Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling::class.java, Bundling.EXTERNAL),
            )
        }
        project.dependencies.add(commonArchive.name, project.dependencies.project(mapOf("path" to ":common")))
        val unpack = project.tasks.register("unpackFabricAmperCommon", UnpackFabricAmperCommon::class.java) { task ->
            task.group = "build"
            task.commonArchive.from(commonArchive)
            task.classesDirectory.set(project.layout.buildDirectory.dir("fabric-amper/common-classes"))
        }
        project.tasks.named("jar", Jar::class.java).configure { task -> task.from(unpack) }
    }
}

/** Extract only genuine common class/resources, without copying a foreign JAR manifest. */
@CacheableTask
abstract class UnpackFabricAmperCommon : org.gradle.api.DefaultTask() {
    @get:Classpath
    abstract val commonArchive: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val classesDirectory: DirectoryProperty

    @TaskAction
    fun unpack() {
        val jar = commonArchive.singleFile
        val root = classesDirectory.get().asFile.toPath()
        if (Files.exists(root)) {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
        Files.createDirectories(root)
        ZipFile(jar).use { zip ->
            val seen = hashSetOf<String>()
            val seenNormalized = hashSetOf<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                val segments = name.split('/')
                if (!seen.add(name) || name.isEmpty() || name.startsWith('/') || '\\' in name ||
                    '\u0000' in name || ':' in name ||
                    segments.withIndex().any { (index, segment) ->
                        segment == "." || segment == ".." ||
                            (segment.isEmpty() && !(entry.isDirectory && index == segments.lastIndex))
                    } ||
                    !seenNormalized.add(name.removeSuffix("/").lowercase(Locale.ROOT))
                ) {
                    throw GradleException("FabricAmper FA013 common: unsafe/duplicate JAR entry '$name'")
                }
                // Preserve legitimate META-INF resources (particularly
                // META-INF/services/** service providers, native-image and
                // license files). Only archive-specific manifest/signatures
                // and foreign Loader manifests must not enter a mod JAR.
                if (isForeignArchiveMetadata(name)) continue
                val output = root.resolve(name).normalize()
                if (!output.startsWith(root)) {
                    throw GradleException("FabricAmper FA013 common: archive path escapes output directory")
                }
                if (entry.isDirectory) {
                    Files.createDirectories(output)
                } else {
                    if (entry.size < 0 || entry.size > 128L * 1024 * 1024) {
                        throw GradleException("FabricAmper FA013 common: invalid/oversized entry '$name'")
                    }
                    Files.createDirectories(output.parent)
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(output).use { outputStream ->
                            val buffer = ByteArray(8192)
                            var written = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                written += count
                                if (written > entry.size || written > 128L * 1024 * 1024) {
                                    throw GradleException("FabricAmper FA013 common: unbounded ZIP entry '$name'")
                                }
                                outputStream.write(buffer, 0, count)
                            }
                            if (written != entry.size) {
                                throw GradleException("FabricAmper FA013 common: incorrect ZIP entry size '$name'")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isForeignArchiveMetadata(name: String): Boolean {
        val upper = name.uppercase(Locale.ROOT)
        if (upper == "FABRIC.MOD.JSON" || upper == "QUILT.MOD.JSON" ||
            upper == "NEOFORGE.MODS.TOML"
        ) return true
        if (!upper.startsWith("META-INF/")) return false

        val child = upper.removePrefix("META-INF/")
        if (child == "MANIFEST.MF" || child == "NEOFORGE.MODS.TOML" ||
            child == "MODS.TOML" || child == "JARJAR/METADATA.JSON"
        ) return true

        // Signed-JAR verification files live directly under META-INF. Never
        // copy their stale signature block when the containing JAR is merged.
        return '/' !in child && (child.startsWith("SIG-") ||
            child.endsWith(".SF") || child.endsWith(".RSA") ||
            child.endsWith(".DSA") || child.endsWith(".EC"))
    }
}
