# Task 7a — Saudi Arabia, the Gulf and Egypt

Eight entries: Umm al-Qura (`sa.ummalqura`), Dubai's IACAD (`ae.iacad.dubai`, with Dubai Rural and
Hatta), the UAE's federal Awqaf (`ae.awqaf`), Qatar's Calendar House (`qa.calendarhouse`), Kuwait
(`kw.awqaf`), Bahrain (`bh.council`), Oman (`om.mara`) and Egypt's Survey Authority (`eg.esa`).

    ./gradlew -p tools/timetables jvmTest gate \
      -PgateGroup=sa-ummalqura,qa-calendarhouse,ae-iacad,ae-awqaf,kw-awqaf,bh-council,om-mara,eg-esa

is green: **0 early starts and 0 late ends on 41,572 place-days** (145 gate rows), and no event
beyond its limit. None of these tables has an open licence, so nothing below is a printed time:
only counts, angles, minutes, margins in seconds and points. The stamps in
`tools/timetables/official/stamps/` hold the full tallies, per unit where there are units.

How to read the tables: each row is an event; `early` counts starts shown before the official
minute and `late end` sunrises or ends of eating shown after it; `0/1/2/3+` counts minutes on the
safe side (late for a start, early for an end); `exact` is the share at the official minute.

## Rules applied everywhere

- **Fit and hold out.** Margins come from the Fitter on each group's `fit` rows (the plan's
  Conventions: a start's margin is `max(O − 60 − x) + 1 + 5 s`, an end's `min(O + 60 − x) − 1 − 5 s`).
  Where a held-out row then failed, or sat inside the 5 s, the margin was widened by the excess plus
  5 s and it is said so below. Where no split exists, it says "fit, nothing held out".
- **The method at the user's own point is a floor** (spec §3.5: "a start never earlier, sunrise and
  end of eating never later"). The engine computes a unit's point beside the user's with one set of
  margins, so a margin fitted to a table printed from a point well off the user must not undercut
  the authority's own method at the user's point. Two places needed this: Bahrain's Fajr (its book
  prints the east point's dawn for the whole kingdom) and Egypt's Ras Gharib, Kharga and Taba
  (ESA computes them 65 s to 2 min off the town). There the start keeps the later and the end the
  earlier of the table and the method at the town; `GulfEgyptProofTest` checks this over 2026.
- **Checked across a zone, not only at its point.** Where one table serves a zone (Dubai, Doha's
  east coast, Bahrain), the gate also checks it at the zone's towns, since the engine keeps each
  place's own sun too; the extra lateness that brings is measured, and recorded as an exception
  only where it passes the class limit.

## Umm al-Qura — `sa.ummalqura`, class A

**Evidence.** KACST's own API (GetPrayerByYear) at 12 points, 13,146 place-days, 2024–2030,
including 1,080 Ramadan place-days (Ramadan 1445–1452). The fast begins at its Fajr (`F+E`).

**Method** (unchanged from the research): Fajr 18.5° with a 3-harmonic declination bias, Asr at the
Asr-moment declination plus 0.4 × that bias, Isha = Maghrib + 90, + 120 on Umm al-Qura's Ramadan
dates, the lag dates' later-of-two-days rule, Standard Asr.

**Split.** Forward in time and across the country: fit on the nine tables that end by 2027
(Madinah, Jeddah, Dammam, Abha 2025–2027; Haql, Al Kharkhir, At Tuwal, Taif, Tabuk 2026: 6,205
place-days); held out, Makkah and Riyadh 2024–2030 and Turaif (31.7° N) 2024 and 2026–2030:
6,941 place-days, including 2024 (no lag dates) and 2028–2030 (beyond every fitted day).

**Margins (s).** Fajr +10, sunrise −8, Dhuhr +10, Asr +11, Maghrib +10, Isha +10 (the Task 5 values,
8/−4/7/9/7/7, were never early but with only 1–3 s of safety on this core); the held-out tables need no
more. End of eating +47: the fit rows allow +50, but Turaif, held out, sits only 2 s inside that
(Task 5's +53 had been exactly at its edge), so it takes the fit over all twelve tables with its 5 s.
This is a choice informed by the holdout, made on the safe side.

| held out, 6,941 place-days | early / late end | 0 | 1 | 2 | 3+ | exact |
|---|---|---|---|---|---|---|
| Fajr | 0 | 5,651 | 1,289 | 1 | 0 | 81.4 % |
| sunrise | 0 | 5,761 | 1,172 | 8 | 0 | 83.0 % |
| Dhuhr | 0 | 5,770 | 1,171 | 0 | 0 | 83.1 % |
| Asr | 0 | 5,772 | 1,169 | 0 | 0 | 83.2 % |
| Maghrib, Isha | 0 | 5,768 | 1,173 | 0 | 0 | 83.1 % |
| end of eating | 0 | 5,410 | 1,518 | 13 | 0 | 77.9 % |

All 13,146 place-days: the same shape (Fajr 81.7 %, sunrise 83.3 %, Dhuhr 83.2 %, Asr 82.7 %,
Maghrib and Isha 82.8 %, end of eating 77.7 % exact; every other day 1 min).

**Exception (entry, 2 min, Fajr, sunrise, end of eating).** On a lag date the API prints the
previous day's times; the engine shows the later of the two days' starts and the earlier of their
ends, so that it is never early if KACST stops the lag. That is a minute more than class A allows on
a few days: Fajr on 1, sunrise on 9, the end of eating on 21 of 13,146 place-days, every one a lag
date (checked cell by cell). Dhuhr to Isha never pass 1 min. Following the listed lag exactly would
remove the exception, but needs a day-rule change in the engine core (see the report's concerns).

**Other methods.** `other.ummalqura` keeps the plain ±30 s (ruling R31) and no exception.

**Not proven.** The lag list after 2030 (and any change KACST makes to it); the API's terms of use
(unread: Cloudflare 403); Task 8's "Fajr 5 minutes later" option, which is not this entry's.

## Umm al-Qura's city points (9 Oct 2026, ruling R44)

**Why.** The official page (ummulqura.org.sa) lists 173 places, each region's capital, its governorates
and five centres, in its own `assets/data/cities.json`, and for each it passes those coordinates to KACST's
GetPrayerByYear: one table per place, which a city's mosques follow. The gate's twelve points are twelve of
them. The engine computed the user's own point, and on `main` (705c4f7f) the app's Riyadh point, 3.8 km
east of KACST's, began Fajr, Dhuhr, Asr, Maghrib or Isha a minute before Riyadh's table on 27 days of
2024–2030 (39 cells). At the app's Madinah point, 8 km away, sunrise and the end of eating came a minute
after Madinah's on 38 days (40 cells).

**The units** (`UmmAlQura.units`, `data/UmmAlQuraCities.kt`). Each of the 173 places is a unit at KACST's
own point, which rides beside the user's (ruling R15: starts the later, ends the earlier). Its reach is
one minute of longitude (R40 for class A: 24 to 27 km). The nearest unit within its reach holds a place. A
unit is called by the app's own name for the city. Beyond every unit the method is used at the user's
own point, the end of eating at SAFE_END (R44), class D and no figure. Of the app's 98 Saudi cities, 94
are in a unit. 89 of them are within 10 km of its point, and Ash Shafa (21 km), Qaisumah (18), At Taraf
(13) and Safwa (11) are farther. Tayma is the special case below. Turubah, King
Khalid Military City, Al Mash'iliyah and one of the two Al 'Aqiq have no KACST place within a minute's
reach.

**Tayma.** KACST's list places Tayma at 28.63° N, a degree north of the town (27.62° N, the same
longitude). So the official Tayma table is the API's answer 111 km north, and at the town's own point the
user's own sun began Asr, Maghrib and Isha up to 3 min, and Fajr up to 2, before it on about 150 days a
year. Within a minute's reach of the town, a place now takes that table's unit (`UnitSet.choose`). Its
times are the later of the table's and the town's own, up to 5 min after the table at Fajr and 3 at
sunrise, Asr, Maghrib and Isha. So the unit is class D (R57), with a 5-min Fajr limit and a 3-min limit
on sunrise and the end of eating (above the entry's lag-date 2).

**Evidence.** The new monthly source `sa-ummalqura-cities` (the same API and parser as
`sa-ummalqura`) read 2026 and 2027 at the other 161 places on 9 Oct 2026. The captures are pinned under
`archive/tables/pinned/sa-ummalqura-cities/2026-10-09/`, with the raw JSON in
`archive/raw/monitor/sa-ummalqura-cities/2026-10-09/` and the list in
`archive/raw/manual/sa-ummalqura/2026-10-09/`. Every row is held out. At their own points, 322 tables
and 117,530 place-days, the current margins gave 0 early, 0 late ends and none over the limit. Then each
Saudi city of the app's list was gated at the app's own point against its unit's tables (180 rows,
77,388 place-days, `sa.ummalqura/<unit>`): 0 early, 0 late ends.

**The app's points beyond class A's minute.** West and south of KACST's point, the start is the user's
own sun. Three units' app points passed the minute by one, on 1 to 3 days, and record it as unit late
limits (R41):

| unit | the app's point | events | worst | days |
|---|---|---|---|---|
| taif | Ash Shafa, 21 km south-south-west | Asr, Maghrib, Isha | 2 | 3 (2026–27) |
| madinah | Sultanah, 7 km south-west | Maghrib, Isha | 2 | 1 (2025–27) |
| al-hofuf | Al Hufuf, 5 km south-west | Maghrib, Isha | 2 | 1 (2026–27) |

At 95 % of every unit's radius, north, east, south and west (CityUnitsTest, the archive's tables), there
are 0 early and 0 late ends. Starts run up to 2 min after the city's table, and sunrise and the end of
eating up to 3 min before it.

**The figures.** Each Saudi page and About now show the city's own unit's worst start. Before, every
place showed the entry's 2. Now Makkah, Riyadh, Jeddah, Dammam, Tabuk, Abha and Buraydah read 1, and
Madinah and Taif read 2. The page's date and About's are the unit's own last checked day: 31 Dec 2030 at
Makkah and Riyadh, 31 Dec 2027 at the others.

| all 520 rows, 210,254 place-days | early / late end | 0 | 1 | 2 | 3+ | exact |
|---|---|---|---|---|---|---|
| Fajr | 0 | 168,479 | 41,433 | 108 | 234 | 80.1 % |
| sunrise | 0 | 171,064 | 38,774 | 387 | 29 | 81.4 % |
| Dhuhr | 0 | 173,161 | 37,093 | 0 | 0 | 82.4 % |
| Asr | 0 | 171,084 | 38,987 | 163 | 20 | 81.4 % |
| Maghrib, Isha | 0 | 171,204 | 38,843 | 163 | 44 | 81.4 % |
| end of eating | 0 | 159,250 | 50,502 | 502 | 0 | 75.7 % |

The days at 2 and 3+ minutes are Tayma's (class D) and the three units above, and the lag dates' Fajr,
sunrise and end of eating. No margin changed.

**Not proven.** A place between KACST's places, or one whose mosques read another place's table than
the nearest, is checked only through the nearest unit's tables. Tayma's coordinate is KACST's to
correct. 2028 onwards waits for the monitor: the recipe adds each new year's rows at the 173 points, not
at the app's points.

## Qatar — `qa.calendarhouse`, class B

**Evidence.** Calendar House's printed Doha calendar (16 Jun–11 Sep 2026, and a second copy to
11 Oct), its Ramadan 1447 imsakiya, the Ministry of Awqaf's API for all 2026, and the Calendar
House website's own header (50 Wayback captures, 2023–2026). The API prints the calendar's Fajr,
sunrise, Maghrib and Isha, and Dhuhr and Asr a minute earlier on all 119 days both print; Taqwa
follows the later, so the API is checked at F S M I and Dhuhr and Asr against the derived calendar
year (the API plus that minute, labelled derived). The website header prints the API's Dhuhr and
Asr and is checked at F S M I. The calendar says the east coast from Al Khor to Al Wakrah uses
Doha's times, so Doha's table is also checked at seven towns of the coast and the metro (the app's
points: Al Khor, Az Za'ayin, Lusail, Umm Salal Muhammad, Ar Rayyan, Mu'aydhir, Al Wakrah).

**Method.** Fajr 18°, Isha = Maghrib + 90 all year (Ramadan too), Maghrib + 3 all year (spec §6.1),
Standard Asr; the fast begins at its Fajr.

**Split.** Forward in time: fit on the print to 11 Sep 2026 and the Ramadan imsakiya (118 days);
held out, the second copy's later days, the rest of 2026 from the API, the website's 2023–2025
and the seven towns: 2,845 place-days.

**Margins (s).** Dhuhr +37, Asr +40, Maghrib and Isha −28 (Maghrib is sunset + 152 s; Ramadan 1447
needs + 147 s). Widened on the holdout, as the procedure says: the website's 21 May 2024 Fajr was a
minute early with the fitted −73, and its 8 Aug 2024 end of eating a minute late with −45, so Fajr
−63 and end of eating −54 (the excess plus 5 s; Task 5 had −44); sunrise, not late there but inside
its 5 s, +15 (the fit said +18).

| held out, 2,845 place-days | early / late end | 0 | 1 | 2 | 3+ | exact |
|---|---|---|---|---|---|---|
| Fajr | 0 | 1,242 | 2,557 | 86 | 0 | 32.0 % |
| sunrise | 0 | 2,631 | 1,254 | 0 | 0 | 67.7 % |
| Dhuhr | 0 | 2,332 | 1,503 | 0 | 0 | 60.8 % |
| Asr | 0 | 1,978 | 1,759 | 98 | 0 | 51.6 % |
| Maghrib, Isha | 0 | 418 | 2,218 | 1,243 | 6 | 10.8 % |
| end of eating | 0 | 2,011 | 1,789 | 85 | 0 | 51.8 % |

At Doha's own point every event is within 2 min. Maghrib is late by 1–2 min on most days because
"+ 3 all year" keeps the calendar's Ramadan Maghrib: outside February and March its Maghrib needs only
sunset + 121 s against + 152 s. A Ramadan-only minute (Maghrib + 2, + 1 min on Ramadan dates widened
by a day) would take about half a minute off ten months of Maghrib and Isha; that is the spec's
decision to revisit, not this subtask's.

**Exception (unit Al Khor, 3 min, Maghrib and Isha).** Al Khor, 44 km north of Doha, follows Doha's
table, and the engine also keeps Al Khor's own sunset, about 50 s after Doha's in summer; with
Maghrib + 3 kept all year its Maghrib and Isha are 3 min late on 6 days of 2026. Al Khor is a unit
of its own (10 km, Doha's point as its fixed point) so that Doha keeps its 2 min; Az Za'ayin, 12 km
south of Al Khor, stays within 2.

**Zones, not measured.** Al Shamal, Dukhan, Abu Samra, Mesaieed and Halul print their own rows, whose
points are not known. The website header's zone ids are inconsistent (its 2023 captures sit up to
16 min from its 2026 ones at the same id; its "Mesaieed" runs 4–6 min before Doha, which no point
near Mesaieed can). Where they look geographical (2025–2026: Al Shamal, Dukhan, Abu Samra, Halul) they
want Fajr up to about 75 s later and sunrise 65 s earlier than Doha's margins give at the zone towns,
so the zones and the rest of Qatar take 90 s either way (not the registry's usual 60) and claim
nothing. An unsourced one-day file named for Al Wakrah (`off-qatarch-wakra.txt`) prints Fajr 2 min
after Doha's calendar that day; with no provenance it is not used, but it is a reason to keep the
east-coast claim to what the calendar states.

## Dubai — `ae.iacad.dubai`, class B (Dubai Rural D)

**Evidence.** IACAD's perpetual zone tables (identical by month and day over 2022–2025): the captured
spreadsheets (Dubai 2022 to June 2025, Dubai Rural the same, Hatta 2023 to June 2025) and the same
tables mapped onto 2026–2029 (derived; Khaleej Times' September 2026 copy matches on 30 of 30 days).

**Split.** Forward in time: fit on the captured years (for Dubai and Dubai Rural a whole leap cycle);
held out, the mapped 2026–2029, the years the app shows. Each zone's table is also checked across
its zone: Dubai's at Jebel Ali, the Marina, the Palm, Deira, Al Qusais, Mirdif, Al Mizhar and Silicon
Oasis (2026–2029), Dubai Rural's at Lahbab.

**Margins (s).** Each zone its own. Dubai: Fajr −10, sunrise −209, Dhuhr +162, Asr +60, Maghrib +216,
Isha +24, end of eating −14. Hatta: +45, −227, +118, +18, +229, −24, end of eating +39. Dubai Rural:
+4, −241, +111, +8, +209, −35, end of eating +12, at 24.66° N: its point is not published, and a scan of
latitudes puts the narrowest bands there (107–122 s over the leap cycle, against 138–153 s at the
earlier guess of 24.90° N; longitude only shifts every time alike). Held out at the three zones'
points, 0 early and 0 late ends; at most 2 min.

| held out, 16,613 place-days (all towns) | early / late end | 0 | 1 | 2 | 3+ | exact |
|---|---|---|---|---|---|---|
| Fajr | 0 | 7,212 | 8,660 | 741 | 0 | 43.4 % |
| sunrise | 0 | 5,916 | 10,293 | 404 | 0 | 35.6 % |
| Dhuhr | 0 | 8,446 | 7,959 | 208 | 0 | 50.8 % |
| Asr | 0 | 5,924 | 9,729 | 960 | 0 | 35.7 % |
| Maghrib | 0 | 4,874 | 10,371 | 1,368 | 0 | 29.3 % |
| Isha | 0 | 2,667 | 10,954 | 2,959 | 33 | 16.1 % |
| end of eating | 0 | 3,618 | 11,162 | 1,824 | 9 | 21.8 % |

**Exception (unit Dubai, 3 min, end of eating).** One table serves the city and the engine keeps each
place's own dawn: at Al Mizhar, the city's north-east, the dawn is about 50 s before the Dubai
point's, and the end of eating is 3 min before the table on 8 of 1,461 June days.

**Dubai Rural is class D** (measured, 3 min): its table fits no point to the minute; within 2 min at
its point, 3 at Lahbab, its north (Isha on 33 summer days, the end of eating on 1).

**Not proven.** The perpetual table beyond 2029 (the sun drifts a few seconds a leap cycle; the 5 s
safety covers roughly one more cycle); Dubai Rural's real point.

## UAE Awqaf — `ae.awqaf`, class D

**Evidence.** One day: 25 September 2026, read from awqaf.gov.ae's page for ten areas, at the app's
points. Abu Dhabi's 2013–2014 monthly tables are an older method (Isha Maghrib + 90 until 2017; their
Fajr runs from 8 min before this method's in January to 2 min after it in June, their Dhuhr about
4 min before) and prove nothing about today's, so they are not gate rows.

**Margins (s).** That day's never-early fit (Fajr −14, sunrise −222, Dhuhr +144, Asr +6, Maghrib +159,
Isha −38, end of eating +22), plus the seasonal allowance IACAD's own tables need over the year beyond
their 23–27 September days (Fajr +15, sunrise −22, Dhuhr +14, Asr +19, Maghrib +1, Isha 0, end of
eating −25), plus 60 s for the unknown zone points: Fajr +61, sunrise −304, Dhuhr +218, Asr +85,
Maghrib +220, Isha +22, end of eating −63. Task 5's (Dubai's method + 60, sunrise −120) was never early
too but 3 min late at most events and 4 min early at Fujairah's sunrise. Fit, nothing held out.

| 10 place-days | early / late end | 0 | 1 | 2 | 3+ |
|---|---|---|---|---|---|
| Fajr | 0 | 0 | 7 | 3 | 0 |
| sunrise | 0 | 0 | 1 | 1 | 8 |
| Dhuhr | 0 | 0 | 2 | 7 | 1 |
| Asr | 0 | 0 | 5 | 5 | 0 |
| Maghrib | 0 | 0 | 4 | 6 | 0 |
| Isha | 0 | 0 | 6 | 4 | 0 |
| end of eating | 0 | 0 | 3 | 7 | 0 |

(3+ here is exactly 3.) **Not proven:** every other day of the year; the zones' points; Jebel Jais and
Jebel Hafeet (−2° horizon, not measured). Awqaf's monthly downloads would settle it.

## Kuwait — `kw.awqaf`, class D

**Evidence.** No official table is publicly reachable: the ministry's times for 25 Sep 2026 (one day,
second-hand) and 24 captures of Al-Anba's prayer box, 2019–2026 (a newspaper that names no source).
About half of those pages print the day before the date they state (2023-01-18 and 01-19 print the
same times); dated as printed, a stale page is up to 4 min off. The gate reads the research's
redated copy, in which those pages move back a day (one back two) — an inference, stated as such.

**Margins (s).** The never-early fit on those 25 days (Fajr +6, sunrise +11, Dhuhr −28, Asr +17,
Maghrib −11, Isha −9, end of eating +6) and 60 s more on every time for the unknown point and thin,
second-hand data. Task 5's first guess (up to +185 s) ran 4–5 min late. Fit, nothing held out.

| 25 place-days | early / late end | 0 | 1 | 2 | 3+ |
|---|---|---|---|---|---|
| Fajr | 0 | 0 | 11 | 14 | 0 |
| sunrise | 0 | 0 | 18 | 7 | 0 |
| Dhuhr | 0 | 0 | 22 | 3 | 0 |
| Asr | 0 | 0 | 6 | 19 | 0 |
| Maghrib | 0 | 0 | 17 | 8 | 0 |
| Isha | 0 | 0 | 16 | 9 | 0 |
| end of eating | 0 | 0 | 14 | 11 | 0 |

## Bahrain — `bh.council`, class D

**Evidence.** The Supreme Council's 1448 calendar book, one table for the kingdom (16 Jun 2026 to
5 Jun 2027, 355 days, Ramadan 1448 included), checked at the method's fixed point and the app's eight
Bahraini towns (3,195 place-days).

**Method, and a better point.** The council computes Fajr and sunrise for the easternmost point and
Dhuhr to Isha for the westernmost. A scan of latitudes shows the book fitting its sun to the rounding
(bands of 64–68 s) at two latitudes, as it says: Fajr and sunrise at about 26.22° N, Dhuhr to Isha at
about 26.15° N, Umm an Nasan's, already the start point. Task 5's fixed point, the Hawar islands at
25.65° N, gives 210–230 s bands. The fixed point is now the main island's east coast at 26.22° N.

**Margins (s).** Fitted on the whole book at the nine points: sunrise −9, Dhuhr −59 (with the book's
+1 min), Asr −62, Maghrib −36, Isha −118, end of eating −13 (the book rounds down). The fit asks Fajr
−123, which reproduces the book everywhere but puts Fajr up to a minute before a western town's own
dawn with the book's own rounding (the book prints the east point's dawn for all), so Fajr keeps that
rounding at the east point, −56, and runs 1–2 min after the book (the §3.5 floor). One year for one
table: fit, nothing held out.

| 3,195 place-days | early / late end | 0 | 1 | 2 | 3+ | exact |
|---|---|---|---|---|---|---|
| Fajr | 0 | 0 | 1,953 | 1,242 | 0 | 0 % |
| sunrise | 0 | 2,844 | 351 | 0 | 0 | 89.0 % |
| Dhuhr | 0 | 2,547 | 648 | 0 | 0 | 79.7 % |
| Asr | 0 | 2,421 | 774 | 0 | 0 | 75.8 % |
| Maghrib | 0 | 2,853 | 342 | 0 | 0 | 89.3 % |
| Isha | 0 | 2,763 | 432 | 0 | 0 | 86.5 % |
| end of eating | 0 | 2,772 | 423 | 0 | 0 | 86.8 % |

Class D only for want of a held-out year: the 1447 or 1449 book would make it B (or A).

## Oman — `om.mara`, class B at Muscat

**Evidence.** The ministry's Muscat table: all 2026, and January and July 2021 and March and
September 2031.

**Method.** 18°/18°, Dhuhr, Asr and Maghrib + 5, rounded up, the sun once a day at 0h UT.

**Split.** Fit on 2026; held out, 2021 and 2031 (123 place-days, back and forward in time).

**Margins (s).** Fajr +8, sunrise +54, Dhuhr, Asr and Maghrib +9 (with the ministry's 5 min), Isha
+7, end of eating +52 (Task 5 had the first-guess ±30).

| held out, 123 place-days | early / late end | 0 | 1 | 2 | 3+ | exact |
|---|---|---|---|---|---|---|
| Fajr | 0 | 110 | 13 | 0 | 0 | 89.4 % |
| sunrise | 0 | 102 | 21 | 0 | 0 | 82.9 % |
| Dhuhr | 0 | 100 | 23 | 0 | 0 | 81.3 % |
| Asr | 0 | 107 | 16 | 0 | 0 | 87.0 % |
| Maghrib | 0 | 107 | 16 | 0 | 0 | 87.0 % |
| Isha | 0 | 106 | 17 | 0 | 0 | 86.2 % |
| end of eating | 0 | 109 | 14 | 0 | 0 | 88.6 % |

All 488 place-days: at most 1 min, 83–89 % exact. The method is reproduced to the minute; only Muscat
is measured, so the entry is B there and D (±60 s, nothing claimed) at the ministry's 85 other
localities, whose tables are the same method at points not known here.

## Egypt — `eg.esa`, class B

**Evidence.** ESA's times as Dar al-Ifta republishes them (its monthly GetPrayer for September 2026,
its Wayback month tables) and ESA's own all-cities page (Wayback, 2024–2026), at 30 towns: Cairo
402 days, the others 16–45 days each. Only days from 1 January 2025 on are read (a new reader,
`daily-from-2025`): ESA changed its method between its December 2024 and January 2025 captures.

**Method.** Fajr 19.5°, Isha 17.5°, the Asr shadow at the noon declination, nearest rounding, each
city at ESA's own point; the fast begins at its Fajr.

**Split.** Across the country, the research's split B: fit on eleven towns (Cairo, Alexandria,
Aswan, Sallum, Halaib, Rafah, Luxor, Port Said, Edfu, Damietta, Suez); held out on eleven others (El
Arish, Marsa Matruh, Sidi Barrani, Siwa, Sharm El Sheikh, Hurghada, Mut, Bawiti, Quseir, El Tor,
El Dabaa) and on five towns new to the registry (Dahab, Nuweiba, Saint Catherine, Farafra, Shalatin,
at their town centres).

**Margins (s), national.** The eleven gave Fajr +2, sunrise +8, Dhuhr −11, Asr −2, Maghrib +3,
Isha −6, end of eating +9. Held out, Bawiti's 22 Sep 2026 sunrise was a minute late (3 s past) and
Dhuhr, Isha and the end of eating sat inside the 5 s, so the margins are the fit over all 22 towns
with its 5 s: Fajr +2, sunrise 0, Dhuhr −6, Asr −2, Maghrib +3, Isha −4, end of eating +5. Cairo's
earlier +5 s is not needed on this core.

| held out, 691 place-days | early / late end | 0 | 1 | 2 | 3+ | exact |
|---|---|---|---|---|---|---|
| Fajr | 0 | 292 | 399 | 0 | 0 | 42.3 % |
| sunrise | 0 | 385 | 306 | 0 | 0 | 55.7 % |
| Dhuhr | 0 | 406 | 285 | 0 | 0 | 58.8 % |
| Asr | 0 | 370 | 321 | 0 | 0 | 53.5 % |
| Maghrib | 0 | 315 | 376 | 0 | 0 | 45.6 % |
| Isha | 0 | 381 | 310 | 0 | 0 | 55.1 % |
| end of eating | 0 | 428 | 263 | 0 | 0 | 61.9 % |

Every held-out town is within 1 min, and so is Cairo on all its 402 days.

**Towns printed from an off point** (fitted on their own days, nothing held out there). Kharga's ESA
point runs about 70 s later than the town and Taba's about 2 min later: their starts are their own
(Kharga +40..+46, Taba +96..+99), their sunrise and end of eating the national ones. Ras Gharib's runs
about 65 s earlier: its starts are the national ones, its sunrise −42 and end of eating −49 its own.
Kharga and Ras Gharib stay within 2 min.

**Exception (unit Taba, 3 min, sunrise and end of eating).** ESA computes Taba's table about half a
degree west of Taba, and Taqwa keeps Taba's own earlier sunrise and dawn (the §3.5 floor): 3 min
before the table on 23 of 45 days at sunrise and 22 at the end of eating.

**Beyond every unit.** ESA's point for its other ~70 cities is not known and may be as far off as
Taba's, so starts take 105 s over the national margins (Taba's own exceed them by up to 103 s) and
sunrise and the end of eating 60 s under (Ras Gharib's need 42 and 54). Checked by computing all 30
towns at that edge instead of their units: 0 early, 0 late ends, at most 4 min. Nothing is claimed
there.

**Not proven.** A full year anywhere but Cairo (hence B); ESA's points for the untested cities;
Toshka (an ESA table, but no point I could place); the five new towns' points are town centres, not
ESA's.

**The weekly monitor's capture (28 September 2026).** Dar al-Ifta's September 2026 month table for
all 30 towns, the same minutes as the held rows on every day both hold. Dahab's adds 29 days to the
one (25 September) the round held, as a held-out row: 0 early, 0 late ends, within 1 min like every
held-out town; `eg.esa` holds 1,707 place-days (was 1,678). The fetcher reads the current month, so
no capture holds October 2026 yet: every Egyptian page stays held from 1 October (ruling R115) until
a capture of October is gated.

## The Other methods in these files and adhan2's Asr

Task 7h found that adhan2 0.0.7, whose presets the Other methods reproduce, takes one declination
for the whole day, so its Asr runs up to about 34 s after this core's iterated Asr, and
`other.singapore` came out a minute early from it (`proof/7h-other-and-default.md`). The five Other
methods in this subtask's files were run against the old adhan2 engine at twelve places (Cairo,
Makkah, Kuwait, Doha, Dubai, Karachi, Kuala Lumpur, Jakarta, Cape Town, London, New York, Sydney),
every day of 2026 and 2027, in both schools (8,760 place-days each):

| entry | Standard Asr early | Hanafi Asr early | with the allowance |
|---|---|---|---|
| `other.egyptian` | 19 (London 17, New York 2) | 0 | 0 |
| `other.kuwait` | 19 (London 17, New York 2) | 0 | 0 |
| `other.qatar` | 19 (London 17, New York 2) | 0 | 0 |
| `other.dubai` | 19 (London 17, New York 2) | 0 | 0 |
| `other.ummalqura` | 0 | 0 | — |

Each early day was one minute. The four take 7h's `ADHAN2_ASR_ALLOWANCE` (35 s) on Asr over the plain
±30 s (ruling R31); `other.ummalqura`, whose Asr carries Umm al-Qura's own declination bias, was never
early and keeps the plain rounding.

## The new reader

`tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/gate/formats/DailyFrom2025Format.kt`: the
`daily` layout keeping only days from 1 January 2025, for files that hold an older method version
beside the current one (ESA). It drops the earlier days rather than counting them unreadable.
