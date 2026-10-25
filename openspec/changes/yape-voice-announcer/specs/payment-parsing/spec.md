# Payment Parsing Specification

## ADDED Requirements

### Requirement: Outcome classification with a first-class unparsed result

The parser SHALL classify every notification text accepted from an armed registry source into
exactly one outcome, using that source's pattern set:

- **Parsed** — an amount was extracted. Name and code are independently optional.
- **Unparsed** — no valid amount could be extracted.

An unparsed outcome is a normal, first-class result, not an error path: it MUST be returned (never thrown, never swallowed) and MUST cause an audible fallback alert (see `voice-announcement`). The parser MUST NOT return a default, guessed, or invented amount under any circumstance.

Inputs classified as Unparsed include: empty text, text with no currency amount, and an amount of zero.

#### Scenario: Text with a valid amount is parsed

- **GIVEN** the notification text `Juan Pérez te envió S/ 50. Código: 123`
- **WHEN** the parser runs
- **THEN** the outcome is Parsed with amount 5000 céntimos, name `Juan Pérez`, and code `123`

#### Scenario: Text without any amount is unparsed

- **GIVEN** the notification text `Recibiste un yapeo. Código: 123`
- **WHEN** the parser runs
- **THEN** the outcome is Unparsed and no amount is reported

#### Scenario: Empty notification text is unparsed, not ignored

- **GIVEN** an empty extracted text
- **WHEN** the parser runs
- **THEN** the outcome is Unparsed and the fallback alert is triggered

### Requirement: Amount extraction across currency variants

The parser SHALL extract a Peruvian sol amount from the notification text. The following lexical forms MUST all be recognized, case-insensitively, with optional whitespace between the currency marker and the number:

- `S/ 50` → 50.00
- `S/ 50.00` → 50.00
- `S/50` → 50.00
- `S/ 6,50` → 6.50

The currency marker MUST be `S/` or `S/.`. A bare number with no currency marker is not an amount.

#### Scenario: Marker without decimals

- **GIVEN** the text `Juan Pérez te envió S/ 50`
- **WHEN** the parser extracts the amount
- **THEN** the amount is 5000 céntimos (50.00 soles)

#### Scenario: Marker with dot decimals

- **GIVEN** the text `Juan Pérez te envió S/ 50.00`
- **WHEN** the parser extracts the amount
- **THEN** the amount is 5000 céntimos

#### Scenario: Marker with no separating space

- **GIVEN** the text `Juan Pérez te envió S/6,50`
- **WHEN** the parser extracts the amount
- **THEN** the amount is 650 céntimos

#### Scenario: Comma decimals

- **GIVEN** the text `Juan Pérez te envió S/ 6,50. Código: 123`
- **WHEN** the parser extracts the amount
- **THEN** the amount is 650 céntimos

#### Scenario: Bare number is not an amount

- **GIVEN** the text `Juan Pérez te envió 50. Código: 123`
- **WHEN** the parser runs
- **THEN** the outcome is Unparsed because no currency marker precedes a number

### Requirement: Amount represented as whole céntimos with deterministic separator rules

Amounts SHALL be represented internally as a whole number of céntimos (integer). Parsing MUST NOT use floating-point arithmetic, so that spoken values are exact.

Separator resolution MUST be deterministic:

1. If both `.` and `,` are present, the last occurring separator is the decimal separator and the other is a thousands separator that is removed (`S/ 1,500.00` → 150000 céntimos).
2. If exactly one separator is present and it is followed by exactly 2 digits, it is the decimal separator (`S/ 6,50` → 650 céntimos).
3. If exactly one separator is present and it is followed by 1 digit, it is the decimal separator and the cents are padded (`S/ 6,5` → 650 céntimos).
4. If exactly one separator is present and it is followed by exactly 3 digits, it is a thousands separator (`S/ 1,500` → 150000 céntimos).

*Assumption:* rule 4 resolves a genuinely ambiguous string in favor of thousands because Peruvian formatting uses `,` and `.` as thousands separators for amounts of that size. If real Yape fixtures contradict it, the fixture wins and this rule is amended.

#### Scenario: Thousands separator is not read as cents

- **GIVEN** the text `Juan Pérez te envió S/ 1,500.00`
- **WHEN** the parser extracts the amount
- **THEN** the amount is 150000 céntimos and not 1500 or 15000000

#### Scenario: Single decimal digit is padded

- **GIVEN** the text `Juan Pérez te envió S/ 6,5`
- **WHEN** the parser extracts the amount
- **THEN** the amount is 650 céntimos

#### Scenario: A zero amount is not announced as a payment

- **GIVEN** the text `Juan Pérez te envió S/ 0.00`
- **WHEN** the parser runs
- **THEN** the outcome is Unparsed

### Requirement: First amount wins deterministically

When the extracted text contains more than one currency-marked number, the parser MUST use the first occurrence in reading order and MUST NOT sum, average, or combine them. The chosen amount MUST be the same on repeated runs for identical input.

*Assumption:* a real Yape payment notification is assumed to carry a single payment amount. This rule exists only to make multi-amount text deterministic rather than undefined.

#### Scenario: Two amounts resolve to the first one

- **GIVEN** the text `Juan Pérez te envió S/ 6,50. Saldo: S/ 200.00`
- **WHEN** the parser extracts the amount
- **THEN** the amount is 650 céntimos

### Requirement: Payer name extraction with tolerant verb matching

The parser SHALL extract the payer name from the words that precede a recognized payment verb phrase. Recognition MUST be case-insensitive and accent-insensitive.

Recognized verb phrases: `te envió`, `te envio`, `te yapeó`, `te yapeo`, `te transfirió`, `te transfirio`.

The candidate name is the run of words immediately before the verb phrase, bounded by: the start of the text, a newline, a sentence terminator, a colon, a digit, or a currency marker. It MUST be limited to at most 6 words and trimmed of surrounding punctuation and whitespace.

A candidate that is empty, that consists only of non-letter characters, or that equals the
announcing source's app label (`Yape` for the Yape entry, `Interbank` for the Interbank entry,
case-insensitive) MUST be rejected so that a notification whose sender label sits before the verb
does not yield the bank name as a payer name.

When no name qualifies, the parser MUST report Parsed with no name. A missing name MUST NOT make the whole notification unparsed.

*Assumption:* the real Yape wording is unverified. The verb list above is the tolerance set fixed by the proposal; additional verbs are added only from real captured fixtures.

#### Scenario: Name before "te envió"

- **GIVEN** the text `Juan Pérez te envió S/ 50. Código: 123`
- **WHEN** the parser extracts the name
- **THEN** the name is `Juan Pérez`

#### Scenario: Name before "te yapeó"

- **GIVEN** the text `Rosa Quispe te yapeó S/ 6,50`
- **WHEN** the parser extracts the name
- **THEN** the name is `Rosa Quispe`

#### Scenario: Name before "te transfirió"

- **GIVEN** the text `Luis Ramos te transfirió S/ 12.00`
- **WHEN** the parser extracts the name
- **THEN** the name is `Luis Ramos`

#### Scenario: Accent-insensitive verb matching

- **GIVEN** the text `ANA TORRES TE ENVIO S/ 8.00`
- **WHEN** the parser extracts the name
- **THEN** the name is `ANA TORRES`

#### Scenario: Truncated or partial name is preserved as written

- **GIVEN** the text `Juan P. te yapeó S/ 10`
- **WHEN** the parser extracts the name
- **THEN** the name is `Juan P` and the outcome is Parsed

#### Scenario: Missing name still yields a parsed amount

- **GIVEN** the text `Te enviaron S/ 50. Código: 123`
- **WHEN** the parser runs
- **THEN** the outcome is Parsed with amount 5000 céntimos and no name

#### Scenario: "Recibiste" wording parses the amount without inventing a name

- **GIVEN** the text `Recibiste S/ 50 de Juan Pérez`
- **WHEN** the parser runs
- **THEN** the outcome is Parsed with amount 5000 céntimos, and no name is reported because `de Juan Pérez` is not one of the recognized verb phrases

#### Scenario: Sender label is not mistaken for a payer name

- **GIVEN** the text `Yape\nte envió S/ 20. Código: 987`
- **WHEN** the parser extracts the name
- **THEN** no name is reported and the outcome is still Parsed

#### Scenario: The Interbank sender label is not mistaken for a payer name

- **GIVEN** the text `Interbank\nte transfirió S/ 20. Código: 987` processed under the Interbank source
- **WHEN** the parser extracts the name
- **THEN** no name is reported and the outcome is still Parsed

### Requirement: Security code extraction with optional presence

The parser SHALL extract a 3-digit security code.

A labeled occurrence MUST take precedence: the label `Código`, `Codigo`, `Cód.`, or `Cod.`, optionally followed by `:`, then optional whitespace, then exactly 3 digits.

If no labeled occurrence exists, a standalone 3-digit token MAY be used as the code only when it is not part of a longer number, not adjacent to a currency marker, and not part of an `HH:MM` time pattern.

A missing code MUST NOT make the notification unparsed. The extracted code MUST be reported verbatim as three digits with no transformation other than stripping surrounding whitespace.

#### Scenario: Labeled code is extracted

- **GIVEN** the text `Juan Pérez te envió S/ 50. Código: 123`
- **WHEN** the parser extracts the code
- **THEN** the code is `123`

#### Scenario: Code without a colon

- **GIVEN** the text `Juan Pérez te envió S/ 50. Codigo 045`
- **WHEN** the parser extracts the code
- **THEN** the code is `045` including its leading zero

#### Scenario: Missing code still yields a parsed amount

- **GIVEN** the text `Juan Pérez te envió S/ 50`
- **WHEN** the parser runs
- **THEN** the outcome is Parsed with amount 5000 céntimos and no code

#### Scenario: A time is not mistaken for a code

- **GIVEN** the text `Juan Pérez te envió S/ 50 a las 21:35`
- **WHEN** the parser extracts the code
- **THEN** no code is reported

### Requirement: Wording tolerance without semantic guessing

The parser SHALL normalize the input before matching: Unicode normalization, accent-insensitive comparison, case-insensitive comparison, and collapsed runs of whitespace.

The parser MUST NOT infer fields from anything other than the recognized patterns above: no values derived from expected defaults, from the current time, from previously parsed notifications, or from the notification's other metadata.

#### Scenario: Extra whitespace and mixed case do not prevent parsing

- **GIVEN** the text `  juan   pérez   TE   ENVIÓ   S/   6,50  .  código:   123  `
- **WHEN** the parser runs
- **THEN** the outcome is Parsed with amount 650 céntimos, name `juan pérez`, and code `123`

#### Scenario: Unrecognized wording is unparsed rather than guessed

- **GIVEN** the text `Tienes un nuevo movimiento en tu cuenta`
- **WHEN** the parser runs
- **THEN** the outcome is Unparsed and no amount, name, or code is reported

### Requirement: Parser is pure Kotlin and JVM-testable

The parser SHALL be implemented as pure Kotlin with zero Android framework imports, so it is exercised by unit tests through `./gradlew :app:testDebugUnitTest` without Robolectric, instrumentation, or a device.

#### Scenario: Parser tests run on the JVM

- **GIVEN** the parser module and its unit tests
- **WHEN** `./gradlew :app:testDebugUnitTest` runs
- **THEN** all parser tests pass and the parser source compiles with no `android.*` import

### Requirement: Parsing is dispatched by source, never by a global mode

The parser entry point SHALL accept the source id together with the notification text and MUST apply
only that source's pattern set. Exactly one pattern set may run per notification.

Every pattern set MUST satisfy the same extraction contract defined above: amount in whole céntimos,
optional payer name, optional three-digit code, deterministic first-amount selection, the shared
separator rules, and an Unparsed outcome when no valid amount is found. A source's pattern set
MUST NOT weaken the Unparsed guarantee or introduce a source-specific announcement format.

A source id that the registry does not declare MUST produce no parse outcome and no announcement;
it is a programming error, not an input to tolerate by guessing.

Adding or changing one source's pattern set MUST NOT alter another source's results for the same
text.

#### Scenario: The source id selects the pattern set

- **GIVEN** the registry declares `yape` and `interbank`, each with a distinguishable pattern set
- **WHEN** the text `Juan Pérez te yapeó S/ 6,50. Código: 123` is parsed with source id `interbank`
- **THEN** only the Interbank pattern set is consulted and the result is attributed to `interbank`

#### Scenario: Both sources share the extraction contract

- **GIVEN** the text `Juan Pérez te yapeó S/ 6,50. Código: 123` under either the `yape` or the `interbank` pattern set
- **WHEN** the parser runs
- **THEN** both produce amount 650 céntimos, name `Juan Pérez`, and code `123`, with no source-specific difference in field shape

#### Scenario: An undeclared source id yields nothing

- **GIVEN** a source id `plin` that the registry does not declare
- **WHEN** the parser is invoked with that id
- **THEN** no outcome is produced, nothing is announced, and no history row is written

### Requirement: Interbank (Plin) is a second source whose notification format is unverified

No real Interbank or Plin notification has been captured. The Interbank pattern set SHALL therefore
be specified as an **assumption**, not as a description of observed wording:

- It MUST be calibrated against 5–10 real captured Interbank/Plin notifications before it is
  treated as correct.
- This specification MUST NOT assert an exact production string. Any Interbank text used in a
  scenario or fixture MUST be labelled a placeholder, and no placeholder may be presented as
  captured wording.
- Until calibration, the pattern set MUST be as tolerant as the Yape set: case-insensitive,
  accent-insensitive, whitespace-collapsed, and accepting the same currency-marker forms.
- Anything it cannot match MUST produce Unparsed and therefore the standard fallback alert. It MUST
  NOT guess an amount, a name, or a code from an unverified shape.
- Calibration MUST also confirm the exact Interbank package identifier; the package value and the
  pattern set are calibrated together (see `source-management`).

Interbank payments inherit the full announcement semantics: the same name-first sentence
construction, the same Spanish-word amount rendering, the same optional name and code handling, and
the same fallback phrase.

#### Scenario: A placeholder Interbank fixture satisfies the shared contract

- **GIVEN** the placeholder calibration fixture `Juan Pérez te transfirió S/ 6,50. Código: 123`, explicitly not captured Interbank wording
- **WHEN** the Interbank pattern set runs
- **THEN** the outcome is Parsed with amount 650 céntimos, name `Juan Pérez`, and code `123`

#### Scenario: Unrecognized Interbank wording is unparsed rather than guessed

- **GIVEN** the Interbank source is armed and the text `Se registró un movimiento en tu cuenta`
- **WHEN** the Interbank pattern set runs
- **THEN** the outcome is Unparsed, no amount is reported, and the fallback alert is triggered

#### Scenario: An Interbank amount without a currency marker is unparsed

- **GIVEN** the placeholder calibration fixture `Juan Pérez te transfirió 50.00`
- **WHEN** the Interbank pattern set runs
- **THEN** the outcome is Unparsed, because a bare number is not an amount

#### Scenario: Placeholder fixtures do not count as calibration

- **GIVEN** the Interbank fixture set, which contains placeholder entries and zero captured notifications
- **WHEN** the readiness of the Interbank source is reported
- **THEN** it is reported as uncalibrated, and the placeholders are not counted toward the 5–10 real fixtures required

### Requirement: Interbank amounts use the same integer céntimos rules

The Interbank pattern set MUST reuse the shared amount representation and separator resolution
rules, not a parallel implementation. `S/ 6,50` and `S/ 6.50` MUST both resolve to 650 céntimos
regardless of which source produced them.

#### Scenario: Separator variants behave identically across sources

- **GIVEN** the texts `Juan Pérez te yapeó S/ 6,50` and the placeholder `Juan Pérez te transfirió S/ 6.50`
- **WHEN** each is parsed by its own source's pattern set
- **THEN** both report 650 céntimos and neither uses floating-point arithmetic
