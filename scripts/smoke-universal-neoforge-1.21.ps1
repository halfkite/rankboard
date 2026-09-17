param(
    [string]$UniversalJar = "",
    [int]$StartIndex = 0,
    [int]$EndIndex = -1
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$neoForgeDirectory = Join-Path $repoRoot 'neoforge'
if ([string]::IsNullOrWhiteSpace($UniversalJar)) {
    $UniversalJar = Join-Path $neoForgeDirectory 'build/libs/rankboard-1.10.4+neoforge+mc1.21.x-universal.jar'
}

$versions = @(
    @{ Minecraft = '1.21'; NeoForge = '21.0.167' },
    @{ Minecraft = '1.21.2'; NeoForge = '21.2.1-beta' },
    # 21.3.96 is the last stable 1.21.3 toolchain; 21.3.97 has a broken
    # NeoForm recompile for the official 1.21.3 sources on current JDKs.
    @{ Minecraft = '1.21.3'; NeoForge = '21.3.96' },
    @{ Minecraft = '1.21.5'; NeoForge = '21.5.98' },
    @{ Minecraft = '1.21.6'; NeoForge = '21.6.20-beta' },
    @{ Minecraft = '1.21.7'; NeoForge = '21.7.25-beta' },
    @{ Minecraft = '1.21.9'; NeoForge = '21.9.16-beta' },
    @{ Minecraft = '1.21.10'; NeoForge = '21.10.64' }
)
if ($StartIndex -gt 0) {
    $versions = @($versions[$StartIndex..($versions.Count - 1)])
}
if ($EndIndex -ge 0) {
    $versions = @($versions[0..([Math]::Min($EndIndex, $versions.Count - 1))])
}

$runDirectory = Join-Path $neoForgeDirectory 'build/run-server'
$modsDirectory = Join-Path $runDirectory 'mods'
New-Item -ItemType Directory -Force $modsDirectory | Out-Null
Get-ChildItem $modsDirectory -File | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
Copy-Item $UniversalJar (Join-Path $modsDirectory 'rankboard-universal.jar') -Force
$webConfig = Join-Path $runDirectory 'config/rankboard/rankboard-web.properties'
if (Test-Path $webConfig) {
    $webText = Get-Content $webConfig -Raw
    $webText = [regex]::Replace($webText, '(?m)^web-enabled=.*$', 'web-enabled=false')
    $webText | Set-Content -Path $webConfig -Encoding utf8
}

Push-Location $neoForgeDirectory
try {
    foreach ($version in $versions) {
        $mc = $version.Minecraft
        $log = Join-Path $repoRoot (".tmp/smoke-neoforge-$($mc -replace '\.', '_').log")
        $props = @(
            'runServer',
            '-Puniversal_smoke=true',
            "-Pminecraft_version=$mc",
            '-Pminecraft_version_range=[1.21,1.22)',
            "-Pneo_version=$($version.NeoForge)",
            "-Pparchment_minecraft_version=$mc",
            '-Pparchment_mappings_version=none',
            '--no-daemon'
        )
        $properties = Join-Path $runDirectory 'server.properties'
        if (Test-Path $properties) {
            $serverText = Get-Content $properties -Raw
        } else {
            $serverText = "eula=true`nonline-mode=false`nserver-port=25565`n"
        }
        # Avoid colliding with a user's local Minecraft server during smoke runs.
        if ($serverText -match '(?m)^server-port=') {
            $serverText = [regex]::Replace($serverText, '(?m)^server-port=.*$', 'server-port=25675')
        } else {
            $serverText += "`nserver-port=25675`n"
        }
        if ($serverText -match '(?m)^level-name=') {
            $serverText = [regex]::Replace($serverText, '(?m)^level-name=.*$', "level-name=universal-smoke-$mc")
        } else {
            $serverText += "`nlevel-name=universal-smoke-$mc`n"
        }
        $serverText | Set-Content -Path $properties -Encoding utf8

        Write-Host "Smoke testing NeoForge Minecraft $mc ($($version.NeoForge))"
        $proc = Start-Process -FilePath $env:ComSpec `
            -ArgumentList @('/c', "..\gradlew.bat $($props -join ' ')") `
            -WorkingDirectory $neoForgeDirectory -RedirectStandardOutput $log `
            -RedirectStandardError ($log + '.err') -PassThru
        # NeoForge's first-run NeoForm recompile can take several minutes on a
        # clean minor-version cache; leave enough time for world generation too.
        $deadline = (Get-Date).AddMinutes(8)
        $done = $false
        while ((Get-Date) -lt $deadline -and -not $done) {
            Start-Sleep -Seconds 3
            if (Test-Path $log) {
                $done = Select-String -Path $log -Pattern 'Done \(' -Quiet
                if (Select-String -Path $log -Pattern 'Exception|ERROR|FAILED' -Quiet) {
                    # Do not stop at harmless command-ambiguity warnings; only a
                    # fatal marker before Done is a failed startup.
                    if (-not $done -and (Select-String -Path $log -Pattern 'FATAL|BUILD FAILED|NoClassDefFoundError' -Quiet)) {
                        break
                    }
                }
            }
        }

        $javaProcesses = Get-CimInstance Win32_Process -Filter "name='java.exe'" |
            Where-Object { $_.CommandLine -match "fml.mcVersion.*$mc|minecraft.*$mc" }
        $javaProcesses | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        if (-not $done) { throw "Smoke test failed or timed out for Minecraft $mc; see $log" }
        Write-Host "PASS $mc"
    }
} finally {
    Pop-Location
}
