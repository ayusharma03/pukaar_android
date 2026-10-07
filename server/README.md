# Pukaar rescuer server

Firebase Cloud Functions implementing the gateway contract in [`docs/protocol.md`](../docs/protocol.md): receives SOS, contacts, safe updates and chat from gateway phones, texts family through Twilio, and returns signed status updates and official broadcasts.

```
server/
├── firebase.json, firestore.rules
└── functions/
    ├── index.js            Cloud Functions: `api` (HTTPS) and `retrySms` (every 5 min)
    └── src/
        ├── core.js         All the logic, no Firebase (tested)
        ├── http.js         Express routes
        ├── stores.js       Firestore and in-memory storage
        ├── sms.js          Twilio (REST, no SDK), log-only, fake
        ├── pukaar-crypto.js  Keys, signing, sealed contacts (matches the app)
        ├── areas.js        Block names from a location, for the dashboard
        └── local.js        Run everything on your laptop
```

## Try it locally (no Firebase needed)

```sh
cd server/functions
npm install
npm test          # 24 tests
npm run local     # prints a fresh key pair, listens on :8787
```

Then build the app against it on the emulator:

```sh
adb reverse tcp:8787 tcp:8787
# in the project root, with the two keys `npm run local` printed:
PUKAAR_GATEWAY_URL=http://127.0.0.1:8787 PUKAAR_SERVER_SIGN_KEY=... PUKAAR_SERVER_BOX_KEY=... ./gradlew :app:installDebug
```

(`http://10.0.2.2` doesn't reach the host from the app on recent emulators because the app uses the emulated Wi-Fi; `adb reverse` does. Plain HTTP is allowed only in debug builds, and only to localhost.)

Send an SOS in the app, then:

```sh
curl localhost:8787/debug/state                       # what arrived
curl -X POST -H "Authorization: Bearer local" -H "Content-Type: application/json" \
  -d '{"status":"attended","by":"District Control Room"}' \
  localhost:8787/v1/admin/sos/<id>/status             # within ~30 s the phone shows "A rescuer is on it"
curl -X POST -H "Authorization: Bearer local" -H "Content-Type: application/json" \
  -d '{"from":"District Control Room","text":"Boats at Rampur school from 4 pm."}' \
  localhost:8787/v1/admin/broadcasts                  # shows as an official message in chat
```

SMS are printed to the console unless `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` and `TWILIO_FROM` are set.

## Deploy to Firebase

Needs a Firebase project on the Blaze plan (Cloud Functions with outbound network) and the Firebase CLI.

```sh
cd server
cp .firebaserc.example .firebaserc        # put your project id in it
cd functions && npm install && npm run keygen    # keep the output safe

firebase functions:secrets:set SIGN_PRIVATE_KEY
firebase functions:secrets:set BOX_PRIVATE_KEY
firebase functions:secrets:set TWILIO_ACCOUNT_SID
firebase functions:secrets:set TWILIO_AUTH_TOKEN
# TWILIO_FROM (your Twilio number) and DEFAULT_COUNTRY_CODE (+91) are asked for on first deploy
npm run deploy
```

Build the app with:

```
PUKAAR_GATEWAY_URL=https://asia-south1-<project>.cloudfunctions.net/api
PUKAAR_SERVER_SIGN_KEY=<from keygen>
PUKAAR_SERVER_BOX_KEY=<from keygen>
```

(in `gradle.properties` or as environment variables; never commit the private keys).

## Dashboard access

The dashboard routes (`/v1/admin/...`) need a Firebase Auth ID token for a user with the custom claim `role: "responder"` or `"admin"`. Firestore reads are allowed for `viewer`, `responder` and `admin`. Set claims with the Admin SDK, for example:

```js
admin.auth().setCustomUserClaims(uid, { role: 'responder' });
```

Dashboard routes (all need a responder or admin token; see [`docs/protocol.md`](../docs/protocol.md) §2): status with `assignee` and `note`, notes, message to the person, broadcasts. History records the signed-in operator separately from who is going.

## Known limits

- Area names come from approximate Darbhanga block centres in `src/areas.js`; villages need official boundary data before they can be named.
- Messages to a person, phone-reported family texts and broadcast reach are on the server, but the Android app doesn't use those routes yet.

- Gateway routes are anonymous by design (any Pukaar phone can relay), so someone could post fake SOS. Rate limiting and abuse checks are not built yet.
- If the sender's phone has signal it texts family itself, and the server may text them too, so family can get two messages. More delivery was preferred over fewer.
- SMS text is English.
