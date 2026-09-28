> Note: my task here was research only, and every repository stayed read-only. I did not take any city pages down. The "take down all 51 pages" request has to be carried out by the orchestrator or the main session.

# Fajr and Isha angle debate: briefing (fiqh-fajr-angle)

## Bottom line for Taqwa

1. **Malaysia: the app shows Subuh about 8 minutes early. HIGH.** The app maps `"MY"` to adhan2's `SINGAPORE` preset (`shared/src/commonMain/kotlin/world/taqwa/app/prayer/CalculationMethodDefaults.kt:23`). In adhan2 0.0.7 that preset is Fajr 20°, Isha 18°, `Rounding.UP` (`CalculationMethod.kt:128-131` in the cached sources jar). JAKIM moved Subuh to 18° in November 2019, which made Subuh 8 minutes later on average (7 to 10). So the app puts Subuh before JAKIM's time. Imsak would be early too, because Malaysian Imsak is set from Subuh. This breaks the owner's rule. The fix is to give MY its own parameters at 18°. JAKIM's other rules, such as its 2-minute safety margin (*ihtiyat*), are not verified here.
2. **Indonesia: the app matches the national authority. MEDIUM.** The 20° preset matches Kemenag (the Ministry of Religious Affairs, -20°). Muhammadiyah has used -18° since 2020, so its members pray about 8 minutes later. Under the "authority or massive majority" rule, Kemenag stays the default. A "Muhammadiyah -18°" option is worth considering. How many mosques follow each: not found.
3. **Egypt and Saudi Arabia: the presets match the official bodies. HIGH.** Egypt is 19.5° for Fajr and 17.5° for Isha (Survey Authority). Saudi Arabia is 18.5° for Fajr, with Isha 90 minutes after Maghrib (Umm al-Qura). The observation studies (about 13.5° to 15°) are a minority view that every official Sunni body rejects. Adopting them would push the end of suhur 20 or more minutes later, which the owner's rule forbids. HIGH.
4. **UK: no single authority. MEDIUM.** The app's default there is the Muslim World League method (18°). That shows Fajr well before the London Unified timetable, which follows Hizbul Ulama's observations and is used by East London Mosque and Regent's Park, especially in summer. Which timetable the majority of UK mosques use: not found.
5. **Where Fajr is disputed, the common Sunni advice is to be cautious both ways. HIGH.** Start the fast at the earlier time and pray after the later one. Official state bodies (Egypt, Saudi Arabia, Jordan, Indonesia) all tell people to simply follow the official timetable.

## 1. Egypt

**The studies (NRIAG, the national astronomy institute; Helwan)**
- **Hassan, Abdel-Hadi, Issa and Hassanin (2014).** Naked-eye observations for morning twilight at different sites in Egypt, *NRIAG J. Astron. Geophys.* 3(1):23-26, doi:10.1016/j.nrjag.2014.02.002. HIGH.
  - Observations ran from 1984 to 1987 at Baharia, Matrouh, Kottamia and Aswan.
  - They were part of a cooperation project between Dar al-Iftaa and the Egyptian Academy of Scientific Research and Technology.
  - First light appeared at 14.7° on average (maximum 15.08°, minimum 12.01°). This agreed with the authors' earlier photoelectric measurements.
- **Hassan, Issa, Mousa and Abdel-Hadi (2016).** *NRIAG J.* 5(1):9-15, doi:10.1016/j.nrjag.2016.02.001. North Sinai (2010 to 2012) gave 14.61°; Assiut (2012 to 2014) gave 13.665°. HIGH.
- **Semeida and Hassan (2018).** *Beni-Suef Univ. J. Basic Appl. Sci.*, doi:10.1016/j.bjbas.2018.03.005. HIGH.
  - 38 observations at Wadi al-Natrun, 2014 to 2015.
  - The false-dawn light (zodiacal light) began at 19.74° and ended at 15.41°. True dawn came at 14.57°.
  - The authors conclude that Egypt's 19.5° marks the false dawn.
- **The 2018 leak.** HIGH.
  - The Solar Research Lab at NRIAG sent a report signed by eight senior professors to the institute's president, Dr Hatem Odah. It called 19.5° a "major error" and gave 14.7° as the correct value. It also called Umm al-Qura's 18.5° wrong.
  - The report was posted on Facebook by mistake in June 2018, and an employee was referred for investigation.
  - The Lab then said the paper was published in error, and that prayer times are a religious matter for Dar al-Ifta to decide (Asharq al-Awsat, 5 Jun 2018; Youm7, 26 Jan 2019).

**Official responses**
- **Dar al-Ifta fatwa no. 4021 (20 Mar 2017, Shawki Allam).** HIGH (primary source).
  - It answers a complaint sent on by the head of the Survey Authority. The complaint enclosed an NRIAG letter to the Ministry of Awqaf: research since 1984 puts Fajr at 14.7°, against the 19.5° in use.
  - The fatwa rules that the Survey Authority's 19.5° is definitively correct. It dismisses 14.7° and 16.5° as a departure from consensus.
  - It cites a 1980s committee at the Academy of Scientific Research. Members were the Survey Authority, the Helwan observatory, Cairo University's astronomy department, al-Azhar's survey and astronomy department, and Dar al-Ifta.
  - Dar al-Ifta's religious delegate on that committee, Judge Muhammad Hassan, observed by naked eye from Aug 1984 to Mar 1985. The fatwa says his results matched Fajr at 19°30′ and Isha at 17°30′.
  - It also cites earlier fatwas: no. 227 of 1983, and no. 13 of 13 Dec 1987.
- **The two accounts of the 1980s observations contradict each other. HIGH that they conflict.** The 2014 NRIAG paper says the 1984-87 Dar al-Iftaa/Academy project found 14.7°. The fatwa says the same era's observations confirmed 19.5°. Which account is right cannot be settled from public sources.
- **The Mufti's statement (May 2018). HIGH.** 19.5° is correct. The Survey Department has used it since it was founded in 1898, and the General Egyptian Survey Authority since 1971.
- **Al-Azhar's Islamic Research Academy, session of 12 Mar 2018. HIGH.** It ruled unanimously, with Muftis Allam, Nasr Farid Wasil and Ali Gomaa, that:
  - praying Fajr straight after the current adhan is valid;
  - the claim that Fajr is only valid a third of an hour after the adhan is false.
- **The Awqaf ministry. HIGH.** Imams must keep the Survey Authority times.
- **The lawsuit. HIGH that it was filed.** Case 33766 of judicial year 71, brought by imam Hussein Ahmed Mustafa Habib. The Administrative Court set 23 March for its verdict. Outcome: not found.
- **The Survey Authority itself.** A standalone public reply was not found. Its position reaches the public through fatwa 4021 and the Mufti's statement. MEDIUM.

## 2. Libya (extra)

- **Hassan et al. (2021).** *Al-Hilal J. Islamic Astronomy* 3(2), doi:10.21580/al-hilal.2021.3.2.8625. Tubruq observations in 2008 and 2009 put dawn at about 13.5°. HIGH. Libya's official angle was not researched.

## 3. Jordan: Shaykh al-Albani

- **Al-Albani's observation. HIGH.** In *al-Silsila al-Sahiha*, vol. 5 p. 301 (Maktabat al-Ma'arif, Riyadh), he writes that he watched repeatedly from his home on Jabal Hamlan, south-east of Amman.
  - He says the Fajr adhan in some Arab lands comes 20 to 30 minutes before true dawn. He also says Maghrib is called about 10 minutes after sunset while Fajr is called about half an hour early.
  - He warns that this makes people stop eating too early and puts their Fajr prayer at risk of being invalid.
  - The page is cited by the Shamela edition of *Jami' Turath al-Albani* and by a second source.
- The hadith number he wrote this under, reportedly 2031: LOW (one search snippet). The year of his observations: not found.
- **Jordanian Iftaa fatwa no. 2002 (9 Feb 2012). HIGH.**
  - The official times were set by specialist committees using astronomical calculation.
  - Following them is fine, and praying straight after the adhan is allowed.
  - Someone's personal belief that the adhan is early does not bind everyone.
  - An official Jordanian response to al-Albani by name: not found.

## 4. Saudi Arabia

- **Khalifa, Hassan and Taha (2018).** *NRIAG J.* 7(1):22-26, doi:10.1016/j.nrjag.2018.01.001. HIGH.
  - About 80 observations at Hail in 2014 and 2015. On the 32 best days, dawn fell between 13.48° and 14.69°, averaging 14.014°.
  - The authors put true dawn at 14.66° (mean plus two standard deviations), and 14.88° in the deep desert.
  - That is about 4° from the value Saudi Arabia uses.
  - Official response: not found.
- **KACST's "twilight study project". HIGH (a KACST letter printed in Al Riyadh, 28 Oct 2005, issue 13640).**
  - The Umm al-Qura Calendar Committee asked for it. That committee is supervised by the Finance Minister and includes KACST and religious bodies.
  - In October 2005 KACST said the study was still in its first phase, with no final results.
  - Results would go to the Grand Mufti and the Council of Senior Scholars.
  - KACST added that if the definition in the 1406 AH (9th session) Fiqh Council fatwa under Ibn Baz is used, today's times "may not change noticeably".
  - Final results: not found. That Dr Zaki al-Mustafa was the lead researcher: LOW.
- **Shaykh al-Ubaykan's claims. HIGH that he made them (Asharq al-Awsat and Al Riyadh, Oct 2005).**
  - He is a Shura Council member and a Ministry of Justice adviser.
  - He said KACST researchers found about a 21-minute gap, and that its committee found no written basis for the calendar.
  - He said the calendar's former preparer, Dr Fadl Nur, set it at 18° and later moved it to 19° as a precaution.
- **The Grand Mufti's reply (Oct 2005). HIGH.**
  - Abdulaziz Al al-Sheikh said Umm al-Qura is official and religiously sound, and the doubts should be ignored.
  - People must not delay imsak or iftar.
  - A committee formed under Ibn Baz confirmed the calendar in an official report.
- **The Umm al-Qura angle.** It was 19° until Muharram 1430 (Dec 2008) and has been 18.5° since (per PrayTimes.org). MEDIUM. The decision document: not found.
- **Ibn Uthaymin.** HIGH that others attribute these views to him; MEDIUM that they are accurate. The two sources don't agree.
  - Midad (2018), citing *Majmu' al-Fatawa* 19/302: the calendar is about 5 minutes early, based on one trip he made himself. He called "a third of an hour" an exaggeration.
  - Al-Ubaykan quotes him as having his muezzin wait 5 minutes, and as advising that iqama be delayed 20 to 25 minutes.
- **Dr Ibrahim al-Subayhi's book** *Tulu' al-Fajr al-Sadiq* defends Umm al-Qura. The Grand Mufti and al-Fawzan wrote prefaces. MEDIUM.
- **"2004 Riyadh observations found 15°."** Cited only by the Fiqh Council of North America (FCNA). LOW.

## 5. Malaysia (JAKIM)

- **The decision. HIGH.**
  - The 116th Muzakarah (fatwa committee) of the National Council for Islamic Affairs met on 20-21 Nov 2019. It set Subuh at 18° below the horizon; it had been 20°, used since 1938.
  - The change averages +8 minutes (7 to 10).
  - The basis was JAKIM's study with Universiti Malaya, UniSZA and UiTM. The Federal Territory mufti's pages give its length as one year in *Bayan Linnas* and 18 months in *Al-Kafi* #1638, so the two disagree.
- **Kelantan used 19° before the change. HIGH (UM, *Jurnal Fiqh* 16(2), 2019).** In the same issue, a single-site Universiti Malaya study at Kudat, Sabah, measured -20.174° with a sky-brightness meter (SQM) and called 20° reasonable, though inconclusive. HIGH.
- **Federal Territories committee, 2 Dec 2019. HIGH.**
  - Subuh is delayed 8 minutes.
  - Past Subuh prayers need not be made up, because they rested on sound earlier reasoning (*ijtihad*).
  - Congregational Subuh should start at least 8 to 10 minutes after the adhan.
- **Isyak did not change.**
  - The Selangor Mufti says only Subuh changed: HIGH for Selangor, MEDIUM nationally.
  - *Al-Kafi* #1638: sunrise (Syuruk) is unchanged; Imsak, 10 minutes before Subuh, moves with Subuh. HIGH.
  - That Isyak is 18°: MEDIUM (secondary sources only).
- The Muzakarah decision document itself: not found online.

## 6. Indonesia

- **Muhammadiyah's decision. HIGH.**
  - Munas Tarjih XXXI (online, 28 Nov to 20 Dec 2020) changed Subuh from -20° to -18°. Subuh moves about 8 minutes later.
  - The basis was work by ISRN UHAMKA, Pastron UAD and OIF UMSU. Their measured values: not found in the pages fetched.
- **The implementing decision. HIGH.**
  - PP Muhammadiyah Decision No. 734/KEP/I.0/B/2021, published 24 Mar 2021, tells all leaders and members to follow the new criterion.
  - It cites the Tarjih council's letter 013/I.1/B/2021 of 15 Mar 2021 and leadership meetings on 5 and 9 Mar 2021.
  - It does not mention Isya. Its signing date was not in the text retrieved.
- **Kemenag's reply (21 Dec 2020). HIGH.**
  - Dirjen Bimas Islam Kamaruddin Amin said -20° is correct by both fiqh and science.
  - He cited a Kemenag observation at Labuan Bajo (2018) and NU observations at Banyuwangi, done with LAPAN, BMKG, BIG and universities.
  - He urged people to use the Kemenag schedule.
- How many mosques follow each: not found.

## 7. UK

- **OpenFajr, Birmingham (research paper, May 2016). HIGH.**
  - An all-sky astronomy camera, 4 miles south of the city centre, photographed every minute from Dec 2014 to Dec 2015, about 25,000 images.
  - There were 42 clear and 33 partial image sets. A 19-member panel voted on them; its members included Birmingham Central Mosque, HM Nautical Almanac Office and Cambridge's Institute of Astronomy.
  - Dawn fell between 12.3° (30 Jun) and 15.0° (20 Apr).
  - The timetable was fitted to a smoothed curve, then shifted later where needed so it is never earlier than the panel.
  - It has an 8-minute margin of error, and communities decide how to use that margin for fasting.
  - In June it is up to 35 minutes later than Birmingham Central Mosque's timetable.
- **Hizbul Ulama (Maulana Yakub Miftahi). MEDIUM.**
  - Naked-eye observations outside Blackburn, 1987 to 1988.
  - The range found is reported as 12° to 16° by Darul Ifta Birmingham and 12° to 18° by FCNA.
- **The London Unified Prayer Timetable (LUPT). HIGH (East London Mosque page).**
  - Fajr and Isha follow Hizbul Ulama's work. Sunrise comes from HMNAO minus 3 minutes, to cover the M25 area.
  - The Fajr adhan is given at the start time, which is also when the fast begins. The congregation prays 20 minutes later.
  - It is adopted by East London Mosque, Regent's Park and others. MEDIUM.
  - A critic, Haitham al-Haddad (Islam21c, 27 Mar 2022), says it works out to 12° or less and goes against the major fiqh councils.
  - When it was agreed and who signed: not found.

## 8. What laypeople are told when Fajr is disputed

| Body | Advice | Conf. |
|---|---|---|
| Dar al-Ifta (Egypt) and al-Azhar's Research Academy | Follow the Survey Authority. Praying straight after the adhan is valid. Ignore the doubts. | HIGH |
| Grand Mufti (Saudi Arabia) | Follow Umm al-Qura. Don't delay imsak or iftar. | HIGH |
| Al-Ubaykan (Saudi Arabia) | Stop eating at the adhan, but hold iqama a third of an hour until the calendar is fixed. | HIGH |
| Jordanian Iftaa (no. 2002) | Following the official times is fine. | HIGH |
| Federal Territory Mufti (Malaysia) | Congregation 8 to 10 minutes after the adhan. No make-up of past prayers. | HIGH |
| IslamQA no. 311727 (25 Apr 2020) | Start the fast at the earliest timetable. Pray Fajr at the latest. | HIGH |
| Darul Ifta Birmingham | End suhur by 18° as a precaution. Pray by 15° or the observation timetable. At high latitudes, use half the night. | HIGH |
| Kemenag, Muhammadiyah | Each tells its followers to use its own schedule. | HIGH |
| FCNA (18 Sep 2024) | 15° for both Fajr and Isha, adjusted if the sky shows otherwise. | HIGH |

## Not found

- Outcome of the Egyptian lawsuit.
- Final results of the KACST study.
- The document behind Umm al-Qura's 2008 change.
- A Jordanian official response to al-Albani.
- The Muzakarah decision text.
- Muhammadiyah's measured values.
- Mosque counts in Indonesia and the UK.
- The LUPT's founding document.

## Sources

- https://api.openalex.org/works/doi:10.1016/j.nrjag.2014.02.002 (also .2016.02.001, .2018.01.001, 10.1016/j.bjbas.2018.03.005, 10.21580/al-hilal.2021.3.2.8625)
- https://www.dar-alifta.org/ar/fatwa/details/13816 (fatwa 4021)
- https://www.youm7.com/story/2019/1/26/.../4118485
- https://www.youm7.com/story/2018/3/12/.../3690583
- https://aawsat.com/home/article/1291516
- https://archive.aawsat.com/print.asp?did=330640&issueno=9831
- https://www.alriyadh.com/103983
- https://www.alriyadh.com/103333
- https://midad.com/article/221380
- https://praytimes.org/docs/methods
- https://ftp.shamela.ws/book/145204/650
- https://omernour.blogspot.com/2015/03/blog-post_21.html
- https://aliftaa.jo/Question.aspx?QuestionId=2002
- https://www.muftiwp.gov.my/en/artikel/bayan-linnas/3858-...
- https://muftiwp.gov.my/en/artikel/al-kafi-li-al-fatawi/4271-...
- https://www.muftiselangor.gov.my/2023/10/12/hanya-waktu-subuh-yang-berubah-mufti-selangor/
- https://ejournal.um.edu.my/index.php/fiqh/article/download/17896/10740/45235
- https://muhammadiyah.or.id/2020/12/muhammadiyah-koreksi-waktu-subuh-dari-20-ke-18-derajat/
- https://muhammadiyah.or.id/2021/03/keputusan-pp-muhammadiyah-tentang-kriteria-awal-waktu-subuh/
- https://www.kompas.tv/nasional/132347/...
- https://openfajr.org/docs/research_paper.pdf
- https://www.eastlondonmosque.org.uk/prayer-times-and-calendar-explained
- https://www.islam21c.com/special/prayer-fasting-ramadan-timetables/
- http://daruliftabirmingham.co.uk/fajr-and-esha-time-in-britain/
- https://islamqa.org/hanafi/daruliftaa-birmingham/86985
- https://islamqa.info/en/answers/311727
- https://fiqhcouncil.org/fifteen-or-eighteen-degrees-calculating-prayer-fasting-times-in-islam/

Scratch copies of the downloaded pages and the unpacked adhan2 sources are in `/private/tmp/claude-501/-Users-mohamedabulgasem-Desktop-Workspace-apps/d2f2d5b2-1f80-4ac1-b4d2-f0b36f6f4636/scratchpad/round/fiqh-fajr-angle/`.