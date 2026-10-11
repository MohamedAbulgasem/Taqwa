# Adhkar translations for review

Drafts by Claude, 11 October 2026, for one reviewer per language; a language ships only after review.

The 15 du‘a of the Morning & Evening Adhkar (Hisn al-Muslim 77–97), translated into Bengali, French, Indonesian, Turkish and Urdu **from the Arabic**, with OpenDua’s English (CC BY 4.0) as a cross-check only. The four Quran items (Ayat al-Kursi and the three Quls) have no entry: the app shows its own Quran translation for them.

Rules the drafts follow: “Allah” untranslated (আল্লাহ, اللہ); ﷺ after the Prophet’s name; the language’s usual word for رب; no explanatory additions; the Arabic’s sentence boundaries; typographic apostrophes; the register of a careful mosque booklet. Hisn 77 and 78 have a morning and an evening wording, both listed; Hisn 90 is read in the morning only.

**How to review.** Each draft is followed by a review line. Tick it if the draft is right as it stands, or write the corrected text after “correction:”. Sign the language’s header when done. The drafts live in `shared/src/commonMain/composeResources/files/adhkar/text/<lang>.json`; corrections go there (keep the keys), then `python3 -I tools/adhkar/check_items.py` must pass. The app bundles every file in that folder, so a language that has not been reviewed must have its file taken out before a release is cut.

## Points for every reviewer

- **hisn-085 وَشَرَكِهِ.** The Arabic is vowelled *sharakihi*, “his snares” (OpenDua and hisnmuslim.com agree), and the drafts follow it (pièges, perangkap, tuzak, جال, ফাঁদ). OpenDua’s English reads the other transmitted wording, وَشِرْكِهِ, “his shirk”. If a reviewer prefers *shirk*, say so: the shipped Arabic’s vowelling would then need the same decision.
- **hisn-084 أَنْ أُغْتَالَ مِنْ تَحْتِي.** The drafts keep the literal sense, “seized unawares from beneath me”. OpenDua’s English gives the narrator’s explanation instead (“lest I be swallowed up by the earth”).
- **hisn-078 بِكَ أَصْبَحْنَا.** The drafts keep the literal “by You”. Some booklets gloss it as “by Your power” or “by Your grace”; that is an addition the rules leave out, but say if the literal form reads badly.
- **hisn-077 وَأَصْبَحَ الْمُلْكُ لِلَّهِ.** OpenDua’s English shortens this to “all sovereignty belongs to Allah”; the drafts keep that the dominion too has entered the morning (evening), belonging to Allah, as far as the language allows.
- **Sentences.** Each draft breaks where the Arabic does, like the English, except hisn-079, which is five sentences at its five clauses (the English has two), and hisn-088, which ends a sentence before the imperative أَصْلِحْ.
- **The app’s own wording.** Where the app already translates the same words (the Tasbeeh screen’s meanings of سبحان الله وبحمده, لا إله إلا الله, الحمد لله and أستغفر الله), the drafts start from that wording (hisn-077, 091–094, 096), so the same dhikr reads the same in both places.

## Languages

- [Bengali (বাংলা)](#bengali-bn) — `text/bn.json`
- [French (Français)](#french-fr) — `text/fr.json`
- [Indonesian (Bahasa Indonesia)](#indonesian-id) — `text/id.json`
- [Turkish (Türkçe)](#turkish-tr) — `text/tr.json`
- [Urdu (اردو)](#urdu-ur) — `text/ur.json`

---

## Bengali (bn)

বাংলা · `shared/src/commonMain/composeResources/files/adhkar/text/bn.json`

Reviewer: ______________________ Date: ____________

Verdict: [ ] ships as drafted [ ] ships with the corrections below [ ] does not ship yet

**Choices to weigh**

- رب is **রব** (as Bengali Hisn al-Muslim booklets print it), not Muhiuddin Khan’s পালনকর্তা; Allah is addressed as **আপনি**; شر is অনিষ্ট throughout, عذاب আযাব, النار জাহান্নাম.
- Spelling of Arabic loans: **আযাব, রিযিক** with য but **জমিন** with জ. The app itself mixes the two (নামাজ, জিকির but আযান, আযকার); please settle one convention.
- hisn-078 **আপনার দ্বারাই** is the literal “by You”; আপনারই অনুগ্রহে would be the gloss.
- hisn-090 writes **মুহাম্মাদ ﷺ-এর** (the case ending after the sign). Typed in NFC (য় as য + ়).

### bn · hisn-077, morning wording

Hisn al-Muslim 77, read morning and evening, said once.

- **Arabic:** أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيرَ مَا بَعْدَهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ
- **English (OpenDua):** We have entered the morning, and all sovereignty belongs to Allah. All praise is for Allah. None has the right to be worshipped except Allah alone, without partner. To Him belong all sovereignty and praise, and He has power over all things. My Lord, I ask You for the good of this day and what follows it. I seek refuge in You from the evil of this day and what follows it. My Lord, I seek refuge in You from laziness and the evils of old age. My Lord, I seek refuge in You from punishment in the Fire and punishment in the grave.
- **Draft:** আমরা সকালে উপনীত হয়েছি, আর আল্লাহর রাজত্বও সকালে উপনীত হয়েছে। সমস্ত প্রশংসা আল্লাহর জন্য। আল্লাহ ছাড়া কোনো ইলাহ নেই, তিনি একক, তাঁর কোনো শরিক নেই। রাজত্ব তাঁরই এবং প্রশংসাও তাঁরই, আর তিনি সবকিছুর ওপর ক্ষমতাবান। হে আমার রব! আমি আপনার কাছে এই দিনের কল্যাণ এবং এর পরে যা আসে তার কল্যাণ চাই। আমি এই দিনের অনিষ্ট এবং এর পরে যা আসে তার অনিষ্ট থেকে আপনার আশ্রয় চাই। হে আমার রব! আমি অলসতা ও বার্ধক্যের অনিষ্ট থেকে আপনার আশ্রয় চাই। হে আমার রব! আমি জাহান্নামের আযাব ও কবরের আযাব থেকে আপনার আশ্রয় চাই।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-077, evening wording

Hisn al-Muslim 77, read morning and evening, said once.

- **Arabic:** أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيرَ مَا بَعْدَهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ
- **English (OpenDua):** We have entered the evening, and all sovereignty belongs to Allah. All praise is for Allah. None has the right to be worshipped except Allah alone, without partner. To Him belong all sovereignty and praise, and He has power over all things. My Lord, I ask You for the good of this night and what follows it. I seek refuge in You from the evil of this night and what follows it. My Lord, I seek refuge in You from laziness and the evils of old age. My Lord, I seek refuge in You from punishment in the Fire and punishment in the grave.
- **Draft:** আমরা সন্ধ্যায় উপনীত হয়েছি, আর আল্লাহর রাজত্বও সন্ধ্যায় উপনীত হয়েছে। সমস্ত প্রশংসা আল্লাহর জন্য। আল্লাহ ছাড়া কোনো ইলাহ নেই, তিনি একক, তাঁর কোনো শরিক নেই। রাজত্ব তাঁরই এবং প্রশংসাও তাঁরই, আর তিনি সবকিছুর ওপর ক্ষমতাবান। হে আমার রব! আমি আপনার কাছে এই রাতের কল্যাণ এবং এর পরে যা আসে তার কল্যাণ চাই। আমি এই রাতের অনিষ্ট এবং এর পরে যা আসে তার অনিষ্ট থেকে আপনার আশ্রয় চাই। হে আমার রব! আমি অলসতা ও বার্ধক্যের অনিষ্ট থেকে আপনার আশ্রয় চাই। হে আমার রব! আমি জাহান্নামের আযাব ও কবরের আযাব থেকে আপনার আশ্রয় চাই।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-078, morning wording

Hisn al-Muslim 78, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ
- **English (OpenDua):** O Allah, by You we enter the morning, and by You we enter the evening. By You we live and by You we die, and to You is the resurrection.
- **Draft:** হে আল্লাহ! আপনার দ্বারাই আমরা সকালে উপনীত হয়েছি এবং আপনার দ্বারাই আমরা সন্ধ্যায় উপনীত হয়েছি। আপনার দ্বারাই আমরা জীবিত থাকি এবং আপনার দ্বারাই আমরা মৃত্যুবরণ করি, আর আপনার দিকেই পুনরুত্থান।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-078, evening wording

Hisn al-Muslim 78, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ
- **English (OpenDua):** O Allah, by You we enter the evening, and by You we enter the morning. By You we live and by You we die, and to You is our return.
- **Draft:** হে আল্লাহ! আপনার দ্বারাই আমরা সন্ধ্যায় উপনীত হয়েছি এবং আপনার দ্বারাই আমরা সকালে উপনীত হয়েছি। আপনার দ্বারাই আমরা জীবিত থাকি এবং আপনার দ্বারাই আমরা মৃত্যুবরণ করি, আর আপনার দিকেই প্রত্যাবর্তন।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-079

Hisn al-Muslim 79, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلاَّ أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لاَ يَغْفِرُ الذُّنوبَ إِلاَّ أَنْتَ
- **English (OpenDua):** O Allah, You are my Lord, none has the right to be worshipped except You, You created me and I am Your servant and I abide to Your covenant and promise as best I can, I take refuge in You from the evil of which I have committed. I acknowledge Your favour upon me and I acknowledge my sin, so forgive me, for verily none can forgive sin except You.
- **Draft:** হে আল্লাহ! আপনি আমার রব, আপনি ছাড়া কোনো ইলাহ নেই। আপনি আমাকে সৃষ্টি করেছেন এবং আমি আপনার বান্দা। আমি আমার সাধ্যমতো আপনার অঙ্গীকার ও প্রতিশ্রুতির ওপর আছি। আমি যা করেছি তার অনিষ্ট থেকে আপনার আশ্রয় চাই। আমার প্রতি আপনার নিয়ামত আমি আপনার কাছে স্বীকার করি এবং আমার গুনাহও স্বীকার করি, অতএব আমাকে ক্ষমা করুন, কেননা আপনি ছাড়া আর কেউ গুনাহ ক্ষমা করে না।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-084

Hisn al-Muslim 84, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالآخِرَةِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ: فِي دِينِي وَدُنْيَايَ وَأَهْلِي، وَمَالِي، اللَّهُمَّ اسْتُرْ عَوْرَاتِي، وَآمِنْ رَوْعَاتِي، اللَّهُمَّ احْفَظْنِي مِنْ بَينِ يَدَيَّ، وَمِنْ خَلْفِي، وَعَنْ يَمِينِي، وَعَنْ شِمَالِي، وَمِنْ فَوْقِي، وَأَعُوذُ بِعَظَمَتِكَ أَنْ أُغْتَالَ مِنْ تَحْتِي
- **English (OpenDua):** O Allah, I ask You for pardon and well-being in this life and the next. O Allah, I ask You for pardon and well-being in my religious and worldly affairs, and my family and my wealth. O Allah, veil my weaknesses and set at ease my dismay. O Allah, preserve me from the front and from behind and on my right and on my left and from above, and I take refuge with You lest I be swallowed up by the earth.
- **Draft:** হে আল্লাহ! আমি আপনার কাছে দুনিয়া ও আখিরাতে ক্ষমা ও নিরাপত্তা চাই। হে আল্লাহ! আমি আপনার কাছে আমার দ্বীন, আমার দুনিয়া, আমার পরিবার ও আমার সম্পদে ক্ষমা ও নিরাপত্তা চাই। হে আল্লাহ! আমার দোষত্রুটি ঢেকে রাখুন এবং আমার ভয়ভীতি থেকে আমাকে নিরাপদ রাখুন। হে আল্লাহ! আমার সামনে থেকে, আমার পেছন থেকে, আমার ডান দিক থেকে, আমার বাম দিক থেকে এবং আমার ওপর থেকে আমাকে রক্ষা করুন, আর আমার নিচ থেকে অতর্কিতে ধ্বংস হওয়া থেকে আমি আপনার মহত্ত্বের আশ্রয় চাই।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-085

Hisn al-Muslim 85, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ عَالِمَ الغَيْبِ وَالشَّهَادَةِ فَاطِرَ السَّمَوَاتِ وَالْأَرْضِ، رَبَّ كُلِّ شَيْءٍ وَمَلِيكَهُ، أَشْهَدُ أَنْ لاَ إِلَهَ إِلاَّ أَنْتَ، أَعُوذُ بِكَ مِنْ شَرِّ نَفْسِي، وَمِنْ شَرِّ الشَّيْطانِ وَشَرَكِهِ، وَأَنْ أَقْتَرِفَ عَلَى نَفْسِي سُوءاً، أَوْ أَجُرَّهُ إِلَى مُسْلِمٍ
- **English (OpenDua):** O Allah, Knower of the unseen and the seen, Creator of the heavens and the Earth, Lord and Sovereign of all things, I bear witness that none has the right to be worshipped except You. I take refuge in You from the evil of my soul and from the evil and shirk of the devil, and from committing wrong against my soul or bringing such upon another Muslim.
- **Draft:** হে আল্লাহ! অদৃশ্য ও দৃশ্যের জ্ঞানী, আসমান ও জমিনের স্রষ্টা, সবকিছুর রব ও মালিক! আমি সাক্ষ্য দিচ্ছি যে, আপনি ছাড়া কোনো ইলাহ নেই। আমি আমার নফসের অনিষ্ট থেকে, শয়তানের অনিষ্ট ও তার ফাঁদ থেকে, এবং নিজের ওপর কোনো মন্দ কাজ করা অথবা তা কোনো মুসলিমের দিকে টেনে আনা থেকে আপনার আশ্রয় চাই।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-086

Hisn al-Muslim 86, read morning and evening, said 3 times.

- **Arabic:** بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلاَ فِي السّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ
- **English (OpenDua):** In the name of Allah with whose name nothing is harmed on earth nor in the heavens, and He is the All-Hearing, the All-Knowing.
- **Draft:** আল্লাহর নামে, যাঁর নামের সাথে জমিনে ও আসমানে কোনো কিছুই ক্ষতি করতে পারে না, আর তিনি সর্বশ্রোতা, সর্বজ্ঞ।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-088

Hisn al-Muslim 88, read morning and evening, said once.

- **Arabic:** يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغيثُ أَصْلِحْ لِي شَأْنِيَ كُلَّهُ وَلاَ تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ
- **English (OpenDua):** O Ever Living, O Self-Subsisting and Supporter of all, by Your mercy I seek assistance, rectify for me all of my affairs and do not leave me to myself, even for the blink of an eye.
- **Draft:** হে চিরঞ্জীব, হে সবকিছুর ধারক! আমি আপনার রহমতের মাধ্যমে সাহায্য প্রার্থনা করি। আমার সকল বিষয় সংশোধন করে দিন এবং এক পলকের জন্যও আমাকে আমার নিজের ওপর ছেড়ে দেবেন না।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-090, morning wording only

Hisn al-Muslim 90, read morning only, said once.

- **Arabic:** أَصْبَحْنَا عَلَى فِطْرَةِ الْإِسْلاَمِ، وَعَلَى كَلِمَةِ الْإِخْلاَصِ، وَعَلَى دِينِ نَبِيِّنَا مُحَمَّدٍ صلى الله عليه وسلم، وَعَلَى مِلَّةِ أَبِينَا إِبْرَاهِيمَ، حَنِيفاً مُسْلِماً وَمَا كَانَ مِنَ الْمُشْرِكِينَ
- **English (OpenDua):** We have entered the morning upon the natural way of Islam, the declaration of sincere faith, the religion of our Prophet Muhammad (may Allah’s peace and blessings be upon him), and the faith of our forefather Ibraheem, who was upright, a Muslim, and not among those who associate partners with Allah.
- **Draft:** আমরা সকালে উপনীত হয়েছি ইসলামের ফিতরাতের ওপর, ইখলাসের কালিমার ওপর, আমাদের নবী মুহাম্মাদ ﷺ-এর দ্বীনের ওপর এবং আমাদের পিতা ইবরাহীমের মিল্লাতের ওপর, যিনি ছিলেন একনিষ্ঠ মুসলিম এবং মুশরিকদের অন্তর্ভুক্ত ছিলেন না।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-091

Hisn al-Muslim 91, read morning and evening, said 100 times.

- **Arabic:** سُبْحَانَ اللَّهِ وَبِحَمْدِهِ
- **English (OpenDua):** How perfect Allah is and I praise Him.
- **Draft:** আমি আল্লাহর পবিত্রতা ও প্রশংসা ঘোষণা করি।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-092

Hisn al-Muslim 92, read morning and evening, said 10 times.

- **Arabic:** لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ
- **English (OpenDua):** None has the right to be worshipped except Allah, alone, without partner, to Him belongs all sovereignty and praise, and He is over all things omnipotent.
- **Draft:** আল্লাহ ছাড়া কোনো ইলাহ নেই, তিনি একক, তাঁর কোনো শরিক নেই, রাজত্ব তাঁরই এবং প্রশংসাও তাঁরই, আর তিনি সবকিছুর ওপর ক্ষমতাবান।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-093

Hisn al-Muslim 93, read morning only, said 100 times.

- **Arabic:** لاَ إِلَهَ إِلاَّ اللَّهُ، وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ
- **English (OpenDua):** None has the right to be worshipped except Allah, alone, without partner, to Him belongs all sovereignty and praise, and He is over all things omnipotent.
- **Draft:** আল্লাহ ছাড়া কোনো ইলাহ নেই, তিনি একক, তাঁর কোনো শরিক নেই, রাজত্ব তাঁরই এবং প্রশংসাও তাঁরই, আর তিনি সবকিছুর ওপর ক্ষমতাবান।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-094

Hisn al-Muslim 94, read morning only, said 3 times.

- **Arabic:** سُبْحَانَ اللَّهِ وَبِحَمْدِهِ: عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ
- **English (OpenDua):** How perfect Allah is and I praise Him by the number of His creation and His pleasure, and by the weight of His throne, and the ink of His words.
- **Draft:** আমি আল্লাহর পবিত্রতা ও প্রশংসা ঘোষণা করি তাঁর সৃষ্টির সংখ্যার সমান, তাঁর নিজের সন্তুষ্টির সমান, তাঁর আরশের ওজনের সমান এবং তাঁর বাণীসমূহের কালির সমান।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-095

Hisn al-Muslim 95, read morning only, said once.

- **Arabic:** اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْماً نَافِعاً، وَرِزْقاً طَيِّباً، وَعَمَلاً مُتَقَبَّلاً
- **English (OpenDua):** O Allah, I ask You for knowledge which is beneficial and sustenance which is good, and deeds which are acceptable.
- **Draft:** হে আল্লাহ! আমি আপনার কাছে উপকারী জ্ঞান, পবিত্র রিযিক এবং কবুলযোগ্য আমল চাই।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-096

Hisn al-Muslim 96, read morning and evening, said 100 times a day.

- **Arabic:** أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ
- **English (OpenDua):** I seek Allah’s forgiveness and I turn to Him in repentance.
- **Draft:** আমি আল্লাহর কাছে ক্ষমা চাই এবং তাঁর কাছে তাওবা করি।
- **Review:** [ ] correct as drafted · correction:

### bn · hisn-097

Hisn al-Muslim 97, read evening only, said 3 times.

- **Arabic:** أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ
- **English (OpenDua):** I take refuge in Allah’s perfect words from the evil He has created.
- **Draft:** আমি আল্লাহর পরিপূর্ণ বাণীসমূহের মাধ্যমে আশ্রয় চাই, তিনি যা সৃষ্টি করেছেন তার অনিষ্ট থেকে।
- **Review:** [ ] correct as drafted · correction:

---

## French (fr)

Français · `shared/src/commonMain/composeResources/files/adhkar/text/fr.json`

Reviewer: ______________________ Date: ____________

Verdict: [ ] ships as drafted [ ] ships with the corrections below [ ] does not ship yet

**Choices to weigh**

- رب is **Seigneur**; Allah is addressed as **Tu/Toi/Ton** with capitals; “Il n’y a de dieu qu’Allah” as the app already writes it.
- hisn-090 has **Abraham**, the French name Hamidullah’s translation in the app uses; Ibrahim is the alternative. كلمة الإخلاص is the literal **la parole de la sincérité**; “la parole du monothéisme pur” would gloss it.
- السميع العليم is **l’Audient, l’Omniscient** (Hamidullah); القيوم is “Toi qui subsistes par Toi-même”.
- No : ; ! ? appear, so no narrow no-break space is needed; a correction that adds one should use U+202F as the app’s French strings do.

### fr · hisn-077, morning wording

Hisn al-Muslim 77, read morning and evening, said once.

- **Arabic:** أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيرَ مَا بَعْدَهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ
- **English (OpenDua):** We have entered the morning, and all sovereignty belongs to Allah. All praise is for Allah. None has the right to be worshipped except Allah alone, without partner. To Him belong all sovereignty and praise, and He has power over all things. My Lord, I ask You for the good of this day and what follows it. I seek refuge in You from the evil of this day and what follows it. My Lord, I seek refuge in You from laziness and the evils of old age. My Lord, I seek refuge in You from punishment in the Fire and punishment in the grave.
- **Draft:** Nous voici au matin, et au matin la royauté appartient à Allah. Toute louange revient à Allah. Il n’y a de dieu qu’Allah, seul, sans associé. À Lui la royauté et à Lui la louange, et Il est capable de toute chose. Seigneur, je Te demande le bien de ce jour et le bien de ce qui le suit. Je cherche refuge auprès de Toi contre le mal de ce jour et le mal de ce qui le suit. Seigneur, je cherche refuge auprès de Toi contre la paresse et les maux de la vieillesse. Seigneur, je cherche refuge auprès de Toi contre un châtiment dans le Feu et un châtiment dans la tombe.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-077, evening wording

Hisn al-Muslim 77, read morning and evening, said once.

- **Arabic:** أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيرَ مَا بَعْدَهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ
- **English (OpenDua):** We have entered the evening, and all sovereignty belongs to Allah. All praise is for Allah. None has the right to be worshipped except Allah alone, without partner. To Him belong all sovereignty and praise, and He has power over all things. My Lord, I ask You for the good of this night and what follows it. I seek refuge in You from the evil of this night and what follows it. My Lord, I seek refuge in You from laziness and the evils of old age. My Lord, I seek refuge in You from punishment in the Fire and punishment in the grave.
- **Draft:** Nous voici au soir, et au soir la royauté appartient à Allah. Toute louange revient à Allah. Il n’y a de dieu qu’Allah, seul, sans associé. À Lui la royauté et à Lui la louange, et Il est capable de toute chose. Seigneur, je Te demande le bien de cette nuit et le bien de ce qui la suit. Je cherche refuge auprès de Toi contre le mal de cette nuit et le mal de ce qui la suit. Seigneur, je cherche refuge auprès de Toi contre la paresse et les maux de la vieillesse. Seigneur, je cherche refuge auprès de Toi contre un châtiment dans le Feu et un châtiment dans la tombe.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-078, morning wording

Hisn al-Muslim 78, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ
- **English (OpenDua):** O Allah, by You we enter the morning, and by You we enter the evening. By You we live and by You we die, and to You is the resurrection.
- **Draft:** Ô Allah, c’est par Toi que nous sommes parvenus au matin et par Toi que nous sommes parvenus au soir. Par Toi nous vivons et par Toi nous mourons, et vers Toi est la résurrection.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-078, evening wording

Hisn al-Muslim 78, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ
- **English (OpenDua):** O Allah, by You we enter the evening, and by You we enter the morning. By You we live and by You we die, and to You is our return.
- **Draft:** Ô Allah, c’est par Toi que nous sommes parvenus au soir et par Toi que nous sommes parvenus au matin. Par Toi nous vivons et par Toi nous mourons, et vers Toi est le retour.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-079

Hisn al-Muslim 79, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلاَّ أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لاَ يَغْفِرُ الذُّنوبَ إِلاَّ أَنْتَ
- **English (OpenDua):** O Allah, You are my Lord, none has the right to be worshipped except You, You created me and I am Your servant and I abide to Your covenant and promise as best I can, I take refuge in You from the evil of which I have committed. I acknowledge Your favour upon me and I acknowledge my sin, so forgive me, for verily none can forgive sin except You.
- **Draft:** Ô Allah, Tu es mon Seigneur, il n’y a de dieu que Toi. Tu m’as créé et je suis Ton serviteur. Je reste fidèle à Ton pacte et à Ta promesse autant que je le peux. Je cherche refuge auprès de Toi contre le mal de ce que j’ai fait. Je reconnais devant Toi Ton bienfait envers moi, et je reconnais mon péché, pardonne-moi donc, car nul autre que Toi ne pardonne les péchés.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-084

Hisn al-Muslim 84, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالآخِرَةِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ: فِي دِينِي وَدُنْيَايَ وَأَهْلِي، وَمَالِي، اللَّهُمَّ اسْتُرْ عَوْرَاتِي، وَآمِنْ رَوْعَاتِي، اللَّهُمَّ احْفَظْنِي مِنْ بَينِ يَدَيَّ، وَمِنْ خَلْفِي، وَعَنْ يَمِينِي، وَعَنْ شِمَالِي، وَمِنْ فَوْقِي، وَأَعُوذُ بِعَظَمَتِكَ أَنْ أُغْتَالَ مِنْ تَحْتِي
- **English (OpenDua):** O Allah, I ask You for pardon and well-being in this life and the next. O Allah, I ask You for pardon and well-being in my religious and worldly affairs, and my family and my wealth. O Allah, veil my weaknesses and set at ease my dismay. O Allah, preserve me from the front and from behind and on my right and on my left and from above, and I take refuge with You lest I be swallowed up by the earth.
- **Draft:** Ô Allah, je Te demande le pardon et la préservation dans ce monde et dans l’au-delà. Ô Allah, je Te demande le pardon et la préservation dans ma religion, ma vie d’ici-bas, ma famille et mes biens. Ô Allah, voile mes défauts et apaise mes frayeurs. Ô Allah, protège-moi par-devant et par-derrière, à ma droite et à ma gauche, et par en haut, et je cherche refuge auprès de Ta grandeur pour ne pas être pris à l’improviste par en bas.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-085

Hisn al-Muslim 85, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ عَالِمَ الغَيْبِ وَالشَّهَادَةِ فَاطِرَ السَّمَوَاتِ وَالْأَرْضِ، رَبَّ كُلِّ شَيْءٍ وَمَلِيكَهُ، أَشْهَدُ أَنْ لاَ إِلَهَ إِلاَّ أَنْتَ، أَعُوذُ بِكَ مِنْ شَرِّ نَفْسِي، وَمِنْ شَرِّ الشَّيْطانِ وَشَرَكِهِ، وَأَنْ أَقْتَرِفَ عَلَى نَفْسِي سُوءاً، أَوْ أَجُرَّهُ إِلَى مُسْلِمٍ
- **English (OpenDua):** O Allah, Knower of the unseen and the seen, Creator of the heavens and the Earth, Lord and Sovereign of all things, I bear witness that none has the right to be worshipped except You. I take refuge in You from the evil of my soul and from the evil and shirk of the devil, and from committing wrong against my soul or bringing such upon another Muslim.
- **Draft:** Ô Allah, Connaisseur de l’invisible et du visible, Créateur des cieux et de la terre, Seigneur et Souverain de toute chose, j’atteste qu’il n’y a de dieu que Toi. Je cherche refuge auprès de Toi contre le mal de mon âme, contre le mal de Satan et contre ses pièges, et contre le fait de commettre un mal contre moi-même ou de l’attirer sur un musulman.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-086

Hisn al-Muslim 86, read morning and evening, said 3 times.

- **Arabic:** بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلاَ فِي السّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ
- **English (OpenDua):** In the name of Allah with whose name nothing is harmed on earth nor in the heavens, and He is the All-Hearing, the All-Knowing.
- **Draft:** Au nom d’Allah, avec le nom duquel rien ne peut nuire, ni sur la terre ni dans le ciel, et Il est l’Audient, l’Omniscient.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-088

Hisn al-Muslim 88, read morning and evening, said once.

- **Arabic:** يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغيثُ أَصْلِحْ لِي شَأْنِيَ كُلَّهُ وَلاَ تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ
- **English (OpenDua):** O Ever Living, O Self-Subsisting and Supporter of all, by Your mercy I seek assistance, rectify for me all of my affairs and do not leave me to myself, even for the blink of an eye.
- **Draft:** Ô Vivant, ô Toi qui subsistes par Toi-même, c’est par Ta miséricorde que j’implore secours. Améliore toutes mes affaires et ne m’abandonne pas à moi-même, ne serait-ce que le temps d’un clin d’œil.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-090, morning wording only

Hisn al-Muslim 90, read morning only, said once.

- **Arabic:** أَصْبَحْنَا عَلَى فِطْرَةِ الْإِسْلاَمِ، وَعَلَى كَلِمَةِ الْإِخْلاَصِ، وَعَلَى دِينِ نَبِيِّنَا مُحَمَّدٍ صلى الله عليه وسلم، وَعَلَى مِلَّةِ أَبِينَا إِبْرَاهِيمَ، حَنِيفاً مُسْلِماً وَمَا كَانَ مِنَ الْمُشْرِكِينَ
- **English (OpenDua):** We have entered the morning upon the natural way of Islam, the declaration of sincere faith, the religion of our Prophet Muhammad (may Allah’s peace and blessings be upon him), and the faith of our forefather Ibraheem, who was upright, a Muslim, and not among those who associate partners with Allah.
- **Draft:** Nous voici au matin sur la nature originelle de l’islam, sur la parole de la sincérité, sur la religion de notre Prophète Muhammad ﷺ et sur la voie de notre père Abraham, qui était un monothéiste pur, soumis, et n’était pas du nombre des associateurs.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-091

Hisn al-Muslim 91, read morning and evening, said 100 times.

- **Arabic:** سُبْحَانَ اللَّهِ وَبِحَمْدِهِ
- **English (OpenDua):** How perfect Allah is and I praise Him.
- **Draft:** Gloire et louange à Allah.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-092

Hisn al-Muslim 92, read morning and evening, said 10 times.

- **Arabic:** لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ
- **English (OpenDua):** None has the right to be worshipped except Allah, alone, without partner, to Him belongs all sovereignty and praise, and He is over all things omnipotent.
- **Draft:** Il n’y a de dieu qu’Allah, seul, sans associé, à Lui la royauté et à Lui la louange, et Il est capable de toute chose.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-093

Hisn al-Muslim 93, read morning only, said 100 times.

- **Arabic:** لاَ إِلَهَ إِلاَّ اللَّهُ، وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ
- **English (OpenDua):** None has the right to be worshipped except Allah, alone, without partner, to Him belongs all sovereignty and praise, and He is over all things omnipotent.
- **Draft:** Il n’y a de dieu qu’Allah, seul, sans associé, à Lui la royauté et à Lui la louange, et Il est capable de toute chose.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-094

Hisn al-Muslim 94, read morning only, said 3 times.

- **Arabic:** سُبْحَانَ اللَّهِ وَبِحَمْدِهِ: عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ
- **English (OpenDua):** How perfect Allah is and I praise Him by the number of His creation and His pleasure, and by the weight of His throne, and the ink of His words.
- **Draft:** Gloire et louange à Allah, selon le nombre de Ses créatures, Son propre agrément, le poids de Son Trône et l’encre de Ses paroles.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-095

Hisn al-Muslim 95, read morning only, said once.

- **Arabic:** اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْماً نَافِعاً، وَرِزْقاً طَيِّباً، وَعَمَلاً مُتَقَبَّلاً
- **English (OpenDua):** O Allah, I ask You for knowledge which is beneficial and sustenance which is good, and deeds which are acceptable.
- **Draft:** Ô Allah, je Te demande une science utile, une subsistance pure et une œuvre acceptée.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-096

Hisn al-Muslim 96, read morning and evening, said 100 times a day.

- **Arabic:** أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ
- **English (OpenDua):** I seek Allah’s forgiveness and I turn to Him in repentance.
- **Draft:** Je demande pardon à Allah et je me repens à Lui.
- **Review:** [ ] correct as drafted · correction:

### fr · hisn-097

Hisn al-Muslim 97, read evening only, said 3 times.

- **Arabic:** أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ
- **English (OpenDua):** I take refuge in Allah’s perfect words from the evil He has created.
- **Draft:** Je cherche refuge auprès des paroles parfaites d’Allah contre le mal de ce qu’Il a créé.
- **Review:** [ ] correct as drafted · correction:

---

## Indonesian (id)

Bahasa Indonesia · `shared/src/commonMain/composeResources/files/adhkar/text/id.json`

Reviewer: ______________________ Date: ____________

Verdict: [ ] ships as drafted [ ] ships with the corrections below [ ] does not ship yet

**Choices to weigh**

- رب is **Tuhan** (“Ya Tuhanku”); Allah is addressed as **Engkau / -Mu**; the first person is **aku**, as in the app’s strings.
- KBBI spellings: **Mahakuasa, Mahahidup** (joined before a root word), **Maha Mendengar, Maha Mengetahui** (apart before an affixed one), gaib, setan, keridaan, Arasy, bertobat.
- hisn-084 عوراتي is **aib-aibku** (faults), not “auratku”, which in Indonesian means the body’s private parts. hisn-077 is “dan pada pagi ini kerajaan milik Allah”; many booklets write “dan kerajaan hanya milik Allah”.
- hisn-088 القيوم is **Yang Maha Berdiri Sendiri**; Kemenag’s 2:255 has “terus menerus mengurus (makhluk-Nya)”.

### id · hisn-077, morning wording

Hisn al-Muslim 77, read morning and evening, said once.

- **Arabic:** أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيرَ مَا بَعْدَهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ
- **English (OpenDua):** We have entered the morning, and all sovereignty belongs to Allah. All praise is for Allah. None has the right to be worshipped except Allah alone, without partner. To Him belong all sovereignty and praise, and He has power over all things. My Lord, I ask You for the good of this day and what follows it. I seek refuge in You from the evil of this day and what follows it. My Lord, I seek refuge in You from laziness and the evils of old age. My Lord, I seek refuge in You from punishment in the Fire and punishment in the grave.
- **Draft:** Kami telah memasuki waktu pagi, dan pada pagi ini kerajaan milik Allah. Segala puji bagi Allah. Tiada tuhan selain Allah semata, tiada sekutu bagi-Nya. Bagi-Nya kerajaan dan bagi-Nya segala puji, dan Dia Mahakuasa atas segala sesuatu. Ya Tuhanku, aku memohon kepada-Mu kebaikan yang ada pada hari ini dan kebaikan sesudahnya. Aku berlindung kepada-Mu dari keburukan yang ada pada hari ini dan keburukan sesudahnya. Ya Tuhanku, aku berlindung kepada-Mu dari kemalasan dan buruknya masa tua. Ya Tuhanku, aku berlindung kepada-Mu dari azab di neraka dan azab di kubur.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-077, evening wording

Hisn al-Muslim 77, read morning and evening, said once.

- **Arabic:** أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيرَ مَا بَعْدَهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ
- **English (OpenDua):** We have entered the evening, and all sovereignty belongs to Allah. All praise is for Allah. None has the right to be worshipped except Allah alone, without partner. To Him belong all sovereignty and praise, and He has power over all things. My Lord, I ask You for the good of this night and what follows it. I seek refuge in You from the evil of this night and what follows it. My Lord, I seek refuge in You from laziness and the evils of old age. My Lord, I seek refuge in You from punishment in the Fire and punishment in the grave.
- **Draft:** Kami telah memasuki waktu petang, dan pada petang ini kerajaan milik Allah. Segala puji bagi Allah. Tiada tuhan selain Allah semata, tiada sekutu bagi-Nya. Bagi-Nya kerajaan dan bagi-Nya segala puji, dan Dia Mahakuasa atas segala sesuatu. Ya Tuhanku, aku memohon kepada-Mu kebaikan yang ada pada malam ini dan kebaikan sesudahnya. Aku berlindung kepada-Mu dari keburukan yang ada pada malam ini dan keburukan sesudahnya. Ya Tuhanku, aku berlindung kepada-Mu dari kemalasan dan buruknya masa tua. Ya Tuhanku, aku berlindung kepada-Mu dari azab di neraka dan azab di kubur.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-078, morning wording

Hisn al-Muslim 78, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ
- **English (OpenDua):** O Allah, by You we enter the morning, and by You we enter the evening. By You we live and by You we die, and to You is the resurrection.
- **Draft:** Ya Allah, dengan-Mu kami memasuki waktu pagi dan dengan-Mu kami memasuki waktu petang. Dengan-Mu kami hidup dan dengan-Mu kami mati, dan kepada-Mu kebangkitan.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-078, evening wording

Hisn al-Muslim 78, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ
- **English (OpenDua):** O Allah, by You we enter the evening, and by You we enter the morning. By You we live and by You we die, and to You is our return.
- **Draft:** Ya Allah, dengan-Mu kami memasuki waktu petang dan dengan-Mu kami memasuki waktu pagi. Dengan-Mu kami hidup dan dengan-Mu kami mati, dan kepada-Mu tempat kembali.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-079

Hisn al-Muslim 79, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلاَّ أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لاَ يَغْفِرُ الذُّنوبَ إِلاَّ أَنْتَ
- **English (OpenDua):** O Allah, You are my Lord, none has the right to be worshipped except You, You created me and I am Your servant and I abide to Your covenant and promise as best I can, I take refuge in You from the evil of which I have committed. I acknowledge Your favour upon me and I acknowledge my sin, so forgive me, for verily none can forgive sin except You.
- **Draft:** Ya Allah, Engkau Tuhanku, tiada tuhan selain Engkau. Engkau telah menciptakanku dan aku adalah hamba-Mu. Aku menetapi perjanjian-Mu dan janji-Mu semampuku. Aku berlindung kepada-Mu dari keburukan yang telah kuperbuat. Aku mengakui di hadapan-Mu nikmat-Mu kepadaku, dan aku mengakui dosaku, maka ampunilah aku, karena tidak ada yang mengampuni dosa-dosa selain Engkau.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-084

Hisn al-Muslim 84, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالآخِرَةِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ: فِي دِينِي وَدُنْيَايَ وَأَهْلِي، وَمَالِي، اللَّهُمَّ اسْتُرْ عَوْرَاتِي، وَآمِنْ رَوْعَاتِي، اللَّهُمَّ احْفَظْنِي مِنْ بَينِ يَدَيَّ، وَمِنْ خَلْفِي، وَعَنْ يَمِينِي، وَعَنْ شِمَالِي، وَمِنْ فَوْقِي، وَأَعُوذُ بِعَظَمَتِكَ أَنْ أُغْتَالَ مِنْ تَحْتِي
- **English (OpenDua):** O Allah, I ask You for pardon and well-being in this life and the next. O Allah, I ask You for pardon and well-being in my religious and worldly affairs, and my family and my wealth. O Allah, veil my weaknesses and set at ease my dismay. O Allah, preserve me from the front and from behind and on my right and on my left and from above, and I take refuge with You lest I be swallowed up by the earth.
- **Draft:** Ya Allah, aku memohon kepada-Mu ampunan dan keselamatan di dunia dan akhirat. Ya Allah, aku memohon kepada-Mu ampunan dan keselamatan dalam agamaku, duniaku, keluargaku, dan hartaku. Ya Allah, tutupilah aib-aibku dan tenteramkanlah rasa takutku. Ya Allah, jagalah aku dari depanku, dari belakangku, dari kananku, dari kiriku, dan dari atasku, dan aku berlindung dengan keagungan-Mu agar aku tidak disergap dari bawahku.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-085

Hisn al-Muslim 85, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ عَالِمَ الغَيْبِ وَالشَّهَادَةِ فَاطِرَ السَّمَوَاتِ وَالْأَرْضِ، رَبَّ كُلِّ شَيْءٍ وَمَلِيكَهُ، أَشْهَدُ أَنْ لاَ إِلَهَ إِلاَّ أَنْتَ، أَعُوذُ بِكَ مِنْ شَرِّ نَفْسِي، وَمِنْ شَرِّ الشَّيْطانِ وَشَرَكِهِ، وَأَنْ أَقْتَرِفَ عَلَى نَفْسِي سُوءاً، أَوْ أَجُرَّهُ إِلَى مُسْلِمٍ
- **English (OpenDua):** O Allah, Knower of the unseen and the seen, Creator of the heavens and the Earth, Lord and Sovereign of all things, I bear witness that none has the right to be worshipped except You. I take refuge in You from the evil of my soul and from the evil and shirk of the devil, and from committing wrong against my soul or bringing such upon another Muslim.
- **Draft:** Ya Allah, Yang Mengetahui yang gaib dan yang nyata, Pencipta langit dan bumi, Tuhan dan Penguasa segala sesuatu, aku bersaksi bahwa tiada tuhan selain Engkau. Aku berlindung kepada-Mu dari keburukan diriku, dari keburukan setan dan perangkapnya, serta dari berbuat keburukan terhadap diriku atau menimpakannya kepada seorang muslim.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-086

Hisn al-Muslim 86, read morning and evening, said 3 times.

- **Arabic:** بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلاَ فِي السّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ
- **English (OpenDua):** In the name of Allah with whose name nothing is harmed on earth nor in the heavens, and He is the All-Hearing, the All-Knowing.
- **Draft:** Dengan nama Allah, yang bersama nama-Nya tidak ada sesuatu pun di bumi maupun di langit yang dapat membahayakan, dan Dialah Yang Maha Mendengar lagi Maha Mengetahui.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-088

Hisn al-Muslim 88, read morning and evening, said once.

- **Arabic:** يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغيثُ أَصْلِحْ لِي شَأْنِيَ كُلَّهُ وَلاَ تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ
- **English (OpenDua):** O Ever Living, O Self-Subsisting and Supporter of all, by Your mercy I seek assistance, rectify for me all of my affairs and do not leave me to myself, even for the blink of an eye.
- **Draft:** Wahai Yang Mahahidup, wahai Yang Maha Berdiri Sendiri, dengan rahmat-Mu aku memohon pertolongan. Perbaikilah seluruh urusanku dan janganlah Engkau serahkan aku kepada diriku sendiri walau sekejap mata.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-090, morning wording only

Hisn al-Muslim 90, read morning only, said once.

- **Arabic:** أَصْبَحْنَا عَلَى فِطْرَةِ الْإِسْلاَمِ، وَعَلَى كَلِمَةِ الْإِخْلاَصِ، وَعَلَى دِينِ نَبِيِّنَا مُحَمَّدٍ صلى الله عليه وسلم، وَعَلَى مِلَّةِ أَبِينَا إِبْرَاهِيمَ، حَنِيفاً مُسْلِماً وَمَا كَانَ مِنَ الْمُشْرِكِينَ
- **English (OpenDua):** We have entered the morning upon the natural way of Islam, the declaration of sincere faith, the religion of our Prophet Muhammad (may Allah’s peace and blessings be upon him), and the faith of our forefather Ibraheem, who was upright, a Muslim, and not among those who associate partners with Allah.
- **Draft:** Kami telah memasuki waktu pagi di atas fitrah Islam, di atas kalimat ikhlas, di atas agama Nabi kami Muhammad ﷺ, dan di atas millah bapak kami Ibrahim, yang lurus lagi berserah diri, dan dia bukan termasuk orang-orang musyrik.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-091

Hisn al-Muslim 91, read morning and evening, said 100 times.

- **Arabic:** سُبْحَانَ اللَّهِ وَبِحَمْدِهِ
- **English (OpenDua):** How perfect Allah is and I praise Him.
- **Draft:** Mahasuci Allah dan segala puji bagi-Nya.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-092

Hisn al-Muslim 92, read morning and evening, said 10 times.

- **Arabic:** لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ
- **English (OpenDua):** None has the right to be worshipped except Allah, alone, without partner, to Him belongs all sovereignty and praise, and He is over all things omnipotent.
- **Draft:** Tiada tuhan selain Allah semata, tiada sekutu bagi-Nya, bagi-Nya kerajaan dan bagi-Nya segala puji, dan Dia Mahakuasa atas segala sesuatu.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-093

Hisn al-Muslim 93, read morning only, said 100 times.

- **Arabic:** لاَ إِلَهَ إِلاَّ اللَّهُ، وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ
- **English (OpenDua):** None has the right to be worshipped except Allah, alone, without partner, to Him belongs all sovereignty and praise, and He is over all things omnipotent.
- **Draft:** Tiada tuhan selain Allah semata, tiada sekutu bagi-Nya, bagi-Nya kerajaan dan bagi-Nya segala puji, dan Dia Mahakuasa atas segala sesuatu.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-094

Hisn al-Muslim 94, read morning only, said 3 times.

- **Arabic:** سُبْحَانَ اللَّهِ وَبِحَمْدِهِ: عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ
- **English (OpenDua):** How perfect Allah is and I praise Him by the number of His creation and His pleasure, and by the weight of His throne, and the ink of His words.
- **Draft:** Mahasuci Allah dan segala puji bagi-Nya, sebanyak bilangan makhluk-Nya, sesuai keridaan-Nya, seberat Arasy-Nya, dan sebanyak tinta kalimat-kalimat-Nya.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-095

Hisn al-Muslim 95, read morning only, said once.

- **Arabic:** اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْماً نَافِعاً، وَرِزْقاً طَيِّباً، وَعَمَلاً مُتَقَبَّلاً
- **English (OpenDua):** O Allah, I ask You for knowledge which is beneficial and sustenance which is good, and deeds which are acceptable.
- **Draft:** Ya Allah, aku memohon kepada-Mu ilmu yang bermanfaat, rezeki yang baik, dan amal yang diterima.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-096

Hisn al-Muslim 96, read morning and evening, said 100 times a day.

- **Arabic:** أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ
- **English (OpenDua):** I seek Allah’s forgiveness and I turn to Him in repentance.
- **Draft:** Aku memohon ampun kepada Allah dan bertobat kepada-Nya.
- **Review:** [ ] correct as drafted · correction:

### id · hisn-097

Hisn al-Muslim 97, read evening only, said 3 times.

- **Arabic:** أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ
- **English (OpenDua):** I take refuge in Allah’s perfect words from the evil He has created.
- **Draft:** Aku berlindung dengan kalimat-kalimat Allah yang sempurna dari keburukan apa yang Dia ciptakan.
- **Review:** [ ] correct as drafted · correction:

---

## Turkish (tr)

Türkçe · `shared/src/commonMain/composeResources/files/adhkar/text/tr.json`

Reviewer: ______________________ Date: ____________

Verdict: [ ] ships as drafted [ ] ships with the corrections below [ ] does not ship yet

**Choices to weigh**

- رب is **Rab** (“Rabbim!”); Allah is addressed as **Sen** with capitals, **O’nun / O’na** with the apostrophe as Diyanet writes them; vocatives end in “!” as Turkish du‘a books print them.
- hisn-090: **Muhammed’in ﷺ dini** (the suffix on the name, the sign after it) and **babamız İbrahim’in milleti** (the classical term); “yolu” would be the modern word for millet.
- النار is **cehennem**; السميع العليم is “her şeyi işitendir, her şeyi bilendir” (Diyanet often has “hakkıyla işiten, hakkıyla bilen”).
- hisn-086 is two sentences: Turkish puts the relative clause before “Allah’ın ismiyle”.

### tr · hisn-077, morning wording

Hisn al-Muslim 77, read morning and evening, said once.

- **Arabic:** أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيرَ مَا بَعْدَهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ
- **English (OpenDua):** We have entered the morning, and all sovereignty belongs to Allah. All praise is for Allah. None has the right to be worshipped except Allah alone, without partner. To Him belong all sovereignty and praise, and He has power over all things. My Lord, I ask You for the good of this day and what follows it. I seek refuge in You from the evil of this day and what follows it. My Lord, I seek refuge in You from laziness and the evils of old age. My Lord, I seek refuge in You from punishment in the Fire and punishment in the grave.
- **Draft:** Sabaha erdik, mülk de Allah’a ait olarak sabaha erdi. Hamd Allah’a mahsustur. Allah’tan başka ilah yoktur, O tektir, ortağı yoktur. Mülk O’nundur, hamd O’na mahsustur ve O her şeye kadirdir. Rabbim! Bu günde olanın hayrını ve ondan sonra gelenin hayrını Senden isterim. Bu günde olanın şerrinden ve ondan sonra gelenin şerrinden Sana sığınırım. Rabbim! Tembellikten ve yaşlılığın kötülüğünden Sana sığınırım. Rabbim! Cehennemdeki azaptan ve kabirdeki azaptan Sana sığınırım.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-077, evening wording

Hisn al-Muslim 77, read morning and evening, said once.

- **Arabic:** أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيرَ مَا بَعْدَهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ
- **English (OpenDua):** We have entered the evening, and all sovereignty belongs to Allah. All praise is for Allah. None has the right to be worshipped except Allah alone, without partner. To Him belong all sovereignty and praise, and He has power over all things. My Lord, I ask You for the good of this night and what follows it. I seek refuge in You from the evil of this night and what follows it. My Lord, I seek refuge in You from laziness and the evils of old age. My Lord, I seek refuge in You from punishment in the Fire and punishment in the grave.
- **Draft:** Akşama erdik, mülk de Allah’a ait olarak akşama erdi. Hamd Allah’a mahsustur. Allah’tan başka ilah yoktur, O tektir, ortağı yoktur. Mülk O’nundur, hamd O’na mahsustur ve O her şeye kadirdir. Rabbim! Bu gecede olanın hayrını ve ondan sonra gelenin hayrını Senden isterim. Bu gecede olanın şerrinden ve ondan sonra gelenin şerrinden Sana sığınırım. Rabbim! Tembellikten ve yaşlılığın kötülüğünden Sana sığınırım. Rabbim! Cehennemdeki azaptan ve kabirdeki azaptan Sana sığınırım.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-078, morning wording

Hisn al-Muslim 78, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ
- **English (OpenDua):** O Allah, by You we enter the morning, and by You we enter the evening. By You we live and by You we die, and to You is the resurrection.
- **Draft:** Allah’ım! Seninle sabaha erdik ve Seninle akşama erdik. Seninle yaşar, Seninle ölürüz ve diriliş Sanadır.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-078, evening wording

Hisn al-Muslim 78, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ
- **English (OpenDua):** O Allah, by You we enter the evening, and by You we enter the morning. By You we live and by You we die, and to You is our return.
- **Draft:** Allah’ım! Seninle akşama erdik ve Seninle sabaha erdik. Seninle yaşar, Seninle ölürüz ve dönüş Sanadır.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-079

Hisn al-Muslim 79, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلاَّ أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لاَ يَغْفِرُ الذُّنوبَ إِلاَّ أَنْتَ
- **English (OpenDua):** O Allah, You are my Lord, none has the right to be worshipped except You, You created me and I am Your servant and I abide to Your covenant and promise as best I can, I take refuge in You from the evil of which I have committed. I acknowledge Your favour upon me and I acknowledge my sin, so forgive me, for verily none can forgive sin except You.
- **Draft:** Allah’ım! Sen benim Rabbimsin, Senden başka ilah yoktur. Beni yarattın, ben de Senin kulunum. Gücüm yettiğince Senin ahdin ve vaadin üzereyim. Yaptıklarımın şerrinden Sana sığınırım. Bana verdiğin nimeti Sana itiraf ederim, günahımı da itiraf ederim, beni bağışla, çünkü günahları ancak Sen bağışlarsın.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-084

Hisn al-Muslim 84, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالآخِرَةِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ: فِي دِينِي وَدُنْيَايَ وَأَهْلِي، وَمَالِي، اللَّهُمَّ اسْتُرْ عَوْرَاتِي، وَآمِنْ رَوْعَاتِي، اللَّهُمَّ احْفَظْنِي مِنْ بَينِ يَدَيَّ، وَمِنْ خَلْفِي، وَعَنْ يَمِينِي، وَعَنْ شِمَالِي، وَمِنْ فَوْقِي، وَأَعُوذُ بِعَظَمَتِكَ أَنْ أُغْتَالَ مِنْ تَحْتِي
- **English (OpenDua):** O Allah, I ask You for pardon and well-being in this life and the next. O Allah, I ask You for pardon and well-being in my religious and worldly affairs, and my family and my wealth. O Allah, veil my weaknesses and set at ease my dismay. O Allah, preserve me from the front and from behind and on my right and on my left and from above, and I take refuge with You lest I be swallowed up by the earth.
- **Draft:** Allah’ım! Dünyada ve ahirette Senden af ve afiyet isterim. Allah’ım! Dinimde, dünyamda, ailemde ve malımda Senden af ve afiyet isterim. Allah’ım! Ayıplarımı ört, korkularımdan beni emin kıl. Allah’ım! Beni önümden, arkamdan, sağımdan, solumdan ve üstümden koru, altımdan ansızın helak edilmekten de azametine sığınırım.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-085

Hisn al-Muslim 85, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ عَالِمَ الغَيْبِ وَالشَّهَادَةِ فَاطِرَ السَّمَوَاتِ وَالْأَرْضِ، رَبَّ كُلِّ شَيْءٍ وَمَلِيكَهُ، أَشْهَدُ أَنْ لاَ إِلَهَ إِلاَّ أَنْتَ، أَعُوذُ بِكَ مِنْ شَرِّ نَفْسِي، وَمِنْ شَرِّ الشَّيْطانِ وَشَرَكِهِ، وَأَنْ أَقْتَرِفَ عَلَى نَفْسِي سُوءاً، أَوْ أَجُرَّهُ إِلَى مُسْلِمٍ
- **English (OpenDua):** O Allah, Knower of the unseen and the seen, Creator of the heavens and the Earth, Lord and Sovereign of all things, I bear witness that none has the right to be worshipped except You. I take refuge in You from the evil of my soul and from the evil and shirk of the devil, and from committing wrong against my soul or bringing such upon another Muslim.
- **Draft:** Ey görülmeyeni ve görüleni bilen, gökleri ve yeri yaratan, her şeyin Rabbi ve Maliki olan Allah’ım! Senden başka ilah olmadığına şahitlik ederim. Nefsimin şerrinden, şeytanın şerrinden ve tuzağından, kendime bir kötülük yapmaktan ya da onu bir Müslümana bulaştırmaktan Sana sığınırım.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-086

Hisn al-Muslim 86, read morning and evening, said 3 times.

- **Arabic:** بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلاَ فِي السّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ
- **English (OpenDua):** In the name of Allah with whose name nothing is harmed on earth nor in the heavens, and He is the All-Hearing, the All-Knowing.
- **Draft:** İsmiyle birlikte yerde ve gökte hiçbir şeyin zarar veremediği Allah’ın ismiyle. O, her şeyi işitendir, her şeyi bilendir.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-088

Hisn al-Muslim 88, read morning and evening, said once.

- **Arabic:** يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغيثُ أَصْلِحْ لِي شَأْنِيَ كُلَّهُ وَلاَ تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ
- **English (OpenDua):** O Ever Living, O Self-Subsisting and Supporter of all, by Your mercy I seek assistance, rectify for me all of my affairs and do not leave me to myself, even for the blink of an eye.
- **Draft:** Ey daima diri olan, ey her şeyi ayakta tutan! Rahmetinle yardım dilerim. Bütün işlerimi düzelt ve beni göz açıp kapayıncaya kadar bile nefsime bırakma.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-090, morning wording only

Hisn al-Muslim 90, read morning only, said once.

- **Arabic:** أَصْبَحْنَا عَلَى فِطْرَةِ الْإِسْلاَمِ، وَعَلَى كَلِمَةِ الْإِخْلاَصِ، وَعَلَى دِينِ نَبِيِّنَا مُحَمَّدٍ صلى الله عليه وسلم، وَعَلَى مِلَّةِ أَبِينَا إِبْرَاهِيمَ، حَنِيفاً مُسْلِماً وَمَا كَانَ مِنَ الْمُشْرِكِينَ
- **English (OpenDua):** We have entered the morning upon the natural way of Islam, the declaration of sincere faith, the religion of our Prophet Muhammad (may Allah’s peace and blessings be upon him), and the faith of our forefather Ibraheem, who was upright, a Muslim, and not among those who associate partners with Allah.
- **Draft:** İslam fıtratı, ihlas kelimesi, Peygamberimiz Muhammed’in ﷺ dini ve hanif bir Müslüman olan, müşriklerden olmayan babamız İbrahim’in milleti üzere sabaha erdik.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-091

Hisn al-Muslim 91, read morning and evening, said 100 times.

- **Arabic:** سُبْحَانَ اللَّهِ وَبِحَمْدِهِ
- **English (OpenDua):** How perfect Allah is and I praise Him.
- **Draft:** Allah’ı hamd ile tenzih ederim.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-092

Hisn al-Muslim 92, read morning and evening, said 10 times.

- **Arabic:** لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ
- **English (OpenDua):** None has the right to be worshipped except Allah, alone, without partner, to Him belongs all sovereignty and praise, and He is over all things omnipotent.
- **Draft:** Allah’tan başka ilah yoktur, O tektir, ortağı yoktur, mülk O’nundur, hamd O’na mahsustur ve O her şeye kadirdir.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-093

Hisn al-Muslim 93, read morning only, said 100 times.

- **Arabic:** لاَ إِلَهَ إِلاَّ اللَّهُ، وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ
- **English (OpenDua):** None has the right to be worshipped except Allah, alone, without partner, to Him belongs all sovereignty and praise, and He is over all things omnipotent.
- **Draft:** Allah’tan başka ilah yoktur, O tektir, ortağı yoktur, mülk O’nundur, hamd O’na mahsustur ve O her şeye kadirdir.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-094

Hisn al-Muslim 94, read morning only, said 3 times.

- **Arabic:** سُبْحَانَ اللَّهِ وَبِحَمْدِهِ: عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ
- **English (OpenDua):** How perfect Allah is and I praise Him by the number of His creation and His pleasure, and by the weight of His throne, and the ink of His words.
- **Draft:** Allah’ı, yarattıklarının sayısınca, kendi rızası kadar, Arş’ının ağırlığınca ve kelimelerinin mürekkebi miktarınca hamd ile tenzih ederim.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-095

Hisn al-Muslim 95, read morning only, said once.

- **Arabic:** اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْماً نَافِعاً، وَرِزْقاً طَيِّباً، وَعَمَلاً مُتَقَبَّلاً
- **English (OpenDua):** O Allah, I ask You for knowledge which is beneficial and sustenance which is good, and deeds which are acceptable.
- **Draft:** Allah’ım! Senden faydalı ilim, temiz rızık ve kabul edilmiş amel isterim.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-096

Hisn al-Muslim 96, read morning and evening, said 100 times a day.

- **Arabic:** أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ
- **English (OpenDua):** I seek Allah’s forgiveness and I turn to Him in repentance.
- **Draft:** Allah’tan bağışlanma dilerim ve O’na tövbe ederim.
- **Review:** [ ] correct as drafted · correction:

### tr · hisn-097

Hisn al-Muslim 97, read evening only, said 3 times.

- **Arabic:** أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ
- **English (OpenDua):** I take refuge in Allah’s perfect words from the evil He has created.
- **Draft:** Yarattığı şeylerin şerrinden Allah’ın eksiksiz kelimelerine sığınırım.
- **Review:** [ ] correct as drafted · correction:

---

## Urdu (ur)

اردو · `shared/src/commonMain/composeResources/files/adhkar/text/ur.json`

Reviewer: ______________________ Date: ____________

Verdict: [ ] ships as drafted [ ] ships with the corrections below [ ] does not ship yet

**Choices to weigh**

- رب is **رب** (“اے میرے رب!”); Allah is addressed as **تو / تیرا** as classical Urdu du‘a does; verbs are masculine, as booklets print them.
- hisn-078 **تیرے ساتھ** is the literal “by/with You”; تیری ہی توفیق سے or تیرے ہی حکم سے would be glosses.
- Orthography: ی ے ک ہ ھ throughout (no Arabic ي ك ه), ۔ and ، for punctuation, کلمۂ with ۂ; a script checked every letter.
- hisn-091 and 094 open with the app’s own “اللہ پاک ہے اور اسی کی تعریف ہے”.

### ur · hisn-077, morning wording

Hisn al-Muslim 77, read morning and evening, said once.

- **Arabic:** أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيرَ مَا بَعْدَهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ
- **English (OpenDua):** We have entered the morning, and all sovereignty belongs to Allah. All praise is for Allah. None has the right to be worshipped except Allah alone, without partner. To Him belong all sovereignty and praise, and He has power over all things. My Lord, I ask You for the good of this day and what follows it. I seek refuge in You from the evil of this day and what follows it. My Lord, I seek refuge in You from laziness and the evils of old age. My Lord, I seek refuge in You from punishment in the Fire and punishment in the grave.
- **Draft:** ہم نے صبح کی اور اللہ کی بادشاہی نے صبح کی۔ سب تعریف اللہ ہی کے لیے ہے۔ اللہ کے سوا کوئی معبود نہیں، وہ اکیلا ہے، اس کا کوئی شریک نہیں۔ اسی کی بادشاہی ہے اور اسی کی تعریف ہے، اور وہ ہر چیز پر قادر ہے۔ اے میرے رب! میں تجھ سے اس دن کی بھلائی اور اس کے بعد کی بھلائی کا سوال کرتا ہوں۔ میں اس دن کے شر اور اس کے بعد کے شر سے تیری پناہ چاہتا ہوں۔ اے میرے رب! میں سستی اور بڑھاپے کی برائی سے تیری پناہ چاہتا ہوں۔ اے میرے رب! میں جہنم کے عذاب اور قبر کے عذاب سے تیری پناہ چاہتا ہوں۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-077, evening wording

Hisn al-Muslim 77, read morning and evening, said once.

- **Arabic:** أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيرَ مَا بَعْدَهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ
- **English (OpenDua):** We have entered the evening, and all sovereignty belongs to Allah. All praise is for Allah. None has the right to be worshipped except Allah alone, without partner. To Him belong all sovereignty and praise, and He has power over all things. My Lord, I ask You for the good of this night and what follows it. I seek refuge in You from the evil of this night and what follows it. My Lord, I seek refuge in You from laziness and the evils of old age. My Lord, I seek refuge in You from punishment in the Fire and punishment in the grave.
- **Draft:** ہم نے شام کی اور اللہ کی بادشاہی نے شام کی۔ سب تعریف اللہ ہی کے لیے ہے۔ اللہ کے سوا کوئی معبود نہیں، وہ اکیلا ہے، اس کا کوئی شریک نہیں۔ اسی کی بادشاہی ہے اور اسی کی تعریف ہے، اور وہ ہر چیز پر قادر ہے۔ اے میرے رب! میں تجھ سے اس رات کی بھلائی اور اس کے بعد کی بھلائی کا سوال کرتا ہوں۔ میں اس رات کے شر اور اس کے بعد کے شر سے تیری پناہ چاہتا ہوں۔ اے میرے رب! میں سستی اور بڑھاپے کی برائی سے تیری پناہ چاہتا ہوں۔ اے میرے رب! میں جہنم کے عذاب اور قبر کے عذاب سے تیری پناہ چاہتا ہوں۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-078, morning wording

Hisn al-Muslim 78, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ
- **English (OpenDua):** O Allah, by You we enter the morning, and by You we enter the evening. By You we live and by You we die, and to You is the resurrection.
- **Draft:** اے اللہ! تیرے ساتھ ہم نے صبح کی اور تیرے ساتھ ہم نے شام کی۔ تیرے ساتھ ہم جیتے ہیں اور تیرے ساتھ ہم مرتے ہیں، اور تیری ہی طرف اٹھ کر جانا ہے۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-078, evening wording

Hisn al-Muslim 78, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ
- **English (OpenDua):** O Allah, by You we enter the evening, and by You we enter the morning. By You we live and by You we die, and to You is our return.
- **Draft:** اے اللہ! تیرے ساتھ ہم نے شام کی اور تیرے ساتھ ہم نے صبح کی۔ تیرے ساتھ ہم جیتے ہیں اور تیرے ساتھ ہم مرتے ہیں، اور تیری ہی طرف لوٹنا ہے۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-079

Hisn al-Muslim 79, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلاَّ أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لاَ يَغْفِرُ الذُّنوبَ إِلاَّ أَنْتَ
- **English (OpenDua):** O Allah, You are my Lord, none has the right to be worshipped except You, You created me and I am Your servant and I abide to Your covenant and promise as best I can, I take refuge in You from the evil of which I have committed. I acknowledge Your favour upon me and I acknowledge my sin, so forgive me, for verily none can forgive sin except You.
- **Draft:** اے اللہ! تو میرا رب ہے، تیرے سوا کوئی معبود نہیں۔ تو نے مجھے پیدا کیا اور میں تیرا بندہ ہوں۔ میں اپنی استطاعت بھر تیرے عہد اور تیرے وعدے پر قائم ہوں۔ میں نے جو کچھ کیا اس کے شر سے تیری پناہ چاہتا ہوں۔ میں تیرے سامنے اپنے اوپر تیری نعمت کا اعتراف کرتا ہوں اور اپنے گناہ کا اعتراف کرتا ہوں، پس مجھے بخش دے، کیونکہ تیرے سوا گناہوں کو کوئی نہیں بخشتا۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-084

Hisn al-Muslim 84, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالآخِرَةِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ: فِي دِينِي وَدُنْيَايَ وَأَهْلِي، وَمَالِي، اللَّهُمَّ اسْتُرْ عَوْرَاتِي، وَآمِنْ رَوْعَاتِي، اللَّهُمَّ احْفَظْنِي مِنْ بَينِ يَدَيَّ، وَمِنْ خَلْفِي، وَعَنْ يَمِينِي، وَعَنْ شِمَالِي، وَمِنْ فَوْقِي، وَأَعُوذُ بِعَظَمَتِكَ أَنْ أُغْتَالَ مِنْ تَحْتِي
- **English (OpenDua):** O Allah, I ask You for pardon and well-being in this life and the next. O Allah, I ask You for pardon and well-being in my religious and worldly affairs, and my family and my wealth. O Allah, veil my weaknesses and set at ease my dismay. O Allah, preserve me from the front and from behind and on my right and on my left and from above, and I take refuge with You lest I be swallowed up by the earth.
- **Draft:** اے اللہ! میں تجھ سے دنیا اور آخرت میں معافی اور عافیت کا سوال کرتا ہوں۔ اے اللہ! میں تجھ سے اپنے دین، اپنی دنیا، اپنے گھر والوں اور اپنے مال میں معافی اور عافیت کا سوال کرتا ہوں۔ اے اللہ! میرے عیبوں پر پردہ ڈال دے اور میری گھبراہٹوں کو امن دے۔ اے اللہ! میرے آگے سے، میرے پیچھے سے، میری دائیں جانب سے، میری بائیں جانب سے اور میرے اوپر سے میری حفاظت فرما، اور میں تیری عظمت کی پناہ چاہتا ہوں اس سے کہ اپنے نیچے سے اچانک ہلاک کیا جاؤں۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-085

Hisn al-Muslim 85, read morning and evening, said once.

- **Arabic:** اللَّهُمَّ عَالِمَ الغَيْبِ وَالشَّهَادَةِ فَاطِرَ السَّمَوَاتِ وَالْأَرْضِ، رَبَّ كُلِّ شَيْءٍ وَمَلِيكَهُ، أَشْهَدُ أَنْ لاَ إِلَهَ إِلاَّ أَنْتَ، أَعُوذُ بِكَ مِنْ شَرِّ نَفْسِي، وَمِنْ شَرِّ الشَّيْطانِ وَشَرَكِهِ، وَأَنْ أَقْتَرِفَ عَلَى نَفْسِي سُوءاً، أَوْ أَجُرَّهُ إِلَى مُسْلِمٍ
- **English (OpenDua):** O Allah, Knower of the unseen and the seen, Creator of the heavens and the Earth, Lord and Sovereign of all things, I bear witness that none has the right to be worshipped except You. I take refuge in You from the evil of my soul and from the evil and shirk of the devil, and from committing wrong against my soul or bringing such upon another Muslim.
- **Draft:** اے اللہ! پوشیدہ اور ظاہر کو جاننے والے، آسمانوں اور زمین کو پیدا کرنے والے، ہر چیز کے رب اور اس کے مالک! میں گواہی دیتا ہوں کہ تیرے سوا کوئی معبود نہیں۔ میں اپنے نفس کے شر سے، شیطان کے شر اور اس کے جال سے، اور اس سے کہ اپنی جان پر کوئی برائی کر بیٹھوں یا اسے کسی مسلمان کی طرف کھینچ لاؤں، تیری پناہ چاہتا ہوں۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-086

Hisn al-Muslim 86, read morning and evening, said 3 times.

- **Arabic:** بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلاَ فِي السّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ
- **English (OpenDua):** In the name of Allah with whose name nothing is harmed on earth nor in the heavens, and He is the All-Hearing, the All-Knowing.
- **Draft:** اللہ کے نام سے جس کے نام کے ساتھ زمین میں اور آسمان میں کوئی چیز نقصان نہیں پہنچا سکتی، اور وہ خوب سننے والا، خوب جاننے والا ہے۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-088

Hisn al-Muslim 88, read morning and evening, said once.

- **Arabic:** يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغيثُ أَصْلِحْ لِي شَأْنِيَ كُلَّهُ وَلاَ تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ
- **English (OpenDua):** O Ever Living, O Self-Subsisting and Supporter of all, by Your mercy I seek assistance, rectify for me all of my affairs and do not leave me to myself, even for the blink of an eye.
- **Draft:** اے ہمیشہ زندہ رہنے والے! اے سب کو تھامنے والے! میں تیری ہی رحمت کے وسیلے سے فریاد کرتا ہوں۔ میرے سب کام درست کر دے اور پلک جھپکنے کے برابر بھی مجھے میرے نفس کے سپرد نہ کر۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-090, morning wording only

Hisn al-Muslim 90, read morning only, said once.

- **Arabic:** أَصْبَحْنَا عَلَى فِطْرَةِ الْإِسْلاَمِ، وَعَلَى كَلِمَةِ الْإِخْلاَصِ، وَعَلَى دِينِ نَبِيِّنَا مُحَمَّدٍ صلى الله عليه وسلم، وَعَلَى مِلَّةِ أَبِينَا إِبْرَاهِيمَ، حَنِيفاً مُسْلِماً وَمَا كَانَ مِنَ الْمُشْرِكِينَ
- **English (OpenDua):** We have entered the morning upon the natural way of Islam, the declaration of sincere faith, the religion of our Prophet Muhammad (may Allah’s peace and blessings be upon him), and the faith of our forefather Ibraheem, who was upright, a Muslim, and not among those who associate partners with Allah.
- **Draft:** ہم نے اسلام کی فطرت پر، کلمۂ اخلاص پر، اپنے نبی محمد ﷺ کے دین پر، اور اپنے باپ ابراہیم کی ملت پر صبح کی، جو یکسو مسلمان تھے اور مشرکوں میں سے نہ تھے۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-091

Hisn al-Muslim 91, read morning and evening, said 100 times.

- **Arabic:** سُبْحَانَ اللَّهِ وَبِحَمْدِهِ
- **English (OpenDua):** How perfect Allah is and I praise Him.
- **Draft:** اللہ پاک ہے اور اسی کی تعریف ہے۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-092

Hisn al-Muslim 92, read morning and evening, said 10 times.

- **Arabic:** لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ
- **English (OpenDua):** None has the right to be worshipped except Allah, alone, without partner, to Him belongs all sovereignty and praise, and He is over all things omnipotent.
- **Draft:** اللہ کے سوا کوئی معبود نہیں، وہ اکیلا ہے، اس کا کوئی شریک نہیں، اسی کی بادشاہی ہے اور اسی کی تعریف ہے، اور وہ ہر چیز پر قادر ہے۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-093

Hisn al-Muslim 93, read morning only, said 100 times.

- **Arabic:** لاَ إِلَهَ إِلاَّ اللَّهُ، وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ
- **English (OpenDua):** None has the right to be worshipped except Allah, alone, without partner, to Him belongs all sovereignty and praise, and He is over all things omnipotent.
- **Draft:** اللہ کے سوا کوئی معبود نہیں، وہ اکیلا ہے، اس کا کوئی شریک نہیں، اسی کی بادشاہی ہے اور اسی کی تعریف ہے، اور وہ ہر چیز پر قادر ہے۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-094

Hisn al-Muslim 94, read morning only, said 3 times.

- **Arabic:** سُبْحَانَ اللَّهِ وَبِحَمْدِهِ: عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ
- **English (OpenDua):** How perfect Allah is and I praise Him by the number of His creation and His pleasure, and by the weight of His throne, and the ink of His words.
- **Draft:** اللہ پاک ہے اور اسی کی تعریف ہے، اس کی مخلوق کی تعداد کے برابر، اس کی اپنی رضا کے برابر، اس کے عرش کے وزن کے برابر اور اس کے کلمات کی سیاہی کے برابر۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-095

Hisn al-Muslim 95, read morning only, said once.

- **Arabic:** اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْماً نَافِعاً، وَرِزْقاً طَيِّباً، وَعَمَلاً مُتَقَبَّلاً
- **English (OpenDua):** O Allah, I ask You for knowledge which is beneficial and sustenance which is good, and deeds which are acceptable.
- **Draft:** اے اللہ! میں تجھ سے نفع دینے والے علم، پاکیزہ رزق اور مقبول عمل کا سوال کرتا ہوں۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-096

Hisn al-Muslim 96, read morning and evening, said 100 times a day.

- **Arabic:** أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ
- **English (OpenDua):** I seek Allah’s forgiveness and I turn to Him in repentance.
- **Draft:** میں اللہ سے مغفرت مانگتا ہوں اور اس کی طرف توبہ کرتا ہوں۔
- **Review:** [ ] correct as drafted · correction:

### ur · hisn-097

Hisn al-Muslim 97, read evening only, said 3 times.

- **Arabic:** أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ
- **English (OpenDua):** I take refuge in Allah’s perfect words from the evil He has created.
- **Draft:** میں اللہ کے کامل کلمات کی پناہ چاہتا ہوں اس کے شر سے جو اس نے پیدا کیا۔
- **Review:** [ ] correct as drafted · correction:
