# Requirements

Status: **Frozen**. Changes need agreement from the whole team and an entry in the [decision log](decisions.md).

## Problem

In floods, earthquakes, cyclones and fires, mobile towers and internet are often the first services to fail. People who are trapped can't tell anyone where they are, and rescue teams have no live picture of who needs help or where.

## Goal

Let anyone with an Android phone send a message or SOS that reaches rescuers and family, even with no internet, no mobile data and no SIM.

## Users

| User | Needs |
|---|---|
| Person in a disaster area | Call for help fast, tell family where they are, find a shelter, know what to do |
| Family and emergency contacts | Learn quickly that someone needs help and where they are |
| Volunteer or bystander with signal | Relay other people's messages without extra effort |
| Government and rescue teams | See who needs help, where, and how urgently, and track what has been handled |

## Functional requirements

### Messaging

| ID | Requirement |
|---|---|
| FR-1 | The app has a shared group chat called "Disaster Relief" that every user can read and post to. |
| FR-2 | Messages are delivered between phones over Bluetooth with no internet, mobile data or SIM. |
| FR-3 | Messages hop through multiple phones to reach phones outside direct Bluetooth range. |
| FR-4 | Any phone running Pukaar with internet uploads received messages to the server automatically. |
| FR-5 | When no Bluetooth peers are available, or delivery isn't confirmed within 30 seconds, messages are sent over LoRa radio through a paired Meshtastic node. |
| FR-6 | The user can choose to send a message over radio manually. |
| FR-7 | The sender sees the delivery status of each message: sending, relayed, reached server, attended. |
| FR-8 | A user can attach their location to a chat message. |

### SOS

| ID | Requirement |
|---|---|
| FR-9 | The user can send an SOS containing their coordinates, time, battery level, number of people with them and a short custom message. |
| FR-10 | When the phone has cellular signal, the SOS is sent directly as SMS to the user's emergency contacts, with a Google Maps link. |
| FR-11 | When the phone has no signal, the SOS travels over the mesh or LoRa to the server, which sends the SMS to the emergency contacts. |
| FR-12 | Every SOS appears on the rescuer dashboard. |
| FR-13 | SOS can be triggered from a Quick Settings tile, a lock-screen widget and by shaking the phone, including when the app is closed. |
| FR-14 | Every SOS trigger shows a 5-second countdown with a cancel button. |
| FR-15 | The user can mark themselves safe, which updates the SOS on the dashboard. |
| FR-16 | The user can save, edit and remove emergency contacts. |

### Calling

| ID | Requirement |
|---|---|
| FR-17 | One-tap calls to 112, police, ambulance, fire, the women's helpline and the district control room. |
| FR-18 | The calling screen shows whether there is a tower signal and suggests SOS when there isn't. |

### Maps and direction

| ID | Requirement |
|---|---|
| FR-19 | The app shows an offline map of a pre-downloaded region. |
| FR-20 | The map shows shelters, hospitals and police stations. |
| FR-21 | The app shows a compass arrow and straight-line distance to the nearest shelter, or to one the user picks, without internet. |
| FR-22 | The user can download or remove map regions while online. |

### Preparedness

| ID | Requirement |
|---|---|
| FR-23 | Offline guides for floods, earthquakes and fires, organised as before, during and after. |
| FR-24 | Tickable checklists for home safety and a go-bag, saved on the device. |
| FR-25 | Guide content comes from official sources such as NDMA. No AI-generated content. |

### Language

| ID | Requirement |
|---|---|
| FR-26 | The whole app is available in Hindi and English, chosen at first launch and changeable later. |
| FR-27 | Adding a regional language requires translation files only, not code changes. |

### Rescuer dashboard

| ID | Requirement |
|---|---|
| FR-28 | Government and rescue staff log in with an account. Roles: viewer and responder. |
| FR-29 | A live map shows every SOS and located message as it arrives. |
| FR-30 | Each SOS shows coordinates, time sent, people count, battery, message and how it arrived (direct, Bluetooth or radio). |
| FR-31 | Responders can change an SOS status: new, attended, resolved. Each change is sent back to the user. |
| FR-32 | The dashboard can filter by status, time and area. |
| FR-33 | The dashboard shows the Disaster Relief group chat. |

## Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-1 | An SOS can be sent within 2 taps from anywhere in the app, or with no taps through shake. |
| NFR-2 | All features except calling and the dashboard work with no network at all. |
| NFR-3 | An SOS packet fits in one LoRa packet (about 200 bytes). |
| NFR-4 | The mesh runs in the background as a foreground service with a visible notification. |
| NFR-5 | The app reduces Bluetooth scanning when battery is below 20%. |
| NFR-6 | The app works on Android phones with Bluetooth Low Energy and runs smoothly on low-end devices. |
| NFR-7 | Duplicate messages arriving through several paths are shown once in the app and stored once on the server. |
| NFR-8 | Location is shared only in an SOS or when the user attaches it to a message. |
| NFR-9 | Users are told during onboarding that Disaster Relief group messages are visible to rescuers on the dashboard. |
| NFR-10 | Text and touch targets stay readable and usable in bright sunlight, darkness and with one hand. |

## Non-goals

These are out of scope for the hackathon:

- An iOS app.
- AI or LLM features.
- Smartwatch and wearable integration.
- Satellite messaging.
- Private one-to-one encrypted chat as a feature we design for (bitchat may already support it).
- Turn-by-turn or calculated walking routes.
