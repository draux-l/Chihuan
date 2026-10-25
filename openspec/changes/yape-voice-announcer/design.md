# Technical Design — yape-voice-announcer

Change: `yape-voice-announcer` · Project: `notisyape` · Status: designed (apply phase next)
Date: 2026-10-25 · Execution mode: interactive · Delivery: auto-chain (6 slices) · Review budget: 800 lines/slice

Design inputs: `proposal.md` (incl. `## Log`), the seven spec deltas under `specs/*/spec.md`, `openspec/project.md`, and the verified build baseline (Kotlin 2.2.10, AGP 9.3.3, Gradle 9.5.0, Compose BOM 2026.02.01, single `:app`, `minSdk 24`, `targetSdk 36`, `compileSdk release(37) { minorApiLevel = 1 }`, Java 11 target, `testDebugUnitTest` green).

Standing rule honored throughout: no statement about real Yape or Interbank notification wording, package identifiers, or notification shape is presented as verified fact. Yape's package `com.bcp.innovacxion.yapeapp` carries the proposal's own assumption marker; the Interbank identifier is an explicitly unverified constant (§4).

---

## 1. Architecture overview

One process, one screen, four planes. The pipeline a notification travels is strictly linear:

```
NotificationListenerService (thin, Android)
  → PackageFilter (pure)          exact registry match + armed flag
  → NotificationTextExtractor (pure)   title/bigText/textLines/text precedence, normalization
  → DuplicateSuppressor (pure)    in-memory identity set
  → PaymentParser (pure)          per-source pattern set → Parsed | Unparsed
  → SettingsGate (pure decision)  enabled toggle, min-amount (parsed only)
  → SpeechAnnouncer (Android)     actor queue → TTS → utterance request
       └── at utterance request: HistoryWriter (separate scope, Room) ← never in front of speech
  → Control panel (Compose)       observes state holders; never touches the parser
```

Two properties this pipeline exists to guarantee, both taken from the specs:

- **Speech is never downstream of storage.** The Room write is initiated after the utterance is handed to the TTS engine, from a separate coroutine scope, inside a try/catch that cannot throw back into speech or the listener (satisfies `payment-history` "write never blocks/delays/suppresses speech").
- **Silence is a defect.** Every accepted notification from an armed source ends in an utterance — parsed announcement or fallback phrase — unless the user disabled announcements (which overrides the fallback guarantee) or a call is in progress (which defers, never discards).

## 2. Package layout and dependency direction (Decision 1)

All code lives in the single `:app` module under `pe.chihuan.app`. No multi-module split: the registry/parser boundary is enforced by package convention and review, not by Gradle. That is a deliberate v1 tradeoff (ADR-1).

```
pe.chihuan.app
├── MainActivity.kt                 panel shell (replaces template greeting); screen switch, no nav library
├── ChihuanApplication.kt         owns all singletons (manual wiring, no DI framework)
├── core/                           PURE KOTLIN — zero android.* imports; JVM-testable
│   ├── model/                      ParseOutcome, PipelineOutcome, ExtractedText, QueueItem, SettingsSnapshot, ContentSelection
│   ├── parser/                     PaymentParser (dispatch), PatternSet, YapePatternSet, InterbankPatternSet,
│   │                               AmountParser, NameParser, CodeParser, TextNormalizer
│   ├── amount/                     NumberToWords (integer céntimos → Spanish words)
│   ├── announce/                   AnnouncementComposer (phrase templates, digit-by-digit code, fallback phrase)
│   ├── queue/                      QueueOrderingEngine (pure FIFO state machine, exactly-once bookkeeping)
│   ├── dedup/                      DuplicateSuppressor (identity keys, ≥10-min retention, bounded size)
│   ├── filter/                     PackageFilter (exact match + armed check)
│   ├── registry/                   SourceRegistry + SourceEntry (data only)
│   └── text/                       NotificationTextExtractor (title/bigText/textLines/text precedence)
├── data/
│   ├── settings/                   SettingsStore (SharedPreferences), SettingsPolicy (pure validation rules)
│   └── history/                    PaymentEntity, PaymentDao, AppDatabase, HistoryWriter, TodayWindow (pure day boundaries)
├── listener/                       NotisNotificationListenerService (thin), NotificationReader (extras access), PaymentPipeline (per-notification orchestration + exception containment)
├── speech/                         SpeechAnnouncer (TTS + actor + audio policy glue), VoiceCatalog, VoiceSelection (pure decision over voice descriptors), AudioPolicy, TtsStatus
├── permission/                     PermissionStateSource (notification access, battery optimization)
└── ui/
    ├── panel/                      PanelScreen, PanelViewModel, PanelState
    ├── history/                    HistorySection, display formatting (céntimos → "S/ 6.50" as pure function)
    ├── voice/                      VoicePicker
    ├── settings/                   SettingsSection
    └── onboarding/                 OnboardingScreen (three steps)
```

Dependency direction (arrows point at the depended-on plane):

- `core` depends on nothing (only `kotlin-stdlib`; `java.util.Locale`, `java.security.MessageDigest`, `java.util.Calendar` are JVM, not Android).
- `data.settings`, `data.history` → `core.model` (+ Room/Android in `data.history`).
- `speech` → `core` (queue engine, composer, model) + Android (`TextToSpeech`, `AudioManager`).
- `listener` → `core` (filter, extractor, dedup, parser) + `speech` + `data.history` + Android service.
- `permission` → Android only; exposes state objects.
- `ui` → `data.settings`, `data.history`, `speech` status, `permission` state, and `core.registry` **for labels and order only**; `ui` never references `core.parser` and never invokes parsing.

The one soft edge: `SourceEntry` carries a pattern-set reference (§4), so `core.registry` compile-depends on `core.parser`. This is intentional (type-safe dispatch, one less indirection); the UI rule is not violated because UI reads only entry metadata (`id`, `label`) and never calls `parse`. Enforced by review, documented here so apply-phase reviewers check it.

## 3. Build additions (verified versions)

The proposal freezes dependencies at "Room only". These are the exact additions; nothing else in the build changes.

`gradle/libs.versions.toml`:

- `[versions]`: `room = "2.8.5"` and `ksp = "2.2.10-2.0.2"`.
- `[libraries]`: `androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }`, `androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }`, `androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }`.
- `[plugins]`: `ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }`.

Root `build.gradle.kts`: `alias(libs.plugins.ksp) apply false`. `app/build.gradle.kts`: add `alias(libs.plugins.ksp)` to `plugins`, `implementation(libs.androidx.room.runtime)`, `implementation(libs.androidx.room.ktx)`, `ksp(libs.androidx.room.compiler)`, and schema export via the KSP argument `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` (one line; avoids adding the separate `androidx.room` Gradle plugin — ADR-6).

Compatibility, verified against the baseline:

- **minSdk 24 is compatible.** Room 2.8.0 raised the library floor from API 21 to **API 23** (androidx Room release notes, 2025-08-27); 24 ≥ 23. No desugaring, no compat shim needed.
- **Kotlin 2.2.10 is compatible.** Room 2.8.x requires Kotlin language 2.0+; 2.2.10 satisfies it.
- **KSP `2.2.10-2.0.2`** is the Kotlin-2.2.10-matched KSP release (KSP2 generation, which Room 2.7+ fully supports). Fallback if any AGP 9.3.3 incompatibility surfaces during apply: the version-independent KSP line `2.3.x` (e.g. `2.3.12`), which decoupled KSP versions from Kotlin versions. This is a one-line catalog change.
- **compileSdk 37.1** exceeds Room 2.8.x's requirements; the existing AndroidX set that forced compileSdk 37 is untouched.
- `kotlinx-coroutines` is used (speech actor, history scope) and arrives **transitively** via the present `lifecycle-runtime-ktx` / `core-ktx`; no new dependency is declared, honoring the proposal's "Room and nothing else". Known fragility (a future lifecycle bump could drop it) is listed in §16.

`AndroidManifest.xml` (the only manifest change, ~15 lines): declare the service — `android:name=".listener.NotisNotificationListenerService"`, `android:exported="true"`, `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"`, intent-filter action `android.service.notification.NotificationListenerService`. No `POST_NOTIFICATIONS` (v1 posts nothing). No network permission, now or ever.

## 4. Source registry (Decision 2)

The registry is a single top-level value in `core/registry/SourceRegistry.kt`:

```
SourceEntry(id: String, label: String, packageName: String, patternSet: PatternSet)
SourceRegistry.entries: List<SourceEntry>   // v1: exactly yape, interbank — in this order
```

- `yape` → label `Yape`, package `com.bcp.innovacxion.yapeapp` (exact-equality match only), `YapePatternSet`.
- `interbank` → label `Interbank`, package = **one unverified constant held in this entry only**, `InterbankPatternSet` (placeholder patterns). The constant is declared directly in the entry with a `// UNVERIFIED — replace from a real Interbank/Plin install; see proposal P0` marker comment. The literal string appears exactly once in the codebase, satisfying the spec's one-place rule. No test, no other file repeats the literal.
- There is no `plin` entry, no `plin` id, and no `Plin` toggle anywhere; Plin payments arrive through the `interbank` entry.

Adding a bank later = one new `SourceEntry` line, one pattern-set object, its fixtures. The panel renders `SourceRegistry.entries` in order, so the row appears with its own toggle with zero panel edits — the spec's additive scenario is a direct consequence of this representation.

Calibration is a one-line change: replace the Interbank package constant (and, separately, pin `InterbankPatternSet` to captured fixtures). Because the constant lives only in the registry entry and is never guessed, unknown packages stay ignored during the uncalibrated period. Calibration completeness is tracked in the entry's marker comment and the proposal Log, not in runtime state.

## 5. Parser contract and number-to-words (Decision 3)

**Input to the parser** is a normalized `ExtractedText` produced by `NotificationTextExtractor`: the thin Android shim (`listener/NotificationReader`) reads title / `EXTRA_BIG_TEXT` / `EXTRA_TEXT_LINES` / `EXTRA_TEXT` as nullable strings; the pure extractor applies the spec's precedence (title always included; big text *replaces* text; otherwise text lines joined by `\n`; otherwise text), then normalizes (Unicode NFC, collapse whitespace runs). Empty/null extras yield an empty body without throwing — the extraction decision is pure and JVM-tested; only the extras reads are Android.

**Dispatch**: `PaymentParser.parse(sourceId: String, text: String): ParseOutcome` looks up the registry entry and runs only that source's pattern set. An undeclared source id is a programming error (`IllegalStateException`), not an input to tolerate — matching the spec.

**Output types** — two levels, deliberately:

- The parser's own contract is exactly the spec's two outcomes: `Parsed(amountCents: Long, payerName: String?, securityCode: String?)` and `Unparsed` (returned, never thrown). `Parsed` requires an amount > 0; name and code are independently optional; the amount is integer céntimos throughout (no floating point anywhere in the feature).
- The pipeline-level union adds the upstream decision the parser never sees: `PipelineOutcome = Ignored | Parsed | Unparsed`. `Ignored` is produced by `PackageFilter` (unknown package, or source present but off) *before* extraction of anything beyond the package name — no parse, no utterance, no row. The parser stays two-outcome because that is what the spec fixes; `Ignored` lives at the filter layer so the two contracts never blur.

Pattern sets implement the shared contract (amount rules, separator rules, first-amount-wins, tolerant verb matching, labeled-code precedence, name-bounds/label-rejection). Yape's set tolerates the verb variants fixed by the proposal (`te envió / te envio / te yapeó / te yapeo / te transfirió / te transfirio`, case- and accent-insensitive). Interbank's set is a placeholder calibrated later; both sets are equally tolerant and share the separator/amount logic — the shared logic is implemented once in `AmountParser` and called by both, so `S/ 6,50` and `S/ 6.50` resolve identically regardless of source (no parallel implementation, per spec).

The amount regex captures at most 9 integer digits (S/ 10 M ceiling); anything larger is Unparsed → fallback alert, never a crash and never a fabricated amount.

**Number-to-words API** (`core/amount/NumberToWords.kt`):

- `fun centsToSpanish(cents: Long): String` — input domain: integer céntimos, `0 ≤ cents ≤ 999_999_999` (guaranteed by the parser's digit cap). Output: the complete amount phrase — `un sol con un céntimo`, `seis soles con cincuenta`, `cincuenta céntimos`.
- Internal cardinal renderer for `0..999_999_999` implementing exactly the spec's normative rules (`un` before the noun, `veintiún`, `<tens> y un` for 31/51/…, `cien` vs `ciento`, `mil` with no article, accented forms preserved). Singular/plural noun selection (`sol/soles`, `céntimo/céntimos`) is part of this API.
- The digit-by-digit code rendering is **not** in `NumberToWords` — it belongs to the composer (§ below), because it is a sentence property (`045` → `cero cuatro cinco`), not an amount rendering.

## 6. Who owns the speech queue (Decision 4)

**One announcer instance, Application-scoped.** `ChihuanApplication` lazily constructs `SpeechAnnouncer` (which owns the single `TextToSpeech` instance) and exposes it to both the listener service and the UI. It therefore survives Activity destruction trivially and is shared by `Probar voz` and real payments — no binding ceremony between the panel and the listener service (ADR-4). It does *not* survive process death; the system rebinds the listener service after process death, `Application.onCreate` rebuilds the announcer, and queued-but-unspeechen items are lost — an accepted consequence, consistent with the in-memory dedupe stance (§8).

**Concurrency primitive, stated precisely: a single-consumer coroutine actor.**

- One unbounded `Channel<QueueItem>` (FIFO by channel order; unbounded because the spec forbids a pending-items cap).
- One consumer coroutine on `Dispatchers.Main.immediate` inside `SpeechAnnouncer`'s own scope. Single-threaded consumption + single-threaded completion callbacks = no locks, no interleaving, natural arrival order.
- For each item the actor: (a) checks the call gate (§7) and parks without dequeuing while a call is active; (b) requests audio focus on the first item of a drain cycle; (c) resolves the voice and rate from a fresh settings snapshot; (d) calls `tts.speak(text, QUEUE_ADD, params, utteranceId)` with a monotonic per-item utterance id; (e) suspends on a `CompletableDeferred` that `UtteranceProgressListener.onDone/onError/onStop` completes for exactly that id; (f) on completion or error, takes the next item.
- **Exactly-once** is guaranteed by construction: one speak call per accepted item, no internal retry of a spoken utterance (a synthesis error advances the queue and marks TTS unhealthy; it never re-speaks the same item). Completion callbacks are matched by utterance id; a stray callback for an unknown id is ignored.
- **Disable** (announcements off): the store check happens at enqueue-time gating *and* the actor is instructed to stop the current utterance (`tts.stop()`) and drain-discard the channel; payments that arrived while disabled are never announced on re-enable.
- **Pure ordering core.** The ordering/counter decisions (enqueue, peek, mark-done, clear, pending count) are extracted into `core/queue/QueueOrderingEngine` — a synchronous pure-Kotlin state machine the actor applies. This is what makes queue ordering JVM-testable without `kotlinx-coroutines-test` (§12), and the pending count it tracks is the number the panel displays.
- TTS init is asynchronous; items enqueued before `onInit` success are held in the engine until the engine is healthy. Init failure is surfaced as unhealthy TTS state (§15) — items arriving while the engine is unusable are dropped without rows (no utterance → no record).

The actor runs on main-immediate because all its non-TTS work is trivial and it must publish state (pending count, status) that Compose observes; the actual synthesis is performed asynchronously by the engine, so nothing blocks the main thread. The history writer never runs on this scope (§10).

## 7. Audio focus and call handling (Decision 5)

**Focus** is owned by `speech/AudioPolicy`, invoked by the actor: request `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` immediately before the first utterance of a drain cycle (queue empty → non-empty); abandon when the queue becomes empty. Other apps are lowered, not stopped. API guard: `AudioFocusRequest` is API 26+; on API 24/25 the deprecated three-arg `requestAudioFocus(streamUsage, durationHint)` path is used under `Build.VERSION.SDK_INT >= 26` guard — the guard is mechanical and listed here so it is not forgotten (minSdk-24 rule). A denied focus request never mutes a payment: the announcement proceeds regardless (spec scenario).

**In-call detection: `AudioManager.mode` is the sole signal** — `MODE_IN_CALL` or `MODE_IN_COMMUNICATION` (spec's own detection definition). Rejected alternatives: `TelephonyManager` callbacks (`PhoneStateListener` is deprecated; `TelephonyCallback` needs API 31+ and a second mechanism for 24–30; two mechanisms for one check is not worth it), and phone-state broadcasts (unreliable across OEMs). `AudioManager.mode` requires no permission, no guard, and also covers VoIP calls (dialers and WhatsApp-type calls set communication mode), which matters for this user population. Documented limitation: a hypothetical call style that sets neither mode would not defer — accepted, and capped by the fact that deferral is a courtesy while correctness of speech is the product.

**Deferral mechanics: a gate, not a second queue.** The actor re-checks the mode before *starting* each item; while a call is active it parks (does not dequeue). Arrivals during the call keep enqueuing behind parked items in the same FIFO channel. When the call ends, the parked head is checked again on each actor wakeup. Because there is only one queue and deferral never removes anything, "deferred items keep order and are not reordered relative to post-call arrivals" holds structurally — there is no separate deferred list that could reorder. The parked actor wakes to re-check the mode on a 2-second ticker; this avoids depending on any call-end callback.

## 8. Listener service lifecycle (Decision 6)

`NotisNotificationListenerService` is thin by construction: `onListenerConnected` updates a process-wide connection holder; `onNotificationPosted` hands the `StatusBarNotification` to `listener/PaymentPipeline` inside a per-notification try/catch (one bad notification must never kill the service); `onListenerDisconnected` (API 31+ only — guarded) downgrades the connection state.

- **Reaching announcer and repository**: both are singletons owned by `ChihuanApplication`; the service resolves them via `context.applicationContext as ChihuanApplication`. No DI, no static state, no binding.
- **Process restart**: when the process dies, the system rebinds the listener (notification access still granted) on the next relevant notification; `Application.onCreate` rebuilds every singleton; the Room database opens lazily on first use; the connection holder starts disconnected until `onListenerConnected` fires again.
- **Rebind**: on rebind, the pipeline resumes identically; nothing persistent needs refreshing because armed flags and settings are read fresh per notification (§9, §11) — a rebind never changes behavior.
- **Deduplication key** (deliberately in-memory, per the spec's own assumption): `packageName | notificationId | tag-or-empty | SHA-256(extracted canonical text)`. Same identity+text → suppressed (no utterance, no row). Same id, different text → new notification, announced. Retention: entries kept ≥ 10 minutes (evicted lazily by insertion timestamp on each insert) with a hard cap of 512 entries evicting oldest-first as a memory bound; the cap exceeds the 10-minute minimum in any realistic payment cadence.
- **Accepted consequence, stated plainly**: the identity set is not persisted. A notification re-posted from a previous process lifetime can be announced twice. The spec declares this an accepted limitation, not a requirement; the alternative (persisted dedupe) was rejected in §16. Raw notification text never reaches any storage; the dedupe set lives only in memory, so the "raw text never persisted" requirement is structurally satisfied.
- **Connection state exposure**: the holder publishes `Connected | Disconnected | AccessMissing`, derived as: access not granted (live check, §9) → `AccessMissing`; access granted + holder not connected → `Disconnected`; `onListenerConnected` → `Connected`. The panel re-derives it on every foreground return (never cached at first launch), which also covers revocation-while-running without a restart.

## 9. Permission state machine (Decision 7)

Three measured states plus one guidance-only step. All measured states are re-read from the system on every foreground return and on process start — never from a first-launch cache.

| State | Detection (API floor) | Panel/onboarding shows |
| --- | --- | --- |
| Notification access — **Denied** | App absent from enabled notification listeners (`NotificationManagerCompat.getEnabledListenerPackages`, all APIs) and the persisted `was_granted` boolean is false | "Payments cannot be heard until access is granted" + action opening the system notification-access settings screen |
| Notification access — **Granted** | App present in enabled listeners | Healthy state; rows read as "reading" when armed |
| Notification access — **Revoked** | Live check says not granted **and** persisted `was_granted` boolean is true | Same blocking copy as Denied; armed flags untouched; state changes require no restart |
| Battery optimization — NotExempt / **Exempt** | `PowerManager.isIgnoringBatteryOptimizations(packageName)` (API 23+, no guard needed at minSdk 24) | Step with current state + action opening `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (list screen; the app never requests the special permission, per spec) |
| Restricted settings (Android 13+) — **unverified by design** | **Not detectable — no public API exists for this state.** Shown only when `SDK_INT >= 33` | Static guidance with the exact navigation path (App info → overflow → `Allow restricted settings`), explicitly labeled as unverified guidance, never as a measured Granted/Denied; never blocks the access step |

The persisted `was_granted` boolean is the *only* onboarding fact stored (written once, the first time Granted is observed; never cleared on revoke — that is what distinguishes Revoked from Denied). It lives in the settings SharedPreferences file (§11) and is configuration, not payment data.

Derived panel surfaces: listener connection (`Connected/Disconnected/AccessMissing`, §8) and TTS status (§15) are displayed alongside the three setup states; armed-but-unreadable is always stated plainly ("armed, but no payment can be heard until access is granted"), and an off source reads as off, not as an error (per spec copy rules).

Onboarding is a separate screen reachable from the panel, leaveable at any time; first launch lands on the panel, which offers the onboarding entry. The three steps show complete / incomplete / unverified using the copy that matches their detection ability.

## 10. Room design (Decision 8)

Database `AppDatabase` (v1, one entity, `version = 1`, schema exported to `app/schemas` via the KSP arg; no migrations needed at v1 and `fallbackToDestructiveMigration` is **not** used — a future migration is authored properly, not destructively).

Entity `PaymentEntity`, table `payments`:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `Long`, `@PrimaryKey(autoGenerate = true)` | tie-break for equal timestamps (most recently inserted first) |
| `timestampMs` | `Long` | epoch millis, captured at the moment the utterance was requested (see sequencing below) |
| `sourceId` | `String` | registry id, carried through from the pipeline, never re-derived |
| `amountCents` | `Long?` | nullable; absent for fallback rows; integer céntimos, never floating point |
| `payerName` | `String?` | as extracted, only when the parser reported one |
| `securityCode` | `String?` | three digits **as text** so `045` keeps its leading zero; only when reported |
| `isFallback` | `Boolean` | false for parsed announcements, true for fallback alerts |

DAO (`PaymentDao`), exactly what the panel needs:

- `observeDay(startMs: Long, endMsExclusive: Long): Flow<List<PaymentEntity>>` — `WHERE timestampMs >= :startMs AND timestampMs < :endMsExclusive ORDER BY timestampMs DESC, id DESC`.
- `observeDayTotal(startMs, endMsExclusive): Flow<Long>` — `SELECT COALESCE(SUM(amountCents), 0) FROM payments WHERE timestampMs >= :startMs AND timestampMs < :endMsExclusive AND isFallback = 0 AND amountCents IS NOT NULL` (integer céntimos sum; fallback rows and amountless rows excluded by construction).
- `suspend fun insert(entity: PaymentEntity)` — suspend (room-ktx), runs on Room's IO executor.

**Timezone rule for "today"**: the local calendar day in the device's current default time zone, computed at query time (not snapshotted). `data/history/TodayWindow` converts "now" → `[startOfDayMs, endOfDayMs)` using `java.util.Calendar` + `TimeZone.getDefault()` (chosen over `java.time` to avoid core-library desugaring at minSdk 24 — ADR-7) and is pure-JVM testable by injecting current millis and a fixed zone, including a midnight-crossing case.

**Insert sequencing — the load-bearing decision.** The write is initiated by `HistoryWriter` when the queue engine hands an item to the TTS engine (the "utterance requested" moment), *after* the `speak()` call returns — never at notification arrival, never before speech. Therefore:

- a threshold-suppressed or disable-suppressed payment never enqueues, so it never gets a row (spec-compliant);
- an item cleared by a mid-flight disable is spoken 0 times and its row is written 0 times — the "no utterance → no record" rule stays exact even in the race;
- `speak()` is on the speech scope; the write is launched on `historyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` — **a separate coroutine scope from the speech path, explicitly**. `HistoryWriter.insert` wraps the DAO call in try/catch and publishes `HistoryStatus` (Healthy / WriteFailed) on a StateFlow; a failure can never throw into the pipeline, the queue, or the service (spec's containment requirement), and the panel shows the non-blocking "history could not be saved" notice while announcement state stays healthy. Repeated failures are contained per event; the next event attempts a fresh write (Room reopens lazily), no retry queue of pending writes exists in v1.

Room + KSP is the only build addition this implies (§3).

## 11. Settings storage (Decision 9)

One SharedPreferences file, `notisyape_settings` (MODE_PRIVATE), framework framework only (DataStore rejected — §16). Keys, types, defaults:

| Key | Type | Default |
| --- | --- | --- |
| `announcements_enabled` | Boolean | `true` |
| `speech_rate` | Float | `1.0` |
| `say_amount` / `say_name` / `say_code` | Boolean ×3 | `true` |
| `minimum_amount_cents` | Long | `0` |
| `source_armed_<sourceId>` (e.g. `source_armed_yape`) | Boolean, one key per registry entry | `true` (both sources armed on fresh install) |
| `selected_voice` | String or absent | absent = automatic Spanish selection |
| `notification_access_was_granted` | Boolean | `false` (the single onboarding fact, §9) |

Semantics:

- **Reads are fault-tolerant by construction**: a missing or unreadable value falls back to the documented default (typed getters with defaults; a `ClassCastException` on a mistyped key is caught and treated as missing).
- **Writes are validated** by `SettingsPolicy` (pure, JVM-tested): rate must be within `0.5..2.0` inclusive (out-of-range write is rejected, stored value unchanged — the spec's example `3.0` is impossible to store); content toggles enforce "at least one group stays on" (turning off the last enabled group leaves it on); minimum amount is a non-negative céntimos value.
- **Selected voice validation on load and per utterance**: when the announcer initializes and before each utterance, the stored voice id is checked against `VoiceCatalog` (the engine's live reported voices). If it is gone: fall back to the automatic selection order, speak normally, **clear the stale key**, and publish the visible notice with the exact spec copy `La voz seleccionada ya no está disponible. Se usará la voz predeterminada.` — never silence, never crash.
- **Live application**: the announcer reads a `SettingsSnapshot` when it dequeues each item (composition time), so rate, content toggles, enable flag, and minimum amount always take effect on the next announcement without restart or rebind — there is no settings cache at app start anywhere in the speech path. (Snapshot reads are cheap in-memory SharedPreferences reads.)
- Settings storage contains configuration only; payment-derived values are never written to it (the `was_granted` boolean and armed flags are configuration, not records).

## 12. UI state model (Decision 10)

`ui/panel/PanelViewModel` is the single source of truth. It merges process-wide state into one immutable `PanelState` exposed as a `StateFlow` (created in the ViewModel's own `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` — no reliance on transitive `viewModelScope`), observed in Compose with `collectAsState()`. No navigation library: the panel and onboarding are two states of one Activity (`rememberSaveable` screen enum + `BackHandler`).

```
PanelState(
  sources: List<SourceRow>          // id, label, armed — rendered from SourceRegistry order
  access: AccessState               // Denied / Granted / Revoked
  connection: ConnectionState       // Connected / Disconnected / AccessMissing
  tts: TtsStatus                    // see §15
  battery: BatteryState             // NotExempt / Exempt
  voice: VoiceState                 // Automatic / Selected(id,label) / Unavailable, + picker list
  voiceFallbackNotice: Boolean      // drives the exact spec-copy notice
  pendingAnnouncements: Int         // from QueueOrderingEngine
  history: HistoryState             // rows (label, amount formatted, code, unreadable marker) + totalCents + empty flag
  historyWriteFailed: Boolean       // non-blocking notice
  settings: SettingsSnapshot        // drives toggles, rate slider, threshold, content groups
)
```

- **Composition of the state**: sources/armed come from `SettingsStore` (re-read on refresh); history rows and total come from the Room `Flow`s collected into the panel state (so the panel updates live while open); access/battery/connection/TTS are read at refresh time and on foreground return (`ON_RESUME` hook calls `PanelViewModel.refresh()` via a `LifecycleEventObserver` — satisfying every "re-read on return to foreground" requirement in one mechanism); voice state comes from `VoiceCatalog` live at picker-open time and at refresh.
- **Writes**: every control writes through `SettingsStore` (validated by `SettingsPolicy`) and then triggers a refresh — the ViewModel never holds a divergent copy, which is what makes "toggle and stored state never disagree" structural. `Probar voz` calls the announcer's sample path (same composer, ignores enable/threshold per spec, applies rate and content selection, enqueues rather than interrupts).
- The panel is the entry screen; it hosts the four sections (sources, voice, history, settings) and surfaces every blocking state (§9). MainActivity keeps `ChihuanTheme` and `setContent`; the template greeting is deleted.

## 13. Fixture capture / calibration mode (Decision 11)

This is the mechanism the whole change's correctness hangs on, so it is designed as a first-class seam, not a debugging afterthought.

**The seam**: a `NotificationCaptureSink` interface (one method: `capture(packageName, title, bigText, textLines, text, receivedAtMillis)`). Two implementations:

- `DebugCaptureSink` — wired in debug builds only: emits a single `Log.d` line under tag `ChihuanCapture`, a compact pipe-delimited record of the raw fields. **Logcat only.** The spec forbids writing raw notification text to SharedPreferences, internal storage, or the history database — logcat is none of these three, is ephemeral, and requires no file, no DB, no export surface, keeping the release behavior and the storage-inspection scenarios clean.
- `NoCaptureSink` — wired in release builds: no-op. The sink is injected into the pipeline; release code paths contain no capture call of any kind, so nothing about capture is reachable from a release install.

**Developer workflow (v1, single developer, sideloaded APK)**: install the debug build on the test phone → trigger real Yape payments (and Interbank/Plin payments for calibration) → `adb logcat -s ChihuanCapture` → copy each record into a fixture file. Capture is by-package-agnostic on purpose: it records *every* notification's raw fields in debug, so the exact Yape/Interbank shape — including which extras carry the readable sentence — is observed rather than assumed. This directly retires the P0 risks in the proposal.

**Fixture layout**: `app/src/test/resources/fixtures/yape/NNN-<slug>.txt` and `.../fixtures/interbank/...`. Each file: first line `#TITLE: <title>`, then the extracted body exactly as captured (big-text or text-lines cases preserved with their newlines). Parser tests load them via `getResourceAsStream` — pure JVM, no Android. Yape fixtures are only ever written from captured notifications (never invented); the test class holds an explicit list of fixture names (plain JUnit4, no new test dependency).

**Placeholder discipline**: Interbank fixtures not captured from reality are named `placeholder-*.txt` and their file header carries `#PLACEHOLDER: not captured wording`. Placeholders never count toward the 5–10 captured fixtures calibration requires; the calibration gate (package constant + pattern set pinned together) is enforced socially — the registry marker comment plus the proposal Log — not by runtime state, because a runtime "calibrated" flag would be invisible to the review that matters.

**What capture must never do**: run in release, write to storage, alter processing, or give the debug build different parsing behavior — the sink sits strictly beside the pipeline, not inside it.

## 14. Testing strategy (Decision 12)

**Pure JVM under `:app:testDebugUnitTest`** (the strict-TDD gate; all of these live in `core` or in pure helpers of `data`/`speech`):

| Test class | Covers |
| --- | --- |
| `NumberToWordsTest` | every normative scenario from `voice-announcement` (650, 100, 5100, 2100, 10000, 10100, 100000, 10050, 50, 5, 1, 101) as table-driven cases |
| `AnnouncementComposerTest` | order and content rules, missing name/code, hidden amount, code digits (`123`→`uno dos tres`, `045`→`cero cuatro cinco`), the no-digit-character invariant over any composed utterance incl. the fallback phrase |
| `AmountParserTest` | currency variants (`S/ 50`, `S/ 50.00`, `S/50`, `S/ 6,50`), the four separator rules, bare-number rejection, zero-amount → Unparsed, first-amount-wins |
| `NameParserTest` / `CodeParserTest` | verb tolerance (case/accent), name bounds and bank-label rejection, labeled-code precedence, time-not-a-code, verbatim `045` |
| `YapeFixtureTest` | all captured Yape fixtures parse end-to-end (drives Slice 1's TDD) |
| `InterbankPlaceholderFixtureTest` | placeholder fixtures satisfy the shared contract; unrecognized wording → Unparsed |
| `NotificationTextExtractorTest` | precedence (bigText replaces text; text lines join; null-safe empty) |
| `PackageFilterTest` / `DuplicateSuppressorTest` | exact-match acceptance incl. the `.debug` look-alike rejection; off-source silence; re-post suppression; same-id-new-text announced; retention eviction |
| `QueueOrderingEngineTest` | FIFO under enqueue-while-speaking, twelve-item drain, exactly-once ids, disable-clear, pending count |
| `VoiceSelectionTest` | automatic order `es-PE` → other `es-*` → engine default, from plain voice descriptors |
| `SettingsPolicyTest` | rate bounds, last-content-group rule, threshold comparison (equal announces) |
| `TodayWindowTest` | day boundaries incl. midnight crossing, fixed zones |

**Instrumented (`androidTest`, minimal — the existing test artifact set only)**: one Room smoke test (insert → observeDay/total round-trip with the real generated DAO), one Compose smoke test (panel launches and shows the two source rows — uses the already-present `ui-test-junit4`).

**Explicitly not automated, with reasons**: real TTS engine behavior (init, voice enumeration, missing-data, engine drift) is hardware/engine-dependent — covered by a documented manual device checklist; audio focus interplay with real music apps and call deferral against real calls — same checklist; OEM service-kill survival — manual, per-device; DAO ordering nuances beyond the smoke test — manual. Robolectric, MockK, Turbine, and `kotlinx-coroutines-test` are not added (proposal freeze); the pure-engine extraction (§6) is what removes the need for coroutines-test on the queue.

## 15. Error handling table

| # | Failure mode | How detected | What the user sees | What the app does |
| --- | --- | --- | --- | --- |
| 1 | TTS engine missing / init fails | `TextToSpeech` init callback not OK, or no engine found | TTS status `EngineUnavailable` with guidance to install a TTS engine | Queue holds nothing; items arriving while unusable are dropped **without rows** (no utterance → no record); no crash; Probar voz reports the state instead of speaking |
| 2 | Spanish language data missing (`LANG_MISSING_DATA`) | Language/voice availability check at init and before each utterance | TTS status `VoiceDataMissing` + guidance action opening `ACTION_INSTALL_TTS_DATA` | No crash; utterance attempted with engine default while the unhealthy state stays visible (spec allows only-with-report; speech > silence for this user) |
| 3 | No Spanish voice at all on the device | `VoiceCatalog` empty for `es` | Voice picker shows unavailable state with guidance, no invented entries | Announcements attempted with engine default, status reported; selection remains automatic |
| 4 | Selected voice disappeared (engine update / data removal) | Per-utterance validation against live `VoiceCatalog` | Non-blocking notice, exact copy `La voz seleccionada ya no está disponible. Se usará la voz predeterminada.` | Automatic Spanish fallback for this utterance; stale `selected_voice` key cleared; picker shows automatic; queue unaffected |
| 5 | Room write failure (full disk, corrupt DB, missing file, DAO exception) | try/catch around every write; `HistoryStatus.WriteFailed` | Non-blocking "history could not be saved" notice; announcement state stays healthy | Speech unaffected (write is post-speak, separate scope, never throws upward); next event retries a fresh write; no pending-write queue |
| 6 | Database fails at open/first query | Same containment around the lazy open and the DAO `Flow` collection | History section shows the failure notice and an explicit empty state | Panel functional; speech functional; no crash |
| 7 | Listener service killed by OEM | Foreground return re-derivation: access granted + not connected → `Disconnected`; battery state shown | Honest state display; battery-exemption guidance (onboarding step) | No self-revival loops in v1; system rebinds on next notifications; mitigation is the exemption flow |
| 8 | Notification access revoked mid-session | System unbinds the listener; `onListenerDisconnected` (API 31+, guarded) + foreground re-check | Access state `Revoked`, "payments cannot be heard" copy, grant action | App stops receiving (system behavior); armed flags and settings untouched; re-grant restores without restart |
| 9 | Synthesis error mid-queue | `UtteranceProgressListener.onError` for the item's id | TTS status unhealthy, visible | Item marked failed (no retry → exactly-once preserved), queue advances to the next item, focus held until queue empties |
| 10 | Audio focus denied | Focus request result | Nothing (deliberate) | Announcement still spoken (spec: payment confirmation outranks audio politeness) |
| 11 | Notification with no text extras | Extractor returns empty body without throwing | Fallback phrase (if the source is armed) | Parser classifies Unparsed → fallback alert + fallback history row |
| 12 | Process death with queued items | — (accepted) | Nothing (items were not yet spoken) | Queued items lost; no rows for unspoken items; dedupe set empty → a re-post may re-announce (accepted, §8) |
| 13 | Amount outside parser domain (>9 integer digits) | Parser digit cap | Fallback phrase | Unparsed, never a crash or invented amount |
| 14 | Unreadable/mistyped SharedPreferences value | Typed getters with defaults + catch | Defaults shown in the panel | Documented defaults applied (never crash, never silently disable a source) |

## 16. Risks and tradeoffs (with rejected alternatives)

1. **Room vs no persistence** — the spec mandates Room for history; the honest tradeoff is build complexity (KSP plugin, codegen time) and a new failure surface on the payment path. Rejected alternative: a plain append-only JSON file (no new dependency, trivially durable) — rejected because today's-list/total queries and the deterministic ordering rule would be hand-rolled and the review budget for that hand-rolling is worse than one dependency; also Room's schema gives the future a migration path. The introduced I/O risk is neutralized structurally (§10), not by trusting Room.
2. **In-memory dedupe vs persisted** — spec mandates in-memory. Accepted consequence: a re-posted notification from a previous process lifetime can be announced twice (rare: requires process death + system re-post). Rejected persisted dedupe (a second Room table + a synchronous-looking check on the announcement path): the write-before-speech ordering that the specs forbid would become tempting, and a dedupe miss is benign (one extra utterance) while a dedupe table adds a storage dependency to the speech path.
3. **SharedPreferences vs DataStore** — spec mandates SharedPreferences. Rejected DataStore: a new dependency the proposal explicitly forbids, coroutines-first machinery for eight configuration keys, and a second storage format to reason about. SharedPreferences' synchronous reads are a feature here: the per-utterance snapshot read (§11) must never itself be async.
4. **Application-scoped announcer vs service-scoped** — rejected service-scoped (a TTS instance inside the listener service): the panel's `Probar voz` would need to bind to the listener service or run a second TTS instance (violating the single-queue spec), and service lifetime is governed by OEM kills rather than by the user's expectations of the app. Application scope gives exactly one TTS instance shared by listener and UI; its cost (no work-boundary control, announcer outlives usefulness) is irrelevant at this scale.
5. **`AudioManager.mode` vs `TelephonyManager` signals** — see §7; rejected TelephonyManager (deprecated listener pre-31, API-31 callback post-31, two mechanisms, marginal gain).
6. **Room Gradle plugin vs KSP argument for schema export** — rejected the extra `androidx.room` plugin: one KSP arg line achieves the same with one less plugin on an AGP 9 upgrade path.
7. **`java.util.Calendar` vs `java.time` + desugaring** — rejected desugaring: a build-flag dependency for one day-boundary computation at minSdk 24; Calendar with an injected clock is equally testable.
8. **Coroutines obtained transitively** — the speech actor and history scope use `kotlinx-coroutines` that arrives via the present lifecycle/core artifacts; the proposal's "no dependency beyond Room" is honored literally, but a future lifecycle bump dropping coroutines would break compilation (loudly, not silently — listed as a known fragility, not a hidden one).

## 17. Slice mapping (apply-phase boundaries)

The proposal's 6-slice forecast maps onto concrete files; each slice is independently green (`testDebugUnitTest` gate):

1. **Slice 1 — Parsing core (pure)**: `core/model/Models.kt`; `core/parser/TextNormalizer.kt`, `AmountParser.kt`, `NameParser.kt`, `CodeParser.kt`, `PatternSet.kt`, `YapePatternSet.kt`, `PaymentParser.kt`; `core/amount/NumberToWords.kt`; `core/text/NotificationTextExtractor.kt`; `core/registry/SourceRegistry.kt`; tests `NumberToWordsTest`, `AmountParserTest`, `NameParserTest`, `CodeParserTest`, `YapeFixtureTest`, `NotificationTextExtractorTest`, `SourceRegistryTest`; fixtures `app/src/test/resources/fixtures/yape/*` (captured). ~750 lines.
2. **Slice 2 — Composition + second source**: `core/announce/AnnouncementComposer.kt`; `core/parser/InterbankPatternSet.kt`; fixtures `fixtures/interbank/placeholder-*`; tests `AnnouncementComposerTest`, `InterbankPlaceholderFixtureTest`. ~390 lines.
3. **Slice 3 — Speech layer + listener**: `ChihuanApplication.kt` (manual wiring); `core/queue/QueueOrderingEngine.kt` + `QueueOrderingEngineTest`; `core/dedup/DuplicateSuppressor.kt` + test; `core/filter/PackageFilter.kt` + test; `speech/SpeechAnnouncer.kt`, `speech/AudioPolicy.kt`, `speech/VoiceCatalog.kt`, `core/speech/VoiceSelection.kt` + `VoiceSelectionTest`; `listener/NotisNotificationListenerService.kt`, `listener/NotificationReader.kt`, `listener/PaymentPipeline.kt`; `DebugCaptureSink`/`NoCaptureSink`; manifest change. ~340 lines.
4. **Slice 4 — History persistence**: build additions (§3); `data/history/PaymentEntity.kt`, `PaymentDao.kt`, `AppDatabase.kt`, `HistoryWriter.kt`, `TodayWindow.kt` + `TodayWindowTest`; Room instrumented smoke test. ~230 lines.
5. **Slice 5 — Settings + voice picker**: `data/settings/SettingsStore.kt`, `SettingsPolicy.kt` + `SettingsPolicyTest`; `ui/voice/VoicePicker.kt`, `ui/settings/SettingsSection.kt`; strings. ~350 lines.
6. **Slice 6 — Control panel**: `MainActivity.kt` (template greeting removed); `ui/panel/PanelScreen.kt`, `PanelViewModel.kt`, `PanelState.kt`; `ui/history/HistorySection.kt` + céntimos formatter; `permission/PermissionStateSource.kt`; `ui/onboarding/OnboardingScreen.kt`; Compose smoke test. ~570 lines.

## 18. ADRs

- **ADR-1 — Single module, package-enforced layering.** No Gradle multi-module split for v1. Tradeoff: the pure-Kotlin rule is convention + review, not a compiler guarantee; accepted because one module keeps the review budget and apply-phase simple, and `core` is mechanically checkable (zero `android.*` imports, enforced in tests by simply existing on the JVM).
- **ADR-2 — Actor with a pure ordering engine.** The speech queue is a single-consumer coroutine actor over an unbounded channel; all ordering decisions live in a synchronous pure `QueueOrderingEngine`. Rationale: exactly-once + FIFO + observable pending count without locks, and JVM-testable ordering without `kotlinx-coroutines-test`. Alternative rejected: `ArrayDeque` + `synchronized` around direct `speak()` calls — works, but ties ordering correctness to callback timing and is harder to test deterministically.
- **ADR-3 — `AudioManager.mode` as the sole in-call signal.** Rationale and rejected alternatives in §7. Limitation accepted: only call styles that set in-call/in-communication mode are deferred.
- **ADR-4 — Application-scoped single announcer.** Rationale in §16.4.
- **ADR-5 — SharedPreferences with a `was_granted` boolean as the only persisted onboarding fact.** It is what makes Revoked distinguishable from Denied per spec; everything else is read live from the system.
- **ADR-6 — Room 2.8.5 + KSP 2.2.10-2.0.2, schema via KSP arg, no destructive migration.** Verified against minSdk 24 (Room 2.8 floor = API 23), Kotlin 2.0 requirement, AGP 9.3.3. Fallback documented (§3).
- **ADR-7 — Capture via logcat sink, release no-op.** Keeps raw notification text out of all persistent storage while making real-fixture capture one `adb logcat` away; the spec's storage rules are satisfied structurally, not by discipline.
- **ADR-8 — Two-outcome parser inside a three-outcome pipeline.** The parser contract stays exactly `Parsed | Unparsed` (spec-fixed); `Ignored` is the filter layer's decision, so the parser never needs to know about packages or armed flags.
- **ADR-9 — Deferral as a gate on one queue, not a deferred list.** Structural guarantee that deferred and post-call announcements cannot reorder (§7).

## 19. Facts to verify at apply time (not design blockers)

These are calibration and platform facts the apply phase must observe on a real device, using §13's capture path — none of them re-open product decisions:

- The real Yape notification: package confirmation, which extras carry the readable sentence, where the code sits, wording variants.
- The real Interbank package identifier and Plin wording (5–10 captured fixtures before the source is treated as calibrated).
- Per-device TTS engine voice inventory and rate semantics (manual checklist).
- Audio routing/volume behavior over Bluetooth headsets (platform-dependent; manual checklist).
