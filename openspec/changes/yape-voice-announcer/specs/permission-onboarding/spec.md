# Permission Onboarding Specification

## ADDED Requirements

### Requirement: Notification access is granted only through system settings

The app MUST NOT attempt to grant notification access programmatically, because Android does not allow it. The onboarding and the control panel SHALL open the system notification-access settings screen when the user acts on the grant step.

The app MUST NOT request runtime `POST_NOTIFICATIONS` in v1, because v1 posts no app notifications of its own and notification *access* is a separate, settings-granted permission.

*Assumption:* v1 posts no notifications, so `POST_NOTIFICATIONS` is not part of the flow. If a persistent status notification is added later, that decision belongs to a different change.

#### Scenario: Grant action opens system settings

- **GIVEN** notification access is not granted
- **WHEN** the user taps the grant step
- **THEN** the system notification-access settings screen opens and the app does not claim the permission was granted

### Requirement: Notification access state is detected as granted, denied, or revoked

The app SHALL report notification access with three distinguishable states:

- **Granted** — this app is present in the system's enabled notification listeners.
- **Denied** — access has never been granted on this install.
- **Revoked** — access was granted previously on this install and is now off.

State MUST be re-read from the system on every return to the foreground, and MUST also be correct immediately after process start and after device reboot. The app MUST NOT rely on an in-memory value cached at first launch, nor on the listener's `onListenerConnected` alone.

The revoked state SHALL be derived from a persisted boolean that records that access was once granted, combined with the live system state. That boolean is the only onboarding fact persisted.

#### Scenario: Fresh install reports denied

- **GIVEN** a fresh install where notification access was never granted
- **WHEN** the app opens
- **THEN** notification access is reported as Denied

#### Scenario: Granted state after the user returns from settings

- **GIVEN** the user granted notification access in system settings
- **WHEN** the user returns to the app
- **THEN** notification access is reported as Granted without restarting the app

#### Scenario: Explicit revocation is reported as revoked

- **GIVEN** access was granted and the app recorded that grant
- **WHEN** the user turns notification access off and returns to the app
- **THEN** notification access is reported as Revoked, distinctly from Denied

#### Scenario: State survives process restart

- **GIVEN** access was granted and the app process was killed
- **WHEN** the app starts again
- **THEN** notification access is reported as Granted, read from the system rather than from memory

#### Scenario: State is correct after reboot

- **GIVEN** the device rebooted while access was granted
- **WHEN** the app is opened
- **THEN** notification access is reported as Granted

### Requirement: Android 13+ restricted settings guidance

On devices running Android 13 or newer, the onboarding SHALL include a restricted-settings step explaining how a sideloaded app is unblocked: open the app's system App info screen, open the overflow menu, choose `Allow restricted settings`, and confirm.

The step MUST state the concrete navigation path in its own copy, because restricted-settings state is not exposed by any public API. The app MUST NOT claim to have detected or verified this state, and MUST NOT block the notification-access state on it.

#### Scenario: Restricted settings step appears on Android 13 and newer

- **GIVEN** the app runs on Android 13 or newer
- **WHEN** the onboarding is displayed
- **THEN** the restricted-settings step is shown with the App info overflow-menu path

#### Scenario: Restricted settings step is not shown on older versions

- **GIVEN** the app runs on Android 12 or older
- **WHEN** the onboarding is displayed
- **THEN** the restricted-settings step is not shown

#### Scenario: The app does not fake a detected restricted-settings state

- **GIVEN** the restricted-settings step is displayed
- **WHEN** the user reads the step state
- **THEN** it is presented as unverified guidance, not as a measured Granted or Denied state

### Requirement: Battery optimization guidance with detectable exemption state

The onboarding SHALL include a battery-optimization step, because aggressive OEM power management can kill the listener service on the devices this app targets.

The app MUST detect and report whether it is exempt from battery optimization, and MUST offer an action that opens the system screen where the user can exempt it. The app MUST NOT request the special `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission in v1; guidance through the settings screen is the supported path.

The exemption state MUST be re-read on return to the foreground, not cached from first launch.

#### Scenario: Exemption is detected after the user grants it

- **GIVEN** the app is not exempt from battery optimization
- **WHEN** the user exempts it in system settings and returns to the app
- **THEN** the battery-optimization step reports Exempt

#### Scenario: Guidance action opens the system screen

- **GIVEN** the app is not exempt from battery optimization
- **WHEN** the user taps the battery-optimization step action
- **THEN** a system screen that allows exempting the app is opened

### Requirement: Onboarding is a secondary flow reachable from the control panel

The onboarding MUST NOT be the app's main screen. The control panel is the entry screen; the
onboarding is a separate flow the user opens from it and can leave at any time.

The app SHALL offer the onboarding on first launch from the control panel and MUST keep it
reachable there afterwards. It MUST show all three setup steps with their current state, and it
MUST allow the user to return to the control panel even when a step is incomplete, so the user is
never trapped.

Each step MUST display one of: complete, incomplete, or unverified, using the copy that matches its
detection ability.

#### Scenario: First launch starts on the control panel

- **GIVEN** a fresh install with no stored onboarding record
- **WHEN** the app starts
- **THEN** the control panel is the first screen shown and it offers the onboarding entry, and opening that entry shows the three steps with their current states

#### Scenario: Incomplete onboarding can be left

- **GIVEN** notification access is still not granted
- **WHEN** the user leaves the onboarding
- **THEN** the control panel is displayed and reports the missing access

#### Scenario: Onboarding can be reopened later

- **GIVEN** the user previously dismissed the onboarding
- **WHEN** the user opens onboarding again from the control panel
- **THEN** the same three steps and their current states are shown

### Requirement: Per-source state is visible where the user sets it up

The control panel and the onboarding SHALL both show, for every registry source, whether that
source is armed. The onboarding MUST state plainly that arming a source produces no announcements
until notification access is granted, so the user does not mistake an armed toggle for a working
reader.

An armed source whose access is missing MUST NOT be presented as reading notifications.

#### Scenario: Armed but unreadable is stated plainly

- **GIVEN** notification access is Denied and both `Yape` and `Interbank` are armed
- **WHEN** the control panel and the onboarding are displayed
- **THEN** the panel lists both sources as armed and states that no payment can be heard until access is granted, and the onboarding states the same dependency

#### Scenario: Off source is shown as off, not as broken

- **GIVEN** notification access is granted and `interbank` is off
- **WHEN** the control panel is displayed
- **THEN** the `Interbank` row reads as off rather than as an error, while `Yape` reads as reading

### Requirement: The control panel surfaces the blocking states

The control panel SHALL present the app's operational state in a way the user can act on, at
minimum: notification access state, listener connection state, TTS availability, battery-optimization
state, and per-source armed state. It MUST also host the per-source toggles, the voice selection,
the `Probar voz` action, and the payment history described in `payment-history`.

When notification access is missing or revoked, the control panel MUST state that payments will not
be heard, so the user does not trust a silent app.

#### Scenario: Missing access is stated plainly

- **GIVEN** notification access is Denied or Revoked
- **WHEN** the control panel is displayed
- **THEN** it states that payments cannot be heard until access is granted, and offers the action that opens system settings

#### Scenario: Working state is stated plainly

- **GIVEN** access is Granted, the listener is Connected, TTS is available, and both sources are armed
- **WHEN** the control panel is displayed
- **THEN** both sources read as reading, all states read as healthy, and no blocking warning is shown
