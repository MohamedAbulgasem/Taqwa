# Taqwa on the Wrist: the watch companion, v1 design

19 October 2026. Branch `watch-companion` off `main` 1dd3208f, in the `site` worktree.

Sources:
- the two design pages Mohamed reviewed, "Taqwa on the Wrist" (https://claude.ai/artifact/A71SqRkciBcTZNkXnpMeRS; round 1 on 18 October, round 2 version 3 on 19 October);
- the reference renderer and mockups copied beside this file in `2026-10-19-watch-companion/` (`faces.js` draws every screen; `mockups.html` is round 2, open it in a browser);
- his answers on 18 October (the face direction, the counter, the dial, the language, the countdown wording);
- his answers on 19 October: "I love love everything … I wouldn't change or throw away any of it", then decisions 1–7.

His decisions, as he made them:

- **Both watches, together:** Galaxy Watch (Wear OS) and Apple Watch, built side by side.
- **v1 scope:** prayer times on the watch face, a tasbeeh counter, and Qibla on watches with a compass.
- **The phone feeds the watch.** The engine stays on the phone.
- **Galaxy:** a Taqwa watch face, plus complications on both watches.
- **All four face styles ship:** Arch, Ring, Sun and Moon. He wants all four on his own watch to test.
  - *24 October:* the Moon's hour and minute hands stay filled always-on, dimmed, as Samsung's own analog faces show them, in place of the drawing's outlines (Ruling 41, 3.4).
- **Colours:** all four (Amber, Pearl, Sage, Night), with amber the default and listed first.
- **Your own complications:** yes. Two slots along the bottom on Arch, Ring and Sun, one at nine on Moon. They start empty.
  - *23 October:* he chose two along the bottom on Moon as well, its lower half re-laid when one is filled (3.4). A slot's place cannot follow the style (3.6), so the slot at nine could not have its own taps or outline.
- **Tasbeeh:** counted as the phone counts. After prayer is 33 · 33 · 34, straight through to 100. Every other phrase counts to 100.
  - It works with the Galaxy's rotating bezel and the Apple Watch's crown.
  - **Turning back takes the last count away.**
  - **His own phrases from the phone come to the watch.**
- **Qibla:** the dial, the app's own.
- **Language:** each watch shows its phone's language, and with it the phone's digits and its 12- or 24-hour setting.
- **Countdown wording:** "in 1 h 12 min" on every surface outside the watch app. In Arabic it is **بعد 1 س 12 د**, the short form as drawn.
- **"Add to Apple Watch":** yes. A button in the iPhone app hands over one of Apple's own faces with Taqwa already in its slots (Apple's face sharing).
- **The Wear OS library** (`play-services-wearable`) is acceptable "as long as it doesn't add any trackers or any unnecessary permissions". It was checked on 18 October: no new permissions, no trackers. The page records the evidence.
- **Test devices:**
  - His Galaxy Watch 8 Classic.
  - His wife's Apple Watch SE 3, through TestFlight on *her* iPhone.
  - **Never the work iPhone 12.**

Standing rules this design obeys:
- **App equals site equals watch.** The watch never computes a prayer time. It shows the phone's, so the never-early promise holds on the wrist.
- No trackers, analytics, accounts, ads, streaks, scores or lifetime tallies.
- The watch app asks for **no location and no internet**.
- Accessibility must not change the visible design at default settings, and every screen gets real screen-reader text.
- Commits carry no `Co-Authored-By`.
- Nothing is pushed without Mohamed's OK.

## Scope

| # | Part | What ships |
|---|------|------------|
| 1 | The watch feed | Phone → watch payload: the coming weeks of prayer starts, the dates, the Qibla, the language bundle and his tasbeeh phrases. Sent from both phone apps whenever the notification plan is rebuilt. |
| 2 | Wear OS app | New `:wearApp` module: the feed receiver, the complication data sources, a Tile, and three screens (Today, Tasbeeh, Qibla). |
| 3 | The Taqwa face | One Watch Face Format face with four styles, four colours, always-on designs and the user's own slots, pushed onto the watch by the Wear app. |
| 4 | watchOS app | A new target inside the iPhone app: the feed receiver, WidgetKit complications in the four accessory families, the Smart Stack widget, and the same three screens. |
| 5 | Phone changes | A "send to watch" piece in each phone app, an "Install on your watch" row on Android, and "Add to Apple Watch" on iPhone. |
| 6 | Strings, tests, devices | New phrases in seven languages through native review, unit tests on the shared rules, emulator and simulator passes, then his two watches. |

**Out of scope for v1:**
- Running the engine on the watch.
- Cellular or phone-away computation beyond the weeks already sent.
- Alerts scheduled by the watch itself. The phone's reminders already reach the watch, and a second set would buzz twice.
- Adhan audio on the watch.
- The Quran on the watch.
- Ramadan suhoor/iftar complications (they come in the Ramadan build).
- Tasbeeh history or sync back to the phone. Counts live on the watch.
- Faces on Wear OS older than 6 (see 3.1).

Ship target: the build after 39 (about build 40, mid-November), both stores, ahead of the Ramadan build.

## 1. The watch feed

### 1.1 One payload, one source of truth

The phone builds one JSON document, **the feed**, and sends it to the watch. The watch draws everything from it:
- the face, the complications, the Tile and the Smart Stack;
- the app's three screens.

The feed is to the watch what the widget mirror (`WidgetMirrorWriter` → `WidgetInputsMirror`) is to the home-screen widgets. The difference is that it holds weeks, not a day and a half, because the watch can be away from the phone.

Fields (v1; names are the builder's to finalise, the content is not):

| Field | What it holds |
|-------|---------------|
| `version` | `1`. A watch that sees a higher major version shows "Update Taqwa on your watch" and keeps the last feed it understood. |
| `writtenAt` | Epoch seconds. |
| `days[]` | **31 days: the phone's yesterday and the 30 days from today.** Yesterday is there so its Isha is still a start after midnight. For each day: its local midnight (epoch seconds, in the *location's* zone, the one the Prayer screen computes today in, `TimeZone.of(location.timeZoneId)`, which may differ from the device's; it sets the day boundary on the watch); the six instants (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha) as epoch seconds, exactly as the phone's engine produced them (polar and high-latitude rules included); the two ends from `DayPrayerTimes.ends` (Fajr's at sunrise, Asr's at sunset; either may be missing on a polar day, 1.4); each one's clock string in the phone's format; the prayer names for that day (Dhuhr reads Jumuʿah on a Friday, as in the app); the Hijri date as the Prayer screen writes it (phone's offset applied), and its day and month alone, with no year, for the face (`hijriDayMonth`, "6 Jumada al-Ula", built from the same parts; the watch's Dates source sends the full form when it is empty); the Gregorian date line in the face's capitals form ("SUN 18 OCT"), and the same date as a screen reader is told it, in words and the phone's own case (`gregorianSpoken`, "Sunday 18 October", plan 1 Task 12; the watch says the face's line when it is empty); the weekday and the day number separately, for Moon's window. |
| `place` | The city name; the Qibla bearing and distance (`QiblaMath.bearing`, `QiblaMath.distanceKm`); the magnetic declination at the place (`WorldMagneticModel.declinationDegrees`); the hemisphere, as a flag only, for Moon's phase. Nothing finer than the city. |
| `ui` | The language tag; RTL; the zero of the digit set the phone draws (`digitZero`: '0' Western, '٠' Arabic-Indic, '০' Bengali, '۰' extended Arabic-Indic; `WidgetDigits.nativeZero` when the app draws native digits); 12 or 24 hours (the app's `ClockFormat`); the AM/PM or ص/م markers; **the string bundle** in the phone's language (1.3); and `showSunrise`, the phone's Show sunrise (the owner's decision of 9 November 2026: the watches list Sunrise only while it is on; true in a feed without it, so `version` stays 1). |
| `tasbeeh` | His own phrases (`TasbeehStore.customPresets`): id, phrase and target. The seven built-ins are in the watch apps themselves. |

**Why weeks, and why 30:** a watch away from the phone keeps working for the weeks it holds. It still has to keep the never-early promise across days the phone hasn't refreshed. Thirty days is about 15 KB in English and 18 KB in Arabic (measured in Task 1); the 31-day samples with the spoken words (Task 12) are about 19 KB and 23 KB, well inside both transports' limits. The phone refreshes it long before it runs out (1.2).

**Days the engine cannot vouch for:** the phone sends whatever its engine returns for each date, with no special casing. Its rules already decide what is safe (the official month, the margins, the polar rules). If the engine cannot produce a day, the feed stops at the day before. The watch shows "Open Taqwa on your phone" when it runs out (2.6).

### 1.2 When the phone sends

From both phone apps, the feed is rebuilt and sent at the end of every `NotificationCoordinator.reschedule(trigger)`. That covers app foreground, background refresh, settings changes, location changes, boot, time-set, time-zone change and alarm-fired. So the watch moves exactly when the phone's own reminders move. It also goes out:
- when his tasbeeh phrases change;
- when the watch asks (2.6), and when a watch may be back (the Android phone's capability change, the iPhone's activation at launch).

The phone only asks: the send runs off the reminders' path, on its own worker, inside one budget (plan 2, Task 4).

**The send is idempotent** (plan 2's call 1, as Ruling 50 numbered it on 25 October). An unchanged feed, compared by its fingerprint (the feed's bytes without `writtenAt`), is not sent again to a watch known to hold it, whatever the ask.
- **Every send is numbered.** Beside the feed, never inside it, goes its delivery: the phone install's `origin` (a random number made once per install, never restored from a backup), a delivery number `seq`, strictly increasing per origin and written down before the link is called, so a number is never used for another feed, and the feed's fingerprint. The phone computes the fingerprint; the watch stores the delivery and echoes it, and never computes one, so the two apps' versions may differ.
- **The watch only moves forward.** It stores an arriving feed only when its origin differs from the stored one's, or its number is greater (`WatchLinkContract.stores`); anything else is stale or a duplicate, dropped unstored and unacknowledged. A greater number for the feed it already holds is stored too, only its redraw skipped. It stores in one order: its delivery removed before the feed is put in place, the arriving one written after. Its acknowledgement (Android) and its handshake (both) echo the delivery it holds, or nothing.
- **The phone answers `UNCHANGED` only once the watch has echoed the latest delivery,** of this very feed: the phone keeps one record (the origin, the last number and the feed it stood for, and the number the watch last echoed, lower or higher). On the iPhone, whose system delivers the latest context it was handed and nothing older after it, a context handed over also counts, for the watch it went to only (a key kept in that watch's own directory). On Android an echo counts for the watch it came from, by its node id, and only while that watch is nearby, so a watch switched to between asks never inherits another's confirmation (Ruling 52 item 9). Outside the known limits below, a late, lost, duplicated or reordered message costs one more send, never a wrong `UNCHANGED`. An echo above the phone's numbers, or of its last number with another feed, lifts its numbers, so a restored phone sends above them; one above half the largest number counts as another install's, so the numbers never overflow. The iPhone's watch-state change forgets the confirmation before it sends, and so does an echo of nothing or of another install's; each forget also sets the next number aside, so no echo the watch sent before it can confirm the latest number again.
- **What the logs say** (Ruling 52): a delivery by its number and fingerprint alone, never its origin, the place or the bearing; only a debug harness may log the origin.
- **The known limits,** each healed by the watch's next acknowledgement or handshake: two installs' origins cannot be ordered, so a straggler of an earlier install stored after the new one's feed holds the watch until then; with two Wear watches connected to the phone at the same time, the one not confirmed can be answered `UNCHANGED` until its own handshake (at its start, when the phone comes back nearby, or daily) heals it, though a phone normally keeps one watch connected (Ruling 53 item 3); on Android an ask judges by the watches it read before building the feed (one reading per ask, Ruling 54 item 4), so a watch switched to during the build can be answered `UNCHANGED` at that ask; its own capability change, served after it, heals it once the old watch has left, and otherwise its handshake does (Ruling 55 item 1); a damped ask cannot know a watch whose echo it has not taken in (a second watch, a wiped one whose handshake was lost, whose echoes from before the wipe may still arrive and confirm, even one that overtakes an echo it sent since, since echoes carry no time: a known limit Ruling 52 accepted); and, outside the documented platforms, a restored record or a lost context before the watch's first echo.

- **Android:** `MessageClient.sendMessage` of the JSON's UTF-8 bytes on `/taqwa/feed/<origin>/<seq>/<fingerprint>`, to each node with the capability `taqwa_watch` that is directly connected (`Node.isNearby`) at the send, never a data item, which would sync to Google's cloud node too. A success is only "queued", so the watch acknowledges each feed it stores on `/taqwa/ack`, and shakes hands on `/taqwa/refresh` when the phone comes back nearby, daily, at its start when it holds nothing from the phone, and when the app opens on a feed that needs one.
  - The phone app gains no new permission.
  - The library adds the manifest entries already checked: `GoogleApiActivity`, not exported, the Play services version tag, and our own listener service.
- **iPhone:** `WCSession.default.updateApplicationContext(["feed": <the JSON's UTF-8 bytes as Data>, "delivery": "<origin>/<seq>/<fingerprint>"])`. The latest context wins, which is the right semantics for a whole-feed document. The session is activated at launch whenever `WCSession.isSupported()`, and `activationState`, `isPaired` and `isWatchAppInstalled` are checked again right before each send. On `payloadTooLarge` the times go again without the phrases, under the next number. The watch shakes hands with `sendMessage(["refresh": <its echo>])` at activation (once what was waiting is stored), when the session becomes reachable and when the app opens on a feed that needs one.

### 1.3 The string bundle

Everything the watch writes *on its screens* (the face, the complications, the Tile, the Smart Stack and the app) is either one of the phone's own strings, or a new phrase translated into all seven languages under the existing in-house review. It all arrives in the feed, so "each watch in its phone's language" holds by construction, even when the watch's own system language differs.

**What the watch system itself shows** cannot come from the feed. The watch reads it from the watch apps' own resources, in the *watch's* language, often before any feed exists:
- the face's name, and the style, colour and slot names in the face editor;
- the app's name;
- the complication and Tile picker entries;
- the watchOS widget gallery name and description;
- any permission prompt;
- the first-run line "Open Taqwa on your phone".

These ship in the watch apps' own resources **in all seven languages**, as the iPhone widget extension already does with its seven `.lproj` folders. They go through the same native review. *My call.* The Arabic names to put to review:

| English | Arabic (to review) |
|---------|--------------------|
| Arch | المحراب |
| Ring | الحلقة |
| Sun | الشمس |
| Moon | القمر |
| Amber | كهرماني |
| Pearl | لؤلؤي |
| Sage | ميرمية |
| Night | ليلي |

The bundle holds:
- the six prayer names and Jumuʿah;
- the countdown phrases:
  - English: "in %1$s h %2$s min", "in %1$s h", "in %1$s min" and a under-a-minute form (the builder words it in all seven);
  - Arabic: بعد %1$s س %2$s د, بعد %1$s س, بعد %1$s د;
  - the Galaxy Tile's compact forms beside them (`compactHoursMinutes`, `compactHours`, `compactMinutes`, Mohamed's report of 9 November 2026): English "in %1$sh %2$sm" (his form, approved), French "dans %1$sh %2$smin" (CLDR's narrow units), Turkish "%1$ssa %2$sdk sonra", Indonesian "%1$sj %2$sm lagi"; Arabic keeps its spaced forms (Mohamed's call of the same day: Wear OS's own Arabic is spaced too), and Urdu and Bengali their full forms, so the three send none. An empty compact form is not written at all, the one exception to every key on the wire, so that a language that keeps its full forms costs the feed nothing (the Lead's ruling of the same day: plan 2 Task 3's worst case keeps all 100 phrases inside the 60,000-byte budget, which does not move); a feed without them gives the full forms; under a minute has one form. French, Turkish and Indonesian, and Urdu and Bengali kept, wait for their native readers;
- the next-in label ("ASR IN" in Latin capitals, متبقٍّ على %1$s in Arabic, as `prayer_next_in`);
- for tasbeeh: "ROUND %1$s", "of %1$s", "After prayer", the seven built-in names, and the watch hints ("Turn the bezel or tap", "Turn the crown or tap");
- for Qibla: "Facing qibla", "Makkah · %1$s km away", "Hold your wrist level" (new), "Compass needs calibrating" and the interference and best-effort lines; the dial draws the distance and the bearing in the watch's short forms, "%1$s km" and "%1$s° · %2$s km" (new, Ruling 66, 31 October), and a screen reader hears the phone's phrases;
- for the app: the three screen titles, "Open Taqwa on your phone" and "Update Taqwa on your watch";
- for a screen reader (`ui.spoken`, plan 1 Task 12): the phone's spoken countdown, never the screen's abbreviations, which a speech engine reads as letters (Arabic's bare س and د). It is the Prayer screen's sentence `prayer_a11y_countdown` ("%1$s in %2$s, at %3$s") and its under-a-minute form, `duration_hours_minutes`, and the plurals `a11y_hours` and `a11y_minutes` as runs of numbers: the phone asks its plural for every number from 0 to 99 and sends one phrase for each run that shares it (`WatchCount`: English hours are 0 "hours", 1 "hour", up to 99 "hours"), so the watch chooses a phrase by the number alone and never by a language's plural rules. A feed without them gives the screen's words.

Digits inside every string are already the phone's digits.

### 1.4 The rules the watch applies

The watch reads the feed; it never re-derives a time. From the current instant and `days[]` it works out:
- **Next:** the first start after now; Sunrise is not a start.
- **Previous:** the latest start at or before now, the previous day's Isha before Fajr.
- **Progress:** (now − previous) / (next − previous). This is the ring's fill.
- **Countdown:** next − now, in whole minutes, worded from the bundle. Under one minute it uses the bundle's under-a-minute phrase.
- **Current prayer for row highlights:** each watch surface copies the phone surface it stands for. "App equals watch" is meant per surface. *My call; the phone itself already splits the two rules this way.*
  - **The watch app's Today list** uses the Prayer screen's rule (`TimelineBuilder.build`):
    - Fajr stops being current at sunrise, and Asr at sunset, from `DayPrayerTimes.ends`.
    - A polar day without those ends keeps the plain rule.
    - Before Fajr, yesterday's Isha lights no row.
  - **The Tile and the Smart Stack** use the home-screen widgets' rule (`TimelineBuilder.buildForWidgets`): the latest prayer that has begun stays lit until the next.
  - In the Ramadan release the phone's widgets move to the screen rule (decision §10.7). The Tile and the Smart Stack move with them, in the same release.
  - So each day in the feed also carries its **ends**: Fajr's end (sunrise) and Asr's end (sunset, the astronomical instant, which is not Maghrib's start). Either may be missing on a polar day.
  - The arc on the face is not affected by either rule: it always runs from the latest start.
  - `faces.js` draws a mix of the two rules in its rows. Take its rows as drawings of layout, not of this rule.

The next/previous/progress/countdown rules are `TimelineBuilder.buildForWidgets` restated over the feed; the highlight rules are as above. They live once in Kotlin, in `:widgetcore` or a new small `:watchcore`, which the Wear app depends on. The watchOS side ports them to Swift, with a shared JSON test-vector file both suites run (same inputs, same next/previous/progress/countdown). **The builder's call:** whether watchOS links a Kotlin/Native watchOS framework instead of the port. The deciding factor is build complexity, since the logic is about a hundred lines.

## 2. Wear OS: the Taqwa watch app

### 2.1 Module and listing

- **Module:** new `:wearApp`, Kotlin with Compose for Wear OS.
- **Package and signing:** `applicationId world.taqwa.app`, the phone's, with the same upload key. The Data Layer only connects apps with the same package and signature.
- **Distribution:** uploaded to the existing Play listing on its **Wear OS track**.
- **minSdk:** 30 (Wear OS 3), so complications and Tiles reach older Galaxy watches. The face needs Wear OS 6 (3.1).
- **Manifest:**
  - `com.google.android.wearable.standalone = false`, since it needs the phone app.
  - Declares the `taqwa_watch` capability.
  - **No `INTERNET` permission, no location permission.** `VIBRATE` and Play services only.
- **Version codes:** one scheme for phone and watch so Play never sees a clash. `bump-version.sh` writes both, for example watch = phone + 100000. The builder picks; the script enforces it.

### 2.2 Receiving the feed

- A `WearableListenerService` on `/taqwa/feed` writes the feed to the watch's own storage (DataStore or a file). Then it asks every Taqwa complication and the Tile to update.
- If the feed is older than 7 days, or runs out within 3, the watch sends `/taqwa/refresh` with `MessageClient` to the phone, and the phone resends.
- **To check in the spec phase, before the first send:** whether DataItems or messages can travel through Google's cloud when the devices are not nearby. If they can, the phone sends only to nearby nodes (`Node.isNearby`), and the page's "the times only travel phone → watch" stays true.

### 2.3 Complications (any face, including Samsung's)

One `ComplicationDataSourceService` per shape, all fed from the stored feed:

| Type | Shows |
|------|-------|
| `RANGED_VALUE` | The ring: progress from the last start to the next, with title "ASR" and text "16:10". |
| `SHORT_TEXT` | "ASR" / "16:10". |
| `LONG_TEXT` | "ASR in 1 h 12 min", the shortened form drawn on the Samsung face. |
| `MONOCHROMATIC_IMAGE` | The Taqwa arch, for slots that take an icon. |

**The countdown changes every minute without waking the app every minute.** The builder chooses between:
- complication timelines (entries with validity windows, refreshed from the feed);
- the face computing the countdown itself from the ranged value's end instant.

The constraint: the wording and digits are the bundle's, and the update is minute-accurate.

**Settled in plan 1** (Task 4's emulator spike, then Tasks 5 and 8):
- **Timelines of dynamic values.** Each source serves a `ComplicationDataTimeline` that breaks where its answer changes, at the prayer starts and at midnight, and covers at least 10 prayer periods (about two days). Next prayer's entries hold a dynamic ring (`DynamicFloat`) and a dynamic countdown (`DynamicString`), so the face moves every minute with the app's process dead. A period is split where the date turns over inside it, if the answer changes there; the ring and the countdown run on across the split.
- **Digits.** `DynamicInt32.format()` writes Western digits, so the countdown maps its digits itself, from the feed's zero.
- **The end of a timeline.** Every timeline ends in a no-data entry, because a face ignores an entry's validity window and would keep a stale "in under a minute". The day dial is the one exception (3.3). Beside typed entries the default is never no data, because the library throws on that; no data alone is the answer for no feed or a spent one.
- **Refresh.** `UPDATE_PERIOD_SECONDS` is 21600 (six hours), and every stored feed calls `requestUpdateAll()`. The six-hourly request was watched by plan 5's 12-hour observation, at 6 h 01 min 18 s and 6 h 03 min 43 s (Ruling 78). Plans 2 and 3 add a safety net (at app start, from the Tile, and daily).
- **On other faces.** NextPrayer (ranged, short, long), the weighted day, Dates (long, short) and the mark are offered in every picker. The Taqwa face's day dial (3.3) is hidden from all of them.
- **Failure.** A source whose computation throws answers no data, and the face shows its empty form.
- **Wear OS 3** (API 30 to 32, Ruling 72 item 1, plan 5's call 6 (b)). The system evaluates no dynamic value there, so a countdown would keep its entry's first words for the whole period. So below API 33 the long text is the prayer's time ("ASR" / "16:10"), and what a screen reader is told is the prayer and its time ("Asr, 16:10"), as the Tile draws without dynamic values; the ring keeps its entry's start, inside the timeline's budget of 2 to 16 entries. From API 33 nothing changes.

### 2.4 The Tile

As drawn:
- the countdown line ("ASR in 1h 12m", the compact forms of 1.3);
- the day's six rows with the app's pips (current in the accent with its halo, passed dot, upcoming hollow), Sunrise small; where the phone's Show sunrise is off (`ui.showSunrise`, its default), the five prayers, sharing the six rows' room (24.4 units apart, the line and the city where they were);
- the city underneath.

Tapping it opens the app on Today. `ProtoLayout`/Tiles Material.

**Settled in plan 3** (Task 8, then its review rounds under Ruling 65; seen whole in Task 9's pass):
- **The countdown moves without the app (call 6), in the rules' own words.** The renderer gives a Tile 200 dynamic nodes (measured on the emulator, schema 1.500), and plan 1's `CountdownExpression` with its sentence is about 800, so the line is a chain of `WatchRules.countdown`'s words, one link a minute (`TileCountdown`). An entry of the moving part is five minutes at most, and shorter towards each change (2 and 4 minutes before it, then every five), so that no entry but the last can still be shown when the change comes, however late the renderer moves on (`TilePlan.shownUntil`). That last entry draws both sides of the change, each shown only on its own side by the platform's clock (schema 1.3). The countdown moves for the timeline's first 80 minutes, less where the whole would pass 200 KB (far north in the multi-byte languages), and the Tile is asked for again within 45 minutes, never sooner than a minute (the Tiles reference's floor, Ruling 68). As a last resort, a city far longer than any the phone writes is cut, half its letters at a time with an ellipsis, so that no timeline passes 200 KB.
- **The countdown is laid out as wide as its widest word on either renderer** (Mohamed's report of 9 November 2026: his Galaxy Watch8 Classic drew "DHUHR in 6 ..."). Samsung's renderer, as his photo measures it, gives the countdown a box as wide as its static value and draws the moving words into it; Google's makes the box as wide as the layout constraint. So the static value is the start's time held `MEASURE_SLACK` (1.5 units) wider than the widest word (figure spaces, then no-break spaces, after a mark of the feed's direction), the constraint is a no-break space wider, and the words are aligned to the name's side in their own box too (`multilineAlignment` START). What the constraint runs past the widest word is balanced by a spacer as wide before the name, so the name and the widest word are centred, and the chord is fitted to what is seen. Widths are measured as the renderer draws them: at the size drawn, where the paint hints each advance to a whole pixel, and from Android 16 in the system surfaces' font the renderer uses (`PaintMeasure`; the review of 9 November found the countdown's letters 4 per cent wider there than in the default font, which ended the last minute in an ellipsis on the emulator). A line that cannot fit whole even at its floor says the start's time instead, never an ellipsis. The Galaxy pass on his watch proves the box's rule.
- **Past the moving part, and wherever a renderer drops a value, the line says the start's time:** "ASR 16:10", said "Asr, 16:10" (call 6's clock form, Ruling 65). It is every dynamic value's static fallback, the link a chain ends in, and the whole line on a renderer below schema 1.2. It is true however late it is shown; a countdown that has passed is never shown. Only a renderer later than `TilePlan.shownUntil` allows for (a doze forced with the Tile in front, Task 9) can keep the rows from before a start beside that time until it moves on; plan 5's Galaxy pass measures a real watch's lateness against the bound.
- **Drawn in the system font (call 7)**, the rows in the feed's direction whatever the watch's language (the renderer lays a Row out left to right on an Arabic watch too, `TodayTileService.HOST_RTL`), the times' column Today's, and a 12-hour phone's times with their day period. The lit row is the widgets' rule (1.4).
- **The states (call 5):** with no feed, "Open Taqwa on your phone" alone, or "Update Taqwa on your watch" alone once a newer format has been seen, both in the watch's language; a spent feed, its own "Open Taqwa on your phone"; a newer format beside a feed, the rows with the update in the city's place.
- **TalkBack** reads the line as the phone's spoken countdown ("Asr in 1 hour 11 minutes, at 4:10 PM"), then the rows shown (six, or five without Sunrise), each with its state first ("Passed. Fajr, 4:33"), then the city or the update; the hidden side of a change is never read.
- **A tap** brings the app's one task forward, on Today.

### 2.5 The app's three screens

The order is **Today, Tasbeeh, Qibla**. A **swipe** moves between them (a horizontal pager), so the bezel and the crown are free to count on Tasbeeh.

- **Today:**
  - The phone's countdown ring around the screen: "ASR IN", the ticking H:MM:SS countdown and the clock time, with a chevron. This is the only place on the watch with a seconds countdown.
  - Swiping up gives the day's list, as the Tile, Sunrise in it only while the phone's Show sunrise is on.
- **Tasbeeh:** the phone's counter exactly (`TasbeehEngine`, `TasbeehPresets`). The rules move into the shared Compose-free module so the Wear app uses the very same code.
  - **Screen layout:** the dhikr in Arabic (Naskh), the transliteration under a Latin interface only, "ROUND n", the count (Manrope Light), and "of 100". The ring has notches at 33 and 66 that show once the arc passes them. The set's three part dots sit under the count.
  - **Counting:** a tap anywhere counts. **One bezel detent clockwise counts one; anticlockwise takes one away** (stopping at 0 within the round).
  - **Haptics:** a tick on each count, a short buzz at a part's end, a long one at 100. The next round starts at 0 a moment later, as on the phone.
  - **The ring:** it fills **clockwise in every language**, because it follows the bezel.
  - **The phrase list:** the seven built-ins, then his own phrases from the feed.
  - **Storage:** counts and the selection live on the watch, per phrase, and are never sent anywhere.
- **Qibla:** the app's dial (`QiblaDial` behaviour).
  - **On the round Galaxy the dial fills the screen,** like a compass: its rim sits on the screen's edge, inside the physical bezel, and the bearing and distance sit inside the dial's lower half. *Mohamed, 20 October.* When the Qibla is behind the wearer, the needle crosses that text for the moment it points down. The Apple Watch keeps the drawn dial, which already spans its rectangular screen.
  - **The dial:** it turns with the heading. The Kaaba marker sits at the feed's bearing on the rim, and the needle points at it.
  - **Facing it:** by the app's own rule, `QiblaAngles.isAligned(heading, bearing, wasAligned)` (B40): the rim lights amber and the watch buzzes once when the heading comes within 5° of the bearing, and both go out only when it is more than 7° off.
  - **Heading:** the watch's rotation-vector sensor gives the magnetic heading. The feed's declination turns it true (`TrueNorth`), with no location on the watch. The `CompassAccuracyGate` and `HeadingFilter` rules apply, moved from the phone into `:watchcore` with `QiblaAngles` and the dial's states (`WatchQibla`), so both watches judge the compass with the phone's own code.
  - **Bad compass states:** low accuracy dims the dial and drops the marker. A watch with no magnetometer shows the bearing and distance with no needle, as the phone does.

**Settled in plan 3** (Tasks 4 to 7, seen whole in Task 9's pass on an English and an Arabic watch, where every page drew pixel for pixel alike):
- **The pager runs left to right in every language (call 4):** Today, Tasbeeh, Qibla, so a swipe right on Today closes the app as everywhere on Wear OS. Each page is laid out in the phone's direction: Today's ring fills anticlockwise for a right-to-left phone, the tasbeeh ring always clockwise, the dial never mirrored.
- **Every word comes from the feed:** the screens' words (`ui.screens`, the phone's own strings read in its own language), the day's names, the clock strings (a 12-hour phone's times as its Prayer screen writes them, day period included), the countdown and spoken phrases, the city and the distance, with the phone's digits (Today's H:MM:SS in the phone ring's digits, Western on Saudi, Egyptian and Bengali phones). Capitals and transliterations show only under a Latin interface. The watch's own resources hold only the app's name, the picker names and the two lines a watch shows before it has a feed (2.6).
- **What the drawings lack, the screens lack (call 16):** no time, no page dots and no scroll bar on any page; on Tasbeeh the bezel belongs to the counter, never the pager.
- **Text size (call 15):** each line grows with the wearer's font size up to its own cap, never shrinks below the drawing's size (only the dhikr and the notices may), and is turned into sp through the screen's own density. Today's countdown and Tasbeeh's count keep the drawing's size at every font size. Today's list grows no further than 1.12, the largest of Wear OS's own font sizes at which, scrolled to its end, every row's time stays inside the round screen's inset on both clocks; at the two larger sizes it is the 1.12 list.
- **The compass (call 13):** the watch keeps hearing the magnetometer beside a trusted rotation vector while Qibla is in front, so an implausible field says "Magnetic interference" even when the vector is confident (B6). It is never used for the heading.
- **Haptics (call 14):** one haptic for each count, undo or turn, the strongest it crossed (the set, then the part, then the tick), and no tick while a part's or a set's buzz still plays.
- **What TalkBack reads:**
  - Today: the ring's one sentence first, with no time before it, the phone's spoken countdown ("Asr in 1 hour 11 minutes, at 4:10 PM"), which changes once a minute; then each row with its state first ("Passed. Fajr, 4:33"), and the city.
  - Tasbeeh: the counter as its count and its dhikr ("27 of 100. SubhanAllah"), with two actions, "Take one away" and "Choose a dhikr"; the phrase list's rows with their selection ("Selected. After prayer, 100"), and the Reset button.
  - Qibla: the dial and its caption are polite live regions, so a turn is said as it comes ("Turn right about 30 degrees", then "15"), and "Facing qibla" once; a dimmed dial says its title, and in BestEffort and NoSensor the bearing and the distance after it.
  - Each page's title is said as the page comes.
- **From one screen to the next with TalkBack:** a one-finger walk stays on its page (on Today it runs from the ring through the rows and the city, then starts again). The next page is a two-finger swipe, as in every Wear OS pager, and the phrase list is the counter's "Choose a dhikr", from which a chosen row returns to the counter. The two-finger swipe could not be drawn on the emulator, so plan 5's Galaxy pass hears it.

### 2.6 States without a usable feed

- **First run, no feed yet:** the screens and the Tile show "Open Taqwa on your phone". Complications show their empty form.
- **The feed's last day has passed:** the same message, and a `/taqwa/refresh` request each time the app opens.
- **A newer feed version than the watch understands:** "Update Taqwa on your watch", and keep drawing from the last feed it understood.

**Settled in plan 3** (call 5, plan 1's order: a newer format outranks the rest; seen in Task 9's pass on an English and an Arabic watch):
- **No feed, nothing newer seen:** every page and the Tile say the watch's own "Open Taqwa on your phone", in the watch's language.
- **No feed, a newer format seen:** every page and the Tile say "Update Taqwa on your watch", in the watch's language, and still do after the app is stopped and opened again. The phone has already sent what this build cannot read, so "Open Taqwa on your phone" would send the wearer to the wrong device. This is the freshly installed old build whose first feed is a version it does not know.
- **A spent feed:** Today and the Tile say the feed's own "Open Taqwa on your phone" (in the phone's language), Today under its title. Tasbeeh and Qibla keep working, since neither reads a time.
- **A newer format beside a feed:** every page draws from the last feed. Today says "Update Taqwa on your watch" in place of the next prayer's clock time (and after its sentence, to a screen reader), the Tile in place of the city. Spent as well, both say the update rather than "Open Taqwa on your phone".

## 3. The Taqwa face (Wear OS, Watch Face Format)

### 3.1 One face, pushed by the app

**Watch Face Push** (Wear OS 6) allows one face per app on the watch. So the four styles are **one face**, switched in the watch's own face editor (Customise). Facts checked on 19 October:
- the face's package must be `world.taqwa.app.watchfacepush.taqwa`;
- the face is resource-only (`android:hasCode="false"`) with `minSdk` 33;
- every build must pass the Watch Face Push validator, which issues the token;
- the face APK must be **signed with a key different from the app's**;
- the Wear app bundles it and installs or updates it with `WatchFacePushManager`.

The Wear app's Today screen gains a row **"Add the Taqwa face"** when the face isn't installed. It can make the face active once `SET_PUSHED_WATCH_FACE_AS_ACTIVE` is granted. On watches below Wear OS 6 the row doesn't appear; the complications and the Tile still work there.

**Keys:**
- **For development**, Mohamed approved on 19 October that the builder creates a **throwaway test key** for the face, the same kind the Android tools generate for every debug build. It is used only for the emulator and for sideloading onto his Galaxy, and is kept out of the repository.
- **The release key** for the face is a new key, kept with his release keystore. He creates it; agents never handle release keys.

### 3.2 Configuration (the face editor)

| Setting | Values |
|---------|--------|
| Style | Arch (default), Ring, Sun, Moon |
| Colour | Amber (default, first), Pearl (the one-colour option), Sage, Night |
| Your complications | Two slots along the bottom on every style, empty by default. On Moon the lower half is re-laid when one is filled (3.4). |

The styles are a `ListConfiguration`; the colours are a `ColorConfiguration`.

**My call:** Arch is the default style. He chose all four without naming a default. Arch was the recommendation on the page he approved.

### 3.3 Where the data comes from

The face holds no code and computes no time. It reads Taqwa's own complication data sources through **fixed slots**: `isCustomizable="FALSE"`, with `DefaultProviderPolicy primaryProvider` set to the Taqwa source and type. Without the Taqwa app a fixed slot falls back to nothing (`EMPTY`), never to another source's data in its place.

As built in plan 1, the face uses all eight slots WFF allows:

| Slot | Source and type | Data | Used by |
|------|-----------------|------|---------|
| 0 (A) | `NextPrayerSource`, RANGED_VALUE | The period in minutes from the date's Dhuhr: min is the start, max the end, and the value is now, dynamic. The prayer is in the title and its clock time in the text. | All four styles: Ring's arc and Arch's light from its share; Sun's lit arc, marks and sun; Moon's chapter arc and curved prayer line |
| 1 (B) | `DaySource`, WEIGHTED_ELEMENTS | The day in seven stretches from where the date begins: midnight → Fajr → Sunrise → Dhuhr → Asr → Maghrib → Isha → midnight, with the daylight and twilight colours. Drawn with `WeightedStroke` on an `Arc` (WFF 2+). | Sun |
| 2 (C) | `DatesSource`, LONG_TEXT | The Hijri day and month in the title (`hijriDayMonth`, no year), the Gregorian line in the text | All four (Moon: the curve under the moon) |
| 3 | `DatesSource`, SHORT_TEXT | The weekday in the title, the day number in the text, and the moon's lit part as the monochromatic image | Moon's date and moon windows |
| 6 | `NextPrayerSource`, LONG_TEXT | The countdown in the text ("in 1 h 12 min"). A ranged value's text is its clock time, so the countdown needs a slot of its own. | All four |
| 7 | `DayDialSource`, RANGED_VALUE only, **hidden from every picker** | The dial's geometry: the date's midnight, Fajr, Sunrise, Asr, Maghrib and Isha in whole minutes from Dhuhr, two to a number (`ComplicationFields.packDial`: `DIAL_BASE` 4096, `DIAL_OFFSET` 1440, the order held). The title and text carry the face's settings (below). | The scene draws the Sun's dial from it (no room of its own since Task 12); every style reads the settings |
| 4–5 (D–E) | The wearer's own | Empty until chosen. Each asks for a short text first, then a ranged value, a goal or an image | Every style, along the bottom (the Moon's curved, option (d)) |

- **Slot 7's source is the Taqwa face's alone.** Its packed numbers mean nothing as another face's ring, so `DayDialSource` declares the library's `METADATA_KEY_HIDDEN`. A fixed slot that names it as its default still gets it. The other way, `SAFE_WATCH_FACE_SUPPORTED_TYPES`, needs `com.google.wear.permission.GET_IS_FOR_SAFE_WATCH_FACE`, which is signature|privileged, so no Play app can hold it.
- **Why a dial slot:** a face can read neither one weight of a weighted complication (its weights are one string) nor a text as a number. So the Sun's marks travel as numbers in a ranged value. The Sun's dial is drawn by the scene, where slot 7 is declared, and reads slots 7, 0 and 1 across slots (`[COMPLICATION.7.RANGED_VALUE_MIN]`, `[COMPLICATION.0.RANGED_VALUE_MIN]` and their siblings). Until Task 12 it was drawn inside slot 7.
- **The dial's end:** where the dial has nothing to say, slot 7 answers a range of 0 to 1 that still carries the settings, and the Sun draws its empty frame. With no feed at all it says no data. It is the one timeline that does not end in no data (2.3).
- **The order and the boxes:** a tap goes to the last slot, in the file's order, whose box holds it, so the wearer's slots are declared last. A screen reader is given the slots in the same order, so slot 0, the prayer, comes before slot 2, the dates (Ruling 42, 3.6): the slots are declared 7, 0, 1, 2, 3, 6, 4, 5. A box is also what a screen reader is given, one item for each, so the boxes follow 3.6's rules (Task 12):
  - **Slots 0 and 2 span the face**, because a complication draws only inside its own box and they draw across it: slot 0 is the face's square less its last row (450 × 449), and slot 2 less its last column (449 × 450). No two boxes may be the same inside the face, and none may be the face's square, which the Moon's clock has.
  - **Slots 1, 3, 6 and 7 have no room:** a 0 by 0 box each, at x = the slot's id, so a screen reader is not given them. The scene draws what they hold from their data, read across slots, each where its slot is declared: the Sun's dial (slots 7 and 1), the Moon's two windows (slot 3, masked by its old box with `renderMode`) and the countdown (slot 6). Giving one of them a box again brings back the repeats of 3.6: two boxes the same make TalkBack say its item again every second.
  - **The wearer's boxes are their own places alone:** slot 4 is (110, 368, 124 × 68) and slot 5 is (234, 368, 106 × 68).

**The clock and the face's settings:** the time comes from the face's own clock (`[HOUR_*]`, `[MINUTE]`). It follows the phone's 12/24 setting, its digits, its day-period markers and its reading direction. These are **carried in slot 7's title and text** (`ComplicationFields.faceSettings`):
- The title is the direction (`L` or `R`), then the clock (`1` for 12 hours, `2` for 24), then the numerals (`A` for the Arabic-Indic sets drawn in Noto, `L` otherwise), then the AM marker.
- The text is the phone's ten digits from its zero, then the PM marker.
- The face takes them apart with `subText()`.
- They hold for as long as a feed is stored, even after its prayers have run out. With no feed, the face writes its clock as the watch does (`[IS_24_HOUR_MODE]`, `[AMPM_STRING]`).

Slot A's own numbers were the first plan for this, but Sun's minutes from Dhuhr took them.

### 3.4 The four styles

The drawings in `faces.js` are the reference for every proportion, size and colour. The face reproduces them in the 450-unit WFF canvas (the drawings use 200 units: multiply by 2.25).

All four share:
- **Palette:** OLED black, ink `#F1F3F1`, mist `#8C948F`, slate `#5A625D`, hairline `#232825`, and the accent.
- **Type:** Manrope (bundled, OFL) for Latin and digits; Noto Sans Arabic for Arabic and Arabic-Indic digits.
- **The time:**
  - The hour in proportional figures, the minutes tabular.
  - 12-hour times are smaller, so that "12:58 PM" clears the frame. The day period is top-aligned with the figures, after the time in reading order: to the right in Latin, to the left in Arabic.
- **The centre stack** (Arch, Ring, Sun): five lines in four voices: Hijri (mist), Gregorian (ink, tracked capitals in Latin), the time (Manrope Light), "ASR 16:10" (accent label + ink time), and "in 1 h 12 min" (mist).

| Style | What it is |
|-------|------------|
| **Arch** | The app icon's arch (`ic_launcher_monochrome.xml`'s path) as the frame, its feet running into the screen's edge. The outline is always drawn in slate. The accent lights it **from the apex down both sides at once** as the period passes, so it needs no mirroring. The icon's dot is a lamp under the crown, with a faint glow of the accent inside the niche (awake only). The time is 56 units (47 in 12-hour). **As built:** WFF has no free path, and a lit image clipped by a falling rectangle draws a flat tent at 12:40 and slanted ends. So the lit arch is four circular arcs a side (two biarcs, within 0.12 units of the drawing's crown) and a foot line, each sweeping its own share of slot 0's progress, written by `tools/face-arch.py`. Its Hijri line has its own 240 px box and three sizes by the title's length, 27, 24 and 22.5 px, so every form keeps 7 px from the lit stroke (`tools/watchface/hijri-fit.py`). A line can therefore drop a step between day 9 and day 10. Its right-to-left Gregorian line takes one of three sizes by its length and its month, the first that applies (Ruling 51a, `ARCH_GREGORIAN_STEPS` in `tools/face-centre.py`): 19 characters or more, which are exactly the Levantine and Iraqi two-word months (تشرين الأول, كانون الثاني …), 11.25 units; else August as ar-SA, ar-EG, ar-LY and ar-AE write it (أغسطس, read on the face by `subText()` of slot 2's text), 13 units; else 14 units, the base box, size and baseline, so the sample is drawn as before. Every form the phones write keeps 7 px from the lit stroke (`tools/watchface/date-fit.py`, nearest 7.9 px). So a phone's date changes size twice a year: on those four into August and out of it, on a Levantine or Iraqi phone at its two-word months, and never on a Maghreb or Urdu phone. Latin, and Ring and Sun, keep one size. |
| **Ring** | The app's countdown ring: a 0.8-unit slate hairline track under a 3.4-unit accent arc from twelve, with a small point of light where it has reached. It fills anticlockwise in Arabic, as `RingArc` does. The time is 60 units (55 in 12-hour). The Taqwa arch mark sits above the dates. |
| **Sun** | A 24-hour dial, Dhuhr at the top and midnight at the bottom. The daylight runs gold, and the twilights (Fajr to sunrise, Maghrib to Isha) are half-strength gold. The five prayers are marks; the previous and next marks and the lit period are in the accent. There is a sun disc by day, with a faint sky glow, and a crescent by night. **It turns clockwise in every language**, being a clock. The sun's colours stay gold whatever the accent. **As built:** it is drawn by the scene from slot 7's dial geometry (inside slot 7 until Task 12), with slot 0's minutes from Dhuhr placing its lit arc, its lit marks and the sun at a quarter of a degree a minute (`tools/face-sun.py`, which reads the two dial constants out of `ComplicationFields.kt`). |
| **Moon** | Analog: faceted dauphine hands with a fine black keyline, and a neutral seconds hand. Always-on the hour and minute hands keep that form, dimmed (Ruling 41, below). The seconds hand sweeps at 15 Hz while the watch is awake, and Mohamed judges its battery cost in plan 5. The Taqwa arch sits at twelve and the sunray dial finish shows awake only. The accent arc on the chapter ring runs from the hour hand to the next prayer, with a pointer; what has passed since the last prayer stays faint, and the arc is capped at one turn. The prayer and countdown curve over the top, beyond the hour hand's reach, as one curve of runs that the runtime centres whatever the name. A date window at three keeps the day number at its outer end. Its weekday keeps the drawing's size while it fits, and is scaled down when it would come within 3 px of the day. A moon window at six shows the moon **as seen from the user's hemisphere** (the feed's flag), with the Hijri date curved under it; always-on its near-black fill is black, its hairline kept (Ruling 46). The moon is the lit part as slot 3's monochromatic image, not `[MOON_PHASE_POSITION]`. **The wearer's slots:** the drawing has one user slot at nine. **Plan 1 built that form (option (a)):** slot 4's data is drawn at nine by the scene, while slot 4's box, its taps and its editor outline stay at the bottom, because a slot cannot move by style (3.6). Once a slot is filled, a tap at nine reaches nothing, and the bottom places take taps unseen. **Mohamed then chose option (d) on 23 October, in place of (a):** two slots along the bottom, as on the other styles. When one is filled, the moon window rises 11 px and the Hijri curve tightens from a 160 to a 132 px radius, under the moon. Both slots curve between the five and seven o'clock indices, inside their own boxes, and nine o'clock is a plain index again. With both slots empty, the Moon is the drawing line for line. Plan 1's Task 11 built (d): the slots' type switches the two lower halves, each slot draws its source's icon and its value along the curve, a long value is scaled to its room as on the other styles, and the editor's outlines and the taps are each slot's own box (`moon-slot-choice.html`, beside `faces.js`, is the drawing). It never mirrors. |

**Always-on, for every style:** `Variant mode="AMBIENT"` on Parts, Groups and the Moon's hands, changing their alpha. WFF ignores a Variant inside a Font or a Stroke, so a thinner always-on stroke is a second PartDraw. Thin strokes, no seconds, the countdown kept in mist, and the Arch outline kept. **The Moon's hands** (Ruling 41, Mohamed's call of 24 October, as Samsung's own analog faces draw them) keep their awake form always-on, the faceted dauphine with its black keyline, dimmed by their own `AMBIENT` alpha Variant to 82% (`A_HANDS`, 209 of 255 in `tools/face-moon.py`). That is the level Arch, Ring and Sun draw their time at always-on, so the lit facet is the face's brightest, as their time is, and the shaded facet falls to about mist, the level of the face's texts. 50% and 70% were tried first: at 50% the hands were no brighter than the Moon's own texts. A dimmed picture lets what lies under it show through, so each hand lies on a black back of its own shape, the other hand of its kind in the `AnalogClock`, alpha 0 awake (it takes at most two of each, and draws them in the order declared). A dimmed hand therefore stays opaque, and its keyline stays black. The cap's ink is dimmed the same way over its black disc. Task 9's outline hands are gone, and the hands move every minute, so burn-in is not a concern. **The lit share** is the share of the round screen's pixels that are lit, a pixel being lit when any channel of the face's colour is over 8, and Google's limit is 15%. The emulator dithers its always-on frame as a strict checkerboard (every pixel it draws on one parity) and draws it at 0.85 of the face's colours, so its frame is measured as the face draws it: twice its lit pixels, a pixel lit at 8 or more there (`tools/watchface/lit.py undither`, since Task 13's fix round 2). On the drawings, dimmed and dithered the same way, that gives their own share within 0.02 point; Task 13's first figures filled each dark pixel from its neighbours instead, which read 0.9–1.3 points high on edges and lost the moon window's near-black fill. At the pass's moments every style is under the limit: Arch, Ring and Sun 10.3–13.5% on the emulator, and the Moon with its filled hands and its black moon window (Ruling 46, below) 9.9–12.6% there and 9.5–12.2% in the drawing. Those moments are not the worst (plan 1 Task 13's fix round 1, `task-13/litworst.py`). The Moon lights the most just after Fajr and just after Isha, when its chapter arc is about eight hours long. **There, with both slots filled, the Moon was over 15% by both measures until Ruling 46, and ordinary values were enough** (Task 13's review round 3): a full battery and the emulator's own step count gave 15.41% at 04:40 (890 steps) and 15.45% at 20:55 (960) in Libyan Arabic with 28 Jumada al-Ula, and 15.42% in English with 1,030 steps and Jumada al-Akhirah; the drawing, with a full battery and 12,345 steps, reached 15.0–15.2%. Of the Moon's share, 2.0–2.3 points were the moon window's fill, #070908, which had no always-on Variant and is lit by the measure (its 9 is over 8). **So the moon window is black always-on** (Ruling 46, the Lead's call of 24 October, built in Task 13's closing fix): its fill is a PartDraw of its own with an `AMBIENT` alpha 0 Variant, and its hairline a second PartDraw of the same rectangle over it, kept, as the moon is. Nothing is drawn under the window always-on, so it is black there. The fill was 6 to 8 of 255 on the emulator, too dark to see always-on, so the look is unchanged (`task-13/r46-before-after.png`), and awake nothing changes. **The Moon is now under 15% at its worst moments:** on emulator-5558, as the face draws it, 12.9–13.3% with both slots filled (a full battery and the emulator's 630–1,160 steps) and 12.6% with them empty, 2.1–2.3 points under the same cases before; in the drawing 13.0% at most with a full battery and 12,345 steps, and 11.9–12.3% with the slots empty. Counting every pixel that is not black, the stricter reading, its worst frames are 13.7–14.7%. The filled hands add 0.35–0.47 point over the outlines, and their level is not a lever, since the share does not depend on it. The date window's fill, `WELL` #0B0D0C, is lit the same way and keeps its fill, since the moon window alone gives the margin; black too, it would take about 2 points more (`task-13/litworst.py` section 5, `task-13/lever-sheet.png`). Ring and Sun, which Task 13 did not touch, reach the limit or pass it at their own worst moments late in the Asr chapter: Ring 15.6–16.1% on the emulator at 18:50 with its slots filled (15.3% in the drawing), though 14.8% with them empty (14.5%), and Sun 15.0% at 18:00 (14.2%). Arch is under, at 13.5% (13.4%). What gives where Ring or Sun is over is a call of its own. The emulator draws always-on dithered and 15% dimmer, so Mohamed judges the real look on his Galaxy Watch (plan 5).

### 3.5 Languages on the face

- Arabic and Urdu are right-to-left.
- The text all comes from slots 0, 2, 3 and 6, so it arrives already in the phone's language and digits. The clock's digits and markers come from slot 7's settings (3.3).
- **Mirroring:** Ring's direction follows the feed's RTL flag, which slot 7's title carries (3.3); Sun and Moon never mirror; Arch is symmetric.
- **Right to left on this runtime:** a Text's runs are laid out left to right whatever their script, so a right-to-left line of several voices is written in the order it is seen. Arabic is never tracked (`letterSpacing` 0), because tracking breaks the joining.
- **Fonts:** the Arabic-Indic sets (٠ and ۰) are drawn in a subset of Noto Sans Arabic (`tools/watchface/subset-noto.sh`). Bengali letters and digits fall back to the watch's system font. The wearer's own slots always ask for Manrope, so Arabic-Indic values from an Arabic watch fall back the same way (plan 5's device pass).

### 3.6 What the format and the runtime allow (learned in plan 1)

Plan 1 found these on the Wear OS 6 emulator and in the format's schema. Most of them are not in Google's documentation. Any later change to the face starts from them. The long form, with each case, is the header of `watchface/src/main/res/raw/watchface.xml`.

**Slots:**
- **A ComplicationSlot cannot move by style.** In formats 2–5 it is a child of the Scene only, with one bounding shape. It takes no Transform, and it is not allowed inside a ListOption or a Condition. A layout that puts a slot in different places on different styles needs a box that is right on every style, or the scene draws the slot's data somewhere else, while its taps and its editor outline stay at the box.
- **A complication draws only inside its slot's bounding shape.** What a slot draws past its BoundingBox is cut off without a word, even inside the slot's rectangle. So a slot whose data a style draws far from the others' place for it spans the face (slots 0 and 2), or has no room and is drawn by the scene from its data (slots 1, 3, 6 and 7, 3.3).
- **Slot tap order follows declaration order.** A tap goes to the last slot in the file whose box holds it, whether or not that slot has a tap action. So the wearer's slots are declared last, and each box is its own place alone.
- **A fixed slot's system fallback must be one of its `supportedTypes`.** Otherwise the system refuses to add the face, and says so only in the log.
- **An expression can read another slot's data,** as `[COMPLICATION.<slotId>.<field>]`, and its type's name as `[COMPLICATION.<slotId>.TYPE]`. The scene can draw a slot's data where the slot is not.

**Expressions and switching:**
- **A text is never a number** (`"18" * 2` is 0), and a weighted complication's weights are one string. So numbers travel in a ranged value. `floor()` and `%` take two fields out of one number.
- **Texts can be compared and taken apart:** `==` on texts, `subText()` and `textLength()`.
- **A Condition whose expressions read complication data can stick** in the branch it took until the face loads again. A Transform reading the same data follows at once. So every switch that follows the data is a set of Groups shown by alpha Transforms, and a Condition tests the configuration alone.
- **A Font's size is no expression.** A size that follows the data is one PartText per size, switched by alpha.
- **A CLOCKWISE Arc whose end is a float step behind its start draws a full ring.** An arc that shrinks to nothing has one end written as the other, with a `clamp(…, 0, …)` between them.

**Validators:**
- **The validators check elements and attributes, not expressions.** `wff-validator` and Watch Face Push's validator (`tools/watchface/validate.sh` runs both) hold the file to the declared format's schema. Neither parses an expression, so an expression is proven only on the runtime.
- **The runtime never says no.** It ignores what it does not understand, so only the validators catch a schema breach.

**Always-on:**
- **A Variant belongs on a Part, a Group or a hand.** Inside a Font or a Stroke it is outside the schema, and it is ignored.

**Drawing:**
- **A picture is a PNG.** Push's validator refuses vector drawables, so `tools/face-art.py` draws every picture.
- **A part's x, y, width and height are whole numbers.** A decimal y reads as 0.
- **A gradient's colours cannot come from the configuration.**

**Text:**
- **Negative `letterSpacing` on a Text is not drawn, but it shifts the line.** Positive spacing is drawn, and so is a TextCircular's negative spacing.
- **A curved text loses the glyphs past its characters.** The runtime draws a TextCircular in its font sandbox with `drawTextOnPath`, which places each glyph by the advance of the character with the glyph's index. A run shaped into more glyphs than characters has its visually last glyphs placed from past the advances, and they were lost or thrown elsewhere, differently at each bind. Noto Sans Arabic shapes a dotted letter into its body and its dots (up to 8 more glyphs than characters), and the watch's Noto Sans Bengali UI, which a Manrope run falls back to, splits an o-kar or an au-kar into its two signs: the Bengali Dhuhr যোহর is 4 characters and 5 glyphs, and a Bengali watch's day and date, সোম ২০, lost its last figure in a curved slot. The glyphs past the count are a right-to-left run's logically first and a left-to-right run's logically last. So a curved run that can hold one carries twelve combining grapheme joiners (U+034F) at that end: no ink, no advance, and they are the glyphs past the count (Ruling 37). Every Arabic-script run of words has them at its start, and the prayer's name and the wearer's curved values left to right at their end. `moon-fit.py` counts every curved run of the phone's words against them. A right-to-left mark in the same place changed nothing (the runtime seems to drop it), and a straight Text is drawn whole.
- **A TextCircular's circle follows no Transform,** but its PartText's scale scales the circle with the letters. So the Moon's curved values are scaled to their room about their anchors, on a wider circle tangent to the baseline there. A curved text is laid out before the scale and cut where its path ends, so its path runs as far as the unscaled value reaches.
- **A Text takes several Font runs.** The runtime lays them out left to right and centres them as one line, each by its own font's metrics. A run of only spaces is dropped.
- **The runtime keeps a font by its resource name,** through reinstalls and even an uninstall. A changed font needs a new file name.

**The hands:**
- **An `AnalogClock` takes at most two hands of each kind.** Its hour hand steps once a minute.
- **It draws its hands in the order they are declared,** and a Variant on a hand sets the whole picture's alpha. So a hand dimmed always-on is laid on a black back of its own shape, declared before it, or what lies under it shows through (a back declared after its hand hides the hand).

**A screen reader (plan 1, Task 12):**
- **The runtime gives the reader a list:** the clock first, at its clock element's box (the Moon's `AnalogClock` spans the face; Arch, Ring and Sun draw their time from parts, which leaves the middle quarter), in the watch's own words and format. Then every slot with data, at its bounding box, in the order the slots are declared, with its description, or its title and text where the description is empty. Then every `ScreenReader` element of a part shown, whose words are a string resource in the watch's language, not the phone's. The list is made again at every change of any slot's data, which the ring makes every second.
- **The system makes one item for each box and tap action, in the list's order.** Where two boxes are the same, it keeps one item with the last one's words, and then builds all its items again each time the list is made. TalkBack then said the item it was on again every second. A box is cut to the screen before two are compared, so two that differ only past the face's edge are one. So no two boxes on the face are the same inside it, and none is the face's square, which the Moon's clock has: slot 0's is the square less its last row, and slot 2's less its last column.
- **A slot with no room is no item.** A description cannot hide a slot: an empty one is replaced by the title and text, and a single space is read as "Space". So slots 1, 3, 6 and 7 have 0 by 0 boxes, and the scene draws the Sun's dial, the Moon's two windows and the countdown from their data, read across slots, each where its slot is declared. A Group does not clip what it holds, so the Moon's windows are masked by slot 3's old box (`renderMode`).
- **What TalkBack reads, in order:** the time; slot 0, the next prayer, the time left and its time, in the phone's spoken sentence ("Dhuhr in 7 hours 10 minutes, at 12:33", `ui.spoken`, 1.3); slot 2, the Hijri date with its year and the Gregorian in words ("6 Jumada al-Ula 1448, Sunday 18 October", `gregorianSpoken`, 1.1); then the wearer's slots, in their sources' words. On a prayer face the prayer comes first, so slot 0 is declared before slot 2 (Ruling 42). A prayer's name is said as the phone wrote it ("Dhuhr"), not in the face's capitals, and no number is said with the screen's abbreviations. A feed without the spoken words gives the screen's ("Dhuhr, 12:33, in 7 h 10 min", "SUN 18 OCT"), the prayer and its time joined by the phone's comma as the Tile and Today join them (Ruling 74 item 1).
- **A dynamic description is worked out like a dynamic text,** a minute at a time, and the reader is given the new words each minute. So the countdown in a description is never a minute stale.
- **A touch finds no item of the face on the emulator** (heard, `task-12/touch.txt`). With TalkBack on, a held one-finger touch on emulator-5558 reached none of the face's items, at any point tried in Arch and in the Moon, the wearer's own slots included; the face from before Task 12, the face in either order of slots 0 and 2, and Google's own accessible face behaved the same. Every hover went to the system UI's own views laid over the face, and TalkBack found nothing to focus under the finger, while the same touch on TalkBack's own window was spoken. So the face is read by swiping, in the order above, and it keeps its boxes and that order (Ruling 42): no box or order of its own changes what a touch finds here, and the swipe order is what a TalkBack user gets for certain. A slot's box is the same in every style (a `ComplicationSlot` is the Scene's child alone and has one bounding shape).
- **What a touch finds is heard on a watch.** A watch's system UI is its maker's, so plan 5's device pass hears a touch on his Galaxy, with TalkBack and with Samsung's screen reader. If a touch there finds the dates where their box and the prayer's overlap, declaring slot 2 before slot 0 (Ruling 40's option (b), which Ruling 42 withdrew) may be reopened with that evidence.

**The sources:** what the complication library taught is in 2.3 ("Settled in plan 1") and 3.3 (the hidden dial, and the safe-types key that a store app cannot use).

**Still open at the end of plan 1:**
- *Closed by Task 12:* TalkBack read the face as three items (one for every full-face slot, the clock and slot 3), so the dates and the countdown were not spoken. It is the list above now. Plan 5's device pass runs TalkBack and Samsung's screen reader on his Galaxy first, swiping and by touch, as a ship gate (Ruling 38).
- *Closed by Task 11:* the Moon's right-to-left Hijri curve dropped letters now and then. The cause and the fix are under "Text" above; the same fix holds the Moon's right-to-left prayer name and countdown, which the same cause reached (العشاء lost its alef and lam, بعد أقل من دقيقة its first letters).

## 4. watchOS: the Taqwa watch app

### 4.1 Target and distribution

- **Target:** a watchOS app target inside `iosApp.xcodeproj`, SwiftUI, with a WidgetKit extension for complications and the Smart Stack.
- **Distribution:** it ships inside the iPhone build (one upload, one review, the same App Store page). It installs on the watch with the iPhone app.
- **Version:** CFBundleShortVersionString and CFBundleVersion must equal the iPhone app's; `bump-version.sh` sets both.
- **Minimum watchOS:** the builder's call, at least the oldest version that runs on an SE 3 with WidgetKit accessory families.
- **No location or HealthKit authorisation.** Heading only (4.4).

### 4.2 Receiving the feed

- `WCSessionDelegate.session(_:didReceiveApplicationContext:)` writes the feed to the app group shared with the widget extension, with its delivery beside it, only when the delivery moves it forward (1.2). Then it calls `WidgetCenter.shared.reloadAllTimelines()`, unless the feed is the one it held.
- The handshake is `sendMessage(["refresh": <its echo>], replyHandler:)`: the delivery it holds (`origin/seq/fingerprint`), or `""` (plan 2's call 1, Ruling 50). It goes at activation once what was waiting is stored, when the session becomes reachable, and on open when the feed needs one (2.2's thresholds). That wakes the iPhone app in the background. The phone takes the echo in, updates the context when the watch does not hold the current feed, and replies `["queued": Bool]`: true when the watch has, or is being given, the current feed. The reply comes asynchronously, so WatchConnectivity's queue never waits for the phone's sync (Ruling 52); the phone queues each handshake in the order it arrived and holds background time until it replies, so a phone app the handshake woke answers before it is suspended (Ruling 53).

### 4.3 Complications and Smart Stack

Every family is drawn as on the page:
- `accessoryCircular`: a gauge ring with "ASR" and "16:10";
- `accessoryCorner`: a gauge holding the Taqwa arch, with "DHUHR 12:33" along the corner;
- `accessoryRectangular`: "ASR 16:10", "in 1 h 12 min" and **the period bar**, the ring's model laid flat;
- `accessoryInline`: "ASR in 1 h 12 min".

The **Smart Stack** widget is the rectangular content in a card with the Taqwa header. Its relevance rises before each prayer.

**One progress model** throughout: the share of the time between two prayers. Only the Galaxy's Sun style draws the whole day.

**Timelines:**
- one entry per minute up to the next start, plus entries for the starts after it, from the feed;
- reload after each start, so the WidgetKit reload budget (about 40–70 a day) is never approached, as the iPhone widget already learned.

**Tinted faces:** shapes must read in one colour (`widgetAccentable` on the accent groups).

### 4.4 The app's screens

The same three as Wear (2.5), the same order, moved between with a horizontal swipe (`TabView` page style), so the crown is free on Tasbeeh.

- **Today:** the ring above the day's list, Sunrise in it only while the phone's Show sunrise is on (`ui.showSunrise`).
- **Tasbeeh:** `digitalCrownRotation` with detents and haptics. Up counts one, down takes one away; a tap counts. The rules match the Swift port and the shared test vectors.
- **Qibla:** the magnetic heading (`CLLocationManager` heading updates), corrected with the feed's declination. The same dial, alignment, buzz and accuracy states. **To verify:** that heading updates need no location authorisation on watchOS. If they do, the screen asks only when opened and says why, and the builder brings it back to Mohamed before shipping.

### 4.5 "Add to Apple Watch" (face sharing)

The iPhone app's Settings gains a row, shown when a watch is paired. It offers one of Apple's faces with Taqwa's complications already in place, through `CLKWatchFaceLibrary.addWatchFace(at:)`, using a `.watchface` file bundled in the app.

- **Making the file:** the builder configures the face on a real Apple Watch with the watch app installed (a Modular-style digital face with Taqwa's rectangular and circular, amber), then exports it.
- **To verify:** that face sharing still works with WidgetKit complications on current watchOS. If it doesn't, the row is dropped, and that is reported to Mohamed.

## 5. Phone changes

- **Android:**
  - the feed sender (1.2);
  - an **"Install on your watch"** row in Settings when a Wear node without `taqwa_watch` is connected (`RemoteActivityHelper` → the watch's Play Store).
  - **Home's watch card** (1.7.0; Option B "Face preview", the owner's decisions of 19 Nov 2026): the same offer as Settings › Watch, "Install on your watch" or "Add the Taqwa face", as a card above the Qibla card in the one-time card's slot, for as long as Settings shows that row. A 52 dp drawing of the user's round watch stands where the Qibla card has its dial (a dashed ghost before Taqwa is installed, the real watch with the Taqwa face faint before the face is added, a thin amber ring while the watch answers a tap); beside it, what this is over the one action, in an amber that reads at 4.5:1, and an x. It reads the watch as Settings does (`readWatchRows`: the 3 s bound, the install held 20 s, the tap follow-up), and it is put in only if that reading comes before the first touch or scroll since Home came to the front, so the Qibla card never moves under a thumb; once Home is touched with no card on it, nothing more is read that visit. A card already there changes in place and folds when no row applies, with no "set up" moment; a card kept from Home's last visit is kept through its resume's reading as through a reading in place (a failure keeps it, the install is held). A tapped install card is held through its tap's whole 3-minute follow-up, saying "Continue on your watch", since a reinstalled watch publishes its face only once its app is started; the face then takes its place in place (unless the watch refused the tap). Its tap joins Settings' requests, and a step that changes in place after a tap is said to a screen reader. The x writes one flag (every watch, both steps, for good) and leaves "Set up your watch any time in Settings." with Undo in its place for 6 s, or for the reader's own "Time to take action" where longer, folding only once no finger is down on Home; under a screen reader the focus moves to Undo, and back to the card after Undo. Settings › Watch never reads the flag. Home reads no watch without a place or in a launch that showed a one-time card. One card per launch: a once-only card goes first. Android only; the iPhone shows nothing until its card is designed.
- **iPhone:**
  - the feed sender (1.2);
  - the "Add to Apple Watch" row (4.5);
  - WatchConnectivity activation (1.2).
- **No new phone permissions on either platform.** The Exodus listing must keep showing no trackers. The Android manifest's two additions are already recorded on the page.
- **Docs:** the README's feature list, the site's Get the app and support pages (a "Watch" answer), and the privacy policy's "data that leaves your phone" section. The feed goes only to the user's own watch; the policy says so.

## 6. Testing

- **Shared rules:**
  - unit tests in the Kotlin module, and the Swift port against the same JSON vectors: next, previous, progress, countdown, the highlight rule, Friday's Jumuʿah, the day boundary, a 12-hour feed, and Arabic-Indic digits;
  - the tasbeeh rules, including undo at a part boundary and at 0.
- **Feed:** for golden feeds (Cape Town, a Friday, Tromsø in May, a 12-hour Arabic phone), the phone writer's output is byte-stable.
- **Wear:**
  - a Wear OS 6 emulator image, round, at 450 dp, on its own port, never stopping anyone else's;
  - install the app, push the face, then check each style and colour, awake and always-on, in English and Arabic;
  - the Tile, the three screens, bezel input (the emulator's rotary), and the complications on a system face.
- **watchOS:**
  - a watch simulator paired with the iPhone simulator, with the feed delivered;
  - each complication family, the Smart Stack, and the three screens.
- **Devices:**
  - **His Galaxy Watch 8 Classic:** sideload with wireless debugging, which he enables and pairs. The face, all four styles, the bezel, Qibla.
  - **His wife's Apple Watch:** through TestFlight on her iPhone, never the work iPhone 12.
  - **What he checks on the devices:** the face at a glance across a day, always-on brightness, the tasbeeh feel, and Qibla in the house.

## 7. Calls made by the Lead

These are not Mohamed's decisions; check them while reading:
1. **The default style is Arch.** He chose all four styles without naming a default (3.2).
2. **One face holding four styles,** installed by the Taqwa watch app (Watch Face Push allows one face per app). Watches below Wear OS 6 get the app, the Tile and the complications, but not the face (3.1).
3. **Thirty days of times in each feed.** The watch asks the phone for more when it is three days from running out or the feed is a week old (1.1, 2.2).
4. **Each watch surface follows the highlight rule of the phone surface it stands for:**
   - the app's Today list follows the Prayer screen;
   - the Tile and the Smart Stack follow the home-screen widgets;
   - the Tile and the Smart Stack move with the widgets in the Ramadan release (1.4).
5. **The watch system's own labels are translated into all seven languages:** the face editor names, the picker entries and the gallery text. The Arabic style and colour names go to review (1.3).
6. **A swipe moves between the app's screens on both watches,** so the bezel and the crown count on Tasbeeh (2.5, 4.4).
7. **Direction:**
   - the watch's tasbeeh ring fills clockwise in every language, because it follows the bezel;
   - Sun and Moon never mirror;
   - Ring mirrors as the phone's ring does (2.5, 3.4).
8. **The watch schedules no alerts of its own.** The phone's reminders already reach it (Scope).
9. **The watch apps ask for no internet and no location.** Qibla's true north uses the declination the phone sends (2.1, 2.5, 4.4).
10. **The face follows the phone's 12- or 24-hour setting,** not the watch's (3.3).
11. **One version-code scheme for phone and watch,** enforced by `bump-version.sh` (2.1, 4.1).
12. **The Moon's moon window is black always-on** (Ruling 46, 24 October): its near-black fill, #070908, goes to black, and its hairline and the moon stay. The fill is 7 to 9 of 255, too dark to see always-on, and it was 2 points of the Moon's always-on share, which reached 15.5% at its worst moments. The date window keeps its fill (3.4).

## 8. Left to the builder

- Module boundaries: `:watchcore` versus growing `:widgetcore`, and where `TasbeehEngine` moves.
- The countdown's minute updates on Wear (2.3).
- Arch's construction in WFF (3.4).
- File storage on the watches, and the JSON field names.
- Whether watchOS links a Kotlin/Native framework or ports the rules.
- The version-code scheme.
- The minimum watchOS version.
- Exact emulator images.

These follow the drawings and the decisions above. Anything that would change what Mohamed sees goes back to him first.
