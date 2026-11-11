# Watch companion 4: the Apple Watch, implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a Taqwa watchOS app ships inside the iPhone build. It reads the feed plan 2's iPhone sender hands to WatchConnectivity, and draws the phone's own times, words, digits, direction and clock on Apple's faces (the circular, corner, rectangular and inline complications), in the Smart Stack and on three screens: Today, Tasbeeh with the Digital Crown and undo, and Qibla with the watch's compass. The iPhone's Settings gains "Add to Apple Watch". Everything is proven on the watchOS 27.0 simulator paired with the iPhone 17 Pro simulator; her watch is plan 5's.

**Architecture:**
- **`iosApp/WatchCore/`, a Swift port of `:watchcore`,** with no Kotlin in it (the Lead's call 1). It holds the feed's model and decoder, `WatchRules`, `WatchScreenWords`, the tasbeeh rules and plan 3's dial (`WatchQibla`, `QiblaGuidanceTracker`), the store, and the presenters every surface draws from (`Glance`, `Today`, `TasbeehScreen`, `TasbeehSpeech`, `QiblaScreen`). It is a Swift package for one reason: `swift test` runs it on the Mac against the JSON vectors Kotlin writes, in seconds and with no simulator. The Xcode targets compile its files directly; they do not depend on the package.
- **Two new targets in `iosApp.xcodeproj`,** made by a reviewable, idempotent Ruby script as the widget target was (`tools/add-watch-targets.rb`):
  - `TaqwaWatch`: a single-target SwiftUI watchOS app, `world.taqwa.ios.watchkitapp`, embedded in `Taqwa.app/Watch/`. It owns the `WCSession`, the store and the three screens;
  - `TaqwaWatchWidgets`: its WidgetKit extension, `world.taqwa.ios.watchkitapp.widgets`, for the four accessory families and the Smart Stack. It reads the store the app writes, in the app group.
  - A watchOS UI-test bundle, `TaqwaWatchUITests`, reads each screen's accessibility elements in order and turns the crown.
- **The phone:** `WatchFaceShare.swift` and a Kotlin bridge put an "Add to Apple Watch" row in Settings, shown while a watch is paired and a `.watchface` file is bundled. A debug-only `WatchHarness.swift` measures the WatchConnectivity size limits. Plan 2's `TaqwaWatchSession` is used as it is, but for one DEBUG-only delegate method that hands `transferUserInfo`'s answers to the harness (Task 5B).
- **`:watchcore` (Kotlin):** new vector files for what the Swift port reads beyond plan 1's (tests only), and two fields on plan 3's `WatchScreenWords`. **`:shared`:** plan 3's words reader fills those two (Task 3).

**Tech stack:** Swift 5 language mode, SwiftUI, WidgetKit, WatchConnectivity, Core Location (heading only), ClockKit's `CLKWatchFaceLibrary` (iPhone only), XCTest and XCUIAutomation (`XCUIDevice.rotateDigitalCrown`); Xcode 27.1 with the watchOS 27.0 and iOS 27.0 simulators; Ruby with the `xcodeproj` gem (the Lead's call 2); Kotlin 2.4.10 for the vector writers.

**Spec:** `docs/superpowers/specs/2026-10-19-taqwa-watch-companion-design.md`, sections 1.3, 1.4, 4 (all), 5 (the iPhone's row) and 6. Drawings: `docs/superpowers/specs/2026-10-19-watch-companion/faces.js`, the `a-*` screens (`aRect`, `aCircular`, `aModular`, `aAnalog`, `aUtility`, `aStack`, `aToday`, `aTasbeeh`, `aQibla`, with `appRing`, `pip`, `periodBar`, `qiblaDial`, `archMark`), drawn in a 172 × 212 space: scale by the screen's width / 172. Mockups: `mockups.html`, "Taqwa on Apple's own faces", "Smart Stack" and the three "On the Apple Watch" panels.

**The series, and when each task starts:** plan 4 of 5. Its tasks wait on these, and on nothing else:
- **Task 1** on plan 2's Task 3 (`WatchLinkContract` in `:watchcore`, the phone goldens in `watchcore/vectors/phone/`).
- **Task 2** on Task 1. **Task 4** on Task 2. **Task 5** on Task 4.
- **Task 3** on plan 3's **Task 10**, which itself follows plan 2's Tasks 1 and 3 and plan 3's Task 3. Plan 3's Task 3 adds `WatchScreenWords`, `clockTime`, `ringDigitZero` and `latinScript` to the model; its Task 10 fills them in `WatchFeedBuilder.kt`, `WatchWords.kt`, `Fixtures.kt`, the vectors and the goldens. Task 3 edits the same files and reads those goldens, so it starts only once Task 10 is on `watch-companion`, never beside it.
- **Task 5B** on Task 5 and plan 2's **Task 8** (`TaqwaWatchSession`, `WatchFeedBridge.watchStateChanged()` and `installWatchSession()` in `iOSApp.swift`). It never runs beside Task 10 (both edit `iOSApp.swift`) or beside plan 2's Task 8 Step 5 (the same simulator pair).
- **Tasks 6 and 7** on Tasks 3 and 5; Task 7 also on Task 6 (`Glances.make`).
- **Task 8** on Task 7 (the pages), plan 3's Task 1 (`TasbeehEngine` with `undo` in `:watchcore`) and plan 3's Task 6 (`TasbeehSpeech` and call 14's haptic rule, whose cases Task 8's Swift tests restate).
- **Task 9** on Task 7, plan 3's Task 2 (`WatchQibla`, `QiblaGuidanceTracker` and the compass rules in `:watchcore`) and plan 3's Task 7 (`qiblaScreen`, whose cases Task 9 restates).
- **Task 10** on plan 2's Task 8 (`installWatchSession()`), never beside Task 5B.
- **Task 11** last.

**Where it runs, and why not in plan 3's worktree.** Plan 4 runs in its own worktree, `~/Desktop/Workspace/apps/Taqwa/.claude/worktrees/watch-apple`, on branch `watch-apple`, which the Lead makes from `watch-companion` before Task 1 (`git worktree add -b watch-apple ~/Desktop/Workspace/apps/Taqwa/.claude/worktrees/watch-apple watch-companion`). Sharing plan 3's worktree is unsafe:
- Task 2 makes `scripts/test.sh` run `swift test` first, and its Step 2 is a deliberate red; plan 3 keeps `scripts/test.sh` green at every commit.
- From Task 4, every `scripts/ios-build.sh` compiles the watch app, and Task 4 Step 3 fails it on purpose; plan 3's Tasks 1, 2 and 9 check with `ios-build.sh`, whose Gradle phase builds `:shared` and `:widgetcore` while plan 3's Tasks 1–2 move files between `:shared` and `:watchcore`.
- Plan 4 writes outside `iosApp/`: `scripts/test.sh`, `scripts/ios-build.sh`, `scripts/watch-build.sh`, `scripts/check-watch-bundle.sh`, `scripts/bump-version.sh`, `tools/add-watch-targets.rb`, `tools/watch-art.py`, `tools/i18n-check.py`, `.github/workflows/ios.yml`, `README.md` and `.gitignore` (Tasks 2 and 4); `watchcore/` tests, vectors and `WatchScreenWords.kt` (Tasks 1, 3, 8, 9); `shared/` `watchfeed/`, `watchface/`, `feature/settings/` and `composeResources/` (Tasks 3, 10); `docs/superpowers/reports/`.

The Lead integrates by merges, never by rebases, so reviewed commits keep their hashes (the execution map, `docs/superpowers/plans/2026-10-24-watch-companion-execution-map.md`): before each task that waits on plan 2 or plan 3, he merges `watch-companion` into `watch-apple`; after every task (each one writes outside `iosApp/`, `tools/add-watch-targets.rb` at least), he merges `watch-apple` into `watch-companion` and runs `scripts/test.sh` there. After taking `watch-companion`, a task's first step regenerates the vectors (`./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors`) and runs `swift test --package-path iosApp/WatchCore`. Once Task 2 is merged, a plan 3 task that changes a vector's answer fails `swift test`: that is the port's guard, and the Lead brings the port along before that task commits. A red step is never committed on either branch. No task here edits `watchface/`, `wearApp/` or plan 3's screens.

**Binding rulings** (ledger `.superpowers/sdd/2026-10-19-watch-companion-1-galaxy-face/progress.md`):
- **Ruling 36** (the Moon's option (d)) is about the Taqwa face, which the Apple Watch does not have. Nothing here changes the face.
- **Ruling 38:** screen readers are a ship gate. Every Taqwa surface on the Apple Watch speaks the essentials in the phone's words (VoiceOver), each complication as one element: on Qibla, which way to turn. Plan 5 hears it on her watch before the store.
- **Ruling 39, amended:** the watch receives the city, the bearing to a whole degree and the rest of plan 2's table, only from his own paired iPhone, never a coordinate. The watch sends the phone nothing but the handshake's echo of the delivery it holds (Ruling 50: a random per-install number, a counter and the fingerprint). Its stored feed, counts and stamps are excluded from backup: that is the watch's half of the policy's Backups paragraph. The iPhone keeps its own copy of the last application context (the city, the bearing, a month of times, his phrases), and plan 2's question 7 adds a Backups sentence for that copy. **That sentence stays.** Task 5B's check on the simulator is evidence for the report, never a reason to drop it: no copy found, or simulator evidence alone, means the sentence stays. Only evidence from a real iPhone could drop it, and no agent can read a device's WatchConnectivity store, so in practice it stays. The promise "Only your own devices can see your location." and the relay sentence are plan 5's to apply.
- **Ruling 42 (the reading order, which replaced Ruling 40):** on the Taqwa face the essentials are read as the time, then the next prayer with its countdown and its time, then the dates: on a prayer face the prayer comes first. Apple's faces read their own clock first and then each complication in their own slot order, which no app can change. What Taqwa controls is each complication's one element: the prayer, the time left and its time in one sentence, never split. On Today, the ring's sentence is read first, then the rows in the order of the day.

## The Lead's calls in this plan

The Lead rules on each in the ledger before the task named. A different ruling changes that task alone.

1. **A Swift port, not a Kotlin/Native watchOS framework** (spec 1.4 and 8 leave it to the builder; before Task 2).
   - **Build complexity, the spec's deciding factor:**
     - A watchOS framework needs three more Kotlin targets in `:watchcore` (`watchosArm64` for arm64_32, `watchosDeviceArm64`, `watchosSimulatorArm64`), and an `embedAndSignAppleFrameworkForXcode` phase in two more Xcode targets.
     - The device archive needs both watch architectures in one framework, which that task builds one at a time.
     - Each watch build would start Gradle.
     - The port is the rules' logic plus a `Decodable` model.
   - **Memory:** the iPhone's widget extension had to drop `shared` for a slim framework under WidgetKit's ceiling (`tools/add-widget-target.rb`'s header). A watch widget extension has less room still, and a Swift port brings no runtime with it.
   - **The contract is the JSON, not the code:**
     - plan 1 wrote `watchcore/vectors/` "for the watchOS port" (16 feeds, 57 cases, 70 countdowns, 8 refreshes), and Tasks 1, 3, 8 and 9 add the rest, plan 3's hand-off among them (the undo cases, `WatchQiblaTest`'s runs, round 2's guidance sequence and plan 3 Task 3's clock cases);
     - plan 2's watch never computes a fingerprint, so no byte of kotlinx's encoder needs copying.
   - **Cost:** two copies of the rules. The vectors hold them together, and every rule change in Kotlin regenerates the vectors, which fails `swift test` until the port follows. Plan 3's rules that live in `:wearApp` (`TasbeehSpeech`, `qiblaScreen`, `today`) have no vectors; Tasks 7–9 restate their tests' cases word for word.
2. **The project file is edited by `tools/add-watch-targets.rb` with the `xcodeproj` gem**, as the widget target was (before Task 4).
   - The gem is not installed on the Mac mini (plan 2, Task 8). Default: `gem install --user-install --no-document xcodeproj` from rubygems.org, a dev tool under Ruling 23's trust, with the installed version written in the script's header.
   - If it will not load on Homebrew's Ruby 4.0.7, the task stops and asks the Lead: Homebrew's keg-only `ruby@3.4`, or a Python editor with fixed object ids.
3. **The watch app and its widgets share the app group `group.world.taqwa.ios`,** the phone's own identifier (before Task 4). A group's container is per device, so the watch's is its own. It is already registered to team 3K93P4PA5H.
4. **One channel: the application context** (before Task 5).
   - The watch reads what plan 2 sends, and nothing is added on the phone.
   - Task 5B observes on the simulators whether a new context reaches a watch app that is not running, after the phone app is brought back so that it reads its location and sends (the iPhone reads its location only when its own triggers fire, `LocationProvider.ios.kt:156`).
   - Plan 5 checks it on her watch: a city changed on her iPhone must reach her complications within an hour without the watch app being opened.
   - If it does not, the phone also sends the same `Data` and delivery through `transferCurrentComplicationUserInfo` while `isComplicationEnabled` (50 a day). That goes to Mohamed as a change to plan 2's sender, on plan 5's device evidence only, never on the simulator's.
5. **The complication timeline** (before Task 6):
   - an entry where each countdown minute begins, for the hour from now: at most 62 entries while every start is on a whole minute (as the phone's are; Ruling 64 item 3), the iPhone widget's 60 a reload (`iosApp/TaqwaWidget/TaqwaWidgetViews.swift:134`). A start inside the hour rolls the entries over to the prayer after it;
   - the entry at the hour's end drops the countdown and the bar's fill: the label and the time alone, spoken as the row is ("Asr, 16:10"), as plan 3's Tile reads below schema 1.2. So a late reload never leaves a stale countdown;
   - reload at the next start or at the hour's end, whichever comes first: about 30 a day plus one per new feed, under WidgetKit's 40–70;
   - a timeline that reaches the feed's end ends in the empty entry, as plan 1's do.
   - **Why not an entry a minute through the period after the next** (round 1's default): that is 600–960 entries a reload, against the iPhone's proven 60, and the round-2 review quotes Apple's "Keeping a widget up to date" as asking for entries about 5 minutes apart or more. Spec 4.3's "one entry per minute up to the next start" holds within the hour; past it, the reload takes over (Task 11's spec edit).
   - **Unproven on watchOS:** whether the face shows each minute's entry while the extension is idle. Task 6 Step 7 watches it on the Series 12 with the app terminated, and plan 5 on her watch (gate). If a minute is dropped or merged, the task stops and the Lead rules; the ready fallback is the countdown-less form throughout.
6. **Settings' "Add to Apple Watch" row is built here,** in its own file, `WatchRows.kt` (Task 10). Plan 5 adds Android's "Install on your watch" row to the same file.
7. **Haptics:**
   - a count is `.click`, an undo `.directionDown`, a part's end `.directionUp`, and the set's end `.success`;
   - each count, undo or crown turn plays at most one: the strongest it crossed (the set, then the part, then the count or undo). No count or undo plays within 0.14 s of a part's or 0.40 s of a set's. That is plan 3's call 14, so the two watches feel alike (`TasbeehFeel`, Task 8);
   - facing the Qibla is one `.success` as the alignment begins.
   - Mohamed judges the feel on her watch in plan 5, and the two windows may change with it.
8. **What the Swift decoder does with text no phone writes** (before Task 1).
   - **The fact:** kotlinx 1.11.0 reads a quoted number or boolean as its value, with or without `isLenient`. `StreamingJsonDecoder.decodeLong` goes through `AbstractJsonLexer.consumeNumericLiteral`, which takes an opening quote; `decodeDouble` parses the string form; `decodeBoolean` uses `consumeBooleanLenient`. So `"writtenAt": "1792303200"` and `"version": "1"` decode as feeds in Kotlin, though `versionOf` refuses a quoted version. Swift's `JSONDecoder` refuses a quoted `Int64`, `Double` or `Bool`.
   - **Default: Swift refuses them.** `WatchFeedJson.encode` writes every number and boolean bare, so a quoted one never comes from a Taqwa phone. A refusal is `.malformed`: the watch keeps the last feed it understood, as for any broken text. The vectors record Kotlin's answer in `result` and Swift's in `swift` for exactly four cases (Task 1), and Kotlin's test holds that list fixed and every `swift` answer a refusal. Swift therefore never accepts a vector case that Kotlin refuses.
   - **Alternative:** Swift copies the leniency. Every `Int`, `Int64`, `Double` and `Bool` field gets a hand-written decode that also takes a quoted form, and the version read takes `"1"`. That is about 40 lines that must copy kotlinx's quoting rules exactly, for text that no phone sends.
   - **Since Ruling 56** (29 October), "never accepts a case Kotlin refuses" holds only where an encoder can write the text: every vector case has a `class`, and on a `FOREIGN` case Swift is held only to no crash and exact values (Global Constraints, "The feed", and Task 2's Interfaces).

## Open questions for Mohamed

None blocks a task: each is built on its default.

1. **Her Apple Watch's model,** which sets the oldest watchOS the app runs on. Default: **watchOS 10.0**. That runs on every Apple Watch from Series 4 and the first SE, all with the Smart Stack and WidgetKit's four accessory families. A Series 4 has no compass, and its Qibla screen shows the bearing and distance with no needle. Only watchOS 27.0 can be simulated here. APIs newer than 10.0 are used behind availability checks, and none newer than watchOS 26, because CI's Xcode 26 compiles the watch targets against the watchOS 26 SDK (Global Constraints). If her watch is an SE 3, the floor could rise to 26.0 later, with nothing visible changing.
2. **When the Smart Stack lifts Taqwa:** default, **for the 30 minutes before each prayer's start**, ending at the start.
3. **The shared face's file** (spec 4.5) has to be made on a real Apple Watch with Taqwa installed. Default: in plan 5, on her watch through her iPhone's TestFlight build, never the work iPhone 12. The face is Modular, as the mockup draws it: Taqwa's rectangular in the middle, its circular at the bottom left, amber. Until the file is bundled the row stays hidden. Task 10 first tries to make it on the simulators.
4. **Colour on the Apple Watch:** default, **amber only**, with no colour setting. Apple's faces tint complications in the face's own colour, and the app's three screens use amber as the phone does.

## Global Constraints

- **Where:** plan 4's own worktree, `~/Desktop/Workspace/apps/Taqwa/.claude/worktrees/watch-apple`, branch `watch-apple` ("Where it runs" above).
  - Start every shell command with `source ~/.zprofile; source ~/.zshrc`, and work from that worktree's root.
  - Nothing is pushed.
  - Commits use `git commit -F <file>`; then check `git log -1`. No `Co-Authored-By` and no AI line, ever. Commit WIP as you go, and continue from WIP an interrupted run left. A deliberately red step is never committed.
- **Ledger:** short dated lines under a "Plan 4" heading in `~/Desktop/Workspace/apps/Taqwa/.claude/worktrees/site/.superpowers/sdd/2026-10-19-watch-companion-1-galaxy-face/progress.md` (git-ignored, so it lives in the `site` worktree only), as each task starts and ends.
- **Devices: simulators only.**
  - The iPhone is iPhone 17 Pro on iOS 27.0, `2D01CE58-7F12-45FC-8DB0-D63330AE9BB1`.
  - The paired watch is Apple Watch Series 12 (46mm) on watchOS 27.0, `C803D94B-7239-435B-9CEB-2A6AD75F141F`, plan 2's pair. Plan 2's Task 8 Step 5 uses the same pair: the two take turns, and the Lead says whose turn it is.
  - **What an `ios-pair` task finds, and how it leaves it** (Ruling 52 item 8, as Ruling 53 item 4 worded it). Each `ios-pair` task (5B, 6 Steps 6–7, 10 Step 5, 11) leaves both simulators as it found them, as the execution map's locks require. Plan 2's Task 8 Step 5 and its reviews left them unpaired (`xcrun simctl list pairs` empty), both shut down, and the Series 12 with no Taqwa app: that is what was observed on 25 October after their `xcrun simctl unpair`. The Series 12 never had the watch app then, so whether an unpair erases a simulator watch was not known then, and no task relies on either. **Task 5B found that it does** (its as-built note): `xcrun simctl unpair` shut the Series 12 down and re-made its data folder, in the task's run and again in its review round 1, so a task that pairs and unpairs leaves a re-made watch. So, whenever `xcrun simctl list pairs` shows no pair, a task pairs the two, installs the watch app (`Watch/TaqwaWatch.app`) as well as the phone's, and sets up again whatever its steps need (a face, the Smart Stack, the watch's language), instead of expecting them from an earlier task. Before it releases the lock it puts back what it found: it removes what it installed where there was none (`xcrun simctl uninstall`), restores what it changed, and unpairs the pair it made (`xcrun simctl unpair`), the one unpair allowed below.
  - The small screen is Apple Watch SE 3 (40mm) on watchOS 27.0, `FCA316A7-A49B-4795-9610-7D61FD958A8A`, unpaired, plan 4's alone, for screenshots and UI tests with the debug sample.
  - Never use the work iPhone 12, his or her phones and watches, or the plugged-in phone `KB2604110095`.
  - Never erase a simulator, and never unpair a pair the task did not make: an `ios-pair` task unpairs only its own, to leave the two as it found them (above). Shut down only those you booted (`xcrun simctl shutdown <udid>`).
- **Builds:**
  - Xcode comes from `scripts/xcode.sh`.
  - Simulator builds need no team.
  - Never archive, export or upload, and never run `scripts/ios-release.sh` (plan 5).
  - `TEAM_ID` and `BUNDLE_ID` come from `iosApp/Configuration/Config.xcconfig`, never as literals.
  - No `xcodebuild` call names `-sdk` for a scheme that builds the watch app. `-sdk` overrides SDKROOT for every target in the build, the embedded watchOS ones included. `-destination` alone gives each target its own simulator SDK, and the products stay in `build/ios-dd/Build/Products/Debug-iphonesimulator/` (the iPhone's) and `Debug-watchsimulator/` (the watch's).
  - CI (`macos-26`, the image's Xcode 26.x) compiles the watch targets with the watchOS 26 SDK and has no watchOS 27 runtime. So no API newer than watchOS 26 is used. CI builds the watch for `generic/platform=watchOS Simulator`, and a CI step installs the watchOS platform if the image lacks it (Task 4). The UI tests run on the Mac only: an unsigned build has no app group, so it cannot store the sample feed.
- **The watch targets:**
  - SDKROOT `watchos`, `TARGETED_DEVICE_FAMILY` 4, `WATCHOS_DEPLOYMENT_TARGET` 10.0 (question 1), `SWIFT_VERSION` 5.0, automatic signing.
  - Bundle ids `$(BUNDLE_ID).watchkitapp` and `$(BUNDLE_ID).watchkitapp.widgets`.
  - The app's Info.plist has `WKApplication` YES, `WKCompanionAppBundleIdentifier` `$(BUNDLE_ID)` and `WKRunsIndependentlyOfCompanionApp` NO.
  - Their `CFBundleShortVersionString` and `CFBundleVersion` equal the iPhone app's, and `scripts/bump-version.sh` writes all four plists.
- **What the watch app never does:**
  - It has no network code (no `URLSession`), and no `NSAppTransportSecurity`.
  - It never asks for location: no `NSLocation*UsageDescription`, and no `requestWhenInUseAuthorization`, `requestAlwaysAuthorization`, `startUpdatingLocation` or `requestLocation`. Heading comes from `startUpdatingHeading` alone, with the feed's declination (spec 4.4), as the iPhone's own `IosCompassSource` does.
  - No HealthKit, no Core Motion (so no tilt), no `UIBackgroundModes`.
  - It schedules no notifications.
  - It links no Kotlin framework.
- **No time is computed on the watch.** Every instant shown is the feed's, and every clock time is the feed's string. No `DateFormatter`, `NumberFormatter`, `Date.FormatStyle` or `Text(_:style:)` is used on a Taqwa surface. App = site = watch.
  - Numbers the watch writes itself (countdowns, counts, degrees) go through `WatchRules.digits`, in the feed's `digitZero`.
  - **Today's H:MM:SS** goes through `WatchRules.inDigits(_:_:)` with `ui.ringDigitZero ?? ui.digitZero`: the phone ring's digits, which are Western on ar-SA, ar-EG and Bengali phones (`CountdownDigits.westernFallback`, plan 3's call 9).
  - **A drawn time** on Today is `WatchRules.clockText` (a 12-hour phone's day period included). On the complications it is the bare `day.clock`, as the Galaxy's complications draw it (plan 3 Task 11).
  - **A spoken time** is `WatchRules.spokenClock`, everywhere. No line passes the bare `clock` to a spoken phrase.
- **The feed:**
  - On every text a phone writes (`WatchFeedJson.encode`'s, so every golden and sample), the Swift decoder gives `WatchFeedJson.decode`'s answer: versions 1 to `VERSION`, unknown keys ignored, defaulted keys optional, everything else required. A `null` where Kotlin has a non-null default is refused, as kotlinx refuses it.
  - On every vector case, Swift is held by the case's `class` (Ruling 56, which narrowed the Lead's call 8 to what an encoder can write). On an `ENCODER` or `ONE_CHANGE` case it answers exactly `swift ?? result`, and for a feed decodes exactly `expect`; `swift` is on the four quoted cases and, since **Ruling 57 item 2**, on one lenient case for each other field the port reads (`quotedFajr` to `quotedTarget`), where kotlinx reads a value of the wrong type leniently (a quoted number or boolean, a bare digit for `digitZero`) and Swift refuses it: an accepted difference in the safe direction, since no phone writes a wrong type and a refusal keeps the last feed. Plan 3's fields get their lenient cases in Task 3, when the port reads them. On a `FOREIGN` case, a text no encoder configuration the app uses can write, it never crashes or hangs, and where it answers feed every value it read is exactly the text's JSON value (no truncation or rounding, the `Int32` and `Int64` ranges checked); Kotlin's answer there is information, not asserted. **Ruling 57 item 1** bounds "exactly": it holds as far as Foundation's `Decimal` keeps a number's digits (about 38 significant digits). Past that, a `FOREIGN` number may read as its rounded whole value. `ENCODER` and `ONE_CHANGE` texts hold plain integers, so they are unaffected. A refusal is always a right answer on a `FOREIGN` case, so no Swift test may fail a port for refusing one (Ruling 57 item 3).
  - The keys are `WatchLinkContract`'s, held equal by a vector.
  - **The delivery (plan 2's call 1, Ruling 50).** Every context carries `delivery`, `WatchDelivery.text` (`origin/seq/fingerprint`: a random per-install number, the delivery number and the fingerprint, each checked as `WatchLinkContract.deliveryOf` checks it, the number read as `Int64`, since watchOS's `Int` can be 32 bits). The watch stores a feed only when `WatchLinkContract.stores` says so: another origin than the one it holds, or a greater number; anything else is stale or a duplicate and is dropped unstored. A greater number for the very feed held is stored too, its delivery written anew (only the widget reload is skipped), so the echo names the newer number. It stores in `WatchLinkContract.STORE_STEPS`'s order: the delivery file removed before the feed is put in place, the arriving delivery written after, none when that write fails. Its handshake echoes `echoOf` the delivery it holds, or "". The delivery and the fingerprint are echoed, never computed.
- **Storage:** the feed, its delivery, the newer-version notice, the handshake stamp and the tasbeeh counts are files in directories marked `isExcludedFromBackup`. Each write is atomic.
- **Words** (spec 1.3):
  - Every word a Taqwa screen or complication draws or speaks comes from the feed: `feed.words` (`ui.screens.orEnglish()`, plan 3's call 3: a blank word is English, which only a development feed lacks), the day's `names`, the clock strings, `nextIn`, the countdown and spoken phrases, the city and the distance.
  - The exceptions are spec 1.3's system-shown list (the app's name, the widget gallery's name and description) and the two lines a watch shows before it holds a feed: `open_taqwa_on_phone`, and `update_taqwa_on_watch` once a newer format has been seen (plan 3's call 5). These live in the watch targets' seven `.lproj` folders and follow the *watch's* language.
  - New phrases carry "draft, to native review (plan 5)" in every language but English, and in Arabic too unless the spec gives the Arabic.
- **Direction:**
  - Every view root and widget view sets `.environment(\.layoutDirection, feed.ui.rtl ? .rightToLeft : .leftToRight)`.
  - The tasbeeh ring fills clockwise in every language.
  - Today's ring fills anticlockwise in a right-to-left language, as the phone's `RingArc` does.
  - The period bar fills in reading order.
  - The crown's arrows stay on the crown's side.
  - The Qibla dial never mirrors.
- **Type:**
  - Manrope Light, SemiBold and ExtraBold under a Latin-script interface (`ui.latinScript`), referenced from `shared/src/commonMain/composeResources/font/`, never copied.
  - The system font, with no tracking, otherwise (ar, ur, bn): the phone's rule (`TaqwaTypography`).
  - Capitals and transliterations only under `ui.latinScript`; the complications' label is `WatchRules.label` (Task 1's `labels` vector), as `ComplicationFields.nextPrayer` writes it.
  - The dhikr's Arabic is in the system font, as the phone's `DhikrBlock` draws it (`FontFamily.Default`).
- **Colours:** black `#000000`, ink `#F1F3F1`, mist `#8C948F`, slate `#5A625D`, hairline `#232825`, surface `#131614`, the Smart Stack card `#1A1D1C`, and amber `#F0B429`. Accent shapes are `.widgetAccentable()`, so tinted faces read them in one colour.
- **Copy:** the countdown is the feed's phrases (English `in %1$s h %2$s min` …, Arabic `بعد %1$s س %2$s د` …), filled by `WatchRules.countdown`. The crown hint in English is exactly "Turn the crown or tap".
- **VoiceOver** (Ruling 38):
  - A prayer's name is said as the phone wrote it ("Asr"), never in the capitals drawn ("ASR").
  - Times left are the feed's spoken words (`ui.spoken`), with the screen's words only when a feed has none. Every time in a spoken line is `spokenClock`.
  - Tasbeeh speaks plan 3's spoken phrases (`TasbeehSpeech`: `countSpoken`, `nameRoundSpoken`, `nextSpoken`, `setCompleteSpoken`, `roundSpoken`, with `countAction` and `undoAction`), never the drawn labels.
  - Qibla speaks the turn while searching (`turnRight`, `turnLeft`), as plan 3's dial does.
  - Each complication is one element. Decorative shapes are hidden.
  - Nothing drawn changes for VoiceOver.
- **Not touched:** `watchface/`, `wearApp/`, plan 3's screens, and the promise lines (plan 5).

## Review Focus

1. **A watch without a usable feed:** none yet, one past its last start, one from a newer phone, one from an older phone that lacks the defaulted fields, a malformed one, and a reload that comes late. Every surface shows its empty or message form in plan 3's call 5 order (a newer format seen says "Update Taqwa on your watch", with a feed or without), keeps the last feed it understood, never shows a stale or negative countdown, and never crashes. Tests: Task 2 `testEveryDecodeCaseMatchesTheVectors`; Task 3 `testAnOlderFeedSpeaksEnglish`; Task 5 `testANewerVersionKeepsTheOldFeedAndRemembersTheNotice`; Task 6 `testASpentFeedEndsInTheEmptyEntry`, `testNoFeedIsOneEmptyEntry`, `testANewerFormatSeenSaysUpdateInTheEmptyForm` and `testPastTheHourTheCountdownIsDropped`; Task 7 `testNoFeedSaysOpenTaqwaOnYourPhone`, `testNoFeedAfterANewerFormatSaysUpdate` and `testASpentFeedIsSpent`.
2. **A watch whose zone is not the place's,** a traveller or a phone set to another city: the list and the complications turn the day over at the feed's midnights, never the watch's. Tests: Task 2 `testDaysMatchTheVectors`; Task 7 `testTheListTurnsOverAtTheFeedsMidnightNotTheWatchs`, run under `Pacific/Auckland`.
3. **The minute boundary, in every digit set:** 72 minutes left reads "in 1 h 12 min" and 71 min 59 s "in 1 h 11 min", and a complication's minute shows the value that holds for that minute. Each string is in one digit set, the feed's `digitZero`, but Today's H:MM:SS, which follows the phone's ring (`ringDigitZero ?? digitZero`): an ar-SA watch reads "1:11:36" above "١٦:١٠", exactly as the ar-SA phone does. Tests: Task 2 `testEveryCountdownMatchesTheVectors`; Task 3 `testTheSaudiRingIsWestern`; Task 6 `testTheEntriesBreakWhereTheCountdownChanges`, `testUnderAMinuteIsTheLastMinute` and `testArabicKeepsTheNameAndTheDigits`; Task 7 `testSaudiTimesInArabicIndicAndTheRingInWestern`.
4. **The crown turned fast or backwards:** several detents in one event, back at 0, back across a part's end, forwards past the hundred during the second the full ring is held. No count is lost or doubled, undo stops at 0 within the round, the next round opens as the phone's does, and one turn gives one haptic. A count or an undo inside the hold cancels its rollover, and leaving the screen mid-hold saves the round about to open. Tests: Task 8 `testTheCrownCountsWholeDetents`, `testAFastSpinPastTheEndOpensTheNextRound`, `testACrownSpinThroughTheHundredDuringTheHoldKeepsEveryCount`, `testAnUndoDuringTheHoldIsKept`, `testLeavingDuringTheHoldSavesTheNextRound`, `testUndoAtZeroStaysAtZeroInTheRound`, `testUndoAcrossAPartBoundaryShowsTheEarlierPart`, `TasbeehFeelTests`, the tasbeeh vectors, and the UI tests `testAFastSpinFrom99OpensRoundTwo` and `testASlowTurnCountsAndATurnBackUndoes`.
5. **Core Location's sentinels, the declination and the seam:** a negative magnetic heading, a negative accuracy, no declination, no compass at all, a place whose declination is large (Cape Town's −25°), and 359° to 1°. No needle points at nothing, the needle points at true north's bearing and not the magnetic one, the filter never swings through 180°, and a VoiceOver user who is searching hears which way to turn. Tests: Task 9 `testANegativeHeadingNeverDrawsANeedle`, `testNoDeclinationNeverDrawsANeedle`, `testTheDeclinationTurnsMagneticIntoTrue`, `testTheSeamDoesNotSwingTheNeedle`, `testNoCompassShowsTheBearingAndDistanceOnly`, `testEveryDialRunMatchesTheVectors` and the UI test `testSearchingSpeaksTheTurn`.

---

### Task 1: `:watchcore`: the vectors the Swift port reads beyond plan 1's

**Files:**
- Create: `watchcore/src/androidHostTest/kotlin/world/taqwa/app/watch/SwiftPortVectorsTest.kt`, `watchcore/vectors/feed-vectors.json` (generated).

**Interfaces:**
- Consumes: `WatchFeedJson.decode`/`versionOf`, `WatchRules.spokenCountdown`/`today`/`dayStart`, `WatchLinkContract` (plan 2 Task 3), `Fixtures` (commonTest), and the four `sample-feed-capetown*.json`.
- Produces `watchcore/vectors/feed-vectors.json`, with an `about` line and five sections (Task 3 adds two more):
  - `decode`: `{name, json, result: "feed"|"newer"|"malformed", swift?: "malformed", expect?}`. `result` is Kotlin's answer. `swift` is present only where Swift's answer differs (the Lead's call 8). For a feed, `expect` holds `days`, `day0HijriDayMonth`, `day0GregorianSpoken`, `day0Midnight`, `digitZero`, `hasSpoken`, `phrases` and `city`. The cases:
    - `sample`: `sample-feed-capetown.json`;
    - `olderPhone`: the sample without `hijriDayMonth`, `gregorianSpoken`, `midnight`, `spoken`, `tasbeeh` and `digitZero`;
    - `newerPhone`: `"future": 1` at the top and in `place`, `ui`, `ui.spoken`, a day, its `times` and a phrase (since Ruling 60 item 1d also in `ui.screens`, its first `turnRight` count and day 0's `clockTime`, so it is the twelve-hour English sample with the first sample's phrases, and since that item's review round 1 in `ui.countdown`: eleven places, one in each class of the model, which `aLaterPhonesKeyIsIgnoredInEveryObject` holds to the serializers' own classes);
    - `version2` is newer;
    - `version0`, `notJson` (`{`), `noDays`, `nullInADefaultedField` (`"hijriDayMonth": null`) and `twoCharacterZero` (`"digitZero": "01"`) are malformed;
    - `emptyDays` is a feed with 0 days;
    - **the four known differences,** each a feed in Kotlin with `swift: "malformed"`: `quotedWrittenAt` (`"writtenAt": "1792303200"`), `quotedVersion` (`"version": "1"`), `quotedBearing` (`"qiblaBearing": "23.0"`) and `quotedRtl` (`"rtl": "false"`).
  - `spoken`: `{feed, name, clock, secondsLeft, spoken|null}` for each of the four samples, with day 0's Asr, at `secondsLeft` 0, 59, 60, 119, 3600, 4319, 4320, 7199, 36000 and 359999, plus `olderPhone` (null).
  - `labels`: `{feed, day, prayer, label}`. The label is `day.names.of(prayer).uppercase(Locale.forLanguageTag(feed.ui.language))`, exactly as `ComplicationFields.nextPrayer` writes it. The cases are each sample on day 0 (Saturday 17 October, since the samples start yesterday) and day 6 (Friday 23 October, whose Dhuhr is the Jumuʿah), plus a copy of the sample with `language` "tr" and Asr named "ikindi" (`İKİNDİ`), and with "en" (`IKINDI`).
  - `days`: `{feed, now, today|null, dayStart|null}` at each day's midnight − 1, midnight and midnight + 1 for days 0, 1 and 30 of the sample, and the same on `Fixtures.tromsoMay()`, whose midnights are 0 (the solar-midnight fallback).
  - `contract`: the four context and message keys, `FEED_BUDGET_BYTES`, `ENVELOPE_BYTES`, `USER_INFO_LIMIT_BYTES` and `CONTEXT_LIMIT_BYTES`, and `fingerprints: [{text, valid}]` from `isFingerprint` for "0123456789abcdef", "0123456789ABCDEF", "0123456789abcde", "0123456789abcdef0", "" and "0123456789abcdeg". Ruling 50 adds the receivers' rules, from `WatchLinkContract` itself: `deliveries: [{text, origin?, seq?, fingerprint?}]` from `deliveryOf` (a valid one, number 1 and `Long.MAX_VALUE`, and every refused form of `WatchLinkContractTest.anythingElseIsNoDelivery`: "", a bare fingerprint, a number 0, a sign, a leading zero, an overflow, capitals, an extra segment); `stores: [{held?, arriving, stores}]` from `stores` (nothing held, a greater number, the same feed under a greater number, the same number, a lower one, another origin); `echoes: [{held?, echo}]` from `echoOf`; and `storeSteps`, the order of `STORE_STEPS`.

- [ ] **Step 1: Write `swiftPortVectorsAreUpToDate`,** in the pattern of `VectorsTest`. It writes the file under `-Ptaqwa.updateVectors` and otherwise fails if the file differs. Write three more tests with it:
  - `decodeCasesAreWhatTheDecoderSays`: every `decode` case's `result` is `WatchFeedJson.decode`/`versionOf`'s own answer, so the file cannot hold a wish. The four known differences come out `feed`.
  - `swiftDiffersOnlyOnTheFourQuotedCases` (since Ruling 57 item 2, `swiftDiffersOnlyWhereKotlinReadsAWrongType`: these four and the lenient cases): the cases carrying `swift` are exactly `quotedWrittenAt`, `quotedVersion`, `quotedBearing` and `quotedRtl`. Each has `result` `feed` and `swift` `malformed`. A new difference needs a new Lead's ruling, never a quiet line in the file. If kotlinx refuses one of the four after all, that case drops its `swift` and leaves the list, and the ledger records it.
  - `theFridayLabelIsTheJumuah`: each sample's day 6 is dated 2026-10-23, and its label is the upper-cased Jumuʿah name, not `DHUHR`.
- [ ] **Step 2:** Run `./gradlew :watchcore:testAndroidHostTest`. Expected: FAIL, no `feed-vectors.json`.
- [ ] **Step 3:** Run `./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors`, then again without the flag. Expected: PASS, and `git status` shows only the new file.
- [ ] **Step 4: Commit** with the message "Watch core: the decode cases, spoken countdowns, labels, days and link keys the watchOS port reads".

### Task 2: `WatchCore` in Swift: the feed, its decoder and the rules, against the vectors

**Files:**
- Create:
  - `iosApp/WatchCore/Package.swift` (tools 6.0; platforms `.macOS(.v14)` and `.watchOS(.v10)`; library `WatchCore`; test target `WatchCoreTests`; `swiftLanguageModes: [.v5]`);
  - `iosApp/WatchCore/Sources/WatchCore/Feed.swift`, `FeedDecoder.swift`, `WatchRules.swift`;
  - `iosApp/WatchCore/Tests/WatchCoreTests/Vectors.swift`, `RulesVectorsTests.swift`, `FeedVectorsTests.swift`, `PhoneGoldensTests.swift`.
- Modify:
  - `scripts/test.sh`: on Darwin with no arguments, `swift test --package-path iosApp/WatchCore` runs before Gradle, and a failure stops the script;
  - `.github/workflows/ios.yml`: a step "The Apple Watch's Swift core against the vectors";
  - `.gitignore` (`iosApp/WatchCore/.build/`);
  - `README.md`'s module tree.

**Interfaces:**
- Consumes: `watchcore/vectors/watch-rules-vectors.json`, `feed-vectors.json` (Task 1), `phone/*.json` (plan 2) and the four samples.
- Produces, all `internal`, with Kotlin's names and JSON keys exactly. Times are `Int64` epoch seconds, and every Kotlin `Int` is an `Int32` (Ruling 56 item 4).
  - `struct WatchFeed: Decodable, Equatable { version: Int32; writtenAt: Int64; days: [WatchDay]; place: WatchPlace; ui: WatchUi; tasbeeh: [WatchPhrase] }`
  - `WatchDay`, `WatchTimes`, `WatchSix<T>` (with `func of(_ prayer: WatchPrayer) -> T` and `sunrise`), `WatchPlace`, `WatchUi` (`digitZero: Character`), `WatchCountdownPhrases`, `WatchSpoken`, `WatchCount` (`upTo: Int32`) and `WatchPhrase` (`target: Int32`), each field as `WatchFeed.kt` declares it at the branch head. Task 3 adds plan 3's fields.
  - Kotlin's defaults are read only when the key is there (`contains`, then `decode`), with the same default when it is not, so a present `null` for a non-optional field throws; a Kotlin `T? = null` is `decodeIfPresent`.
  - `enum FeedDecodeResult: Equatable { case feed(WatchFeed), newerVersion, malformed }` and `enum FeedDecoder { static let version = 1; static func decode(_ data: Data) -> FeedDecodeResult }`.
  - **How it decodes (Ruling 56 item 4, which replaced Ruling 55 item 5; erratum p4 #1):**
    - The port reads the root's version first. A newer version answers newer without decoding the body.
    - It then decodes the body with Foundation's `JSONDecoder`, with strict `Int32`/`Int64` types (Kotlin `Int` is `Int32`, `Long` is `Int64`, `digitZero` is one UTF-16 unit) and default strategies. No field gets a lenient decode, so a quoted number or boolean is refused (the Lead's call 8).
    - It passes every `ENCODER` and `ONE_CHANGE` case exactly, and every `FOREIGN` case without a crash: if it answers feed, every value it read is exactly the text's value, as far as Foundation's `Decimal` keeps a number's digits (about 38 significant digits, Ruling 57 item 1).
    - It has no JSON grammar scanner of its own.
  - `enum WatchPrayer: String { case FAJR, DHUHR, ASR, MAGHRIB, ISHA }` (the vectors' spelling), `struct WatchStart: Equatable { prayer; at: Int64; dayIndex: Int }` and `struct WatchMoment { previous; next; secondsLeft: Int64; progress: Float }`.
  - `enum WatchRules`, each function a line-for-line port of `WatchRules.kt`, the private helpers included:
    - `starts(_:)`, `moment(_:now:)`, `countdown(_:secondsLeft:)`, `spokenCountdown(_:name:clock:secondsLeft:)`, `counted(_:_:_:)`, `digits(_:_:)`;
    - `litOnList(_:now:)`, `litOnGlance(_:now:)`, `needsRefresh(_:now:)`, `today(_:now:)`, `dayStart(_:_:)`;
    - plus `label(_ feed: WatchFeed, _ prayer: WatchPrayer, day: Int) -> String`, which is that day's name upper-cased as Kotlin's `uppercase(Locale.forLanguageTag(feed.ui.language))` cases it on the JVM (Ruling 57 item 6): by the tag's primary language subtag (`tr-u-ca-gregory` is Turkish), and in Turkish, Azerbaijani or Lithuanian casing only, the default otherwise. P4.2 built it as `uppercased(with: Locale(identifier: feed.ui.language))`, which misses the first.
    - Filling `%1$s` is text replacement, never `String(format:)`.

- [ ] **Step 1: Write the failing tests.** `Vectors.swift` finds `watchcore/vectors/` from `#filePath`.
  - `testEveryRuleCaseMatchesTheVectors`: all 57 cases. next and previous are equal; `secondsLeft` and `countdown` are equal; progress is within the file's tolerance; `litOnList` and `litOnGlance` are equal.
  - `testEveryCountdownMatchesTheVectors` (70) and `testEveryRefreshMatchesTheVectors` (8).
  - `testEveryDecodeCaseMatchesTheVectors`, by class (Ruling 56): each `ENCODER` and `ONE_CHANGE` case's `swift ?? result`, and for a feed each `expect` field; each `FOREIGN` case decodes without a crash, and where it answers feed, its values are the text's JSON values.
  - `testTheKnownDifferencesAreRefusals`: the four cases with `swift` decode `.malformed`, and no `ENCODER` or `ONE_CHANGE` case whose `result` is `malformed` or `newer` decodes `.feed`.
  - `testSpokenCountdownsMatchTheVectors`, `testLabelsMatchTheVectors` and `testDaysMatchTheVectors`.
  - `testEveryPhoneGoldenDecodes`: each `phone/*.json` is `.feed`, with 31 days and a non-empty `ui.language`.
  - `testTheFourSamplesDecode`.
- [ ] **Step 2:** Run `swift test --package-path iosApp/WatchCore`. Expected: FAIL, the types are missing. Not committed.
- [ ] **Step 3: Implement the model, the decoder and the rules.**
- [ ] **Step 4:** Run `swift test --package-path iosApp/WatchCore`, then `scripts/test.sh`. Expected: PASS, with every vector file read (the test counts its cases and fails on an empty section).
- [ ] **Step 5: Commit** with the message "Apple Watch: the feed and its rules in Swift, held to the Kotlin vectors".

### Task 3: the words, the 12-hour clock and the ring's digits the Apple Watch reads, and two words of its own

**Starts after plan 3's Task 10**, with `watch-companion` merged into `watch-apple` (the series above). Plan 2's Lead's call 6 and plan 3's hand-off: plan 3 adds the words both watches write, and this task adds the two only the Apple Watch writes, to the same `WatchScreenWords`, through the same reader.

**Files:**
- Modify (Kotlin):
  - `watchcore/src/commonMain/kotlin/world/taqwa/app/watch/WatchScreenWords.kt`: `crownHint` and `appName`, with their `ENGLISH` values;
  - `watchcore/src/commonTest/kotlin/world/taqwa/app/watch/WatchScreenWordsTest.kt`;
  - `watchcore/src/commonTest/kotlin/world/taqwa/app/watch/Fixtures.kt`: `ARABIC_SCREENS` gains the two;
  - `shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/WatchScreenWordsReader.kt`: `appName` is read as `app_name` through `ScreenStrings.text`, and `ResourceScreenStrings`' explicit map, wherever plan 3's Task 10 put it, gains `"app_name" to Res.string.app_name` (a key not in its maps throws); `crownHint` comes from `WatchWords`;
  - `shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/WatchWords.kt`: the `crownHint` phrase;
  - `shared/src/androidHostTest/kotlin/world/taqwa/app/watchfeed/WatchScreenWordsReaderTest.kt`;
  - `watchcore/src/androidHostTest/kotlin/world/taqwa/app/watch/SwiftPortVectorsTest.kt` (Task 1's): the `clock` and `words` sections, and the new `expect` fields;
  - regenerated: `watchcore/vectors/phone/*.json`, the four `sample-feed-capetown*.json` and `feed-vectors.json`;
  - plan 2's document, the "What leaves the phone" table (Step 5).
  - **Not changed:** `WatchFeedInputs`, the sync's `feedFor` and `WatchFeedBuilder`. Since plan 3's Task 10, `feedFor` resolves `watchScreenWords(watchUiLanguage())` into `WatchFeedInputs.screens` and the builder copies it, so two more fields travel with no edit there. `BundledScreenStrings` reads any key of a `strings.xml`, and `app_name` is in all eight folders.
- Modify (Swift): `iosApp/WatchCore/Sources/WatchCore/Feed.swift`, `WatchRules.swift`.
- Create (Swift): `iosApp/WatchCore/Sources/WatchCore/WatchScreenWords.swift`; `iosApp/WatchCore/Tests/WatchCoreTests/WatchScreenWordsTests.swift`, `ClockVectorsTests.swift`.

**Interfaces:**
- Consumes plan 3's Task 3 (`WatchScreenWords` with `ENGLISH` and `orEnglish()`, `WatchFeed.words()`, `WatchUi.screens`, `ringDigitZero` and `latinScript`, `WatchDay.clockTime`, `WatchRules.filled`, `inDigits`, `clockText` and `spokenClock`) and its Task 10 (`watchScreenWords(language, strings)`, `ScreenStrings`, `ResourceScreenStrings`, `BundledScreenStrings`, the builder's `ringDigitZero` and `clockTime`).
- Produces in Kotlin, on `WatchScreenWords`, both `""` by default, so `VERSION` stays 1:
  - `crownHint` (ENGLISH "Turn the crown or tap"), a new `WatchWords` phrase:
    - en "Turn the crown or tap";
    - ar "أدِر التاج أو اضغط";
    - fr "Tournez la couronne ou touchez";
    - tr "Digital Crown’u çevirin veya dokunun";
    - id (and `in`) "Putar Digital Crown atau ketuk";
    - ur "کراؤن گھمائیں یا تھپتھپائیں";
    - bn "ক্রাউন ঘোরান বা ট্যাপ করুন".
    All but the English are drafts. The Turkish apostrophe follows the Compose rule `tools/i18n-check.py` holds.
  - `appName` (ENGLISH "Taqwa"): the phone's `app_name` in the interface language.
  - `ARABIC_SCREENS`: `crownHint` "أدِر التاج أو اضغط", `appName` "تقوى" (`values-ar`).
- Produces `feed-vectors.json`'s new parts, from Kotlin's own functions:
  - each feed's `expect` gains `ringDigitZero` (null for none), `latinScript`, and `day0Asr: {clockText, spokenClock}`;
  - `olderPhone` also lacks `screens`, `ringDigitZero`, `latinScript` and every `clockTime`;
  - `clock`: `{feed, day, time, clockText, spokenClock}` for the four samples, days 0 and 6, all six `WatchTime`s (Sunrise among them, as plan 3's hand-off asks), from `WatchRules.clockText` and `spokenClock` (plan 3's clock cases);
  - `words`: `{feed, words}` for the four samples and `olderPhone`, where `words` is `WatchFeed.words()` encoded, so that Swift's `orEnglish` is held to Kotlin's (since Ruling 60 item 1e also `presetIds`, the presets' ids in Kotlin's order).
- Produces in Swift:
  - `WatchUi.screens: WatchScreenWords` (default empty), `ringDigitZero: Character?` (absent or null is nil; anything but one character is malformed, as `digitZero`), `latinScript: Bool` (default true), and `WatchDay.clockTime: WatchSix<String>?` (default nil);
  - `struct WatchScreenWords: Decodable, Equatable`: every field of the Kotlin class, its JSON key and its default, `static let english` with `ENGLISH`'s values, and `func orEnglish() -> WatchScreenWords` (every blank string, empty list and empty map takes `english`'s; `presets` is `english`'s map overlaid by the feed's);
  - `extension WatchFeed { var words: WatchScreenWords }`, which is `ui.screens.orEnglish()`;
  - on `WatchRules`, ported from plan 3's: `filled(_ pattern: String, _ parts: String...) -> String`, `inDigits(_ text: String, _ zero: Character) -> String`, `clockText(_ day: WatchDay, _ time: WatchTime) -> String` and `spokenClock(_ day: WatchDay, _ time: WatchTime) -> String` (FSI U+2068 and PDI U+2069 around a time holding anything but digits and ':'). Sunrise is `.SUNRISE`, on `clockTime?.sunrise ?? clock.sunrise`, as plan 3's `dayRows` draws it;
  - plan 3's key of a day's six times, ported: `enum WatchTime: String, CaseIterable { case FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA }`, with `static func of(_ prayer: WatchPrayer) -> WatchTime`, and `WatchSix.of(_ time: WatchTime) -> T`.
- **As built (30 October, Lane A):**
  - `appName` sits after `qibla` and `crownHint` after `bezelHint`, in Kotlin and Swift alike, so the screens' object still ends with `turnLeft`. `WatchFeedJsonTest`'s hand-written document of every key names both. `WatchScreenWordsReaderTest`'s `FROM_THE_PHONE` table gains `appName` from `app_name`, so `theWordsAreThePhonesOwn` holds it in all seven languages. The Turkish hint uses the typographic apostrophe (`Digital Crown’u`), the resources' house style.
  - `WatchSix.of(_ prayer:)` (Task 2's) is replaced by `of(_ time: WatchTime)`, as Kotlin has it, with `WatchTime.of(prayer)` at `label`. The `clock` section already existed (P4.1, after erratum p4 #2); this task adds `clockForms` to it.
  - Ruling 57 item 4: `nullScreens`, `nullLatinScript`, `twoCharacterRingZero` and `numberClockTime` are ONE_CHANGE decode cases (Kotlin malformed, measured by the generator), and `screens` and `latinScript` join Swift's null-key test and `TextValues.defaults` (`WatchUi.screens` as an object of defaults, each word "", [] or [:]; `WatchUi.latinScript` true). A null in any one of the 36 words is refused too: `plan3sKeysAreRefusedAsKotlinRefusesThem` measures Kotlin on each, and `testANullInEveryWordIsRefused` holds the port.
  - Ruling 57 item 2 for plan 3's fields: `quotedTurnRightUpTo`, `quotedTurnLeftUpTo`, `bareRingDigitZero` and `quotedLatinScript`, ONE_CHANGE with `swift: malformed`. oneDay's ring zero is null, so `bareRingDigitZero` writes the bare `0` there, which Kotlin reads as `'0'`.
  - Two ENCODER feeds beyond the plan: `blankWords`, whose words are blank or not by Kotlin's `isBlank` (U+001C is blank, U+0085 is not, the reverse of Foundation's `whitespacesAndNewlines`), with an empty and a non-empty list and an id the English lacks; and `clockForms`, Bengali, extended Arabic-Indic, Devanagari and mathematical bold digits and three 12-hour forms. The port's `isBlank` and `spokenClock` test UTF-16 units, as Kotlin iterates `Char`s.
  - Swift's `presets` is a `[String: String]`: Foundation's decoder keeps no order. Every comparison is by key, and the watch lists the built-ins in its own order (Task 8).
  - Ruling 58 item 3: no clock string is worked out on the watch. `clockText` is `clockTime ?? clock`, as the phone sends them.
  - `DebugSampleTests`' reference rebuilds each day with its `clockTime`, which a moved sample keeps.
  - **Ruling 60 (30 October, Lane A), the test gaps the reviews found, with no behaviour change:** `clockForms` gains day 2, clock strings in numbers that are no decimal digit (superscript and circled digits, circled numbers, the Roman numeral ten), which `spokenClock` isolates, so a port that reads `Character.isNumber` or a `numericType` as a digit fails; `blankWords` gives U+001D, U+001E and U+001F a word each, and `testABlankWordIsKotlinsBlank` takes them, so a blank rule that ends at U+001E or takes U+001C alone fails; `testTheSamplesCarryEveryScreenWord` checks blank by `WatchScreenWords.isBlank`; `newerPhone` holds `future` in every kind of object the feed holds, each of the model's eleven classes once, `ui.countdown`'s `WatchCountdownPhrases` among them since the review's round 1 (above, Task 1; Kotlin holds the places to the serializers' classes, so a class added to the model fails until `newerPhone` holds one there), held on both sides by `aLaterPhonesKeyIsIgnoredInEveryObject` and `testALaterPhonesKeyIsIgnoredInEveryObject`; and each `words` case carries `presetIds`, its presets' ids in Kotlin's order (`TasbeehPresets.builtIn`'s, then each id they lack in the order the phone sent it), which Task 8 holds its list to. Report: `docs/superpowers/reports/2026-10-30-watchos-ruling-60.md`.
  - Under the host state of 30 October (Ruling 59), Steps 2 and 4 ran the host suites and `linkDebugTestIosSimulatorArm64` of `:shared`, `:watchcore` and `:widgetcore`. The Kotlin/Native simulator suites are DEFERRED to the catch-up gate, `WatchScreenWordsResourceTest.theDefaultReadsThePhonesOwnStrings` (the real resource read of `app_name`) and `IosWatchLinkTest.theLargestContextFitsTheSmallestLimit` among them. So are `scripts/test.sh` run whole and `scripts/ios-build.sh` at its named iPhone. `ios-build.sh` and `watch-build.sh build` and `build-for-testing` ran at generic destinations.

- [ ] **Step 1: Regenerate and write the failing tests.** First `./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors` and `swift test --package-path iosApp/WatchCore` on the branch with `watch-companion` merged in: PASS before anything changes.
  - Kotlin `WatchScreenWordsReaderTest.theAppleWordsAreThePhonesOwn`: `appName` is "Taqwa" in English, "تقوى" in Arabic, "তাকওয়া" in Bengali and "تقویٰ" in Urdu, read through `BundledScreenStrings.of(language)`; `crownHint` in English is exactly "Turn the crown or tap".
  - Kotlin `theCrownHintIsInEveryLanguage`: seven non-empty phrases, Indonesian the same under `id` and `in`.
  - Kotlin `WatchScreenWordsTest.blankFieldsTakeTheEnglish`, extended: blank `crownHint` and `appName` take the English. Plan 3's `everyLanguageHasEveryWord` and `WatchScreenWordsResourceTest.theDefaultReadsThePhonesOwnStrings` cover the two fields with no edit.
  - Swift `testEveryClockCaseMatchesTheVectors` and `testEveryWordsCaseMatchesTheVectors`; `testEveryDecodeCaseMatchesTheVectors` now reads the new `expect` fields.
  - Swift `testAnOlderFeedSpeaksEnglish`: `olderPhone`'s `words` equal `WatchScreenWords.english`, its `ringDigitZero` is nil and `latinScript` true.
  - Swift `testTheSaudiRingIsWestern`: the ar-SA sample's `ringDigitZero` is "0" and its `digitZero` "٠".
  - Swift `testATwelveHourTimeIsDrawnAndSpokenWhole`: day 0's Asr is "4:10 PM" drawn and FSI + "4:10 PM" + PDI spoken in en12; FSI + "4:10 م" + PDI spoken in ar-ly12; "16:10" both ways, with no isolate, in capetown; en12's `.SUNRISE` carries its day period ("6:01 AM").
  - Swift `testTheSamplesCarryEveryScreenWord`: on each of the four samples, every field of `ui.screens`, read *before* `orEnglish`, is non-empty, `presets` holding every built-in id. So the UI tests hear the phone's words, never the fallback. The English sample's are the ones the UI tests listen for: `rowPassed` "Passed", `of` "of %1$s", `countSpoken` "%1$s of %2$s", `appName` "Taqwa", `crownHint` "Turn the crown or tap", `facingQibla` "Facing qibla" and `distance` "Makkah · %1$s km away".
  - Swift `testEveryPhoneGoldenCarriesTheWords`: `phone-tripoli-ar-ly12.json` and `phone-capetown-en.json` have non-empty `ui.screens.crownHint`, `appName` and `round`.
- [ ] **Step 2:** Run `./gradlew :shared:testAndroidHostTest :watchcore:allTests` and `swift test --package-path iosApp/WatchCore`. Expected: FAIL.
- [ ] **Step 3: Implement the two fields, the reader's lines, the phrase, `ARABIC_SCREENS`, the writer's sections and the Swift port.** Then regenerate with `-Ptaqwa.updateVectors`, and check with `git diff --stat` that the four samples, the goldens and `feed-vectors.json` changed.
- [ ] **Step 4: Re-run the size tests:** plan 2's (`theTimesAloneLeaveRoomForThePhrases`, `theLargestFeedStaysWithinItsBudget`, `phrasesGoWhileTheyFit`, `aFeedAtTheBudgetIsWithinTheMessageLimit`, `theLargestContextFitsTheSmallestLimit`) and plan 3's `theWordsTakeAFewKilobytesAtMost`, then `scripts/test.sh` (which runs `swift test` first) and `./gradlew :shared:iosSimulatorArm64Test`. Expected: all PASS. Record the largest feed's bytes beside the budget in the ledger.
- [ ] **Step 5:** In plan 2's "What leaves the phone" table, the screens' words row (plan 3's Task 10) also names the crown hint and the app's name.
- [ ] **Step 6: Commit** with the message "Watch feed: the crown hint and the app's name join the screens' words, and the Apple Watch reads the words, the twelve-hour times and the ring's digits as the phone sends them".

### Task 4: the watchOS targets, the bundle, the version and the watch's own words

**Files:**
- Create:
  - `tools/add-watch-targets.rb`: idempotent, in `add-widget-target.rb`'s pattern. It makes the targets below and their phases, and writes a shared scheme `TaqwaWatch.xcscheme` with the UI tests in its test action;
  - `tools/watch-art.py`, with `--check`: it writes `TaqwaArch.svg` from `androidApp/src/main/res/drawable/ic_launcher_monochrome.xml` (its paths and stroke, viewBox 0 0 1024 1024);
  - `iosApp/TaqwaWatch/`: `TaqwaWatchApp.swift` (`@main`, showing `open_taqwa_on_phone` from its own table), `Info.plist`, `TaqwaWatch.entitlements` (the app group), `PrivacyInfo.xcprivacy` (no tracking, nothing collected, and the required-reason categories the code uses: SystemBootTime `35F9.1` for the heading's monotonic clock; stamps keep their time inside the file, never in its date), `Assets.xcassets/AppIcon.appiconset` (the iPhone's 1024 PNG, platform watchos), and the seven `.lproj/InfoPlist.strings` (the phone's `CFBundleDisplayName`) and `.lproj/Localizable.strings` (`open_taqwa_on_phone`, `update_taqwa_on_watch`);
  - `iosApp/TaqwaWatchWidgets/`: `TaqwaWatchWidgets.swift` (`@main WidgetBundle`, one placeholder widget drawing the empty form), `Info.plist` (`com.apple.widgetkit-extension`), `TaqwaWatchWidgets.entitlements`, `PrivacyInfo.xcprivacy`, and the seven `.lproj/Localizable.strings` (`next_prayer_widget_name` "Next prayer", `next_prayer_widget_description` "The next prayer, its time and the countdown.", `open_taqwa_on_phone`, `update_taqwa_on_watch`) and `.lproj/InfoPlist.strings` (`CFBundleDisplayName`);
  - `iosApp/WatchShared/WatchAssets.xcassets/TaqwaArch.imageset` (a template image that keeps its vector data), in both targets;
  - `iosApp/TaqwaWatchUITests/LaunchTests.swift`;
  - `scripts/watch-build.sh`: builds the `TaqwaWatch` scheme for `-destination "${WATCH_DEST:-platform=watchOS Simulator,name=Apple Watch SE 3 (40mm),OS=27.0}"` into `build/watch-dd`, Debug, with no `-sdk`, and passes its arguments to `xcodebuild` (`"${@:-build}"`, so `test` and `build-for-testing` work), as `ios-build.sh` does with `IOS_DEST`;
  - `scripts/check-watch-bundle.sh <Taqwa.app>`.
- Modify:
  - `iosApp/iosApp.xcodeproj/project.pbxproj` (by the script only);
  - the version list: the two new plists go in `scripts/version-files.sh`'s `IOS_PLISTS`, which plan 5's Task 1 makes before this task (the execution map's order), so `bump-version.sh` writes them and `check-versions.sh` checks them. Only if plan 5's Task 1 has not landed do they go in `scripts/bump-version.sh`'s loop and its report, for plan 5's Task 1 to move;
  - `scripts/ios-build.sh`: drop `-sdk iphonesimulator` from line 8 and keep `-destination` (Global Constraints). Then run the bundle check on `build/ios-dd/Build/Products/Debug-iphonesimulator/Taqwa.app`, and add the watch widget's guard at 5 MB beside `TaqwaWidget.appex`'s;
  - `tools/i18n-check.py` (the watch `.lproj` tables);
  - `.github/workflows/ios.yml`:
    - both `paths` filters gain `scripts/watch-build.sh`, `scripts/check-watch-bundle.sh`, `tools/add-watch-targets.rb` and `tools/watch-art.py`;
    - a step "watchOS platform", before the build, runs `xcrun simctl list runtimes | grep -q watchOS || xcodebuild -downloadPlatform watchOS`, then prints `xcrun simctl list runtimes`. Since Xcode 15 a simulator build can need its platform installed, and the iPhone build now compiles the watch app;
    - the build step also checks `test -d build/ios-dd/Build/Products/Debug-iphonesimulator/Taqwa.app/Watch/TaqwaWatch.app`;
    - a new step, "The watch app, its widgets and its UI tests compile", runs `WATCH_DEST="generic/platform=watchOS Simulator" ./scripts/watch-build.sh build-for-testing CODE_SIGNING_ALLOWED=NO`. A generic destination names no watch device or OS, so the image's watchOS 26 serves. The header comment says the watch's UI tests run on the Mac only, and why.

**Interfaces:**
- **The targets:**
  - `TaqwaWatch` (watchOS application) and `TaqwaWatchWidgets` (app extension, embedded in the watch app's PlugIns) compile every file in `iosApp/WatchCore/Sources/WatchCore/` and `iosApp/WatchShared/`.
  - `iosApp` depends on `TaqwaWatch` and embeds it in `$(CONTENTS_FOLDER_PATH)/Watch` ("Embed Watch Content").
  - Both watch targets bundle the three Manrope files, with `UIAppFonts` naming them.
- **The phases the script also writes, used by later tasks:**
  - "Debug samples" on `TaqwaWatch`: it copies `watchcore/vectors/sample-feed-capetown*.json` into the bundle when `CONFIGURATION` is Debug, and nothing otherwise;
  - "Watch face" on `iosApp`, a shell-script phase: it copies `iosApp/iosApp/WatchFace/Taqwa.watchface` into the bundle when the file exists (Task 10).
- **Where `iosApp`'s two new phases go.** `tools/add-widget-target.rb` ends with `app.build_phases.move(embed, app.build_phases.count - 1) unless app.build_phases.last == embed`, so "Embed Foundation Extensions" must stay `iosApp`'s last phase. `new_copy_files_build_phase` and `new_shell_script_build_phase` append, so `add-watch-targets.rb`, when it creates "Embed Watch Content" or "Watch face", moves the new phase to the index of "Embed Foundation Extensions", just before it (`ObjectList#move`, as the widget script uses). It never moves a phase it did not create, and never moves its own once they exist. If "Embed Foundation Extensions" is missing, it stops with a message to run `add-widget-target.rb` first. Each script's rerun then leaves the other's work alone.
- **`check-watch-bundle.sh` fails, naming the rule, unless:**
  1. the two bundles exist where the targets embed them, with the ids and `WK*` keys of the Global Constraints;
  2. all four bundles share one `CFBundleShortVersionString` and one `CFBundleVersion`;
  3. the watch bundles hold no `NSLocation*`, `NSHealth*`, `NSMotionUsageDescription`, `NSAppTransportSecurity` or `UIBackgroundModes` key;
  4. their entitlements are the app group alone:
     - (a) always: `iosApp/TaqwaWatch/TaqwaWatch.entitlements` and `iosApp/TaqwaWatchWidgets/TaqwaWatchWidgets.entitlements` (`plutil -convert json -o - …`) each hold one key, `com.apple.security.application-groups`, whose one value is the group in the iPhone's `iosApp/iosApp/iosApp.entitlements` (`group.world.taqwa.ios`), read from that file rather than written in the script;
     - (b) only on a real signature, when `codesign -dvv` names an `Authority`: `codesign -d --entitlements - --xml` holds that group and no other capability key. It is `-dvv` because codesign prints the `Authority` lines only from verbosity 2, and the script reads that output whole before it searches it: under `pipefail`, `codesign -dvv … | grep -q` reads as false, since grep stops at the first `Authority` line and codesign, still writing, dies of SIGPIPE (fix round 1 of this task). An unsigned build (CI's `CODE_SIGNING_ALLOWED=NO`) or an ad hoc simulator build prints "rule 4b skipped: unsigned or ad hoc". A simulator build keeps its entitlements in the binary's simulated-entitlements section, not in its signature, so the signature proves nothing there. The signed check is plan 5's archive report;
  5. no location request, no network, no Kotlin. The scan covers every Mach-O file at each bundle's top level and in its `Frameworks/`: the main executable, `<Name>.debug.dylib` and `__preview.dylib`. A Debug build with `ENABLE_PREVIEWS` (YES across the project, `project.pbxproj:474, 513, 669, 703`) puts the code in `<Name>.debug.dylib` and leaves the executable a stub, so the executable alone proves nothing:
     - their `strings` name none of `requestWhenInUseAuthorization`, `requestAlwaysAuthorization`, `startUpdatingLocation`, `requestLocation`, `dataTaskWithURL` and `NSURLSession`;
     - `otool -L` on each links none of `shared`, `widgetcore` and `watchcore`;
     - **the canary:** across the scanned files, `strings` must find `open_taqwa_on_phone`, a key both watch targets' own code names. A scan that missed the code fails here, naming the files it read;
  6. each holds `PrivacyInfo.xcprivacy` and the seven `.lproj` folders;
  7. the widget extension is at most 5 MB and the watch app at most 15 MB;
  8. a Release-configuration bundle holds no `sample-feed` file (checked when the script is given one).

- [ ] **Step 1: The tool.** After the Lead's call 2, install the gem and run `ruby -e 'require "xcodeproj"; puts Xcodeproj::VERSION'`. Write the version in the script's header.
- [ ] **Step 2: Drop `-sdk` first, on today's project.** Remove `-sdk iphonesimulator` from `scripts/ios-build.sh` and run it. Expected: PASS as before. `build/ios-dd/Build/Products/Debug-iphonesimulator/Taqwa.app/PlugIns/TaqwaWidget.appex` exists, and the size line prints. So the change is proven harmless before any watch target depends on it.
- [ ] **Step 3: Write `check-watch-bundle.sh` and `LaunchTests.testTheAppOpensOnTheFirstRunLine`, and wire the check into `ios-build.sh`.** The UI test launches with no feed and finds the element "Open Taqwa on your phone". Run `scripts/ios-build.sh`. Expected: FAIL, rule 1. Not committed.
- [ ] **Step 4: Write the script, the art tool and the files,** then run `ruby tools/add-watch-targets.rb` and `python3 tools/watch-art.py`.
- [ ] **Step 5: Check the project file.**
  - `iosApp`'s phases, printed with `ruby -e 'require "xcodeproj"; p Xcodeproj::Project.open("iosApp/iosApp.xcodeproj").targets.find { |t| t.name == "iosApp" }.build_phases.map(&:display_name)'`, end with "Embed Watch Content", "Watch face", "Embed Foundation Extensions".
  - `ruby tools/add-watch-targets.rb` run again leaves the project byte-identical (`shasum` before and after).
  - So do `ruby tools/add-widget-target.rb` and `ruby tools/configure-store-targets.rb`, run in that order and then in the reverse order. The phase list prints the same after each run.
  - `git diff --stat` shows only the expected sections grown, and `xcodebuild -list -project iosApp/iosApp.xcodeproj` lists the three new targets and the `TaqwaWatch` scheme.
- [ ] **Step 6: Build and check.** Run `scripts/ios-build.sh`, `scripts/watch-build.sh test`, `scripts/bump-version.sh <the current name> <the current code>` (`git diff` empty but for the new plists, which equal the phone's), `python3 tools/i18n-check.py`, `python3 tools/watch-art.py --check` and `swift test --package-path iosApp/WatchCore`. Expected: all pass, the bundle check included. Its output names the `.debug.dylib` files rule 5 read, and the canary is found. Rule 4b prints "skipped". `Taqwa.app/Watch/TaqwaWatch.app/PlugIns/TaqwaWatchWidgets.appex` exists under `Debug-iphonesimulator/`.
- [ ] **Step 7: Prove that rule 5 bites, then undo.** Add a throwaway `_ = CLLocationManager().requestLocation()` to `TaqwaWatchApp.swift` and run `scripts/ios-build.sh`. Expected: FAIL, rule 5, naming `requestLocation` and `TaqwaWatch.debug.dylib`. Revert the line (`git diff` on the file is empty), and run again: PASS.
- [ ] **Step 8: The CI build, as CI runs it.** Run `./scripts/ios-build.sh build CODE_SIGNING_ALLOWED=NO`, then `WATCH_DEST="generic/platform=watchOS Simulator" ./scripts/watch-build.sh build-for-testing CODE_SIGNING_ALLOWED=NO`. Expected: both pass, and rule 4b prints "skipped". CI's own Xcode 26 run is the Lead's to watch after the push.
- [ ] **Step 9: Commit** with the message "Apple Watch: the watch app and its widgets inside the iPhone build, with one version, no location, no network, and the watch's own words in seven languages".

### Task 5: receiving the feed, keeping it, and the handshake

The watch's half alone: no phone file changes here, so it does not wait for plan 2's Task 8. Task 5B takes it end to end.

**Files:**
- Create:
  - `iosApp/WatchCore/Sources/WatchCore/FeedStore.swift`, `FeedIntake.swift`, `HandshakePolicy.swift`, `ConnectivityDrain.swift`, with `FeedStoreTests.swift`, `FeedIntakeTests.swift`, `HandshakePolicyTests.swift` and `ConnectivityDrainTests.swift`;
  - `iosApp/TaqwaWatch/WatchSession.swift`, `AppModel.swift`, `AppClock.swift`;
  - `iosApp/TaqwaWatch/DebugLaunch.swift` (`#if DEBUG`).
- Modify: `TaqwaWatchApp.swift`, `tools/add-watch-targets.rb` (the new files), re-run.

**Interfaces:**
- Consumes: `FeedDecoder`, `WatchRules.needsRefresh`, and the `contract` vectors.
- Produces `enum FeedWriteResult { case accepted, unchanged, stale, newerVersion, malformed }`, and `final class FeedStore` (Ruling 50: the receivers' rules are the contract's vectors, ported, never invented):
  - `init(directory: URL)`, and `static func appGroup() -> FeedStore` on `<group container>/Library/Application Support/TaqwaWatch`;
  - `func read() -> WatchFeed?`, `func delivery() -> Delivery?` (the stored `feed.delivery`, read as `deliveryOf` reads it, nil when missing or refused) and `var newerVersionSeen: Bool`;
  - `func write(_ feed: Data, delivery: Delivery?) -> FeedWriteResult`:
    - with a delivery that `stores` refuses (the same or a lower number of the origin held), `.stale`, with nothing touched and nothing reloaded;
    - malformed keeps the old feed and its delivery;
    - newer keeps them and persists a `feed.newer` notice that outlives the process (Ruling 13's twin);
    - otherwise, in `STORE_STEPS`'s order, the delivery file is deleted, the feed written atomically, the delivery written (atomically, and none when that write fails), and the notice cleared. That is `.unchanged` when the feed's fingerprint equals the one it replaced (the newer delivery written, no widget reload), and `.accepted` otherwise;
    - without a delivery (the DEBUG samples), the feed is stored whatever is held and the delivery file deleted first, so a sample never carries a phone's delivery;
  - the directory is marked `isExcludedFromBackup` when it is made.
- Produces `enum FeedIntake`:
  - `static let feedKey`, `deliveryKey`, `refreshKey` and `replyKey`, equal to `WatchLinkContract`'s;
  - `static func deliveryOf(_ text: String) -> Delivery?`, `static func stores(held: Delivery?, arriving: Delivery) -> Bool` and `static func echoOf(_ held: Delivery?) -> String`, the ports of the contract's three, against its vectors;
  - `static func accept(_ context: [String: Any], into store: FeedStore) -> FeedWriteResult?`: `Data`, or a UTF-8 `String`, under `feedKey`, with the `String` under `deliveryKey`; nil when there is no feed, and nil too for a feed without a delivery (no phone's);
  - `static func handshake(_ store: FeedStore) -> [String: Any]`: `[refreshKey: echoOf(store.delivery())]`.
- Produces `enum AskWhy { case activated, reachable, opened }` and `enum HandshakePolicy { static let gap: Int64 = 60; static func shouldAsk(_ why: AskWhy, feed: WatchFeed?, now: Int64, lastAskedAt: Int64?) -> Bool }`:
  - never twice within `gap`;
  - `opened` only when the feed is nil or `needsRefresh`.
  - The stamp `refresh.asked` lives in the store's directory.
- Produces `enum ConnectivityDrain { static let limit: Double = 10; static func isDrained(activated: Bool, contentPending: Bool, intakesInFlight: Int) -> Bool }`: true only when the session is activated, `hasContentPending` is false and no intake is in flight. Apple's `WKWatchConnectivityRefreshBackgroundTask` asks the app to keep the task open until the session has activated and every pending item has been received; before activation `hasContentPending` can already read false.
- Produces in the app:
  - `final class WatchSession: NSObject, WCSessionDelegate`, with `static let shared`, `start()` and `func drain() async`.
    - On activation it accepts `receivedApplicationContext`; it accepts each `didReceiveApplicationContext`.
    - It shakes hands (`sendMessage(handshake, replyHandler:)` when `isReachable` and the policy says so) at activation, when the session becomes reachable, and when `AppModel` opens. At activation it first awaits `ConnectivityDrain.isDrained` (as `drain()` does, within `ConnectivityDrain.limit`), because Apple delivers a context already waiting "shortly after you activate": a handshake sent before it would echo the older delivery and cost the iPhone one redundant send at every start with a context waiting (Ruling 50's iPhone design check).
    - An intake is in flight from the start of `accept` until its store write and, on `.accepted`, `WidgetCenter.shared.reloadAllTimelines()` have returned (`.unchanged` and `.stale` reload nothing). Each `.accepted` also publishes on the main thread.
    - `drain()` returns once `ConnectivityDrain.isDrained(activationState == .activated, hasContentPending, intakesInFlight)` holds, checked after each delegate callback and every 0.1 s, or after `ConnectivityDrain.limit` seconds. It logs `TaqwaWatch drain <drained|limit> <ms>`.
    - Every decision is `NSLog("%@", "TaqwaWatch …")`, never with values interpolated into the format string, with the result, the delivery's number and fingerprint (`<seq>/<fingerprint>`, `none` for no delivery) and the day count. Never the delivery's origin, the city or the bearing (Ruling 52 item 4: release log lines on every platform). Only the DEBUG `WatchHarness` may log an origin.
  - `@MainActor final class AppModel: ObservableObject` with `@Published feed: WatchFeed?`, `@Published newerVersionSeen: Bool`, and `clock: AppClock`.
  - `protocol AppClock { func now() -> Int64 }`, with the system's and, in DEBUG, a scripted one.
  - `TaqwaWatchApp` calls `WatchSession.shared.start()` in `init`, and its `.backgroundTask(.watchConnectivity)` awaits `WatchSession.shared.drain()`, so the task completes only after the feed is stored and the timelines are reloaded.
- Produces the DEBUG launch arguments every later task uses:
  - `-taqwaSampleFeed capetown|en12|ar-ly12|ar-sa` stores that sample, re-dated by whole days so its 18 October is today, each day's times and midnight shifting together, as the Wear sample is;
  - `-taqwaAt HH:MM` sets the scripted clock to the re-dated 18 October's midnight plus that time, ticking;
  - `-taqwaFrozen 1` stops it;
  - `-taqwaPage today|tasbeeh|qibla|gallery`;
  - `-taqwaNoFeed 1` empties the store at launch, for the first-run checks (Task 4's UI test uses it from this task on);
  - `-taqwaNewerSeen 1` persists the newer-version notice, as a version-2 feed would;
  - `-taqwaPhrase <text>` appends `WatchPhrase(id: "debug-phrase", phrase: <text>, target: 33)` to the stored sample's `tasbeeh`, for Task 8's list shot.
- **As built (30 October, Lane A):**
  - `FeedStore.write` and `FeedIntake.accept` are `throws`: a file system error removing the old delivery or putting the feed in place is thrown, with the old feed kept and, past the removal, no delivery (the Wear store's `IOException`). A failed delivery write is still swallowed, as above. `WatchSession` logs a throw as `TaqwaWatch store failed <seq>/<fp> via <how>`.
  - `.unchanged` also needs a readable feed held and no newer-version notice to clear: the same fingerprint after a notice is `.accepted`, because the notice's going changes what the surfaces draw.
  - The notice file names the version it was for (the Wear store's rule), through a new `FeedDecoder.versionOf`, so a build that reads that version says nothing more.
  - `Delivery` has a failable `init?(origin:seq:fingerprint:)` holding `WatchDelivery`'s three requirements; `logged` is `<seq>/<fingerprint>`. `FeedIntake` also ports `isFingerprint` and `isOrigin` (the `fingerprints` vector), and gives the log lines (`line`, `echoLine`), which never name an origin.
  - `FeedIntake.handshake` echoes "" while no feed is held, even with a delivery file left, as the Wear app's `PhoneAsk.payload` does.
  - The stamp is `FeedStore.lastAskedAt()`, `stampAsked(_:)` and `unstampAsked(ifAt:)`; a handshake whose `sendMessage` fails takes its stamp back, so the next chance asks again. `FeedStore.clear()` empties the feed, delivery and notice for `-taqwaNoFeed 1`. `FeedStore.appGroupIdentifier` is held equal to the three entitlements files by a test.
  - The launch arguments' rules are in `WatchCore/DebugSample.swift` (`DebugArguments`, `DebugPage`, `DebugSample`, all `#if DEBUG`) with `DebugSampleTests`; the app's `DebugLaunch.swift` applies them. The day is Cape Town's, UTC+2 all year, as the Wear sample's.
  - Erratum p4 #8: every argument that sets the store (`-taqwaSampleFeed`, `-taqwaNoFeed`, `-taqwaNewerSeen`; Task 6 adds `-taqwaStartIn`) turns off intake and handshakes for the run, and logs `TaqwaWatch intake off (debug sample)`. Erratum p4 #9: those arguments set the feed and the notice wholly (`-taqwaNoFeed 1` clears both, a sample stored clears the notice, `-taqwaNewerSeen 1` sets it after either); `-taqwaFresh` for the tasbeeh store and its selection is Task 8's, when they exist.
  - Until Task 7, the app's one screen shows the update line once a newer format is seen, the first-run line with no feed, and otherwise the feed's city. `LaunchTests` holds the three by launch arguments.
  - `tools/add-watch-targets.rb` needed no edit: it finds files on disk, and its re-run added the new files to their targets.
  - Review round 1: `drain()` also returns as soon as its task is cancelled, since watchOS cancels a `backgroundTask(_:action:)` that runs out of time (Apple's reference for it), and logs `TaqwaWatch drain <drained|cancelled|limit> <ms> ms`. The rule is `ConnectivityDrain.end(drained:cancelled:elapsed:)` (drained, then cancelled, then the limit), the loop `ConnectivityDrain.wait(until:waiter:)`, and its pauses `DrainWaiter`, which a cancellation handler wakes at once. All three are in `ConnectivityDrain.swift` and held by `ConnectivityDrainTests` on real cancelled tasks.
  - Review round 2: an intake also reloads the widgets on a `.newerVersion` whose notice has just come, since Task 6's empty form (no feed, or a spent one) is a timeline with no `reloadAt` that would otherwise keep "Open Taqwa on your phone" (Review Focus 1, "with a feed or without"). The rule is `FeedIntake.reloadsWidgets(_:noticeBefore:noticeAfter:)`: `.accepted` always, `.newerVersion` only when the notice is on disk after the intake and was not before (`FeedStore.noticeOnDisk`, what the widgets' process reads of `newerVersionSeen`), anything else never. So a newer feed the phone sends again at each handshake (its delivery is not stored, so the echo still names the older one), a newer format of another version, and a notice whose file could not be written reload nothing, which keeps WidgetKit's budget. `FeedIntake.take(_:into:)` is `accept` with the notice read before and after it, giving `FeedIntake.Outcome(result:reloadsWidgets:)`, and `WatchSession` reloads on that. Held by `FeedIntakeTests`' `testOnlyANewFeedOrANewNoticeReloadsTheWidgets`, `testANewerFormatReloadsTheWidgetsOnlyWhenItsNoticeComes` and `testANoticeThatCannotBeWrittenReloadsNothing`.

- [ ] **Step 1: Write the failing package tests.**
  - `testWriteThenReadReturnsTheFeedAndItsDelivery`
  - `testAMalformedFeedLeavesTheOldOneInPlace`
  - `testANewerVersionKeepsTheOldFeedAndRemembersTheNotice`
  - `testTheNoticeOutlivesTheStore`: a new `FeedStore` on the directory still reports it
  - `testTheSameFeedUnderANewerNumberIsUnchangedAndKeepsTheNewerDelivery`
  - `testAStaleOrDuplicateDeliveryIsDropped` (the same number, a lower one: `.stale`, the feed and its delivery untouched)
  - `testAnotherOriginsFeedIsStored`
  - `testTheStoreFollowsTheContractsSteps`: a failed delivery write leaves none, never the old one beside the new feed
  - `testTheReceiversRulesAreTheContractsVectors`: `deliveryOf`, `stores`, `echoOf` and `storeSteps` against the `contract` vectors (Task 1)
  - `testAContextWithoutADeliveryIsNotStored`
  - `testALeftoverTempFileIsIgnored`
  - `testTheDirectoryIsExcludedFromBackup`
  - `testDataUnderTheFeedKeyIsStored`
  - `testAContextWithoutAFeedIsNothing`
  - `testTheHandshakeEchoesTheStoredDelivery`: empty with none
  - `testActivationAsksAtMostOnceAMinute`
  - `testOpeningAsksOnlyWhenTheFeedNeedsIt`
  - `testNoFeedAlwaysNeedsOne`
  - `testTheContractKeysAreThePhonesOwn`
  - `ConnectivityDrainTests`: `testNotActivatedIsNotDrained` (not activated, nothing pending, nothing in flight: false); `testContentPendingIsNotDrained`; `testAnIntakeInFlightIsNotDrained`; `testActivatedWithNothingLeftIsDrained`.
- [ ] **Step 2:** Run `swift test --package-path iosApp/WatchCore`. Expected: FAIL. Not committed.
- [ ] **Step 3: Implement the package types, the session, the model, the clock and the debug launch.**
- [ ] **Step 4:** Run `swift test`, `scripts/ios-build.sh` and `scripts/watch-build.sh test`. Expected: PASS.
- [ ] **Step 5: Commit** with the message "Apple Watch: the feed received only when it moves the watch forward, kept out of backup and echoed by its delivery, with the background task held open until it is stored".

### Task 5B: the link end to end on the simulators: the phone's harness, the backup check and the limits

**Starts after Task 5 and plan 2's Task 8**, with `watch-companion` merged into `watch-apple`. Never beside Task 10 or plan 2's Task 8 Step 5 (the series above).

**Files:**
- Create: `iosApp/iosApp/WatchHarness.swift` (`#if DEBUG`), `docs/superpowers/reports/2026-10-24-apple-watch-link.md`.
- Modify:
  - `iosApp/iosApp/iOSApp.swift`, in DEBUG only, so a release build is unchanged:
    - route `taqwa://watch/…` to `WatchHarness.shared.handle(_:)`, beside `RecitationHarness`;
    - plan 2's `TaqwaWatchSession` gains `session(_:didFinish:error:)`, which calls `WatchHarness.shared.transferFinished(_:error:)`. That delegate is the phone's only `WCSession` delegate, so `transferUserInfo`'s answer can only be heard there;
  - `iosApp/TaqwaWatch/WatchSession.swift`: the DEBUG `session(_:didReceiveUserInfo:)`;
  - `tools/add-watch-targets.rb` (`WatchHarness.swift` into `iosApp`), re-run.

**Interfaces:**
- Consumes plan 2's Task 8 (`TaqwaWatchSession`, `WatchFeedBridge.shared.watchStateChanged()`) and Task 5's `WatchSession`.
- Produces in the watch app, DEBUG only: `session(_:didReceiveUserInfo:)` logs `TaqwaWatch userInfo <bytes>` (the size of its `probe` value) and drops it. Release builds do not implement it.
- Produces the phone's DEBUG `final class WatchHarness`, with `static let shared`, `handle(_ url: URL)` and `transferFinished(_ transfer: WCSessionUserInfoTransfer, error: Error?)`, through `xcrun simctl openurl <phone> "taqwa://watch/probe?via=context|userInfo&from=60000&to=300000&step=4096"`:
  - It bisects between `from` and `to` until the gap is at most `step`, sending `["probe": Data(count: n)]` on `WCSession.default`.
  - **`via=context`:** `updateApplicationContext` throws `WCError.payloadTooLarge` at once, so each size is answered in the call.
  - **`via=userInfo`:** `transferUserInfo` never throws. It returns a `WCSessionUserInfoTransfer`, and the answer comes later in `transferFinished`. So the harness sends one size, waits for its answer, and only then sends the next. `payloadTooLarge` in the error is a refusal, and a nil error is an acceptance. With no answer in 30 s it logs `TaqwaWatch probe userInfo no-answer <n>` and stops. At the end it cancels any of `outstandingUserInfoTransfers` whose `userInfo` holds `probe`. Bisecting keeps the transfers to about six, each delivered to the watch and dropped there.
  - It logs `TaqwaWatch probe <via> <largest accepted> <first refused>`.
  - **Then it puts the real context back** with `WatchFeedBridge.shared.watchStateChanged()` (WATCH_CHANGED), which forgets the phone's memory and sends the feed again, and logs `TaqwaWatch probe restore`. An ordinary ask would answer `UNCHANGED`, because the iPhone remembers `SENT`, and the probe's context would stay in place.
  - The watch ignores every context key but the feed's: a probe context accepts nothing and keeps the stored feed.
- **As built (31 October, Lane A; report `docs/superpowers/reports/2026-10-24-apple-watch-link.md`):**
  - The probe's rules are their own file, `iosApp/iosApp/WatchProbe.swift` (`#if DEBUG`, pure): `WatchProbeRequest` (the URL), `WatchProbeSearch` (`from`, then `to`, then halves until the gap is at most `step`; every size sent once) and `WatchProbeFill`. `swift test` holds them by a linked test-support target, `PhoneHarness` with `PhoneHarnessTests` (14), as `WatchViews` links the watch's views. `tools/add-watch-targets.rb` adds `WatchHarness.swift` and `WatchProbe.swift` to `iosApp`.
  - `fill=random` was added: WatchConnectivity checks a payload after compressing it (the 21,034-byte feed checks as 6,507 bytes, 300,000 zeros as 1,200), so `Data(count: n)` cannot meet the limit. The thresholds are measured with bytes no compressor shrinks. `transferUserInfo` takes 65,536 serialized bytes and refuses 65,543 (65,521 and 65,528 of payload), so its limit is 64 KiB, `USER_INFO_LIMIT_BYTES`, to within 7 bytes. `updateApplicationContext` takes about 256 KiB: 258,750 accepted and 262,500 refused, checked as 258,765 and 262,515, either side of `CONTEXT_LIMIT_BYTES` (262,144). Both are above `FEED_BUDGET_BYTES + ENVELOPE_BYTES` (61,024).
  - **`random` is the default fill since review round 1**, so the plan's URLs above, which name no fill, measure the limit. Zeros are sent only by name (`fill=zero`), for the record of what they compress to.
  - `handle(_:)` returns a discardable `Bool`, as `RecitationHarness.handle` does. The final line reads `TaqwaWatch probe <via> <fill> <largest accepted> <first refused>[ incomplete][ unmeasured]`. `incomplete` is a search that stopped before its end, and `unmeasured` ends every zero run's line. So only a `random` line with neither word is a threshold, and a zero run's "300000 none" never reads as one.
  - On these simulators a `transferUserInfo` is answered on the phone but never handed to the watch app (the watch's `wcd` receives it and goes no further), so no `TaqwaWatch userInfo` line appears. userInfo's delivery is plan 5's.
  - **`simctl unpair` erases a simulated watch**: it shut the Series 12 down and re-made its data folder. An `ios-pair` task that pairs and unpairs leaves a re-made watch, booted again when it was found booted.
  - The phone's location in its found state is set by hand (MANUAL), so London and Cape Town were written into the stopped app's settings; `simctl location` moves only the simulator's own.

- [ ] **Step 1: Implement the harness, the delegate method and the watch's DEBUG userInfo method.** They are DEBUG plumbing whose evidence is Step 3's logs.
- [ ] **Step 2:** Run `scripts/ios-build.sh` and `scripts/watch-build.sh test`. Expected: PASS, the bundle check included.
- [ ] **Step 3: End to end on the simulators,** for the report. Let `PHONE=2D01CE58-7F12-45FC-8DB0-D63330AE9BB1`, `WATCH=C803D94B-7239-435B-9CEB-2A6AD75F141F`, and `<BUNDLE_ID>` from `Config.xcconfig`. Read each log with `xcrun simctl spawn <udid> log show --last 10m --predicate 'eventMessage CONTAINS "TaqwaWatch"'`.
  - **The first feed.** Pair the two unless `xcrun simctl list pairs` shows the pair, and boot both: plan 2 left the Series 12 with no Taqwa app (Global Constraints). Install `build/ios-dd/…/Taqwa.app` on the phone and its `Watch/TaqwaWatch.app` on the watch. Launch the watch app (`xcrun simctl launch $WATCH <BUNDLE_ID>.watchkitapp`), then the phone app (`xcrun simctl launch $PHONE <BUNDLE_ID>`). Give the phone London as plan 2's Task 8 Step 5 did (written into the app's settings file with the app stopped, then launched again), or with `xcrun simctl location $PHONE set 51.5074,-0.1278` and the onboarding when the simulator tool may tap. Expected:
    - the phone reports `installed=true` and a send with its byte count and delivery `<n>/<fp>` (its number and fingerprint: no release line logs the origin, Ruling 52 item 4);
    - the watch reports `accepted <fp> 31 days`;
    - the watch's handshake is answered `UNCHANGED`, with no second send.
  - **The backup check on the iPhone's copy** (plan 2, Task 8's "For plan 4", Ruling 39), right after that first `accepted`:
    - Let `DATA=~/Library/Developer/CoreSimulator/Devices/$PHONE/data` and `APP=$(xcrun simctl get_app_container $PHONE <BUNDLE_ID> data)`.
    - Find every file holding the context: `grep -rlF --binary-files=text "<fp>" "$DATA/Library/WatchConnectivity" "$APP"`, and over all of `$DATA` only if those two find nothing. The fingerprint is ASCII, so it shows in a binary property list as it is.
    - For each file, and each of its directories up to `$DATA`, read `NSURLIsExcludedFromBackupKey`. Use a three-line Swift script kept in `build/` (untracked), run as `xcrun swift build/backup-key.swift <paths>`, printing each path's `isExcludedFromBackup`. Then `xattr -l` on the same paths shows the attribute behind it (`com.apple.metadata:com_apple_backup_excludeItem`).
    - **The verdict is always "the Backups sentence stays"** (Ruling 39). The report gives each path found, whether it is inside `$APP`, and the key's value on it and its directories, as evidence for plan 5. No copy found, a copy outside `$APP`, a copy not excluded, and an excluded copy all leave the sentence in place: the simulator's WatchConnectivity is not a device's (Apple asks for paired devices to test it), and only device evidence, which no agent can read, could drop it.
  - **A city change with the watch app closed** (the Lead's call 4's observation):
    - Terminate the watch app (`xcrun simctl terminate $WATCH <BUNDLE_ID>.watchkitapp`).
    - Set the location to Cape Town (`xcrun simctl location $PHONE set -33.9249,18.4241`). The iPhone reads its location only when its own triggers fire (`LocationProvider.ios.kt:156`, `requestLocation`), so a location change alone sends nothing.
    - Terminate and relaunch the phone app (`xcrun simctl terminate $PHONE <BUNDLE_ID>`, then `xcrun simctl launch $PHONE <BUNDLE_ID>`). Expected within 2 minutes: a phone send whose fingerprint `<fp2>` differs from `<fp>`, under a greater number. If none comes, stop: that is the phone's trigger, not the link, and the Lead is told.
    - From that send's time, watch the watch's log for up to 30 minutes, without opening the app. Record whether, and after how long, it logs `accepted <fp2>`, and whether a `drain` line follows it. A "not within 30 minutes" is recorded as the simulator's answer only: the Lead's call 4 is decided on her watch in plan 5, and nothing changes in plan 2's sender on the simulator's word.
  - **A handshake that cold-wakes the iPhone app** (Ruling 53 item 4b): terminate the phone app (`xcrun simctl terminate $PHONE <BUNDLE_ID>`), then open the watch app (`xcrun simctl launch $WATCH <BUNDLE_ID>.watchkitapp`) so that it shakes hands. Expected in the phone's log, with the app not opened by hand: `TaqwaWatch handshake have=<n>/<fp>` and then `TaqwaWatch handshake answered queued=<Bool>`, within `WatchFeedSync.CALLER_BOUND` of it (the bridge holds UIKit background time until it replies); in the watch's log, the reply. Record whether WatchConnectivity woke the app, and after how long it answered. If the simulator does not wake it, record that as the simulator's answer: the device check is plan 5's.
  - **Then the probe,** with the watch app open in the foreground (`xcrun simctl launch $WATCH <BUNDLE_ID>.watchkitapp`), so that each accepted transfer is delivered and answered:
    - Run `via=context`, then `via=userInfo`.
    - Expected in the phone's log: each `probe <via> <largest accepted> <first refused>`, then `probe restore` and a send of the real feed. Expected in the watch's log: `unchanged <n>/<fp2>` after each restore, under a newer number with the same fingerprint as before the probe, so the real context is back and nothing was reloaded. For `userInfo`, the watch's `userInfo <bytes>` lines agree with the phone's accepted sizes.
    - Record both thresholds against `FEED_BUDGET_BYTES + ENVELOPE_BYTES` (plan 2's ship gate, here on simulators; plan 5's on the device). A `no-answer` line means the userInfo threshold is not measured: record that, and the gate stays open for plan 5. If a threshold is below the budget, stop and tell the Lead.
- [ ] **Step 4: Commit** with the message "Apple Watch: the link end to end on the simulators, the iPhone's copy looked at for backup, and the link's limits measured". The report states the evidence, the verdict ("the Backups sentence stays"), the city-change observation and both thresholds. Write the verdict and the observation in the ledger too.

### Task 6: the complications and the Smart Stack

**Files:**
- Create:
  - `iosApp/WatchCore/Sources/WatchCore/Glance.swift`, `iosApp/WatchCore/Tests/WatchCoreTests/GlanceTests.swift`;
  - `iosApp/WatchShared/Style.swift` (the colours, `TaqwaFont`, `direction(_ feed:)`);
  - `iosApp/WatchShared/NextPrayerViews.swift` (the four families and the Smart Stack, in both targets);
  - `iosApp/TaqwaWatchWidgets/NextPrayerWidget.swift`;
  - `iosApp/TaqwaWatch/ComplicationGallery.swift` (`#if DEBUG`);
  - `docs/superpowers/reports/2026-10-24-apple-watch-pass/` (the evidence).
- Modify: `TaqwaWatchWidgets.swift` (the placeholder goes), `iosApp/TaqwaWatch/DebugLaunch.swift` (`-taqwaStartIn`), `tools/add-watch-targets.rb`, re-run.

**Interfaces:**
- Consumes: `WatchRules` (with Task 3's `spokenClock`), `feed.words`, `FeedStore.appGroup()`.
- Produces `struct Glance: Equatable { prayer: WatchPrayer; label: String; name: String; time: String; countdown: String; spoken: String; spokenWithoutCountdown: String; progress: Float; previousAt: Int64; nextAt: Int64; rtl: Bool; latinScript: Bool }`:
  - `label` is `WatchRules.label` of the next start's day; `name` is that day's; `time` is that day's bare `clock`, as the Galaxy's complications draw it;
  - `spoken` is `spokenCountdown(feed, name, spokenClock(day, .of(prayer)), secondsLeft)`, or, without spoken words, `"\(name) \(spokenClock), \(countdown)"` (plan 1's fallback form);
  - `spokenWithoutCountdown` is `"\(name), \(spokenClock)"`, the row's form (plan 3's `prayerRowDescription`);
  - `latinScript` is `feed.ui.latinScript`.
- Produces `struct GlanceEntry: Equatable { at: Int64; glance: Glance?; relevant: Bool; countdownShown: Bool }`, `struct GlanceTimeline: Equatable { entries: [GlanceEntry]; reloadAt: Int64?; words: WatchScreenWords?; update: Bool }`, and `enum Glances`:
  - `static func make(_ feed: WatchFeed, now: Int64) -> Glance?`;
  - `static let relevantBefore: Int64 = 1_800` (question 2), and `static let horizon: Int64 = 3_600`;
  - `static func timeline(_ feed: WatchFeed?, newerVersionSeen: Bool, now: Int64) -> GlanceTimeline`: the Lead's call 5.
    - The first entry is `make(now)`, at `now`.
    - Then an entry at each start in `(now, now + horizon)`, and at every instant `n − 60 × k` (k ≥ 1) in that range that falls after the start before it, where `n` is the start that ends that instant's period. Each of these is `make(at + 1)`, the glance that holds for that minute, so an entry at a start already counts to the start after it.
    - The last entry is at `now + horizon`, `make` of it with `countdownShown` false: the label and the time alone. It is nil past the feed.
    - Every other entry has `countdownShown` true. There are at most 62 entries, while every start is on a whole minute (Ruling 64 item 3).
    - `relevant` holds when `at` is in `[nextAt − relevantBefore, nextAt)`. `reloadAt` is the earlier of the first `nextAt` and `now + horizon`.
    - `words` is the feed's `words` (nil with no feed), and `update` is `newerVersionSeen`, for the empty form (plan 3's call 5).
    - No feed, or no moment, gives one empty entry and a nil `reloadAt`.
  - `static func relevanceWindows(_ feed: WatchFeed, now: Int64, count: Int = 10) -> [ClosedRange<Int64>]`.
- Produces the widget `TaqwaNextPrayer`:
  - a `StaticConfiguration` over `NextPrayerProvider`, for `.accessoryCircular`, `.accessoryCorner`, `.accessoryRectangular` and `.accessoryInline`;
  - its display name and description from its own table;
  - its entries carry `TimelineEntryRelevance(score: relevant ? 1 : 0, duration: nextAt − at)`;
  - its policy is `.after(reloadAt)`, or `.never` with no feed;
  - each `getTimeline` logs `TaqwaWatch timeline <entries> <ms> reloadAt <epoch>`;
  - on watchOS 11 and later, `relevance()` gives a `WidgetRelevanceAttribute(context: .date(from:to:))` for each window.
- The views, from `faces.js`:
  - **circular** (`aCircular`): `appRing`'s track and accent arc as a `Gauge` (`.accessoryCircularCapacity`), with the label over the time;
  - **corner** (`aAnalog`): a gauge holding `TaqwaArch`, with `"\(label) \(time)"` as its `.widgetLabel`;
  - **rectangular** (`aRect`): the label in the accent, ExtraBold, tracked 1.1 under `latinScript` and 0 otherwise, then the time in ink; the countdown in mist; and `periodBar`, 4 pt high;
  - **inline** (`aUtility`): `"\(label) \(countdown)"`;
  - **the Smart Stack** (`aStack`): when `showsWidgetContainerBackground` holds, the rectangular below a header of `TaqwaArch` and `words.appName` in mist, on `containerBackground` `#1A1D1C`.
  - **Without the countdown** (`countdownShown` false): no countdown line, the gauge and the bar as their track alone, the inline `"\(label) \(time)"`, and the element said as `spokenWithoutCountdown`.
  - **The empty form** is `TaqwaArch` on its own (circular, corner); the app's name above the notice (rectangular); or the app's name (inline). With a feed (spent), the app's name is `words.appName` and the notice `words.openOnPhone`, or `words.updateOnWatch` when `update`. With no feed, both come from the extension's own table: `CFBundleDisplayName`, and `open_taqwa_on_phone` or `update_taqwa_on_watch`.
  - Every family is one accessibility element: `children: .ignore`, labelled `glance.spoken` (or `spokenWithoutCountdown`), or the empty form's words.
- **As built (30 October, Lane A):**
  - Erratum p4 #10 (a late reload): past the hour's end, `timeline` adds one entry without the countdown at each of the next `Glances.lateStarts` (10) starts. Each entry is `make(start + 1)`, so it names the prayer after its start. The timeline ends in the empty entry where that reaches the feed's end. `reloadAt` is unchanged, so there are at most 72 entries (62 within the hour, while every start is on a whole minute: the minutes are counted back from each start, so a start off the minute would put its own entry between two of them). The tests are `testALateReloadStillNamesTheNextPrayer` and `testLateEntriesThatReachTheFeedsEndEndInTheEmptyEntry`. An empty entry has `countdownShown` false, and a timeline stops at its first empty entry, so one that reaches the feed's end inside the hour ends there.
  - **Ruling 64 item 2 (Ruling 28's turn of the date, everywhere in the timeline):** the late entries step to the earlier of the next start and the next turn of the date after which the answer is another (`Glances.nextChange`; the turn found by halving on `WatchRules.today`, as the Galaxy's `ComplicationTimelines.nextDayChange` finds it; "another answer" is the Galaxy's `answerChangesAt`: any of the prayer, its start, the period's start, the label, the name or the time). A turn that changes nothing gets no entry, so the samples' late entries are as before. In `tromso-late-maghrib`, 18 May's Maghrib (00:45 on the 19th) falls after the 19th's Fajr (00:38): the entries went 17:09 "MAGHRIB 0:45" until 00:45, and now go 17:09 Maghrib, 00:00 Fajr, 00:38 Dhuhr. And in both Tromsø feeds the empty entry now stands at the feed's last midnight (20 May 00:00), not at a Maghrib past it (00:49, 00:29). A turn's entry takes one of the 10, so a timeline still holds at most 72 entries. Within the hour the minutes already step at every turn of the date, which falls on a whole minute. `relevanceWindows` steps the same way: each window is `relevantBefore` up to a start, inside the stretch in which that start is the one counted to, from `now` or the step's start (a start or a turn), so a start a turn drops before it comes (the late Maghrib) has none past the turn, and one the turn brings in (that Fajr) has its window; and none overlaps the next. So an instant is in a window exactly when an entry drawn for it is `relevant`. The tests are `testPastTheHourAMaghribDroppedAtMidnightIsDroppedThere`, `testPastTheHourTheEmptyFormComesAtTheFeedsLastMidnight`, `testTheRelevanceWindowsTurnOverWithTheDate`, and the review's scratch probe, committed as `testEveryEntryAndWindowHoldsAcrossTheDatesTurn`: from 208 instants around every start and turn of the date of both Tromsø feeds, every entry draws through its span what holds there (in the hour, the countdown too; 38,709 spans), the empty entry stands where nothing holds, at most 72 entries, and the windows match the entries' own rule instant by instant and never overlap. At the base it finds 2,904 mismatches (249 in the entries, 2,655 in the windows, overlaps included), and none after.
  - Erratum p4 #5 (VoiceOver's language): `Glance` gains `language` (`ui.language`), and `GlanceTimeline` gains `script: GlanceScript?` (`language`, `rtl`, `latinScript`; nil with no feed). A spent feed's empty form therefore keeps the feed's language, direction and type. Every label is `Speech.line(text, language:)`: an `AttributedString` whose `languageIdentifier` is the feed's tag, and none for the watch's own words. The watchOS SDK has no `accessibilityLanguage` (as the erratum says) and no speech-language key in its AttributedString accessibility scope (`Accessibility.swiftinterface`, watchOS 27.2 SDK), so Foundation's `languageIdentifier` carries it. That this reaches VoiceOver's voice is unproven until plan 5 hears it on her watch.
  - Erratum p4 #12: the timeline reads `feed.words` once, into `GlanceTimeline.words`, and every entry's `NextPrayerContent` shares that copy. `make` reads no words.
  - Erratum p4 #8: `-taqwaStartIn` counts in `DebugArguments.holdsState`. A launch that sets the store also reloads the timelines, so the complications draw it as they would a phone's feed.
  - `-taqwaStartIn <minutes>` (1 to 180) stores `capetown` moved so that its 18 October's Asr is the next start, at the first whole minute that many minutes or more away. The rule is `DebugSample.startingIn`, with three tests. `-taqwaAt` also takes `HH:MM:SS`, for Step 5's 16:09:30.
  - The views take their family as a parameter (`NextPrayerView(family:content:card:)`, with `NextPrayerContent` made from an entry and its timeline), because `\.widgetFamily` cannot be set outside a widget. **Since Ruling 64 item 3** `NextPrayerContent` lives in WatchCore (`NextPrayerContent.swift`) with no family in it: `spoken(inline:)` takes the one thing the family decided, and the watch's own three words come in as `OwnLines`, which each target reads once per timeline (`OwnWords.lines`). `NextPrayerContentTests` holds the notice's order, the spoken sentence and its language, the inline and corner lines and `withoutCountdown` (10 tests; nine mutants of the moved code, each killed). The gallery draws them that way. The widget's entry view reads the family and `showsWidgetContainerBackground`.
  - The corner's gauge is `faces.js`'s `appRing` drawn to the corner's box (`RingGauge`), with the arch at the drawing's size (`ArchMark`, since the asset keeps the launcher icon's margin). The system's `.accessoryCircularCapacity` gauge keeps the circular family's size and would spill out of a corner. The circular keeps the system gauge as planned. In a right-to-left feed both rings fill anticlockwise from 12 o'clock, as measured on the SE 3 in fix round 1 (ar-sa and ar-ly12 at 13:00 and 14:58: the corner and the circular both 310–0° and 120–0° clockwise from 12, the mirror of Cape Town's 0–50° and 0–240°). Review round 1 found the corner's arc starting at 6 o'clock: SwiftUI mirrors a rotation under a right-to-left layout, and that and the explicit flip together misplaced it. So `RingGauge` draws its track and arc under a fixed left-to-right environment and turns the arc by its flip alone; its content keeps the feed's direction. Task 7's Today ring and Task 8's tasbeeh ring take the same rule: reuse `RingGauge`, or draw the ring left to right with its direction explicit, and check its ends by measurement. **Since Ruling 64 item 3** `RingGauge` is in its own file, `WatchShared/RingGauge.swift`, and `RingGaugeTests` holds its direction durably: the review's ImageRenderer probe, committed, draws it on the Mac under the feed's direction and reads 72 points round its stroke, for the corner's thickness and Today's, at 27/217, 145/217 and a half, both ways. Removing the fixed left-to-right drawing fails it (the right-to-left arc lands at 130–155° clockwise from 12), and so does dropping the flip or the quarter turn. It runs in `swift test`, in a `WatchViews` module of the WatchCore package that compiles WatchCore's sources and the view files the Mac can build through symbolic links in `iosApp/WatchCore/Tests/WatchViews/`, one module as each Xcode target compiles them; the targets themselves are unchanged in what they compile.
  - The gallery is in three parts, `-taqwaGalleryPart 1|2|3` (each family, then the Smart Stack card, then each family without its countdown), because the SE 3's screen holds one part at a time. The corner's line sits under it, since `.widgetLabel` draws only on a face.
  - The log line is the plan's, followed by the family and its box: `TaqwaWatch timeline <entries> <ms> reloadAt <epoch|never> <family> <w>x<h>`.
  - `relevance()` uses `RelevantContext.date(from:to:)`, the watchOS 10 form. watchOS 26 deprecates it, and with the deployment target at 10.0 it builds with no warning.
  - Review round 1: watchOS asks `relevance()` only now and then, and it is the timeline provider's only Smart Stack clue there. So whenever the app stores a feed (or first sees a newer format), and after a DEBUG launch that sets the store, it calls `NextPrayerWidgets.reload()` (`WatchShared/NextPrayerViews.swift`): `reloadAllTimelines()`, then, on watchOS 11 and later, `WidgetCenter.shared.invalidateRelevance(ofKind:)` with the widget's kind, which moved into `WatchShared` as `NextPrayerWidgets.kind` so the app can name it. The first feed, or a new city's, then lifts Taqwa before the coming prayers without waiting for the next periodic ask.
  - Review round 1, **AGREED by the Lead in Ruling 64 item 1 (the plan's "`make(now)`" reads as this; 386 assertions in 4 tests and a 1.98M-span probe hold it):** the first entry is the glance that holds for now's minute. That is `make(now)`, or `make(now + 1)` when `now` is a whole minute before its start (`(nextAt − now) % 60 == 0`), as a reload at a start always is. Taken literally, `make(now)` drew the instant's countdown for the whole minute and then skipped one: from 14:58:00, "in 1 h 12 min" and then "in 1 h 10 min"; from Asr's start at 16:10:00, "in 2 h 54 min" and then "2 h 52". This is Review Focus 3's "a complication's minute shows the value that holds for that minute", and Step 7's stop condition. The tests are `testAWholeMinuteNowDrawsTheMinuteThatHolds`, `testATimelineMadeAtAStartDrawsTheMinuteThatHolds` and `testEveryEntryShowsWhatHoldsUntilTheNext`. The last makes 490 timelines on the four samples and Tromsø's May feed, from whole-minute, half-minute and one-second-off instants around up to 10 starts each, and checks 56,682 spans: each entry of the hour draws the prayer and the countdown that hold through its span. The gallery draws the timeline's first entry, so its 14:58 shots now read "in 1 h 11 min".
  - Type: the rectangular's time is Manrope SemiBold, because the drawing's weight 700 has no bundled face. Outside Latin letters the label is 13/11.5 of its Latin size, `faces.js`'s ratio for the Galaxy's label.
  - `tools/add-watch-targets.rb` needed no edit: it finds files on disk, and a second run left the project byte-identical.
  - Steps 6 and 7 did not run. The iOS Simulator control tool's access request for the Series 12 went unanswered, so no face could be edited. Review round 1's request at 19:48 and fix round 1's at 20:10 went unanswered too. As the plan says for this case, they are plan 5's on her watch, or a re-run here once access is granted (`docs/superpowers/reports/2026-10-24-apple-watch-pass/README.md`). **Ruling 64 items 7–8 (30 October, about 23:03):** the `ios-pair` lock was taken to ask once, and the Series 12 was found Booted with no `ios-pair` lock held (as at 22:05), the Claude app's own process streaming its log since 21:50:56 (the simulator panel's attachment), so booted by someone else. It was reported and left as found, unpaired and untouched, and the ask was not made: Steps 6–7 would pair it and install on it, which the rule never to use a simulator another session booted forbids. They stay with P4.11 and then P5.15, on her watch.
  - **Steps 6 and 7 RAN afterwards** (erratum p4 #16, P4.11): on 30 October from 23:20 to 00:07, as "P4.6 faces" (branch `p46-faces` at `44cfbd8d`, merged with Ruling 64), once Mohamed had granted the control tool access to the Series 12 (Ruling 64 item 7). Taqwa was placed on Modular (rectangular and circular), Infograph (a corner), Utility (inline) and in the Smart Stack, and the twenty-minute idle timeline moved each minute and rolled over at the start: the pass README's Steps 6 and 7 (`docs/superpowers/reports/2026-10-24-apple-watch-pass/README.md`). So the lines above that say they did not run describe the task's own rounds only. What stays for plan 5 on her watch: the Always On cadence with the wrist down, an inactive face's next draw, and the Smart Stack's rise by itself. Plan 4 ticks no step in any task, so Steps 6 to 8 stay unticked like every other; this note is their record.

- [ ] **Step 1: Write the failing tests** on the Cape Town sample. `now` is Cape Town time, given as epoch seconds. FSI and PDI are the tests' constants for U+2068 and U+2069.
  - `testAtTwoFiftyEightTheGlanceIsAsr`: "ASR", "16:10", "in 1 h 12 min", progress 145/217, and spoken "Asr in 1 hour 12 minutes, at 16:10".
  - `testATwelveHourGlanceSaysItsDayPeriod`: en12 at 14:58, the time "4:10" (drawn bare), spoken "Asr in 1 hour 12 minutes, at " + FSI + "4:10 PM" + PDI, and `spokenWithoutCountdown` "Asr, " + FSI + "4:10 PM" + PDI.
  - `testAFridaysDhuhrIsJumuah`: day 6 of the sample (Friday 23 October; day 0 is Saturday 17 October, since the sample starts yesterday) at 11:58 gives the Jumuʿah label, in capitals, as Task 1's `labels` vector has it. Day 5, a Thursday, gives "DHUHR".
  - `testArabicKeepsTheNameAndTheDigits`: ar-SA gives "العصر", "١٦:١٠" and "بعد ١ س ١٢ د".
  - `testTheEntriesBreakWhereTheCountdownChanges`: from 14:57:30, the entries are at 14:57:30 ("in 1 h 12 min"), 14:58:00 ("in 1 h 11 min"), then each 60 s to 15:57:00, then 15:57:30. `reloadAt` is 15:57:30.
  - `testPastTheHourTheCountdownIsDropped`: that 15:57:30 entry has `countdownShown` false, its glance "ASR" and "16:10", and `spokenWithoutCountdown` "Asr, 16:10".
  - `testAStartInsideTheHourRollsOverAndReloadsThere`: from 15:30:00, the 16:10:00 entry's glance is Maghrib's, entries go on each minute to 16:29:00, the 16:30:00 entry has no countdown, and `reloadAt` is 16:10:00.
  - `testUnderAMinuteIsTheLastMinute`: from 15:30:00, the 16:09:00 entry says "in under a minute".
  - `testTheTimelineIsAnHourOfMinutes`: on Tromsø's longest period and on each sample at 00:00, at most 62 entries, 60 s apart but at a start, and only the last without the countdown.
  - `testASpentFeedEndsInTheEmptyEntry`, `testNoFeedIsOneEmptyEntry` (with `words` nil and `reloadAt` nil), and `testANewerFormatSeenSaysUpdateInTheEmptyForm` (`update` true, with a spent feed and with none).
  - `testOnlyTheHalfHourBeforeAStartIsRelevant`, `testTheRelevanceWindowsAreTheComingStarts`.
  - `testTheScreenWordsSpeakWhenTheFeedHasNoSpokenWords`: on `olderPhone`, "Asr 16:10, in 1 h 12 min".
- [ ] **Step 2:** Run `swift test`. Expected: FAIL. Not committed.
- [ ] **Step 3: Implement `Glance`, the provider, the views and the gallery,** and the DEBUG `-taqwaStartIn <minutes>`: it stores the `capetown` sample shifted so that its next start after the real now is that many minutes away. Every instant and midnight moves by the same whole number of minutes, and the clock strings are left as written, so only Step 7 uses it. The gallery lays out each family at the SE 3 (40mm) sizes, then the Smart Stack card, then each family without its countdown.
- [ ] **Step 4:** Run `swift test`, `scripts/ios-build.sh` and `scripts/watch-build.sh test`. Expected: PASS.
- [ ] **Step 5: Shoot the gallery** on the SE 3, with `-taqwaPage gallery -taqwaFrozen 1`:
  - at 14:58 in `capetown`, `en12`, `ar-ly12` and `ar-sa`;
  - at 16:09:30 in `capetown`;
  - with no feed, and with no feed and `-taqwaNewerSeen 1`.

  Use `xcrun simctl io <udid> screenshot`, saved in the pass folder. Expected: each family matches its `faces.js` drawing: the words, the digits, the direction, nothing clipped, and the bar filling in reading order.
- [ ] **Step 6: On the Series 12** (paired and given the watch app if `xcrun simctl list pairs` shows no pair, then put back as found, Global Constraints), put Taqwa on a Modular face (rectangular and circular), on an analog face with corners, on Utility (inline) and in the Smart Stack, with the iOS Simulator control tool driving the watch simulator. Shoot each. If the tool cannot drive the watch's face editor, write that in the pass's README; the placement and Step 7 are then plan 5's, on her watch.
- [ ] **Step 7: The timeline with the extension idle,** on the Series 12's Modular face from Step 6 (the pair's turn, Global Constraints):
  - Launch the watch app with `-taqwaStartIn 12`, wait for the `TaqwaWatch timeline` line, then terminate the app (`xcrun simctl terminate C803D94B-7239-435B-9CEB-2A6AD75F141F <BUNDLE_ID>.watchkitapp`) and return to the face with the control tool's `button HOME`.
  - Start a loop script kept in `build/` (untracked), in the background: `xcrun simctl io C803D94B-7239-435B-9CEB-2A6AD75F141F screenshot build/timeline/t<n>.png` every 60 s for 20 minutes, from 30 s past a minute.
  - Read each shot's countdown (zoom on the rectangular), and write the minute-by-minute table in the pass README with the timeline lines from the extension's log.
  - Expected: each shot shows its minute's countdown, the start at minute 12 rolls over to the next prayer, one `getTimeline` comes at that start (the reload) and none between, and each logs 72 entries (62 in the hour, at most, while every start is on a whole minute, plus 10 late ones: Ruling 64 item 3, erratum p4 #10) and its duration.
  - If a minute shows a stale or skipped value, or the face does not move, stop and tell the Lead: the Lead's call 5's fallback. Either way, the same check on her watch is plan 5's gate.
- [ ] **Step 8: Commit** with the message "Apple Watch: Taqwa on Apple's faces and in the Smart Stack, in the phone's words, a minute at a time for the coming hour".

### Task 7: the app's pages, and Today

**Files:**
- Create:
  - `iosApp/WatchCore/Sources/WatchCore/Today.swift`, `iosApp/WatchCore/Tests/WatchCoreTests/TodayTests.swift`;
  - `iosApp/TaqwaWatch/TodayView.swift`, `MessageView.swift`;
  - `iosApp/TaqwaWatchUITests/TodayReadingOrderTests.swift`.
- Modify: `TaqwaWatchApp.swift` (the pages), `tools/add-watch-targets.rb`, re-run.

**Interfaces:**
- Consumes: `WatchRules` (with Task 3's `clockText`, `spokenClock`, `inDigits` and `filled`), `Glances.make`, `feed.words`, `AppModel`. Its rules restate plan 3's `today` and `dayRows` (plan 3 Task 5), and its tests restate their cases.
- Produces:
  - `enum RowState { case current, passed, upcoming }`;
  - `struct TodayRow: Equatable { time: WatchTime; name: String; clock: String; status: RowState; small: Bool; spoken: String; stateWord: String? }`, one for each `WatchTime`, in the day's order. `.SUNRISE` is `small` and never current;
  - `struct TodayModel: Equatable { ringLabel: String; countdown: String; time: String; notice: Bool; progress: Float; spoken: String; rows: [TodayRow]; mirrorsRing: Bool }`;
  - `enum OwnMessage: Equatable { case openOnPhone, updateOnWatch }`, the watch's own two lines;
  - `enum TodayScreen: Equatable { case message(OwnMessage), spent(String), ready(TodayModel) }`;
  - `enum Today { static func screen(_ feed: WatchFeed?, newerVersionSeen: Bool, now: Int64) -> TodayScreen; static func clock(_ feed: WatchFeed, secondsLeft: Int64, seconds: Bool) -> String }`.
- The rules:
  - `ringLabel` is `filled(ui.nextIn, name)`, in capitals in the feed's language under `latinScript` ("ASR IN"), and as written otherwise ("متبقٍّ على العصر").
  - `clock` is H:MM:SS, or H:MM without seconds, the hours unpadded, through `inDigits(_, ui.ringDigitZero ?? ui.digitZero)`: the phone ring's digits.
  - `time` is the next start's `clockText`, from its own day.
  - `spoken` is `spokenCountdown(feed, name, spokenClock(day, .of(next)), secondsLeft)`, or `"\(ringLabel) \(time)"` when the feed has no spoken words.
  - The rows are the six of `WatchRules.today`'s day (since the owner's decision of 9 November 2026, the five prayers alone while the phone's Show sunrise is off, `ui.showSunrise`). `current` is `litOnList`. A start at or before now is `passed`, and so is Sunrise once it has come. The rest are `upcoming`. Each row's `clock` is `clockText`, its `spoken` the name and `spokenClock` joined by ", ", and its `stateWord` `rowCurrent`, `rowPassed`, or `rowNext` on the next start's row, nil otherwise.
  - `mirrorsRing` is `ui.rtl`.
  - **The states, in plan 3's call 5 order:**
    - no feed: `.message(.updateOnWatch)` when `newerVersionSeen`, else `.message(.openOnPhone)`;
    - a spent feed: `.spent(words.updateOnWatch)` when `newerVersionSeen`, else `.spent(words.openOnPhone)`;
    - a feed with `newerVersionSeen`: `.ready`, with `time` = `words.updateOnWatch` and `notice` true, and `spoken` followed by the phone's comma ("، " when `rtl`, else ", ") and `words.updateOnWatch`.
- The view (`aToday`):
  - `appRing`, radius 52 and width 5.4, the label in the accent, the countdown in Manrope Light 23 with tabular figures, and the time in mist (a notice in the accent);
  - a `TimelineView` that ticks each second, and each minute without seconds while `isLuminanceReduced`;
  - below it a `ScrollView` of the rows with `pip`, opened scrolled to the lit row or the next;
  - Sunrise in the small size.
- **The pages:** `TabView` with `.tabViewStyle(.page)` holds Today, Tasbeeh and Qibla, in that order. A swipe moves between them, so the crown is free on Tasbeeh. Until Tasks 8 and 9, the other two show `words.tasbeeh` and `words.qibla`.
  - With no feed, all three pages show `MessageView` with the watch's own line: `update_taqwa_on_watch` when a newer format has been seen, else `open_taqwa_on_phone`.
  - With a spent feed, Today shows `.spent`'s word, and Tasbeeh and Qibla keep working from the last feed's words and place, which hold no time.
- **VoiceOver (Ruling 42, the prayer first):**
  - The ring is one element, labelled `spoken`, read first.
  - Each row is one element, labelled `spoken`, with `stateWord` as its value.
  - Pips and the ring's stroke are hidden.

- **As built (30 October, Lane A):**
  - `TodayModel` gains `language` (`ui.language`, erratum p4 #5: the ring's and the rows' VoiceOver lines are `Speech.line` in the feed's language) and `latinScript` (the type), so a ready Today is drawn from the model alone. Its `focus` (computed, so `Equatable` is unchanged) is the row the list opens on: the lit row, or with none lit the first upcoming prayer's. `TodayRow.time` is the `WatchTime` (erratum p4 #2), Sunrise included.
  - `Today.screen` takes `seconds: Bool = true`. Without seconds (the always-on screen, drawn once a minute on the minute), the screen is the one that holds for the minute from `now`: when `now` is a whole minute before the next start, it is drawn a second later, as `Glances.timeline`'s first entry is (P4.6 review round 1). So at 14:58:00 the always-on ring reads "1:11", not the instant's "1:12". The tests are `testAlwaysOnDropsTheSeconds` and `testAlwaysOnOnAWholeMinuteShowsTheMinuteThatHolds`.
  - The ring label's capitals are `WatchRules.capitals(_:language:)`, the casing `label` already used (Kotlin's `uppercase(Locale.forLanguageTag(tag))`), now shared. `WatchTimes.of(_ time:)` reads a day's instant by `WatchTime`.
  - `feed.words` is read once per `Today.screen` (each frame of the page) and once per drawing of the pages (erratum p4 #12).
  - The spoken fallback with no spoken words is `"\(ringLabel) \(clock)"`, the next start's `clockText` even when the notice takes the time's place, as plan 3's `today` has it.
  - The ring is `RingGauge` (P4.6) with a new `thickness` (5.4 of a 109.4 side), so it takes P4.6's direction rule: drawn left to right with its flip explicit, anticlockwise from 12 o'clock in Arabic, measured in the shots. The texts sit on `aToday`'s baselines (52, 77, 92). The notice is the drawing's builder's call: SemiBold 9.5 on a baseline at 90, two lines in a box 64 wide, shrinking to 0.7 at most, so both lines stay inside the ring (10.5 at 92 cut "on your watch" against the ring's stroke).
  - The rows grow with the watch's text size up to 1.3 times, as the Galaxy's do (plan 3's call 15); the ring keeps its size. Sunrise is 10/12 of a row and 17 high, in slate, with no pip (`gTile`). The list ends in two rows' room, so a lit Isha opens near the top and not under the fade at the foot.
  - The list goes to its `focus` whenever it is seen afresh (review round 1's Important): when it first appears, each time the app is opened again after it went to the background (watchOS resumes the app's views when it is opened again from the clock face, so `onAppear` alone did not run then), and when the focus moves with the app open (the next start lit, Sunrise ending Fajr, the day turning over at the feed's midnight). The UI tests `testOpenedAgainTheListStandsOnTheLitRow` and `testTheListMovesWithTheLitRow` hold it, against the list's own frame: a row's element is its texts', so at the top it starts about 1.5 pt under the list's edge.
  - **Ruling 64 item 4:** the list re-focuses on `.active` only after a `.background`, by a small pure policy in WatchCore (`TodayScroll`, with `TodayScrollTests`: a lowered and raised wrist keeps the scroll, as does a banner; background then active re-focuses, once; the focus's move still follows, in any phase). By Apple's Always On rules a lowered wrist makes the app inactive and leaves it frontmost, dimmed, and a raised one active again, so review round 2 found the scroll a wearer had made reset, and the always-on frame jumping, on every raise. `testOpenedAgainTheListStandsOnTheLitRow` still holds on the SE 3 (its Home press sends the app to the background). No simulator control lowers a wrist, so the inactive path is held by the unit tests alone, and on her watch in plan 5.
  - **Ruling 64 item 5:** a row's state value is inert (`TaqwaWatch/StateValue.swift`): always set, empty with no word, never added by an if/else, whose two branches were two views, so a row that becomes "Next prayer" at a start, or loses its word, kept no identity and VoiceOver lost its focus on it. `StateValueTests` draws a row in an NSHostingView on the Mac and flips its word through nil, "Next prayer", nil, "Current prayer" and "Passed": the if/else form made it anew three times (4 appearances, 3 removals), the inert one keeps it (1, 0). An empty value is not spoken, and the UI tests read it as "". `testTheListMovesWithTheLitRow` now starts at 16:09:00 with `holds(for: 75)`: a minute from the clock's start to Asr's, twice the 30 s it had, which erratum p4 #15 makes the floor for UI tests on a running clock.
  - The pager runs left to right in every language, as the Galaxy's (plan 3's call 4); each page's content takes the feed's direction, or with no feed the watch's own. `TabView`'s page dots stay (`.page`).
  - `AppClock` is `at(_ date:)` with `now()` as `at(Date())`, so a `TimelineView` frame drawn ahead of time for the always-on screen shows its own instant. The schedule is `.periodic` from a whole minute, by 1 s or by 60 s while `isLuminanceReduced`.
  - The UI test reads the elements in the tree's own order, depth first, from `XCUIElement.snapshot()`. A query's `allElementsBoundByIndex` came in another order: a ring declared after the rows still came first. XCUITest's tree also lists the texts drawn inside each element, which VoiceOver does not visit (each element ignores its children), so the test cannot hold that the ring's parts are not read alone; it reads only the elements, by identifier (`today.ring`, `today.row.<TIME>`, `page.<page>`).
  - The shots are at 14:58:24, the drawing's own "1:11:36", rather than 14:58, whose instant reads "1:12:00".

- [ ] **Step 1: Write the failing tests.** FSI and PDI are the tests' constants.
  - `testTodayAtTwoFiftyEight`: at 14:58:24, "ASR IN", "1:11:36", "16:10", and spoken "Asr in 1 hour 11 minutes, at 16:10". The rows are Fajr passed, Sunrise passed and small, Dhuhr current, Asr upcoming, Maghrib upcoming, Isha upcoming; the state words are "Passed", "Passed", "Current prayer", "Next prayer", nil, nil.
  - `testSaudiTimesInArabicIndicAndTheRingInWestern`: ar-SA at 14:58:24 gives "متبقٍّ على العصر", the time "١٦:١٠", the countdown "1:11:36", and every row's clock in Arabic-Indic digits.
  - `testAlwaysOnDropsTheSeconds`: "1:11".
  - `testTwelveHourRowsCarryTheDayPeriod`: en12 gives Asr's row the clock "4:10 PM" and the spoken "Asr, " + FSI + "4:10 PM" + PDI; the ring's time "4:10 PM" and spoken "Asr in 1 hour 11 minutes, at " + FSI + "4:10 PM" + PDI.
  - `testARowIsSaidAsThePhoneSaysIt`: Asr's spoken text is "Asr, 16:10".
  - `testBeforeFajrNothingIsLit`.
  - `testFajrStopsBeingCurrentAtSunrise`.
  - `testTheListTurnsOverAtTheFeedsMidnightNotTheWatchs`: under `NSTimeZone.default = Pacific/Auckland`, at day 1's midnight + 1 s the rows are day 1's.
  - `testNoFeedSaysOpenTaqwaOnYourPhone`: `.message(.openOnPhone)`.
  - `testNoFeedAfterANewerFormatSaysUpdate`: `.message(.updateOnWatch)`.
  - `testASpentFeedIsSpent`: `.spent("Open Taqwa on your phone")`, and `.spent("Update Taqwa on your watch")` with `newerVersionSeen`.
  - `testANewerVersionTakesTheTimesPlace`: at 14:58:24 with `newerVersionSeen`, the time is "Update Taqwa on your watch", `notice` is true, the countdown still "1:11:36", and spoken "Asr in 1 hour 11 minutes, at 16:10, Update Taqwa on your watch". In ar-ly12 the separator is "، ".
  - `testTheRingMirrorsInArabic`.
  - UI test `testTodayReadsTheRingThenTheDay`: with `-taqwaSampleFeed capetown -taqwaAt 14:57 -taqwaFrozen 1 -taqwaPage today`, the first element is labelled "Asr in 1 hour 13 minutes, at 16:10", then "Fajr, 4:33" with the value "Passed", and so on to "Isha, 20:24".
  - UI test `testANewerFormatWithNoFeedSaysUpdate`: with `-taqwaNoFeed 1 -taqwaNewerSeen 1`, each of the three pages (swiped to) shows "Update Taqwa on your watch".
- [ ] **Step 2:** Run `swift test` and `scripts/watch-build.sh test`. Expected: FAIL. Not committed.
- [ ] **Step 3: Implement the model, the pages, Today and the messages.**
- [ ] **Step 4:** Run both again. Expected: PASS. Then shoot Today on the SE 3 at 14:58 in the four samples, with no feed, with no feed and `-taqwaNewerSeen 1`, and with the sample and `-taqwaNewerSeen 1`. Expected: each matches `aToday`, the ar-SA countdown is in Western digits under Arabic-Indic times, en12 shows "4:10 PM", and the Arabic ring fills anticlockwise.
- [ ] **Step 5: Commit** with the message "Apple Watch: the three pages, and Today's ring above the day, in the phone's words, digits and clock".

### Task 8: Tasbeeh with the crown and undo

**Files:**
- Create:
  - `watchcore/src/androidHostTest/kotlin/world/taqwa/app/tasbeeh/TasbeehVectorsTest.kt` (plan 3's Task 1 moved `TasbeehEngine` into `:watchcore`);
  - `watchcore/vectors/tasbeeh-vectors.json`;
  - `iosApp/WatchCore/Sources/WatchCore/Tasbeeh.swift`, `CrownCounter.swift`, `TasbeehCounter.swift`, `TasbeehFeel.swift`, `TasbeehStore.swift`, `TasbeehSpeech.swift`, `TasbeehScreen.swift`, and their tests (`TasbeehVectorsTests.swift`, `CrownCounterTests.swift`, `TasbeehCounterTests.swift`, `TasbeehFeelTests.swift`, `TasbeehStoreTests.swift`, `TasbeehSpeechTests.swift`, `TasbeehScreenTests.swift`);
  - `iosApp/TaqwaWatch/TasbeehView.swift`, `PhraseListView.swift`, `Haptics.swift`;
  - `iosApp/TaqwaWatchUITests/TasbeehTests.swift`, `TasbeehCrownTests.swift`.
- Modify: `TaqwaWatchApp.swift`, `iosApp/TaqwaWatch/DebugLaunch.swift` (`-taqwaTasbeehCount`), `tools/add-watch-targets.rb`, re-run.

**Interfaces:**
- Consumes: plan 3's Kotlin rules (Task 1: `TasbeehEngine` with `undo`, `SET_COMPLETE_HOLD_MS`, `TasbeehPresets`), and plan 3's Task 6 `TasbeehSpeech` and call 14, whose test cases the Swift tests below restate. The tests pin this undo: count − 1 within the round, nothing at 0, the round unchanged. If plan 3's differs, the vectors win and the Lead is told.
- Produces `tasbeeh-vectors.json`: `{name, preset: {id, parts: [count]}, start: {count, round}, steps: [{do: "tap"|"undo"|"nextRound"|"reset", count, round, part, event?}]}`. `event` is `tick`, `part:<i>` or `set`. The sequences:
  - the after-prayer set from 0 to 101 taps;
  - undo at 0, at 33, at 34, at 66 and at 100;
  - `nextRound` after the set;
  - a tap at 100;
  - SubhanAllah's 100;
  - custom presets of 7, 1 and 0.
- Produces in Swift, ported from the Kotlin:
  - `struct Dhikr`, `struct DhikrPart`, `struct TasbeehPreset { id; parts; custom; var total: Int }`, `struct TasbeehState: Codable, Equatable { presetId; count; round }`, and `enum TapEvent: Equatable { case tick, partComplete(Int), setComplete }`;
  - `enum TasbeehEngine` with `tap`, `undo`, `currentPart`, `reset`, `nextRound` and `partEnds`;
  - `enum TasbeehPresets` with `builtIn` (the seven, with their Arabic and transliterations from `TasbeehPresets.kt`), `defaultId = "after_prayer"`, `custom(_ phrase: WatchPhrase)` and `list(_ feed: WatchFeed?)`, which gives the built-ins first and then the feed's phrases in order;
  - `enum TasbeehTiming { static let setCompleteHold = 1.0; static let writeDebounce = 0.3 }`, the phone's.
- Produces `struct CrownCounter { mutating func turned(to value: Double) -> Int }`: the whole detents since the last call, `+n` counts and `−n` undos. The first call seeds it, and it is re-seeded when the phrase changes.
- Produces `struct TasbeehCounter: Equatable`, the phone's `TasbeehViewModel` hold and debounce (`TasbeehViewModel.kt:66-73` and its `holdJob`, `debounceJob`, `settle` and `flush`) as a state machine on explicit time, so that `swift test` reaches it. Time is seconds on a monotonic clock (`ProcessInfo.systemUptime` in the app), never shown.
  - `init(preset: TasbeehPreset, state: TasbeehState, now: Double)`: a state opened at or past its total starts a hold, as the phone's `holdIfComplete` does;
  - `private(set) var state: TasbeehState`, `private(set) var holdUntil: Double?`, `private(set) var writeDue: Double?`, and `var nextDeadline: Double?`, the earlier of the two;
  - `mutating func tap(now: Double) -> TapEvent`: cancels any hold, applies `TasbeehEngine.tap`, sets `writeDue = now + writeDebounce`, and on `.setComplete` sets `holdUntil = now + setCompleteHold`. A tap inside the hold therefore opens the next round at 1, as the phone's does;
  - `mutating func undo(now: Double) -> Bool`: cancels any hold, applies the undo and sets `writeDue` as a tap does. At 0 it answers false and changes nothing;
  - `mutating func turn(_ detents: Int, now: Double) -> TurnResult`, with `struct TurnResult: Equatable { events: [TapEvent]; undos: Int }`: one `tap` for each positive detent and one `undo` for each negative one, in order, answering the taps' events and the number of undos that changed the count. A tap on the screen is `turn(1, now:)`;
  - `mutating func advance(to now: Double) -> TasbeehState?`: the state to save now, or nil. A due hold clears and applies `rolledOver`, the phone's guard: nothing for a total of 0 or a count below the total, so a 1 of round 2 stays a 1. A rollover that moved the count is saved at once. A due `writeDue` clears and saves. When both are due in one call, the answer is the state after the rollover;
  - `mutating func settle() -> TasbeehState`: the phone's `flush`. A pending hold rolls over first, both deadlines clear, and the answer is what to save. The view calls it when `scenePhase` leaves `.active`, before switching phrase, and when the page is swiped away.
- Produces `struct TasbeehFeel` (the Lead's call 7, plan 3's call 14): `enum Haptic: Equatable { case count, undo, part, set }`, `static let partQuiet = 0.14`, `static let setQuiet = 0.40`, and `mutating func choose(_ result: TurnResult, now: Double) -> Haptic?`:
  - the strongest the turn crossed: `.set` for any `.setComplete`, else `.part` for any `.partComplete`, else `.count` for a tap, else `.undo` for an undo that changed the count; nil when nothing changed;
  - a `.part` or `.set` is always chosen, and opens a quiet window of `partQuiet` or `setQuiet` from `now`;
  - no `.count` or `.undo` is chosen inside a quiet window. The count itself is still counted.
- Produces `final class TasbeehStore`:
  - `init(directory: URL)`, `func state(for id: String) -> TasbeehState` (0 and round 1 by default), `func save(_ state: TasbeehState)`, and `var selectedId: String`;
  - one atomic `tasbeeh.json` in the app's own Application Support, excluded from backup;
  - a selected id that is no longer in `list(feed)` falls back to `after_prayer`.
- Produces `enum TasbeehSpeech`, plan 3's rules (its Task 6, round 2's spec §1), restated: `static func name(_ preset: TasbeehPreset, _ state: TasbeehState, _ feed: WatchFeed) -> String` and `static func state(_ preset: TasbeehPreset, _ state: TasbeehState, _ feed: WatchFeed) -> String`.
  - `name` is `words.presets[id]`, or the phrase as typed; from round 2 on, `nameRoundSpoken(name, round)`.
  - `state` is `countSpoken(count, total)`. At a part's end it is `nextSpoken(…, the next part's name)`; at the set's end, `setCompleteSpoken(…)`; at count 0 of round 2 or later, `roundSpoken(…, round)`. Numbers are in the feed's digits.
- Produces `struct TasbeehScreenModel: Equatable { arabic; transliteration: String?; roundLabel; count; ofLabel; progress: Double; notches: [Double]; passedNotches: [Double]; partDots: Int; currentPart: Int; hint; spokenName; spokenState; countAction; undoAction }` and `enum TasbeehScreen { static func model(_ feed: WatchFeed, preset: TasbeehPreset, state: TasbeehState) -> TasbeehScreenModel }`:
  - `roundLabel` is `round` filled, `ofLabel` is `of` filled with the total, and `hint` is `crownHint`: the drawn words, never spoken;
  - `transliteration` only under `latinScript`;
  - `notches` at each part's end but the last, as shares of the total, shown once passed;
  - `partDots` 0 for a single part;
  - `spokenName` and `spokenState` are `TasbeehSpeech`'s, and `countAction` and `undoAction` the feed's.
- The view (`aTasbeeh`):
  - the dhikr in the system font, size 15, with the transliteration under it;
  - the ring of radius 54 and width 5.2, clockwise from twelve in every language;
  - `ROUND n` in the accent, the count in Manrope Light 36, then `of 100` and the part dots;
  - the crown's arrows on the crown's side;
  - the hint at the bottom.
  - A tap anywhere counts.
  - `.focusable()` with `.digitalCrownRotation(detent: $crown, from: -1_000_000, through: 1_000_000, by: 1, sensitivity: .medium, isContinuous: true, isHapticFeedbackEnabled: false)` feeds `CrownCounter`.
  - Every count, undo and crown detent goes through one `TasbeehCounter.turn`, and its answer through one `TasbeehFeel`, whose haptic `Haptics.swift` plays (`WKInterfaceDevice.current().play`). The view keeps one pending `Task` that sleeps until `nextDeadline`, then calls `advance(to:)` and saves what it answers. Each change replaces that task. The view holds no hold, debounce or haptic logic of its own.
  - `onChange(of: scenePhase)` leaving `.active` calls `settle()` and saves, as the phone flushes on leave, so a wrist lowered mid-burst loses nothing.
  - Tapping the dhikr's line opens `PhraseListView`, with the current phrase checked.
  - VoiceOver: one element, `accessibilityIdentifier("tasbeeh.counter")`, labelled `spokenName`, with `spokenState` as its value. Its default action counts, with `countAction` as its hint; a named action `undoAction` undoes; `.accessibilityAdjustableAction` counts on increment and undoes on decrement. After a count or undo from the default or the named action, the view posts `AccessibilityNotification.Announcement(spokenState)`, so a part's and a set's end are heard; VoiceOver reads an adjustable change itself.
- Produces the DEBUG `-taqwaTasbeehCount <n>`: the selected phrase's state at launch is `n` of round 1.

- **As built (31 October, Lane A):**
  - **The vectors** (`tasbeeh-vectors.json`, 16 sequences, 246 steps) also carry `builtIn` (the seven in the phone's order, each part's dhikr id, Arabic, transliteration and count), `defaultId`, `setCompleteHoldMs` and `maxTarget`, and each turn back says `undone`. Beside the plan's sequences: a turn back at 0 of round 3, a count above the target (a tap opens the next round at 1, a turn back takes one from the target), a reset from 64 of round 3, and a phrase of 0 that counts nothing. Kotlin's `everyTurnBackTakesOneCountWithinTheRound` holds every turn back to the undo this task pins (count − 1 within the round, nothing at 0, the round unchanged): plan 3's undo is the same, so the Lead needed no word. `theBuiltInsAreThePhonesInItsOrder` ties the list to `WatchScreenWords.ENGLISH.presets`' order.
  - **The order (errata p4 #13, #14, Rulings 60 item 4 and 61 item 3).** `TasbeehPresets.builtIn` is held to the vectors' built-ins and to the words vectors' `presetIds` (`testTheBuiltInsAreInTheOrderTheWordsCarry`). The list's ids the built-ins lack are his phrases (`feed.tasbeeh`, an array, so the phone's order is kept), each kept only when its words are not blank by `WatchScreenWords.isBlank` (Kotlin's `isBlank`, which `orEnglish` reads by) and its id is no built-in's and not already listed, its target held to 1–1,000 (the Galaxy's `PhraseList.of`). The names' dictionary keeps no order, and no screen lists a name with no dhikr to count, so no unknown name id is ever listed; a name is looked up by id.
  - **Errata p4 #1 and #6:** `TasbeehScreenModel` gains `phrasesAction` and `actions` (`TasbeehSpeech.actions`: take one away, then choose a dhikr), which the counter offers VoiceOver in that order. Each list row is one element, its name, ", " and its total (`PhraseRow.spoken`), with the selected trait on the one counted. XCUITest has no way to perform a named action (watchOS 27's `XCUIVoiceOverService` moves and reads, nothing more), so the order is held by `testTheCounterOffersUndoThenThePhrases` and the model's test, and plan 5 hears it.
  - **Erratum p4 #7:** the list ends in the Galaxy's Reset row; it asks `resetConfirm` in a confirmation dialog whose button is `words.reset` and whose dismiss is the system's own (the feed has no word for it), and `TasbeehCounter.reset(now:)` cancels any hold and answers 0 of round 1 to save at once (`testResetGoesBackToRoundOneAndIsSavedAtOnce`, UI `testResetAsksAndGoesBackToTheStart`).
  - **Erratum p4 #9:** `-taqwaFresh 1` empties the tasbeeh store (every count and the choice), and `-taqwaTasbeehPhrase <id>` chooses a phrase (the shots' Astaghfirullah) before `-taqwaTasbeehCount` applies. Every tasbeeh UI test passes a sample and `-taqwaFresh 1`; none sets a feed, so `holdsState` is unchanged.
  - **Errata p4 #5, #12 and #15:** every spoken line (label, value, hint, the actions' names, the announcement, each row) is `Speech.line` in the feed's language; `feed.words` is read once a drawing (`TasbeehScreen.model`, `rows`, the list) and once an announcement; the ring is `RingGauge` with `rtl: false`, and `RingGauge` gains `notches` (`appRing`'s ticks, a hairline across the stroke, drawn inside its fixed left-to-right group), held by `RingGaugeTests`' new `testTheTasbeehRingFillsClockwiseInEveryLanguage` and `testTheNotchesCutTheArcWhereThePartsEnd` (an anticlockwise-notch mutant fails it). The counter's label and value are always set, and a row's traits are one modifier, never an if/else (Ruling 64 item 5). No UI test here runs on the clock.
  - **The counter as built:** `TasbeehCounter.init` holds the state read back as the Galaxy's `TasbeehCodec` does (the count 0 to the target, the round from 1), so a count kept above a target the phone has since lowered shows as the target, finished, and holds. A count of a phrase with nothing to count changes nothing and writes nothing. `TurnResult.events` lists only the counts that changed the count.
  - **The session above the pager:** `TasbeehSession` (in `TasbeehView.swift`) holds the counter, the one `TasbeehFeel`, the store and the one pending task, in the app, because the pager keeps no page it does not show. It takes the feed's phrases when the feed changes (the Galaxy's `setPhrases`: a phrase gone falls back to After prayer; a reworded or re-targeted one keeps its count held to the new target), and settles when the page is swiped away, when the list opens and whenever the scene leaves `.active` (a lowered wrist is `.inactive`, Ruling 64 item 4's finding, and settling there loses nothing). `CrownCounter` lives on the page and is seeded afresh when the page appears and when the phrase changes. A crown value is read as its nearest detent.
  - **The layout's two departures from `aTasbeeh`:** watchOS draws its clock in a status bar 28 points high across the top of the SE 3 (30 units), so the drawing's dhikr at 30 sat under the clock, and in the first build its tap never opened the list. The line stands at 44 (14 points for 15) with the transliteration at 57, between the clock and the ring's top at 61.4; a phrase of his that does not fit one line takes two at 12. The hint is always drawn, as `aTasbeeh` draws it at 64 (the Galaxy shows it at 0 of round 1 alone). **The hint clears the page dots (review round 4's finding, fixed in its fix round 4).** The system's page dots stand from the screen's foot, and the 46mm screen is 205 units high, not the 40mm's 209. On the Series 12 the drawing's hint baseline at 198 fell at 239.4 of its 248 points, and the dots, from 240, crossed the hint's letters, Latin and Arabic. `TasbeehFoot` (WatchCore) keeps the drawing's baseline but never more than 16.45 units under the page's own foot, the bottom of the safe area the pager leaves (171 of the SE 3's 197 points, 212 of the Series 12's 248), which `TasbeehPage` reads with a `GeometryReader`. The dots stand about 23 units under that foot on both watches. So the SE 3 keeps the drawing's place exactly, and its shots are unchanged outside the clock. The Series 12's hint stands 7.5 points higher, its ink 5 points above the dots (4 on the SE 3). `testTheHintStandsAboveThePageDotsOnEveryWatch` (WatchCore) and the UI test `testTheHintStandsClearOfThePageDots` (Latin and Arabic, the hint's frame at least 3 points above the dots) hold it. The UI test was red on the Series 12 before the fix. Shots: `tasbeeh-astaghfirullah-64-46mm.png` (taken again) and `tasbeeh-ar-ly12-40-46mm.png`. The 41, 42, 44, 45 and 49mm sizes were not measured. The crown's arrows mirror to the left edge when the wearer wears the crown on the left (`WKInterfaceDevice.crownOrientation`).
  - **A tap anywhere counts (review round 1's finding, fixed in its fix round 1).** The page's tap is on the drawing's own full-screen frame inside `TasbeehView` (`contentShape` and `onTapGesture` before its `ignoresSafeArea`). As first built, the tap followed `.ignoresSafeArea()` on the page, and a modifier there covers the page's own frame alone, y 40 to 171 of the SE 3's 197 points: review round 1 measured no count and no haptic from y 175 to 195 (the hint "Turn the crown or tap" and the page dots), and fix round 1 found none at y 33 beside the dhikr's line either. Measured after the fix on the SE 3 (a throwaway probe, five columns from x 6.5 to 155.5, rows from y 3 to 196.5): every tap counted but the three on the clock's own digits (x 137.7, y 10 to 24), which the system takes; the line's top at y 30 still opens the list, and a swipe from the page, the hint's band included, still turns it. The counter's element is now the whole screen to VoiceOver as well. `testATapOnTheHintCounts` and `testATapAnywhereBelowTheClockCounts` hold it, red on the first build.
  - **Text size (review round 1's finding, fixed in its fix rounds 1 and 2; plan 3's call 15).** The lines grow with the watch's text size as the Galaxy's do: `@ScaledMetric(relativeTo: .body)` of 100 points (`TasbeehType.scaleBase`), read as a scale by `TasbeehType.scale` in WatchCore, from a floor of 1 to the Galaxy's caps (`wearApp`'s `TasbeehType`): the list's names, targets and Reset, the transliteration, "ROUND n" and "of 100" at 1.3, the dhikr at 1.15, and the count and the hint at their drawn sizes (`testTheLinesGrowWithTheTextSizeUpToTheGalaxysCaps`). Nothing drawn changes at the watch's own default size: the five default counter shots and the two list shots, taken again, are the committed ones pixel for pixel outside the clock. The list's title is the system's, which follows the text size itself. A grown line keeps its words whole: the dhikr's one line and a row's name are drawn at the largest of three steps from the grown size down at which the line (for a row, its widest word) fits, and at the drawing's size otherwise ("Alhamdulillah" broke as "Alhamdulilla / h" at 1.3 in the first shot); two lines of a phrase of his keep 12, which fills the room between the clock and the ring. **The base of 100 (review round 2's finding, fixed in fix round 2).** Fix round 1 read a base of 1 point, as Today's rows do, and SwiftUI rounds a scaled metric to the pixel grid, half a point on the watch: it read 1.0 from XS to XXXL and 1.5 at every accessibility size, so XL, XXL and XXXL grew no line and every accessibility size jumped to the caps. From 100 the scale is the size's own to half a percent. By launch argument on the SE 3 (`xcrun simctl ui content_size` is unsupported on watchOS), the base read 89, 94.5, 100 and 100 at XS, S, M and L, which draw as the default (the floor; the L shots are the default's pixel for pixel outside the clock), then 105.5, 111 and 116 at XL, XXL and XXXL, 127 at AccessibilityM, 132.5 at AccessibilityL and 138 above (review round 2's probe). Astaghfirullah's transliteration (cap 1.3) is 61.0 points wide at the default, 64.0 at XL, 70.5 at XXXL, 77.0 at AccessibilityM and 79.0 at AccessibilityXL, its cap; the dhikr (cap 1.15) is 62.5, 65.5 and 71.5, its cap from XXXL. `testTheTextSizeIsReadFromABaseThePixelGridKeeps` (the grid's rounding of the measured ratios) and the UI test `testTheStandardSizesAboveTheDefaultGrowTheLines` (XL and XXXL launches against the default) hold it, both red on the base of 1. The AccessibilityXL shots are unchanged, since 1.38 is above every cap. Today's rows (Task 7, `TodayView.swift:47`) still read a base of 1 point, so they grow only at the accessibility sizes, to their cap (on the 46mm only from AccessibilityL, where 1 point first reads 1.5): whether they take the same fix is the Lead's call. Shots: `tasbeeh-astaghfirullah-64-axl.png`, `tasbeeh-ar-ly12-40-axl.png`, `tasbeeh-list-capetown-top-axl.png` and `tasbeeh-list-capetown-his-axl.png`, and the same four at XXXL (`-xxxl`). **The watch's own default (review round 3's finding, measured in fix round 3).** Apple gives 40, 41 and 42mm watches Large and 44, 45, 46 and 49mm ones XLarge as their default text size ("Supporting multiple watch sizes"). Review round 3 read `UIFontMetrics` as scaling from Large, which would draw every line about 5 % larger at a large watch's default. Measured on the Series 12 (46mm) under `ios-pair`, it does not: the body style scales from each watch's own default. At the 46mm's default (`.xLarge`) the base reads 100, as `UIFontMetrics.default.scaledValue(for: 100)` does (body 17 points); L and M read 95, XS 84.5, S 89.5, XXL 105, XXXL 110.5, AccessibilityM 120.5, AccessibilityL 125.5 and 131 above. So the floor already starts at the watch's own default, and no drawing code changed. At its default the 46mm draws Astaghfirullah's dhikr and transliteration 79.5 and 78.0 points wide, as at XS, S, M, L and XL (83.5 and 82.0 at XXL); its counter shots at the default, XS, L and XL are one pixel for pixel, and the list's rows are the same below the system's title. `testTheWatchsOwnDefaultSizeDrawsTheDrawing` (UI: the default as wide as XS and L, on any watch) and WatchCore's `testEachWatchsOwnDefaultDrawsTheDrawing` hold it; run on the 46mm, the UI test is red against a scale taken from Large (82.0 points at the default against 78.0). `testTheStandardSizesAboveTheDefaultGrowTheLines` now starts from the watch's own size, read from `WKInterfaceDevice` in the test's process: XL above L, XXL above XL. As fix round 2 wrote it, it was red on the 46mm, where XL is the default. The 41, 42, 44, 45 and 49mm sizes were not measured: no simulator of theirs is in the execution map's locks. Shots at the 46mm's default: `tasbeeh-astaghfirullah-64-46mm.png` and `tasbeeh-list-capetown-top-46mm.png`.
  - **The list** is a `ScrollView` of a stack, not a `List`: a `List` makes only the rows on screen, so its Reset row and his phrases were missing to the UI test (and to a VoiceOver sweep). Rows are pills on the surface, the one counted with an accent border and name; at the default size the names are 13 points and the targets 11 (they grow, above), and "After prayer" takes two lines on the 40mm. The rows take the feed's direction; the sheet's close button and title keep the watch's, clear of the clock.
  - **The crown, measured on the SE 3 (31 October):** a whole turn (`delta: 1.0`) counted n = 8 at `.slow` and 9 at `.fast`; half a turn back took 4 at either; a fast turn of three detents counted exactly 3, three times. So `crownDetent` is 1/8, and the crown accelerates only over a longer fast turn: `testAFastSpinFrom99OpensRoundTwo` asserts round 2 at the fast detents less one, which is 2, as the plan wrote it.
  - **The UI tests wait for the page to stand** before they touch it: a launch on `-taqwaPage tasbeeh` scrolls the pager from Today, and on the first launch after the SE 3 booted the tap test's tap came inside that scroll and counted nothing. The phrase-list test also turns the crown after the list closes (it counts the phrase chosen).
  - **Turkish:** no Turkish sample feed exists, so `testTheTurkishStateNeverHoldsTheDrawnLabels` sets the Turkish words by hand from `WatchWords.kt` and `values-tr` (`%1$s, toplam %2$s`, `%1$s, %2$s. tur`, the drawn `%1$s. TUR` and `/ %1$s`).
  - **Haptics** cannot be felt on the simulator: `TasbeehFeelTests` hold the choice, `Haptics.type(of:)` maps it, and plan 5 judges the feel.

- [ ] **Step 1: Write the Kotlin writer** (`tasbeehVectorsAreUpToDate`, in `VectorsTest`'s pattern), generate the file with `-Ptaqwa.updateVectors`, and run without the flag. Expected: PASS.
- [ ] **Step 2: Write the failing Swift tests.**
  - `testEveryTasbeehSequenceMatchesTheVectors`
  - `testUndoAtZeroStaysAtZeroInTheRound`
  - `testUndoAcrossAPartBoundaryShowsTheEarlierPart`: 33 → 32 shows part 0, and 34 → 33 still shows part 1
  - `testTheCrownCountsWholeDetents`: 0 → 1 is +1, 1 → 4 is +3, 4 → 2 is −2, and 2 → 2.4 is 0
  - `testAFastSpinPastTheEndOpensTheNextRound`: 99 plus 3 detents is set, then round 2 count 1, then 2
  - `TasbeehCounterTests`, on the after-prayer set, times in seconds:
    - `testTheHoldRollsOverToZeroOfTheNextRound`: 99, a tap at 0 is `.setComplete` with `holdUntil` 1.0. `advance(to: 0.99)` leaves 100 of round 1. `advance(to: 1.0)` answers round 2 count 0, and `holdUntil` is nil.
    - `testACrownSpinThroughTheHundredDuringTheHoldKeepsEveryCount`: 99, taps at 0, 0.4 and 0.5 (the crown's three detents) give 100, then round 2 count 1, then 2. `advance(to: 1.5)` answers round 2 count 2, never 0 and never round 3.
    - `testAnUndoDuringTheHoldIsKept`: 99, a tap at 0, an undo at 0.5 gives 99 of round 1. `advance(to: 2)` answers 99 of round 1, the debounced write.
    - `testLeavingDuringTheHoldSavesTheNextRound`: 99, a tap at 0, `settle()` at 0.5 answers round 2 count 0, with both deadlines nil.
    - `testOpeningOnAFinishedSetHolds`: opened at 100 of round 1 at 10, `holdUntil` is 11, and `advance(to: 11)` answers round 2 count 0.
    - `testOneWriteAfterTheLastCount`: taps at 0, 0.1 and 0.2. `advance(to: 0.45)` is nil, `advance(to: 0.5)` answers count 3, and `advance(to: 0.9)` is nil.
    - `testUndoAtZeroWritesNothing`: an undo at 0 answers false, and `writeDue` stays nil.
    - `testATurnAppliesEveryDetentInOrder`: from 31, `turn(3)` gives 34 with events tick, `partComplete(0)`, tick; from 1, `turn(-3)` gives 0 with `undos` 1.
  - `TasbeehFeelTests`, restating plan 3's `WatchTasbeehTest` cases:
    - `testAFastTurnAcrossAPartsEndBuzzesItAlone`: from 31, `turn(3)` chooses `.part` alone. From 1, `turn(-3)` chooses one `.undo`.
    - `testNoTickCutsThePartsBuzz`: from 31, `turn(3)` at 0 chooses `.part`; `turn(1)` at 0.10 chooses nil, and gives 35; `turn(1)` at 0.14 chooses `.count`.
    - `testNoTickCutsTheSetsBuzz`: from 98, `turn(2)` at 0 chooses `.set`, giving 100 of round 1; `turn(-1)` at 0.399 chooses nil, giving 99; `turn(-1)` at 0.40 chooses `.undo`, giving 98.
    - `testUndoAtZeroIsSilent`: nil.
  - `TasbeehSpeechTests`, restating plan 3's `TasbeehSpeechTest`: English, on After prayer: at 13, the name "SubhanAllah" and the state "13 of 100"; at 33, "33 of 100. Next, Alhamdulillah"; at 100, "100 of 100. Round complete"; at 0 of round 2, "Round complete. 0 of 100, round 2", with the name "SubhanAllah, round 2". Saudi Arabic at 13: "١٣ من ١٠٠". A phrase of his own is named as typed. In the Turkish words (`countSpoken` and `nameRoundSpoken` from `values-tr`), the state never holds the drawn "/ " or "TUR".
  - `testTheSetsRingHasNotchesAt33And66`, `testNotchesShowOnceTheArcPassesThem`
  - `testTheTransliterationShowsOnlyUnderALatinInterface`
  - `testHisOwnPhrasesFollowTheBuiltIns`, `testADeletedPhraseFallsBackToAfterPrayer`
  - `testCountsAreKeptPerPhrase`, `testTheStoreIsExcludedFromBackup`
  - `testTheSpokenStateIsCountOfTarget`: Astaghfirullah at 64 gives "64 of 100", and "٦٤ من ١٠٠" in ar-SA; the drawn `ofLabel` stays "of 100".
  - UI test `testATapCountsAndTheValueIsSpoken`: on `-taqwaPage tasbeeh`, the counter's value goes from "0 of 100" to "1 of 100".
  - UI tests in `TasbeehCrownTests`, with `XCUIDevice.shared.rotateDigitalCrown(delta:velocity:)` and the constant `crownDetent` (Step 4):
    - `testASlowTurnCountsAndATurnBackUndoes`: from 0, `delta: 2 * crownDetent, velocity: .slow` gives the value "2 of 100"; `delta: -crownDetent` gives "1 of 100".
    - `testAFastSpinFrom99OpensRoundTwo`: with `-taqwaTasbeehCount 99`, `delta: 3 * crownDetent, velocity: .fast` shows "ROUND 2" and the value "2 of 100", with the name "SubhanAllah, round 2".
    - `testTheSpokenValueFollowsEachStep`: with `-taqwaTasbeehCount 31`, three slow single detents read "32 of 100", "33 of 100. Next, Alhamdulillah" and "34 of 100", and one back reads "33 of 100. Next, Alhamdulillah" again.
- [ ] **Step 3:** Run `swift test` and `scripts/watch-build.sh test`. Expected: FAIL. Not committed.
- [ ] **Step 4: Implement the port, the crown counter, `TasbeehCounter`, `TasbeehFeel`, the store, the speech, the model and the views.** Then measure the crown on the SE 3: a throwaway UI test turns `rotateDigitalCrown(delta: 1.0, velocity: .slow)` from 0 and reads the count `n`, then the same at `.fast`. Set `crownDetent = 1.0 / n` in `TasbeehCrownTests.swift`, with `n` in a comment and in the ledger, and delete the throwaway test. If `.fast` gives more detents than `.slow` (the crown accelerating), `testAFastSpinFrom99OpensRoundTwo` asserts "ROUND 2" and a count of the fast detents − 1, and the ledger says why.
- [ ] **Step 5:** Run both again. Expected: PASS, the crown tests included. Then shoot Tasbeeh on the SE 3: Astaghfirullah at 64 (the mockup's), the after-prayer set at 40, and Arabic. Also shoot the list with a phrase of his own form, launched with `-taqwaPhrase "اللهم صل على محمد"` (Task 5), standing for one of his. Expected: each matches `aTasbeeh`, and the list shows the phrase after the seven built-ins.
- [ ] **Step 6: Commit** with the message "Apple Watch: tasbeeh as the phone counts and says it, the crown counting up and taking back, one haptic a turn".

### Task 9: Qibla with the watch's compass

**Files:**
- Create:
  - `watchcore/src/androidHostTest/kotlin/world/taqwa/app/qibla/CompassVectorsTest.kt` (plan 3's Task 2 moved the compass rules into `:watchcore`);
  - `watchcore/vectors/compass-vectors.json`;
  - `iosApp/WatchCore/Sources/WatchCore/Compass.swift` (the moved rules), `WatchQibla.swift` (the dial and the guidance), `QiblaScreen.swift`, and their tests (`CompassVectorsTests.swift`, `WatchQiblaTests.swift`, `QiblaScreenTests.swift`);
  - `iosApp/TaqwaWatch/HeadingSource.swift`, `QiblaView.swift`;
  - `iosApp/TaqwaWatchUITests/QiblaTests.swift`.
- Modify: `TaqwaWatchApp.swift`, `tools/add-watch-targets.rb`, re-run.

**Interfaces:**
- Consumes plan 3's Task 2 in `:watchcore` (`QiblaAngles`, `TrueNorth`, `CompassAccuracyRules`, `HeadingFilter`, `CompassAccuracyGate`, `CompassReading`, `Turn`, `Guidance`, `QiblaGuidance`, `QiblaGuidanceTracker`, `DialState`, `DialStep`, `WatchQibla`), and plan 3's Task 7 `qiblaScreen`, whose `QiblaModelTest` cases `QiblaScreenTests` restates without the tilt: the Apple Watch has no Core Motion, so it never says "Hold your wrist level".
- Produces `compass-vectors.json`, every answer from the Kotlin code:
  - `angleDelta` pairs, across the seam;
  - `isAligned` at 4.99, 5.0 and 5.01, and Dev 1's B40 `isAligned(_, _, wasAligned)` at 6.99, 7.0 and 7.01 off with `wasAligned` true and at 5.01 with it false;
  - `TrueNorth.fromMagneticOrInvalid` with a negative heading and with no declination;
  - `iosSampleIsLow` for (true heading, accuracy) (23, 45.0), (23, 45.01), (23, −1) and (−1, 10);
  - `HeadingFilter` sequences (0.15 smoothing; 359 → 1);
  - `CompassAccuracyGate` runs of `{t, low, reason}` and the state after each: Low after 1,000 ms, Good after 1,500 ms, BestEffort after 20,000 ms of one run, BestEffort's only exit, the reason's window, and a timestamp that goes back;
  - `guidanceOf`: round 2's `QiblaGuidance.of` cases (28, −44, 6, 179.9, −179.9);
  - `guidanceTracker`: `QiblaGuidanceTest`'s tracker inputs, from `update(0, 31.0)` to `update(3_300, 33.0)`, and null then non-null, with the tracker's answers;
  - `dial`: `WatchQibla` runs, each step `{reading: {heading, t, low, reason?}, state: {kind, heading?, turn?, degrees?, reason?}, buzz}`. The runs are `WatchQiblaTest`'s seven (bearing 23.35, good readings at 50 ms steps: aligned three times, leaving and coming back, held to 7° once aligned, a steady 340, a second of INTERFERENCE, twenty seconds low then good, and a reset) and `WatchDialParityTest`'s scripted run (good, aligned twice, 1.2 s low, 21 s low, good again), on bearing 23.0 as the Cape Town sample has it.
- Produces in Swift, ported: `enum QiblaAngles { static let alignedEnter = 5.0; static let alignedExit = 7.0; angleDelta; isAligned(_:_:threshold: = alignedEnter); isAligned(_:_:wasAligned:) }`, `enum TrueNorth`, `enum CompassAccuracyRules` (the iOS rules, `iosMaxHeadingError = 45`), `final class HeadingFilter(smoothing: 0.15)`, `enum CompassLowReason`, `enum CompassAccuracyState`, `final class CompassAccuracyGate(enterLowAfter: 1_000, recoverAfter: 1_500, bestEffortAfter: 20_000, reasonWindow: 1_000)` (milliseconds), `struct CompassReading { trueHeadingDegrees: Double; timestampMillis: Int64; isLowAccuracy: Bool; lowReason: CompassLowReason? }`;
  - `enum Turn { case LEFT, RIGHT }`, `struct Guidance: Equatable { turn: Turn; degrees: Int }`, `enum QiblaGuidance { static let step = 15; static func of(_ delta: Double) -> Guidance }`, `final class QiblaGuidanceTracker { func update(nowMillis: Int64, delta: Double?) -> Guidance?; func reset() }`;
  - `enum DialState: Equatable { case noSensor, searching(heading: Double, guidance: Guidance?), aligned(heading: Double), low(CompassLowReason), bestEffort(CompassLowReason) }`, `struct DialStep: Equatable { state: DialState; buzz: Bool }`, and `final class WatchQibla { init(bearing: Double, filter: HeadingFilter = .init(), gate: CompassAccuracyGate = .init(), guidance: QiblaGuidanceTracker = .init()); func reset(); func onReading(_ reading: CompassReading) -> DialStep }`: `QiblaViewModel.collectWhileActive`'s order, as plan 3 restates it (the gate first, then the filter on every sample; BestEffort or Low clears the alignment; Good is Aligned within 5°, and held to 7° once aligned (`isAligned(_:_:wasAligned:)`, Dev 1's B40), with `buzz` only on entry, and Searching otherwise, with the tracker's guidance; every other state gives the tracker nil).
- Produces:
  - `struct HeadingSample { magnetic: Double; accuracy: Double; millis: Int64 }` and `enum HeadingReading { static func reading(_ sample: HeadingSample, declination: Double?) -> CompassReading }`: the iPhone's `IosCompassSource` rule (`CompassSource.ios.kt:75-93`): the heading is `TrueNorth.fromMagneticOrInvalid(magnetic, declination)`, low when `iosSampleIsLow(heading, accuracy)`, reason CALIBRATION;
  - `struct QiblaDialModel: Equatable { dialRotation: Double; marker: Bool; needle: Bool; dimmed: Bool; lit: Bool; caption: String; captionIsAccent: Bool; detail: String; spoken: String }` and `enum QiblaScreen { static func model(_ feed: WatchFeed, _ state: DialState?) -> QiblaDialModel }`, plan 3's `qiblaScreen` without the tilt. `bearing` is `words.bearing` filled with `place.qiblaBearing`'s whole degree in the feed's digits; `distance` is `words.distance` filled with `place.qiblaKm` as the feed writes it:
    - **nil, before the first reading:** dimmed, no needle and no marker; the caption `bearing`, the detail `distance`, spoken `"\(bearing), \(distance)"`;
    - **Searching:** needle and marker; the caption `bearing`; the detail `distance`; spoken the guidance's `turnRight` or `turnLeft` phrase (`WatchRules.counted(feed, words.turnRight, degrees)`), or the caption when the guidance is nil;
    - **Aligned:** `lit`, needle and marker; the caption `facingQibla` in the accent, which is also spoken;
    - **Low:** dimmed, with no needle and no marker (the phone's B41 rule); the caption `needsCalibrating` or `interference`, spoken;
    - **BestEffort and NoSensor:** dimmed, no needle or marker; the caption `bestEffort` or `noSensor`, spoken; the detail `qiblaDetail(bearing, km)`;
    - `dialRotation` is −heading where the state has one, else 0.
  - `struct QiblaStep: Equatable { model: QiblaDialModel; buzz: Bool }` and `final class QiblaTracker { init(feed: WatchFeed); func sample(_ s: HeadingSample) -> QiblaStep; func reset() }`: `HeadingReading.reading(s, declination: place.declination)` into `WatchQibla(bearing: place.qiblaBearing)`, drawn by `QiblaScreen.model`.
- Produces in the app:
  - `protocol HeadingSource { var available: Bool { get }; func start(_ onSample: @escaping (HeadingSample) -> Void); func stop() }`. With `available` false the view draws `QiblaScreen.model(feed, .noSensor)` and starts nothing;
  - `CoreLocationHeading`: `CLLocationManager.headingAvailable()`, `headingFilter = kCLHeadingFilterNone`, `locationManagerShouldDisplayHeadingCalibration` true, and the monotonic clock. It is started only while the Qibla page is visible and the scene active, never asks for any authorization, and logs `authorizationStatus` once in DEBUG;
  - in DEBUG, `ScriptedHeading`, from `-taqwaHeading <deg>`, `-taqwaHeadingAccuracy <deg>` and `-taqwaNoCompass 1`, a steady sample every 50 ms. **`-taqwaHeading` is the magnetic heading,** what Core Location's `magneticHeading` would give: it goes into `HeadingSample.magnetic` as it is, and the tracker adds the feed's declination, as it does for the real compass. On the Cape Town sample, after plan 2's Task 3, the declination is −25.0 and the bearing 23.0. So facing the Qibla is `-taqwaHeading 48`, which is true 23, and `-taqwaHeading 23` is true 358, 25° off. Never drop or zero the declination to make a test pass: Review Focus 5 and `testTheDeclinationTurnsMagneticIntoTrue` pin it.
- The view: `qiblaDial` from `aQibla` (the Apple keeps the drawn dial, spec 2.5), with `words.cardinals`, turning by `dialRotation` and never mirrored, with the Kaaba marker at the bearing; then the caption, size 14 and weight 800, and the detail, size 10, in mist. Each `buzz` plays the Lead's call 7's `.success`.
- **VoiceOver:** one element, labelled `spoken`, with `detail` as its value. While VoiceOver runs, a change of `spoken` posts `AccessibilityNotification.Announcement(spoken)`, so a wearer searching hears each new turn (the tracker already holds a guidance for 1.5 s and speaks only a real change), and then "Facing qibla".

- **As built (31 October, Lane A):**
  - **The vectors** (`compass-vectors.json`: 20 `angleDelta` pairs, 32 `isAligned` cases, 14 `trueNorth` and 5 `correct`, 8 `iosSampleIsLow`, 10 filter runs, 18 gate runs, 17 `guidanceOf`, 10 tracker sequences and 19 dial runs, 2,073 steps in all) carry every case of `WatchQiblaTest`, `QiblaGuidanceTest` and `CompassAccuracyGateTest`, and `WatchDialParityTest`'s scripted run on 23.0. Ruling 58 item 4 (erratum p4 #11) is generated, never hand-written: the interval from the last telling, a non-finite delta (NaN and both infinities, written "NaN", "Infinity" and "-Infinity"), the left margin as the right, and the searching spells on a boot clock from 10,000 ms, which the Kotlin test `theSearchingSpellsRunOnABootClockFarFromNought` holds in the file.
  - **The declination is −27.0 now, not −25.0** (the Cape Town samples since plan 2's rounding, as the Galaxy's as-built note found), so facing the Qibla is `-taqwaHeading 50`, true 340 is 7, true 120 is 147, and true 358 is 25: `testTheDeclinationTurnsMagneticIntoTrue` reads magnetic 50 as aligned and 25 as "Turn right about 30 degrees", and `testTheSampleIsCapeTownAsTheUITestsTakeIt` pins the three figures the UI tests use. Nothing drops or zeroes the declination.
  - **A sample with no direction never points** (`QiblaScreen.drawn`, beyond this text, for Review Focus 5). The iPhone's rule sends Core Location's sentinel (a negative heading, or no declination) as 0 and low, and the gate trusts a low sample for its first second, so the sentinel would draw a needle at true north for up to a second, and could light the rim and buzz where the Qibla is within 5° of north. Such a sample is drawn as Ruling 58 item 1's searching dial with no heading and never buzzes; the dial still sees it, so the gate and the filter keep the phone's behaviour.
  - **`QiblaDialModel`** also carries `bearing` (the marker stands at `bearing + dialRotation`), `spokenDetail` (VoiceOver's value, the detail as it is heard), `cardinals` (the feed's letters while the dial points, none at rest, as the Galaxy and the phone's dimmed dial), `language` and `latinScript`. Before the first reading the label already says the distance ("23°, Makkah · 6,600 km away", the feed's comma in its direction), so the value is empty there rather than said twice. Low shows no readout, as the phone and the Galaxy (`qiblaScreen`) do. The bearing is the feed's whole degree as Kotlin's `toInt()` takes it (`wholeDegree`, toward zero, never a trap).
  - **The layout** is `aQibla` in its 172 × 212 units fitted to the screen both ways and centred across (`QiblaLayout`): the SE 3 and the Series 12 are both shorter for their width than the drawing, so both are scaled by their height, and the readout stands under the dial and above the page dots on each (`testTheReadoutStandsClearOfThePageDots`, from Task 8's measured dots, and the UI test). A bad state's title too wide for one line takes two (`QiblaCaption`: broken at the space nearest its middle, 16/15 of its size apart, the Galaxy's pitch), growing upward from y 180 so the detail keeps y 196. The caption grows with the text size to 1.2, the detail and the letters to 1.3 (plan 3's call 15), from the watch's own default; a two-line title keeps its drawn size, so it stays under the dial.
  - **The compass runs** only while the pager shows the page and the scene is active (`QiblaSession`); each start resets the dial, and while the compass is off, and from each start until its first heading, the page draws the resting dial (review round 1, below). On the SE 3, `CLLocationManager.headingAvailable()` is false, so a launch without the scripted compass shows "No compass sensor"; the debug log read `authorizationStatus 0` (`notDetermined`) and no prompt was drawn.
  - **`-taqwaHeadingAccuracy` alone** scripts magnetic 0 at that accuracy.
  - **Ruling 66 item 8 (erratum p4 #17):** taken after `[Lead/int R66]` (`git merge --ff-only watch-companion` to `5761178a`, the work so far carried over as a patch, no file in common). `QiblaDialModel.detail` draws `distanceOnDial` ("6,600 km") on a dial that turns and before the first reading, and `qiblaDetailOnDial` ("23° · 6,600 km") on one that has given up; `spokenDetail`, the VoiceOver value, keeps the long `distance` and `qiblaDetail`, and the label before the first reading keeps "23°, Makkah · 6,600 km away". One line, `.fixedSize(horizontal: true, vertical: false)`, from 10 grown by the text size to 1.3 times and never below, inside the screen (the UI test's 2 points either side). `WatchScreenWords`' two fields are R66's own. `testTheDialDrawsTheShortFormAndSaysTheLongOne` holds it on the four samples and the five goldens.
  - **Review round 1 (31 October, Lane A):**
    - **The page rests while the compass is off.** The first build kept its last drawing after a stop. A lowered wrist on an Always On watch leaves the app frontmost, dimmed and inactive (the Ruling 64 report, item 4), so a wearer who had faced the Qibla kept a lit rim, the needle and "Facing qibla", drawn and heard, while turning away. The page's life is now a pure `QiblaCompass` in WatchCore (`QiblaScreen.swift`), which `QiblaSession` drives: a stop, a start and a new feed draw `QiblaScreen.model(feed, nil)` (no needle, marker or lit rim, the bearing over the distance), as the Galaxy draws `NO_HEADING_YET` on every start. Only a heading is announced, and only when it changes the label, so the resting page is never announced over another page. A raise shows the resting dial until Core Location's first heading. `QiblaCompassTests` holds it (seven of its twelve tests were red on the first build's behaviour). XCUITest cannot read a page swiped off the screen, and the simulator has no wrist, so plan 5's check 5 sees it on her watch.
    - **A crown on the left.** Core Location's heading is the watch's own top "regardless of device orientation as well as the orientation of the user interface" (`CLHeading.h`), and watchOS turns the screen half round for a crown on the left. Each heading is now turned to the screen's top by `HeadingSample.onScreen(crownOnLeft:)` (+180°; Core Location's negative sentinel and a heading that is not finite kept as they are), with `WKInterfaceDevice.current().crownOrientation` read at each heading, as Tasbeeh reads it for its arrows. `headingOrientation`, which the iPhone sets, is deprecated in watchOS 27, so the turn is WatchCore's, under `swift test`. The sources hand on Core Location's own value, so `-taqwaHeading` still goes into `HeadingSample.magnetic` as it is, and on the simulator (crown on the right) nothing changes. Plan 5's check 13 now runs with the crown on each side.

- [ ] **Step 1: Write the Kotlin writer** (`compassVectorsAreUpToDate`), generate the file, and run without the flag. Expected: PASS.
- [ ] **Step 2: Write the failing Swift tests.**
  - `testEveryCompassCaseMatchesTheVectors` (the angles, alignment, true north, the iOS lowness, the filter and the gate)
  - `testEveryGuidanceCaseMatchesTheVectors` (`of` and the tracker)
  - `testEveryDialRunMatchesTheVectors`: at each step the state's kind, heading (within 1e-9), turn, degrees and reason, and `buzz`
  - `testANegativeHeadingNeverDrawsANeedle`, `testNoDeclinationNeverDrawsANeedle` (`HeadingReading` with a nil declination)
  - `testTheSeamDoesNotSwingTheNeedle`
  - `testFacingWithinFiveDegrees`
  - `testTheDeclinationTurnsMagneticIntoTrue`: on the Cape Town sample (declination −25.0, bearing 23.0), after the filter settles, magnetic 48 is aligned, with `facingQibla` in the accent, and magnetic 23 (true 358) is Searching, with the caption "23°" and spoken "Turn right about 30 degrees".
  - `testSearchingSaysWhichWayToTurn`: a steady magnetic 5 (true 340) is Searching, with the caption "23°", the detail "Makkah · 6,600 km away" and spoken "Turn right about 45 degrees"; magnetic 145 (true 120) is spoken "Turn left about 90 degrees".
  - `testTheBuzzComesOnceOnEnteringAlignment`
  - `testALowCompassDimsAndDropsTheMarker`: "Compass needs calibrating", dimmed, no needle or marker; `testTwentySecondsLowIsBestEffort`: "Compass unavailable" and "23° · 6,600 km to Makkah".
  - `testNoCompassShowsTheBearingAndDistanceOnly`: `.noSensor` gives "No compass sensor" and "23° · 6,600 km to Makkah", with no needle.
  - `testBeforeTheFirstHeadingNoNeedleIsDrawn`.
  - `testTheDistanceIsTheFeedsOwnFigure`: "Makkah · 6,600 km away" on the sample, the figure plan 2's Task 3 rounds under Ruling 39 (the phone's own 6,558 is never sent), with the ar-SA sample's "٦٬٦٠٠" as the feed writes it, and the golden's figure on `phone-capetown-en.json`. The watch never rounds or regroups the string.
  - `testArabicCaptionsAndAnUnmirroredDial`: ar-SA's caption is `bearing` in Arabic-Indic digits, its cardinals the feed's, and its `dialRotation` the English one's.
  - UI tests: `testFacingTheQiblaIsSpoken` (`-taqwaHeading 48`: label "Facing qibla", value "Makkah · 6,600 km away"), `testSearchingSpeaksTheTurn` (`-taqwaHeading 5`: label "Turn right about 45 degrees"), `testNoCompassSpeaksTheBearing` (`-taqwaNoCompass 1`: label "No compass sensor", value "23° · 6,600 km to Makkah"). Each waits up to 5 s for the gate.
- [ ] **Step 3:** Run `swift test` and `scripts/watch-build.sh test`. Expected: FAIL. Not committed.
- [ ] **Step 4: Implement the port, the tracker, the sources and the view.**
- [ ] **Step 5:** Run both again. Expected: PASS. Then shoot Qibla on the SE 3 in English and Arabic: facing (`-taqwaHeading 48`, true 23), at true 120 (`-taqwaHeading 145`), low (`-taqwaHeadingAccuracy 60`), and with no compass. The dial in each is `qiblaDial` drawn at the true heading. Expected: each matches `aQibla`. Check the debug log: `authorizationStatus` stays `notDetermined` and no prompt appears. The simulator has no magnetometer, so the real heading is plan 5's.
- [ ] **Step 6: Commit** with the message "Apple Watch: the Qibla dial on the watch's own compass, true north from the phone's declination, the turn said aloud, with no location asked".

### Task 10: "Add to Apple Watch"

**Starts after plan 2's Task 8** (`installWatchSession()` in `iOSApp.swift`), and never beside Task 5B, which edits the same file.

**Files:**
- Create:
  - `iosApp/iosApp/WatchFaceShare.swift`;
  - `shared/src/commonMain/kotlin/world/taqwa/app/watchface/WatchFaceOffer.kt`, `shared/src/androidMain/kotlin/world/taqwa/app/watchface/WatchFaceOffer.android.kt`, `shared/src/iosMain/kotlin/world/taqwa/app/watchface/WatchFaceOffer.ios.kt`;
  - `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/WatchRows.kt`;
  - `shared/src/iosTest/kotlin/world/taqwa/app/watchface/IosWatchFaceOfferTest.kt`;
  - `iosApp/iosApp/WatchFace/README.md` (how the file is made, and that it is absent until then).
- Modify:
  - `SettingsRootScreen.kt`: one `WatchFaceRow()` in the App group, after Language;
  - `composeResources/values{,-ar,-fr,-tr,-id,-in,-ur,-bn}/strings.xml`;
  - `iosApp/iosApp/iOSApp.swift`: install the hook in `init`, beside `installWatchSession()`;
  - `tools/add-watch-targets.rb`, re-run.

**Interfaces:**
- Produces in Kotlin:
  - `enum class WatchFaceResult { ADDED, FAILED }`, `interface WatchFaceOffer { fun available(): Boolean; fun add(onDone: (WatchFaceResult) -> Unit) }` and `expect fun createWatchFaceOffer(): WatchFaceOffer`;
  - the Android actual is never available;
  - in iosMain, the public `interface WatchFaceShareHook { fun isAvailable(): Boolean; fun add(done: (Boolean) -> Unit) }` and `object WatchFaceShareBridge { var hook: WatchFaceShareHook? }`, in Booleans only.
- Produces the strings, apostrophes as the Compose rule and `tools/i18n-check.py` want them:
  - `settings_add_to_apple_watch`: "Add to Apple Watch";
  - `settings_add_to_apple_watch_failed`: "The face could not be added. Try again from the Watch app.";
  - drafts in fr, tr, id (and `in`), ur and bn; ar "أضِف إلى Apple Watch".
- Produces in Swift `final class WatchFaceShare: NSObject, WatchFaceShareHook`, with `static let shared`:
  - `isAvailable()`: the session is activated and `isPaired`, and `Bundle.main.url(forResource: "Taqwa", withExtension: "watchface")` exists;
  - `add(done:)`: `CLKWatchFaceLibrary().addWatchFace(at:)`, answering `error == nil`, with the error's code logged as `TaqwaWatch face`.
- The row is `LinkRow(…, forward = true)`, shown while `available()` holds. A failure shows `settings_add_to_apple_watch_failed` as a `SettingsNote` under it until the screen is left.
- **As built (31 October, Lane A; report `docs/superpowers/reports/2026-10-31-watchos-add-to-apple-watch.md`):**
  - `WatchFaceRow(offer: WatchFaceOffer = remember { createWatchFaceOffer() })` asks `available()` each time the app comes to the front (`repeatOnLifecycle(STARTED)`), never in composition, and draws nothing while it is false. Shown, it brings its own `CardDivider`, since it sits in the App card after Language. A row that goes takes its failure line with it.
  - `forward` is `LocalLayoutDirection.current == LayoutDirection.Ltr`, as About's link rows give it (`drawExternalLink`'s rule: the arrow leaves toward the trailing edge). A literal `true` would point the arrow to the leading edge in Arabic and Urdu.
  - The failure line is a `SettingsNote` inside the card under the row, at `gutter = 12.dp` (level with the row's label) and 12 dp above the card's edge, in a polite live region that merges the line's words into one node (`semantics(mergeDescendants = true)`): CMP 1.12.1's iOS mediator announces a live region only from that node's own label, and a box that merges nothing has none, so VoiceOver would say nothing (review round 1). It stays while the sheet is asked again, and goes when a face is added, the row goes, or the screen is left (plain `remember`).
  - `createWatchFaceOffer()` on iOS answers no offer for no hook, a hook that throws or one that cannot tell, fails an add it cannot ask at once, and passes on only ClockKit's first answer. Android's never offers and fails an add at once.
  - `WatchFaceShare.isAvailable()` does not need `isWatchAppInstalled`: the add-face sheet offers the apps a face's complications need. It logs `TaqwaWatch face offered=<Bool> activated= paired= bundled=`, and `add` logs `TaqwaWatch face added` or `TaqwaWatch face refused: <ClockKit's name> code=<n> domain=<d>`. ClockKit's header promises the completion on the main thread; the answer hops there anyway if it ever is not.
  - Beyond Step 1's four: `WatchFaceRowTest` (7, the row's Compose tests on the iOS simulator, `aRefusedFaceIsSpokenWithItsWordsAsItComes` and `aRowThatGoesAndComesBackBringsNoOldLine` among them) and `AndroidWatchFaceOfferTest` (1).
  - The strings sit after `settings_language` in all eight folders (added, nothing reordered). Arabic's row is the plan's; every other non-English string carries the draft marker.
  - `tools/add-watch-targets.rb` names `WatchFaceShare.swift` in iosApp's sources (`FACE_FILES`), beside the harness's two.
  - Step 5: no file, no row; a placeholder, the row, and a tap answered `invalidFile` with the line under the row, in English and Arabic. **The simulators cannot make the real file:** the Series 12's face switcher offers Edit and no Share, and the iOS 27.0 simulator's Watch app aborts at launch (an uncaught NSException). So the file, and the check that a tap brings up the add-face sheet (spec 4.5's "to verify"), are plan 5's (question 3).
  - **Not Apple's documented form, and open for the Lead** (review round 1): Apple's "Sharing an Apple Watch face" asks for the face's preview beside an official Add Apple Watch Face button, and for faces to be added only from that button. The row is a text `LinkRow` with neither. It stays hidden until the file lands, so nothing ships either way before the Lead rules (the report's "For the Lead and plan 5", and `iosApp/iosApp/WatchFace/README.md`).

- [ ] **Step 1: Write the failing tests:** `withoutAHookThereIsNoFace`, `theHookSaysWhenTheFaceIsOffered`, `aFaceTheLibraryRefusesIsFailed` and `anAddedFaceIsAdded`.
- [ ] **Step 2:** Run `./gradlew :shared:iosSimulatorArm64Test`. Expected: FAIL. Not committed.
- [ ] **Step 3: Implement the offer, the bridge, the row, the strings and the Swift class.**
- [ ] **Step 4:** Run `scripts/test.sh`, `python3 tools/i18n-check.py` and `scripts/ios-build.sh`. Expected: PASS.
- [ ] **Step 5: On the paired simulators** (the pair's turn; paired, with both apps installed, if `xcrun simctl list pairs` shows no pair, then put back as found, Global Constraints):
  - With no file bundled, the row is absent.
  - With a placeholder copied into `WatchFace/` for this step only, the row shows, and a tap answers `invalidFile` with the failure line.
  - Delete the placeholder before the commit.
  - Then try to make the real file on the Series 12: a Modular face with Taqwa's rectangular in the middle and its circular at the bottom left, amber, shared from the face editor to the phone.
  - If the simulators can, bundle it, check that a tap brings up the Watch app's add-face sheet (spec 4.5's "to verify"), and shoot it. If they cannot, record that, and the file is plan 5's (question 3).
- [ ] **Step 6: Commit** with the message "iPhone: Add to Apple Watch, offered while a watch is paired and the Taqwa face is bundled".

### Task 11: the simulator pass, the languages, VoiceOver, and the as-built notes

**Files:**
- Create: `docs/superpowers/reports/2026-10-24-apple-watch-pass/README.md`, with the shots and their index.
- Modify: this plan, adding "Plan 4 as built" at the end.

- [ ] **Step 1: The language pass, end to end.**
  - On the paired simulators (paired, with both apps installed, if `xcrun simctl list pairs` shows no pair, then put back as found, Global Constraints), set the phone app's language to each of the seven in turn (the per-app language in the phone simulator's Settings), with 12 and 24 hours and with Arabic-Indic digits for ar-SA.
  - Keep the watch's own language English, then set it to Arabic.
  - Shoot Today, Tasbeeh, Qibla (scripted heading) and the gallery each time.
  - Expected: each watch surface speaks the phone's language, digits, clock and direction whatever the watch's own language: on 12 hours Today reads "4:10 PM" (the complications "4:10"), and on ar-SA Today's ring is in Western digits under Arabic-Indic times. The app's name in the system's lists and the gallery's entries follow the watch's.
- [ ] **Step 2: VoiceOver.**
  - Run every UI test with the phone samples in en, en12, ar-ly12 and ar-sa.
  - Expected: the reading orders of Tasks 7–9 hold, the names are said as the phone writes them, every spoken time carries the day period on a 12-hour feed, the tasbeeh says plan 3's phrases (never the drawn capitals or "of" label), Qibla says the turn while searching, and no line is said with the screen's abbreviations.
  - Hearing VoiceOver on the faces and screens is plan 5's ship gate on her watch (Ruling 38).
- [ ] **Step 3: Run every check:**
  - `swift test --package-path iosApp/WatchCore`, `scripts/test.sh`;
  - `scripts/ios-build.sh` and `scripts/watch-build.sh test`, with the bundle check;
  - `python3 tools/i18n-check.py`, `python3 tools/watch-art.py --check`;
  - the three Ruby scripts' byte-identical reruns, in both orders, with "Embed Foundation Extensions" still `iosApp`'s last phase;
  - CI's own commands: `./scripts/ios-build.sh build CODE_SIGNING_ALLOWED=NO` and `WATCH_DEST="generic/platform=watchOS Simulator" ./scripts/watch-build.sh build-for-testing CODE_SIGNING_ALLOWED=NO`.
- [ ] **Step 4: Write "Plan 4 as built"** in this plan, and the spec edits for the Lead:
  - 4.1: the floor, the ids, the app group;
  - 4.2: the store, the handshake, the background task held until the feed is stored, what Task 5B observed, and that the iPhone copy's Backups sentence stays;
  - 4.3: the timeline as built (an hour of minutes, the countdown-less last entry, the reload at the next start or the hour), Task 6 Step 7's observation, the relevance window, the Smart Stack header;
  - 4.4: the crown mapping and its measured detent, the haptics (one a turn), the Qibla's spoken turn, and heading without authorization on the simulator;
  - 4.5: the file, and whether `addWatchFace` took it.

  Then the hand-offs to plan 5 below.
- [ ] **Step 5: Commit** with the message "Apple Watch: the simulator pass in seven languages, with VoiceOver's reading order, and the notes for plan 5".

## For plan 5 (ship), from this plan

- **Release:**
  - `scripts/ios-release.sh` archives the watch app and its widgets. Its archive report checks the two watch bundles as `check-watch-bundle.sh` does, and adds the check this plan cannot make: rule 4b, the signed entitlements of each watch bundle (`codesign -d --entitlements - --xml`) are the app group alone.
  - Automatic signing registers `world.taqwa.ios.watchkitapp` and `.widgets` under team 3K93P4PA5H at the first archive.
  - App Store Connect gets the watch screenshots and the watch's privacy answers (Ruling 39's re-answer).
- **Her watch, through TestFlight on her iPhone (never the work iPhone 12).** Each item is a ship gate where marked:
  - VoiceOver on every surface, the faces included, the tasbeeh's phrases and the Qibla's spoken turn among them (Ruling 38, gate);
  - heading with no authorization prompt, and the needle against a real compass with the crown on each side (gate), and the Qibla page at rest with the wrist down;
  - **the complication's minutes with the app closed** (the Lead's call 5, gate): on her own face, for at least 10 minutes and across one start, the countdown moves each minute and rolls over at the start. If it does not, the Lead rules the countdown-less form;
  - the payload thresholds on the real pair (plan 2's gate), by the same harness with random bytes (`fill=random`, its default since Task 5B's review round 1: zeros compress to almost nothing and measure no limit, and their line ends `unmeasured`; userInfo's delivery to the watch app is open whatever the phone answers, since Task 5B logged no `no-answer` but no transfer reached the watch app on the simulators), and a city change reaching her complications without opening the app (the Lead's call 4);
  - the complications on her own faces, tinted and full colour, and the Smart Stack's rise before a prayer;
  - the crown's and the haptics' feel, and the two quiet windows;
  - the `.watchface` file made on her watch, bundled, and added through the row. It is shared by email to oneself, the one share that carries the face's preview with the file (Apple's "Sharing an Apple Watch face"), so the preview is in hand whichever way the Lead rules;
  - **before the file is bundled, the Lead's ruling on the row's form** (Task 10's review round 1): Apple asks for the face's preview beside an official Add Apple Watch Face button, and for faces to be added only from that button. Either the Settings text row ships as a recorded deviation, or the row becomes the preview and Apple's badge, the badge's tap calling `addWatchFace(at:)`.
- **Docs:** the README's and the site's watch lines name the Apple Watch.
- **The privacy policy's Backups paragraph** (Ruling 39, plan 2's question 7):
  - The watch's own files are excluded from backup (Task 5), so the paragraph needs nothing for the watch.
  - The iPhone's copy of the application context keeps question 7's added Backups sentence. Task 5B's report (`docs/superpowers/reports/2026-10-24-apple-watch-link.md`) gives the simulator's evidence, and its verdict is that the sentence stays. Plan 5 may drop it only on evidence from a real iPhone, which no agent can obtain, so in practice it ships.

## Plan 4 as built

Written by Task 11 (31 October 2026, Lane A). Each task's own as-built note above holds its detail; this is the whole, and the spec edits it asks of the Lead. The simulator pass's evidence is `docs/superpowers/reports/2026-10-24-apple-watch-pass/README.md`, Task 11's section included.

### Spec edits for the Lead (`docs/superpowers/specs/2026-10-19-taqwa-watch-companion-design.md`)

- **4.1 Target and distribution.** Replace "Minimum watchOS: the builder's call" with: watchOS 10.0 (question 1), SDKROOT `watchos`, `TARGETED_DEVICE_FAMILY` 4, Swift 5 mode, compiled against the watchOS 26 SDK on CI and 27 on the Mac, with no API newer than watchOS 26. The ids are `$(BUNDLE_ID).watchkitapp` and `$(BUNDLE_ID).watchkitapp.widgets` (`world.taqwa.ios.watchkitapp`, `.widgets`), embedded in `Taqwa.app/Watch/`. The app group is the iPhone's own, `group.world.taqwa.ios` (the Lead's call 3), the only entitlement of either watch bundle. `scripts/check-watch-bundle.sh` holds the bundle (ids, versions, no location, network, HealthKit, Core Motion or background modes, the sizes, no debug sample in a Release build) at every `scripts/ios-build.sh`. The widget extension is 4,380 to 4,420 KB of its 5,120 at Task 11 by build (the Debug, ad hoc signed builds of Task 11 and its reviews; CI's unsigned build 4,348), unchanged since MM3b's "4.4 of 5 MB" (Task 11 changed no watch source), and the app 12,000 to 12,044 of 15,360 KB (CI's 11,888).
- **4.2 Receiving the feed.** Add:
  - The store is `FeedStore`: `feed.json`, `feed.delivery`, the newer-version notice and the handshake stamp `refresh.asked`, in `Library/Application Support/TaqwaWatch` in the app group, a directory marked `isExcludedFromBackup` as it is made, each write atomic, the delivery removed before the feed is put in place and written after (`WatchLinkContract.STORE_STEPS`). `WatchLinkContract.stores` decides; a greater number for the feed held is stored without a reload.
  - The handshake echoes the delivery held, or "" with no feed. A `sendMessage` that fails takes its stamp back.
  - The activation and each received context are taken inside a WatchConnectivity background task (`backgroundTask(_:action:)`) held until the feed is stored and nothing is pending (`ConnectivityDrain`), and it ends at once when watchOS cancels it.
  - What Task 5B observed on the simulators: the first context reached the watch about 14 s after the phone queued it; a watch already holding the current delivery was answered UNCHANGED with no second send; `transferUserInfo` was answered on the phone but never handed to the watch app (plan 5's on her watch); WatchConnectivity compresses a payload before it checks the size, so the thresholds are measured with random bytes.
  - The iPhone's copy of the application context lies in the app's own container (`Library/Application Support/com.apple.watchconnectivity/…`), so the privacy policy's Backups sentence for it stays (Task 5B's verdict; plan 5 may drop it only on a real iPhone's evidence). The iPhone's `taqwa-watch-key` file beside it is now marked excluded from backup itself (Ruling 69 item 3, Task 11), and the watch's received copy is WatchConnectivity's, unmarked.
- **4.3 Complications and Smart Stack.** Replace "Timelines" with:
  - Each `getTimeline` gives one entry at each minute where the countdown changes, for the hour from now (the first is the glance that holds for now's minute: `make(now)`, or `make(now + 1)` on a whole minute before its start, Ruling 64 item 1), each start in the hour rolling to the next prayer, then 10 late entries at the next starts and the feed's turnovers (Ruling 64 item 2): 72 entries, 62 of them within the hour while every start is on a whole minute (Ruling 64 item 3). The last entry, at the feed's end, draws without a countdown. `reloadAt` is the earlier of the next start and an hour on.
  - Task 6 Step 7 (the pass README): with the extension idle for twenty minutes, the face drew each minute's countdown and rolled over at the start by itself; the reload came once per placed family, 51 to 53 s after the start `reloadAt` named (WidgetKit reads `.after(date)` as "no earlier than"). Seen with the screen lit only: the Always On cadence and an inactive face's next draw are plan 5's.
  - Relevance: an entry is relevant from 30 minutes before the next start to the start (`[nextAt − 1800, nextAt)`). Whether the Smart Stack lifts Taqwa by itself was not seen on the simulator (plan 5).
  - The Smart Stack card is the rectangular content under a header of the arch and the feed's `appName` in mist ("Taqwa", "تقوى", "তাকওয়া"), on the card's `#1A1D1C`.
  - Tinted faces: every accent shape is `.widgetAccentable()`. Task 11's fix round 1 saw it on the Series 12 in Arabic, on a face whose default colour is a tinted rendering: the accent shapes in the tint, the tracks in the face's grey, the bar filling from the right and the gauge anticlockwise (the pass README). Its first draw after the install drew the bar's track as bright as its fill, and a restart drew them apart, so plan 5's check on her faces says which a real watch shows. Another colour and the multicolour setting are chosen with the crown, which the simulator's control tool cannot turn: plan 5's too.
- **4.4 The app's screens.** Add:
  - Tasbeeh: `digitalCrownRotation` over the count with no wrap; a whole turn of the SE 3's crown is 8 detents at a slow turn (9 fast), so `crownDetent` is 1/8 of a turn, each whole detent one count up or one undo down; one haptic a turn (`TasbeehFeel`), never one per detent; a tap anywhere below the clock counts. VoiceOver hears plan 3's spoken phrases, with Count as the counter's default action (its hint), and Take one away then Choose a dhikr as its named actions (plan 4 errata p4 #1 and #6).
  - Qibla: heading from `startUpdatingHeading` alone, made true with the feed's declination, with no location authorisation asked and no `NSLocation*` key (the bundle check's rule 5). The simulator has no magnetometer (`headingAvailable()` is false, so it says "No compass sensor"), so heading without a prompt is plan 5's gate on her watch; the scripted compass (`-taqwaHeading`) stands in for the UI tests. While searching, VoiceOver hears the turn ("Turn right about 45 degrees"), and the label's distance is the long phrase, the dial's the short (Ruling 66).
- **4.5 "Add to Apple Watch".** Add: the row (`WatchFaceRow`) is shown only while a watch is paired and `Taqwa.watchface` is bundled, which it is not yet: the simulators cannot make the file (the Series 12's switcher offers no Share, and the iOS 27.0 simulator's Watch app aborts), so whether `addWatchFace(at:)` takes a real file is plan 5's on her watch. A placeholder file was refused with `invalidFile` (code 2), and the row said so, in English and Arabic. The row's form (a text row, or Apple's preview and badge) waits for the Lead's ruling.

### Task 11's findings for the Lead

- Errata p4 #5 (the speech language) holds as built: every spoken string is an `AttributedString` with the feed's `languageIdentifier` (`Speech.line`, `Glance`'s spoken lines), held in `swift test` by `GlanceTests` and `NextPrayerContentTests`. The simulator has no VoiceOver, and XCUITest sees the plain labels, so the UI tests hold what VoiceOver is given (the labels, values and order, 57 tests; `LanguagesTests` holds every reading in `en12`, `ar-ly12` and `ar-sa`, beside `capetown` in the other suites), not its voice or the language a line carries. Hearing it, in the feed's language on an English watch, is plan 5's gate.
- **Step 1 (the pass README):** 15 phone configurations (the seven languages at 24 and 12 hours, and ar-SA with Arabic-Indic digits) with the watch in English, then all 15 again with the watch in Arabic: 180 shots, each feed on the watch within 6 s. Every surface followed the phone, whatever the watch's language.
- **Step 1's faces and lists (fix round 1, the pass README):** on the Series 12 in Arabic, Taqwa was placed on an Apple face (its inline and a circular slot; the face's own rectangle showed Taqwa's card) and drew right to left and tinted; the system's lists named the app "تقوى" and its entry "الصلاة التالية", in the watch's language. Not seen, and plan 5's on her watch (P5.15): the List View's names (this simulator kept the grid with List View chosen), Modular's rectangular slot in Arabic, and the face's other colours and multicolour (the crown).
- **Step 2 (fix round 1):** every reading of Tasks 7 to 9 is held in the four samples, the Qibla's heard distance pinned to the long phrase in each (Ruling 66). The tests that measure places and sizes read `capetown` (two of them `ar-ly12` too): they hold no words.
- **For the Lead:** this simulator's Bengali phone (`bn_BD`) sends digit zero "0", so the watch draws Western digits on a Bengali phone, as that phone does; plan 3's call 9 assumed Bengali digits there. And a near-full Mac disk (97 %) made the watch's `appconduitd` refuse the phone's push of the watch app once, after which the phone answered `isWatchAppInstalled` false and sent nothing until the app was installed again: a simulator condition, but a real watch short of space could meet the same, and the phone then waits for the next install or capability change.
- **Ruling 69 item 3:** `WatchKeyFile` (`iosApp/iosApp/WatchKeyFile.swift`, held by 5 tests in `PhoneHarnessTests`, seen red first) marks the iPhone's `taqwa-watch-key` file excluded from backup itself on every read and write; on the simulator the file carries `com_apple_backup_excludeItem` and its folder none.

### Hand-offs to plan 5

The "For plan 5 (ship), from this plan" section above stands, with these from Task 11:
- P5.15 (her watch) adds: Taqwa on Apple's faces in Arabic and right to left on her own faces, Modular's rectangular among them, tinted in a colour of hers and in full colour, every accent shape read in one colour and each track apart from its fill; the List View's names in Arabic; VoiceOver hearing an Arabic feed on an English watch (erratum p4 #5).
- Plan 5's simulator checks on `ios-pair` install the debug iPhone Taqwa with its watch app from `scripts/ios-build.sh`'s signed build (CI's unsigned build gives the watch app no app group, and its complications then draw the empty form), as Task 11 did, and expect the watch app to be pushed again if the phone holds `isWatchAppInstalled` false (above).
