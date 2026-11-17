package dev.fabricamper.generator

import dev.fabricamper.config.MinecraftProfile
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.config.ModYamlModel
import dev.fabricamper.util.LoaderVersionConstraints
import dev.fabricamper.util.failAmper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.net.URI
import java.io.IOException
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Validates schema-1 metadata and its referenced project resources. */
@DisableCachingByDefault(because = "Validates current project resources and has no output")
abstract class ValidateFabricAmper : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val yamlFile: RegularFileProperty

    @TaskAction
    fun checkYaml() {
        val model = ModYamlLoader.load(yamlFile.get().asFile.toPath())
        validate(model)
        validateSourceResources(model)
    }

    companion object {
        private val idPattern = Regex("^[a-z][a-z0-9_-]{1,63}$")
        private val adapterPattern = Regex("^[a-zA-Z0-9_.-]+$")
        private val sizePattern = Regex("^[1-9][0-9]*$")
        private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        /** Pure structural and cross-field validation, with no filesystem access. */
        @JvmStatic
        fun validate(model: ModYamlModel) {
            if (model.schemaVersion == "1.1") {
                validate(FabricV11Projection.fromModel(model))
                return
            }
            check(model.schema == 1, "FA101", "schema", "지원하는 스키마는 정수 1뿐입니다.")
            // API/library are Fabric Loader mods too: only publication behavior
            // differs. Neither product name belongs in fabric.mod.json.
            check(model.product in setOf("fabric/mod", "fabric/api", "fabric/library"),
                "FA102", "product", "product는 'fabric/mod', 'fabric/api', 'fabric/library' 중 하나여야 합니다.")

            val mod = model.mod
            checkId(mod.id, "mod.id")
            check(mod.version.isNotBlank(), "FA104", "mod.version", "모드 버전이 비어 있습니다.")
            check(mod.environment in setOf("*", "client", "server"), "FA105", "mod.environment", "허용값은 '*', 'client', 'server'입니다.")
            if (mod.name != null) check(mod.name.isNotBlank(), "FA106", "mod.name", "이름이 비어 있습니다.")
            if (mod.group != null) check(mod.group.isNotBlank(), "FA106", "mod.group", "group이 비어 있습니다.")
            if (mod.archivesName != null) check(mod.archivesName.isNotBlank(), "FA106", "mod.archivesName", "archivesName이 비어 있습니다.")

            check(mod.provides.distinct().size == mod.provides.size, "FA107", "mod.provides", "중복된 모드 ID 별칭이 있습니다.")
            mod.provides.forEachIndexed { index, id ->
                checkId(id, "mod.provides[$index]")
                check(id != mod.id, "FA107", "mod.provides[$index]", "본 모드 ID와 같은 별칭을 제공할 수 없습니다.")
            }

            checkLicense(mod.license)
            checkPeople(mod.authors, "mod.authors")
            checkPeople(mod.contributors, "mod.contributors")
            checkContact(mod.contact, "mod.contact")
            checkIcon(mod.icon)
            mod.entrypoints.forEach { (name, entries) ->
                check(name.isNotBlank(), "FA108", "mod.entrypoints", "진입점 그룹 이름이 비어 있습니다.")
                entries.forEachIndexed { index, entry ->
                    val path = "mod.entrypoints.$name[$index]"
                    check(entry.value.isNotBlank(), "FA108", "$path.value", "진입점 값이 비어 있습니다.")
                    entry.adapter?.let { adapter ->
                        check(adapterPattern.matches(adapter), "FA108", "$path.adapter", "유효하지 않은 언어 adapter 이름입니다.")
                        if (adapter == "kotlin") {
                            check(model.settings.kotlin.enabled, "FA109", path, "Kotlin 진입점은 settings.kotlin.enabled=true가 필요합니다.")
                        }
                    }
                }
            }
            mod.languageAdapters.forEach { (id, implementation) ->
                check(adapterPattern.matches(id), "FA108", "mod.languageAdapters.$id", "adapter ID가 올바르지 않습니다.")
                check(implementation.isNotBlank(), "FA108", "mod.languageAdapters.$id", "구현 클래스 이름이 비어 있습니다.")
                check(id != "kotlin", "FA109", "mod.languageAdapters.kotlin", "Fabric Language Kotlin의 adapter를 중복 등록하지 마세요.")
            }
            mod.mixins.forEachIndexed { index, mixin ->
                check(mixin.config.isNotBlank(), "FA110", "mod.mixins[$index].config", "Mixin 설정 경로가 비어 있습니다.")
                if (mixin.environment != null) {
                    check(mixin.environment in setOf("*", "client", "server"), "FA110", "mod.mixins[$index].environment", "Mixin environment가 올바르지 않습니다.")
                }
            }

            checkVersionMap(mod.depends, "mod.depends")
            checkVersionMap(mod.recommends, "mod.recommends")
            checkVersionMap(mod.suggests, "mod.suggests")
            checkVersionMap(mod.conflicts, "mod.conflicts")
            checkVersionMap(mod.breaks, "mod.breaks")
            checkCustom(mod.custom, "mod.custom", 0)

            check(model.minecraft.version.isNotBlank(), "FA111", "minecraft.version", "Minecraft 버전이 비어 있습니다.")
            check(model.minecraft.loader.isNotBlank(), "FA111", "minecraft.loader", "Loader 버전이 비어 있습니다.")
            if (model.minecraft.profile == MinecraftProfile.NON_REMAPPING) {
                check(model.minecraft.mappings.type == "mojang" && model.minecraft.mappings.version == null,
                    "FA112", "minecraft.mappings", "26.1 이상에서는 내장 Mojang mappings만 지원합니다.")
                if (model.minecraft.version == "26.3") {
                    check(model.settings.java.release == 25, "FA113", "settings.java.release", "Minecraft 26.3은 Java release 25가 필요합니다.")
                }
            }
            check(model.settings.resources.failOnExistingFabricModJson,
                "FA114", "settings.resources.failOnExistingFabricModJson", "중복 fabric.mod.json 거부 설정은 true여야 합니다.")
            if (model.settings.kotlin.enabled) {
                check(model.settings.kotlin.pluginVersion == "2.4.21", "FA115", "settings.kotlin.pluginVersion", "현재 플러그인 번들 버전 2.4.21과 일치해야 합니다.")
                check(model.settings.kotlin.fabricLanguageKotlin.isNotBlank(), "FA115", "settings.kotlin.fabricLanguageKotlin", "FLK 버전이 비어 있습니다.")
            }

            // User-specified requirements are never silently replaced by inferred ones.
            val selectedVersions = linkedMapOf(
                "minecraft" to model.minecraft.version,
                "fabricloader" to model.minecraft.loader,
                "java" to model.settings.java.release.toString(),
            )
            model.minecraft.fabricApi?.let { selectedVersions["fabric-api"] = it }
            if (model.settings.kotlin.enabled) selectedVersions["fabric-language-kotlin"] = model.settings.kotlin.fabricLanguageKotlin
            val merged = MetadataMapper.resolvedDepends(model)
            selectedVersions.forEach { (id, selected) ->
                val requirement = merged[id]
                    ?: failAmper("FA116", "mod.depends.$id", "필수 Loader 의존성 조건이 없습니다.")
                val versions = when (requirement) {
                    is String -> listOf(requirement)
                    is List<*> -> requirement.filterIsInstance<String>()
                    else -> failAmper("FA116", "mod.depends.$id", "버전 조건 타입이 올바르지 않습니다.")
                }
                when (LoaderVersionConstraints.matchesAny(versions, selected)) {
                    true -> Unit
                    false -> failAmper("FA117", "mod.depends.$id", "선택한 버전 '$selected'와 충돌하는 필수 조건 $requirement")
                    null -> failAmper("FA118", "mod.depends.$id", "선택한 버전 '$selected'과 조건 $requirement 의 비교를 확정할 수 없습니다.")
                }
            }

            for ((group, dependencies) in listOf(
                "dependencies" to model.dependencies,
                "test-dependencies" to model.testDependencies,
            )) {
                dependencies.forEachIndexed { index, dependency ->
                    val location = "$group[$index]"
                    check(dependency.target != "client" || model.settings.loom.splitEnvironmentSourceSets,
                        "FA014", "$location.target", "client 의존성은 splitEnvironmentSourceSets=true가 필요합니다.")
                    check(!(dependency.exported && dependency.scope != "all"),
                        "FA007", "$location.exported", "exported는 scope=all에서만 허용합니다.")
                    check(!(dependency.include && dependency.scope != "all"),
                        "FA007", "$location.include", "include는 scope=all에서만 허용합니다.")
                    check(dependency.target != "client" || !(dependency.exported || dependency.include),
                        "FA014", "$location.target", "client 의존성에서 exported/include는 지원하지 않습니다.")
                }
            }
        }

        /** The output task/Project bridge should invoke this when source trees exist. */
        @JvmStatic
        fun validateSourceResources(model: ModYamlModel) {
            val yamlRoot = model.source.toAbsolutePath().normalize().parent
                ?: failAmper("FA120", "mod.yaml", "설정 파일의 상위 폴더를 확인할 수 없습니다.")
            val selectedRoot = if (model.schemaVersion == "1.1" && model.isMultiLoader) {
                yamlRoot.resolve("fabric")
            } else yamlRoot
            validateSourceResources(model, selectedRoot)
        }

        /**
         * The v1.1 root YAML is outside the :fabric project. Resource paths
         * are resolved relative to the actual target project, never the YAML.
         * Keep the v1 one-argument overload for existing callers.
         */
        @JvmStatic
        fun validateSourceResources(model: ModYamlModel, projectRoot: Path) {
            val fabricModel = FabricV11Projection.fromModel(model)
            val base = projectRoot.toAbsolutePath().normalize()
            val yamlRoot = model.source.toAbsolutePath().normalize().parent
                ?: failAmper("FA120", "mod.yaml", "설정 파일의 상위 폴더를 확인할 수 없습니다.")
            val metadataRoots = linkedSetOf(base)
            if (model.schemaVersion == "1.1" && model.isMultiLoader) {
                // Common and root resources can leak into a platform archive.
                metadataRoots.add(yamlRoot)
                metadataRoots.add(yamlRoot.resolve("common"))
            }
            for (metadataRoot in metadataRoots) {
                for (sourceSet in listOf("main", "client")) {
                    val metadata = metadataRoot.resolve("src/$sourceSet/resources/fabric.mod.json")
                    check(!Files.exists(metadata, LinkOption.NOFOLLOW_LINKS),
                        "FA008", metadata.toString(),
                        "직접 만든 fabric.mod.json과 생성 파일은 함께 사용할 수 없습니다.")
                }
            }
            val mod = fabricModel.mod
            when (val icon = mod.icon) {
                is String -> findResource(base, icon, "mod.icon", png = true)
                is Map<*, *> -> icon.forEach { (size, resource) ->
                    findResource(base, resource as String, "mod.icon.$size", png = true)
                }
            }
            mod.mixins.forEachIndexed { index, mixin ->
                check(mixin.config.endsWith(".json"), "FA121", "mod.mixins[$index]", "Mixin 설정은 .json 파일이어야 합니다.")
                findResource(base, mixin.config, "mod.mixins[$index].config")
            }
            mod.accessWidener?.let { resource ->
                // Loom's accessWidenerPath is resolved from the main resources
                // tree. A client-only class tweaker cannot be used here.
                val file = findResource(base, resource, "mod.accessWidener", mainOnly = true)
                if (fabricModel.minecraft.profile == MinecraftProfile.NON_REMAPPING) {
                    check(resource.endsWith(".classtweaker"), "FA122", "mod.accessWidener", "26.1 이상에서는 .classtweaker 확장자가 필요합니다.")
                    val header = try {
                        Files.newBufferedReader(file, StandardCharsets.UTF_8).use { it.readLine() }
                    } catch (e: IOException) {
                        failAmper("FA131", "mod.accessWidener", "Class Tweaker 첫 줄을 읽지 못했습니다: ${e.message}")
                    }
                    check(header == "classTweaker v1 official", "FA123", "mod.accessWidener", "26.3 Class Tweaker 첫 줄은 'classTweaker v1 official'이어야 합니다.")
                }
            }
        }

        private fun checkId(id: String, location: String) {
            check(idPattern.matches(id), "FA103", location, "ID는 소문자 ASCII로 시작하며 [a-z0-9_-], 길이 2~64여야 합니다.")
        }

        private fun checkLicense(license: Any?) {
            when (license) {
                null -> Unit
                is String -> check(license.isNotBlank(), "FA124", "mod.license", "license가 비어 있습니다.")
                is List<*> -> {
                    check(license.isNotEmpty(), "FA124", "mod.license", "license 배열이 비어 있습니다.")
                    license.forEachIndexed { index, item ->
                        check(item is String && item.isNotBlank(), "FA124", "mod.license[$index]", "비어 있지 않은 문자열이어야 합니다.")
                    }
                }
                else -> failAmper("FA124", "mod.license", "문자열 또는 문자열 배열만 허용합니다.")
            }
        }

        private fun checkPeople(people: List<Any>, location: String) {
            people.forEachIndexed { index, person ->
                val path = "$location[$index]"
                when (person) {
                    is String -> check(person.isNotBlank(), "FA125", path, "작성자 이름이 비어 있습니다.")
                    is Map<*, *> -> {
                        check(person.keys.all { it == "name" || it == "contact" }, "FA125", path, "name/contact 이외의 사용자 필드는 지원하지 않습니다.")
                        check(person["name"] is String && (person["name"] as String).isNotBlank(), "FA125", "$path.name", "작성자 이름이 필요합니다.")
                        val contact = person["contact"]
                        if (contact != null) {
                            check(contact is Map<*, *> && contact.keys.all { it is String } && contact.values.all { it is String },
                                "FA125", "$path.contact", "contact는 문자열 맵이어야 합니다.")
                            @Suppress("UNCHECKED_CAST")
                            checkContact(contact as Map<String, String>, "$path.contact")
                        }
                    }
                    else -> failAmper("FA125", path, "문자열 또는 {name, contact} 객체만 허용합니다.")
                }
            }
        }

        private fun checkContact(contact: Map<String, String>, location: String) {
            contact.forEach { (key, value) ->
                check(key.isNotBlank() && value.isNotBlank(), "FA126", "$location.$key", "contact 키/값은 비어 있을 수 없습니다.")
                if (key == "homepage" || key == "issues") {
                    val uri = runCatching { URI(value) }.getOrNull()
                    check(uri != null && uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank(),
                        "FA126", "$location.$key", "HTTP(S) URL이어야 합니다.")
                }
                if (key == "email") check(emailPattern.matches(value), "FA126", "$location.email", "이메일 형식이 올바르지 않습니다.")
            }
        }

        private fun checkIcon(icon: Any?) {
            when (icon) {
                null -> Unit
                is String -> check(icon.isNotBlank(), "FA127", "mod.icon", "아이콘 경로가 비어 있습니다.")
                is Map<*, *> -> {
                    check(icon.isNotEmpty(), "FA127", "mod.icon", "아이콘 크기 맵이 비어 있습니다.")
                    icon.forEach { (size, path) ->
                        check(size is String && sizePattern.matches(size) && size.toBigIntegerOrNull() != null,
                            "FA127", "mod.icon.$size", "아이콘 크기는 양의 정수 문자열이어야 합니다.")
                        check(path is String && path.isNotBlank(), "FA127", "mod.icon.$size", "아이콘 경로는 문자열이어야 합니다.")
                    }
                }
                else -> failAmper("FA127", "mod.icon", "아이콘은 PNG 경로 또는 크기→PNG 경로 맵이어야 합니다.")
            }
        }

        private fun checkVersionMap(requirements: Map<String, Any>, location: String) {
            requirements.forEach { (id, value) ->
                checkId(id, "$location.$id")
                when (value) {
                    is String -> check(value.isNotBlank(), "FA128", "$location.$id", "버전 조건 문자열이 비어 있습니다.")
                    is List<*> -> {
                        check(value.isNotEmpty(), "FA128", "$location.$id", "OR 조건 배열이 비어 있습니다.")
                        value.forEachIndexed { index, part ->
                            check(part is String && part.isNotBlank(), "FA128", "$location.$id[$index]", "OR 조건은 문자열이어야 합니다.")
                        }
                    }
                    else -> failAmper("FA128", "$location.$id", "버전 조건은 문자열 또는 문자열 배열이어야 합니다.")
                }
            }
        }

        private fun checkCustom(value: Any?, location: String, depth: Int) {
            check(depth <= 32, "FA129", location, "custom JSON 중첩 깊이가 32를 넘었습니다.")
            when (value) {
                null, is String, is Boolean, is Byte, is Short, is Int, is Long,
                is java.math.BigInteger, is java.math.BigDecimal -> Unit
                is Float -> check(value.isFinite(), "FA129", location, "유한하지 않은 JSON 숫자입니다.")
                is Double -> check(value.isFinite(), "FA129", location, "유한하지 않은 JSON 숫자입니다.")
                is Map<*, *> -> value.forEach { (key, child) ->
                    check(key is String, "FA129", location, "custom JSON 객체 키는 문자열이어야 합니다.")
                    checkCustom(child, "$location.$key", depth + 1)
                }
                is List<*> -> value.forEachIndexed { index, child -> checkCustom(child, "$location[$index]", depth + 1) }
                else -> failAmper("FA129", location, "JSON으로 표현할 수 없는 값 타입 ${value.javaClass.name}입니다.")
            }
        }

        private fun findResource(
            base: Path,
            relative: String,
            location: String,
            png: Boolean = false,
            mainOnly: Boolean = false,
        ): Path {
            check(relative.isNotBlank() && !relative.contains('\\') && !relative.startsWith('/') && !relative.contains('\u0000'),
                "FA130", location, "리소스는 정규화된 상대 경로를 사용해야 합니다.")
            val parts = relative.split('/')
            check(parts.none { it.isEmpty() || it == "." || it == ".." }, "FA130", location, "리소스 경로에 빈 세그먼트나 ../가 있습니다.")
            if (png) check(relative.lowercase().endsWith(".png"), "FA130", location, "아이콘은 PNG 파일이어야 합니다.")
            val sourceSets = if (mainOnly) listOf("main") else listOf("main", "client")
            for (sourceSet in sourceSets) {
                val resourcesRoot = base.resolve("src/$sourceSet/resources").normalize()
                val candidate = resourcesRoot.resolve(relative).normalize()
                check(candidate.startsWith(resourcesRoot), "FA130", location,
                    "리소스가 src/$sourceSet/resources 경로를 벗어났습니다.")
                if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) continue

                // Compare canonical paths against the *specific source-set root*.
                // Checking only the project root allowed links into other src
                // directories (and arbitrary project-local files).
                val actualRoot = try {
                    resourcesRoot.toRealPath()
                } catch (e: IOException) {
                    failAmper("FA131", location, "src/$sourceSet/resources 디렉터리를 읽지 못했습니다: ${e.message}")
                }
                val expectedRoot = try {
                    base.toRealPath().resolve("src/$sourceSet/resources").normalize()
                } catch (e: IOException) {
                    failAmper("FA131", location, "프로젝트 루트의 실제 경로를 읽지 못했습니다: ${e.message}")
                }
                check(actualRoot == expectedRoot, "FA130", location,
                    "src/$sourceSet/resources 디렉터리 자체가 심볼릭 링크로 다른 위치를 가리킵니다.")
                val actualCandidate = try {
                    candidate.toRealPath()
                } catch (e: NoSuchFileException) {
                    failAmper("FA131", location, "리소스 '$relative'의 심볼릭 링크 대상이 없습니다: ${e.message}")
                } catch (e: IOException) {
                    failAmper("FA131", location, "리소스 '$relative'의 실제 경로를 읽지 못했습니다: ${e.message}")
                }
                check(actualCandidate.startsWith(actualRoot), "FA130", location,
                    "리소스 '$relative'의 심볼릭 링크 대상이 src/$sourceSet/resources 밖에 있습니다.")
                check(Files.isRegularFile(actualCandidate), "FA131", location,
                    "리소스 '$relative'는 일반 파일이어야 합니다.")
                // Read the canonical file thereafter, avoiding a second symlink
                // traversal when the Class Tweaker header is inspected.
                return actualCandidate
            }
            val expectedLocation = if (mainOnly) {
                "src/main/resources (src/client/resources에만 있는 Class Tweaker는 사용할 수 없습니다)"
            } else {
                "src/main/resources 또는 src/client/resources"
            }
            failAmper("FA131", location, "리소스 '$relative'를 $expectedLocation 에서 찾을 수 없습니다.")
        }

        private fun check(condition: Boolean, code: String, location: String, explanation: String) {
            if (!condition) failAmper(code, location, explanation)
        }
    }
}
