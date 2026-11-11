# The Wear spike: how the countdown moves each minute

20 October 2026. Plan 1, Task 4, which settles spec 2.3's open choice before Task 5 builds the
complication sources. Branch `watch-companion`, base `af9dbea3`, run on the Wear OS 6 emulator that
`scripts/wear-emulator.sh` starts (`Taqwa_Wear_OS_6`, serial `emulator-5558`). The spike's code was
thrown away after the run and never committed (Ruling 3); what Task 5 needs of it is quoted in
"What Task 5 takes from this". The screenshots are in `2026-10-19-wear-spike/`.

## Verdict

**Dynamic values, with one timeline entry per period.** The ring and the countdown are complication
dynamic values, which the system evaluates on the watch, so they move every minute while the app
is not running. Each period between two starts is one entry of a `ComplicationDataTimeline`, so
the next prayer takes over at its start without the app either. The app runs only when its source
is asked for data: when a face takes it, when the feed changes, and when the timeline needs
extending. The fallback the plan held in reserve (a static entry a minute for 120 minutes,
re-requested every hour) is not needed where dynamic values are supported. The library treats that
as given from API 34 (Wear OS 5), and the Taqwa face needs Wear OS 6 anyway.

Three things Task 5 must do that the plan does not say yet:

1. **Map the digits in our own expression.** `DynamicInt32.format()` writes the digits of the
   language the watch system runs in, not the feed's, and not even the face's: under an Arabic feed
   on this English emulator it wrote `بعد 2 د`. A chain of conditions per digit, built from the
   feed's `digitZero`, wrote `بعد ٢ د`. This holds for Western digits too. A watch whose own
   language writes Arabic-Indic digits would, by the evaluator's code, put them into `format()`'s
   numbers under an English phone. The watch's own language was not changed in this run.
2. **End the timeline with a `NoDataComplicationData` entry.** The Watch Face Format runtime on
   this image ignored `validTimeRange`: past the last start of a timeline without such an entry,
   the face went on showing "in under a minute" and a full ring. With a NoData entry from the last
   start on, both slots went empty, which is the plan's empty form (Review Focus 3). A NoData
   *default* does not work: the library refuses typed entries under it, and the refusal crashed
   the app.
3. **Keep the timeline to a day or two, and keep it topped up.** The digit chains make an entry
   about 5 to 10 KB on the wire, so the whole 31-day feed cannot be one timeline. See "Decisions"
   below for the length and the refresh.

**No permission prompt** appeared, on Google's own face or on a Watch Face Format face whose fixed
slots named our source as their default. No safe-watch-face meta-data was needed for the latter.

## How it was run

- **The emulator:** the Wear OS 6.0 image (`system-images;android-36;android-wear-signed;arm64-v8a`,
  build BP2A.250605.006.E4, `ro.build.version.sdk` 36), the Wear OS Large Round profile
  (454 × 454, 320 dpi), headless with SwiftShader. A cold first boot took 24 s. The emulator takes
  the Mac's zone (Africa/Johannesburg, the sample's), and runs in en-US. It was taken off its
  virtual charger (`emu power ac off`), because the charging screen covers the face.
- **The source:** a `SuspendingTimelineComplicationDataSourceService` offering `RANGED_VALUE` (and
  `LONG_TEXT`, so that a wide slot could show the whole countdown), update period 0, reading the
  stored feed. The periods came from `WatchRules.moment`, chained from one start to the next. The
  current period was the timeline's default, and each of the four after it a `TimelineEntry`.
- **A short period:** to see a step every minute, the feed was edited so that the current period
  lasted six minutes. Yesterday's Isha was moved to T − 6 min and today's Fajr to T, a whole minute
  a few minutes ahead. The file went into the app's storage with `run-as`, and a spike receiver
  stored it through `FeedStore.write` and called `requestUpdateAll()`.
- **The app not running:** before each run the app's process was ended with
  `am kill world.taqwa.app` (no force-stop), and each screenshot was paired with
  `pidof world.taqwa.app`. Logcat recorded every request the source answered and every start of
  the process.
- **Two faces:** the image's default face, Concentric (`OrbitaWatchFaceService`), has no
  complication slots, so Google's Utility face from the same package stood in for it. Utility has
  three round slots and a wide pill at the top. And because the Taqwa face is a Watch Face Format
  face, a throwaway one was built with `aapt2` alone and sideloaded:
  - package `world.taqwa.app.watchfacepush.spike`, `hasCode="false"`, format version 2;
  - two fixed slots (`isCustomizable="FALSE"`), a ring (`RANGED_VALUE`) and a line (`LONG_TEXT`);
  - each slot's `DefaultProviderPolicy primaryProvider` named the spike's source;
  - it prints the ring's value under the countdown, and its own clock with seconds.

## Evidence

### 1. Every minute, with no app

[Sheet 1](2026-10-19-wear-spike/1-utility-english.png): Utility, the English sample, our digits,
Fajr moved to 03:33:00. The process was ended at 03:28:08.

| Shot | Top line | Round slot | App process |
|---|---|---|---|
| 03:29:20 | FAJR IN 3 MIN | ring a little over a third | none |
| 03:30:20 | FAJR IN 2 MIN | ring past half | none |
| 03:31:20 | FAJR IN 1 MIN | ring near three quarters | none |
| 03:32:20 | FAJR IN UNDER A MINUTE | ring nearly full | none |
| 03:33:20 | DHUHR IN 8 H 59 MIN | ring empty: the next entry | none |
| 03:34:20 | DHUHR IN 8 H 58 MIN | ring empty | none |

From 03:28:08 to 03:34:30, logcat shows no request answered and no start of the process.

[Sheet 2](2026-10-19-wear-spike/2-wff-arabic-our-digits.png): the Watch Face Format face, an
Arabic feed (the plan's Arabic phrases, `digitZero` '٠'), our digits, Fajr at 03:45:00. The face
prints the ring's value in per cent.

| Shot | Countdown | Ring | App process |
|---|---|---|---|
| 03:41:20 | الفجر · بعد ٣ د | 38.6 | none |
| 03:42:20 | بعد ٢ د | 55.3 | none |
| 03:43:20 | بعد ١ د | 71.9 | none |
| 03:44:20 | بعد أقل من دقيقة | 88.6 | none |
| 03:45:20 | الظهر · بعد ٨ س ٤٧ د | 0.1 | none |
| 03:46:20 | الظهر · بعد ٨ س ٤٦ د | 0.3 | none |

The values are right to the second: 38.6 is (03:41:19 − 03:39:00) / 6 min, the instant the face's
own clock shows in that shot. The countdown floors, as the phone's widgets count. Just after
03:27:00, with a little under 66 minutes left before 04:33, Utility read "IN 1 H 5 MIN". The
two-digit numbers come out in the right order in the right-to-left line (`٤٧`).

### 2. The digits

[Sheet 3](2026-10-19-wear-spike/3-wff-arabic-format.png). The same face and Arabic feed with
`DynamicInt32.format()` in place of our chains read `بعد 2 د` and `بعد 8 س 42 د`. Those are Western
digits, from the emulator's en-US.

The face runtime's own language was then set to Arabic
(`cmd locale set-app-locales com.google.wear.watchface.runtime --locales ar-EG`, cleared
afterwards). The face's own clock turned Arabic-Indic (`٠٣:٥١:١٩`), but the countdown stayed
Western, also after a fresh request at 03:51:32. So the values are evaluated outside the face's
process.

The library says why: its evaluator formats with `ULocale.getDefault()`
(`ComplicationDataEvaluator`, `NumberFormatter`). Neither `IntFormatter` nor `DynamicFormatter`
takes a locale. `format()` can therefore never follow the phone's digit set, so our chains are
needed for every digit set, '0' included.

### 3. Always-on

[Sheet 4](2026-10-19-wear-spike/4-wff-always-on.png). `KEYCODE_SLEEP` put the watch in always-on
(wakefulness Dozing, display DOZE). The face then drew once a minute, on the minute.

- **The countdown** was each minute's own at every frame: ٤, ٣, ٢, then ١ د, and at 03:57:00
  `الظهر · بعد ٨ س ٣٦ د`.
- **The ring** was the previous minute's. At 03:53 it read 22.8, the value of 03:52:22 when the
  watch slept, then 33.3 at 03:54, 50 at 03:55 and 66.7 at 03:56, then 0 after the start.
- **Woken at 03:58:33** it read 0.3, that second's own value.

A minute's lag is about a two-hundredth of a three-hour ring, so nothing needs doing about it. In
always-on "under a minute" is never drawn, because the frames fall on whole minutes and so do the
starts. The app had no process during the run.

### 4. The end of the timeline

[Sheet 5](2026-10-19-wear-spike/5-wff-timeline-end.png).

- **`validTimeRange` alone:** the timeline held only the current period, with a `validTimeRange`
  of [previous, next). 20 s and 80 s after `next`, the face still showed "FAJR in under a minute"
  and a full ring.
- **A NoData entry at the end:** the same timeline with
  `TimelineEntry(TimeInterval(next, next + 30 days), NoDataComplicationData())` appended. Both slots
  were empty at +20 s and at +80 s.
- **A NoData default** (with every period, the current one too, as an entry) was refused.
  `ComplicationDataTimeline.validate()` threw `IllegalArgumentException: TimelineEntry's
  complicationData must have the same type as the defaultComplicationData or be
  NoDataComplicationData`. It threw inside the update path of `requestUpdateAll()`, uncaught, and
  the app's process died.

The end of a timeline was not tried on Utility.

### 5. Permission

There was no prompt in either case.

- **Utility:** the source was picked in Utility's own editor, through the system's chooser, with
  no prompt. Google's faces hold `RECEIVE_COMPLICATION_DATA`, granted by default.
- **The Watch Face Format face:** its fixed slots filled from our source the moment the face was
  made active. Our manifest had no `SAFE_WATCH_FACES`, and the face package holds no permission.
  The active wallpaper was `com.google.wear.watchface.runtime/.DeclarativeWatchFaceRuntime0`, which
  holds `RECEIVE_COMPLICATION_DATA_PRIVILEGED`. Google's reference for `ActiveComplicationConfig`
  says that Watch Face Format faces always count as safe, because their runtime is trusted.

`RECEIVE_COMPLICATION_DATA` itself is a dangerous (runtime) permission. A coded face from another
developer asks for it from its own editor, as it does for every source. The Taqwa face's fixed
slots need nothing, so the plan's safe-watch-face meta-data is optional. If Task 5 keeps it as a
guard, it must name the face's package (`world.taqwa.app.watchfacepush.taqwa`): a Watch Face
Format face has no service of its own to name, since the system sees the runtime's.

### 6. Size on the wire

This is each entry's `asWireComplicationData()`, parcelled (`Parcel.dataSize()`).

| Digits | `RANGED_VALUE` | `LONG_TEXT` |
|---|---|---|
| `format()` | 1.8 KB | 1.5 KB |
| our chains, nine conditions a digit | 9.8 to 10.0 KB | 9.5 to 9.6 KB |
| our chains, bounded by the period | 5.2 to 6.2 KB | 4.9 to 5.9 KB |

The bounded chains give each digit only the values it can take in the period:

- the hours left never exceed the period's length;
- the minutes' tens never exceed 5.

The expression has no way to share a sub-expression, so every comparison carries its own copy of
the time arithmetic. The cost is therefore the number of conditions. Five entries of bounded chains
come to about 28 KB a slot.

## What Task 5 takes from this

**Libraries.** `androidx.wear.watchface:watchface-complications-data-source-ktx:1.3.0` (25 February
2026) and `androidx.wear.protolayout:protolayout-expression:1.4.2` (29 July 2026), the latest stable
of each on 20 October. They add no permission to the merged manifest. They add one more optional
library entry: `uses-library com.google.android.wearable`, `required=false`.

**Classes and calls:**

- **The source:** `SuspendingTimelineComplicationDataSourceService`, whose
  `suspend fun onComplicationRequest(request): ComplicationDataTimeline?` builds
  `ComplicationDataTimeline(default, entries)`.
- **The timeline:** each entry is a `TimelineEntry(TimeInterval(start, end), data)`. The data is
  of the requested type, or `NoDataComplicationData`, and `TimeInterval` requires start < end. A
  timeline that breaks these rules throws inside the library's update path, uncaught, and the
  process dies (Evidence 4). Task 5's tests should hold the rules, since nothing on the watch
  catches them.
- **The ring:** `RangedValueComplicationData.Builder(dynamicValue: DynamicFloat,
  fallbackValue: Float, min, max, contentDescription)`. It needs API 33; the form without a fallback
  needs API 34.
- **Text fields:** any of them can carry a `DynamicComplicationText(dynamicValue: DynamicString,
  fallbackValue: CharSequence)` (API 33), as `setText` or `setTitle`. Watch Face Format reads them
  as `[COMPLICATION.TEXT]` and `[COMPLICATION.TITLE]`, and the ring as
  `[COMPLICATION.RANGED_VALUE_VALUE]`.
- **Screen readers:** the content description is a `ComplicationText` too, so it can carry the
  dynamic countdown. The spike's was static, written at the request, so it went stale. No screen
  reader was run here.
- **Previews:** `getPreviewData` must be static. The library refuses dynamic values in a preview.
- **Updates:** `ComplicationDataSourceUpdateRequester.create(context, component).requestUpdateAll()`.
  On API 36 it goes through the Wear SDK's `ComplicationsManager`: our process binds its own source
  and hands the data over. The source answered within 100 ms of the call.
- **Slot types:** Utility's pill accepts both `RANGED_VALUE` and `LONG_TEXT`, and it asked for
  `RANGED_VALUE`. The face picks the type.
- **Round slots:** a round slot cuts the countdown short ("IN 1 H …"). The spec's `RANGED_VALUE`
  text, "16:10", is the right content there. The countdown belongs in `LONG_TEXT`, and in the
  face's own line.

**The expressions, as run** (`zero` is the feed's `digitZero`, `phrases` its `countdown`):

```kotlin
// The ring: (now - previous) / (next - previous), held to 0..1.
val elapsed = DynamicInstant.withSecondsPrecision(Instant.ofEpochSecond(previous))
    .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
    .toIntSeconds().asFloat().div((next - previous).toFloat())
val ring = DynamicFloat.onCondition(elapsed.gt(1f)).use(1f)
    .elseUse(DynamicFloat.onCondition(elapsed.lt(0f)).use(0f).elseUse(elapsed))

// The countdown in the feed's phrases: whole minutes left, floored (toIntMinutes truncates).
val left = DynamicInstant.platformTimeWithSecondsPrecision()
    .durationUntil(DynamicInstant.withSecondsPrecision(Instant.ofEpochSecond(next)))
val h = left.toIntMinutes().div(60)
val m = left.toIntMinutes().rem(60)
val countdown = DynamicString.onCondition(left.toIntSeconds().lt(60)).use(phrases.underAMinute)
    .elseUse(DynamicString.onCondition(h.eq(0)).use(fill(phrases.minutes, number(m, 59)))
        .elseUse(DynamicString.onCondition(m.eq(0)).use(fill(phrases.hours, number(h, maxHours)))
            .elseUse(fill(phrases.hoursMinutes, number(h, maxHours), number(m, 59)))))

// One digit known to be 0..max, in the feed's digit set: a chain of max conditions.
fun digit(d: DynamicInt32, max: Int): DynamicString {
    var chosen = DynamicString.constant((zero + max).toString())
    for (k in max - 1 downTo 0) {
        chosen = DynamicString.onCondition(d.eq(k)).use((zero + k).toString()).elseUse(chosen)
    }
    return chosen
}

// n, 0..max (max < 100), with no leading zero.
fun number(n: DynamicInt32, max: Int): DynamicString =
    if (max < 10) digit(n, max)
    else DynamicString.onCondition(n.lt(10)).use(digit(n.rem(10), 9))
        .elseUse(digit(n.div(10), max / 10).concat(digit(n.rem(10), 9)))

// fill(phrase, a, b): the phrase cut at %1$s and %2$s, pieces and numbers joined by concat().
```

`maxHours` is the period's length in whole hours.

**Correction (Task 5, 20 October): the countdown above turns over a second early.** The platform's
clock is read once a second but is not rounded to it (the evaluator is handed `Instant.now()`), so at
14:58:00.2 the time left to 16:10:00 is 71 min 59.8 s and `left.toIntMinutes()` is already 71, where
`WatchRules.countdown`, which counts in the whole second, says 72 until 14:58:01. The sheets above were
taken about 20 s into a minute and do not show it. Task 5's `CountdownExpression` reads the time left as
how long ago the end was, negated (`end.durationUntil(now).toIntMinutes().times(-1)`): a negative
`Duration` keeps its whole seconds as the floor, so the result is exactly the end less the whole second
now is in. The ring is not affected (`floor(now - start)` is whole seconds already). The unit tests
evaluate the real expression at 0, 1, 500 and 999 ms into each second.

### Decisions for the Lead

1. **The timeline's length, and its refresh.** I recommend the current period and every period to
   the end of tomorrow (about ten entries, some 60 KB a slot), then the NoData entry. I also
   recommend an `UPDATE_PERIOD_SECONDS` of 21600 rather than the plan's 0, so that the system asks
   again a few times a day, and `requestUpdateAll()` on every feed that is stored. With an update
   period of 0, a watch away from its phone would run off the end of its timeline in a day or two,
   with weeks of feed still stored. The update period's real cadence was not observed here (the
   library says it is not guaranteed). The day of slack covers a missed request.
2. **Watches below API 34.** The constructors with a fallback need API 33. Below 34 the library does
   not promise that dynamic values are evaluated at all. A static timeline, an entry a minute, is
   the candidate for API 30 to 33; it was not run here. The Taqwa face needs Wear OS 6 and is
   unaffected; only the complications offered to other faces on older watches are.
3. **The safe-watch-face meta-data** is optional (Evidence 5). If it is kept, it must name the
   face's package.

## Not covered

- **Samsung's own faces.** Only a Google face and a Watch Face Format face were run. Whether One UI
  Watch's faces draw dynamic text and timeline entries is for the Galaxy Watch 8 Classic (plan 5).
  Until then, "any face, including Samsung's" (spec 2.3) is unproven for Samsung's.
- **A physical watch.** The emulator went no deeper than DOZE.
- **Untried here:** the per-minute fallback, the real cadence of the update period, and timelines
  longer than five periods.
- **The face's install path.** The spike face was sideloaded with adb and signed with the debug
  key, not installed by Watch Face Push.

## The emulator

`scripts/wear-emulator.sh` creates `Taqwa_Wear_OS_6` on its first run and starts it on port 5558,
headless by default or in a window with `--window` (the window was not tried here). It refuses an
emulator of another name on that port, and returns once the watch has booted. It also prints the
API level, 36. It is stopped only with `adb -s emulator-5558 emu kill`.

It was left running for Task 5:

- the active face is Utility, whose slots Task 5 can use;
- the debug app is installed without the spike. Its data was cleared, so it now holds the sample
  feed for 20 October, which it installed on its next start;
- the spike face is uninstalled;
- the watch is off its virtual charger (`adb -s emulator-5558 emu power ac on` puts it back on).

The watch app's status screen ran on the emulator for the first time here. It showed "Cape Town"
and "written 20 Oct 08:00" from the sample feed of the day. So Compose runtime 1.11.0 beside UI
1.10.0 (Task 3, concern 3) runs, and `wear-lifecycle` stays at 2.11.0.
