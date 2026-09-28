# How the dominant Sunni timetables are made, and where the app comes out earlier (25 Sep 2026)

## How to read this

**"app".** The app's current default: adhan2 0.0.7 preset, no extra minutes, Standard Asr, automatic high-latitude rule (≥48°: one seventh of the night; ≥65°: twilight angle; otherwise middle of the night).

**"app − official".** In minutes. **Negative means the app is earlier**, which is the error the owner wants ruled out.

**Sources.**
- **Documented:** read in the authority's or mosque's own publication or code, with its URL.
- **Fitted:** inferred by fitting a year or more of the official table against adhan2 raw times with my scripts or the regional agents' scripts.
- **Second-hand** sources are labelled as such.

**Confidence** is marked HIGH, MEDIUM or LOW.

**Status.** All seven regional research passes have reported. Coverage gaps:
- Not verified: Maldives, Tajikistan, Turkmenistan, Azerbaijan, Ethiopia, North Macedonia.
- India: only Dawat-e-Islami was verified.
- ISNA Canada and the Canadian Council of Muslim Theologians: not checked.

**Limits on this run.**
- The shared WebSearch budget ran out (200/200) partway through. Later work fetched known official URLs directly, used the Wayback Machine and fitted numbers, so some history questions are "not found".
- Sites that refused this environment:
  - Kemenag bimasislam (connection refused)
  - Maldives Ministry of Islamic Affairs (403)
  - Darul Uloom Deoband (Cloudflare)
  - Wifaqul Ulama PDFs (Cloudflare)
  - Kelantan mufti
  - Jamiat KZN

**Incidents during browsing (from the UK agent).**
- Fetching a Wifaqul Ulama PDF opened a **file-save dialog on the user's screen**. Nothing was saved; the dialog should be cancelled.
- **Glasgow Central Mosque's prayer-times page redirected to an unrelated gambling site (icsconline.org).** The mosque's site may be compromised. Nobody interacted with it.

**Scratch data.** Everything is under `…/scratchpad/moral-auth-world/`, one subfolder per region.
- My raw-time tool: `RawTimes.java`.
- Scripts: `offsets.py` (official minus raw), `appvs.py` (app minus official), `needed.py` (smallest never-earlier offsets), `multipoint.py` (JAKIM zone envelope).

## Cross-cutting facts (read these first)

1. **Almost every authority adds fixed minutes the app does not.** Typical additions: Dhuhr +3 to +5, Maghrib +2 to +7, sometimes Asr +1 to +4, and sunrise −2 to −7.
   - Result: the app's **Dhuhr and Maghrib are earlier than the authority on every day** in:
     - Malaysia, Indonesia, Bangladesh
     - Uzbekistan, Kazakhstan, Kyrgyzstan, Russia
     - UK (London), Germany/Diyanet, Belgium (Maghrib)
     - Gauteng/KZN, Cape Town calendars, Australia
   - Iftar at the app's Maghrib is 2–9 min early in most of these places.
2. **"Within a minute" is not "never earlier".** MUIS Singapore, which the app matches to within ±1 min, is still 1 min later than the app on 72–119 days a year per prayer. In 2025 it was 2 min later on some days.
3. **Above 48° the app's one-seventh-of-the-night rule is the largest single error.** It affects the UK, Ireland, Benelux, Germany, Russia, Kazakhstan's north and the Nordics.
   - From spring to autumn it makes the app's **Isha 10–75 min earlier** and its **Fajr 30–90 min later** than every official table checked.
   - A later Fajr is the fasting hazard: people eat after the mosque's imsak.
   - Just below 48° the app switches to middle-of-the-night and the error flips. Freiburg (47.99°N): the app's June Fajr is up to 90 min **earlier** than Diyanet's.
4. **Fajr has two roles, and one value cannot serve both.** It is the start of the Fajr prayer (must not be early) and the end of suhur (must not be late).
   - Authorities that separate them print an earlier "sehri/imsak" column:
     - Malaysia, Indonesia, Singapore, Brunei: Subuh − 10
     - IFB: sehri 3 min before true dawn, printed Fajr 3 min after it
     - Jamiat SA: Suhoor = Fajr − 5
     - OpenFajr: suhur − 8
   - Others define imsak = Fajr: Diyanet, DUM RF, the Uzbek Board, LUPT, EMB.
   - An app that shows a single Fajr needs two numbers wherever the published Fajr is later than true dawn.
5. **Sunrise (and imsak) close a period, so "earlier" is the safe direction for them.** The app's sunrise is 1–8 min **later** than the official sunrise in:
   - Kemenag (3–4), JAKIM (1–2)
   - Diyanet (6–8), EMB (1–3), LUPT (2–3)
   - DUM RF (5), Sarajevo (7)
   - A "never earlier" rule must be inverted for sunrise.
6. **Zone tables versus the user's own location.**
   - JAKIM computes each zone at its latest reference point, and syuruk at the earliest point.
   - A calculation at the user's own coordinates is 2–8 min **earlier** than the zone table in the east of wide zones: Kapit, Ranau, Mersing.
   - Kemenag uses the regency capital, so users east of it are in the same position.
7. **The library's Asr is slightly early in the Americas.** adhan2's `SolarTime.afternoon()` takes the declination at 0h UT, not at transit (code verified). It is 1–2 min early on about a third of days in the Americas (the North America pass measured this).
8. **JAKIM's Fajr change date (primary source): the decision is 2019; the rollout ran from late 2019 to early 2020.**
   - The Muzakarah of the National Council for Islamic Affairs (MKI), meeting no. 116 on 20–21 Nov 2019, set Subuh at "18° di bawah ufuk", about 8 min later on average (7–10). Source: Jabatan Mufti Wilayah Persekutuan, Al-Kafi #1638, 3 Mar 2020, https://muftiwp.gov.my/en/artikel/al-kafi-li-al-fatawi/4271; Bayan Linnas Khas 3858, 6 Dec 2019. HIGH.
   - The Federal Territories committee decided on 2 Dec 2019. Selangor had the Sultan's consent on 29 Nov 2019.
   - Melaka and Johor applied it from 1 Jan 2020 (news, second-hand: malaysiagazette.com, 31 Dec 2019).
   - Penang applied it from 8 Feb 2020 (second-hand blog quoting the Penang Mufti).
   - Kelantan's previous value was 19°, not 20° (Selangor Mufti, Taudhih al-Falak 15).
   - So "2019" is right for the decision, and "2020" is right for several states' effective dates.
## Per country

### MALAYSIA — the state muftis' takwim, published through JAKIM e-solat. Massive majority: YES (HIGH)

- **Authority.**
  - Each state mufti department issues the takwim using JAKIM's criteria, the JUPEM Almanak Falak Syarie and JAKIM software. All states publish through e-solat, and Johor's MAIJ embeds it.
  - Sources: https://www.muftiwp.gov.my/en/unit-falak2/149-soalan-soalan-lazim-falak/4021-waktu-solat, https://www.maij.gov.my/waktu-solat/. HIGH.
- **Fajr.**
  - 18° since the 2019 decision (see cross-cutting fact 8). Documented, HIGH.
  - All 59 zones' 2026 data fit 18°, including Perlis, Kelantan, Terengganu, Sabah and Sarawak. Fitted, HIGH.
  - My own check of WLY01 2026 at Kuala Lumpur: official minus raw 18° is +44..+198 s, but +533..+721 s at 20°.
- **Isha.**
  - 18°. Selangor Mufti: "Semua negeri di Malaysia ... menggunakan nilai 18°", https://www.muftiselangor.gov.my/2024/10/08/10-taudhih-al-falak-awal-waktu-isya-dan-perbezaan-cahaya-senja/.
  - **Kelantan (KTN01/02) fits 17°**, 3–4.5 min earlier. Fitted, HIGH on the data; LOW on the rule, because the Kelantan site was unreachable.
- **Precaution minutes.** No flat "+2" is added (HIGH).
  - The "2 minit" in the literature is the zone criterion: the east–west spread inside a zone should not exceed 2 minutes. Sources: the UMP Pahang paper https://journal.ump.edu.my/index.php/ijhtc/article/download/6226/2865 and the Mufti WP FAQ. Real zones reach 4 min (Selangor, Pahang) and up to 15 min (Sabah, Sarawak), per the UM paper https://ejournal.um.edu.my/index.php/RIS/article/download/27353/12400/61519.
  - Subuh, Asr, Maghrib and Isyak are the calculated time at the zone's **latest** reference point, rounded up.
  - Zohor is transit at the western point plus about 1 min ("pinggir timur matahari melintasi meridian"), rounded up.
  - Syuruk is taken at the zone's **eastern** point, rounded down.
  - I checked this myself for WLY01 2026 with the Gedangsa and Tg Rhu envelope:
    - Fajr −5..+64 s, Maghrib −4..+66 s, Isha −8..+67 s (consistent with rounding up);
    - Asr −14..+76 s;
    - Dhuhr +63..+125 s (transit + 1 min, rounded up);
    - Syuruk at Cheras −63..+3 s (rounded down).
    - This explains the 100–175 s spread against a single point. HIGH.
- **Reference points.**
  - The rule is "titik yang paling barat" (the westernmost point), with multipoint zones where several points are used (e-solat Lampiran B, https://www.e-solat.gov.my/portalassets/files/Lampiran%20B.pdf).
  - SGR01 and WLY01 use Kg Gedangsa (3°44′N 101°23′E) and Tg Rhu Sepang (2°38′N 101°37′E). Syuruk uses Pekan Broga for SGR01 and Cheras for WLY01 (https://muftiwp.gov.my/ms/artikel/bayan-al-falak/5381-bayan-al-falak-siri-ke-17-kaedah-penentuan-waktu-solat).
  - Elevation: highland zones are computed with a dip. Gunung Jerai (KDH07, 1214 m) has Subuh about 4 min earlier and Isyak about 4.6 min later (fitted).
- **Asr.** Shafi'i (shadow 1 + noon shadow). HIGH.
- **Imsak.** Subuh − 10 in every zone except **Perlis, where imsak = Subuh**. Documented in Al-Kafi 1638 ("10 minit sebelum"); per-zone values from the e-solat data. HIGH.
- **Machine-readable.**
  - Undocumented public JSON: https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=year&zone=WLY01 (also period=today and period=date).
  - Terms not found; footer says "Hak Cipta Terpelihara JAKIM". LOW on the terms.
- **App vs official.** Singapore preset against WLY01 2026, 365 days:

  | Prayer | app − official (min) | Days app is earlier |
  |---|---|---|
  | Fajr | −12..−8 | 365 |
  | Dhuhr | −2..−1 | 365 |
  | Asr | −3..0 | 341 |
  | Maghrib | −3..0 | 356 |
  | Isha | −3..0 | 350 |
  | Sunrise | +1..+2 | (app later) |

  - Smallest never-earlier fix at Kuala Lumpur: 18°/18°, +3 on each prayer, round up, sunrise −2. The app then runs 0 to +3 min later.
  - Even at 18°, computing at the user's position is 2–8 min early in the east of wide zones: Kapit, Ranau, Mersing.

### INDONESIA — Kemenag (Bimas Islam). Massive majority: YES for Dhuhr to Isha; PARTLY for Fajr (Muhammadiyah uses 18°). MEDIUM-HIGH

- **Angles and precaution.**
  - Fajr 20°, Isha 18°, "2 menit" ihtiyat on each start and −2 on syuruk. Documented only second-hand: id.wikipedia "Waktu salat", and tarjih.or.id 2018, https://tarjih.or.id/penentuan-awal-waktu-shalat-subuh/. Kemenag's own method page was unreachable.
  - Fitted on the myQuran copy of Kemenag data (Jakarta full year 2026; Surabaya and Merauke four months each):
    - Fajr = 20° + 2, rounded up;
    - Isha = 18° + 2, rounded up;
    - Asr ≈ shadow 1 + 2;
    - Dhuhr ≈ transit + 3, rounded up;
    - Maghrib and Syuruk ≈ ±2.7–3.7 min from the standard horizon, i.e. the 2 min ihtiyat plus a lower horizon whose cause is undocumented.
  - My own check (Jakarta, 61 days): official minus raw sits in a 57–61 s band for every prayer. With 20°/18°, rounding up, Fajr +2, Dhuhr +3 and Isha +2 are exact on 61/61 days.
  - HIGH for the angles and +2; MEDIUM for Dhuhr and Maghrib.
- **Reference points.** The regency or city capital, not an extreme point. Merauke fits Merauke town; the west edge would be about 6 min off. Fitted, HIGH.
- **Asr.** Shafi'i.
- **Imsak.** Subuh − 10 on all 611 days checked. HIGH.
- **Muhammadiyah.**
  - Munas Tarjih XXXI (28 Nov – 20 Dec 2020) set Subuh at −18°, about 8 min later. The PP plenary adopted it on 5 and 9 Mar 2021, and the tanfidz decision was posted on 24 Mar 2021. Source: https://muhammadiyah.or.id/2021/03/keputusan-pp-muhammadiyah-tentang-kriteria-awal-waktu-subuh/. HIGH.
  - It changes Subuh only.
  - Share of mosques: not found. It is a large organised minority.
  - NU and Persis: not verified.
- **Machine-readable.**
  - https://api.myquran.com/v2/sholat/jadwal/{id}/{yyyy}/{mm} and /v3. The metadata says "bersumber dari Kementrian Agama". It is a third-party republisher with no licence.
  - Kemenag's own terms: not found.
- **App vs official.** Singapore preset:
  - Fajr −2, Dhuhr −2, Asr −3..−1, Maghrib −3..−2, Isha −2..−1 on every day checked.
  - Sunrise is 3–4 min later.
  - Never-earlier fix for 2026: Fajr +3, Dhuhr +4, Asr +3, Maghrib +4, Isha +3, sunrise −3, round up. On this fit Fajr and Dhuhr can be +2/+3 exactly.

### SINGAPORE — MUIS. Massive majority: YES (a single national table). HIGH

- **Angles.** 20°/18°, Shafi'i Asr. Fitted, HIGH. No MUIS method document found.
- **Precaution and rounding.** Not reproducible to the minute.
  - Official minus raw at 1.29, 103.85 spreads 99–164 s (mean +38 to +103 s).
  - The residual drifts from year to year. Reference point not documented.
- **Imsak.** Subuh − 10 (MUIS Imsakiah 2026 PDF). HIGH.
- **Machine-readable.**
  - data.gov.sg collection 2312 (2024, 2025 and 2026 datasets), under the Singapore Open Data Licence v1.0 (https://data.gov.sg/open-data-licence): attribution required, commercial use allowed.
  - MUIS's own website terms forbid reuse, so use data.gov.sg.
  - Yearly PDFs to 2027: https://www.muis.gov.sg/resources/islamic-calendar/.
- **App vs official (2026).** The Singapore preset is within ±1 min, but the app is **1 min earlier** on Fajr 80, Sunrise 79, Dhuhr 91, Asr 119, Maghrib 95 and Isha 76 of 365 days (my run; the regional agent got 72–116).
  - In 2025 it was 2 min early on 14 Maghrib days.
  - Adding +1 to each prayer (+2 on Dhuhr) and rounding up fixes 2024 and 2026 but not 2025. **Ship the official table.**

### BRUNEI — MORA / Jabatan Mufti Kerajaan. Massive majority: YES (MEDIUM)

- **Method.** Fitted on the 2026 MORA table (MEDIUM):
  - 20°/18°, no precaution minutes, rounded to the nearest minute;
  - Dhuhr about +1;
  - Shafi'i Asr.
- **District offsets.** Documented: "Daerah Belait hendaklah ditambah 3 minit dan Daerah Tutong ... 1 minit", https://www.mora.gov.bn/SitePages/WaktuSembahyang.aspx.
- **Imsak.** Suboh − 10.
- **Machine-readable.**
  - A public SharePoint list: https://www.mora.gov.bn/_api/web/lists/getbytitle('Waktu%20Sembahyang')/items.
  - It is hand-entered, with duplicate rows and typos. One row would put Fajr about 9 min early. **Validate before use.**
  - The mufti.gov.bn wp-json `asr_mithl_2` is a placeholder (mithl 1 + 45 min), not a real Hanafi Asr.
- **App vs official.** The Singapore preset is never earlier at Bandar Seri Begawan. Against Belait's +3 at Kuala Belait it is 1 min early on 15–26 days.

### MALDIVES — Ministry of Islamic Affairs. NOT VERIFIED (LOW)

- The site returned 403 to this environment.
- Wikipedia (second-hand) says the ministry maintains the prayer times.
- Angles, island offsets, Asr school and API: not found.

### PAKISTAN — no binding state timetable. Massive majority: PARTLY (the method is near-universal; there is no single table). MEDIUM

- **Authority.** None issues times. PMD has no prayer section, the Ministry of Religious Affairs site returned HTTP 500, and nothing was found from the Council of Islamic Ideology, Ruet-e-Hilal or Auqaf. MEDIUM.
- **Deobandi: Karachi permanent timetable.** Source: https://www.banuri.edu.pk/assets/uploads/2020/04/1587988961_book_pdf.pdf.
  - Compiled by Prof. Abdul Latif; endorsed by Mufti Yusuf Ludhianvi, Darul Uloom Korangi, Jamia Farooqia and Banuri Town.
  - It prints **both Asr columns**.
  - Subh sadiq is both the sehri end and the Fajr start. Note: it is safer to give the Fajr adhan 10 min after it.
  - Zawal is printed with ±5 min to avoid.
  - All rights reserved.
- **Banuri Town web calculator** (https://www.banuri.edu.pk/namaz-times). For Karachi, September 2026, it equals 18°/18° with no offsets, rounded to nearest, Hanafi. Fitted, HIGH.
- **Barelvi: Dawat-e-Islami** (https://www.dawateislami.net/prayer-times/).
  - 18° Fajr, 18° Isha (Hanafi), and 12° Isha (Shafi'i).
  - Elevation-adjusted sunrise and sunset; shows seconds.
  - Advice: stop sehri 1 min before, give the adhan 1 min after, add 1 min at sunrise and sunset.
  - Printed tables use the earliest dawn and sunrise and the latest Zuhr to Isha over 26 years.
  - "Sharing these default timings is not allowed."
  - Fitted and documented, HIGH.
- **Ahl-e-Hadith.** The Standard Asr column is printed in both sources above. No separate Ahl-e-Hadith table found.
- **Changes.** None found.
- **Machine-readable.** Banuri HTML month tables; Dawat-e-Islami AJAX (sharing prohibited).
- **App vs official.** Karachi preset with Standard Asr (the app's default):
  - Standard Asr is 44 (Dec), 55 (Sep) and 79 (Jun) min earlier than the Hanafi Asr the majority use.
  - With Hanafi Asr, against the permanent timetable, the printed Fajr/sehri is 0–1 min earlier than the app on 62 of 91 days. So the app is up to 1 min late for sehri.
  - Maghrib is 0 to +1; Asr and Isha are equal or 1 min earlier.
  - Against Dawat-e-Islami: Asr 1–2 min and Maghrib up to 1 min earlier than theirs, before their extra +1.

### INDIA — no authority found. Massive majority: UNKNOWN (LOW)

- Hanafi majority (general knowledge; not sourced here).
- Only Dawat-e-Islami India was verified.
  - Delhi: 18°/18°, with the 12° Shafi'i Isha and elevation (701 ft).
  - Its Maghrib is 1.5–2.5 min and its Asr 0.9–1.9 min after the app's (Karachi, Hanafi).
- Not checked, because the sites were blocked or had no timetable:
  - Darul Uloom Deoband
  - Imarat-e-Shariah
  - Kerala: Samastha, KNM
- Kerala's Shafi'i angles, precaution minutes and imsak: not found.

### BANGLADESH — Islamic Foundation Bangladesh (IFB). Massive majority: YES (MEDIUM; no survey)

- **Year-round Dhaka table.** Mirror of the IFB document: https://kivabe.com/namaz/Calender_permanent_namaz_time.pdf (second-hand host).
  - Documented: 3 minutes are added to true Fajr, Zuhr and Maghrib as a precaution ("৩ (তিন) মিনিট যোগ করা হয়েছে").
  - Sehri ends 3 min **before** true dawn, so sehri and the printed Fajr are 6 min apart.
  - Sunrise is not shifted.
  - District offsets: −7 to +8 min for sehri and iftar; Dinajpur, Thakurgaon and Panchagarh +6/+11.
  - Committee of 28 Jun 1993.
  - Fitted: true dawn ≈ 18.5° equivalent; Isha 18° + 1–4.
- **Ramadan 2026: method change** (IFB PDFs on government storage, e.g. …/office-islamicfoundation/2026/0/7b1ace4b-0fff-44ea-99b6-6daa32b98de3.pdf).
  - Separate tables for the 64 districts.
  - Sehri = dawn at the district's **east** edge, seconds dropped.
  - Adhan and iftar = the **west** edge, rounded up.
  - 18° fits exactly. The flat ±3 was dropped.
  - Signed by the muftis led by the Khatib of Baitul Mukarram.
  - 2024 still used the old rule; 2025 was not checked. HIGH.
- **Asr.** Hanafi only.
- **Machine-readable.** PDFs only.
- **App vs official.**
  - Karachi preset with Hanafi Asr, against the year-round table: earlier by up to Fajr 3, Dhuhr 3, Asr 1, Maghrib 5, Isha 4 min, and 3–7 min later than the sehri end. My 5-row check agrees: Fajr −2, Dhuhr −2, Maghrib −3, Isha −2..−1.
  - Against Ramadan 2026: adhan 1–3 and iftar 1–2 min after the app's, and the app's Fajr is 0–1 min after sehri end.

### UZBEKISTAN — Muslim Board (muslim.uz). Massive majority: YES (inferred, MEDIUM)

- **Method.** Documented in code: the islom.uz taqvim JavaScript (/_next/static/chunks/5cea82f971870d15.js) calls adhan-js with:
  - Fajr 15.5°, Isha 15.5°, Hanafi;
  - displayed Maghrib/iftar = sunset **+4 min**.
- The live muslim.uz on 25–26 Sep 2026 equals this exactly (fitted, HIGH).
- **Change (fitted, MEDIUM).** Archived muslim.uz headers from Jul 2024 to 19 Jul 2026 fit Isha 15°, Dhuhr +5 and Maghrib +3. The switch happened between Jul and Sep 2026, possibly with the site rewrite. No dated ruling found.
- **Saharlik.** Equals Fajr at 15.5° (Ramadan 2026: 25 of 30 days exact, 5 days +1).
  - A Board fatwa (kun.uz, 15 Apr 2022, https://kun.uz/news/2022/04/15/taqvimlarda-iftorlik-va-safarlik-vaqtlari-turlicha-bolsa-nima-qilish-kerak) calls the calendar precaution-based and advises personally stopping 10–15 min early.
- **Regional offsets.**
  - Tashkent plus minute differences via the Board's Telegram bot.
  - 2020: separate tables for 26 towns.
  - islom.uz API https://new.islom.uz/api/v1/regions `differ_minute` (secondary): e.g. Nukus +42, Andijon −12.
- **Machine-readable.** No official API. islomapi.uz is third-party and returned 502. islom.uz is "all rights reserved".
- **App vs official (MWL, Tashkent).**
  - Fajr 13–23 min earlier.
  - Maghrib 3–4 min earlier.
  - Isha 8–17 min later.

### KAZAKHSTAN — QMDB (muftyat.kz). Massive majority: YES (inferred, MEDIUM)

- **Method.** Documented in the homepage code:
  - praytimes.js ISNA: Fajr 15°, Isha 15°;
  - Hanafi Asr; AngleBased high-latitude rule;
  - offsets below 48°: sunrise −3, Dhuhr/Asr/Maghrib +3;
  - offsets at or above 48°: sunrise −5, Dhuhr/Asr/Maghrib +5.
  - HIGH.
- **Machine-readable.**
  - API: https://api.muftyat.kz/prayer-times/2026/{lat}/{lng} (365 days); cities list https://api.muftyat.kz/cities/ (5,694 places). Terms not found.
  - The API's `imsak` = Fajr + 10. Its meaning is unknown; do not use it.
- **Changes.** None since 2019, except the default time zone moved from UTC+6 to UTC+5.
- **App vs official (MWL).**
  - Almaty: Fajr 16–32 min earlier; Maghrib 3 min earlier; Dhuhr and Asr 2–3 min earlier.
  - Astana in summer: Isha up to 55 min earlier; Fajr up to 55 min later.

### KYRGYZSTAN — Muftiate (muftiyat.kg). Likely majority (LOW)

- One day of data for 115 localities, from the homepage JSON.
- Bishkek fits:
  - Fajr about 18°, Isha about 16°;
  - Hanafi Asr +2;
  - Maghrib = sunset + 7;
  - sunrise −1.
- No API found.

### TAJIKISTAN, TURKMENISTAN, AZERBAIJAN

Not found (search budget exhausted).

### RUSSIA — no national authority; each muftiate publishes its own. Massive majority nationally: NO; regionally: PARTLY

- **DUM RF (Moscow and oblast).** Sources:
  - https://www.dumrf.ru/img/namaz/MM.jpg (monthly; Wayback copies);
  - the same numbers appear at the Historical Mosque (mosmechet.ru), on muslim.ru and on Mawaqit "Moscow Cathedral Mosque".
  - What the tables show (fitted over 120 days, HIGH):
    - Fajr 18° and Isha 15° outside summer;
    - **Standard Asr**;
    - shuruk −5, Dhuhr +5, Maghrib +5 (±1).
  - Documented on every image: Fajr coincides with the end of suhur.
  - **Summer 2025–26:**
    - Fajr = sunrise − 0.3 × night;
    - Isha = sunset + 0.25–0.3 × night (MEDIUM);
    - in summer 2024 both were fixed 1 h 55 min intervals, so the rule changed between 2024 and 2025.
  - App (MWL) vs DUM RF: summer Isha 29–65 min earlier, summer Fajr 59–87 min later, Maghrib 4–5 min earlier, Dhuhr 4 min earlier, shuruk 5 min later.
- **DUM RT (Tatarstan, 45 localities).** Documented 2026 xlsx: https://dumrt.ru/netcat_files/multifile/2649/vremena_namazov_RT_2026_0.xlsx. Licence **CC BY 4.0**.
  - Sahur end = 18° (rounded down).
  - A separate "mosque Fajr" = sunrise − 91.
  - Zuhr is a fixed time per town (Kazan 12:00).
  - **Hanafi Asr**.
  - Sunrise and Maghrib rounded **up**.
  - Isha 15°.
  - White nights (6 May–8 Aug): sahur = sunrise − 121, Isha = Maghrib + 90.
  - HIGH.
- **DUM Dagestan (Shafi'i).** Source: https://islamdag.ru/vremya-namaza/mahachkala, one month, 26 localities. Fitted, LOW-MEDIUM:
  - Fajr ≈ 18° + 3;
  - sunrise −3, Dhuhr +5, **Shafi'i Asr** +3, Maghrib +5;
  - Isha 15°.
- **TsDUM (Ufa), Chechnya, St Petersburg:** not found.
### UNITED KINGDOM — Massive majority: PARTLY in London (LUPT); NO nationally

**London: London Unified Prayer Timetable (LUPT).**
- **History (documented, HIGH).** First meeting hosted by ICC Regent's Park on 25 Mar 2010; adopted "from 1 Ramadan 1432 (1 August 2011)". Original partners: ICC/London Central Mosque, East London Mosque, Mayfair, Muslim Welfare House, Al Muntada, Al Manaar, Masjid Al Tawhid.
  - Sources: http://www.hizbululama.org.uk/articles/english/Prayer_times.pdf and …/Unified.pdf.
- **Method (documented, HIGH).**
  - Dawn and dusk come from a "mushada-guided computation model" by Khalid Shaukat, based on Hizbul Ulama's Blackburn observations.
  - Sunrise = HMNAO sunrise − 3 min "for safety". Zuhr = noon + 5. Maghrib = sunset + 3.
  - Both Asr times are printed. Valid inside the M25 only.
  - Source: https://www.eastlondonmosque.org.uk/prayer-times-and-calendar-explained.
- **Fitted on the East London Mosque 2026 table (HIGH).**
  - Fajr moves between 12.0° (late June) and 16.25°. Isha moves between 8.6° (July) and 15.6°.
  - Neither is 18°, and summer Isha is not Maghrib + 90.
  - My October check agrees: Fajr and Isha residuals spread about 500 s, so they are not angle-based. The other prayers sit in a 60 s band after the fixed minutes.
- **Imsak.** None; Fajr is "also the time to begin the fast".
- **Machine-readable.** London Prayer Times API, http://www.londonprayertimes.com/api/times/ (free key issued by hand; "completely free for all use"; republishes the ELM table unmodified).
- **Who follows it (MEDIUM).** ICC's printed calendar is identical but printed in GMT all year. In a Mawaqit sample about 37 of 55 London entries match LUPT. The share among Deobandi and Barelvi mosques was not quantified.
- **App (MWL) vs LUPT:**

| Prayer | app − official | When |
|---|---|---|
| Fajr | −24..−11 | Sep–Mar |
| Fajr | up to +60 | May–Jul |
| Sunrise | +2..+3 | all year |
| Dhuhr | −5..−4 | all year |
| Asr | −2..+1 | all year |
| Maghrib | −4..−3 | all year |
| Isha | +4..+25 | Aug–Apr |
| Isha | −22..−12 | June |

- The app's own MOON_SIGHTING_COMMITTEE preset is much closer: Fajr −6..+7, Dhuhr −1..0, Maghrib −1..0, Isha −11..+4.

**UK-wide positions.**
- **Hizbul Ulama (Blackburn).** 98 observations, Sep 1987 to Aug 1988. Fajr is first light, with tabayyun in summer. Isha is shafaq abyad, with shafaq ahmar in summer because of haraj. HIGH.
- **Wifaqul Ulama (Deobandi).**
  - Fajr **18°**, from a 1983 Bradford agreement.
  - Isha 15° at or above 48° (Preston, 11 Nov 2018), with haraj caps.
  - Zuhr = istiwa + 4, Maghrib = sunset + 5, both Asr printed.
  - Rejects the one-seventh rule.
  - Sources: https://www.wifaqululama.co.uk/18degrees/, /highlat/, /salahtimes/ (PDFs are Cloudflare-blocked). HIGH.
- **moonsighting.com.** Seasonal Fajr and Isha, Zuhr +5, Maghrib +3, one-seventh from 55° to 60°. Source: https://moonsighting.com/how-we.html.

**Cities (all no majority).**
- **Birmingham.**
  - Central Mosque uses OpenFajr: camera observations, fitted 12.3–14.3°, endorsed 7 May 2016; it recommends ending suhur 8 min before its Fajr (https://openfajr.org/docs/timetable.pdf).
  - Green Lane Masjid uses Shaukat's Fajr with shafaq ahmar Isha, Zuhr + 5, Standard Asr.
  - About 15 of 29 Mawaqit mosques use about 15°.
- **Manchester.**
  - Central Mosque: Fajr 18° ("FAJR (SEHRI ENDS)"), Isha 15°, Hanafi Asr (https://manchestercentralmosque.org/wp-content/uploads/2026/09/Manchester-Central-Mosque-Time-Table-4.pdf).
  - Didsbury: Fajr = sunrise − 90, Zuhr + 5, Maghrib + 3.
- **Bradford.**
  - The Council for Mosques table was not found.
  - On Mawaqit, the Faizan-e-Madina network uses 18°/18°, and others use 15°.
- **Glasgow.**
  - Central Mosque: Fajr about 17.6°, Isha 18°, Maghrib + 7, Zuhr about + 6, both Asr printed. Its site redirected to a gambling domain (see the incidents above).
  - 8 of 11 Mawaqit mosques use 15–16°.
- **Leicester and Blackburn.** Mixed, or too few samples.

### IRELAND — Massive majority: NO

- **ICCI (Clonskeagh) website.**
  - Code documented: adhan-js MuslimWorldLeague with TwilightAngle and Shafi'i Asr, at 53.3498, −6.2603. The page is labelled "IACAD Dublin Prayer Timetable".
  - App vs ICCI: identical from Nov to Jan; from Apr to Aug the app's Fajr is up to 85 min later and its Isha up to 75 min earlier. HIGH.
- **Islamic Foundation of Ireland / Dublin Mosque.**
  - A fixed GMT table for 28 towns, embedded at https://islamicfoundation.ie/timetable/. Also used by Lucan, Finglas and Al-Khidmah.
  - Fitted: Fajr 11.4–16.4°, Isha 9.3–15.6°, sunrise 1–5 min early, Maghrib 0–6 min late, Standard Asr.
  - App vs IFI: Fajr −15..−12 in winter and up to +71 in summer; Maghrib up to −7; Asr up to −6; Isha up to −42 in summer.
- **Imsak.** Neither publishes one.

### FRANCE — Massive majority: NO (HIGH)

- **No national table.** The CFCM domain is parked. UOIF's 12° is consistent with what mosques use, but I found no UOIF source.
- **Grande Mosquée de Paris.** Changed method for 2026 (fitted, HIGH).
  - January to September 2026 match adhan's MOON_SIGHTING_COMMITTEE exactly, with Isha = sunset + 90.
  - October 2025 was different: about 18° tapering to 14.5°, Zuhr + 1.
  - No method is stated anywhere.
- **Mawaqit surveys (MEDIUM).**
  - Île-de-France, 224 mosques:
    - Fajr families: the 12–13° family 40%, the old GMP 14%, a flat 15° 12%, GMP-2026 5%, 18° 3%.
    - Maghrib + 3/+4 at 78%. Standard Asr at 96%. Imsak not configured at 211.
  - Lyon: about 70% share one table (about 14.5° Fajr, 13.5° Isha).
  - Marseille: 65% use 13°/13° with Zuhr + 5 and Maghrib + 3.
- **App (MWL) vs these.**
  - Against the Paris 12–13° family: Fajr 32–35 min earlier in winter and up to 41 min later in June; Isha up to 42 min earlier in June; Maghrib −4..−2; Dhuhr −4.
  - Against Lyon and Marseille: Fajr 17–53 min earlier all year. Both cities are below 48°, so the app uses 18° there.

### BELGIUM — Executive of Muslims of Belgium (EMB). Massive majority: PARTLY

- **Source.** Yearly PDF: https://www.emb-net.be/sites/default/files/horaire_priere_emb_2026.pdf. One table for Brussels, Antwerp and Charleroi, plus fixed year-round offsets for 18 towns (e.g. Oostende +6, Liège −5). Documented.
- **Method (fitted, HIGH).**
  - Fajr 18°, Isha **18°**.
  - Sunrise −2, Dhuhr 0, Asr + 0..1, Maghrib + 2.
  - My October check: official minus raw sits in a 51–68 s band per prayer.
- **Summer rule (documented in the PDF).** From 1 May to the end of July, above 45°, Fajr, Isha and imsak use "التقدير النسبي لخط عرض 45" (a proportional estimate from latitude 45). Joining Maghrib and Isha is allowed in that period.
- **Imsak.** Same as Fajr.
- **Machine-readable.** PDF only.
- **App (MWL) vs EMB, 360 days:**

| Prayer | app − official | Days app is earlier |
|---|---|---|
| Fajr | −1..+90 | 6 |
| Sunrise | +1..+3 | – |
| Dhuhr | 0..+2 | – |
| Asr | −2..+1 | 167 |
| Maghrib | −2..−1 | 360 |
| Isha | −65..−6 | 360 |

- **Who follows EMB.**
  - On Mawaqit, 42 of 64 Brussels mosques use EMB's Asr, Maghrib and Isha. About 23 put Fajr at sunrise − 90.
  - About 70 Diyanet mosques (out of 328–380 Belgian mosques) follow Diyanet instead.

### NETHERLANDS — Massive majority: NO (HIGH)

- **No authority issues times.** CMO is a contact body.
- **At least four families of timetable:**
  1. **Diyanet (ISN).**
  2. **A shared Moroccan calendar.** 22 of 34 Amsterdam Mawaqit mosques carry it.
     - Fajr goes from about 16.3° in winter to 14° in spring and autumn, with a floor of 03:30.
     - Isha goes from 15.4° to 14.4°, capped at 23:30.
     - Sunrise −2, Dhuhr + 1, Maghrib + 1..+3.
     - The app's Fajr is up to 13 min **earlier** than it from Oct to Mar.
  3. **Plain 18°/17°.**
  4. **Awqati observation tables.** Open API: https://waqti.nl/api/v1/salaat?source=awqati&… (no token, 60 requests per minute). The app's Fajr is about 40 min earlier than Awqati.
- Surinamese and Pakistani mosques use Hanafi Asr.

### GERMANY — Diyanet (DITIB, IGMG). Massive majority: PARTLY (MEDIUM)

- **Who uses it.**
  - DITIB links its times to Diyanet (https://www.ditib.de/).
  - IGMG's calendar is byte-identical to Diyanet on 123 of 123 Berlin days.
  - In Berlin, 17 of 21 Mawaqit mosques show Diyanet-equivalent times.
  - Mosque counts (secondary, BAMF via Wikipedia): about 2,342 mosques; DITIB 896, IGMG 304, VIKZ about 300. Diyanet coverage is therefore about 51% before counting copies.
  - VIKZ uses Fazilet Takvimi instead. Its Isha is 10 min later than Diyanet's, and it prints separate imsak and sabah times.
- **Diyanet's method.**
  - Temkin (documented, HIGH): sunrise −7, Dhuhr + 5, Asr + 4, Maghrib + 7; none on imsak or Isha. Set in 1982, applied since 1983. Source: https://vakithesaplama.diyanet.gov.tr/temkin.php.
  - Angles: Fajr 18°. Isha **16° in Europe since 1 Jan 2023**, by the 2021 congress method (documented; see the Diyanet section below). Turkey itself stays at 17° (Istanbul and Ankara fit 17°).
  - Imsak = Fajr (documented, https://vakithesaplama.diyanet.gov.tr/sabah_ezani.php).
  - Asr = shadow 1 + 4 min, even though the community is Hanafi.
  - High latitudes: the 2023 congress takdir rule north of 44.5°N (see the Diyanet section below). It superseded Kurul decision 61 of 2009.
  - Diyanet's sunset is 1–2 min later than adhan's, so even the TURKEY preset gives Maghrib and Asr early on about half of the days.
  - Every town is computed separately.
  - Awqatsalah API: login, JWT, commitment form and quota.
- **App (MWL) vs Diyanet Berlin, 396 days:**

| Prayer | app − official | Days app is earlier |
|---|---|---|
| Fajr | −1..+51 | 19 |
| Sunrise | +6..+8 | – |
| Dhuhr | −5..−3 | 396 |
| Asr | −7..−2 | 396 |
| Maghrib | −9..−5 | 396 |
| Isha | Oct–Feb +4..+8; Apr–Aug −38..−10 | 190 |

- **Freiburg (47.99°N) shows the rule flip.** The app uses middle-of-the-night there, so its June Fajr is up to 90 min **earlier** than Diyanet's imsak.
### DIYANET'S EUROPEAN METHOD SINCE 2023 (applies to Germany, Austria, Switzerland, the Nordics, and Diyanet mosques in Belgium, the Netherlands and Australia)

**Documented, HIGH:**
- **Origin.** The International Congress on unifying prayer times (Istanbul, 26–27 Sep 2021), followed by a committee statement on 26 Jul 2022.
  - Isha lowered "من 17 درجة إلى 16 درجة" (from 17° to 16°).
  - Fixed summer times abolished.
  - Applied from 1 Jan 2023.
  - Participants named: DITIB, IGMG, the Danish Islamic Union, the European Council for Fatwa and Research (ECFR).
  - Sources: https://www.islamiskaforbundet.se/bonetiders-kalla and http://derislam.at/service/einheitliche-gebetszeiten/islamischer-kalender-gebetszeiten/
- **Rules as IGGÖ states them.**
  - Fajr 18°, Isha 16°.
  - Precaution minutes: imsak 0, sunrise −7, Dhuhr +5, Asr +4, Maghrib +7, Isha 0.
  - An estimation (takdir) rule north of 44.5°N, with a gradual 20-minute transition and 21 June as the reference day.
- **It replaces the 2009 Kurul decision no. 61** (https://kurul.diyanet.gov.tr/Cevap-Ara/Karar/4093/45-enlemin-otesinde-namaz-vakitleri).

**Fitted (MEDIUM):** the written takdir does not match the tables.
- The text says "one third of the night added to Maghrib". The published tables instead give, in core summer:
  - Isha ≈ Maghrib + 18.5–19% of the night;
  - Fajr ≈ sunrise − 20.6% of the night.

**Fitted (HIGH):** where 16° applies.
- 16° is used at Sarajevo and further north.
- Prishtina and Tirana still use 17°.
- The boundary between them is not documented.

**Consequence for the app.** The TURKEY preset (17°) is wrong for Diyanet in Europe.
- CUSTOM:18:16:0:0:-7:5:4:7:0 fits outside summer to within ±2 minutes.
- Diyanet's sunset runs 1–2 min later than adhan's, so Maghrib and Asr can still come out early on some days.

### BOSNIA AND HERZEGOVINA — IZ BiH (Rijaset) vaktija. Massive majority: YES (HIGH)

- **Authority.** The official astronomer (muvekit) and the Takvim commission compute it. The Vijeće muftija adopts it, and it then "ima snagu fetve" (has the force of a fatwa). Source: https://islamskazajednica.ba/index.php/vijesti/aktuelno/23328-povodom-izlaska-iz-stampe-takvima-za-2016-godinu-kako-razumijevati-takvim
- **Method.** Documented in the Takvim 2025 introduction, https://islamskazajednica.ba/images/download/Takvim_2025.pdf
  - Zora (Fajr, and the start of the fast): 18°.
  - Jacija (Isha): 16°.
  - Sunrise: astronomical − 6 min.
  - Akšam (Maghrib): sunset + 6 min, "za relativnu nadmorsku visinu od 920 m" (for a relative elevation of 920 m).
  - Podne (Dhuhr): transit + "1-2 minute".
  - Ikindija (Asr): **first shadow (Standard), not Hanafi**. Fitted −1.4 to +1.1 minutes from Shafi'i.
  - Rounding: to nearest.
  - Imsak is aligned with Diyanet by decision of the Vijeće muftija.
- **One morning time only.** The table prints a single column, "Zora (Sabah)", which is also the imsak.
- **Change.**
  - Before: 19°/17°. The Takvim 2016 text says so, and the 2022 tables still measure 19.0°/17.1°.
  - vaktija.ba commit 29f79c30 "nova vremena 25" (29 Dec 2024), effective 1 Jan 2025: zora 4–14 min later, jacija 4–12 min earlier.
  - Fitted 2025 onward: 18°/16°. HIGH.
- **Towns.** Sarajevo plus whole-minute monthly offsets (documented formula).
  - Verified: **towns south of Sarajevo have the latitude term with the wrong sign.**
  - Example, Trebinje in June: the table's zora is 4 min earlier than Sarajevo, but the true 18° time is 12 min later. An exact calculation there shows Fajr up to about 16 min *later* than the official zora (fasting risk).
  - Novi Pazar and Mostar are affected the same way.
- **Machine-readable.**
  - API: https://api.vaktija.ba/vaktija/v1/{id}[/{year}[/{month}[/{day}]]] (Sarajevo = 77; 118 locations; rate-limited, HTTP 429).
  - GitHub vaktija/vaktija.ba src/data/vaktija.json (no licence).
  - The IZ says commercial copies "krše autorska prava" (infringe copyright).
- **App (MWL, Standard Asr) vs Sarajevo 2026.**

  | Prayer | app − official (min) | Days app is earlier |
  |---|---|---|
  | Fajr | −1..+1 | 1–32 |
  | Sunrise | +4..+8 | — |
  | Dhuhr | −1..+1 | — |
  | Asr | −2..+2 | 80–108 |
  | Maghrib | −8..−4 | every day |
  | Isha | +4..+12 | — |

  - The app's Hanafi setting gives Asr +34..+75, i.e. off the majority.
  - Best fit: CUSTOM:18:16:0:0:-6:1:0:6:0 (±2).
  - Recommendation: use the official town table.

### KOSOVO — BIK Takvimi. Massive majority: YES (MEDIUM)

- **Authority.** The Takvimi is a "dokument zyrtar" (official document) for Kosovo and the Preševo valley: https://dituriaislame.com/wp-content/uploads/2026/01/takvimi2026vaktet.pdf
- **Method.**
  - Documented:
    - one table computed for 42.5°N 21°E;
    - "1,5 gradë – përkatësisht 6 min": sunrise −6 and sunset +6;
    - imsak ends suhoor;
    - **Sabah prayer = imsak + 20 min**;
    - town offsets of −2..+2 min.
  - Fitted: imsak about 19°, Isha about 18.5°. The table is an irregular, old perpetual one.
  - © reproduction forbidden.
- **App (MWL) vs Prishtina, 365 days.**

  | Prayer | app − official (min) | Note |
  |---|---|---|
  | Fajr vs imsak | +3..+11 | app later: fasting risk |
  | Dhuhr | −5..0 | early on 329 days |
  | Asr | −12..+1 | early on 345 days |
  | Maghrib | −9..−4 | early every day |
  | Isha | −11..−4 | early every day |

### ALBANIA — KMSH, which equals Diyanet. Massive majority: YES (HIGH)

- **Source.** Kalendari 2026, https://admin.kmsh.al/uploads/2026/05/file-1778275088384-86c31e4382780837.pdf
  - Tirana reference point with constant town offsets.
  - Identical to Diyanet Tirana (https://namazvakitleri.diyanet.gov.tr/tr-TR/11203) on 31 of 31 days checked.
- **Method.** 18°/17° with the Turkish precaution minutes; first-shadow Asr.
- **App vs KMSH.**
  - MWL: Dhuhr −5..−3, Asr −6..−2, Maghrib −9..−6, early on every day.
  - The TURKEY preset comes within ±2 minutes.

### MONTENEGRO / SANDŽAK / NORTH MACEDONIA (agent-reported, LOW)

- The Islamic Community of Montenegro (IZCG) has its own table: about 19°/18°, Maghrib +8.5. It conflicts with vaktija.ba.
- Sandžak towns inherit the south-of-Sarajevo sign error.
- North Macedonia: not verified.

### NORWAY — Islamsk Råd Norge (IRN) joint calendar (bonnetid.info). Massive majority: MOSTLY in Oslo (MEDIUM-HIGH)

- **History.** Documented at https://bonnetid.info/felles-bonnetidsprosjekt-i-norge/ and https://bonnetid.info/om-prosjekt/
  - Late 1990s: the night divided into 7.
  - 2014/15: 3 zones and standard safety margins.
  - June 2020: a joint project with ICC and Rabita.
  - A national calendar was issued before Ramadan 2022 (agent-reported).
- **Who follows it.**
  - CJAS (the largest mosque) = IRN with Hanafi Asr (verified).
  - Rabita: identical (agent-reported).
  - Turkish and Bosnian mosques use Diyanet Oslo, which differs.
- **Method.** Fitted over 365 days (HIGH):
  - Fajr 16°, Isha 15°.
  - Summer: Fajr = sunrise − 60, then frozen at fixed times. Isha = Maghrib + 40 at the solstice, reached by a ramp.
  - Maghrib +3..+6, Dhuhr +4..+10.
  - Asr: both 1× and 2× printed.
  - No imsak.
- **App vs IRN Oslo.**

  | Prayer | app − official (min) | Days app is earlier |
  |---|---|---|
  | Fajr | −8..+82 | 116 |
  | Dhuhr | −9..−3 | every day |
  | Maghrib | −6..−3 | every day |
  | Isha | −36..+18 | 137–146 |

- **Machine-readable.** api.bonnetid.no needs a token obtained by contacting IRN. © IRN.
- **App bug (verified by the agent).** adhan2 throws IllegalStateException (PrayerTimes.kt:168) at Tromsø on 21 Jun and 21 Dec with TWILIGHT_ANGLE. That is the app's own rule at ≥65°N.

### SWEDEN — no national authority. Massive majority: NO (MEDIUM)

- Islamiska Förbundet / Stockholm Mosque publishes Diyanet Stockholm, i.e. the 2023 congress method.
- Other mosques differ. Example: Malmö uses sunrise − 94 and Maghrib + 90 all year (agent-reported).
- **App vs Stockholm Mosque:**
  - Fajr +6..+56, later on every day (fasting risk).
  - Dhuhr, Asr and Maghrib earlier every day (Maghrib −10..−5).
  - Isha −48..+9.

### DENMARK — no authority. Massive majority: NO (MEDIUM-LOW)

- The Grand Mosque Copenhagen (HBKCC) ≈ Diyanet Copenhagen, except that from May to early August its Isha = Maghrib + 10 (the prayers are combined).
- **App vs HBKCC:**
  - Fajr up to +58.
  - Maghrib −10..−4 every day.
  - Isha −45..+57.

### FINLAND — no authority. Massive majority: NO (LOW-MEDIUM)

- **Rabita Helsinki (Mawaqit).**
  - Outside summer: 18°/17°, Dhuhr +2, Maghrib +3..+6.
  - Summer: Isha ≈ Maghrib + 82–88, Fajr held almost fixed in the small hours for the summer.
- South Asian mosques use Isha = Maghrib + 60.
- **App vs Rabita:**
  - Fajr +9..+141.
  - Isha −138..0.
  - Dhuhr and Maghrib earlier every day.

### AUSTRIA — IGGÖ, which equals Diyanet. Massive majority: LIKELY (MEDIUM; documented method, HIGH)

- One national calendar "seit dem 1. Jänner 2023" (since 1 Jan 2023), using the congress method.
- City PDFs: https://www.derislam.at/api/prayer-times/pdf/?location=wien&month=6
- Matches Diyanet Wien on 121 of 122 days.
- **App vs Vienna:**
  - Dhuhr, Asr and Maghrib earlier every day (Maghrib −9..−6).
  - Isha −27..+8.
  - Fajr up to +37 (June).

### SWITZERLAND — FIDS, which equals Diyanet. Majority not established (LOW-MEDIUM)

- fids.ch/gebetszeiten = Diyanet Zürich on 31 of 31 days checked.
- **App vs Zürich** (47.4°N, so the app uses the middle-of-the-night rule):
  - Fajr −68..+1: up to 68 min **earlier** in summer.
  - Dhuhr, Asr and Maghrib earlier every day.
  - Isha +4..+60.
### NORTH AMERICA: positions of the bodies (US and Canada)

- **ISNA / Fiqh Council of North America (FCNA).** Advisory only; nothing binds mosques.
  - **Dec 2010:** "tentative consensus" on 18°. Source: https://astronomycenter.net/articles/2010/12/26/33?l=en
  - **25 Sep 2011:** FCNA adopted the moonsighting.com functions, with 17.5°/15° as an option. Source (FCNA email quoted by the IAC): https://astronomycenter.net/articles/2012/07/30/81?l=en. HIGH.
  - **27–29 Oct 2017 (current):** FCNA "suggests using 15° for both Fajr and Isha in the USA and using 13° for both Fajr and Isha in Canada, throughout the year". It also advises delaying Fajr and Isha a few minutes and starting the fast a few minutes early. It gives no high-latitude rule. Source: https://fiqhcouncil.org/the-suggested-calculation-method-for-fajr-and-isha/. HIGH.
  - **18 Sep 2024:** FCNA reaffirms 15°. Source: https://fiqhcouncil.org/fifteen-or-eighteen-degrees-calculating-prayer-fasting-times-in-islam/
- **ICNA:** no position found (LOW).
- **Moonsighting Committee Worldwide.** Source: https://www.moonsighting.com/how-we.html (updated 1 Mar 2024).
  - Fajr and Isha are seasonal functions. Fajr is the later of the function and 18°; Isha is the earlier.
  - One seventh of the night from 55° to 60°; above 60°, the 60° times are used.
  - Zuhr = zenith + 5; Maghrib = sunset + 3.
  - adhan2's MOON_SIGHTING_COMMITTEE preset reproduces the official Hanafi (Shafaq General) table to ±1 min in Toronto (all of 2026), Calgary and Edmonton, except Asr.

**App defect found here (code verified by me).** In adhan2 0.0.7, `SolarTime.afternoon()` builds the Asr shadow angle from `solar.declination`, taken for the day's Julian date at 0h UT, not at transit.
- In the Americas the day's transit is 17–20 h after that, so the declination used is stale. Asr shifts by up to about ±1.5 min with the season.
- The North America agent's measurement: against an exact calculation, the app's Asr is 1 min early on about 1/3 of days, and 2 min early on some days (37 of 183 sampled days in Edmonton).
- I confirmed the line in the library source, `internal/SolarTime.kt:65`. The magnitude is the agent's measurement.

### UNITED STATES — FCNA/ISNA 15°/15°. Massive majority: YES for the angles (HIGH)

- **Share of mosques on 15°/15°:**
  - 36 of 42 sampled named mosques.
  - Mawaqit: 39 of 43 computed US tables.
  - Madina Apps (full survey, Sep 2026 window, fitted): 73 of 93 computed US timetables (78%); 8 use 18°/15° (5 in Illinois), 5 use 18°/18° (all Texas), 7 are near-15° variants; 4 fixed/manual tables excluded. Canada: 3 of 10 use 15°/15°, 2 MWL (Alberta), 5 all different.
  - Caveat: the platforms' defaults could inflate these counts.
  - Houston's ISGH publishes one table for all its centres, "Calculation Method: Islamic Society of North America" (https://isgh.org/prayer-schedule-september/).
  - Chicago's CIOGC has no shared table (https://www.ciogc.org/ramadan/).
- **Exceptions:**
  - A Chicago block uses Fajr 18° / Isha 15° with Hanafi Asr. Islamic Foundation Villa Park prints "Fajr 18 degrees Isha 15 degrees Asr Hanfi" (https://islamicfoundation.org/pdf/if_prayer_calendar_2026.pdf).
  - Some Texas South Asian mosques use 18°/18° Hanafi (Masjid Yaseen, Garland).
  - The Islamic Center of Washington uses 19.5°/15°.
  - Al-Farooq Atlanta uses MWL.
  - Turkish mosques follow Diyanet.
- **Precaution minutes:** none in FCNA's rule, but many mosques add them.
  - Maghrib +1..+5: Mosque Foundation, MCC and MEC Chicago, ISOC, Irving, Plano, MCA, King Fahad, Dar Al Noor, ADAMS.
  - Dhuhr +2..+5.
    - MCC: "2 MINUTES PAST THE MERIDIAN".
    - MCA Santa Clara: "Observatory midday time + THREE minutes correction", and the same for sunset (https://mcabayarea.org/wp-content/uploads/2025/12/2026_MCA_Prayer_Time.pdf).
  - ADAMS adds ±2 min to every prayer in the safe direction, "Based on Fiqh Council of North America Methodology" (https://adamscenter.org/wp-content/uploads/2026/02/ADAMS-Prayer-Times-2026.pdf).
- **Rounding:** nearest minute. Some mosques add a flat +1.
- **Reference point:** each mosque's own coordinates. MCC Chicago uses downtown plus 1 min per 13 miles west. No elevation correction.
- **Asr:** Standard dominates, but Hanafi appears at about a third of mosques (14 of 42 named; 29 of 93 on Madina), and at nearly all of Chicago, Arab mosques included.
- **Imsak:** printed by none.
  - FCNA says "a few minutes" early; moonsighting.com's FAQ suggests finishing eating 20–30 min before a 15° Fajr.
  - American Moslem Society Dearborn has Fajr − 10 in its page data but does not display it.
- **Machine-readable:**
  - Masjidal: https://masjidal.com/api/v1/time/range?masjid_id=…
  - Madina Apps: services.madinaapps.com/kiosk-rest/clients/<id>/prayerTimes
  - Mawaqit (rate-limited).
  - moonsighting.com praytable.php ("Copyright © moonsighting.com"; no terms).
  - Terms of use: not checked.
- **App (ISNA preset) vs 15°/15° mosques:**
  - Fajr, Maghrib and Isha within ±1, the app earlier on 1–8% of days. Dhuhr 0..+2. Asr −3..+3, the app earlier on 30–45% of days (the declination defect above).
  - At mosques that add minutes, the app is earlier every day by 1–5 min on Maghrib and Dhuhr.
  - At the 18° mosques, the app's Fajr is 14–29 min **later**. That is a fasting risk against their Fajr, even though it is not an early-prayer error. Against Masjid Yaseen, the app's Isha is 14–20 min early.
  - ICCNY Manhattan is not within 3 min: its table appears to be computed for about 43.5°N, giving Fajr −6..+17 and Isha −19..+4.

### CANADA — no authority; FCNA's 13° is not used. Massive majority: PARTLY (east only)

- **Fits across 41 mosques:**
  - 19 are exactly ISNA 15°/15°.
  - 24 use a 15° Fajr with some Isha rule.
  - 0 use 13°/13°.
- **By city:**
  - **Montreal:** 12 of 16 ISNA. The others are 18°/15° with Maghrib +3..+4, e.g. Assuna Annabawiyah "18.00° / 15.00°" (https://www.mosqueprayertimes.com/assunaannabawiyah.min.js).
  - **Ottawa:** 6 of 6 ISNA.
  - **Toronto:** no majority.
    - MAC Masjid Toronto and Anatolia are ISNA. My own October check of MAC: plain 15°/15°, rounded to nearest, with no Dhuhr +1 and Asr +2..+3.
    - Hilal Committee / Madina Masjid: 15°/12° Hanafi.
    - IFT and SMA: "Fajr Dawn 15 dg / Isha Night 13.5° dg".
    - IIT: Isha = Maghrib + 90.
    - TARIC: 18°-type.
    - MAC ICCO Mississauga: MCW-like, with Dhuhr +5 and Maghrib +3.
  - **Calgary, Edmonton and BC:** each mosque has its own summer rule. Examples:
    - Edmonton on 21 June: the mosques' Fajr is about an hour earlier than the app's, and their Isha 20 to 60 min later than the app's, some after midnight.
    - Calgary Islamic Centre SW: Isha = Maghrib + 90.
    - Surrey Jamea: 18°-type, with both Asr times printed.
- **Asr:** Standard at the Arab-majority Montreal and Ottawa mosques; Hanafi at South Asian mosques (IFT, SMA, Hilal, Markham, BCMA).
- **Imsak:** none printed. IFT: "IF FASTING FINISH EATING FEW MIN. BEFORE FAJR TIME".
- **Clock issue (verified by me).** This Mac's tzdata 2026b moves America/Vancouver to permanent UTC−7 from 1 Nov 2026; America/Edmonton keeps its daylight-saving changes. BC's legal basis was not verified from a primary source.
  - Devices with older tzdata, and several mosque calendars, will be 1 h off in BC from November 2026.
  - moonsighting.com's own table keeps Edmonton at UTC−6 in Nov–Dec 2026, which contradicts tzdata.
- **App (ISNA) vs official:**
  - Montreal and Ottawa ISNA mosques: ±1.
  - The 18°/15° mosques: Fajr +17..+39 (app later) and Maghrib −4..−2 every day.
  - Edmonton summer: Fajr about +60 (fasting risk) and Isha −18..−30.
  - Against moonsighting.com's official Edmonton table: the app's Isha is earlier on 71 days, by up to 21 min.
### SOUTH AFRICA: Gauteng and KwaZulu-Natal
**Massive majority: PARTLY.** The published table is unified; nobody has measured what share of mosques follow it.

One shared "Perpetual Salaah Times" system is published by three bodies:
- Jamiatul Ulama SA: https://jamiat.org.za/salaah-times.php
- Darul Ihsan: 493 cities, https://salaahtimes.starlite.za.net/darul_ihsan/perpetual/
- Jamiatul Ulama KZN: relayed by masjids.co.za, whose times are identical minute for minute on all 122 days tested.

**Method.** Fitted on full-year CSVs for Johannesburg, Durban, Pretoria and Cape Town, except where marked documented.

| Item | Rule | Confidence / source |
|---|---|---|
| Fajr | 18°, nearest minute | fitted, HIGH |
| Isha (Hanafi) | 18° | fitted, HIGH |
| Isha (Shafi'i) | 15° | fitted, HIGH |
| Zuhr | noon + 5; "Zawaal Starts" is noon − 5, and prayer is "NOT ALLOWED" between the two | documented |
| Maghrib | sunset + 3: "A 3 minute safety margin is added to the Sunset time" | documented |
| Sunrise | no margin | fitted |
| Asr | Standard and Hanafi both printed | documented |
| Suhoor ends | Fajr − 5 | documented column |
| Reference points | one table per town, coordinates to whole arc-minutes | documented |
| Elevation | none (Johannesburg is at about 1750 m) | fitted |
| Drift | about ±1 min, because the table is a perpetual mean year | fitted |

- Documented rules come from the guide: https://salaahtimes.starlite.za.net/darul_ihsan/perpetual/Darul%20Ihsan%20-%20Salaah%20Times%20Guide.pdf
- My own check of Jamiat Johannesburg 2026 agrees: Fajr and sunrise ≈ 0, Dhuhr +268..+340 s, Maghrib +127..+257 s.
- **Machine-readable:** one CSV per city, e.g. https://salaahtimes.starlite.za.net/jamiat/perpetual/export-csv.php?id=fdead345d15555d292e52a041aef1c4e402369b8. No terms of use found.
- **jamiatsa.org's new 2026 calculator is broken.** It claims MWL 18/17, but it prints Johannesburg's Dhuhr at one and the same minute every day of October. Do not use it.
- **App (MWL) vs this table.** Negative means the app is earlier.

| Prayer | App − table |
|---|---|
| Dhuhr | −5..−3 |
| Maghrib | −4..−2 |
| Isha (Hanafi) | −7..−4 |
| Asr (app Standard vs Hanafi) | −80..−43 |

- CUSTOM 18/18 with Dhuhr +5 and Maghrib +3 reproduces the table to ±1 minute.
- Adding one more minute to each prayer makes the app never earlier in Johannesburg and Pretoria. Durban's Isha is still early on a few days.

### SOUTH AFRICA: Cape Town
**Massive majority: NO.** There are three tables.
- **MJC website:** https://mjc.org.za/salaah-times/. No method is stated; it behaves like MWL, with no Dhuhr +1 and Asr 1–2 minutes earlier. Radio 786 documents MWL with an 18° Fajr, Shafi'i Asr, and the Fajr time as the end of sehri: https://www.radio786.co.za/salaah-times/. The app is never meaningfully earlier than this table.
- **Community calendar:** VOC, the Wembley calendar and masjids.co.za carry identical numbers. Fitted:
  - Fajr ≈ 16.5°
  - Isha ≈ 15.3–15.4°
  - Dhuhr = noon + 3 (documented columns)
  - Maghrib = sunset + 2 to 3
  - fixed town offsets
  - App vs this calendar: Fajr −11..−6, Dhuhr −3..−1, Maghrib −4..−1.
- **Jamiat perpetual table for Cape Town:** same rules as Gauteng/KZN. My October check found its Shafi'i Isha is 15°.

### AUSTRALIA
**No dominant table (HIGH).**
- ANIC publishes no timetable: https://anic.org.au/?s=fajr. The Islamic Council of Victoria's prayer page returns 404.
- **Lakemba Mosque (LMA) 1448 PDF.** No method stated; 349 days fitted.
  - Fajr: 18°, rounded down.
  - Sunrise: rounded down.
  - Maghrib: sunset, rounded up.
  - Isha: Maghrib + 90.
  - Dhuhr: noon + 2..+7, irregular.
  - Asr: irregular. It runs −11..+22 minutes against the Shafi'i Asr, and is printed *before* the astronomical Asr from about 21 Jul to 6 Sep and from 17 Jan to 17 Apr.
  - The LMA website gets the DST day (4 Oct 2026) wrong by one hour; the PDF is right. I found the same error independently.
  - App vs LMA: Dhuhr −6..−1, Asr up to −22 (Oct–Jan), Isha earlier from about Feb to Nov.
- **Diyanet per-suburb tables** (e.g. https://namazvakitleri.diyanet.gov.tr/tr-TR/11421) equal the TURKEY preset to ±1 minute.
- **Mawaqit sample** (Sydney, Melbourne, Brisbane): mixed.

### NEW ZEALAND
**FIANZ. Massive majority: unknown.**
- Source: https://fianz.com/prayer-times/, 20 cities.
- Fitted (MEDIUM-HIGH): Fajr 18°, Isha 18°, Shafi'i Asr, no offsets, nearest minute.
- App (MWL): Isha 4–8 minutes earlier; everything else within ±1.
- The site also carries an older widget that calls Aladhan's MWL method, which may mean an undated change (LOW).
- No method statement and no imsak found.

### NIGERIA, SENEGAL, KENYA, TANZANIA, SOMALIA, ETHIOPIA
**No authority table found for any of them.** Details:
- **Nigeria:** NSCIA's site has no prayer times, and JNI's site did not resolve. Abuja mosques on Mawaqit are mixed (MWL-like, ISNA, a fixed Dhuhr well after the transit). LOW.
- **Senegal:** Dakar mosques on Mawaqit are mixed (MWL; ≈15° Fajr; a late fixed Dhuhr and Asr, each more than an hour after the app's). All of these are later than the app. LOW.
- **Kenya:** Jamia Mosque Nairobi's Ramadan 1447 PDF, made "in collaboration with Kenya Meteorological Department": https://jamiamosque.co.ke/wp-content/uploads/2026/02/Ramadhan-Timetable-1447-2026.pdf. Its suhoor ≈ Fajr 18° with no margin, and iftar ≈ sunset. No year-round table; SUPKEM's site has none. MEDIUM.
- **Tanzania:** BAKWATA's site is a shell. The Darul Ihsan perpetual table covers Dar es Salaam and Zanzibar with the South African rules; who uses it is unknown. LOW.
- **Somalia:** three Mawaqit mosques ≈ MWL. LOW.
- **Ethiopia:** nothing found.
## Corrections to the 25 September findings

- **Singapore.** "Within a minute" is true. The app is still 1 min early on 72–119 days per prayer in 2026, and up to 2 min early in 2025.
- **Indonesia.** "About 2 min" is confirmed and refined.
  - Fajr, Asr and Isha are +2 and rounded up.
  - Dhuhr is about +3; Maghrib is about +3 (fitted 2.7–3.7).
  - Sunrise is −3..−4 against the app.
  - The reference is the regency capital.
- **Malaysia.**
  - The 18° Fajr is confirmed. It was decided on 20–21 Nov 2019 and took effect between Dec 2019 and Feb 2020, depending on the state.
  - The app's other prayers are also 0–3 min early.
  - The mechanism is zone reference points (latest point, rounded up), not a flat +2.
  - Kelantan's Isha fits 17°.
- **Pakistan and India.**
  - The Banuri calculator equals the Karachi preset exactly.
  - The printed Karachi permanent timetable puts the app up to 1 min late for sehri and 1 min early for iftar.
  - The Standard-vs-Hanafi Asr gap is 44–79 min, not a flat 50.
  - Dawat-e-Islami adds elevation and 1-minute margins.
- **Bangladesh.**
  - +3 on Fajr, Dhuhr and Maghrib is confirmed for the year-round table. Sehri is a separate column 3 min before true dawn.
  - Ramadan 2026 switched to per-district east- and west-edge tables.
- **Uzbekistan.**
  - 15.5°/15.5° Hanafi is confirmed, but the app also needs Maghrib +4.
  - The site's rule changed between Jul and Sep 2026 (before that: Isha 15°, Dhuhr +5, Maghrib +3).
- **South Africa.**
  - "MJC = MWL exactly" holds only for the MJC website, and not exactly.
  - The widely used VOC/Wembley calendar is about 16.5° Fajr with Dhuhr +3 and Maghrib +2..3.
  - Jamiat is Dhuhr +5 and Maghrib +3, as stated, plus an 18° Hanafi Isha, a 15° Shafi'i Isha, and Suhoor = Fajr − 5.
- **UK.**
  - LUPT applies inside the M25 only.
  - Its Fajr/Isha difference against the app reverses by season: in summer the app's Fajr is up to 60 min later and its Isha up to 22 min earlier.
  - The app's Dhuhr (−5..−4) and Maghrib (−4..−3) are early all year.
  - Outside London there is no majority.
- **France.** "Most Île-de-France mosques use 12–13°" is an overstatement: it is 40% of the Mawaqit sample. The GMP changed its method for 2026.
- **Belgium.** EMB's Isha is 18°, which explains the 6–7 min in October. It is up to 65 min later in summer (the 45° proportional rule). Maghrib is +2 every day.
- **Netherlands.** There are at least four timetable families. The dominant Moroccan calendar has a Fajr later than the app's in October–March.
- **Germany.** Diyanet since 2023: Isha 16° with the congress summer rule.
  - The app is early every day at Dhuhr, Asr and Maghrib (up to 9 min).
  - Isha is up to 38 min early in summer, and Fajr up to 51 min late.
- **Russia.** "18°/15°" holds only outside summer, and only for DUM RF Moscow.
  - DUM RF also applies shuruk −5, Dhuhr +5, Maghrib +5 and Standard Asr, with night-fraction summer rules.
  - Tatarstan and Dagestan use different rules.
- **Bosnia, Kosovo, Albania.** The direction differs by prayer.
  - Bosnia: the app is early only at Maghrib (4–8 min).
  - Kosovo: the app is early at Dhuhr, Asr, Maghrib and Isha, and 3–11 min late at Fajr.
  - Albania: the app is early at Dhuhr, Asr and Maghrib.
  - All three use the first-shadow Asr.
- **US and Canada.**
  - FCNA's 2017 recommendation (15° in the US, 13° in Canada) exists. It is advisory, and Canada ignores the 13°.
  - Toronto is not uniformly ISNA.
  - ICCNY is off by up to 17–19 min.
  - Mosques that add minutes put the app early every day at Maghrib and Dhuhr.
- **Australia.**
  - Lakemba's full-year range against the app is wider than 17 min: Asr −11..+22, Isha −8..+13.
  - Its Isha is Maghrib + 90.
  - Its website had the daylight-saving-day error.
## Summary table

Abbreviations used in the table: "+n" / "−n" = minutes added to or subtracted from the astronomical time; "fit" = inferred by fitting; S = Standard/Shafi'i Asr (shadow 1); H = Hanafi Asr (shadow 2); "ref." = reference point; "Diyanet" = the Turkish Presidency of Religious Affairs tables.

| Country | Dominant timetable | Massive majority? | Fajr | Isha | Precaution minutes | Rounding | Asr | Imsak | Zones / reference | Recent changes | Machine-readable source |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Malaysia | State muftis via JAKIM e-solat | yes | 18° | 18° (Kelantan 17°, fit) | none flat; latest zone point; Dhuhr about +1 | start times rounded up; syuruk rounded down at the east point | S | Subuh −10 (Perlis: none) | 59 zones, westernmost or multipoint ref.; elevation for highland zones | 20°→18°: decision 20–21 Nov 2019, in force Dec 2019–Feb 2020 | e-solat JSON (undocumented, terms not found) |
| Indonesia | Kemenag | yes (Dhuhr–Isha), partly (Fajr) | 20° (Muhammadiyah 18°) | 18° | +2 each (Dhuhr about +3), sunrise −2 | up | S | Subuh −10 | regency/city capital | Muhammadiyah 18°: Munas Dec 2020, tanfidz Mar 2021 | myQuran API (third-party, no licence) |
| Singapore | MUIS | yes | 20° | 18° | not reproducible (about +0.5 on average, up to +2.5) | irregular | S | Subuh −10 | single table | none found | data.gov.sg (Open Data Licence) |
| Brunei | MORA | yes | 20° | 18° | none; Dhuhr about +1 | nearest | S | Suboh −10 | national; Tutong +1, Belait +3 | none found | SharePoint list API (errors in data) |
| Maldives | Ministry of Islamic Affairs | not verified | – | – | – | – | – | – | – | – | site blocked |
| Pakistan | Karachi permanent / Banuri; Dawat-e-Islami | partly | 18° | 18° (Dawat-e-Islami also 12° for S) | Banuri none; Dawat-e-Islami ±1 advice; adhan 10 min after dawn advised | nearest / seconds | H majority, both printed | = Fajr (Dawat-e-Islami: −1) | per city; Dawat-e-Islami per coordinate with elevation | none found | Banuri HTML; Dawat-e-Islami AJAX (no sharing) |
| India | unknown (Dawat-e-Islami verified) | unknown | 18° (Dawat-e-Islami) | 18° / 12° | Dawat-e-Islami as Pakistan | seconds | H majority (Kerala S, unverified) | Dawat-e-Islami −1 | per coordinate | none found | Dawat-e-Islami AJAX |
| Bangladesh | IFB | yes (MEDIUM) | about 18.5° + 3; Ramadan 2026: 18° at the district's west edge | about 18° +1..4 | Fajr, Dhuhr, Maghrib +3; sehri −3 | 2026: sehri down, adhan/iftar up | H | sehri 6 min before printed Fajr (2026: 2–3) | Dhaka + offsets → 64 district tables (2026) | Ramadan 2026 per-district east/west-edge method | PDFs |
| Uzbekistan | Muslim Board (muslim.uz) | yes (inferred) | 15.5° | 15.5° (15° until Jul 2026) | Maghrib +4 (old: Dhuhr +5, Maghrib +3) | nearest | H | = Fajr | Tashkent + regional minute offsets; islom.uz by coordinates | switch Jul–Sep 2026 (fit) | none official; algorithm known |
| Kazakhstan | QMDB (muftyat.kz) | yes (inferred) | 15° | 15° | sunrise −3, Dhuhr/Asr/Maghrib +3 (≥48°N: ∓5) | nearest | H | API field unclear (= Fajr + 10) | coordinates, 5,694 places | none since 2019 | api.muftyat.kz (terms not found) |
| Kyrgyzstan | Muftiate | likely (LOW) | about 18° | about 16° | Maghrib +7, Asr +2 | ? | H | – | 115 localities | – | homepage JSON |
| Tajikistan | not found | – | – | – | – | – | – | – | – | – | – |
| Russia, Moscow | DUM RF | partly | 18°; summer: sunrise − 0.3 × night | 15°; summer: sunset + 0.25–0.3 × night | shuruk −5, Dhuhr +5, Maghrib +5 | ? | S | = Fajr | Moscow + oblast | summer rule changed 2024→2025 | monthly JPGs |
| Russia, Tatarstan | DUM RT | partly | 18° (sahur); summer: sunrise −121 | 15°; summer: Maghrib +90 | fixed Zuhr per town | up | H | sahur column | 45 localities | – | xlsx, CC BY 4.0 |
| Russia, Dagestan | DUM RD | partly | about 18° + 3 | 15° | sunrise −3, Dhuhr +5, Asr +3, Maghrib +5 | ? | S | – | 26 localities | – | HTML |
| UK | LUPT (London); fragmented elsewhere | partly (London) / no | LUPT 12–16.3° seasonal; Wifaqul 18°; OpenFajr 12.3–14.3° | LUPT 8.6–15.6°; Wifaqul 15° | LUPT sunrise −3, Zuhr +5, Maghrib +3 | nearest (fit) | both printed | none (OpenFajr: suhur −8) | M25 single zone | LUPT 2011; OpenFajr 2016; Wifaqul 2018 | LPT API (free key) |
| Ireland | IFI table vs ICCI site | no | IFI 11.4–16.4°; ICCI 18° | IFI 9.3–15.6°; ICCI 17° | IFI about ±3; ICCI Dhuhr +1 | nearest | S | none | IFI 28 towns | – | IFI site API; ICCI code |
| France | none (GMP; per-mosque Mawaqit) | no | 12–13° (40% of IDF); GMP 2026 = Shaukat; 15° | 12–13°; GMP sunset +90 | Maghrib +3/+4 (78% of IDF); Zuhr +5 or +0/1 | nearest | S | not configured | per mosque | GMP method changed for 2026 | Mawaqit (unofficial) |
| Belgium | EMB | partly | 18°; May–Jul proportional from 45° | 18°; same | sunrise −2, Asr +0..1, Maghrib +2 | about nearest | S | = Fajr | one table + 18 town offsets | 45° rule in the 2026 PDF | yearly PDF |
| Netherlands | none | no | Diyanet 18° / Moroccan calendar 14–16° / 18° / Awqati | 16° / about 15° / 17° / observation | varies | – | S mostly, some H | = Fajr | per mosque | – | Waqti API (open), Diyanet HTML |
| Germany | Diyanet (DITIB, IGMG) | partly (about 51% + copies) | 18° + congress summer rule | 16° + summer rule | sunrise −7, Dhuhr +5, Asr +4, Maghrib +7 | – | S (+4) | = Fajr | per town | congress method from 1 Jan 2023 | Diyanet HTML; awqatsalah (login) |
| Austria | IGGÖ (= Diyanet) | likely | 18° + takdir | 16° + takdir | as Diyanet | – | S | = Fajr | 9 cities | unified 1 Jan 2023 | IGGÖ PDF API |
| Switzerland | FIDS (= Diyanet) | not established | 18° | 16° | as Diyanet | – | S | = Fajr | 22 cities | 2023 | fids.ch |
| Bosnia | IZ BiH vaktija | yes | 18° | 16° | sunrise −6, Dhuhr +1, Maghrib +6 | nearest (doc) | S (first shadow) | zora = imsak | Sarajevo + monthly town offsets (sign error south of Sarajevo) | 19°/17° → 18°/16° on 1 Jan 2025 | api.vaktija.ba; GitHub JSON |
| Kosovo | BIK Takvimi | yes (MEDIUM) | about 19° (imsak) | about 18.5° | sunrise −6, Maghrib +6; others irregular | – | S | imsak separate; sabah = imsak + 20 | one table (42.5°N 21°E) + ±2 min | sabah +18 → +20 | PDF |
| Albania | KMSH (= Diyanet) | yes | 18° | 17° | as Diyanet | – | S | = Fajr | Tirana + offsets | – | kmsh.al API, PDF |
| Norway | IRN joint calendar | mostly (Oslo) | 16°; summer sunrise −60 / frozen | 15°; summer Maghrib +40 | Maghrib +3..6, Dhuhr +4..10 | – | both | = Fajr | per city | national calendar 2022 | api.bonnetid.no (token) |
| Sweden | none (Stockholm Mosque = Diyanet) | no | congress method | 16° | as Diyanet | – | S | = Fajr | per city | 2023 | Diyanet HTML |
| Denmark | none (HBKCC ≈ Diyanet) | no | congress method | 16°; HBKCC summer Maghrib +10 | as Diyanet | – | S | = Fajr | per city | 2023 | my-masjid JSON |
| Finland | none (Rabita) | no | 18°, frozen in summer | 17°; summer Maghrib +85 | Dhuhr +2, Maghrib +3..6 | – | S | = Fajr | per mosque | – | Mawaqit |
| USA | FCNA/ISNA 15°/15° | yes (about 80–90%) | 15° (Chicago block 18°; DC 19.5°) | 15° (some 18°) | FCNA: none; many mosques add Maghrib +1..+5, Dhuhr +2..+5 | nearest (some flat +1) | S mostly; H about 1/3 (Chicago almost all) | none printed | each mosque's coordinates | 2010 18° → 2011 MCW / 17.5° → 2017 15° | Masjidal, Madina, Mawaqit; moonsighting.com praytable (terms not checked) |
| Canada | none (ISNA 15/15 in Montreal and Ottawa) | partly (east); no majority in Toronto, the Prairies or BC | 15° east; 18°-type / seasonal west | 15°, 12°, 13.5°, Maghrib +90, 18° | Maghrib +3..4 (18/15 mosques); ICCO Dhuhr +5 | nearest | S (Arab); H (South Asian) | none printed | each mosque's coordinates; summer rule set per mosque | FCNA's 2017 13° not adopted; BC permanent UTC−7 from 1 Nov 2026 (tzdata 2026b) | Mawaqit, Masjidal, IFT CSV |
| South Africa, Gauteng/KZN | Jamiat SA/KZN + Darul Ihsan perpetual table | partly | 18° | H 18°, S 15° | Dhuhr +5, Maghrib +3 (doc) | nearest | both | Suhoor = Fajr −5 (doc) | per town (350–493) | 2026 jamiatsa.org calculator broken | CSV per city |
| South Africa, Cape Town | MJC web vs VOC/Wembley calendar | no | MJC 18°; VOC about 16.5° | MJC 17°; VOC about 15.4° | VOC Dhuhr +3, Maghrib +2..3 | nearest | MJC S; VOC both | = Fajr | Cape Town + town offsets | – | HTML, PNG, PDF |
| Nigeria / Senegal / Somalia / Ethiopia | none found | no | mixed | mixed | – | – | S (Maliki / Shafi'i) | – | – | – | – |
| Kenya | Jamia Mosque (Ramadan only) | unknown | about 18° | – | none | – | – | suhoor ≈ Fajr | 14 towns | – | PDF |
| Tanzania | none found (Darul Ihsan covers it) | unknown | 18° (DI) | H 18° / S 15° (DI) | +5 / +3 (DI) | nearest | both | Fajr −5 (DI) | per town | – | DI CSV |
| Australia | none (LMA; Diyanet) | no | LMA 18° | LMA Maghrib +90; Diyanet | LMA Dhuhr +2..7, Asr irregular | LMA Fajr down, Maghrib up | S | – | single / per suburb | – | LMA PDF; Diyanet |
| New Zealand | FIANZ | unknown | 18° | 18° | none | nearest | S | – | 20 cities | possible MWL → 18/18 (LOW) | AJAX |
## Implications for an app that must never be earlier than the time local mosques use

1. **Separate the two meanings of Fajr.** "Fajr prayer" is a start time and must never be earlier than the local time. "Stop eating" (imsak / sehri end) is a cut-off and must never be later.
   - Most authorities publish one time for both, with imsak equal to Fajr: Diyanet, the Uzbek Board, DUM RF, LUPT, EMB, IZ BiH.
   - Several publish an earlier cut-off:
     - Malaysia, Indonesia, Singapore, Brunei: Subuh − 10
     - Jamiat SA: Fajr − 5
     - IFB Bangladesh: 6 min before the printed Fajr
     - Kosovo: sabah = imsak + 20
     - DUM RT: a sahur column separate from the mosque Fajr
     - OpenFajr: − 8
   - The app should show an imsak line wherever the authority prints one. Where there is no majority, imsak should come from the earliest plausible local table.
2. **Treat sunrise as a cut-off too.** "Never earlier" is the wrong test for it.
   - The app's sunrise is later than the official one in:
     - Kemenag (+3..+4)
     - JAKIM (+1..+2)
     - Diyanet (+6..+8)
     - EMB (+1..+3)
     - LUPT (+2..+3)
     - DUM RF (+5)
     - IZ BiH (+4..+8)
     - Kosovo (+3..+11)
   - Apply the authority's negative margin and round sunrise **down**.
3. **Add per-authority fixed minutes and round start times up.** No adhan preset reproduces any authority except these, and even they fall short:
   - Banuri (Karachi preset + Hanafi)
   - Albania, Turkey and Diyanet Australia (the TURKEY preset)
   - LUPT and GMP 2026 (the MOON_SIGHTING_COMMITTEE preset)
   - MUIS (Singapore preset): still 1 min early on 72–119 days.

   Fitted never-earlier configurations (fajr / isha angle; minute offsets to sunrise, Dhuhr, Asr, Maghrib, Isha, Fajr; rounding):

   | Country / authority | Configuration |
   |---|---|
   | Indonesia (Kemenag) | 20/18; Fajr +2..3, sunrise −3..−4, Dhuhr +3..4, Asr +3, Maghrib +3..4, Isha +2..3; round up |
   | Malaysia (JAKIM), at the zone's reference points | 18/18; +3 on all; sunrise −2; round up. Or use the zone table (preferred) |
   | Bangladesh (IFB) | 18/18 Hanafi; Fajr +2..3, Dhuhr +3, Maghrib +3; Isha +1..2 |
   | Uzbekistan | 15.5/15.5 Hanafi; Maghrib +4 |
   | Kazakhstan | 15/15 Hanafi; sunrise −3, Dhuhr/Asr/Maghrib +3 (≥48°N: −5 / +5) |
   | Diyanet Europe | 18/16; sunrise −7, Dhuhr +5, Asr +4, Maghrib +7, plus about 1 min for sunset differences; congress summer rule north of 44.5° |
   | IZ BiH | 18/16.2; Fajr +1, sunrise −6, Dhuhr +1, Asr +2, Maghrib +8 |
   | EMB | 18/18; sunrise −3, Asr +1, Maghrib +2; 45° proportional May–Jul |
   | Jamiat SA / KZN | 18/18 Hanafi; Dhuhr +6, Asr +1, Maghrib +4, Isha +1, Fajr +1 |
   | FIANZ | 18/18 |
   | MAC Toronto | ISNA; Asr +3 |

   - Where residuals exceed 60 s, add a 1-minute margin or ship the official table: MUIS, JAKIM Asr, the Jamiat perpetual table's ±1 min drift.
4. **Replace the automatic ≥48° one-seventh rule.** It is the largest error in Europe, Russia and northern Kazakhstan: Isha 10–140 min early and Fajr 12–141 min late from spring to autumn.
   - Just under 48° the app flips to middle-of-the-night, and the June Fajr becomes up to 68–90 min early (Freiburg, Zürich).
   - Use the authority's own rule instead:
     - Diyanet congress takdir (north of 44.5°)
     - EMB's 45° proportional rule
     - IRN's sunrise − 60 with freezes / Maghrib + 40
     - DUM RF's 0.3 × night
     - DUM RT's Maghrib + 90
     - LUPT's table
   - Where there is no authority, use the latest local Isha and the earliest local imsak.
   - Also handle adhan2's IllegalStateException with TWILIGHT_ANGLE during polar day and night (Tromsø, verified by the Europe agent). It is the app's own rule at ≥65°.
5. **Zone tables beat the user's own coordinates.**
   - In Malaysia, computing at the user's location, even with the right angle, is 2–8 min earlier than the official zone time in the east of wide zones (Sarawak, Sabah, Johor).
   - Kemenag uses the regency capital. IFB 2026 uses district edges. The Bosnian, Kosovo and EMB town offsets are fixed, and the vaktija's south-of-Sarajevo offsets are known to be wrong.
   - Where an authority defines zones, compute at its reference points or ship its table, and pick the zone from the user's location.
6. **Fix the library's Asr, then choose the Asr school by country, not by madhab label.**
   - adhan2's `SolarTime.afternoon()` uses the day's declination at 0h UT, not at transit. In the Americas this makes Asr up to 1–2 min early on about a third of days. Compute the Asr shadow angle with the declination at transit, or add +1 to Asr.
   - Hanafi (shadow 2) is the printed majority in: Pakistan, Bangladesh, Uzbekistan, Kazakhstan, Kyrgyzstan, Tatarstan, Gauteng/KZN (both printed), and South Asian mosques in the UK.
   - The Turkish/Diyanet world, Bosnia, Kosovo and Albania print the first-shadow Asr despite being Hanafi.
   - Defaulting to Standard Asr in Pakistan makes the app 44–79 min early against the majority.
7. **The US is the one Western country with a real majority** (FCNA 15°/15°), and the app's ISNA default matches its angles.
   - Maghrib and Dhuhr at mosques that add minutes, and Asr (the defect above), still come out early.
   - The Chicago 18°/15° block and the South Asian 18°/18° mosques have an earlier Fajr, so the app's 15° Fajr is late there as a suhur cut-off.
   - Canada has no majority, except Montreal and Ottawa, which use ISNA.
8. **Where no massive majority exists** (UK outside London, France, Ireland, the Netherlands, Sweden, Denmark, Finland, Canada outside Montreal and Ottawa, Cape Town, Australia, Nigeria, Senegal, Somalia, Ethiopia, India):
   - **(a) Default to a per-prayer envelope of the major local tables.**
     - Start times take the latest: Dhuhr + 5, Maghrib + 3..+7, Asr at the latest school in use locally, and Isha at the latest common method.
     - Imsak and sunrise take the earliest.
     - This never shows an early time, but it can push Fajr and Isha well after other mosques. For example, UK Fajr would follow OpenFajr or LUPT at about 12–13° in summer, while imsak stays at 18°.
     - This is valid (praying later within the window is allowed) but should be labelled.
   - **(b) Let the user pick "my mosque / my authority" from the named timetables above.** Store the choice.
   - **(c) Say plainly in the UI that local mosques differ**, and name the timetable used.
   - **(d) Never pick a single MWL/ISNA preset silently.**
9. **Ship official data only where terms allow.**
   - Open terms:
     - data.gov.sg (MUIS): Singapore Open Data Licence, attribution required
     - DUM RT: CC BY 4.0
     - London Prayer Times: "completely free for all use"
     - Waqti: open API
   - Restricted:
     - e-solat JSON: terms not found
     - myQuran: no licence stated
     - vaktija.ba: no licence; the IZ says commercial copies infringe copyright
     - Dawat-e-Islami: sharing prohibited
     - Diyanet awqatsalah: login, commitment form and quota
     - islom.uz: all rights reserved
     - muftyat.kz: terms not found
     - Jamiat CSV: no terms found
   - Where terms are restricted or missing, reproduce the documented algorithm (JAKIM, Kemenag, Uzbek Board, QMDB, Diyanet, IZ BiH, Jamiat) rather than copying the table, and ask the publisher.
10. **Validate every imported table.** Errors seen in this run:
   - the Lakemba website printed DST day 4 Oct 2026 in standard time;
   - the Brunei SharePoint list has duplicate and typo rows, one of which would put Fajr about 9 min early;
   - the jamiatsa.org 2026 calculator has a constant Dhuhr;
   - ICC London prints its calendar in GMT all year;
   - Finsbury Park's Mawaqit screen differs from its own website;
   - ICCNY's table appears to be computed for the wrong latitude;
   - the vaktija towns south of Sarajevo carry a sign error;
   - moonsighting.com keeps Edmonton at UTC−6 in Nov–Dec 2026.
   - Also check time zones. tzdata 2026b moves BC to permanent UTC−7 from 1 Nov 2026, so the app must ship or require that tzdata.
11. **Re-verify every year, and before Ramadan.** Rules changed often in the last 7 years:
    - JAKIM: Nov 2019 – Feb 2020
    - Muhammadiyah: 2021
    - LUPT: 2011
    - Diyanet Europe congress method: 2023
    - DUM RF summer rule: 2024–25
    - IZ BiH: 1 Jan 2025
    - Uzbek Board site method: Jul–Sep 2026
    - GMP: 2026
    - IFB district method: Ramadan 2026
    - Kosovo sabah offset: +18 → +20
    - Keep one regression test per authority (a year of official rows, asserting app ≥ official for start times and app ≤ official for imsak and sunrise).
