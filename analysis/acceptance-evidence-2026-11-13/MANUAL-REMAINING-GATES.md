# Fabric Amper — 자동 검증으로 닫을 수 없는 최종 수락 단계

**기준:** `specs.md` G6/G15/G17/G20/G27/G28, `QA_REPORT.md` §§24–25. 자동 검증이 끝나도 이 문서의 항목을 수행한 것처럼 PASS로 기록하지 않는다. 실제 Modrinth·CurseForge API 업로드/토큰 시험은 사용자의 금지에 따라 하지 않는다.

## 1. IntelliJ GUI (G6, G17 IDE, G20/G24 IDE, G28)

**현재:** Gradle Tooling API IdeaProject/소스·의존성·태스크 모델과 별도 `analysis/ide-sync-probe/`의 Java/Kotlin 빌드는 선별 PASS. 실제 IntelliJ **GUI 최초 import, 수동 Reload, `mod.yaml` 저장 후 자동 Sync 여부 및 Loom run configuration 생성**은 관찰하지 못했다. 이전 불안전 GUI 자동화 헬퍼는 사용하지 않는다.

1. 기존 개인 IntelliJ 프로필과 사용자 Minecraft 설치를 바꾸지 않는 **새로운 독립 작업공간**에서, 프로젝트 안의 `analysis/ide-sync-probe/`의 `settings.gradle.kts`, `mod.yaml`, `src/`만 복사한 소비자를 IntelliJ의 **Open**으로 직접 연다. 소비자 루트에 `build.gradle(.kts)`와 직접 작성한 `fabric.mod.json`을 추가하지 않는다.
2. 실제 Gradle import가 성공하는지 확인한다. Project SDK/Gradle JVM은 JDK 25, source roots는 Java와 선택 Kotlin, client/common 분리 source set은 YAML 설정대로여야 한다. Gradle 패널에 `generateFabricModJson`, `runClient`, `runServer`, `jar`가 나타나는지와 Loom Run Configuration 목록을 기록한다.
3. YAML 버전 또는 이름을 **복구 가능한 임시값**으로 바꾼 뒤 저장하고, 수동 Reload를 누르지 않은 상태에서 **자동 Sync가 발생했는지 별도로 관찰**한다. 미발생은 자동 Sync 지원이라는 주장 없이 기록하고, **수동 Reload**를 눌렀을 때 새 메타데이터/모델 반영 여부를 확인한다. 원래 YAML로 되돌린 뒤 다시 Reload한다.
4. 로그·스크린샷/프로젝트 모델, 처음 설치 여부, JDK/IDE/Gradle 버전, 성공/실패 시점을 기록한 **직접 관찰 증거**가 있을 때만 G6/G28 수락 판정을 갱신한다. 단순 Gradle `help`나 Tooling API 모델은 GUI Sync 증거가 아니다.

## 2. EULA 동의 후 dedicated server 완전 기동 (G15/G27)

**현재 확인된 사실:** `FOLLOWUP-G27-server-EULA-refusal-loader-discovery.log`에서 격리 `runServer`가 **실제로 실행**됐으며 Loader 0.20.1이 모드 `fabric_amper_server_probe`를 발견하고 `FABRIC_AMPER_SERVER_LOADER_INITIALIZER=SUCCESS`를 출력했다. 그러나 `eula.txt`가 승인되지 않아 Minecraft 서버가 **완전 시작하기 전에 거부**했다. Gradle task `BUILD SUCCESSFUL`을 서버 부팅 성공으로 혼동하면 안 된다.

1. 사용자가 Minecraft EULA 원문을 직접 검토하고 **동의한다고 명시적으로 결정한 경우에만** 완전히 새 격리 서버 작업공간에서 테스트한다. 현재 자동 검증은 EULA를 **수정·자동 승인하지 않았고**, 사용자의 실제 서버나 월드를 이용하지 않았다.
2. 승인된 별도 작업공간의 `runServer`에서 Fabric Loader의 모드 발견/초기화뿐 아니라 server startup complete, 필요한 side-격리 의존성·Mixin·로그/종료 동작을 관찰한다. 개인 계정 인증을 우회하거나 기존 월드/프로필/서버를 수정하지 않는다.
3. EULA 동의·서버 정상 시작 및 side 검사까지 확인되지 않으면 G15/G27 **완전 PASS 불가**. `runServer`의 EULA 거부 단계까지만 관찰한 현재 상태는 **부분 PASS**다.

## 3. 다른 실환경·외부 의존 범위

- **FA011 외부 TLS/Modrinth 실제 HTTP 오류와 원격 Maven 인증/CC action replay:** 2026-11-13에 자체 서명 SAN IP `127.0.0.1` 인증서와 JDK 신뢰 저장소, Basic 401 challenge를 가진 전용 HTTPS Maven server를 실제 시작했다. Java/Gradle에서 네트워크를 사용하지 않은 `compileJava --offline` PASS 후, **실제 Gradle native remote publish task** 실행은 `--offline`이 **localhost HTTPS조차 전송 금지**해 PUT 전에 FAIL. 또한 Gradle 9.7.1 공식 구성 캐시 리포트에서 명시적 `PasswordCredentials`를 가진 `PublishToMavenRepository`는 **configuration cache INCOMPATIBLE**로 보고되었다. 따라서 `--offline` 원격 PUT+CC HIT는 현 Gradle 네이티브 계약으로 성립하지 않는다. 401 challenge는 실제 게시 인증 성공과 다르다. 실서비스 Modrinth/CurseForge 업로드는 사용자 금지에 따라 **절대 시험하지 않는다**. 이 두 사실은 `analysis/local-https-maven-probe-2026-11-13/`의 보존된 실패 fixture/Gradle 리포트로 구분한다.
- **G9 구형 게임 Loader 런타임:** Java21/Minecraft1.21.11 remapped 배포 JAR·소스 JAR의 실제 로컬 Maven 게시 및 downstream compile은 PASS지만 구형 게임 클라이언트/서버 실행 증거는 없음.
- **G11/G20/G30 복합 악성 입력/병렬 자원 변형:** 11종 hostile YAML×CC/기존 JAR 보존과 6종 리소스·FA001/008/009/012 오류×CC/출력 보존을 실제 고립 소비자에서 **17건 PASS**. 추가 G20 TestKit은 JSON/Mixin/Class Tweaker/아이콘 **4종을 동시에 변조·삭제**, 두 독립 GradleRunner의 병렬 CC 복구와 최종 파일 바이트·JAR·ZIP 단일성을 **JUnit 1 PASS**로 검증했다. 전체 디렉터리의 트랜잭션 원자성과 실제 IntelliJ GUI Sync/자동 Sync까지 입증한 것은 아니다.
- **G29 Kotlin OFF 다운로드 비용:** 두 cold Gradle home에서 정적 KGP 비용을 측정한 것은 명세 요구 충족. 선택적 지연 KGP 최적화는 별도 opt-in으로 조건을 충족하기 전까지 구현/출하되지 않았다.

**결론:** 새 17건 악성 입력·CC와 G20 복합 병렬 복구 등 자동 검증의 실측 PASS, 이전부터의 IntelliJ GUI·EULA 및 네이티브 Maven remote/CC 제약을 분리한다. 사용자 GUI 확인 단계는 별도 고립 fixture `analysis/idea-gui-verification-2026-11-13/`로 안내했으며 파일 생성/Sync 성공의 실측 증거 없이는 PASS가 아니다. 현재 **G1–G30 전체 출하 승인: NOT APPROVED**. 문서만 작성하거나 테스트가 SKIPPED인 것을 수락 증거로 취급하지 않는다.

## §27 추가 안전 검증 이후 최신 수동 상태

원격 Maven에서 **누락 자격증명 native task 실행 → FA003**, **http:// URL `help` 구성 → FA003**는 각 `--offline`로 실제 2/2 PASS했다(`analysis/local-https-maven-probe-2026-11-13/negative-contract-v2-2026-11-13/manifest.json`). 따라서 이 **두 실패 분기**는 더 이상 수동 확인 대상이 아니다. 최종 코드에서 원격 게시 **`--dry-run --configuration-cache --offline` STORE→HIT** 역시 JUnit 1 PASS로 재검증했다(`FOLLOWUP-POST-REMOTE-FA003-DRYRUN-CC-1PASS.xml`). 실제 native **인증 HTTPS PUT의 성공**은 `--offline`에서 localhost조차 Gradle이 차단하며, 명시적 PasswordCredentials native task는 CC 미지원이므로 **NOT VERIFIED**이고 성공으로 인정하지 않는다.

IntelliJ GUI 안내 단계가 사용자의 앱에서 완료로 표시됐더라도 독립 fixture `analysis/idea-gui-verification-2026-11-13/`에 `.idea` 결과가 확인되지 않았고 실제 화면/Sync 로그를 받지 못했으므로 **G6/G28 GUI 수락은 계속 BLOCKED**다. EULA는 명시적 동의가 없으므로 **서버 완전 부팅과 틱 미실행**, Loader 발견·초기화만 이전 부분 PASS를 유지한다. G9 1.21.11 런타임도 같은 의미로 미수락. **최종 기본 JUnit 207 PASS·40 SKIP·0 FAIL**, 마지막 JAR 해시와 매니페스트는 `QA_REPORT.md` §27 및 `FOLLOWUP-FINAL-2026-11-13-remaining-verification-index.json` 참조. 전부 수락되기 전 정식 출시 `NOT APPROVED`.

## §28 추가 — 네이티브 Maven HTTPS 전송은 이제 실제 PASS, CC는 미지원

`QA_REPORT.md` §28의 최신 localhost 전용 HTTPS 네이티브 게시 실험에서 Gradle 9.7.1 실제 게시 태스크는 **두 번 모두 성공**, Basic 인증된 201 PUT 25건씩 총 50건과 JAR/sources/POM/module 바이트 일치를 기록했다. 그 이전 §26/§27의 PUT 0건은 **당시 `--offline` 시험에 대한 역사적 사실**이므로 수정하지 않는다. 신규 매니페스트 `analysis/local-https-maven-probe-2026-11-13/online-loopback-2026-11-14/manifest.json`. 게시 시에만 `--offline`을 제외하고 외부 HTTP(S)는 루프백 거부 프록시로 제한했다.

**수동 확인으로 바꿀 수 없는 현재 기술적 미수락:** Gradle 9.7.1 native `PublishToMavenRepository`와 명시적 `PasswordCredentials`는 실제 두 번의 전송에서 각각 `Configuration cache disabled because incompatible task was found`를 기록했다. 실제 PUT은 PASS라도 해당 태스크의 CC STORE/HIT는 0건이다. IntelliJ GUI 최초/수동/자동 Sync, EULA 직접 검토·승인 후 전용 서버, 레거시 게임 런타임은 여전히 사람의 환경·판단이 필요한 별도 미완료 게이트다. 전체 출시 `NOT APPROVED`.
