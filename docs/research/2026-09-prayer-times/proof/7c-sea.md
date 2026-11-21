# 7c — Singapore, Brunei, Malaysia, Indonesia

Gate: `./gradlew -p tools/timetables gate -PgateGroup=sg-muis,bn-mora,my-jakim,id-kemenag` — green,
0 broken. Stamps: `tools/timetables/official/stamps/{sg.muis,bn.mora,my.jakim,id.kemenag}.json`.

## sg.muis (MUIS, Singapore)

Class B (ruling R57), with one recorded exception for Asr alone (R41). Fitted on 2024–2025 (the
`fit` rows of `sg-muis.tsv`), safety 5 s, checked on the whole of 2024–2026 (1096 place-days,
1 place: the fixed point 1.28967 N 103.85007 E). `sunModel` stays `CLASSIC_NOON`; `asrModel` stays
the default `EXACT_MOMENT` (see below).

| event | margin (s) | fit → held-out worst | over the limit (class/exception) |
|---|---|---|---|
| Fajr | +16 | 1 → 1 min | 0/0 |
| Sunrise | +60 | 1 → 1 min early | 0/0 |
| Dhuhr | +80 | 1 → 1 min | 0/0 |
| Asr (Standard) | +20 | 2 → 3 min | 0/0 (exception: 3 min) |
| Maghrib | +17 | 1 → 2 min | 0/0 |
| Isha | +17 | 1 → 2 min | 0/0 |
| End of eating | +59 | 2 → 2 min early | 0/0 |

Full 2024–2026 (1096 place-days): early 0 everywhere, late ends 0 everywhere. Late-minute buckets
(0/1/2/3+): Fajr 690/400/6/0, sunrise 732/363/1/0, Dhuhr 702/392/2/0, Asr 588/404/98/6, Maghrib
712/379/5/0, Isha 691/402/3/0, end of eating 688/402/6/0. Held-out 2026 alone (365 days) is the same
shape at roughly a third of the counts.

**Class B, not A with exceptions (ruling R57):** an exception is for a specific, reasoned case, not
a way to hold on to a class. Every one of MUIS's seven events needs a second minute of lateness
(starts) or earliness (sunrise, end of eating) on a handful of the 1096 days (Fajr 6, sunrise 1,
Dhuhr 2, Maghrib 5, Isha 3, end of eating 6) — literally class B's own margin, on every event, not
one or two. So the entry is class B outright.

**Exception (R41), recorded on the entry, not the tsv:**
- `asrStandard`, up to 3 min — "MUIS's own Standard Asr is not reproduced to the minute; a further
  minute of drift near the equinoxes past class B's own margin." 6 of 1096 days need a third minute,
  one past class B's own 2; they cluster near the March and September equinoxes (e.g.
  2024-02-27..03-04), where the sun's declination changes fastest. This one stays a scoped
  exception, not a further class change, since it is Asr alone and a specific, dated cause.

### MUIS was red: the Asr investigation (the brief's "must fix")

Before any change, the shipped 2024–2026 gate showed Fajr 7, sunrise 1, Dhuhr 2, Asr 133 (up to
3 min), Maghrib 5, Isha 7 and end of eating 385 over the class A limit. Two separate problems:

1. **`endOfEatingMarginSeconds` was never fitted** (it shipped at the default, 0). `Fitter.fit`
   found +59 s; applying it took end of eating from 385 over to 6, within class B's own 2 min (the
   reason the entry is class B — see above). This alone accounts for almost all of the "must fix"
   table's raw count.
2. **Asr's spread is real, not a bug in the shipped margin.** `Fitter.fit("sg.muis")` on 2024–2025
   gives Asr +20 (vs the shipped +31); testing that on the 2026 holdout still shows 48 of 365 days
   over class A's 1 min, worst 2 min on the holdout alone (3 min over the full 2024–2026 table) —
   matching the brief's own prediction exactly ("even refitted, the 2026 holdout has 48 Asr days
   over the limit").

   Investigated by trying every `AsrModel` this engine offers against the same fit rows (2024–2025),
   each retested on the 2026 holdout with its own fitted margin:

   | AsrModel | fitted margin | holdout over (of 365) | holdout worst |
   |---|---|---|---|
   | `EXACT_MOMENT` (default, unchanged) | +20 | 48 | 2 min |
   | `NOON_SHADOW` | +21 | 55 | 2 min |
   | `UTC12_ONE_SHOT` | +31 | 54 | 2 min |
   | `DAILY_0H_UT` | +21 | 59 | 3 min |

   `EXACT_MOMENT`, the model already in use, is the *best* of the four — none of the alternatives
   (a fixed declination read at noon, at UTC noon, or at 0h UT rather than iterated to the Asr
   moment) narrows the spread. So this is not "MUIS uses a different Asr reference than this
   engine's default": every reference this engine can compute leaves the same few days 2–3 min
   apart from MUIS's own printed Asr, which is more consistent with MUIS's own rounding or reference
   point drifting slightly year to year (profiles-tested.md's own words: "the residual drifts from
   year to year. Reference point not documented"). No engine change is proposed; the model stays
   `EXACT_MOMENT` and the lateness is absorbed by the margin plus the recorded exception above.

## bn.mora (MORA, Brunei)

Brunei has no `BN-*` row in `MANIFEST.tsv`, but the archive does hold three captures of the
ministry's own SharePoint list for Bandar Seri Begawan under the generic `SOUTHEAST-ASIA` tag
(`archive/tables/moral-auth-world/sea/off-bn-2026*.txt`) — real data, so it is checked: gate file
`bn-mora.tsv`, entry `bn.mora/brunei-muara` (Brunei-Muara's own minutes are all zero, so BSB's table
checks that district directly).

- Fit: `off-bn-2026-clean.txt` (the full year, 9 duplicate/typo rows dropped — the ministry's own
  list, e.g. one row would put Fajr about 9 min early, matching Muis.kt's long-standing note).
- Test: `off-bn-2026-09.txt`, September recaptured independently on the same day; it differs from
  the full-year file's own September rows by up to a minute on a few days (site-side noise), which
  is exactly why it is a useful (if modest) held-out check rather than folded into the fit.
- The plain safe-default margins (`SAFE_START` = +30 s, `SAFE_SUNRISE` = −30 s, already shipped)
  are never early on all 355 fit days and the 30 held-out days (0 early, 0 late ends, 0 over the
  class D limit of 3): worst 2 min. `Fitter.fit("bn.mora/brunei-muara")` finds the tightest safe
  margins run from −21 s (Fajr) to +25 s (sunrise) — well inside the shipped ±30 s, so nothing
  needed changing.
- `bn.mora`'s Brunei-Muara unit is now `measured = true`, `entryClass = B` (worst 2 min, matches the
  class definition). Belait, Tutong and Temburong keep their documented minutes
  (mora.gov.bn: Belait +3, Tutong +1) unchecked against any table of their own — still
  `measured = false`, class D. The edge (+240 s beyond the 4 known districts) is unchanged.
- The gate's "held out" count reads as 0 in the report because the fit and test files share the
  same September calendar dates (two captures of one year, not two years); every day in both files
  is still individually checked (385 event-observations tallied per event above 355 place-days), and
  the September file's own rows found 0 broken.

## my.jakim (JAKIM, Malaysia)

Class B, one recorded exception (PLS01's sunrise). All 13 fitted zones refitted with
`Fitter.fit("my.jakim/<zone>")`: fit on each zone's 2025 mirror table
(`api.waktusolat.app`), test on its own 2026 e-solat table — a genuine forward split, unlike
profiles-tested.md's "fit one year, test the other, both directions" (which let the held-out year
help choose the pad). SBH06 stays excluded (its 2026 table has internal breaks, unchanged from
Task 5). Safety 5 s throughout.

Margins are fitted per zone over the **union** of both years (2025+2026, 730 days each): a
single-year fit sometimes falls short on the other year by a handful of seconds (see JHR02, SGR01
below), and the brief's own convention ("if held-out rows are [not safe], widen the margin by the
observed excess plus 5 s and run again") is exactly the union fit. Final per-zone margins are in
`JakimZones.kt`.

Full 2025–2026 (9489 place-days, 13 zones — one PLS01 fit day short of 9490: see the data-error note
below): 0 early, 0 late ends everywhere. Worst late by zone: 1
min (JHR01, KTN01, SGR01, WLY01, WLY02), 2 min (JHR02, PHG06, PNG01, SBH01, SBH07, SWK08), 3 min
(PLS01, sunrise only, exception below).

**Two bugs found and fixed while refitting (not margin issues):**
- **SGR01's sunrise margin was copied from WLY01 by mistake** in an intermediate draft (−28 instead
  of its own fitted −111): produced late ends (sunrise shown after the official time) on about 90 %
  of SGR01's 730 days. Fixed by using SGR01's own fitted value; SGR01 is now clean (worst 1 min).
- **A single-row data error** in `off-jakim-PLS01-2025-mirror.txt`: 2025-03-01's Fajr is printed
  12 hours off (an AM/PM label slip — the surrounding days agree closely with 2026's own 1 March).
  The archive row is left exactly as captured (JAKIM is a restricted table, never edited); the fit
  row in `my-jakim.tsv` instead points at a local `off-jakim-PLS01-2025-mirror-clean.txt`, that one
  row filtered out (archive only, never committed) — the same pattern `bn-mora.tsv` uses for MORA's
  own duplicate/typo rows.

**Kelantan's Isha is now a genuine 17° angle** (`JakimZones.Zone.ishaAngleDeg`), not the 18° base
with a negative margin standing in for the angle difference. The previous "−149 s" margin worked
only by coincidence of the specific years fitted; with the real angle, KTN01's Isha margin is a
small, ordinary +77 s, fitted the same way as every other event.

**PHG06 (Cameron Highlands, −2.5° horizon) — a genuine invariant conflict, not a margin choice.**
The tightest safe Maghrib margin over 2025–2026 is about −67 s. Applying it produces 49 "out of
order" days on the 2026 holdout: the engine's `maghrib ≥ sunset` invariant compares the margined,
ceiling-rounded Maghrib against the unmargined, floor-rounded sunset at the *same* horizon, and a
margin that negative ceils below the floor on days where the raw crossing's fractional second is
unlucky. (Confirmed with a throwaway JUnit test dumping the day at PHG06 across the flagged dates,
deleted before finishing.) Since any margin *less* negative than the fitted floor is equally safe
against MUIS^H^H JAKIM's own printed table (a larger margin only pushes Maghrib later, never
earlier), PHG06 keeps a small **positive** margin (+30 s) instead of the fitted extreme: never early,
never repairs, and stays inside class B's 2 min (worst 2 min, 0 broken).

**Exception: PLS01 (Kangar) sunrise, up to 3 min** — "Kangar's printed sunrise spreads a further
minute beyond a plain classic-noon model across the seasons." 213 of PLS01's 730 days need the third
minute (about 29 %), clearly a real seasonal spread rather than noise, and no single margin removes
it without moving every other PLS01 day later. Every other event at PLS01 stays inside class B's own
2 min.

**Kept unchanged, per the brief:** WLY01/SGR01's two Selangor start points; the outside-zone edge
(verified in an earlier task at Kapit, Sibu, Mukah, Gua Musang, Jeli, Kuala Krai and Dabong — no
official tables for those towns exist locally to re-verify, so the existing derived edge margins are
left as they were).

## id.kemenag (Kemenag, Indonesia) and id.muhammadiyah

Class B. All 13 fitted kab/kota now carry their own margins (`Fitter.fit("id.kemenag/<id>")`),
replacing the single shared national margin. Only one year (2026) has ever been captured for
Kemenag (myQuran's republication), so there is no year-over-year split to use; each place's own
365-day file is split by date instead — fit Jan–Sep (273 days), test Oct–Dec (92 days), forward in
time. Safety 5 s; final margins fitted over the **union** of both halves (365 days), for the same
reason as JAKIM above.

Full 2026 (4745 place-days, 13 kab/kota): 0 early, 0 late ends everywhere, 0 over the class B limit.
Worst late: 1 min at every one of the 13 places. Held-out Oct–Dec alone (1196 place-days): the same,
0 over.

**The gate was already almost green with the single shared margin** (fajr+28/sunrise−3/dhuhr+78/
asr+38/maghrib+31/isha+29, the 2025-era shipping figures): only 2 of 4745×6 checks failed — an
early Fajr at Banda Aceh on 2026-03-21 (the 21–22 March anomaly the research already flagged) and a
late sunrise at Bukittinggi on 2026-02-08. Fitting per kab/kota removes both directly.

**Jakarta's sunrise had 0 s of slack (the brief's question), now answered:** Kota Jakarta's own
fitted sunrise margin is −9 s; the shared national margin needed −10 s to cover Bukittinggi (the
tightest place). Under one shared margin, Jakarta rode 1 s from its own edge — safe on every day
actually checked, but with no room for next year's table to drift even slightly before Jakarta
specifically would need a wider margin again. Per-kab/kota margins remove the squeeze structurally:
Jakarta now carries exactly its own −9 s, independent of Bukittinggi or any other place.

Kota Jakarta's own core (≈15 km, the checked −1° horizon) and its wider Jabodetabek reach unit
(≈55 km, R40's class-B reach, −2° horizon, `measured = false`, no "at most" claimed) were unchanged
from Task 5/6 (ruling R46) in this task; the monitor round's fix rounds cut the core to DKI's own
boundary (8 km, ruling R103) and closed the reach unit (ruling R106) — see the monitor-round
subsection below.

Imsak (Subuh − 10) is unchanged: it already reads from the authority's own dawn as an end
(`dawnEnd`, R14/R27), not the rounded start, so refitting the start margins does not move it off
that rule.

`id.muhammadiyah` (18° Subuh, otherwise Kemenag's own units and margins) is unchanged: no official
Muhammadiyah table exists locally to check it against, so it stays class D_AUTHORITY, a named
timetable per spec §9.

### Monitor round (29 Sep 2026): five more kota, and why the edge ran 6–7 min wide at the horizon

The weekly monitor's first real run (28 Sep 2026) fetched Kemenag's Sep–Oct 2026 tables for five
kota not held before — Kota Medan, Palembang, Semarang, Surabaya and Yogyakarta (61 days each, the
same myQuran republication) — and found the engine never early and never late at an end there, but
with sunrise 6–7 min before Kemenag's and Maghrib 6–7 min after on every day, the other four events
within 3. It also re-fetched the 13 held kab/kota: identical on all 61 overlapping days.

**Cause.** Beyond the fitted units the entry's edge computes sunrise and Maghrib at the deepest
plausible horizon, −2° (the highland class, ruling R46), with 2 min more on every event for a
capital up to half a degree away. All five kota are lowland: the printed sunrise and Maghrib of each
sit exactly where a −1° horizon puts them (the residual against the engine's own astronomy at −1°,
with Kemenag's 2 min ihtiyat, spans one rounding minute at every one of the five, as at the 13) and
about 4 min from where −2° puts them. So the 6–7 min are about 4 min of horizon, 2 min of unknown
capital point, and the rest the edge's margins and the rounding. Nothing in the edge is unjustified
given what it does not know: a kab/kota's horizon class cannot be told without its own table (the 18
now held split 11 lowland / 7 highland, and elevation does not predict the class: Malang at about
450 m is −1°, Bogor at about 260 m is −2°), so the edge keeps −2° and the five become fitted units,
as the 13 are — the brief's "if not" branch. Kemenag's horizon rule, as far as its tables show it:
sunrise and Maghrib at a −1° or −2° sun altitude by kab/kota class (never the plain −0.833°), then
the 2 min ihtiyat (−2 on syuruk), then the minute; its own elevation table is not published.

**The five units** (fix round 1, ruling R104) carry the app's own GeoNames points like the 13
(Kemenag's reference points are not published; myQuran's JSON carries none) and the −1° horizon, and
are fitted exactly as the 13 were: Kemenag's whole 2026 for each was fetched on 29 Sep 2026 from the
same endpoint as the 13's files (its Sep–Oct rows equal the monitor's capture on every day), split
Jan–Sep fit / Oct–Dec test, `Fitter.fit("id.kemenag/<id>")` at safety 5 s. The Jan–Sep fit alone
holds Oct–Dec at all five (0 early, 0 late ends, worst 1 min); the registry's margins are fitted over
both halves, as the 13's are (the two differ by at most 6 s on any event: Yogyakarta's Fajr, decided
by a November day). Margins (seconds, F/S/D/A/M/I): Medan +4/−14/+62/+27/+4/+4; Palembang
+8/−18/+70/+16/+23/+19; Semarang +3/−14/+63/+12/+16/+17; Surabaya +22/−8/+77/+36/+36/+33 (its printed
times run about 10 s later than the engine's at its GeoNames point, a point offset its own margins
absorb); Yogyakarta +9/−10/+80/+18/+34/+27. A first version of this round fitted the five on the
monitor's two months plus an allowance drawn from the 13; the review showed the allowance was a
sample maximum, not a bound (a leave-one-out test failed at Banda Aceh's Fajr on 21 March), and the
whole year was published, so it was replaced by the year.

**Every unit's reach (rulings R103 and R113).** Kemenag prints one table per kabupaten/kota and a user
follows their own, so a unit's circle stays inside its own kab/kota: its radius is the distance from
its point to the nearest boundary of a kabupaten/kota Kemenag prints separately, measured on
OpenStreetMap's administrative boundaries (admin_level 5, Overpass, 29 Sep 2026; point-to-segment
distances over each neighbour's rings, in the engine's own spherical distance), less a 0.5 km buffer
for those boundaries' accuracy, rounded down to 0.1 km and never below 0.1 km — so that a unit's own
point, as the app rounds a location to three decimals (at most 0.08 km), always stays inside. Where a
coastline is nearer than any neighbour (Makassar 2.6 km, Jayapura 0.1, Semarang 4.2, Surabaya 4.9)
the sea bounds no table and the circle may cross it. Radii, km (the nearest neighbour and its
distance): Kota Jakarta 7.8 (Bekasi 8.365), Banda Aceh 0.9 (Aceh Besar 1.438), Makassar 4.0 (Gowa
4.554), Denpasar 3.3 (Badung 3.872), Jayapura 8.9 (Kab. Jayapura 9.464), Malang 2.6 (Kab. Malang
3.137), Bandung 3.8 (Kab. Bandung 4.348), Jayawijaya 13.3 (Yahukimo 13.895), Batu 3.4 (Kab. Malang
3.988), Bukittinggi 0.1 (Agam 0.687: the city entry and little else), Bogor 1.3 (Kab. Bogor 1.822),
Garut 3.8 (Kab. Tasikmalaya 4.332), Wonosobo 9.9 (Banjarnegara 10.475), Medan 3.3 (Deli Serdang
3.873), Palembang 0.6 (Banyuasin 1.189), Semarang 7.3 (Demak 7.841), Surabaya 8.0 (Bangkalan 8.552,
across the strait), Yogyakarta 1.3 (Bantul 1.819). Beyond the circle the edge applies as before these
units existed. The app's own places in the neighbouring kabupaten (Kasihan, Gamping Lor, Melati,
Deli Tua, Mranggen, Kamal, Paseh, Margahayukencana, Dalung, Kuta, Batubulan) and Sunggal (in Kota
Medan on OSM, beyond its circle) resolve to the edge, pinned by RegistryTest; every unit's own point,
rounded as the app rounds it and resolved through the app's engine, resolves to its unit, and so does
the app's city entry for each of the 18.

**Ruling R106 closes R46's reach unit.** Beyond Kota Jakarta's core, R46 let Jakarta's point ride
as the fixed point out to 55 km with the deepest horizon but without the edge's allowance for an
unknown seat, so a start could fall seconds before a neighbouring kabupaten's own table where its
seat lies further west (Kab. Tangerang's Tigaraksa, Kota Tangerang). Of the ruling's two closures —
the reach unit taking the edge's allowance, or stopping at DKI's boundary with the edge beyond — the
second keeps at least as much accuracy everywhere (the fixed point only added lateness east of
Jakarta and bought no safety the allowance does not give), so the reach unit is gone: beyond the
8 km core the edge applies. Shown with the reviewer's model (each kabupaten/kota's table as the pure
method at its seat, at −1° and at −2°, on every day of 2026) at the app's 25 places in the former
reach: 24 resolve to the edge (Kota Bogor's own point keeps its unit) and against their own
kabupaten/kota's table — Kota Bekasi, Kab. Bekasi (Cikarang), Depok, Kab. Bogor (Cibinong), Kota
Tangerang, Tangerang Selatan (Ciputat), Kab. Tangerang (Tigaraksa), Karawang, and for Utan (DKI)
Kota Jakarta's real table — 0 early starts and 0 late ends; starts 1–4 min after the table, sunrise
1–3 min before a −2° table (5–8 before a −1° one), Maghrib 1–4 (5–8) after. Cost against the closed
reach unit: 2 min later at each start, 2 min earlier at sunrise, at those 24 places. RegistryTest
pins all 24 to the edge and asserts the reach unit is gone.

Gate, `id-kemenag` group after the round: 36 rows, 18 places, 6,570 place-days (1,656 held out, 540
in Ramadan), 0 early, 0 late ends, 0 over the class-B limit, worst 1 min at every unit. Per place
before → after (worst minutes): each of the five had sunrise 7 (an end, early), Maghrib 7, the other
four events 3; now 1 on every event. The 13 are unchanged (worst 1).

**Class (spec §5).** The evidence now reads: method rebuilt; 18 kab/kota across Sumatra, Java, Bali,
Sulawesi and Papua; a year including Ramadan 1447 at every one of them; never early on held-out data
(each place's Jan–Sep fit holds its Oct–Dec); at most 1 min after Kemenag's at every unit — every
line of class A's row. What still holds it at B: the reference points are the app's, not Kemenag's
own (spec §5's "a reference point not yet verified"; the residual levels differ between places by up
to about 10 s, which the per-unit margins absorb but a verified point would remove), and only one
calendar year has ever been published (the held-out split is by date inside 2026, not a second year).
Proposed ruling: `id.kemenag` stays class B; its fitted units become class A once Kemenag's 2027
tables (the monitor fetches them from November 2026) hold at each with the same margins — 0 early,
0 late ends, worst 1 min — a real year-over-year holdout that also answers the point question. The
monitor's 28 Sep recaptures of the 13 are identical to the held files on every day, so they add no
rows.

### Round of 12 Nov 2026: 109 more kab/kota

430 of the app's 448 Indonesian places were on the edge (sunrise 6–7 min early and Maghrib 6–7 min late at a
lowland place). This round adds the largest kab/kota among them as units, the R104 way: each one's whole 2026, 2027
and 2028 fetched from myQuran's republication (`api.myquran.com/v2/sholat/jadwal/<id>/<yyyy>/<mm>`, the monitor's own
endpoint, one month a request, about a second apart, the place name checked on every month; 3,924 requests on
12 Nov 2026), kept in the archive under `archive/tables/round/kemenag-kabkota/`.

- **Which.** 57 kota with an app city at their point, from Kota Bekasi, Depok, Tangerang and Tangerang Selatan down to
  Kota Metro, and 52 regencies whose seat is an app city, largest first (Kab. Bogor at Cibinong, Kab. Bekasi at Cikarang,
  Kab. Bandung at Soreang, Kab. Malang at Kepanjen, Sidoarjo, Jember, Karawang, …). Not added: regencies whose seat is
  not an app city (Kab. Tangerang's Tigaraksa, Kab. Serang, Deli Serdang's Lubuk Pakam, Bandung Barat's Ngamprah,
  Brebes), and DKI Jakarta beyond Kota Jakarta's core: one circle per kab/kota reaches only those.
- **Point.** The app's own GeoNames point for the kota or the regency seat, as for the 18 (RegistryCitiesTest holds each
  unit to an app city entry at its point). Kemenag publishes no point and myQuran's JSON carries none; a table rounded
  to the minute cannot separate a point's longitude from a constant offset, so each unit's margins absorb its residual
  level (Kota Dumai's, about 20 s later than the rest, is the largest; Kota Depok's about 15 s).
- **Horizon.** Read from each table: a first fit at −1° whose sunrise and Maghrib margins land about 4.5 min (±270 s)
  out marks a highland kab/kota, refitted at −2°. Five are: Kota Cimahi, Kota Sukabumi, Kota Salatiga, Kab. Bandung and
  Kab. Kuningan. Kota Magelang (about 380 m) and Kab. Cianjur are lowland, which again shows that elevation does not
  tell the class.
- **Split.** First fitted on 2026 alone (safety 5 s) and held to 2027, a true held-out year: 108 of the 109 held every one
  of their 39,785 held-out days, worst 1 min. Kota Pontianak's Asr was a minute early on two days of March 2027: on the
  equator Asr's daily change turns as the noon sun crosses overhead, and 2026 alone did not show the 2027 days' step.
  So, as for the 18 since track T4, the registry's margins are fitted over 2026 and 2027 together and 2028, never fitted
  on, is held out.
- **Reach (R103/R113).** As for the 18: the distance from the point to the nearest other kab/kota on OpenStreetMap's
  admin_level-5 boundaries (Overpass `rel(around:30000,…)[boundary=administrative][admin_level=5]; out geom;`, one
  request a unit, 12 Nov 2026; point-to-segment distances in the engine's spherical distance), less 0.5 km, floored to
  0.1 km; a coast bounds nothing. The method reproduces the 18's radii it was checked on (Kota Jakarta 8.365 km to
  Kota Bekasi, Kota Bogor 1.822 km to Kab. Bogor). Radii run from 0.2 km (Kota Mojokerto) to 24.9 km (Kab. Sumenep), all
  well inside class B's late reach (about 55 km at these latitudes), so the user's own sun, which bounds each start
  inside a unit (R15), adds less than the class's limit even at a circle's edge.

Gate, `id-kemenag` group after the round: 399 rows, 127 places, 139,192 place-days (46,482 held out, 11,176 in Ramadan),
0 early, 0 late ends, 0 over the class-B limit. Worst after Kemenag's: a minute on every event except Asr (2 min on 3
days) and the end of eating (2 min, as at the 18). The round's own 109 units hold 119,464 place-days: Ramadan 1447
in their 2026 rows, Ramadan 1448 (Feb–Mar 2027) in their fitted 2027 rows, and Ramadan 1449 (Jan–Feb 2028) held out.

**Across each circle (the review of 12 Nov 2026).** The figures above are at each unit's point; the circles now reach
24.9 km, so every one of the 127 units' own tables is replayed across its circle as Umm al-Qura's and QMDB's are
(`generateReachRows`, `gate/id-kemenag-reach.tsv`: 8 bearings at 50 and 95 % of the radius, and each event's worst
point of a 392-point sweep; 2,032 grid points and 166 worst points, 6,903 rows, all held out; the monitor's recipes
read each new year there too). Whole gate after it: `id.kemenag — class B, late limit 2 (by exception: endOfEating 3
from unit 1626) · 2325 places · 2548200 place-days (2455490 held out, 204600 in Ramadan) · 7302 rows`; never early,
no end late, worst 2 on every event: near a circle's edge a point's minute becomes 2 (Fajr on 122 place-days, Asr on
2,072), which the stamps and About now say. The end of eating reached 3 once, on one day of December 2028, at Kab.
Sumenep's worst point (near the reach's eastern edge, its islands east of the seat), where the user's own dawn comes about a
minute before the seat's: an earlier end, never a start, recorded as Sumenep's own exception (3 min, end of eating
only, as Dubai's) rather than shrinking the circle, which would hand those islands back to the edge's 6–7 min.

Share of each kab/kota inside its circle (area, sampled on its own OSM polygon): about 15 % of the 109 kab/kota's
combined population (about 17 of 110 million, assuming an even density, so approximate). 139 app city entries now
resolve to a unit of their own instead of the edge. Most of each kab/kota stays on the edge: more circles per
kab/kota (the triage's multi-circle follow-up) are what would move the rest.

| kab/kota (myQuran id) | horizon | radius km (nearest other kab/kota, km) | share of its area inside |
|---|---|---|---|
| Kota Bekasi (1221) | −1° | 3.3 (Jakarta Timur, 3.898) | 16.1% |
| Kota Depok (1225) | −1° | 3.5 (Jakarta Selatan, 4.079) | 19.4% |
| Kota Tangerang (1107) | −1° | 3.3 (Kabupaten Tangerang, 3.859) | 19.6% |
| Kota Tangerang Selatan (1108) | −1° | 3.2 (Tangerang, 3.701) | 19.6% |
| Kota Batam (0506) | −1° | 17.3 (East Region, 17.854) | 40.4% |
| Kota Pekanbaru (0412) | −1° | 6.6 (Kampar, 7.176) | 21.6% |
| Kota Bandar Lampung (1014) | −1° | 4.2 (Pesawaran, 4.762) | 27.9% |
| Kota Padang (0314) | −1° | 14.0 (Pesisir Selatan, 14.553) | 43.7% |
| Kota Samarinda (2310) | −1° | 8.5 (Kutai Kartanegara, 9.084) | 31.8% |
| Kota Tasikmalaya (1227) | −1° | 2.1 (Ciamis, 2.669) | 7.7% |
| Kota Serang (1106) | −1° | 4.4 (Kab Serang, 4.923) | 22.4% |
| Kota Balikpapan (2308) | −1° | 5.5 (Penajam Paser Utara, 6.038) | 8.4% |
| Kota Pontianak (2013) | −1° | 3.4 (Kubu Raya, 3.942) | 30.0% |
| Kota Banjarmasin (2113) | −1° | 2.6 (Barito Kuala, 3.196) | 21.4% |
| Kota Jambi (0610) | −1° | 3.6 (Muaro Jambi, 4.189) | 23.9% |
| Kota Cimahi (1223) | −2° | 0.9 (Bandung Barat, 1.495) | 6.1% |
| Kota Surakarta (1434) | −1° | 2.0 (Sukoharjo, 2.579) | 27.3% |
| Kota Kupang (1922) | −1° | 3.3 (Kupang, 3.862) | 20.3% |
| Kota Cilegon (1105) | −1° | 2.4 (Kab Serang, 2.903) | 10.7% |
| Kota Manado (2914) | −1° | 2.7 (Minahasa, 3.215) | 12.5% |
| Kota Mataram (1810) | −1° | 1.7 (Lombok Barat, 2.264) | 15.0% |
| Kota Bengkulu (0710) | −1° | 4.5 (Bengkulu Tengah, 5.019) | 21.4% |
| Kota Palu (2813) | −1° | 1.2 (Sigi, 1.767) | 1.2% |
| Kota Sukabumi (1226) | −2° | 1.0 (Kab Sukabumi, 1.525) | 6.3% |
| Kota Kendari (2717) | −1° | 3.6 (Konawe, 4.178) | 13.9% |
| Kota Dumai (0411) | −1° | 4.1 (Bengkalis, 4.661) | 2.2% |
| Kota Ambon (3110) | −1° | 7.0 (Maluku Tengah, 7.552) | 33.9% |
| Kota Cirebon (1224) | −1° | 0.3 (Kabupaten Cirebon, 0.885) | 0.7% |
| Kota Pekalongan (1431) | −1° | 1.3 (Pekalongan, 1.804) | 11.7% |
| Kota Palangkaraya (2214) | −1° | 4.9 (Pulang Pisau, 5.421) | 2.8% |
| Kota Kediri (1632) | −1° | 1.1 (Kabupaten Kediri, 1.6) | 5.6% |
| Kota Tegal (1435) | −1° | 0.4 (Tegal, 0.945) | 1.3% |
| Kota Banjarbaru (2112) | −1° | 0.4 (Banjar, 0.967) | 0.2% |
| Kota Binjai (0226) | −1° | 1.6 (Kabupaten Langkat, 2.167) | 8.4% |
| Kota Pematangsiantar (0230) | −1° | 0.7 (Simalungun, 1.258) | 2.0% |
| Kota Tarakan (2405) | −1° | 7.2 (Bulungan, 7.735) | 45.1% |
| Kota Singkawang (2014) | −1° | 9.1 (Sambas, 9.681) | 35.3% |
| Kota Tanjung Pinang (0507) | −1° | 6.4 (Bintan, 6.935) | 54.6% |
| Kota Pangkal Pinang (0907) | −1° | 2.0 (Bangka Tengah, 2.575) | 11.9% |
| Kota Gorontalo (2506) | −1° | 1.5 (Bone Bolango, 2.087) | 9.8% |
| Kab. Bekasi (1203) | −1° | 8.7 (Karawang, 9.286) | 18.3% |
| Kab. Bogor (1204) | −1° | 2.3 (Depok, 2.893) | 0.6% |
| Kab. Bandung (1201) | −2° | 3.5 (Bandung Barat, 4.02) | 2.2% |
| Kab. Malang (1614) | −1° | 10.9 (Kota Malang, 11.4) | 10.6% |
| Kab. Jember (1607) | −1° | 12.5 (Bondowoso, 13.032) | 15.0% |
| Kab. Sukabumi (1216) | −1° | 16.5 (Lebak, 17.077) | 15.0% |
| Kab. Cianjur (1206) | −1° | 12.6 (Kab Sukabumi, 13.107) | 13.9% |
| Kab. Karawang (1210) | −1° | 5.7 (Kab Bekasi, 6.234) | 5.3% |
| Kab. Cirebon (1207) | −1° | 2.7 (Kuningan, 3.227) | 2.2% |
| Kab. Sidoarjo (1624) | −1° | 10.3 (Pasuruan, 10.883) | 46.5% |
| Kab. Cilacap (1407) | −1° | 17.6 (Banyumas, 18.16) | 20.6% |
| Kab. Tasikmalaya (1218) | −1° | 4.4 (Tasikmalaya, 4.936) | 2.2% |
| Kab. Indramayu (1209) | −1° | 24.0 (Kabupaten Cirebon, 24.524) | 44.6% |
| Kab. Banyumas (1402) | −1° | 8.0 (Purbalingga, 8.533) | 14.4% |
| Kab. Subang (1215) | −1° | 9.7 (Sumedang, 10.208) | 13.7% |
| Kab. Gresik (1606) | −1° | 3.3 (Bangkalan, 3.893) | 1.5% |
| Kab. Klaten (1414) | −1° | 9.0 (Gunungkidul, 9.588) | 35.8% |
| Kab. Jepara (1410) | −1° | 13.1 (Demak, 13.697) | 30.3% |
| Kab. Sleman (1504) | −1° | 5.1 (Kota Yogyakarta, 5.675) | 14.4% |
| Kab. Bantul (1501) | −1° | 5.6 (Kulonprogo, 6.163) | 19.5% |
| Kab. Semarang (1423) | −1° | 2.2 (Kendal, 2.706) | 1.5% |
| Kab. Kudus (1415) | −1° | 3.9 (Demak, 4.473) | 10.8% |
| Kab. Demak (1408) | −1° | 8.8 (Grobogan, 9.341) | 24.3% |
| Kab. Purwakarta (1214) | −1° | 8.1 (Karawang, 8.618) | 20.6% |
| Kab. Sumedang (1217) | −1° | 9.1 (Garut, 9.684) | 16.9% |
| Kab. Pasuruan (1620) | −1° | 3.2 (Sidoarjo, 3.792) | 2.2% |
| Kota Pasuruan (1636) | −1° | 1.4 (Pasuruan, 1.993) | 15.5% |
| Kota Probolinggo (1637) | −1° | 1.3 (Probolinggo, 1.882) | 9.2% |
| Kota Madiun (1633) | −1° | 1.0 (Madiun, 1.508) | 9.4% |
| Kota Blitar (1631) | −1° | 1.1 (Blitar, 1.665) | 11.4% |
| Kota Mojokerto (1635) | −1° | 0.2 (Mojokerto, 0.709) | 0.6% |
| Kota Magelang (1430) | −1° | 0.3 (Magelang, 0.852) | 1.6% |
| Kota Salatiga (1432) | −2° | 1.7 (Kabupaten Semarang, 2.267) | 17.2% |
| Kota Bitung (2912) | −1° | 3.2 (Minahasa Utara, 3.753) | 7.4% |
| Kota Lhokseumawe (0121) | −1° | 5.0 (Aceh Utara, 5.569) | 22.7% |
| Kota Ternate (3209) | −1° | 3.6 (Tidore Kepulauan, 4.134) | 13.5% |
| Kota Bontang (2309) | −1° | 4.4 (Kutai Timur, 4.967) | 27.9% |
| Kota Bau-Bau (2716) | −1° | 2.8 (Buton Tengah, 3.303) | 5.1% |
| Kota Parepare (2624) | −1° | 1.1 (Pinrang, 1.642) | 3.4% |
| Kota Palopo (2623) | −1° | 5.2 (Luwu, 5.741) | 19.8% |
| Kota Bima (1809) | −1° | 2.7 (Bima, 3.23) | 8.3% |
| Kota Metro (1015) | −1° | 1.2 (Lampung Tengah, 1.748) | 6.0% |
| Kab. Tegal (1426) | −1° | 6.8 (Brebes, 7.367) | 14.6% |
| Kab. Situbondo (1625) | −1° | 5.1 (Bondowoso, 5.63) | 4.8% |
| Kab. Banyuwangi (1602) | −1° | 9.4 (Jembrana, 9.95) | 5.0% |
| Kab. Jombang (1608) | −1° | 8.7 (Nganjuk, 9.23) | 20.8% |
| Kab. Tulungagung (1629) | −1° | 6.8 (Trenggalek, 7.3) | 12.5% |
| Kab. Pati (1417) | −1° | 7.5 (Kudus, 8.009) | 11.4% |
| Kab. Pemalang (1419) | −1° | 3.0 (Tegal, 3.559) | 2.4% |
| Kab. Boyolali (1405) | −1° | 3.5 (Klaten, 4.044) | 3.5% |
| Kab. Bojonegoro (1604) | −1° | 1.4 (Tuban, 1.927) | 0.3% |
| Kab. Lamongan (1610) | −1° | 6.0 (Gresik, 6.531) | 6.3% |
| Kab. Tuban (1628) | −1° | 11.1 (Lamongan, 11.618) | 12.0% |
| Kab. Kuningan (1211) | −2° | 9.5 (Majalengka, 10.079) | 24.1% |
| Kab. Majalengka (1212) | −1° | 4.2 (Sumedang, 4.718) | 4.3% |
| Kab. Ciamis (1205) | −1° | 2.2 (Tasikmalaya, 2.798) | 1.0% |
| Kab. Lebak (1101) | −1° | 4.9 (Kab Serang, 5.456) | 2.3% |
| Kab. Pandeglang (1102) | −1° | 2.8 (Lebak, 3.356) | 0.9% |
| Kab. Sumenep (1626) | −1° | 24.9 (Pamekasan, 25.403) | 56.0% |
| Kab. Pamekasan (1619) | −1° | 9.3 (Sampang, 9.851) | 32.5% |
| Kab. Bangkalan (1601) | −1° | 8.5 (Gresik, 9.076) | 11.1% |
| Kab. Nganjuk (1616) | −1° | 9.4 (Madiun, 9.909) | 21.6% |
| Kab. Ponorogo (1621) | −1° | 8.6 (Madiun, 9.116) | 16.1% |
| Kab. Lumajang (1611) | −1° | 13.8 (Jember, 14.376) | 33.4% |
| Kab. Bondowoso (1605) | −1° | 10.4 (Situbondo, 10.937) | 21.9% |
| Kab. Sragen (1424) | −1° | 8.4 (Karanganyar, 8.988) | 21.8% |
| Kab. Grobogan (1409) | −1° | 10.5 (Pati, 11.081) | 17.0% |
| Kab. Probolinggo (1622) | −1° | 16.9 (Kota Probolinggo, 17.433) | 28.5% |
| Kota Sorong (3413) | −1° | 3.9 (Sorong, 4.417) | 11.9% |

### Second round of 12 Nov 2026: 119 more kab/kota

After the first round 291 of the app's 448 Indonesian places were still on the edge. This round adds the largest
kab/kota among them whose kota or regency seat is itself an app city, the same way: each one's whole 2026, 2027 and
2028 from myQuran's republication (4,284 requests on 12 Nov 2026, the place name checked on every month), kept beside
the first round's under `archive/tables/round/kemenag-kabkota/`. The printed imsak is Subuh − 10 on all 130,424 days.

- **Which.** Ranked by population (Wikidata's P1082, or the English Wikipedia infobox where Wikidata has none), with
  each regency's seat from Indonesian Wikipedia's *Daftar kabupaten di Indonesia*: 18 kota (Lubuklinggau, Prabumulih,
  Langsa, Tebing Tinggi, Gunungsitoli, Pagar Alam, Payakumbuh, Kotamobagu, Sungai Penuh, Tomohon, Sibolga, Pariaman,
  Tual, Subulussalam, Solok, Padangpanjang, Sabang, Padangsidempuan) and 101 regencies at their seats, from Kab. Lombok
  Timur (Selong), Asahan (Kisaran), Lombok Tengah (Praya) and Langkat (Stabat) down to Manokwari Selatan (Ransiki).
  Eight of the regencies are at the app's entry for the seat under another name: Bireuen (Bireun), Maros (Maros for
  Turikale), Kotawaringin Barat (Pangkalanbuun), Karimun (Tanjung Balai), Tabalong (Tanjung), Toli-Toli (Toli-Toli for
  Baolan), Majene (Majene for Banggae) and Pulau Morotai (Daruba for Morotai Selatan); Kab. Rembang's is the app's
  "Rembangan", GeoNames' point for Rembang town. Each point was checked to lie inside its own kab/kota's OSM polygon.
- **Not added.** Kab. Kubu Raya (the app's "Sungai Raya" is the one in Bengkayang), Sanggau, Pasaman Barat, Berau and
  Tapin (no app city at the seat; a name match found a different town), Kota Sofifi (myQuran lists it twice, 3211 and
  3212, and the town lies inside Kota Tidore Kepulauan's boundary, which prints its own table), Kab. Mimika (the app's
  Timika is about 25 km from the seat's coordinates) and Raja Ampat (Waisai about 40 km off): no clean point. And the
  regencies whose seat is not an app city: Kab. Tangerang (Tigaraksa), Deli Serdang (Lubuk Pakam), Brebes, Bandung
  Barat (Ngamprah; the app's Padalarang is 2.7 km away), Lampung Tengah, Kebumen, Serang (Ciruas), Magelang (Mungkid),
  Wonogiri, Kendal, Pekalongan (Kajen), Sukoharjo, Karanganyar, Simalungun, Purworejo, Kutai Kartanegara, Serdang
  Bedagai, Ketapang, Muara Enim, Bengkalis, Bima (Woha), Kupang (Oelamasi), Tapanuli Tengah, Takalar, Halmahera
  Selatan, Tanjung Jabung Timur, Penajam Paser Utara, Pidie Jaya, Nias Utara and Sorong (Aimas).
- **Horizon.** Read from each table as before: sixteen are highland (−2°): Kab. Karo, Tapanuli Utara, Dairi, Samosir,
  Rejang Lebong, Toraja Utara, Puncak Jaya, Lanny Jaya, Lembata, Manggarai, Timor Tengah Selatan, Minahasa and Kota
  Pagar Alam, Sungai Penuh, Tomohon and Padangpanjang.
- **Papua's mountains.** Kab. Puncak Jaya (Mulia) and Lanny Jaya (Tiom) print a Subuh about 4 min before the 20°
  dawn at their points and an Isya about 4.5 min after the 18° dusk, on top of the highland horizon: the fitted Fajr
  and Isha margins (−230 and +276 s, −237 and +270 s) take both and hold 2028 at worst 1. The end of eating follows
  the dawn, not the Fajr margin, so it ran up to a minute after Kemenag's Subuh on 716 days; it now has its own margin
  (`Kabkota.endOfEating`, −215 and −220 s: the latest that holds every 2026 and 2027 day, less 5 s), never after the
  Subuh on any day, 2028 included, and at most a minute before it.
- **Split.** Fitted on 2026 and 2027 together (safety 5 s), 2028 held out: never early on any of the round's
  130,424 place-days; 2028 at most a minute after Kemenag's on every event but Asr (2).
- **Reach (R103/R113).** As before, from 0.1 km (Kota Sibolga, Solok, Padangpanjang) up. Eighteen points have no other
  kab/kota within the Overpass query's 30 km (Sambas, Nias Selatan, Manggarai Barat, Sumba Timur, Merauke, Alor,
  Halmahera Utara, Nabire, Biak Numfor, Kepulauan Sangihe, Bulungan, Kepulauan Sula, Belitung Timur, Asmat, Kepulauan
  Aru, Fakfak, Teluk Bintuni, Sumbawa): their radius is the query's own reach less 0.5 km, 29.5 km, which stays inside
  their own kab/kota (a coast bounds nothing, so two circles may share open sea: Halmahera Utara's and Pulau Morotai's).

Gate, `id-kemenag` group after the round, at each unit's point: 756 rows, 246 places, 269,616 place-days (90,036 held
out, 21,648 in Ramadan), 0 early, 0 late ends, worst 1 min on every event but Asr and the end of eating (2).

**Across each circle.** Every unit's own tables replayed across its circle (`generateReachRows --only id.kemenag`:
3,934 grid points and 515 sweep-worst points, 13,656 rows; the first round's rows unchanged byte for byte): never early
and no end late. Three circles went to 3 min at Asr on a day near their edge, so they are cut back to where every event
holds (probed at 32 bearings from 10 to 29 km, then the rows themselves): Kab. Sambas 29.5 → 20.0 km, Nias Selatan
29.5 → 21.0, Pulau Morotai 28.3 → 26.0. At 29 wide kab/kota the end of eating reached 3 min before Kemenag's Subuh toward
the circle's edge (1,046 place-days, most at Asmat, Alor, Sumba Timur, Nabire, Merauke and Kepulauan Aru), the user's
own dawn there coming about a minute before the point's, and nothing else went past 2: an earlier end, never a start,
recorded as each unit's exception as Kab. Sumenep's is (`KemenagKabkota.round2EarlierEnd`). The alternative is a
smaller circle: probed in rings of whole km at 32 bearings (10, 12, 14, 16, 18, then every km to the radius), each of
the 29 holds class B's 2 min out to the radius below, and the ring beyond it would fall back to the edge's 6–7 min.
That is a far larger step than Sumenep's (3 min on one day), so it was the owner's choice, and he made it on 12 Nov
2026 (ruling R186): the 30 exceptions of 3 min at the end of eating are approved, Kab. Sumenep's and these 29, being
nearer Kemenag's table for the people in that ring than the edge's 6–7 min. An earlier end, never a start; the table
below stays as the record of the alternative he did not take. Whole gate after it: `id.kemenag — class B, late limit 2
(by exception: endOfEating 3 from 30 units) · 4695 places · 5145720 place-days (4966140 held out, 413160 in Ramadan) ·
14479 rows`; worst 2 on every start, 3 at those 30 units' end of eating; the imsak rows unchanged.

| kab/kota (myQuran id) | radius km | holds class B's 2 min out to (km, ring probe) |
|---|---|---|
| Kab. Bireuen (0113) | 18.8 | 18 (largest ring probed) |
| Kota Sabang (0122) | 28.3 | 18 |
| Kab. Karimun (0502) | 22.1 | 16 |
| Kab. Bangka Barat (0902) | 28.6 | 27 |
| Kab. Belitung Timur (0906) | 29.5 | 26 |
| Kab. Sumbawa (1807) | 29.5 | 25 |
| Kab. Alor (1901) | 29.5 | 21 |
| Kab. Ende (1903) | 26.8 | 24 |
| Kab. Manggarai Barat (1909) | 29.5 | 23 |
| Kab. Sumba Timur (1919) | 29.5 | 23 |
| Kab. Sintang (2012) | 23.3 | 18 |
| Kab. Barito Utara (2203) | 22.9 | 20 |
| Kab. Bulungan (2401) | 29.5 | 20 |
| Kab. Bone (2603) | 29.5 | 22 |
| Kab. Luwu Utara (2611) | 24.4 | 21 |
| Kab. Banggai (2801) | 28.2 | 25 |
| Kab. Toli-Toli (2812) | 21.4 | 19 |
| Kab. Kepulauan Sangihe (2905) | 29.5 | 21 |
| Kab. Buru (3101) | 24.1 | 23 |
| Kab. Kepulauan Aru (3103) | 29.5 | 24 |
| Kab. Halmahera Utara (3203) | 29.5 | 24 |
| Kab. Kepulauan Sula (3205) | 29.5 | 25 |
| Kab. Asmat (3301) | 29.5 | 20 |
| Kab. Biak Numfor (3302) | 29.5 | 22 |
| Kab. Merauke (3315) | 29.5 | 24 |
| Kab. Nabire (3317) | 29.5 | 24 |
| Kab. Fakfak (3401) | 29.5 | 23 |
| Kab. Manokwari (3403) | 23.6 | 22 |
| Kab. Teluk Bintuni (3411) | 29.5 | 24 |

Kab. Bireuen's rings stopped at 18 km, below its 18.8 km radius, so where it would hold is not known beyond 18.

122 app city entries now resolve to a unit of their own instead of the edge (169 of the 448 remain on it). Share of
each kab/kota inside its circle (area, sampled on its own OSM polygon): about 17 % of the 119 kab/kota's combined
population (about 6.5 of 37 million, assuming an even density, so approximate and low, since the seats are the densest
part).

| kab/kota (myQuran id) | seat (app city) | horizon | radius km (nearest other kab/kota, km) | share of its area inside |
|---|---|---|---|---|
| Kab. Lombok Timur (1805) | Selong | −1° | 11.6 (Lombok Tengah, 12.173) | 22.5% |
| Kab. Asahan (0201) | Kisaran | −1° | 6.1 (Batu Bara, 6.654) | 3.1% |
| Kab. Lombok Tengah (1804) | Praya | −1° | 8.3 (Lombok Barat, 8.900) | 18.6% |
| Kab. Langkat (0210) | Stabat | −1° | 5.5 (Kabupaten Deli Serdang, 6.075) | 1.6% |
| Kab. Purbalingga (1420) | Purbalingga | −1° | 4.9 (Banyumas, 5.451) | 9.3% |
| Kab. Lampung Utara (1002) | Kotabumi | −1° | 11.4 (Lampung Tengah, 11.917) | 16.7% |
| Kab. Blora (1404) | Blora | −1° | 7.9 (Rembang, 8.433) | 10.0% |
| Kab. Buleleng (1703) | Singaraja | −1° | 17.1 (Tabanan, 17.663) | 32.7% |
| Kab. Gunungkidul (1502) | Wonosari | −1° | 12.1 (Bantul, 12.653) | 30.9% |
| Kab. Sambas (2009) | Kota Sambas | −1° | 20.0 (none within 30 km); cut back for Asr | 21.7% |
| Kab. Rembang (1422) | Rembangan | −1° | 10.5 (Pati, 11.017) | 18.8% |
| Kab. Banjar (2102) | Martapura | −1° | 2.6 (Banjarbaru, 3.144) | 0.5% |
| Kab. Polewali Mandar (3006) | Polewali | −1° | 13.1 (Mamasa, 13.699) | 15.9% |
| Kab. Wajo (2621) | Sengkang | −1° | 8.3 (Soppeng, 8.831) | 8.6% |
| Kab. Tabanan (1708) | Tabanan | −1° | 3.5 (Badung, 4.094) | 4.5% |
| Kab. Pidie (0116) | Sigli | −1° | 13.7 (Pidie Jaya, 14.212) | 10.2% |
| Kab. Ogan Ilir (0808) | Indralaya | −1° | 13.3 (Ogan Komering Ilir, 13.863) | 24.9% |
| Kab. Lahat (0803) | Lahat | −1° | 18.1 (Muara Enim, 18.616) | 23.7% |
| Kab. Maluku Tengah (3105) | Masohi | −1° | 14.7 (Seram Bagian Barat, 15.298) | 4.3% |
| Kab. Labuhanbatu (0207) | Rantauprapat | −1° | 7.8 (Kabupaten Padang Lawas Utara, 8.371) | 7.1% |
| Kab. Kapuas (2205) | Kualakapuas | −1° | 11.9 (Pulang Pisau, 12.402) | 2.6% |
| Kab. Kotawaringin Timur (2208) | Sampit | −1° | 21.5 (Katingan, 22.092) | 9.2% |
| Kab. Sintang (2012) | Sintang | −1° | 23.3 (Sekadau, 23.837); end of eating 3 | 8.0% |
| Kab. Luwu Utara (2611) | Masamba | −1° | 24.4 (Luwu, 24.949); end of eating 3 | 24.6% |
| Kab. Tanjung Jabung Barat (0607) | Kuala Tungkal | −1° | 4.5 (Indragiri Hilir, 5.007) | 1.1% |
| Kab. Sikka (1915) | Maumere | −1° | 24.4 (Ende, 24.932) | 52.8% |
| Kab. Jembrana (1705) | Negara | −1° | 14.4 (Buleleng, 14.990) | 45.0% |
| Kab. Sumba Barat Daya (1917) | Tambolaka | −1° | 14.7 (Sumba Barat, 15.207) | 36.5% |
| Kab. Bangka (0901) | Sungailiat | −1° | 22.0 (Pangkalpinang, 22.597) | 25.5% |
| Kab. Nias Selatan (0214) | Teluk Dalam | −1° | 21.0 (none within 30 km); cut back for Asr | 5.5% |
| Kab. Bengkayang (2001) | Bengkayang | −1° | 7.1 (Landak, 7.611) | 2.9% |
| Kab. Karo (0206) | Kabanjahe | −2° | 10.1 (Simalungun, 10.617) | 14.3% |
| Kab. Tapanuli Utara (0224) | Tarutung | −2° | 18.1 (Kabupaten Tapanuli Tengah, 18.650) | 27.3% |
| Kab. Ende (1903) | Ende | −1° | 26.8 (Nagekeo, 27.394); end of eating 3 | 53.2% |
| Kab. Dairi (0203) | Sidikalang | −2° | 4.5 (Kabupaten Pakpak Bharat, 5.064) | 3.1% |
| Kab. Rejang Lebong (0708) | Curup | −2° | 2.3 (Kepahiang, 2.813) | 1.1% |
| Kab. Sinjai (2616) | Sinjai | −1° | 0.7 (Bone, 1.219) | 0.2% |
| Kab. Manggarai Barat (1909) | Labuan Bajo | −1° | 29.5 (none within 30 km); end of eating 3 | 42.7% |
| Kab. Timor Tengah Utara (1921) | Kefamenanu | −1° | 11.4 (Oe-Silu, 11.977) | 15.7% |
| Kab. Hulu Sungai Tengah (2105) | Barabai | −1° | 8.2 (Hulu Sungai Utara, 8.703) | 14.5% |
| Kab. Poso (2809) | Poso | −1° | 14.6 (Tojo Una-Una, 15.104) | 5.2% |
| Kab. Toraja Utara (2620) | Rantepao | −2° | 5.5 (Tana Toraja, 6.088) | 7.3% |
| Kab. Sumba Timur (1919) | Waingapu | −1° | 29.5 (none within 30 km); end of eating 3 | 27.7% |
| Kab. Sijunjung (0309) | Sijunjung | −1° | 13.0 (Solok, 13.564) | 16.3% |
| Kab. Merauke (3315) | Merauke | −1° | 29.5 (none within 30 km); end of eating 3 | 3.5% |
| Kab. Hulu Sungai Utara (2106) | Amuntai | −1° | 6.6 (Tabalong, 7.199) | 14.8% |
| Kab. Minahasa Utara (2911) | Airmadidi | −1° | 6.2 (Bitung, 6.789) | 12.3% |
| Kab. Belu (1902) | Atambua | −1° | 7.7 (Timor Tengah Utara, 8.294) | 16.8% |
| Kab. Dompu (1802) | Dompu | −1° | 3.7 (Bima, 4.240) | 1.9% |
| Kota Lubuklinggau (0814) | Lubuklinggau | −1° | 4.3 (Rejang Lebong, 4.857) | 15.2% |
| Kab. Alor (1901) | Kalabahi | −1° | 29.5 (none within 30 km); end of eating 3 | 47.4% |
| Kab. Puncak Jaya (3322) | Mulia | −2° | 12.1 (Puncak, 12.637); end of eating -215 s | 9.2% |
| Kota Prabumulih (0817) | Prabumulih | −1° | 7.2 (Muara Enim, 7.749) | 34.6% |
| Kab. Bangka Barat (0902) | Muntok | −1° | 28.6 (Banyuasin, 29.180); end of eating 3 | 23.9% |
| Kab. Halmahera Utara (3203) | Tobelo | −1° | 29.5 (none within 30 km); end of eating 3 | 30.0% |
| Kab. Aceh Barat (0101) | Meulaboh | −1° | 7.9 (Nagan Raya, 8.478) | 3.1% |
| Kota Langsa (0120) | Langsa | −1° | 3.8 (Aceh Timur, 4.349) | 22.5% |
| Kota Tebing Tinggi (0233) | Tebingtinggi | −1° | 1.1 (Kabupaten Serdang Bedagai, 1.660) | 9.6% |
| Kab. Nunukan (2403) | Nunukan | −1° | 3.3 (Bahagian Tawau, 3.881) | 0.1% |
| Kab. Nabire (3317) | Nabire | −1° | 29.5 (none within 30 km); end of eating 3 | 14.7% |
| Kab. Jayapura (3307) | Sentani | −1° | 11.0 (Jayapura, 11.584) | 2.7% |
| Kab. Belitung (0905) | Tanjung Pandan | −1° | 20.4 (Belitung Timur, 20.951) | 32.8% |
| Kab. Biak Numfor (3302) | Biak | −1° | 29.5 (none within 30 km); end of eating 3 | 48.9% |
| Kab. Barito Utara (2203) | Muara Teweh | −1° | 22.9 (Murung Raya, 23.466); end of eating 3 | 16.4% |
| Kab. Aceh Barat Daya (0102) | Blangpidie | −1° | 19.3 (Aceh Selatan, 19.845) | 42.2% |
| Kab. Samosir (0219) | Pangururan | −2° | 14.4 (Kabupaten Dairi, 14.971) | 41.1% |
| Kab. Kepulauan Sangihe (2905) | Tahuna | −1° | 29.5 (none within 30 km); end of eating 3 | 84.9% |
| Kota Gunungsitoli (0227) | Gunungsitoli | −1° | 3.9 (Nias Regency, 4.419) | 2.8% |
| Kota Pagar Alam (0815) | Pagar Alam | −2° | 2.0 (Lahat, 2.548) | 2.0% |
| Kab. Sumbawa Barat (1808) | Taliwang | −1° | 18.7 (Sumbawa, 19.222) | 48.8% |
| Kab. Lembata (1906) | Lewoleba | −2° | 11.7 (Flores Timur, 12.230) | 22.2% |
| Kab. Aceh Singkil (0106) | Singkil | −1° | 15.5 (Aceh Selatan, 16.042) | 21.0% |
| Kab. Bulungan (2401) | Tanjung Selor | −1° | 29.5 (none within 30 km); end of eating 3 | 19.8% |
| Kota Payakumbuh (0317) | Payakumbuh | −1° | 1.9 (Lima Puluh Kota, 2.453) | 14.9% |
| Kab. Kepulauan Sula (3205) | Sanana | −1° | 29.5 (none within 30 km); end of eating 3 | 44.0% |
| Kota Kotamobagu (2913) | Kotamobagu | −1° | 0.8 (Bolaang Mongondow, 1.347) | 1.8% |
| Kab. Lanny Jaya (3311) | Tiom | −2° | 14.8 (Tolikara, 15.305); end of eating -220 s | 23.6% |
| Kab. Belitung Timur (0906) | Manggar | −1° | 29.5 (none within 30 km); end of eating 3 | 44.6% |
| Kota Sungai Penuh (0611) | Sungai Penuh | −2° | 2.5 (Kerinci, 3.006) | 5.4% |
| Kab. Asmat (3301) | Agats | −1° | 29.5 (none within 30 km); end of eating 3 | 7.9% |
| Kab. Kepulauan Aru (3103) | Dobo | −1° | 29.5 (none within 30 km); end of eating 3 | 9.7% |
| Kota Tomohon (2915) | Tomohon | −2° | 3.0 (Minahasa, 3.592) | 16.5% |
| Kota Sibolga (0231) | Sibolga | −1° | 0.1 (Kabupaten Tapanuli Tengah, 0.525) | 0.3% |
| Kota Pariaman (0316) | Pariaman | −1° | 4.0 (Padang Pariaman, 4.510) | 28.1% |
| Kab. Fakfak (3401) | Fakfak | −1° | 29.5 (none within 30 km); end of eating 3 | 25.7% |
| Kota Tual (3111) | Tual | −1° | 1.4 (Maluku Tenggara, 1.979) | 2.9% |
| Kota Subulussalam (0123) | Subulussalam | −1° | 6.4 (Aceh Selatan, 6.968) | 10.8% |
| Kab. Teluk Bintuni (3411) | Bintuni | −1° | 29.5 (none within 30 km); end of eating 3 | 11.2% |
| Kab. Malinau (2402) | Malinau | −1° | 7.9 (Tana Tidung, 8.420) | 0.5% |
| Kab. Buru Selatan (3102) | Namrole | −1° | 25.8 (Buru, 26.317) | 23.5% |
| Kota Solok (0319) | Solok | −1° | 0.1 (Solok, 0.620) | 0.1% |
| Kab. Sorong Selatan (3409) | Teminabuan | −1° | 16.6 (Maybrat, 17.183) | 12.7% |
| Kota Padangpanjang (0315) | Padangpanjang | −2° | 0.1 (Tanah Datar, 0.235) | 0.1% |
| Kota Sabang (0122) | Sabang | −1° | 28.3 (Aceh Besar, 28.867); end of eating 3 | 98.6% |
| Kab. Manokwari Selatan (3404) | Ransiki | −1° | 12.1 (Pegunungan Arfak, 12.662) | 15.5% |
| Kab. Ngawi (1617) | Ngawi | −1° | 2.8 (Blora, 3.303) | 1.7% |
| Kab. Sampang (1623) | Sampang | −1° | 12.9 (Pamekasan, 13.498) | 28.5% |
| Kab. Trenggalek (1627) | Trenggalek | −1° | 6.8 (Tulungagung, 7.321) | 11.7% |
| Kab. Sumbawa (1807) | Sumbawa Besar | −1° | 29.5 (none within 30 km); end of eating 3 | 28.5% |
| Kab. Manggarai (1908) | Ruteng | −2° | 4.3 (Manggarai Timur, 4.828) | 4.4% |
| Kab. Timor Tengah Selatan (1920) | Soe | −2° | 20.5 (Kupang, 21.074) | 33.0% |
| Kab. Bone (2603) | Watampone | −1° | 29.5 (Soppeng, 30.004); end of eating 3 | 39.5% |
| Kab. Muna (2713) | Raha | −1° | 9.4 (Muna Barat, 9.990) | 9.2% |
| Kab. Banggai (2801) | Luwuk | −1° | 28.2 (Banggai Kepulauan, 28.780); end of eating 3 | 16.8% |
| Kab. Morowali (2806) | Bungku | −1° | 22.7 (Luwu Timur, 23.276) | 17.8% |
| Kab. Minahasa (2908) | Tondano | −2° | 3.3 (Minahasa Utara, 3.874) | 3.0% |
| Kab. Buru (3101) | Namlea | −1° | 24.1 (Buru Selatan, 24.618); end of eating 3 | 16.5% |
| Kab. Manokwari (3403) | Manokwari | −1° | 23.6 (Pegunungan Arfak, 24.191); end of eating 3 | 22.6% |
| Kota Padangsidempuan (0229) | Padangsidempuan | −1° | 2.5 (Kabupaten Tapanuli Selatan, 3.066) | 12.5% |
| Kab. Bungo (0602) | Muara Bungo | −1° | 5.4 (Tebo, 5.950) | 1.9% |
| Kab. Ogan Komering Ulu (0810) | Baturaja | −1° | 14.4 (Ogan Komering Ulu Timur, 14.934) | 16.7% |
| Kab. Bireuen (0113) | Bireun | −1° | 18.8 (Aceh Utara, 19.356); end of eating 3 | 37.1% |
| Kab. Maros (2612) | Maros | −1° | 7.6 (Makassar, 8.157) | 12.5% |
| Kab. Kotawaringin Barat (2207) | Pangkalanbuun | −1° | 24.8 (Sukamara, 25.329) | 20.3% |
| Kab. Karimun (0502) | Tanjung Balai | −1° | 22.1 (Kepulauan Meranti, 22.696); end of eating 3 | 35.6% |
| Kab. Tabalong (2108) | Tanjung | −1° | 9.8 (Balangan, 10.352) | 8.7% |
| Kab. Toli-Toli (2812) | Toli-Toli | −1° | 21.4 (Buol, 21.959); end of eating 3 | 29.2% |
| Kab. Majene (3001) | Majene | −1° | 3.0 (Polewali Mandar, 3.587) | 2.4% |
| Kab. Pulau Morotai (3207) | Daruba | −1° | 26.0 (Halmahera Utara, 28.868); cut back for Asr | 27.9% |

## 7h's ADHAN2_ASR_ALLOWANCE (added to `other.singapore`, per the coordinator's message)

`other.singapore`'s Asr now carries `SAFE_START + ADHAN2_ASR_ALLOWANCE` instead of the plain
`SAFE_START`. Confirmed against 7h's own proof (read directly from 7h's still-unmerged worktree
branch, since both agents share this Mac's git object store — `docs/research/2026-09-prayer-times/
proof/7h-other-and-default.md` there): measured at Cape Town, 15 Oct 2026, against the old
adhan2-backed engine, adhan2's raw Asr runs ~32 s later than this core's own iterated Asr (adhan2
takes one declination for the whole day rather than iterating it to the Asr moment — within the
documented `ADHAN2_ASR_ALLOWANCE`, 35 s), and Singapore is the one adhan2 preset that rounds Asr
**up** rather than to the nearest minute, which turns that sub-minute gap into a full minute-early
violation the plain 30 s safe start does not cover. 7h gives the same allowance to `other.mwl`,
`other.isna`, `other.karachi` and `other.moonsighting` in `Generic.kt` (their file), and their own
proof explicitly names `other.singapore` (Muis.kt) as "confirmed necessary" but not theirs to fix —
exactly the item the coordinator routed to this task. No gate row checks `other.singapore` (no
authority publishes "the old picker's method anywhere" as its own table); the change is a safety
margin, not something fit against data.

## The end of eating, audited (3 Oct 2026, before Ramadan 1448)

None of the three entries' rows checked the end of eating until this audit: e-solat, Kemenag's
schedule and MORA's list print an imsak 10 min before Subuh as a precaution, which the normalised
tables the gate reads do not hold, and the fast begins at Subuh. (JAKIM's raw e-solat captures under
`archive/raw/monitor/my-jakim/` do carry its imsak field: Subuh less 10 min on 12 of the 13
zone-years held, equal to Subuh on one. An `Im` column for `my.jakim` read from them is a to-do for
the next archive round; this fix round could not add archive files.) Every row's Fajr column is now also the end of
eating (`F+E`). Whole gate, nothing else moved:

| entry | days | late ends | worst, min before the printed Subuh | limit |
| --- | --- | --- | --- | --- |
| `my.jakim` | 9,489 | 0 | 4 (1,509 days at 3 or more) | 4, recorded |
| `id.kemenag` | 6,570 | 0 | 2 | 2 (class B) |
| `bn.mora` | 385 | 0 | 1 | 2 (class B) |

**JAKIM's exception (4 min, the end of eating alone).** e-solat prints each zone's Subuh for its
latest reference point, rounded up; the engine's end of eating is the dawn at the place itself,
rounded down, never after it. The fitter would move it later (+93 s at WLY01, +78 s at JHR02), but
an end only ever moves earlier, so the minutes are recorded instead (Jakim.kt). The imsak the app
shows is that same dawn less 10 min, as JAKIM's own is its Subuh less 10, so the check of the end
against Subuh carries to imsak; imsak itself is not gated yet (no normalised column holds it). No margin changed; the
golden vector did not move here.

## What is measured and what is not

- **sg.muis**: measured, one point, three years (2024–2026) — the whole area (Singapore) is one
  fixed point, so "at most" is a real claim.
- **bn.mora**: Brunei-Muara alone is measured (one district, one point, 2026). Belait, Tutong,
  Temburong and the +240 s edge are not: their minutes are the ministry's own documented figures,
  never checked against a table of their own.
- **my.jakim**: 13 of 14 captured zones are measured (SBH06 excluded, its 2026 table breaks
  internally — unchanged from Task 5). The outside-zone edge (Peninsular and Borneo) is not
  measured and was not re-verified this task; the towns it was checked at earlier (Kapit, Sibu,
  Mukah, Gua Musang, Jeli, Kuala Krai, Dabong) have no local official tables to re-run.
- **id.kemenag**: 18 of 18 captured kab/kota are measured (13 in this task, five in the monitor
  round), over one year only (2026; myQuran has never captured a second year for Kemenag, so there is
  no cross-year check — see the split above).
  Everywhere else in Indonesia is not measured (the Jabodetabek reach unit is gone since ruling R106).
  `id.muhammadiyah` is not measured anywhere (no official Muhammadiyah table held locally).

## Concerns

1. **Two shared, cross-authority tests now fail, both caused by changes this task's brief and
   ruling R57 require; this task does not own either file (gate code / registry core's own tests),
   so neither was edited. Per the coordinator: left alone, to be fixed at merge.**
   - `tools/timetables/.../gate/GateFixtureTest.kt`, `` `a dhuhr-only exception leaves fajr at the
     class limit` `` (line ~122): asserts `strict.event(Event.ASR_STANDARD).over` reproduces a
     class-A-with-no-exceptions baseline; `sg.muis` is now class B with an Asr exception, so this
     baseline no longer holds. **Smallest fix**: build `strict` from a de-excepted lookup
     (`Registry.byId("sg.muis")!!.copy(lateLimits = emptyList())`) so the test isolates the
     class-default fallback deliberately, rather than incidentally relying on production carrying
     none.
   - `shared/.../registry/RegistryTest.kt`:
     - `` `brunei's districts are its bandar seri begawan table with their own minutes` `` (line
       ~473): asserts `measured = false` and `entryClass = D_AUTHORITY` for all four districts
       uniformly. Checking Brunei-Muara against a real table (this task) makes it measured/class B
       specifically. **Smallest fix**: give each case its own expected `(measured, entryClass)`
       pair (three stay `(false, D_AUTHORITY)`; Brunei-Muara becomes `(true, B)`).
     - `` `the old picker's methods take the plain safe rounding and never an authority's fitted
       margins` `` (line ~800): asserts every `other.*` entry's margins equal the plain safe
       rounding exactly. `other.singapore`'s Asr now adds `ADHAN2_ASR_ALLOWANCE` (this task, at the
       coordinator's request, per 7h's own finding — see above). **Already half fixed on 7h's own
       branch**, not yet merged here: 7h's version of this test (read from 7h's worktree branch,
       same Mac) replaces the blanket equality with an `allowanceFixed` set
       (`other.mwl`/`other.isna`/`other.karachi`/`other.moonsighting`) that gets the wider Asr margin
       and asserts the plain one for everyone else — including `other.singapore`, since 7h could not
       fix Muis.kt themselves. **Smallest fix once both branches merge**: add `"other.singapore"` to
       7h's `allowanceFixed` set (their own proof.md already names this as the exact next step; the
       coordinator has confirmed this is on their list at merge time).
2. **JAKIM's outside-zone edge was not re-verified.** The brief lists seven towns it was checked at
   in an earlier task (Kapit, Sibu, Mukah, Gua Musang, Jeli, Kuala Krai, Dabong); no official table
   for any of them is held locally in this checkout, so "keep it" was followed literally — the edge
   margins are unchanged, not re-derived or re-checked.
3. **Kemenag has only one captured year.** The Jan–Sep/Oct–Dec split is a reasonable forward-in-time
   substitute, but it cannot catch a mistake that only shows up in a genuinely different year (a
   leap-year effect, a future ihtiyat change, etc.). Worth a real second year once myQuran/Kemenag
   republish 2027.
4. **Brunei's held-out count reads as 0 place-days** in the gate report (explained above): the fit
   and test files are two captures of the *same* calendar year, so their dates collide in the
   report's per-date dedup. The underlying check is still real (both files' rows are independently
   compared), just not visible as a separate "held out only" table.
