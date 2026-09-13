# Store privacy answers

The answers for the Google Play and App Store privacy forms, derived from the code so the forms
match it and nobody re-derives them at upload time. The facts behind every answer are the
network inventory in `docs/superpowers/specs/2026-09-13-taqwa-privacy-about-design.md` §1 and
the policy in `PRIVACY.md`. If the code changes what it sends, change the policy first, then this.

Checked against the **merged** release manifest
(`androidApp/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`,
which is what Play reads) and `iosApp/iosApp/Info.plist` on 13 September 2026, during the
store-readiness sweep (targetSdk 36, 0.16.0).

## Google Play — Data safety

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Play defines "collected" as transmitted off the device. Location, settings, bookmarks and counts stay on the device. An IP address seen by GitHub on a file download is not a listed data type. |
| Is all user data encrypted in transit? | n/a | Nothing is collected. (All traffic is HTTPS regardless.) |
| Do you provide a way to request data deletion? | n/a | Nothing is collected. |
| Independent security review | No | — |

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
  schedules an inexact alarm that still fires in Doze, so a notification is at worst a few minutes
  late and never missing. No Play declaration form applies to `SCHEDULE_EXACT_ALARM`.
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
- **Hardware features:** `android.hardware.location`, `.location.network`, `.location.gps` and
  `.sensor.compass` are declared `required="false"`, so the app stays visible on devices without
  them (a city from the built-in list gives the same prayer times).
- **Languages:** English and Arabic, declared through `android:localeConfig` so Android 13+ offers
  per-app language. The bundle keeps both languages on every phone (`bundle.language.enableSplit
  = false`).
- **Target API:** 36 (Android 16), the level Play requires for new apps from 31 August 2026.
- **Backup:** `allowBackup="false"` plus `data_extraction_rules.xml` excluding every domain from
  cloud backup and device transfer.
- **Privacy policy URL:** `https://github.com/MohamedAbulgasem/Taqwa/blob/main/PRIVACY.md`
  (the `AboutLinks.PRIVACY_POLICY` constant; the repository has been public since 13 September
  2026 and the URL answers 200).
- **Upload:** `scripts/release.sh` builds the signed `.aab`; the R8 mapping rides inside it.

## App Store Connect

- **App Privacy:** Data Not Collected. Same definition as Play: nothing leaves the device except
  the download request, and an IP address is not a declared data type.
- **Privacy manifest:** `iosApp/iosApp/PrivacyInfo.xcprivacy` (app) and
  `iosApp/TaqwaWidget/PrivacyInfo.xcprivacy` (widget extension) declare tracking off, no collected
  data types, and the required-reason APIs the binaries use: user defaults (CA92.1 own app,
  1C8F.1 shared with the extension), file timestamps (C617.1, own container only), system boot
  time (35F9.1, elapsed time for playback), disk space (E174.1, free-space check before a
  download).
- **Privacy Policy URL:** the same `AboutLinks.PRIVACY_POLICY` constant.
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
- **Location:** `NSLocationWhenInUseUsageDescription` says the location is never sent anywhere,
  in English and Arabic (`InfoPlist.strings`); `NSLocationDefaultAccuracyReduced` is on, so the
  permission sheet offers "Precise: Off".
