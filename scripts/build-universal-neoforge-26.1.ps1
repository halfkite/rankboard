param(
    [string]$OutputDirectory = "",
    [string]$ModVersion = ""
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$neoForgeDirectory = Join-Path $repoRoot 'neoforge'
$gradle = if ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT) {
    Join-Path $repoRoot 'gradlew.bat'
} else {
    Join-Path $repoRoot 'gradlew'
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repoRoot (".tmp/neoforge-universal-26.1-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
} elseif (-not [System.IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory = Join-Path $repoRoot $OutputDirectory
}
$variantDirectory = Join-Path $OutputDirectory 'variants'
New-Item -ItemType Directory -Force $variantDirectory | Out-Null

$variants = @(
    @{ Minecraft = '26.1'; NeoForge = '26.1.0.19-beta' },
    @{ Minecraft = '26.1.1'; NeoForge = '26.1.1.15-beta' },
    @{ Minecraft = '26.1.2'; NeoForge = '26.1.2.94' }
)

Push-Location $neoForgeDirectory
try {
    foreach ($variant in $variants) {
        Write-Host "Building NeoForge $($variant.Minecraft) ($($variant.NeoForge))"
        $gradleArguments = @(
            '-p', $neoForgeDirectory, 'clean', 'build',
            "-Pminecraft_version=$($variant.Minecraft)",
            "-Pminecraft_version_range=[$($variant.Minecraft)]",
            "-Pneo_version=$($variant.NeoForge)",
            "-Pparchment_minecraft_version=$($variant.Minecraft)",
            '-Pparchment_mappings_version=none',
            '-Puniversal_build=true',
            '--no-daemon'
        )
        if (-not [string]::IsNullOrWhiteSpace($ModVersion)) {
            $gradleArguments += "-Pmod_version=$ModVersion"
        }
        & $gradle @gradleArguments
        if ($LASTEXITCODE -ne 0) { throw "NeoForge build failed for $($variant.Minecraft)" }

        $jar = Get-ChildItem (Join-Path $neoForgeDirectory 'build/libs') -Filter '*.jar' -File |
            Where-Object { $_.Name -notmatch '(sources|dev-shadow)' } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($null -eq $jar) { throw "No NeoForge jar found for $($variant.Minecraft)" }
        Copy-Item $jar.FullName (Join-Path $variantDirectory "$($variant.Minecraft).jar") -Force
    }
} finally {
    Pop-Location
}

Write-Host "NeoForge 26.1.x variant jars: $variantDirectory"
