# Plan 4 Task 4: the watchOS targets, the bundle, the version and the watch's own words

Lane A, in `watch-apple` (plan 4's own worktree), 30 October 2026. Base `670471e1` ("Merge watch-apple: R57"), which is `watch-companion`'s head. Test-first: the bundle check was written and seen failing (rule 1) before any watch target existed.

**In one line:** the iPhone build now carries `Taqwa.app/Watch/TaqwaWatch.app` with `PlugIns/TaqwaWatchWidgets.appex`, made by `tools/add-watch-targets.rb`, at the iPhone's version, with no location, no network and no Kotlin, and with the watch's own words in seven languages. `scripts/check-watch-bundle.sh` holds all of that on every `scripts/ios-build.sh`. `scripts/watch-build.sh test` runs the first UI test on the SE 3 (40mm), and it passes.

## What was made

- `tools/add-watch-targets.rb` (xcodeproj 1.28.1, on Homebrew's Ruby 4.0.7). It makes three targets:
  - `TaqwaWatch`: a single-target SwiftUI watchOS app, `$(BUNDLE_ID).watchkitapp`, watchOS 10.0, `TARGETED_DEVICE_FAMILY` 4, Swift 5.0, automatic signing under `$(TEAM_ID)`;
  - `TaqwaWatchWidgets`: `$(BUNDLE_ID).watchkitapp.widgets`, embedded in the watch app's `PlugIns`;
  - `TaqwaWatchUITests`: `TEST_TARGET_NAME` TaqwaWatch.
  - Every build setting is written out in full on each run, so the list in the script is the review.
  - Both watch targets compile every Swift file in `iosApp/WatchCore/Sources/WatchCore/` and `iosApp/WatchShared/`. They bundle `WatchShared/WatchAssets.xcassets` and the three Manrope faces, which are referenced from `shared/src/commonMain/composeResources/font/` and never copied. Each target's folder adds its own Swift files, privacy manifest and seven `.lproj` tables.
  - Files are found on disk, so a later task's new file joins its target when the script runs again. A deleted file's reference is removed.
  - `iosApp` gains "Embed Watch Content" (`$(CONTENTS_FOLDER_PATH)/Watch`) and "Watch face". `TaqwaWatch` gains "Debug samples" (the four `sample-feed-capetown*.json` files, in Debug only) and its own "Embed Foundation Extensions".
  - The script writes the shared `TaqwaWatch.xcscheme`, whose test action holds the UI tests.
  - It writes both entitlements files from the iPhone's one app group (the Lead's call 3).
- `scripts/check-watch-bundle.sh <Taqwa.app>`: the plan's eight rules. `scripts/ios-build.sh` runs it after every `build` or `build-for-testing`.
- `scripts/watch-build.sh`: the `TaqwaWatch` scheme, built into `build/watch-dd` for `${WATCH_DEST:-…SE 3 (40mm),OS=27.0}`, with no `-sdk`, and with `"${@:-build}"`.
- `scripts/ios-build.sh` no longer has `-sdk iphonesimulator`. It guards the watch widget's size at 5 MB beside `TaqwaWidget.appex`'s 15 MB, and then runs the bundle check.
- `tools/watch-art.py [--check]`: `TaqwaArch.svg` from `ic_launcher_monochrome.xml`, the catalogs' `Contents.json`, and the watch's copy of the iPhone's 1024 px icon.
- `iosApp/TaqwaWatch/`: `TaqwaWatchApp.swift` (`@main`, the first-run line), `Info.plist`, `TaqwaWatch.entitlements`, `PrivacyInfo.xcprivacy`, `Assets.xcassets/AppIcon.appiconset`, and seven `.lproj` folders, each with `InfoPlist.strings` and `Localizable.strings`.
- `iosApp/TaqwaWatchWidgets/`: `TaqwaWatchWidgets.swift` (`@main WidgetBundle`, one placeholder widget of kind `TaqwaNextPrayer` that draws the empty form in the four accessory families), `Info.plist`, `TaqwaWatchWidgets.entitlements`, `PrivacyInfo.xcprivacy`, and seven `.lproj` folders.
- `iosApp/WatchShared/`: `OwnWords.swift` (the two lines and the app's name, from each target's own table), and `WatchAssets.xcassets/TaqwaArch.imageset`, a template image that keeps its vector data.
- `iosApp/TaqwaWatchUITests/LaunchTests.swift`: `testTheAppOpensOnTheFirstRunLine`.
- `scripts/version-files.sh`: `IOS_PLISTS` lists the two new plists. Plan 5's Task 1 had landed, so `bump-version.sh` and `check-versions.sh` cover them with no change of their own.
- `tools/i18n-check.py`: it checks the watch `.lproj` tables.
- `.github/workflows/ios.yml`: the paths, a "watchOS platform" step, the `Watch/TaqwaWatch.app` test, and the step "The watch app, its widgets and its UI tests compile".

## The steps and their evidence

1. **The tool.** `gem install --user-install --no-document xcodeproj` installed xcodeproj 1.28.1, with nanaimo 0.4.0, colored2 3.1.2, claide 1.1.0, CFPropertyList 3.0.8 and atomos 0.1.3. `ruby -e 'require "xcodeproj"; puts Xcodeproj::VERSION'` prints `1.28.1`. It loads on Ruby 4.0.7, so neither of call 2's fallbacks was needed. The version is in the script's header.
2. **`-sdk` dropped first.** `scripts/ios-build.sh` without `-sdk` gave BUILD SUCCEEDED on today's project, with `TaqwaWidget.appex: 3 MB (limit 15 MB)`. The iPhone simulator SDK was still chosen (`iPhoneSimulator27.1.sdk`).
3. **Red.** With `check-watch-bundle.sh` wired into `ios-build.sh`, the build succeeded and the check failed: `check-watch-bundle: rule 1: Taqwa.app has no Watch/TaqwaWatch.app with PlugIns/TaqwaWatchWidgets.appex inside` (exit 1). That state was not committed: the WIP `7be260f0` holds only the unwired check and the UI test, which nothing ran yet.
4. **The script, the art tool and the files** were written. `ruby tools/add-watch-targets.rb` and `python3 tools/watch-art.py` then ran.
5. **The project file:**
   - `iosApp`'s phases are `["Compile Kotlin", "Sources", "Resources", "Frameworks", "Embed Watch Content", "Watch face", "Embed Foundation Extensions"]`.
   - `TaqwaWatch`'s phases are `["Sources", "Frameworks", "Resources", "Debug samples", "Embed Foundation Extensions"]`.
   - The dependencies are iosApp → TaqwaWidget and TaqwaWatch, TaqwaWatch → TaqwaWatchWidgets, and TaqwaWatchUITests → TaqwaWatch.
   - `add-watch-targets.rb` run again leaves the project and the scheme byte-identical (shasum).
   - The three scripts were run in order (watch, widget, store), then in reverse (store, widget, watch). The project and the scheme stayed byte-identical after each run, and the phase list printed the same each time.
   - `xcodebuild -list` shows the targets iosApp, TaqwaWidget, TaqwaWatch, TaqwaWatchWidgets and TaqwaWatchUITests, and the schemes iosApp, TaqwaWatch, TaqwaWatchWidgets and TaqwaWidget. The iPhone's scheme is still auto-created beside the shared one.
   - **`git diff` beyond the new sections:** the first save through the gem changes six lines of `TaqwaWidget`'s two configurations, and none of them changes what the build does:
     - `isa` is written before `baseConfigurationReference` (two lines);
     - `"${TEAM_ID}"` becomes `"$(TEAM_ID)"` and `"${BUNDLE_ID}.widget"` becomes `"$(BUNDLE_ID).widget"` (four lines). Xcode expands both forms the same way.

     Why: `18ff282b` (22 September) edited the project by hand after `add-widget-target.rb` had already moved to the `$(…)` form, so the committed file was not the widget script's own output. The project is now the fixed point of all three scripts.
6. **Build and check:**
   - **The iPhone build.** `scripts/ios-build.sh` gives BUILD SUCCEEDED, with no Swift warning in any watch file. It prints `TaqwaWidget.appex: 3 MB (limit 15 MB)` and `TaqwaWatchWidgets.appex: 1 MB (limit 5 MB)`, then the bundle check's lines:
     - `rule 4b skipped: unsigned or ad hoc (TaqwaWatch.app TaqwaWatchWidgets.appex)`;
     - `rule 5 read TaqwaWatch.app: TaqwaWatch.debug.dylib __preview.dylib TaqwaWatch`;
     - `rule 5 read TaqwaWatchWidgets.appex: TaqwaWatchWidgets.debug.dylib __preview.dylib TaqwaWatchWidgets`;
     - `TaqwaWatchWidgets.appex 1632 KB (limit 5120 KB), TaqwaWatch.app 3220 KB (limit 15360 KB)`;
     - `rule 8 skipped: a Debug bundle (4 sample feeds in TaqwaWatch.app)`;
     - `every rule holds`.

     The canary was found in both bundles. `Taqwa.app/Watch/TaqwaWatch.app/PlugIns/TaqwaWatchWidgets.appex` exists under `Debug-iphonesimulator/`.
   - **The built bundles:**
     - the watch app's `Info.plist` reads `world.taqwa.ios.watchkitapp`, `MinimumOSVersion` 10.0, `UIDeviceFamily` [4], `WKApplication` true, `WKCompanionAppBundleIdentifier` `world.taqwa.ios` and `WKRunsIndependentlyOfCompanionApp` false, built against watchsimulator 27.0;
     - the widgets' `Info.plist` reads `world.taqwa.ios.watchkitapp.widgets`, with the `com.apple.widgetkit-extension` extension point;
     - all four bundles are 1.3.0 (39);
     - `assetutil` finds `TaqwaArch` as a template image with its vector rendition, and the `AppIcon` as a watch icon.
   - **The UI test.** `scripts/watch-build.sh test` gives TEST SUCCEEDED: `LaunchTests.testTheAppOpensOnTheFirstRunLine` passed (4.9 s, 1 test, 0 failures). It ran on the SE 3 (40mm) `FCA316A7` under the `sim-se3` lock. It ran a second time after the scheme edit below, and passed again (4.6 s).
   - **Shots of the first run.** The English shot shows "Open Taqwa on your phone". With `-AppleLanguages (ar)` it shows "افتح تقوى على هاتفك". Both are in the session scratchpad, not committed (`p44/se3-first-run.png`, `se3-first-run-ar.png`).
   - **The version.** `scripts/bump-version.sh 1.3.0 39` leaves `git diff` empty: the new plists already equal the phone's. `scripts/test-bump-version.sh` passes with "IOS_PLISTS lists 4", and its stale-code and stale-name cases now name `iosApp/TaqwaWatchWidgets/Info.plist`.
   - **The other checks:**
     - `python3 tools/i18n-check.py`: `i18n: clean`. The watch tables are 2, 1, 4 and 1 keys, each in seven languages;
     - `python3 tools/watch-art.py --check`: `watch-art: up to date`;
     - `swift test --package-path iosApp/WatchCore`: 19 tests, 0 failures;
     - `scripts/test.sh`: exit 0. Versions agree, the bump test passes, `swift test` runs first (19/0), and the Gradle suites are up to date, since no Gradle input changed.
7. **Rule 5 bites.** I added `import CoreLocation` and `init() { _ = CLLocationManager().requestLocation() }` to `TaqwaWatchApp.swift`. `scripts/ios-build.sh` then exits 1 with `check-watch-bundle: rule 5: TaqwaWatch.app/TaqwaWatch.debug.dylib names requestLocation`. After a revert (`git diff` on the file empty), the build passes. The same was done once more with `_ = URLSession.shared`: `rule 5: TaqwaWatch.app/TaqwaWatch.debug.dylib names NSURLSession`, then reverted, then passed.
8. **CI's builds, as CI runs them:**
   - `./scripts/ios-build.sh build CODE_SIGNING_ALLOWED=NO`: BUILD SUCCEEDED, and every rule holds. It prints `rule 4b skipped: unsigned or ad hoc`, and `Watch/TaqwaWatch.app` is present;
   - `WATCH_DEST="generic/platform=watchOS Simulator" ./scripts/watch-build.sh build-for-testing CODE_SIGNING_ALLOWED=NO`: TEST BUILD SUCCEEDED, with `TaqwaWatch_watchsimulator27.0-arm64-x86_64.xctestrun`. It was run again on the final tree.

   CI's own Xcode 26 run is the Lead's to watch after the push.

**Beyond the plan's two red steps, every rule was seen to fail.** A scratch harness (session scratchpad `p44/rules-bite.sh`, not committed) ran the check on mutated copies of the built app, and on a scratch root for the entitlement files. There were 17 cases, and each failed on the rule it targets:
- rule 1: no widgets, a wrong watch id, `WKRunsIndependentlyOfCompanionApp` YES;
- rule 2: a widget at code 40;
- rule 3: an `NSLocationWhenInUseUsageDescription` in the watch's `Info.plist`, the same key in a `fr.lproj/InfoPlist.strings`, and `UIBackgroundModes` in the widgets;
- rule 4a: a HealthKit key beside the group, and another group;
- rule 5: the canary renamed in all three of the watch app's Mach-O files, and a `kfun:` in the widgets' `.debug.dylib`;
- rule 6: no privacy manifest, no `bn.lproj`;
- rule 7: the widgets over 5 MB;
- rule 8: the Debug bundle under a `Release-iphonesimulator/` folder (it names the four samples).

The unchanged copy passes. A Release build of the `TaqwaWatch` scheme holds no sample and no `.debug.dylib`. Put in place of the Debug watch app, it passes rule 8, and its canary is found in the executable alone. Rule 4b's signed path is proven on real signatures in "Review round 1" below. The check on the archive's own signature is plan 5's.

## Review round 1: rule 4b could never run

**The finding (Important, the code lens).** `check-watch-bundle.sh:124` decided "signed" with `codesign -dv "$bundle" 2>&1 | grep -q '^Authority='`, under `set -uo pipefail`. It had two faults, and either alone was enough to make every really signed bundle print "rule 4b skipped: unsigned or ad hoc":
1. codesign prints the `Authority=` lines only from verbosity 2. Measured on `/System/Applications/Calculator.app` and `/Applications/Claude.app` (Developer ID): `-dv` prints no `Authority` line, and `-dvv` prints three.
2. Even with `-dvv`, `grep -q` exits at the first `Authority` line, and codesign, still writing the lines after it, dies of SIGPIPE. Under `pipefail` the condition is then false: `PIPESTATUS` `141 0`, and the signed path was taken 0 times in 200 runs on Claude.app (`/bin/bash` 3.2). Capturing the output first took it 200 times in 200.

**The fix.** Rule 4b now runs `signature=$(codesign -dvv "$bundle" 2>&1)` and greps the captured text. Two more changes go with it:
- A signature with no entitlements at all is read as an empty dictionary, so it fails with `com.apple.security.application-groups=null` instead of a Python traceback.
- Rule 5's two other `… | grep -q` tests read their tool's output whole first too: `file -b` and `otool -L`. Neither was seen to fail: a watch dylib's `otool -L` is about 4.6 KB, and a mutant linking `@rpath/shared.framework/shared` was caught 40 times in 40 by the old script as well. The change is defensive, so no tool's exit can turn a match into a miss. The Kotlin framework names are now listed once each, not once per architecture.

The header and plan 4's rule 4b text now say `-dvv`, and why.

**The evidence** (scratch harness in the session scratchpad, `p44fix1/`, not committed; every run on `/bin/bash` 3.2):
- **Real Apple signatures.** In a copy of the built `Taqwa.app`, `Watch/TaqwaWatch.app` was replaced by a copy of `Calculator.app`, and its `PlugIns/TaqwaWatchWidgets.appex` by FindMy's `FindMyWidgetItems.appex`. Both are signed by "macOS Software Signing".
  - `c3b38143` printed `rule 4b skipped: unsigned or ad hoc (TaqwaWatch.app TaqwaWatchWidgets.appex)`.
  - The fix took the signed path and failed rule 4b for both bundles, naming their real entitlements (`com.apple.security.app-sandbox`, `com.apple.chrono.open-urls-direct` and the rest).
- **A stub that replays real output.** A fake `codesign` early on `PATH` replays codesign's own `-dv` and `-dvv` output for Claude.app, captured verbatim, one line per write to stderr as codesign writes it. It prints a chosen entitlements plist for `--entitlements`.
  - `c3b38143` took the signed path 0 times in 100.
  - The fix took it 100 times in 100, and every rule held with the app group plus the signature's own keys (`application-identifier`, `com.apple.developer.team-identifier`, `get-task-allow`, `keychain-access-groups`).
  - The fix failed rule 4b, naming the key, on `com.apple.developer.healthkit`, on `com.apple.developer.location.push`, on another app group, and on no entitlements at all.
- **Each fault alone.** Two mutants of the fix, each with one fault put back, took the signed path 0 times in 30, both on the real signatures and on the stub: `-dv` with the output captured, and `-dvv` piped into `grep -q`. So both halves of the fix are needed.
- **The ad hoc and unsigned builds still skip.** `scripts/ios-build.sh` gives BUILD SUCCEEDED, and every rule holds, with `rule 4b skipped: unsigned or ad hoc (TaqwaWatch.app TaqwaWatchWidgets.appex)` and the same lines as step 6 (1632 KB and 3220 KB). `./scripts/ios-build.sh build CODE_SIGNING_ALLOWED=NO` gives the same.
- **The rules harness, re-run on the fix and on a fresh build.** Its 17 cases still fail on their own rules, the unchanged copy and the Release watch app pass, and five cases are new: a linked `shared.framework` (rule 5), and rule 4b through the stub (the group alone passes; HealthKit, another group and no entitlements fail; the real ad hoc signature skips). That is 23 of 23.

**For the Lead: an erratum for plan 5** (`docs/superpowers/plans/2026-10-24-watch-companion-5-ship.md`):
- **:728, `TAQWA_CODESIGN`:** "`-dv` for the `Authority`" should read "`-dvv` for the `Authority`". codesign prints `Authority` lines only from verbosity 2. The output must be read whole before it is searched, never `| grep -q` under `pipefail`, or codesign's SIGPIPE (141) reads as "unsigned". The key lists "by the signature's first `Authority` line" read that same `-dvv` text.
- **Step 2's stub** in `scripts/test-watch-bundle-signing.sh` must answer as codesign does, or it hides this fault. As written, it "prints a chosen `Authority` line" for each bundle, which a `-dv` read would also accept. It should print no `Authority` line at `-dv` and the lines only at `-dvv`, to stderr, one write per line, followed by the lines real codesign prints after them (`Timestamp`, `Info.plist entries`, `TeamIdentifier` and the rest), so that a piped `grep -q` dies of SIGPIPE as it does on a real signature. The simplest form replays captured real `-dv` and `-dvv` output with only the `Authority` text changed, as this round's harness did. One more case would hold it to a real signature: a copy of `/System/Applications/Calculator.app` in place of `Watch/TaqwaWatch.app` must not print "rule 4b skipped".
- **`beta-reports-active`.** Plan 5 adds it to rule 4b's allowed keys (`SIGNATURE_KEYS`, `check-watch-bundle.sh:53`), for the distribution `Authority` only. Now that rule 4b really runs, a distribution-signed export checked before plan 5's per-`Authority` lists land fails on that key. Plan 4's list stays as it is, because no plan 4 build carries a distribution signature.

## The errata and the rulings

**Plan 4 errata (key `p4`):**
- **#9** (every UI test sets its whole state by launch arguments) touches this task, and is honoured. `LaunchTests` passes `-taqwaNoFeed 1`, Task 5's argument, which this task's app ignores because it holds no feed. It also passes `-AppleLanguages (en) -AppleLocale en_US`, which pins the watch's own language to the table the test reads. `-taqwaFresh 1` is for the later tasks that have a tasbeeh store.
- **#4** (Task 10 also waits on Task 4) is met: the "Watch face" phase is in place, and Task 10 only runs the script again.
- **#5** (VoiceOver's speech language for feed text) does not apply to Task 4's words. They are the watch's own, in the watch's language, so VoiceOver's voice already matches them. The rule starts with Task 6's feed text.
- **#1, #2, #3, #6, #7, #8 and #10** are Tasks 1, 3 and 5–8's. None touches this task.

**The ledger's rulings:**
- **Ruling 39** (privacy; "Only your own devices can see your location."). Neither watch bundle has a location key, a location call, network code or an App Transport Security key, and rules 3 and 5 hold that on every build. The watch's `InfoPlist.strings` carries `CFBundleDisplayName` alone, never the phone's permission sentence. The promise lines themselves are plan 5's, and nothing here touches them. The scheme's "allow location simulation" is off too, since the gem defaults it on.
- **Rulings 41, 42 and 43** are about the Taqwa face (Wear OS), which the Apple Watch does not have, so nothing here changes the face. For Ruling 42's Apple side: the placeholder makes each complication family one element.
- **Rulings 55–57:**
  - The Swift port's files are compiled into both targets unchanged, so Ruling 56's contract is untouched (`swift test` 19/0).
  - In the spirit of Ruling 55 item 6, no test vector reaches a shipped watch build. The "Debug samples" phase copies only the four sample feeds, never `feed-vectors.json`, and only in Debug. Rule 8 holds a Release bundle to none.
- **The Lead's calls 2 and 3** are ruled at their defaults (ledger :713, :715). The gem is installed `--user-install`. The app group is `group.world.taqwa.ios`, read from `iosApp.entitlements`.

## Choices made while building, for the reviewers

1. **`TaqwaArch.svg`** applies the vector's group transform (a 0.78 scale about 512, 512) to the coordinates and the stroke width (68.64). So the SVG has no `transform` for Xcode's renderer to interpret, while keeping the plan's viewBox 0 0 1024 1024. `watch-art.py` refuses any vector attribute it does not apply. Quick Look renders the SVG as the arch with its dot.
2. **`watch-art.py` also owns** the two catalogs' `Contents.json` files and the watch's copy of the iPhone's icon. `--check` catches an iPhone icon changed without the watch's.
3. **Rule 5 also looks for `kfun:`.** A static Kotlin/Native framework never shows in `otool -L`. As a control, the iPhone's `TaqwaWidget.debug.dylib`, which links `:widgetcore` statically, has 2878 `kfun:` strings, and its stub executable has 0. That also shows why the executable alone proves nothing.
4. **Rule 3 also reads each watch bundle's `.lproj/InfoPlist.strings`,** since a localized table could carry a usage string too.
5. **Both privacy manifests declare SystemBootTime `35F9.1`:**
   - the watch app's for the heading's monotonic clock, as the plan says, and for its elapsed-time log lines;
   - the widgets' for Task 6's `TaqwaWatch timeline <entries> <ms>`, which this task's code does not use yet.

   Neither manifest declares a file-timestamp category, since a stamp keeps its time inside its file. If the reviewers prefer the widgets' manifest to declare nothing until Task 6, that is one deletion.
6. **The translations.** "Next prayer" is the phone's own `prayer_a11y_prayer_next` in each language, and the app's name is the iPhone's own `CFBundleDisplayName`. `i18n-check.py` holds both to the phone's text. The other three phrases (`open_taqwa_on_phone`, `update_taqwa_on_watch`, `next_prayer_widget_description`) are drafts, marked "draft, to native review (plan 5)" in the six languages other than English, Arabic included. They are drafted from the app's words: phone = هاتف, téléphone, telefon, ponsel, فون, ফোন; watch = ساعة, montre, saat, jam tangan, گھڑی, ঘড়ি (the privacy-wording report's); update from each language's system-update line.
7. **`i18n-check.py` reads `.strings` files in Python,** since CI's string check runs on Linux. Each watch table must:
   - parse;
   - carry English's keys and placeholders in all seven languages;
   - have no empty value.

   A mutation run found all four problems it was given: a missing key, an added `%@`, a changed app name, and a "Next prayer" that is not the phone's.
8. **The placeholder widget** uses kind `TaqwaNextPrayer`, the kind Task 6's widget keeps, with the gallery name and description from its own table. It draws the empty form as Task 6 words it. Its colours are inline until Task 6's `Style.swift`.
9. **`README.md` is not touched.** It is a shared file whose watch lines are plan 5's (P5.9, P5.10, P5.16). For the Lead: its iOS paragraph ("checks the widget extension stays under its memory budget") and the module tree's `iosApp/` line can name the watch app and `scripts/watch-build.sh` when plan 5 writes the watch docs.

## Devices

The only device used was the SE 3 (40mm) `FCA316A7`, under `~/.cache/taqwa-locks/sim-se3.lock`, three times: the first UI test, the re-run after the scheme edit, and a check of the put-back.
- **As found:** shut down, unpaired (`xcrun simctl list pairs` empty), 15 Apple apps, en-US.
- **As left:** the same. The watch app and its UI-test runner were uninstalled, `get_app_container` finds neither, and the simulator was shut down. Each lock was released.
- **One thing to know:** right after a boot, `simctl listapps` can briefly still list apps uninstalled before the last shutdown, from a stale cache. A few seconds later it lists the 15 Apple apps as found, so compare after the boot settles.
- **Not used:** the paired iPhone 17 Pro and Series 12 (this task has no `ios-pair` step), the iPhone 17 Pro Max on iOS 26.2 another session booted, `KB2604110095` and the work iPhone 12. No release key was used.
- **For the Lead:** `ios-build.sh`'s destination `name=iPhone 17 Pro` matches both the iOS 26.2 and the 27.0 simulator. A build boots neither, so this is only a note.

## Left open

- Rule 4b on the archive's and the export's own signatures is plan 5's, in `ios-release.sh`'s archive report. The signed path itself now runs, as proven above.
- CI's Xcode 26 run of the new steps is the Lead's to watch after the push. The "watchOS platform" step downloads only when the image lacks the platform.
