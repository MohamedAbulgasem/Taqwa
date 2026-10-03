# Task 7g — Europe, the Americas, South Africa and Oceania

This is the proof for London Unified and the rest of the UK, Ireland, France, Belgium, the Netherlands,
Germany, Austria, Switzerland, the Nordics, the United States, Canada, South Africa, Australia and New
Zealand. It was fitted and proved against the official tables held in the local archive.

The files are `registry/authorities/Europe.kt`, `Americas.kt` and `SouthAfrica.kt`, the data files
`LondonUnifiedCurve.kt`, `IfiCurve.kt`, `JamiatTowns.kt` and this group's parts of `EndOfEatingDawns.kt`,
and this group's sections of `TwilightCurves.kt`. The M25 polygon in `Regions.kt` is edited by exception.

Each authority has one gate file under `tools/timetables/official/gate/`, and its stamp is in
`official/stamps/`. The command is:

```
./gradlew -p tools/timetables jvmTest gate -PgateGroup=<the groups below>
```

It must show 0 early starts and 0 late ends for every entry, with lateness within each event's limit.
The limit is the class's, or the `LateLimit` recorded in the authority file, with its reason repeated
here.

**The repository is public.** Of this group's tables, only the London Unified table is openly licensed
("completely free for all use", as London Prayer Times republishes it; ruling R33). Everything else below
is statistics, angles, minutes and margins, never a printed time.

**Tables made for the gate.** Where the gate needed a split or a fault left out, the local archive
(git-ignored) holds files derived from the archived tables. Each file's header names its source and any
line it leaves out. MANIFEST.tsv is unchanged, since it is outside this subtask. The derived files are:

- `namerica/canada/split-7g/`: Toronto's three 2026 tables in halves;
- `round/data-wide-band/jamiat-7g/`: Jamiat's perpetual CSVs with their printed Suhoor column, mapped
  onto 2025–2029;
- `africaoceania/split-7g/`: Cape Town's community calendar by year, and September slices for the
  cautious check.

## Summary

On prayer-engine with 7b merged, the gate over all 25 groups checks 208 rows and 46,205 place-days.
It found 0 early starts and 0 late ends for all 31 entries. Diyanet's Stockholm unit takes a minute more
on Fajr for Islamiska Förbundet's pages; see "After 7b's merge" below.

The table below gives the worst lateness per event in minutes. A limit is shown in brackets where the
class limit is exceeded or an entry exception applies. Starts are late after the table; sunrise and
the end of eating are late before it.

Event codes: F Fajr, S sunrise, D Dhuhr, A Asr (Ah Hanafi), M Maghrib, I Isha, E end of eating.

| entry | class | place-days (held out) | worst per event |
| --- | --- | --- | --- |
| `at.iggo` | D | 184 (92) | F 3 (4), S 1, D 1, A 1, M 1, I 3 (4), E 4 (4) |
| `au.cautious` | C | 380 (380) | F 2, S 8 (8), D 4 (4), A 18 (18), M 1, I 10 (10), E 2 |
| `au.lma` | D | 349 (0) | F 1, S 1, D 3, A 15 (15), M 1, I 1, E 1 |
| `be.cautious` | C | 31 (31) | F 1, S 1, D 1, A 1, M 1, I 1, E 1 |
| `be.emb` | D | 360 (183) | F 7 (7), S 2, D 1, A 2, M 1, I 9 (9), E 7 (7) |
| `ca.cautious` | C | 1788 (1788) | F 36 (36), S 4 (4), D 5 (5), A 5 (5), M 5 (5), I 77 (77), E 38 (38) |
| `ca.ift` | D | 350 (175) | F 2, S 2, D 1, Ah 2, M 2, I 3, E 3 |
| `ca.iit` | D | 350 (175) | F 3, S 4 (4), D 1, A 2, M 4 (4), I 4 (4), E 3 |
| `ca.isna` | D | 6414 (3133) | F 5 (5), S 5 (5), D 2, A 4 (4), Ah 4 (4), M 5 (5), I 4 (4), E 5 (5) |
| `ca.mac` | D | 350 (175) | F 1, S 1, D 1, A 2, M 1, I 1, E 1 |
| `ca.toronto` | C | 1400 (1050) | F 6 (7), S 3, D 5 (6), A 6 (6), Ah 6 (6), M 5 (6), I 7 (7), E 4 (4) |
| `ch.fids` | D | 32 (0) | F 1, S 1, D 1, A 1, M 1, I 1, E 1 |
| `de.cautious` | C | 61 (61) | F 12 (12), S 1, D 1, A 1, M 1, I 11 (11), E 2 |
| `fr.cautious` | C | 365 (365) | F 7 (7), S 1, D 2, A 4 (4), M 3, I 38 (38), E 29 (29) |
| `fr.gmp` | D | 374 (101) | F 2, S 1, D 1, A 2, M 1, I 5 (5), E 16 (16) |
| `gb.london.lupt` | B | 4015 (3650) | F 5 (5), S 3, D 2, A 3, Ah 3, M 3, I 5 (5), E 4 (4) |
| `gb.wifaqul` | D | 7 (0) | F 1, S 1, D 5 (6), Ah 0, M 6 (6), I 0, E 0 |
| `ie.cautious` | C | 365 (365) | F 2, S 2, D 1, A 2, M 2, I 5 (5), E 3 (3) |
| `ie.icci` | D | 365 (184) | F 7 (7), S 1, D 1, A 3, M 1, I 5 (5), E 3 |
| `ie.ifi` | D | 365 (0) | F 2, S 2, D 1, A 2, M 2, I 3, E 1 |
| `nl.cautious` | C | 31 (31) | F 16 (16), S 1, D 1, A 1, M 2, I 6 (6), E 1 |
| `no.cautious` | C | 1095 (1095) | F 51 (51), S 10 (10), D 5 (5), A 7 (8), Ah 8 (8), M 3, I 148 (148), E 96 (96) |
| `no.irn` | D | 1095 (0) | F 11 (56), S 42 (42), D 6 (6), A 10 (36), Ah 7 (36), M 313 (313), I 237 (237), E 235 (235) |
| `nz.fianz` | D | 122 (61) | F 1, S 1, D 1, A 1, M 1, I 1, E 1 |
| `se.cautious` | C | 1300 (1300) | F 118 (118), S 6 (6), D 5 (5), A 7 (7), M 2, I 155 (155), E 137 (137) |
| `us.chicago` | C | 1101 (1101) | F 31 (31), S 3, D 6 (6), Ah 5 (5), M 6 (6), I 5 (5), E 32 (32) |
| `us.isna` | D | 8617 (3993) | F 4 (4), S 4 (4), D 7 (7), A 5 (5), Ah 5 (5), M 6 (6), I 5 (5), E 4 (4) |
| `za.cape` | C | 30 (30) | F 1, S 2, D 1, A 1, M 4 (4), I 1, E 1 |
| `za.jamiat` | B | 14973 (10590) | F 1, S 1, D 1, A 1, Ah 1, M 2, I 2, E 1 |
| `za.mjc` | D | 30 (0) | F 1, S 1, D 1, A 1, M 1, I 1 |
| `za.voc` | D | 271 (149) | F 2, S 2, D 1, A 1, Ah 2, M 3, I 2 |

**Not gated:**

- `gb.cautious`: its members are conventions, so the gate cannot check it under R38. A survey of 71
  Mawaqit calendars checks it instead (`UkMawaqitSurveyTest`, ruling R75; UK section).
- `de.vikz`: no VIKZ table is held.
- IRN's Tromsø unit: it claimed nothing (`measured = false`). Since ruling R82 it is gated and measured;
  see the Norway section.

## London Unified (`gb.london.lupt`, gate `gb-london-lupt.tsv`)

**Class B**, changed from A. At the table's point it is 0–1 min late, except on some days at Fajr, Isha
and the end of eating (below). Across the M25 the recorded exceptions apply.

**The model, and why the curve changed.** The 2026 table shows that LUPT builds Fajr and Isha from whole
minutes counted from its own printed times:

- Fajr = the printed sunrise less a gap (87–121 min over the year, as a staircase of whole minutes);
- Isha = the printed Maghrib plus a gap (63–97 min).

The printed sunrise is HMNAO's less 3, and the printed Maghrib is sunset plus 3. Hizbul Ulama's note
describes timetables built from "the time length gaps shown in the charts", which matches this.

The old curve had the sun's depression at each printed minute, widened over a whole day either side
(ruling R28). That picked up both the table's ±30 s rounding and a day's change of the sun, so the
table's own point ran 2–3 min late at Fajr and Isha (the R49 finding).

`LondonUnifiedCurve` now reads the gaps from the 2026 table and computes the depression at "sunrise less
(3 + gap)" and "sunset plus (3 + gap)" at the table's point for every year from 2025 to 2040:

- the Fajr start takes the smallest depression, the Isha start the largest, and the new end-of-eating
  curve the largest at the larger Fajr gap;
- from 1 March each date also takes the next date's gap: the smaller Fajr gap and the larger Isha gap
  for the starts, the larger Fajr gap for the end. This covers leap years if Shaukat's functions count
  days from the solstice by day of the year, as moonsighting.com's published functions do.

This is R28's envelope taken over the years the leap cycle actually moves the sun through, instead of
a whole day either side. Measured against the ideal "gap" time in each year, the years envelope adds at
most 31–59 s (its widest in April and July).

The end of eating now reads its own curve (`EndOfEating.DawnAngle` by slot, ruling R39), not the late
start curve.

**Margins.** They were fitted on the 2026 table at the point, both Asr columns, with the Fitter's 5 s of
safety. The values, in seconds: fajr −17, sunrise +30, dhuhr −14, asr +36 (the Standard needed +23 and
the Hanafi +36), maghrib −12, isha −15, end of eating +34.

Only 2026 exists, so nothing is held out in time. The October 2026 file (`off-elm-oct.txt`) is the
same table. The margins are sound in other years because the gap time's rounding residual (at most
+30 s plus HMNAO's bias) reaches its maximum within any one year's 365 days, and the years envelope only
makes the start later.

**Gate over 4,015 place-days.** The rows are the table's point and 11 points across the M25 (Uxbridge,
Heathrow, the western edge at Thorpe, the north-west, south-west, north, south, Rainham, the eastern edge
at Thurrock and the south-east, with the October file as a separate row). There were 0 early starts and
0 late ends.

At the point (365 days):

| event | 0 min | 1 min | 2 min | exact | worst |
|---|---|---|---|---|---|
| Fajr | 239 | 119 | 7 | 65.5 % | 2 |
| sunrise | 307 | 58 | 0 | 84.1 % | 1 |
| Dhuhr | 334 | 31 | 0 | 91.5 % | 1 |
| Asr (Standard) | 112 | 253 | 0 | 30.7 % | 1 |
| Asr (Hanafi) | 187 | 178 | 0 | 51.2 % | 1 |
| Maghrib | 308 | 57 | 0 | 84.4 % | 1 |
| Isha | 257 | 97 | 11 | 70.4 % | 2 |
| end of eating | 246 | 103 | 16 | 67.4 % | 2 |

The 2-minute days are the leap-year gap envelope. Without it the point is within a minute on every day
(the reason for class B; see "Not proven").

Across the M25, the worst per event: Fajr 5, sunrise 3, Dhuhr 2, Asr 3, Maghrib 3, Isha 5, end of
eating 4.

**Recorded exceptions (the M25 unit).** Ruling R44 has the whole M25 follow the one table computed at its
point, with starts the later of the point's and the user's own, and ends the earlier. The exceptions,
also in `Europe.kt`, are:

- **Fajr and Isha, 5 min.** The M25 follows one table computed at its point, and Fajr and Isha shown are
  the later of the point's and the user's own. At the M25's southern and western edges the user's Fajr,
  and at its northern and north-western edges the user's Isha, at the table's depression run up to 3 min
  later than the point's in summer. This is the latitude effect of a shallow summer twilight, which the
  earlier 1.6 min estimate (longitude alone) missed.
- **Asr and Maghrib, 3 min.** The user's own sun is 1.6 min later at the western edge.
- **Sunrise, 3 min.** The user's own sunrise is 1.7 min earlier at the eastern edge.
- **End of eating, 4 min.** The user's own dawn is 1.7 min earlier at the eastern edge, and up to 3 min
  earlier at the northern edge in summer.

Dhuhr needs no exception: it is within class B's 2 min everywhere.

**The M25 polygon (R49 item 5).** The 32 junctions were joined by chords, which cut strips of London
off to the UK's cautious entry, whose 15° summer Fajr is earlier than LUPT's. Now each pair of junctions
is joined every 1.5 km or so by points interpolated in bearing and distance about the ring's centre
(51.49 N, 0.12 W), then set 300 m further out (113 vertices).

Checked against town centres:

- inside, as before: Enfield, Loughton, Romford, Upminster, Orpington, Caterham, Cobham, Weybridge,
  Staines, Uxbridge, Rickmansworth, Watford, Abbots Langley, Dartford, Epsom, Coulsdon, Biggin Hill,
  Borehamwood, Radlett, Rainham;
- outside: Potters Bar, Epping, Brentwood, Sevenoaks, Redhill, Reigate, Leatherhead, Egham, St Albans,
  Cheshunt, Slough, Woking.

`EuropeAmericasAfricaProofTest` pins some of them.

**Not proven.**

- No second year of LUPT is held. So the two assumptions behind the envelope cannot be checked:
  - that the gap staircase is fixed by date, possibly shifted a day in leap years;
  - that HMNAO's sunrise stays within its 2026 bias.
- With a second year's table showing the staircase fixed by calendar date, the next-day envelope could
  go. The point would then be within a minute on every day, and London would be class A again.
- The starts across the M25 come from the engine's R44/R15 rule (the later of the point and the user's
  own). A fixed-point mode that keeps the table's Fajr, Isha and end of eating at its point across a
  region that follows one table would bring the corners back within 3 min (see Concerns in the report).

## The UK outside London (`gb.cautious`; `gb.wifaqul`, gate `gb-wifaqul.tsv`)

**`gb.wifaqul`: class D_AUTHORITY.** The only Wifaqul table held is Manchester Central Mosque's September
2026 PDF, seven rows. The Bradford PDF in the research cache is a Cloudflare page, not a table. With seven
rows there is nothing to hold out.

Manchester rounds to the nearest minute, so the margins are nearest rounding's bound rather than the seven
rows' tighter fit (−48 at Fajr). The values, in seconds: starts −20, Asr −5 (its fit −12 plus 7), sunrise
+20, end of eating +20.

Manchester prints Zuhr at the meridian and Maghrib at sunset themselves, although Wifaqul Ulama
documents + 4 and + 5 (HIGH). The documented minutes stay.

The gate over 7 place-days showed 0 early and 0 late ends, worst late Fajr 1, sunrise 1, Asr 0, Isha 0,
end of eating 0, Dhuhr 5 and Maghrib 6. The last two are the recorded exception.

**Recorded exception (`gb.wifaqul`), Dhuhr and Maghrib, 6 min.** Wifaqul Ulama documents Zuhr at the
meridian + 4 and Maghrib at sunset + 5. Manchester Central Mosque, the only table of theirs held, prints
the meridian and sunset themselves, so against it Dhuhr runs up to 5 min and Maghrib up to 6 min late.

**`gb.cautious`: class C, `measured = false`, not gated; checked by a survey.** Its members are
families of UK mosques, not tables printed for one place. Ruling R38's check needs every member's
printed table at the same place and date, and only Wifaqul has a printed table (Manchester, seven days).
So it is checked by a survey instead (research-uk, ruling R75), which the repository now keeps and runs:
`tools/timetables/official/survey/gb-cautious/` and `UkMawaqitSurveyTest`
(`./gradlew -p tools/timetables jvmTest --tests world.taqwa.timetables.gate.UkMawaqitSurveyTest`).

### The survey

- **The calendars.** 71 Mawaqit calendars outside London: 7g's 59 and the 12 its filter dropped
  (Birmingham, Manchester, Bradford, Halifax, Leeds, Leicester, Blackburn, Batley, Keighley, Glasgow
  and around). They come from the research's cache of Mawaqit pages, fetched on 26 September 2026, and
  are kept in the local archive only (`archive/tables/uk-mawaqit/`, MANIFEST rows `GB-MOSQUE`, copied to
  the archive backup). `calendars.tsv` lists each with its mosque's own point and columns. Ashton
  Jam'e Mosque carries a seventh column; 7g's converter read it one column off, which is why 7g dropped
  it.
- **The check.** Each calendar at its own point, day by day, with `gb.cautious` as the app computes it
  there (`DayPipeline`):
  - no Fajr, Dhuhr, Maghrib or Isha before the calendar's;
  - no Asr before it: the default (the later school, Hanafi) against every printed Asr, and the Standard
    option against the prints nearer the Standard time;
  - no sunrise after the calendar's, and no end of eating after its Fajr (these mosques print no imsak:
    their Fajr is when their fast begins, as the gate's `F+E`);
  - no day put back in order, and no Isha left without an end (the next end of eating at or before it).
- **Maghrib** is capped at Wifaqul's by spec §3.6 (the owner's 26 September decision), so it is checked
  against that cap: a Maghrib before a calendar's counts as the cap's only where the members' Maghribs
  spread past 2 min and Wifaqul's own is the one shown (`maghribCap`, recorded below, by the calendar's
  minutes past the cap); any other early Maghrib fails.
- **What is left out and what is recorded.** `faults.tsv` lists every cell left out as faulty, with its
  reason (dates and columns only); `outliers.tsv` records what no member absorbs, with the days and the
  worst minutes each may reach. The test fails on anything else, and on an outlier past its figures.
  Without the archive (CI) it is skipped and says so.

### Left out, each with its reason (`faults.tsv`)

**Whole calendars (9).**

- Congregation (iqama) times, not starts: Dhuhr at a fixed clock time by season, 20–70 min after the
  meridian:
  - Adam Mosque & Dawah Academy, Birmingham (one clock time all year; its Fajr falls after sunrise in
    summer);
  - Al Ma'rifah Academy, Leicester;
  - Bait-us-Salam, Erdington;
  - Faizan E Raza, Bradford (its Fajr one clock time all year too);
  - Ghosiyah Masjid, Keighley;
  - Hamidiya, Leicester;
  - JMAH, Halifax;
  - Masjid Adam, Leicester.
- Masjid At Taqwa, Greater Manchester: whole months repeat (January, March and December are identical)
  and do not follow the sun.

**Months and blocks.**

- An hour off: Al-Sunnah Manchester all March (early); Masjid Ar-Rashideen Batley April–October and
  Masjid Imdadia April–July (late: summer time added twice); Masjid Aisha Leicester all October (late).
- Columns or rows holding another time: Masjid Imdadia's October–December Maghrib and Isha;
  Masjid-E-Hidayah's August–September rows (Dhuhr 13 h off); Masjid Aisha's rows from 29 March to 31 May
  (an hour off, then a flat Fajr, and a sunrise and Maghrib drifting by up to 90 min); Faizan e Madina Stechford's March–July and
  November–December (another table's rows, Dhuhr 48 min after the meridian); Taybah Leicester's
  January–February (other months' rows) and its Dhuhr an hour late from 20 September.
- Congregation times for part of the year: QUBA Birmingham in November–December; Muslim Youth Foundation
  Manchester from October to March, its September broken (April–August is used).
- One column for a month: GUMSA's September Dhuhr (a 12-hour clock); Hazrat Mujaddid's November sunrise
  (an hour early); Zaytuna's Asr on 1–12 February (24 min past its own Hanafi); Masjid e Iqbal's June
  Asr (one clock time every day); Al Falah's June and July Isha (a placeholder where its 15° does not
  occur); Zaytuna's five flat Fajr rows at the November/December edge.
- The week after the clocks change, an hour early: Al-Furqan, Community Masjid and Marwa (25–31
  October), Faizan-e-Madina Bradford's Isha.

**Single days and cells.** Rows and cells found on the printed tables alone (a whole row 45–75 min off
its own neighbours, or a Dhuhr, sunrise or Maghrib cell 20+ min off them, on 35 calendars), and cells
checked by hand (Fajr cells an hour off at a clock change, or up to two hours off by a typo; Taybah's
stray Fajr and sunrise typos; single Asr, Isha and Dhuhr typos 8–60 min off both neighbours). In all, 142 fault lines; 7,119 cells of the 62
calendars used are left out.

**7g's "held in GMT all year" eight** are not that: none is an hour off all summer. Five have the
particular faulty months above and are used for the rest; Woodfarm and Masjid-E-Hidayah are wrong only on
25 October, Al-Sunnah only in March.

### What the 62 calendars print

- **Fajr in winter:** the 12° family, 12.4–12.8° in December and January (South Birmingham Central,
  Wythenshawe, Abdul Raheem; MahmudSabir, Marwa and Zaytuna in December and January only); the 15°
  family, 13.7–15.7° (41 calendars; Glasgow 15.4–15.5°); the 18° family, 17.3–18.5° (14; Faizan-e-Makkah
  alone 18.44–18.58°).
- **Fajr on the summer nights without the angle**, as a share of the night before sunrise:
  - the middle of the night, 0.45–0.53 (14 to 16 calendars, about a quarter: Faizan-e-Madina Bradford
    and Halifax, Faizan-e-Makkah, Jamia Masjid Madni, Zia-ul-Quran, Sultan Bahu, Markaz Quba, Masjid
    Aisha, Mu'adh ibn Jabal, and Glasgow's BMACC, Bishopbriggs, Dawatul, GUMSA and Woodfarm; probably
    Faizan Stechford and Ar-Rashideen, whose summers are faulty);
  - about 0.37 (Jamiyat Tabligh ×2, Masjid Ibraheem); 0.28–0.31 (Glasgow's 15° group, South Birmingham,
    ALBERR, Imam al-Shafi'i, Taybah, Zaytuna, Al-Sunnah, Hidayah); 0.23–0.27 (most Birmingham and
    Manchester 15° calendars);
  - the late dawn, 0.185–0.215, about 8–9° (Leeds, Wythenshawe, Abdul Raheem, Muslim Youth Foundation,
    Ashton).
- **Isha in summer:** mostly 0.14–0.22 of the night after sunset; Imam al-Shafi'i 0.30 (18° as a share
  of the night); Zia-ul-Quran, Sultan Bahu and Markaz Quba 0.46–0.50, the middle of the night.
- **Asr:** 31 Hanafi, 30 Standard, 1 alternating by month (Al-Furqan): no majority, so the later school
  leads (spec §3.7). Glasgow's Asr runs 2–11 min after the exact time in both schools by season
  (+ 9..11 in late autumn); England's at most 5–6.
- **Dhuhr, Maghrib and sunrise:** Dhuhr at most + 7.6 after the meridian (Masjid Bilal, one June day),
  otherwise at most 7.0 in Glasgow and 6.6 in England; Maghrib + 6..10 in Glasgow, Ashton + 7.4,
  Ibraheem + 8, 0–5 elsewhere; sunrise up to 8.3 min before the sun's in Glasgow.

### The members (most-followed first; the Maghrib cap stays at Wifaqul's)

1. **`gb.wifaqul`** (rank 1): unchanged.
2. **`gb.fifteen`** (rank 2): unchanged in England, Wales and Northern Ireland. In Scotland
   (`Regions.scotland`: the Anglo-Scottish border every few km, the Solway and the North Channel; every
   British city in the app's list checked, RegistryCitiesTest) its Asr minutes are monthly, in seconds
   January to December 196, 391, 597, 237, 250, 280, 388, 473, 509, 531, 491, 368, on top of the 30 s
   start margin: each month's latest printed-minus-exact Asr over the 11 Glasgow calendars, less a whole
   printed minute, plus 15 s, less the margin. Its June Dhuhr takes 20 s more (Masjid Bilal's + 7.6).
3. **`other.mwl`** (rank 3): unchanged.
4. **`gb.karachi`** (rank 4): its fast begins at the 18.6° dawn, and on a night where the sun does not
   get 18.6° low at the middle of the night, last sunset to sunrise (`EndOfEating.DawnOrMiddle(18.6)`,
   `SAFE_END`). This is the midnight family's summer Fajr; 18.6° covers Faizan-e-Makkah's 18.44–18.58°.
   Its Fajr start and every other time are unchanged, so no start moves.
5. **`gb.latedawn`**, new (rank 5, a convention named "Late-dawn timetable", not offered in Settings):
   Fajr on `UkLateDawnCurve`, 366 depressions on ruling R28's slots, the smallest depression of the
   family's printed Fajr at each mosque's own point per slot, then the smallest of the slot and its two
   neighbours, rounded down to 0.001° (ruling R42: derived depressions; the calendars are not committed).
   The family is South Birmingham Central, Wythenshawe, Abdul Raheem, MahmudSabir, Zaytuna, Marwa, Leeds
   Grand Mosque, Ashton and Muslim Youth Foundation: 9 of 62, 15 %. The curve runs 12.38–12.67° from
   January to March, falls from 12.4 to 9.9° in April and to 7.95° in May, stays at 7.95–8.03° in June
   and July, rises from 8.1 to 11° in August and to 12.1° in September, and is 11.87–12.51° from October
   to December. Where the sun does not reach it (Orkney and Shetland in June and July) its Fajr is 0.185
   of the night before sunrise, the family's own June share (Leeds 0.185, Wythenshawe 0.187); the MWL
   proportion put it about 28 min before sunrise there, 40 min after the other members. Its Isha 15°,
   Dhuhr + 5 and safe margins are never the ones shown: UkCautiousTest walks every third day of 2026 at
   seven places from Plymouth to Lerwick and finds every time but Fajr unchanged with it or without it. Every other
   calendar's Fajr is no later than the curve at its own point.

### Before and after, on the 62 calendars (22,630 place-days)

About 21,500 days are checked for each of Fajr, the end of eating, sunrise, Dhuhr, the default Asr,
Maghrib and Isha, and 10,696 for the Standard option (its prints).

| event | the engine at `f9da455` | this branch |
|---|---|---|
| Fajr early | 9 calendars, 657 days, up to 21 min | **0** |
| end of eating late | 33 calendars, 2,297 days, up to 147 min | 2 calendars, 82 days (recorded) |
| sunrise late | 0 | 0 |
| Dhuhr early | 2 calendars, 31 days, up to 3 min | 1 calendar, 30 days (recorded) |
| Asr early, the default (Hanafi) | 6 calendars (Glasgow), 466 days, up to 5 min | **0** |
| Asr early, the Standard option | 5 calendars, 353 days, up to 24 min | 2 calendars, 81 days (recorded) |
| Isha early | 4 calendars, 140 days, up to 92 min | the same 4 (recorded) |
| Maghrib before a calendar's (the cap) | 22 calendars, 4,555 days, up to 9 min | the same, every one at the cap (spec §3.6) |
| Isha without an end | 2 calendar-days, 0 min | 7 calendar-days, one a calendar, 0–2 min (recorded) |
| days put back in order | – | 0 |

Before the change the end of eating was the largest finding, and 7g did not check it: a quarter of the
calendars put their Fajr, which is when their fast begins, at the middle of the night on the summer nights
without 18°. 7g's 7 late sunrises were all clock or typing faults (left out above).

### Recorded, not absorbed (`outliers.tsv`)

None of these fits a late limit: a `LateLimit` records lateness, and nothing excuses an early start or a
late end. They are recorded here and in `outliers.tsv`, where the survey holds each to its figures.

1. **Isha, Zia-ul-Quran (62 days, up to 92 min, May–July), Sultan Bahu (36 days, up to 92) and Markaz
   Quba (36 days, up to 79), June–July.** They print Isha at the middle of the night on the nights without
   18°, the same minute as their own Fajr (3 calendars, 5 %). Absorbing it would put everyone's Isha at the
   middle of the night, where the end of eating now is: the Isha window would be empty all summer.
2. **Isha, Masjid Imam al-Shafi'i (6 days, up to 4 min, June–July).** Its Isha is 18° as a share of the
   night, 0.30. An Isha floor at 0.30 would cost everyone up to 6 min in Birmingham, 9–16 min in Manchester
   and Bradford and 23–28 min in Glasgow from May to July.
3. **Dhuhr, QUBA Islamic Centre, Birmingham (April, 30 days, up to 3 min).** Its April Dhuhr alone is
   8–10 min after the meridian, against about half a minute in every other month, and its November and
   December are congregation times. Absorbing it would move everyone's April Dhuhr 3 min later.
4. **Asr, the Standard option only: Brayatee (53 days, up to 24 min, mid-March to early May) and Al Falah
   (28 days, up to 8 min, March).** Their spring Asr drifts between the two schools. The default, the
   later school, is never early against them; absorbing it would delay everyone's Standard Asr by up to
   24 min in spring.
5. **End of eating, Jamia Masjid Madni, Halifax (80 days, up to 13 min, May–July).** Its summer Fajr is
   4–14 min before the sun's lowest point (0.51–0.53 of the night), alone in the survey. An end at 0.535 of
   the night would take it (prototyped) but empties the Isha window on 1–6 nights a year at every city
   checked.
6. **End of eating, Masjid Aisha, Leicester (30–31 July, 14 and 31 min).** It keeps its flat summer Fajr
   two days after the sun reaches 18° again at its point. No clean rule keeps the middle of the night past
   the sign's return.
7. **Maghrib (by design).** Spec §3.6 caps Maghrib at Wifaqul's sunset + 5 where the members spread, so
   the app's Maghrib is up to 5 min before every Glasgow calendar's (they print + 6..10), Ashton's and
   Masjid Ibraheem's on most days, and before a few more by 1–2 min on some days (Taybah by 9 on 3 March
   days). Keeping Maghrib never early here too would need the cap to change (a Scotland Maghrib of + 10 for
   the cap, or a most-followed member per region): a spec change, not a member change. The survey takes a
   Maghrib before a calendar's as the cap's only where the members spread past 2 min and Wifaqul's own
   Maghrib is the one shown: all 4,555 calendar-days are, and any other early Maghrib fails.
8. **Isha on the threshold night (the class of ruling R72).** On the night a member's Isha angle (15°,
   17° or 18°) is last reached before the summer, or first reached again after it, that member's Isha,
   the latest, lies near the sun's lowest point, and the next end of eating, the middle of the night on
   those nights, at or just before it: Isha has no end that night (ruling R26). Across the UK it happens
   from April to August at some latitudes only, on up to two nights a year at a point, by up to 3 min:
   - **A scan of the engine** every 0.05° of latitude from 50.05° to 60.8° N, at 2.1° and 4.0° W, over
     2026 and 2027, found it in 77 of the 864 point-years (80 nights: 0 min on 35, 1 on 28, 2 on 13, 3 on
     4). Two nights in one year at 53.45° N at both longitudes (at 2.1° W, Hyde in Tameside: 14 and 29
     July 2026) and at 54.45° N 2.1° W (29 July and 2 August 2026); 3 min at Hyde (29 July 2026),
     Motherwell (55.8° N 4.0° W, 4 May 2026), north Dorset (50.95° N 2.1° W, 25 May 2027) and 51.7° N
     4.0° W (21 May 2027). The latest Isha was the Karachi family's 18° on 33 nights, the 15° family's or
     Wifaqul's 15° on 26 and MWL's 17° on 21, each on its angle's last or first night. A finer scan every
     0.01° from 51.40° to 51.75° N at 1.2° and 3.2° W over 2026–2028 found it in 41 of 216 point-years,
     again at most two a year (51.57° N: 27 May and 22 June 2026), by up to 2 min.
   - **At the 62 calendars' points in 2026:** 7 calendar-days, one a calendar, 0–2 min (17 and 22 May and
     25 and 29 July, in Birmingham, Smethwick, West Bromwich and Manchester), the figures `outliers.tsv`
     holds. The engine at `f9da455` had 2 of them (0 min); the middle-of-the-night end of eating adds the
     rest.
   - **UkCautiousTest** walks 2026 at nine cities (one night at Stornoway, 15 August, and one at Thurso,
     28 April; none at Plymouth, Birmingham, Manchester, Bradford, Glasgow, Aberdeen or Lerwick) and at
     seven of the scan's points where it happens, and holds every such night to 3 min, two a year and its
     angle's last or first night.

   Elsewhere the shortest Isha window is 1–20 min, on the threshold nights of May and late July. The
   completion planner's empty-window rule (R83) compares Isha with the next Fajr, which is hours later
   here, so it still sounds these Isha alarms: after the end of eating, before Fajr.

Absorbed because it was cheap: Leeds Grand Mosque's summer Fajr, about 8°, is inside the late-dawn curve
(without it Leeds is early on 136 days, by up to 15 min; with it the curve costs everyone about 9–10 min
more on average in May, July and August and 3–5 in April and June, Birmingham figures);
Faizan-e-Makkah's 18.5° dawn is inside the 18.6° end of eating (about 3 min earlier for everyone all
year); Masjid Bilal's June Dhuhr is inside Scotland's + 20 s.

### What it costs everyone outside London

Minutes, 2026, mean/max by month, January to December (the same place computed with the members before
and after; computed on this branch).

Birmingham:

| | J | F | M | A | M | J | J | A | S | O | N | D |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Fajr later | 4/5 | 4/5 | 1/3 | 6/13 | 22/26 | 24/25 | 26/28 | 16/26 | 4/8 | 7/8 | 5/8 | 4/5 |
| end of eating earlier | 4/5 | 4/4 | 4/5 | 6/8 | 47/114 | 88/101 | 89/125 | 8/16 | 5/6 | 4/5 | 4/4 | 4/5 |

Manchester:

| | J | F | M | A | M | J | J | A | S | O | N | D |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Fajr later | 3/3 | 2/3 | 0/2 | 3/9 | 17/21 | 16/18 | 20/23 | 12/22 | 2/5 | 5/6 | 3/6 | 3/4 |
| end of eating earlier | 4/5 | 4/5 | 4/5 | 6/9 | 67/124 | 86/98 | 103/132 | 11/37 | 5/6 | 4/5 | 4/4 | 4/5 |

Bradford:

| | J | F | M | A | M | J | J | A | S | O | N | D |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Fajr later | 2/3 | 2/3 | 0/1 | 3/9 | 15/20 | 14/16 | 18/22 | 11/21 | 1/4 | 5/6 | 3/6 | 2/3 |
| end of eating earlier | 4/5 | 4/5 | 4/5 | 6/10 | 69/123 | 85/97 | 106/133 | 11/35 | 5/6 | 4/5 | 4/5 | 4/5 |

Glasgow:

| | J | F | M | A | M | J | J | A | S | O | N | D |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Fajr later | 0/0 | 0/0 | 0/0 | 0/0 | 3/8 | 0/0 | 4/12 | 4/11 | 0/0 | 1/3 | 0/3 | 0/0 |
| Asr later (both schools) | 0/0 | 1–2/2 | 5/5 | 0/0 | 0/0 | 0/0 | 1/2 | 3/3 | 3–4/4 | 4/4 | 3/4 | 1/2 |
| Dhuhr later | 0 | 0 | 0 | 0 | 0 | 0/1 | 0 | 0 | 0 | 0 | 0 | 0 |
| end of eating earlier | 4/5 | 4/5 | 5/6 | 8/17 | 98/135 | 79/91 | 100/128 | 46/147 | 5/7 | 4/5 | 4/5 | 4/5 |

Elsewhere (the largest monthly maximum, minutes):

| place | Fajr later, winter | Fajr later, May–August | end of eating earlier, winter | end of eating earlier, April–August | Asr later |
|---|---|---|---|---|---|
| Plymouth | 7–8 | 37 | 4 | 108 | – |
| Cardiff | 6–7 | 32 | 4 | 117 | – |
| Leicester | 5 | 28 | 4–5 | 126 | – |
| Belfast | 2 | 19 | 5 | 139 | – |
| Edinburgh | 0 | 12 | 5 | 146 | 0–5 |
| Aberdeen | 0 | 5 | 5 | 153 | 0–5 |
| Inverness | 0 | 2 | 5 | 155 | 0–5 |
| Stornoway | 0 | 0 | 5 | 157 | 0–5 |
| Thurso | 0 | 13 | 5 | 161 | 0–5 |
| Kirkwall | 0 | 13 | 5 | 163 | 0–5 |
| Lerwick | 0 | 11 | 5 | 168 | 0–5 |

- **Unchanged everywhere:** Isha, Maghrib and sunrise; Dhuhr and Asr in England, Wales and Northern
  Ireland.
- **By latitude** (research-uk's 86-point grid, the June maximum of Fajr later): 50° + 39, 51.5° + 31,
  52.5° + 24, 53.5° + 18, 55° + 7, 56–58.5° 0, 59–60.5° + 11..13 (the 0.185 share). The south pays most:
  the late-dawn depression is reached there hours before the other members' dawns.
- **Ramadan.** Ramadan falls in winter and spring until the early 2040s; there the cost is the 18.6°
  dawn's 4–5 min. The summer figures bite on voluntary fasts, on the About page's times, and on Ramadan
  from the early 2040s.

### Judgment calls, with the numbers for each

- **Leeds Grand Mosque in (chosen).** Never early is the owner's first rule, and a later Fajr does not
  harm the prayer. Without Leeds the mean Fajr lateness is 2–10 min less from April to September
  (Birmingham May 12/17 instead of 22/26, July 17/21 instead of 26/28, August 7/12 instead of 16/26;
  Bradford May 5/9, July 9/12), and Leeds is early on 136 days, by up to 15 min.
- **A depression curve (chosen) or a share of the night for the late dawn.** A depression moves Fajr later
  south of the family's 52.4–53.8° (Plymouth + 37 in June); a share of the night would move it less there
  and more in the north. No calendar outside 52.4–53.8° prints this family, so the safe side, a
  depression, was kept; the share is used only where the depression is not reached.
- **The end of eating at 0.5 (chosen) or 0.535 of the night.** 0.535 also covers Jamia Masjid Madni but
  empties the Isha window on 1–6 nights a year at every city; 0.5 leaves only the threshold nights (item
  8 above): up to two a year at a point, by up to 3 min, at some latitudes only.
- **18.6° (chosen) or 18.0° for the end of eating.** 18.0° saves everyone about 3 min all year and leaves
  Faizan-e-Makkah 2–4 min late every day.
- **Scotland (chosen) or only greater Glasgow for the Asr minutes.** The only Scottish calendars are
  Glasgow's (6 of 11 print the later Asr). Scotland-wide is the safe side of an unknown practice; greater
  Glasgow alone (a 50 km circle) would spare Edinburgh, Aberdeen and the rest their 0–5 min.

### London and everything else unchanged

The full gate without stamps (`./gradlew -p tools/timetables gate -Pstamps=false`) gives a report
identical to the one at `f9da455` but for its first line (the checkout's path): 585 rows, 137,468
place-days, 0 broken; London Unified 4,015 place-days and Wifaqul's 7 unchanged. `DawnAngle` behaves as
before. The engine core changed (`TimetableMethod.kt`, `DayComputer.kt`, `Registry.kt`, `Regions.kt`),
so every stamp's `engineHash` moves while its figures do not.

## Toronto (`ca.ift`, `ca.iit`, `ca.mac`, `ca.toronto`, gate `ca-toronto.tsv`)

**The data.** Each of the three mosques' 2026 tables, split forward in time: January to June for the
fit, July to December held out.

IFT's faults are left out of all three tables, so that every cautious place-day holds all three:

- it keeps standard time for 8–12 March and daylight time for 1–6 November, an hour off;
- it repeats 27 March's Fajr on the 28th;
- its Isha is 5 min after its neighbours on 28–30 November;
- IIT's Asr keeps standard time on 8 March.

**Holdout.** Fitted on January to June, July to December came up early:

- IFT: sunrise 10 late ends, Asr 6 early, Isha 9 early;
- IIT: Fajr 2, Maghrib 107 and Isha 107 early, and sunrise 100 late ends;
- MAC: Asr 128 early and Isha 4 early.

Each margin was then widened by the held-out excess plus 5 s, which comes to the Fitter's margin over the
whole year, and recorded here.

MAC's Asr follows the declination at 0h UT (`AsrModel.DAILY_0H_UT`), as adhan-js computes it. It cut
MAC's Asr from 4 min to 2 min late. The other models did not help IFT or IIT.

Margins, in seconds:

- IFT: fajr +82, sunrise +27, dhuhr −44, asr +51, maghrib +33, isha +82;
- IIT: fajr +78, sunrise −54, dhuhr +49, asr +107, maghrib and isha +18;
- MAC: fajr −19, sunrise +20, dhuhr −23, asr +24, maghrib −22, isha −12.

**The members alone, class D_AUTHORITY, 350 place-days each (175 held out): 0 early, 0 late ends.**

| entry | worst late (F S D A M I) | exact, typical |
|---|---|---|
| ca.ift | 2 2 1 2 2 3 | Dhuhr 85 %, the rest 15–37 % |
| ca.iit | 3 4 1 2 4 4 | 13–60 % |
| ca.mac | 1 1 1 2 1 1 | 72–88 %, Asr 26 % |

**Recorded exception (`ca.iit`), sunrise, Maghrib and Isha, 4 min.** IIT's own table drifts over its
year: its sunrise runs from 2 min before to 2 min after the sun's, and its Maghrib from sunset to sunset
+ 2.5, with Isha, 90 min after it, moving with it. The margins keep it never early all year, so in spring
they run up to 4 min.

**`ca.toronto`: class C, 1,400 place-days.** The rows are all three tables at IFT's point and at MAC's,
and July to December at Mississauga, Brampton, Milton (the reach's western end) and Oshawa. There were
0 early and 0 late ends.

At the mosques the worst per event was: Fajr 2, sunrise 3, Dhuhr 2, Asr 3, Maghrib 3, Isha 4. Across the
GTA: Fajr 6, sunrise 3, Dhuhr 5, Asr 6, Maghrib 5, Isha 7. The Maghrib cap engaged on 40 days.

**Recorded exceptions (`ca.toronto`).**

- **Fajr and Isha, 7 min.** Toronto's three tables round and offset their times irregularly against
  their own rules: IFT's Fajr and Isha run up to 80 s after them, IIT's Asr up to 107 s, and IIT's own
  Maghrib drifts over the year. So each member keeps its table's widest margin, and the latest of them
  runs up to 4 min after the latest printed time at the mosques. West of the mosques the user's own sun
  is later than the tables' points (at Milton, the reach's western end, by 2.5 min), and Fajr and Isha
  shown follow it: up to 6 and 7 min.
- **Dhuhr, Asr and Maghrib, 6 min.** The same spread; west of the mosques the user's own sun is later,
  up to 5, 6 and 5 min.
- **Sunrise, 3 min.** Sunrise is the earliest of the three members', each a little before its own
  table's.

## South Africa

### Jamiatul Ulama (`za.jamiat`, gate `za-jamiat.tsv`)

**Class B**, unchanged. Ruling R29's 29 February, the DAILY_0H_UT phase of 2026 and the Suhoor =
Fajr − 5 rule (R27) are unchanged.

**The data.** Jamiat's perpetual CSVs for eight towns, with their printed Suhoor column, mapped onto
2025–2029. The research's split was used: fit on Johannesburg, Cape Town and Durban over 2025–2028; held
out the other five towns over 2025–2029 and the three fit towns' 2029. Also held out:

- Darul Ihsan's own 2026 Pretoria tables, both schools;
- Darul Ihsan Durban's Hanafi table;
- Jamiat KZN's Durban and Johannesburg, as masjids.co.za relays them from March to December 2026. The
  relay's third column is Zawaal (noon), not the Zuhr, so it is not checked as Dhuhr.

**Margins,** fitted in seconds: fajr −18, sunrise +20, dhuhr, asr and maghrib −19, isha −17, end of
eating +20. They replace Task 5's slightly tighter −22/+23/+21, which were within the safety band.

**Gate over 14,973 place-days, 10,590 of them held out:** 0 early and 0 late ends, with 77–84 % exact per
event. Every event is 0 or 1 min late, except Maghrib and Isha, which are 2 min on 3 days. The unit
Pretoria (Darul Ihsan) is measured.

### Cape Town (`za.mjc`, `za.voc`, `za.cape`, gate `za-cape.tsv`)

**`za.mjc`: class D.** Its only table is September 2026, 30 days: fit, with nothing held out. The fitted
margins (−25..−27; Asr −75: the MJC's Asr is about a minute before the exact one) are thin for one month.
Nearest-rounding bounds are used instead: starts −10, Asr −60, sunrise +10. The gate showed 0 early and
0 late ends, worst 1 on every event.

**`za.voc` (the community calendar): class D.** The data is Wembley's calendar in two parts: 2026
(21 June to December) for the fit, and 2027 (January to 5 June) held out. masjids.co.za's 2026 relay and
Wembley's own September page are also held out.

Wembley's table has three Asr faults, left out and named in the split files' headers. On 23 and 25 June
its Asr is 4 min after its neighbours and after the same calendar as masjids.co.za relays it, and on
7 November (the Shafi'i table) it is 3 min after. The Task 5 margin of +180 s was covering these.

2027 came up early at Dhuhr (1), Maghrib (20) and Isha (32), so the margins were widened to the whole
year's. In seconds: fajr −23, sunrise −35, dhuhr −16, asr −7, maghrib +5, isha +37, end of eating −50
(unchanged).

The gate over 271 place-days (149 held out) showed 0 early and 0 late ends, worst 1–3 (Maghrib 3 on
3 days).

**`za.cape`: class C.** The check covers the month all three tables are held at the Cape Town point,
September 2026: the MJC's, the community calendar's (the masjids.co.za relay) and Jamiat's Cape Town
table with its Shafi'i Isha.

The Maghrib cap (R38) caps at the MJC's when the members spread past 2 min. The three tables print
Maghrib from sunset to sunset + 3. Their printed Maghribs agree within 2 min on 20 of the 30 days, where
Maghrib must be the latest of them. Each member's own never-early margin spread the engine's members past
2 min on those days, so it capped Maghrib at the MJC's, 2 min before Jamiat's and the calendar's: 20
early days.

The MJC member in `za.cape` therefore takes its Maghrib at sunset + 2. The `za.mjc` entry itself is
unchanged. The engine's members now agree, and Maghrib is the latest of the three.

**Beyond the MJC table's reach (rulings R87 and R88, 28 September 2026).** Ruling R87 briefly gave a
cautious member that carries its entry's method with a change of its own an edge built on that change
(`UnitSet.outsideOf`, made for Belgium's EMB member and its + 7 Maghrib). The MJC member is such a member:
beyond the MJC table's class-D reach (about 69 km from the Cape Town point), in the NE and SE corners of
the Cape Town box (about 5 % of it, e.g. −33.47, 19.08 and −34.38, 19.08), its edge then carried its own
Maghrib, sunset + 110 s + the edge's 60 s, where it had carried the MJC's own − 10 + 60 s: `za.cape`'s
Maghrib up to 4 min later there on every day of 2026. Ruling R88 removed the hook with Belgium's + 7, so
beyond the reach the member is the MJC's own edge again, as before R87 (`PointTableTest`); within the reach
it carries its + 110 s as it always did. No gate row or golden point lies in those corners.

The gate over 30 place-days showed 0 early and 0 late ends, worst: Fajr 1, sunrise 2, Dhuhr 1, Asr 1,
Maghrib 4, Isha 1, end of eating 1.

**Recorded exceptions (`za.cape`).**

- **Maghrib, 4 min.** The three tables print Maghrib from sunset to sunset + 3. Maghrib is the latest
  member's, never before any, so on the days the MJC prints it 3 min before the others it runs up to
  4 min after the MJC's.
- **Sunrise, 2 min.** Sunrise is the earliest of the three members', each a little before its own
  table's: up to 2 min before the MJC's printed sunrise.

## France

### Grande Mosquée de Paris (`fr.gmp`, gate `fr-gmp.tsv`)

**Class D_AUTHORITY.** The mosque's 2026 table as its Mawaqit page carries it is split in two:

- January to September: the 2026 method, the Moonsighting Committee's seasonal Fajr, Zuhr + 5, Maghrib
  + 3, Isha = sunset + 90. This part is the fit.
- October to December: the page still carries the earlier method. Its October rows are the mosque's
  October 2025 rows from Zuhr on: Fajr about 18° tapering, Isha 15.3–16°, Zuhr + 1. This part is held
  out.

Also held out: the mosque's own September 2026 page and its nine October 2025 days.

The Isha rule (TwilightCurves' GMP section) is now sunset + 90 (the table's Maghrib + 87; Task 5's + 93
was 3 min late). From 2 October to 30 December it is the later of that and 16°, so it is never before
either method. The neighbour envelope carries the later value to 1 October and 31 December.

On the earlier method's rows, the Fajr start and Zuhr are not checked. The 2026 method's are 7–16 and 4–5
min later by design, so they are never early there, and checking them would only measure the distance
between the two methods. Their Fajr is checked as the end of eating (`E`, ruling R80): a reader of the
page stops eating then.

**Holdout.** Fitted on January to September, the held-out rows came up early: Maghrib on 28 days and
Isha on 16, all against the earlier method. The margins were widened by the excess plus 5 s. In
seconds: fajr −11, sunrise +22, dhuhr and the other starts −23, asr +36, maghrib +29, isha +12.

**The end of eating (ruling R80).** The GMP prints no imsak, so the fast begins at its Fajr. It is now
the GMP's own dawn read as an end, `EndOfEatingDawns.gmpParis`: the sun's depression at each printed Fajr
of its 2026 page, its own September page and its October 2025 rows, the largest of them on each month
and day (365 of 366 slots printed), widened over the neighbouring days and rounded up to a hundredth of a
degree. It is an envelope of depressions, not the table (R42). From October it follows the page's earlier
method, whose Fajr is the earlier. R39's 18° dawn came up to 114 min before the printed Fajr in June.

- **Floored at its own rule (R80's fix round).** Away from the mosque, `TwilightCurves.endOfEating` carries
  these dawns south by the fraction of the night, which holds for an angle or a fraction-of-night rule. The
  Moonsighting Committee's seasonal minutes are neither: in January its Fajr takes a larger share of the
  night further south. So in southern Corsica the carried end came after the GMP's own method on 86
  January days from 2025 to 2032 (by up to 33 s at Propriano, 1 min late against its minute on four
  days); a sweep of France from 41.3° N at 0.05° steps found 52 late place-days from 2026 to 2029 (as
  many for France's cautious entry), all south of 42.5° N. Inside the table's reach, where the end is
  the earlier of the mosque's point and the user's but both read on the mosque's curve, it came up to
  57 s after that rule at the user's own point (south of the mosque in winter, east-south-east of it in
  summer; 23 late days in four years at 55 km south). Each slot is now also no shallower than the rule
  itself read as an end (`TwilightCurves.moonsightingAsEnd`: adhan2's minutes at that latitude, each
  date's own day of the year, the sun's declination at the moment, the largest over a leap cycle), at the
  user's latitude beyond the reach and, at the mosque's point, at the south and north of its 55 km reach
  (`PlaceCurves`' floor). After it, the sweep of
  France finds no late place-day from 2026 to 2029, nor do runs from 2025 to 2048 over Corsica and the
  south (0.1° steps), the reach's south (0.03°) and its south-east, nor a ring of 32 points 25 and 50 km
  around the mosque (2026 to 2029). At the reach's edge the end comes at most 9 s after the rule's exact
  moment, before its minute.
- **The margin.** The end margin fitted on January to September is +54 s (decided 28 January). adhan2's
  Moonsighting Committee Fajr at the mosque's point matches 271 of those 273 rows to the minute. Run for
  every year from 2025 to 2048 on the mosque's own dawns alone, it allowed at most +19 s (+20 was late on
  20 June 2028): a table's depressions carry its minute rounding, and the leap cycle moves it. The margin
  is +13 s, that +19 less the Fitter's 5 s safety and a second. The floor leaves more room at the mosque
  (adhan2 now allows +77 s there), but the margin is also the end's inside the reach, where +13 s leaves
  the 9 s above; it stays +13.
- Where the page changes method, each change is carried over the two days either side: 1–2 January
  (the page's December is the earlier method) and 29–30 September.

**Gate over 374 place-days, 101 held out:** 0 early and 0 late ends. The worst per event: Fajr 2,
sunrise 1, Dhuhr 1, Asr 2, Maghrib 1, Isha 5, end of eating 16 (held out 8).

| end of eating, worst min before the printed Fajr | Jan | Feb | Mar | Apr | May | Jun | Jul | Aug | Sep | Oct | Nov | Dec |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R39 (the 18° dawn) | 17 | 12 | 16 | 30 | 73 | 114 | 111 | 41 | 19 | 6 | 1 | 1 |
| R80, its own dawns alone (first round) | 16 | 1 | 1 | 1 | 2 | 2 | 2 | 2 | 8 | 2 | 1 | 1 |
| R80, floored at its rule across the reach | 16 | 2 | 2 | 2 | 3 | 4 | 4 | 3 | 8 | 2 | 1 | 1 |

The floor costs the mosque's own point up to a minute from February to May and in August, and two in June
and July: 22 days are more than 3 min before its printed Fajr, 18 of them 4 min in June and July, besides
1 and 2 January (16) and 29 and 30 September (8 and 7). With the floor at the mosque's latitude alone the
first round's figures stand, and users 40–55 km south or south-east of it keep the 57 s above; that is
the choice to make if the table printed for the mosque, rather than its rule at the user's own point, is
taken as the authority inside its reach (R44).

**Recorded exception (`fr.gmp`), end of eating, 16 min.** The fast begins at its own Fajr, within 4 min
of it, the end holding for everyone within its table's reach, but where its page changes method: the
change is carried over the two days either side, so the end comes up to 16 min before its printed Fajr
on 1 and 2 January and 8 before it on 29 and 30 September.

**Recorded exception (`fr.gmp`), Isha, 5 min.** From October the Grande Mosquée's page still carries its
earlier method, whose Isha moves from about 15.3° to 16° over October. Isha is taken at the later of its
2026 method and 16°, never before either, and so runs up to 5 min after the earlier method's in early
October.

### France (`fr.cautious`, gate `fr-cautious.tsv`)

**Class C.** There is one table for each of the three families, from the research's Île-de-France survey,
all read at the Grande Mosquée's point. The other two mosques are 10–13 km east of it.

- fr.twelve: Drancy (12.7°, + 5.3, + 3).
- fr.gmp: the Grande Mosquée's own table.
- The flat 15° family: Al-Amel Neuilly-Plaisance. This family is now its own convention, `fr.fifteen`,
  replacing the Other method ISNA. Al-Amel's Asr runs 0–2.9 min after the exact one (Drancy's swings
  from −3.4 to + 2.3 over the year), and the ISNA member was 1 min early on 159 days. The member takes
  Asr + 3.
- **Diyanet's European method, a member since ruling R87** (28 September 2026), as in Belgium, the
  Netherlands and Germany: its own entry's Paris table and units. No fourth table is held at Paris for
  this gate (Diyanet's Paris table is gated under `tr.diyanet.europe`); the member is checked by the
  survey below.

**The end of eating (ruling R80).** It is the earliest member's end. From late April to August the flat
15° family's 15° dawn; the rest of the year now Diyanet's Paris dawns (its 18° and its takdir), up to 26
min before the Grande Mosquée's own Fajr in April and August and about 12 in winter; where the Grande
Mosquée's page changes method, its own dawn (16 min on 1–2 January, 8 on 29–30 September). Against
Al-Amel's table as the gate reads it (at the GMP's point, 13 km west of the mosque, where the mosque's
own dawn comes up to 40 s earlier), the 15° member's end was 1 min late on 19 days in late April and May
before its end margin of −34 s, fitted with the end formula and 5 s safety (`FIFTEEN_END_MARGIN`).

**Gate over 365 place-days:** 0 early and 0 late ends. Worst per event: Fajr 15, sunrise 8, Dhuhr 2,
Asr 4, Maghrib 3, Isha 38, end of eating 29.

**Recorded exceptions (`fr.cautious`).**

- **End of eating, 29 min.** From late April to August the end is the flat 15° family's 15° dawn. Al-Amel's
  table, the family's one held, leaves 15° for a later summer Fajr of its own, so the end comes up to 29
  min before the earliest printed Fajr of the three tables in June. The rest of the year it is Diyanet's
  Paris dawn, up to 26 min before the Grande Mosquée's Fajr in April and August and about 12 in winter.
- **Fajr, 15 min.** The 12–13° family prints Fajr from about 12° to 13° (Drancy's at 12.7°). Its member
  takes 12°, the family's latest, never before any of them, so against Drancy's table Fajr runs up to 7
  min late; from late May to June Diyanet's Paris takdir is the latest member, up to 15 min after Drancy's.
- **Sunrise, 8 min.** Sunrise is the earliest member's, Diyanet's (the sun's less 7), up to 8 min before
  the three families' tables, which print the sun's own.
- **Isha, 38 min.** The flat 15° family prints Isha at 15°, which in a Paris June is after midnight,
  while the Grande Mosquée's is sunset + 90 and Drancy's and Al-Amel's summer Isha follow earlier rules
  of their own. Isha is the latest member's, so in summer it runs up to 38 min after the latest of those
  three tables; in winter Diyanet's 16° adds up to 5 min after the 15° family's.
- **Asr, 4 min; Maghrib, 3 min; Dhuhr, 2 min.** Each family's tables differ among themselves: Drancy's
  and Al-Amel's Asr swing by 3–5 min over the year, and the 12–13° family prints Zuhr + 5 and Maghrib
  + 3..4. Each member takes its family's latest (Diyanet's + 4 at Asr).

**The survey (ruling R87; `survey/fr-cautious/`, `ContinentalMawaqitSurveyTest`).** 12 Mawaqit calendars
in Lyon (6) and Marseille (6), each at its own point, 4,380 place-days, 5 cells left out (Bilal
Marseille's and Institut Rissalat's 23 April Maghrib, 58 min off both neighbours; their 23 August Asr,
10 min off; Rissalat's 7 March Isha, an hour off). Before Diyanet was a member the Lyon DITIB calendar,
which prints Diyanet's method to the angle, ran Isha up to 11 min early on 124 days, Asr 2 on 115,
Maghrib 4 on 333, the end of eating 22 min late on 269 days and sunrise 7 min late on all 365; three
other Lyon calendars ran sunrise a minute late on 10–32 days and Isha 3–7 min early (koba, m-essalem).
With Diyanet: **0 early starts and 0 late ends on 8 calendars**, and these recorded outliers
(`outliers.tsv`):

1. **Isha, El-Amine Lyon (365 days, up to 12 min).** A flat 17° Isha all year (its Fajr a flat 15°),
   one calendar of the twelve; absorbing it would move Isha up to 12 min later all over France.
2. **Isha, Koba Lyon (72 days, up to 3 min) and M-Essalem Lyon (5 February days, up to 7).** A 16°
   calendar's rounding, and a step in one table.
3. **End of eating, the Lyon DITIB calendar (71 days, up to 7 min, May–July).** Diyanet's Lyon table,
   as Mawaqit prints it, begins its fast at its own takdir Fajr, a larger share of the night than the
   Zürich dawns Diyanet's member carries to Lyon (Diyanet's Lyon table is not held): a Diyanet-Europe
   question beyond its city tables (see Belgium's, below), not a French member.
4. **Maghrib, the Lyon DITIB calendar (333 days, up to 4 min, the cap).** Diyanet's sunset + 7 on its
   own sun. France's Maghrib stays capped at the 12–13° family's + 4 (spec §3.6), which 78 % of the
   Île-de-France survey prints; one calendar of the twelve.

**What Diyanet as a member costs everyone in France** (2026, mean/max minutes by month, `cost.txt`):
sunrise 5–7 min earlier at Paris, 6–8 at Lyon and Marseille, and 11–14 at Lille, where the nearest
Diyanet table is Brussels' and ruling R45's ends-only fixed point bounds sunrise by it; the end of eating
earlier by 10–19 (max 26) at Paris from January to April and in August–September, 7–21 (max 29) at
Lyon, 1–12 at Marseille, 15–28 (max 38) at Lille; Isha later by up to 10 at Paris (August), 13 at Lyon
(June–July), 11 at Marseille, 52 at Lille in June (Diyanet's generic 22 % of the night beyond its
tables where 15° is barely reached); Asr up to 3 later; Fajr later only where Diyanet's takdir passes
the 12° dawn: Paris June 6/9, Lille May–July 5–26 (max 30). Fajr, Dhuhr and Maghrib are otherwise
unchanged. Lille's figures are Diyanet's own entry's behaviour beyond its city tables (R45), which the
member now carries into `fr.cautious`; a Diyanet Lille table (Diyanet prints one) would end them.

## Belgium

### EMB (`be.emb`, gate `be-emb.tsv`)

**Class D_AUTHORITY.** The table shows clock times in its summer rule. They are fitted in EMB's
TwilightCurves section (the clocks are Brussels summer time):

- Fajr, from 1 May to 31 July: the later of 18° and the earlier of a clock-time floor and the MWL
  proportion from 45°. Task 5's curve had the proportion alone and ran up to 49 min late in May.
- Isha, from 1 May to 31 August: the earlier of 18° and the later of a clock-time cap (a few minutes
  later from July) and the 45° proportion of 17.5°. EMB's own June Isha is 0.26–0.27 of the night,
  between the proportions of 16° and 17°. The real 18° alone ran up to 114 min late, and the fitted
  margin left July early.

The data is its 2026 table, January to June for the fit and July to December held out, plus the October
page held out.

**Holdout.** Fitted on January to June, July to December came up early at Isha (33 days) with the old
Isha rule. With the new rule, the margins were widened to the whole year's. In seconds: fajr −1,
sunrise +5, dhuhr −22, asr +40, maghrib −24, isha −7, end of eating +54 (its own earliest dawns, R39).

**Gate over 360 place-days, 183 held out:** 0 early and 0 late ends. The worst per event: Fajr 7,
sunrise 2, Dhuhr 1, Asr 2, Maghrib 1, Isha 9, end of eating 7.

**Recorded exceptions (`be.emb`).**

- **Fajr and the end of eating, 7 min.** From mid-May to 1 August EMB holds Fajr at one clock time, and its
  fast begins then. The curves carry a clock time as the sun's depression, which the
  day-either-side envelope (R28) moves by a day's change. So Fajr runs up to 7 min after EMB's, and the
  end of eating up to 7 min before it, in May.
- **Isha, 9 min.** From May to August EMB's Isha stops at a clock time or follows its own proportion.
  The curve takes the later of the clock time and the 45° proportion of 17.5°, never before EMB's, and
  runs up to 9 min after it in May.

### Belgium (`be.cautious`)

**Class C.** The check covers EMB with Diyanet's Brussels table on the 2026 days both are held,
25 September to 25 October (31 place-days). There were 0 early and 0 late ends. Every start is within
1 min of the latest member. Maghrib is capped at EMB's, which is the most followed.

**Maghrib (rulings R87 and R88, 28 September 2026).** Of the 11 Belgian Mawaqit calendars research-mawaqit
surveyed outside the Brussels copies (Antwerp, Ghent, Charleroi, Liège, Brussels), 5 (the Diyanet
mosques) print Diyanet's Maghrib: sunset + 7 on Diyanet's own sun taken at 0h UT, which is sunset + 6 in
spring to + 9 in autumn on the exact sun (Beaux-Arts Charleroi's is + 9 to + 9.6 all year, an offset of
its own). Belgium's Maghrib is spec §3.6's cap: the two members spread past 2 min on every day (Diyanet's
3–7 min after EMB's), so it is the most-followed member's own minutes, EMB's sunset + 2, which 42 of 64
Brussels mosques follow. Against those five calendars it comes 2–7 min early on every day of 2026
(`maghribCap`, recorded below): the cap's own cost, the owner's decision of 26 September (the latest
sunset definition plus physical margins, never the sum of every mosque's precaution). Track L had instead
given the EMB member sunset + 7, through an edge hook (`UnitSet.outsideOf`) so that the change held beyond
the Brussels table's reach, which made Belgium's Maghrib the later of the two members' and never before
the Diyanet calendars', at 5–7 min after EMB's own table for EMB's own majority; ruling R88 returned it to
the cap and removed the hook (which also returns Cape Town's MJC member beyond its table's reach to the
MJC's own edge, see Cape Town). **No exception (`be.cautious`):** against EMB's table the gate's Maghrib is
within class C's minute (31 days, 0–1 min).

**The survey (`survey/be-cautious/`, `ContinentalMawaqitSurveyTest`).** 12 calendars, one left out whole
(Al-Ghofrane Anderlecht: Isha equals Maghrib from May to July, its Fajr does not follow the sun); 11 used,
4,015 place-days, 1,079 cells left out. **Faults:** four calendars' Fajr column is a congregation time,
not a start, from late February to early October (Taoubat Antwerp 26 Feb–27 Sep, Sakina Charleroi
18 Feb–5 Oct, Attakwa Liège 21 Feb–2 Oct, Beaux-Arts Charleroi all year): one clock time for months on
end, after sunrise from May to July (as research-uk left Adam Mosque's out), and again for 10–22 days
after the October clock change. Research-mawaqit read these as a Maghreb-diaspora seasonal Fajr family
(brief L item 3); they are not, and outside those months the three print 18° like EMB. So **Belgium gains
no Fajr member.** After the faults: **0 early starts and 0 late ends**, but for these recorded outliers:

1. **Fajr, Masjid Bilal Liège (310 days, up to 30 min), Fatih Bruxelles (365, up to 80) and Pakistan
   Islamic Cultural Centre Bruxelles (365, up to 71).** Mawaqit's own minutes-before-sunrise Fajr setting
   (90, 45 and 60 min), one admin's simplification each and not a convention (research-mawaqit section
   2): not evidence for a Fajr change.
2. **Fajr, Diyanet's followers outside Brussels: El-Gazali Antwerp (124 days, up to 4 min), Fatih Gent
   and Anwar-e-Madina Gent (123 days each, up to 5), April to August.** They print Diyanet's own
   Antwerpen and Gent tables, which are not held: their summer takdir Fajr, about a fifth of the night
   before sunrise, comes up to 4 min after the Brussels table's curve carried to Antwerp as a depression,
   and up to 5 after the generic edge beyond the Brussels table's reach, which keeps Fajr no earlier than
   19 % of the night. This is a Diyanet-Europe question (ruling R44's city tables for its other cities;
   research-mawaqit section 5 left it open for Germany, where the DITIB calendars carry the section-2
   setting), not a Belgian member: fetching Diyanet's Antwerpen, Gent, Liège and Charleroi tables, as
   the twelve held were, would end it.
3. **Isha, Masjid Sakina Charleroi (95 days, up to 107 min, May–August).** Isha at 18° while the sun
   reaches it and at the middle of the night when it does not, the same minute as its Fajr; absorbing it
   would empty the Isha window in June and July (as the UK's midnight-Isha calendars, ruling R75).
4. **Isha, Attakwa Liège (39 days, up to 24 min, May and August):** 18° as long as the sun reaches it,
   later than EMB's summer cap; one calendar. **Beaux-Arts Charleroi:** its own Isha up to 4 min after
   EMB's cap on 16 days and its November Asr a minute.
5. **A minute here and there:** Shahjalal's and Taoubat's Isha on 4 November; El-Gazali's end of eating
   on 19–20 April and its sunrise on 4 spring days, Fatih Bruxelles's sunrise on 9 (Mawaqit's own Diyanet
   computation against Diyanet's Brussels curve); Anwar-e-Madina's Hanafi Asr, the option, on 5
   late-August days (2 min).
6. **Maghrib, the five Diyanet calendars (365 days each, `maghribCap`):** spec §3.6's cap at EMB's own
   sunset + 2 (ruling R88, above) comes before Diyanet's + 7 on every day: El-Gazali Antwerp by 2–7 min,
   Fatih Bruxelles 4–7, Fatih Gent and Anwar-e-Madina Gent 3–7, and Beaux-Arts Charleroi 4–7 (its own
   + 9 to + 9.6). Least in December to February, most from July to November.

**EMB's end of eating south of Brussels (brief L item 6).** EMB's Brussels dawns are carried to another
latitude by the fraction of the night (`TwilightCurves.endOfEating`). Checked at Arlon, Namur, Charleroi
and Liège over 2026–2029 against EMB's own rule read as an end at each place (18°, and from May to July the
later of 18° and the earlier of its clock-time floor and the proportion from 45°, `TwilightCurves.fajrAsEnd`,
`PlaceCurves.fajrRuleAsEnd`; `ContinentalCautiousTest` re-checks all of this):
at Namur, Charleroi and Liège the carried end is never after the rule's own dawn; at Arlon it is after it
on one day a year, 8 July, by 16 s. Beyond the table's reach (Arlon, Namur, Liège: the edge, its −30 s end
margin) the minute shown is never past the rule's moment (33 s before it at the closest, at Arlon). Within
the reach (Charleroi) the table's own end rides with its fitted + 54 s margin (ruling R44), as at Brussels
itself: there, as at Brussels (603 of 1,461 days), the minute shown passes 7g's rule on 321 days by up to
58 s, since the rule only approximates the table and the gate holds Brussels's end to EMB's printed times.
It is never after Brussels's own end (ruling R89, 28 September 2026): before it, on 8 April and May days of
2027–2029 (none in 2026), Brussels's end, a minute past its own Fajr, was held to that Fajr (spec §3.3's
order) while Charleroi's, whose Fajr shown is the later of the two points', was not, and Charleroi's came
a minute after. Now the day the same method shows at the table's own point bounds the sunrise, end of
eating and imsak shown at every point beside it, after every step that moves an end (`DayComputer`,
point 7; `PointTableTest` walks 2026–2030 across the Brussels, Zürich, Oslo and Tromsø reaches). It moved
no gate place-day (0 of 137,835: every gate row is at its table's or unit's own point); in the golden
vector it moved three place-days at one point within the Tromsø calendar's reach, the end of eating 3–4 min
earlier on mornings IRN's Fajr is declared not followed (see Tromsø). So
no floor is added: flooring EMB's end at its rule across the reach, as ruling R80's fix round did for the
Grande Mosquée, would move Brussels's own end up to 288 s earlier on 108 days from January to July,
because 7g's rule only approximates EMB's printed summer floor (the table is the truth at Brussels).
Rabita's end at Malmö and Lund (Sweden), carried south from Helsinki, is never after Rabita's own Fajr
curve at either place over 2026–2029 (0 of 1,461 days each; slot by slot its depression is at least the
curve's, and the minute shown is at least 18 min before the curve's moment).

**Brussels, Diyanet (the weekly monitor's capture of 28 September 2026).** Diyanet's Brussels table as
the member `tr.diyanet.europe` at EMB's point (29 September – 29 October 2026 and 2027; Maghrib not
checked on it, as at Antwerpen and Gent): 0 early, 0 late ends, no figure moved; `be.cautious` holds
1,192 place-days (was 823). The member's days at Brussels now run to 29 October, but EMB's member row
still ends on 25 October (the slice of the days both tables were held on), so Brussels's and Antwerp's
pages stay held from 26 October (ruling R115); and no Diyanet capture holds 30–31 October yet.

## The Netherlands (`nl.cautious`, gate `nl-cautious.tsv`)

**Class C.** At Amsterdam, 25 September to 25 October 2026, there are two tables:

- the Moroccan calendar, as Al-Ihsane's Mawaqit page carries it;
- Diyanet's Amsterdam table.

No MWL table is held. Al-Ihsane's calendar is the one described in the research. Its Fajr goes from
16.2° in winter to 14° in May and August, with a clock-time floor in summer. Its Isha goes from 15.4° to
14.4°, with a clock-time cap. Its Zuhr is + 1..2 and its Maghrib + 1.5..3.8.

The other Amsterdam table (`mw-ahibba`) is a different family: Fajr = sunrise − 90 all year, and Isha
= Maghrib in summer. It is not a member.

**Maghrib (ruling R87, 28 September 2026).** Of the 12 Dutch Mawaqit calendars research-mawaqit surveyed
(Rotterdam, Den Haag, Utrecht, Eindhoven, Amsterdam), 7, Turkish and Arab mosques alike, print Diyanet's
Maghrib: sunset + 7 on Diyanet's own sun taken at 0h UT, sunset + 6 in spring to + 9 in autumn on the
exact sun. The members' Maghribs never agree within 2 min (MWL's is the sun's own), so Maghrib is always
capped at the Moroccan member's, the most followed (spec §3.6), which at + 3 was up to 5 min before
those calendars on all 365 days. The Moroccan member now carries their Maghrib as a floor, month by
month: sunset + 7 from January to May, + 8 in June and December, + 9 from July to November (its
`monthlyOffsets`), never before any of the seven. Against the Moroccan calendar's own + 1..4 it runs 4–8
min late. The floor stays under ruling R88, which returned Belgium's Maghrib to spec §3.6's cap: here
Diyanet's calendars are the majority of the sample (7 of 12), so their Maghrib is the most-followed
practice, and the floor sets the most-followed member's own minutes at it rather than stacking every
mosque's precaution; in Belgium the Diyanet calendars were 5 of 11 against EMB's 42 of 64 Brussels
mosques, and the cap stands at EMB's.

The gate showed 0 early and 0 late ends over 31 place-days.

**Recorded exceptions (`nl.cautious`).**

- **Fajr, 16 min.** The Moroccan calendar's Fajr moves from about 16° in winter to 14° in May and
  August. Its member takes 14°, the calendar's latest, never before it, and so runs up to 16 min after
  it in autumn.
- **Maghrib, 8 min.** The floor above against the Moroccan calendar's + 1..4 (the gate: 7–8 min on its
  31 days).
- **Isha, 6 min.** Isha is the latest member's, MWL's 17°, whose own table is not held here: up to
  6 min after the Moroccan calendar's and Diyanet's.
- **End of eating, 2 min.** The members' own margins.

**The survey (`survey/nl-cautious/`, `ContinentalMawaqitSurveyTest`).** 12 calendars, 4,380 place-days,
30 cells left out (Pakistan Islamic Centre Rotterdam's 22–26 October rows, an hour off before the clock
change). With the floor: **0 early starts and 0 late ends**, but for these recorded outliers:

1. **Fajr, Alfarouk Utrecht (101 days, up to 16 min, May–August).** 18° all year but in summer about
   0.18 of the night before sunrise (8.7–11.7°), later than Diyanet's Amsterdam takdir and the Moroccan
   member's 14°: one calendar of the twelve. Brief L item 1 asked to absorb it only if the Maghreb-family
   curve of item 3 were also given to the Moroccan member; Belgium's family turned out to be
   congregation times and Germany's curve (10.3° in June) would not reach it, so it is recorded:
   absorbing it would move everyone's Fajr up to 16 min later from May to August.
2. **Fajr, İskenderpaşa Rotterdam (365 days, up to 58 min):** Mawaqit's 40 minutes before sunrise
   (section 2), not evidence.
3. **Maghrib, Hicret Den Haag (352 days, up to 2 min):** sunset + 8.4 all year, an offset of its own a
   minute or two beyond Diyanet's (its Fajr is Mawaqit's 110 minutes before sunrise); **Pakistan Islamic
   Centre Rotterdam** (22 days in November–December, 1 min): its own + 7.6..8.3.
4. **End of eating, Mevlana Amsterdam (8 days, up to 14 min) and El-Islam Den Haag (3 days, up to 8),
   20–25 July:** Mawaqit's own Diyanet computation returns to the real 18° dawn days before Diyanet's
   Amsterdam table does; Diyanet's own table is the member.
5. **Pakistan Islamic Centre Rotterdam:** its own late Isha from October to April (18° to 21.5°; 181
   days, up to 30 min), its September Dhuhr + 5.6 (8 days, 2 min), eight February days with Fajr to
   18.7° (2 min before the 18° dawn), and the Standard Asr option against its December Asr (28 days,
   2 min). **Alfarouk's** Isha a little after 17° (69 days, 2 min); **El-Tawheed's** on 10 summer days
   (2 min).

**What the floor costs everyone in the Netherlands:** Maghrib is now sunset + 7 to + 9 (+ 30 s), 4–8 min
after the Moroccan calendar's + 1..4 and within 0–3 min of Diyanet's own, all year (`cost.txt`).

**Not a row: the weekly monitor's Amsterdam capture (28 September 2026).** As a second Diyanet member
row (29 September – 29 October 2026 and 2027) it would leave Diyanet's table alone at Amsterdam after
25 October, while the cautious Fajr, Isha and end of eating follow the other members (the Moroccan
calendar's Fajr, MWL's Isha), so against Diyanet's alone they read as the members' spread: Fajr over
its 16 min on 262 days (up to 37; 26–29 October among them), Isha over its 6 on 303 (up to 131), the
end of eating over its 2 on 127 (up to 123 min early, in 2027). It is left out, and nothing is
loosened: the member row waits for the Moroccan calendar's own table for the same days.

## Germany (`de.vikz`, `de.cautious`, gate `de-cautious.tsv`)

**`de.vikz` (R49 item 2).** It carried Diyanet Europe's end margin (+10 s, fitted at Diyanet's own
cities) at the user's point. With no VIKZ table, and no point of its own, it now takes
`min(Diyanet's, SAFE_END)` (−30 s), as R44 requires. No VIKZ table is held, so `de.vikz` is not gated.

**`de.cautious`: class C.** At Berlin there are two tables, both the member Diyanet: Diyanet's own
(25 September to 25 October 2026) and IGMG's (September and October 2026, byte-identical to Diyanet's).
VIKZ's times are Diyanet's except Isha, so the Diyanet tables stand for both on every event but Isha.
Isha is checked against Diyanet's tables (review I5). The cautious Isha is VIKZ's, 10 min after
Diyanet's by its rule, and no VIKZ table is held, so it runs up to 11 min after Diyanet's.

**Two members more (ruling R87, 28 September 2026),** from the 11 German Mawaqit calendars
research-mawaqit surveyed outside the Berlin copies (Köln, Frankfurt, Hamburg, München, Duisburg,
Nordhorn, Berlin; a twelfth, Licht des Islam Gelsenkirchen, left out whole: its Isha frozen at one clock
time in six months of the year, before Maghrib in June and July):

- **`de.latedawn`, the late-dawn family** (`DeLateDawnCurve`, named "Late-dawn timetable" like the
  UK's): Fajr about 13° below the horizon from September to April and about 10.3° in June, the latest
  printed Fajr of Hilaly Köln (a seasonal 14–16.6° in winter and 10.3–11° in June, the shape of the
  Dutch Moroccan calendar) and Rahma Moschee München (a flat 13°), both Moroccan mosques whose Fajr
  ran up to 26 and 36 min after the app's on 283 and 298 days. Two of eleven (spec §3.6). The curve is
  their latest printed Fajr per slot (the smaller depression, then the smallest of the slot and its
  neighbours, floored to 0.001°; research-uk's method); where the sun does not reach it, the family's own
  June share of the night, 0.206 (nowhere in Germany in a common year). It leads on Fajr alone: its Isha
  (Diyanet's generic 16°, else 22 % of the night, on Diyanet's sun taken at 0h UT like the other
  members', so that on the night 16° is barely reached all four agree it is not: on the exact sun a 15°
  or 16° Isha barely reached lay at the sun's lowest point, an hour or two after VIKZ's, at Köln and
  Berlin in June and July; VIKZ's + 10 then always passes it), Zuhr + 4, Asr, Maghrib + 4 and sunrise
  − 2 are never the ones shown (`ContinentalCautiousTest`). The curve was derived on the exact sun; at
  dawn, three hours from 0h UT, the two models differ by seconds. Germany's Maghrib stays Diyanet's + 7: the latest member's
  where the members' Maghribs agree, and spec §3.6's cap, the most-followed member's, where Diyanet's
  0h-UT sun puts its own past the others' by more than 2 min in autumn.
- **`de.eighteen`, the 18° family** ("18° timetable"): Pakistani and Afghan mosques whose fast begins at
  the 18° dawn and, on the nights the sun does not get 18° low, at the middle of the night. Darul Aman
  and Pak Muhammad Jamia Masjid in Berlin print their Fajr, which is also when their fast begins, at the
  middle of the night from May to July (0.499–0.501 of the night), Pak Islami Majlis Hamburg at three
  tenths, and Diyanet's takdir imsak was up to 140 min after them on 127–146 days. Three of eleven. Its
  fast begins at the 18.4° dawn (Hamburg prints up to 18.35° in April and August, Berlin's two up to
  18.08°) or the middle of the night (`EndOfEating.DawnOrMiddle`, as the UK's Karachi family). Its times
  are Diyanet's European method as VIKZ carries it, so that its starts tie with the members already there
  and never lead; on those nights its own Fajr is the middle of the night, earlier than every other
  member's. It leads on the end of eating alone.

**Gate over 61 place-days:** 0 early and 0 late ends; Fajr worst 44, end of eating 5, Isha 11.

**Recorded exceptions (`de.cautious`).**

- **Fajr, 44 min.** Fajr is the latest member's: the late-dawn family's 13° from September to April, up
  to 44 min after Diyanet's Berlin table in early September as its takdir ends and about 30 in October;
  in summer VIKZ's, Diyanet's generic European method, no earlier than 19 % of the night, up to 12 min
  after the table.
- **End of eating, 5 min.** The 18° family's 18.4° dawn, up to 5 min before Diyanet's Berlin imsak.
- **Sunrise, 2 min.** Diyanet's European method's own margins.
- **Isha, 11 min.** VIKZ's, Diyanet's Isha + 10 min by its own rule.

**The survey (`survey/de-cautious/`, `ContinentalMawaqitSurveyTest`).** 11 calendars, 4,015 place-days,
0 cells left out. With the two members: **0 early starts and 0 late ends**, but for these recorded
outliers:

1. **Fajr, DITIB Zentralmoschee Köln (228 days, up to 30 min), DITIB Duisburg (365, up to 30) and Pak
   Muhammadi Frankfurt (8 May days, 1 min):** Mawaqit's minutes-before-sunrise setting (60, 60 and 90),
   not evidence (section 2). So whether Diyanet's generic edge is early for genuine Diyanet followers far
   from Berlin and München stays open in Germany; Belgium's Ghent calendars answer it there (above).
2. **Isha, Pak Muhammad Jamia Berlin (49 days, up to 117 min, May–July):** the middle of the night, the
   same minute as its Fajr; absorbing it would empty the Isha window.
3. **Maghrib, Pak Islami Majlis Hamburg (154 days, up to 2 min):** sunset + 8 to + 8.6 all year, an
   offset of its own beyond Diyanet's + 7; Pak Muhammad Jamia a minute on six late-winter days.
4. **The threshold night (the class of ruling R72), one night a year at a calendar, up to 7 min:** on the
   night 16° is last reached before the summer or first reached after it, VIKZ's Isha, 16° + 10 min, lies
   at the sun's lowest point plus ten, and the next end of eating, the middle of the night, at or before
   it, so Isha has no end that night (ruling R26): 2 July at Köln (7 min at the DITIB calendar, 4 at
   Hilaly), 15 July at Nordhorn (5), 28 May at Berlin (0–1). `ContinentalCautiousTest` walks 2026 at ten
   German places and holds it to two nights a year, May to July, 10 min, no day put back in order.

**What the two members cost everyone in Germany** (2026, mean/max minutes by month, `cost.txt`):

| | J | F | M | A | M | J | J | A | S | O | N | D |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Köln Fajr later | 32/34 | 31/32 | 33/36 | 25/38 | 0/7 | 0/0 | 0/5 | 17/34 | 35/39 | 32/33 | 32/33 | 33/34 |
| Köln end of eating earlier | 1/2 | 2/3 | 2/3 | 4/18 | 79/138 | 129/133 | 114/135 | 18/55 | 1/3 | 1/2 | 1/2 | 1/2 |
| Frankfurt Fajr later | 32/33 | 31/32 | 32/35 | 27/36 | 2/11 | 0/0 | 3/9 | 21/36 | 35/38 | 31/33 | 32/33 | 32/33 |
| Frankfurt end of eating earlier | 1/2 | 2/3 | 2/3 | 3/9 | 64/137 | 128/135 | 99/130 | 10/38 | 1/3 | 1/2 | 1/2 | 1/2 |
| Berlin Fajr later | 34/36 | 33/34 | 35/38 | 21/39 | 0/0 | 0/0 | 0/0 | 12/31 | 36/40 | 34/36 | 34/36 | 35/36 |
| Berlin end of eating earlier | 2/3 | 2/3 | 3/4 | 9/36 | 105/142 | 128/132 | 130/141 | 34/89 | 2/3 | 2/3 | 2/3 | 2/3 |
| München Fajr later | 31/32 | 30/31 | 31/34 | 31/37 | 11/20 | 8/11 | 14/20 | 28/39 | 34/37 | 31/32 | 31/32 | 32/33 |
| München end of eating earlier | 2/3 | 2/3 | 2/3 | 3/4 | 34/72 | 102/119 | 56/98 | 4/15 | 2/3 | 1/2 | 1/2 | 2/3 |
| Hamburg Fajr later | 34/36 | 33/34 | 35/38 | 16/36 | 0/0 | 0/0 | 0/0 | 7/26 | 35/39 | 34/35 | 34/36 | 35/36 |
| Hamburg end of eating earlier | 2/3 | 2/3 | 2/3 | 10/40 | 107/134 | 114/120 | 122/133 | 40/133 | 2/3 | 1/2 | 1/3 | 2/3 |

Fajr moves about half an hour later from September to April everywhere (Rahma's flat 13° against
Diyanet's 18°), little or nothing in June and July, where Diyanet's takdir is already later than the
curve north of Köln; the end of eating moves to the middle of the night from May to August (about 2 h
earlier; Ramadan only from about 2043, as in the UK) and 1–3 min earlier the rest of the year (18.4°
against 18°). Isha, Dhuhr, Asr, Maghrib and sunrise are unchanged. Brief L item 3 asked whether one curve
could serve Belgium and Germany: Belgium's family was congregation times (above), so there is one curve,
Germany's.

## Austria and Switzerland (`at.iggo`, gate `at-iggo.tsv`; `ch.fids`, gate `ch-fids.tsv`)

Both are point tables at Wien and Zürich (R44). Since 7b's merge, each is on Diyanet's own city curves:
IGGÖ's and FIDS's tables are Diyanet's Wien and Zürich tables. Each keeps its own margins, fitted on its
own table. Beyond each table's reach, the edge takes Diyanet's generic European method, a minute later.
As the brief says, there is no one-table-per-country treatment.

**`at.iggo`: class D_AUTHORITY.** The data is IGGÖ's own 2026 Wien table: six months, March, May, June,
July, September and December. March, May and June are the fit; July, September and December are held
out (review M4). The held-out days showed 0 early and 0 late ends, so the fit's margins stand. In
seconds: fajr +3, sunrise +31, dhuhr and asr −17, maghrib −18, isha −12, end of eating +49.

The gate over 184 place-days showed 0 early and 0 late ends. The worst per event is 3 (Fajr, Isha and
the end of eating) and 1 for the rest. The old exceptions (Fajr 22, Isha 63, end of eating 6), from
Diyanet's generic curves, are gone.

Since the monitor round (29 Sep 2026, brief D; 7b's Diyanet Europe section) Diyanet's city curves are
bounds on its own moment, and Wien's Fajr, Isha and end of eating keep Diyanet's curve margins (−29 s
for the two starts, +30 s for the end) in place of IGGÖ's fitted +3 s, −12 s and +49 s; the other
events keep IGGÖ's margins. The unit records three exceptions of 4 min, on Fajr, Isha and the end of
eating, with Diyanet's own reasons: in spring its takdir moves the dawn and Isha by several minutes a
day in steps, the curve takes the safe side of three neighbouring days so the leap cycle cannot make
a start early or the end late, and on takdir days the slot is the latest moment Diyanet's own could
be under its rounding. Over the same 184 place-days: still 0 early and 0 late ends; the worst is now 3
on Fajr and Isha and 4 on the end of eating (the About figure, R63, moves from 3 to 4 min), the exact
share on Fajr 40 %. IGGÖ's 2026 table, the year before Diyanet's held Wien rows, is that
construction's cross-year check.

**`ch.fids`: class D_AUTHORITY.** The data is FIDS's Zürich page for 25 September to 26 October 2026: 32
days, all fit, with nothing held out. It is on Diyanet's own Zürich curves.

- Margins in seconds: starts −20, fajr −25, asr and maghrib −23, isha −29, sunrise +33.
- The end of eating keeps Diyanet's own curve margin. One autumn month is too thin to fit an end on.

The gate showed 0 early and 0 late ends, every event within 1 min. No summer day is held, so FIDS's
summer takdir is measured only through Diyanet's own Zürich table (7b).

## Norway (`no.irn`, gate `no-irn.tsv`; R48, R82)

**Class D_AUTHORITY.** IRN's joint calendars (bonnetid.info) are printed city by city. Oslo, Trondheim
and Tromsø are held, all 2026, with no imsak and both Asr columns printed.

**The model (R48).** Outside summer IRN follows 16°/15°. In summer its Fajr goes to sunrise − 60 and is
then frozen at fixed times, and its Isha moves to Maghrib + 40 by a ramp, city by city. No rule
reproduces this, so Oslo's and Trondheim's units now carry their own calendars' curves (`IrnCurves.kt`).
Each curve is the sun's depression at each printed Fajr and Isha, placed on R28's slots and widened
over the day before and after. The curves are envelopes with a provenance note (R42), not times.

Zuhr, Maghrib and sunrise follow the month:

- Zuhr is + 4 in winter and + 9 from March to September;
- Maghrib is + 1 to + 6 by season;
- sunrise is − 2 to + 2.

These are per-month offsets fitted on each calendar (`monthlyOffsets`). IRN rounds up, so Fajr and Isha
at their own calendar's minute take no margin. Asr takes −30 s at Oslo and −18 s at Trondheim.

**Fit and held out.** Nothing is held out. The curves come from the same 2026 calendars, and no other
year is held. The gate is a check that the curves and the offsets reproduce their own calendars, not a
held-out proof.

**Gate.** Oslo and Trondheim: 730 place-days (365 each). There were 0 early and 0 late ends. The worst
per event: sunrise, Asr (both schools) and Maghrib 2; Fajr 6; Dhuhr 5; Isha 4. Tromsø (below) adds 365,
for 1,095 place-days over the three calendars (28 September 2026).

**Recorded exceptions (`no.irn`).**

- **Fajr, 6 min, and Isha, 4 min.** IRN's Fajr and Isha are each calendar's own curve (ruling R48),
  widened over the day before and after (ruling R28). When they move fastest, in spring and autumn,
  they run up to 6 min (Fajr) and 4 min (Isha) after the calendar's.
- **Dhuhr, 5 min.** IRN's Zuhr is + 4 in winter and + 9 from spring to autumn, changing within March and
  September. Those two months take the later, up to 5 min after the calendar's before the change.

### Tromsø: IRN's Makkah-time rule (ruling R82, 28 September 2026)

Until this change Tromsø's unit kept 16°/15° with the hour before sunrise from April to September and
claimed nothing (`measured = false`), since its calendar's polar periods looked artificial. Against that
calendar the old unit was early at Fajr on 150 days (up to 238 min) and, in the polar night, at Maghrib
by up to 321 min, Asr by up to 160 and Isha by up to 115; 78 of its days were repaired into order.

**The rule.** IRN says that north of 66.33° N a special solution is needed, and lists a method "based on
Makkah time" (bonnetid.info). Its 2026 Tromsø calendar follows a rule of about ten constants, read from
that calendar (derived parameters only; the calendar is not committed), now `IrnArctic`, a clock rule
(`ClockRule`) that the day computer applies last:

- **ordinary:** Fajr the later of 16° and noon − 9 h; Isha the earlier of 15° and noon + 9 h; Maghrib
  IRN's sunset + 5 (its calculator's sun: 0.70° down while the days lengthen, 1.00° while they shorten);
- **Makkah:** the day at 21.4225° N on Tromsø's meridian: Fajr and Isha at 18°, Maghrib its sunset + 5;
- **windows** (month-day), each blend a straight line on the wall clock (31 October, an anchor, is the
  day the clocks go back in 2027): every event Makkah 30 Nov–10 Jan, blended from 31 Oct and to 9 Feb;
  Fajr and Isha Makkah 29 Apr–14 Aug, blended from 22 Apr and to 21 Aug; Maghrib Makkah 22 Apr–22 Aug,
  blended from 15 Apr and to 29 Aug;
- **Asr** the later of the shadow and midway between noon and IRN's Maghrib − 5, the Hanafi never after
  that Maghrib − 65;
- **Dhuhr** noon + 5, + 10 from 21 March to 20 September;
- **sunrise** IRN's calculator's (1.05° down while the days lengthen, 0.85° while they shorten), noon in
  the polar night, and under the midnight sun the sun's lowest point.

**What Taqwa shows (option C).** IRN's rule wherever the sun allows: Fajr, Dhuhr and both Asr are IRN's;
Maghrib the later of the sun's and IRN's; Isha IRN's where it is after the Maghrib shown; sunrise the
earlier of the sun's and IRN's; the end of eating IRN's Fajr as an end where IRN's Fajr is shown (IRN
prints no imsak). Where the sun neither rises nor sets, IRN's whole Makkah day, sunrise included
(Makkah's sunrise, after IRN's own Fajr). Two things no day in order can show are **declared not
followed** (`PrayerDay.notFollowed`; the gate counts them as declared, neither early nor late, with their
dates in the stamp; About says so in one sentence):

- IRN's Fajr on the mornings it falls at or after the sunrise shown: 52 in 2026 (23 Apr–18 May and
  26 Jul–20 Aug, with the widened dates). Fajr stays before sunrise.
- IRN's sunrise on the 69 days it prints the sun's lowest point (18 May–25 Jul).

Only 2026 is held, so until IRN's 2027 calendar is, every window date and the Dhuhr change are taken a
day either way: each start the latest, each end the earliest. **Margins** (seconds), fitted on the 2026
calendar under the rule's own 2026 dates by the plan's formula (a start's least never early + 1 + 5 s,
an end's least never late − 1 − 5 s): Fajr 35 (the fit asks 17), Dhuhr 29, Asr 75 (the Hanafi
column's), Maghrib 42, Isha 83, end of eating 17; sunrise 0, since its margin also moves the sun's own
sunrise, which the sunrise shown never passes. IRN's noon runs 22 s after the engine's transit at the
unit's point; every start margin carries it.

**Gate (Tromsø, 365 place-days).** 0 early, 0 late ends, 0 over the limits, 0 out of order; Fajr 52 and
sunrise 69 cells declared. Worst per event, each a recorded limit of the unit with its reason:

| event | worst | where |
| --- | --- | --- |
| Fajr | 11 | the Fajr blends' first and last days (22 April, 21 August), with the widened dates; 5 in the winter blends; otherwise 2 (limit 56: the November after a 31 October clock change, below) |
| sunrise | 42 early | the polar-night edges (January; 17 in November), where IRN keeps noon or its own later sunrise; otherwise 4 |
| Dhuhr | 6 | 20 March and 21 September, the widened change; otherwise 1 |
| Asr (Standard / Hanafi) | 10 / 7 | the Maghrib blends' edge days (16 April, 28 August); 7 in the winter blends (limit 36, as Fajr's) |
| Maghrib | 313 | mid-April to late August, never before the real sunset (IRN's is Makkah's sunset + 5); 12 in the winter blends; otherwise 2 |
| Isha | 237 | late April to mid-August, after the Maghrib shown; 12 on the Isha blends' edge days (23 April, 19–20 August), 5 in the winter blends |
| end of eating | 235 early | the 52 declared mornings, the unit's own dawn before sunrise; otherwise 5 |

Without the widening (the rule's own 2026 dates) the edge days' lateness disappears and the winter
blends' falls to a few minutes (the research prototype: outside the summer Fajr 2, Dhuhr 1, Asr 3,
Maghrib 4, Isha 2); the widening can go once the 2027 calendar shows whether IRN's dates are fixed.

**The clock change on 31 October** (review I2). Taken a day early, the autumn blend's first anchor is
30 October, which in 2027, 2032 and 2038 is still summer time: its line on the wall clock starts an hour
later, and the unit runs later than the rule on its own dates from 31 October, below 10 min again by
about 23 November (through the unit up to Fajr 55, Asr 33, Maghrib 67 and Isha 63 min; 4–9 in other
years; never before it). It is kept. No 2026 blend crosses a clock change, so the calendar cannot say
how IRN's own line would, and if IRN's dates do move a day earlier with its line on the wall clock (as
the rule draws it), reading that anchor on winter time would be as early as this is late. The unit's
limits carry it instead: Fajr 56 and Asr 36 (the lead plus the unit's own 1 and 3 in the 2026 autumn
blend), and the Maghrib and Isha reasons add 71 and 65 (within 313 and 237). The gate cannot check
those years; `IrnArcticTest` bounds the widened unit against the plain rule across 31 October 2027.

`IrnArcticTest` walks the unit's point from 2026 to 2035: no day repaired, every Isha ends before the
next end of eating, Fajr never at or after a real sunrise, Maghrib never before a real sunset. It also
walks four points just inside the reach (north, south, east, west) and one 27 km north (review M2): no
day repaired, and a day without an Isha end only on one night of 14–20 May at some of them (16 May 2026
at 27 km north, 18 May 2029 at the south edge; a scratch walk adds 16 May 2033 at the north-east and
north-west edges). They are R72's kind of night: the week before the midnight sun, the real sunset past
midnight, Isha after the Maghrib that follows it and not before the next dawn; nothing early, no end
late, and the planner skips that Isha alert (R83).

**Automatic.** Within the Tromsø unit's reach (about 29 km) Automatic follows `no.irn` alone:
`no.cautious` combines IRN with Diyanet's European takdir, and the two cannot be put in order under
IRN's Makkah time (154 repaired days in the research prototype). Beyond the reach the edge keeps the
calendar's plain method and `no.cautious` stays Automatic: IRN's other Arctic calendars (Bodø, Narvik,
Alta …) are not held. `no.cautious` resolved by id within the reach still places its IRN member there,
rule and all, and repairs 153 days of 2026 at the unit's point (review M3); no one reaches it (it is
never a timetable row or a mosque-board candidate), so a golden vector or benchmark that resolves entries
by id leaves it out there.

**Within the reach under ruling R89** (28 September 2026). On the declared mornings (IRN's Fajr after the
sun has risen, the unit's own dawn shown) the fast ends at the Fajr shown: at the calendar's point its own
dawn, at a point south-west of the city the later of the two points' dawns, 3–4 min after it (the golden
vector's point at 69.597, 18.685: three of its 2027–2028 dates moved). The day at the calendar's own point
now bounds the sunrise, end of eating and imsak shown at every point within the reach (`DayComputer`,
point 7), so that end is the calendar's own there; `PointTableTest` walks three points within the reach
over 2026–2030.

**A paused timetable** (review I1). A chosen timetable not yet confirmed at Tromsø (migrated, or
confirmed elsewhere in Norway, R70) is held to Automatic, now IRN's Makkah time. Where the two days leave
a prayer no time (under the midnight sun a sun-based method's nearest-latitude sunrise before IRN's
Makkah-time Fajr; in the polar night an end of one before the other's start), the later start is kept
with its own day's end, and for Fajr its own day's sunrise: no start before either day's, no day
repaired by the pause, and a sunrise after the chosen method's on 70–75 days of 2026 (up to 322 min).
Before, the repair put Fajr up to 237 min before Automatic's on 69 days.

**Polar days.** A day IRN's rule gives whole is not polar in the engine's sense: nothing on it follows
the nearest latitude, so the Prayer screen shows IRN's estimates with the Set by rule pill instead of the
polar line, on about 120 days a year; the midnight sun's edge days the rule gives only in part (18 May,
25 July in 2026) keep the polar line.

This supersedes R49 item 3's "Tromsø is honestly unmeasured".

**Beyond the calendars.** The edge (Bergen, Stavanger and elsewhere in Norway) keeps 16°/15°, with Fajr
no earlier than an hour before sunrise from April to September, plus the edge's own safe margin. No IRN
calendar for another city is held, so the edge is not measured.

- Its Isha is later than IRN's own summer ramp to Maghrib + 40.
- Its Fajr floor is not proven never-early. At Oslo, IRN's printed Fajr is never after sunrise − 60
  from April to September. At Trondheim it is up to 2 min after it (July), where the frozen times
  outrun the hour.

RegistryTest checks the edge's hour before sunrise at Bergen.

## The Nordics after ruling R64 (`no.cautious`, gate `no-cautious.tsv`; `se.cautious`, gate `se-cautious.tsv`)

**The change (R64).** Before R64, one cautious entry covered Sweden, Norway, Denmark, Finland and
Iceland. Its members were Diyanet's European method, IRN's calendar and Rabita Helsinki's family, and
IRN's calendar applied across all five countries. R64 splits the country map (in `Registry.kt`, by
exception for this item):

- **`no.cautious`** covers Norway and Svalbard. Its members are IRN's joint calendar (Oslo's largest
  mosques, the most followed) and Diyanet's European method (the Turkish and Bosnian mosques).
- **`se.cautious`** covers Sweden, Denmark, Finland, Iceland, the Faroes, Åland and Greenland. Its
  members are Diyanet's European method (Islamiska Förbundet in Stockholm, HBKCC in Copenhagen; the most
  followed) and Rabita Helsinki's family (`se.rabita`, a convention: 18°/17°, Zuhr + 2, Maghrib + 3;
  while 17° does not occur, Isha at Maghrib + 88). Rabita replaced the MWL member, whose Fiqh Council
  estimate put Isha a minute before Rabita's on 49 May days.

RegistryTest checks the resolution: Oslo and Longyearbyen go to `no.cautious`; Stockholm, Copenhagen
and Helsinki go to `se.cautious`, without IRN.

**The tables held.** Every row is held out, since a cautious entry has no margins of its own.

- `no.cautious`, 1,095 place-days:
  - Oslo: IRN's 2026 calendar, and Diyanet's Oslo table split by year. In 2027 Maghrib is not checked:
    the cap follows IRN, whose 2027 calendar is not held.
  - Trondheim: IRN's 2026 calendar.
- `se.cautious`, 1,300 place-days:
  - Stockholm: IFIS's pages and Diyanet's own Stockholm table;
  - Copenhagen: Diyanet's table;
  - Helsinki: Rabita's 2026 calendar. Rabita prints no imsak, so its Fajr is also the end of eating and
    the row is gated `F+E`. Maghrib is not checked there: the cap follows Diyanet, whose table is not held
    at Helsinki.

**Rabita's Fajr and end of eating (review C1).** Gated as `F+E`, the old Rabita member (18° with a
seventh of the night) ended the fast after Rabita's printed Fajr on 124 days (21 April to 22 August), by
up to 100 min. From April to August Rabita prints Fajr near the middle of the night, about 0.4 of it
before sunrise.

- Its own calendar's Fajr is now its curve (`RabitaCurves`, R42 envelope with provenance, as IRN's under
  R48).
- Its end of eating reads its own earliest dawns (`EndOfEatingDawns.rabitaHelsinki`, R39).
- Where the printed Fajr lies within a quarter degree of the sun's lowest point, the end curve stops a
  quarter degree short so that it is always reached. That fell up to 4 min after the printed Fajr, so
  Rabita's end takes a −5 min margin.
- The row now shows 0 late ends.

**Gate.** Both entries show 0 early and 0 late ends. The worst lateness, in minutes, with the old single
entry's figure for comparison:

| event | `no.cautious` | `se.cautious` | before R64 (one entry) |
| --- | --- | --- | --- |
| Fajr | 51 (Trondheim 51, Oslo 48) | 118 (Helsinki 118, Copenhagen 34, Stockholm 5) | 160 (Helsinki 160, Stockholm 96, Copenhagen 86, Oslo 74) |
| Isha | 148 (Trondheim 148, Oslo 29) | 155 (Helsinki 155, Stockholm 136, Copenhagen 135) | 155 |
| end of eating | 96 (Trondheim, before IRN's Fajr; Oslo 55) | 137 (Copenhagen 137, Stockholm 136, Helsinki 25) | 134 |
| sunrise | 11 | 7 | 9 |
| Dhuhr | 5 | 6 | 9 |
| Asr | 8 | 8 | 8 |
| Maghrib | 3 | 3 | 3 |

**What the split changed.** With IRN gone from Sweden, Denmark and Finland, their Fajr fell sharply:
Stockholm 96 → 41 and Copenhagen 86 → 37 min. Their Isha and end of eating did not fall. Those were
never IRN's: they are Diyanet's European method (next section). Norway's Fajr fell from 74 to 57 min at
Oslo. Its Isha is still up to 149 min late, for the same reason.

**Recorded exceptions (`no.cautious`).** Each is scoped to its events, with the reason in `Europe.kt`.

- **Fajr 51 and Isha 148** (with 7b's city curves; 57 and 149 before). Diyanet's European method
  applies across Norway with its summer takdir,
  including at Trondheim, far from any Diyanet table. It is later in summer than IRN's hour before
  sunrise and its Isha ramp.
- **End of eating 96.** The end is the earlier member's. At Trondheim it is Diyanet's European imsak
  beyond its city tables, up to 96 min before IRN's printed Fajr in spring (review I1: IRN's rows are now
  gated `F+E`). At Oslo it is IRN's own earliest dawns (R39), up to 55 min before Diyanet's imsak.
- **Sunrise 11.** Sunrise is the earlier member's (Diyanet's is the sun's less 7).
- **Asr 8.** The later member's, in the later school.
- **Dhuhr 5.** IRN's + 4 to + 9, and Diyanet's + 5.
- **Maghrib 3.** Capped at IRN's where the members spread.

**Recorded exceptions (`se.cautious`).**

Each exception names the member that drives it (review I4; each member was measured alone against each
table):

- **Fajr 118** (with 7b's Stockholm curve, Stockholm falls to 5). Diyanet's European method, with its
  summer takdir:
  - beyond its city tables at Helsinki (119 after Rabita's calendar) and Copenhagen (35);
  - at Stockholm (39), before 7b's city table.
  Rabita's own Fajr follows its calendar.
- **Isha 155.**
  - At Helsinki it is Rabita's own model: 17° while the sun reaches it, which falls near midnight in late
    spring and late summer, up to 155 after its calendar.
  - At Stockholm (141) and Copenhagen (136) it is Diyanet's European takdir.
- **End of eating 137.** Rabita's own earliest dawns, near the middle of the night from April to August
  (R39):
  - applied at Copenhagen and Stockholm, up to 137 and 136 min before Diyanet's imsak there;
  - at Helsinki, up to 25 before Rabita's own Fajr.
- **Asr 8, sunrise 7 and Dhuhr 6.** Diyanet's own minutes, against Rabita's at Helsinki.
- **Maghrib 3.** Capped at Diyanet's where the members spread.

**A registry note.** A cautious Resolution takes `measured` from the entry, not its members' units. At
Tromsø the IRN member is unmeasured, but `no.cautious` still reads as measured. Reported as a concern.

### Who makes the Nordics late: Diyanet Europe beyond its city tables

After R64, the Nordics' summer lateness comes almost entirely from members applied far from their own
tables. The largest share is Diyanet's European method beyond its city point tables.

Each member was measured alone against each table (Fajr / Isha, worst lateness in minutes). The table
shows this branch, and a trial merge of 7b's head `32246fc` (run, then aborted):

| where | Diyanet Europe alone, this branch | Diyanet Europe alone, with 7b | the cautious entry, with 7b |
| --- | --- | --- | --- |
| Stockholm (a 7b city table) | 27 / 80 | 4 / 3 | 41 / 136 |
| Oslo (a 7b city table) | 38 / 143 | — | 48 / 29 (IRN's rows), 25 / 3 (Diyanet's) |
| Copenhagen (no Diyanet city table) | 35 / 136 | 34 / 135 | 37 / 135 |
| Trondheim (no Diyanet city table) | — | 51 / 148 | 51 / 148 |
| Helsinki (no Diyanet city table) | 119 / 153 | 118 / 153 | 143 / 155 |

**In plain terms.**

- **Copenhagen, Helsinki and Trondheim.** Diyanet Europe is the member that makes the entry very late.
  It has no city table there, and its summer takdir runs Isha 135–153 min and Fajr up to 118 min after
  the tables held. 7b's refit does not change this: it adds city tables for Stockholm and Oslo, not for
  these cities.
- **Stockholm and Oslo.** With 7b's city tables, Diyanet Europe is within 5 min of its own Stockholm
  table. Oslo's cautious Isha falls from 141–143 to 29 (IRN's rows) and 3 (Diyanet's). What is left
  at Stockholm (Fajr 41, Isha 136, end of eating 133) comes from Rabita Helsinki's family applied at
  Stockholm, again a member far from its own table. Its rule takes the real 17° and 18° while the sun
  reaches them, and at Stockholm they fall near midnight in late spring and late summer.
- **Sweden's summer lateness is not substantially lower after the split.** Its Fajr fell (Stockholm 96
  → 41, Copenhagen 86 → 37), but its Isha did not (Stockholm 141, Copenhagen 136, Helsinki 155).

**No model is invented.** As the controller directed, no new model is made for the Nordic summer. The
owner decides the Nordic summer policy. The limits above are the data's, each scoped per event.

With 7b, every Nordic figure stays within its limit. Norway's Fajr and Isha fall to 51 and 148;
Sweden's stay at 143 and 155.

**Oslo and Stockholm, Diyanet (the weekly monitor's capture of 28 September 2026).** Diyanet's Oslo
table as `no.cautious`'s member row at Oslo (Maghrib not checked on it: IRN's 2027 calendar is not
held, and in 2026 IRN's own row checks Maghrib under the cap) and its Stockholm table as
`se.cautious`'s at Stockholm, 29 September – 29 October 2026 and 2027, the same minutes as the
research's capture on every day both hold. They add 26–29 October 2026 to the member's days at both
points: 0 early, 0 late ends, no figure moved (`se.cautious` 6,028 place-days, was 6,024; `no.cautious`
2,648, whose Oslo days were IRN's place-days already). Oslo's page stays held from 30 October (ruling
R115): no Diyanet capture holds 30–31 October yet.

## The United States (`us.isna`, gate `us-isna.tsv`)

**Class D_AUTHORITY.** FCNA/ISNA 15°/15° has no binding authority. The brief sets "the mosque tables as
floors": the app is never before any mosque that follows 15°/15°.

**The floors.** 35 tables, each its own mosque's, at the research index's point:

- New York, Virginia, Maryland, Michigan, Georgia, Texas, California and the Chicago area;
- Asr in the school each table prints;
- every Fajr is also the start of the fast, since no US mosque prints an imsak.

**Not floors, with reasons:**

- ICCNY: its June–August rows follow about 17° with Maghrib + 10, a different table;
- the two Diyanet mosques (Diyanet);
- the Islamic Center of Washington (19.5°);
- Masjid Yaseen (18°/18°);
- Al-Farooq Atlanta (MWL);
- the Chicago 18° block (`us.chicago`, below);
- one-day captures with no stated method.

**Faults left out** (namerica/split-7g/, each named in its header):

- Frisco: 22 days, including runs in April with sunrise up to 10 min late and Maghrib before sunset,
  10 March, 23 July, and 1 November's hour;
- ICOI Irvine: 2 days;
- Mishkah Houston: 7 weekly congregation Zuhrs in November and December;
- MCA Santa Clara: 1 Isha.

**Fit and held out.** The split is by mosque. Every region is in both halves: 14 tables are fit
(4624 place-days) and 21 are held out (3993).

- The held-out tables showed no early start except King Fahad Culver City's Maghrib: 1 min on 19 days,
  its Maghrib being sunset + 5. Maghrib was widened by that excess plus 5 s (226 → 266 s, the whole
  fit).
- Margins in seconds: Fajr +71, sunrise −98, Dhuhr +314, Asr +154, Maghrib +266, Isha +150, end of
  eating −99.
- These replace `conventionWithMwlMinutes` (+150 s everywhere), which 7f owns and which is unchanged.

**Gate.** 8617 place-days, 0 early and 0 late ends. The worst per event: Fajr 4, sunrise 4, Dhuhr 7,
Asr 5, Maghrib 6, Isha 5, end of eating 4.

**Recorded exceptions (`us.isna`).** The US mosques that follow 15°/15° add their own minutes:

- Zuhr up to + 5 at Irving and MCC Silver Spring;
- Maghrib up to + 5 at King Fahad and Dar Al Noor;
- ADAMS moves each time 2 min in the safe direction.

The app is never before any of them, so it runs late against the rest:

- **Dhuhr 7 and Maghrib 6** after the tables that add nothing;
- **Asr and Isha 5**: a minute or two after the angle at some tables;
- **Fajr, sunrise and the end of eating 4**: ADAMS's earlier Fajr and sunrise.

This is the price of the floors: against the plain 15°/15° majority, Dhuhr and Maghrib run 5–7 min
late on most days. It is an owner question (report): take every ISNA mosque's own minutes as a floor,
or only the convention's.

**Scope.** `us.isna` is now also a member of `us.chicago`, so it is an Other method and global (a
named member is selectable everywhere). Its countries are unchanged.

## Chicago (`us.chicago`, gate `us-chicago.tsv`)

**Class C, changed from D.** The spec's "Chicago block of 18°/15°, Hanafi" does not hold for the metro.
Seven Chicago tables are held, all Hanafi:

- three follow 18°/15°: Islamic Foundation Villa Park, Makki Masjid, Masjid DarusSalam Lombard;
- four follow 15°/15°: MCC Chicago, MEC Morton Grove, Mosque Foundation Bridgeview, the Prayer Center of
  Orland Park.

An 18° Fajr would start 15–30 min before the 15° mosques' Fajr. Chicago is therefore cautious over:

- `us.isna` (the most followed of the tables held);
- the 18° block (`us.chicago.eighteen`, a convention; string key `timetable_chicago_eighteen` for Task
  13).

The school is Hanafi, and known. Fajr is the 15° mosques', and the end of eating the 18° block's dawn.
RegistryTest now checks the two members.

**Gate.** Each table is checked against its own member, all held out: 1101 place-days, 0 early and 0
late ends.

**Recorded exceptions (`us.chicago`).**

- **Fajr 31**: the 15° mosques' Fajr, never before it, after the 18° block's.
- **End of eating 32**: the 18° block's dawn, never after it, before the 15° mosques' Fajr.
- **Dhuhr and Maghrib 6, Asr and Isha 5, sunrise 3**: both members keep the US floors.

## Montreal and Ottawa (`ca.isna`, gate `ca-isna.tsv`)

**Class D_AUTHORITY.** ISNA 15°/15° is followed by 12 of Montreal's 16 mosques and all 6 of Ottawa's.
As in the US, the 15°/15° tables are floors: 18 Mawaqit tables.

Montreal's four 18° tables (Assuna, ICQ, Makkah Al-Mukarramah, ISQ) are another convention, not floors.

**Faults left out:**

- MAC Al-Rawdah: 13 January (sunrise 20 min early), 24 January (no sunrise), 1 November's hour;
- MAC Abrar: 1 November;
- Jami Omar: 1 November.

**Fit and held out.** Nine tables are fit and nine held out, both cities in each half. The held-out
tables showed 0 early, so the fit's margins stand. In seconds: Fajr +110, sunrise −160, Dhuhr +10, Asr
+71, Maghrib +131, Isha +83, end of eating −103.

**Gate.** 6414 place-days, 0 early and 0 late ends. The worst per event: Fajr, sunrise, Maghrib and the
end of eating 5; Asr and Isha 4; Dhuhr 2.

**Recorded exceptions (`ca.isna`).** Montreal's and Ottawa's 15°/15° tables differ among themselves:

- MAC's two (Al-Rawdah, Abrar) wander around the rule: Fajr from 14.6° to 15.3°, Maghrib from 2 min
  before sunset to 3 min after, and sunrise up to 3.6 min before the sun's;
- Asr runs from a minute before the exact one (Dar Al Arkam, CCML) to 2 min after it (Ottawa South).

The app is never before any of them, and sunrise and the end of eating are never after any:

- Fajr, sunrise, Maghrib and the end of eating up to 5 min;
- Asr and Isha up to 4.

## Canada elsewhere (`ca.cautious`, gate `ca-cautious.tsv`)

**Class C.** Outside Toronto, Montreal and Ottawa there is no majority. Each Prairie mosque applies its
own summer rule.

**The members, rebuilt.** The members were the ISNA and MWL presets plus "15° with Isha = Maghrib + 90".
The presets were dropped: where the sun only just reaches their angles (May and August on the
Prairies), their Fajr falls near midnight, and the cautious end of eating would come hours before any
table's.

The members are now three conventions, each with its own rule and its margins fitted on its own 2026
tables (PlaceCurves, Task 7g):

- **`ca.eighteen`, the Edmonton 18° family** (Al Omari, Al Ansar, Al Farooq; Surrey Jamea, BC, for one
  month). It is the most followed of the tables held.
  - Fajr is no earlier than a quarter of the night before sunrise. Where the sun misses 18°, it is the
    MWL proportion from 45° (the tables match this to ±0.005 of the night).
  - Isha is the earlier of 18° and a sixth of the night.
  - Its own minutes: Zuhr up to + 5, Maghrib up to + 6.
- **`ca.fifteen`, Edmonton's 15° family** (Markaz Al Imam Malik).
  - Fajr is 15°, no earlier than a quarter of the night.
  - Isha is 16.5°, no later than 28 % of the night.
- **`ca.isha90`, Calgary Islamic Centre SW.**
  - Fajr is 15°, no earlier than 23.5 % of the night.
  - Isha is Maghrib + 90.

New string keys for Task 13: `timetable_eighteen_degrees`, and `timetable_fifteen_degrees` (already
used by the UK's 15° family). No member is named, so `ca.cautious` lists no nearby timetable.

**Faults left out:**

- Al Omari, Al Ansar and Al Farooq: 1 November's hour;
- Al Ansar: 4 August (Asr 10 min late);
- Al Farooq: 8 March (Fajr an hour early) and 18 September (Asr);
- Imam Malik: November and December (every time an hour off from the clock change). Its Isha after
  midnight is written as 24:MM, as the gate reads it.

**Gate.** Each table is checked against its own member, all held out: 1788 place-days, 0 early and 0
late ends.

Maghrib is capped at the most-followed member's (the 18° family's, sunset + 5 to 6). At Calgary Islamic
Centre SW and Imam Malik it is therefore not checked: their Maghribs are within minutes of sunset, and
are shown up to 6 min after them.

**Recorded exceptions (`ca.cautious`).** Canada's mosques outside the three cities follow three
families, and the app is never before any of them:

- **Fajr 36**: the 15° families' Fajr after the 18° family's.
- **End of eating 38**: the 18° family's dawn before the 15° families' Fajr.
- **Isha 77**: Isha is Imam Malik's 16.5° (up to 28 % of the night) in spring and autumn, and the 18°
  family's in winter. It runs up to 77 min after Calgary Islamic Centre SW's Maghrib + 90 and the 18°
  family's spring Isha.
- **Dhuhr, Asr and Maghrib 5**: each family's own minutes.
- **Sunrise 4.**

**Not members, and where the app is early against them** (owner questions, report):

- **IISC Calgary** prints 10°/10° all year (Mawaqit). Its November and December are an hour off. The
  cautious Fajr is before its Fajr on every day, by 11 to 47 min over the year (review I8).
- **Al-Medina and Assiddiq Edmonton**: their own Isha rules; Assiddiq's data is partly unreadable.
- **Al-Rahmah Surrey**: 18°/15° Hanafi, one month.
- **Burnaby's 2015 table**, and **Eyup Sultan** (Diyanet).
- **Winnipeg, Halifax, Regina, Saskatoon, Quebec City and Ontario outside Toronto and Ottawa**: no table
  is held. The members' rules apply there unmeasured. **Ruling R87's spot check** (`survey/ca-cautious/`,
  `ContinentalMawaqitSurveyTest`; recorded only, brief L item 7): 6 Mawaqit calendars, Winnipeg (3),
  Windsor (2) and Halifax (1), 2,190 place-days, each on its own zone; Masjid Bilal's and Rahma's 1
  November left out (the clock change on the wrong side). 0 early starts and 0 late ends but for: Manitoba
  Dawah Center's summer, a flat 15° Fajr as long as the sun reaches it, up to 12 min before the 18°
  family's end (55 days) and its 15° Isha up to 31 min after the members' (48 days); Masjid Bilal's and
  Rahma's Fajr at about 14.5° from August to October, 1–2 min after the 15° families' (5 and 52 days);
  TCI Windsor's sunrise a minute before the sun's on 246 days. Nothing here is a member; Winnipeg's own
  high-summer Isha and end of eating run later than the fitted Alberta and BC members, worth a look when
  Canada's entry is next revisited.

## Ireland

### Islamic Foundation of Ireland (`ie.ifi`, gate `ie-ifi.tsv`)

**Class D_AUTHORITY.** IFI publishes one fixed table (GMT, 28 towns) that repeats every year. The gate
uses Dublin's column as local time, at the table's point (R44).

**The model.** The table moves in steps that no rule reproduces. The old four-harmonic curve ran Isha
up to 11 min, and Asr and Maghrib up to 8 and 7 min, after the table.

- `IfiCurve` is now the table's own per-slot curve, as for IRN (R42, R48): the sun's depression at
  each printed Fajr and Isha, on R28's slots, widened over the neighbouring days, with a provenance
  note. It is an envelope, not the times.
- Sunrise, Dhuhr, Asr and Maghrib take per-month offsets fitted on the table.
- Margins in seconds: Fajr and Isha 0, since the curves sit at the table's minute. The fitter allowed
  −54 on 2026 alone, not across other years' rounding, as for IRN. Asr +12. End of eating −54.

**Fit and held out.** Nothing is held out: the table repeats every year and the curve is its own.

Lucan's copy is IFI's except its summer Isha, which is its own and earlier, so it is not a separate
row. The second Dublin capture (`ifi_dublin_b`) is another town's column, unnamed, and is not used.

**Gate.** 365 place-days, 0 early and 0 late ends. The worst per event: Isha 3, Fajr, sunrise, Asr and
Maghrib 2, Dhuhr and the end of eating 1.

### Islamic Cultural Centre of Ireland (`ie.icci`, gate `ie-icci.tsv`)

**Class D_AUTHORITY.** The ICCI's site code is adhan-js MWL with the TwilightAngle rule, at 53.3498,
−6.2603. Its 2026 table is used, January to June fit and July to December held out.

- Under R39's 18° end, the held-out half showed 17 late ends (the end of eating) with the H1 fit's end
  margin of +21 s, so the margins were widened by that excess plus 5 s, to the whole year's fit. R80's end
  (below) needs no widening.
- Margins in seconds: Fajr +21, sunrise +23, Dhuhr and the other starts −22, Asr +59, Maghrib −23, Isha
  −17, end of eating +21 (R80, below).

**The end of eating (ruling R80).** The ICCI prints no imsak, so the fast begins at its Fajr. It is now
that rule's own dawn read as an end (`TwilightCurves.fajrAsEnd`), not R39's 18° dawn:

- Fajr no earlier than 18/60 of the night before sunrise, or 18° where that is later;
- the night adhan-js counts, from the day's sunset to the next sunrise. adhan2 (MWL with the TwilightAngle
  rule) reproduces every day of the ICCI's 2026 table to the minute;
- the sun's declination at the moment itself, not at noon. In an Irish summer night a tenth of a degree
  is a minute or two;
- on each month and day, the largest depression of the four years of a leap cycle. The start's curve
  instead widens each slot over the day either side, which would cost up to 4 min more in July and
  August.

The end margin, +21 s, is fitted on January to June (decided 11 February, an 18° day). It gives 0 late
ends on July to December. Against adhan2's run of the code at the ICCI's point it gives none in any year
from 2025 to 2048: that run allows up to +27 s in every one of those years (+27 to +30 by year; +28 is
late in 2025, 2026, 2031 and others), so +21 keeps 6 s in hand, the Fitter's 5 s safety and a second.

**Gate.** 365 place-days, 0 early and 0 late ends. The end of eating is within class D's 3 min, so its
exception is gone.

| end of eating, worst min before the printed Fajr | Jan | Feb | Mar | Apr | May | Jun | Jul | Aug | Sep | Oct | Nov | Dec |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R39 (the 18° dawn) | 1 | 1 | 1 | 2 | 86 | 2 | 80 | 57 | 1 | 2 | 1 | 1 |
| R80 (its own rule) | 1 | 1 | 1 | 1 | 2 | 2 | 3 | 3 | 1 | 1 | 1 | 1 |

Against adhan2's run of the code, the end is up to 4 min before its Fajr in other years: in May of the
leap years and in July and August of the years before them. The month-and-day curve takes the whole
cycle's earliest.

**Recorded exceptions (`ie.icci`).**

- **Fajr 7 and Isha 5.** The code keeps Fajr no earlier than 18/60 of the night and Isha no later than
  17/60. On the curve's slots each is widened over the neighbouring days (R28). When the rule takes over
  in late April and May, this runs Fajr up to 7 min and Isha up to 5 min after the table.

### Ireland (`ie.cautious`, gate `ie-cautious.tsv`)

**Class C.** The members are IFI (most followed) and the ICCI. Both tables are Dublin's and both are
held for all of 2026, so every start is checked against the later of the two that day. There were 0
early and 0 late ends over 365 place-days. Maghrib is capped at IFI's on 201 days.

**Recorded exceptions (`ie.cautious`).**

- **Fajr, sunrise, Asr and Maghrib, 2 min.** Each member keeps its own margins.
- **Isha, 5 min.** The ICCI's rule on the curve's slots in May.
- **End of eating, 3 min.** The end is the earlier member's. That is the ICCI's own rule read as an end
  (R80), except for most of June, when IFI's own dawn is the earlier: IFI's set 19 June days and 1 July
  day, and the two tied on 4 more. The ICCI's, taken by month and day over the leap cycle, runs up to
  3 min before the earlier of the two tables' Fajr in July and August. Under R39 it was 86 min (the ICCI's
  18° dawn, 14 May).

## Australia

### Lakemba Mosque (`au.lma`, gate `au-lma.tsv`)

**Class D_AUTHORITY.** The data is LMA's 1448 AH table (June 2026 to June 2027, 349 days), at Lakemba
(R30). Its rules:

- Fajr 18°, rounded down;
- sunrise rounded down;
- Maghrib at sunset, rounded up;
- Isha = Maghrib + 90;
- Dhuhr noon + 2..7;
- Asr from 12 min before to 22 min after the Shafi'i Asr, on a smooth seasonal curve that no rule
  here reproduces (sign errors, a stale declination and the Hanafi shadow were all tried).

**The model.** Dhuhr and Asr take per-month offsets (after LMA's latest minutes, + 7 and + 22) fitted on
the whole table. In seconds: starts 0, Fajr −25, sunrise −19, Asr +7, Maghrib +5, Isha +14, end of eating
−17.

**Fit and held out.** Nothing is held out. The monthly offsets need every month, and the October 2026
captures (`off-lakemba-oct`, `lakemba-nodst`) repeat this table's rows. Next year's table may move
its Asr differently; this is not proven.

**Gate.** 349 place-days, 0 early and 0 late ends. The worst per event is 1 min, except Dhuhr 3 and
Asr 15.

**Recorded exception (`au.lma`), Asr 15 min.** LMA's Asr seasonal curve moves up to 0.4 min a day.
Per-month offsets keep it never early, but up to 15 min after it within its fastest-moving months
(October, December and January). Before this fit, LMA's latest minutes (+ 22) ran up to 33 min
late.

### Australia (`au.cautious`, gate `au-cautious.tsv`)

**Class C.** The members are LMA (most followed) and Diyanet's per-suburb tables, which are the TURKEY
preset (`other.turkey`) to within a minute. Diyanet's Sydney and Melbourne tables are held for 25
September to 25 October 2026.

- Diyanet's district points are not published, so Sydney's table is checked at Lakemba, west of the
  city centre (a later sun).
- **Brisbane's capture (`diy-bro`) is a fault:** it repeats Melbourne's times (its Zuhr falls at
  Melbourne's longitude). It is not used.
- Maghrib is capped at LMA's where the two spread, so it is 5–6 min before Diyanet's printed Maghrib
  (its sunset + 7). This is the spec's cautious Maghrib, not an early start under the gate's rule.
  Maghrib is not checked at Melbourne, where no LMA table is held.

**Gate.** 380 place-days, 0 early and 0 late ends.

**Recorded exceptions (`au.cautious`).**

- **Asr 18**: the later member's (LMA's monthly offsets and the preset's). It was 17 before 7b's
  allowance on `other.turkey`, and the limit covers both.
- **Isha 10**: the preset's 17° against LMA's Maghrib + 90.
- **Sunrise 8**: the preset's sunrise less 7.
- **Dhuhr 4.**
- **Fajr and the end of eating 2.**

**7b.** `other.turkey` carries a 70 s Asr allowance on 7b's branch.

A trial merge of 7b's head (494cbdf) into this branch was run and then aborted. It showed:

- Asr here up to 18 min;
- the single Nordic entry's end of eating (before R64's split) up to 144 min, against 134 before;
- every other group of this subtask unchanged within its limits.

The two limits cover both states. The gate must be run again once 7b merges (report).

## New Zealand (`nz.fianz`, gate `nz-fianz.tsv`)

**Class D_AUTHORITY.** FIANZ publishes 20 cities; only Auckland's table is held (March, June, September
and December 2026). Its rules: 18°/18°, Shafi'i Asr, nearest minute.

**The model.** Auckland is a point table with its reach by class D (R40), replacing the old flat 30 km.

**Fit and held out.** March and June are fit; September and December are held out.

- The held-out Fajr was 1 min early on 16 September days. Fajr was widened by that excess plus 5 s to
  the whole table's fit (−27 → −2 s).
- Other margins in seconds: starts −13, sunrise +18, Asr −16, Maghrib −1, Isha −4, end of eating +18.

**Gate.** 122 place-days, 0 early and 0 late ends. Every event is within 1 min, held-out months
included.

**Not proven.** The other 19 cities are not held. Beyond Auckland's reach, the edge's safe margins
apply.

## Tables with no imsak, gated as F+E (review I1)

Some tables print no imsak: IRN's calendars, the GMP's Paris table, the French families' tables, and
Toronto's three. On all of them the printed Fajr is where the fast begins. They were gated with Fajr
alone; every such row is now gated `F+E`.

None shows a late end (an end of eating after the printed Fajr). The early ends are recorded as scoped
exceptions on the end of eating, each for its R39 reason:

| entry | end of eating, worst min before the table's Fajr | why |
| --- | --- | --- |
| `no.irn` | 10 | IRN's own earliest dawns (R39), widened over the neighbouring days: in April, as the summer rule begins |
| `no.cautious` | 96 | at Trondheim, Diyanet's European imsak beyond its city tables (its takdir dawns, read from Oslo's), in spring; at Oslo 55 before Diyanet's imsak and 28 before IRN's Fajr |
| `fr.gmp` | 16 | its own dawns (R80), floored at its rule across its table's reach: within 4 min but on 1–2 January and 29–30 September, where its page changes method; the page's earlier-method rows are now gated `E` |
| `fr.cautious` | 29 | from late April to August the flat 15° family's 15° dawn, against Al-Amel's later summer Fajr; the GMP's own dawn the rest of the year (R80) |
| `ca.toronto` | 4 | each member's own dawn less its margin |
| `ca.ift`, `ca.iit`, `ca.mac` | 3, 3, 1 | within class D's 3 min |

### The end of eating, audited (3 Oct 2026, before Ramadan 1448)

The audit of every gate entry found three more groups whose Fajr is where the fast begins but whose
rows read Fajr alone. Each is now `F+E`; none shows a late end, none is over its limit, nothing else
moved:

| entry | rows | days | worst, min before the table's Fajr | limit |
| --- | --- | --- | --- | --- |
| `za.mjc` | the MJC's September and October (its Fajr is also the end of sehri, Radio 786) | 61 | 1 | 3 (class D) |
| `za.voc` | the community calendar 2026–2027, masjids.co.za's relay, Wembley's page | 348 | 2 | 3 (class D) |
| `za.cape` | its MJC and community-calendar members' rows, beside Jamiat's sehri (`E`) | 61 | 1 | 1 (unchanged: Jamiat's sehri stays the earliest member) |
| `nl.cautious` | the Moroccan calendar's member row, beside Diyanet's | 31 | 1 | 2 (unchanged) |

`za.jamiat`'s other publishers (Darul Ihsan's Pretoria and Durban, the masjids.co.za relays) stay
`F`: Jamiat's fast ends at its printed sehri, 5 min before its Fajr, which every Jamiat row checks
(`E`, 14,608 days); against those tables' Fajr, checked once outside the gate, the end came before
it on every day, more than 2 min before on 974 (5–6 min in the report's samples), never after.

## Where a cautious entry is measured (review I3)

A cautious entry now claims a measured figure only where the entry is measured and every member's own
placement at that place is measured. The change is in the registry core, allowed for this item. A
member's placement is measured when:

- **it is an entry with units:** that unit's flag, and false beyond every unit;
- **it is an entry without units:** that entry's own flag;
- **it is a convention:** only within class C's reach of the tables that prove it (`measuredAt`):
  - the Chicago 18° block at its three mosques;
  - the Canadian families at Edmonton, Surrey and Calgary;
  - the French families and the GMP at Paris;
  - the Moroccan calendar at Amsterdam;
  - Rabita at Helsinki;
  - Jamiat's Shafi'i member at Cape Town;
  - the UK's 15°, Karachi and late-dawn conventions nowhere, since they come from a survey, not a gate.

RegistryTest checks this:

- **not measured:** Bodø (`no.cautious`: beyond every IRN calendar's reach; Tromsø, once the example,
  follows `no.irn` since ruling R82), Winnipeg (`ca.cautious`:
  far from every family's tables) and Perth (`au.cautious`: beyond Lakemba's reach);
- **measured:** Oslo and Toronto.

Some entries are therefore measured nowhere: `ca.cautious` and `se.cautious` (no place holds every
member's own table), and `gb.cautious`. Their "at most" figures are the gate's, but the app does not
claim them for any place. This is the honest reading of their tables.

## After 7b's merge

prayer-engine at 7980819 carries 7b: Diyanet's twelve European city curves and the SunClock fix. All 25
groups were re-run and the stamps regenerated.

- **`ie.icci`, `ie.cautious`:** the end of eating is now up to 86 min before the table (14 May), because
  the engine finds a barely reached 18° dawn exactly. The limits are 86, with that reason.
- **`fr.gmp`, `fr.cautious`:** the end of eating is up to 114 and 112 min before the table (12 June),
  for the same reason. The limits follow.
- Since superseded by R80 (28 Sep): each authority's own dawn as an end. The end-of-eating limits are
  now 3 (`ie.icci`: class D's, no exception), 3 (`ie.cautious`), 16 (`fr.gmp`) and 29 (`fr.cautious`);
  see each section above.
- **`at.iggo`, `ch.fids`:** now on Diyanet's own Wien and Zürich curves (the quick win the coordinator
  named). IGGÖ's Fajr went from 22 to 3, Isha from 63 to 3, and the end of eating from 6 to 3. Its
  exceptions are gone. (Since the monitor round Wien's Fajr, Isha and end keep Diyanet's curve
  margins, with three unit exceptions of 4 min; see the section above.)
- **`de.vikz`:** not changed. It has no units of its own, so following Diyanet's Berlin, München and
  Freiburg curves would need a unit set for it, which means a new list in the registry core's
  `Units.kt`. Germany's cautious Fajr stays up to 12 min after Diyanet's Berlin table.
- **Tightened to the data:**
  - `be.cautious`: the end-of-eating exception is removed (within class C);
  - `de.cautious`: Fajr 12;
  - `no.cautious`: Fajr 51, Isha 148, sunrise 10;
  - `se.cautious`: Fajr 118, sunrise 6, Dhuhr 5, Asr 7, Maghrib 2.
- **`se.cautious` Fajr against Islamiska Förbundet's Stockholm pages: fixed.**
  - 7b's Stockholm city curve follows Diyanet's own Stockholm table, fitted 25 s before each printed
    minute.
  - IFIS's 2025–26 pages print Fajr a minute later than that on some days. Diyanet Europe alone was
    1 min early against them on 40 days, December 2025 to June 2026.
  - `se.cautious` inherited 24 of those days.
  - Diyanet's Stockholm unit now takes 69 s more on Fajr (`STOCKHOLM_IFIS_FAJR` in `Diyanet.kt`, this
    change owned by 7g): 64 s clears every day, plus the fits' 5 s safety.
  - 0 early against IFIS. Against Diyanet's own Stockholm table, Fajr runs up to 5 min late in April and
    August, where it was 4. The Stockholm unit's exception is now 5, with the reason.

## What is not proven, and the owner's questions

**Not proven:**

- the edges beyond every point table (Bergen and Norway's other towns, the Nordics beyond the cities
  held, Australia's and New Zealand's other cities);
- Canada outside the eleven Prairie, BC, Montreal and Ottawa tables;
- the UK outside the Mawaqit survey;
- summer at FIDS Zürich, and at Amsterdam's Moroccan calendar;
- next year's LMA Asr;
- a second LUPT year (class A needs one);
- IRN's Tromsø calendar beyond 2026 (its window dates are widened a day either way until 2027's is held),
  and IRN's other Arctic calendars.

**Owner questions** (the report carries them):

1. The US and Montreal/Ottawa take every ISNA mosque's own minutes as a floor, so Dhuhr and Maghrib run
   5–7 min late on most days against the plain 15°/15° majority.
2. Chicago is now cautious: the spec's 18° block would start Fajr before four of the seven tables held.
3. France's 15° family's summer Isha (38 min late). (The UK's 12–14° group is a member since ruling R75:
   the UK section gives its cost.)
4. IISC Calgary's 10°/10° table: the cautious Fajr is before it by 11 to 47 min, every day.
5. Under R39 the ICCI's end of eating came up to 86 min before its printed Fajr. Answered by R80 (28 Sep):
   each authority's own dawn as an end. The ICCI is within 3 min and the GMP within 4 (2 at the mosque's
   latitude alone; the end also holds for everyone within its table's reach), but for four days where
   its page changes method.
6. The Nordics' summer lateness. After R64's split it comes from Diyanet's European method alone:
   Sweden's and its neighbours' Isha is up to 155 min late, and Norway's up to 149 min. The owner decides
   the Nordic summer policy.

