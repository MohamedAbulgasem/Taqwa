# Urdu (ur) review of the Adhkar translations

Reviewer: Claude (a second reading, separate from the one that wrote the draft), 11 October 2026, on branch `adhkar-text-ur` from `adhkar-12h` 88bbc27e.
File reviewed: `shared/src/commonMain/composeResources/files/adhkar/text/ur.json`.

Verdict: **ships with the corrections below.** Every one of the 15 du‘a (17 wordings) was read against the **Arabic** in `items.json`, with OpenDua’s English only as a cross-check. Six wordings are corrected, in five items; the other eleven pass as drafted. After the corrections every item passes, so `"reviewed"` is `true`.

This is not a native speaker’s reading. Wherever a choice depended on how Urdu readers hear a word, it is written down below so a native reader can overrule it.

## What each item was checked for

- **Meaning.** Each clause of the Arabic is in the Urdu, nothing is added, and fronted words keep their emphasis (بِكَ, إِلَيْكَ, بِرَحْمَتِكَ become تجھ ہی سے, تیری ہی طرف, تیری ہی رحمت).
- **Terms.** The usual Urdu words for these du‘a are used: معبود, شریک, بادشاہی for الملك, پناہ چاہتا ہوں for أعوذ, سوال کرتا ہوں for أسألك, معافی اور عافیت, فطرت, کلمۂ اخلاص, ملت, یکسو, مشرک, اٹھ کر جانا for النشور and لوٹنا for المصير.
- **The app’s own wording.** These match the Urdu Tasbeeh meanings in `values-ur/strings.xml` exactly: سب تعریف اللہ ہی کے لیے ہے (الحمد لله, hisn-077), اللہ کے سوا کوئی معبود نہیں (077, 079, 085, 092, 093), اللہ پاک ہے اور اسی کی تعریف ہے (091, 094) and میں اللہ سے مغفرت مانگتا ہوں (096). The app’s bundled Quran translation, Junagarhi, writes the same words the same way: سب تعریف … کے لئے ہے at 1:2, تیری ہی طرف لوٹنا ہے for وَإِلَيْكَ الْمَصِيرُ at 2:285, اپنے باپ ابراہیم at 22:78, یکسو at 6:79 and مشرکوں میں سے نہ تھے at 16:120.
- **Script and punctuation.** Every character was listed by code point. The text uses only Urdu letters (ی ے ک ہ ھ ں ٹ ڈ ڑ, with no Arabic ي ك ه), ۂ (U+06C2) in کلمۂ, ۔ and ، for punctuation, and ! after a vocative (اے اللہ!), as Junagarhi does. All of it is NFC, with no bidi controls and no ASCII apostrophes. The text runs right to left with no Latin inside it.
- **Honorifics.** ﷺ after محمد, because the Arabic has صلى الله عليه وسلم. Allah is plain اللہ, as the Tasbeeh screen has it. Allah is addressed as تو / تیرا, with imperatives in the تو-form, and the verbs are masculine, as the booklets print them.

## Corrections (before, after, literal English of the new text)

### hisn-078, morning and evening: بِكَ is تجھ ہی سے (the owner’s question 3)

- Before (morning): اے اللہ! **تیرے ساتھ** ہم نے صبح کی اور **تیرے ساتھ** ہم نے شام کی۔ **تیرے ساتھ** ہم جیتے ہیں اور **تیرے ساتھ** ہم مرتے ہیں، اور تیری ہی طرف اٹھ کر جانا ہے۔
- After (morning): اے اللہ! **تجھ ہی سے** ہم نے صبح کی اور **تجھ ہی سے** ہم نے شام کی۔ **تجھ ہی سے** ہم جیتے ہیں اور **تجھ ہی سے** ہم مرتے ہیں، اور تیری ہی طرف اٹھ کر جانا ہے۔
- Literal English: “O Allah! By You alone we entered the morning and by You alone we entered the evening. By You alone we live and by You alone we die, and to You alone is the going, raised up.”
- After (evening): اے اللہ! **تجھ ہی سے** ہم نے شام کی اور **تجھ ہی سے** ہم نے صبح کی۔ **تجھ ہی سے** ہم جیتے ہیں اور **تجھ ہی سے** ہم مرتے ہیں، اور تیری ہی طرف لوٹنا ہے۔
- Literal English: “O Allah! By You alone we entered the evening and by You alone we entered the morning. By You alone we live and by You alone we die, and to You alone is the returning.”
- Why:
  1. **تیرے ساتھ** reads first as “together with You, in Your company”, which the Arabic does not say.
  2. **سے** is the Urdu “by, through” (as in اللہ ہی سے سب کچھ ہوتا ہے), which is what the بِ of بِكَ means.
  3. **ہی** is not an addition. The Arabic puts بِكَ before the verb (بِكَ أَصْبَحْنَا, not أَصْبَحْنَا بِكَ), and that order is the Arabic’s own “by You and no other”. The draft already gives the same order this way in the same sentence, at **تیری ہی طرف** for the fronted وَإِلَيْكَ.
  4. The booklet glosses **تیری ہی توفیق سے**, **تیرے ہی حکم سے** and **تیرے ہی نام سے** each pick one of the explanations the commentators give (by Your power, by Your grace, by Your name), and the rules leave explanations out.

### hisn-084: اس سے کہ is اس بات سے کہ

- Before: … اور میں تیری عظمت کی پناہ چاہتا ہوں **اس سے کہ** اپنے نیچے سے اچانک ہلاک کیا جاؤں۔
- After: … اور میں تیری عظمت کی پناہ چاہتا ہوں **اس بات سے کہ** اپنے نیچے سے اچانک ہلاک کیا جاؤں۔
- Literal English of the changed clause: “and I seek the refuge of Your greatness **from this matter, that** I be destroyed suddenly from beneath me.”
- Why: on its own, اس سے reads “from him” or “from it”. اس بات سے کہ is the ordinary Urdu frame for a clause like أَنْ أُغْتَالَ (“that I be…”). The meaning is unchanged: being destroyed unawares from beneath, not OpenDua’s gloss about the earth swallowing.

### hisn-085: the same, where it is ambiguous

- Before: … شیطان کے شر اور اس کے جال سے، اور **اس سے کہ** اپنی جان پر کوئی برائی کروں …
- After: … شیطان کے شر اور اس کے جال سے، اور **اس بات سے کہ** اپنی جان پر کوئی برائی کروں …
- Literal English of the changed clause: “from the evil of Satan and his snare, and **from this matter, that** I commit some evil against my own soul or drag it to some Muslim”.
- Why: right after اس کے جال (“his snare”), اس سے reads “from him”, meaning Satan, and the clause then says something else. The rest of the item stands. **جال**, “snare”, follows the 11 October decision that the item’s Arabic stays وَشَرَكِهِ (“his snares or traps”), as OpenDua vowels it, and is not read شِرْكِهِ.

### hisn-090: the order of the Arabic, with the relative clause beside Ibrahim

- Before: ہم نے اسلام کی فطرت پر، کلمۂ اخلاص پر، اپنے نبی محمد ﷺ کے دین پر، اور اپنے باپ ابراہیم کی ملت پر **صبح کی**، جو یکسو مسلمان تھے اور مشرکوں میں سے نہ تھے۔
- After: ہم نے اسلام کی فطرت پر **صبح کی**، **اور** کلمۂ اخلاص پر، **اور** اپنے نبی محمد ﷺ کے دین پر، اور اپنے باپ ابراہیم کی ملت پر، جو یکسو مسلمان تھے اور مشرکوں میں سے نہ تھے۔
- Literal English: “We entered the morning upon the natural way (fitrah) of Islam, and upon the word of sincerity, and upon the religion of our Prophet Muhammad ﷺ, and upon the way (millah) of our father Ibrahim, who was an upright Muslim and was not of the polytheists.”
- Why: in the draft, the clause جو یکسو مسلمان تھے came after the verb صبح کی. Its plural تھے could then attach to ہم (“we, who were upright Muslims”). Placed beside ابراہیم, it can only mean him, as the Arabic’s حَنِيفاً مُسْلِماً وَمَا كَانَ does. The verb now comes after the first phrase and each وَعَلَى is an اور … پر, as the Arabic is built.

### hisn-097: مِنْ شَرِّ مَا خَلَقَ as the app’s own Quran translation says it

- Before: میں اللہ کے کامل کلمات کی پناہ چاہتا ہوں **اس کے شر سے جو اس نے پیدا کیا**۔
- After: میں اللہ کے کامل کلمات کی پناہ چاہتا ہوں **ہر اس چیز کے شر سے جو اس نے پیدا کی ہے**۔
- Literal English: “I seek the refuge of Allah’s perfect words from the evil of everything that He has created.”
- Why: اس کے شر سے reads first as “from his evil”. ہر اس چیز … جو is how Urdu says the open مَا (“whatever He created”), so it adds nothing. The same three Arabic words are al-Falaq 113:2, and the app’s Junagarhi translation renders them with this phrase on the al-Falaq page of the same evening set, so the reader now meets the same Arabic in the same Urdu.

## Passed as drafted

- **hisn-077, morning and evening.** اللہ کی بادشاہی نے صبح کی (شام کی) keeps that the dominion too entered the morning (evening), belonging to Allah. بادشاہی is used for both الملك. بڑھاپے کی برائی is right for سُوءِ الْكِبَرِ, and الْكِبَرِ here means old age, not pride. اس دن کی بھلائی for خَيْرَ مَا فِي هَذَا الْيَوْمِ is the usual Urdu, with the same meaning. جہنم for النار is one of Junagarhi’s two renderings of it (2:201); the other is آگ (3:16).
- **hisn-079.** Every clause is there. أَبُوءُ is اعتراف کرتا ہوں. لَكَ, which comes only with the first أَبُوءُ, is تیرے سامنے, and it is not repeated.
- **hisn-086.** “No thing can harm” is the usual sense of لاَ يَضُرُّ … شَيْءٌ. The negation covers both places.
- **hisn-088.** حَيّ and قَيُّوم are ہمیشہ زندہ رہنے والے and سب کو تھامنے والے; Junagarhi has سب کا تھامنے والا at 2:255. أَسْتَغِيثُ is فریاد کرتا ہوں, “to cry for help”. وسیلے is seeking help through Allah’s own attribute, which no school disputes. طَرْفَةَ عَيْنٍ is پلک جھپکنے کے برابر بھی.
- **hisn-091, 092, 093, 094, 095, 096.** These match the Arabic and the app’s Tasbeeh wording. In 094, رِضَا نَفْسِهِ is اس کی اپنی رضا.

## The Quran pages

ur.json carries no entry for hisn-075 (Ayat al-Kursi), 076a, 076b or 076c (al-Ikhlas, al-Falaq, an-Nas), and must not: `check_items.py` and `AdhkarTranslationsTest` accept exactly the 15 du‘a ids. `AdhkarViewModel.quranText` draws those pages’ translation from `quran.db` in the reader’s chosen Quran translation. Under the Urdu interface that defaults to `ur.junagarhi` (`ReadingSettings`, "ur" to "ur.junagarhi"). It reads one ayah for 2:255, and the whole surah with its ayahs joined by a space for the Quls, unedited. The bundled rows: 2:255 is 1 ayah, 112 has 4, 113 has 5 and 114 has 6, all from `ur.junagarhi`, “Tanzil Project, non-commercial use, verbatim, credit the translator”. No Urdu Quran text was written or changed here.

Those rows keep Tanzil’s own spellings: the Arabic ه in وه and پناه, the presentation forms ﻻ (U+FEFB) and ﻇ (U+FEC7) in 2:255. So the Quran pages’ Urdu draws a few letters differently from the du‘a pages. The licence asks for verbatim text, so this is noted, not changed.

## For the owner

1. **The ابراہیم honorific.** The Arabic of hisn-090 has none after إبراهيم, so the Urdu adds none, as the other four languages don’t. Urdu readers usually see ابراہیم علیہ السلام. Junagarhi adds (علیہ السلام) in brackets at 22:78. To follow that, it would read اپنے باپ ابراہیم (علیہ السلام) کی ملت پر, an addition the drafting rules leave out.
2. **Masculine verbs** (کرتا ہوں, چاہتا ہوں), as printed booklets have them. A woman reading says کرتی ہوں, چاہتی ہوں, as she does with any booklet.
3. **Who reviewed.** This review is Claude’s. The language ships on it because the review brief said to flip the flag once every item passed. If you want a native Urdu reader before 1.1.0, set `"reviewed"` back to `false` and move `ur` back to `DRAFTS` in `AdhkarTranslationsTest`. That one commit takes it out, and the corrections stay.
