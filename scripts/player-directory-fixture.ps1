function Initialize-DirectoryRegressionFixture([string]$TestDirectory, [string]$GameVersion) {
    $players = @(
        @{ name='AllowedSmoke'; uuid='00000000-0000-4000-8000-000000000001'; expiresOn='2099-01-01 00:00:00 +0000' },
        @{ name='DeniedSmoke'; uuid='00000000-0000-4000-8000-000000000002'; expiresOn='2099-01-01 00:00:00 +0000' }
    )
    $relativeStats = if ([version]$GameVersion -ge [version]'26.2') { 'world/players/stats' } else { 'world/stats' }
    $stats = Join-Path $TestDirectory $relativeStats
    New-Item -ItemType Directory -Path $stats -Force | Out-Null
    $players | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $TestDirectory 'usercache.json') -Encoding utf8
    ConvertTo-Json -InputObject @(@{ name=$players[0].name; uuid=$players[0].uuid }) |
        Set-Content -LiteralPath (Join-Path $TestDirectory 'whitelist.json') -Encoding utf8
    foreach ($player in $players) {
        @{ stats=@{ 'minecraft:custom'=@{ 'minecraft:play_time'=72000 } } } | ConvertTo-Json -Depth 5 |
            Set-Content -LiteralPath (Join-Path $stats ($player.uuid + '.json')) -Encoding utf8
    }
}
