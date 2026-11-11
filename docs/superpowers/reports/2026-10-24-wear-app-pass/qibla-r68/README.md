# Ruling 68 item 4: the Qibla rows shot again, and every caption clear of the turning letters

Lane P, on `watch-feed`, 6 November 2026. Base `295c310a` (`git merge --ff-only watch-companion`:
already up to date; no WIP from an earlier run). This is no plan task: it is Ruling 68 item 4 in the
ledger, with the items the Lead's notes of 31 October add to it (restore.sh's guard, R67's two test
gaps, the R67 report's opening and lens credit). Built test-first; nothing is pushed; Taqwa identity;
no trailer.

## What changed

| File | Change |
|---|---|
| `wearApp/.../qibla/QiblaFit.kt` | `captionRoom`: the widest box, from a line's highest to its lowest ink, that keeps `LETTER_AIR` from every turning letter at every heading, by Ruling 66 item 3's exact geometry; `letterGrow`. |
| `wearApp/.../qibla/QiblaModel.kt` | `QiblaType.Turning`, `HUB_AIR`; `captionBox(…, turning)`: over a turning dial one box at every font size (`turningBox`), one line while it fits at its floor, else two on the half anchor, no larger than keeps the first line `HUB_AIR` from the hub; `CaptionBox.size`, since review round 1 the size TextAutoSize draws two lines at (`drawnSize`, `CAPTION_STEP`). |
| `wearApp/.../qibla/QiblaPage.kt` | The caption's box takes the dial's letters and the caption's measured glyph ink; `TextAutoSize` goes up to the box's size (a thousandth of a unit over since review round 1, `SIZE_SLACK`), and a debug build logs each caption's size drawn and its baselines. |
| `shared/.../watchfeed/WatchWords.kt` | Indonesian `holdLevel` "Pegang jam mendatar" (was "Jaga pergelangan tetap datar"), a draft to native review. |
| `wearApp/src/test/.../QiblaCaptionTest.kt` (new) | T9, below. |
| `QiblaFitTest.kt`, `QiblaModelTest.kt` | T6's box before the first reading; the caption box's unit tests for the turning box; R67's two gaps (T7); T6's Indonesian hub test folded into T9. |
| `HostInk.kt`, `tools/watchface/qibla-ink.py`, `fallback-ink.json` | Each system font's cmap's sticky marks in the model; a sticky mark the font draws but the model lacks is an error; the raw invisible characters written as escapes. |
| `drivers/restore.sh`, `drivers/test-restore.sh` (new) | The guard: nothing touched unless `found/` is complete; since review round 1, the paired watch put back as found (stamped starts, the face read as found, every file by its sha1) and a stub watch that holds it. |
| `drivers/captions.sh`, `langfeeds.py`, `airpx.py` (new), `large.sh` | The seven languages' captions on the watch; the air on the drawn pixels; `large.sh … qibla`; since review round 1, true 345 for 30 and the caption lines in the log (`captions.sh`), the amber caption measured (`airpx.py`). |
| plan 3 Task 7, the P3.9 report, the R67 report | The as-built note; pointers; R67's corrections. |

## The captions over a turning dial (Ruling 62 item 5, Ruling 68 item 4)

**Red first, on the base.** `QiblaCaptionTest.t9EveryCaptionOverATurningDialKeepsItsAirFromEveryLetterAtEveryHeading`
judged every caption line the page draws over a dial that turns (the bearing at the dimmed sweep's
widest bearings, 23 and 359; "Facing qibla"; the tilt notice), in ten forms of the seven languages,
both system-font variants, fonts 0.85 to 2.0 in hundredths, measured as the host measures and 3 %
wider: 4,332 lines came within `LETTER_AIR` of a letter, the first English "Hold your wrist level"
at 13.5 on one line, −11.63 at 1.0 (the "E" against "level" of P3.9's `qibla-minors/`), the least
Bengali's notice at 1.3, −15.02. "Facing qibla" met letters in Turkish (−9.15 at 1.0) and Indonesian
(−3.08), and French at 1.3 kept only 0.75. `t9ACaptionNeverShrinksAsTheFontGrowsAndKeepsItsLines`
was red too (the box followed the screen's chord as the font grew).

**The fix, by the half anchor's rule** (Ruling 66 item 2), never by clearing the dial:

- **One box at every font size**, the letters' room at the caption's last baseline for its glyphs at
  their largest there, against the letters as drawn, at the drawing's font size and at 1.3 (the
  narrower), never wider than the dial's marks' or the screen's chord. A letter's box L meets the
  line's box wherever its middle lies in G − L, whose farthest corner is √((w/2 + a)² + Y²) out, so
  w/2 = √((72.05 − 1)² − Y²) − a: about 95 units for the Latin scripts, 86 to 92 for Bengali, Urdu
  and Arabic.
- **One line while the words fit it at the line's floor; else two**, the last on the half anchor
  and the first a pitch above it, growing upward, so the detail keeps its place.
- **Two lines no larger than keeps the first line's ink 2 units from the needle's hub** with the
  caption at its highest (its half anchor at 1.2): English 17 at 1.3, not 18.
- **The words, for Indonesian alone:** "Jaga pergelangan tetap datar" fitted no two lines at 11
  ("pergelangan" after "Jaga" is 95.7 units at 11, against a room of 95.4, and so took three lines
  and was cut); the draft "Pegang jam mendatar" is the phone's approved "Pegang ponsel mendatar"
  with the watch for the phone, to native review.

T9's table (the host's measure, compact fonts), at the drawing's font size and at 1.3:

| Caption | 1.0 | 1.3 | Box |
|---|---|---|---|
| en "Hold your wrist level" | 15, "Hold your / wrist level" | 17, two lines | 94.5 |
| en "Facing qibla" | 13, one line | 15.5 | 95.1 |
| fr "Gardez le poignet à plat" | 13.5, "Gardez le / poignet à plat" | 13.5 | 94.6 |
| fr "Face à la qibla" | 13, one line | 14 | 96.2 |
| tr "Bileğinizi düz tutun" | 15, "Bileğinizi / düz tutun" | 17 | 93.5 |
| tr "Kıbleye dönüksünüz" | 13, "Kıbleye / dönüksünüz" | 15.5 | 95.1 |
| id "Pegang jam mendatar" | 15, "Pegang jam / mendatar" | 16.5 | 94.7 |
| id "Menghadap kiblat" | 13, "Menghadap / kiblat" | 15.5 | 95.8 |
| ar "أبقِ معصمك مستويًا" | 12, one line | 12 | 89.8 |
| ar "أنت باتجاه القبلة" | 13, one line | 15.5 | 94.3 |
| ur "اپنی کلائی سیدھی رکھیں" | 15, "اپنی کلائی / سیدھی رکھیں" | 16 | 91.1 |
| ur "آپ قبلہ رخ ہیں" | 13, one line | 15.5 | 91.7 |
| bn "কব্জি সমান করে রাখুন" | 15, "কব্জি সমান / করে রাখুন" | 15.5 | 85.7 |
| bn "কিবলামুখী" | 13, one line | 15.5 | 87.1 |
| the bearing "23°" | 15, one line | 18 | 94 to 102 |

**The tests** (`QiblaCaptionTest`, T9), each seen red: the air (red on the base, above); never cut
(red on the fix with Indonesian's old notice: 232 rows, three lines at 11; and on the mutant "always
one line", 3,248); clear of the hub by 2 units, every caption and line (red on the mutant with no
size cap for two lines, 1,400 rows, English "Hold your" at 17.5 reaching 1.91); never shrinking nor
changing its lines or box as the font grows (red on the base and on the mutant that takes the room
at the font size alone, 8,588 steps); and the room is the widest box the judge allows (a hundredth
narrower keeps the air, a twentieth wider does not, in every form and variant at growths 1, 1.15
and 1.3; red by not compiling first). A mutant without the first line's own room survived: the first
line, between the hub and the last, is nearer the middle at both its ends, so its room is always the
wider, and that code went. Least air 3.15 units (Urdu's notice in the elegant Naskh at 1.1), least
room to the hub 2.33 (Turkish "Bileğinizi" at 1.2); since review round 1, which set two lines from
the size drawn, the least air is 2.50 (French's notice measured 3 % wider at 1.0, its last line now on
the anchor) and the hub's unchanged. `QiblaFitTest.t6SearchingBeforeTheFirstReading…`
now holds the caption's box equal before the first reading and with one; T6's caption-over-detail
room in all seven languages is as Ruling 67 left it (least 0.59, Arabic's notice in the elegant
Naskh over "100 كم").

## R67's test gaps and report

- **A right-to-left run's parts in drawing order** (`QiblaFitTest.t7ARightToLeftRunIsSetFromTheLeftInDrawingOrder`):
  "ش كم" and "كم ١٠٠" (no-break spaces) glyph by glyph where their parts put them, both variants
  and weights. Red on the mutant that drops `Modelled.shape`'s reversal ("ش كم, ui 600: … 1.01
  where its parts put 0.572"), which every earlier test let through.
- **A sticky mark the font draws but the model lacks is an error**
  (`t7AStickyMarkTheFontDrawsButTheModelLacksIsAnError`): `qibla-ink.py` writes each font's cmap's
  sticky marks ("sticky": the Naskh fonts `! , . : U+00A0 U+2010 U+2011`, Bengali's `! , - . : ; ?
  U+00A0 U+2010`), and HostInk throws, naming the tool, where Manrope's mark stood in silently ("كم."
  and "১০০:" red first); "كم?" is Manrope's '?', which the Naskh fonts lack, as the watch draws it.
  `qibla-ink.py --check` clean. `HostInk.kt`'s raw U+202F, U+00A0, U+2010 and U+2011 are escapes.
- **The R67 report** (`../../2026-10-31-ruling-67-tests-and-docs.md`): its opening names the four
  commits and the one `iosApp/` test file; round 2's Important is credited to the code lens; its
  unrecorded round-2 minors are written down.

## restore.sh's guard

`drivers/restore.sh` now stops before the watch is touched unless `found/` holds the record, the app,
and each folder's list with every file it names (the Lead's note of 31 October: an unset
`PASS_WORK` gave an empty `found/`, from which it deleted the app's `feed.json` and `files/`).
`drivers/test-restore.sh`, every adb call to a stub (no device reached): red first, eight
incomplete `found/`s each made 52 to 55 calls; now each stops with "found/ is not complete … the
watch was not touched", and a complete one goes on to the stub.

## Seen on emulator-5558

Under `wear-5558`, held 02:05:48 to 03:25 after waiting from 01:32 for Lane W's P5.8 review round
(logged). The emulator was running, left by Lane W, with `phone-5562` paired beside it, which this
turn never took or touched. `drivers/pullfound.sh` recorded the state found (app `109521c0`, 100041;
Push face 41 `f13548e6` active, Arch, Amber, slots empty; the sample feed `7e35da12`, no delivery;
font 1.0, TalkBack off, en-US, automatic time, one alarm, no Tile), then the debug build of
`watch-feed` at `1d6be29f` (`7a27fe53`, its assets the four sample feeds and the bundled face
alone, Ruling 55 item 6) went on by `adb -s emulator-5558 install -r`. Everything shot is here:

| Path | What |
|---|---|
| `watch-en/<feed>/`, `watch-ar/<feed>/` | P3.9's Qibla rows again (`matrix.sh <en\|ar> <feed> qibla`): Searching at true 340, Aligned at true 23, Low (600 µT) and BestEffort, for en, en12, ar-ly12 and ar-sa, the watch in English and in Arabic (`SetLocale ar-EG`, then en-US and `system_locales` deleted); `words.txt` |
| `compare.tsv` | Searching and Aligned against P3.9's drawings (`ref/`), the words masked |
| `large-text/<1.0\|largest\|1.3>/<feed>/` | Ruling 66 item 9's large-text rows: the four states at 1.0, 1.24 (the watch's Largest) and 1.3 with the longest city (`large.sh … qibla`); `sheet.png` |
| `captions/<1.0\|1.3>/` | Ruling 68 item 4, shot again in review round 1 with the fix's build: the seven languages tilted at true 45, 60, 135, 225, 315 and 345 (letters at the caption's ends and between; true 30, shot first, lies inside the facing window of the Qibla's 23 and drew "Facing qibla", so 345 took its place), upright at 340 and facing at 23 (`captions.sh`); `qibla-log.txt`, the layout log; `sheet.png` |
| `captions/1.24/` | Review round 1: French and Indonesian tilted at 1.24, the watch's Largest (`captions.sh tilted 1.24 fr id`) |
| `air/<1.0\|1.3>/`, `air/air.txt` | The detail's air on the drawn pixels (errata p3 #18): sweeps a degree apart where a letter comes nearest, with the layout log; `airpx.py`'s measures of every caption shot, by folder |

- **The rows match the drawings as P3.9's did:** Searching 0.26 to 0.32 % of pixels apart (P3.9
  0.27 to 0.30), Aligned 1.07 to 1.15 % (1.06 to 1.09), the needle's and the ticks' edges at the
  fused heading; the English and the Arabic watch draw the same (at most 218 pixels apart, the
  needle's edge, as P3.9 found). Every row shows the feed's 23° and "6,600 km" (or ٦٬٦٠٠ كم).
- **The captions keep clear of the letters, in all seven languages, as T9's table says:** English,
  French, Turkish, Urdu and Bengali draw the notice on two lines (Indonesian's new draft too),
  Arabic on one at 12; Turkish and Indonesian "Facing qibla" on two. On the drawn pixels
  (`airpx.py`: the letters found by the major ticks, each line's least distance to a letter's ink,
  the anti-aliased edge counted) the least air from a caption line to a letter, over every caption
  shot, is 3.50 units at 1.0, 3.30 at 1.3 and 3.62 at 1.24 (French "poignet à plat", tilted, true
  135; review round 1's shots); "Facing qibla", in the accent, which `airpx.py` first left out with
  the needle, keeps 19.93 at 1.0 and 15.42 at 1.3 (French); no letter touched a caption in any shot.
  The least room from a caption to the hub is 2.44 units (Turkish "Bileğinizi" at 1.3; T9's 2.33).
  (The first shots' "5.30 at 1.3" was the detail's air, the amber caption left out.) The layout log
  gives each caption's size drawn and both baselines: every caption's last baseline lies on the half
  anchor to the pixel, one line on it and two at 141.04 to 141.30 at 1.0 and 138.24 to 138.57 at 1.3
  and 1.24 (Android sets the pitch
  in whole pixels, rounded up: 16 units at 15 are 36.3 pixels, drawn 37), the first a pitch above
  it at the size drawn: 125 at 1.0 for 15 and 13, 126.07 for French at 14; at 1.3 120.07 (English
  at 17), 119.12 (Turkish "Kıbleye" at 15.5), 121.67 (Bengali at 15.5), 121.13 (Indonesian at 16)
  and 123.27 (French at 14).
- **The detail is unchanged by this work**, as the layout log gives it (size, least letter gap):
  1.0 en 10, 7.42; ar-sa 10, 7.41; tr 10, 7.27; fr 7.51; id 7.42; ur 7.77; bn 10, 5.41; 1.3 en 13,
  1.56; ar-sa 13, 1.98; tr 1.36; fr 1.66; id 1.67; ur 1.87; bn 12, 1.25: R66's device table, to
  the hundredth.
- **Errata p3 #18 (Ruling 67 item 3), the detail's air on the drawn pixels:** a sweep a degree apart
  where a letter comes nearest. English at 1.0: least 7.44 units at true 203, against the exact
  least 7.42 (T2 and the device log); Bengali in Western digits, 4,400 km at 1.3, the sweep's
  tightest case: size 11.5 (T2's), the device log's least 1.05 (T2 1.00), the pixels' least 1.93 at
  true 205; Saudi Arabic at 1.0 only four degrees apart (least 11.40, the exact 7.41 between the
  steps). The drawn pixels never come nearer than the exact geometry.
- **The fade by the measured ink (item 5's insurance):** with no short form the insurance is idle
  (T7), so a hand-made English feed put the phone's long phrase in the dial's short form
  ("Makkah · 6,600 km away", `langfeeds.py`'s `qibla-en-long`): the layout log gives size 9 and a
  least gap of −9.31 (no size keeps the air), and on the pixels the north letter is drawn at 0.72 of
  its ink at true 228, where its middle is under a unit from the detail's ink, and not at all at
  true 132, 136, 220 and 224, where its middle lies inside G − L; the other letters stay whole.
- **What voided the first run, and the drivers' fix:** with `phone-5562` running Taqwa, a hand-made
  feed put with its delivery file removed made the watch echo none on its next start, and the
  phone sent its own (London, 118°, deliveries 38 to 40), so English's first captions showed
  "118°". The drivers now keep the delivery the watch holds, so the phone's next send is stale
  (Ruling 50); the French feed then stayed. The hand-made `feeds/` lacked the short forms (made
  before R66), so Arabic fell back to English "km": they carry them now. A tilted watch is given its
  field in its tilted axes, so the tilt-compensated heading is the one asked (a level field on a
  tilted watch held the dial near one heading).
- **Put back as found:** `restore.sh` put back the app and the files, but its `DEBUG_REBIND` started
  the app, whose start handshake (a sample, no delivery, an old stamp) let the phone refill it; so
  the found files were written again, `refresh.asked` set to now for one rebind (the face redrawn
  from the sample, FAJR 4:33, with no handshake inside its 60 s), and written back byte for byte:
  `state.sh` STATE AS FOUND, the face Arch, Amber, slots empty. That took a hand, and review round 1
  found `restore.sh` alone leaves the watch otherwise (below); it does it now. The emulator was left running;
  `phone-5562`, `KB2604110095`, Dev 1's `emulator-5572` and the work iPhone 12 were not touched.

## Review round 1 (6 November)

Both lenses found one Important each; both are fixed, test-first, and seen on the watch.

**1. Two lines over a turning dial left the half anchor** (code lens). `turningBox` set the first
baseline from `box.size` while `TextAutoSize` steps from the floor by halves and may draw smaller, so
the last line stood `16 × (box.size − drawn) / units` above `QiblaFit.captionY`: on the host, French
"Gardez le / poignet à plat" drawn at 13.5 in a box of 15 (1.60 high at 1.0, 3.20 at 1.1 to 1.3), and
a largest size between two steps (Wear's 1.06 to 1.24, 13 × 1.1 = 14.3) 0.37 to 0.53. Now
`QiblaType.drawnSize` takes the size `TextAutoSize` draws there (the largest of its steps from the
floor, at or below the box's largest, whose lines fit the room in two; `StepBased`'s own rule, read
in its source: it never draws the largest size when that lies between two steps), `CaptionBox.size`
is that size and the first baseline is set from it. `QiblaPage` hands `TextAutoSize` the box's size
a thousandth of a unit over (`SIZE_SLACK`): `StepBased` adds pixels before it compares, so a box
whose largest size is a step could otherwise lose that step to a float's rounding.
- Tests, red first: `QiblaCaptionTest.t9ACaptionsLastLineKeepsTheHalfAnchor` (every caption, form,
  variant, width and font size: the last baseline on `captionY`) was red on the base, 1,001 rows,
  the farthest French's notice at 1.1, 136.40 against 139.60; red on the mutant that drops the fit
  from `drawnSize` (766 rows). `QiblaModelTest.aCaptionOfTwoLinesOverATurningDialIsSetFromTheSizeTextAutoSizeDrawsItAt`
  holds the steps tried and the y at 1.0, 1.3 and between two steps. T9's table is unchanged (the
  sizes were TextAutoSize's all along; only where the lines stand moved); least air 2.50 (French's
  notice 3 % wider, at 1.0: its last line now on the anchor, nearer the letters at the line's ends,
  where the room was always judged), least room to the hub 2.33.
- On the watch (review round 1's build, `473a3d05`): the device draws French at 14 (its fonts a
  little narrower than the host's model), in a box of 14 now, where it was a box of 15 at 1.0 and
  16.5 at 1.3; its last baseline 141.04 and 138.24, where it was about 139.97 and 135.57 (the
  shots' line heights are equal before and after: the size did not change, the lines moved down 1.07
  and 2.67 units). Indonesian's notice at 1.3 and 1.24: 16 in a box of 16 (16.5 before), 138.31.
  Shot for shot against the first shots, only those captions moved: at 1.0 French's 2,822 pixels in
  the caption's band, at 1.3 French's 4,754 and Indonesian's 2,867; every other caption at most 30
  pixels apart there (the letters' anti-aliased edges at the fused heading). The whole-pixel pitch
  (above) leaves two lines' last baseline up to 0.30 units under the anchor at 15 and 13, as before
  the fix; the layout log line exists so a device pass can read it.

**2. `restore.sh` left the paired watch otherwise while it printed STATE AS FOUND** (spec lens).
With `phone-5562` running Taqwa and the sample held (no delivery), its `DEBUG_REBIND` start shook
hands and the phone refilled the feed; the first start after its `install -r -d` read the face
inactive (`face-activation-spent` "seen 41 inactive", the face-addable capability published) and
rewrote `profileInstalled`; and `state.sh` names the files, so the diff passed with two of five
files changed. Now (`drivers/README.md`): the found app is installed only when it is not the one
there; `refresh.asked` is set to now before the install and before each stop and start, and the
found stamp is never written while a start may follow; the app is started until it reads its face
as found (20 s apart, up to eight times), each start given 20 s after its face's line; the found files
are written once more after the starts, with the app left running and settled as found; and STATE
AS FOUND needs `state.sh`'s record, every file's sha1 and no handshake or taken feed in the log
since the clock was the network's again. A face that never reads as found says NOT AS FOUND, exit 1,
and names `tools/watchface/activate.sh`.
- `drivers/test-restore.sh`: a stub watch whose app does at each start what Taqwa's does on a paired
  watch (a handshake unless the stamp is under a minute old, and the phone's feed then; the face
  read inactive for its first n starts; the profile installer's mark): the found app over a turn's
  build, a face the system has not settled (4 starts), the found app already there (not installed
  again), a file kept wrong that `state.sh` only names (NOT AS FOUND, the file named) and a face
  that never settles (NOT AS FOUND). Six mutants each red: no stamp before the starts (3 cases,
  and 3 again once `start` stamped before its stop), none before the install (2), no last write (3),
  names only (1), one start (2), always install (1).
- On the watch (`wear-5558`, 10:42 to 11:19): run 1 put the watch back (two starts, the first read
  the face inactive, the second active and removed the capability; both handshakes skipped) but said
  NOT AS FOUND: its log was read from a time on the mock day, before `clock.sh auto`, and so
  counted the handshake my own install of the fix's build had made at 10:43 (its start, with the
  found stamp 9 h old: the phone sent one feed, which the turn's feeds replaced). The log is read
  from after the clock is back now. Run 2, after the fix's build was installed again as a turn
  would: two starts, every handshake skipped, the face inactive then active, the capability removed,
  every file byte for byte as found (checked by hand too), the app running, STATE AS FOUND.

**The minors.** Fixed: `airpx.py` left the amber "Facing qibla" out with the needle (an amber part
is the needle only when it reaches the hub now; the facing caption is measured above); the
`tilted-t030` shots were Aligned (true 345 in their place); the room at 1.3 was unpinned (a box
judged at 1.0 alone passed every Qibla test: `QiblaModelTest.aTurningDialsCaptionBoxIsTheNarrowerOfItsRoomAtTheDrawingsFontSizeAndAtTheLettersLargest`
kills it). Left, in the ledger: the hand-made feeds carry no turn phrases; `site/pages/id/support.html`
still says "Jaga pergelangan tetap datar" (native review); Arabic's notice at 12 and French's at 14
never grow with the font (for Mohamed); Watch Face Push answers "not active" for about a minute
after the app is reinstalled at the same face code, so that start publishes the face-addable
capability (Lane W's `FacePush`, for the Lead; `restore.sh` waits it out); a dimmed dial's title is
still set from its largest size (up to 0.43 units high at Wear's 1.06, 1.12 and 1.18, since plan 3
Task 7); the whole-pixel pitch (above); `restore.sh` leaves `no_backup/` and `cache/`, the app's
per-install copy of its face (`FaceStage`), as the found build's next start writes them; and the code
lens's ten minors are not in the ledger's line, so they are read from its run's journal.

## Checks

- `scripts/test.sh` whole, under `ios-sim-tests` (01:56 to 02:04), on WIP `0b0b8a2e` with the docs:
  exit 0 in 7m 45s. Versions agree (1.5.0 (41), the Galaxy app 100041; not bumped: the watch ships
  as 1.6.0 (42)); `test-bump-version.sh`, `test_privacy_check.py` and `test_store_listing_check.py`
  PASS; privacy-check 263 rows (all approved), 236 sweep lines, clean; listing clean; `swift test`
  (WatchCore 285 and the rest) 0 failures; the Kotlin/Native simulator suites shared 2,377,
  watchcore 200, widgetcore 109; host shared 2,401 (run: `WatchWords.kt` changed), watchcore 233
  and widgetcore 111 (from the cache, unchanged); wearApp 571; 0 failures. The Gradle flake
  (EOFException, `EngineBudgetBenchmarkTest`) was not met.
- On the final tree: `./gradlew :wearApp:testDebugUnitTest --rerun` 571/0 (`QiblaCaptionTest` 5,
  `QiblaFitTest` 21, `QiblaModelTest` 25) and `:wearApp:lintDebug` "No issues found".
- `scripts/ios-build.sh` (the shared change): BUILD SUCCEEDED; `check-watch-bundle` every rule holds
  (TaqwaWatchWidgets 4,412 / 5,120 KB, TaqwaWatch 12,012 / 15,360 KB; 4b and 8 skipped, ad hoc and Debug).
- `python3 -I tools/watchface/qibla-ink.py --check`: the model is the fonts' (0.0000 em);
  `tools/i18n-check.py` clean; `git diff --check` clean; `drivers/test-restore.sh` PASS; every driver
  `bash -n` and `py_compile` clean; `tools/face-*.py --check` all five up to date (no face file
  changed, so the validators and the always-on share are as they were).

- **Review round 1's fixes, on the final tree:** `./gradlew :wearApp:testDebugUnitTest --rerun`
  574/0 (`QiblaCaptionTest` 6, `QiblaFitTest` 21, `QiblaModelTest` 27) and `:wearApp:lintDebug` "No
  issues found"; `scripts/test.sh` whole exit 0 under `ios-sim-tests` (versions agree, bump, privacy
  263 approved and clean, listing clean, swift green; shared, watchcore and widgetcore unchanged by
  round 1, so their suites up to date from the base's runs; wearApp's the run above);
  `drivers/test-restore.sh` PASS (eight guards and five put-backs on the stub watch);
  `qibla-ink.py --check`, `i18n-check.py` and `git diff --check` clean; every driver parses
  (`bash -n`, `ast`). No face, vector or shared file changed in round 1, so the face tools, the
  validators and `ios-build.sh` were not run again.

## The binding rulings and the errata

| Item | How it was met |
|---|---|
| Erratum 1 (`git merge --ff-only watch-companion`) | Already up to date at `295c310a`; no WIP from an earlier run |
| Erratum 2 (Ruling 68 item 4 exactly; the half anchor's rule or the words; `wear-5558`; the report beside P3.9's) | The captions over a turning dial keep the letters' room on the half anchor (two lines upward), Indonesian's notice is a shorter draft, the dial is never cleared; the device turn under `wear-5558`; this folder sits in P3.9's |
| Erratum 3 (errata p3) | #3: the rows are set beside P3.9's drawings with the words masked; #12: a heading that is not a number keeps the Searching caption, and T6 now holds its caption box equal to Searching's; #14 and #15 are Ruling 66's (#17): the detail keeps inside the ticks on every shot (the log's "ticks" 15.2 units or more), one line, never cut; #17: Ruling 66 item 9's rows shot again (above); #18: the detail's air on the drawn pixels and the fade by the measured ink (above) |
| Erratum 4 (Rulings 39, 41-43, 50, 52-70; the style; the face's tools; Ruling 55 item 6) | The watch draws only the feed's city, whole-degree bearing and distance, asks for no location, and the promise "Only your own devices can see your location." is untouched; no face file changed (`face-*.py --check` up to date); the delivery number is read, never changed, by the app (the drivers keep the one the watch holds, Ruling 50); the debug APK's assets are the four samples and the bundled face, and the hand-made feeds live in the report, never in an APK |
| Erratum 5 (the version) | Not bumped: 1.5.0 (41) on the branch; the watch ships as 1.6.0 (42) |
| Erratum 6 (the budget) | WIPs at each green step: `5afcfc2e`, `0d8baea6`, `0b0b8a2e`, `1d6be29f`, `c3604166`, `89095c66`, folded into the one commit |
| Erratum 7 (the devices) | `emulator-5558` was running (Lane W's); used under its lock, left running and as found; `emulator-5562` was running and paired, never taken or touched (its feed reached the watch, above); nothing installed on `KB2604110095`; no release key; the work iPhone 12 not used |
| Erratum 8 (the Gradle flake) | Not met |

## For the Lead

1. **For Mohamed's eye** (`captions/1.0/sheet.png`, `captions/1.3/sheet.png`): the tilt notice is now
   two lines in five languages ("Hold your / wrist level"), and "Facing qibla" two in Turkish and
   Indonesian; Arabic's notice stays one line at 12 (its words fit the room there, and one line
   comes first, as the notice's rule always was).
2. **For native review (errata p5):** Indonesian `holdLevel` "Pegang jam mendatar" replaces "Jaga
   pergelangan tetap datar", which no layout clear of the letters could hold on two lines; a
   reviewer's form ships only if `QiblaCaptionTest` passes on it (it is a draft in `WatchWords.kt`,
   whose marker names it).
3. **A paired phone refills a watch that holds the sample.** With `phone-5562` running Taqwa, the
   watch's next app start shakes hands and takes the phone's feed (Ruling 50's heal), so "the
   sample feed" is a found state only until then; the drivers now keep the held delivery for
   hand-made feeds, and this turn put the found state back without a handshake (above).
4. **`airpx.py` is a measure of pixels**, the anti-aliased edge over a threshold, a whole-pixel
   grain (0.44 units) and the letters found by the major ticks: it never came below the exact
   geometry, and it is evidence beside the host's judge, not a test.
