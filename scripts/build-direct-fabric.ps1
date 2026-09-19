param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('1.21.x', '26.1.x', '26.2', '26.3')]
    [string]$Target,
    [string]$OutputDirectory = 'build/libs',
    [string]$ModVersion = ''
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$gradle = if ($IsWindows) { Join-Path $projectRoot 'gradlew.bat' } else { Join-Path $projectRoot 'gradlew' }

$properties = Get-Content (Join-Path $projectRoot 'gradle.properties') |
    Where-Object { $_ -match '^[^#=]+=.*$' } |
    ForEach-Object {
        $parts = $_ -split '=', 2
        @{ Key = $parts[0].Trim(); Value = $parts[1].Trim() }
    }
$modVersion = if ([string]::IsNullOrWhiteSpace($ModVersion)) {
    ($properties | Where-Object { $_.Key -eq 'mod_version' }).Value
} else { $ModVersion }

$targets = @{
    '1.21.x' = @{
        Minecraft = '1.21.11'; MappingType = 'yarn'; YarnMappings = '1.21.11+build.6'
        Loader = '0.16.10'; Fabric = '0.141.5+1.21.11'; Dependency = '>=1.21 <1.22'
        Java = 21
    }
    '26.1.x' = @{
        Minecraft = '26.1.2'; MappingType = 'none'; YarnMappings = ''
        Loader = '0.19.3'; Fabric = '0.155.2+26.1.2'; Dependency = '>=26.1 <26.2'
        Java = 25
    }
    '26.2' = @{
        Minecraft = '26.2'; MappingType = 'none'; YarnMappings = ''
        Loader = '0.19.3'; Fabric = '0.157.0+26.2'; Dependency = '26.2'
        Java = 25; Metadata = 'https://piston-meta.mojang.com/v1/packages/2ee1d19a4f344bcc84c0499ba332b98f6441e6ff/26.2.json'
    }
    '26.3' = @{
        Minecraft = '26.3'; MappingType = 'none'; YarnMappings = ''
        Loader = '0.19.5'; Fabric = '0.160.5+26.3'; Dependency = '26.3'
        Java = 25
    }
}
$config = $targets[$Target]

$arguments = @(
    'clean', 'build', '--no-daemon',
    "-Pminecraft_version=$($config.Minecraft)",
    "-Pmapping_type=$($config.MappingType)",
    "-Ploader_version=$($config.Loader)",
    "-Pfabric_version=$($config.Fabric)",
    "-Pminecraft_dependency=$($config.Dependency)",
    "-Pmod_version=$modVersion"
)
if ($config.YarnMappings) { $arguments += "-Pyarn_mappings=$($config.YarnMappings)" }
if ($config.Metadata) { $arguments += "-Ploom_custom_metadata=$($config.Metadata)" }

Write-Host "Building one direct Fabric JAR for $Target (compile target $($config.Minecraft))"
& $gradle @arguments
if ($LASTEXITCODE -ne 0) { throw "Fabric build failed for $Target with exit code $LASTEXITCODE." }

$artifact = Get-ChildItem (Join-Path $projectRoot 'build/libs') -Filter 'rankboard-*.jar' |
    Where-Object { $_.Name -notlike '*-sources.jar' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $artifact) { throw "No distributable Fabric JAR was produced for $Target." }

$outputRoot = Join-Path $projectRoot $OutputDirectory
New-Item -ItemType Directory -Force $outputRoot | Out-Null
$output = Join-Path $outputRoot "rankboard-$modVersion+fabric+mc$Target.jar"
if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Force }
Copy-Item -LiteralPath $artifact.FullName -Destination $output -Force
$hash = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  $([IO.Path]::GetFileName($output))" | Set-Content -LiteralPath ($output -replace '\.jar$', '.sha256') -Encoding ascii
Write-Host "Direct Fabric artifact: $output"
