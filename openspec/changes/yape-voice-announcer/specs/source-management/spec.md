# Source Management Specification

## ADDED Requirements

### Requirement: The source registry is the single list of readable notification sources

The app SHALL define every readable payment source as a registry entry, and the registry MUST be
data rather than per-source code branches. A registry entry MUST carry, at minimum:

- a stable source id,
- the user-facing label shown on the panel,
- the exact notification `packageName` to accept, and
- the parser pattern set selected for that source.

The control panel, the notification listener, and parser selection MUST all iterate the registry.
None of these surfaces may contain a hard-coded branch per source.

Adding a source later MUST be additive: a new registry entry, its pattern set, and its fixtures.
It MUST NOT require changing the panel layout logic, the announcement queue, or the history
schema.

#### Scenario: The panel renders one row per registry entry

- **GIVEN** a registry containing exactly three entries, labelled `Yape`, `BBVA` and `Interbank`
- **WHEN** the control panel is displayed
- **THEN** it shows exactly three source rows, in registry order, each with its own toggle

#### Scenario: A fourth source is added without touching panel logic

- **GIVEN** a registry extended with a fourth entry, id `demo-bank`, label `Banco Demo`, package `com.example.demo`, and its own pattern set
- **WHEN** the control panel is displayed
- **THEN** a fourth source row labelled `Banco Demo` appears with its own toggle, and no panel, queue, or history code was edited to produce it

### Requirement: v1 registers exactly three sources, Yape, BBVA and Interbank

The registry SHALL contain exactly three entries in v1:

| Source id | Label | Package name |
| --- | --- | --- |
| `yape` | `Yape` | `com.bcp.innovacxion.yapeapp` |
| `bbva` | `BBVA` | the BBVA app package, unverified and therefore not guessed |
| `interbank` | `Interbank` | the Interbank app package, unverified and therefore not guessed |

The `yape` entry MUST accept only the exact package string `com.bcp.innovacxion.yapeapp`, compared
with exact equality — never a prefix or substring match.

The `bbva` and `interbank` entries SHALL represent Plin payments received inside each bank's own
app. There MUST NOT be a separate source entry for Plin, and the panel MUST NOT show a toggle
labelled `Plin`: Plin is a payment function inside each bank's app, not an installable app, so a
`Plin` toggle would be a control with no parser behind it.

#### Scenario: The panel offers exactly the two real toggles

- **GIVEN** a fresh install with the v1 registry
- **WHEN** the control panel renders its source list
- **THEN** the labels shown are exactly `Yape` and `Interbank`, and no control labelled `Plin` exists

#### Scenario: Plin payments arrive through the Interbank entry

- **GIVEN** the `interbank` source is armed and notification access is granted
- **WHEN** a Plin payment notification is posted by the Interbank app
- **THEN** it is attributed to source id `interbank`, and no separate `plin` source id exists anywhere in the app

#### Scenario: A look-alike package is not accepted

- **GIVEN** the `yape` source is armed
- **WHEN** a notification is posted with package `com.bcp.innovacxion.yapeapp.debug`
- **THEN** the notification is ignored because the package does not equal a registry entry exactly

### Requirement: The Interbank package identifier is an explicit, single-source assumption

No real Interbank or Plin notification has been captured. The package identifier used by the
`interbank` entry MUST therefore be held in exactly one place, marked in code as unverified, and
MUST be replaced by the value captured from a real Interbank installation before the source is
treated as calibrated.

The app MUST NOT present the unverified identifier as established fact in user-facing copy, and it
MUST NOT fall back to guessing a package name by substring, by a list of candidates, or by scanning
notifications from unknown packages.

Calibration is complete only when 5–10 real Interbank/Plin notifications have been captured and the
`interbank` pattern set and package identifier are pinned to them. Until then, the source is wired
and unit-tested against placeholder fixtures, but it MUST NOT be described as verified.

#### Scenario: Exactly one place declares the Interbank identifier

- **GIVEN** the source code of the app
- **WHEN** the Interbank package identifier is searched for
- **THEN** it appears once, in the registry entry, accompanied by an unverified marker

#### Scenario: An unknown Interbank package is never guessed

- **GIVEN** the `interbank` entry still carries its unverified identifier
- **WHEN** a notification arrives from a package the registry does not declare
- **THEN** it is ignored rather than attributed to Interbank by similarity

### Requirement: A source toggle arms exactly that source's parser

Arming a source SHALL make notifications from that source's package eligible for parsing, announcement, and history recording, using that source's own pattern set.

While a source is off, every notification from that source's package MUST produce no observable
effect: no parsing result, no announcement request, no fallback alert, no queue entry, and no
history row. Turning a source off MUST NOT affect any other source.

#### Scenario: Armed source announces, off source stays silent

- **GIVEN** `yape` is armed and `interbank` is off
- **WHEN** a Yape payment notification and an Interbank payment notification each arrive
- **THEN** the Yape payment is announced and the Interbank payment produces no speech and no history row

#### Scenario: The default is symmetric

- **GIVEN** `interbank` is armed and `yape` is off
- **WHEN** an Interbank payment notification and a Yape payment notification each arrive
- **THEN** the Interbank payment is announced and the Yape payment produces no speech and no history row

#### Scenario: An off source's unparseable notification stays silent

- **GIVEN** `interbank` is off
- **WHEN** an Interbank notification arrives whose text cannot be parsed
- **THEN** nothing is spoken, because the fallback alert exists only for armed sources

#### Scenario: Disarming one source leaves the other working

- **GIVEN** both sources are armed and announcements are taking place
- **WHEN** the user turns `interbank` off
- **THEN** the next Yape payment is still announced and the next Interbank payment is silent

### Requirement: Armed state is persisted per source and survives restart

The armed state of each registry entry SHALL be persisted independently, keyed by source id, in
framework `SharedPreferences`. Both sources MUST default to armed on a fresh install, because a
silent default would hide payments.

Persisted armed state MUST survive process death and device reboot. A missing or unreadable value
MUST fall back to its documented default rather than crash or disable the source silently. A change
to the armed state MUST take effect for the next notification without an app restart and without a
service rebind.

#### Scenario: A fresh install has both sources armed

- **GIVEN** no armed state has ever been stored
- **WHEN** the control panel is displayed for the first time
- **THEN** `Yape` and `Interbank` both read as armed

#### Scenario: Per-source state survives process death

- **GIVEN** the user armed `interbank` and turned `yape` off
- **WHEN** the app process is killed and started again
- **THEN** `Interbank` is still armed and `Yape` is still off

#### Scenario: Toggling applies without restart

- **GIVEN** both sources are armed
- **WHEN** the user turns `interbank` off and an Interbank payment arrives in the same session
- **THEN** no announcement is made and no restart was needed

### Requirement: The panel reflects the actual armed state, not a cached one

The control panel MUST read each source's armed state from the settings store when the panel is
shown and when the app returns to the foreground. It MUST NOT render a value captured at first
launch or held only in memory.

If the stored state changes outside the panel's own toggle interaction, the panel MUST reflect the
new value on the next foreground return without an app restart.

#### Scenario: External change is reflected on return

- **GIVEN** the panel previously displayed `interbank` as armed
- **WHEN** the stored armed state for `interbank` becomes off and the user returns to the panel
- **THEN** the `Interbank` toggle is displayed as off

#### Scenario: Toggle and stored state never disagree

- **GIVEN** the user turns `yape` off on the panel
- **WHEN** the panel recomposes
- **THEN** the `Yape` row reads as off and no other row changes state

### Requirement: Sources the registry does not declare are ignored

Any notification whose package name is not declared by a registry entry MUST be ignored with no
observable effect and no audible alert, regardless of the notification's content.

#### Scenario: A messaging app notification is ignored

- **GIVEN** both sources are armed and notification access is granted
- **WHEN** a notification arrives with package `com.whatsapp` and text `Juan Pérez te envió S/ 50. Código: 123`
- **THEN** nothing is parsed, spoken, or recorded

### Requirement: Source identity is carried through parsing, speech, and history

Every notification accepted by a registry entry SHALL carry its source id through the rest of the
pipeline, so that the announcement, the fallback alert, and the persisted history row can all
identify which source produced the event. The source id MUST NOT be re-derived later from the
notification text.

#### Scenario: A recorded payment names its source

- **GIVEN** `interbank` is armed
- **WHEN** an Interbank payment of `S/ 6,50` from `Juan Pérez` is announced
- **THEN** the resulting history row carries source id `interbank`, and the panel displays the label `Interbank` for it

#### Scenario: The source id survives a fallback

- **GIVEN** `yape` is armed
- **WHEN** an unparseable Yape notification is announced with the fallback alert
- **THEN** the resulting history row carries source id `yape` and is marked as a fallback event
