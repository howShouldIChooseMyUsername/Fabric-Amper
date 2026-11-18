@echo off
setlocal EnableExtensions DisableDelayedExpansion
rem Fabric Amper Windows wrapper; requires a compatible Java and Gradle.
rem No Gradle build script or authored fabric.mod.json is needed in consumers.
set "FA_ROOT=%~dp0"
set "FA_CONSUMER=%CD%"
set "FA_GRADLE="
set "FA_VERSION=0.3.1"
set "FA_MIGRATE_PROJECT_PREFIX=0"
set "FA_HELPER_SOURCE=release"
rem Release-pinned helper downloads always go into the scripts directory.
set "FA_SCRIPTS_DIR=%FA_ROOT%scripts"
rem Release-pinned helper hashes must match the exact uploaded bytes.
rem Reusing a helper requires the v0.3.1 release to publish the same verified
rem bytes; never assume a helper was updated just because FA_VERSION changed.
set "FA_DOCTOR_SHA256=c8fa4389ba8e8fe3b1d8430fb77313c41d3f042c2f8d06e4630e969643903053"
set "FA_MIGRATE_SHA256=20ff79e813933b724b8032789253c89747cd36df5895d05d1321ffb8133d0b2d"
set "FA_SCHEMA_SHA256=5974608d6a8a0b37c9830fd9e2e01c298cf2d6495b6af40639d99ba63a716615"

if /i "%~1"=="-C" goto :projectOption
if /i "%~1"=="--project" goto :projectOption
goto :dispatch

:projectOption
if "%~2"=="" (
    echo ERROR: --project requires a consumer directory.
    exit /b 2
)
set "FA_CONSUMER=%~f2"
set "FA_DOCTOR_PROJECT_SEEN=1"
set "FA_MIGRATE_PROJECT_PREFIX=1"
shift
shift

:dispatch
if "%~1"=="" goto :version
if /i "%~1"=="-V" goto :version
if /i "%~1"=="--version" goto :version
if /i "%~1"=="help" goto :help
if /i "%~1"=="--help" goto :help
if /i "%~1"=="/?" goto :help
if /i "%~1"=="version" goto :version
if /i "%~1"=="doctor" goto :doctor
if /i "%~1"=="migrate" goto :migrate
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
echo ERROR: Unknown command "%~1".
goto :usageError

:help
echo.
echo Fabric Amper %FA_VERSION% - Windows convenience wrapper
echo.
echo Usage:
echo   fabric-amper.bat            Show the release version and exit
echo   fabric-amper.bat -V         Show the release version and exit
echo   fabric-amper.bat --version  Show the release version and exit
echo   fabric-amper.bat help
echo   fabric-amper.bat doctor [-C consumer_folder] [--full^|--paths^|--fix [--dry-run]]
echo   fabric-amper.bat doctor -C consumer_folder doctor
echo   fabric-amper.bat migrate [--dry-run] [-D^|--delete]
echo   fabric-amper.bat migrate -C "consumer_folder" [--dry-run] [-D^|--delete]
echo   fabric-amper.bat -C "consumer_folder" migrate [-D^|--delete]
echo   fabric-amper.bat init ^<mod_id^> [new_folder] [mod^|api] [--mod-fabric] [--mod-neoforge] [--architectury] [--kotlin] [--splitEnvironmentSourceSets] [--legacy-schema1]
echo   fabric-amper.bat init ^<mod_id^> --here [mod^|api] [--mod-fabric] [--mod-neoforge] [--architectury] [--kotlin] [--splitEnvironmentSourceSets] [--legacy-schema1]
echo               Missing schema is downloaded from GitHub main, SHA-256 verified.
echo               No loader flag defaults to Fabric; a single flag creates one loader project.
echo               Both loader flags create fabric/ and neoforge/; --architectury also creates common/.
echo               --architectury enables per-loader Architectury API and shared examples.
echo               --kotlin selects Kotlin examples; otherwise Java is used.
echo               --splitEnvironmentSourceSets creates a Fabric client source set.
echo               Both loaders with Architectury ignore the split flag.
echo               New projects use schema 1.1; --legacy-schema1 opts into old Fabric-only schema 1.
echo               Flags can be combined in any order after the optional folder/profile.
echo   fabric-amper.bat [-C consumer_folder] ^<command^>
echo.
echo Consumer commands:
echo   validate    Validate every selected loader ^(dual: :fabric and :neoforge^)
echo   build       Validate, build and verify every selected loader
echo   verify      Verify every selected loader's distributable JAR
echo               Dual-loader projects run both platform tasks explicitly.
echo               No additional Gradle flags are accepted for these commands.
echo   test        Gradle test
echo   tasks       Gradle tasks --all
echo   diagnose    Gradle fabricAmperDoctor for the consumer
echo   explain     Gradle fabricAmperExplain dependency report
echo   plan        Gradle fabricAmperReleasePlan local release preview
echo   port ^<target_version^>  Gradle fabricAmperPortingReport (e.g. 26.3)
echo   migrate     Convert an existing Gradle Fabric mod to settings + mod.yaml
echo               Use --dry-run to preview; -D/--delete removes originals only
echo               after the migration helper validates all generated outputs.
echo               The first normal migrate fetches SHA-256 verified helpers into scripts\.
echo               Missing helpers in --dry-run mode only preview the download.
echo   clean       Gradle clean
echo   run [--mod-fabric^|--mod-neoforge]     Start the selected client.
echo   server [--mod-fabric^|--mod-neoforge]  Start the selected server; review the Minecraft EULA.
echo               Dual-loader projects require exactly one loader flag.
echo               Single-loader projects accept no flag or their matching loader flag.
echo               Example: fabric-amper.bat -C "consumer_folder" run --mod-neoforge
echo   publish local^|maven^|modrinth^|curseforge^|all
echo               Only remote targets ask for typed confirmation.
echo.
echo Other commands: docs, version, -V, --version.
echo   doctor             Check local Java and Gradle availability.
echo   doctor --full      Also inspect JDK hints and Windows paths.
echo   doctor --paths     Inspect local path lengths without running Gradle.
echo   doctor --fix       Opt-in JDK 25/Gradle repair and missing consumer setup.
echo   doctor --fix --dry-run   Preview the repair without changes or downloads.
echo                        First --fix downloads one SHA-256 checked PS1 from
echo                        this version's GitHub Release into scripts\.
echo.
echo If invoked from this producer directory, init creates a sibling folder.
echo Otherwise init creates a new child folder in the current directory.
echo init --here keeps existing tools and notes, but rejects existing project files.
echo Generated Settings projects target the Fabric Amper v%FA_VERSION% prerelease via JitPack.
echo Use --project or -C before consumer commands; doctor also accepts them after its name.
echo See HUMAN_QuickGuide.md and FabricAmperSKILL_SYN.md.
exit /b 0

:usageError
echo Run fabric-amper.bat help for examples.
exit /b 2

:version
echo v%FA_VERSION%
exit /b 0

:docs
echo Quick guide: "%FA_ROOT%HUMAN_QuickGuide.md"
echo Human manual: "%FA_ROOT%HUMAN.md"
echo Compact skill: "%FA_ROOT%FabricAmperSKILL_SYN.md"
echo Full skill: "%FA_ROOT%FabricAmperSKILL.md"
exit /b 0

:migrate
rem The dedicated migration script performs all reads, conversion, validation
rem and optional original-file deletion. BAT only parses arguments and routes;
rem neither Gradle nor any Gradle DSL is executed here.
set "FA_MIGRATE_PROJECT_SEEN=%FA_MIGRATE_PROJECT_PREFIX%"
set "FA_MIGRATE_DELETE=0"
set "FA_MIGRATE_DRY_RUN=0"
shift
:migrateOptions
if "%~1"=="" goto :migrateOptionsDone
if /i "%~1"=="-C" goto :migrateProjectOption
if /i "%~1"=="--project" goto :migrateProjectOption
if /i "%~1"=="-D" goto :migrateFlagDelete
if /i "%~1"=="--delete" goto :migrateFlagDelete
if /i "%~1"=="--dry-run" goto :migrateFlagDryRun
echo ERROR: Unknown migrate argument "%~1". Use -C, -D, --delete or --dry-run.
exit /b 2

:migrateProjectOption
if "%FA_MIGRATE_PROJECT_SEEN%"=="1" goto :migrateDuplicateOption
if "%~2"=="" goto :migrateMissingProject
if /i "%~2"=="-C" goto :migrateMissingProject
if /i "%~2"=="--project" goto :migrateMissingProject
if /i "%~2"=="-D" goto :migrateMissingProject
if /i "%~2"=="--delete" goto :migrateMissingProject
if /i "%~2"=="--dry-run" goto :migrateMissingProject
set "FA_CONSUMER=%~f2"
set "FA_MIGRATE_PROJECT_SEEN=1"
shift
shift
goto :migrateOptions

:migrateMissingProject
echo ERROR: migrate -C/--project requires a consumer directory path.
exit /b 2

:migrateFlagDelete
if "%FA_MIGRATE_DELETE%"=="1" goto :migrateDuplicateOption
set "FA_MIGRATE_DELETE=1"
shift
goto :migrateOptions

:migrateFlagDryRun
if "%FA_MIGRATE_DRY_RUN%"=="1" goto :migrateDuplicateOption
set "FA_MIGRATE_DRY_RUN=1"
shift
goto :migrateOptions

:migrateDuplicateOption
echo ERROR: Duplicate migrate arguments or conflicting consumer directories.
exit /b 2

:migrateOptionsDone
if not exist "%FA_CONSUMER%\" (
    echo ERROR: Migration consumer directory does not exist: "%FA_CONSUMER%".
    exit /b 2
)
where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo ERROR: Windows PowerShell is required for migration.
    exit /b 1
)
set "FA_HELPER_NAME=fabric-amper-migrate.ps1"
set "FA_HELPER_DIR=%FA_SCRIPTS_DIR%"
set "FA_HELPER_HASH=%FA_MIGRATE_SHA256%"
if not exist "%FA_SCRIPTS_DIR%\fabric-amper-migrate.ps1" (
    if "%FA_MIGRATE_DRY_RUN%"=="1" (
        echo [preview] Would fetch exactly fabric-amper-migrate.ps1 from:
        echo [preview] https://github.com/howShouldIChooseMyUsername/Fabric-Amper/releases/download/v%FA_VERSION%/fabric-amper-migrate.ps1
        echo [preview] Would verify SHA-256 and save into scripts\ before running the migration preview.
        echo [preview] No downloads or writes performed. Run migrate without --dry-run to install the verified helper.
        exit /b 0
    )
    call :fetchPinnedHelper
    if errorlevel 1 exit /b 1
)
call :verifyPinnedHelper
if errorlevel 1 exit /b 1
set "FA_HELPER_NAME=fabric-amper.schema.json"
set "FA_HELPER_DIR=%FA_ROOT%"
set "FA_HELPER_HASH=%FA_SCHEMA_SHA256%"
if not exist "%FA_ROOT%fabric-amper.schema.json" (
    if "%FA_MIGRATE_DRY_RUN%"=="1" (
        echo [preview] Would fetch exactly fabric-amper.schema.json from the v%FA_VERSION% GitHub Release.
        echo [preview] A matching local schema is needed to run migration preview. No downloads or writes performed.
        exit /b 0
    )
    call :fetchPinnedHelper
    if errorlevel 1 exit /b 1
)
call :verifyPinnedHelper
if errorlevel 1 exit /b 1
set "FA_MIGRATE_ARGS="
if "%FA_MIGRATE_DELETE%"=="1" set "FA_MIGRATE_ARGS=-Delete"
if "%FA_MIGRATE_DRY_RUN%"=="1" set "FA_MIGRATE_ARGS=%FA_MIGRATE_ARGS% -Preview"
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%FA_SCRIPTS_DIR%\fabric-amper-migrate.ps1" %FA_MIGRATE_ARGS%
exit /b %errorlevel%

:doctor
rem Parse doctor options in any order; support -C both before and after doctor.
rem Accept one redundant trailing "doctor" when a project path was supplied,
rem matching the common "doctor -C folder doctor" invocation.
set "FA_DOCTOR_MODE=basic"
set "FA_DOCTOR_FIX=0"
set "FA_DOCTOR_DRY_RUN=0"
set "FA_DOCTOR_REPEAT_SEEN=0"
if not defined FA_DOCTOR_PROJECT_SEEN set "FA_DOCTOR_PROJECT_SEEN=0"
shift
:doctorOptions
if "%~1"=="" goto :doctorOptionsDone
if /i "%~1"=="-C" goto :doctorProjectOption
if /i "%~1"=="--project" goto :doctorProjectOption
if /i "%~1"=="--full" goto :doctorModeFull
if /i "%~1"=="--paths" goto :doctorModePaths
if /i "%~1"=="--fix" goto :doctorFlagFix
if /i "%~1"=="--dry-run" goto :doctorFlagDryRun
if /i "%~1"=="doctor" goto :doctorRepeatedCommand
echo ERROR: Unknown doctor option "%~1". Use --full, --paths, --fix, --dry-run or -C.
exit /b 2

:doctorProjectOption
if "%~2"=="" (
    echo ERROR: -C or --project requires a consumer directory.
    exit /b 2
)
if "%FA_DOCTOR_PROJECT_SEEN%"=="1" (
    echo ERROR: doctor accepts only one -C or --project option.
    exit /b 2
)
set "FA_CONSUMER=%~f2"
set "FA_DOCTOR_PROJECT_SEEN=1"
shift
shift
goto :doctorOptions

:doctorRepeatedCommand
if not "%FA_DOCTOR_PROJECT_SEEN%"=="1" goto :doctorOptionConflict
if "%FA_DOCTOR_REPEAT_SEEN%"=="1" goto :doctorOptionConflict
set "FA_DOCTOR_REPEAT_SEEN=1"
shift
goto :doctorOptions

:doctorModeFull
if not "%FA_DOCTOR_MODE%"=="basic" goto :doctorOptionConflict
if "%FA_DOCTOR_FIX%"=="1" goto :doctorOptionConflict
set "FA_DOCTOR_MODE=full"
shift
goto :doctorOptions

:doctorModePaths
if not "%FA_DOCTOR_MODE%"=="basic" goto :doctorOptionConflict
if "%FA_DOCTOR_FIX%"=="1" goto :doctorOptionConflict
set "FA_DOCTOR_MODE=paths"
shift
goto :doctorOptions

:doctorFlagFix
if "%FA_DOCTOR_FIX%"=="1" goto :doctorOptionConflict
if not "%FA_DOCTOR_MODE%"=="basic" goto :doctorOptionConflict
set "FA_DOCTOR_FIX=1"
shift
goto :doctorOptions

:doctorFlagDryRun
if "%FA_DOCTOR_DRY_RUN%"=="1" goto :doctorOptionConflict
set "FA_DOCTOR_DRY_RUN=1"
shift
goto :doctorOptions

:doctorOptionConflict
echo ERROR: Conflicting or duplicate doctor arguments.
exit /b 2

:doctorOptionsDone
if "%FA_DOCTOR_DRY_RUN%"=="1" if not "%FA_DOCTOR_FIX%"=="1" (
    echo ERROR: --dry-run requires doctor --fix.
    exit /b 2
)
if "%FA_DOCTOR_FIX%"=="1" goto :doctorFix
echo.
echo [doctor] Producer: "%FA_ROOT%"
echo [doctor] Consumer: "%FA_CONSUMER%"
set "FA_DOCTOR_ERROR=0"
echo [doctor] Plugin source: JitPack com.github.howShouldIChooseMyUsername:Fabric-Amper:v%FA_VERSION% ^(prerelease^).
echo [INFO] Remote JitPack prerelease availability is not checked by doctor.
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
where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo ERROR: Windows PowerShell is required for opt-in repair.
    exit /b 1
)
set "FA_HELPER_NAME=fabric-amper-doctor-fix.ps1"
set "FA_HELPER_DIR=%FA_SCRIPTS_DIR%"
set "FA_HELPER_HASH=%FA_DOCTOR_SHA256%"
if "%FA_DOCTOR_DRY_RUN%"=="1" goto :doctorFixPreview
if not exist "%FA_SCRIPTS_DIR%\fabric-amper-doctor-fix.ps1" (
    call :fetchPinnedHelper
    if errorlevel 1 exit /b 1
)
call :verifyPinnedHelper
if errorlevel 1 exit /b 1
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%FA_SCRIPTS_DIR%\fabric-amper-doctor-fix.ps1"
exit /b %errorlevel%

:doctorFixPreview
if not exist "%FA_SCRIPTS_DIR%\fabric-amper-doctor-fix.ps1" (
    echo [preview] Would fetch exactly fabric-amper-doctor-fix.ps1 from:
    echo [preview] https://github.com/howShouldIChooseMyUsername/Fabric-Amper/releases/download/v%FA_VERSION%/fabric-amper-doctor-fix.ps1
    echo [preview] Would SHA-256 check it and store it in scripts\ beside this BAT before any repair.
    echo [preview] Helper is missing; toolchain checks require the helper. No downloads or writes performed.
    exit /b 0
)
call :verifyPinnedHelper
if errorlevel 1 exit /b 1
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%FA_SCRIPTS_DIR%\fabric-amper-doctor-fix.ps1" -Preview
exit /b %errorlevel%

:verifyPinnedHelper
rem Never execute an existing helper unless its exact release SHA-256 matches.
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; try { $dir=$env:FA_HELPER_DIR; $p=Join-Path $dir $env:FA_HELPER_NAME; if (-not (Test-Path -LiteralPath $dir -PathType Container)) { throw 'Helper directory is unavailable.' }; if (((Get-Item -LiteralPath $dir -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Helper directory is a symlink or junction.' }; if (-not (Test-Path -LiteralPath $p -PathType Leaf)) { throw 'Helper file is unavailable.' }; if (((Get-Item -LiteralPath $p -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Helper is a symlink or junction.' }; $actual=[BitConverter]::ToString(([Security.Cryptography.SHA256]::Create()).ComputeHash([IO.File]::ReadAllBytes($p))).Replace('-',''); if ($actual -ine $env:FA_HELPER_HASH) { throw 'Helper SHA-256 mismatch. File was not executed or overwritten.' }; [Console]::Out.WriteLine('[OK] Verified '+$env:FA_HELPER_NAME+' SHA-256.') } catch { [Console]::Error.WriteLine('[ERROR] '+$_.Exception.Message); exit 1 }"
exit /b %errorlevel%

:fetchPinnedHelper
rem Download one exact asset from the pinned release, or the main branch for init schema.
rem Verify expected bytes before installing; do not overwrite existing files.
if /i "%FA_HELPER_SOURCE%"=="main" (
    echo [fetch] Fetching %FA_HELPER_NAME% from GitHub main branch.
) else (
    echo [fetch] Fetching %FA_HELPER_NAME% from v%FA_VERSION% GitHub Release.
)
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $dir=$env:FA_HELPER_DIR; $name=$env:FA_HELPER_NAME; $dest=Join-Path $dir $name; $tmp=Join-Path $env:FA_ROOT ('.fabric-amper-'+[guid]::NewGuid().ToString('N')+'.tmp'); try { if (Test-Path -LiteralPath $dest) { throw 'Helper appeared during download; refusing to overwrite.' }; if ($env:FA_HELPER_SOURCE -eq 'main') { if ($name -cne 'fabric-amper.schema.json') { throw 'Only the schema may be fetched from main.' }; $url='https://raw.githubusercontent.com/howShouldIChooseMyUsername/Fabric-Amper/main/fabric-amper.schema.json' } else { $url='https://github.com/howShouldIChooseMyUsername/Fabric-Amper/releases/download/v'+$env:FA_VERSION+'/'+$name }; Invoke-WebRequest -Uri ($url+'?sha256='+$env:FA_HELPER_HASH) -Headers @{'Cache-Control'='no-cache';'Pragma'='no-cache'} -OutFile $tmp -MaximumRedirection 5 -TimeoutSec 90 -UseBasicParsing; $actual=[BitConverter]::ToString(([Security.Cryptography.SHA256]::Create()).ComputeHash([IO.File]::ReadAllBytes($tmp))).Replace('-',''); if ($actual -ine $env:FA_HELPER_HASH) { throw 'Downloaded helper SHA-256 mismatch.' }; if (-not (Test-Path -LiteralPath $dir)) { [void](New-Item -ItemType Directory -Path $dir -ErrorAction Stop) }; if (-not (Test-Path -LiteralPath $dir -PathType Container)) { throw 'Helper directory is not a directory.' }; if (((Get-Item -LiteralPath $dir -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Helper directory is a symlink or junction.' }; if (Test-Path -LiteralPath $dest) { throw 'Helper already exists; refusing to overwrite.' }; Move-Item -LiteralPath $tmp -Destination $dest -ErrorAction Stop; [Console]::Out.WriteLine('[OK] Saved SHA-256 verified '+$name+' in '+$dir) } catch { [Console]::Error.WriteLine('[ERROR] Pinned helper download failed: '+$_.Exception.Message); exit 1 } finally { if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue } }"
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
    echo ERROR: init requires a lowercase mod ID.
    goto :usageError
)
set "FA_INIT_ID=%~2"
set "FA_INIT_KOTLIN=0"
set "FA_INIT_SPLIT=0"
set "FA_INIT_ARCHITECTURY=0"
set "FA_INIT_FABRIC=0"
set "FA_INIT_NEOFORGE=0"
set "FA_INIT_MULTI=0"
set "FA_INIT_V11=1"
set "FA_INIT_LEGACY=0"
set "FA_INIT_SPLIT_IGNORED=0"
set "FA_INIT_ARG3="
set "FA_INIT_ARG4="
set "FA_INIT_FLAGS_STARTED=0"
call :initParseArgument "%~3"
if errorlevel 1 exit /b 2
call :initParseArgument "%~4"
if errorlevel 1 exit /b 2
call :initParseArgument "%~5"
if errorlevel 1 exit /b 2
call :initParseArgument "%~6"
if errorlevel 1 exit /b 2
call :initParseArgument "%~7"
if errorlevel 1 exit /b 2
call :initParseArgument "%~8"
if errorlevel 1 exit /b 2
call :initParseArgument "%~9"
if errorlevel 1 exit /b 2
rem Maximal accepted invocation has init + ID + folder + profile + five flags.
rem All relevant values were copied into FA_INIT_* already. Shift the current
rem argument frame to reject a tenth token without reparsing arbitrary input.
shift
shift
shift
shift
shift
shift
shift
shift
shift
if not "%~1"=="" goto :initExtraArguments
goto :initArgumentsReady

:initParseArgument
if "%~1"=="" exit /b 0
if /i "%~1"=="--kotlin" goto :initParseKotlinFlag
if /i "%~1"=="--splitEnvironmentSourceSets" goto :initParseSplitFlag
if /i "%~1"=="--architectury" goto :initParseArchitecturyFlag
if /i "%~1"=="--mod-fabric" goto :initParseFabricFlag
if /i "%~1"=="--mod-neoforge" goto :initParseNeoForgeFlag
if /i "%~1"=="--legacy-schema1" goto :initParseLegacyFlag
rem Unknown switches must not silently become an output directory/profile.
set "FA_INIT_CANDIDATE=%~1"
if "%FA_INIT_CANDIDATE:~0,1%"=="-" if /i not "%~1"=="--here" goto :initExtraArguments
if "%FA_INIT_FLAGS_STARTED%"=="1" goto :initExtraArguments
if not "%FA_INIT_ARG3%"=="" goto :initParseFourthArgument
set "FA_INIT_ARG3=%~1"
exit /b 0

:initParseFourthArgument
if not "%FA_INIT_ARG4%"=="" goto :initExtraArguments
set "FA_INIT_ARG4=%~1"
exit /b 0

:initParseKotlinFlag
if "%FA_INIT_KOTLIN%"=="1" goto :initExtraArguments
set "FA_INIT_KOTLIN=1"
set "FA_INIT_FLAGS_STARTED=1"
exit /b 0

:initParseSplitFlag
if "%FA_INIT_SPLIT%"=="1" goto :initExtraArguments
set "FA_INIT_SPLIT=1"
set "FA_INIT_FLAGS_STARTED=1"
exit /b 0

:initParseArchitecturyFlag
if "%FA_INIT_ARCHITECTURY%"=="1" goto :initExtraArguments
set "FA_INIT_ARCHITECTURY=1"
set "FA_INIT_FLAGS_STARTED=1"
exit /b 0

:initParseFabricFlag
if "%FA_INIT_FABRIC%"=="1" goto :initExtraArguments
set "FA_INIT_FABRIC=1"
set "FA_INIT_FLAGS_STARTED=1"
exit /b 0

:initParseNeoForgeFlag
if "%FA_INIT_NEOFORGE%"=="1" goto :initExtraArguments
set "FA_INIT_NEOFORGE=1"
set "FA_INIT_FLAGS_STARTED=1"
exit /b 0

:initParseLegacyFlag
if "%FA_INIT_LEGACY%"=="1" goto :initExtraArguments
set "FA_INIT_LEGACY=1"
set "FA_INIT_FLAGS_STARTED=1"
exit /b 0

:initExtraArguments
echo ERROR: init received unexpected, duplicated or misplaced arguments.
echo        Use init ^<mod_id^> [new_folder] [mod^|api] [--mod-fabric] [--mod-neoforge] [--architectury] [--kotlin] [--splitEnvironmentSourceSets] [--legacy-schema1].
exit /b 2

:initArgumentsReady
if "%FA_INIT_FABRIC%"=="0" if "%FA_INIT_NEOFORGE%"=="0" set "FA_INIT_FABRIC=1"
if "%FA_INIT_LEGACY%"=="1" if "%FA_INIT_NEOFORGE%"=="1" (
    echo ERROR: --legacy-schema1 is only available for Fabric-only projects.
    exit /b 2
)
if "%FA_INIT_LEGACY%"=="1" set "FA_INIT_V11=0"
if "%FA_INIT_FABRIC%"=="1" if "%FA_INIT_NEOFORGE%"=="1" set "FA_INIT_MULTI=1"
if "%FA_INIT_NEOFORGE%"=="1" if "%FA_INIT_FABRIC%"=="0" if "%FA_INIT_SPLIT%"=="1" (
    echo ERROR: --splitEnvironmentSourceSets requires Fabric; NeoForge-only projects have no Loom client source set.
    exit /b 2
)
if "%FA_INIT_MULTI%"=="1" if "%FA_INIT_ARCHITECTURY%"=="1" if "%FA_INIT_SPLIT%"=="1" (
    set "FA_INIT_SPLIT=0"
    set "FA_INIT_SPLIT_IGNORED=1"
)
powershell -NoProfile -NonInteractive -Command "if($env:FA_INIT_ID -cmatch '^[a-z][a-z0-9_-]{1,63}$'){exit 0}else{exit 1}"
if errorlevel 1 (
    echo ERROR: Mod ID must start with lowercase a-z and be 2-64 chars [a-z0-9_-].
    exit /b 2
)
if "%FA_INIT_NEOFORGE%"=="1" (
    powershell -NoProfile -NonInteractive -Command "if($env:FA_INIT_ID -cmatch '^[a-z][a-z0-9_]{1,63}$'){exit 0}else{exit 1}"
    if errorlevel 1 (
        echo ERROR: NeoForge mod IDs must use lowercase a-z, 0-9 and underscore; hyphen is not allowed.
        exit /b 2
    )
)
set "FA_INIT_HERE=0"
if /i "%FA_INIT_ARG3%"=="--here" (
    set "FA_INIT_HERE=1"
    set "FA_TARGET=%FA_CONSUMER%"
) else (
    if /i "%FA_INIT_ARG4%"=="--here" goto :initInvalidHere
    if not "%FA_INIT_ARG3%"=="" (
        for %%P in ("%FA_INIT_ARG3%") do set "FA_TARGET=%%~fP"
    ) else (
        if /i "%CD%\"=="%FA_ROOT%" (
            set "FA_TARGET=%FA_ROOT%..\%FA_INIT_ID%"
        ) else (
            set "FA_TARGET=%CD%\%FA_INIT_ID%"
        )
    )
)
set "FA_PROFILE=%FA_INIT_ARG4%"
if not defined FA_PROFILE set "FA_PROFILE=mod"
if /i "%FA_PROFILE%"=="mod" (
    set "FA_PROFILE=mod"
    set "FA_PRODUCT=fabric/mod"
)
if /i "%FA_PROFILE%"=="api" (
    set "FA_PROFILE=api"
    set "FA_PRODUCT=fabric/api"
)
if not defined FA_PRODUCT (
    echo ERROR: Profile must be mod or api.
    exit /b 2
)
if "%FA_INIT_NEOFORGE%"=="1" if "%FA_INIT_MULTI%"=="0" set "FA_PRODUCT=neoforge/%FA_PROFILE%"
if "%FA_INIT_MULTI%"=="1" set "FA_PRODUCT=[fabric/%FA_PROFILE%, neoforge/%FA_PROFILE%]"
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
if exist "%FA_TARGET%\settings.gradle" goto :initConflict
if exist "%FA_TARGET%\settings.gradle.kts" goto :initConflict
if exist "%FA_TARGET%\build.gradle.kts" goto :initConflict
if exist "%FA_TARGET%\build.gradle" goto :initConflict
if exist "%FA_TARGET%\src" goto :initConflict
if "%FA_INIT_MULTI%"=="1" if exist "%FA_TARGET%\common" goto :initConflict
if "%FA_INIT_MULTI%"=="1" if exist "%FA_TARGET%\fabric" goto :initConflict
if "%FA_INIT_MULTI%"=="1" if exist "%FA_TARGET%\neoforge" goto :initConflict
if exist "%FA_TARGET%\schemas\mod.schema.json" goto :initConflict
goto :initPreflightDone

:initConflict
echo ERROR: Existing project files detected. init --here refuses to overwrite or merge them.
echo        Check mod.yaml, mod.yml, settings.gradle^(.kts^), build.gradle^(.kts^), src, common, fabric, neoforge and schemas.
exit /b 3

:initInvalidHere
echo ERROR: --here must immediately follow the mod ID, not a folder path.
exit /b 2

:initPreflightDone
rem Always use the producer's CURRENT schema for editor completion. Every
rem normal starter uses namespaced schema 1.1; an explicit --legacy-schema1
rem retains old Fabric-only fields for consumers that specifically request it.
where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo ERROR: Windows PowerShell is required to verify the init schema.
    exit /b 1
)
set "FA_HELPER_NAME=fabric-amper.schema.json"
set "FA_HELPER_DIR=%FA_ROOT%"
set "FA_HELPER_HASH=%FA_SCHEMA_SHA256%"
if not exist "%FA_ROOT%fabric-amper.schema.json" (
    set "FA_HELPER_SOURCE=main"
    call :fetchPinnedHelper
    if errorlevel 1 exit /b 1
)
call :verifyPinnedHelper
if errorlevel 1 exit /b 1
set "FA_HELPER_SOURCE=release"
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
if "%FA_INIT_KOTLIN%"=="1" (
    set "FA_INIT_SOURCE_LANG=kotlin"
    set "FA_INIT_SOURCE_EXT=kt"
) else (
    set "FA_INIT_SOURCE_LANG=java"
    set "FA_INIT_SOURCE_EXT=java"
)
if "%FA_INIT_MULTI%"=="1" goto :initMultiSourceDirectory
if "%FA_INIT_KOTLIN%"=="1" goto :initKotlinSourceDirectory
mkdir "%FA_TARGET%\src\main\java\org\example"
goto :initSourceDirectoryDone

:initKotlinSourceDirectory
mkdir "%FA_TARGET%\src\main\kotlin\org\example"

:initSourceDirectoryDone
if errorlevel 1 (
    echo ERROR: Cannot create target: "%FA_TARGET%"
    exit /b 1
)
if "%FA_INIT_SPLIT%"=="0" goto :initDirectoriesReady
mkdir "%FA_TARGET%\src\main\resources"
if errorlevel 1 exit /b 1
mkdir "%FA_TARGET%\src\client\resources"
if errorlevel 1 exit /b 1
if "%FA_INIT_KOTLIN%"=="1" goto :initKotlinClientDirectory
mkdir "%FA_TARGET%\src\client\java\org\example"
if errorlevel 1 exit /b 1
goto :initDirectoriesReady

:initKotlinClientDirectory
mkdir "%FA_TARGET%\src\client\kotlin\org\example"
if errorlevel 1 exit /b 1
goto :initDirectoriesReady

:initMultiSourceDirectory
if "%FA_INIT_ARCHITECTURY%"=="1" (
    mkdir "%FA_TARGET%\common\src\main\%FA_INIT_SOURCE_LANG%\org\example"
    if errorlevel 1 exit /b 1
    mkdir "%FA_TARGET%\common\src\main\resources"
    if errorlevel 1 exit /b 1
)
mkdir "%FA_TARGET%\fabric\src\main\%FA_INIT_SOURCE_LANG%\org\example"
if errorlevel 1 exit /b 1
mkdir "%FA_TARGET%\neoforge\src\main\%FA_INIT_SOURCE_LANG%\org\example"
if errorlevel 1 exit /b 1
mkdir "%FA_TARGET%\fabric\src\main\resources"
if errorlevel 1 exit /b 1
mkdir "%FA_TARGET%\neoforge\src\main\resources"
if errorlevel 1 exit /b 1
if "%FA_INIT_SPLIT%"=="1" (
    mkdir "%FA_TARGET%\fabric\src\client\%FA_INIT_SOURCE_LANG%\org\example"
    if errorlevel 1 exit /b 1
    mkdir "%FA_TARGET%\fabric\src\client\resources"
    if errorlevel 1 exit /b 1
)

:initDirectoriesReady
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
if "%FA_INIT_NEOFORGE%"=="1" >> "%FA_TARGET%\settings.gradle.kts" echo(        maven("https://maven.neoforged.net/releases/")
if "%FA_INIT_V11%"=="1" if "%FA_INIT_ARCHITECTURY%"=="1" >> "%FA_TARGET%\settings.gradle.kts" echo(        maven("https://maven.architectury.dev/")
>> "%FA_TARGET%\settings.gradle.kts" echo(        gradlePluginPortal()
>> "%FA_TARGET%\settings.gradle.kts" echo(        mavenCentral()
>> "%FA_TARGET%\settings.gradle.kts" echo(    }
>> "%FA_TARGET%\settings.gradle.kts" echo(    resolutionStrategy {
>> "%FA_TARGET%\settings.gradle.kts" echo(        eachPlugin {
>> "%FA_TARGET%\settings.gradle.kts" echo(            if ^(requested.id.id == "dev.fabric-amper.settings"^) {
>> "%FA_TARGET%\settings.gradle.kts" echo(                useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v%FA_VERSION%")
>> "%FA_TARGET%\settings.gradle.kts" echo(            }
>> "%FA_TARGET%\settings.gradle.kts" echo(        }
>> "%FA_TARGET%\settings.gradle.kts" echo(    }
>> "%FA_TARGET%\settings.gradle.kts" echo(}
>> "%FA_TARGET%\settings.gradle.kts" echo(plugins {
>> "%FA_TARGET%\settings.gradle.kts" echo(    id("dev.fabric-amper.settings") version "%FA_VERSION%"
>> "%FA_TARGET%\settings.gradle.kts" echo(}

if "%FA_INIT_V11%"=="1" goto :initWriteSchema11
rem New projects default to Java, with explicit --kotlin selecting a Kotlin entrypoint.
rem The plugin defaults omitted schema to 1.1. Keep this Fabric-only legacy
rem starter's existing metadata layout with an explicit schema: 1 instead.
> "%FA_TARGET%\mod.yaml" echo(# yaml-language-server: $schema=./schemas/mod.schema.json
>> "%FA_TARGET%\mod.yaml" echo(# $schema: ./schemas/mod.schema.json
>> "%FA_TARGET%\mod.yaml" echo(schema: 1
>> "%FA_TARGET%\mod.yaml" echo(product: %FA_PRODUCT%
>> "%FA_TARGET%\mod.yaml" echo(mod:
>> "%FA_TARGET%\mod.yaml" echo(  id: %FA_INIT_ID%
>> "%FA_TARGET%\mod.yaml" echo(  version: "1.0.0+26.3"
>> "%FA_TARGET%\mod.yaml" echo(  group: org.example
>> "%FA_TARGET%\mod.yaml" echo(  name: Example Mod
>> "%FA_TARGET%\mod.yaml" echo(  entrypoints:
>> "%FA_TARGET%\mod.yaml" echo(    main:
>> "%FA_TARGET%\mod.yaml" echo(      - org.example.ExampleMod
if "%FA_INIT_SPLIT%"=="1" (
    >> "%FA_TARGET%\mod.yaml" echo(    client:
    >> "%FA_TARGET%\mod.yaml" echo(      - org.example.ExampleModClient
)
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
if "%FA_INIT_KOTLIN%"=="1" goto :initEnableKotlin
>> "%FA_TARGET%\mod.yaml" echo(    enabled: false
goto :initKotlinSettingDone

:initEnableKotlin
>> "%FA_TARGET%\mod.yaml" echo(    enabled: true

:initKotlinSettingDone
if "%FA_INIT_SPLIT%"=="1" (
    >> "%FA_TARGET%\mod.yaml" echo(  loom:
    >> "%FA_TARGET%\mod.yaml" echo(    splitEnvironmentSourceSets: true
)
if "%FA_INIT_ARCHITECTURY%"=="1" (
    >> "%FA_TARGET%\mod.yaml" echo(architectury:
    >> "%FA_TARGET%\mod.yaml" echo(  enable: true
)
>> "%FA_TARGET%\mod.yaml" echo(repositories:
>> "%FA_TARGET%\mod.yaml" echo(  - fabric
>> "%FA_TARGET%\mod.yaml" echo(  - mavenCentral

if "%FA_INIT_KOTLIN%"=="1" goto :initWriteKotlinSource
if "%FA_INIT_ARCHITECTURY%"=="1" goto :initWriteJavaArchitecturySource

:initWriteJavaSource
> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(package org.example;
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(import net.fabricmc.api.ModInitializer;
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(public final class ExampleMod implements ModInitializer {
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(    @Override public void onInitialize() {
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(        System.out.println("Fabric Amper starter initialized");
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(    }
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(}
goto :initSourceWritten

:initWriteJavaArchitecturySource
> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(package org.example;
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(import net.fabricmc.api.ModInitializer;
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(import dev.architectury.platform.Platform;
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(// Common initializer: safe on both the client and dedicated server.
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(public final class ExampleMod implements ModInitializer {
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(    @Override public void onInitialize() {
if "%FA_INIT_SPLIT%"=="1" goto :initWriteJavaArchitecturyCommon
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(        System.out.println("Architectury single source set on Minecraft " + Platform.getMinecraftVersion());
goto :initWriteJavaArchitecturyDone

:initWriteJavaArchitecturyCommon
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(        System.out.println("Architectury common source set on Minecraft " + Platform.getMinecraftVersion());

:initWriteJavaArchitecturyDone
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(    }
>> "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" echo(}
goto :initSourceWritten

:initWriteKotlinSource
if "%FA_INIT_ARCHITECTURY%"=="1" goto :initWriteKotlinArchitecturySource
> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(package org.example
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(import net.fabricmc.api.ModInitializer
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(class ExampleMod : ModInitializer {
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(    override fun onInitialize() {
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(        println("Fabric Amper starter initialized")
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(    }
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(}
goto :initSourceWritten

:initWriteKotlinArchitecturySource
> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(package org.example
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(import net.fabricmc.api.ModInitializer
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(import dev.architectury.platform.Platform
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(// Common initializer: safe on both the client and dedicated server.
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(class ExampleMod : ModInitializer {
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(    override fun onInitialize() {
if "%FA_INIT_SPLIT%"=="1" goto :initWriteKotlinArchitecturyCommon
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(        println("Architectury single source set on Minecraft " + Platform.getMinecraftVersion())
goto :initWriteKotlinArchitecturyDone

:initWriteKotlinArchitecturyCommon
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(        println("Architectury common source set on Minecraft " + Platform.getMinecraftVersion())

:initWriteKotlinArchitecturyDone
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(    }
>> "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" echo(}

:initSourceWritten
if "%FA_INIT_SPLIT%"=="0" goto :initClientSourceWritten
if "%FA_INIT_KOTLIN%"=="1" goto :initWriteKotlinClientSource
if "%FA_INIT_ARCHITECTURY%"=="1" goto :initWriteJavaArchitecturyClientSource

> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(package org.example;
>> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(import net.fabricmc.api.ClientModInitializer;
>> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(public final class ExampleModClient implements ClientModInitializer {
>> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(    @Override public void onInitializeClient() {
>> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(        System.out.println("Fabric Amper client starter initialized");
>> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(    }
>> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(}
goto :initClientSourceWritten

:initWriteJavaArchitecturyClientSource
> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(package org.example;
>> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(import net.fabricmc.api.ClientModInitializer;
>> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(import dev.architectury.platform.Platform;
>> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(// This initializer runs only on the client and belongs in src/client.
>> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(public final class ExampleModClient implements ClientModInitializer {
>> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(    @Override public void onInitializeClient() {
>> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(        System.out.println("Architectury client source set on Minecraft " + Platform.getMinecraftVersion());
>> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(    }
>> "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" echo(}
goto :initClientSourceWritten

:initWriteKotlinClientSource
if "%FA_INIT_ARCHITECTURY%"=="1" goto :initWriteKotlinArchitecturyClientSource
> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(package org.example
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(import net.fabricmc.api.ClientModInitializer
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(class ExampleModClient : ClientModInitializer {
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(    override fun onInitializeClient() {
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(        println("Fabric Amper client starter initialized")
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(    }
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(}
goto :initClientSourceWritten

:initWriteKotlinArchitecturyClientSource
> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(package org.example
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(import net.fabricmc.api.ClientModInitializer
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(import dev.architectury.platform.Platform
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(// This initializer runs only on the client and belongs in src/client.
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(class ExampleModClient : ClientModInitializer {
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(    override fun onInitializeClient() {
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(        println("Architectury client source set on Minecraft " + Platform.getMinecraftVersion())
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(    }
>> "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" echo(}

:initClientSourceWritten

if not exist "%FA_TARGET%\settings.gradle.kts" exit /b 1
if not exist "%FA_TARGET%\mod.yaml" exit /b 1
if not exist "%FA_TARGET%\schemas\mod.schema.json" exit /b 1
if "%FA_INIT_KOTLIN%"=="1" goto :initVerifyKotlinSource
if not exist "%FA_TARGET%\src\main\java\org\example\ExampleMod.java" exit /b 1
goto :initSourceVerified

:initVerifyKotlinSource
if not exist "%FA_TARGET%\src\main\kotlin\org\example\ExampleMod.kt" exit /b 1

:initSourceVerified
if "%FA_INIT_SPLIT%"=="0" goto :initClientSourceVerified
if not exist "%FA_TARGET%\src\main\resources\" exit /b 1
if not exist "%FA_TARGET%\src\client\resources\" exit /b 1
if "%FA_INIT_KOTLIN%"=="1" goto :initVerifyKotlinClientSource
if not exist "%FA_TARGET%\src\client\java\org\example\ExampleModClient.java" exit /b 1
goto :initClientSourceVerified

:initVerifyKotlinClientSource
if not exist "%FA_TARGET%\src\client\kotlin\org\example\ExampleModClient.kt" exit /b 1

:initClientSourceVerified
echo [init] Created: "%FA_TARGET%"
echo [init] Profile: %FA_PRODUCT%
if "%FA_INIT_V11%"=="1" echo [init] Schema: 1.1 ^(namespaced Fabric configuration^).
if "%FA_INIT_LEGACY%"=="1" echo [init] Schema: 1 ^(explicit legacy compatibility^).
if "%FA_INIT_KOTLIN%"=="1" echo [init] Language: Kotlin ^(Fabric Language Kotlin automatically configured^).
if "%FA_INIT_KOTLIN%"=="0" echo [init] Language: Java.
if "%FA_INIT_SPLIT%"=="1" echo [init] Loom: splitEnvironmentSourceSets=true ^(common/client source sets and starter entrypoints^).
if "%FA_INIT_ARCHITECTURY%"=="1" echo [init] Architectury API: enabled ^(22.0.5 default, generated usage examples^).
if "%FA_INIT_ARCHITECTURY%"=="1" echo [init] Architectury example targets Fabric Amper v%FA_VERSION% prerelease.
if "%FA_INIT_HERE%"=="1" echo [init] Mode: --here; existing non-project tools preserved.
echo [init] YAML autocomplete: schemas\mod.schema.json ^(current producer schema snapshot^).
echo [init] Settings plugin: JitPack Fabric-Amper v%FA_VERSION% prerelease ^(Internet required on first resolution^).
echo [init] Next: cd to the new folder and run build.
exit /b 0

:initWriteSchema11
rem Schema 1.1 keeps shared identity/settings at the root and isolates Loader
rem configuration. The Settings plugin, not a consumer build script, owns the
rem Gradle :common/:fabric/:neoforge project wiring for dual-loader builds.
if "%FA_INIT_NEOFORGE%"=="1" if "%FA_INIT_MULTI%"=="0" (
    mkdir "%FA_TARGET%\src\main\resources"
    if errorlevel 1 exit /b 1
)
> "%FA_TARGET%\mod.yaml" echo(# yaml-language-server: $schema=./schemas/mod.schema.json
>> "%FA_TARGET%\mod.yaml" echo(# $schema: ./schemas/mod.schema.json
>> "%FA_TARGET%\mod.yaml" echo(schema: "1.1"
>> "%FA_TARGET%\mod.yaml" echo(product: %FA_PRODUCT%
>> "%FA_TARGET%\mod.yaml" echo(mod:
>> "%FA_TARGET%\mod.yaml" echo(  id: %FA_INIT_ID%
>> "%FA_TARGET%\mod.yaml" echo(  version: "1.0.0+26.3"
>> "%FA_TARGET%\mod.yaml" echo(  group: org.example
>> "%FA_TARGET%\mod.yaml" echo(  name: Example Mod
>> "%FA_TARGET%\mod.yaml" echo(  license: MIT
>> "%FA_TARGET%\mod.yaml" echo(minecraft:
>> "%FA_TARGET%\mod.yaml" echo(  version: "26.3"
>> "%FA_TARGET%\mod.yaml" echo(settings:
>> "%FA_TARGET%\mod.yaml" echo(  java:
>> "%FA_TARGET%\mod.yaml" echo(    release: 25
>> "%FA_TARGET%\mod.yaml" echo(    sourcesJar: true
>> "%FA_TARGET%\mod.yaml" echo(  kotlin:
if "%FA_INIT_KOTLIN%"=="1" goto :initV11KotlinEnabled
>> "%FA_TARGET%\mod.yaml" echo(    enabled: false
goto :initV11KotlinDone

:initV11KotlinEnabled
>> "%FA_TARGET%\mod.yaml" echo(    enabled: true

:initV11KotlinDone
if "%FA_INIT_ARCHITECTURY%"=="1" (
    >> "%FA_TARGET%\mod.yaml" echo(architectury:
    >> "%FA_TARGET%\mod.yaml" echo(  enable: true
    >> "%FA_TARGET%\mod.yaml" echo(  version: "22.0.5"
)
if "%FA_INIT_FABRIC%"=="1" goto :initV11FabricYaml
goto :initV11NeoForgeYaml

:initV11FabricYaml
>> "%FA_TARGET%\mod.yaml" echo(fabric:
>> "%FA_TARGET%\mod.yaml" echo(  loader: "0.20.1"
>> "%FA_TARGET%\mod.yaml" echo(  api: "0.165.2+26.3"
>> "%FA_TARGET%\mod.yaml" echo(  mappings: mojang
>> "%FA_TARGET%\mod.yaml" echo(  entrypoints:
>> "%FA_TARGET%\mod.yaml" echo(    main:
if "%FA_INIT_MULTI%"=="1" (
    >> "%FA_TARGET%\mod.yaml" echo(      - org.example.ExampleFabric
) else (
    >> "%FA_TARGET%\mod.yaml" echo(      - org.example.ExampleMod
)
if "%FA_INIT_SPLIT%"=="1" (
    >> "%FA_TARGET%\mod.yaml" echo(    client:
    if "%FA_INIT_MULTI%"=="1" (
        >> "%FA_TARGET%\mod.yaml" echo(      - org.example.ExampleFabricClient
    ) else (
        >> "%FA_TARGET%\mod.yaml" echo(      - org.example.ExampleModClient
    )
    >> "%FA_TARGET%\mod.yaml" echo(  loom:
    >> "%FA_TARGET%\mod.yaml" echo(    splitEnvironmentSourceSets: true
)

:initV11NeoForgeYaml
if "%FA_INIT_NEOFORGE%"=="1" (
    >> "%FA_TARGET%\mod.yaml" echo(neoforge:
    >> "%FA_TARGET%\mod.yaml" echo(  version: "26.3.0.101"
    >> "%FA_TARGET%\mod.yaml" echo(  modLoader: javafml
)
>> "%FA_TARGET%\mod.yaml" echo(repositories:
>> "%FA_TARGET%\mod.yaml" echo(  - mavenCentral
if "%FA_INIT_FABRIC%"=="1" >> "%FA_TARGET%\mod.yaml" echo(  - fabric
if "%FA_INIT_NEOFORGE%"=="1" >> "%FA_TARGET%\mod.yaml" echo(  - neoforge
>> "%FA_TARGET%\mod.yaml" echo(dependencies: []
if "%FA_INIT_MULTI%"=="0" if "%FA_INIT_FABRIC%"=="1" goto :initV11SingleFabricSource
if "%FA_INIT_MULTI%"=="1" if "%FA_INIT_ARCHITECTURY%"=="1" goto :initV11CommonSource
if "%FA_INIT_MULTI%"=="1" goto :initV11FabricSource
goto :initV11NeoForgeSource

:initV11SingleFabricSource
rem Reuse the proven Fabric-only Java/Kotlin/client source templates; schema
rem 1.1 namespaced entrypoint keys above still reference ExampleMod classes.
if "%FA_INIT_KOTLIN%"=="1" goto :initWriteKotlinSource
if "%FA_INIT_ARCHITECTURY%"=="1" goto :initWriteJavaArchitecturySource
goto :initWriteJavaSource

:initV11CommonSource
if "%FA_INIT_KOTLIN%"=="1" goto :initV11KotlinCommonSource
> "%FA_TARGET%\common\src\main\java\org\example\ExampleCommon.java" echo(package org.example;
if "%FA_INIT_ARCHITECTURY%"=="1" >> "%FA_TARGET%\common\src\main\java\org\example\ExampleCommon.java" echo(import dev.architectury.platform.Platform;
>> "%FA_TARGET%\common\src\main\java\org\example\ExampleCommon.java" echo(public final class ExampleCommon {
>> "%FA_TARGET%\common\src\main\java\org\example\ExampleCommon.java" echo(    private ExampleCommon() {}
>> "%FA_TARGET%\common\src\main\java\org\example\ExampleCommon.java" echo(    public static void initialize() {
if "%FA_INIT_ARCHITECTURY%"=="1" goto :initV11JavaCommonArch
>> "%FA_TARGET%\common\src\main\java\org\example\ExampleCommon.java" echo(        System.out.println("Shared loader-neutral mod logic initialized");
goto :initV11JavaCommonEnd

:initV11JavaCommonArch
>> "%FA_TARGET%\common\src\main\java\org\example\ExampleCommon.java" echo(        System.out.println("Architectury common on Minecraft " + Platform.getMinecraftVersion());

:initV11JavaCommonEnd
>> "%FA_TARGET%\common\src\main\java\org\example\ExampleCommon.java" echo(    }
>> "%FA_TARGET%\common\src\main\java\org\example\ExampleCommon.java" echo(}
goto :initV11FabricSource

:initV11KotlinCommonSource
> "%FA_TARGET%\common\src\main\kotlin\org\example\ExampleCommon.kt" echo(package org.example
if "%FA_INIT_ARCHITECTURY%"=="1" >> "%FA_TARGET%\common\src\main\kotlin\org\example\ExampleCommon.kt" echo(import dev.architectury.platform.Platform
>> "%FA_TARGET%\common\src\main\kotlin\org\example\ExampleCommon.kt" echo(object ExampleCommon {
>> "%FA_TARGET%\common\src\main\kotlin\org\example\ExampleCommon.kt" echo(    fun initialize() {
if "%FA_INIT_ARCHITECTURY%"=="1" goto :initV11KotlinCommonArch
>> "%FA_TARGET%\common\src\main\kotlin\org\example\ExampleCommon.kt" echo(        println("Shared loader-neutral mod logic initialized")
goto :initV11KotlinCommonEnd

:initV11KotlinCommonArch
>> "%FA_TARGET%\common\src\main\kotlin\org\example\ExampleCommon.kt" echo(        println("Architectury common on Minecraft " + Platform.getMinecraftVersion())

:initV11KotlinCommonEnd
>> "%FA_TARGET%\common\src\main\kotlin\org\example\ExampleCommon.kt" echo(    }
>> "%FA_TARGET%\common\src\main\kotlin\org\example\ExampleCommon.kt" echo(}

:initV11FabricSource
if "%FA_INIT_KOTLIN%"=="1" goto :initV11KotlinFabricSource
> "%FA_TARGET%\fabric\src\main\java\org\example\ExampleFabric.java" echo(package org.example;
>> "%FA_TARGET%\fabric\src\main\java\org\example\ExampleFabric.java" echo(import net.fabricmc.api.ModInitializer;
>> "%FA_TARGET%\fabric\src\main\java\org\example\ExampleFabric.java" echo(public final class ExampleFabric implements ModInitializer {
>> "%FA_TARGET%\fabric\src\main\java\org\example\ExampleFabric.java" echo(    @Override public void onInitialize() {
if "%FA_INIT_ARCHITECTURY%"=="0" goto :initV11JavaFabricIndependent
>> "%FA_TARGET%\fabric\src\main\java\org\example\ExampleFabric.java" echo(        ExampleCommon.initialize();
goto :initV11JavaFabricDone

:initV11JavaFabricIndependent
>> "%FA_TARGET%\fabric\src\main\java\org\example\ExampleFabric.java" echo(        System.out.println("Independent Fabric starter initialized");

:initV11JavaFabricDone
>> "%FA_TARGET%\fabric\src\main\java\org\example\ExampleFabric.java" echo(    }
>> "%FA_TARGET%\fabric\src\main\java\org\example\ExampleFabric.java" echo(}
goto :initV11FabricClientSource

:initV11KotlinFabricSource
> "%FA_TARGET%\fabric\src\main\kotlin\org\example\ExampleFabric.kt" echo(package org.example
>> "%FA_TARGET%\fabric\src\main\kotlin\org\example\ExampleFabric.kt" echo(import net.fabricmc.api.ModInitializer
>> "%FA_TARGET%\fabric\src\main\kotlin\org\example\ExampleFabric.kt" echo(class ExampleFabric : ModInitializer {
>> "%FA_TARGET%\fabric\src\main\kotlin\org\example\ExampleFabric.kt" echo(    override fun onInitialize() {
if "%FA_INIT_ARCHITECTURY%"=="0" goto :initV11KotlinFabricIndependent
>> "%FA_TARGET%\fabric\src\main\kotlin\org\example\ExampleFabric.kt" echo(        ExampleCommon.initialize()
goto :initV11KotlinFabricDone

:initV11KotlinFabricIndependent
>> "%FA_TARGET%\fabric\src\main\kotlin\org\example\ExampleFabric.kt" echo(        println("Independent Fabric starter initialized")

:initV11KotlinFabricDone
>> "%FA_TARGET%\fabric\src\main\kotlin\org\example\ExampleFabric.kt" echo(    }
>> "%FA_TARGET%\fabric\src\main\kotlin\org\example\ExampleFabric.kt" echo(}

:initV11FabricClientSource
if "%FA_INIT_SPLIT%"=="0" goto :initV11NeoForgeSource
if "%FA_INIT_KOTLIN%"=="1" goto :initV11KotlinFabricClientSource
> "%FA_TARGET%\fabric\src\client\java\org\example\ExampleFabricClient.java" echo(package org.example;
>> "%FA_TARGET%\fabric\src\client\java\org\example\ExampleFabricClient.java" echo(import net.fabricmc.api.ClientModInitializer;
>> "%FA_TARGET%\fabric\src\client\java\org\example\ExampleFabricClient.java" echo(public final class ExampleFabricClient implements ClientModInitializer {
>> "%FA_TARGET%\fabric\src\client\java\org\example\ExampleFabricClient.java" echo(    @Override public void onInitializeClient() {
>> "%FA_TARGET%\fabric\src\client\java\org\example\ExampleFabricClient.java" echo(        System.out.println("Fabric client initialized");
>> "%FA_TARGET%\fabric\src\client\java\org\example\ExampleFabricClient.java" echo(    }
>> "%FA_TARGET%\fabric\src\client\java\org\example\ExampleFabricClient.java" echo(}
goto :initV11NeoForgeSource

:initV11KotlinFabricClientSource
> "%FA_TARGET%\fabric\src\client\kotlin\org\example\ExampleFabricClient.kt" echo(package org.example
>> "%FA_TARGET%\fabric\src\client\kotlin\org\example\ExampleFabricClient.kt" echo(import net.fabricmc.api.ClientModInitializer
>> "%FA_TARGET%\fabric\src\client\kotlin\org\example\ExampleFabricClient.kt" echo(class ExampleFabricClient : ClientModInitializer {
>> "%FA_TARGET%\fabric\src\client\kotlin\org\example\ExampleFabricClient.kt" echo(    override fun onInitializeClient() {
>> "%FA_TARGET%\fabric\src\client\kotlin\org\example\ExampleFabricClient.kt" echo(        println("Fabric client initialized")
>> "%FA_TARGET%\fabric\src\client\kotlin\org\example\ExampleFabricClient.kt" echo(    }
>> "%FA_TARGET%\fabric\src\client\kotlin\org\example\ExampleFabricClient.kt" echo(}

:initV11NeoForgeSource
if "%FA_INIT_MULTI%"=="1" (set "FA_INIT_NEO_SRC=%FA_TARGET%\neoforge\src\main") else (set "FA_INIT_NEO_SRC=%FA_TARGET%\src\main")
if "%FA_INIT_KOTLIN%"=="1" goto :initV11KotlinNeoForgeSource
> "%FA_INIT_NEO_SRC%\java\org\example\ExampleNeoForge.java" echo(package org.example;
>> "%FA_INIT_NEO_SRC%\java\org\example\ExampleNeoForge.java" echo(import net.neoforged.bus.api.IEventBus;
>> "%FA_INIT_NEO_SRC%\java\org\example\ExampleNeoForge.java" echo(import net.neoforged.fml.ModContainer;
>> "%FA_INIT_NEO_SRC%\java\org\example\ExampleNeoForge.java" echo(import net.neoforged.fml.common.Mod;
if "%FA_INIT_MULTI%"=="0" if "%FA_INIT_ARCHITECTURY%"=="1" >> "%FA_INIT_NEO_SRC%\java\org\example\ExampleNeoForge.java" echo(import dev.architectury.platform.Platform;
rem Escape the closing parenthesis for cmd.exe; keep the literal @Mod("id") in Java.
>> "%FA_INIT_NEO_SRC%\java\org\example\ExampleNeoForge.java" echo(@Mod("%FA_INIT_ID%"^)
>> "%FA_INIT_NEO_SRC%\java\org\example\ExampleNeoForge.java" echo(public final class ExampleNeoForge {
>> "%FA_INIT_NEO_SRC%\java\org\example\ExampleNeoForge.java" echo(    public ExampleNeoForge(IEventBus modEventBus, ModContainer modContainer) {
if "%FA_INIT_MULTI%"=="1" goto :initV11NeoForgeJavaCommon
if "%FA_INIT_ARCHITECTURY%"=="1" goto :initV11NeoForgeJavaArchitectury
>> "%FA_INIT_NEO_SRC%\java\org\example\ExampleNeoForge.java" echo(        System.out.println("NeoForge starter initialized");
goto :initV11NeoForgeJavaEnd

:initV11NeoForgeJavaCommon
if "%FA_INIT_ARCHITECTURY%"=="0" goto :initV11NeoForgeJavaIndependent
>> "%FA_INIT_NEO_SRC%\java\org\example\ExampleNeoForge.java" echo(        ExampleCommon.initialize();
goto :initV11NeoForgeJavaEnd

:initV11NeoForgeJavaIndependent
>> "%FA_INIT_NEO_SRC%\java\org\example\ExampleNeoForge.java" echo(        System.out.println("Independent NeoForge starter initialized");
goto :initV11NeoForgeJavaEnd

:initV11NeoForgeJavaArchitectury
>> "%FA_INIT_NEO_SRC%\java\org\example\ExampleNeoForge.java" echo(        System.out.println("NeoForge Architectury on Minecraft " + Platform.getMinecraftVersion());

:initV11NeoForgeJavaEnd
>> "%FA_INIT_NEO_SRC%\java\org\example\ExampleNeoForge.java" echo(    }
>> "%FA_INIT_NEO_SRC%\java\org\example\ExampleNeoForge.java" echo(}
goto :initV11Verify

:initV11KotlinNeoForgeSource
> "%FA_INIT_NEO_SRC%\kotlin\org\example\ExampleNeoForge.kt" echo(package org.example
>> "%FA_INIT_NEO_SRC%\kotlin\org\example\ExampleNeoForge.kt" echo(import net.neoforged.bus.api.IEventBus
>> "%FA_INIT_NEO_SRC%\kotlin\org\example\ExampleNeoForge.kt" echo(import net.neoforged.fml.ModContainer
>> "%FA_INIT_NEO_SRC%\kotlin\org\example\ExampleNeoForge.kt" echo(import net.neoforged.fml.common.Mod
if "%FA_INIT_MULTI%"=="0" if "%FA_INIT_ARCHITECTURY%"=="1" >> "%FA_INIT_NEO_SRC%\kotlin\org\example\ExampleNeoForge.kt" echo(import dev.architectury.platform.Platform
rem Kotlin requires the same complete annotation; cmd.exe must not eat ')'.
>> "%FA_INIT_NEO_SRC%\kotlin\org\example\ExampleNeoForge.kt" echo(@Mod("%FA_INIT_ID%"^)
>> "%FA_INIT_NEO_SRC%\kotlin\org\example\ExampleNeoForge.kt" echo(class ExampleNeoForge(modEventBus: IEventBus, modContainer: ModContainer) {
>> "%FA_INIT_NEO_SRC%\kotlin\org\example\ExampleNeoForge.kt" echo(    init {
if "%FA_INIT_MULTI%"=="1" goto :initV11NeoForgeKotlinCommon
if "%FA_INIT_ARCHITECTURY%"=="1" goto :initV11NeoForgeKotlinArchitectury
>> "%FA_INIT_NEO_SRC%\kotlin\org\example\ExampleNeoForge.kt" echo(        println("NeoForge starter initialized")
goto :initV11NeoForgeKotlinEnd

:initV11NeoForgeKotlinCommon
if "%FA_INIT_ARCHITECTURY%"=="0" goto :initV11NeoForgeKotlinIndependent
>> "%FA_INIT_NEO_SRC%\kotlin\org\example\ExampleNeoForge.kt" echo(        ExampleCommon.initialize()
goto :initV11NeoForgeKotlinEnd

:initV11NeoForgeKotlinIndependent
>> "%FA_INIT_NEO_SRC%\kotlin\org\example\ExampleNeoForge.kt" echo(        println("Independent NeoForge starter initialized")
goto :initV11NeoForgeKotlinEnd

:initV11NeoForgeKotlinArchitectury
>> "%FA_INIT_NEO_SRC%\kotlin\org\example\ExampleNeoForge.kt" echo(        println("NeoForge Architectury on Minecraft " + Platform.getMinecraftVersion())

:initV11NeoForgeKotlinEnd
>> "%FA_INIT_NEO_SRC%\kotlin\org\example\ExampleNeoForge.kt" echo(    }
>> "%FA_INIT_NEO_SRC%\kotlin\org\example\ExampleNeoForge.kt" echo(}

:initV11Verify
if not exist "%FA_TARGET%\mod.yaml" exit /b 1
if not exist "%FA_TARGET%\settings.gradle.kts" exit /b 1
if not exist "%FA_TARGET%\schemas\mod.schema.json" exit /b 1
if not exist "%FA_INIT_NEO_SRC%\%FA_INIT_SOURCE_LANG%\org\example\ExampleNeoForge.%FA_INIT_SOURCE_EXT%" exit /b 1
if exist "%FA_TARGET%\build.gradle" exit /b 1
if exist "%FA_TARGET%\build.gradle.kts" exit /b 1
if exist "%FA_TARGET%\src\main\resources\fabric.mod.json" exit /b 1
if exist "%FA_TARGET%\src\main\resources\META-INF\neoforge.mods.toml" exit /b 1
if "%FA_INIT_MULTI%"=="1" (
    if "%FA_INIT_ARCHITECTURY%"=="1" if not exist "%FA_TARGET%\common\src\main\%FA_INIT_SOURCE_LANG%\org\example\ExampleCommon.%FA_INIT_SOURCE_EXT%" exit /b 1
    if not exist "%FA_TARGET%\fabric\src\main\%FA_INIT_SOURCE_LANG%\org\example\ExampleFabric.%FA_INIT_SOURCE_EXT%" exit /b 1
    if "%FA_INIT_SPLIT%"=="1" if not exist "%FA_TARGET%\fabric\src\client\%FA_INIT_SOURCE_LANG%\org\example\ExampleFabricClient.%FA_INIT_SOURCE_EXT%" exit /b 1
    if exist "%FA_TARGET%\fabric\src\main\resources\fabric.mod.json" exit /b 1
    if exist "%FA_TARGET%\neoforge\src\main\resources\META-INF\neoforge.mods.toml" exit /b 1
)
echo [init] Created: "%FA_TARGET%"
echo [init] Schema: 1.1; product: %FA_PRODUCT%
if "%FA_INIT_MULTI%"=="1" if "%FA_INIT_ARCHITECTURY%"=="1" echo [init] Modules: common, fabric, neoforge.
if "%FA_INIT_MULTI%"=="1" if "%FA_INIT_ARCHITECTURY%"=="0" echo [init] Modules: fabric and neoforge ^(independent^).
if "%FA_INIT_MULTI%"=="0" echo [init] Loader: NeoForge.
if "%FA_INIT_KOTLIN%"=="1" echo [init] Language: Kotlin.
if "%FA_INIT_KOTLIN%"=="0" echo [init] Language: Java.
if "%FA_INIT_SPLIT%"=="1" echo [init] Fabric splitEnvironmentSourceSets enabled.
if "%FA_INIT_SPLIT_IGNORED%"=="1" echo [init] Fabric split flag ignored for this loader combination.
if "%FA_INIT_ARCHITECTURY%"=="1" echo [init] Architectury API: enabled ^(22.0.5 default^).
if "%FA_INIT_HERE%"=="1" echo [init] Mode: --here; existing tools preserved.
echo [init] YAML autocomplete: schemas\mod.schema.json.
echo [init] Settings-only JitPack Fabric-Amper v%FA_VERSION% prerelease ^(schema 1.1, Internet required on first resolution^).
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
set "FA_CONSUMER_ACTION=validate"
goto :selectConsumerBuildTasks

:build
set "FA_CONSUMER_ACTION=build"
goto :selectConsumerBuildTasks

:verify
set "FA_CONSUMER_ACTION=verify"
goto :selectConsumerBuildTasks

:selectConsumerBuildTasks
rem Do not forward any caller-supplied arguments into Gradle. All task names
rem below are exact literals, selected solely by the checked consumer product.
if not "%~2"=="" goto :consumerBuildInvalidArgs
if not "%~3"=="" goto :consumerBuildInvalidArgs
call :checkConsumer
if errorlevel 1 exit /b 2
call :detectRunProduct
if errorlevel 3 (
    echo ERROR: Cannot safely determine loader targets from mod.yaml/mod.yml. No Gradle task started.
    exit /b 2
)
if errorlevel 2 goto :dualConsumerBuildTasks
goto :singleConsumerBuildTasks

:singleConsumerBuildTasks
set "FA_CONSUMER_TASKS="
if "%FA_CONSUMER_ACTION%"=="validate" set "FA_CONSUMER_TASKS=validateFabricAmper"
if "%FA_CONSUMER_ACTION%"=="verify" set "FA_CONSUMER_TASKS=verifyFabricAmperJar"
if "%FA_CONSUMER_ACTION%"=="build" set "FA_CONSUMER_TASKS=validateFabricAmper build verifyFabricAmperJar"
goto :invokeConsumerBuildTasks

:dualConsumerBuildTasks
set "FA_CONSUMER_TASKS="
if "%FA_CONSUMER_ACTION%"=="validate" set "FA_CONSUMER_TASKS=:fabric:validateFabricAmper :neoforge:validateFabricAmper"
if "%FA_CONSUMER_ACTION%"=="verify" set "FA_CONSUMER_TASKS=:fabric:verifyFabricAmperJar :neoforge:verifyFabricAmperJar"
if "%FA_CONSUMER_ACTION%"=="build" set "FA_CONSUMER_TASKS=:fabric:validateFabricAmper :neoforge:validateFabricAmper build :fabric:verifyFabricAmperJar :neoforge:verifyFabricAmperJar"
goto :invokeConsumerBuildTasks

:consumerBuildInvalidArgs
echo ERROR: validate/build/verify do not accept additional arguments or Gradle flags.
exit /b 2

:invokeConsumerBuildTasks
if not defined FA_CONSUMER_TASKS (
    echo ERROR: Internal unsupported consumer action. No Gradle task started.
    exit /b 2
)
echo [%FA_CONSUMER_ACTION%] Gradle tasks: %FA_CONSUMER_TASKS%
call :consumerGradle %FA_CONSUMER_TASKS%
exit /b %errorlevel%

:test
call :consumerGradle test
exit /b %errorlevel%

:clean
call :consumerGradle clean
exit /b %errorlevel%

:run
set "FA_RUN_SIDE=Client"
goto :selectRunLoader

:server
set "FA_RUN_SIDE=Server"
goto :selectRunLoader

:selectRunLoader
rem Only one of two literal loader flags is accepted. Never pass user-provided
rem arguments through to Gradle or treat an ambiguous dual product as default.
set "FA_RUN_LOADER="
set "FA_RUN_TASK="
if not "%~3"=="" goto :runInvalidArguments
if "%~2"=="" goto :runArgumentsValid
if /i "%~2"=="--mod-fabric" set "FA_RUN_LOADER=fabric"
if /i "%~2"=="--mod-neoforge" set "FA_RUN_LOADER=neoforge"
if not defined FA_RUN_LOADER goto :runInvalidArguments

:runArgumentsValid
call :checkConsumer
if errorlevel 1 exit /b 2
call :detectRunProduct
if errorlevel 3 (
    echo ERROR: Cannot safely determine the loader targets from mod.yaml/mod.yml. No game launched.
    exit /b 2
)
if errorlevel 2 goto :runDualLoader
if errorlevel 1 goto :runSingleNeoForge
goto :runSingleFabric

:runDualLoader
if not defined FA_RUN_LOADER (
    echo ERROR: Dual-loader project requires run/server --mod-fabric or --mod-neoforge.
    echo        No client or server was started.
    exit /b 2
)
if /i "%FA_RUN_LOADER%"=="fabric" set "FA_RUN_TASK=runFabric%FA_RUN_SIDE%"
if /i "%FA_RUN_LOADER%"=="neoforge" set "FA_RUN_TASK=runNeoForge%FA_RUN_SIDE%"
goto :invokeSelectedRun

:runSingleFabric
if defined FA_RUN_LOADER if /i not "%FA_RUN_LOADER%"=="fabric" goto :runLoaderMismatch
set "FA_RUN_TASK=run%FA_RUN_SIDE%"
goto :invokeSelectedRun

:runSingleNeoForge
if defined FA_RUN_LOADER if /i not "%FA_RUN_LOADER%"=="neoforge" goto :runLoaderMismatch
set "FA_RUN_TASK=run%FA_RUN_SIDE%"
goto :invokeSelectedRun

:runLoaderMismatch
echo ERROR: The requested loader is not selected by this consumer's product.
echo        No client or server was started.
exit /b 2

:runInvalidArguments
echo ERROR: run/server accepts at most one --mod-fabric or --mod-neoforge flag.
echo        Arbitrary Gradle options and additional arguments are not accepted.
exit /b 2

:invokeSelectedRun
if /i "%FA_RUN_SIDE%"=="Server" (
    echo [server] The user must read and accept Minecraft's EULA manually.
)
echo [run] Starting Gradle %FA_RUN_TASK% in consumer project: "%FA_CONSUMER%"
call :consumerGradle %FA_RUN_TASK%
exit /b %errorlevel%

:detectRunProduct
rem Exit codes: 0 Fabric-only; 1 NeoForge-only; 2 both; 3+ unsafe/unknown.
rem Bounded, read-only, UTF-8-strict inspection of the root YAML's schema and
rem product declarations, shared by run/server and validate/build/verify.
rem This is deliberately NOT a YAML evaluator: reject
rem unsupported or ambiguous syntax instead of guessing a Gradle run target.
set "FA_RUN_YAML=%FA_CONSUMER%\mod.yaml"
if not exist "%FA_RUN_YAML%" set "FA_RUN_YAML=%FA_CONSUMER%\mod.yml"
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; try { $p=$env:FA_RUN_YAML; $f=Get-Item -LiteralPath $p -Force; if(-not $f -or $f.PSIsContainer -or $f.Length -gt 1048576 -or (($f.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)){throw 'Unsafe or oversized mod YAML'}; $t=[IO.File]::ReadAllText($p,(New-Object System.Text.UTF8Encoding($false,$true))); $pr=[regex]::Matches($t,'(?m)^product[ \t]*:[ \t]*(.*)$'); if($pr.Count -ne 1){throw 'Expected exactly one plain root product key'}; $sr=[regex]::Matches($t,'(?m)^schema[ \t]*:[ \t]*(.*)$'); if($sr.Count -gt 1){throw 'Duplicate schema keys'}; $v=([string][char]34+'1.1'+[char]34); if($sr.Count -eq 1){$v=([regex]::Replace($sr[0].Groups[1].Value,'[ \t]+#.*$','')).Trim()}; $v11=([string][char]34+'1.1'+[char]34); $v11s=([string][char]39+'1.1'+[char]39); if($v -cne '1' -and $v -cne $v11 -and $v -cne $v11s){throw 'Unsupported or unquoted schema'}; $raw=([regex]::Replace($pr[0].Groups[1].Value,'[ \t]+#.*$','')).Trim(); $items=@(); $array=$false; if($raw.StartsWith('[') -and $raw.EndsWith(']')){$array=$true; $items=@($raw.Substring(1,$raw.Length-2) -split ',')} elseif($raw.Length -gt 0){$items=@($raw)} else {$array=$true; foreach($line in ($t.Substring($pr[0].Index+$pr[0].Length) -split '\r?\n')){if($line -match '^[ \t]*(?:#.*)?$'){continue}; if($line -cmatch '^[ \t]+-[ \t]+(.+)$'){$items+=([regex]::Replace($Matches[1],'[ \t]+#.*$','')).Trim(); continue}; if($line -cmatch '^[^ \t]'){break}; throw 'Unsupported block product syntax'}}; if($items.Count -lt 1 -or $items.Count -gt 2){throw 'Expected one or two loader products'}; $seen=@{}; foreach($item in $items){$item=$item.Trim(); if($item.Length -ge 2 -and (($item[0] -eq [char]34 -and $item[$item.Length-1] -eq [char]34) -or ($item[0] -eq [char]39 -and $item[$item.Length-1] -eq [char]39))){$item=$item.Substring(1,$item.Length-2)}; if($item -cnotmatch '^(fabric/(mod|api|library)|neoforge/(mod|api))$'){throw 'Unsupported loader product'}; $loader=$item.Split('/')[0]; if($seen.ContainsKey($loader)){throw 'Repeated loader target'}; $seen[$loader]=$true}; if(($array -or $seen.ContainsKey('neoforge')) -and $v -cne $v11 -and $v -cne $v11s){throw 'Schema 1.1 required'}; if($seen.Count -eq 2){exit 2}; if($seen.ContainsKey('neoforge')){exit 1}; exit 0 } catch { [Console]::Error.WriteLine('[ERROR] run/server product inspection: '+$_.Exception.Message); exit 3 }"
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
