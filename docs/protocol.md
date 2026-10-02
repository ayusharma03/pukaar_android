# Pukaar protocol

How Pukaar messages travel between phones, and between a gateway phone and the rescuer server. Code: `app/src/main/java/app/pukaar/sos/Packets.kt` (packets) and `app/pukaar/gateway/Gateway.kt` (server). Golden vectors: `app/src/test/java/app/pukaar/sos/PacketsTest.kt`.

## 1. On the mesh

Pukaar rides on bitchat's **public mesh timeline**. Every Pukaar packet is a plain bitchat public message whose text starts with a tag. Ordinary text is a normal Disaster Relief chat message.

Fields are separated by `|`. Free-text fields have `|` replaced by `/` and newlines by spaces before sending. The last field of a packet may contain anything.

| Packet | Format | Who sends it |
|---|---|---|
| SOS | `PKSOS1\|id\|seq\|lat,lon\|acc\|time\|battery\|people\|flags\|name\|message` | The person in trouble |
| Safe | `PKSAFE1\|id\|time` | The person, after "I'm safe now" |
| Ack | `PKACK1\|id\|status\|time\|by` | A gateway phone, relaying a server status change |
| Official | `PKOFF1\|id\|from\|text` | A gateway phone, relaying a dashboard broadcast |

- `id`: 8 hex characters, random per SOS. `seq` goes up with each "Update details".
- `lat,lon`: decimal degrees with 5 places, or empty when there is no fix. `acc`: metres, or empty.
- `time`: Unix seconds when the SOS started. `battery`: percent.
- `flags`: bit mask. Injured 1, Trapped 2, Need water 4, Need medicine 8, Child or elderly 16.
- `name`: at most 24 UTF-8 bytes.
- `status`: `N` help notified, `A` rescuer attending, `R` resolved.

**Size (NFR-3):** an SOS packet is at most **200 UTF-8 bytes**, so it fits one LoRa packet. The message is truncated to fit, without splitting a character. The countdown shows the bytes left.

Example:

```
PKSOS1|a1b2c3d4|2|25.98140,85.67210|12|1790000000|30|4|18|Meena Kumari|On the roof of the blue house
```

**Chat location (FR-8):** a chat message with a location ends with `[loc:lat,lon]`, for example `Water near the temple [loc:25.98141,85.67209]`.

### Sending rules (sender phone)

1. On send: broadcast the SOS packet at once.
2. Send again whenever a phone not seen before comes into direct range, and on a timer: every 30 s for the first 5 minutes, then every 2 minutes, until the SOS is closed.
3. If there is a mobile signal and SMS permission: text each emergency contact directly (FR-10). Retry up to 3 times, including later when signal returns.
4. If the phone has internet and a gateway URL is set: upload directly, including the profile and contact numbers, so the server can text family (FR-11).
5. Duplicates are fine. Receivers keep one card per `id`, the highest `seq` wins (NFR-7).

**Privacy decision (needs team sign-off):** contact phone numbers and medical notes are **never** put on the mesh, because the public mesh is readable by anyone nearby. They only go straight from the sender's phone to the server. So when an SOS reaches the server only through other phones, the server has no contact numbers to text. The sender's phone texts family itself as soon as it has signal. Closing this gap needs a server public key, so the contact details can be encrypted for the server alone.

## 2. Gateway phone ↔ rescuer server (HTTPS, JSON)

Any phone with internet is a gateway (FR-4). Set the server base URL at build time with the Gradle property or environment variable `PUKAAR_GATEWAY_URL`. With no URL, the gateway is off. A Firebase HTTPS Cloud Function can implement these endpoints.

| Method and path | Body or query | Response |
|---|---|---|
| `POST /v1/sos` | `{id, seq, lat?, lon?, accuracyM?, time, battery, people, flags[], name, message, via: "direct"\|"mesh", relayedBy?, phone?, bloodGroup?, medicalNotes?, contacts?: [{name, phone}]}` | `{status: "new"\|"attended"\|"resolved", by?, smsSent?}` |
| `POST /v1/safe` | `{id, time}` | 2xx |
| `POST /v1/messages` | `{messages: [{id, sender, text, time, lat?, lon?}]}` | 2xx |
| `GET /v1/sos/status?ids=a,b` | | `{statuses: [{id, status, time, by}]}` |
| `GET /v1/broadcasts?since=<unix s>` | | `{items: [{id, from, text, time}]}` |

- Any 2xx response to `POST /v1/sos` counts as "Help notified" for that SOS.
- `smsSent: true` tells the sender's phone the server texted the family.
- The gateway polls status for SOS it relayed and broadcasts a `PKACK1` into the mesh when a status changes, so the sender learns even with no internet.
- New dashboard broadcasts are relayed into the mesh as `PKOFF1`.
- The gateway polls every 30 seconds while online.

## 3. Known gaps

- **No authentication on Ack and Official packets.** Anyone on the mesh can forge "help notified" or an "official" message. Sign them with a server key before real use.
- **No LoRa yet.** Meshtastic pairing and "Send by radio" (FR-5, FR-6) are not built. The packet size already fits.
- **Per-hop relay counts aren't known.** bitchat doesn't report how many hops a broadcast took, so the app shows how many nearby phones took the SOS.
