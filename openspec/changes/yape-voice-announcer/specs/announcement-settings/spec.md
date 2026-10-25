# Announcement Settings Specification

## ADDED Requirements

### Requirement: Settings are persisted locally with defined defaults

The app SHALL persist announcement settings in framework `SharedPreferences`. Room is used only for
payment history (see `payment-history`) and MUST NOT store settings. No DataStore and no additional
dependency may be introduced for settings.

The persisted settings are:

| Setting | Type | Default |
| --- | --- | --- |
| Announcements enabled | Boolean | `true` |
| Speech rate | Float, `0.5`–`2.0`, step `0.1` | `1.0` |
| Say amount | Boolean | `true` |
| Say payer name | Boolean | `true` |
| Say security code | Boolean | `true` |
| Minimum amount to announce | amount in céntimos | `0` (announce all) |
| Armed state per source | Boolean, one entry per registry source (declared in `source-management`) | `true` |
| Selected voice | engine voice identifier (text), or absent | absent, meaning automatic Spanish selection |

All values MUST survive process death and device reboot. An unreadable or missing value MUST fall
back to its default rather than crash.

*Assumption:* the defaults above are the v1 starting point; only the minimum amount default (`0`,
announce everything) is a deliberate product choice, because a silent default would hide payments.

#### Scenario: Defaults apply on a fresh install

- **GIVEN** no settings have ever been stored
- **WHEN** the settings screen is opened
- **THEN** announcements are enabled, speech rate is `1.0`, all three content groups are on, the minimum amount is `0`, both sources are armed, and the voice is in automatic mode

#### Scenario: Changed settings survive restart

- **GIVEN** the user set the speech rate to `1.4` and disabled the security code
- **WHEN** the app process is killed and started again
- **THEN** the speech rate is still `1.4` and the security code is still disabled

### Requirement: Disabling announcements silences the app completely

When announcements are disabled, the app MUST NOT speak anything: no payment announcement, no sample, and no fallback alert. User intent to silence the app overrides the fallback-alert guarantee.

Disabling MUST take effect immediately: the current utterance is stopped and pending queued announcements are cleared. Notification listening may continue internally, but no announcement request may be queued while disabled.

Re-enabling MUST apply to the next payment without restarting the app. Payments that arrived while disabled MUST NOT be announced on re-enable.

#### Scenario: Disable stops speech immediately

- **GIVEN** the announcer is speaking a payment
- **WHEN** the user disables announcements
- **THEN** speech stops within about one second and pending announcements are discarded

#### Scenario: Disabled app stays silent on a payment

- **GIVEN** announcements are disabled
- **WHEN** a Yape payment notification arrives
- **THEN** nothing is spoken

#### Scenario: Disabled app stays silent on an unparsed notification

- **GIVEN** announcements are disabled
- **WHEN** a Yape notification arrives whose text cannot be parsed
- **THEN** nothing is spoken, and the fallback alert is suppressed by the explicit disable

#### Scenario: Re-enabling does not replay old payments

- **GIVEN** three payments arrived while announcements were disabled
- **WHEN** the user enables announcements again
- **THEN** those three payments are not announced

### Requirement: Speech rate is bounded and applies to subsequent announcements

The app SHALL accept a speech rate between `0.5` and `2.0` inclusive. Values outside that range MUST be rejected and the stored value left unchanged.

A stored rate MUST be applied to every utterance created after the change, including the `Probar voz` sample, without requiring an app restart.

#### Scenario: Out-of-range rate is rejected

- **GIVEN** the speech rate is `1.0`
- **WHEN** a code path attempts to store `3.0`
- **THEN** the stored rate remains `1.0`

#### Scenario: Rate change applies without restart

- **GIVEN** the speech rate is `1.0`
- **WHEN** the user moves it to `0.8` and the next payment arrives
- **THEN** that announcement is spoken at `0.8` in the same app session

### Requirement: Content selection controls the spoken fields, never the amount format

The app SHALL allow independently enabling or disabling the amount, the payer name, and the security code.

- A disabled field MUST be omitted entirely from the utterance, including its joining words.
- The remaining fields MUST keep the fixed order payer name, then amount, then code.
- The amount, when spoken, MUST always be in Spanish words; disabling other fields MUST NOT change number formatting.
- The app MUST NOT allow all three content groups to be disabled. When the user turns off the last enabled group, that group stays enabled.
- Content selection MUST NOT alter the fallback alert phrase.

#### Scenario: Name disabled

- **GIVEN** amount 650 céntimos, name `Juan Pérez`, code `123`, and name disabled
- **WHEN** the announcement is composed
- **THEN** the utterance is exactly `Te yapearon seis soles con cincuenta. Código: uno dos tres.`

#### Scenario: Code disabled

- **GIVEN** amount 650 céntimos, name `Juan Pérez`, code `123`, and code disabled
- **WHEN** the announcement is composed
- **THEN** the utterance is exactly `Juan Pérez te yapeó seis soles con cincuenta.`

#### Scenario: Last enabled group cannot be disabled

- **GIVEN** amount and code are disabled and only name is enabled
- **WHEN** the user tries to disable name
- **THEN** name remains enabled because at least one content group must stay on

#### Scenario: Fallback is unaffected by content selection

- **GIVEN** name and code are disabled
- **WHEN** an unparsed notification arrives
- **THEN** the spoken utterance is exactly `Llegó un pago, pero no pude leerlo.`

### Requirement: Minimum amount suppresses small payments but never the fallback alert

The app SHALL suppress an announcement when the parsed amount is strictly less than the configured minimum amount. An amount exactly equal to the threshold MUST be announced.

Comparison MUST use the whole-céntimos representation from the parser, never floating point.

The minimum-amount filter MUST NOT suppress the unparsed fallback alert: a notification whose amount cannot be read has no amount to compare, and it MUST still produce the fallback alert so that a broken parser is never mistaken for "no payment arrived".

#### Scenario: Below threshold is suppressed

- **GIVEN** the minimum amount is `10.00` soles
- **WHEN** a payment of `S/ 6,50` is parsed
- **THEN** no announcement is spoken

#### Scenario: Exactly at threshold is announced

- **GIVEN** the minimum amount is `10.00` soles
- **WHEN** a payment of `S/ 10.00` is parsed
- **THEN** the payment is announced

#### Scenario: Above threshold is announced

- **GIVEN** the minimum amount is `10.00` soles
- **WHEN** a payment of `S/ 12` is parsed
- **THEN** the payment is announced

#### Scenario: Unparsed notification is never filtered by the threshold

- **GIVEN** the minimum amount is `50.00` soles
- **WHEN** a Yape notification arrives whose amount cannot be parsed
- **THEN** the fallback alert is still spoken

### Requirement: Settings changes apply live without an app restart

Every settings change MUST be read at the moment an announcement is composed and spoken, not snapshotted at app start. No restart, no service rebind, and no TTS re-initialization may be required for a setting to take effect.

#### Scenario: Toggle takes effect on the next payment

- **GIVEN** all content groups are enabled
- **WHEN** the user disables the amount and the next payment arrives
- **THEN** that announcement omits the amount and no restart is needed

### Requirement: Settings storage contains configuration only

The settings storage MUST NOT contain payment history. Recorded payments live in the Room history
database described by `payment-history`; `SharedPreferences` holds only configuration.

Notification text, notification extras, and any per-payment record MUST NOT be written to settings
storage. The minimum-amount value and the armed flags are configuration, not records of a payment.

#### Scenario: No payment data in settings storage

- **GIVEN** several payments have been announced and recorded in history
- **WHEN** the app's SharedPreferences are inspected
- **THEN** they contain only configuration entries — enable flag, speech rate, three content toggles, minimum amount, per-source armed flags, and the selected voice — and no payment content

### Requirement: Voice selection lists the Spanish voices actually installed on the device

The app SHALL let the user choose the voice used for announcements from the Spanish voices the
active `TextToSpeech` engine reports as installed.

- The picker MUST be built from the engine's reported voices at the moment it is shown. It MUST NOT
  contain a hard-coded voice list, a bundled catalogue, or a voice the engine does not report.
- Each entry MUST be identifiable by the engine's voice identifier and MUST be presented with a
  human-readable label containing its locale.
- Selecting a voice MUST apply to the next utterance, including the `Probar voz` sample, without an
  app restart or a visible engine re-initialization.
- The selection MUST be persisted and restored on the next launch.
- Changing the voice MUST NOT change the spoken text: the amount is still rendered as Spanish
  words, and the sentence composition rules are unchanged.
- When the engine reports no Spanish voice at all, the picker MUST show an unavailable state with
  guidance, MUST NOT crash, and MUST NOT fabricate an entry.

#### Scenario: The picker offers only engine-reported Spanish voices

- **GIVEN** the engine reports the Spanish voices `es-PE` labelled `Español (Perú)` and `es-ES` labelled `Español (España)`, and an English voice `en-US`
- **WHEN** the voice picker is opened
- **THEN** exactly the two Spanish voices are offered, each labelled with its locale, and no English voice or invented entry appears

#### Scenario: A selected voice is used for the next announcement

- **GIVEN** the voice is in automatic mode and the engine reports `es-PE` and `es-ES`
- **WHEN** the user selects the `es-ES` voice and the next payment of `S/ 6,50` from `Juan Pérez` arrives
- **THEN** the announcement is spoken with the `es-ES` voice and its text is still `Juan Pérez te yapeó seis soles con cincuenta.`

#### Scenario: Choosing a voice does not change how money is spoken

- **GIVEN** the voice is `es-PE` and the amount 650 céntimos is rendered
- **WHEN** the user switches the voice to `es-ES` and the same amount is announced
- **THEN** the rendered words are still exactly `seis soles con cincuenta`, with no digits and no `punto`

#### Scenario: The selected voice survives a restart

- **GIVEN** the user selected the `es-PE` voice
- **WHEN** the app process is killed and started again
- **THEN** the picker still shows `es-PE` as the selected voice

#### Scenario: No Spanish voice is reported

- **GIVEN** the engine reports no Spanish voice
- **WHEN** the voice picker or the control panel is displayed
- **THEN** the voice control shows an unavailable state with guidance, the app does not crash, and no voice entry is invented

### Requirement: A missing selected voice falls back and is surfaced, never silent

Before each utterance, the app MUST confirm that the stored voice identifier is still reported by
the active engine. If it is not — because the voice data was removed, the engine changed, or an
engine update dropped that voice — the app MUST:

- fall back to the automatic Spanish selection order defined in `voice-announcement`,
- speak the announcement normally, with the amount still in Spanish words,
- clear the stale selection so the picker shows automatic mode again, and
- display a visible, non-blocking notice in the control panel with the exact copy
  `La voz seleccionada ya no está disponible. Se usará la voz predeterminada.`

The app MUST NOT crash, MUST NOT remain silent, and MUST NOT speak in a non-Spanish voice without
reporting the degraded state.

#### Scenario: A removed voice falls back and announces

- **GIVEN** the stored selection is the voice `es-PE-x-removed` and the engine no longer reports it
- **WHEN** a payment of `S/ 6,50` from `Juan Pérez` arrives
- **THEN** the announcement `Juan Pérez te yapeó seis soles con cincuenta.` is spoken with the automatic Spanish voice, and the notice `La voz seleccionada ya no está disponible. Se usará la voz predeterminada.` is displayed in the control panel

#### Scenario: Fallback clears the stale selection

- **GIVEN** the fallback triggered because the selected voice disappeared
- **WHEN** the voice picker is opened afterwards
- **THEN** it reads as automatic, and the missing identifier is no longer stored as the selection

#### Scenario: A missing voice never produces silence

- **GIVEN** the selected voice is no longer available and the announcement queue is empty
- **WHEN** an unparseable notification arrives from an armed source
- **THEN** the fallback alert `Llegó un pago, pero no pude leerlo.` is still spoken

#### Scenario: The fallback applies to the test action too

- **GIVEN** the selected voice is no longer available
- **WHEN** the user taps `Probar voz`
- **THEN** the sample announcement is spoken with the automatic Spanish voice and the notice is displayed
