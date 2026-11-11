# Store privacy answers

The answers for the Google Play and App Store privacy forms, derived from the code so the forms
match it and nobody re-derives them at upload time. The facts behind every answer are the
network inventory in `docs/superpowers/specs/2026-09-13-taqwa-privacy-about-design.md` §1 and
the policy in `PRIVACY.md`. If the code changes what it sends, change the policy first, then this.

Checked against the **merged** release manifest
(`androidApp/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`,
which is what Play reads) and `iosApp/iosApp/Info.plist` on 13 September 2026, during the
store-readiness sweep (targetSdk 36, 0.16.0).

**Answered again for the watch release (1.6.0), 30 October 2026** (plan 5 Task 9, Ruling 39),
against each store's own definitions, because from this release the phone sends a feed to the
person's own watch. What the feed holds is the policy's "Your watch" section and plan 2's table
(`docs/superpowers/reports/2026-10-23-watch-privacy-wording.md` §1): the city as the Prayer screen
names it, the Qibla's bearing to a whole degree and its distance to two figures, the compass
correction to a whole degree, a flag for the southern hemisphere, a month of prayer times as the
app writes them, the app's words, and the person's own tasbeeh phrases. Beside the feed, never
inside it, travels its delivery (Ruling 50, `WatchLinkContract`'s `WatchDelivery`): the phone
install's **origin**, a random number made once per install and the same in every feed that install
sends (so it is an identifier of the app install), a delivery number that counts up, and the feed's
fingerprint, written `origin/seq/fingerprint` (on Android the Data Layer message's path,
`/taqwa/feed/<origin>/<seq>/<fingerprint>`; on the iPhone the application context's `delivery`
key). Never the coordinates, and nothing goes to Taqwa or to anyone else. The policy says the
same in plain words: its "Your watch" section names the city, the Qibla's direction and distance,
the compass correction, the month's times and the tasbeeh phrases, and the origin as "a random
number made once on your phone that tells your watch which phone sent them"; the hemisphere flag
follows from the city, and the app's words are its own, so it names neither. The merged release
manifests were read again the same day: the phone's permissions below are unchanged, and the Wear
OS app's are in its own section. **Mohamed approved both recommended answers below in chat on 31
October 2026**, with the privacy wording (M3: "M3 approved, with your two suggested tweaks (the
opening and the backups sentence)"; the ledger records the forms' answers approved with it, which
is plan 5's M9), so Task 17 enters them in the consoles as written. His two tweaks change only the
policy's words, not what the feed holds or who can read it: the opening now says "If you add Taqwa
to your watch, its data may travel over the internet too, encrypted, as below", and Backups no
longer says the widgets' backup "is encrypted by Apple and never visible to us", since its next
sentences say how an iPhone backup is encrypted.

## Google Play — Data safety

Google's definitions, read on 24 October 2026 and again on 30 October 2026, in Play Console Help,
"Provide information for Google Play's Data safety section"
(support.google.com/googleplay/android-developer/answer/10787469):
- Collection: "“Collect” means transmitting data from your app off a user’s device."
- Not in scope for collection: data sent off the device that stays "unreadable by you or anyone
  other than the sender and recipient" because of end-to-end encryption need not be disclosed.
- Sharing is transferring collected data to a third party.

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** (recommended, unchanged; approved by Mohamed on 31 October 2026) | The feed, with its delivery and so the install's origin, leaves the phone only for the person's own watch with Taqwa on it (the same package and signing key): directly over Bluetooth when the two are connected, and otherwise through Google's Wear OS Data Layer, which may carry it over Google's servers end-to-end encrypted, so that only the phone and the watch can read it, and neither Taqwa nor Google can. That is the exemption above, so it is not collected; and no third party receives anything, so nothing is shared. Location, settings, bookmarks and counts otherwise stay on the device. An IP address seen by GitHub on a file download is not a listed data type. |
| Is all user data encrypted in transit? | n/a | Nothing is collected. (Recitation traffic is HTTPS; the watch link is end-to-end encrypted.) |
| Do you provide a way to request data deletion? | n/a | Nothing is collected. Deleting Taqwa from the phone and the watch deletes everything it keeps. |
| Independent security review | No | — |

**The fallback**, only if Google's text on the day no longer exempts end-to-end encrypted data:
"Approximate location: collected, not shared, optional, for app functionality, encrypted in
transit, deleted by uninstalling Taqwa from the phone and the watch" (the city and the Qibla's
bearing and distance are approximate location, and so is the hemisphere flag; coordinates never
go). Two more data types would then be answered the same way (collected, not shared, optional,
for app functionality, encrypted in transit, deleted by uninstalling Taqwa from the phone and the
watch): the person's own tasbeeh phrases, as "Other user-generated content" under App activity;
and the install's origin, which every feed's delivery and every echo of it carries, as "Device or
other IDs" (Google: "Identifiers that relate to an individual device, browser or app", read on
30 October 2026).

The Wear OS app is a form factor of the same Play listing (`world.taqwa.app`), so these answers
cover it too: it receives the feed and sends nothing back but its acknowledgement and its
handshake, to the phone alone and by the same link. Each echoes the delivery the watch holds
(Ruling 50): the phone install's origin, the delivery number and the feed's fingerprint, the same
three that came with the feed, or nothing. They name no place and no person, and nothing the
phone did not send.

## Google Play — App content

- **Ads:** No ads.
- **Target audience:** pick 13 and over. Selecting an under-13 band triggers the Families policy
  review; the content is unrestricted, and 13+ avoids that review.
- **Government app / Financial features / Health / News:** No.
- **Foreground service permissions** (targetSdk 34+ requires a declaration and a short video for
  each):
  - `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — Quran recitation keeps playing with the screen off,
    with lock-screen controls. Video: start a surah, lock the phone, show the lock-screen
    controls.
  - `FOREGROUND_SERVICE_DATA_SYNC` — recitation downloads the person started finish while the
    app is in the background. Video: tap Download, leave the app, show the notification.
- **Exact alarms:** the app declares `SCHEDULE_EXACT_ALARM` only. `USE_EXACT_ALARM` was removed
  on 13 September 2026 because Play limits it to alarm-clock, timer and calendar apps and refuses
  the rest at review. Taqwa instead asks for "Alarms & reminders" from Settings › Notifications
  (a button opens the system screen; the note disappears once granted) and, until it is granted,
  schedules an inexact alarm that still fires in Doze, so a notification can arrive up to an hour
  late, as that note itself says. No Play declaration form applies to
  `SCHEDULE_EXACT_ALARM`.
- **Permissions in the merged manifest** (all of them, i.e. what ships):
  - `INTERNET` — recitation downloads and the catalogue check, on request only.
  - `ACCESS_COARSE_LOCATION` — optional; prayer times and Qibla. Fine location is not declared.
  - `POST_NOTIFICATIONS` — prayer notifications.
  - `SCHEDULE_EXACT_ALARM` — notifications at the exact prayer time (see above).
  - `RECEIVE_BOOT_COMPLETED` — re-schedule the alarms after a restart (the same receiver also
    re-schedules after an app update, `MY_PACKAGE_REPLACED`).
  - `VIBRATE` — the Qibla alignment tick and the Tasbeeh pulses.
  - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` —
    as above.
  - `ACCESS_NETWORK_STATE`, `WAKE_LOCK` — merged from WorkManager: the Wi-Fi-only download
    constraint, and keeping the CPU awake while a surah is being written.
  - `world.taqwa.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — a signature-level permission
    androidx.core defines for its own runtime receivers; no other app can hold it.
  - Exodus's report of the APK Google Play delivers (9 October 2026, 1.0.0 (35)) lists a 13th,
    `com.android.vending.CHECK_LICENSE`, which is not in the merged manifest above: Google Play
    adds it when it builds the APKs it serves from the bundle (most likely Play Console's automatic
    protection, under App integrity). It is a Play licensing check, not a tracker; Exodus found 0.
- **Package visibility (1.0.0 (33)):** one `<queries>` element, an intent for
  `android.intent.action.TTS_SERVICE` (`androidApp/src/main/AndroidManifest.xml`), lets read-aloud
  see the phone's text-to-speech engines (Google's, Samsung's) from Android 11 on. It is not a
  permission and needs no Play declaration; Taqwa's manifest does not ask for
  `QUERY_ALL_PACKAGES`. It is the only manifest change since build 32 apart from the source
  folders' rename (`androidMain` → `main`, `androidDebug` → `debug`).
- **Hardware features:** `android.hardware.location`, `.location.network`, `.location.gps` and
  `.sensor.compass` are declared `required="false"`, so the app stays visible on devices without
  them (a city from the built-in list gives the same prayer times).
- **Languages:** English, Arabic, French, Turkish, Indonesian, Urdu and Bengali, declared through
  `android:localeConfig` so Android 13+ offers per-app language. The bundle keeps every language
  on every phone (`bundle.language.enableSplit = false`); iOS carries the same seven as `.lproj`
  folders.
- **Crash report (0.18.0):** an uncaught exception writes a text report on the phone; the next
  launch offers, once, to email it to support@taqwa.world through the user's own mail app. Nothing
  is collected or sent automatically, so the data-safety and App Privacy answers are unchanged
  ("Data Not Collected"); the policy's paragraph on it exists in all seven languages.
- **Report a problem (1.5.1 (42)):** on Android the About screen's support email also carries two
  short lines: whether notifications and exact alarms are allowed, the battery setting (optimised,
  not optimised or restricted) and the ringer; then roughly when the last prayer alert came (marked
  when Android had no channel for it) and when the app was installed or last updated. Not the
  alert's sound level: that is the person's own Taqwa setting, and About, the site and the policy
  say settings stay on the person's own devices. The person sends it from their own mail app, so
  the answers stay "Data Not Collected"; the policy's crash paragraph says so in all seven
  languages, first dated 7 November 2026.
- **Read-aloud (1.0.0 (33)):** during recitation, the phone's own text-to-speech voice can read
  the translation or tafsir after each ayah. Taqwa uses only voices that make their speech on the
  phone (on Android a voice its engine marks as needing no connection and not network synthesis,
  `isOfflineVoice` in `SpeechVoices.android.kt`; on iOS Apple's installed voices) and sends the
  text nowhere itself. On Android the engine is a separate app, Google's or Samsung's, under its
  own settings and terms, and "Get the voice" opens that engine's own voice download. The text
  read is the app's content, not user data, so the data-safety and App Privacy answers are
  unchanged ("Data Not Collected"), and `AVSpeechSynthesizer` is not a required-reason API. The
  privacy policy's own read-aloud paragraph is due before Android production (launch audit C4).
- **Target API:** 36 (Android 16), the level Play requires for new apps from 31 August 2026.
- **Backup:** `allowBackup="false"` plus `data_extraction_rules.xml` excluding every domain from
  cloud backup and device transfer.
- **Privacy policy URL:** `https://taqwa.world/privacy/` (English; the same policy is at
  `https://taqwa.world/<lang>/privacy/` for ar, fr, tr, id, ur and bn, and the About screen opens
  the one for the interface language through `AboutLinks.privacyPolicy`).
- **Upload:** `scripts/release.sh` builds the signed `.aab`; the R8 mapping rides inside it.
- **The watch link (1.6.0):** `play-services-wearable` (the Wear OS Data Layer) adds no permission
  to the phone: its merged release manifest still holds exactly the twelve above (read again on
  30 October 2026). The library adds only its own non-exported `GoogleApiActivity` and its version
  meta-data; Taqwa adds its listener service for the watch's messages, which Play services alone
  wakes, one literal path a filter. Exodus is run again on the first build with a sender, and
  `CONTRIBUTING.md`'s "no trackers" stays only if it still finds none.
- **Exodus, locally (plan 5 Task 17 Step 1, 10 November 2026):** Exodus's tracker list, the body
  of its API's `/api/trackers` (432 trackers, 428 with a code signature; the API refused
  connections that day, so the copy is the Internet Archive's capture of that address on
  17 October 2026), was matched against the release-signed 1.6.0 (44) APKs, the phone's and the
  Wear OS app's, and against the face inside the second (`assets/default_watchface.apk`), the way
  exodus-core matches: every signature longer than three characters, searched as a regex in every
  name. Three lists of names per APK: every package and class `apkanalyzer dex packages` prints,
  defined or referenced (phone 180 packages and 7,718 classes, watch 141 and 4,401); exodus-core's
  own `dexdump` names (831 and 476); and, because R8 moves most classes into no package under
  short names (6,535 of the phone's 6,979, 3,723 of the watch's 3,834), the original names in
  each build's `mapping.txt`, whose `pg-map-id` is the one in its APK (8,013 and 4,630). **No
  signature matched any name.** The face has no code (`hasCode="false"`, no dex). The nearest
  signatures are Google's analytics, ads and Tag Manager packages under `com.google.android.gms`;
  the APKs carry only the Play services classes the watch link needs (`common`, `tasks`,
  `wearable`, and the small `internal`, `signin`, `dynamic`, `auth` and `dynamite`). 1.6.0 (45)
  changes only the three version codes in the build files and nothing in
  `gradle/libs.versions.toml`, so it adds no library. The permissions Exodus will list are the
  phone's twelve above and the watch's four in its section below (Play's APKs may add
  `CHECK_LICENSE`, as above); the face asks for none. The public report of the build Play serves
  is still M14.

## Google Play — the Wear OS app

The Galaxy watch app (`:wearApp`) ships in the same listing as the phone (`world.taqwa.app`, the
Wear OS form factor, version code 100000 + the phone's), so the Data safety answers above are its
answers too.
- **Permissions in its merged release manifest**
  (`wearApp/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`,
  read on 30 October 2026): `VIBRATE` (the tasbeeh ticks and the Qibla's buzz) and androidx.core's
  signature-level `world.taqwa.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. **No `INTERNET` and no
  location permission**: the watch learns its times and the Qibla's bearing from the phone's feed
  alone and is never given coordinates. Watch Face Push adds `PUSH_WATCH_FACES` and the runtime
  `SET_PUSHED_WATCH_FACE_AS_ACTIVE` (plan 5 Task 5); from plan 5 Task 3 on, `wearApp/permissions.txt`
  is the one list of them, and every merged-manifest check reads it.
- **Optional libraries:** `wear-sdk`, `com.google.android.wearable` and the window extensions are
  declared `required="false"`, so no watch is filtered out by them.
- **Backup:** off, as on the phone (`allowBackup="false"` and `data_extraction_rules.xml`): the
  feed names the person's city, and nothing of Taqwa's is copied off the watch.
- **Standalone:** no (`com.google.android.wearable.standalone` is `false`): it needs the phone app.

## App Store Connect

Apple's definition, read on 30 October 2026 in "App privacy details on the App Store"
(developer.apple.com/app-store/app-privacy-details/): data is collected when it is transmitted off
the device "in a way that allows you and/or your third-party partners to access it" for longer
than servicing the request in real time needs; data processed only on the device is not collected.

- **App Privacy:** **Data Not Collected** (recommended, unchanged). Nothing leaves the iPhone for
  Taqwa or a partner except the recitation download request, and an IP address is not a declared
  data type. The feed, with its delivery (the install's random origin, the delivery number and the
  fingerprint, above), goes only to the person's own paired Apple Watch, through WatchConnectivity,
  which Apple carries between the pair (over Wi-Fi or cellular when they are apart) encrypted with
  the pair's keys: neither Taqwa nor any partner of Taqwa's can access it, so it is not collected.
  The watch's handshake goes back to the iPhone alone, the same way, and echoes only that delivery.
  iOS keeps the last copy it sent: WCSession's `applicationContext`, where "the session object
  places a copy of your dictionary". Apple documents neither where WatchConnectivity keeps it nor
  whether an iPhone backup holds it, and WCSession has no way to keep it out, so the policy's
  Backups section says it may be there, and how such a backup is encrypted: in iCloud with keys
  Apple keeps ("Apple can decrypt your data on your behalf", standard data protection), end to end
  only with Advanced Data Protection; on a computer only if the person encrypts the backup. That
  backup is the person's own, which neither Taqwa nor a partner can access, so the answer stands.
  The watch's own copy is kept out of a backup: `FeedStore` marks its directory excluded, and of
  third-party apps Apple lists only their settings in a watch's backup. Apple's pages, read on
  30 October 2026: "iCloud data security overview" (support.apple.com/102651), "What does iCloud
  back up?" (support.apple.com/108770), "Back up your Apple Watch" (support.apple.com/108358),
  "About encrypted backups on your iPhone" (support.apple.com/108353), and the
  `applicationContext` reference. Mohamed approved this answer on 31 October 2026.
- **Privacy manifest:** `iosApp/iosApp/PrivacyInfo.xcprivacy` (app) and
  `iosApp/TaqwaWidget/PrivacyInfo.xcprivacy` (widget extension) declare tracking off, no collected
  data types, and the required-reason APIs the binaries use: user defaults (CA92.1 own app,
  1C8F.1 shared with the extension), file timestamps (C617.1, own container only), system boot
  time (35F9.1, elapsed time for playback), disk space (E174.1, free-space check before a
  download).
- **The Apple Watch app (1.6.0):** it ships inside the iPhone build, at the iPhone's version, under
  the same App Privacy answer. `iosApp/TaqwaWatch/PrivacyInfo.xcprivacy` (the watch app) and
  `iosApp/TaqwaWatchWidgets/PrivacyInfo.xcprivacy` (its complications) declare tracking off, no
  tracking domains, no collected data types, and one required-reason API, the monotonic clock
  (system boot time, 35F9.1: the Qibla's heading filter and the elapsed times the log lines
  measure). The watch app has no network code and asks for no location and no HealthKit
  authorisation (spec 4.1): the Qibla uses the compass heading alone, turned true with the feed's
  declination.
- **Privacy Policy URL:** `https://taqwa.world/privacy/`; each App Store localisation may point at
  its own `https://taqwa.world/<lang>/privacy/`.
- **Export compliance:** exempt. `ITSAppUsesNonExemptEncryption` is `false` in `Info.plist`; the
  app uses only HTTPS to public hosts and has no App Transport Security exception.
- **Content rights:** Yes, the app contains third-party content and you have the rights.
  Recitations under the Islamic Network's non-commercial redistribution terms (plus one bundled
  preview clip per reciter, the corpus's own bytes), Tanzil text and translations, KFGQPC font,
  GeoNames CC BY 4.0, Wikimedia adhan recordings — all listed in `docs/ATTRIBUTION.md` and in the
  app's Attribution screen, which reads the reciter list from the catalogue in force.
- **Advertising Identifier (IDFA):** No.
- **Age rating:** no restricted content; answer "None" throughout.
- **Devices:** iPhone only at launch (`TARGETED_DEVICE_FAMILY = 1`, set by
  `tools/configure-store-targets.rb`); an iPad installs it as an iPhone app. `arm64` is the only
  required capability.
- **Background modes declared:** `fetch` and `audio` (recitation playback), plus the background
  download session identifiers `world.taqwa.app.recitation` and `world.taqwa.app.recitation.mobile`.
- **Notifications:** local only, no push entitlement. Every notification sound is a bundled
  `.caf` under Apple's 30-second limit (the longest is 29.96 s).
- **Location:** `NSLocationWhenInUseUsageDescription` says the location is used to calculate the
  times on the device and that only the person's own devices can see it (Ruling 39), in all seven
  languages (`InfoPlist.strings`, with `Info.plist`'s English as the fallback);
  `NSLocationDefaultAccuracyReduced` is on, so the permission sheet offers "Precise: Off".
