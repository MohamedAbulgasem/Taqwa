# Adhan report, 7 Nov 2026: «لا يؤذن» on a realme phone, Android 15

The report came through the in-app mail: Taqwa 1.5.0 (41), Android 15 (SDK 35), a realme phone,
the app in Arabic, no crash report saved. The user wrote only «لا يؤذن» ("it doesn't sound the
adhan"). That mail carried four facts, so it cannot say whether nothing showed, a notification
showed in silence, or only a takbir played. The reporter's phone model, locale and region are left
out of this public report on purpose.

Branch `fix/adhan-report`, from main `feac1414`. For notification code, that commit is identical to
1.5.0: `git diff build-41 feac1414` touches none of it.

## In short

- **Most likely cause: realme's battery management stopping Taqwa.** A force-stop deletes every
  alarm the app has armed, and the app stays silent until it is opened by hand. Reboots and Play
  updates do not wake it. Nothing in the app sees this happen or tells the user.
- **Next most likely: a muted phone, or the default level.**
  - Silent, vibrate or Do Not Disturb at prayer time: the notification shows, but no sound plays.
  - Every prayer still on the default level, Takbir: about 16 s of «الله أكبر» plays. The 1.5.0
    notes promise «الأذان كاملًا».
- **One real app bug was found and reproduced, and is fixed on the branch.**
  - An update keeps the alarms the old build armed.
  - A 1.4.0 Adhan-level alarm that fires under 1.5.0 posts into the new `…_adhan_full` channel.
    Nothing has created that channel yet, so Android drops the post without a word.
  - This happens whenever the update's own reschedule (`MY_PACKAGE_REPLACED`) did not run, which
    is likely on a phone that blocks the app in the background.
  - The drop goes on until the app is opened. Opening the app to send the report heals it, which
    fits a user who wrote once and has not written since.
- **The branch also makes the next report tell the causes apart.** On Android, "Report a problem"
  now adds two short lines: the phone's notification, exact-alarm, battery and ringer settings, and
  roughly when the last prayer alarm reached the app and when the app was installed or last updated.
  Mohamed limited them to two, so the report does not look as if it pulls a lot of data. The prayer's
  sound level is not in them: it is the person's own Taqwa setting, and the reply asks for it.
- **It is safe to fold into the 1.5.1 hotfix.** Every change only acts when something has already
  failed (a missing channel), or runs after the notification is posted, or runs only when the user
  taps "Report a problem". The emulator, the tests and an independent three-lens review agree;
  see "Why this cannot harm a working adhan".

## Ranked causes

The likelihoods are for this report: a realme phone on Android 15, nobody else reporting.

| # | Cause | What the user sees | Likelihood | Does the app tell them today? |
|---|---|---|---|---|
| 1 | realme stops or freezes Taqwa through App battery management ("Smart mode" or "Restrict background activity"), Deep cleanup, Recents "Clear all" or App Quick Freeze. A force-stop deletes every alarm and the WorkManager job. A stopped app gets no `BOOT_COMPLETED` and no `MY_PACKAGE_REPLACED`. | Nothing at all, until Taqwa is opened again | **High** | No |
| 2 | The phone is on silent or vibrate, the notification volume is 0, or Do Not Disturb or Bedtime is on. The adhan is the channel's sound on the notification stream, so it obeys all of these. | The notification shows, with no sound | Medium | Only inside the Sound sheet |
| 3 | Every prayer is still on Takbir, the default for every install (`NotificationSettings.kt:27`). Only the Adhan level plays the full recording, and the 1.5.0 Arabic notes say «الإشعارات تُشغّل الآن الأذان كاملًا». | About 5–16 s of «الله أكبر» | Medium | It shows «تكبير» next to each prayer |
| 4 | **App bug, fixed here.** A 1.4.0 Adhan alarm survives the update and posts into 1.5.0's `_full` channel before any 1.5.0 plan has created it. | Nothing for Adhan-level prayers only; Takbir and Notification prayers still sound | Low to medium. It needs the Adhan level on 1.4.0 and a lost update broadcast, which cause 1 makes likely. | No |
| 5 | The full adhan is the channel's sound, so opening the shade, swiping the notification away or another app's notification sound stops it (AOSP `onPanelRevealed` → `clearEffects`; `NotificationPlayer`). This has been true since 1.5.0 and is noted in the 1.5.0 smoke sheet. | The adhan stops after a few seconds | Low | No |
| 6 | "Alarms & reminders" not granted, which is the default on Android 14 and later. The emulator shows the plan armed with `window=+1h`. | Late, by up to an hour | Low (the symptom is "late", not "doesn't sound") | Yes, on the Prayer and Notifications screens |
| 7 | The user set a prayer's channel to Silent or turned it off (long-press on the notification, or realme's per-app «نغمة الرنين»). Taqwa cannot restore it. | Silent, or nothing | Low | No |
| 8 | "Don't allow" at onboarding, or the person themselves, turns Taqwa's own Prayer notifications switch off. Allowing notifications later in system settings never turns it back on. After "Don't allow" the plan armed at first launch stays until the app is next opened (on the emulator, with notifications then allowed in system settings, it went on posting takbirs for 3 days while the switch showed off); from then on the plan is empty. | Nothing | Low | Only an off switch on the Notifications screen |
| 9 | The standby bucket is `restricted`, or background use is restricted. AOSP allows one alarm a day in the restricted bucket. | Varies | Low | No |
| 10 | Very low: edge cases of "From your phone"; a reminder clamped onto the previous prayer's adhan replacing its sound (far north only); no location stored; races inside the scheduler. | Varies | Very low | No |

### Checked and ruled out

- **The report's Arabic locale:** it resolves to Arabic. No formatting path in the plan or the
  channel names throws, and `values-ar` has every key.
- **The time zone:** no clock change was involved.
- **The sound files:** all eleven are present in `res/raw` and are Ogg Vorbis at 44.1 kHz. Resource
  shrinking is off.
- **The 64-alert capacity:** the plan covers at least 4 days, and the top-up starts at 3.
- **Channel ids:** the scheduler and the receiver compute the same id for every kind, and the new
  `ChannelKeyTest` pins this.
- **Doze:** in forced deep idle, with exact alarms refused, the test adhan was still delivered.
- **A crash during scheduling:** this would leave a crash report, and none was saved.

## What I reproduced

These ran on a new Android 15 emulator (`google_apis` arm64, SDK 35, port 5602, muted with
`-no-audio`). Taqwa was set to a fixed city and the emulator to that city's time zone.

- **Builds used:** the 1.5.0 notification code is this branch's base, built as a debug APK. The
  1.4.0 build is `build-40` as a debug APK, built in a temporary worktree.
- **Port change:** port 5600 could not be used. `emulator-5562` holds `adb forward tcp:5601`, so the
  emulator moved to 5602 (the brief allowed 5602 and up).

| Scenario | Result |
|---|---|
| Baseline. Onboarding with "Not now" for exact alarms. | The plan is armed. Every prayer alarm has `window=+1h0m` (inexact). |
| Test adhan, Makkah, ringer normal | Posted in `prayer_fajr_adhan_makkah_full`, importance 4, sound `raw/adhan_makkah_full`. `mSoundNotificationKey` is set to the Taqwa notification, so its sound played. |
| Ringer on vibrate | Posted, but `mSoundNotificationKey=null`: no sound (cause 2). |
| Do Not Disturb set to priority | Posted, but `mSoundNotificationKey=null`: no sound (cause 2). |
| Forced deep Doze, exact alarms refused | The test adhan was posted on time. |
| `am force-stop`, the end state of realme's kill | 67 pending Taqwa alarms → **0**. WorkManager jobs → 0. `stopped=true` (cause 1). |
| Reboot after the force-stop | Still `stopped=true` with **0** alarms. `BOOT_COMPLETED` does not reach a stopped app. |
| Play-style update (`install -r`) of a running app | Re-armed (55 → 55) through `MY_PACKAGE_REPLACED`. |
| The same update of a **stopped** app | Still **0** alarms and still stopped. An update does not revive a killed app. |
| Restricted standby bucket, two test adhans | Both were delivered. Inconclusive: the app had just been in the foreground. |
| AOSP "Restricted" battery (`appops RUN_ANY_IN_BACKGROUND deny`, unplugged), clock moved past Dhuhr | Alarm Manager lists Taqwa under "Restricted packages". **Dhuhr never posted.** Its alarm left the pending list and the recorded last alert stayed the 5-h-old test. Even the `TIME_SET` broadcast was held until the restriction was lifted. This is the AOSP form of realme's "Restrict background activity" (cause 1). |
| **1.4.0 → 1.5.0 with the update broadcast lost.** The update receiver was disabled with `adb root` + `pm disable` to stand in for realme blocking it, Fajr was set to Adhan on 1.4.0, then the clock was moved past Fajr. | The 1.4.0 alarm object survived the update unchanged (`Alarm{40af66}`), and no `_full` channel existed. At Fajr, NotificationService logged `No Channel found for pkg=world.taqwa.app, channelId=prayer_fajr_adhan_full` and **nothing was posted** (cause 4). |
| **The same, updating to this branch** | The surviving alarm posted into a newly created `prayer_fajr_adhan_full` with `raw/adhan_full`, and its sound played. The plan was rebuilt (new alarm objects, 59 pending). No "No Channel found". |

This also corrects a belief written into three comments, including the manifest: an update does
not drop the old build's alarms. They survive it, with that build's extras.

Not reproducible here: realme's own power manager, Smart mode, Quick Freeze and Deep cleanup. The
emulator can only show their end state (a force-stop or a blocked broadcast).

## The fix

The code is in commits that can be taken apart:

- `271489ef` (on `fix/adhan-report`): the channel fix alone (part 1 below). Checked out on its own,
  `assembleDebug` builds and all 162 notification host tests pass.
- `3ade30fc` (on `fix/adhan-report`): the first report lines, many of them, on top of the fix.
- On `fix/adhan-report-lines`, after Mohamed's decision: the report cut to two short lines (part 2),
  then one privacy sentence in the seven policies, then this report brought up to date, then the
  second review's fixes (the policy sentence named the level, the bug form, this report).
  `271489ef`'s behaviour is untouched.
- On `release-1.5.1-marks`, the channel fix is its own commit (the same change as `271489ef`), and
  everything else from `fix/adhan-report-lines`, with the Lead's integration, is one commit after it:
  the level left line 2, the AlarmLog and the policy sentence (see "Integration into 1.5.1").

If the report lines were ever declined, the Lead could still take `271489ef` alone.

### 1. The alarm makes its channel if it is missing (`PrayerAlarmReceiver`, `AndroidNotificationScheduler.ensureChannel`)

- **What happens now:** before posting, the receiver asks for the channel it is about to post
  into.
  - If the channel exists, nothing else happens. That is every alarm armed by the build that is
    running.
  - If it is missing, the receiver creates it exactly as the plan would have (same id, name,
    importance and sound), posts, and then rebuilds the plan, so the next alarms are this build's
    own.
- **What it leaves alone:** a channel the person blocked or silenced still exists, so it is left
  untouched.
- **Where the key logic lives:** `channelKeyOf` moved unchanged from a private method to a
  top-level `internal` function.
- **Test:** `ChannelKeyTest` proves that for every kind, prayer, sound and voice, the channel made
  is the channel the alarm posts into.

### 2. The report says what the phone says, in two lines (`NotificationDiagnostics`, `NotificationState`, `AlarmLog`)

**Mohamed limited the lines to two.** His words: "let's not add more than two (not so long) lines
for notification so we don't make the report seem like it's pulling a lot of data". `3ade30fc` added
thirteen plus one per alert channel; `fix/adhan-report-lines` keeps two, in English `key: value`
form like the device lines above them, after a blank line:

- **Line 1, the phone's settings now:** notifications `on`/`off` (the phone's switch for the whole
  app, not Taqwa's own Prayer notifications switch, which neither line shows); exact alarms
  `on`/`off`; battery `optimised`, `not optimised` or `restricted` (Android's "Restricted" battery
  setting, which wins); ringer `normal`, `vibrate`, `silent` or `do not disturb` (any Do Not Disturb
  filter, which wins).
- **Line 2, the history:** roughly when the last prayer alarm reached the app, with ` (no channel)`
  if Android had no channel to post it into; then when the app was last installed or updated (from
  `PackageInfo`, no log needed). Until a prayer alarm has been recorded (the record starts with
  1.5.1), it says `none recorded`. Not the prayer's level: that is the person's own Taqwa setting,
  and About («الموقع والإعدادات والعلامات والعدّاد لا تغادره»), the site and the policy say settings
  never leave the phone.

```
notifications: on, exact alarms: off, battery: restricted, ringer: vibrate
last alarm: 3 d ago, app updated: 5 d ago
```

At their longest a phone writes 84 and 66 characters:

```
notifications: off, exact alarms: on, battery: not optimised, ringer: do not disturb
last alarm: under 1 h ago (no channel), app updated: under 1 h ago
```

The formatter's own worst case, `exact alarms: off` beside `battery: not optimised`, is 85, but no
phone gives it: below Android 12 exact alarms are always on, and from 12 an app the battery
optimisation leaves alone may always set them (the emulator said `exact alarms: on` with the
permission denied until Taqwa left the allowlist). Common states are 71 to 75 characters, 81 with Do
Not Disturb on.

**What went.** The per-channel lines, the standby bucket, the notification volume, the Do Not
Disturb detail beyond the ringer word, the plan's size and reach, the last system event, the last
background refresh, the alarm's lateness and its `EXTRA_AT` extra, and the `AlarmLog` writes in
`SystemEventReceiver` and `NotificationTopUpWorker`. Those two files and
`AndroidNotificationScheduler` are back to `271489ef`'s exactly.

**What it leaves out.** Every time is relative and no finer than the hour, then the day ("under
1 h ago", "5 h ago", "3 d ago"). The mail carries its own send time, so "47 min ago" would have given
a prayer's clock time and roughly the place; the review caught this. The prayer's name is not
written either. No clock time or place is ever written. Nothing it reads needs a permission. iOS
adds nothing yet (`notificationState()` returns null), and the crash sheet's mail is unchanged.

**How the alarm is recorded.** `AlarmLog`, one small preference file, keeps only the last prayer
alarm: its time and whether its channel was missing. `PrayerAlarmReceiver` writes it
after `notify()`, inside `runCatching`, and only for a prayer's own alarm, not for reminders,
Tahajjud, adhkar, Duha or the iqama. Since `271489ef` makes a missing channel before the post,
` (no channel)` now appears only if making it failed.

**How it would answer this report.** If this user sends a report from 1.5.1, the two lines split
the causes as far as two lines can:

- `last alarm` days old, or `none recorded` days after `app updated`: realme is stopping the app
  (cause 1), **or Taqwa's own Prayer notifications switch is off (cause 8)**, which reads the same
  because nothing is armed. Ask about the switch first; it is one tap. The update to 1.5.1 is itself
  the first test: an alarm should be recorded within hours of `app updated`.
- `battery: restricted`: cause 1.
- `ringer: vibrate`, `silent` or `do not disturb`: cause 2.
- `(no channel)`: cause 4, if the channel could not even be made.
- `exact alarms: off`: cause 6.
- `notifications: off`: the whole app is blocked in the phone's settings.

What two lines cannot show: the prayer's level (cause 3, which the reply asks about), a single
channel the person silenced (cause 7), the shade cutting the adhan (cause 5), Taqwa's own Prayer
notifications switch (cause 8, seen only as an old `last alarm`) and the standby bucket (cause 9, apart from background restriction, which reads
`battery: restricted`). A recent `last alarm` with `ringer: normal` points to causes 5 and 7, and the
reply's questions cover them. Whether line 1 should also carry Taqwa's own switch (for example
`app: off`) is Mohamed's call.

### 3. Comments and debug

- The manifest, `SystemEventReceiver` and `SystemEventReceiverTest` comments now say that an
  update keeps the old alarms.
- A debug-only harness command, `--es cmd diagnostics`, logs the report lines (still the two).

### Tests added

- `NotificationDiagnosticsTest` (13 tests, rewritten for the two lines): the two lines in order;
  never more than two, with the formatter's worst case pinned at 85 and 66 characters and the
  longest a phone can write at 84; every word of line 1
  (notifications and exact alarms on/off, the three battery words with "restricted" winning, the
  four ringer words with Do Not Disturb winning, an unknown ringer code); no level word in line 2,
  and ` (no channel)`; `none recorded`; a fact that cannot be read leaving only its own part out; times
  to the hour, then the day; no clock time and no minute; a moment after now after a clock change.
- `ReportMailTest` (12 tests, 6 of them for the lines).
  - With no lines, a long report's body is pinned exactly to the old cut.
  - The lines come after a blank line.
  - A trace of short frames fills the room the lines leave, landing within 40 characters of the
    cap, never over.
  - A trace with no newline to cut at stays under the cap too.
- `ChannelKeyTest`: 2 tests (part 1).
- `3ade30fc`'s `NotificationChannelsTest` addition went with the channel lines.

### Why this cannot harm a working adhan

- **The missing-channel path is dead code for a working user.** Their channel always exists. The
  only new work before `notify()` is a scheduler object (two `getSystemService` calls and opening its
  preference file) and one `getNotificationChannel` lookup, inside `runCatching`. The lookup can
  only answer "exists", which changes nothing.
- **The plan rebuild runs only when a channel had to be made.** That is the one case that is
  already broken today. The rebuild is the same full replace the app runs at every launch.
- **The log write cannot stand between the alarm and the adhan.** There is one, in the receiver,
  for a prayer's own alarm. It comes after `notify()`: a channel lookup for " (no channel)" and a
  small preference write, inside a catch-all. It can add milliseconds before the widget refresh and
  the top-up start (the first open of its file waits on the main thread, and the broadcast's
  `finish()` waits for the `apply()` to land), never to the notification already posted, which is
  enqueued and whose sound plays in the system. Nothing else on the notification path changed: no
  new extra on the alarm's intent, and the system-event receiver and the background refresh are as
  they were.
- **The report lines are only read when the user taps "Report a problem".** If reading them fails,
  the mail is the old one, and with no lines the body is byte-identical (tested).
- **1.5.0 → 1.5.1 changes no channel id.** Alarms armed by 1.5.0 find their channels, and the new
  path does nothing.

### Independent review

A workflow reviewed the full diff through three read-only lenses.

**Harm to working users: safe.**

- A 1.5.x alarm always finds its channel, so `madeChannel` is false and the old gating holds.
- A blocked or silenced channel is never touched.
- Everything new on the notify path is inside `runCatching`.
- `EXTRA_AT` leaves the PendingIntent's identity alone. (It has since gone with the two-line limit.)
- The rebuild cannot loop, because the planner keeps only entries after "now".
- 1.5.0 → 1.5.1 changes no channel id.

**Correctness: safe with changes. Refute the fix: safe with changes.** Changes made after the
review (several of these lines then went when Mohamed limited the mail to two; see part 2):

- **Privacy (both lenses):** event times are no finer than the hour (see above). There is a test
  for it.
- **An ended plan** was printed as "the last in under a minute". It now reads "the last 2 d ago
  (plan ended)", with a test.
- **A refresh recorded after a clock was set back** now reads "in 3 h", like the other two lines,
  with a test.
- **"last alert" became "last alarm"** and gains ", no channel" when Android dropped the post, so a
  dropped post is not read as delivered.
- **"app updated: …" was added**, so "none recorded" can be read.
- **The report-cap tests** now reach the cap, and the self-comparing test was replaced by an exact
  expected value.
- **Comments:** `AlarmLog`'s KDoc and the last stale "every alarm gone" comment in
  `SystemEventReceiver` were corrected.

**Accepted, not changed:**

- **A made channel the rebuild does not keep.** Seen on an emulator, as root: an alarm whose channel
  is missing, for a sound the current plan does not use for that prayer (Dhuhr fired as Adhan while
  Dhuhr was set to Takbir), makes its channel and posts, and the rebuild it starts sweeps that channel
  within milliseconds as stale. Deleting a channel withdraws its notification, so nothing shows and
  nothing sounds, though `AlarmLog` records it. A real old alarm carries the settings it was armed
  from, and the rebuild uses the same settings unless the person changed one, which re-arms the plan
  at once; so its channel is one the rebuild keeps, as the emulator's update runs show. The cure, if
  it is ever needed, is for the sweep to spare a channel that has a notification showing. Left out
  of the hotfix: it changes the rebuild every launch runs, Mohamed asked for no further adhan work
  now, and no case found outside root reaches it.
- **A possible second adhan.** It needs an update straight from 1.4.0 on a phone that blocked the
  update broadcast, and a prayer the newer engine moved a few minutes later (for example the 1.5.0
  Malaysia refit). The rescued old alarm sounds, the rebuilt plan arms the same prayer at its new
  time, and the adhan can sound twice. Before the fix it sounded zero times.
- **`ensureChannel` throwing.** Nothing known can make it throw. If it did, the post is dropped
  exactly as today, and no rebuild is forced, on purpose: a device where channel calls throw would
  fail the rebuild itself after its cancel-all.
- **No live alarm check.** Opening the app to send the report re-arms everything, so a live
  check would always read healthy. The "last alarm" and "app updated" parts carry that signal
  instead. ("alerts planned" went with the two-line limit.)
- **The privacy policy did not mention these lines.** Mohamed accepted one sentence; it is now in
  all seven policies (decision 2 below).

## Verification

Of the two-line version, on `fix/adhan-report-lines`:

- **`scripts/test.sh`, the whole suite:** green, `BUILD SUCCESSFUL in 5m 48s`, with 0 failures and
  0 errors in every suite. Three fewer tests than `3ade30fc` (2239 and 2219): the two report-cap
  tests for many lines and the channel-list test went with the lines they tested. Run again after
  the second review's fixes: `BUILD SUCCESSFUL in 5m 26s`, the same counts (the new 84-character
  check is an assertion inside an existing test), 0 failures and 0 errors; widgetcore's inputs did
  not change, so Gradle kept its results.

  | Suite | Tests |
  |---|---|
  | shared, iOS simulator | 2236 |
  | shared, Android host | 2216 |
  | widgetcore, iOS | 119 |
  | widgetcore, Android host | 121 |

- **`scripts/ios-build.sh`:** `** BUILD SUCCEEDED **`, `TaqwaWidget.appex: 3 MB (limit 15 MB)`,
  both times. Shared code changed: an iOS `actual` returning null.
- **`./gradlew :androidApp:assembleDebug`:** `BUILD SUCCESSFUL`.
- **The site and strings checks:** `site/build.py --check` ("links, assets, structured data,
  sitemap and timetables all good", the privacy pages rebuilt from the seven policies, each showing
  the new sentence and the 7 November date), `tools/i18n-check.py` and `scripts/check-strings.sh`
  clean, both times.
- **Locks:** test.sh and ios-build.sh ran holding `~/.cache/taqwa-locks/ios-sim-tests` and
  `ios-sim-tests.lock`, taken with mkdir and an owner file, and released afterwards.
- **Device check:** the emulator runs in the table above were made on `3ade30fc`'s build. The
  surviving 1.4.0 Fajr alarm posted with its sound and "No Channel found" appeared 0 times; that
  path is `271489ef`'s and is unchanged. The second review then ran this branch's two-line code
  (the commits after `c27dd23c` change no code) as a debug build on another Android 15 emulator:
  - In 17 phone states, `--es cmd diagnostics` printed exactly two lines. In 8 of them, Arabic
    included, the real About screen's mail (the `mailto:` Gmail was handed) carried them after the
    device lines.
  - A real Fajr alarm fired through `PrayerAlarmReceiver`, posted, and was recorded with its
    channel present. (That build still printed the level; the integration into 1.5.1 removed it.)
  - The longest settings line it could produce was 84 characters.
  - **Not checked on that build:** the adhan's sound and logcat's "No Channel found". A few-minute
    debug smoke on an emulator the Lead names (`--es cmd adhan`, then `--es cmd diagnostics`)
    closes that.
- **Not checked on a real realme phone**, nor on the LoopPhone. The realme behaviour is inferred
  from realme's own support articles and from its AOSP end states, reproduced here.

## Integration into 1.5.1

The Lead brought `fix/adhan-report-lines` into `release-1.5.1-marks` and then:

- **Took the level out of line 2.** The level is the person's own Taqwa setting. About
  (`about_stays_body`, in all eight values directories), the site's home page and the policy ("None
  of this is sent to us or to anyone") say settings never leave the phone; without the level they
  stay true with no string change, and the reply already asks Takbir or Adhan. `AlarmLog` no longer
  stores it, and the seven policy sentences say what the two lines now carry.
- **Took the reporter's details out of this report** (phone model, model number, locale and
  region) and made every promise in the draft reply true: it says what the two lines carry ("no
  location, no prayer times") instead of "no personal data", and it is signed «دعم تقوى» rather than
  «فريق تقوى», since one person makes Taqwa.
- **Added the two Play bullets** (Android only) and an Android section to the 1.5.1 smoke sheet.
  The App Store notes and the App Review notes are unchanged.
- **Kept the reporter's details out of the branch's history, not only its last tree.** The early
  commits of `fix/adhan-report` named the phone in a subject line and in this file, and `main` cannot
  be rewritten once they reach it. So on `release-1.5.1-marks` the channel fix is one commit and the
  rest one more, written from the clean tree; nothing on the branch names the phone, the locale or
  the region. `fix/adhan-report` and `fix/adhan-report-lines` still do, and are never pushed.
- **A review pass on the words.** The Play bullets say "how your phone handles notifications", not
  "notification settings" (line 2 is not a setting, and in Arabic «إعدادات الإشعارات» reads as
  Taqwa's own Settings › Notifications, which never leaves the phone), quote "Report a problem" in
  English as the other six do, and say "after an app update", so they are not read as "after this
  update". The policy sentence says how far the phone limits Taqwa's battery use rather than "its
  battery setting", beside a policy that says your settings are never sent. The draft reply's
  promise is the two lines' own: they carry no location and no prayer times.

## Draft reply to the user (Arabic, for Mohamed to send)

> السلام عليكم ورحمة الله،
>
> شكرًا لك على رسالتك، ونأسف لأن الأذان لم يعمل عندك. لنعرف السبب بدقة، نرجو أن تجيبنا عن هذه الأسئلة:
>
> 1. عند وقت الصلاة، ماذا يحدث بالضبط؟ هل يظهر إشعار تقوى بلا صوت، أم لا يظهر شيء أبدًا، أم تسمع «الله أكبر» فقط لثوانٍ؟
> 2. في تقوى › الإعدادات › الإشعارات، هل مفتاح «إشعارات الصلاة» مفعّل؟ وماذا تجد مكتوبًا بجانب كل صلاة: «تكبير» أم «أذان»؟ الأذان الكامل لا يُشغَّل إلا إذا اخترت «أذان» لتلك الصلاة.
> 3. هل يعود الأذان بعد أن تفتح تقوى، ثم يتوقف بعد «مسح الكل» من التطبيقات الأخيرة أو بعد إعادة تشغيل الهاتف؟
> 4. هل يكون الهاتف وقت الصلاة على الوضع الصامت أو الاهتزاز أو «عدم الإزعاج»؟
>
> هواتف realme قد توقف التطبيقات في الخلفية لتوفير البطارية، وحينها لا يصل الأذان حتى يُفتح التطبيق من جديد. نرجو تجربة ما يلي:
>
> 1. الإعدادات › البطارية › إعدادات توفير الطاقة › إدارة بطارية التطبيقات › تقوى، ثم اختر «السماح بالنشاط في الخلفية».
> 2. اضغط مطولًا على أيقونة تقوى › معلومات التطبيق › استخدام البطارية، وفعّل «السماح بالتشغيل التلقائي» إن وجدته.
> 3. اقفل تقوى في التطبيقات الأخيرة: اسحب من أسفل الشاشة إلى أعلى، ثم اضغط النقاط الثلاث فوق بطاقة تقوى واختر «قفل». ويُستحسن ألا تستخدم «التنظيف العميق».
> 4. معلومات التطبيق › إدارة الإشعارات: تأكد أن «السماح بالإشعارات» مفعّل، وأن «نغمة الرنين» مفعّلة.
> 5. في تقوى › الإعدادات › الإشعارات، إن ظهر زر «السماح بالمنبهات الدقيقة» فاضغطه واسمح لتقوى، حتى يأتي الأذان في وقته بالضبط.
> 6. افتح تقوى مرة واحدة بعد هذه الخطوات، ثم انتظر الصلاة التالية.
>
> وبعد التحديث القادم (1.5.1)، إن بقيت المشكلة، نرجو أن ترسل تقريرًا جديدًا من تقوى › الإعدادات › عن تقوى › الإبلاغ عن مشكلة. سيضيف التقرير هذه المرة سطرين قصيرين: هل الإشعارات والمنبهات الدقيقة مسموح بها لتقوى، وإلى أي حد يقيّد الهاتف استهلاكه للبطارية، وهل الهاتف على الوضع الصامت أو الاهتزاز أو «عدم الإزعاج»، ثم متى وصل آخر إشعار صلاة تقريبًا ومتى ثُبّت التطبيق أو حُدّث آخر مرة، وهذا يساعدنا على معرفة السبب. ولا يحمل السطران موقعك ولا مواقيت صلاتك، وترى التقرير كاملًا قبل أن ترسله.
>
> جزاك الله خيرًا،
> دعم تقوى

**Where the labels come from:**

- **Taqwa's own Arabic strings:** «تكبير», «أذان», «الإشعارات», «إشعارات الصلاة», «عن تقوى»,
  «الإبلاغ عن مشكلة», «السماح بالمنبهات الدقيقة».
- **realme's Arabic support site** (realme.com/eg, realme UI 6.0 and 7.0 articles: 2273621,
  2313551, 2310499, 2317596, 2273631, 2276606):
  - «الإعدادات › البطارية › إعدادات توفير الطاقة › إدارة بطارية التطبيقات»;
  - «السماح بالنشاط في الخلفية»;
  - «معلومات التطبيق › استخدام البطارية › السماح بالتشغيل التلقائي»;
  - «قفل»;
  - «التنظيف العميق»;
  - «إدارة الإشعارات › السماح بالإشعارات»;
  - «نغمة الرنين».
- **Not verified on a phone:**
  - These are realme Egypt's translations. The reporter's phone may differ by a word.
  - realme contradicts itself on whether «السماح بالتشغيل التلقائي» exists on realme UI 6.0,
    hence «إن وجدته».
  - I found no Arabic realme text for the notification-volume slider, so the reply does not name it.

## For Mohamed to decide

1. **Fold this into 1.5.1?** I recommend yes. It is a silent fix plus two support-only mail lines.
   There is no UI change and no new app string; the one policy sentence is written in all seven
   languages.
2. **Privacy policy: decided.** Mohamed accepted one honest sentence. Each of the seven policies
   now says, after its crash paragraph, that on Android the "Report a problem" email also says
   whether Taqwa's notifications and exact alarms are allowed, how far the phone limits its battery
   use, whether the phone is on silent, vibrate or Do Not Disturb, and roughly when the last prayer
   alert came and when the app was installed or last updated, and that the person sees it before
   sending. The
   second review found that the first wording left out the sound and ended "nothing more", which the
   email as a whole is not (it has always carried the Android version and the language); "nothing
   more" went in all seven, and at the integration the Lead took the sound out of the email instead
   of naming it. Each policy is dated 7 November 2026, as the 29 September
   change was. The site's privacy pages are built from these files, and the public bug form
   (`.github/ISSUE_TEMPLATE/bug.yml`) no longer says the email holds "nothing else". The old crash
   paragraph's "nothing else" was already short before this branch (the saved report also carries
   its time, the thread, the Android version and the language); correcting it is the Lead's call.
   The same "nothing else" is in the app's own crash sheet (`crash_sheet_body`, in all eight values
   directories), so the policy alone cannot change: it is a string change for a later release, not
   this hotfix.
3. **Release notes: decided.** Mohamed accepted a short line. The Play notes of 1.5.1 (42) carry it
   in all seven languages, with a second one for the channel fix. In English: "Report a problem"
   (quoted) now adds two short lines about how your phone handles notifications; and Notifications:
   more reliable after an app update on some phones. The App Store notes do not, as iOS gets neither
   change.
4. **Follow-ups not built here** (each larger, or touching the working path):
   - **(a) A "missed adhan" card.** On API 35, `ApplicationStartInfo.wasForceStopped()`, or an
     `AlarmLog` horizon check, can tell the app it was stopped. It could then show one dismissible
     card naming the realme steps. Low risk, but it needs a design and 7 languages.
   - **(b) A realme paragraph on the support page's `#late` section** (7 languages), with the
     labels above.
   - **(c) The full adhan through a short media foreground service**, so that opening the shade or
     another notification does not cut it (cause 5). High risk: it touches the one path that must
     not fail and changes the silent-mode behaviour. Not for a hotfix.
   - **(d) A one-line hint under the Adhan voice row** that the full adhan needs the Adhan level
     (cause 3).
   - **(e) Turn the master switch on when notifications are granted later.** Only if it was off
     because of the onboarding "Don't allow" (cause 8). And cancel the launch plan when onboarding
     ends with the switch off, so the switch and the plan agree before the app is next opened.
   - **(f) Do not plan a reminder that lands exactly on the previous adhan** (far north).
   - **(g) Store the build's version code with the plan** and rebuild on the first alarm after any
     update. This would also heal old plans whose channels still exist (their text in an old
     language, for example).
