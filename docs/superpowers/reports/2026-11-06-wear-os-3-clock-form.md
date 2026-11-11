# Ruling 72 items 1, 2 and 7 (Wear): Wear OS 3's clock form and the sources' log gate

Lane W2, on `watch-wear-fix`, 6 November 2026. Base `6f8e2cb7` (`git merge --ff-only
watch-companion`: already up to date; the worktree was new, with no WIP from an earlier run). This
is not a plan task. It is Ruling 72 items 1, 2 and the Wear parts of item 7, from the ledger
(6 Nov ~20:55). Nothing is pushed. Commits use the Taqwa identity and carry no trailer. The version
is not bumped: the watch ships as 1.6.0 (42), and the cut is the Lead's.

| Commit | Subject |
|---|---|
| `8e10a072` | Wear: Wear OS 3's complications say the prayer and its time, the sources' timeline lines are written only on request, and clock.sh holds an old watch's clock (Ruling 72) |
| this report's | Docs: Wear OS 3's clock form and the sources' log gate, on the old watches (Ruling 72) |

`8e10a072` folds the lane's WIPs (`f70ec8c9`, `9c21dd4e`, `56dde660`, `41668bfe` and `80677818`)
by a soft reset, and its tree is the tree of `80677818`, the last one tested. The devices ran
debug builds of `56dde660` (the 5-minute pairs) and `80677818` (a final read-back). Between those
two, the app changed only where two API checks went back to the form lint reads, and the result
on the watch was the same.

The evidence is in [`2026-11-06-wear-os-3-clock-form/`](2026-11-06-wear-os-3-clock-form/) (`logs.txt`
and three shot strips). The full captures are in the session scratchpad (`r72w/`).

## What changed

| Item | Files | What it does |
|---|---|---|
| 1 | `NextPrayerSource.kt`, `SpokenLine.kt`; test `ClockFormTest` (new); `SpokenTest` and `ComplicationFieldsTest` now name API 33 | Below API 33, NextPrayer's long text is the prayer's time (`ASR` over `16:10`), and every line a screen reader gets is the prayer and its time (`Asr 16:10`). The picker's preview matches. From API 33 nothing changes. |
| 2 | `ComplicationSources.kt`, and its four callers (`NextPrayerSource`, `DatesSource`, `DaySource`, `DayDialSource`); test `TimelineLogTest` | The `steps, last until <UTC instant>` line is written only while `Log.isLoggable("TaqwaComplications", Log.DEBUG)`, in every build, the debug build too. The KDoc says why and what is true. |
| 2 | plan 5 Task 12 Step 2, Task 13 check 7 (one line each) | `setprop log.tag.TaqwaComplications DEBUG` comes before an observation's capture. |
| 7 | `wearApp/build.gradle.kts`, `WristCompass.kt` (`qiblaLogOn`'s KDoc and the collection's comment) | The comments now say what is true (below). |
| 7 | `tools/watchface/clock.sh`; test `clock-test.sh` (new); `tools/watchface/README.md` | `WATCH=emulator-5566` or `WATCH=emulator-5568` drives the Wear OS 4 or Wear OS 3 emulator. On those watches the script turns off the watch's own time sync and puts it back as found. |

## Item 1: the clock form below API 33 (call 6 = (b))

**The code.** `NextPrayerSource.answer` reads `Build.VERSION.SDK_INT` once and passes it to three
pure functions. Each one branches on `evaluatesDynamicValues(sdk)` (`sdk >= TIRAMISU`):
- `nextPrayerLongText(fields, feed, secondsLeft, sdk)`: the countdown from API 33. Below it, the
  prayer's time.
- `nextPrayerSaid(…, sdk)`: the spoken sentence from API 33. Below it,
  `ComplicationFields.prayerSpoken(fields)`, the words the short text has always said, in the
  phone's case and with a twelve-hour phone's day period.
- `nextPrayerPreview(name, time, countdown, sdk)`: the picker's preview, in the same two forms.

The ring and the long text are made from these. `SpokenLine.complicationText()` and the ring's
dynamic builder keep the `Build.VERSION.SDK_INT >= TIRAMISU` guard that lint reads. A first
version passed `sdk` there too, and lint found two `NewApi` errors in it. Below API 33 a line holds
words alone, so it becomes plain text either way. The SHORT_TEXT form was already the clock form
and is unchanged.

**Tests first, at API 30 and API 33.** The order was: the test, a compile failure, scaffolding
that kept today's behaviour, the red, then green.
- `ClockFormTest` has seven cases. The four at API 30 failed for the reason the ruling gives:
  - expected `16:10`, was `in 1 h 12 min`;
  - expected `Asr 16:10`, was `Asr in 1 hour 12 minutes, at 16:10`;
  - over every step of two days, expected `4:33`, was `in 4 h 3 min`;
  - the preview, expected `16:10` and `Asr 16:10`.
- The cases at API 33, and every case in `SpokenTest` and `ComplicationFieldsTest` (now called
  with `sdk = 33`), passed before and after.
- The two-day case covers the four samples (en, en12, ar-ly12, ar-sa), each also as a feed from
  before the spoken words. It checks at each step's start, 7 minutes in and 61 minutes in.

*Erratum resolved.* The brief says "Robolectric @Config sdk, as the module's tests already do".
`:wearApp` has no Robolectric: there is no dependency and no `@Config`, and two tests say in
comments that they avoid it. Adding it would bring in a new library, which needs plan 2's three
checks. So the API level is a parameter of the pure functions. The tests call them at 30 and 33,
the same branch points the source takes.

**The ring below API 33 is left as it is, and why.** Stepping it with shorter entries does not
fit the existing timeline budget. `ComplicationTimelinesTest.aTimelineIsAFewEntriesAndNotAMonthsWorth`
holds every plan to 2–16 entries. The next-prayer plan already uses 13: ten periods, two
midnights and the empty step. It is one plan for all three types (Ruling 28). One extra ring step
per period would make it 23. A ring that moved every 10 minutes would need about 290 entries over
the two days. So the ring stays at its value at the entry's start, which on Shapes is an empty
track for the whole period. Nothing in it is false in words; it is a picture that does not move.
The KDoc of `NextPrayerSource` now says this.

**Wear OS 3 on `emulator-5568`, as P5.12 Step 1 ran it.**
- Setup:
  - the AVD was started with `scripts/wear-old-emulator.sh 30`, which isolates the watch at boot.
    The script is Lane W's (watch-wear, blob `51d52c87`), run from the scratchpad because it is
    not on `watch-companion` yet;
  - the debug Wear app from this worktree, with the stored sample;
  - NextPrayer `RANGED_VALUE` on Shapes;
  - the clock set to 19:02:30 on the mock day with this branch's
    `WATCH=emulator-5568 tools/watchface/clock.sh` (which sets `clockwork_auto_time 2`), and the
    sample stored then;
  - the watch taken off its virtual charger. Otherwise Wear OS 3's charging screen covers the
    face, and the face never selects its next entry. The first two runs were void for that
    reason, and are kept in the scratchpad only.
- The read-back: `dumpsys` of the face's service, with `log.tag.ComplicationData DEBUG` set only
  for the dump and unset after, so that the face prints its data unredacted.

| Build | 19:04:11, the entry's start | 19:09:11, 5 minutes later |
|---|---|---|
| base `6f8e2cb7` | `ISHA` / `20:24`, description **"Isha in 1 hour 20 minutes, at 20:24"**, ring 391.0 of 391–471 | `ISHA` / `20:24`, **"Isha in 1 hour 19 minutes, at 20:24"**, 391.38 |
| this branch | `ISHA` / `20:24`, description **"Isha 20:24"**, ring 391.0 | `ISHA` / `20:24`, **"Isha 20:24"**, ring 391.0 |

- On the base build, the daily nudge asked the sources again at 19:04:23 (`asked again (daily)`,
  in `logs.txt`). That is why its second read-back is "1 hour 19 minutes". That entry's words are
  those of 19:04:23, frozen since: 1 hour 14 minutes were left at 19:09:11.
- On this branch, every entry of the timeline the face holds is in the clock form: "Fajr 4:33",
  "Dhuhr 12:33", "Asr 16:10", "Maghrib 19:04" and "Isha 20:24", across the two days. Each value is
  its entry's start.
- The final tree's build (`80677818`) reads back the same at 19:04:11.
- The strips are `api30-base-ring.png` and `api30-clock-form-ring.png`. The drawn words are the
  same in both: "ISHA 20:24" over an empty ring. The difference is in what a screen reader is
  given.

The long text cannot be shown on this device. As Step 1 found, no system face on these images has
a slot that takes `LONG_TEXT`. Its clock form is held by `ClockFormTest` alone.

**Wear OS 4 on `emulator-5566`: the API 33 path is unchanged.** The same build, face and moments:
- before the start, "Maghrib in 1 minute, at 19:04" (389.67);
- at the start, "Isha in 1 hour 19 minutes, at 20:24" (391.18);
- 5 minutes later, "Isha in 1 hour 14 minutes, at 20:24" (396.18).

The ring and the description move by themselves (`api33-unchanged-ring.png`: 22.5° of red at
19:09:11).

## Item 2: the sources' `last until` lines only on request

**The gate.**
- `timelineLog(tagEnabled, source, type, steps, lastUntil): String?` returns null while the tag
  is off.
- `logTimeline(source, type, plan)` reads `Log.isLoggable(COMPLICATION_TAG, Log.DEBUG)` at every
  answer and writes the line at `Log.d`. The four sources log through it.
- `timelineLogLine` is now private, so no source can write the line ungated. Plan 5's Interfaces
  line (Task 12) still calls it internal; see "For the Lead".
- The two lines that carry no instant stay as they were: `asked again (<why>)` at `Log.i`, and the
  warnings.

The KDoc now says what is true. A prayer's start, with its date, says roughly where the wearer is.
Anything that reads the watch's log could read it, and a bug report taken from the watch carries
the log whole. So the line is written only on request, as the compass's line is on a store build.
That keeps the promise that only the wearer's own devices see where they are (Ruling 39).

**Test first.** `TimelineLogTest.theLineIsWrittenOnlyWhileItsTagIsEnabled` failed against
scaffolding that wrote the line always (`expected null, but was: <next prayer, RANGED_VALUE: 10
steps, last until 2026-11-02T03:58:00Z>`). It passes on the gate. The example lines are
unchanged.

**On Wear OS 3 (the debug build), the same sample stored three times:**
- with the tag unset (as found), only `asked again (feed)` is logged;
- after `setprop log.tag.TaqwaComplications DEBUG`, `next prayer, RANGED_VALUE: 13 steps, last
  until 2026-11-08T18:24:00Z` follows it;
- at `INFO`, only `asked again (feed)` again.

The face's own history shows it was given data at 21:12:59, 21:13:09 and 21:13:19, so the source
answered all three times and only the tag decided the line. With the tag unset, Wear OS 4 logged
no line either. The tag was put back unset. **The running observation was not touched.** Its
build on `emulator-5558` is the ungated staging one, as Ruling 72 item 2 expects.

**Plan 5's text, only the two lines named:**
- Task 12 Step 2 now runs `adb -s emulator-5558 shell setprop log.tag.TaqwaComplications DEBUG`
  before the fresh feed. The first request's line is the one the step records.
- Task 13 check 7 gains one line: a capture of the sources' lines on the candidate starts with
  the setprop to DEBUG and ends with the setprop to INFO.

## Item 7 (Wear): three wordings and `clock.sh`

- **`wearApp/build.gradle.kts`:** the staging comment now says what staging takes from release: no
  feed dropped, no layout lines from the Qibla page, and the compass's line only on request (its
  tag set to DEBUG, `qiblaLogOn`), as on a store build. The old comment said "no compass log".
- **`qiblaLogOn`'s KDoc and the comment where `WristCompass` reads it:** only the setprop to DEBUG
  waits for a fresh open of Qibla. A lower level stops the line at once, in every build, because
  the system drops a line below its tag's level as it is written. Seen on Wear OS 3 with the `log`
  command, which goes through liblog as `Log.d` does:
  - at `INFO`, the debug line was not written;
  - at `DEBUG` it was, and with the tag unset it was too.

  The probe tag was put back unset.
- **`clock.sh`,** test first: `clock-test.sh` uses a fake adb and no device, on `store-test.sh`'s
  pattern.
  - Today's `clock.sh` failed 10 of the test's checks. With `WATCH=emulator-5568` it drove
    emulator-5558, the face's watch. The Wear OS 4 and `auto_time` checks, added after the run
    below, failed 5 against the first version before the second passed.
  - Now `WATCH=` names `emulator-5566` or `emulator-5568`, each only as its own AVD. Any other
    serial exits 2 and touches nothing. Wear OS 6 makes its old calls and one more, a read of
    its API level (`getprop ro.build.version.sdk`, which tells the watches apart), and
    `clockwork_auto_time` is never read there. (Corrected by Ruling 74 item 7: this line first
    said "exactly its old calls".)
  - On the old watches it records `<clockwork_auto_time> <auto_time>` as found, on the watch
    (`/data/local/tmp/taqwa-clockwork-auto-time`; a second set keeps the first record). It then
    sets `clockwork_auto_time 2`. `auto` puts both back, deletes a setting that was not there,
    sets the host's time and forgets the record.
  - **Beyond the ruling's words: Wear OS 4 too.** The first Wear OS 4 run had `auto_time 0` alone.
    The watch's `TimeSyncManager` moved the set clock by NITZ at 19:05:08, 2.5 minutes in ("Time
    changed by +19d2h42m"). Wear OS 3 did the same in Step 1. So the rule is "below API 34", which
    covers both AVDs, and the second run held the clock. `clock-test.sh` holds both watches.
  - **`auto_time` as found.** Both old images boot with `auto_time 0` and `clockwork_auto_time 0`,
    so `auto` restores those rather than setting `auto_time 1`. Wear OS 6 keeps `auto_time 1`,
    which is what plan 1 leaves there.
  - The script was used for every clock change in this run. In the last run on 5568, with the
    final script, the settings went from `0 0` to `2 0` and back to `0 0`, and the record was
    removed. 5566's went the same way.

## Plan errata and the brief's notes

- **1** The fast-forward was already up to date.
- **2** Done exactly as written. `watch-wear` was read only through git objects (its
  `wear-old-emulator.sh` and the Step 1 report), and never checked out or changed.
- **3** Item 1 is done. Robolectric is replaced by the API-level parameter (above). The ring is
  left, and the reason is recorded. The device proof was done on 5568, then 5566.
- **4** Item 2 is done, with the gate test and the two plan lines.
- **5** Item 7 (Wear) is done. `clock.sh` was widened to Wear OS 4, as seen.
- **6** The commit subjects end "(Ruling 72)". This report.
- **7** None of the 30 entries under `plan-errata-2026-10-24.json` key p5 touches this task. #8
  (wear-sdk not required) is already built: `wear-release-check` passes it. #23 (QiblaInk's API 30
  path) belongs to Step 1 and was not repeated.
- **8** No face file changed, so the face generators' `--check` and the lit share are unaffected.
  No test vector reaches an APK: `wear-release-check` passes release and staging. No coordinate or
  bearing is logged.
- **9** No string file, no draft marker and no `watch-feed` file was touched.
- **10** No version bump.
- **12** Only 5568 and 5566 were used, each under its lock, booted with the isolating script, and
  stopped by serial. 5558 and 5562 were never addressed. adb's server was never restarted.
  `KB2604110095`, 5572, the iPhone 17 Pro Max simulator and the work iPhone 12 were not touched.
- **13** No flake was seen (see the checks).

## Checks

- `./gradlew :wearApp:testDebugUnitTest`: 599 tests, 0 failures (598 before the gate test). Run
  after each step.
- `tools/watchface/clock-test.sh`: PASS, 19 checks.
- Two mutants, in a scratch copy of the tree: dynamic values from API 30 (`sdk >= R`) fails 5 of
  `ClockFormTest`'s 7 cases, and `clock.sh`'s sync rule at "below API 33" fails `clock-test.sh`'s
  2 Wear OS 4 checks. `logTimeline`'s own `Log.isLoggable` read is Android's, so it is held on the
  watch (item 2) and not on the host.
- `swift test` (WatchCore), the timetables compile and `tools/i18n-check.py` (`i18n: clean`), and
  `tools/watchface/store-test.sh`: PASS. `lib.sh` is unchanged.
- `./gradlew :wearApp:lintDebug :wearApp:lintRelease -Ptaqwa.unsignedRelease=true`: "No issues
  found."
- `tools/wear-release-check.sh release` and `staging`, after `assembleRelease` and
  `assembleStaging` with `-Ptaqwa.unsignedRelease=true`: PASS. The APKs are 4.3 and 4.7 MB, hold
  exactly the permissions in `permissions.txt`, have no sample or test vector, are not debuggable,
  and do not require wear-sdk.
- `scripts/test-unsigned-release.sh` (this run edited `wearApp/build.gradle.kts`): PASS. Both
  release builds are unsigned beside the decoy, and without the property they fail on the decoy's
  store file. The decoy was removed after.
- `scripts/test.sh` with the host suites (`--rerun-tasks`): exit 0. The versions agree, the
  bump, privacy and listing checks pass, and the suites ran shared 2427, widgetcore 111, watchcore
  233 and wearApp 599, with 0 failures.
- `scripts/test.sh`, whole, on `8e10a072` under `ios-sim-tests` (22:22–22:27, after the Lead's
  run): exit 0 in 284 s, first run, with no EOFException. `swift test` passed. Shared ran 2403 on
  the iOS simulator and 2427 on the host, widgetcore 109 and 111, watchcore 200 and 233, and
  wearApp 599, with 0 failures.

## For the Lead

1. **The support answer's Wear OS 3 line** stays, as ruled: "On Wear OS 3 a complication's
   countdown moves only at each prayer". Under (b), the complications there show no countdown,
   only the prayer and its time, and the ring moves only at each prayer. The line is still not
   false, but it now describes the ring alone. If it is to change, it is a strings and site edit
   after M8 lands (string files are frozen for this wave).
2. **Plan 5's call 6 and Review Focus 3** still describe (a): "the complications' countdown is
   fixed for each prayer period". Task 12's Interfaces line still calls `timelineLogLine`
   internal. This run edited only the two setprop lines it was asked to.
3. **Wear OS 4's time sync.** `clock.sh` covers Wear OS 4 as well as Wear OS 3, which goes beyond
   the ruling's words, because the clock was seen to move there.
4. **A put-back can be lost at an `emu kill`.** 5568 was found at `auto_time 0` and
   `clockwork_auto_time 2`, where Step 1 had put the setting back to 0. The settings provider
   writes to disk a moment later. Each stop here waited at least 5 s after the last `settings
   put`, and the values were read back before the stop.
5. **The base build's daily nudge** logged `NUDGE armed in 0s found=true stopped=true` after the
   install and the clock moved back 19 days, and fired at 19:04:23. This is an artifact of moving
   the clock back, in P2.6's code. It was not looked into further.

## The devices, put back

- **`emulator-5568`** (Taqwa_Wear_OS_3): taken three times, 21:11–21:44, 21:57 and 22:02–22:05,
  each under `wear-5568`.
  - Left at automatic time as the image boots (`auto_time 0`, `clockwork_auto_time 0`), with no
    record file, font scale 1.0, en-US, TalkBack off, on Shapes, and every log tag unset.
  - It holds this branch's debug build (the tree of `8e10a072`) with today's sample. It was found
    holding Lane W's debug build of `watch-wear`.
- **`emulator-5566`** (Taqwa_Wear_OS_4): taken 21:44–21:57 under `wear-5566`.
  - Left as found: `auto_time 0`, `clockwork_auto_time 0`, font scale 1, TalkBack off, on Shapes,
    log tags unset.
  - It holds this branch's debug build of `56dde660` with today's sample.
- Both were taken off their virtual charger by the console while up. That resets at the next
  boot. Both were stopped with `adb -s <serial> emu kill` and their locks released.

# Ruling 74 items 1–8 (Wear): Wave 8's Wear minors

Lane W3, on `watch-wear-fix`, 6 November 2026, from about 23:06 SAST. Base `ce3f9acd` (`git
merge --ff-only watch-companion`: already up to date; no WIP from an earlier run). This is not a
plan task. It is Ruling 74 items 1–8, from the ledger (6 Nov 23:20). Ruling 74 names no report for
its Wear items, so they are written here, in the lane's report of 6 November. Nothing is pushed.
Commits use the Taqwa identity and carry no trailer. The version is not bumped. No device was
used.

| Commit | Subject |
|---|---|
| `87dca096` | Wear: the complications join the prayer and its time with the phone's comma, and their API levels and log gate are held on Robolectric and the host (Ruling 74) |
| this section's | Docs: Wave 8's Wear minors, on the host and on Robolectric (Ruling 74) |

`87dca096` folds the lane's WIPs (`226992f0`, `73d8d0b0`, `e03b913d` and `02ec1545`) by a soft
reset. Its tree is the tree of `02ec1545`, the one tested.

## What changed

| Item | Files | What it does |
|---|---|---|
| 1 | `ComplicationFields.kt` (`prayerSpoken`, `spokenSeparator(rtl)`), `NextPrayerSource.kt` (the preview); tests `SpokenTest`, `ClockFormTest`, `ComplicationFieldsTest` | Every spoken "prayer and time" on the Galaxy complications is the name, the phone's comma and the time, at every API level. Only the comma is new, and only in what is said. |
| 3 | `ComplicationSources.kt` (`logTimeline`); tests `TimelineLogTest`, `NextPrayerSourceTest` (new, `src/testDebug`); `gradle/libs.versions.toml`, `wearApp/build.gradle.kts` | `logTimeline` takes its tag reader and its writer. `NextPrayerSource` itself runs on Robolectric at API 30 and 33. |
| 4 | `NextPrayerSource.kt`'s KDoc | It names the 2–16-entry budget and why the budget exists. |
| 5 | plan 5 (7 places), spec 2.3, and one example in spec 3 | They say what was built. |
| 7 | `tools/watchface/clock.sh`'s header, `clock-test.sh` (header and 3 checks), this report's line on Wear OS 6 | They say what `clock.sh` reads, and what `auto` does with nothing recorded. |
| 2, 6, 8 | none | Each stays as it is, for the reason below. |

## Item 1: the prayer, the phone's comma and the time

**The code.** `prayerSpoken(fields)` was `"${name} ${spokenTime}"`. It is now `name +
spokenSeparator(fields.rtl) + spokenTime`. `NextPrayerFields` already carried `rtl` (the feed's
`ui.rtl`), so nothing had to be threaded. The smallest change was a `spokenSeparator(rtl: Boolean)`
overload, which `spokenSeparator(ui)` now calls, so the rule (`، ` right to left, else `, `) is
written once. (True since Ruling 76 item 1: `TodayModel.kt`'s `spokenSeparator(rtl)`, which the
Tile imports too, held a second copy of it until then, and now calls this one.) The forms that
change:
- the short text's description, at every level: "Asr, 16:10";
- below API 33, the ring's and the long text's descriptions (the clock form): "Asr, 16:10";
- from API 33, a feed written before the spoken words: "Asr, 16:10, in 1 h 12 min".

A feed with the spoken words says the phone's sentence from API 33, "Asr in 1 hour 12 minutes, at
16:10", as before. The title, the text and the ring are untouched, so nothing drawn changes.

**The preview follows.** `ClockFormTest.thePickersPreviewShowsAndSaysWhatTheSlotWill` holds that
the picker's preview says what the slot will say. So `nextPrayerPreview` now joins its own words
the same way: "Asr, 16:10", and from API 33 "Asr, 16:10, in 1 h 12 min". The short text's preview
takes the same words (`prayerSaid`). The preview's words are the app's resources, in the watch's
language, not the feed's. So its comma comes from the resources' language:
`previewRightToLeft(resources.configuration.locales[0].language)`, true for `ar` and `ur`, the two
of the seven folders written right to left. No string resource changed.

**Tests first.** First the tests, then a compile failure, then scaffolding that kept today's
words, then the red. Nine cases failed, each for the missing comma:
- `ComplicationFieldsTest.aTwentyFourHourFeedIsSaidAsBefore`: expected `Asr[,] 16:10`, was
  `Asr[] 16:10`;
- `ComplicationFieldsTest.aTwelveHourPhoneSaysItsDayPeriod`: expected `Asr[,] ⁨4:10 PM⁩`;
- `SpokenTest.theNamesAreThePhonesAndNeverTheFacesCapitals` and
  `aFeedWrittenBeforeTheSpokenWordsSaysTheScreensWords` (`Asr[,] 16:10, in 1 h 12 min`; the Saudi
  feed's `العصر، ١٦:١٠، بعد ١ س ١٢ د`);
- `SpokenTest.thePrayerAndItsTimeAreJoinedByThePhonesCommaInEveryLanguage` (new): expected
  `Fajr[,] 4:33`;
- `ClockFormTest`: the reader on Wear OS 3 (`Asr[,] 16:10`), the API 33 screen's words, the
  preview, and the new `thePreviewsCommaIsTheResourcesLanguages` (`ar`).

The new `SpokenTest` case runs the phone's seven languages on both clocks (`PhoneFeeds.ALL`, 14
phones) at five moments. It holds three things:
- the separator is `، ` exactly where the language is Arabic or Urdu, and `feed.ui.rtl` agrees;
- the line is the name, that separator and the spoken time, the function's own form (that the
  words are the ones said before is held by the literal lines above, `Asr, 16:10`, not here);
- below API 33 it is what the ring and the long text say.

The cases at API 33 and the two-day sweeps follow `prayerSpoken`, and passed before and after.
`:wearApp` went from 601 to 603 tests, all green, at API 30 and 33 alike, because the functions take
the level.

**Left as it is: the day dial's line.** `dialSpoken` (slot 7, and DaySource's
`preview_day_times` string) says "Fajr 4:33, Sunrise 6:01, …": six pairs, each joined by a space,
and the pairs joined by the phone's comma. The ruling's formula names `prayerSpoken`. A comma
inside each pair as well would make the list twelve items, and its preview twin is a string
resource in seven languages, which this wave may not touch (Ruling 73 is editing those files). It
is for the Lead (below).

## Item 2: Wear OS 3's long text keeps `fields.time`

No change, as ruled. Below API 33 the long text is `fields.time`, as the ring's and the short
text's text are, with no day period (`4:10` on a twelve-hour phone), so the three types show the
time in one form. The spoken line has the day period (`spokenTime`).

## Item 3: the two wiring gaps, held

**(a) The gate at the call.** `logTimeline(source, type, plan, tagEnabled = { Log.isLoggable(
COMPLICATION_TAG, Log.DEBUG) }, write = { Log.d(COMPLICATION_TAG, it) })`. The sources pass
nothing, so their behaviour is unchanged. The host test
`TimelineLogTest.aSourceWritesItsLineOnlyWhenTheTagReadsEnabledAtThatAnswer` passes its own reader
and writer. With the reader false, nothing is written; with it true, the line is written. The
reader is asked once at each answer.
- It was first wired exactly as the ruling's mutant, `timelineLog(true, …)`, and was red:
  `expected:<[]> but was:<[next prayer, RANGED_VALUE: 3 steps, last until
  1970-01-01T00:05:00Z, day: 3 steps, last until 1970-01-01T00:05:00Z]>`.
- With `timelineLog(tagEnabled(), …)` it is green.

**(b) The source's own API level, on Robolectric.** `NextPrayerSourceTest` (`src/testDebug`,
since it stores the debug build's own sample for today with `SampleFeed.install`) runs
`NextPrayerSource` itself:
- its application is `TaqwaWearApp` with no start work (no nudge, handshake or face refresh);
- it is driven through `onComplicationRequest` and `getPreviewData`;
- each entry of the real timeline is checked against the rules at its own start, and the default
  at the second before the first entry, so a prayer that passes during the run cannot move an
  answer.

| Case | `@Config` | Holds |
|---|---|---|
| `onWearOsThreeTheRingAndTheLongTextSayThePrayerAndItsTime` | `sdk = [30]` | en and ar-sa: no dynamic value on the ring; the long text is `fields.time`, plain; every description is `prayerSpoken` |
| `onWearOsThreeThePickersPreviewShowsAndSaysTheClockForm` | `sdk = [30]` | "16:10", "Asr, 16:10" for the ring, long and short |
| `anArabicWatchsPreviewIsJoinedByTheArabicComma` | `sdk = [30], qualifiers = "ar"` | "العصر، 16:10" |
| `onWearOsFourTheRingAndTheLongTextCountDown` | `sdk = [33]` | en and ar-sa: a dynamic ring; the long text and the descriptions evaluated at each entry are the rules' countdown and spoken sentence |
| `onWearOsFourThePickersPreviewCountsDown` | `sdk = [33]` | "in 1 h 12 min", "Asr, 16:10, in 1 h 12 min" |
| `theSourceLogsWhereItsTimelineEndsOnlyWhileItsTagIsEnabled` | `sdk = [30]` | the default reader: no `last until` line while `Log.isLoggable(…, DEBUG)` is false; one at `Log.d` after `ShadowLog.setLoggable(…, DEBUG)` |

The mutants, one at a time, in this tree, each put back with `git checkout`:

| Mutant | Red |
|---|---|
| `answer()`: `val sdk = 33` | `onWearOsThreeTheRingAndTheLongTextSayThePrayerAndItsTime`: expected `Fajr[,] 4:33`, was `Fajr[ in 5 hours 10 minutes, at] 4:33` |
| `getPreviewData`: `33` for `Build.VERSION.SDK_INT` | both API 30 preview cases: expected `Asr, 16:10[]`, was `Asr, 16:10[, in 1 h 12 min]` (and the Arabic) |
| the preview's `rtl = false` | `anArabicWatchsPreviewIsJoinedByTheArabicComma`: expected `العصر[،] 16:10`, was `العصر[,] 16:10` |
| `logTimeline`'s default reader `{ true }` | `theSourceLogsWhereItsTimelineEndsOnlyWhileItsTagIsEnabled`: expected `[]`, was `[next prayer, RANGED_VALUE: 13 steps, last until 2026-11-08T18:24:00Z]` |
| `answer()`: `val sdk = 30` | `onWearOsFourTheRingAndTheLongTextCountDown`: expected `Fajr[ in 4 hours 33 minutes, at] 4:33`, was `Fajr[,] 4:33` |

**The new library.** Wave 8 replaced Robolectric with a level parameter, because it would be a new
library. Ruling 74 asks for it, so it is added as `org.robolectric:robolectric` 4.17, the latest
stable (10 Sep 2026; 4.18 is a beta), as `:wearApp`'s `testImplementation` alone. It comes with
`testOptions.unitTests.isIncludeAndroidResources = true`, so that the source's previews read the
app's own strings. Plan 2's three checks:
- the `debugRuntimeClasspath`, `releaseRuntimeClasspath` and `stagingRuntimeClasspath` dependency
  trees are byte-identical before and after, so no APK's manifest, permissions or dex can change;
- `wear-release-check` passes release and staging, with exactly `permissions.txt`;
- the unit-test classpath names no Firebase, analytics, measurement, ads or crash-reporting
  artifact.

The unit test now also merges the debug assets (the samples among them), its manifest and its
resources: seven tasks more (the face's debug build was already in its graph). Robolectric
fetched Android's `android-all` for API 30 and 33 into `~/.m2` (289 MB) at the first run. CI
fetches them too: it has the network.

## Item 4: the ring's budget, named

`NextPrayerSource`'s KDoc now says:
- the timeline's budget is 2 to 16 entries
  (`ComplicationTimelinesTest.aTimelineIsAFewEntriesAndNotAMonthsWorth`);
- the next-prayer plan takes 13 of them (ten periods, the night's midnight steps and the empty
  one), so one more step a period (23) would pass it;
- the budget exists because, from API 33, each entry carries its own copy of the dynamic countdown:
  a few kilobytes of protobuf in the text or value, and again in the description
  (`SpokenTest.theSpokenSentenceIsSmallEnoughToSendTenAtATime`);
- one plan serves every type and every level (Ruling 28).

## Item 5: plan 5 and the spec catch up

Only these lines were touched. Lane P3's Task 13 Step 2 (the phones' checklist) was not.

| Where | Now |
|---|---|
| Review Focus 3 (:139) | Below API 33 the complications show no countdown, as call 6 ruled ((b)). Its tests add `ClockFormTest` and `NextPrayerSourceTest`. |
| Call 6 (:154, and a new :158) | The stale `NextPrayerSource.kt:44-46` becomes the KDoc and `evaluatesDynamicValues`. A "Ruled: (b)" bullet says what was built, the ring's budget, and that the support line stays. |
| Task 10 (:889) | The Wear OS 3 line records Ruling 72's keep and Ruling 74 item 6, in place of "(b) rewrites this line". |
| Task 12 Interfaces (:945) | The private `timelineLogLine`, reached only through the internal `timelineLog` (null while the tag is off) and `logTimeline` (its reader and writer). |
| Task 12 Step 0's Expected (:960) | The `setprop … DEBUG` first, and the tag put back unset after. |
| Task 12 Step 2 (:977) | It ends with `getprop log.tag.TaqwaComplications`, which still prints `DEBUG` (a reboot clears it, so a missing line would not mean no request), then `adb -s <watch> shell 'setprop log.tag.TaqwaComplications ""'` and the same `getprop`, which prints an empty line. |
| Task 13 check 7 (:1052) | The same ending, in place of `setprop … INFO`. |
| Spec 2.3 (:214) | One "Wear OS 3" bullet: the clock form, the ring inside the budget, and API 33 unchanged. |
| Spec 3 (:415) | The example of a feed without the spoken words becomes "Dhuhr, 12:33, in 7 h 10 min". It is item 1's change, and the line would otherwise be false. |

"Unset" is `adb -s <watch> shell 'setprop log.tag.TaqwaComplications ""'`, the state the tag was
found in on 5568 (`log.tag.TaqwaComplications=""`, `logs.txt` section 2). The quotes go round the
whole command (review round 1's fix, at the end of this report).

## Item 6: the support answer's Wear OS 3 line stays

No change. It is Mohamed's text, approved at M8 in seven languages
(`site/pages/*/support.html`). It is true of the ring, whose countdown still moves only at each
prayer, while the text forms show the prayer's time.

## Item 7: what `clock.sh` reads, and `auto`'s fallback

- **`clock.sh`'s header** now says two things:
  - with nothing recorded, `auto` falls back to automatic time on, `auto_time 1`, and leaves
    `clockwork_auto_time` as it is;
  - on Wear OS 6, the one call added reads its API level (`getprop ro.build.version.sdk`), which
    tells the watches apart.
- **`clock-test.sh`'s header** says the same, and three new checks hold it:
  - Wear OS 6's set makes exactly the old calls plus the getprop;
  - so does its `auto`;
  - on Wear OS 3, `auto` with nothing recorded sets `auto_time 1`.
- They pass on today's `clock.sh` (22 checks, from 19), and two mutants, each put back after, are
  red:
  - with no fallback (`auto` with no record sets nothing), 3 checks fail: Wear OS 6's `auto`
    leaves `auto_time 0`, its calls lack the setting, and Wear OS 3 is left at 0;
  - with one more call on every watch (`getprop ro.product.model`), Wear OS 6's two call checks
    fail.
- **This report's** "Wear OS 6 makes exactly its old calls" (above, item 7 of Ruling 72) now says
  "its old calls and one more, a read of its API level", with a note that Ruling 74 corrected it.
- Wear OS 4's coverage is accepted, as ruled. The `scripts/wear-old-emulator.sh` reference in
  `clock.sh` becomes true when P5.12 (`watch-wear`) lands. Nothing was changed for it here.

## Item 8: "armed in 0s" after the clock moved back

By design, as "For the Lead" 5 above asked to settle. `DailyNudge.arming` takes an anchor more
than `STALE` (a day and a half, `DailyNudge.kt:47`) from now, **either way**, as stale. It then sets
the alarm due now and anchored now (`Arming(0, now)`). On 5568 the base build's anchor was written
at the install on the real date, 6 November, and the clock was then moved back 19 days. So the
anchor was 19 days ahead of "now", stale, and the nudge was due at once: `NUDGE armed in 0s`, then
`asked again (daily)` at 19:04:23. `DailyNudgeTest.aNudgeUnheardForADayAndAHalfIsDueNow` already
holds it ("a clock set back by days"). No change.

## Plan errata and the brief's notes

- **1** The fast-forward was already up to date (`ce3f9acd`).
- **2** Ruling 74 items 1–8 exactly, with one addition and one omission, both stated:
  - added: the preview's comma (item 1, so the preview still says what the slot says) and spec
    3's example (:415);
  - left: the dial's line (item 1, for the Lead).

  `watch-wear` was not touched, and this section is in the lane's report of 6 November.
- **3** Item 1 is done, tests first and red then green. It holds at API 30 and 33: through the
  functions' level, and through the source on Robolectric (item 3).
- **4** Item 3 is done: (a) and (b), each mutant red (above).
- **5** Item 4 is done.
- **6** Item 5 is done, on the lines listed. Lane P3's Task 13 Step 2 was not touched.
- **7** Item 7 is done. Items 2, 6 and 8 need no change, as said.
- **8** The commit is "Wear: … (Ruling 74)". The checks are below.
- **9** `plan-errata-2026-10-24.json` key p5:
  - #19 (the ar/ur row joins) is the rule item 1 now makes the Galaxy complications follow;
  - no other entry touches this task (#23 and #24 are P5.12's).
- **10** No face file changed, so the face generators' `--check` and the lit share are not
  affected. No coordinate or bearing is logged. The debug assets, which now reach the unit test,
  hold only the four samples, as the debug APK does. `wear-release-check` passes, so no test vector
  reaches an APK.
- **11** No string resource and no `watch-feed` file was touched. No draft marker was added:
  `git grep "draft, to native review"` outside `docs/superpowers` prints nothing.
- **12** No version bump.
- **13** The WIPs were committed at each green step and folded.
- **14** No device, emulator or simulator UI was used, and no adb command was run. `scripts/test.sh`
  ran under `ios-sim-tests` (both names).
- **15** See the checks.

## Checks

- `./gradlew :wearApp:testDebugUnitTest --rerun-tasks`: 610 tests, 0 failures, 0 skipped, in
  119 s. That is 601 + 2 (item 1) + 1 (3a) + 6 (3b, Robolectric).
- `./gradlew :wearApp:lintDebug :wearApp:lintRelease -Ptaqwa.unsignedRelease=true`: "No issues
  found." (both).
- `tools/wear-release-check.sh release` and `staging`, after `assembleRelease` and
  `assembleStaging` with `-Ptaqwa.unsignedRelease=true`: PASS. The APKs are 4.3 and 4.7 MB.
- `scripts/test-unsigned-release.sh` (this run edited `wearApp/build.gradle.kts`): PASS.
- `tools/watchface/clock-test.sh`: PASS, 22 checks.
- `python3 tools/i18n-check.py`: `i18n: clean`. `git diff --check`: clean.
- `scripts/test.sh`, whole, on `87dca096` under `ios-sim-tests` (both names; 00:12–00:19 on 7 Nov,
  after a 39-minute wait behind the other lanes' runs): exit 0 in 442 s, first run, with no EOFException.
  `swift test` passed (286 and 19). Shared ran 2412 on the iOS simulator and 2436 on the host,
  widgetcore 109 and 111, watchcore 200 and 233, and wearApp 610, with 0 failures and 0 skipped.

## For the Lead

1. **The day dial's line** (slot 7, `dialSpoken`, and its `preview_day_times` in seven
   languages) still joins each prayer to its time with a space. Should it take the comma inside
   each pair too? That would be "Fajr, 4:33, Sunrise, 6:01, …". Its preview is a string change, so
   it would wait until Ruling 73's string edits land.
2. **The two watches' no-spoken-words fallback now differ by one comma.** The Galaxy says "Asr,
   16:10, in 1 h 12 min". The Apple Watch's `Glance.spoken` fallback says "Asr 16:10, in 1 h 12
   min" (`Glance.swift`, plan 1's form; Ruling 73 changed only `spokenWithoutCountdown`). Only a
   feed written before the phone sent its spoken words takes this form. The no-countdown form is
   one rule on both watches once Ruling 73 lands.
3. **The new test library.** `NextPrayerSourceTest` is the first Robolectric test in the project.
   The comments that say "no Robolectric here" (`:shared`, `widgetcore`, `wearApp`'s
   `AppStateTest`) are each about their own module or test, and stay true.

4. **The Lead's answers (Ruling 76, 7 November).** Item 1 above and the preview's comma are ruled
   in [Ruling 76](#ruling-76-items-17-wave-9s-wear-minors) below. Item 2, the two watches' fallback,
   is Ruling 75's and Ruling 77's in the ledger.

## Review round 1's fix: the line that puts the tag back

Both lenses found the same fault in item 5, and it is this lane's: the run used no adb (note 14 above),
so its line was never tried.

- **The fault.** `adb -s <watch> shell setprop log.tag.TaqwaComplications ""` does not put the tag
  back. The host shell makes `""` an empty argument, and `adb shell` joins its arguments with
  spaces, so the watch is sent `setprop log.tag.TaqwaComplications ` with the value gone. `setprop`
  prints its usage and exits 1, and the tag stays `DEBUG`. The line had replaced `setprop … INFO`,
  which worked, and the plan's `getprop` came before it, so nothing would have shown the failure.
- **What it would have cost.** At Task 13 check 7 this is his Galaxy, where the `last until` line
  would stay on until a reboot. That is the instant Ruling 72 item 2 gates for Ruling 39's promise.
  On an emulator it breaks the map's put-back rule.
- **The fix.** The three plan lines (:960, :977, :1052) and item 5's table and note above now write
  `adb -s <watch> shell 'setprop log.tag.TaqwaComplications ""'`, followed by a `getprop` that
  prints an empty line. Task 13 check 7 is not over while it still prints `DEBUG`. "Back to unset"
  stays, as Ruling 74 item 5 rules. `logTimeline`'s KDoc ("`… INFO` or a reboot") stays too, because
  `INFO` also ends the line.

**The evidence, kept apart by what each one is:**

| | The old line | The new line | Source |
|---|---|---|---|
| On a device: `emulator-5568`, API 30 | usage, exit 1, `getprop` still `DEBUG` | exit 0, `getprop` empty. With the tag empty, a stored sample logged only `asked again (feed)`; at `DEBUG`, the `last until` line too | The spec lens, under `wear-5568`, 7 Nov 00:29–00:33 |
| The real adb 1.0.41 client, on a local stand-in server | sends `setprop log.tag.TaqwaComplications ` | sends `setprop log.tag.TaqwaComplications ""` | This round, `logs.txt` section 6 |

- **This round booted no device,** because Ruling 74 says "No devices this wave". It took each
  command from the plan's own text, as written, and ran it in zsh and in bash with the real client.
  The stand-in's `setprop` follows AOSP's rule (exactly a name and a value, otherwise usage and
  exit 1), so its outcomes show what the client sends, and the device result is the spec lens's.
- **Not run:** the new line on Wear OS 6, Wear OS 4 or a Galaxy. `emulator-5558` holds the running
  observation. Those steps' own closing `getprop` is the check there.
- **No code changed,** so the code checks above hold: this commit touches plan 5 and this report.

## Ruling 76 items 1–7: Wave 9's Wear minors

Lane `watch-phone-fix`, 7 November 2026 (the Lead's ruling of 03:20 SAST, one lane for its 14
items, no devices, no visible words). The two decisions first, since they change no code:

- **Item 6: the day dial stays `Fajr 4:33, Sunrise 6:01, ...`.** This answers For the Lead 1. The
  commas separate the dial's pairs, and a comma inside each pair would flatten the list: "Fajr,
  4:33, Sunrise, 6:01" reads as names and times in a row, and no longer as pairs. `dialSpoken`
  (`ComplicationFields.kt`) and its `preview_day_times` are left as they are. Ruling 74 item 1's
  formula names `prayerSpoken` alone.
- **Item 7: the preview's first-locale comma stays.** `getPreviewData` reads
  `resources.configuration.locales[0].language`, while Android resolves the words across the whole
  locale list, so a watch with `[fa, ar]` would read the `values-ar` words joined by `, `. It is
  rare, and it is only what a spoken preview says. A per-folder resource for the comma would close
  it, and is not worth a string for this. `previewRightToLeft` is left as it is.

The other five:

- **Item 1: one definition.** `today.spokenSeparator(rtl)` now calls
  `ComplicationFields.spokenSeparator(rtl)` (`TodayModel.kt`), the one place the rule is written.
  **Proof:** the one definition mutated to `؛ ` for right to left fails 12 tests of 612, across
  `SpokenTest`, `ClockFormTest`, `ComplicationFieldsTest`, `NextPrayerSourceTest`, `QiblaModelTest`,
  `TodayModelTest` and `TilePlanTest`, so Today and the Tile follow it. The earlier mutant of that
  function left them alone.
- **Item 2: the vacuous assertion is gone.** `SpokenTest`'s line that put the comma back to a space
  built its expected value from the fields of the assertion before it, so it could not fail on its
  own. This report's sentence about it is reworded (Item 1's list above).
- **Item 3: the short text's request is held.** `NextPrayerSourceTest` asks `SHORT_TEXT` at API 30
  and at 33, in English and Arabic, for every entry and the default: the label, the time, and
  `prayerSpoken` as a static description. **Red:** with the description mutated to
  `text(fields.time)`, both new tests fail, at both levels (`expected:<[Fajr, ]4:33> but
  was:<[]4:33>`); green on the real code.
- **Item 4: plan 5.** The closing `getprop` and `setprop` moved out of Step 2's first bullet into its
  own last one, so a reader going top to bottom cannot unset the tag before the 12-hour capture.
  The same bullet notes that the running observation predates the gate, so its closing `getprop`
  may print an empty line and that means nothing. Also: Step 1's stale `NextPrayerSource.kt:44-46`
  now names the KDoc's paragraph; Task 2's `release-face.sh` line carries the flag; Task 7's two
  lines say the bare script (`all`) refuses too.
- **Item 5: the KDoc.** `NextPrayerSource`'s budget paragraph now cites "Ruling 28 of the close", as
  the ledger asks (there are two Rulings 28), and says "every type": that ruling says nothing of
  API levels.

`:wearApp` is at 612 tests.
