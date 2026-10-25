# Notification Listening Specification

## ADDED Requirements

### Requirement: Registry-scoped package filtering

The system SHALL consider only notifications whose `packageName` equals exactly the package name of
a registry source entry (see `source-management`). Matching MUST be an exact string comparison,
never a prefix or substring match.

Included in v1 are the Yape entry (`com.bcp.innovacxion.yapeapp`) and the Interbank entry. A
notification from a registry source that is currently off MUST be ignored exactly as an unknown
package is ignored.

For every notification from any other package, the system MUST do nothing observable: no text extraction, no parsing attempt, no announcement request, no queue entry, and no change to persisted state. Ignoring a notification MUST NOT emit an audible alert.

*Assumption:* `com.bcp.innovacxion.yapeapp` is Yape's package name as stated in the proposal. It has not been verified against an installed Yape APK in this repository. The Interbank package name is unverified as well; see `source-management`.

#### Scenario: Notification from the Yape package is processed

- **GIVEN** notification access is granted and the listener is connected
- **WHEN** a notification is posted with package `com.bcp.innovacxion.yapeapp` and text `Juan Pérez te envió S/ 50. Código: 123`
- **THEN** the listener accepts the notification and forwards its extracted text to the payment parser

#### Scenario: Notification from an unrelated package is ignored

- **GIVEN** notification access is granted and the listener is connected
- **WHEN** a notification is posted with package `com.whatsapp` and text `Juan Pérez te envió S/ 50. Código: 123`
- **THEN** no announcement is requested, no queue entry is created, the text is never parsed, and no persisted state changes

#### Scenario: Package with a similar prefix is ignored

- **GIVEN** notification access is granted and the listener is connected
- **WHEN** a notification is posted with package `com.bcp.innovacxion.yapeapp.debug`
- **THEN** the notification is ignored and nothing is announced

#### Scenario: Notification from the Interbank package is processed

- **GIVEN** notification access is granted, the listener is connected, and `interbank` is armed
- **WHEN** a notification is posted with the registry package for Interbank and Plin payment text
- **THEN** the listener accepts it and forwards the extracted text to the Interbank pattern set

#### Scenario: Notification from an off source is ignored

- **GIVEN** notification access is granted, the listener is connected, and `interbank` is off
- **WHEN** a notification is posted with the registry package for Interbank
- **THEN** no text is extracted, nothing is parsed, nothing is announced, and no history row is written

### Requirement: Notification text extraction including big-text and multi-line extras

The listener SHALL extract text from the notification's title and text content, and MUST also cover the extended extras that real notifications may use.

The extraction MUST use the following precedence so that truncated collapsed text is never concatenated with its full replacement:

1. The title is always included, if present.
2. If `Notification.EXTRA_BIG_TEXT` is present and non-empty, it MUST be used **instead of** `EXTRA_TEXT`.
3. Otherwise, if `Notification.EXTRA_TEXT_LINES` is present and non-empty, its lines MUST be joined with newlines and used **instead of** `EXTRA_TEXT`.
4. Otherwise, `Notification.EXTRA_TEXT` MUST be used if present.

Missing or null extras MUST NOT throw and MUST NOT abort processing: a Yape notification with no usable text is still a Yape notification and MUST continue to the parser (see the unparsed-outcome requirement in `payment-parsing`).

*Assumption:* the shape of a real Yape notification (whether the readable sentence lives in `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, or `EXTRA_TEXT_LINES`, and where the security code sits) is unverified. The precedence above is the smallest rule set that tolerates all three shapes without double-reading an amount.

#### Scenario: Extended text replaces truncated collapsed text

- **GIVEN** a Yape notification whose `EXTRA_TEXT` is `Juan Pérez te envió S/ 5...` and whose `EXTRA_BIG_TEXT` is `Juan Pérez te envió S/ 50. Código: 123`
- **WHEN** the listener extracts the notification text
- **THEN** the extracted text contains `S/ 50` and does not contain the truncated `S/ 5`

#### Scenario: Inbox-style multi-line text is read

- **GIVEN** a Yape notification with no big text and with `EXTRA_TEXT_LINES` equal to `["Pago recibido", "S/ 6,50 de Juan Pérez", "Código: 123"]`
- **WHEN** the listener extracts the notification text
- **THEN** the extracted text contains all three lines separated by newlines, including `S/ 6,50` and `Código: 123`

#### Scenario: Notification with no text extras still reaches the parser

- **GIVEN** a Yape notification whose title, text, big text, and text lines are all absent
- **WHEN** the listener processes it
- **THEN** extraction completes without an exception and the empty extraction is passed to the parser, which classifies it as unparsed

### Requirement: Thin listener with delegated detection logic

The `NotificationListenerService` implementation SHALL stay thin: it MUST only receive the notification, apply package filtering, extract text, and delegate the remaining decisions to testable non-Android code.

Filtering, text normalization, parsing, deduplication, and announcement composition MUST be implemented outside the service class, with no Android framework imports, so that they are unit-testable on the JVM with `./gradlew :app:testDebugUnitTest`.

#### Scenario: Listener logic is exercised without an Android runtime

- **GIVEN** the filtering and deduplication components
- **WHEN** `./gradlew :app:testDebugUnitTest` runs
- **THEN** tests cover accept/reject by package and duplicate suppression, and those components compile without any Android framework import

### Requirement: Arrival-order delivery to the announcement pipeline

Accepted notifications SHALL be handed to the announcement pipeline in the order the system delivered them to the listener. The system MUST NOT reorder notifications by amount, name, text length, or parse success.

#### Scenario: Three rapid payments keep their arrival order

- **GIVEN** notification access is granted and the listener is connected
- **WHEN** three Yape notifications arrive one second apart with amounts `S/ 6,50`, `S/ 12`, and `S/ 4`
- **THEN** the announcement pipeline receives them in the order `6,50`, `12`, `4`

### Requirement: Duplicate notification suppression

The system MUST announce a given notification at most once. A notification re-posted by the system with the same identity and the same extracted text MUST NOT produce a second announcement.

Notification identity SHALL combine `packageName`, the notification `id`, the notification `tag` when present, and a fingerprint of the extracted text. The identity set is held in memory for the process lifetime with a minimum retention window of 10 minutes.

A notification that reuses a previously seen `id` but carries different extracted text MUST be treated as a new notification and announced.

*Assumption:* the identity set is deliberately kept in memory and is not persisted, even though
payment history is. A re-post of a notification from a previous process lifetime may therefore be
announced again; this is an accepted limitation, not a requirement.

#### Scenario: Re-posted identical notification is announced once

- **GIVEN** a Yape notification with id `7` and text `Juan Pérez te envió S/ 50. Código: 123` was already announced
- **WHEN** the system re-posts the same notification with id `7` and the same text two seconds later
- **THEN** no second announcement is requested

#### Scenario: New payment reusing the same notification id is announced

- **GIVEN** a Yape notification with id `7` and text `Juan Pérez te envió S/ 50. Código: 123` was already announced
- **WHEN** a Yape notification with id `7` and text `Rosa Quispe te yapeó S/ 8. Código: 456` arrives
- **THEN** it is treated as a new notification and announced

### Requirement: Listener connection state exposure

The system SHALL expose the listener connection state so the UI can report it. At minimum it MUST distinguish:

- **Connected** — the service is bound and receiving notifications.
- **Disconnected** — notification access appears granted but the service is not connected.
- **AccessMissing** — notification access is not granted to this app.

The state MUST be re-evaluated when the app returns to the foreground and MUST NOT be served only from a value cached at first launch. State changes caused by the user revoking access MUST be reflected without an app restart.

#### Scenario: Revoking notification access is detected on return to the app

- **GIVEN** the app reported Connected
- **WHEN** the user revokes notification access in system settings and returns to the app
- **THEN** the reported state becomes AccessMissing without restarting the app

#### Scenario: Reconnection is reported after the service rebinds

- **GIVEN** the listener reported Disconnected after a service rebind
- **WHEN** `onListenerConnected` fires again
- **THEN** the reported state returns to Connected

### Requirement: Notification content never leaves the device and raw text is never persisted

The system MUST NOT send notification text, payer names, amounts, or security codes off the
device. Notification content exists only in memory for the duration of processing and announcement.

Raw notification text, notification titles, and `Notification` extras MUST NOT be written to
`SharedPreferences`, to internal storage, or to the history database. The only notification-derived
data that is persisted is the structured payment record defined by `payment-history`: timestamp,
source id, amount in céntimos, payer name when present, security code when present, and the
fallback flag.

#### Scenario: Raw notification text is never written to storage

- **GIVEN** three notifications have been processed and announced
- **WHEN** the app's SharedPreferences and internal storage are inspected
- **THEN** no raw notification text, title, or extras payload appears in them

#### Scenario: Only structured fields reach the history database

- **GIVEN** a notification with text `Juan Pérez te envió S/ 50. Código: 123` was announced
- **WHEN** the persisted records are inspected
- **THEN** the payment record holds only the timestamp, source id, amount, name, code, and fallback flag, and not the notification text
