param(
    [Parameter(Mandatory = $true)][string]$ModJar,
    [string[]]$Versions = @(
        '1.21', '1.21.1', '1.21.2', '1.21.3', '1.21.4', '1.21.5',
        '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11'
    ),
    [int]$TimeoutSeconds = 180,
    [switch]$StartupOnly,
    [string]$FixtureRoot = 'wrapper-smoke'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$artifact = (Resolve-Path -LiteralPath $ModJar).Path
$testRoot = Join-Path $projectRoot ('.tmp/fabric-direct-smoke-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$java = 'C:\Program Files\Java\jdk-25.0.3\bin\java.exe'
$node = 'C:\Program Files\nodejs\node.exe'
$joinClient = Join-Path $projectRoot '.tmp/mc-join/join-smoke.js'
$results = [System.Collections.Generic.List[object]]::new()

if (-not (Test-Path -LiteralPath $java)) { throw "Java runtime not found: $java" }
if (-not (Test-Path -LiteralPath $node)) { throw "Node.js runtime not found: $node" }
if (-not (Test-Path -LiteralPath $joinClient)) { throw "Join smoke client not found: $joinClient" }
New-Item -ItemType Directory -Path $testRoot | Out-Null

for ($index = 0; $index -lt $Versions.Count; $index++) {
    $version = $Versions[$index]
    $source = Join-Path (Resolve-Path -LiteralPath (Join-Path $projectRoot $FixtureRoot)).Path $version
    if (-not (Test-Path -LiteralPath $source)) { throw "Cached server fixture is missing: $source" }
    $testDirectory = Join-Path $testRoot $version
    $mods = Join-Path $testDirectory 'mods'
    New-Item -ItemType Directory -Path $testDirectory | Out-Null
    New-Item -ItemType Directory -Path $mods | Out-Null

    foreach ($file in @('server.jar', 'fabric-server-launch.jar')) {
        $sourceFile = Join-Path $source $file
        if (-not (Test-Path -LiteralPath $sourceFile)) { throw "Cached server file is missing: $sourceFile" }
        New-Item -ItemType HardLink -Path (Join-Path $testDirectory $file) -Target $sourceFile | Out-Null
    }
    $apiSource = Join-Path $source 'mods/fabric-api.jar'
    if (-not (Test-Path -LiteralPath $apiSource)) { throw "Cached Fabric API is missing: $apiSource" }
    New-Item -ItemType HardLink -Path (Join-Path $mods 'fabric-api.jar') -Target $apiSource | Out-Null
    Copy-Item -LiteralPath $artifact -Destination (Join-Path $mods 'rankboard.jar')

    foreach ($directoryName in @('libraries', 'versions', '.fabric')) {
        $sourceDirectory = Join-Path $source $directoryName
        if (Test-Path -LiteralPath $sourceDirectory) {
            New-Item -ItemType Junction -Path (Join-Path $testDirectory $directoryName) `
                -Target $sourceDirectory | Out-Null
        }
    }

    $serverPort = 25700 + $index
    $webPort = 28700 + $index
    # minecraft-protocol does not carry metadata for every point release. Use a
    # client build with the same protocol number; the server under test remains
    # the exact version represented by this fixture.
    $joinVersion = switch ($version) {
        '1.21' { '1.21.1'; break }
        '1.21.2' { '1.21.3'; break }
        '1.21.7' { '1.21.8'; break }
        '1.21.10' { '1.21.9'; break }
        '26.1.1' { '26.1'; break }
        '26.1.2' { '26.1'; break }
        default { $version }
    }
    $encoding = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText((Join-Path $testDirectory 'eula.txt'), "eula=true`n", $encoding)
    [System.IO.File]::WriteAllText((Join-Path $testDirectory 'server.properties'), @"
server-port=$serverPort
online-mode=false
enable-query=false
enable-rcon=false
level-name=world
max-tick-time=60000
view-distance=2
simulation-distance=2
spawn-protection=0
pause-when-empty-seconds=0
"@, $encoding)
    $configDirectory = Join-Path $testDirectory 'config/rankboard'
    New-Item -ItemType Directory -Path $configDirectory -Force | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $configDirectory 'rankboard-web.properties'),
        "host=127.0.0.1`nport=$webPort`n", $encoding)

    $stdout = Join-Path $testDirectory 'stdout.log'
    $stderr = Join-Path $testDirectory 'stderr.log'
    $process = $null
    $ready = $false
    $joined = $false
    $failure = ''
    $dashboardHttp = $false
    try {
        $process = Start-Process -FilePath $java `
            -ArgumentList @('-Xms512M', '-Xmx1G', '-jar', 'fabric-server-launch.jar', 'nogui') `
            -WorkingDirectory $testDirectory `
            -RedirectStandardOutput $stdout `
            -RedirectStandardError $stderr `
            -WindowStyle Hidden -PassThru
        $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
        $latestLog = Join-Path $testDirectory 'logs/latest.log'
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 2
            if (Test-Path -LiteralPath $latestLog) {
                $logText = Get-Content -Raw -LiteralPath $latestLog
                if ($logText -match 'Done \(') { $ready = $true; break }
                if ($logText -match 'Incompatible mods found|Mod discovery failed|Failed to start the minecraft server|Exception in server tick loop|NoClassDefFoundError|NoSuchMethodError|FAILED TO BIND') {
                    $failure = 'Server startup log contains an error.'
                    break
                }
            }
            if ($process.HasExited) { $failure = "Server exited with code $($process.ExitCode)."; break }
        }
        if (-not $ready -and -not $failure) { $failure = 'Server did not become ready before timeout.' }
        if ($ready) {
            try {
                $response = Invoke-WebRequest -Uri "http://127.0.0.1:$webPort/" -TimeoutSec 8 -UseBasicParsing
                $dashboardHttp = $response.StatusCode -eq 200 -and $response.Content.Contains('root')
            } catch { $failure = "Dashboard HTTP smoke failed: $($_.Exception.Message)" }
            if (-not $dashboardHttp -and -not $failure) { $failure = 'Dashboard did not return the expected page.' }
        }
        if ($ready -and -not $StartupOnly -and -not $failure) {
            $joinOutput = & $node $joinClient $serverPort $joinVersion 2>&1 | Out-String
            $joined = $LASTEXITCODE -eq 0 -and $joinOutput.Contains('JOIN_OK')
            if (-not $joined) { $failure = "Offline player join failed: $joinOutput" }
            $logText = Get-Content -Raw -LiteralPath $latestLog
            if ($logText -notmatch 'RankBoard initialized') { $failure = 'RankBoard did not initialize.' }
            if ($logText -match 'NoClassDefFoundError|NoSuchMethodError|Exception in server tick loop') {
                $failure = 'Server log contains a Minecraft linkage/runtime exception.'
            }
        }
    } finally {
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            $process.WaitForExit()
        }
    }

    $results.Add([pscustomobject]@{
        Minecraft = $version
        ServerReady = $ready
        DashboardHttp = $dashboardHttp
        PlayerJoined = $joined
        Result = if ($failure) { $failure } elseif ($StartupOnly) { 'PASS (server startup only)' } else { 'PASS' }
        TestDirectory = $testDirectory
    })
    $joinResult = if ($StartupOnly) { 'not-tested' } else { [string]$joined }
    Write-Host "$version server-ready=$ready web-http=$dashboardHttp join=$joinResult result=$(if($failure){$failure}elseif($StartupOnly){'PASS (server startup only)'}else{'PASS'})"
}

$results | Format-Table -AutoSize
Write-Host "Logs and isolated worlds: $testRoot"
if ($results | Where-Object { $_.Result -ne 'PASS' }) { exit 1 }
