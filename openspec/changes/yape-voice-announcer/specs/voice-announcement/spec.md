# Voice Announcement Specification

## ADDED Requirements

### Requirement: Amount spoken as Spanish words, never as digits

The system MUST render every monetary amount as Spanish words before handing text to `TextToSpeech`. The utterance MUST NOT contain digit characters, the word `punto`, or the word `coma`. `S/ 6.50` MUST be spoken as `seis soles con cincuenta`, never `seis punto cincuenta` and never `seis coma cincuenta`.

The rendering rules are normative and unit-testable:

- The integer part is rendered as a Spanish cardinal, except that `1` before `sol`/`soles` or `céntimo`/`céntimos` MUST be `un` (never `uno`).
- `21` MUST be `veintiún` and, for values ending in 1 above 30, the form MUST be `<tens> y un` (`31` → `treinta y un`, `51` → `cincuenta y un`).
- `100` MUST be `cien`, never `ciento`; `101`–`199` MUST use `ciento` (`101` → `ciento un`).
- `1000` MUST be `mil`, never `un mil`; `21000` MUST be `veintiún mil`.
- Accents MUST be preserved (`dieciséis`, `veintidós`, `céntimos`).
- The noun is `sol` when the amount is exactly one sol and `soles` otherwise.

#### Scenario: Six soles and fifty cents

- **GIVEN** an amount of 650 céntimos
- **WHEN** the amount is rendered as words
- **THEN** the result is exactly `seis soles con cincuenta`

#### Scenario: Exactly one sol uses the singular

- **GIVEN** an amount of 100 céntimos
- **WHEN** the amount is rendered as words
- **THEN** the result is exactly `un sol` with no `con` clause and no `céntimos` clause

#### Scenario: Fifty-one soles uses apocope

- **GIVEN** an amount of 5100 céntimos
- **WHEN** the amount is rendered as words
- **THEN** the result is exactly `cincuenta y un soles`

#### Scenario: Twenty-one soles uses veintiún

- **GIVEN** an amount of 2100 céntimos
- **WHEN** the amount is rendered as words
- **THEN** the result is exactly `veintiún soles`

#### Scenario: One hundred soles uses cien

- **GIVEN** an amount of 10000 céntimos
- **WHEN** the amount is rendered as words
- **THEN** the result is exactly `cien soles`

#### Scenario: One hundred and one soles uses ciento

- **GIVEN** an amount of 10100 céntimos
- **WHEN** the amount is rendered as words
- **THEN** the result is exactly `ciento un soles`

#### Scenario: One thousand soles omits the article

- **GIVEN** an amount of 100000 céntimos
- **WHEN** the amount is rendered as words
- **THEN** the result is exactly `mil soles`

#### Scenario: One hundred soles and fifty cents

- **GIVEN** an amount of 10050 céntimos
- **WHEN** the amount is rendered as words
- **THEN** the result is exactly `cien soles con cincuenta`

### Requirement: Céntimos clause and the céntimos-only case

When the cent part is greater than zero, the rendering MUST append `con <cents in words>` after the sol clause. The cents are always rendered as a cardinal number, never digit by digit: 50 céntimos MUST be `cincuenta`, not `cinco cero`.

When the integer part is zero, the rendering MUST contain no `sol`/`soles` at all and MUST be `<cents in words> céntimos`. One cent MUST be `un céntimo` (singular).

When the cent part is zero, no `con` clause and no `céntimos` word may appear.

#### Scenario: Céntimos only

- **GIVEN** an amount of 50 céntimos
- **WHEN** the amount is rendered as words
- **THEN** the result is exactly `cincuenta céntimos`

#### Scenario: Five céntimos

- **GIVEN** an amount of 5 céntimos
- **WHEN** the amount is rendered as words
- **THEN** the result is exactly `cinco céntimos`

#### Scenario: One céntimo is singular

- **GIVEN** an amount of 1 céntimo
- **WHEN** the amount is rendered as words
- **THEN** the result is exactly `un céntimo`

#### Scenario: One sol and one céntimo

- **GIVEN** an amount of 101 céntimos
- **WHEN** the amount is rendered as words
- **THEN** the result is exactly `un sol con un céntimo`

### Requirement: Announcement composition order and content

The spoken announcement SHALL be built by the app from the extracted fields using its own phrase templates. The field order MUST be payer name first, then amount, then security code, matching the natural Peruvian phrasing `Juan Pérez te yapeó seis soles con cincuenta`.

- The sentence MUST open with the payer name followed by the fixed verb `te yapeó` when a name is present.
- When the name is absent, the sentence MUST open with the fixed verb `Te yapearon` and continue with the amount, so the payer role is never implied to be the device owner.
- When the amount is present it MUST follow the verb directly, with no joining word.
- When the amount is hidden by settings, the verb stands alone as its own sentence.
- A present code MUST be appended as a separate sentence `. Código: <digits spoken individually>`.
- The utterance MUST end with a period.
- The first character of the utterance MUST be uppercase; proper nouns keep the casing they had in the notification.

The security code MUST be spoken digit by digit, in order, separated by spaces: `123` → `uno dos tres`, `045` → `cero cuatro cinco`.

#### Scenario: Amount, name, and code

- **GIVEN** amount 650 céntimos, name `Juan Pérez`, code `123`
- **WHEN** the announcement is composed
- **THEN** the utterance is exactly `Juan Pérez te yapeó seis soles con cincuenta. Código: uno dos tres.`

#### Scenario: Missing name is omitted without failing

- **GIVEN** amount 5000 céntimos, no name, code `123`
- **WHEN** the announcement is composed
- **THEN** the utterance is exactly `Te yapearon cincuenta soles. Código: uno dos tres.`

#### Scenario: Missing code is omitted without failing

- **GIVEN** amount 650 céntimos, name `Juan Pérez`, no code
- **WHEN** the announcement is composed
- **THEN** the utterance is exactly `Juan Pérez te yapeó seis soles con cincuenta.`

#### Scenario: Amount hidden but name and code present

- **GIVEN** amount 650 céntimos, name `Juan Pérez`, code `123`, and the amount disabled by settings
- **WHEN** the announcement is composed
- **THEN** the utterance is exactly `Juan Pérez te yapeó. Código: uno dos tres.`

#### Scenario: Leading zero in the code is spoken

- **GIVEN** amount 5000 céntimos, name `Rosa Quispe`, code `045`
- **WHEN** the announcement is composed
- **THEN** the utterance is exactly `Rosa Quispe te yapeó cincuenta soles. Código: cero cuatro cinco.`

#### Scenario: No digit character reaches the speech engine

- **GIVEN** any composed announcement, including the fallback alert
- **WHEN** the text is inspected before being passed to `TextToSpeech`
- **THEN** it contains no character in `0-9`

### Requirement: Distinct audible fallback for unparsed notifications

When the parser reports an unparsed outcome, the system MUST speak exactly the fallback phrase:

`Llegó un pago, pero no pude leerlo.`

The fallback phrase MUST NOT be composed from fields and MUST NOT be prefixed or suffixed with amount, name, or code text. It MUST be spoken as its own queue item.

#### Scenario: Unparsed notification produces the fallback phrase

- **GIVEN** a Yape notification whose text cannot be parsed
- **WHEN** the announcer handles it
- **THEN** the spoken utterance is exactly `Llegó un pago, pero no pude leerlo.`

#### Scenario: A broken parser never produces silence

- **GIVEN** a Yape notification with text `Tienes un nuevo movimiento en tu cuenta`
- **WHEN** the notification is processed end to end
- **THEN** an audible fallback alert is requested and no announcement request is dropped silently

### Requirement: Sequential announcement queue

The system SHALL announce payments sequentially through a single `TextToSpeech` instance, in arrival order.

- If speech is already playing, a new announcement MUST be enqueued, not spoken over the current one, and MUST NOT interrupt it.
- The next item MUST start only after the current item's completion or error callback.
- Every enqueued item MUST be announced in full: no truncation, no dropping, no skipping, and no merging of two payments into one utterance.
- A long queue MUST be announced in full, in order, with no cap on pending items. The number of pending announcements MUST be observable for display in the control panel.
- Each accepted announcement request MUST be spoken exactly once; internal retries MUST NOT cause a duplicate utterance.

#### Scenario: Speech already playing

- **GIVEN** the announcer is speaking `Juan Pérez te yapeó seis soles con cincuenta.`
- **WHEN** a second payment arrives
- **THEN** the second announcement is enqueued, the first is not interrupted, and the second starts after the first completes

#### Scenario: Three payments within five seconds

- **GIVEN** three Yape notifications arrive one to two seconds apart
- **WHEN** the queue drains
- **THEN** exactly three utterances are spoken in arrival order, none overlapping, each one complete

#### Scenario: Long queue is fully announced

- **GIVEN** twelve payments are queued while the announcer is speaking
- **WHEN** the queue drains
- **THEN** all twelve are announced in arrival order and none is dropped

#### Scenario: Engine error does not block the queue

- **GIVEN** `TextToSpeech` reports a synthesis error for the current item
- **WHEN** the error callback fires
- **THEN** the queue advances to the next item and the TTS status becomes unhealthy and visible in the control panel

### Requirement: Transient audio focus with ducking

Before starting the first utterance of a drain cycle, the announcer SHALL request transient audio focus that permits ducking. Other apps' playback MUST be lowered, not stopped. Focus MUST be abandoned when the queue becomes empty.

If the focus request is denied, the announcement MUST still be spoken, because a payment confirmation is more important than polite audio behavior.

#### Scenario: Music is ducked, not stopped

- **GIVEN** another app is playing music
- **WHEN** a payment announcement starts
- **THEN** transient ducking focus is requested, the music keeps playing at reduced volume, and focus is abandoned after the last queued announcement

#### Scenario: Denied focus does not mute the payment

- **GIVEN** the audio focus request is denied by another app
- **WHEN** the announcement starts
- **THEN** the announcement is still spoken

### Requirement: No announcement during a phone call

The system MUST NOT start speaking while a phone call is in progress, detected through the audio mode being in-call or in-communication.

A payment arriving during a call MUST be deferred, not discarded. Deferred announcements MUST be spoken in arrival order when the call ends. Deferral MUST NOT reorder them relative to announcements that arrive after the call ends.

#### Scenario: Payment during a call is deferred

- **GIVEN** a phone call is in progress
- **WHEN** a Yape payment arrives
- **THEN** no audio is produced during the call and the announcement is spoken after the call ends

#### Scenario: Several deferred payments keep order

- **GIVEN** a phone call is in progress and two payments arrive in sequence
- **WHEN** the call ends
- **THEN** both are announced in arrival order

### Requirement: Spanish voice selection with fallback and missing-data handling

The announcer SHALL use the platform `TextToSpeech` engine with a Spanish voice.

When the user has selected a voice from those installed on the device, that voice takes precedence;
the selection rules below apply in automatic mode, and again as the fallback target when a selected
voice is no longer available (see `announcement-settings`).

In automatic mode the selection order MUST be: `es-PE`, then any other Spanish locale variant
available on the device (`es-419`, `es-US`, `es-ES`, then any other `es-*`), then the engine
default.

If the Spanish voice data is missing, the system MUST surface an unhealthy TTS state with guidance to install voice data. It MUST NOT crash, and it MUST NOT produce an announcement in a non-Spanish voice without reporting that state.

#### Scenario: Peruvian Spanish is preferred

- **GIVEN** the engine reports both `es-PE` and `es-ES` voices available
- **WHEN** the voice is selected
- **THEN** `es-PE` is used

#### Scenario: Fallback to another Spanish variant

- **GIVEN** the engine reports no `es-PE` voice but reports `es-ES`
- **WHEN** the voice is selected
- **THEN** `es-ES` is used and the announcement is spoken in Spanish

#### Scenario: Missing voice data is reported, not crashed

- **GIVEN** the engine reports `LANG_MISSING_DATA` for Spanish
- **WHEN** the app initializes speech
- **THEN** the TTS status becomes unhealthy with user-visible guidance, and the app does not crash

### Requirement: Test button works without a payment or notification access

The control panel SHALL provide a `Probar voz` action that speaks a fixed sample announcement
immediately.

- It MUST work with no real payment, with notification access not granted, and with no listener connection.
- It MUST use the same composition and number-to-words pipeline as real announcements, so the sample demonstrates the configured content selection.
- It MUST ignore the enabled/disabled toggle and the minimum-amount threshold, because it is an explicit user test.
- It MUST apply the configured speech rate.
- If the queue is busy, the sample MUST be enqueued rather than interrupting the current announcement.
- It MUST NOT modify any setting.

#### Scenario: Sample announcement with default settings

- **GIVEN** notification access is not granted and no payment has ever arrived
- **WHEN** the user taps `Probar voz`
- **THEN** the utterance `Juan Pérez te yapeó seis soles con cincuenta. Código: uno dos tres.` is spoken

#### Scenario: Test works while announcements are disabled

- **GIVEN** announcements are disabled in settings
- **WHEN** the user taps `Probar voz`
- **THEN** the sample announcement is still spoken

#### Scenario: Test during playback does not interrupt

- **GIVEN** the announcer is speaking a payment
- **WHEN** the user taps `Probar voz`
- **THEN** the sample is queued after the current utterance and no announcement is cut off

### Requirement: Speech rate applies to subsequent utterances

The announcer SHALL apply the current speech-rate setting to every utterance, including the sample from `Probar voz`. A rate change MUST take effect on the next utterance without an app restart or engine re-initialization visible to the user.

#### Scenario: Rate change applies to the next announcement

- **GIVEN** the speech rate is `1.0`
- **WHEN** the user sets it to `1.5` and the next payment arrives
- **THEN** that announcement is spoken at `1.5` without restarting the app
