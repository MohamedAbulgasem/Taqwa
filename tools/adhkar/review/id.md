# Adhkar translation review: Indonesian (id)

File: `shared/src/commonMain/composeResources/files/adhkar/text/id.json`

Reviewer: Claude (a review agent, not a native speaker), 11 October 2026, on Dev 2's
`adhkar-12h` head 88bbc27e. Read against the **Arabic** in `items.json` first, with OpenDua's
English only as a cross-check, against:

- the established wording of Indonesian doa translations, and Kemenag's own wording where the du'a
  shares a phrase with the Quran (the app's bundled `id.indonesian`, Terjemahan Kementerian Agama,
  read from `quran.db`);
- the app's Indonesian strings (`values-in/strings.xml`): the Tasbeeh meanings ("Mahasuci Allah",
  "Segala puji bagi Allah", "Tiada tuhan selain Allah", "Aku memohon ampun kepada Allah",
  "Mahasuci Allah dan segala puji bagi-Nya"), "pagi" and "petang" for the two sets, "aku" for the
  speaker;
- KBBI/PUEBI spelling: *maha* joined before a base word (Mahakuasa, Mahahidup, Mahasuci), apart
  before an affixed one (Maha Mendengar, Maha Mengetahui, Maha Berdiri Sendiri); gaib, setan,
  keridaan, Arasy, bertobat, rezeki;
- the conventions every language follows: "Allah" untranslated; Allah addressed as Engkau/-Mu and
  spoken of as Dia/-Nya, capitalised; ﷺ after the Prophet's name; no explanatory additions.

**Verdict: ships with the three corrections below.** Every item now passes, so `"reviewed"` is
`true`. The other twelve items (fifteen wordings) were already right and are unchanged.

## Corrections

| Item | Draft | Now | Literal English of the new text |
|------|-------|-----|----------------------------------|
| hisn-086 | yang **bersama** nama-Nya | yang **dengan** nama-Nya | "In the name of Allah, **with** whose name there is nothing on earth or in the sky that can harm, and He is the All-Hearing, the All-Knowing." |
| hisn-090 | di atas **millah** bapak kami Ibrahim | di atas **agama** bapak kami Ibrahim | "…and upon the **religion** of our father Ibrahim, upright and submitting, and he is not among the polytheists." |
| hisn-097 | dari keburukan **apa** yang Dia ciptakan | dari keburukan **makhluk** yang Dia ciptakan | "I seek refuge by the perfect words of Allah from the evil of the **creatures** that He created." |

Why:

- **hisn-086, مَعَ اسْمِهِ.** Both words are literal for مع, but "yang bersama nama-Nya" reads as
  "alongside His name" and is not how Indonesian doa books say it; "yang dengan nama-Nya tidak ada
  sesuatu pun … yang dapat membahayakan" is the established wording, and *dengan* carries the
  sense of the hadith (it is by His name being said that nothing harms).
- **hisn-090, مِلَّةِ أَبِينَا إِبْرَاهِيمَ.** Kemenag renders ملة إبراهيم as **agama Ibrahim** every
  time (2:135, 3:95, 16:123, 22:78 in the bundled text); *millah* is a loanword most readers do
  not use. The Arabic's دين and ملة then both read *agama*, as they do in Indonesian booklets. The
  ending already matches Kemenag's 3:67 and 3:95 ("yang lurus lagi berserah diri", "bukanlah dia
  termasuk orang-orang musyrik").
- **hisn-097, مِنْ شَرِّ مَا خَلَقَ.** "keburukan apa yang Dia ciptakan" can be read as "whatever
  evil He created" (*keburukan apa* = "what evil"), which is not the Arabic's "the evil of what He
  created". "dari keburukan makhluk yang Dia ciptakan" is the established doa wording, cannot be
  misread, and keeps the verb خلق visible; it agrees with Kemenag's 113:2 for the same three words
  ("dari kejahatan makhluk-Nya") except that the adhkar keep *keburukan* for شر throughout.

## Every item, as it now reads

Literal back-translations (Indonesian has no tense; the English follows the Arabic's).

| Item | Verdict | Literal English |
|------|---------|-----------------|
| hisn-077 morning | correct | We have entered the time of morning, and on this morning the dominion is Allah's. All praise is for Allah. There is no god but Allah alone, He has no partner. His is the dominion and His is all praise, and He is All-Powerful over every thing. O my Lord, I ask of You the good that is in this day and the good after it. I seek refuge in You from the evil that is in this day and the evil after it. O my Lord, I seek refuge in You from laziness and the badness of old age. O my Lord, I seek refuge in You from punishment in the Fire and punishment in the grave. |
| hisn-077 evening | correct | The same, with "the time of evening", "on this evening" and "this night" (هذه الليلة). |
| hisn-078 morning | correct | O Allah, by You we enter the time of morning and by You we enter the time of evening. By You we live and by You we die, and to You is the resurrection. |
| hisn-078 evening | correct | O Allah, by You we enter the time of evening and by You we enter the time of morning. By You we live and by You we die, and to You is the place of return. |
| hisn-079 | correct | O Allah, You are my Lord, there is no god but You. You created me and I am Your servant. I keep to Your covenant and Your promise as far as I am able. I seek refuge in You from the evil of what I have done. I acknowledge before You Your favour to me, and I acknowledge my sin, so forgive me, for there is none who forgives sins but You. |
| hisn-084 | correct | O Allah, I ask of You pardon and well-being in the world and the Hereafter. O Allah, I ask of You pardon and well-being in my religion, my worldly life, my family and my wealth. O Allah, cover my faults and calm my fear. O Allah, guard me from my front, from my back, from my right, from my left and from above me, and I seek refuge in Your greatness lest I be seized unawares from beneath me. |
| hisn-085 | correct | O Allah, Knower of the unseen and the seen, Creator of the heavens and the earth, Lord and Ruler of every thing, I bear witness that there is no god but You. I seek refuge in You from the evil of myself, from the evil of Satan and his trap, and from doing evil against myself or bringing it upon a Muslim. |
| hisn-086 | corrected | In the name of Allah, with whose name there is nothing on earth or in the sky that can harm, and He is the All-Hearing, the All-Knowing. |
| hisn-088 | correct | O Ever-Living, O Self-Subsisting (lit. "Who stands by Himself"), by Your mercy I ask for help. Set right all my affairs and do not leave me to myself even for the blink of an eye. |
| hisn-090 morning | corrected | We have entered the time of morning upon the fitrah of Islam, upon the word of sincerity, upon the religion of our Prophet Muhammad ﷺ, and upon the religion of our father Ibrahim, upright and submitting, and he is not among the polytheists. |
| hisn-091 | correct | Allah is Most Pure, and all praise is His. |
| hisn-092 | correct | There is no god but Allah alone, He has no partner, His is the dominion and His is all praise, and He is All-Powerful over every thing. |
| hisn-093 | correct | The same as hisn-092 (the Arabic differs by one comma). |
| hisn-094 | correct | Allah is Most Pure, and all praise is His, as many as the number of His creatures, as befits the good pleasure of His Self, as heavy as His Throne, and as much as the ink of His words. |
| hisn-095 | correct | O Allah, I ask of You knowledge that is beneficial, provision that is good, and deeds that are accepted. |
| hisn-096 | correct | I ask forgiveness of Allah and repent to Him. |
| hisn-097 | corrected | I seek refuge by the perfect words of Allah from the evil of the creatures that He created. |

## The decisions taken by the lead

- **hisn-085 شَرَكِهِ** stays as OpenDua vowels it, so the translation is "dari keburukan setan dan
  **perangkapnya**" (his trap / snare), as drafted, not *syirik*. It matches the Arabic on screen.
  (The English `en.json` still says "shirk"; that file is not this review's.)
- Bengali and Urdu decisions do not touch this file.

## Choices kept, and why

- **hisn-077 أَصْبَحَ الْمُلْكُ لِلَّهِ** stays "dan pada pagi ini kerajaan milik Allah": it keeps
  that the dominion too is Allah's *this morning*, and adds no "hanya" (only), which many booklets
  write but the Arabic does not. *Kerajaan* for الملك is Kemenag's (64:1, 67:1).
- **رَبِّ** is "Ya Tuhanku", Kemenag's form; **لا إله إلا** is "tiada tuhan selain", with a lowercase
  *tuhan* as the app's own `dhikr_meaning_la_ilaha_illallah` writes it (Kemenag capitalises it;
  the app does not, and the adhkar follow the app).
- **hisn-078 بِكَ** stays the literal "dengan-Mu" (no "dengan rahmat-Mu" gloss); *dengan* is
  instrumental in Indonesian, so it reads "by You", not "together with You". النشور is
  "kebangkitan", المصير "tempat kembali" (Kemenag 2:285 "kepada Engkaulah tempat kembali").
- **hisn-079 شَرِّ مَا صَنَعْتُ** stays "keburukan apa yang telah kuperbuat": the usual wording of
  Sayyidul Istighfar in Indonesian, and its other reading ("whatever evil I have done") is close to
  the Arabic, unlike hisn-097's. "Menetapi perjanjian-Mu dan janji-Mu semampuku" is the
  established rendering of وأنا على عهدك ووعدك ما استطعت.
- **hisn-084 عَوْرَاتِي** stays "aib-aibku" (my faults), the commentaries' sense (faults and what
  one is ashamed of); *aurat* would mean the body's private parts. **العفو** stays "ampunan", the
  usual word in this du'a's Indonesian translations (strictly *pemaafan*, as Kemenag's 2:286 "beri
  maaflah kami"; not changed, since "ampunan dan keselamatan" is how readers know it). **أُغْتَالَ**
  stays "disergap" (seized unawares); booklets also write "disambar", the same sense.
- **hisn-085** "Yang Mengetahui yang gaib dan yang nyata" and "Pencipta langit dan bumi" are
  Kemenag's own wording for the same names (59:22, 39:46), in KBBI spelling (*gaib*, not *ghaib*).
- **hisn-086** ends "dan Dialah Yang Maha Mendengar lagi Maha Mengetahui", word for word Kemenag's
  rendering of وهو السميع العليم (2:137).
- **hisn-088 القيوم** stays "Yang Maha Berdiri Sendiri", the Asmaul Husna rendering Indonesians
  know for this du'a. The Ayat al-Kursi page shows Kemenag's "Yang Hidup kekal lagi terus menerus
  mengurus (makhluk-Nya)" for the same name; that page must stay Kemenag's verbatim, and the
  du'a's shorter name reads better without the parenthesis, so the two pages differ on purpose.
- **hisn-094 رِضَا نَفْسِهِ** stays "sesuai keridaan diri-Nya" (keeps نفسه); "Arasy" is KBBI's
  spelling of العرش.

## The Quran items

`id.json` carries **no** Quran text, and must not: `check_items.py` and `AdhkarTranslationsTest`
both require exactly the 15 du'a ids, and `README.md` and `TRANSLATIONS-FOR-REVIEW.md` say the app
shows its own Quran translation for them. Checked in the code: `AdhkarViewModel.quranText` reads the
reader's chosen translation from `quran.db` by reference (`translationText` for Ayat al-Kursi,
`translationTexts` joined with a space for each Qul), and an Indonesian reader's default is
`id.indonesian` (`ReadingSettings`, "id" to "id.indonesian"), Terjemahan Kementerian Agama, whose
licence is verbatim only. The bundled rows exist: 2:255, 112:1–4, 113:1–5, 114:1–6. So Ayat
al-Kursi, al-Ikhlas, al-Falaq and an-Nas show Kemenag's text exactly as bundled; nothing here edits
or replaces it. (The bundled 2:255 has a question mark after "tanpa izin-Nya" as Tanzil
distributes it; it must stay.)

Quranic phrases inside the du'a (hisn-085's names from 39:46 and 59:22, hisn-086's ending from
2:137, hisn-090's from 3:67, hisn-097's from 113:2) are part of a hadith and are translated with
it, not Quran items; their Indonesian agrees with Kemenag's wording where that reads naturally.

## Not checked

- A native Indonesian reader. This review is Claude's; if the owner wants a human reader before
  1.1.0, the corrections above are the points to show them.
- How the text draws on a phone: line breaks at large text sizes, and whether ﷺ falls back to a
  font that has it beside the app's Latin face (it is used in all five languages).
- The Arabic against the hisnmuslim.com witness (not needed for a text file; the structural and
  language checks ran).
