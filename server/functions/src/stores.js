// Storage backends for core.js: in memory (tests, local runs) and Firestore (Cloud Functions).
'use strict';

const { FieldValue } = require('firebase-admin/firestore');

function memoryStore() {
  const sos = new Map();
  const messages = new Map();
  const broadcasts = [];
  const copy = (o) => (o ? JSON.parse(JSON.stringify(o)) : null);
  return {
    async getSos(id) { return copy(sos.get(id)); },
    async putSos(id, doc) { sos.set(id, copy(doc)); },
    async addMessages(list) { list.forEach((m) => { if (!messages.has(m.id)) messages.set(m.id, copy(m)); }); },
    async getBroadcastsSince(since) { return broadcasts.filter((b) => b.time > since).map(copy); },
    async addBroadcast(item) { broadcasts.push(copy(item)); },
    async markBroadcastSeen(id, device) {
      const b = broadcasts.find((x) => x.id === id);
      if (!b) return;
      b.seenBy = b.seenBy || [];
      if (!b.seenBy.includes(device)) { b.seenBy.push(device); b.reach = b.seenBy.length; }
    },
    async listPendingSms(limit) {
      return [...sos.values()].filter((d) => !d.smsSent && d.time && d.contacts.length).slice(0, limit).map(copy);
    },
    // For tests and the local dashboard view.
    _all: () => ({ sos: [...sos.values()].map(copy), messages: [...messages.values()].map(copy), broadcasts: broadcasts.map(copy) }),
  };
}

/** Collections: sos/{id}, messages/{id}, broadcasts/{id}. */
function firestoreStore(db) {
  return {
    async getSos(id) {
      const snap = await db.collection('sos').doc(id).get();
      return snap.exists ? snap.data() : null;
    },
    async putSos(id, doc) {
      await db.collection('sos').doc(id).set(JSON.parse(JSON.stringify(doc)));
    },
    async addMessages(list) {
      // create() fails on duplicates, which is what we want (NFR-7).
      await Promise.all(list.map((m) => db.collection('messages').doc(m.id).create(m).catch(() => null)));
    },
    async getBroadcastsSince(since) {
      const q = await db.collection('broadcasts').where('time', '>', since).orderBy('time').limit(50).get();
      return q.docs.map((d) => d.data());
    },
    async addBroadcast(item) {
      await db.collection('broadcasts').doc(item.id).set(item);
    },
    async markBroadcastSeen(id, device) {
      // One doc per install under the broadcast; reach counts only first sightings.
      const ref = db.collection('broadcasts').doc(id);
      try {
        await ref.collection('seen').doc(device).create({ at: Date.now() });
      } catch (e) {
        return; // seen before
      }
      await ref.update({ reach: FieldValue.increment(1) }).catch(() => null);
    },
    async listPendingSms(limit) {
      const q = await db.collection('sos').where('smsSent', '==', false).limit(limit * 4).get();
      return q.docs.map((d) => d.data()).filter((d) => d.time && (d.contacts || []).length).slice(0, limit);
    },
  };
}

module.exports = { memoryStore, firestoreStore };
