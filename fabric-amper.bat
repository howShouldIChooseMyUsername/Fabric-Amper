@echo off
setlocal EnableExtensions DisableDelayedExpansion
rem Fabric Amper Windows wrapper; requires a compatible Java and Gradle.
rem No Gradle build script or authored fabric.mod.json is needed in consumers.
set "FA_ROOT=%~dp0"
set "FA_CONSUMER=%CD%"
set "FA_GRADLE="
set "FA_VERSION=0.1.1"

if /i "%~1"=="-C" goto :projectOption
if /i "%~1"=="--project" goto :projectOption
goto :dispatch

:projectOption
if "%~2"=="" (
    echo ERROR: --project requires a consumer directory.
    exit /b 2
)
set "FA_CONSUMER=%~f2"
shift
shift

:dispatch
if /i "%~1"=="help" goto :help
if /i "%~1"=="--help" goto :help
if /i "%~1"=="/?" goto :help
if /i "%~1"=="version" goto :version
if /i "%~1"=="doctor" goto :doctor
if /i "%~1"=="docs" goto :docs
if /i "%~1"=="init" goto :init
if /i "%~1"=="tasks" goto :tasks
if /i "%~1"=="diagnose" goto :diagnose
if /i "%~1"=="explain" goto :explain
if /i "%~1"=="plan" goto :plan
if /i "%~1"=="port" goto :port
if /i "%~1"=="validate" goto :validate
if /i "%~1"=="build" goto :build
if /i "%~1"=="verify" goto :verify
if /i "%~1"=="test" goto :test
if /i "%~1"=="clean" goto :clean
if /i "%~1"=="run" goto :run
if /i "%~1"=="server" goto :server
if /i "%~1"=="publish" goto :publish
if "%~1"=="" goto :help
echo ERROR: Unknown command "%~1".
goto :usageError

:help
echo.
echo Fabric Amper %FA_VERSION% - Windows convenience wrapper
echo.
echo Usage:
echo   fabric-amper.bat help
echo   fabric-amper.bat doctor [--full^|--paths^|--fix [--dry-run]]
echo   fabric-amper.bat init ^<mod_id^> [new_folder] [mod^|api]
echo   fabric-amper.bat init ^<mod_id^> --here [mod^|api]
echo   fabric-amper.bat [-C consumer_folder] ^<command^>
echo.
echo Consumer commands:
echo   validate    Gradle validateFabricAmper
echo   build       Gradle validateFabricAmper, build, verifyFabricAmperJar
echo   verify      Gradle verifyFabricAmperJar
echo   test        Gradle test
echo   tasks       Gradle tasks --all
echo   diagnose    Gradle fabricAmperDoctor for the consumer
echo   explain     Gradle fabricAmperExplain dependency report
echo   plan        Gradle fabricAmperReleasePlan local release preview
echo   port ^<target_version^>  Gradle fabricAmperPortingReport (e.g. 26.3)
echo   clean       Gradle clean
echo   run         Gradle runClient in the consumer development directory
echo   server      Gradle runServer; user must review the Minecraft EULA
echo   publish local^|maven^|modrinth^|curseforge^|all
echo               Only remote targets ask for typed confirmation.
echo.
echo Other commands: docs, version.
echo   doctor             Check local Java and Gradle availability.
echo   doctor --full      Also inspect JDK hints and Windows paths.
echo   doctor --paths     Inspect local path lengths without running Gradle.
echo   doctor --fix       Opt-in JDK 25/Gradle repair and missing consumer setup.
echo   doctor --fix --dry-run   Preview the repair without changes or downloads.
echo.
echo If invoked from this producer directory, init creates a sibling folder.
echo Otherwise init creates a new child folder in the current directory.
echo init --here keeps existing tools and notes, but rejects existing project files.
echo Generated Settings projects resolve Fabric Amper v0.1.1 from JitPack.
echo Use --project or -C to run consumer commands from any directory.
echo See HUMAN_QuickGuide.md and FabricAmperSKILL_SYN.md.
exit /b 0

:usageError
echo Run fabric-amper.bat help for examples.
exit /b 2

:version
echo Fabric Amper wrapper %FA_VERSION%
exit /b 0

:docs
echo Quick guide: "%FA_ROOT%HUMAN_QuickGuide.md"
echo Human manual: "%FA_ROOT%HUMAN.md"
echo Compact skill: "%FA_ROOT%FabricAmperSKILL_SYN.md"
echo Full skill: "%FA_ROOT%FabricAmperSKILL.md"
exit /b 0

:doctor
if /i "%~2"=="--fix" goto :doctorFix
set "FA_DOCTOR_MODE=basic"
if /i "%~2"=="--full" set "FA_DOCTOR_MODE=full"
if /i "%~2"=="--paths" set "FA_DOCTOR_MODE=paths"
if not "%~2"=="" if /i "%FA_DOCTOR_MODE%"=="basic" (
    echo ERROR: Unknown doctor option "%~2". Use --full or --paths.
    exit /b 2
)
if not "%~3"=="" (
    echo ERROR: doctor accepts at most one option: --full or --paths.
    exit /b 2
)
echo.
echo [doctor] Producer: "%FA_ROOT%"
echo [doctor] Consumer: "%FA_CONSUMER%"
set "FA_DOCTOR_ERROR=0"
echo [doctor] Plugin source: JitPack com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.1
echo [INFO] Remote JitPack availability is not checked by doctor.
if /i "%FA_DOCTOR_MODE%"=="full" call :doctorPaths
if errorlevel 1 exit /b 1
if /i "%FA_DOCTOR_MODE%"=="paths" (
    call :doctorPaths
    if errorlevel 1 exit /b 1
    exit /b 0
)
where java.exe >nul 2>&1
if errorlevel 1 (
    echo [ERROR] java.exe is not on PATH. JDK 25 is required for MC 26.3.
    set "FA_DOCTOR_ERROR=1"
) else (
    echo [doctor] Java runtime:
    java -version
)
if /i "%FA_DOCTOR_MODE%"=="full" call :doctorToolchains
call :resolveGradle
if errorlevel 1 (
    echo [ERROR] No supported Gradle launcher found.
    exit /b 1
)
echo [doctor] Gradle launcher: "%FA_GRADLE%"
rem Running an uncached Gradle Wrapper can download a distribution even for --version.
rem Read the Wrapper's pinned version instead; direct local Gradle launchers are safe.
for %%G in ("%FA_GRADLE%") do set "FA_GRADLE_LEAF=%%~nxG"
if /i "%FA_GRADLE_LEAF%"=="gradlew.bat" (
    call :doctorWrapperVersion
) else (
    call "%FA_GRADLE%" --version
    if errorlevel 1 exit /b 1
)
if "%FA_DOCTOR_ERROR%"=="1" exit /b 1
exit /b 0

:doctorFix
if not "%~4"=="" (
    echo ERROR: doctor --fix accepts at most --dry-run.
    exit /b 2
)
if not "%~3"=="" if /i not "%~3"=="--dry-run" (
    echo ERROR: doctor --fix accepts only the optional --dry-run flag.
    exit /b 2
)
if not exist "%FA_ROOT%scripts\fabric-amper-doctor-fix.ps1" (
    echo ERROR: The doctor repair helper is missing from this Fabric Amper checkout.
    exit /b 1
)
where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo ERROR: Windows PowerShell is required for opt-in repair.
    exit /b 1
)
if /i "%~3"=="--dry-run" (
    powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%FA_ROOT%scripts\fabric-amper-doctor-fix.ps1" -Preview
) else (
    powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%FA_ROOT%scripts\fabric-amper-doctor-fix.ps1"
)
exit /b %errorlevel%

:doctorToolchains
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        echo [OK] JAVA_HOME contains java.exe.
    ) else (
        echo [WARN] JAVA_HOME does not contain bin\java.exe.
    )
    if exist "%JAVA_HOME%\bin\javac.exe" (
        echo [OK] JAVA_HOME contains javac.exe.
    ) else (
        echo [WARN] JAVA_HOME does not contain bin\javac.exe.
    )
) else (
    echo [INFO] JAVA_HOME is unset; Gradle may use a toolchain configured elsewhere.
)
where javac.exe >nul 2>&1
if errorlevel 1 (
    echo [INFO] javac.exe is not on PATH. A JDK may still be available via Gradle toolchains.
) else (
    echo [doctor] PATH Java compiler:
    javac -version
)
echo [hint] Minecraft 26.3 requires Java 25; supported legacy 1.21.11 uses a Java 21 compile toolchain.
echo [hint] The active Java, JAVA_HOME and Gradle's selected toolchain can differ.
echo [hint] Check Gradle toolchains in the consumer before changing any installed JDK.
echo [hint] Check IntelliJ import and Sync in the IDE; command-line success does not verify GUI Sync.
echo [hint] Amper's metadata mirror and Loom/Gradle caches are separate; inspect the failing layer first.
echo [hint] For suspected stale dependencies, review --refresh-dependencies manually. Doctor never clears caches.
exit /b 0

:doctorPaths
set "FA_CWD=%CD%"
where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo [ERROR] PowerShell is needed for read-only Windows path length diagnostics.
    exit /b 1
)
rem The two suffixes are estimates for Amper-owned outputs. Loom's own cache
rem and user resources can create longer paths; do not claim a global maximum.
powershell.exe -NoProfile -NonInteractive -Command "$cwd=$env:FA_CWD; $consumer=($env:FA_CONSUMER).TrimEnd('\'); if($consumer -match '^[A-Za-z]:$'){$consumer+='\' }; $suffix='\build\generated\fabric-amper\resources\main\fabric.mod.json'; $mirrorSuffix='\.gradle\fabric-amper\loom\'+('a'*64)+'\fabric.mod.json'; $generated=$consumer.Length+$suffix.Length; $mirror=$consumer.Length+$mirrorSuffix.Length; Write-Output ('[paths] Current directory characters: '+$cwd.Length); Write-Output ('[paths] Consumer directory characters: '+$consumer.Length); Write-Output ('[paths] Estimated generated metadata characters: '+$generated); Write-Output ('[paths] Estimated 64-hash mirror characters: '+$mirror); if(-not [IO.Directory]::Exists($env:FA_CONSUMER)){Write-Output '[WARN] Consumer directory does not exist.'}; if($consumer.Length -ge 140 -or $generated -ge 240 -or $mirror -ge 240){Write-Output '[WARN] Deep Windows paths can exceed legacy 260-character limits after Loom/Gradle add suffixes.'; Write-Output '[hint] Consider a short consumer location such as C:\dev\my-mod, or a short project-specific Gradle cache.'}else{Write-Output '[OK] Estimated Amper paths are below the conservative 240-character warning threshold.'}; Write-Output '[INFO] Actual Loom and Gradle cache paths may be longer; this is not a runtime compatibility check.'"
exit /b %errorlevel%

:doctorWrapperVersion
for %%G in ("%FA_GRADLE%") do set "FA_WRAPPER_PROPS=%%~dpGgradle\wrapper\gradle-wrapper.properties"
if not exist "%FA_WRAPPER_PROPS%" (
    echo [INFO] Wrapper version unknown: gradle-wrapper.properties is missing. No Wrapper run attempted.
    exit /b 0
)
where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo [INFO] Wrapper version unknown: PowerShell unavailable. No Wrapper run attempted.
    exit /b 0
)
powershell.exe -NoProfile -NonInteractive -Command "$found=$false; foreach($line in [IO.File]::ReadAllLines($env:FA_WRAPPER_PROPS)){if($line -match '^\s*distributionUrl\s*=.*gradle-(\d+(?:\.\d+){1,3})-(?:bin|all)\.zip'){Write-Output ('[doctor] Gradle Wrapper requested version: '+$Matches[1]+' (properties only; not executed)'); $found=$true; break}}; if(-not $found){Write-Output '[INFO] Wrapper version unknown: inspect distributionUrl in gradle-wrapper.properties.'}"
if errorlevel 1 echo [INFO] Could not read Wrapper version; no Wrapper run attempted.
exit /b 0

:init
if "%~2"=="" (
    echo ERROR: init requires a lowercase Fabric mod ID.
    goto :usageError
)
set "FA_INIT_ID=%~2"
if not "%~5"=="" (
    echo ERROR: init received too many arguments.
    exit /b 2
)
powershell -NoProfile -NonInteractive -Command "if($env:FA_INIT_ID -cmatch '^[a-z][a-z0-9_-]{1,63}$'){exit 0}else{exit 1}"
if errorlevel 1 (
    echo ERROR: Mod ID must start with lowercase a-z and be 2-64 chars [a-z0-9_-].
    exit /b 2
)
set "FA_INIT_HERE=0"
if /i "%~3"=="--here" (
    set "FA_INIT_HERE=1"
    set "FA_TARGET=%FA_CONSUMER%"
) else (
    if /i "%~4"=="--here" goto :initInvalidHere
    if not "%~3"=="" (
        set "FA_TARGET=%~f3"
    ) else (
        if /i "%CD%\"=="%FA_ROOT%" (
            set "FA_TARGET=%FA_ROOT%..\%FA_INIT_ID%"
        ) else (
            set "FA_TARGET=%CD%\%FA_INIT_ID%"
        )
    )
)
set "FA_PROFILE=%~4"
if not defined FA_PROFILE set "FA_PROFILE=mod"
if /i "%FA_PROFILE%"=="mod" set "FA_PRODUCT=fabric/mod"
if /i "%FA_PROFILE%"=="api" set "FA_PRODUCT=fabric/api"
if not defined FA_PRODUCT (
    echo ERROR: Profile must be mod or api.
    exit /b 2
)
if "%FA_INIT_HERE%"=="1" goto :initExistingDirectory
if exist "%FA_TARGET%" (
    echo ERROR: Target already exists. Refusing to overwrite: "%FA_TARGET%"
    exit /b 3
)
goto :initPreflightDone

:initExistingDirectory
if not exist "%FA_TARGET%\" (
    echo ERROR: --here requires an existing directory: "%FA_TARGET%"
    exit /b 3
)
rem Project files are never merged or overwritten by init --here, even when
rem tool/config files like fabric-amper.bat, markdown or .gitignore exist.
if exist "%FA_TARGET%\mod.yaml" goto :initConflict
if exist "%FA_TARGET%\mod.yml" goto :initConflict
if exist "%FA_TARGET%\settings.gradle.kts" goto :initConflict
if exist "%FA_TARGET%\build.gradle.kts" goto :initConflict
if exist "%FA_TARGET%\build.gradle" goto :initConflict
if exist "%FA_TARGET%\src" goto :initConflict
if exist "%FA_TARGET%\schemas\mod.schema.json" goto :initConflict
goto :initPreflightDone

:initConflict
echo ERROR: Existing project files detected. init --here refuses to overwrite or merge them.
echo        Check mod.yaml, mod.yml, settings.gradle.kts, build.gradle^(.kts^), src and schemas.
exit /b 3

:initInvalidHere
echo ERROR: --here must immediately follow the mod ID, not a folder path.
exit /b 2

:initPreflightDone
rem Always use the producer's CURRENT schema for editor completion. The YAML
rem document itself omits schema: N so the plugin selects its latest supported
rem schema at parse time (currently v1), instead of pinning init's version.
if not exist "%FA_ROOT%fabric-amper.schema.json" (
    echo ERROR: Current Fabric Amper schema is missing: "%FA_ROOT%fabric-amper.schema.json".
    echo        Cannot create a starter with a broken YAML autocomplete link.
    exit /b 1
)
mkdir "%FA_TARGET%\schemas"
if errorlevel 1 (
    echo ERROR: Cannot create target schema directory: "%FA_TARGET%\schemas".
    exit /b 1
)
copy /b /y "%FA_ROOT%fabric-amper.schema.json" "%FA_TARGET%\schemas\mod.schema.json" >nul
if errorlevel 1 (
    echo ERROR: Cannot copy current Fabric Amper schema into new project.
    exit /b 1
)
mkdir "%FA_TARGET%\src\main\java\org\example"
if errorlevel 1 (
    echo ERROR: Cannot create target: "%FA_TARGET%"
    exit /b 1
)
if not exist "%FA_TARGET%\.vscode\settings.json" (
    if not exist "%FA_TARGET%\.vscode\" mkdir "%FA_TARGET%\.vscode"
    if errorlevel 1 (
        echo ERROR: Cannot create .vscode directory for YAML suggestions.
        exit /b 1
    )
    > "%FA_TARGET%\.vscode\settings.json" echo({
    >> "%FA_TARGET%\.vscode\settings.json" echo(    "yaml.schemas": {
    >> "%FA_TARGET%\.vscode\settings.json" echo(        "./schemas/mod.schema.json": ["mod.yaml", "mod.yml"]
    >> "%FA_TARGET%\.vscode\settings.json" echo(    }
    >> "%FA_TARGET%\.vscode\settings.json" echo(}
)
rem GitHub JitPack module provides the Settings plugin without a local setup.
rem Keep the GitHub owner spelling and tagged artifact version exact.
> "%FA_TARGET%\settings.gradle.kts" echo(pluginManagement {
>> "%FA_TARGET%\settings.gradle.kts" echo(    repositories {
>> "%FA_TARGET%\settings.gradle.kts" echo(        maven("https://jitpack.io")
>> "%FA_TARGET%\settings.gradle.kts" echo(        maven("https://maven.fabricmc.net/")
>> "%FA_TARGET%\settings.gradle.kts" echo(        gradlePluginPortal()
>> "%FA_TARGET%\settings.gradle.kts" echo(        mavenCentral()
>> "%FA_TARGET%\settings.gradle.kts" echo(    }
>> "%FA_TARGET%\settings.gradle.kts" echo(    resolutionStrategy {
>> "%FA_TARGET%\settings.gradle.kts" echo(        eachPlugin {
>> "%FA_TARGET%\settings.gradle.kts" echo(            if ^(requested.id.id == "dev.fabric-amper.settings"^) {
>> "%FA_TARGET%\settings.gradle.kts" echo(                useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.1")
>> "%FA_TARGET%\settings.gradle.kts" echo(            }
>> "%FA_TARGET%\settings.gradle.kts" echo(        }
>> "%FA_TARGET%\settings.gradle.kts" echo(    }
>> "%FA_TARGET%\settings.gradle.kts" echo(}
>> "%FA_TARGET%\settings.gradle.kts" echo(plugins {
>> "%FA_TARGET%\settings.gradle.kts" echo(    id("dev.fabric-amper.settings") version "0.1.1"
>> "%FA_TARGET%\settings.gradle.kts" echo(}

rem New projects default to a Java-only Minecraft 26.3 fixture.
rem Omitted schema selects the newest syntax supported by this plugin (currently v1).
> "%FA_TARGET%\mod.yaml" echo(# yaml-language-server: $schema=./schemas/mod.schema.json
>> "%FA_TARGET%\mod.yaml" echo(product: %FA_PRODUCT%
>> "%FA_TARGET%\mod.yaml" echo(mod:
>> "%FA_TARGET%\mod.yaml" echo(  id: %FA_INIT_ID%
>> "%FA_TARGET%\mod.yaml" echo(  version: "1.0.0+26.3"
>> "%FA_TARGET%\mod.yaml" echo(  group: org.example
>> "%FA_TARGET%\mod.yaml" echo(  name: Example Mod
>> "%FA_TARGET%\mod.yaml" echo(  entrypoints:
>> "%FA_TARGET%\mod.yaml" echo(    main:
>> "%FA_TARGET%\mod.yaml" echo(      - org.example.ExampleMod
>> "%FA_TARGET%\mod.yaml" echo(minecraft:
>> "%FA_TARGET%\mod.yaml" echo(  version: "26.3"
>> "%FA_TARGET%\mod.yaml" echo(  mappings: mojang
>> "%FA_TARGET%\mod.yaml" echo(  loader: "0.20.1"
>> "%FA_TARGET%\mod.yaml" echo(  fabricApi: "0.165.2+26.3"
>> "%FA_TARGET%\mod.yaml" echo(settings:
>> "%FA_TARGET%\mod.yaml" echo(  java:
>> "%FA_TARGET%\mod.yaml" echo(    release: 25
>> "%FA_TARGET%\mod.yaml" echo(    sourcesJar: true
>> "%FA_TARGET%\mod.yaml" echo(  kotlin:
>> "%FA_TARGET%\mod.yaml" echo(    enabled: false
>> "%FA_TARGET%\mod.yaml" echo(repositories:
>> "%FA_TARGET%\mod.yaml" echo(  - fabric
>> "%FA_TARGET%\mod.yaml" echo(  - mavenCentral

> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(package org.example;
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(import net.fabricmc.api.ModInitializer;
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(public final class ExampleMod implements ModInitializer {
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(    @Override public void onInitialize() {
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(        System.out.println("Fabric Amper starter initialized");
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(    }
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(}

if not exist "%FA_TARGET%\settings.gradle.kts" exit /b 1
if not exist "%FA_TARGET%\mod.yaml" exit /b 1
if not exist "%FA_TARGET%\schemas\mod.schema.json" exit /b 1
if not exist "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" exit /b 1
echo [init] Created: "%FA_TARGET%"
echo [init] Profile: %FA_PRODUCT%
if "%FA_INIT_HERE%"=="1" echo [init] Mode: --here; existing non-project tools preserved.
echo [init] YAML autocomplete: schemas\mod.schema.json ^(current producer schema snapshot^).
echo [init] Settings plugin: JitPack Fabric-Amper v0.1.1 ^(Internet required on first resolution^).
echo [init] Next: cd to the new folder and run build.
exit /b 0

:tasks
call :consumerGradle tasks --all
exit /b %errorlevel%

:diagnose
call :consumerGradle fabricAmperDoctor
exit /b %errorlevel%

:explain
call :consumerGradle fabricAmperExplain
exit /b %errorlevel%

:plan
call :consumerGradle fabricAmperReleasePlan
exit /b %errorlevel%

:port
if "%~2"=="" (
    echo ERROR: port requires a numeric target version such as 26.3 or 1.21.11.
    exit /b 2
)
if not "%~3"=="" (
    echo ERROR: port accepts exactly one target version.
    exit /b 2
)
set "FA_PORT_TARGET=%~2"
where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo ERROR: PowerShell is required to validate the target version.
    exit /b 2
)
rem Validate before inserting the target into a Gradle command line. Only numeric
rem Minecraft version components are accepted; no spaces, shell metacharacters,
rem Gradle flags or property syntax can pass this check.
powershell.exe -NoProfile -NonInteractive -Command "if($env:FA_PORT_TARGET -cmatch '^(?:[0-9]{1,4}\.){1,3}[0-9]{1,4}$'){exit 0}else{exit 2}"
if errorlevel 1 (
    echo ERROR: Invalid target version. Use numeric dotted form, e.g. 26.3 or 1.21.11.
    exit /b 2
)
call :consumerGradle fabricAmperPortingReport "-PfabricAmperPortTarget=%FA_PORT_TARGET%"
exit /b %errorlevel%

:validate
call :consumerGradle validateFabricAmper
exit /b %errorlevel%

:build
call :consumerGradle validateFabricAmper build verifyFabricAmperJar
exit /b %errorlevel%

:verify
call :consumerGradle verifyFabricAmperJar
exit /b %errorlevel%

:test
call :consumerGradle test
exit /b %errorlevel%

:clean
call :consumerGradle clean
exit /b %errorlevel%

:run
echo [run] Starting isolated Gradle runClient in consumer project: "%FA_CONSUMER%"
call :consumerGradle runClient
exit /b %errorlevel%

:server
echo [server] Gradle runServer uses the consumer's development run directory.
echo [server] The user must read and accept Minecraft's EULA manually.
call :consumerGradle runServer
exit /b %errorlevel%

:publish
if /i "%~2"=="local" goto :publishLocal
set "FA_PUBLISH_TASK="
if /i "%~2"=="maven" set "FA_PUBLISH_TASK=publishFabricAmperMaven"
if /i "%~2"=="modrinth" set "FA_PUBLISH_TASK=publishFabricAmperModrinth"
if /i "%~2"=="curseforge" set "FA_PUBLISH_TASK=publishFabricAmperCurseForge"
if /i "%~2"=="all" set "FA_PUBLISH_TASK=publishFabricAmper"
if not defined FA_PUBLISH_TASK (
    echo ERROR: Choose publish local, maven, modrinth, curseforge, or all.
    exit /b 2
)
call :checkConsumer
if errorlevel 1 exit /b 2
pushd "%FA_CONSUMER%" || exit /b 1
call :resolveGradle
if errorlevel 1 (
    popd
    exit /b 1
)
echo.
echo [PUBLISH WARNING] Destination: %~2
echo [PUBLISH WARNING] Gradle task: %FA_PUBLISH_TASK%
echo [PUBLISH WARNING] This can upload a REAL public release or Maven artifact.
echo [PUBLISH WARNING] Verify YAML project IDs, versions, permissions, token env names.
echo [PUBLISH WARNING] Multi-platform publishing is NOT atomic and may partially succeed.
echo.
set "FA_CONFIRM="
set /p "FA_CONFIRM=Type PUBLISH (all capitals) to continue, anything else to cancel: "
if not "%FA_CONFIRM%"=="PUBLISH" (
    echo [publish] Cancelled. No remote publishing task invoked.
    popd
    exit /b 2
)
call "%FA_GRADLE%" validateFabricAmper build verifyFabricAmperJar
if errorlevel 1 (
    echo ERROR: Local validation failed. No publishing task invoked.
    popd
    exit /b 1
)
call "%FA_GRADLE%" %FA_PUBLISH_TASK%
set "FA_RESULT=%errorlevel%"
popd
exit /b %FA_RESULT%

:publishLocal
echo [publish local] Writes to consumer build/fabric-amper/maven-repository only.
call :consumerGradle validateFabricAmper build verifyFabricAmperJar publishMavenJavaPublicationToFabricAmperLocalRepository
exit /b %errorlevel%

:checkConsumer
if not exist "%FA_CONSUMER%\settings.gradle.kts" (
    echo ERROR: Missing consumer settings.gradle.kts in "%FA_CONSUMER%".
    exit /b 2
)
if exist "%FA_CONSUMER%\build.gradle" (
    echo ERROR: Consumer build.gradle is incompatible with settings-only mode.
    exit /b 2
)
if exist "%FA_CONSUMER%\build.gradle.kts" (
    echo ERROR: Consumer build.gradle.kts is incompatible with settings-only mode.
    exit /b 2
)
if exist "%FA_CONSUMER%\mod.yaml" if exist "%FA_CONSUMER%\mod.yml" (
    echo ERROR: Both mod.yaml and mod.yml exist. Keep exactly one.
    exit /b 2
)
if not exist "%FA_CONSUMER%\mod.yaml" if not exist "%FA_CONSUMER%\mod.yml" (
    echo ERROR: Missing mod.yaml or mod.yml in "%FA_CONSUMER%".
    exit /b 2
)
if exist "%FA_CONSUMER%\src\main\resources\fabric.mod.json" (
    echo ERROR: Authored src/main/resources/fabric.mod.json is forbidden [FA008].
    exit /b 2
)
if exist "%FA_CONSUMER%\src\client\resources\fabric.mod.json" (
    echo ERROR: Authored src/client/resources/fabric.mod.json is forbidden [FA008].
    exit /b 2
)
exit /b 0

:consumerGradle
call :checkConsumer
if errorlevel 1 exit /b 2
pushd "%FA_CONSUMER%" || exit /b 1
call :resolveGradle
if errorlevel 1 (
    popd
    exit /b 1
)
call "%FA_GRADLE%" %*
set "FA_RESULT=%errorlevel%"
popd
exit /b %FA_RESULT%

:resolveGradle
set "FA_GRADLE="
if defined FABRIC_AMPER_GRADLE (
    if not exist "%FABRIC_AMPER_GRADLE%" (
        echo ERROR: FABRIC_AMPER_GRADLE points to a missing executable.
        exit /b 1
    )
    set "FA_GRADLE=%FABRIC_AMPER_GRADLE%"
    exit /b 0
)
if exist "%CD%\gradlew.bat" (
    set "FA_GRADLE=%CD%\gradlew.bat"
    exit /b 0
)
if defined GRADLE_HOME (
    if exist "%GRADLE_HOME%\bin\gradle.bat" (
        set "FA_GRADLE=%GRADLE_HOME%\bin\gradle.bat"
        exit /b 0
    )
)
for /f "delims=" %%G in ('where gradle.bat 2^>nul') do if not defined FA_GRADLE set "FA_GRADLE=%%G"
if defined FA_GRADLE exit /b 0
rem Fallback: scan all cached Wrapper distributions (bin and all, any version/hash).
rem Compare numeric versions: 9.10.0 is newer than 9.9.1.
if defined USERPROFILE call :findCachedGradle
if defined FA_GRADLE exit /b 0
echo ERROR: No Gradle launcher found. Install Gradle, use a consumer Wrapper,
echo        or set FABRIC_AMPER_GRADLE to the absolute path of gradle.bat.
exit /b 1

:findCachedGradle
for /f "usebackq delims=" %%G in (`powershell -NoProfile -NonInteractive -Command "$root=Join-Path $env:USERPROFILE '.gradle\wrapper\dists'; $best=[version]'0.0'; $selected=''; foreach($file in (Get-Item -Path (Join-Path $root 'gradle-*\*\gradle-*\bin\gradle.bat') -ErrorAction SilentlyContinue)) { if($file.FullName -match '\\gradle-(\d+(?:\.\d+){1,3})\\bin\\gradle\.bat$') { $version=[version]$Matches[1]; if($version -gt $best) { $best=$version; $selected=$file.FullName } } }; if($selected) { Write-Output $selected }"`) do if not defined FA_GRADLE set "FA_GRADLE=%%G"
exit /b 0
