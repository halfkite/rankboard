// Regression: enable whitelist filtering and a private board, then rejoin.
// A plain first login never exercises the restore path from the reported crash.
const path = require('node:path');
const net = require('node:net');
const minecraft = require(path.join(__dirname, '../.tmp/mc-join/node_modules/minecraft-protocol'));
const [port, version, rconPort] = process.argv.slice(2);
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const timeout = setTimeout(() => { console.error('Regression timed out'); process.exit(1); }, 60000);

class Rcon {
  constructor(socket) {
    this.socket = socket;
    this.buffer = Buffer.alloc(0);
    this.pending = new Map();
    this.id = 0;
    socket.on('data', chunk => {
      this.buffer = Buffer.concat([this.buffer, chunk]);
      while (this.buffer.length >= 4 && this.buffer.length >= this.buffer.readInt32LE(0) + 4) {
        const length = this.buffer.readInt32LE(0);
        const id = this.buffer.readInt32LE(4);
        const type = this.buffer.readInt32LE(8);
        const body = this.buffer.subarray(12, length + 2).toString();
        this.buffer = this.buffer.subarray(length + 4);
        const pending = this.pending.get(id);
        if (pending && type === pending.type) {
          this.pending.delete(id);
          pending.resolve(body);
        }
        if (id === -1) throw new Error('RCON authentication failed');
      }
    });
  }
  request(type, body) {
    return new Promise((resolve, reject) => {
      const id = ++this.id;
      const payload = Buffer.from(body);
      const packet = Buffer.alloc(payload.length + 14);
      packet.writeInt32LE(payload.length + 10, 0);
      packet.writeInt32LE(id, 4);
      packet.writeInt32LE(type, 8);
      payload.copy(packet, 12);
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error('RCON timeout')); }, 8000);
      this.pending.set(id, { type: type === 3 ? 2 : 0, resolve: value => { clearTimeout(timer); resolve(value); } });
      this.socket.write(packet);
    });
  }
  async command(command) {
    const response = await this.request(2, command);
    console.log(`COMMAND ${command}: ${response}`);
    if (/NoSuchMethod|Exception|Unknown or incomplete command|An unexpected error/.test(response)) {
      throw new Error(response);
    }
    return response;
  }
}

function join() {
  return new Promise((resolve, reject) => {
    const client = minecraft.createClient({ host: '127.0.0.1', port: Number(port), username: 'RankBoardSmoke', auth: 'offline', version });
    const session = { client, objectives: 0, ended: false, chats: [] };
    let ready = false;
    client.on('ping', packet => client.write('pong', { id: packet.id }));
    client.on('packet', (data, meta) => {
      if (meta.name === 'scoreboard_objective') session.objectives++;
      if (meta.name === 'system_chat') {
        const chat = JSON.stringify(data, (_, value) => typeof value === 'bigint' ? String(value) : value);
        session.chats.push(chat);
        console.log('CHAT ' + chat);
      }
    });
    client.on('state', state => {
      if (state === 'play' && !ready) { ready = true; resolve(session); }
    });
    client.on('error', error => {
      if (!ready) reject(error);
      else console.error('CLIENT_PACKET_WARNING: ' + error.message);
    });
    client.on('end', reason => {
      session.ended = true;
      if (!ready) reject(new Error(`Login ended: ${reason}`));
    });
  });
}

(async () => {
  const socket = net.createConnection({ host: '127.0.0.1', port: Number(rconPort) });
  await new Promise((resolve, reject) => { socket.once('connect', resolve); socket.once('error', reject); });
  const rcon = new Rcon(socket);
  await rcon.request(3, 'rankboard-smoke-only');
  const first = await join();
  await delay(2500);
  const firstChats = first.chats.join('\n');
  if (!firstChats.includes('欢迎来到') || !firstChats.includes('展开榜单信息')
      || firstChats.includes('请选择语言') || firstChats.includes('[白名单]')) {
    throw new Error('Non-OP did not receive the simplified join button');
  }
  console.log('NON_OP_COMPACT_JOIN_OK');
  await rcon.command('whitelist add RankBoardSmoke');
  await rcon.command('leaderboard whitelist true');
  await rcon.command('execute as RankBoardSmoke run leaderboard display show all playtime');
  await delay(2500);
  if (first.ended || !first.objectives) throw new Error('Private scoreboard was not sent');
  await rcon.command('save-all');
  first.client.end('regression-rejoin');
  await delay(1500);
  const second = await join();
  await delay(5000);
  if (second.ended || !second.objectives) throw new Error('Private scoreboard was not restored on rejoin');
  console.log('DIRECTORY_REJOIN_OK');
  // Whitelist rejection must remain effective, rather than accepting all players.
  await rcon.command('whitelist remove RankBoardSmoke');
  const filtered = await rcon.command('leaderboard all playtime');
  if (filtered.includes('RankBoardSmoke')) throw new Error('Removed player leaked through whitelist filter');
  console.log('WHITELIST_DENIAL_OK');
  await rcon.command('leaderboard whitelist false');
  second.client.end('smokeComplete');
  await rcon.command('op RankBoardSmoke');
  await delay(1500);
  const operator = await join();
  await delay(2500);
  const operatorChats = operator.chats.join('\n');
  if (!operatorChats.includes('请选择语言') || !operatorChats.includes('/leaderboard help') || operatorChats.includes('展开榜单信息')) {
    throw new Error('OP did not receive the full menu');
  }
  console.log('OP_FULL_JOIN_OK');
  operator.client.end('smokeComplete');
  socket.end();
  clearTimeout(timeout);
  console.log('JOIN_OK');
})().catch(error => { console.error(error); process.exit(1); });
