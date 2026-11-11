# Watch companion 2: the feed from the phones, implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** both phone apps build the feed from their own engine and send it to the user's own watch alone. The Galaxy watch stores it, acknowledges it, shakes hands with the phone when they reconnect and once a day, and asks for a new one when it runs low. After this plan a paired Wear emulator shows the phone's own times, language, digits and clock on the Taqwa face, with no sample feed involved. The iPhone side is built up to the point where plan 4's watchOS app only has to decode the feed.

**Architecture:**
- **`:shared` (commonMain), new package `world.taqwa.app.watchfeed`,** `internal` throughout except the bridge entry points:
  - `WatchFeedBuilder` turns the engine's days, the app's settings and the app's own interface language into a `WatchFeed`. It is pure and tested on both platforms.
  - `WatchFeedSync` decides when to send, and the reminders never wait for it. A reschedule, a phrase change or a watch appearing only *asks* it, and the ask returns at once. It serves the asks one at a time, in the order they were made, on its own process scope and on `Dispatchers.Default`, each inside one 5-second budget. It asks the link whether a watch is there before it builds anything. It never sends again a feed the watch is known to hold, whatever the reason for the ask, a watch coming back included. Every send carries, beside the feed, the install's random origin and a delivery number written down before the link is called (Ruling 50); the watch stores a feed only when it moves it forward and echoes what it holds, so the phone answers `UNCHANGED` only once the watch has echoed the latest delivery (on the iPhone, also once the latest context was handed over to that watch). Only the iPhone's watch-state change (an app installed or removed, another watch paired) forgets before it asks; a watch that holds nothing says so in its own handshake. It names each feed by a fingerprint it computes itself and sends beside the feed, so the watch never has to compute one.
- **The transports:** `WatchLink` is an `expect` with two actuals:
  - Android uses the Wear Data Layer, through `MessageClient`, to nodes that are directly connected when the send is made.
  - iPhone uses `WCSession.updateApplicationContext`, with the feed as UTF-8 `Data`, driven from Swift through a hook, the way `WidgetRefresher.ios.kt` drives WidgetKit.
- **`:wearApp`:**
  - a `WearableListenerService` that writes into plan 1's `FeedStore` only what moves it forward (`WatchLinkContract.stores`), keeps the delivery the phone sent (origin, number, fingerprint) beside the feed, and acknowledges each feed it stored with that delivery;
  - the handshake: the stored feed's delivery, as the phone sent it, echoed to the phone when the phone comes back nearby, once a day, and when the app opens on a feed that runs low;
  - a daily nudge (Ruling 22's safety net), which carries the daily handshake.
- **`:watchcore`:** the link's contract, shared by phone and watch: paths, capability names, context keys, the fingerprint's and the origin's form, the delivery each feed travels with and the receivers' rules (Ruling 50), each link's size limit and the feed's byte budget. The fingerprint itself is computed in `:shared` alone (Task 4), because a watch on another version than the phone decodes and re-encodes a different text.
- **`:watchface`, one change:** Urdu's ۴ ۶ ۷ in Urdu forms in all three Noto weights, under new font names (Task 2), which plan 1's close gave this plan.

**Tech stack:**
- Kotlin 2.4.10 and AGP 9.4.0; Compose Multiplatform 1.12.1 resources (`getString`, for the interface language off the composition);
- `com.google.android.gms:play-services-wearable` (20.0.1, the newest stable on 18 and 23 October), on the phone and the watch;
- WatchConnectivity (Swift, in `iosApp`);
- kotlinx-serialization-json 1.11.0, through `WatchFeedJson`;
- the Wear OS 6 emulator `Taqwa_Wear_OS_6`, and a new Google Play phone emulator `Taqwa_Phone_Wear` (`system-images;android-36;google_apis_playstore;arm64-v8a`, already installed).

**Spec:** `docs/superpowers/specs/2026-10-19-taqwa-watch-companion-design.md`, sections 1.1, 1.2, **1.3 in part**, 1.4, 2.1, 2.2, 2.6, 3.3, 3.5, 4.2, 5 and 6. Of 1.3's string bundle, this plan sends the words the face and the complications draw and say: the prayer names, Jumuʿah, the countdown, the next-in label, the day markers, and the screen reader's words that plan 1's Task 12 added to the model (`ui.spoken`, `days[].gregorianSpoken`). The bundle's tasbeeh, Qibla and app words are not built here: plans 3 and 4 add them to the feed with the screens that write them (the Lead's call 6). Binding rulings are in `.superpowers/sdd/2026-10-19-watch-companion-1-galaxy-face/progress.md`: 6, 7, 8, 9, 13, 22, 24, 27, 36–38 and 42, which replaced 40 (plan 1's last tasks, below), and **39** (Mohamed, 23 October: what the feed may carry and what the promises say; the section after this one).
- **Ruling 38** (TalkBack is a ship gate) reaches this plan through the spoken words: the face says the phone's sentence only when the phone sends `ui.spoken` and `gregorianSpoken`, so Task 3 builds them and Task 7 check 4 hears them on the paired emulators.
- **Ruling 42** (the face's reading order, which replaced Ruling 40: the time, the prayer, then the dates) is plan 1's: no task here changes a slot, a box or the order they are declared in. The words this plan sends are what those items say.

**The series, and when this plan starts:** this is plan 2 of 5. Plan 3 is the Galaxy watch app, plan 4 the Apple Watch, and plan 5 the ship.
- Plan 1's **Task 11** (the Moon's option (d), Ruling 36, with its right-to-left Hijri fix, Ruling 37) is closed. Its **Task 12** (TalkBack on the face, Ruling 38, closed under Ruling 42) and **Task 13** (the Moon's filled always-on hands, Ruling 41) touch `watchface/`, `tools/face-*.py` and `wearApp/src/main/kotlin/world/taqwa/app/wear/complication/`.
- **Task 1 touches none of their files, so it may start at once,** in its own worktree (the execution map, `docs/superpowers/plans/2026-10-24-watch-companion-execution-map.md`). Task 3 and every later task start once Task 12 has closed, because Task 3 regenerates the vectors Task 12 changed. Task 2 starts once Task 13 has closed too, because it regenerates `watchface.xml` through `face-moon.py`.
- Task 2's face change starts from the face, the fit tools and the committed pass (`docs/superpowers/reports/2026-10-19-face-pass/`) as Task 13 leaves them.
- No task of this plan edits a file under `wearApp/src/main/kotlin/world/taqwa/app/wear/complication/`; Task 6 only calls `ComplicationSources.requestUpdates`.

**What the spec's open check found (spec 2.2, "to check before the first send"):**
- **Android** (Google's pages, read 23 October):
  - The Data Layer overview: "All Data Layer clients may exchange data either using Bluetooth or using the cloud, depending on connections available to the devices. Assume that data transmitted using Data Layer may at some point use Google-owned servers." Over Wi-Fi or LTE the path is "a network node on Google's servers as an intermediary", and "All data transferred through Google Cloud is end-to-end encrypted."
  - Data items sync to every node, the cloud node included, and a DataItem cannot be addressed to one node. So the feed **does not use `DataClient`**; spec 1.2's `PutDataMapRequest` is replaced.
  - `MessageClient` is "Bluetooth preferred, but can use Wi-Fi if it's the only type of connection available", has "No persistence and no retry", and does not support data larger than 100 KB (the messages page). Its reference: "A message is considered successful if it has been queued for delivery", "A successful result doesn't guarantee delivery", and the data is "Generally not larger than 100k".
  - `Node.isNearby()`: "this node is directly connected to this device. For example, when they are connected via Bluetooth." It is read when `getCapability` answers, before `sendMessage`, and the link can change in between.
  - So the feed goes as a `MessageClient` message to each node that has `taqwa_watch` and is `isNearby`, asked again at the send. That keeps the normal case on Bluetooth; it does not keep the data off Google's servers in every case, which Ruling 39 accepts.
- **iPhone:**
  - When the Apple Watch is out of Bluetooth range, "Wi-Fi or cellular can be used instead" (Apple Platform Security). Traffic between the pair is encrypted with the pair's own keys (IKEv2/IPsec).
  - WatchConnectivity has no API to keep a transfer on Bluetooth.
  - Apple documents no size for any WatchConnectivity call, only the error `payloadTooLarge`. The figures reported from the framework's own constants (a 2016 answer, repeated in 2022) are 65,536 bytes for `sendMessage` and `transferUserInfo`, and 262,144 bytes for `updateApplicationContext`. A binary property list stores a `String` holding any non-ASCII character as UTF-16, two bytes a unit, but stores `Data` as its own bytes.
  - So the feed goes through `updateApplicationContext` (the latest wins, and its limit is the largest) as UTF-8 `Data`, and its budget sits under the smallest figure of all (the Lead's call 4).

## Ruling 39: what leaves the phone, and what the promises say

Mohamed ruled on 23 October at about 22:10 SAST, and amended it at about 22:20 (the ledger, after Task 11's start line). The ruling answers this plan's former questions 1 and 6, so **no task waits on him**:
- **The relay is accepted on both phones** (former question 1). The feed goes only to his own paired watch, through the nearby-only sends this plan designs. When the phone and the watch are not directly connected, Google or Apple may carry it over the internet, end-to-end encrypted. Tasks 5–8 are built as written.
- **What the feed may carry** (former question 6): the city name and the Qibla's bearing to a whole degree. The distance is rounded too, since a watch compass is good only to a few degrees. Coordinates never go.
- **The location promise becomes "Only your own devices can see your location."** It replaces "Your location is never sent anywhere." and every "never leaves your phone" form, everywhere, in all seven languages. The translations follow the app's existing localisation rules.
- **The privacy policy gains one sentence on the relay:** "When your phone and watch are not directly connected, Google (Android) or Apple (iPhone) may carry this data over the internet, end-to-end encrypted so only your phone and watch can read it."
- **Plan 5 answers Play's Data safety and the App Store's privacy label again,** against their own definitions, before any build with a sender ships.
- **His standard for every line:** short, easy to understand and true.

### What leaves the phone (Task 3 builds every row but the one marked for plans 3 and 4)

| What | As sent | Why the watch needs it |
|---|---|---|
| 31 days of prayer starts, Fajr's and Asr's ends, each date's midnight | the engine's own instants, to the second as the engine gives them | every surface, and the never-early promise |
| each day's clock strings, prayer names, Hijri and Gregorian date words, and the date in full words for a screen reader (`gregorianSpoken`); on a 12-hour phone, each day's six times with their day period as well (`clockTime`, plan 3 Task 10) | the phone's text. The clock strings and `clockTime` are the first row's times at his place, as the phone writes them; `clockTime`'s day period is the phone locale's, not always the interface language's: on the English interface a he-IL phone sends "4:10 אחה״צ", an en-US phone "4:10 PM" | each watch in its phone's language; `clockTime`, the 12-hour times on Today and the Tile as the Prayer screen writes them |
| the city | the name the Prayer screen shows: `cityRepository.setLanguage(format.languageTag())`, then `displayName(cityId)`, else `location.cityName`, else `""` (Task 3's `watchCityName`, the path of `PrayerViewModel.resolveCityName`) | the Tile and Today ("the city underneath", spec 2.4), and the watch app's status screen (`StatusActivity.kt:94-99`) |
| the Qibla's bearing | the phone's whole degree, `QiblaMath.bearing(location).toInt()`: the number the Prayer screen's Qibla card shows (`PrayerScreen.kt:839`) | the dial |
| the magnetic declination | rounded to a whole degree | true north from the watch's compass, with no location on the watch |
| the distance to the Kaaba | rounded to two significant figures (Task 3's `roundedKm`), in the phone's digits and grouping. That is the whole kilometre from 10 km, 10 km from 100, 100 km from 1,000 and 1,000 km from 10,000, so the distance is about as coarse along the line as the whole-degree bearing is across it. Under 10 km it is the phone's own whole kilometre. Cape Town "6,600" (the phone shows 6,558), London "4,800", Tripoli "2,900", Riyadh "790", Madinah "340", Jeddah "69" | the dial's caption, "Makkah · %1$s km away", whose words plan 3 adds to the feed (the Lead's call 6) |
| the hemisphere | a flag | the way the Moon style draws the phase |
| the words locale's tag, direction, digit zero, the ring's digit zero where the phone's countdown ring writes Western digits and its clock strings do not (`ringDigitZero`, plan 3 Task 10), 12 or 24 hours, AM/PM markers, the countdown and next-in phrases, and the screen reader's countdown sentence with its plural phrases (`ui.spoken`) | the phone's text. `ringDigitZero` follows the phone's locale tag: on the Arabic interface an ar-SA phone sends "0", an ar-LY phone none | as the phone writes and says them |
| the rest of spec 1.3's bundle: the tasbeeh, Qibla and app words | the phone's text. **Not built in this plan:** plans 3 and 4 add each word to the feed with the screen that writes it, and add its row here (the Lead's call 6) | each watch in its phone's language |
| the screens' words (`ui.screens`, plan 3 Task 10): the titles, the two notices, the Today rows' spoken states, the tasbeeh counter's words and the built-ins' names, the Qibla dial's words and turns, and the Apple Watch's two (plan 4 Task 3): the crown's hint (`crownHint`) and the app's name (`appName`); beside them whether the interface is in Latin letters (`latinScript`). The same task's `clockTime` and `ringDigitZero` are the phone's text, in the rows above | the app's own text: its strings in the interface language (the app's name is its `app_name`), or the watch's own new phrases (the crown's hint is one); `latinScript` follows the interface language alone. The same on every phone of that language; never anything of his | each watch's screens and Tile in its phone's language and script; the crown's hint on the Apple Watch's Tasbeeh, and the app's name where the Apple Watch draws it |
| the face screen's words (`ui.face`, plan 5 Task 5): "Add the Taqwa face", what the face is, and the three lines for how a tap ends (active, choose it yourself, failed) | the phone's text, in the interface language (`WatchWords.face`) | the Galaxy watch's face screen and Today's face chip, in the phone's language; nothing of the place or the person |
| his own tasbeeh phrases, with their targets | his text | the counter's list |
| the feed's version, the time it was written; beside the feed, its fingerprint, the install's origin and the delivery number (Ruling 50) | numbers inside the feed; beside it (the path or the context key, the Lead's call 1) 16 hex characters, a random per-install number and a counter | freshness, and the watch's acknowledgement and handshake, which echo the three back |

- **Never sent:** the coordinates, the timetable chosen, the notification settings, bookmarks, counts, or anything else.
- **The fix is coarse to begin with:** Android asks only `ACCESS_COARSE_LOCATION`, and iOS reduced accuracy.
- **Who gets it:** only his own paired watch with Taqwa on it.
  - On Android, nodes with the `taqwa_watch` capability, directly connected at the send. Only Taqwa's own watch app declares that capability (the same package and signing key).
  - On iPhone, the paired Apple Watch's Taqwa app, through `WCSession`.
  - Nothing goes to Taqwa or to anyone else.
- **Whether it can cross Google's or Apple's servers:** yes, at times, encrypted end to end so that only his phone and his watch can read it (the open check above, and Ruling 39).

### The lines that change, and the plan 5 ship gate

No store build carries a sender until every line below reads as ruled or as he approved, in each of its languages (seven, or English alone for the English-only files), with the policy's date updated. The policy's own "Changes" section already promises this: "If a future version needs the network for anything new, this page will say so before that version ships". **Plan 5 applies them; this plan changes none of them.** Task 3 Step 7 drafts the translations of each, and Tasks 5 and 6 change only code comments that quote a promise (below).

Each line has six translations: `values-{ar,fr,tr,id,in,ur,bn}` for the app's strings (`values-in` is Indonesian's twin), `PRIVACY.{ar,bn,fr,id,tr,ur}.md`, `site/pages/{ar,bn,fr,id,tr,ur}/`, `docs/store/listing/{ar,fr,tr,id,ur,bn}.md` and `iosApp/iosApp/{ar,fr,tr,id,ur,bn}.lproj/InfoPlist.strings`. English only: `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, and the stores' review notes (the review section of `docs/store/listing/en.md` and the notes files in `docs/store/app-review/`). The site's privacy pages are built from `PRIVACY*.md` (`site/build.py`), so they follow the policy.

1. **The location line, as ruled.** In each of these, "Only your own devices can see your location." takes the place of the old clause, and the rest of the text stands:
   - `NSLocationWhenInUseUsageDescription`, in the seven `InfoPlist.strings` and `Info.plist`'s fallback: "Taqwa uses your location to calculate prayer times on your device. Only your own devices can see your location." It was "… Your location is never sent anywhere."
   - `onboarding_location_body`: "Prayer times depend on where you are. Everything is calculated on your device. Only your own devices can see your location." It was "… Your location never leaves your phone."
   - `location_privacy_note`: "Coordinates are stored on your device and used only to calculate times. Only your own devices can see your location." It was "… Nothing is sent anywhere."
   - `PRIVACY.md`'s Location section: "Your location is used on the device. Only your own devices can see your location." It was "Your location is used on the device and never transmitted."
   - `docs/store/listing/en.md:49`: "• Your location is used on your phone to calculate times. Only your own devices can see your location." It was "… and never sent anywhere."
   - `README.md:208`: "Only your own devices can see your location." It was "Location never leaves the phone."
2. **The relay sentence, as ruled,** in `PRIVACY.md`, as a short "Your watch" paragraph after "What stays on your phone": "When your phone and watch are not directly connected, Google (Android) or Apple (iPhone) may carry this data over the internet, end-to-end encrypted so only your phone and watch can read it." It needs one lead-in, so that "this data" names something. The lead-in is not ruled, so it is question 7's.
3. **The lines Ruling 39 does not word, but which the feed or the relay makes false.** Each is question 7's, with the draft shown there.
   - Lines true only of the phone:
     - `about_stays_title` and `about_stays_body`, and the site home page's row (`site/pages/en/home.html:105`): "Stays on your phone" / "Location, settings, bookmarks and counts never leave it."
     - `PRIVACY.md:9-19`: the heading "What stays on your phone", "Everything the app knows is stored on your device and nowhere else:", and "None of this is sent to us or to anyone."
     - `PRIVACY.md:20`: "Deleting the app deletes all of it." The same sentence closes that paragraph in each translation (lines 19-21; French "Supprimer l’application supprime tout cela."). Once a watch holds `feed.json` (the city, the bearing, the distance, a month of times, his phrases), deleting the phone app leaves it there until Taqwa is deleted from the watch too: neither platform runs an app's code at its uninstall, and question 5's default sends no "forget" message.
     - `site/pages/en/support.html:37`, both halves: "Everything Taqwa knows lives on your phone: …" and "Uninstalling the app removes all of it." (French "Désinstaller l’application efface tout."), line 37 in every language.
     - `CONTRIBUTING.md:37`: "**Nothing leaves the phone.**"
     - `SECURITY.md:3`: "Taqwa's promise is that nothing about you leaves your phone."
     - `README.md:57`: "Settings › About Taqwa says what stays on the phone and what the internet is used for", which follows `about_stays_*`.
     - `PRIVACY.md:61-67`, Backups, in each translation: "the only things of Taqwa's that are backed up are the widgets' small caches, … neither of which contains your location". On the iPhone, WatchConnectivity keeps the last application context it was given (the city, the bearing, a month of times, his phrases) in its own store outside the app's container: on the simulator, the device's `data/Library/WatchConnectivity/`. `DataStoreFactory.ios.kt`'s backup exclusions do not reach it, and Apple documents nowhere whether a backup holds it. So the line is not known to be true once the iPhone sends.
       - **The check is plan 4's,** because only a paired watch app lets the iPhone store a context (without one, `updateApplicationContext` answers `watchAppNotInstalled`): with the simulator pair, after a send, find the file and read its `NSURLIsExcludedFromBackupKey`. A file in the app's own container that is not excluded, or a store outside it, counts as backed up.
       - **Until that check says otherwise,** the policy gets question 7's Backups sentence, which is true either way. The phone's own `WATCH_FEED_FINGERPRINT` sits in the settings store, which `DataStoreFactory.ios.kt` already excludes, and it names no place.
   - Lines about the internet and third-party code. On Android the relay runs through a Google library whose data "may at some point use Google-owned servers"; on the iPhone, through Apple's.
     - `about_online_title` and `about_online_body` (`strings.xml:156-157`): "Online only when you ask" / "The internet is used for one thing: recitations you choose to download, from Taqwa’s public repository on GitHub."
     - The site home page (`site/pages/en/home.html:100-106`): the heading "Offline by design. Online only when you ask.", its lede ("The one thing it uses the internet for is recitation … Until then, it makes no network request at all."), and the row "Online only when you ask".
     - `site/pages/en/meta.json:28`, the privacy page's description: "What Taqwa stores on your phone, the one thing it uses the internet for, and what it never does."
     - `PRIVACY.md:5-7`: "It works offline. The one thing it uses the internet for is downloading Quran recitations, and only when you ask it to."
     - `PRIVACY.md:46-47`: "Until you first use recitation, the app makes no network request at all."
     - `PRIVACY.md:74`: "No third-party SDK that talks to the internet."
     - `README.md:208`: "The app makes no network request until you use Quran recitation." and "no third-party SDKs that talk to the internet".
     - The contributor constraints, `README.md:204` and `CONTRIBUTING.md:29-30`: "no network calls beyond the recitation downloads the person asked for, no third-party SDKs that phone home".
     - `docs/store/listing/en.md:50`: "• The internet is used only for recitation: the audio you ask for, and a daily check of the reciter list once you have used it."
     - `README.md:112`: "The only network use is recitation downloads from a public GitHub repository, on request."
     - `SECURITY.md:4-5`: "The one network use is downloading the Quran recitations you ask for." And `SECURITY.md:70`'s out-of-scope third-party services ("GitHub, Google Play, the App Store, Cloudflare and the phone's own text-to-speech engine"), which do not name the watch links.
   - **The stores' review notes,** English only. They sit live in Play Console and App Store Connect, and build 34 was rejected once under guideline 5.1.1 already:
     - `docs/store/listing/en.md:115`, the review notes for both stores: "Nothing is collected or sent anywhere; …".
     - App Store Connect's Notes field. Its newest file is `docs/store/app-review/2026-10-02-notes-1.0.0-35.txt`: line 7, "Taqwa calculates everything on the device, works offline, has no ads, and collects no data.", and line 17, "The only network use is recitation: …".
     - `en.md:117` says to "update the newest file and the field together when a feature, a network use or a licence changes", and that "The field holds 4,000 bytes". The newest file is 3,953 bytes, so the new text must make room for itself. Plan 5 writes a new dated notes file for the first build with a sender, pastes it into the field, and points `en.md:117` at it (its pointer still names the 29 September file).
4. **Lines that stay, and why.** Task 3 Step 7's report lists them for him to see. Its sweep (Step 7) shows that no line is in neither list:
   - "No account, no ads, no analytics" (`home.html:107`), and the policy's other "never" lines: still true.
   - `PRIVACY.md:75` ("No sale or sharing of data"), `:83` ("Taqwa collects no data from anyone, of any age.") and `README.md:115` ("no data collection"): still true. Taqwa itself receives nothing, and the feed goes only to his own watch. Plan 5's re-answer checks these against the stores' own definitions of collecting and sharing.
   - The crash report's lines (`PRIVACY.md:22-23`, `support.html:12`, `crash_sheet_body`), and read-aloud's (`PRIVACY.md:57`, and the comment at `androidApp/src/main/AndroidManifest.xml:29-30`, "nothing is sent anywhere"): still true. Neither goes to the watch.
   - The Android half of `PRIVACY.md:61-67` (Backups): still true. The Android phone keeps no copy of a sent message, and the Wear app opts out of backup as the phone does (its `data_extraction_rules.xml`, plan 1). The iPhone half is in item 3. Plan 4 keeps the Apple Watch's own stored feed out of backup, or the same paragraph says so.
   - `PRIVACY.md:87`, "If a future version needs the network for anything new, this page will say so before that version ships, with the date above updated": it stays, and it binds plan 5 to apply this list, with the date, before the build ships.
   - "Offline by design" (`README.md:9` and its badge at `:26`) and "works offline" (`site/pages/en/meta.json:18`): the app works with no connection. The watch link is optional, and its carrying is the platform's.
   - "Exodus Privacy's report finds no trackers" (`CONTRIBUTING.md:38-39`) and the site's Exodus row: plan 5 runs Exodus on the first build with a sender, and the line stays only if it still finds none.
   - `docs/STORE-PRIVACY.md` (`:17-19`, `:96`, `:122`) and `docs/STORE-CHECKLIST.md:267` and `:321`: plan 5's re-answer (Ruling 39).
5. **Code comments that quote a replaced line.** Each is rewritten to quote "Only your own devices can see your location." by the task that already edits its file. They are comments, not copy, and their reasoning for leaving backup off stands:
   - `androidApp/src/main/AndroidManifest.xml:45-51`, Task 5;
   - `androidApp/src/main/res/xml/data_extraction_rules.xml:2-7`, Task 5;
   - `wearApp/src/main/AndroidManifest.xml:8-18`, Task 6;
   - `wearApp/src/main/res/xml/data_extraction_rules.xml:2-7`, Task 6;
   - `shared/src/iosMain/kotlin/world/taqwa/app/settings/DataStoreFactory.ios.kt:14-15` ("your location never leaves your phone" has to include Apple's backup), Task 8.

## Open questions for Mohamed

Only he can answer these. None blocks a task's start: the plan proceeds on the default shown. Question 2's sign-in is needed before Task 7's pairing step, and his answer to 7 is part of plan 5's ship gate.

1. **Answered by Ruling 39:** the link may leave the direct connection, and the senders are built on both phones.
2. **Pairing the emulators takes one sign-in that only he can do** (Task 7): a Google account in the Play Store on the test phone emulator, the Pixel Watch app, and Android Studio's Pair Wearable. Default: a throwaway test account of his.
3. **The countdown in French, Turkish, Indonesian, Urdu and Bengali** is a draft (Task 1) until plan 5's native review. Default: the drafts ride in development builds until then.
4. **At most 100 of his own tasbeeh phrases go to the watch,** in order, and only as many as keep the whole feed within its 60,000-byte budget (Task 3).
   - Measured on this branch at 0298756d, with plan 1 Task 12's spoken words in: the ar-SA sample is 23,027 bytes. 100 Arabic phrases at the 60-character limit bring it to about 40.6 KB, and 100 phrases of emoji to about 52.6 KB, so all 100 go. The longest city name adds at most a few hundred bytes (Task 3's `WorstFeeds.CITY`).
   - Only phrases made of control characters, which JSON writes as six bytes each, can lose their last few. The times always go.
   - Default: 100 and the byte budget.
5. **With location turned off,** the phone sends nothing more, and the watch keeps its last feed until it runs out (up to a month). Default: keep it. The alternative is a small "forget" message to the watch.
   - Deleting the phone app cannot tell the watch either way: neither platform runs an app's code at its uninstall. The watch keeps its feed until Taqwa is deleted from it too, which question 7's deletion lines say.
6. **Answered by Ruling 39:** the place is the city, the bearing to a whole degree, the declination to a whole degree and the distance rounded, never a coordinate (the table above). The wording is the ruled line and the relay sentence. Whether the city is in fact sent, and the distance's rounding, are the Lead's call 5.
7. **The wording of the lines Ruling 39 does not word** (list item 3 above). Default: the drafts below, which Task 3 Step 7 puts into each file's languages (the English-only files stay English) beside the ruled lines, for his approval. They include the stores' review notes, which he pastes into Play Console and App Store Connect himself in plan 5. Each is meant to read short, easy to understand and true.
   - The relay sentence's lead-in, in `PRIVACY.md`'s "Your watch" paragraph: "If you add Taqwa to your watch, your phone gives it your city, the Qibla's direction and distance, your area's compass correction, the coming month's prayer times as the app writes them, and your own tasbeeh phrases." The ruled sentence follows it, so its "this data" covers every field that leaves the phone (the table under Ruling 39), his phrases among them. Plans 3 and 4's words for the screens are the app's, not his, and change nothing here.
   - `about_stays_title` / `about_stays_body` and the home page row: "Stays on your devices" / "Only your own devices can see your location, settings, bookmarks and counts."
   - `PRIVACY.md`: the heading becomes "What stays on your devices"; then "Everything the app knows is stored on your own devices:", and "None of this is sent to us or to anyone else."
   - `PRIVACY.md:20`: "Deleting Taqwa from your phone and your watch deletes all of it."
   - `PRIVACY.md:61-67`, Backups, one sentence added at its end: "If you add Taqwa to an Apple Watch, iOS keeps the last copy your iPhone sent it, and that copy may be in your iPhone's backup, encrypted by Apple." Plan 4's check (item 3) is evidence only: the sentence stays, because only a real iPhone could show the copy kept out of backup and no agent can read one (plan 4, Ruling 39).
   - `support.html:37`: "Everything Taqwa knows lives on your own devices: …", and its second half: "Uninstalling Taqwa from your phone and your watch removes all of it."
   - `CONTRIBUTING.md:37`: "**Nothing leaves the person's own devices.**"
   - `SECURITY.md:3-5`: "Taqwa's promise is that only your own devices can see anything about you. … The one network use is downloading the Quran recitations you ask for; the link to your own watch is Google's or Apple's, end-to-end encrypted." `SECURITY.md:70` lists "Google's and Apple's watch links" among the third-party services.
   - `README.md:57`: "… says what stays on your own devices and what the internet is used for …".
   - `README.md:112`: "The app's only network use is recitation downloads from a public GitHub repository, on request; the link to the person's own watch is Google's or Apple's."
   - `docs/store/listing/en.md:115`, the review notes: "Nothing is collected or sent anywhere;" becomes "Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it;". The rest stands.
   - App Store Connect's notes, in a new file in `docs/store/app-review/` made from the newest. Measured with these three changes on 24 October: 3,992 bytes, within the field's 4,000.
     - line 7 ends "… has no ads, and collects no data; it sends only to the user's own Apple Watch.";
     - section 4 gains, after the recitation sentence: "If the user adds Taqwa to their Apple Watch, the iPhone also sends it the month's prayer times, the city, the Qibla's direction and distance and the user's own tasbeeh phrases through WatchConnectivity, which Apple may carry over Wi-Fi or cellular, end-to-end encrypted.";
     - section 1's recording paragraph shrinks to make room: "Our reply of 24 September attached a screen recording from a physical iPhone (iOS 27) of version 1.0.0 (32), from first launch through every main feature." That drops its feature list and its "It predates …" sentence. If plan 5 records a new screen recording, the paragraph says so instead.
   - `about_online_title` / `about_online_body` and the home page row: "Online only for what you choose" / "The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google or Apple may carry its data over the internet, encrypted so only your phone and watch can read it."
   - The home page heading: "Offline by design. Online only for what you choose." Its lede ends: "… Until then, the app itself makes no network request. If you add Taqwa to your watch, Google or Apple may carry its data between your phone and watch over the internet, encrypted so only they can read it."
   - `meta.json:28`: "What Taqwa stores on your devices, what it uses the internet for, and what it never does."
   - `PRIVACY.md:5-7`: "It works offline. It uses the internet to download Quran recitations, and only when you ask it to. A watch you add Taqwa to may be reached over the internet too, encrypted, as below."
   - `PRIVACY.md:46-47`: "Until you first use recitation, the app itself makes no network request at all."
   - `PRIVACY.md:74`: "No third-party SDK that talks to the internet. The Android app's one Google Play services library links your phone to your own watch, and Google may relay that link, encrypted, as above."
   - `README.md:208`: "The app itself makes no network request until you use Quran recitation; the link to your own watch may be relayed by Google or Apple, encrypted." Later in the line: "no third-party SDKs that talk to the internet, apart from Google's watch link on Android".
   - `README.md:204` and `CONTRIBUTING.md:29-30`: "no network calls beyond the recitation downloads the person asked for and the link to the person's own watch, no third-party SDKs that phone home (Google's Wear OS library, for the person's own watch, is the one exception)".
   - `docs/store/listing/en.md:50`: "• The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. A watch you add Taqwa to may be reached over the internet by Google or Apple, encrypted."

## The Lead's calls in this plan

Each default below is what the tasks build. The Lead rules on each in the ledger before the task named; a different ruling changes that task alone.

1. **Delivery: acknowledged, and a handshake on reconnect** (before Task 4), **numbered since Ruling 50** (the feed's delivery number, ledger 25 October, as its two design checks amended it).
   - A `MessageClient` success is only "queued", and the call has "No persistence and no retry"; Google documents no order. If the phone remembered a feed on `SENT`, every later reschedule would answer `UNCHANGED` to a feed the watch may never have stored. And a watch away when a change was made is never sent it again: the phone's alarms reschedule only when the plan runs low or the phone has moved.
   - Ruling 48 tried to tell an old message from a new one by fingerprints alone, and three fix rounds each found another out-of-order case: a fingerprint names WHAT a feed is, never WHEN it was sent. Ruling 50 numbers every delivery.
   - **Default, all together:**
     - **The fingerprint travels with the feed, and the watch only echoes it.** The phone computes it (Task 4's `watchFeedFingerprint`) and sends it beside the feed. The phone and watch apps update separately on Play and the App Store, so after every release one of them runs a version the other does not. A watch not yet updated drops the keys a newer phone adds (`ignoreUnknownKeys`), and a newer watch reading an older phone's feed writes the new fields' defaults (`encodeDefaults`). Either way, a fingerprint of the watch's own re-encoding would never equal the phone's, and every ask would resend the whole feed. Plan 4's Swift watch would also have had to reproduce kotlinx's exact bytes (key order, `23.0`, escaping); now it stores a string.
     - **The delivery (Ruling 50 items 1 and 2).** Every send carries, beside the feed and never inside it, a `WatchDelivery`: the phone's `origin` (a random number made once per install, kept in the phone's settings store, never restored from a backup), a delivery number `seq`, strictly increasing per origin and written to the store BEFORE the link is called, so a number is never used for another feed, and the feed's fingerprint. On Android it is the path after `/taqwa/feed/` (`WatchLinkContract.feedPath`); on the iPhone the context's `delivery` key. Its text is `origin/seq/fingerprint` (`WatchDelivery.text`).
       - **The watch stores an arriving feed only when its origin differs from the stored one's, or its number is greater** (`WatchLinkContract.stores`). Anything else is stale or a duplicate, dropped unstored and unacknowledged. So what the watch holds only moves forward, whatever order the links deliver in. A greater number is stored even for the very feed it holds (only the redraw may be skipped), so that its echo names the newer number.
       - The watch stores in `WatchLinkContract.STORE_STEPS`'s order: its delivery removed before the feed is put in place, the arriving one written after, and none when that write fails. So a crash leaves an echo of nothing (one more send), never a delivery naming a feed it does not hold (a wrong `UNCHANGED`).
       - **Its acknowledgement and its handshake both echo** `WatchLinkContract.echoOf` what it holds: the delivery's text, or `""`.
     - **The phone's one record (item 3, as amended).** In the settings store, written whole in one edit, origin and numbers together: `origin`, `lastSeq` (the highest number used, sent or set aside: Ruling 52 item 2 adopts A2's wording), `lastStandsFor` (the full feed's fingerprint that send stood for; the times alone after a `TOO_LARGE` stand for the full feed), `lastSent` (the fingerprint that number travelled with: the times alone after a `TOO_LARGE`, otherwise `lastStandsFor`), `confirmedSeq` and `confirmedFor`. An ask answers `UNCHANGED` exactly when `confirmedSeq == lastSeq`, `lastStandsFor` is the current feed's fingerprint and `confirmedFor` is the session's watch. Everything else sends.
       - **An echo is taken in** (`WatchFeedRecord.echoed`): of this origin, its number becomes `confirmedSeq`, lower or higher (amendment A1/A1b: a second watch, a wiped watch or an older context re-read can echo a lower number; every echo is true when sent, and one sent before a forget names a number below the one the forget set aside, so outside the known limits a stale one costs one more send, never a wrong `UNCHANGED`); of nothing, or of another origin, the record is forgotten as for `WATCH_CHANGED`; above `lastSeq`, or at `lastSeq` with another fingerprint than `lastSent` (a record restored from a backup despite item 1, or a number used twice), `lastSeq` is lifted to it and nothing it stood for is known, so the next ask sends above it (item 5, amendment A4).
       - **Robustness (Ruling 52 item 3).** An echo numbered above `Long.MAX_VALUE / 2` (`WatchFeedRecord.MAX_ECHOED_SEQ`) counts as another origin's and is forgotten, so neither `lastSeq` nor a forget after it can overflow (an echo at `Long.MAX_VALUE` once lifted `lastSeq` there, and every later ask was `FAILED`). The store takes a stored origin only when `WatchLinkContract.isOrigin` accepts it, and otherwise starts a fresh record under a new origin, so no ask can fail for good on a delivery that cannot be made.
       - **A link that confirms (Android)** moves `confirmedSeq` only on an echo. **A link that does not (the iPhone's latest-wins application context)** also sets it on `SENT`, since the system delivers the latest handed context and nothing older after it; a throw, a refusal or a send the budget cut off leaves it where it was, so the next ask sends again (item 4).
       - **The watch a confirmation counts for (amendment A3).** On the iPhone `confirmedFor` is a random UUID the bridge keeps in `WCSession.watchDirectoryURL` (written when absent; that directory is per watch, and iOS deletes it on unpair or app removal), read through `WatchSessionHook.watchKey()`. It is set with `confirmedSeq` on `SENT` (the key read before the call) and on an echo from that watch; an ask whose session watch has another key reads nothing as confirmed, and an echo from a watch that is not the session's is ignored. That makes `WATCH_CHANGED` durable: a watch switched to while the app was not running, or a change lost to a process death, is still sent the feed. **On Android (Ruling 52 item 9)** `confirmedFor` is the Wear node id of the watch whose echo was taken in last (the message's `sourceNodeId`). Every echo is taken in, one from a watch no longer nearby among them (its own word on what it holds, so a wipe heard from afar is never lost), and an ask whose nearby nodes with Taqwa (`WatchLink.watchKeys`, the node ids `getCapability` answers nearby) do not include that node reads nothing as confirmed, so a watch switched to between asks never inherits another's confirmation. A second watch beside the first, and a watch switched to while an ask builds its feed, are known limits below, each healed by the new watch's own ask.
     - **The handshake:** the watch's request on `/taqwa/refresh` carries its echo. The phone takes it in, builds its current feed, answers `UNCHANGED` when the record now holds it (the watch echoed the latest delivery, of this very feed), and sends otherwise. The watch shakes hands when the phone's `taqwa_phone` capability comes back nearby, at every daily nudge, and when the app opens on a feed that needs one. So a watch that missed a change while away is sent it as soon as it hears the phone come back, or at the latest at the next day's nudge, even when no capability changed on the phone.
     - **The iPhone's watch** shakes hands the same way, with `sendMessage(["refresh": <its echo>])`, and does not acknowledge. Plan 4's activation handshake waits for `ConnectivityDrain.isDrained`, within its 10 s, so its echo names what was just stored.
     - **A watch coming back is damped like a reschedule** (spec 1.2: "An unchanged feed … is not resent"). `WATCH_APPEARED` is the Android phone's `onCapabilityChanged` with a nearby watch (every Bluetooth reconnect) and the iPhone's `activationDidCompleteWith` (every launch, background refreshes included). It answers `UNCHANGED` when the record holds the feed, and sends otherwise. Undamped, each of those would send the whole feed again with a new `writtenAt`, wake the watch's complications and, on watchOS, reload every widget timeline.
       - What the record cannot know is a watch that holds nothing it names: the watch app reinstalled, a new watch, a reset. Such a watch says so itself: its handshake echoes nothing, and an echo of nothing clears the confirmation. The Galaxy watch shakes hands at its process start when it holds no phone feed (Task 6's `AskWhy.STARTED`), when the app opens, when the phone comes back nearby and daily; plan 4's watch at activation.
       - **A forget** (`WatchFeedRecord.forgotten`, item 6 as amendment A2 completed it after D1 in Ruling 50's review) confirms nothing and sets the next number aside: `lastSeq` goes up by one, standing for nothing and never sent, so the next send takes the number after it. So once the phone has heard the watch say it holds nothing (or another install's feed), no echo the watch sent before that, an acknowledgement still on its way when it was wiped among them, can name the latest number again. It costs no send: every forget already makes the next ask send.
       - **`WATCH_CHANGED`** is the one reason that forgets the confirmation before it is served, then sends (item 6): the iPhone's `sessionWatchStateDidChange`, which fires only when `isPaired`, `isWatchAppInstalled`, `isComplicationEnabled` or the watch directory changes. Android has no such signal (a reinstall and a reconnect raise the same capability change), so there the watch's own handshake does it. Task 7's harness `send` uses it too.
     - **The known limits** (the design checks, both lenses; the report `docs/superpowers/reports/2026-10-25-feed-delivery-number.md`):
       - two origins cannot be ordered: a straggler of an earlier install (a message handed to Play services before an uninstall, a cleared app or a phone move, or a watch taken back to a phone it left), delivered after the new install's feed, replaces it, and until its acknowledgement or the watch's next handshake reaches the phone a damped ask may answer `UNCHANGED`. Ordering origins by `writtenAt` would let a phone whose clock runs behind be refused for good;
       - a damped ask cannot know a watch whose echo it has not taken in: a second watch nearby or switched to, a wiped watch whose handshake was lost. Until the phone takes in an echo of nothing from a wiped watch, an echo that watch sent before the wipe may still arrive and confirm, even after one it sent since (echoes carry no time: `aStaleAckOvertakingAWipedWatchsNewerOneIsHealedByItsHandshake`). Ruling 52 item 1 accepts that stale acknowledgement overtaking a newer one as a known limit: the model saw it about once in 300,000 schedules, and the watch's next handshake heals it. Each watch's own handshake heals the rest;
       - **two Wear watches connected to the phone at the same time** (Ruling 53 item 3): `confirmedFor` is one node id, so while the confirmed watch is nearby, the other, if it missed a send (away at it, or the message lost), can be answered `UNCHANGED` until its own handshake (`AskWhy.STARTED`, `PHONE_NEARBY` or the daily one) heals it (`aSecondWatchBesideTheFirstIsSentTheFeedAtItsOwnHandshake`). A phone normally keeps one watch connected;
       - **on Android, a watch switched to while an ask builds its feed** (Ruling 54's review, round 1): the ask judges `UNCHANGED` by the watches it read before the build (Ruling 54 item 4's one reading per ask), so with the confirmed watch read there and the other nearby by step 4, it answers `UNCHANGED` and sends nothing. Whatever tells the phone of the switch, the new watch's capability change (`WATCH_APPEARED`) or its handshake (`PHONE_NEARBY`), comes after that reading, so it is queued behind the ask and served on a reading of its own. The capability change heals it once the old watch has left; otherwise the old watch's confirmation still counts there, as for the second watch above, and the new watch's handshake does (`aWatchSwitchedToDuringTheBuildIsSentTheFeedAtItsOwnNextAsk`, and `aWatchSwitchedToDuringTheBuildHoldingAnOlderDeliveryIsSentTheFeedAtItsHandshake` for a watch whose handshake echoes an older delivery of its own). Ruling 55 item 1 ratified this limit, option (a). Reading the watches again at step 4 (option (b)) would narrow it to that query, as before Ruling 54, at the cost of a third capability query for every ask that sends, which Ruling 54 item 4's shared reading rules out. A second reading after the build, shared by `watchKeys()` and `send()`, is not taken either, because it moves the send's nearness reading away from the send;
       - outside the documented platform (a record restored from an older snapshot, Quick Start carrying the record and the watch key, a context the system loses), a damped ask may answer `UNCHANGED` before the watch's first echo, which no phone-side rule can see; that echo heals it.
   - **Alternatives:** a fingerprint that expires after a few hours; or one remembered per node and cleared on `WATCH_APPEARED`. For the fingerprint's carrying: the watch fingerprints the text it received, with `writtenAt` set to 0 at the `JsonObject` level. That is not the default, because plan 4's Swift would still have to reproduce the phone's bytes. Ruling 48's fingerprint-only lists (`onTheirWay`, the stand-in record) are replaced by Ruling 50.
2. **Urdu's ۴ ۶ ۷ on the face** (before Task 2).
   - Plan 1's close gives the Light subset's item to plan 2 (ledger L219, L300). `subset-noto.py` maps U+06F0–U+06F9 in the clock's Light weight straight to their `pnum` glyphs, which takes ۴ ۶ ۷ out of Noto's `URD` `locl`: an Urdu phone's clock shows Persian forms.
   - The SemiBold and Bold weights keep the `locl`, but the runtime shapes in the *watch's* language, and the face cannot name one. So on any watch not set to Urdu, the slots' times, the dates and the countdown of an Urdu phone show Persian forms too, beside the phone's Urdu ones. No feed has shown it yet; Task 3's is the first Urdu one.
   - **Default:** all three weights map U+06F4, U+06F6 and U+06F7 through Noto's own `URD` `locl` (the Light weight then to that glyph's `pnum` form), under new names for all three files. An Urdu phone's digits are then Urdu forms on every watch, as the phone draws them.
   - The cost: a phone outside the seven languages whose digits are U+06F0–U+06F9 (Persian) would show those three in Urdu forms. Under call 3 no such phone sends those digits.
   - **Alternative:** the Light weight alone, as the close's row says, accepting Persian forms in the slots on watches not set to Urdu.
3. **A phone whose language is outside the seven, and a digit set the face was not built for** (before Task 1).
   - **Default:** the words, the direction and the day markers follow the app's interface language, which is English for those phones. The phone's own locale gives the date words only when the tag's own language is the interface language (Task 1's `isTagsOwnLanguage`). `UiLanguage.of(tag)` is never the test, because it answers English for every tag outside the seven, so he-IL with English would pass it.
   - The digits are the phone's own only in the (language, digits) pairs the face is built and measured for: English, French, Turkish and Indonesian with Western digits; Arabic with Western or Arabic-Indic; Urdu with Western or Extended Arabic-Indic; Bengali with Western or Bengali. Any other pair is written in Western digits throughout: fa-IR's Persian digits, mr-IN's Devanagari, an `-u-nu-arab` English phone.
   - Those phones are the one case where app ≠ watch: the app writes its dates in the locale's own words and script, and may draw other digits. A Hebrew, Hindi or Chinese word is in none of the face's fonts, and English words beside Persian digits fall outside the fonts the face picks for an English line.
   - **Alternative for Devanagari** (Marathi, Nepali): measure it in Task 2 with the emulator's Devanagari font, as Bengali is measured, and send it.
4. **The feed's byte budget and the iPhone's transport** (before Task 3).
   - **Default:** `WatchLinkContract.FEED_BUDGET_BYTES` = 60,000, in UTF-8, which is what both links carry:
     - Android sends the JSON's UTF-8 bytes;
     - the iPhone puts the same bytes in the application context as `Data`, which a property list stores as they are. As a `String`, a feed holding a single Jumuʿah's ʿ would be stored in UTF-16 at twice its size: the English sample is 18,873 bytes in UTF-8 and 37,738 in UTF-16 (at 0298756d).
   - 60,000 sits, with a 1,024-byte envelope, under every limit: the Data Layer's 100 KB, and the smallest WatchConnectivity figure (65,536, `transferUserInfo` and `sendMessage`), let alone `updateApplicationContext`'s 262,144. So `updateApplicationContext`, the latest-wins call with the largest limit, carries it, and even a later move to `transferUserInfo` would fit.
   - The times always go: 31 days in any language are at most 40,000 bytes. The phrases go in order while they fit.
   - Plan 4 measures the real thresholds on the paired watch, as its ship gate. A `payloadTooLarge` on the iPhone sends the times again without the phrases (Tasks 4 and 8), so nothing is ever lost whole.
   - **Alternative:** compress the feed (zlib on both sides). That would be a format change for both watch apps, which this plan does not need.
5. **Whether the city is sent, and how far the distance is rounded** (before Task 3). Ruling 39 allows the city but does not require it.
   - **Default:** the city goes, as the Prayer screen names it (the table under Ruling 39). Spec 2.4 draws it under the Tile, Today shows it, and the watch app's status screen shows only `place.city` when it has a feed (`StatusActivity.kt:94-99`), so an empty city would leave that screen blank.
   - **Default for the distance:** two significant figures (Task 3's `roundedKm`), so the distance is about as coarse along the line as the whole-degree bearing is across it, and under 10 km the phone's own whole kilometre. The cost: the watch's caption reads "6,600 km" where the phone's reads "6,558 km".
   - **Alternatives:** an empty city, with the Tile, Today and the status screen showing none (plan 3 would draw the Tile without spec 2.4's city); or the distance to the nearest 100 km, which near the Kaaba says nothing (Jeddah's 69 km would read "100").
6. **Spec 1.3's tasbeeh, Qibla and app words** (before Task 3).
   - Spec 1.3: everything the watch writes on its screens arrives in the feed, so that "each watch in its phone's language" holds by construction. This plan sends the words the face and the complications draw and say (`WatchUi`'s names, Jumuʿah, countdown, `nextIn`, `am`/`pm`, `spoken`, and each day's `gregorianSpoken`). `WatchUi`'s own KDoc says the rest of the bundle "joins it as the screens that need them are built".
   - **Default: plans 3 and 4 add them, with the screens that write them.**
     - Plan 3 adds every word the Galaxy app's screens and Tile write, including those both watches share.
     - Plan 4 adds the words only the Apple Watch writes (the crown hint, the Smart Stack's).
     - Each word becomes a `WatchUi` field with a default (`""`), filled by `WatchFeedBuilder`. A word the phone already has comes from `getString` in the interface language, as `watchUiLanguage` reads `ui_language`. A new phrase goes in `WatchWords`, in seven languages, the six that are not English as drafts to native review.
     - A field with a default keeps `WatchFeedJson.VERSION` at 1 (the `WatchFeed` KDoc's rule). Nothing ships before plan 5, so no watch in anyone's hands ever reads a feed without them.
     - The watch draws a screen's words from the feed, never from its own resources. The exceptions are spec 1.3's list of what the watch system shows, and the lines a watch must show before it holds any feed: `open_taqwa_on_phone` and `update_taqwa_on_watch` (`wearApp/src/main/res/values/strings.xml`), which the feed's words replace once a feed is held.
     - Each plan re-runs this plan's size tests with its fields in (Task 3's `theTimesAloneLeaveRoomForThePhrases`, `theLargestFeedStaysWithinItsBudget`, `phrasesGoWhileTheyFit`; Task 5's `aFeedAtTheBudgetIsWithinTheMessageLimit`; Task 8's `theLargestContextFitsTheSmallestLimit`). It regenerates the sample vectors and Task 3's goldens, and adds its words' row to the table under Ruling 39.
   - **The phone strings they would reuse,** with their keys: `tasbeeh_round` ("ROUND %1$s"), `tasbeeh_of`, `tasbeeh_preset_after_prayer` and the six other `tasbeeh_preset_*`, `tasbeeh_title`, `qibla_title`, `qibla_back` ("Today"), `qibla_facing_qibla`, `qibla_distance` ("Makkah · %1$s km away"), `qibla_needs_calibrating`, `qibla_interference_title` and `qibla_best_effort_title`.
     - Measured on 24 October in `composeResources/values*/strings.xml`, with 25 bytes a field for the key and the quotes, those 17 take 662 bytes in English, 810 in Arabic, 815 in Urdu and 1,085 in Bengali.
     - The new phrases (the bezel and crown hints, "Hold your wrist level") add a few hundred more, so the words come to under 2 KB in any language. Task 3's report records the largest times-alone feed beside `TIMES_ALONE_MAX_BYTES` (40,000), so plans 3 and 4 know the margin they add into. In the 60,000-byte budget, 2 KB costs his phrases at most five, and only phrases made of control characters (question 4).
   - **Why not now:** the screens decide which words they need. Which compass states Wear OS and watchOS can report, and the short forms a 1.4-inch screen needs in place of the phone's long help lines, are plan 3's and 4's to design. Words added now would be guesses.
   - **Alternative:** add all of 1.3's words now, from the phone's keys above plus drafts of the new ones, and re-measure here.
7. **The Moon's under-a-minute countdown in French, Indonesian and Bengali** (before Task 1).
   - Plan 1's Task 11 measured the first drafts on the Moon's new countdown curve (`moon-fit.py forms=`, ledger, Task 11 fix round 1's minors): "dans moins d’une minute" 139.2°, "kurang dari semenit lagi" 133.6° and "এক মিনিটেরও কম সময়ে" 121.2°, against the curve's 121.1°. As drafted they would stop Task 2.
   - **Default: shorter drafts, with the same meaning, measured in Task 2:** French "d’ici une minute" ("within a minute"), Indonesian "kurang dari semenit" (the same words without "lagi"), Bengali "এক মিনিটের কম সময়ে" (without the emphatic ও). None holds a digit, since the watch writes digits only where it fills a number. They stay drafts to native review (question 3).
   - **Alternative:** keep the first drafts and change the Moon's curve, which is a face change and a ruling.

## Global Constraints

- **Where:** the worktree `~/Desktop/Workspace/apps/Taqwa/.claude/worktrees/site`, branch `watch-companion`, or the lane worktree the execution map assigns. The ledger stays in `site` (it is git-ignored), appended by its absolute path.
  - Start every shell command with `source ~/.zprofile; source ~/.zshrc`, and run Gradle from the worktree root.
  - Nothing is pushed.
  - Commits use `git commit -F <file>`. Then check `git log -1`. No `Co-Authored-By` and no AI attribution line, ever. Make WIP commits as you go; continue from WIP an interrupted run left.
- **Ledger:** append short dated lines to `.superpowers/sdd/2026-10-19-watch-companion-1-galaxy-face/progress.md`, under a "Plan 2" heading, as each task starts and ends.
- **Packages and signing:**
  - The phone and the watch are both `world.taqwa.app`.
  - The debug builds of both are signed with the default `~/.android/debug.keystore`, so the Data Layer connects them.
  - Agents never read or use release keys (`~/keys`, `keystore.properties`). Never run `:androidApp:assembleRelease`, `bundleRelease` or `installRelease`, because those sign with the upload key. The phone's release is checked through `:androidApp:processReleaseMainManifest` and `:androidApp:minifyReleaseWithR8`, which do not sign.
- **Devices:**
  - The physical phone `KB2604110095` is plugged in. Never install on it or touch it.
  - Every `adb` command names its serial with `-s`.
  - Install with `adb -s <serial> install -r <apk>`, never with Gradle's `install*` tasks, which install on every attached device.
  - Only simulators on the iPhone side: never the work iPhone 12 or his phones.
- **Emulators:**
  - The Wear emulator is `Taqwa_Wear_OS_6` on port **5558** (`emulator-5558`); reuse it if it is running.
  - The pairing phone is `Taqwa_Phone_Wear` on port **5562** (`emulator-5562`). The phone without Play services (Task 7, if the Lead allows the download) is `Taqwa_Phone_NoPlay` on **5564**. The Lead's phone is 5556 and Dev 2's is 5560.
  - Stop an emulator only with `adb -s emulator-55xx emu kill`, never with `pkill`.
- **The face:** it is signed only with `~/.android/taqwa-face-debug.keystore`. This plan changes the face in one place: Task 2's Urdu digits (the three Noto subsets under new names, and every line that names them, `watchface.xml`'s included through its generators). No other face file changes. A form that does not fit is handed to the Lead, never fixed on the face.
- **The library:** `play-services-wearable` is acceptable "as long as it doesn't add any trackers or any unnecessary permissions" (Mohamed). The verdict on each module it joins rests on three checks, as on 18 October:
  - the phone's merged release manifest keeps the same 12 `uses-permission` entries, and `:wearApp`'s keeps only androidx.core's own;
  - the only other manifest additions are `GoogleApiActivity` (`exported="false"`), the `com.google.android.gms.version` meta-data and our own listener service;
  - no Firebase, measurement, ads, adservices or crash-reporting code is added: every package matching the tracker pattern that the debug APK holds and did not hold before the library is either absent from the release dex (`apkanalyzer dex packages` on the R8 release's dex, read through its mapping) or explained in the report (Ruling 53 item 1). Task 5 Step 1 records that baseline for the phone, and Task 6 Step 1 for the watch. Plan 5's Exodus scan of the release build stays the final word.
    - The scan is a diff, never an empty result, because today's APKs already match the pattern without any tracker. The phone ships media3 (`shared/build.gradle.kts:90`, for `RecitationService`), whose `androidx.media3.exoplayer.analytics` holds `AnalyticsListener`. The face workflow's `wearApp-debug.apk` of 24 October, with no ads library in `:wearApp`, already defines `android.adservices` and nine of its subpackages.
    - Each baseline match is listed in the report with the library or build step it comes from.

  The dependency diff is recorded, not counted:
  - On the phone, `play-services-wearable`, `-base`, `-basement` and `-tasks` arrive with `androidx.fragment:fragment` (1.1.0, asked by `-base` and `-basement`). Fragment brings `androidx.loader:loader` and `androidx.viewpager:viewpager` 1.0.0, and possibly other AndroidX the classpath lacks today. None of these is on the phone's release classpath now.
  - On the watch, the diff should be the four Google libraries alone, since `:wearApp` already has fragment 1.1.0 through androidx.preference and appcompat.
  - Every addition is listed in the report with the library that pulled it in.

  Add no other dependency. In particular, no `kotlinx-coroutines-play-services`: a few lines of our own await a `Task`.
- **Transport, Android:**
  - The feed goes over `MessageClient.sendMessage(nodeId, "/taqwa/feed/<origin>/<seq>/<fingerprint>", utf8Json)` (`WatchLinkContract.feedPath(delivery)`, Ruling 50). It goes only to nodes from `CapabilityClient.getCapability("taqwa_watch", FILTER_REACHABLE)` that are `isNearby`, asked again at the send.
  - The fingerprint is 16 lowercase hex characters (`WatchLinkContract.isFingerprint`), computed by the phone alone, and the origin the same form (`isOrigin`), a random number made once per install. The watch stores the delivery beside the feed and echoes it; it never computes one (the Lead's call 1).
  - Never use `DataClient`, `putDataItem`, `ChannelClient` or any asset.
  - The watch's handshake is `"/taqwa/refresh"`, sent only to nearby nodes with `taqwa_phone`. Its payload, in UTF-8, is `WatchLinkContract.echoOf` the delivery it holds, or empty.
  - The watch's acknowledgement is `"/taqwa/ack"`, sent to the node the feed came from, after each feed it stores. Its payload is the same echo, of the delivery it now holds.
  - Neither says anything about the place or the person: a random number, a counter and a fingerprint.
  - **Each answer is read in its own code space, never mixed:**
    - `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)`, the public reference's call (Ruling 54 item 1; never `GoogleApiAvailabilityLight`, which is `@ShowFirstParty` and `@KeepForSdk`), answers a `ConnectionResult` code. Anything but `ConnectionResult.SUCCESS` (0) is no watch. Nothing that shows an update prompt, a dialog or a notification is called.
    - `ApiException.statusCode` from a Wearable call is a `CommonStatusCodes` code (`WearableStatusCodes` extends that space). `CommonStatusCodes.API_NOT_CONNECTED` (17, "an API that failed to connect": no Wear OS app, Play services missing or updating) is no watch. From `sendMessage`, `WearableStatusCodes.TARGET_NODE_NOT_CONNECTED` (4000) is that node gone. Every other status is a failure: in particular `CommonStatusCodes.CANCELED` (16), `TIMEOUT`, `NETWORK_ERROR`, `INTERNAL_ERROR` and `ERROR`.
    - The `ConnectionResult` inside a 17 (`status.connectionResult?.errorCode`: `API_UNAVAILABLE` 16 for a phone without the Wear OS app, `SERVICE_MISSING` 1 …) is logged, never compared. `CommonStatusCodes.SERVICE_VERSION_UPDATE_REQUIRED` and `SERVICE_DISABLED` are deprecated, "handled during connection, not during API requests", so they arrive inside that 17 and are not listed.
  - Nothing that can show UI or a notification is ever called.
- **Transport, iPhone:**
  - The feed goes as `WCSession.default.updateApplicationContext(["feed": <the JSON's UTF-8 bytes as Data>, "delivery": <WatchDelivery.text as a String>])`: the whole document, and the latest wins. Never `transferUserInfo`, and never `sendMessage`, for the feed. On `payloadTooLarge` the same feed goes once more without its phrases, under the next number, inside the same budget.
  - The watch shakes hands with `sendMessage(["refresh": "<WatchLinkContract.echoOf what it holds>"], replyHandler:)`, and the phone replies `["queued": Bool]`, asynchronously, so that WatchConnectivity's delegate queue never waits for the sync (Ruling 52 item 6). The handshake's ask is queued on the caller's thread, so the sync keeps WatchConnectivity's arrival order, and the app holds UIKit background time until it replies (Ruling 53 item 4).
  - The session is activated at launch whenever `WCSession.isSupported()`. Before each send, the phone checks `activationState == .activated && isPaired && isWatchAppInstalled`. Those three are only valid after activation, so spec 1.2's "activated only when isPaired && isWatchAppInstalled" cannot be done as written.
- **Logs (Ruling 52 item 4), on every platform:** a release log line names a delivery or an echo by its number and fingerprint alone, `<seq>/<fingerprint>` (`loggedDelivery` on the phones, `WatchFeedBridge.logged` from Swift; `none` for an echo of nothing and `unreadable` for anything else), never by its origin, which is the install's one random number and the same in every feed of that phone. No log line carries the place or the bearing. Only a debug harness (Task 7's `WatchFeedDebug`, the watch's debug seams, plan 4's `WatchHarness`) may log an origin. On the iPhone, a release log line (`TaqwaWatch`, and plan 4's watch lines) never interpolates into `NSLog`'s format string: every one is `NSLog("%@", line)`. The rule is for release lines (Ruling 53 item 4): the `#if DEBUG` `TaqwaHarness` lines in `iOSApp.swift`, which no shipping build holds, are outside it.
- **Sizes:** the encoded feed's UTF-8 bytes stay within `WatchLinkContract.FEED_BUDGET_BYTES` = 60,000. The contract also holds `MESSAGE_LIMIT_BYTES` = 100,000, `USER_INFO_LIMIT_BYTES` = 65,536, `CONTEXT_LIMIT_BYTES` = 262,144 and `ENVELOPE_BYTES` = 1,024, each with its source in the KDoc (the Lead's call 4).
- **Visibility:**
  - Every declaration in `world.taqwa.app.watchfeed` is `internal`. So are `WatchWords`, `WatchDateLine`, `AndroidWatchLink` and `PlayWatchNodes`.
  - The exceptions are the bridge entry points: Task 8's `WatchFeedBridge` and `WatchSessionHook`, and Task 7's debug seam `WatchFeedDebug`. They speak only in strings, Booleans, `Int`s and, for the feed's bytes, `NSData`.
  - `:watchcore` is an `implementation` dependency of `:shared`, never exported. No feed type (the `Watch*` names Task 1 Step 4's grep lists) reaches the iOS framework's header or `:androidApp`. Plan 3 moves the tasbeeh and compass rules into `:watchcore` (its Tasks 1 and 2); those types may reach the header with watchcore's prefix, and no Swift names them.
- **Permissions:**
  - The phone gains none.
  - `:wearApp` declares **no `INTERNET` and no location permission**, and in this plan no permission at all. Its merged manifest keeps only androidx.core's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, and plan 3's `VIBRATE`, which plan 3's Task 4 added on 30 October, after Task 6.
- **No time is computed on the watch:**
  - `:wearApp` never links `:shared` or the engine.
  - Every instant in the feed is the engine's own `epochSeconds`, unchanged, so app = site = watch.
- **The feed's content (spec 1.1 and the rulings):**
  - **Days:** the phone's yesterday and the 30 days from today, 31 in all (Ruling 7). The date at the place is in `TimeZone.of(location.timeZoneId)`.
  - **Midnight:** `WatchDay.midnight` is each date's start in that zone (Ruling 8).
  - **Language: the app's own choice, never the locale tag.** The interface language is what the app's `uiLanguage()` reads: the `ui_language` string resource, as Compose Multiplatform resolves it (`DisplayNames.kt`, `App.kt`'s `stringResource(Res.string.ui_language)`). Off the composition the feed reads the same resource through `getString(Res.string.ui_language)` (`watchUiLanguage()`, Task 1), and never `UiLanguage.of(format.languageTag())`. `uiLanguage()`'s KDoc records why: a per-app language or a regional variant once gave Arabic strings with the tag's English rule. When the app's language changes in place, `LanguageChangeReschedule` already reschedules, and the reschedule asks the watch.
    - The names, the countdown, `nextIn`, Jumuʿah and the Hijri month are in that language.
    - The date words (`shortWeekday`, `shortMonth`) and the day markers (`dayPeriods`) are CLDR's in the *words locale*. That is the phone's own locale when the tag's own primary language subtag (with `in` read as `id`) is the interface language's code, and otherwise the bare interface language (English, for phones outside the seven). The test is `isTagsOwnLanguage` (Task 1), never `UiLanguage.of(tag) == language`.
    - `rtl` is the interface language's `rtl`, the app's own rule (`isRtlLocale() = uiLanguage().rtl`). `LayoutDirection.isRtl` is not used.
    - `ui.language` is the **words locale's tag** (Task 1's `wordsLocaleTag`): the phone's own tag when the tag's own language is the interface language, otherwise the interface language's code, always written with `-`. The watch takes letter case from it and nothing else: plan 1's slot A label is `names.of(prayer).uppercase(Locale.forLanguageTag(feed.ui.language))` (`ComplicationFields.kt:186`), which must equal the app's `uppercaseIn(language)` (`Casing.kt`). With the phone's raw tag, an az-AZ phone on the English interface would get "MAGHRİB" (Azerbaijani's dotted capital) against the app's "MAGHRIB". Words, direction and digits come from the feed's words, `rtl` and `digitZero`, never from this tag.
  - **The screen reader's words** (plan 1's Task 12, Ruling 38), both built on the phone, both from the same resource environment that `watchUiLanguage()` reads:
    - `ui.spoken`: the phone's `prayer_a11y_countdown`, `prayer_a11y_countdown_soon` and `duration_hours_minutes` as templates, and the plurals `a11y_hours` and `a11y_minutes` asked for every number from 0 to 99 with `"%1$s"` as the argument, kept as runs (`WatchCount(upTo, phrase)`). So the watch never applies a plural rule, and its sentence is the Prayer screen's (`PrayerScreen.kt:566-577`).
    - `days[].gregorianSpoken`: "weekday day month" with the weekday and the month in full, CLDR's `EEEE` and `MMMM` in the words locale, in the phone's case and the feed's digits (`Sunday 18 October`, `الأحد ١٨ أكتوبر`). Never the face's capitals.
    - A read that throws fails the ask (`FAILED`), as the language's does: the words are never guessed.
  - **Digits** (Ruling 9, "the zero of the phone's digit set", and the Lead's call 3): the phone's digits are `format.localizedDigits(0..9)`. When (interface language, zero) is one of the face's pairs, `digitZero` is that zero and every digit is the phone's. Otherwise every digit in the feed is Western and `digitZero` is `'0'`. `digitZero` is always the zero of the feed's own strings; `WidgetDigits.nativeZero` is not used.
  - **Hijri:** `hijriDayMonth` is built from the same parts as `hijri` (Ruling 24).
  - **Clock strings:** in 12-hour, `clock` holds the time *without* its day period ("4:10"). The markers go in `ui.am` and `ui.pm`, as the face and the samples expect.
  - **12-hour and RTL:** they reach the face through slot 7's title and text, which plan 1 built from `ui.twelveHour`, `ui.rtl` and `ui.digitZero` (Ruling 27). The phone only fills those fields.
  - **The place, as Ruling 39 allows it** (and the Lead's call 5):
    - `city` is the name the Prayer screen shows (`watchCityName`, Task 3), `""` only when the location has neither a city id the list knows nor a stored name;
    - `qiblaBearing` is `QiblaMath.bearing(location).toInt().toDouble()`, the phone's own readout (`PrayerScreen.kt:839`);
    - `declination` is `WorldMagneticModel.declinationDegrees(…)` rounded to a whole degree;
    - `qiblaKm` is `WatchFeedBuilder.roundedKm(QiblaMath.distanceKm(location))`, two significant figures, written by `WatchFeedBuilder.distance()`, which supersedes the formula first written here (`localizedGroupedKm(…, format)`): the phone's own Qibla card string where the feed keeps the phone's digits, and otherwise Western throughout, its thousands mark included (`DigitGrouping`'s for Western digits in the interface language), so no U+066C stands beside Western digits (Ruling 48);
    - `southern` is latitude < 0.
    - Never a coordinate.
  - **Every string is the phone's:** words, digits, thousands grouping and the city's name come from the phone's own functions. A fixture or a sample that disagrees is changed, never the builder. The exceptions are the Lead's call 3's, for phones outside the seven and unbuilt digit pairs, and Ruling 39's rounding of the place.
  - **Size:** within `FEED_BUDGET_BYTES` in UTF-8. The times always go; the phrases go in order while they fit.
  - **Format:** `WatchFeedJson.VERSION` stays 1. This plan adds no field to the model: `hijriDayMonth` (Ruling 24), `ui.spoken` and `days[].gregorianSpoken` (Task 12) are plan 1's, each with a default, and the phone fills all three. The fields plans 3 and 4 add for spec 1.3's other words each have a default, which keeps it at 1 too (the Lead's call 6). The fingerprint travels beside the feed (the path, the context key), never inside it.
  - **The string bundle (spec 1.3), in part:** the feed carries the words the face and the complications draw and say. Its tasbeeh, Qibla and app words are plans 3 and 4's to add (the Lead's call 6). No task here draws a watch screen's words from the watch's own resources.
- **The reminders come first:** nothing the watch side does may delay or fail a reschedule, a widget step, or the next reschedule.
  - The coordinator only *asks* (`WatchFeedSync.request`, which neither suspends nor blocks), and its lock never waits on the watch.
  - The sync runs on `WatchFeedSync`'s own process scope, on `Dispatchers.Default`, never on the caller's dispatcher (Main for a settings write or a language change).
  - One 5-second budget covers a whole ask: the reachability question, the build (31 engine days, DataStore reads, the resource read), the encode and the send.
  - With no watch there, an ask costs one capability query and builds nothing. On Android `reachable()` and `watchKeys()` share the ask's one capability reading and `send()` asks again at the send, so an ask that sends makes two queries and any other ask one (Ruling 54 item 4).
  - The background entry points whose process may stop when they return call `settle()` last, after every widget step, inside their own budgets: `SystemEventReceiver`, `PrayerAlarmReceiver`, `NotificationTopUpWorker` and the iPhone's `BackgroundRefreshBridge`.
  - **No caller waits on the watch side unbounded,** even with the sync's worker gone. `sync`, `acknowledge` and `settle` each bound their own wait by `WatchFeedSync.CALLER_BOUND` (10 s: the ask being served, then this one). When the bound passes, `sync` answers `FAILED` and `settle` answers false; the ask itself stays queued. Every caller also wraps its call in `withTimeoutOrNull(WatchFeedSync.CALLER_BOUND)`:
    - `PhoneWatchListenerService`'s three bodies, each inside `runBlocking`;
    - the iPhone's `WatchFeedBridge.refreshRequested`, from a coroutine on the bridge's own scope, never `runBlocking`: it queues its ask and returns, and answers WatchConnectivity's reply handler when the bound ends or the sync answers, so WatchConnectivity's delegate queue never waits for the sync (Ruling 52 item 6). The coroutine starts `UNDISPATCHED`, so the ask is queued in WatchConnectivity's order, and UIKit background time is held until the reply (Ruling 53 item 4);
    - `NotificationTopUpWorker`'s `settleWatch`, since `topUp` has no budget of its own;
    - `SystemEventReceiver`, `PrayerAlarmReceiver` and `BackgroundRefreshBridge`, inside their own budgets as well;
    - Task 7's `WatchFeedDebug`.
  - Every call into the watch side catches `Exception`. A `CancellationException` is rethrown only when the caller's own coroutine has been cancelled (`ensureActive()` throws). One thrown while it is still active is a failure like any other. That covers `java.util.concurrent.CancellationException` from a Play services `Task.getResult()`, or any library's. So a stray one never ends the sync's worker.
  - `WatchFeedJson.encode` throws on NaN or Infinity, and that throw is caught as well.
  - Our own `Task.await()` reads a `Task`'s result only when it succeeded. A failed task gives its own exception, so the `ApiException` 17 reaches `noWearApi`. A cancelled one gives our own `TaskCancelled`, never a `CancellationException`.
  - **It is cancellable** (`suspendCancellableCoroutine`, never `suspendCoroutine`), on the phone and on the watch. A Play services `getCapability` or `sendMessage` that never answers (Play services updating, for example) is then cut by the budget around it. With a non-cancellable await it could not be: `WatchFeedSync`'s single worker would stall for the life of the process, every later ask, acknowledgement and settle would wait out `CALLER_BOUND`, and the watch's 5 s and 10 s bounds in `FeedListenerService`, `PhoneAsk` and `DailyNudge` would not fire either. A task that completes after the cut resumes nothing and throws nothing.
- **Tests on virtual time:** a virtual-time budget (`withTimeout` or `withTimeoutOrNull` under `runTest`) is only ever tested over fakes that suspend by `delay`, on a `CompletableDeferred`, or on a `FakeTask` that never calls its listener (Tasks 5 and 6), which suspends in our own `await()` and nowhere else, all on one test scheduler.
  - A test that holds a real DataStore asserts order and outcome only, never a virtual instant. That is a DataStore made with `PreferenceDataStoreFactory.createWithPath` and its default `Dispatchers.IO` scope, as `TasbeehStoreTest` (lines 23-25) and `NotificationCoordinatorTest` build theirs. The same goes for a test that holds a real `NotificationCoordinator`, which plans on `Dispatchers.Default`.
  - A test that opens a second DataStore on a file gives the first an explicit scope, `CoroutineScope(Dispatchers.IO + Job())`, and cancels and joins it before the second opens the file. DataStore 1.1.7's `OkioStorage` refuses two active stores on one file, with "There are multiple DataStores active for the same file", and a default scope is never cancelled.
  - When such a test also holds a `WatchFeedSync`, the sync runs on a second `TestCoroutineScheduler` that the test advances by hand, after the real work has returned. So no virtual timer can fire while real work is in flight.
- **Tests hold rules, never live figures:** `scripts/prove.sh` ends with `:shared:testAndroidHostTest || put_back` (since 13 October).
  - No test in `:shared` may compare a committed file or literal against what the real engine gives today.
  - The real engine appears only in tests that compare it with itself at run time (Task 3's `WatchFeedAgreesTest`).
  - Golden feeds are built from frozen instants.
- **Copy:** English and Arabic are exactly the spec's:
  - countdown: `in %1$s h %2$s min`, `in %1$s h`, `in %1$s min`, `in under a minute`;
  - Arabic: `بعد %1$s س %2$s د`, `بعد %1$s س`, `بعد %1$s د`, `بعد أقل من دقيقة`.

  French, Turkish, Indonesian, Urdu and Bengali are drafts given in Task 1. Each carries a "draft, to native review (plan 5)" comment.
- **Not findings:**
  - Rulings 36–38 and 42 (the Moon's option (d), its right-to-left Hijri date, TalkBack on the face, the face's reading order, which replaced 40) are plan 1's Tasks 11 and 12, closed before any task of this plan that touches their files.
  - The 12-hour spoken time has no day period ("at 4:10", Task 12's minor): the feed's 12-hour clock strings carry no marker, and the marker's place differs by language. Saying it needs a new defaulted field and a change in `wearApp/.../complication/`, which this plan does not touch, so it is plan 3's (after the plan).
  - The watch app's screens and the Tile are plan 3, and spec 1.3's tasbeeh, Qibla and app words are plans 3 and 4's (the Lead's call 6).
  - The "Install on your watch" row, the privacy policy text and every line listed under Ruling 39's "The lines that change" are plan 5's, as its ship gate. This plan changes no copy outside the watch's own words. Task 3 Step 7 only drafts the wording: Ruling 39's lines as ruled, and question 7's for his approval. Tasks 5 and 6 rewrite four code comments that quote the old promise.
  - The iPhone's own date forms on the Apple Watch's faces are plan 4's to measure.

## Review Focus

1. **A phone without Google Play services, or with Play services but no Wear OS app** (most Android phones: there, `getCapability` fails with `ApiException` 17 carrying `API_UNAVAILABLE`), **or with Play services that never answer** (updating, say). The reschedule arms exactly as before and is never slowed; nothing is shown, no update prompt appears and no feed is built; the result is `NO_WATCH`, never `FAILED`. The 17 reaches that rule as itself, never wrapped in a `RuntimeExecutionException` by an eager `Task.getResult()`. A `Task` that never completes is cut by the ask's budget (`FAILED` at 5 s), and the next ask is served. Tests: Task 5 `eachCallIsReadInItsOwnCodeSpace`, `aFailedTaskAwaitsToItsOwnException`, `aTaskThatNeverCompletesIsCutByTheBudget`, `aWatchQueryThatNeverAnswersIsCutAndTheNextAskIsServed`, `withoutPlayServicesNothingIsAskedOrShown`, `theWearApiMissingIsNoWatchNotAFailure` and `aCancelledCapabilityQueryIsAFailure`, and Ruling 54's `playServicesIsAskedThroughItsPublicApiAndNothingCanShow`, `aCapabilityQueryThatFailsOtherwiseIsAFailure`, `aSendCutByTheBudgetCallsNoFurtherNode`, `anAskReadsTheWatchesOnceAndAgainAtTheSend` and `aSyncWhoseWorkerIsGoneHoldsTheListenerNoLongerThanItsBound`; Task 6 `theWatchsAwaitIsCutByItsBound`; Task 4 `noWatchBuildsNothing`, `theLockIsFreeWhileTheWatchIsSlow`, `aWatchRequestThatThrowsNeverCostsTheReminders` and `aStrayCancellationIsAFailureAndTheWorkerGoesOn`; Task 7 checks 0a and 0b on emulators.
2. **A watch that misses a change, or runs another version than the phone:** a send queued and lost; a change made while the watch was away or reachable only through the cloud; a watch app older or newer than the phone's, as after every release, since the two update separately. A change undone before its acknowledgement, acknowledgements and handshakes that arrive late, twice or out of order, a second watch, a wiped watch and a phone restarted at any point belong here too. Every send carries the install's origin and the next number, written down before the link is called (Ruling 50); the watch stores only what moves it forward and echoes what it holds; the phone answers `UNCHANGED` only once the watch has echoed the latest delivery (on the iPhone, or once the latest context was handed over to that very watch). No ask answers `UNCHANGED` to a feed the watch does not hold, whatever order anything arrives in, except the known limits of call 1, each healed by the watch's next acknowledgement or handshake: another install's straggler stored after the watch's last echo; two Wear watches connected at the same time, where the one not confirmed can get `UNCHANGED` until its own handshake (`STARTED`, `PHONE_NEARBY` or the daily one) heals it (Ruling 53 item 3: `aSecondWatchBesideTheFirstIsSentTheFeedAtItsOwnHandshake`; a phone normally keeps one watch connected); on Android, a watch switched to while an ask builds its feed, judged as the watch read before the build (Ruling 54 item 4's one reading per ask), so that ask answers `UNCHANGED`; what tells the phone of the switch is queued behind it, and the new watch is sent the feed at its own capability change (`WATCH_APPEARED`) once the old watch has left, otherwise at its handshake (`PHONE_NEARBY`) (Ruling 54's review, round 1, ratified by Ruling 55 item 1: `aWatchSwitchedToDuringTheBuildIsSentTheFeedAtItsOwnNextAsk` and `aWatchSwitchedToDuringTheBuildHoldingAnOlderDeliveryIsSentTheFeedAtItsHandshake`); a watch whose echo the phone has not taken in (a second watch, a wiped one whose handshake was lost, whose echoes from before the wipe may still arrive, a stale acknowledgement among them overtaking a newer one, which Ruling 52 item 1 accepts as a known limit: `aStaleAckOvertakingAWipedWatchsNewerOneIsHealedByItsHandshake`); and, outside the documented platform, a restored record or a lost context before the watch's first echo. Outside those limits a stale echo costs one more send, never a wrong `UNCHANGED`: every forget sets a number aside (amendment A2, after D1 in Ruling 50's review), so once the phone has heard a watch echo nothing, no echo sent before that names the latest number. A watch the phone knows to hold the current feed is never sent it again, whatever its version and whatever the ask: a reschedule, a phrase change, a reconnect or an iPhone launch. A watch that holds nothing the phone knows of (reinstalled, new, reset) gets the feed from its own handshake. Tests: Task 4's `WatchFeedRecordTest` (the record's rules, `anEchoNumberedAboveHalfALongIsAnotherInstalls` among them), `WatchFeedMemoryTest.aStoredOriginOfAnotherFormStartsAWholeNewRecord` (Ruling 52 item 3), `WatchFeedSyncTest` (`eachSendIsWrittenDownBeforeTheLinkIsCalled`, `aNumberIsNeverUsedTwice`, `theDeliveryTravelsWithTheFeed`, `aWatchComingBackWithTheFeedItHoldsIsNotSentItAgain`, `theWatchsHandshakeWithItsCurrentFeedIsUnchangedAndRemembered`, `aHandshakeWithoutADeliveryIsSent`, `aReinstalledWatchIsHealedByItsOwnHandshake`, `aConfirmingLinkRemembersOnlyWhatTheWatchAcknowledged`, `onTheIphoneASendThatDidNotAnswerSentConfirmsNothing`, `aChangeUndoneBeforeItsAckIsSentAgain`, `aLateAckForAnOlderFeedCostsOneMoreSendNeverAWrongUnchanged`, `aSendCutOffByItsBudgetClaimsNothing`, `anEchoAboveTheLastNumberLiftsIt`, `anEchoOfTheLastNumberWithAnotherFeedIsSentTheFeed`, `aChangedWatchForgetsFirst`, `onTheIphoneAConfirmationCountsOnlyForItsWatch`; Ruling 52's `anEchoAtTheLargestNumberNeverWedgesTheSync`, `onTheIphoneAHandOverCountsForTheWatchKeyReadBeforeTheCall` and, for item 9, `anAcknowledgementIsTakenInFromTheWatchItCameFrom`), `WatchFeedNamedCasesTest` (every named counterexample of Ruling 48 and the design checks, on each link it applies to: the undo during a handshake, its relaunch, its send not through, the ack overtaking a handshake, ABA with a loss, the stand-in cases, two feeds out of order, the second watch, the wiped watch's straggler, the late acknowledgement from before a wipe (D1), the stale acknowledgement overtaking a wiped watch's newer one (its known limit), two watches one missed, a new install, the old install's straggler, the restored record, the reused number, the iPhone switched while the app was not running, and Quick Start) and `WatchFeedDeliveryModelTest` (the seeded model, properties S, L and D on both links, S judging a wipe against every echo of nothing the phone took in); watchcore `WatchLinkContractTest` (`aWatchStoresOnlyWhatMovesItForward`, `theEchoIsTheDeliveryHeldOrNothing`, `aDeliveryIsWrittenAfterItsFeedAndRemovedBefore`); Task 6 `thePhoneComingBackNearbyAlwaysShakesHands`, `theDailyNudgeAlwaysShakesHands`, `aStartWithNothingFromThePhoneAsks`, `theHandshakeCarriesTheStoredDelivery`, `aStaleOrDuplicateFeedIsDroppedUnacknowledged` and `theWatchEchoesThePhonesDeliveryWhateverItsVersion`; Task 5 `aSwitchedWatchNeverInheritsAnotherWatchsConfirmation`, `anEchoFromAWatchNotNearbyCountsOnlyWhileItIsNearby` and `aSecondWatchBesideTheFirstIsSentTheFeedAtItsOwnHandshake` (Ruling 52 item 9), Ruling 54's `aWatchSwitchedToDuringTheBuildIsSentTheFeedAtItsOwnNextAsk` and Ruling 55's `aWatchSwitchedToDuringTheBuildHoldingAnOlderDeliveryIsSentTheFeedAtItsHandshake`; Task 8 `theLaunchIsDampedAndTheWatchStateChangeIsNot` and `aConfirmationCountsOnlyForTheWatchItWentTo`, Ruling 52's `aHandshakeTheSyncCannotServeIsFalse` and `aHandshakeIsAnsweredOnceWhateverBecomesOfIt`, and Ruling 53's `aHandshakeReachesTheSyncInWatchConnectivitysOrder` (handshakes and watch-state changes served in WatchConnectivity's order) and `aHandshakeHoldsBackgroundTimeUntilItIsAnswered`; Task 7 checks 8, 9 and 10 on the emulators: check 8's reinstalled watch healed by its own handshake, check 9's second half undoing a change while the watch holds back its acknowledgement, and check 10's reconnect with nothing changed (no send) and its daily handshake through the debug nudge.
3. **The app's language is not the locale tag's:** a per-app language, a regional variant, a phone outside the seven (hi-IN, he-IL, fa-IR, mr-IN, zh-CN, az-AZ). The watch writes and says the app's own interface language, in its direction and its letter case, in words and digits the face can draw: no Azerbaijani "MAGHRİB" on an English interface. Tests: Task 1 `theWordsLocaleIsTheTagsOwnLanguageOnly`, `theWordsLocaleTagIsTheTagOrTheLanguage`, `theLanguageIsTheResourcesString`, `theWordsFollowTheLanguageNotTheFormat` and `outsideTheSevenTheDateWordsAreTheInterfaceLanguages`; Task 3 `theLanguageIsTheAppsNotTheTags`, `aPhoneOutsideTheSevenGetsTheAppsOwnFallback` and `theWatchsCaseIsTheAppsCase`; Task 7 check 4, with a per-app language set in place and with the app closed, the face heard with TalkBack.
4. **The largest feed:** 31 Arabic, Bengali or Urdu days in 12-hour time, a city name longer than any the bundle holds, and 100 phrases at the 60-character limit, in Arabic, in emoji and in control characters. The feed stays within 60,000 UTF-8 bytes, under every link's limit; the times are never cut; and an iPhone `payloadTooLarge` still delivers the times. Tests: Task 3 `theLargestFeedStaysWithinItsBudget`, `theTimesAloneLeaveRoomForThePhrases`, `theWorstCityIsLongerThanAnyTheBundleNames` and watchcore `theFeedBudgetFitsEveryLink`; Task 4 `aFeedTooLargeForTheLinkGoesAgainWithoutItsPhrases`; Task 5 `aFeedAtTheBudgetIsWithinTheMessageLimit`; Task 8 `theLargestContextFitsTheSmallestLimit` and `aContextTooLargeIsTooLarge`.
5. **Digits that differ from the tag's default, and Urdu's forms:** the S23's ar-LY drawn in Arabic-Indic (D2), an Urdu phone on a watch set to English. The feed's `digitZero` is the zero of its own strings, a pair the face was not built for is Western throughout, and an Urdu phone's ۴ ۶ ۷ are Urdu forms on every watch. Tests: Task 1 `theFourPairsTheFaceDrawsAreKept`; Task 3 `theDigitZeroIsTheFeedsOwnZero` and `aSetTheFaceWasNotBuiltForIsWesternThroughout`; Task 2 `formsComposeAsTheFeedWritesThem` and Steps 7–8 (the fonts, the validators, the pass); Task 3 Step 6 (the first Urdu feed, shot under an English and an Urdu watch); Task 7's `forms` diff on the phone emulator's ICU.

## Tasks, modules and parallel lanes

Each task's **Files** block is the full list; this table is what the cross-plan execution map (`docs/superpowers/plans/2026-10-24-watch-companion-execution-map.md`) needs to put tasks in parallel worktrees. "Shared" files are touched by more than one task.

| Task | Needs | Modules and folders it writes | Shared files it touches | Devices |
|---|---|---|---|---|
| 1 Words | plan 1 closed | `:shared` `i18n/`, `hijri/`, new `watchfeed/` (words, date line, digits, spoken words, the tags the face is measured for); `:widgetcore` `PrayerNaming.kt` | `shared/build.gradle.kts` (the `:watchcore` dependency) | an iOS simulator, for `iosSimulatorArm64Test` |
| 2 Forms and Urdu digits | 1; plan 1's Task 13 | `:shared` androidMain `PhoneForms.kt`; `tools/watchface/`; `tools/face-centre.py`, `tools/face-moon.py`; `watchface/` fonts and the generated `watchface.xml` | `shared/build.gradle.kts` (test input `phone-forms.json`); `tools/watchface/README.md` | `emulator-5558` (Step 8) |
| 3 Builder and contract | 1; plan 1's Task 12; its Steps 4 and 6 also 2 | `:shared` `watchfeed/` builder and city; `:watchcore` `WatchLinkContract.kt`, `Fixtures.kt`, `vectors/`, `WatchFeed.kt` KDoc; `tools/watchface/store.sh`; reports | `shared/build.gradle.kts` (test input `vectors/phone/`); `tools/watchface/README.md` | `emulator-5558` (Steps 4 and 6) |
| 4 Sync | 3 | `:shared` `watchfeed/` sync, link, memory, fingerprint; `notifications/` (coordinator, receivers, worker, background bridge); `di/AppContainer.kt`; `settings/SettingsKeys.kt`; `feature/tasbeeh/TasbeehViewModel.kt`; `PrayerRoutes.kt` | creates the interim `WatchLink.android.kt` and `WatchLink.ios.kt` | an iOS simulator (`scripts/test.sh`) |
| 5 Android sender | 4 | `:shared` androidMain `watchfeed/` (codes, listener, link); `androidApp` manifest and `res/` | `gradle/libs.versions.toml` (the `play-services-wearable` line); `shared/build.gradle.kts` (androidMain dependency); replaces `WatchLink.android.kt` | none |
| 6 Wear receiver | 3 (the contract only) | `:wearApp` alone: manifest, `res/`, `FeedStore.kt`, `TaqwaWearApp.kt`, `StatusActivity.kt`, new `link/`, `src/debug`, `src/release`, tests | `gradle/libs.versions.toml` (the same line as Task 5, word for word) | none |
| 7 Android pass | 2, 5, 6 | `scripts/phone-emulator.sh`, `scripts/wear-pair.sh`; `:shared` androidMain `WatchFeedDebug.kt`; `androidApp/src/debug/`; the pass report | `tools/watchface/README.md` (`store.sh`'s line) | `emulator-5558`, `emulator-5562` (and 5564 if allowed); Mohamed's one sign-in |
| 8 iPhone sender | 4 | `:shared` iosMain `watchfeed/` and the `DataStoreFactory.ios.kt` comment; `iosApp/iosApp/iOSApp.swift`; iosTest | replaces `WatchLink.ios.kt` | the iPhone 17 Pro and Series 12 simulators |

**The lanes:**
- **Lane A, in order:** 1, then 3, then 4, then 5.
- **Task 2 runs beside Task 3** once Task 1 is in: both write `shared/build.gradle.kts`'s test-input block (different lines) and one line each of `tools/watchface/README.md`, which the integrator merges.
  - Task 3's Steps 1–3 need nothing of Task 2's, so they compile and pass in a worktree without it: the tag lists and the digit-set rule that `theTimesAloneLeaveRoomForThePhrases` runs over are Task 1's `PhoneTags`, which Task 2's `PhoneForms` reads too.
  - Task 3's Steps 4 and 6 wait until Task 2 is merged, because both use `emulator-5558`, Task 3's pass compares with Task 2's, and its Urdu shots need Task 2's fonts.
- **Task 6 runs beside Tasks 4 and 5** once Task 3 is in. It touches no file of theirs. If Task 5 is not merged yet, Task 6 adds the same `play-services-wearable` catalog line, and the identical change merges cleanly.
- **Task 8 runs beside Tasks 5, 6 and 7** once Task 4 is in. It touches no Android file, and its simulators are its own.
- **Task 7 is last on Android,** after 2, 5 and 6, and alone on `emulator-5558` while it runs. No other plan's task may use 5558 or 5562 then.
- **Never in parallel:** two tasks that use `emulator-5558` (2 Step 8, 3 Steps 4 and 6, 7), and any task with plan 1's face work, which also runs on 5558 and in `watchface/`.
- **Every lane's commits** follow Global Constraints (no trailer, `git commit -F`). The Lead integrates the lanes onto `watch-companion`, and runs `scripts/test.sh` after each merge.

---

### Task 1: The phone's words for the watch

**Files:**
- Modify:
  - `shared/build.gradle.kts`: commonMain gets `implementation(project(":watchcore"))`. It is not exported (no `export(...)` in the framework block), because Swift sees only strings.
  - `shared/src/commonMain/kotlin/world/taqwa/app/i18n/PlatformFormat.kt`
  - `shared/src/androidMain/kotlin/world/taqwa/app/i18n/PlatformFormat.android.kt`
  - `shared/src/iosMain/kotlin/world/taqwa/app/i18n/PlatformFormat.ios.kt`
  - `shared/src/commonMain/kotlin/world/taqwa/app/hijri/HijriFormatter.kt`
  - `widgetcore/src/commonMain/kotlin/world/taqwa/app/i18n/PrayerNaming.kt`
- Create, in `shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/`: `WatchUiLanguage.kt`, `WatchWords.kt`, `WatchDateLine.kt`, `WatchDigits.kt`, `WatchSpokenWords.kt` and `PhoneTags.kt`.
- Test:
  - `shared/src/androidHostTest/kotlin/world/taqwa/app/i18n/AndroidPlatformFormatTest.kt` (add)
  - `shared/src/iosTest/kotlin/world/taqwa/app/i18n/IosPlatformFormatTest.kt` (add)
  - in `shared/src/commonTest/kotlin/world/taqwa/app/watchfeed/`: `WatchUiLanguageTest.kt`, `WatchWordsTest.kt`, `WatchDigitsTest.kt`, `WatchSpokenWordsTest.kt` and `PhoneTagsTest.kt`
  - `shared/src/commonTest/kotlin/world/taqwa/app/i18n/WordsLocaleTest.kt`
  - in `shared/src/iosTest/kotlin/world/taqwa/app/watchfeed/`: `WatchUiLanguageResourceTest.kt` and `WatchSpokenResourceTest.kt`
  - in `shared/src/androidHostTest/kotlin/world/taqwa/app/watchfeed/`: `WatchWordsResourcesTest.kt` (it also holds the spoken words' guard) and `WatchDateLineTest.kt`
  - `shared/src/commonTest/kotlin/world/taqwa/app/hijri/HijriFormatterLanguagesTest.kt` (add)
  - `shared/src/commonTest/kotlin/world/taqwa/app/i18n/PrayerNamingTest.kt` (add)

**Interfaces:**
- Consumes `WatchCountdownPhrases`, `WatchSpoken` and `WatchCount` from watchcore (`WatchFeed.kt`, plan 1's Task 12 added the last two), reachable once the dependency line above is in.
- Produces `internal suspend fun watchUiLanguage(resolve: suspend () -> String = { getString(Res.string.ui_language) }): UiLanguage`, which is `UiLanguage.of(resolve())`. The default reads the very resource `uiLanguage()` reads, resolved by Compose Multiplatform outside a composition. A throw passes to the caller, whose sync answers `FAILED`: the language is never guessed from the tag.
- Produces, on `PlatformFormat`, each with a default so the fakes compile:
  - `fun clockFigures(hour: Int, minute: Int): String`: the clock time without its day period, "4:10" in 12-hour and "16:10" in 24-hour. It defaults to `clockTime(hour, minute)`.
  - `fun dayPeriods(language: UiLanguage): Pair<String, String>`: the platform's own markers (Android `DateFormatSymbols.amPmStrings`, iOS `AMSymbol`/`PMSymbol`, the ones `clockTime` already uses), in the words locale. It defaults to `"AM" to "PM"`.
  - `fun shortWeekday(date: LocalDate, language: UiLanguage): String` and `fun shortMonth(date: LocalDate, language: UiLanguage): String`: CLDR's abbreviated format forms, pattern `EEE` and `MMM`, in the words locale and the Gregorian calendar that `longDate` uses. They default to English ("Sun", "Oct").
  - `fun longWeekday(date: LocalDate, language: UiLanguage): String` and `fun longMonth(date: LocalDate, language: UiLanguage): String`: the full format forms, `EEEE` and `MMMM`, the same way, for the screen reader's date. They default to English ("Sunday", "October").
  - **The words locale:** `wordsLocaleTag(languageTag(), language)` (below), as `Locale.forLanguageTag` (iOS: `NSLocale(localeIdentifier = …)`). So it is the format's own locale when the tag's own language is the interface language, otherwise the bare interface language. The format's tag only answers "can the phone's locale give these words?", never which language they are in. `clockTime` and `longDate`, the app's own, do not change.
- Produces, in `PlatformFormat.kt` (commonMain), `internal fun isTagsOwnLanguage(languageTag: String, language: UiLanguage): Boolean`. It takes the tag's primary subtag (before the first `-` or `_`), lowercased, reads `in` as `id` (Java's old spelling), and compares it with `language.code`. It never calls `UiLanguage.of(tag)`, which answers ENGLISH for any language outside the seven (`UiLanguage.kt:36-43`): he-IL, hi-IN, fa-IR, mr-IN and zh-CN with ENGLISH would then pick the phone's own Hebrew, Hindi, Persian, Marathi or Chinese words, in scripts the face's fonts lack.
- Produces, beside it, `internal fun wordsLocaleTag(languageTag: String, language: UiLanguage): String`: `languageTag` with every `_` written `-` when `isTagsOwnLanguage` holds, otherwise `language.code`. It is the words locale above and the feed's `ui.language` (Task 3), from which the watch takes letter case alone.
- Produces `HijriFormatter.formatDayMonth(date: HijriDate, format: PlatformFormat = EnglishPlatformFormat, language: UiLanguage = UiLanguage.of(format.languageTag())): String`: `format`'s output without " <year>", from the same day digits and month name. `HijriFormatter.format` gains the same last parameter with the same default, so every existing caller is unchanged.
- Produces `PrayerNaming.nextInPattern(languageTag: String): String`: the `COUNTDOWN` entry with `{prayer}` written as `%1$s`. The builder passes `language.code`.
- Produces `internal object WatchWords`, in plain Kotlin like `PrayerNaming`, because a boot receiver has no composition:
  - `fun countdown(language: UiLanguage): WatchCountdownPhrases`;
  - `fun jumuah(language: UiLanguage): String`: each language's `prayer_jumuah`, kept in step by the test below.

  The five drafts, in the order hoursMinutes, hours, minutes, underAMinute. The French, Indonesian and Bengali under-a-minute forms are the Lead's call 7's shorter ones:
  - French: `dans %1$s h %2$s min`, `dans %1$s h`, `dans %1$s min`, `d’ici une minute`.
  - Turkish: `%1$s sa %2$s dk sonra`, `%1$s sa sonra`, `%1$s dk sonra`, `bir dakika içinde`.
  - Indonesian: `%1$s j %2$s mnt lagi`, `%1$s j lagi`, `%1$s mnt lagi`, `kurang dari semenit`.
  - Urdu: `%1$s گھنٹے %2$s منٹ میں`, `%1$s گھنٹے میں`, `%1$s منٹ میں`, `ایک منٹ سے کم میں`.
  - Bengali: `%1$s ঘ %2$s মি পরে`, `%1$s ঘ পরে`, `%1$s মি পরে`, `এক মিনিটের কম সময়ে`.
- Produces `internal data class WatchDateLine(val gregorian: String, val weekday: String, val dayNumber: String, val spoken: String)`, with `companion object { fun of(date: LocalDate, format: PlatformFormat, language: UiLanguage): WatchDateLine }`:
  - the weekday and the month each lose one trailing `.`, then go through `uppercaseIn(language)`;
  - `dayNumber` is `format.localizedDigits(date.day)`;
  - `gregorian` is `"$weekday $dayNumber $month"`, in logical order in every language, as the samples are;
  - `spoken` is `"${format.longWeekday(date, language)} $dayNumber ${format.longMonth(date, language)}"`, in the same order, with no dot taken off and no change of case: the feed's `gregorianSpoken` (Task 3).
- Produces, in `WatchSpokenWords.kt`, the screen reader's words for `ui.spoken`, read from the phone's own strings in the same resource environment as `watchUiLanguage()`:
  - `internal interface SpokenStrings { suspend fun countdown(): String; suspend fun countdownSoon(): String; suspend fun hoursMinutes(): String; suspend fun hours(n: Int): String; suspend fun minutes(n: Int): String }`;
  - `internal object ResourceSpokenStrings : SpokenStrings`: `getString(Res.string.prayer_a11y_countdown, "%1$s", "%2$s", "%3$s")`, `getString(Res.string.prayer_a11y_countdown_soon, "%1$s", "%2$s")` and `getString(Res.string.duration_hours_minutes, "%1$s", "%2$s")`, so each template comes back with its places whether or not a bare `getString` formats; `getPluralString(Res.plurals.a11y_hours, n, "%1$s")` and the same for `a11y_minutes`, so the plural is chosen by `n` and the number's place is kept;
  - `internal suspend fun countRuns(phraseFor: suspend (Int) -> String): List<WatchCount>`: `phraseFor` asked for every `n` from 0 to 99, and one `WatchCount(upTo = the run's last n, phrase)` for each run of equal phrases, in order. A phrase may come back in a later run (Arabic's دقيقة at 1 and at 99);
  - `internal suspend fun watchSpoken(strings: SpokenStrings = ResourceSpokenStrings): WatchSpoken`: the three templates, then `countRuns(strings::hours)` and `countRuns(strings::minutes)`. A throw passes to the caller, whose sync answers `FAILED`.
- Produces `internal class WatchDigits private constructor(phone: List<String>, language: UiLanguage)`, the feed's digit rule (Global Constraints, "Digits"), with `companion object { val FACE_PAIRS: Map<UiLanguage, Set<Char>>; fun of(format: PlatformFormat, language: UiLanguage): WatchDigits }`:
  - `phone` is `format.localizedDigits(0..9)`, each one a string, so a supplementary-plane digit is whole;
  - `FACE_PAIRS`: English, French, Turkish and Indonesian → `{'0'}`; Arabic → `{'0', '٠'}` (U+0660); Urdu → `{'0', '۰'}` (U+06F0); Bengali → `{'0', '০'}` (U+09E6);
  - `val zero: Char` is the phone's zero when it is in `FACE_PAIRS[language]`, else `'0'`;
  - `fun write(text: String): String` is `text` itself when the phone's zero is kept. Otherwise each of the phone's ten digits in `text` becomes its Western digit, and nothing else changes.
- Produces, in `PhoneTags.kt`, `internal object PhoneTags`: the tags the face is measured for, and which digit sets each can carry. It lives here, in commonMain, so that Task 2's `PhoneForms` and Task 3's size test both read it and neither needs the other (the lanes). No release code calls it.
  - `val TAGS: List<String>`: en, en-GB, en-US, en-ZA, ar-SA, ar-EG, ar-LY, ar-MA, ar-TN, ar-DZ, ar-IQ, ar-SY, ar-JO, ar-LB, ar-AE, fr-FR, fr-MA, fr-DZ, fr-CA, tr-TR, id-ID, ur-PK, ur-IN, bn-BD, bn-IN.
  - `val FALLBACK_TAGS: List<String>`, phones outside the seven languages: hi-IN, he-IL, fa-IR, mr-IN, zh-CN.
  - `fun numberingTags(tag: String): List<String>`: the tags a format is built on, one for each digit set the feed may carry for `tag`. First `tag` itself, the runtime's own digits. Then, when `UiLanguage.of(tag)` is ARABIC, `"$tag-u-nu-latn"` and `"$tag-u-nu-arab"`; when it is URDU, `"$tag-u-nu-latn"` and `"$tag-u-nu-arabext"`. Nothing more for any other tag.
  - `internal data class PhoneDigitSet(val formatTag: String, val digits: String)`, and `fun digitSets(tag: String, formatFor: (String) -> PlatformFormat): List<PhoneDigitSet>`. For each of `numberingTags(tag)` in order, the format `formatFor` builds on it, and its ten digits as the feed writes them: `WatchDigits.of(format, UiLanguage.of(tag)).write` of `format.localizedDigits(0..9)`. A set already listed is dropped. So:
    - the first set is the runtime's own when (language, its zero) is a face pair, else Western;
    - every Arabic tag also carries the other of Western and Arabic-Indic, and every Urdu tag the other of Western and Extended Arabic-Indic;
    - every fallback tag carries Western alone: fa-IR's and mr-IN's own digits are not carried (the Lead's call 3).
  - The rule runs on whatever runtime builds the formats: the JVM's CLDR in the host tests, the phone's ICU in Task 7's harness.

- [ ] **Step 1: Write the failing tests:**
  - Android (`AndroidPlatformFormat(locale, clockFormat)`):
    - `clockFiguresLeaveTheDayPeriodOut`: `Locale.US` in 12-hour gives `clockFigures(16, 10)` = "4:10" and `clockFigures(0, 5)` = "12:05"; in 24-hour, `clockFigures(16, 10)` = "16:10".
    - `arabicIndicFiguresInTwelveHours`: ar-SA in 12-hour gives `clockFigures(16, 10)` = "٤:١٠".
    - `dayPeriodsAreTheLocalesOwn`: en-US with ENGLISH gives ("AM", "PM"); ar-SA with ARABIC gives ("ص", "م").
    - `shortWeekdayAndMonthAreCldrsAbbreviations`: on 2026-10-18, en-US/ENGLISH gives "Sun"/"Oct", fr-FR/FRENCH "dim."/"oct.", tr-TR/TURKISH "Paz"/"Eki", and ar-SA/ARABIC "الأحد"/"أكتوبر".
    - `longWeekdayAndMonthAreCldrsFullForms`: on 2026-10-18, en-US/ENGLISH gives "Sunday"/"October", fr-FR/FRENCH "dimanche"/"octobre", tr-TR/TURKISH "Pazar"/"Ekim", ar-SA/ARABIC "الأحد"/"أكتوبر", and he-IL/ENGLISH "Sunday"/"October".
    - `outsideTheSevenTheDateWordsAreTheInterfaceLanguages`: hi-IN, he-IL, fa-IR, mr-IN, zh-CN and az-AZ, each with ENGLISH, give "Sun"/"Oct", "Sunday"/"October" and ("AM", "PM"). Their `clockFigures` keep their own digits (Task 3 rewrites them), and `longDate` and `clockTime` are unchanged.
    - `theWordsFollowTheLanguageNotTheFormat`: en-US with ARABIC gives "الأحد"/"أكتوبر" and ("ص", "م"); ar-SA with ENGLISH gives "Sun"/"Oct" and ("AM", "PM"). `localizedDigits` stays each format's own.
  - iOS: the same seven tests for `en_US`, `ar_SA` and `he_IL`.
  - `WordsLocaleTest.theWordsLocaleIsTheTagsOwnLanguageOnly` (common), `isTagsOwnLanguage(tag, language)`:
    - true for ("en-US", ENGLISH), ("en", ENGLISH), ("ar-SA", ARABIC), ("ar_SA", ARABIC), ("id-ID", INDONESIAN), ("in-ID", INDONESIAN), ("ur-PK-u-nu-arabext", URDU) and ("bn-BD", BENGALI);
    - false for ("he-IL", ENGLISH), ("hi-IN", ENGLISH), ("fa-IR", ENGLISH), ("mr-IN", ENGLISH), ("zh-CN", ENGLISH), ("zh-Hans-CN", ENGLISH), ("he_IL", ENGLISH), ("az-AZ", ENGLISH), ("ar-SA", ENGLISH) and ("en-US", ARABIC).
  - `WordsLocaleTest.theWordsLocaleTagIsTheTagOrTheLanguage`: `wordsLocaleTag` gives "tr-TR" for ("tr-TR", TURKISH) and for ("tr_TR", TURKISH), "ar-SA" for ("ar-SA", ARABIC), "in-ID" for ("in-ID", INDONESIAN), and "en" for ("az-AZ", ENGLISH), ("he-IL", ENGLISH) and ("ar-SA", ENGLISH), and "ar" for ("en-US", ARABIC).
  - `WatchSpokenWordsTest` (common, fake `SpokenStrings`):
    - `aCountIsKeptAsRunsOfOnePhrase`: English's rule (1 is "%1$s hour", every other number "%1$s hours") gives `[(0, "%1$s hours"), (1, "%1$s hour"), (99, "%1$s hours")]`. Arabic's CLDR rule (0 zero, 1 one, 2 two, 3–10 few, 11–99 many) over `values-ar`'s `a11y_minutes` items (zero, one, many and other "%1$s دقيقة", two "%1$s دقيقتين", few "%1$s دقائق") gives `[(1, "%1$s دقيقة"), (2, "%1$s دقيقتين"), (10, "%1$s دقائق"), (99, "%1$s دقيقة")]`, the list in watchcore's `WatchCount` KDoc and the ar-SA sample. One phrase for every number gives `[(99, it)]`.
    - `theSentencesAreTheTemplates`: the fake's three templates are the fields, unchanged.
    - `aReadThatThrowsIsNotGuessed`: a fake whose `minutes(n)` throws makes `watchSpoken` throw it.
    - `theWatchSaysWhatThePhoneSays`: a feed whose `ui.spoken` is the English fake's gives `WatchRules.spokenCountdown(feed, "Asr", "16:10", 4_320)` = "Asr in 1 hour 12 minutes, at 16:10", and at 59 seconds "Asr in under a minute, at 16:10", the Prayer screen's sentences.
  - `WatchSpokenResourceTest.theDefaultReadsThePhonesOwnStrings` (iOS simulator, the English environment the other iOS tests run in): `watchSpoken()` equals the English sample's `ui.spoken`: "%1$s in %2$s, at %3$s", "%1$s in under a minute, at %2$s", "%1$s %2$s", and the two three-entry lists above for "hour(s)" and "minute(s)".
  - `WatchUiLanguageTest` (common): `theLanguageIsTheResourcesString`: `resolve` answering "ar", "ur", "id" and "en" gives ARABIC, URDU, INDONESIAN and ENGLISH; a `resolve` that throws `IllegalStateException` makes `watchUiLanguage` throw it, never answer English.
  - `WatchUiLanguageResourceTest` (iOS simulator): `theDefaultReadsTheUiLanguageString`: `watchUiLanguage()` equals `UiLanguage.of(getString(Res.string.ui_language))`.
  - `WatchWordsTest`:
    - `englishCountdownIsTheSpecs` and `arabicCountdownIsTheSpecs`: the exact strings in Global Constraints.
    - `everyLanguageHasEveryFormWithItsPlaces`: for each `UiLanguage`:
      - `hoursMinutes` holds `%1$s` before `%2$s`;
      - `hours` and `minutes` hold `%1$s` and no `%2$s`;
      - `underAMinute` holds neither;
      - none is blank.
  - `WatchDigitsTest` (fake formats):
    - `theFourPairsTheFaceDrawsAreKept`: for each pair in `FACE_PAIRS`, a format writing that set with that language gives that zero, and `write` returns its text unchanged.
    - `aSetTheFaceWasNotBuiltForTheLanguageIsWrittenWestern`: Devanagari with ENGLISH (mr-IN), Extended Arabic-Indic with ENGLISH (fa-IR), Arabic-Indic with ENGLISH and Extended Arabic-Indic with ARABIC each give `'0'`, and `write` turns each one's "16:47" into the Western "16:47", and leaves "SUN" and ":" alone.
    - `aSupplementarySetIsWrittenWestern`: Chakma digits (U+11136–U+1113F, two UTF-16 units each) with ENGLISH give `'0'`, and `write` turns their "16:47" into Western.
  - `PhoneTagsTest` (common; `formatFor` a fake whose format writes, for each tag, the digit set the case names, like `WatchDigitsTest`'s fakes):
    - `everyTagIsItsOwnLanguageAndEveryFallbackIsOutsideTheSeven`: for every tag of `TAGS`, `isTagsOwnLanguage(tag, UiLanguage.of(tag))` holds; for every tag of `FALLBACK_TAGS`, `UiLanguage.of(tag)` is ENGLISH and `isTagsOwnLanguage(tag, ENGLISH)` is false; no tag is in both lists, and each of the seven `UiLanguage`s has a tag in `TAGS`.
    - `theNumberingTagsAreTheTagThenItsOtherSets`: `numberingTags` gives ["ar-LY", "ar-LY-u-nu-latn", "ar-LY-u-nu-arab"] for "ar-LY", ["ur-IN", "ur-IN-u-nu-latn", "ur-IN-u-nu-arabext"] for "ur-IN", and the tag alone for "bn-BD", "en-GB" and "fa-IR".
    - `aNumberingTagKeepsItsTagsLanguage`: for every tag of both lists and every `t` of its `numberingTags`, `UiLanguage.of(t)` equals `UiLanguage.of(tag)`, and `isTagsOwnLanguage(t, UiLanguage.of(tag))` equals `isTagsOwnLanguage(tag, UiLanguage.of(tag))`.
    - `theDigitSetsAreTheFeedsOwnInOrder`, as (`formatTag`, `digits`):
      - "ar-SA" writing Arabic-Indic, with its `-u-nu-latn` Western and `-u-nu-arab` Arabic-Indic: [("ar-SA", "٠١٢٣٤٥٦٧٨٩"), ("ar-SA-u-nu-latn", "0123456789")];
      - "ar-LY" writing Western: [("ar-LY", "0123456789"), ("ar-LY-u-nu-arab", "٠١٢٣٤٥٦٧٨٩")];
      - "ur-IN" writing Extended Arabic-Indic: [("ur-IN", "۰۱۲۳۴۵۶۷۸۹"), ("ur-IN-u-nu-latn", "0123456789")];
      - "bn-BD" writing Bengali: [("bn-BD", "০১২৩৪৫৬৭৮৯")];
      - "fa-IR" writing Extended Arabic-Indic, and "mr-IN" writing Devanagari: [(tag, "0123456789")].
  - `WatchWordsResourcesTest`:
    - `jumuahIsThePrayerScreensWord`: `prayer_jumuah` in `src/commonMain/composeResources/values{,-ar,-fr,-tr,-id,-in,-ur,-bn}/strings.xml` equals `WatchWords.jumuah` for each language.
    - `everyLanguageHasTheSpokenWords`: in each of the same eight directories, `prayer_a11y_countdown` holds `%1$s`, `%2$s` and `%3$s`; `prayer_a11y_countdown_soon` and `duration_hours_minutes` hold `%1$s` and `%2$s`; and `a11y_hours` and `a11y_minutes` each have an `other` item holding `%1$s`. So no language's spoken words fall back to English.
    - `everyNamedTagResolvesAsUiLanguageOfIt`: for every tag of `PhoneTags.TAGS` and `PhoneTags.FALLBACK_TAGS` (this task's), and every one of its `numberingTags`, the `ui_language` of the directory Compose Multiplatform's qualifier rule picks for it equals `UiLanguage.of(tag).code`. That directory is `values-<language>` when there is one (Indonesian's twins `values-id` and `values-in` both hold "id"), and `values` otherwise. So Task 2's forms and Task 3's size test, which build each tag with `UiLanguage.of(tag)`, use the language the app shows on such a phone.
  - `WatchDateLineTest` (host, real formats), on 2026-10-18:
    - en/ENGLISH: `("SUN 18 OCT", "SUN", "18", "Sunday 18 October")`.
    - fr-FR/FRENCH: "DIM 18 OCT", spoken "dimanche 18 octobre".
    - tr-TR/TURKISH: "PAZ 18 EKİ", with the dotted capital, spoken "Pazar 18 Ekim".
    - ar-SA/ARABIC: `("الأحد ١٨ أكتوبر", "الأحد", "١٨", "الأحد ١٨ أكتوبر")`.
    - he-IL, hi-IN and az-AZ with ENGLISH: as en.
  - `formatDayMonthDropsTheYear`: `HijriDate(1448, 5, 6)` gives "6 Jumada al-Ula" in English. In Arabic with an Arabic-Indic fake format it gives "٦ جمادى الأولى". An en-US format with ARABIC gives "6 جمادى الأولى".
  - `nextInPatternKeepsThePlaceForTheName`: en gives "%1$s in"; ar gives "متبقٍّ على %1$s".
- [ ] **Step 2:** Run `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test`. Expected: FAIL, unresolved `clockFigures`.
- [ ] **Step 3:** Add the dependency line, then implement the interfaces above. The Android forms use `DateTimeFormatter.ofPattern("EEE"/"MMM", wordsLocale)` with `DecimalStyle.of(wordsLocale)`, and `dayPeriods` reads `DateFormatSymbols.getInstance(wordsLocale)`. iOS uses an `NSDateFormatter` on the words locale with the Gregorian calendar, as `longDateFormatter` does, and `dateFormat` set to `EEE` or `MMM`.
- [ ] **Step 4:** Run `scripts/test.sh`, then `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`. Expected: PASS, and `grep -oE '[A-Za-z]*(WatchCountdownPhrases|WatchFeed|WatchUi|WatchSpoken|WatchCount|WatchDay|WatchPlace|WatchLinkContract)[A-Za-z]*' shared/build/bin/iosSimulatorArm64/debugFramework/shared.framework/Headers/shared.h | grep -vE '(WatchFeedBridge|WatchSessionHook)$'` prints nothing. The second grep lets Task 8's two public bridge names through, which the first would otherwise catch.
- [ ] **Step 5: Commit** with the message "Watch feed: the phone's words for the watch in the app's own language, its clock without the day period, the face's date line, the screen reader's words, the digits the face can draw, and the tags it is measured for".

### Task 2: The phone's date words and countdowns, measured on the face, and Urdu's digits

The face's tools measure, today:
- the Hijri date, from the phone's own `HijriMonthNames` (`hijri-fit.py` on Arch, `moon-fit.py` on the Moon);
- the Moon's prayer line (`moon-fit.py`) and the wearer's slots (`slot-fit.py`);
- the Moon's weekday from a hand table (`window-fit.py`);
- the Moon's countdown, from the committed samples' English and Arabic alone (`moon-fit.py`, until `phone-forms.json` exists).

Three of the phone's forms reach the face unmeasured: the Gregorian date line on Arch, Ring and Sun; the countdown on Arch, Ring and Sun; and the countdown in French, Turkish, Indonesian, Urdu and Bengali on the Moon (ledger L304 and L333, and Ruling 34's Bengali Thursday at the 0.5 floor). Task 3 builds the feed from these forms, so they are measured first. Task 11 has re-laid the Moon's lower half and its Hijri curve for option (d); `moon-fit.py` is run as Task 11 left it, in both layouts it measures.

The digits are measured in both sets for every Arabic-script tag. A phone may draw either set, whatever the tag's default: the S23 draws ar-LY in Arabic-Indic, while CLDR and the JDK give it Western digits (`WidgetDigitChoice.kt`). The JVM's CLDR 42 can also differ from a phone's newer ICU, so Task 7 writes the same file on the phone emulator and compares the two.

Steps 6–9 are the face fix (the Lead's call 2): Urdu's ۴ ۶ ۷ in all three Noto weights, under new font names. The runtime keeps a font by its resource name, through a new APK and an uninstall, so a changed font takes a new name (`subset-noto.py`'s own docstring). Task 3 Step 6 shoots it with the first Urdu feed.

**Files:**
- Create:
  - `shared/src/androidMain/kotlin/world/taqwa/app/watchfeed/PhoneForms.kt`
  - `shared/src/androidHostTest/kotlin/world/taqwa/app/watchfeed/FaceFormsTest.kt`
  - `tools/watchface/phone-forms.json` (generated)
  - `tools/watchface/date-fit.py`
  - `docs/superpowers/reports/2026-10-23-phone-forms-fit.md`
  - `watchface/src/main/res/font/noto_sans_arabic_subset_v2_{light,semibold,bold}.ttf` (generated by `subset-noto.sh`)
- Delete: `watchface/src/main/res/font/noto_sans_arabic_subset_{light,semibold,bold}.ttf`.
- Modify:
  - `tools/watchface/window-fit.py`: `WEEKDAYS` and `PHONES` are read from the forms file, so the hand table goes.
  - `tools/watchface/README.md`: one line for `date-fit.py`, and the new font names.
  - `shared/build.gradle.kts`: `phone-forms.json` is a test input, rewritten under `-Ptaqwa.updateVectors`, in the pattern of `watchcore/build.gradle.kts`.
  - `tools/watchface/subset-noto.py`: the Urdu forms and the new names, with its docstring.
  - Every file that names the old fonts, as `grep -rlE 'noto_sans_arabic_subset_(light|semibold|bold)' --exclude-dir=build --exclude-dir=.git . | grep -v '^./docs/superpowers'` lists them. At 17d57995 those are `tools/face-centre.py`, `tools/face-moon.py`, `tools/watchface/{moon-fit,hijri-fit,window-fit,ref}.py`, `tools/watchface/get-noto.sh`, `subset-noto.py`, and the generated `watchface/src/main/res/raw/watchface.xml`. Also `subset-noto.sh`'s comment (`noto_sans_arabic_subset_*`). The generated XML is rewritten by its generators, never by hand.

**Interfaces:**
- Consumes: Task 1's `WatchDateLine`, `WatchWords`, `WatchDigits`, `PlatformFormat` and `PhoneTags` (`TAGS`, `FALLBACK_TAGS`, `digitSets`).
- Produces `internal object PhoneForms` with `fun json(): String`. It holds the forms the feed writes, through `AndroidPlatformFormat` and `WatchDigits`, for every tag of `PhoneTags.TAGS` and `PhoneTags.FALLBACK_TAGS`, each with `UiLanguage.of(tag)` as its interface language (Task 1's `everyNamedTagResolvesAsUiLanguageOfIt`). It declares no tag list or digit rule of its own. It runs on whatever runtime runs it: the JVM in this test, the phone's ICU in Task 7's harness. Release code never calls it, so R8 drops it.
- Produces `phone-forms.json`: `{"locales": {"<tag>": {"language": "ar", "rtl": true, "digitSets": ["0123456789", "٠١٢٣٤٥٦٧٨٩"], "weekdays": [7, Sunday first], "months": [12], "countdown": {…the four phrases…}}}}`. `language` is the interface language's code and `rtl` its `rtl`.
  - `digitSets` is `PhoneTags.digitSets(tag) { AndroidPlatformFormat(Locale.forLanguageTag(it), …) }`'s `digits`, in order: the runtime's own set first when it is a face pair (else Western), the other Arabic or Urdu set after it, and Western alone for a fallback tag (Task 1).
- `date-fit.py` and `window-fit.py` take `forms=<path>`, as `moon-fit.py` already does, defaulting to `tools/watchface/phone-forms.json`, so Task 7 can run all three on the device's file.
- Produces, in `subset-noto.py`, the digit map of each weight:
  - in every weight, U+06F4, U+06F6 and U+06F7 map to the glyph that the `arab` script's `URD` LangSys `locl` gives their default glyph. Checked on the committed SemiBold at 17d57995: that `locl` maps `uni06F4` to `glyph00143` (the Urdu ۴), `uni06F6` to `uni0666` (٦'s glyph) and `uni06F7` to `glyph00144` (the Urdu ۷);
  - in the Light weight, those three then map to that glyph's `pnum` form, and every other digit (U+0660–U+0669, U+06F0–U+06F9) to its own `pnum` form, as now. `pnum` holds `glyph00143 → glyph00145`, `glyph00144 → glyph00146` and `uni0666 → glyph00133`. (These are the names the committed subset gives the glyphs; the script works from the upstream font's own `locl` and `pnum` lookups, never from names);
  - the script stops, naming the code point and the weight, when one of the three has no `URD` `locl` form, or, in the Light weight, no `pnum` form;
  - every other code point keeps its glyph, and no layout feature changes.
- Produces the files `noto_sans_arabic_subset_v2_light.ttf`, `_semibold.ttf` and `_bold.ttf`, whose family names in `face-centre.py`'s family map are `noto_sans_arabic_subset_v2_light`, `…_semibold` and `…_bold`.

- [ ] **Step 1: Write the failing tests:**
  - `phoneFormsAreUpToDate`: the file equals `PhoneForms.json()`. It rewrites the file under `-Ptaqwa.updateVectors`.
  - `formsComposeAsTheFeedWritesThem`: for every tag, fallback tags included, each of its digit sets (the format built on that `PhoneDigitSet`'s `formatTag`), and every date of 2027: `WatchDigits.of(format, language).write(WatchDateLine.of(date, format, language).gregorian)` is the file's weekday, then the day in that set, then its month, separated by single spaces.
  - `theFallbackLocalesAreMeasuredAsTheFeedWritesThem`: hi-IN, he-IL, fa-IR, mr-IN and zh-CN have `language` "en", `rtl` false, en's weekdays, months and countdown, and `digitSets` `["0123456789"]`.
- [ ] **Step 2:** Run `./gradlew :shared:testAndroidHostTest --tests '*FaceFormsTest*'`. Expected: FAIL, because the file is missing.
- [ ] **Step 3:** Implement `PhoneForms`, and generate the file with `-Ptaqwa.updateVectors`. Write `date-fit.py` in `hijri-fit.py`'s manner. It reuses `hijri-fit.py`'s arch curve and clearance, reads the boxes, sizes and fonts from `tools/face-centre.py`, and takes `fonts=` for the emulator's Bengali font, without which it fails. It checks, in every digit set of every tag:
  - every composed Gregorian line (7 weekdays × 12 months × days 1–31): on Arch at least 7 px clear of the lit stroke and inside its box; on Ring and Sun inside the 360 px line box, never cut by the ellipsis;
  - every countdown form, filled with 23 and 59 (and with 99 and 59, the cap): inside the countdown box on Arch, Ring and Sun.

  Point `window-fit.py` at the file, and give it `forms=`.
- [ ] **Step 4:** Copy the emulator's Bengali font into a scratch dir (as `hijri-fit.py`'s docstring says), then run each with `fonts=<dir>`:
  - `python3 -I tools/watchface/date-fit.py`: the Gregorian lines and the countdowns on Arch, Ring and Sun;
  - `python3 -I tools/watchface/window-fit.py`: the Moon's weekdays and day numbers from the phone's forms;
  - `python3 -I tools/watchface/moon-fit.py`: the Moon's countdown in all seven languages and every digit set the file carries (it reads `phone-forms.json` by default once it exists), its Hijri date and its prayer line, in each layout Task 11 left it measuring;
  - `python3 -I tools/watchface/hijri-fit.py`, unchanged, as a guard that nothing Task 11 or this task did moved Arch's Hijri line.

  Expected: all four pass. Bengali's Thursday is measured against Ruling 34's floor with the phone's own short form. The French, Indonesian and Bengali under-a-minute forms are the Lead's call 7's shorter drafts; the report gives their degrees beside Task 11's figures for the first drafts (139.2°, 133.6° and 121.2° of 121.1°). `gregorianSpoken` and `ui.spoken` are never drawn, so no tool measures them.
  - **If any form fails** (likely candidates: `تشرين الثاني` on Arch, Bengali বৃহস্পতি in the Moon's window, the Urdu countdowns, an Arabic-Indic line wider than its Western twin): **stop**. Record every failing form, its digit set, its tool and its margin in the report, and hand it to the Lead. The remedy is a face change or a shorter form, which is a ruling and not this plan's call.
- [ ] **Step 5: Commit** `PhoneForms`, the test, the JSON, the tools and the report, with the message "Watch feed: the phone's date words and countdowns in seven languages and both digit sets, measured on the face".
- [ ] **Step 6: The fonts as they are.** Run `tools/watchface/get-noto.sh <empty scratch dir>` (Google's files, as in plan 1), then `tools/watchface/subset-noto.sh <that dir>`. Expected: `git status --short watchface/src/main/res/font` shows nothing, so the change starts from the committed bytes. Then, with the pinned fontTools (`~/.cache/taqwa-watchface-tools/fonttools-4.55.0/bin/python -I`), print for each upstream weight the `URD` `locl` forms of U+06F4, U+06F6 and U+06F7, and for the Light their `pnum` forms, and put them in the report.
- [ ] **Step 7: The Urdu forms.** Change `subset-noto.py` as in Interfaces, and write the three weights under their `_v2_` names. Delete the three old files. Rename every reference the Files grep lists, then run `python3 tools/face-centre.py` and `tools/face-moon.py`, as their headers say, and each generator's `--check`. Then check:
  - the Files grep finds nothing;
  - with the pinned fontTools, in each new weight, the cmap's glyphs for U+06F4, U+06F6 and U+06F7 have the outlines of the upstream `locl_URD(…)` glyphs (`pnum(locl_URD(…))` in the Light);
  - every other code point's glyph has the outline it had in the old file of the same weight.
- [ ] **Step 8: The face, signed with the test key.** Run `./gradlew :watchface:assembleDebug :watchface:assembleRelease :watchface:lint`, then `tools/watchface/validate.sh`: wff-validator, and the Push validator on the debug face and on the release's scratch copy signed with `~/.android/taqwa-face-debug.keystore`. Every check passes. Then run the CI face step locally: the generators' `--check`, and no `classes.dex` in either APK.
  - Install on `emulator-5558` (`tools/watchface/install.sh`) and run `tools/watchface/pass.sh <scratch dir>`. Every shot equals the committed pass in 0 px, the Moon's seconds hand and the emulator's step count aside: no committed shot draws an Extended Arabic-Indic digit, and every other glyph is unchanged. Any other difference stops the task, for the Lead.
  - Rerun Step 4's four tools on the new fonts. Expected: the same results, since only three glyphs that no form measured in Western or Arabic-Indic digits changed.
  - The Urdu shots wait for Task 3, whose builder writes the first Urdu feed.
- [ ] **Step 9: Commit** the fonts, the script, the generated `watchface.xml`, the tools and the report's new section (the font check's output, the validators' lines, the pass), with the message "Face: Urdu's ۴ ۶ ۷ in Urdu forms in all three Noto weights, under new font names".

### Task 3: The feed builder and the link's contract

**Files:**
- Create:
  - `shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/WatchFeedBuilder.kt`
  - `shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/WatchCityName.kt`
  - `watchcore/src/commonMain/kotlin/world/taqwa/app/watch/WatchLinkContract.kt`
  - `docs/superpowers/reports/2026-10-23-phone-feed-builder.md`, with the Urdu shots of Step 6 in `docs/superpowers/reports/2026-10-23-phone-feed-builder/`
  - `docs/superpowers/reports/2026-10-23-watch-privacy-wording.md` (Step 7)
  - `tools/watchface/store.sh`: stores any feed file on `emulator-5558` and has the face bind again, as `arabic.sh`'s last lines do (the file written as the watch app's `files/feed.json` with `run-as`, the app stopped, the face's debug APK installed again). It also removes `files/feed.json.delivery` (`rm -f`), so that a feed stored by hand never carries the delivery of the phone feed it replaced (Task 6). It sources `lib.sh` and calls `need_emulator`. One line in `tools/watchface/README.md`.
- Modify:
  - `shared/build.gradle.kts`: `watchcore/vectors/phone/` becomes a test input. It is rewritten under `-Ptaqwa.updateVectors`, and its `times/` tables only under `-Ptaqwa.recordTimes`, passed to the test the same way.
  - `watchcore/src/commonTest/kotlin/world/taqwa/app/watch/Fixtures.kt`: every place and word where the phone writes otherwise. Known in advance, under Ruling 39:
    - Cape Town's `qiblaBearing` becomes 23.0 and its `declination` -25.0; Tromsø's bearing becomes 154.0 and its declination 8.0;
    - Cape Town's `qiblaKm` becomes "6,600" in Western digits and "٦٬٦٠٠" (U+066C) for ar-SA, `DigitGrouping`'s forms; Tromsø's becomes "5,500";
    - the cities stay ("Cape Town", "كيب تاون", "Tromsø"), unless the sample test below finds that the bundle names one otherwise.

    `WatchFeedJsonTest`'s full-precision literals stay, because they test the codec.
  - `watchcore/vectors/*.json`: regenerated from the fixtures.
  - `watchcore/src/commonMain/kotlin/world/taqwa/app/watch/WatchFeed.kt`: two KDocs only.
    - `WatchPlace`: what the place holds under Ruling 39, instead of "no finer than the city": the city as the Prayer screen names it, the bearing to the phone's whole degree, the declination to a whole degree, the distance to two significant figures, and never a coordinate.
    - `WatchUi.language`: the words locale's tag (`wordsLocaleTag`), instead of "The phone's language tag", and that the watch takes letter case from it and nothing else.
- Test:
  - `watchcore/src/commonTest/kotlin/world/taqwa/app/watch/WatchLinkContractTest.kt`
  - `shared/src/commonTest/kotlin/world/taqwa/app/watchfeed/WatchFeedBuilderTest.kt`
  - `shared/src/commonTest/kotlin/world/taqwa/app/watchfeed/WatchCityNameTest.kt`
  - `shared/src/androidHostTest/kotlin/world/taqwa/app/watchfeed/WatchFeedSampleTest.kt`
  - `shared/src/androidHostTest/kotlin/world/taqwa/app/watchfeed/BundledCities.kt` (test helper: `fun repository(): CityRepository` over `src/commonMain/composeResources/files/cities.csv` and `city-names-<language>.csv`, read from the module directory as `WatchWordsResourcesTest` reads `strings.xml`, and `fun allNames(): List<String>`, every name in those files)
  - `shared/src/androidHostTest/kotlin/world/taqwa/app/watchfeed/BundledSpoken.kt` (test helper: `fun of(language: UiLanguage): WatchSpoken`, the host's stand-in for `watchSpoken()`, which needs Compose's resource environment. It reads the three templates and the two plurals from that language's `strings.xml`, picks each number's item by CLDR's rule, and keeps the runs with Task 1's `countRuns`. The rules: English, Turkish and Urdu "one" for 1; French and Bengali "one" for 0 and 1; Indonesian "other" always; Arabic zero 0, one 1, two 2, few 3–10, many 11–99. A missing item falls back to "other", as Compose's does.)
  - `shared/src/commonTest/kotlin/world/taqwa/app/watchfeed/WorstFeeds.kt` (test helper, in commonTest so that Task 5's host test and Task 8's simulator test reuse it)
  - `shared/src/androidHostTest/kotlin/world/taqwa/app/watchfeed/FrozenTimes.kt` (test helper)
  - `shared/src/androidHostTest/kotlin/world/taqwa/app/watchfeed/WatchFeedGoldenTest.kt`. It writes `watchcore/vectors/phone/phone-{capetown-en,capetown-friday-en,capetown-ur24,tromso-may-en,tripoli-ar-ly12}.json` from `watchcore/vectors/phone/times/{capetown,tromso-may,tripoli}.json`.
  - `shared/src/androidHostTest/kotlin/world/taqwa/app/watchfeed/WatchFeedAgreesTest.kt`

**Interfaces:**
- Consumes:
  - Task 1's words, `WatchDateLine` (with its `spoken`), `WatchDigits`, `wordsLocaleTag`, and `watchUiLanguage` and `watchSpoken` (through the inputs, below); `countRuns` in the `BundledSpoken` helper; `PhoneTags.TAGS` and `PhoneTags.digitSets` in the size test. Nothing of Task 2's: Steps 1–3 run in a worktree without it;
  - `CityRepository.setLanguage` and `displayName`, as `PrayerViewModel.resolveCityName` calls them;
  - `QiblaMath.bearing`/`distanceKm`;
  - `localizedGroupedKm` (`feature/qibla/QiblaScreen.kt`), for the grouping the phone's Qibla card writes;
  - `WorldMagneticModel.declinationDegrees(lat, lon, 0.0, decimalYear(today))`;
  - `TabularHijriCalendar.fromGregorian(date + hijriOffsetDays)`;
  - `TimelineBuilder.isJumuah`;
  - the watchcore model and `WatchFeedJson.encode`, to measure the feed.

  `UiLanguage.of(format.languageTag())`, `LayoutDirection.isRtl`, `usesNativeDigits` and `WidgetDigits.nativeZero` are not used.
- Produces `object WatchLinkContract` in watchcore, constants with their sources in the KDoc:
  - `FEED_PATH = "/taqwa/feed"`, `REFRESH_PATH = "/taqwa/refresh"`, `ACK_PATH = "/taqwa/ack"`;
  - `WATCH_CAPABILITY = "taqwa_watch"`, `PHONE_CAPABILITY = "taqwa_phone"`;
  - `CONTEXT_FEED_KEY = "feed"`, `CONTEXT_DELIVERY_KEY = "delivery"` (Ruling 50; it was `CONTEXT_FINGERPRINT_KEY = "fingerprint"`), `REFRESH_MESSAGE_KEY = "refresh"`, `REFRESH_REPLY_KEY = "queued"`;
  - `fun isFingerprint(text: String): Boolean`: exactly 16 characters of `0-9a-f`. The phone writes it (Task 4), and the watch only checks its form before it stores or echoes one; `fun isOrigin(text: String): Boolean`, the same form, for an install's random origin;
  - **Ruling 50:** `data class WatchDelivery(val origin: String, val seq: Long, val fingerprint: String)`, always well formed (`seq` from 1), whose `text` is `"origin/seq/fingerprint"`; `fun deliveryOf(text: String): WatchDelivery?`, which reads exactly that form (the number in its one decimal form, within a `Long`, which Swift reads as `Int64`) and nothing else, the empty echo among it;
  - `fun feedPath(delivery: WatchDelivery): String` = `"$FEED_PATH/${delivery.text}"`, the path the Android phone sends a feed on; `fun deliveryIn(path: String): WatchDelivery?`, the delivery after `"$FEED_PATH/"`, or null (a feed that comes without one is no phone's: not stored, not acknowledged);
  - `fun isFeedPath(path: String): Boolean`: `FEED_PATH` itself or anything under `"$FEED_PATH/"`, never another path the listener's `pathPrefix` also matches (`/taqwa/feedback`);
  - **the receivers' rules,** which Task 6 and plan 4's Task 5 build their stores on: `fun stores(held: WatchDelivery?, arriving: WatchDelivery): Boolean` (only another origin, or a greater number; a greater number even for the same feed), `fun echoOf(held: WatchDelivery?): String` (the acknowledgement's and the handshake's payload: the delivery's text, or `""`), and `val STORE_STEPS: List<WatchStoreStep>` = `REMOVE_DELIVERY`, `PLACE_FEED`, `WRITE_DELIVERY`, the only order a receiver stores in;
  - `MESSAGE_LIMIT_BYTES = 100_000` (Google: `sendMessage`'s data is "Generally not larger than 100k"; the messages page: MessageClient does not support data larger than 100 KB);
  - `USER_INFO_LIMIT_BYTES = 65_536` and `CONTEXT_LIMIT_BYTES = 262_144` (not documented by Apple; the framework's own constants as reported in 2016 and 2022; plan 4 measures them on the paired watch);
  - `ENVELOPE_BYTES = 1_024`, room for the message path or the property list's dictionary around the feed;
  - `FEED_BUDGET_BYTES = 60_000`, the encoded feed's UTF-8 bytes.

  The KDoc states the whole link as Global Constraints does, Google's and Apple's caveats included, so plans 3 and 4 read it in one place. The code-space rules are not here: each side reads the library's own constants (Task 5, Task 6).
- Produces `internal data class WatchFeedInputs(val location: GeoLocation, val city: String, val language: UiLanguage, val spoken: WatchSpoken?, val prayerSettings: PrayerSettings, val format: PlatformFormat, val clockFormat: ClockFormat, val phrases: List<TasbeehPreset>, val now: Instant, val timesFor: (LocalDate) -> DayPrayerTimes)`.
  - `language` is the app's interface language (Task 4's `feedFor` passes `watchUiLanguage()`); the builder never derives one.
  - `spoken` is the screen reader's words in that language (Task 4's `feedFor` passes `watchSpoken()`); the builder copies it, and never reads a resource. Only the tests pass null.
  - `city` is the city's name as the Prayer screen shows it (Task 4's `feedFor` passes `watchCityName(…)`); the builder never looks one up.
- Produces `internal suspend fun watchCityName(location: GeoLocation, format: PlatformFormat, cities: CityRepository): String`, in `WatchCityName.kt`. It is the Prayer screen's path (`PrayerViewModel.resolveCityName`), with the format's tag and not the interface language, so that app = watch:
  - with a `cityId`, `cities.setLanguage(format.languageTag())`, then `cities.displayName(cityId)`;
  - when that is null, or there is no `cityId`, `location.cityName`;
  - when that is null too, `""`.
  - The first lookup in a process parses the bundled list (`App.kt`'s comment: about 0.4 s on a phone). It runs inside the ask's budget, on `work`, and Task 7 records the cold-start ask's milliseconds.
- Produces `internal object WatchFeedBuilder { const val DAYS = 31; const val MAX_PHRASES = 100; const val TIMES_ALONE_MAX_BYTES = 40_000; fun sizeOf(json: String): Int; fun roundedKm(km: Double): Double; fun build(inputs: WatchFeedInputs, budgetBytes: Int = WatchLinkContract.FEED_BUDGET_BYTES): WatchFeed? }`. Only the tests pass `budgetBytes`, so that the phrase rule can be shown at any feed's size:
  - `sizeOf` is `json.encodeToByteArray().size`, the UTF-8 bytes both links carry.
  - `roundedKm` gives two significant figures (the Lead's call 5). Under 10 km it is `km` itself, which `localizedGroupedKm` truncates to the phone's own whole kilometre. From 10 km it is `(km / step).roundToLong() * step`, with `step` = 10^(⌊log10 km⌋ − 1): 1 km from 10, 10 km from 100, 100 km from 1,000 and 1,000 km from 10,000.
  - **Dates:** yesterday through today + 29, at the place. The first date whose `timesFor` throws (anything but `CancellationException`) ends the feed at the day before. Null when yesterday or today cannot be given.
  - **Each day:**
    - `times`: the six `time(p).epochSeconds`; `fajrEnd` and `asrEnd` come from `ends[FAJR]` and `ends[ASR]`.
    - `clock`: `format.clockFigures` at the place.
    - `names`: `PrayerNaming.name(p, language.code)`, with Dhuhr becoming `WatchWords.jumuah(language)` when `isJumuah`.
    - `hijri` and `hijriDayMonth`: from one `HijriDate`, with `language`.
    - `gregorian`, `weekday`, `dayNumber` and `gregorianSpoken`: from `WatchDateLine.of(date, format, language)`, the last its `spoken`.
    - `midnight`: `date.atStartOfDayIn(zone).epochSeconds`.
    - Every string that can hold a digit goes through `WatchDigits.of(format, language).write`: `clock`, `hijri`, `hijriDayMonth`, `gregorian`, `dayNumber`, `gregorianSpoken`, and the place's `qiblaKm`. The phrase templates (`countdown`, `nextIn`, `spoken`) hold no digit of the phone's and are never rewritten: their `%1$s` stays as it is.
  - **`place`,** as Ruling 39 allows it:
    - `city` = `inputs.city`;
    - `qiblaBearing` = `QiblaMath.bearing(location).toInt().toDouble()`, the phone's readout (`PrayerScreen.kt:839`);
    - `declination` = `declinationDegrees(…).roundToInt().toDouble()`;
    - `qiblaKm` = `distance(roundedKm(QiblaMath.distanceKm(location)), …)` (`WatchFeedBuilder.distance()`, which supersedes the formula first written here, `localizedGroupedKm(…, format)` then `WatchDigits.write`; Ruling 48): `localizedGroupedKm(km, format)` where the feed keeps the phone's digits, and otherwise `DigitGrouping.group(km.toLong(), language)` in Western digits, so the mark is the Western one too, never U+066C beside Western digits;
    - `southern` = latitude < 0.
    - No coordinate is in the feed.
  - **`ui`:**
    - `language` = `wordsLocaleTag(format.languageTag(), language)`, never the raw tag: the watch upper-cases slot A's label in it (Global Constraints, "Language");
    - `rtl` = `language.rtl` (the input's); `twelveHour` from `clockFormat`;
    - `digitZero` = `WatchDigits.of(format, language).zero`;
    - `am`/`pm` = `format.dayPeriods(language)`;
    - `nextIn` = `nextInPattern(language.code)`, and `countdown` = `WatchWords.countdown(language)`;
    - `spoken` = `inputs.spoken`.
  - **`tasbeeh`:** the longest run of the first `MAX_PHRASES` custom presets, in order, as `WatchPhrase(id, parts.first().dhikr.arabic, total)`, for which `sizeOf(WatchFeedJson.encode(feed))` is at most `budgetBytes`. It is found by bisection, at most 8 encodes. The times are never cut: with no phrase fitting, the feed goes without phrases. `build` lets an `encode` throw (NaN) pass to its caller, whose sync answers `FAILED`.
- Produces, in androidHostTest, `internal object FrozenTimes`:
  - `fun read(file: File): (LocalDate) -> DayPrayerTimes`, which throws for a date the table does not hold;
  - `fun record(file: File, location: GeoLocation, dates: ClosedRange<LocalDate>)`, run only under `taqwa.recordTimes`, the one place where the golden test may construct `PrayerTimesEngine`.

  A table is `{"zone": "<id>", "days": {"<yyyy-mm-dd>": {"fajr": s, "sunrise": s, "dhuhr": s, "asr": s, "maghrib": s, "isha": s, "fajrEnd": s|null, "asrEnd": s|null}}}`. It is recorded once, when the golden test is written, and never refreshed to follow the engine: it is data, as the sample's instants are.
- Produces, in commonTest, `internal object WorstFeeds { val CITY: String; fun presets(kind: PhraseKind, count: Int = 100): List<TasbeehPreset> }` with `enum class PhraseKind { ARABIC, EMOJI, CONTROL }`:
  - `presets` gives phrases of 60 code points (`TasbeehStore.MAX_PHRASE`): of Arabic letters, of U+1F4FF (4 UTF-8 bytes each), or of U+0001 (JSON's six-byte escape, the most any character costs);
  - `CITY` is 100 Bengali letters, 300 UTF-8 bytes. The longest name the bundle holds on 23 October is 91 bytes (Bengali), and `theWorstCityIsLongerThanAnyTheBundleNames` keeps `CITY` ahead of the bundle.

- [ ] **Step 1: Write the failing tests.**
  - `WatchLinkContractTest`:
    - `theFeedBudgetFitsEveryLink`: `FEED_BUDGET_BYTES + ENVELOPE_BYTES` is at most `MESSAGE_LIMIT_BYTES`, `USER_INFO_LIMIT_BYTES` and `CONTEXT_LIMIT_BYTES`.
    - `aFingerprintIsSixteenLowercaseHex`: `isFingerprint` holds for "0123456789abcdef", and refuses "", 15 or 17 characters, capitals and "g".
    - `theFeedPathCarriesTheDelivery` (Ruling 50, which replaced `theFeedPathCarriesTheFingerprint`): `deliveryIn(feedPath(d))` is `d`. `deliveryIn` is null for "/taqwa/feed", "/taqwa/feed/", a bare fingerprint after it (the form before Ruling 50), a delivery with a segment after it, and the same delivery under "/taqwa/feedback" or "/taqwa/refresh". `isFeedPath` holds for "/taqwa/feed" and `feedPath(d)`, and not for "/taqwa/feedback".
    - Ruling 50 adds `anOriginIsSixteenLowercaseHexToo`, `aDeliveryTravelsAsItsOriginItsNumberAndItsFingerprint`, `anythingElseIsNoDelivery` (no number 0, no sign, no leading zero, no overflow, no other case), `aDeliveryIsAlwaysWellFormed`, `aWatchStoresOnlyWhatMovesItForward`, `theEchoIsTheDeliveryHeldOrNothing` and `aDeliveryIsWrittenAfterItsFeedAndRemovedBefore`, which stops a receiver after each step of `STORE_STEPS` and of the three wrong orders, and checks that only the right one never echoes a delivery naming a feed it does not hold.
  - The common tests use `EnglishPlatformFormat`, `language = ENGLISH` unless named, and a `timesFor` fake built from fixed instants:
    - `theFeedRunsFromYesterdayForThirtyOneDays`: at Cape Town 2026-10-18 08:00, days run from 2026-10-17 to 2026-11-16, 31 of them.
    - `aDayTheEngineCannotGiveEndsTheFeed`: a throw for 2026-10-25 leaves 8 days, the last 2026-10-24.
    - `withoutTodayThereIsNoFeed`: null.
    - `aPolarDayKeepsItsEndsMissing`: no ends gives null `fajrEnd` and `asrEnd`.
    - `fridaysDhuhrIsJumuah`: on 2026-10-23, `names.dhuhr` is "Jumuʿah", and on the 22nd it is "Dhuhr".
    - `theScreenReadersWordsAreSent`: `ui.spoken` is the input's `spoken` (and null for null), and each day's `gregorianSpoken` is `WatchDateLine.of(date, format, language).spoken`: "Sunday 18 October" on 2026-10-18.
    - `aClockChangeInTheFeedKeepsEachDaysOwnMidnight`: in Europe/London, the 26 October midnight minus the 25 October one is 90 000 s, and every midnight equals `atStartOfDayIn`.
    - `theFirstHundredPhrasesGo`: 101 short presets give 100 phrases, in order, with id, phrase and target.
    - `phrasesGoWhileTheyFit`, with 100 presets of 60 U+0001 characters each (`WorstFeeds.presets(CONTROL)`). Whether all 100 fit the real budget in English is a figure, not a rule (18,873 + 41,499 = 60,372 bytes at 0298756d, just over), so the test sets its own budget. Let `bare` be `sizeOf` of the feed built with no presets, and `each` the feed with the first preset alone minus `bare` (about 415 bytes; a later phrase costs a comma more, which the half phrase of slack below covers).
      - With `budgetBytes = bare + 10 * each + each / 2`, exactly the first 10 go, in order. `sizeOf` of the feed is at most that budget, and the same feed with the 11th preset added passes it.
      - With `budgetBytes = bare - 1`, the feed still holds all 31 days, and no phrase.
      - With the default budget, the phrases are a prefix of the presets, `sizeOf` is at most `FEED_BUDGET_BYTES`, and either all 100 go or the same feed with the next preset added passes it.
    - `thePlaceIsWhatRuling39Allows`: for Cape Town (-33.9249, 18.4241), London (51.5074, -0.1278), Tripoli (32.8872, 13.1913), Cairo (30.0444, 31.2357), Riyadh (24.7136, 46.6753), Madinah (24.4672, 39.6111) and Jeddah (21.5433, 39.1728), each with its name as the input's `city`:
      - `place.city` is the input's `city`;
      - `place.qiblaBearing` equals `QiblaMath.bearing(fix).toInt().toDouble()`;
      - `place.declination` equals `declinationDegrees(…).roundToInt().toDouble()`;
      - `place.qiblaKm` is "6,600" for Cape Town, "4,800" for London, "2,900" for Tripoli, "1,300" for Cairo, "790" for Riyadh, "340" for Madinah and "69" for Jeddah;
      - the encoded JSON contains neither the latitude's nor the longitude's text to two decimals ("-33.92", "18.42" and so on), and every number in `place` is whole.
    - `theDistanceIsRoundedToTwoFigures`: `roundedKm` gives 6,600 for 6,558.3; 4,800 for 4,793.8; 790 for 790.3; 340 for 339.3; 69 for 68.9; 100 for 99.6; 1,000 for 995.0; 12,000 for 12,345.0; and, under 10 km, 9.7 for 9.7 and 0.4 for 0.4, which `localizedGroupedKm` writes "9" and "0", as the phone does.
  - `WatchCityNameTest` (common), with a `CityRepository` built as `CityRepositoryTest` builds one: a two-row list (1 "Cape Town", 2 "London") and an Arabic names file (1 "كيب تاون").
    - `theCityIsThePrayerScreensName`: a location with `cityId` 1 and `cityName` "Cape Town" gives "كيب تاون" with an `ar-SA` format, and "Cape Town" with an `en-US` format.
    - `aTagTheBundleHasNoNamesForGivesTheListsName`: an `he-IL` format gives "Cape Town", since `setLanguage` reads any language the bundle has no file for as English, as the Prayer screen's header shows it.
    - `aCityTheListLacksFallsBackToTheStoredName`: `cityId` 99 with `cityName` "Somewhere" gives "Somewhere".
    - `withoutAnIdTheStoredNameIsSent`: no `cityId` and `cityName` "London" gives "London"; with neither, "".
  - `WatchFeedSampleTest`, host, through `AndroidPlatformFormat`, each tag with `UiLanguage.of(tag)` as its language:
    - `capeTownIsTheSampleFeed`:
      - Cape Town at (-33.9249, 18.4241), `cityId` 3369157, with the sample's own instants as the `timesFor` fake and `now` = the sample's `writtenAt`.
      - The input's `city` is `watchCityName(location, format, BundledCities.repository())`, the bundle's own name in each tag, and its `spoken` is `BundledSpoken.of(language)`.
      - Run for each of en/24, en/12, ar-LY/12 and ar-SA/24.
      - Expected: `days`, `ui` and `place` equal `watchcore/vectors/sample-feed-capetown{,-en12,-ar-ly12,-ar-sa}.json`, field by field: `gregorianSpoken` on every day, and `ui.spoken` and `ui.language` ("en", "ar-LY", "ar-SA") among them.
    - `bundledSpokenIsTheSamples`: `BundledSpoken.of(ENGLISH)` and `of(ARABIC)` equal the en and ar-SA samples' `ui.spoken`, which plan 1's Task 12 wrote from the phone's strings. Task 1's simulator test and Task 7 check 4 hold the real resource path to the same words.
    - **Every string the phone's own functions write wins:** CLDR's or our words, the digits, the thousands grouping, the bundle's city name, and the place as Ruling 39 rounds it. The builder is never bent to match a fixture.
      - Change `Fixtures.kt` to the phone's value.
      - Regenerate with `./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors`, then rerun `./gradlew :wearApp:testDebugUnitTest` (its `SampleFeedTest` reads the sample).
      - Record each change in the report: the field, the old value, the new value, and the phone function that wrote it.
      - If a changed string is drawn on the face (a date word, a digit), rerun Task 2's tools on it, and Step 4's pass below.
    - `theLanguageIsTheAppsNotTheTags`, over the sample's instants, each format given a `language` other than its tag's:
      - an `en-US` format with `language = ARABIC` gives Arabic names, Arabic countdown and `nextIn`, Arabic Hijri months, "الأحد" weekdays, `rtl` true, `am`/`pm` ("ص", "م"), Western digits (`digitZero` '0', the pair Arabic-Western), and `ui.language` "ar";
      - an `ar-SA` format with `language = ENGLISH` gives English names and words, `rtl` false, Western digits throughout (`digitZero` '0'), since (English, Arabic-Indic) is not a face pair, and `ui.language` "en".
    - `theWatchsCaseIsTheAppsCase`: for az-AZ, lt-LT and he-IL formats with ENGLISH, tr-TR with TURKISH and en-US with TURKISH, over the sample's instants: for every day and each of its six names, `name.uppercase(Locale.forLanguageTag(feed.ui.language))` (plan 1's slot A rule, `ComplicationFields.kt:186`) equals `name.uppercaseIn(language)` (the app's, `Casing.kt`). So az-AZ's Maghrib is "MAGHRIB", never "MAGHRİB", and Turkish keeps its dotted capitals.
    - `theDigitZeroIsTheFeedsOwnZero`: built over the sample's instants for en, ar-SA, ar-LY, ar-LY-u-nu-arab, ar-EG, bn-BD, ur-PK, ur-PK-u-nu-arabext and fr-FR. Every digit in every `clock`, `hijri`, `hijriDayMonth`, `gregorian`, `gregorianSpoken`, `dayNumber` and `qiblaKm` lies in `digitZero..digitZero+9`, and `digitZero` equals `format.localizedDigits(0)` for each of these tags.
    - `aSetTheFaceWasNotBuiltForIsWesternThroughout`: for mr-IN and ne-NP (Devanagari), my-MM (Myanmar) and fa-IR (Persian), `digitZero` is `'0'`, and the encoded feed holds no decimal digit (Unicode category Nd) outside 0–9.
    - `aPhoneOutsideTheSevenGetsTheAppsOwnFallback`: for hi-IN, he-IL, fa-IR, mr-IN, zh-CN and az-AZ, against the en feed over the same instants:
      - the names, `countdown`, `nextIn`, `spoken`, and every `weekday`, `gregorianSpoken` and the words of every `gregorian` are en's;
      - `am`/`pm` are ("AM", "PM"), `rtl` is false, `digitZero` is `'0'` and `ui.language` is "en".
    - `theLargestFeedStaysWithinItsBudget`: for ar-SA and bn-BD on 12 hours and ur-PK-u-nu-arabext on 12 hours, each in its own digits, over the sample's 31 days, with `city = WorstFeeds.CITY`, `spoken = BundledSpoken.of(language)` and `WorstFeeds.presets` of each `PhraseKind`:
      - `sizeOf` is at most `FEED_BUDGET_BYTES`, and all 31 days are there;
      - the phrases are a prefix of the presets, in order;
      - for ar-SA with `ARABIC` and with `EMOJI`, all 100 go;
      - for ar-SA and bn-BD with `CONTROL`, fewer than 100 go, and the same feed with the next preset added passes `FEED_BUDGET_BYTES`. The real budget bites here: the ar-SA sample alone is 23,027 bytes, Bengali's script and digits take three bytes a character, and 100 such phrases add 41,499, against the 18,501 bytes the times may take before not all fit.

      The report records each case's UTF-8 size and its phrase count, beside the UTF-16 size a `String` context would have taken. Measured on this branch at 0298756d, with Task 12's spoken words in: the ar-SA sample is 23,027 bytes in UTF-8 (38,330 in UTF-16); with 100 Arabic phrases about 40.6 KB, with emoji about 52.6 KB.
    - `theTimesAloneLeaveRoomForThePhrases`: for every tag of Task 1's `PhoneTags.TAGS`, in each of its digit sets (a format on each `formatTag` of `PhoneTags.digitSets(tag) { AndroidPlatformFormat(Locale.forLanguageTag(it), ClockFormat.TWELVE_HOUR) }`), on 12 hours, with `city = WorstFeeds.CITY`, `spoken = BundledSpoken.of(UiLanguage.of(tag))` and no phrases: `sizeOf` is at most `TIMES_ALONE_MAX_BYTES`. The report records the largest, with its tag, so that plans 3 and 4 know the margin their words add into (the Lead's call 6).
    - `theWorstCityIsLongerThanAnyTheBundleNames`: every name in `BundledCities.allNames()` takes fewer UTF-8 bytes than `WorstFeeds.CITY`, so the size tests stay the worst case when the city list is refreshed.
  - `WatchFeedGoldenTest.phoneFeedsAreUpToDate`: default `PrayerSettings`, `AndroidPlatformFormat`, each tag's `UiLanguage.of(tag)` with its `BundledSpoken.of(…)`, and **only frozen instants** (`FrozenTimes.read`):
    - Cape Town (-33.9249, 18.4241, Africa/Johannesburg) in en/24h at 2026-10-18T06:00Z, and again on Friday 2026-10-23. The table holds 2026-10-17 to 2026-11-21.
    - Tromsø (69.6492, 18.9553, Europe/Oslo) in en/24h at 2026-05-15T10:00Z. The table holds 2026-05-14 to 2026-06-13.
    - Tripoli (32.8872, 13.1913, Africa/Tripoli) in ar-LY/12h at 2026-10-18T06:00Z. The table holds 2026-10-17 to 2026-11-16.
    - Cape Town again in `ur-PK-u-nu-arabext` on 24 hours at 2026-10-18T06:00Z, from the Cape Town table (`phone-capetown-ur24.json`): the first Urdu feed, in Extended Arabic-Indic digits, for Step 6's shots and for plan 4.

    The cities are literals, frozen as the instants are: "Cape Town", "کیپ ٹاؤن" for the Urdu feed, "Tromsø" and "طرابلس". `WatchCityNameTest` and the sample test hold the path that names them.

    Each feed is byte for byte the file in `watchcore/vectors/phone/`, which the test rewrites under `-Ptaqwa.updateVectors`. That flag never touches the tables. Every golden carries `ui.spoken` and `gregorianSpoken`, so plan 4's Swift decoder meets the spoken words in English, Arabic and Urdu. The goldens are byte-stable by construction: Cape Town's November rows (VOC by hand around 27 October, MJC and Jamiat from the 1 November cloud run), or any later engine change, move no byte of them. Plan 4's Swift decoder needs the format and the shapes (Jumuʿah, polar ends, Arabic 12-hour, Urdu digits), not today's figures.
  - `WatchFeedAgreesTest`, host, real engine, comparing it with itself at run time (a rule, never a figure):
    - `everyInstantIsTheEngines`: each day the feed holds has six seconds that equal `engine.timesFor(location, date, prefs)`.
    - `theWatchAgreesWithThePrayerScreenAndTheWidgets`: for Cape Town, London (from 2026-10-20, across the clock change), Tromsø in May and Makkah. Sample every 10 minutes from today's midnight up to, but not including, the Isha of the feed's last day. That is day 29 when the engine gives every day; after the last Isha the watch has no tomorrow, and answers null. At each sample `t`, with `buildForWidgets(yesterday, today, tomorrow, t, false, zone)` from the same engine days:
      - `WatchRules.moment(feed, t)` is non-null;
      - its `next.prayer` and `next.at` equal the state's `next.prayer` and `next.instant.epochSeconds`;
      - its `secondsLeft` equals `countdown.inWholeSeconds`;
      - its `progress` equals `ringProgress`;
      - `WatchRules.litOnList` equals `TimelineBuilder.build`'s CURRENT row;
      - `WatchRules.litOnGlance` equals `buildForWidgets`'s.

      `TodayState` has no `previous`, so the ring's progress stands for it. The sole exception is Ruling 11's case, which the engine never produces; any other difference fails.
- [ ] **Step 2:** Run `./gradlew :watchcore:testAndroidHostTest :shared:testAndroidHostTest :shared:iosSimulatorArm64Test`. Expected: FAIL.
- [ ] **Step 3:** Implement `WatchLinkContract`, `watchCityName`, `WatchFeedBuilder`, `FrozenTimes`, `BundledCities` and `WorstFeeds`.
  - Record the three tables once with `-Ptaqwa.recordTimes`, then generate the goldens with `-Ptaqwa.updateVectors`.
  - Change the fixtures and regenerate the watchcore vectors, as above.
  - In the report, write every fixture change.
- [ ] **Step 4:** Run `scripts/test.sh`. Expected: PASS. `:watchcore` and `:wearApp` stay green with the new fixtures and the `vectors/phone/` files (they are debug assets only).
  - Check that `grep -n "PrayerTimesEngine" shared/src/androidHostTest/kotlin/world/taqwa/app/watchfeed/{WatchFeedGoldenTest,FrozenTimes,WatchFeedSampleTest}.kt shared/src/commonTest/kotlin/world/taqwa/app/watchfeed/WorstFeeds.kt` shows the engine only inside `FrozenTimes.record`.
  - Run `tools/watchface/pass.sh <scratch dir>` on `emulator-5558` with the regenerated sample. Every shot equals Task 2's pass in 0 px, the Moon's seconds hand and the step count aside, unless a drawn string changed. Each such shot is listed in the report with its changed string and a side-by-side, and committed in place of the old one. A form that no longer fits stops the task, for the Lead.
- [ ] **Step 5: Commit** with the message "Watch feed: the phone builds the feed from its own engine, a month of it from yesterday, in the app's language and case, with the screen reader's words, the place as Ruling 39 allows it, within its byte budget, and golden feeds from frozen instants for the watchOS port".
- [ ] **Step 6: Urdu's digits on the face** (Task 2 Steps 6–9's fix, shot with the first Urdu feed). On `emulator-5558` with Task 2's face installed (`tools/watchface/install.sh`, `activate.sh`), write `tools/watchface/store.sh`, then:
  - `tools/watchface/store.sh watchcore/vectors/phone/phone-capetown-ur24.json`, then for Arch, Ring and Sun (`tools/watchface/choose.sh style <style>`): `KEEP_FEED=1 tools/watchface/shoot.sh <report dir> <style>-ur 16:47 awake`. At 16:47 the clock reads ۱۶:۴۷, so it holds ۶, ۴ and ۷. The Moon too, with its slots empty.
  - Shoot once with the watch's system language in English, and once in Urdu, set in the emulator's Settings; the report says how it was set. Put the language back to English afterwards.
  - Expected: in every shot, every ۴, ۶ and ۷ (the clock, the slots' times, the dates, the countdown) is Noto's Urdu form. Compare them with the new fonts' own glyphs, drawn with Pillow at each size, and with the Persian forms beside them, and put both in the report.
  - If any of them shows a Persian form under either language, **stop** and hand it to the Lead.
  - Then `tools/watchface/feed.sh` (the sample again) and `tools/watchface/clock.sh auto`.
  - Commit `store.sh`, its README line and the shots, with the message "Face: Urdu's digits shot with the phone's first Urdu feed, under an English and an Urdu watch".
- [ ] **Step 7: The wording, for plan 5 and Mohamed's approval.** Write `docs/superpowers/reports/2026-10-23-watch-privacy-wording.md`:
  - **What leaves the phone,** field by field, as the builder now writes it: Ruling 39's table, with one real feed's `place` quoted (Cape Town, en and ar-SA).
  - **Ruling 39's lines (items 1 and 2 under "The lines that change"),** for every file listed there, in each of its languages: the file, the key or the line, the current text quoted, and the new text.
    - The English is the ruled sentence, "Only your own devices can see your location.", in place of the old clause, and the ruled relay sentence. Neither is reworded. The relay sentence's lead-in is question 7's.
    - The other six languages are drafted from the app's existing localisation rules: the terms the app's own strings already use for "device", "location" and "watch" in that language, and the repository's i18n rules (the `values-in` twin for Indonesian, Compose's apostrophes in `strings.xml`). Each is marked "draft, to native review (plan 5)".
  - **Question 7's lines (item 3),** in the same form. The English is question 7's draft, and each file's other languages are drafted the same way. The English-only files (`README.md`, `CONTRIBUTING.md`, `SECURITY.md` and the stores' review notes) have the English alone. Each is marked for his approval and, where translated, for native review.
    - The App Store Connect notes are drafted in the report as the whole text of the new file plan 5 writes (`docs/store/app-review/<date>-notes-<version>.txt`): `2026-10-02-notes-1.0.0-35.txt` with question 7's three changes. The report gives its UTF-8 byte count, which is at most 4,000 (measured at 3,992 on 24 October). The file itself is plan 5's.
  - **The lines that stay (item 4),** with the reason for each.
  - **The code comments (item 5),** with the task that rewrites each.
  - **The sweep,** so that no promise is left in neither list. Run it, and put its output in the report:

    ```bash
    git ls-files | grep -vE '^(docs/superpowers|\.superpowers|docs/research|tools)/' \
      | grep -E '\.(md|html|json|xml|strings|txt|plist)$' \
      | grep -vE '/values-[a-z]|\.(ar|bn|fr|id|tr|ur)\.md$|/(ar|bn|fr|id|tr|ur)/|(ar|bn|fr|id|tr|ur)\.lproj/|composeResources/files|vectors/' \
      | tr '\n' '\0' | xargs -0 grep -niE 'leaves? (the|your) phone|never leaves|sent anywhere|nowhere|on your phone|stays on|collect|transmit|uninstall|delet(e|es|ing) the app|removes all|network|internet|offline|third-party|sdk'
    ```

    Each line it prints is in item 1, 2, 3, 4 or 5, or is about something else: a recitation download, a build tool, the research notes, a code identifier. The report says which, a line at a time for the first kind, and in one sentence for each of the other kinds. A promise in no item is added to the list, with a draft, before the commit.
  - **The gate:** the list plan 5 checks off before a build with a sender ships. That is every line in each of its languages (the Backups sentence among them, which stays whatever plan 4's simulator check finds), the policy's date, `STORE-PRIVACY.md`'s re-answer, Exodus on the first sender build, and the stores' review notes: `en.md:115` in both consoles, and the new notes file pasted into App Store Connect's Notes field with `en.md:117` pointing at it.

  No app, site or store file changes. Commit the report with the message "Docs: the watch feed's privacy wording in seven languages, Ruling 39's lines and the drafts for Mohamed's approval".

### Task 4: When the phone sends, never at the reminders' cost

**Files:**
- Create in `shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/`: `WatchLink.kt`, `WatchFeedSync.kt`, `WatchFeedMemory.kt` and `WatchFeedFingerprint.kt`.
- Create `shared/src/{androidMain,iosMain}/kotlin/world/taqwa/app/watchfeed/WatchLink.{android,ios}.kt`. Each is an interim `actual fun createWatchLink(): WatchLink = NoWatchLink`; Task 5 replaces the Android one and Task 8 the iOS one.
- Modify:
  - `shared/src/commonMain/kotlin/world/taqwa/app/notifications/NotificationCoordinator.kt`
  - `shared/src/commonMain/kotlin/world/taqwa/app/di/AppContainer.kt`
  - `shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsKeys.kt` (the record's six `WATCH_FEED_*` keys, Ruling 50; they replaced `WATCH_FEED_FINGERPRINT`)
  - `shared/src/commonMain/kotlin/world/taqwa/app/feature/tasbeeh/TasbeehViewModel.kt`
  - `shared/src/commonMain/kotlin/world/taqwa/app/PrayerRoutes.kt`
  - `shared/src/androidMain/kotlin/world/taqwa/app/notifications/SystemEventReceiver.kt`
  - `shared/src/androidMain/kotlin/world/taqwa/app/notifications/PrayerAlarmReceiver.kt`
  - `shared/src/androidMain/kotlin/world/taqwa/app/notifications/NotificationTopUpWorker.kt`
  - `shared/src/iosMain/kotlin/world/taqwa/app/notifications/BackgroundRefreshBridge.kt` (its body moved into `afterBackgroundRefresh`)
- Test:
  - `shared/src/commonTest/kotlin/world/taqwa/app/watchfeed/WatchFeedSyncTest.kt`
  - `shared/src/commonTest/kotlin/world/taqwa/app/watchfeed/FakeWatchLink.kt` (with `TestWatch`, a watch storing by the contract's rule), `FakeWatchFeedMemory.kt` and `WorldLink.kt` (test helpers)
  - `shared/src/commonTest/kotlin/world/taqwa/app/watchfeed/WatchFeedMemoryTest.kt` and `WatchFeedRecordTest.kt`
  - `shared/src/commonTest/kotlin/world/taqwa/app/watchfeed/WatchFeedNamedCasesTest.kt` and `WatchFeedDeliveryModelTest.kt` (Ruling 50 item 7)
  - `shared/src/commonTest/kotlin/world/taqwa/app/watchfeed/WatchFeedFingerprintTest.kt`
  - `shared/src/commonTest/kotlin/world/taqwa/app/notifications/NotificationCoordinatorTest.kt` (add)
  - `shared/src/commonTest/kotlin/world/taqwa/app/feature/tasbeeh/TasbeehViewModelTest.kt` (add)
  - `shared/src/androidHostTest/kotlin/world/taqwa/app/notifications/SystemEventReceiverTest.kt` (add)
  - `shared/src/androidHostTest/kotlin/world/taqwa/app/notifications/NotificationTopUpWorkerTest.kt` (add)
  - `shared/src/iosTest/kotlin/world/taqwa/app/notifications/BackgroundRefreshBridgeTest.kt` (P2.4's fix round 1)

**Interfaces:**
- Consumes Task 3's `WatchFeedBuilder`, `WatchFeedInputs`, `watchCityName` and `WatchLinkContract` (`isFingerprint` among it), and Task 1's `watchUiLanguage` and `watchSpoken`.
- Produces:
  - `internal enum class WatchSendResult { SENT, UNCHANGED, NO_WATCH, NO_FEED, FAILED, TOO_LARGE }`. `TOO_LARGE` is a link's answer only; an ask never ends in it.
  - `internal enum class WatchSyncReason { RESCHEDULED, PHRASES_CHANGED, WATCH_ASKED, WATCH_APPEARED, WATCH_CHANGED }` (the Lead's call 1):
    - `WATCH_ASKED` is the watch's handshake;
    - `WATCH_APPEARED` is a watch that may be back: Android's capability change with a nearby watch, the iPhone's activation at launch. It is damped by the record, as a reschedule is;
    - `WATCH_CHANGED` is the iPhone's `sessionWatchStateDidChange`, and Task 7's harness `send`: the confirmation is forgotten before the ask is served (Ruling 50 item 6).
  - `internal interface WatchLink { val confirmsDelivery: Boolean; suspend fun reachable(): Boolean; suspend fun watchKey(): String?; suspend fun send(json: String, delivery: WatchDelivery): WatchSendResult }` (Ruling 50):
    - `reachable()` is the cheap question, "is a watch with Taqwa there?": the ask's one capability reading on Android, one property read on iOS;
    - `suspend fun <T> oneAsk(block: suspend () -> T): T`, with the default `block()` (Ruling 54 item 4): the sync runs each ask in it, inside the ask's budget, so a link may read its watches once there and answer `reachable()` and `watchKeys()` from that one reading. Android's does; the iPhone's keeps the default;
    - `watchKey()` is the key of the one watch a send now goes to (amendment A3): on the iPhone the UUID the bridge keeps in that watch's `watchDirectoryURL` (Task 8), null on Android and when there is none to read;
    - `watchKeys(): Set<String?>`, with the default `setOf(watchKey())`, is the keys of every watch a send now goes to (Ruling 52 item 9, Task 5 as built): the iPhone's one session watch, or the Android phone's nearby watches' node ids. The sync reads it once where an ask needs it: a confirmation counts only while its watch is one of them (`holds(fingerprint, keys)`), a link that does not confirm hands over to its one key (`keys.singleOrNull()`, read before the call), and on such a link an echo is taken in only from that key (`WatchFeedRecord.echoed(echo, from, keys)`, A3); a link that confirms by echo (Android) takes every echo in, for the node it came from;
    - `send` carries the feed and its delivery (`WatchDelivery`: the install's origin, the number, that very feed's fingerprint) together: the path after `/taqwa/feed/` on Android (Task 5), the context's `delivery` key on the iPhone (Task 8). The number is on record before `send` is called;
    - `send` answers only `SENT`, `NO_WATCH`, `FAILED` or `TOO_LARGE`. `SENT` means handed to the platform: on Android only queued. Every other answer means handed over to nothing, or maybe not, which Tasks 5 and 8 keep: no node queued it, or `updateApplicationContext` was never called or threw, which leaves the earlier context in place. Only `SENT` may confirm, and only where the link does not confirm by echo;
    - `confirmsDelivery` is true when only the watch's echo confirms (Android, the Lead's call 1), and false when the platform delivers the latest feed it was handed and nothing older after it (the iPhone's application context), so a `SENT` confirms its number (item 4).
  - `internal object NoWatchLink : WatchLink`, which never confirms, is never reachable, has no key and is always `NO_WATCH`, and `internal expect fun createWatchLink(): WatchLink`.
- Produces, in `WatchFeedFingerprint.kt`, `internal fun watchFeedFingerprint(feed: WatchFeed): String`: the 64-bit FNV-1a (offset basis `0xcbf29ce484222325`, prime `0x100000001b3`), as 16 lowercase hex characters, of the UTF-8 of `WatchFeedJson.encode(feed.copy(writtenAt = 0))`. Beside it, `internal fun fnv1a64Hex(bytes: ByteArray): String`.
  - It lives in `:shared`, so only the phone can compute it. The watch stores the one it was sent and echoes it (the Lead's call 1). A watch on another version than the phone decodes a feed and re-encodes a different text: without the keys a newer phone added, or with the defaults of fields an older phone never wrote. Its own fingerprint would never match.
  - `WatchFeedJson` in watchcore does not change.
- Produces, in `WatchFeedMemory.kt` (Ruling 50 item 3, as amended), `internal data class WatchFeedRecord(origin: String, lastSeq: Long = 0, lastStandsFor: String? = null, lastSent: String? = null, confirmedSeq: Long? = null, confirmedFor: String? = null)` with its pure rules:
  - `holds(fingerprint, key)`: `confirmedSeq != null && confirmedSeq == lastSeq && lastStandsFor == fingerprint && confirmedFor == key`, the one test for `UNCHANGED`;
  - `sending(standsFor, sent)`: the next number, with what it stands for and the fingerprint it carries; `lastDelivery` is that number's `WatchDelivery`;
  - `handedOver(key)`: `confirmedSeq = lastSeq` for that watch (the iPhone's `SENT`);
  - `forgotten()`: nothing confirmed, and the next number set aside, `lastSeq + 1` with `lastStandsFor` and `lastSent` null, never sent (`WATCH_CHANGED`, an echo of nothing or of another origin; amendment A2, so no echo sent before the forget names the latest number);
  - `echoed(echo, from, session)`: ignored when `from != session` (A3); `forgotten()` for an echo of nothing or another origin; `lastSeq` lifted, `lastStandsFor` and `lastSent` unknown, when the echo is above `lastSeq` or at it with another fingerprint than `lastSent` (item 5, A4); otherwise `confirmedSeq` is the echo's number, lower or higher, for `from` (A1b).
- Produces `internal interface WatchFeedMemory { suspend fun read(): WatchFeedRecord; suspend fun write(record: WatchFeedRecord) }`, and `internal class DataStoreWatchFeedMemory(store: DataStore<Preferences>, newOrigin: () -> String = ::newWatchOrigin) : WatchFeedMemory` under the six `SettingsKeys.WATCH_FEED_*` keys, every field written in one edit. The first read of an install makes its origin in one edit with an empty record, and a store whose origin is gone starts a whole new record, so origin and numbers live and die together (the store's `ReplaceFileCorruptionHandler` resets the whole file). `newWatchOrigin()` is 16 hex characters of `Uuid.random()`. The settings store is never restored from a backup (Android's extraction rules, `DataStoreFactory.ios.kt`'s exclusion), so a new install, a cleared app or a new phone makes a new origin.
- Produces `internal class WatchFeedSync(feedFor: suspend (GeoLocation?) -> WatchFeed?, link: WatchLink, memory: WatchFeedMemory, scope: CoroutineScope, work: CoroutineDispatcher = Dispatchers.Default, budget: Duration = 5.seconds, callerBound: Duration = CALLER_BOUND, report: (WatchSyncReason, WatchSendResult, elapsedMillis: Long) -> Unit = { _, _, _ -> })`, with `companion object { val CALLER_BOUND: Duration = 10.seconds }`. Its members:
  - `fun request(reason: WatchSyncReason, location: GeoLocation? = null, have: String? = null, from: String? = null)`: queues an ask and returns at once. It never suspends, blocks or throws, even once the worker is gone.
  - `suspend fun sync(reason: WatchSyncReason, location: GeoLocation? = null, have: String? = null, from: String? = null): WatchSendResult`: queues an ask the same way, and awaits its result for at most `callerBound`, then answers `FAILED`. The ask stays queued. It is used where a caller must answer (the watch's handshake).
  - `have` is the watch's echo, for `WATCH_ASKED` only (anything `WatchLinkContract.deliveryOf` refuses is an echo of nothing), and `from` the key of the watch it came from: read by the bridge when it arrived on the iPhone, the message's source node id on Android (Ruling 52 item 9).
  - `suspend fun acknowledge(echo: String, from: String? = null)`: queues the Android watch's acknowledgement, from the node `from` names (Ruling 52 item 9, Task 5 as built), and awaits it, for at most `callerBound`. Served in its turn, it is taken in as a handshake's echo is (step 0), for that node; on the iPhone, whose watch never acknowledges, one with no key is ignored. It never throws.
  - `suspend fun settle(): Boolean`: returns true once every ask made before it has been served, or false when `callerBound` passes first.

  How the asks are served:
  - **In order, one at a time,** by one worker on `scope`, on `work`, for example a `Channel(UNLIMITED)` and a single consumer. Two asks are never served in swapped order, and two sends never interleave.
  - **Each ask, in this order:**
    0. `WATCH_CHANGED`: the record is `forgotten()` (item 6), so even an answer of `NO_WATCH` leaves nothing confirmed. `WATCH_ASKED`: the watch's echo is taken in (`echoed`, against `link.watchKey()` as the session's watch).
    1. `link.reachable()` false answers `NO_WATCH`. Nothing is read or built.
    2. `feedFor(location)` null answers `NO_FEED`.
    3. An encode that throws answers `FAILED`, and nothing is sent.
    4. With `fp = watchFeedFingerprint(feed)` and `key = link.watchKey()`: `memory.read().holds(fp, key)` answers `UNCHANGED`, whatever the reason (spec 1.2's idempotent send). Otherwise the feed is sent: always after `WATCH_CHANGED`, and after a handshake that did not echo the latest delivery of this very feed.
    5. **Before the send,** the next number, with `lastStandsFor = lastSent = fp`, is written to the record and the write awaited (item 1); then `link.send(json, delivery)`. A `TOO_LARGE` (nothing handed over) sends `feed.copy(tasbeeh = emptyList())` once more, under the next number written the same way, standing for `fp` and carrying its own fingerprint as `lastSent`. So the times always arrive, and an unchanged feed over the link's limit is not sent again at every ask. Its answer is the ask's, and a second `TOO_LARGE` is `FAILED`.
    6. On `SENT`, a link that does not confirm writes `handedOver(key)`, the key read before the call. Any other answer, and every answer on a link that confirms, confirms nothing: on Android the watch's acknowledgement or its next handshake does. So a feed queued and lost is sent again at the next ask of any kind; a change undone before its acknowledgement is sent again, because its number is not confirmed; a late acknowledgement of an earlier feed lowers the confirmation and costs one more send.
  - **One budget for the whole ask:** steps 0–6 run under `withTimeoutOrNull(budget)`, the second send of step 5 included, and a timeout is `FAILED`. The budget starts when the ask is served, not when it was queued, so an ask waiting behind another is never failed by the wait.
  - **Exceptions:** any `Exception` becomes `FAILED`, never a throw, and the worker goes on to the next ask. That includes a `CancellationException` thrown while the worker's own scope is still active (`currentCoroutineContext().ensureActive()` does not throw), such as a `java.util.concurrent.CancellationException` from a library. Only the cancellation of `scope` itself ends the worker. When the worker ends, however it ends, its queue is closed (Ruling 48), so an ask, an acknowledgement or a settle made after that is answered at once, never after `callerBound`.
  - **Reporting:** `report` is called once per ask, with the elapsed milliseconds.
  - **As built, Ruling 70 item 1 (5 November):** an ask the budget cuts is asked again once, `AGAIN_AFTER` (30 s) after the cut, through the same path (its reason, location, echo and watch, a budget of its own, no caller waiting); only the budget's cut, never a `FAILED` the link answered, a throw or an unwritable feed; a second cut waits for the usual triggers. Only a reschedule's ask carries a planned location, and only its ask again gives way: an ask as made and served after its cut takes its place, so an older plan is never sent after a newer one; a settle, an acknowledgement or another ask asked again takes no place. Every other ask builds from what is stored when it is served, so it is asked again whatever is served meanwhile, and a cut in its step 0 (the forget, or the echo taken in) is never lost (review round 1). The wait runs on `scope`, not in the worker's turn, so nothing behind it waits and `settle` does not wait for it; it lives in the process, so whether it runs in a cached Android process is P5.13's to measure (the report's For the Lead 2). The budget stays 5 s. Two new last parameters, `clock: TimeSource = TimeSource.Monotonic` (the tests' virtual clock) and `timed: (AskTimes) -> Unit = {}`, give each ask's step times (`WatchAskStep`: the forget or echo, the capability query, the build, the encoding, the record's read, the send), the step the cut or a throw ended named unfinished. `AppContainer` prints them as `askStepsLine` in a debug build (`isDebugBuild`: Android's `FLAG_DEBUGGABLE`, iOS's debug Kotlin binary), and on Android in a signed build too once `adb shell setprop log.tag.TaqwaWatch DEBUG` is set (`stepsLogged`: `stepsLogOn(isDebugBuild(), Log.isLoggable("TaqwaWatch", Log.DEBUG))`, read at each ask), so P5.13's candidate can name a slow step (review round 2; plan 5 Task 13 check 9). Tests: `WatchFeedSyncTest`'s Ruling 70 sections (17 tests after review round 1), `WatchFeedLogTest.anAsksStepsAreOneDebugLine`, `theStepsLineIsOnInADebugBuildOrWhenItsTagIsEnabled` and `theTagTheStepsLineIsAskedByIsItsFirstWord`, `DebugBuildTest` (host and iOS). Report: `docs/superpowers/reports/2026-11-05-ruling-70-ask-again.md`.
- Produces a new last constructor parameter on `NotificationCoordinator`: `afterPlan: (location: GeoLocation) -> Unit = {}`. It is a plain function, not `suspend`.
  - It is called inside the lock, right after `scheduler.scheduleAll(plan)`, with the location that was planned. The asks are therefore made in the order the plans were armed. It must only queue (`request`), so the lock is held no longer than before.
  - It is never called when the location is null. The watch then keeps its last feed until it runs out (question 5).
  - It is wrapped in `try`/`catch (Exception)`, so nothing it does can escape `reschedule`.
  - A language change in place reaches the watch through it: `LanguageChangeReschedule` (`AppEffects.kt`) already reschedules on a new `ui_language`.
- Produces a new `TasbeehViewModel` parameter, `onPhrasesChanged: () -> Unit = {}`. `addCustom`, `updateCustom` and `removeCustom` call it after their `_state.update`, so the new chip shows first.
- Produces, in `SystemEventReceiver.kt`, `internal suspend fun afterSystemEvent(reschedule: suspend () -> Unit, refreshMirror: suspend () -> Unit, redrawWidgets: suspend () -> Unit, redrawAyah: suspend () -> Unit, settleWatch: suspend () -> Unit)`. It is the receiver's body inside its `withTimeout(8 000)`, with the steps passed in as `topUp`'s are. Each step runs in its own `runCatching`, in that order, with `settleWatch` last.
- Changes `topUp(...)` to take a fourth step, `settleWatch: suspend () -> Unit = {}`, run last in its own `runCatching`.
- `PrayerAlarmReceiver.afterAlarm` runs `runCatching { withTimeoutOrNull(WatchFeedSync.CALLER_BOUND) { appContainer.watchFeedSync.settle() } }` after `topUpPlan`, inside its 8-second budget. `BackgroundRefreshBridge.runBackgroundRefresh` runs the same after the mirror and the widget reload.
- Produces, in `BackgroundRefreshBridge.kt` (iosMain), `internal suspend fun afterBackgroundRefresh(reschedule: suspend () -> Unit, refreshMirror: suspend () -> Unit, redrawWidgets: suspend () -> Unit, settleWatch: suspend () -> Unit): Boolean`, the bridge's body with its steps passed in, as `afterSystemEvent`'s are (P2.4's fix round 1). Each step runs in its own `runCatching`, in that order; `redrawWidgets` is `refreshWidgets()`, Swift's `WidgetRefreshBridge` hook, so the widgets are reloaded before the watch's turn; `settleWatch` is last. The answer is whether the reschedule ran. `BackgroundRefreshBridgeTest` (iosTest) holds the order and the independence of the steps.
- **AppContainer:**
  - `internal val watchFeedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })`. `watchFeedMemory` (a `DataStoreWatchFeedMemory` over the settings store) and `watchFeedSync` are `internal` too. All three are declared *before* `notificationCoordinator`, because properties initialise in order.
  - `feedFor` reads the location (the argument, or `settingsRepository.location.first()`), `clockFormat` and `prayerSettings`, then `watchUiLanguage()` and `watchSpoken()`, one after the other in the same call, so the words and the language come from one resource environment. It builds the format with `createPlatformFormat(clockFormat)`, as the coordinator does, and the city with `watchCityName(location, format, cityRepository)`.
  - The phrases are `tasbeehStore.customPresets.first()`, `timesFor` is `prayerTimesEngine.timesFor`, and the result goes through `WatchFeedBuilder.build`. All of it runs on `work`, inside the budget.
  - `feedFor` and the link are kept as `internal val watchFeedFor` and `internal val watchLink = createWatchLink()`, which `watchFeedSync` is built from, so that Task 7's debug seam reaches the same two.
  - `report` prints `TaqwaWatch <reason> <result> <ms>ms`.
  - The coordinator gets `afterPlan = { watchFeedSync.request(RESCHEDULED, it) }`. `PrayerRoutes` passes `onPhrasesChanged = { container.watchFeedSync.request(PHRASES_CHANGED) }`.
  - `SystemEventReceiver` passes `settleWatch = { withTimeoutOrNull(WatchFeedSync.CALLER_BOUND) { appContainer.watchFeedSync.settle() } }` to `afterSystemEvent`, and so does `NotificationTopUpWorker` to `topUp`. `topUp` has no budget of its own, so that bound is the worker's.

- [ ] **Step 1: Write the failing tests.** `FakeWatchLink` records its calls, with a programmable `confirmsDelivery`, `reachable` and result, and an optional `CompletableDeferred` gate. Every fake suspends only by `delay` or on a `CompletableDeferred` (Global Constraints, "Tests on virtual time").
  - **`WatchFeedSyncTest`, on virtual time, fakes only** (rewritten to Ruling 50): `FakeWatchFeedMemory` holds the record in memory; `runTest`'s `backgroundScope` is the sync's `scope`, and `StandardTestDispatcher(testScheduler)` its `work`. Every "at N s" below is `testScheduler.currentTime`, read in the `report` callback, `fp(f)` is `watchFeedFingerprint(f)`, and "X confirmed" is X sent and, on a link that confirms, its delivery's echo acknowledged.
    - `anUnchangedFeedIsNotSentAgain`: on a link that does not confirm, as the iPhone's, two syncs whose feeds differ only in `writtenAt` give one send; the second answers `UNCHANGED`.
    - `aChangedFeedIsSent`.
    - Item 1: `eachSendIsWrittenDownBeforeTheLinkIsCalled` (inside the link's `send`, the record already names that number, the times alone after a `TOO_LARGE` included) and `aNumberIsNeverUsedTwice` (after a refusal, a `NO_WATCH`, a cut-off, a throw, a `TOO_LARGE` and a restart over the same record, the numbers are 1 to 7); `theDeliveryTravelsWithTheFeed` (each JSON decodes to its feed, beside the delivery `(origin, n, fp(feed))`).
    - Item 3: `aWatchComingBackWithTheFeedItHoldsIsNotSentItAgain`, `aPhraseChangeTheWatchAlreadyHoldsIsNotSentAgain` (Ruling 48's mutant MC), `theWatchsHandshakeWithItsCurrentFeedIsUnchangedAndRemembered` (on both links, the echo of the latest delivery, before any acknowledgement, is `UNCHANGED`, and the next reschedule sends nothing), `theWatchsHandshakeWithAnOlderFeedIsSent`, `aHandshakeWithoutADeliveryIsSent` (null, "", "xyz", a bare fingerprint, a delivery with a segment after it, another origin's) and `aReinstalledWatchIsHealedByItsOwnHandshake`.
    - Item 4: `aConfirmingLinkRemembersOnlyWhatTheWatchAcknowledged` (an acknowledgement of the first of two sends confirms nothing), `onTheIphoneASendThatDidNotAnswerSentConfirmsNothing` (refused, no watch, thrown, cut off: the next ask sends again), `nothingIsRememberedWhenNoWatchTookIt`, `aChangeUndoneBeforeItsAckIsSentAgain`, `aLateAckForAnOlderFeedCostsOneMoreSendNeverAWrongUnchanged` (Y then X sent and stored, X's ack then Y's: one more send of X, then `UNCHANGED`), `aSendCutOffByItsBudgetClaimsNothing` and `anAcknowledgementIsServedInTurn`.
    - Item 5 and A4: `anEchoAboveTheLastNumberLiftsIt` (a record restored to number 1 while the watch holds 3: the handshake is sent number 4, and the watch takes it) and `anEchoOfTheLastNumberWithAnotherFeedIsSentTheFeed` (a restored record uses number 2 again for T; the watch drops it and echoes 2 with Y; T goes as 3).
    - Item 6 and A3: `aChangedWatchForgetsFirst`, and `onTheIphoneAConfirmationCountsOnlyForItsWatch` (handed over to watch k0, then the session on k1: `WATCH_APPEARED` is sent; k0's echo and a keyless acknowledgement are ignored).
    - Over the limit: `aFeedTooLargeForTheLinkGoesAgainWithoutItsPhrases` (the times alone under the next number; a link that answers `TOO_LARGE` twice gives `FAILED`; two sends of 3 s each give `FAILED` at 5 s, the one budget), `anUnchangedFeedTooLargeForTheLinkIsNotSentAgain` and `aHandshakeNamingTheTimesStillOnTheirWayIsRemembered`.
    - Kept from Ruling 48: `noWatchBuildsNothing`, `noLocationSendsNothing`, `aFeedThatCannotBeWrittenIsNotSentAndNotThrown`, `theWholeSyncHasOneBudget`, `anAskWaitingItsTurnKeepsItsWholeBudget`, `theFeedIsBuiltOnTheSyncsOwnDispatcher`, `aLinkThatThrowsIsAFailureNotACrash`, `aStrayCancellationIsAFailureAndTheWorkerGoesOn`, `cancellingTheScopeStillStopsTheWorker`, `aStoppedWorkerNeverHoldsACaller`, `aSyncWhoseWorkerHasEndedAnswersAtOnce`, `aWorkerThatEndsWhileServingAnswersAtOnceAfterwards` (Ruling 50's "kept from Ruling 48", item 4), `asksAreServedInTheOrderMade`, `requestNeverWaitsForTheWorker` and `settleWaitsForWhatWasAsked`, as first written.
    - Ruling 50 deleted the tests that pinned Ruling 48's machinery alone: `aChangedWatchForgetsThePendingFeedToo`, `aChangedWatchForgetsTheFeedHandedOverToo`, `aRefusedSendLeavesTheFeedSentBeforeItTheLastOnItsWay` (its true rule is `aHandshakeAfterARefusedSendIsSentTheCurrentFeed`), `anAcknowledgementLandsEveryFeedSentBeforeIt`, `aLinkThatDoesNotConfirmTakesNoAcknowledgement` (now A3's), `aHandshakeNamingThePendingFeedIsRemembered` (folded into `theWatchsHandshakeWithItsCurrentFeedIsUnchangedAndRemembered`) and `theTimesAloneAreTheFeedOnceThePhrasesAreGone` (item 3 compares what the send stood for; the case is `aLateStandInAckAfterThePhrasesAreDeletedConfirmsNothingWrong`).
  - **`WatchFeedRecordTest`,** the record's pure rules: `aNewRecordKnowsOfNothing`, `aSendTakesTheNextNumber`, `theEchoOfTheLatestDeliveryConfirmsIt`, `theLastEchoCountsLowerOrHigher`, `anEchoOfNothingOrOfAnotherOriginClearsIt`, `anEchoSentBeforeAForgetConfirmsNothing` (D1, A2), `anEchoAboveTheLastNumberLiftsIt`, `anEchoOfTheLastNumberWithAnotherFeedIsNoConfirmation`, `theTimesAloneStandForTheFeedTheLinkRefused`, `aHandOverConfirmsItsNumberForThatWatch`, `anEchoCountsForTheWatchItCameFrom` and `aChangedWatchForgetsTheConfirmation`.
  - **`WatchFeedNamedCasesTest`** (Ruling 50 item 7): every named counterexample of Ruling 48 and of the two design checks as a plain scripted test, over `WorldLink` and `TestWatch`, each checking every `UNCHANGED` as it is answered and ending with the links quiet and each watch's own handshake leaving it on the current feed: `aHandshakeDuringAnUndoEndsWithTheWatchOnTheUndo` (both links, the + sent or cut off, with and without a restart), `aFeedStillInFlightAtAHandshakeCountsWhenItsAckArrives`, `aHandshakeAfterAnUndoThatDidNotGetThroughEndsWithTheWatchOnTheUndo` (refused, no watch, cut off before or after the hand-over, thrown before or after), `aRelaunchedPhoneWhoseUndoDidNotGetThroughEndsWithTheWatchOnTheUndo`, `anAckOvertakingAStaleHandshakeLeavesTheWatchOnTheUndo`, `anAbaWhoseLastSendIsLostIsSentAgain`, `aWatchHoldingAnOlderFeedIsSentTheFeedTooLargeForTheLink` (`SENT` on both links), `aWatchHoldingTheSameTimesForAnotherReasonIsStillSentTheStandIn`, `aLateStandInAckAfterThePhrasesAreDeletedConfirmsNothingWrong`, `twoFeedsDeliveredOutOfOrderLeaveTheWatchOnTheLater` (call 1's former "one case left", now closed), `aRelaunchedAndroidPhoneTakesTheWatchsWordForAFeedCutOff`, `aHandshakeAfterARefusedSendIsSentTheCurrentFeed`, `aSendThatMayHaveGotThroughStaysOnItsWayBesideTheOneBeforeIt`, `aRefusedResendLeavesTheFeedOnItsWay`, `aRestartWithAFeedInFlightEndsWithTheWatchOnTheUndo`; the design checks' `aSecondWatchSwitchedToIsSentTheFeedAtItsHandshake` (n17), `aWipedWatchThatTookALateOlderFeedIsSentTheCurrentOne` (n18), `aLateAckFromBeforeAWipeConfirmsNothingOnceTheWatchSaidItHoldsNothing` (D1, Ruling 50's review: no watch, no feed or cut off in the build, then a reschedule or a reconnect), `aStaleAckOvertakingAWipedWatchsNewerOneIsHealedByItsHandshake` (the known limit D1 leaves, its accepted transient marked as such), `aWatchThatMissedTheFeedTheOtherTookIsSentItAtItsHandshake` (n19), `aNewInstallStartsItsOwnNumbersAndTheWatchTakesThem` (n20), `anOldInstallsStragglerIsHealedByTheWatchsNextEcho`, `aRestoredRecordSendsUntilItPassesTheWatchsNumber` (n21), `aNumberUsedAgainForAnotherFeedIsNoConfirmation` (n22, N3), `anIphoneSwitchedToTheOtherWatchWhileTheAppWasNotRunningSendsTheFeed` (N1), `aQuickStartedIphoneSendsTheFeedToTheWatchItMovedWith` (N2), `aQuickStartThatCarriedTheWatchKeyIsHealedByTheWatchsHandshake` (N2b, its accepted transient marked as such) and `aChangedWatchIsSentTheFeedAgain`.
  - **`WatchFeedDeliveryModelTest`** (Ruling 50 item 7): a seeded randomized model of the real `WatchFeedSync` over a record in memory, both links and one or two watches storing by the contract's rule, with reordering, loss, duplicates, refusals, cut-offs before and after the hand-over, throws, phone restarts, Play services restarts, watch wipes, a second watch (nearby at once or switched to; on the iPhone, automatic switching with the change told only to a running app), new installs with stragglers, feeds over the limit, and, outside the documented platform, restored records and lost contexts. It asserts property S (an `UNCHANGED` to a handshake only while that watch holds the feed; any other only while the watch the phone heard from last, on the iPhone the session's watch, holds it; either way the watch may instead have been wiped after its last echo, unseen: after the echo the phone took in from it last and after every echo of nothing it took in from it, so a wipe the phone has heard of excuses nothing, whatever order the echoes arrive in (D1, Ruling 50's review); on the iPhone "holds" also counts the latest context handed over for that watch with a number above what it holds; the stragglers rider, unseen in the same way), L (once the links are quiet, each watch's own handshake leaves it on the current feed) and D (then a reschedule is `UNCHANGED`), S only on the documented platform. 960 seeded schedules, about half a second on the JVM and two on the simulator.
  - **`WatchFeedMemoryTest`,** a real DataStore built as `TasbeehStoreTest` builds one (`createWithPath`), with no budget and no timeout, order and outcome only: `theFirstReadMakesTheOriginOnce`, `anOriginIsARandomNumberOfTheContractsForm` (two stores, two origins), `theRecordIsWrittenAndReadBackWhole`, `aStoreWithoutAnOriginStartsAWholeNewRecord`, and `itSurvivesANewStoreOnTheSameFile`: the first store is built with `scope = CoroutineScope(Dispatchers.IO + Job())` and writes a record. That scope is cancelled and its job joined. Only then is a second store built on the same path, on its default scope, and it reads the record back. Without the cancel and the join, DataStore 1.1.7 throws "There are multiple DataStores active for the same file".
  - **`WatchFeedFingerprintTest`:**
    - `theHashIsFnv1a64`: `fnv1a64Hex` of the empty bytes is "cbf29ce484222325", and of "a" is "af63dc4c8601ec8c" (the published FNV-1a vectors);
    - `theFingerprintIgnoresWrittenAt`: two feeds that differ only in `writtenAt` share one fingerprint;
    - `aChangedFeedHasAnotherFingerprint`: one more phrase, or one minute moved on one day, gives another;
    - `itIsTheFormTheContractExpects`: for every feed these tests build, `WatchLinkContract.isFingerprint` holds for its fingerprint.
  - **Coordinator,** with a real `SettingsRepository` as the file's other tests build it and the default `planning`. These tests assert order and outcome, never a virtual instant:
    - `theWatchIsAskedAfterThePlanIsArmed`: with a recording `afterPlan`, the events are "armed", then "watch London".
    - `theLockIsFreeWhileTheWatchIsSlow`:
      - the `afterPlan` is `sync.request(RESCHEDULED, it)` on a real `WatchFeedSync` whose `scope` and `work` sit on a second `TestCoroutineScheduler`, whose `feedFor` takes 60 s by `delay`, and whose link's `send` waits on a deferred never completed;
      - two `reschedule`s back to back both return, and the scheduler armed twice;
      - only then the test advances the second scheduler by 10 s and runs what is due (`advanceTimeBy`, then `runCurrent`): both asks report `FAILED`, the first at 5 s and the second at 10 s, as the `report` callback reads that scheduler's `currentTime`.
    - `aWatchRequestThatThrowsNeverCostsTheReminders`: `afterPlan` throws `IllegalStateException`, yet `reschedule` returns the plan and the scheduler armed it once.
    - `withNoLocationTheWatchIsNotAsked`.
  - **Tasbeeh:**
    - `addingEditingOrRemovingAPhraseTellsTheWatch`: three calls.
    - `theWatchIsToldAfterTheChipIsShown`: inside the callback, `state.value.custom` already holds the new phrase.
  - **`SystemEventReceiverTest.theWidgetsAreRedrawnWhileTheWatchLinkHangs`,** on virtual time, fakes only. `afterSystemEvent` runs under `withTimeout(8 000)` with:
    - `reschedule` a fake that records "armed" and calls `sync.request(RESCHEDULED, london)`, on a real `WatchFeedSync` in `backgroundScope` whose link hangs and whose `feedFor` takes 3 s;
    - recording fakes for the mirror, the widgets and the ayah;
    - that sync's `settle` as the last step.

    Expected: the events are [armed, mirror, widgets, ayah], all at virtual time 0; `settle` returns at 5 s, when the ask's budget ends; the call returns before 8 s.
  - **`NotificationTopUpWorkerTest.theWatchIsSettledLastAndNeverCostsTheWidget`:** with fakes, the order is reschedule, mirror, redraw, settle. A `settleWatch` that throws still gives `success()`.
  - **`NotificationTopUpWorkerTest.aStoppedWorkerCostsTheTopUpOnlyItsBound`,** on virtual time: `settleWatch` is the wiring's own lambda, `withTimeoutOrNull(CALLER_BOUND) { sync.settle() }`, over a `WatchFeedSync` whose scope was cancelled. `topUp` gives `success()` at 10 s at the latest.
- [ ] **Step 2:** Run `./gradlew :watchcore:testAndroidHostTest :shared:testAndroidHostTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the code.
- [ ] **Step 4:** Run `scripts/test.sh` twice. Expected: PASS both times, and every existing coordinator, receiver, worker and tasbeeh test is unchanged.
- [ ] **Step 5: Commit** with the message "Watch feed: asked after every reschedule, every phrase change and every return of the watch, served in order off the reminders' path, inside one budget, remembered only once the watch has it, and never sent again to a watch that holds it".

### Task 5: The Android sender over the Data Layer, to directly connected watches only

Ruling 39 accepted the relay for Android, so this task and Tasks 6–7 are built as written.

**Files:**
- Modify:
  - `gradle/libs.versions.toml`: `play-services-wearable = "20.0.1"`, alias `play-services-wearable`. First check Google's Maven `maven-metadata.xml` for a newer stable, and take it if there is one; the checks below run either way.
  - `shared/build.gradle.kts`: androidMain gets `implementation(libs.play.services.wearable)`.
  - `androidApp/src/main/AndroidManifest.xml`: the listener service. Also the allowBackup comment (lines 45-51), which quotes "your location never leaves your phone": it quotes "Only your own devices can see your location" instead (Ruling 39, item 5 of its list), and its reasoning for leaving backup off stands. No attribute changes.
  - `androidApp/src/main/res/xml/data_extraction_rules.xml`: its comment (lines 2-7), the same way. The rules themselves do not change.
- Create:
  - `androidApp/src/main/res/values/wear.xml`: `android_wear_capabilities` = `taqwa_phone`, marked `tools:keep`.
  - `shared/src/androidMain/kotlin/world/taqwa/app/watchfeed/WearCodes.kt`
  - `shared/src/androidMain/kotlin/world/taqwa/app/watchfeed/PhoneWatchListenerService.kt`
  - `docs/superpowers/reports/2026-10-23-wearable-library-check.md`
- Replace: `shared/src/androidMain/kotlin/world/taqwa/app/watchfeed/WatchLink.android.kt`.
- Test: `shared/src/androidHostTest/kotlin/world/taqwa/app/watchfeed/AndroidWatchLinkTest.kt` and `WearCodesTest.kt`, with the test helpers `FakeTask.kt` and `TaskWatchNodes.kt` beside them.

**Interfaces:**
- Consumes `WatchLinkContract` (Task 3) and `WatchFeedSync` (Task 4).
- Produces in `WearCodes.kt`, all `internal`, each parameter named for the code space it is read in (Global Constraints, "Transport, Android"):
  - `fun noPlayServices(availability: Int): Boolean = availability != ConnectionResult.SUCCESS`, for `GoogleApiAvailability.isGooglePlayServicesAvailable`'s answer;
  - `fun noWearApi(statusCode: Int): Boolean = statusCode == CommonStatusCodes.API_NOT_CONNECTED`, for `ApiException.statusCode`;
  - `fun nodeGone(statusCode: Int): Boolean = statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED`, for `sendMessage`'s `ApiException.statusCode`;
  - `class TaskCancelled : Exception()`, and `fun <T> outcome(isCanceled: Boolean, exception: Exception?, result: () -> T): T`. It throws `TaskCancelled` for a cancelled task and `exception` for a failed one. Only otherwise does it call `result()`.
    - The result is read lazily because Play services' `Task.getResult()` throws for a task that did not succeed. A failed task's getter throws `RuntimeExecutionException` around the real error, which would hide the `ApiException` 17 from `noWearApi`, so most phones would log `FAILED` instead of `NO_WATCH`. A cancelled task's getter throws `java.util.concurrent.CancellationException`, the very throw that must never reach the sync's worker.
    - `internal suspend fun <T> Task<T>.await(): T` is a `suspendCancellableCoroutine`, never a `suspendCoroutine`. It adds one `OnCompleteListener` on a direct executor (no main looper), which resumes the continuation with `runCatching { outcome(task.isCanceled, task.exception) { task.result } }`. It never throws a `CancellationException` for a cancelled task, and never has to construct an `ApiException`.
    - Being cancellable is what lets a budget cut it (Global Constraints, "The reminders come first"): a `getCapability` or `sendMessage` that never answers ends at the ask's 5 s, and the worker serves the next ask. A Play services `Task` has no way to remove a listener, so a completion after the cut reaches a cancelled continuation, which ignores it: nothing resumes and nothing throws.
- Produces, in `shared/src/androidMain`, all `internal`:
  - `data class WatchNode(val id: String, val nearby: Boolean)` and `fun directlyConnected(nodes: List<WatchNode>): List<String>`;
  - `enum class NodeSend { QUEUED, GONE }`;
  - `interface WatchNodes { suspend fun watches(): List<WatchNode>?; suspend fun send(nodeId: String, path: String, data: ByteArray): NodeSend }`. `watches()` answers null when there is no Wear API on this phone. `send` answers `GONE` when the node went away or the API did, and throws for any other failure.
  - `class PlayWatchNodes(context: Context) : WatchNodes`:
    - It first checks `noPlayServices(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context))` (public API only, Ruling 54 item 1), which makes `watches()` null. It never calls anything that can show UI or post a notification.
    - Then it calls `CapabilityClient.getCapability(WATCH_CAPABILITY, FILTER_REACHABLE)`. An `ApiException` for which `noWearApi(e.statusCode)` holds is null, and its `status.connectionResult?.errorCode` is printed (`TaqwaWatch no Wear API <code>`). Any other throw propagates, and the sync answers `FAILED`.
    - `send` is `MessageClient.sendMessage`; an `ApiException` for which `nodeGone` or `noWearApi` holds is `GONE`.
  - `class AndroidWatchLink(nodes: WatchNodes) : WatchLink`, with `actual fun createWatchLink(): WatchLink = AndroidWatchLink(PlayWatchNodes(appContext))`. It is internal, as its parameter's type is.
    - `confirmsDelivery` is true (the Lead's call 1).
    - `oneAsk` holds the ask's one reading, `directlyConnected(nodes.watches() ?: emptyList())`, taken at its first need inside the ask's budget and kept in the ask's coroutine context, so it never outlives the ask (Ruling 54 item 4). Its first need is the sync's step 1, before the build, so step 4 judges by the watches nearby then: a watch switched to during the build is call 1's known limit, healed at its own capability change once the old watch has left, otherwise at its handshake (Ruling 55 item 1). `reachable()` is that reading `.isNotEmpty()`, and `watchKeys()` its ids. Outside an ask each reads for itself.
    - `send` asks `watches()` again, so the nearby flag is read as close to the send as the API allows, then sends `json.encodeToByteArray()` on `WatchLinkContract.feedPath(delivery)` to each id from `directlyConnected`, each in its own `try`, so one node's throw never stops the next. Any node `QUEUED` makes the result `SENT`. Otherwise, no ids or every node `GONE` is `NO_WATCH`, and any node that threw is `FAILED`. The ask's own cut passes through the `catch` (`ensureActive()`), so no further node is called after it.
    - So an ask makes one capability query when it sends nothing (no watch, no feed, a feed that cannot be encoded, or one the watch holds) and two when it sends; never three, since this link never answers `TOO_LARGE`. An acknowledgement makes none. The KDoc says so.
- Produces `internal class PhoneWatchListenerService : WearableListenerService`:
  - `onMessageReceived` on `REFRESH_PATH` runs `runBlocking { withTimeoutOrNull(WatchFeedSync.CALLER_BOUND) { appContainer.watchFeedSync.sync(WATCH_ASKED, have = data.decodeToString()) } }`;
  - `onMessageReceived` on `ACK_PATH` runs `runBlocking { withTimeoutOrNull(WatchFeedSync.CALLER_BOUND) { appContainer.watchFeedSync.acknowledge(data.decodeToString()) } }`, and prints `TaqwaWatch ACK <loggedDelivery(echo)>`, the number and fingerprint of the delivery the watch now holds (Ruling 50), never its origin (Ruling 52 item 4);
  - `onCapabilityChanged` for `WATCH_CAPABILITY`, with at least one nearby node, runs `runBlocking { withTimeoutOrNull(WatchFeedSync.CALLER_BOUND) { sync(WATCH_APPEARED) } }`, which the record damps (the Lead's call 1).
  - So no body holds the service's thread past 10 s, even with the sync's worker gone. Each runs in `bounded(bound = CALLER_BOUND, body)`, an internal function a test calls with a shorter bound (Ruling 54 item 5).

  Each body catches `Exception`, as every call into the watch side does. The manifest declares it exported, with three intent filters, each on the `wear` scheme and `android:host="*"` and each on one literal `android:path`, never `pathPrefix`, so Play services wakes the service only for these (Ruling 54 item 2):
  - `com.google.android.gms.wearable.MESSAGE_RECEIVED`, `path="/taqwa/refresh"`;
  - `com.google.android.gms.wearable.MESSAGE_RECEIVED`, `path="/taqwa/ack"`;
  - `com.google.android.gms.wearable.CAPABILITY_CHANGED`, `path="/taqwa_watch"`.

  `PhoneWatchListenerServiceTest`, a `:shared` host test whose Gradle inputs include both files, holds the three filters (action, scheme, host and one literal path each) and `res/values/wear.xml`'s `android_wear_capabilities` (`taqwa_phone`, kept) to `WatchLinkContract`'s `REFRESH_PATH`, `ACK_PATH`, `WATCH_CAPABILITY` and `PHONE_CAPABILITY` and the library's `MessageClient.ACTION_MESSAGE_RECEIVED` and `CapabilityClient.ACTION_CAPABILITY_CHANGED`, so neither side can drift silently.

- **As built (25 October, Ruling 52 item 9 and two test seams):**
  - **The watch key is the Wear node id.** `AndroidWatchLink.watchKey()` is null (a send goes to every nearby watch, not one), and `watchKeys()` (Task 4's link, a new member with the default `setOf(watchKey())`, which keeps the iPhone as it was) is `directlyConnected(nodes.watches())`, one capability query as first built, and since Ruling 54 item 4 the ask's one reading. The listener passes each message's `sourceNodeId` as `from`, to `sync(WATCH_ASKED, have, from)` and to `acknowledge(echo, from)` (Task 4's `acknowledge` gains `from`). So `confirmedFor` is the node whose echo was taken in last, every echo is taken in (one from a watch no longer nearby among them: its own word, so a wipe heard from afar is never lost), and an ask whose nearby nodes do not include `confirmedFor` sends. `WatchFeedRecord` gains `holds(fingerprint, keys)` and `echoed(echo, from, keys)`; the one-key forms are those with `setOf(key)`.
  - **`PlayWatchNodes`** takes its three Play services calls as functions (`availability`, `capability`, `message`), and `PlayWatchNodes(context)` makes them `GoogleApiAvailability`'s (Ruling 54 item 1; `GoogleApiAvailabilityLight`'s as first built) and `Wearable`'s. `TaskWatchNodes` is that very class over the test's tasks, so the real `await()` and the real reading of each `ApiException` are on the sync's path.
  - **The listener's bodies** are `takeWatchMessage(sync, path, data, from)` and `takeCapabilityChange(sync, name, nodes)`, which the service calls inside `runBlocking { withTimeoutOrNull(CALLER_BOUND) { … } }` and a `catch (Exception)`, so a test holds the routing over a sync of its own.
  - **Tests added beyond Step 2's:** `AndroidWatchLinkTest.theWatchKeyIsTheNodeIdOfTheWatchThatEchoed`, `aSwitchedWatchNeverInheritsAnotherWatchsConfirmation`, `anEchoFromAWatchNotNearbyCountsOnlyWhileItIsNearby`, `aSecondWatchBesideTheFirstIsSentTheFeedAtItsOwnHandshake` (call 1's known limit, healed) and `theListenerServesEachMessageForTheWatchItCameFrom`; `WatchFeedRecordTest.aConfirmationCountsOnlyWhileItsWatchIsAmongTheSessions` and `anEchoFromAWatchOutsideTheSessionsIsIgnored`; `WatchFeedSyncTest.anAcknowledgementIsTakenInFromTheWatchItCameFrom`. `WatchFeedDeliveryModelTest` names each Android watch by its node id, gains the configuration "android, two watches switched" (the worn watch away as the other comes nearby, often once the links are quiet), and (S) also requires the watch heard from last to be nearby at every `UNCHANGED` that is not its own handshake's.
- **Ruling 54 (P2.5's review notes, 25 October):**
  - public API only: `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)` replaces `GoogleApiAvailabilityLight`'s, and no update prompt is shown, as before. `WearCodesTest.playServicesIsAskedThroughItsPublicApiAndNothingCanShow` reads the compiled classes of `world.taqwa.app.watchfeed` (their constant pools, `ClassReferences`): they refer to `GoogleApiAvailability.getInstance` and `isGooglePlayServicesAvailable`, to nothing of `GoogleApiAvailabilityLight`, and to no call that shows a dialog or a notification or asks for an update;
  - the manifest tied to the contract: the two message filters are literal `android:path`s, and `PhoneWatchListenerServiceTest.theManifestWakesTheListenerForTheContractsPathsAlone` and `thePhonesCapabilityIsTheContractsAndKept` hold them and `wear.xml` to `WatchLinkContract`;
  - two mutants killed: `AndroidWatchLinkTest.aCapabilityQueryThatFailsOtherwiseIsAFailure` (G1: `CANCELED` 16, `INTERNAL_ERROR`, `TIMEOUT`, `NETWORK_ERROR` and `ERROR` from `getCapability` are thrown as themselves and `FAILED`, never `NO_WATCH`) and `aSendCutByTheBudgetCallsNoFurtherNode` (G2: two nearby nodes, the first's message never answers, the budget cuts it at 5 s and the second is never called);
  - one capability reading per ask: `WatchLink.oneAsk`, run by the sync around each ask inside its budget; `AndroidWatchLinkTest.anAskReadsTheWatchesOnceAndAgainAtTheSend` (one query without a send, two with one, none for an acknowledgement, a fresh reading at the next ask) and `aWatchGoneByTheSendOfAnAskIsNotSent`. Its review's round 1 found the reading taken before the build, so a watch switched to during the build is answered `UNCHANGED` at that ask: kept as call 1's known limit, since this item rules the shared reading (ratified by Ruling 55 item 1), and documented in `WatchFeedSync` step 4, `AndroidWatchLink`, Review Focus 2 and the spec, and held by `aWatchSwitchedToDuringTheBuildIsSentTheFeedAtItsOwnNextAsk` (`UNCHANGED`, then `SENT` to the new watch at its capability change, the old watch having left, and at its handshake);
  - `PhoneWatchListenerServiceTest.aContainerThatThrowsCostsTheListenerThatMessageAlone` and `aSyncWhoseWorkerIsGoneHoldsTheListenerNoLongerThanItsBound` (a cancelled scope, and a worker that never serves under an hour's caller bound, each body cut by the wrapper's own bound, on a thread of its own);
  - the largest feed is a shared fixture: `WorstFeeds.largestFeed` (commonTest) builds it for each platform's format, spoken words and day, and `CapeTownSample.largestFeed(tag, kind)` (androidHostTest) is Android's, on the committed sample's days. `WatchFeedSampleTest`, `AndroidWatchLinkTest` and `IosWatchLinkTest.theLargestContextFitsTheSmallestLimit` all build it there, and no test class builds another;
  - `WatchFeedSync.acknowledge`'s KDoc says what an iPhone acknowledgement with no key comes to (nothing calls it there; it would be ignored while the session's watch has a key and taken in while it has none), and `WatchFeedSyncTest.onTheIphoneAnAcknowledgementWithNoKeyIsAKeylessWatchsWord` holds it.
- **Ruling 55 items 1 and 2 (Ruling 54's review notes, 29 October), no behaviour change:**
  - the known limit of a watch switched to during the build is ratified as built (option (a)). Its capability change heals it once the old watch has left; otherwise the old watch's confirmation still counts there, and its handshake does. `WatchFeedSync` step 4, `AndroidWatchLink`, `WatchLink.oneAsk`, call 1, Review Focus 2, this task's interfaces and spec 1.2 say so, and `WatchFeedRecord.holds` and the library check report say "between asks";
  - the stronger handshake case: `AndroidWatchLinkTest.aWatchSwitchedToDuringTheBuildHoldingAnOlderDeliveryIsSentTheFeedAtItsHandshake`, where B, worn before, echoes an older delivery of its own (an older feed, number 1 of 2), not nothing. The record takes it in for B at that number, and B is sent the feed;
  - `PhoneWatchListenerServiceTest.aContainerThatThrowsCostsTheListenerThatMessageAlone` also throws an `UninitializedPropertyAccessException` and a checked `IOException`, so `bounded()` narrowed to `IllegalStateException`, or to `RuntimeException`, fails; and `theServiceReadsTheContainerInsideTheWrapper` runs the real service's `onMessageReceived` and `onCapabilityChanged` in a host test, where `appContainer` cannot be built (no `appContext`), so a read of it outside `bounded` throws on the service's thread and fails.

- [ ] **Step 1: Record the baseline** before touching the catalog:
  - `./gradlew :androidApp:dependencies --configuration releaseRuntimeClasspath > <scratch>/deps-before.txt`;
  - the `uses-permission` lines of `androidApp/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml` after `./gradlew :androidApp:processReleaseMainManifest`. Expected: 12.
  - The debug APK's packages and the tracker scan, with the same commands Step 5 runs after the library:
    ```bash
    ./gradlew :androidApp:assembleDebug
    "$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" dex packages --defined-only \
      androidApp/build/outputs/apk/debug/androidApp-debug.apk \
      | awk '$1=="P"{print $NF}' | sort -u > <scratch>/packages-before.txt
    grep -E 'firebase|crashlytics|analytics|measurement|appmeasurement|gms\.ads|adservices|privacysandbox' \
      <scratch>/packages-before.txt > <scratch>/trackers-before.txt
    ```
    Expected: `trackers-before.txt` is **not** empty. It holds at least media3's `androidx.media3.exoplayer.analytics` (Global Constraints, "The library"), and maybe `android.adservices` packages as the watch's APK does. The report lists each line with the library or build step it comes from. A line no library of today's explains is reported to the Lead before the catalog changes.
- [ ] **Step 2: Write the failing tests** (`FakeWatchNodes`; every code is the library's own constant, never a literal):
  - `WearCodesTest.eachCallIsReadInItsOwnCodeSpace`:
    - `noPlayServices` is false for `ConnectionResult.SUCCESS`, and true for `SERVICE_MISSING`, `SERVICE_VERSION_UPDATE_REQUIRED`, `SERVICE_DISABLED`, `SERVICE_INVALID` and `SERVICE_UPDATING`;
    - `noWearApi` is true for `CommonStatusCodes.API_NOT_CONNECTED` alone. It is false for `CommonStatusCodes.CANCELED`, `TIMEOUT`, `NETWORK_ERROR`, `INTERNAL_ERROR` and `ERROR`, and for `WearableStatusCodes.TARGET_NODE_NOT_CONNECTED`;
    - `nodeGone` is true for `WearableStatusCodes.TARGET_NODE_NOT_CONNECTED` alone;
    - and, as the reason the spaces are never mixed: `ConnectionResult.API_UNAVAILABLE == CommonStatusCodes.CANCELED` (both 16), yet `noWearApi(CommonStatusCodes.CANCELED)` is false.
  - `aCancelledTaskIsAnErrorNotACancellation`: `outcome(true, null) { throw java.util.concurrent.CancellationException() }` throws `TaskCancelled`, which is not a `CancellationException`; `outcome(false, e) { throw RuntimeExecutionException(e) }` throws `e` itself; `outcome(false, null) { 1 }` is 1.
  - `FakeTask<T>(canceled: Boolean, exception: Exception?, result: T?) : Task<T>` (test helper) overrides `Task`'s abstract members and `addOnCompleteListener(Executor, OnCompleteListener)`, which calls the listener at once. Its `getResult()` throws as Play services' does: `RuntimeExecutionException(exception)` for a failed task and `java.util.concurrent.CancellationException` for a cancelled one. `FakeTask.never<T>()` is one that never completes: `isComplete` false, and `addOnCompleteListener` keeps the listener and never calls it; `complete(result)` later makes it successful and calls the kept listener. Then, through the real `await()`:
    - `aFailedTaskAwaitsToItsOwnException`: a task failed with `ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED))` throws that `ApiException`, never a `RuntimeExecutionException`, and `noWearApi` holds for its `statusCode`. (If the host JVM's android.jar stubs refuse to build a `Status`, the task fails with an `IllegalStateException` instead, the assertion is that the same instance comes out, and the report says so.)
    - `aCancelledTaskAwaitsToTaskCancelled`: a cancelled task throws `TaskCancelled`;
    - `aSuccessfulTaskAwaitsToItsResult`.
    - `aTaskThatNeverCompletesIsCutByTheBudget`, under `runTest`: `withTimeoutOrNull(5.seconds) { FakeTask.never<Int>().await() }` is null, and `testScheduler.currentTime` is then 5,000. Then `complete(1)` on that task calls the kept listener, and nothing throws: the cancelled continuation ignores it.
  - `TaskWatchNodes` (test helper): a `WatchNodes` built as `PlayWatchNodes` is, over tasks the test hands it, so the real `await()` is on the sync's path. `watches()` is `capabilityTasks.removeFirst().await()`, and `send` is `sendTasks.removeFirst().await()`, then `QUEUED`.
  - `aWatchQueryThatNeverAnswersIsCutAndTheNextAskIsServed`: a `WatchFeedSync` on virtual time, as Task 4's tests build it (`backgroundScope`, `StandardTestDispatcher(testScheduler)`, the `report` callback reading `currentTime`), over `AndroidWatchLink(TaskWatchNodes(…))`:
    - the first ask's capability task is `FakeTask.never()`: `FAILED` at 5 s;
    - the second ask's capability tasks answer a nearby node at once and its send task completes at once: `SENT`, at 5 s;
    - a third ask whose capability tasks answer at once and whose send task is `FakeTask.never()`: `FAILED` at 10 s, 5 s after it was served;
    - a fourth, answered at once, is `SENT`. So a `getCapability` or a `sendMessage` that never answers never holds the worker.
  - `withoutPlayServicesNothingIsAskedOrShown`: `watches()` null makes `reachable()` false and `send` `NO_WATCH`, and `send` on the nodes is never called.
  - `theWearApiMissingIsNoWatchNotAFailure`: a `WatchFeedSync` over an `AndroidWatchLink` whose `watches()` answers null gives `NO_WATCH`, with nothing built.
  - `aCancelledCapabilityQueryIsAFailure`: the same sync, with `watches()` throwing `TaskCancelled`, answers `FAILED`, never `NO_WATCH`, and serves the next ask.
  - `theLinkConfirmsDelivery`: `AndroidWatchLink(…).confirmsDelivery` is true.
  - `noWatchWithTaqwaIsNoWatch`.
  - `aWatchReachedOnlyThroughTheCloudIsNotSent`: `nearby = false` is excluded, so the result is `NO_WATCH`.
  - `nearbyIsAskedAgainAtTheSend`: `watches()` gives a nearby node the first time and only a cloud-reached one the second. So `reachable()` is true, `send` is `NO_WATCH`, and no message goes.
  - `aNodeThatLeftAtTheSendIsNoWatch`: every node answers `GONE`, so the result is `NO_WATCH`, not `FAILED`.
  - `theFeedGoesToEveryNearbyWatchOnTheFeedPath`: two nodes get two sends on `WatchLinkContract.feedPath(delivery)`, the delivery `send` was given, with the bytes equal to the JSON in UTF-8.
  - `aFeedAtTheBudgetIsWithinTheMessageLimit`: for the largest feed of Task 3's `theLargestFeedStaysWithinItsBudget` (bn-BD on 12 hours with `WorstFeeds.presets(CONTROL)`, built the same way), the bytes the node receives are the JSON's UTF-8, at most `FEED_BUDGET_BYTES`, and so, with `ENVELOPE_BYTES`, within `MESSAGE_LIMIT_BYTES`.
  - `oneWatchThatTookItIsSent`: one node `QUEUED` and one that throws give `SENT`, and both were tried.
  - `aThrowingNodeIsAFailureNotACrash`: a lone node that throws gives `FAILED`, and the next ask is served.
- [ ] **Step 3:** Run `./gradlew :shared:testAndroidHostTest --tests '*AndroidWatchLinkTest*' --tests '*WearCodesTest*'`. Expected: FAIL.
- [ ] **Step 4:** Implement the code, then run `scripts/test.sh`. Expected: PASS.
- [ ] **Step 5: Re-verify the library** and write the report: the commands, their outputs and the verdict.
  - Record the dependency diff (`deps-before.txt` against the same command now). It should hold:
    - the four Google libraries;
    - `androidx.fragment:fragment`, `androidx.loader:loader` and `androidx.viewpager:viewpager`;
    - any other AndroidX artifact the diff shows, each one listed with the library that pulled it in.

    A library outside `com.google.android.gms` and `androidx`, or a new artifact whose name matches Step 1's tracker pattern, fails the check.
  - The 12 permissions are unchanged.
  - The merged release manifest's new entries are only `GoogleApiActivity` (`exported="false"`), the `com.google.android.gms.version` meta-data and `PhoneWatchListenerService` with its three filters.
  - `./gradlew :androidApp:assembleDebug`, then Step 1's `apkanalyzer` and `grep` lines into `packages-after.txt` and `trackers-after.txt`. The debug APK is unminified, so it is a superset of the release.
    - Expected: `comm -13 <scratch>/trackers-before.txt <scratch>/trackers-after.txt` prints nothing: the library adds no package matching the tracker pattern. A line in the before-list alone is fine. **Any line it prints must either be absent from the release dex or be explained in the report** (Ruling 53 item 1); otherwise it fails the check. The release proof is `apkanalyzer dex packages --defined-only --proguard-mappings <mapping.txt>` over the dex `:androidApp:minifyReleaseWithR8` writes (`androidApp/build/intermediates/dex/release/minifyReleaseWithR8/`, zipped as it is, never signed), with the same `awk` and `grep`. As built (25 October): the one line, `com.google.firebase`, is `play-services-basement`'s three exception classes, and R8's release holds none of them.
    - `comm -13 <scratch>/packages-before.txt <scratch>/packages-after.txt`, every package the library added, goes in the report, each with the library it comes from.
  - `./gradlew :androidApp:minifyReleaseWithR8` succeeds, so the release still shrinks.

  The verdict rests on the permissions, the manifest and the tracker diff, never on how many libraries were added.
- [ ] **Step 6: Commit** with the message "Watch feed: the Android phone sends it over the Wear Data Layer, to watches directly connected at the send, each answer read in its own code space".

### Task 6: The Wear receiver, its acknowledgement, the handshake and the daily nudge

**Files:**
- Modify:
  - `wearApp/build.gradle.kts`: `implementation(libs.play.services.wearable)`.
  - `wearApp/src/main/AndroidManifest.xml`: the listener service and the nudge receiver. Rewrite the permissions comment, because Play services now joins and the times no longer travel "over the Data Layer and nowhere else". In the allowBackup comment, "your location never leaves your phone" becomes "Only your own devices can see your location" (Ruling 39, item 5 of its list). No attribute changes.
  - `wearApp/src/main/res/xml/data_extraction_rules.xml`: its comment, the same way. The rules themselves do not change.
  - `wearApp/src/main/kotlin/world/taqwa/app/wear/FeedStore.kt`: the delivery the phone sent (origin, number, fingerprint, Ruling 50), kept beside the feed, and the store taking only what moves it forward.
  - `wearApp/src/main/kotlin/world/taqwa/app/wear/TaqwaWearApp.kt`
  - `wearApp/src/main/kotlin/world/taqwa/app/wear/StatusActivity.kt`
  - `wearApp/src/debug/AndroidManifest.xml`: the debug nudge receiver, exported, with a comment beside `DebugFeedReceiver`'s that says why.
  - The two test-compile warnings in `:wearApp`'s tests (ledger L460), wherever `./gradlew :wearApp:compileDebugUnitTestKotlin` prints them.
- Create:
  - `wearApp/src/main/res/values/wear.xml`: `android_wear_capabilities` = `taqwa_watch`, marked `tools:keep`.
  - In `wearApp/src/main/kotlin/world/taqwa/app/wear/link/`: `FeedListenerService.kt`, `FeedReceiver.kt`, `RefreshPolicy.kt`, `PhoneAsk.kt`, `WearCodes.kt` and `DailyNudge.kt`.
  - `wearApp/src/debug/kotlin/world/taqwa/app/wear/link/FeedDrop.kt` and `wearApp/src/release/kotlin/world/taqwa/app/wear/link/FeedDrop.kt`: Task 7's dropped delivery and held acknowledgement, seams in the debug build only.
  - `wearApp/src/debug/kotlin/world/taqwa/app/wear/link/DebugNudgeReceiver.kt`: Task 7's daily handshake on demand, in the debug build only.
- Test:
  - in `wearApp/src/test/kotlin/world/taqwa/app/wear/link/`: `FeedReceiverTest.kt`, `RefreshPolicyTest.kt`, `PhoneAskTest.kt`, `WearCodesTest.kt` and `NoEngineOnTheWatchTest.kt`;
  - `wearApp/src/test/kotlin/world/taqwa/app/wear/FeedStoreTest.kt` (add);
  - in `wearApp/src/testDebug/kotlin/world/taqwa/app/wear/link/`, beside `SampleFeedTest`'s source set, because they test debug-only code: `FeedDropTest.kt` and `DebugNudgeTest.kt`.

**Interfaces:**
- Consumes `FeedStore.write`, `FeedStore.read`, `FeedStore.changes` and `FeedWriteResult` (Ruling 6), `newerVersionSeen` and its persisted notice (Ruling 13), `WatchRules.needsRefresh`, `ComplicationSources.requestUpdates`, and `WatchLinkContract` as Task 3 and Ruling 50 left it: `WatchDelivery`, `isFeedPath`, `deliveryIn` (the delivery a feed's path carries), `deliveryOf` (a delivery's text read back), `stores` (the receivers' rule), `echoOf` (what the watch says back) and `STORE_STEPS` (the order it stores in). It never uses `fingerprintIn`, which Ruling 50 removed from the contract: a fingerprint alone names no delivery. The watch never computes a fingerprint: `watchFeedFingerprint` is in `:shared`, which `:wearApp` does not link.
- Produces, on `FeedStore` (Ruling 50: the receivers' rules are `WatchLinkContract`'s, built on, never copied), `fun write(json: String, delivery: WatchDelivery? = null): FeedWriteResult`, `fun delivery(): WatchDelivery?` and `internal val deliveryFile: File` (`file.path + ".delivery"`):
  - With a delivery, the store first asks `WatchLinkContract.stores(delivery(), delivery)`. When it says no (the same or a lower number of the origin held: stale or a duplicate), the write is `STALE`, a new `FeedWriteResult`, and nothing is touched: the feed, its delivery and the notice stay as they are, and nothing is acknowledged.
  - On `ACCEPTED` it follows `WatchLinkContract.STORE_STEPS`: the delivery file is deleted before the feed is renamed into place; afterwards the delivery is written the way the feed is (a temporary file, then a rename).
    - So a crash or an `IOException` in between leaves no delivery, and never one that names another feed. The handshake is then empty, and the phone sends once more.
    - An `IOException` on the delivery's write is swallowed: the feed is stored, nothing past that may fail the write, and the watch holds no delivery.
    - A greater number for the very feed held (the same fingerprint) is `ACCEPTED` too, its delivery written anew, so that the echo names the newer number; `ComplicationSources.requestUpdates` may be skipped for it.
  - On `NEWER_VERSION` and `MALFORMED`, the delivery stays the stored feed's.
  - `delivery()` reads the file through `WatchLinkContract.deliveryOf`. It answers null when the file is missing or unreadable, or holds anything `deliveryOf` refuses.
  - Plan 1's callers (`SampleFeed`, `DebugFeedReceiver`) pass none: the sample is stored whatever the watch holds, and its delivery file deleted first, so a sample never carries a phone's delivery. Plan 1's `FeedStoreTest` passes unchanged.
- Produces `internal data class Received(val result: FeedWriteResult, val echo: String?)` and `internal object FeedReceiver { fun onMessage(path: String, data: ByteArray, store: FeedStore): Received? }`.
  - It answers null for any path `isFeedPath` refuses, for a feed path `WatchLinkContract.deliveryIn` reads no delivery from (no phone's: nothing stored, nothing acknowledged), and for an `IOException`, which it logs.
  - Otherwise it answers `store.write(data.decodeToString(), deliveryIn(path))`. `echo` is `WatchLinkContract.echoOf(store.delivery())` when the result is `ACCEPTED`, and null otherwise: a stale, newer or broken feed is never acknowledged.
- Produces in `WearCodes.kt` the watch's own `noPlayServices(availability: Int)`, `noWearApi(statusCode: Int)`, `TaskCancelled`, `outcome(isCanceled, exception, result: () -> T)` and `Task.await()`, each as Task 5 defines it, over the library's own constants: the result read only for a task that succeeded, and `await()` a `suspendCancellableCoroutine`. That is what lets `FeedListenerService`'s 5 s and `PhoneAsk`'s and `DailyNudge`'s 10 s bounds fire when Play services never answer. `:wearApp` has no `:shared`, so these lines are the watch's copy.
- Produces `class FeedListenerService : WearableListenerService`:
  - `onMessageReceived` on a feed path: when `FeedDrop.takeOne(filesDir)` is true, it logs `TaqwaWearFeed DROPPED` and does nothing more. Otherwise it hands the message to `FeedReceiver` with `(application as TaqwaWearApp).feedStore` and logs `TaqwaWearFeed <result>`.
  - **Its log lines, and every `TaqwaWearFeed` line of the release build** (Ruling 52 item 4): a delivery is named by its number and fingerprint alone, `<seq>/<fingerprint>` (`"${d.seq}/${d.fingerprint}"`), and an echo of nothing as `none`. Never its origin (the install's random number, the same for every feed of that phone), the place or the bearing. Only the debug build's seams (`FeedDrop`, `DebugNudgeReceiver`, `DebugFeedReceiver`) may log an origin.
  - With an echo, it sends that echo in UTF-8 on `ACK_PATH` to `messageEvent.sourceNodeId`, through `MessageClient.sendMessage`. That runs inside `runBlocking { withTimeoutOrNull(5.seconds) { … } }` and catches `Exception`, and it logs `TaqwaWearFeed ACK <seq>/<fingerprint> <sent|failed>`, the echo's number and fingerprint, never its origin. A lost acknowledgement costs only one more send, or the next handshake. A `STALE` feed logs `TaqwaWearFeed STALE <seq>/<fingerprint>` of its delivery, and nothing is sent.
  - Before that send, when `FeedDrop.holdAck(filesDir)` is true, it logs `TaqwaWearFeed ACK HELD` and waits `FeedDrop.ACK_HOLD` (20 s) first. The listener's callbacks run one at a time, so a feed sent meanwhile is stored after the held acknowledgement goes.
  - `onCapabilityChanged` for `PHONE_CAPABILITY`, with a nearby node, runs `PhoneAsk(this, (application as TaqwaWearApp).feedStore).askIfDue(AskWhy.PHONE_NEARBY, now)` in `runBlocking { withTimeoutOrNull(10.seconds) { … } }`.
  - Its manifest entry is exported, with two filters on `wear` and `host="*"`: `MESSAGE_RECEIVED` with `pathPrefix="/taqwa/feed"`, which matches `/taqwa/feed/<origin>/<seq>/<fingerprint>` (`FeedReceiver` refuses the rest of what the prefix matches), and `CAPABILITY_CHANGED` with `path="/taqwa_phone"`.
- Produces `internal object FeedDrop { val ACK_HOLD: Duration; fun takeOne(filesDir: File): Boolean; fun holdAck(filesDir: File): Boolean }`, with `ACK_HOLD` = 20 s. The debug `takeOne` answers true once when `drop-next-feed` exists in `filesDir`, deleting it first, and `holdAck` the same for `hold-next-ack`. The release ones always answer false, and its build holds neither file name.
- Produces `internal enum class AskWhy { STARTED, OPENED, PHONE_NEARBY, DAILY }` and `internal object RefreshPolicy { const val GAP_SHORT = 60L; const val GAP_DAILY = 3_600L; fun shouldAsk(why: AskWhy, feed: WatchFeed?, fromPhone: Boolean, now: Long, lastAskedAt: Long?): Boolean }`. `fromPhone` is `store.delivery() != null`: the feed held is one a phone sent.
  - `STARTED` asks when the watch holds no feed from the phone (no feed, or one without a delivery, as the debug sample is), at least `GAP_SHORT` after the last ask. It is the heal for a watch the phone's memory cannot know is empty (the Lead's call 1: a reinstalled app, a new watch, a reset), since the phone's `WATCH_APPEARED` is damped;
  - `OPENED` asks when there is no feed or `needsRefresh` holds (spec 2.2: 3 days left or 7 days old; spec 2.6: "each time the app opens" once it is spent), at least `GAP_SHORT` after the last ask;
  - `PHONE_NEARBY` always asks, at least `GAP_SHORT` after the last ask: the reconnect handshake, damped for a link that flaps;
  - `DAILY` always asks, at least `GAP_DAILY` after the last ask: the daily handshake, which heals a return that changed no capability.
- Produces `internal class PhoneAsk(context: Context, store: FeedStore, stamp: File = File(context.filesDir, STAMP))`, with `suspend fun askIfDue(why: AskWhy, now: Long): Boolean`, and a `companion object` that needs no `Context`, so the JVM tests reach it: `const val STAMP = "refresh.asked"`, `fun payload(store: FeedStore): ByteArray` and `fun lastAskedAt(stamp: File): Long?`.
  - `payload` is `WatchLinkContract.echoOf(store.delivery())` in UTF-8 when `store.read()` is a feed, and empty bytes otherwise: the handshake (the Lead's call 1, Ruling 50).
  - `lastAskedAt` is the epoch second the stamp holds, or null when there is none.
  - `askIfDue` reads `store.read()` and `store.delivery()`, checks `RefreshPolicy.shouldAsk` with `lastAskedAt(stamp)`, then Play services' availability (`noPlayServices` is no phone), then sends `payload(store)` on `REFRESH_PATH` to each nearby node with `PHONE_CAPABILITY`. An `ApiException` with `noWearApi` is no phone; a cancelled or failed query is logged and asked again at the next chance.
  - It stamps the time only when a send went out, and logs `TaqwaWearFeed ASK <why> <sent|skipped|no phone>`.
- **`TaqwaWearApp.onCreate`** arms the daily nudge (below) and launches `PhoneAsk(this, feedStore).askIfDue(AskWhy.STARTED, now)` on the app's own `CoroutineScope(SupervisorJob() + Dispatchers.IO)`, inside `withTimeoutOrNull(10.seconds)`, catching `Exception`. It never blocks `onCreate`, and a process that ends first only loses this one ask. In a debug build `DebugWearApp` seeds the sample after `super.onCreate()`; the sample has no fingerprint, so `STARTED` still asks, and a paired phone replaces the sample with its feed.
- Produces `object DailyNudge { fun arm(context: Context); suspend fun nudge(context: Context, now: Long) }` and `class DailyNudgeReceiver : BroadcastReceiver` (not exported).
  - `arm` sets `AlarmManager.setInexactRepeating(ELAPSED_REALTIME, …, INTERVAL_DAY, …)`. That alarm does not wake the watch and needs no permission. The `PendingIntent` is `FLAG_IMMUTABLE`, and `FLAG_NO_CREATE` makes arming idempotent.
    - As built after review (round 1): a `PendingIntent` found does not prove the alarm is set. On Wear OS 3 to 5 (API 30-34, the watch's `minSdk` is 30) a force stop removes the package's alarms and keeps its `PendingIntent`s; only from Android 15 does the stopped state cancel those too. So `arm` also reads the nudge's anchor (`nudge.anchor`, the epoch second the alarm last went off, written by `DailyNudgeReceiver`, or when it was first set) and whether the last process ended by `ApplicationExitInfo.REASON_USER_REQUESTED`. `DailyNudge.arming` decides: a live alarm (found, an anchor within 36 h, no stop by the user) is left alone; with no anchor it is set a day away; with a stale anchor, due now; otherwise for when it was due, a day after the anchor, within a day from now. Held by `DailyNudgeTest`.
  - `nudge` runs `ComplicationSources.requestUpdates`, then `PhoneAsk(context, store).askIfDue(AskWhy.DAILY, now)`.
  - The receiver runs `goAsync`, then `nudge` inside `withTimeoutOrNull(10.seconds)`.
  - `TaqwaWearApp.onCreate` arms it. That is every process start, after a reboot included.
  - **A hand-stored feed** (`store.sh`, plan 1's `feed.sh` and `arabic.sh`, `DebugFeedReceiver`) carries no delivery, so with a paired phone nearby the next `STARTED`, `PHONE_NEARBY` or daily handshake has the phone replace it. Task 7 adds that to `store.sh`'s line in `tools/watchface/README.md`: stop the phone emulator by its serial before storing a feed by hand for shots.
- Produces, in the debug build only, `internal object DebugNudge { fun forgetLastAsk(filesDir: File) }` and `class DebugNudgeReceiver : BroadcastReceiver`. It is exported in `wearApp/src/debug/AndroidManifest.xml` for the action `world.taqwa.app.wear.DEBUG_NUDGE`, and asks for no permission, as `DebugFeedReceiver` is.
  - It runs `goAsync`, then `forgetLastAsk(filesDir)`, which deletes `refresh.asked`. Then it runs `DailyNudge.nudge` under the same 10-second bound, and ends with a result line that `am` prints: `nudged asked=<true|false>`.
  - Why it exists: `DailyNudgeReceiver` is not exported, so `am broadcast` from `adb shell` (neither the app's uid nor system, on a user build) is refused. And a handshake a minute earlier leaves `refresh.asked` inside `GAP_DAILY`, so the daily path would not ask. Its manifest comment says both.
- **`StatusActivity.onResume`:** `ComplicationSources.requestUpdates` (Ruling 22's app-start net), then `askIfDue(AskWhy.OPENED, now)`.
  - Since plan 3 Task 4 (30 October, erratum p3 #1): `StatusActivity` is deleted, and these two lines are `TaqwaWearApp.onAppOpened`, which `WearMainActivity.onResume` calls: `ComplicationSources.requestUpdates(this)`, then `askPhone(AskWhy.OPENED)` (`PhoneAsk.askIfDue` on the app's scope, inside `PhoneAsk.BOUND`). Opening the app on `emulator-5558` logs `TaqwaWearFeed ASK OPENED <sent|skipped|no phone>`.

- [ ] **Step 1: Record the watch's baseline** before `wearApp/build.gradle.kts` names the library (whether or not Task 5's catalog line is merged, it changes nothing in `:wearApp` until then):
  - `./gradlew :wearApp:dependencies --configuration releaseRuntimeClasspath > <scratch>/wear-deps-before.txt`;
  - the `uses-permission` lines of `:wearApp`'s merged release manifest after `./gradlew :wearApp:processReleaseMainManifest`. Expected: androidx.core's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` alone, as recorded on 30 October. Since plan 3's Task 4 landed after it, plan 3's `VIBRATE` stands beside it, and nothing else;
  - `./gradlew :wearApp:assembleDebug`, then Task 5 Step 1's `apkanalyzer` and `grep` lines on `wearApp/build/outputs/apk/debug/wearApp-debug.apk`, into `<scratch>/wear-packages-before.txt` and `<scratch>/wear-trackers-before.txt`. Expected: `android.adservices` and its subpackages, as in the face workflow's APK of 24 October (Global Constraints, "The library"), each listed in the report with the build step or library it comes from.
- [ ] **Step 2: Write the failing tests:**
  - `FeedReceiverTest`, with `d` = `WatchDelivery("0123456789abcdef", 5, "fedcba9876543210")` and the sample feed's JSON (`TestFeeds`):
    - `aFeedOnTheFeedPathIsStored`: on `feedPath(d)`, `ACCEPTED`; the feed reads back, `store.delivery()` is `d`, and `Received.echo` is `d.text`.
    - `aStaleOrDuplicateFeedIsDroppedUnacknowledged` (Ruling 50): after `d`, the same delivery again, and number 4 of the same origin, are `STALE`, with a null echo, the feed and `d` untouched.
    - `theSameFeedUnderANewerNumberIsStoredAndAcknowledged`: number 6 with `d`'s fingerprint is `ACCEPTED`, and the echo names 6.
    - `anotherInstallsFeedIsStored`: another origin's number 1 after `d` is `ACCEPTED` (two origins cannot be ordered; call 1's known limit).
    - `aNewerFeedKeepsTheLastAndSaysUpdate`: `"version": 2` gives `NEWER_VERSION`; the old feed and its delivery are kept, and `newerVersionSeen` is true.
    - `aBrokenFeedIsIgnored`: `MALFORMED`, and nothing is said.
    - `anotherPathIsNotAFeed`: "/taqwa/refresh" and "/taqwa/feedback" give null, with the store untouched.
    - `aFeedWithoutADeliveryIsNotStored`: on "/taqwa/feed" alone, on "/taqwa/feed/<a bare fingerprint>" (the form before Ruling 50) and on "/taqwa/feed/XYZ", null, with the store untouched.
    - `theWatchEchoesThePhonesDeliveryWhateverItsVersion`: two feeds, on `feedPath(d)` and then on the next number. One is a newer phone's: the sample with an unknown key `"future": 1` at the top level and in `ui`. The other is an older phone's: the sample without `hijriDayMonth` in any day.
      - Each is `ACCEPTED`, and `Received.echo` and `store.delivery()` name its delivery.
      - `WatchFeedJson.encode(store.read()!!)` differs from the text sent, so the case is real: a fingerprint of the watch's own re-encoding would not have matched the phone's.
    - `aNewerOrBrokenFeedIsNotAcknowledged`: `NEWER_VERSION` and `MALFORMED` have a null echo.
  - `FeedStoreTest` (add):
    - `aNewFeedReplacesTheDelivery`: `write(a, dA)`, then `write(b, dB)` with a greater number, gives `dB`; then `write(c)` (no delivery, as the sample) gives null.
    - `aNewerOrBrokenFeedKeepsTheDelivery`.
    - `theStoreFollowsTheContractsSteps`: a store whose feed rename is made to fail (its target taken by a directory) after the delivery's removal leaves the old feed and no delivery, never the old delivery beside a new feed; and the delivery is written only once the feed is in place (`WatchLinkContract.STORE_STEPS`).
    - `aFailedDeliveryWriteLeavesNoneNeverTheOldOne`: after `write(a, dA)`, the delivery's temporary path is taken by a directory, so its write throws. `write(b, dB)` is still `ACCEPTED`, `read()` is `b`, and `delivery()` is null, not `dA`.
  - `FeedDropTest.aDroppedFeedIsTakenOnce` (testDebug): in a temporary `filesDir` holding `drop-next-feed`, `takeOne` is true once and the file is gone; then it is false.
  - `FeedDropTest.aHeldAckIsTakenOnce` (testDebug): the same for `hold-next-ack` and `holdAck`.
  - `DebugNudgeTest.theDebugNudgeForgetsTheLastAsk` (testDebug), in a temporary `filesDir`:
    - with `refresh.asked` stamped 60 s before `now` and a fresh phone feed, `RefreshPolicy.shouldAsk(DAILY, feed, fromPhone = true, now, PhoneAsk.lastAskedAt(stamp))` is false;
    - after `forgetLastAsk(filesDir)`, `lastAskedAt` is null and `shouldAsk` is true.
  - `WearCodesTest.theWatchReadsEachCodeInItsOwnSpace`: as Task 5's `eachCallIsReadInItsOwnCodeSpace`, for the watch's copy.
  - `WearCodesTest.theWatchsAwaitReadsTheResultOnlyOnSuccess`: Task 5's `aCancelledTaskIsAnErrorNotACancellation`, `aFailedTaskAwaitsToItsOwnException` and `aCancelledTaskAwaitsToTaskCancelled`, for the watch's copy, over a `FakeTask` of its own in `wearApp/src/test/kotlin/world/taqwa/app/wear/link/`, with Task 5's `FakeTask.never()` and `complete(result)`.
  - `WearCodesTest.theWatchsAwaitIsCutByItsBound`: Task 5's `aTaskThatNeverCompletesIsCutByTheBudget`, for the watch's copy: under `runTest`, `withTimeoutOrNull(5.seconds) { FakeTask.never<Int>().await() }` is null at `currentTime` 5,000, and a `complete(1)` after it throws nothing. The same with `10.seconds` is null at 10,000, the bound `PhoneAsk` and `DailyNudge` run under.
  - `PhoneAskTest.theHandshakeCarriesTheStoredDelivery`:
    - with the sample stored with `d`, `payload` decodes to `d.text`, which `WatchLinkContract.deliveryOf` reads back as `d`;
    - with the sample stored without one, `payload` is empty;
    - with no feed, it is empty.
  - RefreshPolicy:
    - `aStartWithNothingFromThePhoneAsks`: `STARTED` with no feed, and with the sample held without a delivery (`fromPhone` false), gives true; 30 s after the last ask, false;
    - `aStartWithThePhonesFeedDoesNotAsk`: `STARTED` with a phone feed (`fromPhone` true) gives false, even a week-old one, which `OPENED`, `PHONE_NEARBY` and `DAILY` already cover;
    - `openedWithNoFeedAsks`;
    - `openedWithAFreshFeedWithWeeksLeftDoesNotAsk`: written an hour ago;
    - `openedThreeDaysFromTheEndAsks`;
    - `openedOnAWeekOldFeedAsks`;
    - `aSpentFeedAsksEachTimeTheAppOpens`: asked 61 s ago and opened gives true; 30 s ago gives false;
    - `thePhoneComingBackNearbyAlwaysShakesHands`: a fresh feed written an hour ago, last asked 2 minutes ago, gives true; last asked 30 s ago gives false;
    - `theDailyNudgeAlwaysShakesHands`: a fresh feed, last asked 2 hours ago, gives true; 30 minutes ago gives false.
  - `theWatchHasNoPrayerEngine`: `Class.forName` of `world.taqwa.app.prayer.PrayerTimesEngine` and of `com.batoulapps.adhan2.PrayerTimes` each throw `ClassNotFoundException`, and `java.class.path` holds no `adhan2`.
- [ ] **Step 3:** Run `./gradlew :wearApp:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 4:** Implement the code, and fix the two test-compile warnings. `SourceManifestTest` reads every `<service>`; keep it to the `.complication.` ones if the new service trips it.
- [ ] **Step 5:** Run `scripts/test.sh` and `./gradlew :wearApp:assembleRelease :wearApp:lint`. Expected:
  - all pass, and `:wearApp:compileDebugUnitTestKotlin` prints no warning;
  - the merged release manifest has no `INTERNET`, no location and no `uses-permission` beyond androidx.core's own and, since plan 3's Task 4, plan 3's `VIBRATE`, and declares neither `DebugNudgeReceiver` nor `DebugFeedReceiver`;
  - the `:wearApp` release dependency diff (`wear-deps-before.txt` against the same command now) is recorded, and should be the four Google libraries alone (fragment is already there). Anything else is listed with the library that pulled it in, and judged as in Task 5;
  - after `./gradlew :wearApp:assembleDebug`, Step 1's scan into `wear-packages-after.txt` and `wear-trackers-after.txt`: `comm -13 <scratch>/wear-trackers-before.txt <scratch>/wear-trackers-after.txt` prints nothing, or each line it prints is absent from the release dex or explained in the report, read as in Task 5 Step 5 (Ruling 53 item 1), and `comm -13` of the two package lists, every package the library added, goes in the report with the library it comes from;
  - the release APK's dex holds none of `drop-next-feed`, `hold-next-ack`, `DEBUG_NUDGE` and `DebugNudgeReceiver` (`apkanalyzer dex code` or a `strings` search of `classes.dex`).

  Add these checks to the Task 5 report.
- [ ] **Step 6: Commit** with the message "Wear: the watch stores the phone's feed only when it moves it forward, with the delivery it came with, and echoes it, shakes hands at start when it holds nothing from the phone, on reconnect and daily with the feed it holds, and nudges its complications daily".

### Task 7: The phone and the watch paired on emulators, the Android pass

**Files:**
- Create:
  - `scripts/phone-emulator.sh`: AVD `Taqwa_Phone_Wear`, device `pixel_8`, image `system-images;android-36;google_apis_playstore;arm64-v8a`, port 5562, `--window`. It follows `scripts/wear-emulator.sh` line for line. `--noplay` makes it AVD `Taqwa_Phone_NoPlay` instead, on image `system-images;android-36;default;arm64-v8a`, port 5564.
  - `scripts/wear-pair.sh`: starts both emulators if they are down, then runs `adb -s emulator-5562 forward tcp:5601 tcp:5601`, then polls the harness's `nodes` for up to 2 minutes.
  - `shared/src/androidMain/kotlin/world/taqwa/app/watchfeed/WatchFeedDebug.kt`: the debug seam.
  - `androidApp/src/debug/kotlin/world/taqwa/app/debug/WatchHarnessReceiver.kt`, and its entry in `androidApp/src/debug/AndroidManifest.xml` (action `world.taqwa.app.debug.WATCH`, as `RecitationHarnessReceiver`).
  - `docs/superpowers/reports/2026-10-23-phone-feed-pass/README.md`, with the screenshots, the logs and the device's `phone-forms-api36.json` beside it.
- Modify: `tools/watchface/README.md`, `store.sh`'s line: with the phone emulator paired and running, the watch's next handshake has the phone replace a hand-stored feed, so stop it by its serial first (Task 6).

**Interfaces:**
- Produces `object WatchFeedDebug`. It is public, because androidApp's debug harness is another module. Release code never calls it, so R8 drops it. It speaks only in strings and `Int`s:
  - `suspend fun nodes(context: Context): String`: every connected node with its id, name and `isNearby`, and which ones have `taqwa_watch`, through `PlayWatchNodes`. When `watches()` is null, it prints "no Wear API".
  - `suspend fun sync(reason: String): String`: `appContainer.watchFeedSync.sync(WatchSyncReason.valueOf(reason))` under `withTimeoutOrNull(WatchFeedSync.CALLER_BOUND)`, as the result's name, or "TIMEOUT".
  - `suspend fun sendWithVersion(version: Int): String`: builds the feed as `feedFor` does, raises its version, and sends it through the link directly, with that feed's own `watchFeedFingerprint`, as the result's name.
  - `suspend fun settle()`, under the same bound;
  - `suspend fun language(): String`: `watchUiLanguage().code`, the language the next feed is built in;
  - `fun phoneFormsJson(): String`: `PhoneForms.json()`, on this phone's ICU.
- Produces the harness: `adb -s emulator-5562 shell am broadcast -n world.taqwa.app/world.taqwa.app.debug.WatchHarnessReceiver -a world.taqwa.app.debug.WATCH --es cmd <cmd>`. It uses only public API: `appContainer`'s public repositories and `notificationCoordinator.reschedule`, as `RecitationHarnessReceiver` does, and `WatchFeedDebug`. The commands:
  - `nodes`;
  - `place --es place "lat,lon,zone,name[,cityId]"`: `settingsRepository.setLocation` with that `cityName` and `cityId`, then `settingsRepository.setLocationSource(LocationSource.MANUAL)` and `settingsRepository.setOnboardingComplete(true)`, then `reschedule(SETTINGS_CHANGED)`, then `WatchFeedDebug.settle()`. London is `51.5074,-0.1278,Europe/London,London,2643743` and Tripoli `32.8872,13.1913,Africa/Tripoli,Tripoli,2210247` (their ids in `files/cities.csv`).
    - **Why the two writes.** A fresh install opens on onboarding (`App.kt:224`, `if (!settings.onboardingComplete.first()) navigator.replaceAll(Screen.Onboarding)`), and the only way through it is its location step, which can replace the harness's city. Every launch also makes an `APP_FOREGROUND` reschedule, which with a source other than MANUAL asks for a fix (`LocationRefresher.refreshFor`, lines 52-57) and would move the feed off London in the middle of the pass.
    - The source already reads MANUAL when it was never stored (`SettingsRepository.kt:228-229`), but `place` writes it every time, so the pass never depends on what an earlier run or a hand in the emulator's window chose. MANUAL needs no location permission, and the pass grants none.
    - `place` is the first command on every fresh install (Step 2's 0a and 0b, Step 4's check 1), so every `am start` in the pass opens on the Prayer screen. The report says that `place` does both and why;
  - `clock --es format 12|24`: the setting, then the same reschedule and settle;
  - `phrase --es text … --ei target n`: `tasbeehStore.addCustom`, then `WatchFeedDebug.sync("PHRASES_CHANGED")`. The harness fires the hook itself, because the hook lives on `TasbeehViewModel`;
  - `send`: `WatchFeedDebug.sync("WATCH_CHANGED")`, a send that forgets the confirmation first;
  - `appeared`: `WatchFeedDebug.sync("WATCH_APPEARED")`, the damped ask a reconnect makes;
  - `send --ei version 2`: `WatchFeedDebug.sendWithVersion(2)`;
  - `reschedule`: `reschedule(SETTINGS_CHANGED)`, then settle. It prints the plan's size and its first entry;
  - `language`: `WatchFeedDebug.language()`;
  - `forms`: writes `WatchFeedDebug.phoneFormsJson()` to `files/phone-forms-device.json`.

  Each command prints its result.

**Pairing (one-time steps are Mohamed's; agents never sign in to anything):**
1. Run `scripts/wear-emulator.sh` (5558) and `scripts/phone-emulator.sh --window` (5562). Both must be running *before* pairing, so that Android Studio keeps their ports.
2. **Mohamed, once:**
   - in the phone emulator's window, sign in to the Play Store with an account of his choice;
   - install the **Google Pixel Watch** app (`com.google.android.apps.wear.companion`);
   - in Android Studio: Device Manager › ⋮ next to `Taqwa_Phone_Wear` › **Pair Wearable** › `Taqwa_Wear_OS_6` › Next;
   - finish the setup the Pixel Watch app shows on the phone.
3. Check: the harness's `nodes` lists the watch with `nearby=true`.
4. After any restart, `scripts/wear-pair.sh` tries the re-pair and says whether the node came back. If it did not, **stop**: the Lead asks Mohamed for Studio's Pair Wearable again. The report records which way works.

- [ ] **Step 1:** Write the two scripts, `WatchFeedDebug` and the harness. Build with `./gradlew :androidApp:assembleDebug :wearApp:assembleDebug`. Install with `adb -s emulator-5562 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk` and `adb -s emulator-5558 install -r wearApp/build/outputs/apk/debug/wearApp-debug.apk`.
- [ ] **Step 2: The phones without a watch**, before any pairing. Each is shot, logged and dumped into the report.
  - **0a. Play services, no Wear OS app.** This is `Taqwa_Phone_Wear` before Mohamed installs the Pixel Watch app. Run `place` London (51.5074, -0.1278, Europe/London), then `reschedule`. Expected:
    - `nodes` prints "no Wear API" or no nodes, and the log's `TaqwaWatch no Wear API <code>` names the connection result (`API_UNAVAILABLE` 16 expected); the report says which;
    - the log says `TaqwaWatch RESCHEDULED NO_WATCH`, never `FAILED`;
    - the harness prints a non-empty plan;
    - `adb -s emulator-5562 shell dumpsys alarm` holds that many `world.taqwa.app` prayer alarms;
    - `adb -s emulator-5562 shell dumpsys notification --noredact` shows no notification from `com.google.android.gms` or `world.taqwa.app` posted since the install.
  - **0b. No Play services at all.** The image `system-images;android-36;default;arm64-v8a` is not installed. Downloading it (Google's SDK repository, about 1.5 GB, through `sdkmanager`) is the Lead's call, as Ruling 23 was for the validator: ask first. With it, run `scripts/phone-emulator.sh --noplay` (5564), install, then `place` and `reschedule` as in 0a. The expectations are the same, then `adb -s emulator-5564 emu kill`. Without it, the report says the no-Play case is proven on fakes only.
  - **The phone's own forms.** On 5562, run `forms`, then `adb -s emulator-5562 exec-out run-as world.taqwa.app cat files/phone-forms-device.json > docs/superpowers/reports/2026-10-23-phone-feed-pass/phone-forms-api36.json`. Compare it with `tools/watchface/phone-forms.json`, tag by tag and field by field (`python3 -I -c` with `json`), and record every difference in the report.
    - If there is any difference, run `date-fit.py`, `window-fit.py` and `moon-fit.py` with `forms=` set to the device's file.
    - **August's spelling** (Ruling 51a): Arch draws a right-to-left date ending in أغسطس at 13 units and every other form of 18 characters or fewer at 14 (`ARCH_GREGORIAN_STEPS`), so the face holds to that one spelling. The report names the device's August for ar-SA, ar-EG, ar-LY and ar-AE, and whether it is أغسطس. If it is not, `date-fit.py forms=<the device's file>` measures that August at 14 units, and the difference goes to the Lead with its margin, as any other.
    - If a form fails, **stop** and hand it to the Lead, as in Task 2. The plan does not close with a device form unmeasured.
- [ ] **Step 3:** Pair as above. If the Pixel Watch app is missing, **stop** and report: the sign-in is Mohamed's.
- [ ] **Step 4: The pass.** Each check is run, shot (`adb -s <serial> exec-out screencap -p`) and logged (`adb -s <serial> logcat -d -s TaqwaWearFeed System.out`) into the report. Every `TaqwaWatch` line's milliseconds go in the report too, the first sync after a cold start included: it parses the bundled city list (Task 3's `watchCityName`). If any sync takes more than 4 s, flag it to the Lead.
  1. **The feed arrives.** `place` London (with its city id) logs `TaqwaWearFeed ACCEPTED`, then the watch's `TaqwaWearFeed ACK <seq>/<fingerprint> sent` and the phone's `TaqwaWatch ACK <seq>/<fingerprint>`, with the same number and fingerprint. No line of the release code's (`TaqwaWearFeed`, `TaqwaWatch`) holds the origin (Ruling 52 item 4); only the debug harness's own may.
     - `adb -s emulator-5558 shell run-as world.taqwa.app cat files/feed.json.delivery` shows the same delivery, its origin before the number.
     - `… cat files/feed.json` shows 31 days, and the first date is the phone's yesterday.
     - The place is as Ruling 39 allows it: `city` "London", whole-degree `qiblaBearing` and `declination`, and `qiblaKm` "4,800". No number in `place` has a decimal part, and neither coordinate appears anywhere in the file.
     - Today shows "London" under its title (plan 3 Task 4 draws the city there; Task 5's list ends in it).
  2. **App = watch.** `adb -s emulator-5562 shell am start -n world.taqwa.app/.MainActivity`. Expected: it opens on the Prayer screen for London, never onboarding (the shot shows it), and the launch's reschedule logs `TaqwaWatch RESCHEDULED UNCHANGED`: the launch kept the place. At the same minute, the phone's Prayer screen and the Taqwa face (Arch) show the same next prayer, its time and its countdown minute.
  3. **Unchanged.** `reschedule` twice, the second after the phone has logged the first one's `TaqwaWatch ACK`: the second logs `TaqwaWatch RESCHEDULED UNCHANGED`.
  4. **Settings and the app's language.**
     - `clock 12`: the face's time is 12-hour and the feed's `twelveHour` is true.
     - **A per-app language, the app closed:** `adb -s emulator-5562 shell am force-stop world.taqwa.app`, then `adb -s emulator-5562 shell cmd locale set-app-locales world.taqwa.app --locales ar-SA`, then `language` and `reschedule` through the harness, with no activity started. Expected: `language` prints "ar"; the feed is in Arabic, with `rtl` true, `digitZero` "٠", `city` "لندن" (the bundle's Arabic name, as the Prayer screen shows it) and `qiblaKm` "٤٬٨٠٠" (U+066C, as the phone's Qibla card groups); the face is in Arabic.
     - **In place, the app open:** `adb -s emulator-5562 shell am start -n world.taqwa.app/.MainActivity`, check it opens on its Prayer screen, in Arabic, with London's times (`place` marked onboarding done and the source manual), then `cmd locale set-app-locales world.taqwa.app --locales en-US` while it is in front. Expected: the app redraws in English, the phone logs `TaqwaWatch RESCHEDULED SENT` (`LanguageChangeReschedule`), and the feed is in English, left to right, with `digitZero` "0".
     - **Outside the seven:** `cmd locale set-app-locales world.taqwa.app --locales he-IL`, then `language` and `reschedule`. Expected: "en", an English feed with English weekdays and months ("SUN", "OCT", never Hebrew), `rtl` false, and `city` "London". If the command refuses a locale outside the app's `locales_config`, the report says so, and the host tests stand for this case.
     - **The spoken words, from the phone's real resources:** in each of the Arabic and English feeds above, `ui.spoken` equals the ar-SA and the en sample's `ui.spoken` (`python3 -I -c` with `json`), and every day's `gregorianSpoken` is the weekday and month in full ("الأحد ١٨ أكتوبر", "Sunday 18 October" for the 18th). Then, on the Arch face, `tools/watchface/talkback.sh on`, swipe through the face as plan 1's Step 7 does, and `talkback.sh off`. Expected, in each language: the next prayer's sentence in the phone's spoken words ("Asr in 1 hour 12 minutes, at 16:10", "العصر بعد ١ ساعة و١٢ دقيقة، الساعة ١٦:١٠", with the minute of the walk), and the dates with the Gregorian in words. The report quotes the lines heard. TalkBack is off at the end (Ruling 38).
     - Then put the system's language back (the app's language page in the emulator's Settings, "System default", or `cmd locale set-app-locales` with an empty `--locales` if the shell passes one), and the report shows `language` again.
  5. **Phrases.** `phrase "سبحان الله وبحمده" 100` logs `TaqwaWatch PHRASES_CHANGED SENT`, and the feed's `tasbeeh` holds the phrase.
  6. **The watch asks when its feed runs low.** Run `adb -s emulator-5558 shell am broadcast -n world.taqwa.app/world.taqwa.app.wear.DebugFeedReceiver -a world.taqwa.app.wear.DEBUG_FEED --el epoch <now − 29 days>` (the sample, nearly spent), then open the watch app. The sample carries no delivery, so the handshake is empty: the phone logs `WATCH_ASKED SENT`, and the feed is London's again, with its delivery under the next number.
  7. **A newer version.** `send --ei version 2` logs `NEWER_VERSION`. Today says "Update Taqwa on your watch" in place of the next prayer's clock time (plan 3's call 5; before Task 5, in the city's place under the title), and `feed.json` is unchanged. A plain `send` then logs `ACCEPTED`, and the notice is gone.
  8. **No watch app, then a reinstalled one healed by its own handshake** (the Lead's call 1). The feed is acknowledged, so the record confirms it.
     - `adb -s emulator-5558 uninstall world.taqwa.app`, then `reschedule` gives `NO_WATCH`.
     - Reinstall it (`adb -s emulator-5558 install -r …`). The phone logs `WATCH_APPEARED UNCHANGED` within a minute: the record still confirms the feed, and a reinstall looks like a reconnect.
     - The watch app's first process start (the face's slots binding its sources, or `adb -s emulator-5558 shell am start -n world.taqwa.app/world.taqwa.app.wear.app.WearMainActivity` if nothing starts it within a minute) logs `TaqwaWearFeed ASK STARTED sent` (or `OPENED`), and the phone `WATCH_ASKED SENT`. `feed.json` holds London's days and `feed.json.delivery` their delivery. The report says which ask came first.
  9. **A delivery dropped, then healed** (the Lead's call 1). `adb -s emulator-5558 shell run-as world.taqwa.app touch files/drop-next-feed`, then `place` Tripoli (with its city id). Expected:
     - the phone logs `TaqwaWatch RESCHEDULED SENT`, and the watch `TaqwaWearFeed DROPPED`, with no acknowledgement;
     - `feed.json` still shows London's days;
     - a `reschedule` then logs `TaqwaWatch RESCHEDULED SENT`, never `UNCHANGED`, and the watch `ACCEPTED` and its acknowledgement. `feed.json` shows Tripoli's;
     - one more `reschedule` logs `UNCHANGED`.

     **Then a change undone before its acknowledgement.** The feed is Tripoli's on 12 hours, as check 4 left the clock, and acknowledged. Run `adb -s emulator-5558 shell run-as world.taqwa.app touch files/hold-next-ack`, then `clock 24`, then within 10 s `clock 12`. Expected:
     - the watch logs `ACCEPTED` and `ACK HELD` for the 24-hour feed;
     - the phone logs `RESCHEDULED SENT` for `clock 12`, never `UNCHANGED`, because the 24-hour send's number is not confirmed;
     - after the hold, the watch stores the 12-hour feed and acknowledges it. The phone's two `TaqwaWatch ACK` lines name the 24-hour and the 12-hour deliveries, in that order, the second's number one above the first's;
     - `feed.json` has `twelveHour` true, and the face draws 12 hours;
     - one more `reschedule` logs `UNCHANGED`.
  10. **A change made while the watch was away, healed on its return.**
      - Take the link down with `adb -s emulator-5562 forward --remove tcp:5601`, and wait until `nodes` shows no nearby watch.
      - `place` London logs `TaqwaWatch RESCHEDULED NO_WATCH`.
      - Put the forward back. Within 2 minutes the phone logs `WATCH_ASKED SENT` (the watch's reconnect handshake) or `WATCH_APPEARED SENT`, and `feed.json` shows London's days. The report says which came first.
      - **A reconnect with nothing changed:** once the phone has logged the acknowledgement, take the forward down and put it back again, with no change on the phone. Expected: `WATCH_APPEARED UNCHANGED` and `WATCH_ASKED UNCHANGED`, and the watch logs no new `TaqwaWearFeed ACCEPTED`: a watch that holds the current feed is not sent it again (spec 1.2). The harness's `appeared` gives the same `UNCHANGED`.
      - Then the daily handshake with nothing to do, through Task 6's debug trigger: `adb -s emulator-5558 shell am broadcast -n world.taqwa.app/world.taqwa.app.wear.link.DebugNudgeReceiver -a world.taqwa.app.wear.DEBUG_NUDGE`. It forgets the reconnect handshake's stamp and runs the daily path.
        - `am` prints `nudged asked=true`.
        - The phone logs `WATCH_ASKED UNCHANGED`, and the watch logs no new `TaqwaWearFeed ACCEPTED`.
        - The real `DailyNudgeReceiver` is not exported, so `adb shell` cannot reach it. It runs the same `DailyNudge.nudge`, and its alarm shows in `adb -s emulator-5558 shell dumpsys alarm` as `world.taqwa.app`'s inexact repeating alarm, which the report quotes.
      - If removing the forward does not drop the node within 2 minutes, take the watch down with `adb -s emulator-5558 emu kill` instead, then restart it with `scripts/wear-pair.sh`; the report says which way was used.
  11. **No watch,** last because it ends the pairing session. `adb -s emulator-5558 emu kill`, then `reschedule`: the result is `NO_WATCH` within the budget, and the harness shows the plan armed as before. Then restart and re-pair (`scripts/wear-pair.sh`).
- [ ] **Step 5:** Run `scripts/test.sh` once more, then `./gradlew :androidApp:minifyReleaseWithR8`. Expected: PASS, and `androidApp/build/outputs/mapping/release/usage.txt` lists `world.taqwa.app.watchfeed.WatchFeedDebug`, `world.taqwa.app.watchfeed.PhoneForms` and `world.taqwa.app.watchfeed.PhoneTags` as removed.
- [ ] **Step 6: Commit** the scripts, `WatchFeedDebug`, the harness and the report, with the message "Watch feed: the phone and the watch paired on emulators, and the Android pass".
- **As built, Steps 3 to 6 (31 October; the report has the detail):**
  - Studio's Pair Wearable makes two halves: the phone's forward from a host port of its own (`tcp:64922 → tcp:5601` here) and a reverse on the watch (`adb -s emulator-5558 reverse tcp:5601 tcp:<that port>`). adb keeps neither across a restart of its emulator, so `scripts/wear-pair.sh` makes both through host 5601 (`adb -s emulator-5562 forward tcp:5601 tcp:5601`, `adb -s emulator-5558 reverse tcp:5601 tcp:5601`), test-first in `scripts/test-pairing-scripts.sh`. With it the node came back after each of the watch's three restarts, and Studio was not needed again.
  - Check 10: there is no `tcp:5601` forward to remove (Studio's is on its own port), and removing Studio's left the watch connected and nearby for two minutes: a forward only takes new connections. So check 10 takes the link down with the fallback, `adb -s emulator-5558 emu kill`, and brings it back with `scripts/wear-pair.sh`, for the change made while away and for the reconnect with nothing changed.
  - Check 4's he-IL: `cmd locale` accepts it, but the app's process runs with `[he_IL,en_US]` and Android makes its default locale the first one the app's resources match, `en_US`. So the device's "outside the seven" feed is the English one itself (`UNCHANGED`); the host tests hold a he-IL tag reaching the format.
  - Check 9 found that after check 8's uninstall and reinstall, the face (installed by adb) kept its complications' data from before the uninstall until the face was installed again; for plan 5's Galaxy pass (the report's "For the Lead").

### Task 8: The iPhone sender over WatchConnectivity

Ruling 39 accepted the relay for the iPhone, so this task is built as written.

**Files:**
- Replace: `shared/src/iosMain/kotlin/world/taqwa/app/watchfeed/WatchLink.ios.kt`.
- Modify:
  - `iosApp/iosApp/iOSApp.swift`. The new class lives in this file rather than a new one: the project lists its files by hand, and the `xcodeproj` gem the repository's tools use is not installed on the Mac mini. Plan 4, which adds the watchOS target, may move it.
  - `shared/src/iosMain/kotlin/world/taqwa/app/settings/DataStoreFactory.ios.kt:14-15`, the comment only: it quotes "Only your own devices can see your location" in place of "your location never leaves your phone" (Ruling 39, item 5), and its reasoning for the backup exclusion stands. No code changes.
- Test: `shared/src/iosTest/kotlin/world/taqwa/app/watchfeed/IosWatchLinkTest.kt`.
- Create: `docs/superpowers/reports/2026-10-23-iphone-watch-link.md`.

**Interfaces:**
- Consumes `WatchLinkContract` (Task 3), `WatchFeedSync` (Task 4) and `WatchFeedBuilder` (Task 3).
- Produces `interface WatchSessionHook { fun isReady(): Boolean; fun watchKey(): String?; fun send(feed: NSData, delivery: String): Int }` (Ruling 50). It is public, and Swift sees it as a protocol with `Bool`, `String?` and `Int32` returns, a `Data` parameter and a `String` one. `send` answers one of the bridge's three codes, and `delivery` is `WatchDelivery.text`. `watchKey()` is amendment A3's key: a random `UUID().uuidString` the hook keeps in a file `taqwa-watch-key` inside `WCSession.default.watchDirectoryURL`, written there when absent. It reads that URL only when `activationState == .activated` and answers nil otherwise, or when the URL is nil (Ruling 52 item 5). What Apple documents, and all the code relies on: the URL is "undefined and should not be used" while the session is `notActivated`; it is a directory "specific to the currently paired and active Apple Watch", whose URL "changes when the active Apple Watch changes"; "If the user uninstalls your app or unpairs their Apple Watch, iOS deletes this directory and its contents"; and "If there is no paired watch, the value of this property is nil". So the key names one watch and one install of its app.
- Produces `object WatchFeedBridge`, public, in strings, Booleans and `Int`s only:
  - `var hook: WatchSessionHook? = null`;
  - `val contextFeedKey`, `val contextDeliveryKey`, `val refreshMessageKey` and `val refreshReplyKey`, the `WatchLinkContract` constants, so that Swift names no key of its own;
  - `val sendQueued = 0`, `val sendFailed = 1` and `val sendTooLarge = 2`, the hook's answers;
  - `fun refreshRequested(have: String, reply: (Boolean) -> Unit)`, as built after Ruling 52 item 6 (it first blocked, in `runBlocking`). It reads `hook?.watchKey()` first, as the message arrives (the watch it came from, amendment A3), then launches, on the bridge's own scope (`SupervisorJob() + Dispatchers.Default`), `withTimeoutOrNull(WatchFeedSync.CALLER_BOUND) { appContainer.watchFeedSync.sync(WATCH_ASKED, have = have, from = key) }`, and returns. `reply` is called exactly once: true for `SENT` or `UNCHANGED` (the watch has, or is being given, the current feed), false for anything else, a bound passed, a throw, a scope gone, or the system taking the background time back. So WatchConnectivity's delegate queue never waits for the sync, and the watch asks again at its next chance after a false. **Ruling 53 item 4, as built:**
    - (a) the coroutine starts `CoroutineStart.UNDISPATCHED`, so its ask goes on the sync's queue (`trySend`, which never suspends or blocks) on the caller's thread before the call returns, and the sync serves the handshakes and `watchStateChanged()` in WatchConnectivity's own order; the wait and the reply are off that queue. A reply can then come on the caller's thread too, when the ask is answered before it suspends (a key or a container that throws, a sync whose worker has ended);
    - (b) before anything else it asks UIKit for background time (`HandshakeTime`, `UIKitHandshakeTime`: `beginBackgroundTask(withName:expirationHandler:)`), and ends it right after the reply, once, so an app the handshake woke in the background is not suspended before it answers. The expiration handler answers false and ends it, even when it comes before `begin` returns; no time given (`UIBackgroundTaskInvalid`) still answers. P4.5B records a handshake that cold-wakes the iPhone app;
  - `fun logged(delivery: String): String`, `loggedDelivery`'s form for the Swift session's log lines: `<seq>/<fingerprint>`, `none` or `unreadable`, never the origin (Ruling 52 item 4);
  - `fun watchActivated()`, which calls `appContainer.watchFeedSync.request(WATCH_APPEARED)` and returns at once: the damped ask of every launch (the Lead's call 1);
  - `fun watchStateChanged()`, which calls `appContainer.watchFeedSync.request(WATCH_CHANGED)` and returns at once: the watch app installed or removed, or another watch paired, so the confirmation is forgotten first. Amendment A3's key makes it durable: a change the app never hears of (it was not running, or its process died first) still finds another key.
- Produces `internal fun feedData(json: String): NSData`: the JSON's UTF-8 bytes (`json.encodeToByteArray()`), as `NSData`. A property list stores `Data` as its bytes; a `String` holding any non-ASCII character would be stored in UTF-16.
- Produces `internal class IosWatchLink(hook: () -> WatchSessionHook? = { WatchFeedBridge.hook }) : WatchLink`, with `actual fun createWatchLink(): WatchLink = IosWatchLink()`.
  - `confirmsDelivery` is false: the system keeps the latest application context and delivers it when the watch can take it, so a `SENT` confirms its number for the watch it went to (Task 4, Ruling 50 item 4). `watchKey()` is `hook()?.watchKey()`.
  - `reachable()` is `hook()?.isReady() == true`, so an iPhone without a watch builds nothing at launch.
  - With no hook or not ready, `send` is `NO_WATCH`. Otherwise it calls `hook.send(feedData(json), delivery.text)`: `sendQueued` is `SENT`, `sendTooLarge` is `TOO_LARGE` (the sync then sends the times without the phrases), and any other answer or a throw is `FAILED`.
- Produces in Swift `final class TaqwaWatchSession: NSObject, WCSessionDelegate, WatchSessionHook`, with `static let shared`:
  - `start()`, as built: if `WCSession.isSupported()`, `WatchFeedBridge.shared.hook = self` first, then the delegate, then `activate()`. The hook is set before `activate()` because activation completes on WatchConnectivity's own queue, and its `watchActivated()` ask could otherwise reach the sync before the hook was set and answer `NO_WATCH`. `iOSApp.init()` calls `Self.installWatchSession()`, beside `installWidgetRefreshHook()`.
  - `isReady()`: `activationState == .activated && isPaired && isWatchAppInstalled`.
  - `send(feed:delivery:)`: first it checks again, right before the call, `activationState == .activated && isPaired && isWatchAppInstalled`, and answers `sendFailed` otherwise, with nothing handed over: a watch switch can make the session inactive between `isReady()` and the send, and Apple calls `updateApplicationContext` on an inactive session a programmer error, which Swift may not be able to catch. Then `try updateApplicationContext([WatchFeedBridge.shared.contextFeedKey: feed, WatchFeedBridge.shared.contextDeliveryKey: delivery])`, with `feed` as `Data` and `delivery` as a `String`. It answers `sendQueued`; `sendTooLarge` for `WCError.payloadTooLarge`; and `sendFailed`, logged, for any other error. Each answer is logged with the feed's byte count and the delivery's number and fingerprint (`WatchFeedBridge.shared.logged(delivery:)`), never its origin.
  - `watchKey()`: as above, `guard session.activationState == .activated, let directory = session.watchDirectoryURL`; it logs `TaqwaWatch key <new|kept>` (never the key itself).
  - The delegate methods:
    - `activationDidCompleteWith` calls `watchActivated()`, and `sessionWatchStateDidChange` calls `watchStateChanged()`. Never the other way round: activation follows every `activate()`, so an undamped ask there would resend the whole feed at every launch, background refreshes included, and reload every widget timeline on the watch;
    - `sessionDidBecomeInactive` does nothing;
    - `sessionDidDeactivate` calls `activate()` again;
    - `didReceiveMessage` with a reply handler, holding `refreshMessageKey`, calls `refreshRequested(have: message[refreshMessageKey] as? String ?? "") { queued in replyHandler([refreshReplyKey: queued.boolValue]) }` and returns at once; the form without a reply handler calls it with a reply that only logs. A message without the key is answered `[:]`.
  - Every decision is logged with `NSLog("%@", "TaqwaWatch …")`, never with values interpolated into the format string (Ruling 52 item 4): activation, each `isReady()` with its three facts (`activated`, `paired`, `installed`), each send, and each handshake with its echo's number and fingerprint. Kotlin's `println` does not reach the unified log on iOS, so these Swift lines are the evidence.
- **For plan 4:**
  - The watch reads `applicationContext["feed"] as? Data`, decodes its UTF-8, both in `session(_:didReceiveApplicationContext:)` and from `receivedApplicationContext` at activation, and decodes the JSON with the Swift port against `watchcore/vectors/phone/*.json`.
  - It reads `applicationContext["delivery"] as? String` with the Swift port of `WatchLinkContract.deliveryOf` and stores the feed only when `WatchLinkContract.stores` says so (Ruling 50), in `STORE_STEPS`'s order, keeping the delivery beside it. It never computes a fingerprint: no port of it, and no copy of kotlinx's bytes in Swift (the Lead's call 1). A context without a delivery is no phone's, and is not stored.
  - It shakes hands with `sendMessage(["refresh": <echoOf the delivery it holds, or "">], replyHandler:)` when `isReachable`: at activation, when the session becomes reachable, and when the app opens on a feed that needs one. The handshake at activation waits for `ConnectivityDrain.isDrained` (activated, no content pending, no intake in flight), within its 10 s limit, so that its echo names what was just stored; it is what heals a reinstalled watch app, since the iPhone's launch ask is damped.
  - **The backup check** (Ruling 39's item 3): after a send to the paired simulator watch, find where WatchConnectivity keeps the iPhone's copy of the application context, and read its `NSURLIsExcludedFromBackupKey`. The report says whether the policy's added Backups sentence (question 7) stays.
  - It skips its widget reload when a stored context's fingerprint equals the one it held, and still writes the newer delivery, so its echo names the newer number.
  - **Ship gate:** measure, with the paired watch app, the `Data` size at which `updateApplicationContext` and `transferUserInfo` first answer `payloadTooLarge`. If either is below `FEED_BUDGET_BYTES + ENVELOPE_BYTES`, the Lead lowers the budget, and `WatchLinkContract`'s figures take the measured values. The times-first resend covers the gap meanwhile.

- [ ] **Step 1: Write the failing tests:**
  - `withoutASessionThereIsNoWatch`: not reachable, and `NO_WATCH`;
  - `aSessionNotReadyIsNoWatch`: not reachable, and `NO_WATCH`;
  - `aReadySessionIsReachable`;
  - `theFeedTravelsAsUtf8Data`: the hook receives `NSData` whose bytes equal `json.encodeToByteArray()`, for an Arabic feed, and beside it the delivery's text that `send` was given;
  - `aRefusedContextIsFailed`;
  - `aContextTooLargeIsTooLarge`: `sendTooLarge` gives `TOO_LARGE`, and through a `WatchFeedSync` the hook's second payload decodes to the same days and no phrases, and the ask is `SENT`;
  - `aThrowingSessionIsAFailure`;
  - `theIphoneRemembersWhatItSent`: `confirmsDelivery` is false;
  - `aHandshakeWithTheCurrentFeedIsAnsweredWithoutASend`: through a `WatchFeedSync` with a fake `feedFor` over an `IosWatchLink` with a ready fake hook, after one send of the feed, `refreshRequested(have = <that send's delivery text>)` is true and the hook's `send` was called once in all: the watch echoed the latest delivery (Ruling 50);
  - `theLaunchIsDampedAndTheWatchStateChangeIsNot`: through a `WatchFeedSync` over an `IosWatchLink` with a ready fake hook, a first `WATCH_APPEARED` is `SENT` and a second, for the same feed, `UNCHANGED` with one hook `send` in all; then `WATCH_CHANGED` of the same feed is `SENT`, a second hook `send`. The Swift side's mapping (activation to `watchActivated()`, state change to `watchStateChanged()`) is two lines, checked in review and in Step 5's log;
  - `aConfirmationCountsOnlyForTheWatchItWentTo` (amendment A3): through a `WatchFeedSync` over an `IosWatchLink` whose fake hook answers key "k0", a `WATCH_APPEARED` is `SENT` and then `UNCHANGED`; the hook's key becomes "k1" (another watch, or the app reinstalled), and the next `WATCH_APPEARED` is `SENT`; a `refreshRequested` read while the key was "k0" and served under "k1" is ignored.
  - `aSessionThatWentInactiveIsRefusedNotSent`: the hook's `send` answering `sendFailed` (its re-check) is `FAILED`, and the next ask sends again.
  - `theLargestContextFitsTheSmallestLimit`: for feeds built by `WatchFeedBuilder` through `IosPlatformFormat` for `ar_SA` and `bn_BD` on 12 hours, with `city = WorstFeeds.CITY`, `spoken = watchSpoken()` (the simulator's real resources) and with `WorstFeeds.presets(CONTROL)` and `WorstFeeds.presets(EMOJI)`:
    - the dictionary `[contextFeedKey: feedData(json), contextDeliveryKey: delivery.text]`, serialised with `NSPropertyListSerialization` in `NSPropertyListBinaryFormat_v1_0`, is at most `USER_INFO_LIMIT_BYTES`;
    - the same dictionary through `NSKeyedArchiver.archivedDataWithRootObject(requiringSecureCoding = false)` is at most `USER_INFO_LIMIT_BYTES`;
    - the report records both sizes and the feed's own byte count;
  - `theContractKeysAreTheWatchcoreOnes`.
  - **Added by Ruling 52** (the asynchronous handshake, items 4, 6 and 7): `aHandshakeWithNothingIsSentTheFeed`; `aHandshakeNeverBlocksWatchConnectivitysQueue` (the call returns while the sync still builds, the reply comes later); `aHandshakeWithNoWatchOrNoFeedIsFalse` (`NO_WATCH` and `NO_FEED` are false); `aHandshakeTheSyncCannotServeIsFalse` (a sync whose worker has ended answers false at once, a stuck one false at the bridge's bound); `aHandshakeIsAnsweredOnceWhateverBecomesOfIt` (a key that throws, no container, the bridge's scope gone: one reply each); `aBridgeCallNeverThrows`; `theBridgeLogsADeliveryWithoutItsOrigin`. In `commonTest`: `WatchFeedLogTest`, `WatchFeedMemoryTest.aStoredOriginOfAnotherFormStartsAWholeNewRecord`, `WatchFeedRecordTest.anEchoNumberedAboveHalfALongIsAnotherInstalls`, `WatchFeedSyncTest.anEchoAtTheLargestNumberNeverWedgesTheSync` and `onTheIphoneAHandOverCountsForTheWatchKeyReadBeforeTheCall` (A3's key read before the call, mutant M9).
  - **Added by Ruling 53 item 4:** `aHandshakeReachesTheSyncInWatchConnectivitysOrder` (a handshake, a watch-state change and a handshake are served in that order, over a bridge scope that has not run); `aHandshakeHoldsBackgroundTimeUntilItIsAnswered`; `theBackgroundTimeEndsRightAfterTheReplyWhateverBecomesOfTheHandshake` (its bound, a key that throws, no container, the scope gone, the worker gone; and no time given); `theSystemTakingTheTimeBackAnswersAndEndsItThere` (while the sync works, and before `begin` returned).
- [ ] **Step 2:** Run `./gradlew :shared:iosSimulatorArm64Test`. Expected: FAIL.
- [ ] **Step 3:** Implement the Kotlin side and the Swift class.
- [ ] **Step 4:** Run `scripts/test.sh` and `scripts/ios-build.sh`. Expected: both pass, the widget extension's size guard holds, and the framework header names no watchcore type (the Task 1 grep).
- [ ] **Step 5: The simulator check,** for the report:
  - Use the iPhone 17 Pro on iOS 27.0 (`2D01CE58-7F12-45FC-8DB0-D63330AE9BB1`) and the watchOS 27.0 Apple Watch Series 12 (46mm) `C803D94B-7239-435B-9CEB-2A6AD75F141F`, the pair plan 4 uses. Pair them with `xcrun simctl pair <watch> <phone>` unless `xcrun simctl list pairs` already shows a pair. Boot both.
  - Install `build/ios-dd/Build/Products/Debug-iphonesimulator/Taqwa.app`, then `xcrun simctl launch <phone> world.taqwa.ios`.
  - Check `xcrun simctl spawn <phone> log show --last 5m --predicate 'eventMessage CONTAINS "TaqwaWatch"'`. Expected:
    - the session activated, and the Swift line for it names `watchActivated()`, never `watchStateChanged()` (the Swift logs each call it makes into the bridge);
    - once the app has a location, an `isReady()` line with `paired=true installed=false`, and no send. The watch app is plan 4's.
  - **As built (25 October):** the location was written into the app's settings file, with the app stopped: London (51.5074, -0.1278, `Europe/London`, source `MANUAL`, onboarding complete), the keys the onboarding writes. `xcrun simctl location <phone> set 51.5074,-0.1278` alone does not give the app a location, and the onboarding's taps need the simulator tool, which asks Mohamed for access to the device. Then the app was launched again, and its Prayer screen showed London.
  - **Put back:** the pair this step made is removed with `xcrun simctl unpair`, so the two are left as found. Observed on 25 October after that unpair: no pair, both shut down, the Series 12 with no Taqwa app. It never had the watch app, so whether an unpair erases a simulator watch is not known (Ruling 53 item 4); plan 4's `ios-pair` tasks pair and install whenever they find no pair, and put back what they found.

  Only simulators: never the work iPhone 12 or his phones. The end-to-end iPhone check is plan 4's.
- [ ] **Step 6: Commit** with the message "Watch feed: the iPhone hands it to WatchConnectivity as UTF-8 data, damped at each launch, ready for the Apple Watch to read".

---

**After the plan, for the Lead (docs, no code):**
- **Spec 1.1:**
  - the place is what Ruling 39 allows: the city as the Prayer screen names it, the bearing to the phone's whole degree, the declination to a whole degree, the distance to two significant figures, and never a coordinate;
  - the interface language is the app's own `ui_language`, never the locale tag;
  - `ui.language` is the words locale's tag, from which the watch takes letter case alone;
  - `ui.spoken` and `gregorianSpoken` are built on the phone from its own strings and plurals, in the interface language;
  - a phone outside the seven languages: English words, direction and markers; digits are the phone's only in the face's (language, digits) pairs, Western otherwise;
  - the byte budget in UTF-8, and the phrases kept only while they fit.
- **Spec 1.2:**
  - the Android transport is `MessageClient` to nodes nearby at the send, because of the cloud finding;
  - done by Ruling 50 (spec 1.2 rewritten on 25 October): every send carries the install's origin and a delivery number; the watch stores only what moves it forward and echoes what it holds, in its acknowledgement on `/taqwa/ack` and in its handshake; the phone answers `UNCHANGED` only once the watch has echoed the latest delivery, or, on the iPhone, once the latest context was handed over to that very watch;
  - the phone computes each feed's fingerprint and sends it with the feed (the Android path, the iPhone's context key); the watch stores it and echoes it, and never computes one, so the two apps' versions may differ;
  - the watch's request is a handshake carrying that fingerprint, at its start when it holds nothing from the phone, on reconnect, daily, and when the app opens on a feed that needs one;
  - "The send is idempotent" holds for every phone-side ask, a watch coming back and the iPhone's launch included; only the iPhone's watch-state change forgets first, and a watch that holds nothing gets the feed from its own handshake;
  - the iPhone activates whenever `isSupported()`, checks pairing before each send, sends the feed as UTF-8 `Data`, and resends the times without the phrases on `payloadTooLarge`;
  - the clock strings in 12-hour carry no marker;
  - the date words are the platform's CLDR forms, in the interface language, from the phone's own locale only when the tag's own language is the interface language;
  - the phone only asks the sync, which runs off the reminders' path inside one budget.
- **Spec 1.3:** the feed carries, after plan 2, the words the face and the complications draw and say: the prayer names, Jumuʿah, the countdown, the next-in label, the day markers, and the screen reader's countdown sentence and date in words. Its tasbeeh, Qibla and app words join the feed in plans 3 and 4 (the Lead's call 6):
  - each as a defaulted `WatchUi` field, which keeps `VERSION` at 1;
  - filled by the phone's builder, from the phone's own string or, for a new phrase, from `WatchWords` in seven languages;
  - with the byte budget re-checked.
  The watch's own resources hold only the system-shown list, and the two lines a watch shows before any feed.
- **Spec 2.2:** the cloud check is answered, with Google's and Apple's caveats; the refresh request is the handshake.
- **Spec 3.5 (the face's fonts):** Urdu's ۴ ۶ ۷ are Noto's Urdu forms in all three weights (Task 2), and the fonts' new names.
- **Plan 3:**
  - The Tile and Today show the feed's city (spec 2.4, as drawn), and the Qibla screen's caption shows `qiblaKm`, two significant figures. A city name may be as long as the bundle's longest (91 UTF-8 bytes on 23 October, Bengali), so the Tile measures it. The app's screens shake hands as `StatusActivity` does.
  - **Spec 1.3's words, as the Lead's call 6 sets out.** Plan 3 adds to `:watchcore`'s `WatchUi` and to `:shared`'s `WatchFeedBuilder` every word its screens and Tile write: the tasbeeh words ("ROUND %1$s", "of %1$s", "After prayer", the seven built-in names, the bezel hint), the Qibla words ("Facing qibla", "Makkah · %1$s km away", "Hold your wrist level", the calibration, interference and best-effort lines) and the app's (the three screen titles, and "Open Taqwa on your phone" and "Update Taqwa on your watch" for a watch that holds a feed).
    - They come from the phone's own strings (the keys in call 6) or new `WatchWords` phrases, each a field with a default.
    - It re-runs the size tests, regenerates the sample vectors and the goldens, and adds the words' row to the table under Ruling 39.
    - No screen draws its words from the watch's own resources but spec 1.3's system list and the two lines a watch shows before any feed.
  - **The 12-hour spoken time's day period** (Task 12's minor): "at 4:10" says no AM or PM. Plan 3 adds a defaulted field the phone fills with the clock as the Prayer screen says it (its own `clockTime`, marker included, isolated as `isolatedClock` does), and `ComplicationFields` speaks it; the feed's drawn `clock` stays without the marker.
- **Plan 4:**
  - The `Data` context with its `delivery` key, the receivers' rules and the handshake that echoes the delivery (Ruling 50), and the size ship gate (Task 8, "For plan 4").
  - The words only the Apple Watch writes (the crown hint, the Smart Stack's), added the same way. Its Swift decoder reads the goldens as plan 3 regenerated them.
  - The Apple Watch's stored feed is kept out of backup, as the Wear app's is, or the policy's Backups paragraph says so.
  - The iPhone's own copy of the application context: the backup check (Task 8, "For plan 4"), whose simulator evidence goes in plan 4's report. Question 7's Backups sentence stays (plan 4, Ruling 39).
  - The handshake at activation, which heals a reinstalled watch app now that the iPhone's launch ask is damped, and no widget reload for a context whose fingerprint it already holds.
- **Plan 5, ship gate:** every line under Ruling 39's "The lines that change" applied in each of its languages, from Task 3 Step 7's report, before any build with a sender ships.
  - Ruling 39's lines go as ruled ("Only your own devices can see your location." and the one relay sentence), and question 7's as he approves them, the Backups sentence among them.
  - The policy's "Last updated" date moves. The code comments are already done (Tasks 5 and 6).
  - The stores' review notes are part of it: `docs/store/listing/en.md:115` in Play Console and App Store Connect, and the new notes file, within 4,000 bytes, pasted into App Store Connect's Notes field, with `en.md:117` pointing at it.
  - The Data safety and App Privacy answers are made again against Google's and Apple's own definitions at the time (Ruling 39), and Exodus runs on the first build with a sender.
- **The ledger's plan-1 table:** the Urdu digits row closed by Task 2's commit.
- **Ledger:** a closing line for plan 2.
