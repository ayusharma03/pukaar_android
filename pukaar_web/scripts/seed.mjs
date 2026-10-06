// Loads realistic fake data into the LOCAL Firebase emulators (never a real project):
// SOS in every state around Darbhanga district, Disaster Relief chat, broadcasts, and staff
// accounts with role claims. Wipes the emulators' data first.
//
//   node scripts/seed.mjs             about 20 SOS
//   node scripts/seed.mjs --count 150 many SOS (clusters, virtualised list)
//   node scripts/seed.mjs --count 0   no incidents (empty states)
//
// Document shapes follow server/functions/src/core.js (see src/lib/types.ts). Village names are
// sample data: the server only knows blocks until real village boundaries are added.

const PROJECT = 'demo-pukaar';
const FIRESTORE = '127.0.0.1:8080';
const AUTH = '127.0.0.1:9099';
process.env.FIRESTORE_EMULATOR_HOST = FIRESTORE;
process.env.FIREBASE_AUTH_EMULATOR_HOST = AUTH;
process.env.GCLOUD_PROJECT = PROJECT;

const countArg = process.argv.indexOf('--count');
const COUNT = countArg > 0 ? Math.max(0, Number(process.argv[countArg + 1]) || 0) : 20;

const { initializeApp } = await import('firebase-admin/app');
const { getAuth } = await import('firebase-admin/auth');
const { getFirestore } = await import('firebase-admin/firestore');

initializeApp({ projectId: PROJECT });
const auth = getAuth();
const db = getFirestore();

// Deterministic randomness, so every seed looks the same (times are relative to now).
let state = 20261006;
const rand = () => ((state = (state * 1664525 + 1013904223) % 4294967296) / 4294967296);
const pick = (list) => list[Math.floor(rand() * list.length)];
const between = (lo, hi) => lo + rand() * (hi - lo);
const int = (lo, hi) => Math.floor(between(lo, hi + 1));
const id4 = () => Array.from({ length: 4 }, () => pick('ABCDEFGHJKLMNPQRSTUVWXYZ23456789')).join('');

// Blocks of Darbhanga district (approximate centres) and sample village names. Sample data only.
const BLOCKS = [
  { block: 'Darbhanga Sadar', lat: 26.152, lon: 85.897, villages: ['Laheriasarai', 'Donar', 'Kadirabad', 'Bela'] },
  { block: 'Bahadurpur', lat: 26.105, lon: 85.955, villages: ['Bahadurpur', 'Devpura', 'Rampur', 'Basudeopur'] },
  { block: 'Hayaghat', lat: 26.045, lon: 86.03, villages: ['Hayaghat', 'Anandpur', 'Hasanchak', 'Malikpur'] },
  { block: 'Keoti', lat: 26.3, lon: 85.92, villages: ['Keoti', 'Ranwe', 'Pindaruch', 'Asrahi'] },
  { block: 'Jale', lat: 26.38, lon: 85.8, villages: ['Jale', 'Rarhi', 'Brahmpur', 'Kachhua'] },
  { block: 'Singhwara', lat: 26.2, lon: 85.75, villages: ['Singhwara', 'Bharwara', 'Simri', 'Kasraur'] },
  { block: 'Benipur', lat: 26.04, lon: 86.12, villages: ['Benipur', 'Ajnaul', 'Bahera', 'Mahinam'] },
  { block: 'Biraul', lat: 25.98, lon: 86.25, villages: ['Biraul', 'Supaul Bazar', 'Pokhram', 'Saho'] },
  { block: 'Kusheshwar Asthan', lat: 25.88, lon: 86.27, villages: ['Kusheshwar Asthan', 'Itharwa', 'Usari', 'Bhirua'] },
  { block: 'Ghanshyampur', lat: 26.07, lon: 86.29, villages: ['Ghanshyampur', 'Pali', 'Lagma', 'Baura'] },
  { block: 'Manigachhi', lat: 26.27, lon: 86.05, villages: ['Manigachhi', 'Rajey', 'Makrampur', 'Bhatsimar'] },
  { block: 'Hanuman Nagar', lat: 26.13, lon: 86.0, villages: ['Hanuman Nagar', 'Patori', 'Sohwan', 'Basant'] },
];

const NAMES = [
  'Sunita Devi', 'Ramesh Yadav', 'Anita Kumari', 'Mohammad Irfan', 'Rekha Jha', 'Sanjay Mishra',
  'Pooja Paswan', 'Arvind Thakur', 'Shabnam Khatoon', 'Manoj Sahni', 'Kiran Mandal', 'Rajesh Choudhary',
  'Geeta Devi', 'Abdul Kalam Ansari', 'Priya Kumari', 'Vijay Ram', 'Lalita Devi', 'Dinesh Mukhiya',
  'Rubi Khatoon', 'Suresh Kamat', 'Meena Kumari', 'Bablu Sah', 'Nirmala Devi', 'Rakesh Jha', '',
];
const MESSAGES = [
  'Water up to the roof, 2 children with us',
  'Grandmother cannot walk, water rising fast',
  'House collapsed, my brother is stuck under the wall',
  'No food since yesterday. 6 people on the school roof',
  'Snake bite, need medicine urgently',
  'Pregnant woman with us, needs a doctor',
  'हम छत पर हैं, पानी बढ़ रहा है',
  'नाव भेजिए, बच्चे डरे हुए हैं',
  'Boat capsized near the embankment, 3 people holding a tree',
  'Old man fever for 2 days, no medicine',
  'Cattle shed fell, father injured on the leg',
  'Need drinking water, handpump under water',
  '',
  '',
];
const FLAG_SETS = [
  [], [], ['NeedWater'], ['Injured'], ['Trapped'], ['ChildOrElderly'], ['Injured', 'Trapped'],
  ['NeedMedicine'], ['NeedWater', 'ChildOrElderly'], ['Trapped', 'ChildOrElderly'], ['Injured', 'NeedMedicine'],
];
const BLOOD = ['B+', 'O+', 'A+', 'AB+', 'O-', ''];
const RESPONDERS = ['NDRF team 9', 'SDRF Benipur', 'Boat 4 (Rampur)', 'Block medical team', 'Police PS Hayaghat', 'District Control Room'];
const GATEWAYS = ['Raju (gateway)', 'Kamlesh shop', 'PHC Keoti', 'Panchayat bhawan', 'Ward 7 school'];
const phone = () => `9${int(100000000, 999999999)}`;

const now = Math.floor(Date.now() / 1000);
// The responder who acts on seeded SOS (filled in once the accounts exist).
const operator = { by: 'Priya Sharma', uid: '' };

function makeSos(i) {
  const where = pick(BLOCKS);
  const village = pick(where.villages);
  const time = now - int(60, 6 * 3600) - (i % 7 === 0 ? int(6, 20) * 3600 : 0);
  const receivedAt = time + int(5, 900);
  const roll = rand();
  const status = roll < 0.5 ? 'new' : roll < 0.78 ? 'attended' : 'resolved';
  const pathRoll = rand();
  const via = pathRoll < 0.35 ? ['direct'] : pathRoll < 0.85 ? ['mesh'] : pathRoll < 0.92 ? ['mesh', 'direct'] : ['radio'];
  const direct = via.includes('direct');
  // Contact details come with direct uploads, or when the sealed contacts packet also arrived.
  const hasContacts = direct || rand() < 0.4;
  const name = pick(NAMES);
  const hasFix = rand() > 0.08;
  const people = pick([1, 1, 2, 2, 3, 4, 5, 6, 8, 12]);
  const battery = pick([4, 9, 15, 18, 22, 35, 48, 61, 77, 90, null]);

  const doc = {
    id: id4() + id4(),
    seq: int(1, 4),
    status: 'new',
    statusTime: receivedAt,
    by: '',
    time,
    createdAt: receivedAt,
    updatedAt: receivedAt,
    lat: hasFix ? where.lat + between(-0.06, 0.06) : null,
    lon: hasFix ? where.lon + between(-0.06, 0.06) : null,
    accuracyM: hasFix ? pick([8, 12, 25, 40, 90, 300]) : null,
    people,
    flags: pick(FLAG_SETS),
    name,
    message: pick(MESSAGES),
    battery,
    contacts: [],
    via,
    relayedBy: via.includes('mesh') ? [pick(GATEWAYS)] : [],
    smsSent: false,
    history: [{ status: 'new', time: receivedAt, by: '' }],
    // proposed fields
    receivedAt,
    area: { block: where.block, village },
  };
  if (via.includes('mesh')) doc.hops = int(1, 6);
  if (via.includes('radio')) doc.radioNode = `!a3${int(10, 99)}f${int(100, 999)}`;
  if (hasFix && rand() < 0.15) doc.locationAt = time - int(5, 40) * 60;

  if (hasContacts) {
    doc.phone = phone();
    doc.bloodGroup = pick(BLOOD);
    if (rand() < 0.3) doc.medicalNotes = pick(['Diabetic, takes insulin', 'Asthma', 'Heart patient', '7 months pregnant']);
    doc.contacts = Array.from({ length: int(1, 3) }, () => ({ name: pick(NAMES) || 'Family', phone: phone() }));
    const at = receivedAt + int(5, 60);
    doc.smsResults = doc.contacts.map((c) => ({ name: c.name, phone: c.phone, ok: rand() > 0.2, by: direct ? 'server' : 'phone', at }));
    doc.smsSent = doc.smsResults.some((r) => r.ok);
  }

  if (status !== 'new') {
    const t = receivedAt + int(120, 1800);
    const by = pick(RESPONDERS);
    doc.status = 'attended';
    doc.statusTime = t;
    doc.by = by;
    doc.assignee = by;
    doc.history.push({ status: 'attended', time: t, ...operator, assignee: by });
    doc.notes = [{ at: t, text: `${by} on the way`, ...operator }];
  }
  if (status === 'resolved') {
    const t = doc.statusTime + int(600, 5400);
    doc.status = 'resolved';
    doc.statusTime = t;
    doc.history.push({ status: 'resolved', time: t, ...operator, assignee: doc.assignee });
    doc.notes.push({ at: t, text: 'Rescued, taken to relief camp', ...operator });
  }
  // A few people tapped "I'm safe now" before anyone reached them.
  if (status !== 'resolved' && rand() < 0.1) {
    doc.safeAt = Math.min(now - 30, doc.statusTime + int(300, 3000));
    doc.history.push({ status: 'safe', time: doc.safeAt, by: '' });
  }
  doc.updatedAt = Math.max(...doc.history.map((h) => h.time));
  return doc;
}

const CHAT = [
  ['Ravi Kumar', 'Road to Benipur is under water past the bridge'],
  ['Asha Didi', 'School at Rampur has dry space on the first floor'],
  ['Md. Salim', 'Kisi ke paas ORS hai? Bacche bimar hain'],
  ['Neha', 'Boat seen near Hayaghat station going north'],
  ['Pintu', 'Mobile tower working near the block office, 2 bars'],
  ['Shanti Devi', 'हमारे गाँव में खाना बँट रहा है, पंचायत भवन पर'],
  ['Guddu', 'Embankment crack near Kusheshwar temple, stay away'],
  ['Ravi Kumar', 'Water level going down slowly in ward 4'],
  ['Kavita', 'Need 20 tarpaulins at Biraul high school'],
  ['Arjun', 'Anyone going to DMCH? Can take 2 people'],
  ['Rubina', 'Handpump at mosque road still clean water'],
  ['Asha Didi', 'Doctor camp at Laheriasarai till 6 pm'],
  ['Sonu', 'Battery 10%, will be off. My family is safe'],
  ['Md. Salim', 'Snake seen in the relief camp near the gate'],
  ['Neha', 'Thank you to the NDRF boat team!'],
];

async function wipe() {
  const fsUrl = `http://${FIRESTORE}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`;
  const authUrl = `http://${AUTH}/emulator/v1/projects/${PROJECT}/accounts`;
  for (const url of [fsUrl, authUrl]) {
    const res = await fetch(url, { method: 'DELETE' }).catch((e) => ({ ok: false, statusText: e.message }));
    if (!res.ok) throw new Error(`Couldn't reach the emulator at ${url} (${res.statusText}). Is it running? npm run emulators`);
  }
}

async function seedUsers() {
  const users = [
    { email: 'viewer@pukaar.test', displayName: 'Amit Verma (DM office)', role: 'viewer' },
    { email: 'responder@pukaar.test', displayName: 'Priya Sharma', role: 'responder' },
    { email: 'admin@pukaar.test', displayName: 'Rahul Singh', role: 'admin' },
    // Signed in but no role: for the "no access" state of D1.
    { email: 'norole@pukaar.test', displayName: 'Deepak Kumar', role: null },
  ];
  for (const u of users) {
    const user = await auth.createUser({ email: u.email, password: 'pukaar123', displayName: u.displayName });
    if (u.role) await auth.setCustomUserClaims(user.uid, { role: u.role });
    if (u.role === 'responder') operator.uid = user.uid;
  }
  return users;
}

async function writeAll(collection, docs) {
  for (let i = 0; i < docs.length; i += 400) {
    const batch = db.batch();
    docs.slice(i, i + 400).forEach((d) => batch.set(db.collection(collection).doc(d.id), d));
    await batch.commit();
  }
}

await wipe();
const users = await seedUsers();
const sos = Array.from({ length: COUNT }, (_, i) => makeSos(i));
await writeAll('sos', sos);

const messages = CHAT.map(([sender, text], i) => {
  const where = pick(BLOCKS);
  const located = rand() < 0.5;
  return {
    id: `m${i}${id4()}`,
    sender,
    text,
    time: now - (CHAT.length - i) * int(180, 900),
    lat: located ? where.lat + between(-0.04, 0.04) : null,
    lon: located ? where.lon + between(-0.04, 0.04) : null,
  };
});
await writeAll('messages', messages);

const broadcasts = [
  { id: (Date.now() - 7200000).toString(36), from: 'District Control Room', text: 'Boats at Rampur Govt. School from 4 pm. Stay on the roof and wave a cloth.', time: now - 7200, reach: 46, sentBy: operator },
  { id: (Date.now() - 1800000).toString(36), from: 'District Control Room', text: 'Relief camp open at Laheriasarai stadium: food, water and doctors.', time: now - 1800, reach: 19, sentBy: operator },
];
await writeAll('broadcasts', broadcasts);

const count = (s) => sos.filter((d) => d.status === s).length;
console.log(`Seeded ${sos.length} SOS (new ${count('new')}, attended ${count('attended')}, resolved ${count('resolved')}), ${messages.length} chat messages, ${broadcasts.length} broadcasts.`);
console.log('Sign in with password pukaar123 as:');
users.forEach((u) => console.log(`  ${u.email.padEnd(24)} ${u.role ?? 'no role'}`));
