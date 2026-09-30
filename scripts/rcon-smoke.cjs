const net = require('node:net');
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

async function connect(port) {
  const socket = net.createConnection({ host: '127.0.0.1', port: Number(port) });
  await new Promise((resolve, reject) => { socket.once('connect', resolve); socket.once('error', reject); });
  const rcon = new Rcon(socket);
  await rcon.request(3, 'rankboard-smoke-only');
  return rcon;
}
module.exports = { connect };
