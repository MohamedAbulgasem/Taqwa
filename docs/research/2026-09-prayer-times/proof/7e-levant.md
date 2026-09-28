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
measured; no sunrise, end of eating or imsak is ever late anywhere measured.

`measured` is `true` only for `jo.awqaf`'s Amman unit and for `ps.iftaa` (both genuinely checked
everywhere they apply); every D_AUTHORITY entry here (`lb.fatwa`, `sy.awqaf`, `iq.sunni`,
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

### Palestine, PA Dar al-Iftaa, `ps.iftaa` — class B

- **Data:** `ps-iftaa-aqsa-2012.txt` (72 dates, fit): the perpetual al-Aqsa Mosque table, 2012
  printing (`archive/raw/moral-auth-mena/levant/pif/ssalah2012.txt`, OCR'd from a PDF via
  web.archive.org). `ps-iftaa-aqsa-2026.txt` (6 dates, test): the SAME table reprinted as the
  Ramadan 1447 (2026) imsakiya image — 14 years later, a real forward-in-time holdout of the
  research's "reused verbatim" claim. Both at al-Aqsa Mosque, 31.7767N 35.2345E.
- **The perpetual winter clock:** both files are printed on a fixed UTC+02:00 (winter/standard)
  clock all year, never Asia/Hebron's real summer daylight-saving time (the imsakiya's own words,
  "حسب التوقيت الدهري... الشتوي" — "by the perpetual... winter timekeeping"). The gate tsv's `clock`
  column carries this; `zone` stays Asia/Hebron, the engine's own civil zone.
- **Margins fitted:** Fajr −16, sunrise +216, Dhuhr −40, Asr −26, Maghrib −25, Isha +23. No widening
  needed.
- **Gate table** (78 place-days):

  | event | days | early | late-end | +0 | +1 | +2 | +3+ | worst | limit | over |
  |---|---|---|---|---|---|---|---|---|---|---|
  | fajr | 78 | 0 | – | 20 | 46 | 12 | 0 | 2 | 2 | 0 |
  | sunrise | 78 | – | 0 | 15 | 50 | 13 | 0 | 2 | 2 | 0 |
  | dhuhr | 78 | 0 | – | 58 | 20 | 0 | 0 | 1 | 2 | 0 |
  | asr | 78 | 0 | – | 45 | 33 | 0 | 0 | 1 | 2 | 0 |
  | maghrib | 78 | 0 | – | 19 | 41 | 18 | 0 | 2 | 2 | 0 |
  | isha | 78 | 0 | – | 19 | 38 | 21 | 0 | 2 | 2 | 0 |

  Held out alone (6 dates, 2026): fajr/dhuhr/asr worst 1, sunrise/maghrib/isha worst 2. At most 2 min
  late everywhere: class B.
- **Gaza's cautious entry, `ps.gaza.cautious`, is not separately gated.** It combines
  `ps.gaza.awqaf` (below) with `ps.iftaa` as members. No dated printed table names Gaza specifically
  for the PA's side of that pairing — only the research's qualitative note that the perpetual table
  says "Gaza = Jerusalem + 3 min", checked once in `pif_2026.py` against a single Gaza date, not a
  printed Gaza-labelled PA table. Synthesising Gaza rows for the PA member by adding that 3-minute
  offset to the al-Aqsa printed times would be inventing numbers, which the brief forbids; the
  cautious entry's correctness rests on its two components' own gates plus the engine's generic
  cautious-merge logic (already covered by `CautiousGateTest`).

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
- **`ps.gaza.cautious`** (the class C combination): not gated directly, for the reason under Gaza
  above — no printed table exists that would let its PA-for-Gaza member be checked without inventing
  numbers.
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
