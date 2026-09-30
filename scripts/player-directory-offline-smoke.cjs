// No client protocol dependency: exercise the real whitelist API using seeded stats.
const { connect } = require('./rcon-smoke.cjs');
const timeout = setTimeout(() => process.exit(1), 30000);
(async () => {
  const rcon = await connect(process.argv[2]);
  await new Promise(resolve => setTimeout(resolve, 2000));
  const all = await rcon.command('leaderboard all playtime');
  if (!all.includes('AllowedSmoke') || !all.includes('DeniedSmoke')) {
    throw new Error('Seeded offline players were not loaded');
  }
  await rcon.command('leaderboard whitelist true');
  const filtered = await rcon.command('leaderboard all playtime');
  if (!filtered.includes('AllowedSmoke') || filtered.includes('DeniedSmoke')) {
    throw new Error('Runtime whitelist filtering did not match whitelist.json');
  }
  await rcon.command('leaderboard whitelist false');
  rcon.socket.end();
  clearTimeout(timeout);
  console.log('OFFLINE_DIRECTORY_OK');
})().catch(error => { console.error(error); process.exit(1); });
