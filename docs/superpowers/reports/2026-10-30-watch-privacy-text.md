# The privacy text, as Ruling 39 rules, everywhere it lives

Plan 5 Task 9, Lane P, on `watch-feed`, 30 October 2026. Base `ba3c7cc7` (= `watch-companion`;
the fast-forward merge was already up to date). Four commits, in the two parts Step 5 asks for.
M10a takes part 1's two, in this order, and no commit of part 2:
- part 1, the public pages:
  - `fefdc44e` "Privacy: only your own devices can see your location, and the watch's relay in the
    policy, in seven languages";
  - `dd14e26c` "Privacy: the gate runs in a copy that is no git checkout, after review";
- part 2, the rest:
  - `1835dc71` "Privacy: the app's promise for the watch, and the stores' forms answered again";
  - `4dd3934d` "Privacy: the stores' answers name the install's origin, and French and Indonesian
    fit Play's 4,000, after review".

Ruling 63 item 4's polish of these lines adds a commit to each part; its report,
`docs/superpowers/reports/2026-10-30-watch-privacy-polish.md`, names them.

Nothing is pushed. No device, emulator or lock was used.

## Where it stood on 30 October: built, waiting for M3 (given 31 October, below)

**Since 31 October 2026, M3 is given**, and every row is approved: see "M3, given 31 October
2026" at the end. The rest of this section is the record of 30 October.

**M3 is not given.** Mohamed has not approved plan 2's report, question 7's lines. As the task
allows, Ruling 39's own words went in as ruled (`approved`), and every line of question 7's went in
as its draft (`awaiting`). The cut gate therefore stays shut:

- `python3 tools/privacy-check.py` exits 1 and names **177 rows awaiting M3, and nothing else**;
- `python3 tools/privacy-check.py --allow-awaiting` (what `scripts/test.sh` runs) exits 0, with those
  177 rows as warnings.

When he answers, each awaiting row takes the text he approved, in every language, and its status
becomes `approved`, in a further commit split the same two ways.

## What changed

224 rows in `tools/privacy-check.py`, one per line of plan 2's report
(`docs/superpowers/reports/2026-10-23-watch-privacy-wording.md`), in each of its languages:

| Line | Where | Status |
|---|---|---|
| 1, the location line: "Only your own devices can see your location." | `NSLocationWhenInUseUsageDescription` (7 `InfoPlist.strings` + `Info.plist`), `onboarding_location_body`, `location_privacy_note` (`values`, `-ar -fr -tr -id -in -ur -bn`), the policy's Location section (7), the listings' location bullet (7), `README.md` | approved (ruled) |
| 2, the relay sentence | a new "Your watch" section in each policy, before Location | approved (ruled) |
| 2, that section's heading and lead-in | the same | awaiting |
| 3, 4a–4n | About's two rows, the home page's privacy heading, lede and rows, support's "Everything Taqwa knows", the privacy page's description, the policy's opening, "What stays", deletion, Backups, first request and SDK lines, the listings' internet bullet, CONTRIBUTING, SECURITY, README, the review notes (in the six listings that carry them) and the new App Store Connect notes file | awaiting |
| 4n, `en.md`'s pointer to the newest notes file | `docs/store/listing/en.md` | approved (a pointer, no wording) |

Also:
- every policy's "Last updated" is **30 October 2026**, in its language's own form (Bengali digits
  in Bengali);
- the new notes file `docs/store/app-review/2026-10-30-notes-1.4.0-40.txt` is the report's draft,
  **3,992 bytes** (`wc -c`), within the field's 4,000;
- the six translated `InfoPlist.strings` comments no longer say "the location never leaves it";
- `docs/STORE-PRIVACY.md`: both forms answered again (below), a Wear OS section and an Apple Watch
  section;
- `scripts/test.sh` runs the checker's tests and `privacy-check.py --allow-awaiting`, after the
  version checks.

The English of the awaiting lines, for the message to Mohamed, is the report's sections 3 and 4
word for word; the checker's rows quote each one in full.

## The gate: `tools/privacy-check.py`

It fails, naming each row, on: an old text still there (outside its own new text, since 4k's new
line begins with the old one); a new text missing (whitespace and line wraps do not count); a row
awaiting M3, unless `--allow-awaiting`; a `PRIVACY*.md` dated before the watch branch
(19 October 2026); and any line of plan 2's sweep that is in no row and in none of the report's
lists (items 4 and 5, and its kinds of line about something else, held as `KNOWN` with each one's
reason). `--pages` checks only the public pages' 145 rows and sweeps only those files; `--root`
reads another tree.

- Step 1 was red on every one of the 200 row keys (the three rows that hold 4b's unchanged Turkish,
  Urdu and Bengali sentence in place failed as awaiting), on all seven dates, and on all 196 sweep
  lines.
- `tools/test_privacy_check.py`, 20 tests on scratch trees, first failed with the checker missing.
  Ten mutants of the checker (old text counted inside its new text, awaiting never failing, the
  date compare, non-Latin digits, row overlap, KNOWN ignored, the translation filter, the pages
  filter, whitespace kept, a missing new text not reported) are each killed by the test meant for
  it.
- Python's case folding matches Turkish's dotted capital İ, so the sweep reads one line more than
  BSD grep: `docs/store/listing/tr.md`'s "İnternet" bullet, which is row 4l's own line.

## The stores' forms answered again (Step 3)

Read on 30 October 2026, as on 24 October: Google still exempts data sent off the device that only
the sender and recipient can read because of end-to-end encryption, and Apple still counts data as
collected only when the developer or its partners can access it for longer than servicing the
request in real time. The quotes in `STORE-PRIVACY.md` are kept short, with each page named.

- **Play Data safety: "No" stays** (recommended). The feed goes only to his own watch, directly
  over Bluetooth or through Google's relay end-to-end encrypted. The fallback is written out, as
  the plan words it. **One addition of mine, for his approval:** if the fallback is ever needed,
  his tasbeeh phrases would also be "Other user-generated content" under App activity, since the
  plan's fallback names only approximate location.
- **App Store App Privacy: "Data Not Collected" stays** (recommended). WatchConnectivity moves the
  feed between his own devices, and neither Taqwa nor a partner can access it.
- **Both wait for Mohamed's approval in chat** before Task 17 enters them.
- The merged release manifests were read again: the phone still holds exactly its twelve
  permissions. The Wear app holds `VIBRATE` and androidx.core's own permission, no `INTERNET` and no
  location, and its optional libraries are `required="false"`. `wearApp/permissions.txt` does not
  exist yet (plan 5 Task 3), so the section names it as the list from then on.

## Checks (Step 4)

| Check | Result |
|---|---|
| `python3 tools/privacy-check.py` | fails on the 177 awaiting rows alone, as expected before M3 |
| `python3 tools/privacy-check.py --allow-awaiting` | PASS: 224 rows, 7 policies, 208 sweep lines |
| `python3 tools/test_privacy_check.py` | 20 tests, OK |
| `scripts/check-strings.sh` | `i18n: clean`; `values-in` byte-identical to `values-id` |
| `python3 tools/store-listing-check.py` | **FAILED** for French (4,124/4,000) and Indonesian (4,122/4,000); see finding 1. PASS since review fix round 1 (French 3,975, Indonesian 3,983) |
| `python3 site/build.py --check` | PASS, built with timetables generated through a temporary fix; see finding 2 |
| notes file | 3,992 bytes |
| `scripts/test.sh` (whole, simulators included) | exit 0: bump PASS, the privacy gate's tests and the gate, swift test 97/0, shared 2,271 host / 2,215 iOS, widgetcore 111/109, watchcore 224/197, wearApp 398, 0 failures, no EOFException |
| `IOS_DEST='generic/platform=iOS Simulator' scripts/ios-build.sh build ARCHS=arm64` | BUILD SUCCEEDED, every watch-bundle rule holds; the built `ar.lproj/InfoPlist.strings` carries the new sentence |
| merged release manifests | the phone's twelve permissions unchanged; the Wear app's are `VIBRATE` and androidx.core's own |

Step 5: in a scratch detached worktree at `origin/main` (`9d3ba3ef`), `git cherry-pick fefdc44e`
applied with no conflict. There, `site/build.py --check` passed (main's `tools/timetables` builds),
and `privacy-check.py --pages --allow-awaiting` passed. Without the flag it failed on its 130
awaiting rows alone. The scratch worktree was removed.

## Findings for the Lead

1. **French and Indonesian Play full descriptions were over 4,000 characters.** They were already
   at 3,998 and 3,997. Ruling 39's location line alone adds 19 and 20 characters, so even the ruled
   change overflows them, and 4l's watch sentence adds about 105 more. Making them fit means
   cutting other store copy in those two languages, which is a copy choice. Nothing was cut in the
   first pass; review fix round 1 drafts the cut (below), for Mohamed with M3 and for native review.
   Task 10 must still cut every language to fit its "On your wrist" paragraph (English would reach
   about 4,150 with it).
2. **`tools/timetables` does not compile on `watch-companion`** since P3.2 (`0c4a4e73`): `QiblaMath`
   delegates to `QiblaAngles`, now in `:watchcore`, which the tool does not compile. The Pages
   workflow would fail once the watch branch reaches main. The fix is two lines in
   `tools/timetables/build.gradle.kts`: `"world/taqwa/app/qibla/QiblaAngles.kt"` in `appSources`,
   and `kotlin.srcDir(repoRoot.resolve("watchcore/src/commonMain/kotlin"))` beside widgetcore's.
   `.github/workflows/pages.yml`'s paths also need watchcore's `qibla/QiblaAngles.kt`. With that
   patch, `generate` and `CI=true jvmTest` pass. It was applied only for this check, then
   reverted, because it is outside this task's files.
3. **Each store should name only its own watch** (Task 10). The 4l bullet and the review notes say
   "Google or Apple" in both stores' text. When Task 10 gives the App Store its own description,
   "by Apple" fits there and "by Google" on Play.
4. **The App Store Connect notes have 8 bytes of room left.** Task 10 cannot add the Apple Watch's
   features to section 3 without cutting something else.
5. **Backups** now says "the only things of Taqwa's that are backed up are the widgets' small
   caches" and then adds the watch's copy. He may prefer "Besides these" in the added sentence
   (M3).
6. `en.md`'s pointer named the 29 September notes file although the 2 October one was newer. It
   now names the new file, and both older files stay as the record.

## For Task 11 (native review)

- 144 markers "draft, to native review (plan 5 Task 11)":
  - `PRIVACY.{ar,bn,fr,id,tr,ur}.md`, 10 each;
  - the six translated Compose `strings.xml` and `values-in`, 6 each;
  - the six translated `InfoPlist.strings`, 1 each;
  - `site/pages/<lang>/home.html`, 4 each, and `support.html`, 1 each;
  - one note per translated listing, at the end of its Play full description.
- **No comment possible:** `site/pages/{ar,fr,tr,id,ur,bn}/meta.json`, `privacy.description` (4h),
  six lines.
- The review notes' copies in the fr, tr, id, ur and bn listings are English, so they need no
  review.
- Plans 2–4 mark theirs "(plan 5)". This task's markers follow its own wording, "(plan 5 Task 11)".
  Both match the cut gate's grep, which skips `docs/superpowers` (Ruling 63 (h)), since the plans
  and reports there quote the marker: `git grep -n "draft, to native review" -- .
  ':(exclude)docs/superpowers'`.

## The binding rulings and the errata, as built

- **Ruling 39:** the sentence is "Only your own devices can see your location." word for word,
  which a test holds for every English location row. The policy's "Your watch" lead-in names the
  city, the Qibla's direction and distance and the compass correction, and never coordinates.
  `STORE-PRIVACY.md` says the same. Since review fix round 1 it also says what the watch's
  acknowledgement and handshake echo: the delivery it holds (Ruling 50), that is the phone install's
  random origin, the delivery number and the fingerprint, and no place.
- **Rulings 41–43 and 52–58:** nothing in this task touches them.
- **Errata:**
  - 1: the fast-forward merge was already up to date.
  - 2: built on MM2's site pages and strings, with no cautious line changed. M3 is open, as
    above.
  - 3: Android `values-in` was not touched. The Compose `values-id` and its `values-in` twin were
    both changed and are byte-identical. Every non-English line is marked.
  - 4: erratum p5 #5 (Task 10's presence rule) is not yet in `store-listing-check.py`, so it does
    not bite here. Task 10 still needs to key it to the newest notes code. No other p5 entry
    touches this task.
  - 6: WIP commits `15955c2c` and `4eb4ef68`, folded into the two parts.
  - 8: `xcodebuild -checkFirstLaunchStatus` exited 0, so everything ran in full.

## Review fix round 1

Review round 1 (two lenses, on `ba3c7cc7..1835dc71`) found no Critical and four Important items,
two of them the same. All are fixed here; no ruling is contradicted (39, 41, 42 and 43 hold).

1. **The listing check failed, and the failure was committed** (Important 1 and 3 of the two
   lenses). Step 4 expects PASS, and the map commits no red step. No Lead ruling handed the cut to
   Task 10, so the cut is drafted here, in French and Indonesian only, and marked in each listing's
   note as a draft for Mohamed's approval with M3 and to native review (plan 5 Task 11), so the cut
   gate's grep for "draft, to native review" (outside `docs/superpowers`) holds it:
   - both: the website and support lines at the end are left out, since Play's contact details and
     App Store Connect's support and marketing URLs show them in their own fields;
   - French: the opening's last sentence ("Gratuite, sans publicité ni compte, elle fonctionne hors
     ligne."), the Mushaf bullet ("et, au choix, la mise en page du Mushaf imprimé") and the
     recitation bullet ("les sourates s'enchaînent") say the same in fewer words;
   - Indonesian: the city bullet ("kota pilihan Anda"), the Mushaf bullet ("serta, bila Anda mau,
     tata letak halaman Mushaf cetak"), the recitation bullet ("surah berikutnya langsung menyusul")
     and the tasbeeh bullet (without "di dalamnya") likewise.

   No feature, credit, cautious-times sentence or privacy row was touched: the opening's "Berfungsi
   offline" (a `KNOWN` line) stays, and so does every line of the rows 1, 4l and 4n. French is now
   3,975 and Indonesian 3,983 of 4,000. The App Store description is still "the same text", so it
   fits too. Task 10's own cut, for every language, supersedes this one.
2. **`STORE-PRIVACY.md` was wrong about what crosses the watch link** (Important 2). It said the
   echo carries the phone's fingerprint and delivery number. `WatchLinkContract` makes every feed
   travel with, and every acknowledgement and handshake echo, `origin/seq/fingerprint`, and the
   origin is a random number made once per install: an identifier of the app install. The document
   now names the delivery and the origin in "what the feed holds" (with the Data Layer path and the
   iPhone's `delivery` key), and names the hemisphere flag (`place.southern`, `WatchFeedBuilder.kt`
   269). Play's "No" row now says the delivery travels the same way. The fallback adds "Device or
   other IDs" for the origin, quoting Google's definition (read on 30 October 2026), and counts the
   hemisphere flag as approximate location. The App Store answer names the delivery and the watch's
   handshake. The recommended answers, "No" and "Data Not Collected", stand: the origin goes only
   where the feed goes, end-to-end encrypted, and only the phone and the watch can read it.
3. **The policy's "Your watch" lead-in has the same gap: for the Lead, with M3.** The lead-in
   (question 7's draft, awaiting) names the city, the Qibla's direction and distance, the compass
   correction, the month's times and the tasbeeh phrases. Plan 2's question 7 meant "this data" in
   the relay sentence to cover every field of plan 2's table, which since Ruling 50 lists the
   install's origin and the delivery number. Nothing was changed, since the text awaits his answer.
   Proposed for M3, in English: end the lead-in "…and your own tasbeeh phrases, with a random number
   made once on your phone that tells your watch which phone sent them." The hemisphere flag
   follows from the city, so it needs no words of its own. If he takes it, the six translations
   follow, each to native review.
4. **`scripts/test.sh` needed a git checkout** (Important 4). `privacy-check.py` asked `git
   ls-files` and crashed with a `CalledProcessError` in a git-archive copy or a source zip. It now
   asks git only where the root is the top of its own work tree (`git rev-parse --show-toplevel`),
   and otherwise walks the tree, without `.git` and without what the root `.gitignore` ignores
   (`build/`, `_site/`, `.superpowers/` and the rest). That also closes a silent pass the crash hid:
   a copy unpacked inside another repository would have been listed by that repository's `git
   ls-files` as no files at all. Three new tests (`NoGit`) were red first (one crash, two
   assertions): a tree without git swept without its ignored and build files; a copy inside another
   repository walked, not listed; and the whole gate run as a command on a tree without git, which
   names what it finds and prints no traceback.

### Checks (review fix round 1)

| Check | Result |
|---|---|
| `python3 tools/test_privacy_check.py` | 23 tests, OK (the 3 new ones red before the fix) |
| `python3 tools/privacy-check.py --allow-awaiting` | PASS: 224 rows, 7 policies, 210 sweep lines (the 2 more are `STORE-PRIVACY.md`'s new lines, under its whole-document `KNOWN` entry) |
| `python3 tools/privacy-check.py` | fails on the 177 awaiting rows alone, as expected before M3 |
| the same two in a `git archive` copy with no `.git`, built and unbuilt | the same 210 sweep lines and the same results; planted files under `build/` and `_site/` are skipped |
| `python3 tools/store-listing-check.py` | PASS (`listing: clean`): French 3,975, Indonesian 3,983 |

The rest of the round's checks are in the ledger's fix round 1 line.

### Minors left (also in the ledger)

From the code lens: the sweep masks a promise on a line that holds a row's new text (so the drafts'
own "The one thing it uses the internet for" on the home page, "The only network use is
recitation" in the notes file and "works offline except recitation" in the review notes pass
unseen: question 7's drafts, for M3); the notes file's first paragraph drops the older file's
"predates" caveat; the date check cannot see edits after M3; the whole-file `KNOWN` entries; the gate
is not in `tests.yml`'s or `ios.yml`'s paths; 4l's "Google or Apple" in the App Store's shared
description (Task 10, finding 3); the iCloud Backup keys against the promise (for M3); `main()` was
untested (now run end to end by `NoGit`'s gate test, the rest of it still not); the first DONE
ledger line stamped about 20 minutes ahead (the ledger is append-only, so it stays).

## M3, given 31 October 2026

Lane P, on `watch-feed`, 31 October 2026. Base `3ce05e8b` (`git merge --ff-only watch-companion`
from `ea6ac807`; `watch-feed` had no commits of its own). Two commits, split as P5.9's were, so
that M10a can take the public part alone:
- part 1, the public pages: `516253d5` "Privacy: every line as Mohamed approved it, with his
  opening and Backups sentences, in seven languages (M3)" (`PRIVACY*.md`, `tools/privacy-check.py`,
  `tools/test_privacy_check.py`);
- part 2, the rest: the commit that holds this section, "Privacy: the suite runs the strict gate,
  and the stores' answers are approved (M3)" (`scripts/test.sh` and its test, `docs/STORE-PRIVACY.md`,
  plan 5 Task 9 and this report).

Nothing is pushed. No device, emulator, simulator or lock was used.

### His answer

The ledger's "[Lead] 31 Oct ~13:30 SAST: M3 GIVEN". Mohamed, in chat, after the M3 page: "M3
approved, with your two suggested tweaks (the opening and the backups sentence)." Every awaiting
row is approved as drafted, but for the two English changes, which the six other languages carry
with the same meaning:

| Where | Drafted | Now |
|---|---|---|
| The policy's opening, its last sentence (row `3-4i-policy-opening`) | A watch you add Taqwa to may be reached over the internet too, encrypted, as below. | If you add Taqwa to your watch, its data may travel over the internet too, encrypted, as below. |
| Backups, the section's earlier sentence (new rows `3-4d-policy-backups-caches`) | the only things of Taqwa's that are backed up are the widgets' small caches, …, neither of which contains your location, and that backup is encrypted by Apple and never visible to us. | the only files of Taqwa's own that are backed up are the widgets' small caches, …, neither of which contains your location. |

The Backups sentence was main's own text, in no row until now. Its new rows hold, in each language,
the clause he dropped as the old text and his sentence as the new, so neither the clause nor the
"things of Taqwa's" wording can come back unseen. The sentences after it (R63a's) say how an
iPhone backup is encrypted.

The stores' recommended answers, Play's "No" and the App Store's "Data Not Collected", were
approved with it (plan 5's M9). `STORE-PRIVACY.md` says so, and Task 17 enters them as written.

### The translations

Each is a draft to native review (plan 5 Task 11), inside a paragraph that already carries its
`<!-- draft, to native review (plan 5 Task 11) -->` marker, so the cut gate's grep still finds it.
For Task 11's table:

| Language | The opening's last sentence | Backups' earlier sentence |
|---|---|---|
| ar | وإن أضفت تقوى إلى ساعتك، فقد تنتقل بياناته عبر الإنترنت أيضًا، مشفّرةً، كما يأتي أدناه. | وكل ما يُنسخ احتياطيًا من ملفات تقوى نفسه هو ذاكرتا الأدوات الصغيرتان: …، وليس في أيٍّ منهما موقعك. |
| fr | Si vous ajoutez Taqwa à votre montre, ses données peuvent aussi transiter par Internet, chiffrées, comme expliqué plus bas. | les seuls fichiers propres à Taqwa qui soient sauvegardés sont les petits caches des widgets, …, dont aucun ne contient votre position. |
| tr | Taqwa'yı saatinize eklerseniz, verileri de aşağıda anlatıldığı gibi şifreli olarak internet üzerinden iletilebilir. | Taqwa'nın kendi dosyalarından yedeklenen tek şey widget'ların küçük önbellekleri, … yüz ayettir. Bunların hiçbiri konumunuzu içermez. |
| id | Jika Anda menambahkan Taqwa ke jam tangan Anda, datanya juga dapat dikirim lewat internet, secara terenkripsi, seperti dijelaskan di bawah. | berkas milik Taqwa sendiri yang dicadangkan hanyalah cache kecil milik widget, …, dan keduanya tidak memuat lokasi Anda. |
| ur | اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو اس کا ڈیٹا بھی انٹرنیٹ کے ذریعے، خفیہ کاری کے ساتھ، منتقل ہو سکتا ہے، جیسا کہ نیچے بیان ہے۔ | تقویٰ کی اپنی فائلوں میں سے صرف وہ بیک اپ ہوتی ہیں جو ویجٹ کے چھوٹے ذخیرے ہیں — … — اور ان میں سے کسی میں آپ کا مقام نہیں ہوتا۔ |
| bn | তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্যও ইন্টারনেটের মাধ্যমে, এনক্রিপ্ট করা অবস্থায়, যেতে পারে, যেমনটি নিচে বলা আছে। | তাকওয়ার নিজের ফাইলের মধ্যে যা ব্যাকআপ হয় তা কেবল উইজেটের ছোট ক্যাশ — … — আর এর কোনোটিতেই আপনার অবস্থান থাকে না। |

The opening's translations follow the listings' approved 4l bullets ("If you add Taqwa to your
watch, … may carry its data …"), so the policy and the store copy say it alike. "Its data" is
Taqwa's on the watch, as the English means it; the Arabic says بياناته (Taqwa's), and the others
keep the English's own "its". Each paragraph was re-wrapped from the changed line to its end at its
own width. No app string, plist, listing, site page, README, CONTRIBUTING or SECURITY line changed:
neither sentence appears anywhere else, and the site's privacy pages are built from `PRIVACY*.md`
(checked in the built `_site/<lang>/privacy/` of all seven: the new sentences there, the old
clauses and the drafted opening gone).

### The gate, test-first

- **Red, the tests** (`tools/test_privacy_check.py`, a new `M3` class): 5 failures, each for its
  reason: 194 rows awaiting; the English opening still the draft; no `3-4d-policy-backups-caches`
  rows; `scripts/test.sh` still passing `--allow-awaiting`; the README row's status `awaiting`.
  The superseded `test_only_ruling_39s_lines_count_as_approved_before_m3` is replaced by
  `test_every_row_is_approved_since_m3`.
- **Red, the tree:** with the rows changed and the policies not yet, the strict gate failed on
  exactly the 14 new texts (7 openings, 7 Backups sentences), the 7 dropped clauses still there, and
  the 3 lines of the drafted English opening, which no row held any more (the sweep).
- **Green:** 248 rows (248 approved, 0 awaiting), 7 policies, 232 sweep lines, clean; `--pages`
  153 rows, clean; the tests 35, OK.
- **Mutants, each killed:** the French dropped clause put back (2 failures, by its row); one Urdu
  Backups row set back to `awaiting` (the gate and `test_every_row_is_approved_since_m3`); the
  English drafted opening put back (its row and 3 sweep lines).
- **The strict gate in the suite:** `scripts/test.sh` runs `python3 tools/privacy-check.py` without
  `--allow-awaiting` (plan 5 Task 9: the flag was "so that the suite stays green for other work
  until M3"). `test_scripts_test_sh_runs_the_strict_gate_since_m3` holds it, and holds vacuously on
  main, whose `scripts/test.sh` runs neither the gate nor its tests. That test and the `test.sh`
  change are in part 2, so part 1 is green alone.
- The flag itself stays, for a row added later that waits for him.

### The date

Every policy's "Last updated" stays **30 October 2026**, which the date check accepts (on or after
19 October). The text changed on 31 October, before any of it is live. If the Lead wants the
published date to be the day of the last change, it moves at M10a, in all seven languages with
the Bengali digits.

### Checks

| Check | Result |
|---|---|
| `python3 tools/privacy-check.py` (strict, the cut gate's) | PASS: 248 rows (248 approved, 0 awaiting), 7 policies, 232 sweep lines |
| `python3 tools/privacy-check.py --pages` | PASS: 153 rows, 54 sweep lines |
| `python3 tools/test_privacy_check.py` | 35 tests, OK |
| `scripts/check-strings.sh` | `i18n: clean` (no string changed) |
| `python3 tools/store-listing-check.py` | `listing: clean`; the newest code, 40 (1.4.0), has both notes files |
| `CI=true ./gradlew -p tools/timetables jvmTest generate`, then `python3 site/build.py --check` (erratum p5 #18) | PASS: 55 cities (2 held), 129 pages; 164 pages in 7 languages, links, assets, structured data, sitemap and timetables all good |
| `scripts/test.sh` (whole; `xcodebuild -checkFirstLaunchStatus` 0) | exit 0: versions agree, bump PASS, the gate's tests and the strict gate, the listing tests and check, swift test (WatchCoreTests 285, PhoneHarnessTests 14 and the rest, 0 failures), Gradle BUILD SUCCESSFUL (90 of 103 tasks up to date and 4 from cache: no Kotlin, Swift or resource input changed), no EOFException |
| part 1 alone, in a `git archive` of `516253d5` | the tests OK, strict gate clean (231 sweep lines: part 2's new `STORE-PRIVACY.md` line is the 232nd) |

**Step 5, on `origin/main` (`9d3ba3ef`), in a scratch detached worktree, removed after:**
- the literal chain: P5.9's and R63a's public commits (`fefdc44e`, `dd14e26c`, `8c6e665f`) cherry-pick
  clean, and `516253d5`'s seven `PRIVACY*.md` apply on them with no conflict, but its
  `tools/privacy-check.py` and `tools/test_privacy_check.py` conflict, because P5.10's rows and
  tests (`4722656d`, `7b68f80f`, `50732b35`) are not in that chain, and `4722656d` itself does not
  cherry-pick onto main alone (README, the listings, the notes file, `scripts/test.sh`, the two
  checkers). So M10a needs the Lead's main-side commit for P5.10's public part first, as plan 5 Task
  9 Step 5 allows, and then `516253d5` applies;
- the public pages as M10a will have them before M3 (main, with `PRIVACY*.md`, `site/pages/`,
  `README.md`, `CONTRIBUTING.md`, `SECURITY.md` and the two gate files as at `3ce05e8b`), then
  `516253d5`: cherry-pick clean; the tests OK; `privacy-check.py --pages` strict PASS, 153 rows, 0
  awaiting; `tools/timetables` generate and `site/build.py --check` PASS (164 pages).

### The rulings and errata, as built

- **Ruling 39:** "Only your own devices can see your location." is unchanged in every row; the
  policy's "Your watch" names the city and the Qibla's direction and distance, never coordinates.
  Neither tweak touches what the feed holds or who can read it.
- **Rulings 41, 42, 43, 50, 52–68:** nothing here touches the face, the reading order, the
  glyph-splitting fonts, the delivery numbers, the Qibla or the Tile.
- **Errata (this run's list):** 1, the fast-forward merge; 2 and 3, as above; 4, the translations marked (inside
  marked paragraphs), and the commits split, public pages first; 5, no device, and the commits'
  messages end "(M3)"; 6, plan errata p5: #18 (the site's generate step and `site/build.py
  --check`) run, #6 (the listing check's presence rule, keyed to the newest notes code) passes, no
  other entry touches this task; 7, the M3 GIVEN line followed word for word; 8, WIP commits
  `32d8d494` and `674f0236`, folded by a soft reset into the two parts; 9, no Gradle flake seen; 10,
  `scripts/test.sh` ran whole, and no simulator was taken.

### For the Lead

1. **M10a's chain:** `516253d5` comes after P5.10's public part, which needs a main-side commit
   (Step 5 above).
2. **The listings' notes** "(Plan 5 Task 10's cut, … for Mohamed's approval: …)" are P5.10's cut,
   not a privacy row, so M3 did not change them. If the M3 page carried the cut and his approval
   covers it, the notes need their "for Mohamed's approval" removed; if not, the cut still waits for
   him.
3. **The date** stays 30 October 2026 (above).
4. Task 11's table gains the 12 translated sentences above, in paragraphs already listed by their
   markers.

## Ruling 69 item 1, 31 October 2026

Lane P, on `watch-feed`, 31 October 2026. Base `65798ae9` (`git merge --ff-only watch-companion`:
already up to date). Two commits, split as M3's were, so that M10a can take the public part alone:
- part 1, the public pages: `f9e70de7` "Privacy: the home page says what the internet is used for
  in the approved words, and the gate holds every drafted opening, in seven languages (Ruling 69)"
  (`site/pages/*/home.html`, `tools/privacy-check.py`, `tools/test_privacy_check.py`);
- part 2, the rest: the commit that holds this section, "Privacy: the App Review notes say what the
  internet is used for in the approved words, and the sweep sees a promise left beside a row's new
  text (Ruling 69)" (the App Store Connect notes file, `tools/privacy-check.py` and its tests, plan
  5 Task 9, this report and plan 2's report).

Nothing is pushed. No device or emulator was used; `ios-sim-tests` was held for `scripts/test.sh`
alone.

### What the ruling asked, and what changed

The ledger's Ruling 69 item 1: no page or note still says recitation is the one thing Taqwa uses
the internet for; the lines take the approved rows' wording; the sweep must see them; rows hold
the six translated drafted openings; the strict-gate test joins continued lines; the stale report
heading and plan 2's report's pointer are fixed. No new promise text was written: each new line is
an approved row's own words.

| Where | Was | Now |
|---|---|---|
| `site/pages/<lang>/home.html`, the privacy lede, 7 languages | "The one thing it uses the internet for is recitation:" | "The internet is used for recitation:", the approved store bullet's words (4l); the lede's watch clause is its own approved last sentence (4g) |
| `docs/store/app-review/2026-10-30-notes-1.4.0-40.txt`, section 4 | "The only network use is recitation:" | "The internet is used for recitation:" (4l's words); the watch clauses are the notes' own approved ones, section 2's "it sends only to the user's own Apple Watch" and section 4's next sentence. 3,999 of 4,000 bytes |

The translations, each the approved 4l bullet's translation, inside paragraphs that already carry
`<!-- draft, to native review (plan 5 Task 11) -->` (254 markers outside `docs/superpowers`, as
before), for Task 11's table:

| Language | Was | Now |
|---|---|---|
| ar | الشيء الوحيد الذي يستخدم الإنترنت لأجله هو التلاوة: | يستخدم التطبيق الإنترنت للتلاوة: |
| fr | La seule chose pour laquelle l’application utilise Internet, c’est la récitation : | Internet sert à la récitation : (the page's narrow no-break space kept) |
| tr | İnterneti tek bir şey için kullanır: tilavet. | İnternet tilavet için kullanılır. |
| id | Satu-satunya hal yang membuatnya memakai internet adalah tilawah: | Internet dipakai untuk tilawah: |
| ur | انٹرنیٹ صرف ایک کام کے لیے استعمال ہوتا ہے: تلاوت۔ | انٹرنیٹ تلاوت کے لیے استعمال ہوتا ہے۔ |
| bn | ইন্টারনেট একটিমাত্র কাজে লাগে — তিলাওয়াত: | ইন্টারনেট ব্যবহার হয় তিলাওয়াতের জন্য: |

For the native reviewers: in Turkish and Urdu the old words ended their sentence ("…: tilavet." /
"…: تلاوت۔"), so the new clause ends it too, and the next sentence is unchanged. Its subject stays
implied in Turkish ("onu alır": Taqwa), and Urdu's stays passive.

The gate, `tools/privacy-check.py` (248 rows to 263, all approved):
- **rows `3-4g-home-lede-recitation`**, seven, one per home page: the old words gone, the new
  sentence there;
- **rows `3-4i-policy-opening-drafted`**, seven: question 7's drafted opening sentence in each
  policy as the old text, Mohamed's approved sentence as the new. The ruling names the six
  translations; the English row is there too, because the table test holds every non-English-only
  row in all seven languages (the sweep already caught the English draft);
- **one more `3-4n-notes-file` row:** "The only network use is recitation" gone, "The internet is
  used for recitation: …" there;
- **the sweep:** only a row's own words are the row's. On a line that holds a row's new text, the
  words beside it are swept as any other line is, and `KNOWN` is matched against those words, not
  the whole line. That closes P5.9's code-lens minor and M3's minor 7 (the masked home lede, notes
  and review notes). A failure shows the words from just before the sweep's first hit, so a long
  line shows what was caught;
- **four `KNOWN` entries** for what the report keeps beside a row, each held whole from the row's
  edge to the line's (`^…$`), so that anything added there is seen: `CONTRIBUTING.md:38`'s "No
  analytics, no crash-reporting SDKs, no account. There" (4m's line, still true); `SECURITY.md:71`'s
  list label "Third-party services:" (4m); the review notes' other sentences in the six listings that
  carry them, "Everything works offline except recitation downloads, …" among them (4n kept them,
  "The rest stands", and M3 approved them; see For the Lead 1); and `en.md:157`'s instruction to keep
  the notes current (4n, the pointer);
- the docstring says so, and its 144-character line is wrapped (M3's minor 4).

`tools/test_privacy_check.py`: the strict-gate test reads `scripts/test.sh` through
`script_commands`, which joins a line ending in a backslash to the next, as the shell does (M3's
minor 2); the three blank lines before `class M3` are two (M3's minor 5).

The documents: this report's heading "Where it stands: built, waiting for M3" now reads "Where it
stood on 30 October: built, waiting for M3 (given 31 October, below)". Plan 2's report: its 4n
said `en.md:117`'s pointer "still names the 29 September file", which since P5.9 is `en.md:157`
naming the 30 October file; 4n and its gate list say so now, and its first paragraph points to
this report, M3 and the gate. Plan 5 Task 9's Interfaces say how the sweep reads a covered line.

### Test-first

- **Part 1, red:** the three new `Ruling69` tests failed for their reason (no such rows). With the
  rows and the pages not yet changed, the strict gate failed on exactly 14 problems, the seven home
  pages' old words still there and their new sentences missing. M3's code-lens mutant (the French
  drafted opening appended to `PRIVACY.fr.md`, in a `git archive` copy) passed `65798ae9`'s gate
  clean and fails the new one by `3-4i-policy-opening-drafted [fr]`. **Green:** the seven pages
  changed; strict gate clean (262 rows), `--pages` clean, the tests OK.
- **Part 2, red:** four new tests failed, each by its assertion: the two sweep tests (a promise beside
  a row's text, and a `KNOWN` pattern of the row's own words that must not excuse it), the
  continued-flag test, and the notes row. The continued-flag mutant of `scripts/test.sh`
  (`python3 tools/privacy-check.py \` with `--allow-awaiting` on the next line, in a copy) passed the
  old line-by-line test and fails the new one. With the sweep changed, the gate on the tree failed on
  10 lines beside rows (11 on the base tree, `site/pages/en/home.html:106` the 11th): the notes
  file's line 16 and the nine kept ones above. With the notes row it failed on the notes' old and new
  text too. **Green:** the notes sentence changed, the four `KNOWN` entries added; strict gate clean.
- **Mutants of the tree, each clean under M3's gate (`65798ae9`) and failing the new one:** "Your
  location never leaves your phone." added to the English home lede (m1); "Nothing leaves the
  phone." added to the notes' section 4 (m2); "Nothing goes to the internet." added to `en.md`'s
  review notes (m3). With the review notes' `KNOWN` pattern unanchored, m3 passes again, and the new
  test `test_a_sentence_added_beside_the_review_notes_row_is_seen` fails: the anchors are what hold
  it.

### Checks

| Check | Result |
|---|---|
| `python3 tools/test_privacy_check.py` | 44 tests, OK |
| `python3 tools/privacy-check.py` (strict, the cut gate's) | PASS: 263 rows (263 approved, 0 awaiting), 7 policies, 232 sweep lines |
| `python3 tools/privacy-check.py --pages` | PASS: 167 rows, 54 sweep lines |
| `scripts/check-strings.sh` | `i18n: clean` (no string changed) |
| `python3 tools/test_store_listing_check.py`, `python3 tools/store-listing-check.py` | 42 tests OK; `listing: clean`, the newest code 40 (1.4.0) has both notes files; the notes 3,999 bytes |
| `CI=true ./gradlew -p tools/timetables jvmTest generate`, then `python3 site/build.py --check` (erratum p5, Ruling 63 item 1) | PASS: 164 pages in 7 languages; the built home page in all seven holds the new words and none of the old |
| part 1 alone, a `git archive` of `f9e70de7` | tests OK (38); strict gate clean (262 rows, 232 sweep lines); `--pages` clean; listing clean |
| Step 5, in a copy of `origin/main` (`9d3ba3ef`) with the public pages and the two gate files as at `65798ae9` (M10a's state once P5.10's and M3's public parts are on main) | part 1's diff applies clean; the tests OK; `--pages` strict PASS (167 rows); `tools/timetables` generate and `site/build.py --check` PASS (164 pages) |
| `scripts/test.sh` (whole; `xcodebuild -checkFirstLaunchStatus` 0; under `ios-sim-tests`, held 14:58:56 after Lane A's run) | exit 0 in 16 s: versions agree, bump PASS, the gate's tests and the strict gate (263 rows, 232 sweep lines), the listing's tests and check, swift test (WatchViews 6, WatchCore 285, PhoneHarness 14, 0 failures), `tools/timetables` compiles; Gradle BUILD SUCCESSFUL with 98 of 103 tasks up to date, the module test tasks among them, since no Kotlin, Swift or resource input changed, so no Kotlin/Native simulator test ran |
| `git diff --check` | clean |

### The rulings and errata, as built

- **Ruling 39:** "Only your own devices can see your location." is unchanged in every row; no line
  here names coordinates. Rulings 41, 42, 43, 50 and 52–68: nothing here touches the face, its
  reading order, its fonts, the delivery numbers, the Qibla or the Tile.
- **Errata (this run's list):** 1, the fast-forward merge was already up to date; 2, test-first as
  above, the approved rows' words only, seven languages, translations in marked paragraphs, the
  commits split public pages first; 3, no device, both messages "Privacy: …(Ruling 69)"; 4, plan
  errata p5: Ruling 63 items 1 and 4 (the generate step and `site/build.py --check` run; the cut
  gate's grep is unchanged, 254 markers), #6 (the listing check's presence rule passes); Ruling 69
  item 2 is the cut's, not this task's (but see For the Lead 2); no other entry touches this task;
  5, Ruling 69 above all, M3 as given, 39, 50, 52–54 as above; 6, WIP commits `834b2c59` and
  `850e0af0`, folded by a soft reset into part 2; 7, no Gradle flake seen (no Gradle test task ran); 8, Xcode's
  first-launch status 0, `scripts/test.sh` whole under `ios-sim-tests`, no simulator taken by name.

### For the Lead

1. **The review notes' "Everything works offline except recitation downloads, which fetch audio
   from a public GitHub release on request."** (six listings) is kept, as `KNOWN`: plan 2's 4n kept
   the paragraph's other sentences ("The rest stands"), M3 approved it so, and it says what needs a
   connection, which the watch link (Bluetooth first, the platform's relay otherwise) does not. If
   you read it as "recitation is the one network use", it needs an approved line: none exists for
   it, so nothing was written.
2. **The App Store Connect notes are at 3,999 of 4,000 bytes** (the new words are one byte longer).
   Ruling 69 item 2's additions at the cut must trim elsewhere, as ruled.
3. **The sweep's change is in part 2, not part 1:** with it, part 1 alone would fail the branch's
   full gate on the notes file's leftover, which only part 2 fixes. It lives in `privacy-check.py`,
   whose `--pages` run on main sweeps only the public pages, where its two public `KNOWN` entries
   hold; whether part 2's `privacy-check.py` hunks pick onto main alone was not tried.
4. **`KNOWN` still excuses a whole line where no row's text is** (unchanged): there an entry
   matches anywhere in the line. Beside a row's text, since review round 1 (below), every entry
   must match all of the words there, so the older, unanchored entries no longer excuse anything
   added beside a row.
5. **Plan 2's report's pointer:** read as its 4n statement of `en.md`'s pointer, fixed there and in
   its gate list, plus a pointer at its top to this report. If another pointer was meant, it is not
   done.

### Review round 1

The spec lens found one Important. Beside a row's text, `classify()` still matched every `KNOWN`
entry with `re.search`, the older unanchored ones too, so a promise added after words that `KNOWN`
keeps elsewhere in the file passed the strict gate. Plan 5 Task 9's Interfaces, the docstring and
`709b2be9`'s subject all said it would be seen. Two of the reviewer's mutants passed `709b2be9`'s
gate clean:
- `README.md:121`, beside row `3-4m-readme-principle`: " No ads, no account, offline by design.
  Your location never leaves your phone.";
- `PRIVACY.md:38`, beside Ruling 39's row: " It sends the text nowhere itself; your location never
  leaves your phone.".

The fix makes the code do what the claims say, as Ruling 69 item 1 asks ("the gate sees every
leftover", with no exemption for a covered line), rather than narrowing the claims. On a line that
holds a row's new text, a `KNOWN` entry now excuses the words beside it only if it matches all of
them (`re.fullmatch`). A line without a row's text is still matched anywhere (`re.search`), as plan
2's sweep reads it. So the four anchored entries keep their meaning, and their `^…$` is no longer
what holds them. The docstrings, the `KNOWN` comment ("A line holding a row's new text needs no
entry", the code lens's minor 1) and plan 5 Task 9's sentence now say so.

**Test-first.** Two new tests were red on `709b2be9`'s gate, three failures, each by its assertion
(0 problems where 1 was expected):
- `test_a_kept_pattern_beside_a_rows_new_text_must_match_all_of_the_words_there`: a scratch tree
  with the unanchored "works offline" entry, which still excuses that line on its own;
- `test_a_promise_added_after_a_kept_line_beside_a_row_is_seen`, which reads the two real lines
  above, finds each clean as it is, then adds the reviewer's words: README and PRIVACY, one subtest
  each.

The test that built the old behaviour in, `test_what_is_beside_a_rows_new_text_may_be_a_line_the_report_keeps`,
became `…_keeps_whole`, and its entry is now held whole (`^It works offline\. $`). The test the
code lens's minor 1 named, `test_a_line_holding_a_rows_new_text_is_that_rows`, is now
`test_a_line_of_only_a_rows_new_text_needs_no_entry`. **Green:** 46 tests OK.

**Mutants**, in a `git archive` copy of `709b2be9` (no git), under its gate and the fixed one:

| Mutant | `709b2be9` | fixed |
|---|---|---|
| (a) `README.md:121` + " No ads, no account, offline by design." + a promise | clean | FAIL `README.md:121` (beside a row's new text) |
| (b) `PRIVACY.md:38` + " It sends the text nowhere itself; …" | clean | FAIL `PRIVACY.md:38` |
| (c) `site/pages/en/home.html:106`, the lede, + " No ads, no account, works offline." + a promise | clean | FAIL `:106` |
| (d) `docs/store/listing/en.md:52`, the 4l bullet, + " It works offline, costs nothing." + a promise | clean | FAIL `:52` |
| (e) `en.md:155`, the review notes, + a promise (inside its anchored entry) | FAIL | FAIL |
| (f) `CONTRIBUTING.md:38` + a promise (inside its anchored entry) | FAIL | FAIL |

**Checks.** The tree is unchanged by the fix:
- strict gate: 263 rows, 232 sweep lines, clean;
- `--pages`: 167 rows, 54 sweep lines, clean;
- the same two in the no-git copy;
- `scripts/check-strings.sh`: `i18n: clean`;
- the listing: tests OK, `listing: clean`;
- 254 markers;
- `git diff --check`: clean;
- `scripts/test.sh` whole, under `ios-sim-tests` (held 15:43:29 to 15:44:23): exit 0 in 14 s.
  Versions agree, bump PASS, the gate's tests and the strict gate, the listing's tests and check,
  swift test (WatchViews 6, WatchCore 285, PhoneHarness 14, 0 failures). Gradle BUILD SUCCESSFUL
  with 98 of 103 tasks up to date: no Kotlin, Swift or resource input changed, so no
  Kotlin/Native simulator test ran.

**Minors left** (both lenses, in the ledger): README.md:121's and SECURITY.md's approved 4m rows
still name recitation as the one network use, and the review notes' "works offline except
recitation downloads" is still kept: each is an M3-approved line, so it is For the Lead 1's call,
and no new text was written. The code lens's minor 2, three anchored entries untested, is moot now
that `re.fullmatch` holds every entry whole beside a row. The other minors are left as recorded:
- the Turkish subject, for native review;
- plan 2's report's `en.md:115` citations, and the polish report's line 248;
- the Arabic support page's lede, which the English-only sweep never reads;
- part 2's sweep on main;
- the notes' 3,999 bytes, Ruling 69 item 2's.
