# Store launch runbook

Everything between the code on `main` and Taqwa being live on Google Play and the App Store, in
the order to do it. Rewritten 14 September 2026 for the accounts Mohamed decided on: a new Google
account **mohamed.abulgasem.ly@gmail.com** for Google Play and a new Apple Account with the same
address for the App Store. LOOPDL LIMITED (team 5S5P2Q72MV) is his employer and is never used to
publish. The code side is done (§8); what remains is two accounts, one key, listings, uploads and
waiting.

Two clocks decide the order. Google lets a personal account publish only after a closed test with
12 testers opted in for 14 consecutive days; Apple's enrolment takes a day or two. Start both
accounts on day one: the App Store can be live within about a week, Google Play in three to five.

## 1. Decisions (made, so nobody re-decides at the keyboard)

- **Store version 1.0.0 (28)**, cut on 16 September for Play's internal test: the first bundle
  signed with the upload key, and the version the iOS archive carries too. Every later store
  build takes the next code (29, 30, …); codes 18–27 were device builds and never left the Mac.
- **Public identity:** seller/developer name "Mohamed Abulgasem" on both stores (Apple shows an
  individual's legal name; Play matches it), public contact email **support@taqwa.world**
  (already forwards to Gmail), website https://taqwa.world, privacy policy
  https://taqwa.world/privacy/ (App Store localisations may use https://taqwa.world/<lang>/privacy/).
  mohamed.abulgasem.ly@gmail.com is the login, not the public contact.
- **Names in the stores:** Play title "Taqwa: Prayer, Quran & Qibla" (28 of 30 characters). App
  Store name "Taqwa" if it is free (App Store names are unique across the whole store and a
  five-letter Arabic word is probably taken), otherwise the same 28-character title; subtitle
  "Prayer times, Quran & Qibla". Outcome, 22 September: the App Store record is named "Taqwa:
  Prayer, Quran & Qibla", because plain "Taqwa" is taken (so are a dozen look-alikes such as
  "Taqwa - Prayer Times & Quran", checked 24 September), and that subtitle repeats the name. From
  the next version each App Store language gets a translated name carrying prayer, Quran and
  Qibla, a subtitle carrying adhan, tasbeeh and no ads, and keywords with neither's words: the
  values sit in `docs/store/listing/<lang>.md` (App Information for name and subtitle, the
  version page for keywords). A translated name refused as taken keeps the English title and the
  1.0.0 subtitle for that language.
- **Category:** Lifestyle on both (Apple secondary: Reference). Primary language English (United
  Kingdom); listings in all seven languages.
- **Age:** Play target audience 13+ (an under-13 band triggers the Families review); content
  rating questionnaire as "Reference, News, or Educational" with every restricted-content answer
  "No" → rated for everyone. Apple's age rating questionnaire all "None" → 4+.
- **EU trader status:** declare **non-trader** on both stores (free app, no monetisation, no
  business). Both consoles ask; it is a legal statement Mohamed makes, not a build setting.
- **iOS identifiers:** Xcode registered `world.taqwa.app`, `world.taqwa.app.widget` and
  `group.world.taqwa.app` under LOOPDL's team, and Apple identifiers are unique across all
  teams. **Done 22 September:** the iOS identity is `world.taqwa.ios`, `world.taqwa.ios.widget`
  and `group.world.taqwa.ios` (and `world.taqwa.ios.refresh` for the background task). One
  source of truth: `iosApp/Configuration/Config.xcconfig` holds `TEAM_ID` and `BUNDLE_ID`, and
  both targets read them (the widget is `$(BUNDLE_ID).widget`); the app group is literal in the
  two `.entitlements`, `TaqwaWidgetViews.swift` and `widgetcore/…/KeyValueStore.ios.kt`, which
  must agree. `TEAM_ID` stays empty until Mohamed's individual Team ID is in — never LOOPDL's
  `5S5P2Q72MV`. Nobody sees a bundle id and the Android package stays `world.taqwa.app`.
- **Release timing:** iOS releases automatically on approval; Android follows when the closed
  test ends. The site's store buttons go live one at a time.
- **Screenshots:** Play gets framed 1080 × 1920 images (the phone slot wants 16:9 or 9:16 and
  flags a raw 1080 × 2340 capture as too tall); the App Store gets raw 1320 × 2868 captures from
  the iPhone 16 Pro Max simulator (the 6.9-inch slot, which Apple scales down for smaller
  phones). Seven languages each, eight screens each: Prayer, Quran list, Reader, Mushaf,
  Recitation, Qibla, Tasbeeh, Settings.
- **The framed sets are checked in** at `docs/store/screenshots/{play,appstore}/<lang>/` (since
  18 September). A feature that changes a screen re-shoots that screen in the same piece of work
  — `ONLY="2-notifications" tools/store/capture-android.sh <serial> en ar fr tr id ur bn`, the
  same for `capture-ios.sh`, then `tools/store/assemble.sh` — and commits the result.
  `git status docs/store/screenshots` (or the commit's file list) is what to upload to each
  store by hand. Play's release notes per build live in `docs/store/release-notes/`.
- **Recruiting testers (since 20 September):** the closed track takes its testers from the Google
  Group `taqwa-testers@googlegroups.com` (anyone can join; the console allows email lists *or*
  groups, not both). The site's home pages and the README carry a "Join the Android beta"
  section with the three links — group, `play.google.com/apps/testing/world.taqwa.app`, store
  page. **At launch:** `tools/site-beta.py remove && python3 site/build.py`, and delete the
  README block between its `beta:start`/`beta:end` markers.
- **Version until production:** the name stays 1.0.0 and only the code moves (28, 29, …); see
  `scripts/bump-version.sh`. Settings › About shows the name alone; Mohamed tells testers which
  build they are on.

## 2. Accounts (Mohamed, about an hour at the keyboard, then waiting)

### 2a. The Google account

1. In a private window → https://accounts.google.com/signup → "For my personal use" → name and
   birthday → choose the address mohamed.abulgasem.ly@gmail.com (Google proposes alternatives if it
   is taken) → password → phone number (SMS code) → recovery email algiriany93@gmail.com.
2. Security → turn on **2-Step Verification** (Play Console requires it for the account owner;
   the Google prompt on the S23 or an authenticator app both work). Add a passkey if offered.
3. Keep this account for Taqwa only; the S23 keeps its own main account. The new account's
   YouTube channel is where the two foreground-service demo videos go later (unlisted).

### 2b. Google Play Console (25 USD once)

1. https://play.google.com/console/signup, signed in as the new account → **Yourself** (a
   personal account; an organisation would need a D-U-N-S number).
2. Developer account details: **developer name** "Mohamed Abulgasem" (public), legal name and
   address exactly as on the ID and the card, **contact email** support@taqwa.world (Google sends
   a code there; it lands in Gmail), contact phone (SMS code), preferred language.
3. "About you": no other developer accounts (the one closed in April 2024 is history), one app,
   free, personal project.
4. Pay the 25 USD registration with a card in the same name; the payments profile is created as
   **Individual**. Then **verify identity**: a government photo ID (passport or driving licence),
   sometimes a proof of address as well. Google answers by email, usually within a couple of
   days, and nothing can be published until it does.
5. The console shows exactly what will be public on the listing before the account is finished
   (developer name, contact email, country); read that screen before confirming. It also asks the
   EU trader question → non-trader.
6. Optional: Users and permissions → invite algiriany93@gmail.com as an Admin, so the old account
   can also sign in to the console.

### 2c. Apple Account and the Developer Program (99 USD a year)

1. https://account.apple.com → **Create Your Apple Account** → legal name, country of residence
   (must match the ID and the card), birthday, email mohamed.abulgasem.ly@gmail.com, password, phone
   (SMS code); verify the email code. Turn on **two-factor authentication** (Sign-In and Security)
   with the phone number; the Developer Program refuses accounts without it.
2. On the iPhone 12: install the **Apple Developer** app from the App Store → Account tab → sign
   in with the **new** account (this does not touch the phone's own iCloud account) → **Enroll
   Now** → Individual / Sole Proprietor → personal details as on the ID → scan the ID (and a
   selfie if asked) → agree to the licence agreement → buy the membership. The 99 USD purchase
   goes through the App Store on that phone, so its payment method is charged. Web enrolment
   (https://developer.apple.com/enroll) is the fallback if the app misbehaves.
3. If enrolment complains about a brand-new account, wait a day and retry; Apple's identity check
   on fresh accounts is known to stall. The welcome email normally arrives within 48 hours.
4. After the welcome email: https://appstoreconnect.apple.com → sign in → accept the Apple
   Developer Program License Agreement if a banner asks (only the Account Holder can). A free app
   needs no Paid Apps agreement, bank account or tax forms.
5. Read the **Team ID** at https://developer.apple.com/account → Membership details (ten
   characters) and pass it on; it replaces `TEAM_ID` in `iosApp/Configuration/Config.xcconfig`
   and the widget target's team.
6. On this Mac: Xcode → Settings → Accounts → **+** → Apple Account → sign in with the new account.
   The account that carries LOOPDL may stay signed in, but that team is never selected for Taqwa
   again. From then on automatic signing also works from the command line
   (`-allowProvisioningUpdates`), and the first archive registers the bundle ids and the App Group
   under the new team.

### 2d. Testers for Google's closed test

Recruit **15–20 people** with an Android phone and a Google account (12 is the minimum and the
count must never dip below it during the 14 days). Each needs: their Gmail address on the tester
list, one tap on the opt-in link, the app installed from Play, and to keep it installed for two
weeks. Family and friends anywhere in the world count. Ask now; the list is needed the day the
first bundle is uploaded.

## 3. The upload key (Mohamed, two minutes)

```bash
scripts/make-upload-key.sh
```

It asks for one password (12+ characters), writes `~/keys/taqwa-upload.jks` and the git-ignored
`keystore.properties` that `scripts/release.sh` reads, and prints the certificate fingerprint.
Back up the `.jks` and the password in two places (password manager plus an offline copy). With
Play App Signing, on by default for every new app, Google holds the key that signs what phones
install and this one only signs uploads; a lost upload key is reset through Play support, which
costs days.

## 4. Built here before the uploads (nothing needs an account)

- Listing copy in seven languages: title and subtitle, short description (≤ 80), full description
  (≤ 4000), App Store keywords (≤ 100 characters), release notes for 1.0.0, review notes. Mohamed
  reads English and Arabic; the native reviewers read the rest.
- Screenshots per §1, plus the 512 icon (`androidApp/src/main/ic_launcher-playstore.png`)
  and the 1024 × 500 feature graphics already in `assets/store/`.
- Two screen recordings for Play's foreground-service declarations, recorded on the emulator on
  16 September: `~/Downloads/Taqwa-fgs-media-playback.mp4` (a surah playing, the app left, the
  media notification with its controls, 29 s) and `~/Downloads/Taqwa-fgs-data-sync.mp4` ("Download
  the whole Quran", the app left, the download notification counting up, 44 s). Mohamed uploads
  them unlisted to the new account's YouTube channel and pastes the two links into the
  declaration form (mediaPlayback and dataSync).
- The store build: `scripts/test.sh`, `scripts/ios-build.sh`, the bump (done for 1.0.0 (28)),
  the iOS identifier rename and Team ID, then `scripts/release.sh` →
  `androidApp/build/outputs/bundle/release/androidApp-release.aab` (+ APK). The APK goes on the
  S23 once before upload. The S23 and the LoopPhone carry debug-key-signed builds, so the Play copy
  will not install over them: uninstall first (bookmarks and tasbeeh counts are lost; backup is
  off by design).
- Site: `site/pages/<lang>/home.html` store buttons get their real links as each store goes live.

## 5. Google Play, in console order

1. **Create app**: All apps → Create app → name "Taqwa: Prayer, Quran & Qibla", default language
   English (United Kingdom), App, Free (a free app can never become paid), tick the declarations
   → Create.
2. **Dashboard → Set up your app**, every answer from `docs/STORE-PRIVACY.md`: privacy policy URL;
   App access (all functionality available without special access); Ads (no); Content rating
   (questionnaire, category Reference/News/Educational, everything "No"); Target audience (13–15,
   16–17, 18+; does not unintentionally appeal to children); News (no); COVID-19 (no); Data
   safety (does not collect or share any user data); Government app (no); Financial features
   (none); Health (none); Advertising ID (no); **Foreground service permissions** (mediaPlayback
   and dataSync, each with a sentence from STORE-PRIVACY and the YouTube link); Store settings
   (category Lifestyle, tags, contact email support@taqwa.world, website).
3. **Main store listing**: title, short and full description, the 512 icon, the feature graphic
   (`assets/store/feature-graphic-en.png`), 4–8 phone screenshots; then Manage translations →
   Add languages for ar, fr, tr, id, ur, bn with their own text and screenshots (and the Arabic
   feature graphic).
4. **Internal testing** first: Testing → Internal testing → Testers (an email list with Mohamed's
   own Gmail addresses) → Releases → Create → upload the `.aab`; on the first upload Play App
   Signing offers a Google-generated key, accept it → Save → Review → Start rollout. The opt-in
   link is under Testers; install on the S23 from Play and open every tab once. The
   **pre-launch report** (real devices, crashes, accessibility) appears here too: read it.
5. **Closed testing**: Testing → Closed testing → the default Alpha track → Testers → a list with
   the 15–20 addresses → Releases → Create → add the same bundle from the library → Save → Review →
   Start rollout. Copy the opt-in link from the Testers tab and send it to everyone. The dashboard
   counts opted-in testers and days; **12 for 14 consecutive days** is the bar. Builds may be
   updated during the fortnight without resetting the clock.
6. **Apply for production access**: when the dashboard offers it (after the 14 days), answer the
   questions about the test (who tested, what feedback came back, what changed). Google answers
   within about seven days.
7. **Production**: Production → Countries/regions → add all → Releases → Create → add the bundle
   → release notes → Review → Start rollout (staged or 100 %, both fine). A new account's first
   production review can take up to a week. Then the listing is live at
   https://play.google.com/store/apps/details?id=world.taqwa.app.

## 6. App Store, in App Store Connect order

1. **Signing**: with the Team ID in `Config.xcconfig` and the widget target, and the new account
   in Xcode, one device archive registers `world.taqwa.ios`, `world.taqwa.ios.widget` and the App
   Group under the new team (visible at https://developer.apple.com/account/resources/identifiers/list).
2. **Archive and upload**: `scripts/test.sh` and `scripts/ios-build.sh` green, then
   `scripts/ios-release.sh archive` (the archive lands in `build/ios-archive/`, and the script
   prints the built identifiers, team, app group and widget size back), the app record (step 3),
   then `scripts/ios-release.sh upload`. The script signs automatically through the Apple account
   signed into Xcode 26 and refuses an empty or LOOPDL team. If the command-line upload is refused,
   open the `.xcarchive` in Xcode (Window › Organizer) → Distribute App → App Store Connect →
   Upload, automatic signing, symbols included. The build appears under TestFlight after 10–30
   minutes of processing. Export compliance is answered by `ITSAppUsesNonExemptEncryption =
   false`, so no encryption question appears. Tag the archived commit `ios-build-<code>`.
3. **New app record**: My Apps → + → New App → iOS, name (§1), primary language English (U.K.),
   bundle ID `world.taqwa.ios` from the list, SKU `taqwa-ios`, full access → Create. Done 22
   September: Apple ID **6814975544**, so the store link is https://apps.apple.com/app/id6814975544
   (the site's App Store button at launch). Build 1.0.0 (32) uploaded the same night.
4. **App Information**: subtitle, categories Lifestyle + Reference, content rights (contains
   third-party content, rights held: `docs/ATTRIBUTION.md`), age rating questionnaire (all None →
   4+), standard licence agreement. Localizations: add ar, fr, tr, id (name, subtitle, privacy
   URL per language). App Store Connect offered no Urdu or Bengali listing in September 2026, so
   those users see the English one; the app itself still opens in their language. Apple listed
   both as App Store languages on 31 March 2026, but developers reported that adding them failed
   ("The language specified is not listed for localization"); look again at every new version,
   since `ur.md` and `bn.md` and their screenshots are ready.
5. **Pricing and Availability**: Free, all countries and regions. **App Privacy**: Get started →
   no data collected → Publish ("Data Not Collected"). **Trader status** when the banner asks:
   non-trader.
6. **Version 1.0.0 page**: screenshots (the 6.9-inch set per language; a localisation without its
   own falls back to English), promotional text, description, keywords, support URL
   https://taqwa.world/support/, marketing URL https://taqwa.world, copyright
   "2026 Mohamed Abulgasem", App Review contact (name, phone, email), sign-in required: No,
   **review notes** (no account; location optional, Settings › Location picks a city; only
   recitation downloads use the network; play a surah and lock the phone for the lock-screen
   player; widgets come from the home-screen gallery), select the processed build, release
   automatically after approval.
7. **TestFlight** before submitting: TestFlight → Internal Testing → a group with Mohamed as
   tester → the TestFlight app on the iPhone 12 installs the exact store build. Caution: TestFlight
   uses the Apple Account signed into the phone's App Store, and a tester links to one Apple
   Account only — accepting Mohamed's invitation on the work iPhone (signed in with a work Apple
   Account) would bind that account to his tester slot, and undoing it can take up to 90 days.
   Without TestFlight, `build/ios-archive/dev-<v>-<code>/` holds the archive re-signed for
   development (`xcodebuild -exportArchive`, method `debugging`): the same compiled binary as the
   upload, installable on a registered phone with `xcrun devicectl device install app`. This is the first
   hardware run of the whole app on iOS; the simulator cannot prove the compass heading, the
   lock-screen controls or the widget timelines, so check those three.
8. **Submit for Review** → Waiting for Review → In Review → Ready for Distribution, typically one
   to three days. A new developer account first gets "Guideline 2.1 - Information Needed - New
   App Submission" (it happened on 23 September 2026, answered and resubmitted on 24 September
   with the recording attached): a screen recording on a physical iPhone on
   the latest iOS, starting at launch, plus six written answers, sent as a reply (Resolve → Reply
   to App Review → Attach File) and pasted into App Review Information → Notes, then Edit → Add for
   Review → Resubmit to App Review with the same build. The answers are in
   `docs/store/app-review/2026-09-23-guideline-2.1-reply.txt` (3,814 of the 4,000 characters both
   fields allow); keep Notes in step with the app on every later submission. "Metadata Rejected" or "Information Needed" is answered in App Store Connect's
   App Review messages without a new build; a binary rejection needs a fix, the next build number
   (33, 34, …, shared with Android) and a resubmission.

## 7. Cost and calendar

| | Google Play | App Store |
|---|---|---|
| Fee | 25 USD once | 99 USD every year |
| Account ready | 1–3 days (identity check) | 1–2 days (enrolment) |
| Before publishing | closed test 14 days, then production access ≤ 7 days | TestFlight, no waiting period |
| Review | 1–7 days | 1–3 days |
| Live, starting today | 3–5 weeks | about a week |

## 8. Already in the code (listed so nobody re-checks)

- **targetSdk 36** (`gradle/libs.versions.toml`), the level Play requires for new apps since
  31 August 2026; verified on an Android 16 emulator and on the S23.
- **Release signing** reads `keystore.properties` (git-ignored; `scripts/make-upload-key.sh`
  writes it); `scripts/release.sh` builds the signed App Bundle and APK with the R8 mapping
  inside the bundle.
- **Manifest**: `MY_PACKAGE_REPLACED` re-arms prayer alarms after every update; `USE_EXACT_ALARM`
  removed, `SCHEDULE_EXACT_ALARM` kept with an in-app route to "Alarms & reminders"; location and
  compass hardware optional; `localeConfig` lists the seven languages; backup opted out twice
  (`allowBackup`, `dataExtractionRules`); dark window background at launch.
- **iOS**: `PrivacyInfo.xcprivacy` in the app and the widget extension; `InfoPlist.strings` in the
  seven languages; iPhone-only (`TARGETED_DEVICE_FAMILY 1`, `tools/configure-store-targets.rb`);
  `arm64`; export compliance answered in `Info.plist`.
- **Content and licences**: `NOTICE` and `docs/ATTRIBUTION.md` say the GPL covers the code only;
  the Manrope OFL text is in `docs/licences/`; the Mixkit source clip is no longer in the
  repository; the privacy policy is live at taqwa.world in seven languages and the About screen
  opens it.
- **Store assets in the repo**: 512 px icon `androidApp/src/main/ic_launcher-playstore.png`;
  feature graphics `assets/store/feature-graphic-{en,ar}.png` (1024 × 500, `tools/feature-graphic.py`).
- **Store answers**: `docs/STORE-PRIVACY.md` has every Data safety, App content and App Privacy
  answer with the reason.

## 9. After launch, or when time allows (from the audits, none blocks a submission)

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
- The Mixkit WAV remains in git history from before the repository was public; purging history
  would rewrite every clone, so it was left.
- Surah meanings and city regions are English in every language (data, not strings); Urdu and
  Bengali still deserve a human native read.

## 10. What the store-readiness sweep verified (13 September 2026), and where

- Android 16 emulator, release build, targetSdk 36: launch, tab and reader back navigation, per-app
  Arabic through `cmd locale`, exact alarms present in `dumpsys alarm`, no crashes in logcat.
- S23 (Android 16): installed and launched.
- Release APK: 16 KB page-size alignment check passes; only two tiny AndroidX native libraries;
  R8 mapping and baseline profile inside the bundle.
- iOS simulator build: both privacy manifests and the localised `InfoPlist.strings` present in the
  bundle, `UIDeviceFamily` 1, widget extension 3 MB.
- Lint (release): no errors; the remaining warnings are icon-shape notes on legacy PNGs the
  adaptive icon supersedes, dependency-update notices, and deliberate choices already commented
  in the build file.
