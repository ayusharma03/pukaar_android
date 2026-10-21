// Makes sure the Functions emulator has server keys: writes server/functions/.secret.local
// (gitignored) with a fresh key pair the first time, and prints the public keys an Android debug
// build needs to trust this local server.
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const functionsDir = fileURLToPath(new URL('../../server/functions/', import.meta.url));
const secretFile = functionsDir + '.secret.local';

if (!existsSync(functionsDir + 'node_modules')) {
  console.error('server/functions has no node_modules. Run: npm --prefix ../server/functions install');
  process.exit(1);
}

const pk = require(functionsDir + 'src/pukaar-crypto.js');

if (!existsSync(secretFile)) {
  const k = pk.keygen();
  writeFileSync(
    secretFile,
    [
      '# Local emulator keys only (made by pukaar_web/scripts/local-secrets.mjs). Never use in production.',
      `SIGN_PRIVATE_KEY=${k.SIGN_PRIVATE_KEY}`,
      `BOX_PRIVATE_KEY=${k.BOX_PRIVATE_KEY}`,
      'TWILIO_ACCOUNT_SID=',
      'TWILIO_AUTH_TOKEN=',
      '',
    ].join('\n'),
  );
  console.log('Wrote server/functions/.secret.local with new local keys.');
}

const secrets = Object.fromEntries(
  readFileSync(secretFile, 'utf8')
    .split(/\r?\n/)
    .filter((l) => l && !l.startsWith('#') && l.includes('='))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]),
);
const signPub = pk.b64u(pk.rawPublic(pk.edPrivate(pk.unb64u(secrets.SIGN_PRIVATE_KEY))));
const boxPub = pk.b64u(pk.rawPublic(pk.xPrivate(pk.unb64u(secrets.BOX_PRIVATE_KEY))));
console.log('Android debug build against this emulator:');
console.log(`  PUKAAR_SERVER_SIGN_KEY=${signPub}`);
console.log(`  PUKAAR_SERVER_BOX_KEY=${boxPub}`);
