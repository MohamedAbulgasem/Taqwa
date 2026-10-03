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
