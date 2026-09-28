# How other prayer apps handle accuracy, and where they have failed (25–26 September 2026)

Written up from the round's apps report, delivered in the session; notes in the local archive
(`archive/reports/apps-*.md`). Confidence as reported: HIGH = primary source read or result
reproduced; MEDIUM = indirect or reliable press; LOW = second-hand.

## What established apps do

| App or source | Where its times come from | Asr default | How the source is shown |
|---|---|---|---|
| Muslim Pro | mosque or authority timetables where "Verified", otherwise its own calculation; "App Recommended" per city | set by App Recommended | a "Verified" badge naming no authority |
| Athan (IslamicFinder) | its own calculation | selectable | none found |
| IslamicFinder website | calculation; Karachi for PK/IN/AF/BD, Egyptian for Egypt **and Libya**, MWL for **Morocco**, "Diyanet" 18°/17°, "JAKIM" 18°/18° | Hanafi only in Pakistan | method, angles and school under the times |
| Aladhan API | calculation (PrayTimes port); with no method given, **the nearest authority by distance** (Tripoli gets Tunisia) | Standard everywhere, including Karachi and Dhaka | method and angles in each response |
| Pillars | on-device (adhan-dart fork with a London table) | user picks | FAQ explains differences |
| Salaat First | 16 named methods | — | the strongest disclaimer found: check against official local times |
| Muslim App (Muslim Assistant) | "Automatic" per country; its own İstanbul widget showed MWL | by country method | under Settings |
| Mawaqit | **each mosque sets its own times** (uploads its calendar or a method), validated by hand | per mosque | "100% precise times set by your imam" |
| Diyanet, JAKIM e-Solat, Kemenag, MUIS, Umm al-Qura (KACST), UAE Awqaf and IACAD | official tables, in the authorities' own apps | the authority's | the authority itself |

Spot checks on 26 Sep 2026 (HIGH, reproducible): Aladhan's "JAKIM" still uses 20°, so its Kuala
Lumpur Fajr is **10 min before** e-Solat's; IslamicFinder's "Diyanet" page for İstanbul has Maghrib
**7 min before** Diyanet's (the bare sunset, without temkin). A method named after an authority is
not the authority's times.

## Documented failures

- **Malaysia, Nov 2019:** Subuh moved 8 min later nationwide; presets still at 20° seven years on;
  in Feb 2022 people were still doubting the new time (HIGH).
- **Malaysia, 3 Apr 2022:** Tawau FM aired Maghrib 4 min early on 1 Ramadan; muftis ruled the fasts
  must be made up (HIGH). **2019:** breaking the fast on the Kuala Lumpur broadcast before one's own
  area's Maghrib voids it (HIGH). **2021:** mosque calendars off by 2–10 min (HIGH).
- **Indonesia, Dec 2020:** Muhammadiyah moved Subuh to 18°; Kemenag reaffirmed 20° the next day (HIGH).
- **Türkiye:** Bolu's 2021 imsakiye printed from the wrong calendar (imsak 18 min early); Fatsa 2023
  and Burhaniye 2026 azans 3–5 min early; Diyanet: a few minutes are covered by temkin, earlier is
  made up (HIGH).
- **Egypt:** the 14.7° dispute; Dar al-Ifta 2017 fatwa: 19.5° is correct and binding (HIGH).
- **Morocco:** short-notice clock changes (2018, 2020); on 20 Sep 2026 the country moved to UTC+0
  (tzdata 2026c) and prayer software with older zone data went an hour wrong (Mihrab issue #56,
  Cloudflare Workers, Home Assistant; HIGH).
- **UK:** Birmingham's Fajr times varied by up to 45 min; OpenFajr's camera timetable (2016) is used by
  about 170 mosques (HIGH).
- **UAE, 2018:** an app notified about 5 min before the real azan (MEDIUM).
- App-store reviews (Apple's public feed, MEDIUM): iftar times too early and fasts made up; "prayed
  early for two years"; "doesn't match my mosque", mostly Fajr and Isha in the UK and Canada; forced
  methods; Asr school confusion; daylight-saving bugs.

## Certification

No country checked certifies prayer-time apps (MEDIUM, from absence). Authorities publish their own
apps, license data (Diyanet's Awqat Salah API with a commitment form; Kemenag's API by letter; MUIS
as open data), or tell people to check apps against the official source. Certification exists only
for Quran text (Malaysia's LPPPQ, Indonesia's LPMQ).

## Lessons the report drew

- Reproduce the authority, not a preset named after it; copy its minutes, rounding and reference
  points, not just its angles; test against its data automatically.
- Treat method changes as events; record each table's source and date.
- Never choose a method by distance or country name alone.
- Err only in the safe direction, which differs by time: starts later, sunrise and end of eating
  earlier.
- Name the source on screen every time; be honest where practice is disputed; never claim
  "official", "certified" or "approved" unless the times are the authority's own data.
- Show the place and zone the times are for; never fall back silently to another city.
- Any user offset must be marked as the user's; consider forbidding Maghrib earlier than the
  authority.
- Store instants in UTC and recompute on zone changes; warn when the phone's zone data is stale.
