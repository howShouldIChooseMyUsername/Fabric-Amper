# Fabric Amper G1–G30 재검증 매트릭스 — 2026-11-13

기준: `specs.md` §9; 과거 QA §§1–23은 불변인 역사적 증거. 이 문서는 **최종 독립 소스 스냅샷**에 대해 다시 실행한 선택 결과와 과거 독립 검증을 분리한다. 새 실제 TestKit XML/빌드 기록은 이 디렉터리의 `FOLLOWUP-*`를 참조한다.

| 게이트 | 판정 | 재검증 근거와 정확한 미수락 부분 |
| --- | --- | --- |
| G1 | PASS(선별) | `settings.gradle.kts`+YAML만 있는 독립 소비자 `help`; Project 자동 설정 |
| G2 | PASS(선별) | `tasks --all`에서 Java/JSON/JAR/client/server task 확인 |
| G3 | PASS | 실제 소비자 build, 최종 JAR 루트 `fabric.mod.json` 1개 |
| G4 | PASS(선별) | YAML ID/version/archive 편집 후 빌드 결과 반영 |
| G5 | PASS(선별 + Java option 복귀) | 기존 실제 `build` STORE→HIT→YAML 버전/의존성 변경 MISS→HIT. 추가 독립 설정 전용 소비자에서 `settings.java.sourcesJar true→false→true` 변경 시 `tasks --all --configuration-cache --offline`의 **STORE→HIT→MISS→HIT→MISS→HIT**, 태스크 존재/부재 연동 **6회 PASS** (`FOLLOWUP-G5-java-setting-CC-6run-PASS.json`). 유효하지 않은 Minecraft 26.3/Java 24로 억지 전환하지 않음 |
| G6 | **PARTIAL** | Gradle Tooling API IdeaProject/모듈/의존성 조회는 PASS; 실제 IntelliJ GUI 최초 import·Sync·Run Configuration은 NOT RUN |
| G7 | PASS(선별) | Java-only/Kotlin-enabled compile/JAR, Kotlin 설정 on/off |
| G8 | PASS | 26.3+Yarn이 FA004로 거부됨 |
| G9 | PASS(선별) | 1.21.11/Java21 remap Loom 및 실제 API 로컬 Maven `remapJar`·`remapSourcesJar` 게시/소비자 컴파일; 레거시 게임 프로세스 런타임 NOT RUN |
| G10 | **PASS(실제 클라이언트 JIJ)** | `FOLLOWUP-G10-real-JIJ-Loader-invocation-1PASS-isolated.xml`: 외부 배포 mod/JIJ nested JAR을 Loader가 실제 탐지·호출. Modrinth Maven client compile-only 별도 PASS; 모든 외부 플랫폼 스코프 조합을 뜻하지 않음 |
| G11 | **PASS(신규 17종 고립된 오류·보존 행렬), 그 외 조합 PARTIAL** | `FOLLOWUP-G11-G30-hostile-YAML-CC-preservation-PASS.json`: 악성 YAML 11건×CC/입력 바이트·정상 generated/mirror/JAR 보존, 복구 HIT 실제 PASS. `FOLLOWUP-G11-resource-FA001-FA008-FA009-FA012-CC-PASS.json`: Class Tweaker 헤더, 삭제 icon, traversal, 중복 의존성, YAML 이중 별칭, 원본 JSON 등 6건 실제 FA001/008/009/012 및 동일 보존 검사 PASS. 가능한 모든 조합의 수학적 전수 또는 PNG decode까지 증명한 것은 아님 |
| G12 | PASS | 기존 main/client fabric.mod.json에 FA008, 소스 보존 |
| G13 | **PASS(두 정확한 조합)** | Gradle9.7.0+Loom1.18.4 `FOLLOWUP-G13-Gradle970-Loom1184-isolated.xml` 1 PASS; Gradle9.9.1+Loom1.19.4 `FOLLOWUP-G13-G25-Gradle991-Loom1194-isolated-4PASS.xml` 내 1 PASS. 생산 플러그인은 조합별 실제 Gradle로 각각 빌드 |
| G14 | PASS | 소비자 settings plugins에서 Amper 단일 plugin entry, Loom/선택 KGP 자동 로드 |
| G15 | **PARTIAL(클라이언트 틱·서버 Loader 초기화 PASS)** | `FOLLOWUP-G15-real-FabricLoader-client-tick-1PASS-isolated.xml`: 실제 Loader main/client/tick PASS. 이전 Windows `0xC0000409` FAIL 역사 보존. 별도 `runServer`는 **실제로 실행되어** SERVER Loader가 모드를 발견하고 초기화 성공을 기록했지만, 미동의 EULA에서 정지해 **서버 완전 부팅/월드/틱은 NOT RUN** (`FOLLOWUP-G27-server-EULA-refusal-loader-discovery.log`) |
| G16 | PASS(선별) | 유효/무효 Class Tweaker·access widener의 실제 Loom validation |
| G17 | **PARTIAL(실제 주입 게임 틱 PASS)** | custom-only YAML 음성/양성 javac PASS; `FOLLOWUP-G17-real-injected-interface-client-tick-1PASS-isolated.xml`: 실제 Loader 인터페이스 주입/기본 메서드 호출/게임 틱 1 PASS. 과거 native FAIL 보존. IntelliJ IDE 주입 모델 검증 NOT RUN |
| G17a | PASS(구성 시점) | 생성 task 이전 Loom 미러/ID/version/interface 메타데이터를 help에서 읽음. 실제 GUI IDE 경로 미검증 |
| G18 | PASS(선별) | split client/common source sets 및 client compile-only의 Java 접근 경계 |
| G19 | **PASS(실제 Loom·KGP 충돌)** | 실제 외부 Loom1.18.4→FA010+번들1.17.21 정상 대조 `FOLLOWUP-FabricAmperG19RealLoomCollisionConsumerTest-1PASS.xml`. 실제 외부 KGP2.4.20→FA010+번들2.4.21 정상 대조 `FOLLOWUP-G19-real-KGP2420-conflict-1PASS-isolated.xml`(no-cache real run) |
| G20 | **PASS(실제 4종 동시 미러 손상+두 GradleRunner 복구), IDE PARTIAL** | `FOLLOWUP-G20-FOUR-MIRROR-FILES-PARALLEL-CC-1PASS.xml`: mirror fabric.mod.json·Mixin·Class Tweaker 변조 및 icon 삭제를 한 번에 수행하고 서로 다른 GradleRunner `help`/`generateFabricModJson --configuration-cache --offline` 병렬 실행, 두 성공·최종 4종·원본·generated·JAR 바이트 일치·중복 ZIP/임시파일 없음·최종 CC HIT **JUnit 1 PASS**. **개별 파일 atomic replace**이지 전체 디렉터리 트랜잭션 보장 아님. 실제 IntelliJ Sync는 미실행 |
| G21 | **PASS(두 실제 clean JAR 동일성)** | `FOLLOWUP-G21-two-independent-clean-builds-PASS.json`: 독립 settings-only 소비자에서 두 번 `clean build --offline --no-build-cache`, main/sources JAR SHA 동일, mirror/generated/JAR 루트 metadata 동일, 중복 JSON 없음 |
| G22 | PASS(선별) | 모드 group/version/archivesName, sources JAR/생성 JSON/실제 JAR 이름 |
| G23 | PASS(선별) | YAML test dependency JUnit Jupiter 실제 테스트 발견·실행 |
| G24 | PARTIAL | 실제 Mixin/CT/icon 바이트 변경 후 재패키징·content addressed mirror/JAR 변경 `G24-repackage-1PASS.xml` PASS. 실제 IDE Sync 부분은 G6/G28과 함께 미검증 |
| G25 | **PASS(세 정확한 variant 결과)** | `FOLLOWUP-G13-G25-Gradle991-Loom1194-isolated-4PASS.xml` 안의 G25 3 PASS: Gradle9.7.0에서 Loom1.19.4 API9.8 variant의 **의도된 거부**, Gradle9.8.0/9.9.1 정상 resolve |
| G26 | PASS(선별) | 공식 Loader0.20.1 predicate AND/OR/opaque 비교와 모순 진단 검증. 전체 loader 버전 런타임 매트릭스는 아님 |
| G27 | **PARTIAL** | `FOLLOWUP-FabricAmperG27SideConsumerTest-1PASS.xml`: client/common Java, Mixin/JAR/Loader side 규칙 선별 PASS. 별도 EULA 미동의 `runServer`가 SERVER Loader에서 모드를 발견·`FABRIC_AMPER_SERVER_LOADER_INITIALIZER=SUCCESS`를 남긴 뒤 EULA 거부로 정지. **동의 후 실제 서버 완전 기동/side 안전성은 NOT RUN** |
| G28 | **BLOCKED(실제 IDE GUI)** | Tooling API 모델은 PASS, IntelliJ GUI 최초 import·수동 Sync·YAML 저장 후 자동 Sync는 NOT RUN |
| G29 | **측정 PASS / lazy KGP 미출하** | 독립 cold-home에서 Kotlin disabled에도 KGP2.4.21 JAR 정적 전이 다운로드가 남음을 QA §17에서 실측; 지연 `buildscript` opt-in 최적화는 classloader/isolated/CC를 통과하지 않았으므로 구현·출하하지 않음 |
| G30 | **PASS(악성 YAML 11건×CC·원본/출력 무변경), 추가 조합 PARTIAL** | `FOLLOWUP-G11-G30-hostile-YAML-CC-preservation-PASS.json`에서 중복 하위 키, scalar alias/anchor, collection tag, 다중/빈 문서, 깨진 UTF-8, 숫자형 게임 버전, 중첩 미정의 키, merge key, 과도한 깊이를 **11개 실제 잘못된 구성**으로 검증, FA002/FA003·CC invalidate·원본·JAR 보존과 valid 복구 HIT 확인. 별도 파일명 교체/store→hit→miss→hit, ZIP 중복 unit PASS. 모든 극단 조합/GUI Sync는 범위 밖 |

## 이번 후속 실행의 엄격한 구분

- G13/G25: **5개 실제 선택 TestKit PASS**, 각 실행의 비활성화된 메서드는 SKIP로 남김. 1.19.4를 9.7.1 생산 Gradle로 컴파일하려던 선행 시도는 **공식 Gradle 9.8 최소 버전** 불일치로 실패했고, 생산 Gradle9.9.1로 정확히 빌드해 통과. 이 선행 실패를 제품 assertion FAIL로 바꾸지 않음.
- G19: 외부 Loom real 충돌, 외부 Kotlin real 충돌 각각 **1 PASS**; Kotlin 검증의 최초 FROM-CACHE는 실측으로 집계하지 않고, `--rerun-tasks --no-build-cache`로 실수행 후 **1 PASS**로 기록.
- 실제 Loader: G10/G15/G17 각각 **1 PASS / 0 FAIL / 0 SKIP**. 모두 전용 런처/사용자 홈·프로세스 트리 감독기를 사용했으며 기존 계정·월드·EULA/서버에 접근하지 않음. 과거 SDL 뒤 네이티브 `0xC0000409` 실패는 삭제하지 않음. 이번 성공이 다른 GPU/Windows 환경의 재현성을 증명하지는 않음.
- remote Maven CC (2026-11-13 재평가): 원래 `FOLLOWUP-REMOTE-MAVEN-OFFLINE-CC-DRYRUN-1PASS.xml`은 이전 생산 코드의 **dry-run task graph** 1 PASS만 의미한다. 이후 고립된 localhost HTTPS 실제 액션 시도에서 초기 `task.repository` getter가 CC 직렬화 후 폐기되는 제품 버그가 확인되어 생산 코드에서 제거했다. 수정 버전은 Gradle 9.7.1 네이티브 `PublishToMavenRepository`에 **명시적 자격증명을 구성하면 configuration cache INCOMPATIBLE**하다는 공식 Gradle 진단과, `--offline`인 경우 **127.0.0.1 HTTPS Maven에도 PUT 자체를 차단**한다는 사실을 확인했다. 실제 authenticated native PUT+CC STORE/HIT **NOT VERIFIED**. `FOLLOWUP-POST-HTTPS-REMOTE-CC-DRYRUN-REGRESSION-1FAIL.xml`은 명시적 credentials로 dry-run CC가 비활성화된 회귀를 보존한다. `--dry-run`에 자격증명 미설정 보정 후의 선택 재검증은 QA §26 이후 실측만 반영한다.
- G5 Java/Gradle CC 추가: `FOLLOWUP-G5-java-setting-CC-6run-PASS.json`은 별도의 `settings-only` 소비자에서 실제 `sourcesJar` 태스크 on/off와 구성 캐시의 STORE/HIT/MISS/HIT/MISS/HIT를 증명한다. 첫 unmeasured task-enumeration 준비 부족 시도에서 `directory '.' has changed` cache MISS였던 로그도 남기고, 동일한 `tasks --all` 사전 워밍업 뒤 **새 소비자**에서 재측정해 성공.
- 최종 기본: `FOLLOWUP-FINAL-clean-no-cache-manifest.json`, `FOLLOWUP-FINAL-clean-no-cache-JUnit-XML.zip`; **Gradle9.7.1/JDK25 clean build validatePlugins, --no-build-cache --rerun-tasks, 45 suites / 246 tests = 207 PASS +39 SKIP +0 FAIL/ERROR**, binary/sources byte-equal 로컬 Maven 게시. 이후 선택 검증의 PASS를 246개 수치에 합산하지 않음.

## 출시 차단과 사용자 명시 제약

실제 IntelliJ GUI G6/G28/G17 IDE, EULA 동의 후 dedicated server G15/G27 **완전 부팅**, G9 구형 게임 Loader 실행, Kotlin-off lazy-KGP 최적화, 네이티브 실제 원격 Maven 인증+CC replay/TLS의 동시 수락, FA011 외부 오류 전체 조합은 **완전 수락 아님**. G11/G30 악성 입력 17건과 G20 4종 병렬 복구는 추가 실제 PASS로 범위가 확대되었으나, 다른 가능한 조합까지 전수 입증하지 않는다. Dedicated `runServer`에서 **Loader 발견·Initializer 성공까지는 실제 관측**됐다. 사용자가 금지한 실제 Modrinth·CurseForge 업로드/토큰 API 시험은 **0건**. 따라서 현재 결과는 **출하 승인 NOT APPROVED**이며, §24 기본 실행의 39 SKIP 및 후속 기본 재빌드의 40 SKIP를 PASS로 계산하지 않는다.

## 최종 추가 검증 및 최신 artifact — QA §27

위 행렬은 §24–26 증거를 보존하는 역사적 판정이며 **이 절의 실제 최신 재검증으로 업데이트**한다. G11/G30은 악성 YAML **11건**, 추가 리소스/metadata 음성 **6건**에서 FA002/003/FA001/008/009/012, 정상 JAR/mirror/generated/원본 바이트 보존, CC 거부·복구 HIT가 실제 PASS였다. G20은 JSON/Mixin/Class Tweaker/icon **4개 미러 동시 손상/삭제**와 두 GradleRunner 병렬 CC 실행 후 바이트·JAR/ZIP 단일성·최종 cache HIT **JUnit 1 PASS**였다. 이 범위 외 임의 조합이나 실제 GUI Sync를 전체 PASS로 확대하지 않는다.

새로운 원격 Maven **실제 native task** 음성 검증에서 제품의 누락된 `credentials.username`이 Gradle에 의해 플러그인 FA003보다 먼저 거부되는 문제를 발견, `FabricAmperPublishingConfigurer.kt`에서 null 대신 빈 **임시값**을 구성하고 doFirst가 원래 환경 Provider를 검사하여 전송 전에 FA003으로 거부하도록 수정했다. 새 오프라인 독립 소비자 두 건은 (1) 누락된 username/password **native remote task 실행→정확한 `FA003 publish.maven`**, (2) `http://127.0.0.1` 입력 **`help` 구성 단계에서 `FA003 publish.maven.url`**로 각각 기대한 실패/원본 보존 **2/2 PASS**. `analysis/local-https-maven-probe-2026-11-13/negative-contract-v2-2026-11-13/manifest.json` 참조. 성공 인증 PUT이 아니라 **거부 동작 검증**이다.

마지막 production 수정 후 별도 `clean build validatePlugins --offline --no-build-cache --rerun-tasks` **46 suites, 247 tests = 207 PASS + 40 SKIP + 0 FAIL/ERROR**, `validatePlugins` PASS, G29 로컬 Maven marker/main/sources 실제 바이트 일치. 선택 `FabricAmperRemoteMavenCredentialConfigurationCacheConsumerTest`를 **한 번 더 실제 실행하여 1 PASS, `--dry-run --configuration-cache --offline` STORE→HIT**와 모든 태스크 SKIPPED 확인 (`FOLLOWUP-POST-REMOTE-FA003-DRYRUN-CC-1PASS.xml`). 최신 JAR SHA-256 **`13022B24ADE7C9809400FC6AEBE99E7751783A9DAE7EF8B8C53780FE70B698AD`**, sources **`C3E69E8188B516F4F290588B3342F1D21BB24391658A75B4E26BEA3A5175B510`**. 최종 증거/해시 색인 `FOLLOWUP-FINAL-2026-11-13-remaining-verification-index.json`이 PASS 상태로 교차 검증됨.

**계속 미수락:** G6/G28/G17 실제 IntelliJ GUI 모델/Sync, G15/G27 EULA 승인 후 서버 완전 기동, G9 레거시 게임 실제 런타임, Gradle 9.7.1 native Maven 명시적 자격증명+실제 HTTPS PUT+CC HIT의 동시 충족은 증명되지 않았다. `.invalid` dry-run/FA003 음성은 원격 성공 테스트가 아니다. 실제 Modrinth·CurseForge 게시 0건. **전체 G1–G30 정식 출하 `NOT APPROVED`**, 새 기본 **40 SKIP**는 PASS가 아니다.

## 2026-11-14 검증 보강 — 실제 인증 HTTPS PUT만 추가 PASS (QA §28)

§27의 `nativeHttpsActualPut: NOT_VERIFIED`는 **그 시점에 유효했던 역사적 상태**다. 신규 독립 `analysis/local-https-maven-probe-2026-11-13/verify_online_loopback.py`가 설정 전용 `fabric/api` 소비자에서 실제 Gradle 9.7.1 네이티브 원격 게시 태스크를 두 번 성공시켰다. 외부 HTTP(S)는 127.0.0.1의 거부 프록시로 제한하고 실제 게시 저장소도 IP 127.0.0.1 자체 서명 SAN TLS+Basic으로만 구축했다. 각 실행 **인증 HTTP 201 PUT 25건**, 총 50건, JAR/POM/module/sources 실제 수신 해시와 로컬 출력 완전 일치. 외부 플랫폼 업로드는 0건. 증거 `analysis/local-https-maven-probe-2026-11-13/online-loopback-2026-11-14/manifest.json` SHA-256 `9B35DE9FA5E04CDD245705FB88F583CBAB59A58924C25393831961AE7D3BA06E`.

**여전히 미수락:** 해당 native task의 명시적 `PasswordCredentials` 때문에 구성 캐시는 **두 번 모두 DISABLED**(STORE 0, HIT 0). 처음의 `--offline` 차단을 피하려 게시에만 `--offline`을 제외했으므로 이 시험이 Gradle offline native upload를 증명하는 것은 아니다. 그 외 GUI G6/G28/IDE, EULA 후 G15/G27 전용 서버, 레거시 G9 게임 런타임, 원격 네이티브 게시+CC HIT는 여전히 별도 남은 조건이다. 최종 JUnit과 production JAR은 §27 이후 변경 없음. **전체 출시 `NOT APPROVED`.**
