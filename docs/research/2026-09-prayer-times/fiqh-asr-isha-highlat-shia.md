**Not done here:** the relayed request to take down all 51 city pages is outside this read-only research task, so this agent did not act on it. The parent session has to handle it.

# Fiqh briefing: Asr, Isha, high latitudes and Shia timing

## Findings that affect the engine first

1. **The MWL's own resolution adds precaution minutes that adhan2 leaves out. HIGH.** Resolution 6 of the MWL Islamic Fiqh Council's 9th session (Makkah, 12–19 Rajab 1406 AH) sets these times:
   - Fajr at 18°, sunrise and sunset at 50′ below the horizon.
   - Asr when a shadow equals the object's length plus its noon shadow.
   - Isha when the red twilight has gone, at 17°.
   - Precaution (tamkīn): add 2 minutes to Dhuhr, Asr, Maghrib and Isha, and take 2 minutes off Fajr and sunrise.

   adhan2 0.0.7's `MUSLIM_WORLD_LEAGUE` preset only adds `dhuhr = 1` (read from the cached sources jar, `CalculationMethod.kt` lines 89–91). So against the MWL's own text, adhan2 shows Asr, Maghrib and Isha 2 minutes early, Dhuhr 1 minute early, and sunrise 2 minutes late. The resolution's −2 minutes on Fajr pulls two ways under your rules: it protects "end of suhur never late" but breaks "Fajr prayer never early". The fix is to show imsak and the Fajr prayer time as two separate times.
   - Source: https://shamela.ws/book/894/8349 and /8350.
2. **adhan2's high-latitude rules move Isha earlier than the MWL allows. HIGH for the code, MEDIUM-HIGH for the fiqh reading.**
   - The MWL 19th session (resolution 2) says this. Between 48° and 66°, when the twilight still disappears, even very late, Isha must be prayed at its proper time. People for whom waiting is a hardship may combine prayers. Estimating the time is only for when the sign is missing.
   - adhan2 caps every day with no exception. Isha is never later than the "safe" time, and Fajr is never earlier than it (`PrayerTimes.kt` lines 127 and 160). The default rule is `recommendedFor`: above 48° it uses one-seventh of the night, otherwise middle of the night.
   - So above 48°, in the months when the red twilight does set but late, the app shows Isha before its real time. It also shows Fajr, which is the end of suhur, later than the real dawn. Both break your standard.
   - None of adhan2's rules implements the MWL's method of scaling to the nearest latitude where the signs are clear, with 45° proposed (القياس النسبي, "proportional estimation").
3. **adhan2 0.0.7 has no Tehran or Jaʿfari (Shia) preset, so the engine is Sunni by construction. HIGH** (from the source enum).
4. **Diyanet's own precaution minutes** match adhan2's `TURKEY` preset. HIGH.
   - Diyanet: Dhuhr +5, Asr +4, Maghrib +7, sunrise −7, none on imsak or Isha.
   - adhan2: `sunrise=-7, dhuhr=5, asr=4, maghrib=7`.
   - So what's left of the gap against Diyanet is rounding, not the presets.
   - Source: https://vakithesaplama.diyanet.gov.tr/temkin.php

## 1. Asr

**South Asian Hanafi practice is the two-shadow time (mithl thānī).**
- **Darul Uloom Deoband**, fatwa 1091/1091/M=08/1435. HIGH.
  - The preferred Hanafi view is that Asr starts at two shadow-lengths.
  - A Hanafi should not pray or lead Asr at one length (mithl awwal) without a severe need.
  - If one does pray at one length through travel or necessity, the prayer is valid and need not be repeated, because the second view also exists in Hanafi fiqh.
  - Source: https://islamqa.org/hanafi/darulifta-deoband/110170/
- **Jamia Banuri Town**, fatwa 144504100646 (23‑10‑2023). HIGH.
  - For Hanafis, Asr runs from two shadow-lengths to sunset.
  - Praying at one length is not allowed without a valid excuse.
  - Joining the Haramain congregation at one length is allowed. The fatwa says the Haramain follow the Hanbali school.
  - Source: https://www.banuri.edu.pk/readquestion/144504100646/23-10-2023
- **Jamia Binoria**, fatwa 6715. HIGH.
  - Hanafis should not pray before two shadow-lengths and should hold their own congregation then.
  - Praying behind a Shafiʿi imam is valid if one is compelled.
  - Source: https://www.onlinefatawa.com/view_fatwa_english/6715
- **Can a Hanafi pray at one length?** Only as an exception (need, travel, or following another congregation). All three institutions agree it is valid in those cases but not the norm. HIGH.
- **The classical minority view.** Al-Durr al-Mukhtar (1/359) reports that Abu Hanifa also has a one-length narration, which is the Sahibayn's view. It says al-Tahawi adopted it, and cites al-Fayd that people practise it and fatwa is given on it. MEDIUM: quoted via https://islamqa.info/ar/answers/326693, not checked against a print edition. I did not verify Ibn Abidin's reply.

**Diyanet**
- Diyanet's fatwa board (Din İşleri Yüksek Kurulu), 12 July 2017, says only this. HIGH.
  - The Diyanet calendar is set on asr-ı evvel (one shadow-length), and prayers are performed on that view.
  - That view belongs to Abu Yusuf, Muhammad and the other three imams; asr-ı sânî (two lengths) is Abu Hanifa's.
  - Source: https://kurul.diyanet.gov.tr/tr/fetva/asr-i-evvel-ve-asr-i-sani-ne-demektir/0193c42d-4d64-7acf-2961-12b0db4e1723
- **Diyanet's own reason for choosing it: not found.** The only reason in Diyanet's text is implicit: it is the Sahibayn's and the majority's view. Other explanations circulate:
  - "It was the Ottoman fatwa position" is not from Diyanet.
  - The foundation HGEV says fatwa in the Hanafi school is on asr-ı evvel, citing al-Hidaya, Badaʾiʿ and Tabyin. HGEV is not Diyanet. LOW as an account of Diyanet's reasoning.

## 2. Isha: red twilight (shafaq ahmar) and white twilight (shafaq abyad)

- **Who holds which.** Abu Hanifa holds white twilight; the Sahibayn and the Shafiʿi, Maliki and Hanbali schools hold red. HIGH. Sources: Darul Iftaa Birmingham (Shaykh Mohammed Tosir Miah), https://islamqa.org/hanafi/daruliftaa-birmingham/19679/, and Qibla (Shaykh Ilyas Patel), https://islamqa.org/hanafi/qibla-hanafi/43601/
- **MWL.** Isha is when the red twilight disappears, at 17° (9th session, resolution 6). HIGH.
- **Subcontinent ulama.** Mufti Muhammad Shafiʿ, Maulana Yusuf Banuri and most scholars of the region put red twilight at 15° and white twilight at 18°. MEDIUM. The source is Mufti Ebrahim Desai's Darul Iftaa: https://islamqa.org/hanafi/askimam/679/
  - That fatwa says some jurists give the fatwa on the Sahibayn's view, and it allows 15° only in summer, following Mufti Taqi Usmani.
  - Mufti Rashid Ahmad Ludhianvi's figures (red 12°, white 15°) come only from a search snippet of askimam fatwa 15287. LOW.
- **Karachi 18° as white twilight.** This is consistent with the attribution above. MEDIUM. **Not found:** any document from the University of Islamic Sciences Karachi itself.
- **Diyanet's 17° Isha.** Diyanet's page did not state the angle. The 18°/17° pair comes from AlAdhan's API and adhan-js, and the link to red twilight comes from an unsourced search summary. LOW.
- **adhan2.** Its `Shafaq` setting (`GENERAL`, `AHMER`, `ABYAD`) is only used by the Moonsighting Committee method. HIGH.

## 3. High latitudes

**MWL Islamic Fiqh Council** (Arabic text from Shamela's compilation and the MWL's own site; saved in the extract file listed at the end)
- **5th session, resolution 3.** Session 3, Thursday 10/4/1402 AH = 4/2/1982. HIGH.
  - It sets three cases:
    - Continuous day or night: take the times of the nearest place that has a normal 24-hour day.
    - Evening and morning twilight never separate: estimate Isha and Fajr from the last date on which they did.
    - Very long days or nights but distinct times: pray at the real times.
  - It cites a Saudi Council of Senior Scholars resolution "no. 16 of 21/4/1398". That conflicts with the Council's own numbering (see below).
  - Source: https://shamela.ws/book/894/8353
- **9th session, resolution 6.** 12–19 Rajab 1406 AH; the source gives no Gregorian date. This is the one with the 45°/48°/66° zones. HIGH.
  - 45°–48°: prayer and fasting at the real times.
  - 48°–66°: when the signs are missing, set Isha and Fajr by proportional estimation, proposing 45°. For example, if Isha falls after the first third of the night at 45°, use the same fraction locally.
  - Above 66°: estimate all times by time-comparison with 45°.
  - The angles and the ±2-minute precaution are listed above.
- **19th session, resolution 2.** Makkah, 22–26 Shawwal 1428 = 3–7 Nov 2007. HIGH.
  - It restates the earlier rulings and clarifies the 48°–66° band: estimation applies only when the sign is missing.
  - If the twilight still sets, even late, Isha is obligatory at its real time, and those facing hardship may combine. Combining must not become the rule for everyone.
  - It asks for an astronomy centre in Makkah.
  - Sources: https://sunnionline.us/arabic/2007/12/125/ (mirror of the text). The MWL's own clarification, from its **21st session** (24–28/1/1434 = 8–12 Dec 2012), is at https://ar.themwl.org/node/48
- **Saudi Council of Senior Scholars: resolution 61 of 12/4/1398 AH.** 12th session, Riyadh, early Rabiʿ al-Akhir 1398. HIGH.
  - It answered a question from Malmö. Where day and night are distinct, however long, pray and fast at the real times. Anyone unable to finish a fast breaks it and makes it up later.
  - Where the sun does not set or rise, pray five times every 24 hours, timed by the nearest place where the times are distinct.
  - Sources: https://binbaz.org.sa/fatwas/12038 and https://shamela.ws/book/894/8338 (citing the Council's research volume 4, pp. 446–459).
  - The MWL's "no. 16 / 21/4/1398" is probably a transcription error. LOW that the two numbers refer to different decisions.

**European Council for Fatwa and Research (ECFR)**

Its website blocks automated fetching, so details come from its own published collection or from search snippets.
- **Resolution 3/3.** 3rd session, Cologne, 4–7 Safar 1420 = 19–22 May 1999. HIGH, from the ECFR's First Collection of Fatwas: https://wcdn.boiv.org.au/wp-content/uploads/2023/11/15134755/ECFR-Fatawa-Coolection-01-1.pdf
  - Maghrib and Isha may be combined in the European summer when Isha comes around midnight or its signs vanish.
  - Dhuhr and Asr may be combined in winter under hardship.
  - It warns against making combining a habit.
- **Resolution 41 (2/12).** 12th session, Dublin, 6–10 Dhu al-Qaʿda 1424 = 31 Dec 2003 – 4 Jan 2004. MEDIUM, from snippets of the ECFR's own posts.
  - It treats the matter as ijtihad.
  - It sees no harm in relying on other bodies' estimates, for example 12° for Fajr and Isha, or a fixed 1.5 hours between Maghrib and Isha and between Fajr and sunrise.
  - It reaffirms 3/3.
  - Under your standard, 12° is far earlier than 17°/18°.
- **22nd session.** Istanbul, 6–10 Shaʿban 1433 = 26–30 July 2012. A resolution, numbered 2/22 in one snippet, covers Isha and Fajr in Ramadan where nights are short or the signs are late or missing, acknowledging both combining and proportional estimation. MEDIUM for the session; LOW for the number and the full clauses, which are **not found**.

## 4. Shia (Jaʿfari) timing, for honest labels only

- **Maghrib, per Sistani.** Ruling 722: by obligatory precaution, do not pray Maghrib until the redness in the eastern sky has passed overhead. HIGH. Source: https://www.sistani.org/english/book/48/2212/
  - Asked for a figure in minutes, his office answers: "لا يمكننا تحديده بالدقائق لإختلاف الأزمنة والأمكنة" ("we cannot fix it in minutes, because times and places differ"). HIGH. Source: https://www.sistani.org/arabic/qa/02340/
  - **No official number of minutes exists.** One London Shia centre (SICM) uses sunset + 10 minutes. LOW as a general figure.
- **Midnight.** The midpoint between sunset and Fajr (Sistani Q&A 8 and 31), and Isha ends at midnight (ruling 726). HIGH. Source: https://www.sistani.org/arabic/qa/0268/
- **Tehran method** (Institute of Geophysics, University of Tehran, the body that sets Iran's official times): Fajr 17.7°, Maghrib 4.5° (eastern redness gone). MEDIUM.
  - Sources: Persian Wikipedia, AlAdhan's API (`TEHRAN: Fajr 17.7, Isha 14, Maghrib 4.5, Midnight JAFARI`), adhan-js docs.
  - The official site, calendar.ut.ac.ir, returned a bot challenge.
  - praytimes.org says the Tehran method does not explicitly define an Isha angle, so the 14° is a library convention. MEDIUM.
- **Qum method** (Leva Research Institute): Fajr 16°, Isha 14°, Maghrib 4°. MEDIUM (AlAdhan's API, praytimes.org, Persian Wikipedia; no Leva document found).
- **Label guidance.** Call the Sunni time "Maghrib (sunset)"; it corresponds to the MWL's 50′ below the horizon. Do not show anything as "Shia Isha", because in Jaʿfari fiqh Isha shares one window after Maghrib up to midnight.

## Not found

- Diyanet's own stated reason for choosing asr-ı evvel.
- A University of Islamic Sciences Karachi document on 18°.
- The full text of the ECFR 2/12 and 22nd-session resolutions.
- Any official Shia figure in minutes for Maghrib.
- A first-hand Tehran calendar-centre document.

Files are in `/private/tmp/claude-501/-Users-mohamedabulgasem-Desktop-Workspace-apps/d2f2d5b2-1f80-4ac1-b4d2-f0b36f6f4636/scratchpad/round/fiqh-asr-isha-highlat-shia/`:
- `highlat_resolutions_ar.txt`: Arabic text of the MWL 5th, 9th, 19th and 21st-session items and Saudi resolution 61
- `ecfr_first.txt`: ECFR resolution 3/3
- `aladhan_methods.json`: AlAdhan's method parameters
- `adhan2src/`: unpacked adhan2 0.0.7 sources