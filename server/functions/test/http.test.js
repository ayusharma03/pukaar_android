'use strict';

// End-to-end over HTTP, shaped exactly like the app's Gateway.kt requests.
const test = require('node:test');
const assert = require('node:assert/strict');
const pk = require('../src/pukaar-crypto');
const { createService } = require('../src/core');
const { memoryStore } = require('../src/stores');
const { createApp } = require('../src/http');
const { fakeSms } = require('../src/sms');

const keys = { signPrivate: pk.b64u(Buffer.alloc(32, 1)), boxPrivate: pk.b64u(Buffer.alloc(32, 2)) };
const boxPub = pk.b64u(pk.rawPublic(pk.xPrivate(Buffer.alloc(32, 2))));

async function withServer(fn) {
  const sms = fakeSms();
  const service = createService({ store: memoryStore(), sms, keys });
  const app = createApp(service, { verifyResponder: async (req) => (req.get('Authorization') === 'Bearer ok' ? { uid: 'u', name: 'Control' } : null) });
  const server = app.listen(0);
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    await fn(base, sms);
  } finally {
    server.close();
  }
}

const json = (body, headers = {}) => ({ method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) });

test('gateway flow: SOS, contacts, status, responder update', () => withServer(async (base, sms) => {
  let res = await fetch(`${base}/v1/sos`, json({ id: 'a1b2c3d4', seq: 1, lat: 25.98, lon: 85.67, time: 1790000000, battery: 30, people: 2, flags: ['Injured'], name: 'Ramesh', message: '', via: 'mesh', relayedBy: 'p1' }));
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { status: 'new', by: '', smsSent: false });

  res = await fetch(`${base}/v1/contacts`, json({ id: 'a1b2c3d4', encrypted: true, data: pk.sealContacts({ phone: '9845012345', contacts: [{ name: 'Sita', phone: '9931044821' }] }, boxPub) }));
  assert.deepEqual(await res.json(), { smsSent: true });
  assert.equal(sms.sent[0].to, '+919931044821');

  res = await fetch(`${base}/v1/admin/sos/a1b2c3d4/status`, json({ status: 'attended' }));
  assert.equal(res.status, 403, 'responders only');
  res = await fetch(`${base}/v1/admin/sos/a1b2c3d4/status`, json({ status: 'attended' }, { Authorization: 'Bearer ok' }));
  assert.equal(res.status, 200);

  res = await fetch(`${base}/v1/sos/status?ids=a1b2c3d4`);
  const { statuses } = await res.json();
  assert.equal(statuses[0].status, 'attended');
  assert.equal(statuses[0].by, 'Control');
  assert.equal(statuses[0].smsSent, true);
  assert.ok(statuses[0].sig.length > 80);
}));

test('broadcasts need a responder and come back signed', () => withServer(async (base) => {
  let res = await fetch(`${base}/v1/admin/broadcasts`, json({ text: 'Boats at 4 pm' }, { Authorization: 'Bearer ok' }));
  const item = await res.json();
  assert.equal(item.from, 'Control');
  res = await fetch(`${base}/v1/broadcasts?since=0`);
  const { items } = await res.json();
  assert.equal(items[0].text, 'Boats at 4 pm');
  assert.ok(items[0].sig);
}));

test('messages and bad requests', () => withServer(async (base) => {
  let res = await fetch(`${base}/v1/messages`, json({ messages: [{ id: 'm1', sender: 'A', text: 'hi', time: 1 }, { id: 'm1', sender: 'A', text: 'hi', time: 1 }] }));
  assert.deepEqual(await res.json(), { stored: 2 });
  res = await fetch(`${base}/v1/sos`, json({ id: 'bad id!', seq: 1 }));
  assert.equal(res.status, 400);
}));
