// Run the Pukaar server on your laptop, without Firebase: `npm run local`.
// Data is kept in memory and SMS are printed instead of sent (unless Twilio env vars are set).
// Keys come from env vars, or are generated at start and printed so you can build the app with them.
'use strict';

const pk = require('./pukaar-crypto');
const { createService } = require('./core');
const { memoryStore } = require('./stores');
const { createApp } = require('./http');
const { twilioSms, fakeSms } = require('./sms');

const port = Number(process.env.PORT || 8787);
let keys = { signPrivate: process.env.SIGN_PRIVATE_KEY, boxPrivate: process.env.BOX_PRIVATE_KEY };
if (!keys.signPrivate || !keys.boxPrivate) {
  const k = pk.keygen();
  keys = { signPrivate: k.SIGN_PRIVATE_KEY, boxPrivate: k.BOX_PRIVATE_KEY };
  console.log('Generated keys for this run. Build the app with:');
  console.log(`  PUKAAR_SERVER_SIGN_KEY=${k.PUKAAR_SERVER_SIGN_KEY}`);
  console.log(`  PUKAAR_SERVER_BOX_KEY=${k.PUKAAR_SERVER_BOX_KEY}`);
  console.log('  (set SIGN_PRIVATE_KEY and BOX_PRIVATE_KEY to reuse them next time:)');
  console.log(`  SIGN_PRIVATE_KEY=${k.SIGN_PRIVATE_KEY} BOX_PRIVATE_KEY=${k.BOX_PRIVATE_KEY}`);
}

const sms = process.env.TWILIO_ACCOUNT_SID
  ? twilioSms({ accountSid: process.env.TWILIO_ACCOUNT_SID, authToken: process.env.TWILIO_AUTH_TOKEN, from: process.env.TWILIO_FROM })
  : (() => {
    const f = fakeSms();
    const send = f.send;
    f.send = async (to, text) => { console.log(`SMS to ${to}: ${text}`); return send(to, text); };
    return f;
  })();

const store = memoryStore();
const service = createService({ store, sms, keys });
// Locally, the dashboard routes accept the token in LOCAL_ADMIN_TOKEN (default "local").
const adminToken = process.env.LOCAL_ADMIN_TOKEN || 'local';
const app = createApp(service, {
  verifyResponder: async (req) => ((req.get('Authorization') || '') === `Bearer ${adminToken}` ? { uid: 'local', name: 'Local control room' } : null),
});
// A quick look at what arrived: GET /debug/state
app.get('/debug/state', (req, res) => res.json(store._all()));
app.use((req, res) => res.status(404).json({ error: 'not found' }));

app.listen(port, () => {
  console.log(`\nPukaar server on http://localhost:${port}`);
  console.log(`From the Android emulator: run "adb reverse tcp:${port} tcp:${port}" and build with PUKAAR_GATEWAY_URL=http://127.0.0.1:${port}`);
  console.log(`Mark an SOS attended:  curl -X POST -H "Authorization: Bearer ${adminToken}" -H "Content-Type: application/json" -d '{"status":"attended","by":"District Control Room"}' http://localhost:${port}/v1/admin/sos/<id>/status`);
});
