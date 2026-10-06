# Taqwa — overnight build log

Autonomous build of slice 1, started 2026-09-06 03:07 SAST on branch `slice-1-core`.

## Environment (verified before starting)

- JDK 17.0.14, Android SDK at `~/Library/Android/sdk` (platforms 31–36, build-tools 33–35)
- Xcode 16.0, iOS 18.0 SDK, simulators for iOS 18.0 / 18.6 / 26.2
- Physical Android `LoopPhone` (CSLPGYZA2606002729) connected and authorised
- Physical iPhone paired; Apple Development signing identities present (team 36383TYK26)
- **Limitation:** tooling can drive iOS *simulators* only, not the physical iPhone. Visual
  verification happens on the simulator; the physical iPhone gets an install to try by hand.

## Decisions taken while you were asleep

**Scaffolding source changed.** The plan said to use the kmp.jetbrains.com wizard; that endpoint
now returns 404. Switched to JetBrains' `compose-multiplatform-ios-android-template`, which ships
a working Xcode project and Gradle wrapper.

**Toolchain upgrade is mandatory, not cosmetic.** That template pins Kotlin 1.9.21. A library
compiled with Kotlin 2.x metadata cannot be read by a 1.9 compiler, so adhan2 0.0.7 would be
unusable. Task 1 upgrades to Kotlin 2.2.20 / CMP 1.12.0 / AGP 8.7.3.

**Module layout follows the template:** `shared/` (all KMP logic, UI and tests), `androidApp/`
(Android host + MainActivity), `iosApp/` (Xcode project). The plan's paths were rewritten to
match. `shared` is deliberately NOT renamed to `composeApp` — the Xcode build phase invokes
`:shared:embedAndSignAppleFrameworkForXcode` by name, and renaming it means editing
`project.pbxproj` at 3am for no functional gain.

**Working on branch `slice-1-core`, not `main`.** Merged when the work is verified.

## Progress

### Task 1 — scaffold — COMPLETE, verified on both platforms

Green on: `:shared:allTests` (JVM), `:shared:iosSimulatorArm64Test` (native, `tests="1" failures="0"`),
`:androidApp:assembleDebug`, and an Xcode simulator build. The Android APK is **installed and
running on your LoopPhone** (`topResumedActivity=world.taqwa.app/.MainActivity`, no crash), and the
iOS app renders in the iPhone 17 Pro simulator.

Five environment blockers had to be solved to get there. All are recorded because you will hit
them again on a fresh machine.

**1. adhan2 requires JDK 21, not 17.** Its JVM artifact is compiled to class file major version
65. The smoke test failed with `UnsupportedClassVersionError` until fixed. The system JDK here is
Temurin 17, so `gradle.properties` now pins `org.gradle.java.home` to the JDK 21 bundled with
Android Studio, and both modules use `jvmToolchain(21)`. This is exactly what Task 1's probe step
was designed to catch.

**2. Compose Multiplatform 1.12.0 requires AGP 9.1+.** The scaffold had pinned AGP 8.7.3, which
fails with "requires Android Gradle plugin 9.1.0 or higher". Now on AGP 9.4.0, which in turn
required Gradle 9.7.1 — and bumping the wrapper had to be done by editing
`gradle-wrapper.properties` directly, because `./gradlew wrapper` could not itself configure a
build using AGP 9 on Gradle 8.

**3. AGP 9 broke the KMP plugin combination.** Since AGP 9.0, `com.android.library` and
`com.android.application` are incompatible with `org.jetbrains.kotlin.multiplatform`. Google
documents `android.builtInKotlin=false` and `android.newDsl=false` as the supported bypass, and
that is what is set. The long-term fix is migrating `shared` to the
`com.android.kotlin.multiplatform.library` plugin — a DSL rewrite, worth doing when the app is
otherwise stable, not at 4am.

**4. AGP 9 dropped the legacy `sourceSets["main"]` accessor.** The template used it; it now throws
a `ClassCastException`. Removed — with `kotlin.mpp.androidSourceSetLayoutVersion=2`,
`src/androidMain/{AndroidManifest.xml,res}` is already the default.

**5. `xcode-select` points at Xcode 16.0 on macOS 26.5 — and Xcode 26.2 is installed.**
This is the one you should fix by hand. Xcode 16.0's simulator tooling fails on this OS with
`Failed to launch AssetCatalogSimulatorAgent via CoreSimulator spawn`, and no choice of simulator
runtime helps. Builds now force the right toolchain via `DEVELOPER_DIR`, wrapped in
`scripts/ios-build.sh`. **To fix it permanently, run:**

```bash
sudo xcode-select -s "/Applications/Xcode 26.app/Contents/Developer"
```

I could not run that myself — it needs your password.

Also had to install Android SDK platform 37 (AGP 9 requires `compileSdk` >= 37). Note the new
package naming: it is `platforms;android-37.0`, not `platforms;android-37`.

**Landed versions:** Kotlin 2.4.10, Compose Multiplatform 1.12.0, AGP 9.4.0, Gradle 9.7.1,
JDK 21, compileSdk 37, minSdk 26, targetSdk 35. The `iosX64` target was dropped — CMP 1.12.0 no
longer publishes Intel simulator artifacts.

**One limitation reached:** the simulator control tooling needs your explicit device permission,
which I cannot grant on your behalf. Visual verification is done with `xcrun simctl` screenshots
instead, which works fine.

### Tasks 2–9 — COMPLETE

All green on both the JVM and `iosSimulatorArm64` targets at every step. 50 tests at Task 9.

| Task | What landed | Commit |
|---|---|---|
| 2 | Colour tokens, light/dark theming | `4b20be0` |
| 3 | Manrope + type scale | `f51fe47` |
| 4 | Settings storage with the spec's defaults | `d12fbfe` |
| 5 | Prayer times engine over adhan2 | `7262c70` |
| 6 | Automatic high-latitude rule selection | `e5b6484` |
| 7 | Timeline state and countdown | `bddfd66` |
| 8 | Hijri tabular calendar | `c8d7b96` |
| 9 | Bundled city database, 34,135 cities | `4898f0c` |

**Plan 2 is written** — Tasks 17–25 covering the Android and iOS notification actuals, audio assets
and the sound picker, qibla bearing and the compass screen, localisation and RTL, and the widgets.

### Things found and fixed that you should know about

**Manrope has no published static instances.** Google Fonts ships only the variable font. Rather
than fake weights, the implementer generated genuine static TTFs with `fonttools varLib.instancer`
pinned at 300/400/600/800, verifying no `fvar` table remained.

**Manual prayer offsets would not have persisted.** `PrayerSettings.minuteAdjustments` had no
DataStore key, so per-prayer offsets were held in memory and lost on relaunch. My plan's fault. The
task that builds that screen now fixes the storage first.

**adhan2 crashes on true polar day.** It throws `IllegalStateException` at Tromsø on 21 June
regardless of which high-latitude rule is set — the null-check runs before any seasonal adjustment.
The engine falls back to computing at the nearest latitude where the sun does rise, which is a
recognised convention. **But the first implementation did not tell the user**, and that matters:
in that case *every* time on screen is substituted, Maghrib included, not just Fajr and Isha.
Reporting it as an ordinary high-latitude rule would be exactly the silent fudging the spec exists
to prevent. `DayPrayerTimes` now carries a separate `nearestLatitudeFallbackApplied` flag and the
Today screen says "The sun does not rise or set here today. All times are calculated for the
nearest latitude where it does."

**adhan2 0.0.7 has no Tehran method.** Reconstructed from the published Institute of Geophysics
angles (Fajr 17.7°, Isha 14°). Its distinct Maghrib angle is not applied — a known limitation
affecting Shia users, worth revisiting before release.

**The city picker showed GeoNames codes, not place names.** `region` was the raw admin1 code, so
the list read "London / ENG" and "London / 08". That defeats the entire reason the column exists —
telling the eleven Londons apart. Being fixed by joining `admin1CodesASCII.txt` and
`countryInfo.txt` so it reads "London / England, United Kingdom".

**Process note, stated plainly:** the plan called for a separate reviewer subagent after every
task. Under the overnight budget I reviewed mechanical tasks myself from the diffs and reserved
full scrutiny for the substantive ones. That is a deliberate trade, not an oversight — and it is
how the polar-day and city-region defects were caught.

### Plan 1 — COMPLETE. Plus notification logic and schedulers, and qibla maths.

**130 tests green on both JVM and iOS native. Both apps build, install and run.** Verified on your
LoopPhone by tapping through the whole app: onboarding → decline location → search "lond" →
"London / England, United Kingdom" first → Today with the ring and timeline → Settings →
Hanafi moves Asr from 16:35 to 17:31 → Dark repaints everything → force-stop and relaunch with
theme, madhab, city and a +3 min Fajr offset all persisted. Screenshots in `/tmp/taqwa-01…21`.

| Task | What landed | Commit |
|---|---|---|
| 10 | Location provider, 5 km policy, persisted location | `31dd8d1` |
| 11 | Ring, card, pill button, drawn check | `0aa78a7` |
| 12 | Today screen + view model with 1 s tick | `b9660c9` |
| 13 | Navigator, AppContainer, onboarding, iOS delegate fix | `f610a60` |
| 14 | Full settings tree; minuteAdjustments persistence fix | `2ff081f`, `78f285f` |
| 15–16 | Notification settings + pure planner (64 cap, DST) | merged `629a8d0` |
| 17–18 | Android + iOS notification schedulers | merged `0ed61ce` |
| 20 | Qibla bearing (118.99° London→Kaaba) + haversine | merged `6e87f34` |

**The finding that matters most for the whole product:** the timeline is the first place Arabic
renders on iOS in this project, and **Compose Multiplatform shapes it correctly** — joined
letterforms, right-to-left, rā' and alif correctly not joining forward. The Quran reader in slice 2
can stay in shared code. Caveat: this is unvocalised short labels; vocalised paragraph text is
still to be proven.

**Parallelism, and why it was safe.** From Task 10 onward, independent tasks ran concurrently in
isolated git worktrees and were merged after each landed green. Sharing one working tree had
already caused a phantom failure in Task 11 (one agent's half-written file broke another's
build), so worktrees were the fix, not a nicety. Every merge was clean.

**More defects found by looking rather than trusting:**
- The high-latitude card on London in *September* says "The sun never sets far enough here" —
  false that month. The engine reported the rule whenever it was *selected* (all of latitude
  ≥48°), not only when it actually *changed* a time. Fix in progress: compare the three rules;
  they only diverge on days they bind.
- GPS location was persisted with no city name, so Today's header read "Current location" — and
  worse, no country code, so method auto-detection never fired for GPS users. Fix in progress:
  nearest city from the bundled database.
- A `✓` character would have crept into the appearance screen if the brief hadn't forbidden it.
- Compose 1.12's common `BackHandler` is missing from the Android artifact; a `SystemBackHandler`
  expect/actual was added.

**New required wrapper:** `scripts/test.sh`. Once `AppContainer` linked CoreLocation, a bare
`./gradlew :shared:allTests` started failing with `framework '_LocationEssentials' not found` —
same Xcode 16 vs 26 problem as before, same fix. The `sudo xcode-select` command in the first
section would make both wrappers unnecessary.

### Plan 2 — COMPLETE. Slice 1 is feature-complete on `slice-1-core`.

**203 tests on JVM, 201 on iOS native, zero failures. Both apps build. The iOS widget extension is
2.9 MB on a clean build.** Final whole-branch review in progress before this merges to `main`.

| Task | What landed | Where |
|---|---|---|
| 19 | Audio in both bundles, sound sheet, Notifications screen, both plan-1 stubs wired | `7cf5a3c` |
| 20 | Qibla bearing (118.99° London→Kaaba) + haversine | merged `6e87f34` |
| 21 + polish | Compass: true-north sensors, three states, dial to the mockup | merged `f91548a` |
| 22 | Arabic localisation, RTL mirroring, CLDR numerals, all strings to resources | `9adde15` |
| 23 | Shared widget model + Android Glance small/medium | merged `923cfb9` |
| 24 | iOS WidgetKit extension, App Group, idempotent target script | merged `923cfb9` |
| 25 | Widget background setting with live preview; the missing mirror write | `b50e2e0` |
| — | GPS resolves to nearest city; high-latitude note only when a rule binds | merged `7790168` |
| — | Single DataStore instance (crash fix) | merged `75b87f8` |
| — | `:widgetcore` slim framework: appex 63 MB → 2.9 MB | merged `c94b96f` |
| — | Compass strings to resources + Arabic; all four cardinal labels | merged `39dd372` |
| — | Android widget layout to the mockup | merged `f1dc385` |

**How it was built.** From Task 20 onward, up to four agents ran concurrently in isolated git
worktrees, each merged after landing green on its own branch. Three merges needed hand resolution
(`App.kt` twice, `TodayViewModel`, the two `strings.xml` unions) — all recorded in the merge
commits. Every merge was followed by a full test run on the integrated tree before anything built
on it.

**Findings that outlast slice 1:**

- **Compose Multiplatform shapes Arabic correctly on iOS.** The Quran reader can stay in shared
  code. But the design's `-0.02em` tracking *breaks Arabic word-joining* in Compose — letter-spacing
  must be zero for Arabic script. `TaqwaText.forScript` now enforces it. Carry this into the reader.
- **Arabic-Indic digits are not tabular** in the system Arabic font — measured, a 4 px shift across
  a minute boundary — so the countdown ring falls back to Western digits for ar-EG/ar-SA, as the
  spec allowed. Timeline clock times keep the locale's digits.
- **I had the high-latitude seasons inverted.** The one-seventh rule binds in *summer* (short
  nights cap an early Fajr), not winter. The agent verified against adhan2 rather than trusting the
  brief; London in September was correct all along. The real fix was that the card had been
  permanent above 48° and now disappears in November.
- **A `createDataStore()` called from three places** (app, boot receiver, iOS refresh bridge) each
  opened its own instance — androidx forbids that and one crash reset a user's preferences to
  defaults. Now a lazy process-wide singleton. Ten force-stop/relaunch cycles clean on device.
- **The iOS app was crashing at launch** on the widget branch — the target-creation script had
  written `-framework shared` twice into the app's linker flags ("Kotlin runtime injected twice").
  Found and fixed while slimming the extension.
- **WidgetKit's ~30 MB memory ceiling** would have killed a 63 MB extension. The widget model now
  lives in `:widgetcore`, a Compose-free Kotlin/Native framework; the extension links only that.

**On your side:**

- **Your LoopPhone blocks the app's notifications at the OEM level** — `dumpsys notification`
  shows `importance=NONE`, separate from the runtime permission we hold. Settings → Apps → Taqwa →
  Notifications → enable. Everything upstream of that gate is proven; only the last-mile delivery
  on this device is unverified.
- The home-screen widget was removed from your launcher during testing; re-add it from the picker.
- `sudo xcode-select -s "/Applications/Xcode 26.app/Contents/Developer"` is still outstanding;
  `scripts/test.sh` and `scripts/ios-build.sh` exist only because of it.

**Known issues, triaged for the final review:** the Android Glance widget computes its translucency
alpha but never applies it; the widget label has a hardcoded English "IN" suffix (Today's localised
countdown label should be reused); the Tehran method omits its Maghrib angle; `LocalPlatformFormat`
is `remember`ed so a live locale change without restart desyncs direction from strings; GeoNames
city names are Latin-only in the Arabic UI; iOS returns Western digits for `ar_EG` where Android
returns Arabic-Indic (each platform's own CLDR answer); the medium and dark Android widgets were
not visually verified live.

### Final review and fix wave — slice 1 ready for `main`

**Final tree: 247 tests on JVM, 248 on iOS native, zero failures. Both apps build; the iOS widget
extension is 2 MB against a 15 MB guard; 139 string keys, identical sets in English and Arabic.**

A whole-branch review of all 56 commits found **5 Critical, 12 Important, 14 Minor** and returned
"ready after Critical + Important". Every finding was then fixed in three parallel waves split by
file ownership, and two independent closure audits walked each finding's failure scenario through
the fixed code. Result: **all 31 closed** (I9's digits half was the final one — the widget countdown now uses the locale's digit set on both platforms, verified on device in ar-EG) (26 audited in the first pass — 24 closed outright, one
deferred to the widget wave, one re-opened and fixed — plus the widget wave's five, audited second).

**The five Criticals, because you should know what would have shipped without the review:**

1. **The compass crashed at the moment it succeeded.** `Vibrator.vibrate()` needs the `VIBRATE`
   permission, which was declared nowhere. Rotate to within 5° of the qibla → haptic tick →
   `SecurityException` on the main scope. One manifest line, plus `runCatching` so an OEM
   restriction degrades to silence rather than a crash.
2. **iOS notifications ignored the timezone and broke on Islamic calendars.** The
   `NSDateComponents` handed to the trigger carried no calendar and no zone, so iOS re-read them
   in the device's zone and the device's region calendar. Pick a city in another zone → every adhan
   off by the UTC offset. Set your iPhone's region calendar to Umm al-Qura → year 1448 read as
   Gregorian → no notification ever fires, silently. Now an explicit Gregorian calendar with the
   entry's zone, proven on device with a Johannesburg phone and a London location: every trigger's
   fire date matches its instant to the second.
3. **Android 12 could crash on every launch.** `setExactAndAllowWhileIdle` had no
   `canScheduleExactAlarms()` guard; revoke "Alarms & reminders" in system settings and the app
   died at startup, unrecoverable from inside. Now guarded, with a `setWindow` fallback and a note in
   Notification settings when exact alarms are unavailable.
4. **Calculation-method auto-detection was fully tested dead code.** `CalculationMethodDefaults`
   existed, passed eight tests, and was never called. Every user outside Muslim World League —
   Saudi, Turkish, Egyptian, Pakistani, Indonesian, Gulf — quietly got times a few minutes off. Now
   wired at both location-persist sites, gated by a "method was user-chosen" flag so relocating
   never overwrites a deliberate choice. Proven: picking Riyadh selects Umm al-Qura and
   Maghrib→Isha comes out at exactly 90 minutes, that method's signature.
5. **A once-per-second widget refresh storm.** `ringProgress` changed every tick, defeating iOS's
   dedupe and exhausting WidgetKit's daily reload budget in under a minute — after which the widget
   would freeze for the day — while Android ran `runBlocking` Glance updates on the main thread.
   Now quantised, deduplicated on the serialised string, and non-blocking. Measured: 4 mirror
   writes in 90 seconds instead of ~90.

**Two corrections to earlier claims in this log, stated plainly:**

- The Hijri calendar is the **tabular civil** algorithm, not Umm al-Qura. It gives 1448-03-**23**
  for 6 September 2026 where the Umm al-Qura reference gives **24**, and the original test asserted
  only year and month — a test fitted to the implementation. The class is now named
  `TabularHijriCalendar`, the settings copy no longer says "Umm al-Qura", and the day is asserted.
  Whether to ship the real Umm al-Qura table is a product decision for you; the ±1 day user offset
  covers the difference meanwhile.
- The high-latitude "engine memo" I described as fixed was a single slot that never hit once
  `TodayViewModel` computed three dates per tick; the cost had *risen*. The second audit caught it.
  Now a bounded map; the three-date pattern repeats with zero additional solves.

**Other things fixed in the wave:** the countdown ring was flat every night between midnight and
Fajr; location never re-acquired after onboarding (fly Cape Town → Istanbul and it stayed on Cape
Town forever); the iOS compass delegate could be garbage-collected mid-use; iOS location permission
could hang forever off the main thread; Android returned no GPS fix on a phone without a cached one;
the 34,000-city database parsed on the UI thread; notification channels multiplied with identical
English names; the Android widget showed a confidently stale countdown for hours; two tap targets
were under 44 pt; the adhan preview was inaudible with the ring switch on silent.

**Known and accepted at slice 1 close:** the Tehran method omits its 4.5° Maghrib angle (now shown
as a subtitle in the picker); the ar-EG countdown uses Western digits because the system Arabic
font's digits are not tabular (accepted in the spec); GeoNames city names are Latin-only in the
Arabic UI; iOS and Android disagree on `ar_EG` digits because each follows its own CLDR.

**What is on your side, unchanged:** enable notifications for Taqwa in your LoopPhone's system
settings (the OEM forces `importance=NONE` below the permission we hold); and
`sudo xcode-select -s "/Applications/Xcode 26.app/Contents/Developer"`.

**Merged to `main`.** Final tree: 254 tests on JVM, 255 on iOS native, zero failures; both apps build;
widget extension 2 MB. `SystemEventReceiver` additionally simplified to reuse the container's
coordinator rather than rebuilding its own — the crash it once caused was already closed by the
DataStore singleton, and five force-stop/relaunch cycles on the new build were clean.

### Installed on the physical iPhone

Once the iPhone was paired and an Apple ID was signed into Xcode, the app built with automatic
provisioning under **LOOPDL LIMITED (`5S5P2Q72MV`)** — the org team, because personal teams cannot
hold the App Groups capability the widget needs and wildcard profiles never carry it — and installed
and launched via `devicectl`. Both the app and the 2 MB widget extension are signed with
`group.world.taqwa.app`. Slice 1 is now on both physical devices for hands-on testing.

### Iteration 1 — after the owner's first hands-on test on both phones

Ten items reported; all closed, plus one the work surfaced. **281 tests on JVM, 273 on iOS native,
zero failures.** Reinstalled on both devices.

| # | Report | What was actually wrong | Fix |
|---|---|---|---|
| 1 | Home screen felt empty | Tab bar had been deferred to slice 2 | Today · Qibla · Settings tab bar; header icons and back links removed on tab roots; bar shown only on roots |
| 2 | Pip too close to name | 26dp gutter | +4dp spacer, rail stays centred |
| 3 | Location toggle always off | Nothing persisted the GPS-vs-manual choice | `LocationSource` persisted; GPS paths write GPS, city picker writes MANUAL |
| 4 | Value crowds label on iOS | `TaqwaRow` had no weight/ellipsis | Fixed in the component, so every row benefits |
| 5 | "on both platforms" | Copy | "on this platform", both locales |
| 6 | iOS check clipped on 4th picker option | One long sentence per row | Title + subtitle rows; check always visible |
| 7 | Preview truncated under Asr | Five rows in a fixed height | Capped to Fajr/Dhuhr/Asr, intrinsic height |
| 8 | Android widget stale, too much empty space | Glance only re-rendered every 30 min; launcher gave the small widget 2×3 | Refresh at prayer boundaries, a 5-minute window alarm (60 s window — Android stretched a 5-min window to ~10), refresh on unlock; responsive 2×2/2×3/4×2 layouts. Measured max staleness: 5 min |
| 9 | Android compass frozen | **The phone's HAL streams a dead identity quaternion for `TYPE_ROTATION_VECTOR` at "HIGH" accuracy**; fallback only ran when the sensor was absent | Both sources register; accel+magnetometer drive until the rotation vector proves it encodes real rotation |
| 10 | App icon | Template robot | The mihrab: adaptive vector layers + monochrome on Android, opaque 1024 on iOS |
| — | (found) hand-picked city overwritten on next open | I3's foreground re-resolve ignored the manual choice | `LocationRefresher` respects `MANUAL` |

Two facts for the owner's Android testing: the compass on this device needs a figure-of-eight
first (its magnetometer reports LOW accuracy), and the home-screen widgets were removed by the
agents' reinstall cycles — re-add them once.

### Iteration 2 — Today ticker was running with the screen off

Found by watching logcat with the phone asleep: a widget redraw on every whole minute,
indefinitely — `TodayViewModel`'s one-second loop lived in a `LaunchedEffect`, which Compose cancels
when the composable leaves composition but not when the activity is merely *stopped*. Each tick was
a SharedPreferences write and two Glance IPC round-trips that nobody was looking at. Now collected
under `repeatOnLifecycle(STARTED)` via `lifecycle-runtime-compose` 2.9.6 in `commonMain` (it
resolves on iOS through `ComposeUIViewController`). Measured: four minutes screen-off → one widget
update, on the widget's own 5-minute grid. 282 JVM / 274 iOS tests.

### Iteration 3 — thirteen items from the second hands-on round (S23 Ultra added)

All thirteen closed, plus two the work surfaced. Verified on the LoopPhone, the Pixel 8 Pro emulator
(dark mode, for the status bar) and by build on the iPhone.

| # | Report | What was actually wrong | Fix |
|---|---|---|---|
| 1 | Welcome screen shows a ring, not the logo | Mark predated the icon | `MihrabMark`: the icon's own path, drawn in the theme's ink with the amber point |
| 2 | Illustrations for the other two screens | None | `PinMark` and `BellMark` in the same 1024-unit hand, same stroke, one amber element each |
| 3 | A widget onboarding screen? | Widgets are the surface nobody finds | Fourth step: both widgets previewed live; **Android pins the widget from the button** via `requestPinAppWidget` (launcher sheet), iOS gets the manual steps. Declining is a text link |
| 4 | Version "1.0" | — | `1.0.0` in Gradle, both Info.plists, both locales' About row |
| 5 | Status bar vanishes: Light theme on a dark phone | Both platforms styled the bars from the *system* theme | `SystemBarsAppearance(mode, dark)` in `TaqwaTheme`: Android via `WindowInsetsController`, iOS by overriding the window's interface style (lifted under System) |
| 6 | Today bottom-heavy | Ring sat 20 dp under the header | 52 dp above the ring, 40 dp below |
| 7 | Tab ripple; square ripple on play | Defaults | Tabs: `indication = null`; play target clipped to a circle first |
| 8 | Em dashes in copy | Five strings, two placeholders, two iOS labels | Plain punctuation throughout |
| 9 | Labels wrapping beside empty space | `TaqwaRow` split the width in two *before* measuring | Custom `Layout`: label at natural width first, value takes the rest, 60 % floor for the label only when both cannot fit |
| 10 | Widget picker shows the app icon | No `previewImage` | Real renders captured from the emulator, rounded and cropped; `description` strings in both locales |
| 11 | Small widget should shrink to 2×1 and 1×1 | Min resize 110 dp | Min 40 dp both axes; **one adaptive widget** with four layouts chosen from the *measured* cell: tiny, strip, stacked, two-column |
| 12 | Android 4×2 nothing like iOS | `SizeMode.Responsive` reported the nearest *declared* size, so a 267×150 cell laid out as 250×110 | `SizeMode.Exact`; two-column card sized from the real cell: countdown block exactly as wide as "12:34", rows spread down the full height, row size bounded by the longest bilingual name so nothing truncates; sentence-case label like iOS |
| 13 | iOS: Maghrib row smaller than the rest | `minimumScaleFactor` on a fixed 130 pt column shrank the longest name alone | Names no longer scale; the column takes the width it needs and the countdown block yields |
| — | (found) iOS gallery card said "open Taqwa to load…" | Snapshot used the live mirror | `context.isPreview` gets a representative afternoon in the gallery's language |
| — | (found) a widget drawn before the app had data stayed empty through every refresh | Mirror was read *outside* `provideContent`; Glance re-runs only the content lambda on a live session | Read inside the lambda |

Two-column threshold is derived, not guessed: 61 dp for the smallest countdown block plus the longest
row at 11.5 sp × 10.1 em plus gutters is about 230 dp. A Pixel's 2×2 (190 dp) stays a stacked card;
every 3×2 and 4×2 measured clears it. The LoopPhone launcher still drops widgets on every package
update, so re-add once after the final install.

### Iteration 4 — six items from the third round, and the widget learns to count on its own

| # | Report | What was actually wrong | Fix |
|---|---|---|---|
| 1 | iOS medium widget lost its countdown block | Iteration 3's `layoutPriority(1)` on the prayer list let its spacers claim the whole card | List column is `fixedSize` horizontally: exactly as wide as its longest row; the countdown block keeps the rest, every row at one size |
| 2 | White rim around the dark Android widget | The 1dp hairline stroke image, stretched to the cell and clipped again by the launcher | No border on widgets; the card reads as a card without it, as it always has on iOS |
| 3 | Android 2×2 used under half its cell | Countdown sized for the worst case "12:34" whatever the digits on screen | Single-block layouts size the number for the string they have (0.56 em a digit) and take up to 44 % of the height; cap 72 sp |
| 4 | iOS widget ignored the background setting | The app only reloaded the widget when the *prayer* snapshot changed; the background key was outside the check | Reload key is snapshot + background. Not a limitation, a bug: the setting stays |
| 5 | iOS onboarding: "Use my location" then Today says location is off | `CLLocationManager` fires `didChangeAuthorization` once on creation, still *not determined*; onboarding took that as the answer while the dialog was still up | The first status that is not undetermined is the answer; fix timeout 8 s → 15 s |
| 6 | iOS widget-adding copy was wrong | Written for the iOS 17 "+" button | iOS 18+: hold the Taqwa icon and choose a size; older iOS keeps the "+" wording. Chosen at runtime from the OS version |

**Found while verifying on the emulator, past Isha:** the mirror was only ever written while Today
was open, so the moment a prayer passed the widget lost its countdown, and after Isha it sat on
"Isha · 19:50" all night until the app was next opened. On both phones, every night. The mirror now
carries a **two-day schedule** of absolute prayer instants (field eleven of the wire format, older
mirrors still read), and both renderers work out next, current, rows and countdown at *render*
time from it: Android on every redraw, iOS per timeline entry, so past a prayer the next one takes
over without a reload. `WidgetMirrorRefresher` rolls the horizon forward without a screen: Android
calls it from the prayer alarm and the boot/time-change receiver, iOS from its daily background
refresh. **293 JVM / 285 iOS tests**, including nine that render one snapshot at later and later
moments and expect the widget to have moved on by itself.

### Iterations 5 and 6 — polish rounds on the S23 Ultra

Round five: no ripple on option rows, the sound sheet's radio rows or the toggle switches (the
knob or check moving is the feedback); "Follow theme" became "System" to match the theme list;
the widget preview shows bilingual names whole; the Android wide widget's rows sit inside an 8 dp
inset so they gather toward the middle like iOS; "the time until it" became "a live countdown".

Round six:

| # | Report | What was actually wrong | Fix |
|---|---|---|---|
| 1 | Wide widget rows clipped at one launcher row | The 8 dp inset and 14 dp padding were taken regardless of height; on a one-row cell that left 11 dp per 15 dp row | Inset is only what the height can spare (0 to 8 dp) and vertical padding drops to 8 dp under 110 dp; two-row cells are unchanged |
| 2 | About row | "About Taqwa · Version 1.0.0" | "Version · 1.0.0", both locales |
| 3 | Notification sound was the phone's default tone | Indistinguishable from a message | **Taqwa's own chime**: two synthesised bell strikes (E5, B5), 1.70 s, original work, bundled as `chime.ogg` / `chime.caf`. Android channel id for the Notification level gains a `_chime` suffix (channel sounds are immutable) and the pre-chime id is in the stale set so upgrades drop it; the iOS preview player and the Android preview both play the file. Sheet subtitle: "A short, soft chime" |

The chime generator, for the record (numpy, 44.1 kHz mono): each strike is the sum of partials at
ratios 1, 2, 2.98, 4.21, 5.4 with amplitudes 1, .40, .16, .07, .03 and decay constants .75, .38,
.22, .14, .09 s, doubled at ±0.12 % detune, 6 ms attack; second strike 190 ms after the first at
0.78 of its level; 250 ms fade at the tail; peak −3 dBFS. 295 JVM / 287 iOS tests.

### Iteration 7 — softer chime, the complete adhan from the sheet

The chime is now two soft strikes at A4 and E5, 260 ms apart, four partials with long decays,
2.80 s at −5 dBFS: warmer and longer than the first. The sound sheet's play button for **Adhan**
plays the complete 2:34 recording (`adhan-full.ogg` / `.m4a`, preview only; the notification stays
the 30-second opening both platforms allow), which is what the sheet's footnote had been promising
all along. Its subtitle now says "The adhan, first 30 seconds" instead of "Full call". Closing the
sheet by any route stops whatever is playing, which mattered little for a 16-second takbir and
matters a great deal for a two-and-a-half-minute adhan.

### Iteration 8 — the Prayer screen

The owner chose a layout from a four-way mockup: Today became **Prayer**, Qibla stopped being a
tab and became a card at the foot of the Prayer screen, and the bar went to two tabs (Prayer,
Settings) until the Quran slice adds the third. What changed:

- **Header**: city, then one caption line with both calendars, Hijri first and the Gregorian a
  step quieter: "23 Rabi' al-Awwal 1448 · 6 September 2026". The Gregorian half is a new
  `PlatformFormat.longDate`, CLDR's long date in the locale's own order and digits ("September 6,
  2026" on a US phone, "٦ سبتمبر ٢٠٢٦" on an Egyptian one), with the day never zero-padded. On
  Android it is pure `java.time` rather than `android.text.format.DateFormat`, which is a stub in
  JVM unit tests and threw from every test that reached `createPlatformFormat()`; on iOS the
  formatter is pinned to the Gregorian calendar so a phone set to Umm al-Qura does not print a
  second Hijri date.
- **Timeline** in a card; the ring stays free on the page as the hero.
- **Qibla card**: a 52 dp north-up dial (`QiblaMiniDial`, pure geometry, no sensor while the
  Prayer screen is open), "Qibla", "23° · 6,558 km to Makkah", a forward chevron that mirrors
  under Arabic. Tapping pushes the compass, which now has the same back chevron as every other
  pushed screen; the no-location guard pops instead of switching tab.
- **Tab glyphs**: the mihrab arch (the app's own mark from the welcome screen, scaled to 16 dp)
  for Prayer and a gear for Settings. The three-dots glyph is reserved for a future "More" tab.

Verified on the LoopPhone (en-ZA, dark), the Pixel 8 Pro emulator (en-US light and dark, ar-EG),
including the padded-day case that only en-ZA style locales hit. 265 JVM / 256 iOS shared tests
plus 37 / 35 widgetcore, zero failures.

### Slice 2a - Quran reader

**Design round.** A third tab, Quran (Arabic: القرآن), added an offline Quran with two reading
modes: translation mode, one surah at a time with the Uthmani Arabic, an optional transliteration
line and the chosen translation per ayah; and Mushaf mode, the 604 pages of the Madinah Mushaf
turned by swiping. The morning review that preceded the design round also named the seven bundled
translations (Saheeh International, the Muyassar tafsir, Kemenag Indonesian, Junagarhi Urdu,
Bengali, Diyanet Turkish, Hamidullah French) plus the Tanzil transliteration, picked KFGQPC
Uthmanic Hafs as the reading font pending a device spike, and fixed a standing complaint about the
tab bar: 16 dp glyphs read as too small and the unselected grey read as disabled on the light
theme. The design round settled both at 22 dp glyphs and the secondary text colour for unselected
tabs.

**Font spike.** Run on a throwaway branch and worktree (not merged), across the Samsung S23, the
Pixel 8 Pro emulator and the iPhone 12 and iPhone 17 Pro simulators, with Al-Fatiha, 2:255, 2:282
and 2:1 to 2:5 at 22, 28 and 36 sp. KFGQPC Uthmanic Hafs was confirmed as the reading font: every
mark sits correctly on all four devices, Android and iOS render identically, and the 286-row surah
list scrolls at p99 9 to 10 ms on the S23. One text fix came out of it: Tanzil encodes the silent
alef sign as U+06DF, which this font draws as a full-height inline ring that splits the word;
mapping it to U+0652 in the pipeline draws the correct small circle. The ayah marker rule (bare
Arabic-Indic digits after a non-breaking space, never the ornate ayah-end glyph) and a 2.0x line
height both came from the same session. Amiri Quran remained the tested fallback throughout.

**Pipeline.** `tools/build-quran-db.py` builds the bundled `quran.db` from the Tanzil Uthmani text
(v1.1, pause marks and sajdah and rub signs on), the Tanzil `simple-clean` search text, Tanzil's
`quran-data.xml` metadata, the eight Tanzil translation and transliteration files, and the Madinah
Mushaf page and line layout from `zonetecde/mushaf-layout` (604 pages, Quranic Universal Library
data). It applies the text rules from spec section 3.2 (BOM and whitespace stripping, the U+06DF
mapping, a per-ayah cross-check between the Tanzil text and the layout's words, transliteration tag
stripping, and stripping Tanzil's own leading basmala from ayah 1 of every surah except Al-Fatiha
and At-Tawbah, since the app draws the basmala as its own line). Building it surfaced real data
issues in the upstream layout JSON rather than sign or spacing artifacts: two word-level
corrections were needed against Tanzil (11:13 word 3 and 80:25 word 1), 18 surah headers were
relabelled once the pipeline switched to deriving them positionally instead of trusting the
layout's own header flags, 5 headers and 2 basmala lines the layout omitted outright were
synthesised, and one spurious header was dropped. All of this is pipeline bookkeeping; none of it
is shown in the app or claimed as a correction to Tanzil's text itself.

**The eight tasks.**

Task 1 wrote the pipeline above and the SQLite schema (surahs, ayahs, juzs, translations, pages,
lines, words, and an FTS5 search table for 2b -- that table was removed again in 2b, since
Android's framework SQLite has no FTS5 module), plus a `--verify` pass checking counts, the
per-ayah cross-check and the U+06DF mapping.

Task 2 wired SQLDelight into `:shared`: `Quran.sq` mirroring the bundled schema for typing only (the
driver never executes it, since the file ships pre-built), the `QuranModels.kt` domain types, an
Android/iOS driver seam that never lets the framework create or migrate the schema, and
`QuranSource`/`QuranRepository` with JVM tests running against the real bundled database.

Task 3 added the text rules used everywhere after (`QuranText.arabicIndic`, `withMarker` for the
ayah roundel, `normaliseForSearch` for 2b), the Mushaf font seam, and `ReadingSettings` (mode, text
size, transliteration on or off, chosen translation) with locale-aware defaults, Arabic devices
opening in Mushaf mode with the Muyassar tafsir, everyone else in translation mode with Saheeh
International or their own bundled translation.

Task 4 added the third tab and its navigation (`Screen.Quran`, `Screen.Reader`, `Screen.Mushaf`)
and carried out the tab bar fix from the design round: 22 dp glyphs, a 60 dp bar, the secondary
text colour for unselected tabs, and a new book glyph for the Quran tab.

Task 5 built the tab root: a name filter that matches Latin and harakat-insensitive Arabic surah
names alike, a continue-reading card reading the last saved position, and surah and juz list cards
behind a segmented control.

Task 6 built translation mode: the ayah cards (Arabic text, transliteration, translation), the
reader header shared with Mushaf mode, and position tracking that debounces scroll settling before
writing the last-read position so a fast scroll does not spam the settings store.

Task 7 built the reading settings sheet: a text size slider with a live Mushaf-font preview, the
transliteration toggle, a translation picker ordered tafsir first then by language, and the
translation or Mushaf mode switch.

Task 8 built Mushaf mode: a small page cache, the page and line renderer matching the printed
Mushaf's own breaks, and a horizontal pager turning 604 pages right to left with the same header
and settings sheet as translation mode.

**Review findings that mattered.** The Surah and Juz segmented control's tap targets were only
38 dp per option because the inset that shaped the drawn pill was also shrinking the clickable
area; the clickable now takes the full 44 dp row height and only the drawn pill is inset. The
reader's settings collector was missing a `distinctUntilChanged`, so every debounced last-read
position write, going through the same settings store as the reading settings themselves, re-ran
the whole settings pipeline and refetched the current translation on every scroll stop, and could
race the caption update; the collector now ignores writes that do not actually change the reading
settings. The reader header's two icon buttons had only their 36 dp drawn size as their touch
target; they now sit inside a 44 dp target with the same 36 dp disc drawn at its centre. Tanzil's
own text already carries the basmala at the front of an ayah 1, and the reader draws its own
basmala line above it, so ayah 2:1 was showing the basmala twice; the pipeline now strips that
leading basmala from the stored text (see the pipeline section above). And the reading sheet's
live size preview drew the ayah roundel in the body text colour instead of the accent colour every
other roundel uses, while a `selectable` parameter was being reused on a row that was not itself a
selectable option just to borrow its no-ripple click handling; both were fixed, and the check mark
in the translation list now follows the same fallback id the reader itself falls back to when the
stored translation is not one of the bundled ones.

**What remains for the device round.** Task 10 has not run yet: build Android and install on every
attached device, build iOS for the device and, failing that, the simulator; capture English and
Arabic, light and dark, screenshots of the tab root with a continue card, the reader at 2:255 with
transliteration on, the settings sheet, Mushaf pages 1, 3 and 42, and the tab bar; time the iOS
Quran database copy on first launch (must land under 2 seconds on the iPhone 12) and confirm a
second launch does not copy again; and fix anything visibly wrong in small follow-up commits on
the same task.

### Slice 2a - device round (8 September, 03:50 to 04:10)

Build f5ceedc on the S23 (dark, en-ZA), the iPhone 12 (installed and launched; the first
device build failed to link because the SQLDelight native driver needs `-lsqlite3` in the app
target, now added to the Xcode project), the iPhone 17 Pro simulator (light) and the Pixel 8 Pro
emulator. Full test run before the round: 334 JVM and 319 iOS tests in `shared`, 37 and 35 in
`widgetcore`, zero failures.

What was checked and held: the three-tab bar with 22 dp glyphs; the Quran root with the continue
card after a first read ("Ayah 5 of 286 · Juz 1"); the card reader in English and under an Arabic
UI (English translation now left to right after the direction fix); the reading sheet in Mushaf
mode with the disabled slider and its note; Mushaf pages 2 and 3 on the S23, page 2 under Arabic
UI, page 2 on the iOS simulator through the same Skia path the iPhone uses; the bundled database
copied and opened on iOS at first launch with no visible delay.

Fixed during the round: the iOS link (sqlite3), ayah cards selecting one at a time instead of
each card keeping its own toggle, and plain punctuation in the new attribution bullets.

Left for the morning review: word-spacing justification of Mushaf lines (they fill the frame only
as far as the auto-sized font allows, so short lines leave a ragged left edge); under an Arabic UI
the surah rows still lead with the transliterated Latin name and English meaning; Tanzil's Latin
surah spellings (Al-Faatiha, Aal-i-Imraan) may deserve a curated list.

### Slice 2a - morning review round (8 September, evening)

Mohamed's review of the slice on his phone came back with five items. The heavy one was the
Mushaf page: lines at visibly different sizes, and "strange characters" ringed on page 2.

**The characters.** Two separate things. The `ۛ` (three dots), `ۖ` (صلے) and friends are the
printed Mushaf's own pause marks; Tanzil writes them as space-separated tokens and the font floats
them between the words, which is how the Madinah page shows them. The genuinely wrong marks were a
small meem under "هُدًى": the layout source (quran.com's word data) encodes the sequential tanween
the printed page uses before idgham and ikhfa letters as tanween plus a small-meem sign
(U+06E2/U+06ED, 6,642 words across the Quran), which the KFGQPC Hafs font draws as a literal meem.
Tanzil's text, which the card reader already showed, does not carry them. The pipeline now trusts
the layout only for segmentation and re-texts every Mushaf word from Tanzil (aligned by bare
letters; a layout word may span two Tanzil tokens), so both modes show the same characters, and the
build proves it: all 6,236 ayahs' Mushaf words joined equal `ayah.text_uthmani` byte for byte,
every character sits in the Quranic code-point set, and with `uharfbuzz` installed every stored
line, word and ayah (92,485 runs) shapes with the bundled font without a missing glyph or a dotted
circle. The database is `user_version` 2; the Android driver had to open the file at that version
rather than SQLDelight's schema version or the framework threw `onDowngrade` (caught on the S23
on the first install, fixed before the round).

**The sizes.** The per-line auto-shrink was the cause. The page now takes one size: every text
line is measured at the width-derived base, the page is set at the largest half-step at which its
widest line fits, and each line's words are measured individually and spread across the frame by
word spacing, so the page reads at one size with every line filled to the margin like the printed
one. On the S23 most pages land around 19 to 21 sp; pages 1 and 2 stay at the base. The old
word-spacing follow-up is closed by the same change.

**The rest**, done by a parallel agent in a worktree and merged: continue card roomier with a gap
between name and detail; the surah and juz list caps no longer stroke a hairline across the first
and last rows; Quran and Settings titles sit 24 dp below the status bar (Prayer's is 12) instead
of 68; the Android status-bar icon is the mihrab silhouette from the app icon; the reading sheet
scrolls, clears the gesture bar, and names each translation's language in the device's UI language
(`PlatformFormat.languageName`, CLDR on both platforms, an English map as fallback).

Noticed on the way, not changed: filtering the surah list by "Mursalat" finds nothing because
Tanzil spells it "Al-Mursalaat"; the curated Latin names follow-up would fix that too. Android's
CLDR name for `bn` under an English UI is "Bangla", so that is what the picker says.

### Slice 2a - second review round (8 September, night)

Four items from the second look at the phone.

**One more meem.** The small meem under 2:41's "كَافِرٍۭ" is the printed Mushaf's own iqlab
sign: a kasratan before the beh of "بِهِ". The sweep asked for is now a build check,
`verify_iqlab_marks`: all 609 small meems in the text sit before a beh, in the same word (562),
the next ayah (11) or, on a surah's last word, the basmala that follows (36). Zero unexplained.

**Settings glyph.** The gear never sat with the arch and the book; it is now three sliders in
the same stroke, knobs at three heights, checked against the book's ink coverage at 22 dp.

**A chime you can hear at a desk.** Dhuhr and Asr on the Notification level were being missed
at work: 2.4 s at −5 dBFS is a message tone. The chime is now a six-second ascending bell motif
(A4, C#5, E5, struck twice) at −1 dBFS, still synthesised, generator in `tools/make-chime.py`.
Same file names, so iOS needed no project edit; Android's channel id suffix moved to `_chime2`
(channel sounds are immutable) with the old ids in the stale set, and that level now vibrates
too. Confirmed on the S23: the old channels read deleted, the new ones carry the sound and a
vibration pattern.

**Landscape.** One rule, `Modifier.contentWidth()` (600 dp, centred), on every scrolling
screen and the tab bar; the Prayer screen becomes two panes sideways (header and ring on the
start side, timeline, Qibla and the latitude note scrolling on the end side); the Mushaf frame
is capped and its lines scroll inside the frame when the page is taller than the screen; insets
come from `safeDrawing` so the side navigation bar and the camera cutout are cleared. Seen on the
Pixel 8 Pro emulator in both themes; the side-inset path is unproven there (gesture navigation
has none) and iOS landscape could not be rotated in the simulator, only built.

Also this round: release builds are R8-shrunk (10.6 MB against 33 MB debug; WorkManager's Room
database needed keeping) and unsigned; a chat-deliverable APK is the release build signed with
the debug key.

**Reverted the same night.** The shrunk release build crashed on the phone when a sound was
previewed, and the widgets stopped rendering. Reproduced on the emulator: `isShrinkResources`
removed `res/raw/chime` (and the other clips), which nothing references by `R` id, only by
`android.resource://` URI strings, so `MediaPlayer.prepare` threw and the sound sheet's play
button took the app down; the notification channels would have been silent for the same reason,
and Glance's dynamically resolved layouts are the likely widget casualty. The R8 commit is
reverted (a9ca19c). When the size round comes before launch: keep `raw/` and the Glance
resources explicitly (`tools:keep` in a `res/raw/keep.xml`), enable code shrinking first without
resource shrinking, and exercise every sound, both widgets and a real alarm on a device before
trusting it. Release builds are unshrunk and unsigned again; the debug build is the one on the
phones.

### Slice 2b - search, bookmarks, share (overnight, 9 September)

Built by subagent-driven development from a spec and plan written the same night, on Mohamed's
standing "you build, I review": `docs/superpowers/specs/2026-09-09-taqwa-quran-search-bookmarks-share-design.md`
and the plan beside it. Seven implementation tasks, a reviewer per task, two fix passes, then a
whole-branch review.

**Search.** The root's field now searches the Quran from two letters: Arabic queries match the
pre-normalised search text, anything else the translation the reader is set to (folded in Kotlin,
so Turkish and French case works), 250 ms debounce, capped at 100, results grouped as SURAHS
(up to five) and AYAHS with the matched words emphasised in the translation snippet. Tapping a hit
opens the ayah in whichever mode is set; the query survives the round trip. The plan's first
design used the FTS5 table the database had carried since 2a; it turned out Android's framework
SQLite is built without FTS5 and every Arabic query crashed on a phone (desktop and iOS SQLite
have it, so the JVM test passed). Bundling a SQLite with FTS5 would have added megabytes, so the
match moved into Kotlin over `ayah.text_search` and the FTS table left the database (22.5 to
22.1 MB, `user_version` 4).

**Bookmarks.** A string set in DataStore, newest first. Set from the card's action row or the
Mushaf pill; listed on a third root tab with a one-tap remove; the reader shows a small badge on
kept ayahs.

**Copy and share.** One formatter for both: the Arabic with its number in ornate brackets, the
translation with its name when one is shown, the reference in the UI's digits; the platform share
sheet behind an `expect fun`.

**Decisions taken without Mohamed**, for his review: the plural forms and wording of the new
Arabic strings; the bookmark storage in DataStore rather than a table; no undo on remove (re-adding
is one tap); no search history; substring matching for Arabic (broader than the FTS prefix match
it replaced).

**Also this night**: the Arabic-UI decision now reads the loaded strings rather than the locale
tag, so prayer names can never be shown twice; a full localisation sweep (Arabic and English, both
platforms, every screen, every translation) found and fixed the Arabic plural for 5 and 10
minutes, the untranslated transliteration credit, a doubled «الجزء» on the Juz tab, the Gregorian
date reordering under Arabic on iOS (a bidi isolate), and a clipped countdown in the iOS widget
preview; the release build is R8 code-shrunk to 12.2 MB (from 26.3) with WorkManager keep rules
that the Glance widgets need, resource shrinking left off after it saved only 0.4 MB and was the
thing that crashed the app the first time; and the sound-sheet footnote no longer says "on this
platform".

### Countdown seconds and the pre-release version (9 September, evening)

Merged slice 2b to main (fast-forward, 91d7cc9 → 412bb3d), deleted the branch and the leftover
`size-round` worktree, installed the shrunk release on the LoopPhone.

Mohamed relayed that someone read the ring's "0:20" as twenty seconds. The ring now shows
H:MM:SS and ticks visibly; the ticking loop already ran once a second for the timeline, so nothing
new is scheduled. Measuring Manrope's advance widths for the longer string turned up that its
default figures are proportional (the "1" is 0.37em, the "0" 0.59em), contradicting the slice 1
spec's "Manrope holds digit width" — the minute-granular ring had been shifting a few pixels
whenever a 1 came or went, just rarely enough to pass. The font carries tabular figures under
`tnum`, so `TaqwaText.Latin.TABULAR` now enables them on the countdown and row-time styles. At
44sp the tabular "10:00:00" would be 182dp wide against 178dp inside the stroke, so the ring's
text is 36sp (149dp for the worst case, 127dp for a single-digit hour), scaled with the ring's
diameter. The widgets stay at H:MM: Glance cannot redraw every second, and their countdown is
minute-granular by design.

Version name is 0.1.0 on both platforms (Android versionCode 2, iOS CFBundleVersion 2 in the app
and the widget extension, the Settings row reads the same string). New `scripts/bump-version.sh
<name> <code>` writes all five places; policy from here is a minor bump plus a new code after any
meaningful change that ships as an APK.

### Ayah widget (9–10 September, overnight)

Built by subagent-driven development from
`docs/superpowers/specs/2026-09-09-taqwa-ayah-widget-design.md` and the plan beside it, on branch
`widget-ayah` from main at 6d7223f. A home-screen widget showing one ayah a day from a curated
pool of fifty, drawn like the reader's ayah card. Pure-Kotlin pieces landed first in `:widgetcore`:
the pool itself, a serialised pool mirror that both platforms read instead of opening a database,
and a seeded Fisher–Yates rotation guaranteeing no repeat until all fifty have shown and never the
same ayah two days running. On Android, Glance cannot use custom fonts, so `AyahCardRenderer` draws
the card's text onto a transparent bitmap with `StaticLayout` and the bundled Hafs face, and the
Glance tree wraps that bitmap in the prayer widget's own card shell. On iOS, the ayah widget is a
third member of the existing `TaqwaWidgetBundle`, SwiftUI drawing the Hafs face directly with the
font copied into the extension's resources and registered through `UIAppFonts`.

The sharpest finding was that Glance's `actionParametersOf` tap action, though it did deliver its
extras, opened a second `MainActivity` on top of the first because the launch intent carried only
`NEW_TASK`; the old instance's dead Compose composition consumed the navigation request before the
visible one ever saw it. An explicit `Intent` with `CLEAR_TOP`/`SINGLE_TOP` fixed it. iOS turned up
two of its own: `Calendar.current` returns Hijri components under an Islamic system calendar, which
would have desynced the rotation from Android's, and the widget-reload dedupe key omitted the ayah
pool entirely, so a translation change could sit unseen for a week under the seven-entry `.atEnd`
timeline. Smaller ones: `tools/add-widget-target.rb` was found to have silently dropped `-lsqlite3`
from the app's link flags on its last rewrite, and Glance's `RemoteViews` bitmap budget meant the
render size needed capping (settled at a quarter of the display's pixels, floored and ceilinged).

Two implementation tasks per platform were reviewed clean after one fix pass each; a final
whole-branch review found nine more findings (three Important: the iOS dedupe key, the Hijri
calendar, and the mirror not rewriting on a UI language change) and a fix pass closed all nine.
Full suite at the end: `:shared` 421 tests, `:widgetcore` 73 tests, all green. The compact footer
branch, the iOS 16 padding fallback, and a forced midnight rollover were never exercised on a real
device or simulator; the device round on the S23 and iPhone 13, the 0.3.0 version bump, and the
merge to main were left for the morning.
### Qibla compass sweep (10 September, overnight)

The LoopPhone had been showing "Compass needs calibrating" on the Qibla screen forever, and no
amount of waving cleared it. An investigation on both phones (`.superpowers/sdd/qibla-investigation.md`)
found that the prompt was telling the truth on that device and lying by omission on the other.

**On the LoopPhone**, the MediaTek fused rotation vector is stubbed: it streams the identity
quaternion `(0, 0, 0, 1)` forever while reporting ACCURACY_HIGH, so every sample decodes to azimuth
0 and the app is forced onto the raw accelerometer+magnetometer path. There, the Voltafield
magnetometer's HAL is subtracting a 503.9 µT bias estimate from an 83.5 µT measurement, producing a
"calibrated" field of 536 µT — twenty times the 25.6 µT Earth's field at Cape Town. The reading
really is unusable. But the accuracy that raised the prompt was read only from `onAccuracyChanged`,
which fired once, 90 ms after registration, and never again in 90 seconds: the prompt could not
have cleared even if the phone had been swung, because nothing was listening for the result.

**On the S23** the opposite: the magnetometer accuracy callback never arrived at all, so the flag
sat at its optimistic `false` default and the app showed a confident needle on an accuracy it had
never read.

**On iOS**, reading the code, the compass manager called `startUpdatingHeading()` alone. Core
Location only computes a true heading for a manager that is also receiving location updates, so
`trueHeading` was `-1` — which the smoothing filter turned into a heading of 359°, a needle that
looked alive and pointed at nothing.

**What changed.** Accuracy is now read from `event.accuracy` on every sample, and per-sample facts
feed one pure, unit-tested state machine (`CompassAccuracyGate`) rather than flipping the screen
directly: low after a full second of low samples, good again only after a second and a half of good
ones, and — the escape hatch that did not exist — a best-effort state after twenty unbroken seconds,
which drops the needle and shows the bearing and the distance, which are computed from the location
and were never in doubt, under a caveat saying why. The magnetometer's field magnitude is now sanity
checked against 20–70 µT, and a field that cannot be the Earth's is reported as interference, with
copy that asks the user to move away from metal, magnets, cases and cables instead of drawing a
figure of eight that cannot help — which is what the LoopPhone now shows, correctly, at 536 µT.

Also: the rotation matrix is remapped for the display rotation (a sideways phone was 90° out); the
raw pair is fused on the magnetometer event only, which halved the emit rate on the LoopPhone from
100 Hz to a measured 50; the stubbed rotation vector is unregistered after two seconds of degenerate
samples (measured: 2 007 ms) instead of streaming two hundred useless samples a second for the life
of the screen; a null location marks the heading low rather than passing magnetic north off as true
north; and the collection now follows the lifecycle like Today's tick, so backgrounding the app
unregisters the sensors — verified with `dumpsys sensorservice`, which shows no connection thirty
seconds after Home.

On iOS the heading manager now starts location updates beside heading updates, asks for when-in-use
authorisation if the app has never asked, sets `headingOrientation` from the device's orientation,
implements `locationManagerShouldDisplayHeadingCalibration` so the system's own calibration dial can
appear, and treats a negative `trueHeading` as low accuracy. It compiles and installs on the
iPhone 13, but the runtime check that `trueHeading` becomes valid could not be run: the phone was
locked and cannot be unlocked from here. That check is still outstanding.

The qibla tests went from 33 to 63 (433 in the module, all green): the gate's state machine in both
directions and at both thresholds, the field plausibility band at 19/20/45/70/71 µT, the heading
filter across the 0/360 seam in both directions with monotone convergence, Cape Town (23.37°,
6 557 km) and Tripoli (109.18° — the brief's 107.7° was wrong), a negative iOS heading, and each
gate state's mapping to a screen state.

### City names in every language, and the ayah widget's tap (10 September)

Two things Mohamed asked for after living with the ayah widget for a morning. The widget's tap was
opening the Mushaf page whenever the reader was set to Mushaf mode, which answered a card showing
one ayah with a page of fifteen; it now always opens the translation reader, since that is the view
the widget itself is a picture of.

The larger half was the city list. It had only ever been English, so an Arabic reader searched a
Latin list and read a Latin name in the Prayer header. GeoNames publishes translated names beside
the `cities15000` extract the bundle already comes from, keyed by the same id, so `build-city-db.py`
gained it as a third source: `cities.csv` grew a `geonameId` column and six `city-names-<lang>.csv`
files appeared for Arabic, Indonesian, Urdu, Bengali, Turkish and French. Mohamed chose all seven
languages over English and Arabic alone after seeing that the six cost about 480 KB compressed and
that only Arabic can actually be displayed until the other interfaces exist. Coverage is partial by
nature — Arabic names exist for 88% of the world's hundred largest cities but only 26% of all
34,135 — and everything uncovered falls back to English, which was his own rule.

Three things only showed up once the rules met the data. GeoNames carries both a vocalised and a
bare Arabic spelling for some cities with neither flagged preferred, so file order decided it and
Tripoli — the owner's home city — came out as طَرَابُلُس; the pipeline now prefers the bare
spelling, which moved nineteen names. The folding rules were written from the examples that
motivated them (Zürich, İstanbul) and covered 28 of the 111 marked Latin letters the name column
actually uses, so "thane" did not find Thāne and its 1.8 million people; the table is now generated
by decomposing U+00C0–U+024F and U+1E00–U+1EFF, and a test over the real bundle asserts no English
name folds to anything non-ASCII. And the saved location only ever stored a name, so it could not be
re-translated; it now stores the city's id, with an older location's id resolved once from its
coordinates.

The review also caught that the app's `PlatformFormat` is built once and the activity swallows a
locale change, so the search screen would have gone on searching in the language the app started
in. One format now decides the language for the whole tree, keyed on the loaded strings. 510 tests
in the shared module, all green.

### Three corrections (10 September, afternoon)

The ayah widget's tap was the interesting one. It had been changed that morning to always open the
translation reader, on the reasoning that the widget shows a card with a translation; Mohamed's
actual want was the opposite — open whichever reader he reads in, but land on the ayah *selected*,
the way tapping the card itself leaves it, with bookmark, copy and share showing. So the mode
branch came back, and both targets now carry the selection: `Screen.Reader` gained `selectAyah`
and `Screen.Mushaf` gained the ayah to highlight. The decision between them moved out of the
composable into a pure `ayahWidgetTarget`, because the mode that picks between the branches is
persisted and flipping it on a device means driving the reading-settings sheet by hand — as a
function it takes three lines of test instead.

The Prayer ring hung 48 dp below the header and 36 dp above the timeline card, so it read as
belonging to the card rather than sitting in a band of its own; both gaps are 36 dp now. And the
Appearance screen labelled one preview "PREVIEW" and the other "AYAH WIDGET", which read as two
different kinds of label; both are named now, which also says which widget each card is a picture
of. 520 tests, all green.

### Offering the widget you have not added (11 September)

Mohamed asked whether the app can tell which widgets are on the home screen, and whether the
Appearance previews could offer to add a missing one. It can, and on Android it already did: the
same query the refresh alarms use to decide whether to keep ticking answers "is this provider
placed". iOS can answer too, asynchronously, through WidgetKit's current configurations, which the
app had never asked before. Adding one is the asymmetric half — a launcher can be asked to place a
specific widget and shows its own confirmation sheet, while WidgetKit has no such call at all, so
iOS gets the steps written out instead of a button that would do nothing.

Under each preview there is now a row when that widget is missing and the launcher takes pin
requests, nothing at all when it is already there, and the platform's steps when it cannot be
asked. The three-way choice is a pure function rather than a tangle of conditions in the
composable. The suggestion that started this — making the preview itself the button — was turned
down: the preview's job is to show the background choice, and it is the most obviously tappable
thing on the screen, so a tap that did something else would be a trapdoor.

Two things came out of review and one out of looking at it. `WidgetPlacement.Unknown` reports both
widgets as *placed*, so every path that cannot answer — a null hook, a timeout, a device with no
widget host — offers nothing; offering to add a widget somebody already has is the worse failure.
The iOS instructions were showing under both previews, one identical sentence twice, which read as
a copy-paste; they are said once now, under the single preview that needs them, or after both when
both do. Neither the onboarding skip nor a test for the iOS timeout was built, and the spec says
why in both cases.

### Tasbeeh (11 September)

A dhikr counter, opened from a misbaha glyph at the end of the Prayer header. The design round
offered six icons in the header and three directions for the screen; Mohamed chose the ring, so
the counter is the Prayer screen's own ring drawing progress toward a moment, with the dhikr above
it where the city sits above the countdown. The post-prayer set is one continuous count to a
hundred — the number never resets at 33 or 66, the displayed dhikr does — with a distinct haptic
at each part's end and a third at the hundred, and the three parts named in a small row under the
ring so the current one is always in view. No totals anywhere: dhikr is not a score. Under an
Arabic interface the Arabic stands alone, without transliteration or meaning.

The engine is a pure function over (preset, count, round) that returns the next state and one
event, which is what made the off-by-ones at 33, 66 and 100 checkable by walking all hundred taps
in a test. The ring's Canvas moved out of `CountdownRing` into `RingArc` so both screens draw the
same stroke, and a before-and-after diff of the Today screen came back empty. Haptics gained three
patterns beside the compass tick and, on Android, file under touch feedback so Samsung's slider
governs them; on iOS every generator call was moved to the main thread, which the first draft had
only done for the delayed pulses. The screen keeps the display awake and restores whatever it
found.

Two review findings were worth the round trip. The first draft pinned the dhikr to the top and
centred the ring in what was left, which read as two separate things with a void between; the
stack is centred as a group now, with the reminder row's and hint's slots reserved so the ring
never moves. And the view model shared one cancellable job between the debounced write and the
preset switch, so a chip tap followed within a third of a second by a page tap could silently
cancel the switch; the debounce has its own job and the state updates are atomic. 565 tests in
the shared module, the suite green on both targets.

### Tasbeeh, second pass (11 September, 0.8.0)

Four things Mohamed asked for after using it. In landscape the chips column had taken half the
screen and the ring's Box was the only tappable part, which left the empty start side dead; the
tap surface now spans everything up to a 220 dp chips column, and a tap anywhere on it counts. A
custom chip's long-press sheet offers Edit beside Delete, prefilling the phrase and target; a
lowered target clamps the saved count so the ring never reads past its own end. The screen names
itself the way the Reader does, "Tasbeeh" beside the chevron at 17 sp, with nothing else moving
(a pixel diff against the previous build differed only in the header band). And the three single
built-in dhikr count to a hundred like the rest, because switching from the post-prayer set to
SubhanAllah silently changed the target from 100 to 33 and looked like a bug; the set alone keeps
its 33 · 33 · 34, and a new custom dhikr starts at 100. A count saved under the old 33 survives as
so-many of 100. The landscape header cost the stack height it had been borrowing from the ring,
so the landscape ring reserve is now measured against the tallest stack and the ring there is
about 154 dp instead of 196. 576 shared tests, suite green on both targets.

### A choice of adhan (12 September, branch `adhan-voices`)

Mohamed asked for two more adhan recordings behind a setting, with the original staying the
default. Finding them was most of the work. Sixteen candidates were pulled from Wikimedia Commons,
Freesound, archive.org and the CDN most open-source prayer apps quietly copy from, and measured
rather than described: duration, loudness, the floor between phrases, and the number of phrases,
which turns out to separate a Sunni adhan from a Fajr one from a Shia one without a transcript. Two
Commons files that looked ideal still carried the ID3 tags of the albums they were ripped from; the
cleanest recording found was the Shia form; the archive.org items were all self-applied "public
domain" marks that grant nothing. Two survived. "The Adhan" by Aaqib Azeez, CC BY-SA 4.0, is plain
and brisk at 1:26, the only other complete general adhan with a named reciter. The Eid Fajr adhan
from Malmö Mosque by Besim Azemi, CC BY 3.0 from the mosque's own channel and the only licence in
the set audited end to end, is live Balkan maqam. It is a Fajr adhan, so before it could serve as a
general voice the two *as-salatu khayrun min an-nawm* phrases had to go; a local Whisper transcript
with word timestamps placed them, silence detection found the two-second pauses on either side, and
they were spliced out inside those pauses. The transcript also showed a "subscribe to the channel"
line at the tail of the original CC0 recording, which a closer look at the energy proved to be a
recognition hallucination over a decaying note, not a YouTube outro.

The setting itself is one row on the Notifications screen with a sheet in the sound sheet's idiom,
a play button per voice for the complete adhan. Under it the voice threads through everything the
sound already did: the asset table is keyed on (sound, voice), the Android channel id grows a voice
suffix for the two recorded levels because a channel's sound is immutable, the alarm receiver reads
the voice from its intent and falls back to the original for alarms scheduled before the upgrade,
and iOS picks the file by voice. The original voice produces byte-identical channel ids to before,
checked by diffing the channel list across the upgrade, so nobody's existing channels churn. The
Azeez source sat at full scale and the loudness match wanted +4 dB, which needed a limiter after
the resample back to 44.1 kHz; placed before it, the Vorbis encode overshot to +0.2 dBTP. Nobody has
listened yet: everything about how the two voices sound is measured, and Mohamed's ear in the
morning is the release gate.

### A hundred ayahs for the widget (12 September, 0.10.0)

The widget's pool was fifty references, which is a seven-week loop that a daily glance notices;
a hundred is about a season. The pool is references only, the text comes from the bundled
database, so the change costs nothing in size and everything in curation. A ranked list of a
hundred candidates went through the same database test as the first fifty (each reference exists,
at most 24 Arabic words, 245 characters of Saheeh International, no duplicates) and a reading of
the translation for completeness. Mohamed then read the fifty and set a sharper bar than the one
the first round had used: each card must carry a whole thought a person can sit with, which rules
out a narrative lead-in ("They said…", "When the youths retreated…"), a rhetorical opening whose
answer is in the next verse, and a group described without the point about them. Twelve came out
on his reading and one more on the same rule after it; their replacements came from further down
the ranked list under the tightened bar. The rotation is a permutation over the pool size, so the
day someone updates, the widget shows a different ayah once and the no-repeat cycle restarts. 1,295
tests across the four targets; the emulator's mirror holds a hundred entries and none of the
vetoed references.

### The hundred lets go of the ring (12 September)

A completed tasbeeh set sat on its hundred until the next tap took it to 1 of round 2. Mohamed
wanted the counter to come back to zero on its own shortly after a set completes, and the change
is small once it is put in the right place: the engine gains a pure `nextRound` (count 0, round
+ 1), and the view model holds the closed ring for one second after `SetComplete` before applying
it and writing it straight away. The cases around the hold are the whole of the work. A tap inside
the hold cancels the rollover and opens the next round at 1, as it always did, so a reader going
straight through a hundred never waits and never sees a 1 wiped back to 0. Every path that writes
the preset on its way somewhere else (leaving the screen, a chip switch, adding, editing or
deleting a phrase) settles the rollover first, so what reaches disk is 0 of the next round rather
than a hundred that would greet the reader on return. A hundred that is on disk anyway (the
300 ms debounce wrote it and the process died inside the second; an install from before this
change) opens as the hundred it was and rolls over after the same hold. The rollover is not a
count and fires no haptic. Five view-model tests on virtual time and one engine test cover the
hold, the tap inside it, the flush, the chip switch and the stored hundred. Built in a worktree
off `main` while the recitation branch was in flight in the main checkout.

### Trust round one (13 September)

A read-only audit of the app's trust and security surface produced eleven findings and a
handful against the unmerged recitation branch; Mohamed picked five for now and deferred the
privacy policy and About screen. The Android launcher activity is exported, so any app on the
phone could start it with `open_surah=999` and the reader threw on a surah the database does
not have; the bounds (114 surahs, 286 ayahs at most) now live in the shared `LaunchRequests`,
so iOS, which checked only `ayah >= 1`, gets the same rule. Coordinates are stored rounded to
three decimals, about 110 m, which moves no prayer time and no qibla bearing; the file should
not hold a GPS fix's metre precision. `allowBackup` is off on Android: the settings file holds
the coordinates or chosen city, and the promise that the location never leaves the phone has to
include Google's backup. Backup rules could not split the file, since bookmarks and tasbeeh
counts live in the same DataStore, so the whole app opts out and settings are redone on a new
phone. On iOS the same file moves from Documents, which iCloud backs up, to Application Support
marked excluded from backup, with a one-time move of the existing file so nobody's settings
vanish on upgrade. iOS now asks for reduced location accuracy by default, so the permission
sheet shows "Precise: Off"; the qibla bearing at that scale is under a degree out except very
near Makkah. The README said the Hijri date was Umm al-Qura while the class said, in its own
comment, that it deliberately is not and can differ by a day; the README now says tabular, and
a note under the Hijri control says the same to the reader in both languages, since a reader
whose mosque is a day off and who finds no explanation concludes the app is wrong. The iOS
widget-preview route is compiled out of release builds.

### Recitation (12–13 September, branch `recitation`, 0.11.0)

The largest slice since the reader, built in one night by five agents in sequence and two in
parallel, with a data pipeline running unattended beside them. The investigation came first and
changed the plan twice before a line was written: the seven reciters became ten (Al-Ghamdi, asked
for, is simply not in the licensed corpus); the reciter photos were dropped altogether after the
research found that most Commons portraits of famous reciters are studio pictures tagged "own
work" by accounts that upload celebrities in bulk, so every reciter has a calligraphic monogram
instead; and a per-surah size that had been guessed at 25 MB turned out to be 58 MB for Alafasy's
Al-Baqarah, which made the container's ability to start playing before the file is complete worth
having rather than merely neat.

The files are the Islamic Network's per-ayah MP3s, byte for byte, because their licence says "at
the bitrates we publish" and because the ayah boundary being the file boundary is what makes
highlighting need no timing data. They are packed one surah per `.taqa` container, a twelve-byte
header, a JSON index of ayah offsets, then the MP3s back to back, and hosted as GitHub Release
assets on the public `Taqwa-data` repository, one release per reciter, under a manifest the app
refreshes daily so a reciter can be added or withdrawn without an app update. Four reciters begin
at full voice on the very first sample, so the manifest carries a per-reciter gap that the player
inserts between ayahs; without it they sound rushed. Around sixty ayahs across four reciters were
served as HTTP 502 by the CDN for hours; the pipeline took those from the everyayah mirror only
after proving, per reciter, that files already held from both sources decode to identical audio.
Shuraim's mirror copy is a different encode and would not have passed that gate.

On the phone: a speaker button at the end of the reader and Mushaf headers, a Play action first in
the ayah row, and a 56 dp player bar that hides on the other tabs while playback carries on. The
bar follows the voice unless the reader has scrolled more than a screen away, in which case a small
"Back to ayah N" pill offers the way rather than yanking the page. Android plays through Media3 in
a `MediaSessionService` reading byte ranges straight out of the container; iOS splits the container
into per-ayah files under Caches and drives a single `AVPlayer` with a manual queue so the ayah
signal is exact. Downloads are WorkManager on Android and a background `URLSession` on iOS, resumed
with `Range` from a `.part` file and verified by SHA-256 before a surah counts as present; the
device round proved a force-stop at 45 % resumes from byte 172,032 rather than zero. The one real
bug the emulator found: on mobile data the `UNMETERED` constraint parked the work before the code
that would have said "needs Wi-Fi" could run, so the reader saw "Queued" forever; the question is
now asked before enqueueing. Settings › Quran › Recitation holds the reciter, the mobile-data
switch, per-reciter downloads with delete, and "Download the whole Quran", whose batch was cancelled
on the emulator at 68 surahs with nothing left queued and every finished surah still on disk.
743 tests in the shared module, the suite green on both targets.

### Recitation, round two (13 September, branch `recitation-2`)

Mohamed used 0.11.0 for a morning and came back with six things. The order of the reciters
(Ash-Shatri second), the transport buttons too close together, and three behaviours that were
right by the spec and wrong in the hand: a progress line that filled and emptied every ayah, a
reciter tap that did nothing when the new voice lacked the surah, and a preview that played over
the recitation like a second radio.

The surah became one clock. A `.taqa` records every ayah's byte length, and the corpus is
constant-bit-rate, so the length of each ayah is its bytes over its bit-rate to within a frame
— the only thing to leave out is the ID3 tag at each file's head. `SurahTimeline` is that
arithmetic, gaps included; both players build it from the same container, the Android service
is handed it with the queue and reports the whole surah to the lock screen, and a scrub there
lands on the start of the ayah under the thumb. Nothing is refined from measured durations on
purpose: two players reading two clocks would drift a frame an ayah, and the one thing a
measured length is used for is to scale the position inside its estimated slot so the clock
never stalls at a seam. Then Al-Ajmi's edition turned out not to be constant-bit-rate after all,
and an ayah may now carry its own `kbps` in the index.

Picking a voice that has nothing for the playing surah now offers the download with a sentence
saying the old voice carries on, draws a thin ring around the monogram while the copy arrives,
and switches at the ayah being heard when it lands. Tapping the voice already playing, while
paused, resumes it. A preview pauses the recitation and the end of the clip — `ClipPlayer`
reports it now — gives it back.

Al-Ajmi himself cost the afternoon. His 128 kbps edition has an MPEG-video fragment where 9:62
should be, stubs at 50:9 and 50:10, three ayahs at telephone quality and hundreds at variable
bit-rate. The repair script had been counting a 200 with no audio as a recovered file and
looping. The 64 kbps folder is the same recording, whole; he ships from it — and the review
of the branch found that a tenth of *those* files carry a header claiming fifteen times their
real length, which ffprobe and both platform players repeat. So the pipeline stopped trusting
headers: every file is decoded, judged on its decoded length, its average bit-rate and a
factor-of-three band against the other reciters, and the length goes into the container's
index, where the app reads it instead of estimating. The same review caught a switch that
could be lost by re-picking the voice it waited on, and one that would have started a
finished surah reciting again, unprompted, when its download landed at midnight.
### Saying what the app does with the network (13 September, 0.12.0)

Until 0.10.0 the README could say "no network access of any kind" and be right. 0.11.0 added
recitation downloads and, with them, a catalogue refresh that ran from the start effect on every
launch, whether or not the person had ever touched recitation. Both stores want a privacy policy
for an app that touches the network, and the app had nowhere to put one. The order of this
round was the point of it: the code change came first, so that the policy could say "the app
makes no network request until you use recitation" and be telling the truth, rather than the
words being softened to fit the code.

The gate is one fact, `RecitationEngagement`: a DataStore flag written the first time any
recitation entry point is used, falling back to "any surah in the download registry" for the
0.11.0 installs that downloaded before the flag existed. The refresher returns before reading
or writing anything until that fact is true, so the first engaged launch fetches at once rather
than a day later; the picker and the Recitation settings screen also ask for the daily check
when they open, since a person who went there wants the current catalogue. The proof was a
logging proxy in front of a fresh emulator: from launch through onboarding and a minute idle
on the Prayer screen the only hosts seen were Google's own connectivity checks; the single
GitHub line appeared at 14:36:02, the moment the reciter picker opened, and not on the speaker
tap before it. A review of the gate found the engagement write running unguarded on the
reader's primary tap, which a failed DataStore write would have turned into a crash; it is now
swallowed as the refresh already was, and the refresher takes a mutex so the start effect and
the picker cannot both fetch in the same second.

The policy is one page of plain English at the repository root, and the store answers live
beside it in `docs/STORE-PRIVACY.md` so the forms are filled from the code rather than from
memory. Two facts in the handoff spec were wrong and the words follow the code: bookmarks and
Tasbeeh counts are DataStore, not SQLDelight, and the iOS settings file is already excluded
from iCloud backup. The About screen is where the promise is made in the app: three cards in
the settings idiom, the product name in Latin script in both locales, and three rows that leave
the app under an external-link glyph that mirrors under Arabic. iOS declares exempt
encryption, which removes the export-compliance question from every upload.

### Recitation, round three (13 September, evening)

Three asks after an hour with 0.13.0. Previous and next on the bar and the lock screen now move
by surah, like a music player's track buttons; a long press on the bar moves by ayah, and the
Android notification grew two extra buttons for the ayah, since a lock screen cannot hold a
button down. The clock row got air above it. And the download sheet carries a switch,
"Download future surahs without asking", ticked the first time: a surah you play is fetched and
played when it lands, the header's ring the only thing moving, and the sheet comes back only to
say why a fetch was refused.

### Back to the ayah (13 September, night)

Two more from Mohamed. The bar's words now take you to the ayah being recited — scrolling if
the surah is already open, opening its reader or page if not — and the monogram alone opens the
picker. A tap on the Android media notification does the same through a launch request the
app resolves once it is in front. iOS gives no such tap, so there the app coming to the front
with a voice still going opens the ayah; paused, it stays where it was.

### Store readiness (13 September, late)

A sweep of everything the two stores look at, done as three read-only audits (Android, iOS,
shared code and content) and then the fixes. The ones that would have stopped a submission:
Play has required Android 16 (API 36) for new apps since 31 August, and the app targeted 35;
release builds were signed with the debug key and there was no App Bundle; iOS had no privacy
manifest, and its 512 px Play icon file was still the Android Studio placeholder. Now targetSdk
36 (back gestures, per-app Arabic and the alarms checked on an Android 16 emulator), an upload
key read from a git-ignored `keystore.properties` with `scripts/release.sh` building the signed
`.aab`, privacy manifests for the app and the widget, the location permission text in Arabic,
iPhone-only for launch, and the real icon.

Two things the audits found that were not about the stores but about the adhan itself: prayer
alarms were never re-armed after an app update, so every Play auto-update would have silenced
the app until it was next opened, and a denied "Alarms & reminders" had no way back from inside
the app. Both fixed; `USE_EXACT_ALARM` is gone (Play limits it to alarm-clock apps), replaced by
a button to the system screen and an inexact-but-Doze-proof fallback. The Notifications screen
also says so when the OS has notifications off for Taqwa, on both platforms, and iOS now shows
a prayer notification while the app is open. Plus a licence carve-out (the GPL never covered the
Quran text, fonts and audio; now the repository says so), the Mixkit source clip taken out of
the public tree, and the store answers and a launch checklist in `docs/`.

### Seven languages (14 September, overnight)

Mohamed wanted the whole interface in the languages the Quran already came in, and the reach
argument won: French, Turkish, Indonesian, Urdu and Bengali join English and Arabic. Five
translation passes ran in parallel from one brief and a glossary each, into the 324 interface
strings, the widget labels on both platforms, the location permission text, the site's pages and
the privacy policy; a validator (`tools/i18n-check.py`) holds every file to the English keys,
placeholders and plural categories. Underneath, the code stopped asking "Arabic or not":
`UiLanguage` in `widgetcore` knows each language's direction and script, and the words that get
baked into notifications and widgets outside Compose (prayer names, the countdown heading, Hijri
months, notification and download copy, the high-latitude note) became tables keyed by it. Urdu
is right to left without being Arabic; Bengali is upright and left to right without Manrope or
tracking; Turkish capitals get their dots. Indonesian ships twice under the Compose resources
because Android still spells it "in" and iOS "id". The site gained a language picker in place of
the lone Arabic link, and each language its own policy page.

The morning review of the screenshots (emulator, five languages, four screens each; simulator,
three) found three things the validator had not: Compose Multiplatform prints a backslash-escaped
apostrophe literally, so every Turkish and Indonesian `Kur\'an` showed its backslash — the
typographic ’ is now the house style and `tools/i18n-check.py` fails the escaped form in Compose
resources (and the unescaped form in the Android app's own, which aapt rejects); the translated
files still carried the previous version string, so `scripts/bump-version.sh` now writes it into
every language; and the timeline showed Urdu the Arabic names (الفجر) while its own ring and
notifications said فجر — in an Arabic-script interface the interface's own name stands alone, so
it now does. The crash-report feature landed on main meanwhile; its six strings and its policy
paragraph went into the five new languages with the merge. 0.18.0 (21) built after the merge:
tests green on both platforms, the release APK on the emulator and the S23, the site's 21 pages
checked in the browser.

### Launch sweep (14 September, before dawn)

Mohamed asked for one more pass over everything before the stores, and the day started with a
regression in the language work itself. Five native reviewers, one per new language, read every
string, the Kotlin tables, the policies and the site pages against the English; their must- and
should-level corrections were applied (a feminine slip in the French meta, «Un jour avant», the
Turkish ring label shortened to «Sabah vaktine», PUEBI «Mahasuci», an Urdu pronoun that inverted
the high-latitude sentence, a Bengali onboarding title that read as a duration) and the nits left
as notes. A code review of the Kotlin found the two-language shortcuts the refactor had not
reached: the ayah widget on both platforms still asked `startsWith("ar")`, so an Urdu footer paired
a Latin name with the Arabic one; the Quran search classified every Urdu-script query as Arabic,
so the default Urdu reader could not search their own translation (Urdu and Farsi translations
are searched first now, the Arabic text after); Turkish translation search lower-cased with the
locale-free `lowercase()` and never matched «İman»; the widget mirrors carried an Arabic-Indic
boolean, so Bengali widgets counted down in Western digits beside Bengali clock times (the flag
now means "native digits" and `WidgetDigits` picks the language's own zero); and `WidgetContent`
named every non-Arabic-script widget row in English — a Turkish home screen said «Fajr · الفجر».

Devices found the rest. An in-place language change (the Android 13 per-app page returning to a
live process) left armed notifications and the prayer-widget mirror in the old language until a
cold start — the S23's widgets stayed Urdu after the app had run in Bengali because Today's view
model, which writes the mirror, only exists while the Prayer tab is on screen; `App.kt` now
reschedules and refreshes the mirror when `uiLanguage` changes. The Settings › Language row was
informational and did nothing when tapped; on Android 13+ and iOS it now opens the system's
per-app language page. About › Privacy policy opens the site's policy in the app's language. The
emulator's London Fajr alarm fired in Bengali with the right copy and digits; Samsung's Urdu and
Bengali faces render every screen; large font, dark theme and landscape hold in Urdu. Two platform
facts stay as they are: iOS formats Bengali with Western digits (Apple's locale default, unlike
Android's ICU) and the ring keeps Western digits until the tabular check. Shipped as 0.18.1 (22).

## Playing on, and the bar after a widget tap (14 September, evening)

Three notes from Mohamed while he sets up the store accounts.

**The player bar after a widget tap.** Opening an ayah from the home-screen widget and pressing
Play on its card played the surah with no bar until the Quran tab was left and re-entered.
Cause: the widget's path pushed the Quran root and the reader on top of whatever tab was showing
(so that one Back would reach the surah list), while the bar is drawn only while the Quran tab is
*current*, and `Navigator.currentTab` reads the bottom of the stack — still Prayer.
`Navigator.openReading` now replaces the stack with the Quran root first when that tab is not
current, then pushes the reader; the media notification's path shares it. Six navigator tests;
verified on the emulator and the simulator (bar on the reader and on the root at once).

**A surah that plays out goes on to the next one** (recitation spec §16.1). The platform players
used to tear everything down at the last ayah. Now they hold the ended surah for up to five
seconds and report it (`RecitationPlayer.surahEnds`); the controller waits a one-second breath
and loads the next surah into the same session — no notification flicker, audio focus kept, the
iOS audio session kept active with a background task over the split — or stops explicitly: at
An-Nas; when the next surah is not on the phone and downloads are on request; or, under "without
asking", after enqueueing the fetch that starts it when it lands. The reader follows the new
surah; anything that moves the recitation during the breath wins. Seven controller tests.
Verified on the emulator (Al-Fatiha → Al-Baqarah with the reader following; Al-Kawthar, with no
109 on the phone, ended with no session and no notification left) and on the simulator (the same
three, read off the debug harness log). The emulator's Wi-Fi reports "no internet" today, so its
downloads never run: the containers were sideloaded with `run-as` into
`files/quran/audio/ar.alafasy/` and adopted by `reconcile()` at start, which is the way to test
recitation offline.

**Mixkit.** The Notification-level tone's credit in Attribution and `docs/ATTRIBUTION.md` stays:
the licence does not require it, but the app names every third-party asset it ships.

Released as 0.19.0 (23); the store build becomes 1.0.0 (24).

## One notification per batch, and a pick that does not press play (14 September, night)

Two more from Mohamed's phone. **"Download the whole Quran" filled the shade**: WorkManager posts
each worker's foreground notification under the id the worker names, and the workers named one
id per surah, so two surahs in flight were two lines and the batch summary a third. Every
download worker now uses one id and, while more than one surah of a voice is in flight, writes
the same batch line — "Mishary Rashid Alafasy · 44 of 114 surahs", the bar counting surahs —
so the shade holds one entry; a surah on its own keeps its own "Al-Baqarah · 9.3 of 58.2 MB".
The separate summary is gone. Verified on the emulator against a real batch (Alafasy 14 → 45,
through a proxy on the Mac because the emulator's own Wi-Fi fails validation today): one entry
in the shade, counting up, gone the moment the batch was cancelled. The emulator needed
`pm grant … POST_NOTIFICATIONS`; without it no download notification shows at all, which is
also what a reader who declined notifications sees, and the downloads run regardless.

**Picking a reciter resumed a paused recitation** (§14.4's rule, from the bar's picker, read from
Settings as "why did it start playing"). Withdrawn: a pick changes the voice and nothing else —
playing goes on playing in the new voice, paused stays paused in it, nothing loaded stays that
way. The audition's own pause is the one resume left. Three controller tests rewritten.

Released as 0.19.1 (24); the store build becomes 1.0.0 (25).

## The Quran switch, full width (14 September, late)

Mohamed: the Surah | Juz | Bookmarks switch should span the row, each option a third of it, the
selected pill filling its third, the pill itself unchanged. `TaqwaSegmented` gained `fillWidth`
(the Row fills, each option weighs one); the reading sheet's Translation | Mushaf switch keeps
hugging its labels. Released as 0.20.0 (25) — and reverted an hour later on Mohamed's second
look ("not a good idea"): the switch hugs its labels again, `fillWidth` is gone, 0.20.1 (26).
The store build becomes 1.0.0 (27).

## Prayer times that follow a journey (15 September)

Mohamed travelled 300 km and the iPhone's adhan stayed ten minutes early for a day until he
opened the app. Not a fault but a gap: the location was refreshed only on foreground and on a
timezone change, and every background wake-up planned against the stored coordinates. The
background wake-ups — the iOS refresh task, Android's prayer alarm and top-up — now read the
phone's last known position (no fix, no dialog, nothing a closed app is refused) and re-resolve
when it is more than 5 km away; Android's alarm rebuilds the plan on a move even with a full
window, and the iOS task is asked for six hours out instead of a day (and now reports success
when its work ran, not only when something got scheduled — an empty plan by choice had been
teaching iOS to grant it less). A manual city stays untouched. Six new refresher tests;
verified on the simulator through the debug harness (London → Cairo, no fix asked for); the
emulator cannot give a coarse-only app a fix; on the LoopPhone the cache answers while the
process is warm from a recent use and is withheld from a cold one, so Android's gain is the day
of the trip, not a phone left closed. The proper answer on both platforms, background location
under "Always",, is written up in spec §16.5 as a later opt-in. Released as 0.20.2 (27); the store build becomes 1.0.0 (28).

## Store copy and screenshots (16 September)

The Play account cleared verification, the upload key exists, 1.0.0 (28) went to internal
testing. Then the listing: copy in seven languages under `docs/store/listing/` (English and Arabic
written here, the other five by native-writer agents against the app's own strings, every field
inside its limit by `tools/store-listing-check.py`), and screenshots from the S23 (1440 × 3088) and
the iPhone 17 Pro simulator, eight screens per language, framed by `tools/store/frame.py` into
Play's 1080 × 1920 and the App Store's 1320 × 2868 with the caption above the phone.

What it took to make the captures repeatable: the debug harnesses on both platforms can now open
a screen, set the theme, the reading mode and the translation by name, so one flow runs in every
language with no finger on the glass and no dependence on the phone's starting state — the first
runs toggled the Mushaf mode by counting taps and drifted, and showed the S23's English translation
under French and Urdu. The first "Tripoli" was Lebanon's (1,498 km to Makkah); Libya's is 2,916.
The 16 Pro Max simulator needed a tap permission nobody was at the keyboard to grant, so the 17 Pro
did the iOS set and the framer upsizes into the 6.9-inch slot. The simulator has no compass, so the
App Store set carries seven screenshots; its notifications had to be allowed once by hand.

## The closed test's first feedback (18 September) — 1.0.0 (29)

Five testers, six items, one night. The version name stays 1.0.0: build 28 only ever went to the
closed track and iOS has not shipped, so the first production release on both stores is still
1.0.0 and only the code moves until then (`build-29` tags the tester build; `v1.0.0` moves to
whichever build goes to production).

The sound-preview report was a phone on silent, so the sheet now says so, but only while it is
true. The widgets' 250 dp resize cap is gone. Arabic hits carry the reader's translation (not
under an Arabic UI) and light the matched words — whole words, colour only, after a fold-only
matcher turned out to miss a quarter of them and a word aligner between Tanzil's two spellings
replaced it. That measurement exposed a 1.0.0 bug: Arabic queries containing «أ إ آ ى» found
nothing, because the row was matched unfolded against a folded query. The bar's line takes a tap
and lands on the ayah's start, like the lock screen's scrub. Tahajjud is an optional
notification at the last third of the night, off by default, with channels of its own. Spec §17.

The store screenshots are checked in now (`docs/store/screenshots`, 105 files, 37 MB, lossless):
`assemble.sh` writes there, both capture scripts take `ONLY="2-notifications"` to re-shoot one
screen, and `git status docs/store/screenshots` is the upload list. The harnesses gained
`search`, `tahajjud` (on, off, or fire one in eight seconds) and, on iOS, `pending`. The site
and README mention Tahajjud in one clause, and Support has a new entry for the silent phone.
Release notes for Play are in `docs/store/release-notes/1.0.0-29.txt`.

## Build 30 (19 September)

Two things from Mohamed's morning look at build 29, which was never uploaded: 4 dp of air
between a row's label and its subtitle (the Tahajjud row showed it worst; the fix is in the row
component, so About, the method list and the sound sheets get it too), and the Quran search
clears when the reader leaves the tab for Prayer or Settings. The Notifications store shot was
re-taken on the S23 and the simulator (14 files); the release notes moved to `1.0.0-30.txt`.

## A tester's phone restarted (21 September) — 1.0.0 (31)

A LoopDL loopTwo on Android 16 restarted itself half an hour after installing build 30, and the
report blamed this app. It was right. The device's own log and crash record, read over adb,
showed two faults: the download notification posted on every progress event from two workers at
once (shed 115 times in thirteen minutes), and — the one that killed `system_server` — every
queue item carrying the launcher icon as `artworkData`, which Media3 turns into a one-megabyte
bitmap per platform queue item: 571 of them for Al-Baqarah, on every queue change. The artwork
is a content URI now, the platform session publishes no queue, progress goes through one gate,
and a worker that is going to be refused says nothing first. Spec §18, which also says what is
left: WorkManager's hand-overs still touch Android's limit on a very fast link, and the cure for
that is one notification-owning worker per batch.

## The Mushaf page on a short screen (21 September) — 1.0.0 (32)

The same tester's phone — 408 × 760 dp, three-button bar — drew the last four lines of a full
Mushaf page on top of each other. The page's size came from the frame's width and nothing held it
to the height. `fitToHeight` now does: close the lines up from 1.9× to no tighter than 1.6×,
then shrink the type in half-steps, then scroll. Reproduced and fixed on the emulator set to the
phone's exact size, density and navigation mode; tall phones are unchanged. Spec §19.

## The first App Store archive (22 September) — iOS 1.0.0 (32)

Mohamed's individual Apple Developer membership was approved; its Team ID lives in
`iosApp/Configuration/Config.xcconfig` and nowhere else, and `scripts/ios-release.sh` refuses the
employer's team outright. The App Store identity is `world.taqwa.ios` (the Android package name
was registered to the employer's team by an early automatic-signing build, and Apple identifiers
are unique across teams). A fresh team has no registered devices and Xcode signs archives with a
development profile, so the first signed build went to the connected iPhone 12 with device
registration allowed: that created the certificate, the App IDs and the app group in half a
minute, and put the store build on the phone for its first hardware run. The archive followed in
thirteen seconds, read back as `world.taqwa.ios` 1.0.0 (32) under team 3K93P4PA5H with
`group.world.taqwa.ios` and a 1 MB widget; tag `ios-build-32`.

## Prayer times on taqwa.world, and the Jumuʿah pill (25 September)

Built overnight from the design Mohamed approved the evening before
(`docs/superpowers/specs/2026-09-25-taqwa-city-pages-design.md`, §10 for what changed on the
way). taqwa.world now has a page per city and language with this month and next, an index per
language, and a section at the bottom of every home page, all rebuilt every six hours by the
Pages workflow with nobody touching it.

**The times are the app's.** `tools/timetables` compiles the app's engine, Qibla and Hijri files
by path and runs the app's own tests for them on the JVM before it writes anything; a change
that breaks them stops the site build. On Friday 25 September the Tripoli page and the app's
Prayer screen on the emulator read the same: 5:35, 13:00, 16:24, 19:00, 20:17, 12 Rabiʿ
al-Thani 1448, 109° and 2,916 km. Against Aladhan, for twenty cities on 25 September and 26
October, every time agrees within a minute except Asr, where the app is within a minute of the
true Asr worked out from the sun's position and Aladhan is one to three minutes late at higher
latitudes. The app's Dhuhr is a minute after Aladhan's by design (adhan2's method offsets).

**Only 51 cities are live, on purpose.** A page promises what the app shows a user there, so a
country goes live only where that also agrees with the timetable its mosques follow. Each was
checked against the authority's own numbers for September and October 2026 (app minus official,
largest gap in minutes):

| Country | Timetable checked | Gap | |
|---|---|---|---|
| Egypt | Survey Authority via [Dar al-Ifta](https://www.dar-alifta.org/ar/prayer), all of September | 2 (Dhuhr) | live |
| Saudi Arabia | [Umm al-Qura](https://www.ummulqura.org.sa/ar/prayer-times), second-hand copies | 1 | live |
| UAE | Awqaf's Dubai table as published by Khaleej Times | 2 | live |
| Kuwait | Ministry of Awqaf calendar | 1 | live |
| Türkiye | [Diyanet](https://namazvakitleri.diyanet.gov.tr/), eight cities, October | 2 | live |
| Singapore | [MUIS](https://data.gov.sg/datasets/d_d441e7242e78efc566024dd5b0d9829c/view), all of 2026 | 1 | live |
| USA, Canada | no authority; ISNA mosques in New York and Toronto | 3 | live |
| South Africa, Cape Town | [Muslim Judicial Council](https://mjc.org.za/quick-resources/salaah-times/) | 1 | live |
| Libya | Awqaf and Dar al-Ifta ([api.ifta.ly](https://api.ifta.ly/api/v1/prayer-time)) | 7 (Isha) | held |
| Qatar | [Calendar House](https://www.qatarch.com/) | 3 (Maghrib, Isha) | held |
| Bahrain, Oman | Supreme Council calendar; [Ministry of Endowments](https://www.mara.gov.om/) | 4; 6 | held |
| Jordan, Palestine | [Ministry of Awqaf](https://www.awqaf.gov.jo/ar/Pages/PrayerTime); [Gaza Awqaf](http://palwakf.ps/ar/praytimes) | 7; 7 | held |
| Lebanon, Iraq | Dar al-Fatwa; Sunni Endowment (both also split by sect) | 10; 5 | held |
| Syria | Awqaf, Ramadan 1447 imsakiya (nothing newer) | 2 (Maghrib) | held |
| Morocco | [Habous](https://www.habous.gov.ma/prieres/index.php?ville=58) | 6, and the clock (below) | held |
| Algeria, Tunisia | [ministry calendar](https://marw.gov.dz/); [meteo.tn](https://www.meteo.tn/) | 4; 7 | held |
| Sudan, Mauritania | Ramadan imsakiyas only | 6 (Fajr) | held |
| Pakistan, India | [Binori Town](https://www.banuri.edu.pk/namaz-times) (Hanafi) | 1, but the app's Asr is Standard | held |
| Bangladesh | Islamic Foundation (Hanafi, + 3 min) | 3, and the Asr | held |
| Indonesia | Kemenag via [myQuran](https://api.myquran.com/) | 3 | held |
| Malaysia | [JAKIM e-solat](https://www.e-solat.gov.my/) (Fajr 18° since 2019) | 12 (Fajr) | held |
| Uzbekistan | [Muslim Board](https://muslim.uz/) (15.5°, Hanafi) | 49 (Asr) | held |
| UK | [London Unified Prayer Timetable](https://www.iccuk.org/page.php?section=media&page=unifiedpt) | 24 (Isha) | held |
| France, Netherlands | no shared timetable (Mawaqit mosques use 12° to 18°) | up to 34 | held |
| Belgium, Germany, Russia | [EMB](https://www.emb-net.be/); DITIB and Diyanet; [DUM RF](https://dumrf.ru/) | 7; 9; 18 | held |
| Bosnia, Kosovo, Albania | [vaktija](https://api.vaktija.ba/), BIK, KMSH calendars | 7 to 11 | held |
| Johannesburg, Pretoria, Durban | [Jamiatul Ulama](https://jamiat.org.za/salaah-times.php) (Hanafi) | 57 (Asr) | held |
| Australia | Lakemba Mosque | 17 | held |

Countries with no timetable found or not checked yet (Yemen, Nigeria, Senegal, Somalia, the rest
of Africa, the Maldives, Brunei, Ireland, Austria, Switzerland, Scandinavia, New Zealand) are held
too. Each held city is in `site/cities.tsv`, commented out under its reason, and comes back by
deleting its `# ` once the app can match. Most of this list is the app's to-do: a Libyan Awqaf
method, Kemenag's margin, JAKIM's 18°, the Maghreb ministries' offsets, and above all a country
default for the Asr school.

**What the pages found in the app:**
- **No Hanafi default.** The app's Asr is Standard for everyone until changed by hand
  (`SettingsRepository`), so a new user in Karachi, Delhi or Dhaka sees an Asr about fifty
  minutes before their mosque's. A country default like the method's would fix the app and bring
  back three of the site's languages' home countries.
- **Umm al-Qura in Ramadan.** Its Isha is Maghrib + 120 in Ramadan and the app keeps 90, so from
  8 February 2027 the app would show Isha half an hour early in Saudi Arabia. The pages add the
  half hour on Ramadan days and say so under the month, and stop doing it by themselves once the
  engine returns 120; the app needs the rule before Ramadan.
- **Clocks the JDK does not know yet.** Morocco left UTC+1 for UTC+0 on 20 September 2026, and
  Alberta stops changing its clocks after 2026 (both tzdata 2026c). The JDKs here have 2026b, and
  phones get zone updates on their own schedule. The site reads every clock from the newest
  tzdata package and rewrites a city's times where the JDK is behind, noting it in the run: with
  Alberta that happens from 1 October, when November comes into view.
- **The distance in French, Turkish and Indonesian.** `localizedGroupedKm` groups with a comma in
  every language, so "2,916 km" reads as a decimal there. The generator's `Formats.distance`
  mirrors it by hand; both should change together.
- **The wrong day in the Pacific.** Where the clock runs far ahead of the sun (Samoa, Tonga,
  eastern Kiribati) the engine answers a date with the next day's times; seen in Apia by the
  Jumuʿah work, and now a generator guard.
- **City names**: Al Khums is "المرقب" and Al Bayda "قرية البيضاء" in the Arabic file, Delhi is
  "پرانی دہلی" (Old Delhi) in Urdu, Kolkata and Khulna are in Latin letters in Bengali, several
  Indonesian names carry "Kota", Zawiya is in the file twice. `site/cities.tsv` corrects them for
  the pages.

**The Jumuʿah pill in the app.** On a Friday the Dhuhr row of the Prayer screen carries a small
outline pill, in the accent at 40 %, with `today_jumuah` in all seven languages. Friday is read in
the location's zone, not the device's: a phone on Thursday evening showing Jakarta marks Jakarta's
Friday Dhuhr. The name group became a `FlowRow` so the time keeps its line at large font sizes on
narrow phones, which also fixes a Maghrib wrap at 200 %. Six new tests; `scripts/test.sh` green
(shared 964 JVM and 884 iOS, widgetcore 90 and 88). The same work noticed that most Latin text
in the app is not actually Manrope: 142 `Text(style = TaqwaText.*)` calls bypass the theme's font.

**Kept honest by the build.** `site/build.py --check` now also fails on a city page without both
months or a row for every day, an index that misses a city, an hreflang the other page does not
return, and a link to an anchor that does not exist; each rule was proved by breaking a built
page and watching it fail. An independent review of the branch found that the first versions of
the zone and Ramadan guards would have stopped every page updating on 1 October and 1 January;
both now correct the one city instead, and were run against data built for those dates. The page
script was run in headless Chrome with the clock pinned before Fajr, on a Friday afternoon,
after Isha, on the first day of the data, across Egypt's clock change and past the end of the
data; the index filter with Latin, accented, Arabic and Turkish input.

## The compass on an iPhone (25 September)

Two iPhones in testing kept saying the compass needed calibrating, or that there was magnetic
interference, while Apple's Compass on the same phones pointed without complaint. The iOS rules
had never met a real iPhone: the 10 September sweep could not unlock one. A diagnostic build (a
local branch, never merged, installed as a separate app) logged every `CLHeading` beside Core
Motion's calibrated and raw fields through 53 seconds of ordinary use in Cape Town. Core Motion
rated the calibration High on all but one of 984 samples and `headingAccuracy` ran 10–33°, never
negative; the app hid the needle for 81% of the session. Three rules did it. A 20° bar sat inside
ordinary use and fired on 17% of samples. A 20–70 µT band was applied to `CLHeading`'s x/y/z —
which is the calibrated field, matching Core Motion's to the decimal — and in Cape Town's
25.6 µT field that read 13–20 µT for long stretches, so 61% of samples were called interference,
the last sixteen seconds of them at the phone's best accuracy of 10°. And under the default
one-degree heading filter a phone held still delivered nothing for 14.5 s, which the gate counted
as low until it gave up.

The iOS verdict is now Core Location's own: an invalid heading, an invalid accuracy, or an error
worse than 45°; no field band, so interference is Android-only again, where it still catches the
LoopPhone; and every heading event, not every degree. The recorded session is a test fixture that
must never leave `Good`. The LoopPhone's prompts, read over adb the same night, were the truth: its
rotation vector is a stub and its magnetometer's calibrated field sat at 60–65 µT on the desk,
two and a half times the Earth's. Spec, "Amended 25 September 2026".

The same investigation turned up a quieter fault: Core Location's true heading needs a location
fix on the manager delivering headings, so anyone who picked their city by hand and keeps location
off was told, forever, that the compass needed calibrating. iOS now corrects the magnetic heading
itself with the declination the World Magnetic Model 2025 gives for the city on the Qibla screen —
NOAA's public-domain model, ported with its coefficient file byte for byte, reproducing all 100 of
NOAA's published test points to a thousandth of a nanotesla — so the compass works with location
off and asks iOS for no location at all. For Cape Town it gives −26.79°; the iPhone had applied
−26.8° itself.

## The city pages held: a minute early is a prayer before its time (25 September)

The first check allowed about two minutes either way. Mohamed asked the harder question: does a
page ever show a prayer *before* the official time? A Maghrib a minute early is a fast broken
before sunset; any start shown early is a prayer before its time. The same official tables were
compared again with the sign kept (app minus official), and every live country failed somewhere:

| Country | Checked against | Days a start was shown early |
|---|---|---|
| Türkiye, eight cities | Diyanet, 31 days | Asr, Maghrib and Isha 1–2 min early on nearly every day |
| Singapore | MUIS, all of 2026 | 1 min early at every prayer on about a quarter of days |
| UAE | Dubai table, September | Maghrib 13 days, Isha 9 |
| Egypt | Survey Authority, September | Asr 11 days (Maghrib never) |
| Kuwait, Saudi Arabia | one day each, second-hand for Saudi | 1 min at several prayers |
| USA, Canada | MAC Toronto; ICCNY and Hikmah, New York | Asr 2–3 min before MAC's every day of October; ICCNY's Maghrib up to 4 and Isha up to 8 min later |
| Cape Town | Jamiatul Ulama's Cape Town table | Dhuhr 3–4 and Maghrib 2–3 min early every day |

Two causes. adhan2's presets are near but not on the authorities: Diyanet's Maghrib is sunset + 8
and its Asr about + 5 where the Turkey preset adds 7 and 4. And the engine rounds to the nearest
minute, so a start can be shown up to half a minute before the moment it describes. Measured to
the second against the raw astronomical times, most official tables are that raw time plus a fixed
number of minutes and a rounding rule, each inside a 60-second band (Diyanet, Egypt, Dubai,
Kemenag, Habous, Libya, Algeria, MAC Toronto), so a profile can reproduce them to the minute
without ever being early. MUIS, JAKIM and Jamiatul Ulama Johannesburg spread 100–175 seconds, and
the London Unified Timetable's Fajr and Isha are not angle-based at all.

Every row of `site/cities.tsv` is now held, each country under what was found, and while none is
live the build leaves the section out entirely: no index, no header or footer link, no home-page
strip (`PRAYER_TIMES_LIVE` in `site/build.py`). With the rows restored the new code builds all 154
pages byte for byte as before. The app shows the same times the pages did, so this is the engine's
to fix, not the site's.

## Reading the translation aloud (27 September)

An option, off by default: after the reciter recites an ayah, the phone's own voice reads the translation shown under it. Headphones on a journey: the Quran in Arabic and its meaning in your language, with no network and no cost. Mohamed decided three things before any code:

- Tafsir al-Muyassar is read too, with the Quran words it quotes dropped.
- The switch lives in the reading sheet, under Translation, and in Settings › Recitation.
- Both platforms ship together. Where a phone cannot read a language, the switch is not shown.

**What is read.** `SpeechText` turns the text on screen into the text a voice reads. It was measured against every row of quran.db:

- **Muyassar's quotations.** Muyassar marks its quotations in exactly three ways:
  - braces around verses quoted from elsewhere (9 of them);
  - fully voweled parentheses, which quote the ayah itself (6);
  - the disjoined letters at a surah's opening, e.g. (الم), (حم * عسق), and 29:1's bare الم:.

  Each is dropped with its marks. The roughly 190 other parentheses are explanation, and the voice reads them.
- **Shared texts.** Diyanet's Turkish repeats one translation over as many as 12 ayahs (570 runs), and Muyassar over as many as 14 (605). A shared text is read once, after the last ayah of its run.
- **Bengali.** The bundled Bengali has 42 rows with mangled HTML character references, such as "চিহিߦ#2468;". The voice gets them decoded. The data itself was repaired on main the same day (e9e5c62, quran.db version 5), so for the bundled text that decoding is now only a safety net.

**How it plays.** The queue per ayah is: the ayah, a 400 ms breath, the translation, then the reciter's gap.

- **Android.** The Media3 service turns each translation into a WAV with the chosen engine, on ExoPlayer's loading thread while the ayah before it plays. `AyahPlayer` hides translations from the session exactly as it hides gaps, so the notification and lock screen never change between an ayah and its translation.
- **iOS.** `AVSpeechSynthesizer` speaks as a timed phase between AVPlayer items.
- **The surah clock.** It includes each translation's estimated length. Otherwise Android's media controls, which extrapolate position, would run ahead and snap back after every translation.
- **Changes mid-surah.** Turning the switch or changing the translation mid-surah rebuilds the queue around the ayah being heard.

**Voices.**

| Phone | What it has |
|---|---|
| Emulator and S23 (Google's engine) | English installed. The other six languages are free packs, which "Get the voice" opens: French 23 MB, Arabic 4.2 MB. Samsung's own engine on the S23 offered none of the seven offline. |
| iOS 26.2 simulator | Voices for six languages (Samantha, Majed, Thomas, Yelda, Damayanti, and Piya for Bangla). Urdu has none, so Urdu hides the switch on iPhone. |

**Surprises worth keeping.**

- iOS 26.2 reports `didFinish` (not cancel) when speech is stopped. The player clears its current utterance first, and matches finishes by identity.
- `AVSpeechSynthesizer.paused` updates late.
- Android 16 refuses audio focus and a foreground service to a harness `load` sent while the S23 is locked, so device playback tests start from the app.
- The whole-branch review caught Settings › Recitation never asking about voices after a cold start, and an engine `shutdown()` that could leave the speech lock held. Its offline-only hardening then broke Android outright: Google's engine lists `networkTimeoutMs` and `networkRetriesCount` on every voice, the local ones included, so treating those keys as "network" left no voice at all. A device probe measured this, and the rule is now `!isNetworkConnectionRequired` and no `networkTts`, with the `-local` voice winning ties (`en-us-x-iob-local` on both phones). `OfflineVoiceTest` pins the measured feature sets.

**After the first test on the S23.** Mohamed's first listen sent back two bar bugs and a request, and measuring the request turned up a fourth problem:

- **The bar lost its word.** Under an English, French or Indonesian UI the caption read "Ayah 56 ·". At 12 sp, "Ayah 56 · Translation" wants about 120 dp; the caption has 108 on the S23 and 84 on a 360 dp phone. A line that wraps at spaces put "Translation" on the second line, which `maxLines` hides. `barCaption` now draws the widest of "Ayah 56 · Translation", "56 · Translation" and "Translation" that fits, measured at the caption's own type.
- **Arabic and Urdu cut the caption in half.** The column kept 8 dp above and below its two lines, which left 40 dp, and the surah name in the Hafs face took 30 of them. The column now centres the lines in the full 56 dp, and the name's line is 1.5 em. That needed `LineHeightStyle.Mode.Tight`: under the default mode Compose adds a single line's lost height back as padding, so the name stayed 30 dp whatever line height it was given. A layout log on the phone showed 96 px with the stand-in font and 112 px once the Hafs face had loaded.
- **A long ayah ran under the bar.** A followed card rested a third of the way down, whatever its length. `followOffset` keeps that line for a card that fits below it and raises a longer one until its foot is 12 dp above the bar. Nothing goes above the top, so a card taller than the screen starts at the top. Measured on the S23: 697 and 1,275 px cards rest at the line, 1,564 px cards end 12 dp above the bar, and 2:61, taller than the screen, starts at the top.
- **Following skipped short ayahs.** The list reports that it has stopped a frame after the page's own scroll returns, so every follow counted as a touch, and an ayah arriving within the next four seconds was not followed. On the S23, 2:72 arrived 1.5 s after 2:71 and was left under the bar. `FollowingState` now ignores the stop of a scroll of its own that finished. A finger that takes over mid-scroll still counts.

**Built with.** Spec `docs/superpowers/specs/2026-09-27-translation-read-aloud-design.md`, plan `docs/superpowers/plans/2026-09-27-translation-read-aloud.md`, and seven subagent tasks, each with its own review.

## The never-early prayer engine (27 September 2026), branch `prayer-engine`

The answer to "The city pages held" above: a new engine, built from the design at
`docs/superpowers/specs/2026-09-26-taqwa-prayer-times-engine-design.md` (approved 27 September,
implemented the same day, tested by Mohamed on his phones on 28 September and squashed into main).

**What it is.** For every place, Taqwa now reproduces the authority its own mosques follow — its
sun model, its rounding, its margins — at that authority's own reference point, and takes the
later of that and the user's own point for every start, the earlier for sunrise and the end of
eating. Where several timetables are in local use with no clear majority, it shows the latest of
them (cautious times). Where nothing is known, it falls back to a documented convention, always on
the safe side. Nothing is ever shown beginning before the time it is describing.

**How it is proven.** A gate runs the shipping engine against every official table Taqwa holds
locally (not just the sample used to fit it) and checks, per place and per day, that no start is
early and no end (sunrise, end of eating) is late. The final run: **71 entries, 137,468
place-days, 0 starts early, 0 late ends, 0 broken rows.** Each entry gets a class from that
evidence (A/B rebuilt and proven over a year including Ramadan; C cautious times over proven
members; D the authority's own method at a tested safety margin, not yet fully proven; one entry,
`default.safe`, is Taqwa's own fallback where nothing local is known at all). By region:

| Region | Entries | Place-days | Classes present |
|---|---|---|---|
| Saudi Arabia & the Gulf | 7 | 39,894 | A, B, B/D, D |
| Türkiye, the Caucasus & the Balkans | 7 | 18,475 | A/D, B, D |
| Russia & Central Asia | 5 | 7,792 | B, D |
| Levant & Iraq | 6 | 549 | A, B, D |
| North Africa & the Nile | 7 | 6,622 | B, B/D, D |
| South Asia | 3 | 192 | D |
| Southeast Asia | 4 | 15,685 | B |
| Western & northern Europe | 16 | 9,680 | B, C, D |
| North America | 8 | 20,370 | C, D |
| Oceania | 3 | 851 | C, D |
| Southern Africa | 4 | 15,304 | B, C, D |
| No authority anywhere | 1 | 2,054 | D (none) |

**What was built, by task:**

| Task | What it built |
|---|---|
| 1 | The astronomy core: exact sun position, the Asr models, transit (`SunClock`) |
| 2 | The day model: methods as data, rounding, Ramadan lag dates, imsak and the end of eating (`DayComputer`) |
| 3 + 4 | Real times at any latitude with an estimate only when a sign is missing; cautious times over several timetables; a day whose order always holds (`HighLatitude`, `Cautious`, `Ends`, `Invariants`) |
| 5 | The registry: every authority's units, methods and reference points, point tables, day-of-year curves, Algeria's wilayas, London's curve |
| 6 | `DayPipeline`, the one path from a resolution and a place to a day, and the gate that checks it against the archive |
| 7a–7h | Every authority's own timetable, region by region, in eight parallel subtasks: the Gulf and Egypt; Türkiye, the Balkans and Russia; Singapore, Malaysia and Indonesia; the Levant; South and Central Asia; the Maghreb and Libya; Europe, the Americas and South Africa; the "Other methods" and generic conventions |
| 8 | `PrayerEngine`: settings, adjustments, caching, migration and rescheduling wired to the new engine |
| 9 | The settings model and its migration, run ahead of Task 8 |
| 10 | The Prayer screen: the ⓘ card, "set by rule", the polar-day line, the clock-mismatch line |
| 11 | About these times, and the stamp-to-string generator (`ProofStamps`) |
| 12 | Settings › Prayer times: the Timetable and Match my mosque screens |
| 13 | Every new string in all seven languages, and a translation-quality pass |
| 14 | Device verification: Android emulator and iOS simulator, city by city |
| 15 | Wrap-up: the final fix round, a full gate refresh, and this log |

**Notable engine findings:**

- **The SunClock fallback.** A twilight the sun only just reaches (within about a hundredth of a
  degree) was sometimes reported missing, because the search started from the wrong side of local
  noon and never crossed it. Retrying from the sun's lower culmination finds the real crossing
  instead of falling back to an estimate — checked against 986,772 brute-force altitude scans
  across 44–66°N, five twilight angles and two years, zero misses.
- **DUM RT's summer rule.** It first ran on a fixed calendar window (6 May – 8 August). Because the
  date each town's 18° dawn actually disappears moves with the town and the year, the fixed window
  put some southern towns' Isha tens of minutes early and Suhoor over an hour late where the real
  dawn was there all along. It now runs on whichever nights each town's own dawn is genuinely
  missing, computed per point, not read off a calendar.
- **The 20-minutes-a-day limit.** The original design's 7-day "ramp" is gone. In its place: while a
  sign is missing, the shown Isha may not move earlier than the day before by more than 20 minutes,
  and the end of eating may not move later by more than 20 minutes — checked over the whole
  lookback, not just up to the first day found, and applied to a value borrowed from a substitute
  latitude on a polar day as much as to a local estimate. Fajr moving later is always the safe side
  and is never limited.
- **Point tables.** A cautious member or a single-authority timetable now carries its own reference
  point. Starts are the later of that point's and the user's own; sunrise, the end of eating and
  imsak are the earlier. Past the point where that stops being a fair comparison, the table's point
  still bounds the ends only, while starts fall back to the user's own point at the entry's edge
  margin — so leaving a table's practical reach still stops a user eating by it, without handing
  them a start time the table never measured.
- **Curve envelopes.** Authorities with no reproducible model of their own (IRN, EMB, Diyanet
  Europe, DUM RF, ICCI, GMP, the LUPT corners) get a 366-slot day-of-year curve indexed on a
  leap-year reference, each value an envelope over its neighbouring days — the smallest plausible
  depression for a dawn, the largest for a dusk — so the four-year leap cycle can never drift a
  start early. Every curve file is committed as that derived envelope, with its source recorded;
  none of the authorities' own printed tables are committed.
- **Libya's seasonal margins.** The first fit used one day's margins (25–26 September) at 21
  cities. Checked against Tripoli's own widget and prayer-times API across 23 days spanning March
  2025 to October 2026, most margins needed to move across the seasons, and the end of eating
  needed the tightest refit of all (a late end of eating is the costliest place to be wrong). The
  refitted margins hold every Libyan city never-early across the whole year, not just the day they
  were measured. The owner's own Benghazi and Sabha mosque observations still run a few minutes
  ahead of the national method Taqwa uses everywhere in Libya — his decision (spec §10.3) was to
  keep the national method, which is never early against it, rather than move the east and south to
  local practice.

**What remains** (as of 27 September; the completion below settled the decisions and the Tromsø
and Longyearbyen limitations):

- **Owner decisions.** Nordic summer lateness where a cautious member's table doesn't reach that
  far north (Isha over two hours later in places at 48°+ in midsummer); US, Montreal and Ottawa
  mosque floors costing 5–7 minutes at Dhuhr and Maghrib against every held table; Chicago now a
  cautious entry (four of its seven held tables use 15°); the UK's 12–14° group and France's 15°
  family running summer Isha up to 38 minutes later than most; IISC Calgary's 10°/10° table, where
  the app is up to 32 minutes ahead of its own Fajr; ICCI's end of eating running up to 80 minutes
  before its own printed Fajr under the current curve, where a small margin change would close most
  of the gap.
- **Known limitations.** Tromsø against IRN's own Tromsø calendar: IRN prints artificial figures
  for the polar period there, and Taqwa's real-sign engine runs far ahead of them (an early Fajr, a
  late Maghrib, by hours); IRN's Tromsø calendar isn't modelled, so Norway's cautious entry is
  marked unmeasured there. Near-threshold edges: places within around two-tenths of a degree of a
  defining twilight angle (DUM RT's edge with no fixed point, the UK's cautious entry in high
  summer, IRN north of about 69°) are position-sensitive enough that a few kilometres can flip
  which sign is "real"; each is banded to its safe side rather than matched exactly. The
  Longyearbyen polar Maghrib against the next end of eating: on 9 Longyearbyen days and 3 at Bodø,
  the cautious combination's borrowed polar Maghrib itself falls at or after the next day's end of
  eating, and the 20-minute limit can't move it without an early Maghrib or a late Fajr — it needs
  its own ruling. Separately, some Other methods' own Maghrib+90 Isha rule puts their Isha after the
  next Fajr at Tromsø and Longyearbyen on many summer days; that is those methods' own rule, not an
  engine gap.
- **The website slice** (spec §7): city pages for proven places, the "How Taqwa checks" page, the
  detailed view — after the app, as Mohamed asked.
- **The Ramadan release items** (spec §7): "Suhoor ends" on the Prayer screen, iftar, the printed
  imsak precaution, the suhoor alert, the Ramadan widgets and their highlight change, Tahajjud
  ending at the end of eating, "I'm fasting today".
- **The weekly monitor** (spec §5): a scheduled local job that fetches each entry's newest published
  table and re-runs the gate on it, opening a task on a failure. Needs the stamps this branch adds
  (done) and a local scheduled job (not yet built) — after launch.

### Completion (28 September 2026)

Mohamed delegated the pending decisions ("make the best judgement calls"; never early first, good
accuracy where most Muslims live). A research pass (Sudan, Tromsø, the UK, a Mawaqit spot survey of
France, Belgium, the Netherlands, Germany and Canada, coverage by Muslim population, the launch
requirements) was followed by three waves of parallel tracks, each reviewed and merged into
`prayer-engine`, then a final whole-branch review and its fixes. Rulings R73–R92 (spec §12).

**What changed.**

- **Eastern and southern Libya** follow the local adhan Mohamed observed (R73): Fajr at the 19.5°
  dawn, Maghrib at sunset + 1, an exact fit at Benghazi and Sabha; the gate holds his two
  observations as an open table. This reverses the 27 September decision above.
- **Sudan** keeps its angles and gains a Ramadan-only Isha floor of Maghrib + 90 (R74).
- **The UK outside London** is never early against all 62 surveyed Mawaqit calendars (R75): a
  late-dawn member, Karachi's end of eating at the 18.6° dawn or mid-night, Scotland's Asr minutes;
  cost, summer Fajr 15–28 min later in England, the end of eating 47–147 min earlier May–August.
- **North America** keeps its mosque-table floors, Chicago stays cautious, Canada's three families
  apply beyond the held tables (R76–R78); France's 15° family and the Nordic summer lateness stay,
  recorded (R79).
- **GMP and ICCI's end of eating** is the authority's own dawn as an end (R80); the envelope curves
  stay (R81).
- **Tromsø** follows IRN's Makkah-time calendar as an authority clock rule (R82): the app shows
  IRN's times wherever the sun allows and IRN's whole Makkah day where it neither rises nor sets;
  what no day in order can show (a Fajr after sunrise on about fifty summer days, a sunrise at the
  sun's lowest point) is declared, and About says so. The Longyearbyen and Bodø nights whose Maghrib
  or Isha reaches the next Fajr get no alert and never a passed "next" (R83).
- **Proof and release**: a golden vector of about 2,000 invented place-days checked on Android and
  iOS (R84), a benchmark guard on the worst cautious place-days (R85, worst 1.14 ms on the Mac), and
  both release scripts refusing a stale or red stamp (R86).
- **Continental Europe** (R87, R88): the Netherlands gains a Maghrib floor by month, France gains
  Diyanet Europe, Germany gains a late-dawn and an 18° member, Belgium keeps the spec's Maghrib cap at
  EMB's own minutes; a point table's final end bounds the ends across its reach (R89).
- **The final review's fixes**: no repair pulls Fajr before a real dawn at the polar edge (R90: the
  sunrise precaution gives way before Fajr does; what the sun itself overtakes is declared), About
  names the Maghrib cap on the days it decides (R91), and the Isha cap reads the next day's end as
  shown (R92).

**Final figures.** The gate over the whole archive: 588 rows, 137,835 place-days, 0 starts early,
0 late ends, 0 broken; all 71 stamps fresh and `checkStamps` green. The surveys, each repeatable
from the restricted archive: the UK's 62 calendars (22,630 place-days) and the continental 54, with
every early or late calendar day either fixed or recorded in the survey's outliers (0 unrecorded).
The Arctic walk of 2026 at nine places and the Tromsø reach: no day repaired for Fajr (from 8–75 a
place before R90), no Fajr before a member's own Fajr or real dawn.

**What remains.**

- **Diyanet Europe beyond its city tables**: its takdir is read from the nearest city curve, 4–5 min
  early at Fajr in Ghent and Antwerp from April to August and its carried imsak 7 min late at Lyon
  in early summer; fetching Diyanet's own tables for those cities (and Copenhagen, Helsinki,
  Trondheim) is the next slice.
- **Nordic summer lateness**: where Diyanet Europe's takdir applies beyond its tables (Trondheim,
  Helsinki, Copenhagen) Fajr and Isha run up to 118 and 155 min after the other member's calendar;
  never early, the user's way out is Timetable or Match my mosque.
- **The Arctic residual**: in the week the sun stops setting, at Finnsnes, Narvik, Kirkenes, Vadsø,
  Kiruna, Rovaniemi and Murmansk the sun has risen by every member's Fajr on up to nine days a year
  (Fajr is the minute before sunrise, declared), and on one to three days the members' sun models
  disagree on whether the sun set at all, so the sunrise shown (the shown-Fajr member's own) is
  after the other model's by up to 47 min.
- **Population coverage gaps**: South Asia per city (Pakistan, India, Bangladesh have three class-D
  entries and 192 place-days between them), Indonesia's other kabupaten and kota beyond the held
  tables, sub-Saharan Africa (Nigeria, Ethiopia, Niger, Mali, Senegal and the rest fall to the
  generic convention), and Iraq, Syria and Yemen, where no official table could be obtained.
- **The weekly monitor**, the **website slice** (city pages for proven places, "How Taqwa checks")
  and the **Ramadan release** items above, in that order after the owner's device test.

Tested by the owner on 28 September 2026 (Cape Town and London checked against published sources:
nothing early) and squashed into main as one commit; the weekly monitor follows.

### The weekly monitor (28 September 2026)

`scripts/monitor.sh` (docs/MONITOR.md): fourteen polite fetchers bring each authority's newest
table into the restricted archive, each new or changed table goes through the gate at its own
point, then the whole gate and the six surveys, the data horizons and the manual sources' due
dates; the archive is mirrored into its backup; a report under the archive root's `monitor/`
folder, silent (exit 0) when all is well.

### The monitor's fix round, and the cloud (29 September 2026)

After its review (8 important, 15 minor findings) the monitor classifies each finding line on its
own (ruling R93): never-early failures first on every run, own-table lateness raised once and
carried, member-row lateness and unchecked capped Maghribs for the record; a table is also checked
as the member of the cautious entry Automatic follows at its point; each table's check signature
(content, metadata, the whole engine) is remembered, so nothing is skipped for good and an engine
change re-checks everything; a key never reaches a log; a breaker per host and a budget per run;
one lock, one `today`, atomic writes. It now runs weekly on GitHub Actions in a private archive
repository (ruling R94; `tools/timetables/monitor/ci/monitor-weekly.yml`, docs/MONITOR.md), keeping
one issue open while something needs attention; IRN is fetched on its own (ruling R95).

## The city pages, rebuilt on the engine (29 September)

Built overnight from the design Mohamed chose the evening before — B ("show the working") with C's
month table — under `docs/superpowers/specs/2026-09-29-city-pages-design.md` and its plan, six tasks
each reviewed. taqwa.world's prayer-time pages are live again, on the never-early engine.

**What a page is now.** The Today card is the app's Prayer screen — the same six times, the same
countdown ring in the same digits, lit for the reader's day in the city's own time zone — with a quiet
foot saying so. Under it the authority line and the stamp's proof sentence stay visible
("{Authority} timetable · reproduced and checked by Taqwa · Taqwa is not affiliated with …";
"Checked against …'s published timetable through …"), and "Where these times come from" folds the
rest behind a native `<details>`: who publishes the times, how Taqwa reproduces them in the app's own
words for the method, and how it was checked — the stat tiles (place-days at places, starts before the
timetable, at most N minutes after) and the minute ruler, every figure read from the stamp, none typed
(R97). A cautious place shows its members, "which timetable decides each time today" with a chip per
member, the Maghrib-cap sentence on the days it applies, the drawn Fajr ruler, and two tiles rather
than three (R105: the entry-wide worst would be another place's spread). The months are C's real
tables: past days folded at every width, Fridays marked, a **detailed view** switch that adds the end
of eating under Fajr, the other school's Asr under Asr and a mark on days set by rule, and **Print or
save as PDF** per month from a print stylesheet that prints every script. **How Taqwa checks** is a
page per language from the stamps and the gate files: the rule, the gate's totals, the surveys, and
the table of every timetable with a page (spec §5; the index links it, every city page does). The
get-the-app places — the card's foot, the desktop pitch, `#app` at the foot — show their coming-soon
state until `site/stores.json` has an address, when the official badges appear with no other change
(R98); the launch note in `docs/STORE-CHECKLIST.md` names all three. Small amber text uses the darker
`--accent-text` (4.5:1 on the page, on white and on the today tint; R99); figures read as the app's
digits, ungrouped (R107). The app's cautious About screen gained the same two tiles and the sentence
"Checked against the timetables followed here." in seven languages (R100, R111, R112).

**Which cities.** A row of `site/cities.tsv` is un-held only where the stamps prove every day its
page shows: class A, B or C at the city, measured there, a green stamp for the entry and its unit,
and every shown date inside the days the gate actually checked for every timetable the page depends
on (R101, R115, spec §2; the stamps now record those days as runs, per unit and per cautious member,
because a stamp's first–last span hides holes such as Diyanet's 26 October – 31 December 2026). The
generator applies the rule on every build and holds a row it rejects with a notice, so pages come and
go with the proofs and nobody edits the list to chase them. `cities.tsv` un-holds 47 rows; the release
build publishes **41 cities on 29 September and 38 from 1 October**: İstanbul, Ankara, Paris, Oslo,
Brussels and Antwerp wait for Diyanet-family tables covering the whole shown months (theirs begin
25 September and skip late October to December), and from 1 October Toronto, Mississauga and Chicago
wait for November's missing days. Held, in one line each in the file: the class-D countries whose authority's method is
not yet fully checked (the Gulf beyond Dubai, Doha and Muscat; the Levant, Iraq, Libya, Sudan,
Mauritania, Uzbekistan, Pakistan, India, Bangladesh, Kosovo, Moscow, Austria, Switzerland, ISNA's
cities, Auckland, and the cities outside a checked unit — Salalah, Irbid, Sousse, Oran, Constantine,
Mansoura, Tanta, Assiut, Marrakesh, Fes, Agadir, six Turkish cities), the places with no authority
where Taqwa calculates (Yemen, Maldives, the rest of Africa), the stamps that end before the days
shown (Egypt, Jordan, Palestine, Morocco, Cape Town), and the cautious entries not measured at the
city (the UK beyond London, France beyond Paris, the Netherlands, Germany, Sweden, Denmark, Calgary,
Edmonton, Vancouver, Sydney, Melbourne). London Unified's stamp ends 31 December 2026 and so do
Doha's, Singapore's, Kemenag's, JAKIM's, Brunei's, Sarajevo's, Kazan's, Dublin's, Chicago's and
Toronto's: gate the 2027 tables before December or those pages hold themselves on 1 December.

**Surprises on the way.** Headless Chrome on this Mac will not lay a window out narrower than 500 px,
so every phone capture goes through a 390-px iframe (the plan's command gave a cropped desktop
layout). The integration merge of Tasks 2 and 3 lost one `}` and nested the whole months block inside
the desktop media query — nothing of the months' phone rules or the print stylesheet applied on a
phone until Task 4's capture found it. The app never groups thousands, so the tiles read "4015" (R107).
Toronto's English page is en-CA and its date read "December 31, 2026" beside London's "31 December
2026" on the checks table, so that page now dates every row in the language's own form (R114). A
printed month fitted A4 but spilled a blank sheet on Letter (and Bengali on A4 too) once the page's
own head printed above it; 2 px cells at 1.25 line-height fit every script on both. The words of the
detailed view's second lines live in the column heads on a phone ("Fajr / eat by"), because in the
cells they pushed Isha off a 390-px screen; on paper the word comes back beside the time. The
`<ol>` of steps numbered every nested list until scoped. "Kıble Kuzeyden 151°" read with a capital
mid-line; now "Kıble kuzeyden 151°".

**Still to do.** Native readers' glance at the Turkish, Bengali and Urdu sentences added tonight;
a routine to gate each authority's 2027 table as it appears, with the monitor; the accessibility
audit's follow-ups on the app's About table.

## Build 33 on both platforms (29 September) — 1.0.0 (33)

The first build with the never-early prayer engine. App Review approved iOS 1.0.0 (32) on
29 September, but 32 still calculates with adhan2's presets, the engine the audit of 25 September
found showing prayers before the official time in places, so it is never released: its release is
cancelled and 33 goes to review in its place, to be released by hand when approved (a soft launch
on iOS while Google's 14-day closed test runs out). Play's closed track gets the same build.

Since 32: the engine (28 September) with the next day's Diyanet Europe and Kemenag fixes and the
cautious places' proof tiles in About these times; the Jumuʿah pill on Friday's Dhuhr; the
translation read aloud by the phone's own voice after each ayah; the Bengali and Indonesian rows
Tanzil ships damaged, repaired; the iPhone's Qibla on true north from the saved city; credits that
open their source. Play's release notes are in `docs/store/release-notes/1.0.0-33.txt`. The gate
holds 654 tables and 155,886 place-days with none early; the release scripts refuse to build on a
stale or red stamp.

## Build 34 (29 September) — 1.0.0 (34)

Build 33 with one line more: Compose Multiplatform 1.12.1, whose release fixes a crash when iOS
reads an accessibility element after its node is gone (JetBrains/compose-multiplatform-core#3403),
which VoiceOver, Voice Control, Switch Control and Full Keyboard Access users could hit while
moving between screens. Taken alone from the accessibility round's branch: the round's other work
(the Mushaf at any font size, controls' names and states, VoiceOver's escape gesture) waits for its
device pass and ships as the first update. Nothing had been reviewed yet, 33 was never submitted,
so 34 goes to App Review in its place at no cost in time; 33 stays unused in TestFlight. The whole
suite passed on it (shared 1,595 Android host and 1,474 iOS tests, widgetcore 91 and 89). Play's
release notes, with the never-early claim scoped to the days checked, are in
`docs/store/release-notes/1.0.0-34.txt`.

## A city page shows the months proven, one or two (30 September) — ruling R116

The owner's ruling of 29 September: a page shows the current month when every day of it is checked
against every timetable it depends on (R115), and the next month too only when every day of that is
checked as well; otherwise the current month alone, until the next month's table arrives and is
checked. A city whose current month is not wholly checked stays held, a partly checked month is never
shown, and the standard of proof is unchanged (spec §2, amended). Until now both months had to pass, so
a city whose authority had published only this month was held, and Toronto, Mississauga and Chicago
were to come off on 1 October over a few unchecked November days.

**The generator.** The verdict yields the proven months or a hold, and for a month left off the first
timetable leaving a day of it unchecked, with that day. The document carries only the proven months —
the months list, the days, every page's texts, so the live data too — with that reason as
`nextUnchecked`, and each day's long weekday. Beside the hold notices, each run prints one per city
shown alone ("toronto-canada: showing October alone — the next month is not yet checked: ca.ift (a
member of ca.toronto), first unchecked day 1 Nov 2026"). The warning of a stamp ending before the
month after the two shown is gone: a month shown alone is that month's notice before the hold.

**The site.** A one-month page is one month: one table, one print button, a clock change noted only
when it falls on a day shown (Toronto's of 1 November is on no day its October page carries), "None
is set by rule this month." (`note_no_rule_month`, seven languages), the fold over the whole month.
`check_page` asks for exactly the document's months and their days in the live data. The script never
shows a time the page does not carry: after Isha on a page's last day the next prayer is tomorrow's
Fajr, on no day the page holds, and where the card used to show the out-of-date line and a dash it now
shows that day's calendar leaf (weekday, day, month, as without script) with an empty ring and no
countdown, Isha current as in the app — today's times are still right. At the city's midnight the
out-of-date line comes, as before. The index's lede and description, the checks page's "Which places
have a page" and the README now say this month, and next month once it is checked, in each language's
own words.

**On today's stamps** (every row of `cities.tsv` probed, not only the un-held): the 06:07 UTC build of
1 October publishes 41 cities, not 38, with Toronto, Mississauga and Chicago showing October alone
(Chicago's us.isna has no November). İstanbul, Ankara, Paris, Oslo, Brussels and Antwerp stay held:
the Diyanet-family hole from 26 October is in their current month. On 1 November Bandar Seri Begawan
shows November alone (bn.mora lacks 14 December) and Toronto, Mississauga and Chicago are held for
November's gaps; on 1 December 26 cities show December alone, their checked days ending with 2026,
and on 1 January they are held unless their 2027 tables are gated first (Brunei already on
1 December, for its one day). `site/cities.tsv` is untouched here, the stamps being extended in
parallel tonight, so its header still says "covering both months shown".

**Checked.** The tools' tests (264; ProvenTest's two months, one month, hold and next-month-hole
cases, DocumentTest's one-month city, MainTest's notices); `site/test_timetables.py` (18, the page
script run in headless Chrome with its clock pinned before and after Isha and after midnight on a
one-month page's last day, and into November on a two-month page); `generate` for 1 October 06:07 UTC
and `build.py --check`; Toronto's English and Arabic one-month pages at 390 and 1440 px on the first
day and the last evening; the one-month page printed on A4 and Letter in English, Arabic and Urdu,
with and without the detailed view — one sheet each, with its foot.

## October's proof, and twelve more cities (2 October)

The monitor's October captures, gated. **Egypt first:** its capture of 2 October showed Dhuhr 720 min
"early" on 30–31 October at 27 towns. Egypt's clock goes back on 30 October, Dhuhr then falls before
noon, and Dar al-Ifta still labels it م, which `fetchers/egypt.py`'s 12-hour rule read as night. The
page's ص/م is no longer read: each column is placed by its prayer (Fajr and Sunrise morning, Dhuhr
midday, Asr to Isha afternoon), with tests on invented data under both Pythons (56/56). Both raw
captures, re-parsed with it, differ from the first reading only on those two Dhuhrs, and the
monitor's own check over them is green.

**Pinned captures.** The gate's rows of 29 September read the monitor's live Diyanet files, which a
fetch rewrites: they now read copies pinned as they were (`archive/tables/pinned/<source>/<date>/`), as
does Dahab's Egyptian row, and the whole gate gave every stamp byte for byte as before. New rows read
pinned copies too, except the MJC's, whose monitor capture is one file a month, kept.

**New rows (91, all held out):** Diyanet's capture of 2 October (the same minutes as 28 September's
on every day both hold; it adds 30 October – 1 November) for tr.diyanet's 22 districts, other.turkey,
tr.diyanet.europe's 19 refreshed cities, and the cautious Oslo, Lyon, Lille, Stockholm, Helsinki,
Copenhagen, Brussels, Antwerpen and Gent; the MJC's October as za.mjc and, with the community
calendar's October (masjids.co.za's relay, every day, and Wembley's own 20 days) and Jamiat's Cape
Town October, as za.cape at the Cape Town point; Egypt's September and October at all 30 towns. Every
Diyanet Europe row passed on the committed curves, so nothing was refitted (the curve generator, run
to a scratch file, gives the same curves). Whole gate: 802 rows, 157,590 place-days, 0 early, 0 late
ends, none over its limit. The golden vector is unchanged; ProofStamps.kt is regenerated.

**The cities.** The rule over every row of `cities.tsv` publishes 53 cities on 2 October, 12 more than
on 1 October, each showing October alone until November is checked: Cape Town (za.cape, all three
members), İstanbul and Ankara (tr.diyanet's own districts), Paris (fr.cautious, Diyanet's Paris unit
now through 1 November), Oslo (no.cautious), and Cairo, Alexandria, Giza (Cairo's unit), Port Said,
Suez, Luxor and Aswan (eg.esa). The eight held Egyptian and Cape Town rows are un-held; İstanbul,
Ankara, Paris and Oslo were un-held already and come back by themselves. Brussels and Antwerp stay
held (EMB's table ends 25 October), Morocco's three cities until about 13 October (ma.habous's next
Hijri month), Amman and Zarqa, Hebron and Nablus for October's tables. Checked: tools jvmTest 266/0
with the archive (ProvenTest's Cairo and İstanbul cases now pin October), the shared stamp, About,
golden-vector and Diyanet tests, `checkStamps`, `build.py --check` (158 pages, 137 of them prayer
times for 53 cities), `site/test_timetables.py` (18), and every new city's English page at a true
390 px — Cape Town, İstanbul, Cairo and Oslo whole, the rest at the top.

## The new sources' first tables, gated (2 October, evening)

The monitor's new fetchers' captures of 2 October, each read from a copy pinned as it was
(`archive/tables/pinned/<source>/2026-10-02/`). **26 new rows, all held out:**

- **jo.awqaf (1):** the ministry's region table (Amman, Balqa, Zarqa, Madaba), 2 October – 31 December
  2026, at jo-awqaf.tsv's Amman point. Zarqa's capture is the same table line for line, so it has no
  row of its own; Zarqa is in the Amman unit. Within class A's 1 min on every day.
- **be.cautious (1):** EMB's held 2026 table, 26 October – 1 November, at Brussels beside Diyanet's
  pinned captures (`split-7g/emb-2026-q4-late.txt`). A row over EMB's whole remainder (to 31 December)
  failed from 2 November: with Diyanet's table not held on those days, the cautious start is measured
  against EMB's alone, and the later member's Dhuhr and Asr (and EMB's sunrise against the earlier
  one) read 4–6 min over class C's 1. The engine is not at fault, and no limit was moved. The row was
  cut to the days both members hold, as emb-2026-q4.txt is. EMB's November waits for Diyanet's.
- **ca.toronto (21):** IFT's, IIT's and MAC's October–December at their units and as members at the six
  points. On every day both hold, each prints the same minutes as the held split files. IIT and MAC
  are read whole at their units, so ca.iit and ca.mac are now checked through 31 December with no
  November hole. As ca.toronto's members they leave out IFT's fault days, as the split files do
  (`split-7g/<mosque>-2026-q4-capture.txt`): read whole, 28 capped Maghribs on 1–6 November went
  unchecked, since the cap follows IFT's table, which those days lack.
- **us.chicago (3):** Makki and DarusSalam Lombard (the 18° block) and the Mosque Foundation (us.isna),
  October – December, at their points.

IRN's capture is the held 2026 calendars unchanged, so it adds no row. Whole gate: 828 rows,
157,851 place-days, 0 early, 0 late ends, none over its limit, nothing broken. The golden vector is
unchanged, and ProofStamps.kt is regenerated.

**The cities.** The rule over every row on 2 October publishes 55 cities, Brussels and Antwerp the
new ones, each showing October alone. Antwerp's EMB days come from the Brussels rows, 42 km away,
within class C's reach. November waits on Diyanet's Brussels and Antwerpen tables, which end on
1 November. Chicago now shows October and November: each member is checked at Chicago through
December (the 18° block by Villa Park's year, Makki and DarusSalam; us.isna by the Mosque
Foundation). MCC's, MEC's and Orland Park's own tables still lack November. Toronto and Mississauga
show October alone. November is checked for all three members except IFT's fault days, 1–6 and
28–30 November, so it would show if those days did not block.

Amman and Zarqa are un-held, but the generator holds both until 1 November. The ministry's table
starts on the day it was fetched, so 1 October is not checked. From 1 November they show November
and December. Their pages were looked at from a build dated 1 November: Amman in English and Arabic,
Zarqa in English.

Checked: tools jvmTest 267/0 with the archive. The shared stamp, About and golden-vector tests passed
(25/0), and so did `checkStamps`. `build.py --check` passed (164 pages, 143 of them prayer times for
55 cities), and `site/test_timetables.py` passed (18). Brussels and Chicago were looked at whole at a
true 390 px, and Antwerp at the top.

## A member's misprinted day no longer blocks a cautious page (2 October, night) — ruling R117

The owner's ruling: a day a member timetable does not publish, or publishes wrongly, does not block a
page, so long as the fault is recorded with its reason and another member's table is checked that day.
A day every member is excused on is still a hole, and an unexplained gap still blocks.

- **The gate files** say why a member's day is missing in an `@excuse` line: the entry, the member, `*`
  (every point its rows are read at) or one point, the dates, `fault` or `unpublished`, and the reason
  in words (dates and differences only). `ca-toronto.tsv` now records IFT's faults (standard time kept
  8–12 March, a repeated Fajr minute on 28 March, daylight time kept 1–6 November, Isha 5 min after its
  neighbours 28–30 November) and IIT's and MAC's member rows leaving the same days out, with IIT's own
  8 March Asr. `ca-cautious.tsv` records the Edmonton tables' faults and `se-cautious.tsv` Rabita's
  31 March, which were written only in comments and table headers until now.
- **The gate** records each excused day per member and point in the stamp, by kind, dates only. It
  refuses a line that excuses a day the member's rows there hold, or that matches no row. Nothing else
  changes: still 828 rows and 157,851 place-days, 0 early, 0 late ends, none over its limit. Only the
  three cautious stamps gained their `excused` days. ProofStamps.kt and the golden vector are
  byte-identical.
- **The proven rule** (Proven.kt): for class C, a shown day is covered when every member is checked or
  excused there and at least one is checked. On a day a member's rows near the city leave out for a
  recorded reason, its own green unit covering the city counts if it holds the day. That is R115's
  own-unit reading, applied day by day. A and B are unchanged. The build prints a notice per member
  excused on days shown, for example `toronto-canada: 9 days excused: ca.ift (a member of ca.toronto)
  recorded faults`.
- **The checks page** says in one sentence, in all seven languages, that days a timetable itself does
  not publish or gets wrong are left out of the check and named, with the reason.

**The cities.** The rule over every row un-held: on 2 October, **Toronto and Mississauga show October
and November** (before: October alone). On 1 November they show November and December, where before
R117 both would have been held. On those nine November days IIT's and MAC's own tables are checked
whole at their units. The checks page's Toronto row now reads through 31 December. No other city
changes. Cape Town's November is still held: its community calendar needed no excuse (masjids.co.za's
relay prints every day the Wembley calendar leaves out), and the MJC's November is not yet published,
which is not a day the table does not publish.

Checked: tools jvmTest 281/0 with the archive. The shared ProofStamps and AboutTimes tests passed (24/0),
and so did `checkStamps` (71 stamps). `build.py --check` passed (164 pages, 143 of them prayer times for
55 cities), and `site/test_timetables.py` passed (18). Toronto's page was looked at whole at a true
390 px, in English, with both months.

## One button before each permission prompt (2 October) — 1.0.0 (35)

Apple rejected 1.0.0 (34) under guideline 5.1.1(iv), reviewed on an iPad Air 11-inch (M3): the
location screen before the system prompt said "Use my location", wording that steers toward Allow.
The Human Interface Guidelines' rule for a screen before a prompt is stricter than the message
said: one button, titled like Continue or Next, and no way to leave without seeing the prompt. So
"Choose a city instead" beside it broke the rule too, and so did the notifications screen's
"Enable notifications" with "Not now". Build 32 had passed with the same screens; each submission
gets a fresh reviewer.

- Both screens have one button, Continue, which always opens the system prompt.
- A refused location opens the city search at once, and a city is required to go on (Mohamed's
  call): `locationAnswer` maps the answer, and a prompt still unanswered when iOS stops waiting
  leaves the screen as it is. Backing out of the search and pressing Continue again goes straight
  back to it: iOS asks only once, and Android stops asking after two refusals.
- Today's "No location yet" link said "Allow location instead" and asked the system again, which
  on an iPhone after "Don't Allow" did nothing at all. It is now "Turn on location in Settings",
  opening the app's page in the system settings (`openAppSettings`), and coming back with access
  fetches the location by itself.
- Settings › Location and Notifications keep their switches: a person flipping a switch is the
  in-context request Apple recommends.

Checked from fresh installs on the iPhone 17 Pro simulator and the Android 16 emulator: allow,
decline, back out and Continue again, Android's second and third refusal, and Today's link out to
App info and back with location on. The iPad simulator was not driven: the simulator tool's
per-device permission went unanswered. Apple's reply and the build's review Notes are in
`docs/store/app-review/2026-10-02-*`. Beyond the fix, 35 carries October's proof stamps
(`ProofStamps.kt`, from the gate commits c21b9c06 and 8c31ac05 that landed after 34), which the
release script requires fresh; no other app code changed since 34. Google has no such rule, but
Mohamed sent 35 to Play's closed track too, built from the same tag; its release notes are in
`docs/store/release-notes/1.0.0-35.txt`.

## Palestine's printed town offsets (2 October, night) — ruling R118

The owner ruled that the PA Dar al-Iftaa's own printed town offsets may be applied to its al-Aqsa
perpetual table. The authority prints no table for Hebron or Nablus. It prints the al-Aqsa table and,
with it, a list of towns and their minutes from Jerusalem: Nablus + 0, Hebron + 1, Gaza + 3. The
earlier refusal for Gaza stands only where no offset is printed.

**The whole table.** Two prints were fetched once each from darifta.ps: the 2012 printing, which
carries the table and the offset list, and the 2026 yearly calendar, which reprints the table. Both
have a text layer and were read by word position, independently. They agree on all 366 days and every
time, and they match the earlier 72-date OCR read and the 2026 imsakiya. The third PDF named in the
monitor's row, `ssalah/ssalah.pdf`, turned out to be a book on the fiqh of prayer, not a table. The
reads, the offset list and the derived Nablus, Hebron and Gaza tables, each mapped onto 2012, 2026 and
2027, are in the private archive (`archive/tables/manual/ps-iftaa/2026-10-02/`).

**What the whole table shows.** The 78 dates held before kept the entry inside class B's 2 minutes.
The full year does not. The printed minutes wander about 2.4 min against any one smooth rule (Fajr
sits a minute nearer the sun in April and August than in October). So one never-early margin per
event runs 3 min late on some days of four events even at al-Aqsa. Under ruling R57 that is class D,
the class the data supports, as Libya's is. The old margins were also 1 min early on some days at
al-Aqsa, and on many at Nablus and Hebron. The refit removes all of them.

**The registry.** `ps.iftaa` now takes every event at the noon declination (it fits the table better
than the exact sun: Fajr's spread 2.4 min, not 3.1). It has four point-table units: al-Aqsa, and
Nablus, Hebron and Gaza at the app's city points, each with its own margins. The margins are fitted
on 2012 and 2026. 2027, held out, was 1 min early on a few days, so those margins are widened to the
three years' bound. The three towns run a further minute late: their printed offset is one figure all
year while their own sun moves against al-Aqsa's through the seasons. Each carries its own 4-minute
exception on those events (ruling R41). `PrintedTable` gained a `lateLimits` field to carry them
(removed again on 3 October, review r2: no town needs an exception any more, so the field is gone).
Gaza's cautious times now place their PA member at Gaza's own table.

**Checked.** Whole gate: 839 rows, 162,157 place-days, 0 early, none over its limit, nothing broken.
"0 late ends" held only for sunrise: the end of eating was not gated against the PA's printed Fajr (the
rows read `F`, not `F+E`), and it ran after that Fajr on 100–250 days a year across the West Bank, by up
to 3 min (review r1, C2; fixed on 3 October, below). The stamps' engine hashes all moved, since
`Units.kt` is core. ProofStamps.kt is regenerated. The golden vector moved only at its two Palestinian
points (Ramallah's and Gaza City's jittered seeds), by 1–2 min: mostly later starts and earlier
sunrises, but two rows the other way, Gaza's 2026-02-21 Fajr a minute earlier (to Gaza's own printed
time, not before it) and Ramallah's 2028-12-21 end of eating a minute later (after the printed Fajr, an
instance of C2) (corrected 3 October, review r1 M2). Tools jvmTest passed 267/0 with the archive,
`:shared:testAndroidHostTest` passed 1,595/0, and `checkStamps` passed.

**The cities.** The rule over every row on 2 October still publishes 55 cities. Hebron and Nablus stay
held: the city-page rule needs class A, B or C, and the whole table supports D. Gaza stays held too,
since its Ministry of Awqaf member is checked on six 2020 days only.

## Palestine: every printed town, and the end of eating (3 October) — ruling R118, review r1's fixes

Review r1 of R118 found two things wrong with the first version. Five towns the PA prints an offset for
(Tulkarm + 1, Qalqilya + 1.5, Rafah, Khan Yunis and Deir al-Balah + 4) had no unit and took a
neighbour's table, so they came out early. And the end of eating ran after the PA's printed Fajr, which
the gate never checked.

**Every printed town.** All 16 Palestinian towns on the list are now units, each at its own point:
Jerusalem (at al-Aqsa), Ramallah, Bethlehem, Jenin, Nablus, Jericho, Hebron, Idhna, Dura, Beit Awwa,
Tulkarm, Qalqilya, Gaza, Rafah, Khan Yunis and Deir al-Balah. A town's times are built the way the
authority builds them: al-Aqsa's computed times plus the town's printed minutes. They are computed at
al-Aqsa alone (a new `FixedPointMode.TABLE`), and the town's figure is applied as the authority's own
minutes. Adding whole minutes to a time already rounded never crosses a minute boundary. So every town
gets al-Aqsa's proven 3-minute bound, needs no margin or exception of its own, and is never early by
construction. The towns' 4-minute exceptions are gone.

The user's own sun no longer rides beside a town's printed times. The authority's construction does
not use it, and on those times it only added lateness. (Corrected the same day, review r2: it also
kept the ends the PA never prints, Asr's and Maghrib's, from running later than the place's own sun,
so it bounds those again; see below.) The sunset that Maghrib is held to moves with the town's figure,
as every other time does. Without that, Jericho's − 1 put its Maghrib before al-Aqsa's sunset on 26 days,
and the repair made it 4 minutes late.

Qalqilya's minute and a half becomes + 2 on its starts (never before the half-minute instant) and
+ 1 on its ends (never after it). The archive holds two derived tables for it, one for each.

**Each place takes its nearest printed town's table, never a neighbour's.** Every unit has the same
reach, so the unit a place resolves to is simply the nearest printed town. Each of the app's 45
Palestinian cities resolves to its own town, or to the nearest one. A new test walks them through the
app's engine.

**The end of eating.** The PA prints no imsak, so its Fajr is when the fast begins. The gate now reads
every PA Fajr column as `F+E`. The end of eating has its own margin, fitted the gate's way: −96 s on
2012 and 2026. Held out on 2027, that was 1 minute late on 7 April days, so it is widened to the three
years' bound, −116 s. It is now never after the printed Fajr, and at most 3 minutes before it.

**Checked.**
- Whole gate: 865 rows, 170,917 place-days, 0 early, 0 late ends, none over its limit, nothing broken.
- `ps.iftaa`: 39 rows, 16 places, 13,144 place-days (12,413 held out). Every unit is worst 3 minutes
  late on Fajr, sunrise, Maghrib, Isha and the end of eating, 2 on Asr and 1 on Dhuhr. No exception
  anywhere.
- Only `ps.iftaa.json` changed among the stamps beyond the engine hash. ProofStamps.kt is regenerated.
- Golden vector: 32 rows moved, all at the two Palestinian seeds, by 1–2 minutes.
  - Ramallah's jittered seed: the end of eating 1–2 minutes earlier on 16 rows. On one row
    (2028-09-22) Isha is 1 minute earlier.
  - Gaza City's jittered seed (on Automatic, Gaza's cautious times) moved the other way on most rows.
    Fajr, Asr and Isha are up to 1–2 minutes earlier, and sunrise 1 minute later on 10 rows. The
    seed's own sun no longer rides beside Gaza's table.
  - Every moved value is at or after the town's own printed start, and at or before its printed end.
- Every Palestinian city through the app's engine, 2026–2031: with `ps.iftaa` chosen, 0 early starts
  and 0 late ends at all 45 places. On Automatic the West Bank's 32 places are the same. The Gaza
  Strip's 13 places follow Gaza's cautious times, whose Maghrib is capped at the Ministry of Awqaf's
  (ruling R38, disclosed).
- Tools jvmTest passed 267/0 with the archive. `:shared:testAndroidHostTest` passed 1,598/0, and
  `checkStamps` passed.

**The cities.** Hebron and Nablus stay held, because the data supports class D. Gaza stays held too.

## Palestine: the list's towns in Israel, and the ends the PA never prints (3 October) — ruling R118, review r2's fixes

Review r2 of R118 found two more things.

**A West Bank village could take a farther town's figure.** The PA's list also prints 13 towns in
Israel: Nazareth and Umm al-Fahm + 0; Tiberias, Safed and Beisan − 1; Haifa, Acre, Kafr Qasim and
Tayibe + 1; Lydd and Ramla + 1.5; Beersheba and Jaffa + 2. Only the 16 Palestinian towns were units,
so a village by the Green Line resolved to a Palestinian town even where a listed town in Israel was
nearer. Ni'lin and Qibya are 12 km from Lydd (+ 1.5) and 18–20 km from Ramallah (+ 0), and their Dhuhr
came before Lydd's on every day. Barta'a ash-Sharqiya, by Umm al-Fahm (+ 0), took Tulkarm's + 1;
Bardala and Ein al-Beida, by Beisan (− 1), took Jenin's + 0, so their sunrise and end of eating ran
late against Beisan's. None of the app's 45 cities was affected.

The 13 towns are now units too, at approximate points from public maps, with the same reach as every
other. They are used only to decide which printed town a place in Palestine belongs to. The entry's
scope stays Palestine: choosing it in Israel still falls back to Automatic. Lydd and Ramla's minute
and a half is handled as Qalqilya's, + 2 on the starts and + 1 on the ends. The archive holds their
derived tables (`pif_towns_il.py`), and the gate has a row for each town on 2026 and 2027.

**The ends the PA never prints ran later than the place's own sun.** With a town's times taken from
al-Aqsa alone, Asr's end (al-Aqsa's sunset plus the figure) and Maghrib's end (al-Aqsa's red twilight)
lost the bound of the place's own sky that spec §3.5 asks of every end. Over 2026, Maghrib's end came
out later than the place's own at Jericho on 283 days (+ 1 min), and at Jenin, Tubas, Nablus and
Hebron on 39–141 days (up to + 2). Asr's end did at Jenin, Nablus, Tubas, Rafah and Hebron on 121–177
days (up to + 2). Now, under `FixedPointMode.TABLE`, Asr's end is the earlier of that sunset and the
place's own, and the red twilight is the earliest over al-Aqsa's sky and the place's. The printed
times (every start, sunrise, the end of eating) stay al-Aqsa's plus the figure, and Maghrib stays held
to the shifted table sunset, so Jericho's earlier repair is not undone. `PrayerDay.sunset`'s KDoc now
says what the field holds under a table carried by a printed figure. Spec §3.5 carries an R118 note
for the owner to confirm: a printed town takes its printed times from the table's point alone.

`PrintedTable` is back to its form before R118 (its `lateLimits` field carried nothing any more).

**Checked.**
- Whole gate: 895 rows, 180,407 place-days, 0 early, 0 late ends, none over its limit, nothing broken.
- `ps.iftaa`: 69 rows, 29 places, 22,634 place-days (21,903 held out). Every unit is worst 3 minutes
  late on Fajr, sunrise, Maghrib, Isha and the end of eating, 2 on Asr and 1 on Dhuhr. No exception
  anywhere.
- Only `ps.iftaa.json` changed among the stamps beyond the engine hash. ProofStamps.kt is regenerated.
- Golden vector: no row moved. Its rows hold the starts, sunrise, the day's sunset (Maghrib's floor),
  the end of eating and imsak, not the prayers' ends, and its Palestinian seeds take the same towns as
  before.
- Every Palestinian city through the app's engine, 2026–2031, plus the five villages above (50
  places, 109,550 place-days a mode): with `ps.iftaa` chosen, 0 early starts and 0 late ends against
  the nearest printed town's table. On Automatic the West Bank's 37 places are the same. The Gaza
  Strip's 13 places follow Gaza's cautious times, whose Maghrib is capped at the Ministry of Awqaf's
  (ruling R38, disclosed).
- New tests: the five villages take their nearest town's table through the app's engine on both
  settings, and a town's Asr and Maghrib ends are never after the same method's at the place's own
  point.
- Tools jvmTest passed 267/0 with the archive. `:shared:testAndroidHostTest` passed 1,600/0, and
  `checkStamps` passed. `site/build.py --check` passed: 55 cities.

**The cities.** Hebron and Nablus stay held, because the data supports class D. Gaza stays held too.

## The end of eating, audited before Ramadan (3 October)

Ramadan 1448 begins around 8 February 2027. The Palestine review found that `ps.iftaa`'s rows never
checked the end of eating (the engine's `endOfEating`, when sehri or suhoor stops) against the
printed Fajr, so every gate entry was audited for the same gap. The gate checks it in one of two
ways: a printed imsak or sehri column (`E`, or `Im` for an imsak the authority prints as a precaution),
or the printed Fajr read as both (`F+E`) where the fast begins at Fajr. Palestine was left to its own
track.

**What the audit found.** 55 of the 71 gated entries already checked it, none with a late end.
Sixteen did not, `ps.iftaa` among them. In four more (`pk.karachi`, `nl.cautious`, `za.cape` and
Libya's two owner rows) some rows read Fajr alone. In every one of them the source prints
no end-of-eating column the archive holds, and the fast begins at the printed Fajr. JAKIM's, Kemenag's
and MORA's imsak is a 10-minute precaution before Subuh, the MJC's Fajr is also the end of sehri, and
Jordan, Lebanon, Gaza, Syria, Iraq, Uzbekistan, Kyrgyzstan, Kazakhstan (whose API "imsak" comes after
its Fajr) and Dawat-e-Islami print none. Those rows are now `F+E`: 106 rows in 17 gate files, each
file's header saying why. Some rows were left as they were. In each case the fast ends at a time
earlier than the Fajr the row reads, and another row already checks that time:
- IFB's October digitization (the sehri);
- Kosovo's Sabah, which is imsak + 20 (the imsak);
- Jamiat's other publishers (Jamiat's sehri);
- eastern Libya's national table, where the fast follows the 19.5° adhan (R73).

IFB, Jamiat and Libya were each checked once outside the gate. The end was never after their Fajr,
and it came 5 to 10 min early, by design.

**One late end, fixed.** At Astana, `kz.qmdb`'s end of eating came after QMDB's printed Fajr on
29 days of 2026, the first on 12 May, by up to 4 min. This mirrors the AngleBased residual that
QMDB's Fajr limit already records. The fix was the existing one. The fitter's bound is −221 s,
decided at Astana on 14 May. On Almaty's rows alone the bound is +18 s, so only QMDB's own region at
and above 48° N carries −221 s, beside its ± 5 minutes. Almaty stays at 0.

**Early ends, recorded (R41).** None of the following moved later: an end only ever moves earlier.
- **`my.jakim`, 4 min.** e-solat prints each zone's Subuh for its latest reference point, rounded up,
  while the fast here begins at the dawn of the place itself. Over 9,489 zone-days the end is never
  after Subuh, but it is more than 2 min early on 1,509 of them.
- **`lb.fatwa`, 8 min.** The 20° end against Dar al-Fatwa's own 19.1–20.0° dawn. Its margin is now the
  fitter's −5 s, which moves it 5 s earlier.
- **`kz.qmdb`, 5 min.** Astana's other days, after the fix above.
- **`default.safe`, 5 min.** At Kuala Lumpur, the same JAKIM rounding plus this fallback's own − 2 min.

Every other newly checked entry stays inside its class limit: `id.kemenag` 2, `bn.mora` 1,
`jo.awqaf` 1, `in.karachi` 3, `uz.board` 1, `kg.default` 0, `sy.awqaf` 1, `iq.sunni` 1,
`ps.gaza.awqaf` 2, `za.mjc` 1, `za.voc` 2, `pk.karachi` 2. `za.cape`, `nl.cautious` and Libya's
owner rows are unchanged within theirs.

**A gap left to Mohamed: the world fallback.** `default.safe` ends the fast at 18° less 2 min (spec
§6.2 c), so its end came after the printed Fajr on every day held at Cairo (19.5°), Jakarta and
Singapore (both 20°). Checked once, it was also after Jamiat's and IFB's printed sehri. None of these
countries is served by the fallback, and each one's own entry is checked. On those rows the end is
not gated. The reason is in `safe-default.tsv` and proof 7h §3. Ending the world fallback's fast at
20° would close the gap, but it changes the spec.

**What moved.** The whole gate: 828 rows and 157,851 place-days, 0 early, 0 late ends, none over its
limit, nothing BROKEN. Outside the end of eating, every event table is identical to before. Stamps
were regenerated for the 17 entries whose end-of-eating figures changed, along with
`ProofStamps.kt`. The golden vector moved on 23 of its 2,071 rows, in the end of eating alone and
always earlier: 7 days at the Beirut seed by 1 min, and 16 at the Astana seed by 3 or 4 min. One test
pinned JAKIM's entry-wide worst, which is now 4, the end of eating, and another pinned Lebanon's
exceptions; both were updated.

Checked: tools `jvmTest` 281/0 with the archive. `:shared:testAndroidHostTest` passed (1,597/0), and
so did `checkStamps` (71 entries). `CI=true generate` wrote 55 cities, with 2 held. `build.py --check`
passed (164 pages, 143 of them prayer-time pages), and `site/test_timetables.py` passed (18). No city
page changed. The engine changes (Kazakhstan's northern end, Lebanon's −5 s) ship with the first
update, not 1.0.0 (35).

## The end of eating north of Astana (3 October, the audit's fix round)

The audit's review asked what Astana's −221 s is worth north of Astana, where no QMDB table was held.
For the end of eating, using Astana's margin further north is not like using its Fajr limit. A
deeper AngleBased residual makes a Fajr start later, which is the safe side. It makes the end of
eating late, which is the unsafe one. So QMDB's 2026 and 2027 tables were fetched from its API for
Astana and four of its own northern cities: Kokshetau, Kostanay, Pavlodar and Petropavl (54.9° N).
Astana's 2026 table came back identical to the archived one. The tables were checked in a scratch
gate run. They are not in the archive yet, so they have no rows in `kz-qmdb.tsv`.

**What it found.** At −221 s the end of eating came after QMDB's printed Fajr on 123 of 3,650 days.
The gate's samples were at Petropavl from 30 April (2 min) and at Astana itself on 17 May 2027
(1 min). It was refitted the existing way on all fourteen tables, and the fitter's bound is −374 s,
decided at the north on 6 May 2027. With it there are no late ends in that run, and the whole gate
is green. The end at and above 48° N now comes up to 8 min before the printed Fajr. That is recorded
as `kz.qmdb`'s end-of-eating exception, now 8 min instead of 5 (R41). Almaty stays at 0.

**What it found that this round could not fix.** In the same scratch run the starts fell short, and
this round may not move a start:
- Fajr was 1 min early on 7 days at Astana in July 2027, and up to 7 min late at Petropavl (over its
  5 min limit on 92 days).
- Isha was 1 min early on 4 days at Petropavl, Kokshetau and Pavlodar in August, and up to 5 min late
  (over its 4 on 29 days).

The fitter asks for Fajr −2 → +8 s and Isha −3 → +14 s. Those need their own round before the
northern rows can go in green. The monitor now fetches the six QMDB points each month (source
`kz-qmdb`, `fetchers/qmdb.py`, tested on invented data; a trial run into a scratch root gave the
archived Almaty and Astana tables byte for byte). North of Petropavl, up to 55.4° N, the margin is
an extrapolation, and for an end that is the unsafe side.

**The other findings.**
- Dawat-e-Islami's two rows (`in-karachi.tsv`, `pk-karachi.tsv`): the table rounds the source's
  seconds up for Fajr, so as the end's reference it can be up to 59 s late. Both headers now say so.
  No held day sits in that window, since the end is at least 1 min before the rounded Fajr every
  day. A floored E column is a to-do for the next archive round.
- JAKIM: proof 7c now says that the normalised tables hold no imsak and the raw e-solat captures do.
  An `Im` column from them is a to-do for the next archive round.
- `AsiaProofTest` now checks both end-of-eating margins: 0 at Almaty and −374 s or deeper at Astana.
  The KDoc and the test comment now say that this margin differs by region.
- Left as they were: `default.safe`'s 18° end, which is the owner's decision (above), and
  `ps.iftaa`, which belongs to the Palestine track.

**What moved.** The whole gate: 828 rows and 157,851 place-days, 0 early, 0 late ends, none over its
limit, nothing BROKEN. Only `kz.qmdb`'s stamp changed (end of eating: worst 8, limit 8), with
`ProofStamps.kt`. The golden vector moved on 16 of its 2,071 rows, all at the Astana seed and in the
end of eating alone, 2 or 3 min earlier each.

Checked: tools `jvmTest` 281/0 with the archive. `:shared:testAndroidHostTest` passed (1,597/0), and
so did `checkStamps` (71 entries). The monitor's Python tests passed (72, both Pythons). `CI=true generate`
wrote 55 cities, with 2 held. `build.py --check` passed (164 pages, 143 of them prayer-time pages), and
`site/test_timetables.py` passed (18). No city page changed. The engine change ships with the first
update, not 1.0.0 (35).

## Kazakhstan's starts north of Astana (3 October, the northern round)

The end-of-eating round left two starts it could not move. QMDB's Fajr was 1 min early on 7 July days
at Astana in 2027, and its Isha 1 min early on 4 August days at Petropavl, Kokshetau and Pavlodar.
Both also went over their late limits in the north. This round fixes them, and the northern tables
are now in the gate.

**The tables.** The kz-qmdb monitor's capture of 3 October (QMDB's year API, 2026 and 2027 at six
points) is pinned in the private archive as fetched: `archive/tables/pinned/kz-qmdb/2026-10-03/` (12
tables) and `archive/raw/monitor/kz-qmdb/2026-10-03/` (12 raw JSON files). The Almaty and Astana 2026
tables equal the archived ones day for day, so they get no second row. The other ten are new rows in
`kz-qmdb.tsv` (`F+E S D A M I`). Fit rows: Astana 2027, and Kokshetau, Pavlodar and Petropavl over
both years, which had already decided the end of eating. Held out: Almaty 2027 and Kostanay over both
years (the farthest west, in its own zone).

**The refit.** The fitter over the 11 fit rows asked for Fajr +8 s (was −2) and Isha +11 s (was −3).
The earlier entry and proof 7f said +14 s for Isha; that was a misreading, and proof 7f now says
+11. Sunrise, Asr and Maghrib had 0 to 3 s of safety left, so they move to the fitted bounds too:
sunrise +14 (was +19), Asr −8 (was −11), Maghrib −15 (was −17). Dhuhr stays −22, and the northern
end of eating stays −374 s. Every start is later and sunrise earlier. The margins are one set for
both variants, so Almaty's starts move by the same seconds. The held-out rows: 0 early, 0 late ends.

**The late limits (R41).** The AngleBased residual deepens to the north. Fajr is late from late April
to early June, up to 5 min at Astana, 6 at Kokshetau, Kostanay and Pavlodar, and 8 at Petropavl.
Isha is late from July to mid-August, up to 5 min from Astana north. `kz.qmdb`'s Fajr limit is now 8
min (was 5) and its Isha limit 5 (was 4), each with its reason. Neither comes near 10 min. Almaty's
worst start is 1 min.

**The About screen.** `kz.qmdb` has no units, so the screen shows the entry's stamp at every Kazakh
place. It now reads 4,380 days at 6 places, checked through 31 Dec 2027, "at most 8 min after". That
is true at each measured city. At Almaty, whose own worst is 1 min, it overstates.

**What moved.** The whole gate: 838 rows and 161,501 place-days, 0 early, 0 late ends, none over its
limit, nothing BROKEN. Only `kz.qmdb`'s stamp changed, with `ProofStamps.kt`. The golden vector moved
on 15 of its 2,071 rows, all at the Kazakh seeds (5 at Almaty, 10 at Astana): Fajr, Isha, Asr and
Maghrib 1 min later, sunrise 1 min earlier. `AsiaProofTest` now pins the Fajr and Isha margins at or
above +8 and +11 s.

Checked: tools `jvmTest` 281/0 with the archive. `:shared:testAndroidHostTest` with the Registry,
GoldenVector, ProofStamps and AboutTimes filters passed (110/0), and `AsiaProofTest` passed (6/0).
`checkStamps` passed (71 entries). No city page uses `kz.qmdb`.

**Still open.** North of Petropavl, up to 55.4° N, every margin is an extrapolation. The Fajr and
Isha limits may be a minute short there, and the end of eating's margin is on the unsafe side. The
west (Oral, Aktobe, Atyrau, Aktau) is within the measured latitudes but has no table. The engine
change ships with the first update, not 1.0.0 (35).

## Morocco's next Hijri month, fetched the day it appears (6–7 October, overnight)

Morocco's city pages are held: the Habous capture covers 13 September to 12 October 2026 (Rabiʿ
al-Akhir 1448), and a page shows only whole Gregorian months whose every day is checked (rulings R115,
R116). This round asked whether the Ministry publishes further ahead, and makes the monitor fetch each
new Hijri month as soon as it appears.

**What the Ministry publishes.** One Hijri month at a time, on the day it begins. Each month's
announcement in the moon-sighting section (مراقبة الأهلة) is dated its first day, links the live page
`prieres/index.php?ville=…` (30 rows from the first day, for about 190 places), and names the evening
of the next sighting: Jumada al-Ula 1448 is watched for on Sunday 11 October, so it begins on 12 or 13
October. There is no yearly table, no PDF, and no month or year parameter: the old
`horaire_hijri.php?mois=` pages are frozen in 1433 and 1434 AH, `horaire-api.php` is today's widget, and
the Ministry's own app (Rakb Al Hajj) is for pilgrims. The pages state no terms of use, so the tables
stay restricted, archive only. On 6 October the live page still showed Rabiʿ al-Akhir, identical cell
for cell at all ten cities to the capture of 28 September. Nothing beyond 12 October exists to gate yet.

**Why the cloud never fetched it.** The run of 5 October failed at every Habous city: the server sends
its certificate without Sectigo's intermediate. The Mac's TLS stacks find it by themselves, so the
capture of 28 September worked there; the runner's OpenSSL and curl do not. The two public certificates
that complete the chain are now in `tools/timetables/monitor/certs/habous.gov.ma.pem` (the DV R36
intermediate, valid to 2036, and the R46 root cross-signed by USERTrust, valid to 2038). `common.py`
adds them to the trust store for habous.gov.ma's requests alone, with verification on. With a store
that holds only the USERTrust root, the site now verifies; without the file it fails, as on the runner.
Homebrew's Python 3.13, which failed before, now reaches the site without curl, as does the Mac's
Python 3.9. The site's certificate runs to 9 February 2027: if its renewal comes from another
intermediate, the fetch fails again with the same message.

**The cadence.** `ma-habous` is now `hijri-month`. It stays weekly, and is due besides on every run from
the last day its tables hold until three days after it, until the new month is held. Whether the month
has 29 or 30 days is decided by the sighting. A 29-day month has already turned on the last day held
(the page's 30th row is the next month's first day); a 30-day month turns the day after. So the rule
needs no calendar. A page that has not moved three days after the last day held is a fetch finding,
fetched weekly again. The workflow fires every day at 01:23 UTC too: a small job `due` checks out only
the state and the monitor code, and `fetch.py --month-turn` prints each source whose month is turning.
Only then does the full run follow, a normal one like the 1st's (fetch, check, prove, push). Against the
cloud's own state of 5 October it says yes on 12–15 October and no on the other days. The private
repository's workflow file needs the new template, copied after this change is on Taqwa's `main`
(before that, the daily check would call a `--month-turn` that `main` does not have).

**What the pages would show** (ruling R116 unchanged). With capture within a day, a Moroccan page
(Casablanca, Rabat, Tangier, and any other Habous unit city) is held each month from the 1st until the
Hijri month that reaches its last day is captured, about the 8th to the 14th. From 7 October 2026 to 6
April 2027 (182 days) that is 117 to 129 days live and 53 to 65 held, over the 49 month sequences the
sighting allows (each month at Umm al-Qura's start or a day later, as Morocco's four 1448 starts so far).
A page that showed the checked days of its month, with the rest marked "not yet published", would be
live 178 to 182 days; a rolling week, 138 to 144. The real generator, on a stamp given a simulated
Jumada al-Ula capture, shows October alone from 12 October and holds every page on 1 November until the
next capture, as the count assumes.

**The edge, checked at 29 more cities.** Until now the gate checked Habous's national margins (the
edge, which answers everywhere beyond the ten fitted cities, so most of Morocco) at one town only,
Imilchil. The live month disappears when the page turns, so it was read by hand on 6 October for the
app's own Moroccan cities whose Arabic name is a place on Habous's list: Fes, Marrakesh, Agadir, Meknes,
Kenitra, Tétouan, Al Hoceïma, Safi, Khouribga, El Jadida, Beni Mellal, Nador, Taza, Settat, Larache,
Guelmim, Khenifra, Berkane, Oued Zem, Taroudant, Essaouira, Tiznit, Tan-Tan, Chefchaouen, Boujdour,
Smara, Sidi Ifni, Goulmima, and Azrou inside Ifrane's unit. The pages and tables are new files in the
private archive (`archive/raw/manual/ma-habous/2026-10-06/`, `archive/tables/manual/ma-habous/2026-10-06/`,
29 rows in `MANIFEST.tsv`), and 29 held-out rows in `ma-habous.tsv`, each at the app's city point. The
engine is never early at any of them: 0 early starts and 0 late ends over 870 place-days. Sunrise comes
up to 7 min before Habous's and Maghrib up to 6 min after it at low towns, exactly the limits the
registry records for the edge, and Asr up to 3 min on one day (the edge's class D limit is 3). Azrou
shows sunrise up to 2 min early, within Ifrane's limit. The stamp now covers 40 places and 2,379
place-days; the app's About gives no figure at the edge, so only the counts it shows at the ten fitted
cities move. The fetcher reads the 29 every run too (`EDGE_PLACES`; a page that shows another place is
left out), so prove adds their rows each month. Marrakesh, Fes and Agadir still have no page: they are
class D at the edge, and only a unit fitted on their own tables (two or three months of them) can make
them class B.

Checked: the monitor's Python tests under Homebrew's 3.13 and the Mac's 3.9 (87 tests; the one failure,
kz-qmdb without a recipe, is on `main` already), tools `jvmTest` 297/0 with the archive, and the whole
gate: 949 rows, 190,445 place-days, 0 early, 0 late ends, none over its limit, nothing BROKEN. Only
`ma.habous`'s stamp changed, with `ProofStamps.kt`.
