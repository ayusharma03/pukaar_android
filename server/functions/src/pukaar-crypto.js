// Reference crypto for the Pukaar rescuer server (Node 18+, no dependencies).
// Matches app/src/main/java/app/pukaar/sos/ServerCrypto.kt. See docs/protocol.md §3.
//
//   node pukaar-crypto.js keygen   -> prints new server keys (keep the private ones secret)
//
// In a Firebase Cloud Function:
//   const pk = require('./pukaar-crypto');
//   const sig = pk.sign(pk.ackSignedText({id, status: 'N', time, smsSent: true, by}), SIGN_PRIVATE_KEY);
//   const contacts = pk.openContacts(body.data, BOX_PRIVATE_KEY);   // when body.encrypted

'use strict';
const crypto = require('crypto');

const b64u = (buf) => Buffer.from(buf).toString('base64url');
const unb64u = (s) => Buffer.from(s, 'base64url');

// PKCS#8 / SPKI wrappers so raw 32-byte keys can be used with Node's crypto.
const ED_PRIV = Buffer.from('302e020100300506032b657004220420', 'hex');
const ED_PUB = Buffer.from('302a300506032b6570032100', 'hex');
const X_PRIV = Buffer.from('302e020100300506032b656e04220420', 'hex');
const X_PUB = Buffer.from('302a300506032b656e032100', 'hex');

const edPrivate = (raw) => crypto.createPrivateKey({ key: Buffer.concat([ED_PRIV, raw]), format: 'der', type: 'pkcs8' });
const xPrivate = (raw) => crypto.createPrivateKey({ key: Buffer.concat([X_PRIV, raw]), format: 'der', type: 'pkcs8' });
const xPublic = (raw) => crypto.createPublicKey({ key: Buffer.concat([X_PUB, raw]), format: 'der', type: 'spki' });
const rawPublic = (privateKey) => crypto.createPublicKey(privateKey).export({ format: 'der', type: 'spki' }).subarray(-32);

/** Text the server signs for an ack: PKACK1|id|status|time|sms|by (by with | and newlines cleaned). */
function ackSignedText({ id, status, time, smsSent, by }) {
  return `PKACK1|${id}|${status}|${time}|${smsSent ? 1 : 0}|${clean(by || '')}`;
}

/** Text the server signs for an official broadcast: PKOFF1|id|from|text. */
function officialSignedText({ id, from, text }) {
  return `PKOFF1|${id}|${clean(from)}|${text}`;
}

/** Text the server signs for a control-room message to one SOS sender: PKMSG1|sosId|msgId|time|from|text. */
function messageSignedText({ sosId, id, time, from, text }) {
  return `PKMSG1|${sosId}|${id}|${time}|${clean(from)}|${text}`;
}

function clean(s) {
  return s.replace(/\|/g, '/').replace(/\n/g, ' ').trim();
}

/** Ed25519 signature, base64url, over the UTF-8 text. signPrivate is the raw 32-byte seed (base64url). */
function sign(text, signPrivate) {
  return b64u(crypto.sign(null, Buffer.from(text, 'utf8'), edPrivate(unb64u(signPrivate))));
}

function deriveKey(shared, ephPub, serverPub) {
  return Buffer.from(crypto.hkdfSync('sha256', shared, Buffer.concat([ephPub, serverPub]), Buffer.from('pukaar-contacts-v1'), 32));
}

/** Opens a PKCT1 sealed payload. boxPrivate is the raw 32-byte X25519 key (base64url). Returns the JSON object. */
function openContacts(data, boxPrivate) {
  const bytes = unb64u(data);
  const ephPub = bytes.subarray(0, 32);
  const ct = bytes.subarray(32, bytes.length - 16);
  const tag = bytes.subarray(bytes.length - 16);
  const priv = xPrivate(unb64u(boxPrivate));
  const serverPub = rawPublic(priv);
  const shared = crypto.diffieHellman({ privateKey: priv, publicKey: xPublic(ephPub) });
  const decipher = crypto.createDecipheriv('chacha20-poly1305', deriveKey(shared, ephPub, serverPub), Buffer.alloc(12), { authTagLength: 16 });
  decipher.setAAD(Buffer.from('PKCT1'));
  decipher.setAuthTag(tag);
  const plain = Buffer.concat([decipher.update(ct), decipher.final()]);
  return JSON.parse(plain.toString('utf8'));
}

/** Seals like the app does (for tests). ephPrivate is optional raw 32 bytes, for fixed vectors. */
function sealContacts(obj, boxPublic, ephPrivate) {
  const eph = ephPrivate ? xPrivate(ephPrivate) : crypto.generateKeyPairSync('x25519').privateKey;
  const ephPub = rawPublic(eph);
  const serverPub = unb64u(boxPublic);
  const shared = crypto.diffieHellman({ privateKey: eph, publicKey: xPublic(serverPub) });
  const cipher = crypto.createCipheriv('chacha20-poly1305', deriveKey(shared, ephPub, serverPub), Buffer.alloc(12), { authTagLength: 16 });
  cipher.setAAD(Buffer.from('PKCT1'));
  const ct = Buffer.concat([cipher.update(Buffer.from(JSON.stringify(obj), 'utf8')), cipher.final()]);
  return b64u(Buffer.concat([ephPub, ct, cipher.getAuthTag()]));
}

function keygen() {
  const signSeed = crypto.randomBytes(32);
  const boxRaw = crypto.randomBytes(32);
  return {
    PUKAAR_SERVER_SIGN_KEY: b64u(rawPublic(edPrivate(signSeed))),
    PUKAAR_SERVER_BOX_KEY: b64u(rawPublic(xPrivate(boxRaw))),
    SIGN_PRIVATE_KEY: b64u(signSeed),
    BOX_PRIVATE_KEY: b64u(boxRaw),
  };
}

module.exports = { ackSignedText, officialSignedText, messageSignedText, sign, openContacts, sealContacts, keygen, rawPublic, edPrivate, xPrivate, b64u, unb64u };

if (require.main === module && process.argv[2] === 'keygen') {
  const k = keygen();
  console.log('# Build the app with these (gradle.properties or environment):');
  console.log(`PUKAAR_SERVER_SIGN_KEY=${k.PUKAAR_SERVER_SIGN_KEY}`);
  console.log(`PUKAAR_SERVER_BOX_KEY=${k.PUKAAR_SERVER_BOX_KEY}`);
  console.log('# Server secrets only. Never commit these or put them in the app:');
  console.log(`SIGN_PRIVATE_KEY=${k.SIGN_PRIVATE_KEY}`);
  console.log(`BOX_PRIVATE_KEY=${k.BOX_PRIVATE_KEY}`);
}
