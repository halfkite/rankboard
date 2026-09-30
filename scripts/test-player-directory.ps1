param([string]$JavaHome = 'C:\Program Files\Java\jdk-25.0.3')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root ('.tmp/player-directory-test-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $output | Out-Null
& (Join-Path $JavaHome 'bin/javac.exe') --release 21 -d $output `
    (Join-Path $root 'src/main/java/cn/bamgdam/rankboard/PlayerDirectoryBridge.java') `
    (Join-Path $root 'tests/java/cn/bamgdam/rankboard/PlayerDirectoryBridgeTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Player directory regression test compilation failed.' }
& (Join-Path $JavaHome 'bin/java.exe') -cp $output cn.bamgdam.rankboard.PlayerDirectoryBridgeTest
if ($LASTEXITCODE -ne 0) { throw 'Player directory regression tests failed.' }
