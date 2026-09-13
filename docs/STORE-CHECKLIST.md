# Store launch checklist

What stands between the code on `main` and Taqwa being live on Google Play and the App Store,
after the store-readiness sweep of 13 September 2026. The code side is done; every remaining
item needs Mohamed's accounts, keys or judgement. Tick them in order.

## 1. Already in the code (nothing to do, listed so nobody re-checks)

- **targetSdk 36** (`gradle/libs.versions.toml`), the level Play requires for new apps since
  31 August 2026; verified on an Android 16 emulator (back gestures under predictive back, per-app
  Arabic, exact alarms scheduled) and on the S23.
- **Release signing** reads `keystore.properties` (git-ignored; template in
  `keystore.properties.example`); `scripts/release.sh` builds the signed App Bundle and APK.
- **Manifest**: `MY_PACKAGE_REPLACED` re-arms prayer alarms after every app update; `USE_EXACT_ALARM`
  removed, `SCHEDULE_EXACT_ALARM` kept with an in-app route to "Alarms & reminders"; location and
  compass hardware declared optional; `localeConfig` (English, Arabic); backup opted out twice
  (`allowBackup`, `dataExtractionRules`); a dark window background so a dark phone no longer
  flashes white at launch.
- **iOS**: `PrivacyInfo.xcprivacy` in the app and the widget extension; `InfoPlist.strings` in
  English and Arabic (location text, display name «تقوى»); iPhone-only (`TARGETED_DEVICE_FAMILY 1`,
  via `tools/configure-store-targets.rb`); `arm64`; notifications shown while the app is open;
  export compliance answered in `Info.plist`.
- **Content and licences**: `NOTICE` and the header of `docs/ATTRIBUTION.md` say the GPL covers the
  code only; the Manrope OFL text is in `docs/licences/`; the Mixkit source clip is no longer in
  the repository; the privacy policy URL is live.
- **Store assets in the repo**: 512 px icon `androidApp/src/androidMain/ic_launcher-playstore.png`
  (the real mihrab, regenerated from the 1024 px iOS artwork); feature graphics
  `assets/store/feature-graphic-en.png` and `-ar.png` (1024 × 500, `tools/feature-graphic.py`).
- **Store answers**: `docs/STORE-PRIVACY.md` has every Data safety, App content and App Privacy
  answer with the reason.

## 2. Accounts and keys (Mohamed only)

- [ ] **Apple Developer Program**, individual membership (99 USD a year), enrolled through the Apple
      Developer app on an iPhone with a government ID. The project currently signs with LOOPDL
      LIMITED's team (`iosApp/Configuration/Config.xcconfig`), which is Mohamed's employer and
      cannot publish the app. Xcode's automatic signing has registered `world.taqwa.app`,
      `world.taqwa.app.widget` and the App Group `group.world.taqwa.app` under that team, and
      identifiers are unique across all of Apple, so either an admin of LOOPDL's developer
      portal deletes those three identifiers before the new account registers them, or the iOS
      identifiers are renamed. Then `TEAM_ID` in `Config.xcconfig` changes to the new team and
      the widget target's team with it.
- [ ] **Google Play developer account.** The old one under algiriany93@gmail.com was closed for
      inactivity on 6 April 2024 and cannot be reinstated; a new personal account (25 USD,
      identity verification) is created after 13 November 2023 and therefore needs a closed
      test with **12 testers opted in for 14 consecutive days** before production access.
      Start that test with the current build; updates during the fortnight do not reset it.
- [ ] **Upload keystore** (never in the repository, backed up in two places):

      ```bash
      keytool -genkeypair -v -keystore ~/keys/taqwa-upload.jks -alias taqwa-upload -keyalg RSA -keysize 4096 -validity 10000
      ```

      then copy `keystore.properties.example` to `keystore.properties` and fill in the path,
      alias and passwords. Enrol in **Play App Signing** when creating the app (it is the default);
      Google then holds the app signing key and this key is only the upload key.

## 3. Android: first upload

- [ ] `scripts/test.sh` green, `scripts/ios-build.sh` green.
- [ ] `scripts/bump-version.sh <version> <code>` once (the first store build could be `1.0.0 19`;
      versionCode must only ever go up).
- [ ] `scripts/release.sh` → `androidApp/build/outputs/bundle/release/androidApp-release.aab`.
      Install the sibling APK on the S23 and open it once before uploading.
- [ ] Play Console → create app → **Internal testing** → upload the `.aab`. Fill in, from
      `docs/STORE-PRIVACY.md`: Data safety (collects nothing), Ads (none), Target audience (13+),
      Content rating questionnaire (Reference/Utility, no restricted content), Government/Health/
      Financial/News (no), **Foreground service declarations** for `dataSync` and `mediaPlayback`
      (each wants a short screen recording: download a surah and leave the app; play a surah and
      lock the phone), App access (no login), privacy policy URL.
- [ ] Store listing in the app's seven languages (English, Arabic, French, Turkish, Indonesian,
      Urdu, Bengali): app name «Taqwa» / «تقوى» / «تقویٰ» / «তাকওয়া», short description (≤ 80
      characters), full description (≤ 4000), the 512 icon, the feature graphic, 4–8 phone
      screenshots (the S23 at 1080 × 2340 is accepted; the `site` branch has a full set under
      `docs/screenshots/`, recapture at full size and per language for the localised listings),
      category Lifestyle, contact email.
- [ ] Read the **pre-launch report** on the internal track (it runs the app on real devices and
      flags crashes and accessibility) before promoting.
- [ ] Closed testing (12 × 14 days if required) → production. Staged rollout is fine.

## 4. iOS: first upload

- [ ] Xcode → scheme `iosApp`, Any iOS Device → Product › Archive (Release) → Distribute → App
      Store Connect. Automatic signing under the chosen team; the widget extension archives with
      the app.
- [ ] App Store Connect → new app, bundle id `world.taqwa.app`, SKU anything, primary language
      English, add the six other localisations (Arabic, French, Turkish, Indonesian, Urdu,
      Bengali). **App Privacy: Data Not Collected.** Age rating: none.
      Category: Lifestyle (secondary Reference). Export compliance is answered by `Info.plist`.
- [ ] Screenshots: 6.9-inch (1320 × 2868) from the iPhone 17 Pro Max simulator, English and Arabic
      (`xcrun simctl launch <udid> world.taqwa.app -AppleLanguages "(ar)" -AppleLocale ar_LY` gives
      the Arabic run). Same screens as Android for consistency.
- [ ] **Review notes**: no account; location is optional (Settings › Location lets the reviewer pick
      a city); recitation downloads need internet, everything else is offline; to see the
      lock-screen player, play a surah and lock the phone; the widget is added from the home
      screen's widget gallery.
- [ ] TestFlight to your own iPhone first, then submit.

## 5. After launch, or when time allows (from the audits, none blocks a submission)

- A reciter withdrawn from the catalogue leaves their downloads on the phone with no card to
  delete them from (`RecitationController.allDownloaded` is catalogue-bounded).
- A phone with under ~25 MB free crashes on first launch while installing the Quran database
  (both platforms throw rather than showing "free some space").
- Android: with the 5-minute reminder on and the phone in deep Doze, the adhan can arrive up to
  4 minutes late (one allow-while-idle alarm per 9 minutes); `setAlarmClock()` for the prayer
  entries would fix it at the cost of an alarm icon in the status bar.
- iOS: the widget timeline is reloaded about once a minute while the Prayer screen is open
  (the countdown minute is part of the change key); WidgetKit may throttle.
- iOS: a `CLLocationManager` is retained per Qibla visit (small leak); notification categories
  and actions are not registered (no "snooze"); the whole-Quran download's 6-hour daily data-sync
  budget on Android 15+ has not been exercised on a slow connection.
- `androidx.activity` 1.9.3 predates predictive-back polish; back works but without the system's
  back-to-home animation.
- README (the `site` branch owns the rewrite): roadmap rows, masthead line.
- The Mixkit WAV remains in git history from before the repository was public; purging history
  would rewrite every clone, so it was left.

## 6. What the sweep verified, and where

- Android 16 emulator, release build, targetSdk 36: launch, tab and reader back navigation, per-app
  Arabic through `cmd locale`, exact alarms present in `dumpsys alarm`, no crashes in logcat.
- S23 (Android 16): installed and launched.
- Release APK: 16 KB page-size alignment check passes; only two tiny AndroidX native libraries;
  locales reduced to English and Arabic; R8 mapping and baseline profile inside the bundle.
- iOS simulator build: both privacy manifests and the Arabic `InfoPlist.strings` present in the
  bundle, `UIDeviceFamily` 1, widget extension 3 MB.
- Lint (release): no errors; the remaining warnings are icon-shape notes on legacy PNGs the
  adaptive icon supersedes, dependency-update notices, and deliberate choices already commented
  in the build file.
