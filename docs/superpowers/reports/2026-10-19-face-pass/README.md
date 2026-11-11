# The Taqwa face on the Wear OS 6 emulator: the full pass

Plan 1, Task 10 (`docs/superpowers/plans/2026-10-19-watch-companion-1-galaxy-face.md`). Every shot is
`emulator-5558` (the AVD `Taqwa_Wear_OS_6`, 454 x 454 round), the debug face signed with the throwaway
test key, and the watch app's debug samples: Cape Town on the mock day, Sunday 18 October 2026, the day
`faces.js` draws. `tools/watchface/pass.sh <dir>` takes them all again. The mockups they are judged
against are `tools/watchface/ref.py`'s renderings of `faces.js`; the side-by-side pictures are in
`task-10/` (mockup left, emulator right; a few carry a third panel, the pixels that differ;
always-on frames undithered).

The samples: `en` is English on 24 hours with Western digits (the debug build's own sample); `en-12h`
is the same on twelve hours; `ar-ly-12h` is Arabic as a Libyan phone writes it (Western digits) on
twelve hours; `ar-sa` is Arabic as a Saudi phone writes it (Arabic-Indic digits) on 24 hours. Amber,
unless a colour is named. The battery is set to 82%, as `faces.js` draws it (its source writes the
value as "82"); the emulator's step count is whatever it has counted.

## The pass (this folder)

| Shot | What it shows |
|---|---|
| `pass-arch-en-1458-awake.png`, `-aod.png` | Arch at 14:58, English, 24 hours: the arch lit from the crown, the lamp, the yearless Hijri date |
| `pass-arch-en-1240-awake.png`, `-aod.png` | Arch at 12:40: the small round-ended chevron at the apex, and the outline down both sides |
| `pass-arch-en-1855-awake.png`, `-aod.png` | Arch at 18:55: lit into both feet, "MAGHRIB 19:04", "in 9 min" |
| `pass-ring-en-1458-awake.png`, `-aod.png` | Ring at 14:58: the arc clockwise from twelve with its point of light |
| `pass-sun-en-1458-awake.png`, `-aod.png` | Sun at 14:58: the day dial, the lit period, the sun by day |
| `pass-moon-en-1458-awake.png`, `-aod.png` | Moon at 14:58: the hands, the chapter arc to Asr, the two windows, the curved texts; always-on the hands keep their facets and keyline, dimmed (Ruling 41), and cover what lies under them as they do awake |
| `pass-moon-en-1010-awake.png`, `-aod.png` | Moon at the mockup's 10:10: the arc across twelve to Dhuhr; always-on the two hands apart, filled and dimmed |
| `pass-arch-ar-ly-12h-1458-awake.png`, `-aod.png` | Arch in Arabic (Libya) on twelve hours: Noto Sans Arabic, the day period م to the left of the time, top-aligned; the Gregorian line at 14 units, as faces.js draws it (Ruling 51a, below) |
| `pass-ring-ar-ly-12h-1458-awake.png`, `-aod.png` | Ring in Arabic (Libya), twelve hours: the arc anticlockwise, the prayer's time to the left of its name |
| `pass-sun-ar-ly-12h-1458-awake.png`, `-aod.png` | Sun in Arabic (Libya), twelve hours: the dial still clockwise |
| `pass-moon-ar-ly-12h-1458-awake.png`, `-aod.png`, `pass-moon-ar-ly-12h-1010-…` | Moon in Arabic (Libya), twelve hours: the right-to-left curves and the wider date window, never mirrored |
| `pass-ring-ar-sa-1458-awake.png`, `-aod.png` | Ring in Arabic (Saudi Arabia): the clock, the prayer's time and the dates in Arabic-Indic digits, in Noto Sans Arabic |
| `pass-arch-pearl-1458-awake.png`, `pass-arch-sage-…`, `pass-arch-night-…` | Arch in the other three colours (Amber is `pass-arch-en-1458-awake.png`) |
| `pass-arch-slots-1458-awake.png`, `-aod.png` | Arch with the wearer's two slots filled (the battery, the steps): the centre moved up to `LAYOUT_COMPS`, the slots dimmed always-on |
| `pass-moon-slots-1010-awake.png`, `-aod.png`, `pass-moon-slots-1458-…` | The Moon with the wearer's two slots filled (the battery, the steps), option (d): the moon window risen, the Hijri date on its tighter curve under it, both slots curved along the bottom between five and seven, nine a plain index; always-on the values in mist and the icons in slate |
| `pass-moon-ar-ly-12h-slots-1458-awake.png`, `-aod.png`, `pass-moon-ar-ly-12h-slots-1010-…` | The same in Arabic (Libya), twelve hours |
| `pass-arch-en-12h-1258-awake.png`, `pass-arch-en-12h-2258-awake.png` | Arch at 12:58 PM and 10:58 PM, English, twelve hours: nothing clipped |
| `pass-arch-ar-ly-12h-1258-awake.png`, `…-2258-awake.png` | The same in Arabic (Libya) |
| `pass-ring-en-12h-1258-awake.png`, `…-2258-…`, `pass-ring-ar-ly-12h-1258-…`, `…-2258-…` | Ring at the two widest twelve-hour times, English and Arabic |
| `pass-sun-en-12h-1258-awake.png`, `…-2258-…`, `pass-sun-ar-ly-12h-1258-…`, `…-2258-…` | Sun at the same two times |

The Moon's shots and their side-by-sides were taken again in the final review's fix round 2,
when its prayer line became one curve of runs that the runtime centres (the names now sit on the
drawing's centre) and its right-to-left Hijri date two runs (a two-figure day had lost a figure in
some months), and again in Task 11: its slot shots for option (d), and its Arabic ones, whose
right-to-left curves (the prayer's name, the countdown, the Hijri month) now begin with the joiners
that keep every glyph (Ruling 37), which moves their letters by a fraction of a pixel. Its always-on shots
were taken again in Task 13 (Ruling 41: the hands filled and dimmed), and the pass gained the Moon
at 10:10 with the slots empty, in English and Arabic, and in Arabic with them filled; and again in
Task 13's closing fix (Ruling 46: the moon window's fill black always-on). Its awake shots are
unchanged. Arch's 12:40 and 18:55 shots are the final review's fix round 1's. The rest of the pass is Task 10's and equal
to it to the pixel then, but for the emulator's step count and one pixel at the screen's edge.

Arch's four Arabic (Libya) shots, `pass-arch-ar-ly-12h-1458-awake.png`, `-1458-aod.png`,
`-1258-awake.png` and `-2258-awake.png`, with their side-by-sides in `task-10/`, are Task 10's again.
Plan 2's Task 2 took them again at its review round 1's fixes (25 October), when Arch's right-to-left
Gregorian line stepped down by its length (Ruling 51 item 1) and drew the sample's 15-character
"الأحد 18 أكتوبر" at 13 units. Ruling 51a replaced that table with a rule by length and month: 19
characters or more 11.25 units, else August (أغسطس) 13, else 14. So the sample is drawn at 14 units
again, in the base face's own box, and these four files are back to the bytes committed before
Ruling 51. On emulator-5558 the face as committed draws them as `ff28cd5a`'s face does, 0 px apart in
one session, and within 1 level of these files
(`docs/superpowers/reports/2026-10-23-phone-forms-fit/r51a/`). This folder, as committed, is the pass
plan 2's Task 3 Step 4 compares with.

Earlier shots in this folder are Task 6's Ring (`ring-awake-1458.png`, `ring-aod-1458.png`, their
`-vs-mockup` pictures, `ring-maghrib-name-1800.png`, `ring-pearl-in-editor.png`), from before the
Hijri date lost its year.

## The final review's fix round 2 (`task-10/fixr2-*`)

| File | What it shows |
|---|---|
| `fixr2-moon-prayer-every-name-vs-mockup.png` | The Moon's prayer line for Fajr, Dhuhr, Asr, Maghrib and Isha (02:30, 08:30, 14:30, 17:30 and 19:30), in English, in Libya's Arabic and in Saudi Arabia's, mockup left and emulator right, a line at twelve: one curve of runs the runtime centres, each within 1.4 px of the drawing's centre (it was up to 6 px off by the name) |
| `fixr2-moon-hijri-rtl-before-after.png` | The Moon's right-to-left Hijri date before the fix (left: "29 جمادى الآخرة" drawn " 9", "30 جمادی الثانی" "3", "12 جمادى الثانية" "1") and after it (right: the month and the day as two runs, each drawn whole in that shot). The two runs still lost letters at some binds; Task 11 found why and closed it (`task-11/`, with the app's own forms; جمادى الثانية is not one) |

## Task 13's evidence (`task-13/`): the Moon's filled always-on hands (Ruling 41)

Mohamed's call of 24 October: always-on, the Moon's hour and minute hands keep their awake form, the
faceted dauphine with its black keyline, dimmed. The awake hands take an `AMBIENT` alpha Variant at
`A_HANDS` (209 of 255, 82%, in `tools/face-moon.py`), each on a black back of its own shape, and the
cap's ink is dimmed with them. Its closing fix (Ruling 46, the Lead's call of 24 October) makes the
moon window's near-black fill black always-on, its hairline and the moon kept, so that the Moon's
always-on lit share stays under Google's 15% at its worst moments. The Moon's always-on pass shots
above, and their side-by-sides in `task-10/` (`ref.py` draws Ruling 41's hands and Ruling 46's
window by default), are the closing fix's.

| File | What it shows |
|---|---|
| `tuning-sheet.png`, `tuning.txt` | The levels tried: Arch, Ring and Sun always-on at 14:58 and the Moon with its old outline hands, then the Moon at 50%, 70% and 82% at 14:58 and 10:10, undithered, with each facet's level. 82% puts the lit facet at the level the others draw their time at (170 against 168.7) and the shaded one at the face's texts; at 50% the hands were no brighter than the Moon's own countdown |
| `hands-1458-awake-before-after.png` | The hands at 14:58: awake (unchanged), always-on before (outlines at 85%, the text showing through them) and now (filled, dimmed, covering the prayer line and the weekday as they do awake) |
| `draw-order-probe.png` | Why each back is declared before its hand: a scratch face with the hour hand's back declared after it draws no hour hand at all, so the clock draws its hands in the order they are declared |
| `lit-share.txt`, `litshare.py` | The always-on lit share of the eight Moon pass frames, at 14:58 and 10:10, when the chapter arc is short, as rebuilt under Ruling 46 (the closing fix): 4.90–6.24% raw, 9.86–12.55% as the face draws them (`lit.py undither`: twice the lit half of the emulator's checkerboard, at the levels its 0.85 dimming leaves), 9.51–12.17% in the drawing, against 12.18–14.82% before the ruling (ec8f1bd4's frames); and the other styles' pass frames beside them (10.26–13.49%). The round 1 column is the first figure, which filled each black pixel from its neighbours: a point high on edges. These are not the worst moments: the second half of the file is `litworst.py`'s |
| `litworst.py`, `worst/`, `worst-r46/` | Fix rounds 1 to 3 and the closing fix: the lit share at the worst moments. The Moon is brightest just after Fajr and just after Isha (04:40 and 20:55, an eight-hour arc), with both slots filled and a Jumada month. Before Ruling 46 it was over 15% there by both measures (`worst/`, the face before it): the drawing reached 15.0–15.2% with 100% and 12,345 steps, and emulator-5558 15.0–15.5% as the face draws it, ordinary values too (review round 3's frames, 890, 960 and 1,030 steps: 15.41%, 15.45% and 15.42%). **As rebuilt, with the moon window's fill black always-on, it is under** (`worst-r46/`, the same thirteen Moon cases on the rebuilt face, a full battery and the emulator's 630–1,160 steps): 12.91–13.32% with both slots filled and 12.62–12.64% with them empty on the emulator, 13.02% at most in the drawing. Untouched by Task 13, Ring at 18:50 is 14.8% on the emulator with its slots empty and 15.6–16.1% with them filled (drawing 14.5% and 15.3%), Sun at 18:00 15.0% (14.2%) and Arch 13.5% (13.4%) (`worst/ring-*`, `sun-*`, `arch-*`). Section 4 checks the emulator's measure on the drawings (within 0.02 point); section 5 sets each Moon case before the ruling, with the fill's pixels black (what fix round 3 expected), and as rebuilt side by side, and the date window's fill, which the ruling left lit, black too. `sweep=5` adds the whole day for each form; `minute-sweep.txt` is the drawing minute by minute where the Moon peaked before the ruling |
| `lever-sheet.png`, `leversheet.py` | The two levers side by side, the sheet Ruling 46 was decided on: the Moon always-on at its worst before the ruling (15.17% in the drawing, `ref.py window=filled`), with the moon window's fill black (12.92%: the ruling's choice) and with the date window's fill black too (10.75%), at their own levels, at 8 times the light (the fills are 7 to 13 of 255), and review round 3's emulator frame with the same pixels black (15.41%, 13.36%, 11.28%). Each window keeps its hairline |
| `hands-vs-committed.txt`, `handsdiff.py` | The Moon's always-on frames against the committed ones: they differ inside the hands' footprint, and outside it within 2 levels |
| `pass-vs-committed.txt` | The full pass against the committed shots (`task-12/passdiff.py`): every awake shot and every always-on shot of Arch, Ring and Sun within 3 levels, but where the Moon's lit arc meets its pointer in one Arabic shot (17 levels, 3 pixels over 8), which moves between runs |
| `variants.txt`, `variants.py` | Ruling 25's audit at the closing fix: 144 Variants (Task 13's 142 and the moon window's fill in its two windows), all `AMBIENT` alpha, none in a Font or a Stroke; the five hands and their always-on levels |
| `validate.log` | Both validators on the final face, at the closing fix too (the log unchanged): wff-validator at format 2, and Watch Face Push's 10 checks on the debug face and on the release |
| `r46-before-after.png`, `r46sheet.py` | The closing fix (Ruling 46) on emulator-5558: the Moon always-on at its worst moment, Libyan Arabic at 04:40 with both slots filled and 28 Jumada al-Ula, before the ruling (15.41%) and as rebuilt (13.29%), at its own levels, where the two look the same, and at 8 times the light, where the window's fill is gone and its hairline stays |
| `r46-same-session.txt`, `windowdiff.py` | The face before the ruling (ec8f1bd4's) and as rebuilt, installed in turn in one session, each moment shot old, new, old again: outside the moon window nothing differs by a pixel, awake or always-on, but the seconds hand and the step count, as between two shots of the same face; always-on the window's 1,662–1,811 fill pixels are black |
| `r46-pass-vs-committed.txt` | The closing fix's full pass against the committed pass before it: every awake shot and Arch, Ring and Sun's always-on shots within 3 levels, but the battery's icon, which moves between runs; the Moon's always-on shots differ only in the window, whose fill is black. Its 8 Moon always-on shots are committed in the pass above, with their side-by-sides in `task-10/` |
| `windowcheck.py` | That `ref.py` draws the window as the face does: always-on its fill black and everything else as `window=filled` draws it, awake the same. It does not show that `window=filled` is the approved drawing. That is shown by ec8f1bd4's `ref.py`, from before the closing fix, against this one with `window=filled`: 0 px in five cases, always-on and awake (the Moon, the Moon of (d), Arch, Ring, the outline hands). `leversheet.py` also draws `lever-sheet.png` again to 0 px |

## Task 12's evidence (`task-12/`): what TalkBack says on the face

| File | What it shows |
|---|---|
| `research.txt` | What the format, the face runtime and the system UI give a screen reader, read from their code (dexdump) |
| `before.txt`, `before-talkback-one-swipe.log` | The face before the task: three items, each said again every second, and TalkBack's own log of it |
| `talkback-walks.txt` | TalkBack's words for each item, in order, and how often it spoke untouched (`tools/watchface/talkback.sh`), heard on the final build (07e511be, slot 0 declared before slot 2 again by Ruling 42) at Task 12's close: all four styles in English with the wearer's slots empty and with the battery and the steps, Libya on 12 hours, Saudi Arabia and English on 12 hours. The time, "Asr in 1 hour 11 minutes, at 16:10", "6 Jumada al-Ula 1448, Sunday 18 October", then the wearer's slots, once each, and nothing in 10 s untouched; the line moving on its own with no new request (to Maghrib, and past midnight to Fajr and Monday's dates); and the empty form four days on. Fix round 3's walks on fef65dc7 (Ruling 40's dates first, since withdrawn) are in git at a75edce9, review round 2's on 95559883 at 0298756d, and the first build's, in the screen's words ("in 5 h 20 min", "SUN 18 OCT"), at 6c074bc7 |
| `touch.txt` | Explore by touch, heard: on emulator-5558 no item of the face answers a touch, before Task 12 or after it, in either order of slots 0 and 2, nor on Google's own accessible face (every hover goes to the system UI's views over the face; TalkBack's own window, as a control, is spoken). So the face keeps plan 1's order by Ruling 42 (the prayer before the dates), and plan 5's device pass hears a touch on his Galaxy, where the dates answering over the prayer may reopen Ruling 40's reorder. Why the earlier reading of the system UI's code was wrong |
| `pass-vs-committed.txt`, `passdiff.py` | The full pass at the end against the committed shots, pixel by pixel; `*-vs-committed.png` side by side (Arch, Sun, the Moon with both slots) |
| `reorder-pass.txt` | Ruling 40's reorder (slot 2 declared before slot 0, fix round 3) and the drawing: the shots where slots 0 and 2 both draw, before and after it in one session (at most 6 px a shot by 1 level), and the full pass after it against the committed shots (the same totals as before it) and against review round 3's pass (51 px in all 45, 1 level). Its section 3 is Ruling 42's revert (07e511be, the face 0298756d's again but for its comments): the same 13 shots after it against 0298756d's of that morning, at most 7 px a shot by 1 level, all on the face's round edge |
| `validate.log` | Both validators on the final face (95559883): wff-validator at format 2, and Watch Face Push's 10 checks on the debug face and on the release. Run again in review fix round 2 on fresh builds, in fix round 3 on fef65dc7's (slot 2 before slot 0), and at Task 12's close on 07e511be's (slot 0 before slot 2 again, Ruling 42), with the same output; the walks and the touches of the close were heard on that face (the installed APK is the build) |

## Task 11's evidence (`task-11/`): the Moon's lower half for option (d), and Ruling 37

The Moon of option (d), Mohamed's choice of 23 October (`docs/superpowers/specs/2026-10-19-watch-companion/moon-slot-choice.html`), and the fix for its right-to-left curves. The mockups are `ref.py face=moon-d`, the page's own drawing.

| File | What it shows |
|---|---|
| `empty-moon-vs-committed.png` | Both slots empty: the Moon at 14:58 against the committed shot (103f6aea) and their difference. English awake and always-on, 0 px but the seconds hand; Arabic, only the right-to-left curves, by a fraction of a pixel (the joiners) |
| `values-vs-mockup.png` | The two slots along the bottom, the page's drawing left and the emulator right: a scratch face with literal values (82 and 0, 100 and 6,410, 12,804 twice, Fri 23 and 10:58), then the emulator's own battery and steps |
| `hijri-compact-forms-vs-mockup.png` | With a slot filled, the risen moon window and the Hijri date on its tighter curve: the twelve forms `moon-fit.py` is calibrated on, page left and emulator right, at 10:15:15 |
| `editor-outlines.png` | The face editor's slot page with the battery and the steps (the outlines are the slots' boxes, around what the Moon draws in them), and with both empty |
| `taps-moon-d.png` | Where a tap goes with the battery and the steps (green: the slot's own action started; red: nothing), beside the editor's outlines |
| `rtl-hijri-before-after.png` | The right-to-left Hijri date in 49 forms (Arabic and Urdu, both digit sets; `probe-summary.txt` names each): one bind each on the 103f6aea face (17 lose a glyph), and on Task 11's face each form's one render over the acceptance probe, 30 shots in five binds with the slots empty and 32 in four with them filled, the font sandbox restarted between binds |
| `rtl-prayer-countdown-before-after.png` | The right-to-left prayer name and countdown, which the same cause reached: Isha's last minute, Maghrib and Fajr, before the joiners and after |
| `rtl-days-midnight.png` | Libya's twelve-hour sample across fourteen days, and across an unattended midnight |
| `ltr-bengali-before-after.png` | Review round 1's fix, the left-to-right curves in Bengali, whose o-kar Noto Sans Bengali UI draws as two signs: a scratch face's literal সোম ২০ in both curved slots (64f27a6f lost its last figure at 3 binds of 30; ending with the joiners, 60 binds whole) and the Bengali Dhuhr যোহর from `bengali.sh` (whole at 30 binds before, and the same pixels at 30 after) |
| `probe-summary.txt`, `taps.log`, `fits.log`, `validate.log`, `cpu.log` | The probes' counts (every form's renders, the restarts and their processes), the taps' log, the fit tools' and the validators' output, and the face runtime's CPU before and after |

## Task 10's evidence (`task-10/`)

| File | What it shows |
|---|---|
| `pass-*-vs-mockup.png` | Each pass shot beside its mockup (`ref.py` with the shot's style, time, language, clock, colour and slots) and the pixels that differ |
| `prayer-line-every-name-vs-mockup.png` | The centre's prayer line for Fajr, Dhuhr, Asr, Maghrib and Isha, in English and in Arabic (twelve hours), mockup left and emulator right, a line at the face's axis: each within 1 px of the drawing's centre |
| `empty-form-12h-4-days-on.png` | A twelve-hour feed at 14:58; four days on with no new request (the empty form: the frame, the lamp and the time still written as the phone writes it); and after the face is bound again, fresh data |
| `editor-slots-arch.png`, `editor-slots-moon.png` | The face editor's complications page after fix round 1: each slot's outline is its own bottom place (on the Moon too, where slot 4's data showed at nine, option (a); Task 11's `task-11/editor-outlines.png` shows option (d)) |
| `fix1-taps.png` | Fix round 1: where a tap goes, on Arch with the battery and the steps and on the Moon with the battery at nine, option (a) (green: the source's own action started; red: nothing); Task 11's `task-11/taps-moon-d.png` is option (d) |
| `fix1-values-scaled.png` | Fix round 1: long values scaled to their room (a scratch face with literal values; the emulator's Day and date and Time and date sources; the pass's battery and steps; the Moon's ring with a literal "100%") |
| `fix2-moon-window-all.png` | Fix round 2: the Moon's date window at 10:10:36 on 24 dates (English, Libya, Saudi Arabia, one Urdu name), the weekday scaled to the room its day leaves it and 5 to 9 px clear |
| `fix2-moon-window-before-after.png` | The same dates in the review's shots before the fix, where the weekday ran into or touched its day |
| `fix2-moon-window-aod.png` | الخميس ٢٢ awake and always-on: the scale and the gap hold |
| `runtime-font-runs-probe.png` | The probe that showed a Text takes several Font runs, that runs are laid out left to right, and that a run of spaces is dropped |
| `runtime-clip-by-bounding-box-probe.png` | The probe that showed a complication is clipped to its bounding box (the green square, inside slot 3's rectangle but outside its box, is not drawn) |
| `runtime-font-kept-by-name-probe.png` | The same subset font file under the old resource name (top, the old tabular digits) and under a new one (below, the new proportional ones): the runtime keeps a font by its name |

Tasks 7, 8 and 9's evidence is in `task-7/`, `task-8/` and `task-9/`, each described in its report in
the ledger folder (`.superpowers/sdd/2026-10-19-watch-companion-1-galaxy-face/`), which git does not
keep: the reports are on the Mac that built the face, beside the ledger. Each folder shows the
face as it was at that task's commit: `task-7/arch-{awake,aod}-{1458,1240,1855}.png` are 82cf6a19's,
before Task 7's fix rounds (the Hijri date still with its year, the clock 4-5 px right). The final
face's Arch at those three moments is `pass-arch-en-{1458,1240,1855}-*.png` here, with their lit arch
equal to the old shots' and to `faces.js`'s, row for row and column for column.
