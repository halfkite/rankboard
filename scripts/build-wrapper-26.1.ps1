param(
    [string]$OutputDirectory = "build/libs",
    [string]$ModVersion = ""
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression

$projectRoot = Split-Path -Parent $PSScriptRoot
$gradle = if ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT) {
    Join-Path $projectRoot "gradlew.bat"
} else {
    Join-Path $projectRoot "gradlew"
}
$properties = Get-Content (Join-Path $projectRoot "gradle.properties") |
    Where-Object { $_ -match "^[^#=]+=.*$" } |
    ForEach-Object {
        $parts = $_ -split "=", 2
        @{ Key = $parts[0].Trim(); Value = $parts[1].Trim() }
    }
$modVersion = if ([string]::IsNullOrWhiteSpace($ModVersion)) {
    ($properties | Where-Object { $_.Key -eq "mod_version" }).Value
} else {
    $ModVersion
}

$variants = @(
    @{ Name = "26.1"; Minecraft = "26.1"; Fabric = "0.145.1+26.1" },
    @{ Name = "26.1.1"; Minecraft = "26.1.1"; Fabric = "0.145.4+26.1.1" },
    @{ Name = "26.1.2"; Minecraft = "26.1.2"; Fabric = "0.155.2+26.1.2" }
)
$timestamp = Get-Date -Format "yyMMddHHmm"
$work = Join-Path $projectRoot ".rankboard-wrapper-work-26.1"
$innerDirectory = Join-Path $work "META-INF/jars"
$staging = Join-Path $work "wrapper"

if (Test-Path -LiteralPath $work) {
    [System.IO.Directory]::Delete($work, $true)
}
New-Item -ItemType Directory -Force $innerDirectory, $staging | Out-Null

foreach ($variant in $variants) {
    Write-Host "Building RankBoard for Minecraft $($variant.Minecraft)..."
    $arguments = @(
        "clean", "build", "-x", "remapSourcesJar", "--no-daemon",
        "-Pminecraft_version=$($variant.Minecraft)",
        "-Pmapping_type=none",
        "-Ploader_version=0.19.3",
        "-Pfabric_version=$($variant.Fabric)",
        "-Pmod_version=$modVersion"
    )
    & $gradle @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Minecraft $($variant.Minecraft) build failed with exit code $LASTEXITCODE."
    }

    $artifact = Get-ChildItem (Join-Path $projectRoot "build/libs") -Filter "rankboard-*.jar" |
        Where-Object { $_.Name -notlike "*-sources*" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $artifact) {
        throw "No distributable JAR was produced for Minecraft $($variant.Minecraft)."
    }
    Copy-Item -LiteralPath $artifact.FullName -Destination (Join-Path $innerDirectory "rankboard-mc$($variant.Name)-$modVersion.jar")
}

Copy-Item -LiteralPath (Join-Path $projectRoot "LICENSE") -Destination (Join-Path $staging "LICENSE")
Copy-Item -LiteralPath (Join-Path $work "META-INF") -Destination (Join-Path $staging "META-INF") -Recurse

$wrapperManifest = [ordered]@{
    schemaVersion = 1
    id = "rankboard_wrapper"
    version = "$modVersion+mc26.1.x+$timestamp"
    name = "RankBoard 26.1.x Wrapper"
    description = "Selects the compatible RankBoard build for Minecraft 26.1.x."
    environment = "*"
    entrypoints = @{}
    depends = [ordered]@{
        fabricloader = ">=0.15.11"
        minecraft = ">=26.1 <26.2"
        java = ">=25"
    }
    jars = @($variants | ForEach-Object {
        [ordered]@{ file = "META-INF/jars/rankboard-mc$($_.Name)-$modVersion.jar" }
    })
}
[System.IO.File]::WriteAllText(
    (Join-Path $staging "fabric.mod.json"),
    ($wrapperManifest | ConvertTo-Json -Depth 8),
    [System.Text.UTF8Encoding]::new($false)
)

$outputRoot = Join-Path $projectRoot $OutputDirectory
New-Item -ItemType Directory -Force $outputRoot | Out-Null
$output = Join-Path $outputRoot "rankboard-$modVersion+mc26.1.x+$timestamp.jar"
if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Force }
$archive = [System.IO.Compression.ZipFile]::Open($output, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem -LiteralPath $staging -File -Recurse) {
        $relative = $file.FullName.Substring($staging.Length).TrimStart("\", "/").Replace("\", "/")
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive, $file.FullName, $relative, [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
} finally {
    $archive.Dispose()
}
Write-Host "Universal 26.1.x wrapper created: $output"
Get-FileHash -LiteralPath $output -Algorithm SHA256
