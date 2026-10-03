# Task 7d — Libya, Tunisia, Algeria, Morocco, Mauritania and Sudan

Six entries: Libya's Awqaf and Dar al-Ifta (`ly.awqaf`), Tunisia's INM (`tn.inm`), Algeria's
Ministry of Religious Affairs (`dz.marw`), Morocco's Habous (`ma.habous`), Mauritania's ministry
(`mr.ministry`) and Sudan's Fiqh Academy (`sd.ministry`).

    ./gradlew -p tools/timetables jvmTest gate \
      -PgateGroup=ly-awqaf,tn-inm,dz-marw,ma-habous,mr-ministry,sd-ministry

is green: **0 early starts and 0 late ends on 4,946 place-days** (64 gate rows), and no event
beyond its limit. None of these tables has an open licence, so nothing below is a printed time: only
counts, angles, minutes, margins in seconds, points and dates. The stamps in
`tools/timetables/official/stamps/` hold the full tallies, per unit where there are units. The owner's
own adhan times for Benghazi and Sabha (spec §10.3, public; since the 28 Sep 2026 completion also
two held-out rows at the end of `ly-awqaf.tsv`, read from `open/LY-AWQAF-OWNER/`, ruling R73) are the
one exception, and they are checked in `shared/.../registry/MaghrebProofTest.kt` too.

How to read the tables: each row is an event; `early / late end` counts starts shown before the
official minute or sunrises and ends shown after it; `0/1/2/3+` counts minutes on the safe side (late
for a start, early for an end); `exact` is the share at the official minute; `limit` is the most the
class or a recorded exception allows.

## Rules applied everywhere

- **Fit and hold out.** Margins come from the Fitter on each group's `fit` rows (the plan's
  Conventions, 5 s safety). Where a held-out row was then early or a late end, the margin was widened
  by the excess plus 5 s (the Fitter over all rows) and it is said so below; where the held-out rows
  held, the fitted margin stands. Where no split exists it says "fit, nothing held out".
- **One season is not a method.** Mauritania and Sudan have only Ramadan tables. There every start
  keeps at least the plain safe 30 s after the method itself and every end at most −30 s (the R31
  rounding), even where one Ramadan's table would allow less; Dhuhr is never before the transit and
  Maghrib never before the standard sunset, whatever a table prints.
- **Duplicates are not rows.** Where the manifest holds the same days twice (OCR stages, copies in
  other research folders), the canonical table is the row and each gate file's header names the
  copies left out, so no day is counted twice.

## Libya — `ly.awqaf`, class D

**Evidence.** awqaf.gov.ly's prayer widget for its 22 cities (25 and 26 Sep 2026, with its emsak,
the imsak: Fajr − 9 to − 11, added to the city files in the archive from the research's own fetches)
and Tripoli's own table: 19 Wayback captures of the widget from March 2025 to September 2026 and
api.ifta.ly's eight days (25 Sep–2 Oct 2026, with imsak). Checked at each city's unit point. No other
multi-month table exists for any Libyan city.

**Method (the owner's answer).** The national method at each of the 22 cities: Fajr 18.5°, Isha 18.3°,
standard Asr; Jalu and Awjila print their Asr 7.5 min after the others' rule (their own offset, 450 s
every month); imsak is the dawn − 11 (the table prints Fajr − 10 or − 11). The end of eating is that
18.5° dawn in the west; east of 18.5° E or south of 29.5° N it is the 19.5° dawn the mosques there
call, with the plain −30 s and no + 20 interim (spec §10.3).

**Completion (28 Sep 2026): the east and south's Fajr and Maghrib move to the local adhan (ruling
R73).** This national table is a projection there too (its 25–26 Sep rows), but it is not what those
mosques call. The owner heard the adhan at two cities on one day, Benghazi and Sabha on 27 Sep 2026
(below): each the 19.5° dawn and sunset + 1, floored, 7 min before the national method's Fajr and 4
min before its Maghrib there. So in the east and south Fajr is now the 19.5° dawn and Maghrib is
sunset + 1 minute, each with the plain safe 30 s margin (no city-by-city fit, like Mauritania and
Sudan), and beyond 30 km of the cities the edge's minute on top. Engine against engine, not an
observation: this method is 5–7 min before the national method's Fajr and 2–3 min before its
Maghrib at Benghazi, Sabha, Tobruk and Kufra on 27 Sep 2026, 21 Jun 2027 and 21 Dec 2026. That is a
projection; what those mosques call in any other season, or at any other city, is not observed.
This table's Fajr and Maghrib columns are excluded for those cities (ly-awqaf.tsv's header records
why) and the owner's two observations are the check instead (two held-out rows at the end of
`ly-awqaf.tsv`, `open/LY-AWQAF-OWNER/`). Dhuhr, Asr, Isha, sunrise, and the west, are unchanged.

**Split.** Fit on the 21 other cities' two days (42 city-days, one season, every reference point);
held out, Tripoli, the one table across the seasons (23 checked days, March 2025 to October 2026) and
its imsak, and since the completion the owner's two adhan rows (Benghazi and Sabha, 27 Sep 2026, Fajr
and Maghrib only). This is the research's split turned round: the research fitted on Tripoli's eight API
days and found the other cities early, because each city's reference point scatters; fitting on the
21 cities takes that scatter in.

**Why the fit alone is not enough (review).** Tripoli's holdout was never early with the margins the
21 cities gave, but Tripoli sits 1–2 min inside most of them, so it cannot show whether the tightest
cities hold in other seasons, and Tripoli's own table drifts against the model through the year: its
Fajr prints about a minute later in spring and summer than in late September, its Dhuhr later in
January–February, its sunrise earlier in July–August. The western end of eating, fitted at +38 s,
already gave six late ends at Tripoli: the same drift, where Tripoli is tightest. If every city
drifts as Tripoli does, summer Fajr would have come a minute before the calendar at Hun, Tobruk,
Brega, Ghadames, Ras Lanuf, Benghazi, Emsaed and Sabha.

**Margins (s), projected.** For each event and city: the city's late-September offset from Tripoli
(its two days against Tripoli's same two days, read three ways: the two bounds, the two means, the
city's mean against the eight API days' mean; the most cautious kept, since two days carry up to a
minute of rounding each) plus Tripoli's worst over its 23 days, plus 5 s; the margin is the most
cautious city's. Every city is then never early on any day on which it drifts as Tripoli does. That
is a projection outside late September, not a measurement.

| event | fitted on the 21 cities | projected, shown | decided by |
|---|---|---|---|
| Fajr | +18 | +63 | Hun |
| sunrise | −66 | −98 | Bayda |
| Dhuhr | +212 | +246 | Hun |
| Asr | −10 | +29 | Hun |
| Maghrib | +241 | +261 | Hun |
| Isha (on 18.3°) | +22 | +42 | Brega |
| end of eating (west) | +38 | +7 | Tripoli |

The end of eating and imsak share the +7 s. An end of eating is where being late matters most, so it
takes the strictest reading (ruling R59): each western city's earliest late-September day against
Tripoli's API mean, plus Tripoli's earliest over its 23 days, which puts Tripoli at 13 s, Zawiya 14
and Khoms 17 (with the +19 s of the three readings above they would have been 3–7 s late); the
margin is Tripoli's less 6 s. Imsak is the dawn − 11 (in the east and south, since the completion,
the 19.5° dawn with −30 s, less 11). It is checked against the printed emsak in the west only: never
after it on the 22 days held (the seven western cities and Tripoli), at most 2 min before it. In the
east and south it reads the moved dawn, so the national emsak there is no longer its check.
Jalu's and Awjila's Asr takes the same margin over their own 450 s.

Since the completion, Fajr and Maghrib are two tables' worth: the national one, 37 place-days at the
7 western cities and Tripoli (unmoved), plus 2 more, Benghazi's and Sabha's own adhan on 27 Sep 2026
(the owner rows of `ly-awqaf.tsv`, not this table, since east and south no longer follow it there —
ruling R73).
Imsak, which now reads the same moved dawn in the east and south, keeps only the western 8.

| all rows, 67 place-days | early / late end | 0 | 1 | 2 | 3+ | exact | worst | limit |
|---|---|---|---|---|---|---|---|---|
| Fajr | 0 | 0 | 20 | 19 | 0 | 0.0 % | 2 | 3 |
| sunrise | 0 | 3 | 33 | 28 | 1 | 4.6 % | 3 | 3 |
| Dhuhr | 0 | 1 | 44 | 20 | 0 | 1.5 % | 2 | 3 |
| Asr | 0 | 3 | 28 | 32 | 2 | 4.6 % | 3 | 3 |
| Maghrib | 0 | 4 | 22 | 13 | 0 | 10.3 % | 2 | 3 |
| Isha | 0 | 2 | 48 | 10 | 5 | 3.1 % | 3 | 3 |
| end of eating | 0 | 19 | 18 | 0 | 0 | 51.4 % | 1 | 3 |
| imsak | 0 | 1 | 20 | 1 | 0 | 4.5 % | 2 | 3 |

Against their own adhan (not this table), Benghazi and Sabha were never early: Fajr 1 min late at
Benghazi, 2 at Sabha; Maghrib 1 min late at each — inside the west's own worst on these events, so
nothing here comes only from the completion.

Held out: Tripoli's 23 days, and for Fajr and Maghrib also the owner's 2 (Benghazi and Sabha).

| held out, 25 place-days | early / late end | 0 | 1 | 2 | 3+ | exact | worst | limit |
|---|---|---|---|---|---|---|---|---|
| Fajr | 0 | 0 | 12 | 13 | 0 | 0.0 % | 2 | 3 |
| sunrise | 0 | 1 | 15 | 7 | 0 | 4.3 % | 2 | 3 |
| Dhuhr | 0 | 0 | 10 | 13 | 0 | 0.0 % | 2 | 3 |
| Asr | 0 | 0 | 6 | 17 | 0 | 0.0 % | 2 | 3 |
| Maghrib | 0 | 0 | 12 | 13 | 0 | 0.0 % | 2 | 3 |
| Isha | 0 | 0 | 11 | 7 | 5 | 0.0 % | 3 | 3 |
| end of eating | 0 | 13 | 10 | 0 | 0 | 56.5 % | 1 | 3 |
| imsak | 0 | 1 | 7 | 0 | 0 | 12.5 % | 1 | 3 |

The other twelve eastern and southern cities' Fajr and Maghrib are "–": nothing there is checked
against either table (no adhan of their own is held), only projected from Benghazi and Sabha's.
Every city in the region keeps "–" at the end of eating and imsak (both follow that same local dawn,
proved by the adhan, not this table); Benghazi's and Sabha's own Fajr and Maghrib are the two extra
place-days, checked against their adhan instead of this one.

| unit | place-days | worst: F · sunrise · D · A · M · I · end of eating · imsak |
|---|---|---|
| Ajdabiya | 2 | – · 2 · 1 · 1 · – · 1 · – · – |
| Awjila | 2 | – · 2 · 1 · 1 · – · 1 · – · – |
| Bani Walid | 2 | 2 · 2 · 1 · 2 · 1 · 1 · 1 · 1 |
| Bayda | 2 | – · 0 · 2 · 3 · – · 2 · – · – |
| Benghazi | 3 | 1 · 2 · 1 · 2 · 1 · 1 · – · – |
| Brega | 2 | – · 2 · 1 · 2 · – · 1 · – · – |
| Derna | 2 | – · 2 · 1 · 1 · – · 1 · – · – |
| Emsaed | 2 | – · 2 · 1 · 2 · – · 1 · – · – |
| Ghadames | 2 | 1 · 2 · 1 · 2 · 1 · 1 · 1 · 2 |
| Hun | 2 | – · 3 · 1 · 1 · – · 1 · – · – |
| Jalu | 2 | – · 2 · 1 · 1 · – · 1 · – · – |
| Khoms | 2 | 2 · 2 · 1 · 2 · 1 · 1 · 1 · 1 |
| Kufra | 2 | – · 2 · 1 · 1 · – · 1 · – · – |
| Marj | 2 | – · 2 · 2 · 1 · – · 2 · – · – |
| Misrata | 2 | 2 · 2 · 2 · 1 · 1 · 1 · 1 · 1 |
| Ras Lanuf | 2 | – · 2 · 1 · 2 · – · 1 · – · – |
| Sabha | 3 | 2 · 2 · 2 · 2 · 1 · 1 · – · – |
| Sirte | 2 | 2 · 2 · 2 · 2 · 1 · 1 · 1 · 1 |
| Tobruk | 2 | – · 2 · 1 · 1 · – · 1 · – · – |
| Tripoli | 23 | 2 · 2 · 2 · 2 · 2 · 3 · 1 · 1 |
| Zawiya | 2 | 2 · 1 · 1 · 2 · 1 · 1 · 1 · 1 |
| Zliten | 2 | 2 · 2 · 2 · 1 · 1 · 1 · 1 · 1 |

Nothing held is more than 3 min late (the class limit). At 3 min: Tripoli's Isha on five days and
Bayda's Asr and Hun's sunrise (in late September, when the drift the margins carry for other seasons
is not needed). That is the price of the projection. Libya's table rests
on the 1992–94 observation committee's work (Dar al-Ifta's statement of 8 Feb 2022), and the
depression of its printed Isha runs at about 17.8–18.1° from March to August and 18.1–18.3° from
September to December. A seasonal curve would take a full Tripoli year to fit safely: fifteen widget
days, one or two a month, do not bound it between them.

**Stale captures (a known exception to the data, not to the promise).** The widget shows "today" as
the page was cached, and a date-shift test puts four captures on an earlier day than their own: all
six printed times, against the model the API days fit at the capture's date and one and two days
before, leave 0.34–0.44 of their own-date residual at the best shift (19 May 2025 and 21 May 2026 two
days back, 2 Feb 2026 and 3 Sep 2026 one), and no other capture's best shift goes below 0.66 (16 June
2026, near the solstice, where a day moves the times least). Read at its capture date, the 3 Sep 2026
capture puts Maghrib and Isha a minute "early", and the May captures Fajr. The reader `awqaf-widget`
skips those four lines (counted as unreadable: 4 of the 10 in the table's header; the other six are
the imsak file's lines without an imsak) rather than re-date them by a guess.

**The owner's adhan times (MaghrebProofTest, the owner rows of `ly-awqaf.tsv`).** On 27 Sep 2026
Benghazi's mosques called Fajr at 05:02 and Maghrib at 18:31, Sabha's at 05:28 and 18:54: the 19.5°
dawn and sunset + 1 there. Those two cities on that one day are all that was observed. Since the
completion (ruling R73) Taqwa follows that rule itself at every eastern and southern city, not the
national calendar: at those two cities' points it shows Benghazi Fajr 05:03, Maghrib 18:32; Sabha
Fajr 05:30, Maghrib 18:55 — never before the adhan, 1–2 min after it. Against the national method
(engine values, not printed), which stays the west's and remains never early there too, the R73
method is 5–7 min earlier at Fajr and 2–3 min earlier at Maghrib at Benghazi, Sabha, Tobruk and Kufra
on 27 Sep 2026, 21 Jun 2027 and 21 Dec 2026 alike. That compares two engine methods, a projection of
the rule heard on 27 Sep: it says nothing about what those mosques call in June or December, or at
Tobruk and Kufra on any day. The end of eating is unaffected by the completion (it already read the
19.5° dawn there); imsak, which reads Fajr's dawn, now reads the 19.5° one too, a few minutes earlier
than before (an end, so on the safe side). The test also walks all 22 cities every 16 days of 2026
(every day in order, the end of eating and imsak never after Fajr), and confirms Tripoli and
Misrata's own margins unmoved.

**Not proven.** A full year at any city (hence D); the reference points (city centres; awqaf.gov.ly
publishes none); the east's and south's Fajr, Maghrib, end of eating and imsak against this table,
since the awqaf calendar is not what those mosques follow there (the owner's two adhan times are the
only check, at the two cities they were heard in; the other twelve eastern and southern cities have
none of their own — ruling R73); the east's and south's Fajr and Maghrib in any other season (the
rule was heard on one late-September day; if the east uses a shallower angle in another season,
Fajr there would be early, R73's recorded cost if wrong); places beyond 30 km of the 22 cities (the
edge, a minute more on every start, the east and south's Fajr and Maghrib included, claims
nothing). The Dar al-Ifta statement of 2022 said the eastern Awqaf had put mosques on a Fajr 15–20
min later; the owner's own adhan times of 27 Sep 2026 show the east now calls Fajr earlier than the
national calendar, not later.

**The weekly monitor's capture (28 September 2026).** The widget's day for all 22 cities (28
September, its emsak as the imsak) and api.ifta.ly's eight days for Tripoli (28 September – 5 October;
the five to 2 October the same as `nile/tripoli.txt`'s), held out with each city's round columns
(east and south of R73's line no Fajr, Maghrib or imsak): 0 early, 0 late ends, within class D's
3 min. Seven units' worst rose a minute, to 2 (Awjila's, Bani Walid's and Jalu's Isha, Derna's Dhuhr,
Marj's, Misrata's and Zliten's Asr). `ly.awqaf` holds 91 place-days (was 67), checked to 5 October at
Tripoli and to 28 September elsewhere; still class D everywhere, so no page.

## Tunisia — `tn.inm`, class B

**Evidence.** meteo.tn per delegation at INM's own reference points: Tunis, Sfax and Tataouine every
day of 2026, Tabarka, Tala and Ben Guerdane every third day, sunrise every third day at four of them;
Tunis 2019–2026 (eight days a year, at INM's earlier Tunis point). INM prints no imsak in these tables,
so imsak (Fajr − 10) is not checked against print; the end of eating is (`F+E`).

**Method.** Fajr 18°, Isha 18°, Dhuhr = transit + 7, Maghrib = sunset + 2, sunrise floored; Asr
first-principles plus the research's monthly table; the dips at the elevated delegations (Tataouine
0.45°, Tala 1.0°, applied to Fajr, sunrise, Maghrib and Isha, their Fajr 60 s more), both found on
those delegations' own tables in the research; the end-of-eating margin is the national one at the
dips too.

**Split.** Fit on the four low delegations' 2026 (Tunis, Sfax, Tabarka, Ben Guerdane: 974 place-days);
held out, Tunis's eight days a year 2019–2026 at INM's earlier point (the eight of 2026 are fitted
dates, at another point) and the two dipped delegations (545 place-days). The dips themselves were
found on the dipped tables, so those rows test the national margins with the dips, not the dips.

**Margins (s).** Fajr −51, sunrise −27, Dhuhr −15, Asr +14, Maghrib +26, Isha +43, end of eating −41
(over the authority's minutes). Nothing held out was early or late, so nothing was widened. Task 5's
margins had been fitted on Tunis alone with extra seconds for Sfax and Tabarka; the Fajr it had
(−56) sat exactly on the no-early bound with no safety.

| all rows, 1,519 place-days | early / late end | 0 | 1 | 2 | 3+ | exact | worst | limit |
|---|---|---|---|---|---|---|---|---|
| Fajr | 0 | 765 | 754 | 0 | 0 | 50.4 % | 1 | 2 |
| sunrise | 0 | 202 | 286 | 0 | 0 | 41.4 % | 1 | 2 |
| Dhuhr | 0 | 1,142 | 377 | 0 | 0 | 75.2 % | 1 | 2 |
| Asr | 0 | 943 | 576 | 0 | 0 | 62.1 % | 1 | 2 |
| Maghrib | 0 | 804 | 715 | 0 | 0 | 52.9 % | 1 | 2 |
| Isha | 0 | 497 | 985 | 37 | 0 | 32.7 % | 2 | 2 |
| end of eating | 0 | 550 | 930 | 39 | 0 | 36.2 % | 2 | 2 |

| held out, 545 place-days | early / late end | 0 | 1 | 2 | 3+ | exact | worst | limit |
|---|---|---|---|---|---|---|---|---|
| Fajr | 0 | 145 | 400 | 0 | 0 | 26.6 % | 1 | 2 |
| sunrise | 0 | 36 | 208 | 0 | 0 | 14.8 % | 1 | 2 |
| Dhuhr | 0 | 419 | 126 | 0 | 0 | 76.9 % | 1 | 2 |
| Asr | 0 | 339 | 206 | 0 | 0 | 62.2 % | 1 | 2 |
| Maghrib | 0 | 122 | 423 | 0 | 0 | 22.4 % | 1 | 2 |
| Isha | 0 | 26 | 482 | 37 | 0 | 4.8 % | 2 | 2 |
| end of eating | 0 | 33 | 473 | 39 | 0 | 6.1 % | 2 | 2 |

| unit | place-days | worst: F · sunrise · D · A · M · I · end of eating · imsak |
|---|---|---|
| Ben Guerdane | 122 | 1 · – · 1 · 1 · 1 · 1 · 1 · – |
| Sfax | 365 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |
| Tabarka | 122 | 1 · – · 1 · 1 · 1 · 1 · 1 · – |
| Tala | 122 | 1 · 1 · 1 · 1 · 1 · 2 · 2 · – |
| Tataouine | 365 | 1 · 1 · 1 · 1 · 1 · 2 · 1 · – |
| Tunis | 415 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |

**Class B**: at most 1 min late at the four low delegations and on Tunis's earlier years, 2 at the
dipped ones (Isha and the end of eating), a full year at three places including Ramadan.

**Not proven.** The other 252 delegations (their points are INM's but their elevations are not known:
the edge takes the deepest dip seen for sunrise, Maghrib and Isha and claims nothing); INM's Asr
definition (a monthly table fitted on one year); the dips' mechanism (INM's elevation data was not
found).

**The weekly monitor's capture (28 September 2026).** INM's coming seven days (28 September – 4
October) for the six delegations, with its sunrise on each. Tabarka's, Ben Guerdane's and Tala's are
held-out rows: they add 29–30 September and 2–3 October to their every-third-day tables, and Tabarka's
and Ben Guerdane's first sunrise cells (7 each): 0 early, 0 late ends, worst 1 min (Tala's Isha and end
of eating 2, as before). Tunis's, Sfax's and Tataouine's print the same minutes as their rows and add
only four sunrises each, so they are not rows. `tn.inm` holds 1,531 place-days (was 1,519).

## Algeria — `dz.marw`, class B at the base cities, D at Oran and Tamanrasset

**Evidence.** The ministry's 1448 calendars (image PDFs, OCR checked by eye): Algiers, Djelfa and Adrar
every day from 16 Jun 2026 to 5 Jun 2027; eight days of Algiers's 1447 calendar (2025); and the tables
the ministry derives for Oran (Algiers plus a monthly difference) and Tamanrasset (Adrar plus a
half-monthly one). MARW prints no imsak (`F+E`).

**Method.** Fajr 18°, Isha 17°, Maghrib + 3, every column rounded up (sunrise too); Asr by the one-shot
formula with the sun at 12h UT (the research's finding, now in the method: `AsrModel.UTC12_ONE_SHOT`);
no elevation. Every other wilaya is computed at its own seat (the owner's answer, spec §10.4).

**Split.** Base cities: fit on Algiers 1448 (355 days); held out, Djelfa and Adrar 1448 and Algiers
1447 (718 place-days). Oran and Tamanrasset: each fits its own seat's margins on its derived table,
nothing held out.

**Margins (s).** Base: Fajr +14, sunrise +49, Dhuhr +7, Asr +10, Maghrib +11 (after the 3 min), Isha
+14, end of eating +46; nothing held out was early, so nothing was widened. Oran: Fajr +135, sunrise
+62, Dhuhr +21, Asr +111, Maghrib +132, Isha +144, end +65. Tamanrasset: Fajr +188, sunrise +67, Dhuhr
+19, Asr +195, Maghrib +178, Isha +194, end +62. Every other seat takes the later of the two for each
start and the earlier sunrise (Fajr +188, sunrise +62, Dhuhr +21, Asr +195, Maghrib +178, Isha +194),
and the edge's end of eating (−30 s at most, ruling R44).

| all rows, 1,783 place-days | early / late end | 0 | 1 | 2 | 3+ | exact | worst | limit |
|---|---|---|---|---|---|---|---|---|
| Fajr | 0 | 900 | 558 | 252 | 73 | 50.5 % | 3 | 3 |
| sunrise | 0 | 1,108 | 530 | 128 | 17 | 62.1 % | 3 | 3 |
| Dhuhr | 0 | 1,601 | 182 | 0 | 0 | 89.8 % | 1 | 3 |
| Asr | 0 | 983 | 509 | 197 | 94 | 55.1 % | 6 | 6 |
| Maghrib | 0 | 906 | 495 | 321 | 61 | 50.8 % | 3 | 3 |
| Isha | 0 | 890 | 532 | 272 | 89 | 49.9 % | 3 | 3 |
| end of eating | 0 | 976 | 577 | 196 | 34 | 54.7 % | 3 | 3 |

| held out, 718 place-days | early / late end | 0 | 1 | 2 | 3+ | exact | worst | limit |
|---|---|---|---|---|---|---|---|---|
| Fajr | 0 | 549 | 169 | 0 | 0 | 76.5 % | 1 | 2 |
| sunrise | 0 | 611 | 107 | 0 | 0 | 85.1 % | 1 | 2 |
| Dhuhr | 0 | 634 | 84 | 0 | 0 | 88.3 % | 1 | 2 |
| Asr | 0 | 589 | 129 | 0 | 0 | 82.0 % | 1 | 2 |
| Maghrib | 0 | 563 | 155 | 0 | 0 | 78.4 % | 1 | 2 |
| Isha | 0 | 548 | 170 | 0 | 0 | 76.3 % | 1 | 2 |
| end of eating | 0 | 561 | 157 | 0 | 0 | 78.1 % | 1 | 2 |

| unit | place-days | worst: F · sunrise · D · A · M · I · end of eating · imsak |
|---|---|---|
| Adrar | 355 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |
| Tamanrasset | 355 | 3 · 3 · 1 · 6 · 3 · 3 · 3 · – |
| Algiers | 363 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |
| Djelfa | 355 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |
| Oran | 355 | 3 · 2 · 1 · 2 · 3 · 3 · 3 · – |

The held-out table is the three base cities alone: at most 1 min late. Oran runs up to 3 min late
(Fajr on 3 days, Maghrib, Isha and the end of eating on 1 each) and Tamanrasset 3, its Asr 6: the
ministry's differences are constant for a month or half a month while the true difference between
the base city and the seat drifts through it, so a margin that is never early over the year is late
through part of each month.

**Classes.** Algiers, Djelfa and Adrar: B (a year at three spread places including Ramadan, at most
1 min late held out; B rather than A because MARW's reference points are not published). Oran and
Tamanrasset: D, from the data. The other 53 seats: not measured, nothing claimed.

**Where the seat's table is claimed.** A whole wilaya follows its seat's table, so the seat rides as
the fixed point everywhere in it (a start is never before the seat's). West of the seat the user's own
sun is later, so the claim holds only within the class's reach (ruling R40: 44–49 km at the base
cities for B, 68 km at Oran and 77 km at Tamanrasset for D); beyond it the same wilaya unit is marked
not measured and claims nothing. Adrar's and Tamanrasset's wilayas are hundreds of kilometres wide.

**Not proven.** The 1449 calendar (spec §6.3: re-prove on it); the other 53 wilayas' derived tables
(printed in the calendars but not read); computing each wilaya exactly as the ministry does (base plus
its differences) would copy the ministry's difference rows, which its pages say may not be reproduced.

## Morocco — `ma.habous`, class B at the fitted cities

**Evidence.** habous.gov.ma's city tables: the live month (13 Sep–12 Oct 2026, UTC+0) at ten cities
and Imilchil, and Wayback captures of month pages 2015–2025 (legal time) at nine of them, the site's
default page (Rabat, 2019–2022) and Rabat's 2022–2023 days and Ramadan 1445: 1,509 place-days. Habous
prints no imsak (`F+E`).

**Reading the pages (two new readers).** `habous-live` reads a file's live month as printed (UTC);
`habous-archive` reads its Wayback pages into UTC. A page is one Hijri month printed in one clock,
which is not always tzdata's for each of its days (the Sha'ban 1441 page kept UTC+1 after tzdata's
switch of 19 April 2020, the Shawwal 1441 page UTC+0 after tzdata's return of 31 May, the Sha'ban 1445
page UTC+1 on 10–11 March 2024): the reader starts each run of days in tzdata's offset and follows the
page's own clock when its Dhuhr jumps by half an hour or more. On all 1,144 archived lines the clock
so read is the one the line's own Dhuhr implies (the transit + 5 min at the city). It skips 22
misdated lines (counted as
unreadable): the **Laayoune capture of 19 Sep 2020 is dated October from 19 Oct 2020**, where its
Dhuhr jumps by about 9 min (those twelve days are September's), and two runs on the default page
(28–31 Jan 2020 and 1–6 Oct 2021, ten days) carry another month's days, 4 to 11 min off.

**Method.** Fajr 19° (rounded down), Isha 17°; national margins for Fajr, Dhuhr, Asr, Isha and the end
of eating; sunrise and Maghrib per city (Habous corrects them for elevation).

**Split.** Fit on the ten cities' live month (300 place-days); held out, every Wayback page, Rabat's
2022–2023 days and Ramadan 1445, and Imilchil (1,209 place-days, 2015–2025, 126 in Ramadan).

**Margins (s).** National: Fajr −29, Dhuhr +293, Asr +12, Isha −7, end of eating −9. The live month
gave Fajr −36 and Asr −6; the held-out pages put Fajr a minute early on one day and Asr on eleven, so
Fajr was widened by 7 s and Asr by 18 s. Per city (sunrise / Maghrib, before 20 s more safety):

| city | fitted on the live month | held-out pages asked | shown |
|---|---|---|---|
| Casablanca | −159 / +252 | −179 / +271 | −199 / +291 |
| Rabat | −150 / +206 | −163 / +225 | −183 / +245 |
| Oujda | −297 / +339 | −314 / +365 | −334 / +385 |
| Tangier | −218 / +259 | −225 / +272 | −245 / +292 |
| Laayoune | −117 / +207 | −128 / +220 | −148 / +240 |
| Dakhla | −145 / +183 | — / +219 | −165 / +239 |
| Figuig | −319 / +354 | −354 / +385 | −374 / +405 |
| Midelt | −416 / +427 | −441 / +456 | −461 / +476 |
| Lagouira | −118 / +201 | — | −138 / +221 |
| Ifrane | −403 / +421 | (no page) | −439 / +457 |

Eight of the nine cities with Wayback pages needed their sunrise or Maghrib widened, by 7–36 s, for
another season or year, so each fitted city keeps 20 s more than the Fitter's 5 (the city points are
centres, and most cities' pages cover one or two seasons), and Ifrane, with its live month alone,
36 s more (the most another city's pages widened a margin, Dakhla's Maghrib). The base method's
sunrise and Maghrib (−479 / +466) are those that hold at every place checked. The Task 5 values
(research fits + 25 s, Dakhla, Figuig and Midelt widened for their 2019/2022 pages) were never early
either; the new ones are fitted on this core and cover every page.

| all rows, 1,509 place-days | early / late end | 0 | 1 | 2 | 3+ | exact | worst | limit |
|---|---|---|---|---|---|---|---|---|
| Fajr | 0 | 900 | 592 | 17 | 0 | 59.6 % | 2 | 3 |
| sunrise | 0 | 644 | 865 | 0 | 0 | 42.7 % | 1 | 7 |
| Dhuhr | 0 | 1,080 | 413 | 16 | 0 | 71.6 % | 2 | 3 |
| Asr | 0 | 649 | 836 | 24 | 0 | 43.0 % | 2 | 3 |
| Maghrib | 0 | 593 | 916 | 0 | 0 | 39.3 % | 1 | 6 |
| Isha | 0 | 1,093 | 403 | 13 | 0 | 72.4 % | 2 | 3 |
| end of eating | 0 | 1,102 | 407 | 0 | 0 | 73.0 % | 1 | 3 |

| held out, 1,209 place-days | early / late end | 0 | 1 | 2 | 3+ | exact | worst | limit |
|---|---|---|---|---|---|---|---|---|
| Fajr | 0 | 714 | 478 | 17 | 0 | 59.1 % | 2 | 3 |
| sunrise | 0 | 539 | 670 | 0 | 0 | 44.6 % | 1 | 7 |
| Dhuhr | 0 | 869 | 324 | 16 | 0 | 71.9 % | 2 | 3 |
| Asr | 0 | 541 | 644 | 24 | 0 | 44.8 % | 2 | 3 |
| Maghrib | 0 | 522 | 687 | 0 | 0 | 43.2 % | 1 | 6 |
| Isha | 0 | 882 | 314 | 13 | 0 | 73.0 % | 2 | 3 |
| end of eating | 0 | 881 | 328 | 0 | 0 | 72.9 % | 1 | 3 |

| unit | place-days | worst: F · sunrise · D · A · M · I · end of eating · imsak |
|---|---|---|
| Casablanca | 210 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |
| Dakhla | 60 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |
| Figuig | 120 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |
| Ifrane | 30 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |
| Laayoune | 224 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |
| Lagouira | 60 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |
| Midelt | 60 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |
| Oujda | 60 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |
| Rabat | 595 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |
| Tangier | 60 | 1 · 1 · 1 · 1 · 1 · 1 · 1 · – |

**The edge: the other ~180 Habous cities.** Taqwa knows neither their elevation nor Habous's point
for them. Sunrise and Maghrib take a national margin (−510 / +510 s) that covers the highest town
checked, Imilchil (about 2,150 m); Fajr, Dhuhr, Asr and Isha take the national margins and a minute
more, like the Libyan and Tunisian edges; the end of eating moves with sunrise (−40 s). Checked at
Imilchil, the one unfitted table held: nothing early, sunrise and Maghrib at most 1 min late, the other
starts 2.

**Recorded exceptions (entry, beyond the fitted cities).** Sunrise up to 7 min and Maghrib up to 6 min:
at a low town the edge's margins are that far from Habous's own (against Lagouira's, the lowest: 392 s
before its sunrise, 309 s after its Maghrib, each read in whole minutes). Each fitted city carries its
own 2-min limit for those two events, so the exceptions apply only beyond them.

**Not proven.** The other ~180 Habous cities (spec §6.3: slice 3 fits each city's horizon); Habous's
own reference points; the default page and the Ramadan 1445 page are taken as Rabat's because their
Dhuhr sits as far after Rabat's transit as Rabat's own page's does.

## Mauritania — `mr.ministry`, class D

**Evidence.** The ministry's Ramadan imsakiyas only: Nouadhibou 1444 (2023, eight days) and
Nouakchott 1445 (2024, thirty days, with imsak). Nothing shows a year-round table is followed.

**Method.** Fajr 19.5°, Isha 17.5°, Maghrib = sunset + 2, imsak = Fajr − 5 (now shown: the ministry
prints it), standard Asr.

**Split.** Forward in time and to another city: fit on Nouadhibou 1444, held out Nouakchott 1445.
The 1444 fit (Fajr −26, Dhuhr −26, Asr −17) was early at Nouakchott (Fajr on 21 days, Dhuhr 5, Asr 8),
so they were widened to +18, −11 and +5; then every start was raised to the plain safe 30 s (one season)
and the end of eating lowered to −30 s from the +35 the tables allow. Sunrise −124 s: Nouadhibou prints
its sunrise about two minutes before a −0.83° sunrise, and Nouakchott shows that as up to 3 min early.

| all rows, 38 place-days | early / late end | 0 | 1 | 2 | 3+ | exact | worst | limit |
|---|---|---|---|---|---|---|---|---|
| Fajr | 0 | 22 | 16 | 0 | 0 | 57.9 % | 1 | 3 |
| sunrise | 0 | 7 | 1 | 23 | 7 | 18.4 % | 3 | 3 |
| Dhuhr | 0 | 9 | 29 | 0 | 0 | 23.7 % | 1 | 3 |
| Asr | 0 | 20 | 18 | 0 | 0 | 52.6 % | 1 | 3 |
| Maghrib | 0 | 32 | 6 | 0 | 0 | 84.2 % | 1 | 3 |
| Isha | 0 | 1 | 30 | 7 | 0 | 2.6 % | 2 | 3 |
| end of eating | 0 | 0 | 16 | 22 | 0 | 0.0 % | 2 | 3 |
| imsak | 0 | 0 | 8 | 22 | 0 | 0.0 % | 2 | 3 |

| held out, 30 place-days | early / late end | 0 | 1 | 2 | 3+ | exact | worst | limit |
|---|---|---|---|---|---|---|---|---|
| Fajr | 0 | 22 | 8 | 0 | 0 | 73.3 % | 1 | 3 |
| sunrise | 0 | 0 | 0 | 23 | 7 | 0.0 % | 3 | 3 |
| Dhuhr | 0 | 9 | 21 | 0 | 0 | 30.0 % | 1 | 3 |
| Asr | 0 | 15 | 15 | 0 | 0 | 50.0 % | 1 | 3 |
| Maghrib | 0 | 24 | 6 | 0 | 0 | 80.0 % | 1 | 3 |
| Isha | 0 | 0 | 23 | 7 | 0 | 0.0 % | 2 | 3 |
| end of eating | 0 | 0 | 8 | 22 | 0 | 0.0 % | 2 | 3 |
| imsak | 0 | 0 | 8 | 22 | 0 | 0.0 % | 2 | 3 |

**Not proven.** Any season but Ramadan (hence D, and the entry claims nothing: it is not marked
measured); Nouadhibou's sunrise.

## Sudan — `sd.ministry`, class D

**Evidence.** One table: Khartoum State's imsakiya for Ramadan 1443 (2022, the Fiqh Academy's), at the
research's Khartoum point. Its first column, labelled imsak, is the Fajr and when the fast begins
(`F+E`); no sunrise.

**Method.** Fajr 18.3° (the 2022 table), Isha 18.0°, Dhuhr + 131 s (fitted; the table prints about
two minutes after the transit), standard Asr; the end of eating at the 19.5° dawn; no imsak (the
Academy calls one unfounded). Ruling R74 (28 Sep 2026 completion): on the entry's own Ramadan dates,
Isha is also never earlier than Maghrib + 90 min (`RamadanRule.ishaFloorMinutesAfterMaghrib`), a
floor never a replacement, so the 18.0° angle still wins where it falls later.

**Split.** Fit, nothing held out. The fit asked for less than the method itself on Fajr (−9), Asr
(−46), Isha (−28) and Maghrib (−36: the imsakiya prints its iftar about half a minute before the
standard sunset); each is kept at the plain safe 30 s, so Maghrib is never before the sunset.

| all rows, 30 place-days | early / late end | 0 | 1 | 2 | 3+ | exact | worst | limit |
|---|---|---|---|---|---|---|---|---|
| Fajr | 0 | 6 | 19 | 5 | 0 | 20.0 % | 2 | 3 |
| Dhuhr | 0 | 26 | 4 | 0 | 0 | 86.7 % | 1 | 3 |
| Asr | 0 | 0 | 18 | 12 | 0 | 0.0 % | 2 | 3 |
| Maghrib | 0 | 0 | 24 | 6 | 0 | 0.0 % | 2 | 3 |
| Isha (post-R74) | 0 | 0 | 0 | 0 | 30 | 0.0 % | 20 | 20 |
| end of eating | 0 | 0 | 0 | 0 | 30 | 0.0 % | 7 | 7 |

Isha's own worst moved from 2 min (the plain 18.0° angle, still what every non-Ramadan day and
every other registry method shows) to 20 min on this table's 30 Ramadan 1443 days, because every one
of them is now bound by the Maghrib + 90 floor (ruling R74 below) instead of the angle alone: an
intended, recorded widening, not a regression.

**Recorded exception (entry, end of eating, 7 min).** It rests on news reports only (LOW): reports of
Sudan's 2026 imsakiya put its dawn near 19.4°, earlier than this table's 18.3°, and no 2026 table was
found. Until one is held, the fast begins at the 19.5° dawn, up to 7 min before the 2022 table's
Fajr: "when unsure, earlier for ends".

**Recorded exception (entry, Isha, 20 min, ruling R74).** A 2026 Ramadan imsakiya circulating under
the Fiqh Academy's name (`nile/khartoum.txt`, below) puts Isha at Maghrib + 90, contradicting this
verified 2022 table's 18.0°-angle Isha by 13–19 min on the research's projected 2024–2030 dates. Since
that 2026 file is genuinely circulating under the Academy's name (a second, independent
republication corroborates its day-1 Khartoum figures) but its authorship is still unconfirmed, the
engine now takes the later of the two, Ramadan dates only: `IshaRule.Angle`'s existing angle, floored
at Maghrib + 90 (`method.ramadan.ishaFloorMinutesAfterMaghrib`). This table's own Ramadan 1443 dates,
whose Isha fits the angle exactly, are therefore up to 20 min "late" against that floor — the honest
cost of "when unsure, the later start", never an early Isha. No other registry method carries this
floor (a regression test asserts it).

**Not used, still.** `nile/khartoum.txt` (eight days of Ramadan 2026 from a research copy labelled
"Khartoum Fiqh Academy Ramadan 2026") is still not read as a gate row: it gives no source, and it
looks like an Umm al-Qura-style calculator's output rather than the Academy's table (columns named
noon and sunset, Fajr near 18.6°, Isha = Maghrib + 90 on all eight days without Umm al-Qura's Ramadan
120). New evidence (ruling R74 research): a Sudanese news aggregator's Ramadan 2026 imsakiya article,
attributed to "مجمع الفقه الإسلامي" (the Fiqh Academy), gives the identical day-1 Khartoum Fajr and
Isha — so this table is genuinely circulating under the Academy's name in 2026, not an isolated
scrape, even though who actually computed it is still unresolved. That is enough to back a
never-early safety floor (above); it is not enough to promote the file to a gate row or to change the
base method.

**Not proven.** Any season but Ramadan; any place but Khartoum (the imsakiya gives other cities as
constant offsets from Khartoum; Taqwa computes each at its own point, not checked). Ruling R74 does
not close either gap: it only narrows Ramadan Isha, at Khartoum's own point, never asserting Maghrib
+ 90 as the Academy's actual method.

## The end of eating, audited (3 Oct 2026, before Ramadan 1448)

Every Maghreb entry already checks its end of eating: Fajr is `F+E` in `tn-inm`, `dz-marw`,
`ma-habous`, `mr-ministry` and `sd-ministry`, and in Libya's western rows with its imsak (`Im`). The
audit adds Libya's east and south: the owner's two adhan rows (Benghazi, Sabha, 27 Sep 2026) are now
`F+E M`, the adhan the fast begins at there (ruling R73). Never after it (worst 1 min before, within
class D's 3; `ly.awqaf` now 54 end-of-eating days). The national rows in the east and south still
leave their first column out: checked once outside the gate, the end of eating there (the 19.5°
dawn the mosques call) comes more than 3 min before the national table's Fajr on all 42 city-days
held (5–6 min in the report's samples), never after it: over class D's limit by design, since that
table is not what the mosques call.
No margin changed.

## Recorded exceptions

| entry / unit | events | minutes | reason |
|---|---|---|---|
| `ma.habous` (beyond the fitted cities) | sunrise | 7 | Elevation unknown: a national margin covering Imilchil (~2,150 m); at a low town up to 7 min before Habous's sunrise. |
| `ma.habous` (beyond the fitted cities) | Maghrib | 6 | The same margin: at a low town up to 6 min after Habous's Maghrib. |
| `ma.habous`, each fitted city | sunrise, Maghrib | 2 | The city's own table gives its elevation: the national margin does not apply there (the class B limit). |
| `dz.marw`, Tamanrasset | Asr | 6 | Its table is Adrar's plus one difference per half month, which does not follow Tamanrasset's own Asr through the half month; never before it, the seat's Asr is up to 6 min after it. |
| `sd.ministry` | end of eating | 7 | News reports only (LOW) put the 2026 dawn near 19.4°; the fast begins at the 19.5° dawn, up to 7 min before the one table held. |
| `sd.ministry` | Isha | 20 | Ruling R74: a 2026 imsakiya circulating under the Fiqh Academy's name puts Isha at Maghrib + 90; on the entry's Ramadan dates only, Isha never earlier than that, up to 20 min after the one table's 18.0° angle. |

## The new readers

- `AwqafWidgetFormat` (`awqaf-widget`): Tripoli's widget captures, skipping the four stale ones.
- `HabousLiveFormat` (`habous-live`) and `HabousArchiveFormat` (`habous-archive`): one Habous file's
  live month as printed, and its Wayback pages read into UTC with each page's own clock, skipping the
  misdated lines.

## Data left out, and why

- Libya: `off-ifta-tripoli.txt` and `nile/tripoli_all.txt` repeat Tripoli's days (the latter adds
  24 Sep 2026 from a copy of the widget); `off-darifta-cairo-sep.txt`, filed LY-IFTA by its name, is
  Egypt's Dar al-Ifta for Cairo (its Dhuhr is Cairo's, not Tripoli's).
- Algeria: the OCR stages and four copies of the same calendar days.
- Tunisia: five copies of Tunis and Tala days.
- Morocco: six copies of the round files' days.
- Mauritania: the files whose Maghrib column is the printed sunset, three copies, and a reporter's two
  Fajr times derived from an imsak.
- Sudan: the unsourced 2026 file (above).
