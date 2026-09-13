# Taqwa — privacy policy, About screen and network honesty

**Date:** 2026-09-13 · **Ships as:** 0.12.0 (14) · **Branch:** `privacy-about`
**Written by:** the recitation session, as a handoff. The implementing session should read
§1 first: it is the list of facts the words in §4–§6 are allowed to state, and one of those
facts is wrong today and must be fixed in code (§2) before the words are written.

---

## 0. Why now

Until 0.10.0 the app held no `INTERNET` permission and the README could say "no network
access of any kind" (README.md line 146 still says so — that sentence is now false).
0.11.0 added Quran recitation, and with it:

- the `INTERNET` permission on Android and network use on iOS;
- two hosts the app talks to, both GitHub;
- a catalogue refresh that runs **from the app start effect on every launch**, at most once
  a day, whether or not the person has ever touched recitation
  (`shared/src/commonMain/kotlin/world/taqwa/app/App.kt:205`).

Both stores now require a privacy policy URL for an app that touches the network, and the
app has no About surface to put it on. This spec adds the policy, the store answers, the
About screen, and makes one code change so the policy can say "the app makes no network
request until you ask it to" and be telling the truth.

Positioning after this work: **offline by design, online only on request.** Not "offline
only" any more; the new line is stronger because it is specific and checkable.

---

## 1. Network inventory — the facts the documents must match

Everything below was read from the 0.11.0 code. If the implementing session finds a
request not listed here, the inventory is wrong and the policy must change, not the other
way round.

| # | Request | Host | When | What the server learns |
|---|---------|------|------|------------------------|
| 1 | `manifest.json` catalogue refresh | `raw.githubusercontent.com` | Today: every app start, ≤ 1 per 24 h (attempt-based). **After §2: only once the person has engaged with recitation.** | IP address, platform default User-Agent, the path (which is the same for everyone). |
| 2 | Surah download `audio-<reciter>-v1/<nnn>.taqa` | `github.com/MohamedAbulgasem/Taqwa-data/releases/download/…` then a redirect to GitHub's release-asset host (`objects.githubusercontent.com` / `release-assets.githubusercontent.com`) | Only when the person taps Download, Play on an undownloaded surah, or "Download the whole Quran". Resumes carry a `Range` header. | IP address, platform default User-Agent, and **the path, which reveals reciter and surah**. |
| — | Reciter previews | none | bundled in the app (`files/recitation/previews/`) | — |
| — | Adhan, chime, Quran text, translations, city database, fonts | none | bundled | — |
| — | Prayer times, Qibla, Hijri | none | computed on device | — |
| — | Widgets | none | read the app's own mirror | — |

Clients: `HttpURLConnection` on Android (`shared/src/androidMain/kotlin/world/taqwa/app/recitation/Http.android.kt`),
`NSURLSession` on iOS. No cookies are set or sent, no identifiers are added, no headers
beyond `Range`. There is no third-party SDK that opens a socket: Media3, WorkManager,
DataStore, SQLDelight and Compose do not phone home. No analytics, no crash reporter, no
ads, no advertising identifier, no account.

Storage: settings (DataStore), bookmarks and Tasbeeh counts (SQLDelight), the location or
chosen city, downloaded recitations. Android `allowBackup="false"`
(`androidApp/src/androidMain/AndroidManifest.xml:31`); iOS recitation files are excluded
from backup. **Implementer: confirm whether the iOS DataStore/SQLDelight files are also
excluded from iCloud backup and word §4 "Backups" accordingly. Do not claim more than the
code does.**

GitHub's own privacy statement governs what GitHub keeps about requests 1 and 2:
<https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement>.

---

## 2. Code: gate the catalogue refresh behind engagement

**Goal.** A person who installs Taqwa for prayer times and never opens recitation causes
zero network requests, ever. A person who has engaged gets the daily catalogue check as
today, plus an immediate check when they open the picker.

### 2.1 Definition of "engaged"

Engaged is true when either:

- the flag `recitation_engaged` (new `booleanPreferencesKey`, add to `SettingsKeys` next to
  `RECITATION_MANIFEST_CHECKED` at `settings/SettingsKeys.kt:98`) is `true`; or
- the library holds at least one downloaded surah for any reciter (covers 0.11.0 users who
  downloaded before this flag existed, and reinstalls over left-behind files, because
  `RecitationLibrary.reconcile()` rebuilds the registry from disk at start).

Introduce a small class in `world.taqwa.app.recitation`:

```kotlin
class RecitationEngagement(
    private val store: DataStore<Preferences>,
    private val library: RecitationLibrary,
) {
    suspend fun isEngaged(): Boolean
    /** Idempotent; writes the flag once. */
    suspend fun mark()
}
```

`isEngaged()` reads the flag first (one DataStore read) and only falls back to a library
scan when the flag is unset. For "any download" add
`suspend fun hasAnyDownloads(): Boolean` to `RecitationLibrary` reading the DataStore sets
under `RECITATION_DOWNLOADED_PREFIX` (no disk I/O).

### 2.2 Where `mark()` is called

All in `feature/recitation/RecitationController.kt`, fire-and-forget on the controller's
scope, at the first line of each:

- `onHeaderTap` (line 318) — the speaker button;
- `requestPlay` (299) — Play on an ayah row;
- `openPicker` (418);
- `downloadWholeQuran` (359).

And once from `RecitationSettingsScreen` (`feature/settings/RecitationSettingsScreen.kt:68`)
in a `LaunchedEffect(Unit)`, through whatever the screen already receives (add an
`onOpened: () -> Unit` parameter wired in `App.kt`; do not hand the screen the DataStore).

Opening Settings › Quran › Recitation counts as engagement on purpose: the screen shows the
reciter list and the "Download the whole Quran" row, and a person who went there wants the
current catalogue.

### 2.3 `ManifestRefresher`

Add a constructor parameter `engaged: suspend () -> Boolean` and make `refreshIfStale()`
return **before** reading or writing `RECITATION_MANIFEST_CHECKED` when it is false. The
first engaged launch then fetches immediately rather than a day later. Wire it in
`di/AppContainer.kt` as `{ recitationEngagement.isEngaged() }`.

### 2.4 Opportunistic refresh

`openPicker()` and the Recitation settings `onOpened` also launch
`manifestRefresher.refreshIfStale()` on `Dispatchers.Default` after `mark()`. The 24-hour
attempt window still applies, so this adds at most one request a day. If the fetch lands a
new reciter while the picker is open, the list updates only if `ManifestProvider` already
exposes a flow; if it exposes a one-shot read, leave it — the next open shows the new
reciter. Do not add a flow for this.

### 2.5 Tests (`shared/src/commonTest/kotlin/world/taqwa/app/recitation/`)

- `ManifestRefresherTest`: not engaged → fetch never called **and** the checked timestamp
  is not written; engaged → fetch called; a stale timestamp with `engaged=false` still does
  not fetch.
- New `RecitationEngagementTest` (use the existing fake DataStore pattern from
  `RecitationLibraryTest`): flag unset and no downloads → false; flag unset, one download
  set non-empty → true; `mark()` then `isEngaged()` → true; `mark()` twice writes once.
- `RecitationController` test (existing test file for the controller): `openPicker()` marks
  engaged; `onHeaderTap` marks engaged.

Run `./scripts/test.sh` and trust its final BUILD line. No comma in a backtick test name.

### 2.6 Device proof

On a fresh install (emulator, `adb -s emulator-5554 install -r`, app data cleared), launch,
wait, and confirm from logcat or the harness that no request left the process. Then tap
the speaker in the reader and confirm the manifest fetch appears. One screenshot is not
needed for this; the log lines are the evidence. Record them in the report.

---

## 3. Code: iOS export compliance

Add to `iosApp/iosApp/Info.plist`:

```xml
<key>ITSAppUsesNonExemptEncryption</key>
<false/>
```

The app uses only HTTPS to GitHub, which is exempt. There is no ATS exception in the plist
today; keep it that way. This removes the export-compliance question from every App Store
upload.

---

## 4. `PRIVACY.md` (repo root)

Plain English, one page, no legal register. Use the text below verbatim as the starting
draft; the implementer adjusts only where §1 verification (backups) requires it. Mohamed
reviews the wording before merge — the words are his in a way the code is not.

```markdown
# Taqwa privacy policy

_Last updated 13 September 2026. Applies to Taqwa 0.12.0 and later on Android and iOS._

Taqwa is a free Islamic app with no account, no ads and no analytics. It works offline.
The one thing it uses the internet for is downloading Quran recitations, and only when you
ask it to.

## What stays on your phone

Everything the app knows is stored on your device and nowhere else:

- your location, or the city you chose, used to compute prayer times and the Qibla;
- your settings: calculation method, notification choices, adhan voice, theme, reciter;
- your Quran bookmarks and reading position;
- your Tasbeeh counts;
- any recitations you have downloaded.

None of this is sent to us or to anyone. There is no server behind Taqwa and no account to
create. Deleting the app deletes all of it.

## Location

The app asks for your location only to compute prayer times and the Qibla direction, and
only if you allow it. Picking a city from the built-in list works just as well and needs no
permission. Your location is used on the device and never transmitted.

## Recitation downloads

Recitations are fetched one surah at a time from Taqwa's public data repository on GitHub
(github.com/MohamedAbulgasem/Taqwa-data), and only when you tap Download, tap Play on a
surah you have not downloaded, or choose "Download the whole Quran". By default this
happens over Wi-Fi only; you can allow mobile data in Settings › Quran › Recitation.

Like any download, the request shows GitHub your IP address and which file you asked for,
which reveals the reciter and surah. It carries nothing that identifies you: no account, no
device identifier, no cookie. GitHub's own privacy statement covers what GitHub keeps about
such requests: docs.github.com/site-policy/privacy-policies/github-general-privacy-statement.

Once you have used recitation, the app also checks once a day for an updated list of
reciters, from the same repository. That request is the same for everyone and reveals
nothing but your IP address. Until you first use recitation, the app makes no network
request at all.

Downloaded recitations can be deleted per reciter in Settings › Quran › Recitation ›
Downloads.

## Backups

<!-- Implementer: keep whichever sentence the code supports. -->
Taqwa opts out of Android's app backup, so your location and settings are not copied to
Google. On iOS, downloaded recitations are excluded from iCloud backup; [settings are / are
not] included in your device backup, which is encrypted by Apple and never visible to us.

## What Taqwa never does

- No account, sign-in or profile.
- No ads and no advertising identifier.
- No analytics, crash reporting or usage statistics.
- No third-party SDK that talks to the internet.
- No sale or sharing of data, because there is none to sell or share.

## Children

Taqwa collects no data from anyone, of any age.

## Changes

If a future version needs the network for anything new, this page will say so before that
version ships, with the date above updated.

## Contact

Open an issue at github.com/MohamedAbulgasem/Taqwa/issues or email algiriany93@gmail.com.
```

Open question for Mohamed (decide before merge, one line in the review): keep the personal
email in the Contact section, or GitHub issues only. Recommendation: both — the stores ask
for a contact email anyway, and it is already public on the developer account.

---

## 5. `docs/STORE-PRIVACY.md`

Short, so the forms match the code and nobody has to re-derive answers at upload time.

### Google Play — Data safety

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Play defines "collected" as transmitted off the device. Location, settings, bookmarks and counts stay on device. An IP address on a file download is not a listed data type. |
| Is all user data encrypted in transit? | n/a | Nothing collected. (All traffic is HTTPS regardless.) |
| Do you provide a way to request data deletion? | n/a | Nothing collected. |
| Independent security review | No | — |

### Google Play — App content

- Ads: **No ads.**
- Target audience: adults and children? Choose **18 and over is not required**; pick the age
  bands Mohamed wants, but note choosing under-13 triggers Families policy review. Recommend
  13+ to avoid it; the content is unrestricted.
- Government app / Financial features / Health: **No.**
- News app: No.
- **Foreground service permissions** (targetSdk 35 requires a declaration and a short demo
  video for each):
  - `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — Quran recitation playback continues with the
    screen off and lock-screen controls. Video: start a surah, lock the phone, show the
    lock-screen controls.
  - `FOREGROUND_SERVICE_DATA_SYNC` — user-initiated recitation downloads finish while the
    app is in the background. Video: tap Download, leave the app, show the notification.
- Permissions used: `INTERNET`, `ACCESS_COARSE/FINE_LOCATION` (optional, prayer times),
  `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` as already declared,
  foreground service permissions above.

### App Store Connect

- App Privacy: **Data Not Collected.** Same definition as Play: nothing leaves the device
  except the download request, and an IP address is not a declared data type.
- Privacy Policy URL: the `AboutLinks.PRIVACY_POLICY` constant (§6.3).
- Export compliance: exempt, `ITSAppUsesNonExemptEncryption = false` in the plist (§3).
- Content rights: **Yes, the app contains third-party content and you have the rights.**
  Recitations under the Islamic Network's non-commercial redistribution terms, Tanzil text,
  KFGQPC font, GeoNames CC BY 4.0, Wikimedia adhan recordings — all listed in
  `docs/ATTRIBUTION.md`.
- Advertising Identifier (IDFA): **No.**
- Age rating: no restricted content; answer "None" throughout.
- Background modes already declared: audio (playback), and the download session identifier
  `world.taqwa.app.recitation`.

---

## 6. About screen

### 6.1 Entry

In `SettingsRootScreen.kt` (about line 306) the ABOUT group card becomes:

```
About Taqwa                    0.12.0  ›     ← opens Screen.About
──────────────────────────────────────
Attribution & licences                 ›     ← unchanged
```

String `settings_about` changes from "Version" / "الإصدار" to "About Taqwa" / "عن تقوى".
`settings_version_value` stays as the row's value; `scripts/bump-version.sh` already
rewrites it. Add `onOpenAbout: () -> Unit` beside `onOpenAttribution` (line 236).

### 6.2 Screen

New `feature/settings/AboutScreen.kt`, new `Screen.About` in `nav/Screen.kt`, wired in
`App.kt` next to `Screen.Attribution` (line 866). **Header family: the settings family** —
large `screenTitle` under the chevron, like Attribution and every other settings screen.
Not the inline reader style.

Layout, top to bottom, all inside the settings idiom (`SettingsCard`, `TaqwaRow`,
`CardDivider`, `SectionLabel`, `GroupGap`):

1. **Identity card.** App name in `screenTitle` weight (the word "Taqwa" in Latin script in
   both locales — it is the product name, as on the icon), the version beneath in
   `caption` tint using `settings_version_value`, and one line of `rowLabel`:
   "Free, for everyone, for good." / "مجاني، للجميع، دائمًا."
2. **Section label** `about_privacy_label`: "PRIVACY" / "الخصوصية".
   **Privacy card**: three rows, each a `TaqwaRow` with no chevron, label + caption:
   - "Stays on your phone" — "Location, settings, bookmarks and counts never leave it."
     / "يبقى على هاتفك" — "الموقع والإعدادات والعلامات والعدّاد لا تغادره."
   - "Online only when you ask" — "The internet is used for one thing: recitations you
     choose to download, from Taqwa's public repository on GitHub."
     / "متصل فقط عندما تطلب" — "يُستخدم الإنترنت لشيء واحد: التلاوات التي تختار تنزيلها، من
     مستودع تقوى العام على GitHub."
   - "No account, no ads, no analytics" — "Nothing to sign into, nothing to sell."
     / "بلا حساب، بلا إعلانات، بلا تحليلات" — "لا شيء لتسجيل الدخول إليه، ولا شيء للبيع."
3. **Section label** `about_links_label`: "MORE" / "المزيد".
   **Links card**, three `TaqwaRow`s with an external-link glyph at the trailing end
   instead of the chevron (mirror in RTL like the speaker glyph):
   - "Privacy policy" / "سياسة الخصوصية" → `AboutLinks.PRIVACY_POLICY`
   - "Source code" / "الشفرة المصدرية" → `AboutLinks.SOURCE`, value "GitHub"
   - "Licence" / "الرخصة" → `AboutLinks.LICENCE`, value "GPL-3.0"

Attribution stays where it is on the root screen; About does not repeat the reciters or
the credits.

### 6.3 Links and opening

```kotlin
// shared/src/commonMain/kotlin/world/taqwa/app/about/AboutLinks.kt
object AboutLinks {
    const val PRIVACY_POLICY = "https://github.com/MohamedAbulgasem/Taqwa/blob/main/PRIVACY.md"
    const val SOURCE = "https://github.com/MohamedAbulgasem/Taqwa"
    const val LICENCE = "https://github.com/MohamedAbulgasem/Taqwa/blob/main/LICENSE"
}
```

One place to change when the repo is public or when taqwa.world exists. Open with Compose's
`LocalUriHandler.current.openUri(url)` inside `runCatching` — no new platform seam, and a
device with no browser fails silently rather than crashing. The links only resolve once the
Taqwa repo is public; that is a release-day step, not a code one.

### 6.4 Tests

- `AboutLinksTest`: every link starts with `https://`, points at
  `github.com/MohamedAbulgasem/`, and the policy link ends in `PRIVACY.md`.
- Strings: every new key exists in both `values/strings.xml` and `values-ar/strings.xml`
  (extend the existing locale-parity test if there is one; add one if not).
- Device: screenshots of the About screen in English light and Arabic dark on the emulator
  and the simulator, each link tapped once on the emulator to see Chrome open.

---

## 7. Copy fixes across the repo

Every absolute claim about the network must match §1.

- `README.md` line 9: "Free, offline, no ads, no accounts, no tracking." → "Free, offline
  by design, no ads, no accounts, no tracking."
- `README.md` line 146 (Privacy section): replace the last sentence. New paragraph: location
  as before; then "The app makes no network request until you use Quran recitation.
  Recitations are downloaded one surah at a time from Taqwa's public data repository on
  GitHub, only when you ask; that request shows GitHub your IP address and the file you
  asked for, and nothing else. There are no analytics, no crash reporters and no
  third-party SDKs that talk to the internet. Full policy in [PRIVACY.md](PRIVACY.md)."
- `README.md` line 53 "Offline first." — keep.
- `strings.xml` `onboarding_welcome_body` ("works offline") — keep; it is true.
- Add a "Privacy" line to the README features list pointing to the About screen.
- `docs/BUILD-LOG.md`: one entry, "Saying what the app does with the network", covering
  the gate, the policy, the About screen and why the gate came first.

Grep both locales for `internet|offline|network|never` before finishing and check each hit
against §1.

---

## 8. Order of work and release

1. §2 gate + tests (this is the one that changes what is true).
2. §3 plist key.
3. §4 `PRIVACY.md` and §5 `STORE-PRIVACY.md` — then **stop and show Mohamed the policy
   text** before anything else lands; he reviews words, not code.
4. §6 About screen + tests + screenshots.
5. §7 copy fixes.
6. Release per `taqwa-versioning`: features since 0.11.0 → **0.12.0 (14)**,
   `scripts/bump-version.sh 0.12.0 14`, `./scripts/test.sh`, signed release APK to the
   scratchpad, install on whichever phones are connected (never `installDebug`), iOS device
   build, BUILD-LOG, memory.

The recitation session is iterating on the reciter feature in parallel on its own branch.
Both branches touch `RecitationController.kt` and `App.kt`; keep the §2 edits to the first
line of each method and to the start effect, so the merge is additive.

---

## 9. Out of scope

- Bundling the policy inside the app for offline reading. A link is what both stores
  require, and a reader who is offline can read it later.
- The taqwa.world domain. One constant swap when it exists.
- Any change to what is downloaded or from where.
- Crash reporting or analytics of any kind — never, not just not now.
