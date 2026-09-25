param(
    [Parameter(Mandatory = $true)][string]$ModJar,
    [Parameter(Mandatory = $true)][string]$MinecraftJar,
    [string]$JdepsPath = '',
    [switch]$FailOnMissing
)

$ErrorActionPreference = 'Stop'
$modPath = (Resolve-Path -LiteralPath $ModJar).Path
$gamePath = (Resolve-Path -LiteralPath $MinecraftJar).Path
$jdeps = if ($JdepsPath) { (Resolve-Path -LiteralPath $JdepsPath).Path } else {
    (Get-Command jdeps -ErrorAction SilentlyContinue).Source
}
if (-not $jdeps) {
    $java = (Get-Command java -ErrorAction Stop).Source
    $candidate = Join-Path (Split-Path $java) 'jdeps.exe'
    if (Test-Path -LiteralPath $candidate) { $jdeps = $candidate }
}
if (-not $jdeps) { throw 'jdeps was not found; install a JDK and add it to PATH.' }

Add-Type -AssemblyName System.IO.Compression
$game = [IO.Compression.ZipFile]::OpenRead($gamePath)
try {
    $available = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($entry in $game.Entries) { [void]$available.Add($entry.FullName) }
    $references = @(& $jdeps -verbose:class -filter:none $modPath 2>$null |
        ForEach-Object {
            if ($_ -match '-> (net\.minecraft\.[A-Za-z0-9_.$]+)') { $Matches[1] }
        } | Where-Object {
            $simple = ($_ -split '\.')[-1]
            $simple -cmatch '^[A-Z]' -or $simple -match '^class_\d+'
        } | Sort-Object -Unique)
    $missing = @($references | Where-Object {
        -not $available.Contains(($_ -replace '\.', '/') + '.class')
    })
    [pscustomobject]@{
        ModJar = $modPath
        MinecraftJar = $gamePath
        MinecraftReferences = $references.Count
        MissingCount = $missing.Count
        MissingClasses = $missing
    }
    if ($FailOnMissing -and $missing.Count -gt 0) { exit 1 }
} finally {
    $game.Dispose()
}
