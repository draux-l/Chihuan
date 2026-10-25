# Project Context

## Purpose

NotisYape is a local-only Android app that listens to payment notifications, parses the
payment amount, payer name, and security code, and reads them aloud using the platform
`TextToSpeech` engine. No backend, no cloud sync, no LLM-based parsing, no ads.

Current scope:
- Sources (armed individually from a control panel): Yape (`com.bcp.innovacxion.yapeapp`) and
  Interbank's Plin function. Plin is not a standalone app; each participating bank emits its own
  notification, so extra banks are added one parser at a time.
- Parsing is regex / tolerant string matching implemented in Kotlin.
- Persistence is local-only Room: a payment history of what was announced. There is no export and
  nothing leaves the device.
- The Yape and Interbank notification formats are still unverified assumptions until real fixtures
  are captured.

## Tech Stack

Detected from `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`,
`gradle/libs.versions.toml`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`.

| Item | Value |
| --- | --- |
| Language | Kotlin 2.2.10 |
| Build system | Gradle 9.5.0 (wrapper), configuration cache enabled |
| Android Gradle Plugin | 9.3.3 |
| compileSdk | 36 with `minorApiLevel = 1` (reports as android-36.1) |
| minSdk | 24 (Android 7.0) |
| targetSdk | 36 |
| Java source/target | 11 (toolchain JVM: 21.0.12) |
| UI toolkit | Jetpack Compose, Compose BOM 2026.02.01, Material3 |
| View binding | Not used |
| Modules | Single `:app` module |
| Root project name | `notisYape` |

## Project Conventions

Conventions are the unmodified Android Studio Compose template defaults. No project-specific
conventions have been established yet.

- Kotlin style: `kotlin.code.style=official` (`gradle.properties`).
- Packages: `com.example.notisyape` at the root, UI theme split into `ui.theme`.
- 4-space indentation, trailing commas in multi-line argument lists.
- UI is Compose-only; `ComponentActivity` + `setContent` + `MaterialTheme` wrapper.
- Theme entry point is `NotisYapeTheme` in `ui/theme/Theme.kt`, with dynamic color on API 31+.
- Previews live next to the composable they preview (`@Preview` on `GreetingPreview`).
- Dependencies are declared in `gradle/libs.versions.toml` and consumed via `libs.*` accessors;
  no inline version strings inside `app/build.gradle.kts`.

Planned conventions that do not exist yet and must be created by the change that introduces them:
- Notification parsing must be pure Kotlin with no Android framework dependencies, so it stays
  unit-testable on the JVM.
- `NotificationListenerService` implementations must stay thin and delegate to testable code.

## Domain Context

Yape sends a notification when a payment is received. The app must react to that notification,
extract the amount, payer name, and security code, and speak the result. The security code exists
to let the user confirm that the notification matches a real payment shown in the Yape app, so the
spoken output must preserve it verbatim.

## Important Constraints

- `minSdk 24` is the floor. Any API used above 24 needs a `Build.VERSION.SDK_INT` guard or an
  `androidx` compat shim. `NotificationListenerService` itself is API 18+, so the listener is safe,
  but `TextToSpeech` language/voice APIs, `Notification.extras` accessors, and
  `POST_NOTIFICATIONS` (API 33+) all need guards.
- Notification access cannot be requested programmatically; only the system settings screen can
  grant it.
- All processing must be local. No network permission is added, and none should be.
- Java 11 target means no Java 17+ language APIs without raising `sourceCompatibility`.
- Build currently fails before this constraint is resolved: the resolved AndroidX dependency set
  (`core-ktx 1.19.0`, `core 1.19.0`, `lifecycle-runtime-compose 2.11.0`) declares a minimum
  compileSdk of 37. `compileSdk` must be raised to 37 (the android-37.1 platform is installed
  locally) or the dependency versions must be pinned lower.

## External Dependencies

Runtime: none beyond the platform. `TextToSpeech` is supplied by the system; no third-party TTS
SDK. Notification content comes from the Yape app's posted notifications and is only read through
`NotificationListenerService`.

Declared Gradle dependencies (complete list):
- `androidx.core:core-ktx:1.19.0`
- `androidx.lifecycle:lifecycle-runtime-ktx:2.11.0`
- `androidx.activity:activity-compose:1.13.0`
- `androidx.compose:compose-bom:2026.02.01` (ui, ui-graphics, ui-tooling, ui-tooling-preview,
  material3, ui-test-manifest, ui-test-junit4)
- `junit:junit:4.13.2` (test)
- `androidx.test.ext:junit:1.3.0`, `androidx.test.espresso:espresso-core:3.7.0` (androidTest)

Not present and not to be assumed today: Room, DataStore, Hilt/Dagger, KSP, Retrofit/OkHttp,
navigation-compose, kotlinx-coroutines-test, Robolectric, MockK, Turbine.

Planned additions for change `yape-voice-announcer` (not yet in the build): Room + KSP for the
local payment history. DataStore is still not planned; settings stay in SharedPreferences.
