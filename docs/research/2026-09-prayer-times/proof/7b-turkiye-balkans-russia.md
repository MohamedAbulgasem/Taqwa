# Proof 7b: Türkiye, the Balkans and Russia

Task 7b, 27 September 2026. Every figure below comes from the gate
(`./gradlew -p tools/timetables gate -PgateGroup=…`) over the tables named in each group's
`tools/timetables/official/gate/<group>.tsv`, on the engine of `prayer-engine` at 25fd215 with
Task 7b's engine commits (ruling R58: d815498 SunClock, 915908f DUM RT's rule by night; ruling R61:
420cfe4 DUM RT's nights too close to call; ruling R65: DUM RT's edge across a band of latitude) and
the registry of this branch. Statistics only: no printed time from a restricted table appears here.

How to read the tables: *days* are checked cells; *early* is a start shown before the printed
minute, *late end* a sunrise or end of eating shown after it (both must be 0); +0 … +3+ count the
minutes on the safe side (late for a start, early for an end); *worst* is the largest.

| Entry | Class | Place-days (held out) | Early / late ends | Worst late (min) | Gate |
|---|---|---|---|---|---|
| `tr.diyanet` units | A | 5,148 (3,168) | 0 / 0 | 1 | green |
| `tr.diyanet` edge | D | 3,564 (3,564) | 0 / 0 | 2 | green |
| `other.turkey` | D | 2,376 (2,376) | 0 / 0 | 3 | green |
| `tr.diyanet.europe` | D | 4,752 (2,376) | 0 / 0 | 3; 4–8 by exception | green |
| `ba.iz` | B | 1,095 (2) | 0 / 0 | 2; 3 by exception | green |
| `xk.bik` | D | 730 (365) | 0 / 0 | 3; 4–6 by exception | green |
| `al.kmsh` | A at Tirana, D beyond | 780 (415) | 0 / 0 | 1 at Tirana, 2 beyond | green |
| `me.izcg` | D, no figure | 30 (30) | 0 / 0 | 3 | green |
| `ru.dumrt` | B | 6,935 (6,570) | 0 / 0 | 2; 3–132 by exception (R61) | green |
| `ru.dumrf` | D | 107 (0) | 0 / 0 | 2; 4–5 by exception | green |
| `ru.dumrd`, `ru.bashkortostan` | D | no table held | — | — | not checked |

## Tatarstan: DUM RT (`ru.dumrt`)

**Data.** DUM RT's 2026 xlsx (CC BY 4.0) prints 44 localities. Kazan's two files were already open;
this task adds the other 18 localities the app lists (`open/RU-DUMRT/dumrt-2026-<town>.txt`, every
column as printed). Fit on Kazan; the 18 towns held out (6,570 place-days).

**Its white nights are a rule by night, not by date.** At every one of the 19 towns the 2026 table
prints sahur = sunrise − 121 on exactly the mornings the sun does not reach 18° at that town
(computed with the exact sun at the town's app point), and Isha = Maghrib + 90 on the evening before
each such morning. So the window moves with latitude (Agryz 3 May–10 Aug, Kazan 6 May–8 Aug, Bugulma
10 May–3 Aug) and with the leap cycle (Kazan's first white morning is 6 May in 2026, 2027, 2030 and
2031, and 5 May in 2028, 2029, 2032 and 2033).

**Kazan, 5 May 2026.** The exact sun reaches 18.054° at 23:39:55 on the 4th, so the 18° dawn exists
(at 23:53:10) and DUM RT prints it (23:54). `SunClock.altitudeTime` (EXACT) used to evaluate the
crossing first with the declination at noon of the 5th, 0.14° higher, find none and return null;
the night then counted as missing, and the estimate and the 20 min/day limit gave 00:33 (39 min
late) and Fajr 02:08 (134 min late).

**Two engine changes (ruling R58), each its own commit:**

1. d815498 `SunClock.altitudeTime`, EXACT: when the noon-seeded iteration finds no crossing, the
   crossing is looked for by bisection on the sun's own altitude between the lower culmination and
   transit; null only when the sun at its lowest stays above the altitude. `SunClockTwilightWalkTest`
   checks the verdict against a 30 s scan of the sun's altitude on every night of 2026 and 2028 at
   44–66° N in half degrees, three longitudes, 12°, 15°, 17°, 18° and 19.5°, mornings and evenings:
   986,772 verdicts, 78 left uncalled (the lowest sun within 0.001° of the depression), 0 failures,
   every found crossing within 5 s of the scan's. The old code failed it (the first at 47.5° N,
   19.5°, in June). It runs on the JVM host only (about 6 s). `HighLatitudeYearWalkTest` stays
   green.
2. 915908f `HighLatRule.DumRtSummer`: sahur = sunrise − 121 and Fajr on the mornings whose 18° dawn
   is missing at every point of the place, Isha = Maghrib + 90 on the evening before; the fixed dates
   are gone. Checked against the altitude scan at all 19 towns, April to September of 2026 and of
   2028 (Kazan's window opens on 5 May in 2028).
3. Ruling R61, DUM RT's nights too close to call: each time takes its safe side of the rule and
   the real sign. Fajr is the later of sunrise − 121 and the real dawn, the end of sahur the earlier
   of sunrise − 121 and the sun's lowest point (a sun that only just reaches 18° dawns there, so no
   table whose sun reaches it prints its dawn before that), and on the evening before Isha is the
   later of Maghrib + 90 and the real 15° dusk. A night is too close to call at a unit's town when
   the sun's lowest is within 0.01° of 18° (where DUM RT's sun and ours may fall either side: in
   2026 Zelenodolsk's 5 May, 17.998°, and Yelabuga's 8 Aug, 18.008°; one to four nights a year among
   the 19 towns through 2035); beyond the units, where the rule is read at the user's own point, when
   it is within 0.2° (the locality DUM RT would print for may lie 20 km away; two to four nights a
   year). In a unit the town's own point decides, beside the user's (the review found no night on
   the wrong side of a town's table anywhere within its 25 km).

A fixed window could not work in any year: a window morning the table does not have prints sunrise −
121 about 2 h after the real 18° dawn (a late end); a table window morning outside it gets the
estimate, whose end of eating the rate limit lets run up to 17–20 min after sunrise − 121 at the end
of the window, and whose Fajr comes before it at the start. With Kazan's 2026 dates at every town,
Isha was up to 62 min early at the southern towns (Almetyevsk 5–7 May, Naberezhnye Chelny and
Nizhnekamsk 7 Aug) and the end of sahur up to 121 min late.

| DUM RT, 19 towns | days | early | late end | +0 | +1 | +2 | +3+ | exact | worst |
|---|---|---|---|---|---|---|---|---|---|
| before (fixed dates): fajr | 6,935 | 0 | – | 1,055 | 4,478 | 1,312 | 90 | 15.2% | 137 |
| before (fixed dates): isha | 6,935 | **69** | – | 5,184 | 1,670 | 0 | 12 | 74.8% | 71 |
| before (fixed dates): end of eating | 6,935 | – | **78** | 3,098 | 3,733 | 18 | 8 | 44.7% | 88 |
| fajr | 6,935 | 0 | – | 1,071 | 4,537 | 1,322 | 5 | 15.4% | 126 (Yelabuga 8 Aug, R61); else 3 (Zainsk), by exception |
| sunrise | 6,935 | – | 0 | 4,880 | 2,055 | 0 | 0 | 70.4% | 1 |
| dhuhr | 6,935 | 0 | – | 6,935 | 0 | 0 | 0 | 100% | 0 |
| asr (Hanafi) | 6,935 | 0 | – | 1,480 | 5,297 | 158 | 0 | 21.3% | 2 |
| maghrib | 6,935 | 0 | – | 5,300 | 1,635 | 0 | 0 | 76.4% | 1 |
| isha | 6,935 | 0 | – | 5,245 | 1,689 | 0 | 1 | 75.6% | 62 (Zelenodolsk 4 May, R61, by exception); else 1 |
| end of eating | 6,935 | – | 0 | 3,130 | 3,772 | 26 | 7 | 45.1% | 132 (Zelenodolsk 5 May, R61); else 12, 3, 3, by exception |

Held out alone (the 18 towns, 6,570 place-days): the same shape, 0 early, 0 late ends. Dhuhr is exact
on every day at every town (12:00, 11:45, 11:50, 12:05 and 12:15 as printed).

**Margins** (fitted on Kazan, widened by the held-out towns' excess plus 5 s): Fajr + 16 s, sunrise + 37 s, Asr + 78 s,
Maghrib + 21 s, Isha + 13 s, end of sahur − 34 s (the brief's −8 s refit becomes −34 s once the 18
towns are held out: Bavly's 3 August, a night the sun only just reaches 18°). Zainsk's table runs
20–50 s later than its app point gives and keeps its own starts (Fajr + 77, Asr + 101, Maghrib + 45,
Isha + 24).

**Recorded exceptions, each on its own unit.**
- Zelenodolsk, 5 May 2026 (R61): the end of sahur at the sun's lowest point, 132 min before the
  sunrise − 121 DUM RT prints; on the evening before, Isha the real 15° dusk, 62 min after its
  Maghrib + 90.
- Yelabuga, 8 Aug 2026 (R61): Fajr at sunrise − 121, 126 min after the real dawn DUM RT prints, and
  the end of sahur at the sun's lowest, 8 min before it. The next night's dawn is printed 24 min
  later and the daily limit lets the end shown move 20 min, so it stays 12 min before the printed
  time on 9 Aug (end of sahur up to 12 min).
- Bavly (4 Aug) and Zainsk (6 May, 7 Aug): the end of sahur up to 3 min early on a night whose dawn
  lies minutes from the sun's lowest point, where the dawn moves by minutes with the town's
  unpublished point.
- Zainsk's Fajr up to 3 min late: its own margin, set by 6 May, on other days.

**Beyond the 19 towns** (the edge, DUM RT's rule at the user's own point; it claims no figure).
The review compared it, 5.5–22 km from each town, with that town's own table (a stand-in for the
locality DUM RT would print for the user). Before R61 the nights within 0.2° of 18° fell on the
wrong side by up to two hours: Fajr 119 min early 5.5 km south of Kazan on 8 Aug, Isha 60 min
early and the end of sahur 123 min late 5.5 km north of Yelabuga on 7–8 Aug, likewise north of
Buinsk on 7–8 May. With R61 every one of those nights is on the safe side. The nights just outside
the band were not: there the real dawn lies within the hour after the sun's lowest point and moves
by minutes with every few km, so the end of sahur came up to 5 min after a town's table and Fajr up
to 3 min before it from 11 km away, and up to 14 and 9 min from 22 km.

Ruling R65 (as amended): at the edge, on every night, Fajr and Isha are the latest and the end of
sahur the earliest that a table printed anywhere within 0.2° of latitude would give (sampled every
0.05°, each sample its own table's point with R61's 0.01°), and never less safe than the point's own
day (with R61's band). First applied only within 0.5° of 18°, it left the nights just beyond that
wrong by up to 2 min from 11 km and 7 (the end of sahur) and 6 (Fajr) from 22 km, shrinking only
slowly with the threshold (3°: still 1 min at 4.3° from 18°), so it applies on every night. Against
the 19 towns' tables, 5.5, 11, 17 and 22 km north and south of each, every night from April to
September 2026: none is on the wrong side. The test pins Kazan and Yelabuga at 11 and 22 km on every
night of 20 April–12 May and 1–20 August.

What it costs, at the 19 towns' points and 11 and 22 km north and south of each over 2026 (34,675
place-days), against the band on the nights within 0.5° of 18° only (the time a band adds, on
nights more than 0.5° from 18°):

| Months | Fajr later | Isha later | End of sahur earlier |
|---|---|---|---|
| October–February | ≤ 1 min | ≤ 1 | ≤ 1 |
| March, June, July, September | ≤ 2 | ≤ 2 | ≤ 2 |
| April, May, August | ≤ 8 | ≤ 3 | ≤ 20 (Agryz + 0.2°, 13 Aug) |

Most days gain nothing or a minute (Fajr: 0 on 36%, 1 on 44%, 2 on 14%, 3 or more on 7%). The
April–May and August figures are the band doing its work: on those nights the locality 22 km off
prints its dawn that much earlier or later. Nothing is less safe than before. The edge's day costs
about 0.5 ms instead of 0.07 (nine table points a day).

**White-night Isha before the real 15° dusk.** Maghrib + 90 is DUM RT's own printed rule, so the app
follows it and marks Isha set by rule. It falls before the real 15° dusk on 450 of the 1,748 window
evenings across the 19 towns (22–26 a town), by up to 2 h 12 min (Naberezhnye Chelny); at Kazan on
23 evenings (5–15 May and 27 Jul–7 Aug), by up to 1 h 57 min. For Mohamed's report.

**Tatarstan's printed 12:00 Dhuhr** is exact at all 19 towns on every day; no late-limit exception is
needed. Whether 12:00 is a congregation time remains the spec's open question (§3.3, slice 2).

## Türkiye: Diyanet (`tr.diyanet`)

Tables: the 13 analysed districts (396 days each, 25 Sep 2026 – 31 Dec 2027) at their units' points;
nine more districts at the app's city points beyond every unit (Antalya, Bursa, Diyarbakır,
Gaziantep, İzmir, Konya, and İstanbul's Çatalca, Şile, Silivri). Fit on İstanbul, Ankara, Tekirdağ,
Edirne and Van (1,980 days); the rest held out.

Fitted with the Fitter's formula, the model needs starts at least 22–25 s before the minute and
sunrise and the end of eating 30 s after (Diyanet rounds to the nearest minute; this model is within
8 s of its own). The registry takes the research's 10 s of safety rather than the Fitter's 5, so that
a user a few kilometres east of their own ilçe's point inside İstanbul's or Ankara's 21 km reach is
still on the safe side: Fajr − 11 s, Dhuhr − 14, Asr − 12, Maghrib − 12, Isha − 14, sunrise and the
end of eating + 19 (the end of eating was + 0 before: floored at the raw dawn, up to 3 min early at
the edge).

| `tr.diyanet`, 13 units | days | early / late end | +0 | +1 | +2 | exact | worst |
|---|---|---|---|---|---|---|---|
| fajr | 5,148 | 0 | 3,763 | 1,385 | 0 | 73.1% | 1 |
| sunrise | 5,148 | 0 | 3,963 | 1,185 | 0 | 77.0% | 1 |
| dhuhr | 5,148 | 0 | 3,870 | 1,278 | 0 | 75.2% | 1 |
| asr | 5,148 | 0 | 3,915 | 1,233 | 0 | 76.0% | 1 |
| maghrib | 5,148 | 0 | 3,782 | 1,366 | 0 | 73.5% | 1 |
| isha | 5,148 | 0 | 3,915 | 1,233 | 0 | 76.0% | 1 |
| end of eating | 5,148 | 0 | 3,920 | 1,228 | 0 | 76.1% | 1 |

Held out alone (3,168 place-days): the same shape, 74–76% exact, worst 1. Class A at every unit.
At the nine districts beyond every unit (the edge, + 60 s on starts, − 60 s on sunrise; 3,564
place-days): 0 early, 0 late ends, 1–2 min late on every day (limit 3).

İstanbul, 26 Sep 2026 (the spec's row, pinned in `TurkiyeBalkansRussiaProofTest`): Fajr, Asr and Isha
are shown a minute late (their moments fall 19–27 s past the minute, inside the 10 s safety; nearest
rounding would need − 27 s, which is early on other days), the rest exact, the end of eating exact.

**`other.turkey`** (plain ± 30 s, R31) at Diyanet's six published points, 2,376 place-days: 0 early,
0 late ends, 1 min late on almost every day and its Asr 2–3 min (it now carries Task 7h's adhan2
allowance and 35 s more: against the old adhan2 TURKEY preset over 2026 at 13 places its Asr ran up
to 65 s ahead; with 70 s it is never earlier at any of them). Also measured against adhan2 and not
changed (not asked): its Isha is up to 50 s and its Maghrib up to 20 s earlier than adhan2's TURKEY on
some days at London, Berlin, New York and İstanbul (Isha only), never earlier than Diyanet's own
tables.

## Diyanet's European method (`tr.diyanet.europe`)

Tables: Diyanet's own for the twelve held cities (Sarajevo, Zürich, Freiburg, München, Wien, Paris,
Brussels, London, Amsterdam, Berlin, Stockholm, Oslo), each its own point table, in two captures:
the research's of 25/26 September 2026 (that month to 25/26 October, and all of 2027; 396 days) and
the weekly monitor's of 28 September (29 September – 29 October 2026 and all of 2027; 396 days).
The two print the same minutes on every day both hold (392–393 a city, 0 differing cells).

North of 44.5° Diyanet's takdir set its Fajr up to 39 min and its Isha up to 143 min before what the
generic rule (19 % of the night; the real 16°) gave. Each takdir city follows its own table: Fajr,
Isha and the end of eating at depression curves derived from every held row of it
(`DiyanetEuropeCurves`, written by `tools/timetables`' `generateDiyanetEuropeCurves`, under the
method's own daily sun, the R28 envelope), with − 29 s on the start curves and + 30 s on the end
curve (the end never passes the Fajr at either point, R15). Those three columns are therefore fitted
at every city; the other columns' margins are fitted on six cities (Sarajevo, Zürich, München,
Paris, Berlin, Stockholm) and held out on six: Fajr and Isha at the plain angles − 13 / − 10 s,
sunrise + 10, Dhuhr − 13, Asr − 12, Maghrib − 15, the end + 9.

**The clock-change days (monitor round, brief D).** The monitor's captures hold 26–29 October 2026,
the first winter-time days, which no research capture does (its month ends on the change day, 25
October). There the first curves were a minute early: Isha at Amsterdam, Berlin and London on 27–28
October, at Brussels on 26–27, at Wien on 28, and Oslo's Fajr on 27 (9 of 44 Isha cells, 1 of 44
Fajr). Not the clock change itself and not a reader: a curve slot then reproduced the printed minute
of the one year the slot was held in (the depression 25 s before it), and a printed minute, rounded
to the nearest, says only that Diyanet's own moment lies within 30 s of it; which side depends on
the year, as the sun's position on a month and day shifts through the leap cycle. Every slot from 30
October on had one year of rows, so the same thing would have followed on about half the plain days
of November and December 2026 and of 2028. Each slot is now a bound on Diyanet's own moment: for a
start the latest it could be under the rounding (the printed minute + 35 s), or, where the plain
18°/16° holds the row, the plain method's own moment; for the end the earliest (− 35 s) or the
plain end; across the years a slot is held in, the tightest. The cost: on takdir days the start
shows the printed minute or the one after (a minute of lateness the tightening across years will
shrink as the monitor holds more years); on plain days the plain method's own minute.

| `tr.diyanet.europe`, 12 cities, both captures | days | early / late end | +0 | +1 | +2 | +3+ | exact | worst |
|---|---|---|---|---|---|---|---|---|
| fajr | 9,504 | 0 | 4,332 | 2,984 | 1,120 | 1,068 | 45.6% | 6 |
| sunrise | 9,504 | 0 | 6,195 | 3,263 | 4 | 42 | 65.2% | 8 |
| dhuhr | 9,504 | 0 | 7,075 | 2,429 | 0 | 0 | 74.4% | 1 |
| asr | 9,504 | 0 | 6,876 | 2,628 | 0 | 0 | 72.3% | 1 |
| maghrib | 9,504 | 0 | 7,337 | 2,167 | 0 | 0 | 77.2% | 1 |
| isha | 9,504 | 0 | 4,559 | 3,217 | 1,018 | 710 | 48.0% | 4 |
| end of eating | 9,504 | 0 | 4,166 | 3,346 | 968 | 1,024 | 43.8% | 5 |

Recorded exceptions (units): the spring takdir moves Fajr and the dawn by several minutes a day in
steps, and in spring and August Isha too; the R28 envelope takes the latest (a start) or earliest
(the end) of three days, and the rounding bound a minute more on takdir days: Fajr up to 4 min late
at Brussels, London, Amsterdam and Berlin, 5 at Oslo and 6 at Stockholm (which also waits for
Islamiska Förbundet's pages); Isha up to 4 at those six; the end of eating up to 4 min early at
Brussels, London, Amsterdam and Berlin, 5 at Stockholm and Oslo. Diyanet prints Oslo's June and
July sunrise up to 7 min after the sun's (its rule is not published); the app keeps the sun's: up to
8 min early.

**Beyond the twelve tables** (not gate rows: the entry claims no figure there): Diyanet's own tables
for Copenhagen (396 days) and nine Nordic cities (31 days), 675 place-days at the app's points: 0
early, 0 late ends; Fajr up to 34 min late, Isha up to 135 min, the end of eating up to 35 min
early, in the takdir months. A latitude-free takdir rule could replace the generic one there.

**Countries (ruling R50):** Germany, Austria, Switzerland, Liechtenstein, France, Belgium, the
Netherlands, the UK, Sweden, Norway, Denmark, Finland, Bosnia. Not Kosovo, Albania or Australia:
Diyanet's own tables there (Prishtina, Tirana, Sydney, Melbourne) keep the Turkish method's 17° Isha,
and this method's 16° was early on every held day.

## Bosnia and Herzegovina: IZ BiH (`ba.iz`)

IZ prints every town as Sarajevo plus whole-minute monthly offsets, which drift from the sun: at Banja
Luka and Bihać the zora runs 1–7 min after the real 18° dawn (the old registry's Fajr was early on
325 of their days) and Isha up to 7 min before 16°. Each of the three held towns now follows its own
2026 table: dawn, Isha and end curves derived from it (`BalkanCurves`, exact sun) and per-month
offsets for sunrise, Dhuhr, Asr and Maghrib (never early or late on any of its days, 5 s to spare).
All three tables are therefore fit; only a two-day live capture is held out.

| `ba.iz`, 3 towns | days | early / late end | +0 | +1 | +2 | +3 | exact | worst |
|---|---|---|---|---|---|---|---|---|
| fajr | 1,097 | 0 | 1,055 | 38 | 4 | 0 | 96.2% | 2 |
| sunrise | 1,097 | 0 | 591 | 471 | 35 | 0 | 53.9% | 2 |
| dhuhr | 1,097 | 0 | 946 | 151 | 0 | 0 | 86.2% | 1 |
| asr | 1,097 | 0 | 409 | 538 | 148 | 2 | 37.3% | 3 |
| maghrib | 1,097 | 0 | 551 | 482 | 62 | 2 | 50.2% | 3 |
| isha | 1,097 | 0 | 1,064 | 26 | 7 | 0 | 97.0% | 2 |
| end of eating | 1,097 | 0 | 318 | 763 | 13 | 3 | 29.0% | 3 |

Class B (not A: nothing independent is held out, and the curves carry 2026 into later years). Recorded
exceptions: Banja Luka and Bihać, Asr, Maghrib and the end of eating up to 3 min (IZ's monthly offset
drifts within the month). Southern towns (spec §10.8): units for Mostar, Konjic, Goražde, Trebinje and
Novi Pazar at the app's points, claiming no figure, record Fajr up to about 24 min after IZ's printed
zora: its zora comes about 16 min before the real dawn at Trebinje in June, Taqwa keeps the real
dawn, and the edge's monthly Fajr margin over the three towns' drift puts it 3.7 (January) to 8.2
(June) min after that dawn. The margin stays: in winter the sign error may run the other way, so
the real dawn alone could come before IZ's zora. **Not proven:** no table is held for any town but the three; the
edge (the plain angles with per-month offsets a minute beyond the latest of the three towns', the end
of eating at 18° − 73 s) covers the rest of Bosnia and Sandžak unmeasured. If IZ's sign error runs
the other way in winter (its southern zora after the real dawn), the real dawn there could be before
IZ's printed zora: only IZ's own town tables (vaktija.ba, 118 places) can settle it.

## Kosovo: BIK (`xk.bik`)

The Takvimi is one old perpetual table for 42.5° N 21° E. Its imsak (the end of eating) sits between
18.5° and 19.7° and its Asr 2–11 min after the first shadow; the old method ran up to 16 min late.
The app now follows it at any point in Kosovo by curves and monthly offsets derived from its base
table, with 30 s more either way for the towns' whole-minute offsets (a minute on sunrise, where
Prishtina's table held out needed 24 s more in winter: widened by the excess and 5 s). Fit on the base
table (imsak and Sabah columns), Prishtina's own table held out (365 days).

Both: 0 early, 0 late ends; Fajr (Sabah = imsak + 20) worst 1. The table's uneven day-to-day steps
put Dhuhr up to 4 min late, sunrise and Maghrib 5, Asr 6 (recorded exceptions), Isha and the end of
eating 3. Class D, measured.

## Albania: KMSH (`al.kmsh`)

KMSH's 2026 Tirana table (fit) and Diyanet's own Tirana table to the end of 2027 (held out, forward in
time) are Diyanet's algorithm at the app's Tirana point within noise: margins Fajr − 22 s, Dhuhr − 24,
Asr − 23, Maghrib − 23, Isha − 21, sunrise and the end of eating + 21 / + 20. Tirana unit: 730
place-days, 0 early, 0 late ends, worst 1 (415 of them held out forward in time): class A, as
Diyanet's own units, since the method is Diyanet's rebuilt exactly; its reach is R40's minute at
Tirana, about 21 km (it was 25 km under class B). KMSH's Korçë and Shkodër (every half
month of 2026, at the app's points, the edge + 60 s): worst 2.

## Montenegro: IZCG (`me.izcg`)

One month is held (Podgorica, September 2026, agent-reported, LOW). It puts the dawn about 2 min before
19°, sunrise 7.5 min before the sun's, Dhuhr a minute after the transit, Asr at the first shadow,
Maghrib 7.5 min after sunset; the old method was a late end on all 30 days (sunrise and the end of
eating). Margins fitted on it and widened for the seasons not held (2 min at the dawn, the end of
eating and Isha, 30 s elsewhere): 0 early, 0 late ends, worst 3. Class D, no figure claimed.

## Russia: DUM RF (`ru.dumrf`), DUM RD, Bashkortostan

**DUM RF.** Its captured Moscow rows: 2024 (13 rows) follow its old summer rule, a fixed 1 h 55 min
from sunrise and Maghrib, a known exception: the `daily-since-2025` reader leaves them out and the
gate reports them unread. The 107 rows of 2025–26 show the current rule exactly: Fajr the later of
18° and sunrise − 0.30 of the night (0.298–0.302), Isha the earlier of 15° and sunset + a fraction of
the night running 0.25 (early May) → 0.30 (June) → 0.25 (early August, when the 15° dusk is back and
later), stepping about 0.005 every three days, never over 0.301. At Moscow Fajr, Isha and the end of
suhur follow the depressions its rows print (104 slots); on the other slots its rule by its own
fractions, interpolated between the held days where the rule is what they print and held beyond
them, the largest Isha and end fraction and the smallest Fajr one within three days either side,
on the sun of the nearest held row's year (`DumRfMoscow`, derived depressions only). The end of
suhur is now built from these rows (the earliest of three days, R28/R39) in place of Task 5's
curve, which mirrored June into July and August and ran 5–8 min early there; the rest of Russia's
end reads the same depressions. Away from Moscow Isha stays the earlier of 15° and 0.305 of the
night, where no table can check a seasonal fraction. Fit on all 107 rows, nothing held out (the
captures are sparse): the margins are unchanged, 0 early, 0 late ends; sunrise, Dhuhr, Asr, Maghrib
worst 2. Recorded exceptions at the Moscow unit, each for its own cause (they were Fajr 13, Isha 23,
the end 9 under one reason):
- Fajr up to 4 min late: the sun at sunrise − 0.30 of the night stands up to a quarter of a degree
  higher or lower from one day to the next in May and August, and R28's curve takes the latest of
  the neighbouring days (May, and 25 Jul–19 Aug).
- Isha up to 5 min late: the same envelope, and between its held May days the largest fraction
  within three days (1 May 2025 and 19 May 2026).
- End of suhur up to 4 min early: the earliest of the neighbouring days' dawns (May, and
  21 Jul–18 Aug).

**The Moscow unit's reach.** DUM RF prints one table for "Moscow and Moscow oblast", so the oblast
follows the Moscow table: the M25 case of R44, not a neighbour publishing its own. Neither 60 km nor
R40's 47 km was right on its own: beyond 60 km east the old edge (the user's own sun, a minute later)
was early against the Moscow table the oblast follows. Now the Moscow unit keeps R40's 47 km, and a
second unit on the same point reaches the oblast's edges (165 km, no figure claimed): starts never
before the Moscow table, ends never after it, up to about 11 min from it at the oblast's edges.

**DUM RD (Dagestan) and Bashkortostan:** no table is held (the research saw one month of islamdag.ru
and kept none). Unchanged, class D (DUM RD) and D (none), not measured.

## What is not proven

- DUM RT beyond 2026: its rule by night is what its 2026 table shows at all 19 towns; a later table
  could change the rule.
- IZ BiH beyond its three towns (no town tables), including the southern sign error's other season.
- Diyanet beyond its units and European tables beyond their twelve cities: never early where held,
  but only as the edge; Diyanet's European tables for the Balkans (Prishtina, Tirana) and Australia
  follow its Turkish method, not this one.
- DUM RF outside its 107 captured days (its rule by interpolated fractions stands in; before 1 May and
  after mid-August its Isha fraction is held at the nearest printed one), and the whole of its
  oblast away from Moscow (geometry, not tables).
- Kosovo's towns other than Prishtina, Albania's other towns beyond Korçë and Shkodër's half-monthly
  rows, Montenegro beyond one September.
