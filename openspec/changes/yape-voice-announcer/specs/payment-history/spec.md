# Payment History Specification

## ADDED Requirements

### Requirement: Every announced payment is persisted locally in Room

The app SHALL persist one local record per payment that produced a spoken utterance, in a Room
database on the device. A record MUST be created for both outcomes:

- a parsed announcement, and
- an unparsed fallback alert.

Each record MUST carry exactly these fields:

| Field | Type | Presence |
| --- | --- | --- |
| Timestamp | epoch milliseconds, captured when the utterance was requested | always |
| Source id | registry id of the announcing source | always |
| Amount | whole céntimos, integer | present for parsed announcements, absent for fallback events |
| Payer name | text, as extracted | present only when the parser reported one |
| Security code | text, as extracted, three digits | present only when the parser reported one |
| Fallback flag | boolean | always |

The amount MUST be stored as an integer number of céntimos. No monetary value may be stored as a
floating-point number anywhere in this feature.

Events that produced no utterance MUST NOT be recorded: a payment suppressed by the minimum-amount
threshold, a payment silenced because announcements are disabled, and any notification from a
source that is off.

This change specifies no retention limit, no deletion action, and no editing of recorded rows.

#### Scenario: A parsed payment is recorded with all extracted fields

- **GIVEN** a Yape payment is announced with amount 650 céntimos, payer `Juan Pérez`, and code `123`
- **WHEN** the history record is written
- **THEN** one row exists with source id `yape`, amount `650`, payer name `Juan Pérez`, code `123`, and fallback flag false

#### Scenario: A fallback event is recorded without an amount

- **GIVEN** an unparseable Yape notification produced the fallback alert
- **WHEN** the history record is written
- **THEN** one row exists with source id `yape`, no amount, no payer name, no code, and fallback flag true

#### Scenario: A suppressed payment leaves no row

- **GIVEN** the minimum amount is `10.00` soles
- **WHEN** a payment of `S/ 6,50` is parsed and suppressed
- **THEN** no history row is written for it

#### Scenario: A payment from an off source leaves no row

- **GIVEN** `interbank` is off
- **WHEN** an Interbank payment notification arrives and is ignored
- **THEN** no history row is written for it

### Requirement: A history write never blocks, delays, or suppresses speech

The announcement path and the history path MUST be ordered so that speech never waits on storage.
An announcement MUST be handed to the speech queue before or independently of the history write,
and the write MUST run off the main thread.

Any storage failure — full disk, corrupt database, missing database file, an exception thrown by
the data layer — MUST be contained: it MUST NOT prevent, delay, truncate, duplicate, or silence an
announcement, and it MUST NOT throw into the notification listener or the speech queue.

A storage failure MUST be observable as a non-blocking condition in the control panel. It MUST NOT
be presented as a payment failure.

#### Scenario: A failing write still produces the announcement

- **GIVEN** the history data layer throws on every insert
- **WHEN** a Yape payment of `S/ 6,50` from `Juan Pérez` arrives
- **THEN** the announcement `Juan Pérez te yapeó seis soles con cincuenta.` is spoken exactly once

#### Scenario: A fallback alert is spoken even when the write fails

- **GIVEN** the history data layer throws on every insert
- **WHEN** an unparseable Yape notification arrives
- **THEN** `Llegó un pago, pero no pude leerlo.` is still spoken

#### Scenario: A slow write does not delay speech

- **GIVEN** the history data layer takes several seconds to complete an insert
- **WHEN** a payment arrives
- **THEN** speech begins on the normal announcement timeline and does not wait for the insert to return

#### Scenario: A write failure is surfaced without alarming the user

- **GIVEN** the last history write failed
- **WHEN** the control panel is displayed
- **THEN** it shows a non-blocking notice that the history could not be saved, while the payment announcement state remains healthy

### Requirement: The panel shows today's history and today's total

The control panel SHALL display the payment history for the current local calendar day, most recent
first, and a total for that day.

- Each row MUST show the source label, the amount in soles, and the security code when present.
- A fallback row MUST be visibly marked as unreadable and MUST show no amount.
- Ordering MUST be deterministic: timestamp descending, and for equal timestamps the most recently
  inserted row first.
- The daily total MUST be computed with integer céntimos arithmetic as the sum of the day's amounts,
  and MUST exclude fallback rows and rows with no amount.
- A day with no records MUST display a total of `S/ 0.00` and an explicit empty state, not a hidden
  section.

#### Scenario: Two payments produce the day's total

- **GIVEN** today's announced payments are `S/ 6,50` from `Juan Pérez` and `S/ 12.00` from `Rosa Quispe`
- **WHEN** the control panel displays the history
- **THEN** both rows appear with `Rosa Quispe` first, and the today total reads `S/ 18.50`

#### Scenario: A fallback row appears but does not change the total

- **GIVEN** today's records are one payment of `S/ 6,50` and one fallback event
- **WHEN** the control panel displays the history
- **THEN** two rows appear, the fallback row is marked as unreadable with no amount, and the today total reads `S/ 6.50`

#### Scenario: Yesterday's payments are excluded from today's total

- **GIVEN** one payment of `S/ 10.00` was recorded yesterday and one payment of `S/ 6,50` today
- **WHEN** the control panel displays the history
- **THEN** the today total reads `S/ 6.50` and the earlier payment is not counted

#### Scenario: An empty day is stated, not hidden

- **GIVEN** no payment has been recorded today
- **WHEN** the control panel displays the history
- **THEN** the history section shows an explicit empty state and a today total of `S/ 0.00`

### Requirement: History survives process death and device reboot

Recorded history MUST be durable. After the app process is killed, or the device reboots, the
records still present MUST be listed again with the same fields and MUST contribute to the same
totals.

#### Scenario: History is intact after the process is killed

- **GIVEN** a payment of `S/ 6,50` from `Juan Pérez` with code `123` was recorded
- **WHEN** the app process is killed and the control panel is opened again
- **THEN** the row is listed with payer `Juan Pérez`, amount `S/ 6.50`, and code `123`, and the today total still includes it

#### Scenario: History is intact after a reboot

- **GIVEN** today's records exist in the history database
- **WHEN** the device reboots and the control panel is opened
- **THEN** the same rows and the same today total are displayed

### Requirement: The history has no export capability

The app MUST NOT provide any way to export payment history. Specifically, there MUST be no CSV,
TSV, JSON, PDF, or spreadsheet output; no share intent; no file writing of history rows; and no
copy-to-clipboard of rows. The control panel MUST NOT contain any export, share, or download action.

This is a product boundary, not a deferred item: no export command, setting, flag, or placeholder
requirement may be added to satisfy this change.

#### Scenario: The panel offers no export action

- **GIVEN** the control panel with several recorded payments
- **WHEN** the user inspects the history section
- **THEN** no control offers export, share, download, or clipboard of the payment rows

#### Scenario: No file is produced from history

- **GIVEN** several payments have been recorded and displayed
- **WHEN** the app's external files and cache directories are inspected
- **THEN** they contain no generated CSV, spreadsheet, or history dump

### Requirement: Stored history is limited to announcement fields and never leaves the device

History rows MUST contain only the fields listed in the first requirement. The app MUST NOT store
raw notification text, notification titles, `Notification` extras, or any other captured
notification payload.

The history MUST NOT be transmitted off the device, and no network permission may be added to
support it.

#### Scenario: Raw notification text is absent from storage

- **GIVEN** a Yape notification with text `Juan Pérez te envió S/ 50. Código: 123` was announced
- **WHEN** the history database is inspected
- **THEN** the stored row contains the extracted fields, and the raw notification text `Juan Pérez te envió S/ 50. Código: 123` appears nowhere

#### Scenario: History is never uploaded

- **GIVEN** recorded history rows exist
- **WHEN** the app performs any background or foreground work
- **THEN** no request carrying those rows is made, and the app declares no network permission
