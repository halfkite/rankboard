param(
    [Parameter(Mandatory = $true)][string]$ModJar,
    [string[]]$Versions = @(
        '1.21', '1.21.1', '1.21.2', '1.21.3', '1.21.4', '1.21.5',
        '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11'
    ),
    [int]$TimeoutSeconds = 420,
    [switch]$StartupOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$neoForgeProject = Join-Path $projectRoot 'neoforge'
$artifact = (Resolve-Path -LiteralPath $ModJar).Path
$gradle = Join-Path $projectRoot 'gradlew.bat'
$node = 'C:\Program Files\nodejs\node.exe'
$joinClient = Join-Path $projectRoot '.tmp/mc-join/join-smoke.js'
$java21 = Join-Path $projectRoot '.tmp/toolchain-temurin21/jdk-21.0.12.1+1'
$javaHomesArgument = if (Test-Path -LiteralPath $java21) { "-Dorg.gradle.java.installations.paths=$java21" } else { '' }
$testRoot = Join-Path $projectRoot ('.tmp/neoforge-direct-smoke-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$neoVersions = @{
    '1.21' = '21.0.167'; '1.21.1' = '21.1.235'; '1.21.2' = '21.2.1-beta'
    '1.21.3' = '21.3.96'; '1.21.4' = '21.4.157'; '1.21.5' = '21.5.98'
    '1.21.6' = '21.6.20-beta'; '1.21.7' = '21.7.25-beta'; '1.21.8' = '21.8.54'
    '1.21.9' = '21.9.16-beta'; '1.21.10' = '21.10.64'; '1.21.11' = '21.11.44'
    '26.1.2' = '26.1.2.94'; '26.2' = '26.2.0.41-beta'; '26.3' = '26.3.0.1-beta'
}

if (-not (Test-Path -LiteralPath $env:JAVA_HOME)) { throw 'JAVA_HOME must point to the required JDK for NeoForge smoke tests.' }
if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) { throw "JDK not found under JAVA_HOME: $env:JAVA_HOME" }
if (-not (Test-Path -LiteralPath $node)) { throw "Node.js not found: $node" }
if (-not (Test-Path -LiteralPath $joinClient)) { throw "Join smoke client not found: $joinClient" }
New-Item -ItemType Directory -Path $testRoot | Out-Null
$results = [System.Collections.Generic.List[object]]::new()

for ($index = 0; $index -lt $Versions.Count; $index++) {
    $version = $Versions[$index]
    if (-not $neoVersions.ContainsKey($version)) { throw "No cached NeoForge version mapping is configured for $version" }
    $testDirectory = Join-Path $testRoot $version
    $mods = Join-Path $testDirectory 'mods'
    $configDirectory = Join-Path $testDirectory 'config/rankboard'
    New-Item -ItemType Directory -Force -Path $mods,$configDirectory | Out-Null
    Copy-Item -LiteralPath $artifact -Destination (Join-Path $mods 'rankboard.jar') -Force

    $serverPort = 25800 + $index
    $webPort = 28800 + $index
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
    [System.IO.File]::WriteAllText((Join-Path $configDirectory 'rankboard-web.properties'), "host=127.0.0.1`nport=$webPort`n", $encoding)

    $joinVersion = switch ($version) {
        '1.21' { '1.21.1'; break }
        '1.21.2' { '1.21.3'; break }
        '1.21.7' { '1.21.8'; break }
        '1.21.10' { '1.21.9'; break }
        '26.1.2' { '26.1'; break }
        default { $version }
    }
    $stdout = Join-Path $testDirectory 'gradle.stdout.log'
    $stderr = Join-Path $testDirectory 'gradle.stderr.log'
    $gameLog = Join-Path $testDirectory 'logs/latest.log'
    $range = if ($version.StartsWith('26.1')) { '[26.1,26.2)' } elseif ($version -eq '26.2') { '[26.2]' } elseif ($version -eq '26.3') { '[26.3]' } else { '[1.21,1.22)' }
    $dependency = if ($version.StartsWith('26.1')) { '26.1.0' } elseif ($version -eq '26.2') { '26.2.0.41-beta' } elseif ($version -eq '26.3') { '26.3.0.1-beta' } else { '21.0' }
    $command = @()
    if ($javaHomesArgument) { $command += $javaHomesArgument }
    $command += @(
        '-p', 'neoforge', 'runServer', '-Puniversal_smoke=true',
        "-Prankboard_run_directory=$testDirectory",
        "-Pminecraft_version=$version", "-Pminecraft_version_range=$range",
        "-Pneo_version=$($neoVersions[$version])", "-Pneo_dependency_version=$dependency",
        "-Pparchment_minecraft_version=$version", '-Pparchment_mappings_version=none',
        '-Pmod_version=1.10.6', '--no-daemon', '--console=plain'
    )
    $argumentLine = ($command | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }) -join ' '
    $process = $null
    $ready = $false
    $joined = $false
    $failure = ''
    $dashboardHttp = $false
    try {
        $process = Start-Process -FilePath $env:ComSpec -ArgumentList @('/c', "`"$gradle`" $argumentLine") `
            -WorkingDirectory $projectRoot -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
            -WindowStyle Hidden -PassThru
        $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 2
            if (Test-Path -LiteralPath $gameLog) {
                $text = Get-Content -Raw -LiteralPath $gameLog
                if ($text -match 'Done \(') { $ready = $true; break }
                if ($text -match 'NoClassDefFoundError|NoSuchMethodError|FATAL|FAILED TO BIND|Exception in server tick loop') {
                    $failure = 'NeoForge server log contains a startup/linkage error.'
                    break
                }
            }
            if ($process.HasExited) { $failure = "Gradle exited with code $($process.ExitCode)."; break }
        }
        if (-not $ready -and -not $failure) { $failure = 'NeoForge server did not become ready before timeout.' }
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
            $text = Get-Content -Raw -LiteralPath $gameLog
            if ($text -notmatch 'RankBoard NeoForge initialized|RankBoard initialized') { $failure = 'RankBoard did not initialize.' }
            if ($text -match 'NoClassDefFoundError|NoSuchMethodError|Exception in server tick loop|Couldn.t place player in world') {
                $failure = 'NeoForge server log contains a Minecraft linkage/runtime exception.'
            }
        }
    } finally {
        if ($null -ne $process -and -not $process.HasExited) {
            $serverProcesses = Get-CimInstance Win32_Process -Filter "name='java.exe'" |
                Where-Object { $_.CommandLine -and $_.CommandLine.Contains($testDirectory) }
            $serverProcesses | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            $process.WaitForExit()
        }
    }
    $result = if ($failure) { $failure } elseif ($StartupOnly) { 'PASS (server startup only)' } else { 'PASS' }
    $results.Add([pscustomobject]@{ Minecraft = $version; NeoForge = $neoVersions[$version]; ServerReady = $ready; DashboardHttp = $dashboardHttp; PlayerJoined = $joined; Result = $result; TestDirectory = $testDirectory })
    $joinResult = if ($StartupOnly) { 'not-tested' } else { [string]$joined }
    Write-Host "$version NeoForge=$($neoVersions[$version]) ready=$ready web-http=$dashboardHttp join=$joinResult result=$result"
}

$results | Format-Table -AutoSize
Write-Host "Isolated NeoForge smoke data: $testRoot"
if ($results | Where-Object { $_.Result -ne 'PASS' }) { exit 1 }
