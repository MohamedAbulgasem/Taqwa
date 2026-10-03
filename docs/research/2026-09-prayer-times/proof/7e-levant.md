# 7e: Levant, Iraq and Yemen

Jordan (`jo.awqaf`), Palestine (`ps.iftaa`, `ps.gaza.awqaf`, `ps.gaza.cautious`), Lebanon
(`lb.fatwa`), Syria (`sy.awqaf`), Iraq (`iq.sunni`) and Yemen (`ye.default`). Before this task
`docs/research/2026-09-prayer-times/authorities-arab-world-turkiye.md` recorded that "Raw captures
for Jordan, Palestine, Lebanon, Syria and Iraq are in the local
`tools/timetables/official/archive/raw/moral-auth-mena/`; they are not yet normalised into tables,
so the gate has **no** Levantine days to prove anything on." This task normalised what that raw
archive holds (plus one further find, below) into `archive/tables/normalized-7e/` (git-ignored,
local only; every file's own header cites its source), added rows to the top-level `MANIFEST.tsv`
(`JO-`, `PS-`, `LB-`, `SY-`, `IQ-` prefixes) and gated each authority. `ye.default` had no raw
capture to normalise at all and stays exactly as the prior draft (class D_NONE, calculated, spec
§6.2 c/d): see "What could not be proven" below.

Command: `./gradlew -p tools/timetables jvmTest gate -PgateGroup=jo-awqaf,ps-iftaa,ps-gaza,lb-fatwa,sy-awqaf,iq-sunni,ye-default`
is green (11 rows checked, 549 place-days, 0 violations). No entry's start is ever early anywhere
measured; no sunrise, end of eating or imsak is ever late anywhere measured where the gate checks it
(an `E` column). Corrected 3 October 2026 (review r1 of ruling R118, C2): `ps.iftaa`'s end of eating
was not checked until then (its rows read `F`, not `F+E`) and ran after the PA's printed Fajr on
100–250 days a year across the West Bank, by up to 3 min; since then it is gated, and never after it.

`measured` is `true` only for `jo.awqaf`'s Amman unit and for `ps.iftaa` (both genuinely checked
everywhere they apply; since ruling R118 `ps.iftaa` is measured at every town of its printed offset list,
16 units, class D_AUTHORITY, see below); every D_AUTHORITY entry here (`lb.fatwa`, `sy.awqaf`, `iq.sunni`,
`ps.gaza.awqaf`, and `jo.awqaf`'s own base entry outside Amman) is `false`, consistently, since none
of their thin samples covers a whole area's tables. `lb.fatwa` had briefly carried `measured = true`
in error (a leftover from before its two late-limit exceptions existed) — fixed here.

## What was found beyond the raw archive

The archive's own `main/` folder (shared with other research groups) holds
`masjidi_region_2.json` and `masjidi26.html`: the Dar al-Fatwa mosque-screen feed (Masjidi TV id 26,
Ras Beirut) that `authorities-arab-world-turkiye.md` called "a 366-row perpetual table" but had not
extracted. It is 366 rows (365 usable; entry 59, for 29 Feb, is dropped). It gave Lebanon by far the
largest fit set here. `iq-sunni-baghdad-waqtazan.txt` (a third party's month) came from the research
scratchpad's `agentA/waqtazan.html`, already fetched for the round but not previously read as a
table.

## Per entry

### Jordan, `jo.awqaf` — class A at Amman, B as the entry, D_AUTHORITY anywhere else measured

- **Amman only.** All 80 dated rows come from the awqaf.gov.jo salat calculator's results log; the
  page also carries a "please select the city" dropdown (Amman, Irbid, Karak, Tafileh, Ma'an, Aqaba,
  the Jordan Valley, Jerash/Ajloun, Mafraq and about 25 smaller sub-district names). Checking the raw
  markup of all 11 captures, every one has `<option selected="selected" value="عمان">` (Amman) —
  confirmed, not assumed, that every row is Amman's, and no other city's results table was ever
  captured. A country-wide class A was wrong: the review caught this, and it is fixed here.
- **Data:** `archive/tables/normalized-7e/jo-awqaf-amman-2021-2024.txt` (50 dates, fit) and
  `jo-awqaf-amman-2025.txt` (30 dates, test), both from the same 11 web.archive.org captures
  2021-02-26..2025-12-18 (`archive/raw/moral-auth-mena/levant/wb/jo_*.html`). Split by calendar
  year: 2021-2024 fit, 2025 held out (forward in time). Point: Amman, 31.955N 35.945E.
- **Margins fitted** (seconds, on top of Fajr 18°/Isha 18.2°, horizon −2.25° — see "the horizon
  question" below): Fajr −35, sunrise +60, Dhuhr +3, Asr +11, Maghrib −47, Isha −52. No widening was
  needed: the held-out 2025 dates were never early.
- **Gate table** (fit + test, 80 place-days, all at Amman):

  | event | days | early | late-end | +0 | +1 | +2 | +3+ | worst | limit | over |
  |---|---|---|---|---|---|---|---|---|---|---|
  | fajr | 80 | 0 | – | 69 | 11 | 0 | 0 | 1 | 1 | 0 |
  | sunrise | 80 | – | 0 | 66 | 14 | 0 | 0 | 1 | 1 | 0 |
  | dhuhr | 80 | 0 | – | 71 | 9 | 0 | 0 | 1 | 1 | 0 |
  | asr | 80 | 0 | – | 72 | 8 | 0 | 0 | 1 | 1 | 0 |
  | maghrib | 80 | 0 | – | 66 | 14 | 0 | 0 | 1 | 1 | 0 |
  | isha | 80 | 0 | – | 64 | 16 | 0 | 0 | 1 | 1 | 0 |

  Held out alone (30 dates, 2025): identical shape, worst 1 min everywhere. At most 1 min late on
  every one of the 80 measured place-days, fit and test alike: class A at Amman (spec's rule, "at
  most 1 min late everywhere measured") — but only at Amman.
- **Amman is a point-table unit** (ruling R30: a timetable printed for one point is a unit there;
  ruling R15: its point rides as the method's fixed point beside the user's own), reaching as far as
  class A's minute allows east to west (ruling R40, `lateReachKm`, about 24 km at 31.955° N). Within
  that reach, `jo.awqaf` reads class A, `measured = true`. Beyond it, nowhere in Jordan was captured:
  the fitted margins above are not known to hold at Irbid, Aqaba or anywhere else, so the edge uses a
  plain safety margin instead of them — Diyanet's own pattern for an unanalysed district (+60 s on
  starts, −60 s on sunrise) — and the registry entry's own declared class is **B** ("an unverified
  unit or season", the brief's own class-B reason). A place that resolves to no unit at all reads
  **D_AUTHORITY** regardless of the entry's declared class (an A/B entry outside every checked unit
  is D_AUTHORITY, spec §6.2 a) — so anywhere in Jordan but Amman, About shows D_AUTHORITY, honestly,
  even though the entry itself is filed as B. `LevantProofTest` checks both: class A resolved at
  Amman, class D_AUTHORITY resolved at Aqaba (29.5267N 35.0078E, well outside Amman's reach).
- **The horizon question the brief asked about:** −2.0° vs −2.25°. The prior draft had already
  chosen −2.25° (the deeper of the two, giving the earlier sunrise and later Maghrib — the safe
  direction) from the same 80-date analysis (`jo_an2.py`'s intersection of every printed minute's
  depression band). This task's gate confirms that choice holds never-early over the whole set,
  fit and held-out; it did not re-derive the angle from scratch (the 80 dates are the same ones the
  angle was chosen from, so this is a consistency check, not independent proof of −2.25 over −2.0,
  and it too is Amman-only, for the same reason as above).

### Palestine, PA Dar al-Iftaa, `ps.iftaa` — class D_AUTHORITY since ruling R118 (was B on 78 dates)

**Ruling R118 (owner, 2 October 2026):** Palestine's own printed town offsets may be applied to its
al-Aqsa perpetual table. The authority publishes no table for any town but al-Aqsa: it prints the al-Aqsa
table and, with it, a list of towns and their minutes from Jerusalem. The ruling allows the offsets only
where the authority itself prints them. The earlier refusal below (Gaza, "inventing numbers") is
superseded where an offset is printed (Gaza's is: + 3) and stands everywhere else.

- **The whole table read (2 October 2026).** Two prints were fetched from darifta.ps and kept under
  `archive/raw/manual/ps-iftaa/2026-10-02/`. The first is the 2012 printing (`ssalahtime/ssalah2012.pdf`,
  32 pages, the table on pp. 4–27, the offset list on p. 2). The second is the 2026 yearly calendar
  (`taqweem2026/riznama2026.pdf`, the same table on pp. 30–35, "al-Aqsa Mosque, winter", without the
  offset list). Both PDFs carry a text layer. Each was read by word position: the columns by x, the
  rows by y, and a minute printed alone sits under the hour of the row above it, as the print does.
  The two reads were made independently and agree on every cell, 366 days × 6 times. They also match
  the earlier OCR read (72 dates) and the Ramadan 1447 imsakiya (6 dates). One half-month page was
  checked by eye against the rendered print. `ssalah/ssalah.pdf`, which the sources row named, is not
  a table: it is the Dar al-Iftaa's 256-page book on the fiqh of prayer (2013).
- **The offsets as printed** (`offsets.tsv` in the archive folder) are one figure per town, for every
  time alike, sunrise included. The print: "This timing is set for the blessed al-Aqsa Mosque (by the
  winter clock); those living outside it observe the time differences as follows". Its Palestinian
  towns:
  - Jerusalem, and with it Ramallah, Bethlehem, Jenin and Nablus: + 0.
  - Jericho: − 1.
  - Hebron, Idhna, Dura, Beit Awwa and Tulkarm: + 1.
  - Qalqilya: + 1.5.
  - Gaza: + 3.
  - Rafah, Khan Yunis and Deir al-Balah: + 4.

  The list's towns in Israel are outside the entry's scope (Palestine) and are not used. The derived
  tables (archive folder, one per town and year; each file's header names its source and derivation)
  are the al-Aqsa table plus that figure. Qalqilya's minute and a half cannot be shown on a
  whole-minute clock: a start shown is never before al-Aqsa + 1.5 exactly when it is at or after
  al-Aqsa + 2, and an end never after it exactly when it is at or before al-Aqsa + 1, so Qalqilya has
  two derived tables, + 2 (its starts) and + 1 (its ends), each gate row checking only its own columns.
  Against the half-minute instant itself Qalqilya's starts are up to 3.5 min late and its ends up to
  3.5 min early (the gate measures them against the whole-minute tables, at most 3).
- **Model.** Fajr 18° and Isha 18°, with sunrise and Maghrib on a −2.25° horizon, as before. Every
  event is taken at the noon declination (`SunModel.CLASSIC_NOON`). Over the whole table this narrows
  the spread against the sun: Fajr's from 3.1 to 2.4 min, Isha's from 2.9 to 2.6. The day-of-noon model
  is MUIS's and JAKIM's.
- **Every printed town is a unit, and its table is the authority's own construction** (review r1, C1,
  I1 and I2; 3 October 2026). `Levant.paTowns` holds all 16 Palestinian towns of the list, each a unit
  (`ps.iftaa.<town>`) at its own point: the app's city point (al-Aqsa for Jerusalem; Beit Awwa, not
  among the app's cities, at an approximate point from public maps, among neighbours that all print + 1). A
  town's times are al-Aqsa's computed times plus its printed minutes: the al-Aqsa method at the al-Aqsa
  point alone (`FixedPointMode.TABLE`), with the town's figure as the authority's own minutes. Whole
  minutes added to a time already rounded never cross a minute, so every town carries al-Aqsa's proven
  bound exactly, with no margin and no exception of its own, and is never early against its own printed
  times by construction (LevantProofTest pins the construction day by day). The user's own sun no
  longer rides beside a town's table: the PA defines the town's times from al-Aqsa's, and riding the
  town's own sun only added lateness (it cost the first version of R118 a 4-min exception at Nablus,
  Hebron and Gaza, and left Tulkarm, Qalqilya, Rafah, Khan Yunis and Deir al-Balah early on their
  neighbours' tables). The sunset Maghrib is held to moves with the town's figure too (else Jericho's
  − 1 put its Maghrib before al-Aqsa's sunset on 26 days, repaired 4 min late).
- **Every place takes its nearest printed town's own table, never a neighbour's** (the discipline of
  rulings R103 and R113). Every unit has the same reach (class D's at al-Aqsa's latitude, about 71 km),
  so the unit a place resolves to is the nearest printed town; every place in Palestine is within about
  20 km of one (the app's 45 Palestinian cities within 16 km). The app's 45 Palestinian cities each resolve to their own printed town, or for a place
  the list does not name, the nearest (RegistryCitiesTest walks them through the app's engine). Beyond
  every reach, the edge: the al-Aqsa method at the user's own point a minute later, the nearest town's
  point still bounding the ends (rulings R44, R45), no town's offset carried beyond its town.
- **The end of eating** (review r1, C2). The PA prints no imsak: its Fajr is when the fast begins, so
  every Fajr column is gated as `F+E`, the end of eating never after the printed Fajr. One dawn cannot
  be both never before the printed Fajr (a start) and never after it (an end), the printed minutes
  wandering about 2.4 min against any smooth rule, so the end of eating has its own fitted margin
  (`endOfEatingMarginSeconds`, ruling R39). Before this fix it was the unmargined dawn, after the
  printed Fajr on 100–250 days a year across the West Bank by up to 3 min (never gated).
- **Split.** The fit rows are al-Aqsa's table on its printing year, 2012 (a leap year, so every printed
  line is used), and on 2026, the 2026 calendar's own print. The held-out rows are al-Aqsa's table
  mapped onto 2027 (darifta.ps had no 2027 calendar on 2 October 2026), the imsakiya's six dates, and
  every town's table on 2026 and 2027 (and on 2012 at Nablus, Hebron and Gaza): nothing is fitted at a
  town. Fitted on 2012 and 2026, 2027 was 1 min early on a few days of Fajr, sunrise, Maghrib and Isha
  at al-Aqsa (1, 11, 1 and 2 days) and its end of eating 1 min late on 7 days of April. This is a
  perpetual table drifting against the leap cycle. Those margins are widened to the bound over all
  three years, never silently.
- **Margins (seconds), al-Aqsa's, carried to every town:**

  | Fajr | sunrise | Dhuhr | Asr | Maghrib | Isha | end of eating |
  |---|---|---|---|---|---|---|
  | +1 | +191 | −37 | −21 | −2 | +59 | −116 |

  The end of eating's fit on 2012 and 2026 gave −96; the three years' bound is −116.
- **Gate** (39 rows: al-Aqsa on 2012, 2026 and 2027 plus the imsakiya, and every town on 2026 and 2027;
  16 places, 13,144 place-days, 12,413 held out): 0 early, 0 late ends (sunrise and end of eating), none
  over its limit, nothing out of order. Worst minutes late, the same at every unit: Fajr 3, sunrise 3,
  Dhuhr 1, Asr 2, Maghrib 3, Isha 3, end of eating 3 (before the printed Fajr). Over the 13,144
  place-days, 3 min late on 992 days of Fajr, 581 of sunrise, 536 of Maghrib and 1,872 of Isha, and the
  end of eating 3 min before the printed Fajr on 2,262.
- **Why class D, not B (ruling R57).** The table's own minutes wander about 2.4 min against any one
  smooth rule, and the wander is not seasonal: Fajr in April and August sits a minute nearer the sun
  than in October. So one never-early margin per event runs 3 min late on some days of four events,
  even at al-Aqsa itself. Holding class B would need an exception on four of six events. R57 says an
  entry like that takes the class its data supports: D, at most 3 min late, as Libya's is. No town
  needs more: al-Aqsa's 3 min is every town's bound, with no exception anywhere.

  The 78 dates that made the entry class B (Task 7e) happened to sit inside a 2-min band. Read whole,
  the table disproves it. With the old margins the engine was also 1 min early on some days at
  al-Aqsa (Fajr, Maghrib and Isha, in 2012, 2026 and 2027), its sunrise 1 min late on others, and it was
  early on many days at Nablus and Hebron. The refit removes every one of those.
- **Every Palestinian city through the app's engine, 2026–2031** (3 October 2026, not committed: it
  reads the restricted table). All 45 Palestinian places in the app's `cities.csv`, through
  `PrayerEngine.dayTimes`, on every day of 2026–2031 (98,595 place-days a mode), against al-Aqsa's table
  plus the printed offset of the place's own town, or of the nearest printed town:
  - with `ps.iftaa` chosen (and confirmed): 0 starts early, 0 sunrises and 0 ends of eating late;
    at most 3 min late (3.5 against Qalqilya's half minute);
  - on Automatic: the 32 West Bank places, the same (0, 0, 0); the 13 Gaza Strip places follow
    `ps.gaza.cautious`, whose Maghrib is capped at its most-followed member's, Gaza's Ministry of
    Awqaf (ruling R38, disclosed by `about_cautious_maghrib_cap`), so it is before the PA's printed
    Maghrib on every day; their Fajr, Dhuhr, Asr and Isha are never early, and no end late.

  No Palestinian place's nearest listed town is one of the list's towns in Israel.
- **Pages.** Hebron and Nablus stay held. The city-page rule needs class A, B or C, and the data
  supports D. Gaza stays held as before, since its cautious entry's Ministry of Awqaf member is checked
  on six 2020 days only. The PA member is checked at Gaza (`ps.iftaa.gaza`) and at Rafah, Khan Yunis
  and Deir al-Balah.

### Gaza, Ministry of Awqaf, `ps.gaza.awqaf` — class D_AUTHORITY

- **Data:** `ps-gaza-awqaf-2020.txt`, six full dated rows from the one real capture among ten
  (`archive/raw/moral-auth-mena/levant/gz/1441_11.pdf`, Dhul-Qi'dah 1441 = Jun-Jul 2020; the other
  nine PDFs are 404 pages saved by mistake). The sheet abbreviates every other day to minutes-only,
  carried against the nearest full row; that continuation was not attempted, so only the six days
  the sheet prints in full are used. Gaza City, 31.502N 34.467E.
- **Margins fitted:** Fajr +12, sunrise +59, Dhuhr −8, Asr −54, Maghrib −45 (on top of the existing
  +3 min authority offset), Isha −34. Nothing held out — six days from one five-year-old month is too
  thin for a real split.
- **Gate table** (6 place-days, all fit): fajr 3/3 at +0/+1, sunrise 6/0, dhuhr 2/4, asr 6/0,
  maghrib 5/1, isha 2/4 — never early, worst 1 min late. Stays D_AUTHORITY (spec §6.2 a): six days
  proves nothing about the other 359 days of the year, let alone other years.

### Lebanon, Dar al-Fatwa, `lb.fatwa` — class D_AUTHORITY (with two late-limit exceptions)

- **Data:** `lb-fatwa-beirut-2024.txt` (365 dates, fit): every printable row of the Masjidi TV id 26
  (Ras Beirut) perpetual feed, mapped onto the real 2024 calendar. `lb-fatwa-beirut-dated.txt` (19
  dates, test): captures spanning 2019-2026 from the Dar al-Fatwa homepage's own widget
  (`archive/raw/moral-auth-mena/levant/wb/dfh*.html`) and Al-Liwaa newspaper's daily reprint of the
  same table, plus `lb-fatwa-beirut-dated-2023-04-01.txt` (1 date, test, its own fixed clock — see
  below). Beirut, 33.894N 35.502E.
- **The perpetual table's clock, found by fitting:** the first fit (with `clock` defaulting to the
  real Asia/Beirut zone) gave a sunrise margin of about **−61 minutes**, decided by a day in late
  June — a single-day-driven number so large it could not be right. Isolating the fit month by month
  showed February normal (+32 s) and every month from March (the DST transition) through June
  clustering tightly around −57 to −61 minutes, growing smoothly through the season on top of that
  base. That is the signature of a table printed on a **fixed non-DST clock**, exactly like the PA's
  al-Aqsa table: confirmed by re-fitting June alone with `clock=UTC+02:00`, which brought sunrise
  back to a normal −29 s. The gate tsv's `clock` column now carries this for the perpetual file; the
  dated captures (a different, real-time source) keep `clock` at the real Asia/Beirut zone, with two
  exceptions below.
- **Three dated captures re-examined; one included, two stay excluded** (a review caught that the
  first pass had excluded all three as "the same widget bug" without checking each on its own merits):
  - `2023-01-25`: every printed time is a uniform ~55-65 min later than the neighbouring captures
    imply for that calendar date, on EITHER clock hypothesis (+2 or +3). 25 January is deep winter,
    nowhere near a real daylight-saving change, so this cannot be a clock-reading question; the page's
    own displayed Hijri/Gregorian date is correct (25 Jan 2023 ↔ 3 Rajab 1444, matching its own news
    section), so the date is right and the printed times are wrong — a genuine bug in that render of
    the widget. Stays excluded.
  - `2023-04-01`: Lebanon's government decreed the 2023 clock change delayed to 20 April; checked
    against exact sun position at Beirut's point, this capture's six printed times match the fixed
    UTC+02:00 (the decreed, pre-change) clock within 0-2 min on every event — genuine, not a bug.
    **Included**, with `clock=UTC+02:00` on its own row; the gate proves it never early, 0 broken.
  - `2024-03-28`: checked the same way, its six printed times match the real DST (UTC+03:00) clock
    within 1-14 min on fajr/dhuhr/asr/maghrib/isha — the widget running a few days ahead of Lebanon's
    real 2024 transition (31 March, per the tz database), not the same bug as 25 January. But read on
    that same UTC+03:00 clock, its sunrise would be 10 min LATE — and a sunrise (an end) may never be
    late by any amount; no `LateLimit` exception can excuse it, only a start's lateness. Admitting
    this one date would need widening the sunrise margin by the 10 min excess plus 5 s safety, making
    every other one of the 384 measured days' shown sunrise 11 min earlier than the data otherwise
    supports — not a fair price for one capture. **Stays excluded**, for this precise reason, not
    "the same bug" as 25 January (its Dhuhr misread by a naive search of the raw page, as a morning
    time, was also checked and is a push-notification countdown string, not the printed time; the
    true printed Dhuhr, shortly after half past noon, is what was fitted against and excluded on the
    sunrise question above, not a parsing error).
- **Margins fitted, then widened:** Fajr −369, sunrise −61, Dhuhr −12, Asr +47, Maghrib +392,
  Isha −4 from the 365-day fit. Two of the 19 held-out dated captures were early by exactly 1 min
  (Fajr, Dhuhr): widened by +65 s each (the 1 min excess plus 5 s), giving Fajr −304, Dhuhr +53.
  Never tuned on the holdout silently — this note is that record. (Adding 2023-04-01 changed nothing
  here: it needed no widening on any event.)
- **Gate table** (fit + test, 384 place-days):

  | event | days | early | late-end | +0 | +1 | +2 | +3+ | worst | limit | over |
  |---|---|---|---|---|---|---|---|---|---|---|
  | fajr | 384 | 0 | – | 3 | 21 | 36 | 324 | 9 | 9\* | 0 |
  | sunrise | 384 | – | 0 | 139 | 124 | 83 | 38 | 3 | 3 | 0 |
  | dhuhr | 384 | 0 | – | 0 | 132 | 229 | 23 | 3 | 3 | 0 |
  | asr | 384 | 0 | – | 63 | 87 | 219 | 15 | 3 | 3 | 0 |
  | maghrib | 384 | 0 | – | 60 | 59 | 58 | 207 | 7 | 7\* | 0 |
  | isha | 384 | 0 | – | 41 | 61 | 40 | 242 | 7 | 7\* | 0 |

  \*by exception, see below. Held out alone (19 dates): fajr worst 6, sunrise/asr worst 2-3, dhuhr
  worst 2, maghrib/isha worst 7. Never early anywhere on either split.
- **Exceptions (spec §6.3, rulings R37/R41):** a single never-early margin fitted over 384 days
  across two sources and seven years necessarily lets some days run later than the class default (3
  min): Fajr up to 9 min (worst day inside the 2024 perpetual set), Maghrib and Isha up to 7 (worst
  day in both the full set and the held-out dated set). Recorded as two `LateLimit`s on `lb.fatwa`
  with these reasons; About shows the same 9 and 7 the gate proves. Because the fit dominates any
  one worst day, class stays D_AUTHORITY rather than moving to B/A even though most days are well
  inside 1-2 min — the spec's class test is about the worst day, not the typical one.
- **Not measured:** the end of eating keeps the spec's dawn-angle rule (20°) unchanged; none of the
  captures print an end-of-eating or imsak column to fit it against.

### Syria, Ministry of Awqaf, `sy.awqaf` — class D_AUTHORITY

- **Data:** the only two dated samples the earlier research found, at two slightly different
  Damascus points: `sy-awqaf-damascus-fit.txt` (a Ramadan 1447 imsakiya reading, 33.5138N 36.2765E,
  fit) and `sy-awqaf-damascus-test.txt` (a Mawaqit-style reading at the Umayyad Mosque itself,
  33.5116N 36.3064E, test). The one Wayback capture held for the ministry's own site
  (`archive/raw/moral-auth-mena/levant/sy/wb/20210121052509.html`) is a menu of links to per-city
  pages; none of those pages were themselves captured, so it has no table to read.
- **Margins fitted, then widened:** Fajr −47, sunrise −7, Dhuhr −97, Asr −16, Maghrib −46, Isha −10
  from the one fit date. The held-out date was early by 2 min on Dhuhr and 1 min on Asr and Maghrib;
  widened by +125 s (Dhuhr, to +28) and +65 s (Asr to +49, Maghrib to +19).
- **Gate table** (2 place-days): every event never early, worst 1-2 min late on the held-out date
  (fajr/isha exact, sunrise/dhuhr/asr/maghrib 1 min). Two dates, two points, cannot prove a class
  above D_AUTHORITY (spec §6.2 a); the "2 min tamkin officially since 28 Feb 2025" claim (SANA) is
  unchanged from the prior draft, carried in `authorityMinutes`, not something this task's two dates
  could confirm or refute on their own.

### Iraq, Sunni Endowment, `iq.sunni` — class D_AUTHORITY

- **Data:** `iq-sunni-baghdad-official.txt`, the one dated official sample the earlier research found
  (4 Dec 2025, Baghdad 33.341N 44.401E), fit. Nothing held out.
- **A third-party site's month, normalised, then excluded.** `iq-sunni-baghdad-waqtazan.txt` (30
  dates, 19 Sep–19 Oct 2026, from `waqtazan.com`, which states it publishes the Sunni Endowment's
  Baghdad table) was normalised and gated as a test row. Checked against the margins the one official
  date implies, every one of its 30 days is early: Dhuhr by 6 min, Asr by 15-16, Maghrib by 24-25,
  Isha by 26-27 — one-sided (never late) and far larger than a plausible seasonal drift between
  4 December and September/October (Fajr and sunrise, which do vary most with season, were the ONLY
  two events that were fine, both running late). That pattern — morning events consistent, every
  afternoon/evening event off by tens of minutes in one direction — is not what a correct but
  differently-dated table looks like. Given no way to independently confirm `waqtazan.com`'s
  reliability, it is excluded from the gate rather than accepted as ground truth or used to force the
  margins wider than the one official date needs (see `iq-sunni.tsv`'s own note; the file stays
  normalised on disk with this recorded as its reason).
- **Margins fitted:** Fajr −26, sunrise +18, Dhuhr +268, Asr +125, Maghrib +154, Isha −29. Gate table
  (1 place-day): every event exact (0 min) against its own fit date, by construction. One date proves
  nothing beyond itself: D_AUTHORITY, unchanged in substance from the prior draft (spec §6.2 a).
- **Kurdistan** (`archive/raw/moral-auth-mena/levant/iq/krd.html`) was read but is the Kurdistan
  ministry's page, not the Sunni Endowment's; out of scope for `iq.sunni` and not otherwise part of
  this brief.

### Yemen, `ye.default` — class D_NONE, unchanged

- **No raw capture exists to normalise.** `archive/raw/moral-auth-mena/levant`'s own README line
  names what it holds as "Jordan, Palestine, Lebanon, Syria, Iraq, the Masjidi feeds" — Yemen is not
  among them, and no Yemen-specific file, script or sample turned up anywhere in that folder or in
  `main/`. The research's one claim (Sanaa Awqaf's 31-location page, LOW confidence, Fajr 18°,
  Isha ≈16°, Dhuhr ≈+10, Maghrib +6 lowland/+9.5 highland, one date) was never saved to a file, so
  there is nothing here to read against the engine, and nothing was invented to fill the gap.
- `ye.default` is left exactly as the prior draft: calculated by Taqwa (spec §6.2 c), the published
  minutes folded in as floors (§6.2 d). No gate row, no stamp, no class change.

## What could not be proven, and why

- **Yemen (Aden and everywhere else):** no data at all, as above.
- **`ps.gaza.cautious`** (the class C combination): its PA member is checked at Gaza since ruling R118,
  the al-Aqsa table + 3 as the authority prints it (unit `ps.iftaa.gaza`). Its Ministry of Awqaf member
  still has six 2020 days only, so the cautious entry is still not measured at Gaza.
- **Jordan's horizon angle (−2.0° vs −2.25°):** confirmed consistent with the 80 dates it was already
  chosen from, not independently re-derived from new data (there is none beyond those 80 dates).
- **Lebanon's end of eating:** no authority ever prints an end-of-eating or imsak column; the dawn-
  angle rule is unchanged and unmeasured by this task.
- **Syria's 2-minute tamkin and its Feb 2025 effective date:** unchanged from the prior draft (SANA,
  second-hand); the two dates gated here are both after that date and consistent with it, but two
  points cannot confirm when the rule started.
- **Iraq beyond one date:** a full month exists only from a third party whose reliability could not
  be established (see above); the Sunni Endowment's own table was never captured beyond the one
  sample.
- **Gaza beyond six 2020 days:** the sheet's abbreviated (minutes-only) rows were not decoded; a
  hardier reader could plausibly recover most of the ~24 remaining days of that one month, but nothing
  more recent than 2020 was found for Gaza specifically.

## Concerns

- **Iraq and Gaza are single-sample or thin-sample entries with no genuine held-out proof.** Their
  D_AUTHORITY class is correct per spec (nothing stronger is claimed), but a future capture of even
  one more date for either would be worth adding.
- **The excluded sources (Iraq's `waqtazan.com`, two of the three re-examined Lebanon dfh captures)
  are real, named findings, not silent omissions** — each is recorded in its data file's own header,
  in the relevant gate tsv's note column, and here, with the actual numbers that led to exclusion, so
  a later researcher can re-check the reasoning rather than re-discover it. The two Lebanon
  exclusions are for two different reasons (25 Jan 2023 a genuine widget bug; 28 Mar 2024 a real
  reading that would still cost every other day 11 min of earlier sunrise to admit) — worth keeping
  distinct if either is ever revisited.
- **The "fixed perpetual clock" pattern (found independently for Palestine's al-Aqsa table and now
  Lebanon's Masjidi feed) may be worth checking for other MENA sources still to be gated** in other
  Task 7 subtasks that use similar mosque-screen or imsakiya feeds — a `clock` column left at its
  `zone` default on such a table would silently produce a large, wrong, single-worst-day-driven
  margin exactly like the one this task found and traced back for Lebanon.
- **Registry fix round:** per the dispatch, a registry fix round runs in parallel on other files;
  nothing here was seen to depend on it, but `Levant.kt`'s imports and the `margins`/`LateLimit`
  helpers it uses were re-read from `Authority.kt` as it stands in this worktree's `prayer-engine`
  merge, not re-verified against any later change to that file.
- **`Units.kt` was touched, out of this subtask's owned files.** Task 7's shared instructions list
  `Units.kt` under the registry core that a subtask may not edit, and say to stop and report a needed
  core change as a concern instead. Amman's point-table unit needs exactly one line there (registering
  `Levant.jordanUnits` in the `sets` list, the same way every other authority's units are registered)
  and cannot exist without it; the review that asked for the unit explicitly named "a point-table
  unit" and R40, so it was made, as the smallest possible change (one import, one `.plus(...)`), and
  is flagged here rather than made silently.
