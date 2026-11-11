# Ruling 63 items 1 and 4: the site builds again, and the privacy text's M3 package put right

Lane P, on `watch-feed`, 30 October 2026. Base `87d01f3a` (= `watch-companion`, with P3.7, P4.6 and
P5.9; `git merge --ff-only watch-companion` was already up to date). This is no plan task: it is
Ruling 63 items 1 and 4 in the ledger. Nothing is pushed, and no device, emulator, simulator or lock
was used.

**M3 is still not given.** Every row that awaited it still awaits it: `python3
tools/privacy-check.py` fails on **186 rows awaiting M3, and nothing else** (177 before, plus the
eight new per-platform About rows and the notes' "predates" row). `--allow-awaiting`, which
`scripts/test.sh` runs, passes.

## The commits

| Part | Commit | What |
|---|---|---|
| item 1, alone | `c4ab27fd` "Site: the timetables build again with :watchcore's QiblaAngles, and scripts/test.sh compiles them (Ruling 63)" | `tools/timetables/build.gradle.kts`, `.github/workflows/pages.yml`, `scripts/test.sh` |
| item 4, part 1: the public pages | `8c6e665f` "Privacy: the public pages say who can read the watch's data, and the policy names the install's random number and how a backup is encrypted, in seven languages (Ruling 63)" | `PRIVACY*.md`, `site/pages/*/home.html`, `tools/privacy-check.py` (the public rows and the tree walk), `tools/test_privacy_check.py` |
| item 4, part 2: the rest | this report's commit: "Privacy: About names one company per platform, and the store copy, the notes and STORE-PRIVACY agree with the policy (Ruling 63)" | the Compose strings and `AboutScreen.kt` with its test, the listings, the notes file, `tools/store-listing-check.py`, `docs/STORE-PRIVACY.md`, the other rows of `tools/privacy-check.py`, P5.9's report and this one |

**For M10a** (the public pages live before any store track): cherry-pick, in this order, P5.9's
`fefdc44e` and `dd14e26c`, then this run's part 1, `8c6e665f`. No commit of either part 2, and not `c4ab27fd`
(main's `tools/timetables` already builds: there `QiblaMath` has no `:watchcore` to reach). Checked on
an archive of `origin/main` (`9d3ba3ef`), no git, with the three applied as patches: no conflict;
`test_privacy_check.py` 30/0; `privacy-check.py --pages --allow-awaiting` clean, strict failing on
its 130 awaiting rows alone; `./gradlew -p tools/timetables generate` and `site/build.py --check`
PASS. Each part is green on its own here: part 1 leaves every non-public row as it was, and part 2
brings those rows with their files.

## Item 1: the site builds again (blocking, first, alone)

- **Red at `87d01f3a`:** `./gradlew -p tools/timetables jvmTest generate` failed at
  `:compileKotlinJvm`: "Unresolved reference 'QiblaAngles'" at `QiblaMath.kt` 42, 45, 48, 52 and 57.
  `site/build.py --check` passed only because `_data/timetables.json` was P5.9's, generated at 20:51
  with its scratch patch. With that file moved aside it failed: "No prayer timetables at
  _data/timetables.json". On the Pages workflow the generate step would stop the build first.
- **The fix**, as the P5.9 report's finding 2 says: `world/taqwa/app/qibla/QiblaAngles.kt` in
  `appSources`, and `watchcore/src/commonMain/kotlin` as a source directory beside widgetcore's (no
  other `:watchcore` file matches `appSources`). `.github/workflows/pages.yml` gains
  `watchcore/src/commonMain/kotlin/world/taqwa/app/qibla/QiblaAngles.kt` in its paths.
- **`scripts/test.sh` compiles `tools/timetables`** (`compileKotlinJvm compileTestKotlinJvm`, in
  the whole run, after `swift test` and before the main Gradle run). Its tests and the generate
  step stay the Pages workflow's: the gate needs the official archive, which a lane does not have.
  - Added first, it failed the whole `test.sh` on the unfixed build (exit 1 at that compile).
  - Then, with the fix, in a scratch copy of the tree with only `build.gradle.kts` put back to
    `87d01f3a`'s: exit 1, the same five errors. With the fix restored: the step passes, and the whole
    `test.sh` there exits 0.
- **Green:** `CI=true ./gradlew -p tools/timetables jvmTest --rerun` 343 tests, 0 failures
  (`QiblaMathTest` 12, `PrayerTimesEngineTest` 16; CI so that the archive's rows are skipped, as
  on the workflow without the archive); `generate` wrote 55 cities (2 held) and 129 pages;
  `site/build.py --check` PASS, 164 pages.
- From now every integration also runs the generate step and `site/build.py --check` (Ruling 63;
  errata p5's last entry).

## Item 4: the M3 package polish

Every change below went into `tools/privacy-check.py`'s rows first, and the gate failed on each row
by name (70 problems: the new texts missing, the old About key still there, and the sweep's
unmatched lines) before any text changed. Every non-English line is a draft to native review,
marked in its file where the file takes a comment (`<!-- draft, to native review (plan 5 Task 11)
-->`).

### (a) The home page's privacy lede

"…encrypted so only they can read it" now reads **"…encrypted so only your phone and watch can read
it."** In Indonesian, "hanya keduanya" ("only the two of them") is now "hanya ponsel dan jam tangan
Anda". The Arabic (سواهما), Turkish (bu ikisi), Urdu (یہی دونوں) and Bengali (এ দুটিই) drafts had
the same pronoun, which can also be read as Google and Apple, so they name the phone and the watch
too, in the words each language's About row already uses. French had "ces deux appareils", which was
not ambiguous, and now says "votre téléphone et votre montre" like the others. The Bengali
"Google বা Apple এর তথ্য" also reads as "Google's or Apple's data", so the sentence now says "the
watch's data" (ঘড়ির তথ্য) first.

### (b) The listings' internet bullet (4l)

It no longer says Google or Apple may reach the watch. English: **"If you add Taqwa to your watch,
Google or Apple may carry its data over the internet, encrypted."** The six translations follow,
each from its About row's words. Lengths: French 3,970 and Indonesian 3,995 of Play's 4,000; all
seven `listing: clean`. Each store naming only its own watch stays Task 10's (P5.10).

### (c) `STORE-PRIVACY.md`

"…from the phone's feed alone and is never told where it is" → **"…and is never given
coordinates"**, which agrees with the city the same document lists.

### (d) About's "Online only for what you choose", one per platform

`about_online_body` is gone. `about_online_body_android` names Google alone and
`about_online_body_ios` Apple alone (App Store Review Guideline 2.3.10), in seven languages and the
`values-in` twin. `AboutScreen` reads `aboutOnlineBody(isIosPlatform)`, a pure function like the
widget background's labels.
- `AboutOnlineBodyTest` (3 tests) was red first: the build failed on the missing key and function.
- The gate holds every text: 16 rows, each with the old key `<string name="about_online_body">` as
  the text that must be gone.
- `check-strings.sh` is clean, and `values-in` is byte-identical to `values-id`.

English, for M3's message:
- Android: "The internet is used for recitations you choose to download, from Taqwa’s public
  repository on GitHub. If you add Taqwa to your watch, **Google** may carry its data over the
  internet, encrypted so only your phone and watch can read it."
- iPhone: the same, with **Apple**.

The site's home-page row (3-4f) still says "Google or Apple": the site serves both phones.

### (e) The policy's "Your watch" lead-in and the review notes

- **The policy** now ends its list: "…and your own tasbeeh phrases, **with a random number made once
  on your phone that tells your watch which phone sent them.**" That is the P5.9 report's proposal.
  The compass correction was already there. Six translations follow.
- **The App Store Connect notes** (section 4) now say: "If the user adds Taqwa to their Apple Watch,
  the iPhone also sends it, through WatchConnectivity, the month's prayer times, the city, the
  Qibla's direction and distance, **the compass correction** and the user's own tasbeeh phrases,
  **with a random number made once on the iPhone that tells the watch which iPhone sent them**; Apple
  may carry them over Wi-Fi or cellular, end-to-end encrypted."
- The listings' short review notes list no fields, so they needed no change.
- **`STORE-PRIVACY.md`** says the policy names these in plain words. The hemisphere flag follows from
  the city, and the app's words are its own, so the policy names neither.

### (f) Backups: what is true, after a check

What Apple's pages say, read on 30 October 2026:
- "iCloud data security overview" (support.apple.com/102651): under standard data protection, the
  default, iCloud data is encrypted in transit and at rest, but "the keys to your backups are secured
  in Apple data centers", "so Apple can decrypt your data on your behalf". With Advanced Data
  Protection, "iCloud Backup and everything inside it is end-to-end encrypted".
- "About encrypted backups on your iPhone" (support.apple.com/108353): a backup to a computer "isn't
  encrypted by default".
- "Back up your Apple Watch" (support.apple.com/108358): the watch backs up to its iPhone, and of
  third-party apps it lists only their settings.
- `WCSession.applicationContext`: "the session object places a copy of your dictionary in this
  property". So iOS keeps the last copy sent.

What the iPhone keeps for the watch (read in the code):
- **The application context**, the feed with its delivery. It names the city, the bearing, the
  times and the phrases, and never coordinates. WatchConnectivity holds it. Apple documents neither
  where it is kept nor whether an iPhone backup holds it, and `WCSession` has no way to keep it out.
  A read-only look at the paired simulator's data folder (nothing booted or changed, no `simctl`)
  showed nothing of it inside Taqwa's own container.
- **`WatchFeedMemory`** (the origin, the last delivery and its confirmation), in the settings store
  under `Application Support/Taqwa`, which `dataStoreDirectory()` marks excluded from backup.
- **`taqwa-watch-key`**, a random UUID naming the watch, in `WCSession.watchDirectoryURL`. Its backup
  status is not documented, and it holds no place.
- The watch's own copy (`FeedStore`) is in a directory marked `isExcludedFromBackup`.

**So the copy cannot cleanly be kept out of the backup, and the sentence is now worded to be true.**
English, the 4d row (awaiting): "If you add Taqwa to an Apple Watch, iOS keeps the last copy your
iPhone sent it, **which names your city**, and that copy may be in your iPhone's backup. **An iCloud
backup is encrypted with keys Apple keeps, unless you turn on Advanced Data Protection, which
encrypts it end to end; a backup to a computer is encrypted only if you choose to encrypt it.**"
- The Arabic, French, Turkish and Indonesian drafts use Apple's own names for the feature in those
  languages, as far as I know them: حماية البيانات المتقدمة, protection avancée des données, Gelişmiş
  Veri Koruması, Perlindungan Data Lanjutan. Urdu and Bengali keep "Advanced Data Protection" in
  Latin script. The native reviewers should check each against the iPhone's own Settings.
- `STORE-PRIVACY.md`'s App Store section says the same, with the pages quoted. The answer, Data Not
  Collected, stands: the backup is the person's own, and neither Taqwa nor a partner can access it.

### (g) The notes keep the "predates" caveat

Section 1 now reads: "…of version 1.0.0 (32), from first launch through its main features. **It
predates the adhkar and the Apple Watch app.**" This is a new awaiting row (3-4n).

**The 4,000 bytes.** `tools/store-listing-check.py` now also checks the newest notes file at
4,000 UTF-8 bytes. It failed on 4,200 after (e) and (g), and passes on **3,999** after these trims.
They are copy choices for Mohamed with M3:
- the opening "Thank you for reviewing Taqwa.";
- "who want one simple, private app";
- "No account, login or" in section 3 (section 1 says it);
- "account deletion" → "deletion";
- "published" before terms;
- "the iPhone's" before on-device voices;
- "on the device" after the engine (section 2 says it);
- "once" after download;
- "with the authority" → "with it";
- "that mirrors … , and" → "mirroring … ;";
- "Our reply of 24 September" → "Our 24 September reply".

### (h) The report's shas, and the cut gate's grep

- P5.9's report header now gives all four shas in their two parts: part 1 `fefdc44e` + `dd14e26c`,
  part 2 `1835dc71` + `4dd3934d`. It also names this report for this run's two.
- Its two mentions of the cut gate's grep now skip `docs/superpowers`: `git grep -n "draft, to
  native review" -- . ':(exclude)docs/superpowers'`. The plans and reports there quote the marker,
  so the grep as the plan writes it can never print nothing.
- The plan's own lines (plan 5, 84 and 918) are the Lead's documents; errata p5's last entry holds
  the change for Task 11.

### (i) The tree walk fails loudly on a `.gitignore` it does not read as git does

`tools/privacy-check.py`'s `tree_files`, used where the tree is no git checkout, now raises
`UnsupportedIgnore`, and the gate prints `FAIL: <file>:<line>: …` and exits 1 with no traceback, on:
- a negation (`!`);
- a `**`;
- a backslash escape;
- a `.gitignore` below the root.

It does not walk the folders the sweep never reads (`tools/`, `docs/superpowers/`,
`.superpowers/`, `docs/research/`). Taqwa's own nested `.gitignore` files are under
`tools/timetables/official`, so they are never reached.

**A slash pattern:** the root `.gitignore` holds four, among them `*.xcworkspace/xcuserdata/`.
Refusing slash patterns would fail every copy. The bug was that `fnmatch` let a `*` match across a
`/`. So a slash pattern is now matched as git does: anchored to the root, segment by segment, with
`*`, `?` and `[…]` never crossing a slash. Git itself checks it: a test compares the walk with `git
ls-files -o --exclude-standard` on the same 16 files.

Seven new tests (`NoGit`, now 30 in all):
- the four refusals;
- the nested `.gitignore` under `tools/` that must not stop the walk;
- the git-checked slash patterns;
- the gate run as a command.

Six were red first (four errors on the missing exception, two failures). The seventh, the folder
under `tools/`, passed before the change and guards the pruning. Six mutants of the new code are each
killed: no pruning, no nested check, negation or `**` let through, the old whole-path `fnmatch`, and
`main` not catching the exception.

On a tar copy of the whole tree, the walk lists exactly git's 1,454 files outside the skipped folders,
and the gate there reads the same 214 sweep lines as in the checkout.

## Checks (final tree)

| Check | Result |
|---|---|
| `python3 tools/test_privacy_check.py` | 30 tests, OK |
| `python3 tools/privacy-check.py` | exit 1 on the 186 awaiting rows alone (0 other problems) |
| `python3 tools/privacy-check.py --allow-awaiting` | clean: 233 rows (47 approved, 186 awaiting), 7 policies, 214 sweep lines |
| `python3 tools/privacy-check.py --pages` | 145 rows, fails on its 130 awaiting rows alone |
| `scripts/check-strings.sh` | `i18n: clean`; `values-in` = `values-id` |
| `python3 tools/store-listing-check.py` | `listing: clean`; notes 3,999/4,000 bytes |
| `./gradlew -p tools/timetables generate`, `python3 site/build.py --check` | PASS, 164 pages; the built home pages and policies carry the new lines |
| `scripts/test.sh` (whole) | exit 0: versions and bump PASS, `test_privacy_check` and the gate, `swift test` 125/0, `tools/timetables` compiled, shared 2,274 host / 2,218 iOS simulator (`AboutOnlineBodyTest` 3 on each), widgetcore 111/109, watchcore 224/197, wearApp 398, 0 failures, no EOFException |
| `IOS_DEST='generic/platform=iOS Simulator' scripts/ios-build.sh build ARCHS=arm64` | BUILD SUCCEEDED; check-watch-bundle: every rule holds (widgets 1,820/5,120 KB, watch app 4,012/15,360 KB). The generic destination keeps off Lane A's simulators |
| `git diff --check` | clean |

## Findings for the Lead

1. **Ruling 39's sentence and iCloud's keys (for Mohamed, with M3).** The iPhone's watch copy names
   the city. If it is in a standard iCloud backup, Apple holds that backup's keys. The policy now
   says so. Whether "Only your own devices can see your location." still reads true enough beside it
   is his call: the only clean alternative would be a different WatchConnectivity call, which
   changes Rulings 50–56's link.
2. **The cut gate's grep also finds a test.** Skipping `docs/superpowers` is not enough:
   `wearApp/src/test/kotlin/world/taqwa/app/wear/app/SystemStringsTest.kt:107` holds the marker as a
   constant (`DRAFT`, Lane W's). Task 11 must change that test when it removes the markers, or the
   gate's grep must skip it.
3. **The site's About row in Bengali** (3-4f-home-row) still reads "Google বা Apple এর তথ্য", with the
   ambiguity fixed in (a), (b) and (d). It is not in Ruling 63's list, so it is left for Task 11.
4. **"The only network use is recitation"** stays in the notes' section 4 (round 1's minor),
   followed by "If the user adds Taqwa to their Apple Watch, the iPhone also sends…". For M3.
5. **(a) in five more languages** than the ruling names, and (i)'s slash patterns matched rather than
   refused: both are explained above.

## Rulings and errata, as built

- **Ruling 39** holds: city and whole-degree bearing only, never coordinates; the location sentence
  is unchanged and word for word. **Rulings 41, 42, 43, 50 and 52–62**: nothing here touches them.
- **Ruling 63**: items 1 and 4 (a)–(i) as above. Items 2 and 3 are R63b, the next task.
- **Errata (the run's list):**
  - 1: the merge was up to date.
  - 2: item 1 first and alone (`c4ab27fd`).
  - 3: test-first, split as P5.9 did.
  - 4: the p5 entries: the last one ("Ruling 63 items 1 and 4") is honoured. `test.sh` compiles
    `tools/timetables`; the grep skips `docs/superpowers` in the reports; M3's message carries the
    per-platform About text and the backup answer, in (d) and (f). Entry #5 (Task 10's presence
    rule) still does not bite. No other p5 entry touches this work.
  - 5: the rulings above.
  - 6: WIP commits `81b2883c`, `debd8643` and `8263f47c`, folded into the two parts by a soft reset.
  - 7: no EOFException, and no re-run was needed.
  - 8: Xcode 27.2 ready, so everything ran in full.
