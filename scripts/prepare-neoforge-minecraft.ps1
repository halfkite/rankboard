param(
    [Parameter(Mandatory = $true)]
    [string]$MinecraftVersion
)

$ErrorActionPreference = 'Stop'
$cacheRoot = Join-Path $env:USERPROFILE '.gradle\caches\neoformruntime\artifacts'
$assetRoot = Join-Path $env:USERPROFILE '.gradle\caches\neoformruntime\assets\indexes'
New-Item -ItemType Directory -Force -Path $cacheRoot, $assetRoot | Out-Null

function Test-Sha1([string]$Path, [string]$Expected) {
    return (Test-Path -LiteralPath $Path) -and
        ((Get-FileHash -LiteralPath $Path -Algorithm SHA1).Hash -eq $Expected.ToUpperInvariant())
}

function Get-VerifiedFile([string]$Url, [string]$Path, [string]$Sha1) {
    if (Test-Sha1 $Path $Sha1) { return }
    $temporary = "$Path.manual"
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
    & curl.exe -L --fail --silent --show-error --retry 5 --retry-delay 2 --output $temporary $Url
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
    if (-not (Test-Sha1 $temporary $Sha1)) { throw "SHA-1 mismatch: $temporary" }
    Move-Item -LiteralPath $temporary -Destination $Path -Force
}

$launcherPath = Join-Path $cacheRoot 'minecraft_launcher_manifest.json'
& curl.exe -L --fail --silent --show-error --retry 5 --output $launcherPath `
    'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'
if ($LASTEXITCODE -ne 0) { throw 'Could not download the Minecraft launcher manifest.' }
$launcher = Get-Content -LiteralPath $launcherPath -Raw | ConvertFrom-Json
$entry = $launcher.versions | Where-Object id -eq $MinecraftVersion | Select-Object -First 1
if ($null -eq $entry) { throw "Minecraft version not found: $MinecraftVersion" }

$versionPath = Join-Path $cacheRoot "minecraft_${MinecraftVersion}_version_manifest.json"
Get-VerifiedFile $entry.url $versionPath $entry.sha1
$version = Get-Content -LiteralPath $versionPath -Raw | ConvertFrom-Json

$downloads = @(
    [pscustomobject]@{ Name = 'client'; Info = $version.downloads.client },
    [pscustomobject]@{ Name = 'server'; Info = $version.downloads.server },
    [pscustomobject]@{ Name = 'client_mappings'; Info = $version.downloads.client_mappings }
)

$pending = @()
foreach ($download in $downloads) {
    if ($null -eq $download.Info) { continue }
    $extension = if ($download.Name -eq 'client_mappings') { '.txt' } else { '.jar' }
    $path = Join-Path $cacheRoot "minecraft_${MinecraftVersion}_$($download.Name)$extension"
    if (Test-Sha1 $path $download.Info.sha1) { continue }
    $temporary = "$path.manual"
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
    $stdout = "$temporary.stdout.log"
    $stderr = "$temporary.stderr.log"
    $process = Start-Process -FilePath 'curl.exe' -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
        -ArgumentList '-L', '--fail', '--silent', '--show-error', '--retry', '5', '--retry-delay', '2',
            '--output', $temporary, $download.Info.url
    $pending += [pscustomobject]@{
        Process = $process; Path = $path; Temporary = $temporary; Sha1 = $download.Info.sha1;
        Stdout = $stdout; Stderr = $stderr
    }
}

if ($pending.Count -gt 0) {
    $pending.Process | Wait-Process
}
foreach ($item in $pending) {
    $item.Process.Refresh()
    if ($item.Process.ExitCode -ne 0) {
        $details = Get-Content -LiteralPath $item.Stderr -Raw -ErrorAction SilentlyContinue
        throw "Download failed for $($item.Path): $details"
    }
    if (-not (Test-Sha1 $item.Temporary $item.Sha1)) { throw "SHA-1 mismatch: $($item.Temporary)" }
    Move-Item -LiteralPath $item.Temporary -Destination $item.Path -Force
    Remove-Item -LiteralPath $item.Stdout, $item.Stderr -Force -ErrorAction SilentlyContinue
}

$assetPath = Join-Path $assetRoot "$($version.assetIndex.id).json"
Get-VerifiedFile $version.assetIndex.url $assetPath $version.assetIndex.sha1

[pscustomobject]@{
    Minecraft = $MinecraftVersion
    AssetIndex = $version.assetIndex.id
    Client = $version.downloads.client.sha1
    Server = $version.downloads.server.sha1
    ClientMappings = $version.downloads.client_mappings.sha1
} | ConvertTo-Json -Compress
