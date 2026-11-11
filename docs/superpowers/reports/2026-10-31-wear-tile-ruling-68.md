# Ruling 68 item 2: the Tile's left minors

Lane W (`watch-wear`), 31 October 2026, R68w. Ruling 68 item 2 (a) to (e), built test-first: review
round 6's minors on the Tile (P3.8) and review round 2's on the pass (P3.9).

- **Base** `6ba0afc1` (`git merge --ff-only watch-companion`: already up to date; `watch-wear` had no
  commits of its own).
- **Commits:** three, each `Wear: … (Ruling 68)`, folded from the WIPs `7b791ca4` (red first),
  `d9108578`, `4a39e42e` and `9578f6a1`. Mohamed's Taqwa identity, no trailer, nothing pushed.
- **Devices:** none. Every item is held on the host (JVM tests, Gradle's own up-to-date checks, the
  drivers' host scripts against the committed outputs), so no turn on `emulator-5558` was needed and
  the `wear-5558` lock was not taken. The plugged-in phone `KB2604110095`, the work iPhone 12 and
  every simulator but `scripts/test.sh`'s own Kotlin/Native runs were not touched. No release key
  was read or used.

## (a) Never asked for more than once a minute

The Tiles reference, on `setFreshnessIntervalMillis`: "This mechanism should not be used to update
your tile more frequently than once a minute, and the system may throttle your updates if you
request updates faster than this interval" (androidx `TileBuilders.java`).

- `TilePlan.MIN_FRESH_MILLIS` = 60,000. `freshnessMillis` is 0 once the notice shows, as before, and
  otherwise never less than a minute. A moving part the Tile's weight ends within 105 s (a minute
  and three quarters again), or a notice that begins within a minute, is asked for a minute on; the
  line says the start's time in between (call 6), as past the moving hours, so it stays true
  (Ruling 65).
- **Test** `TilePlanTest.theTileIsNeverAskedForMoreThanOnceAMinute`: the sample at 16:07:58 with the
  moving part ended at 16:08:00 (review round 6's case: 1,142 ms before), 20 s before the feed's
  last start, then every 17 minutes through a day with the moving part ended at each of its entries
  and every 7 s across the last start: at least a minute while anything is left to show, 0 once the
  notice shows. **Red first** on `6ba0afc1`'s code: `expected:<60000> but was:<1142>`.
- **Mutant** (killed, restored): the floor applied to 0 too fails it (`expected:<0> but was:<60000>`
  past the last start).

## (b) The city cut as a last resort

Review round 6 found `fitted`'s "keeps every timeline within it" false when nothing moves: the
sample with a 6,000-, 9,000- or 12,000-byte city sent 308,081, 434,109 or 560,109 B. The ruling
allowed the last resort if it is under 30 lines; it is 10 (`fitted` and the signature of the
`movingFitted` it now wraps), so it is built.

- `TileLayout.fitted` first fits the moving part as before (`movingFitted`). Only while that is still
  over `MAX_TIMELINE_BYTES` does it cut the city to half its letters (code points, so no surrogate
  pair is split), mark the cut with an ellipsis, and fit the moving part again for the cut city;
  again until it fits, or the city is empty. So the city sent is the longest of its halvings at
  which the timeline fits, and the moving part is whatever fits beside it. Where the update takes
  the city's place (a newer format seen), the city weighs nothing and is never cut.
- `MAX_TIMELINE_BYTES`' KDoc now says what it holds for: every feed the phone writes, and, by the
  last resort, whatever the city's length. The city is the one text of the Tile that no list of
  the phone's bounds (the names, the countdown words and the notices are the phone's own strings);
  a feed no phone writes, with names as long, is not claimed.
- **Tests:**
  - `TileLayoutTest.aCityNoPhoneWritesIsCutBeforeTheTimelinePassesItsBound`: the sample with a
    6,000-byte city (2,000 Bengali letters) and a 12,000-byte one, at 14:58 and every 6 hours
    through a day, without and with the update. Each Tile sent is within the bound as its own
    protobuf; every entry carries one city; without the update it is one of the city's halvings
    with an ellipsis, the halving before it does not fit even with nothing moving, and the entries
    are those `fitted` sends for the cut city (the moving part fitted again); with the update the
    city is whole. **Red first** on `6ba0afc1`'s code: `en at 1792274400, newer false: 286121 bytes`.
  - `assertSmallEnough`, which every size sweep calls (Cape Town and Tromsø, the fourteen phones,
    the worst city of 300 bytes, with and without the update), now also holds the city whole, so
    the phone's own cities are never cut.
- **Mutants** (each killed, restored): halving by quarters ("the halving before it does not fit even
  with nothing moving"); cutting the city on the plan with nothing moving and no refit (the refit
  assertion: the entries differ); cutting the city whenever the moving part was shortened, before
  it is given up (the same "halving before" assertion, and
  `theMovingPartEndsAtTheLatestOfItsEntriesAtWhichTheTimelineFits`).
- **Measured** (a scratch probe, never committed; the sample, `TestMeasures.ROUGH`, the JVM):

| City | Sent as | Bytes | Counting down |
|---|---|---|---|
| 3,000 B (1,000 letters) | whole | 195,257–197,418 | 2 s to 9 min |
| 6,000 B | 1,000 letters + "…" (3,003 B) | 195,392–197,553 | 2 s to 9 min |
| 9,000 B | 750 letters + "…" (2,253 B) | 197,460–199,955 | 12 to 24 min |
| 12,000 B | 1,000 letters + "…" | as 6,000 B | as 6,000 B |
| 60,000 B (20,000 letters) | 1,250 or 625 letters + "…" | 189,264–199,862 | none, or 22 to 25 min |

  At 14:58, 16:07:58 and every 6 hours from midnight. A heavy request took 15 to 66 ms on the JVM
  (the first, 144 ms, with the JIT cold), against 2 to 5 ms for the phone's feeds; on a watch it
  will be slower, but only for a city no phone writes. The 16:07:58 request with 2 s counting down
  is now asked again in a minute, by (a).

## (c) The tests run again when their inputs change

- **Seen stale first:** with `:wearApp:testDebugUnitTest` up to date, a changed
  `watchcore/vectors/phone/phone-capetown-en.json` (read by `PhoneFeedsTest` and
  `TileLayoutTest.theMovingPartIsSentWholeWhereverItFits`) and a changed
  `shared/src/commonMain/composeResources/files/cities.csv` (read by `LongestCity`) each left it
  `UP-TO-DATE`.
- **Fixed** in `wearApp/build.gradle.kts`: `phone-capetown-en.json` joins `phoneFeeds`, and
  `cities.csv` and `city-names-*.csv` are a new `phoneCities` input of every `Test` task, whose
  directory Gradle hands in as `taqwa.phoneCities` (which `LongestCity` already read, and was never
  set). Now each change reruns the task (`cities.csv` and `city-names-bn.csv` too), and the restored
  files come back `FROM-CACHE`.
- Every other file a wearApp test reads was already a declared input (the samples, the other
  goldens, the manifest, the face, the capabilities, the app's and the phone's resources, fonts,
  haptics, forms, strings and `WatchWords.kt`).

## (d) The binder wording and the deprecation claim

- `javap -c` of tiles 1.6.2's `TileCallback$Stub$Proxy.updateTileData`: `transact(1, data, null, 1)`,
  `FLAG_ONEWAY` with no reply. So the timeline lands in the renderer's async space, half of its
  process's binder buffer of about a megabyte, shared by every one-way call in flight to it.
  `MAX_TIMELINE_BYTES`' KDoc, `TilePlan`'s and `TileLayoutTest.theTimelineIsSmallEnoughToSend`'s
  comment now say so; the bound itself (200,000 B, the project's margin) is unchanged.
- `javap -v` of `TileService`: `Deprecated` marks `onResourcesRequest`, `onTileEnterEvent` and
  `onTileLeaveEvent`, never `onTileResourcesRequest`, which `TodayTileService` overrides. Round 5's
  minors-left line in `2026-10-30-wear-tile.md` is corrected in place, with a section at its end
  pointing here.

## (e) The pass's Step 5 counts and its drivers

- `2026-10-24-wear-app-pass/README.md` Step 5 now says wearApp 498 tests on the final tree
  `8adb903e` (492 before; 4 added by the Today fix `4f33198b`, 2 by its review fix `8adb903e`), the
  figure review round 2 ran; the other figures of that line are round 2's too.
- The scripts that drove the pass (`p39/bin/` in the pass's scratchpad, 21 files) and Step 4's six
  hand-made feeds are committed beside the report in `drivers/`, with a README: the turn on the
  watch under the lock, each script, and the Qibla rows again (Ruling 68 item 4). They are the
  pass's scripts with only their paths changed: each finds the drivers, the repository and
  `tools/watchface` from where it lives and writes into `PASS_WORK`; `pullfound.sh` also writes
  `state.sh`'s record, which `restore.sh` diffs against (the pass wrote it by hand). The feeds are
  the samples with the bundle's longest city (city, bearing to the degree, distance, declination; no
  coordinate, Ruling 39). `SetLocale` stays where `tools/watchface/README.md` builds it from source.
- **Held on the host:** from another directory, `refs-tasbeeh.sh` redrew the 12 Tasbeeh drawings
  and masks byte for byte as committed in `ref/`, and `compare-all.sh`, run on the committed matrix
  shots, printed the committed `compare.tsv` byte for byte (137 lines). Every script passes
  `bash -n`; the Python ones compile.

## The errata and rulings this task carried

1. `git merge --ff-only watch-companion`: already up to date at `6ba0afc1`.
2. Not a plan task: Ruling 68 item 2 (a) to (e) exactly, test-first, three `Wear: … (Ruling 68)`
   commits.
3. Plan 3 errata (p3): #5 and #6 (the renderer's quota) are untouched: no dynamic node is added, and
   a cut city only shortens a static text. #16 (Ruling 65, never false under a late renderer)
   holds: past a moving part ended early, the line says the start's time until the next request,
   a minute at least. The rest touch no part of this task; #17 and #18 (Qibla) are Lane P's, which
   the committed drivers serve.
4. Rulings 39, 41, 42 and 43 hold: nothing more leaves the watch or the phone (the city is only
   ever cut, on the watch), and no face file changed, so `face-*.py --check` stays up to date and
   the always-on lit share is unchanged.
5. Ruling 55 item 6 was already done (`fa62c182`); held again below.
6. WIP commits as listed above.
7. `emulator-5558`: not used (see Devices).
8. The known flakes: see Checks.

## Checks

On the final tree:

- `scripts/test.sh`: exit 0 (4 min 33 s). Versions agree at 1.3.0 (39) and (100039);
  `test-bump-version.sh`, `test_privacy_check.py` and `test_store_listing_check.py` PASS;
  privacy-check 241 rows, clean but the 194 awaiting M3; listing clean; `swift test` WatchCoreTests
  234/0 and WatchViewsTests 6/0; Kotlin/Native `iosSimulatorArm64Test` shared 2282, watchcore 198,
  widgetcore 109; host shared 2313, watchcore 228, widgetcore 111 (from the cache: neither module
  changed); 0 failures. No `EOFException` and no `EngineBudgetBenchmarkTest` failure, so no re-run
  was needed.
- `./gradlew :wearApp:testDebugUnitTest --rerun`: 505 tests, 0 failures (503 at the base, 2 added).
- `:wearApp:assembleDebug` and `assembleRelease` (unsigned; no release key read); `lintDebug` and
  `lintRelease`: 0 errors, the one old warning (MissingApplicationIcon). The merged debug and
  release manifests ask for `VIBRATE` and `world.taqwa.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
  alone.
- **Ruling 55 item 6 holds:** the debug APK's assets are the four `sample-feed-capetown*.json`, the
  release's the baseline profile alone; no vector file in either.
- `face-arch.py`, `face-centre.py`, `face-moon.py` and `face-sun.py --check`: up to date. No file
  under `watchface/` or `tools/watchface/` changed, so the validators and `lit.py` had nothing new
  to judge. `tools/i18n-check.py` clean; `git diff --check` clean.

## Minors left

- The cut stops at the longest halving with which the timeline fits, even when nothing then moves
  (the 60,000-byte city at midnight: 1,250 letters, nothing counting down, where 625 would have
  counted down for 22 minutes). That is the ruling's "last resort": the countdown goes before the
  city does. Only a city no phone writes reaches it.
- A heavy city's request was timed on the JVM (15 to 66 ms), not on a watch.
- With the floor, a moving part ended within 105 s can be asked for again after it has ended; the
  line then says the start's time (call 6), so nothing false is shown, but the countdown stops early
  until the next request.
- The drivers' device scripts were not run in this task (no device turn); their paths, and
  `pullfound.sh`'s state record, are the only changes, and the host scripts reproduce the committed outputs. Lane P's Qibla re-shoot is their
  first run on the watch.

## Review fix round 1

Review round 1's spec lens ran the committed drivers on `emulator-5558` (Important, (e)): run as
their README says, `restore.sh` did not put the watch back as found, and its `STATE AS FOUND` could
never print. It left `files/complications.asked`, the stamp `TodayTileService` writes on every Tile
request, since it deleted only a fixed list of the app's files; `state.sh` recorded the APK's
install path, which every `install -r` changes, beside the sha1 that already names it; and its alarm
line counted the alarm manager's removal history, which keeps a line for the alarm a force stop
removes, so it read 1 where 0 was found. The last bullet of "Minors left" above is answered here.

- `pullfound.sh` now records every file in the app's `files/` and `shared_prefs/` by name
  (`found/<folder>.list`) and keeps each (`found/<folder>/`); `restore.sh` writes each back byte
  for byte and removes every other, and a folder found empty or absent is removed. A found
  `shared_prefs` is now put back, where it was deleted unsaved before (the code lens's minor).
- `state.sh` names the app by its version and its APK's sha1, not its path, and counts the app's
  pending alarms alone (`Alarm{… world.taqwa.app}`).
- **On emulator-5558** (31 Oct, 11:18 to 11:24 SAST, under `wear-5558`; found off, booted, stopped
  by serial): found the app `2b2d77c9` (100038), the face `d4f50b1d`, the feed `0b3f92ef` (en),
  `feed.json`, `nudge.anchor` and `profileInstalled`, no `shared_prefs`, no pending alarm, no Tile.
  The debug build of `9e372c2a` (`832eaa68`), the Tile added and shot ("DHUHR in 1 h 11 min",
  Cape Town), which left `complications.asked` and the daily nudge's alarm. The first
  `restore.sh`, with the old alarm line, differed in that line alone (the removal history's line
  for the alarm its force stop removed); `state.sh` with the line corrected matched the record. Then
  from a fresh `pullfound.sh`, the Tile again and `restore.sh`: `STATE AS FOUND`; and again with
  leftovers written by hand (`files/sample.held`, `shared_prefs/tasbeeh.xml`, a changed
  `feed.json`): `STATE AS FOUND`. Each record also equals the one taken at 11:19:59, before
  anything changed. The face left as found (`found/left-as-found.png` against `face-found.png`: Arch, Amber,
  the same rows but the minute).
