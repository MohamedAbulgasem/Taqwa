# Sunni authorities in the Arab world and Türkiye (research of 25–26 September 2026)

Written up from the round's regional report, which was delivered in the session. Confidence:
**HIGH** = read on the authority's own page, API or PDF; **MEDIUM** = inferred from a year or more
of its table, or from a reliable secondary source; **LOW** = one day or one weak source.
"Pub" is the published time; "app" is the adhan2 preset with nearest rounding and no extra minutes.
Raw captures for Jordan, Palestine, Lebanon, Syria and Iraq are in the local
`tools/timetables/official/archive/raw/moral-auth-mena/`; they are not yet normalised into tables,
so the gate has **no** Levantine days to prove anything on.

The main finding: no adhan preset with nearest rounding matches any of these authorities without
sometimes being early. The authorities add margins the presets lack (Maghrib usually sunset + 2
to + 10, Dhuhr + 1 to + 10, Isha often 18° rather than 17°), several round starts up, and every one
uses the standard (shadow 1) Asr, Türkiye included.

## Per country

**Saudi Arabia, Umm al-Qura (KACST).** Official engine: an open JSON API,
`https://umqserv.kacst.gov.sa/api/v1/Prayer/{GetPrayers|GetPrayerByMonth|GetPrayerByYear|…}?lat=&lon=&zone=3`
(base URL in the site's bundle; 173 city points in `/assets/data/cities.json`). Fajr 18.5° with a
seasonal quirk (1–1.7 min after exact 18.5° in Aug–Nov, 0–0.8 min before in Feb–Apr; HIGH that it
exists). Isha = Maghrib + 90, + 120 on every day of the Umm al-Qura month of Ramadan (59 of 59
days; live since at least Ramadan 1431). No precaution minutes; sunrise rounded down, Dhuhr, Asr and
Maghrib rounded up, Isha taken from the rounded Maghrib. Standard Asr, no imsak, no elevation.
Fajr moved from 19° to 18.5° at Muharram 1430 (Dec 2008; MEDIUM, second-hand). The ministry sets
the adhan by it (MEDIUM). App today: 1 min early on about 41–55% of days at every start, sunrise
late on about 47%. The later workflow rebuilt it exactly from 13,146 place-days (`profiles-tested.md`).

**UAE, Dubai (IACAD) and the federal Awqaf.** IACAD publishes a perpetual table per zone (Dubai,
Dubai Rural, Hatta) as yearly Excel open data; 2022–2025 identical by calendar date (HIGH).
About 18.2°/18.2° (MEDIUM). Sunrise − 3, Dhuhr + 3, Asr + 1, Maghrib + 3 (MEDIUM-HIGH); Asr was + 3
until 2020, Isha was Maghrib + 90 until 2017. Awqaf lists about 60 zones including mountain tops
(Jebel Hafeet, Jebel Jais), so probably handles elevation by zone (MEDIUM). App: Maghrib 1 min
early on 45–61% of days, Isha on 17–27%.

**Kuwait, Ministry of Awqaf with the Al-Ojairi Scientific Centre.** 18°/17.5°, no minutes, mostly
nearest rounding (MEDIUM, from 46 newspaper copies 2019–2026). The ministry's own mosque app gives
times "by the Al-Ojairi Scientific Centre's computation" (HIGH). No machine-readable source found.

**Qatar, Calendar House.** About 18° (published Fajr 0.3–1.6 min before exact 18°), Isha = Maghrib
+ 90 on all 365 days including Ramadan (HIGH). Dhuhr + 1, Asr + 1, Maghrib + 2, or + 3 in
Ramadan (MEDIUM; the Ramadan + 3 rests on one Ramadan). Doha is the reference for the east coast
from Al Khor to Al Wakrah (HIGH); separate rows for Al Shamal, Mesaieed, Dukhan, Abu Samra, Halul.
The Ministry's open JSON API (`meiaservicesext.islam.gov.qa/.../PrayerTimes?date=`) is 1 min earlier
than the printed calendar at Dhuhr and Asr on all 119 days compared (HIGH). App: Maghrib and Isha
1–3 min early every day.

**Bahrain, Supreme Council for Islamic Affairs.** Isha 18°, replacing Maghrib + 90 (HIGH, the
calendar book). Fajr and sunrise computed for the easternmost point, Dhuhr to Isha for the
westernmost point; Dhuhr when the whole disc has crossed (+ 1). One table for the kingdom. Sunni
mosques follow it (HIGH); the Ja'fari endowment publishes no times.

**Oman, MARA.** 18°/18° (every day within 0.03 min), Dhuhr, Asr, Maghrib + 5, rounded up, sun
evaluated once a day at about 00 UT, 86 localities at their own coordinates (HIGH as inferred; no
held-out test written up). App: 4–7 min early every day.

**Jordan, Ministry of Awqaf.** Fajr 18°, Isha ≈ 18.2°; sunrise 5.6–7.5 min before true sunrise and
Maghrib + 5.9..7.6, behaving like fixed depression angles (about −2.0° and −2.25°) rather than fixed
minutes; Dhuhr, Asr, Isha + 0..1, rounded up (inferred from 80 Wayback dates 2021–2025, HIGH).
16 regional tables plus Jerusalem. Copyright under Law 22/1992. App: Maghrib 6–8 and Isha 5–7 min
early.

**Palestine.** (a) The PA Dar al-Iftaa publishes a perpetual al-Aqsa table reprinted every year
(2012 printing, reused verbatim in the 2026 imsakiya): ≈ 18°/18°, Maghrib + 4.9..7.3, sunrise
1.6–4.3 min early, city offsets printed (Hebron + 1, Gaza + 3, Rafah + 4, Jericho − 1); a "first
adhan" 20 min before Fajr in Ramadan (HIGH). (b) Gaza's Ministry of Awqaf: Fajr ≈ 19.5° (second
adhan; first adhan 30 min earlier), Isha ≈ 17.5°, Maghrib + 2..3; it conflicts with the PA table for
Gaza (the PA's Fajr about 7 min later, Maghrib 3 min later).

**Lebanon, Dar al-Fatwa.** Its own mosque-screen feed (Masjidi TV id 26) serves a 366-row perpetual
table; Fajr 19.1–20.0° (mean 19.6°, 6–13 min before 18°), Isha 17.2–18.1°, Maghrib + 3.1..6.8
(HIGH that the table is identified). The website widget changed method around 2024 and has shown
nothing since about February 2026. The Shia (al-Manar) table is separate: Fajr ≈ 15.8°, Maghrib ≈
sunset + 17.

**Syria, Ministry of Awqaf.** The MWL method plus a 2-minute tamkin, officially since 28 Feb 2025
(SANA; HIGH that 2 minutes were added), declared the only reference in all Syria's mosques on
6 Mar 2025. The + 2 sits on Maghrib (MEDIUM, 2 dates). The ministry's app computes from GPS.

**Iraq, Sunni Endowment.** 18°/17°, Dhuhr ≈ + 5, Asr + 3..4.5, Maghrib ≈ + 3 (MEDIUM, one official
sample plus a third-party site matched to the Baghdad table). The Shia endowment's Maghrib is
≈ sunset + 15. Kurdistan's ministry: not reachable.

**Yemen.** The Sanaa Awqaf embeds today's times for 31 locations: Fajr 18°, Isha ≈ 16°, Dhuhr ≈ + 10,
Maghrib + 6 lowland and ≈ + 9.5 highland (LOW, one date). The Sanaa ministry is Houthi (Zaydi) run;
whether its Maghrib delay is Zaydi practice or elevation is unresolved. Aden side: not found.

**Egypt, Egyptian General Survey Authority (Dar al-Ifta republishes).** Fajr 19.5° (Dar al-Ifta
fatwa 4021, 2017; HIGH), Isha 17.5°, no minutes, nearest rounding, about 100 cities at their own
points, no elevation (HIGH, inferred). Imsak = Fajr − 20 in Ramadan (ESA imsakiya, HIGH). The
14.7° proposal was rejected in 2017 and again debated in 2018 without change. ESA's terms forbid
copying except private non-commercial use. App: Asr early on 12 days, Maghrib and Isha on 1 day
each (the later workflow found a method change between the Dec 2024 and Jan 2025 captures).

**Libya, Awqaf and Dar al-Ifta (Tripoli).** Fajr ≈ 18.5°, Isha ≈ 18.2–18.3°; sunrise ≈ − 1,
Dhuhr ≈ + 3, Asr 0, Maghrib + 2.5..4 (HIGH, 25 Tripoli dates and 9 cities). A 1992 observation
committee made the table used since 1994 (Dar al-Ifta statement of 8 Feb 2022, read in full).
The same statement complains that the eastern Awqaf forced mosques in the east and south onto a
Fajr 15–20 min later all year. Mawaqit screens in Benghazi and Tobruk in Sep 2026 show the
Tripoli-type method (LOW–MEDIUM). Imsak = Fajr − 10 or − 11. api.ifta.ly serves Tripoli only.

**Tunisia, INM.** 18°/18° (183 days, HIGH). Dhuhr = transit + 7 and Maghrib = sunset + 2 on 183 of 183
days. 258 delegations with their own coordinates; an elevation correction that moves Fajr and Isha
too (Thala: Maghrib + 7). Imsak = Fajr − 10 all year (HIGH). Open JSON endpoints on meteo.tn; its
terms forbid giving the data to third parties without INM's written consent.

**Algeria, MARW.** 18°/17°, Maghrib + 3, every column rounded up (HIGH). Three base tables
(Algiers, Djelfa, Adrar) plus monthly per-wilaya minute offsets that are 0–2 min conservative; no
elevation; no imsak. Image-only PDFs, "not to be printed or copied without the ministry's permission".

**Morocco, Ministry of Habous.** Fajr 19° (rounded down), Isha 17°; sunrise − 3, Dhuhr + 5, Maghrib + 4
(Casablanca + 4..5), Asr 0 (HIGH except Maghrib MEDIUM). About 190 city tables; elevation applied to
sunrise and sunset only (Ifrane Maghrib + 7.4, Imilchil + 8.1). No imsak. UTC+0 since 20 Sep 2026.

**Mauritania.** Ramadan imsakiyas only: Fajr ≈ 19.5°, Isha ≈ 17.5°, Maghrib = printed sunset + 2,
imsak = Fajr − 5 (HIGH for what they show; LOW that anyone follows a year-round table).

**Sudan, Fiqh Academy.** The 2022 Khartoum imsakiya implies Fajr ≈ 18.3°, Isha ≈ 18.0°, Dhuhr ≈ + 2.5;
2026 news reports imply ≈ 19.4° (LOW). The Academy calls an imsak before Fajr unfounded.

**Türkiye, Diyanet.** İmsak 18°, Yatsı 17° (Diyanet statement of 17 Jul 2013, HIGH). Temkin:
sunrise − 7, Dhuhr + 5, Asr + 4, Maghrib + 7, İmsak 0, Isha 0 (vakithesaplama.diyanet.gov.tr/temkin.php,
HIGH). The sun's declination and equation of time are taken once a day at 0h UT, with nearest
rounding: that reproduces 98.3% of minutes as they stand (HIGH). One point per district near the
town centre (six published, e.g. İstanbul 41.012, 28.974; Ankara 39.938, 32.848); no elevation.
Asr-ı evvel only. İmsak is the Fajr start itself. 1983: 19° plus temkin became 18° with reduced
temkin. Abroad (above 44.5°): Isha 16° with the 2021 congress rule. App (TURKEY preset) over 8
cities × 396 days: Maghrib 1–2 min early on about 36% of days, Isha on 39%, Asr on 21%; Fajr 1 min
early on 197 days; sunrise late on 191.

## Adjustments tested never earlier (adhan2 base, nearest rounding unless noted)

| Country | Adjustments | Worst late |
|---|---|---|
| Saudi Arabia | round up + 1 on every start, Isha from rounded Maghrib, sunrise down (nearest: Fajr + 2, Dhuhr + 1, Asr + 1, Maghrib + 2, Isha + 2) | 2 |
| UAE (Dubai) | Fajr + 1, Dhuhr + 3, Asr + 2, Maghrib + 4, Isha + 1, sunrise − 5 | 1–2 |
| Kuwait | Kuwait preset + Fajr + 1, Asr + 1, Isha + 1 | 1–2 |
| Qatar | 18°, Isha = sunset + 90, Dhuhr + 2, Asr + 2, Maghrib + 3, Isha + 3 | 2 |
| Bahrain | 18°/18°, Dhuhr + 2, Asr + 1, Maghrib + 1, sunrise − 1 | 2 |
| Oman | Fajr 18° + 1, Isha 18° + 2, Dhuhr + 6, Asr + 7, Maghrib + 7 | 2–3 |
| Jordan | 18°/18°, Dhuhr + 1, Asr + 1, Maghrib + 8, Isha + 1, sunrise − 7 | 2 |
| Palestine (PA table) | 18°/18°, Asr + 1, Maghrib + 7, Isha + 1 | ~2 |
| Lebanon | Isha 18° + 1, Asr + 2, Maghrib + 7 | ~4 |
| Syria | MWL + Asr + 1, Maghrib + 3, Isha + 1 (2 dates) | — |
| Iraq (Sunni) | MWL + Dhuhr + 5, Asr + 5, Maghrib + 4 | — |
| Egypt | Egyptian + Asr + 1, Maghrib + 1, Isha + 1, sunrise − 1 | 2 |
| Libya (west) | Fajr 18°, Isha 18.5° + 1, sunrise − 1, Dhuhr + 4, Asr + 1, Maghrib + 5 | 2–5 |
| Tunisia | 18°/18°, Dhuhr = noon + 8, Maghrib + 3; highland Maghrib/Isha + 5 or real elevation | — |
| Algeria | 18°/17°, round up, Maghrib + 4 (Algiers) or + 6 elsewhere; Fajr, Asr, Isha + 1..3 outside the base cities | — |
| Morocco | Fajr 19° rounded down, sunrise − 3, Dhuhr + 5, Maghrib + 5 (mountain towns + 8) | — |
| Mauritania | 19.5°/17.5° + 1, Maghrib + 3 | — |
| Sudan | Isha 18°, Dhuhr + 3, Maghrib + 4 outside Khartoum | — |
| Türkiye | Diyanet's own model (sun at 0h UT, its temkin, starts biased + 3 s, sunrise − 3 s) at its district points: 0 early days over 396 days in İstanbul and Ankara | 1 |

## Implications drawn in the report

- Nearest rounding makes the app early on about half of days wherever the authority rounds starts
  up (Umm al-Qura, Oman, Algeria, much of Jordan, UAE Maghrib).
- Maghrib (iftar) is the largest risk; Jordan's and the PA's margins behave like angles and grow
  toward the solstices, so a fixed number of minutes must cover the worst season.
- Isha at 17° is too early in Jordan, the PA, Tunisia, Oman, Bahrain, Libya and Sudan.
- With `ishaInterval`, adhan measures Isha from the unadjusted sunset: any Maghrib adjustment
  (Qatar, Umm al-Qura) must also move Isha.
- Fajr cuts both ways: follow the authority's angle where its Fajr is earlier than the app's
  (Morocco 19°, Mauritania 19.5°, Gaza 19.5°, Lebanon ≈ 19.6°, Egypt 19.5°, Libya ≈ 18.5°, Umm
  al-Qura 18.5°), and show the imsak where it is published (Egypt − 20 Ramadan, PA − 20 Ramadan, Gaza
  − 30 first adhan, Tunisia − 10 all year, Libya − 10, Mauritania − 5 Ramadan). Do not invent one
  where the authority rejects it (Türkiye, Sudan, Saudi Arabia, Qatar, Algeria, Morocco).
- Sunrise must never be later than official: Türkiye − 7, Jordan − 6..7, Morocco − 3, Dubai − 3,
  Libya − 1, rounded down.
- Reference points differ (Bahrain's two extreme points; Algeria's conservative wilaya offsets;
  Doha for the east coast; al-Aqsa plus offsets; Khartoum plus drifting offsets; Diyanet's district
  centres): computing at a GPS point can be earlier than the table.
- Elevation matters in Tunisia (it moves Fajr and Isha too), Moroccan mountain towns, UAE mountain
  zones and highland Yemen.
- Split authorities need a policy: Palestine (PA vs Gaza), Lebanon, Iraq, Bahrain, Libya's east,
  Yemen (one day; hold).
