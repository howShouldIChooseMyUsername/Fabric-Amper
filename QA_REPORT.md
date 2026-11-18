# Fabric Amper — Phase 3 QA 트리아지 보고서

**검토 기준:** 2026-11-11, `/vbc/Fabric Amper/specs.md` 및 당시 `src/main/kotlin`, `src/test/kotlin`, `src/test/resources` 정적 읽기. **소유권:** 이 보고서(`QA_REPORT.md`)만 작성. 생산 코드·테스트·설정 파일은 변경하지 않았다. **근거 수준:** `CONFIRMED`는 소스에서 직접 확인한 API/논리 오류이거나 사용자·Prime이 전달한 실제 실패 기록이다. `STATIC SUSPECT`는 재현 실행 전의 위험 가설이다. 실행되지 않은 테스트를 `PASS`로 기록하지 않는다.

## 1. 출하 판정 및 실행 증거

**판정: BLOCKED — 빌드 실패가 확인되어 Phase 4 수정이 먼저 필요.** 아래 Q-001은 현재 명시적으로 확인된 컴파일 오류이고, 나머지는 소스 대조 결과다. 아직 성공적인 배포 JAR, consumer fixture, Gradle configuration cache, 실제 Loader 인식 등의 실행 증거가 없다.

| 실행/검증 항목 | 관찰된 결과 | 근거·한계 |
| --- | --- | --- |
| 초기 Gradle Kotlin 컴파일(`:compileKotlin`) | **FAIL, 프로세스 exit 1** | Prime/작업자 보고: `src/main/kotlin/dev/fabricamper/loom/RepositoryConfigurer.kt:46`, `content.forRepository(repository)`에서 `MavenArtifactRepository`를 받은 호출과 Gradle API 서명 불일치. 원본 전체 명령문/로그·세션 ID는 이 QA 작업자에게 전달되지 않았으므로 임의로 구성하지 않는다. |
| 작업자 4 TestKit 통합 테스트 실행 시도 | **FAIL, exit 1 — 같은 `:compileKotlin` 선행 실패** | 테스트 실행 단계에 도달하지 않음. **`:test` NOT RUN**, 통합 테스트 PASS/FAIL 산정 불가. 보고된 시도에 쓰인 정확한 Gradle 명령 인자는 미제공. |
| 작업자 5 JUnit 단위 테스트 실행 시도 | **FAIL, exit 1 — 같은 `:compileKotlin` 선행 실패** | 테스트 실행 단계에 도달하지 않음. **`:test` NOT RUN**, 단위 테스트 PASS/FAIL 산정 불가. 보고된 시도에 쓰인 정확한 Gradle 명령 인자는 미제공. |
| 이 Phase 3 작업자의 명령·빌드·테스트 | **NOT RUN** | 사용자 지시대로 파일 읽기와 QA 문서 작성만 수행했다. 기존 빌드 로그를 새로 실행하거나 exit code를 생성하지 않았다. |

초기 컴파일 및 테스트 시도는 별개의 3개 제품 버그가 아니라 **Q-001 동일 원인으로 묶인 차단 실패**다. `compileKotlin`의 exit 1을 `:test` 실패, 특정 JUnit 테스트 실패 또는 Minecraft 다운로드 실패로 해석하면 안 된다.

## 2. 우선순위별 이슈 — 담당자와 최소 수정 방향

| ID | 심각도 | 판정 | 담당 | 수락 기준 |
| --- | --- | --- | --- | --- |
| **Q-001** | **P0 / 컴파일 차단** | **CONFIRMED** | **2 — plugin/loom** | Gradle 컴파일 성공, 이후 테스트 실행 가능 |
| **Q-002** | **P1 / 레거시 기능 거부** | **CONFIRMED** | **1 — config/build** | 레거시 `.accesswidener`와 26.3 `.classtweaker`를 버전별 판정 |
| **Q-003** | **P1 / 의존성 무결성** | **CONFIRMED** | **1 — config/build** | 판정 불가능한 버전 조건을 무조건 참으로 처리하지 않음 |
| **Q-004** | **P1 / 경로 안전성** | **CONFIRMED** | **3 — generator/util** | 리소스 디렉터리 밖 심볼릭 링크 거부 |
| **Q-005** | **P1 / client 리소스 정합성** | **CONFIRMED**(코드 간 불일치) | **2 — plugin/loom**, **1 협의** | Class Tweaker 허용 디렉터리를 단일 계약으로 정리 |
| **Q-006** | P2 / 테스트 포괄성 | **CONFIRMED**(테스트 구성) | **4 — integration 테스트 소유** / Prime 조율 | 미실행·skip·실행 테스트 집계를 정확히 보고 |
| **Q-007** | P2 / 설정 캐시 및 미러 안정성 | **STATIC SUSPECT** | **2 — plugin/loom** | G5/G20/G24 실측으로 확증 또는 기각 |

### Q-001 — `ExclusiveContentRepository` 호출 오버로드 오류

- **재현 근거:** `src/main/kotlin/dev/fabricamper/loom/RepositoryConfigurer.kt:39-49`, 특히 **46행**. `project.repositories.maven { ... }` 또는 기존 저장소 조회로 얻은 `repository: MavenArtifactRepository`를 `content.forRepository(repository)`로 전달했다. 보고된 컴파일의 `:compileKotlin` **exit 1**은 이 위치를 가리킨다.
- **원인:** `forRepository(...)`는 전달하려는 **생성·설정 액션** 형태와 맞지 않으며, 이미 생성된 저장소 인스턴스를 등록할 때는 Gradle `ExclusiveContentRepository.forRepositories(ArtifactRepository...)` 오버로드가 적합하다. Java API 출처: <https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/repositories/ExclusiveContentRepository.html>.
- **담당/수정안:** **owner-2**. `content.forRepositories(repository)`로 교체하고 Modrinth `maven.modrinth` 그룹 필터를 유지한다. 이미 존재하는 `Modrinth` 저장소를 재사용할 때도 `forRepositories`로 같은 저장소를 지정한다. 오버로드의 정확한 Gradle/Kotlin DSL 바인딩을 실제 컴파일로 확인한다.
- **검증:** 동일한 컴파일을 단독 재실행하여 exit 0 확인 → `:test` 실행 → TestKit 구성·실제 소비자 빌드 순서. **컴파일만 고쳐졌다고 G1/G10/G14가 PASS인 것은 아니다.**

### Q-002 — 레거시 Access Widener를 일괄적으로 Class Tweaker로 제한

- **재현 근거:** `src/main/kotlin/dev/fabricamper/config/ModYamlValidator.kt:74-92`. **76-78행**에서 Minecraft 프로파일을 보기 전에 `.classtweaker`가 아닌 모든 `mod.accessWidener`를 `FA009`로 거부한다. 이후 **87-90행**의 `NON_REMAPPING` 헤더 검사는 이미 거부된 레거시 경로를 복구하지 못한다.
- **원인/영향:** `specs.md`의 버전별 remap 지원(G9, 레거시 `1.21.11` 이하)에서 `.accesswidener`는 유효한 기존 Fabric 리소스 형식이다. 현재 공통 validator가 레거시 모드 프로젝트를 부당하게 거부한다.
- **담당/수정안:** **owner-1**. `raw.minecraft.profile`에 따라 `NON_REMAPPING`에는 `.classtweaker`와 `classTweaker v1 official`을, `REMAPPING`에는 `.accesswidener`와 지원 매핑 네임스페이스/헤더를 별도 확인한다. Loom extension의 실제 지원 버전별 API와 맞춘다. **owner-2**는 `LoomConfigurer`가 해당 경로를 전달하는 계약을 확인하되 설정 파서 검증 수정은 owner-1만 맡는다.
- **검증:** legacy resource 두 형식에 대한 정상/오류 단위 테스트; G9/G11/G16. 실제 레거시 Loom 바이너리 실행은 별도 조건이다.

### Q-003 — 판정할 수 없는 Loader 버전 조건을 참으로 판정

- **재현 근거:** `src/main/kotlin/dev/fabricamper/config/ModYamlValidator.kt:275-305`. 특히 **293-295행**의 `cmp == null` 처리: 동등 비교 외의 `>=`, `<`, `~`, `^` 등에서 `true`를 반환한다. 예를 들어 SemVer로 비교할 수 없는 선택 버전에 부등식 조건을 적용해도 상충 관계를 조용히 통과시킬 수 있다.
- **원인/영향:** `specs.md`의 **G26(Loader AND/OR 정확한 조건 검증)**과 사용자 명시 요구인 조건 보존·모순 거부를 충족하지 못한다. `generator/ValidateFabricAmper.kt:119-132`는 `LoaderVersionConstraints.matchesAny`가 `null`이면 `FA118`로 거부하므로 **config와 generator의 판정도 다르다.** 동일 YAML이 앞 검증을 통과한 뒤 뒤 단계에서 실패할 수 있다.
- **담당/수정안:** **owner-1**이 `VersionRequirements`의 독자적인 근사 비교를 제거하고, **owner-3** 소유 `util/LoaderVersionConstraints`의 단일 판정 API를 호출하도록 협의한다. `null`(비교 불가) 시 `FA009` 또는 명세에 정의된 별도 진단을 내고 자동 승인하지 않는다. OR/AND 및 Loader의 비SemVer 정책은 기존 spec에 맞춰 일치시킨다. 타 소유 파일을 직접 고치지 않는다.
- **검증:** `>=` + 비SemVer, `~`/`^` 경계, OR 배열, AND 연속 조건, 만족/불만족/비교 불가의 세 결과; G26/G11.

### Q-004 — 리소스 심볼릭 링크가 `src/<sourceSet>/resources` 밖을 가리켜도 통과

- **재현 근거:** `src/main/kotlin/dev/fabricamper/generator/ValidateFabricAmper.kt:275-289`. **281-287행**에서 `Files.isRegularFile`(기본적으로 링크 추적)로 후보를 고른 다음 `matched.toRealPath().startsWith(base.toRealPath())`로 **프로젝트 루트** 내부인지만 검사한다. 예: `src/main/resources/linked.mixins.json` → `<project>/secret.mixins.json` 심볼릭 링크는 이 검사를 통과한다.
- **원인/영향:** 실제 JAR resources 루트의 격리 경계를 벗어난 로컬 파일이 리소스로 승인될 수 있다. `config/ModYamlValidator.kt:238-260`도 실제 리소스 루트 내 실경로를 검증하지 않지만, 리소스 최종 무결성 검사의 정규 소유자는 generator이다.
- **담당/수정안:** **owner-3**. 매치된 후보의 **선택된** `src/main/resources` 또는 `src/client/resources` 루트에서 `root.toRealPath()`를 구하고, `matched.toRealPath().startsWith(rootReal)`로 검증한다. 파일/루트 읽기 실패, 존재하지 않는 대상, symlink 탈출은 `FA130` 계열로 명시적으로 실패. 모호한 동일 상대경로가 양쪽 리소스에 있는 경우 우선순위도 정책화한다. 변경은 generator 경로에 한정하고 owner-1에는 검증 차이를 알린다.
- **검증:** 이미 작성된 `src/test/kotlin/dev/fabricamper/unit/ResourceValidationUnitTest.kt:125-149`의 링크 테스트는 이 버그를 **실행되면 재현하도록 작성된 테스트**이나 현재는 NOT RUN. Windows에서 링크 생성 권한 부족 시 `assumeTrue`로 SKIPPED이므로 정상 환경에서 통과 또는 다른 플랫폼 재현 증거 필요. G11/G24.

### Q-005 — Class Tweaker `client` 위치 허용과 Loom 원본 경로 고정이 충돌

- **재현 근거:** `src/main/kotlin/dev/fabricamper/generator/ValidateFabricAmper.kt:281-285`는 `src/main/resources`와 `src/client/resources`를 모두 검색한다. `config/ModYamlValidator.kt:238-243`도 양쪽을 허용한다. 그러나 `src/main/kotlin/dev/fabricamper/loom/LoomConfigurer.kt:44-49`는 `loom.accessWidenerPath`를 **항상 `src/main/resources/$accessWidener`에 고정**한다.
- **영향:** `src/client/resources/example.classtweaker`에만 파일이 있는 구성은 YAML/resource validation 이후 Loom 단계에서 `FA009`로 실패할 수 있다. `specs.md`는 client 리소스 자체를 허용하면서 classTweaker 실제 원본 위치는 main을 예시로 두고 있어 **정책 판단이 필요한 코드 간 불일치**다.
- **담당/수정안:** **owner-2**가 Loom의 client-only Class Tweaker 사용 가능 여부를 확인하여, 지원 가능하면 main/client 탐색과 `loom.accessWidenerPath`를 동일한 실제 파일로 연결한다. Loom이 main-only를 요구한다면 **owner-1/owner-3과 협의**해 validator에서 client 전용 `accessWidener`를 일찍 거부하고 spec·진단을 단일화한다. `mixins`와 `icon`의 client 허용까지 일괄 제한하지 않는다.
- **검증:** split true/false, main-only/client-only, 같은 이름 양쪽 존재, `validateAccessWidener`, G16/G18/G24. Loom의 client-only 지원 자체는 **미확인**이며 이 부분을 이미 지원한다고 판단하지 않는다.

### Q-006 — TestKit 선택 실행·검증 범위 혼동

- **재현 근거:** `src/test/kotlin/dev/fabricamper/integration/FabricAmperDiagnosticsTest.kt`에는 `@Test` **4개**가 있고, `FabricAmperConsumerTest.kt:32-34`는 `@EnabledIfEnvironmentVariable(named="FABRIC_AMPER_TESTKIT_FULL", matches="true")`가 클래스 전체를 제한한다. 현행 `FabricAmperConsumerTest.kt`의 `@Test`는 **8개**(39/55/83/104/124/159/186/204행)다. 작업자 4가 전달한 “경량 4개 + 무거운 7개”는 **현재 소스 기준 4 + 8**과 차이가 있으므로 최근 추가된 G24 등을 반영해 집계해야 한다.
- **의미:** 환경변수가 없으면 heavy 8개가 **SKIPPED**이지 PASS가 아니다. 모든 테스트는 `:compileKotlin` 실패로 **아직 실행되지 않았다**. 경량 4개도 TestKit GradleRunner를 사용하므로 플러그인 컴파일 선행이 필요하다. Heavy 8개는 Loom/MC 다운로드 및 격리 서버·클라이언트 조건이 더 필요할 수 있다.
- **담당/수정안:** 테스트 파일 owner는 **worker-4**, 본 Phase 3은 상태만 기록한다. Prime이 실제 환경변수/ Gradle 도구 버전별 실행을 통제한다. `G17a` 테스트는 init script로 `loom.fabricModJsonPath` 경로와 JSON 내용을 조회하는 **배선 확인**이며 **실제 interface injection 완료(G17)**를 증명하지 않는다. `G5`의 로그 문자열 매칭은 Gradle 버전별 문구 차이로 오탐 가능성 있으므로 실제 task/cache 결과를 함께 본다. G6/G15와 최신/레거시 G9는 TestKit 소스 존재만으로 검증되지 않는다.

### Q-007 — 구성 단계 미러 복구·설정 캐시 재사용

- **정적 근거:** `src/main/kotlin/dev/fabricamper/plugin/FabricAmperProjectPlugin.kt:31-49`는 선택 YAML을 `ProviderFactory.fileContents(...).asBytes.get()`으로 구성 단계에 읽고 미러를 만든다. `src/main/kotlin/dev/fabricamper/loom/LoomMetadataMirror.kt:41-63`는 `.gradle/fabric-amper/loom/<SHA>/` 아래 미러를 만들고 `fileContents(...).asBytes.get()`으로 읽기 추적한다. 이 패턴은 `specs.md` §5/G20의 선행 파일 조회 요구를 의식한 구현이다.
- **확인해야 할 위험:** configuration cache HIT에서 `ProjectPlugin.apply`는 재호출되지 않는다. 미러 삭제·변조가 CC 입력의 무효화로 정확히 감지되는지, 최초 미러 생성이 자체 캐시 입력을 오염시키지 않는지, 파일 자격·권한/병렬 실행/심볼릭 링크/해시 디렉터리 축적 문제가 남아 있다. **현재만으로 재현된 버그는 아니므로 `STATIC SUSPECT`**로 둔다.
- **담당/수정안:** **owner-2**가 Q-001 이후 G20/G24/G5를 재현해 원인 확인 시 최소 수정한다. 미러를 `processResources`의 또 다른 소스로 붙이면 최종 JAR 중복 우려가 생기므로 현재 `from(generate)` 단일 배선은 유지한다. 필요 시 구성 캐시 수명주기와 스냅샷 삭제 정책을 spec에 맞춘다.

## 3. 중복 제보 통합 및 기각한 오탐

1. **Q-001은 컴파일 시도 세 건에 공통인 하나의 장애**다. Gradle `:test`가 실행되지 않았는데 “테스트 2세트가 실패”라고 분리하여 신규 제품 오류로 세지 않는다.
2. **Q-002와 Q-005는 서로 다른 원인**이다. Q-002는 Minecraft 프로파일별 파일 형식/헤더 검증, Q-005는 선택 소스셋 파일을 Loom에 연결하는 실제 경로 문제다. 둘 다 `accessWidener` 키를 다루지만 담당과 수정 대상이 다르다.
3. **Q-004의 중복된 검증기 진단은 하나의 보안/패키징 이슈**다. config와 generator 각각 경로를 검사하나 generator의 최종 실경로 검사가 프로젝트 루트로 제한됨을 핵심으로 잡았다. `ResourceValidationUnitTest`에 준비된 테스트는 실행 전이므로 “테스트로 확인됨”이라 표현하지 않는다.
4. **기각 — `processResources.from(generateTask)` 자체가 잘못됐다는 제보.** `FabricAmperProjectPlugin.kt:94-107`의 `@OutputFile` 생산 태스크를 `ProcessResources.from(TaskProvider)`에 연결하는 방식은 Gradle의 출력/태스크 의존성 추론과 일치한다. `sourceSets.main.output.dir(generate)`와 함께 붙였다면 중복 위험이 있지만 **현재 코드는 그렇게 하지 않는다.** 다만 Loom 최종 JAR의 중복 여부는 G3/G21로 별도 검증해야 한다. 참고: <https://docs.gradle.org/current/userguide/incremental_builds.html>.
5. **기각 — YAML에서 `mod.yml`을 지원하지 않는다는 제보.** `FabricAmperSettingsPlugin.kt:23-32`, `FabricAmperProjectPlugin.kt:110-118`, `ModYamlLoader.kt:37-59`는 두 별칭을 선택하고 동시 존재를 거부한다. 다만 구성 캐시에서 파일 별칭 전환까지 안전한지는 **미실행**, G30으로 남긴다.
6. **기각 — SnakeYAML 이벤트 파싱만 하고 중복 키를 검사하지 않는다는 제보.** `StrictYamlReader.kt:74-110`에서 별칭·앵커·태그/다중 문서를 검사하고 **57행**에서 별도 `Load(settings).loadFromString`을 실행하며 **49행**의 duplicate 키 거부 설정을 사용한다. 올바른 접근이며 세부 경계 조건은 G11/G30에서 검증할 항목이다.
7. **기각 — 기존 소스에 `fabric.mod.json`을 직접 쓰거나 덮어쓴다는 제보.** `FabricAmperProjectPlugin.kt:120-125`는 양쪽 리소스의 기존 metadata를 `FA008`로 거부한다. 생성 태스크의 출력은 `build/generated/...`이고 Loom 구성용 미러는 `.gradle/...`이다. 실제 최종 JAR의 1개 포함은 별도 수락 조건이다.

## 4. 현재 테스트 소스와 수락 항목 대응

| 범위 | 정적 확인된 테스트 | 누락·주의 및 대응 G ID |
| --- | --- | --- |
| 빠른 TestKit 진단 | `FabricAmperDiagnosticsTest.kt` **4개 `@Test`** | G8, G12, YAML 구문/중복키·별칭 충돌 일부(G11/G30). `mod.yml` **단독 성공**은 실제 Gradle 빌드로 검사하지 않는다. |
| 무거운 opt-in TestKit | `FabricAmperConsumerTest.kt` **8개 `@Test`**, env `FABRIC_AMPER_TESTKIT_FULL=true` | G1/G2/G14, G3/G21, G4, G5, G7, G17a, G20, G24 일부. 실행 시 Gradle/Loom 다운로드·패키징이 동반될 수 있으므로 기본 실행 누락은 SKIPPED으로 기록. |
| 단위 테스트 | `unit/` 아래 `@Test` 관련 파일 **8개**(이 중 `YamlFixtures.kt`는 공용 fixture 유틸) | 파서·DTO·버전 조건·메타데이터·경로 검증을 구성. **실제 테스트 클래스는 7개 + fixture 1개**; source-only, 실행 0. |
| 외부/환경 수락 | 현재 테스트 코드만으로 판정 불가 | IntelliJ 실제 Sync G6/G28, 최신·레거시 Gradle/Loom 조합 G9/G13/G25, 실제 Loader `runClient`/`runServer` G15, interface injection 런타임 G17, plugin classpath 충돌 G19, Loader side-specific 의존성 G27, KGP 캐시 비용 G29. |
| 추가 회귀 | 우선 작성 또는 실행 설계 | Q-002 G9/G16, Q-003 G26, Q-004 G11/G24, Q-005 G16/G18/G24, YAML 별칭·빈/다중 문서·잘못된 UTF-8 G30, generated JSON/JAR 중복·SHA G3/G21. |

**실행 상태 표기 규칙:** `NOT RUN` = 해당 작업에 도달하지 않음, `SKIPPED` = 테스트 엔진이 실제 실행될 때 조건에 의해 제외함, `FAIL` = 개별 테스트가 실제 실행되어 실패함, `PASS` = 실제 실행 후 성공함. 현재 이 보고서의 모든 개별 테스트는 **NOT RUN**이다. Heavy 테스트는 차단 컴파일을 해결한 뒤 환경변수가 없을 경우에만 `SKIPPED`로 판정할 수 있다.

## 5. Phase 4 권장 수정·검증 순서

1. **owner-2 Q-001을 먼저 수정**하고 독립적인 `:compileKotlin`/컴파일 결과를 확보한다. 이 게이트 전 다른 테스트의 성공/실패를 논하지 않는다.
2. **owner-1 Q-002와 Q-003**, **owner-3 Q-004**, **owner-2 Q-005**를 담당 파일 경계 안에서 수정한다. cross-owner API 변경 필요 시 Prime을 통해 계약을 조율한다.
3. 먼저 `:test`에 포함된 단위 및 빠른 TestKit 테스트를 실행하고, 해당 환경에서 요구되는 Gradle/JDK25·Loom 아티팩트가 준비되면 `FABRIC_AMPER_TESTKIT_FULL=true`로 heavy TestKit을 실행한다. **환경변수 없이 heavy 테스트가 제외되는 것은 정상이며 성공 증거가 아니다.**
4. G3/G5/G10/G14/G17a/G20/G21/G24/G26/G30과 실제 Loader·IDE 수락 G6/G15를 구분해 로그·산출물·JAR ZIP 엔트리·설정 캐시 재사용/무효화 증거를 남긴다. G13/G25는 대상 Gradle/Loom 조합을 각각 독립 실행하고, 실행하지 못한 조합은 미검증으로 표기한다.

검토 중 참조한 프로젝트 기준은 `specs.md` §5~§10이며, 전체 요구를 완료했다고 선언하는 조건은 현재 성립하지 않는다. 이 문서는 **Phase 3 QA 트리아지 결과**로서 소스 수정이나 테스트 실행 결과를 대체하지 않는다.

---

## 6. Phase 4 수정·재검증 후속 기록 (2026-11-11)

> **상태 갱신 공지:** 위 §1~§5는 **Phase 3 당시의 초기 증거와 미실행 판정**을 보존한 기록이다. 아래 §6.1~§6.7이 Phase 4 이후의 **최신 상태**이며, 앞부분의 `BLOCKED`, `NOT RUN`을 지금도 모든 검사에 적용한다는 뜻이 아니다. 모든 명령은 Prime이 `/vbc/Fabric Amper`에서 실행·보고한 결과다. 이 QA 작업자는 해당 명령을 새로 실행하지 않았으며 `QA_REPORT.md`만 편집했다. 실행 환경: **Gradle 9.7.1 / JDK 25 / Windows**. 명령 접두어는 전달받은 `& '..\Minecord\gradlew\.bat' -p .`를 그대로 기록한다.

### 6.1 결론 및 수정 완료 상태

**현재 판정: 플러그인 자체의 clean build 및 실행된 테스트 범위는 PASS. 전체 제품 수락은 아직 NOT VERIFIED.** 확인된 P0 컴파일 차단은 제거됐고, P1 Q-002~Q-005에는 대응 수정이 반영됐다. 그러나 **선택 실행 TestKit 소비자 테스트 8개가 SKIPPED**되었으므로 settings-only 소비자 빌드, 생성 `fabric.mod.json`의 실제 소비자 JAR 포함, Loom 구성 미러의 configuration cache 복구, 게임 실행·IDE Sync를 완료했다고 판정할 수 없다. 현재 실행된 범위의 재현 실패 **0건**을 전체 버그 **0건**이라고 부르지 않는다.

| 기존 이슈 | 최초 심각도·담당 유지 | Phase 4 상태 | 수정/검증 증거 | 아직 완료되지 않은 수락 |
| --- | --- | --- | --- | --- |
| **Q-001** | **P0 / owner-2 (plugin/loom)** | **PATCHED · COMPILE PASS** | `src/main/kotlin/dev/fabricamper/loom/RepositoryConfigurer.kt:46` → `content.forRepositories(repository)`; `compileKotlin` **exit 0**, `BUILD SUCCESSFUL`. clean build도 exit 0. | 소비자 Modrinth 저장소 해석·jar-in-jar **G10**, single-settings 플러그인 적용 **G1/G14** |
| **Q-002** | **P1 / owner-1 (config/build)** | **PATCHED · UNIT SCOPE PASS** | `config/ModYamlValidator.kt:75-100`가 Minecraft 프로파일에 따라 modern `.classtweaker`/레거시 `.accesswidener` 확장자·헤더를 분기. 현재 단위 테스트 전체 PASS. | 레거시 Loom/실제 Access Widener 빌드·런타임 **G9/G16** |
| **Q-003** | **P1 / owner-1 (config/build), util 계약 owner-3 협의** | **PATCHED · UNIT SCOPE PASS** | `config/ModYamlValidator.kt:197-213`: `LoaderVersionConstraints.matchesAny` 사용, `false` 또는 판정 불가 `null`이면 `FA009`로 실패. 과거 비교 불가 관계식을 `true`로 통과시키는 경로 제거. | 실제 Loader AND/OR 전체 경계·런타임 **G26** |
| **Q-004** | **P1 / owner-3 (generator/util)** | **PATCHED · REGRESSION PASS** | `generator/ValidateFabricAmper.kt:286-335`가 **각 `src/<sourceSet>/resources`의 정규 실경로**를 기준으로 심볼릭 링크 이탈을 거부. 현재 XML `ResourceValidationUnitTest` **8 tests / 0 skipped / 0 failures / 0 errors**이며 symlink 케이스가 실제 `testcase`로 남음. | 실제 소비자 리소스 패키징·외부 환경 **G24** |
| **Q-005** | **P1 / owner-2 (plugin/loom), owner-1/3 validator 협의** | **CONTRACT ALIGNED · UNIT SCOPE PASS** | 원래 `loom/LoomConfigurer.kt:44-49`의 main-only 실제 경로는 **`specs.md` §5의 main source Class Tweaker 규칙**에 맞춰 유지. owner-1 `config/ModYamlValidator.kt:75-78` 및 owner-3 `generator/ValidateFabricAmper.kt:286-341`에 `mainOnly` 검증을 반영해 client-only Class Tweaker를 앞 단계에서 오류로 처리. client Mixin/icon까지 불필요하게 금지하지 않음. | Loom의 `validateAccessWidener`, 실제 split/client 사용 **G16/G18/G24** |
| **Q-006** | **P2 / owner-4 (integration tests)** | **QUICK TESTKIT PASS / HEAVY SKIPPED** | `FabricAmperDiagnosticsTest` **4/4 PASS**. `FabricAmperConsumerTest` **8/8 SKIPPED**(`FABRIC_AMPER_TESTKIT_FULL` 미설정). 초기 4+7 제보보다 현재 테스트 소스·XML의 **4+8**이 정확함. | heavy 테스트 G1/G2/G3/G4/G5/G7/G14/G17a/G20/G21/G24 |
| **Q-007** | **P2 / owner-2 (plugin/loom)** | **STATIC SUSPECT · NOT RUN** | 미러의 구성 캐시 재사용·삭제/손상 복구는 선택 실행 Consumer TestKit에만 있고 실행되지 않음. 초기 의심을 검증 완료로 승격하지 않음. | **G5/G20/G24** |

**판정 용어:** `PATCHED`는 실제 소스 변경 확인, `UNIT SCOPE PASS`는 현행 JUnit 단위 검증 통과, `COMPILE PASS`는 플러그인 컴파일 성공, `SKIPPED`는 JUnit이 조건 때문에 테스트 메서드를 실행하지 않은 상태다. Q-002~Q-005의 **실제 Loom 소비자·Minecraft 환경 검증**은 아래 미확인 항목으로 계속 남긴다.

### 6.2 정확한 실행 명령과 결과 기록

다음 명령들은 위의 공통 작업 디렉터리에서 보고된 명령이다. `-p .` 앞의 wrapper 접두어는 위에 적었다. 반환 코드는 **Prime 전달값**이고, 현재 XML 및 수정된 소스 읽기로 일부를 교차 확인했다. 소요 시간은 Prime의 완료 로그 값이다.

| 순서 | Gradle 인자 / 작업 | 종료 코드·결과 | 판정 |
| --- | --- | --- | --- |
| 1 | `compileKotlin --console=plain --no-daemon` | **EXIT 0**, `BUILD SUCCESSFUL`, **22초** | owner-2 Q-001 패치 후 Kotlin 컴파일 차단 해제 |
| 2 | owner-4 TestKit 테스트 컴파일(당시 수행된 명령 인자는 별도 미전달) | **컴파일 실패**: `TestKitFixture.kt:80`에서 nullable `ByteArray?.orEmpty` 사용 | 테스트 유틸 코드 회귀. owner-4가 `?: ByteArray(0)`로 교체; 최종 `TestKitFixture.kt:83-87`에서 수정 확인 |
| 3 | `test --tests '*unit*' --console=plain --no-daemon` (첫 실행) | **EXIT 1**, **66개 실행 / 65 PASS / 1 FAIL** | `StrictYamlReaderUnitTest.rejects merge keys and multiple YAML documents()` 실패, `<<:` merge mapping key 거부 누락 발견 |
| 4 | 같은 `test --tests '*unit*' --console=plain --no-daemon` (owner-1 파서 수정 후) | **EXIT 0**, `BUILD SUCCESSFUL`, **17초** | 단위 테스트 전체 PASS, 현재 clean XML의 unit tests **failures=0, errors=0, skipped=0** |
| 5 | `test --tests '*integration*' --console=plain --no-daemon` (첫 실행) | **EXIT 1**, quick TestKit **4 FAIL** | JUnit `@TempDir` 정리 단계에서 Windows 네이티브 DLL 및 Gradle `.lock` 파일 삭제 실패. 제품 기능 실패로 단정하지 않음 |
| 6 | 같은 `test --tests '*integration*' --console=plain --no-daemon` (owner-4 fixture 수정 후) | **EXIT 0**, `BUILD SUCCESSFUL`, **31초** | quick **4 PASS**, opt-in heavy **8 SKIPPED**, 실제 heavy consumer 결과 없음 |
| 7 | `clean build --console=plain --no-daemon` (첫 실행) | **EXIT 1** / `:validatePlugins FAILED` | `ValidateFabricAmper` 검증 전용 태스크에 Gradle 캐시 정책 annotation이 없어서 validation 차단 |
| 8 | 같은 `clean build --console=plain --no-daemon` (owner-3 수정 후) | **EXIT 0**, `BUILD SUCCESSFUL`, **48초**, **11 tasks executed / 11** | 플러그인 자체 clean build/테스트/Gradle plugin validation 통과 |

중간에 발생한 **테스트 소스 컴파일 실패**, **파서 merge key 단위 실패**, **Windows TestKit 정리 실패**, **Gradle `:validatePlugins` 실패**는 서로 다른 독립 문제이며, 수정 후 성공한 실행과 실패 당시 실행을 각각 유지한다. 초기 Phase 3 `:compileKotlin` 오류와 중복 집계하지 않는다.

### 6.3 Phase 4 신규 발견·수정된 회귀(원래 Q-001~Q-007과 구분)

| 사건 | 발생·원인 | 실제 수정 담당/파일 | 최종 상태 및 한계 |
| --- | --- | --- | --- |
| **R-01 — 테스트 유틸 nullable 타입** | `ByteArray?.orEmpty`는 이 위치에 적용할 Kotlin 표준 확장으로 해석되지 않아 테스트 컴파일 실패 | **owner-4**, `src/test/kotlin/dev/fabricamper/integration/TestKitFixture.kt:83-87`에서 Elvis `?: ByteArray(0)` 사용 | **FIXED · TEST COMPILE PASS**. 생산 코드 오류가 아님 |
| **R-02 — YAML merge key 누락** | `StrictYamlReader.kt` 이벤트 비교에서 `ScalarStyle.toString() == "PLAIN"`에 의존해 `<<:`를 놓침. 최초 단위 실행 66개 중 1개 실패 | **owner-1**, `src/main/kotlin/dev/fabricamper/config/StrictYamlReader.kt:109-115`의 `checkedMap`에서 정확한 **키 `<<`**를 거부 | **FIXED · UNIT PASS (66/66)**. 값 문자열에 포함된 `<<`까지 무조건 금지하는 회귀를 피하도록 키 문맥에서 검사 |
| **R-03 — Windows JUnit TempDir 정리/잠금** | TestKit의 Gradle 사용자 캐시·TestKit home이 `@TempDir` 밑에 있어서 daemon/native DLL/fileevents lock을 JUnit 종료 시 삭제하지 못함. 경량 테스트 4개가 종료 단계에서 실패 | **owner-4**, `src/test/kotlin/dev/fabricamper/integration/TestKitFixture.kt:15-23,37-45`에서 캐시를 프로젝트 `build/fabric-amper-testkit-cache/pid-<pid>`로 이동하고 `--no-watch-fs` 추가 | **FIXED · QUICK 4/4 PASS**. 테스트 격리·환경 정리 실패를 Loader 제품 결함으로 오판하지 않음. 캐시 폴더 정리 정책은 필요 시 별도로 검토 |
| **R-04 — 출력 없는 Gradle 태스크 캐시 정책 annotation 누락** | 최초 `clean build`의 `:validatePlugins`가 `ValidateFabricAmper`를 `@CacheableTask` 또는 `@DisableCachingByDefault` 없이 선언했다고 실패 처리 | **owner-3**, `src/main/kotlin/dev/fabricamper/generator/ValidateFabricAmper.kt:14,23-25`에 `@DisableCachingByDefault(because = "Validates current project resources and has no output")` 추가 | **FIXED · CLEAN BUILD PASS**. 출력 없는 검증용 태스크를 무의미한 cacheable 태스크로 선언하지 않음 |

### 6.4 현재 JUnit XML 근거와 SKIPPED 주의

| 검증 자료 | Tests | Pass(계산) | Failures | Errors | Skipped | 판정 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 전체 unit 범위 — `test --tests '*unit*'` 최종 실행 및 clean build | **66** | **66** | **0** | **0** | **0** | **PASS** |
| `build/test-results/test/TEST-dev.fabricamper.unit.ResourceValidationUnitTest.xml` | **8** | **8** | **0** | **0** | **0** | **PASS**, symlink 검사도 건너뛰지 않음 |
| `build/test-results/test/TEST-dev.fabricamper.integration.FabricAmperDiagnosticsTest.xml` | **4** | **4** | **0** | **0** | **0** | **PASS** — settings 오류 진단용, 의도적인 실패 명령을 내부에서 검사 |
| `build/test-results/test/TEST-dev.fabricamper.integration.FabricAmperConsumerTest.xml` | **8** | **0** | **0** | **0** | **8** | **SKIPPED** — `FABRIC_AMPER_TESTKIT_FULL=true`가 설정되지 않음 |

XML의 `<system-out>`에 나타나는 `BUILD FAILED` 및 `FA001/FA008` 등은 **`buildAndFail()`로 의도한 오류를 검증하는 내부 fixture 로그**일 수 있다. 해당 JUnit `testsuite`의 `failures/errors` 값과 메서드 검증 결과를 근거로 분류해야 한다. **경량 진단 4 PASS는 소비자 `build` 또는 Minecraft client/server PASS를 의미하지 않는다.**

### 6.5 최종 빌드 산출물과 무결성

- **플러그인 JAR:** `/vbc/Fabric Amper/build/libs/fabric-amper-0.1.0.jar` (약 **166.1 KB**). `clean build` **EXIT 0**에서 생성. Prime이 실행한 `jar tf` 검사에서 **`dev.fabric-amper.settings` 및 `dev.fabric-amper.project`의 두 Gradle plugin marker**를 확인했다.
- **SHA-256 (Prime 검증값):** `176B7B1A57A668A5FE2C36D99EAFE12C417B25364E3B086FB88B0E404652BEC8`.
- **소스 JAR:** `build/libs/fabric-amper-0.1.0-sources.jar` 존재(파일 목록에서도 확인).
- **해석 주의:** 이 파일은 **Fabric Amper Gradle 플러그인 JAR**이다. `settings.gradle.kts`만 있는 실제 사용자 프로젝트가 빌드한 **Minecraft Fabric 모드 배포 JAR은 아직 검증되지 않았다**. 두 marker의 존재도 consumer-side 플러그인 의존성 해결 G1/G14까지 입증하지는 않는다.

### 6.6 heavy TestKit 재실행이 이루어지지 않은 정확한 이유

Prime이 PowerShell에서 `$env:FABRIC_AMPER_TESTKIT_FULL='true'; & ..\Minecord\gradlew\.bat ...` 형태로 환경변수를 설정해 선택 테스트를 실행하려 한 호출은 사용자 명령 정책에 의해 **`COMMAND_NOT_ALLOWED` — unsupported shell `$` syntax**로 거부됐다. **프로그램이 시작되지 않았고 실행 코드·결과·exit code가 없다.** 이를 `EXIT 1` 또는 heavy 테스트 실패로 기록하지 않는다. 차단된 명령을 다른 표기·도구·스크립트로 우회해서도 안 된다. 따라서 heavy 8개는 **SKIPPED**로 유지한다. 정책이 허용되는 정식 실행 경로를 사용하기 전까지 G1/G3/G5/G7/G17a/G20/G24 통과를 주장하지 않는다.

### 6.7 남은 수락 게이트 — 구분된 범위

| 항목 | 현재 상태 | 필요한 검증 |
| --- | --- | --- |
| 자체 Gradle 플러그인 컴파일·단위·경량 진단·`clean build` | **PASS**(위 명령·XML 범위) | 새로운 변경이 발생할 때만 관련 회귀 검증 |
| settings-only 실제 소비자 적용 및 리포지토리/Gradle classpath | **NOT RUN (heavy opt-in)** | **G1/G2/G10/G14/G19/G29** |
| 생성 `fabric.mod.json`, 구성 미러, 실제 소비자 JAR 및 SHA 재현성 | **NOT RUN (heavy opt-in)** | **G3/G4/G17a/G21/G22/G24/G30** |
| configuration cache 재사용·YAML 변경·미러 삭제/손상 복구(Q-007) | **STATIC SUSPECT / NOT RUN** | **G5/G20** |
| Kotlin 전환·client sourceSets·Class Tweaker 실제 사용 | **일부 unit PASS / 통합 NOT RUN** | **G7/G16/G18/G26/G27** |
| IntelliJ Sync/자동 갱신, 실제 Minecraft Fabric Loader 클라이언트·서버 | **NOT RUN** | **G6/G15/G17/G28** |
| 레거시 remap 및 여러 Gradle/Loom 버전 조합 | **NOT RUN** | **G9/G13/G25** |

**Phase 4 완료 경계:** Q-001 P0 컴파일 차단 제거, Q-002~Q-005의 해당 소스 정합화, R-01~R-04 회귀 수정, **66개 unit PASS + quick 4개 PASS + Gradle 플러그인 clean build PASS**까지 확인됐다. heavy 8개·실제 소비자/G 시나리오·IDE/게임 환경 수락은 남아 있으므로 프로젝트 전체 기능 완료/출시 승인으로 판정하지 않는다. Phase 3의 원인·담당자·우선순위는 그대로 보존했고, 이 후속 절의 상태가 현 시점의 최신 판정이다.

---

## 7. Phase 4 후속 정적 감사 — 신규 발견 이슈 및 수정 대기 (2026-11-11)

**증거 시점 분리:** §1~§6의 **단위 테스트 66 PASS, 경량 TestKit 4 PASS, 선택 실행 TestKit 8 SKIPPED, Gradle 9.7.1/JDK25 `clean build` PASS 및 기존 JAR SHA-256**는 **아래 감사 제보·이후 패치 이전의 증거**로 보존한다. 현재 작업자 1~5가 각각 담당 파일을 수정 중일 수 있다. 소스에 수정 코드가 이미 보이더라도 **새 패치에 대한 Prime의 Gradle 컴파일·테스트 완료 보고가 없으므로 아래 항목을 FIXED, PASS, CLOSED로 변경하지 않는다.** §7의 상태는 일괄적으로 **AUDIT FINDING · PATCH IN PROGRESS/NOT VERIFIED**, 신규 및 수정된 테스트는 **NOT RUN**이다. 이 절은 QA 기록 추가만 수행하며 생산 코드·테스트·빌드 파일은 수정하지 않는다.

### 7.1 소유자별 초기 심각도 및 구체 원인

| 신규 ID | 최초 심각도 | 정적 감사 발견 지점(정확한 파일 경로는 프로젝트 루트 기준) | 문제·초기 재현 근거 및 권고 수정 | 단일 담당 및 현 상태 |
| --- | --- | --- | --- | --- |
| **A1-01** | **P1** | `src/main/kotlin/dev/fabricamper/config/StrictYamlReader.kt` | YAML 컬렉션 총 10,000개 계약을 실제 원소 수가 아니라 스칼라/컬렉션 이벤트 수 `MAX_ITEMS * 2`로 근사하면 10,001개 리스트 원소를 놓칠 수 있다. **맵 항목·리스트 원소를 실제로 카운트하고 깊이 32를 별도 적용**, 10,000 허용/10,001 거부 테스트. | **owner-1(config/build)** · AUDIT / 재검증 전 |
| **A1-02** | **P2** | `src/main/kotlin/dev/fabricamper/config/StrictYamlReader.kt`, `src/main/kotlin/dev/fabricamper/config/ModYamlLoader.kt` | 일부 FA002 이벤트·명시적 금지 진단에서 실제 source mark 대신 고정 `:1:1` 위치가 출력될 위험. SnakeYAML `Event`의 start mark, `MarkedYamlEngineException.problemMark`를 사용하고 **실제 두 번째 이후 줄/열**에서 위반 발생 시 정확히 출력하는 회귀 추가. | **owner-1** · AUDIT / 재검증 전 |
| **A1-03** | **P1** | `src/main/kotlin/dev/fabricamper/config/ModYamlDecoder.kt` | 선택적인 map/list 값에 **명시적 YAML null**을 지정했는데 키 부재와 동일하게 빈 값으로 처리하면 잘못된 타입이 FA003 없이 통과한다. `containsKey`와 값 null을 분리해 **키가 있으면 null 거부**, 누락만 기본값 허용. `settings`, `depends`, `mixins` 등 각 optional 경로 대상. | **owner-1** · AUDIT / 재검증 전 |
| **A1-04** | **P1** | `src/main/kotlin/dev/fabricamper/config/ModYamlDecoder.kt` | `mod.entrypoints`는 **그룹별 배열만** 허용해야 하며 배열 원소는 문자열 또는 정확한 `{adapter,value}` 객체여야 한다. 단일 스칼라 그룹, `adapter`·`value` 누락 또는 잘못된 타입은 FA003/FA009로 명시 실패하도록 경계 검증. | **owner-1** · AUDIT / 재검증 전 |
| **A1-05** | **P2** | `src/main/kotlin/dev/fabricamper/config/ModYamlDecoder.kt` | `mod.icon: {}`는 사이즈별 PNG 경로가 하나도 없어 무의미하다. **빈 icon 크기 맵을 FA003**으로 거부. 유효한 양의 크기 맵/문자열 아이콘 허용 회귀 유지. | **owner-1** · AUDIT / 재검증 전 |
| **A1-06** | **P1** | `src/main/kotlin/dev/fabricamper/config/ModYamlValidator.kt` | generator와 별도로 설정 단계에서 **mixin/icon/Class Tweaker 헤더를 읽기 전**에도 리소스 루트 밖을 가리키는 심볼릭 링크를 실경로로 검증해야 한다. `src/main/resources`·`src/client/resources` 각각 `toRealPath` 경계 강제. 기존 Q-004와 **동일 계열의 다른 검증 지점**이므로 이미 해결된 generator 경로를 다시 별도 중복 버그로 세지 않는다. | **owner-1** · AUDIT / 재검증 전 |
| **A1-07** | **P1** | `src/main/kotlin/dev/fabricamper/config/ModYamlValidator.kt`, 관련 리소스 처리 계약 `src/main/kotlin/dev/fabricamper/plugin/FabricAmperProjectPlugin.kt` | `splitEnvironmentSourceSets=false`인데 `src/client/resources`만 존재하는 mixin/icon을 유효하게 수용하면 실제 main 리소스 출력과 불일치한다. **split=false일 때 client-only 참조 조기 FA009**, split=true에서 규칙대로 허용. 프로젝트 연결 코드의 수정 필요 시 Prime이 owner-2와 계약 조율. | **owner-1(config)** · AUDIT / 재검증 전 |
| **A1-08** | **P2** | `src/main/kotlin/dev/fabricamper/config/ModYamlValidator.kt` | 같은 좌표·target의 모순된 dependency scope를 FA012로 거부할 때 **최초 위치와 충돌 위치 두 인덱스 모두** 진단에 기록해야 한다. 중복 위치를 Pair(index, spec)로 추적하고 모든 관련 위치 포함 회귀. | **owner-1** · AUDIT / 재검증 전 |
| **A1-09** | **P1** | `src/main/kotlin/dev/fabricamper/config/ModYamlModel.kt`, `src/main/kotlin/dev/fabricamper/config/ModYamlValidator.kt` | 명시적으로 작성한 `mod.depends`와 Validator가 추론해 삽입한 기본 의존성 간 **원천 정보 소실** 위험. `ModYamlModel.authoredDependsKeys: Set<String>`를 **처음 작성한 mod.depends.keys**로 보존하고 `copy(mod=merged)`에서도 유지하여 generator가 사용자 명시 조건을 임의로 변경하지 않도록 한다. | **owner-1** (모델 API), **owner-3** (소비) · AUDIT / 재검증 전 |
| **A2-01** | **P1** | `src/main/kotlin/dev/fabricamper/loom/RepositoryConfigurer.kt` | `Modrinth`라는 이름의 기존 Maven 저장소가 **다른 URL**을 가리킬 경우 해당 이름만 믿고 독점 콘텐츠 필터를 적용하면 잘못된 저장소를 신뢰하는 충돌이 된다. 기존 저장소의 URL을 기대 URL과 정규화 비교, 다르면 **FA010**으로 거부. | **owner-2(plugin/loom)** · AUDIT / 재검증 전 |
| **A2-02** | **P2** | `src/main/kotlin/dev/fabricamper/loom/RepositoryConfigurer.kt` | 사용자 지정 Maven 이름에 `url.hashCode().toUInt()`(32-bit)를 사용하면 다른 URL이 같은 이름으로 충돌하고 기존 저장소를 재사용할 수 있다. 안정적인 **SHA-256 등 충돌 저항 식별자** 사용 및 동일 이름의 URL 검증. | **owner-2** · AUDIT / 재검증 전 |
| **A2-03** | **P1** | `src/main/kotlin/dev/fabricamper/loom/LoomConfigurer.kt`, `src/main/kotlin/dev/fabricamper/loom/JavaKotlinConfigurer.kt`, `src/main/kotlin/dev/fabricamper/plugin/FabricAmperProjectPlugin.kt` | Amper가 적용하기 전에 외부 **remap/nonremap Loom 또는 KGP**가 이미 적용된 경우 플러그인 이중 적용/클래스로더·버전 충돌이 모호해진다. 기존 plugin ID·extension을 **Amper 적용 이전**에 검사하고 명확한 **FA010**. 단 Amper가 직접 적용하는 정상 경로까지 차단하면 안 된다. | **owner-2(plugin/loom)** · AUDIT / 재검증 전 |
| **A2-04** | **P1** | `src/main/kotlin/dev/fabricamper/plugin/FabricAmperProjectPlugin.kt`, 생성 task 후보 `src/main/kotlin/dev/fabricamper/generator/VerifyFabricAmperJar.kt` | 최종 **소비자** JAR의 ZIP 루트 `fabric.mod.json` 개수가 정확히 1인지 확인하는 **FA013 검증 태스크/배선이 없음**. owner-3가 ZIP `@InputFile`·`@DisableCachingByDefault` 검사 task API를 제공하고 owner-2가 `jar`/레거시 `remapJar` 뒤에 의존성 연결. 플러그인 자체 `jar`에 metadata marker가 있는 사실과 혼동하지 말 것. | **owner-3** (검증 태스크), **owner-2** (plugin wiring) · AUDIT / 재검증 전 |
| **A3-01** | **P1** | `src/main/kotlin/dev/fabricamper/util/LoaderVersionConstraints.kt` | Fabric Loader의 **`^`/`~`, `x`/`X`/`*` wildcard, 임의 길이 버전 구성요소, prerelease·build metadata**를 단순 접두사/숫자 비교로 처리하면 실제 Loader 판단과 달라진다. AND/OR·3상태 유지하면서 공식 Loader 의미에 맞게 수정하고 경계 테스트 보강. 이미 Q-003의 불명확 버전 무조건 통과를 막은 것과 **별도의 세부 파서 일치 문제**다. | **owner-3(generator/util)** · AUDIT / 재검증 전 |
| **A3-02** | **P2** | `src/main/kotlin/dev/fabricamper/generator/ValidateFabricAmper.kt` | `model.dependencies.plus(model.testDependencies)`를 하나의 목록으로 도는 진단에서 test 항목까지 `dependencies[n]`로 표시되면 실제 원본 YAML의 **`test-dependencies[n]`**와 다르다. 두 목록 각각의 명칭/인덱스를 유지하도록 수정. | **owner-3** · AUDIT / 재검증 전 |
| **A3-03** | **P1** | `src/main/kotlin/dev/fabricamper/generator/ValidateFabricAmper.kt`, `src/main/kotlin/dev/fabricamper/util/FabricAmperException.kt` | generator의 `FA10x`/`FA12x`/`FA30x` 등 **비공개 코드**를 노출하면 `specs.md`의 공용 `FA001–FA015` 진단 계약과 불일치한다. 각 원인에 맞춰 공개 코드로 매핑하고 내부 상세 위치·메시지는 유지; 무작정 하나의 코드로 몰아넣지 않는다. | **owner-3** · AUDIT / 재검증 전 |
| **A3-04** | **P1** | `src/main/kotlin/dev/fabricamper/generator/MetadataMapper.kt`, `src/main/kotlin/dev/fabricamper/config/ModYamlModel.kt` | `dependencyOrigins(model)`가 validator가 삽입한 기본 `depends`까지 **사용자가 직접 작성한 조건**으로 오인할 수 있다. owner-1의 `authoredDependsKeys` 계약을 generator에서 사용하여 원 작성 키만 `authored`로 분류, `copy(mod=merged)` 후에도 원천 보존 테스트. | **owner-3** (사용), **owner-1** (필드 제공) · AUDIT / 재검증 전 |
| **A4-01** | **P2** | `src/test/kotlin/dev/fabricamper/integration/TestKitFixture.kt` | 최초 Base64 `onePixelPng`는 **PNG IDAT CRC 오류**로 실제 디코드 불가 가능성이 있으며 G24의 아이콘 검증에 거짓 실패를 유발한다. **ImageIO로 유효한 1×1 PNG 생성 또는 CRC 확인**, `ImageIO.read` 회귀 테스트와 G24 fixture 검사. 단순 PNG signature 검사만으로 충분하지 않다. | **owner-4(integration tests)** · AUDIT / 재검증 전 |

위 경로는 **감사에서 문제를 발견한 위치 및 현재 담당 파일**이다. 패치 작업이 진행 중이므로 이후 줄 번호·구현은 변할 수 있다. 동일한 원인군을 묶는 것은 허용하되, 실질적으로 서로 다른 오류 재현 경계(예: config와 generator의 검증 시점, generator 검증 태스크와 plugin 배선)를 누락하지 않는다.

### 7.2 우선순위별 조치 및 수락 게이트

**P1 우선:** A1-01(컬렉션 상한), A1-03/04(입력 형태), A1-06/07(리소스 탈출 및 누락), A1-09/A3-04(원천 provenance), A2-01/03(저장소·플러그인 충돌), A2-04(최종 소비자 ZIP 검증), A3-01/03(버전 구문·공개 진단). **P2 후속:** A1-02/05/08, A2-02, A3-02, A4-01. 다만 A2-02의 해시 충돌도 재현되면 부정확한 저장소 선택으로 이어지므로 P1과 함께 고치는 편이 안전하다. 모든 수정은 소유자 파일에서만 진행하고 공유 모델 API는 먼저 합의한다.

| QA 수락 축 | 새/변경된 테스트가 확인해야 할 내용 | 대응 `specs.md` G ID | 현 상태 |
| --- | --- | --- | --- |
| 엄격 YAML | 10,000 허용/10,001 거부, 실제 2행 이상 mark, explicit null, entrypoint 그룹 배열/object adapter-value, 빈 icon 금지 | **G11, G30** | **NOT RUN** (새 패치 기준) |
| 파일·의존성 검증 | 리소스 내부 symlink 통과/외부 symlink 거부, split=false client-only 조기 실패, FA012의 두 중복 위치, 원래 authored depends 키 보존 | **G11, G12, G24, G26, G27** | **NOT RUN** |
| 저장소·플러그인 | Modrinth 동일 이름 다른 URL FA010, 사용자 URL 해시 충돌 방지, 선적용 Loom/KGP 충돌 FA010, 정상 Amper 단독 적용 | **G1, G10, G14, G19** | **NOT RUN** |
| Loader·진단·패키징 | caret/tilde/wildcard/extended SemVer, `test-dependencies` 위치, 공개 FA001–FA015 코드, consumer ZIP 루트 JSON 정확히 1개 및 중복 FA013 | **G3, G9, G11, G21, G26, G30** | **NOT RUN** |
| TestKit PNG | PNG 실제 ImageIO 디코드/CRC, G24 icon 리소스·최종 JAR 검증 | **G24** | **NOT RUN** |
| 구성 캐시·실제 환경 | 기존 Q-007의 미러 캐시 무효화·복구, settings-only 소비자 빌드, IDE Sync/MC 실제 런타임 | **G1, G5, G6, G15, G17, G20, G28** | **NOT RUN** |

**과거 PASS는 과거 시점의 PASS다.** owner-1~4 수정 코드가 현재 리포지토리에 보이는지 여부와 관계없이, **Prime의 수정 후 재컴파일·전체 단위·경량 TestKit·검증 태스크·최종 clean build/JAR ZIP 검사가 끝나기 전에는 이 표의 어떤 항목도 PASS로 올리지 않는다.** 기존 heavy TestKit 8개의 SKIPPED 상태도 유지된다. 사용자 명령 정책에 의해 거부된 선택 테스트 활성화 시도는 **명령 미실행**이며 우회하지 않는다.

---

## 8. Phase 4 정적 감사 후 최종 Prime 검증 결과 (2026-11-11)

> **최신 상태 기록:** 앞선 **§1~§7 총 236행은 당시의 근거·판정으로서 수정 없이 보존**했다. §7의 `AUDIT / 재검증 전` 표시는 **그 당시의 감사 상태**이며 이 §8이 이후에 받은 패치·실행 증거의 최신 판정이다. 아래 빌드/테스트는 **Prime이 실행·보고**했고, QA 작업자 6은 명령을 새로 실행하지 않았다. 파일 소유권에 따라 이번 변경은 **오직 `QA_REPORT.md`**이다.

### 8.1 최종 결론과 검증 범위

**결론: Fabric Amper 플러그인 자체 clean build 및 이번에 실행된 단위·경량 통합 테스트 범위는 PASS; 전체 소비자·Minecraft 환경 수락은 여전히 NOT RUN/NOT VERIFIED.** Phase 4 감사 18건(A1-01~A4-01)에 대해 담당 소유자별 패치가 반영되고 검증용 테스트가 추가되었다. 재검증 중 추가로 나타난 Kotlin KDoc 컴파일 오류와 이전 기대값을 사용하는 단위 테스트 6개도 담당자가 수정했다. 그러나 실제 **settings-only 소비자 프로젝트**가 Loom/Minecraft 아티팩트를 받고 배포 모드 JAR을 만들거나, 설정 캐시 복구·IDE 동기화·Minecraft 클라이언트/서버로 구동됐다는 증거는 없다.

| 구분 | 최종 관찰 결과 | 판정 기준 |
| --- | --- | --- |
| Gradle/JDK | **Gradle 9.7.1 / JDK 25** | Prime이 `/vbc/Fabric Amper`에서 실행 |
| 최종 `clean build` | **EXIT 0; `BUILD SUCCESSFUL in 40s`; 11개 actionable task: 9 executed, 2 from cache** | Gradle 플러그인 프로젝트의 빌드·검증 성공 |
| 컴파일 및 플러그인 검사 | 최종 `:compileKotlin FROM-CACHE`, `:validatePlugins FROM-CACHE`; **직전 두 번째 실행에서 둘 다 정상 통과** | 캐시 명중을 새 컴파일 실행으로 표시하지 않고 이전 성공 근거와 구분 |
| 전체 JUnit | **109 tests / 101 passed / 0 failed / 0 errors / 8 skipped** | `build/test-results/test/TEST-*.xml` **13개 suite** 교차 확인 |
| 단위 테스트 | **96 tests / 96 passed / 0 failed / 0 skipped** | `dev.fabricamper.unit.*` **10개 suite** |
| 경량 통합 테스트 | **5 tests / 5 passed / 0 failed / 0 skipped** | `FabricAmperDiagnosticsTest` 4 + `FabricAmperPngFixtureTest` 1 |
| 선택 실행 TestKit | **8 tests / 0 executed / 8 skipped** | `FabricAmperConsumerTest` JUnit XML; **G1/G3/G5/G20 등 수락 통과로 간주 금지** |

### 8.2 재실행 순서 — 실패 이력도 삭제하지 않음

공통 실행 디렉터리: `/vbc/Fabric Amper`. Prime이 보고한 정확한 최종 명령:

```powershell
& '..\Minecord\gradlew\.bat' -p . clean build --console=plain --no-daemon
```

| 시행 | 결과 | 원인/수정 담당 | 보고서 해석 |
| --- | --- | --- | --- |
| 감사 패치 후 **첫 `clean build`** | **실패**, `:compileKotlin` Kotlin 구문 오류 | `src/main/kotlin/dev/fabricamper/util/LoaderVersionConstraints.kt`의 KDoc `x/X/*`가 Kotlin 주석 안에서 `/*` 중첩 주석 시작으로 해석되어 `Unclosed comment` 발생. **owner-3**가 자기 소유 util 문서 문구만 수정 | 첫 빌드는 최종 성공으로 계산하지 않음. 사용자에게 이미 보고한 이전 배포 JAR도 최신 산출물과 구분 |
| **두 번째 `clean build`** | `:compileKotlin` 및 `:validatePlugins` **PASS**; JUnit **109개 중 6 FAIL, 8 SKIPPED** | 실패 6개는 **owner-5 소유 단위 테스트의 옛 기대값**(수정된 명세·검증 의미와 충돌)으로 분류. **owner-5**가 해당 unit fixture/expectation을 맞게 갱신. 이 시도의 6개 실패를 제품 회귀라고 확정하지 않음 | 검증 범위가 실제 실행되었으나 실패 기록 유지; 최종 성공 이전의 중간 상태 |
| **세 번째/최종 `clean build`** | **EXIT 0, `BUILD SUCCESSFUL in 40s`**. **11 actionable tasks = 9 executed + 2 from cache** | 수정된 소스·테스트 및 Gradle validation을 한 번에 검증. 이번 실행에서 `:compileKotlin`과 `:validatePlugins`는 `FROM-CACHE`였으며, **두 번째 실행의 실제 PASS**와 합쳐 컴파일/검사 성립을 판단 | **현재 최종 빌드 PASS**. 실행 가능한 단위·경량 통합 검사는 전체 PASS; heavy 소비자 TestKit 8개는 계속 SKIPPED |

두 번째 실행의 **6 FAIL**과 최종 실행의 **0 FAIL**을 서로 혼합하지 않는다. 첫 실패의 **KDoc 컴파일 오류**는 주석 원문 `/*` 패턴의 Kotlin lexer 문제이지 Fabric Loader 버전 구문을 실행해 발견한 회귀는 아니었다. 수정 후 **실제 `compileKotlin`이 통과**했고 최종 cache reuse도 성공했다.

### 8.3 최종 JUnit XML 13개 — 확인된 숫자

XML 위치: `/vbc/Fabric Amper/build/test-results/test/TEST-dev.fabricamper.*.xml`. **각 suite 첫 줄의 `tests/skipped/failures/errors`를 읽어 대조**했다. 아래 `PASS`는 `tests - skipped - failures - errors`로 계산한다.

| 테스트 suite | Tests | PASS | Fail | Skip |
| --- | ---: | ---: | ---: | ---: |
| `unit.ConfigBoundaryRegressionUnitTest` | 12 | 12 | 0 | 0 |
| `unit.DependencyAndValidationUnitTest` | 10 | 10 | 0 | 0 |
| `unit.LoaderExtendedVersionRegressionUnitTest` | 8 | 8 | 0 | 0 |
| `unit.LoaderVersionConstraintsUnitTest` | 10 | 10 | 0 | 0 |
| `unit.MetadataMapperUnitTest` | 5 | 5 | 0 | 0 |
| `unit.ModYamlLoaderUnitTest` | 10 | 10 | 0 | 0 |
| `unit.ResourceValidationUnitTest` | 8 | 8 | 0 | 0 |
| `unit.SchemaValidationUnitTest` | 13 | 13 | 0 | 0 |
| `unit.StrictYamlReaderUnitTest` | 10 | 10 | 0 | 0 |
| `unit.VerifyFabricAmperJarUnitTest` | 10 | 10 | 0 | 0 |
| **단위 소계 (10 suites)** | **96** | **96** | **0** | **0** |
| `integration.FabricAmperDiagnosticsTest` | 4 | 4 | 0 | 0 |
| `integration.FabricAmperPngFixtureTest` | 1 | 1 | 0 | 0 |
| **경량 통합 소계 (2 suites)** | **5** | **5** | **0** | **0** |
| `integration.FabricAmperConsumerTest` (heavy opt-in) | 8 | 0 | 0 | 8 |
| **전체 (13 suites)** | **109** | **101** | **0** | **8** |

특히 **`ConfigBoundaryRegressionUnitTest` 12개**, **`LoaderExtendedVersionRegressionUnitTest` 8개**, **`VerifyFabricAmperJarUnitTest` 10개**, **PNG fixture 회귀 1개**는 모두 **PASS**다. PNG 회귀 통과는 이미지 디코드/fixture 검사 근거이지 실제 Minecraft 아이콘 표시와 동일하지 않다. `VerifyFabricAmperJarUnitTest` 10 PASS는 **합성 ZIP fixture에 대한 검증기 단위 테스트**이며 실제 소비자 배포 JAR에 해당 검증기가 연결돼 성공했다는 G3/FA013 실환경 증거는 아니다.

### 8.4 §7 감사 18건 후속 분류 — 패치 반영과 수락 완료를 분리

| 감사 이슈 | 원래 담당 및 변경 영역 | 최신 패치 상태 | 확인 범위·남은 조건 |
| --- | --- | --- | --- |
| **A1-01 ~ A1-02** | **owner-1**, `config/StrictYamlReader.kt` | **PATCHED · 부분 회귀 PASS** | 컬렉션 10,000 원소 제한·FA002 실제 mark를 보강. `ConfigBoundaryRegressionUnitTest` 12개 등 단위 검증 PASS; 추가 YAML 전체 입력 조합은 미검증 |
| **A1-03 ~ A1-05** | **owner-1**, `config/ModYamlDecoder.kt` | **PATCHED · 부분 회귀 PASS** | explicit-null optional, entrypoint list/object 형태, empty icon map 검증 보강; 전체 unit 96 PASS. 실제 소비자 설정 예외 메시지는 별도 미검증 |
| **A1-06 ~ A1-08** | **owner-1**, `config/ModYamlValidator.kt` | **PATCHED · 부분 회귀 PASS** | 실경로/링크 선행 검사, split=false client-only 리소스 거부, FA012 첫·중복 위치 진단 반영; 검증기 단위 범위 PASS. 최종 client 런타임 **G18/G24** 미검증 |
| **A1-09** | **owner-1 + 교체 작업자 owner-7**, `config/ModYamlModel.kt` 등 config 계약 | **PATCHED · 부분 회귀 PASS** | `authoredDependsKeys` 원 작성 키 유지, YAML 필드 위치 provenance/진단 보강. **owner-7**도 config 후속 패치를 수행. 단위 검증 PASS지만 실제 Gradle/Loader metadata 연결은 미검증 |
| **A2-01 ~ A2-02** | **owner-2**, `loom/RepositoryConfigurer.kt` | **PATCHED · 정적/부분 회귀 확인** | Modrinth 기존 저장소 URL 일치 검사, 사용자 Maven 저장소 안정적 해시/URL 충돌 검사 반영. 실제 외부 Maven/Modrinth 해결 **G10** 미검증 |
| **A2-03** | **owner-2**, `loom/LoomConfigurer.kt`, `loom/JavaKotlinConfigurer.kt` | **PATCHED · 정적/부분 회귀 확인** | 선적용 Loom/KGP 충돌 진단 경로 보강. 실제 충돌 fixture 및 Gradle 설정 과정 **G19** 미검증 |
| **A2-04** | **owner-3** `generator/VerifyFabricAmperJar.kt` + **owner-2** `plugin/FabricAmperProjectPlugin.kt` | **PATCHED · 검증기 UNIT 10 PASS / 실제 소비자 연결 NOT RUN** | consumer ZIP 루트 metadata 중복/누락 및 nested jars 참조 검증 task와 plugin 배선 코드 추가. **가짜/합성 ZIP fixture에서의 검사 성공 ≠ 실제 소비자 JAR의 G3/G21/FA013 PASS** |
| **A3-01** | **owner-3**, `util/LoaderVersionConstraints.kt` | **PATCHED · Loader unit/extended regression PASS** | caret/tilde/wildcard/extended SemVer 분기 보강. `LoaderExtendedVersionRegressionUnitTest` 8 PASS, 기존 Loader constraints 10 PASS. 실제 Loader 런타임 **G26** 추가 검증 필요 |
| **A3-02** | **owner-3**, `generator/ValidateFabricAmper.kt` | **PATCHED · 부분 회귀 확인** | `test-dependencies[n]`과 `dependencies[n]` 진단 구분 보강. 개별 동적 consumer 로그는 미검증 |
| **A3-03** | **owner-3**, `generator/ValidateFabricAmper.kt`, `util/FabricAmperException.kt` | **PATCHED · 부분 회귀 확인** | 비공개 FA10x/FA30x 계열을 공용 FA001–FA015 진단 계약에 맞추는 매핑 보강. 모든 오류 입력 조합 및 실제 Gradle 전체 로그 수락은 미검증 |
| **A3-04** | **owner-3**, `generator/MetadataMapper.kt`, owner-1 config 모델 필드 | **PATCHED · 부분 회귀 PASS** | `authoredDependsKeys`를 통한 사용자 작성 조건/기본 주입 조건의 원천 구분. 단위 검증 PASS, consumer metadata provenance 검증은 미실행 |
| **A4-01** | **owner-4**, `integration/TestKitFixture.kt` + PNG fixture regression | **PATCHED · PNG 회귀 1 PASS** | 이전 손상된 Base64 PNG 대신 ImageIO로 유효한 1×1 PNG 생성 및 디코드 회귀 확인. 실제 G24 소비자 JAR의 아이콘 검사는 SKIPPED |

**owner-5**는 `unit/**` 테스트 소유자로서 최종 테스트 fixture/기대값을 교정했다. owner-1·owner-7은 config, owner-2는 plugin/loom, owner-3은 generator/util, owner-4는 integration 소유권을 유지했다. 이 표의 `PATCHED`는 **코드 반영과 현재 수행된 부분 검증**을 뜻한다. 실제 Fabric Loader/Gradle consumer 통합이 통과했거나 **18건의 관련 위험이 실환경에서 완전히 종결됐다는 주장**이 아니다.

### 8.5 최종 플러그인 배포 산출물 — 이전 해시 폐기하지 않고 시점 구분

| 항목 | 최신 검증값 |
| --- | --- |
| 배포 대상 | **Fabric Amper Gradle 플러그인 JAR** (Minecraft 소비자 모드 JAR이 아님) |
| 경로 | `/vbc/Fabric Amper/build/libs/fabric-amper-0.1.0.jar` |
| 크기 | **196,176 bytes** |
| SHA-256 | **`5B53A9C483A356214481B8B1E91A4F5739CF392E02B7559A7BF036149C6EFD1A`** |
| JAR 엔트리 확인 | Prime의 `jar tf` 검사에서 **두 plugin marker**(`dev.fabric-amper.settings`, `dev.fabric-amper.project`) 및 새 **`VerifyFabricAmperJar` 클래스** 확인 |
| 병행 산출물 | `build/libs/fabric-amper-0.1.0-sources.jar` 존재 |

§6의 구 SHA-256 **`176B7B1A57A668A5FE2C36D99EAFE12C417B25364E3B086FB88B0E404652BEC8`**는 **감사 패치 이전 빌드의 역사적 결과**로 남겨 두었고, 현 배포 JAR을 지칭하지 않는다. JAR 내 두 Gradle plugin marker와 verifier class가 존재하는 사실은 **실제 소비자 모드 프로젝트에서 정상 구동했다는 뜻이 아니다.**

### 8.6 남은 완료 게이트 및 잘못된 PASS 방지

| 남은 항목 | 최종 상태 | 수락 기준 |
| --- | --- | --- |
| 실제 settings-only 소비자 `help/tasks/build`, generated metadata와 JAR 내용 | **NOT RUN** (heavy TestKit 8 SKIPPED) | **G1/G2/G3/G4/G14/G17a/G21/G22/G24** |
| **실제 소비자 JAR**의 FA013 검증기 연결·ZIP 루트 1개/중복 거부·nested JAR 경로 | **UNIT FIXTURE PASS / CONSUMER NOT RUN** | **G3/G10/G21** |
| Gradle configuration cache의 HIT/MISS, YAML 수정, 구성 미러 삭제·복구 | **NOT RUN** — 최종 clean build에 `--configuration-cache` **없음** | **G5/G20**, 기존 **Q-007 계속 미검증** |
| JDK/Loom/Kotlin Gradle/Modrinth 조합·충돌·client/classTweaker 수락 | **일부 unit PASS / 실제 통합 NOT RUN** | **G7/G10/G13/G16/G18/G19/G25/G26/G27/G29** |
| IntelliJ Gradle Sync, 인터페이스 주입, Minecraft Fabric Loader client/server 구동 | **NOT RUN** | **G6/G15/G17/G28** |
| 유효 PNG fixture가 들어간 소비자 아이콘·Mixin·Class Tweaker 실제 로딩 | **PNG regression PASS / G24 consumer SKIPPED** | **G24** |

**추가 경계:** 사용자의 수동 `full-test` 단계가 `DONE`으로 표시됐더라도 **현재 최종 clean build 시각의 `FabricAmperConsumerTest.xml`은 `tests="8" skipped="8" failures="0"`**이다. 따라서 **깊은 통합 테스트 8개 PASS라는 주장은 근거가 없다.** 과거 환경변수 설정 시도 `COMMAND_NOT_ALLOWED`도 실제 실행으로 재분류하지 않는다. 최종 clean build는 **configuration cache 없이** 수행되었으며 `:compileKotlin FROM-CACHE`를 Gradle 설정 캐시 재사용과 혼동하지 않는다.

**최종 QA 판정:** 현재 확인된 Gradle 플러그인 배포 **clean build PASS / unit 96 PASS / quick integration 5 PASS / heavy consumer 8 SKIPPED / 실환경 수락 NOT RUN**. 수정 후 실행된 범위에서 재현 중인 테스트 실패 **0건**이지만, **전체 제품 버그 0건 또는 완전한 출시 수락으로 확정할 수 없다.** 이후 소비자·IDE·Minecraft 검증을 수행했다면 이 문서에 실행 명령·종료 코드·JAR 엔트리·XML·캐시 증거를 **새 시점의 후속 기록으로** 추가해야 한다.

---

## 9. G1–G30 전체 수락 매트릭스 및 검증 공백 (2026-11-11 기준)

### 9.1 적용 범위와 상태 규칙

이 절은 `specs.md:498-534`의 **G1부터 G30까지 전 항목과 독립 항목 G17a**를 누락 없이 기록한다. 총 **31개 수락 행**이다. **§1~§8의 344행은 원문 그대로 보존**했다. 새 테스트·빌드·설정 수정 없이 기존 증거를 분류한 **수락 계획/증거 대장**이며, 이미 실행된 결과만 실측이라고 표현한다.

**기준 실행:** Prime의 Gradle **9.7.1/JDK25** 플러그인 프로젝트 `clean build --console=plain --no-daemon` **EXIT 0**, JUnit **13 suites · 109 tests = 96 unit PASS + 5 quick integration PASS + 8 heavy consumer SKIPPED**, failures/errors **0**. 경량 통합 5개는 `FabricAmperDiagnosticsTest` **4**와 `FabricAmperPngFixtureTest` **1**이다. `VerifyFabricAmperJarUnitTest` **10 PASS**는 **합성 ZIP fixture**를 검사했으며 실제 소비자 JAR 성공을 입증하지 않는다. 플러그인 자체 JAR SHA-256은 §8.5의 최신 값이며, 사용자가 소비자 프로젝트에서 생성한 모드 JAR의 해시가 아니다.

**수락 상태 규칙:** 아래 G 행의 최종 수락은 모두 **`NOT VERIFIED`**다. `부분 근거`는 유닛·진단·정적 배선·합성 fixture의 **좁은 시나리오가 실제 통과했다는 사실**이고, 특정 G 전체를 `PASS`로 승격했다는 뜻이 아니다. `SKIPPED`는 JUnit이 해당 선택 실행 테스트를 건너뛴 것, `NOT RUN`은 요구된 환경·명령이 실행되지 않은 것이다. `:compileKotlin FROM-CACHE`를 **Gradle configuration cache 검증**으로 해석하지 않는다. 새 결과를 받을 때 Prime이 **명령·종료 코드·JUnit XML/로그·산출물·실행 환경**을 제공한 경우에만 해당 G 행을 재평가한다.

**담당 표기:** **O1** = config/build(작업자 1 및 인계받은 **O7** config), **O2** = plugin/loom, **O3** = generator/util, **O4** = TestKit 통합·fixture, **O5** = unit 회귀. 복수 담당은 제품 기능의 최종 수정 소유자/실행 증거 확보 소유자를 함께 적는다. Prime은 별도 Gradle/Loom 조합·IDE·Minecraft 수락의 실행과 결과 기록을 총괄한다. owner별 기존 승인 파일 범위를 넘어서는 변경을 이 표가 허용하지 않는다.

### 9.2 수락 테스트별 증거·확인 방법·잔여 과제

| ID | 실제 수락을 위한 재현·검증 방법 | 현재 확인된 근거(범위를 제한해서 표기) | 현재 공백 및 후속 담당 — **G 수락 상태** |
| --- | --- | --- | --- |
| **G1** | **`build.gradle(.kts)`가 없는** 격리된 소비자 폴더에 `settings.gradle.kts`의 Amper ID 하나와 YAML만 두고 GradleRunner `help` 실행; Settings→Project 적용, exit 0, 구성 실패 없음 확인 | 두 플러그인 **marker** 존재; `FabricAmperConsumerTest` 내 G1/G2/G14 fixture 작성됨 | 해당 heavy fixture **SKIPPED**, 실제 `help` **NOT RUN**. 플러그인 classpath·Loom 초기화 증거 필요. **O2+O4/Prime · NOT VERIFIED** |
| **G2** | 같은 settings-only fixture에서 `tasks --all` 실행해 `compileJava`, `processResources`, `generateFabricModJson`, `jar`, `runClient`, `runServer` 태스크가 실제 등록됐는지 로그로 확인 | 태스크 생성/연결 소스 존재; G1/G2/G14 TestKit 시나리오 존재 | 태스크 목록 조회 **SKIPPED**; 플러그인 JAR의 task API 클래스만으로 충분하지 않음. **O2+O3+O4/Prime · NOT VERIFIED** |
| **G3** | settings-only 모드 fixture에서 실제 `build` 수행, 배포 모드 JAR의 ZIP **root `fabric.mod.json` 정확히 1개**, ID/version/entrypoint·클래스와 소비자 JAR 정상 여부 확인; 없거나 중복이면 **FA013** | `VerifyFabricAmperJarUnitTest` **10 PASS(합성 ZIP)**, 플러그인 JAR에 verifier 클래스 포함. G3/G21 heavy fixture 있음 | 실제 **소비자** ZIP/컴파일/FA013 task hook **NOT RUN**. plugin 자신의 JAR과 구별. **O2+O3+O4/Prime · NOT VERIFIED** |
| **G4** | 동일 소비자 fixture의 YAML `mod.id`·`mod.version`을 수정한 뒤 재빌드; 프로젝트 설정·생성 JSON·JAR 파일명/내용이 변경값인지 비교 | config/metadata 회귀 단위 테스트 존재; G4 heavy TestKit 작성 | 두 차례 실제 소비자 빌드 및 증분 구성 상태 비교 **SKIPPED**. **O1+O2+O3+O4/Prime · NOT VERIFIED** |
| **G5** | 소비자 `build --configuration-cache`를 원본 2회 → YAML 의존성·Java·버전 편집 → 3·4회. 2/4회 **CC 재사용**, 3회 **CC 무효화 및 JSON 재생성**을 로그/산출물로 확인 | `ProviderFactory.fileContents` 사용 및 구성 입력 추적 코드 정적 확인, G5 heavy fixture 존재 | **`--configuration-cache` 포함 소비자 실행 NOT RUN**. 최종 자체 빌드의 `FROM-CACHE`는 다른 기능. Q-007 포함. **O2+O1+O3+O4/Prime · NOT VERIFIED** |
| **G6** | **실제 IntelliJ**에서 settings-only 프로젝트 Gradle Sync 수행; Java/Kotlin 소스·의존성·Loom Run configuration과 오류 없는 import 확인 | 플러그인 marker 및 Java/Kotlin/Loom 설정 코드만 확인 | IDE 자체 사용/스크린샷·sync 로그·run config 증거 **NOT RUN**. **O2+O4/Prime · NOT VERIFIED** |
| **G7** | Java-only fixture `build`, YAML에서 Kotlin on/off 전환 후 다시 `build`; Kotlin task 출현/소멸, FLK adapter·classpath, Java/Kotlin `.class`와 최종 JAR 확인 | Kotlin 버전/adapter·config 관련 일부 **unit PASS**, G7 heavy TestKit 존재 | Java/Kotlin **소비자 3상태 빌드 SKIPPED**; 실제 classpath·adapter·Kotlin output 미확인. **O2+O1+O4/Prime · NOT VERIFIED** |
| **G8** | `minecraft.version: "26.3"` + `mappings: yarn`인 소비자 fixture의 `help`가 **FA004**로 실패하고 원래 YAML이 유지되는지 확인 | **경량 `FabricAmperDiagnosticsTest` 4 PASS 중 G8 Yarn 오류 검사 1개 PASS**, config 관련 unit PASS | **FA004의 경량 재현 근거 확보**. Loader/다른 프로파일 조합까지 확장된 수락은 아직 Prime 별도 확인 없음. **O1+O4/Prime · NOT VERIFIED** |
| **G9** | 지원 공개가 결정된 경우 **Minecraft 1.21.11** 분리 fixture로 매핑·레거시 Access Widener·remap Loom 및 `remapJar`를 실제 빌드·실행 | 레거시 `.accesswidener` 조건 검증을 수정, 버전 관련 일부 **unit PASS** | 구버전 Loom 의존성/매핑 다운로드·`remapJar`/런타임 **NOT RUN**. 이 상태로 레거시 지원 완료 공표 불가. **O1+O2+O3+O4/Prime · NOT VERIFIED** |
| **G10** | Maven/Modrinth `compile-only`, `runtime-only`, `include`, `exported`별 소비자 의존성 그래프/빌드 확인; nested JAR의 `jars[].file` 경로와 실제 Loader 로딩 교차 점검 | scope/config 검증 관련 **unit PASS**, 저장소 URL·충돌 코드 패치, verifier 합성 ZIP 10 PASS | 실제 원격 저장소 해결·consumer include/nested JAR·런타임 **NOT RUN**. **O2+O3+O1+O4/Prime · NOT VERIFIED** |
| **G11** | duplicate key/alias/tag/잘못된 타입·버전 숫자·리소스 누락·Class Tweaker 헤더 및 **잘못된 멤버**를 독립 YAML fixture로 오류 유도; FA 코드·행열·원본/출력 보존 확인, 가능한 경우 `validateAccessWidener` 실행 | `ConfigBoundaryRegressionUnitTest` **12 PASS**, `StrictYamlReaderUnitTest` **10 PASS**, 기타 schema/resource 단위 PASS, 경량 YAML 진단 fixture PASS | 파서/검증기의 여러 **부분 진단 검증은 통과**. 전체 FA 케이스와 Loom 실제 `validateAccessWidener` **NOT RUN**. **O1/O7+O3+O2+O4/Prime · NOT VERIFIED** |
| **G12** | 두 독립 소비자 fixture의 `src/main/resources/fabric.mod.json` 및 `src/client/resources/fabric.mod.json`을 미리 기록·해시하고 `help` 실행; 각각 **FA008**, 원본 내용·바이트 불변 확인 | **경량 `FabricAmperDiagnosticsTest` 4 PASS 중 G12 main/client 선행 메타데이터 거부 fixture 1개 PASS**, resource unit PASS | 해당 오류의 경량 재현 근거 확보. 전체 생성/JAR 작업과 결합된 원본 보존은 **NOT RUN**. **O1+O3+O4/Prime · NOT VERIFIED** |
| **G13** | **Gradle 9.7.0 + Loom 1.18.4**, **Gradle 9.9.1 + Loom 1.19.4**를 분리된 소비자 fixture에서 각각 플러그인 resolution/실제 `build`·KGP 호환성 판정 | **Gradle 9.7.1/JDK25**에서 Fabric Amper **자체** clean build 성공만 확인 | 지정된 **두 정확한 조합 모두 NOT RUN**; 인접 버전 자체 빌드를 대체 근거로 쓸 수 없음. **O2+O4/Prime · NOT VERIFIED** |
| **G14** | 소비자의 settings `plugins { id("dev.fabric-amper.settings") }`만 선언하고 추가 Loom/KGP 플러그인 선언 없이 `help/build`·플러그인 ID/classloader 확인 | 배포 JAR에 **Settings/Project marker 2개** 존재; G1/G2/G14 heavy fixture 작성 | 실제 소비자 plugin resolution 및 선택 KGP 적용 **SKIPPED**. marker의 존재와 실제 적용을 구분. **O2+O4/Prime · NOT VERIFIED** |
| **G15** | EULA·인증·격리 정책을 준수하는 새 테스트 인스턴스에서 `runClient`와 `runServer` 실행, Fabric Loader 로그가 생성 metadata ID/version/entrypoint를 인식하는지 확인 | 플러그인 자체 컴파일 성공·메타데이터 생성 코드 존재 | **게임 클라이언트/전용 서버 실행 NOT RUN**; 실제 Fabric Loader 동작을 추측하지 않음. **O2+O3+O4/Prime · NOT VERIFIED** |
| **G16** | 유효 `classTweaker v1 official` + 의도적으로 잘못된 멤버 선언을 가진 두 resource fixture에서 Loom `accessWidenerPath` 연결·`validateAccessWidener` 성공/실패를 각각 검사 | config/generator 헤더·main-only 경로 검증 **unit PASS**, Q-002/Q-005 수정 이력 | Loom 태스크 실제 실행·클래스/멤버 해석 **NOT RUN**. **O1+O2+O3+O4/Prime · NOT VERIFIED** |
| **G17** | 사용자 YAML `mod.custom`의 interface injection 정보만 제공하고 원본 `fabric.mod.json` 없는 소비자로 `help`, compile, IntelliJ Sync, 런타임에서 실제 인터페이스 주입 여부 확인 | `MetadataMapper`·미러 구성 경로 정적 확인; custom/YAML 관련 **unit PASS** | **실제 Loom interface injection·IDE·게임 NOT RUN**. 미러 JSON 존재만으로 기능 완료 불가. **O2+O3+O4/Prime · NOT VERIFIED** |
| **G17a** | 원본 메타데이터와 `generateFabricModJson` 출력 파일이 **아직 모두 없는 상태**에서 소비자 `help`/IDE Sync 수행; Loom `fabricModJsonPath`가 구성 단계 미러의 ID·version·interface injection 정보를 읽는지 확인 | G17a heavy fixture가 실제 작성돼 있고 미러 관련 일부 정적 배선 근거 존재 | 해당 heavy fixture **SKIPPED**; 생성 태스크 실행 전 Loom 조회·IDE Sync **NOT RUN**. **O2+O3+O4/Prime · NOT VERIFIED** |
| **G18** | `splitEnvironmentSourceSets=true` 및 `target=client` 모드/라이브러리를 가진 소비자에서 `clientImplementation`·`clientCompileOnly` 해석, main의 접근 실패와 client의 접근 성공을 컴파일로 확인 | `ConfigBoundaryRegressionUnitTest` **12 PASS** 중 split=false client-only 리소스 거부 관련 검사, dependency validator 단위 PASS | **실제 분리 SourceSet 클래스패스 및 client/공통 컴파일 NOT RUN**. **O1+O2+O4/Prime · NOT VERIFIED** |
| **G19** | 다른 Settings/Project plugin이 Loom 또는 KGP 버전을 선적용한 소비자 fixture에서 Amper의 명확한 충돌 **FA010**과 정상 단일 적용을 비교; 중복 classloader/애매한 성공 금지 | 사전 적용 Loom/KGP 충돌 검사 코드 정적 확인, 기존 audit A2-03 수정 | 외부 plugin classloader를 실제로 섞은 소비자 TestKit **NOT RUN**. **O2+O4/Prime · NOT VERIFIED** |
| **G20** | 구성 캐시를 켠 settings-only 소비자에서 `.gradle/fabric-amper/loom` 미러를 정상→삭제→손상시키고 동일 YAML로 `help/build` 반복; `clean`/IDE Sync/병렬 구성도 포함해 재생성·해시·원자 기록·오래된 상태 미사용 확인 | content-addressed 미러/Gradle fileContents 추적 코드 존재; G20 heavy fixture 작성 | **G20 heavy fixture SKIPPED**, 구성 캐시 복구 실험 **NOT RUN**. **Q-007 계속 미검증**. **O2+O1+O3+O4/Prime · NOT VERIFIED** |
| **G21** | 소비자 `generateFabricModJson`을 단독 실행하고 구성 미러와 태스크 JSON의 **바이트 동일성** 비교; `jar` 반복·클린 빌드로 root metadata 개수와 결정적 출력 해시 확인 | metadata mapper 단위 회귀·**합성 ZIP verifier 10 PASS**, G3/G21 heavy fixture 존재 | 실제 소비자 생성 task·미러·JAR 바이트 비교 **SKIPPED**. 자체 플러그인 SHA를 소비자 결정성으로 대체 불가. **O2+O3+O4/Prime · NOT VERIFIED** |
| **G22** | `mod.version`/`group`/`archivesName`이 지정된 소비자에서 `jar`·`sourcesJar`, Project properties, JSON version, 모드 JAR 파일명과 POM 좌표를 비교 | Project.version/group/archivesName 설정 코드 정적 확인, 모드 version 관련 단위 PASS; **플러그인** sources JAR 존재 | **소비자** `jar/sourcesJar` 미실행. 플러그인 자기 JAR 파일명은 G22 근거 아님. **O1+O2+O3+O4/Prime · NOT VERIFIED** |
| **G23** | 소비자 YAML에 Jupiter `test-dependencies`를 넣고 테스트 1개를 작성해 `gradle test` 실행; `useJUnitPlatform`, `junit-platform-launcher` 런타임 클래스패스·테스트 발견/통과 검증 | **Fabric Amper 플러그인 프로젝트** JUnit 109개가 실행/skip으로 집계됨; test-dependencies 디코딩·Gradle 매핑 코드 및 단위 검사 | **소비자** JUnit 동작과 launcher 주입 **NOT RUN**. 자체 플러그인의 JUnit 성공을 소비자 자동 테스트 구성 성공으로 해석하지 않음. **O1+O2+O4/Prime · NOT VERIFIED** |
| **G24** | main 리소스의 Mixin·Class Tweaker·아이콘을 넣은 소비자로 `help`/IDE Sync/`build` 수행; 미러 상대경로 참조, 원본 리소스 편집 전후 snapshot hash/CC/실제 JAR·ZIP 중복을 확인 | **PNG fixture 1 PASS**, config symlink/리소스 관련 unit PASS, G24 heavy fixture 작성 | PNG 유효성은 확인됐으나 **실제 소비자 G24 fixture SKIPPED**; IDE·Loom 구성 읽기·JAR 전파·캐시 갱신 미확인. **O1+O2+O3+O4/Prime · NOT VERIFIED** |
| **G25** | **Loom 1.19.4** 플러그인을 **Gradle 9.7.0/9.8.0/9.9.1** 각각에서 resolution; Gradle `.module` variant 요구가 9.8 API에 미치는 영향과 각 조합 실제 실행 결과를 독립 기록 | Gradle **9.7.1** 자체 플러그인 빌드 PASS, 사전 스펙의 Loom 배포 정적 조사만 존재 | 세 조합 모두 **NOT RUN**; 기본 환경의 다른 Loom/Gradle을 대입 불가. **O2+O4/Prime · NOT VERIFIED** |
| **G26** | YAML 사용자 `mod.depends`와 Minecraft/Loader/Java/API/Kotlin의 선택 버전을 만족·충돌·비교 불가 사례별로 조합해 Loader와 동일한 AND/OR·caret/tilde/wildcard·원래 조건 보존/행열 진단을 비교 | `LoaderExtendedVersionRegressionUnitTest` **8 PASS**, `LoaderVersionConstraintsUnitTest` **10 PASS**, config/metadata unit PASS, A1-09/A3-04 provenance 수정 | Parser/validator의 **단위 근거 확보**. 실제 Fabric Loader의 판정·Gradle consumer metadata 계약 교차 검증 **NOT RUN**. **O1/O7+O3+O4/Prime · NOT VERIFIED** |
| **G27** | `environment:"*"` + client 전용 라이브러리/모드로 split 소비자 생성, main/client classpath·Loader `depends`·전용 서버 부팅·Mixin 오염 여부 확인; 불가능한 side별 필수 의존성은 오류 기대 | 환경/target 검증 관련 unit PASS, split=false client 리소스 조기 거부 회귀 PASS | **실제 side-aware Loader 런타임·server startup·의존성 그래프 NOT RUN**. **O1+O2+O3+O4/Prime · NOT VERIFIED** |
| **G28** | `settings.gradle.kts`만 있는 폴더를 IntelliJ에서 처음 열어 Gradle 프로젝트 인식 확인; 수동 Sync, `mod.yaml`만 저장한 뒤 자동 Sync 여부를 분리 기록; 안 되면 사용자 문서에 제한 기재 | Gradle settings plugin marker 존재 및 일반 구성 코드만 정적 확인 | IntelliJ 최초 인식·재동기화·자동 reload 전부 **NOT RUN**. **O2+O4/Prime · NOT VERIFIED** |
| **G29** | `kotlin.enabled=false`이고 KGP가 외부 캐시에 없는 격리된 초기 환경에서 전이 플러그인 다운로드 비용을 측정; 지연 KGP 대안은 별도로 classloader/isolated-projects/CC를 통과할 때만 채택 | 플러그인 배포에 KGP classpath 의존성이 선언된 정적 설계, Kotlin on/off 일부 단위 검사 | **cold-cache 다운로드/비용 측정 및 지연 적용 실험 NOT RUN**. 비용 개선 완료 주장 불가. **O2+O4/Prime · NOT VERIFIED** |
| **G30** | `mod.yaml`·`mod.yml` 동시/교체, 잘못된 UTF-8, 다중/빈 문서, scalar alias/tag, root ZIP 중복을 독립 fixture로 검증; **FA001/FA002/FA003/FA013**, CC 재구성 무효화, 원본 보존 확인 | `StrictYamlReader`/loader/config **unit PASS**, 경량 YAML aliases/구문 진단 PASS, 합성 ZIP verifier **10 PASS** | 해당 **개별 검증의 부분 증거**는 있으나 **별칭 전환·CC와 실제 consumer ZIP 경로 전부 NOT RUN**. **O1/O7+O2+O3+O4/Prime · NOT VERIFIED** |

### 9.3 G 수락 승인에 필요한 제출 증거와 우선순위

**단계 1 — 소비자 최소 동작:** O2/O4가 G1·G2·G14에 대해 실제 settings-only Gradle TestKit `help`·`tasks --all`의 종료 코드, plugin resolution·Loom task 목록을 제출한다. 그다음 O2/O3/O4가 G3·G21·G22의 생성 JSON·미러·소비자 JAR ZIP root entry 수·모드 클래스·해시를 제시한다. JAR verifier의 **unit fixture PASS**는 이 제출을 대체하지 못한다.

**단계 2 — 설정 안전성과 기능:** O1/O7/O3/O4는 G11·G12·G26·G30의 FA 진단/위치·원본 보존을 확장하고, O2/O4는 G4·G5·G7·G10·G16·G18·G19·G20·G23·G24·G27의 소비자 classpath, KGP/JUnit/Loader 동작, snapshot 및 구성 캐시를 실제 재현한다. configuration cache가 필요한 시나리오에서는 `--configuration-cache`를 **명시**하고 `FROM-CACHE`(태스크 캐시)와 CC reuse를 구별한다.

**단계 3 — 환경별 출시 게이트:** Prime이 O2/O4와 함께 G6·G15·G17·G28의 IntelliJ/클라이언트/전용 서버를 안전한 격리 환경에서 검증한다. G9 지원 공개는 레거시 빌드·런타임 검증 후에만 결정한다. G13·G25는 정확히 지정된 Gradle/Loom **서로 다른 조합마다** 독립 증거를 남기고, G29는 cold-cache와 옵션 전환 비용을 별도로 기록한다.

**이번 §9의 변경 범위:** `QA_REPORT.md`에 매트릭스만 추가. **새 Gradle 명령 0회, 새 테스트 실행 0회, 소스/설정/다른 문서 변경 0건.** Prime의 새로운 실행 결과가 도착하기 전까지 G1~G30 및 G17a의 **전체 수락 등급은 모두 `NOT VERIFIED`**로 유지한다. 이는 부분 검증 성과를 부정하는 것이 아니라, 명세의 **전체 수락 조건**과 실제 실행 범위를 혼동하지 않기 위한 엄격한 기록 방식이다.

---

## 10. 소비자 TestKit opt-in 1·2차 실패 후속 기록 — G1/G2/G14/G17a (2026-11-11)

> **최신 증거이며 이전 §1~§9는 역사적 스냅샷으로 원문 보존.** Prime이 `-PfabricAmperTestkitFull=true` 옵션으로 **실제 opt-in 소비자 테스트 2개를 실행**했다. 따라서 §8~§9의 “heavy 8 SKIPPED”는 **그 이전 최종 자체 clean build**의 결과이지 최신 opt-in 실행 상태의 설명은 아니다. 이번 실행은 **선택된 2개만** 실제 실행했으며 **둘 다 FAIL**이다. 나머지 6개를 PASS/FAIL 또는 이번 실행의 SKIPPED로 추정하지 않는다. **이 QA 작업자는 명령·빌드·테스트를 수행하지 않았다.**

### 10.1 실행별 시간순 결과 및 원인 구분

| 실행 | 실제 대상·JUnit 증거 | 관찰한 장애와 단계 | 원인 귀속·현재 판정 |
| --- | --- | --- | --- |
| **1차 opt-in** (Loom 옵션 수정 전) | `FabricAmperConsumerTest`의 **G1/G2/G14**와 **G17a**, 두 메서드 실행 **2 FAIL / 0 SKIPPED** | settings-only 소비자 `help`에서 `Fabric Loom: 1.17.21` 뒤 `Failed to setup Minecraft, java.lang.IllegalArgumentException: Configuration 'mappings' has no dependencies` | **제품 P0 재현 확인**. `loom/LoomConfigurer.kt`에서 nonremap Loom 프로그램 적용 시 플러그인 ID 인식/disable-obfuscation 설정 순서와 관련. **owner-2 (plugin/loom)**, 필요한 빌드 계약 변경은 **owner-7**과 조율. 1차 P0는 패치 전 기록으로 유지 |
| **2차 opt-in** (owner-2/7 Loom property 패치 이후) | 현재 `build/test-results/test/TEST-dev.fabricamper.integration.FabricAmperConsumerTest.xml:1-5`: `tests="2" skipped="0" failures="2" errors="0"`; **G1/G2/G14**, **G17a** 실제 두 메서드 실패 | `Fabric Loom: 1.17.21` 이후 **이전 `mappings` 오류 대신** `Problem configuring task :help from command line` 및 `Unknown command-line option '--no-daemon'` 발생. 1차 오류가 재현되지 않고 **task CLI parsing 단계까지 도달** | **테스트 하네스 오류 CONFIRMED**, 제품 P0 오류와 **별도**. **owner-4 (integration fixture)**의 `TestKitFixture.kt:44-49` 인자 순서 수정 요청. 해당 수정 후 정상 `help`·G17a 결과를 재확인하기 전 **제품 측 G1 PASS 아님** |

**2차 오류의 실제 전달 인자:** `src/test/kotlin/dev/fabricamper/integration/TestKitFixture.kt:44-49`는 `args.toList() + listOf("--console=plain", "--stacktrace", "--no-daemon", "--no-watch-fs", "--gradle-user-home", isolatedGradleHome.toString())`로 합친다. 그 결과 GradleRunner의 실제 인자는 `[help, --console=plain, --stacktrace, --no-daemon, --no-watch-fs, --gradle-user-home, ...]`가 된다. **Gradle 9.7.1에서 `--no-daemon`을 `:help` 태스크 옵션으로 잘못 해석**하여 실패했다. `--no-daemon`, `--gradle-user-home` 등 전역 Gradle 옵션을 **요청 task 이름보다 앞에** 배치하고, 필요한 나머지 옵션도 실제 Gradle 지원 위치·파싱을 확인해야 한다. 수정은 **owner-4의 integration harness 소유 파일만**에서 수행; 이번 QA는 수정하지 않는다.

### 10.2 현재 수락 게이트와 패치 재검증 요청

| 게이트/증거 | 최신 상태 | 완료 판단을 위해 필요한 결과·담당 |
| --- | --- | --- |
| **G1** settings-only `help` | **OPEN · FAIL (2차 실행은 하네스 CLI 인자 오류)** | **owner-4** 인자 순서 수정 후 동일 opt-in `help` **EXIT 0**, Settings→Project/Loom 적용 로그; **owner-2/7**이 첫 P0 `mappings` 오류 재발 여부 확인 |
| **G2** 소비자 `tasks --all` | **NOT VERIFIED / 실행 경로 차단** | G1 수정 후 태스크 전체 목록 실제 조회·필수 태스크 존재 확인. **owner-2+owner-4/Prime** |
| **G14** settings 한 플러그인으로 Loom/KGP 적용 | **NOT VERIFIED / 실행 경로 차단** | G1 동일 fixture에서 성공한 plugin resolution/classloader·선택 KGP 증거. **owner-2+owner-4/Prime** |
| **G17a** 생성 출력 전 구성 미러 인식 | **OPEN · FAIL (2차 실행은 하네스 CLI 인자 오류)** | **owner-4** 수정 후 `help`에서 미러 파일/ID/버전/interface injection metadata 조회 성공, 태스크 출력 없이 구성 완료; **owner-2/7/Prime** 재검증 |
| **G3/G5/G20/나머지 소비자 심층 수락** | **NOT VERIFIED** | 두 테스트 재실행 성공을 확인하기 전 후속 heavy 소비자 빌드/설정 캐시를 완료했다고 말하지 않음 |

**엄격한 판정:** 1차 실측에서는 **실제 P0 소비자 `mappings` 오류**가 확인되었다. 2차 실측에서는 그 오류가 **관찰되지 않았고 CLI parsing까지 진행**했으므로 Loom property 패치가 해당 증상을 해소했음을 **부분적으로 뒷받침**한다. 그러나 **성공한 `help`가 아직 없으므로** “P0 제품 차단 완전 해결” 또는 **G1/G2/G14/G17a PASS**로 표시하지 않는다. 이후 owner-4 하네스 수정과 Prime의 실제 재실행·XML 갱신이 필요하다. 이 2차 실패는 **테스트 인자 조립 결함**이고, Loader나 설정 파일을 다시 수정해야 한다는 증거가 아니다.

**이 §10의 변경 범위:** `QA_REPORT.md`에 기록만 추가. **기존 §1~§9 원문 보존; 생산 소스·테스트·빌드 변경 0, 새 Gradle 실행 0.**

---

## 11. 실제 opt-in 소비자 빌드 진전 및 G5 Configuration Cache P1 발견 (2026-11-11)

> **증거 시점:** 기존 **§1~§10의 433행은 역사적 기록으로 원문 보존**한다. 이번 절은 Prime이 **`-PfabricAmperTestkitFull=true`**로 각각 **필터링해 실행한** 실제 소비자 TestKit의 후속 결과다. §8의 **96 unit PASS + 5 quick PASS + 8 consumer SKIPPED**는 *그 이전 clean build 실행*이고, §10의 G1/G17a **2 FAIL**은 *인자 하네스 패치 전* 기록이다. **각 필터 실행은 독립적인 JUnit 실행**이므로 동일 경로 `FabricAmperConsumerTest.xml`이 마지막 필터 실행 결과로 덮어써질 수 있다. 따라서 과거 필터별 **2 PASS / 1 PASS**와 마지막 **1 FAIL**을 하나의 현재 XML suite에 동시에 들어 있다고 집계하거나, 서로 다른 시점의 전체 tests 합계로 오인하지 않는다. 아래 G1·G3 성공은 Prime 전달 결과, G5 실패는 현재 XML도 직접 대조했다.

### 11.1 필터별 실제 실행 결과 — 오래된 오류와 새 오류 분리

| 필터 / 포함 수락 | 해당 필터 실행 결과 | 확인된 실제 소비자 동작 | 현재 판정 / 범위 한계 |
| --- | --- | --- | --- |
| `FabricAmperConsumerTest.G1*` — **G1/G2/G14, G17a** | **2 tests · 2 PASS · 0 FAIL · 0 SKIP**, Gradle **9.7.1/JDK25**, `-PfabricAmperTestkitFull=true` | **settings-only** fixture에서 nested `help`와 `tasks --all` 성공, Loom/Project 연결 및 구성 단계 mirror 조회를 검사. 이전 §10의 `mappings` P0와 `--no-daemon` TestKit 인자 오류를 거쳐 재실행한 **성공 증거** | **G1/G2/G14의 해당 TestKit 시나리오 PASS, G17a 구성 시점 TestKit 시나리오 PASS**. 단, G17a 명세의 **IDE Sync**와 G17 실제 interface injection 런타임은 **NOT RUN** |
| `FabricAmperConsumerTest.G3*` — **G3/G21** | **1 test · 1 PASS · 0 FAIL · 0 SKIP**, 실제 소비자 `build` 실행 | 소비자 모드 JAR ZIP 루트 `fabric.mod.json` **정확히 1개**, `generateFabricModJson` 산출 JSON과 Loom 구성 미러의 **바이트 동일성**, JAR 안에 컴파일된 **Java `.class`** 존재, 반복 `jar`에서 metadata 단일성 확인 | **G3/G21의 해당 실제 소비자 JAR TestKit 시나리오 PASS**. 전체 G21에서 요구하는 **모든 환경의 클린 빌드 결정성/캐시 변동** 및 MC Loader 실행은 별도 미검증 |
| `FabricAmperConsumerTest.G5*` — **G5** | **1 test · 0 PASS · 1 FAIL · 0 SKIP**. 최신 `build/test-results/test/TEST-dev.fabricamper.integration.FabricAmperConsumerTest.xml:1-5`에서 `tests="1" skipped="0" failures="1" errors="0"` | 첫 소비자 `build --configuration-cache` 후 두 번째 소비자 `build` 자체는 **`BUILD SUCCESSFUL in 1s`**, **`Configuration cache entry stored`**. 하지만 **재사용(reuse)이 아니라 입력 변경으로 무효화/재계산**됨 | **G5 FAIL · OPEN P1**. `FabricAmperConsumerTest.kt:107-113`의 두 번째 실행 `Reusing configuration cache` assertion에서 실패. 이후 YAML 변경/4차 재사용 단계는 해당 테스트에서 **검증되지 않음** |

**시간순 근거:** 이전 제품 P0(`Configuration 'mappings' has no dependencies`) 및 후속 TestKit 하네스 `Unknown command-line option '--no-daemon'`는 §10에 **그대로 보존**한다. 지금의 **G1/G2/G14/G17a 필터 PASS**는 해당 소비자 `help/tasks` 단계가 두 오류를 더 이상 재현하지 않고 통과했다는 **새 근거**다. 그렇다고 IDE/MC/전체 G1~G30을 동시에 PASS로 승격하지 않는다.

### 11.2 신규 **G5-P1-CC-FIRST-MIRROR** — 첫 구성 시 파일 부재를 설정 캐시 입력으로 추적

| 필드 | 확인 내용 |
| --- | --- |
| **ID / 심각도 / 상태** | **G5-P1-CC-FIRST-MIRROR / P1 / CONFIRMED in real consumer opt-in**; **OPEN — owner-2 수정 진행, 패치 후 테스트 성공 보고 없음** |
| **기능·근거** | `specs.md` **G5**, 연관 **G20/Q-007**. `src/test/kotlin/dev/fabricamper/integration/FabricAmperConsumerTest.kt:107-123`의 **113행** assertion이 실패했다. 현재 최신 `FabricAmperConsumerTest.xml`은 **1 test, 1 failure, 0 skip** |
| **실제 Gradle 로그** | `Calculating task graph as configuration cache cannot be reused because the file system entry '.gradle\\fabric-amper\\loom\\<hash>\\fabric.mod.json' has been created.` 즉 **첫 실행에 없던 미러가 만들어진 사실**이 캐시 입력 변화로 추적되어 두 번째 실행 재사용을 막음 |
| **확인된 현상과 원인 구분** | nested 두 번째 `build`는 **BUILD SUCCESSFUL**. 문제는 **태스크 빌드 실패/JSON 누락**이 아니라 **configuration cache HIT 실패**다. 최초 미러를 실체화하기 전에 `Files.exists`, `Files.isRegularFile` 등 부재 상태를 조회하면 Gradle 9.7.1이 그 **부재를 구성 입력**으로 관찰해 다음 실행의 생성된 파일과 충돌할 수 있음 |
| **원인 소유 파일** | **owner-2**, `src/main/kotlin/dev/fabricamper/loom/LoomMetadataMirror.kt`의 미러 파일 생성/콘텐츠 확인 경로, 과거 미러 확인 시점의 pre-write 존재 검사 |
| **수정 원칙** | **첫 구성에서 대상 파일 부재를 조회하지 않고** 콘텐츠 주소 기반 경로에 임시 파일을 쓰고 원자적 move로 미러를 실체화한 다음에만 `ProviderFactory.fileContents(...).asBytes.get()`으로 읽기 추적. 기존 **원자적 기록**, **손상 복구 G20**, 경로·SHA 무결성 유지. 단, 현재 소스에 owner-2 패치 코드가 보이더라도 **새 G5 XML PASS 전에는 해결 판정 불가** |
| **재검증 게이트** | 동일 `-PfabricAmperTestkitFull=true` **G5 필터 재실행**: 최초 설정 캐시 저장 → **두 번째 재사용** → YAML 의존성/Java/버전 수정 후 **세 번째 무효화·최신 JSON** → **네 번째 재사용**. 이어서 **G20** 미러 삭제/손상 및 복구 후 재사용 시나리오 확인 |

특히 **`UP-TO-DATE` 또는 `FROM-CACHE` task 출력은 configuration cache 재사용의 근거가 아니며**, 테스트는 실제 `Reusing configuration cache` 조건을 확인해야 한다. 이 오류는 **P0 소비자 초기화 실패의 재발이 아니라 별도의 P1 캐시 수명주기 결함**이다.

### 11.3 최신 항목별 수락 대장 갱신

| G 항목 | 기존 §9 시점 | §11 최신 증거 기준 | 반드시 남겨 둘 경계 |
| --- | --- | --- | --- |
| **G1/G2/G14** | heavy SKIPPED / 이후 §10 두 테스트 실패 | **선별된 실제 소비자 TestKit PASS (G1* 2/2의 첫 메서드)** | 설정만으로 `help/tasks` 성공 확인. 다른 Gradle/Loom 버전의 해석·실제 게임은 검증과 별개 |
| **G17a** | heavy SKIPPED / 이후 §10 실패 | **구성 시점 mirror 관련 TestKit PASS (G1* 2/2의 둘째 메서드)** | `specs.md`가 요구하는 **실제 IntelliJ Sync**는 아직 NOT RUN. G17 전체 interface injection도 별개 |
| **G3/G21** | heavy SKIPPED / 합성 ZIP verifier unit 10 PASS | **실제 소비자 ZIP + 바이트 비교 TestKit PASS (G3* 1/1)** | 실제 Java `.class` 포함·루트 JSON 1개 확인. 환경별 deterministic clean build와 Loader 런타임은 미검증 |
| **G5** | heavy SKIPPED / Q-007 STATIC SUSPECT | **실제 소비자 G5* 1 FAIL; 새로운 P1 원인 확인** | 두 번째 nested build 성공에도 configuration cache **재사용 실패**. owner-2 패치 후 재실행 전까지 **OPEN FAIL** |
| **G20** | NOT RUN | **NOT RUN** | G5 관련 미러 로직 수정이 G20 삭제/손상 복구를 만족하는지 별도 실제 TestKit 검증 필요 |
| **G6/G9/G13/G15/G17/G25/G28/G29 및 그 밖의 G** | 단계별 부분 근거 또는 NOT RUN | **이번 실행으로 신규 완전 수락 증거 없음** | 실제 IDE/MC, 지정 Gradle/Loom 조합, Kotlin·Classpath·클라이언트/서버·캐시 검증은 독립 수행 |

**보고 규칙:** `FabricAmperConsumerTest.xml` 하나에 이전 `G1*`의 2 PASS, `G3*`의 1 PASS, 최신 `G5*`의 1 FAIL이 **동시에 기록된 것은 아니다**. 각 결과는 **서로 다른 필터 실행 결과**이며 가장 최근 XML은 G5 **1 FAIL**이다. 그러므로 `4 tests / 3 PASS / 1 FAIL`을 단일 JUnit suite나 동일 빌드 실행 결과로 표기하지 않는다. 과거 **109 tests / 101 PASS / 8 SKIPPED**도 이전 자체 `clean build`의 별도 이력이다.

**§11 작성 범위:** 기존 §1~§10은 원문 그대로, **`QA_REPORT.md`만 수정**. QA 작업자가 추가로 실행한 Gradle/테스트 **0회**, 생산·테스트·빌드·기타 문서 변경 **0건**. **최신 출시 판정: 소비자 `help/tasks`·기본 배포 JAR 검증에 실질적 진전이 있으나 G5 P1이 열려 있고 다수 G 항목이 미검증이므로 출시 완료 아님.**

---

## 12. G5 재실행: Amper 미러 부재 추적 이후 Loom 콜드 캐시 원인으로 이동 (2026-11-11)

> **이전 기록 보존:** §1~§11 **478행은 당시 실제 이력으로 변경하지 않았다.** §11의 `G5-P1-CC-FIRST-MIRROR` 실패는 **owner-2 패치 이전**의 첫 실측이다. 이 절은 해당 패치 **이후 Prime이 실행한 별도의 실제 opt-in G5 테스트**를 기록한다. 테스트 수행 환경은 **Gradle 9.7.1/JDK25**, 선택 실행 옵션은 **`-PfabricAmperTestkitFull=true`**. QA 작업자 6은 빌드·테스트를 수행하지 않고 보고서만 수정했다.

### 12.1 패치 전·후 실패 원인의 구분

| 실제 필터 실행 시점 | 관찰된 Configuration Cache MISS 사유 | 검증 상태 및 의미 |
| --- | --- | --- |
| **1차 G5 실측 (§11)** | `file system entry '.gradle\\fabric-amper\\loom\\<hash>\\fabric.mod.json' has been created` | **Amper 자체 미러 생성 전 존재 여부 조회**가 첫 구성에서 부재를 입력으로 추적해 두 번째 실행 재사용을 차단한 **P1**. 당시 G5 테스트 **1 FAIL** |
| **owner-2 수정** | `src/main/kotlin/dev/fabricamper/loom/LoomMetadataMirror.kt:67-87`: 미러 대상의 `Files.exists/isRegularFile` 선행 조회를 없애고 임시 파일 생성 → `Files.move(..., ATOMIC_MOVE)` (지원 불가 시 교체 이동) → 기록 후 `ProviderFactory.fileContents`로 실제 파일 내용 확인 | **PATCHED / 부분 증거**. 첫 번째 Amper 미러 생성 오류는 아래 재실행에서 **재현되지 않음**. 그러나 전체 G5 통과, G20 손상 복구, 병렬 구성까지 검증됐다는 뜻은 아님 |
| **패치 후 G5 재실행 (§12, 최신)** | `file system entry '...\\build\\fabric-amper-testkit-cache\\pid-<pid>\\testkit-home\\caches\\fabric-loom\\mojang_versions_manifest.json' has been created` | **Fabric Loom 자체의 콜드 TestKit 캐시 생성**을 설정 입력으로 추적한 다음 실행 캐시 MISS. **소비자 nested `build`는 성공했으나 G5는 여전히 1 FAIL / 0 SKIPPED**. 이전 Amper 미러 경로와 **서로 다른 실패 원인** |

**최신 JUnit 근거:** `/vbc/Fabric Amper/build/test-results/test/TEST-dev.fabricamper.integration.FabricAmperConsumerTest.xml:1-5`의 `tests="1" failures="1" errors="0" skipped="0"`. `FabricAmperConsumerTest.kt:107-123` 중 **113행**에서 두 번째 `build --configuration-cache` 결과에 `Reusing configuration cache`가 없다는 assertion이 실패했다. 현재 XML의 메시지는 **`mojang_versions_manifest.json`이 새로 생성됐음**을 가리킨다. 중첩 빌드 출력은 **`BUILD SUCCESSFUL in 1s`, `Configuration cache entry stored`**다. 여기서 성공은 **빌드 성공**, 실패는 **Configuration Cache HIT 미달**이다.

### 12.2 원인 귀속 및 다음 검증 계약

- **기존 Amper P1 (owner-2):** `LoomMetadataMirror.writeAtomically`의 최초 미러 부재 선행 조회 문제는 **패치 반영 및 재실행 시 해당 이유 미발생**으로 분류한다. 현재 실패 메시지에 Amper 미러가 없다는 사실을 **전체 설정 캐시 안정성 PASS**로 확대하지 않는다.
- **신규 콜드 Loom 캐시 조건 (owner-4 / TestKit fixture):** `testkit-home/caches/fabric-loom/mojang_versions_manifest.json`은 **Fabric Loom의 외부 manifest 캐시**다. 첫 측정 `build --configuration-cache` 전에 **같은 격리 소비자 fixture에서 `help`를 한 차례 워밍업 실행**해 Loom의 콜드 다운로드/캐시 생성만 먼저 마친 다음 측정한다. 제품 설정 캐시 위반을 무조건 통과시키지 않도록 **워밍업 단계와 측정 1~4회를 로그에서 분리**한다. 패치 소유권은 **owner-4 `src/test/kotlin/dev/fabricamper/integration/FabricAmperConsumerTest.kt` 등 integration fixture**이며 이 QA 작업에서는 수정하지 않는다.
- **엄격한 G5 수락:** 워밍업 `help` 성공 → 측정 첫 번째 `build --configuration-cache`에서 entry 저장 → **두 번째 `build --configuration-cache`에서 실제 `Reusing configuration cache`** → YAML 버전·의존성·Java 항목 수정 뒤 **세 번째 설정 캐시 무효화와 JSON 최신값** → **네 번째 재사용**을 모두 확인. `UP-TO-DATE`·태스크 `FROM-CACHE` 또는 `BUILD SUCCESSFUL`만으로 Configuration Cache 재사용을 인정하지 않는다.
- **G20은 별개:** 미러 **삭제·손상·재생성·원자성·설정 캐시 입력 무효화**까지 포함한 G20 테스트는 여전히 **NOT VERIFIED**다. 워밍업은 검증 대상의 오류를 무조건 숨기기 위한 방법이 아니며 콜드 외부 의존성 생성과 Amper 미러 변경을 별도 구분하기 위한 초기 조건이다.

### 12.3 최신 수락 대장

| 항목 | 이번 추가 근거 | 현재 판정 |
| --- | --- | --- |
| **G1/G2/G14 + G17a** | §11의 **독립 필터 G1* 2/2 PASS**; settings-only `help/tasks` 및 구성 단계 미러 경로 검사 | **해당 소비자 TestKit 부분 시나리오 PASS 유지**. IDE Sync·게임·전체 조건과 구별 |
| **G3/G21** | §11의 **독립 필터 G3* 1/1 PASS**; 실제 소비자 `build`/루트 `fabric.mod.json` 1개/생성-미러 바이트 일치/Java 클래스 포함 | **해당 소비자 JAR 부분 시나리오 PASS 유지**. 반복 클린 환경·Loader 실행은 별도 |
| **G5** | owner-2 Amper mirror 패치 후 재실행에서 **Fabric Loom 콜드 manifest 캐시 생성**이 두 번째 설정 캐시 재사용을 방해 | **최신 XML 1 FAIL · OPEN**. **owner-4 워밍업 수정 요청, 패치 후 새 G5 XML PASS 전에는 승격 금지** |
| **G20** | 본 재실행에서 미러 삭제·손상 복구 테스트 수행 보고 없음 | **NOT VERIFIED** |
| **그 밖의 G** | 추가 실제 실행 근거 없음 | §9/§11의 부분 증거 또는 NOT RUN 상태 유지 |

**중요한 시점 주의:** `FabricAmperConsumerTest.xml`의 최신 파일은 **G5 필터 한 건**의 결과다. 이전 **G1* 2 PASS**, **G3* 1 PASS**, 최신 **G5* 1 FAIL**을 단일 동시 JUnit suite로 합산하지 않는다. §8의 **96 unit + 5 quick PASS / 8 heavy SKIPPED** 역시 별도 과거 전체 빌드 증거다.

**최신 출시 판정:** G1/G17a 및 G3/G21의 실제 소비자 시나리오에서 성공 근거가 추가됐지만, **G5는 여전히 실패**, G20·IDE·Minecraft·복수 Gradle/Loom 조합은 미검증이므로 **출시 수락 완료 아님**. **기존 §1~§11 보존, `QA_REPORT.md` 이외 수정 0건, QA 작업자 신규 Gradle/테스트 실행 0회.**

---

## 13. 실제 opt-in G20 미러 복구 PASS 및 G5 워밍업 후 정상 상태 PASS (2026-11-11)

> **시점·이력 구분:** 기존 **§1~§12 총 515행을 원문 그대로 보존**한다. §11~§12에서 보고한 **G5 설정 캐시 MISS 2차례**(먼저 Amper 미러 생성 전 부재 조회, 다음으로 Loom의 콜드 `mojang_versions_manifest.json` 생성)는 당시 **실제 실패 기록**이며 삭제·정정하지 않는다. 아래 **G5 PASS는 owner-2 미러 패치 및 owner-4의 콜드 Loom 캐시 `help` 워밍업 도입 이후 별도 필터 실행한 결과**다. Prime이 실행한 로그·XML을 기준으로 작성하며 QA 작업자는 Gradle/테스트/빌드를 다시 수행하지 않았다.

### 13.1 독립 필터 실행 및 실측 결과

| 실행 시나리오 | Gradle/JDK 및 명령 조건 | 실행/테스트 결과 | 실제로 확인한 내용 |
| --- | --- | --- | --- |
| **G20 — 손실·손상된 콘텐츠 주소 미러 복구** | **Gradle 9.7.1 / JDK 25**, 소비자 TestKit `test --tests ...G20* -PfabricAmperTestkitFull=true` (Prime 실행) | **EXIT 0**, `BUILD SUCCESSFUL in 41s`. 필터 XML **`tests="1" skipped="0" failures="0" errors="0"`**, testcase `G20 missing or corrupted content-addressed mirror is restored with configuration cache` **1 PASS** | 실제 `.gradle/fabric-amper/loom/<hash>/fabric.mod.json` **삭제 후 재생성** 및 **오염된 JSON 바이트를 기록한 후 원본 생성 JSON과 동일한 바이트로 복구**. 후속 `clean`·`help` 호출 및 미러 무결성 재확인 |
| **G5 — 워밍업 후 엄격한 Configuration Cache 시퀀스** | 같은 **Gradle 9.7.1 / JDK 25**, 별도 선택 테스트 `...G5*`, `-PfabricAmperTestkitFull=true`; 측정 전 같은 TestKit fixture에서 **`help` 워밍업** | **EXIT 0**, 실행 시간 **1분 9초**, 필터 XML **`tests="1" skipped="0" failures="0" errors="0"`**, **1 PASS** | 측정 **1차 `build --configuration-cache` 성공·저장**, **2차 실제 재사용(HIT)**, YAML 변경 후 **3차 재사용 안 함(MISS) 및 변경된 JSON version 반영**, **4차 다시 재사용(HIT)**. `FabricAmperConsumerTest.kt:107-128`의 원래 엄격한 HIT/MISS assertions 보존 |

**검증 세부:** G20 테스트 소스 `src/test/kotlin/dev/fabricamper/integration/FabricAmperConsumerTest.kt:193-209`에서 `generateFabricModJson --configuration-cache`를 실행한 뒤 `Files.delete(mirror)` 및 재실행, 이어 `Files.writeString`으로 손상된 JSON을 덮어쓴 후 재실행한다. 두 경우 모두 `assertArrayEquals(generated, mirror.readBytes())`로 복구 확인한다. 다음으로 `clean`과 `help`를 실행하고 다시 생성 메타데이터와 같은 바이트의 미러가 존재하는지 확인한다. **G20의 재생성·복원 시나리오가 실제 PASS**했다는 뜻이지, 병렬 구성·IntelliJ Sync를 실제 실행했다는 뜻은 아니다.

**G5 검증 세부:** owner-4가 `FabricAmperConsumerTest.kt`의 측정 전 단계에 **별도 `f.run(root, "help")` 워밍업**을 추가했다. 측정된 네 `build --configuration-cache` 중 **2·4회는 `Reusing configuration cache`를 실제 확인**하고, **3회는 YAML 버전/의존성 변경 뒤 캐시 미재사용과 최신 `fabric.mod.json` version을 확인**하는 원래 assertion을 유지했다. `BUILD SUCCESSFUL`이나 task `UP-TO-DATE`만 보고 캐시 HIT라고 간주하지 않는다. **수정된 안정 상태(steady-state) 경로의 G5 시나리오 PASS**다.

### 13.2 콜드 캐시와 미검증 범위

- **Amper 미러 결함과 Loom 콜드 캐시 구분:** 최초 G5 실패였던 `.gradle/fabric-amper/loom/<hash>/fabric.mod.json has been created`는 owner-2의 미러 선행 `Files.exists` 조회 제거 패치 후 재현되지 않았다. 이어 확인된 `testkit-home/caches/fabric-loom/mojang_versions_manifest.json has been created`는 **Fabric Loom 자체의 첫 실행 캐시 생성** 문제였고, 이번 G5 시나리오는 사전 `help`로 이 외부 콜드 부트스트랩을 측정 범위 밖에서 마친 뒤 설정 캐시 HIT/MISS를 확인했다. 따라서 이 성공을 **완전히 차가운 신규 TestKit 홈에서 첫 실행부터 CC 재사용이 보장된다**는 증거로 해석하면 안 된다.
- **24시간 만료 관련 불확실성:** Loom의 버전 manifest 캐시가 **24시간 후 만료·재다운로드되어 CC 무효화될 가능성**은 *점검할 가설*일 뿐이다. **24시간 경과 후 재실행·TTL 만료 시 재현 시험은 NOT RUN**이며, 이 가설을 확인된 버그나 해결된 문제로 표시하지 않는다.
- **G20 전체 운영 환경 경계:** 현 **G20 opt-in 1 PASS**는 실제 미러 삭제·오염 복구 및 `clean/help` 재구성을 검증했다. `specs.md`가 함께 요구하는 **IDE Sync·병렬 구성·다른 Gradle/Loom 조합의 원자성·동시성까지 모두 검증한 것은 아니다**.
- **필터별 XML 기록 구분:** G20 선택 실행의 **1 PASS**, G5 별도 선택 실행의 **1 PASS**, §11의 G1/G17a 선택 실행 **2 PASS**, G3/G21 선택 실행 **1 PASS**는 **각각 다른 Gradle/JUnit 실행**이다. `build/test-results/test/TEST-dev.fabricamper.integration.FabricAmperConsumerTest.xml`은 필터 실행마다 덮어써질 수 있으므로 **동시에 기록된 5개 테스트/5 PASS인 것처럼 단일 suite 수치를 만들지 않는다**. §8의 과거 **unit 96 + quick 5 PASS / heavy 8 SKIPPED**도 별도 실행 기록으로 남겨 둔다.

### 13.3 G 수락 대장 최신 상태

| G 항목 | 실측 근거 | 최신 판정 / 추가 게이트 |
| --- | --- | --- |
| **G1/G2/G14 및 G17a** | §11 독립 필터 **`G1*` 2/2 PASS**, 실제 settings-only `help/tasks`와 구성 미러 검사 | **해당 TestKit 시나리오 PASS 유지**; G17a의 별도 IntelliJ Sync는 아직 **NOT RUN** |
| **G3/G21** | §11 독립 필터 **`G3*` 1/1 PASS**, 소비자 JAR 루트 JSON 1개·미러/생성 바이트 동일·컴파일된 Java 클래스 | **해당 TestKit 시나리오 PASS 유지**; 광범위한 반복 클린 빌드·환경 변형은 별도 |
| **G5** | owner-4의 `help` 워밍업 이후 `G5*` **1/1 PASS**, 1차 저장→2차 HIT→3차 YAML 수정 MISS→4차 HIT | **워밍업된 안정 상태 G5 TestKit PASS**. 완전 콜드 환경 첫 실행과 추정 TTL 24시간 이후 **NOT VERIFIED** |
| **G20** | `G20*` **1/1 PASS**, 미러 삭제·손상 바이트 재생성/복원 및 `clean/help` | **해당 실제 소비자 복구 TestKit PASS**. 병렬 구성·IDE Sync·다른 환경 조합은 **NOT VERIFIED** |
| **G6/G9/G13/G15/G17/G25/G28/G29 등** | 이번 두 실행에서 해당 테스트 결과 없음 | **NOT RUN / 기존 부분 근거 유지**. IntelliJ·Minecraft 런타임·지정 버전 매트릭스 수락은 계속 별도 |

**최신 QA 판정:** **G5의 이전 실패는 이후 별도 실행에서 steady-state PASS로 진전했고, G20의 실제 복구 TestKit도 PASS**했다. 다만 이는 **선택 실행·명시한 범위의 성공**이며, Loom 콜드 캐시 만료/최초 실행 재사용, IDE/MC/병렬 구성·다중 버전의 G 전체 게이트가 자동으로 승인되는 것은 아니다. **출시 완전 수락 선언은 보류**한다.

**§13 변경 범위 및 확인:** `/vbc/Fabric Amper/QA_REPORT.md`만 append, 기존 §1~§12 원문 보존. QA 작업자가 실행한 Gradle/테스트/빌드 **0회**, 생산 소스·테스트·빌드 파일 수정 **0건**.

---

## 14. 실제 opt-in G4/G24 및 G7 소비자 수락 시나리오 추가 PASS (2026-11-11)

> **증거와 이력의 경계:** 기존 **§1~§13의 553행은 원문 그대로 보존**한다. 이 절은 Prime의 **Gradle 9.7.1/JDK 25** 실제 소비자 TestKit 후속 **필터별 개별 실행** 근거를 추가한다. 모든 신규 테스트는 `-PfabricAmperTestkitFull=true`로 선택 실행되었으며, QA 작업자 6은 Gradle·빌드·테스트를 별도로 실행하지 않았다. 기존 G1/G17a 2/2, G3/G21 1/1, 워밍업한 G5 1/1, G20 1/1 PASS는 §11·§13의 **독립 과거 실행 결과**로 보존한다.

### 14.1 최신 실행별 XML 및 실제 확인 대상

| 필터 / 테스트 | Prime 보고와 JUnit XML 결과 | 실제 소비자 확인 내용 | 상태·명시적 한계 |
| --- | --- | --- | --- |
| **`FabricAmperConsumerTest.G4*` + `...G24*`** | **EXIT 0, `BUILD SUCCESSFUL in 49s`; `tests="2" failures="0" skipped="0"` — 2/2 PASS**, 실 소비자 TestKit | **G4:** YAML의 `mod.id`·`mod.version`·`archivesName` 변경 후 새 `fabric.mod.json`의 ID/버전, 새 이름의 소비자 모드 JAR, root JSON 단일성, mirror/생성 JSON 동일성을 검증. **G24:** Mixin JSON·`classTweaker v1 official`·유효 PNG 아이콘이 미러 및 소비자 JAR에 존재하고 중복 없음을 확인; 원본 Mixin 리소스 수정 후 새로운 콘텐츠 주소 snapshot 경로 및 변경 바이트를 검사 | **해당 G4/G24 실제 소비자 시나리오 PASS**. 그 밖의 소비자 의존성/Loader의 Mixin·Class Tweaker **게임 런타임 적용**, IntelliJ Sync 및 모든 캐시 조합은 이번 실행에서 검증하지 않음 |
| **`FabricAmperConsumerTest.G7*`** | **EXIT 0, `BUILD SUCCESSFUL in 2m 4s`; `tests="1" failures="0" skipped="0" errors="0"` — 1/1 PASS**. 현재 확인한 `build/test-results/test/TEST-dev.fabricamper.integration.FabricAmperConsumerTest.xml:1-4`의 최신 XML도 해당 G7 테스트 1 PASS | **Java-only → Kotlin 활성화 → Java-only** 설정 전환: Java-only에서 `compileKotlin` 없음·Java 진입점 클래스 존재, Kotlin 활성화 후 `compileKotlin` 존재·Kotlin `adapter` 메타데이터 반영·**소비자 배포 JAR에 컴파일된 `org/example/KotlinEntry.class` 존재**, Kotlin 다시 끈 후 `compileKotlin` 없음. 외부 `build.gradle(.kts)` 없는 설정-only fixture 유지 | **해당 G7 Java/Kotlin 조건부 KGP·FLK 어댑터/TestKit 시나리오 PASS**. 모든 Kotlin/FLK 버전 조합, 사용자 실제 게임 환경에서 어댑터 로딩 및 클래스패스·런타임 호환성까지 완료됐다는 뜻은 아님 |

**검증과 소스 대응:** `src/test/kotlin/dev/fabricamper/integration/FabricAmperConsumerTest.kt:85-104`는 G4의 YAML 변경 전후 ID·version·archivesName 및 JAR/미러를 검사한다. 같은 파일 **211–244행**은 G24의 Mixin·Class Tweaker·아이콘 snapshot/JAR, root `fabric.mod.json` 1개 및 리소스 수정 뒤 내용 주소 snapshot 갱신을 검사한다. **130–164행**은 G7의 전환 전후 task 존재와 Kotlin 출력 클래스·메타데이터 adapter를 검사한다. 이 확인은 **소비자 모드 배포 JAR의 `.class` 검증**이며 Fabric Amper **플러그인 자체**의 배포 JAR을 뜻하지 않는다.

### 14.2 성공 범위별 수락 대장 최신화

| 수락 축 | 새로 확보된 실측 근거 | 아직 독립적으로 확인할 영역 |
| --- | --- | --- |
| **G4** | 실제 settings-only 소비자에서 **ID/version/archive 변경 → JSON·JAR 갱신**, 필터 PASS | 다양한 연속 편집·IDE 재동기화·설정 캐시 조합은 별도 |
| **G24** | 실제 소비자 **Mixin/Class Tweaker/PNG mirror snapshot 및 JAR 단일 포함**, 원본 리소스 수정에 따른 **새 해시 snapshot** 확인, 필터 PASS | 실제 Minecraft 로딩·Mixin 적용·Class Tweaker 멤버 유효성, 별도 IDE Sync/CC 전체 행렬 미검증 |
| **G7** | 실제 Java-only → Kotlin on → Java-only 전환, **KGP/FLK 조건부 동작 및 Kotlin 클래스 컴파일·JAR 포함**, 필터 PASS | MC Loader 런타임 Kotlin adapter/FLK 동작, 추가 KGP 버전과 콜드 다운로드 비용(**G29**) 미검증 |
| **기존 G1/G2/G14/G17a·G3/G21** | §11의 **`G1*` 2/2 PASS**, **`G3*` 1/1 PASS**를 **개별 필터 결과 그대로 유지** | G17a 실제 IntelliJ Sync, G21 완전한 결정성·기타 환경 추가 검증 |
| **기존 G5·G20** | §13의 워밍업 후 **`G5*` 1/1 PASS** 및 삭제/손상 미러 복구 **`G20*` 1/1 PASS**를 **개별 필터 결과 그대로 유지** | 완전 콜드 초기 Loom 캐시·추정 24시간 만료, G20 병렬 구성·IDE Sync는 **NOT VERIFIED** |
| **G6/G9/G13/G15/G17/G25/G28/G29 등 잔여 명세** | 이번 G4/G24/G7 필터로 신규 전체 수락 근거 없음 | 실제 IntelliJ/MC 클라이언트·서버, 지정 Gradle/Loom 조합, 레거시 remap, cold-cache 비용 등 **NOT RUN** |

### 14.3 필터 XML 집계 주의 및 출하 판정

`build/test-results/test/TEST-dev.fabricamper.integration.FabricAmperConsumerTest.xml`은 **필터별 실행 시 동일 경로에 다시 생성**되며, 읽기 시점의 최신 파일은 `G7*` **`tests="1" failures="0" skipped="0"`**이다. Prime이 보고한 `G4*+G24*` **2 PASS**와 최신 `G7*` **1 PASS**는 **서로 다른 Gradle 실행**이다. 기존 `G1*` 2 PASS·`G3*` 1 PASS·`G5*` 1 PASS·`G20*` 1 PASS도 동일 이유로 **하나의 XML suite에 합쳐 기재하지 않는다**. §8의 과거 96 unit + 5 quick PASS, 8 heavy SKIPPED는 opt-in 이전 전체 테스트 실행의 **역사적 기준**이다.

**최신 QA 판정:** 실제 소비자의 **G4/G24(2/2)와 G7(1/1) 필터 PASS**가 추가돼 YAML 변경 반영, 리소스 snapshot/JAR, Java/Kotlin 조건부 컴파일의 신뢰도가 높아졌다. 그러나 **G1~G30 전 항목의 종합 PASS, IDE/MC 실행 검증 완료, 출시 승인으로 확대하지 않는다.** 기존 실패 이력과 별도 검증 게이트는 계속 유효하다.

**§14 변경 범위:** `QA_REPORT.md`에만 append. **§1~§13 원문 수정 0건**, 이 QA 작업자의 **Gradle/테스트/빌드 실행 0회**, 생산 소스·테스트·빌드 파일 수정 **0건**.

---

## 15. 포스트 패치 클린 빌드 회귀 PASS·Extended 소비자 opt-in 5 PASS·남은 수락 공백 (2026-11-11)

> **이력 불변 원칙:** 앞선 **§1~§14 총 587행을 원문 그대로 보존**한다. §1의 초기 컴파일 실패, §8의 이전 96 unit + 5 quick PASS / 8 heavy SKIPPED, §10의 실제 소비자 P0·하네스 실패, §11~§12의 G5 CC 실패, §13~§14의 선별 opt-in PASS는 **각 시점의 서로 다른 결과**다. 아래는 **Prime이 새로 수행한 Gradle 9.7.1/JDK 25** 검증 결과이며, QA 작업자 6은 빌드·테스트를 실행하지 않았다. 이번 §15는 **전체 G1~G30 수락이나 출시 완료 선언이 아니다.**

### 15.1 시간순 실제 실행 이력 — 실패 수·선별 실행·클린 재실행 구분

| 순서 / 실행 | 실제 결과 | 해석 및 범위 |
| --- | --- | --- |
| **① 첫 번째 post-patch `clean build`** | **EXIT 1**. **총 148 테스트 = 133 PASS + 1 FAIL + 14 SKIPPED**. `compileKotlin`, `compileTestKotlin`, `pluginDescriptors`, `validatePlugins` **PASS** | **유일한 실패**는 `dev.fabricamper.unit.VersionSelectorAndSiblingPredicateUnitTest`의 **97행**에서 나타난 테스트 fixture 오류. `{operator: >=}`의 **따옴표 없는 `>=`는 YAML 문법 위반**이므로 파서의 **FA002가 올바른 반응**인데, 테스트는 **FA003**을 기대했다. 이 결과를 **147 PASS**로 계산하면 잘못이다. |
| **② owner-5 fixture-only 보정** | 테스트 입력을 YAML 유효 문자열인 `{operator: ">="}`로 변경. **제품 코드·진단 규칙의 완화 없음** | 문법이 유효하지만 값의 타입이 잘못된 mapping을 **FA003**으로 검사해야 한다는 **원래 의미와 엄격성 유지**. 소스 `src/test/kotlin/dev/fabricamper/unit/VersionSelectorAndSiblingPredicateUnitTest.kt:89-99`에서 수정된 fixture를 확인했다. |
| **③ 단일 단위 테스트 선별 재실행** | `test --tests dev.fabricamper.unit.VersionSelectorAndSiblingPredicateUnitTest` **EXIT 0**, **BUILD SUCCESSFUL in 19s**, JUnit XML **9 tests / 9 PASS / 0 FAIL / 0 SKIPPED** | 잘못된 fixture를 포함한 해당 **9개 단위 테스트 모두 PASS**. 이는 전체 빌드 성공의 선행 증거이고, 9개를 나중의 전체 148개에 다시 더하지 않는다. |
| **④ Extended 실제 소비자 opt-in 별도 실행** | `-PfabricAmperTestkitFull=true`, `FabricAmperExtendedConsumerTest` **EXIT 0**, **BUILD SUCCESSFUL in 1m 45s**, 선택 실행 XML **5 tests / 5 PASS / 0 FAIL / 0 SKIPPED** | 실제 설정만 있는 독립 소비자 fixture에서 **G10 / G18(구성 선언만) / G22 / G23 / G30** 시나리오를 검사. 이 opt-in XML은 **후속 기본 clean build에 의해 덮어쓰일 수 있어** 역사적 선별 PASS로 보관한다. |
| **⑤ 두 번째 전체 `clean build`** | **EXIT 0**, **BUILD SUCCESSFUL in 35s**, `validatePlugins` **PASS**. **최종 18개 JUnit XML suite 합계: 148 tests = 134 PASS + 0 FAIL + 0 ERROR + 14 SKIPPED** | **현재 기본 전체 회귀 빌드 PASS**. 첫 번째 빌드의 단일 fixture FAIL은 해소되었다. 다만 **14 SKIPPED는 PASS가 아님**: 환경 게이트가 없는 기본 전체 빌드에서 heavy TestKit이 제외된 것. |

**현재 XML 대조:** `build/test-results/test/TEST-dev.fabricamper.unit.VersionSelectorAndSiblingPredicateUnitTest.xml:1-12`는 **9/9 PASS**; `TEST-dev.fabricamper.integration.FabricAmperCompatibilityConsumerTest.xml:1-7`는 **2 tests / 1 PASS / 1 SKIPPED / 0 FAIL**; 현재 `TEST-dev.fabricamper.integration.FabricAmperExtendedConsumerTest.xml:1-12`는 **5 tests / 5 SKIPPED**; 현재 `TEST-dev.fabricamper.integration.FabricAmperConsumerTest.xml:1-16`는 **8 tests / 8 SKIPPED**다. 즉 마지막 두 XML의 `skipped`는 **최신 기본 clean build 시점**이고, **④의 실제 Extended opt-in 5 PASS와 모순되지 않는다**. Compatibility의 G9 별도 1 SKIPPED을 합치면 이 세 class의 5+8+1이 **전체 14 SKIPPED**이다.

### 15.2 Extended opt-in 5개: 개별 수락 근거와 주장하지 않는 것

| 해당 수락 / 소스 검사 | 새 실측 PASS의 정확한 내용 | 여전히 미검증인 경계 |
| --- | --- | --- |
| **G10**, `FabricAmperExtendedConsumerTest.kt:35-95` | Maven Central의 `commons-lang3:3.17.0`을 `:include`로 소비자 **실제 `build`**. 루트 JSON 1개, Loader metadata의 `jars[].file`이 지목하는 **`META-INF/jars/` 아래 중첩 JAR 1개**, 실제 ZIP 내부 `StringUtils.class`, 소비자 `IncludedLibraryConsumer.class`를 검증 | **중첩 JAR의 실제 Fabric Loader 런타임 로딩**, Modrinth 분리 경로, `compile-only`/`runtime-only`/`exported` 종합 행렬은 **NOT VERIFIED** |
| **G18**, `:97-143` | `splitEnvironmentSourceSets=true`, client `all`/client `compile-only`가 **`clientImplementation`/`clientCompileOnly`에 선언**되고 common `implementation`/`compileOnly`에 직접 선언되지 않았음을 `help` + init script로 확인 | **구성 선언(config-only) PASS**. client/common **실제 Java/Kotlin 컴파일 가시성·소스셋 격리**, 게임 client/server 동작은 **NOT VERIFIED** |
| **G22**, `:145-176` | YAML의 `group`·`version`·`archivesName`를 **`properties`에서 확인**, 실제 `jar` 및 `sourcesJar` 생성, 이름 및 소스 Java 파일 포함, Loader JSON의 `version` 일치·빌드 전용 `group/archivesName` 미유출 확인 | 사용자별 다양한 문자열·IDE 모델·게임 실행 등 추가 조합은 별도 |
| **G23**, `:178-205` | YAML `test-dependencies: org.junit.jupiter:junit-jupiter:6.0.0` 소비자에서 **실제 `test --tests org.example.JupiterSmokeTest` 실행**, `:test` SUCCESS, 별도 **JUnit XML 1 test / 0 fail / 0 skip** 검사. JUnit Platform 구동의 실행 증거 | 모든 JUnit 조합 및 사설 저장소 해석, 다른 테스트 엔진 호환성까지 포괄하지 않음 |
| **G30**, `:207-232` | `mod.yml`만 있는 소비자에서 `help` 워밍업 후 CC **2차 HIT**, `mod.yml → mod.yaml` 교체와 버전 변경 후 **3차 MISS + 미러 JSON 최신 version**, **4차 HIT** 검증 | **이 별칭 교체·CC 시나리오 PASS**. 모든 잘못된 UTF-8, 다중/빈 문서, scalar alias/tag, 기존 generated ZIP 중복 경로 및 원본 보존의 **전체 G30 행렬**을 이 한 테스트만으로 증명하지 않음 |

**독립 실행 보존:** §11·§13·§14의 기존 **`G1*` 2 PASS**, **`G3*` 1 PASS**, **`G4*+G24*` 2 PASS**, **`G7*` 1 PASS**, **워밍업한 `G5*` 1 PASS**, **`G20*` 1 PASS**를 그대로 유지한다. 이들 선별 TestKit과 **④ Extended 5 PASS**, **③ 단위 9 PASS**, **⑤ 기본 전체 134 PASS**는 **서로 다른 Gradle 실행**이며 **모두 합쳐 한 번의 JUnit suite 수치로 발표하지 않는다**.

### 15.3 G19·G9 게이트와 미충족 수락 항목

| 대상 | 현재 실측·한계 | 추가 수락 게이트 |
| --- | --- | --- |
| **G19 / FA010** | 기본 전체 빌드의 `FabricAmperCompatibilityConsumerTest`는 **2 tests, 1 PASS, 1 SKIPPED**. 실행된 G19 테스트(`FabricAmperCompatibilityConsumerTest.kt:22-53`)는 init script로 **가짜 `loom` extension 이름 충돌**을 주입하고 **FA010 + 명시적 `Loom plugin conflict`**를 검사해 PASS | 실제로 **타 버전의 Loom/KGP 플러그인이 먼저 적용된 소비자**에서 plugin resolution/classloader 충돌 진단까지 확인한 것은 **아님** |
| **G9 레거시 remap** | `FabricAmperCompatibilityConsumerTest.kt:55-89`의 **1.21.11 `remapJar` 시험은 SKIPPED**. `FABRIC_AMPER_TESTKIT_FULL=true`와 별도의 `FABRIC_AMPER_TESTKIT_LEGACY=true`, **JDK 21 설치**가 요구됨 | 격리된 **Java 21 toolchain + 레거시 Loom/매핑 다운로드**에서 `remapJar`·컴파일 클래스·루트 JSON 확인 후에만 **G9 지원 PASS/공개** |
| **G6/G28** | IntelliJ **최초 import·수동 Sync·YAML 변경 후 자동 Sync** 실제 증거 없음 | 단일 `settings.gradle.kts` 소비자에서 의존성/Java/Kotlin source roots/run configurations 및 자동 Sync UX를 별개 기록 |
| **G13/G25** | **Gradle 9.7.1/JDK25 기본 실측은 존재**하나, 명세의 **Gradle 9.7.0/Loom 1.18.4, Gradle 9.9.1/Loom 1.19.4**, 또 **Loom 1.19.4 + Gradle 9.7.0/9.8.0/9.9.1** 조합의 구별 실측 없음 | 각 버전 조합별 plugin metadata variant resolution / KGP 호환성 / `.module` 제약 **독립 테스트 및 PASS/FAIL 명시** |
| **G15/G17** | settings-only `help`에서 미러 연결(G17a) 및 Kotlin 소비자 컴파일(G7)은 검사됐지만 **실제 격리 `runClient`/`runServer`**나 **interface injection 적용된 compile·IDE·runtime** 검증은 없음 | Minecraft Loader가 생성 메타데이터를 인정하는지, 주입 인터페이스가 실제 게임/IDE에서 적용되는지 별도 실행 |
| **G16/G27** | G16 **잘못된 헤더/client-only 위치의 조기 진단**은 빠른 테스트 소스로 존재, G27 일부 side scope의 정적·구성 확인은 있음. 그러나 G16 **유효·잘못된 멤버에 대한 실제 `validateAccessWidener`**와 G27 **`environment:"*"` + client 전용 모드 의존성의 실제 전용 서버 부팅/Loader 요구**는 미실행 | 진단뿐 아니라 **유효/무효 Class Tweaker 멤버의 태스크 결과**, 서버/클라이언트 부팅과 Loader `depends` 실제 확인 |
| **G29** | Java-only ↔ Kotlin on/off(G7) 기능은 PASS지만 **외부 KGP가 캐시되지 않은 콜드 환경 전이 다운로드 비용**·지연 KGP opt-in 실험은 없음 | 새 격리 홈에서 원격 KGP artifact 전이 비용·설정 캐시/classloader 영향 계측 |
| **FA011 및 관련 FA 진단** | FA001–FA015 파서·구조·JAR 무결성 단위/빠른 테스트가 **⑤ 기본 회귀 빌드에서 통과한 범위**의 증거는 있으나, **외부 Maven/Modrinth 좌표가 실제로 없을 때의 FA011** 및 메시지의 정확한 미해결 좌표 실측은 별도 근거 없음 | FA011 누락 좌표 실제 해석 실패, FA010 외부 plugin 충돌, FA015 미러 참조 리소스 손상·IDE 구성의 진단을 독립 확보. **FA 전체 실소비자 진단 PASS로 포괄하지 않음** |

**주의: 다른 G 항목도 부분 완료 범위가 존재한다.** G10의 `include` 패키징 PASS는 실제 Loader 중첩 모드 로딩을 의미하지 않고, G18의 **configuration-only** PASS는 common/client 컴파일 격리 증명이 아니다. §13 G20의 미러 metadata 복구 PASS는 병렬 구성·IDE Sync까지 포괄하지 않으며, G5의 **사전 `help` 워밍업** PASS는 Loom **완전 콜드 초기 캐시**나 추정 **24시간 후 manifest 만료** 검증이 아니다. §14의 G7 마지막 Java-only 상태는 Kotlin task 제거까지 확인했지만 최종 Java-only 재빌드/JAR 결과는 별도다.

### 15.4 배포 플러그인 JAR 및 최신 출하 판정

| 산출물·검증 | Prime 최신 실제 증거 |
| --- | --- |
| **플러그인 JAR 경로** | `/vbc/Fabric Amper/build/libs/fabric-amper-0.1.0.jar` |
| **파일 크기** | **206,217 bytes** |
| **SHA-256** | **`14E278CDAACBB773B772A6796DC040DAFCCCB4D641F15D3D763431DE87E23220`** |
| **Gradle 플러그인 마커** | **`dev.fabric-amper.settings`**, **`dev.fabric-amper.project`** **둘 다 Prime이 JAR에서 확인** |
| **빌드/유효성 확인** | 두 번째 `clean build` **EXIT 0 / BUILD SUCCESSFUL in 35s**, `validatePlugins` **PASS**, 최종 **148 = 134 PASS + 14 SKIPPED + 0 FAIL/ERROR**, 18 XML suite 합계 |

**정확한 출하 판단:** **현재 플러그인 자체의 클린 빌드와 선택된 실제 소비자 TestKit 시나리오는 PASS**다. 특히 G10 중첩 라이브러리 JAR·G22 project/JAR/sourcesJar·G23 소비자 JUnit 실행·G30 alias 캐시·G19 충돌 경고의 신규 근거가 확보되었다. 그러나 **14개 기본 전체 테스트 SKIPPED**, **G9 JDK21 레거시**, **G6/G28 IDE**, **G13/G25 정확한 Gradle/Loom 매트릭스**, **G15/G17 실제 Minecraft**, **G16/G27 side/runtime**, **G29 cold KGP**, **FA011 실제 누락 아티팩트** 등 공개 수락 게이트가 남아 있다. **G1~G30 전수 PASS 또는 완전한 출시 승인이라고 기록하지 않는다.**

**§15 수정 범위:** `/vbc/Fabric Amper/QA_REPORT.md`에만 append; **§1~§14 역사적 증거 원문 보존**. QA 작업자 6의 Gradle 실행·테스트 실행·제품/테스트 코드 변경 **모두 0건**.

---

## 16. 공식 Loader 0.20.1 predicate parity · G9/G16/G18 · FA011 실제 소비자 수락 및 최신 165 테스트 클린 빌드 (2026-11-11)

> **역사 보존 및 실행 단위:** 기존 **§1~§15의 646행은 한 글자도 덮어쓰지 않고 유지**한다. 아래 결과는 Prime이 **Gradle 9.7.1/JDK 25**로 수행한 여러 **독립적 선별 실행**과 마지막 **기본 `clean build`**에 관한 신규 기록이다. G9 레거시 소비자 내부의 Java toolchain은 **JDK 21**이다. 과거 실패/스킵과 후속 PASS는 **시간순으로 모두 유효**하며, 필터별 JUnit XML은 나중 실행에서 덮어써질 수 있으므로 **선별 PASS를 마지막 20 XML suite에 합산하지 않는다**. QA 작업자 6은 새 Gradle/테스트/빌드를 실행하지 않았다.

### 16.1 공식 Fabric Loader 0.20.1 비교: 오류 발견 → parity 수정 → 11/11 PASS

| 단계 | 해당 선별 실행의 실제 결과 | 확인한 Loader 계약 및 후속 처리 |
| --- | --- | --- |
| **1차 parity** | `OfficialFabricLoaderPredicateParityUnitTest` **8 tests / 2 FAIL** | 공식 Loader가 `!=1.2.3`를 일반적인 부등호 연산이 아니라 **opaque exact version**으로 허용하는 동작, 잘못된 **OR arm** 처리의 차이 확인. 로컬 구현/기존 테스트 기대와 충돌 |
| **2차 parity** | **9 tests / 2 FAIL** | 공식 Loader는 단독 `x`와 `X`를 보편 wildcard가 아니라 **opaque exact**로 본다. `>=1.0 || <2.0`도 일반적인 논리 OR 구문으로 거부되는 것이 아니라 **공식 파서가 수용하는 AND 항목 시퀀스/opaque 항목**으로 처리하므로 로컬과 차이 확인 |
| **3차 parity** | **11 tests / 1 FAIL** | 공식 Loader는 비말미 wildcard 표기 `1.2.*.3`도 **opaque exact**로 수용했으나 로컬 로직에서 다르게 처리했던 경계 사례 확인 |
| **최종 parity** | **11 tests / 11 PASS / 0 FAIL / 0 SKIPPED**. 최신 `build/test-results/test/TEST-dev.fabricamper.unit.OfficialFabricLoaderPredicateParityUnitTest.xml:1-14`도 해당 11개 PASS 확인 | `net.fabricmc.loader.api.Version`와 `VersionPredicate.parse(...)`의 **실제 Fabric Loader 0.20.1 API를 참조**하는 단위 비교. exact/범위/`^`/`~`/wildcard/AND·OR/opaque/빌드 메타데이터/사전 릴리스와 부적절한 OR arm 등 선택된 11개 테스트 시나리오가 일치. **모든 외부 Fabric Loader 버전·런타임 조건을 보편적으로 증명한 것은 아님** |

**진단 엄격성 유지:** 공식 Loader가 받아들이는 **opaque exact 버전**과 **명백히 미완성인 `>=`·`<=` 조건**은 구별된다. 뒤의 미완성 연산자나 잘못된 OR 항목은 Loader도 거부하므로 Amper가 허용하지 않는다. 소스 근거는 `src/test/kotlin/dev/fabricamper/unit/OfficialFabricLoaderPredicateParityUnitTest.kt:23-39,87-113,141-190`다. 이 1차→4차 수정 이력은 **기존 실패를 삭제하지 않고** 마지막 parity PASS와 구별해서 남긴다.

### 16.2 최신 opt-in 소비자 TestKit: G16 · G18 · FA011 · G9 · G5

| 수락 대상 / 실행 | 실제 결과 및 핵심 증거 | 무엇까지 PASS이며 무엇이 아닌가 |
| --- | --- | --- |
| **G16 실제 Loom Class Tweaker 멤버 검증** | `FabricAmperExtendedConsumerTest.G16*` opt-in **EXIT 0, 1 test / 1 PASS**. `validateAccessWidener`가 **유효한 `classTweaker v1 official`의 실제 vanilla `Blocks` 클래스 접근 선언을 통과**시키고, **존재하지 않는 메서드 이름**의 별도 fixture는 **해당 이름을 출력하면서 예상대로 FAIL**하는지 검사. `FabricAmperExtendedConsumerTest.kt:194-232` | **유효/무효 멤버에 대한 실제 Loom 검사 작업 결과 PASS**. 테스트 내부의 의도된 invalid-fixture Gradle 실패는 **JUnit 테스트 FAIL이 아니다**. Minecraft 부팅 시 Class Tweaker 적용과 전체 멤버 문법 행렬은 별도 |
| **G18 분리된 client/common 컴파일 격리** | `FabricAmperExtendedConsumerTest.G18*` opt-in **EXIT 0, 2 tests / 2 PASS**. 기존 **clientImplementation/clientCompileOnly 선언과 common 선언 분리** 검사 외에, `src/client/java`의 실제 **`compileClientJava` SUCCESS + `ClientClasspathProbe.class` 생성**, 동일 client 전용 `commons-collections4` 사용을 시도하는 common `compileJava`는 **`package org.apache.commons.collections4.bag does not exist` 오류로 예상대로 실패**했는지 확인. `FabricAmperExtendedConsumerTest.kt:97-192` | §15의 **G18 config-only** 수준에서 **실제 Java 컴파일 격리까지 진전**. 의도된 common 컴파일 실패는 **부정 검증의 성공**이고 JUnit **2 PASS**와 충돌하지 않는다. 실제 전용 서버 Loader 런타임·다른 client dependency scope는 별도 |
| **FA011 실제 미존재 Maven 좌표** | `FabricAmperMissingDependencyConsumerTest` 첫 opt-in 실행은 **테스트 자체 EXIT 1**: 테스트가 `:compileJava` 태스크 결과를 기대했으나 실제 Loom은 **프로젝트 구성 시점 `:mainRuntimeDependencyResolve`를 먼저 해결하다 실패**. 당시 **제품 진단은 이미 정확했음**. owner-4가 **테스트 fixture 기대만 수정**한 후 **선별 재실행 EXIT 0, XML 1 PASS**. 소스 `FabricAmperMissingDependencyConsumerTest.kt:21-79` | 실제 결측 GAV **`dev.fabricamper.fixtures.nonexistent:fa011-missing-module-20261111:0.0.0`**를 해결할 수 없을 때 **`FabricAmper FA011 mod.yaml:32:5 dependencies[0]: Maven/Modrinth artifact not found: [해당 GAV]`** 및 Gradle 네이티브 **`Could not find [해당 GAV].`** 둘 다 보존. 구성 단계의 **`Could not resolve all files for configuration ':mainRuntimeDependencyResolve'`**, task graph 미생성 확인. **네트워크/TLS/오프라인 오류는 미존재 아티팩트 판정의 대체 증거가 아님** |
| **G9 레거시 Minecraft 1.21.11 remap** | 실제 **`C:\Program Files\Java\jdk-21`의 JDK 21.0.6** toolchain 사용, Gradle 자체는 JDK25. **`-PfabricAmperTestkitFull=true -PfabricAmperTestkitLegacy=true` 이중 opt-in**, `FabricAmperCompatibilityConsumerTest.G9*` **EXIT 0, XML 1 PASS**. 레거시 `remapJar` **TaskOutcome.SUCCESS**, 배포 `testkit-mod-1.0.0+1.21.11.jar`의 컴파일 `.class`와 ZIP 루트 `fabric.mod.json` **정확히 1개**. `FabricAmperCompatibilityConsumerTest.kt:55-89` | §15에서 **G9 SKIPPED**였던 당시 기본 빌드 기록은 유지; **새 레거시 실제 remap 배포 TestKit 시나리오 PASS**로 승격. **실제 Fabric Loader가 레거시 JAR을 클라이언트/서버에서 로딩한 E2E는 아직 NOT RUN** |
| **G5 FA011 FlowAction 도입 후 구성 캐시 회귀** | FA011 관련 **FlowAction 주입/변경 이후** 별도 `FabricAmperConsumerTest.G5*` 재실행 **EXIT 0, XML 1 PASS**. 측정 전 `help` 워밍업 유지, 이후 실제 **4회의 `build --configuration-cache`**: 첫 저장 → 두 번째 HIT → YAML 의존성/버전 변경 뒤 세 번째 MISS·JSON 갱신 → 네 번째 HIT | **FA011 변경 뒤에도 워밍업된 G5 steady-state 수명주기 테스트 PASS**. 완전 초기 외부 Loom manifest cache, 장시간 TTL 만료 또는 별도 TLS 조건에서 CC 안정성이 검증됐다는 뜻은 아님 |

**FA011 실패의 정확한 의미:** 첫 **JUnit FAIL**은 **제품 FA011 오류가 아니라 TestKit의 `compileJava` task 존재 전제 오류**였다. Loom은 설정 단계에서 `:mainRuntimeDependencyResolve` 실패 후 태스크 그래프를 만들지 못한다. owner-4는 자연스러운 **configuration failure**와 원본 네이티브 `Could not find`를 오히려 엄격하게 확인하도록 테스트를 바로잡았고, 후속 1 PASS가 이를 검증했다. `FabricAmperMissingDependencyConsumerTest.kt:43-78`은 **FA011 경로·라인·열·좌표**와 **손상되지 않은 YAML/Java 소스**까지 확인한다. **임의 TLS/Modrinth/장기 캐시/모든 저장소 오류 분류의 보편 PASS를 주장하지 않는다.**

**FA011 정확한 문자열(placeholder 없이):** `FabricAmper FA011 mod.yaml:32:5 dependencies[0]: Maven/Modrinth artifact not found: dev.fabricamper.fixtures.nonexistent:fa011-missing-module-20261111:0.0.0`. 해당 좌표를 포함한 네이티브 `Could not find dev.fabricamper.fixtures.nonexistent:fa011-missing-module-20261111:0.0.0.`도 함께 확인했다.

### 16.3 최신 전체 회귀 `clean build` 및 배포 파일

| 구분 | Prime 최종 실측 |
| --- | --- |
| **환경 및 명령** | **Gradle 9.7.1 / JDK 25**, `clean build` |
| **결과 / 소요 시간** | **EXIT 0, `BUILD SUCCESSFUL in 41s`**, `validatePlugins` **PASS** |
| **JUnit 전체 집계** | **20개 XML suite, 165 tests = 148 PASS + 17 SKIPPED + 0 FAIL + 0 ERROR** |
| **건너뜀의 의미** | 기본 빌드는 heavy opt-in을 실행하지 않음. 현재 `FabricAmperConsumerTest` **8 SKIPPED**, `FabricAmperExtendedConsumerTest` **7 SKIPPED**, `FabricAmperMissingDependencyConsumerTest` **1 SKIPPED**, `FabricAmperCompatibilityConsumerTest.G9` **1 SKIPPED** = **총 17**. 나머지 G19 빠른 collision 테스트는 PASS |
| **플러그인 배포 JAR** | `/vbc/Fabric Amper/build/libs/fabric-amper-0.1.0.jar`, **217,888 bytes** |
| **SHA-256** | **`25471A5703A45A8EA6F46C1D792055B6960089C853067A4883C9CF2585391985`** |
| **플러그인 marker** | **`dev.fabric-amper.settings`** 및 **`dev.fabric-amper.project`** 정확히 2개 확인 |

**최신 기본 빌드 XML 직접 대조:** `TEST-dev.fabricamper.unit.OfficialFabricLoaderPredicateParityUnitTest.xml:1-14`의 **11/11 PASS**, `TEST-dev.fabricamper.integration.FabricAmperExtendedConsumerTest.xml:1-24`의 **7/7 SKIPPED**, `TEST-dev.fabricamper.integration.FabricAmperMissingDependencyConsumerTest.xml:1-6`의 **1 SKIPPED**, `TEST-dev.fabricamper.integration.FabricAmperCompatibilityConsumerTest.xml:1-7`의 **G9 SKIPPED + G19 PASS**가 위 **165/148/17**의 범위와 일치한다. 이는 Prime의 **전체 XML suite 합계**이며, §16.2 **별도 opt-in PASS들을 165개에 더해 집계하지 않는다**.

### 16.4 최신 수락 경계: PASS로 확장하면 안 되는 항목

| 남은 항목 | 확인되지 않은 실제 수락 |
| --- | --- |
| **G6/G28** | IntelliJ **초기 프로젝트 인식, 소스 루트·Loom Run 구성, 수동/자동 Gradle Sync** 미실행 |
| **G13/G25** | 명세의 **Gradle 9.7.0/9.8.0/9.9.1**, Loom **1.18.4/1.19.4** 조합과 plugin `.module` variant 호환성 **미검증**. 지금 실측된 Gradle 9.7.1/JDK25의 성공을 다른 조합에 이식하지 않음 |
| **G15/G17** | Minecraft `runClient`/`runServer`에서 **실제 Loader metadata 인식** 및 interface injection의 **컴파일·IDE·게임 런타임 효과** 미실행 |
| **G27** | `mod.environment: "*"` + client 전용 모드 의존성의 **실제 클라이언트/전용 서버 부팅 및 Loader side `depends` 계약** 미검증. G18의 classpath 격리 성공만으로 충족 불가 |
| **G29** | 외부 KGP 미캐시 환경의 **초기 다운로드·전이 비용과 지연 적용 대안**, 실제 isolated project/classloader/configuration cache 비교 없음 |
| **G10/G20 잔여** | G10의 실제 **Fabric Loader jar-in-jar 런타임 로딩**, G20의 **IDE Sync·병렬 구성·참조 리소스 손상과 복구**는 여전히 미검증 |
| **FA011 잔여** | 정확한 **미존재 Maven GAV + 네이티브 오류 + 위치 진단**은 실제 PASS. 그러나 **TLS 연결 실패, Modrinth 저장소 거절, 오프라인/시간초과, CC 전후 모든 오류 분류**의 실소비자 행렬은 미실행 |

**최종 QA 판정:** 공식 Loader 0.20.1 **G26 관련 11 parity 테스트가 PASS**, **G16 유효/무효 Class Tweaker**, **G18 실제 client/common 컴파일 격리**, **FA011 정확한 미존재 Maven 진단**, **G9 레거시 1.21.11 remapJar 배포**, **G5 FA011 변경 후 회귀** 등 기존 미검증 영역에 새 실측이 추가됐다. 동시에 기본 `clean build`도 **165 = 148 PASS + 17 SKIPPED**, 오류 0으로 통과했다. 그러나 **17 SKIPPED는 PASS가 아니며**, 별도 opt-in 결과도 하나의 동일한 XML 실행이 아니다. **G1~G30 전체 수락 완료나 출시 완결을 주장하지 않는다.**

**§16 소유권·수정 범위:** `QA_REPORT.md`에만 신규 절 append; **§1~§15 원문 보존**. QA 작업자 6은 **Gradle·테스트·빌드 명령을 실행하지 않았고** 소스·테스트·설정·빌드 파일을 **수정하지 않았다**.

---

## 17. G1~G30 현행 수락 대장 및 G13/G20/G25/G29 실측 추가 (2026-11-12, 진행 중 증거 동결)

> **추가만 수행:** 이 절 직전의 **§1~§16 원문 707행과 과거 배포 JAR SHA-256·실패 기록을 그대로 보존**한다. 특히 §16의 당시 기준 **Gradle 9.7.1/JDK25 `clean build` 20 XML suites = 165 tests, 148 PASS·17 SKIPPED·0 FAIL/ERROR**, 플러그인 JAR **217,888 bytes**, SHA-256 **`25471A5703A45A8EA6F46C1D792055B6960089C853067A4883C9CF2585391985`**는 **이전 시점의 검증값**이다. 이 절의 새 G13/G20/G25/G29 결과는 **Prime이 별도로 수행/전달한 실행별 실측**이며, 새 전체 클린 빌드나 새 최종 배포 JAR 해시로 대체하지 않는다. QA 작업자 6은 Gradle·테스트·빌드를 실행하지 않았다.

### 17.1 새 실측: 완료된 실행과 진행 중인 실행을 분리

| ID / 실행 경로 | 확인된 범위와 결과 | 빠뜨리면 안 되는 판정 |
| --- | --- | --- |
| **G20 병렬 미러 복구** | Prime의 Gradle **9.7.1 / Loom 1.17.21**, `FabricAmperParallelMirrorConsumerTest` 독립 opt-in **XML 1 test / 1 PASS**, parallel + configuration cache 조건에서 **콘텐츠 주소 기반 JSON 및 아이콘 참조 리소스 복구** 확인. 실행 시 보고된 증거 경로: `build/test-results/test/TEST-dev.fabricamper.integration.FabricAmperParallelMirrorConsumerTest.xml` | §13의 기존 단일 미러 삭제/손상 복구 **1 PASS**에 **병렬 CC + JSON/아이콘 복구** 근거가 추가됨. **실제 IntelliJ Sync와 전체 Mixin/Class Tweaker 동시성 행렬은 여전히 NOT RUN**. 현재 Prime의 동시 Gradle 작업으로 위 XML이 조회되지 않아 **이번 QA에서 내용 직접 재확인은 불가**; Prime의 선별 실행 기록에 근거한다 |
| **G25 공식 native `.module` variant 검증** | 실제 게시된 **Loom 1.19.4 `.module`**를 세 버전에서 독립 조회한 선별 검증 **3 XML 테스트 / 3 PASS**. **Gradle 9.7.0**은 **Gradle API 9.8 요구 variant를 예상대로 거부**; **Gradle 9.8.0 및 9.9.1**은 variant resolution **PASS** | **G25 플러그인 variant 해석의 세 조합이 기대 동작과 일치**. `9.7.0`의 거부를 **9.7.0에서 Loom 1.19.4 실행 성공**이라고 바꾸지 않는다. 세 테스트의 JUnit PASS는 **예상 비호환 판정까지 포함**하므로 실제 소비자 전체 빌드 PASS 3개가 아니다 |
| **G13: Gradle 9.7.0 + Loom 1.18.4** | 업그레이드된 실제 settings-only 소비자 **`build` 성공**, 생성 소비자 JAR의 **컴파일된 `.class`와 루트 `fabric.mod.json` 정확히 1개 확인**, 독립 XML **1/1 PASS**, `BUILD SUCCESSFUL in 1m 18s` | **해당 정확 조합의 실제 소비자 배포 빌드 PASS**. KGP/모든 IDE·MC 런타임 버전 호환성을 자동 승인하지 않음. Prime의 진행 중 빌드로 현재 XML 디렉터리가 정리되어 **이번 QA에서 XML 직접 재열람 불가** |
| **G13: Gradle 9.9.1 + Loom 1.19.4** | 앞서 진행된 **`help` 독립 실행 1/1 PASS**. 후속 **실제 소비자 전체 `build` 재검증은 Prime 전용 세션에서 현재 진행 중** | **help/configuration 단계만 PASS**. 본 §17 작성 시 **전체 빌드의 종료 코드·클래스·JAR·XML 결과가 도착하지 않았으므로 `IN PROGRESS / NOT VERIFIED`**. G25 9.9.1 `.module` PASS를 대신하여 G13 실제 빌드를 PASS 처리하지 않음 |
| **G27 / FA011 / ZIP 선별 회귀** | Prime의 이전 handoff에서 **타깃 회귀 36/36 PASS** 보고 | **지정 단위/선별 회귀 36 PASS**로만 기록. **G27 전용 서버 Loader 부팅/side 필수 의존성**과 **FA011 TLS·Modrinth·CC 전범위 오류 분류**, **실사용 Loader 실행**까지 완료된 것은 아님. 단독 현재 XML 재열람 없이 이 36건을 전체 clean build 집계에 더하지 않음 |
| **G29 local Maven + 빈 캐시 소비자 2개** | 플러그인의 **marker + POM + JAR + `.module`** 격리 local Maven 게시 **EXIT 0**. `-g`가 각각 **완전히 빈 별개 Gradle user home**을 가리키는 settings-only 소비자에서 **Java-only `settings.kotlin.enabled=false` `help` 39초 EXIT 0**, **Kotlin enabled=true `help` 46초 EXIT 0** | 콜드 환경에서 **KGP 2.4.21 Gradle 플러그인 JAR 다운로드가 두 경우 모두 실제 발생**. 현재 정적 classpath의 KGP 전이 비용 존재를 관측했으며, **Kotlin off에서 KGP를 게으르게 미로드·미다운로드했다는 주장 금지** |

**G29 계측값(단위와 해석 고정):** `CacheMeasure.java`로 두 **독립 Gradle cache** 내부 `org.jetbrains.kotlin` JAR을 계수한 결과, Java-only **18개 / 39,055,873 bytes**, Kotlin enabled **22개 / 44,719,615 bytes**. 두 경우 모두 **`KGP 2.4.21-gradle96.jar` 30,672,980 bytes**가 캐시에 존재했다. 이것은 **다운로드 후 캐시에 남은 JAR 파일 수·바이트**이지 **네트워크 wire 전송량이나 KGP 순수 다운로드 시간의 직접 측정값이 아니다**. 39초/46초 `help` 실행 시간에는 **Loom·Minecraft 관련 초기화와 기타 네트워크/Gradle 비용**이 포함될 수 있으므로 **7초 차이를 KGP 단독 비용으로 계산하지 않는다**. *지연 KGP `buildscript` 대안*의 isolated projects/classloader/CC 검증은 수행된 근거가 없으며 **NOT VERIFIED**다.

### 17.2 전체 수락 31행(G1~G30 + G17a) 최신 상태 — 검증 범위를 한정한 분류

**상태 읽는 법:** **`PASS(선별)`** = 명시한 독립 TestKit/단위/구성/빌드 시나리오에서 PASS, **전체 G 출하 수락과 동의어가 아님**. **`PARTIAL`** = 요구 조건 가운데 일부만 실측. **`NOT RUN`** = 해당 필수 실환경 실측 없음. **`IN PROGRESS`** = Prime 실행 중 결과 미확정. **`SKIPPED`** = 특정 JUnit 실행에서 환경 게이트로 건너뜀(전체 테스트 부정 아님). **`BLOCKED`** = 실환경·인증·IDE·툴체인 제한으로 아직 필수 증거를 확보하지 못함. 과거 §9의 전 항목 NOT VERIFIED는 **그 당시 기준의 역사적 표**이며 수정하지 않는다.

| G | 현재 최신 실행 근거 | 남은 조건 / 판정 |
| --- | --- | --- |
| **G1** | build.gradle 없는 소비자 `help` **PASS(선별)** (§11) | Settings→Project 적용 확인; 정확 시나리오 **PASS(선별)** |
| **G2** | 동일 소비자 `tasks --all`의 Java/Resources/JSON/JAR/run 태스크 **PASS(선별)** | 실제 실행과 태스크 *목록* 구별 |
| **G3** | 소비자 `build`·클래스·루트 JSON 1개 JAR **PASS(선별)** (§11) | 실제 게임 Loader 로딩 별도 |
| **G4** | ID/version/archive YAML 수정 후 JSON/JAR **PASS(선별)** (§14) | IDE 증분 모델 영향은 별도 |
| **G5** | 미러·FA011 패치 후 warm `help` + 측정 4빌드 CC 저장→HIT→YAML 수정 MISS→HIT **PASS(선별)** (§13/§16) | 실제 테스트는 version+dependency 편집; **Java 변경 사례·완전 콜드 Loom manifest·TTL 이후 재사용 NOT RUN → PARTIAL** |
| **G6** | 실제 IntelliJ 사용 로그 없음 | Java/Kotlin roots·의존성·Loom run 구성 인식 **NOT RUN / IDE 환경 BLOCKED** |
| **G7** | Java→Kotlin→Java 조건부 태스크, Kotlin 클래스·adapter/JAR **PASS(선별)** (§14) | 마지막 Java-only 복귀 후 최종 JAR 재빌드는 미검증 → **PARTIAL** |
| **G8** | MC26.3 + Yarn 거부 **FA004 PASS(빠른 진단)** (§9/§15) | 해당 오류 시나리오 **PASS(선별)** |
| **G9** | 실제 JDK21.0.6 + MC1.21.11 `remapJar`, 컴파일 클래스·루트 JSON 1개 **PASS(선별)** (§16) | 레거시 클라이언트/서버 Loader 실제 로딩 **NOT RUN** |
| **G10** | `:include` 실제 Commons Lang nested JAR 및 Loader `jars[].file` **PASS(선별)** (§15) | **Modrinth, compile-only/exported/runtime-only 행렬 및 실제 Loader JIJ 런타임 NOT RUN → PARTIAL** |
| **G11** | 오타·타입·alias/tag/중복 키·없는 리소스 등 FA 단위/빠른 회귀 및 실제 G16 멤버 검사 PASS (§9/§16) | 요청한 **모든 FA 진단/원본 보존/오류 경로 전체 조합**을 하나의 완료 수락으로 보증 불가 → **PARTIAL** |
| **G12** | main/client 기존 `fabric.mod.json` 거부 + 원본 보존 **FA008 PASS(빠른 진단)** | 해당 회귀 **PASS(선별)** |
| **G13** | **9.7.0+Loom1.18.4 실제 `build`·JAR PASS 1/1**; **9.9.1+Loom1.19.4 `help` 1/1 PASS** | **9.9.1 실제 `build` IN PROGRESS**, KGP 별도 판정 남음 → **PARTIAL/IN PROGRESS**, 전수 PASS 금지 |
| **G14** | settings 단일 Amper plugin으로 Loom 구성 + 선택 KGP 소비자 G1/G7 **PASS(선별)** | 외부 선적용 Loom/KGP classloader 충돌은 G19 별개 |
| **G15** | 실제 격리 `runClient`·`runServer` Loader 실행 근거 없음 | **NOT RUN / 실제 MC·전용 서버·EULA 환경 BLOCKED** |
| **G16** | 실제 `validateAccessWidener` 유효 Class Tweaker 성공 / 없는 멤버 의도된 실패 **1/1 PASS(선별)** (§16) | Loader 런타임 적용은 G15와 별개 |
| **G17** | 구성 미러에 interface injection JSON 전달(G17a) 근거 존재 | **실제 interface injection compile·IDE·Loader runtime 미검증 → PARTIAL/NOT RUN** |
| **G17a** | 원본/태스크 출력 부재 시 Loom 구성 시점 미러 조회 `help` **PASS(선별)** (§11) | 명세의 **실제 IDE Sync 직전/중 metadata 검증 NOT RUN → PARTIAL** |
| **G18** | split client 의존성 선언 + client Java 컴파일 성공/common Java 접근 예상 실패 **2/2 PASS(선별)** (§16) | 실제 게임/서버 side Loader는 G27에 속함 |
| **G19** | 기존 `loom` extension 이름 충돌 → **FA010 PASS(선별)** (§15/§16) | 다른 *실제 선적용* Loom/KGP plugin 버전·classloader 충돌 **NOT RUN → PARTIAL** |
| **G20** | 기존 JSON 미러 삭제/오염 복구 + clean/help **1/1 PASS**, 신규 **병렬 CC·JSON/icon 복구 1/1 PASS** | **IDE Sync 및 전체 참조 리소스(Mixin/Class Tweaker) 손상·경쟁 조건 행렬 NOT RUN → PARTIAL** |
| **G21** | 생성 태스크 JSON/구성 미러 바이트 일치 및 JAR 루트 JSON 1개 **PASS(선별)** (§11) | **반복 clean 빌드의 결정적 모드 JAR 해시** 직접 비교 미검증 → **PARTIAL** |
| **G22** | `Project.group/version`, archivesName, 실제 `jar`+`sourcesJar` **PASS(선별)** (§15) | 해당 정확 소비자 시나리오 PASS |
| **G23** | YAML Jupiter 테스트 의존성, 소비자 JUnit 1개 실제 검색·실행·XML **PASS(선별)** (§15) | 명시 테스트 범위 PASS; 추가 JUnit 엔진 행렬 불포함 |
| **G24** | Mixin/Class Tweaker/icon snapshot + 초기 JAR·중복 없음, Mixin 원본 수정 후 snapshot hash 변경 **PASS(선별)** (§14) | **수정 후 실제 JAR 재빌드·바이트 갱신 및 IDE Sync NOT RUN → PARTIAL** |
| **G25** | Loom1.19.4 native `.module` **9.7.0 예상 거부 / 9.8.0 PASS / 9.9.1 PASS**, 세 결과를 각각 검증한 **JUnit 3/3 PASS** | **variant resolution 판단 PASS(선별)**; **9.7.0은 실제 실행 비호환**. 이것을 세 버전 소비자 전체 빌드 PASS로 표기 금지 |
| **G26** | 공식 Fabric Loader0.20.1 predicate API parity **11/11 PASS**와 조건 보존/모순 진단 단위 회귀 (§16) | **선정된 Loader 버전·조건 테스트 PASS**, 다른 Loader/전체 런타임은 별도 |
| **G27** | G18 split classpath + 별도 side/FA011/ZIP 타깃 회귀 **36/36 PASS(보고됨)** | **`environment:"*"`+client-only mod, Loader `depends`, 전용 서버 부팅 및 Mixin side 격리 NOT RUN → PARTIAL/BLOCKED** |
| **G28** | 실제 IntelliJ first-import, 수동 Sync, YAML 저장→자동 Sync 관찰 없음 | **NOT RUN / IDE 환경 BLOCKED**. 자동 Sync 미지원 판정 및 UX 문서화 여부 미확정 |
| **G29** | 두 완전 빈 `-g` 홈 소비자 `help` 모두 EXIT0, 각 cache 내 KGP JAR 실다운로드 및 파일 수/바이트 **측정 PASS** | **정적 classpath KGP cold 전이 비용 실측 부분 PASS**. wire-byte·순수 시간·지연 KGP 도입/CC·classloader 격리 **NOT VERIFIED → PARTIAL** |
| **G30** | 양 alias 동시 FA001, YAML 문법/UTF8/tag/anchor 오류, 소비자 `mod.yml→mod.yaml` CC 무효화·재사용, 중복 ZIP 음성 회귀 **PASS(선별)** | 모든 scalar/collection tag·빈 문서·실배포 ZIP 오염·출력 보존의 완전 교차 행렬은 별도 → **PARTIAL** |

### 17.3 집계 규칙, 남은 출하 게이트와 Prime 후속 입력

**PASS/FAIL/SKIP를 섞지 말 것:** §16 최종 기본 `clean build` **165 = 148 PASS + 17 SKIPPED + 0 FAIL/ERROR**는 그 시점 단일 실행의 합계다. §17의 **G13 1 PASS, G25 3 PASS, G20 1 PASS, 타깃 36/36 PASS 및 G29 외부 소비자 `help` 두 번**은 **각각 별도 실행**이며 이 165개에 더해 단일 JUnit suite 합계를 만들지 않는다. **G25의 9.7.0 variant 예상 거부**, **G18의 common javac 의도된 실패**, **G16의 invalid Class Tweaker 실패**, **FA011 미존재 Maven 구성 실패**는 각각 *부정 검증의 기대 동작*이다. 과거 §10/§11/§12/§15/§16 실패 사건과 그 후 수정·통과 이력도 삭제하지 않았다.

**G13 진행 중 항목:** Prime만 실행 중인 **Gradle 9.9.1 + Loom 1.19.4 소비자 전체 `build`** 결과는 이 §17 기준 **IN PROGRESS**다. 아직 `EXIT 0`, 새 JAR 클래스/메타데이터, XML PASS 또는 배포 해시를 확인하지 않았으므로 **종료 전에 성공 예측 불가**. Prime이 실제 종료 코드·XML·JAR 검증을 전달하면 **§18 등 새로운 시점의 후속 절**로 기록하되 **§1~§17을 소급 변경하지 않는다**.

**출시 판단:** G1~G30에는 **실측된 좁은 테스트 PASS가 다수** 있으나 **G6/G28 IDE, G15/G17 Minecraft Loader 및 injection, G27 전용 서버 side, G13 후반 빌드·KGP, G10 JIJ 런타임, G20 IDE, G29 지연 KGP** 등의 **필수 수락 게이트가 여전히 OPEN/NOT RUN/BLOCKED**다. **`clean build` 성공 또는 `.module` 조회 성공을 G1~G30 전체 수락/완전 출시로 선언하지 않는다**.

**§17 변경·증거 주의:** 오직 `QA_REPORT.md`에 절을 **append**했고 **기존 §§1~16 원문과 기존 모든 SHA·실패 이력을 수정하지 않았다**. 이번 G20 병렬 XML은 Prime이 Gradle 재실행하는 동안 파일이 존재하지 않아 QA가 직접 읽지 못했으므로 **Prime 전달 결과 1 PASS로 출처를 구분**했다. 해당 시점 G13 9.7.0 별도 XML도 재열람할 수 없었다. QA 작업자 6의 **Gradle/테스트/빌드·생산 소스 수정 0건**.

---

## 18. G13 전체 매트릭스 빌드·G15 실제 Loader 클라이언트·G10 실제 JIJ 런타임 검증 후속 (2026-11-12)

> **시점/소유권:** 기존 **§1~§17 총 774행의 원문·기존 SHA-256·PASS/FAIL/SKIP 이력은 그대로 보존**한다. 여기 기록한 결과는 **Prime이 각자 독립적으로 실행한 필터/소비자 작업**이며 새 전체 `clean build`나 동일 JUnit suite를 뜻하지 않는다. 아래 음성 테스트의 의도된 실패 및 G19의 부정확한 실험은 제품 오류/검증 완료와 구분한다. **QA 작업자 6은 Gradle/테스트/소스 수정 없이 보고서에만 추가**했다.

### 18.1 새로 완료된 실제 소비자 검증

| 항목 | Prime의 실제 증거 | 수락 범위 / 한계 |
| --- | --- | --- |
| **G13 — Gradle 9.9.1 + Loom 1.19.4** | **전체 settings-only 소비자 Java `ModInitializer` 실제 컴파일·`build`·배포 JAR 확인**, 독립 TestKit **XML 1/1 PASS**, outer `BUILD SUCCESSFUL in 1m 24s`. Prime의 직전 XML 직접 확인에서 `:compileJava`, `:jar`, `:verifyFabricAmperJar` **SUCCESS**, 레거시 `:remapJar` **실행되지 않음**; 소비자 배포 JAR의 **컴파일된 `.class` 및 루트 `fabric.mod.json` 정확히 1개**, 생성/미러 JSON 동일성 확인 | §17의 이 조합 **`help` PASS / full build IN PROGRESS**는 당시 기록으로 보존한다. **이제 해당 실제 소비자 전체 빌드 1 PASS**. G13 다른 독립 조합 **Gradle 9.7.0 + Loom 1.18.4 전체 빌드 1 PASS**도 §17과 함께 유지. **선별된 두 정확 조합의 Java 소비자 빌드는 PASS**, KGP 조합별 실행·IDE·게임 Loader 호환성을 한꺼번에 증명한 것은 아님 |
| **G15 — Minecraft 26.3 / Fabric Loader 0.20.1 실제 클라이언트** | 사전 게시한 플러그인으로 **`settings.gradle.kts` + YAML만 있는 격리 소비자**의 실제 `runClient`를 감독 실행. Fabric Loader 로그가 생성된 **`amper_rt_g15`** 모드를 로드했고, 소비자의 UUID nonce 증거 **`main` / `client` / `tick`**가 실제 메인 초기화·클라이언트 초기화·게임 틱을 증명. 실제 배포 JAR 루트 metadata 1개 및 초기화 클래스도 검증. 선택 Runtime TestKit **1 PASS / 0 FAIL**, 같은 실행의 G10 sibling **SKIPPED**, outer **EXIT 0 in 2m 18s** | **G15 클라이언트 측 실제 Fabric Loader 로딩 PASS(선별)**. 실제 전용 서버 `runServer`는 실행하지 않았으며 **EULA 승인/인증 우회/세계 수정 없음**. G15 명세의 **클라이언트+서버 양측 전체 수락은 PARTIAL**. 테스트 소스 `FabricAmperRuntimeClientConsumerTest.kt:39-100` |
| **G10 — 배포 Maven `:include`의 실제 Loader JIJ 런타임** | Commons Collections **4.5.0**을 포함하는 별도 producer 소비자 실제 `build` **1m 55s PASS**. 외부 모드가 없는 **음성 host** `build` **1m 39s + `runClient`**에서 호스트 main/tick 증거와 해당 라이브러리·외부 모드 **부재**를 확인. **양성 host** `build` **1m 29s**, producer의 **실제 배포 JAR**를 격리 `run/mods`에 설치해 `runClient`. Loader 로그/식별자에서 외부 모드 **`amper_rt_g10_embedded`** 및 중첩 모드 **`org_apache_commons_commons-collections4` 버전 4.5.0**을 인식했고, UUID nonce **`embedded-library-invoked`**, **`loader-saw-external-and-nested-library`**, 클라이언트 **tick** 증거를 확인. Target Runtime TestKit **XML 1 PASS / 0 SKIPPED / 0 FAIL**, **2026-11-11T16:14:23Z**, outer Gradle **EXIT 0 in 8m 5s** | `build/test-results/test/TEST-dev.fabricamper.integration.FabricAmperRuntimeClientConsumerTest.xml:1-4`를 이번 QA에서 **직접 확인**했다. 다른 opt-in 실행의 G15 XML은 같은 경로를 재사용하므로 **현재 G10 XML 한 개에 G15 PASS를 합산하지 않는다**. 배포 JAR 기반 **실제 Loader 중첩 라이브러리 호출 PASS**지만 **Modrinth·exported·runtime-only·compile-only 전체 행렬은 별도**. 테스트 소스 `FabricAmperRuntimeClientConsumerTest.kt:102-170,172-250` |

**G10 양성/음성 구분의 중요성:** 음성 host에는 `commons-collections4` 직접 Gradle 의존성이 없고 외부 producer JAR도 설치하지 않아 Loader의 외부/중첩 모드 인식과 `HashBag` 가시성이 모두 **false**임을 확인했다. 양성 host에서는 producer **배포** JAR만 `run/mods`에 넣었고, Loader의 중첩 `jars[].file`을 통해 라이브러리 클래스가 실제 호출됐다. 이는 단순히 producer 개발 classpath에 있던 라이브러리의 호출을 **JIJ 런타임 PASS로 오인하지 않도록** 구성된 실측이다. 실제 클라이언트 테스트는 격리 홈/감독 대상 프로세스를 사용하며, 사용자 `.minecraft`·계정·세계·EULA를 수정한 증거는 없다.

### 18.2 IDE 대안 증거 및 실패/부정 경계 실험

| 항목 | 후속 결과 | 정확한 판정 |
| --- | --- | --- |
| **G6/G28 — IDE 없는 Gradle Tooling API 모델 탐색** | G29에서 게시한 플러그인을 사용한 **새 격리 settings-only 소비자**, **JDK25/Gradle9.7.1** Tooling API `IdeaProject`/모듈 모델 탐색 **PASS (89,060 ms)**. **1개 Gradle 프로젝트/모듈**, **160개 의존성**, `src/main/java`, `runClient`·`runServer`·`compileJava`·`generateFabricModJson` 태스크 인식 | **IDE-free Tooling API 모델 조회 PASS(부분 증거)**. 실제 **IntelliJ GUI 최초 import, 수동 Sync, YAML 저장 후 자동 Sync, Kotlin source root 및 IDE Run Configuration 생성**은 **NOT RUN**. 따라서 **G6/G28 전체 PASS 아님** |
| **G19 — 실제 다른 Loom 선적용 시험** | init script로 **'real Loom 1.18.4'** 선적용 시도했지만, 실제 구현 `codeSource`는 classloader 해석으로 **Loom 1.17.21**이었음. 엄격한 '다른 버전 실제 선적용' 검증 assertion은 **FAIL**한 반면 제품은 **FA010** 충돌 진단을 출력 | 이 실험은 **원하는 다른 버전 조건이 성립하지 않아 INVALID / NOT ACCEPTED**. `FA010` 자체가 관측됐더라도 **G19 실제 다른 버전 Loom/KGP 선적용 전체 PASS로 인정하지 않음**. 기존 §15의 **합성 extension 충돌 PASS**만 별개 유지. 잘못된 시험의 실패를 제품 일반 FAIL이나 검증 성공으로 왜곡하지 않음 |
| **FA011 — 진짜 오프라인 결측 Maven** | `--offline` + 캐시에 없는 GAV에서 Gradle 네이티브 **`No cached version ... offline mode`** 오류와 **EXIT 1(의도된 음성 결과)**; **거짓 FA011 진단은 발생하지 않음** | §16의 온라인 실제 결측 Maven GAV **FA011 위치/좌표 + 네이티브 `Could not find` PASS**와는 **다른 오류 분류**. **오프라인 네트워크 가용성 오류 ≠ 미존재 artifact**를 보존한 음성 실측. TLS/Modrinth/다양한 CC 상태는 **NOT VERIFIED** |
| **G30 — YAML 별칭 동시 존재와 CC** | 사전 워밍업한 `mod.yaml` 소비자에서 CC **HIT**, 이후 **`mod.yml`을 추가 생성**하자 구성 캐시 **MISS**(새 파일 생성을 입력 변화로 추적), 두 별칭 공존에 **FA001** 예상 오류와 **EXIT 1(의도된 음성 결과)** 확인 | **별칭 충돌을 침묵하거나 옛 CC로 통과시키지 않는 음성 경계 PASS**. 충돌 제거 후 CC 재사용 **후속 복구/HIT는 아직 미검증**. §15의 별도 alias rename HIT→MISS→HIT 실측과 구별 |

### 18.3 최신 G 수락 변경분과 출시 제한

| G / FA | §17 이후 최신 상태 | 남은 필수 조건 |
| --- | --- | --- |
| **G13** | **두 지정 Gradle/Loom Java 소비자 전체 빌드 1 PASS씩** (9.7.0/1.18.4 및 9.9.1/1.19.4); G25의 variant 3/3과 **별개** | 선택 KGP와 각 조합의 전체 런타임/IDE 호환성 미검증 |
| **G15** | 실제 **MC26.3 Loader0.20.1 client** main/client/tick **PASS(부분)** | **전용 서버 `runServer` NOT RUN**, 필요한 EULA·격리 승인 확인 전 실제 서버 수락 금지 |
| **G10** | 실제 프로덕션 배포 JIJ + Loader nested Commons Collections 4.5.0 **PASS(부분)** | Modrinth/다른 dependency scopes 및 추가 런타임 시나리오 미검증 |
| **G6/G28** | **Gradle Tooling API IdeaProject/모듈/태스크 조회 PASS(부분)** | 실제 IntelliJ GUI 최초 import, 수동·자동 Sync **NOT RUN** |
| **G19** | synthetic extension 충돌 FA010 PASS 유지; 실제 다른 Loom 시험 **INVALID** | **서로 다른 실제 Loom/KGP 버전/classloader를 엄격히 재현**하고 명확한 충돌 확인 |
| **G17** | owner-12 구현 진행 중이나 **컴파일·Loader runtime·IDE 수락 NOT RUN** | 작업 완료 후 분리된 인터페이스 주입 compile/negative/runtime 증거 필요; **진행 중 코드를 PASS로 선기록 금지** |
| **G27** | 이전 단위·side 정적 회귀 일부 PASS | **전용 서버 부팅 NOT RUN**, **서버 EULA 승인되지 않음**; 무단 EULA 동의·인증 우회·기존 세계 접근 금지 |
| **FA011/G30** | 각각 **오프라인 미존재 캐시 false FA011 방지**, **동시 YAML alias CC MISS+FA001** 음성 실측 | FA011 TLS/Modrinth/CC·G30 충돌 제거 후 복구와 전체 악성 YAML 행렬 미검증 |
| **G20/G29 등 기존 검증** | §17의 병렬 CC 미러/아이콘 복구와 KGP 콜드 캐시 **실측값 변경 없음** | G20 실제 IDE/나머지 리소스·G29 lazy-KGP isolated/classloader/CC 검증은 여전히 미완료 |

**집계와 출하:** §16의 당시 기본 `clean build` **165 tests = 148 PASS + 17 SKIPPED** 및 당시 배포 JAR **217,888 bytes / SHA-256 `25471A5703A45A8EA6F46C1D792055B6960089C853067A4883C9CF2585391985`**는 **이전 기준선으로 유지**하고 새로운 최종 파일 해시로 재사용하지 않는다. G13·G15·G10의 새 **선별 XML**은 이 합계와 별도다. **G13 후반 전체 빌드가 통과했어도 G6/G28 IntelliJ, G15 서버, G17 interface injection, G27 server side, G19 실제 선적용 버전 검증, G20 IDE, G29 지연 KGP** 등 미검증·환경 제약이 남는다. **G1~G30 전항목 PASS나 출시 승인으로 표시하지 않는다.**

**§18 변경 범위:** `QA_REPORT.md`에만 새 절 append, **§1~§17 역사적 기록 774행 보존**. QA 작업자 6의 Gradle/테스트 실행·제품/테스트/빌드 파일 수정 **0건**.

---

## 19. G10 Modrinth 컴파일·G17 주입 컴파일 PASS와 런타임 네이티브 실패·G30 복구 (2026-11-12)

> **기록 단위:** 기존 **§1~§18 총 817행과 모든 과거 SHA/FAIL/PASS 기록을 원문 그대로 보존**한다. 아래는 Prime의 **서로 다른 선별 실행**에 관한 새 증거다. 특별히 **G17의 Java 컴파일 PASS, G17 실제 런타임 JUnit FAIL, G15 이전 런타임 PASS와 최신 대조군 FAIL**을 하나의 성공/실패 수치로 뭉치지 않는다. 후속 전체 `clean build` 및 최신 배포 JAR 해시 산출은 **§20 예정, 이 절에서 미실행/미기재**다.

### 19.1 신규 통과: Modrinth 소비자 컴파일과 YAML custom-only 인터페이스 주입

| 대상 | 실제 실행 결과·검사 | 수락의 정확한 범위 |
| --- | --- | --- |
| **G10 Modrinth Maven 의존성** | settings + YAML **만 있는 소비자**에서 `maven.modrinth:modmenu:21.0.0`을 **`target: client`, `scope: compile-only`**로 선언. 실제 Modrinth JAR 약 **836.7KB** 캐시 확인, `ModMenuApi`를 참조하는 **client Java 컴파일** 및 배포 **JAR 검증 PASS**, 빌드 **1m 19s PASS**. 생성된 Loader `depends`에 **빌드 전용 client compile-only 라이브러리를 자동 필수 의존성으로 오염시키지 않음** 확인 | **Modrinth 원격 Maven 좌표 해석, 클라이언트 전용 컴파일 및 Loader metadata 비오염 PASS(선별)**. **ModMenu의 실제 Fabric Loader 런타임 로딩, 모든 exported/runtime-only/include 조합은 NOT RUN**. §18의 **Commons Collections 4.5.0 실제 JIJ 런타임 PASS**와는 독립 시나리오 |
| **G17 `custom`만으로 컴파일 시점 인터페이스 주입** | `FabricAmperG17CompileConsumerTest` **JUnit 1 PASS / 0 SKIP**, outer **EXIT 0 in 3m 6s**. 음성 소비자에서 **동일한 Java 코드** `ItemStack.EMPTY.amper_rt_g17$ping()`의 `compileJava`가 **`cannot find symbol`로 예상대로 실패**함을 먼저 검증. 양성 소비자에는 YAML `custom."loom:injected_interfaces"`만 추가하여 **동일 호출 `compileJava` 성공**, 클래스 생성 및 **구성 시점 Loom `fabricModJsonPath` 미러 = 생성 JSON = 배포 JAR의 루트 `fabric.mod.json` 바이트 동일**, 루트 메타데이터 정확히 1개 확인. **Class Tweaker·Mixin·수동 `fabric.mod.json` 사용 없음** | **YAML custom-only → Loom 구성 시점 미러 → Mojang `ItemStack`의 직접 메서드 호출 컴파일**이라는 **엄격한 음성/양성 대조 계약 PASS**. JVM에 해당 인터페이스가 **실제로 구현되는 런타임 효과**는 *다른 테스트*다. 소스: `FabricAmperG17CompileConsumerTest.kt:17-56,58-144` |

**G17 컴파일 음성 테스트의 의미:** 두 소비자는 `loom:injected_interfaces` YAML stanza 이외의 Java 소스·의존성·매핑 조건이 일치하도록 검사한다. **먼저 음성 프로젝트를 실행**하여 컴파일 실패가 실제 `G17CompileProbe.java`의 없는 `amper_rt_g17$ping` 심볼 때문임을 확인하고, 이어 양성 프로젝트에서 Loom의 *구성 시점* 메타데이터 경로와 실제 `compileJava`·배포 ZIP을 검증한다. **이 성공을 게임 틱이나 IDE 인터페이스 주입 성공으로 확대하지 않는다.**

### 19.2 G17 런타임 두 번 실패 및 G15 대조군에서도 발생한 동일 네이티브 종료

| 실행·증거 | 확인된 사실 | 판정 및 금지할 추론 |
| --- | --- | --- |
| **G17 실제 Loader 런타임, 1차·독립 2차 실행** | 별도의 **`classTweaker v1 official` `inject-interface net/minecraft/world/item/ItemStack org/example/runtime/G17Injected`** 소비자에서 실제 `build` 및 `validateAccessWidener` **PASS (1m 10s)**. Fabric Loader 로그에 **`amper_rt_g17` 로드**가 나타남. UUID nonce **`main`** 증거가 실제 `ItemStack.EMPTY instanceof G17Injected` 및 기본 메서드 **직접 호출 성공** 뒤에 기록되었고, **`client`** 초기화 증거도 존재. 그러나 **틱 증거는 없음**. 실제 runClient 프로세스가 SDL revision 로그 직후 **Windows NTSTATUS `0xC0000409`**로 종료하여, **두 독립 G17 런타임 JUnit 실행이 각각 `1 FAIL`** | **빌드·Loom CT 검증·Loader 모드 발견·main 단계 주입/기본 메서드 호출은 관찰된 성공**. 그러나 **요구한 실제 게임 틱까지의 런타임 수락은 FAIL / NOT VERIFIED**. JUnit **2개의 서로 다른 실행 각각 1 FAIL**이며 **단일 2-tests suite**라고 쓰지 않음. `FabricAmperG17RuntimeConsumerTest.kt:37-104,106-116,149-190`의 의도된 틱 nonce assertion은 아직 만족되지 않음 |
| **새 G15 독립 단순 대조군** | §18에서 **`amper_rt_g15` 실제 runClient main/client/tick 1 PASS**였던 동일 유형의 격리 단순 모드도 이번 독립 재실행에서는 **`main`·`client`만 기록**, **`tick` 없음**. SDL revision 이후 **동일 `NTSTATUS 0xC0000409` / Gradle `:runClient` EXIT 1** 발생. 현재 `build/test-results/test/TEST-dev.fabricamper.integration.FabricAmperRuntimeClientConsumerTest.xml:1-4`에서 **`tests="1" failures="1" skipped="0" errors="0"`**를 QA가 직접 확인 | **G15의 과거 실제 틱 PASS를 지우지 않고, 최신 G15 대조 실행 1 FAIL을 추가**. 즉 **현재 환경에서 같은 네이티브 종료가 G17 주입 여부와 무관한 대조군에도 나타남**은 실제 관찰이다. **공통 그래픽/SDL/드라이버·네이티브 환경 불안정 가능성은 있으나 원인이 확정된 것은 아님** |

**진단 해석의 경계:** `0xC0000409`는 **Windows 네이티브 프로세스 종료 코드**이고, 관찰 로그에는 해당 지점의 Java 예외가 보고되지 않았다. **G17 주입 버그가 원인이라고 단정할 수 없고**, 반대로 대조군에서 같은 코드가 나왔다는 이유만으로 **G17 runtime/게임 틱 PASS**로 처리할 수도 없다. G17의 **custom-only 컴파일 PASS**와 런타임 **CT+main 부분 성공 / 틱 미검증·JUnit FAIL**은 **명시적으로 분리**한다. 런타임 환경·시스템 그래픽 원인 진단 및 안정적인 동일 조건 대조 재검증이 필요하지만 **이번 QA 작업자는 재실행하지 않는다**.

### 19.3 G30 YAML 별칭 충돌 후 복구 및 남은 수락 항목

**G30 Configuration Cache 복구 완료:** §18의 **워밍업된 `mod.yaml` 소비자 CC HIT → `mod.yml` 추가 생성 → CC MISS → 두 별칭 충돌 FA001 / 예상 EXIT 1** 음성 검증에 이어, Prime이 **새로 만든 `mod.yml`을 삭제한 뒤 `help --configuration-cache`를 다시 실행**하여 **`Reusing configuration cache` + `BUILD SUCCESSFUL in 20s`** 확인. **충돌 입력 생성의 무효화/FA001과 충돌 해제 후 캐시 복구까지 PASS(선별)**. 다만 전체 malformed YAML/중복 ZIP/원본 보존 조합을 이 경로 하나로 완료 처리하지 않는다.

| 남은 G/FA | 현시점 수락·차단 조건 |
| --- | --- |
| **G17** | **compile strict custom-only 1 PASS**, **Loader CT+main 기본 메서드 부분 확인**, 실제 **틱까지의 두 런타임 JUnit 각 1 FAIL**. **IntelliJ Sync/IDE interface injection NOT RUN**; 네이티브 오류 근본 원인 미확정. **G17 전체 PASS 불가** |
| **G15** | §18의 과거 실제 client tick **PASS**와 최신 단순 대조군 native **FAIL**을 동시 보존. **안정적인 client 재현과 전용 서버 런타임 미완료** |
| **G10** | §18의 실제 Maven JIJ Loader 호출 **PASS**, 이번 Modrinth compile-only 소비자 **PASS**. **Modrinth 게임 런타임과 타 의존성 scope 결합 검증은 NOT RUN** |
| **G6/G28** | Tooling API IdeaProject 조회만 PASS. 실제 **IntelliJ GUI 최초 import·수동/자동 Sync NOT RUN** |
| **G19** | 합성 Loom extension FA010 PASS, 실제 다른 버전 선적용 실험은 §18의 **INVALID** 유지 |
| **G27** | **전용 서버 부팅 NOT RUN / EULA 승인 없음**. 서버 접근, 인증 우회, 자동 EULA 승인 절대 금지 |
| **FA011** | 실제 결측 Maven과 offline 오류를 구별한 선별 증거는 PASS. **TLS/Modrinth 오류 분류/CC 전체 행렬 NOT RUN** |
| **G20/G29 및 기타** | §17의 병렬 미러 복구·정적 KGP cold-cache 계측값은 유지, **G20 IDE·전체 리소스 복구**, **G29 지연 KGP classloader/CC**는 미검증 |

**릴리스 판정·향후 §20 경계:** §16의 과거 기본 `clean build` **165=148 PASS+17 SKIPPED**, 과거 플러그인 JAR **217,888 bytes / SHA-256 `25471A5703A45A8EA6F46C1D792055B6960089C853067A4883C9CF2585391985`**는 당시 검증 결과로 보존한다. §19의 G17 **컴파일 1 PASS**와 **런타임 각 1 FAIL**, G15 **이전 1 PASS/최신 1 FAIL** 및 G10 Modrinth 빌드 PASS는 **독립 필터/프로세스 결과이므로 합산해 새로운 clean suite 수를 만들지 않는다**. 이 절 작성 시 **최신 clean build·배포 JAR SHA는 미확정**이며 후속 §20에서 실제 결과가 도착한 뒤 기록한다. **G1~G30 전수 검증 완료·완전 출시 승인 선언 불가**.

**§19 변경 범위:** 오직 `/vbc/Fabric Amper/QA_REPORT.md` 끝에 추가; **§1~§18 원문 817행·과거 SHA·실패 이력 불변**, QA 작업자 6의 **Gradle/테스트 실행 및 다른 파일 수정 0건**.

---

## 20. 최종 클린 빌드·실제 G19 외부 Loom 충돌·G27 비서버 수락과 G1~G30 출하 판정 (2026-11-12)

> **최종 기록의 범위:** 이전 **§1~§19 총 860행을 한 글자도 변경하지 않고** 본 절만 추가했다. 모든 기존 **PASS·FAIL·SKIPPED·INVALID·SHA-256**은 당시 실행의 원문 이력으로 보존한다. 아래에서 **최종 기본 빌드 JUnit 집계**, **별도 opt-in TestKit**, **의도된 음성 소비자 실패**, **실제 환경 차단**을 혼동하지 않는다. 수치·해시는 **Prime이 실제 Gradle/산출물에서 확인해 전달한 결과**이며 QA 작업자 6은 Gradle·테스트·빌드를 실행하지 않았다.

### 20.1 정리 과정까지 포함한 최종 Gradle 9.7.1/JDK 25 빌드

| 시간순 사건 | 실제 결과 | 해석 |
| --- | --- | --- |
| **첫 `clean build validatePlugins` 시도** | **FAILED**. 프로젝트 내부 격리 홈 `build/g6g28-probe/isolated-gradle-home`의 **Gradle daemon PID 10584**가 잠금을 보유해 작업 불가 | **실행 환경의 파일 잠금 장애**를 숨기지 않는다. 테스트/제품 검증 FAIL 또는 최종 클린 빌드 성공으로 기록하지 않는다 |
| **제한된 daemon 정리** | Prime이 **해당 프로젝트 소유 격리 홈에만 `gradle --stop -g`를 적용하여 daemon 정확히 1개 종료** | 사용자/전역 Gradle daemon이나 다른 프로세스를 종료하지 않았음. QA 담당자는 이 조치를 실행하지 않음 |
| **정리 뒤 첫 클린 성공** | `clean build validatePlugins` **PASS**, 당시 **186 tests = 158 PASS + 28 SKIPPED + 0 FAIL**, `validatePlugins` PASS; 그 시점의 플러그인 SHA는 **`F36...`**으로 보고됨 | **G19 독립 실소비자 TestKit 소스가 추가되기 전**의 별도 기준선. 완전 해시를 이 중간 실행의 새 독립 증거인 것처럼 추정하지 않음 |
| **G19 실제 외부 Loom TestKit 소스 추가 후 최종 실행** | `clean build validatePlugins` **EXIT 0, `BUILD SUCCESSFUL in 41s`**, Gradle **9.7.1/JDK 25**, **11 tasks: 8 executed + 3 cached**. 최종 **29 JUnit XML suites = 187 tests = 158 PASS + 29 SKIPPED + 0 FAIL + 0 ERROR**, `validatePlugins` **PASS** | **이것이 §20의 최종 기본 빌드 집계**. 186→187의 신규 G19 테스트는 기본 빌드에서 **SKIPPED**이므로 PASS는 158로 유지되고 SKIPPED가 28→29가 됨. 과거 §16의 165=148 PASS+17 SKIPPED와도 서로 다른 빌드 |

**최종 배포 산출물 — Prime 실측, 프로젝트 루트 `/vbc/Fabric Amper`:**

| 파일 | 정확한 크기 | SHA-256 |
| --- | ---: | --- |
| `build/libs/fabric-amper-0.1.0.jar` | **218,069 bytes** | **`F36EC2A9465A8E0801D342CCDFAD8D3952B25777E8F781DEAA717A50E2DBDC3D`** |
| `build/libs/fabric-amper-0.1.0-sources.jar` | **51,628 bytes** | **`8DC48566F787B9005CFC1B11EE7B1C74D4FF1988C091AC5AF7E64208BED37C5C`** |

이는 **Settings/Project Gradle 플러그인 본체와 소스 JAR**이며, 소비자가 게임의 `mods/`에 설치할 **Fabric 모드 JAR 자체가 아니다**. Prime의 **최종 바이너리 ZIP 직접 검사**에서 `META-INF/gradle-plugins/dev.fabric-amper.settings.properties`, `META-INF/gradle-plugins/dev.fabric-amper.project.properties`, `dev/fabricamper/plugin/FabricAmperSettingsPlugin.class`, `dev/fabricamper/plugin/FabricAmperProjectPlugin.class`는 **각각 정확히 1개**, **중복 ZIP entry 0개**였다. 이전 독립 소비자가 게시된 settings marker로 적용·빌드/실행하는 증거도 §17~§19에 있다. **최종 기본 빌드의 29 SKIPPED를 29 PASS로 바꾸지 않는다.**

**최종 클린 빌드 이후 로컬 전용 게시(새 `clean` 아님):** Prime이 `publishAllPublicationsToG29ProbeRepository -PfabricAmperProbeMavenRepo=true`를 별도로 실행하여 **`BUILD SUCCESSFUL in 8s`, 12 tasks = 7 executed + 5 up-to-date**를 확인했다. 대상은 프로젝트 내부 **`build/g29-probe/maven-repo`**로 제한되며 `dev/fabric-amper/settings/dev.fabric-amper.settings.gradle.plugin/0.1.0/` 아래 **실제 settings plugin marker POM이 존재**한다. 게시된 플러그인 JAR의 SHA-256도 위 최종 `build/libs` 바이너리와 **동일한 `F36EC2A9465A8E0801D342CCDFAD8D3952B25777E8F781DEAA717A50E2DBDC3D`**로 검증되었다. 따라서 README의 settings-only 부트스트랩을 **해당 로컬 Maven 저장소를 통해 실제 사용할 수 있는 상태**다. **공개 Gradle Plugin Portal 게시·정식 공개 출시는 수행되지 않았으며**, 게시 작업 뒤 **다시 `clean`을 실행하지 않아 로컬 게시물은 보존**되었다. 이 게시 작업은 최종 JUnit 187개와 **별도의 Gradle 실행**이다.

### 20.2 새 외부 충돌/side 계약 opt-in: 실제 JUnit 두 개 모두 별도 1 PASS

| 별도 독립 실행 | 실제 근거 | 정확한 수락 수준 |
| --- | --- | --- |
| **G19: 진짜 외부 Loom 1.18.4 선적용** | 새 `FabricAmperG19RealLoomCollisionConsumerTest` **selected JUnit 1 PASS, 0 FAIL/ERROR/SKIPPED**, outer **`BUILD SUCCESSFUL in 1m 46s`**. **Gradle 9.7.0**, **`withPluginClasspath()` 없이** 게시한 플러그인/독립 settings-only 소비자. init-script **class CodeSource와 적용한 Loom plugin instance CodeSource가 모두 실제 `net.fabricmc/fabric-loom/1.18.4/.../fabric-loom-1.18.4.jar`**임을 검증한 뒤, **FA010 `Loom plugin conflict`가 구성 시점에 발생하여 음성 소비자가 예상 EXIT 1**. 별도 동일 published-Amper 정상 대조군은 **번들 Loom 1.17.21 `help` PASS**. 다른 경로를 실수로 로딩한 설정은 테스트가 거부 | **엄격한 서로 다른 실제 외부 Loom 버전 충돌 진단 PASS(선별)**. §18의 **codeSource가 번들 1.17.21로 잘못 잡혔던 INVALID 시험**은 그대로 유지하되, **이번 진짜 1.18.4와 대조한 시험은 유효**하다. **실제 외부 KGP 버전/중복 classloader 충돌은 NOT VERIFIED** |
| **G27: 비서버 환경/클라이언트 전용 범위** | `FabricAmperG27SideConsumerTest` **FULL+G27 opt-in XML 1 PASS, 0 FAIL/ERROR/SKIPPED**, timestamp **2026-11-11T17:14:35.709Z**, outer **`BUILD SUCCESSFUL in 1m 50s`**, **8 tasks**. **settings+YAML 전용 독립 Java 소비자 3개**: ① `mod.environment: "*"`에서 client resource 전용 Mixin을 전역 선언하면 **`FA009 mod.yaml:16:15 mod.mixins[0].config`로 예상 실패**; ② common Java가 `target:client` + `clientCompileOnly`의 `commons-collections4` `HashBag`을 import하면 **`package ... does not exist` 예상 javac 실패**; ③ 정상 split 소비자가 main/client entrypoints와 client-only `@Mixin(Minecraft.class)`, `HashBag` 호출을 컴파일하고 배포 JAR 검증 **PASS** | ③에서 **구성 미러 = 생성 JSON = 배포 JAR 메타데이터 원본 바이트 일치**, **루트 `fabric.mod.json` 1개**, client Mixin 파일 **정확히 1개·`environment: client`·원문 바이트 유지**, client-only 라이브러리 클래스는 **JAR에 없음**·Loader `depends`에도 **없음**. **G27 비서버 컴파일·배포 및 FA009 경계 PASS(선별)**. **실제 전용 서버 `runServer`는 EULA 미승인으로 NOT RUN**, 클라이언트는 `0xC0000409` 불안정으로 재실행하지 않음 |

**독립 증거 보관 위치:** `/vbc/Fabric Amper/analysis/acceptance-evidence-2026-11-12/`를 **최종 `clean` 이전**에 Prime이 복사 보존했다. **`G19-real-external-Loom-1PASS.xml`**은 `tests="1" skipped="0" failures="0" errors="0"`이며 **`G19-real-external-Loom.txt`**에 독립 명령·진짜 class/instance CodeSource·기본 Loom 1.17.21 대조 기록이 있다. **`G27-1PASS.xml`** 역시 `tests="1" skipped="0" failures="0" errors="0"` 및 위 FA009 위치 증거를 포함한다. 이 두 XML은 **QA가 직접 읽어 확인**했다. 같은 폴더의 **`README.md`**는 이전 실제 **G10 JIJ 양성/음성 증거, G15 초기 tick PASS와 나중 native FAIL, G17 main/client 증거와 native FAIL**의 보존 위치·한계를 설명한다.

**최종 기본 XML과 opt-in의 차이:** 최종 `build/test-results/test/TEST-dev.fabricamper.integration.FabricAmperG19RealLoomCollisionConsumerTest.xml`과 `...FabricAmperG27SideConsumerTest.xml`은 각각 **1 SKIPPED**(정규 기본 빌드 opt-in 게이트 OFF), 보관된 선별 XML은 각각 **1 PASS**다. 이는 서로 다른 Gradle 실행이며 **187 기본 집계에 2 PASS를 가산하지 않는다**. G19의 **예상 EXIT 1 충돌 소비자**와 G27의 **예상 FA009·javac FAIL 음성 fixture**는 **TestKit JUnit 전체 PASS를 구성하는 올바른 부정 검증**, 제품/플러그인 기본 빌드 실패가 아니다.

### 20.3 `specs.md` G1~G30 + G17a 최신 수락 매트릭스 (이 시점의 사실만)

**분류 규칙:** `PASS(선별)`은 해당 TestKit/실소비자 하위 시나리오가 실제 통과했다는 뜻; `PARTIAL`은 명세의 중요한 하위 수락이 남았다는 뜻; `NOT RUN`은 실제 환경 검증이 없음; `BLOCKED`는 미승인 EULA·IDE 등 필요한 조건이 확보되지 않음. **원본 §9·§17의 당시 상태표는 수정하지 않고** 최신 상태를 이 절에만 추가한다.

| ID | 최종 확인한 실측 | 미충족 조건·수락 판정 |
| --- | --- | --- |
| **G1** | settings-only 독립 소비자 `help` **PASS(선별)** | 해당 기초 적용 계약 실측 |
| **G2** | `tasks --all` 필수 태스크 등록 **PASS(선별)** | 태스크 목록과 실제 게임 실행을 구별 |
| **G3** | 실제 소비자 JAR, 컴파일 클래스·루트 JSON 1개 **PASS(선별)** | 제품 JAR과 플러그인 JAR 구별 |
| **G4** | YAML ID/version/archive 수정과 재빌드 JSON/JAR 갱신 **PASS(선별)** | IDE 편집 반영 별도 |
| **G5** | 워밍업 후 4개 build CC 저장→HIT→YAML 변경 MISS→HIT **PASS(선별)** | Java 설정 변경·완전 cold Loom TTL **PARTIAL** |
| **G6** | Tooling API IdeaProject/모듈 및 160 dependency **PASS(부분)** | **실제 IntelliJ GUI 최초·수동 Sync NOT RUN/BLOCKED** |
| **G7** | Java↔Kotlin 활성/비활성, Kotlin 컴파일/JAR/adapter **PASS(선별)** | 비활성 복귀 후 실제 최종 Java JAR 재빌드 **PARTIAL** |
| **G8** | MC26.3+Yarn에 FA004 **PASS(선별)** | 해당 음성 오류 계약 |
| **G9** | 별도 JDK21/MC1.21.11 `remapJar`·클래스·루트 JSON **PASS(선별)** | **레거시 Loader 게임 런타임·선택 프로파일 전체 NOT RUN/PARTIAL** |
| **G10** | Maven Commons Collections 실제 Loader JIJ 호출·음성 대조 **PASS**; Modrinth Mod Menu client compile-only **PASS(선별)** | 모든 exported/runtime-only scope 및 **Modrinth 실제 runtime NOT RUN/PARTIAL** |
| **G11** | FA 진단 단위/빠른/실제 G16 Class Tweaker 유효·무효 멤버 **PASS(부분)** | 전체 YAML 오류·출력/원본 보존 행렬 **PARTIAL** |
| **G12** | 기존 main/client `fabric.mod.json` FA008 거부·원본 보존 **PASS(선별)** | 해당 음성 계약 |
| **G13** | **Gradle9.7.0+Loom1.18.4** 및 **9.9.1+Loom1.19.4** 각각 실제 Java 소비자 full `build`·JAR **1 PASS씩** | 정확 조합 Java 빌드 PASS; 선택 KGP/IDE/Loader runtime 매트릭스 **PARTIAL** |
| **G14** | 단일 Amper settings plugin으로 Loom·선택 KGP 연결 **PASS(선별)** | 다른 플러그인 버전 충돌은 G19 별도 |
| **G15** | 이전 실제 MC26.3 Loader0.20.1 client **main/client/tick PASS**; 이후 단순 독립 control **1 FAIL** `0xC0000409` | **최신 틱 재현성 불안정**, dedicated `runServer` **NOT RUN/BLOCKED** |
| **G16** | 실제 Loom `validateAccessWidener` 유효 Class Tweaker 성공·없는 멤버 예상 실패 **1 PASS** | 런타임 클래스 적용은 G15/G17과 별개 |
| **G17** | `loom:injected_interfaces` custom-only 음성 javac 실패/양성 직접 호출 컴파일 **1 PASS**; Loader CT·main 기본 메서드 증거 | **두 독립 게임 런타임 JUnit 각각 1 FAIL**(native `0xC0000409`, tick 없음), IDE **NOT RUN → PARTIAL/FAIL 기록 유지** |
| **G17a** | 태스크 출력 없는 구성 시점 Loom 미러 `help` **PASS(선별)** | 실제 IDE Sync **NOT RUN/PARTIAL** |
| **G18** | client compile-only 라이브러리 Java 컴파일 성공/common javac 예상 실패 등 **2 PASS** | 실제 서버 Loader 환경은 G27 별도 |
| **G19** | **실제 외부 Loom1.18.4 CodeSource+instance** 선적용 시 **FA010 예상 실패**와 번들 Loom1.17.21 정상 대조 **1 PASS** | 실제 다른 **KGP** 충돌·모든 classloader 조합 **NOT RUN/PARTIAL**. §18 잘못된 구버전 probe **INVALID** 역사 보존 |
| **G20** | JSON 미러 삭제·손상 복구, 병렬 CC JSON/icon 원자 복구 **PASS(선별)** | 실제 IntelliJ Sync·모든 참조 리소스 동시성 **PARTIAL** |
| **G21** | mirror/generated/JAR metadata 바이트 동일·단일 JSON **PASS(선별)** | 반복 clean 소비자 JAR 결정적 SHA 비교 **PARTIAL** |
| **G22** | group/version/archivesName, `jar`·`sourcesJar` 실제 소비자 **PASS(선별)** | 명시 소비자 계약 PASS |
| **G23** | YAML JUnit Jupiter 소비자 테스트 발견·실행/결과 XML **PASS(선별)** | 선택 Jupiter 계약 PASS |
| **G24** | Mixin/CT/icon 미러·초기 JAR, 내용주소 snapshot 변경 **PASS(선별)** | 원본 수정 후 실제 JAR 재빌드·IDE Sync **PARTIAL** |
| **G25** | 실제 Loom1.19.4 native `.module`: Gradle9.7.0 **예상 API9.8 variant 거부**, 9.8/9.9.1 성공, **JUnit 3 PASS** | **9.7 호환 실행 성공 아님**, plugin variant 판정 PASS(선별) |
| **G26** | 공식 Fabric Loader0.20.1 버전 조건 parity **11/11 PASS**·사용자 조건 충돌 진단 | 다른 Loader 런타임/추가 조건 조합 **PARTIAL** |
| **G27** | **실제 비서버 settings-only Java 소비자 3 fixture, JUnit 1 PASS**: FA009, common/client javac 경계, client-only Mixin/JAR/Loader metadata 정확성 | **dedicated `runServer` 미실행(EULA 미승인)**, 클라이언트 재실행 미실시 → **PARTIAL/BLOCKED** |
| **G28** | IDE-free Gradle Tooling API IDEA 모델 **PASS(부분)** | **IntelliJ GUI 최초 import, 수동·YAML 저장 후 자동 Sync NOT RUN/BLOCKED**, 수동 Reload 안내 README 기재 |
| **G29** | 두 독립 empty `-g` 캐시: Kotlin off/on 모두 KGP2.4.21 JAR 다운로드, 캐시 크기/소요 시간 계측 **PASS(측정)** | Kotlin off에서도 KGP 정적 전이 비용 존재; **지연 opt-in 미출하·미검증**, cache byte ≠ wire byte |
| **G30** | UTF-8/alias/tag 등 오류 경계 단위 검증, `mod.yml` 추가 CC MISS+FA001, 삭제 후 CC HIT/빌드 복구 **PASS(선별)** | 전체 악성 YAML/중복 ZIP/출력 보존 모든 조합 **PARTIAL** |

### 20.4 보존된 외부 증거·README·명시적 차단 및 최종 출하 결론

**독립 증거의 출처:** `analysis/acceptance-evidence-2026-11-12/`에는 **G19/G27 선택 1 PASS XML·G19의 실제 다른 CodeSource/대조 텍스트·G10 positive/negative 실제 Loader·nested JAR 라이브러리 호출 증거·G15 초기 tick PASS·G15 이후 native control FAIL·G17 main/client 및 native FAIL 기록**이 `README.md`의 한계 설명과 함께 보존돼 있다. 이 보관 폴더는 `clean` 때문에 덮어써지는 JUnit XML/런타임 proof의 **기존 실제 스냅샷**이지 새 TestKit 실행이 아니다. 최신 기본 **29 XML suites 187 tests**와 이 선별 결과는 **서로 다른 실행**이다.

**한국어 `README.md`:** Prime이 **초기 bootstrap, settings-only+YAML 최소 프로젝트, 로컬 Maven 게시, 빌드·실행·설치 경로, IntelliJ 수동 Reload, EULA·격리/보안 주의, 현재 미출시 제한**을 작성했다. 실제로 읽은 `README.md:105-123`은 IntelliJ GUI Sync가 **NOT RUN**임을 밝히고 **187=158 PASS+29 SKIPPED**, 최신 플러그인 JAR 크기/해시, 게임 틱 네이티브 재현성 위험을 공개한다. README 작성 자체가 수락 PASS나 Portal 공개 게시를 뜻하지 않는다.

**미해소된 핵심 게이트:** **G6/G28** 실제 IntelliJ 최초 import·수동/자동 Sync 미실행(IDE 런처 안전성 위험으로 무리한 GUI 실행 안 함); **G15/G27** 실제 dedicated server `runServer` **NOT RUN, EULA 승인 없음**; **G17** Loader 런타임 게임 tick **두 별도 JUnit FAIL** 및 같은 native `0xC0000409`를 G15 단순 대조군에서도 관측, **원인 미확정**; **G19 실제 외부 KGP** 충돌 미검증; **FA011** 온라인 결측 Maven 정확 진단·오프라인 거짓 양성 방지는 일부 PASS이나 **TLS/Modrinth/CC 전 범위 오류 구분 NOT VERIFIED**; **G9** 레거시 프로필 게임 런타임 미검증; **G29** lazy-KGP opt-in은 검증/출하되지 않음. 추가로 G10의 다른 범위, G20 IDE, G24 최종 리소스 수정 뒤 JAR 갱신 등 부분 수락이 남는다. 네이티브 `0xC0000409`의 원인을 **G17 주입 결함 또는 시스템 문제로 단정하지 않는다**.

**최종 QA 판정:** **플러그인 자체 최종 클린 빌드 및 `validatePlugins` PASS**, 실제 독립 소비자·Loader·메타데이터·병렬 캐시·Loom 버전·side 격리에 의미 있는 **선별 PASS**가 확보됐다. 하지만 **187개 중 29 SKIPPED**, 실제 런타임 FAIL 및 미검증 출시 게이트가 존재한다. 따라서 `specs.md`의 **G1~G30 모두 완전 수락 / 정식 출시 완료는 `NOT APPROVED`**로 기록한다. **컴파일·플러그인 빌드 성공을 실제 IDE/서버/게임 런타임 전체 성공으로 확대하지 않는다.**

**§20 소유권·변경 범위:** 오직 `/vbc/Fabric Amper/QA_REPORT.md`의 맨 끝에 **append**. **기존 §1~§19 원문 첫 860행, 과거 빌드 실패·native FAIL·JAR SHA-256을 변경하지 않음.** QA 작업자 6은 **Gradle/테스트/빌드/프로세스 종료 명령을 실행하지 않았고**, README·소스·XML·프로젝트 설정·배포 산출물을 **수정하지 않았다**.

---

## 21. Fabric API/Library 제품·Maven 전이 게시·독립 Draft-07 스키마 신규 수락과 최신 오프라인 빌드 (2026-11-13)

> **역사적 증거 보존:** §1~§20의 **기존 947행 원문을 그대로 보존**한다. §20의 Gradle 9.7.1/JDK25 **187 tests = 158 PASS + 29 SKIPPED**, 당시 플러그인 **218,069 bytes / SHA-256 `F36EC2A9465A8E0801D342CCDFAD8D3952B25777E8F781DEAA717A50E2DBDC3D`**, 이전 실제 Loader 성공·네이티브 실패·중간 fixture 실패도 해당 시점의 참값이다. 본 절만 **2026-11-13 신규 제품 구현 후** 증거를 기록하며 과거 값을 새 산출물 값으로 소급 치환하지 않는다. QA 작업자 6은 Gradle·테스트·배포 명령을 실행하지 않았고 **이 보고서에만 append**했다.

### 21.1 요구사항·연구·README 선행 계약 및 변경 소유 범위

사용자가 기존 settings-only `fabric/mod` 계약을 훼손하지 않으면서 **`product: fabric/api`와 정확한 별칭 `product: fabric/library`**, Java API 라이브러리 자동 설정·Maven 게시·진실한 POM/Gradle Module Metadata 전이성, 프로젝트 루트 독립 SchemaStore용 JSON Schema를 요청했다. Prime은 구현에 **앞서** `README.md`의 **「라이브러리/API 제품 구현 계약 (2026-11-13)」**을 작성했고, **4개 연구 작업자**가 owo-lib·GeckoLib·Gradle Java Library/Maven Publish/Module Metadata·SchemaStore/YAML Language Server 등의 공개 원본 문서를 검토했다. **README 선행 계약과 연구 완료는 구현 수락이 아니므로** 실제 아래 검증과 구분한다.

**변경된 생산 범위(Prime/각 소유 작업자의 변경, QA 담당자 변경 아님):** `src/main/kotlin/**/config/**`의 제품/게시/엄격한 YAML 검증 모델, `src/main/kotlin/**/plugin/**` 및 `loom/FabricAmperPublishingConfigurer.kt`의 Java 라이브러리·게시 wiring, `generator/ValidateFabricAmper.kt`의 제품별 검증, 신규·기존 `unit/**` 및 opt-in `integration/FabricAmperApiPublishingConsumerTest.kt`, 신규 **루트 `fabric-amper.schema.json`**, README 문서화. **`fabric/mod` 일반 모드는 자동 `maven-publish`가 없으며**, API/Library 제품만 `java-library`와 `maven-publish` 및 `mavenJava` publication을 자동 구성한다. 소비자 작성 파일은 변함없이 `settings.gradle.kts`와 `mod.yaml`/`mod.yml`; 소비자 `build.gradle(.kts)`·직접 작성 `fabric.mod.json` 없음.

### 21.2 신규 독립 API/Library 실제 게시·다운스트림 컴파일 TestKit

| 단계 | 실측 및 검증 사항 | 정확한 판정 |
| --- | --- | --- |
| **실행 범위** | `FABRIC_AMPER_TESTKIT_FULL=true`와 `FABRIC_AMPER_TESTKIT_API_PUBLISHING=true` 선택 opt-in **`FabricAmperApiPublishingConsumerTest` JUnit 1 test / 1 PASS / 0 SKIPPED / 0 FAIL / 0 ERROR**, outer Gradle **`BUILD SUCCESSFUL in 1m 4s`**. 보관 XML: `analysis/acceptance-evidence-2026-11-13/API-library-publishing-1PASS.xml`; **SHA-256 `1C622BC7116F628BC8651F3041CEBC9D16A19DD8C000B64DB0648583AC856DEA`** | QA가 보관된 XML **`tests="1" skipped="0" failures="0" errors="0"`**와 핵심 테스트 소스를 직접 읽었다. 이 실행은 최신 기본 클린 빌드의 선택 게이트 **SKIPPED**와 **별도**이며 **205개 기본 테스트에 PASS 1을 가산하지 않는다** |
| **생산자 A — 두 제품 독립 fixture** | **`fabric/api`와 `fabric/library` 각각** settings-only YAML 소비자에서 `publishMavenJavaPublicationToFabricAmperLocalRepository`를 **실제 수행**. 각 소비자의 자체 `build/fabric-amper/maven-repository/` 아래 **`groupId:artifactId:version` GAV 디렉터리**, 최종 배포 **JAR 1개 + sources JAR 1개 + POM 1개 + Gradle `.module` 1개** 확인. Loader 루트 `fabric.mod.json` **정확히 1개**, 생성 메타데이터/최종 배포 JAR 일치, Loader `jars[].file`로 참조되는 **실제 nested JAR** 포함. 두 별칭의 POM 스코프, API/runtime module variant, 생성 Loader JSON 및 배포 JAR entry 목록 동일 | **API/Library 별칭의 실제 로컬 Maven 게시·패키징 및 일치성 PASS(선별)**. 개발용 중간 JAR이 아니라 **실제 배포 JAR 바이트**가 게시되었고 sources를 중복 게시하지 않는지 검증 |
| **POM·Gradle Module Metadata의 스코프 정확성** | **공개 `exported: true` API 의존성 → POM `compile`, Gradle API/runtime 모두**; 내부 `implementation`·`runtime-only`·`include` 의존성 → POM `runtime`, Gradle runtime만; **`compile-only`·`test-dependencies` → POM 및 게시 API/runtime variant에 없음**. `include` nested 라이브러리 JAR과 POM 의존성 중복·전이 혼동도 독립 확인 | **선별 fixture의 POM 전이 스코프·`.module` API/runtime visibility PASS**. Maven POM이 모든 client-side Gradle variant를 완전 재현한다거나 모든 실제 외부 Maven의 전이 POM이 정확하다는 주장은 하지 않음 |
| **독립 소비자 B의 긍정·부정 컴파일** | B는 A의 공개 의존 모듈을 **직접 선언하지 않은 채** 게시된 A를 의존해 실제 **공개 전이 타입 `PublicToken`을 참조하는 Java 소스 `compileJava` SUCCESS**, 다운스트림 compile/runtime classpath 차이 확인. 별도 **음성 B**에서는 `InternalHelper` 및 `CompileMarker`(implementation/compileOnly) import의 실제 `javac` **`cannot find symbol`/패키지 부재**가 예상대로 발생 | **공개 API만 다운스트림 컴파일에 노출, 내부/compileOnly 타입은 숨김 PASS**. 부정 fixture의 **의도된 `compileJava` FAIL**은 **JUnit 1 PASS를 구성하는 엄격한 기대 결과**, 제품 빌드 실패가 아님 |
| **부작용 격리** | 생산자·소비자 각각 **소비자 `build.gradle(.kts)` 없음**. 게시 위치는 **fixture 자체 프로젝트의 로컬 Maven**으로 제한. `publishToMavenLocal`, 사용자 `~/.m2`, remote Maven 업로드 **없음**; 프로젝트 구성/소스 설치만으로 원격 게시가 발생하지 않음 | **안전한 로컬 게시 작업·settings-only 계약 PASS(선별)**. 원격 HTTPS credentials 업로드/실서버, 레거시 remap API 라이브러리의 Maven 배포 JAR·sources variant, 별도 Kotlin/IDE/Loader 런타임은 **NOT VERIFIED** |

**이전 opt-in 실패 2회는 삭제하지 않음(제품 결함으로 오인 금지):** (1) fixture가 `RepositoriesMode.PREFER_SETTINGS`를 사용하면서 Loom이 프로젝트에 추가하는 **Mojang 등 필수 Maven 저장소를 억제**하여 `authlib` 등 의존성을 찾지 못했다. (2) 교정된 fixture에서 사용한 settings Kotlin DSL `gradle.beforeProject(org.gradle.api.Action { project -> ... })`/중첩 `Action` 구문은 **Kotlin DSL 스크립트 컴파일 오류**였다. owner-12는 **fixture만** `settings.gradle.kts`의 자연스러운 **`gradle.beforeProject { repositories { maven { url = uri(...) } } }`** 수신자 DSL로 고쳤다. **제품 코드·테스트 assertion을 약화하지 않은 세 번째 실제 선택 실행은 JUnit 1/1 PASS**. 첫 두 시도의 **TestKit FAIL 기록과 정상 제품 검증을 구별**한다. 소스 근거: `FabricAmperApiPublishingConsumerTest.kt:54-154,179-200,223-295`.

### 21.3 루트 Draft-07 스키마 및 안전 경계

| 대상 | 검증 증거 | 남은 범위 |
| --- | --- | --- |
| **독립 파일** | 프로젝트 루트 **`fabric-amper.schema.json` 18,039 bytes**, **SHA-256 `6D17EC0EB8C53134A261110673A51AE2E370B9B737CDBD833C3559042AA72ACF`** (Prime 검증). 실제 헤더 `"$schema": "http://json-schema.org/draft-07/schema#"`, required `schema/product/mod/minecraft`, 제품 enum **`fabric/mod`, `fabric/api`, `fabric/library`** | SchemaStore **공식 카탈로그 등록/외부 IDE 자동완성 실사용은 NOT RUN**; 파일 존재와 로컬 스키마 유효성만 증명 |
| **공식 validator + 정규·부정 예제** | Prime의 Python JSON 파싱 및 설치된 공식 **`jsonschema.Draft7Validator.check_schema`**, 실제 valid/invalid 예제 검증 **PASS**. `publishing`은 **API/Library 제품으로 제한**, API 필수 `mod.group`, 루트 필드·정의·오류 조건 및 원본 strict YAML parser 정렬 테스트. 최신 JUnit **`FabricAmperSchemaContractUnitTest` 6/6 PASS**, **`FabricLibraryProductContractUnitTest` 5/5 PASS** | **JSON Schema 주석 `default`는 런타임 값을 넣지 않는다**. 스키마 검증은 실제 Kotlin decoder/검증기와 완전히 동등한 추상 실행을 자동 보장하지 않으므로 신규 사양은 지속 회귀 검증 필요 |
| **게시 검증·보안 단위** | 최신 **`FabricLibraryPublishingContractUnitTest` 6/6 PASS**. HTTPS만 허용하는 remote URL, credential/쿼리·fragment/인라인 비밀 차단, 암호 값 대신 환경변수 **이름** 사용, 잘못된 게시 키·`fabric/mod`에서 `publishing` 거부, client-only 의존성의 전역 Loader depends/API 전이 금지 등 검증 | **실제 외부 remote Maven 인증·TLS 전송·실패 분류는 NOT RUN**. 테스트 통과를 비밀정보 전혀 누출되지 않는다는 모든 경로 보증으로 확대하지 않음 |

### 21.4 구현 후 최종 오프라인 클린 빌드·JAR·로컬 재게시

| 검증·산출물 | Prime 신규 실제 결과 |
| --- | --- |
| **환경과 명령** | Gradle **9.7.1/JDK 25**, **`clean build validatePlugins --offline`** |
| **빌드** | **EXIT 0, `BUILD SUCCESSFUL in 26s`**, `validatePlugins` **PASS** |
| **최종 기본 JUnit** | **33 XML suites, 205 tests = 175 PASS + 30 SKIPPED + 0 FAIL + 0 ERROR** |
| **새 API 선택 TestKit 기본 실행** | 최종 기본 `build/test-results/test/TEST-dev.fabricamper.integration.FabricAmperApiPublishingConsumerTest.xml` **1 SKIPPED** (opt-in 게이트 꺼짐). 앞선 보관 **`API-library-publishing-1PASS.xml`**은 별도 실행에서 **1 PASS** |
| **배포 플러그인 JAR** | `build/libs/fabric-amper-0.1.0.jar` **238,950 bytes**, SHA-256 **`D30019F79AF7B4ADD0E549802F7A5051DF84BFE201F3A12AA0670F6CFDF61F55`** |
| **배포 플러그인 sources JAR** | `build/libs/fabric-amper-0.1.0-sources.jar` **54,917 bytes**, SHA-256 **`AC7BF70081D191E00BBB2645F36863C7C0AA26235D98163C189C7968109B968F`** |
| **플러그인 ZIP 무결성** | 최종 JAR의 **`dev.fabric-amper.settings` / `dev.fabric-amper.project`** plugin marker 및 Settings/Project 구현 `.class` **각각 정확히 1개**, **중복 ZIP entry 0개**(Prime 직접 검사) |
| **클린 뒤 프로젝트 내부 로컬 재게시** | `publishAllPublicationsToG29ProbeRepository -PfabricAmperProbeMavenRepo=true --offline`, **`BUILD SUCCESSFUL in 1s`**, **12 tasks = 7 executed + 5 up-to-date**, `build/g29-probe/maven-repo` 내부 settings marker POM 존재. 게시된 JAR 및 sources JAR **둘 다 최종 `build/libs` 파일과 byte-for-byte 동일**, 사용자 `~/.m2`/원격 저장소/Plugin Portal에 게시하지 않음 |

**수치 해석:** §20의 당시 **29 suites/187 = 158 PASS + 29 SKIPPED**와 §21의 새 **33 suites/205 = 175 PASS + 30 SKIPPED**는 **서로 다른 시간의 기본 클린 빌드**다. 새 17개 단위 테스트 PASS(제품 5·게시 6·스키마 6) 및 새 opt-in API TestKit 1 SKIPPED가 JUnit 증가와 일치하나, **선택 opt-in 1 PASS는 기본 205개 합계에 추가하지 않는다**. 앞선 두 TestKit fixture 실패와 §19의 실제 G17/G15 native 실패 역시 **최종 기본 빌드에 포함된 PASS로 해석하지 않으며**, 기존 이력을 보존한다. `--offline` 빌드 성공은 이미 준비된 의존성 캐시에서의 빌드 성공이지 완전 신규 인터넷 다운로드/원격 게시를 검증했다는 뜻이 아니다.

### 21.5 최신 기능 수락/출하 게이트 및 최종 결론

| 영역 | 이 시점의 수락 판정 |
| --- | --- |
| **새 `fabric/api` / `fabric/library`** | 두 제품 **별칭 일치·자동 `java-library`/`maven-publish`·실제 로컬 Maven JAR/sources/POM/.module·API vs implementation vs runtime-only vs include 전이·compileOnly/test 누출 방지·독립 하위 소비자 Java 컴파일 PASS(선별)** |
| **기존 `fabric/mod`** | 로컬 기존 YAML 계약·자동 게시 없음 단위/기본 빌드 **PASS(선별)**; 과거 G1~G30 개별 상태는 §20 기준 유지 |
| **스키마·문서** | 독립 Draft-07 JSON Schema 파싱·공식 validator·valid/negative 예제·unit **PASS(선별)**; **실제 SchemaStore 등록·IntelliJ/VS Code 자동 매핑·GUI import NOT RUN** |
| **새 API 제품의 남은 실환경** | **레거시 remap API 라이브러리의 Maven 배포·실제 로드**, 실 **외부 원격 Maven/TLS 인증**, Kotlin/분리 client variant 조합·Maven POM의 모든 제약, API 제품의 실제 게임 Loader 런타임은 **NOT VERIFIED** |
| **기존 출하 차단** | **G6/G28** IntelliJ GUI 최초/수동/자동 Sync **NOT RUN**; **G15/G27** 실제 dedicated `runServer` **EULA 미승인/NOT RUN**; **G17** 과거 두 실제 런타임 JUnit 각 **1 FAIL** `0xC0000409`(main 메서드 증거는 있으나 tick 없음), 별도 G15 대조군도 같은 native 종료; **G19** 실제 외부 KGP 충돌 **NOT VERIFIED**; **FA011** TLS/Modrinth/CC 전범위 분류 **NOT VERIFIED**; **G29** lazy KGP opt-in 미출하; G9 선택 레거시 게임/API 게시 런타임 제한 유지 |

**한국어 `README.md`**는 Prime이 새 제품 예제·정확한 Maven scope/보안 계약·로컬 게시 방법·SchemaStore/IDE 매핑 주의·기존 사용자 부트스트랩·이번 **205=175 PASS+30 SKIPPED**, 최종 JAR SHA와 미출시 제한까지 최신화했다. **README나 스키마가 존재한다는 이유로 공개 Gradle Plugin Portal·SchemaStore 카탈로그에 이미 등록됐다고 쓰지 않는다.**

**최종 QA 판정:** 새 API/Library 제품의 **엄격한 실제 로컬 게시 + 별도 다운스트림 Java 소비자 TestKit 1 PASS**와 루트 스키마 검증 및 신규 **205개 기본 회귀(175 PASS / 30 SKIPPED / 0 FAIL)**는 명확한 진전이다. 그러나 **30 SKIPPED는 PASS가 아니고**, 기존 실제 게임/IDE/전용 서버 출시 게이트와 신규 API의 레거시·원격 게시·런타임 게이트가 남는다. `specs.md` **G1~G30 전체 수락·정식 출시 승인 = `NOT APPROVED`**로 유지한다.

**§21 소유권·변경:** **`/vbc/Fabric Amper/QA_REPORT.md`에만 신규 절 append**, **§1~§20 최초 947행·이전 모든 해시와 실패 이력 변경 없음**. QA 작업자 6의 **Gradle·테스트·빌드·파일 게시 실행 0건**, 다른 소스·README·schema·XML·배포 파일 **수정 0건**.

---

## 22. 통합 `publish:` 원격 게시 구현·안전한 로컬 수락·최종 오프라인 출하 재검증 (2026-11-13)

> **역사적 보존:** 이 절은 §21까지 **기존 1,010행을 삭제·수정하지 않고 append**한 후속 결과다. §21의 당시 205 tests/238,950-byte JAR/18,039-byte schema 및 실환경 미검증 문장은 그때의 정확한 기록이며, 아래 더 최신 결과로 소급 교체하지 않는다. 새 기능은 실제 구현·로컬 동작 범위만 PASS로 표시하고 **Modrinth/CurseForge 실제 API 업로드는 사용자가 금지했으므로 절대 실행하지 않았다.**

### 22.1 사용자 추가 요구와 구현 경계

README의 **「파일을 작성하지 않고 배포하는 통합 `publish` 계약 (2026-11-13 추가 요구)」**을 **제품 코드 변경 전에** 작성하고, 4개 조사 작업자가 Modrinth Create Version API 및 CurseForge Author Upload API/Gradle 관행을 조사했다. 사용자 요구에 따라 원래 `settings.gradle.kts` + `mod.yaml`/`mod.yml`만으로 구성하는 계약과 `fabric/mod`·`fabric/api`·`fabric/library`를 유지하며 새 최상위 `publish.maven`·`publish.modrinth`·`publish.curseforge`를 구현했다. 기존 API/Library의 `publishing.repository` 호환성을 유지하고 중복 원격 Maven 설정은 `FA003`으로 거부한다. 소비자 `build.gradle(.kts)`와 직접 작성한 `fabric.mod.json`은 요구하지 않는다.

**생산 변경:** `config/ModYamlModel.kt`, `ModYamlDecoder.kt`, `ModYamlValidator.kt`의 게시 모델·엄격한 validation; `plugin/FabricAmperProjectPlugin.kt`, 신규 `FabricAmperPublishMetadata.kt`, `FabricAmperPlatformUploadTask.kt`, `loom/FabricAmperPublishingConfigurer.kt`의 자동 Java/Maven publication·선택 플랫폼 task; 신규 `util/PlatformReleaseClient.kt`의 Gson metadata 및 고정 HTTPS multipart streaming; `fabric-amper.schema.json` Draft-07 확장; 해당 unit/integration Tests; README 실사용 안내. 실제 배포 artifact는 26.3 non-remap `jar`, legacy 1.21.11 `remapJar`이며 검증 태스크를 선행한다. `build`/`check`/`help`에서 토큰 또는 외부 게시를 실행하지 않고 **명시적 게시 작업의 action에서만** token Provider를 읽는다.

Modrinth `POST https://api.modrinth.com/v2/version`은 raw `Authorization`, JSON multipart `data`+배포 JAR `file`을 구성한다. CurseForge Author `POST https://minecraft.curseforge.com/api/projects/{numericId}/upload-file`은 `X-Api-Token`, `metadata`+`file`을 구성한다. 엔드포인트 고정, redirect 금지, POST 자동 재시도 금지, 응답 본문 폐기, 토큰/HTTP body 비출력은 정적 코드·순수 JSON unit 범위의 확인이며 **실제 플랫폼 연결·서버 수락·권한·HTTP 상태·TLS 검증으로 확대하지 않는다.** 원격 Maven `PublishToMavenRepository.doFirst` credentials Provider의 **실제 HTTPS 인증 및 configuration-cache 재실행 시 정확한 동작 역시 NOT VERIFIED**다.

### 22.2 새 게시 TestKit 및 기존 실제 소비자 회귀 — 보관 XML

| 테스트/증거 | 실제 수행 및 결과 | 안전 경계 |
| --- | --- | --- |
| `FabricAmperPublishPlanConsumerTest` | `fabric/mod`, `fabric/api`, `fabric/library` 각각 실제 settings-only `help`/`tasks --all`/`build`, 유효 JAR·Loader JSON·검증 태스크, 게시 `--dry-run`의 **3 PASS / 0 FAIL / 0 SKIP**. 보관 `analysis/acceptance-evidence-2026-11-13/PUBLISH-plan-3PASS.xml` | 예시 Maven `.invalid` endpoint와 가짜 플랫폼 프로젝트 ID; **HTTP 업로드·실제 원격 Maven 게시 0건** |
| **신규 platform-only `fabric/mod`** | `publish.maven`·`mod.group` 없이 Modrinth·CurseForge만 명시. 실제 Java 빌드·metadata/JAR 1개, `publishFabricAmperModrinth`/`CurseForge`/`publishFabricAmper`/`publish` `--dry-run`, Maven task·local Maven repo 부재 확인. **JUnit 1 PASS / 0 FAIL / 0 SKIP**, outer **BUILD SUCCESSFUL in 1m 17s**. `PUBLISH-platform-only-1PASS.xml` SHA-256 `B0E4F795DCC69FF6F2248D32EE7DA9571D54DEC8C8D711B54DFFCBB6136C24ED` | 플랫폼 upload action 모두 **SKIPPED**; 전송 0건 |
| **신규 API 실제 로컬 Maven configuration-cache 4-run** | `FabricAmperApiPublishingConfigurationCacheConsumerTest`: `help` warm-up 후 **동일 실제 `publishMavenJavaPublicationToFabricAmperLocalRepository --configuration-cache` 4회**: **STORE→HIT→YAML(version/description) 변경 MISS/STORE→HIT**. 초기·변경 GAV/POM/.module/JAR/sources, root Loader JSON, 생성 JSON와 content-addressed 미러 바이트, 이전 GAV 보존·새 배포 바이트 변경까지 확인. **JUnit 1 PASS / 0 FAIL / 0 SKIP**, outer **BUILD SUCCESSFUL in 1m 2s**. `API-local-publish-CC-4run-1PASS.xml` SHA-256 `C112192443CDD7AA584E6067A94C48F0A0A07476B3E2D1FBB251B484E8DB9356` | 독립 소비자의 `build/fabric-amper/maven-repository`만 실제 게시; `publishToMavenLocal`·HTTPS·플랫폼 upload 0건 |
| **G9 legacy API 실 게시** | Minecraft 1.21.11/Java21 API 제품 실제 local Maven에서 게시 JAR=`remapJar`, sources=`remapSourcesJar` 바이트 동일; 개발용 unremapped JAR과 구분, 다운스트림 Java21 공개 API 컴파일. **1 PASS**, `G9-legacy-api-maven-1PASS.xml` | 레거시 게임 런타임/원격 게시 PASS 아님 |
| **G24 리소스 재패키징** | Mixin JSON/Class Tweaker/PNG icon 변경 전후 두 JAR과 metadata·mirror 일치, 변경된 실제 JAR 바이트 및 root JSON 1개 **1 PASS**, `G24-repackage-1PASS.xml` | Fabric Loader 게임 실행과 구분 |
| **API/library Maven 전이 회귀** | 두 라이브러리 별칭의 로컬 Maven JAR/sources/POM/.module, 공개 API compile, 내부/compileOnly/test 비전파, JIJ 및 독립 Java consumer **1 PASS**, `API-regression-after-publish-1PASS.xml` | 기존 신규 publish가 API 라이브러리를 훼손하지 않았음을 선별 입증 |
| **G5 configuration cache 회귀** | 기존 실제 소비자 YAML 편집에 따른 STORE→HIT→MISS→HIT **1 PASS**, `G5-CC-after-publish-1PASS.xml` | 업로드 없음 |

모든 위의 XML은 `analysis/acceptance-evidence-2026-11-13/` 아래 독립 보관됐고 **기본 클린 빌드 242 tests 합계에 이 opt-in 선택 실행의 PASS를 다시 더하지 않는다.** 기존 §21 API/Library 원본 실제 게시 `API-library-publishing-1PASS.xml`도 그대로 보존했다.

### 22.3 보안 READ-ONLY 감사에서 확인한 불일치와 실제 수정

1. **확정 결함 — 업로드 파일명 검증:** `ModYamlValidator`의 합법적 `mod.archivesName`은 `_`, `-`, `.`로 시작할 수 있지만 `PlatformReleaseClient.safeFilename`은 영문·숫자로 시작하지 않으면 `LOCAL_ARTIFACT`를 반환했다. 유효 JAR 업로드가 로컬에서 실패할 수 있었으므로 외부 요청 전에 사용하는 **순수 `isSafeReleaseJarFilename()`**로 통일해 유효한 선두 마커·일반 버전 punctuation을 허용하고 경로 separator·quote·제어문자·소스/개발/Javadoc JAR·길이 제한을 유지했다. 신규 `PlatformReleaseJarFilenameUnitTest` **3개 PASS**(최종 기본 빌드 포함). 실제 HTTP POST 테스트하지 않음.
2. **확정 결함 — 구형 Maven 자격증명 쌍:** `publishing.repository.usernameEnv` 또는 `passwordEnv` 하나만 작성했을 때 plugin wiring에서 늦게 실패했다. 구성 단계 `ModYamlValidator`에 양쪽 동시 지정 또는 둘 다 생략 규칙을 `FA003 publishing.repository`로 추가하고 **Draft-07 schema의 상호 `if/then required`**를 동기화했다. `FabricLibraryPublishingContractUnitTest`, `FabricAmperSchemaContractUnitTest`에 양성/음성 회귀 추가 및 PASS.
3. **`publish` 의미 차이:** `publishFabricAmper`는 YAML에서 선택한 대상만 모은다. 하지만 API/Library 제품에서 Gradle **표준 `publish`는 `maven-publish`의 프로젝트 로컬 `fabricAmperLocal` 게시도 포함**한다. 같은 원격 task가 단일 그래프에서 두 번 POST한다는 결함은 확인되지 않았다. README의 두 명령 의미와 예상 부작용을 정확히 구별하도록 수정했다.
4. **추정만 있는 위험은 PASS/결함으로 단정하지 않음:** remote Maven `doFirst` credential mutation/Gradle9 CC 재수화, symlink TOCTOU, 실제 HTTP 상태별 응답·TLS·인증·API 메타데이터 수락은 **실환경 NOT VERIFIED**. 사용자 금지에 따라 HTTP 성공/실패를 증명하려는 시험도 수행하지 않았다.

### 22.4 실패 기록 보존 후 최종 클린 빌드·산출물·로컬 재게시

| 항목 | 최종 실측 |
| --- | --- |
| **빌드 환경** | Windows, Gradle **9.7.1**, Java **25**; 프로젝트 `/vbc/Fabric Amper` 내부만 접근. Gradle 병렬 외부 실행 없음 |
| **중간 문제 1** | 신규 단위 테스트 첫 선택 시도는 Kotlin 증분 cache `source-to-classes.tab already registered` 오류로 실패. 제품 assertion 실패 아님. 프로젝트 내 `clean` 및 `-Pkotlin.incremental=false --no-daemon`으로 재실행해 **선택 unit BUILD SUCCESSFUL in 18s**. 오류를 삭제하거나 PASS로 소급하지 않음 |
| **중간 문제 2 — 실제 새 플러그인 결함** | 첫 `clean build validatePlugins --offline`: `:test`는 **41 XML suites, 240 tests=203 PASS+37 SKIP+0 FAIL/ERROR**, 그러나 `:validatePlugins`가 `FabricAmperPlatformUploadTask`의 **Not cacheable without reason**으로 FAIL. 클래스에 **`@DisableCachingByDefault(because = "External publishing has side effects and must not be cached")`**를 추가해 외부 부작용 업로드의 캐싱 금지를 명시. 절대 `@CacheableTask`로 우회하지 않음 |
| **최종 재실행 명령** | `clean build validatePlugins --offline --console=plain --no-daemon -Pkotlin.incremental=false` |
| **최종 클린 판정** | **EXIT 0 / BUILD SUCCESSFUL in 41s**, `validatePlugins` PASS |
| **기본 전체 JUnit** | **42 XML suites, 242 tests = 205 PASS + 37 SKIPPED + 0 FAIL + 0 ERROR**. 37 SKIP는 선택 옵트인 게이트로 **PASS가 아님** |
| **플러그인 바이너리** | `build/libs/fabric-amper-0.1.0.jar` **314,704 bytes**, SHA-256 **`164CADA56DF0BEFBFB0AFABAE574F639A89795DFD86CA222AA8789F44C829F0A`** |
| **소스 JAR** | `build/libs/fabric-amper-0.1.0-sources.jar` **67,202 bytes**, SHA-256 **`2032A41383A3534B2FF0B087586B968189C752D74029A545E95ACAA747F2B119`** |
| **SchemaStore용 JSON** | 루트 `fabric-amper.schema.json` **26,823 bytes**, SHA-256 **`D5FD84098F1FD6AB926D504F23C69EC991AD17CBCF127B4C14308672B1BC5233`**. Python 실제 `jsonschema.Draft7Validator.check_schema` PASS |
| **ZIP 무결성** | 최종 바이너리 **123 ZIP entries / 중복 0**. `dev.fabric-amper.settings`·`dev.fabric-amper.project` Gradle plugin marker 두 개와 Settings/Project class 두 개 **각 1개** |
| **최종 로컬 게시** | `publishAllPublicationsToG29ProbeRepository -PfabricAmperProbeMavenRepo=true --offline`: **BUILD SUCCESSFUL in 8s**, **12 tasks: 7 executed+5 up-to-date**. `build/g29-probe/maven-repo` 내부 main JAR·sources JAR이 최종 `build/libs`와 **byte-for-byte 동일**, 두 플러그인 marker POM 존재. 전역 `~/.m2`·Plugin Portal·외부 Maven 업로드 없음 |
| **재현 증거 보관** | `analysis/acceptance-evidence-2026-11-13/FINAL-default-clean-42-suites.zip` **42개 최종 XML**, ZIP SHA-256 **`F0BF62998E0ED568A45F9DD02B7296950D615AF2FD00502036892E10846DD61A`**; 파일별 크기/해시·JUnit 총합은 `FINAL-offline-acceptance-manifest.json` |

### 22.5 출하 판단 및 완료되지 않은 조건

**선별 PASS:** settings-only `fabric/mod`·`fabric/api`·`fabric/library` 생성/빌드, 실제 로컬 Maven 전이·레거시 API remap Maven 배포, 신규 게시 YAML·JSON 구성·Gradle 작업 wiring, Maven 없는 플랫폼 전용 작업, 원격 태스크의 안전한 dry-run, API local Maven configuration-cache 4-run, G24 실제 리소스 재패키징, G5 기존 캐시 회귀, 검증 가능한 파일명/credential/schema 보안 조건, 마지막 전체 기본 단위·플러그인 빌드. **원격 플랫폼 게시 성공은 한 번도 주장하지 않는다.**

**남은 게이트/제한:** **G6/G28** 실제 IntelliJ GUI 신규 import·수동/자동 Sync 미완료; **G15/G27** 실제 dedicated server 수락은 EULA 자동 승인 없이 **NOT RUN**; **G17/G15** 기존 격리 클라이언트 일부 실행의 Windows native `0xC0000409` FAIL 원인/재현성 미해결(과거 PASS·FAIL 둘 다 보존); **G19** 외부 KGP 실제 충돌·**G29** Kotlin-disabled cold-cache lazy 다운로드 최적화 미출하; **FA011** TLS/외부 원격 해석과 에러 분류 전 범위 미검증; legacy 게임 Loader 런타임 및 일부 Gradle/Loom/IDE 조합 미완료; 실제 HTTPS Maven credentials+configuration-cache 인증, Modrinth/CurseForge API 요청/프로젝트 권한/검수 수락 **NOT RUN**, SchemaStore 카탈로그 실제 등록은 사용자 담당. `README.md`의 전역 출하 주의도 동기화했다.

**최종 판정: 새로운 게시 기능의 안전한 로컬 범위와 플러그인 오프라인 클린 빌드는 PASS. 그러나 `specs.md`의 G1–G30 모두 완전 수락 / 정식 출시 승인은 여전히 `NOT APPROVED`.** 중간 컴파일 캐시 실패·실제 validatePlugins 실패·기존 Native 런타임 실패·미실행 GUI/서버/외부 API를 숨기지 않는다.

**§22 소유권·보존:** QA는 오직 이 신규 절을 **기존 §1–§21 뒤에 append**하고 이전 1,010행의 수치·기록·SHA를 수정하지 않았다. README·제품 소스·schema·integration/unit 테스트 수정의 이력은 본 절에 분리 기술한다. 다른 프로젝트(Minecord/EMI/Inventory Expanded)·기존 Minecraft 사용자 계정/월드/서버·EULA·인증 설정은 변경하지 않았다.

---

## 23. 게시 검증 후속 수락·Gradle 테스트 실행 충돌과 G9 독립 재검증 (2026-11-13)

> **역사적 기록 불변:** §1–§22의 기존 **1,072행**을 수정하거나 §22의 당시 **242 tests = 205 PASS + 37 SKIPPED** 및 배포 해시를 소급 교체하지 않는다. 여기서는 후속 검사 시점에 관측된 결과만 따로 기록한다. 테스트 실행 주체가 확인되지 않은 기록에 Prime 실행을 귀속하지 않는다.

### 23.1 초기 단위 실패·검증 순서 수정 및 표적 재검사

후속 첫 기본 실행은 **236 tests = 198 PASS + 37 SKIPPED + 1 FAIL**이었다. 실패는 **FabricLibraryPublishingContractUnitTest**의 잘못된 `usernameEnv` 입력 경로에서 발생했으며, 실제 원인은 **`ModYamlValidator.kt:60–71`의 개별 환경변수 이름 문법 확인과 username/password 지정 쌍 검증 순서**였다. 생산 검증기를 **각 필드의 이름 문법을 먼저 확인하고 그다음 쌍의 완전성을 확인**하도록 고쳤다. 이후 **`clean compileKotlin` PASS**, 해당 **FabricLibraryPublishingContractUnitTest 7/7 PASS**를 별도로 확인했다. 첫 1 FAIL을 삭제하거나 당시 실행 전체를 성공으로 수정하지 않는다.

### 23.2 JSON Schema의 세 가지 정합성 보정 및 metadata 단위 결과

Prime이 **루트 `fabric-amper.schema.json`만** 수정해 **멀티라인 changelog**, **Modrinth `versionNumber`/`gameVersions`/`loaders` 정규식**, **CurseForge relation slug** 조건을 보정했다. 공식 Python **`jsonschema.Draft7Validator.check_schema` PASS**이며 세 가지 재현 입력의 검증 오류 수는 기대대로 **0 / 1 / 1**이다. 수정된 루트 schema SHA-256: **`D5FD84098F1FD6AB926D504F23C69EC991AD17CBCF127B4C14308672B1BC5233`**.

신규 **`FabricAmperPublishMetadataUnitTest.kt` 2개 테스트**의 별도 **2026-11-13T10:04Z JUnit XML = 2 PASS / 0 FAIL / 0 ERROR / 0 SKIPPED**를 관측했다. 이 XML은 **공유 기본 테스트 실행에서 생성된 결과**이므로 **Prime이 그 실행을 시작했다고 단정하지 않는다**. 또한 **XML test timestamp `10:04:35Z`가 최종 테스트 파일 수정 시각 `10:04:48Z`보다 13초 빠르므로, 이 XML만으로 최종 테스트 파일 버전의 실행 PASS를 증명할 수 없다**. 당시에는 최종 파일의 독립 표적 재실행 증거가 없었고, **아래 후속 재검증에서 별도로 입증**했다. 별도 표적 수락을 당시 전체 테스트 수에 임의 합산하지 않는다.

**최종 파일 기준 독립 재검증:** Prime이 `test --offline --tests dev.fabricamper.unit.FabricAmperPublishMetadataUnitTest -P kotlin.incremental=false --console=plain --no-daemon`을 실행해 **`:compileTestKotlin` 실제 수행 / EXIT 0 / BUILD SUCCESSFUL in 11s**를 확인했다. 신규 JUnit XML timestamp **`2026-11-13T10:17:17.089Z`**, **2 PASS / 0 FAIL / 0 ERROR / 0 SKIPPED**로 최종 테스트 파일 수정 시각보다 늦다. 보관 파일 **`analysis/acceptance-evidence-2026-11-13/PUBLISH-metadata-final-2PASS.xml`**, SHA-256 **`AF9EE2F3FC21A0B01B93E491609CD107EA7A859B60F221CD2928DA2B6A7F88E1`**. 앞서 Windows `.bat`에서 붙여 쓴 `-Pkotlin.incremental=false` 인수를 잘못 해석하여 **존재하지 않는 `.incremental=false` task 오류로 EXIT 1**이었던 시도는 **명령행 문법 문제로 테스트 미실행**이며, 재실행 PASS로 소급하지 않는다. 이 2 PASS는 선택 실행이므로 전체 기본 clean 결과에 합산하지 않는다.

### 23.3 G9 첫 실행 불확정·안정화 뒤 정확한 선택 재검증

최초 선택 G9 실행에서는 Gradle **`NoSuchFileException: build/test-results/test/binary/in-progress-results-generic.bin`**이 발생했다. 그 시점 기본 XML 출력은 선택 G9 클래스 대신 **`FabricAmperPublishPlanConsumerTest.xml` 한 개**로 바뀌었고, 외부의 동시 테스트 결과 기록자가 있었을 가능성은 있으나 **실행 주체를 확인하지 못했다**. 따라서 이 시도는 **테스트 결과 기록 충돌로 인한 인프라 불확정**으로만 분류하며 **G9 제품 assertion PASS/FAIL 어느 쪽도 판정하지 않는다**.

테스트 실행이 안정된 후 Prime이 정확한 opt-in **`FabricAmperG9LegacyApiMavenConsumerTest`**를 재실행했고 **`BUILD SUCCESSFUL in 1m 32s`**, JUnit XML **`tests=1 skipped=0 failures=0 errors=0`**, 즉 **1 PASS**를 독립 확인했다. 보관 증거 **`analysis/acceptance-evidence-2026-11-13/G9-legacy-api-maven-recheck-1PASS.xml`** SHA-256 **`78612FD5004F18F960D124C08834E7354F03E33D47FB8352CD265968FCA32430`**. 이 결과는 레거시 API Maven 선택 소비자 검증이며 레거시 실제 게임 Loader 실행 수락은 아니다.

### 23.4 현 산출물·미완료 출하 조건

현재 `build/libs/fabric-amper-0.1.0.jar` SHA-256 **`164CADA56DF0BEFBFB0AFABAE574F639A89795DFD86CA222AA8789F44C829F0A`**, `build/libs/fabric-amper-0.1.0-sources.jar` SHA-256 **`2032A41383A3534B2FF0B087586B968189C752D74029A545E95ACAA747F2B119`**로 **§22 기록과 일치**한다. **§22 이후 새 전체 clean build를 실행한 것으로 주장하지 않으며 새 배포 artifact도 약속하지 않는다**.

**미수락 범위:** `specs.md`의 **G1–G30 전체 게이트**는 여전히 완전 입증되지 않았다. 실제 IntelliJ/GUI Sync, EULA 동의가 필요한 dedicated server·일부 게임 Loader 런타임, 외부 Modrinth/CurseForge API 업로드 및 remote Maven 인증·TLS 등은 **NOT VERIFIED/NOT RUN**이다. 따라서 **정식 출시 승인 = `NOT APPROVED`**를 유지한다. QA 작업자 6은 이 절을 보고서 끝에만 추가했으며 **Gradle/HTTP/API 실행, 다른 소스 및 산출물 수정은 하지 않았다**.

---

## 24. G1–G30 남은 안전 게이트 재검증: 격리 Gradle/Loom 매트릭스·실제 Loader 틱·최종 무캐시 빌드 (2026-11-13)

> **증거 보존 및 범위:** 이전 **§1–§23의 1,102행 원문과 모든 당시 FAIL/PASS/SKIP, JAR 해시, JVM/SDL 종료 관측은 수정하지 않는다.** 사용자의 추가 지시에 따라 후속 검증을 실제로 수행했으나, 다른 실행자의 Gradle 프로세스가 같은 원본 `build/`를 사용하는 파일잠금·JUnit 바이너리 기록 충돌을 재현했다. 따라서 원본 프로젝트 **안의** `analysis/isolated-acceptance-2026-11-13/`와 `analysis/final-verification-2026-11-13/`에 **동일 production/test source의 SHA 목록이 있는 독립 스냅샷**을 만들고, 격리된 자체 `build/`·TestKit home에서 나머지 실행을 완료했다. 마지막 비교에서 원본과 최종 스냅샷의 생산·테스트 소스 해시 차이 **0개**. 다른 프로젝트 폴더, 기존 게임 계정·월드·서버/EULA는 건드리지 않았다.

### 24.1 실제 새 선택 TestKit

| 게이트/대상 | 실제 재실행·XML | 정밀 판정 |
| --- | --- | --- |
| **G17 compile + G27 side + G19 real Loom** | 기존 기본 생산 플러그인에서 세 클래스 선택 `test`: **각 1 PASS/0 SKIP/0 FAIL**. `FOLLOWUP-FabricAmperG17CompileConsumerTest-1PASS.xml`, `FOLLOWUP-FabricAmperG27SideConsumerTest-1PASS.xml`, `FOLLOWUP-FabricAmperG19RealLoomCollisionConsumerTest-1PASS.xml` 각각 보존 | 실제 Java 주입 양·음성 컴파일, main/client Mixin/의존성 경계, 진짜 외부 Loom1.18.4 CodeSource·FA010·번들1.17.21 정상 대조 **PASS(선별)**. G27 dedicated server는 이 테스트에 포함되지 않음 |
| **G13 Gradle9.7.0/Loom1.18.4** | 별도 producer 번들 `-PfabricAmperBundledLoomVersion=1.18.4`, 실제 Gradle9.7.0 소비자 full build·CodeSource, `FOLLOWUP-G13-Gradle970-Loom1184-isolated.xml`: **1 PASS, 다른 조합 4 SKIP** | 요구한 정확한 조합 **PASS**. TestKit의 미선택 SKIP는 통과로 계산하지 않음 |
| **G13 Gradle9.9.1/Loom1.19.4 + G25 세 variant** | producer를 실제 **Gradle9.9.1**로 Loom1.19.4로 재빌드한 후 `FOLLOWUP-G13-G25-Gradle991-Loom1194-isolated-4PASS.xml`: **4 PASS / 1 SKIP / 0 FAIL** | G13 Java build PASS, G25 Gradle9.7.0 **9.8 이상 API variant 의도된 거부**/9.8.0·9.9.1 정상 resolution **3 PASS**. 선행 Gradle9.7.1 producer에서 Loom1.19.4 compileClasspath가 거부된 것은 Gradle 최소 버전 9.8 요구에 따른 **정확한 호환성 경계**, 제품 TestKit assertion FAIL 아님 |
| **G19 real 외부 KGP** | 프로젝트 내부 로컬 G29 plugin marker/POM/JAR 게시 후 독립 real KGP2.4.20 init-script 선적용→명시적 FA010, 다른 소비자에서 번들 KGP2.4.21 정상. `--rerun-tasks --no-build-cache` 실제 재실행, `FOLLOWUP-G19-real-KGP2420-conflict-1PASS-isolated.xml` **1 PASS** | 중간 `:test FROM-CACHE`는 재실행 PASS로 계산하지 않았고, 별도 실제 외부 classloader 및 ID 검증으로 **G19 KGP 선별 수락**. 최초 다른 동시 테스트의 `line216` FAIL/사라진 G29 fixture 이력은 삭제하지 않음 |
| **G15 실제 Loader 클라이언트** | `FOLLOWUP-G15-real-FabricLoader-client-tick-1PASS-isolated.xml` **1 PASS**, outer `BUILD SUCCESSFUL in 3m 51s`, native TestKit/client nonce main+client+game tick | **실제 게임 틱 PASS**. 이전 §19의 독립 `0xC0000409` 실패 두 기록 및 플랫폼 재현성 불확정은 그대로 유지; 서버 수락 아님 |
| **G17 실제 주입 런타임** | `FOLLOWUP-G17-real-injected-interface-client-tick-1PASS-isolated.xml` **1 PASS**, outer `BUILD SUCCESSFUL in 3m 44s`, Class Tweaker 주입·기본 메서드 직접 호출+game tick | §19의 과거 두 native FAIL은 당시 사실로 보존하되, 이번 격리 실행 **새 런타임 game tick PASS**. IntelliJ IDE 주입 모델은 미실행 |
| **G10 실제 JIJ Loader invocation** | `FOLLOWUP-G10-real-JIJ-Loader-invocation-1PASS-isolated.xml` **1 PASS**, outer `BUILD SUCCESSFUL in 10m 10s`, 별도 production 모드/nested JAR library 실제 로딩/호출 | 패키징만이 아닌 Fabric Loader 실제 nested library 호출 수락 **PASS**. 다른 Modrinth 런타임/모든 scope 전수 검증을 뜻하지 않음 |
| **remote Maven Provider CC dry-run** | `FOLLOWUP-REMOTE-MAVEN-OFFLINE-CC-DRYRUN-1PASS.xml` **1 PASS**, 설정 없는 dummy 토큰 환경변수 이름·`.invalid` HTTPS 주소에서 명시적 `--offline --dry-run --configuration-cache` 두 번 `STORE→HIT` | **태스크 그래프/Provider config-cache 직렬화 및 재사용만 PASS**. doFirst 실제 credentials·TLS·HTTP 게시 성공은 미검증. 처음 두 cold TestKit cache 실패 XML은 `FOLLOWUP-REMOTE-CC-*-FAIL.xml`로 별도 보존 |

선택 TestKit들의 PASS는 **마지막 기본 JUnit 246개 수치에 더하지 않는다**. 외부 API/실제 게시 HTTP 시도는 없음. 격리 runClient는 해당 fixture의 새 홈·APPDATA/Gradle home과 PID/descendant 제한 종료를 사용했다.

### 24.2 G21 두 번 실제 클린 빌드의 결정성

`analysis/g21-determinism-consumer-2026-11-13/`에 **실제 settings.gradle.kts + mod.yaml + Java source만** 작성하고, 로컬 Maven plugin marker를 통해 Fabric Amper 0.1.0을 적용했다. `help` warm-up 이후 **각각 독립적인 두 번의 `clean build --offline --no-build-cache`** 모두 EXIT0. 배포 JAR SHA-256 **`46C31C562A0F9F156067E63D70900D33922BAF03B584E35941E7FC9910710A33`**, sources JAR **`83D7713B521664FD76A75E785A8B8190380A87F5D8D9C4FCDE6AF0D54E57C62A`**, generated JSON **`3B73BB498E307A4D20CC0414C655E87DAE9E0485C2FB82F8F44DB28641937C3A`**가 **첫째/둘째 빌드 사이 byte-for-byte 완전 동일**. 루트 JSON 정확히 하나, 배포 JAR JSON=generated JSON=구성 시점 미러 내용. `FOLLOWUP-G21-two-independent-clean-builds-PASS.json`에 두 비교값을 보존.

### 24.3 소스 동등성·최종 실제 무캐시 Gradle 빌드 및 산출물

| 항목 | 관측 결과 |
| --- | --- |
| **검증용 원본 소스 snapshot** | `analysis/final-verification-2026-11-13/`의 `source-snapshot-sha256.json`은 당시 생산·테스트/README/schema의 모든 소스 해시를 기록. 마지막 원본 루트의 생산·테스트 파일과 비교해 **changed 0, added 0** |
| **정확한 Gradle 명령** | Gradle **9.7.1**/JDK **25**, `clean build validatePlugins --offline --console=plain --no-daemon --no-build-cache --rerun-tasks -Pkotlin.incremental=false`, **EXIT0, BUILD SUCCESSFUL in 1m 38s**, 11 actionable tasks **11 executed** |
| **기본 JUnit** | **45 suites, 246 tests = 207 PASS + 39 SKIPPED + 0 FAIL + 0 ERROR**. 부작용 있는 플랫폼 게시 선택/실제 Loader opt-in은 기본 빌드에서 SKIP; 별도 실측 XML과 혼동하지 않음 |
| **Gradle Plugin marker 검증** | `validatePlugins` 실제 실행 PASS. 최종 플러그인 ZIP **123 entries / 중복 0**; 두 plugin marker와 Settings/Project 구현 클래스 각각 **정확히 1개** |
| **최종 이진 JAR** | `build/libs/fabric-amper-0.1.0.jar` **314,704 bytes**, SHA-256 **`164CADA56DF0BEFBFB0AFABAE574F639A89795DFD86CA222AA8789F44C829F0A`**. §22/§23 과거 해시와도 **동일**하나 이번은 **새 무캐시 재빌드** 결과 |
| **sources JAR** | `build/libs/fabric-amper-0.1.0-sources.jar` **67,202 bytes**, SHA-256 **`2032A41383A3534B2FF0B087586B968189C752D74029A545E95ACAA747F2B119`** |
| **SchemaStore Draft-07** | `fabric-amper.schema.json` **26,823 bytes**, SHA-256 **`D5FD84098F1FD6AB926D504F23C69EC991AD17CBCF127B4C14308672B1BC5233`**, `jsonschema.Draft7Validator.check_schema` PASS. 실제 SchemaStore 등록은 사용자 담당 |
| **프로젝트 내부 로컬 G29 재게시** | `publishAllPublicationsToG29ProbeRepository --offline` **BUILD SUCCESSFUL in 22s**. marker POM 2개, 실제 게시 main JAR/sources JAR이 방금 생성한 최종 파일과 **byte-for-byte 동일**. 전역 `~/.m2`·원격 Maven 게시 없음 |
| **증거 보관** | `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-FINAL-clean-no-cache-manifest.json`, `FOLLOWUP-FINAL-clean-no-cache-JUnit-XML.zip` (**45 XML**, ZIP SHA-256 **`BD7019624B7AC6167E6FDB8052126A82253288E34789FDB54A8DE4781D4F73D6`**), 별도 `G1-G30-followup-gate-matrix.md` |

### 24.4 전체 출시 판정 — 계측 성공과 권한·환경 차단 구분

G1–G30의 **개별 판정·근거·미완료 조건**은 `analysis/acceptance-evidence-2026-11-13/G1-G30-followup-gate-matrix.md`에 기록한다. **신규 닫힌 부분:** G13·G25 실제 Gradle 조합, G19 실제 다른 Loom와 KGP 클래스 로딩, G10·G15·G17 실제 Loader 게임 틱, G21 청정 재빌드 결정성, remote Maven graph config-cache replay. **남아 있는 완전 수락의 차단:** G6/G28 실제 IntelliJ GUI 최초 import/수동·자동 Sync 및 G17 IDE injection; G15/G27 전용 서버 EULA **수락·구동 NOT RUN**; G20 IDE 구성, 일부 G11/G30 공격 입력 전체 조합, G29 Kotlin-off KGP 다운로드 비용은 측정됐으나 lazy KGP opt-in은 미출하, 실제 HTTPS Maven 인증+Gradle CC replay/FA011 TLS·Modrinth 오류 전조합은 미검증. **실제 Modrinth·CurseForge HTTP API 테스트/토큰 인증·업로드는 사용자가 금지하므로 0건 수행**.

**최종 출하 판정: 많은 독립 실소비자 및 실제 클라이언트 검증 PASS, 플러그인 무캐시 클린 빌드 PASS. 하지만 G1–G30 전 게이트와 승인·외부 API를 모두 검증했다고 주장할 수 없으므로 `NOT APPROVED`.** 과거 실패 기록 및 최종 기본 빌드의 **39 SKIPPED**는 그대로 유지한다.

---

## 25. G5 Java 설정 CC·G20 병렬 미러·G30 별칭 재검증 및 EULA 서버 Loader 증거 정정 (2026-11-13)

**§1–§24 기존 1,147행 원문/수치/실패 기록을 보존**하면서 마지막 미완료 안전 테스트를 추가했다. §24가 기록한 최종 기본 Gradle 9.7.1/JDK 25 **무캐시** `clean build validatePlugins --offline` **45 suites/246 tests=207 PASS+39 SKIP/0 FAIL** 및 바이너리 SHA는 그대로 유효하다. 아래는 **서로 다른 선택 실행**, 기본 246개에 합산하지 않는다. 프로젝트의 다른 Minecraft 계정·월드·서버/EULA를 수정하지 않았으며, 플랫폼 게시용 실제 HTTP 요청을 실행하지 않았다.

| 대상 | 새 실측 증거·정확한 판정 |
| --- | --- |
| **G20 — 실제 병렬 복구 재검증** | `FabricAmperParallelMirrorConsumerTest`가 두 독립 GradleRunner를 동시에 실행해 손상 JSON·삭제 아이콘을 복구하고 mixin/아이콘/생성 메타데이터 바이트 동일 및 임시파일 0개를 확인: **JUnit 1 PASS / 0 FAIL / 0 SKIP**, outer `BUILD SUCCESSFUL in 2m 10s` (G30 선택 테스트와 같은 외부 실행). `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-RECHECK-FabricAmperParallelMirrorConsumerTest-1PASS.xml`, SHA-256 `F8387C28538D6EC20F64F0E8CC24669BEBA3EB913B5B9849865CBEBCD1DD37FA`. Class Tweaker·Mixin·아이콘 **전부 동시 손상** 및 실제 IDE Sync는 별도 미검증 |
| **G30 — YAML 파일명/CC 재검증** | `FabricAmperExtendedConsumerTest.G30 switching mod yml to mod yaml...`: 사전 `mod.yml` CC 저장·HIT→`mod.yaml`로 교체 후 MISS·새 미러 버전 반영→HIT, **JUnit 1 PASS / 0 FAIL / 0 SKIP**. `FOLLOWUP-RECHECK-FabricAmperExtendedConsumerTest-1PASS.xml`, SHA-256 `DC57A47E43508C3F06AE0716F8199573D86B6B104C646DB78AA2EC0690794106`. 비정상 UTF-8/anchor/tag/multidoc는 기본 `FabricAmperDiagnosticsTest`·`StrictYamlReaderUnitTest`의 별도 기본 PASS이며 공격 입력×CC×ZIP 출력 전수 조합은 미완료 |
| **G5 — 유효한 Java 옵션 변경 CC** | 신규 독립 settings-only 소비자 `analysis/g5-java-setting-cc-probe-2026-11-13/settings-only-consumer-v2/`에서 **`settings.java.release=25` 유지, `settings.java.sourcesJar: true→false→true`**, `tasks --all --configuration-cache --offline` 6회 **STORE→HIT→Java 옵션 MISS→HIT→복구 MISS→HIT** 실측. `sourcesJar` task 표시도 ON→OFF→ON으로 실제 변함. 최초 `help`/task enumeration warm은 측정에서 제외. `FOLLOWUP-G5-java-setting-CC-6run-PASS.json` 및 단계별 8개 독립 stdout 로그 해시 검증 PASS, JSON SHA-256 `765E17A9382974B6E42E7F2B813D9658CC4BB3948631A65CEADB536BA4436AF5`. 처음 다른 cold consumer의 `directory '.' has changed` 비재사용 시도도 삭제하지 않으며, 사전 `tasks --all` 실행이 필요했던 Fixture 비용으로 구분. 전체 Java toolchain/버전 교차 호환성으로 확대하지 않음 |

### 25.1 §24 표현 정정 — 실제 `runServer` 시도는 있었으나 EULA를 수락하지 않음

§24 마지막 문단의 **G15/G27 전용 서버 `구동 NOT RUN`은 완전한 서버 부팅·월드 실행의 의미로만 해석해야 한다.** 별도의 기존 선별 실행 기록 `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-G27-server-EULA-refusal-loader-discovery.log:27-38`에서는 `:runServer`가 **실제로 실행**됐고, Fabric Loader **0.20.1 SERVER**가 `fabric_amper_server_probe 0.1.0+26.3`을 발견했으며 `FABRIC_AMPER_SERVER_LOADER_INITIALIZER=SUCCESS`를 출력했다. 같은 로그 **58–59행**에서 `eula.txt` 미동의로 서버 완전 시작이 거부됐다. **Gradle `BUILD SUCCESSFUL`은 서버 실행 수락 증거가 아니다.** 따라서 정확한 상태는 **서버 Loader 발견·초기화 부분 PASS / EULA 승인 후 정상 서버 완전 부팅·side 분리·월드 틱 NOT RUN**이다. 사용자 EULA 자동 수락·기존 월드 접근·인증 우회는 수행하지 않았다.

### 25.2 문서 정합성·최종 산출물

`README.md` 최신 검증 문구를 수정해 **과거 G17 `0xC0000409` 런타임 JUnit FAIL과 그 이후 격리한 G17 실제 주입·기본 메서드·게임 틱 JUnit PASS를 별도 기록**했다. 초기 원본 기본 `clean build` 39초와 뒤의 해시 동등 격리 스냅샷 **`--no-build-cache --rerun-tasks` 1분 38초**도 서로 다른 실행이라고 명시했다. G5 신규 opt-in은 생산 소스를 변경하지 않는 외부 격리 소비자 실행이므로 최종 플러그인 JAR, sources JAR, SchemaStore schema는 각각 §24에서 이미 실측한 **`164CADA56DF0BEFBFB0AFABAE574F639A89795DFD86CA222AA8789F44C829F0A`**, **`2032A41383A3534B2FF0B087586B968189C752D74029A545E95ACAA747F2B119`**, **`D5FD84098F1FD6AB926D504F23C69EC991AD17CBCF127B4C14308672B1BC5233`**을 유지한다.

**현재 최종 게이트별 상세 판정:** `analysis/acceptance-evidence-2026-11-13/G1-G30-followup-gate-matrix.md`. 실제 IntelliJ GUI 최초/수동/자동 Sync, 사용자 EULA 승인 후 dedicated server 전체 부팅, 실제 HTTPS Maven 인증·TLS/FA011 오류 전수, 일부 복합 악성 YAML/CC, 레거시 게임 런타임 등은 **미수락**이다. G29는 기준 명세가 요구한 **cold-cache 정적 KGP 다운로드 비용 측정은 완료**, 지연 KGP opt-in 구현은 조건부 향후 과제이지 현재 전체 PASS의 근거가 아니다. Modrinth·CurseForge HTTP 업로드는 사용자 요청에 따라 **0건**. **모든 G1–G30 및 출시 승인 `NOT APPROVED`**를 유지한다. 중간 테스트 실패, 과거 런타임 실패, 최종 39 SKIP를 절대 PASS로 소급하지 않는다.

---

## 26. 미완료 안전 검증 확장: G11/G30 악성 입력·G20 복합 동시 복구·HTTPS 원격 Maven 실태·최종 무캐시 빌드 (2026-11-13)

**역사 보존:** 이전 **§1–§25의 1,169행**과 그 당시 `FAIL`/`PASS`/`SKIP`, JAR SHA, 실제 런타임·IDE/EULA 판단을 한 글자도 소급 수정하지 않았다. 이번 절은 사용자의 “검증 남은거 해 제발”에 따라 **새롭게 실제 실행한 독립 검증**과 그 과정에서 발견한 불가능 조건/제품 결함을 각각 분리해 기록한다. 기존 다른 프로젝트, 계정/`.minecraft`, 개인 월드, EULA 및 전역 Maven Local을 변경하지 않았으며 Modrinth·CurseForge 실제 업로드는 **0건**이다. `analysis/` 내 실패 fixture도 덮어쓰거나 삭제하지 않고 별도 이름으로 유지했다.

### 26.1 G11/G30 — 악성 YAML 11종 × CC 오류/복구 × 원본/출력 보존

`analysis/g30-adversarial-cc-probe-2026-11-13/verify.py`에서 **최종 독립 settings-only 소비자 `consumer-v3`**에 대해 15회 실행을 계측했다. 먼저 정상 구성 캐시 STORE→HIT를 검증했다. 이후 (1) 하위 중복 키, (2) scalar alias, (3) collection tag, (4) 다중 YAML 문서, (5) 빈 문서, (6) 잘못된 UTF-8, (7) 숫자형 `minecraft.version`, (8) 미정의 중첩 설정 필드, (9) 명시적 merge key, (10) anchored scalar, (11) 깊이 초과 YAML 총 **11개 실패 입력**이 기대한 **FA002 또는 FA003**으로 실제 거부됨을 확인했다. 각 입력의 원본 바이트를 보존하고, 기존 정상 `build/generated/.../fabric.mod.json`, 콘텐츠 주소 Loom mirror, 배포 메인/sources JAR의 **SHA가 변경되지 않았으며**, 손상된 YAML에서 기존 configuration cache HIT가 되지 않았다. 정상 YAML 복원 후의 두 실행은 다시 **CC HIT**였다. 복원 단계 라벨에 `store`가 들어 있어도 실제로는 원래의 유효한 CC entry로 돌아가 HIT된 것임을 보존한다.

실측 **11/11 PASS, 15회 실행**, 근거 `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-G11-G30-hostile-YAML-CC-preservation-PASS.json`, SHA-256 `5EDA0374C0D7B1472F05375902723F483D623997E48638092591D769C4CA962E`. 초기 `consumer`/`consumer-v2`의 CC cold Minecraft cache 생성, Windows `Path` 구분자 assertion 오류는 fixture 문제이고 마지막 `consumer-v3`는 실제 PASS; 선행 실패 기록을 보존한다.

### 26.2 G11 리소스·메타데이터 오류 6종 × CC/기존 파일 보존

`analysis/g11-resource-cc-probe-2026-11-13/verify.py`, 최종 별도 `consumer-v2`에서 (1) Class Tweaker 헤더 불일치 → FA009, (2) 참조한 icon 삭제 → FA009, (3) resource 경로 traversal → FA009, (4) 충돌하는 의존성 scope → FA012, (5) `mod.yaml`·`mod.yml` 동시 존재 → FA001, (6) 개발자 작성 `src/main/resources/fabric.mod.json` → FA008의 **6개 실제 실패**와 **정상 CC HIT 복구**를 검증했다. 각 실패에서 원본 YAML/리소스와 이전 정상 mirror/generated/build 메인·소스 JAR을 **바이트 단위로 보존**, 이전 정상 configuration cache를 잘못 재사용하지 않았다. **6/6 PASS, 10회 실행, 정상 baseline 해시 8개**. 근거 `FOLLOWUP-G11-resource-FA001-FA008-FA009-FA012-CC-PASS.json`, SHA-256 `6370060C6CFFCB5BE6E6FB9478ED81B4FAD43EFFF9B4F8CDC59A9D0EEF28D2AE`. 초기에 icon 내부에 임의 문자열을 넣은 경우는 `.png` 확장자·경로·존재만 검증한다는 현재 명세에 없는 이미지 디코딩 assertion이었으며, `missing-icon`으로 바로잡았다. 내부 PNG decode를 실제 검증한 것으로 주장하지 않는다.

### 26.3 G20 — 4종 미러 동시 손상과 실제 병렬 GradleRunner 1 PASS

새로운 `src/test/kotlin/dev/fabricamper/integration/FabricAmperG20CompositeMirrorConsumerTest.kt`(기본 OFF) 및 선택 빌드 속성 `-PfabricAmperTestkitFull=true -PfabricAmperTestkitG20Composite=true`를 추가했다. Java25/MC26.3 `settings.gradle.kts`+YAML만 있는 별도 소비자가 먼저 실제 JAR을 만들고, 독립 TestKit Gradle homes 각각에서 `help`·`generateFabricModJson --configuration-cache --offline` 구성을 예열했다. **authored source를 변경하지 않고** 콘텐츠 주소 Loom mirror 내 `fabric.mod.json`, Mixin, Class Tweaker 바이트를 동시에 손상시키고 아이콘 미러 파일을 삭제했다. 두 실제 GradleRunner가 barrier 이후 서로 다른 명령을 **병렬 실행**, 모두 성공하고 **4개 미러 파일, authored source, generated JSON, 기존 배포 JAR**이 정상 원본 바이트와 완전히 일치했으며 각 JAR 리소스 엔트리는 한 개씩, `.amper-` 임시파일 0개, 마지막 CC 재사용 HIT를 확인했다.

정확한 선택 명령: `gradle test --offline --console=plain --no-daemon --no-build-cache -Pkotlin.incremental=false -PfabricAmperTestkitFull=true -PfabricAmperTestkitG20Composite=true --tests dev.fabricamper.integration.FabricAmperG20CompositeMirrorConsumerTest`. **EXIT0, BUILD SUCCESSFUL in 2m24s, JUnit 1 PASS/0 FAIL/0 ERROR/0 SKIP**. 보존 XML `FOLLOWUP-G20-FOUR-MIRROR-FILES-PARALLEL-CC-1PASS.xml`, SHA-256 `913A9CB20947CE86E27CCDD20E5268274CD15AE5DAF116E08F89AC6F4B0104ED`. 이 결과가 보장하는 것은 **최종 바이트 일관성과 개별 파일 교체**이며 전체 디렉터리의 하나의 atomic transaction 또는 실제 IntelliJ GUI Sync를 입증하지 않는다.

### 26.4 실제 localhost HTTPS Maven 테스트 — 확인된 Gradle 9.7.1 네이티브 제약과 수정 이력

`analysis/local-https-maven-probe-2026-11-13/verify.py`는 `cryptography` SAN IP `127.0.0.1` 자기서명 인증서, JDK25 전용 PKCS12 truststore, `ThreadingHTTPServer` 루프백 ephemeral port, 고정 GAV allowlist, 가짜 휘발성 Basic username/password, Gradle `--offline` 조건만 사용한다. 외부 HTTP 업로드·플랫폼 API 호출이 아니라 **프로젝트 내부의 고립된 가짜 원격 Maven**이다. 서버는 HTTPS GET 무인증 접근에 실제 `401 WWW-Authenticate: Basic`을 반환했다. 마지막 실행 `run/server-events-on-exit.json`에는 **GET 401 한 건, 인증된 성공 PUT 0건**이 기록됐다. 실제 remote Maven 배포 JAR/POM/.module/sources 수신 해시를 PASS로 기록하지 않는다.

단계별 부정 관측을 보존한다.

1. 최초 `run-first-WinError206/`: `modules-2` 전체를 깊은 Windows 분석 경로에 복사하다 **WinError206/MAX_PATH**, 서버 시작/게시 전 실패. 둘째 `run-short-WinError3/`: 프로젝트 루트 `.h`로 복사 경로를 줄였으나 기존 종속 플러그인의 긴 캐시 경로로 오류. 재시도에서는 지정 캐시 홈의 부적합 길이 항목 **14개만 제외**하고 `modules-2`를 복사해 독립 캐시를 생성했다. 원본 사용자 캐시 삭제/변경 없음.
2. `run-offline-minecraft-cache-fail/`, `run-offline-fapi-omission/`: 분리 Gradle home에 Loom 26.3 manifest/game JAR 및 선택적인 Fabric API 전이 JAR 캐시가 부족해 `compileJava --offline` 사전 준비가 실패. 기존 읽기 전용 Loom cache의 필요한 Minecraft 파일을 복사하고, **이 Maven API 게시 시험에 불필요한** 선택적 `minecraft.fabricApi`만 fixture YAML에서 제외했다. 실제 독립 소비자 `compileJava --offline`는 이후 PASS. 이는 제품 결함 판정이 아니다.
3. `run-native-credentials-cc-discard-fail/`: 초기 제품 `FabricAmperPublishingConfigurer.kt`는 `PublishToMavenRepository.doFirst`에서 `task.repository.credentials`를 수정하여, 구성 캐시 직렬화 후 폐기된 속성 접근으로 **실제 게시 태스크 FAILED: `The value of this property has been discarded during serialization.`**. 이 사실은 실제 제품 결함이므로 **프로덕션 `src/main/kotlin/dev/fabricamper/loom/FabricAmperPublishingConfigurer.kt`를 수정**하여 선택된 원격 태스크 *구성 시점*에 Gradle native String-only `PasswordCredentials`를 설정하고, `doFirst`는 환경 Provider의 누락값만 FA003으로 검사하도록 수정했다. 나머지 HTTPS URL 검증/비밀 redaction/로컬 저장소 설정 유지.
4. 수정 이후 **`--offline`에서 Gradle 자체가 `https://127.0.0.1:<port>/maven/...` 네이티브 게시도 거부**: `Cannot upload ... in offline mode` (`run/01-publish-cc-store.log:19–39`, **실제 실패**). 같은 `run`의 `configuration-cache-report.html` embedded JSON에는 native `PublishToMavenRepository`에 대해 **`Explicit credentials are unsupported with the Configuration Cache`** 라는 `incompatibleTask` 진단이 있으며 **`Configuration cache disabled because incompatible task was found`**. `totalProblemCount=0`이라는 숫자로 CC compatible이라 추정하면 안 된다. 이 조건에서 **실제 authenticated PUT + CC STORE/HIT**를 모두 입증하는 것은 불가능하고, 실제 성공으로 표시하지 않는다. Gradle 암묵 credentials property 모드가 대안일 수 있으나 현재 YAML의 임의 환경변수 이름 두 개를 자동으로 매핑하는 계약과 동등하지 않아 미구현/미검증이다.
5. 초기 제품 수정 후 `--dry-run --configuration-cache --offline` 선택 TestKit에서 **1 FAIL**, 이유는 실행하지 않는 publish 태스크에도 명시적 credentials를 설정하여 Gradle native configuration cache를 비활성화한 것 (`FOLLOWUP-POST-HTTPS-REMOTE-CC-DRYRUN-REGRESSION-1FAIL.xml`). **다시 보정**: `project.gradle.startParameter.isDryRun`일 때는 실제 인증 설정을 하지 않도록 원격 태스크 구성만 분기, 실제 게시(`!isDryRun`) 경로는 그대로 유지. 선택 `FabricAmperRemoteMavenCredentialConfigurationCacheConsumerTest`를 **실제로 재실행**한 결과 `--dry-run`의 모든 태스크 `SKIPPED`, **CC STORE→HIT**, **JUnit 1 PASS/0 FAIL/0 SKIP** (`FOLLOWUP-POST-REMOTE-DRYRUN-CC-RESTORED-1PASS.xml`). 이 PASS는 **dry-run 태스크 그래프 전용**이며 실제 TLS 인증 성공이 아니다. 마지막 두 번의 로컬 HTTPS retry에서도 외부 HTTP/플랫폼 업로드를 허용하지 않았다.

**인증 경계:** `.invalid`/localhost 가짜 credentials는 로그/문서에 기록하지 않았고, `run/`의 private test certificate/key 및 truststore는 외부에 공유하지 않는다. 네이티브 Maven 인증의 실제 원격 PUT은 **0건**, 누락된 credential의 실제 업로드 시 FA003 결과도 그 단계에 진입하지 않아 **NOT VERIFIED**, HTTP URL 비HTTPS 거부는 기존 unit/YAML 진단 PASS. 외부 플랫폼 HTTP 토큰 테스트는 금지 조건대로 **0건**이다.

### 26.5 마지막 제품 수정 후 독립 무캐시 빌드·원격 dry-run 회귀·최종 SHA

최종 프로덕션 수정은 `FabricAmperPublishingConfigurer.kt`의 실 remote 자격증명 직렬화 수정과 `--dry-run` 안전 분기이며, 기본 OFF의 `build.gradle.kts` G20 composite TestKit opt-in 및 새로운 G20 통합 테스트가 함께 포함된다. 이 뒤 `analysis/final-post-dryrun-correction-2026-11-13/verify.py`를 사용해 원본 루트의 **Gradle 9.7.1/JDK25 `clean build validatePlugins --offline --no-build-cache --rerun-tasks -Pkotlin.incremental=false`**를 실제 실행했다. **EXIT0, BUILD SUCCESSFUL, validatePlugins 실행 PASS**, 기본 `build/test-results/test/` XML **46 suites / 247 tests = 207 PASS + 40 SKIP + 0 FAIL + 0 ERROR**. 선택 G20 1 PASS·선택 remote dry-run 1 PASS는 **247 기본 테스트에 중복 합산하지 않는다**. 기본 SKIP가 §24의 39에서 40으로 증가한 이유는 새 opt-in G20 복합 통합 테스트 1개가 기본 비활성화되었기 때문이며 이를 PASS로 속이지 않는다.

| 최종 실측 항목 | 값 |
| --- | --- |
| 최신 플러그인 main JAR | `build/libs/fabric-amper-0.1.0.jar`, **315,071바이트**, SHA-256 **`2DC9F20238BED6284C0D80C898F47C13B49AC897DE41D819DBE8667274E00264`** |
| 최신 플러그인 sources JAR | `build/libs/fabric-amper-0.1.0-sources.jar`, **67,690바이트**, SHA-256 **`E31E8461EA8F57A3FCBCEB04A2892BC6387D07DC29B696F63D677BE819320803`** |
| SchemaStore 스키마 (변화 없음) | `fabric-amper.schema.json`, **26,823바이트**, SHA-256 **`D5FD84098F1FD6AB926D504F23C69EC991AD17CBCF127B4C14308672B1BC5233`** |
| 메인 JAR 엔트리/마커 | ZIP entry **123개**, **중복 0**, Settings/Project plugin marker 각각 한 개 |
| 로컬 Maven 실제 게시 | 동일 루트에서 `publishAllPublicationsToG29ProbeRepository -PfabricAmperProbeMavenRepo=true --offline` **EXIT0/PASS**, marker POM 및 main/sources JAR **최종 빌드 바이트와 동일**, 전역 `~/.m2` 미변경 |
| 기본 JUnit XML 아카이브 | `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-POST-REMOTE-DRYRUN-FINAL-JUnit-XML.zip`, **46 XML**, SHA-256 **`6CF2602B2E0119BAF68FF421E1C721DA26D98345F8605408EA67F0BC68AC2127`** |
| 최신 producer manifest | `analysis/final-post-dryrun-correction-2026-11-13/run/manifest.json` (두 Gradle 실제 실행의 로그 포함), 이전 변경 직후의 중간 `analysis/final-post-remote-2026-11-13/` manifest/JAR 해시도 역사 증거로 보존 |

### 26.6 수동·환경 차단 및 최종 출시 판정

G6/G28와 G17 IDE injection/G20/G24 IDE Sync의 **실제 IntelliJ IDEA GUI 최초 import, 수동 Reload, YAML 편집 자동 Sync**는 Tooling API 검증과 다른 조건이며, 이번에 프로젝트 내부 `analysis/idea-gui-verification-2026-11-13/`의 독립 입력 fixture를 마련하고 사용자에게 직접 IntelliJ GUI 열기 단계를 전달했다. 도구의 사용자 단계는 완료 표시가 있었으나 `.idea` 결과 파일은 관측되지 않았고 GUI Sync/Run Configuration의 화면 증거가 없으므로 **GUI PASS 아님**. G15/G27 서버는 독립 Loader SERVER에서 모드 탐지·초기화 **부분 PASS**지만 EULA 미동의로 부팅/월드/틱 미완료; **자동 EULA 승인 금지**. G9 레거시 Java21 remap·다운스트림 Maven 컴파일 PASS지만 1.21.11 구형 게임 런타임 NOT RUN. G29 cold Kotlin OFF/ON 비용 측정 PASS이나 lazy KGP 미출하. 모든 실제 Modrinth·CurseForge API 업로드 금지 유지.

G11/G30의 **17건 안전 악성 입력×CC 및 원본/결과 불변 PASS**, G20 **4종 동시 미러 복구 1 PASS**, 원격 Maven **dry-run CC 회귀 복구 1 PASS**, 마지막 Gradle/JUnit/로컬 Maven 전부 PASS는 확인됐지만, 사람의 판단을 요구하는 GUI·EULA와 native 원격 Maven 명시적 credentials+CC 지원 제약을 해결한 것으로 주장하지 않는다. **최종 출하 판정: G1–G30 전체 출시 `NOT APPROVED`.** 과거 제품/fixture FAIL, 서버 EULA 거부, 현재 Gradle 자체의 offline PUT 거부, 최종 **40 SKIP** 모두 별도로 보존한다.

---

## 27. 원격 Maven 누락 자격증명 실제 음성 검사·FA003 수정·최종 회귀 봉인 (2026-11-13)

**§1–§26 기존 모든 기록(§26까지 1,225행)을 유지하고 새 결과만 추가했다.** §26에서 `--offline`이 원격 Maven PUT 자체를 차단하여 추가 파생 음성 검증을 건너뛴 뒤, 안전하게 가능한 **실제 native task의 누락 자격증명 진단**과 `http://` URL 구성 거부를 별도 격리 소비자로 끝까지 실행했다. 외부 서버·플랫폼·인증 토큰 사용은 **0건**이다.

### 27.1 초기 발견 — native credentials 사전 검증이 FA003을 가림

`analysis/local-https-maven-probe-2026-11-13/verify_negatives.py`의 최초 `negative-contract-2026-11-13/`에서는 실 Gradle 9.7.1 `publishMavenJavaPublicationToFabricAmperRemoteRepository --offline`에 누락된 두 환경변수를 주입하지 않았다. 실제 `:compileJava`·JAR·POM·module 생성 이후 native task가 실행 직전 실패했지만 **플러그인 FA003이 나오지 않았다**. Gradle task input validation이 먼저 `Value not set ... property 'credentials.username'`을 거부했기 때문이다. 최초 실제 실패 로그 `negative-contract-2026-11-13/01-missing-credentials.log:21-32`를 보존한다. 이것은 `--offline` 캐시/Windows 파일 경로 문제가 아니라 **제품의 요구 진단을 가리는 실제 제어 흐름 결함**이다.

수정은 **`src/main/kotlin/dev/fabricamper/loom/FabricAmperPublishingConfigurer.kt`만**: `!dryRun`으로 실제 게시 태스크가 구성될 때 `username.orNull ?: ""`, `password.orNull ?: ""`로 native PasswordCredentials에 **null이 아닌 값**을 설정해 Gradle의 값 미설정 검증을 먼저 통과하게 한다. 바로 다음 `RequireRemoteMavenCredentials`의 `doFirst`는 원래 Provider에서 **null/빈값/공백 값**을 검사해 **`FabricAmper FA003 publish.maven`**로 전송 전에 거부한다. 빈 문자열을 실제 인증으로 승인하지 않으며, 외부 API/계정·개인 파일을 바꾸지 않는다. 명시적 credentials의 Gradle native config-cache 비호환성은 이 수정으로 해결되지 않는다는 점을 그대로 명시한다.

### 27.2 후속 실제 두 음성 시나리오 — 정확한 FA003 두 번

수정된 새 바이너리를 **실제로 재빌드하고 프로젝트 내부 G29 Maven repository에 게시**한 뒤 `analysis/local-https-maven-probe-2026-11-13/verify_negatives_v2.py`를 실행했다. 기존 첫 실패를 덮어쓰지 않고 `negative-contract-v2-2026-11-13/` 독립 폴더를 생성했고, 입력은 `settings.gradle.kts` + `mod.yaml` + Java entrypoint만 있는 `product: fabric/api`였다. 두 실 Gradle 9.7.1 실행 모두 **`--offline --no-daemon --no-build-cache`**로 네트워크 전송이 불가능하다.

| 음성 검사 | 실제 결과 | 근거 |
| --- | --- | --- |
| 자격증명 두 env 변수 모두 없는 상태에서 **native 원격 게시 task 실제 실행** | **예상 EXIT1, plugin `FabricAmper FA003 publish.maven: both Maven credential environment variables must be set for remote publishing`**. Gradle 기본 `credentials.username Value not set`로 실패하던 문제가 사라졌으며, doFirst가 실제로 도달함. YAML 원본 바이트 SHA 변경 0 | `negative-contract-v2-2026-11-13/01-missing-credentials.log:17-32`, log SHA-256 **`B0041E5DBC8374A118E59F20455C8C322B42046A916186853015686BCBCCAFAC`** |
| `publish.maven.url: http://127.0.0.1:1/maven`가 있는 settings-only 소비자의 `help` | **예상 EXIT1, `FabricAmper FA003 mod.yaml:30:10 publish.maven.url: expected HTTPS`**. 설정 적용 단계에서 거부, JAR 미생성, YAML 원본 바이트 보존 | `negative-contract-v2-2026-11-13/02-http-url-rejection.log:4-20`, log SHA-256 **`3BC7D09AA2B7FF7958B9F99039B60037399741884647896E3FC1218EE037A983`** |

결론: **2/2 실제 음성 수락 PASS(각각 기대한 비정상 종료/FA003)**. JSON `analysis/local-https-maven-probe-2026-11-13/negative-contract-v2-2026-11-13/manifest.json`에 정확한 명령, 로그/저장된 원본 해시와 **실제 성공 PUT 0건**을 기록했다. 이것은 **누락된 자격증명 및 비HTTPS 입력의 실패 검증**이며, 자격증명이 제공된 HTTPS 정상 업로드 성공·인증 전송·CC replay PASS를 의미하지 않는다.

### 27.3 최종 플러그인 무캐시 빌드와 선택 dry-run 재검증

FA003 수정 뒤 원본 프로젝트에서 `analysis/final-post-missing-credential-fix-2026-11-13/verify.py`가 Gradle **9.7.1/JDK25**로 `clean build validatePlugins --offline --no-build-cache --rerun-tasks -Pkotlin.incremental=false`를 재실행해 **EXIT0/BUILD SUCCESSFUL, validatePlugins PASS**, 일반 JUnit **46 suites, 247 tests = 207 PASS + 40 SKIP + 0 FAIL + 0 ERROR**를 XML로 실제 집계했다. 로컬 실제 `publishAllPublicationsToG29ProbeRepository -PfabricAmperProbeMavenRepo=true --offline`도 EXIT0이며 새 main/sources JAR의 저장소 게시물이 **바이트 동일**했다. 일반 기본 40 SKIP 안에는 opt-in G20 composite 1건이 포함되어 있으며 선택 G20 및 remote Maven dry-run의 별도 PASS를 기본 247개 수치에 중복 추가하지 않는다.

| 최신 최종 산출물 | 마지막 FA003 수정 후 실제 값 |
| --- | --- |
| `build/libs/fabric-amper-0.1.0.jar` | **315,122 bytes**, SHA-256 **`13022B24ADE7C9809400FC6AEBE99E7751783A9DAE7EF8B8C53780FE70B698AD`** |
| `build/libs/fabric-amper-0.1.0-sources.jar` | **67,799 bytes**, SHA-256 **`C3E69E8188B516F4F290588B3342F1D21BB24391658A75B4E26BEA3A5175B510`** |
| `fabric-amper.schema.json` | **26,823 bytes**, SHA-256 **`D5FD84098F1FD6AB926D504F23C69EC991AD17CBCF127B4C14308672B1BC5233`** |
| 마지막 기본 JUnit ZIP | `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-POST-REMOTE-CREDENTIAL-NEGATIVE-FINAL-JUnit-XML.zip`, **46 XML**, SHA-256 **`EA0BA1DA7A3E0EB9FC1346AE1734832C79C581EBB4B79ADF2DB4BB84D5DBAD13`** |
| 실제 빌드/로컬 게시 manifest | `analysis/final-post-missing-credential-fix-2026-11-13/run/manifest.json`, status PASS, JAR ZIP entry 123개/중복 0, Settings/Project marker 각각 하나 |

이 **마지막 생산 코드**에서 remote Maven `--dry-run --configuration-cache --offline` 선택 `FabricAmperRemoteMavenCredentialConfigurationCacheConsumerTest`를 **다시 한 번 실제 실행**, 외부 `BUILD SUCCESSFUL in 1m23s` 및 XML **1 PASS/0 FAIL/0 SKIP**를 확인했다. 내부의 첫 실행은 `Configuration cache entry stored.`, 둘째 실행은 `Reusing configuration cache.` + `Configuration cache entry reused.`; 업로드/비밀 인증 액션 없이 모든 태스크 `SKIPPED`. 근거 `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-POST-REMOTE-FA003-DRYRUN-CC-1PASS.xml`. §26의 이전 수정 직후 XML, 최초 dry-run FAIL XML, 새 음성 시험 최초 실패 로그를 모두 별도 보존한다.

### 27.4 완료 인덱스와 미수락 조건

`analysis/acceptance-evidence-2026-11-13/verify_completion_manifest.py`는 **Gradle 실행/외부 네트워크 없이** 최종 JAR·sources/schema의 실재 SHA/바이트 수, 46개 XML 아카이브, 기본 207/40/0, G11/G30 11건·G11 리소스 6건, G20 복합 1 PASS, 최종 remote dry-run 1 PASS, 원격 FA003 음성 **2건**의 JSON/XML을 교차 검사했다. **EXIT0, `PASS final grounded completion index`**. 결과는 `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-FINAL-2026-11-13-remaining-verification-index.json`에 기록하고 각 증거의 상대경로·SHA-256을 색인했다.

**수동/기술 제약에 따른 정확한 출시 상태:** IntelliJ IDEA GUI의 실제 최초 import·수동/자동 Sync 및 IDE 주입 모델은 현 도구로 직접 관찰한 증거가 없어 **NOT VERIFIED**(Tooling API 모델 PASS와 분리). EULA를 사용자가 직접 검토/동의한 후 dedicated server의 완전 부팅·틱도 **NOT RUN**(서버 Loader initializer만 이전에 PASS). 1.21.11/Java21 실제 레거시 게임 런타임은 NOT RUN. 외부 Modrinth·CurseForge API 업로드/토큰 시험은 명시적으로 금지되어 **0건**. Gradle `--offline`은 루프백 HTTPS Maven 실제 전송까지 차단하고, 네이티브 `PublishToMavenRepository`의 **명시적 PasswordCredentials와 configuration-cache가 비호환**이므로 이 사용자 계약에서 실제 인증 PUT + CC STORE/HIT는 **NOT VERIFIED**. 새 FA003 음성 PASS나 dry-run CC PASS를 이에 대한 대체 증거로 사용하지 않는다.

**최종 판정: 안전하게 실행한 신규 잔여 음성 검증까지 완료했으나 G1–G30 전체 출하 조건이 모두 충족되지는 않았으므로 `NOT APPROVED`.** 기본 40 SKIP, 과거 TestKit FAIL과 실제 Maven CC 제약·GUI/EULA 제약을 그대로 보고한다.

---

## 28. 실제 HTTPS 원격 Maven 인증 PUT 성공과 CC 비호환 실측 분리 (2026-11-14)

**역사 보존:** §1–§27 당시 기록된 실제 실패 및 `NOT VERIFIED`는 당시 실행 상태 그대로 정확하다. 이번에는 그중 **실제 네이티브 HTTPS PUT 성공 여부 하나를 별도의 새로운 독립 시험으로 추가 검증**했다. §26의 `--offline` 실패나 §27의 FA003 음성 검사 결과는 수정·삭제하지 않았다. 프로덕션 소스·Gradle 빌드 아티팩트는 **이번 §28에서 변경하지 않았다**.

### 28.1 외부 API/개인 파일을 건드리지 않는 루프백 전용 실제 게시

신규 Python probe: `analysis/local-https-maven-probe-2026-11-13/verify_online_loopback.py`, 실행 결과 `analysis/local-https-maven-probe-2026-11-13/online-loopback-2026-11-14/`. 기존 `verify.py`와 실패 `run*/`, 생산 소스 및 전역 Gradle 캐시는 덮어쓰지 않았다. 미리 복사·예열된 **프로젝트 내부 `.h` Gradle 홈**과 로컬 G29 Maven plugin marker를 사용했다.

- 테스트 전용 자기서명 인증서(SAN IP `127.0.0.1`)를 **별도 JDK25 PKCS12 truststore**에 등록. 일반 JSSE TLS 인증서·호스트명 검사를 유지했다.
- Maven HTTPS 서버는 **`127.0.0.1` 동적 포트**만 바인딩, 고정 GAV `dev.fabricamper.localhttps:probe-api:0.1.0+26.3`의 JAR/sources/POM/module/metadata/체크섬만 허용했다. Base HTTP 로그에는 인증 헤더/가짜 비밀번호를 저장하지 않았다.
- HTTP·HTTPS 프로세스 환경 프록시와 JVM 시스템 프록시를 **외부 연결을 하지 않고 403만 반환하는 또 다른 `127.0.0.1` 프록시**로 지정했다. JVM `http.nonProxyHosts`는 `localhost|127.0.0.1`만 예외로 설정했다. 측정된 거부 프록시 요청 **0건**이며, 시험의 실제 Maven 대상 또한 `127.0.0.1`이다. 이는 이 Gradle 실행의 프록시 설정에 대한 관측이지 운영체제 차원의 완전한 egress 방화벽 증명은 아니다.
- **`compileJava --offline`** 준비 작업 PASS. Gradle 자체가 loopback HTTPS PUT까지 차단하므로 **게시 태스크에만 `--offline`을 제외**, 실제 `publishMavenJavaPublicationToFabricAmperRemoteRepository --configuration-cache --no-build-cache --no-daemon`를 **두 번 실행**했다. 실제 원격 플랫폼·개인 서버 업로드 0건, 기존 `.minecraft`/계정/EULA 접근 없음.

### 28.2 네이티브 게시 2회 실제 성공 — 25 PUT씩, 4개 주요 아티팩트 완전 일치

**첫 실행과 둘째 실행 모두 Gradle `BUILD SUCCESSFUL in 7s`, 네이티브 게시 태스크 실제 실행.** 서버 로그에서 각 **성공 HTTP 201 + Basic 인증 PUT 25회**, 합계 **인증된 50회**를 확인했다. JAR, sources JAR, POM, Gradle `.module`의 서버 수신 바이트를 매번 소비자 로컬 출력과 정확히 비교했고, 두 시도 사이의 SHA-256 값도 완전히 일치했다. POM의 GAV, `.module`의 component 좌표, 메인 JAR 내부 `fabric.mod.json` 정확히 1개와 Mod ID 역시 검사 통과. Maven metadata와 각 체크섬도 허용 경로로 수신되었으며 인증 없이 성공한 PUT은 0건이었다. URL은 HTTPS이고 서버 401 Basic challenge도 실측했다.

| 서버가 수신한 실제 아티팩트 | SHA-256 |
| --- | --- |
| 메인 `probe-api-0.1.0+26.3.jar` | `35C2485C4D0D5F7EB426C79FC9D3F614206BAFD0C9515C489A3D30B215AFA8F9` |
| `-sources.jar` | `F34FB68364DB455933765A14A1FEBBDC43FF59DADDAF35F2333D9B7F252A8FB2` |
| `.pom` | `BC570A6A4809556CD69A341BA1B9C87F14BEAE942C7A2614CA922990899C56EF` |
| `.module` | `65FAB6171205E344B9B54E54950082124A50EF5936AD22B6F5275C2F17457D74` |

검증 매니페스트: `online-loopback-2026-11-14/manifest.json` (**SHA-256 `9B35DE9FA5E04CDD245705FB88F583CBAB59A58924C25393831961AE7D3BA06E`**). 1·2차 Gradle 로그: `publish-01-with-cc.log` (SHA `E9B76F4F1EF4567A5C5950E8D0260D3CC53CC72651A7D1D53B8D28CFAB140638`), `publish-02-with-cc.log` (SHA `8318B34012549D2406C8892F8BDE2FD909B52FFDE4A404B0581154C970983F1C`). Python 프로세스 **exit 0**. 로그·매니페스트·실험용 private key는 사용자 다운로드/외부 게시 대상이 아니다.

### 28.3 실제 게시의 configuration cache는 2회 모두 DISABLED — 독립 미충족 조건

첫 로그 4행과 둘째 로그 4행 모두 `Calculating task graph as no cached configuration is available`이며, 각각 마지막 행은 **`Configuration cache disabled because incompatible task was found.`**이다. 1·2회 모두 **`configurationCacheStored: false`, `configurationCacheReused: false`, `configurationCacheDisabled: true`**. 앞서 §26에서 확인한 Gradle native `PublishToMavenRepository` + 명시적 `PasswordCredentials`의 Gradle 9.7.1 구성 캐시 비호환성은 재현된다. **실제 인증 HTTPS 게시 PASS ≠ 실제 게시 configuration cache STORE→HIT PASS**다. 반대로 `--dry-run`의 STORE→HIT 1 PASS는 그대로 유효하되 실제 전송이 없다는 제한을 유지한다.

이번 테스트는 게시 시 `--offline`을 사용하지 않았으므로 **오프라인 네이티브 PUT의 성공이라고 서술해서는 안 된다**. 다른 모든 앞선 오프라인 테스트와 별개이며, 외부 전송을 제한하도록 JVM·프로세스 프록시를 분리한 localhost 테스트다.

### 28.4 최신 산출물·출시 판정

§27 이후 production 변경이 없으므로 마지막 확인한 플러그인 JAR SHA-256 **`13022B24ADE7C9809400FC6AEBE99E7751783A9DAE7EF8B8C53780FE70B698AD`**, sources JAR **`C3E69E8188B516F4F290588B3342F1D21BB24391658A75B4E26BEA3A5175B510`**, 기본 JUnit **207 PASS·40 SKIP·0 FAIL/ERROR** 및 최종 로컬 Maven 게시 결과를 유지한다. **이번에 신규 JUnit 전체 재실행을 하거나 production JAR을 재생성한 것은 아니다.** 검증 범위는 **네이티브 인증 HTTPS 게시 2회 실성공**으로 확장됐다.

여전히 G6/G28 실제 IntelliJ GUI 최초 import·수동/자동 Sync, 사용자 EULA 승인 후 G15/G27 dedicated server 완전 부팅·틱, G9 레거시 실제 게임 런타임, **네이티브 원격 게시의 configuration cache HIT**가 미수락이다. 외부 Modrinth/CurseForge API 실제 전송 금지 유지. **G1–G30 전체 정식 출하 승인은 여전히 `NOT APPROVED`.**

### 28.5 별도의 사후 증거 재검증

`analysis/acceptance-evidence-2026-11-13/verify_2026_11_14_native_https.py`가 네트워크/Gradle을 전혀 실행하지 않고 위 `manifest.json` 원본 SHA, 성공한 두 Gradle 게시 로그와 CC 비활성화, 인증 201 PUT 50건(25개 경로 각각 두 번), 서버 종료 이벤트, 기존 독립 소비자 빌드 JAR/sources/POM/module 실제 SHA, settings-only 계약, 메인 JAR 메타데이터 단일성, production main/sources JAR SHA를 재검증했다. **실제 실행 EXIT0, PASS**. 독립 요약 인덱스 `analysis/acceptance-evidence-2026-11-13/FOLLOWUP-2026-11-14-real-native-HTTPS-PUT-summary.json`의 정확한 의미는 `PASS_FOR_CHECKED_EVIDENCE_ONLY`이며, GUI·서버 EULA·CC HIT까지 PASS라는 뜻이 아니다.

---

## 29. `PROBLEMS.md`의 수정 가능한 불편 사항 구현·신규 보고서 검증 (2026-11-14)

**역사적 기록 보존:** §1–§28의 PASS/FAIL/SKIP, 당시 JAR 해시·원격 Maven CC 차단·EULA/IDE 한계를 변경하지 않는다. 이번 절은 사용자의 후속 요청 **“수정가능한 불편함들은 개선해줘”**에 따라 추가한 읽기 전용 편의 기능과 **별도 실행에서 새로 관측한** 검증만 기록한다. 새 YAML 키를 만들지 않았고, `settings.gradle.kts` + 정확히 하나의 `mod.yaml`/`mod.yml` 및 소비자 `build.gradle`·수동 `fabric.mod.json` 불필요 계약을 유지했다.

### 29.1 실제 신규 구현

| 기능 | 구현 및 안전 경계 |
| --- | --- |
| `fabricAmperDoctor` | Gradle/JVM/Java target, Minecraft/Loom 선택 프로필, Kotlin 및 split source sets, 추정 Windows 경로, 알려진 호환성 근거와 **검증 안 된 조합은 UNKNOWN**으로 설명. Gradle 설정 시 외부 resolve·캐시 삭제·토큰 조회 없음. 번들 Loom 버전을 실제로 관측하지 못하면 추측 없이 `unknown` 표시 |
| `fabricAmperExplain` | `dependencies`, `test-dependencies`, Loader `mod.depends` 작성/기본값 출처와 `publish.common`/플랫폼 관계를 각각 분리. Maven GAV를 Loader 모드 ID로 혼동하지 않음; 원격 의존성 해결·계정 조회 없음 |
| `fabricAmperReleasePlan` | 기존 YAML 게시 공통값/플랫폼 override를 기반으로 선택된 최종 실제 JAR의 루트 Loader JSON **정확히 1개**, 일치하는 ID/version, 크기 및 SHA-256을 로컬에서 검사·미리보기. 검증된 아카이브 작업에 의존하며 **HTTP 요청 0·업로드 작업 0·토큰/본문 출력 0**; 외부 프로젝트 ID/승인은 `UNVERIFIED` |
| `fabricAmperPortingReport -PfabricAmperPortTarget=...` | 근거가 구체적으로 한정된 Minecraft 26.3 ↔ 1.21.11 마이그레이션 점검표: Loom remap, Java baseline, mappings, class tweaker/access widener, 의존성/side/Mixin 수동 확인. **자동 포팅 아님**. 유효한 다른 게임 버전을 가진 프로젝트의 `help`/`tasks`는 그대로 작동하고, 지원되지 않는 직접 포팅 보고서 실행만 오류 |
| Windows `fabric-amper.bat` | 비파괴 `doctor --paths`/`doctor --full`, `diagnose`, `explain`, `port <numeric-version>`, `plan` 단축 명령. 기존 Gradle 실행기 검색·`setup`·`init`·빌드/게시 확인 경로 보존. 비정상 옵션/비숫자 포팅 버전은 Gradle 시작 전에 거부 |
| 문서 | `README.md`, `HUMAN.md`, `HUMAN_QuickStart.md`, `HUMAN_QuickGuide.md`, `FabricAmperSKILL.md`, `FabricAmperSKILL_SYN.md`의 설명과 엄격 YAML 완전 예제 회귀 단위 테스트 추가. 연구용 `PROBLEMS.md`에는 실제 구현된 범위와 남은 제안을 분리해 갱신 |

### 29.2 단위·실제 TestKit 수락과 중간 오류

- 첫 새 단위 선택 실행에서 **20 tests 중 2 FAIL**: 포팅 보고서 테스트의 문구 기대값이 실제 의도된 출력과 달랐고, release-plan의 Gson strict trailing junk가 `IOException`으로 빠져 실제 파일 읽기 오류로 잘못 분류됐다. 문구 회귀와 JSON 예외 분류를 **각각 수정**, malformed JSON payload의 오류 메시지는 입력값을 반사하지 않도록 고정했다. 같은 **5개 신규 단위 테스트 클래스 재실행: 20/20 PASS, 0 FAIL/ERROR/SKIP, BUILD SUCCESSFUL in 48s**. 최초 2 FAIL을 소급 삭제하지 않는다.
- 신규 `FabricAmperDeveloperReportsConsumerTest`에 실제 GradleRunner **2 TestKit PASS, 0 FAIL, 0 ERROR, 0 SKIP**, outer `BUILD SUCCESSFUL in 3m 29s`를 관측했다(당시 `TEST-dev.fabricamper.integration.FabricAmperDeveloperReportsConsumerTest.xml`의 `tests=2`·`failures=0`·`errors=0`·`skipped=0`, 이후 `clean` 기본 테스트가 같은 결과 폴더를 재생성). 첫 소비자 26.3에서 `help` 예열 → 세 보고서 오프라인 CC 두 번 실행 **둘째 Reusing configuration cache 검증** → 실제 Java `build`/단일 루트 JSON → `fabricAmperReleasePlan`이 실 JAR SHA-256·ID/version 대조 및 `verifyFabricAmperJar` 선행을 확인했다. fake Modrinth destination은 계획만 표시하고 게시 작업은 수행하지 않았다. 둘째 소비자 26.2는 유효한 non-remap YAML의 `help`/`tasks --all` PASS, 명시 포팅 보고서만 예상한 unsupported-source 오류로 FAIL 처리되는 **음성 테스트 PASS**. 이 테스트는 게임 클라이언트·서버를 시작하지 않았다.
- Windows 배치 양성·음성 인자 검사를 별도로 시행하고, 새 독립 fixture `analysis/devex-smoke/`를 실제 `fabric-amper.bat init devexprobe devex-smoke mod`로 생성했다. **새 프로젝트 자체에 소비자 build.gradle/fabric.mod.json을 작성하지 않았다.** wrapper 자동 탐색 Gradle **9.9.1**/JDK **25.0.4.1**에서 `diagnose` **BUILD SUCCESSFUL in 30s**, `explain` **3s**, `port 1.21.11` **2s**, `plan` **33s** 모두 EXIT0. 실제 `plan`은 JAR `devexprobe-1.0.0+26.3.jar` **1,490 bytes / SHA-256 `25df7deaa1cc53a5bd465889622372f51b1ebb0467b5f570b83849f5b271b6ac`**, 루트 JSON 1개, `remoteRequests: 0`을 출력. `doctor --paths`/`--full` 모두 EXIT0, 로컬 플러그인 마커 확인 및 168자 추정 미러 경로·JDK/Gradle 검사. 이것은 IntelliJ GUI/실게임 런타임 성공의 증거가 아니다.

### 29.3 최종 실제 클린 빌드, 플러그인 아티팩트와 게시 마커

첫 재실행에서는 옵션 `-Pkotlin.incremental=false`가 Windows 명령 래퍼에 의해 별도 `.incremental=false` 태스크로 해석되어 **Selection failed / EXIT1**, 실제 컴파일·테스트 판정 전에 종료됐다. 이전 §23에서도 기록된 CLI 인수 문제이며 제품 assertion FAIL이 아니다. 인수 제거 뒤 실제 Gradle **9.7.1/JDK25**의 `clean build validatePlugins --offline --no-daemon --no-build-cache` **EXIT0 / BUILD SUCCESSFUL in 2m 5s, 11 actionable tasks 모두 executed**. JUnit XML 직접 집계: **53 suites / 276 tests = 234 PASS + 42 SKIP + 0 FAIL + 0 ERROR**. 42 SKIP는 별도 opt-in 런타임/네트워크/GUI 수락이 아니므로 PASS로 세지 않는다. `validatePlugins` PASS.

| 아티팩트·무결성 | 최종 관측 |
| --- | --- |
| `build/libs/fabric-amper-0.1.0.jar` | **368,061 bytes**, SHA-256 **`d06d3d226198b5014383656b506272938acdccaa7b0125917f8ff3c3396dc3cd`** |
| `build/libs/fabric-amper-0.1.0-sources.jar` | **83,746 bytes**, SHA-256 **`84dbc51b996aebf5f05647f2c39e3627a11f521d0f0b00084484f1123465227f`** |
| 바이너리 ZIP | **138 entries / 중복 0**, `dev.fabric-amper.settings` 및 `dev.fabric-amper.project` marker 각 1개 |
| 프로젝트 전용 G29 로컬 Maven | `publishAllPublicationsToG29ProbeRepository -PfabricAmperProbeMavenRepo=true --offline --no-daemon --no-build-cache` **EXIT0 / BUILD SUCCESSFUL in 14s**. 두 marker POM 확인. 게시된 바이너리·소스는 위 `build/libs` 파일들과 **byte-for-byte 동일**, SHA-256 일치. 외부 Maven/Plugin Portal 및 전역 `~/.m2` 게시 없음 |

**출하 판정 유지:** 신규 읽기 전용 DX 작업·개발자 CLI·선택 소비자 검증과 오프라인 클린 빌드는 확인한 범위에서 PASS. 그러나 **G6/G28 IntelliJ 실제 GUI Sync, G15/G27 EULA 승인 전용 서버, G9 레거시 게임 런타임, 실제 원격 Maven 게시의 native Gradle configuration-cache HIT, 외부 Modrinth/CurseForge API 인증·업로드, G29 Kotlin-off cold cache 최적화** 등은 이번 기능 구현으로 닫히지 않았다. `specs.md` **G1–G30 전체 출하 승인은 `NOT APPROVED`**. 사용자 계정·월드·EULA·다른 프로젝트 수정, 원격 업로드 또는 글로벌 캐시 파괴는 하지 않았다.

---

## 30. 최신 YAML 구현과 `fabric-amper.schema.json` 정합성 보정 (2026-11-14)

사용자의 후속 요청 **“그리고 그거 schema.json도 최신 그거대로 수정해줘”**는 실제 루트 스키마 파일 `fabric-amper.schema.json`을 기준으로 적용했다. `src/main/kotlin/dev/fabricamper/config/ModYamlModel.kt`, `ModYamlDecoder.kt`, `ModYamlValidator.kt`, `MinecraftProfile.kt`, `VersionSyntax.kt`와 대조하여 스키마를 업데이트했다. **Schema 버전은 v1 그대로**이며 `schema` 생략은 최신 구현된 v1을 선택한다. 새 Doctor/Explain/ReleasePlan/Porting **Gradle 작업 이름을 가짜 YAML 키로 만들지 않았다**. `fabric/mod`·`fabric/api`·`fabric/library`, `publish.common`, optional remote Maven/Modrinth/CurseForge 및 기존 `publishing.repository` 계약도 유지했다.

### 30.1 수정한 schema 조건

| 부분 | 실제 Kotlin 동작에 맞춘 편집기/Schema 검사 |
| --- | --- |
| Minecraft 26.1+ 프로필 | non-remapping Mojang 공식 매핑만 허용, 명시적 `settings.java.release`가 있으면 **25**만 허용. 생략된 매핑/Java는 기존 내부 기본값 적용. 26.1/26.2/26.3, 향후 메이저 및 0-padding 숫자 프로필 경계 검사 |
| `mod.accessWidener` | 현대 26.1+는 `.classtweaker`, legacy remap은 `.accesswidener` 확장자를 요구. 내용 헤더·파일 존재 확인은 실제 Kotlin 검증기 담당 |
| Loader/어댑터 | `mod.depends` 등 버전 관계 map key에 Loader mod-ID 문법 적용, 예약된 `mod.languageAdapters.kotlin` 재정의 거부 |
| 사용자 지정 `repositories[].maven` | `https://` 형식 유지, URL user-info 및 fragment 금지. Kotlin의 현재 커스텀 의존성 저장소에서는 URL query를 허용하므로 새 Schema도 불필요하게 거부하지 않음. 원격 게시용 `publish.maven`의 더 엄격한 URL 조건과 구분 |
| 리소스·아이콘 | Mixin/accessWidener/icon의 상대 리소스 경로에 drive/backslash/부모 경로/빈 분할·시작 점 경로 제한, 아이콘 `.png` 확장자 검사, 아이콘 크기 map 키가 10자리를 초과하면 거부. 파일·심볼릭 링크·PNG 바이트 검사와 정확한 32-bit 정수 범위는 실제 Kotlin 검증이 우선 |
| 배포 changelog | `publish.common`/`publish.modrinth`/`publish.curseforge`의 `changelogFile`에 절대·상위 경로 이동, `.`/`..` 세그먼트, 백슬래시·콜론·빈 경로 세그먼트 금지. 실제 파일 존재/파일 경계 확인은 Kotlin 담당 |
| 배포 관계 | `publish.common.dependencies`, `publish.modrinth.dependencies`, `publish.curseforge.relations`의 **완전히 동일한** 객체 중복을 Draft-07 `uniqueItems`로 거부. 서로 다른 필드 값을 가진 **동일 slug/ID 중복**은 Draft-07만으로 일반적으로 표현할 수 없어 기존 Kotlin validator가 검사한다고 명시 |

### 30.2 새 고정 회귀 및 실제 실행 결과

- 신규 독립 **오프라인** 재검증 스크립트 `analysis/schema-contract-2026-11-14/verify.py` 작성(공식 Python `jsonschema.Draft7Validator` + PyYAML). **EXIT0 / `PASS Draft-07 schema, 54 positive/negative fixtures, 3 schema examples, 13 complete manual YAML fences`**. 정상/오류 케이스에는 schema 생략/v1, product 별칭/group, modern/legacy mappings·Java, class tweaker/access widener, Loader ID, 사용자 지정 HTTPS userinfo/fragment/query, PNG 경로/확장자, changelog traversal, 공통·Modrinth·CurseForge 관계 중복, 누락된 게시 자격증명 *필드 이름*이 포함된다. 임의의 네트워크 연결·원격 게시·Gradle 캐시 삭제를 수행하지 않는다.
- Gradle **9.7.1/JDK25** 선택 회귀 명령: `test --tests dev.fabricamper.unit.FabricAmperDocumentationContractUnitTest --tests dev.fabricamper.unit.ModYamlLoaderUnitTest --tests dev.fabricamper.unit.PublishCommonAndSchemaFallbackUnitTest --tests dev.fabricamper.unit.FabricAmperPublishMetadataUnitTest --offline --no-daemon --no-build-cache`. **EXIT0 / `BUILD SUCCESSFUL in 22s`**. 실제 JUnit XML 집계: **4 suites / 21 tests = 21 PASS, 0 SKIP, 0 FAIL, 0 ERROR**. 이것은 **선별 재실행**이므로 §29 당시 기본 전체 `276=234 PASS+42 SKIP`에 중복 합산하거나 새 전체 빌드 성공이라고 주장하지 않는다. Gradle test XML 디렉터리는 필터 실행으로 덮어써졌다.
- 수정 후 `fabric-amper.schema.json`: **35,728 bytes**, SHA-256 **`7d12a0d5400b32914bb4be3a4cb2f5d0c6a17041db9b58f358053fdf7c30a36c`**. Python Draft-07 스키마 자체 유효, 포함된 예제 3개와 완전한 매뉴얼 YAML fenced block 13개 스키마 통과.

**범위·출하 판정:** JSON Schema는 편집기에서 빠르게 확인할 수 있는 구조·조건용이며, YAML의 실제 허용/거부는 strict Kotlin loader/validator가 결정한다. 로컬 파일 존재·심볼릭 링크, Loader 버전 predicate의 실제 의미, 여러 객체에 걸친 ID 충돌, IntelliJ GUI, dedicated server, 공개 게시 승인 등은 JSON Schema만으로 입증할 수 없다. `specs.md` 전체 G1–G30 출하 판정 **`NOT APPROVED`**는 §29와 동일하게 유지하고, 다른 프로젝트·계정·서버·전역 캐시는 변경하지 않았다.

---

## 31. QuickStart + QuickGuide 영문 단일 문서 병합 (2026-11-14)

사용자의 후속 문서 요청: **“QuickStart 내용을 QuickGuide로 병합하고, QuickGuide의 내용을 영어로 작성할 것. QuickGuide가 기존 QuickStart의 초단기 입문서 + 기존 QuickGuide의 기능별 치트시트 역할을 겸용할 것.”** 구현을 위해 아래 **문서/문서 전용 테스트만** 수정했다. 이 작업에서 플러그인 Kotlin 소스·바이너리·Gradle 실행기·외부 게시 동작은 바꾸지 않았다.

- **`HUMAN_QuickGuide.md`**: 기존 한글 QuickGuide를 **영어 단일 정본**으로 전면 재작성했다. 맨 앞의 짧은 `doctor`→`setup`→`init`→`build` 명령, `settings.gradle.kts`+`mod.yaml`+실제 Java 3파일 템플릿과 `validate/build/verify` 확인 단계를 통합했다. 그 뒤에 Java/Kotlin, 클라이언트 분리, Mixin/Class Tweaker/레거시 AW, Gradle 의존성·Loader 의존성·플랫폼 관계 구분, 테스트 scope, `publish.common` 공유 release/프로젝트 ID/타입 매핑, API/library, 명시적 Maven·Modrinth·CurseForge 게시, 배치 명령별 side effect, FA 오류 치트시트를 넣었다. **완전 예제는 완전 예제라고, 부분 YAML은 FRAGMENT라고 명시**해 최상위 키 중복 복사 오류를 예방했다.
- **`HUMAN_QuickStart.md`**: 별도 중복 튜토리얼을 삭제하고 기존 북마크·링크가 깨지지 않도록 **11줄 영문 연결 안내**만 유지했다. 실제 단일 유지보수 대상은 QuickGuide다.
- **`HUMAN.md`**, **`PROBLEMS.md`**: 새 영문 QuickGuide를 초단기 시작+기능 치트시트의 단일 진입점으로 링크하도록 정리했다. 오래된 §1–§30 QA 기록은 수정하지 않았다.
- **`src/test/kotlin/dev/fabricamper/unit/FabricAmperDocumentationContractUnitTest.kt`**: 기존 모든 완전 YAML 예제를 실제 strict decoder로 읽는 회귀에, **QuickGuide의 초단기·기능별·릴리스·CLI·진단 주요 섹션, 3파일 샘플, 한글 완성형 비포함, QuickStart 링크 안내**를 확인하는 독립 테스트를 추가했다.

**검증 증거:** Gradle 9.7.1 / JDK 25에서 `test --tests dev.fabricamper.unit.FabricAmperDocumentationContractUnitTest --offline --no-daemon --no-build-cache` → **EXIT0 / BUILD SUCCESSFUL in 29s**. 결과 XML **2 tests / 2 PASS / 0 SKIP / 0 FAIL / 0 ERROR**. 프로젝트 기존 오프라인 스키마 회귀 `python analysis/schema-contract-2026-11-14/verify.py` → **EXIT0 / Draft-07 schema 정상, 54 positive/negative fixtures, 3 schema examples, 12 complete manual YAML fences PASS** (중복 QuickStart 완전 예제 제거로 전 절의 13→12가 정상). 추가 Python 검증: 두 문서의 Markdown 상대 링크 **29개 중 누락 0개**, QuickGuide 완성형 Hangul 문자 **0개**, QuickGuide **329줄**, QuickStart **11줄**. `HUMAN_QuickGuide.md`: **23,103 bytes**, SHA-256 **`798486c2c3302a7dc740ef4710cfee83bf1084293505829b775f0abe42bd9e6d`**. `HUMAN_QuickStart.md`: **1,164 bytes**, SHA-256 **`22aba6e11b8e4aed79bbcba96eb9abf0a38bf0e67e7e88745f61fafba0b2465a`**.

**한계 및 출하 판정:** 본 회차는 **문서 병합과 정확성 회귀**만 검증했고 실제 소비자 새 빌드, 원격 전송, Minecraft 게임 실행 또는 IntelliJ GUI 테스트를 새로 수행했다고 주장하지 않는다. 독립 소비자와 출하 게이트 미해결 사항은 §29의 범위를 유지한다. 전체 `specs.md` G1–G30 출하 판정은 계속 **`NOT APPROVED`**.

---

## 32. Windows `fabric-amper.bat init` YAML 자동완성·최신 스키마 선택 (2026-11-14)

사용자 요청: `fabric-amper.bat init` 생성 YAML **첫 줄**에 정확히 `# yaml-language-server: $schema=./schemas/mod.schema.json` 추가, YAML 데이터 필드 `schema: 1`은 **작성하지 않기**, 추후 플러그인 스키마 버전이 바뀌어도 명시적으로 옛 YAML 스키마 버전을 고정하지 않도록 하기.

**구현:** `fabric-amper.bat`의 기존 `init` 경로는 새 소비자 생성 **전에** producer 루트 `fabric-amper.schema.json` 존재를 확인한다. 새 프로젝트에 `schemas/` 디렉터리를 만들고 `copy /b /y`로 현재 producer 스키마를 `schemas/mod.schema.json`에 **바이트 그대로** 복사한다. 그 후 `mod.yaml` 첫 줄에 요청한 `yaml-language-server` 주석을 기록하고, 기존 Java-only 26.3 스타터 `product/mod/minecraft/settings/repositories` YAML 및 Java 소스·Settings plugin을 생성한다. `schema:` 루트 키는 생성하지 않는다. 생성 성공 조건에는 JSON Schema 파일 존재도 포함한다. 소스에 스키마가 없으면 잘못된 편집기 참조를 가진 프로젝트를 만들기 전에 즉시 실패한다. 스키마의 실제 YAML 문법 버전은 `ModYamlDecoder`의 **생략 시 최신 내장 버전**(`FabricAmperSchemaVersion.LATEST`, 현재 1)을 따른다. 초기화 이후 기존 소비자의 `schemas/mod.schema.json`은 자동 갱신되지 않는 **생성 시점의 스냅샷**으로, 플러그인 문법을 업그레이드할 때 업데이트해야 한다.

**관련 변경:** 영어 단일 가이드 `HUMAN_QuickGuide.md`의 명령 설명·수동 생성 트리·완전한 YAML 예제에 자동완성 주석과 schema 복사 설명 추가. `HUMAN.md`의 Windows `init` 및 에디터 자동완성 FAQ 수정. `FabricAmperDocumentationContractUnitTest.kt`의 스타터 YAML 앞줄 기대값 갱신. 신규 Windows 전용 `FabricAmperInitSchemaUnitTest.kt`는 Gradle 중첩 실행·원격 접속 없이 **실제 `cmd.exe`로 `init`을 실행**하여 mod/API 각각 정해진 경로 생성, 주석 첫 줄, YAML `schema:` 미존재, Kotlin `ModYamlLoader.load` 기본 버전, producer와 스키마 바이트 동일, Settings·Java 생성, 금지된 소비자 `build.gradle(.kts)`·작성 JSON 부재, 같은 대상 재실행 시 exit **3** 무덮어쓰기를 검증한다. 임시 테스트 파일은 **producer `build/` 내부**에만 생성한다.

**실행 근거:**

- 실제 Windows `cmd /c fabric-amper.bat init autocompletemod analysis\init-schema-smoke-2026-11-14\mod mod`: **EXIT0**, `fabric/mod`; `... init autocompleteapi analysis\init-schema-smoke-2026-11-14\api api`: **EXIT0**, `fabric/api`. 동일 mod 대상 `init` 재실행: **EXIT3**, 명시적 기존 프로젝트 무덮어쓰기. 두 YAML 모두 첫 줄 정확히 일치하고 `schema` 루트 키 부재; 둘 다 현재 루트 스키마와 byte-identical SHA-256 `7d12a0d5400b32914bb4be3a4cb2f5d0c6a17041db9b58f358053fdf7c30a36c`, Draft-07 스키마 유효성/각 인스턴스 검증 PASS.
- 새 소비자 `analysis/init-schema-smoke-2026-11-14/mod/`에서 Gradle **9.7.1/JDK25** `validateFabricAmper --offline --no-daemon --no-build-cache`: **EXIT0 / BUILD SUCCESSFUL in 16s**, 실제 YAML comment가 strict Kotlin loader에 영향 주지 않음.
- 선별 회귀 `test --tests dev.fabricamper.unit.FabricAmperInitSchemaUnitTest --tests dev.fabricamper.unit.FabricAmperDocumentationContractUnitTest --offline --no-daemon --no-build-cache`: 첫 시도 **3 tests 중 기존 문서 기대값 1 FAIL**, 원인: 앞부분 YAML `product:`가 항상 즉시 첫 줄이라는 과거 문서 assertion. 기대값을 요청한 주석 + `product:` 형식으로 고쳐 같은 필터 재실행: **EXIT0 / BUILD SUCCESSFUL in 14s**, XML **2 suites / 3 tests = 3 PASS, 0 FAIL, 0 ERROR, 0 SKIP**. 신규 실제 Windows `init` 통합 단위 테스트는 첫 시도부터 PASS.
- `python analysis/schema-contract-2026-11-14/verify.py`: **EXIT0**, Draft-07/54 positive-negative/스키마 예제 3/매뉴얼 완전 YAML 12 PASS. `fabric-amper.bat` **560 CRLF 줄/0 bare LF** 보존. 수정 bat 22,758 bytes, SHA-256 `27936da15e2dbb72aa14c993b4292b92d97f7a8ef29f4cb2e0fad6e93cad9a15`.

**범위:** 현재 플러그인의 실제 스키마 v1 및 init 기본 Minecraft 26.3 프로필에서 검증했다. 미래 **새로운 필수 YAML 필드, Gradle 플러그인 바이너리/버전 호환성**은 미래 릴리스에 맞게 init 구현과 producer schema 자체를 유지보수해야 한다. 이번 수정은 **schema 버전 고정 및 깨진 에디터 참조 문제를 해결**하지만, 무조건적인 미래 비호환 설정의 자동 변환 기능을 의미하지 않는다. 글로벌 캐시 삭제·원격 게시·실제 게임 실행은 하지 않았다. §29의 전체 출하 판정은 `NOT APPROVED`로 유지한다.

---

## 33. develop 전용 Windows init의 JitPack 전환 (2026-11-14)

사용자 요구: 메인 브랜치를 **수정하지 않고 `develop`에서만** `fabric-amper.bat setup`을 제거하고, 새 `init` 소비자의 `settings.gradle.kts`가 **GitHub JitPack `com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.0`** 을 `pluginManagement.resolutionStrategy.eachPlugin { useModule(...) }`로 사용하도록 변경. 단순 생성 여부뿐 아니라 실제 Gradle 초기화를 검사하며 **커밋·푸시 없이 검증까지만** 진행.

**변경:**
- `fabric-amper.bat`: `setup` 명령 분기·레이블·로컬 프로듀서 Maven bootstrap 및 관련 프롬프트 제거. `doctor`에서 producer 마커 유무를 JitPack 유무로 오인하지 않도록 로컬 마커 검사를 없애고 사용 좌표만 안내한다(원격 실시간 확인이라고 주장하지 않음).
- `init`이 생성하는 Settings: `maven("https://jitpack.io")`, `maven("https://maven.fabricmc.net/")` (사용자 예시의 `https://fabricmc.net`이 아닌 **정확한 Fabric Maven**), `gradlePluginPortal()`, `mavenCentral()`; `resolutionStrategy.eachPlugin` 안에서 `dev.fabric-amper.settings` 요청에 `useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.0")` 사용. `plugins` 요청 ID/버전 `dev.fabric-amper.settings:0.1.0` 유지. GitHub 계정명은 원격 URL에 실제 표시되는 **`howShouldIChooseMyUsername`** 대소문자를 적용.
- 이전 자동완성 첫 줄 `# yaml-language-server: $schema=./schemas/mod.schema.json`, 로컬 JSON Schema 복사, 스키마 필드 미작성 및 Java/API 프로필은 보존했다.
- Windows 실제 생성 회귀 `FabricAmperInitSchemaUnitTest`에 JitPack 좌표·URL·pluginManagement mapping·기존 로컬 마커 경로 제거·`setup` 미지원 검증을 추가. 영어 `HUMAN_QuickGuide.md`, `HUMAN.md`와 두 AI 스킬 안내를 새 Settings 형식에 맞게 보완.

**실행 결과:**
- `git switch develop` 성공. 작업 시작 시 `develop=a44c889`, `main=9f87842` (사용자가 main에서 별도로 `jitpack.yml`을 추가한 이력)이며 `develop`에서만 위 파일을 수정했다. 태그 `v0.1.0`은 원격 main의 `9f87842`를 가리킨다.
- `cmd /c fabric-amper.bat init jitpackprobe build\jitpack-init-probe-2026-11-14 mod`: **EXIT0**, 실제 생성된 `settings.gradle.kts` 18행의 URL/좌표/플러그인 조건문 모두 확인. YAML 편집기 주석/JSON schema 포함. `cmd /c fabric-amper.bat setup`: 예상한 **EXIT2 / Unknown command**, help에는 setup 미표시.
- 실제 생성 프로젝트에서 Gradle 9.7.1 / JDK25 `help --no-daemon --no-build-cache --console=plain --refresh-dependencies`: **EXIT1 / BUILD FAILED in 1m 41s**, 요청 ID는 올바르게 `com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.0`로 치환됐으나 모든 플러그인 저장소에서 해당 아티팩트 해석에 실패. 정확한 메시지: `could not resolve plugin artifact 'com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.0'`. 별도 HTTPS HEAD `https://jitpack.io/com/github/howShouldIChooseMyUsername/Fabric-Amper/v0.1.0/Fabric-Amper-v0.1.0.pom`은 15초 동안 0바이트 수신 후 **curl exit28 timeout**. 따라서 오류가 **태그 아티팩트 미게시·JitPack 서비스/프라이빗 캐시·현재 네트워크 제한 중 무엇 때문인지 확정 불가**. 태그만 생성했다고 실제 JitPack Maven 모듈 준비가 입증되지는 않는다. `build` 및 게임 런타임 **미검증**.
- `test --tests dev.fabricamper.unit.FabricAmperInitSchemaUnitTest --tests dev.fabricamper.unit.FabricAmperDocumentationContractUnitTest --offline --no-daemon --no-build-cache --console=plain`: **EXIT0 / BUILD SUCCESSFUL in 32s**, 2개 suite 합계 **3 tests / 3 PASS / 0 FAIL / 0 ERROR / 0 SKIP**. 실제 Windows BAT mod/API init과 같은 대상 재실행 무덮어쓰기를 포함한다.
- `python analysis/schema-contract-2026-11-14/verify.py`: **EXIT0**, Draft-07/54 fixture/3 schema examples/12 manual YAML fences PASS.

**결론 및 제한:** 이 단계에서는 **Windows 명령 인터페이스와 JitPack 템플릿 생성은 PASS**, **실제 원격 플러그인 해석은 FAIL/원인 미확정**이므로 JitPack 기반 소비자 `help/build` 성공이라고 선언하지 않는다. JitPack의 태그 빌드 로그/해당 좌표 POM/JAR 접근성 및 공개 전환 직후의 캐시 상태를 확인하고 다시 실행해야 한다. `main` 수정, 새 태그·원격 게시, 삭제/전역 캐시 정리 없음. 전체 G1–G30 출하 판정은 여전히 **NOT APPROVED**.

---

## 34. JitPack 아티팩트 게시 완료 이후 실제 Settings 플러그인·mod/API 소비자 재검증 (2026-11-14)

사용자가 JitPack 페이지의 `dependencyResolutionManagement { repositories { maven { url = uri("https://jitpack.io") } } }` 및 `implementation("com.github.howShouldIChooseMyUsername:Fabric-Amper:Tag")` 예시를 제시하며 재시험을 요청함. **그 예시는 일반 라이브러리 의존성용**이고 Fabric Amper의 `settings.gradle.kts`에서 사용하는 **Settings 플러그인의 `plugins {}` 해석용이 아님**. Gradle의 `pluginManagement.repositories` + `resolutionStrategy.eachPlugin { useModule(...) }` 방식은 유지했다. 이후 나온 실제 성공 결과가 **직전 §33의 일시적 JitPack 미해석 실패를 대체한다**. 이전 오류 기록은 원본 증거로 보존한다.

**원격 게시 상태 확인:** JitPack API `https://jitpack.io/api/builds/com.github.howShouldIChooseMyUsername/Fabric-Amper/v0.1.0` 반환 `status=ok`, `version=v0.1.0`, `commit=9f87842329bf8a2d4b6c674b15b6191642e41421`, JDK 25, `private=false`, 모듈 `dev.fabric-amper.project.gradle.plugin`, `dev.fabric-amper.settings.gradle.plugin`, `fabric-amper`. `https://jitpack.io/com/github/howShouldIChooseMyUsername/Fabric-Amper/v0.1.0/Fabric-Amper-v0.1.0.pom` **HTTP 200**, 최상위 POM은 집합 모듈 의존성 `com.github.howShouldIChooseMyUsername.Fabric-Amper:fabric-amper:v0.1.0`을 참조함. 실제 구현 모듈 `.../Fabric-Amper/fabric-amper/v0.1.0/fabric-amper-v0.1.0.pom`, `.jar` **HTTP 200**이며 JAR **368,061 bytes**, POM은 Loom/Kotlin/SnakeYAML/Gson 런타임 의존성을 기록. 별도 JitPack `0.1.0` 경로도 HTTP 200이었지만 `init`은 기존 요구대로 Git 태그 **`v0.1.0`**을 고정한다. 네트워크가 응답한 이후 동일한 `useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.0")` 템플릿이 실제로 동작했으므로 아티팩트 집합/구현 모듈을 억지로 변경하지 않았다.

**신규 실제 소비자 증거:**
- 앞서 `init`으로 만들어진 `build/jitpack-init-probe-2026-11-14` (`fabric/mod`)의 **기존 생성 Settings를 수정하지 않고** Gradle **9.7.1/JDK25** `help --no-daemon --no-build-cache --console=plain --refresh-dependencies`: **EXIT0 / BUILD SUCCESSFUL in 28s**, Loom 1.17.21 적용. 이어 `validateFabricAmper build verifyFabricAmperJar`: **EXIT0 / BUILD SUCCESSFUL in 12s** (컴파일/메타데이터 생성/검증 포함). 실제 Windows 래퍼 `cmd /c ..\..\fabric-amper.bat build`도 **EXIT0 / BUILD SUCCESSFUL in 6s** (해당 래퍼가 캐시된 Gradle 9.9.1을 자동 선택했으며 빌드/검증 성공).
- `cmd /c fabric-amper.bat init jitpacktestapi build\jitpack-api-probe-2026-11-14 api`: **EXIT0**, 별도 API 소비자 생성. 이 API 프로젝트에서 Gradle 9.7.1/JDK25 `help validateFabricAmper build verifyFabricAmperJar --no-daemon --no-build-cache --console=plain`: **EXIT0 / BUILD SUCCESSFUL in 10s**.
- 두 소비자의 실제 배포 JAR (`jitpackprobe-1.0.0+26.3.jar`, `jitpacktestapi-1.0.0+26.3.jar`)을 ZIP으로 열어 root `fabric.mod.json`이 **정확히 1개**이고 YAML에서 생성한 정확한 ID/version(`jitpackprobe`, `jitpacktestapi`, `1.0.0+26.3`)을 보유하는 것까지 확인했다.
- producer 선별 회귀 `test --tests dev.fabricamper.unit.FabricAmperInitSchemaUnitTest --tests dev.fabricamper.unit.FabricAmperDocumentationContractUnitTest --offline --no-daemon --no-build-cache --console=plain`: **EXIT0 / BUILD SUCCESSFUL in 32s**, Windows 실제 init 및 문서 단위 회귀. `main` 또는 JitPack에 재게시/변경은 전혀 하지 않음.

**최종 판정:** 이 버전에서 `fabric-amper.bat init`의 JitPack 플러그인 해석, **Java-only mod/API 생성→Gradle 구성→컴파일→메타데이터→JAR 검증은 실제로 PASS**. 직전의 실패는 해당 시점의 JitPack 아티팩트 미준비/접근 지연으로 보는 것이 타당하나 단일 원인 확정은 하지 않는다. GUI Sync, Minecraft 실제 실행, 원격 게시, 나머지 G1–G30 출하 조건이 완료됐다는 의미는 아니다. 작업 범위는 **`develop`의 미커밋 파일 변경·검증만**이며 `main`, 기존 tag, 원격 게시 상태는 변경하지 않았다.

---

## 35. v0.1.1 main 공개용 이관·릴리스 패키징 (2026-11-14)

사용자 요청: 기존 `main` 이력을 `develop` 전체 이력으로 병합하지 말고 **직전 develop의 변경 파일만 선별**하여 `main`에 이관. `README.md` 맨 위에 정확히 `[![](https://jitpack.io/v/howShouldIChooseMyUsername/Fabric-Amper.svg)](https://jitpack.io/#howShouldIChooseMyUsername/Fabric-Amper)` 배지 삽입. `build.gradle.kts`의 버전과 Windows `init`의 플러그인 요청 버전은 **`0.1.1`**, JitPack Git 태그는 **`v0.1.1`**로 변경. v0.1.0과 동일한 형태의 GitHub Release를 준비하고 **추가 테스트는 실행하지 않음**.

**적용 내용:** 현재 `develop` 작업 트리를 수정하거나 변경사항을 초기화하지 않도록 `main`을 **별도 Git worktree**에 열었다. 이곳에 develop의 수정된 `fabric-amper.bat`, `HUMAN.md`, `HUMAN_QuickGuide.md`, `FabricAmperSKILL.md`, `FabricAmperSKILL_SYN.md`, `QA_REPORT.md`, 두 Windows/documentation unit test 파일 **8개만** 명시적으로 복사했다. 기존 main의 README 및 `jitpack.yml` 등 다른 파일은 유지했다. main에서 release version 상수를 `0.1.1`로 올리고 Windows wrapper의 `FA_VERSION`, 출력 안내, 생성 Settings의 `useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.1")` 및 `id("dev.fabric-amper.settings") version "0.1.1"`을 일치시켰다. 영문 README/사용 설명서/에이전트 가이드의 새 설치 경로와 버전 예시를 갱신했고, 로컬 G29 메타데이터 경로에 버전 `0.1.0`이 하드코딩된 **네 개 integration test 파일의 향후 기대 경로만 `0.1.1`로 갱신**했다. 각 테스트가 만드는 별도 모드의 `0.1.0+26.3` 같은 **모드 자체의 예제 버전은 유지**했다. 기록용 역사적 QA 내용 및 `legacy_README.md`도 보존했다.

**실행한 명령과 제한:** 패키징만을 위해 Gradle **9.7.1 / JDK 25** 에서 `publishAllPublicationsToG29ProbeRepository -PfabricAmperProbeMavenRepo=true -x test --offline --no-daemon --no-build-cache --console=plain` 실행: **EXIT0 / BUILD SUCCESSFUL in 28s**. 새 `fabric-amper-0.1.1.jar` (**368,061 bytes**)와 `fabric-amper-0.1.1-sources.jar` (**83,746 bytes**), v0.1.1의 Settings/Project 마커 POM, 모듈 POM 및 Gradle `.module` 생성. 별도 패키징 스크립트는 바이트 동일성, plugin marker의 실제 구현 의존성, ZIP 엔트리/CRC 및 SHA-256을 확인하였지만 이는 **배포물 파일 구조 확인일 뿐 단위·통합 테스트가 아님**. 소스 변경에 대한 `test`, `check`, Gradle TestKit, consumer `help/build`, Minecraft runtime, IntelliJ Sync 등의 테스트는 **사용자의 명령에 따라 실행하지 않았다**. 이전 §34의 `v0.1.0` 소비자 PASS가 `v0.1.1`의 새 테스트 PASS를 의미하지 않음. 모든 G1–G30 승인 게이트는 종전과 동일하게 **`NOT APPROVED`**.

**릴리스 파일 준비:** `build/release/v0.1.1` 아래에 `fabric-amper-0.1.1-maven-repository.zip`, 동일 빌드의 binary/sources JAR, `fabric-amper.bat`, `fabric-amper.schema.json`, `SHA256SUMS.txt`, 영문 `RELEASE_NOTES.md`를 생성했다. ZIP에 `maven-repo/` 접두와 **두 plugin marker + 실제 구현 JAR/POM/module**이 포함됨. ZIP SHA-256 `97ca3dbd1cb1629a823603e6de981df528d48faca1bbbccf4fc6e4c294880398`, Windows wrapper SHA-256 `a9d1f2ef812389976d7bb072f6a0769609f42fd79541d79bc37f1532bc93bc04`. 릴리스 파일만 GitHub 자산으로 올리며 로컬 `build/` 전체나 `analysis/` 증거를 main 커밋에 포함하지 않음.

## 36. Windows doctor --fix 및 init --here, AI 스킬 BAT 우선 규칙 (2026-11-14)

**사용자 요구:** (1) Windows `doctor --fix`를 명시했을 때 JDK25 없거나 잘못됐으면 공식 winget ID `EclipseAdoptium.Temurin.25.JDK` 설치, Gradle이 없으면 먼저 `Gradle.Gradle` winget 설치를 시도하고 해당 패키지를 사용할 수 없으면 공식 Gradle 9.7.1 ZIP fallback, 사용자 환경 변수 설정 갱신 및 선택적 소비자 Settings/VS Code 스키마 매핑 보완. (2) `init night_vision --here`은 현재 폴더에 기존 BAT/스킬/`.md`/`.gitignore` 같은 **도구 파일**이 있어도 보존하며 생성하되, 기존 `mod.yaml`, `mod.yml`, `settings.gradle.kts`, `build.gradle(.kts)`, `src` 등 프로젝트 파일 충돌은 무조건 오류(덮어쓰기 금지). (3) `FabricAmperSKILL.md` 및 `FabricAmperSKILL_SYN.md`에 BAT가 지원하는 명령은 BAT로 우선 수행, 지원하지 않는 것은 기존 native Gradle/Java 방식으로 수행하는 명시적 에이전트 규칙. `develop`에서 완료 및 전체 테스트 통과 시 **변경 내용만 main에 선별 동기화**.

**구현:** `fabric-amper.bat`에 `doctor --fix`/`--fix --dry-run`과 `init <id> --here [mod|api]` 추가. `scripts/fabric-amper-doctor-fix.ps1`를 새로 작성해 완전한 Windows checkout의 batch로만 실행한다. 기존 JDK25/호환 Gradle 경로를 먼저 확인하며, **명시적 --fix가 아니면 설치·네트워크 작업이 없음**. JDK25가 없으면 winGet Temurin JDK25; Gradle이 없으면 winget Gradle.Gradle을 시도한 후 사용자별 `%LOCALAPPDATA%\FabricAmper\tools\gradle-9.7.1`에 공식 HTTPS ZIP과 **공식 .sha256의 해시를 일치 확인 후** 압축을 해제한다. 글로벌 Gradle 캐시를 삭제하거나 무검증 archive를 실행하지 않는다. 현재 수정 프로세스 및 **사용자 범위의** JAVA_HOME/PATH를 갱신하고, 자식 BAT 프로세스가 부모 터미널의 실시간 PATH까지 변경할 수는 없다는 한계를 정직하게 안내한다. 기존 `mod.yaml` **또는** `mod.yml` 한 개가 있는 소비자에서만 빠진 Settings JitPack bootstrap, local schema 사본, `.vscode/settings.json`을 보완한다. 기존 파일은 덮어쓰지 않고 두 YAML 파일 공존이나 소비자 `build.gradle(.kts)` 충돌 시 skip한다. `doctor --fix --dry-run`은 설치·다운로드·파일/레지스트리 변경 없이 계획만 출력하며 내부 테스트 전용 `-Preview -AssumeMissing`도 같은 무변경 원칙을 지킨다.

`init --here`은 `-C`로 선택한 폴더 또는 현재 디렉터리를 사용한다. **쓰기 이전 사전검사**에서 기존 YAML, Settings, `build.gradle(.kts)`, `src` 또는 `schemas/mod.schema.json` 중 하나라도 있다면 EXIT3으로 거부하며, 미지원 인수/잘못된 `--here` 위치는 EXIT2. 도구 및 비프로젝트 문서 파일은 모두 보존하고 `.vscode/settings.json`이 이미 존재하면 덮어쓰지 않는다. 평소 신규 `init`도 `.vscode/settings.json`의 YAML mapping을 추가한다. 이전 `# yaml-language-server: $schema=./schemas/mod.schema.json` 주석, `schema: 1` 미생성, JitPack bootstrap 유지. 영어 `HUMAN.md` 및 `HUMAN_QuickGuide.md`도 새 명령을 설명하고, 두 AI 스킬에 **BAT 지원 → BAT 우선, 미지원 → 기존 네이티브 명령**, 확인되지 않은 `./gradlew` 추측 실행 금지와 opt-in 설치 승인 경계를 명문화했다.

**검증 (Windows, Gradle 9.7.1/JDK25):**
- `cmd /c fabric-amper.bat doctor --fix --dry-run`: **EXIT0**, JDK25/Gradle 기설치 상태의 무변경 미리보기. 의도적으로 없는 상태를 강제하는 내부 Preview 역시 `winget EclipseAdoptium.Temurin.25.JDK` 및 winget→공식 Gradle ZIP SHA256 fallback 계획을 표시하고 **파일/레지스트리/다운로드 변경 없이 EXIT0** (자동 JUnit 포함).
- 별도 `build/here-probe-2026-11-14/` 도구 전용 폴더에서 `-C ... init night_vision --here`: **EXIT0**, 기존 skill/.gitignore 그대로 유지, `mod.yaml`, `settings.gradle.kts`, schema/.vscode JSON/Java initializer 생성. 동일 프로젝트 재실행 **EXIT3**; 개별 기존 `mod.yaml`, `mod.yml`, Settings, build script 2종, `src`, schema 충돌 사례 각각 **EXIT3**이며 기존 파일 byte 불변. 잘못된 인수 **EXIT2**. 첫 실행에는 중첩 BAT 분기의 `--here` 위치 오류가 EXIT0을 반환하는 문제가 있어 전용 레이블로 이동해 EXIT2로 수정한 후 다시 확인했다.
- 생성된 **실제 --here 소비자**에서 `help validateFabricAmper build verifyFabricAmperJar --offline --no-daemon --no-build-cache`: **EXIT0 / BUILD SUCCESSFUL in 15s**, 실제 Loom 1.17.21, Java 컴파일, root metadata 생성 및 archive 검증.
- 선별 `FabricAmperInitSchemaUnitTest` + `FabricAmperDocumentationContractUnitTest`: **5/5 PASS**, 재실행 BUILD SUCCESSFUL in 15s. 전체 `test validatePlugins --offline --no-daemon --no-build-cache` 첫 시도는 **280 tests 중 과거 schema 배열 인덱스 가정 1 FAIL**; 이미 main에 적용됐던 정확한 해결 방식으로 `FabricAmperSchemaContractUnitTest`를 보정. 재실행 **EXIT0 / BUILD SUCCESSFUL in 49s**, 54 suites / **280 tests = 238 PASS, 42 SKIP, 0 FAIL/ERROR**, `validatePlugins` PASS.

**제약:** 실제 미설치 상태에서 winget UAC/소프트웨어 다운로드를 실행하지 않았으므로 `doctor --fix`의 **실제 신규 설치 성공은 검증하지 않음**; 보안·동의 측면에서 일반 테스트는 미리보기만 수행한다. 이 회차의 Gradle build와 단위/회귀 통과는 JitPack 서비스의 미래 가용성, GUI Sync, 원격 업로드 또는 모든 G1–G30 출하 승인과 동일하지 않다. 전체 출하 판정은 계속 **NOT APPROVED**.

## 37. main v0.1.1 선별 동기화 및 독립 재검증 (2026-11-14)

**브랜치 보호:** `develop` a44c889에서 구현·검증한 §36 기능을 `main` 353e183 기반 별도 worktree에 **관련 변경 부분만** 이식했다. `main`의 `fabric-amper.bat` 버전 `0.1.1`, 생성 Settings 플러그인 `0.1.1`/JitPack `v0.1.1`, `build.gradle.kts`, README 배지, 기존 QA §35는 보존했다. 새 `scripts/fabric-amper-doctor-fix.ps1`와 BAT·두 스킬·두 영문 가이드·새 Windows JUnit 회귀 및 QA §36만 포함; `develop` 전체 merge 및 불필요한 producer 변경/산출물 동기화는 하지 않았다.

**main 실제 검증 (Windows, JDK25, Gradle 9.7.1):** `cmd /c fabric-amper.bat version`은 **0.1.1 / EXIT0**, 도움말은 `--fix`/`--here` 표시, `doctor --fix --dry-run` **EXIT0** (no mutation), `doctor --fix --bogus`은 **EXIT2**. `gradle.bat test validatePlugins --offline --no-daemon --no-build-cache --console=plain` **EXIT0 / BUILD SUCCESSFUL in 1m 6s**, XML 54 suites / **280 tests = 238 PASS, 42 SKIP, 0 FAIL, 0 ERROR**, `validatePlugins` PASS. `git diff --check` PASS.

**실제 v0.1.1 소비자 확인:** `build/main-011-here-smoke` 도구 전용 폴더에 `.gitignore`를 둔 뒤 `fabric-amper.bat -C build\main-011-here-smoke init night_vision --here` **EXIT0**, 생성 Settings에 JitPack `com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.1` / plugin version `0.1.1`. 최초 `--offline` 소비자 Gradle 실행은 **실패**했으나 원인은 v0.1.1 JitPack 아티팩트가 소비자 Gradle 캐시에 아직 없어 plugin resolution을 수행할 수 없었던 것임(컴파일 실패 아님). 동일 명령을 네트워크 허용 상태로 실행하니 실제 JitPack 플러그인을 해석, `help validateFabricAmper build verifyFabricAmperJar --no-daemon --no-build-cache --console=plain` **EXIT0 / BUILD SUCCESSFUL in 14s**, `Fabric Loom 1.17.21`, Java compile, metadata, JAR, sourcesJar, 검증 task PASS. 첫 온라인 해석에 네트워크가 필요하다는 점은 여전하다.

**남은 한계:** 설치가 없는 호스트에서 실제 `doctor --fix` winget/ZIP 설치는 실행하지 않았다. 42 SKIP은 PASS가 아니며, GUI Sync·실제 Minecraft 실행·원격 업로드 및 전체 G1–G30 출하 승인은 이 검증만으로 증명하지 않는다. **NOT APPROVED** 상태 유지. 이 기록은 새 릴리스/버전 발행을 뜻하지 않고 v0.1.1 기반 `main`의 소스 기능 동기화만 기록한다.

## 38. v0.1.2 preview 버전 갱신 및 릴리스 후보 검증 (2026-11-14)

**요청 및 보호:** v0.1.1에서 도입한 Windows `doctor --fix`, `init --here`, 두 AI 스킬 BAT 우선 규칙을 **v0.1.2**로 기존 GitHub Release 양식에 맞춰 출하하도록 요청받았다. `develop` a44c889는 미커밋 작업을 보존하며, 별도 `main` 원격 커밋 d452bce 기반의 격리된 Git worktree에서 작업. `build.gradle.kts`의 실제 프로젝트 버전과 `settings.gradle.kts`의 `fabricAmperReleaseVersion` 메타데이터를 **0.1.2**로 설정하고, 릴리스 BAT 버전·생성 Settings bootstrap(JitPack `com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.2` + Settings plugin `0.1.2`)·README·두 영문 가이드·두 스킬·Windows 테스트 기대값까지 같은 버전으로 동기화했다. 기존 README JitPack 배지와 QA §1–§37 및 과거 해시는 수정하지 않았다.

**프로듀서 실증 (Windows/JDK25/Gradle 9.7.1):** 격리된 v0.1.2 worktree에서 `gradle.bat test validatePlugins publishAllPublicationsToG29ProbeRepository -PfabricAmperProbeMavenRepo=true --offline --no-daemon --no-build-cache --console=plain`: **EXIT0 / BUILD SUCCESSFUL in 1m 25s, 17 tasks executed**. JUnit XML 54 suites / **280 tests = 238 PASS, 42 SKIP, 0 FAIL, 0 ERROR**. 두 plugin marker POM의 실제 의존성 GAV `dev.fabricamper:fabric-amper:0.1.2`, sources/binary JAR의 Maven 저장소와 `build/libs` 바이트 동일성, Gradle `.module` 및 POM 존재 확인.

**배포 ZIP 실증:** `fabric-amper-0.1.2-maven-repository.zip`은 `maven-repo/` 접두로 Gradle marker 2종과 구현 JAR/POM/module, checksums 및 메타데이터를 포함. ZIP CRC·중복 경로 금지·파일 유형 검사·필수 파일 확인·구현 JAR SHA-256 일치를 통과했다. v0.1.1의 JAR(바이너리/소스), BAT, schema, Maven ZIP, `SHA256SUMS.txt` 배포 양식에 더해, 새 PowerShell 도구 누락을 막기 위해 `fabric-amper-0.1.2-windows-tools.zip`에 BAT, `scripts/fabric-amper-doctor-fix.ps1`, schema, 두 스킬 및 영문 문서를 상대 경로 그대로 넣었다. 모든 7개 업로드 자산에 SHA-256을 기록하고 재해시하여 대조했다. JAR **368,061 bytes**, SHA-256 `d06d3d226198b5014383656b506272938acdccaa7b0125917f8ff3c3396dc3cd`; Maven ZIP SHA-256 `99ae6d35ae4bf715d5d90e772c8a918b66c0138a212c4bd5c857e08ca8984ef8`.

**독립 소비자 실증:** `fabric-amper.bat version` **0.1.2**, `doctor --fix --dry-run` **EXIT0 (읽기 전용)**. tools-only 디렉터리에 `.gitignore`를 둔 후 `-C ... init night_vision --here` **EXIT0**, 기존 도구 파일 보존 및 정확한 v0.1.2 JitPack/Settings 요청 생성 확인. 공개 JitPack v0.1.2 태그가 만들어지기 전이므로 생성된 소비자의 `settings.gradle.kts`만 **릴리스 Maven ZIP의 압축 해제 저장소를 플러그인 저장소로 사용**하도록 변경한 뒤, 진짜 plugin marker 경로로 `help validateFabricAmper build verifyFabricAmperJar --offline --no-daemon --no-build-cache`: **EXIT0 / BUILD SUCCESSFUL in 18s**, Loom 1.17.21, Java 컴파일, Loader metadata, main JAR, sourcesJar, JAR 검증 PASS. 이는 ZIP 기반 로컬 소비자 검증이며 아직 원격 `v0.1.2` JitPack availability 검증이라고 주장하지 않는다.

**안전/한계:** 새 JDK·Gradle의 실제 winget/ZIP 설치는 수행하지 않았다. 42개 SKIP 및 IDE GUI Sync·일부 Minecraft runtime·인증된 외부 Maven/Modrinth/CurseForge 게시 미검증 상태는 유지한다. 이번 검증은 **v0.1.2 릴리스 후보의 빌드·패키징**을 확인한 것이며 모든 G1–G30 출하 승인 상태는 계속 **NOT APPROVED**. GitHub Release 게시 결과는 원격 실제 게시 상태로 별도 확인한다.

## 39. 기존 v0.1.2 프리릴리즈의 단일 PS1 온디맨드 부트스트랩 (2026-11-14)

**사용자 요구:** 새 릴리스/태그가 아니라 **이미 공개된 v0.1.2 GitHub 프리릴리즈의 자산을 보완**한다. Windows 사용자가 `fabric-amper.bat doctor --fix`를 명시적으로 실행할 때, BAT 버전 `0.1.2`와 일치하는 `https://github.com/howShouldIChooseMyUsername/Fabric-Amper/releases/download/v0.1.2/fabric-amper-doctor-fix.ps1`에서 **PS1 하나만** 받아 BAT와 동일한 폴더에 저장한 후 기존 JDK/Gradle 검증·필요 시 설치/소비자 설정 복구를 진행한다.

**구현 및 방어:** 기존 `scripts/fabric-amper-doctor-fix.ps1`을 BAT와 같은 저장소 루트의 `fabric-amper-doctor-fix.ps1`로 이동. BAT는 첫 `doctor --fix` 시 해당 파일이 없으면 HTTPS GitHub Release에서 유일한 임시 파일로 다운로드, **배포 PS1의 바이트 단위 SHA-256을 BAT 내부에 고정**하고 실제 해시를 일치 검증한 다음에만 이동·실행한다. 기존 파일이 있으면 네트워크 요청/덮어쓰기를 하지 않고 해시 일치 시에만 실행하며, 변조된 파일은 오류로 즉시 종료한다. 다운로드 실패/해시 불일치 시 임시 파일을 정리하고 **복구 작업을 시작하지 않는다**. `--dry-run`은 기존 도우미가 있으면 검증 후 `-Preview` 실행, 없으면 원격 URL/저장 경로만 출력하고 **다운로드·설치·설정 변경을 하지 않는다**. BAT의 검증과 PS1의 Gradle 공식 ZIP 검증은 자식 CMD PowerShell에서 간헐적으로 `Get-FileHash` 검색이 실패하는 문제를 피하도록 **.NET SHA-256**으로 수행한다.

**회귀 검증 (JDK25/Gradle 9.7.1/Windows):** 실제 `cmd /c fabric-amper.bat doctor --fix --dry-run` **EXIT0**, 설치/다운로드 없는 로컬 프리뷰. `doctor --fix --bogus` **EXIT2**. 단독 BAT + 미존재 PS1의 프리뷰는 구체적인 v0.1.2 릴리스 단일 파일 URL을 출력하고 파일을 생성하지 않으며, 변조된 PS1이 기존 폴더에 있으면 SHA-256 불일치 **EXIT1**, 파일 내용 불변. 변경된 루트 PS1과 BAT의 SHA-256 상수를 실제 바이트 해시로 비교하는 JUnit 회귀 추가. 프로듀서 `gradle.bat test validatePlugins --offline --no-daemon --no-build-cache --console=plain` **EXIT0 / BUILD SUCCESSFUL in 51s**, XML 54 suites / **281 tests = 239 PASS, 42 SKIP, 0 FAIL/ERROR**, `validatePlugins` PASS.

**로컬 릴리스 자산 확인:** 이전 v0.1.2 Maven 저장소 ZIP, 플러그인 바이너리/소스 JAR, schema 자산은 변경하지 않고, 단독 `fabric-amper-doctor-fix.ps1`를 추가하고 `fabric-amper.bat` 및 `fabric-amper-0.1.2-windows-tools.zip`을 동일 버전 내용으로 재패키징했다. ZIP 내 PS1 경로도 `scripts/`가 아닌 BAT 바로 옆의 flat 경로다. 새 `SHA256SUMS.txt`에 **총 7개 파일의 해시**가 있고, SHA256SUMS 자체를 포함한 업로드 자산은 8개다. ZIP CRC/중복 경로 금지, BAT에 고정된 PS1 SHA256, Maven plugin marker/JAR byte identity 확인. 다운로드/원격 GitHub 업로드 실증은 원격 게시 별도 단계에서 확인한다.

**범위 제한:** 실제 winget/JDK/Gradle 설치나 사용자 PATH 레지스트리 변경을 자동 테스트에서 수행하지 않았다. `develop`의 HEAD 및 미커밋 파일은 보존한다. 기존 `v0.1.2` 태그는 이미 공개된 JitPack 구현 커밋을 참조하므로 **강제 이동하지 않는다**. 수정된 BAT·PS1은 같은 v0.1.2 GitHub Release의 자산 및 후속 main 소스에 반영하되, 기존 태그 소스 스냅샷과 이후 소스 커밋은 구별한다. 전체 G1–G30 상태는 계속 **NOT APPROVED**.

**실제 GitHub 파일 다운로드 추가 검증:** `gh release upload v0.1.2 ...fabric-amper-doctor-fix.ps1`로 새 PS1 단독 asset을 업로드. 첫 실제 PowerShell `Invoke-WebRequest`는 **교체 전 자산을 CDN 캐시에서 받아 SHA-256 불일치로 EXIT1** 처리(설치 시작 없음). 이 문제 때문에 URL에 `?sha256=<BAT에 고정된 해시>`와 `Cache-Control/Pragma: no-cache`를 적용했다. 캐시 갱신된 URL은 PowerShell 실제 요청에서 현재 PS1 14,793바이트 및 SHA-256 `c8fa4389ba8e8fe3b1d8430fb77313c41d3f042c2f8d06e4630e969643903053`로 확인되었다. **테스트 전용 사본 BAT의 다운로드·검증 로직은 원본 그대로, 실행 인수만 `-Preview`로 대체**한 상태에서 최초 파일 없는 폴더에 `doctor --fix` 실행: **EXIT0**, `[OK] Saved verified doctor helper beside fabric-amper.bat.` 및 `[OK] Verified doctor helper SHA-256.`, JDK25·Gradle 사전 설치 탐지, 무변경 미리보기 모두 확인. 실제 설치는 하지 않았다.


## 40. v0.3.0 프리릴리즈 후보 — 스키마 1.1 기본값·BAT 멀티로더 검증 (2026-11-17)

**사용자 요구:** `mod.yaml` 또는 `mod.yml`에서 `schema`를 생략하면 1.1로 해석하고, 명시적 `schema: 1`은 기존 Fabric 단일 로더 스키마를 보존한다. 최신 NeoForge/듀얼로더 개발 구현을 `develop`에서 `main`으로 반영하고 AI 2개·사람용 2개 가이드를 양 브랜치에 동일하게 유지하여 v0.3.0 **프리릴리즈** 자산 9종을 기존 릴리스 형식으로 게시한다.

**파서·스키마 변경:** `ModYamlDecoder`가 키 생략을 곧바로 `decodeV11`로 분기하고, `fabric-amper.schema.json` Draft-07의 기본값도 문자열 `"1.1"`로 정렬했다. 명시적 정수 `1`은 종전 v1으로 유지한다. 무인용 소수 `1.1`, null, 불일치하는 구형 필드, 알 수 없는 스키마는 FA003 진단으로 거부한다. 스키마 생략과 `mod.yaml`·`mod.yml` 별칭 조합, Fabric/NeoForge 단독 및 멀티 `product`, 레거시 v1 호환/비호환 부정을 단위 테스트에 추가했다.

**Windows 명령 경로:** BAT 버전 및 생성 Settings/JitPack 모듈을 `0.3.0`/`v0.3.0`으로 통일. 기존 Fabric 단독 v1 형식의 `init` 예제는 명시적 `schema: 1`을 출력하고, NeoForge/듀얼 프로젝트는 `schema: "1.1"`을 출력한다. 명시적 로더 선택 `run`/`server`뿐 아니라 `validate`, `verify`, `build`도 듀얼에서는 각 `:fabric`·`:neoforge` 태스크에 안전하게 분기하도록 구현했다. 알 수 없는 인수·불명확/악성 YAML은 Gradle 호출 전 거부. 신규 BAT 가짜 Gradle 명령 매핑 테스트와 기존 `init` 4개 조합에 대한 BAT 단독 스모크는 PASS. 가짜 Gradle 명령 테스트는 **실제 Minecraft 런타임 시험이 아니다**.

**실제 Gradle 실행:** Gradle **9.7.1**, JDK **25**, `clean build validatePlugins --offline --no-daemon --no-build-cache --console=plain`의 최신 후보 실행이 **BUILD SUCCESSFUL (2m22s)**. JUnit XML 71 suites / **371 tests = 314 PASS, 57 SKIP, 0 FAIL, 0 ERROR**. 스키마·문서 YAML 파서·BAT 명령 테스트의 이전 실패는 구형 계약 단언, 문서에서 누락한 NeoForge 필수 license와 구형 필드, Windows stdout 파이프 충돌 문제였으며 모두 수정하고 동일 필터/전체 명령에서 재검증했다. 57 SKIP는 PASS로 간주하지 않는다. 기존 364 tests(307 PASS, 57 SKIP) 기록은 **변경 전 기준선**이므로 덮어쓰지 않는다.

**미수락:** 전체 G1–G30/NF1–NF26, Minecraft 26.3 클라이언트/전용 서버의 실제 NeoForge/듀얼 런타임, IntelliJ GUI Sync, 인증된 Modrinth/CurseForge 게시, 공개 JitPack `v0.3.0` 해석은 별도 시험 필요. dual Architectury `:common` JAR 첫 물질화로 구성 캐시 **콜드 두 번째 실행 1회 MISS**였고, 같은 상태의 세 번째 실행 HIT는 성공했으나 즉시 두 번째 HIT 엄격 기준은 **미충족**. 릴리스 전 원격 공개/실제 소비자 검증을 이미 통과했다고 주장하지 않는다.

## 41. v0.3.0 공개 프리릴리즈·JitPack 실소비자 후속 검증 및 main 버전 정렬 (2026-11-17)

**§40 이후 완료된 별도 릴리즈 작업의 증거:** GitHub annotated `v0.3.0` 태그는 `7ca37388195e2bfbde376920052431553b8cad82` 커밋을 가리킨다. GitHub Release는 `draft: false`, `prerelease: true`로 공개되었고, 자산 **9개**를 실제 게시한 뒤 **원격에서 다시 다운로드하여 SHA-256 전부 일치**를 확인했다. 이 사실은 §40의 당시 미수락 상태를 소급 변경하는 것이 아니라 **이후에 완료된 별도 검증**이다. 배포물은 다음과 같다.

| 공개 자산 | 확인한 SHA-256 |
| --- | --- |
| `fabric-amper-0.3.0.jar` | `e9567ce0d80512c60b3d379c7af7c8e3dcce4947625af81204eb0b9f6852e2c0` |
| `fabric-amper-0.3.0-sources.jar` | `00c9b02e77aec450259cfd35a26b3720cdd1f4eb827065ed5cc579f8936ab996` |
| `fabric-amper-0.3.0-maven-repository.zip` | `5e76888a5be3b72852ea81f29cbcdbaa340f645b0d4c96191df864b8daaf7ae7` |
| `fabric-amper-0.3.0-windows-tools.zip` | `e87125f99a6571b96470e00697098284bffb1da176ce0de382bbe2bf8cb9e2d5` |
| `fabric-amper-doctor-fix.ps1` | `c8fa4389ba8e8fe3b1d8430fb77313c41d3f042c2f8d06e4630e969643903053` |
| `fabric-amper-migrate.ps1` | `68cf6834064ded80dc30ae7f99366de3c284c9cca7c1b8029f12c59343f6313a` |
| `fabric-amper.bat` | `bdb80fe9d307460553683aee04c7b49a911c7e3012cb5d292fa5a78759e0df43` |
| `fabric-amper.schema.json` | `453620efca0115d5b584ddf71c515da1a8908b4c4ce4bc084a82c8d3b9764573` |

9번째 업로드 자산은 위 파일들의 해시 목록 `SHA256SUMS.txt`이다. BAT에 고정된 doctor/migrate/schema 해시는 각 공개 파일의 바이트와 일치한다. 기존 SHA-pinned 파일의 오래된 버전 주석은 역사적 정보로 보존하며 바이트를 변경하지 않는다.

**공개 JitPack 확인:** `/api/builds/com.github.howShouldIChooseMyUsername/Fabric-Amper/v0.3.0`의 응답은 `status: ok`, `version: v0.3.0`, `isTag: true`, 커밋 `7ca3738...`이고 Java 25 빌드에서 `dev.fabric-amper.project.gradle.plugin`, `dev.fabric-amper.settings.gradle.plugin`, `fabric-amper` 세 모듈을 생성했다. 원격 `Fabric-Amper-v0.3.0.pom` 조회는 **HTTP 200**이다.

**실제 공개 좌표 소비자:** 공개 BAT의 `init myneo ... --mod-neoforge --architectury --kotlin`으로 신규 프로젝트 생성, JitPack `v0.3.0` 원본 Settings를 그대로 사용. `gradle help` **BUILD SUCCESSFUL**, 이어 `validateFabricAmper build verifyFabricAmperJar --no-daemon --no-build-cache --console=plain` **BUILD SUCCESSFUL in 3m23s (8 tasks executed)**. 실제 `generateNeoForgeModsToml`, `createMinecraftArtifacts`, `compileKotlin`, `jar`, `verifyFabricAmperJar`를 수행한 원격 소비자 빌드 증거이며, 게임 실행 증거는 아니다.

**이번 main 후속 정리:** `settings.gradle.kts`의 과거 생산자 릴리즈 메타데이터 `0.1.2`를 `build.gradle.kts` 및 BAT와 일치하는 `0.3.0`으로 수정하고 현재 문서의 미게시/개발 예정 진술을 위의 확인된 공개 상태로 갱신했다. **이미 게시된 `v0.3.0` 태그/Release 자산의 바이트·SHA·대상 커밋은 그대로 보존**한다. 이 후속 소스 커밋은 기존 Git 태그가 가리키는 배포 스냅샷과 구분한다.

**미수락 유지:** 전체 G1–G30/NF1–NF26, 실제 Minecraft 26.3 client/server 모드 런타임, IntelliJ 실제 GUI Sync, 엄격한 콜드 두 번째 실행 configuration-cache HIT, 인증된 외부 Modrinth/CurseForge 게시. §40의 57 SKIP·콜드 CC MISS 등 역사적 사실을 덮어쓰지 않는다.

**동일 main 후속 문서 정합성 검증 (2026-11-18 KST):** 사용자의 추가 요청에 따라 `README.md` 전부를 영어로 전환(한글 잔존 **0건**), 5개 인간/AI 문서에서 **현재 복사 가능한 완전한 YAML 예제는 스키마 1.1**로 정리하고 구형 `schema: 1` 완전 예제는 문서마다 **단일 역사 섹션** 안에만 유지했다. 루트 `repositories`/`dependencies`/`test-dependencies`가 **v1·v1.1 모두 배열**이며, v1 `mod.depends`/v1.1 `fabric.depends`는 맵, `neoforge.dependencies`는 배열이라는 파서/스키마 사실을 명시했다. 실제 BAT Fabric 전용 `init`은 `--mod-fabric` 단독을 포함해 여전히 명시적 `schema: 1`을 생성한다는 호환 예외도 기록했다. `FabricAmperDocumentationContractUnitTest`에 README 영문 유지·QuickGuide 1.1 예제·5개 문서 역사 섹션 단일성 회귀를 추가. 중간 문서 YAML 파서 시험의 두 실패(1.1 예제의 구형 필드 위치, 역사 예제의 미존재 파일 참조)는 예제/테스트 계약을 수정한 뒤 **선택 16 tests / 0 FAIL / BUILD SUCCESSFUL in 9s**로 재실행 확인. 이어 JDK25/Gradle9.7.1 `clean build validatePlugins --offline --no-daemon --no-build-cache --console=plain` **BUILD SUCCESSFUL in 2m23s / 11 tasks executed**, `validatePlugins` PASS. 본 검증 역시 Minecraft 실제 게임·IDE GUI나 전체 NF/G 게이트의 추가 승인은 아니다.

## 42. v0.3.1 프리릴리즈 후보 — 통합 dependencies[].loader 및 빌드/자산 검증 (2026-11-18)

**변경 계약:** 현행 스키마 1.1 루트 `dependencies[]` 객체 항목에 선택적 `loader: fabric|neoforge`를 추가했다. 생략한 항목은 선택된 로더들의 공통 Gradle 라이브러리이며, 명시된 로더의 항목은 그 로더에서만 사용하고 Architectury `:common`에서 제외한다. 두 로더의 동일 모드에 서로 다른 Maven 버전을 지정할 수 있다. 단일 로더에서도 실제 선택된 로더와 일치하면 허용하며, 알 수 없거나 선택되지 않은 로더는 FA003으로 거절한다. 공통+특정 로더가 같은 좌표/대상을 중복 선언한 충돌은 FA012. 스키마 1의 기존 입력, Loader 런타임 관계 필드 `fabric.depends`·`neoforge.dependencies`, 기존 `fabric.gradleDependencies`·`neoforge.gradleDependencies`는 보존한다.

**문서/도구:** 영문 README 최상단 `Write mods, not build scripts` 및 원본 JitPack 이미지 배지를 유지한 채 254줄로 재구성했고, 현재 bootstrap 예시·플러그인 버전·BAT·마이그레이션 도구를 0.3.1 / v0.3.1로 정렬했다. 과거 v0.3.0의 실제 공개 검증·자산 SHA 기록은 날짜가 있는 역사적 자료로 보존했다. JSON Schema Draft-07 구조와 9개 허용/거부 시나리오 PASS. BAT는 schema SHA-256 `dd84e7af00ce9bde155ec7d13d672d040f43d8c46f5f1145131cdbee5f9c6d3c`, migrate SHA `20ff79e813933b724b8032789253c89747cd36df5895d05d1321ffb8133d0b2d`, 원래 doctor SHA `c8fa4389ba8e8fe3b1d8430fb77313c41d3f042c2f8d06e4630e969643903053`를 실제 파일 바이트와 맞춘다. Fabric 단독(schema 1), NeoForge 단독(1.1), 듀얼(1.1) BAT init 3/3 스모크 PASS 및 생성 Settings JitPack v0.3.1 확인.

**검증:** 새 루트 로더 선택/겹치는 좌표/모드 형식/클라이언트 분리를 포함한 단위 선택 28/28 PASS. 실제 TestKit 독립 및 Architectury 듀얼의 Fabric/NeoForge 버전 분리, 중립 :common 의존성, Fabric 전용 include/JIJ 비누출 **2/2 PASS**. 필터 시험 중 YAML 들여쓰기와 테스트 fixture 소스 디렉터리 부재로 일시 실패했던 두 경우는 **테스트 fixture만** 수정해 재실행 PASS. 기존 IDE 스키마 자동완성 단언은 새 `loader` 필드의 구조/enum을 정확히 확인하도록 갱신한 뒤 11/11 PASS.

**생산자 전체 빌드:** Gradle 9.7.1/JDK25, `clean build validatePlugins -PfabricAmperProbeMavenRepo=true --offline --no-daemon --no-build-cache --console=plain` **BUILD SUCCESSFUL in 2m**, JUnit XML 73 suites / **383 tests = 324 PASS + 59 SKIP + 0 FAIL + 0 ERROR**, `validatePlugins` PASS. G29 `publishAllPublicationsToG29ProbeRepository`도 **BUILD SUCCESSFUL**, 두 Gradle Plugin Marker POM 및 구현 JAR/소스 JAR, POM, `.module`을 로컬 Maven으로 게시하고 실제 바이너리와 바이트 동일성을 확인했다.

**v0.3.1 배포 후보:** 새 컴파일 결과에서 구버전 바이너리 이름을 바꾸지 않고 플러그인 JAR / sources JAR / 로컬 Maven ZIP / Windows 도구 ZIP / 4개 단독 도구·스키마 / SHA256SUMS 총 **9개 자산**을 구성했다. 별도 검증 스크립트가 8개 해시, Maven ZIP 양쪽 Plugin Marker, Windows ZIP 9개 정확한 구성요소와 고정된 helper/schema SHA, Maven/JAR byte identity를 검사해 **PASS**. 이 절의 검증은 로컬 후보 자산 검사로, GitHub 원격 재다운로드 및 JitPack v0.3.1 공개 소비자 빌드 결과와 구분한다.

**미수락 상태:** Minecraft 실제 클라이언트/서버 런타임, IntelliJ 실제 GUI Sync, 엄격한 콜드 두 번째 config-cache HIT, 외부 플랫폼 인증 게시, 모든 G1–G30/NF1–NF26은 여전히 별도 출하 게이트다. 59 SKIP는 PASS가 아니다.
