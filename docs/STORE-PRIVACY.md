# Store privacy answers

The answers for the Google Play and App Store privacy forms, derived from the code so the forms
match it and nobody re-derives them at upload time. The facts behind every answer are the
network inventory in `docs/superpowers/specs/2026-09-13-taqwa-privacy-about-design.md` §1 and
the policy in `PRIVACY.md`. If the code changes what it sends, change the policy first, then this.

Checked against `androidApp/src/androidMain/AndroidManifest.xml` and `iosApp/iosApp/Info.plist`
on 13 September 2026 (0.12.0).

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
- **Foreground service permissions** (targetSdk 35 requires a declaration and a short video for
  each):
  - `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — Quran recitation keeps playing with the screen off,
    with lock-screen controls. Video: start a surah, lock the phone, show the lock-screen
    controls.
  - `FOREGROUND_SERVICE_DATA_SYNC` — recitation downloads the person started finish while the
    app is in the background. Video: tap Download, leave the app, show the notification.
- **Permissions declared** (all of them, from the manifest):
  - `INTERNET` — recitation downloads and the catalogue check, on request only.
  - `ACCESS_COARSE_LOCATION` — optional; prayer times and Qibla. Fine location is not declared.
  - `POST_NOTIFICATIONS` — prayer notifications.
  - `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` — notifications at the exact prayer time.
  - `RECEIVE_BOOT_COMPLETED` — re-schedule the alarms after a restart.
  - `VIBRATE` — the Qibla alignment tick and the Tasbeeh pulses.
  - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` —
    as above.
- **Privacy policy URL:** `https://github.com/MohamedAbulgasem/Taqwa/blob/main/PRIVACY.md`
  (the `AboutLinks.PRIVACY_POLICY` constant; resolves once the repository is public).

## App Store Connect

- **App Privacy:** Data Not Collected. Same definition as Play: nothing leaves the device except
  the download request, and an IP address is not a declared data type.
- **Privacy Policy URL:** the same `AboutLinks.PRIVACY_POLICY` constant.
- **Export compliance:** exempt. `ITSAppUsesNonExemptEncryption` is `false` in `Info.plist`; the
  app uses only HTTPS to public hosts and has no App Transport Security exception.
- **Content rights:** Yes, the app contains third-party content and you have the rights.
  Recitations under the Islamic Network's non-commercial redistribution terms, Tanzil text,
  KFGQPC font, GeoNames CC BY 4.0, Wikimedia adhan recordings — all listed in
  `docs/ATTRIBUTION.md`.
- **Advertising Identifier (IDFA):** No.
- **Age rating:** no restricted content; answer "None" throughout.
- **Background modes already declared:** `fetch` and `audio` (recitation playback), plus the
  background download session identifier `world.taqwa.app.recitation`.
- **Location:** `NSLocationWhenInUseUsageDescription` says the location is never sent anywhere;
  `NSLocationDefaultAccuracyReduced` is on, so the permission sheet offers "Precise: Off".
