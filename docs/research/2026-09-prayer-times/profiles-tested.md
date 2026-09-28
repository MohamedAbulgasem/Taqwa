# Profiles fitted on official data and tested on days and places they were not fitted on

Workflow run of 26 September 2026. One section per authority group; fields as the agents returned them.

## data-umm-al-qura

**Authority.** Saudi Arabia: the Umm al-Qura calendar prayer times published by KACST at https://www.ummulqura.org.sa/. The prayer-times page is an Angular app. Its bundle sets apiBaseUrl to https://umqserv.kacst.gov.sa/api, and chunk-AK7NYW7O.js lists the endpoints /v1/Prayer/GetPrayers, GetPrayerByMonth, GetPrayerByYear, GetPrayerHijriMonth and GetPrayerHijriYear. The API is open, needs no login and takes lat, lon and zone. For each city the site passes the coordinates in its own /assets/data/cities.json, with zone 3 (Asia/Riyadh). I did not test the official Android app (sa.gov.kacst.ummulqura). I also did not touch the site or any repository: the relayed request to take down the 51 city pages is outside this read-only data task.

### sources

- https://www.ummulqura.org.sa/assets/data/cities.json (city coordinates the site passes to the API), fetched 2026-09-25
- https://umqserv.kacst.gov.sa/api/v1/Prayer/GetPrayerByYear?lang=en&format=24&yg=2026&lat=21.426666&lon=39.831666&zone=3 (Makkah 2026), fetched 2026-09-25T20:54:16Z. The same URL pattern was fetched for Madinah (24.54,39.63), Riyadh (24.67,46.69), Jeddah (21.5,39.17), Dammam (26.44,50.1) and Abha (18.22,42.51) at 20:54:16Z.
- https://umqserv.kacst.gov.sa/api/v1/Prayer/GetPrayerByYear?lang=en&format=24&yg={2024,2025,2027,2028,2029,2030}&lat=..&lon=..&zone=3 for the other place-years, fetched 2026-09-25T21:01:08Z to 21:15:59Z. Every URL and its UTC time is in round/data-umm-al-qura/raw/fetch-log.txt and in the first line of each off-uq-*.txt.
- https://umqserv.kacst.gov.sa/api/v1/Prayer/GetPrayers?lang=en&format=24&lat=21.426666&lon=39.831666&zone=3&yg=2026&mg=9&dg=15 (plus dg=14, 16, 2026-10-15, 2026-12-12 and 2026-12-13), fetched 2026-09-25T20:58:13Z to 20:58:28Z. Cross-check: the single-day, month and Hijri-month endpoints return values identical to the year table.
- https://umqserv.kacst.gov.sa/api/v1/Prayer/GetPrayerByMonth?lang=en&format=24&lat=21.426666&lon=39.831666&zone=3&yg=2026&mg=10, fetched 2026-09-25T20:58:34Z
- https://umqserv.kacst.gov.sa/api/v1/Prayer/GetPrayerHijriMonth?lang=en&format=24&lat=21.426666&lon=39.831666&zone=3&yh=1448&mh=5, fetched 2026-09-25T20:58:42Z
- Where the API base came from: the saved site bundle https://www.ummulqura.org.sa/chunk-ZNY5LYAW.js (apiBaseUrl) and chunk-AK7NYW7O.js (endpoints, parameters yg/mg/dg/yh/mh/lat/lon/zone/lang/format). Local copies are in scratchpad/uqchunks/, saved 2026-09-25 by an earlier agent.
- https://raqmi.dga.gov.sa/platforms/platforms/2029d845-2662-4cbf-774a-08ddf374f302/platform-license (licence link found in the site bundle): fetched 2026-09-25, returned a Cloudflare 403 block, not bypassed, so the terms are not read

### places and days

12 places, 13,146 place-days of official times. Every row has fajr, sunrise, dhuhr, asr, maghrib and isha, with no gaps.
- Makkah (21.426666, 39.831666): 2024-01-01 to 2030-12-31, 2557 days.
- Riyadh (24.67, 46.69): 2024 to 2030, 2557 days.
- Turaif (31.68, 38.66), far north: 2024, 2026 and 2028-2030, 1827 days.
- Madinah (24.54, 39.63), Jeddah (21.5, 39.17), Dammam (26.44, 50.1) and Abha (18.22, 42.51, high altitude): 2025-01-01 to 2027-12-31, 1095 days each.
- Haql (29.3, 34.95, far west), Al Kharkhir (18.85, 51.819, far east), At Tuwal (16.528, 42.9685, far south), Taif (21.25, 40.4, high altitude) and Tabuk (28.4, 36.58): 2026, 365 days each.

The data covers the Ramadans of 1445-1452: 212 Ramadan dates and 1080 Ramadan place-days. Ramadan 1447 (2026-02-18 to 2026-03-19, per the API's Hijri dates) is covered at all 12 places.

Files are in scratchpad/round/data-umm-al-qura/, named off-uq-<place>.txt. Each file's first line is a # comment naming the exact URLs and fetch times.

The data matches both second-hand copies exactly (Makkah 2026-09-20, Riyadh 2026-09-25).

### profile

This is the UQ reproduction profile. It needs code beyond adhan2 parameters. HIGH confidence unless marked otherwise.

Base: adhan2 0.0.7 raw times with no preset adjustments and no rounding (Rounding.NONE), Shafi Asr (shadow factor 1), no elevation correction.

Rounding: every start is rounded UP to the minute and sunrise is rounded DOWN. This is what the official table itself does: per place, official minus the model sits in a -5..+64 s window, which is ceil or floor plus a few seconds of ephemeris noise.

- FAJR: 18.5 deg, with the solar declination biased by ddec(doy) [MEDIUM].
  - ddec = -0.008390 + 0.032536 cos t + 0.323067 sin t - 0.046890 cos 2t + 0.030917 sin 2t + 0.003527 cos 3t + 0.019364 sin 3t (degrees), where t = 2*pi*(day_of_year - 0.5)/days_in_year.
  - The bias ranges from about -0.3 deg (Sep-Nov) to +0.37 deg (Mar-Apr) and is the same at every latitude.
  - Shown = adhan2 raw Fajr + [T(-18.5 deg, dec+ddec) - T(-18.5 deg, dec)] + 7.7 s, rounded up. The reference routine is solar.py event_ddec.
  - If a separate end-of-suhur time is shown, use ceil(model - 6.6 s).
- SUNRISE: adhan2 raw sunrise - 4 s, rounded down.
- DHUHR: adhan2 raw transit + 7 s, rounded up.
- ASR: own routine (solar.py asr_ddec) [MEDIUM on the 0.4 factor].
  - Shadow factor 1, with the declination taken at the Asr moment (iterated) plus 0.4*ddec(doy), in both the shadow altitude and the hour angle.
  - Then + 8.6 s, rounded up.
  - adhan2's own Asr takes the declination at 0h UT for the shadow length, which puts it up to about +-35 s off seasonally.
- MAGHRIB: adhan2 raw sunset + 7 s, rounded up.
- ISHA: raw sunset + 90 min, or 120 min on Umm al-Qura Ramadan dates (Hijri month 9 of the UQ calendar), + 7 s, rounded up. In every one of the 13,146 official rows, Isha equals official Maghrib + 90, or + 120 in Ramadan.
- LAG ROWS: on 17-20 dates a year in 2025-2030 the whole official row is the previous day's astronomy. There are none in 2024.
  - The dates are the same at every place: 11,658 decisive place-days, 0 disagreements.
  - They recur about every 29.43 days, but no single periodic rule fits 2025-2030, so a date list is needed: lag-dates-model.txt, 114 dates, location-independent.
  - On listed dates, show the later of the same-day and previous-day result for starts, and the earlier for sunrise. This stays never-early even if KACST stops the lag.
  - Regenerate the list yearly from one GetPrayerByYear call.

The margins are the in-sample never-early margins from the six requested cities in 2026 (Fajr +2.7, Sunrise floor(raw + 1.0 s), Dhuhr +2, Asr +3.6, Maghrib and Isha +2) plus 5 s safety.

A pure adhan2 alternative (no code change) is never-early only with margins fitted on all the data: 18.5 deg, 90/120 min, Fajr +53, Sunrise -44 (rounded down), Dhuhr +23, Asr +72, Maghrib and Isha +73 s, all rounded up.

### in sample

Training set: the six requested cities in 2026, 2190 place-days, with the final margins including the +5 s safety and the safe-side rule on lag dates.
- Every prayer: 0 days early, 0 sunrise days late.
- Fajr: 0 on 1883, +1 on 307.
- Sunrise: 0 on 1951, 1 min early on 239.
- Dhuhr: 0 on 1933, +1 on 257.
- Asr: 0 on 1907, +1 on 283.
- Maghrib and Isha: 0 on 1910, +1 on 280.

Ramadan 1447 Isha (Maghrib + 120) fits at all 12 places, with official minus raw within -42..+61 s, which is pure rounding up.

Current app: Fajr, Dhuhr, Asr and Maghrib are each 1 minute early on about 50% of all 13,146 place-days.

### held out result

Final profile (above), fitted on the six requested cities in 2026 and tested on 10,956 held-out place-days. The held-out set is:
- the six cities in 2025 and 2027;
- Makkah and Riyadh in 2024 and 2028-2030;
- Turaif in all its years;
- Haql, Al Kharkhir, At Tuwal, Taif and Tabuk in 2026.

Results:
- Fajr: 0 days early. Minutes late: 0 on 9417, +1 on 1538, +2 on 1. Fajr later than official (end-of-suhur risk) on 1539 days (14%), all +1 except one day at +2. With the separate suhur-end rule ceil(model - 6.6 s), suhur end is later than official on 0 days (0 min on 9609, 1 min early on 1334, 2 min early on 13).
- Sunrise: 0 days later than official. Minutes early: 0 on 9644, 1 on 1308, 2 on 4.
- Dhuhr: 0 days early. 0 on 9653, +1 on 1303.
- Asr: 0 days early. 0 on 9489, +1 on 1467.
- Maghrib: 0 days early. 0 on 9667, +1 on 1289.
- Isha: 0 days early. 0 on 9667, +1 on 1289, including every Ramadan day.

Without the extra 5 s safety (training margins only): 7 Fajr and 6 Asr days are 1 minute early out of 10,956, each by less than 1 s beyond the margin. The test set needs Fajr +3.6 s and Asr +4.1 s against +2.7 and +3.6 from training.

Pure adhan2 profile with constant margins fitted on the same training set: held-out early days are Fajr 52 (43 at Turaif), Dhuhr 3, Asr 3, Maghrib and Isha 6, and 2 sunrise days are late. Maghrib is +1 on 90% of days, Asr +2 on 11%, and Fajr is later than official on 6354 days (58%).

The app today (UMM_AL_QURA preset, via apptimes.sh, all 13,146 place-days):
- Fajr is 1-2 minutes early on 6955 days.
- Dhuhr is early on 6564, Asr on 6610 and Maghrib on 6631.
- Sunrise is 1 minute late on 6369.
- Isha is early on 7191, including all 1080 Ramadan place-days at 29-31 minutes early, because the preset has no 120-minute Ramadan rule.

### never early achievable

True

### explanation of wide bands

Official minus raw adhan2 (18.5 deg / 90 min) spreads 111-170 s per prayer. The explained part:
1. Rounding. Official starts are rounded up and sunrise down, which accounts for 60 s.
2. Lag rows. On 17-20 dates a year (2025-2030) the whole row is the previous day's solution, which adds up to one day's change (about +-60 s) at every prayer. Found by testing same-day against previous-day solutions over all places. The rows are identical from the day, month and Hijri-month endpoints, so it is not a table-building artefact of one endpoint.
3. Fajr. There is a smooth seasonal error of about -35 to +35 s at Makkah and -62 to +56 s at Turaif, growing with latitude.
   - A different fixed angle does not fit (best band 84-91 s).
   - A fixed-time declination does not fit (best at 21h UT, band 71-76 s).
   - Shifting the whole day does not fit (band 90-100 s).
   - What does fit is a declination bias that depends only on the date and is the same at every latitude, repeating year to year. Fitted as a 3-harmonic series, it leaves 60-69 s bands at all 12 places in 2024-2030.
4. Asr. adhan2 takes the declination at 0h UT for the shadow length. An Asr with the declination at the Asr moment already cuts the band from 101-122 s to 68-89 s. Adding 0.4 times the Fajr declination bias (factor chosen on training) gives 62-69 s.

Sunrise, Dhuhr and Maghrib with lag rows handled fit adhan2 within +-4 s, including at high-altitude Abha and Taif, so no elevation is used.

Note: Isha equals Maghrib + 90 min exactly, and + 120 min on the API's Hijri month 9 dates.

### recommendation

Compute with this profile (the UQ reproduction model). It is never early on every held-out place-day and matches the official minute on 86-88% of days, 1 minute late otherwise. Implementing it takes four pieces of code that the app and site share:
1. the Fajr declination-bias correction;
2. its own Asr routine;
3. the 120-minute Ramadan Isha, with Ramadan taken from the Umm al-Qura calendar dates;
4. a small, location-independent list of lag dates, regenerated each year from the open API (one call a year).

Fajr is two-sided. Either show a separate end-of-suhur time (ceil(model - 6.6 s), never later than official on held-out data) or accept that Fajr is 1 minute after official on about 14% of days.

If code changes are ruled out, compute with margin using pure adhan2 (18.5 deg, 90/120 min, Fajr +53, Asr +72, Maghrib/Isha +73, Dhuhr +23 s, Sunrise -44 s rounded down). It is never early in-sample, but Maghrib and Isha run 1-2 minutes late on 99% of days and Fajr is later than official on 72%.

Shipping the official data per city is possible, since the API is open. But the app computes for any GPS point and the terms of use were not readable (Cloudflare 403), so that is not recommended.

At minimum, the current app must add the 120-minute Ramadan Isha: today it shows Isha 30 minutes early on every Ramadan day.

### data licence or terms

Not found. The site bundle links a DGA platform-licence page (https://raqmi.dga.gov.sa/platforms/platforms/2029d845-2662-4cbf-774a-08ddf374f302/platform-license), but the fetch on 2026-09-25 was blocked by Cloudflare (403) and was not bypassed. No terms of use for the umqserv.kacst.gov.sa API were found.

### confidence

- HIGH: the data itself, which is official, complete and cross-checked across four endpoints and against two second-hand copies.
- HIGH: official rounding (starts up, sunrise down), no elevation correction, Shafi Asr, Isha = Maghrib + 90/120.
- HIGH: the existence and location-independence of the lag rows.
- HIGH: the app is early today, including Isha 30 minutes early in Ramadan.
- MEDIUM: the Fajr declination-bias series and the Asr factor 0.4. Both are empirical: fitted on 2026 at six cities and validated on 2024-2030 at 12 places, with the physical mechanism not identified.
- MEDIUM: the lag-date list for future years. It is a server quirk: absent in 2024, drifting relative to the Hijri months, and possibly fixed or changed by KACST. Hence the safe-side rule on listed dates and the yearly regeneration.
- LOW: behaviour outside Saudi Arabia or outside 2024-2030 (not tested).

## data-diyanet-kemenag

**Authority.** Türkiye: Diyanet İşleri Başkanlığı (namazvakitleri.diyanet.gov.tr tables, vakithesaplama.diyanet.gov.tr method pages). Indonesia: Kemenag / Bimas Islam schedules, taken from the open api.myquran.com v2, which republishes them. bimasislam.kemenag.go.id and kemenag.go.id timed out from this machine on 2026-09-25, so myQuran could not be checked against the primary site.

### sources

- https://namazvakitleri.diyanet.gov.tr/tr-TR/9541 (İstanbul) fetched 2026-09-25
- https://namazvakitleri.diyanet.gov.tr/tr-TR/9206 (Ankara) fetched 2026-09-25
- https://namazvakitleri.diyanet.gov.tr/tr-TR/9879 (Tekirdağ) fetched 2026-09-25
- https://namazvakitleri.diyanet.gov.tr/tr-TR/9146 (Adana) fetched 2026-09-25
- https://namazvakitleri.diyanet.gov.tr/tr-TR/20089 (Hatay) fetched 2026-09-25
- https://namazvakitleri.diyanet.gov.tr/tr-TR/9587 (Karaman) fetched 2026-09-25
- https://namazvakitleri.diyanet.gov.tr/tr-TR/9419 (Edirne) fetched 2026-09-25
- https://namazvakitleri.diyanet.gov.tr/tr-TR/9352 (Çanakkale) fetched 2026-09-25
- https://namazvakitleri.diyanet.gov.tr/tr-TR/9354 (Gökçeada, saved but not analysed: no coordinates in the app's city list) fetched 2026-09-25
- https://namazvakitleri.diyanet.gov.tr/tr-TR/9930 (Van) fetched 2026-09-25
- https://namazvakitleri.diyanet.gov.tr/tr-TR/9507 (Hakkari) fetched 2026-09-25
- https://namazvakitleri.diyanet.gov.tr/tr-TR/9594 (Kars) fetched 2026-09-25
- https://namazvakitleri.diyanet.gov.tr/tr-TR/9451 (Erzurum) fetched 2026-09-25
- https://namazvakitleri.diyanet.gov.tr/tr-TR/9522 (Iğdır) fetched 2026-09-25
- https://namazvakitleri.diyanet.gov.tr/tr-TR/home/GetRegList?ChangeType=state&CountryId=2&StateId=<id> (public dropdown endpoint used to find district ids) fetched 2026-09-25
- https://vakithesaplama.diyanet.gov.tr/temkin.php (documented temkin: sunrise -7, maghrib +7, dhuhr +5, asr +4, imsak 0, isha 0) fetched 2026-09-25
- https://vakithesaplama.diyanet.gov.tr/vakit_kiyaslamalari.php (Diyanet's own coordinates for İstanbul 41.012/28.974, Ankara 39.938/32.848, Tekirdağ 40.973/27.511, Adana 36.987/35.326, Hatay 36.2/36.153, Karaman 37.181/33.214) fetched 2026-09-25
- https://vakithesaplama.diyanet.gov.tr/imsak.php (imsak = first appearance of dawn; no angle stated) fetched 2026-09-25
- https://api.myquran.com/v2/sholat/jadwal/1301/2026/01..12 (KOTA JAKARTA) fetched 2026-09-25
- https://api.myquran.com/v2/sholat/jadwal/0119/2026/01..12 (KOTA BANDA ACEH) fetched 2026-09-25
- https://api.myquran.com/v2/sholat/jadwal/2622/2026/01..12 (KOTA MAKASSAR) fetched 2026-09-25
- https://api.myquran.com/v2/sholat/jadwal/3329/2026/01..12 (KOTA JAYAPURA) fetched 2026-09-25
- https://api.myquran.com/v2/sholat/jadwal/1709/2026/01..12 (KOTA DENPASAR) fetched 2026-09-25
- https://api.myquran.com/v2/sholat/jadwal/1219/2026/01..12 (KOTA BANDUNG) fetched 2026-09-25
- https://api.myquran.com/v2/sholat/jadwal/3308/2026/01..12 (KAB. JAYAWIJAYA / Wamena) fetched 2026-09-25
- https://api.myquran.com/v2/sholat/jadwal/1630/2026/01..12 (KOTA BATU) fetched 2026-09-25
- https://api.myquran.com/v2/sholat/jadwal/0313/2026/01..12 (KOTA BUKITTINGGI) fetched 2026-09-25
- https://api.myquran.com/v2/sholat/jadwal/1634/2026/01..12 (KOTA MALANG) fetched 2026-09-25
- https://api.myquran.com/v2/sholat/jadwal/1222/2026/01..12 (KOTA BOGOR) fetched 2026-09-25
- https://api.myquran.com/v2/sholat/jadwal/1208/2026/01..12 (KAB. GARUT) fetched 2026-09-25
- https://api.myquran.com/v2/sholat/jadwal/1429/2026/01..12 (KAB. WONOSOBO) fetched 2026-09-25
- https://api.myquran.com/v2/sholat/kota/cari/<keyword> (id lookup) fetched 2026-09-25
- https://bimasislam.kemenag.go.id/jadwalshalat: not reachable (connection timed out), 2026-09-25
- Coordinates: the app's shared/src/commonMain/composeResources/files/cities.csv (GeoNames cities15000), read-only

### places and days

DIYANET: 13 districts analysed, each with 396 days from 2026-09-25 to 2027-12-31 (the page's monthly table plus its full-year 2027 table), 5,148 place-days in total. West: Edirne, Çanakkale, Tekirdağ, İstanbul. Centre and south: Ankara, Karaman, Adana, Hatay. East: Van, Hakkari, Kars, Iğdır. High: Erzurum. Gökçeada was fetched but not analysed. The site offers only weekly, 30-day and one-year (2027) views, so no earlier months were available. KEMENAG: 13 kota/kabupaten, each with the full year 2026-01-01..2026-12-31 (365 days), 4,745 place-days in total. Far west: Banda Aceh. Far east: Jayapura. Others: Jakarta, Makassar, Denpasar, Malang, and highland places Bandung, Jayawijaya (Wamena), Batu, Bukittinggi, Bogor, Garut, Wonosobo. myQuran's Jakarta Sep/Oct 2026 rows are identical to the earlier saved off-kemenag-jakarta.txt. Imsak = Subuh - 10 min on all 4,745 days.

### profile

DIYANET (HIGH). adhan2 cannot reproduce Diyanet with constant offsets. The profile that works is Diyanet's own algorithm, which I identified. For each civil date D, compute the sun's declination d and equation of time E once, at 0h UT of D, using the Meeus low-accuracy formulas in round/data-diyanet-kemenag/sun.py. Then: transit T = 12h - lon/15 - E + zone, and H(h) = acos((sin h - sin lat sin d)/(cos lat cos d)).
- Imsak/Fajr = T - H(-18)
- Sunrise = T - H(-0.8333) - 7 min
- Dhuhr = T + 5 min
- Asr = T + H(acot(1 + tan|lat - d|)) + 4 min (Shafi, asr-i evvel)
- Maghrib = T + H(-0.8333) + 7 min
- Isha = T + H(-17)
No elevation term, no Ramadan rule, no high-latitude rule needed. Diyanet itself rounds to the nearest minute. App rule with a 10 s safety:
- starts = ceil(x - 20 s)
- sunrise = floor(x + 20 s)
- end of suhur (imsak) = floor(x_fajr + 20 s), shown separately from the Fajr start
For comparison, the best never-early adhan2 profile at app coordinates, fitted on İstanbul 2027 (18/17, Shafi): Fajr -23 s up, Sunrise -409 s down, Dhuhr +274 s up, Asr +284 s up, Maghrib +454 s up, Isha +56 s up. It fails on held-out data.

KEMENAG (lowland HIGH, highland MEDIUM). adhan2 with Fajr 20 deg, Isha 18 deg, Asr Shafi. Sunrise and Maghrib are NOT adhan2's sunrise/sunset: they are the adhan2 twilight times with angle 1.0 deg (lowland) or 2.0 deg (highland class), i.e. fajrAngle = ishaAngle = 1 or 2. Shipping offsets, which cover all 4,745 place-days:
- Fajr +148 s, round up
- Sunrise -123 s, round down
- Dhuhr +198 s, round up
- Asr +158 s, round up
- Maghrib +151 s, round up
- Isha +149 s, round up
- Imsak = floor(raw Fajr + 177 s) - 10 min
Jakarta-only fit: Fajr +129, Sunrise -123, Dhuhr +189, Asr +153, Maghrib +141, Isha +147 s. Measured highland (2 deg) class: Bandung, Jayawijaya, Batu, Bukittinggi, Bogor, Garut, Wonosobo. Lowland (1 deg): Jakarta, Banda Aceh, Makassar, Denpasar, Jayapura, Malang. One special case: on 21-22 March 2026 Kemenag's times run 20-30 s later than its own pattern at 8 or more places, and the shipping margins are sized to cover this.

### in sample

DIYANET, recommended rule (s=10), all 13 districts: see held-out (c); the only fitted value is s. Fitted margins on the 5-district fit set (1,980 days): 0 early; one minute late Fajr 236, Dhuhr 182, Asr 172, Maghrib 220, Isha 217; sunrise 1 min early on 256. adhan2 constant offsets on İstanbul 2027 (365 days): 0 early; minutes late Asr 0:102 1:89 2:139 3:35, Maghrib 0:99 1:112 2:154, Isha 0:92 1:74 2:126 3:73.

KEMENAG, Jakarta 2026 (365 days): 0 early; one minute late Fajr 44, Dhuhr 24, Asr 182, Maghrib 105, Isha 162; sunrise 1 min early on 34. Shipping profile on all 13 places (4,745 days): 0 early, 0 sunrise later; one minute late Fajr 1,993, Dhuhr 1,240, Asr 2,760, Maghrib 2,270, Isha 2,092; sunrise 1 min early on 317.

### held out result

DIYANET
(a) adhan2 constant offsets. Fit on İstanbul 2027; test on 4,783 place-days (the other 12 districts plus İstanbul 2026).
- Days early: Fajr 97, Dhuhr 160, Asr 61, Maghrib 30, Isha 17. Sunrise later: 0.
- Minutes late: Fajr 0:4235 1:451 | Dhuhr 0:4379 1:244 | Asr 0:1593 1:1287 2:1704 3:138 | Maghrib 0:1657 1:1707 2:1389 | Isha 0:1303 1:1150 2:1905 3:408.
- Fajr later than official: 451 days.
- Seasonal split (fit Jan-Jun 2027 on all 13 districts, test the rest, n=2,795): Asr 1,984, Maghrib 1,755 and Isha 1,814 days early; sunrise later on 334 days.

(b) Diyanet algorithm with margins fitted on İstanbul, Ankara, Tekirdağ, Edirne and Van (1,980 days; starts x-22..-24 s up, sunrise x+23 s down). Test on the other 8 districts x 396 = 3,168 days.
- Days early: Fajr 2, Asr 1 (all in Çanakkale); Dhuhr, Maghrib, Isha 0. Sunrise later: 0.
- Minutes late: Fajr 0:2945 1:221 | Dhuhr 0:2968 1:200 | Asr 0:2972 1:195 | Maghrib 0:2918 1:250 | Isha 0:2821 1:347. Sunrise 1 min early on 534 days.
- Fajr later than official: 221 days.

(c) Recommended rule, s=10 s, all 13 districts x 396 = 5,148 days at app coordinates.
- 0 days early for every start; 0 days of sunrise later.
- One minute late: Fajr 620, Dhuhr 727, Asr 542, Maghrib 644, Isha 672 (all others exact). Sunrise 1 min early on 1,052 days.
- Imsak rule: 0 days later, 1,078 days 1 min earlier. Fajr start later than official imsak: 620 days, which is why imsak must be shown separately.

Current app (TURKEY preset), for comparison:
- Days early: Asr 2,071, Maghrib 1,874, Isha 2,049, Fajr 315, Dhuhr 293.
- Sunrise later: 310 days.

KEMENAG
Current app (SINGAPORE preset), on every one of the 4,745 days:
- Fajr, Dhuhr, Asr and Isha are 1-3 min early (e.g. Fajr 2 min early on 4,547).
- Maghrib is 2-8 min early.
- Sunrise is 3-9 min late.

(a) Fit on Jakarta 2026; test on 12 places x 365 = 4,380 days, with the highland flag.
- Days early: Fajr 2, Dhuhr 5, Asr 1, Maghrib 3, Isha 1, all on 21-22 Mar 2026. Sunrise later: 0.
- Minutes late: Fajr 0:3896 1:482 | Dhuhr 0:3861 1:514 | Asr 0:2191 1:2188 | Maghrib 0:2971 1:1406 | Isha 0:2592 1:1787. Sunrise 1 min early on 283 days.
- Fajr later than official subuh: 482 days.
- Without the highland flag (1 deg everywhere): Maghrib 3-5 min early on 2,557 days and sunrise later on 2,555 days, i.e. every day at all 7 highland places.

(b) Fit Jan-Jun on all 13 places; test Jul-Dec (n=2,392).
- 0 early, 0 sunrise later.
- One minute late: Fajr 994, Dhuhr 606, Asr 1,389, Maghrib 1,137, Isha 1,041.

(c) Leave one place out (4,745 days): 1 early day per prayer and 1 sunrise-later day, all on 21-22 Mar.

(d) End of suhur, fitted on Jakarta: imsak later than official on 3 of 4,380 days; the all-data margin of 177 s gives 0.

### never early achievable

True

### explanation of wide bands

DIYANET: with adhan2 at 18/17, official minus raw spans up to 230 s (Isha), 212 s (Asr) and 176 s (Maghrib), and the pattern is seasonal: Maghrib runs from +376 s (Feb) to +492 s (Sep). Tested explanation: Diyanet uses one sun declination and equation of time per day, taken at 0h UT, while adhan2 interpolates to each event's own moment. An independent model built that way reduces every band to about 61-64 s at all 13 districts, so no elevation term is needed (Erzurum, Kars and Van fit the same way), with temkin 0/-7/+5/+4/+7/0 and rounding to nearest. Interpolating adhan2 between day D-1 and D fixes Fajr, sunrise, Dhuhr, Maghrib and Isha to about 65 s but not Asr (about 145 s), because adhan2's Asr already uses the day's 0h declination for the shadow altitude.

KEMENAG: sunrise and Maghrib bands are shifted, not wide. Kemenag uses a horizon of -1.0 deg in lowland places and -2.0 deg in highland places, fitted to within about 0.02 deg, against adhan2's -0.833 deg. At highland places this moves both times about 4.3 min. The remaining 65-90 s bands come from rounding up plus about 5-15 s of per-day noise, and from the 21-22 March 2026 anomaly: 20-30 s late at Jakarta, Banda Aceh, Makassar, Denpasar, Bandung, Batu, Bukittinggi, Bogor and Malang. No fixed sun-position instant tightens the Kemenag bands.

### recommendation

DIYANET: adhan2 constant offsets cannot match Diyanet. The never-early fit is 2-3 min late on about 40-50% of days for Asr, Maghrib and Isha, and it breaks across seasons and places. The recommendation is to compute with Diyanet's own algorithm (sun position once per day at 0h UT, documented temkin), in shared Kotlin so app and site stay identical. Round starts up with a 10 s safety, sunrise down, and show imsak separately with the round-down rule. On 5,148 place-days that gave 0 early days and at most +1 minute (about 10-14% of days).
- Use Diyanet's own district points where known: in the fit, a GeoNames point a few seconds east of Diyanet's point was enough to cause early days.
- Tables are per district (ilçe). Computing at a GPS position east of the district point will be earlier than the table (4 min per degree of longitude), so snap to the district point or add margin.
- Shipping the official tables is the fallback. The Diyanet pages say "all rights reserved", so that needs permission.

KEMENAG: compute with the adhan2 profile above (20/18 Shafi, +148/-123/+198/+158/+151/+149 s, starts up, sunrise down, sunrise and maghrib at a 1 or 2 deg horizon). It is never early with at most +1 minute, but only if each kab/kota carries Kemenag's highland flag. The flag cannot be inferred from any elevation data I have: Malang is 1 deg while Bogor is 2 deg. It would have to be derived per kota from Kemenag data, for example with the h0 fit in km5.py over all myQuran ids, or the official data shipped. The margins also include about 25 s just to cover the 21-22 March anomaly, which is why +1 minute is common (26-58% of days).

The current app presets fail both authorities badly, Indonesia most of all. That is consistent with keeping the city pages down until this round finishes, as the user asked; this agent made no repository changes.

### data licence or terms

vakithesaplama.diyanet.gov.tr footer: "© 2021 T.C. Diyanet İşleri Başkanlığı. Her hakkı saklıdır." (all rights reserved). Terms for namazvakitleri.diyanet.gov.tr: not found. myQuran API terms: not found. Kemenag site: not reachable, terms not found. Shipping either authority's tables would need their permission; computing from the published method does not copy the tables.

### confidence

HIGH that the Diyanet algorithm is identified exactly. At the 6 points whose coordinates Diyanet itself publishes, official minus model stays within -30..+34 s for all six prayers on all 396 days, which is pure rounding plus about 4 s of model noise. The sun-position instant fits best at 0h UT (plus or minus 0.5 h), and the temkin matches Diyanet's own page. HIGH for the Kemenag lowland profile. MEDIUM for the Kemenag highland class (measured on 7 places; membership rule unknown) and for myQuran's fidelity, since the primary Kemenag site was unreachable. HIGH for the baselines of the current app presets, which were run through apptimes.sh; the app maps TR to TURKEY and ID to SINGAPORE in CalculationMethodDefaults.kt.

## data-maghreb-libya

**Authority.** Four Maghreb authorities. LIBYA: Dar al-Ifta (api.ifta.ly) and the General Authority of Awqaf and Islamic Affairs (awqaf.gov.ly, 22 cities). Both publish the same Tripoli times. MOROCCO: Ministry of Habous and Islamic Affairs (habous.gov.ma/prieres). ALGERIA: Ministry of Religious Affairs and Waqfs (MARW), official 1448 AH calendars (Alger, Djelfa, Adrar PDFs). TUNISIA: Institut National de la Météorologie (meteo.tn), per delegation. Scope note: this was a read-only data task, so I did not take down the 51 city pages the user asked about. Going only by the git-status snapshot, commit ac9f53a on branch hold-city-pages-2 already holds every city page; I did not verify the live site.

### sources

- https://api.ifta.ly/api/v1/prayer-time (Dar al-Ifta, Tripoli, 8 days 2026-09-25..10-02; fetched 2026-09-25, re-fetched same day, identical; ignores every query parameter)
- https://awqaf.gov.ly/sovinel/admin-ajax.php POST action=get_prayer_times&city=<22 cities> (the public widget of https://awqaf.gov.ly/; today only; fetched 2026-09-25T21:29Z for 2026-09-25 and 2026-09-25T22:04Z for 2026-09-26, the date follows Libyan local midnight)
- https://web.archive.org/web/<ts>id_/https://awqaf.gov.ly/ : 19 captures with the Tripoli widget, 20250323181311 .. 20260903064411 (fetched 2026-09-25; list in ly_wb.log)
- https://ifta.ly/?p=23131 (Dar al-Ifta statement of 2022-02-08 on Fajr time, via the agentB copy agentB/ifta_23131.json)
- https://www.habous.gov.ma/prieres/index.php?ville=58,1,31,14,156,165,33,100,166,136 (Casablanca, Rabat, Oujda, Tangier, Laayoune, Dakhla, Figuig, Ifrane, Lagouira, Midelt; current Hijri month 2026-09-13..10-12 in UTC+0; fetched 2026-09-25)
- https://web.archive.org/web/<ts>id_/https://www.habous.gov.ma/prieres/horaire_hijri_2.php?ville=<id> and .../horaire_hijri.php?ville=<id> : 2015-2025 captures (fetched 2026-09-25; city taken from the page's selected option; per-file URLs in each off-habous-*.txt header)
- https://marw.gov.dz/media/calendrier/1448/Alger.pdf (fetched 2026-09-25, byte-identical to agentB/alger1448.pdf)
- https://marw.gov.dz/media/calendrier/1448/Djelfa.pdf (fetched 2026-09-25)
- https://marw.gov.dz/media/calendrier/1448/Adrar.pdf (fetched 2026-09-25)
- https://www.meteo.tn/horaire_gouvernorat/<yyyy-mm-dd>/<gouv>/<deleg> (INM prayer times, one request per day, all of 2026: Tunis 342/615, Sfax 359/632, Tataouine 351/624 daily; Tabarka 354/497, Tala 362/573, Ben Guerdane 353/488 every 3rd day; fetched 2026-09-25)
- https://www.meteo.tn/lever_coucher_gouvernorat/<yyyy-mm-dd>/<gouv>/<deleg> (INM sunrise/transit/sunset, every 3rd day of 2026, Tunis, Sfax, Tataouine, Tala; fetched 2026-09-25)

### places and days

All tables are in /private/tmp/claude-501/-Users-mohamedabulgasem-Desktop-Workspace-apps/d2f2d5b2-1f80-4ac1-b4d2-f0b36f6f4636/scratchpad/round/data-maghreb-libya/.

LIBYA:
- Tripoli, 27 days. 8 reliable consecutive days (2026-09-25..10-02) plus 19 Wayback widget days, one or two per month, 2025-03-23..2026-09-03. The widget days may be up to 2 days stale because of page caching: a date-shift test fits several of them better at -1 or -2 days.
- 21 other awqaf.gov.ly cities, 2 days each (2026-09-25/26): Benghazi, Sabha, Tobruk, Misrata, Ghadames, Kufra, Derna, Sirte, Bayda, Zawiya, Hun, Jalu, Emsaed, Ajdabiya, Khoms, Marj, Bani Walid, Ras Lanuf, Zliten ('Zaleaten'), Brega, Awjila.
- No multi-month data exists for any city other than Tripoli. The API serves 8 days of Tripoli only.

MOROCCO, 1,466 place-days. The live month 2026-09-13..10-12 for 10 places, plus Wayback months:
- Rabat: default page 480 days 2019-04..2022-02, plus 90 days 2022-26
- Casablanca 210 (2015, 2017, 2022, 2025, 2026)
- Laayoune 236 (2019-2026)
- Figuig 120 (2019, 2020, 2026)
- Oujda, Tangier, Dakhla, Lagouira, Midelt: 60 each (Jul-Aug 2022 + 2026)
- Ifrane (1665 m): 30

ALGERIA, full Hijri year 2026-06-16..2027-06-05 (355 days each):
- Algiers, Djelfa (1140 m) and Adrar (far south-west): OCR of the calendars. 36 misread cells were fixed by eye; all 29 Algiers days of Rabi II match agentB's independent transcription.
- Oran (far west) and Tamanrasset (far south, 1320 m): built exactly as MARW publishes them. Oran = Algiers + the page's monthly difference row; Tamanrasset = Adrar + half-monthly rows.

TUNISIA (INM's own reference coordinates are in every response):
- Tunis, Sfax, Tataouine: all 365 days of 2026
- Tabarka (far north-west), Tala (~1000 m), Ben Guerdane (far east): 122 days each
- Sunrise: 122 days each for Tunis, Sfax, Tataouine, Tala

### profile

Margins are in seconds and are added to adhan2 raw times. Starts are then rounded UP, sunrise DOWN. The Asr figures use a first-principles standard Asr (see below). Standard shadow-1 Asr everywhere (Shafi; Maliki is the same).

Common finding: adhan2 0.0.7's raw Asr differs from first-principles Asr by +34 s (Feb-Mar) to -37 s (Sep-Oct). Its other times are within 6 s (adhan2_vs_true.py). Every authority fits better once this is corrected.

MOROCCO:
- Fajr 19°, Isha 17°.
- National margins (fitted on 6 places, 5 s safety added): Fajr -33, Dhuhr +295, Asr +7, Isha -2. With adhan2's own Asr the margin would be +32.
- Sunrise and Maghrib cannot be national; Habous corrects them per city for elevation. Never-early margins across all 11 places would be sunrise -435 and Maghrib +451.
- Per-city margins fitted on the live month (sunrise / Maghrib): Rabat -142/+202, Casablanca -151/+247, Laayoune -110/+203, Tangier -211/+255, Oujda -290/+335, Figuig -314/+350, Midelt -410/+424, Dakhla -138/+179, Lagouira -112/+197. Each needs about 25 s more safety.
- No Ramadan rule seen. The live table is UTC+0; archived tables follow the legal clock.

ALGERIA (base cities):
- Fajr 18°, Isha 17°. MARW rounds everything UP, sunrise included.
- Margins over Algiers + Djelfa + Adrar: Fajr +9, Sunrise +51, Dhuhr +4, Asr +10, Maghrib +188, Isha +14. Add ~5 s safety.
- No elevation correction: Djelfa at 1140 m has the same Maghrib offset as Algiers.
- MARW's Asr matches a one-shot formula using the Sun at 12h UT (band 68 s).
- Oran and the other associated wilayas = base city + the ministry's published monthly (Adrar group: half-monthly) minute differences. To match them, compute the base city and add that table (58 wilayas × 12 months × 6 prayers).

TUNISIA:
- Fajr 18°, Isha 18°. It is 18°, not 17°: 17° leaves Isha +297..+531 s.
- Margins over the 4 low places: Fajr -56, Dhuhr +402, Maghrib +141, Isha +37. Sunrise -8 (INM floors its sunrise).
- Asr: first-principles Asr plus a per-month table (Jan -57, Feb -58, Mar -45, Apr -38, May -35, Jun -35, Jul -15, Aug +7, Sep +20, Oct +10, Nov +9, Dec -20). INM's Asr definition is none of the variants I tested.
- INM applies a horizon-dip correction to Fajr, sunrise, Maghrib and Isha at elevated delegations: about 0.45° at Tataouine and 1.0° at Tala. That needs elevation or per-delegation offsets.

LIBYA:
- Fajr 18.5°, Isha 18.3°.
- Margins from the 8 API days: Fajr -31, Sunrise -17, Dhuhr +148, Asr -62, Maghrib +179, Isha -35.
- National never-early margins (22 cities, 5 s safety): Fajr +20, Sunrise -64, Dhuhr +214, Asr -5, Maghrib +242, Isha +27.

### in sample

Fit bands on the training data. Values are official minus raw in seconds; for Asr, official minus first-principles Asr.
- Rabat, 560 days: Fajr -57..+3, sunrise -204..-146, Dhuhr +273..+332, Asr about -29..+33, Maghrib +204..+279, Isha -26..+34.
- Algiers, 355 days: Fajr -6..+68, sunrise -3..+67, Dhuhr +1..+63, Asr -7..+68, Maghrib +176..+247, Isha -6..+73.
- Tunis, 365 days: Fajr -72..+3, sunrise -62..+3, Dhuhr +380..+460, Asr -67..+74, Maghrib +117..+183, Isha (18°) -2..+73.
- Tripoli API, 8 days: Fajr -34..+28, sunrise -76..-34, Dhuhr +167..+207, Asr -56..-3, Maghrib +197..+238, Isha -24..+24.

### held out result

MOROCCO: margins fitted on 6 places, tested on Tangier, Figuig, Ifrane, Midelt and Lagouira (330 place-days).
- Fajr: 0 early; late 0 min 222, +1 108. Fajr later than official 108/330.
- Dhuhr: 0 early; 197 / 133.
- Asr: 0 early; 153 / 177.
- Isha: 0 early; 208 / 122.
- Maghrib: 92 early (-1: 84, -2: 8). Late 0:98, +1:39, +2:46, +3:55.
- Sunrise: later than official on 114 days (up to 3 min at Midelt, Ifrane and Figuig); up to 4 min earlier at Lagouira.
- With never-early margins over all 11 places, Maghrib is 3-5 min late on 81% of place-days and sunrise up to 6 min early.
- Per-city sunrise/Maghrib fitted on one month and tested on other seasons still gives 1-min errors: Casablanca Maghrib 38/180 early, sunrise later 46/180.

ALGERIA: fitted on Algiers Jun-Dec.
- Algiers Dec-Jun (177 days): early days Fajr 1, Dhuhr 1, others 0; sunrise later 0. Late +1: Fajr 17, Asr 44, Maghrib 34, Isha 41. Fajr later than official 17.
- Adrar (355): 0 early; sunrise later 9; +1 on 7-26%; Fajr later 67.
- Djelfa (355): Dhuhr 5 early, others 0; Fajr later 31.
- With margins over the three base cities (1065 days): 0 early, 0 sunrise-later; +1 on 5-21%.
- Oran with the profile: early on 23-75% of days per prayer (Fajr 265/355, 1-3 min). With its own margins: late 0-3 min.
- Tamanrasset: Fajr early 291/355; with its own margins late up to 3 min (Asr up to 6).

TUNISIA: fitted on Tunis Jan-Jun, 5 s safety.
- Tunis Jul-Dec (184 days): 0 early for every prayer. +1: Fajr 15, Dhuhr 27, Asr 36, Maghrib 36, Isha 51.
- Sfax (365): Maghrib 48 early, Isha 25, Asr 2; Fajr +1 on 169 days (later than official 169).
- Tabarka (122): Maghrib 18 early, Isha 10.
- Ben Guerdane (122): Asr 1 early.
- Sunrise: Sfax later on 20/122 days.
- Tataouine: Maghrib and Isha early 365/365 (1-3 min); Fajr 2-3 min late every day; sunrise 1-2 min later on 122/122 days.
- Tala: Maghrib 4-5 min early, Isha 4-7 min early, Fajr 4-7 min late, sunrise 4-5 min later, on every day.
- With a constant Asr offset, Asr is early on 99/184 held-out days.

LIBYA: fitted on the 8 API days.
- 21 cities (42 city-days): early days Fajr 15, Dhuhr 22, Maghrib 22, Isha 10, Asr 12. Four of the Asr early days are Jalu and Awjila, whose official Asr is 7.5 min later than astronomy.
- With national margins: 0 early on the Tripoli and other-city days; +1 on about 55-75% of city-days.
- On the Wayback days: 0-2 early per prayer; late up to 3 min for Maghrib and 4 min for Isha.

### never early achievable

False

### explanation of wide bands

Tested explanations for bands wider than 60 s:

1. adhan2 Asr: adhan2 minus first-principles Asr runs from +34 s in Feb-Mar to -37 s in Sep-Oct. It explains the 127-143 s Asr bands at Habous (60 s after correction) and MARW (76 s after correction).
2. INM Asr: still 134-141 s wide after correction. It is smooth by month but none of the tested definitions fits: true, adhan2, mirrored error, declination at 0h UT, one-shot formula at 12h UT. Hence the monthly table.
3. Habous sunrise/Maghrib: offsets grow with city elevation. Maghrib goes from +224 s at Lagouira to +469 s at Midelt; Fajr and Isha are unaffected. Reference-point scatter between cities is about ±20 s, visible in Dhuhr.
4. INM Tataouine/Tala: a horizon dip of ~0.45° and ~1.0° reproduces Fajr, Isha and Maghrib together (elev_test.py).
5. MARW derived cities: the official is base city + a constant monthly difference, so the band is 140-380 s. It is not an astronomical computation for that city.
6. MARW base cities: 62-76 s bands, i.e. ceiling rounding plus a few seconds of algorithm difference. Every value outside a strict 60 s window was checked by eye against the page image.
7. Libya: the 8 API days give clean 40-62 s bands. The Wayback days spread 84-283 s (Isha official earlier than 18.3° in spring and summer). The causes are cached widget pages (the best date shift is -1 or -2 days for 7 of 19 captures) and possibly a fixed observation-based day/month table; not resolved.
8. Morocco archive: 22 archived days were dropped. Their Dhuhr sat more than 45 s off the city median (another city or another date).

### recommendation

No single computed national profile passes the standard for any of the four countries. Per authority:

ALGERIA: compute with this profile for Algiers, Djelfa and Adrar (never-early, 0 or +1 min). For every other wilaya, replicate MARW's own method: base city plus the published monthly or half-monthly difference table. Computing Oran or Tamanrasset directly is 1-3 min early on most days. The calendar pages say it may not be printed or reproduced without the ministry's permission, so ask before shipping the tables.

MOROCCO: compute Fajr/Dhuhr/Asr/Isha with the national profile (never early, +1 on 33-54% of days). Sunrise and Maghrib need per-city elevation offsets or official data. National margins make Maghrib 3-5 min late.

TUNISIA: the profile works at low delegations: Maghrib/Isha ~1 min early on 5-15% of days at Sfax and Tabarka until ~15-20 s more margin is added, then +1 on 20-40%. Asr needs the monthly table. Elevated delegations need INM's horizon-dip (elevation) handling or official data.

LIBYA: ship the official table. It is keyed by day and month only, which suggests a fixed yearly table. Per Dar al-Ifta it rests on a 1992-94 observation committee. Computing needs about +1 min margin, and the seasonal behaviour is unproven.

Fix adhan2's Asr for every authority.

Fajr is "later than official" (suhur risk) on 5-50% of days wherever margins are added.

### data licence or terms

- MARW calendars: each month page carries a footer that I read as saying the calendar may not be printed or reproduced without the ministry's permission (reading from the page image; not separately confirmed).
- habous.gov.ma, meteo.tn, api.ifta.ly and awqaf.gov.ly: licence or terms not found.

### confidence

- ALGERIA base cities: HIGH. Full year, OCR verified by eye, three cities agree.
- Oran and Tamanrasset derivation: HIGH (it is printed in the calendar).
- TUNISIA low places: HIGH (full year, INM's own reference coordinates).
- Tunisia elevation-dip explanation: MEDIUM-HIGH. The dip fits Fajr, Isha and Maghrib at once, but the elevation data INM uses was not found.
- Tunisia Asr monthly table: MEDIUM (one year, the mechanism is unknown).
- MOROCCO starts: MEDIUM-HIGH (1,466 place-days over 2015-2026, but city reference points are unpublished).
- Morocco sunrise/Maghrib per city: MEDIUM (few seasons for most cities).
- LIBYA: LOW-MEDIUM. Only 8 reliable Tripoli days; Wayback dates uncertain by up to 2 days; other cities have 2 days each.
- Eastern and southern Libya: LOW. The 2022 Dar al-Ifta statement says the eastern Awqaf authority made mosques use a Fajr 15-20 min later than the official calendar. I found no current eastern table, so I cannot tell which Fajr eastern mosques follow now. If they use the later Fajr, awqaf.gov.ly's Benghazi and Tobruk Fajr would be 15-20 min early by the owner's standard.
- adhan2 Asr error (±37 s seasonal): HIGH.

## data-gulf-egypt

**Authority.** Four authorities (label data-gulf-egypt). EG: Egyptian General Authority for Survey (ESA), as republished by Dar al-Ifta ("according to the calculations of the Egyptian General Authority for Survey"). AE: Dubai IACAD for the Dubai zones (Dubai, Dubai Rural, Hatta), plus the federal Awqaf (GAIAE) for the other emirates; Awqaf's own site republishes IACAD's Dubai, Dubai Rural and Hatta times unchanged. KW: Ministry of Awqaf. I found no official table; the only data is a secondary newspaper box, Al-Anba, which names no source. QA: Qatar Calendar House (qatarch.com, "the official calendar of the State of Qatar"). I used its printed calendar and Ramadan imsakiya as primary data and its website header as secondary.

### sources

- https://www.dar-alifta.org/ar/Prayer/GetPrayer?town=<Arabic town name>: September 2026 month tables for 30 towns, fetched 2026-09-25. This is the public GET endpoint the page's own town picker calls.
- Wayback captures of https://www.dar-alifta.org/ar/prayer: Cairo month tables for Apr, May, Jun, Jul, Sep, Oct and Dec 2025 and Jan, Feb, Mar, Jun and Aug 2026. Fetched 2026-09-25.
- Wayback captures of https://www.esa.gov.eg/praytimes.aspx: all-cities daily table on 22 days from 2024-05-24 to 2026-06-12. Also the live page for 2026-09-25, saved earlier in scratchpad/agentB/esa.html. Fetched 2026-09-25.
- Wayback captures of https://www.iacad.gov.ae/Documents/Prayer%20Timings%20-%20Dubai%20{2022,2023,2024,2025}.xlsx, ...Dubai%20Rural%20{2022-2025}.xlsx and ...Hatta%20{2023-2025}.xlsx. The live site Cloudflare-blocks scripted access. Some copies came from moral-auth-mena/gulfA; the rest were fetched 2026-09-25.
- https://www.khaleejtimes.com/prayer-time-uae/{dubai,abu-dhabi,sharjah,ajman,umm-al-quwain,ras-al-khaimah,fujairah}: September 2026 tables, fetched 2026-09-25. The pages name no source.
- https://www.awqaf.gov.ae/prayer-times: public page read in a browser on 2026-09-25 with its city picker, 18 areas, one day only. I did not use the download button. https://mobileappapi.awqaf.gov.ae/APIS/v3/prayer-time/EmiratesAndCities?lang=en answers HTTP 401 and was not used. The page's local cache is encrypted and was not decrypted.
- Wayback captures of https://www.alanba.com.kw/prayers/: 25 snapshots from 2019 to 2026 that state their own date. The HTML was cached by another agent in moral-auth-mena/gulfA/alanba-cache; I read it 2026-09-25. The page names no source.
- https://www.qatarch.com/public/uploads/orginal/emsakia/emsakya.pdf: Ramadan 1447 imsakiya for Doha, fetched 2026-09-25. I transcribed it from the rendered page because the PDF text layer drops digits. My transcription matches scratchpad/off-qatar-doha-ramadan1447.txt on 30 of 30 days.
- Qatar Calendar House monthly calendar PDFs 1) Al moharram 1448, 2) Safar 1448 and 3) Rabia I 1448. The site links 'https://www.qatarch.com/public/uploads/orginal/documents/3) Rabia I 1448.pdf'; I read copies saved in moral-auth-mena/gulfB/qch on 2026-09-25. The 1447 PDFs are images only and were not parsed.
- Wayback captures of https://www.qatarch.com/*: every page header embeds `var prayData` with today's times for 13 cities. 50 days with data from 2023-02 to 2026-06, fetched 2026-09-25.

### places and days

EG: I built 31 place files in the standard line format. With coordinates from the app's cities.csv: Cairo 409 days (2024-05 to 2026-09 across 23 months; 402 of them from 2025 on), and 45 to 52 days each (22 ESA days plus all of September 2026) for Alexandria, Aswan, Marsa Matruh, El Arish, Sallum and Sidi Barrani (far west), Siwa, Halaib (far south-east), Rafah (far east), Sharm, Hurghada, Kharga, Mut, Bawiti, Luxor, Qusair, Port Said, El-Tor, Edfu, El Dabaa, Damietta, Ras Gharib and Suez. With no app coordinates, used only for the elevation check: Saint Catherine (about 1,600 m), Taba, Shalatin, Toshka, Farafra, Nuweiba and Dahab.

AE: IACAD Dubai 1,273 days (2022, 2023 and 2024 full years, plus Jan-Jun 2025), Hatta 908 days, Dubai Rural 1,273 days. The three year files are identical by month and day, so it is a perpetual table. I mapped it onto 2026-2029 dates and marked those files DERIVED. Khaleej Times has 30 days for each of 7 emirates. Awqaf has one day (2026-09-25) for 18 areas: Abu Dhabi, Al Ain, Jebel Hafeet peak, Liwa, Sila (far west), Ruwais, Das island, Zayed City, Dubai, Dubai Rural, Hatta, Sharjah, Ajman, Umm Al Quwain, Ras Al Khaimah, Jebel Jais (high altitude), Fujairah and Dibba.

KW: Kuwait City only, 25 dated days from 2019 to 2026, 10 of them from Dec 2025 to Jul 2026. Some days are stale (2023-01-18 and 01-19 are identical).

QA: Doha printed calendar 88 days (16 Jun to 11 Sep 2026) plus the Ramadan 1447 imsakiya, 30 days (18 Feb to 19 Mar 2026). The website header gives 50 days from 2023 to 2026 for Doha and other zones. As a test, I evaluated the Doha table at Al Khor and Al Wakrah coordinates, because the calendar tells east-coast towns from Al Khor to Al Wakra to use Doha's times. All files are in /private/tmp/claude-501/-Users-mohamedabulgasem-Desktop-Workspace-apps/d2f2d5b2-1f80-4ac1-b4d2-f0b36f6f4636/scratchpad/round/data-gulf-egypt/off-*.txt.

### profile

Rounding for every profile: prayer starts are shown as ceil(raw + m) and sunrise as floor(raw - m), with raw from adhan2 to the second and m in seconds. All use Shafi Asr and no elevation.

EG (ESA, 2025 onward): Fajr 19.5°, Isha 17.5°. m: Fajr +12, Sunrise 0, Dhuhr 0, Asr +28, Maghrib +14, Isha +4. These are the split-B fit plus 15 s of safety. What the official table itself does: raw rounded to the NEAREST minute with no precaution minutes, at ESA's own per-city points. Cairo's point comes out near 30.08N 31.27E, against 30.063/31.250 in the app. ESA's Asr puts the shadow angle at the noon declination; at Cairo that reproduces 398 of 409 days, against 263 for adhan2's Asr. ESA applies no elevation: Saint Catherine minus El-Tor day length centres on 0 min.

AE (IACAD Dubai zone): Fajr 18.2°, Isha 18.2° (the 18.0-18.1 bands are only slightly narrower). m: Fajr -15, Sunrise +203 (sunrise shown about 3.4 min before raw), Dhuhr +156, Asr +73, Maghrib +215, Isha +25. This is fitted over a full leap cycle, 2026-2029. Hatta and Dubai Rural have their own tables and need their own m. Awqaf applies elevation for mountain areas: on 25 Sep, Jebel Jais sunrise is 5 min earlier and Maghrib 4 min later than Ras Al Khaimah city.

QA (Doha zone, printed calendar): Fajr 18°, Isha = shown Maghrib + 90 min all year, and Ramadan 1447 keeps the 90 min on all 30 days. m: Fajr -79, Sunrise -23 (floor(raw + 23 s)), Dhuhr +33, Asr +50, Maghrib +147. The +147 covers Ramadan; the Jun-Sep calendar alone needs only +93. So an optional Ramadan rule is Maghrib +1 min in Ramadan. As found: official Fajr is about 1 min before 18° raw, Maghrib about 2 min after sunset in summer and about 3 min in Ramadan 1447, Dhuhr about transit + 1 min.

KW (not validated): KUWAIT preset angles 18° / 17.5°. The 10 recent days fit nearest rounding with no offsets. A provisional profile is ceil(raw + 30 s) for starts and floor(raw - 30 s) for sunrise. Confidence LOW.

### in sample

Official minus raw, in seconds, at app coordinates.

EG, Cairo from 2025 on, 19.5/17.5: Fajr -39..+24, Sunrise -41..+23, Dhuhr -35..+25, Maghrib -33..+28, Isha -37..+30. Most places are within ±45 s; the Asr band is about 110 s. Across 22 places from 2025 on, the smallest never-early margins are Fajr -3, Sunrise -8, Dhuhr -12, Asr +15, Maghrib -1, Isha -8.

AE, Dubai 2026 at 18.2°: Fajr -50..+43, Sunrise -245..-169, Dhuhr +141..+206, Asr +12..+114, Maghrib +171..+249, Isha -37..+48. With the Dubai-only in-sample margins (Fajr -16, Sunrise +186, Dhuhr +147, Asr +55, Maghrib +190, Isha -11) there are 0 early days. Late 0/1 min: Fajr 262/103, Sunrise 329/36, Dhuhr 349/16, Asr 243/122, Maghrib 326/39, Isha 295/70.

QA, Doha print: Fajr -97..-20, Sunrise -36..+40, Dhuhr +27..+92, Asr +12..+109, Maghrib +90..+206, Isha minus Maghrib exactly 90 min.

KW, recent 10 days: every prayer within about ±55 s.

### held out result

Each result reads "days early" for starts, "days later" for sunrise, and the late distribution in minutes as 0/1/2/3+.

EG, split B: trained on 11 places, tested on 11 other places, 495 place-days, 2025 onward, with the +15 s margins. Early days: 0 for every prayer. Sunrise later: 0. Late: Fajr 155/340/0/0, Dhuhr 261/234/0/0, Asr 202/274/19/0, Maghrib 155/340/0/0, Isha 234/261/0/0. Sunrise before official 0 min on 276 days, 1 min on 219. Fajr is later than official on 340 of 495 days.
- Split A, trained on Cairo only and tested on 23 places (1,035 days): does not transfer. Early days: Fajr 164, Dhuhr 152, Asr 105, Maghrib 128, Isha 121. Sunrise later 55.
- Split C, trained before 2025-10 and tested after, 22 places (1,045 days), without the +15 s: early Fajr 24, Dhuhr 3, Asr 7, Maghrib 2, Isha 1. Sunrise later 3.
- Final profile on all 24 places: 0 early at the 22 normal places. Kharga starts are early on 20 to 30 of 45 days, because ESA's point there is about 70 s later on every prayer. Ras Gharib sunrise is later on 22 of 45 days, because its point is about 60 s earlier. Cairo Asr is 2 min late on 87 of 402 days.

AE: trained on Dubai 2026 and tested on the same perpetual table against the 2027, 2028 and 2029 sun (1,096 days). Early days: Fajr 1, Dhuhr 18, Asr 36, Maghrib 54, Isha 89. Sunrise later 60. So the fit must span the whole leap cycle.
- With the 4-year fit: 0 early and 0 sunrise later for Dubai. Late across 1,471 days, including the Awqaf cross-section: Fajr 1040/431/0, Dhuhr 1204/266/1, Asr 529/932/10, Maghrib 663/803/5, Isha 366/1078/27. Fajr later than official on 431 days.
- Awqaf one-day cross-section, 10 cities: 0 early. Al Ain sunrise is 1 min later than official.
- Dubai profile applied to Hatta's own table (365 days): Fajr early 221, Maghrib early 56, sunrise later 28. Hatta needs its own zone.
- Dubai Jan-Jun trained and tested on Jul-Dec: Dhuhr early 2, Asr 20, Maghrib 1, Isha 6.

QA: trained on the printed data (118 days) and tested on the website header (50 days, 2023-2026). Early days: Fajr 2, all other starts 0. Sunrise later 1. Late: Dhuhr 14/32/4, Asr 9/24/17, Maghrib and Isha 12/37/1.
- Doha table at Al Khor coordinates: Fajr early on 57 of 88 print days and 16 of 50 web days; sunrise later on 15 of 50.
- Doha table at Al Wakrah coordinates: Dhuhr early 16 of 88, Asr early 38 of 88.
- Season splits: trained on summer and tested on Ramadan gives Maghrib and Isha early on 28 of 30 days and Fajr early on 12. Trained on Ramadan and tested on summer gives Asr early on 50 of 88 and sunrise later on 25.

KW: trained before 2023 and tested from 2023, 13 days: Fajr early 1. Late: Asr 0/8/5, Maghrib 1/7/4/1, Isha 1/1/10/1. Unreliable data.

The current app is already wrong in places. QATAR preset: Maghrib and Isha 2-3 min early on every printed day, Dhuhr 1 min early on 85 of 88 days, Fajr 1-2 min late. DUBAI preset: Maghrib 1 min early on 170 of 365 days, Isha early on 52, sunrise later on 184. EGYPTIAN preset at Cairo: Asr 1 min early on 40 of 402 days.

### never early achievable

False

### explanation of wide bands

These are explained and tested.
1) Asr bands are 100-150 s at every authority because adhan2 takes the shadow angle from the day's 0h UT solar coordinates. ESA, and the others by the same pattern, use the noon declination for the shadow and the Asr-moment declination for the hour angle. At Cairo that model matches nearest rounding on 398 of 409 days with a 64 s band; adhan2 matches 263 of 409 with a 115 s band. In Dubai the band drops from 103 s to 75 s.
2) Egypt: reference points. A grid search puts ESA's Cairo point at about 30.08N 31.27E, which raises Fajr+Isha matches from 179 to 201 of 206. Kharga sits about +70 s on every prayer and Ras Gharib about -60 s. The 2024 captures are shifted (Fajr and sunrise 25-35 s earlier, Maghrib and Isha 20-40 s later), so ESA changed its method between the 2024-12-02 and 2025-01-08 captures; only data from 2025 on is used for the profile. The 2024-07-23 capture has single-day outliers down to -162 s at a few towns.
3) Dubai: the perpetual table is compared against a moving sun. The bands are narrowest for 2026-type years (2018, 2022, 2026, 2030) and widen by 15-25 s across the leap cycle, so margins are fitted over 2026-2029.
4) Hatta: its Fajr is about +1 min and its Isha about -1 min relative to the Dubai method at Hatta's coordinates, so its zone parameters are separate. No elevation effect was seen there.
5) Qatar: Maghrib is sunset +2 min in summer and +3 min in Ramadan 1447. Fajr's offset shifts by about 20 s between seasons, so the angle is not exactly 18°; 18.2° gives the narrowest band over both seasons, 74 s. The website header runs about 1 min earlier on Dhuhr and Asr than the printed calendar.
6) Kuwait: the newspaper box is sometimes stale or misdated.

### recommendation

EG: Compute with the profile above, 19.5/17.5 plus the margins, which holds never early at 22 of 24 tested places for about +1 min on most days. Add per-zone reference points or per-town official data for places where ESA's point is far from the app's point: Kharga/New Valley and Ras Gharib at least. Dar al-Ifta's monthly GetPrayer is public, covers 78 towns, and would support shipping ESA tables. Fajr is 1 min later than official on about 70% of days, so show a separate end-of-suhur time equal to floor(raw) or earlier.

AE: Ship the official data. IACAD's perpetual tables for Dubai, Dubai Rural and Hatta can go in the app as-is. Awqaf publishes per-area tables for about 60 areas, applies elevation on mountain areas, and its monthly lists are a download on awqaf.gov.ae that only Mohamed or the team can fetch. The Dubai profile is never early only inside the Dubai zone. Khaleej Times' other emirates are just the Dubai table plus fixed offsets; its Fujairah is 1-3 min earlier than Awqaf's, so do not use it.

QA: Compute with the Doha-zone profile, and snap every user from Al Khor to Al Wakrah to Doha's reference coordinates. Give the other zones (Dukhan, Al Shamal, Abu Samra, Mesaieed, Halul) their own points. The alternative is shipping the calendar. The current QATAR preset must change now: its Maghrib and Isha are 2-3 min early.

KW: Cannot match. No official table is publicly reachable. Keep conservative margins and get the Ministry's or the Al-Ojairi calendar.

In general, fixing adhan2's Asr formula (shadow angle at the noon declination) would cut Asr lateness by about 1 min at every authority.

### data licence or terms

Not found for any of the sources. The Dar al-Ifta, ESA, IACAD, Awqaf UAE and Qatar Calendar House pages I saw state no terms of reuse. Awqaf UAE offers a prayer-time service to external websites only through a registration request, and its API requires a token. Al-Anba and Khaleej Times are newspapers that name no source.

### confidence

EG HIGH: over 1,400 place-days, cross-checked between Dar al-Ifta and ESA with 0 conflicts after one AM/PM label fix. The inferred method change between 2024-12 and 2025-01 is MEDIUM. AE Dubai HIGH: the perpetual table is confirmed by the IACAD files, by Khaleej Times September 2026 (30 of 30 days) and by the Awqaf page on 25 Sep. The Awqaf areas outside Dubai are LOW-MEDIUM, from one day only. QA MEDIUM: 118 printed days in two seasons, and the website header differs from the print by up to 1 min on Dhuhr and Asr. The Qatar zone ids on the website are unreliable across years, LOW. KW LOW: secondary source, stale days, no official data.

## data-wide-band

**Authority.** Three authorities: (1) Singapore MUIS (one national table); (2) Malaysia JAKIM e-solat (zone tables); (3) South Africa Jamiatul Ulama (jamiat.org.za), using its perpetual timetables for Johannesburg and 7 other towns.

### sources

- https://data.gov.sg/api/action/datastore_search?resource_id=d_a6a206cba471fe04b62dd886ef5eaf22&limit=2000 : MUIS 'Muslim Prayer Timetable (consolidated)' 2024-01-01..2026-12-31, fetched 2026-09-25
- https://data.gov.sg/api/action/datastore_search?resource_id=d_dddc19f6c90edd7cff6b57494630ad29&limit=2000 (MUIS 2024) and ...resource_id=d_e81ea2337599b674c4f645c1af93e0dc&limit=2000 (MUIS 2025): both match the consolidated set on every day, fetched 2026-09-25
- https://isomer-user-content.by.gov.sg/muis_prayers_timetable.json : data behind the MUIS website, 2025-12-29..2026-12-31. It matches data.gov.sg on all 368 days. Fetched 2026-09-25
- https://data.gov.sg/datasets/d_a6a206cba471fe04b62dd886ef5eaf22/view : dataset page. The Open Data Licence claim comes from a web-search snippet; I did not fetch this page
- https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=year&zone=ZONE for WLY01 SGR01 WLY02 JHR01 JHR02 PHG01 PHG06 PLS01 PNG01 KTN01 SBH01 SBH06 SBH07 SWK08: 2026 full year each, fetched 2026-09-25 (serverTime in JSON)
- https://api.waktusolat.app/v2/solat/ZONE?year=2025&month=1..12 : public mirror of JAKIM data, used for the 2025 held-out year, same 14 zones, fetched 2026-09-25. Its 2026 data matched e-solat exactly for SWK08 and WLY01
- https://www.e-solat.gov.my/ : footer says '2020 Hak Cipta Terpelihara Jabatan Kemajuan Islam Malaysia'. No API or licence terms found. Fetched 2026-09-25
- https://www.muftiselangor.gov.my/2025/07/03/15-taudhih-al-falak-fajar-sadiq-dalam-menentukan-permulaan-waktu-solat-subuh/ : Malaysia used a 20 degree Subuh angle until 2019, then 18 degrees (about 8 min later on average). Fetched 2026-09-25
- https://salaahtimes.starlite.za.net/jamiat/perpetual/export-csv.php?id=ID : perpetual CSVs, linked from https://jamiat.org.za/salaah-times.php?ref=pst&id=ID. Johannesburg fdead345d15555d292e52a041aef1c4e402369b8, Cape Town a8e12c9f28bf220e8243849cd2231fd9fb427566, Durban 712755729da76a79096bdf78b8842bf22e349ecd, Polokwane f7a1d15e6d3d2e4ed81da1fda613c7d575a92193, Springbok 09dd6d665f44201302d49b3d4db48785c1eb9668, Port Elizabeth a0c80a4c16b36e8e04878464768e2e6b735056e0, Bloemfontein 13e8e7f3520e66b731971f4fb9a72b8601c0af3e, Upington 53bc000c016a76020a8d36f814d4b2a173355493. Fetched 2026-09-25. The Johannesburg CSV is byte-identical to the saved agentD/jamiat-jhb.csv
- Secondary claim, not checked at source: 'JAKIM zones are built on a 2-minute ihtiyati; the reference point is the westernmost point of the zone'. It appeared only in a web-search summary (takbir.my/panduan/cara-jakim-kira/ was among the results).

### places and days

MUIS: one national table (Singapore), 2024-01-01..2026-12-31, 1,096 days. Measured at the app's Singapore point (1.28967, 103.85007). Device-location check at Jurong West (west), Tampines (east) and Woodlands (north).
JAKIM: 14 zones, each with the full year 2026 (e-solat) and the full year 2025 (mirror), 365+365 days each (10,220 zone-days):
- WLY01 Kuala Lumpur, SGR01 Shah Alam
- PLS01 Kangar (far north-west), PNG01 George Town
- KTN01 Kota Bharu (north-east)
- JHR02 Johor Bahru (south), JHR01 Pulau Aur and PHG01 Pulau Tioman (point-like islands)
- SWK08 Kuching, WLY02 Labuan, SBH07 Kota Kinabalu
- SBH01 Sandakan (far east)
- PHG06 Cameron Highlands and SBH06 Gunung Kinabalu (high altitude)
SBH06 2026 has internal breaks: all times except Maghrib and Isha drop 3-7 min on 15 Mar, and Maghrib drops 8 min on 1 Dec. It is excluded from the profiles.
Jamiat: perpetual 366-row tables (Shafi and Hanafi Asr/Isha columns) for 8 towns: Johannesburg (about 1,750 m), Polokwane (north), Durban (east), Cape Town (south-west), Springbok (far west), Port Elizabeth (south), Bloemfontein, Upington. Each was mapped onto 2025, 2026, 2027, 2028 and 2029 (a full leap cycle plus one year): 8 towns x 2 schools x 1,826 days.
Files are in /private/tmp/claude-501/-Users-mohamedabulgasem-Desktop-Workspace-apps/d2f2d5b2-1f80-4ac1-b4d2-f0b36f6f4636/scratchpad/round/data-wide-band/ (off-muis-singapore*.txt, off-jakim-ZONE.txt, off-jakim-ZONE-2025-mirror.txt, off-jamiat-TOWN-S|H-YEAR.txt).

### profile

Offsets are in seconds. Starts = ceil_to_minute(raw + offset). Sunrise = floor_to_minute(raw + value), so a smaller value means an earlier sunrise.

MUIS (recommended rule "B"):
- Always compute at the fixed point 1.28967N 103.85007E, whatever the device location in Singapore.
- Fajr 20 deg, Isha 18 deg, Asr Shafi.
- Starts = ceil(max(raw of yesterday, raw of tomorrow) + offset): Fajr +21, Dhuhr +79, Asr +31, Maghrib +18 (+78 in Ramadan), Isha +20.
- Sunrise = floor(min(raw of yesterday, raw of tomorrow) + 58 s).
- These are margins fitted on all 3 years plus a 5 s pad.
- Simpler rule "A" (same day's raw only), all-years margins: Fajr +47, sunrise floor(raw+44), Dhuhr +101, Asr +72, Maghrib +36 (+96 in Ramadan), Isha +41. It needs about 15 s more pad to stay never-early.

JAKIM:
- There is no single national profile. It has to be per zone, computed at the zone's calibration point, with Fajr 18 deg, Isha 18 deg and Shafi Asr.
- Per-zone offsets are in jakim-zone-offsets.csv (fit on 2025+2026, +20 s pad). Examples, in the order Fajr/sunrise value/Dhuhr/Asr/Maghrib/Isha:
  - Kuching 194/-94/222/202/180/183
  - Kota Bharu 102/-35/148/106/92/-149 (Isha is effectively 17 deg there)
  - Labuan 50/53/108/61/48/49
  - Cameron Highlands PHG06 19/+392 (sunrise shown about 6.5 min earlier)/103/22/382/19
- KL and Selangor (WLY01/SGR01) starts need two points: ceil(max(raw at 2.62N 101.69E, raw at 3.73N 101.45E) + Fajr 45, Dhuhr 102, Asr 59, Maghrib 43, Isha 44). Sunrise uses the city point.

Jamiat:
- adhan2 plus fixed offsets does not work. The profile that works is Jamiat's own rule:
  - The sun's declination and equation of time are taken once per date at 0h UT, in a year with 2026's solar phase (the table is perpetual).
  - transit = 12h - EoT - lon/15 + 2h; each event = transit -+ H/15, with H from that declination.
  - Fajr 18 deg; Isha 15 deg (Shafi) or 18 deg (Hanafi); Asr factor 1 or 2.
  - Horizon -0.8333 deg with no elevation correction, even at Johannesburg.
  - Dhuhr = transit + 5 min; Maghrib = sunset + 3 min; Jamiat rounds to the nearest minute.
  - Use each town's Jamiat coordinates.
  - Never-early variant margins: Fajr -24, sunrise floor(model+25), Dhuhr +275, Asr -25, Maghrib +156, Isha -24.
- Suhoor ends is Fajr - 5 min in Jamiat's table.

### in sample

Official minus adhan2 raw, in seconds, with the documented angles.

MUIS (app point, 20/18 Shafi, 2024-2026):
- Fajr -19..+106 (125)
- Sunrise -15..+101 (116)
- Dhuhr +47..+160 (113)
- Asr -61..+131 (192)
- Maghrib -21..+148 (169; 116 after the Ramadan +60 s)
- Isha -21..+100 (121)

JAKIM 2026 at city points (18/18 Shafi):
- Kuala Lumpur: Fajr +44..+197, Sunrise -81..-10, Dhuhr +136..+198, Asr +36..+211, Maghrib +47..+191, Isha +45..+195
- Kuching: +111..+233 / +55..+169 / +201..+261 / +108..+232 / +106..+219 / +101..+222
- Kota Bharu Isha: -222..-110 (a different Isha angle)
- Cameron Highlands: Sunrise -428..-339, Maghrib +330..+417

Jamiat (app city points, 18/18 Hanafi, 2025-2029):
- Fajr -77..+94 (171)
- Sunrise -71..+100
- Dhuhr +237..+364
- Asr -103..+139 (242)
- Maghrib +75..+322 (247)
- Isha -134..+160 (294)

How well the discovered rules reproduce the tables, minute for minute (my own solar code, which agrees with adhan2 to about 1-3 s except Asr):
- MUIS rule: 98.9-99.6% exact per prayer over 1,096 days. Under the rule, 20.0 deg, 18.0 deg, -0.8333 deg and Asr factor 1 each leave a spread of 60.5-61.1 s, which is pure rounding.
- Jamiat rule: 95.3-96.2% exact over 8 towns (both schools); spreads 64-70 s.
- JAKIM: single-point zones leave spreads of 60-65 s, and the KL/Selangor two-point envelope 61-64 s. Johor and Pahang zones keep a seasonal Dhuhr residual of about +-15 s (spread 92-96).

### held out result

In the tallies below, "early" (or "late" for sunrise) counts days that break the rule and must be 0. "0/1/2/3+" is the minutes-late distribution (for sunrise, how many minutes earlier than official). "Fajr later" is the end-of-suhur risk: days the app's Fajr is after the official Fajr.

MUIS, rule B, 5 s pad, leave-one-year-out (fit on 2 years, test on the third, pooled, n=1,096):
- 0 early and 0 sunrise-late on every prayer.
- Fajr 0:616 1:478 2:2 (Fajr later 480 days, 44%)
- Sunrise 0:717 1:367 2:12
- Dhuhr 0:738 1:358
- Asr 0:475 1:473 2:129 3+:19
- Maghrib 0:721 1:374 2:1
- Isha 0:684 1:411 2:1
- Rule A, fit 2024+2025, test 2026: 0 early. Fajr 0:127 1:231 2:7; Asr 0:81 1:197 2:82 3+:5.
- Fitting on a single year fails the other year (for example fit 2025, test 2026: Fajr 17 early), because the table's one-day offset flips sign.
- Computing at the device location fails: at Tampines, 24-33 early days per prayer.

JAKIM per-zone, 20 s pad (13 zones, fit one year and test the other, both directions; n=9,490, sunrise 8,760):
- 0 early and 0 sunrise-late.
- Fajr 0:3451 1:5036 2:993 3+:10 (Fajr later 64%)
- Sunrise 0:3917 1:4713 2:130
- Dhuhr 0:5279 1:4211
- Asr 0:2667 1:5521 2:1188 3+:114
- Maghrib 0:4027 1:4835 2:628
- Isha 0:3933 1:4822 2:735
- With a 10 s pad, 9 Asr days are early, so the pad was chosen by looking at the test data.
- With the two-point rule, KL and Selangor starts are 0 early and 100% at most +1 min.
- A single national profile fitted on 5 zones fails badly: margins of 160-202 s, 2-3+ min late on most days, 365 early Maghribs at Cameron Highlands, and 353 late sunrises at Shah Alam.

Jamiat with adhan2 and fixed offsets (fit Johannesburg, Cape Town and Durban over 2025-2028; test the 5 other towns over 2025-2029; n=9,130):
- Fajr early 30, 0:2746 1:6214 2:140
- Dhuhr early 22
- Asr(H) early 36, 0:1209 1:3539 2:4258 3+:88
- Maghrib early 20, 0:1013 1:3302 2:4549 3+:246
- Isha(H) early 9, 0:644 1:2519 2:4395 3+:1563
- Result: it fails.

Jamiat with its own rule (fit Johannesburg, Cape Town and Durban; test 5 other towns; n=1,825):
- 1 early day (Fajr), 0 on every other prayer.
- Every prayer is 0 or +1 min: about 90% exact, 10% +1 min (Fajr later 188 days).

### never early achievable

False

### explanation of wide bands

MUIS (HIGH):
- Each year's table is dated one day off from the astronomy, and the direction changes by year.
  - 2024 and 2026 rows hold the times for date+1; 2025 rows hold date-1.
  - Proof: official Dhuhr minus adhan2 transit(d+1) spans exactly 60 s in 2024 and 2026, and transit(d-1) spans 61 s in 2025. With no shift the span is about 110 s. MUIS's own website JSON shows the same.
- On top of that, MUIS uses the classic method: accurate transit, and each other time = transit -+ H/15, with H from the declination at noon.
  - The reference point is about 1.27-1.28N, 103.82-103.85E (1 deg 16-17' N, 103 deg 51' E).
  - Everything is rounded up (ceil(model + about 8 s)). Dhuhr is transit + about 1 min.
  - Maghrib is +1 min on every Ramadan day (2024-03-12..04-09, 2025-03-01..03-30, 2026-02-19..03-20).
- adhan2's Asr uses the declination at 0h UT for the shadow term (checked in the SolarTime.kt source). That adds 10-30 s of difference, which is why Asr is the widest band.

JAKIM (MEDIUM):
- Subuh has been 18 deg since 2019, not 20 deg. Isha is 18 deg, except Kelantan at about 17 deg. The calculation is the same classic transit +- H method.
- KL (WLY01) uses the same starts as the whole Selangor zone (SGR01), which runs from about 2.6N to 3.75N. The start times are the latest of a south point (about 2.62N) and a north-west point (about 3.73N, 0.24 deg further west). That produces a V-shaped seasonal band of up to about 170 s at any single city point.
- Other state-level differences:
  - The Cameron Highlands zone uses a lowered horizon of about -2.5 deg, so sunrise is about 6.5 min earlier and Maghrib about 6 min later than at sea level.
  - Johor and Pahang zones have a +-15 s semi-annual Dhuhr pattern I could not explain (LOW).
  - Penang Asr jumps near the sun's zenith passage (early April).
  - Perlis changed its sunrise between 2025 and 2026, and its Fajr residual has a seasonal pattern (LOW).
  - SBH06's 2026 table has breaks.

Jamiat (HIGH):
- The perpetual table is computed with the sun's declination and equation of time fixed at 0h UT of each date. This gives Mar/Sep asymmetric errors of up to about +-35 s in the evening prayers.
- The table repeats every year, while real astronomy drifts up to about +-12 h of solar motion over the leap cycle.
- Jamiat rounds to the nearest minute, so the official time can be up to 30 s before the computed time.
- Elevation is ignored even at 1,753 m.

None of these patterns can be absorbed by a constant offset on adhan2.

### recommendation

Not across all three authorities with one adhan2 raw-plus-seconds profile each. By authority:

MUIS: ship the official data. It is one national table under an open licence, published yearly. Keep the profile as a fallback for years not yet published:
- Compute at the fixed point for all of Singapore, never the device location.
- Use rule B: the later of yesterday's and tomorrow's raw time, with the offsets above.
- Add Maghrib +1 min in Ramadan.
- Expect 0 early days, and at most +1 min late on 97-100% of days, except Asr at 87%.

JAKIM: a single national profile cannot match. The options are:
- Per-zone reference points and offsets (my table, plus zone polygons and a zone lookup, and two points for the Selangor/KL zone). This gives 0 early days and at most +1 min late on 86-100% of days per prayer.
- Or ship the e-solat zone tables. That needs JAKIM permission: the site says all rights reserved.

Jamiat: adhan2 with fixed offsets cannot be both never-early and at most +1 min late. The options are:
- Implement Jamiat's perpetual rule (fixed instant at 0h UT, 18 deg / 15 deg / 18 deg, Dhuhr +5 min, Maghrib +3 min, Jamiat town coordinates). On held-out towns this gave 1 early day in 1,825 (a pad of about 2 s would clear it) and at most +1 min late on every day.
- Or ship Jamiat's perpetual per-town tables. I found no licence; the page offers the CSV for printing and publishing.

City pages for Singapore, Malaysia and South Africa should not be re-added until one of these is in place.

### data licence or terms

MUIS:
- data.gov.sg Open Data Licence. This is stated in the task and in the search snippet for the dataset page; I did not fetch the licence page itself.
- The MUIS website JSON has identical data.

JAKIM:
- e-solat footer: '2020 Hak Cipta Terpelihara Jabatan Kemajuan Islam Malaysia' (all rights reserved).
- No API or reuse terms found.
- waktusolat.app mirror: terms not found. It was used only for analysis.

Jamiat:
- No licence or copyright text found on the jamiat.org.za salaah-times pages.
- The page offers the CSV export for "printing & publishing". That is not a licence; ask Jamiat before shipping.

### confidence

HIGH:
- MUIS rule: day offset by year, classic method, ceil, Ramadan Maghrib +1 min, 20/18. Reproduces 99%+ of 1,096 days.
- Jamiat rule: reproduces about 96% of minutes at 8 towns.
- JAKIM Fajr 18 deg: fits the tables and matches the Selangor Mufti statement.
- All held-out counts: computed with the real adhan2 0.0.7 jar.

MEDIUM:
- JAKIM Selangor/KL two-point envelope: fitted points, not published coordinates.
- Kelantan Isha 17 deg.
- Cameron Highlands horizon about -2.5 deg.
- Whether the MUIS offsets hold for 2027: the shift direction cannot be predicted, and rule B covers both directions.
- JAKIM per-zone offsets for future years: only 2 years tested, and the pad was chosen after seeing the test.

LOW: Johor/Pahang Dhuhr pattern, Penang Asr, Perlis Fajr (unexplained).
