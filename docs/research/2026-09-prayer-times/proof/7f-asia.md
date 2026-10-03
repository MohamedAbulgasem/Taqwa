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
−11, maghrib −17, isha −3.

**Gate (all 730 place-days, two cities):** 0 early, 0 late ends. Fajr (worst 5, mid-May, Astana)
and Isha (worst 4, mid-July, Astana) exceed 3 min: both carry a `LateLimit` exception, since the
residual is the AngleBased curve's own (already fitted as tightly as a flat margin allows; a
further reduction would need a change inside `TwilightCurves`' `kz.qmdb` section itself, which
this subtask left alone as it already reproduces QMDB's documented rule and the residual is small).
Sunrise, Dhuhr, Asr and Maghrib stay at 1 min.

**Not proven:** only two of QMDB's 5,694 API places are measured (Almaty, Astana); `measured =
true` (kept from Task 5) claims the whole country on the strength of the documented, reproduced
algorithm plus these two points, not a nationwide fit.

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
fitter asks for Fajr −2 → +8 s and Isha −3 → +14 s; those starts need their own fix round before the
northern rows can be added green.

`bd.ifb`'s October digitization (`off-ifb-dhaka.txt`) stays `F`: IFB's fast ends at its printed
sehri, 6 min before its printed Fajr, which the year-round row checks (`E`); checked once against
the October rows' own Fajr, outside the gate, the end came 9–10 min before it, never after.

## Verification run

```
./gradlew -p tools/timetables jvmTest gate -PgateGroup=pk-karachi,in-karachi,bd-ifb,uz-board,kz-qmdb,kg-default
  → BUILD SUCCESSFUL — 0 early, 0 late ends, 0 over the limit, 942 place-days, 6 stamps written
./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.prayer.engine.*'
  → BUILD SUCCESSFUL
./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.prayer.engine.registry.AsiaProofTest'
  → 6 tests, 0 failures
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
