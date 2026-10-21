// Pukaar rescuer server logic (docs/protocol.md §2). No Firebase here: storage and SMS are
// passed in, so this runs in tests with in-memory fakes and in Cloud Functions with Firestore/Twilio.
'use strict';

const pk = require('./pukaar-crypto');
const { areaFor } = require('./areas');

const STATUS_CODE = { new: 'N', attended: 'A', resolved: 'R' };
const FLAG_LABELS = ['Injured', 'Trapped', 'Need water', 'Need medicine', 'Child or elderly'];
const ID_RE = /^[A-Za-z0-9]{4,32}$/;
// Messages that travel over the mesh or LoRa radio must fit in a radio packet.
const RADIO_LIMIT_BYTES = 200;
const VIA = ['direct', 'mesh', 'radio'];

class BadRequest extends Error {
  constructor(message, status = 400) {
    super(message);
    this.status = status;
  }
}

/**
 * @param {object} deps
 * @param {object} deps.store   async get/put for sos docs, messages and broadcasts (see stores.js)
 * @param {object} deps.sms     { send(to, text) -> Promise<boolean> }
 * @param {object} deps.keys    { signPrivate, boxPrivate } base64url raw keys
 * @param {function} [deps.now] () -> ms
 * @param {string} [deps.defaultCountryCode] e.g. '+91'
 * @param {function} [deps.area] (lat, lon) -> { block, village } | null, for the dashboard's area labels
 */
function createService({ store, sms, keys, now = () => Date.now(), defaultCountryCode = '+91', area = areaFor }) {
  const nowSec = () => Math.floor(now() / 1000);

  function requireId(id) {
    if (typeof id !== 'string' || !ID_RE.test(id)) throw new BadRequest('bad id');
    return id;
  }

  function signAck(doc) {
    const ack = { id: doc.id, status: STATUS_CODE[doc.status] || 'N', time: doc.statusTime, smsSent: !!doc.smsSent, by: doc.by || '' };
    return pk.sign(pk.ackSignedText(ack), keys.signPrivate);
  }

  function statusOf(doc) {
    const st = { id: doc.id, status: doc.status, time: doc.statusTime, by: doc.by || '', smsSent: !!doc.smsSent, sig: signAck(doc) };
    // Control-room messages to this person ride along with the status (the latest few, signed).
    const msgs = (doc.outbox || []).slice(-3);
    if (msgs.length) st.messages = msgs.map((m) => ({ ...m, sig: pk.sign(pk.messageSignedText({ sosId: doc.id, ...m }), keys.signPrivate) }));
    return st;
  }

  /** The signed-in operator, for history and notes. */
  function actorOf(actor) {
    return { by: str(actor && actor.name, 80), uid: str(actor && actor.uid, 128) };
  }

  /** A new SOS record, or an existing one, ready to merge into. */
  async function load(id) {
    const found = await store.getSos(id);
    if (found) return found;
    const t = nowSec();
    return {
      id, status: 'new', statusTime: t, by: '', smsSent: false, via: [], relayedBy: [], contacts: [],
      history: [{ status: 'new', time: t, by: '' }], createdAt: t, receivedAt: t,
    };
  }

  /** An SOS that must already exist (dashboard routes). */
  async function existing(id) {
    requireId(id);
    const doc = await store.getSos(id);
    if (!doc) throw new BadRequest('unknown sos', 404);
    return doc;
  }

  /** POST /v1/sos */
  async function receiveSos(body) {
    const id = requireId(body.id);
    const seq = Number(body.seq);
    if (!Number.isInteger(seq) || seq < 1) throw new BadRequest('bad seq');
    const doc = await load(id);

    // Highest seq wins for the SOS content (NFR-7: duplicates through many paths are stored once).
    if (!doc.seq || seq >= doc.seq) {
      Object.assign(doc, {
        seq,
        lat: num(body.lat), lon: num(body.lon), accuracyM: num(body.accuracyM),
        time: num(body.time), battery: num(body.battery), people: num(body.people) || 1,
        flags: Array.isArray(body.flags) ? body.flags.map(String).slice(0, 8) : [],
        name: str(body.name, 64), message: str(body.message, 400),
      });
      // Set when the fix is older than the SOS ("last known, 12 min old").
      const locationAt = num(body.locationAt);
      if (locationAt) doc.locationAt = locationAt;
      else delete doc.locationAt;
      const a = area(doc.lat, doc.lon);
      if (a) doc.area = a;
    }
    const via = VIA.includes(body.via) ? body.via : 'mesh';
    if (!doc.via.includes(via)) doc.via.push(via);
    if (body.relayedBy && !doc.relayedBy.includes(String(body.relayedBy))) doc.relayedBy.push(str(body.relayedBy, 64));
    // Fewest hops seen across all the paths it arrived by.
    const hops = num(body.hops);
    if (via === 'mesh' && Number.isInteger(hops) && hops >= 0 && hops <= 50) doc.hops = doc.hops == null ? hops : Math.min(doc.hops, hops);
    if (via === 'radio' && body.radioNode) doc.radioNode = str(body.radioNode, 32);

    // Only the sender's own phone sends these, over HTTPS (never over the mesh in plain).
    if (via === 'direct') {
      if (body.phone) doc.phone = str(body.phone, 32);
      if (body.bloodGroup) doc.bloodGroup = str(body.bloodGroup, 8);
      if (body.medicalNotes) doc.medicalNotes = str(body.medicalNotes, 400);
      if (Array.isArray(body.contacts)) doc.contacts = cleanContacts(body.contacts);
    }

    doc.updatedAt = nowSec();
    await maybeTextFamily(doc);
    await store.putSos(id, doc);
    return { status: doc.status, by: doc.by || '', smsSent: !!doc.smsSent };
  }

  /** POST /v1/contacts: who to text, from a PKCT1 packet relayed by any phone. */
  async function receiveContacts(body) {
    const id = requireId(body.id);
    let info;
    try {
      info = body.encrypted
        ? pk.openContacts(String(body.data), keys.boxPrivate)
        : JSON.parse(pk.unb64u(String(body.data)).toString('utf8'));
    } catch (e) {
      throw new BadRequest('contacts unreadable');
    }
    const doc = await load(id);
    if (info.phone && !doc.phone) doc.phone = str(info.phone, 32);
    if (info.name && !doc.name) doc.name = str(info.name, 64);
    if (info.bloodGroup && !doc.bloodGroup) doc.bloodGroup = str(info.bloodGroup, 8);
    if (info.medicalNotes && !doc.medicalNotes) doc.medicalNotes = str(info.medicalNotes, 400);
    if (Array.isArray(info.contacts) && doc.contacts.length === 0) doc.contacts = cleanContacts(info.contacts);
    doc.updatedAt = nowSec();
    await maybeTextFamily(doc);
    await store.putSos(id, doc);
    return { smsSent: !!doc.smsSent };
  }

  /** POST /v1/safe (FR-15) */
  async function receiveSafe(body) {
    const id = requireId(body.id);
    const doc = await store.getSos(id);
    if (!doc) return { ok: true }; // nothing to update; the SOS never reached us
    if (!doc.safeAt) {
      doc.safeAt = num(body.time) || nowSec();
      doc.history.push({ status: 'safe', time: doc.safeAt, by: '' });
      if (doc.smsSent) {
        const text = `Pukaar: ${doc.name || 'Your contact'} is safe now.`;
        await Promise.all(doc.contacts.map((c) => sendSms(c.phone, text)));
      }
      await store.putSos(id, doc);
    }
    return { ok: true };
  }

  /**
   * POST /v1/sos/sms: the person's own phone texted family itself (for example the SOS reached us
   * only through the mesh, so we had no contacts) and reports how it went.
   */
  async function receiveSmsReport(body) {
    const doc = await existing(body.id);
    const list = Array.isArray(body.results) ? body.results.slice(0, 5) : [];
    const reported = list
      .filter((r) => r && r.phone)
      .map((r) => ({ name: str(r.name, 64), phone: str(r.phone, 32), ok: !!r.ok, by: 'phone', at: num(r.time) || nowSec() }));
    if (!reported.length) return { ok: true };
    const others = (doc.smsResults || []).filter((r) => !(r.by === 'phone' && reported.some((p) => p.phone === r.phone)));
    const firstReport = !(doc.smsResults || []).some((r) => r.by === 'phone');
    doc.smsResults = [...others, ...reported];
    if (firstReport) doc.history.push({ status: 'sms', time: nowSec(), by: '', text: 'Family texted from their phone' });
    doc.updatedAt = nowSec();
    await store.putSos(doc.id, doc);
    return { ok: true };
  }

  /** POST /v1/messages: the Disaster Relief chat as gateways saw it (FR-33). */
  async function receiveMessages(body) {
    const list = Array.isArray(body.messages) ? body.messages.slice(0, 500) : [];
    const clean = list
      .filter((m) => m && typeof m.id === 'string' && m.id.length <= 64)
      .map((m) => ({ id: m.id, sender: str(m.sender, 64), text: str(m.text, 2000), time: num(m.time), lat: num(m.lat), lon: num(m.lon) }));
    await store.addMessages(clean);
    return { stored: clean.length };
  }

  /** GET /v1/sos/status?ids=a,b */
  async function statuses(ids) {
    const wanted = String(ids || '').split(',').filter((id) => ID_RE.test(id)).slice(0, 200);
    const docs = await Promise.all(wanted.map((id) => store.getSos(id)));
    return { statuses: docs.filter((d) => d && d.time).map(statusOf) };
  }

  /** GET /v1/broadcasts?since=unixSeconds */
  async function broadcasts(since) {
    const items = await store.getBroadcastsSince(Number(since) || 0);
    return {
      items: items.map((b) => {
        const item = { id: b.id, from: b.from, text: b.text, time: b.time };
        return { ...item, sig: pk.sign(pk.officialSignedText(item), keys.signPrivate) };
      }),
    };
  }

  /**
   * POST /v1/broadcasts/seen: a phone reports official messages it has shown, with a random install
   * id. Reach is the number of different installs (anonymous, so treat it as an estimate).
   */
  async function broadcastsSeen(body) {
    const device = str(body.device, 64);
    if (!/^[A-Za-z0-9_-]{8,64}$/.test(device)) throw new BadRequest('bad device');
    const ids = (Array.isArray(body.ids) ? body.ids : []).map(String).filter((x) => /^[a-z0-9]{1,16}$/.test(x)).slice(0, 20);
    await Promise.all(ids.map((id) => store.markBroadcastSeen(id, device)));
    return { ok: true };
  }

  /**
   * Dashboard (D3): a responder changes an SOS status, including undo and reopen (any status to any
   * other). Sent back to the person as a signed ack.
   *
   * opts.assignee: who is going (free text). The person's phone shows it ("A rescuer is on it").
   * opts.note: optional team note. opts.actor: the signed-in operator { uid, name }, kept in history
   * and notes so there's a record of who changed what. A plain string is taken as the assignee
   * (older callers).
   */
  async function setStatus(id, status, opts = {}) {
    if (typeof opts === 'string') opts = { assignee: opts };
    requireId(id);
    if (!STATUS_CODE[status]) throw new BadRequest('bad status');
    const doc = await existing(id);
    const actor = actorOf(opts.actor);
    const t = nowSec();
    const assignee = str(opts.assignee, 80).trim();
    if (status === 'new') doc.assignee = '';
    else if (assignee) doc.assignee = assignee;
    doc.status = status;
    doc.statusTime = t;
    doc.by = status === 'new' ? '' : (doc.assignee || actor.by);
    const entry = { status, time: t, ...actor };
    if (doc.assignee && status !== 'new') entry.assignee = doc.assignee;
    doc.history.push(entry);
    const note = str(opts.note, 1000).trim();
    if (note) (doc.notes = doc.notes || []).push({ at: t, text: note, ...actor });
    doc.updatedAt = t;
    await store.putSos(id, doc);
    return statusOf(doc);
  }

  /** Dashboard (D3): a team note on an SOS. Notes stay on the server; the person doesn't see them. */
  async function addNote(id, text, actor) {
    const doc = await existing(id);
    const clean = str(text, 1000).trim();
    if (!clean) throw new BadRequest('text is required');
    const note = { at: nowSec(), text: clean, ...actorOf(actor) };
    (doc.notes = doc.notes || []).push(note);
    doc.updatedAt = note.at;
    await store.putSos(id, doc);
    return note;
  }

  /**
   * Dashboard (D3): a message to the person who sent the SOS. Signed, and delivered through gateways
   * with the status poll (GET /v1/sos/status), so it must fit in a radio packet.
   */
  async function messagePerson(id, text, actor) {
    const doc = await existing(id);
    const clean = String(text || '').trim();
    if (!clean) throw new BadRequest('text is required');
    if (Buffer.byteLength(clean, 'utf8') > RADIO_LIMIT_BYTES) throw new BadRequest(`text is over ${RADIO_LIMIT_BYTES} bytes`);
    const who = actorOf(actor);
    const t = nowSec();
    const msg = { id: Math.floor(now()).toString(36), time: t, from: who.by || 'Control room', text: clean };
    (doc.outbox = doc.outbox || []).push(msg);
    doc.history.push({ status: 'message', time: t, ...who, text: clean });
    doc.updatedAt = t;
    await store.putSos(id, doc);
    return msg;
  }

  /** Dashboard (D4): an official broadcast into the mesh. Must fit in a radio packet. */
  async function addBroadcast(from, text, actor) {
    const item = { id: Math.floor(now()).toString(36), from: str(from, 80).trim(), text: String(text || '').trim(), time: nowSec() };
    if (!item.from || !item.text) throw new BadRequest('from and text are required');
    if (Buffer.byteLength(item.text, 'utf8') > RADIO_LIMIT_BYTES) throw new BadRequest(`text is over ${RADIO_LIMIT_BYTES} bytes`);
    await store.addBroadcast({ ...item, reach: 0, ...(actor ? { sentBy: actorOf(actor) } : {}) });
    return item;
  }

  /** Scheduled: retry family SMS that failed (for example Twilio was down). */
  async function retryPendingSms() {
    const docs = await store.listPendingSms(50);
    let sent = 0;
    for (const doc of docs) {
      if (doc.safeAt || doc.status === 'resolved') continue;
      await maybeTextFamily(doc);
      if (doc.smsSent) sent++;
      await store.putSos(doc.id, doc);
    }
    return { checked: docs.length, sent };
  }

  async function maybeTextFamily(doc) {
    // Text once the SOS itself and the contacts have both arrived (in either order).
    if (doc.smsSent || !doc.time || doc.contacts.length === 0) return;
    const text = smsText(doc);
    const results = await Promise.all(doc.contacts.map((c) => sendSms(c.phone, text)));
    const at = nowSec();
    doc.smsResults = [
      ...(doc.smsResults || []).filter((r) => r.by === 'phone'),
      ...doc.contacts.map((c, i) => ({ name: c.name, phone: c.phone, ok: results[i], by: 'server', at })),
    ];
    if (results.some(Boolean)) {
      doc.smsSent = true;
      doc.statusTime = at; // phones learn about the SMS through a fresh signed ack
    }
  }

  async function sendSms(phone, text) {
    const to = normalizePhone(phone, defaultCountryCode);
    if (!to) return false;
    try {
      return await sms.send(to, text);
    } catch (e) {
      return false;
    }
  }

  return {
    receiveSos, receiveContacts, receiveSafe, receiveSmsReport, receiveMessages, statuses, broadcasts, broadcastsSeen,
    setStatus, addNote, messagePerson, addBroadcast, retryPendingSms,
  };
}

/** The family SMS, in the same shape the phone sends (handoff 2w). */
function smsText(doc) {
  const parts = [`Pukaar SOS from ${doc.name || 'someone'}.`, `${doc.people || 1} ${doc.people === 1 ? 'person' : 'people'}.`];
  const flags = (doc.flags || []).map(flagLabel).filter(Boolean);
  if (flags.length) parts.push(flags.join(', ') + '.');
  if (doc.message) parts.push(`"${doc.message}"`);
  parts.push(doc.lat != null && doc.lon != null
    ? `Location: maps.google.com/?q=${doc.lat.toFixed(5)},${doc.lon.toFixed(5)}`
    : 'Location not available yet.');
  const time = new Date((doc.time || 0) * 1000).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata' });
  return `${parts.join(' ')} · ${time} · battery ${doc.battery ?? '?'}%`;
}

function flagLabel(f) {
  const map = { Injured: 0, Trapped: 1, NeedWater: 2, NeedMedicine: 3, ChildOrElderly: 4 };
  return FLAG_LABELS[map[f]];
}

/** E.164 for Twilio. Indian numbers without a country code get the default one. */
function normalizePhone(phone, defaultCountryCode = '+91') {
  let p = String(phone || '').replace(/[\s\-().]/g, '');
  if (p.startsWith('00')) p = '+' + p.slice(2);
  if (p.startsWith('+')) return /^\+\d{8,15}$/.test(p) ? p : null;
  if (/^0\d{10}$/.test(p)) p = p.slice(1);
  if (/^\d{10}$/.test(p)) return defaultCountryCode + p;
  return null;
}

function cleanContacts(list) {
  return list.slice(0, 5)
    .map((c) => ({ name: str(c && c.name, 64), phone: str(c && c.phone, 32) }))
    .filter((c) => c.phone);
}

function num(v) {
  const n = Number(v);
  return v === null || v === undefined || v === '' || Number.isNaN(n) ? null : n;
}

function str(v, max) {
  return v === null || v === undefined ? '' : String(v).slice(0, max);
}

module.exports = { createService, smsText, normalizePhone, BadRequest, RADIO_LIMIT_BYTES };
