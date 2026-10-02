// Firebase Cloud Functions entry point: one HTTPS function, `api`, serving docs/protocol.md.
// Deployed URL (e.g. https://asia-south1-<project>.cloudfunctions.net/api) is the app's PUKAAR_GATEWAY_URL.
'use strict';

const { onRequest } = require('firebase-functions/v2/https');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const { defineSecret, defineString } = require('firebase-functions/params');
const admin = require('firebase-admin');
const { createService } = require('./src/core');
const { firestoreStore } = require('./src/stores');
const { createApp } = require('./src/http');
const { twilioSms, logSms } = require('./src/sms');

admin.initializeApp();

const SIGN_PRIVATE_KEY = defineSecret('SIGN_PRIVATE_KEY');
const BOX_PRIVATE_KEY = defineSecret('BOX_PRIVATE_KEY');
const TWILIO_ACCOUNT_SID = defineSecret('TWILIO_ACCOUNT_SID');
const TWILIO_AUTH_TOKEN = defineSecret('TWILIO_AUTH_TOKEN');
const TWILIO_FROM = defineString('TWILIO_FROM', { default: '' });
const DEFAULT_COUNTRY_CODE = defineString('DEFAULT_COUNTRY_CODE', { default: '+91' });

let app;
let service;
function getService() {
  if (service) return service;
  const sid = TWILIO_ACCOUNT_SID.value();
  const sms = sid && TWILIO_FROM.value()
    ? twilioSms({ accountSid: sid, authToken: TWILIO_AUTH_TOKEN.value(), from: TWILIO_FROM.value() })
    : logSms();
  service = createService({
    store: firestoreStore(admin.firestore()),
    sms,
    keys: { signPrivate: SIGN_PRIVATE_KEY.value(), boxPrivate: BOX_PRIVATE_KEY.value() },
    defaultCountryCode: DEFAULT_COUNTRY_CODE.value(),
  });
  return service;
}

function getApp() {
  if (app) return app;
  app = createApp(getService(), {
    // Responders are Firebase Auth users with the custom claim role = "responder" (FR-28).
    verifyResponder: async (req) => {
      const token = (req.get('Authorization') || '').replace(/^Bearer /, '');
      if (!token) return null;
      try {
        const user = await admin.auth().verifyIdToken(token);
        return user.role === 'responder' ? { uid: user.uid, name: user.name || user.email || '' } : null;
      } catch (e) {
        return null;
      }
    },
  });
  return app;
}

exports.api = onRequest(
  {
    region: 'asia-south1',
    secrets: [SIGN_PRIVATE_KEY, BOX_PRIVATE_KEY, TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN],
    cors: true,
  },
  (req, res) => getApp()(req, res),
);

// Retry family SMS that failed earlier.
exports.retrySms = onSchedule(
  {
    schedule: 'every 5 minutes',
    region: 'asia-south1',
    secrets: [SIGN_PRIVATE_KEY, BOX_PRIVATE_KEY, TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN],
  },
  async () => {
    const result = await getService().retryPendingSms();
    if (result.checked) console.log('retrySms', result);
  },
);
