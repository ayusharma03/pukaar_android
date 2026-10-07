'use strict';

// Dashboard-facing server behaviour: who acted vs who is going, notes, messages to the person,
// undo/reopen, radio limits, broadcast reach, and the fields the dashboard shows.
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const pk = require('../src/pukaar-crypto');
const { createService } = require('../src/core');
const { memoryStore } = require('../src/stores');
const { createApp } = require('../src/http');
const { fakeSms } = require('../src/sms');
const { areaFor } = require('../src/areas');

const signSeed = Buffer.alloc(32, 1);
const keys = { signPrivate: pk.b64u(signSeed), boxPrivate: pk.b64u(Buffer.alloc(32, 2)) };
const signPub = crypto.createPublicKey({
  key: Buffer.concat([Buffer.from('302a300506032b6570032100', 'hex'), pk.rawPublic(pk.edPrivate(signSeed))]),
  format: 'der',
  type: 'spki',
});
const verify = (text, sig) => crypto.verify(null, Buffer.from(text), signPub, pk.unb64u(sig));

const sos = {
  id: 'k9wd2024', seq: 1, lat: 25.95, lon: 86.2, accuracyM: 18, time: 1790000000, battery: 11,
  people: 6, flags: ['Trapped', 'Injured'], name: 'Gita Devi', message: 'Roof of the school', via: 'mesh', relayedBy: 'gw1', hops: 3,
};
const kavita = { uid: 'uid-kavita', name: 'Kavita Rao' };

function setup() {
  const store = memoryStore();
  let t = 1790000060000;
  const service = createService({ store, sms: fakeSms(), keys, now: () => t });
  return { store, service, tick: (s) => { t += s * 1000; } };
}

test('attend records the operator separately from who is going; the phone sees who is going', async () => {
  const { service, store } = setup();
  await service.receiveSos(sos);
  const st = await service.setStatus(sos.id, 'attended', { assignee: 'NDRF team 3', note: 'Boat leaving Kiratpur ghat', actor: kavita });
  assert.equal(st.by, 'NDRF team 3');
  assert.ok(verify(pk.ackSignedText({ id: sos.id, status: 'A', time: st.time, smsSent: false, by: 'NDRF team 3' }), st.sig));

  const doc = await store.getSos(sos.id);
  assert.equal(doc.assignee, 'NDRF team 3');
  assert.deepEqual(doc.history.at(-1), { status: 'attended', time: st.time, by: 'Kavita Rao', uid: 'uid-kavita', assignee: 'NDRF team 3' });
  assert.deepEqual(doc.notes, [{ at: st.time, text: 'Boat leaving Kiratpur ghat', by: 'Kavita Rao', uid: 'uid-kavita' }]);
});

test('undo back to new clears who is going; reopen from resolved keeps the team', async () => {
  const { service, store, tick } = setup();
  await service.receiveSos(sos);
  await service.setStatus(sos.id, 'attended', { assignee: 'NDRF team 3', actor: kavita });
  tick(5);
  let st = await service.setStatus(sos.id, 'new', { actor: kavita });
  assert.equal(st.by, '');
  assert.equal((await store.getSos(sos.id)).assignee, '');

  await service.setStatus(sos.id, 'attended', { assignee: 'SDRF boat 2', actor: kavita });
  await service.setStatus(sos.id, 'resolved', { actor: kavita });
  st = await service.setStatus(sos.id, 'attended', { actor: kavita });
  assert.equal(st.by, 'SDRF boat 2');
  const doc = await store.getSos(sos.id);
  assert.deepEqual(doc.history.map((h) => h.status), ['new', 'attended', 'new', 'attended', 'resolved', 'attended']);
});

test('team notes need text and an existing SOS', async () => {
  const { service, store } = setup();
  await service.receiveSos(sos);
  const note = await service.addNote(sos.id, '  All 6 rescued  ', kavita);
  assert.equal(note.text, 'All 6 rescued');
  assert.equal((await store.getSos(sos.id)).notes.length, 1);
  await assert.rejects(service.addNote(sos.id, '   ', kavita), /text is required/);
  await assert.rejects(service.addNote('nosuch12', 'x', kavita), (e) => e.status === 404);
});

test('a message to the person is signed and rides along with the status, within 200 bytes', async () => {
  const { service } = setup();
  await service.receiveSos(sos);
  const msg = await service.messagePerson(sos.id, 'Boat coming in 20 minutes. Stay on the roof.', kavita);
  const { statuses } = await service.statuses(sos.id);
  const sent = statuses[0].messages;
  assert.equal(sent.length, 1);
  assert.equal(sent[0].text, msg.text);
  assert.equal(sent[0].from, 'Kavita Rao');
  assert.ok(verify(pk.messageSignedText({ sosId: sos.id, ...msg }), sent[0].sig));

  // Hindi is 3 bytes per letter in UTF-8: 67 letters fit, 68 don't.
  await service.messagePerson(sos.id, 'क'.repeat(66), kavita);
  await assert.rejects(service.messagePerson(sos.id, 'क'.repeat(67), kavita), /over 200 bytes/);
});

test('broadcasts over 200 bytes are refused; phones get only the signed fields; reach counts installs once', async () => {
  const { service, store } = setup();
  await assert.rejects(service.addBroadcast('Control', 'x'.repeat(201), kavita), /over 200 bytes/);
  const item = await service.addBroadcast('District Control Room', 'Relief camp open at Biraul panchayat bhawan.', kavita);

  const { items } = await service.broadcasts(0);
  assert.deepEqual(Object.keys(items[0]).sort(), ['from', 'id', 'sig', 'text', 'time']);

  await service.broadcastsSeen({ device: 'install-aaaa', ids: [item.id] });
  await service.broadcastsSeen({ device: 'install-aaaa', ids: [item.id] });
  await service.broadcastsSeen({ device: 'install-bbbb', ids: [item.id, 'unknown'] });
  const stored = store._all().broadcasts[0];
  assert.equal(stored.reach, 2);
  assert.deepEqual(stored.sentBy, { by: 'Kavita Rao', uid: 'uid-kavita' });
  await assert.rejects(service.broadcastsSeen({ device: 'x', ids: [] }), /bad device/);
});

test('SOS fields for the dashboard: arrival paths, hops, radio node, area, received time, stale fix', async () => {
  const { service, store } = setup();
  await service.receiveSos({ ...sos, locationAt: sos.time - 720 });
  await service.receiveSos({ ...sos, relayedBy: 'gw2', hops: 1 });
  await service.receiveSos({ ...sos, via: 'radio', radioNode: '!a3f2c1' });
  const doc = await store.getSos(sos.id);
  assert.deepEqual(doc.via, ['mesh', 'radio']);
  assert.equal(doc.hops, 1);
  assert.equal(doc.radioNode, '!a3f2c1');
  assert.equal(doc.receivedAt, 1790000060);
  assert.equal(doc.locationAt, undefined, 'the newest content had a fresh fix');
  assert.deepEqual(doc.area, { block: 'Kiratpur', village: '' });
  assert.equal(areaFor(28.6, 77.2), null, 'outside the district');
  assert.equal(areaFor(null, null), null);
});

test('family texts: server results name the contact; the person\'s phone can report its own', async () => {
  const { service, store } = setup();
  await service.receiveSos(sos);
  await service.receiveSmsReport({ id: sos.id, results: [{ name: 'Raju', phone: '9835122140', ok: false, time: 1790000100 }] });
  await service.receiveSmsReport({ id: sos.id, results: [{ name: 'Raju', phone: '9835122140', ok: true, time: 1790000200 }] });
  let doc = await store.getSos(sos.id);
  assert.deepEqual(doc.smsResults, [{ name: 'Raju', phone: '9835122140', ok: true, by: 'phone', at: 1790000200 }]);
  assert.equal(doc.history.filter((h) => h.status === 'sms').length, 1);

  await service.receiveSos({ ...sos, via: 'direct', contacts: [{ name: 'Savitri', phone: '9431077802' }] });
  doc = await store.getSos(sos.id);
  assert.deepEqual(doc.smsResults.map((r) => [r.name, r.by, r.ok]), [['Raju', 'phone', true], ['Savitri', 'server', true]]);
});

test('dashboard routes over HTTP: responders only, operator passed through', async () => {
  const service = createService({ store: memoryStore(), sms: fakeSms(), keys });
  const app = createApp(service, {
    verifyResponder: async (req) => (req.get('Authorization') === 'Bearer ok' ? { uid: 'u1', name: 'Kavita Rao', role: 'responder' } : null),
  });
  const server = app.listen(0);
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = (path, body, auth) => fetch(base + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(auth ? { Authorization: auth } : {}) },
    body: JSON.stringify(body),
  });
  try {
    await post('/v1/sos', sos);
    for (const path of [`/v1/admin/sos/${sos.id}/notes`, `/v1/admin/sos/${sos.id}/message`]) {
      assert.equal((await post(path, { text: 'hi' })).status, 403);
      assert.equal((await post(path, { text: 'hi' }, 'Bearer ok')).status, 200);
    }
    let res = await post(`/v1/admin/sos/${sos.id}/status`, { status: 'attended', assignee: 'NDRF team 3', note: 'On the way' }, 'Bearer ok');
    assert.equal((await res.json()).by, 'NDRF team 3');
    res = await post('/v1/admin/sos/nosuch12/notes', { text: 'x' }, 'Bearer ok');
    assert.equal(res.status, 404);
    res = await post('/v1/sos/sms', { id: sos.id, results: [{ phone: '9835122140', ok: true }] });
    assert.deepEqual(await res.json(), { ok: true });
    res = await post('/v1/broadcasts/seen', { device: 'install-cccc', ids: [] });
    assert.equal(res.status, 200);
  } finally {
    server.close();
  }
});
