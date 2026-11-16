# Fabric Amper v0.2.0 migration helper. Called by fabric-amper.bat with
# FA_CONSUMER, FA_ROOT and FA_VERSION. PowerShell 5.1 and 7 compatible.
# This is a static converter: source Gradle scripts are never evaluated.
[CmdletBinding()]
param([switch]$Preview, [switch]$Delete)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

function Fail([string]$message) { throw "[migrate] $message" }
function Has([System.Collections.IDictionary]$map, [string]$key) {
    return $null -ne $map -and $map.Contains($key)
}
function Nonblank($value, [string]$name) {
    if ($value -isnot [string] -or [string]::IsNullOrWhiteSpace($value)) { Fail "$name must be a nonempty string." }
    return [string]$value
}
function Safe-Name([string]$value, [string]$name, [string]$pattern) {
    if ($value -cnotmatch $pattern) { Fail "Invalid $name '$value'; edit the source manually." }
    return $value
}
function Convert-JsonTree($value) {
    if ($null -eq $value) { return $null }
    if ($value -is [System.Collections.IDictionary]) {
        $result = [ordered]@{}
        foreach ($k in $value.Keys) {
            if ($k -isnot [string] -or $result.Contains($k)) { Fail 'Invalid or duplicate JSON object key.' }
            $result[$k] = Convert-JsonTree $value[$k]
        }
        return ,$result
    }
    if ($value -is [pscustomobject]) {
        $result = [ordered]@{}
        foreach ($p in $value.PSObject.Properties) {
            if ($result.Contains($p.Name)) { Fail "Duplicate JSON object key '$($p.Name)'." }
            $result[$p.Name] = Convert-JsonTree $p.Value
        }
        return ,$result
    }
    if ($value -is [array]) {
        $result = [System.Collections.ArrayList]::new()
        foreach ($item in $value) { [void]$result.Add((Convert-JsonTree $item)) }
        return ,$result.ToArray()
    }
    if ($value -is [string] -or $value -is [bool] -or $value -is [int] -or
        $value -is [long] -or $value -is [decimal] -or $value -is [double]) { return $value }
    Fail "Unsupported JSON value type '$($value.GetType().FullName)'."
}
function Yaml-String([string]$value) {
    $s = [System.Text.StringBuilder]::new()
    [void]$s.Append('"')
    foreach ($c in $value.ToCharArray()) {
        switch ([int]$c) {
            34 { [void]$s.Append('\"'); break }
            92 { [void]$s.Append('\\'); break }
            10 { [void]$s.Append('\n'); break }
            13 { [void]$s.Append('\r'); break }
            9  { [void]$s.Append('\t'); break }
            default {
                if ([int]$c -lt 32 -or [int]$c -eq 127 -or [int]$c -in @(0x85,0x2028,0x2029)) {
                    [void]$s.Append(('\u{0:x4}' -f [int]$c))
                } else { [void]$s.Append($c) }
            }
        }
    }
    [void]$s.Append('"')
    return $s.ToString()
}
function Yaml-Flow($value, [int]$depth = 0) {
    if ($depth -gt 48) { Fail 'Metadata nesting is too deep for safe YAML conversion.' }
    if ($null -eq $value) { return 'null' }
    if ($value -is [string]) { return (Yaml-String $value) }
    if ($value -is [bool]) { return $value.ToString().ToLowerInvariant() }
    if ($value -is [System.Collections.IDictionary]) {
        $items = [System.Collections.Generic.List[string]]::new()
        foreach ($key in $value.Keys) {
            [void]$items.Add((Yaml-String ([string]$key)) + ': ' + (Yaml-Flow $value[$key] ($depth + 1)))
        }
        return '{' + ($items -join ', ') + '}'
    }
    if ($value -is [array]) {
        $items = [System.Collections.Generic.List[string]]::new()
        foreach ($item in $value) { [void]$items.Add((Yaml-Flow $item ($depth + 1))) }
        return '[' + ($items -join ', ') + ']'
    }
    if ($value -is [int] -or $value -is [long] -or $value -is [decimal] -or $value -is [double]) {
        return [System.Convert]::ToString($value, [Globalization.CultureInfo]::InvariantCulture)
    }
    Fail 'Unsupported value in YAML output.'
}
function Assert-TreeValue($value, [string]$name) {
    if ($value -isnot [System.Collections.IDictionary]) { Fail "$name must be an object." }
}
function Assert-Keys([System.Collections.IDictionary]$value, [string[]]$allowed, [string]$name) {
    foreach ($k in $value.Keys) {
        if ($k -cnotin $allowed) { Fail "Unknown $name field '$k'; manual migration is required to avoid data loss." }
    }
}
function Assert-StringMap($value, [string]$name, [bool]$allowArray = $false) {
    Assert-TreeValue $value $name
    foreach ($key in $value.Keys) {
        [void](Nonblank $key "$name key")
        $v = $value[$key]
        if ($allowArray -and $v -is [array]) {
            if ($v.Count -eq 0) { Fail "$name.$key must not be empty." }
            foreach ($x in $v) { [void](Nonblank $x "$name.$key entry") }
        } else { [void](Nonblank $v "$name.$key") }
    }
}
function Assert-StringArray($value, [string]$name, [bool]$allowObjects = $false) {
    if ($value -isnot [array]) { Fail "$name must be an array." }
    foreach ($item in $value) {
        if ($item -is [string]) { [void](Nonblank $item "$name entry") }
        elseif ($allowObjects -and $item -is [System.Collections.IDictionary]) {
            Assert-Keys $item @('name','contact') "$name entry"
            if (-not (Has $item 'name')) { Fail "$name object requires name." }
            [void](Nonblank $item['name'] "$name.name")
            if (Has $item 'contact') { Assert-StringMap $item['contact'] "$name.contact" }
        } else { Fail "$name contains an unsupported entry." }
    }
}

# Reject junctions and symlinks at each existing component before reading or writing.
function Assert-SafePath([string]$root, [string]$relative) {
    $full = [IO.Path]::GetFullPath((Join-Path $root $relative))
    $rootPrefix = $root.TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        Fail "Path escapes consumer project: $relative"
    }
    $cursor = $root
    foreach ($part in ($relative -split '[/\\]')) {
        if ($part -eq '' -or $part -eq '.' -or $part -eq '..') { Fail "Unsafe path segment in '$relative'." }
        $cursor = Join-Path $cursor $part
        if (Test-Path -LiteralPath $cursor) {
            $entry = Get-Item -LiteralPath $cursor -Force
            if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                Fail "Symlink/junction is not allowed in migration input/output path: $relative"
            }
        }
    }
    return $full
}
function Relative-Resource([string]$path, [string]$name) {
    [void](Safe-Name (Nonblank $path $name) $name '^(?!\.)(?!.*(?:^|/)\.\.?(/|$))(?!.*//)(?!.*[\\:])[^/]+(?:/[^/]+)*$')
    return $path
}
function Get-SourceMetadata([string]$root) {
    $src = Join-Path $root 'src'
    if (-not (Test-Path -LiteralPath $src -PathType Container)) { Fail 'No src directory exists.' }
    $found = [System.Collections.Generic.List[string]]::new()
    $dirs = [System.Collections.Generic.Stack[string]]::new()
    $dirs.Push('src')
    while ($dirs.Count -gt 0) {
        $dir = $dirs.Pop()
        $path = Assert-SafePath $root $dir
        foreach ($item in (Get-ChildItem -LiteralPath $path -Force)) {
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                Fail "Symlink/junction inside source tree: $($item.FullName)"
            }
            $rel = $dir + '/' + $item.Name
            if ($item.PSIsContainer) { $dirs.Push($rel) }
            elseif ($item.Name -ieq 'fabric.mod.json') { [void]$found.Add($rel.Replace('\','/')) }
        }
    }
    if ($found.Count -ne 1) { Fail "Expected exactly one src/**/fabric.mod.json, found $($found.Count)." }
    if ($found[0] -cnotin @('src/main/resources/fabric.mod.json', 'src/client/resources/fabric.mod.json')) {
        Fail "Custom metadata location '$($found[0])' requires a manual source-set migration."
    }
    return $found[0]
}
# ConvertFrom-Json on Windows PowerShell may collapse repeated JSON keys.
# Detect them in the original text before they can silently overwrite fields.
function Assert-UniqueJsonKeys([string]$raw) {
    $stack = [System.Collections.ArrayList]::new()
    for ($i = 0; $i -lt $raw.Length; $i++) {
        $c = $raw[$i]
        if ($c -eq '"') {
            $start = $i; $i++
            $closed = $false
            while ($i -lt $raw.Length) {
                if ($raw[$i] -eq '\') { $i += 2; continue }
                if ($raw[$i] -eq '"') { $closed = $true; break }
                $i++
            }
            if (-not $closed) { Fail 'Unterminated JSON string.' }
            if ($stack.Count -gt 0) {
                $frame = $stack[$stack.Count - 1]
                if ($frame.Object -and $frame.ExpectKey) {
                    $end = $i + 1
                    $tail = $end
                    while ($tail -lt $raw.Length -and [char]::IsWhiteSpace($raw[$tail])) { $tail++ }
                    if ($tail -lt $raw.Length -and $raw[$tail] -eq ':') {
                        try { $key = ConvertFrom-Json -InputObject $raw.Substring($start, $end - $start) }
                        catch { Fail 'Invalid JSON object property escape.' }
                        if (-not $frame.Keys.Add([string]$key)) { Fail "Duplicate JSON key '$key' (case or escape equivalent)." }
                        $frame.ExpectKey = $false
                    }
                }
            }
            continue
        }
        if ($c -eq '{') {
            [void]$stack.Add([pscustomobject]@{
                Object=$true; ExpectKey=$true
                Keys=[System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
            })
        } elseif ($c -eq '[') {
            [void]$stack.Add([pscustomobject]@{Object=$false;ExpectKey=$false;Keys=$null})
        } elseif ($c -eq '}' -or $c -eq ']') {
            if ($stack.Count -eq 0) { Fail 'Malformed JSON nesting.' }
            $stack.RemoveAt($stack.Count - 1)
        } elseif ($c -eq ',' -and $stack.Count -gt 0) {
            $frame = $stack[$stack.Count - 1]
            if ($frame.Object) { $frame.ExpectKey = $true }
        }
    }
}
function Read-Metadata([string]$path) {
    try { $raw = [IO.File]::ReadAllText($path, [Text.UTF8Encoding]::new($false, $true)) }
    catch { Fail "Cannot decode fabric.mod.json as UTF-8: $($_.Exception.Message)" }
    if ($raw.Length -gt 4MB) { Fail 'fabric.mod.json exceeds the conservative 4 MiB migration limit.' }
    Assert-UniqueJsonKeys $raw
    try { $obj = Convert-JsonTree (ConvertFrom-Json -InputObject $raw -ErrorAction Stop) }
    catch { Fail "Invalid fabric.mod.json: $($_.Exception.Message)" }
    Assert-TreeValue $obj 'fabric.mod.json'
    Assert-Keys $obj @('schemaVersion','id','version','name','description','environment','license',
        'authors','contributors','contact','icon','provides','entrypoints','languageAdapters',
        'mixins','accessWidener','depends','recommends','suggests','conflicts','breaks','custom') 'Loader metadata'
    if (-not (Has $obj 'schemaVersion') -or
        ($obj['schemaVersion'] -isnot [int] -and $obj['schemaVersion'] -isnot [long]) -or
        $obj['schemaVersion'] -ne 1) {
        Fail 'Only Fabric Loader schemaVersion 1 can be migrated automatically.'
    }
    [void](Safe-Name (Nonblank $obj['id'] 'mod.id') 'mod.id' '^[a-z][a-z0-9_-]{1,63}$')
    [void](Nonblank $obj['version'] 'mod.version')
    if (Has $obj 'environment') {
        if ($obj['environment'] -cnotin @('*','client','server')) { Fail 'Invalid mod.environment.' }
    }
    foreach ($key in @('name','description','accessWidener')) {
        if (Has $obj $key) { [void](Nonblank $obj[$key] "mod.$key") }
    }
    if (Has $obj 'license') {
        if ($obj['license'] -is [array]) { Assert-StringArray $obj['license'] 'mod.license' }
        else { [void](Nonblank $obj['license'] 'mod.license') }
    }
    foreach ($key in @('authors','contributors')) {
        if (Has $obj $key) { Assert-StringArray $obj[$key] "mod.$key" $true }
    }
    if (Has $obj 'contact') {
        Assert-StringMap $obj['contact'] 'mod.contact'
        foreach ($key in @('homepage','issues')) {
            if ((Has $obj['contact'] $key) -and $obj['contact'][$key] -cnotmatch '^https?://[^\s]+$') {
                Fail "mod.contact.$key requires an HTTP(S) URL."
            }
        }
    }
    if (Has $obj 'provides') {
        Assert-StringArray $obj['provides'] 'mod.provides'
        $seenAliases = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($alias in $obj['provides']) {
            [void](Safe-Name $alias 'mod.provides entry' '^[a-z][a-z0-9_-]{1,63}$')
            if ($alias -ceq $obj['id'] -or -not $seenAliases.Add($alias)) { Fail "Duplicate or self-referential provided mod ID '$alias'." }
        }
    }
    if (Has $obj 'icon') {
        if ($obj['icon'] -is [string]) { [void](Relative-Resource $obj['icon'] 'mod.icon') }
        else {
            Assert-StringMap $obj['icon'] 'mod.icon'
            foreach ($size in $obj['icon'].Keys) {
                $n = 0
                if (-not [int]::TryParse($size, [ref]$n) -or $n -lt 1) { Fail "Invalid icon size '$size'." }
                [void](Relative-Resource $obj['icon'][$size] "mod.icon.$size")
            }
        }
    }
    foreach ($key in @('depends','recommends','suggests','conflicts','breaks')) {
        if (Has $obj $key) {
            Assert-StringMap $obj[$key] "mod.$key" $true
            foreach ($id in $obj[$key].Keys) {
                if ($id -cnotmatch '^[a-z][a-z0-9_-]{1,63}$' -and $id -cnotin @('minecraft','fabricloader','java')) {
                    Fail "Unsupported Loader dependency ID '$id'."
                }
            }
        }
    }
    if (Has $obj 'entrypoints') {
        Assert-TreeValue $obj['entrypoints'] 'mod.entrypoints'
        foreach ($entry in $obj['entrypoints'].Keys) {
            if ($obj['entrypoints'][$entry] -isnot [array] -or $obj['entrypoints'][$entry].Count -eq 0) {
                Fail "mod.entrypoints.$entry must be a nonempty array."
            }
            foreach ($v in $obj['entrypoints'][$entry]) {
                if ($v -is [string]) { [void](Nonblank $v 'mod.entrypoint') }
                elseif ($v -is [System.Collections.IDictionary]) {
                    Assert-Keys $v @('value','adapter') 'entrypoint'
                    [void](Nonblank $v['value'] 'entrypoint.value')
                    if (-not (Has $v 'adapter')) { Fail 'Object entrypoints require an explicit adapter in mod.yaml.' }
                    [void](Nonblank $v['adapter'] 'entrypoint.adapter')
                } else { Fail 'Unsupported entrypoint representation.' }
            }
        }
    }
    if (Has $obj 'languageAdapters') {
        Assert-StringMap $obj['languageAdapters'] 'mod.languageAdapters'
        if (Has $obj['languageAdapters'] 'kotlin') { Fail 'Fabric Amper disallows redefining the built-in Kotlin language adapter.' }
    }
    if (Has $obj 'mixins') {
        if ($obj['mixins'] -isnot [array]) { Fail 'mod.mixins must be an array.' }
        foreach ($m in $obj['mixins']) {
            if ($m -is [string]) { [void](Relative-Resource $m 'mod.mixins') }
            elseif ($m -is [System.Collections.IDictionary]) {
                Assert-Keys $m @('config','environment') 'mixin'
                [void](Relative-Resource $m['config'] 'mixin.config')
                if (Has $m 'environment') {
                    if ($m['environment'] -cnotin @('*','client','server')) { Fail 'Invalid mixin environment.' }
                }
            } else { Fail 'Unsupported mixin representation.' }
        }
    }
    if (Has $obj 'custom') { Assert-TreeValue $obj['custom'] 'mod.custom' }
    return ,$obj
}

# Strip comments and split Gradle's static DSL into statements / block boundaries.
# Quotes and ${...} in dependency strings are never executed or interpreted as code.
function Gradle-Tokens([string]$source) {
    if ($source -match '(?s)"""|\x27\x27\x27') { Fail 'Multiline Gradle strings require manual migration.' }
    $events = [System.Collections.ArrayList]::new()
    $buffer = [Text.StringBuilder]::new()
    $quote = [char]0; $escape = $false; $lineComment = $false; $blockComment = $false; $paren = 0
    for ($i = 0; $i -lt $source.Length; $i++) {
        $c = $source[$i]
        $next = if ($i + 1 -lt $source.Length) { $source[$i + 1] } else { [char]0 }
        if ($lineComment) {
            if ($c -ne "`n") { continue }
            $lineComment = $false
        }
        if ($blockComment) {
            if ($c -eq '*' -and $next -eq '/') { $i++; $blockComment = $false }
            continue
        }
        if ($quote -ne [char]0) {
            [void]$buffer.Append($c)
            if ($escape) { $escape = $false }
            elseif ($c -eq '\') { $escape = $true }
            elseif ($c -eq $quote) { $quote = [char]0 }
            continue
        }
        if ($c -eq '/' -and $next -eq '/') { $lineComment = $true; $i++; continue }
        if ($c -eq '/' -and $next -eq '*') { $blockComment = $true; $i++; continue }
        if ($c -eq '"' -or $c -eq "'") { $quote = $c; [void]$buffer.Append($c); continue }
        if ($c -eq '(') { $paren++; [void]$buffer.Append($c); continue }
        if ($c -eq ')') { $paren--; if ($paren -lt 0) { Fail 'Unbalanced parentheses in Gradle DSL.' }; [void]$buffer.Append($c); continue }
        if (($c -eq '{' -or $c -eq '}') -and $paren -eq 0) {
            $s = $buffer.ToString().Trim()
            if ($s) { [void]$events.Add([pscustomobject]@{type='statement'; value=$s}) }
            [void]$buffer.Clear()
            [void]$events.Add([pscustomobject]@{type=if ($c -eq '{') {'open'} else {'close'};value=''})
            continue
        }
        if (($c -eq "`n" -or $c -eq ';') -and $paren -eq 0) {
            $s = $buffer.ToString().Trim()
            if ($s) { [void]$events.Add([pscustomobject]@{type='statement'; value=$s}) }
            [void]$buffer.Clear()
            continue
        }
        [void]$buffer.Append($c)
    }
    if ($quote -ne [char]0 -or $blockComment -or $paren -ne 0) { Fail 'Unclosed string, comment or parentheses in Gradle DSL.' }
    $s = $buffer.ToString().Trim()
    if ($s) { [void]$events.Add([pscustomobject]@{type='statement';value=$s}) }
    return ,$events.ToArray()
}

function Read-Properties([string]$root) {
    $props = @{}
    $path = Assert-SafePath $root 'gradle.properties'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return $props }
    foreach ($line in [IO.File]::ReadAllLines($path)) {
        $line = $line.Trim()
        if (-not $line -or $line.StartsWith('#') -or $line.StartsWith('!')) { continue }
        if ($line -notmatch '^([^\s:=]+)\s*[:=]\s*(.*)$') { Fail 'Nonstandard gradle.properties syntax; migrate it manually.' }
        $key = $Matches[1]; $value = $Matches[2].Trim()
        if ($props.ContainsKey($key)) { Fail "Duplicate gradle.properties key '$key'." }
        $props[$key] = $value
    }
    return $props
}
function Resolve-Expression([string]$expression, [System.Collections.IDictionary]$vars) {
    $s = $expression.Trim()
    if ($s -match '^(?:project\.|rootProject\.)?([A-Za-z_][\w.-]*)$') {
        if ($vars.Contains($Matches[1])) { return [string]$vars[$Matches[1]] }
    }
    if ($s -match '^(?:project\.)?(?:property|findProperty)\([\x22\x27]([\w.-]+)[\x22\x27]\)$' -or
        $s -match '^providers\.gradleProperty\([\x22\x27]([\w.-]+)[\x22\x27]\)\.get\(\)$') {
        if ($vars.Contains($Matches[1])) { return [string]$vars[$Matches[1]] }
    }
    if ($s -match '^JavaVersion\.VERSION_(\d+)$') { return $Matches[1] }
    if ($s -match '^JavaLanguageVersion\.of\((\d+)\)$') { return $Matches[1] }
    if ($s -match '^\d+$') { return $s }
    if ($s -match '^([\x22\x27])(.*)\1$') {
        $delimiter = $Matches[1]; $body = $Matches[2]
        if ($delimiter -eq "'" -and $body.Contains('$')) { Fail "Unexpanded Groovy single-quoted variable '$body'." }
        if ($body -match '(?<!\\)\$(?!\{)') { Fail "Unsupported Gradle string interpolation '$body'." }
        $pattern = '\$\{([^{}]+)\}'
        $evaluator = [Text.RegularExpressions.MatchEvaluator]{
            param($match)
            $name = $match.Groups[1].Value.Trim()
            if ($name -match '^(?:project\.|rootProject\.)?([\w.-]+)$') { $name = $Matches[1] }
            elseif ($name -match '^(?:project\.)?property\([\x22\x27]([\w.-]+)[\x22\x27]\)$') { $name = $Matches[1] }
            else { Fail "Dynamic Gradle template variable '$name' is not supported." }
            if (-not $vars.Contains($name)) { Fail "Missing statically declared Gradle property '$name'." }
            return [string]$vars[$name]
        }
        $body = [regex]::Replace($body, $pattern, $evaluator)
        if ($body -match '\$\{|\$[A-Za-z_]') { Fail "Unresolved Gradle template '$body'." }
        return $body
    }
    Fail "Cannot safely evaluate static Gradle expression: $s. Set a fixed literal/gradle.properties value or migrate manually."
}
function Extract-Call([string]$line, [string]$name) {
    $pattern = '^(?:' + [regex]::Escape($name) + ')\s*(?:\(\s*(.+?)\s*\)|\s+(.+))$'
    if ($line -notmatch $pattern) { Fail "Unrecognized $name syntax '$line'." }
    return $(if ($Matches[1]) { $Matches[1] } else { $Matches[2] })
}
function Put-Unique([System.Collections.IDictionary]$map, [string]$key, [string]$value) {
    if ($map.Contains($key) -and $map[$key] -cne $value) { Fail "Conflicting static declarations for $key." }
    $map[$key] = $value
}
function Parse-Dependency([string]$line, [System.Collections.IDictionary]$state) {
    if ($line -notmatch '^([A-Za-z][\w]*)\s*(?:\((.*)\)|\s+(.+))$') {
        Fail "Unrecognized dependencies statement: $line"
    }
    $scope = $Matches[1]
    $expr = if ($null -ne $Matches[2] -and $Matches[2] -ne '') { $Matches[2].Trim() } else { $Matches[3].Trim() }
    $embedded = $false
    if ($expr -match '^include\s*\(\s*(.+?)\s*\)$') { $expr = $Matches[1]; $embedded = $true }
    if ($scope -eq 'mappings' -and $expr -match '^loom\.officialMojangMappings\(\)$') {
        Put-Unique $state.profile 'mappings' 'mojang'
        return
    }
    $coordinate = Resolve-Expression $expr $state.vars
    if ($scope -eq 'minecraft') {
        if ($coordinate -cnotmatch '^com\.mojang:minecraft:(\d+\.\d+(?:\.\d+)?)$') { Fail "Unrecognized Minecraft dependency '$coordinate'." }
        Put-Unique $state.profile 'minecraft' $Matches[1]
        return
    }
    if ($scope -eq 'mappings') {
        if ($coordinate -cnotmatch '^net\.fabricmc:yarn:([A-Za-z0-9_.+\-]+):v2$') { Fail "Unrecognized Yarn mappings '$coordinate'." }
        Put-Unique $state.profile 'mappings' 'yarn'
        Put-Unique $state.profile 'yarnVersion' $Matches[1]
        return
    }
    if ($coordinate -cnotmatch '^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+\-]+$' -or
        $coordinate -match '(?i):(?:latest\.|.*(?:\.\+|\[|\]|\(|\)|\*))') {
        Fail "Dynamic or unsupported dependency coordinate '$coordinate'."
    }
    if ($coordinate -match '^net\.fabricmc:fabric-loader:(.+)$') {
        if ($scope -cnotin @('modImplementation','implementation')) { Fail 'Fabric Loader uses an unsupported dependency scope.' }
        Put-Unique $state.profile 'loader' $Matches[1]; return
    }
    if ($coordinate -match '^net\.fabricmc\.fabric-api:fabric-api:(.+)$') {
        if ($scope -cnotin @('modImplementation','implementation')) { Fail 'Fabric API uses an unsupported dependency scope.' }
        Put-Unique $state.profile 'fabricApi' $Matches[1]; return
    }
    if ($coordinate -match '^net\.fabricmc:fabric-language-kotlin:(.+)$') {
        if ($scope -cnotin @('modImplementation','implementation')) { Fail 'Fabric Language Kotlin uses an unsupported dependency scope.' }
        Put-Unique $state.profile 'fabricLanguageKotlin' $Matches[1]; return
    }
    if ($scope -cnotin @('implementation','modImplementation','api','modApi','compileOnly',
        'modCompileOnly','runtimeOnly','modRuntimeOnly','clientImplementation','clientCompileOnly',
        'clientRuntimeOnly','testImplementation','testCompileOnly','testRuntimeOnly','include')) {
        Fail "Unsupported Gradle dependency configuration '$scope'."
    }
    $target = if ($scope.StartsWith('client')) { 'client' } else { 'main' }
    $test = $scope.StartsWith('test')
    $isInclude = $scope -ceq 'include' -or $embedded
    if ($test -and $isInclude) { Fail 'Test dependencies cannot use Loom jar-in-jar include.' }
    $where = if ($test) { $state.testDependencies } else { $state.dependencies }
    if ($scope -ceq 'include') {
        if ($where.Contains($coordinate)) { $where[$coordinate].include = $true }
        else { $where[$coordinate] = [pscustomobject]@{scope='all';target='main';exported=$false;include=$true;kind='library';includeOnly=$true} }
        return
    }
    $normalizedScope = if ($scope -match '(?i)CompileOnly') { 'compile-only' }
        elseif ($scope -match '(?i)RuntimeOnly') { 'runtime-only' } else { 'all' }
    $exported = $scope -cin @('api','modApi')
    $kind = if ($scope.StartsWith('mod', [StringComparison]::Ordinal) -and $scope -ne 'modApi') {'mod'}
        elseif ($scope -eq 'modApi') {'mod'} else {'library'}
    $spec = [pscustomobject]@{scope=$normalizedScope;target=$target;exported=$exported;include=$isInclude;kind=$kind;includeOnly=$false}
    if ($where.Contains($coordinate)) {
        $old = $where[$coordinate]
        if (-not $old.includeOnly -and ($old.scope -cne $spec.scope -or $old.target -cne $spec.target -or
            $old.exported -ne $spec.exported -or $old.kind -cne $spec.kind)) {
            Fail "Duplicate dependency '$coordinate' has conflicting scope/target/kind."
        }
        $spec.include = $spec.include -or $old.include
    }
    $where[$coordinate] = $spec
}
function Parse-Gradle([string]$file, [System.Collections.IDictionary]$props, [bool]$settings = $false) {
    $state = @{
        vars = @{}; profile = @{}; dependencies = [ordered]@{}; testDependencies = [ordered]@{}
        repositories = [System.Collections.ArrayList]::new(); plugins = [System.Collections.ArrayList]::new()
        split = $false; sourcesJar = $false; kotlin = $false; kotlinVersion = $null; name = $null; processVersion = $false
        sawLoom = $false; sawSourceSets = $false
    }
    foreach ($p in $props.Keys) { $state.vars[$p] = $props[$p] }
    $tokens = Gradle-Tokens ([IO.File]::ReadAllText($file))
    $stack = [System.Collections.ArrayList]::new()
    $pending = $null
    foreach ($ev in $tokens) {
        if ($ev.type -eq 'statement') {
            if ($null -ne $pending) { Handle-GradleStatement $pending $stack $state $settings }
            $pending = $ev.value
        } elseif ($ev.type -eq 'open') {
            if ($null -eq $pending) { Fail "Block opener without declaration in '$file'." }
            $opener = $pending.Trim(); $pending = $null
            $parent = if ($stack.Count -gt 0) { $stack[$stack.Count - 1] } else { 'root' }
            $block = Get-GradleBlock $opener $parent $settings
            [void]$stack.Add($block)
            if ($block -eq 'split') { $state.split = $true }
        } else {
            if ($null -ne $pending) { Handle-GradleStatement $pending $stack $state $settings; $pending = $null }
            if ($stack.Count -eq 0) { Fail 'Unmatched closing brace in Gradle DSL.' }
            $stack.RemoveAt($stack.Count - 1)
        }
    }
    if ($null -ne $pending) { Handle-GradleStatement $pending $stack $state $settings }
    if ($stack.Count -ne 0) { Fail "Unclosed Gradle DSL block in '$file'." }
    if (-not $settings) {
        if (-not $state.sawLoom) { Fail 'Gradle build must apply a known Fabric Loom plugin.' }
        if (-not $state.profile.ContainsKey('minecraft') -or -not $state.profile.ContainsKey('loader')) {
            Fail 'Minecraft and Fabric Loader must be specified by statically resolvable Gradle dependencies.'
        }
        foreach ($pair in $state.dependencies.GetEnumerator()) {
            if ($pair.Value.includeOnly) { Fail "include '$($pair.Key)' has no compile/runtime dependency; migrate manually." }
        }
    }
    return $state
}
function Get-GradleBlock([string]$opener, [string]$parent, [bool]$settings) {
    $name = $opener -replace '\s+', ' '
    if ($name -match '^plugins$' -and $parent -eq 'root') { return 'plugins' }
    if ($name -match '^pluginManagement$' -and $settings -and $parent -eq 'root') { return 'pluginManagement' }
    if ($name -match '^repositories$' -and $parent -in @('root','pluginManagement')) { return 'repositories' }
    if ($name -match '^maven$' -and $parent -eq 'repositories') { return 'maven' }
    if ($name -match '^dependencies$' -and $parent -eq 'root' -and -not $settings) { return 'dependencies' }
    if ($name -match '^base$' -and $parent -eq 'root' -and -not $settings) { return 'base' }
    if ($name -match '^loom$' -and $parent -eq 'root' -and -not $settings) { return 'loom' }
    if ($name -match '^java$' -and $parent -eq 'root' -and -not $settings) { return 'java' }
    if ($name -match '^kotlin$' -and $parent -eq 'root' -and -not $settings) { return 'kotlin' }
    if ($name -match '^toolchain$' -and $parent -eq 'java' -and -not $settings) { return 'toolchain' }
    if ($name -match '^splitEnvironmentSourceSets\(\)$' -and $parent -eq 'loom') { return 'split' }
    if ($name -match '^processResources$|^tasks\.named\([\x22\x27]processResources[\x22\x27]\)$' -and $parent -eq 'root') { return 'processResources' }
    if ($name -match '^filesMatching\([\x22\x27]fabric\.mod\.json[\x22\x27]\)$' -and $parent -eq 'processResources') { return 'filesMatching' }
    if ($name -match '^tasks\.withType(?:<JavaCompile>|\(JavaCompile(?:\.class)?\))(?:\.configureEach|\.all)?(?:\(\))?$' -and $parent -eq 'root') {
        return 'javaCompile'
    }
    Fail "Unknown Gradle DSL block '$opener' inside '$parent'. Automatic conversion would lose behavior."
}
function Handle-GradleStatement([string]$line, [System.Collections.ArrayList]$stack, [System.Collections.IDictionary]$state, [bool]$settings) {
    $context = if ($stack.Count -gt 0) { $stack[$stack.Count - 1] } else { 'root' }
    $s = ($line -replace '\s+', ' ').Trim()
    if ($s -match '^(?:val|def)\s+(\w+)\s*=\s*(.+)$' -and $context -eq 'root') {
        $key = $Matches[1]; $val = Resolve-Expression $Matches[2] $state.vars
        Put-Unique $state.vars $key $val; return
    }
    switch ($context) {
        'root' {
            if ($s -match '^rootProject\.name\s*=\s*(.+)$' -and $settings) {
                $state.name = Resolve-Expression $Matches[1] $state.vars; return
            }
            if (-not $settings -and $s -match '^(?:project\.)?(group|version|archivesBaseName|sourceCompatibility|targetCompatibility)\s*=\s*(.+)$') {
                $key = $Matches[1]; $expr = $Matches[2]
                Put-Unique $state.profile $key (Resolve-Expression $expr $state.vars); return
            }
            if (-not $settings -and $s -match '^java\.sourceCompatibility\s*=\s*(.+)$') {
                Put-Unique $state.profile 'sourceCompatibility' (Resolve-Expression $Matches[1] $state.vars); return
            }
        }
        'plugins' {
            if ($s -match '^id\s*(?:\(\s*)?[\x22\x27]([\w.-]+)[\x22\x27](?:\s*\))?(?:\s+version\s+(.+?))?(?:\s+apply\s+false)?$') {
                $plugin = $Matches[1]; $versionExpr = $Matches[2]
                $pluginVersion = if ($versionExpr) { Resolve-Expression $versionExpr $state.vars } else { $null }
                if ($plugin -in @('fabric-loom','net.fabricmc.fabric-loom','net.fabricmc.fabric-loom-remap')) {
                    $state.sawLoom = $true
                } elseif ($plugin -in @('org.jetbrains.kotlin.jvm','kotlin-jvm')) {
                    $state.kotlin = $true; $state.kotlinVersion = $pluginVersion
                }
                elseif ($plugin -notin @('java','java-library','idea','eclipse')) {
                    Fail "Plugin '$plugin' requires manual migration of its behavior."
                }
                [void]$state.plugins.Add($plugin); return
            }
            if ($s -match '^kotlin\([\x22\x27]jvm[\x22\x27]\)\s+version\s+(.+)$') {
                $state.kotlinVersion = Resolve-Expression $Matches[1] $state.vars
                $state.kotlin = $true; return
            }
        }
        'pluginManagement' { }
        'repositories' {
            if ($s -match '^(mavenCentral|gradlePluginPortal|mavenLocal)\(\)$') {
                if ($Matches[1] -eq 'mavenLocal') { Fail 'mavenLocal() is not reproducible in mod.yaml; migrate manually.' }
                [void]$state.repositories.Add($Matches[1]); return
            }
            if ($s -match '^maven\s*\(\s*(.+)\s*\)$') {
                $url = Resolve-Expression $Matches[1] $state.vars
                if ($url -cnotmatch '^https://[^\s]+$') { Fail 'Custom Maven repository requires a fixed HTTPS URL.' }
                [void]$state.repositories.Add($url); return
            }
        }
        'maven' {
            if ($s -match '^url\s*(?:=\s*)?(?:uri\s*\(\s*)?([\x22\x27].+[\x22\x27])\s*\)?$') {
                $url = Resolve-Expression $Matches[1] $state.vars
                if ($url -cnotmatch '^https://[^\s]+$') { Fail 'Custom Maven repository requires a fixed HTTPS URL.' }
                [void]$state.repositories.Add($url); return
            }
            if ($s -match '^name\s*=\s*[\x22\x27][\w .-]+[\x22\x27]$') { return }
        }
        'dependencies' { Parse-Dependency $s $state; return }
        'base' {
            if ($s -match '^archivesName\s*(?:\.set\s*\((.+)\)|=\s*(.+))$') {
                $expr = if ($Matches[1]) { $Matches[1] } else { $Matches[2] }
                Put-Unique $state.profile 'archivesName' (Resolve-Expression $expr $state.vars); return
            }
        }
        'loom' {
            if ($s -match '^splitEnvironmentSourceSets\(\)$') { $state.split = $true; return }
            if ($s -match '^accessWidenerPath\s*(?:=\s*file\((.+)\)|\.set\(file\((.+)\)\))$') {
                $expr = if ($Matches[1]) { $Matches[1] } else { $Matches[2] }
                Put-Unique $state.profile 'accessWidener' (Resolve-Expression $expr $state.vars); return
            }
        }
        'java' {
            if ($s -eq 'withSourcesJar()') { $state.sourcesJar = $true; return }
            if ($s -match '^sourceCompatibility\s*=\s*(.+)$') {
                Put-Unique $state.profile 'sourceCompatibility' (Resolve-Expression $Matches[1] $state.vars); return
            }
            if ($s -match '^targetCompatibility\s*=\s*(.+)$') {
                Put-Unique $state.profile 'targetCompatibility' (Resolve-Expression $Matches[1] $state.vars); return
            }
        }
        'toolchain' {
            if ($s -match '^languageVersion\s*(?:=\s*|\.set\s*\()(.*)$') {
                $expr = $Matches[1].TrimEnd(')')
                Put-Unique $state.profile 'javaRelease' (Resolve-Expression $expr $state.vars); return
            }
        }
        'kotlin' {
            if ($s -match '^jvmToolchain\((\d+)\)$') {
                Put-Unique $state.profile 'javaRelease' $Matches[1]; return
            }
        }
        'javaCompile' {
            if ($s -match '^(?:it\.)?options\.release(?:\.set\((.+)\)|\s*=\s*(.+))$') {
                $expr = if ($Matches[1]) { $Matches[1] } else { $Matches[2] }
                Put-Unique $state.profile 'javaRelease' (Resolve-Expression $expr $state.vars); return
            }
            if ($s -match '^(?:it\.)?options\.encoding\s*=\s*[\x22\x27]UTF-8[\x22\x27]$') { return }
        }
        'processResources' {
            if ($s -match '^inputs\.property\s*\(\s*[\x22\x27]version[\x22\x27]\s*,\s*(?:project\.)?version\s*\)$' -or
                $s -match '^inputs\.property\s+[\x22\x27]version[\x22\x27]\s*,\s*(?:project\.)?version$') { return }
        }
        'filesMatching' {
            if ($s -match '^expand\s*\(\s*[\x22\x27]version[\x22\x27]\s+to\s+(?:project\.)?version\s*\)$' -or
                $s -match '^expand\s+[\x22\x27]version[\x22\x27]\s*:\s*(?:project\.)?version$') {
                $state.processVersion = $true; return
            }
        }
    }
    Fail "Unsupported Gradle DSL statement in $context : $s. Migrate this behavior manually."
}

function Verify-Resources([string]$root, [System.Collections.IDictionary]$meta, [bool]$split, [string]$profile) {
    $resources = [System.Collections.ArrayList]::new()
    if (Has $meta 'icon') {
        if ($meta['icon'] -is [string]) { [void]$resources.Add(@{path=$meta['icon'];type='png';client=$true}) }
        else {
            foreach ($iconPath in $meta['icon'].Values) { [void]$resources.Add(@{path=$iconPath;type='png';client=$true}) }
        }
    }
    if (Has $meta 'mixins') {
        foreach ($mix in $meta['mixins']) {
            if ($mix -is [string]) { $p = $mix; $client = $false }
            else { $p = $mix['config']; $client = (Has $mix 'environment') -and $mix['environment'] -eq 'client' }
            [void]$resources.Add(@{path=$p;type='mixin';client=$client})
        }
    }
    if (Has $meta 'accessWidener') {
        [void]$resources.Add(@{path=$meta['accessWidener'];type='aw';client=$false})
        $ending = if ($profile -eq 'modern') { '.classtweaker' } else { '.accesswidener' }
        if (-not $meta['accessWidener'].EndsWith($ending, [StringComparison]::Ordinal)) {
            Fail "Access widener '$($meta['accessWidener'])' must use $ending for this Minecraft profile."
        }
    }
    foreach ($resource in $resources) {
        $p = Relative-Resource $resource.path 'metadata resource'
        $main = Assert-SafePath $root ('src/main/resources/' + $p)
        $client = Assert-SafePath $root ('src/client/resources/' + $p)
        $mainExists = Test-Path -LiteralPath $main -PathType Leaf
        $clientExists = Test-Path -LiteralPath $client -PathType Leaf
        if (-not $mainExists -and -not ($clientExists -and $split -and $resource.client)) {
            Fail "Resource '$p' is missing from src/main/resources (or an allowed split client resources folder)."
        }
        if ($resource.type -eq 'png' -and -not $p.EndsWith('.png', [StringComparison]::OrdinalIgnoreCase)) {
            Fail "Icon '$p' must be a PNG."
        }
        if ($resource.type -eq 'aw') {
            if (-not $mainExists) { Fail "Access widener '$p' must be in main resources." }
            $header = [IO.File]::ReadLines($main) | Select-Object -First 1
            if ($profile -eq 'modern' -and $header -cne 'classTweaker v1 official') {
                Fail "Class tweaker '$p' requires header 'classTweaker v1 official'."
            }
            if ($profile -eq 'legacy' -and $header -cnotmatch '^accessWidener v[12] (?:named|intermediary)$') {
                Fail "Access widener '$p' needs a legacy named/intermediary header."
            }
        }
    }
}
function Check-NoUnresolvedTemplates($value, [string]$path, [bool]$allowVersion = $false) {
    if ($value -is [string]) {
        if ($allowVersion -and $value -ceq '${version}') { return }
        if ($value -match '\$\{[^}]+\}|\$[A-Za-z_][\w.]*') {
            Fail "Unexpanded Loader metadata variable at $path : $value"
        }
    } elseif ($value -is [System.Collections.IDictionary]) {
        foreach ($k in $value.Keys) { Check-NoUnresolvedTemplates $value[$k] "$path.$k" }
    } elseif ($value -is [array]) {
        for ($i = 0; $i -lt $value.Count; $i++) { Check-NoUnresolvedTemplates $value[$i] "$path[$i]" }
    }
}

# Check authored Loader constraints against versions selected by the old build.
# Supports comparable numeric Fabric versions used by migrated projects; opaque
# or ambiguous ordering fails closed before any original file is moved.
function Compare-FabricVersions([string]$a, [string]$b) {
    $left = $a.Split('+')[0]
    $right = $b.Split('+')[0]
    if ($left -cnotmatch '^\d+(?:\.\d+)*$' -or $right -cnotmatch '^\d+(?:\.\d+)*$') { return $null }
    $aa = @($left.Split('.')); $bb = @($right.Split('.'))
    for ($i = 0; $i -lt [Math]::Max($aa.Count, $bb.Count); $i++) {
        $x = if ($i -lt $aa.Count) { [System.Numerics.BigInteger]::Parse($aa[$i]) } else { [System.Numerics.BigInteger]::Zero }
        $y = if ($i -lt $bb.Count) { [System.Numerics.BigInteger]::Parse($bb[$i]) } else { [System.Numerics.BigInteger]::Zero }
        $cmp = $x.CompareTo($y)
        if ($cmp -ne 0) { return $cmp }
    }
    return 0
}
function Check-LoaderTerm([string]$term, [string]$selected) {
    if ($term -eq '*') { return $true }
    if ($term -eq '||' -or $term -match '^!=') { return $term -ceq $selected }
    $op = ''
    foreach ($candidate in @('>=','<=','>','<','=','^','~')) {
        if ($term.StartsWith($candidate, [StringComparison]::Ordinal)) {
            $op = $candidate; break
        }
    }
    $v = $term.Substring($op.Length)
    if (-not $v -or $v -match '[<>\^~!=|&]') { return $null }
    if (-not $op -and $v -match '^(\d+(?:\.\d+)*)\.(?:x|X|\*)(?:\.(?:x|X|\*))*$') {
        $prefix = $Matches[1]
        $prefixParts = $prefix.Split('.')
        $increment = [System.Numerics.BigInteger]::Parse($prefixParts[$prefixParts.Length - 1]) + [System.Numerics.BigInteger]::One
        $prefixParts[$prefixParts.Length - 1] = $increment.ToString()
        $upper = $prefixParts -join '.'
        return (Compare-FabricVersions $selected $prefix) -ge 0 -and (Compare-FabricVersions $selected $upper) -lt 0
    }
    if ($v -match '[*xX]') {
        if (-not $op -and $v -ceq $selected) { return $true }
        return $null
    }
    $cmp = Compare-FabricVersions $selected $v
    if ($null -eq $cmp) {
        if ($op -in @('','=', '>=','<=','^','~')) { return $v -ceq $selected }
        return $null
    }
    switch ($op) {
        '' { return $cmp -eq 0 }
        '=' { return $cmp -eq 0 }
        '>=' { return $cmp -ge 0 }
        '<=' { return $cmp -le 0 }
        '>' { return $cmp -gt 0 }
        '<' { return $cmp -lt 0 }
        '^' {
            $bits = $v.Split('.')
            $upper = [System.Numerics.BigInteger]::Parse($bits[0]) + [System.Numerics.BigInteger]::One
            return $cmp -ge 0 -and (Compare-FabricVersions $selected ($upper.ToString() + '.0')) -lt 0
        }
        '~' {
            $bits = $v.Split('.')
            $axis = if ($bits.Length -gt 1) { 1 } else { 0 }
            $upper = [System.Numerics.BigInteger]::Parse($bits[$axis]) + [System.Numerics.BigInteger]::One
            $ceiling = if ($axis -eq 0) { $upper.ToString() + '.0' }
                else { $bits[0] + '.' + $upper.ToString() }
            return $cmp -ge 0 -and (Compare-FabricVersions $selected $ceiling) -lt 0
        }
    }
    return $null
}
function Assert-MatchesLoaderRequirement($requirement, [string]$selected, [string]$name) {
    $alternatives = if ($requirement -is [array]) { $requirement } else { @($requirement) }
    $matched = $false
    foreach ($expression in $alternatives) {
        $terms = @(([string]$expression).Trim() -split '\s+')
        $andMatches = $true
        foreach ($term in $terms) {
            $answer = Check-LoaderTerm $term $selected
            if ($null -eq $answer) {
                Fail "Loader requirement '$expression' for $name cannot be safely evaluated; manually verify compatibility."
            }
            if (-not $answer) { $andMatches = $false }
        }
        if ($andMatches) { $matched = $true }
    }
    if (-not $matched) { Fail "Loader requirement for $name does not accept selected version '$selected'." }
}
function Pick-JavaRelease([System.Collections.IDictionary]$profile, [System.Collections.IDictionary]$meta, [string]$mc) {
    $candidates = [System.Collections.ArrayList]::new()
    foreach ($key in @('javaRelease','sourceCompatibility','targetCompatibility')) {
        if ($profile.Contains($key)) { [void]$candidates.Add([string]$profile[$key]) }
    }
    if ($candidates.Count -eq 0 -and (Has $meta 'depends') -and (Has $meta['depends'] 'java')) {
        $dep = $meta['depends']['java']
        if ($dep -is [string] -and $dep -match '^>=(\d+)$') { [void]$candidates.Add($Matches[1]) }
    }
    $modern = $mc -match '^26\.'
    if ($candidates.Count -eq 0) {
        if ($modern) { [void]$candidates.Add('25') }
        elseif ($mc -match '^1\.21\.') { [void]$candidates.Add('21') }
        else { Fail "Cannot infer safe Java release for Minecraft $mc; specify a static Java toolchain/release in Gradle." }
    }
    foreach ($release in $candidates) {
        if ($release -cnotmatch '^\d+$' -or [int]$release -lt 8 -or [int]$release -gt 30) {
            Fail "Unsupported Java release '$release'."
        }
        if ([int]$release -ne [int]$candidates[0]) { Fail 'Conflicting Gradle Java target/toolchain declarations.' }
    }
    if ($modern -and [int]$candidates[0] -ne 25) { Fail 'Minecraft 26.1+ requires Java release 25.' }
    return [int]$candidates[0]
}
function New-Migration([string]$root, [System.Collections.IDictionary]$metadata,
                       [System.Collections.IDictionary]$build, [System.Collections.IDictionary]$settings,
                       [string]$version) {
    $p = $build.profile
    $mc = $p['minecraft']
    if ($mc -cnotmatch '^\d+\.\d+(?:\.\d+)?$') { Fail "Invalid fixed Minecraft version '$mc'." }
    $parts = $mc.Split('.')
    $modern = [int]$parts[0] -gt 26 -or ([int]$parts[0] -eq 26 -and [int]$parts[1] -ge 1)
    $legacy = [int]$parts[0] -eq 1
    if (-not $modern -and -not $legacy) { Fail "Unsupported Minecraft migration profile '$mc'." }
    $flavor = if ($modern) {'modern'} else {'legacy'}
    if ($modern -and $build.plugins.Contains('net.fabricmc.fabric-loom-remap')) { Fail 'Remapping Loom cannot target modern Minecraft.' }
    if ($legacy -and $build.plugins.Contains('net.fabricmc.fabric-loom')) { Fail 'Non-remapping Loom cannot target legacy Minecraft.' }
    if ($modern -and $p.ContainsKey('mappings') -and $p['mappings'] -ne 'mojang') { Fail 'Modern Minecraft requires official Mojang mappings.' }
    if ($legacy -and -not $p.ContainsKey('mappings')) { Fail 'Legacy Minecraft requires an explicit mappings declaration.' }
    foreach ($dependency in $build.dependencies.Values) {
        if ($dependency.target -eq 'client' -and (-not $build.split -or $legacy -or $dependency.include -or $dependency.exported)) {
            Fail 'Client-only Gradle dependencies require modern Minecraft, Loom split source sets, and no include/exported flags.'
        }
        if ($dependency.include -and $dependency.scope -ne 'all') {
            Fail 'Loom include is only supported alongside all-scope dependencies.'
        }
    }
    if ($p.ContainsKey('fabricLanguageKotlin') -and $p['fabricLanguageKotlin'] -cne '1.14.2+kotlin.2.4.21') {
        Fail 'Fabric Amper currently pins Fabric Language Kotlin 1.14.2+kotlin.2.4.21.'
    }
    if ($build.kotlin -and $build.kotlinVersion -cne '2.4.21') {
        Fail 'Fabric Amper requires an explicit Kotlin Gradle plugin version 2.4.21 for safe migration.'
    }
    if ($p.ContainsKey('fabricLanguageKotlin') -and -not $build.kotlin) { Fail 'Fabric Language Kotlin dependency requires Kotlin JVM plugin.' }
    if ($build.kotlin -and -not $p.ContainsKey('fabricLanguageKotlin')) {
        Fail 'Kotlin JVM plugin is used without a fixed Fabric Language Kotlin dependency.'
    }
    if ((Has $metadata 'entrypoints')) {
        foreach ($key in $metadata['entrypoints'].Keys) {
            foreach ($entry in $metadata['entrypoints'][$key]) {
                if ($entry -is [System.Collections.IDictionary] -and (Has $entry 'adapter') -and
                    $entry['adapter'] -eq 'kotlin' -and -not $build.kotlin) {
                    Fail 'Kotlin entrypoint found, but Kotlin JVM plugin is missing.'
                }
            }
        }
    }
    if ($p.ContainsKey('version')) {
        $v = [string]$p['version']
        if ($metadata['version'] -eq '${version}') { $metadata['version'] = $v }
        elseif ($metadata['version'] -cne $v) { Fail "build.gradle version '$v' differs from fabric.mod.json version '$($metadata['version'])'." }
    }
    if ($metadata['version'] -eq '${version}') { Fail 'fabric.mod.json version needs a static Gradle project version.' }
    Check-NoUnresolvedTemplates $metadata['version'] 'mod.version'
    foreach ($key in $metadata.Keys) {
        if ($key -eq 'version') { continue }
        Check-NoUnresolvedTemplates $metadata[$key] "mod.$key"
    }
    [void](Safe-Name ([string]$metadata['version']) 'mod.version' '^\S+$')
    if ($p.ContainsKey('group')) { [void](Safe-Name $p['group'] 'mod.group' '^[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*$') }
    $archive = if ($p.ContainsKey('archivesName')) { $p['archivesName'] }
        elseif ($p.ContainsKey('archivesBaseName')) { $p['archivesBaseName'] } else { $null }
    if ($archive) { [void](Safe-Name $archive 'mod.archivesName' '^[A-Za-z0-9._-]+$') }
    if ($archive -in @('.','..')) { Fail 'Invalid archive name.' }
    if ($p.ContainsKey('accessWidener')) {
        $aw = $p['accessWidener'].Replace('\','/')
        if ($aw.StartsWith('src/main/resources/')) { $aw = $aw.Substring('src/main/resources/'.Length) }
        if (-not (Has $metadata 'accessWidener') -or $aw -cne $metadata['accessWidener']) {
            Fail 'Loom access widener path disagrees with fabric.mod.json; migrate it manually.'
        }
    }
    $java = Pick-JavaRelease $p $metadata $mc
    if (Has $metadata 'depends') {
        $versions = [ordered]@{ minecraft=$mc;fabricloader=$p['loader'];java=[string]$java }
        if ($p.ContainsKey('fabricApi')) { $versions['fabric-api'] = $p['fabricApi'] }
        if ($p.ContainsKey('fabricLanguageKotlin')) { $versions['fabric-language-kotlin'] = $p['fabricLanguageKotlin'] }
        foreach ($key in $versions.Keys) {
            if (Has $metadata['depends'] $key) {
                Assert-MatchesLoaderRequirement $metadata['depends'][$key] $versions[$key] $key
            }
        }
    }
    Verify-Resources $root $metadata $build.split $flavor

    $mod = [ordered]@{}
    foreach ($key in @('id','version')) { $mod[$key] = $metadata[$key] }
    if ($p.ContainsKey('group')) { $mod['group'] = $p['group'] }
    if ($archive) { $mod['archivesName'] = $archive }
    foreach ($key in @('name','description','environment','license','authors','contributors','contact','icon',
            'provides','entrypoints','languageAdapters','mixins','accessWidener','depends','recommends',
            'suggests','conflicts','breaks','custom')) {
        if (Has $metadata $key) { $mod[$key] = $metadata[$key] }
    }
    $mappings = if ($modern -or $p['mappings'] -eq 'mojang') { 'mojang' }
        else { [ordered]@{type='yarn';version=$p['yarnVersion']} }
    $game = [ordered]@{version=$mc;mappings=$mappings;loader=$p['loader']}
    if ($p.ContainsKey('fabricApi')) { $game['fabricApi'] = $p['fabricApi'] }
    $yaml = [System.Collections.Generic.List[string]]::new()
    [void]$yaml.Add('# Migrated by Fabric Amper v' + $version + '. Review this file before building.')
    [void]$yaml.Add('schema: 1')
    [void]$yaml.Add('product: fabric/mod')
    [void]$yaml.Add('mod:')
    foreach ($key in $mod.Keys) { [void]$yaml.Add('  ' + $key + ': ' + (Yaml-Flow $mod[$key])) }
    [void]$yaml.Add('minecraft:')
    foreach ($key in $game.Keys) { [void]$yaml.Add('  ' + $key + ': ' + (Yaml-Flow $game[$key])) }
    [void]$yaml.Add('settings:')
    [void]$yaml.Add('  java:')
    [void]$yaml.Add('    release: ' + $java)
    [void]$yaml.Add('    sourcesJar: ' + $(if ($build.sourcesJar) {'true'} else {'false'}))
    [void]$yaml.Add('  kotlin:')
    [void]$yaml.Add('    enabled: ' + $(if ($build.kotlin) {'true'} else {'false'}))
    [void]$yaml.Add('  loom:')
    [void]$yaml.Add('    splitEnvironmentSourceSets: ' + $(if ($build.split) {'true'} else {'false'}))
    [void]$yaml.Add('repositories:')
    $repos = [System.Collections.Generic.List[string]]::new()
    foreach ($repo in @('fabric','mavenCentral') + @($build.repositories.ToArray())) {
        $item = switch ($repo) {
            'mavenCentral' {'mavenCentral';break}
            'fabric' {'fabric';break}
            'https://maven.fabricmc.net/' {'fabric';break}
            'https://api.modrinth.com/maven' {'modrinth';break}
            default { $repo }
        }
        if ($item -eq 'gradlePluginPortal') { continue }
        if ($item -cnotin $repos) { [void]$repos.Add($item) }
    }
    foreach ($repo in $repos) {
        if ($repo -in @('fabric','mavenCentral','modrinth')) { [void]$yaml.Add('  - ' + $repo) }
        else { [void]$yaml.Add('  - maven: ' + (Yaml-Flow $repo)) }
    }
    foreach ($type in @('dependencies','testDependencies')) {
        $items = $build[$type]
        if ($items.Count -eq 0) { continue }
        $title = if ($type -eq 'testDependencies') {'test-dependencies'} else {'dependencies'}
        [void]$yaml.Add($title + ':')
        foreach ($coordinate in $items.Keys) {
            $dep = $items[$coordinate]
            [void]$yaml.Add('  - maven: ' + (Yaml-Flow $coordinate))
            [void]$yaml.Add('    kind: ' + $dep.kind)
            [void]$yaml.Add('    scope: ' + $dep.scope)
            if ($type -eq 'dependencies') {
                if ($dep.target -eq 'client') { [void]$yaml.Add('    target: client') }
                if ($dep.exported) { [void]$yaml.Add('    exported: true') }
                if ($dep.include) { [void]$yaml.Add('    include: true') }
            }
        }
    }
    $projectName = if ($settings -and $settings.name) { $settings.name } else { [IO.Path]::GetFileName($root) }
    [void](Nonblank $projectName 'rootProject.name')
    if ($projectName -match '[\r\n\x00-\x1F]') { Fail 'Unsafe Gradle project name.' }
    $settingsText = @'
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
                useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v__VERSION__")
            }
        }
    }
}
plugins {
    id("dev.fabric-amper.settings") version "__VERSION__"
}
rootProject.name = __PROJECT_NAME__
'@
    $settingsText = $settingsText.Replace('__VERSION__', $version).Replace('__PROJECT_NAME__', (Yaml-String $projectName))
    return [pscustomobject]@{Yaml=($yaml -join "`n") + "`n";Settings=$settingsText + "`n"; Minecraft=$mc; ModId=$mod['id'];Java=$java;Count=$build.dependencies.Count}
}

function Migrate-Project {
    if ([string]::IsNullOrWhiteSpace($env:FA_CONSUMER)) { Fail 'FA_CONSUMER is unset; specify a consumer project directory.' }
    if (-not [IO.Directory]::Exists($env:FA_CONSUMER)) { Fail "Consumer directory does not exist: $env:FA_CONSUMER" }
    $root = [IO.Path]::GetFullPath($env:FA_CONSUMER).TrimEnd('\','/')
    if (-not [IO.Directory]::Exists($root) -or [IO.Directory]::GetParent($root) -eq $null) {
        Fail 'Consumer must be a project subdirectory, not a filesystem root.'
    }
    $rootCursor = [IO.DirectoryInfo]::new($root)
    while ($null -ne $rootCursor) {
        $rootEntry = Get-Item -LiteralPath $rootCursor.FullName -Force
        if (($rootEntry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            Fail "Consumer path traverses a symlink/junction: $($rootCursor.FullName)"
        }
        $rootCursor = $rootCursor.Parent
    }
    $version = if ($env:FA_VERSION) { $env:FA_VERSION } else { '0.2.0' }
    if ($version -cnotmatch '^0\.2\.0$') { Fail "Unexpected helper version '$version'; this engine requires Fabric Amper 0.2.0." }
    if (-not $env:FA_ROOT -or -not (Test-Path -LiteralPath (Join-Path $env:FA_ROOT 'fabric-amper.schema.json') -PathType Leaf)) {
        Fail 'FA_ROOT must point to a Fabric Amper producer folder with fabric-amper.schema.json.'
    }
    $backup = Assert-SafePath $root 'gradle_backup'
    if (Test-Path -LiteralPath $backup) { Fail 'gradle_backup already exists; move/inspect it before migration to prevent overwrites.' }
    foreach ($rel in @('mod.yaml','mod.yml','settings.gradle','settings.gradle.kts','build.gradle','build.gradle.kts')) {
        [void](Assert-SafePath $root $rel)
    }
    if ((Test-Path -LiteralPath (Join-Path $root 'mod.yaml')) -or (Test-Path -LiteralPath (Join-Path $root 'mod.yml'))) {
        Fail 'Existing mod.yaml/mod.yml detected. Refusing to overwrite it.'
    }
    $builds = @(@('build.gradle.kts','build.gradle') | Where-Object { Test-Path -LiteralPath (Join-Path $root $_) -PathType Leaf })
    if ($builds.Count -ne 1) { Fail "Expected exactly one build.gradle.kts or build.gradle, found $($builds.Count)." }
    $settingsFiles = @(@('settings.gradle.kts','settings.gradle') | Where-Object { Test-Path -LiteralPath (Join-Path $root $_) -PathType Leaf })
    if ($settingsFiles.Count -gt 1) { Fail 'Both settings.gradle and settings.gradle.kts exist; resolve manually.' }
    $metadata = Get-SourceMetadata $root
    $metadataPath = Assert-SafePath $root $metadata
    $props = Read-Properties $root
    $buildState = Parse-Gradle (Join-Path $root $builds[0]) $props
    $settingsState = if ($settingsFiles.Count -eq 1) {
        Parse-Gradle (Join-Path $root $settingsFiles[0]) $props $true
    } else { $null }
    $json = Read-Metadata $metadataPath
    $result = New-Migration $root $json $buildState $settingsState $version

    $sources = [System.Collections.Generic.List[string]]::new()
    [void]$sources.Add($builds[0])
    if ($settingsFiles.Count) { [void]$sources.Add($settingsFiles[0]) }
    [void]$sources.Add($metadata)
    $displaySources = $sources -join ', '
    Write-Output "[migrate] Consumer: $root"
    Write-Output "[migrate] Source files: $displaySources"
    Write-Output "[migrate] Detected mod $($result.ModId), Minecraft $($result.Minecraft), Java $($result.Java), Gradle dependencies $($result.Count)."
    if ($Preview) {
        Write-Output '[preview] Validation complete; no files were created, moved, deleted or overwritten.'
        Write-Output "[preview] Would create mod.yaml and settings.gradle.kts."
        Write-Output $(if ($Delete) { '[preview] --delete: originals would be deleted after conversion.' }
            else { '[preview] Originals would move to gradle_backup/ (including any existing settings file).' })
        Write-Output '[preview] Generated mod.yaml:'
        Write-Output $result.Yaml
        return
    }

    # Stage complete outputs and byte-exact copies of inputs before touching the source.
    # The transaction's catch block restores originals on any recoverable I/O error.
    $stageName = '.fabric-amper-migrate-' + [guid]::NewGuid().ToString('N')
    $stage = Assert-SafePath $root $stageName
    $moved = [System.Collections.ArrayList]::new()
    $created = [System.Collections.ArrayList]::new()
    $createdBackup = $false
    $committed = $false
    $rollbackFailures = [System.Collections.Generic.List[string]]::new()
    try {
        [void](New-Item -ItemType Directory -Path $stage -ErrorAction Stop)
        [IO.File]::WriteAllText((Join-Path $stage 'new-mod.yaml'), $result.Yaml, [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText((Join-Path $stage 'new-settings.gradle.kts'), $result.Settings, [Text.UTF8Encoding]::new($false))
        for ($i = 0; $i -lt $sources.Count; $i++) {
            $src = Assert-SafePath $root $sources[$i]
            [IO.File]::WriteAllBytes((Join-Path $stage ('snapshot-' + $i)), [IO.File]::ReadAllBytes($src))
        }
        if (-not $Delete) {
            [void](New-Item -ItemType Directory -Path $backup -ErrorAction Stop)
            $createdBackup = $true
        }
        for ($i = 0; $i -lt $sources.Count; $i++) {
            $original = Assert-SafePath $root $sources[$i]
            $name = if ($sources[$i] -eq $metadata) { 'fabric.mod.json' } else { $sources[$i] }
            $destination = if ($Delete) { Join-Path $stage ('removed-' + $i) } else { Join-Path $backup $name }
            if (Test-Path -LiteralPath $destination) { Fail "Backup destination unexpectedly exists: $destination" }
            Move-Item -LiteralPath $original -Destination $destination -ErrorAction Stop
            [void]$moved.Add([pscustomobject]@{Original=$original;Destination=$destination;Index=$i})
        }
        foreach ($pair in @(@('new-mod.yaml','mod.yaml'), @('new-settings.gradle.kts','settings.gradle.kts'))) {
            $output = Assert-SafePath $root $pair[1]
            if (Test-Path -LiteralPath $output) { Fail "Destination appeared during migration: $output" }
            Move-Item -LiteralPath (Join-Path $stage $pair[0]) -Destination $output -ErrorAction Stop
            [void]$created.Add($output)
        }
        $committed = $true
        Write-Output '[OK] Created mod.yaml and settings.gradle.kts.'
        if ($Delete) { Write-Output '[OK] Original build script, metadata and prior settings removed after successful conversion.' }
        else { Write-Output '[OK] Source build script, metadata and prior settings preserved in gradle_backup/.' }
    } catch {
        $failure = $_.Exception.Message
        foreach ($output in $created) {
            try { if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Force -ErrorAction Stop } }
            catch { [void]$rollbackFailures.Add("Cannot remove generated output: $output") }
        }
        for ($i = $moved.Count - 1; $i -ge 0; $i--) {
            $m = $moved[$i]
            try {
                if (Test-Path -LiteralPath $m.Original) { Fail "Original location unexpectedly occupied: $($m.Original)" }
                if (Test-Path -LiteralPath $m.Destination) {
                    Move-Item -LiteralPath $m.Destination -Destination $m.Original -ErrorAction Stop
                } else {
                    Copy-Item -LiteralPath (Join-Path $stage ('snapshot-' + $m.Index)) -Destination $m.Original -ErrorAction Stop
                }
            } catch { [void]$rollbackFailures.Add("Cannot restore original: $($m.Original)") }
        }
        if ($createdBackup) {
            try { Remove-Item -LiteralPath $backup -Force -ErrorAction Stop }
            catch { [void]$rollbackFailures.Add('Cannot remove newly created gradle_backup directory.') }
        }
        if ($rollbackFailures.Count) {
            Fail "Migration failed ($failure). Rollback requires manual recovery; snapshots are in '$stage'. $($rollbackFailures -join '; ')"
        }
        Fail "Migration failed ($failure). All moved sources were restored."
    } finally {
        if ($committed -or $rollbackFailures.Count -eq 0) {
            if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue }
        }
    }
}

try { Migrate-Project }
catch { [Console]::Error.WriteLine('[ERROR] ' + $_.Exception.Message); exit 1 }
