# Design brief

This brief is the input for designing Pukaar in Claude Design. It covers who uses the app, the conditions they're in, the principles every screen must follow and the visual direction. The screen-by-screen specification is in [screens.md](screens.md).

## The product in one sentence

Pukaar lets people in a disaster call for help and talk to each other when there is no network, by passing messages phone to phone over Bluetooth and over radio.

## Who uses it

### Meena, 38, flood-affected village

Uses a ₹8,000 Android phone, mostly in Hindi. Comfortable with WhatsApp and YouTube, not with settings. During a flood she is on a rooftop with her two children and her mother-in-law. Her phone has 30% battery and no signal. She needs to tell someone where she is, and know that someone got it.

### Arjun, 22, college student and volunteer

Has a good phone with mobile data in a nearby town. He installed Pukaar during a preparedness drive. He doesn't need to do anything: his phone silently relays messages to the server. He might check the Disaster Relief chat to see what's happening.

### Ramesh, 61, retired, lives alone

Poor eyesight, uses large text. Would struggle with small buttons or English-only labels. Needs SOS to be obvious and calling to be one tap.

### Inspector Kavita Rao, district control room

Works at a desktop with two monitors during an emergency, often for long shifts. Needs to see where help is needed, prioritise, assign teams and mark progress, without missing anything.

## Conditions of use

Design for the worst moment, not the average one:

- **Panic and stress.** People can't read paragraphs or work out unfamiliar icons.
- **Low battery.** Every screen should be cheap to show. Dark backgrounds save power on OLED screens.
- **Bright sunlight or total darkness.** Contrast must hold in both.
- **One hand, wet or shaking.** Holding a child, a railing or a torch.
- **Low literacy or second-language reading.** Icons always come with words.
- **Cheap phones.** Small screens (360 dp wide), slow processors, no fancy effects.
- **Long text in Hindi.** Hindi labels often run 30 to 50% longer than English. Layouts must not break.

## Design principles

1. **SOS is always one tap away.** Every main screen shows a way to reach SOS.
2. **Always show the connection state.** Users must always know whether they're online, on the mesh (and with how many phones), on radio, or cut off. This is the most important piece of information after SOS.
3. **Always show whether a message got through.** Every message and SOS shows its delivery state in plain words, not just ticks.
4. **Words and icons together.** No icon-only buttons on primary actions.
5. **Calm, not alarming.** The interface should lower panic. Red is reserved for SOS and real danger only.
6. **Big targets.** Primary actions at least 56 dp tall. The SOS button much larger.
7. **Works without explanation.** No tutorials needed to send an SOS.
8. **Respect the battery.** No decorative animation, no autoplay, no heavy imagery.

## Visual direction

Pukaar must not look like a generic Material template, a government form, or a red-cross medical app. It should feel trustworthy, calm and distinctly Indian, and it should feel like a tool that keeps working when everything else has failed.

### Concept: the call that spreads

"Pukaar" means a call for help. The core visual idea is a call spreading outward, like ripples or a signal passing from person to person. Use it with restraint:

- Concentric rings around the SOS button and in the logo.
- A small "hops" visual showing how a message travelled (phone, phone, phone, server).
- Connection status shown as rings that fill as more nearby phones join.

### Colour

Exact colours are in [design-tokens.md](design-tokens.md). In short:

- Primary is an indigo blue from a Material 3 theme.
- Five status colours carry meaning: SOS red, mesh teal, radio violet, confirmed green and warning amber.
- SOS red is reserved for SOS only.
- Dark is the default theme. Light and a high-contrast "Sunlight mode" are also available.

Because the primary colour is a familiar Material blue, Pukaar's own character must come from the ripple motif, the status colours, the custom icons, Mukta, the illustrations and the custom components (SOS button, connection pill, delivery states, compass), not from the primary colour alone.

### Typography

**Mukta** for everything, in Hindi and English. Details are in [design-tokens.md](design-tokens.md#typography).

### Shape and iconography

- Rounded but not bubbly: medium corner radius.
- Thick-stroke, rounded icons that stay readable at small sizes.
- Custom icons for: mesh, radio, gateway, shelter, SOS, safe.

### Imagery

No stock photos. Use simple illustrations only in onboarding, empty states and guides, drawn in the brand style, with Indian settings and people.

## Tone of voice

Short, direct, warm. Speak like a calm neighbour, not a government notice or a tech product.

| Situation | English | Hindi |
|---|---|---|
| SOS sending | Sending your SOS | आपका SOS भेजा जा रहा है |
| Server got it | Help has been notified | मदद को सूचना मिल गई है |
| Rescuer attending | A rescuer is on it | बचाव दल आपकी मदद के लिए आ रहा है |
| No network at all | No phones nearby yet. We'll keep trying. | अभी पास में कोई फ़ोन नहीं है। हम कोशिश करते रहेंगे। |
| Sent by radio | Sent by radio | रेडियो से भेजा गया |

Have the Hindi copy checked by a native speaker before the final design.

## Connection states (design these as a set)

| State | Meaning | Suggested treatment |
|---|---|---|
| Online | Phone has internet | Green dot, "Online" |
| Mesh | No internet, 1 or more Bluetooth peers | Teal rings, "Connected to 4 phones" |
| Radio | LoRa node paired, no peers | Violet, "Radio connected" |
| Isolated | Nothing available | Amber, "No connection. Messages will wait." |
| Gateway | Online and relaying others' messages | Small badge, "Helping 3 people" |

## Platforms

| Product | Platform | Notes |
|---|---|---|
| Pukaar app | Android phones | Portrait only, 360 dp minimum width |
| System surfaces | Android | Quick Settings tile, lock-screen widget, persistent notification |
| Rescuer dashboard | Desktop web | 1440 px wide primary, usable at 1280 px. Light and dark themes for long shifts |

## Deliverables expected from the design phase

1. Logo and app icon.
2. Components and screens built on [design-tokens.md](design-tokens.md), in both themes.
3. Component set: buttons, SOS button, status bar, message bubbles with delivery states, cards, list rows, inputs, tabs, toasts, dialogs.
4. All screens in [screens.md](screens.md), in English and Hindi, in the dark theme, plus key screens in light.
5. The Android system surfaces.
6. The dashboard screens.
7. A custom map style for the offline map.
