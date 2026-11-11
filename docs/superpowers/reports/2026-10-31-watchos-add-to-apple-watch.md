# Plan 4 Task 10: "Add to Apple Watch" (31 October 2026, Lane A)

Branch `watch-apple`, base `d3c73d98` (`git merge --ff-only watch-companion`: already up to date). Plan: `docs/superpowers/plans/2026-10-24-watch-companion-4-watchos.md`, Task 10. Spec 4.5 and 5.

## What was built

- **`shared/src/commonMain/kotlin/world/taqwa/app/watchface/WatchFaceOffer.kt`:** `enum class WatchFaceResult { ADDED, FAILED }`, `interface WatchFaceOffer { available(); add(onDone) }`, `expect fun createWatchFaceOffer()`.
- **`WatchFaceOffer.android.kt`:** never available; an add fails at once.
- **`WatchFaceOffer.ios.kt`:** the public `interface WatchFaceShareHook { isAvailable(); add(done: (Boolean) -> Unit) }` and `object WatchFaceShareBridge { var hook }`, in Booleans only. The actual answers no offer for no hook, a hook that throws or one that cannot tell. An add with no hook, or a hook that throws, fails at once. Only ClockKit's first answer reaches the row.
- **`shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/WatchRows.kt`:** `WatchFaceRow()`, the file plan 5 adds Android's rows to (the Lead's call 6).
  - It asks the offer each time the app comes to the front, and draws nothing while the offer does not hold.
  - Shown, it is a divider and `LinkRow("Add to Apple Watch")` in the App card after Language.
  - A refused face shows "The face could not be added. Try again from the Watch app." under the row, in a polite live region, until a face is added, the row goes or the screen is left. The live region merges the line's words into one node (`semantics(mergeDescendants = true)`, review round 1 below), so VoiceOver has words to announce.
- **`SettingsRootScreen.kt`:** one `WatchFaceRow()` in the App card, after Language.
- **The strings** `settings_add_to_apple_watch` and `settings_add_to_apple_watch_failed`, after `settings_language` in all eight Compose folders (added; nothing reordered). Arabic's row is the plan's "أضِف إلى Apple Watch". Every other non-English string carries `<!-- draft, to native review (plan 5 Task 11) -->`. `values-in` equals `values-id`.
- **`iosApp/iosApp/WatchFaceShare.swift`:** `final class WatchFaceShare: NSObject, WatchFaceShareHook` with `static let shared`.
  - `isAvailable()`: `WCSession` activated and `isPaired`, and `Bundle.main.url(forResource: "Taqwa", withExtension: "watchface")`.
  - `add(done:)`: `CLKWatchFaceLibrary().addWatchFace(at:)`, answering `error == nil` on the main thread.
  - Each decision is logged as `NSLog("%@", "TaqwaWatch face …")`, with no value in the format string and nothing about the place or the person.
- **`iOSApp.swift`:** `Self.installWatchFaceShare()` in `init`, right after `installWatchSession()`. It sets `WatchFaceShareBridge.shared.hook = WatchFaceShare.shared`, and never activates the session itself.
- **`tools/add-watch-targets.rb`:** `WatchFaceShare.swift` joins iosApp's sources (`FACE_FILES`, beside the harness's two). The re-run added one file reference and one build file. Two more runs left the project byte-identical, and so did running the three Ruby scripts (`add-watch-targets.rb`, `add-widget-target.rb`, `configure-store-targets.rb`) in both orders. iosApp's phases still end "Embed Watch Content", "Watch face", "Embed Foundation Extensions".
- **`iosApp/iosApp/WatchFace/README.md`:** how the file is made (Modular, Taqwa's rectangular in the middle and circular at the bottom left, amber, staged on Cape Town), that it is absent until then, how to test with a placeholder, and what the simulators could not do.

## Test first

- **Step 1's four,** in `shared/src/iosTest/kotlin/world/taqwa/app/watchface/IosWatchFaceOfferTest.kt`: `withoutAHookThereIsNoFace`, `theHookSaysWhenTheFaceIsOffered` (asked again each time, and a hook that throws offers nothing), `aFaceTheLibraryRefusesIsFailed` (a throwing hook too, answered once) and `anAddedFaceIsAdded` (a second answer ignored).
- **Beyond the plan:**
  - `AndroidWatchFaceOfferTest.anAndroidPhoneOffersNoAppleFace` (Android host);
  - `WatchFaceRowTest` (iOS simulator, Compose UI): `noFaceOfferedShowsNoRow`, `anOfferedFaceIsOneTapAway`, `aRefusedFaceIsSaidUnderTheRowUntilOneIsAdded`, `theOfferIsAskedAgainEachTimeTheAppComesToTheFront` and `theFailureLineGoesWhenTheScreenIsLeft`, and since review round 1 `aRefusedFaceIsSpokenWithItsWordsAsItComes` and `aRowThatGoesAndComesBackBringsNoOldLine` (7 in all).
- **Red:**
  - first as a compile failure (`Unresolved reference 'WatchFaceResult'`, `'createWatchFaceOffer'`);
  - then on a stub that compiled: `:shared:iosSimulatorArm64Test` 4 of 4 failed on their assertions, and the Android test on `NotImplementedError`;
  - the row's tests on a stub that drew nothing: 4 of 5 failed (`noFaceOfferedShowsNoRow` holds on any row that asks and shows nothing).
- **Green:** 4/4, 1/1 and 5/5.
- **Mutants:** two, run together, each killed by its own test.
  - Reading the offer once instead of each time in front fails `theOfferIsAskedAgainEachTimeTheAppComesToTheFront`.
  - A failure line that an added face does not clear fails `aRefusedFaceIsSaidUnderTheRowUntilOneIsAdded`.

## Step 4: the checks

- **`scripts/test.sh` whole: exit 0.**
  - check-versions 1.3.0 (39) / (100039), test-bump PASS, test_privacy_check PASS, privacy-check clean but for the 194 rows awaiting M3, the listing clean.
  - `swift test`: WatchCoreTests 285, WatchViewsTests 6 and PhoneHarnessTests 14, 0 failures. The timetables compiled.
  - The Gradle suites, 0 failures and 0 skipped: shared iOS 2,291 and host 2,314, watchcore 198 and 231, widgetcore 109 and 111, wearApp 505.
  - No EOFException, and no re-run was needed.
- **`python3 tools/i18n-check.py`:** clean. So is `scripts/check-strings.sh`.
- **`scripts/ios-build.sh`:** BUILD SUCCEEDED. TaqwaWidget is 3 MB of 15, TaqwaWatchWidgets 4 MB of 5. `check-watch-bundle` holds every rule: 4380 of 5120 KB, 12000 of 15360 KB, 4b and 8 skipped as for any Debug simulator build.
  - The new Swift file compiles with no warning. The one `KotlinUnit` warning in `iOSApp.swift`, now at line 220 and moved down by the new lines, is the known one in the widget placement hook.
  - The final build, made after the placeholder was deleted, holds no `Taqwa.watchface`: the "Watch face" phase removed the copy the placeholder build left.
- `git diff --check`: clean.

## Step 5: on the paired simulators

Under the `ios-pair` lock, 13:47:49 to 13:57:31. The control tool tapped on the iPhone and the watch with no access request left unanswered. Every shot is `xcrun simctl io <udid> screenshot`, and every log line is from `log show`, saved in [`2026-10-31-watchos-add-to-apple-watch/phone.log`](2026-10-31-watchos-add-to-apple-watch/phone.log).

**Found:**
- No pair.
- iPhone 17 Pro `2D01CE58`: Shutdown, with the store's Taqwa 1.2.0 (38) (`prev-Taqwa.app`, tree md5 `b0ea6765`; Doha, MANUAL). 151 data containers, 83 groups, 502 plugin containers and 251 Preferences files.
- Series 12 `C803D94B`: Booted, as P4.5B's put-back left it (no lock, nothing attached). Unpaired, 15 apps, no Taqwa app or group, `("en-US")`, `en_US@rg=zazzzz`, Numerals Duo.
- Before anything changed, the iPhone's whole data folder was cloned (APFS clone, `cp -c`).

**What ran (erratum p4 #16):**
1. The pair `2B37CC2C` was made and the iPhone booted.
2. The debug `Taqwa.app` went on the iPhone over the store build, and its `Watch/TaqwaWatch.app` on the Series 12.
3. Settings was opened with the DEBUG route `taqwa://recite/screen?name=settings`. iOS 27 showed no "Open in Taqwa?" alert here.

| | log | shot |
|---|---|---|
| No file bundled | `TaqwaWatch face offered=false activated=true paired=true bundled=false` | [`settings-no-face-en.png`](2026-10-31-watchos-add-to-apple-watch/settings-no-face-en.png): the App card ends at Language, with no row |
| A placeholder `Taqwa.watchface` bundled (a text file, this step only) | `offered=true activated=true paired=true bundled=true` | [`settings-placeholder-row-en.png`](2026-10-31-watchos-add-to-apple-watch/settings-placeholder-row-en.png): "Add to Apple Watch" after Language, the link glyph at the trailing edge |
| A tap | `TaqwaWatch face refused: invalidFile code=2 domain=CLKWatchFaceLibraryErrorDomain` | [`settings-placeholder-refused-en.png`](2026-10-31-watchos-add-to-apple-watch/settings-placeholder-refused-en.png): the line under the row, level with its label, inside the card |
| The same in Arabic (`-AppleLanguages (ar)`) | `offered=true …`, then `refused: invalidFile code=2 …` | [`settings-placeholder-row-ar.png`](2026-10-31-watchos-add-to-apple-watch/settings-placeholder-row-ar.png), [`settings-placeholder-refused-ar.png`](2026-10-31-watchos-add-to-apple-watch/settings-placeholder-refused-ar.png): "أضِف إلى Apple Watch", the arrow at the trailing (left) edge, the draft line under it right-aligned |

The placeholder was deleted from `iosApp/iosApp/WatchFace/` before any commit, and `git status` showed the folder clean of it.

### The real file: the simulators cannot make it

- **On the watch:** on the Series 12 (watchOS 27.0), the face switcher (touch and hold) offers **Edit** and no Share button. See [`series12-switcher-no-share.png`](2026-10-31-watchos-add-to-apple-watch/series12-switcher-no-share.png). The "New" card's gallery opened, but with nothing to share a face, building the Modular face here would not give a file.
- **On the iPhone:** the iOS 27.0 simulator's Watch app (`com.apple.Bridge`), where a face is shared from My Faces, **aborts at launch**. Two crash reports were made at 13:52:26 and 13:52:27: `EXC_CRASH (SIGABRT)`, an uncaught `NSException` raised from Bridge's own code. Its first launch asked for notifications. "Don't Allow" was chosen, and the put-back undid that.
- So neither of Apple's two ways to share a face exists on these simulators. **The file is plan 5's** (question 3: made on her watch through her iPhone's TestFlight build, staged on Cape Town, inspected before the commit). So is spec 4.5's "to verify": that a tap with the real file brings up the add-face sheet, and that sharing works with WidgetKit complications. Nothing was bundled or shot for it here. If her watch cannot make the file, spec 4.5 drops the row and Mohamed is told.

**Put back:**
- **The apps:** the watch app was uninstalled from the Series 12, and the debug app from the iPhone. The store's 1.2.0 (38) was reinstalled from the clone, and its data and group trees equalled the found ones.
- **The iPhone:**
  - It was shut down, and its whole data folder was put back from the clone (`ditto --clone`).
  - Every entry equals the found folder but three locationd cache files the clone could not read. Those three were moved back from the used folder as they were, and the used folder was removed.
  - The Preferences md5 list is identical, the store bundle is `b0ea6765`, and the data, group, plugin and bundle containers are equal by identifier, with `Library/WatchConnectivity` as found.
- **The pair** `2B37CC2C` was unpaired, which shut the Series 12 down, as Task 5B found. The Series 12 was booted again, as found:
  - unpaired, 15 apps, the same data containers by identifier, `("en-US")`, `en_US@rg=zazzzz`, Numerals Duo;
  - two system groups (`feedbacklogger`, `private.translation`) were not back yet at 13:57. Daemons make them as they run, as Task 5B saw.
- **Off the devices:** the Mac's `~/Library/Logs/DiagnosticReports` keeps the two Bridge crash reports. The ledger has the found and put-back lines.

## The errata and rulings

1. **`git merge --ff-only watch-companion`:** already up to date, base `d3c73d98` (with P2.8, P4.4, P4.6, P4.5B and the Settings redesign, MM3).
2. **`ios-pair` under its lock, never beside P4.5B:** P4.5B had landed (`d3c73d98`). Step 5 took the lock for its whole device part.
   - The Compose strings and `SettingsRootScreen.kt` were only added to: one call after Language, and one block after `settings_language` in each folder. Nothing was reordered, so Lane P, P5.6 and `notifications-layout-b` merge around it.
   - Every language is there, with `values-id` and its `values-in` twin equal. The non-English text is a draft to native review, except Arabic's row, which the plan gives.
3. **Plan 4 errata (p4):**
   - **#4** (Task 10 also waits on Tasks 4 and 6): both were on `watch-companion`.
   - **#16:** the debug iPhone Taqwa with the watch app went on the pair, and the store build was put back (above).
   - The others concern Tasks 1 to 3, 5 to 9 and 11.
4. **Rulings 55 to 64:**
   - **64 item 8:** the found state was recorded and put back. The Series 12 was found booted by a put-back with no lock, so it was left booted.
   - **64 item 7:** the control tool's taps on the iPhone and the watch went through, with no request waiting on Mohamed.
   - **Ruling 56:** untouched. No Swift port, decoder or vector changed, and `swift test` stays 285 + 6 + 14.
   - **Ruling 39:** the row and its hook carry no location, and their log lines name no place, bearing or person. The promise lines are plan 5's, and nothing here touches them.
   - **Rulings 41, 42 and 43** concern the face's hands, reading order and padding: untouched.
5. **WIP commits:** `fe7e69bf` (the offer, green), `60f9940a` (the row and the strings, green) and `8a64048f` (the Swift side, ios-build green). They are folded into the task's commit.
6. **Host state:** Xcode 27.2. `scripts/test.sh` ran whole, and the simulator step ran under its lock.
7. **Devices:** only the pair (`ios-pair`). The SE 3 was not used. No real device and no work iPhone 12; `KB2604110095` was untouched.
8. **The Gradle flake** did not arise.

## Where it differs from the plan

- **`forward`** is `LocalLayoutDirection.current == LayoutDirection.Ltr`, as About's link rows give it, not a literal `true`. `drawExternalLink`'s contract is the caller's reading of the direction, and `true` would point the arrow to the leading edge in Arabic and Urdu.
- **The failure line** is inside the card under the row: `SettingsNote(gutter = 12.dp)`, level with the row's 16 dp label, 12 dp above the card's edge, in a polite live region that merges its words (review round 1). "Under it" read literally, and plan 5's `WatchSection` keeps the row in a card too.
- **Two more tests than Step 1's four,** one per behaviour the row adds: the row's Compose tests and Android's actual.
- **`isAvailable()` does not need the watch app installed.** That is the plan's rule (activated, paired and a file): Apple's add-face sheet offers the apps a face's complications need.

## For the Lead and plan 5

- **The real file and the add-face sheet are plan 5's** (above). Until the file is bundled, the row is never shown, on any phone.
- **The row is not the form Apple documents, and the Lead rules on it before the file is bundled** (review round 1). Apple's [Sharing an Apple Watch face](https://developer.apple.com/documentation/clockkit/sharing-an-apple-watch-face) says:
  > Always display both the watch face preview with an official Add Apple Watch Face button.

  > Only add watch faces in response to the user tapping an Add Apple Watch Face button.

  The same page says to share a face offered in an app by email, the one share that carries the face's preview with the `.watchface` file. The row as built is a text `LinkRow` with the external-link glyph, with no preview and no official button, and neither spec 4.5 nor plan 4 Task 10 asked for them. The two ways:
  - **Keep the Settings row,** recorded as a deviation from Apple's guidance. Nothing changes. The cost is a row unlike what Apple shows, and a risk, which no one has measured, that App Review asks for the documented form.
  - **The face's preview and Apple's Add Apple Watch Face badge,** built when the file lands (plan 5 Task 15): the preview image the email share gives, bundled beside the file, and the badge in each of the seven languages from Apple's marketing resources, under their terms. The badge's tap calls `addWatchFace(at:)`. The cost is a visual change to Settings (Mohamed's design call), two more bundled images, and the badge's terms checked.
  - Either way the face is shared by email (`iosApp/iosApp/WatchFace/README.md` and plan 4's hand-off now say so), so the preview is in hand. No code changes now: the row stays hidden until the file exists.
- **A cancelled sheet is untested.** ClockKit's errors are `notFileURL`, `invalidFile`, `permissionDenied`, `faceNotAvailable` and `noURL`. If her "Cancel" in Apple's sheet answers one of them (likely `permissionDenied`), the row shows "The face could not be added. Try again from the Watch app." after a cancel. Plan 5's check 8 should cancel once. If it does, the Lead may rule that `permissionDenied` shows nothing: one line in `WatchFaceShare.add`, and a test.
- **The offer is read each time the app comes to the front.** A watch paired while Settings is in front shows the row at the next return, and so does a session not yet activated on a cold launch straight into Settings (activation took 0.2 s here).
- **The Arabic line ends with a Latin word.** "…من تطبيق Watch." puts the full stop at the line's left, as right-to-left text does. That is for the native review with the draft.

## Review round 1 (31 October, two lenses)

The spec, behaviour and evidence lens found it clean (7 minors). The code, platform, tests, privacy, accessibility and hygiene lens found 2 Important items and 9 minors. Both Important items are fixed here.

1. **VoiceOver never read the failure line on iOS.** The polite live region sat on a `Column` with no text that merged nothing. CMP 1.12.1's iOS mediator (`Accessibility.ios.kt`, `makeSemanticsNode`) announces a live region only when that node's own `accessibilityLabel` changes. A node that is not an accessibility element has no label: `shouldMergeDescription` is `canBeAccessibilityElement()`, which is false for a box with no text and no merge, so its label is null and nothing is posted.
   - **Fix:** `semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }` on that `Column`. `canBeAccessibilityElement()` is then true, the label is the merged line, and a line that has just appeared is a new element whose old label is null, so it is announced. Nothing drawn changes: this is a semantics-only edit.
   - **Test first:** `aRefusedFaceIsSpokenWithItsWordsAsItComes` asserts that no live region exists before the tap, and that exactly one polite `LiveRegion` node's merged text is `settings_add_to_apple_watch_failed` after it. It failed on `b42ebc35` ("Expected exactly '1' node but could not find any node that satisfies: ((LiveRegion = 'Polite') && (Text … contains 'The face could not be added. …'))"), and it passes with the fix.
   - **The spoken announcement itself** is not heard here: the simulator has no VoiceOver. It is Task 11's VoiceOver pass and plan 5's VoiceOver gate on her iPhone.
2. **Apple's face-sharing guidance was recorded nowhere.** It is now quoted in `iosApp/iosApp/WatchFace/README.md` and above, with the two ways put to the Lead. Plan 4's hand-off to plan 5 asks for the ruling before the file is bundled, and asks for the face to be shared by email. No code changed for it.

**Also fixed (the spec lens's minor 3):** `if (!offered) failed = false` was held by no test. `aRowThatGoesAndComesBackBringsNoOldLine` covers it: the line shows, then the watch is unpaired and the row goes, then the watch is paired again and the row comes back with no line. With that reset removed, the new test fails and the other six pass, so it kills that mutant.

**Checks after the fixes:**
- **`scripts/test.sh` whole: exit 0.** Shared iOS 2,293 (`WatchFaceRowTest` 7, `IosWatchFaceOfferTest` 4) and host 2,314, watchcore 198 and 231, widgetcore 109 and 111, wearApp 505, and `swift test` 285 + 6 + 14, all with 0 failures. No EOFException. The run held `ios-sim-tests`.
- **`python3 tools/i18n-check.py`:** clean. No string changed.
- **`scripts/ios-build.sh`:** BUILD SUCCEEDED. `check-watch-bundle` holds every rule (4380 of 5120 KB, 12000 of 15360 KB). `WatchFace/` holds only its README.
- **`git diff --check`:** clean.
- **No device was used.** The fix changes semantics only, so Step 5's shots stand.
