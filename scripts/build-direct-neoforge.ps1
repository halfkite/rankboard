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
    '1.21.x' = @{ Minecraft = '1.21.11'; Range = '[1.21,1.22)'; Neo = '21.11.44'; NeoDependency = '21.0'; ParchmentMinecraft = '1.21.11'; Java = 21 }
    '26.1.x' = @{ Minecraft = '26.1.2'; Range = '[26.1,26.2)'; Neo = '26.1.2.94'; NeoDependency = '26.1.0'; ParchmentMinecraft = '26.1.2'; Java = 25 }
    '26.2' = @{ Minecraft = '26.2'; Range = '[26.2]'; Neo = '26.2.0.41-beta'; NeoDependency = '26.2.0.41-beta'; ParchmentMinecraft = '26.2'; Java = 25 }
    '26.3' = @{ Minecraft = '26.3'; Range = '[26.3]'; Neo = '26.3.0.1-beta'; NeoDependency = '26.3.0.1-beta'; ParchmentMinecraft = '26.3'; Java = 25 }
}
$config = $targets[$Target]

$arguments = @(
    '-p', 'neoforge', 'clean', 'build', '--no-daemon', '--console=plain',
    "-Pminecraft_version=$($config.Minecraft)",
    "-Pminecraft_version_range=$($config.Range)",
    "-Pneo_version=$($config.Neo)",
    "-Pneo_dependency_version=$($config.NeoDependency)",
    "-Pparchment_minecraft_version=$($config.ParchmentMinecraft)",
    '-Pparchment_mappings_version=none',
    "-Pmod_version=$modVersion"
)

Write-Host "Building one direct NeoForge JAR for $Target (compile target $($config.Minecraft))"
& $gradle @arguments
if ($LASTEXITCODE -ne 0) { throw "NeoForge build failed for $Target with exit code $LASTEXITCODE." }

$artifact = Get-ChildItem (Join-Path $projectRoot 'neoforge/build/libs') -Filter 'rankboard-*.jar' |
    Where-Object { $_.Name -notlike '*-sources.jar' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $artifact) { throw "No distributable NeoForge JAR was produced for $Target." }

$outputRoot = Join-Path $projectRoot $OutputDirectory
New-Item -ItemType Directory -Force $outputRoot | Out-Null
$output = Join-Path $outputRoot "rankboard-$modVersion+neoforge+mc$Target.jar"
if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Force }
Copy-Item -LiteralPath $artifact.FullName -Destination $output -Force
$hash = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  $([IO.Path]::GetFileName($output))" | Set-Content -LiteralPath ($output -replace '\.jar$', '.sha256') -Encoding ascii
Write-Host "Direct NeoForge artifact: $output"
