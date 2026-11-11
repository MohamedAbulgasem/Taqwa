# Plan 2 Task 7: the phone and the watch on emulators, the Android pass

Lane W (`watch-wear`), 31 October 2026, in two runs:

- **Steps 1 and 2** (Ruling 68 item 3), before the pairing: the two scripts, `WatchFeedDebug`, the
  harness, the `Taqwa_Phone_Wear` AVD, the phone without a watch (0a) and the phone's own forms.
  Base `1bc9fd42` (`git merge --ff-only watch-companion`: already up to date), folded into the WIP
  `1c5fbc66` 'WIP P2.7: Steps 1-2, before the pairing'; review round 1 CLEAN on both lenses.
- **Steps 3 to 6**, after Mohamed's one sign-in (SIGN, about 12:50): the pairing, 0b (the phone
  without Play services, run now that the disk had room), the pass's eleven checks on the paired
  emulators, Step 5's checks and the commit. Base `1c5fbc66` (`watch-companion` still at
  `1bc9fd42`, nothing to merge), WIP `35345b73` on the way, folded into the plan's commit.

Mohamed's Taqwa identity, no trailer, nothing pushed. Not touched in either run: the plugged-in phone
`KB2604110095`, the Lead's `emulator-5556`, Dev 2's `emulator-5560`, Dev 1's `emulator-5572`, the
work iPhone 12, every simulator but `scripts/test.sh`'s own Kotlin/Native runs. No release key was read or used. Nothing was signed in to by an agent:
the Play Store account, the Pixel Watch app and Studio's Pair Wearable are Mohamed's.

**In short:** every check of Step 4 passed as the plan expects it, with three things the plan did
not foresee, each in its place below and gathered under "For the Lead": Android Studio's pairing
needs a reverse on the watch as well as the phone's forward, so `wear-pair.sh` makes both (fixed
test-first, then used three times); removing the forward does not drop a connected watch, so check
10 took the plan's own fallback (the watch stopped by its serial and brought back by
`wear-pair.sh`); and after check 8's uninstall and reinstall of the watch app, the face kept its
complications' data from before the uninstall until the face was installed again. A per-app
Hebrew reaches the app as `en-US`, so check 4's "outside the seven" is the English feed itself.
The slowest sync of this run took 749 ms (the first build after a cold start). **But the reviews,
on the same emulators and build, crossed the plan's 4 s flag line twice:** round 1 measured a first
sync of 4,049 ms after a fresh install on a loaded host, under 1 s inside the ask's 5 s budget; and
**round 2's re-run of check 8 saw the budget cut a warm process's handshake**: `WATCH_ASKED FAILED
5262ms`, so the reinstalled watch kept its debug sample until the reviewer's `reschedule` 46 s
later sent it the feed. Both are flagged under "For the Lead" (item 1). This run's 749 ms is not
the margin to rely on, and a fresh install's cold build is not the only way past it.

## Step 1: the scripts, the seam and the harness

### `scripts/phone-emulator.sh`

It follows `scripts/wear-emulator.sh` line for line: AVD `Taqwa_Phone_Wear`, device `pixel_8`,
image `system-images;android-36;google_apis_playstore;arm64-v8a`, port 5562, headless unless
`--window`; `--noplay` makes `Taqwa_Phone_NoPlay` on `system-images;android-36;default;arm64-v8a`,
port 5564, and stops with the `sdkmanager` line when that image is not installed (it never
downloads). Two additions, both forced by this Mac:

- **The new AVD's `config.ini` is corrected.** cmdline-tools 23's `avdmanager` writes the
  placeholders `avd.id=<build>`, `avd.name=<build>` and `disk.dataPartition.path=<temp>`, and leaves
  `PlayStore.enabled=no`, `hw.gpu.enabled=no` and `hw.keyboard=no` whatever the image (the toolchain
  notes say the same of the Wear AVD, fixed by hand then). The script drops the placeholders and
  writes `PlayStore.enabled=true` (false for `--noplay`), the GPU, a keyboard for typing in the
  window, and:
- **a 6 GB data partition**, as the Wear AVD has. The `pixel_8` profile's 10 GB was refused at the
  first boot: "Not enough space to create userdata partition. Available: 11968.66 MB …, need
  12288.00 MB." The Mac's disk had 12 GB free (98 % used). **For the Lead:** that is tight for a
  second phone image's updates and Mohamed's Pixel Watch app; the half-made AVD of that first try
  was deleted and made again by the script.

### `scripts/wear-pair.sh`

Starts each emulator only when it is down (`scripts/wear-emulator.sh`, `scripts/phone-emulator.sh`,
`--window` passed to the phone), refuses when the phone has no Taqwa build (no harness to ask),
then makes **the pairing's two halves**, then polls the harness's `nodes` every 5 s for up to 2
minutes until a line `node … nearby=true taqwa_watch=true` appears. Otherwise it stops and says to
ask Mohamed for Studio's Pair Wearable again. It stops neither emulator. `PAIR_TIMEOUT_SECONDS` and
`PAIR_POLL_SECONDS` exist for its test.

**The two halves (changed in Steps 3 to 6).** The plan's script forwarded the phone's port alone
(`adb -s emulator-5562 forward tcp:5601 tcp:5601`). Studio's Pair Wearable, as found after SIGN,
made two: the phone's forward from a host port of Studio's own (`tcp:64922 → tcp:5601`) and **a
reverse on the watch** (`adb -s emulator-5558 reverse tcp:5601 tcp:64922`), so the watch's own 5601
reaches the phone through the host. adb keeps neither across a restart of its emulator, so after
the watch restarts its reverse is gone and a forward alone cannot bring it back. The script now
makes both through host 5601, rebinding whatever is there:

```
adb -s emulator-5562 forward tcp:5601 tcp:5601
adb -s emulator-5558 reverse tcp:5601 tcp:5601
```

and an adb call that fails during the poll is asked again at the next poll, where it ended the
script silently under `set -e` before (a minor of round 1).

**Test:** `scripts/test-pairing-scripts.sh` (24 checks, all pass, about 20 s), on a fake SDK in a
scratch repository, after the pattern of `tools/watchface/store-test.sh`: a fake `adb`, `emulator`,
`avdmanager` and `sdkmanager` record every call, and a failing `adb` and `sdkmanager` lead `PATH`,
so a bare call or a download fails the test. Each check was seen red first: in Steps 1 and 2 the
scripts absent, then the config and the partition before their code; in Steps 3 to 6 the fake
watch comes back only through both halves, and an `ADB-ERROR` answer fails one poll, and six checks
failed on the old script (the watch never came back; the adb error ended it) before the change made
all 24 pass. It is not wired into `scripts/test.sh`, as `store-test.sh` is not (`test.sh` is a
shared file other tasks own).

**Seen working** three times in the pass (checks 10 and 11): each time the watch booted and was
listed nearby with Taqwa's watch app within about 27 s of the script's start, and Mohamed was never
needed again. Android Studio, still running, made a new forward of its own each time the watch came
back (`tcp:57901`, then `tcp:60214`); the script's chain through 5601 does not depend on it.

### `WatchFeedDebug` (`shared/src/androidMain/.../watchfeed/WatchFeedDebug.kt`)

Public, because the harness is another module; it speaks only in strings, Booleans and `Int`s. Every
call into the watch side is bounded by `WatchFeedSync.CALLER_BOUND` and catches `Exception`
("TIMEOUT", "FAILED <class>"). As the plan's Interfaces list it:

- `nodes(context)`: `PlayWatchNodes.watches()` first, so without Play services no Wearable client is
  made; null is "no Wear API" and no node list is asked for. Otherwise
  `NodeClient.getConnectedNodes()` beside the capability's nodes, one line each:
  `node <id> "<name>" nearby=<b> taqwa_watch=<b>`, or "no nodes";
- `sync(reason)`, `settle()`, `language()` (`watchUiLanguage().code`), `phoneFormsJson()`
  (`PhoneForms.json()`);
- `sendWithVersion(version)`: settles the sync first (the asks before it are served, so its number
  follows theirs), builds the feed as `feedFor` does, raises its version, writes the next number
  down before the link is called, as the sync does (Ruling 50 item 1), and sends through the link
  with the raised feed's own fingerprint. It logs `TaqwaWatch DEBUG version <n> <result>
  <seq>/<fingerprint>`, never the origin. **Its known limit** (in its KDoc): it runs outside the
  sync's one worker, so an ask served in the very moment between its read and write could take the
  same number; the record's rule for an echo at the last number with another fingerprint (Ruling 50
  amendment A4) then costs one more send, never a wrong `UNCHANGED`. The harness is driven one
  command at a time, and no release build holds it.

**One addition to the plan's list, `phrase(text, target)`.** The plan's harness called
`tasbeehStore.addCustom` itself, but plan 3 Task 1 moved `TasbeehPreset` into `:watchcore`, which
`androidApp` cannot see (the first build failed: "Cannot access class
'world.taqwa.app.tasbeeh.TasbeehPreset'"). So the seam adds the phrase and makes the Tasbeeh
screen's ask (`PHRASES_CHANGED`) itself, and answers `added <id>; PHRASES_CHANGED <result>`. That
also keeps plan 2's rule that no `:watchcore` type reaches `androidApp`.

**Tests:** `WatchFeedDebugTest`, 17, on fakes and virtual time (`PlayWatchNodes` over `FakeTask`s,
`FakeWatchNodes`, the sync's `SyncHarness`, a `TasbeehStore` over preferences in memory). All 17
were seen failing first: the compile, then a stub answering nothing, then green. They hold: no Play
services asks nothing else; a 17 from the capability query is "no Wear API" and asks for no node
list; every connected node and every capability node is listed with its flags; the result's name,
"TIMEOUT" at exactly the bound, and an unknown reason named with nothing asked; settle true once
served and false at the bound; the raised version under the next number, on record before the link
is called, after the asks queued before it, "NO_FEED" with no number used, "TIMEOUT" with its
number kept, "FAILED IllegalStateException" for a link that throws; the phrase added then asked,
and a blank phrase refused with nothing asked; the forms are `PhoneForms.json()`'s.

### `WatchHarnessReceiver` (`androidApp/src/debug/`)

`am broadcast -n world.taqwa.app/world.taqwa.app.debug.WatchHarnessReceiver -a
world.taqwa.app.debug.WATCH --es cmd <cmd>`, registered in the debug manifest beside the recitation
harness (merged debug manifest checked). The commands are the plan's: `nodes`, `place`, `clock`,
`phrase`, `send` (and `send --ei version n`), `appeared`, `reschedule`, `language`, `forms`. Each
prints its result as the broadcast's data (`am` shows it) and under the log tag
`TaqwaWatchHarness`.

The rows below are shorthand for what follows `--es cmd`. A value with a space needs the remote shell's
quotes too, so the whole `am` line is quoted once more on the host: `adb -s emulator-5562 shell "am
broadcast … --es cmd phrase --es text 'سبحان الله وبحمده' --ei target 100"`. Without them the phone's
shell splits the text, and only its first word is stored.

- **`place` does both writes, and why** (the plan asks the report to say so): it stores the
  location with its `cityName` and `cityId`, then `setLocationSource(MANUAL)` and
  `setOnboardingComplete(true)`, then reschedules and settles. A fresh install opens on onboarding,
  whose location step could replace the harness's city, and a launch with a source other than MANUAL
  asks for a fix and could move the feed off London mid-pass. MANUAL needs no location permission,
  and none was granted. Seen: after `place`, `am start` opened straight on London's Prayer screen
  (`0a/phone-prayer-screen.png`), never onboarding.
- It prints the stored location back (city, id, zone, the three-decimal coordinates the store
  keeps): a debug line on an emulator, about a public city, never a release line.
- The harness's own glue has no host test (`androidApp` has no test source set, and adding one is
  outside this task's files); its commands are proven by their printed results on the phone below.

### Builds and installs

- `./gradlew :androidApp:assembleDebug :wearApp:assembleDebug`: BUILD SUCCESSFUL.
  `androidApp-debug.apk` sha1 `e4c68dbc` (1.3.0 (39)); `wearApp-debug.apk` sha1 `832eaa68`
  (100039), up to date since R68w (no `wearApp` source changed after it).
- `adb -s emulator-5562 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk`:
  Success (twice, before each 0a run).
- `adb -s emulator-5558 install -r wearApp/build/outputs/apk/debug/wearApp-debug.apk`: Success; the
  device's APK has the same sha1 `832eaa68`, and `FeedListenerService` (MESSAGE_RECEIVED,
  CAPABILITY_CHANGED), `DebugNudgeReceiver` and `DebugFeedReceiver` are registered
  (`step1/wear-install.txt`). The watch was then put back as found (below).
- Neither debug APK carries a test vector (Ruling 55 item 6, done in `fa62c182`): the watch's holds
  only its four sample feeds under `assets/`, the phone's none.

## Step 2: the phones without a watch

### 0a. Play services, no Wear OS app (`Taqwa_Phone_Wear`, before the Pixel Watch app)

Found (`0a/found-state.txt`): Android 16 (API 36, `BE2A.250530.026.D1`), Play services 25.08.34,
Play Store 45.3.21, no account, no Pixel Watch app (`com.google.android.apps.wear.companion`) and no
Wear OS app, 246 packages. The Taqwa debug build was installed, `logcat -c`, then the harness:

| Command | Printed |
|---|---|
| `nodes` | `no Wear API` |
| `place --es place 51.5074,-0.1278,Europe/London,London,2643743` | `stored London id=2643743 zone=Europe/London at 51.507,-0.128; plan of 59, first DHUHR-PRAYER-2026-10-31 PRAYER at 2026-10-31T11:49:00Z; settled=true` |
| `reschedule` | `plan of 59, first DHUHR-PRAYER-2026-10-31 PRAYER at 2026-10-31T11:49:00Z; settled=true` |
| `nodes` | `no Wear API` |
| `language` | `en` |

The log (`0a/logcat.txt`, `logcat -s System.out TaqwaWatchHarness`):

```
12:24:32.596 I/System.out: TaqwaWatch no Wear API 16
12:24:32.915 I/System.out: TaqwaWatch no Wear API 16
12:24:32.915 I/System.out: TaqwaWatch RESCHEDULED NO_WATCH 2ms
12:24:32.996 I/System.out: TaqwaWatch no Wear API 16
12:24:32.997 I/System.out: TaqwaWatch RESCHEDULED NO_WATCH 1ms
12:24:33.024 I/System.out: TaqwaWatch no Wear API 16
```

Against the plan's expectations:
- `nodes` prints **"no Wear API"**, and the log names the connection result: **16,
  `API_UNAVAILABLE`**, as expected. So the path was Play services available (`SUCCESS`), then the
  capability query's `ApiException` 17 carrying 16.
- **`TaqwaWatch RESCHEDULED NO_WATCH`, never `FAILED`**, in 1 to 2 ms: one capability query, nothing
  built.
- The harness printed a **non-empty plan, 59 entries**, and `dumpsys alarm` holds **59
  `world.taqwa.app` alarms** (`0a/dumpsys-alarm.txt`).
- `dumpsys notification --noredact` shows **no notification from `com.google.android.gms` or
  `world.taqwa.app`** since the install: before, Play Store's account notice and Android's physical
  keyboard notice; after, the same two; at the end only the keyboard's (`0a/notifications-*.txt`;
  the three gms matches in the full dump were its disabled Driving DND rule, not notifications).
  Nothing was shown on the screen (`0a/phone-after-0a.png`, the home screen).
- Opening the app afterwards (`am start`) landed on London's Prayer screen and its launch reschedule
  logged `RESCHEDULED NO_WATCH` in 420 ms and again in 5 ms (`0a/logcat-launch.txt`): the first
  launch of a cold process, which builds nothing for the watch either.

**Run again on current Play services.** At 12:25:31, after the run above, the image updated Play
services by itself to **26.42.31** (no account: the system's own update; nothing of ours asked for
it). 0a was run again on it with a fresh install (`0a-gms26/`): the same answers, `no Wear API 16`,
`RESCHEDULED NO_WATCH` in 2 and 1 ms, plan of 59, 59 alarms, no notification from gms or Taqwa. The
Play Store's own log warns that it needs a newer Play services; that is `com.android.vending`'s
process, not Taqwa's.

### 0b. No Play services at all (`Taqwa_Phone_NoPlay`, in the Steps 3 to 6 run)

Not run in Steps 1 and 2 (Ruling 68). In Steps 3 to 6 the Mac's data volume had 27 GB free when the
pass reached it, over the 15 GB the Lead set for it, and its image
`system-images;android-36;default;arm64-v8a` was already installed (24 October), so nothing was
downloaded. It ran while the watch was down after check 11, so no more than two of the lanes'
emulators ran at once, under a lock of its own (`phone-5564`, 13:27 to 13:28).

`scripts/phone-emulator.sh --noplay` made `Taqwa_Phone_NoPlay` (pixel_8, `PlayStore.enabled=false`,
the 6 GB data partition) and booted it headless on 5564 in 35 s. Found (`0b/found-state.txt`):
Android 16 (API 36, `BE2A.250530.026.D1`, `sdk_phone64_arm64`), **no `com.google.android.gms`, no
`com.google.android.gsf`, no Play Store**, 181 packages. The debug build `e4c68dbc` installed with
`adb -s emulator-5564 install -r`, `logcat -c`, then the harness (`0b/harness.txt`):

| Command | Printed |
|---|---|
| `nodes` | `no Wear API` |
| `place` London (with its id) | `stored London id=2643743 …; plan of 59, first DHUHR-PRAYER-2026-10-31 PRAYER at 2026-10-31T11:49:00Z; settled=true` |
| `reschedule` | `plan of 59, …; settled=true` |
| `nodes` | `no Wear API` |
| `language` | `en` |

- The log (`0b/logcat.txt`): **`TaqwaWatch RESCHEDULED NO_WATCH 0ms`** for both, never `FAILED`.
  Unlike 0a there is **no `TaqwaWatch no Wear API <code>` line**: Play services answer
  `isGooglePlayServicesAvailable` with something other than `SUCCESS`, so no Wearable call is made
  and no `ApiException` arrives to name a connection result. That is the path the plan's fakes
  take (`withoutPlayServicesNothingIsAskedOrShown`).
- **59 `world.taqwa.app` alarms** in `dumpsys alarm` for the plan of 59 (`0b/dumpsys-alarm.txt`).
- **No notification from `world.taqwa.app`** (there is no `com.google.android.gms` to post one):
  before the install and after the checks, the same three of Android's own (`0b/notifications-*.txt`).
- Opened afterwards, the app landed on London's Prayer screen (`0b/phone-prayer-screen.png`), and
  its launch logged `RESCHEDULED NO_WATCH` in 0 ms twice (`0b/logcat-launch.txt`).

Then `adb -s emulator-5564 emu kill`, and `avdmanager delete avd -n Taqwa_Phone_NoPlay` gave its
space back (26 GB free after). So the no-Play case is proven on an emulator as well as on fakes.

### The phone's own forms

`forms` wrote `files/phone-forms-device.json` (17,123 bytes), read back with
`adb -s emulator-5562 exec-out run-as world.taqwa.app cat files/phone-forms-device.json` into
`phone-forms-api36.json`. Compared with `tools/watchface/phone-forms.json` tag by tag and field by
field (`python3 -I -c` with `json`, `forms-diff.txt`): **30 tags on each side, in the same order, 0
differences.** The two files are byte-identical (sha1 `b65c1459`). So no fit tool needed a run with
`forms=` the device's file, and no form goes to the Lead.

**August (Ruling 51a):** the device's August is **أغسطس** for ar-SA, ar-EG, ar-LY and ar-AE alike,
the one spelling Arch's 13-unit step is built for. Their digit sets, phone's first: ar-SA and ar-EG
Arabic-Indic then Western, ar-LY and ar-AE Western then Arabic-Indic.

The forms were read again in Steps 3 to 6 with the command's own line kept (a minor of round 1,
`forms-rerun/forms-command.txt`): `forms` printed `wrote files/phone-forms-device.json (17123
bytes)`, and the file read back has sha1 `b65c1459`, byte-identical to `phone-forms-api36.json` and
`tools/watchface/phone-forms.json`, on Play services 26.42.31.

## Step 3: the pairing

**SIGN** was Mohamed's, at about 12:50: in `emulator-5562`'s window he signed in to the Play Store,
installed the Google Pixel Watch app and paired with Android Studio's Pair Wearable. The Lead saw
the pairing and left both emulators running and paired. Found by this run at 12:55
(`step3/pairing.txt`, `left/*-found.*`):

- the phone: one Google account (the count only; nothing of it was read), the Pixel Watch app
  `5.1.0.1005629187`, Play services 26.42.31, 249 packages, no Taqwa, Africa/Johannesburg, font
  scale 1.0, TalkBack off, Wi-Fi on; its `WearableService` with **1 connected out of 1**,
  `Taqwa_Wear_OS_6` (node `c76e057a`, `isWatch=true`, `IsConnected=true`); in front, the Pixel
  Watch app's setup screen with its spinner;
- the links Studio made: `emulator-5562 tcp:64922 → tcp:5601` and, on the watch,
  `reverse tcp:5601 → tcp:64922`;
- the watch: as Steps 1 and 2 left it (the drivers' `pullfound.sh`: app `2b2d77c9` (100038), face
  `d4f50b1d`, the sample feed `0b3f92ef`, no Tile, automatic time, font 1.0, TalkBack off, en-US),
  and the phone `4e7c91b9` connected.

Both debug builds were installed with `adb -s … install -r` (13:03:15 to 13:03:17): the watch's
`832eaa68`, the phone's `e4c68dbc`, the APKs Steps 1 and 2 built and tested (no source changed
since but the two scripts). Then:

```
nodes: node c76e057a "Taqwa_Wear_OS_6" nearby=true taqwa_watch=true
```

**The check passes: the harness lists the watch nearby, with Taqwa's watch app.** The watch had
already shaken hands when the phone's app appeared (`TaqwaWearFeed ASK PHONE_NEARBY sent`), and the
phone answered `WATCH_ASKED NO_FEED` in 144 ms: no place stored yet, so nothing to build.

**Which way the re-pair works** (the plan asks the report to record it): `scripts/wear-pair.sh`,
with the reverse it now makes (Step 1 above). Used after each of the watch's three restarts in the
pass, it brought the node back nearby every time, so Studio's Pair Wearable was never needed again.

## Step 4: the pass

Each check is shot and logged into its folder (`checkN/`): the harness's printed lines, the phone's
`TaqwaWatch` and the watch's `TaqwaWearFeed` lines (`logcat -s System.out TaqwaWatchHarness` and
`-s TaqwaWearFeed System.out`), the feed and its delivery where the check reads them, shots of the
phone and the watch. Checks 3 and 11 have their logs and no shots: neither changed what either
screen showed (check 3's answers were `UNCHANGED`, and check 11 stops the watch). Every
`TaqwaWatch` line with its milliseconds is in `sync-timings.txt`: 42 lines, **0 to 749 ms, median
39.5 ms; none over 4 s in this run**. The slowest are the first builds of a cold process, which
parse the bundled city list (Task 3's `watchCityName`): 749 ms (check 1's first feed), 685 ms
(check 4's first Arabic feed in a cold process), 526 ms (check 4's Hebrew, cold). **The reviews
measured slower syncs on the same emulators**, two of them over 4 s: round 1's 4,049 ms first sync
after a fresh install (host load average about 30), and round 2's `WATCH_ASKED FAILED 5262ms`, a
handshake cut by the budget in a warm process (load about 35 to 40): see "For the Lead", item 1.

Delivery numbers: each send's number is one above the last, and each forget sets one aside
(amendment A2): an empty handshake (an echo of nothing) and every `send` (`WATCH_CHANGED`) forget,
so the numbers 1, 7, 10 and 12 went to those and 2, 3, 4 … 19 to the sends. No `TaqwaWatch` or
`TaqwaWearFeed` line in any log of the pass holds the origin (`677789a581af4ba0`); only the files
`feed.json.delivery` and this report name it.

### 1. The feed arrives

`place` London with its city id (13:04:12):

```
phone 13:04:14.280  TaqwaWatch RESCHEDULED SENT 749ms
watch 13:04:14.113  TaqwaWearFeed ACCEPTED 2/6efab8e0696fdd95
watch 13:04:14.258  TaqwaWearFeed ACK 2/6efab8e0696fdd95 sent
phone 13:04:14.588  TaqwaWatch ACK 2/6efab8e0696fdd95
```

(the two emulators' clocks differ by a few hundred milliseconds). The same number and fingerprint
on both sides.

- `files/feed.json.delivery` (`check1/feed.json.delivery`): `677789a581af4ba0/2/6efab8e0696fdd95`,
  the origin before the number.
- `files/feed.json` (`check1/feed.json`, 21,034 bytes): **31 days, 2026-10-30 to 2026-11-29**; the
  first is the phone's yesterday (31 October in Johannesburg and in London).
- **The place, as Ruling 39 allows it:** `{"city": "London", "qiblaBearing": 118.0, "qiblaKm":
  "4,800", "declination": 1.0, "southern": false}`. No number in it has a fractional part, and
  none of `51.5`, `51,5`, `51.507`, `-0.12`, `0.1278`, `0.128`, `5150` or `1278` appears anywhere in
  the file (`check1/coords-check.txt`).
- **Today shows "London"** at the end of its list (`check1/watch-today-list.png`, its tree: the six
  rows, then `text='London'`); its ring reads "Dhuhr in 44 minutes, at 11:49"
  (`check1/watch-today.png`).

### 2. App = watch

`am start -n world.taqwa.app/.MainActivity` (13:05:37): it opened **on London's Prayer screen, never
onboarding** (`check2/phone-prayer.png`), and the launch logged **`RESCHEDULED UNCHANGED`** (74 ms),
then a second `RESCHEDULED UNCHANGED` (161 ms), by its timing the Prayer screen's one-time backfill
of the country and region `place` does not store (R53's refresh): the feed stayed the same, so the
launch kept the place and the backfill changed none of London's times. At the same minute (both
shots at 13:05:44): the phone says **DHUHR IN 0:43:16, 11:49**, and the Arch face **DHUHR 11:49, in
43 min** (`check2/watch-face.png`). The same next prayer, its time and its countdown minute.

### 3. Unchanged

`reschedule` at 13:05:56 and again at 13:05:59, after the place's `ACK 2/…` was logged: **`RESCHEDULED
UNCHANGED`** (41 ms, then 21 ms), and the watch logged nothing.

### 4. Settings and the app's language

- **`clock 12`** (13:06:08): `RESCHEDULED SENT 30ms`, `ACCEPTED 3/6071cb0943b4f33b` and its ACK on
  both sides. The feed's `twelveHour` is **true**, `clock` holds "2:47" for Asr and `clockTime`
  "2:47 PM" (with its no-break space), `am`/`pm` "AM"/"PM"; the face draws **1:06 PM**
  (`check4/watch-face-clock12.png`).
- **A per-app language, the app closed:** `am force-stop`, `cmd locale set-app-locales
  world.taqwa.app --locales ar-SA` (`get-app-locales`: `[ar-SA]`), then `language` and
  `reschedule` with no activity started. **`language` printed `ar`.** The cold process's first ask
  built and sent the Arabic feed (`RESCHEDULED SENT 685ms`, `ACCEPTED 4/bca974c6176a7730` and its
  ACK), and the harness's reschedule, served after it, was `UNCHANGED` (36 ms), because the
  watch's acknowledgement had arrived first (the phone's `ACK 4/…` at 13:06:31.183, the reschedule
  logged at 13:06:31.249). That order is timing: review round 1's re-run got `SENT` twice
  (deliveries 3 then 4, one fingerprint), as the rule wants when the second ask is served before
  the acknowledgement (`UNCHANGED` only once the watch has echoed the latest). The feed
  (`check4/feed-ar-closed.json`, `check4/feed-ar-closed-check.txt`): `ui.language` `ar-SA`, **`rtl`
  true, `digitZero` "٠", `city` "لندن", `qiblaKm` "٤٬٨٠٠"** (U+0664 U+066C U+0668 U+0660 U+0660),
  the countdown "بعد %1$s س %2$s د", the days "السبت ٣١ أكتوبر". The face is in Arabic
  (`check4/ar-closed/watch-face.png`: ١٩ جمادى الأولى, السبت ٣١ أكتوبر, ١:٠٧ م, الظهر ١١:٤٩, بعد ٤١ د).
- **Ruling 58 item 5, the phone's half** (errata p3 #12, carried from P3.9): `ui.screens` arrived in
  the phone's language, built by `AppContainer.watchFeedFor`'s `watchScreenWords(language)` in the
  phone's own resource environment: it **equals the ar-SA sample's `ui.screens`** ("اليوم",
  "التسبيح", "القبلة", "الجولة %1$s" …). On the watch, the pages draw them: the Tasbeeh page
  "الجولة ١", "من ١٠٠", "أدر الإطار أو اضغط" (`check4/ar-closed/watch-tasbeeh.png`), Qibla "أبقِ
  معصمك مستويًا" and "٤٬٨٠٠ كم"; and with TalkBack on, the pager said each page's title as it came:
  **"التسبيح"**, **"القبلة"**, "التسبيح", **"اليوم"** (`check4/ar-closed/talkback-pages.txt`).
- **In place, the app open:** `am start` opened the Prayer screen **in Arabic, with London's times**
  (لندن, متبقٍّ على الظهر 0:38:45, ١١:٤٩ ص: `check4/in-place/phone-ar.png`; its launch
  `UNCHANGED` 384 ms). Then `set-app-locales … --locales en-US` while it was in front: the app redrew
  **in English** (`check4/in-place/phone-en.png`), the phone logged **`RESCHEDULED SENT`** (48 ms,
  `LanguageChangeReschedule`), and the watch stored `5/6071cb0943b4f33b`: the English 12-hour feed,
  left to right, `digitZero` "0", the very fingerprint of delivery 3, as the same words make the
  same feed.
- **Outside the seven:** `set-app-locales … --locales he-IL` was **accepted** (`get-app-locales`:
  `[he-IL]`; the app declares no Hebrew in its `locales_config`, and the shell does not check it).
  `language` printed **`en`**, and the feed is English: "SAT 31 OCT", "Saturday 31 October", `rtl`
  false, `city` "London" (`check4/he/feed-check.txt`). `reschedule` was **`UNCHANGED`**, in the open
  app (13 ms) and again in a cold process after `force-stop` (526 ms, then 46 ms): the feed is the
  English one byte for byte, with `ui.language` "en-US". **Why en-US and not the bare "en" Task 1's
  rule gives a he-IL tag:** the app's process runs with the configuration `[he_IL,en_US] ldrtl`
  (`dumpsys activity processes`), and Android makes the default locale the first of that list the
  app's own resources match, `en_US`. So `Locale.getDefault()`, and the format's tag, are `en-US`:
  a phone's own English locale, which the rule keeps whole. The Hebrew tag never reaches the app's
  format on Android; the host tests stand for one that does (`WordsLocaleTest`,
  `AndroidPlatformFormatTest` and `WatchFeedSampleTest` over he-IL, hi-IN, fa-IR, mr-IN, zh-CN and
  az-AZ). The configuration's `ldrtl` did not turn the app: it drew left to right
  (`check4/he/phone-he.png`), by its own rule (`isRtlLocale() = uiLanguage().rtl`).
- **The spoken words, from the phone's real resources:** the Arabic feed's `ui.spoken` **equals the
  ar-SA sample's**, and the English feed's **the en sample's** (and the en12 sample's); every day's
  `gregorianSpoken` is the weekday and month in full ("السبت ٣١ أكتوبر", "Saturday 31 October"). On
  the Arch face, `tools/watchface/talkback.sh on`, a walk, and `off`, in each language
  (`check4/ar-closed/talkback-face.txt`, `check4/in-place/talkback-face-en.txt`):
  - Arabic: **"الظهر بعد ٤٠ دقيقة، الساعة ⁨١١:٤٩ ص⁩"**, then **"١٩ جمادى الأولى ١٤٤٨، السبت ٣١ أكتوبر"**
    (the dates, the Gregorian in words), the wearer's two empty slots "No data", the charging icon,
    the time in the watch's own words ("1:09 PM"), and the next minute's "الظهر بعد ٣٩ دقيقة …";
  - English: **"Dhuhr in 38 minutes, at ⁨11:49 AM⁩"**, then **"19 Jumada al-Ula 1448, Saturday 31
    October"**.

  The phone's sentence, in its words, at the minute of the walk. The 12-hour time is spoken with its
  day period ("ص", "AM"): the phone's `clockTime` (plan 3 Task 10) now carries it. TalkBack is off
  at the end (Ruling 38).
- **The system's language back:** `set-app-locales world.taqwa.app --locales ''` (the shell passed
  the empty list): `get-app-locales` `[]`, then **`language` `en`** and `reschedule` `UNCHANGED`
  (`check4/back/`).

### 5. Phrases

`phrase --es text 'سبحان الله وبحمده' --ei target 100` printed `added custom_1793445175739;
PHRASES_CHANGED SENT`; the phone logged **`TaqwaWatch PHRASES_CHANGED SENT 51ms`**, the watch
`ACCEPTED 6/23aee84e77b1f1f2`. The feed's `tasbeeh` holds `{"id": "custom_1793445175739", "phrase":
"سبحان الله وبحمده", "target": 100}`, and the watch's phrase list shows it after the built-ins
("سبحان الله وبحمده, 100": `check5/watch-phrase-list.png`).

### 6. The watch asks when its feed runs low

`DEBUG_FEED --el epoch <now − 29 days>` (1790939606, 2 October) stored the sample (Cape Town, no
delivery, `writtenAt` 2 October), then the watch app was opened. The sample was written 29 days
before, and with its days moved back 16 (its 18 October onto 2 October) its last start falls on
31 October, under a day away. So both of `WatchRules.needsRefresh`'s rules hold (written over a
week ago; last start under three days away), and the opening asks: **`TaqwaWearFeed ASK OPENED
sent`**; the handshake carried nothing, so the phone logged **`WATCH_ASKED SENT`** (52 ms) and
the watch stored **London's feed again, `8/23aee84e77b1f1f2`** (the empty echo set 7 aside), with
its delivery (`check6/`).

### 7. A newer version

`send --ei version 2`: the phone logged `TaqwaWatch DEBUG version 2 SENT 9/9c6aae003f912399` and the
watch **`TaqwaWearFeed NEWER_VERSION 9/9c6aae003f912399`**. `feed.json` is unchanged (sha1
`afcb9d09` before and after, delivery still 8), and **Today says "Update Taqwa on your watch"** in
place of the next prayer's clock time (`check7/watch-today-v2.png`; its sentence "Dhuhr in 34
minutes, at 11:49 AM, Update Taqwa on your watch"). A plain `send` then logged `WATCH_CHANGED SENT`,
the watch **`ACCEPTED 11/23aee84e77b1f1f2`**, and the notice is gone (`check7/watch-today-send.png`).

### 8. No watch app, then a reinstalled one healed by its own handshake

- `adb -s emulator-5558 uninstall world.taqwa.app` (13:14:39); `nodes` then lists the watch
  `nearby=true taqwa_watch=false`, and **`reschedule` gives `NO_WATCH`** (2 ms).
- Reinstalled with `install -r` (13:14:58): the phone logged **`WATCH_APPEARED UNCHANGED`** at
  13:15:01, three seconds later: the record still confirms the feed, and a reinstall looks like a
  reconnect.
- Nothing started the watch app's process within a minute (no `TaqwaWearFeed` line, no
  `feed.json`), so at 13:16:19 `am start … WearMainActivity`. **The start's handshake came first:
  `TaqwaWearFeed ASK STARTED sent`**, then `ASK OPENED skipped` (damped, a minute's gap). The phone
  logged **`WATCH_ASKED SENT`** (287 ms) and the watch stored `13/23aee84e77b1f1f2`: London's days and
  their delivery (`check8/`).
- **On review round 2's re-run this last expectation did not hold.** The start's handshake went out
  (`ASK STARTED sent`, 14:24:27.747), but the phone answered `WATCH_ASKED FAILED 5262ms`: the ask's
  5 s budget cut it, in a warm process, before it sent anything. The watch kept its debug sample
  until the reviewer's `reschedule` 46 s later sent it the feed (`RESCHEDULED SENT 67ms`, `ACCEPTED
  12/dcd0420ecdc97f21`), with no wrong `UNCHANGED` on the way. The cause, the heal and the Lead's
  call are in "For the Lead", item 1, and the review's logs in `review2/`.

### 9. A delivery dropped, then healed; a change undone before its acknowledgement

- `touch files/drop-next-feed`, then `place` Tripoli with its id: the phone **`RESCHEDULED SENT`**
  (160 ms), the watch **`TaqwaWearFeed DROPPED`**, no acknowledgement; `feed.json` still London's
  (delivery 13).
- `reschedule`: **`RESCHEDULED SENT`**, never `UNCHANGED` (40 ms); the watch `ACCEPTED
  15/048de2e7a0ed0eb7` and its ACK; `feed.json` Tripoli's (bearing 109, "2,900", declination 3).
  One more `reschedule`: **`UNCHANGED`**.
- **The undo.** On Tripoli's acknowledged 12-hour feed, `touch files/hold-next-ack`, `clock 24`
  (13:17:07), then `clock 12` four seconds later:

  ```
  watch 13:17:08.019  TaqwaWearFeed ACCEPTED 16/4e439885f310dae4      (the 24-hour feed)
  watch 13:17:08.019  TaqwaWearFeed ACK HELD
  phone 13:17:08.295  TaqwaWatch RESCHEDULED SENT 170ms               (clock 24)
  phone 13:17:12.084  TaqwaWatch RESCHEDULED SENT 100ms               (clock 12: never UNCHANGED)
  watch 13:17:28.039  TaqwaWearFeed ACK 16/4e439885f310dae4 sent      (after the 20 s hold)
  watch 13:17:28.063  TaqwaWearFeed ACCEPTED 17/048de2e7a0ed0eb7      (the 12-hour feed)
  watch 13:17:28.069  TaqwaWearFeed ACK 17/048de2e7a0ed0eb7 sent
  phone 13:17:28.407  TaqwaWatch ACK 16/4e439885f310dae4
  phone 13:17:28.430  TaqwaWatch ACK 17/048de2e7a0ed0eb7
  ```

  The phone's two acknowledgements name the 24-hour and the 12-hour deliveries in that order, the
  second's number one above the first's; the 12-hour feed's fingerprint is delivery 15's, the same
  Tripoli 12-hour feed. `feed.json` has `twelveHour` true, the face draws **1:17 PM**, and one more
  `reschedule` is **`UNCHANGED`** (55 ms).
- **What the face's next prayer showed here** (`check9/watch-face-tripoli-12h.png`): still
  **London's "DHUHR 11:49, in 31 min"**, though the watch held Tripoli's feed, whose next prayer was
  Asr at 3:55. The complications had kept the data they served before check 8's uninstall: the app's
  `requestUpdateAll` after each stored feed, and opening the app, changed nothing on the face, and
  the face's log read `[12:TEXT] "11:49"` throughout. Installing the face again (`install -r -d` of
  the same APK, as the drivers' `restore.sh` does) made it ask its sources, and it drew **"ASR 3:55,
  in 2 h 34 min"** at once (`check9/watch-face-tripoli-12h-after-face-reload.png`). Before check 8
  every feed reached the face at once (checks 1 to 7), and after the watch's restarts in check 10 it
  did again (`check10/watch-face-back.png`: London's Dhuhr). See "For the Lead".

### 10. A change made while the watch was away, healed on its return

- **Taking the link down.** There was no `tcp:5601` forward to remove: Studio's is `tcp:64922`. With
  it removed (13:20:26), **the watch stayed connected and nearby for the whole two minutes**: the
  connection already made outlives the forward, which only takes new ones. So the plan's fallback:
  `adb -s emulator-5558 emu kill` (13:23:08). Ten seconds later `nodes` printed "no nodes".
- `place` London: **`TaqwaWatch RESCHEDULED NO_WATCH`** (2 ms).
- `scripts/wear-pair.sh` (13:23:33): the watch booted, the forward and the reverse made, the node
  nearby at 13:24:00. **The phone's capability change came first: `WATCH_APPEARED SENT`** (360 ms,
  `ACCEPTED 18/23aee84e77b1f1f2`); then the watch's reconnect handshake, `ASK PHONE_NEARBY sent`,
  echoing what it held, delivery 17 (Tripoli), which arrived before 18's acknowledgement, so
  **`WATCH_ASKED SENT`** (59 ms, `ACCEPTED 19/…`): one more send for a handshake that overtook an
  acknowledgement, never a wrong `UNCHANGED` (Review Focus 2). `feed.json` shows London's days
  (delivery 19), and the face London's Dhuhr.
- **A reconnect with nothing changed**, after the phone had logged `ACK 19`: the watch down and back
  the same way (13:25:18 `emu kill`; `nearby=false taqwa_watch=true` while it was down; back at
  13:25:54). **`WATCH_APPEARED UNCHANGED`** (25 ms) and **`WATCH_ASKED UNCHANGED`** (21 ms), and the
  watch logged `ASK PHONE_NEARBY sent` and **no `ACCEPTED`**: a watch that holds the current feed is
  not sent it again. The harness's `appeared`: **`WATCH_APPEARED UNCHANGED`** (35 ms).
- **The daily handshake, nothing to do:** `am broadcast … DebugNudgeReceiver -a
  world.taqwa.app.wear.DEBUG_NUDGE` printed **`nudged asked=true`**; the watch `ASK DAILY sent`, the
  phone **`WATCH_ASKED UNCHANGED`** (24 ms), and no `ACCEPTED`. The real `DailyNudgeReceiver`'s
  alarm, from `dumpsys alarm` (`check10/daily-nudge-alarm.txt`): `ELAPSED #22: Alarm{… type 3 …
  world.taqwa.app}`, `tag=*alarm*:world.taqwa.app/.wear.link.DailyNudgeReceiver`, `type=ELAPSED
  … window=+18h0m0s0ms repeatInterval=86400000`: an inexact, non-waking alarm repeating daily.
- **Which way was used:** the fallback, twice: the watch stopped by its serial and brought back by
  `scripts/wear-pair.sh`.

### 11. No watch

`adb -s emulator-5558 emu kill` (13:26:38). Six seconds later the phone had not yet seen the watch
go (`nearby=true`), and a `reschedule` was `UNCHANGED` (26 ms): right, since the watch's confirmed
feed is the current one. Nine seconds after that, `nodes` printed `nearby=false`, and
**`reschedule` gave `NO_WATCH` in 1 ms**, with **the plan of 59 armed as before** (59
`world.taqwa.app` alarms). After 0b (above), `scripts/wear-pair.sh` (13:28:37) brought the watch
back and paired by 13:29:00: `WATCH_APPEARED UNCHANGED` and `WATCH_ASKED UNCHANGED`, no `ACCEPTED`.

## Step 5: the checks

- **`scripts/test.sh`: exit 0** (20 s): `test-bump-version.sh`, `test_privacy_check.py` and
  `test_store_listing_check.py` PASS, `swift test` green, and every Gradle suite up to date, since
  no source the suites read changed after Steps 1 and 2's run (2,330 shared host tests then).
  Re-run fresh for this report: `./gradlew :shared:testAndroidHostTest :wearApp:testDebugUnitTest
  --rerun` (below, "The checks at the end").
- **`./gradlew :androidApp:minifyReleaseWithR8`**: BUILD SUCCESSFUL, unsigned, no release key read.
  `androidApp/build/outputs/mapping/release/usage.txt` lists **`world.taqwa.app.watchfeed.WatchFeedDebug`,
  `world.taqwa.app.watchfeed.PhoneForms` and `world.taqwa.app.watchfeed.PhoneTags`** (and
  `DebugNode`) as whole classes removed, with their 12 inner classes (`step5/usage-check.txt`).

## For the Lead

1. **Two syncs over 4 s (Step 4's flag), measured by the reviews, not by this run: a fresh
   install's first sync (round 1), and a warm handshake the budget cut (round 2).**

   **Round 1: a fresh install's first sync, 4,049 ms.** The
   spec lens of review round 1 re-ran the feed on the same paired emulators and the same phone build
   (`e4c68dbc`), under the locks, 13:48 to 13:56: a fresh install of the phone's debug build on
   `emulator-5562`, `place` London about 20 s after it, with the host's load average about 30. The
   phone logged

   ```
   10-31 13:50:31.888 I System.out: TaqwaWatch RESCHEDULED SENT 4049ms
   ```

   That is over the plan's 4 s line, and Step 4 says such a sync goes to the Lead. The
   milliseconds are counted from when the ask is served (`WatchFeedSync.kt:194`), the same start
   as the ask's 5 s budget (`budget = 5.seconds`, `WatchFeedSync.kt:104`), so this ask had **under
   1 s to spare**. The same review's other first syncs: five more first asks in a cold process
   took 1,084, 1,271, 1,341, 1,376 and 1,663 ms (load average 19 to 29), and a second fresh install
   1,115 ms (load about 15). This run's slowest was 749 ms (check 1, a cold process but not a fresh
   install). So **the margin depends on the host's load and on how fresh the install is**, and this
   run's figures are not the margin to rely on.

   What happens past 5 s: the ask is cut and answers `FAILED` (`withTimeoutOrNull(budget)` gives
   null), and a feed it had not sent waits for the next ask (one of the watch's own handshakes, on
   opening, on reconnecting or the daily one, or the phone's next reschedule or change). The
   reminders are not affected: the coordinator only asks, and the sync runs on its own worker
   (Global Constraints, "The reminders come first").

   **Round 2: a warm handshake cut by the budget, `WATCH_ASKED FAILED 5262ms`.** The spec lens of
   review round 2 re-ran check 8 on the same emulators and build, under `wear-5558` then
   `phone-5562`, 14:23 to 14:25, with the host's load average about 35 to 40. Its logs are in
   `review2/` (`check8-phone.txt`, `check8-watch.txt`, `check8-phone-frames.txt`, every ask of its
   re-run in `phone-asks.txt`, and the phone's process events, read afterwards under `phone-5562`,
   in `phone-events.txt`):

   ```
   phone 14:23:09.230  TaqwaWatch RESCHEDULED NO_WATCH 12ms        (the watch app uninstalled)
   phone 14:23:11.832  TaqwaWatch WATCH_APPEARED UNCHANGED 34ms    (installed again)
   watch 14:24:27.747  TaqwaWearFeed ASK STARTED sent              (am start … WearMainActivity)
   watch 14:24:28.049  TaqwaWearFeed ASK OPENED skipped
   phone 14:24:42.632  TaqwaWatch WATCH_ASKED FAILED 5262ms
   watch 14:25:28.376  TaqwaWearFeed ACCEPTED 12/dcd0420ecdc97f21  (after the reviewer's reschedule)
   phone 14:25:28.488  TaqwaWatch RESCHEDULED SENT 67ms
   phone 14:25:28.599  TaqwaWatch ACK 12/dcd0420ecdc97f21
   ```

   - **Not the cold build.** The phone's process (13764) had started at 14:21:01.717 and had
     served ten asks that built a feed before this one, all of them London's: the first, the Arabic
     feed's cold build, took 1,246 ms, the nine after it 34 to 658 ms. So the city list was parsed
     and London looked up. The events show no other Taqwa process between that start and its stop
     at 14:26:33.343, and no freeze of it.
   - **Where the 5 s went, as far as the logs tell.** Step 0's forget of the watch's empty echo was
     written: it set number 11 aside, and the next send took 12. So the cut came after that write
     and before step 5 wrote a send number. Between the two are the ask's capability query
     (`isGooglePlayServicesAvailable`, then `getCapability("taqwa_watch", FILTER_REACHABLE)`), the
     build (`feedFor`), its encoding and fingerprint, and the record's read for step 4. The app logs
     nothing between them, so which step held the ask is not known. In those seconds the phone's
     log shows `artd` checking Play services' modules (14:24:26 to 14:24:41), a Play services check
     in Taqwa's own process (`GoogleSignatureVerifier`, 14:24:37.131, just before the ask was
     served), and Taqwa's main thread starved (Choreographer skipping up to 418 frames, `Davey!
     duration=9874ms` at 14:24:26.980). The 262 ms past 5 s is the cut itself landing late on that
     starved process.
   - **Time before the budget, too.** The milliseconds count from the serve, here about
     14:24:37.37 (14:24:42.632 less 5,262 ms), nearly 9.6 s after the watch sent its handshake (the
     clocks differ by a few hundred ms). The worker was idle, with no ask since 14:23:11, so that
     time went in the message's way through Play services to the phone's listener. It is outside
     the budget, and outside every `TaqwaWatch` figure in this report.
   - **The heal was right, but it was the reviewer's.** Step 0's forget, written before the cut,
     is what made the next ask answer `SENT` under number 12, not a wrong `UNCHANGED`; the watch
     stored and acknowledged it, and the reminders stayed armed (round 2 counted 58 alarms for the
     plan of 58). That next ask was the reviewer's `reschedule`, 46 s on. Left alone, a reinstalled
     watch whose handshake fails waits for whichever comes first: the phone's next reschedule (a
     setting, a language, a journey, a boot, the plan's top-up), or the watch's next handshake. That
     is its next process start while it holds no feed from the phone (`STARTED`), its next opening
     while it holds no feed at all (`OPENED`), each at least a minute after the last handshake
     (`RefreshPolicy.GAP_SHORT`), a reconnect (`PHONE_NEARBY`), or the daily nudge (armed here at
     14:24:25.793 for 86,400 s). The watch is never told the ask failed: the phone answers a
     handshake only by sending a feed. Meanwhile a release build shows no feed; this debug build
     showed its sample (Cape Town, with no delivery).
   - So **the plan's check 8 expectation** (the phone `WATCH_ASKED SENT`, `feed.json` with London's
     days and `feed.json.delivery` their delivery) **did not hold on round 2's run**, though it held
     on this pass's own (287 ms).

   **Also seen by round 2: the fresh install's first process start ANR'd** on the loaded host,
   before check 1. The process started for the harness's broadcast at 14:18:30.577 (13471) did not
   complete its startup: `am_anr … failed to complete startup` at 14:19:08.910, killed (`bg anr`) at
   14:19:16.082, and a new one (13557) started for the same broadcast at 14:19:16.705
   (`review2/phone-events.txt`). The first `nodes` answered at 14:19:23.869: about 53 s after the
   first start, and about 60 s after the broadcast was enqueued by round 2's count. That process's
   first ask then took 110 ms (`WATCH_ASKED NO_FEED`), so the cost was the start itself, before any
   ask and outside every budget. `TaqwaApplication.onCreate` installs the crash handler and sets
   the widget hooks, the unlock refresh and the top-up worker; nothing in it builds a feed or asks
   the Wear API.

   **The Lead's call** (widened by round 2): what to do about an ask the budget can cut. A fresh
   install's cold build is one case of it, and a slow or busy phone is another. Three places to
   take it, none chosen here:
   - **The budget itself:** 5 s (`WatchFeedSync.kt:104`), counted from the serve. It cuts only
     the ask. The handshake's listener stops waiting at `CALLER_BOUND` (10 s from when it queued)
     while the ask runs on, and the two receivers that settle the watch do so inside their 8 s
     `WORK_BUDGET_MILLIS`, within `goAsync`'s ten seconds. A longer budget has those bounds to stay
     within: past them, the process's end can cut the ask instead.
   - **How a failed handshake is asked again:** today nothing asks again. The watch is never told,
     and the phone does not retry a `FAILED` ask. A watch that asks once more when no feed comes
     after its handshake, or a phone that serves a failed `WATCH_ASKED` once more, would heal a
     reinstalled watch without waiting for the next reschedule, opening, reconnect or daily nudge.
     Either is new code and tests for the plan that takes it; step 0's forget already makes a
     repeated handshake safe against a wrong `UNCHANGED`.
   - **The cold build's cost** (round 1's option): the first lookup's parse of the bundled city
     list (Task 3's `watchCityName`, which plan 2 puts at about 0.4 s on a phone; `App.kt`'s comment
     names the cost, not a figure) and the first Play services calls of a new process (the
     capability query, asked twice for an ask that sends, and the first `sendMessage`). Cutting it
     helps a fresh install's first sync, and **would not have prevented round 2's failure**, a warm
     ask.

   No code defect is claimed, and neither fix round changed code for it. An emulator on a host at
   load 30 to 40 is slower than a phone. But a phone whose Taqwa was just installed or updated is
   the fresh-install case, and a phone as slowed as round 2's emulator was is the other, so both
   measures are worth taking on the Galaxy in plan 5: a fresh install's first sync, and a
   reinstalled watch's first handshake.
2. **The face after the watch app is uninstalled and installed again** (check 9). The face, here
   installed by adb and not pushed by the app, kept serving the complication data from before the
   uninstall: the app's `ComplicationDataSourceUpdateRequester.requestUpdateAll` after each stored
   feed no longer reached it, and its next prayer stayed London's while the watch held Tripoli's
   feed, until the face itself was installed again. A plain `install -r` (an update) never did this
   (checks 1 to 7, and Steps 1-2). Whether a watch app reinstalled beside a face it pushed with Watch
   Face Push does the same, or loses the face with the app, is not known from the emulator: plan 5's
   Galaxy pass could uninstall and reinstall the app and read the face's next prayer after a feed.
3. **Pairing on emulators needs two halves.** Studio's Pair Wearable makes the phone's forward and a
   reverse on the watch; a watch that restarts loses its reverse. `scripts/wear-pair.sh` now makes
   both (test-first). Plan 5's paired steps (P5.3 Step 4, P5.5 Step 7, P5.6 Step 6, P5.12) can rely
   on it, and Mohamed's Pair Wearable was needed only once.
4. **Removing the forward does not drop a connected watch** (check 10): the established connection
   outlives it. The plan's fallback (the watch stopped by serial, `wear-pair.sh`) is the way that
   works; the plan's text now says so.
5. **A per-app language outside the app's own reaches the app as the phone's next locale the app
   supports** (`en-US` for he-IL on an en-US phone), so the "outside the seven" check on a device is
   the English feed itself; the host tests hold the he-IL tag. Review round 1 notes that a phone
   whose system language is he-IL, with no per-app language, would bring that tag to the format;
   that case was not run on a device.
6. **0b ran**, with the image already on disk and 27 GB free; the AVD is deleted again.
7. The round-1 minors this run did not take (the harness's `place` printing the stored coordinates,
   its receiver without a permission, an `Error` skipping `finish()`, `place` storing no country or
   region, the stale "only the worker" KDoc, the plan's Interfaces not naming `phrase()`) are left as
   they were: the pass ran on the reviewed builds, and changing them now would put the pass on a build
   it did not see. `place`'s missing country and region showed no effect: the launch's backfill left
   the feed as it was (check 2).

## The devices, as found and as left

**Steps 1 and 2:**

- **`emulator-5562`:** found with no AVD `Taqwa_Phone_Wear` and nothing on the port. Left: the AVD
  made (it stays, for SIGN), with no account, no Pixel Watch app, Taqwa uninstalled, no adb forward,
  Play services 26.42.31 (its own update), 246 packages as at first boot, time zone
  Africa/Johannesburg, font scale 1.0 (`0a/left-state.txt`, `0a/phone-left-as-found.png` five
  seconds after); stopped with `adb -s emulator-5562 emu kill`.
- **`emulator-5558`:** found OFF; booted with `tools/watchface/emulator.sh`; the committed drivers'
  `pullfound.sh` recorded app `2b2d77c9` (100038), face `d4f50b1d`, the sample feed `0b3f92ef` (en),
  files `feed.json`, `nudge.anchor`, `profileInstalled`, no `shared_prefs`, 0 Taqwa alarms, 0 Tiles,
  automatic time, font 1.0, TalkBack off, en-US (`step1/watch-state-found.txt`). After the install,
  `restore.sh` printed **STATE AS FOUND** (`step1/watch-state-after.txt`), and the face showed Arch in
  Amber 10 s after (`step1/watch-left-as-found.png`). Stopped with `adb -s emulator-5558 emu kill`.

**Steps 3 to 6** (`wear-5558` then `phone-5562` held 12:55:46 to 13:31; `phone-5564` 13:27 to
13:28, as the ledger's release lines say). Both emulators were found **running and paired** and are
**left running and paired**
(`left/`):

- **`emulator-5558`:** found as Step 3 says (`left/watch-state-found.txt`, recorded by `pullfound.sh`
  before anything changed). Restarted three times in the pass (checks 10 and 11, by its serial,
  booted by `wear-pair.sh` with `scripts/wear-emulator.sh`), never with `pkill`, and its console
  answered each time. Put back with the drivers' `restore.sh` (`PASS_WORK` set to this run's
  scratch folder, its `found/` complete): TalkBack off, font 1.0, no Tile, automatic time, the found
  app `2b2d77c9` and face `d4f50b1d` reinstalled, `files/` as found byte for byte (the sample feed
  `0b3f92ef`, `nudge.anchor`; the pass's `feed.json.delivery` and `refresh.asked` removed), no
  `shared_prefs`; it printed **STATE AS FOUND** (`left/watch-state-after.txt`), and the shot a few
  seconds after shows Arch in Amber (`left/watch-left-as-found.png`). Its boot is newer than found,
  and its Taqwa process is restarted.
- **`emulator-5562`:** Taqwa uninstalled (as found), so its per-app language went with it; the
  account, the Pixel Watch app 5.1.0, Play services, time zone, font scale, TalkBack and locale as
  found (`left/phone-state-found.txt` against `left/phone-state-after.txt`: only the time and the
  links differ). The links: `wear-pair.sh`'s `tcp:5601` forward removed, Studio's chain put back as
  found (`emulator-5562 tcp:64922 → tcp:5601`, the watch's `reverse tcp:5601 → tcp:64922`), plus the
  forward Studio made itself after the last restart (`tcp:60214 → tcp:5601`), left as Studio's. The
  phone's `WearableService`: **1 connected out of 1**, `Taqwa_Wear_OS_6` `IsConnected=true`; the
  watch's: the phone `4e7c91b9` connected. In front: the Play Store's page for the Pixel Watch app
  (`left/phone-left-as-found.png`), since the Pixel Watch app's setup screen found in front
  (`left/phone-found.png`) has closed by itself during the pass (its task is gone from recents);
  this run never opened or touched either, and signed in to nothing.
- **`emulator-5564`:** made for 0b and deleted after it (above).

## The plan's errata, and how each run met them

**Steps 1 and 2:**

1. `git merge --ff-only watch-companion`: already up to date at `1bc9fd42`.
2. Ruling 68 item 3: Steps 1 and 2 only; 0a and the forms on `emulator-5562`; 0b not run; nothing
   signed in to, nothing paired; each emulator under its lock, put back and stopped by serial;
   `emulator-5572` never touched.
3. The WIP commit and the ledger's `[Lane W] P2.7 WAITING FOR SIGN` line.
4. Steps 3 to 6 were out of that run.
5. Plan 3's errata (`plan-errata-2026-10-24.json`, key `p3`): entry 0's edits to Task 7 (check 1
   "Today shows "London" under its title", check 7 "Today says "Update Taqwa on your watch" in place
   of the next prayer's clock time", check 8 starts `WearMainActivity`) written into the plan's text.
6. Rulings 39, 41, 42 and 43: no face file, feed field or copy changed, no release log line added.
7. Ruling 55 item 6 already done (`fa62c182`); `wearApp/build.gradle.kts` not touched.
8. WIP commits after each green step. 9. One short turn on `emulator-5558`. 10. No flake met.

**Steps 3 to 6:**

1. Continued on `1c5fbc66` and its round-1 reviews; `watch-companion` had not moved (`1bc9fd42`), so
   nothing to merge.
2. SIGN done: both emulators taken under `wear-5558` then `phone-5562`, recorded as found (running,
   paired), never stopped while the pass needed the pairing but where the plan's checks 10 and 11 stop
   the watch, and each time brought back by `scripts/wear-pair.sh`, the node back within about 27 s;
   left running and paired. Nothing signed in to.
3. Disk: checked before each build and emulator start (7.6 GB at the first, then 14 to 27 GB as
   others cleared space); never under 4 GB. **0b ran**: 27 GB free when the pass reached it, the
   image already installed; `scripts/phone-emulator.sh --noplay` on 5564, then `adb -s emulator-5564
   emu kill` and `avdmanager delete avd -n Taqwa_Phone_NoPlay`.
4. Ruling 58 item 5's phone half: check 4, with the app in Arabic: `ui.screens` equals the ar-SA
   sample's, and the watch drew and said التسبيح, الجولة, القبلة and اليوم. The task lands only after
   Step 6, with this report.
5. Plan 3's errata touching this task: entry 0 (applied in Steps 1 and 2, followed here: check 1
   reads Today's list, check 7 Today's notice, check 8 starts `WearMainActivity`); entry 12 (= item
   4 above); entry 6's no-break space (the 12-hour `clockTime` "2:47 PM" carries U+00A0, check 4).
   The others concern plan 3's own tasks.
6. Rulings 39, 41, 42, 43 and 55 to 68: no face file, feed field, copy or release log line changed;
   `tools/face-*.py` untouched, so `--check` and the always-on lit share are as they were. The place
   on the device is Ruling 39's (check 1: the city, whole degrees, "4,800", no coordinate), and no
   release line names the origin.
7. Ruling 55 item 6: done before (`fa62c182`); no test vector in either APK, and this run does not
   touch `wearApp/build.gradle.kts`.
8. WIP `35345b73` after checks 1 to 4, folded into the plan's commit.
9. `emulator-5558`: one turn under `wear-5558` for the whole paired pass (the map makes Task 7 alone
   on it), installed with `adb -s emulator-5558 install -r` only, stopped by serial only (bounded
   console calls), put back with `restore.sh`, the shot a few seconds after. No hang, no gfxstream
   error.
10. The Gradle flake: not met (no `EOFException`; the suites green at the first run).

**Found in the plan's text by this run, and written in** (plan 2 Task 7, "As built, Steps 3 to
6"): `wear-pair.sh` makes the watch's reverse too; check 10's link is taken down by the fallback,
since removing the forward leaves a connected watch connected; Studio's forward is on a port of its
own, so the plan's `forward --remove tcp:5601` has nothing to remove.

## The checks at the end

**Steps 1 and 2:** `scripts/test.sh` exit 0 (shared host 2,330, 0 failures, `WatchFeedDebugTest`'s
17 among them); `scripts/test-pairing-scripts.sh` 23 of 23; three mutants of `WatchFeedDebug`
killed; `:androidApp:lintDebug` 0 errors (the harness's `ExportedReceiver` warning, as the
recitation receivers beside it have).

**Steps 3 to 6:**

- `scripts/test-pairing-scripts.sh`: **24 checks, all pass** (6 failed on the old `wear-pair.sh`
  first).
- `scripts/test.sh`: **exit 0** (the three script suites PASS, `swift test` green, the Gradle suites
  up to date, no source they read having changed).
- Fresh: `./gradlew :wearApp:testDebugUnitTest --rerun`: **505 tests, 0 failures**;
  `./gradlew :shared:testAndroidHostTest --rerun`: **2,330 tests, 0 failures, 0 errors** (225
  classes, `WatchFeedDebugTest`'s 17 among them; run again in the round-1 fix, same source).
- `./gradlew :androidApp:minifyReleaseWithR8`: BUILD SUCCESSFUL; `usage.txt` as Step 5 says.
- `git diff --check` clean; the commit in Mohamed's Taqwa identity, with no trailer.
- **Review round 2's fix** changed this report and added `review2/` (the review's own logs, and the
  phone's events buffer read once under `phone-5562`, nothing installed, set or cleared), no source
  or script. Each figure in "For the Lead", item 1, was checked against those logs and the code it
  names.
