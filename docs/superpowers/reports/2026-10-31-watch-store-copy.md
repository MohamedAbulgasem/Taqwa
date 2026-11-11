# The README, the site, the store copy and the release notes (plan 5 Task 10)

Lane P, branch `watch-feed`, base `5761178a` (`git merge --ff-only watch-companion`: already up to
date). One commit, "Docs: Taqwa on your wrist in the README, the site, the store copy and the release
notes, in seven languages"; the WIP commits `46211b45`, `f852a33f` and `75c05c4b` are folded into it.
Nothing pushed. M3 is not given: every line that waits for it still waits, and every non-English line
is a draft to native review (Task 11).

## Where it stands

- `python3 tools/store-listing-check.py`: clean. Its tests (`tools/test_store_listing_check.py`, 40
  since review round 1) pass, and `scripts/test.sh` now runs both.
- `python3 tools/privacy-check.py --allow-awaiting`: clean but for the 194 rows awaiting M3. Without
  the flag it fails on those 194 rows alone (186 before this task; the 4l rows are now one per store,
  so 7 more, and review round 1 added the README's watch line, 1 more).
- `python3 site/build.py --check`: PASS, after `CI=true ./gradlew -p tools/timetables generate`
  (55 cities, 2 held, 129 pages; 164 pages built).
- `scripts/test.sh` whole: exit 0.

## What changed

**The listing check** (`tools/store-listing-check.py`), test-first:
- **Each store names only its own watch.** No field under `## App Store` names Android, Wear OS,
  Galaxy, Google Play, the Play Store, or Google (the erratum's extension: R63a's forward note). No
  field under `## Google Play` names the Apple Watch, watchOS, the Digital Crown, or Apple (my mirror of
  that extension, so the relay is Google's alone on Play, as the P5.9 report's finding 3 asks). Names
  stay in Latin script in every language, and a name ends only at a letter that is not Latin (review
  round 1): Arabic and Urdu write a conjunction or preposition onto a Latin name ("وGalaxy Watch",
  "بـGoogle"), Bengali an ending ("Apple Watchএ"), and `\w` counts those letters as word characters.
  "Googleplex" is still no name. Notes in parentheses, the shared review notes and the screenshot
  captions are not held to it.
- **A field holding only notes fails as empty**, which is how every App Store description was red.
- **The App Store Connect notes** are the file en.md's review notes point to (the backticked path),
  not the newest by name (erratum 2). They are held to 4,000 UTF-8 bytes and to the App Store's names.
  A pointer to nothing, or no pointer, fails.
- **The release notes:** every file in `docs/store/release-notes/` has Play's seven tag blocks, each at
  most 500 characters (every file since 1.0.0 (30) already did). The newest phone code's two files,
  `<V>-<C>.txt` and `<V>-<100000+C>-wear.txt`, must both exist and name no Apple Watch. Erratum p5 #6
  keys the presence rule to the newest notes code, not to androidApp's code (39 today), and the rule
  still catches a bump: the newest code must be no older than androidApp's `versionCode`, with the same
  version once they are equal. So `bump-version.sh 1.4.0 41` without 41's two files fails, and so does
  40 bumped as 1.5.0.
- Red first: the tests failed on `main(root=…)` missing; then, with the check written, the real tree
  failed 15 ways: every App Store description empty, every Play description naming Apple, and code 39
  with no Wear OS notes. The real-listings test stayed red until the copy was written.
- Its self-test copies the committed store copy and writes Galaxy Watch into each language's App Store
  description, then Apple Watch into each Play description: all 14 fail by name. `scripts/test.sh` was
  seen to stop at the listing step on a scratch copy whose en.md names Galaxy Watch.

**The store copy** (`docs/store/listing/*.md`), seven languages:
- **The Play full description** gains the plan's English paragraph ("On your wrist: on Wear OS watches
  such as Galaxy Watch, …"), between the last feature section and the trust section, and the bullet
  after the location one says "Google may carry its data" (it said "Google or Apple").
- **The App Store description is its own text:** the Play one with the plan's Apple paragraph in place
  of the Wear one, and "Apple may carry its data". No line of the Play text is one the iPhone app lacks
  (widgets, lock-screen controls, read-aloud, the adhan's opening, Match my mosque and the compass are on
  both), so none is left out.
- **What's new** is 1.4.0's: the plan's Apple Watch bullet, then the rest of the build as
  watch-companion holds it today: Settings › Widgets (MM3) and About Taqwa redesigned. The cut adds
  anything merged after (for example the notifications layout B branch, if it joins 1.4.0).
- **The cut**, which supersedes P5.9's French and Indonesian one (Ruling 63 item 5), for Mohamed's
  approval. Every language's note says what was cut.
  - In every language: the website and support lines go (both stores have fields for them), and so
    does the line of interface languages (the App Store lists them itself). These write no new text.
  - English and Turkish: the Mushaf bullet in fewer words (P5.9 had done French and Indonesian).
  - French, Turkish and Indonesian, which need more room: the high-latitude rules join the methods
    bullet; the 24- or 12-hour clock joins the Jumu'ah bullet; the trust section's first bullet keeps
    only what the opening does not already say (no account, no adverts).
  - French and Indonesian also: the (i) bullet says in fewer words that it shows whose times these
    are and how they are checked, and several bullets and the sources line are shorter (each listed in
    the language's note), with every source still credited. French's wrist paragraph is in the
    language's shorter listing style; Indonesian's is shorter too. P5.9's other cuts stay.
  - Untouched in every language: the never-early bullet, the cautious-times wording, the privacy rows'
    lines (but for the relay's company) and the "not affiliated" sentence.

| | Play before | Play now | App Store now | What's new |
|---|---|---|---|---|
| en | 3,826 | 3,965 | 3,904 | 242 |
| ar | 3,157 | 3,259 | 3,215 | 222 |
| fr | 3,970 | 3,969 | 3,887 | 276 |
| tr | 3,956 | 3,970 | 3,907 | 272 |
| id | 3,995 | 3,969 | 3,884 | 260 |
| ur | 3,729 | 3,896 | 3,848 | 233 |
| bn | 3,761 | 3,904 | 3,859 | 235 |

Every Play description now has at least 30 characters to spare.

**The release notes:**
- `docs/store/release-notes/1.4.0-40.txt`, the phone's notes in Play's tag template: the plan's watch
  bullet, then the Widgets and About bullets (252 to 298 characters a language).
- `docs/store/release-notes/1.4.0-100040-wear.txt`, the Wear OS track's notes: the plan's English and
  six drafts (138 to 188 characters a language).
- Neither names the Apple Watch. A later candidate gets its own two files at its code (Task 13 Step 6),
  and the check fails until it has them.

**The App Store Connect notes** (`docs/store/app-review/2026-10-30-notes-1.4.0-40.txt`, now 3,998 of
4,000 bytes). en.md's rule says the notes change with a feature, and the P5.9 report's finding 4 says
they had 8 bytes left. So, within the budget:
- section 3 gains "- Apple Watch: its app comes with the iPhone app; complications and Today show the
  iPhone's times.", and its Settings line names the new Widgets screen;
- section 6's path is corrected: Attribution & licences moved into About Taqwa with MM3;
- to make room: "screen recording … of version 1.0.0 (32)" reads "recording … of 1.0.0 (32)"; "The Quran
  text" reads "The Quran"; section 5 drops "on the device" and repeats "it" for "Taqwa"; the closing
  "Support:" line goes, since App Store Connect has its own support field.
- Every privacy row's text in the file is unchanged, and the "predates" caveat stays.
- All of it is for M3, with the rest of the file.

**The README:**
- an "On your wrist" part right after "What it does": both watches, what each shows, install and the
  face, Wear OS 3's countdown, the phone's language, and, since review round 1, a line led by Ruling
  39's sentence word for word ("Only your own devices can see your location."): neither watch app asks
  for it, the phone never gives the watch your coordinates, and what it does give, and who may carry
  it, is the policy's "Your watch" section, by its link, not a shorter list of the README's own. The
  line is row `3-4m-readme-watch`, awaiting M3;
- Roadmap slice 5;
- the Project layout's `wearApp/` line, and `iosApp/TaqwaWatch/`, `TaqwaWatchWidgets/` and
  `WatchShared/`;
- "The watches" under Building: the face's debug key, the Wear emulator, `tools/watchface/install.sh`,
  `scripts/watch-build.sh`, `scripts/face-gate.sh`, and the owner's face key
  (`make-face-key.sh`, `release-face.sh`). Task 7's bundling in `release.sh` is not built yet, so the
  README does not claim it.
- The Attribution path is corrected to Settings › About Taqwa › Attribution & licences.

**The site:**
- every home page gains a card, "On your wrist", last in the features grid, with a watch glyph in the
  house style (amber dot). It is a `card wide` (`grid-column: 1 / -1`, its text at 68ch), so it is a
  row of its own at any column count, never a ninth card left alone on a row.
- every support page gains `<h2 id="watch">`, four short paragraphs: installing (Settings › Watch or
  the watch's Play Store; on Apple Watch with the iPhone app); adding the face (Wear OS 6) and the
  complications, with the Wear OS 3 line of the Lead's call 6 default (a), "a complication's countdown
  moves only at each prayer"; weeks away from the phone, "Open Taqwa on your phone", and the phone
  app's language; calibrating the watch's compass (figure of eight; in Arabic, Urdu and Bengali the
  numeral form each support page already uses).
- French text uses the site's narrow no-break spaces.

**`tools/privacy-check.py`:**
- the 4l rows are now one per store and language (`3-4l-listing-internet-play`, Google;
  `-app-store`, Apple), all still awaiting M3;
- two KNOWN entries for lines that quote the watch's own message ("Open Taqwa on your phone" on the
  support page; the Wear notes' "Needs Taqwa on your phone, which sends the prayer times.");
- since review round 1, row `3-4m-readme-watch` (awaiting) for the README's watch line, whose old text
  is the first draft's "Your location stays yours." so that second promise cannot come back, and two
  tests: the row leads with Ruling 39's sentence and links the policy's "Your watch", and every line
  of the README's "On your wrist" naming the location or coordinates is a row's text (the sweep's
  words did not catch the first draft).

## Seen in the browser pane

The built `_site`, served on 127.0.0.1 from this worktree only (nothing signed into, nothing submitted;
the server and the tab are closed again).
- `/support/` and `/ar/support/#watch` at 375 px: the answer is there, Arabic `dir="rtl"` with every
  paragraph right to left, the page `scrollWidth` 375, and no element past either edge.
- The same holds for all 14 home and support pages in seven languages (each in a 375 × 812 frame:
  `scrollWidth` 375, no element past either edge). Each home card is 327 px wide.
- At 1280 px the grid is four columns of 236 px and the card spans all 992 px.

## The binding rulings and the errata, as built

- **Ruling 39:** the location sentence is unchanged everywhere, and it is the only location promise.
  The README's watch line leads with it word for word and points to the policy's "Your watch" for what
  the phone gives the watch (since review round 1; the first draft coined "Your location stays yours."
  and listed only the city and the bearing). No store text says what the feed carries; each names
  only its own company's encrypted relay.
- **Rulings 41, 42, 43, 50, 52–66:** nothing here touches the face, the feed or the Qibla detail.
  Ruling 66 replaced Ruling 62 item 2 and Ruling 63 item 2. The copy describes the Qibla screen only
  as using the watch's compass.
- **Erratum 1:** the merge was already up to date.
- **Erratum 2:** done as above. Every language is cut, superseding P5.9's French and Indonesian cut.
  Each store names only its own watch, with Google added to the App Store rule. The notes hold at
  3,998 bytes. The check reads the notes file en.md points to.
- **Erratum 3:** the browser pane was used only on the locally built pages.
- **Erratum 4:** M3 is not assumed: 194 rows awaiting. Every non-English line is marked (below).
- **Erratum 5, the p5 errata:** #6 (the presence rule) is keyed to the newest notes code, plus the
  bump check. #8 (wear-sdk) and the Lead's call 6 shape only the support answer's Wear OS 3 line, which
  is default (a)'s and not waiting for Task 12. No other entry touches this task.
- **Erratum 6:** Rulings 55–66 bind code this task does not touch. Ruling 63 item 5 is this task.
- **Erratum 7:** WIP commits `46211b45`, `f852a33f` and `75c05c4b`, folded.
- **Erratum 8:** no EOFException; nothing needed a re-run.
- **Erratum 9:** `scripts/test.sh` ran whole, Kotlin/Native suites included. No simulator was named or
  touched beyond Gradle's own test runs.

## Checks

- `scripts/test.sh` whole: exit 0, BUILD SUCCESSFUL 6m 6s.
  - versions 1.3.0 (39)/(100039); test-bump PASS; test_privacy_check PASS; the privacy gate clean but
    the 193 awaiting (194 after review round 1); test_store_listing_check PASS; listing clean;
  - swift test 234/0;
  - tools/timetables compiled;
  - shared host 2,313 and iOS 2,282; widgetcore 111 and 109; watchcore 228 and 198; wearApp 497; 0
    failures.
- `scripts/check-strings.sh`: i18n clean.
- `git diff --check`: clean.
- Not run: `scripts/ios-build.sh` (no iOS, shared or Kotlin file changed; `scripts/test.sh` gained only
  a Python step).
- No device, emulator, simulator or lock was used. KB2604110095 and the work iPhone 12 were not
  touched.

## Review round 1

Both lenses: NEEDS FIXES, 0 Critical, 2 Important each, the same two. Both fixed, in two commits:
- **The platform rule missed a name with a letter of another script written onto it.**
  `names_in` bounded names with `\w`, which counts Arabic, Urdu and Bengali letters, so "وGalaxy
  Watch" in an App Store description and "وApple Watch" in a Play one passed. It now bounds them by
  Latin letters, `(?<![A-Za-z])` and `(?![A-Za-z])`. Test-first: 20 cases red on the old rule (an
  Arabic tree with each attached form on each side, `names_in` itself, and the real ar, ur and bn
  listings broken the same way), then 40/40 green. The committed copy is clean under the new rule, and
  a substring scan of every store field, in Latin and native script, finds no other platform's name.
  Commit "Store copy: the platform rule finds a name with an Arabic, Urdu or Bengali letter written
  onto it, after review".
- **The README's watch line was a second location promise that no row held.** "Your location stays
  yours." named the city and the bearing alone, less than the policy's "Your watch" lists (the
  distance, the compass correction, the month's times, the phrases and the install's random number),
  and the sweep's words did not catch it, so neither the gate nor M3 saw it. It now leads with Ruling
  39's sentence and points to the policy's section for the list:

  > **Only your own devices can see your location.** Neither watch app asks for it, and the phone
  > never gives the watch your coordinates. What it does give the watch, and who may carry it, is in
  > the privacy policy's [Your watch](PRIVACY.md#your-watch) section.

  Test-first: row `3-4m-readme-watch` (awaiting M3; its old text is the first draft, so it cannot come
  back) and two tests (the row's shape and link; every line of "On your wrist" naming the location
  or coordinates is a row's text), red on the old line, then green. The gate counts 194 awaiting.
  Commit "Docs: the README's watch line is Ruling 39's promise with the policy's list behind it, a
  row for M3, after review".

No finding contradicted a ruling. Minors left (the spec lens's; the code lens's nine are in its
review, not in this round's brief):
- the site and README name P5.5/P5.6's "Settings › Watch" and "Add the Taqwa face" before those
  strings exist (Task 11 aligns them, above);
- the Lead's call 6 follow-up and erratum p5 #8 must also cover the README's Wear OS 3 line, not only
  the support answer's;
- the App Store Connect notes say "About Taqwa › Attribution" without "Settings ›": the file is at
  3,998 of 4,000 bytes, so it waits for M3's edits to the notes;
- Roadmap slice 5 reads Done before the release: the README reaches main only with the release's
  squash, when it is true;
- the home card names the Taqwa face without "Wear OS 6" (the README and the support answer say it);
  a seven-language edit for Task 11's round.

## For Task 11 (native review)

- **Markers:** 60 new, 6 replaced, so `git grep "draft, to native review"` outside docs/superpowers
  counts 254, up from 200.
  - `site/pages/{ar,fr,tr,id,ur,bn}/home.html`: the card's title and text, 2 each;
  - `site/pages/{ar,fr,tr,id,ur,bn}/support.html`: the heading and four paragraphs, 5 each;
  - each translated listing: 3 notes (the Play description's, the App Store description's, What's new's).
- **No comment possible:** list these in Task 11's table.
  - `docs/store/release-notes/1.4.0-40.txt`: six languages, three bullets each;
  - `docs/store/release-notes/1.4.0-100040-wear.txt`: six languages.
- **Terms for the reviewers to settle** (drafted, not looked up in the watch systems' own UI):
  - the watch face (ar واجهة, fr cadran, tr kadran, id tampilan jam, ur/bn واچ فیس / ওয়াচ ফেস);
  - complications (ar مضاعفات, fr/tr/id complications / komplikasyon / komplikasi, ur/bn transliterated);
  - the Tile (ar مربّع, fr vignette (Google's French may say carte), tr kutucuk, id kartu, ur/bn ٹائل /
    টাইল);
  - the crown on Wear OS (tr taç, id tombol putar);
  - Smart Stack, left in Latin;
  - iPhone, in Latin in the new Arabic text, where the 1.3.0 notes wrote آيفون.
- **Keep in Latin:** Taqwa, Wear OS, Galaxy Watch, Apple Watch, Digital Crown, Smart Stack. Each
  listing keeps its own spelling of the app's name (تقوى, تقویٰ, তাকওয়া), as its other lines do.
- **To match strings not yet built:** the support answer and the README name the phone's Settings ›
  Watch (P5.6) and the watch's "Add the Taqwa face" (P5.5). Their translations are guesses until those
  strings exist; Task 11 aligns them.

## For the Lead and Mohamed

1. **The cut** (each listing's note), for Mohamed's approval with M3. The biggest choices:
   - the website, support and languages lines go in every language;
   - French and Indonesian lose the separate About these times clause, which the (i) leads to anyway.
2. **The App Store Connect notes' edits**, above, for M3: the Apple Watch line, the Attribution path,
   and the room made for them.
3. **What's new and the phone notes carry the Widgets and About bullets** for what MM3 brought. The
   cut must add any later change (and its translations go through Task 11).
4. **`CONTRIBUTING.md:106` still says "Settings › Attribution & licences"** (MM3 moved it into About
   Taqwa). It is not this task's file, so it is left for whoever next edits it. (Fixed by Ruling 67,
   below.)
5. **`scripts/test.sh` now runs the listing check.** A `bump-version.sh` to a new code fails the suite
   until that code's two release-notes files exist, which is the plan's presence rule doing its job.

## Ruling 67 (31 October): the left minors, and the code lens's nine

Ruling 67 item 2 took P5.10's left minors and the nine minors of review round 1's code lens, which
the round's brief did not carry, read from the run's journal (wf_b7aef63b-2f0) and recorded here and
in the ledger: each fixed where it is a line, else carried. Report:
`docs/superpowers/reports/2026-10-31-ruling-67-tests-and-docs.md`.

- **Roadmap slice 5 reads "Coming in 1.4.0"**, not Done, until the release; plan 5's M13 row turns it
  to Done in the squash.
- **The App Store Connect notes keep "About Taqwa › Attribution & licences".** "Settings › " is 13
  bytes ("›" takes three), and the file is at 3,998 of the Notes field's 4,000, so it would be 4,011;
  the room can only come from other words of the notes, which are M3's to edit (item 2 above). Carried
  to M3 with them.

The code lens's nine:

1. *The notes at 3,998 of 4,000 bytes, and line 22 without "Settings ›".* Carried, as above.
2. *The notes' Apple Watch line does not say the watch shows "Open Taqwa on your phone" until the
   iPhone app has run once with the watch near* (a 2.1 risk). Carried to M3: one clause, with no bytes
   to spend until the notes are edited.
3. *The platform rule was case-sensitive ("android", "WEAR OS", "watchos" passed), and the App Store
   list had no "Samsung".* Fixed: `names_in` matches in any case and the App Store list names
   Samsung. Test-first: `test_a_name_in_another_case_fails_too` (three cases) and
   `test_samsung_fails_in_the_app_store_fields`, four failures on the old rule, then 42/42; the
   committed copy is clean under the new rule ("Googleplex" and "Pineapple" are still no names).
4. *`check_release_notes` keeps the last of two blocks with one tag, and ignores text outside a
   block, untested.* Carried: a change to the parser with its tests, not a line; for Task 11's pass
   over the notes, or the Lead.
5. *`notes_file` takes the last backticked app-review path in en.md, so a later note citing an older
   file would change which file is checked.* Carried: en.md cites one file today, and which pointer
   counts is a rule to choose, not a line.
6. *The Play paragraph and the Wear notes (the plan's fixed English) do not say the face needs Wear OS
   6.* Carried to the Lead, since the plan fixes that English; it joins the spec lens's fifth minor.
7. *Each listing's Google Play "Release notes (500)" field still holds 1.0.0's welcome text, beside
   the per-build files.* Fixed: a note line under it in all seven listings says it is 1.0.0's record
   and to paste `docs/store/release-notes/`; the check skips notes, so every count is unchanged.
8. *`README.md`'s build steps say `./tools/watchface/install.sh` installs the face too, and plan 5 Task
   4, which turns that into `push-face.sh`, did not name the root README.* Fixed in plan 5: Task 4's
   files name the root README's build steps. The line itself changes with Task 4.
9. *`CONTRIBUTING.md:106` said "Settings › Attribution & licences".* Fixed: "Settings › About Taqwa ›
   Attribution & licences", as `README.md:237` and the app say it. It reaches main with the release's
   squash, not with Task 9's public-pages commit.
