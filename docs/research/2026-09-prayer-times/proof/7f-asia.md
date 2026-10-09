# Task 7f — South Asia and Central Asia

Six authorities: Pakistan (`pk.karachi`), India (`in.karachi`), Bangladesh (`bd.ifb`), Uzbekistan
(`uz.board`), Kazakhstan (`kz.qmdb`), Kyrgyzstan (`kg.default`). Tajikistan, Turkmenistan and
Afghanistan live in `Generic.kt`, owned by subtask 7h; not touched here (see Concerns).

All six pass `./gradlew -p tools/timetables jvmTest gate -PgateGroup=pk-karachi,in-karachi,bd-ifb,uz-board,kz-qmdb,kg-default`
green: **0 early starts, 0 late ends, 0 lateness over each event's limit**, across 942 place-days.
Every gate row and its stamp is checked against real archived tables; none of the six has an open
licence (unlike MUIS, DUM RT or LUPT), so no printed minute appears anywhere below — only
statistics, angles, minutes and margins (seconds), as the design requires.

## Where the evidence came from

None of MANIFEST.tsv's PK-, IN- or KG- prefixes exist (the manifest is a first pass over what an
earlier research round placed under `official/archive/`, and it never reached these three). What
does exist there: `BD-IFB` (`archive/tables/off-ifb-dhaka.txt`, 5 October 2026 rows) and
`KZ-QMDB`/`UZ-BOARD` under `archive/tables/moral-auth-world/centralasia/`. The wider research pass
behind `docs/research/2026-09-prayer-times/authorities-asia-africa-europe-americas.md` (its own
scratch data, `scratchpad/moral-auth-world/{southasia,centralasia}/`) held real digitized tables
for Pakistan, India, Kazakhstan, Kyrgyzstan and more of Uzbekistan than the manifest listed. Since
`OfficialTable` reads whatever a gate row's `path` names under the official root, not something
MANIFEST.tsv itself gates, I converted the missing tables into the `daily` format and placed them
under `archive/tables/moral-auth-world/{southasia,centralasia}/` (still git-ignored, still
restricted; MANIFEST.tsv itself was left alone, out of this subtask's ownership). Every conversion
is named in its own file's header comment, with the source, and any correction made.

## pk.karachi — Pakistan

**Class D_AUTHORITY.** No binding state table; the Karachi convention (18°/18°, Hanafi, "nearest")
is the near-universal method (Banuri Town's own web calculator equals it exactly, HIGH confidence).

**Evidence (`pk-karachi.tsv`):** the Deobandi Karachi Daimi (permanent) timetable, all rights
reserved, digitized from three month-pages (June, September, December 2026) read as images
(`banuri.edu.pk`); and Dawat-e-Islami's Karachi AJAX (September 2026, "sharing these default
timings is not allowed"), a later, Barelvi member.

**Fit/held-out.** First fit on June and December (the solstices) alone; September (Banuri) and
Dawat-e-Islami both held out. Both showed early starts against the solstice-only margins:

```
pk.karachi — held out only (30 place-days = Banuri Sep; DI Karachi Sep not shown separately)
  fajr: 27 early (up to 1 min)      sunrise: 5 late ends (up to 1 min)
  asrHanafi: 30 early (up to 2 min) maghrib: 17 early (up to 1 min)
  isha: 25 early (up to 1 min)
```

Per the brief's rule ("if held-out rows are early, widen... note it"), both were folded into the
fit set rather than hand-tuning a single "excess + 5 s" step across two different sources: the
Fitter was re-run on the union (Jun + Sep + Dec + DI, 121 place-days). Nothing is held out for this
entry; `pk-karachi.tsv`'s note column records the fold for each row.

**Margins (fitted, seconds):** fajr +4, sunrise −28, dhuhr +3, asr +71 (the larger of the printed
Standard and Hanafi columns' own fits, −37 and +71 — a single margin has to clear both), maghrib
+26, isha +5, end of eating (Subh sadiq, printed once for both roles: `F+E`) −26.

**Gate (all 121 place-days, both months and DI, current margins):** 0 early, 0 late ends, 0 over
the limit. Worst late: fajr 2, asrHanafi 3, all others ≤ 1 min (endOfEating 1 min early, the safe
side). No exceptions needed.

**Not proven:** the fit is at Karachi's own point; the convention is angle-based and used
device-wide, so other Pakistani cities (Lahore, Peshawar, the north) are not separately measured.
Kerala's Shafi'i Asr (mentioned in the research) is not modelled at all — no table found.

## in.karachi — India

**Class D_AUTHORITY.** No authority found; the only verified table is Dawat-e-Islami Delhi.

**Evidence (`in-karachi.tsv`):** Dawat-e-Islami's Delhi AJAX, September 2026 (same "sharing... not
allowed" restriction), 18°/18° Hanafi with the 701 ft elevation folded into the printed times.

**Fit/held-out.** One month, one source: fit only, nothing held out (as RU-DUMRT's single-town
table). Margins: fajr +1, sunrise −123, dhuhr +4, asr +75, maghrib +121, isha +4. Delhi's elevation
pushes sunrise and Maghrib far wider than Karachi's own fit (−28/+26 s there), which is why India
keeps its own method rather than sharing Pakistan's `conventionWithMwlMinutes` guess.

**Gate:** 0 early, 0 late ends, 0 over the limit (worst 1 min on every event). No exceptions.

**Not proven:** no sehri/imsak column was in the capture, so the end of eating has no evidence of
its own; it reuses the fitted sunrise margin as a same-point, same-kind ("end") stand-in — a
judgement call, not a fit. Kerala's Shafi'i Asr: not modelled, no table found (as Pakistan).

## bd.ifb — Bangladesh

**Class D_AUTHORITY.** The Islamic Foundation's year-round Dhaka table (3 min added to true Fajr,
Zuhr and Maghrib; sehri 3 min before true dawn).

**Evidence (`bd-ifb.tsv`):** the permanent table digitized across the year (72 rows, roughly every
sixth day; no licence found, mirrored PDF), fit; an independently digitized October page (already
in the public manifest as `BD-IFB`, `archive/tables/off-ifb-dhaka.txt`), held out. One cell (a
late-June Maghrib reading) was blanked before fitting: an hour off its neighbours, a clear OCR
misread, not guessed.

**Held-out result:** the October page's 5 days match the permanent table's own October rows
exactly (same Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha to the minute) — a same-table cross-check
across two independent digitizations, not an independent year, but a real agreement check none the
less.

**Margins (fitted at Dhaka's own point, seconds):** fajr −53, sunrise −56, dhuhr +29, asr −34,
maghrib +47, isha +165, end of eating (its own dawn-angle rule, ruling R39) −358. The entry has no
per-district units (Task 5's design), so each is then widened by a further "district edge" (a
quarter degree, about a minute, kept from the original code): fajr +7, sunrise −116, dhuhr +89, asr
+26, maghrib +107, isha +225, end of eating −418.

**Gate (all 76 place-days):** 0 early, 0 late ends. Fajr, Isha and the end of eating exceed the
class's plain 3 min: worst 4 min late (fajr, isha) and 4 min early (an end, endOfEating), each
recorded as a `LateLimit` exception with its reason (the swings across the year plus the district
edge stacking on top). Sunrise, Dhuhr, Asr and Maghrib all stay at or under 3.

**Not proven — a concern:** research documents other districts' sehri/iftar running −7 to +8 min
(Dinajpur/Thakurgaon/Panchagarh +6/+11) from Dhaka's own table; the flat 60 s district edge, kept
from Task 5, does not cover that spread. The entry has no per-district units to fit against; a
proper fix needs one (see Concerns). The 2026 Ramadan district-edge method itself (sehri at the
east edge, adhan/iftar at the west) is not separately gate-tested; only the year-round table is.

## uz.board — Uzbekistan

**Class D_AUTHORITY.** The Muslim Board (muslim.uz/islom.uz): the current rule (confirmed live,
25–26 Sep 2026) is 15.5°/15.5° Hanafi, Maghrib = sunset + 4. Wayback captures show an older rule
(15° Isha, Dhuhr + 5, Maghrib + 3) in force from at least Jul 2024 to 19 Jul 2026; the exact switch
date is not known, only that it happened before 25 Sep 2026.

**Evidence (`uz-board.tsv`):** the live site's own two most recent days (fit), and 18 Wayback
captures from Jul 2024 to Sep 2026 spanning both eras (held out first, see below).

**Fit/held-out.** Fit on the 2 current-rule days alone first: held out against the 18-day span,
almost every event failed (6 early Fajr, 4 late sunrise ends, 17 early Dhuhr, 4 early Asr — the two
rules are not margin-compatible). Since it is not certain which era every cached client sees, both
eras were folded into one fit set (20 place-days), per the widening rule; nothing is held out.

**Margins (fitted, seconds):** fajr −7, sunrise −246, dhuhr +274, asr −30, maghrib −26, isha −69.
Sunrise and Dhuhr are this wide specifically to also clear the older rule (its own Dhuhr + 5 min,
and an earlier sunrise on at least one capture, 19 Jul 2026).

**Gate (all 20 place-days):** 0 early, 0 late ends. Sunrise (worst 5, an early end — the safe
side), Dhuhr (worst 6) and Isha (worst 4) exceed the plain 3 min limit; each carries a `LateLimit`
exception naming the two-era cause. Fajr, Asr and Maghrib stay at or under 3.

**Not proven:** the exact switch date; whether a client that still sees the old rule after the pad
window changes will remain covered (the margins are fit to what was captured, not a guarantee
against a future third revision). islom.uz's regional per-city minute offsets (Nukus +42, Andijon
−12, etc.) are not modelled; only Tashkent is measured.

## kz.qmdb — Kazakhstan

**Class D_AUTHORITY.** QMDB (muftyat.kz): praytimes.js ISNA 15°/15°, Hanafi, the AngleBased
high-latitude rule (already reproduced as a `TwilightCurves` day-of-year angle envelope, `kz.qmdb`
section — read, not edited beyond the margins below, since the residual it leaves is small and
this subtask's edits are its own margins/authorityMinutes/LateLimits only), sunrise − 3 and Dhuhr,
Asr, Maghrib + 3 below 48° N, − 5/+5 at or above it (`CentralAsia.variants`, unchanged).

**Evidence (`kz-qmdb.tsv`):** Almaty (43.24° N, below 48) and Astana (51.13° N, at/above it), both
the full archived year 2026 (`archive/tables/moral-auth-world/centralasia/off_ala.txt`/`off_ast.txt`,
already present, `CENTRAL-ASIA`/`KZ-QMDB` in MANIFEST.tsv). The API's own `imsak` field (Fajr + 10,
undocumented) is confirmed unused, as the wider research already found for `off_ala_imsak.txt`.

**Fit/held-out.** First fit on Jan–Jun alone (both cities, 362 place-days); held out on Jul–Dec
(368 place-days): 1 early Fajr, 25 early Asr, 23 early Isha and 4 Isha days over the limit, all in
high summer (Jul–Sep) at both cities, each by exactly 1 min. Folded Jul–Dec into the fit set too
(730 place-days total); nothing is held out.

**Margins (fitted, seconds, shared across both variants):** fajr −2, sunrise +19, dhuhr −22, asr
−11, maghrib −17, isha −3. Refitted on 3 Oct 2026 with the northern and 2027 tables (*The starts
north of Astana*, below): fajr +8, sunrise +14, dhuhr −22, asr −8, maghrib −15, isha +11. Refitted
again on 6 Oct 2026 (*North of Petropavl, the west and the south*, below): asr −6 and maghrib −14
(later), the others unchanged; from 46° to 48° N the end of eating carries −150 s, and the curves are
read at each place's own latitude as well as on the 0.1° grid.

**Gate (all 730 place-days, two cities):** 0 early, 0 late ends. Fajr (worst 5, mid-May, Astana)
and Isha (worst 4, mid-July, Astana) exceed 3 min: both carry a `LateLimit` exception, since the
residual is the AngleBased curve's own (already fitted as tightly as a flat margin allows; a
further reduction would need a change inside `TwilightCurves`' `kz.qmdb` section itself, which
this subtask left alone as it already reproduces QMDB's documented rule and the residual is small).
Sunrise, Dhuhr, Asr and Maghrib stay at 1 min.

**Not proven:** only two of QMDB's 5,694 API places are measured (Almaty, Astana); `measured =
true` (kept from Task 5) claims the whole country on the strength of the documented, reproduced
algorithm plus these two points, not a nationwide fit. Since 3 Oct 2026 six are measured, over 2026
and 2027 (below), from 43.2° N to 54.9° N. Since 6 Oct 2026, 29 are, from QMDB's southernmost place
(Zhenis, 40.6° N) to its northernmost (Isakovka, 55.4° N), in every zone the app's Kazakh cities use.

## kg.default — Kyrgyzstan

**Class D_AUTHORITY**, kept unmeasured. The Muftiate (muftiyat.kg), a likely majority, LOW
confidence: no API, and only one region-wide homepage JSON fetch (115 localities) was ever
archived.

**Evidence (`kg-default.tsv`):** that one fetch's Bishkek entry, 26 Sep 2026. Its JSON prints both
`sunset` and a separate, later `maghrib`, about 7 min apart — the Muftiate's own addition, matching
the wider research's fit.

**Fit/held-out.** One day, one place: fit only, nothing held out (as RU-DUMRT's single-town table).
Margins (fitted, seconds): fajr −64, sunrise −10, dhuhr −62, asr +47, maghrib +397, isha −45. The
large Maghrib margin is the Muftiate's own + 7 min, not a loose fit.

**Gate:** 0 early, 0 late ends, 0 over the limit (the sole day fits exactly, worst 0/1 min per
event). No exceptions.

**Not proven — deliberately not upgraded.** One day cannot stand for a season: even though every
event is exact on its single measured day, the entry stays class D and `measured = false`. Only
Bishkek is measured, of 115 localities; regional spread across Kyrgyzstan is not checked at all.

## The end of eating, audited (3 Oct 2026, before Ramadan 1448)

Until this audit `pk.karachi`'s Banuri rows and `bd.ifb`'s sehri column were the only end-of-eating
checks in this group. No other table here prints an imsak or sehri column (QMDB's API "imsak", after
its Fajr, is not used), and the fast begins at the printed Fajr, so every other row's Fajr column is
now also the end of eating (`F+E`). Whole gate, no start moved:

| entry | days | late ends | worst, min before the printed Fajr | limit |
| --- | --- | --- | --- | --- |
| `kz.qmdb` | 730 | 0 (29 before the fix) | 8 (364 days at 3 or more) | 8, recorded |
| `uz.board` | 20 | 0 | 1 | 3 (class D) |
| `kg.default` | 1 | 0 | 0 | 3 (class D) |
| `in.karachi` | 30 | 0 | 3 | 3 (class D) |
| `pk.karachi` (with Dawat-e-Islami's Karachi row) | 121 | 0 | 2 | 3 (class D) |

**Kazakhstan: the one late end found.** At Astana the end of eating came after QMDB's printed Fajr on
29 days of 2026, the first 12 May, by up to 4 min: the mirror of the Fajr residual above (QMDB's
AngleBased Fajr runs up to 5 min before the model's in mid-May, and the end of eating was the model's
plain dawn). Fixed the existing way: the fitter's end-of-eating bound over the whole year is −221 s,
decided at Astana on 14 May; fitted on Almaty's rows alone it is +18 s. So the end of eating at and
above 48° N carries −221 s (`CentralAsia.variants`, beside QMDB's ± 5 min there), and Almaty's
stays 0: an end only ever moves earlier, and there it needs nothing.

**North of Astana (fix round, 3 Oct 2026).** The review asked what Astana's −221 s is worth further
north, where the AngleBased clamp runs for more of the year and cuts deeper. Using Astana's margin
there is not the same as using its Fajr limit: a deeper residual makes a Fajr start later, the safe
side, but makes the end of eating late, the unsafe one. So QMDB's 2026 and 2027 tables were fetched
from the same API for Astana and for its own Kokshetau (53.3° N), Kostanay (53.2° N), Pavlodar
(52.3° N) and Petropavl (54.9° N) — the Astana 2026 table came back identical to the archived one —
and checked in a scratch gate run (the tables are not yet in the archive, so they have no rows in
`kz-qmdb.tsv`). At −221 s the end of eating came after QMDB's printed Fajr on 123 of 3,650 days (among
the gate's samples: Petropavl from 30 April 2026, 2 min, and Astana itself on 17 May 2027, 1 min);
the refit needed 153 s more than Astana's 2026 bound. Refitted the same way on all fourteen tables (Almaty's, Astana's archived 2026 and the ten
new ones), the fitter's bound is −374 s, decided at the north on 6 May 2027. With it there is no late
end on any of the 4,015 place-days of that run, and the whole gate is green. The cost is on the safe
side: on most days the end at and above 48° N now comes 3 or more minutes before the printed Fajr,
up to 8 min, recorded as the third exception (8 min, the end of eating alone; it was 5). The golden
vector's Astana seed moved on 16 days again, the end of eating alone, 2 or 3 min earlier than
before this round.

Two things stay open. North of Petropavl (to 55.4° N, the country's edge) the margin is an
extrapolation, and for an end that is the unsafe side. And the northern tables must go into the
archive and get their rows (`F+E S D A M I`, fit) before Ramadan 1448 (from about 8 February 2027):
the monitor now fetches them each month (source `kz-qmdb`, `fetchers/qmdb.py`), and the gate will
then hold the margin to account. The same scratch run found the starts there short too, which this
end-of-eating round could not touch (no start may move): Fajr 1 min early on 7 days at Astana in July
2027 and up to 7 min late at Petropavl (over the 5 min limit on 92 days), Isha 1 min early on 4 days
at Petropavl, Kokshetau and Pavlodar in August and up to 5 min late (over its 4 on 29 days). The
fitter asks for Fajr −2 → +8 s and Isha −3 → +11 s (first written here as + 14 s, a misreading of
the fitter's output); those starts got their own fix round the same day (*The starts north of
Astana*, below), and the northern rows are now in the gate. North of Petropavl was held to account on
6 Oct 2026 (*North of Petropavl, the west and the south*, below): the end of eating there needed the
curves read at each place's own latitude, not a deeper margin.

`bd.ifb`'s October digitization (`off-ifb-dhaka.txt`) stays `F`: IFB's fast ends at its printed
sehri, 6 min before its printed Fajr, which the year-round row checks (`E`); checked once against
the October rows' own Fajr, outside the gate, the end came 9–10 min before it, never after.

## The starts north of Astana (3 Oct 2026, the northern round)

**Evidence.** The kz-qmdb monitor's capture of 3 Oct 2026 (QMDB's year API, 2026 and 2027 at six
points) is pinned in the archive as fetched: tables in `archive/tables/pinned/kz-qmdb/2026-10-03/`,
raw JSON in `archive/raw/monitor/kz-qmdb/2026-10-03/`. Its Almaty and Astana 2026 tables equal
`off_ala`/`off_ast` day for day, so they get no second row; the other ten are new rows in
`kz-qmdb.tsv` (`F+E S D A M I`, as the end-of-eating audit set). The gate now holds 14 rows, 4,380
place-days at six places, 2026-01-01 to 2027-12-31, 354 of them in Ramadan.

**Fit/held-out.** Fit rows: the archived Almaty and Astana 2026, Astana 2027, and Kokshetau, Pavlodar
and Petropavl over both years (11 rows, 3,285 place-days); they had already decided the end of
eating's −374 s. Held out: Almaty 2027 and Kostanay (53.2° N, the farthest west and its own zone,
`Asia/Qostanay`) over both years, 1,095 place-days. At the 2026-only margins the fit rows had Fajr
1 min early on 7 July days at Astana in 2027 and Isha 1 min early on 4 August days at Petropavl,
Kokshetau and Pavlodar, and the held-out rows one more Fajr. The fitter (safety 5 s):

| event | fitted (s) | was | decided by |
| --- | --- | --- | --- |
| fajr | +8 | −2 | 19 Jul 2027 |
| sunrise | +14 | +19 | 30 Nov 2026 |
| dhuhr | −22 | −22 | 14 Mar 2026 |
| asr (Hanafi) | −8 | −11 | 22 Aug 2027 |
| maghrib | −15 | −17 | 7 Aug 2027 |
| isha | +11 | −3 | 15 Aug 2027 |
| end of eating (≥ 48° N) | −374 | −374 | 6 May 2027 |

All taken as fitted: every start later, sunrise earlier (it had 0 s of safety left at + 19 s, Asr
2 s, Maghrib 3 s). The margins stay one set for both variants, so Almaty's starts move by the same
seconds. On the held-out rows with these margins: 0 early, 0 late ends.

**Lateness, by city (both years, minutes after QMDB's printed start; superseded by the north-west
round's table below):**

| city | lat | Fajr worst | Isha worst | other starts | end of eating, worst min before |
| --- | --- | --- | --- | --- | --- |
| Almaty | 43.2 | 1 | 1 | 1 | 1 |
| Astana | 51.1 | 5 | 5 | 1 | 8 |
| Pavlodar | 52.3 | 6 | 5 | 1 | 8 |
| Kostanay (held out) | 53.2 | 6 | 5 | 1 | 8 |
| Kokshetau | 53.3 | 6 | 5 | 1 | 7 |
| Petropavl | 54.9 | 8 | 5 | 1 | 7 |

The residual is the AngleBased curve's, as at Astana, and it deepens to the north: Fajr from late
April to early June (deepest in early to mid May; 8 min only at Petropavl, on 5 days of 2027), Isha
from July to mid-August. A flat margin cannot take it out without making the other days early, so
both are recorded (R41): Fajr 8 min (was 5) and Isha 5 min (was 4), each with its reason in
`CentralAsia.kt`. Neither comes near 10 min. Sunrise, Dhuhr, Asr and Maghrib stay within 1 min.

**Gate (whole, 838 rows, 161,501 place-days):** 0 early, 0 late ends, none over its limit, nothing
BROKEN. Only `kz.qmdb`'s stamp changed.

**What the About screen says.** `kz.qmdb` has no units: its stamp is the entry's, and the screen
shows it at every Kazakh place. It now reads 4,380 days at 6 places, checked through 31 Dec 2027,
"at most 8 min after" (the worst start over all six). That is true at each measured city; at Almaty,
whose own worst is 1 min, it overstates the lateness, the safe direction for a claim. A per-city figure
would need units (`worstLateByUnit`), which the registry gives only to authorities that print a table
per point; QMDB computes one rule for any point, so it stays one entry.

**Still not proven** (as of this round; closed on 6 Oct 2026 by the next section). North of Petropavl
(to 55.4° N) every margin was an extrapolation, and the west (Oral, Aktobe, Atyrau, Aktau) had no table.

## North of Petropavl, the west and the south (6 Oct 2026, the north-west round)

**Evidence.** QMDB's year API answers only at its own places, so its city list
(`api.muftyat.kz/cities/`: 5,694 places, every one on UTC+5) was read first and kept in the archive
(`archive/raw/manual/kz-qmdb/2026-10-06/cities.json.gz`). The kz-qmdb fetcher, its parsing unchanged,
read 2026 and 2027 at 23 more of QMDB's own places, pinned as fetched in
`archive/tables/pinned/kz-qmdb/2026-10-06/` (raw JSON in `archive/raw/monitor/kz-qmdb/2026-10-06/`):

- north of Petropavl to the country's edge: Isakovka (55.4° N, the northernmost of QMDB's list),
  Krasny Yar and Kulomzino;
- the west and every zone of the app's Kazakh cities that had no table: Oral and Aksay (`Asia/Oral`),
  Aktobe, Khromtau and Shalqar (`Asia/Aqtobe`), Atyrau, Makat and Oteshqali Atambayev (`Asia/Atyrau`),
  Aktau (`Asia/Aqtau`), Kyzylorda (`Asia/Qyzylorda`);
- the south: Shymkent and Zhenis (40.6° N, the southernmost of QMDB's list);
- from 46° to 48° N: Oteshqali Atambayev (QMDB's place nearest 48° N) and Ayagoz, with Shalqar,
  Atyrau, Mamyrsu and Makat;
- places just either side of the engine's 0.1° curve steps (below): Vagulino, Bugrovoe, Pulemetovka,
  Spasovka, Khromtau, Arkalyk, Oskemen (Ust-Kamenogorsk), Mamyrsu, Makat, and Kulomzino above.

The first six places came back day for day as pinned on 3 Oct. Each table's Maghrib less its own
sunset gives QMDB's minutes: 5 at every place at or above 48° N, 3 below it, Shalqar (47.8° N, in the
Aktobe region) included, so QMDB switches by latitude, as the engine does.

**Where the engine stood** (the margins of 3 Oct, every new row held out). No start was early at the
fourteen places north, west, south and from 46° to 48° N. Isha went 1 min over its 5-min limit (6) at
Isakovka (3 days) and Krasny Yar (1), in August 2027. The end of eating came after QMDB's printed Fajr
at Kulomzino (2 days of spring 2027, 1 min) and, where its margin was 0 from 46° to 48° N, at Atyrau
(9 days, 1 min), Makat (15, up to 2), Shalqar (27, up to 2), Oteshqali Atambayev (27, up to 3), Ayagoz
(35, up to 3) and Mamyrsu (35, up to 3), from late May to mid-June of both years: QMDB's AngleBased
rule binds there too around the solstice, from about 46.15° N. The west at and above 48° N (Oral,
Aksay, Aktobe, Khromtau), the south (Shymkent, Zhenis), Aktau and Kyzylorda were green within their
recorded limits.

**The curve step.** QMDB's AngleBased rule is carried as a day-of-year angle curve computed on a 0.1°
latitude grid (`TwilightCurves` rounds the latitude), and in the north, while the rule binds, its
moment moves by up to about a minute for every 0.1°. A place just short of a step reads its curve up to
0.05° to its south, which puts its Fajr early; one just past a step reads it 0.05° to its north, which
puts its end of eating late (Kulomzino, above). Nine of QMDB's own places within 0.003° of a step were
fetched and gated at their own points (and, outside the gate, 0.001° across their step): Fajr 1 min
before QMDB's printed one at QMDB's own Bugrovoe (14 days), Spasovka (11) and Oskemen (2), from the
solstice to mid-August; the end of eating 1 min late at Vagulino (2 days), as at Kulomzino; Isha 6 min
at Bugrovoe and Spasovka. Any point just short of a step in the north was in the same case.

**The fix** (kz.qmdb only; every time moves later, or for an end earlier, or not at all):

1. QMDB's Fajr and Isha curves are also computed at the place's own latitude; each slot takes the later
   start of the grid's curve and the place's own, and the end of eating, read from the Fajr dawn, the
   earlier dawn of the two (`TwilightCurves.kt`, the qmdb rule's `ownLatitude`; no other entry's curves
   change). Across a step the probes are now green on both sides.
2. From 46° to 48° N, an end-of-eating margin of −150 s (`CentralAsia.kt`): the fitter's bound at the
   band's top, Oteshqali Atambayev and Ayagoz, decided on 30 May 2027. Below 46° N the rule never binds
   and the margin stays 0.
3. The fitter over the fit rows (Isakovka and Krasny Yar at the edge, Oteshqali Atambayev and Ayagoz at
   the band's top, and the eleven of 3 Oct) moved Asr from −8 to −6 s (decided at Krasny Yar, 1 Sep
   2027) and Maghrib from −15 to −14 s (Isakovka, 13 Jan 2027), both later. Fajr (fitted −4 s) and Isha
   (+11 s) stay at +8 and +11, Dhuhr at −22, sunrise at +14. At and above 48° N the end of eating's
   −374 s stands: with the curve change the fitter asks −366 s (Isakovka, 3 May 2027).
4. Isha's limit goes from 5 to 6 min (R41), with its reason; Fajr's 8 and the end of eating's 8 stand,
   their reasons now naming the band and Isakovka.

**Lateness, by place, after the fix** (both years, minutes after QMDB's printed start; the end of
eating in minutes before the printed Fajr; `fit` rows decide the margins, `test` rows are held out):

| place | lat | zone | row | Fajr | Isha | other starts | end of eating |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Zhenis | 40.6 | Almaty | test | 1 | 1 | 1 | 1 |
| Shymkent | 42.4 | Almaty | test | 1 | 1 | 1 | 1 |
| Almaty | 43.2 | Almaty | fit (2027 test) | 1 | 1 | 1 | 1 |
| Aktau | 43.6 | Aqtau | test | 1 | 1 | 1 | 1 |
| Kyzylorda | 44.8 | Qyzylorda | test | 1 | 1 | 1 | 1 |
| Atyrau | 47.1 | Atyrau | test | 3 | 2 | 1 | 4 |
| Makat | 47.6 | Atyrau | test | 3 | 3 | 1 | 4 |
| Shalqar | 47.8 | Aqtobe | test | 4 | 3 | 1 | 4 |
| Mamyrsu | 47.95 | Almaty | test | 4 | 3 | 1 | 4 |
| Ayagoz | 47.97 | Almaty | fit | 4 | 3 | 1 | 4 |
| Oteshqali Atambayev | 47.99 | Atyrau | fit | 4 | 3 | 1 | 4 |
| Oskemen | 49.9 | Almaty | test | 5 | 5 | 1 | 8 |
| Arkalyk | 50.2 | Qostanay | test | 5 | 5 | 1 | 8 |
| Khromtau | 50.3 | Aqtobe | test | 6 | 4 | 1 | 7 |
| Aktobe | 50.3 | Aqtobe | test | 6 | 4 | 1 | 7 |
| Astana | 51.1 | Almaty | fit | 6 | 5 | 1 | 8 |
| Aksay | 51.2 | Oral | test | 6 | 4 | 1 | 8 |
| Oral | 51.2 | Oral | test | 6 | 4 | 1 | 7 |
| Pavlodar | 52.3 | Almaty | fit | 7 | 5 | 1 | 8 |
| Kostanay | 53.2 | Qostanay | test | 6 | 5 | 1 | 8 |
| Kokshetau | 53.3 | Almaty | fit | 6 | 5 | 1 | 8 |
| Spasovka | 54.3 | Almaty | test | 7 | 6 | 1 | 8 |
| Pulemetovka | 54.6 | Almaty | test | 8 | 5 | 1 | 8 |
| Petropavl | 54.9 | Almaty | fit | 8 | 6 | 1 | 8 |
| Bugrovoe | 55.0 | Almaty | test | 8 | 6 | 1 | 8 |
| Vagulino | 55.2 | Almaty | test | 8 | 6 | 1 | 8 |
| Kulomzino | 55.3 | Almaty | test | 8 | 6 | 1 | 8 |
| Krasny Yar | 55.4 | Almaty | fit | 8 | 6 | 1 | 8 |
| Isakovka | 55.4 | Almaty | fit | 8 | 6 | 1 | 8 |

Reading the curve at the place's own latitude costs a minute here and there, where the grid had read
a place's curve to its south (Fajr) or north (Isha, the end of eating): Fajr at Astana (5 → 6),
Pavlodar (6 → 7) and Shalqar (3 → 4), Isha at Petropavl, Kulomzino and Vagulino (5 → 6), and the end
of eating at Kokshetau, Petropavl and Krasny Yar (7 → 8 min early). From 46° to 48° N Fajr is up to 4 min late
and the end of eating up to 4 min early, over class D's plain 3, both within the entry's recorded
limits (8, 8), whose reasons now say so.

**Gate (whole, 966 rows, 206,365 place-days, the committed stamps; this Mac's unpulled archive, whose
ly-awqaf and tn-inm live files are older than the cloud's, gives 206,322):** 0 early, 0 late ends,
none over its limit, nothing BROKEN. `kz.qmdb`: 60 rows, 21,170 place-days at 29 places, 2026–2027 (14,965 held out, 1,711 in
Ramadan); held out alone, 0 early and 0 late ends. Only `kz.qmdb`'s figures changed. The golden vector
moved on 3 of its 2,071 rows, all at the Kazakh seeds and all the same field, the other school's
(Standard) Asr, 1 min later: the Almaty seed on 8 Jan 2030, the Astana seed on 19 Jan 2029 and
31 Oct 2027 (the Asr margin).

**What the About screen says.** `kz.qmdb` still has no units, so the screen shows the entry's stamp at
every Kazakh place: now 21,170 days at 29 places, checked through 31 Dec 2027, "at most 8 min after"
(Fajr, from 54.5° N north). In the south, whose worst is 1 min, it overstates, the safe direction for a
claim.

**Still not proven.** No Kazakh city in the app's list now lies north of the northernmost table
(Petropavl, at 54.87° N in the list, is 60 km south of Isakovka) or in a zone without one: all 84 are
within the measured latitudes (40.6° N to 55.4° N) and zones. What remains: QMDB prints only for its
own places, and the engine computes at the user's own point, so a user between them is checked through
the nearest tables rather than their own; the residual of QMDB's AngleBased curve (its envelope over
neighbouring days and its shorter night) keeps Fajr up to 8 min and Isha up to 6 min late in the north,
and the end of eating up to 8 min early; and 2028 is not held (the monitor fetches all 29 places each
month, and `prove` adds a new year's rows by itself, the source now having its recipe).

## The city points (9 Oct 2026, ruling R44)

**Why.** QMDB prints a table for each of its 5,694 places, and a city's mosques follow the city's. The
engine computed the user's own point. On `main` (705c4f7f), replaying each gate row's table at the app's
own point for the same city (`cities.csv`) found early starts at Oral (3.8 km east of QMDB's point: 121
cells on 116 days of 2026–27, Dhuhr above all), Aktobe (3.7 km east: 70), Astana (14) and Pavlodar (8),
and 4 sunrises after the table at the app's Ust-Kamenogorsk (QMDB's Oskemen, 3 km away).

**The units** (`CentralAsia.kazakhstanUnits`, `data/QmdbPlaces.kt`). QMDB's place for every city of the
app's list is a unit at QMDB's own point: its "<name> қаласы" city entry where it lists one, else the
locality of that name. So are the gate's 29 places, 87 in all. QMDB's point rides beside the user's (R15:
starts the later, ends the earlier), within three minutes of longitude (R40 for class D: 48 to 59 km).
- **Its band.** The unit's method carries its own point's band, QMDB's ±5 at and above 48° N and ±3
  below, with the end-of-eating margin of 46° to 48° N and of the north. The user's own band applies on
  top, and each band's end-of-eating margin now keeps the earlier of the two (`min`). So where a unit
  reaches across 46° or 48° N, as Shalqar's, Ayagoz's, Zhezkazgan's, Satpayev's, Baikonur's and five
  more do, both bands' times bound the day.
- **Its curves.** The AngleBased curves are read at the user's latitude as well as the unit's, on the
  grid and at each latitude's own value (`PlaceCurves.at`'s `also`). Each slot takes the later start,
  and the end of eating the earlier dawn. A unit reaches about half a degree north and south of its
  point, and while the rule binds it moves by up to a minute a tenth of a degree.

So no start is earlier, and no end later, than the same method at the user's own point without units.
`RegistryCitiesTest` checks this at every Kazakh city of the app's list, every ninth day of 2026,
through the app's engine. It holds whichever table the user's mosque reads, the city's or a village's
own.
- **Five without a table.** Shchuchinsk, Zhitikara, Esik, Abay (Karaganda region) and Abay (Turkistan
  region) share their point in QMDB's list with another of its places (each city and a locality of
  the same name, Abay's Duanshy, and the Turkistan Abay twice). There QMDB's year API answers HTTP 500
  (9 Oct 2026), so it publishes no table to check. These are units that are not measured
  (`QmdbPlaces.unanswered`), so no neighbour's figure (Makinsk's, 37 km from Shchuchinsk) is claimed
  there.
- **Beyond every unit**, the user's own point as before, with the end of eating at SAFE_END below 46° N
  (R44), and no figure. Only Shalkar in the Atyrau region, whose nearest QMDB place is another village
  16 km off, is beyond every unit among the app's 84 cities.

**Evidence.** The kz-qmdb fetcher read 2026 and 2027 at the 58 new places on 9 Oct 2026, pinned under
`archive/tables/pinned/kz-qmdb/2026-10-09/` with the raw JSON in `archive/raw/monitor/kz-qmdb/2026-10-09/`.
The 29 places held before came back day for day. Every row is held out. At their own points, 116
tables and 42,340 place-days, the margins of 6 Oct gave 0 early, 0 late ends and none over the limits.
At the app's own points, 78 cities, 158 rows and 56,940 place-days against the unit's tables
(`kz.qmdb/<unit>`), there were 0 early and 0 late ends. Gated against a neighbour's tables before those
units existed, Shchuchinsk went over the north's limits against Makinsk's (Fajr 9 min, Isha 8, the end of
eating 9 early) and Abay (Karaganda) against Saran's at the end of eating: a neighbour's table, not theirs,
and now not claimed.

| all 334 rows, 120,450 place-days | early / late end | 0 | 1 | 2 | 3+ | exact |
|---|---|---|---|---|---|---|
| Fajr | 0 | 39,433 | 74,932 | 1,584 | 4,501 | 32.7 % |
| sunrise | 0 | 86,973 | 33,400 | 77 | 0 | 72.2 % |
| Dhuhr | 0 | 103,166 | 17,284 | 0 | 0 | 85.7 % |
| Asr (Hanafi) | 0 | 71,219 | 49,078 | 153 | 0 | 59.1 % |
| Maghrib | 0 | 86,905 | 33,483 | 62 | 0 | 72.2 % |
| Isha | 0 | 33,628 | 76,683 | 5,392 | 4,747 | 27.9 % |
| end of eating | 0 | 26,573 | 28,188 | 1,510 | 64,179 | 22.1 % |

The limits are unchanged: Fajr 8, Isha 6, end of eating 8, class D's 3 elsewhere. No margin changed.

**About, per city** (the worst start of the city's own unit, over its own and the app's point; before
this round every Kazakh place read the entry's 8):

| figure | cities |
|---|---|
| 1 | Taraz, Turkistan, Taldykorgan, Zhanaozen, Sarkand, Kentau, Talgar, Konaev, Shu, Karatau, Zhetysay, Arys, Novokazalinsk, Zharkent, Zhanatas, Mangystau, Shiyeli, Shelek, Aksu (Turkistan), Shardara, Saryagash, Zhanakorgan, Lenger, Burunday, Ushtobe, Zhosaly, Otegen Batyr, Turar Ryskulov, Merke, Sarykemer |
| 2 | Almaty, Shymkent, Kyzylorda, Aktau, Balkhash, Tekeli, Makanshy, Tasbuget |
| 3 | Atyrau, Kulsary, Aral, Balykshi |
| 4 | Zhezkazgan, Baikonur, Satpayev, Ayagoz, Shalqar, Embi, Zaysan |
| 5 | Karaganda, Altay, Saran, Shakhtinsk, Kandyagash, Kalbatau |
| 6 | Astana, Aktobe, Oral, Ust-Kamenogorsk, Semey, Temirtau, Ekibastuz, Ridder, Aksu (Pavlodar), Arkalyk, Lisakovsk, Aksay, Atbasar, Khromtau, Shemonaikha, Makinsk |
| 7 | Pavlodar, Kostanay, Kokshetau, Rudny, Stepnogorsk, Akkol |
| 8 | Petropavl |
| none | Shchuchinsk, Zhitikara, Esik, both Abay (no table), Shalkar (beyond every unit) |

**The edges.** At 95 % of every unit's radius, north, east, south and west (CityUnitsTest, the archive's
tables), there are 0 early and 0 late ends. The start runs up to the class's three minutes after the
city's table, plus its own lateness and, in the north, the AngleBased rule's change with latitude:
Dhuhr and Asr up to 4 min, Maghrib 5, Isha 11, Fajr 12, and the end of eating up to 15 min early. About
shows the unit's figure, measured at its point and the app's, so near a unit's north or south edge in
summer the figure reads better than the times run (ruling R40's cost, larger for class D's three
minutes than class A's one). A one-minute reach would still leave Dhuhr up to 3 and Fajr up to 9 at its
edges, and would push Baikonur (39 km from QMDB's own point) out of its unit.

**Not proven.** A village with its own QMDB table inside a city's unit is checked only through the
city's table. The five cities QMDB lists twice have no table to check. 2028 waits for the monitor: the
recipe adds new years at the 87 points, not at the app's points.

## Verification run

```
./gradlew -p tools/timetables jvmTest gate -PgateGroup=pk-karachi,in-karachi,bd-ifb,uz-board,kz-qmdb,kg-default
  → BUILD SUCCESSFUL — 0 early, 0 late ends, 0 over the limit, 942 place-days, 6 stamps written
./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.prayer.engine.*'
  → BUILD SUCCESSFUL
./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.prayer.engine.registry.AsiaProofTest'
  → 6 tests, 0 failures

# the northern round, 3 Oct 2026
./gradlew -p tools/timetables gate -Pentry=kz.qmdb -Pfit=kz.qmdb -Pstamps=false
  → fit on 11 rows (3,285 place-days); held out 3 rows (1,095): 0 early, 0 late ends
./gradlew -p tools/timetables gate
  → 838 rows, 161,501 place-days: 0 early, 0 late ends, none over its limit, nothing BROKEN
./gradlew -p tools/timetables jvmTest       → 281/0, with the archive
./gradlew -p tools/timetables checkStamps   → 71 entries, every stamp fresh and green
./gradlew :shared:testAndroidHostTest --tests '*Registry*' --tests '*GoldenVector*' --tests '*ProofStamps*' --tests '*AboutTimes*'
  → 110/0 (and AsiaProofTest 6/0)

# the north-west round, 6 Oct 2026 (the cloud's 9 Kemenag tables of 5 Oct read from the private
# repository, the local copy not yet holding them)
./gradlew -p tools/timetables gate -Pentry=kz.qmdb -Pfit=kz.qmdb -Pstamps=false
  → fit on 19 rows (6,205 place-days): asr -6, maghrib -14, end of eating -366 (the north)
./gradlew -p tools/timetables gate
  → 966 rows, 206,365 place-days (the committed stamps; 206,322 on the unpulled local archive):
    0 early, 0 late ends, none over its limit, nothing BROKEN
./gradlew -p tools/timetables jvmTest       → 297/0, with the archive
./gradlew -p tools/timetables checkStamps   → 71 entries, every stamp fresh and green
./gradlew :shared:testAndroidHostTest       → 1,604/0 (AsiaProofTest 8/0)
python3 -m unittest tools/timetables/monitor/tests.py  → 77/0 (3.13 and the Mac's 3.9)

# the city points, 9 Oct 2026
./gradlew -p tools/timetables gate -PgateGroup=kz-qmdb -Pstamps=false
  → 334 rows, 120,450 place-days: 0 early, 0 late ends, none over its limit
./gradlew -p tools/timetables gate
  → 1,771 rows, 501,433 place-days: 0 early, 0 late ends, none over its limit, nothing BROKEN
./gradlew -p tools/timetables jvmTest       → 300/0, with the archive (CityUnitsTest 3/0)
./gradlew -p tools/timetables checkStamps   → 71 entries, every stamp fresh and green
./scripts/test.sh                           → shared 1,611/0 Android host, 1,488/0 iOS (AsiaProofTest 9/0)
python3 -m unittest tests prove_tests (tools/timetables/monitor) → 105/0 (3.13 and the Mac's 3.9)
```

## Concerns (for the controller / other subtasks)

1. **Bangladesh has no per-district units**, but the research documents districts several minutes
   off Dhaka's own table. The flat 60 s "district edge" (Task 5's choice, kept here) does not cover
   that. The smallest fix: give `bd.ifb` a `UnitSet` (Dhaka plus the documented outliers), the way
   `Units.of` already does for JAKIM/Kemenag/Habous, so the class-limit reach (`lateReachKm`) is the
   one already built for this. Out of this subtask's ownership (no unit set exists to edit without
   adding one, which R5 does not list).
2. **`off_mkh.txt`**, sitting beside the Kazakhstan/Uzbekistan captures in
   `archive/tables/moral-auth-world/centralasia/`, is not Central Asia at all: its coordinates
   (42.98° N, 47.50° E) and its companion `islamdag_mkh.html` title ("Время намаза в Махачкале")
   identify it as Makhachkala, Dagestan — subtask 7b's Russia section, not this one's. Left
   untouched; flagged so 7b knows it is already archived, mis-filed only by folder.
3. **`archive/tables/moral-auth-world/centralasia/off_kzn.txt`/`off_kzn_mosque.txt`/`off_msk*.txt`**
   in the same folder are DUM RT (Kazan)/DUM RF (Moscow) captures, also not this subtask's — left
   untouched, likely already 7b's own copies via the `open/RU-DUMRT/` set (`off_kzn.txt` is
   byte-identical in both places).
4. **India and Kyrgyzstan stay thin by construction** (one month/one source; one day/one place):
   correct per the data, but a future pass with more captures (more Indian cities, more Kyrgyz
   localities across seasons) could move either toward class B once the class rules' "unverified
   season" caveat no longer applies.
