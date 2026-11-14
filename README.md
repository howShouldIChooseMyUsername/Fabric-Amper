# Fabric Amper (개발·검증 중)

Minecraft Fabric 모드를 **소비자 프로젝트의 `settings.gradle.kts`와 `mod.yaml` 또는 `mod.yml`만으로** 구성하는 Gradle 바이너리 플러그인입니다. 소비자에게 `build.gradle`, `build.gradle.kts`, 직접 작성한 `fabric.mod.json`은 필요하지 않습니다. **명세의 G1~G30 및 출하 게이트는 아직 모두 닫히지 않았으므로 정식 출시 완료 상태가 아닙니다.** 정확한 수행 이력과 남은 제한은 [`QA_REPORT.md`](QA_REPORT.md), 필수 계약은 [`specs.md`](specs.md)를 기준으로 합니다.

> **현재 개발 브랜치 동기화 (2026-11-14):** 공개 프리릴리즈는 **[v0.1.2](https://github.com/howShouldIChooseMyUsername/Fabric-Amper/releases/tag/v0.1.2)**이며, 이 브랜치의 Gradle 프로젝트/BAT/소비자 Settings 예제도 `0.1.2` 기준입니다. `doctor --fix`는 사용자가 명시적으로 실행할 때 해당 릴리스에서 **단독 PS1을 BAT 옆에 내려받고 SHA-256을 검증한 뒤** 환경 복구를 수행합니다. `--dry-run`은 다운로드하지 않습니다. 자세한 현재 사용법은 [HUMAN.md](HUMAN.md), [HUMAN_QuickGuide.md](HUMAN_QuickGuide.md), [FabricAmperSKILL.md](FabricAmperSKILL.md), [FabricAmperSKILL_SYN.md](FabricAmperSKILL_SYN.md)를 참고하세요. 아래 이전 `0.1.0` 체크섬·빌드 결과는 **과거 검증 기록**이며 현재 0.1.2 바이너리의 값이 아닙니다.

## 파일을 작성하지 않고 배포하는 통합 `publish` 계약 (2026-11-13 추가 요구)

> **현재 구현과 검증 범위:** YAML 기반 게시 기능과 `publish.common` 공유 설정은 구현됐습니다. 로컬 Maven 게시·독립 소비자 작업 구성·플랫폼 업로드 `--dry-run`은 검증했고, [QA §28](QA_REPORT.md)에서는 **127.0.0.1 루프백 HTTPS 서버에 대한 네이티브 Maven 인증 PUT**을 성공했습니다. **외부 Modrinth/CurseForge 업로드는 아직 실행하지 않았고**, 실제 Maven 게시 시 Gradle 9.7.1 configuration cache HIT도 검증되지 않았습니다. 이 결과를 외부 게시 승인으로 해석하지 않습니다.

### 소비자가 작성할 내용

`fabric/mod`, `fabric/api`, `fabric/library` 모두 `mod.yaml`/`mod.yml`에 선택적으로 **새 최상위 `publish:`** 블록을 적을 수 있습니다. 기존 라이브러리 사용자의 `publishing.repository`는 그대로 지원하는 **레거시 Maven 설정**이며 제거하거나 이름을 바꾸지 않습니다. 새로운 `publish.maven`과 기존 `publishing.repository`가 **동시에 원격을 지정하면 오류**로 처리합니다. `publish` 블록을 생략한 일반 `fabric/mod` 소비자에게 원격 게시 작업이나 원격 전송이 생기지 않습니다.

```yaml
# schema: 1                  # 선택: 생략하면 플러그인의 최신 내장 스키마(현재 v1)
product: fabric/mod              # fabric/api, fabric/library도 동일한 publish 문법
mod:
  id: sample-mod
  version: "1.0.0+26.3"
  name: Sample Mod
  group: org.example             # Maven에 게시할 때만 필수
minecraft:
  version: "26.3"
  mappings: mojang
  loader: "0.20.1"
  fabricApi: "0.165.2+26.3"
publish:
  common:
    releaseType: release
    name: "Sample Mod 1.0.0+26.3"
    gameVersions: ["26.3"]       # CF에서는 문자열 gameVersionNames 기본값
    changelog: "Initial release"
  maven:
    url: https://maven.pkg.github.com/OWNER/REPOSITORY
    credentialsFromEnv: [GITHUB_ACTOR, GITHUB_TOKEN]
  modrinth:
    projectId: "AABBCCDD"       # Modrinth의 기존 프로젝트 ID (또는 지원되는 slug)
    tokenEnv: MODRINTH_TOKEN
    loaders: [fabric]            # 생략하면 fabric
  curseforge:
    projectId: 123456            # CurseForge 기존 프로젝트의 숫자 ID
    tokenEnv: CURSEFORGE_TOKEN
    gameVersionNames: ["26.3", "Fabric", "Client", "Server"] # 플랫폼별 태그가 필요하면 개별 덮어쓰기
```

이 예제의 프로젝트 ID·Maven 주소·토큰 환경변수명은 **예시용**입니다. 실제 기존 프로젝트·적절한 권한을 가진 작성자 토큰이 있어야 하며, 프로젝트 생성·계정 권한 취득·게임 버전 지원 여부를 플러그인이 추측하거나 자동 승인하지 않습니다. 변경 기록 파일은 소비자 프로젝트 루트 기준의 일반 파일이며, 프로젝트 밖 상대 경로·심볼릭 링크 탈출을 금지합니다. `changelog` 문자열과 `changelogFile`을 둘 다 지정할 수 없습니다.

**스키마 버전:** YAML의 `schema`는 이제 **선택적**입니다. 생략하면 플러그인에 구현된 최신 문법(현재 **1**)을 적용하며, 명시적으로 `schema: 1`을 적어 v1을 고정할 수 있습니다. 존재하지 않는 명시적 버전은 FA003으로 거부합니다.

**공통 게시 레이어:** `publish.common`의 `releaseType`·`name`·`gameVersions`(문자열 배열)·`changelog`/`changelogFile`·`dependencies`는 **명시적으로 선언한 Modrinth/CurseForge 대상에만** 상속합니다. `name`은 Modrinth `versionName`과 CurseForge `displayName`에, `gameVersions`는 Modrinth `gameVersions`와 CurseForge의 **문자열** `gameVersionNames`에 대응합니다. CurseForge 숫자 버전 ID `gameVersions`는 추론하지 않습니다. 공통 의존성 항목은 `{slug, type, modrinthId?, curseforgeId?}`이고 타입 `required/optional/incompatible/embedded`를 플랫폼별 타입으로 변환합니다. 플랫폼별 동일 ID/slug는 덮어쓰고 신규 항목은 추가하며, 명시적 `[]`로 해당 플랫폼 상속 목록을 비웁니다. 배포 대상의 `projectId`·`tokenEnv`는 항상 개별 블록에 작성합니다. `common`만으로 업로드 대상이 만들어지지 않습니다. 자세한 예제는 [HUMAN.md](HUMAN.md#share-release-fields-with-publishcommon)를 참고하세요.

### 정확한 YAML 형태와 기본값

- `publish.maven`: `url`은 **HTTPS URL만** 허용합니다(쿼리·fragment·사용자명/암호가 들어간 URL 금지). `credentialsFromEnv: [사용자명_환경변수, 암호_환경변수]`는 이름 2개만 받으며 두 값은 **게시 작업 실행 시** 제공받습니다. `credentialsFromEnv` 생략 시 인증 없이 시도합니다. 게시 artifact 좌표는 `mod.group:(mod.archivesName ?: mod.id):mod.version`이고, 유효한 `mod.group`이 필수입니다. GitHub Packages와 일반 Maven HTTPS 저장소가 대상입니다.
- `publish.modrinth`: `projectId`(문자열), `tokenEnv`(환경변수 이름), `releaseType`(`release|beta|alpha`, 기본 `release`), `versionName`(생략 시 모드명+버전), `versionNumber`(생략 시 `mod.version`), `gameVersions`(비어 있지 않은 문자열 배열, 기본 `[minecraft.version]`), `loaders`(기본 `[fabric]`), `environment`(선택: Modrinth가 정한 9개 설치 측면 enum), `changelog` 또는 `changelogFile`, `dependencies`(선택)가 지원됩니다. `dependencies`는 `{projectId, type: required|optional|incompatible|embedded}` 형태의 배열이며 플랫폼 ID를 **사용자가 명시한 경우에만** 게시합니다. Maven 또는 Loader의 `mod.depends` 좌표만으로 외부 Modrinth 프로젝트 ID를 추론하지 않습니다.
- `publish.curseforge`: `projectId`(양수 정수), `tokenEnv`(환경변수 이름), `releaseType`(기본 `release`), `displayName`(생략 시 모드명+버전), `gameVersionNames`(기본 `[minecraft.version, Fabric, Client, Server]`이고 `mod.environment`이 client/server이면 해당 측면만 포함), `gameVersions`(선택, 작성자가 확인한 **정수** 버전 ID 배열), `changelog` 또는 `changelogFile`, `changelogType`(`text|markdown|html`, 기본 `markdown`), `manualRelease`(기본 `false`), `relations`(선택)가 지원됩니다. `relations` 항목은 `{slug, type: embeddedLibrary|incompatible|optionalDependency|requiredDependency|tool}`이고, 정확한 대상 판별이 필요하면 `projectId` 문자열도 명시할 수 있습니다. 외부 플랫폼의 잘못된 ID를 플러그인이 고쳐서 추측하지 않습니다.
- 프로필로부터 유추 가능한 **플랫폼 구분·모드 버전·배포 JAR**만 자동 구성합니다. Modrinth `environment`의 기본값은 `mod.environment`의 `*`/`client`/`server`를 각각 `client_and_server`/`client_only`/`server_only`로 대응시키지만, Loader의 양측 로드 가능 여부만으로 **서버/클라이언트에 필수 설치인지** 결정할 수는 없습니다. 선택한 모드의 실제 지원 방식이 다르면 `publish.modrinth.environment`를 명시해 덮어써야 합니다. 서로 다른 플랫폼의 의존성 ID, 버전별 태그 지원 여부, 다중 게임 호환성은 자동 추론하지 않습니다.
- `publish`/각 하위 섹션의 알 수 없는 키, 빈 필수 값, 잘못된 환경변수명, 상대 경로 탈출, 중복/모호한 원격 Maven 설정은 기존 `FA003` 형식으로 조기에 거부합니다. 토큰·인증 비밀번호의 원문을 YAML/POM/`.module`/Gradle 로그/오류 메시지/검증 기록에 저장하지 않습니다.

### Gradle 작업과 실행 시점의 안전 경계

설정 플러그인이 YAML을 읽는 **구성 시점에는 HTTP 요청이나 토큰 확인을 하지 않습니다**. 실제 게시 작업이 호출될 때만 빌드·검증이 선행되며, 정상 생산 JAR(Minecraft 26.3 non-remap `jar`, 레거시 remap `remapJar`)의 **루트 `fabric.mod.json` 1개**를 확인한 뒤 해당 파일만 전송합니다. 개발용·sources JAR을 메인 릴리스로 착각해 업로드하지 않습니다.

```powershell
gradle build                        # 빌드만 수행; 절대 업로드하지 않음
gradle publishFabricAmperModrinth    # Modrinth만 (설정되어 있을 때)
gradle publishFabricAmperCurseForge  # CurseForge만 (설정되어 있을 때)
gradle publishFabricAmperMaven       # 원격 Maven만 (설정되어 있을 때)
gradle publishFabricAmper            # 구성된 대상 전체 게시 (명시적 요청)
gradle publish                       # Gradle 표준 Maven publish + 선택한 플랫폼 게시
```

`publishFabricAmper`는 새 `publish:`에서 **명시적으로 지정한 대상만** 모읍니다. 반면 Gradle 표준 `publish`는 기존 `maven-publish` 게시 태스크까지 포함합니다. 특히 **`fabric/api`와 `fabric/library`는 플랫폼만 지정했더라도 표준 `publish` 실행 시 소비자 프로젝트 자체의 `build/fabric-amper/maven-repository`에 로컬 Maven 게시도 수행**합니다. 이는 같은 원격 태스크를 두 번 업로드한다는 뜻은 아니지만 두 명령의 부작용 범위가 같지도 않습니다. 특정 플랫폼만 게시하려면 해당 `publishFabricAmper...` 작업을 사용하세요. 하나의 게시 작업으로 여러 사이트에 보내는 경우 **분산 트랜잭션이 아니므로 일부만 성공할 수 있습니다**. HTTP POST는 중복 버전 생성을 피하기 위해 자동 재시도하지 않으며, 이미 게시된 버전에 무단 덮어쓰기도 하지 않습니다. HTTP 401/403·중복/유효성 오류·429/5xx는 상태 코드와 해당 플랫폼의 오류 유형만 보여주고 토큰·요청 본문·서버 응답 본문을 출력하지 않습니다.

**Modrinth/CurseForge** 토큰은 명시적 게시 작업의 실행 시점에 Gradle Provider 환경변수로 읽습니다. **원격 Maven은 예외:** Gradle 네이티브 `maven-publish`의 `PasswordCredentials`가 문자열 속성이므로, 실제 원격 게시 태스크를 구성할 때 환경변수 Provider 값을 자격증명에 설정하고, `doFirst`에서 빈 값이면 FA003으로 거부합니다. 평범한 `help`·`build`·로컬 게시에서는 해당 원격 태스크가 실현되지 않아 비밀번호를 요구하지 않으며, `--dry-run`에서는 업로드/태스크 액션이 전혀 없으므로 명시적 자격증명을 설정하지 않습니다. Gradle 9.7.1은 **명시적 자격증명이 있는 네이티브 `PublishToMavenRepository`를 configuration-cache 비호환 태스크로 판정**하므로, 실제 원격 Maven 게시에 CC STORE/HIT를 약속하지 않습니다. 또한 `--offline`은 로컬 `127.0.0.1` HTTPS 게시도 차단합니다. 다른 계정 로그인·개인 Minecraft 프로필·Maven 사용자 홈·서버 EULA는 조작하지 않습니다.

### 실제 HTTP 요청 계약 — **외부 Modrinth·CurseForge에는 전송하지 않음**

| 대상 | 공식 엔드포인트·인증 | Multipart 본문 |
| --- | --- | --- |
| **Modrinth** | `POST https://api.modrinth.com/v2/version`, `Authorization: <token>`, 고유한 `User-Agent`. 토큰에는 프로젝트 버전 생성 권한이 필요 | `data`: JSON (`project_id`, `file_parts: ["file"]`, `primary_file: "file"`, `name`, `version_number`, `version_type`, `loaders`, `game_versions`, `featured: false`, `dependencies: []` 기본, `changelog`, `environment`), `file`: 실제 생산 JAR |
| **CurseForge** | `POST https://minecraft.curseforge.com/api/projects/{projectId}/upload-file`, `X-Api-Token: <token>`. **일반 조회용 `api.curseforge.com/v1`과 다른 API** | `metadata`: JSON (`changelog`, `changelogType`, `displayName`, `releaseType`, `gameVersionNames` 및 선택한 정수 `gameVersions`, `isMarkedForManualRelease`, 작성자가 명시한 `relations.projects`), `file`: 실제 생산 JAR |
| **Maven** | Gradle `maven-publish`의 `MavenPublication.from(components["java"])`, HTTPS 리포지터리와 환경변수 기반 `PasswordCredentials` | 게시 라이브러리의 프로덕션 JAR, 선택 소스 JAR, 올바른 POM 및 Gradle `.module`; 소스 JAR/컴포넌트 중복 게시 금지 |

CurseForge `releaseType`은 작성자 업로드 API에서 **문자열** `release|beta|alpha`입니다. 다른 CurseForge 조회 API에서 반환하는 숫자 enum과 혼동하지 않습니다. CurseForge `gameVersionNames`는 플랫폼 태그 이름이므로 버전 문자열을 수치 ID로 단정하거나 임의의 고정 `Fabric` ID를 만들어내지 않습니다. Modrinth `file_parts`는 multipart 파트 이름과 정확히 일치해야 하며, 토큰은 `Bearer ` 접두사를 자동 추가하지 않습니다.

**공식 근거 (2026-11-13에 확인):** [Modrinth Create Version](https://docs.modrinth.com/api/operations/createversion/), [Modrinth API Overview: token/User-Agent/rate limit](https://docs.modrinth.com/api/), [CurseForge 작성자 Upload API (2026-09-27 업데이트)](https://support.curseforge.com/support/solutions/articles/9000197321), [CurseForge 게시 심사](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project), [CurseForgeGradle](https://github.com/Darkhax/CurseForgeGradle). 외부 API 테스트·실제 게시를 수행하지 말라는 사용자 요청 때문에 **유효 HTTP 전송·인증·원격 심사 성공은 미검증**으로 남깁니다.

## 라이브러리/API 제품 구현 계약 (2026-11-13)

> **구현·검증 계약:** 이 절은 기존 `fabric/mod` 명세를 확장하는 라이브러리 제품의 동작 기준입니다. 신규 API/별칭의 실제 Maven 게시·다운스트림 의존성 TestKit 및 기본 클린 빌드는 아래에 기록한 범위에서 통과했습니다. 다만 기존 `specs.md` G1~G30 전체 및 출하 게이트가 닫힌 것은 아니므로 **정식 출시 승인과 구별**합니다.

### 제품 선택과 결과물

- `product: fabric/mod`: 기존 일반 모드 빌드 동작 및 YAML 호환성을 그대로 유지합니다. Maven 게시를 자동 적용하지 않습니다.
- `product: fabric/api`: Fabric Loader가 인식하는 **실제 Fabric 라이브러리 모드**를 빌드합니다. `fabric.mod.json`, 진입점·Mixin·Class Tweaker·리소스 검증·Loom/실행 작업을 그대로 제공하면서 `java-library`와 `maven-publish`를 자동 적용합니다.
- `product: fabric/library`: `fabric/api`의 **완전한 별칭**입니다. 내부에서는 같은 API 제품 프로필로 정규화하며 빌드 작업, 출력 JAR, POM·Gradle Module Metadata, 배포 방식이 같아야 합니다. 생성된 Loader JSON에는 제품 이름을 넣지 않습니다.
- 모든 제품에서 소비자 입력 파일은 `settings.gradle.kts` 하나와 `mod.yaml` 또는 `mod.yml` 하나입니다. 소비자가 `build.gradle(.kts)`나 `fabric.mod.json`을 작성할 필요가 없습니다.
- API 제품은 Java 25/Minecraft 26.3 non-remap 경로를 기본으로 사용합니다. **레거시 Minecraft 1.21.11/Java 21 API 제품도 실제 로컬 Maven 게시 TestKit에서 최종 `remapJar`·`remapSourcesJar`의 게시 바이트 일치를 PASS**했습니다. 이는 레거시 클라이언트·서버 실제 실행 및 원격 Maven 업로드의 성공 증거는 아닙니다.
- Java 소스 JAR은 기존 `settings.java.sourcesJar: true` 기본값을 사용합니다. 명시적 `false`는 존중하되, 소비자를 위한 API 게시에서는 소스 게시를 권장합니다. Javadoc JAR, Kotlin, client/main 분할, JAR-in-JAR은 임의로 켜거나 확장하지 않고 각각 해당 YAML 설정을 따릅니다.

### API 라이브러리의 최소 설정 예제

```yaml
# yaml-language-server: $schema=./fabric-amper.schema.json
schema: 1
product: fabric/api           # fabric/library 역시 동일하게 동작
mod:
  id: sample-api
  name: Sample API
  version: 1.0.0
  group: org.example.fabric   # API Maven 게시에는 필수
  archivesName: sample-api    # 미지정 시 mod.id
  license: MIT
  description: Example Fabric library mod
  environment: "*"
  entrypoints:
    main:
      - org.example.api.SampleApiMod
minecraft:
  version: "26.3"
  mappings: mojang
  loader: "0.20.1"
  fabricApi: "0.165.2+26.3"
settings:
  java:
    release: 25
    sourcesJar: true
  loom:
    splitEnvironmentSourceSets: false
repositories:
  - mavenCentral
dependencies:
  # API에서 공개되는 타입: 다운스트림 compile 및 runtime에 전이
  - maven: "org.example:public-contract:1.2.3"
    exported: true
  # 구현 내부용: 다운스트림 runtime에는 전이하지만 compile API에는 비노출
  - maven: "org.example:internal-helper:2.0.0"
    scope: all
  # compile만 필요한 개발 도구: Maven POM 필수 의존성에 넣지 않음
  - maven: "org.example:compile-annotations:1.0.0"
    scope: compile-only
```

위 예시의 `org.example` 의존성 좌표는 **문법 설명용 가상 값**입니다. 실제 빌드에서는 접근 가능한 Maven의 고정 버전 좌표로 교체해야 합니다. `settings.gradle.kts`는 앞 절의 기존 Settings 플러그인 단일 적용 예제를 그대로 사용합니다. `mod.group`은 API 제품에서 빈 값으로 추측하지 않으며, Maven 좌표는 `groupId = mod.group`, `artifactId = mod.archivesName ?: mod.id`, `version = mod.version`입니다.

### 자동 Maven 게시 계약

API 제품을 읽으면 소비자 프로젝트에 `java-library`, `maven-publish`, `mavenJava` 게시를 자동 생성합니다. **아무 게시 명령도 실행하지 않은 구성·빌드 단계에서 원격에 업로드하면 안 됩니다.** 일반 모드에는 자동 게시를 만들지 않습니다. 게시물은 최종 실행 가능 Fabric 모드 JAR(루트 `fabric.mod.json` 정확히 1개), 선택한 소스 JAR, `pom.xml`, Gradle `.module`을 포함해야 합니다. 이미 Java 컴포넌트에서 생성되는 소스 JAR을 두 번 추가하지 않습니다.

기본 로컬 리포지터리는 **소비자 프로젝트 내부** `build/fabric-amper/maven-repository/`이며, 게시 작업은 `publishMavenJavaPublicationToFabricAmperLocalRepository`입니다. `publishToMavenLocal`은 사용자가 명시적으로 실행한 경우에만 `~/.m2`에 씁니다. 추가 원격 Maven 저장소는 아래와 같이 **명시적으로** 지정한 경우에만 작업이 생깁니다.

```yaml
publishing:
  repository:
    url: https://maven.example.org/releases
    usernameEnv: FABRIC_AMPER_MAVEN_USERNAME
    passwordEnv: FABRIC_AMPER_MAVEN_PASSWORD
```

- `publishing`은 API/라이브러리 제품에서만 허용되는 선택 항목입니다. `repository` 없이 API 제품을 게시하면 로컬 리포지터리만 사용합니다. 지원하는 원격 URL은 인증정보·쿼리 문자열·fragment가 포함되지 않은 `https://` 주소이며 명시적 게시 작업 이름은 `publishMavenJavaPublicationToFabricAmperRemoteRepository`입니다.
- `usernameEnv`와 `passwordEnv`는 **비밀 값이 아닌 환경변수의 이름**입니다. 환경변수가 지정되지 않으면 URL이 인증 없이 접근 가능한 경우에만 원격 게시를 시도할 수 있습니다. YAML·POM·`.module`·로그에 비밀번호, 토큰, 인라인 `user:pass@host`를 기록하지 않습니다. 민감한 설정을 테스트 파일에 하드코딩하지 않습니다.
- `publish`를 명시 실행하면 Gradle이 구성된 Maven 저장소들로 게시하므로 원격 저장소를 설정한 사용자는 먼저 개별 로컬 게시 작업으로 결과를 검증해야 합니다. 이 **기존 `publishing.repository` 블록 자체에는 CurseForge/Modrinth 업로드 기능이 없으며**, 새 `publish.modrinth`/`publish.curseforge` 블록에서만 각 플랫폼 업로드 작업을 별도로 구성합니다. Maven Central·Gradle Plugin Portal도 자동 등록·공개 게시하지 않습니다.
- `publishing`의 알 수 없는 키, 잘못된 URL, 일반 `fabric/mod`에서의 사용은 기존 strict YAML 검증 체계의 설정 오류로 처리합니다. Gradle configuration cache 사용을 위해 플러그인 적용 및 등록 작업에서 외부 네트워크를 eager resolve하지 않습니다.

### POM / 전이성 정책 (구현 및 독립 소비자 테스트의 수락 조건)

게시의 진실 원본은 Java 라이브러리 컴포넌트와 Loom의 실제 게시 variant입니다. `from(components["java"])`를 우선 사용하고 POM XML을 손으로 추측해 조립하지 않습니다. 변형별 정보가 풍부한 Gradle `.module`과 Maven POM 모두 생성하되, **Maven POM은 Gradle의 모든 variant·client 환경 구분을 완벽하게 표현할 수 없다는 제약**을 명시합니다. 이 기능에서 ‘정확한 전이적 의존성’은 다음의 지원 가능한 범위를 의미합니다.

| YAML 의존성 | API 라이브러리 Gradle 구성 | Maven POM 결과 | 다운스트림 효과 |
| --- | --- | --- | --- |
| `scope: all`, `exported: true` | `api` (레거시 모드 의존성은 `modApi`) | `compile` | 공개 API를 컴파일·실행에 전파 |
| `scope: all`, `exported: false` | `implementation` / `modImplementation` | `runtime` | 공개 컴파일 API에서는 숨기고 실행 시 전파 |
| `scope: runtime-only` | `runtimeOnly` / `modRuntimeOnly` | `runtime` | 실행 시에만 전파 |
| `scope: compile-only` | `compileOnly` / `modCompileOnly` | 없음 | 소비자의 필수 전이 의존성이 아님 |
| `target: client` | 분리된 client 구성 | 기본 main Java POM에 없음 | 서버/공통 API로 전파하지 않음 |
| `test-dependencies` | 테스트 구성 | 없음 | 게시 의존성에 포함되지 않음 |
| `include: true` + 유효한 `scope` | 선택한 구성 **및** Loom `include` | 해당 scope에 따라 정확히 한 번 | Loader nested JAR 및 Maven 소비자 의존성은 별개 역할 |

명시적으로 `exported: true`인 API만 공개하며 일반 의존성을 일괄 `api`로 승격하지 않습니다. `include`는 **번들링 여부**이므로 `exported`와 동의어가 아니고, 중복 POM 항목을 만들거나 외부 전이성을 무조건 제거하는 옵션이 아닙니다. Minecraft·Fabric Loader·Fabric API 플랫폼 의존성은 최종 Java/Loom variant가 노출하는 스코프와 실제 Loader `depends`를 각각 검사하고, 조용히 모든 POM 의존성을 `compile`로 바꾸지 않습니다. `mod.depends`의 Loader 실행 요구사항은 Maven 전이성과 다른 메타데이터이므로 계속 독립 생성·검증합니다.

Maven/Modrinth 쪽 원본 모듈이 POM이나 올바른 메타데이터를 제공하지 않으면 Fabric Amper가 **상위 게시자의 누락된 전이 관계를 추론할 수는 없습니다**. 특히 Modrinth Maven의 artifact 다운로드가 저자 운영 Maven의 전이 의존성과 동일하다고 가정하지 않습니다. 독립 Gradle/Maven 소비자로 직접 `api`·`implementation`·`runtime-only`·`compile-only`·`include`를 검사하고, `fabric/library` 별칭 및 레거시 remap variant도 실제 POM·`.module`·JAR로 확인해야 통과입니다.

예를 들어 다운스트림 Gradle 모드는 게시 리포지터리를 등록하고 `implementation("org.example.fabric:sample-api:1.0.0")`으로 라이브러리를 가져올 수 있습니다. 그 프로젝트가 `public-contract`의 공개 타입은 컴파일하고 `internal-helper`의 내부 타입을 컴파일 API로 사용하지 못하며, 실행 클래스패스에서는 모두 필요한 의존성을 확인해야 합니다. **이는 테스트 대상 예시이지 이 가상 좌표가 실재함을 의미하지 않습니다.**

### IDE 완성과 SchemaStore 제출

새 기능 구현과 수락 검증 후 프로젝트 루트에 **독립 파일** `fabric-amper.schema.json`을 제공합니다. `schema`, 세 제품 값, 기존 Loader/YAML 구조, 의존성 축약형과 상세형, `publishing`을 실제 strict decoder와 일치시켜 자동완성 및 잘못된 키 표시를 지원합니다. SchemaStore의 폭넓은 IDE 호환성을 우선하여 JSON Schema **Draft-07**을 사용하며 조건 분기는 `oneOf`/`if`/`then`과 `definitions`로 기술합니다. `mod.custom`처럼 의도적으로 사용자 확장을 받는 곳은 닫지 않습니다. Schema `default`는 문서화 힌트이지 플러그인 값을 자동 삽입하는 기능이 아닙니다.

VS Code의 YAML 확장에서는 예제 첫 줄의 `# yaml-language-server: $schema=./fabric-amper.schema.json` 주석이나 `yaml.schemas` 설정으로 연결합니다. **상대경로 예제는 해당 스키마 파일을 소비자 프로젝트에도 복사했을 때만 유효**하며, SchemaStore 등 외부 URL로 제공하는 경우에는 실제 배포된 URL로 바꿉니다. IntelliJ IDEA에서는 JSON Schema Mappings를 통해 `mod.yaml`/`mod.yml`과 파일을 연결할 수 있습니다. 두 IDE 모두 플러그인에 허용하지 않은 YAML 루트 `$schema:` 키를 **추가해서는 안 됩니다**. SchemaStore 등록 시 전역 `mod.yaml` 같은 지나치게 일반적인 파일 패턴의 자동 연결은 피하고, 사용자가 직접 schema URL/매핑을 선택하도록 안내합니다. SchemaStore 목록 등록 자체는 사용자가 직접 진행합니다.

### 연구 근거 및 확인 범위

- 실제 owo-lib 라이브러리 빌드/메타데이터(레거시 브랜치): [공개 Gradle 설정](https://github.com/wisp-forest/owo-lib/blob/braid-ui/build.gradle), [Loader metadata](https://github.com/wisp-forest/owo-lib/blob/braid-ui/src/main/resources/fabric.mod.json), [소비자 설정](https://docs.wispforest.io/owo/setup). `include api(...)`, `from components.java`, `withSourcesJar()`가 관찰되었지만 이를 모든 26.3 프로젝트의 필수 구성이라 주장하지 않습니다.
- [Fabric 예제 26.3 Gradle 설정](https://github.com/FabricMC/fabric-example-mod/blob/26.3/build.gradle), [Fabric Loom 문서](https://docs.fabricmc.net/develop/loom/), [GeckoLib 5 Fabric 사용 안내](https://wiki.geckolib.com/docs/geckolib5/setup/fabric/quick-reference/). GeckoLib의 다중 플랫폼 배포와 Maven 게시를 혼동하지 않습니다.
- [Gradle Java Library](https://docs.gradle.org/current/userguide/java_library_plugin.html), [Maven Publish](https://docs.gradle.org/current/userguide/publishing_maven.html), [Module Metadata](https://docs.gradle.org/current/userguide/publishing_gradle_module_metadata.html), [SchemaStore CONTRIBUTING](https://github.com/SchemaStore/schemastore/blob/master/CONTRIBUTING.md), [YAML Language Server](https://github.com/redhat-developer/yaml-language-server/blob/main/README.md).

## 환경과 플러그인

- 플러그인 ID: `dev.fabric-amper.settings` (소비자 설정에서 이 플러그인 **하나만** 적용), 내부 프로젝트 플러그인 `dev.fabric-amper.project`.
- Fabric Amper 버전: `0.1.0`.
- 기본값: Minecraft **26.3**, Java **25**, Fabric Loader **0.20.1**, Fabric API **0.165.2+26.3**, Mojang 공식 클래스명, non-remapping Fabric Loom.
- 개발 당시 검증된 기본 Gradle: **9.7.1**. 별도 소비자에서는 Gradle **9.7.0 + Loom 1.18.4** 및 **9.9.1 + Loom 1.19.4** 조합의 컴파일·배포 JAR 빌드도 검증했습니다. 이들은 빌드 시점에 선택한 각각의 플러그인 바이너리로 검증한 조합이며, 소비자 YAML에서 Loom 버전을 임의로 바꿀 수 있다는 뜻이 아닙니다.

## 최소 소비자 프로젝트

디렉터리 구조:

```text
my-fabric-mod/
├── settings.gradle.kts
├── mod.yaml
└── src/main/java/org/example/ExampleMod.java
```

플러그인이 **실제로 게시되어 있는 저장소**를 사용할 경우의 `settings.gradle.kts` 예시입니다. 현재 Fabric Amper `0.1.0`이 공개 Gradle Plugin Portal에 게시됐다는 뜻은 아닙니다.

```kotlin
pluginManagement {
    repositories {
        // Fabric Amper의 plugin marker와 본체가 게시된 저장소를 먼저 추가합니다.
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("dev.fabric-amper.settings") version "0.1.0"
}
```

프로젝트 내부에 임시 로컬 Maven 게시물을 만들려면 **Fabric Amper 개발 프로젝트**에서 다음을 실행합니다. 전역 Maven Local이나 타 프로젝트를 변경하지 않습니다.

```powershell
gradle publishAllPublicationsToG29ProbeRepository -PfabricAmperProbeMavenRepo=true
```

그 후 소비자의 `pluginManagement.repositories`에 다음을 **다른 원격 저장소보다 앞에** 추가할 수 있습니다. 실제 상대경로는 소비자 폴더 위치에 맞춰야 합니다. 이 임시 게시물은 Fabric Amper 측 `clean`에서 사라지므로 필요하면 다시 출판합니다.

```kotlin
maven { url = uri("../Fabric Amper/build/g29-probe/maven-repo") }
```

`mod.yaml` 최소 예시:

```yaml
schema: 1
product: fabric/mod
mod:
  id: example_mod
  version: "1.0.0+26.3"
  group: org.example
  archivesName: example-mod
  name: "Example Mod"
  environment: "*"
  entrypoints:
    main:
      - org.example.ExampleMod
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
    enabled: false
  loom:
    splitEnvironmentSourceSets: false
  resources:
    failOnExistingFabricModJson: true
repositories:
  - fabric
  - mavenCentral
```

`src/main/java/org/example/ExampleMod.java`:

```java
package org.example;

import net.fabricmc.api.ModInitializer;

public final class ExampleMod implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("Example Mod initialized by Fabric Loader");
    }
}
```

필요한 Gradle과 JDK 25가 준비된 환경에서 소비자 폴더에서 `gradle help`, `gradle build`, `gradle runClient`를 차례로 사용할 수 있습니다. `build/libs/example-mod-1.0.0+26.3.jar`가 배포 파일이며, 플러그인이 `fabric.mod.json`을 자동 생성해 JAR 루트에 정확히 하나만 포함하는지 검증합니다. 소비자에 수동 `fabric.mod.json`을 추가하지 마세요. `mod.yml`을 사용해도 되지만 **`mod.yaml`과 `mod.yml`을 동시에 두면 FA001**입니다.

`runServer`는 별도의 테스트 서버에서만 사용하세요. 플러그인은 Minecraft EULA를 자동 승인하지 않으며, 기존 서버의 EULA·월드·계정·인증 설정을 변경하지 않습니다. 아직 전용 서버 실제 기동 수락은 완료되지 않았습니다.

### IntelliJ에서 열기

IntelliJ IDEA에서 소비자 폴더 자체를 **Gradle 프로젝트로 열고**, 신뢰 여부와 JDK 25/Gradle 환경은 사용자가 직접 확인합니다. `build.gradle.kts`를 새로 만들지 마세요. Gradle 도구 창에서 `compileJava`, `generateFabricModJson`, `runClient` 등의 작업과 Java/Kotlin 소스셋, 외부 의존성 표시 여부를 검사합니다. `mod.yaml`이나 `mod.yml` 수정 뒤에는 Gradle 도구 창의 **Reload All Gradle Projects**로 수동 동기화할 수 있습니다. 자동 동기화는 설치된 IntelliJ의 Gradle 자동 재로드 설정에 영향을 받으며, **현재 실제 GUI에서 최초 import·수동·자동 Sync를 각각 수락 검증하지 못했습니다**. Gradle Tooling API의 IdeaProject/모델 조회 성공을 실제 GUI 동기화 성공으로 간주하지 않습니다.

## 주요 스키마 기능

- `mod.entrypoints`, `mod.mixins`, `mod.accessWidener`, `mod.custom` 등 Loader 메타데이터를 YAML에서 표현합니다.
- `dependencies`의 Maven 및 Modrinth 좌표, `compile-only`, `runtime-only`, `exported`, `include`, `target: client`를 지원합니다. `include`는 실제 배포 JAR 안에 nested JAR과 Loader `jars` 메타데이터를 만듭니다.
- `settings.loom.splitEnvironmentSourceSets: true`면 client 코드 및 client 전용 Gradle 의존성을 분리합니다. `target: client`인 라이브러리가 자동으로 Loader의 전역 `depends`가 되지는 않습니다.
- `settings.kotlin.enabled: true`는 검증된 고정 KGP/FLK 버전을 사용합니다. `enabled: false`여도 현재 배포 플러그인의 정적 전이 클래스패스 때문에 KGP JAR 다운로드 비용이 남아 있습니다.
- 미러 경로 `.gradle/fabric-amper/loom/`의 구성 시점 `fabric.mod.json`과 최종 생성 메타데이터는 동일한 JSON 바이트를 사용하도록 구성합니다.

### 읽기 전용 진단·배포 미리보기

유효한 settings-only 소비자 폴더에서 아래 Gradle 작업을 **명시적으로** 호출할 수 있습니다. 새 YAML 키는 필요하지 않으며 `build`나 `check` 시 자동 실행되지 않습니다.

```powershell
gradle fabricAmperDoctor
gradle fabricAmperExplain
gradle fabricAmperReleasePlan
gradle fabricAmperPortingReport -PfabricAmperPortTarget=26.3
```

- `fabricAmperDoctor`: Minecraft/Java/Gradle/Loom 프로필, Kotlin·소스셋 설정과 Windows 경로 길이 위험의 **추정치**를 출력합니다. 실제 설치된 Loom 버전을 확실히 판별하지 못하면 `UNKNOWN`으로 표기하며, 캐시를 삭제하지 않습니다.
- `fabricAmperExplain`: Maven/Modrinth 아티팩트 `dependencies`와 Loader `mod.depends`, 배포 사이트 관계 `publish.common.dependencies`를 혼동하지 않고, scope/target/include/export와 Loader 기본값/작성값을 보여줍니다. 저장소를 조회하지 않습니다.
- `fabricAmperReleasePlan`: 검증된 실제 JAR의 해시·Loader ID/버전과 상속/오버라이드된 플랫폼별 메타데이터를 **오프라인 미리보기**합니다. 비밀 토큰/변경 기록 본문을 출력하거나 업로드하지 않습니다. 외부 ID와 호환 태그는 여전히 별도 검증 대상입니다.
- `fabricAmperPortingReport`: 목표 버전과 현재 버전 간 매핑/Java/리소스 변경 **수동 체크리스트**입니다. 실제 소스·Mixin의 자동 포팅 기능이 아닙니다.

Windows에서는 `fabric-amper.bat diagnose`, `explain`, `plan`, `port 26.3`과 `doctor --full` / `doctor --paths`를 사용할 수 있습니다. 사용법은 [HUMAN.md](HUMAN.md#read-only-developer-diagnostics-and-release-preview), 추가 개선 후보와 미해결 한계는 [PROBLEMS.md](PROBLEMS.md)를 참조하세요.

## 검증 상태와 제한

독립 실제 소비자의 Gradle 빌드, 두 G13 조합, G25 native `.module`, G20 병렬 미러 복구, 공식 Loader 버전 조건 비교, Maven JAR-in-JAR의 실제 Loader 로딩과 라이브러리 호출, Modrinth 실물 client-only compile, YAML custom 인터페이스 주입의 양성·음성 Java 컴파일, G19 실제 외부 Loom 1.18.4와 기본 Loom 1.17.21의 분리 및 `FA010` 충돌 진단, G27 client-only Mixin/의존성 분리 등을 선별 검증했습니다.

**출시 전 남은 조건:** 실제 IntelliJ 최초 import·수동/자동 GUI Sync, 일부 외부 TLS/Modrinth 오류 분류와 원격 Maven 인증, EULA 동의 후 전용 서버 완전 기동, 레거시 remap 게임 런타임, Kotlin 비활성 상태의 선택적 지연 KGP 최적화 및 일부 G1~G30 전체 행렬. 실제 외부 KGP 2.4.20 사전 적용 vs 번들 2.4.21 충돌은 후속 TestKit에서 `FA010` **1 PASS**로 확인했습니다. **새로 격리 실행한 G15 메인·클라이언트·게임 틱 1 PASS 및 G17 실제 인터페이스 주입·기본 메서드 호출·게임 틱 1 PASS**를 각각 무캐시 실행·XML로 확인했습니다. 단, 이전 G17 런타임은 게임 틱 전에 Windows `0xC0000409`로 종료돼 **별도 JUnit FAIL**이었으며 당시 실패를 소급해 없애지 않습니다. 시스템별 네이티브 재현성이 보장된다는 뜻은 아닙니다. 별도 G27 테스트에서 `runServer`가 Fabric Loader와 모드 초기화까지 진입했지만, **EULA 동의가 없어 서버 완전 부팅은 차단**된 것도 관측했습니다. `eula.txt`를 자동 수락하지 않았습니다.

**신규 API/라이브러리 실소비자 검증:** 설정 파일과 YAML만 쓰는 별도 Fabric 라이브러리 A(`fabric/api`와 `fabric/library` 각각)를 실제 로컬 Maven 저장소에 게시하고, 독립 소비자 B가 API 공개 타입을 전이받아 컴파일하는지 확인했습니다. POM `compile`/`runtime` 범위, Gradle `.module` API/runtime variants, `compile-only`·테스트 의존성 미전파, JAR-in-JAR, 배포 JAR의 정확히 하나인 `fabric.mod.json`, 소스 JAR 및 별칭의 결과 일치에 대한 **opt-in TestKit JUnit 1 PASS / 0 FAIL**, outer **BUILD SUCCESSFUL in 1m 4s**입니다. XML은 `analysis/acceptance-evidence-2026-11-13/API-library-publishing-1PASS.xml`에 별도로 보관했습니다. 초기 두 실패는 테스트 fixture의 저장소 우선순위와 Kotlin DSL 콜백 구문이 원인이어서 **실제 fixture를 수정한 뒤 엄격한 검사 없이 PASS로 처리하지 않았습니다**.

**통합 게시 구현 이후 선택 실소비자 검증(2026-11-13):** 원격 게시의 안전한 `help`/`tasks`/실제 `build`와 플랫폼 `--dry-run`에 대해 `fabric/mod`·`fabric/api`·`fabric/library` **3 PASS**; Maven 없는 `fabric/mod`의 Modrinth+CurseForge 전용 게시 그래프 **1 PASS**; 실제 로컬 API Maven 게시의 configuration-cache `STORE → HIT → YAML 수정 MISS/STORE → HIT` **1 PASS**입니다. 레거시 Java 21 API의 실 `remapJar`·`remapSourcesJar` 로컬 Maven 게시 및 하위 소비자 컴파일 **1 PASS**, G24 Mixin/Class Tweaker/icon 리소스 변경 후 실제 재패키징 **1 PASS**, API/library 로컬 Maven 전이 회귀 **1 PASS**, 기존 G5 구성 캐시 회귀 **1 PASS**를 각 선택 실행 XML로 보존했습니다. 새로운 파일명 안전 검사와 레거시 Maven 자격증명 쌍 규칙, Draft-07 스키마 동등성 단위 회귀도 통과했습니다. 실제 원격 Modrinth/CurseForge API 업로드·원격 Maven 인증 테스트는 전혀 하지 않았습니다.

**추가 G5/G20/G30 오프라인 수락:** 독립 settings-only 소비자에서 **`settings.java.sourcesJar: true→false→true`** 변경 시 `tasks --all --configuration-cache --offline`의 **STORE→HIT→MISS→HIT→MISS→HIT 6회 PASS**와 `sourcesJar` 태스크 ON/OFF를 확인했습니다. **G20 손상 미러/삭제 아이콘의 두 GradleRunner 병렬 원자 복구 1 PASS**, **G30 `mod.yml→mod.yaml` 변경과 CC MISS→HIT 1 PASS**도 다시 검증했습니다. 근거는 `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-G5-java-setting-CC-6run-PASS.json`, `FOLLOWUP-RECHECK-FabricAmperParallelMirrorConsumerTest-1PASS.xml`, `FOLLOWUP-RECHECK-FabricAmperExtendedConsumerTest-1PASS.xml`입니다. 선택 검증 PASS는 아래 기본 JUnit 246개 수치에 중복 합산하지 않습니다.

원본 프로젝트의 **Gradle 9.7.1/JDK 25 `clean build validatePlugins --offline --no-daemon -Pkotlin.incremental=false`**는 **45 XML suites, 246 tests = 207 PASS / 0 FAIL / 0 ERROR / 39 SKIP**, `validatePlugins` PASS, **`BUILD SUCCESSFUL in 39s`**입니다. 이와 **별도로 소스 해시가 일치하는 프로젝트 내부 격리 스냅샷** `analysis/final-verification-2026-11-13/`에서 `clean build validatePlugins --offline --no-daemon --no-build-cache --rerun-tasks -Pkotlin.incremental=false`를 실행한 결과, **11개 task 전부 실제 실행·BUILD SUCCESSFUL in 1m 38s**, 동일한 **207 PASS / 39 SKIP / 0 FAIL·ERROR**를 확인했습니다. 중간의 캐시·fixture·네이티브 실패는 `QA_REPORT.md` §24에 분리 기록했습니다. 최종 Gradle 플러그인 `build/libs/fabric-amper-0.1.0.jar`는 **314,704바이트**, SHA-256 **`164CADA56DF0BEFBFB0AFABAE574F639A89795DFD86CA222AA8789F44C829F0A`**입니다. 소스 JAR **67,202바이트**, SHA-256 **`2032A41383A3534B2FF0B087586B968189C752D74029A545E95ACAA747F2B119`**, SchemaStore용 `fabric-amper.schema.json` **26,823바이트**, SHA-256 **`D5FD84098F1FD6AB926D504F23C69EC991AD17CBCF127B4C14308672B1BC5233`**입니다. 플러그인 마커·클래스 각각 1개, 중복 ZIP entry 0개이며, 격리 프로젝트 내부 G29 Maven 재게시 JAR·sources와 최종 산출물 **바이트 동일성 PASS**입니다.

이 JAR은 Minecraft `mods/`에 설치하는 모드가 아니라 **Gradle 빌드 플러그인**입니다. SchemaStore 등록은 사용자가 별도로 수행합니다. `specs.md`의 G1~G30 **전체 출하 승인은 `NOT APPROVED`**이며 **39 SKIP를 PASS로 간주하지 않습니다**. 후속 선택 검사에는 G19 실제 외부 KGP 충돌, FA011 내부 Gradle 예외 트리, G13/G25 독립 버전 매트릭스, G10·G15·G17 실제 Loader 런타임의 새 PASS와 예전 G17 네이티브 FAIL, G27 EULA 미동의 서버 Loader 초기화 및 EULA 거부, 원격 Maven 오프라인 dry-run CC가 포함됩니다. 별도 소비자 `analysis/ide-sync-probe/`의 Java/Kotlin/main/client 빌드와 `analysis/server-eula-probe/`의 모드 JAR 빌드·서버 Loader 탐지까지 관측됐지만, **실제 IDEA GUI 최초 Sync와 동의 후 전용 서버 완전 기동은 검증되지 않았습니다**. 최신 근거는 `QA_REPORT.md` §24, `analysis/acceptance-evidence-2026-11-13/G1-G30-followup-gate-matrix.md`, `FOLLOWUP-FINAL-clean-no-cache-JUnit-XML.zip`, `FOLLOWUP-FINAL-clean-no-cache-manifest.json`입니다. **이 문단은 §24–25 당시의 역사적 스냅샷이며, 아래 2026-11-13 후속 검증이 더 최신입니다.**

### 2026-11-13 추가 수락 결과와 마지막 산출물 (QA §26)

**G11/G30:** 실제 settings-only 독립 소비자에서 악성 YAML **11종(FA002/FA003)** 및 Class Tweaker 오류·삭제 아이콘·경로 traversal·의존성 충돌·이중 YAML 별칭·원본 `fabric.mod.json` **6종(FA001/FA008/FA009/FA012)**, 총 **17개의 실패 입력**에 대해 정확한 진단, 이전 정상 generated/mirror/배포 JAR·소스 바이트 보존, 구성 캐시 invalidation 및 유효한 YAML 복구 후 HIT를 검증했습니다. **G20:** JSON/Mixin/Class Tweaker 미러 변조 + 아이콘 미러 삭제를 동시에 가한 뒤, 두 GradleRunner의 병렬 `help`/`generateFabricModJson --configuration-cache --offline` 성공·4종 최종 바이트 복구·ZIP entry 중복 없음·임시파일 잔존 없음·CC 재사용을 JUnit **1 PASS**로 실측했습니다. 개별 파일의 원자 교체이며 전체 디렉터리 atomic transaction 및 IntelliJ GUI Sync 입증은 아닙니다.

**원격 Maven 실제 HTTP/CC의 정확한 경계:** 프로젝트 내부 별도 fixture의 Python `ThreadingHTTPServer`가 **`127.0.0.1`에만 바인드**되고, 자체 서명 SAN IP 인증서/JDK 전용 truststore를 이용한 HTTPS 연결과 인증 없는 GET에 대한 **Basic 401 challenge**를 실제 확인했습니다. 수정 전 Gradle 9.7.1 실제 네이티브 게시 태스크의 `task.repository` 직렬화 후 접근 오류를 발견·수정했습니다. 이후 시험에서는 **`--offline`이 loopback HTTPS PUT도 거부**했고, 명시적 인증값을 설정하는 네이티브 `PublishToMavenRepository`가 **configuration cache 호환 불가**로 판정되었습니다. 실제 인증된 native PUT과 CC STORE→HIT를 동시에 검증했다고 주장하지 않습니다. `--dry-run --configuration-cache --offline`에서 명시적 인증 설정을 생략하도록 보정하여 실제 선택 JUnit **1 PASS/0 FAIL**, **STORE→HIT**, 작업 전부 SKIPPED로 회복했으며, 보정 전의 회귀 **1 FAIL** XML도 보존했습니다. 외부 Modrinth/CurseForge 전송은 **0건**입니다.

**마지막 제품 빌드 (후속 변경 이후):** Gradle **9.7.1/JDK 25**, `clean build validatePlugins --offline --no-build-cache --rerun-tasks -Pkotlin.incremental=false` 실제 PASS, 기본 **46 XML suites / 247 tests = 207 PASS + 40 SKIP + 0 FAIL + 0 ERROR**. 원격 인증 코드를 보정한 최신 `build/libs/fabric-amper-0.1.0.jar`은 **315,071바이트**, SHA-256 **`2DC9F20238BED6284C0D80C898F47C13B49AC897DE41D819DBE8667274E00264`**, `-sources.jar`은 **67,690바이트**, SHA-256 **`E31E8461EA8F57A3FCBCEB04A2892BC6387D07DC29B696F63D677BE819320803`**, 스키마 SHA-256 **`D5FD84098F1FD6AB926D504F23C69EC991AD17CBCF127B4C14308672B1BC5233`**입니다. 실제 로컬 G29 Maven marker·게시 JAR/sources와 출력 바이트 일치 PASS. 증거는 `analysis/final-post-dryrun-correction-2026-11-13/run/manifest.json`, `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-POST-REMOTE-DRYRUN-FINAL-JUnit-XML.zip`, `FOLLOWUP-POST-REMOTE-DRYRUN-CC-RESTORED-1PASS.xml`, `FOLLOWUP-G11-G30-hostile-YAML-CC-preservation-PASS.json`, `FOLLOWUP-G11-resource-FA001-FA008-FA009-FA012-CC-PASS.json`, `FOLLOWUP-G20-FOUR-MIRROR-FILES-PARALLEL-CC-1PASS.xml`입니다. **현재 전체 출시 판정 `NOT APPROVED`**: 실제 IntelliJ GUI 최초 import/수동·자동 Sync, 직접 EULA 검토·동의 후의 dedicated server 완전 부팅, 구형 게임 실제 런타임 및 원격 native Maven 인증+CC 동시 수락은 별도 조건이 남아 있습니다. **40 SKIP는 PASS가 아닙니다.**

### 가장 최신 결과: FA003 원격 Maven 음성 수락 및 최종 JAR (QA §27)

§26 이후 **추가 제품 결함을 찾아 수정**했습니다. 실제 `publishMavenJavaPublicationToFabricAmperRemoteRepository --offline`에 자격증명 환경변수가 없을 때, 처음에는 Gradle의 `Value not set credentials.username` 오류가 플러그인의 `doFirst`보다 먼저 발생했습니다. 원격 게시 태스크의 native credential 입력이 미설정이 되지 않도록 빈 문자열을 임시값으로 설정하고, **전송 전** 원래 Gradle Provider의 환경변수를 검사해 없는 값/공백을 **FA003으로 명시적 거부**하도록 수정했습니다. 이 임시값을 실제 인증 수단으로 받아들이는 동작은 없습니다.

신규 독립 settings-only/`fabric/api` 소비자에서 오프라인 실 Gradle **음성 테스트 2/2 PASS**: (1) 자격증명 env 두 개가 없으면 실제 native remote publish task가 `FabricAmper FA003 publish.maven`으로 실패하며 YAML 입력 보존, (2) `http://127.0.0.1` URL은 `help` 설정 단계의 `FA003 publish.maven.url`로 거부되며 JAR을 생성하지 않습니다. 각각 기대한 **실패 코드 1**을 판정한 것이고 실제 성공 PUT이 아닙니다. 증거 `analysis/local-https-maven-probe-2026-11-13/negative-contract-v2-2026-11-13/manifest.json`과 두 단계 로그, 실패했던 첫 fixture도 보존합니다.

**최종 코드로 다시 수행한 결과:** Gradle **9.7.1/JDK25**, `clean build validatePlugins --offline --no-build-cache --rerun-tasks` **BUILD SUCCESSFUL**, **46 suites / 247 tests = 207 PASS·40 SKIP·0 FAIL·0 ERROR**, `validatePlugins` PASS, 실제 프로젝트 내부 G29 로컬 Maven 게시 artifact/markers와 최종 산출물 바이트 일치. 최신 `build/libs/fabric-amper-0.1.0.jar`은 **315,122 bytes, SHA-256 `13022B24ADE7C9809400FC6AEBE99E7751783A9DAE7EF8B8C53780FE70B698AD`**, sources JAR은 **67,799 bytes, SHA-256 `C3E69E8188B516F4F290588B3342F1D21BB24391658A75B4E26BEA3A5175B510`**, 스키마는 **26,823 bytes, SHA-256 `D5FD84098F1FD6AB926D504F23C69EC991AD17CBCF127B4C14308672B1BC5233`**입니다. 그 직후 최종 source에서 원격 Maven **`--dry-run --configuration-cache --offline` 실제 JUnit 1 PASS / 0 FAIL**, **STORE→HIT**, 태스크 전부 SKIPPED를 다시 확인했습니다.

최신 빌드 및 기본 JUnit XML 증거는 `analysis/final-post-missing-credential-fix-2026-11-13/run/manifest.json`, `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-POST-REMOTE-CREDENTIAL-NEGATIVE-FINAL-JUnit-XML.zip`, 선택 dry-run XML `FOLLOWUP-POST-REMOTE-FA003-DRYRUN-CC-1PASS.xml`에 있습니다. `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-FINAL-2026-11-13-remaining-verification-index.json`은 최신 JAR 해시·G11/G30 17종·G20 복합 1 PASS·원격 Maven 음성 2종·dry-run 1 PASS를 교차 점검한 완료 인덱스입니다. **이전 §24–26의 JAR 해시/수치는 각 당시에 유효했던 역사적 기록**이므로 최신 값과 혼동하지 마세요.

**아직 승인되지 않은 조건:** 실제 IntelliJ GUI 최초 import/수동·자동 Sync, 사용자 EULA 결정 후 dedicated server 완전 기동, 구형 Minecraft 게임 런타임, 네이티브 Maven의 **실제 인증된 HTTPS PUT과 구성 캐시 HIT의 동시 충족**은 완료 증거가 없습니다. Gradle `--offline`은 localhost HTTPS 게시도 차단하고 Gradle 9.7.1 native explicit PasswordCredentials는 구성 캐시 비호환입니다. 실제 Modrinth·CurseForge API 업로드는 사용자 금지에 따라 **0건**입니다. **G1–G30 전체 출하 승인 `NOT APPROVED`; 기본 40 SKIP는 PASS가 아닙니다.**

### 2026-11-14 후속: 실제 localhost HTTPS 네이티브 게시 성공 (QA §28)

기존 `--offline`의 localhost HTTPS 차단을 피하되 외부 네트워크 접근은 프로젝트 안의 **거부 전용 localhost HTTP(S) 프록시 설정**으로 제한한 독립 시험을 추가했습니다. Gradle 9.7.1의 실제 `publishMavenJavaPublicationToFabricAmperRemoteRepository`를 두 번 실행했고, **두 번 모두 `BUILD SUCCESSFUL`, 테스트 서버에서 Basic 인증된 성공 PUT 각 25건(총 50건)**을 관측했습니다. 메인 JAR, sources JAR, POM, Gradle module metadata의 서버 수신 바이트·SHA가 로컬 산출물과 동일하고 1·2차 SHA도 같음을 검증했습니다. GAV 및 JAR 루트에 하나뿐인 `fabric.mod.json`도 검사했습니다. 실제 Modrinth·CurseForge API 요청/업로드는 0건이며 개인 인증정보를 사용하지 않았습니다.

**중요한 구분:** 실제 네이티브 PUT은 **PASS**지만, Gradle 9.7.1은 명시적 Maven `PasswordCredentials`가 연결된 해당 태스크의 구성 캐시를 **두 번 모두 비활성화**했습니다. **실제 게시 configuration cache STORE→HIT는 여전히 미충족**이며, 별도로 PASS한 `--dry-run` CC 검사로 대체할 수 없습니다. 증거는 `analysis/local-https-maven-probe-2026-11-13/online-loopback-2026-11-14/manifest.json`, 두 `publish-0*-with-cc.log`, `QA_REPORT.md` §28. 최신 플러그인 main/sources JAR은 §27 해시와 동일합니다. 실제 IntelliJ GUI, EULA 후 서버, 레거시 게임 런타임과 원격 게시 CC가 남아 있으므로 **전체 출하 판정 `NOT APPROVED`**입니다.

독립 사후 증거 대조 스크립트 `analysis/acceptance-evidence-2026-11-13/verify_2026_11_14_native_https.py`의 실제 실행도 **PASS**했으며, 요약은 `FOLLOWUP-2026-11-14-real-native-HTTPS-PUT-summary.json`에 있습니다.
