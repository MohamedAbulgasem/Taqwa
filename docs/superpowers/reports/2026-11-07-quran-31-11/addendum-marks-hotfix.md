# Addendum, 7 November 2026 (evening): the marks hotfix

1.5.1 (42) was pushed and tagged on `main` feac1414 but never uploaded. Before it goes to the stores, Mohamed asked
that every confirmed drawing error go into the same release. The local branch `release-1.5.1-marks`, on feac1414 and
not pushed, does that; the version stays 1.5.1 (42). Much of the report's list of
[other differences from the print](../2026-11-07-quran-31-11.md#other-differences-from-the-print) is now fixed. This
says what changed, how each item it left open was settled for 1.5.1, and what stays open.

## What is stored

`quran.db` (user_version 6) holds Tanzil's Uthmani text v1.1 byte for byte, apart from the basmala, taken off the first
ayah of the 112 surahs that carry it and drawn on its own line above the surah in Al-Fatihah's spelling. The pipeline
no longer turns U+06DF into U+0652, so decision 3's 3,988 substituted marks are gone, and `verify_tanzil_bytes` holds
the database to the download. Copy and share give Tanzil's bytes.

## What is drawn

`HafsSpelling.forDrawing` hands the KFGQPC Hafs v2.2 typeface the King Fahd Complex's encoding of the same 1405 AH
Madinah Mushaf, on the way to the screen only:

- **R1**: sounded sukun U+0652 drawn as U+06E1 (head of kha), the silent-letter sign U+06DF as U+0652 (the ring).
- **R2**: every tatweel straight before a small alef dropped (5,924; the first 1.5.1 cut dropped 1,448).
- **R6**: after a lam, ـٔ َا drawn as أٓ, the lam-alef with hamza and madda (277 words).
- **R5, then R4**: an iqlab tanwin drawn as one vowel with the small meem, as the print writes iqlab (339); every other
  tanwin staggered (6,643) unless a throat letter or ٱ is read next (1,911 stay stacked), reading on into the next
  ayah, the next basmala or 9:1.
- **44 words at their places** (`HafsSpelling.RESPELLINGS`, each with its reason): 2:72's free hamza (U+0621), 2:97's
  connecting stroke after the lam, 12:11's U+06EC, 52:37's U+06DC, the hamza below its seat in 14 words, and the
  line (U+06E4) over the 26 words of the 15 sajdahs that the Complex's text marks.
- **The sakta's seen** (added in the fix pass after the verification): where Tanzil writes the small seen U+06DC apart
  after its word (18:1, 36:52, 69:28, 75:27, 83:14, e.g. مَنْ ۜ رَاقٍ), the space before it is dropped, so the face sets
  the seen on the word's last letter, as the 1405 print and the Complex's text do. After the space the face hung it
  past the word, over the gap or the start of the next word (رَاقٍ at 75:27, رَانَ at 83:14). Search, which counts words
  in the stored text, moves its matches to the drawn words (`HafsSpelling.drawnWordPositions`).

Each of these was checked against the 1405 print's own page font, glyph by glyph: every table word, and the general
rules on about 890 words spread over all 604 pages. 43 of the 44 table words match it; 12:11 does not (below). What
is read is unchanged: the letters as read, the pause signs other than the sakta's seen, and the word division stay
as Tanzil has them, though some code points that are not marks change on screen (the hamza's seat in 14 words, R6's
alef, 2:72's hamza, 2:97's tatweel). ى is kept where the Complex writes ي, which is invisible as the app lays words
out.

## Against the report's list

| Item | Now |
|---|---|
| 2:72 and the 18 words like it | Fixed. R1 clears the blot from all 24 tokens (2:72 and 23 others, 19 distinct words); 2:72 also takes the free hamza. On screen the face draws that hamza a little raised after the raa's small alef, as the Complex's current print does; in the 1405 print it sits on the line. The reading is the same. |
| 52:37 | Fixed. U+06DC: the face draws the small seen below the saad, the fatha above, as in 1405. |
| 12:11 | The dotted circle is gone. The face now draws U+06EC, a solid dot: the Complex's current form, and the same mark the app draws for 41:44's tasheel. The 1405 hollow lozenge is in no Hafs font, and the report warned against this exact trade, so it was Mohamed's call, and he kept the solid dot (decided by Mohamed, 7 Nov 2026); the only other choice was the broken placeholder. |
| 11:41 | Unchanged. The app draws U+06EA, the code point the Complex writes too; the 1405 lozenge is in no Hafs font. |
| Iqlab, the sukun's shape, staggered tanwin, lam-alef | Fixed (R5, R1, R4, R6). |
| The 6 low-meem tokens the first cut worsened | Cured: the meem no longer touches the vowel. |
| Not on the list | Also fixed: the hamza below its seat (14 words), 2:97's lam-jeem, the sajdah line (26 words), the sakta's seen (5 places). |

What the hotfix left open, and how each item was settled for 1.5.1. Mohamed's decisions are marked "decided by
Mohamed, 7 Nov 2026"; the Lead's calls for this hotfix, "the Lead's call for 1.5.1".

- **27:25 لِلَّهِ and 84:21 لَا.** The 1405 print runs the sajdah line over them too; the Complex's text doesn't, so
  the app doesn't. 27:25 could take the Complex's own spelling of an overlined لِلَّهِ (U+06E4, as at 41:37 and 53:62)
  and would then match 1405; the face cannot draw the line over لَا in any encoding tried. Both stay without the
  line, as the Complex's current print has them (decided by Mohamed, 7 Nov 2026).
- **12:11** (the table above): the Complex's solid dot, U+06EC, because the 1405 print's hollow lozenge is in no Hafs
  font; the only other choice was the dotted circle (decided by Mohamed, 7 Nov 2026).
- **The 44-word table and the Complex's permission.** Permission to use the Complex's text is still pending
  (`tools/quran-drawn-check.py` says so). The table stays in 1.5.1 meanwhile (the Lead's call for 1.5.1). It is
  drawing only. The Complex's text file itself is not committed or bundled; the table holds only these 44 single words
  in the Complex's spelling, which the app does ship. 43 of them are written as the 1405 print writes them (on screen
  2:72's hamza is drawn a little raised, as in the Complex's current print; see the table above), and 12:11 takes the
  Complex's current form.
- **17:7** needs nothing. The face draws the print's signs from Tanzil's spelling; it is the one drawn word that
  differs from the Complex's text outside the pause-sign and yeh classes, on purpose.
- **Pause signs after a space.** The app draws Tanzil's pause signs as "word, space, sign", as before. In 4 Mushaf
  tokens the sign touches the last mark of its word by 16 to 18 % (ۘ on the silent ring at 2:212, 5:2 and 5:64, ۛ on
  the madda at 7:172), and 46 more by 5 to 15 %, mostly ۖ on the ring. All 50 are the same as on feac1414; in the
  1405 print the sign sits clear. Joining every pause sign to its word, as the Complex writes them, would move about
  3,700 signs, which is not hotfix work, so the 4 stay as they are (the Lead's call for 1.5.1). The 46 at 5 to 15 %
  are under the 15 % the overlap measure reports (below) and are left as they were too.
- **Touching rows on short phones.** At the tightest line box (1.6 x the size) on the 408 x 760 dp phone, 6 row pairs
  share a few pixels more ink than the stored text did (p44, p46, p95, p109, p346, p473; at most 7 px there, 8 px at
  p473 on the 360 x 640 dp phone at 200 %): a staggered tanwin at the end of a row meets a mark starting the next (at
  p473 rows 10 and 11 the kasratan of سُلْطَـٰنٍ touches the ۚ below it, so the two look like one sign). Nothing leaves the frame and no word changes; `MushafFitDbTest` pins the
  count. Loosening the line box where the drawn ink would meet is a layout change, not hotfix work, so the 6 pairs are
  accepted at the 1.6 x line box (the Lead's call for 1.5.1; this addendum had first left it for Mohamed).
- **The sajdah line's ends.** The face's overlined glyphs run their line past the word to meet the next overlined word
  at the face's own spacing. The Mushaf places words one by one with justified gaps, so on some lines the line breaks
  between two overlined words, or runs a little past the word (22:18 on p334 l5, 25:60 on p365 l6). Cosmetic; no ink
  collision was found. It stays (the Lead's call for 1.5.1).
- **The low meem of iqlab.** With R5 the meem no longer overprints the kasra and sits lower: in 21 tokens its tip goes
  up to 3.5 % of an em below the face's descent, exactly as the Complex's own text draws them. Cosmetic; it stays (the
  Lead's call for 1.5.1). Still worth a look on the short phone and in the Android widget (34:54's مُّرِيبٍۭ on p434
  l7: the smoke sheet checks it in the Mushaf on the S23 and the iPhone 12, not on the short phone or in the widget).
- **The iPhone ayah widget after the update.** The widget mirror's format changed (version 3, each entry gains what is
  read after it), so the old mirror is refused and the widget shows "Open Taqwa once" until Taqwa is opened. Android
  rewrites its mirror on its own. Refusing the old one is right: it stored the silent sign as U+0652, which R1 would
  draw as the head of kha. The smoke sheet checks it; the App Store notes do not mention it (the Lead's call for
  1.5.1).

## Measured

- **Oracle.** `tools/quran-drawn-check.py --kf <the Complex's hafsData_v2-0.json>` reads that text only from the
  evidence folder; it is never committed or bundled. Of 77,427 aligned words, 73,394 are byte-identical. The rest:
  - 352 pause-sign words (344, plus 8 that also differ in yeh)
  - 3,690 yeh words (3,680, the same 8, and 2 inside the joins)
  - the three places the texts divide words differently: 15:7, 27:20 and 36:22
  - 17:7
  - 214 ۞/۩ signs left out of the comparison
  - unexplained: 0

  The tool now fails on any word in a class the drawing converts.
- **Placeholder.** The font's dotted circle is drawn nowhere.
- **Overlap.** In the app's own drawing (each pause sign after a space, as drawn), 4 Mushaf tokens have a mark 15 % or
  more overprinted, all a pause sign touching its word's last mark (above); the first 1.5.1 cut had 226. With each
  pause sign joined to its word, the print-match method, none is. The Complex's own text scores 0, which calibrates
  the measure.
- **Fit.** `MushafFitDbTest`: every line of all 604 pages fits all six screen shapes, the tightest gap between two words
  0.186 em. On the short shapes 4 to 6 row pairs share up to 8 px more ink than the stored text did (above).

Not verified: no real phone has shown the new drawing yet (the smoke sheet). The verification pass saw it on an
Android emulator, at the default and the short phone's shape, and on an iOS simulator; the sakta's seen, changed after
that, only in the JDK tests and HarfBuzz. The shaping was checked with the JDK, HarfBuzz and macOS CoreText, not
Minikin or Skia. The java.awt tests have not run on Linux CI.

## Left for the next code change

The hotfix changes no code after db5fc468, so three comments that predate the owner's decisions wait for the next
code change:

- `widgetcore/src/commonMain/kotlin/world/taqwa/app/widget/HafsSpelling.kt:325` says 27:25 "waits on the owner". It
  is settled: 27:25 and 84:21 are left without the line, as the Complex's current print has them (decided by Mohamed,
  7 Nov 2026).
- `shared/src/androidHostTest/kotlin/world/taqwa/app/quran/HafsMarkPlacementDbTest.kt:172-173` says the font draws
  12:11's and 52:37's signs "as the 1405 print has them". True at 52:37; at 12:11 it draws the Complex's current solid
  dot (decided by Mohamed, 7 Nov 2026).
- `tools/quran-drawn-check.py:30` lists 12:11 among the "single words the 1405 print decided", and `:237` prints
  "single words the print decides". 12:11's drawn form is the owner's decision, not the print's.

## For the draft reply to the reader

Its second paragraph promises only the alef. If it goes after this build, the last sentence could read:
«وقد راجعنا مع ذلك سائر علامات المصحف مقارنةً بمصحف المدينة المطبوع، وأصلحنا أكثر ما وجدناه من فروق في رسمها على الشاشة، وسيصل ذلك كله في التحديث نفسه إن شاء الله.»
