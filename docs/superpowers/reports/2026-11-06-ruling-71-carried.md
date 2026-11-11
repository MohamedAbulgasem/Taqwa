# Ruling 71 items 2, 3 and 4a: the MM4 review's carried minors

Lane P, on `watch-feed`, 6 November 2026. Base `b25dfa0a` (`git merge --ff-only watch-companion`:
already up to date; `watch-feed` had no commits of its own and no WIP from an earlier run). This is
no plan task: it is Ruling 71 items 2, 3 and 4a in the ledger, the four minors MM4's review
(5 Nov 22:45) carried and the Lead's 6 Nov 09:10 line listed. Item 4b (the What's New draft
markers) was P5.11's and landed in `d056374a`. Nothing is pushed; Taqwa identity; no trailer. The
version is not bumped (the watch ships as 1.6.0 (42); the cut is the Lead's).

Two commits, each test-first:

| Commit | Item | Subject |
|---|---|---|
| `92d0eb4a` | 3 and 4a | Docs: the watch's text names 1.6.0, its release, and the App Review notes are the 1.6.0 (42) file (Ruling 71) |
| `071590a6` | 2 | iOS: the launch URLs reach the watch probe before the ayah route, as an opened URL does (Ruling 71) |

The evidence is in [`2026-11-06-ruling-71-carried/`](2026-11-06-ruling-71-carried/).

## Item 2: the launch route tries the watch probe (DEBUG)

**The defect.** `RecitationHarness.runLaunchURLs` hands each `TAQWA_HARNESS_URLS` entry to
recitation's commands and then straight to `openAyah`. `onOpenURL` tries `WatchHarness.shared.handle`
between the two, so plan 4's probe (`taqwa://watch/probe?...`) worked through `simctl openurl`
only. On iOS 27 `simctl openurl` waits behind "Open in Taqwa?", which no script answers, so the
launch URLs are the only scriptable way in, and the probe could not be driven there.

**The change** (`iosApp/iosApp/iOSApp.swift`, inside `#if DEBUG`), exactly as the ruling words it:

```swift
if !handle(url) && !WatchHarness.shared.handle(url) { openAyah(url) }
```

The doc comment now names the order (recitation, the watch probe, the ayah route) and the probe
as a user of the launch URLs. A release build is unchanged: the whole harness, `WatchHarness`
included, is compiled out.

**Red, then green, with plan 4's probe on the `ios-pair` simulators.** The probe can run there, so
that is the proof the task asks for (the fallback, a test that the route consults `WatchHarness`,
was not needed). The iPhone 17 Pro `2D01CE58` (iOS 27.0) and the Series 12 (46mm) `C803D94B`
(watchOS 27.0) were paired (`6ECD3183`), both booted, and the base build's `Taqwa.app` and its
`Watch/TaqwaWatch.app` installed (`scripts/ios-build.sh`'s ad hoc signed simulator build, as
erratum p5 #29 asks for an `ios-pair` check). WatchConnectivity reported the pair reachable,
paired and installed. Each run was the same launch:

```
SIMCTL_CHILD_TAQWA_HARNESS_URLS="taqwa://watch/probe?via=context" \
  xcrun simctl launch --terminate-running-process 2D01CE58-... world.taqwa.ios
```

| Build | What the phone logged ([red](2026-11-06-ruling-71-carried/red-phone.log), [green](2026-11-06-ruling-71-carried/green-phone.log)) |
|---|---|
| base `b25dfa0a` (red) | 19:35:26.341 `TaqwaHarness launch url taqwa://watch/probe?via=context`, then no `TaqwaWatch probe` line in the 37 s that followed: `openAyah` dropped it |
| fixed (green) | 19:37:03.958 the same launch line, then at once `TaqwaWatch probe context from=60000 to=300000 step=4096 fill=random reachable=true`, eight sizes, `TaqwaWatch probe context random 258750 262500`, `TaqwaWatch probe restore` and `send queued: bytes=21335 delivery=5/8dfbe547cf8f60c4` |

The green figures are plan 4 Task 5B's (`docs/superpowers/reports/2026-10-24-apple-watch-link.md`):
258,750 accepted and 262,500 refused. The watch logged `nothing to store via context` for each
accepted probe and `unchanged 5/8dfbe547cf8f60c4 31 days via context` after the restore
([watch log](2026-11-06-ruling-71-carried/green-watch.log)): the real context was back.

The ayah route is unchanged by the order: `taqwa://ayah/36/1` through the same launch route still
opens Ya-Sin at 36:1 on the fixed build ([shot](2026-11-06-ruling-71-carried/green-ayah-36-1.png)).
That is the path `tools/store/capture-ios.sh` uses.

A first red attempt did not count: launched after the watch app, the phone app had already been
started in the background by the watch's handshake (19:34:22, as plan 4 Task 5B saw), so
`simctl launch` only brought that process forward and its environment had no
`TAQWA_HARNESS_URLS`. Every counted run used `--terminate-running-process`, so the process was new.

**Ruling 39.** No line either app logged names a city, a coordinate or a bearing: sizes, the
delivery number and the feed's fingerprint only.

**`scripts/ios-build.sh`** on the fixed tree: BUILD SUCCEEDED; `TaqwaWidget.appex` 3 MB of 15,
`TaqwaWatchWidgets.appex` 4 MB of 5; `check-watch-bundle.sh` every rule holds (4,412 of 5,120 KB,
12,012 of 15,360 KB; rule 4b skipped ad hoc, rule 8 skipped for a Debug bundle). No Swift warning
in `iosApp/iosApp/` on either build.

### The devices, found and put back

- `ios-pair` taken at 19:30:58 (no wait) and released at 19:41:47. Found: no pair, both Shutdown,
  the iPhone holding the store's 1.2.0 (38) (`prev-Taqwa.app`), the Series 12 no Taqwa app.
- Both `data` folders were cloned first (`cp -c`). On the iPhone, the four files in
  `Library/Caches/locationd/locScoreInfo` cannot be read from this shell ("Operation not
  permitted"), so they were not cloned. They were left where they were, never read or written, and
  the hashes below leave that folder out.
- Put back: both apps terminated, the watch shut down, the pair unpaired (which erased the watch
  again, to 11 entries, as plan 4 recorded), the iPhone shut down. Each `data` folder was moved
  aside and its clone copied back whole. The `locScoreInfo` folder was moved back into the
  iPhone's, and the one cache file this run's boot had added to it (19:33) was removed.
- Compared ([state](2026-11-06-ruling-71-carried/ios-pair-state.txt)): iPhone 54,434 entries,
  content hash `b2b38a68` (found: the same), plus the same four `locScoreInfo` files (names, sizes
  and times as found); Series 12 7,078 entries, hash `4c49f056` (found: the same). No pair; both
  Shutdown. The extended attributes (the backup exclusions among them) are the clone's, and the
  clone's are the found ones. The copy adds macOS's own `com.apple.provenance` to files it writes,
  as any copy from this shell does.
- No other device. `KB2604110095`, `emulator-5572`, the booted iPhone 17 Pro Max, the SE 3 and the
  work iPhone 12 were not touched. No Android emulator was used.

## Item 3: the watch's text names 1.6.0

1.4.0 and 1.5.0 shipped without the watch, with the Play description on the App Store too. So
every line that named 1.4.0 as the watch's release names 1.6.0:

| File | Line | Now |
|---|---|---|
| `README.md` | 144 | slice 5: `**Coming in 1.6.0**` |
| `docs/STORE-PRIVACY.md` | 13 | `**Answered again for the watch release (1.6.0), 30 October 2026**` (the date is when the forms were answered, so it stays) |
| `docs/STORE-PRIVACY.md` | 146 | `**The watch link (1.6.0):**` |
| `docs/STORE-PRIVACY.md` | 205 | `**The Apple Watch app (1.6.0):**` |
| `docs/store/listing/{en,ar,fr,tr,id,ur,bn}.md` | the App Store description's note (en 132, ar 127, fr/tr/bn 126, ur 125, id 123) | `(Its own text since 1.6.0, as each store names only its own watch ...)` |

- Red first: the grep for those five texts found 11 lines. Green: it finds none, and the same
  grep for `1.6.0` finds the 11.
- No store field changed: the listings' notes sit outside every field. `store-listing-check`
  clean, every field within its limit; the App Review notes 4,000 of 4,000 bytes.
- Nothing else in the tree names 1.4.0 as the watch's release. The other hits are main's real
  1.4.0 (40) (the What's New sets' rule in `store-listing-check.py`, its test fixtures, the
  1.4.0-40 smoke sheet and release notes), a library version comment, and a widget comment about
  the 1.4.0 build. The plans and reports are records and stay as written.
- The native review's tables (`2026-10-24-watch-native-review.md`) hold that note as a row in six
  languages (ar 116, fr 124, tr 118, id 126, ur 116, bn 116), both columns. Those six rows follow
  the files, so the tables still equal the files ("all rows match their files" is what each reader
  checks).

## Item 4a: the App Review notes are the 1.6.0 (42) file

- `git mv docs/store/app-review/2026-10-30-notes-1.4.0-40.txt
  docs/store/app-review/2026-10-30-notes-1.6.0-42.txt`. The file is byte for byte the same (md5
  `eba9f23f` before and after), so Mohamed's English (M8) is unchanged. The name keeps its date,
  the day the text was written, and takes the release's `<V>-<C>`, plan 5 Task 9's
  `<date>-notes-<V>-<C>.txt`.
- Red first, on the `git mv` alone: `store-listing-check` exit 1 (`app-review:
  2026-10-30-notes-1.4.0-40.txt, which en.md points to, is not there`) and `privacy-check` exit 1,
  9 problems (four `3-4n-notes-file` rows "the file is missing", and five lines of the renamed file
  in no row or list, since KNOWN still named the old path). The output is in
  [`4a-red.txt`](2026-11-06-ruling-71-carried/4a-red.txt).
- What names it now:
  - `docs/store/listing/en.md:156`, the Notes pointer;
  - `tools/privacy-check.py`: the four `3-4n-notes-file` rows, the `3-4n-notes-pointer` row's new
    text (the same pointer), and the two KNOWN entries (the notes' credits lines, and the line
    quoting the watch's message, Ruling 69 item 2);
  - `tools/test_store_listing_check.py`: the notes file in its fixtures (10 lines), so the test's
    tree is named as the real one is;
  - `docs/STORE-CHECKLIST.md`: line 358 (App Review Information › Notes, which named the build 35
    file) and lines 383-384 ("the Notes, last brought up to date for 1.6.0 (42), are
    `docs/store/app-review/2026-10-30-notes-1.6.0-42.txt` (4,000 bytes; ..."), which named the build
    35 file and its 3,969 bytes. No other STORE-CHECKLIST line changed. Lane A's P5.7 section is
    at lines 280-331 on `watch-apple`, so the two do not meet.
  - the native review's English list (E 114 and E-notes) and its five tables' Review notes rows,
    which quote `en.md:156`. Its review-round 4 line (767) is a record of that round and keeps the
    old name.
- Green: `store-listing-check` clean (`app-review: 2026-10-30-notes-1.6.0-42.txt 4000/4000
  bytes`); `privacy-check` 263 rows (263 approved, 0 awaiting), 7 policies, 238 sweep lines,
  clean; `test_store_listing_check` 56 OK; `test_privacy_check` 46 OK. `git grep` finds the old
  name only in `docs/superpowers` (plans and reports, as records).

## The errata

1. **`git merge --ff-only watch-companion`:** already up to date, base `b25dfa0a`.
2. **The task is Ruling 71 items 2, 3 and 4a exactly.** Item 2 proved with plan 4's probe through
   `TAQWA_HARNESS_URLS` on `ios-pair` under its lock (the probe could run, so no fallback test),
   and `scripts/ios-build.sh` passes. Item 3 docs text only, the store copy untouched, every field
   within its limit. Item 4a: the `git mv`, `en.md:156`, the pin and its test, STORE-CHECKLIST's
   two pointers only; the notes' text unchanged.
   - **"store-listing-check's KNOWN pin":** `tools/store-listing-check.py` has no KNOWN; it reads
     the notes file from `en.md`'s pointer. The KNOWN that pins the file is
     `tools/privacy-check.py`'s (two entries), with its rows. Both were updated, and both checkers'
     tests pass: `test_store_listing_check.py` names the file in its fixtures (updated),
     `test_privacy_check.py` does not name it.
   - **STORE-CHECKLIST "about 384":** the pointer is the sentence on lines 383-384, so both lines
     changed, and 358.
3. **Plan errata p5:** #29 (an `ios-pair` check installs `scripts/ios-build.sh`'s signed build, not
   CI's unsigned one) was followed: the installed builds are `ios-build.sh`'s. #25 (the notes
   carry Settings › About Taqwa › Attribution & licences and the "Open Taqwa on your phone"
   clause, within 4,000 bytes) is in the file already and is unchanged by the rename. #27
   (STORE-PRIVACY's backup qualification) is untouched by the three version labels. No other p5
   entry touches these items.
4. **Rulings 39, 41, 42, 43, 50, 52-71:** no face, string, privacy wording, delivery or Wear file
   changed. The privacy promise "Only your own devices can see your location." is untouched, and
   `privacy-check` is clean. The probe's restore used the real delivery path (Ruling 50: delivery
   `5/8dfbe547cf8f60c4`). No face change, so `tools/face-*.py --check` and the always-on share are
   unaffected. No APK was built (Ruling 55 item 6 untouched).
5. **Ruling 71:** read first; this is its item 6's Lane P order, after P5.11's close (landed
   `b25dfa0a`).
6. **Version:** not bumped.
7. **Budget:** one red build, one green build, one device session; WIP was not needed between the
   green steps, as each commit was made at its first green.
8. **Devices:** above. `wear-5558` and `phone-5562` were not taken and not touched; they stay as
   Lane W left them.
9. **The Gradle flake:** see the checks below.

## The checks

- `scripts/ios-build.sh`: BUILD SUCCEEDED on the base (red build) and on the fix, every bundle rule.
- `python3 tools/store-listing-check.py`: clean. `python3 tools/privacy-check.py`: clean.
- `python3 tools/test_store_listing_check.py`: 56 OK. `python3 tools/test_privacy_check.py`: 46 OK.
- `git diff --check`: clean on both commits.
- `scripts/test.sh` whole on `071590a6`: exit 0 in 116 s, under `ios-sim-tests` (taken 19:42:12,
  released 19:44:09, no wait). `test-bump-version.sh` PASS, `test_privacy_check.py` PASS,
  privacy-check 263 rows clean, `test_store_listing_check.py` PASS, listing clean; `swift test`
  WatchCore 285, WatchViews 6, PhoneHarness 19 (WatchProbeTests 14, WatchKeyFileTests 5), 0
  failures; `:wearApp:testDebugUnitTest` ran; the `:shared`, `:widgetcore` and `:watchcore` host
  and Kotlin/Native suites were UP-TO-DATE: Gradle found their inputs unchanged since their last
  green run in this worktree, as neither commit touches Kotlin. 0 failures, no EOFException, no
  re-run needed.

## For the Lead

- The ruling's "store-listing-check's KNOWN" is `privacy-check.py`'s KNOWN (erratum 2 above).
- On iOS 27, a `simctl launch` with `SIMCTL_CHILD_*` reaches the app only when it starts a new
  process. A paired watch's handshake can start the phone app first, in the background, so a
  launch-URL run on `ios-pair` needs `--terminate-running-process` (or the phone app launched
  before the watch app).
- The iPhone 17 Pro's `Library/Caches/locationd/locScoreInfo` holds files this shell cannot read,
  so a whole clone of that simulator misses them. Here they were left in place. A later `ios-pair`
  put-back should do the same, or it will lose them.
