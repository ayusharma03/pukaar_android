// Before `npm start`: make sure the emulator and dashboard ports are free, and explain what to do
// if not (usually an earlier `npm start` is still running in another terminal).
import net from 'node:net';

const PORTS = [
  [4000, 'Emulator UI'],
  [8080, 'Firestore emulator'],
  [9099, 'Auth emulator'],
  [5001, 'Functions emulator'],
  [5173, 'Dashboard (Vite)'],
];

const inUse = (port) =>
  new Promise((resolve) => {
    const server = net.createServer();
    server.once('error', () => resolve(true));
    server.once('listening', () => server.close(() => resolve(false)));
    server.listen(port, '127.0.0.1');
  });

const busy = [];
for (const [port, name] of PORTS) if (await inUse(port)) busy.push(`  ${port}  ${name}`);

if (busy.length) {
  console.error('These ports are already in use:');
  console.error(busy.join('\n'));
  console.error('\nPukaar is probably already running in another terminal: open http://localhost:5173,');
  console.error('or stop it there with Ctrl+C and run `npm start` again.');
  console.error('If nothing else is running, a crashed run may have left processes behind. In PowerShell:');
  console.error('  Get-NetTCPConnection -State Listen -LocalPort 4000,8080,9099,5001,5173 | Select LocalPort,OwningProcess');
  console.error('  Stop-Process -Id <OwningProcess>');
  process.exit(1);
}
