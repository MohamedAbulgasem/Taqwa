# The audit that took the city pages down (25–26 September 2026)

Every figure is **app minus official**, in minutes: a negative number is the app (and the site,
which ran the app's engine) showing a time before the official one. Official tables are in
`tools/timetables/official/` (MANIFEST.tsv); the scripts that produced these numbers are in the
local archive (`archive/code/moral/audit_live.py`, `offsets.py`, `highlat.py`, `RawTimes.java`).

## The 51 live cities against their official tables

Measured with the app's own presets (`apptimes.sh`, the adhan2 0.0.7 jar the app resolves) at the
cities' GeoNames points.

| Table | Days | Starts shown early | Ends shown late |
|---|---|---|---|
| Diyanet, 8 Turkish cities, 25 Sep–25 Oct 2026 | 31 each | Asr −2..−1 every day; Maghrib −2..−1 on 27–31 days; Isha −2..−1 on 29–31 days; Dhuhr −1 on 1–6 days | sunrise +1 on 2–14 days |
| MUIS Singapore, all of 2026 | 365 | −1 at Fajr 77, Dhuhr 90, Asr 116, Maghrib 95, Isha 72 days | sunrise +1 on 25 days |
| ESA via Dar al-Ifta, Cairo, Sep 2026 | 30 | Asr −1 on 11 days | Fajr and sunrise +1 on 4 days |
| Khaleej Times copy of the Dubai table, Sep 2026 | 30 | Maghrib −1 on 13, Isha −1 on 9 days | sunrise +1 on 9 days |
| Kuwait ministry (one day) | 1 | Asr, Maghrib −1 | — |
| Umm al-Qura, second-hand copies (Makkah, Riyadh) | 1 each | −1 at several prayers | sunrise +1 |
| MAC Toronto, Jun, Oct, Dec 2026 | 30–31 each | Asr −3..−2 every October day; Fajr −1 on 8 June days; Maghrib −1 on 9 December days | sunrise +1 on 3–11 days |
| ICCNY Manhattan, Sep 2026 | 30 | Maghrib −4..0 on 25 days, Isha −8..−3 every day | Fajr +1..+6 every day |
| Hikmah (New York), Sep 2026 | 30 | Asr −1 on 16 days | sunrise +1 on 20 days |
| Jamiatul Ulama's Cape Town table, Oct 2026 | 31 | Dhuhr −4..−3 and Maghrib −3..−2 every day | — |

## Official minus raw astronomy, to the second

adhan2 raw times (the authority's angles, no adjustments, no rounding) against each official
minute. A band about 60 s wide means the table is "raw + a fixed number of minutes + a rounding
rule" and can be reproduced to the minute.

| Table | Result |
|---|---|
| Diyanet | 60–72 s bands with 18°/17°: sunrise ≈ −7 min, Dhuhr ≈ +5, Asr ≈ +5, Maghrib ≈ +8, Isha ≈ +1 (later traced to Diyanet's once-a-day sun model; its published temkin is −7/+5/+4/+7) |
| ESA Cairo | every prayer within ±30 s: raw rounded to the nearest minute, no minutes added |
| Dubai | 53–60 s bands: sunrise ≈ −3.3, Dhuhr ≈ +3, Asr ≈ +1.5, Maghrib ≈ +3.5 |
| Kemenag Jakarta | 57–63 s bands: every prayer ≈ raw + 2–3 min, rounded up |
| Habous Casablanca | 50–60 s bands with 19°/17°: sunrise ≈ −3, Dhuhr ≈ +5, Asr ≈ +1, Maghrib ≈ +4.5 |
| Libya (api.ifta.ly, 8 days) | 40–62 s bands with 18.5°/18.3°: sunrise ≈ −1, Dhuhr ≈ +3, Maghrib ≈ +3.5 |
| Algeria MARW | 56–61 s bands: rounded up, Maghrib + 3 |
| MAC Toronto | ±30 s except Asr (+2..+3 min, seasonal) |
| MUIS 2026, JAKIM KL 2026, Jamiat Johannesburg 2026 | 100–175 s bands: not reproducible at one point (later explained by MUIS's one-day table shift, JAKIM's zone reference points, and Jamiat's perpetual table) |
| London Unified Prayer Timetable (East London Mosque, Oct 2026) | Fajr and Isha bands of 311 s and 512 s: not angle-based |

## The high-latitude rule the app applies above 48°

The app's automatic rule (one-seventh of the night from 48°) clamps Fajr and Isha even on days
when the real twilight occurs. App (one-seventh) minus middle-of-the-night (which leaves real
times alone), from `highlat.py`:

| Place | Date | Fajr | Isha |
|---|---|---|---|
| London | 15 Apr 2026 | +41 min | −34 min |
| London | 22 Sep 2026 | +13 | −5 |
| Birmingham, Berlin | 15 Apr 2026 | +47 | −39 |
| Paris | 15 Apr 2026 | +31 | −24 |
| Oslo | 10 Feb, 25 Feb, 8 Mar 2027 (Ramadan 1448) | +12, +20, +29 | −4, −12, −22 |
| Edmonton | 15 Apr 2026 | +25 | −26 |

A later Fajr lets people eat after dawn; an earlier Isha is a prayer before its time.

## Other findings in the app

- No Hanafi Asr default anywhere: Pakistan, India, Bangladesh and Central Asia get the Standard
  Asr, 44–79 min before the Hanafi Asr (the gap varies with the season).
- Umm al-Qura's Ramadan Isha (Maghrib + 120) is missing: Isha 30 min early on every Ramadan day.
- adhan2's Asr takes the sun's declination at 0h UT: ±37 s seasonally in the Maghreb, and 1–2 min
  early on about a third of days in the Americas (up to 2 min on 37 of 183 Edmonton days).
- Iran defaults to the Tehran method, whose Isha (14°) is earlier than any Sunni table.
- GPS users are computed at their exact point, which in wide zones (JAKIM, Diyanet districts,
  Kemenag regencies) is earlier than the mosque's table in the east of the zone.
