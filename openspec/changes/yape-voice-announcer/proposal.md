# Change: yape-voice-announcer

Proposal for Chihuan v1. Status: proposed, not yet specced.

**Baseline this proposal is written against (verified from files, not assumed):**
Kotlin 2.2.10, AGP 9.3.3, Gradle 9.5.0, Compose BOM 2026.02.01 (Material3), single `:app` module,
`minSdk 24`, `targetSdk 36`, `compileSdk { version = release(37) { minorApiLevel = 1 } }`.
`./gradlew :app:testDebugUnitTest` is green on that baseline; it is not to be reverted.
The manifest currently declares only the launcher `MainActivity`: no permissions, no
`NotificationListenerService`. No Room, DataStore, Hilt, or network dependencies exist. This
proposal adds Room for local payment history and no other dependency: no network client, no
DataStore, no DI framework, and no network permission. The project is not yet a git repository;
`git init` happens at apply time.

## Why

A payment arrives as a notification, and the people this app is for — taxi and mototaxi drivers,
bodegueros, market sellers — cannot look at their phone at the moment it lands. They are driving,
weighing, or serving the next customer. Their options today: stop everything to check the screen,
take the customer's word for it, or miss the payment entirely.

Chihuan closes that gap by speaking every payment out loud — who paid, how much, and the
confirmation code — hands-free and eyes-free, entirely on-device. The user hears something like
"seis soles con cincuenta" seconds after the payment and keeps working.

The problem in one sentence: **confirming a payment requires looking at a screen, and this user's
eyes are busy.**

The product shape follows from who uses it: it is a **notification reader the user switches on
per payment source**, not a wizard and not an accounting tool. The user decides which apps talk to
them, in which voice, and can see what has already been read — with nothing else on the screen.

## What Changes

v1 adds notification listening, deterministic per-source parsing, Spanish voice announcement, a
local payment history, and a single control panel that exposes every switch the user needs.

### Scope (v1), enumerated

1. **Notification listener** — a `NotificationListenerService`, manifest-declared with
   `BIND_NOTIFICATION_LISTENER_SERVICE`, accepting only the package names present in the source
   registry. The service stays thin; all logic delegates to testable code.
2. **Source registry, three sources armed from day one** — Yape (`com.bcp.innovacxion.yapeapp`),
   BBVA, and Interbank (the Plin payment function inside each bank's own app). Each source is a data entry —
   stable id, display label, package name, parser pattern set — not a code branch in the UI. A
   later bank is an additive entry plus its pattern set. Plin is not a separate app and is not
   modelled as one.
3. **Pure-Kotlin parsers, one pattern set per source** — regex plus tolerant string matching, zero
   Android imports, unit-testable on the JVM via `./gradlew :app:testDebugUnitTest`. Every source
   yields the same extraction contract: amount, optional payer name, optional security code.
   Amounts are whole céntimos, never floating point.
4. **Numbers spoken as natural Spanish words** — `S/ 6.50` is announced "seis soles con
   cincuenta", never the platform default "seis punto cincuenta". Security codes are spoken digit
   by digit ("uno dos tres").
5. **Voice announcer with user-chosen voice** — platform `TextToSpeech` only, no third-party SDK.
   The user picks among the Spanish voices actually installed on the device; the amount stays
   Spanish words regardless of the chosen voice. If the chosen voice disappears, the app falls
   back to the automatic Spanish selection and says so, without crashing or going silent.
6. **Announcement queue** — multiple payments in quick succession are queued and spoken
   sequentially in arrival order. Never overlapping, never cut off, exactly once each.
7. **Fallback alert on parse failure** — if an armed source's notification cannot be parsed, the
   app still speaks an alert: "Llegó un pago, pero no pude leerlo." Fallback is a first-class
   outcome, and a fallback event is recorded in history as such. Silence is a defect: a broken
   parser must never be indistinguishable from "no payment arrived".
8. **Permission onboarding, reached from the panel** — a guided flow through the three setup
   hazards: (a) notification-access grant (system settings only; it cannot be requested
   programmatically), (b) the Android 13+ "restricted settings" unlock required for sideloaded
   apps, and (c) battery-optimization exemption. This flow is mandatory because v1 ships as a
   sideloaded APK, but it is a gate the user crosses once, not the app itself.
9. **Single control panel** — one screen, not a three-screen wizard. It shows which sources are
   being read with a toggle per source, the current voice with a picker, a "Probar voz" action
   that speaks a sample immediately, and the payment history. Onboarding is one action from this
   panel; settings live here too.
10. **Payment history in Room** — every announced event is persisted locally with timestamp,
    source, amount in céntimos, payer name when present, security code when present, and whether
    it was a fallback event. The panel shows today's entries and today's total. A history write
    must never delay or suppress speech.
11. **Settings** — per-source armed flags, announcements enabled/disabled, speech rate, what to say
    (amount / name / code toggles), minimum amount to announce, and the selected voice. Persisted
    in framework SharedPreferences. Room is used only for history.
12. **Default spoken content: name first, then amount, then security code.** Target example:
    "Juan Pérez te yapeó seis soles con cincuenta. Código: uno dos tres." The announcement
    sentence is *constructed by us from the extracted fields* using our own phrase templates;
    only field extraction depends on each bank's notification wording.

### Non-goals (v1) — explicitly out, with reasons

- **CSV or any other export** — no CSV, no file export, no share sheet, no copy-to-clipboard of
  payment rows. The history is a glance-back surface inside the app, nothing more. Export is not
  deferred with a placeholder; it is out of this product's shape.
- **Plin as a separate app** — Plin is a payment function inside Interbank's app, not an
  installable source. There is no "Plin" toggle, because a toggle with no parser behind it would
  be a dead control. Interbank is the toggle.
- **Other banks** (BBVA, Scotiabank, BanBif, and any other issuer) — not in v1. The registry is
  additive, so each one later costs a registry entry plus a calibrated pattern set, not a UI
  rewrite.
- **Backend / cloud / network of any kind** — no network permission is added and none should be.
  All processing is local; data never leaves the device.
- **AI/LLM parsing** — deterministic regex/tolerant matching only.
- **Ads and monetization** — no ads, no business mode, no multi-merchant, no reconciliation, no
  invoicing. The app has no advertising surface of any kind.
- **Accounting authority** — the history is a convenience record of what was read aloud. The
  Yape, Interbank, and BCP apps remain the authoritative record of the user's money; this app
  does not reconcile, dispute, or balance anything.
- **iOS** — out; notification interception is not achievable there anyway.
- **Play Store publication** — v1 ships sideloaded; Play brings a policy problem deliberately
  deferred (see Risks).
- **Non-payment notifications** (WhatsApp, SMS, promotions, other issuers) — filtered out, never
  parsed, never recorded.

### UI model

One screen. Not a wizard.

The control panel is the whole surface, top to bottom:

1. **Sources** — one row per registry entry: label, per-source status (armed / off), and a
   toggle. Arming a source is what makes its notifications speak. When notification access is
   missing, the panel says payments will not be heard and offers the onboarding entry.
2. **Voice** — the voice currently in use, a picker over the Spanish voices installed on the
   device, and the `Probar voz` action.
3. **History** — today's payments with payer, amount, and code where present, today's total, and
   fallback events marked as unreadable rather than silently omitted. Most recent first.
4. **Settings** — announcements on/off, speech rate, content toggles, minimum amount.

A separate onboarding screen exists for the permission walkthrough. It is reached from the panel
and can be left at any time; it is not the app's main screen and it does not gate the panel.

## Impact

Affected capabilities (spec deltas are authored in the spec phase; this proposal only names them):

- **source-management** (new) — source registry as data, one toggle per source, persisted armed
  state, panel reflects armed state, off source is silent with no side effects, additive source
  addition, and the Interbank-vs-Plin naming rule.
- **notification-listening** (amended) — listen to registry-declared packages via
  `NotificationListenerService`, package filtering by registry, arrival order, duplicate
  suppression, and the narrowed persistence rule (structured history only, never raw text).
- **payment-parsing** (amended) — pure-Kotlin extraction of amount / payer / code per source
  pattern set, tolerant matching, unparsed fallback classification; Interbank added as a second
  source with an explicitly unverified format.
- **payment-history** (new) — Room persistence of announced payments and fallback events, today's
  list and total, survival across process death, and the rule that a write failure never affects
  speech.
- **voice-announcement** (amended) — Spanish TTS output, user-selected voice taking precedence,
  number-to-words rendering, sequential queueing, fallback alert, `Probar voz`, audio focus, and
  in-call deferral.
- **permission-onboarding** (amended) — notification access, Android 13+ restricted-settings
  unlock, battery-optimization guidance, reachable from the control panel, with per-source state
  visible there.
- **announcement-settings** (amended) — enabled/disabled, speech rate, content selection, minimum
  amount, voice selection, and missing-voice fallback.

Code areas: `AndroidManifest.xml` (service + permission), new registry / parser / speech /
listener / history / UI code under `pe.chihuan.app`, replacement of the template greeting
in `MainActivity.kt`, new string resources, and the build files for Room plus its annotation
processor. **Build-file changes are required by this proposal** — Room runtime, Room KTX, and the
KSP plugin must be declared in `gradle/libs.versions.toml` and `app/build.gradle.kts`. Nothing
else in the build changes.

## Success Criteria

Observable and testable:

1. **Real notification → correct speech.** With notification access granted and the source armed,
   a real payment notification produces an intelligible Spanish announcement within ~2 seconds of
   arrival, containing the payer name first, then the amount in words, then the security code
   digits matching the notification.
2. **Money is spoken as money.** For every amount in the fixtures, `S/ 6.50` is announced "seis
   soles con cincuenta" — never "seis punto cincuenta".
3. **No silence on failure.** A notification from an armed source that cannot be parsed still
   triggers the fallback alert ("Llegó un pago, pero no pude leerlo."). A silent phone after a
   payment notification is a defect.
4. **Rapid payments don't collide.** Three notifications within 5 seconds produce three sequential
   announcements in arrival order — no overlap, no cutoff.
5. **`Probar voz` works before any real payment exists.** The action speaks a sample announcement
   immediately from the control panel, with notification access not granted and no listener
   connection.
6. **Parser is JVM-testable.** All parser and number-to-words tests are green under
   `./gradlew :app:testDebugUnitTest`, with zero Android imports in that code.
7. **Settings take effect live.** Changing speech rate, content toggles, minimum amount, selected
   voice, or enable/disable applies to the next announcement without an app restart.
8. **Onboarding reaches a working state.** A user on a fresh sideloaded install can follow the
   onboarding from the control panel to: notification access granted, Android 13+ restricted
   settings unlocked, battery optimization exempted — with each detectable state verifiable in-app.
9. **Each source toggle is independently observable.** With Yape armed and Interbank off, a Yape
   payment is announced and recorded while an Interbank payment produces no speech, no queue
   entry, and no history row; with Interbank armed and Yape off, the reverse holds. Arming and
   disarming takes effect on the next notification with no restart.
10. **History persists and totals correctly.** After a payment is announced, killing the process,
    and reopening the panel, the payment is listed with payer, amount, and code and is included in
    today's total; the total equals the integer sum of today's céntimos and matches the amounts
    spoken. A forced history write failure still produces the spoken announcement.
11. **Voice selection and fallback.** Picking an installed Spanish voice changes the voice heard on
    the next utterance while the amount is still spoken as words; if that voice is then removed
    from the device, the next utterance uses the automatic Spanish fallback and the panel shows a
    visible notice instead of crashing or going silent.

## Risks & Open Unknowns

Ranked:

1. **P0 — The real Yape notification wording is UNKNOWN.** No real sample has been captured or
   verified. The only reference is a third-party example ("Juan Pérez te envió S/ 50. Código:
   123"), and every statement in this proposal about the notification text is an ASSUMPTION.
   Mitigation: capturing real notification samples is the true first task; the parser must
   tolerate variants (case, spacing, currency separators, "te envió / te yapeó / recibiste") and
   fixtures must come from real notifications, not invented ones.
2. **P0 — The Interbank (Plin) notification format is UNVERIFIED, and so is its package name.**
   No real Interbank or Plin notification has been captured. The v1 Interbank pattern set is an
   assumption that must be calibrated with 5–10 real fixtures before its toggle can be trusted;
   until then the source is wired and tested but not proven. No exact Interbank wording or package
   identifier is asserted as fact in this proposal.
3. **P1 — Play Store policy risk.** Notification access is a restricted permission, and a
   payment-notification-reading app is a likely policy target (declaration required, plausible
   rejection). v1 is sideloaded, so nothing is blocked today, but this caps the "Play Store
   later" plan — and the onboarding's restricted-settings step exists precisely because of the
   sideload path.
4. **P1 — OEM battery killers.** Xiaomi/Huawei/Samsung aggressively kill listener services.
   Onboarding covers the battery-optimization exemption; residual risk remains on aggressive OEMs
   and cannot be fully engineered away in v1.
5. **P1 — History must not become a hidden dependency of speech.** Adding Room introduces
   I/O on the announcement path. If a write is ever placed before the enqueue, a slow or corrupt
   database becomes a silent phone. The spec forbids that ordering; the implementation must keep
   the write off the announcement path and off the main thread.
6. **P2 — TTS voice availability and voice drift.** The Spanish voice may be missing data on some
   devices (`LANG_MISSING_DATA`), and a user-selected voice can disappear after an engine update or
   voice-data removal. Both must degrade to the automatic Spanish fallback with a visible notice,
   never to silence and never to a crash.
7. **P2 — Regex drift on bank app updates.** A Yape or Interbank update can change wording at any
   time. Tolerant matching plus the fallback alert cap the damage: the worst case is a generic
   "llegó un pago" alert, never silence.
8. **P3 — minSdk 24 guards.** `Notification.extras` accessors, `POST_NOTIFICATIONS` (API 33+),
   TTS voice APIs, and Room's minimum API all need `Build.VERSION.SDK_INT` guards or a compatible
   dependency floor; mechanical but easy to forget.
9. **P3 — Listener reliability.** `onListenerConnected` rebind behavior and ordering guarantees
   for rapid notifications need on-device verification.
10. **P3 — `openspec/project.md` is now stale.** It states that persistence is deferred and that
    Room must not be assumed, and that Plin is out of scope for the MVP. This change contradicts
    both. `project.md` is outside this change's edit surface and needs a separate update before it
    is trusted as current context.

Open unknowns to resolve during execution (not before proposal approval):

- Exact title/text/extras shape of a real Yape notification (big-text vs ticker, where the code
  lives, whether extras carry structured spans).
- Exact package name, title/text shape, and Plin wording of a real Interbank notification.
- Whether either bank posts notifications uniformly across devices, channels, and app versions.
- Per-device TTS engine behavior (Google TTS vs OEM engines): which voices the picker can honestly
  offer, and how rate semantics vary.
- Audio routing when the user wears a Bluetooth headset/speaker (drivers typically do): works via
  the platform, but volume and routing quirks are unverified.

## Delivery Approach (outline)

High-level order only; detailed tasks are NOT part of this proposal:

1. **Capture real notification samples** — the true first task. Collect actual Yape notifications
   and actual Interbank/Plin notifications (title, text, extras) as test fixtures. Everything
   downstream depends on this; the Interbank entry stays assumption-driven until 5–10 fixtures
   exist.
2. **Source registry + parsers with fixtures** — Strict TDD: registry, per-source pattern sets,
   number-to-words, and announcement composition grow only through unit tests against captured
   fixtures (`./gradlew :app:testDebugUnitTest` is the gate).
3. **Speech layer** — `TextToSpeech`, voice enumeration and selection with fallback, sequential
   queue, `Probar voz`, audio focus, in-call deferral.
4. **History persistence** — Room entity, DAO, and the writer that sits after the speech enqueue
   and never in front of it.
5. **Listener wiring** — thin `NotificationListenerService` filtering by registry, delegating to
   the parsers, then to speech, then to history.
6. **Control panel + onboarding** — the single panel (sources, voice, history, settings) and the
   permission onboarding reached from it.
7. **Fallback path + polish** — unparseable-notification alert, missing-voice notice, end-to-end
   manual verification on a real device.

## Changed-Line Forecast

Against the 800-line review budget. Generated files excluded; estimates are honest ±30%. The
previous forecast (~1,585) predates the control panel, the second parser, Room, and the voice
picker, so it is superseded.

| Area | Additions | Deletions |
| --- | --- | --- |
| Manifest (service + permission) | ~15 | ~2 |
| Source registry + unit tests | ~150 | 0 |
| Yape parser + unit tests | ~360 | 0 |
| Interbank parser + unit tests (assumption-driven) | ~220 | 0 |
| Number-to-words conversion + unit tests | ~240 | 0 |
| Announcement builder (incl. fallback phrase) + tests | ~170 | 0 |
| TTS announcer + sequential queue | ~130 | 0 |
| Voice enumeration and selection (installed Spanish voices) | ~140 | 0 |
| Thin listener service | ~55 | 0 |
| Settings (SharedPreferences) | ~110 | 0 |
| Room: entity, DAO, database, history writer + tests | ~230 | 0 |
| UI: control panel, onboarding, history view, voice picker, settings, state wiring | ~700 | 0 |
| Strings/resources | ~70 | 0 |
| Template greeting replaced (MainActivity) | ~40 | ~30 |
| **Total** | **~2,630** | **~32** |

**Verdict: the change exceeds the 800-line review budget (~3.3×), so chained PR slicing IS
required**, consistent with `delivery_strategy: auto-chain`. Proposed 6-slice split, each slice
under budget:

1. **Slice 1 — Parsing core** (pure Kotlin; no UI, no Android imports): source registry, Yape
   pattern set, number-to-words, fixtures, all tests. ~750 lines.
2. **Slice 2 — Composition + second source**: announcement builder, Interbank pattern set and its
   placeholder fixtures, tests. ~390 lines.
3. **Slice 3 — Speech layer**: TTS announcer, sequential queue, voice enumeration and fallback,
   thin service, manifest change. ~340 lines.
4. **Slice 4 — History persistence**: Room entity/DAO/database, history writer that runs after the
   speech enqueue, tests. ~230 lines.
5. **Slice 5 — Settings + voice picker UI**: settings storage and screen, voice list presentation,
   strings. ~350 lines.
6. **Slice 6 — Control panel**: panel shell, per-source toggles, history view with today's total,
   onboarding entry, MainActivity replacement. ~570 lines.

Slice 1 is the largest but is pure-Kotlin TDD work behind a hard test gate; slices 3 through 6 are
thin integration and UI layers.

## Log

Requirement changes recorded against this proposal. The user's words are kept verbatim so the
change stays traceable; they are not translated or corrected here.

### 2026-10-25 — Control panel, two sources, local history, voice selection

> "A ver, mira, la aplicación va a ser más tipo como un activador de notificaciones para leerles en voz alta. Por ejemplo, va a haber lo que sé, dos opciones: activar, BSP o ya PO Plin. y siga. O sea, es que cuando el usuario le ponga activar uno, activar el otro, pues arma el de la notificación. No debe haber nada, no va a haber nada de anuncios ni esas cosas. Solo van a aparecer los botones esos de cualquiera que lea, con qué voz, tu historial y esas cositas, ya puedes ver absolutamente todo. Es como un sistema de permisos dentro de la misma aplicación, con más control del usuario, ¿sabes? Era lo que yo buscaba, pero como lo estas planteando ?"

> "no coloques la exportacion de csv, pero si que tenga un historial tipo quien te yapeo y asi, aunque no es tan necesario porque eso ya lo gestiona lo que es las mismas apps de interbank y el de bcp"

What these change, in order:

1. The three linear screens become **one control panel**: per-source toggles, voice, `Probar voz`,
   and history on a single screen. Onboarding survives as a reachable sub-flow, not the app.
2. **Three sources from day one**, Yape, BBVA and Interbank (Plin inside each bank's own app), each
   armable by its own toggle, with the registry modelled as data. BBVA was added on
   2026-10-25 by explicit user request; see the Log entry below.
3. **Local payment history** is in scope (Room); **CSV export is not**, and no export placeholder
   is left behind. The history is a glance-back convenience, not an accounting system.
4. The user chooses **which installed Spanish voice** reads the payments, with a defined fallback
   when that voice disappears.
5. The **name-first announcement order** is unchanged and remains authoritative:
   "Juan Pérez te yapeó seis soles con cincuenta. Código: uno dos tres."

### 2026-10-25 — BBVA added as a third source, onboarding first

> "vale necesito que al principio de la app diga instalar permisos y cosas asi, que existan los switchs que permitan leer las notificaicoens de las apps de bcp yape y bbva y plin mas que todo, otra cosa me gustaria que en este caso yo lo correre en mi celular a traves de android studio entonces , primero necesito que pushees y me digas que pruebas hacer en la pap"

What this changes:

1. **BBVA joins the registry**, so v1 has three sources: Yape, BBVA and Interbank. Plin has no
   toggle of its own; it is reached through each bank's app.
2. **Permission onboarding is the launch screen** when notification access is missing, not a
   sub-flow the user has to find. The panel is the destination once access exists.
3. BBVA's and Interbank's package names remain **unverified**. Until the debug capture reveals them,
   the registry matches those two by app-label fragment and the panel states that verification is
   pending. A label bridge is temporary by definition and must be replaced with exact package names.
