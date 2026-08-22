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
        Range = ">=1.21 <1.21.1"
    },
    @{
        Name = "1.21.1"
        Minecraft = "1.21.1"
        Mappings = "1.21.1+build.3"
        Fabric = "0.102.1+1.21.1"
        Range = ">=1.21.1 <1.21.2"
    },
    @{
        Name = "1.21.2"
        Minecraft = "1.21.2"
        Mappings = "1.21.2+build.1"
        Fabric = "0.106.1+1.21.2"
        Range = ">=1.21.2 <1.21.3"
    },
    @{
        Name = "1.21.3"
        Minecraft = "1.21.3"
        Mappings = "1.21.3+build.2"
        Fabric = "0.114.1+1.21.3"
        Range = ">=1.21.3 <1.21.4"
    },
    @{
        Name = "1.21.4"
        Minecraft = "1.21.4"
        Mappings = "1.21.4+build.8"
        Fabric = "0.119.4+1.21.4"
        Range = ">=1.21.4 <1.21.5"
    },
    @{
        Name = "1.21.5"
        Minecraft = "1.21.5"
        Mappings = "1.21.5+build.1"
        Fabric = "0.128.2+1.21.5"
        Range = ">=1.21.5 <1.21.6"
    },
    @{
        Name = "1.21.6"
        Minecraft = "1.21.6"
        Mappings = "1.21.6+build.1"
        Fabric = "0.128.2+1.21.6"
        Range = ">=1.21.6 <1.21.7"
    },
    @{
        Name = "1.21.7"
        Minecraft = "1.21.7"
        Mappings = "1.21.7+build.8"
        Fabric = "0.129.0+1.21.7"
        Range = ">=1.21.7 <1.21.8"
    },
    @{
        Name = "1.21.8"
        Minecraft = "1.21.8"
        Mappings = "1.21.8+build.1"
        Fabric = "0.136.1+1.21.8"
        Range = ">=1.21.8 <1.21.9"
    },
    @{
        Name = "1.21.9"
        Minecraft = "1.21.9"
        Mappings = "1.21.9+build.1"
        Fabric = "0.134.1+1.21.9"
        Range = ">=1.21.9 <1.21.10"
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
        "clean", "build", "-x", "remapSourcesJar", "--no-daemon",
        "-Pminecraft_version=$($variant.Minecraft)",
        "-Pmapping_type=yarn",
        "-Pyarn_mappings=$($variant.Mappings)",
        "-Pminecraft_dependency=$($variant.Range)",
        "-Ploader_version=0.15.11",
        "-Pfabric_version=$($variant.Fabric)",
        "-Pmod_version=$modVersion"
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
