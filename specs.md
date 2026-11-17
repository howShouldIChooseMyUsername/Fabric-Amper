# Fabric Amper — 구현 청사진 (`specs.md`)

**기준일:** 2026-11-11. **단계:** 조사·설계만 완료 대상. 플러그인 코드, 배포물, TestKit/IntelliJ 실행 검증은 아직 없다. 본문에 등장하는 `dev.fabric-amper.settings`와 `0.1.0`은 **설계상 향후 발행할 ID와 버전**이다.

## 1. 사용자 계약·기술적 사실

Fabric Amper는 Fabric 모드가 아니라 **Gradle binary plugin**이다. 개발자에게 필요한 빌드 설정은 프로젝트 루트의 `mod.yaml`(또는 `mod.yml`) 하나이며 `settings.gradle.kts`는 플러그인 해석을 위한 최소 진입점이다. 개발자 프로젝트에 `build.gradle`/`build.gradle.kts`를 생성하거나 요구하지 않는다. `Settings`/`Project`/Fabric Loom API를 프로그램으로 구성한다. `fabric.mod.json`은 개발자가 직접 만들지 않는다. Loom의 설정 단계 읽기용 **관리형 미러(`.gradle/`)** 및 Gradle의 **태스크 생성 출력(`build/`)**을 사용하되 최종 모드 JAR 루트에는 정확히 하나만 포함한다.

검증 가능한 공식 사실과 본 프로젝트의 설계 선택을 구분한다.

| 질문 | 공식 확인된 사실 | Fabric Amper 설계 |
| --- | --- | --- |
| Minecraft 26.3용 Loom | MC 26.1+는 비난독화. 플러그인 ID `net.fabricmc.fabric-loom`, `mappings` 의존성 불필요, `jar` 사용 | 26.3을 기본 프로파일 |
| 과거 Yarn 지원 | 1.21.11 이하 난독화 버전은 `net.fabricmc.fabric-loom-remap` + `mappings` | 명확히 분리된 레거시 프로파일 |
| Modrinth | Maven URL `https://api.modrinth.com/maven`, 좌표 `maven.modrinth:slug-or-id:version-or-id` | YAML 객체형/좌표형 지원 |
| Amper | `module.yaml`의 `product`/`dependencies`/`settings`, `: compile-only` 및 `exported` 축약형 | `product: fabric/mod`는 **Fabric Amper 고유 타입** |
| `include` | Loom의 **비전이적** jar-in-jar 구성. 비리매핑이면 `jar`, 리매핑이면 `remapJar`에 포함 | `: include`는 컴파일/런타임 + jar-in-jar 축약형 |
| Gradle 훅 | `GradleLifecycle.beforeProject(IsolatedAction<? super Project>)`, Gradle 8.8부터 | Settings plugin에서 훅 등록, 루트 Project plugin 적용 |
| 외부 플러그인 | `beforeProject`만으로 아직 해석되지 않은 외부 plugin ID를 자동 다운로드할 수 없음 | 설정 플러그인 자체 클래스패스로 Loom/Kotlin 구현을 공급; TestKit을 출시 게이트로 지정 |

### 독립 심층 조사 6개 트랙과 Prime 교차 검증

| 트랙 | 소스 근거 | 교차 검증 및 명세 반영 |
| --- | --- | --- |
| **A1 — Loom 구현/Extension** | [LoomGradlePlugin.java](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/src/main/java/net/fabricmc/loom/LoomGradlePlugin.java), [LoomGradleExtensionApiImpl.java](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/src/main/java/net/fabricmc/loom/extension/LoomGradleExtensionApiImpl.java) | 실제 Loom 적용 순서와 `splitEnvironmentSourceSets` 시그니처 대조 |
| **A2 — Loom 의존성·패키징** | [Fabric 공식 Loom 문서](https://docs.fabricmc.net/develop/loom/), [NonRemappedJarTaskConfiguration.java](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/src/main/java/net/fabricmc/loom/task/NonRemappedJarTaskConfiguration.java) | `mappings`/`modImplementation`의 버전별 구분 및 `include`의 JAR 경로 검증 |
| **B3 — Settings 플러그인** | [Gradle Isolated Projects](https://docs.gradle.org/current/userguide/isolated_projects.html#sec:applying_plugins_from_a_lifecycle_callback) | `beforeProject`와 외부 플러그인 클래스패스의 제약 확인 |
| **B4 — Project/외부 플러그인** | [Gradle Binary Plugin 구현·의존성](https://docs.gradle.org/current/userguide/implementing_gradle_plugins_binary.html#sec:plugin_dependencies) | Project plugin의 적용 시점, marker, Kotlin/Loom 버전 충돌 위험 분석 |
| **C5 — Amper 스타일 YAML** | [Kotlin Toolchain module.yaml](https://kotlin-toolchain.org/latest/reference/module/), [Dependencies](https://kotlin-toolchain.org/latest/user-guide/dependencies/) | 원본 스코프와 Fabric Amper 고유 `: include` 문법을 분리 |
| **C6 — 안전한 브릿지** | [SnakeYAML Engine LoadSettingsBuilder](https://javadoc.io/static/org.snakeyaml/snakeyaml-engine/2.10/org/snakeyaml/engine/v2/api/LoadSettingsBuilder.html), [Gradle Incremental Builds](https://docs.gradle.org/current/userguide/incremental_builds.html) | AST/DTO 변환, anchor·alias 금지, 작업 입력/출력과 캐시 조건 정의 |

**검증 범위:** 여섯 트랙 모두 공식 문서·공개 소스에 대한 **읽기 전용 조사**를 완료했다. 여기에서의 '검증'은 문서·소스 대조에 한하며 Fabric Amper를 직접 컴파일하거나 실행해 검증한 결과가 아니다. 이후 추가 조사에서는 **Loom 1.19.4 배포 sources JAR의 주요 Java 파일 7개와 GitHub `1.19` 태그 원본이 바이트 단위로 일치**하고, 배포 바이너리의 플러그인 descriptor와 주요 class symbols가 존재함을 확인했다. 다만 **모든 bytecode 메서드 디스크립터·실제 실행 동작**의 동치는 검증하지 않았다(`5` 참고).

**지원 버전 후보**(공식 게시 확인과 실행 검증은 별개):

| 항목 | 초기 고정 후보 | 근거/상태 |
| --- | --- | --- |
| Minecraft / Java | `26.3` / Java `25` | Fabric 26.3 공식 예제 `options.release = 25` |
| Fabric Loader | `0.20.1` | 2026-11 공식 26.3 예제·Fabric Maven 게시 |
| Fabric API | `0.165.2+26.3` | 2026-11 공식 26.3 예제·Fabric Maven 게시 |
| Loom | `1.18.4` | 공식 Maven 게시, 통합 검증 필요 |
| Gradle Wrapper | `9.7.0` | 초기 호환성 후보, 통합 검증 필요 |
| Kotlin Gradle plugin(선택) | `2.4.21` | Fabric Language Kotlin 조합 기준 후보 |
| Fabric Language Kotlin(선택) | `1.14.2+kotlin.2.4.21` | FabricMC README 표기 |

2026-11-11 현재 **Gradle 9.9.1**(11월 10일 공개)과 **Loom 1.19.4**(11월 6일 게시)도 있다. 출시는 이 최신 안정 조합과 초기 고정 조합의 **두 독립 fixture**로 검증한 다음 선택한다. Kotlin Gradle plugin 2.4.21의 공식 완전 지원 범위는 Gradle 9.7.0까지이므로 Gradle 9.9.1과의 호환은 별도 검증한다. 26.3 출시 당시 Fabric 공식 권고는 Loom 1.17/Gradle 9.6.0이었다. 게시 버전만 확인한 조합을 테스트 통과라고 쓰지 않는다.

**첫 통합 시험의 우선순위:** 가장 보수적인 공식 문서 기반은 `Gradle 9.6.0 + Loom 1.17.21 + Kotlin Gradle Plugin 2.4.21 + JDK 25 + Minecraft 26.3`다. 중간 `9.7.0/1.18.4` 및 최신 `9.9.1/1.19.4`는 각각 독립적으로 테스트한다. 이 세 조합 모두 **현재까지 실제 통합 빌드 결과는 없음**.

**추가 호환성 조사(2026-11-11):**

| 프로파일 | Gradle / Loom / Kotlin | 문서 근거 및 판정 |
| --- | --- | --- |
| A — 26.3 출시 권고 중심 | `9.6.0` / `1.17.21` / `2.4.21` | Fabric 출시 안내는 Gradle 9.6.0·Loom 1.17 계열, Kotlin 공식 표는 Gradle 9.6.0 지원. **세 제품 동시 빌드 미실행** |
| B — 초기 구현 후보 | `9.7.0` / `1.18.4` / `2.4.21` | Kotlin의 공식 완전 지원 범위 안. **해당 Loom 바이너리와의 조합 미실행** |
| C — 최신 안정 탐색 | `9.9.1` / `1.19.4` / `2.4.21(선택)` | KGP 2.4.21은 Gradle 9.9.1을 **공식 완전 지원하지 않음**. Java-only도 Loom/Gradle 실제 동작 확인 필요 |

**1.19.4의 공식 배포 메타데이터에서 최소 Gradle API 확인:** Fabric Maven의 [`fabric-loom-1.19.4.module`](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.4/fabric-loom-1.19.4.module)에는 API·runtime variants 모두 `org.gradle.plugin.api-version="9.8.0"`이 기록되어 있고, `createdBy.gradle.version="9.8.0"` 및 `org.gradle.jvm.version=25`도 존재한다. 즉 **Gradle variant 해석 기준의 요구 API 버전은 9.8.0**이다. POM에는 이 속성이 없으므로 POM만 보고 9.7에서의 호환을 주장하면 안 된다. Loom 1.19.4를 Gradle 9.7.0에 결합하는 구상은 **배포 metadata와 충돌한다**. 동시에 Kotlin Gradle plugin 2.4.21의 공식 완전 지원 상한은 Gradle 9.7.0이므로, **Loom 1.19.4와 KGP 2.4.21을 둘 다 공식 지원 범위에서 함께 사용하는 Gradle 버전은 없다**. 이는 실제 실행이 반드시 실패한다는 뜻은 아니지만, 최신 조합 C를 공식 검증 완료라고 주장할 수 없다는 의미다. [Fabric 26.3](https://fabricmc.net/2026/09/15/263.html), [Kotlin KGP 호환성](https://kotlinlang.org/docs/gradle-configure-project.html), [Loom 배포 목록](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.4/).

## 2. 소비자 프로젝트와 부트스트랩

```text
my-mod/
├── settings.gradle.kts       # Gradle bootstrap
├── mod.yaml                  # 유일한 모드 빌드 설정
├── gradlew, gradlew.bat, gradle/wrapper/
├── src/main/java/...
├── src/main/kotlin/...       # Kotlin 활성화한 경우
├── src/main/resources/
│   ├── assets/example_mod/icon.png
│   ├── example.mixins.json
│   ├── example.client.mixins.json
│   └── example.classtweaker
└── src/client/java|kotlin/... # client 분리 시
```

공개 Plugin Portal 및 전이 의존성 해석이 구성된 경우 `settings.gradle.kts`의 목표 최소형:

```kotlin
plugins {
    id("dev.fabric-amper.settings") version "0.1.0"
}
```

Plugin Portal만으로 Fabric Loom의 전이 의존성을 해결할 수 없는 배포 환경에서는 이 파일의 **부트스트랩 저장소**를 다음과 같이 추가한다:

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("dev.fabric-amper.settings") version "0.1.0"
}
```

위 두 코드는 **배포 이후**의 사용 형태다. 현재 플러그인이 발행되지 않았으므로 그대로 실행해 작동한다는 의미가 아니다. 자체 개발에서는 `pluginManagement { includeBuild("relative-plugin-project") }`로 플러그인 프로젝트를 연결할 수 있다. 소비자 `build.gradle(.kts)`는 필요하지 않다.

## 3. 최종 `mod.yaml` 전체 예시 (Minecraft 26.3)

아래 `product: fabric/mod` 및 `minecraft`/`mod` 구조는 공식 Amper `module.yaml`을 참고해 **새로 정의하는 스키마**다. 의존성 샘플 중 `com.example`, `example-dependency`는 자리표시 좌표이며 게시 여부는 확인되지 않았다.

```yaml
schema: 1
product: fabric/mod

mod:
  id: example_mod
  version: "1.0.0+26.3"
  group: com.example
  archivesName: example-mod
  name: "Example Mod"
  description: "Fabric Amper를 사용하는 예시"
  environment: "*"
  license: MIT
  authors: ["Example Developer"]
  contributors: []
  contact:
    homepage: "https://example.org"
    sources: "https://example.org/source"
    issues: "https://example.org/issues"
  icon: "assets/example_mod/icon.png"
  provides: []
  entrypoints:
    main:
      - adapter: kotlin
        value: com.example.ExampleMod
    client:
      - adapter: kotlin
        value: com.example.ExampleModClient
  mixins:
    - config: example.mixins.json
      environment: "*"
    - config: example.client.mixins.json
      environment: client
  accessWidener: example.classtweaker
  depends:
    minecraft: "26.3"
    fabricloader: ">=0.20.1"
    java: ">=25"
    fabric-api: ">=0.165.2"
    fabric-language-kotlin: ">=1.14.2"
  recommends: {}
  suggests: {}
  conflicts: {}
  breaks: {}
  custom: {}

minecraft:
  version: "26.3"
  mappings: mojang
  loader: "0.20.1"
  fabricApi: "0.165.2+26.3"

settings:
  java:
    release: 25
    sourcesJar: true
  kotlin:
    enabled: true
    pluginVersion: "2.4.21"
    languageVersion: "2.4"
    fabricLanguageKotlin: "1.14.2+kotlin.2.4.21"
  loom:
    splitEnvironmentSourceSets: true
  resources:
    failOnExistingFabricModJson: true

# Optional Fabric Architectury API switch. 26.3 default verified on 2026-11-16.
architectury:
  enable: true
  # version: "22.0.5"  # set explicitly only to override the pinned default

repositories:
  - mavenCentral
  - fabric
  - modrinth

dependencies:
  - "com.google.code.gson:gson:2.13.2": compile-only
  - "com.example:embedded-library:1.0.0": include
  - maven: "com.example:runtime-helper:1.0.0"
    kind: library
    scope: runtime-only
  - modrinth:
      project: example-dependency
      version: "mc26.3-1.0.0"
    scope: compile-only
    target: client
    exported: false
    include: false

test-dependencies:
  - "org.junit.jupiter:junit-jupiter:6.0.0": all
```

`mod.depends`는 **Fabric Loader의 실행 시 필수 모드 관계**, `dependencies`는 **Gradle 클래스패스/패키징 관계**다. 둘의 의미가 다르므로 서로 대체하지 않는다. Modrinth 좌표의 게시·게임 버전 호환 여부는 의존성 해석 및 수락 테스트에서 확인한다.

## 4. 스키마 세부 계약

| 위치 | 타입 / 기본값 | 필수 검증 |
| --- | --- | --- |
| `schema` | 정수 `1`, 필수 | 알 수 없는 버전 거부 |
| `product` | 문자열 `fabric/mod`, 필수 | 다른 product 거부 |
| `mod.id` | 문자열, 필수 | Fabric Amper 정책: 소문자 ASCII 시작, 소문자·숫자·`_`·`-`, 길이 2~64. Loader 일반 설명보다 엄격한 자체 규칙 |
| `mod.version` | 비어 있지 않은 문자열, 필수 | 확장 SemVer 권장. Loader가 일반 문자열도 허용하지만 버전 범위 비교가 제한됨 |
| `mod.group` | Gradle group 문자열, 선택 | `Project.group` 설정; JSON에는 넣지 않음 |
| `mod.archivesName` | JAR 기본 이름 문자열, 선택(기본 id) | `BasePluginExtension.archivesName` 설정; JSON에는 넣지 않음 |
| `mod.environment` | `"*"` / `client` / `server`, 기본 `"*"` | Fabric Loader v1의 세 가지 지정 문자열 중 하나; `client`는 전용 서버에서 로드되지 않음 |
| `mod.authors/contributors` | 문자열 또는 `{name,contact}` 객체 배열, 기본 [] | 사람 이름 필수, contact 키별 타입 검사 |
| `mod.provides` | mod ID 문자열 배열, 기본 [] | 중복·자기 ID·모드 ID 충돌 검사 |
| `mod.license` | 문자열 또는 문자열 배열, 선택 | 원래 형식 유지, 빈 문자열 거부 |
| `mod.icon` | PNG 리소스 경로 문자열 또는 `{ "32": "icons/32.png", "128": "icons/128.png" }` 맵 | 키는 양의 정수 크기 문자열, 실제 PNG 리소스 확인 |
| `mod.contact` | 문자열 맵, 기본 {} | `homepage`/`issues`는 HTTP(S), `email`은 이메일, `sources`는 VCS URL 등을 허용; 확장 키도 보존 |
| `mod.entrypoints` | 문자열 또는 `{adapter,value}`의 배열을 값으로 갖는 맵 | adapter/값 존재 확인; 기본/타 언어 adapter의 정적 호출 문법 수용 |
| `mod.languageAdapters` | language adapter ID → 구현 클래스명 맵, 기본 {} | Fabric Loader v1 선택 필드, Kotlin 기본 adapter는 FLK가 제공 |
| `mod.mixins` | 파일명 또는 `{config, environment}` 객체 배열 | 리소스 파일 존재/환경 검증 |
| `mod.accessWidener` | 문자열 리소스 상대 경로, 선택 | 실제 `.classtweaker` 또는 지원되는 변환 리소스 확인 |
| `mod.depends/recommends/suggests/conflicts/breaks` | mod id → Loader 버전 조건 문자열 또는 문자열 배열 맵 | 배열의 OR 의미를 보존; Gradle 좌표와 독립 |
| `mod.custom` | JSON 호환 맵, 기본 {} | 임의 객체·YAML 태그 금지 |
| `minecraft.version` | 문자열, 필수 | 고정 Minecraft 버전 |
| `minecraft.mappings` | `mojang` 또는 `yarn` / `{type,version}` | 대상 Minecraft와 호환 |
| `minecraft.loader` | 문자열, 필수 | Loader 좌표에 사용 |
| `minecraft.fabricApi` | 문자열, 선택 | 있으면 Fabric API 의존성 추가 |
| `settings.java.release` | 정수, 기본 프로파일에서 25 | 26.3이면 25 강제 |
| `settings.java.sourcesJar` | boolean, 기본 true | Gradle Java Extension 반영 |
| `settings.kotlin.enabled` | boolean, 기본 false | 활성화 시 KGP/FLK/adapter 정합성 |
| `architectury.enable` (`enabled` 별칭) | boolean, 기본 false | Fabric Architectury API 활성화; 두 철자를 동시에 쓰면 FA003 |
| `architectury.version` | 정확한 고정 버전 문자열, 선택 | Minecraft 26.3에서 생략 시 `22.0.5`, 명시 시 해당 버전을 사용; 활성화 없이 version만 쓰면 FA003 |
| `settings.kotlin.pluginVersion` | 문자열, 내장 호환 버전 | 임의 외부 plugin 버전 교체 금지 |
| `settings.kotlin.fabricLanguageKotlin` | 고정 좌표 버전 | Kotlin adapter 사용 시 필수 |
| `settings.loom.splitEnvironmentSourceSets` | boolean, 기본 false | client 소스셋 및 mods 결합 |
| `settings.resources.failOnExistingFabricModJson` | true 고정 | false 요청 시 오류 |
| `repositories` | `fabric`, `mavenCentral`, `modrinth`, `{maven: https-url}` 배열 | 중복 제거, HTTPS만 |
| `dependencies`, `test-dependencies` | 의존성 배열, 기본 [] | 아래 정규화 규칙; client 전용 구성 시 `target` 분기 |

`mod.name`, `description`, `license`, `icon`, `authors`, `contributors`, `contact`, `entrypoints`, `languageAdapters`, `mixins`, `provides`, `depends`, `recommends`, `suggests`, `conflicts`, `breaks`, `custom`을 Fabric Loader schemaVersion 1의 같은 의미로 출력한다. `mod` 안의 빌드 전용 `group`·`archivesName`은 출력에서 제외한다. `icon`과 mixin config, access widener/class tweaker 파일 경로는 최종 JAR의 루트 기준이다.

**Gradle Project 메타데이터 동기화:** 설정 시 `project.version = mod.version`을 반드시 지정하고 `project.group = mod.group`(설정 시), `extensions.configure(BasePluginExtension::class.java) { it.archivesName.set(mod.archivesName ?: mod.id) }`를 적용한다. 그렇지 않으면 생성 JSON은 `1.0.0+26.3`인데 실제 아카이브 파일명이 `unspecified`를 포함하는 식으로 불일치할 수 있다. 배포 기본 파일명은 `<archivesName>-<version>.jar`를 목표로 하되 Loom의 실제 `jar`/`remapJar` 및 `sourcesJar` 결과에서 확인한다. [Gradle Java Plugin](https://docs.gradle.org/current/userguide/java_plugin.html), [BasePluginExtension](https://docs.gradle.org/current/javadoc/org/gradle/api/plugins/BasePluginExtension.html).

`entrypoints`의 `value`에 단순 클래스명뿐 아니라 Fabric Loader가 지원하는 `pkg.Class::method` 등의 표현이 올 수 있으므로 임의 Java 클래스명 정규식으로 제한하지 않는다. Adapter 구현체가 평가하는 문자열 의미는 해당 adapter의 규약을 따르며, YAML 브릿지는 공백·빈 값·누락 등 공통 구조만 검증한다. `languageAdapters`는 Fabric Loader 고유 옵션이고 `kotlin` adapter를 직접 중복 등록하지 않는다. [Loader v1 원본 규격](https://wiki.fabricmc.net/documentation:fabric_mod_json_spec).

Loader 버전 조건에는 `*`, `>=`, `<`, 정확 버전과 복수 조건의 문자열 배열이 있다. **배열은 하나라도 만족하면 참(OR)**이므로 이를 문자열 하나로 변환하지 않는다. `mod.depends.java: ">=25"`와 `minecraft.version: "26.3"`의 정합성을 확인한다. [Loader 공식 사양](https://docs.fabricmc.net/develop/loader/fabric-mod-json).

**YAML→Loader 관계의 불변식:**

- **기본 병합 정책(스키마 1 결정):** `mod.depends`가 생략됐거나 특정 항목이 빠졌으면 `minecraft.version` → `minecraft: "<version>"`(정확 버전), `minecraft.loader` → `fabricloader: ">=<version>"`, `settings.java.release` → `java: ">=<release>"`를 자동 채운다. `minecraft.fabricApi`가 있으면 `fabric-api: ">=<declared-api-semver>"`, Kotlin을 쓰면 `fabric-language-kotlin: ">=<declared-flk-version>"`도 채운다. 사용자 명시 항목은 **그대로 유지**하고 선택한 실제 빌드 버전을 허용하는지 Loader 버전 규약으로 검증한다. 충돌하면 **실패**하며 강제로 덮어쓰지 않는다. 자동 생성 항목과 사용자 작성 항목의 출처·최종 합성을 진단에 표시한다.
- `minecraft.fabricApi`를 추가했다면 Gradle 의존성 외에 `mod.depends.fabric-api`도 존재·호환성을 확인한다. Kotlin adapter를 활성화했다면 `net.fabricmc:fabric-language-kotlin` Gradle 의존성과 `mod.depends.fabric-language-kotlin` 모두 있어야 한다. 버전 범위가 실제 선택 버전을 허용하는지 검사한다. 단 `+26.3` 같은 SemVer build metadata는 Loader 버전 범위 비교에서 무시되므로 **Minecraft 대응 배포 파일 자체의 정합성을 따로 확인**한다.
- Loader 버전 제약 문자열의 공백으로 연결된 여러 조건(예 `">=0.20.1 <0.21"`)은 **AND**이고, 배열의 각 요소(예 `[">=0.20.1 <0.21", ">=0.22"]`)는 **OR**이다. 숫자로 변환될 위험이 있는 `26.3`과 YAML alias로 해석될 `*`는 반드시 따옴표로 묶어야 한다. 비SemVer 버전은 Loader의 비교 가능한 범위가 제한되므로 임의 크기 비교를 강제하지 않는다.
- `mod.environment`는 **모드 전체의 로드 대상**을 결정한다. `entrypoints.client`·`mixins.environment:client`·Gradle 의존성 `target:client`와는 별개다. `environment:"*"`인 모드가 `mod.depends`에 클라이언트 전용 모드를 필수로 선언하면 전용 서버에서도 그 모드를 요구할 수 있다. `mod.depends` 항목별 `environment`는 Loader v1 형식이 아니며 `mod.client`라는 최상위 필드를 생성하지 않는다.
- `mod.provides`는 **모드 ID 별칭**으로서 Gradle `compileOnly`나 Maven 'provided'와 무관하다. 빌드 시 자기 ID·중복은 검사하지만 다른 설치 모드와의 충돌은 Loader 실기동에서 확인한다. `license` 배열은 메타데이터 의미를 보존하며 자동으로 `LICENSE` 파일을 JAR에 넣는 것으로 간주하지 않는다.

### 버전별 매핑 규칙

**Minecraft 26.1 이상**: `net.fabricmc.fabric-loom`. `mappings: mojang`은 내장 공식 이름을 의미하며 Gradle `mappings` 구성에 아무것도 추가하지 않는다. `mappings: yarn` 또는 `mappings` 외부 좌표는 **오류**. `jar`가 배포 대상.

**Minecraft 1.21.11 이하**: `net.fabricmc.fabric-loom-remap`. `mappings: mojang`이면 `loom.officialMojangMappings()`를 `mappings`에 추가. `mappings: {type: yarn, version: "1.21.1+build.3"}`이면 `net.fabricmc:yarn:1.21.1+build.3:v2`를 추가. 실제 Yarn 버전 존재 여부는 Maven 해석 단계에서 확인. `remapJar`가 배포 대상. `fabric-loom` 레거시 ID는 신규 빌드에서 사용하지 않는다. 버전별 분기는 문자열 사전순 비교를 하지 않고 검증된 버전 분류로 수행한다.

### 의존성 구문 / 의미

#### Fabric Architectury API 간편 활성화 (develop 선행 기능)

`architectury`는 **최상위** 선택 설정이며 Gradle `build.gradle(.kts)`나 `gradle.properties`를 요구하지 않는다. `architectury: {enable: true}`가 정식 예이고 `enabled`는 Kotlin 설정과 철자를 맞춘 동일 의미의 별칭이다. 둘을 동시에 쓰거나 알 수 없는 키·잘못된 boolean을 사용하면 FA003으로 실패한다. `enable` 기본값은 false이며 버전만 지정하고 비활성화하면 오류다. 활성화 시 고정 `version`을 주지 않으면 **2026-11-16에 검증된 Minecraft 26.3 Fabric용 최신 `22.0.5`를 빌드에 고정**한다. 이는 빌드 중 동적으로 Maven `latest.release`를 찾는 기능이 아니며, 새로운 배포가 나와도 기존 프로젝트가 자동 변경되지 않도록 플러그인 업데이트로 기본 버전을 갱신한다. 사용자 `version`은 이 기본값을 덮어쓰며 `+`/`latest.release` 같은 Gradle 동적 선택자를 받지 않는다.

```yaml
minecraft:
  version: "26.3"
  loader: "0.20.1"
  fabricApi: "0.165.2+26.3"
architectury:
  enable: true
  # version: "22.0.5"
```

활성화할 때는 검증된 공식 `https://maven.architectury.dev/` 저장소를 추가하고 `dev.architectury:architectury-fabric:<resolved-version>`을 `kind=mod`, `scope=all`, `target=main`, `include=false`, `exported=false`로 정규화하여 다른 Fabric Amper 의존성과 동일하게 Gradle에 추가한다. 26.3 비리매핑 프로젝트에서는 `implementation`으로 해석하며 직접 `dependencies`/`repositories`에 복사할 필요가 없다. 단일 설정으로 실행 가능한 의존성을 충족하기 위해 `minecraft.fabricApi`를 사용자가 작성하지 않았을 때는 검증된 26.3 기본 `0.165.2+26.3`을 선택하여 Fabric API Gradle/Loader 의존성도 제공한다. 이미 작성했다면 **사용자가 선택한 Fabric API 버전을 유지**한다. `fabric.mod.json`에는 `architectury: ">=<resolved-version>"`을 누락 시 자동 추가하고 사용자가 적은 `mod.depends.architectury`는 변경하지 않으며 실제 선택 버전과 충돌할 경우 FA009를 표시한다.

현재 사전검증된 기본값은 **26.3 전용**이다. Architectury 활성화 후 `minecraft.version`이 `26.3`이 아닌 경우 기본 버전 재사용을 금지하며 FA003로 거부한다(명시적인 버전도 현재 개발 범위에서는 지원하지 않는다). `dependencies`에 동일 Maven 그룹·아티팩트를 직접 중복 지정하면 자동 추가와 충돌할 수 있으므로 FA012를 반환하며 둘 중 하나만 쓰도록 안내한다. Fabric API/Architectury API 런타임 모드는 외부 설치 대상이다. `include=true`로 Jar-in-Jar 패키징하지 않고, 멀티플랫폼 공통 소스셋이나 Architectury Loom 플러그인을 자동 생성하지 않는다. 일반 YAML 구성 변경과 마찬가지로 Gradle 설정 캐시의 입력으로 추적하여 IntelliJ Gradle Reload/consumer 빌드 때 일관되게 재해석한다.

**증빙 출처:** [Architectury 공식 26.3 Setup](https://docs.architectury.dev/api/getting-started/setup/)은 `22.0.5`, `dev.architectury:architectury-fabric:${architectury_api_version}`, 공식 Maven URL을 명시한다. [Architectury Maven 22.0.5 디렉터리](https://maven.architectury.dev/dev/architectury/architectury-fabric/22.0.5/)에서 실제 Fabric 배포물이 게시된 사실을 확인했다. IntelliJ 편집 보조 스키마는 루트 `fabric-amper.schema.json`의 `architectury` 객체와 boolean 자동완성, 정확한 버전 검증을 제공한다. 수락 시험은 enable=false/생략, 기본/명시 버전, 잘못된 키/타입/동적 버전, 비26.3, 수동 중복/Loader 충돌, 실제 Maven 의존성 해석·JAR metadata와 설정 캐시 재실행을 포함한다.

**Windows `init --architectury` 시작 템플릿 (develop 전용):** `fabric-amper.bat init <id> [new_folder] [mod|api] [--kotlin] [--splitEnvironmentSourceSets] [--architectury]` 또는 `init <id> --here [mod|api] ...`를 지원한다. 세 플래그는 위치 인자 **뒤에서 순서 무관**하게 최대 한 번씩 허용하고, 중복·이전 위치·알 수 없는 인자·초과 인자는 파일을 생성하기 전에 exit code 2로 거부한다. `--architectury`를 생략하면 기존 Java/Kotlin·split 템플릿 결과가 그대로 유지된다. 지정하면 생성 `mod.yaml` 최상위에 `architectury:\n  enable: true`를 넣고, 버전은 의도적으로 명시하지 않아 현재 개발 플러그인의 고정 기본값을 사용한다. 비분리 프로젝트는 `src/main/java|kotlin/org/example/ExampleMod`에서 `dev.architectury.platform.Platform.getMinecraftVersion()`을 호출하는 **단일 소스셋 전용 예제**를 생성한다. 분리 프로젝트는 `src/main` 공통 ModInitializer에 **common 전용 예제**, `src/client` ClientModInitializer에 **client 전용 예제**를 각각 생성한다. 모두 Architectury API를 직접 import하고 실제 API를 호출하되 서버 공통 코드에는 클라이언트 전용 Minecraft 클래스를 참조하지 않는다. 기존 `--kotlin`, `--here`, `api` 프로파일과 결합해도 언어·entrypoint·소스셋이 일치하고 `build.gradle(.kts)` 및 작성된 `fabric.mod.json`은 생성하지 않는다. TestKit은 생성한 **실제 예제 소스**를 현재 develop 바이너리 플러그인으로 Java/Kotlin·분리/비분리 빌드까지 확인해야 한다.

**develop 단계 배포 주의:** BAT가 생성하는 `settings.gradle.kts`는 기존의 공개 v0.1.3 JitPack 플러그인을 계속 가리키므로, 해당 공개 바이너리에는 `architectury` 파서가 아직 없다. 따라서 `--architectury` 출력물을 그대로 외부 공개 v0.1.3으로 빌드할 수 있다고 주장하지 않는다. develop의 TestKit에서 생성 Settings의 플러그인 선언만 현재 로컬 테스트 바이너리로 교체해 소비자 빌드를 검증한다. 이후 기능 출하 시 BAT의 생성 플러그인 버전을 새 릴리즈 태그로 업데이트해야 한다. main·v0.1.3 릴리즈 파일을 선행 변경하지 않는다.

```yaml
dependencies:
  - "com.example:normal:1.0.0"               # all
  - "com.example:provided:1.0.0": compile-only
  - "com.example:packed:1.0.0": include
  - "com.example:public-api:1.0.0": exported
  - maven: "com.example:full:1.0.0"
    scope: all
    exported: false
    include: false
  - modrinth:
      project: sodium
      version: "exact-published-version"
    scope: compile-only
```

정규화 모델 `DependencySpec(kind, coordinate, scope, target, exported, include)`:

1. 문자열 `group:artifact:version` → `kind=library, scope=all, target=main, exported=false, include=false`. 일반 Java 라이브러리 축약형이다.
2. 정확히 하나의 좌표를 키로 갖는 맵 → `kind=library`, 값 `all`/`compile-only`/`runtime-only`/`exported`/`include` 중 하나. `exported`는 `all + exported=true`; `include`는 `all + include=true`.
3. 풀 객체는 `maven` 문자열 **또는** `modrinth: {project, version}` 정확히 하나. `kind`는 `library` 또는 `mod`이며 Maven은 `library`, Modrinth는 `mod`를 기본값으로 둔다. **Maven에 게시된 Fabric 모드**는 `kind: mod`를 명시한다. `scope` 기본 `all`, `target` 기본 `main`, boolean 기본 false.
4. `include`는 **Gradle `include` 구성에 추가**하는 별도 패키징 축이다. `: include`면 기본 `implementation` 및 `include` **양쪽**에 같은 아티팩트를 선언한다. `include` 단독 구성만으로 컴파일 가능하다고 가정하지 않는다.
5. `exported=true`는 `scope=all`에서만 허용하고 `java-library`의 `api` 구성으로 연결한다. `include=true`와 `compile-only`/`runtime-only`의 결합은 스키마 1에서 거부한다.
6. 좌표는 고정 버전 필수. `+`/`latest.release`/동적 범위/누락 버전 및 네트워크 URL 직접 JAR은 재현성 문제로 거부한다. 단 버전에 포함되는 `+` 문자(예: `0.165.0+26.3`)는 **문자열 일부이므로 허용**하고, 동적 Gradle `1.+`만 거부한다.
7. Modrinth는 `maven.modrinth:project:version`로 정규화하고 해당 그룹을 `https://api.modrinth.com/maven`에 한정한다. 서비스가 실제 게시한 version ID 또는 버전 문자열이 필요하다.
8. `target=client`는 `settings.loom.splitEnvironmentSourceSets=true`에서만 허용하며 26.3에서는 `clientImplementation` / `clientCompileOnly` / `clientRuntimeOnly`로 매핑한다. `target=client`에서 `exported=true`·`include=true` 조합은 스키마 1에서 금지한다. 구버전 remap에서의 `clientMod*` 구성은 **대상 버전 실제 Loom 구성 검증 뒤**에 지원한다. Loader `mod.depends`에는 개별 dependency의 `target`을 변환해 삽입하지 않는다.
9. 같은 좌표가 동일 target 내 모순되는 scope로 중복되면 실패한다. Modrinth Maven은 **전이적 의존성 메타데이터를 제공하지 않으므로** 필요한 2차 라이브러리도 별도 선언해야 한다. [Modrinth 공식 안내](https://support.modrinth.com/en/articles/8801191-modrinth-maven).

| 유형 | 26.3 기본 비리매핑 구성 | 1.21.11 이하 리매핑 구성 |
| --- | --- | --- |
| MC | `minecraft = com.mojang:minecraft:<game-version>` | 동일 `minecraft` |
| Loader | `implementation net.fabricmc:fabric-loader:<version>` | `modImplementation` |
| Fabric API | `implementation net.fabricmc.fabric-api:fabric-api:<version>` | `modImplementation` |
| `settings.kotlin.enabled=true` | `org.jetbrains.kotlin.jvm` + `implementation net.fabricmc:fabric-language-kotlin:<version>` | Kotlin Gradle plugin + `modImplementation net.fabricmc:fabric-language-kotlin:<version>` |
| `all` | `implementation` | `modImplementation` |
| `all + exported` | `api` | `modApi` |
| `compile-only` | `compileOnly` | `modCompileOnly` |
| `runtime-only` | `runtimeOnly` | `modRuntimeOnly` |
| `target=client,scope=all` | `clientImplementation` | 별도 verified `clientModImplementation` 지원 전까지 거부 |
| `target=client,scope=compile-only` | `clientCompileOnly` | 별도 verified 구성이 생기기 전까지 거부 |
| `target=client,scope=runtime-only` | `clientRuntimeOnly` | 별도 verified 구성이 생기기 전까지 거부 |
| `include=true` | 해당 scope **및** `include` → `jar` | 해당 scope **및** `include` → `remapJar` |
| `test-dependencies` | `testImplementation`/`testCompileOnly`/`testRuntimeOnly` | 동일, 별도 구버전 모드 매핑 필요 여부 확인 |

**표의 구버전 `mod*` 행은 `kind=mod`에만 적용한다.** 구버전이더라도 `kind=library`인 일반 Maven 라이브러리는 `implementation`/`api`/`compileOnly`/`runtimeOnly`를 사용한다. 26.3 비리매핑에서는 두 종류 모두 일반 Gradle 구성으로 연결한다. 예를 들어 `com.google.code.gson:gson`은 `kind=library`이고, Maven에 게시된 Fabric 모드라면 풀 객체에 `kind: mod`를 지정해 매핑 대상임을 알린다. Gradle 아티팩트의 그룹 이름만으로 모드인지 추측하지 않는다. Modrinth 버전이 다른 Loader용일 수도 있으므로 실제 배포 파일/metadata와 Minecraft 호환성 검사는 별도로 수행한다.

## 5. Settings/Project 주입 순서

```mermaid
flowchart TD
  A["Gradle 시작 / IntelliJ Gradle Sync"] --> B["settings.gradle.kts plugins 블록 해석"]
  B --> C["FabricAmperSettingsPlugin.apply(Settings)"]
  C --> D["mod.yaml 탐지 / rootProject.name 결정"]
  D --> E["gradle.lifecycle.beforeProject(IsolatedAction) 등록"]
  E --> F["루트 Project 평가 직전"]
  F --> G["FabricAmperProjectPlugin.apply(Project)"]
  G --> H["YAML strict parse / DTO validate"]
  H --> Q["구성 시점 metadata 미러 생성"]
  Q --> I{"Minecraft 26.1 이상?"}
  I -->|예| J["non-remapping Loom 적용 / mappings 생략"]
  I -->|아니오| K["remapping Loom 적용 / mappings 지정"]
  J --> L["Java/Kotlin, dependencies, repositories 설정"]
  K --> L
  L --> R["loom.fabricModJsonPath = metadata 미러"]
  R --> M["generateFabricModJson TaskProvider 등록"]
  M --> N["processResources 생성 output 연결"]
  N --> O["설정/Tooling 모델 완료"]
  O --> P["build: JSON 생성 → resources → classes → jar/remapJar"]
```

**구현 단계별 책임**: Settings plugin은 설정 디렉터리 `mod.yaml`/`mod.yml` 충돌·필수 필드를 확인하고 `rootProject.name` 및 callback만 설정한다. Callback은 루트 Project에 `dev.fabric-amper.project` 플러그인을 적용한다. Project plugin은 YAML 전체 파싱과 **설정 단계 metadata 미러 준비**를 거쳐 Loom/Java/Kotlin 설정, Gradle 의존성, 생성 리소스 태스크를 등록한다. Project 평가 이후에 뒤늦게 plugin을 적용하는 `afterEvaluate`를 기본 경로로 사용하지 않는다.

**Java 실행 환경:** Minecraft 26.3용 Gradle 데몬을 실행하는 JVM도 Java 25 이상이어야 한다. `settings.java.release: 25`와 `JavaPluginExtension.toolchain.languageVersion`은 **컴파일용 설정**이므로 이미 실행 중인 Gradle JVM을 바꿀 수 없다. IntelliJ의 Gradle JVM과 명령행 `java -version`을 검사하도록 진단을 설계한다. Kotlin 활성화 시 Java `options.release=25`와 Kotlin `compilerOptions.jvmTarget=25`를 일치시킨다. [Fabric 26.1 Java 25 안내](https://www.fabricmc.net/2026/03/14/261.html), [Kotlin JVM target 검증](https://kotlinlang.org/docs/gradle-configure-project.html).

IntelliJ Sync는 Gradle의 **구성 및 Tooling Model** 경로다. Sync만으로 생성 JSON 태스크·JAR 태스크가 실행되는 것은 아니므로 Sync 결과와 `build` 결과를 별도 검증한다. **`settings.gradle.kts`만 있고 `build.gradle(.kts)`가 없는 프로젝트를 IntelliJ가 자동으로 Gradle 프로젝트로 인식하는지, `mod.yaml` 저장을 자동 Sync 트리거로 보는지는 미확인**이다. 최초에는 IDE에서 Gradle로 명시적 열기/재동기화가 필요할 수 있다. configuration cache 무효화가 IntelliJ의 자동 Reload를 의미하는 것은 아니다. [JetBrains Gradle 프로젝트 가이드](https://www.jetbrains.com/help/idea/gradle.html), [Gradle 구성 캐시](https://docs.gradle.org/current/userguide/configuration_cache.html).

### Gradle API 시그니처 (Java API / Kotlin 호출 위치)

| 기능 | API 계약 | 출처 |
| --- | --- | --- |
| Settings 구현 | `Plugin<Settings>`의 `void apply(Settings target)` | [Plugin](https://docs.gradle.org/9.9.0/javadoc/org/gradle/api/Plugin.html) |
| Project 구현 | `Plugin<Project>`의 `void apply(Project target)` | [Plugin](https://docs.gradle.org/9.9.0/javadoc/org/gradle/api/Plugin.html) |
| lifecycle | `Gradle.getLifecycle(): GradleLifecycle`; `GradleLifecycle.beforeProject(IsolatedAction<? super Project>): void` (8.8+) | [GradleLifecycle](https://docs.gradle.org/9.9.0/javadoc/org/gradle/api/invocation/GradleLifecycle.html) |
| 레거시 훅 | `Gradle.beforeProject(Action<? super Project>): void` | [Gradle](https://docs.gradle.org/9.9.0/javadoc/org/gradle/api/invocation/Gradle.html) |
| Plugin 적용 | `PluginManager.apply(String pluginId): void` | [PluginManager](https://docs.gradle.org/9.9.0/javadoc/org/gradle/api/plugins/PluginManager.html) |
| 내부 플러그인 직접 적용 | `PluginManager.apply(Class<?> type): void` | [PluginManager](https://docs.gradle.org/9.9.0/javadoc/org/gradle/api/plugins/PluginManager.html) |
| Plugin 협업 | `PluginManager.withPlugin(String pluginId, Action<? super AppliedPlugin>): void` | [PluginManager](https://docs.gradle.org/9.9.0/javadoc/org/gradle/api/plugins/PluginManager.html) |
| Gradle 의존성 | `DependencyHandler.add(String configurationName, Object dependencyNotation): Dependency` | [DependencyHandler](https://docs.gradle.org/9.9.0/javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html) |
| 태스크 | `TaskContainer.register(String name, Class<T> type, Action<? super T>): TaskProvider<T>` | [TaskContainer](https://docs.gradle.org/9.9.0/javadoc/org/gradle/api/tasks/TaskContainer.html) |
| 생성 리소스 | `SourceSet.getResources(): SourceDirectorySet` / `SourceDirectorySet.srcDir(Object)` | [SourceSet](https://docs.gradle.org/9.9.0/javadoc/org/gradle/api/tasks/SourceSet.html) |
| 프로젝트 파일 | `project.layout.projectDirectory.file("mod.yaml")` | [ProjectLayout](https://docs.gradle.org/9.9.0/javadoc/org/gradle/api/file/ProjectLayout.html) |
| 입력 추적 | `providers.fileContents(RegularFile).asText` | [ProviderFactory](https://docs.gradle.org/9.9.0/javadoc/org/gradle/api/provider/ProviderFactory.html) |
| Loom Extension | `extensions.configure(LoomGradleExtension.class, Action)` 또는 `extensions.getByName("loom")` | [Fabric Loom 소스](https://github.com/FabricMC/fabric-loom) / **타입은 정확한 대상 JAR에서 컴파일 확인 필수** |
| Java toolchain | `JavaPluginExtension.getToolchain(): JavaToolchainSpec` → `getLanguageVersion(): Property<JavaLanguageVersion>` → `set(JavaLanguageVersion.of(25))` | [JavaPluginExtension](https://docs.gradle.org/current/javadoc/org/gradle/api/plugins/JavaPluginExtension.html), [JavaToolchainSpec](https://docs.gradle.org/current/javadoc/org/gradle/jvm/toolchain/JavaToolchainSpec.html) |
| Kotlin JVM 옵션 | KGP 적용 후 `KotlinJvmProjectExtension`의 `compilerOptions.jvmTarget: Property<JvmTarget>`, `JvmTarget.JVM_25` | [Kotlin JVM DSL](https://kotlinlang.org/api/kotlin-gradle-plugin/kotlin-gradle-plugin-api/org.jetbrains.kotlin.gradle.dsl/-kotlin-jvm-extension/), [compiler options](https://kotlinlang.org/docs/gradle-compiler-options.html) |
| Gradle 아카이브 이름 | `BasePluginExtension.getArchivesName(): Property<String>` | [BasePluginExtension](https://docs.gradle.org/current/javadoc/org/gradle/api/plugins/BasePluginExtension.html) |

**결정적 플러그인 해석 제약**: `project.pluginManager.apply("net.fabricmc.fabric-loom")`에는 버전 인자가 없다. `pluginManagement { plugins { ... } }`로 버전을 선언하는 일만으로 Project의 `beforeProject` callback에서 외부 plugin JAR가 다운로드되는 것은 아니다. Gradle 공식 문서는 설정 convention plugin에서 hook을 등록하거나 설정 `plugins { id("...") version "..." apply false }`로 플러그인 클래스를 먼저 해석하라고 안내한다. Fabric Amper의 단일 진입점 요구를 충족하려면 **Settings 플러그인 배포물의 전이 클래스패스에 Loom 및 선택 Kotlin Gradle plugin 구현을 포함**하고 Project에서 보이는지 TestKit 검증을 통과해야 한다. 실패하면 배포 불가, 사용자가 추가 설정을 적어야 한다고 조용히 사양을 변경하지 않는다.

**적용 클래스 선정:** Fabric Amper 내부 클래스 `FabricAmperProjectPlugin`은 `project.pluginManager.apply(FabricAmperProjectPlugin::class.java)`를 권장한다. 그 내부에서는 `net.fabricmc.fabric-loom` **ID**를 사용한다. Class 기반 외부 적용을 선택한다면 26.3은 `LoomNoRemapGradlePlugin`이어야 하며 `LoomGradlePlugin` 공통 구현 클래스를 직접 적용하면 레거시 경로와 혼동될 수 있다. 외부 플러그인의 클래스패스가 누락되면 String/Class 어느 방법도 그 문제를 해결하지 않는다. [Loom plugin marker 매핑](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/build.gradle), [Gradle Isolated Projects 설명](https://docs.gradle.org/current/userguide/isolated_projects.html#sec:applying_plugins_from_a_lifecycle_callback).

또한 [Gradle의 binary plugin 의존성 경고](https://docs.gradle.org/9.9.0/userguide/implementing_gradle_plugins_binary.html#sec:plugin_dependencies)에 따라 Loom/KGP 구현 JAR을 `implementation` 종속성으로 배포하면 다른 플러그인 버전을 덮어쓸 수 있다. **초기 스키마 1은 단일 프로젝트·고정된 외부 플러그인 조합으로 제한**하며, 다중 프로젝트와 외부 버전 혼용은 지원 범위 밖으로 둔다. `compileOnly`로 바꾸면 소비자가 외부 플러그인을 선적용해야 하므로 Settings 플러그인 하나만 사용하는 제품 요구를 충족하지 못한다.

따라서 YAML의 `settings.kotlin.pluginVersion`은 **Fabric Amper 배포 시점에 번들된 KGP 버전과 일치할 때만** 허용한다. 향후 Loom 버전도 Gradle 플러그인 해석이 끝난 뒤 YAML만으로 임의 다운로드할 수 있다고 주장하지 않는다. 게임·Loader·Fabric API·모드 라이브러리 버전과 **빌드 플러그인 버전**을 구별한다.

**선택적 Kotlin 플러그인 클래스패스의 비용:** Settings 플러그인의 `implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:<pin>")`은 Java-only 프로젝트에서도 KGP를 **플러그인 배포 전이 클래스패스로 해석**하므로 `settings.kotlin.enabled=false`가 'KGP 미다운로드'를 의미하지 않는다. 단일 Settings plugin ID를 유지하면서 Kotlin 사용 시점에만 classpath를 올리는 **실험적 대안**은 Project plugin의 `buildscript.repositories` + `buildscript.dependencies.add("classpath", "org.jetbrains.kotlin:kotlin-gradle-plugin:<pin>")`, 그 뒤 `pluginManager.apply("org.jetbrains.kotlin.jvm")`이다. [Gradle buildscript 적용법](https://docs.gradle.org/current/userguide/plugins_intermediate.html), [Fabric Language Kotlin 자체 빌드 예](https://github.com/FabricMC/fabric-language-kotlin/blob/master/build.gradle). **일반 build.gradle의 이 패턴이 `beforeProject`의 binary plugin에서도 동작한다는 보장은 확인되지 않았다.** 따라서 v1 기본은 단일 버전 묶음의 정적 전이 해석을 출하 게이트로 두고, 지연 로딩은 별도 TestKit에서 명확히 통과할 때에만 대체한다. Kotlin API 타입을 `ProjectPlugin`에 직접 링크할 경우 없는 클래스패스에서 `NoClassDefFoundError`가 날 수 있으므로 지연 로딩 실험에서는 Kotlin 전용 어댑터 클래스 분리/Toolchain 기반 jvmTarget 상속을 검토한다.

공식 26.3 예제의 Loom API 호출: `loom.splitEnvironmentSourceSets()`, `loom.mods { "<id>" { sourceSet(sourceSets.main); sourceSet(sourceSets.client) } }`. 바이너리 플러그인은 Kotlin DSL accessor `loom { }`가 아니라 Loom Extension의 **실제 공개 인터페이스와 메서드**를 이용한다. `LoomGradleExtension`/`LoomGradleExtensionAPI` 클래스명·`mods` container 설정·`sourceSet` 시그니처는 **해당 Loom JAR을 대상으로 컴파일 TestKit으로 확인**한다. 이 문서의 Groovy DSL 표현을 JVM 메서드 시그니처로 오인하지 않는다.

**Class Tweaker에 필요한 Loom 주입:** `mod.accessWidener`가 지정되면 생성 JSON의 `accessWidener` 필드에 기록하는 동시에 Loom Extension의 `accessWidenerPath`를 실제 원본 리소스 `src/main/resources/<relative-file>`에 연결한다. 26.3 `.classtweaker`의 첫 줄은 `classTweaker v1 official`이어야 한다. 대상 Loom에서 제공하는 `validateAccessWidener` 실행을 별도 수락 시험으로 추가한다. [Fabric 공식 Class Tweakers](https://docs.fabricmc.net/develop/class-tweakers/).

### Loom 내부 소스 기반 상세 호출 계약 (dev/1.19 분기)

| API·구현 클래스 | 공개/관찰된 시그니처 및 실행 영향 | 1차 소스 |
| --- | --- | --- |
| Plugin ID → 구현체 | `net.fabricmc.fabric-loom` → `net.fabricmc.loom.LoomNoRemapGradlePlugin`, `net.fabricmc.fabric-loom-remap` → `LoomRemapGradlePlugin` | [build.gradle plugin marker](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/build.gradle) |
| `LoomNoRemapGradlePlugin.apply(Project)` | 내부에서 공통 `LoomGradlePlugin` 적용; `java-library`와 `eclipse` 플러그인 및 `loom` 확장 등록 | [LoomGradlePlugin.java](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/src/main/java/net/fabricmc/loom/LoomGradlePlugin.java) |
| `LoomGradleExtensionImpl(Project, LoomFiles)` | 비리매핑 플러그인 적용 여부로 `dontRemap`/`disableObfuscation` 결정; 비리매핑에서 `getMappingConfiguration()` 호출 시 예외 | [LoomGradleExtensionImpl.java](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/src/main/java/net/fabricmc/loom/extension/LoomGradleExtensionImpl.java) |
| `void splitEnvironmentSourceSets()` | 호출 즉시 `MinecraftSourceSets.evaluateSplit(project)`로 `client` 소스셋을 생성 | [LoomGradleExtensionApiImpl.java](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/src/main/java/net/fabricmc/loom/extension/LoomGradleExtensionApiImpl.java), [MinecraftSourceSets.java](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/src/main/java/net/fabricmc/loom/configuration/providers/minecraft/MinecraftSourceSets.java) |
| `void mods(Action<NamedDomainObjectContainer<ModSettings>>)` | 모드 ID별 소스셋 그룹 구성, 해당 모드의 `main` 및 `client` 등록 | [LoomGradleExtensionAPI.java](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/src/main/java/net/fabricmc/loom/api/LoomGradleExtensionAPI.java) |
| `ModSettings.sourceSet(SourceSet)` / `sourceSet(String)` | 지정 모드에 속한 소스셋 추가 | [ModSettings.java](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/src/main/java/net/fabricmc/loom/api/ModSettings.java) |
| `RegularFileProperty getFabricModJsonPath()` | Loom의 **injected interface 처리용** 메타데이터 파일 경로 API. 일반 Loader JSON 출력 경로 지정 기능으로 오해하지 않는다 | [LoomGradleExtensionAPI.java](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/src/main/java/net/fabricmc/loom/api/LoomGradleExtensionAPI.java), [Loom options](https://docs.fabricmc.net/develop/loom/options) |
| `LoomConfigurations.run()` | 비리매핑에서는 `mappings` 구성을 생성하지 않음 | [LoomConfigurations.java](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/src/main/java/net/fabricmc/loom/configuration/LoomConfigurations.java) |
| `RemapTaskConfiguration.run()` | 비리매핑이면 `NonRemappedJarTaskConfiguration.configure()`로 분기하고 `remapJar`를 생성하지 않음; `include`가 `jar`에 연결됨 | [RemapTaskConfiguration.java](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/src/main/java/net/fabricmc/loom/task/RemapTaskConfiguration.java), [NonRemappedJarTaskConfiguration.java](https://github.com/FabricMC/fabric-loom/blob/dev/1.19/src/main/java/net/fabricmc/loom/task/NonRemappedJarTaskConfiguration.java) |

이 호출 계약은 **공개된 `dev/1.19` 소스를 직접 대조한 정적 근거**다. 기존 작성 코드에 있는 `LoomGradleExtension`이라는 단순 타입명은 정확한 클래스라고 보증할 수 없으므로 구현 시 `LoomGradleExtensionAPI`(인터페이스)와 `LoomGradleExtensionImpl`(구현체)의 의존성 경계를 구분한다. `splitEnvironmentSourceSets()`를 호출한 다음 `loom.mods`에 `main`과 `client`를 연결한다.

**Loom 1.19.4 배포 소스에 나타난 구성 시점 파일 접근:** `FabricModJsonHelpers.getModsInProject(Project)`는 `loom.fabricModJsonPath`가 지정되면 `FabricModJsonFactory.createFromFile(...)`로 그 파일을 즉시 읽고, 없으면 `src/main/resources/fabric.mod.json`(split 시 client 포함)을 탐색한다. `MinecraftJarProcessorManager.create` → `DeobfSpecContext.create` → `projectView.getMods()` 경로는 프로젝트 평가 성공 시점, 즉 태스크 실행 전에 호출된다. `FabricModJsonFactory`는 `Files.newBufferedReader`로 디스크를 읽는다. `LoomGradleExtensionApiImpl.getModVersion()`도 로컬 metadata 누락 시 예외를 던질 수 있다. 따라서 **`processResources` 태스크에서만 JSON을 생성하는 이전 설계는 Loom의 모든 요구를 충족하지 않는다.** 근거: [FabricModJsonHelpers.java](https://github.com/FabricMC/fabric-loom/blob/1.19/src/main/java/net/fabricmc/loom/util/fmj/FabricModJsonHelpers.java), [FabricModJsonFactory.java](https://github.com/FabricMC/fabric-loom/blob/1.19/src/main/java/net/fabricmc/loom/util/fmj/FabricModJsonFactory.java), [DeobfSpecContext.java](https://github.com/FabricMC/fabric-loom/blob/1.19/src/main/java/net/fabricmc/loom/configuration/processors/speccontext/DeobfSpecContext.java), [LoomGradleExtensionApiImpl.java](https://github.com/FabricMC/fabric-loom/blob/1.19/src/main/java/net/fabricmc/loom/extension/LoomGradleExtensionApiImpl.java).

**Loom 적용과 경로 설정의 정확한 선후 관계(1.19.4 소스 확인):** `LoomNoRemapGradlePlugin.apply`가 `LoomGradlePlugin.apply`를 호출하면 `LoomGradleExtensionAPI`가 등록되고, `CompileConfiguration.run`은 `afterSuccessfulEvaluation` 콜백을 예약한다. 확장이 적용 **전에는 존재하지 않으므로** 정확한 순서는 **(1) 구성 스냅샷 준비 → (2) `pluginManager.apply("net.fabricmc.fabric-loom")` → (3) `loom.fabricModJsonPath`에 스냅샷 경로 지정 → (4) `splitEnvironmentSourceSets`/`loom.mods` 및 나머지 설정 → (5) 프로젝트 평가 완료 후 Loom이 파일 조회**다. Amper가 `loom.getModVersion()`을 경로 설정 전에 호출하지 않도록 한다. `loom.mods`는 ID→SourceSet을 연결하는 기능으로 로컬 JSON 파일 조회와 **별개이므로 둘 다 필요**하다. [LoomGradlePlugin.java](https://github.com/FabricMC/fabric-loom/blob/1.19/src/main/java/net/fabricmc/loom/LoomGradlePlugin.java), [CompileConfiguration.java](https://github.com/FabricMC/fabric-loom/blob/1.19/src/main/java/net/fabricmc/loom/configuration/CompileConfiguration.java), [ModSettings.java](https://github.com/FabricMC/fabric-loom/blob/1.19/src/main/java/net/fabricmc/loom/api/ModSettings.java).

**두 단계 메타데이터 브릿지 — Loom의 상대 경로 동작을 검증할 설계 후보:**

1. `FabricAmperProjectPlugin.apply(Project)` 시작 시 YAML을 엄격 파싱하여 `MetadataMapper`로 기본 `fabric.mod.json`의 정규화된 UTF-8 바이트를 만든다. JSON과, **실제로 구성 단계에 읽어야 하는 것이 확인된 참조 리소스가 있으면 그 리소스도**, 내용·상대경로를 해시해 `<project>/.gradle/fabric-amper/loom/<snapshot-hash>/`에 **구성 시점 스냅샷**을 만든다. 원본 `src`에는 출력 파일을 생성하지 않는다.
2. **상대 경로 함정(1.19.4 소스로 확인):** `FabricModJsonFactory.createFromFile(file)`은 `new FabricModJsonSource.DirectorySource(file.parent)`를 생성하고, `DirectorySource.read(path)`는 그 directory에 `path`를 붙여 파일을 읽는다. 따라서 **그 메서드를 호출하는 특정 프로세서/확장에서** Mixin 설정이나 Class Tweaker 등을 읽는다면 원본 `src/main/resources`가 아닌 `.gradle` 미러의 상대경로가 기준이 된다. **하지만 Loom 1.19.4의 기본 localMods 처리 경로가 그 파일들을 무조건 읽어 오류가 난다는 증거는 없다.** 실제로 로컬 Class Tweaker는 `loom.accessWidenerPath`로 별도 읽는 경로가 있고, 여러 Mixin/javadoc 읽기는 종속 모드 JAR에 대한 처리다. 그러므로 기본 프로파일은 **JSON만의 미러로 우선 프로브**하고, 추가 프로세서가 `DirectorySource.read`로 읽는 리소스는 해당 하위 집합만 상대경로를 보존해 스냅샷에 복사하는 쪽으로 명세를 확장한다. 복사하지 않은 리소스가 있는데도 지원 완료라고 하지 않는다. [FabricModJsonFactory.java](https://github.com/FabricMC/fabric-loom/blob/1.19/src/main/java/net/fabricmc/loom/util/fmj/FabricModJsonFactory.java), [FabricModJsonSource.java](https://github.com/FabricMC/fabric-loom/blob/1.19/src/main/java/net/fabricmc/loom/util/fmj/FabricModJsonSource.java), [AccessWidenerJarProcessor.java](https://github.com/FabricMC/fabric-loom/blob/1.19/src/main/java/net/fabricmc/loom/configuration/accesswidener/AccessWidenerJarProcessor.java).
3. 필요한 스냅샷이 존재하는 상태에서 Loom을 적용하고 `fabricModJsonPath`를 **스냅샷 루트 `fabric.mod.json`**으로 설정한다. 별개의 `GenerateFabricModJson` 태스크는 동일한 YAML/Mapper로 `build/generated/fabric-amper/resources/main/fabric.mod.json`을 만들고 `processResources.from(TaskProvider)`로 **원본 `src/main/resources`의 실제 리소스들과 함께 JAR 루트에** 넣는다. 스냅샷은 Loom **구성용으로만** 두며 `processResources`/`jar` 소스에 또 등록하지 않는다. **미러의 기본 JSON 바이트와 빌드 태스크의 JSON 바이트는 동일**해야 한다. 마지막 JAR에 Loom `include`가 보강하는 `jars` 메타데이터는 별도로 검사한다.

**이 설계의 검증 조건:** 설정 시점 파일 쓰기는 Gradle의 구성 캐시·격리 프로젝트에서 주의가 필요한 예외적 방식이다. 현재는 Loom의 선행 디스크 읽기 때문에 선택한 **미검증 호환 설계 후보**다. **실제 활성화한 Loom 프로세서가 참조 리소스를 어느 기준으로 읽는지 검증 없이 구현 완료/출시 금지**. 미러 삭제·원본 mixin/classTweaker 내용 변경·파일 손상 후 configuration cache 재사용, `clean`·IDE Sync·병렬 구성·원자적 기록, YAML 수정 시 구성 캐시 무효화를 별도 TestKit 수락 조건으로 둔다. Loom의 공식 인메모리 메타데이터 설정 또는 지연 파일 입력 API로 대체할 수 있는지 우선 조사한다.

**구성 캐시의 미러 무효화 계약:** Gradle은 구성 단계의 파일 읽기·존재 여부 확인을 입력으로 추적하지만, **구성 캐시 HIT에서는 `ProjectPlugin.apply` 자체가 실행되지 않는다**. 따라서 구성 때 미러를 생성한 **이후** 그 최종 파일을 `providers.fileContents(...).asBytes.get()`으로 명시적으로 읽어 구성 입력으로 등록하는 방식을 실험한다. 삭제·변조 시 캐시 MISS로 돌아가 재생성을 수행하는지 G20에서 검증한다. 파일 생성 이전의 `exists()`를 무심코 구성 입력으로 기록하면 첫 구성의 '없음' → 다음 구성의 '있음' 변화로 불필요한 반복 MISS 가능성이 있으므로 순서를 설계한다. `IsolatedAction`에서 Project·Settings 객체 또는 공유 mutable `BuildService`를 캡처하지 않는다. [Gradle configuration-cache 입력](https://docs.gradle.org/current/userguide/configuration_cache.html), [IsolatedAction](https://docs.gradle.org/current/javadoc/org/gradle/api/IsolatedAction.html).

**1.19.4 산출물에 대한 정적 교차 검증:** Fabric Maven의 `fabric-loom-1.19.4-sources.jar`에서 확인한 주요 Java 소스 7개가 GitHub `1.19` 태그의 대응 파일과 바이트 단위로 일치했고, 실제 1.19.4 JAR의 `META-INF/gradle-plugins/net.fabricmc.fabric-loom.properties`에서 `implementation-class=net.fabricmc.loom.LoomNoRemapGradlePlugin`을 확인했다. 정식 게시 태그 `v1.19.4`의 [Wrapper](https://raw.githubusercontent.com/FabricMC/fabric-loom/v1.19.4/gradle/wrapper/gradle-wrapper.properties)는 Gradle **9.8.0**, 해당 [CI 정의](https://raw.githubusercontent.com/FabricMC/fabric-loom/v1.19.4/.github/workflows/test-push.yml)는 JDK25 기반 실행을 사용한다. **Loom 소스 자체 빌드 시 내장 Kotlin 버전 검사**와 **게시된 Loom 1.19.4 플러그인을 Gradle 9.9.1에 적용하는 일**은 별개다. Gradle 9.9.1의 API variant 요구 조건(≥9.8)은 만족하지만 실제 소비자 적용/빌드 PASS는 확인되지 않았다. [Maven 배포 소스 아카이브](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.4/fabric-loom-1.19.4-sources.jar), [배포 JAR 색인](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.4/), [공식 릴리스 태그](https://github.com/FabricMC/fabric-loom/tree/v1.19.4).

## 6. 클래스와 태스크 설계

```text
fabric-amper-plugin/
└── src/main/kotlin/dev/fabricamper/
    ├── FabricAmperSettingsPlugin.kt       Plugin<Settings>
    ├── FabricAmperBeforeProjectAction.kt IsolatedAction<Project>
    ├── FabricAmperProjectPlugin.kt        Plugin<Project>
    ├── model/
    │   ├── ModYamlModel.kt               불변 typed DTO
    │   ├── DependencySpec.kt            kind/scope/target/exported/include
    │   └── MinecraftProfile.kt          non-remap/remap 구분
    ├── yaml/
    │   ├── StrictYamlReader.kt           제한된 YAML 파싱
    │   ├── ModYamlDecoder.kt             AST -> DTO
    │   └── ModYamlValidator.kt           필드/교차 필드 검증
    ├── bridge/
    │   ├── LoomPluginSelector.kt         plugin ID 선정
    │   ├── LoomConfigurer.kt             Extension/SourceSets
    │   ├── LoomMetadataMirror.kt          구성 시점 JSON·참조 리소스 스냅샷, 원자적 기록
    │   ├── DependencyConfigurer.kt       Gradle Configuration 매핑
    │   ├── RepositoryConfigurer.kt       Maven/Modrinth
    │   ├── JavaKotlinConfigurer.kt       Java/Kotlin 툴체인
    │   └── MetadataMapper.kt             Fabric Loader JSON 모델
    └── task/
        ├── GenerateFabricModJson.kt       @InputFile -> @OutputFile
        └── ValidateFabricAmper.kt         FA 오류 진단
```

플러그인 자체는 `java-gradle-plugin`의 `gradlePlugin.plugins.register(...)`로 `dev.fabric-amper.settings` → `FabricAmperSettingsPlugin`, `dev.fabric-amper.project` → `FabricAmperProjectPlugin` **두 개의 Marker**를 등록한다. Project 플러그인은 소비자 `build.gradle`에 직접 적용할 필요가 없다. `BeforeProjectAction`은 다른 프로젝트나 `Settings`/`Gradle` 객체를 캡처하지 않고 **현재 Project의 데이터로만 동작**한다. 설정 캐시 직렬화 대상 태스크 필드에는 `Project`/`Settings`/`ClassLoader` 참조를 보관하지 않는다.

## 7. YAML 파싱과 JSON 생성의 데이터 무결성

1. YAML UTF-8 최대 1 MiB, 중첩 깊이 32, 컬렉션 원소 총 10,000. SnakeYAML Engine **2.10** `LoadSettings.builder()`에 `setUseMarks(true)`, `setAllowDuplicateKeys(false)`, `setAllowRecursiveKeys(false)`, `setMaxAliasesForCollections(0)`, `setCodePointLimit(1_000_000)`, `setSchema(new JsonSchema())`를 설정한다. `.asBytes.get()` 또는 추적된 UTF-8 바이트 입력을 `CharsetDecoder(CodingErrorAction.REPORT)`로 엄격 해독한다(기본 `asText`의 replacement 허용에 의존하지 않음). [LoadSettingsBuilder Javadoc](https://javadoc.io/static/org.snakeyaml/snakeyaml-engine/2.10/org/snakeyaml/engine/v2/api/LoadSettingsBuilder.html).
2. `maxAliasesForCollections(0)`만으로 **scalar alias**가 전부 차단되지 않으므로 `new Parse(settings).parseString(text)` 이벤트를 끝까지 검사하며 `AliasEvent`와 `NodeEvent.getAnchor().isPresent()`를 거부한다. 명시 태그는 `ScalarEvent.getTag()` 및 `CollectionStartEvent.getTag()`에서 검사하고 YAML merge key `<<`를 거부한다. `DocumentStartEvent`가 정확히 1개여야 하며 빈·다중 문서 또는 루트 맵이 아닌 문서도 실패시킨다. **그 후 새로운 `Load(settings).loadFromString(text)`로 별도 파싱**해 실제 중복 맵 키·스칼라 타입·DTO 변환을 검사한다. 원문에서 `&`나 `!` 문자를 정규식으로 단순 탐지해 거부하면 정상 문자열까지 막으므로 금지한다. [SnakeYAML Parse API](https://javadoc.io/static/org.snakeyaml/snakeyaml-engine/2.10/org/snakeyaml/engine/v2/api/lowlevel/Parse.html), [Load API](https://javadoc.io/static/org.snakeyaml/snakeyaml-engine/2.10/org/snakeyaml/engine/v2/api/Load.html).
3. `mod.custom`을 제외한 unknown key·잘못된 타입은 오류. Java/Gradle 객체로 직접 파싱하지 않고 typed DTO를 거친다. `true/false` 이외 boolean, 숫자로 변환된 Minecraft 버전, 임의 YAML 표현식·외부 include·환경변수/코드 실행은 허용하지 않는다.
4. Settings 단계는 `mod.yaml`·`mod.yml`의 **두 존재 여부를 모두** 관찰하고 둘 다 있거나 둘 다 없으면 FA001로 실패한다. 둘 중 선택한 **바로 그 `RegularFile`**을 Settings/Project 구성 입력과 `GenerateFabricModJson.@InputFile`로 전달해야 한다. 2개가 모두 있는 상태에서 단순히 `mod.yaml`을 우선하는 구현은 금지한다. 스키마 문맥이 경로명에 의존하지 않도록 `@PathSensitive(PathSensitivity.NONE)`을 사용하되 두 별칭 교체 시 구성 캐시가 올바르게 무효화되는지 검증한다.
5. `generateFabricModJson`이 `build/generated/fabric-amper/resources/main/fabric.mod.json`을 출력한다. JSON은 UTF-8, 고정된 키 순서, 끝 개행 1개, 시각/절대경로 무삽입으로 재현 가능해야 한다. 같은 `MetadataMapper`의 바이트를 `LoomMetadataMirror`도 사용하되 출력 소유권은 구분한다: **구성 단계 미러는 `.gradle/`**, **정식 태스크 산출물은 `build/`**.
6. `tasks.register("generateFabricModJson", GenerateFabricModJson::class.java, ...)`의 `TaskProvider`를 `tasks.named("processResources", ProcessResources::class.java, ... { from(generatorTask) })`에 연결한다. `@OutputFile`이 단일 `fabric.mod.json`이므로 copy task의 루트에 놓이며 Gradle이 태스크 의존성을 추론한다. `duplicatesStrategy=FAIL`을 지정한다. **동일 출력에 `sourceSets.main.output.dir(generatorTask)`를 추가하지 않는다**(중복 패키징). [Gradle Incremental Builds](https://docs.gradle.org/current/userguide/incremental_builds.html), [ProcessResources API](https://docs.gradle.org/current/dsl/org.gradle.language.jvm.tasks.ProcessResources.html).
7. `src/main/resources/fabric.mod.json`이나 split client source set의 `src/client/resources/fabric.mod.json`이 이미 있으면 원본을 덮지 않고 `FA008`로 실패한다. `mod.yaml`/`mod.yml`이 둘 다 있으면 `FA001`. `jar`/`remapJar`에 리소스를 이중 삽입하지 않는다.
8. Fabric Loader 메타데이터의 `jars`는 nested jar/include 후 Loom이 처리할 수 있으므로 사용자 YAML에서 임의 `jars` 입력을 허용하지 않는다. `mod.depends` 등은 실제 Loader runtime contract로 별도 검증한다.
9. 비밀값은 YAML에 저장하지 않는다. private Maven 인증은 Gradle Credentials/Provider API를 사용해 추후 정의하며 로그/구성 캐시에 평문으로 저장하지 않도록 한다.
10. `@InputFile`은 **생성 태스크의 증분 실행 입력**이다. YAML은 적용 플러그인·의존성·레포지토리·Java 타깃 자체도 바꾸므로 Project 구성 단계에서 `providers.fileContents(selectedYamlRegularFile).asBytes.get()` 등 Gradle이 추적하는 **실제 획득**을 사용한다. `Provider`만 태스크에 넘기고 구성 단계에서 읽지 않으면 설정 입력으로는 기록되지 않는다. 그래야 YAML 변경 때 configuration cache의 오래된 모델을 재사용하지 않는다. [Gradle configuration cache 요구사항](https://docs.gradle.org/current/userguide/configuration_cache_requirements.html).
11. Fabric Loom의 `include`가 최종 `jar`/`remapJar`의 루트 메타데이터에 추가하는 `jars` 목록은 자동 생성 결과를 검사한다. Loom 1.19.4 `NonRemappedJarTaskConfiguration`은 `jar`에 `NestJarsAction`을 등록하고, `JarNester.nestJars`는 **JAR을 만든 뒤 `doLast`**에서 최종 JAR 루트의 `fabric.mod.json`을 확인해 종속 JAR을 `META-INF/jars/<file>`에 추가하고 `jars[].file`을 갱신한다. 따라서 `processResources`에서 생성한 메타데이터와의 순서는 **소스상 양립 가능**하다. 다만 다른 Loom 버전까지 내부 경로를 추측해 하드코딩하지 않는다. 최종 JAR의 `jars[].file`가 실제 내포 JAR 경로와 전부 일치하는지 확인한다. [NestJarsAction](https://github.com/FabricMC/fabric-loom/blob/1.19/src/main/java/net/fabricmc/loom/task/NestJarsAction.java), [JarNester](https://github.com/FabricMC/fabric-loom/blob/1.19/src/main/java/net/fabricmc/loom/build/nesting/JarNester.java), [Loader nested jars](https://docs.fabricmc.net/develop/loader/).
12. `test-dependencies`에서 JUnit Jupiter를 사용하는 경우 `Test.useJUnitPlatform()`을 설정하고 필요한 `org.junit.platform:junit-platform-launcher`를 `testRuntimeOnly`에 추가해야 실제 테스트를 실행할 수 있다. 개발자에게 `build.gradle` 수정을 요구하지 않는다. 이후 TestNG/JUnit 4 지원을 추가하려면 `settings.tests.framework` 같은 명시적 선택 규칙을 별도 스키마 버전에서 정의한다. [Gradle Java Testing](https://docs.gradle.org/current/userguide/java_testing.html).

생성되는 `fabric.mod.json`의 주요 필드 예:

```json
{
  "schemaVersion": 1,
  "id": "example_mod",
  "version": "1.0.0+26.3",
  "name": "Example Mod",
  "environment": "*",
  "entrypoints": {
    "main": [{"adapter":"kotlin","value":"com.example.ExampleMod"}],
    "client": [{"adapter":"kotlin","value":"com.example.ExampleModClient"}]
  },
  "mixins": [
    {"config":"example.mixins.json","environment":"*"},
    {"config":"example.client.mixins.json","environment":"client"}
  ],
  "accessWidener": "example.classtweaker",
  "depends": {
    "minecraft": "26.3",
    "fabricloader": ">=0.20.1",
    "java": ">=25",
    "fabric-api": ">=0.165.2",
    "fabric-language-kotlin": ">=1.14.2"
  }
}
```

실제 생성기는 전체 YAML 메타데이터(`license`/`description`/`authors`/`contact`/`icon`/`provides`/`recommends`/`suggests`/`conflicts`/`breaks`/`custom` 포함)를 보존한다. 빌드 전용 `group`, `archivesName`, `dependencies`, `repositories`, `settings`는 JSON에 포함하지 않는다. Kotlin entrypoint의 `adapter=kotlin` 사용 시 `fabric-language-kotlin` 개발 의존성 및 Loader `depends`가 모두 있는지 검사한다.

## 8. 명시적 실패 진단

진단 형식: `FabricAmper FAxxx mod.yaml:line:column path: reason`.

| 코드 | 조건 | 결과 |
| --- | --- | --- |
| FA001 | YAML 없음 또는 mod.yaml/mod.yml 동시 존재 | 경로 포함 구성 실패 |
| FA002 | YAML 문법 오류, 중복 키, alias/anchor/tag | 행/열 포함 실패 |
| FA003 | 알 수 없는 필드·잘못된 타입/범위 | 지원 키/타입 표시 |
| FA004 | 26.3 + Yarn mapping | 공식 Mojang 이름 사용 안내 후 실패 |
| FA005 | 26.3 + Java release ≠ 25 | 요구 release 명시 후 실패 |
| FA006 | Kotlin plugin/FLK 조합 불일치 | 지원 핀 버전 표시 후 실패 |
| FA007 | include와 compile-only/runtime-only 결합 | 범위 오류 |
| FA008 | src/main 또는 src/client 리소스에 fabric.mod.json 이미 존재 | 원본 보존 후 실패 |
| FA009 | Fabric ID, version, entrypoint, mixin/class tweaker 리소스 오류 | 정확한 YAML 경로·리소스 안내 |
| FA010 | 외부 Loom/Kotlin plugin classpath 미해결 | settings plugin classpath와 repository 확인 |
| FA011 | Maven/Modrinth 아티팩트 없음 | 실패 좌표 그대로 표시 |
| FA012 | 동일 좌표의 충돌하는 scope 선언 | 모든 중복 위치 표시 |
| FA013 | JAR 내 fabric.mod.json이 없거나 중복 | 패키징 검증 실패 |
| FA014 | `target=client`인데 split source set이 없거나 허용되지 않는 exported/include 조합 | 지원되는 client 설정으로 수정하라는 오류 |
| FA015 | 구성 시점 미러가 없거나 YAML과 불일치, Loom의 조회 실패 | 설정 구성 중 실패, 오래된 metadata로 진행 금지 |

## 9. 수락 테스트: 모두 향후 구현에서 실행할 항목

| ID | 재현 시나리오 | 필수 결과 |
| --- | --- | --- |
| G1 | **build.gradle 없는** 별도 Gradle TestKit fixture에서 `help` | Settings→Project 적용 성공 |
| G2 | 동일 fixture에서 `tasks --all` | `compileJava`/`processResources`/`generateFabricModJson`/`jar`/`runClient`/`runServer` 존재 |
| G3 | `build` 실행 후 JAR 검사 | 루트 `fabric.mod.json` 정확히 1개, 배포 JAR 정상 |
| G4 | YAML 버전/모드 ID 수정 후 재빌드 | Gradle 구성과 생성 JSON 최신값 |
| G5 | `build --configuration-cache` 2회 → YAML 의존성/Java/버전 변경 → 3번째/4번째 실행 | 2회째 캐시 재사용, 3회째 구성 무효화·재평가와 JSON 갱신, 4회째 다시 재사용 |
| G6 | IntelliJ Gradle Sync | 의존성·Java/Kotlin 소스·Loom Run configuration 인식 |
| G7 | Java 전용 / Kotlin 켜기/끄기 | 정확한 클래스패스·adapter·실제 JAR |
| G8 | `mappings: yarn` + Minecraft 26.3 | FA004 |
| G9 | Minecraft 1.21.11 구버전 별도 fixture | 공식 remap Loom/매핑/`remapJar` 성공(지원한다고 공개할 경우) |
| G10 | Maven/Modrinth + `compile-only`/`include`/`exported` | 컴파일/런타임/JAR 내용 일치. `include`면 중첩 JAR과 생성 `jars[].file` 경로 및 실제 런타임 로딩까지 확인 |
| G11 | 중복 키, alias/tag, 오타, 버전 숫자, 없는 리소스, 잘못된 classTweaker header/대상 | 지정 FA 진단 및 출력 보존, `validateAccessWidener` 사용 가능 시 실행 |
| G12 | 기존 `src/main/resources/fabric.mod.json` 또는 `src/client/resources/fabric.mod.json` | FA008 및 파일 미변경 |
| G13 | Gradle 9.7.0/Loom 1.18.4, Gradle 9.9.1/Loom 1.19.4 | 각 조합의 충돌/통과 독립 판정; KGP 호환성 별도 판정 |
| G14 | settings plugins 블록에 **Amper 하나만** 적용 | Project에서 Loom·선택 KGP 플러그인 로딩 성공 |
| G15 | 격리된 `runClient`/`runServer` | Fabric Loader가 생성된 metadata를 실제로 인식 |
| G16 | `mod.accessWidener` + `classTweaker v1 official` 파일과 유효하지 않은 멤버 선언 | Loom `accessWidenerPath` 연결과 `validateAccessWidener` 성공/실패 각각 확인 |
| G17 | 모드 `custom`의 인터페이스 주입 메타데이터가 YAML에서만 제공됨 | 구성 시점 미러를 `loom.fabricModJsonPath`가 정확히 읽고, Gradle 구성/compile/IDE/런타임에서 실제 인터페이스 적용 확인 |
| G17a | `help` 및 IDE Sync 직전에는 원본 `fabric.mod.json`과 생성 태스크 출력 파일이 모두 없음 | Loom이 구성 시점 미러로 자체 모드 ID·버전·interface injection 정보를 올바르게 읽고 정상 구성 |
| G18 | `splitEnvironmentSourceSets=true` + `target=client` 의존성 | `clientImplementation`/`clientCompileOnly`가 client에만 노출되고 common에서 접근 차단 |
| G19 | 다른 Settings/Project plugin에서 이미 Loom·KGP 버전을 선언한 소비자 | 예상된 명시적 충돌 진단; 중복 plugin ID나 classloader 이상으로 인한 애매한 빌드 성공 금지 |
| G20 | `.gradle/fabric-amper/loom` 스냅샷 삭제/손상 후 동일 YAML로 재실행, `clean`, IDE Sync, 병렬 구성 | JSON과 참조 리소스 재생성·무결성 재확인·원자적 기록; 구성 캐시가 재사용될 때도 파일 누락으로 잘못 진행하지 않음 |
| G21 | `generateFabricModJson` 단독 실행 후 구성 미러/태스크 JSON 비교 및 `jar` 반복 | 두 JSON의 바이트 일치, 최종 리소스 1개, 클린 빌드의 결정적 출력 |
| G22 | `mod.version/group/archivesName` 설정 후 `jar`·`sourcesJar` 실행 | `Project.version/group` 및 `BasePluginExtension.archivesName`이 반영되고 JSON version과 배포 JAR 명명 일치 |
| G23 | `test-dependencies`에 JUnit Jupiter 모듈 선언, 단위 테스트 1개 | `useJUnitPlatform` 활성화, `junit-platform-launcher` 실행 클래스패스 포함, `gradle test`가 실제 테스트를 발견·실행 |
| G24 | `mod.mixins`·`mod.accessWidener` 등 실제 리소스가 `src/main/resources`에 있고 구성 미러는 `.gradle` 아래에만 존재 | `help`/IDE Sync/`build`에서 Loom 구성 단계 참조 파일을 모두 정상 읽음. 원본 리소스 수정 시 스냅샷 해시·캐시·실제 JAR 변경, 중복 리소스 없음 |
| G25 | Loom 1.19.4+Gradle 9.7.0, 9.8.0, 9.9.1 각각 plugin resolution | `.module` Gradle 9.8 API 요구가 variant 선택에 미치는 영향과 최신 조합 실행 성공 여부를 독립적으로 판정 |
| G26 | 사용자 `mod.depends`와 `minecraft.version`/loader/Java/API/Kotlin의 버전 조건이 맞거나 의도적으로 모순됨 | AND/OR를 Loader와 동일하게 계산, 유효 조건 통과, 모순은 필드 위치 포함 진단; 사용자 조건 임의 덮어쓰기 금지 |
| G27 | `mod.environment:"*"` + `target:client` Gradle 라이브러리/클라이언트 전용 모드, client/server 초기화·Mixin | Gradle classpath, Loader `depends`, 전용 서버 부팅에서 구분이 보존되고 불가능한 side별 필수 의존성은 사용자에게 진단 |
| G28 | `settings.gradle.kts`만 있는 독립 폴더를 IntelliJ로 열고, `mod.yaml`만 편집 | IDE Gradle 프로젝트 최초 인식, 수동 Sync, YAML 저장 후 자동 Sync 여부를 분리 기록; 자동 Sync 미지원이면 공식 UX 문서에 명시 |
| G29 | `settings.kotlin.enabled=false`에서 외부 KGP를 의도적으로 캐시하지 않은 초기 환경 | 기본 정적 classpath 방식의 KGP 전이 다운로드 비용을 측정. 별도 지연 KGP `buildscript` 실험이 단일 settings 플러그인, classloader, isolated projects, configuration cache를 모두 통과할 때만 opt-in |
| G30 | 2개 YAML 별칭 동시/교체, 비정상 UTF-8, 다중/빈 YAML 문서, scalar alias, scalar/collection tag, 기존 generated JSON 복수 ZIP 진입 | FA001/FA002/FA003/FA013와 CC 무효화 확인, 원본 및 빌드 출력 보존 |

테스트는 `GradleRunner`/TestKit 기반 격리된 소비자 프로젝트에서 수행한다. `help` 성공을 게임 작동으로 간주하지 않는다. 현재 명세 단계에서는 **모든 G 항목 NOT RUN**이다.

## 10. 구현 순서와 출하 게이트

1. `java-gradle-plugin`에서 Settings/Project 두 marker를 정의하고 G1/G14 **단일 진입점·플러그인 클래스패스**부터 통과시킨다.
2. 파서/DTO/검증기와 `FA001–FA015`를 구현한다. `MetadataMapper`는 파일 출력과 분리해 결정적 바이트를 만들어야 한다.
3. **Loom 구성 이전 `LoomMetadataMirror` 파일이 필요한 이유와 수명주기를 G17/G20으로 검증**한 뒤 26.3 non-remapping Loom, Minecraft/Loader/API, Java25, Kotlin 선택 적용과 의존성 연결.
4. `GenerateFabricModJson`, `processResources` 연결, 미러와 JSON 바이트 일치, JAR 루트 JSON 정합성 검증.
5. Modrinth, include, exported, split client source sets, configuration cache, IDE Sync 확인.
6. 레거시 remap 지원은 자체 G9 검증 후에만 공개.
7. 독립 Minecraft 클라이언트/서버 검증과 배포 전 전체 수락 매트릭스 실행.

**출시 불가 기준**: (a) 설정 플러그인 하나로 Loom을 적용하지 못함, (b) 구성 시점 미러의 JSON/참조 리소스를 Loom이 올바르게 읽지 못함, (c) 생성 JSON이 JAR에 없거나 중복, (d) 26.3에 Yarn/remap 경로를 적용, (e) YAML 변경·미러 손상 후 구식 캐시/IDE 모델 사용, (f) Kotlin adapter/runtime 관계 누락, (g) Gradle·Loom·KGP API 지원 범위 및 실제 실행 결과를 구분하지 않음. 검증되지 않은 항목은 기능 완료라고 선언하지 않는다.

## 11. 공식 출처

1. Fabric 26.3 (2026-09-15): https://fabricmc.net/2026/09/15/263.html
2. Fabric 26.1 비난독화 전환: https://www.fabricmc.net/2026/03/14/261.html
3. Loom plugin ID/설정/`include`: https://docs.fabricmc.net/develop/loom/
4. 26.3 공식 build.gradle: https://github.com/FabricMC/fabric-example-mod/blob/26.3/build.gradle
5. 26.3 공식 settings.gradle: https://github.com/FabricMC/fabric-example-mod/blob/26.3/settings.gradle
6. Fabric Loader metadata: https://docs.fabricmc.net/develop/loader/fabric-mod-json
7. mappings 이관: https://docs.fabricmc.net/develop/porting/mappings/
8. Fabric Maven Loom 게시 기록: https://maven.fabricmc.net/net/fabricmc/fabric-loom/
9. Fabric Language Kotlin: https://github.com/FabricMC/fabric-language-kotlin/blob/master/README.md
10. Modrinth Maven(2026-10-07): https://support.modrinth.com/en/articles/8801191-modrinth-maven
11. Gradle 9.9.1 릴리스: https://docs.gradle.org/9.9.1/release-notes.html
11a. Fabric 26.3 공식 버전 선언: https://github.com/FabricMC/fabric-example-mod/blob/26.3/gradle.properties
11b. Kotlin/Gradle 호환성: https://kotlinlang.org/docs/gradle-configure-project.html
12. Gradle Isolated Projects와 callback plugin resolution: https://docs.gradle.org/9.9.0/userguide/isolated_projects.html#sec:applying_plugins_from_a_lifecycle_callback
13. GradleLifecycle: https://docs.gradle.org/9.9.0/javadoc/org/gradle/api/invocation/GradleLifecycle.html
14. Gradle binary plugin marker: https://docs.gradle.org/9.9.0/userguide/java_gradle_plugin.html
15. Gradle Configuration Cache: https://docs.gradle.org/9.9.0/userguide/configuration_cache_requirements.html
16. JetBrains 계승 Kotlin Toolchain module.yaml: https://kotlin-toolchain.org/latest/reference/module/
17. SnakeYAML Engine: https://javadoc.io/doc/org.snakeyaml/snakeyaml-engine/latest/index.html
18. Loom 1.19.4 원본 소스 JAR: https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.4/fabric-loom-1.19.4-sources.jar
19. Loom 1.19 메타데이터 조회 구현: https://github.com/FabricMC/fabric-loom/blob/1.19/src/main/java/net/fabricmc/loom/util/fmj/FabricModJsonHelpers.java
20. Gradle PluginManager 정확한 apply(Class<?>) 메서드: https://docs.gradle.org/current/javadoc/org/gradle/api/plugins/PluginManager.html
21. Fabric Class Tweaker 위치와 header: https://docs.fabricmc.net/develop/class-tweakers/
22. Loom 1.19.4 Gradle variant API metadata: https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.4/fabric-loom-1.19.4.module
23. Gradle plugin API variant 의미: https://docs.gradle.org/current/javadoc/org/gradle/api/attributes/plugin/GradlePluginApiVersion.html
24. Loom 1.19.4 빌드 Wrapper: https://raw.githubusercontent.com/FabricMC/fabric-loom/v1.19.4/gradle/wrapper/gradle-wrapper.properties
25. Loom Mixin/AccessWidener 상대경로 출처: https://github.com/FabricMC/fabric-loom/blob/1.19/src/main/java/net/fabricmc/loom/util/fmj/FabricModJsonSource.java
26. Gradle Configuration Cache lifecycle: https://docs.gradle.org/current/userguide/configuration_cache.html

**증거 수준:** 위 문서는 공식 문서에 존재하는 API/구성 의미를 기반으로 **구현을 위한 설계 선택**을 포함한다. 자체 Gradle plugin의 컴파일, 최신 Loom Extension binary API, 배포 클래스패스, IntelliJ Sync, Kotlin 호환, 실제 모드 실행은 아직 확인되지 않았다.

---

## 12. NeoForge 확장 설계 — YAML 스키마 **1.1** (2026-11-17, 조사 단계)

> **상태: 제안된 차기 설계, 미구현·미검증.** 이 절은 NeoForge 지원 도입을 위한 **새 스키마 계약의 초안**이다. 기존 1–11절의 Fabric 중심 설명과 `schema: 1` 예시는 이미 정의된 **v1 계약·과거 설계 기록**으로 보존한다. 현재 공개 v0.2.0의 NeoForge 빌드·실행을 주장하지 않으며, `fabric-amper.schema.json`, YAML 파서/생성기, BAT, Gradle 플러그인 및 JAR은 **이 절을 기록한다고 바뀌지 않는다**. 사용자가 추가로 제시할 구역 분리 규칙이 있으면 이 설계를 그에 맞게 조정한다.

### 12.1. 제품 목표와 스키마 버전 정책

1. 기존의 가장 중요한 조건인 **소비자 `settings.gradle.kts` + `mod.yaml`/`mod.yml`만으로 개발·빌드·실행**을 NeoForge 단독 모드에도 적용한다. 소비자가 `build.gradle(.kts)`, 수동 `fabric.mod.json`, 수동 `META-INF/neoforge.mods.toml`, 추가 로더 Gradle 속성을 작성하도록 요구하지 않는다. Java/Kotlin 소스와 실제 에셋은 개발자가 작성한다.
2. **명시적 스키마 버전은 `schema: "1.1"`**로 표현한다. `1.1`을 YAML 부동소수점으로 해석하거나 `schema: 1`과 숫자 비교하지 않는다. 차기 파서는 버전 토큰을 별도 타입/문자열로 검사해야 한다. **기존 `schema: 1` 및 스키마를 생략한 기존 Fabric 프로젝트는 v1 의미를 계속 유지**한다. v1.1은 명시적으로 선택하는 방식으로 시작하고, 생략 시 기본 버전 변경은 호환성 검증 뒤 별도 결정한다.
3. 단일 모드 소비자는 `product: fabric/mod` 또는 **새 `product: neoforge/mod`** 중 하나만 선택한다. `fabric/api`, `fabric/library`의 v1 동작은 보존한다. `neoforge/api`·`neoforge/library`의 Maven API 게시 계약과 두 로더를 동시에 빌드하는 제품 타입은 **별도 설계/수락 전에는 허용하지 않는다**.
4. **공통 필드는 계속 루트에, Fabric 전용은 `fabric:`, NeoForge 전용은 `neoforge:`에 둔다.** 선택하지 않은 로더 블록과 선택 로더 고유의 루트 레거시 키를 혼합하면 조용히 무시하지 않고 위치가 있는 진단으로 거부한다. `architectury:`는 **공통 의도 선언**으로 남기되 각 로더에 다른 실제 아티팩트를 연결한다.
5. v1.1에선 우선 **하나의 YAML → 하나의 로더 → 하나의 최종 모드 JAR**만 다룬다. 하나의 프로젝트에서 Fabric과 NeoForge JAR을 동시에 만드는 `common/fabric/neoforge` 멀티프로젝트, Architectury Loom 자동 전환 및 공통 API 소스 공유는 **미래 기능**으로 분리한다. Architectury API를 의존성으로 추가하는 것만으로 멀티로더 프로젝트가 완성되지는 않는다.

### 12.2. 공식 자료에서 확인한 전제와 버전 후보

| 항목 | 2026-11-17 공식 자료로 확인한 사항 | v1.1 설계 판정 |
| --- | --- | --- |
| Minecraft/Java | NeoForge `26.3.x` 및 공식 26.3 MDK 존재; Minecraft `26.3`, Java **25** | 1차 NeoForge 프로파일은 **26.3 전용**. Java 25 사용, 다른 MC 버전은 추후 검증 |
| NeoForge 런타임 | 공식 26.3 MDK의 `neo_version=26.3.0.101`; 공식 프로젝트 목록에는 조회 당시 더 새로운 `26.3.0.103`도 존재 | **재현 가능한 초기 핀 후보는 `26.3.0.101`**. 최신이라는 이유로 자동 업데이트하지 않음. 어느 조합이 실제 통과하는지는 TestKit/런타임으로 결정 |
| Gradle 개발 백엔드 | 공식 26.3 ModDevGradle MDK는 `net.neoforged.moddev` **2.0.148**, `neoForge.version`, Java25, client/server/data/gameTestServer, `mods { sourceSet(main) }` 사용 | **ModDevGradle을 1차 후보**, NeoGradle은 검토한 대안. 소비자에게 별도 plugin 적용을 요구하지 않음 |
| NeoGradle 대안 | 공식 Userdev 플러그인 `net.neoforged.gradle.userdev`; 일반 의존성 `net.neoforged:neoforge:<version>` | ModDevGradle과 플러그인 ID/API가 다름. 1.1 구현에서 자동 혼합하지 않음 |
| Gradle 조합 | 26.3 MDK Wrapper는 Gradle 9.2.1 사용; MDG 문서는 구성 캐시 지원을 설명 | 기존 Fabric Amper의 Gradle 9.7.1/9.9.x에서 **실제 호환성 미검증**, 조합별 확인 필수 |
| NeoForge 시작 API | 공식 26.3 MDK는 `@Mod(MODID)`, `IEventBus`/ `ModContainer` 생성자 주입, `DeferredRegister.register(modBus)`, `NeoForge.EVENT_BUS` 사용 | Fabric `ModInitializer`를 NeoForge에 재사용/자동 변환하지 않음 |
| 로더 메타데이터 | NeoForge는 JAR 내부 `META-INF/neoforge.mods.toml`; Fabric은 JAR 루트 `fabric.mod.json` | 생성기·검증기·리소스 경로를 로더별로 독립 구현 |
| Architectury API | 공식 26.3 문서는 NeoForge `26.3.0.48-beta+`, Architectury API `22.0.5` 예시 및 `architectury-neoforge` 좌표를 안내 | Fabric용 `architectury-fabric`을 NeoForge에 설치하지 않음. 버전·메타데이터·게임 실행별 별도 검증 |

**근거 수준 주의:** 공식 NeoForge 일반 문서의 이벤트/메타데이터 화면은 조회 당시 **26.1 문서 버전**을 보여 준다. 26.3 MDK와 `26.3.x` 소스에서 확인한 API 예제를 설계 입력으로 쓰되, 나머지 API 시그니처·모드 로딩/구성 캐시/IDE 결과까지 26.3에서 실증됐다고 주장하지 않는다. `26.3.0.101`, MDG `2.0.148`은 **후보**이지 Fabric Amper의 검증 완료 매트릭스가 아니다.

### 12.3. v1.1 키 배치 — 공통과 로더 전용의 경계

| 영역 | 스키마 1.1에서의 위치/의미 | 기존 스키마 1과의 관계 |
| --- | --- | --- |
| `schema`, `product` | 공통 루트: 스키마 식별·단일 대상 로더 결정 | `schema: 1` 그대로 유효. `schema: "1.1"`만 새 이름공간 사용 |
| `mod.id/version/group/archivesName/name/description/license/authors/contributors` | 공통 모드 신원·배포명. 각 로더가 표현 가능한 범위만 출력하며 로더별 제약 검사 | v1 모드 정보 보존. NeoForge mod ID는 영문 소문자로 시작하고 이후 소문자·숫자·`_`만 허용(2–64자), license 문자열 필수 |
| `mod.contact` | 공통 원본 정보. 로더별 동일 의미가 확인된 키만 출력, 변환 불가 키는 진단 또는 명시적 전용 필드로 처리 | Fabric의 자유로운 contact 맵을 NeoForge TOML로 무손실 자동 변환한다고 가정하지 않음 |
| `minecraft.version` | 공통 대상 게임 버전(초기 `26.3`) | `minecraft.loader`, `minecraft.fabricApi`, `minecraft.mappings`는 v1.1에서 금지·이동 |
| `settings.java.release/sourcesJar`, `settings.kotlin.enabled/pluginVersion/languageVersion` | 공통 Java/선택 Kotlin 빌드 설정. NeoForge Kotlin 초기 지원은 클래스패스·언어 로더 실증 후 확정 | Fabric Language Kotlin은 공통이 아니라 Fabric 전용 |
| `repositories`, `dependencies`, `test-dependencies` | 공통 **의존성 의도**·저장소. 실제 Gradle configuration/패키징은 선택한 백엔드에 따라 별도 매핑 | `fabric`/ `neoforge` 저장소 별칭은 저장소를 뜻할 뿐 로더 활성화가 아님. 의존성 `target/include`의 전 로더 동일 동작 보장 안 함 |
| `publish`, `publishing` | 공통 **배포 의도**. 실제 artifact/loader 값·게시 가능 제품 타입을 로더별 검증 | `publish.modrinth.loaders` 등은 제품 로더와 충돌하면 거부. 기존 `publishing` API 전용 계약은 우선 Fabric v1만 보장 |
| `architectury.enable/enabled/version` | 공통 옵션: 선택한 플랫폼에 맞는 Architectury API 아티팩트를 요청 | v1 루트 Architectury Fabric 전용 작동은 보존. v1.1 NeoForge 의미는 새 백엔드 검증 후 제공 |
| `fabric.loader/api/mappings` | Fabric Loader/Fabric API/매핑 | v1의 `minecraft.loader/fabricApi/mappings`를 **1.1에선 이동** |
| `fabric.environment/entrypoints/languageAdapters/mixins/accessWidener/provides/depends/recommends/suggests/conflicts/breaks/custom` | `fabric.mod.json`의 로더 고유 메타데이터 | v1의 같은 이름 `mod.*`를 1.1에서 **이동**. Fabric Loader의 AND/OR 버전 조건은 이 영역에만 적용 |
| `fabric.icon`, `neoforge.logoFile` | 각각 Fabric의 아이콘 경로·크기별 이미지 표현, NeoForge의 단일 TOML 로고 리소스 경로 | v1 `mod.icon`을 NeoForge로 자동 복사하지 않음. 리소스 형식·경로 및 파일 존재 검증은 로더별로 수행 |
| `fabric.loom.splitEnvironmentSourceSets`, `fabric.kotlin.fabricLanguageKotlin`, `fabric.resources.failOnExistingFabricModJson` | Loom 소스셋, FLK, Fabric 원본 Loader JSON 충돌 정책 | v1 `settings.loom`, `settings.kotlin.fabricLanguageKotlin`, `settings.resources.failOnExistingFabricModJson` 이동 |
| `neoforge.version`, `neoforge.modLoader/loaderVersion` | NeoForge 실행 플랫폼 버전과 **별도의** 언어 로더 설정 | Fabric Loader version이나 Maven 범위를 재해석해 대체하지 않음; 기본 `modLoader=javafml` |
| `neoforge.dependencies` | `[[dependencies.<mod.id>]]`로 나갈 로더 관계. `modId/type/versionRange/ordering/side/reason` | Fabric `mod.depends`와 형식·의미가 다름. 공통 Gradle `dependencies`와도 독립 |
| `neoforge.mixins/accessTransformers` | `[[mixins]]`, `[[accessTransformers]]` 및 실제 리소스 | Fabric classTweaker/AccessWidener를 NeoForge access transformer로 변환하지 않음 |
| `neoforge.logoFile/displayURL/issueTrackerURL/credits/updateJSONURL/modProperties/features` | NeoForge TOML에서 의미가 다른 표시·부가 메타데이터 | 공통 `mod`에 이름만 같은 JSON 필드를 무리하게 합치지 않음 |

**위 경로 이름은 다음 구현의 목표 API 제안이지 이미 파서가 인식하는 필드가 아니다.** 표에서 이동하지 않은 공통 키까지 강제로 한쪽 블록에 밀어 넣지 않는다. 예를 들어 Maven 배포 주소와 JUnit은 특정 모드 로더에 종속되지 않는다.

### 12.4. 목표 YAML 예시 (실행 예제가 아닌 설계 초안)

**NeoForge 전용 26.3 모드 (`schema: "1.1"`):**

```yaml
schema: "1.1"                 # 문자열로 명시: 숫자 1.1 사용 금지
product: neoforge/mod
mod:
  id: examplemod
  version: "1.0.0+26.3"
  group: org.example
  archivesName: examplemod
  name: Example Mod
  description: NeoForge 모드 예시
  license: MIT
  authors: ["Example Developer"]
minecraft:
  version: "26.3"
settings:
  java:
    release: 25
    sourcesJar: true
  kotlin:
    enabled: false
neoforge:
  version: "26.3.0.101"      # MDK 기준 초기 핀 후보, 런타임 검증 전
  modLoader: javafml
  # 기본 네오포지·마인크래프트 required 의존성은 별도 로더 메타데이터로 자동 생성
  dependencies:
    - modId: example_dependency
      type: optional
      versionRange: "[1.0,)"
      ordering: NONE
      side: BOTH
  mixins: []
  accessTransformers: []
architectury:
  enable: true                # 향후 NeoForge 백엔드: architectury-neoforge
  version: "22.0.5"
repositories:
  - mavenCentral
  - neoforge
dependencies: []
test-dependencies: []
```

**Fabric 전용 26.3 모드 (`schema: "1.1"`):**

```yaml
schema: "1.1"
product: fabric/mod
mod:
  id: example_mod
  version: "1.0.0+26.3"
  name: Example Mod
  license: MIT
minecraft:
  version: "26.3"
settings:
  java:
    release: 25
  kotlin:
    enabled: false
fabric:
  loader: "0.20.1"
  api: "0.165.2+26.3"
  mappings: mojang
  environment: "*"
  entrypoints:
    main: ["org.example.ExampleMod"]
  depends:
    minecraft: "26.3"
  loom:
    splitEnvironmentSourceSets: false
architectury:
  enable: true                # Fabric에서는 architectury-fabric
repositories:
  - fabric
  - mavenCentral
dependencies: []
```

위 두 예제는 **독립적인 두 소비자 프로젝트**이며 한 YAML 안에 두 `product`를 동시에 지정하는 구문이 아니다. NeoForge 예시의 `example_dependency`는 **자리표시 모드 ID**여서 실제 실행 시 설치된 모드와 호환성이 필요하다. Fabric 예시의 의존성 메타데이터는 현재 v1의 Fabric Loader 규칙을 1.1 이름공간에 투영한 것이다.

### 12.5. NeoForge Gradle 백엔드·API 접합 계약

1. Settings 플러그인 하나가 YAML의 `schema/product`를 **Gradle 프로젝트 구성 전에** 확인하고, `fabric/mod`이면 기존 Loom 구성기를, `neoforge/mod`이면 독립 `NeoForgeConfigurer`(가칭)를 택한다. 소비자 `build.gradle(.kts)` 추가, 수동 `plugins { id("net.neoforged.moddev") }` 추가, 임의 `gradle.properties` 속성 작성은 해결책으로 허용하지 않는다.
2. NeoForge 후보는 공식 26.3 MDK와 같은 **ModDevGradle `net.neoforged.moddev` 2.0.148** 및 `neoForge.version = <validated pin>`이다. 저장소 `https://maven.neoforged.net/releases` 등을 플러그인 해석/의존성 용도에 맞춰 구분한다. 26.3에서는 Java25 toolchain/Gradle JVM 검사, `neoForge.mods { <mod.id> { sourceSet(sourceSets.main) } }` 구성, `runClient`/`runServer`/datagen 독립 시험이 필요하다.
3. 현재 Fabric Amper의 **Settings 플러그인 전이 클래스패스에서 Loom을 제공하는 방식**에 ModDevGradle을 무조건 병합하면 불필요한 다운로드·서로 다른 Gradle 플러그인 버전·클래스 충돌 가능성이 있다. **선택 로더 백엔드만 로딩**하는 경로를 설계하고 실제 Gradle plugin classloader/variant/IsolatedAction/configuration cache에서 검증해야 한다. 구현 전에는 MDG 플러그인 ID를 Project에서 문자열로 적용하기만 하면 자동 다운로드될 것이라고 가정하지 않는다.
4. NeoForge `@Mod("examplemod")` Java 초기화 클래스는 **사용자 소스**이며 Fabric `ModInitializer`/metadata `entrypoints`로 대체되지 않는다. 공식 26.3 MDK의 `IEventBus`/ `ModContainer` 주입, mod-specific event bus 및 `NeoForge.EVENT_BUS` 구분, `DeferredRegister` 등록 시점을 보존한다. 별도 `@Mod(dist=Dist.CLIENT)`·client 전용 클래스를 공통 서버 클래스에서 직접 참조하지 않는다. `init` 템플릿을 추가한다면 실제 NeoForge `@Mod` 소스를 생성하고 client/server로 빌드 검증한다.
5. 새 Generator는 `generateNeoForgeModsToml`(가칭)로 **최종 JAR의 `META-INF/neoforge.mods.toml`을 정확히 한 번** 만든다. `[[mods]]`의 `modId/version/displayName` 및 파일 레벨 `license/modLoader/loaderVersion`, `[[dependencies.<mod.id>]]`, mixins, access transformers 등을 올바른 TOML로 직렬화한다. `modLoader=javafml`의 **`loaderVersion`은 JavaFML 언어 로더 범위**이지 NeoForge API 버전이 아니다. 기본 NeoForge·Minecraft required 버전 조건은 네이티브 **Maven Version Range** 형식으로 생성한다. 사용자가 동일 ID를 수동 지정했다면 범위가 호환되는지 검사하고 충돌/중복을 진단한다.
6. NeoForge `license`는 필수, `@Mod` 값과 `mod.id`는 일치해야 한다. NeoForge mod ID는 영문 소문자·숫자·밑줄(2–64자)로 제한되므로 Fabric에서 허용하던 `-`를 NeoForge 프로파일에 그대로 허용하지 않는다. TOML 문자열/배열/테이블 이스케이프와 순서를 결정적으로 출력하고 자원·메타데이터 중복/원본 파일(`src/main/resources/META-INF/neoforge.mods.toml` 등)은 **fail-closed** 한다.
7. NeoForge의 메타데이터 생성이 IDE Gradle Reload에도 반영되도록 공식 MDK의 `neoForge.ideSyncTask(generateModMetadata)` 패턴을 조사한다. 현재 Fabric 전용 `LoomMetadataMirror`를 NeoForge에 무조건 복제하지 않는다. 필요하다면 네이티브 Gradle output/provider를 통해 구성 단계·IDE Sync·빌드·configuration cache의 내용 주소/무결성을 각각 검증한다.
8. Kotlin은 일반 `settings.kotlin.enabled` 스위치를 재사용하되 **Fabric Language Kotlin 모드를 NeoForge에 삽입하면 안 된다**. NeoForge 언어 로더·KGP 2.4.21 호환, `@Mod` Kotlin 클래스/초기화 시맨틱, runtime reflection은 실물 TestKit과 게임 실행 확인 전 **지원 미확정**으로 표시한다. 미검증 Kotlin 요청을 Java 성공으로 둔갑시키지 않는다.

### 12.6. 의존성·Loader 관계·Jar-in-Jar 분리

**핵심 규칙:** 공통 `dependencies`/ `test-dependencies`는 Gradle 컴파일·실행·게시 클래스패스, `fabric.depends`는 Fabric Loader의 관계, `neoforge.dependencies`는 NeoForge Loader TOML의 관계다. **Gradle 좌표 하나로 Loader 의존성 관계를 자동 추측하지 않는다.** 특히 `maven.modrinth:<project>:<version>`의 project slug·Maven GAV·NeoForge `modId`는 서로 다른 식별자다.

| 공통 의존성 의도 | Fabric 26.3 | NeoForge 26.3 ModDevGradle 후보 |
| --- | --- | --- |
| `scope: all` | `implementation` | `implementation` |
| `scope: compile-only` | `compileOnly` | `compileOnly` |
| `scope: runtime-only` | `runtimeOnly` | `runtimeOnly` 또는 비전이적 개발 전용 `localRuntime`을 **명시적으로 구별** |
| `exported: true` | Java-library `api`(지원 프로파일에서) | Java-library `api`; 실제 Maven POM/.module 공개 의존성 검사 |
| `target: client` | 검증된 Loom client source set 구성 | NeoForge에선 **일단 거부**, MDG 분리 source set과 실제 classpath isolation 확인 후 도입 |
| `include: true` | Loom `include`, Fabric Loader `jars[].file` | MDG `jarJar` 및 `META-INF/jarjar/metadata.json`; 동일한 task/포맷으로 취급 금지 |
| 테스트 의존성 | `testImplementation` 등 | `testImplementation` 등 + MDG 테스트/게임 실행 호환성 별도 확인 |

NeoForge에서는 `modImplementation` 등 **Fabric Loom 구성 이름을 사용하지 않는다**(MDG의 레거시 리매핑 경로에 등장하는 같은 이름과 현대 26.3 동작을 혼동하지 않음). NeoForge 자체는 사용자가 일반 `dependencies`에 직접 중복 삽입하는 Fabric API 같은 모드가 아니라 MDG `neoForge.version`에 의해 개발 플랫폼이 구성된다. 외부 모드의 **선택적 개발 실행**은 MDK의 `compileOnly` + `localRuntime` 패턴을 따로 검토한다. MDG 기반 `jarJar`는 버전 **`strictly` 범위와 `prefer`**를 사용할 수 있으며 Fabric `include`를 아무 검증 없이 1:1 복사하지 않는다. v1.1에서 NeoForge용 `include=true`의 지원 범위가 정해질 때까지는 **명시적 오류**로 거부한다. 후속 확장 시 예컨대 `jarJar: { strictly: "[1.0,2.0)", prefer: "1.4.0" }` 같은 별도 하위 구조를 검증하고, 단일 고정 버전 범위 `[1.4.0]`도 공식 도구가 허용하는지 시험한다.

`neoforge.dependencies[]` 각 원소는 최소 `modId`와 `type: required|optional|incompatible|discouraged`, `versionRange`(NeoForge Maven 범위), `ordering: NONE|BEFORE|AFTER`, `side: BOTH|CLIENT|SERVER` 및 선택 `reason`으로 설계한다. 같은 modId의 모순·자기 의존성·순환 ordering·물리적 side 위반을 검사한다. Fabric의 `>=22.0.5` 서술을 NeoForge TOML에 그대로 복사하지 않고 Maven 범위 `[22.0.5,)` 같은 NeoForge 형식으로 생성한다. `side=CLIENT`는 **Loader가 클라이언트에서 의존성을 요구한다는 뜻**이지 Gradle `target=client`와 동일하지 않다.

저장소 `mavenCentral`, `modrinth`, `neoforge`, HTTPS 전용 Maven 객체는 재사용할 수 있지만 각 저장소의 콘텐츠 필터와 취득 대상은 검증한다. NeoForge Maven은 `https://maven.neoforged.net/releases`이고, Modrinth Maven은 `https://api.modrinth.com/maven` / `maven.modrinth:<project>:<version>`으로 **전이 의존성을 자동 제공하지 않는다**. 별도의 CurseMaven(`https://cursemaven.com/`)은 **CurseForge 공식 저장소와 동일시하지 않으며** 지원 여부는 추후 검증한다. 빌드 환경의 인증·동적 버전·네트워크/의존성 누락은 기존 FA 보안/진단 정책에 따라 fail-closed 처리한다.

### 12.7. Architectury API와 두 로더의 관계

- `architectury.enable: true`는 **Architectury API를 해당 단일 로더에 추가**한다는 뜻이다. Fabric에서는 검증된 기존 `dev.architectury:architectury-fabric:22.0.5`; NeoForge에서는 공식 26.3 문서·Maven에 게시된 `dev.architectury:architectury-neoforge:22.0.5`를 선택한다. 공통 `dev.architectury:architectury` 모듈은 별도의 `common` 소스 프로젝트를 위한 것으로, 1.1 단독 모드의 모든 의존성을 이 좌표로 대체하지 않는다.
- 현재 확인된 API 22.0.5와 Minecraft 26.3을 초기 버전 후보로 삼되 **NeoForge용 실물 컴파일/Loader 실행에서 버전 적합성**을 확인한다. `architectury.version`은 고정값만 허용하며 Fabric/NeoForge의 Loader 관계를 각 메타데이터 형식으로 생성한다. Architectury 런타임 모드를 그냥 Jar-in-Jar로 숨겨 번들하지 않는다.
- 공식 Architectury 멀티플랫폼 예제의 `common/fabric/neoforge` 모듈과 `dev.architectury.loom-no-remap`은 **별도의 멀티로더 빌드 시스템**이다. Fabric Amper의 `architectury` 옵션과 혼동하지 않는다. 이 부분을 자동화하려면 multi-project Settings 적용/소스셋·플랫폼별 출력을 새 수락 범위로 정의해야 한다.

### 12.8. 이전 프로젝트와 입력 검증·진단

1. `schema: 1` 또는 생략 v1의 `minecraft.loader`, `mod.entrypoints`, `settings.loom`, 최상위 `architectury`를 **변경 없이 계속 파싱**한다. v1을 NeoForge로 자동 판단/변환하지 않는다. 기존 v0.2.0 Fabric 릴리즈의 동작이 기본 회귀 기준이다.
2. **v1 → v1.1 선택적 이동**은 키 위치를 명시적으로 바꾸며 기존 입력을 덮어쓰지 않는다. 예: `minecraft.loader → fabric.loader`, `minecraft.fabricApi → fabric.api`, `minecraft.mappings → fabric.mappings`, `mod.entrypoints → fabric.entrypoints`, `mod.depends → fabric.depends`, `settings.loom → fabric.loom`, `settings.kotlin.fabricLanguageKotlin → fabric.kotlin.fabricLanguageKotlin`. Fabric `mod.icon`은 `fabric.icon`으로, NeoForge에서는 별도 `neoforge.logoFile`로 취급한다.
3. `schema: "1.1"`에서 `product: fabric/mod`에 `neoforge`가 있거나 `product: neoforge/mod`에 `fabric`이 있으면 **실패**한다. 두 블록이 동시에 있거나 필드를 구/신 위치에 동시에 작성한 경우, 어느 쪽이 우선인지 추측하지 않는다. `minecraft.loader`를 NeoForge 버전으로 읽거나 Fabric의 `mod.accessWidener`를 NeoForge AT로 변환하는 자동 마이그레이션도 금지한다.
4. `schema: 1.1`(미인용 float), 알 수 없는 스키마, 알 수 없는 플랫폼/필드, 다른 loader용 JAR 또는 원본 메타데이터, 잘못된 YAML, 유효하지 않은 버전·경로·TOML, 잘못된 `@Mod` ID, 중복/모순 Gradle·Loader 관계는 **명시적 경로/줄·열을 갖는 진단**이 필요하다. 기존 FA001–FA015 의미를 훼손하지 않고, 새 플랫폼 전용 코드(예: `FA016` 이상)는 구현 때 충돌 없이 확정한다.
5. JSON Schema는 **Draft-07 유지**, 향후 `fabric-amper.schema.json`에서 v1/v1.1 분기(버전별 `oneOf`/조건부 `required`/additionalProperties), `product`별 독립 필드 자동완성, 출처가 있는 설명과 유효한 샘플을 제공한다. **이 단계에서는 스키마 파일을 변경하지 않는다.** `fabric-amper.bat init`의 서명된 스키마 다운로드는 schema bytes가 바뀌는 구현 릴리즈에서 BAT 핀도 동시에 갱신해야 한다.
6. GitHub/Modrinth/CurseForge 게시와 로더 변경은 별개다. NeoForge 제품을 위한 release artifact, Modrinth `loaders: ["neoforge"]`, CurseForge 게임 버전/로더와 실제 소비자 JAR을 검사하고 수동 승인 없는 원격 게시를 하지 않는다.

### 12.9. 향후 수락 시험 (전부 **NOT RUN**, 구현 시작 조건 아님)

| 게이트 | 필수 검증과 실패 조건 |
| --- | --- |
| NF1 | `schema: 1`·생략 기존 Fabric 소비자의 help/build/metadata/CC 바이트·동작 회귀 없음; 새 `schema: "1.1"`은 아직 구현 전이라 지원한다고 주장하지 않음 |
| NF2 | `schema: "1.1"`과 `neoforge/mod`를 선택한 **독립 settings-only** 소비자의 `help`/`tasks`; 추가 build.gradle, 작성 TOML, 추가 plugin 선언 없이 MDG 적용 |
| NF3 | NeoForge 26.3/Java25/MDG 고정 조합, Gradle 9.2.1(MDK), 9.7.1 및 사용 가능한 9.9.x를 **각각** 실제 metadata variant/compile로 검증 |
| NF4 | Java `@Mod` 진입점, ModBus/GameBus, registry와 `runClient`/`runServer` 실제 Loader 로딩; EULA/인증은 자동 동의하지 않음 |
| NF5 | 생성 `META-INF/neoforge.mods.toml` 정확히 1개·Fabric `fabric.mod.json` 0개가 JAR에 있고, TOML 테이블·순서·UTF-8·리소스 스냅샷과 결정적 JAR, 작성된 원본 TOML/중복 ZIP 진입 거부 |
| NF6 | `[[dependencies.<modid>]]`의 required/optional/incompatible/discouraged, Maven 범위/ordering/side 동작·오류, `modLoader/loaderVersion` 의미 보존 |
| NF7 | NeoForge Gradle classpath와 모드 런타임 관계 분리, `compileOnly/runtimeOnly/localRuntime/api` 동작 및 잘못된 Fabric `mod*` 구성 미사용 |
| NF8 | MDG `jarJar`의 version strict/prefer/배포된 `META-INF/jarjar/metadata.json`과 **실제 게임 로딩**; Fabric JIJ와 독립 |
| NF9 | `architectury.enable` 26.3 기본/명시 버전, `architectury-neoforge` 실제 컴파일·런타임/자동 NeoForge Loader 의존성. Fabric 기존 기능 회귀 없음 |
| NF10 | NeoForge Java→Kotlin 활성화/비활성화·언어 로더/KGP 클래스패스, 미검증 Kotlin 프로파일 오작동 거부 |
| NF11 | Fabric `fabric.loom`과 NeoForge MDG의 소스셋·클라이언트 격리 서로 침범하지 않음. NeoForge client-only는 실증 전 거부 |
| NF12 | Gradle 구성 캐시 STORE/HIT→YAML 수정 MISS→HIT, IntelliJ 최초 Gradle 인식/수동 Reload, generated TOML 갱신·손상 복구·병렬 구성 |
| NF13 | 파일 경로 traversal/symlink, 악성 TOML/중복 스키마/비정상 UTF-8, 타 로더 블록·틀린 `@Mod` 및 메타데이터 충돌을 원본 보존과 함께 진단 |
| NF14 | Modrinth/NeoForge Maven 저장소 필터·고정 좌표/전이성, API 게시 시 실제 POM/.module, 로더별 publish 대상 및 원격 업로드 안전성 |
| NF15 | 명시 `schema: 1.1` float 거부, v1/v1.1 Draft-07 자동완성, 단계적 `migrate` dry-run/백업/옵션 누락·충돌 검증 |
| NF16 | 사용자에게 제공되는 `init` Java NeoForge 예제가 실제로 빌드·실행되고 생성 Settings/YAML/소스 외 작성 Gradle 빌드 파일·Loader TOML이 없음 |

**출하 차단:** NF2·NF4·NF5·NF6·NF8·NF9·NF12 등의 런타임/IDE/패키징 검증 없이 NeoForge 지원을 공개 기능으로 광고하지 않는다. NeoForge 백엔드 하나를 추가하더라도 **기존 G1–G30의 Fabric 수락 게이트를 없애지 않는다**. 지금 단계에는 변경된 실행 코드나 통과한 NF 시험이 **없다**.

### 12.10. 조사 트랙과 공식 참고자료

이번 설계의 **6개 독립 읽기 전용 트랙**: (1) NeoForge 26.3 실제 MDK API·등록·버전, (2) 이벤트/측면·metadata/AT/Mixin/Architectury API, (3) ModDevGradle/NeoGradle 구성·기본 의존성, (4) Maven/Modrinth/JarJar·Loader 관계, (5) 현재 Fabric Amper YAML/DTO/Generator와 NeoForge 접합, (6) 공통/`fabric`/`neoforge` 구획 및 v1 호환·v1.1 마이그레이션. Prime은 각 트랙을 26.3 MDK, NeoForge 공식 문서, 현재 `specs.md`·DTO·스키마 구조와 교차 검토한다. **이 절은 설계 기록이며 빌드·테스트 수행 기록이 아니다.**

- NeoForge 26.3 공식 MDK [Gradle](https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle/blob/main/build.gradle), [버전](https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle/blob/main/gradle.properties), [Java @Mod 예제](https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle/blob/main/src/main/java/com/example/examplemod/ExampleMod.java)
- [NeoForge 26.3.x 프로젝트 소스](https://github.com/neoforged/NeoForge/blob/26.3.x/gradle.properties), [NeoForge 버전 목록](https://projects.neoforged.net/neoforged/neoforge), [ModDevGradle 버전 목록](https://projects.neoforged.net/neoforged/moddevgradle)
- [NeoForge 모드 메타데이터](https://docs.neoforged.net/docs/gettingstarted/modfiles/), [이벤트](https://docs.neoforged.net/docs/concepts/events/), [레지스트리](https://docs.neoforged.net/docs/concepts/registries/), [물리/논리 측면](https://docs.neoforged.net/docs/concepts/sides/) (**일반 문서 UI는 26.1 기준**, 26.3 MDK/실행 별도 확인)
- [ModDevGradle 공식](https://docs.neoforged.net/toolchain/docs/plugins/mdg/), [NeoGradle 공식](https://docs.neoforged.net/toolchain/docs/plugins/ng/), [비마인크래프트 라이브러리](https://docs.neoforged.net/toolchain/docs/dependencies/nonmclibs/), [Jar-in-Jar](https://docs.neoforged.net/toolchain/docs/dependencies/jarinjar/)
- [Architectury API 26.3 Setup](https://docs.architectury.dev/api/getting-started/setup/), [Architectury 프로젝트 구조](https://docs.architectury.dev/api/getting-started/project-structure/), [Architectury NeoForge Maven](https://maven.architectury.dev/dev/architectury/architectury-neoforge/22.0.5/)
- [Modrinth Maven 공식 안내](https://support.modrinth.com/en/articles/8801191-modrinth-maven), [NeoForge Maven](https://maven.neoforged.net/releases/)
