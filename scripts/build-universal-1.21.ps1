param(
    [string]$OutputDirectory = "build/libs"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression

$projectRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $projectRoot "gradlew.bat"
$properties = Get-Content (Join-Path $projectRoot "gradle.properties") |
    Where-Object { $_ -match "^[^#=]+=.*$" } |
    ForEach-Object {
        $parts = $_ -split "=", 2
        @{ Key = $parts[0].Trim(); Value = $parts[1].Trim() }
    }
$modVersion = ($properties | Where-Object { $_.Key -eq "mod_version" }).Value
$timestamp = Get-Date -Format "yyMMddHHmm"
$work = Join-Path $projectRoot ".rankboard-wrapper-work"
$innerDirectory = Join-Path $work "META-INF/jars"
$staging = Join-Path $work "wrapper"

$variants = @(
    @{
        Name = "1.21"
        Minecraft = "1.21"
        Mappings = "1.21+build.9"
        Fabric = "0.102.0+1.21"
        Range = ">=1.21 <1.21.5"
    },
    @{
        Name = "1.21.5"
        Minecraft = "1.21.5"
        Mappings = "1.21.5+build.1"
        Fabric = "0.128.2+1.21.5"
        Range = ">=1.21.5 <1.21.10"
    },
    @{
        Name = "1.21.10"
        Minecraft = "1.21.10"
        Mappings = "1.21.10+build.3"
        Fabric = "0.138.4+1.21.10"
        Range = ">=1.21.10 <1.21.11"
    },
    @{
        Name = "1.21.11"
        Minecraft = "1.21.11"
        Mappings = "1.21.11+build.6"
        Fabric = "0.141.5+1.21.11"
        Range = ">=1.21.11 <1.22"
    }
)

if (Test-Path -LiteralPath $work) {
    [System.IO.Directory]::Delete($work, $true)
}
New-Item -ItemType Directory -Force $innerDirectory, $staging | Out-Null

foreach ($variant in $variants) {
    Write-Host "Building RankBoard for Minecraft $($variant.Minecraft)..."
    $arguments = @(
        "clean", "build", "--no-daemon",
        "-Pminecraft_version=$($variant.Minecraft)",
        "-Pmapping_type=yarn",
        "-Pyarn_mappings=$($variant.Mappings)",
        "-Pminecraft_dependency=$($variant.Range)",
        "-Ploader_version=0.15.11",
        "-Pfabric_version=$($variant.Fabric)"
    )
    & $gradle @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Minecraft $($variant.Minecraft) build failed with exit code $LASTEXITCODE."
    }

    $artifact = Get-ChildItem (Join-Path $projectRoot "build/libs") -Filter "*.jar" |
        Where-Object { $_.Name -notlike "*sources*" } |
        Select-Object -First 1
    if ($null -eq $artifact) {
        throw "No distributable JAR was produced for Minecraft $($variant.Minecraft)."
    }

    $innerName = "rankboard-mc$($variant.Name)-$modVersion.jar"
    $innerPath = Join-Path $innerDirectory $innerName
    Copy-Item -LiteralPath $artifact.FullName -Destination $innerPath

}

Copy-Item -LiteralPath (Join-Path $projectRoot "LICENSE") -Destination (Join-Path $staging "LICENSE")
Copy-Item -LiteralPath (Join-Path $work "META-INF") -Destination (Join-Path $staging "META-INF") -Recurse

$wrapperManifest = [ordered]@{
    schemaVersion = 1
    id = "rankboard_wrapper"
    version = "$modVersion+mc1.21.x+$timestamp"
    name = "RankBoard 1.21.x Wrapper"
    description = "Selects the compatible RankBoard build for Minecraft 1.21.x."
    environment = "*"
    entrypoints = @{}
    depends = [ordered]@{
        fabricloader = ">=0.15.11"
        minecraft = ">=1.21 <1.22"
        java = ">=21"
    }
    jars = @($variants | ForEach-Object {
        [ordered]@{ file = "META-INF/jars/rankboard-mc$($_.Name)-$modVersion.jar" }
    })
}
$manifestJson = $wrapperManifest | ConvertTo-Json -Depth 8
[System.IO.File]::WriteAllText(
    (Join-Path $staging "fabric.mod.json"),
    $manifestJson,
    [System.Text.UTF8Encoding]::new($false)
)

$outputRoot = Join-Path $projectRoot $OutputDirectory
New-Item -ItemType Directory -Force $outputRoot | Out-Null
$output = Join-Path $outputRoot "rankboard-$modVersion+mc1.21.x+$timestamp.jar"
if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Force }
$outputArchive = [System.IO.Compression.ZipFile]::Open(
    $output,
    [System.IO.Compression.ZipArchiveMode]::Create
)
try {
    foreach ($file in Get-ChildItem -LiteralPath $staging -File -Recurse) {
        $relative = $file.FullName.Substring($staging.Length).TrimStart("\", "/").Replace("\", "/")
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $outputArchive,
            $file.FullName,
            $relative,
            [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
} finally {
    $outputArchive.Dispose()
}

Write-Host "Universal wrapper created: $output"
Get-FileHash -LiteralPath $output -Algorithm SHA256
