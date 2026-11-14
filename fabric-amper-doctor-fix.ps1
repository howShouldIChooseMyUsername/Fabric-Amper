# Fabric Amper --fix is an explicit, opt-in Windows repair operation.
# v0.1.2: The BAT wrapper pins this file's SHA-256 and fetches this one asset
# from its matching GitHub Release into the BAT directory if it is missing.
# The batch wrapper supplies FA_ROOT, FA_CONSUMER and FA_VERSION through
# environment variables so paths never need interpolation into PowerShell.
param(
    [switch] $Preview,
    [switch] $AssumeMissing
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module Microsoft.PowerShell.Utility -ErrorAction Stop

if ($AssumeMissing -and -not $Preview) {
    throw '-AssumeMissing is only available in read-only preview mode.'
}

$producer = [IO.Path]::GetFullPath($env:FA_ROOT)
$consumer = [IO.Path]::GetFullPath($env:FA_CONSUMER)
$pluginVersion = $env:FA_VERSION
if ($pluginVersion -notmatch '^\d+\.\d+\.\d+$') {
    throw 'Invalid Fabric Amper release version.'
}

function Say([string] $message) {
    # Output must not accidentally become part of a function's Boolean return.
    [Console]::Out.WriteLine($message)
}

function Refresh-ProcessPath {
    # WinGet can change user/machine PATH without changing this process.
    # Preserve any temporary PATH entries inherited from the caller.
    $entries = [Collections.Generic.List[string]]::new()
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($value in @(
        [Environment]::GetEnvironmentVariable('Path', 'Process'),
        [Environment]::GetEnvironmentVariable('Path', 'User'),
        [Environment]::GetEnvironmentVariable('Path', 'Machine')
    )) {
        if ([string]::IsNullOrEmpty($value)) { continue }
        foreach ($entry in $value.Split(';')) {
            $part = $entry.Trim().Trim('"')
            if ($part -and $seen.Add($part)) { $entries.Add($part) }
        }
    }
    [Environment]::SetEnvironmentVariable('Path', ($entries -join ';'), 'Process')
}

function Add-UserPath([string] $entry) {
    $existing = [Environment]::GetEnvironmentVariable('Path', 'User')
    $parts = @($existing -split ';' | Where-Object { $_ })
    if ($parts -notcontains $entry) {
        $newPath = (@($parts) + $entry) -join ';'
        if ($newPath.Length -gt 20000) { throw 'User PATH is too long to append a toolchain safely.' }
        [Environment]::SetEnvironmentVariable('Path', $newPath, 'User')
    }
    Refresh-ProcessPath
}

function Find-Jdk25 {
    $candidates = [Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
    foreach ($cmd in @(Get-Command javac.exe -All -ErrorAction SilentlyContinue)) {
        if ($cmd.Source) {
            $candidates.Add((Split-Path -Parent (Split-Path -Parent $cmd.Source)))
        }
    }
    foreach ($base in @(
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
        (Join-Path $env:ProgramFiles 'Java')
    )) {
        if (-not (Test-Path -LiteralPath $base -PathType Container)) { continue }
        foreach ($dir in @(Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue)) {
            if ($dir.Name -match '^jdk-?25(?:[.\-+_]|$)') { $candidates.Add($dir.FullName) }
        }
    }
    foreach ($candidate in @($candidates | Select-Object -Unique)) {
        $javac = Join-Path $candidate 'bin\javac.exe'
        $java = Join-Path $candidate 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or
            -not (Test-Path -LiteralPath $javac -PathType Leaf)) { continue }
        $version = & $javac -version 2>&1 | Out-String
        if ($LASTEXITCODE -eq 0 -and $version -match '(?m)^javac 25(?:\.|\s|$)') {
            return [IO.Path]::GetFullPath($candidate)
        }
    }
    return $null
}

function Find-Gradle {
    $candidates = [Collections.Generic.List[string]]::new()
    if ($env:FABRIC_AMPER_GRADLE) { $candidates.Add($env:FABRIC_AMPER_GRADLE) }
    if ($env:GRADLE_HOME) { $candidates.Add((Join-Path $env:GRADLE_HOME 'bin\gradle.bat')) }
    $local = Join-Path $env:LOCALAPPDATA 'FabricAmper\tools\gradle-9.7.1\bin\gradle.bat'
    $candidates.Add($local)
    foreach ($cmd in @(Get-Command gradle.bat -All -ErrorAction SilentlyContinue)) {
        if ($cmd.Source) { $candidates.Add($cmd.Source) }
    }
    $wrapperCache = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-9.7.1-bin'
    if (Test-Path -LiteralPath $wrapperCache -PathType Container) {
        foreach ($item in @(Get-ChildItem -LiteralPath $wrapperCache -Directory -ErrorAction SilentlyContinue)) {
            $candidates.Add((Join-Path $item.FullName 'gradle-9.7.1\bin\gradle.bat'))
        }
    }
    foreach ($candidate in @($candidates | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        # A direct launcher is safe to inspect; never execute gradlew.bat here
        # because an uncached Wrapper can download an unexpected distribution.
        $output = & $candidate --version 2>&1 | Out-String
        if ($LASTEXITCODE -eq 0 -and $output -match '(?m)^Gradle\s+9\.(?:7|8|9)\.\d+') {
            return [IO.Path]::GetFullPath($candidate)
        }
    }
    return $null
}

function Install-Jdk25 {
    if (-not (Get-Command winget.exe -ErrorAction SilentlyContinue)) {
        Say '[ERROR] winget is unavailable; install Eclipse Temurin JDK 25 manually.'
        return $false
    }
    Say '[fix] Installing Eclipse Temurin JDK 25 using winget.'
    & winget.exe install --id EclipseAdoptium.Temurin.25.JDK -e --accept-source-agreements --accept-package-agreements --disable-interactivity
    if ($LASTEXITCODE -ne 0) {
        Say ('[ERROR] JDK 25 winget installer exited with code ' + $LASTEXITCODE + '.')
        return $false
    }
    Refresh-ProcessPath
    return $true
}

function Install-Gradle {
    # The winget package may be unavailable on some Windows/winget sources.
    if (Get-Command winget.exe -ErrorAction SilentlyContinue) {
        Say '[fix] Trying Gradle.Gradle from winget.'
        & winget.exe install --id Gradle.Gradle -e --accept-source-agreements --accept-package-agreements --disable-interactivity
        if ($LASTEXITCODE -eq 0) {
            Refresh-ProcessPath
            if (Find-Gradle) { return $true }
        } else {
            Say '[INFO] Gradle.Gradle is not available from this winget source; using the official archive.'
        }
    }

    $tools = Join-Path $env:LOCALAPPDATA 'FabricAmper\tools'
    $destination = Join-Path $tools 'gradle-9.7.1'
    if (Test-Path -LiteralPath $destination) {
        Say '[ERROR] Existing Gradle destination is not usable. Refusing to overwrite it.'
        return $false
    }
    $temp = Join-Path $tools ('.gradle-9.7.1-install-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $temp -Force | Out-Null
    $zipFile = Join-Path $temp 'gradle-9.7.1-bin.zip'
    $hashFile = Join-Path $temp 'gradle-9.7.1-bin.zip.sha256'
    try {
        $url = 'https://services.gradle.org/distributions/gradle-9.7.1-bin.zip'
        Say '[fix] Downloading Gradle 9.7.1 only from the official HTTPS distribution service.'
        Invoke-WebRequest -Uri $url -OutFile $zipFile -MaximumRedirection 8 -TimeoutSec 300 -UseBasicParsing
        Invoke-WebRequest -Uri ($url + '.sha256') -OutFile $hashFile -MaximumRedirection 8 -TimeoutSec 60 -UseBasicParsing
        $expected = (Get-Content -LiteralPath $hashFile -Raw).Trim()
        if ($expected -notmatch '^[a-fA-F0-9]{64}$') { throw 'Official Gradle SHA-256 response was malformed.' }
        # Stream the official distribution instead of loading the entire ZIP
        # or depending on Get-FileHash cmdlet discovery in child PowerShell.
        $stream = [IO.File]::OpenRead($zipFile)
        try {
            $actual = [BitConverter]::ToString(([Security.Cryptography.SHA256]::Create()).ComputeHash($stream)).Replace('-', '')
        } finally {
            $stream.Dispose()
        }
        if ($actual -ine $expected) { throw 'Gradle archive SHA-256 did not match the official checksum.' }
        Say '[fix] Official Gradle SHA-256 verified.'
        $unpacked = Join-Path $temp 'expanded'
        Expand-Archive -LiteralPath $zipFile -DestinationPath $unpacked -ErrorAction Stop
        $folder = Join-Path $unpacked 'gradle-9.7.1'
        if (-not (Test-Path -LiteralPath (Join-Path $folder 'bin\gradle.bat') -PathType Leaf)) {
            throw 'Official archive is missing the expected Gradle launcher.'
        }
        Move-Item -LiteralPath $folder -Destination $destination -ErrorAction Stop
        Add-UserPath (Join-Path $destination 'bin')
        Say ('[fix] Gradle 9.7.1 installed at ' + $destination)
        return $true
    } catch {
        Say ('[ERROR] Portable Gradle installation failed: ' + $_.Exception.Message)
        return $false
    } finally {
        # Only delete the unique temporary directory created by this invocation.
        Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Repair-ConsumerFiles {
    if (-not (Test-Path -LiteralPath $consumer -PathType Container)) {
        Say '[INFO] Consumer directory does not exist; no project files repaired.'
        return
    }
    $yaml = Join-Path $consumer 'mod.yaml'
    $yml = Join-Path $consumer 'mod.yml'
    $hasYaml = Test-Path -LiteralPath $yaml -PathType Leaf
    $hasYml = Test-Path -LiteralPath $yml -PathType Leaf
    if ($hasYaml -and $hasYml) {
        Say '[WARN] Both mod.yaml and mod.yml exist; refusing to modify ambiguous project.'
        return
    }
    if (-not $hasYaml -and -not $hasYml) {
        Say '[INFO] No mod.yaml/mod.yml here; use init <mod_id> --here to create a project.'
        return
    }
    if ((Test-Path -LiteralPath (Join-Path $consumer 'build.gradle')) -or
        (Test-Path -LiteralPath (Join-Path $consumer 'build.gradle.kts'))) {
        Say '[WARN] Existing build.gradle(.kts) detected; refusing to repair a conflicting consumer.'
        return
    }
    $settings = Join-Path $consumer 'settings.gradle.kts'
    if (-not (Test-Path -LiteralPath $settings)) {
        if ($Preview) {
            Say '[preview] Would create missing settings.gradle.kts with JitPack Settings plugin bootstrap.'
        } else {
            $source = @"
pluginManagement {
    repositories {
        maven("https://jitpack.io")
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "dev.fabric-amper.settings") {
                useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v$pluginVersion")
            }
        }
    }
}
plugins {
    id("dev.fabric-amper.settings") version "$pluginVersion"
}
"@
            [IO.File]::WriteAllText($settings, $source + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
            Say '[fix] Created missing settings.gradle.kts without changing mod.yaml.'
        }
    }
    $schema = Join-Path $consumer 'schemas\mod.schema.json'
    if (-not (Test-Path -LiteralPath $schema)) {
        $producerSchema = Join-Path $producer 'fabric-amper.schema.json'
        if (-not (Test-Path -LiteralPath $producerSchema -PathType Leaf)) {
            Say '[WARN] Producer schema is unavailable; cannot repair local autocomplete schema.'
        } elseif ($Preview) {
            Say '[preview] Would copy missing schemas/mod.schema.json.'
        } else {
            New-Item -ItemType Directory -Path (Split-Path -Parent $schema) -Force | Out-Null
            Copy-Item -LiteralPath $producerSchema -Destination $schema
            Say '[fix] Copied missing schemas/mod.schema.json.'
        }
    }
    $vscodeFile = Join-Path $consumer '.vscode\settings.json'
    if (-not (Test-Path -LiteralPath $vscodeFile)) {
        if ($Preview) {
            Say '[preview] Would create missing .vscode/settings.json for YAML schema mapping.'
        } else {
            New-Item -ItemType Directory -Path (Split-Path -Parent $vscodeFile) -Force | Out-Null
            $mapping = '{"yaml.schemas":{"./schemas/mod.schema.json":["mod.yaml","mod.yml"]}}'
            [IO.File]::WriteAllText($vscodeFile, $mapping + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
            Say '[fix] Created missing .vscode/settings.json.'
        }
    }
}

$hadError = $false
try {
    Say ('[doctor --fix] Mode: ' + $(if ($Preview) { 'READ-ONLY PREVIEW' } else { 'REPAIR' }))
    $jdk = if ($AssumeMissing) { $null } else { Find-Jdk25 }
    if ($jdk) {
        Say '[OK] A compatible JDK 25 already exists; no reinstall needed.'
    } elseif ($Preview) {
        Say '[preview] Would try winget EclipseAdoptium.Temurin.25.JDK and refresh PATH/JAVA_HOME.'
    } else {
        if (-not (Install-Jdk25)) { $hadError = $true }
        $jdk = Find-Jdk25
        if (-not $jdk) {
            Say '[ERROR] JDK 25 is still not discoverable after the attempted installation.'
            $hadError = $true
        }
    }
    if ($jdk -and -not $Preview) {
        $env:JAVA_HOME = $jdk
        [Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk, 'User')
        Add-UserPath (Join-Path $jdk 'bin')
        Say '[OK] Selected JDK 25 for this repair process and saved user JAVA_HOME/PATH.'
    }

    $gradle = if ($AssumeMissing) { $null } else { Find-Gradle }
    if ($gradle) {
        Say '[OK] Compatible Gradle launcher found; no reinstall needed.'
    } elseif ($Preview) {
        Say '[preview] Would try winget Gradle.Gradle, then the official Gradle 9.7.1 ZIP with SHA-256 verification.'
        Say '[preview] Portable destination: LOCALAPPDATA/FabricAmper/tools/gradle-9.7.1.'
    } else {
        if (-not (Install-Gradle)) { $hadError = $true }
        $gradle = Find-Gradle
        if (-not $gradle) {
            Say '[ERROR] Compatible Gradle launcher is still unavailable.'
            $hadError = $true
        }
    }
    Repair-ConsumerFiles
    if ($Preview) {
        Say '[preview] No installations, downloads, file writes, cache changes or registry changes were performed.'
    } else {
        Say '[INFO] Toolchains are refreshed in the repair process and user environment.'
        Say '[INFO] A child batch script cannot update the parent PowerShell/CMD process; open a new terminal if it still uses the old PATH.'
    }
} catch {
    Say ('[ERROR] doctor --fix: ' + $_.Exception.Message)
    $hadError = $true
}
if ($hadError) { exit 1 }
exit 0
