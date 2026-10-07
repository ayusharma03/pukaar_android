# Android changes for the rescuer dashboard

The dashboard (`pukaar_web/`) and the server (`server/functions/`) gained features that need the Android app to take part. This file lists every change the app needs, why it's needed, the exact contract, where it goes in the code, how to test it, and what to do first.

**Nothing here is urgent for safety.** Current phones keep working against the new server: the server only added optional fields and new routes, and the gateway's Gson parsing ignores fields it doesn't know. But until these changes land, some dashboard features show nothing or show something wrong (see the table).

Contract reference: [`docs/protocol.md`](protocol.md) §2. Server code: `server/functions/src/core.js`.

## Summary

| # | Change | Why | Without it | Size | Bitchat files |
|---|---|---|---|---|---|
| 1 | **Status can go back** (undo, reopen) | Dashboard has Undo and Reopen | The person keeps seeing "A rescuer is on it" after a responder undoes it | Small | None |
| 2 | **Messages from the control room** (`PKMSG1`) | Dashboard D3 "Send message to this person" | Messages are stored on the server and never reach the phone | Medium | None |
| 3 | **Report family texts sent from the phone** (`POST /v1/sos/sms`) | Dashboard D3 "Family texted" | Dashboard says "no contacts" even when the phone texted family itself | Small | None |
| 4 | **Hop count** (`hops`) | Dashboard shows "Mesh · 3 hops" and the path | Dashboard shows just "Mesh" | Small | 1 line in `MessageHandler.kt` |
| 5 | **Location age** (`locationAt`) | Dashboard warns "Last known, 12 min old" | An old fix looks fresh to rescuers | Small | None |
| 6 | **Broadcast reach** (`POST /v1/broadcasts/seen`) | Dashboard shows "reached ~1,240 phones" | Reach always says no phones reported | Small (phase 1) | None |
| 7 | **Radio arrival** (`via: "radio"`, `radioNode`) | Dashboard shows "Radio" | Nothing yet: there's no Meshtastic code | Comes with Meshtastic | None |

Suggested order: 1, 2, 3, 5, 4, 6. Change 7 waits for the Meshtastic work.

**Status (2026-10-07):** changes 1 to 6 are built in the app and unit-tested (`MessagePacketTest`, `SosStatusRulesTest`). Not yet tested end to end against the local server, and changes 2 (mesh relay), 3 (via mesh) and 4 still need real phones. The "From the control room" card on SOS status is a stand-in until there's a design.

Nothing is needed for **who is going**: the server already puts the team name (for example "NDRF team 3") in the signed ack's `by`, and the app already shows `by` as `handledBy`.

---

## 1. Status can go back (undo and reopen)

### Why

Responders can now undo "attended" (back to `new`) and reopen a resolved SOS (back to `attended`). The server sends a fresh signed ack with a newer `time` for each change.

The app only ever moves forward. In `SosManager.applyServerStatus`:

```kotlin
if (stage.ordinal <= sos.stage.ordinal) sos   // ← ignores any ack that goes back
```

So after an undo, the person still sees "A rescuer is on it", which is exactly the wrong thing to show someone waiting for help.

### What to change

`app/src/main/java/app/pukaar/sos/SosManager.kt`:

1. Add the last applied ack time to `ActiveSos`:
   ```kotlin
   /** Server time (s) of the last status applied, so an older ack arriving late is ignored. */
   val lastAckTime: Long = 0,
   ```
   `ActiveSos` is restored from SharedPreferences with Gson, which leaves missing fields at their JVM default. `0` for a `Long` is fine, but don't add non-null object fields without handling `null` on restore (see how `init` checks `events`, `family` and `details`).

2. Order acks by the server's `time`, not by stage:
   - `onPacket(AckPacket)` already has `packet.timeSec`. Pass it through to `applyServerStatus`.
   - Ignore an ack whose `timeSec` is older than or equal to `lastAckTime`.
   - Otherwise apply its stage even when it is lower than the current one, and record `lastAckTime = timeSec`.
   - Local stages (`Sending`, `Relayed`, `SentByRadio`) are not from the server. When an ack moves the SOS back to `new`, show `HelpNotified` (the server has it), never `Sending`.
   - The direct-upload response (`uploadDirect`) has no server time. Keep treating it as "at least `HelpNotified`", and only move forward from it.

3. Reopen after resolved: `ActiveSos.closed` is true once the stage is `Resolved`. The keep-alive `tick()`, the mesh resend and the notification all stop. When an `Attending` ack with a newer time arrives for a resolved SOS:
   - set the stage back to `RescuerAttending`
   - call `SosNotifier.showActive` again
   - don't reopen if the person tapped "I'm safe now" (`safeAt != null`); their choice wins on the phone.

4. Add a timeline event for each change, so the person sees what happened. Suggested `detail` text: "The control room moved your SOS back to waiting" for an undo. Needs new strings (`pk_sos_event_back_to_new`, Hindi marked for review).

`Gateway.relayRound` needs no change: it already relays an ack whenever the status differs from the last one it relayed (`knownStatus[ack.id] != ack.status`).

### Tests

`app/src/test/java/app/pukaar/sos/`: `SosManager` isn't unit-tested today because it needs Android. Pull the decision into a pure function and test that:

```kotlin
/** The stage after applying a server ack, or null to ignore it. */
internal fun nextStage(current: SosStage, lastAckTime: Long, ack: AckStatus, ackTime: Long, safe: Boolean): SosStage?
```

Cases: forward, back to new, reopen from resolved, reopen ignored when safe, stale ack (older time) ignored, same time ignored.

---

## 2. Messages from the control room (`PKMSG1`)

### Why

Dashboard D3 has "Send message to this person" (for example "Boat coming in 20 minutes. Stay on the roof."). The server stores it, signs it, and returns it in the status poll. Nothing on the phone reads it yet.

### The contract (already live on the server)

`GET /v1/sos/status?ids=…` now returns, for an SOS with messages:

```json
{
  "statuses": [{
    "id": "k9wd2024", "status": "attended", "time": 1790000100, "by": "NDRF team 3", "smsSent": true, "sig": "…",
    "messages": [
      { "id": "mfx3k2a", "time": 1790000123, "from": "Kavita Rao", "text": "Boat coming in 20 minutes. Stay on the roof.", "sig": "…" }
    ]
  }]
}
```

- At most the **latest 3** messages per SOS. The phone must de-duplicate by message `id`.
- `text` is at most **200 UTF-8 bytes**. `from` is at most 80 characters.
- `sig` is Ed25519 (same server key as acks) over:
  ```
  PKMSG1|<sosId>|<msgId>|<time>|<from cleaned>|<text>
  ```
  "Cleaned" means the same as `Packets.clean`: `|` becomes `/`, newlines become spaces, then trim. `text` is signed as is.

### New mesh packet

```
PKMSG1|sosId|msgId|time|sig|from|text
```

`text` is last so it may contain `|`. It follows the pattern of `PKOFF1` (signature right after the ids, then the human-readable parts).

### What to change

**a. `sos/Packets.kt`: add the packet.**

```kotlin
const val MESSAGE = "PKMSG1"

// in parse():
content.startsWith("$MESSAGE|") -> MessagePacket.decode(content)

/**
 * `PKMSG1|sosId|msgId|time|sig|from|text`: a control-room message for the person who sent SOS
 * `sosId`, relayed by a gateway phone. `sig` signs [signedText]. Unsigned or badly signed ones
 * must be ignored.
 */
data class MessagePacket(
    val sosId: String,
    val id: String,
    val timeSec: Long,
    val from: String,
    val text: String,
    val sig: String,
) : PukaarPacket {
    fun signedText() = "${Packets.MESSAGE}|$sosId|$id|$timeSec|${Packets.clean(from)}|$text"
    fun encode() = "${Packets.MESSAGE}|$sosId|$id|$timeSec|$sig|${Packets.clean(from)}|$text"
    fun verified(key: ByteArray? = ServerCrypto.signKey) = ServerCrypto.verify(signedText(), sig, key)

    companion object {
        fun decode(content: String): MessagePacket? {
            val p = content.split("|", limit = 7)
            if (p.size < 7) return null
            val time = p[3].toLongOrNull() ?: return null
            return MessagePacket(p[1], p[2], time, p[5], p[6], p[4])
        }
    }
}
```

`PukaarPacket` is a sealed interface, so the compiler will point at every `when` that must handle the new type. Today that's:
- `ui/screens/chat/ChatRoute.kt`: add `is MessagePacket` to the `is AckPacket, is ContactsPacket -> Unit` branch, so the raw packet never shows in the group chat.
- `ui/PukaarNavHost.kt` around line 311 (`lastMessageFrom`): hide it there too.
- `unreadCount` in `ChatRoute.kt` already counts only plain chat, SOS and verified officials, so nothing to do.

**Important:** add the packet to `Packets.parse` in the same release that starts relaying it. Today an unknown `PKMSG1|…` line parses as `null`, which the app treats as plain chat. It would show up in the Disaster Relief chat, and gateways would upload it to `/v1/messages`.

**b. `gateway/Gateway.kt`: read and relay.**

- `pollStatus` builds `AckPacket`s only. Change it to also return the messages. For example, return a small data class:
  ```kotlin
  data class StatusResult(val ack: AckPacket, val messages: List<MessagePacket>)
  ```
  and parse `o.getAsJsonArray("messages")` when present (each element becomes a `MessagePacket(sosId = id, …)`).
- In `relayRound`:
  - drop messages whose `verified()` is false (the same rule as acks)
  - for the phone's **own** active SOS (`ack.id == ownId`), hand each message to a new callback `onOwnMessage(MessagePacket)`, wired in `PukaarRuntime.init` to `SosManager.onPacket`
  - for **others'** SOS, broadcast each new message into the mesh once: keep a `relayedMessages = mutableSetOf<String>()` keyed by message `id`, like `relayedOfficial`
- Gateways poll status only for SOS they uploaded (`uploadedSos`) plus their own. That's what we want: the phone that brought the SOS in is the one most likely to reach the sender again.

**c. `sos/SosManager.kt`: store and show.**

- New model and field:
  ```kotlin
  data class ControlMessage(val id: String, val from: String, val text: String, val at: Long)
  // in ActiveSos:
  val messages: List<ControlMessage>? = null,   // nullable: older saved JSON has no such field
  ```
  Use it as `sos.messages.orEmpty()`, or normalise it in `init` like the other restored fields.
- In `onPacket`, handle `MessagePacket`:
  - only for the active SOS (`packet.sosId == sos.id`)
  - only if `packet.verified()`
  - ignore an `id` already stored
  - append it and call `SosNotifier.showControlMessage(...)`
- Keep showing messages after the SOS is resolved, until the user dismisses it.

**d. `sos/SosNotifier.kt`: notify.**

- Add `showControlMessage(context, sos, message)`, posting on the existing `CHANNEL_OFFICIAL` (high importance).
  - Title: "Message from the control room" (`pk_notif_control_message_title`)
  - Text: the message
  - Tapping it opens SOS status (`PukaarIntents.ROUTE_SOS_STATUS`)
- Use a per-message notification id (for example `message.id.hashCode()`) so two messages don't replace each other.

**e. `ui/screens/sos/SosStatusScreen.kt`: show it.**

- New section "From the control room", placed right under `HeroCard` (the most important thing on the screen after the status). Show the newest message first, with sender and time.
- **Design gap:** this isn't in `docs/design/HANDOFF.md` (screen 2i) or `docs/screens.md`. Suggested look: a `PukaarCard` in `primaryContainer` with the `verified` icon, like official messages in chat. Get a design decision before polishing.
- Add `@Preview`s (dark and light, 360 × 800) with one and two messages. Check long Hindi text at 1.3× font scale.

**f. Strings** (`values/strings_pukaar.xml` and `values-hi/strings_pukaar.xml`, Hindi marked "NEEDS REVIEW by a native speaker"):
- `pk_sos_control_messages_title`: "From the control room"
- `pk_notif_control_message_title`: "Message from the control room"
- `pk_sos_control_message_meta`: "%1$s · %2$s" (sender · time)

### Size on the mesh

The packet is up to about 400 bytes: header, an 86-character signature, `from`, and up to 200 bytes of text. That's fine over Bluetooth (bitchat fragments it) but too big for one LoRa packet, like `PKACK1` and `PKOFF1` already are. When Meshtastic arrives, either split it or send a shorter signature-less form only over radio. Decide that with change 7.

### Compatibility

Older Pukaar versions and plain bitchat clients will show `PKMSG1|…` lines as raw text in public chat, as they already do for `PKACK1`. That's acceptable for a hackathon build. Mention it in the release notes.

### Tests

`app/src/test/java/app/pukaar/sos/PacketsTest.kt`. A real vector from the server code, using the same fixed test key as `ServerCryptoTest` (sign seed 32 × `0x01`, public key `iojj3XQJ8ZX9UtstPLpdcspnCb8dlBIb83SIAbQPb1w`):

```
signed text: PKMSG1|k9wd2024|mfx3k2a|1790000123|Kavita Rao|Boat coming in 20 minutes. Stay on the roof.
sig:         BaeZp1Jq8StqB0pbp5zicpONAWhH-GJbBiuFzm2Yz8ek3M2-UEQYX0ebN10kZXTD6BD28UbNwJC9dmEtO1Y7Bg
```

(Made with `pukaar-crypto.js` `sign()` over `messageSignedText(...)`, the same functions the server uses.)

Test cases:
- encode then decode gives the same packet
- the vector above verifies against the test public key
- changing `text`, `from` or `sosId` breaks the signature
- a `|` inside `text` survives the round trip
- a missing field gives `null`
- a packet signed by another key is rejected
- `Packets.parse` returns a `MessagePacket`, not `null`

---

## 3. Report family texts sent from the phone (`POST /v1/sos/sms`)

### Why

When the person's phone has a tower signal, it texts their emergency contacts itself (`SosManager.sendFamilySms`, statuses `FamilyStatus.SentDirect` / `Failed`). The server never hears about this. If the SOS reached the server only through the mesh, the dashboard says "The server has no contacts for this SOS" even though family was already told.

### The contract

```
POST /v1/sos/sms
{ "id": "k9wd2024", "results": [ { "name": "Raju Kumar", "phone": "9835122140", "ok": true, "time": 1790000200 } ] }
→ { "ok": true }
```

- Up to 5 results.
- Results for the same phone replace earlier reports, so it's safe to send again.
- Unknown SOS id gives HTTP 404. That means the SOS hasn't reached the server yet, so keep the report and retry later.
- Anonymous, like the other gateway routes.

### What to change

- `gateway/Gateway.kt`: add
  ```kotlin
  suspend fun reportFamilySms(sosId: String, results: List<FamilyNotice>): Boolean
  ```
  mapping each notice to `{name, phone, ok = status == SentDirect, time}`. Only include notices whose status is `SentDirect` or `Failed`, not `Waiting`, `Sending` or `SentByServer`.
- `sos/SosManager.kt`:
  - Remember what was reported. Add `val familyReportedAt: Long = 0` to `ActiveSos` and compare it with the latest change in `family`, or keep a hash of the reported list.
  - In `tick()` (every 15 s), when the phone has internet and there are unreported results, call `reportFamilySms`. On success, mark them reported.
  - Also report right after `uploadDirect()` succeeds, since the SOS now certainly exists on the server.
- **Privacy:** these results contain family phone numbers, so send them **only over HTTPS from the person's own phone**, never as a mesh packet in plain text. If phones without data should be able to report through the mesh later, seal the payload to the server's box key like `PKCT1`. That needs a new server route, so don't do it now.

### Tests

The JSON mapping can be a pure function (`familyReportBody(results)`) with a unit test: only `SentDirect` and `Failed` included, `ok` correct, numbers passed unchanged.

---

## 4. Hop count (`hops`)

### Why

The dashboard shows how far an SOS travelled ("Mesh · 3 hops") and draws the path. Rescuers use it to judge how isolated the area is. The server keeps the **fewest** hops reported across all gateways.

### The contract

`POST /v1/sos` with `via: "mesh"` accepts `hops`: an integer from 0 to 50, the number of Bluetooth links the copy travelled from the sender's phone to this gateway. A direct neighbour is 1 hop. The dashboard shows `hops − 1` as "phones in between".

### What to change

Bitchat doesn't put the packet's TTL on `BitchatMessage`, so this needs **one small bitchat change**:

- `com/bitchat/android/mesh/MessageHandler.kt`, in the broadcast plain-text path (the "Fallback: plain text" block that builds `BitchatMessage(id = PacketIdUtil.computeIdHex(packet).uppercase(), …)`). Record the hop count by message id before calling `delegate?.onMessageReceived(message)`:
  ```kotlin
  // Pukaar: how many links this broadcast travelled, for the rescuer dashboard.
  app.pukaar.sos.MeshHops.record(message.id, AppConstants.MESSAGE_TTL_HOPS.toInt() - packet.ttl.toInt() + 1)
  ```
  This keeps bitchat's own model unchanged, so pulling bitchat updates stays easy. Note it in the list of changed bitchat files (see `CLAUDE.md`).
- New Pukaar file `app/src/main/java/app/pukaar/sos/MeshHops.kt`: a small thread-safe map from message id to hops, capped (for example the last 2,000 ids).
- `gateway/Gateway.kt`, in `relayRound` where it uploads someone else's SOS: look up `MeshHops.get(msg.id)` and pass it to `uploadSos` as a new optional parameter. Add it to the body only when known.

**Check on real phones before trusting the number.** It depends on whether relays decrement the TTL before forwarding, and on the sender starting at `MESSAGE_TTL_HOPS` (7). With three phones in a line (A → B → C, A and C out of range of each other), an SOS from A should report 2 hops at C. Fix the `+ 1` if not. This needs real phones; Bluetooth doesn't work on emulators.

---

## 5. Location age (`locationAt`)

### Why

`SosManager.start` uses `Locations.current(…)`, which falls back to the **last known** location when there's no fresh fix. That fix can be old. The dashboard warns rescuers ("Last known, 12 min old") when it knows the fix time.

### The contract

`POST /v1/sos` accepts `locationAt` (Unix seconds): when the fix was taken. Send it only when the fix is older than the SOS. The server drops it when the newest SOS content has no `locationAt`.

### What to change

- `SosLocation.at` already holds the fix time (`Location.time`).
- `gateway/Gateway.kt` `uploadSos`: add an optional `locationAtSec: Long?` parameter and send it.
- `sos/SosManager.kt` `uploadDirect`: pass `sos.location?.at?.div(1000)` when it is more than 2 minutes before `sos.startedAt`.
- **Mesh copies don't carry it:** `PKSOS1` has no field for it, and its last field is free text, so nothing can be appended. Changing the mesh format (a `PKSOS2`) isn't worth it for this alone. Direct uploads cover the common case.
- Optional, on the phone: show "Using your last known location (12 min old)" on SOS status in the same case. It's useful to the person too.

---

## 6. Broadcast reach (`POST /v1/broadcasts/seen`)

### Why

The dashboard shows how many phones an official broadcast reached, and uses the biggest past reach in the "This goes to everyone" confirmation.

### The contract

```
POST /v1/broadcasts/seen
{ "device": "q7Hk2mR9xT4vB1nZ", "ids": ["mfx3k2a"] }
→ { "ok": true }
```

- `device`: a random install id, 8 to 64 characters from `A-Z a-z 0-9 _ -`. It must not be linked to the person (not the phone number, not the bitchat peer id).
- `ids`: up to 20 broadcast ids, each `[a-z0-9]{1,16}`.
- The server counts each device once per broadcast, so repeating a report is harmless.

### What to change (phase 1: phones with internet)

- `data/PukaarStore.kt`: create and keep a random install id the first time it's needed. Use 16 characters from `SecureRandom`, stored in the existing prefs. Don't reuse bitchat's identity.
- Track official messages this phone has **shown**:
  - verified `OfficialPacket`s in `PukaarRuntime.watchPackets` and in the chat
  - store their ids as "seen, not reported"
- `gateway/Gateway.kt`: each round, if there are unreported ids, call `/v1/broadcasts/seen` and mark them reported on success.

That counts gateways and other phones with internet. Phones without internet aren't counted, so reach is an undercount. The dashboard calls it "reached ~N phones so far", which stays honest.

### Phase 2 (optional): count phones without internet

A tiny mesh packet `PKSEEN1|device|id1,id2`, sent once per phone per new official message, uploaded by gateways to the same route. It costs mesh traffic for every phone and every broadcast, so try it only after real-phone tests. Batch the ids and add random delay so a crowd doesn't flood the mesh.

---

## 7. Radio arrival (`via: "radio"`, `radioNode`)

When Meshtastic support is built, a phone that receives an SOS over LoRa uploads it with:

```json
{ "via": "radio", "radioNode": "!a3f2c1", … }
```

`radioNode` is the Meshtastic node id that heard it (at most 32 characters). The dashboard already shows "Radio" and the node. Nothing to do until then.

---

## Testing all of this locally

You can run the whole server and the dashboard on your laptop, then point a debug build at it.

1. Start everything in `pukaar_web/`:
   ```
   npm start
   ```
   It prints the two public keys for the app:
   ```
   PUKAAR_SERVER_SIGN_KEY=…
   PUKAAR_SERVER_BOX_KEY=…
   ```
   The Functions emulator serves the API at `http://127.0.0.1:5001/demo-pukaar/asia-south1/api`.
2. **Emulator phone:**
   ```
   adb reverse tcp:5001 tcp:5001
   ```
   Then build with:
   ```
   PUKAAR_GATEWAY_URL=http://127.0.0.1:5001/demo-pukaar/asia-south1/api
   PUKAAR_SERVER_SIGN_KEY=<printed>
   PUKAAR_SERVER_BOX_KEY=<printed>
   ```
   Pass these as Gradle properties or environment variables, then run `./gradlew :app:installDebug`.
3. **Real phones on the same Wi-Fi:** use the laptop's LAN address instead of `127.0.0.1`.
   - The debug network config only allows plain HTTP to localhost, so add the laptop's address to the debug `network_security_config` or use `adb reverse` over USB.
   - The emulators listen on `127.0.0.1` only. For phones on Wi-Fi, set `"host": "0.0.0.0"` for `functions` in `server/firebase.json` (locally; don't commit it).
   - `docs/dashboard-testing.md` (build step 7) will cover this in full.
4. Sign in to the dashboard at http://localhost:5173 as `responder@pukaar.test` / `pukaar123`. Send an SOS from the phone, and it appears within about 30 s (the gateway poll interval).

| Change | How to check |
|---|---|
| 1 | Mark attended, then Undo (Z). The phone goes back from "A rescuer is on it" to "Help notified" within about 30 s. Resolve, then Reopen: the SOS comes back as attending. Tap "I'm safe now" first: reopen doesn't change the phone. |
| 2 | Send a message from D3. The phone gets a notification and the message appears on SOS status. With a second phone as gateway and the sender offline, the message arrives through the mesh. A message edited by hand (bad signature) is ignored. |
| 3 | Turn data off and keep a SIM signal on the sender. Send an SOS through a gateway phone, then turn data back on. D3 "Family texted" shows the contacts "texted from their phone". |
| 4 | Three real phones in a line. D3 shows the hop count and the path. |
| 5 | Get a fix, move the phone indoors or turn off GPS, wait 5 minutes, then send an SOS. D3 shows "Last known, N min old". |
| 6 | Send a broadcast and open the chat on a phone with internet. Reach on the broadcast goes up by one. |

Changes 2, 4 and the mesh parts of 1 and 3 need **real phones**; Bluetooth doesn't work on emulators.

## Files you'll touch

Pukaar (`app/src/main/java/app/pukaar/`):
- `sos/Packets.kt`: `MessagePacket`, `Packets.MESSAGE`, `parse`
- `sos/SosManager.kt`: ack ordering, reopen, messages, family-text report, `locationAt`
- `sos/SosNotifier.kt`: `showControlMessage`
- `sos/MeshHops.kt` (new)
- `gateway/Gateway.kt`: status messages, relay `PKMSG1`, `reportFamilySms`, `hops`, `locationAt`, broadcasts seen
- `data/PukaarStore.kt`: install id, seen broadcasts
- `PukaarRuntime.kt`: wire `onOwnMessage`, record seen broadcasts
- `ui/screens/sos/SosStatusScreen.kt`: "From the control room"
- `ui/screens/chat/ChatRoute.kt`, `ui/PukaarNavHost.kt`: hide `PKMSG1` from chat
- `res/values/strings_pukaar.xml`, `res/values-hi/strings_pukaar.xml`

Tests (`app/src/test/java/app/pukaar/sos/`):
- `PacketsTest.kt`: `MessagePacket` with the vector above
- a new test for `nextStage`
- a new test for `familyReportBody`

Bitchat: only `mesh/MessageHandler.kt`, one line, for change 4.

Docs, once each change lands: `docs/protocol.md` (remove the "not yet handled by the Android app" notes), and `docs/architecture.md` if the gateway flow changes.
