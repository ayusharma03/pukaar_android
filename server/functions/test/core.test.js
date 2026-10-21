'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const pk = require('../src/pukaar-crypto');
const { createService, smsText, normalizePhone } = require('../src/core');
const { memoryStore } = require('../src/stores');
const { fakeSms } = require('../src/sms');

// Test-only keys, same fixed bytes as the app's ServerCryptoTest.
const signSeed = Buffer.alloc(32, 1);
const boxRaw = Buffer.alloc(32, 2);
const keys = { signPrivate: pk.b64u(signSeed), boxPrivate: pk.b64u(boxRaw) };
const signPub = pk.rawPublic(pk.edPrivate(signSeed));
const boxPub = pk.b64u(pk.rawPublic(pk.xPrivate(boxRaw)));

const sos = {
  id: 'a1b2c3d4', seq: 1, lat: 25.9814, lon: 85.6721, accuracyM: 12, time: 1790000000, battery: 30,
  people: 4, flags: ['Trapped', 'ChildOrElderly'], name: 'Meena Kumari', message: 'On the roof', via: 'mesh', relayedBy: 'peer1',
};
const contacts = { name: 'Meena Kumari', phone: '+919845012345', contacts: [{ name: 'Suresh', phone: '98450 12399' }] };

function setup({ smsOk = true } = {}) {
  const store = memoryStore();
  const sms = fakeSms({ succeed: smsOk });
  let t = 1790000060000;
  const service = createService({ store, sms, keys, now: () => t });
  return { store, sms, service, tick: (s) => { t += s * 1000; } };
}

function verifyAck(st) {
  const text = pk.ackSignedText({ id: st.id, status: { new: 'N', attended: 'A', resolved: 'R' }[st.status], time: st.time, smsSent: st.smsSent, by: st.by });
  return crypto.verify(null, Buffer.from(text), crypto.createPublicKey({ key: Buffer.concat([Buffer.from('302a300506032b6570032100', 'hex'), signPub]), format: 'der', type: 'spki' }), pk.unb64u(st.sig));
}

test('SOS relayed over the mesh, then sealed contacts: family gets one SMS', async () => {
  const { service, sms } = setup();
  const r1 = await service.receiveSos(sos);
  assert.deepEqual(r1, { status: 'new', by: '', smsSent: false });
  assert.equal(sms.sent.length, 0, 'no contacts yet');

  const sealed = pk.sealContacts(contacts, boxPub);
  const r2 = await service.receiveContacts({ id: sos.id, encrypted: true, data: sealed });
  assert.equal(r2.smsSent, true);
  assert.equal(sms.sent.length, 1);
  assert.equal(sms.sent[0].to, '+919845012399');
  assert.match(sms.sent[0].text, /^Pukaar SOS from Meena Kumari\. 4 people\. Trapped, Child or elderly\. "On the roof" Location: maps\.google\.com\/\?q=25\.98140,85\.67210/);

  // The same SOS and contacts arriving again through other phones don't text again (NFR-7).
  await service.receiveSos({ ...sos, relayedBy: 'peer2' });
  await service.receiveContacts({ id: sos.id, encrypted: true, data: pk.sealContacts(contacts, boxPub) });
  assert.equal(sms.sent.length, 1);
});

test('contacts can arrive before the SOS itself', async () => {
  const { service, sms } = setup();
  await service.receiveContacts({ id: sos.id, encrypted: false, data: pk.b64u(Buffer.from(JSON.stringify(contacts))) });
  assert.equal(sms.sent.length, 0, 'wait for the SOS content');
  const r = await service.receiveSos(sos);
  assert.equal(r.smsSent, true);
  assert.equal(sms.sent.length, 1);
});

test('direct upload with contacts texts family at once', async () => {
  const { service, sms } = setup();
  const r = await service.receiveSos({ ...sos, via: 'direct', phone: '+919845012345', contacts: contacts.contacts });
  assert.equal(r.smsSent, true);
  assert.equal(sms.sent.length, 1);
});

test('contacts from the mesh in plain form are ignored when sent as part of a relayed SOS', async () => {
  const { service, store } = setup();
  await service.receiveSos({ ...sos, via: 'mesh', phone: '+910000000000', contacts: [{ name: 'x', phone: '9999999999' }] });
  const doc = (await store.getSos(sos.id));
  assert.equal(doc.contacts.length, 0);
  assert.equal(doc.phone, undefined);
});

test('unreadable sealed contacts are rejected', async () => {
  const { service } = setup();
  const otherKey = pk.b64u(pk.rawPublic(pk.xPrivate(Buffer.alloc(32, 7))));
  await assert.rejects(service.receiveContacts({ id: sos.id, encrypted: true, data: pk.sealContacts(contacts, otherKey) }), /contacts unreadable/);
});

test('statuses are signed, and a responder change comes back as a new signed ack', async () => {
  const { service, tick } = setup();
  await service.receiveSos(sos);
  let { statuses } = await service.statuses('a1b2c3d4,unknown1');
  assert.equal(statuses.length, 1);
  assert.equal(statuses[0].status, 'new');
  assert.ok(verifyAck(statuses[0]));

  tick(60);
  await service.setStatus(sos.id, 'attended', 'District Control Room, Darbhanga');
  ({ statuses } = await service.statuses('a1b2c3d4'));
  assert.equal(statuses[0].status, 'attended');
  assert.equal(statuses[0].by, 'District Control Room, Darbhanga');
  assert.ok(verifyAck(statuses[0]));
  // Tampering breaks the signature.
  assert.equal(verifyAck({ ...statuses[0], status: 'resolved' }), false);
});

test('higher seq wins; an older update arriving late does not overwrite', async () => {
  const { service, store } = setup();
  await service.receiveSos({ ...sos, seq: 2, people: 6 });
  await service.receiveSos({ ...sos, seq: 1, people: 4 });
  assert.equal((await store.getSos(sos.id)).people, 6);
});

test('safe update texts family that was told, once', async () => {
  const { service, sms } = setup();
  await service.receiveSos({ ...sos, via: 'direct', contacts: contacts.contacts });
  await service.receiveSafe({ id: sos.id, time: 1790000500 });
  await service.receiveSafe({ id: sos.id, time: 1790000600 });
  assert.equal(sms.sent.length, 2);
  assert.equal(sms.sent[1].text, 'Pukaar: Meena Kumari is safe now.');
});

test('failed SMS leaves smsSent false and the scheduled retry sends it', async () => {
  const store = memoryStore();
  let ok = false;
  const sms = { sent: [], async send(to, text) { this.sent.push({ to, text }); return ok; } };
  const service = createService({ store, sms, keys, now: () => 1790000060000 });
  const r = await service.receiveSos({ ...sos, via: 'direct', contacts: contacts.contacts });
  assert.equal(r.smsSent, false);
  ok = true;
  assert.deepEqual(await service.retryPendingSms(), { checked: 1, sent: 1 });
  assert.deepEqual(await service.retryPendingSms(), { checked: 0, sent: 0 });
});

test('broadcasts are signed and filtered by time', async () => {
  const { service, tick } = setup();
  const item = await service.addBroadcast('District Control Room', 'Boats are going to Rampur school from 4 pm.');
  tick(10);
  const { items } = await service.broadcasts(item.time - 1);
  assert.equal(items.length, 1);
  const ok = crypto.verify(null, Buffer.from(pk.officialSignedText(items[0])),
    crypto.createPublicKey({ key: Buffer.concat([Buffer.from('302a300506032b6570032100', 'hex'), signPub]), format: 'der', type: 'spki' }),
    pk.unb64u(items[0].sig));
  assert.ok(ok);
  assert.equal((await service.broadcasts(item.time)).items.length, 0);
});

test('bad input is rejected', async () => {
  const { service } = setup();
  await assert.rejects(service.receiveSos({ ...sos, id: '../x' }), /bad id/);
  await assert.rejects(service.receiveSos({ ...sos, seq: 0 }), /bad seq/);
  await assert.rejects(service.setStatus(sos.id, 'done', ''), /bad status/);
});

test('phone numbers are normalised to E.164', () => {
  assert.equal(normalizePhone('98450 12399'), '+919845012399');
  assert.equal(normalizePhone('09845012399'), '+919845012399');
  assert.equal(normalizePhone('+91 98450-12399'), '+919845012399');
  assert.equal(normalizePhone('0044 20 7946 0958'), '+442079460958');
  assert.equal(normalizePhone('123'), null);
});

test('sms text without a location says so', () => {
  assert.match(smsText({ name: 'A', people: 1, time: 0, battery: 50 }), /^Pukaar SOS from A\. 1 person\. Location not available yet\./);
});
