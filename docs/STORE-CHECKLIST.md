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
  1.0.0 subtitle for that language. Cancelling the release of 1.0.0 (32) on 29 September made
  them editable again, so they go in with 1.0.0 (34) rather than waiting for the next version.
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
  must agree. `TEAM_ID` is `3K93P4PA5H`, Mohamed's individual team, set on 22 September — never
  LOOPDL's `5S5P2Q72MV`. Nobody sees a bundle id and the Android package stays `world.taqwa.app`.
- **The launch is split by store: iOS first, Android after production access.** iOS 1.0.0 goes
  to App Review with **"Manually release this version"**: approval parks it in Pending Developer
  Release, and Mohamed presses Release This Version when he chooses (a soft launch while
  Google's closed test runs). Android reaches production only after the closed test (12 testers
  opted in for 14 days) and Google's answer to the production-access application (§5.6–5.7), so
  some days or weeks later. The site's store buttons, the beta section and the README go live
  one store at a time: the steps for each are below and in §6.10.
- **Screenshots:** Play gets framed 1080 × 1920 images (the phone slot wants 16:9 or 9:16 and
  flags a raw 1080 × 2340 capture as too tall); the App Store gets framed 1320 × 2868 images made
  from simulator captures (the 6.9-inch slot, which Apple scales down for smaller phones). Seven
  languages each. Play has eight screens: 1 Prayer, 2 Notifications, 3 Reader, 4 Mushaf,
  5 Recitation, 6 Qibla, 8 Prayer in Dark, 9 Morning and evening adhkar (the adhkar took Tasbeeh's place in
  1.1.0); the App Store has eight too, with 7 Tasbeeh and without Qibla, because the simulator has no compass. 1 and 8 show the Prayer screen, so a change to its
  times or its ⓘ means re-shooting both, as the engine did (launch audit A8).
- **The framed sets are checked in** at `docs/store/screenshots/{play,appstore}/<lang>/` (since
  18 September). A feature that changes a screen re-shoots that screen in the same piece of work
  — `ONLY="2-notifications" tools/store/capture-android.sh <serial> en ar fr tr id ur bn`, the
  same for `capture-ios.sh`, then `tools/store/assemble.sh` — and commits the result.
  `git status docs/store/screenshots` (or the commit's file list) is what to upload to each
  store by hand. Play's release notes per build live in `docs/store/release-notes/`.
  **Re-shoot only from the main checkout** (`~/Desktop/Workspace/apps/Taqwa`), never from a
  worktree: `assemble.sh` starts with `rm -rf docs/store/screenshots/play docs/store/screenshots/appstore`
  and frames back only what `build/store-shots/{android,ios}/<lang>/` holds. `build/` is
  git-ignored, so the raw captures, all eight per language, exist only in the main checkout's
  `build/store-shots`; run from a worktree, `assemble.sh` deletes both sets and writes back only
  the screens just shot.
- **Recruiting testers (since 20 September):** the closed track takes its testers from the Google
  Group `taqwa-testers@googlegroups.com` (anyone can join; the console allows email lists *or*
  groups, not both). The site's home pages and the README carry a "Join the Android beta"
  section with the three links — group, `play.google.com/apps/testing/world.taqwa.app`, store
  page. **At Android production**, not at the iOS release: `tools/site-beta.py remove && python3
  site/build.py`, and delete the README block between its `beta:start`/`beta:end` markers. The
  hero's "Join the Android beta" button goes with the section by itself. **At the iOS release**
  the section stays, because the closed test is still running, but its heading "Help test Taqwa
  before it launches." stops being true: re-word it for Android in all seven languages in
  `tools/site-beta.py` (en: "Help test Taqwa for Android before it reaches Google Play.") and
  run `tools/site-beta.py add && python3 site/build.py` (`add` strips and re-inserts, so it can
  run again). **Done 8 October 2026,** with Taqwa live on Google Play: the section is off all
  seven home pages and the README's block is gone. `tools/site-beta.py add` puts the section back
  for a future test.
- **The site's store badges** come from `site/stores.json`, one address per store, `null` until
  live: set `google_play` to `https://play.google.com/store/apps/details?id=world.taqwa.app`
  when production opens, `app_store` to `https://apps.apple.com/app/id6814975544` when Apple
  releases it (check the id in App Store Connect first), and push. Each store's official badge
  then replaces its "coming" wording on all seven home pages, and the App Store one also turns on
  Safari's Smart App Banner on every page. Once the Play listing is public, request its report at
  reports.exodus-privacy.eu.org and set `exodus` to its address: the privacy section links it.
  The city pages' two app places — the desktop pitch and the section `#app` at the foot of every
  city page (`site/timetables.py`, through `site/stores.py`, the same function as the hero) —
  switch with the same edit; the Today card's foot keeps its "Get Taqwa" pill, which points to
  `#app`. After the build open one city page and check the badge shows in the pitch and in `#app`.
  **Done 8 October 2026:** `google_play` is set, so every home page and city page carries both
  badges and no "coming" line. **Done 9 October 2026:** Exodus's report of the Play build 1.0.0
  (35) (report 787937: 0 trackers, 13 permissions) is published, and `exodus` points to the
  package's latest report (`/reports/world.taqwa.app/latest/`, no language prefix, so Exodus
  serves the reader's language where it has one and each new Play version's report replaces it).
  Request a new analysis at reports.exodus-privacy.eu.org/en/analysis/submit/ after each Play
  release so the latest report is the current version.
- **Campaign tags on the badges, and the download links (since 11 October 2026):** the badges
  appear in three places: every home page's hero, the two app places on every city page (the
  desktop pitch and `#app`), and the download links at `taqwa.world/get/` and
  `taqwa.world/get/<channel>/` (English, one per channel). Each place's badges lead to the stores
  with that place's tags from `site/campaigns.json`, built in `site/campaigns.py` from the bare
  addresses in `site/stores.json` (whose validation stays strict: never put a tagged link there):
  the home pages as channel `site-home`, the city pages as `site-city`, each `/get/<channel>/`
  page as its channel, and `/get/` itself untagged. On a phone a `/get/` page goes straight on to
  the phone's store; a computer sees the two badges. Google Play's link carries the tags in
  `&referrer=` (Play Console's acquisition reports, by UTM source and UTM campaign; only visits in
  the Play Store app count, not play.google.com in a browser). The structured data keeps the bare
  addresses, and so does Safari's Smart App Banner: Apple documents no syntax for campaign tokens
  there (see `app_banner` in `site/stores.py`). The pages count no one (no cookie, no analytics,
  nothing stored); the stores count, in aggregate, in their own consoles.
  **The App Store provider token** (only Mohamed can do this): in App Store Connect open Taqwa in
  Analytics → Acquisition → Campaigns → **+**, name a campaign `poster`, and copy the generated
  link. Its `pt=` number is the provider token, the same for every campaign of the account (Apple
  shows the button only once the app has analytics data). Set it in `site/stores.json` as a string
  of digits, `"app_store_provider_token": "123456789"`, run `python3 site/test_campaigns.py &&
  python3 site/build.py --check`, and push. Until then every App Store link stays the bare address,
  since a campaign token without the provider token counts nothing; from then the App Store badges
  and `/get/` links carry `pt`, `ct=<channel>` and `mt=8`. App Store Connect shows a campaign's
  figures only once they reach Apple's minimum (five, in its help).
  **Adding a channel:** one line in `site/campaigns.json`. The name becomes the address
  `taqwa.world/get/<name>/` and the App Store's campaign token, so it is at most 30 characters of
  a-z, 0-9 and single hyphens; give it `utm_source` (where the link sits), `utm_medium` (what kind of
  place) and `utm_campaign` (the name itself, so both consoles list the same name), all lower case.
  The build refuses anything malformed, a name used twice, and two channels with the same three
  tags. Run the two commands above and push; the page is live after the Pages run. A channel whose
  address is printed or posted is never renamed or removed: its page would be gone (a 404) under
  every poster and post that carries it.
- **Version until production:** the name stays 1.0.0 and only the code moves (28, 29, …); see
  `scripts/bump-version.sh`. Settings › About shows the name alone; Mohamed tells testers which
  build they are on.
- **Versioning for a split launch** (proposed 29 September; Mohamed decides the tag): a code
  means the same build on both stores and only goes up (33 and 34 were cut for both). iOS 1.0.0
  (34) is the first production release. Until Apple releases 1.0.0, a replacement build keeps
  the name and takes the next code (1.0.0 (35)). Once 1.0.0 is live on the App Store, App Store
  Connect needs a new version name for any new iOS release, so every later release build follows
  the rule in `scripts/bump-version.sh` on both platforms (1.0.1 for fixes only, 1.1.0 if
  anything visible changed): one name per code everywhere. Android's first production release
  is whatever the closed track carries when production access comes: 1.0.0 (34) if nothing newer
  was cut, otherwise that newer name, since Play needs only a higher code. `v1.0.0` still points
  at 500f1e5f (1.0.0 (28), Play internal); moving it to 4af923ac, the build that reaches
  production first, is Mohamed's call.

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

## 3. The upload key and the face key (Mohamed, two minutes each)

```bash
scripts/make-upload-key.sh
```

It asks for one password (12+ characters), writes `~/keys/taqwa-upload.jks` and the git-ignored
`keystore.properties` that `scripts/release.sh` reads, and prints the certificate fingerprint.
Back up the `.jks` and the password in two places (password manager plus an offline copy). With
Play App Signing, on by default for every new app, Google holds the key that signs what phones
install and this one only signs uploads; a lost upload key is reset through Play support, which
costs days.

**The face key** (the watch release, two minutes, once): the Taqwa watch face is signed with a key
of its own, never the app's, because Watch Face Push requires it (spec 3.1). Only you hold it.

```bash
scripts/make-face-key.sh
```

It asks for one password twice (12+ characters), never echoed, and writes it nowhere: unlike the
upload key there is no properties file. It writes `~/keys/taqwa-face.jks` (alias `taqwa-face`) and
prints the certificate's SHA-256. Paste that line into chat. It is public, not a secret: the Lead
writes it into `watchface/face-release-cert.sha256` and commits it, and `scripts/release-face.sh`
and `scripts/release.sh` hold every signed face to it. Back up the `.jks` and its password in two
places, as for the upload key. **Unlike the upload key, nobody can reset a lost face key**, since
Google does not hold it. A new key means every watch's Taqwa face is removed and added again, which
loses its wearer's style, colour and slots.

Each watch release, past the cut gate, you sign the face yourself, typing the face key's password
(about a minute): `scripts/release-face.sh`, in a Terminal window, its output not piped or
redirected. It builds the face, then apksigner asks for the password once, and what you type is
not shown. It signs the face into `build/face-release/watchface-release-signed.apk`, and deletes
it again unless its certificate is the pinned one. Then the Lead runs `scripts/release.sh`.

## 4. Built here before the uploads (nothing needs an account)

- Listing copy in seven languages: title and subtitle, short description (≤ 80), full description
  (≤ 4000), App Store promotional text (≤ 170) and keywords (≤ 100 bytes, which is how App Store
  Connect Help states it: an Arabic or Urdu letter takes two, a Bengali one three;
  `tools/store-listing-check.py` counts them that way), release notes for 1.0.0, review notes.
  Mohamed reads English and Arabic; the native reviewers read the rest.
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
- Site: `site/pages/<lang>/home.html` store buttons get their real links as each store goes live,
  and the city pages' app sections with them (no edit there: `site/stores.json` drives both).

### Each watch release

From the watch release on, one release is four builds at one version: the phone, the Galaxy watch
app with the Taqwa face inside it, and the iPhone app with the Apple Watch app inside it. A
candidate is cut only past the cut gate (every privacy line applied, every new word reviewed), and
each takes the next version code, never one used before. In order, from the release worktree's
root:

1. **The bump:** `scripts/bump-version.sh <V> <C>`. It writes the phone's C, the Galaxy watch
   app's 100000 + C, the face's C and every iOS plist's C, all at V, and runs
   `scripts/check-versions.sh`. Then `scripts/test.sh` and `scripts/ios-build.sh`, which also runs
   `scripts/test-watch-bundle-signing.sh` on the build it made.
2. **The face gate:** `scripts/face-gate.sh`. The face's generators, its fit tools on the watch's
   own fonts, and Google's two validators.
3. **Mohamed:** `scripts/release-face.sh`, in a Terminal window, its output not piped or
   redirected (§3). He types the face key's password once. It writes
   `build/face-release/watchface-release-signed.apk`, signed by the key
   `watchface/face-release-cert.sha256` pins. Its face build passes `-Ptaqwa.unsignedRelease=true`,
   which leaves the face as it is and keeps `keystore.properties` unread.
4. **The Lead alone, the Android store build:**
   ```bash
   ln -s ~/Desktop/Workspace/apps/Taqwa/keystore.properties keystore.properties
   scripts/release.sh
   rm keystore.properties && ls keystore.properties   # "No such file or directory": the link is gone
   ```
   `scripts/release.sh` refuses without the link, without the face's pin (M1, §3), or with
   `TAQWA_FACE_CERT_FILE` set. In order it runs the version and stamp checks, the face gate, the
   signed face's checks (the pinned certificate, and the current face entry for entry: otherwise
   "run scripts/release-face.sh again"), the four builds, then the checks of what was built, and it
   prints the two AABs and two APKs with their sizes and certificate SHA-256s. It ends with
   `release.sh: PASS` only when every check passed. A run that fails from the build on removes the
   four outputs it built ("the outputs it built are removed"), so no bundle that failed a check is
   left at the upload paths. Leave the worktree
   alone while it runs: the face gate fails on any change to a tracked file during its run. Only
   Gradle reads `keystore.properties`; no agent opens, prints or copies it. Before it, agents prove the same
   path with `scripts/test-release.sh` and the dry run,
   `TAQWA_RELEASE_DRY_RUN=1 TAQWA_FACE_CERT_FILE=<test pin> scripts/release.sh` after
   `scripts/release-face.sh` with the throwaway test key (`TAQWA_FACE_KS` and the rest). The dry
   run builds unsigned, signs with the debug key into `build/release-dry-run/*-DRY-RUN.{aab,apk}`
   for the emulators, and Play refuses those. Every build of the root project it makes passes
   `-Ptaqwa.unsignedRelease=true`, the face gate's included, so none reads a `keystore.properties`
   even beside one. A dry run refused before its build (the versions, the face gate, the signed
   face) leaves the last dry run's outputs where they were; one that fails from the build on
   removes them.
5. **With Mohamed's OK in chat, each time:** `scripts/ios-release.sh archive`, then
   `scripts/ios-release.sh export-check`, then `scripts/ios-release.sh upload`. The archive's
   report runs `scripts/ios-release.sh check` on it: the Apple Watch app is inside under `Watch/`
   with its widgets, every app and extension is at V (C), and `scripts/check-watch-bundle.sh
   --release` passes on its development signature. `export-check` exports it locally as an upload
   would sign it, checks the IPA's distribution signature the same way, deletes the IPA and
   uploads nothing. The Lead records the keys each signature carried, as the check prints them.
   `scripts/ios-release.sh` with no argument runs the three in that order and stops at the first
   that fails, so it never uploads an archive whose export check did not pass. If `export-check`
   cannot sign on the Mac, §6 step 2's fallback applies.
6. **The uploads:** both AABs to Play (the phone's and the watch's, on the one listing; the watch's
   on the Wear OS track), and the iPhone build in App Store Connect (`upload`, step 5). Upload the
   AABs only after `scripts/release.sh` printed `release.sh: PASS` in step 4. Play never takes a
   code twice, so a bundle that failed a check would burn its candidate. Every Play Console action
   is Mohamed's, in the order of the next section.

### The watch release in the stores (1.6.0), in Mohamed's order

Plan 5 Task 17's table, in console order: the cut gate, candidate C, M10a, then each later
candidate up to production. What is done carries its date from the watch ledger
(`.superpowers/sdd/2026-10-19-watch-companion-1-galaxy-face/progress.md`). Every Play Console and
App Store Connect click is Mohamed's; an upload to App Store Connect needs his OK in chat, each
time; the real `scripts/release.sh` is the Lead's alone.

1. **The cut gate**, before any candidate is cut. Held for C on 8 November 2026:
   - M1, the face key: made by Mohamed on 6 November. Its certificate's SHA-256 is
     `watchface/face-release-cert.sha256`, and `release.sh` holds every signed face to it.
   - M3, the privacy wording, and M9, both store forms ("No data collected" on Play, "Data Not
     Collected" on the App Store, `docs/STORE-PRIVACY.md`): given on 31 October, with his two
     tweaks.
   - M8, his reading of the English and the Arabic: 6 November. The five native readers of
     Task 11 read the other five languages the same day (Ruling 71), and
     `git grep "draft, to native review" -- ':!docs'` prints nothing.
   - `python3 tools/privacy-check.py`: every row approved, none awaiting.
   - Task 12: the Lead's call 6 ruled (b) on 6 November (Ruling 72 item 1); the 12-hour
     observation passed on 7 November (Ruling 78).
2. **C = 1.6.0 (43)**, 8 November: the bump, M5 (Mohamed signs the face with
   `scripts/release-face.sh`), then the Lead's `scripts/release.sh`, PASS. Both AABs on the
   Desktop, and both notes files pasted in chat.
3. **M10a, the public privacy pages**, 9 November, with his OK: `main` `dd3f8a30`, the seven
   policies dated 9 November, live on taqwa.world before any track took C. He approved the Play
   listing's two privacy lines the same day, for the live listing.
4. **M10, its first half**, 9 November: Test and release › Advanced settings › Form factors ›
   Add form factor › Wear OS. Its opt-in and Wear OS's terms stay locked until every listing has
   Wear OS screenshots and a Wear OS testing release exists; they are step 12.
5. **M11, C on Play's internal tracks**, 9 November: the phone AAB on the phone's internal
   testing, the Wear AAB (100043) on Wear OS's (the Releases page's form-factor selector), each with
   its notes file. His Galaxy Watch 8 Classic installed 100043 from Play the same night. No closed
   track is recorded as taking a 1.6.0 candidate, so question 8's three-day closed test has not
   run.
6. **M6a, her devices**, 9 November: her iPhone and Apple Watch registered under team 3K93P4PA5H.
   Her builds go on by cable (a Release build signed for development), as Mohamed chose on
   7 November, so M6's TestFlight group is not used.
7. **C+1 = 1.6.0 (44)**, 9 November: Sunrise on both watches follows the phone's Show sunrise, the
   watch app refreshes less when it opens, and Settings › Watch's rows follow the watch while
   Settings is open. Face signed, `release.sh` PASS, notes in 43's words. It reached his watch from
   Play's internal track that day, and her pair by cable.
8. **C+2 = 1.6.0 (45)**, 10 November: the Tile's countdown in short words where the full ones do
   not fit ("in 6h 20m", and French, Turkish and Indonesian alike; Arabic, Urdu and Bengali keep
   the full words), and Report a problem's layout. Bumped in `c9a08a97`, notes `46ec030d` (44's
   words), the whole gate green on `46ec030d`. `2f0aae26` (Report a problem's typing place) came
   after the gate, so the gate runs again on the commit that is cut, and the iPhone archive is made
   from that same commit.
9. **Each later candidate (M11a)**, in this order: M5, the Lead's `release.sh`, then the pair on
   the internal tracks over the candidate before (his own account is an internal tester), with its
   two notes files. Then Task 17 Step 3's update check on his watch: Play updates the watch app,
   `dumpsys` shows the new face code, one `TaqwaFace update` line, the face still active and still
   in his style, and `logcat -b crash` is empty. Then the checks Task 13 Step 6 asks for its
   changes. Her pair takes it by cable for Task 15's checks 1 and 2. A failure is a finding, and
   the next code is cut the same way.
10. **The ship gate** (Task 17), every line before step 11. As of 10 November:
    - done: the cut gate (step 1), both forms approved, the 12-hour observation, call 6 and its
      follow-up; M10a live since before any track took a candidate; 1.5.1 (42) live on the App
      Store and at 100 % on Play, so the 1.6.0 notes carry no Quran bullet;
    - open: Task 10's cut, which each listing's note under its Play full description puts to him;
    - open: his Galaxy on the production candidate, Task 13's checks with the screen readers, Ruling
      42 holding (otherwise Task 14), and Step 3's update from Play over the candidate before;
    - open: her Apple Watch, every gate of Task 15 (check 6, VoiceOver; check 10, the payload
      thresholds; check 12, no location prompt; check 13, the needle within 5° with the crown on
      each side), and check 11;
    - open: the phones' smoke test on the S23 and the iPhone 12 (Task 13 Step 2);
    - open: Task 16, the 35 Galaxy shots and the Apple Watch face shots (`<lang>-04.png`) shot again
      in the afternoon;
    - open: Exodus's local check of both release APKs, recorded in `docs/STORE-PRIVACY.md` (Task 17
      Step 1).
11. **M13, before either store's release button:** with his OK, the Lead squashes the release onto
    `main` under his Taqwa identity, with the README's Roadmap slice 5 turned to Done, pushes it
    (the site goes live with "On your wrist" and the support answer `#watch`), and does the
    after-push cleanup.
12. **Play, the production candidate (M10's second half, then M11a's end):** Main store listing, in
    each of the seven languages: the full description with its wrist paragraph, and under Wear OS
    the five screenshots in `docs/store/screenshots/play-wear/<lang>/`. Then Advanced settings ›
    Form factors › Wear OS: opt in and accept Wear OS's terms. Then promote the candidate's two
    internal releases to production, the phone's and Wear OS's, each with its notes file, and send
    every change for review from Publishing overview. The plan names no staged rollout (§5 step 7:
    staged or 100 %, both fine). Play reviews Wear OS on its own, and a first Wear review can take
    days.
13. **M12, the App Store:** with his OK, the Lead uploads the production candidate
    (`scripts/ios-release.sh upload`). Then the version 1.6.0 with that build, and in each
    localization the four Apple Watch screenshots (`docs/store/screenshots/appstore-watch/<lang>/`,
    all 422 × 514), the Description and What's New from the App Store section of
    `docs/store/listing/<lang>.md`. App Privacy stays "Data Not Collected". App Review Information ›
    Notes from `docs/store/app-review/2026-10-30-notes-1.6.0-43.txt` (the 1.6.0 notes, 3,993 of
    4,000 bytes). Version Release: Manually release this version. Then Add for Review and Submit.
    The name, subtitle, keywords and promotional text are 1.5.1's, unchanged. Look again for Urdu
    and Bengali localizations (§6 step 4).
14. **M14, once Play serves the build:** a new Exodus analysis of 1.6.0 (45)
    (reports.exodus-privacy.eu.org/en/analysis/submit/), by Mohamed, or by the Lead with his OK.
    `CONTRIBUTING.md`'s and the site's "no trackers" stay only if it finds none.
15. **After each store goes live:** a ledger line. `site/stores.json` needs no change. On the App
    Store, Release This Version when Play's release is live too, or when he chooses.

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
   https://play.google.com/store/apps/details?id=world.taqwa.app. **Live by 8 October 2026**;
   the site and README switched that day (§1).

## 6. App Store, in App Store Connect order

1. **Signing**: with the Team ID in `Config.xcconfig` and the widget target, and the new account
   in Xcode, one device archive registers `world.taqwa.ios`, `world.taqwa.ios.widget` and the App
   Group under the new team (visible at https://developer.apple.com/account/resources/identifiers/list).
2. **Archive and upload**: `scripts/test.sh` and `scripts/ios-build.sh` green, then
   `scripts/ios-release.sh archive` (the archive lands in `build/ios-archive/`, and the script
   prints the built identifiers, team, app group and widget size back), the app record (step 3),
   then `scripts/ios-release.sh export-check` (a local export signed as the upload would be, its
   bundles checked as a release, the IPA deleted, nothing uploaded), then
   `scripts/ios-release.sh upload`, each with Mohamed's OK in chat (§4, "Each watch release",
   step 5). The script signs automatically through the Apple account signed into Xcode and refuses
   an empty or LOOPDL team. If `export-check` cannot sign on the Mac, upload alone,
   `scripts/ios-release.sh upload` with Mohamed's OK, and read each bundle's entitlements in App
   Store Connect › TestFlight › the build › Build Metadata instead; the Lead records them against
   `scripts/check-watch-bundle.sh`'s distribution keys. If the command-line upload is refused, open
   the `.xcarchive` in Xcode
   (Window › Organizer) → Distribute App → App Store Connect →
   Upload, automatic signing, symbols included. The build appears under TestFlight after 10–30
   minutes of processing. Export compliance is answered by `ITSAppUsesNonExemptEncryption =
   false`, so no encryption question appears. Tag the archived commit `ios-build-<code>`.
3. **New app record**: My Apps → + → New App → iOS, name (§1), primary language English (U.K.),
   bundle ID `world.taqwa.ios` from the list, SKU `taqwa-ios`, full access → Create. Done 22
   September: Apple ID **6814975544**, so the store link is https://apps.apple.com/app/id6814975544
   (`app_store` in `site/stores.json` at the iOS release). Build 1.0.0 (32) uploaded the same night.
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
   **App Accessibility** (app level, voluntary, not tied to a version, so it can wait): declare
   **Dark Interface only for now**, and only after walking the common tasks in Dark Mode on an
   iPhone running the submitted build: Prayer with the ⓘ card, About these times, Settings ›
   Prayer times › Timetable and Match my mosque; the Quran reader, the Mushaf and recitation;
   Qibla; Tasbeeh; Settings; and the Home Screen and Lock Screen widgets. Declare nothing else
   yet (launch audit of 29 September, §6): VoiceOver and Voice Control wait for the accessibility
   round (unnamed controls, a silent Mushaf page), Larger Text for the Mushaf at large sizes,
   Differentiate Without Color Alone and Sufficient Contrast for their fixes, Reduced Motion and
   Captions for a device check; Audio Descriptions do not apply (no video).
6. **Version 1.0.0 page**: screenshots (the 6.9-inch set per language; a localisation without its
   own falls back to English), promotional text, description, keywords, support URL
   https://taqwa.world/support/, marketing URL https://taqwa.world, copyright
   "2026 Mohamed Abulgasem", App Review contact (name, phone, email), sign-in required: No,
   **App Review Information › Notes** from `docs/store/app-review/2026-10-30-notes-1.6.0-43.txt` (the
   Guideline 2.1 answers, which Apple asked to find there on every submission), select the
   processed build, and under Version Release choose **Manually release this version**. A first
   version has no "What's New" field, so Play's release notes have no App Store twin until the
   next version.
   The promotional text is the one field that can change at any time, even after release, with
   no review.
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
   fields allow), kept as the record of that reply; the Notes, last brought up to date for 1.6.0 (43), are
   `docs/store/app-review/2026-10-30-notes-1.6.0-43.txt` (3,993 bytes; App Store Connect Help gives
   the Notes field 4,000 bytes). Keep Notes in step with the app on every later submission.
   "Metadata Rejected" or "Information Needed" is answered in App Store Connect's
   App Review messages without a new build; a binary rejection needs a fix, the next build number
   (35, 36, …, shared with Android) and a resubmission.
9. **Replacing an approved build** (done on 29 September: 1.0.0 (32) was approved but calculates
   with the old presets, so it was never released, and 1.0.0 (34) goes in its place; 33 stays
   unused in TestFlight). It works only while the approved version waits in Pending Developer
   Release, which is what manual release is for; a version already released cannot be withdrawn
   this way and needs a new version name instead.
   1. On the version page, **Cancel this release**. The version returns to Developer Rejected,
      and every field on it is editable again, as are the name and subtitle under App Information.
   2. Upload the replacement: `scripts/ios-release.sh archive`, then `scripts/ios-release.sh
      export-check`, then `scripts/ios-release.sh upload` (step 2, its fallback included), and
      wait until TestFlight shows the build processed.
   3. Edit whatever metadata changed: App Information (name, subtitle), then per localisation the
      promotional text, description, keywords and screenshots, then App Review Information ›
      Notes. Every value is in `docs/store/listing/<lang>.md` and `docs/store/app-review/`, and
      `python3 tools/store-listing-check.py` counts them against the limits.
   4. Under Build, remove the old build (the remove button beside it) and **Add Build** → the new
      one.
   5. Version Release: **Manually release this version**. Save.
   6. **Add for Review**, then **Submit to App Review**. Review starts again from Waiting for
      Review.
10. **At the iOS release** (after approval, when Mohamed presses Release This Version): check the
    live page at https://apps.apple.com/app/id6814975544, then set `app_store` in
    `site/stores.json`, build and push the site (§1, "The site's store badges"), re-word the site's
    beta heading for Android (§1, "Recruiting testers"), and update the README's "Get it" section.
    Nothing Android-side changes until production access.

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
  writes it); `scripts/release.sh` builds the phone's and the Galaxy watch app's signed App
  Bundles and APKs together, with each R8 mapping inside its bundle. Both are signed with the one
  upload key, which Play re-signs, and the Taqwa face inside the watch app with Mohamed's face key
  alone. `-Ptaqwa.unsignedRelease=true` keeps every key out of a release-type build and leaves
  `keystore.properties` unread. The dry run passes it to every build of the root project it makes,
  `tools/watchface/validate.sh` (the face gate's) to its face builds, and `scripts/release-face.sh`
  to the face it signs, which the flag leaves as it is, so none reads a `keystore.properties`
  beside it (`scripts/test-release.sh`). `scripts/release.sh`'s own builds in the store run never
  pass it, so the upload key signs the four store outputs.
- **The watch builds' permissions**: the Galaxy watch app's merged release manifest asks for
  exactly `wearApp/permissions.txt`: `VIBRATE`, androidx.core's
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, `PUSH_WATCH_FACES` and
  `SET_PUSHED_WATCH_FACE_AS_ACTIVE` (a runtime permission, asked on the wearer's tap). It has no
  `INTERNET` and no location permission, and holds Watch Face Push's `wear-sdk` as not required,
  so Wear OS 3 to 5 install it. The face has no code and asks for nothing. The phone's merged
  release manifest asks for exactly `androidApp/permissions.txt`, today's 12, each with its reason:
  the watch adds none. The Apple Watch app and its widgets ask for no location, no HealthKit and
  no network, and are signed with the app group alone.
- **Where each check lives**:

  | What | Checked by | Run by |
  |---|---|---|
  | One version for the four builds | `scripts/check-versions.sh` | `release.sh` step 1, `scripts/test.sh`, CI |
  | The face's generators, fit tools and validators | `scripts/face-gate.sh` | `release.sh` step 2 |
  | The face's key, its pin and its freshness | `release.sh` step 3 (pin `watchface/face-release-cert.sha256`) | `release.sh`; `scripts/release-face.sh` holds the pin too |
  | The face inside the watch app: its package, code, key and token | `tools/watchface/check-bundle.sh` | `release.sh` step 5 |
  | The watch's R8 build: mapping, no debug code or test data, permissions, `wear-sdk` | `tools/wear-release-check.sh release` | `release.sh` step 5 |
  | The phone's permissions | `androidApp/permissions.txt` | `release.sh` step 5 |
  | One key for the phone and the watch, the versions built, the face and its token as bundled, no test data | `release.sh` step 5 | `release.sh` |
  | The dry run, its refusals and its outputs | `scripts/test-release.sh` | by hand, before a cut |
  | No release build signs by accident | `scripts/test-unsigned-release.sh` | by hand, before a cut |
  | The face's release tools | `scripts/test-face-release.sh` | by hand, before a cut |
  | The Apple Watch bundles: ids, one version, no location or network, the app group, sizes | `scripts/check-watch-bundle.sh` | `scripts/ios-build.sh` (every build, CI too); `--release` in `ios-release.sh check` (the archive) and `export-check` (the IPA) |
  | The bundle check's Release signal and each signature's keys | `scripts/test-watch-bundle-signing.sh` | `scripts/ios-build.sh` |
  | The archive: the watch inside, every bundle at V (C) | `scripts/ios-release.sh check` | `ios-release.sh archive`'s report |
- **Prayer-time stamps**: `scripts/release.sh` and `scripts/ios-release.sh` both run
  `./gradlew -p tools/timetables checkStamps` first and refuse to build on a stale or red stamp
  (spec §5, ruling R86). The Ramadan build runs them as `TAQWA_RAMADAN=1448 scripts/release.sh`
  (and `ios-release.sh`): they then also refuse an A/B timetable with a unit whose Fajr, end of eating
  and Maghrib were not each checked on every day of Ramadan 1448 and a day each side (7 February to
  10 March 2027), unless the gate has demoted it (rulings R146 and R148).
- **Manifest**: `MY_PACKAGE_REPLACED` re-arms prayer alarms after every update; `USE_EXACT_ALARM`
  removed, `SCHEDULE_EXACT_ALARM` kept with an in-app route to "Alarms & reminders"; location and
  compass hardware optional; `localeConfig` lists the seven languages; backup opted out twice
  (`allowBackup`, `dataExtractionRules`); dark window background at launch.
- **iOS**: `PrivacyInfo.xcprivacy` in the app and the widget extension; `InfoPlist.strings` in the
  seven languages; iPhone-only (`TARGETED_DEVICE_FAMILY 1`, `tools/configure-store-targets.rb`);
  `arm64`; export compliance answered in `Info.plist`.
- **Content and licences**: `NOTICE` and `docs/ATTRIBUTION.md` say the GPL covers the code only;
  the Manrope OFL text is in `docs/licences/` (and beside the fonts the site serves itself, which
  `site/build.py` copies from the app); the Mixkit source clip is no longer in the
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
