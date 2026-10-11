# Bengali adhkar review (bn)

`shared/src/commonMain/composeResources/files/adhkar/text/bn.json`, reviewed 11 October 2026
against the Arabic in `files/adhkar/items.json` (OpenDua, byte for byte), with OpenDua's English
as a cross-check only.

**Reviewer: Claude (AI), for the lead.** No native Bengali speaker has read this text. The
meanings were checked word by word against the Arabic. The register is a careful
mosque-booklet register, but it has not been judged by a native ear. If the owner wants a
Bangladeshi reader to sign it off before 1.1.0, this file is what they should read.

**Verdict: ships with the corrections below.** All 15 du'a, with both wordings of Hisn 77 and
78, pass after the corrections, so `"reviewed"` is now `true` and `bn` moves from `DRAFTS` to
`REVIEWED` in `AdhkarTranslationsTest`, in the same change, as the release switch requires.

## What was checked, item by item

- The meaning against the Arabic, clause by clause, with nothing added and nothing left out.
- Terminology. The words come from Islamic Foundation Bangladesh usage, as the reviewer knows
  it (no IFB text was at hand to check against). They were also checked, and counted where it
  mattered, against the app's own bundled Quran translation (`bn.bengali`, Muhiuddin Khan, in
  `quran.db`), which the Bengali reader sees on the four Quran pages of the same session.
  Where the app already says the same words, the adhkar use its wording: the Tasbeeh meanings
  in `values-bn/strings.xml`
  (আল্লাহ ছাড়া কোনো ইলাহ নেই, সমস্ত প্রশংসা আল্লাহর জন্য, আমি আল্লাহর কাছে ক্ষমা চাই,
  আল্লাহর পবিত্রতা ও প্রশংসা) and the screen names (আযকার).
- Spelling of Arabic loanwords. The lead's decision is য for ز and ذ, never জ.
- Honorifics as the app has them: Allah is addressed as আপনি with the polite imperatives (করুন,
  দিন, দেবেন না); তিনি, তাঁর and যাঁর are used for Allah, and যিনি for Ibrahim. ﷺ goes after
  the Prophet's name, with the case ending after it (মুহাম্মাদ ﷺ-এর). Nothing is added where
  the Arabic has no honorific.
- Script and punctuation: the Bengali danda । closes each sentence, ! follows a vocative
  (হে আল্লাহ!, হে আমার রব!), no ASCII apostrophe, NFC throughout (`check_items.py`).

## Changes (every one, with a literal English back-translation)

| Item | Was | Now | Back-translation of the new text | Why |
|------|-----|-----|----------------------------------|-----|
| hisn-077 morning | এই দিনের কল্যাণ এবং এর পরে যা আসে তার কল্যাণ | এই দিনে যা আছে তার কল্যাণ এবং এর পরে যা আছে তার কল্যাণ | "the good of what is in this day and the good of what is after it" | خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيرَ مَا بَعْدَهُ: the draft followed the English ("the good of this day") and dropped مَا فِي, "what is in". It now has both halves in parallel, as the Arabic does. |
| hisn-077 morning | এই দিনের অনিষ্ট এবং এর পরে যা আসে তার অনিষ্ট | এই দিনে যা আছে তার অনিষ্ট এবং এর পরে যা আছে তার অনিষ্ট | "the evil of what is in this day and the evil of what is after it" | شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ, the same repair. |
| hisn-077 evening | এই রাতের কল্যাণ এবং এর পরে যা আসে তার কল্যাণ | এই রাতে যা আছে তার কল্যাণ এবং এর পরে যা আছে তার কল্যাণ | "the good of what is in this night and the good of what is after it" | خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيرَ مَا بَعْدَهَا, the same repair. |
| hisn-077 evening | এই রাতের অনিষ্ট এবং এর পরে যা আসে তার অনিষ্ট | এই রাতে যা আছে তার অনিষ্ট এবং এর পরে যা আছে তার অনিষ্ট | "the evil of what is in this night and the evil of what is after it" | شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا, the same repair. |
| hisn-077 (both wordings), 092, 093 | তাঁর কোনো শরিক নেই | তাঁর কোনো শরীক নেই | "He has no partner" | Spelling only. شَرِيك has a long ī. The app's Quran translation prints শরীক (69 times, against শরিক 30), as Bangladeshi Islamic print usually does. It now matches the draft's own long vowels in ইবরাহীম and দ্বীন. মুশরিক keeps its short i, as *mushrik* has. |
| hisn-085 | আসমান ও জমিনের স্রষ্টা | আসমান ও যমীনের স্রষ্টা | "Creator of the heavens and the earth" | য for ز (the lead's rule). যমীন is how the app's Quran translation spells it (64 times, জমিন 3), including 2:255, which is on the Ayat al-Kursi page of this same session, and 39:46, which has the same phrase in the words আসমান ও যমীনের স্রষ্টা. |
| hisn-086 | জমিনে ও আসমানে | যমীনে ও আসমানে | "on the earth and in the heaven" | The same spelling. |
| (file) | `"reviewed": false` | `"reviewed": true` | n/a | Every item passes. |

The full sentences, after the changes:

- hisn-077 morning: হে আমার রব! আমি আপনার কাছে এই দিনে যা আছে তার কল্যাণ এবং এর পরে যা আছে তার কল্যাণ চাই। আমি এই দিনে যা আছে তার অনিষ্ট এবং এর পরে যা আছে তার অনিষ্ট থেকে আপনার আশ্রয় চাই।
  "O my Lord! I ask of You the good of what is in this day and the good of what is after it. I seek Your refuge from the evil of what is in this day and the evil of what is after it."
- hisn-077 evening: the same with এই রাতে, "in this night".

## Passed as drafted (and why)

| Item | Back-translation of the shipped Bengali | Note |
|------|------------------------------------------|------|
| hisn-077 (the rest) | "We have reached the morning (evening), and Allah's dominion too has reached the morning (evening). All praise is for Allah. There is no god but Allah; He is one, He has no partner. Dominion is His and praise is His, and He has power over everything. … O my Lord! I seek Your refuge from laziness and the bad state of old age. O my Lord! I seek Your refuge from the punishment of Jahannam and the punishment of the grave." | الْكِبَرِ is vowelled *al-kibar*, old age, so বার্ধক্য is right, not অহংকার (pride, *al-kibr*). عَذَابٍ فِي النَّارِ is জাহান্নামের আযাব, the usual Bengali rendering. |
| hisn-078 morning / evening | "O Allah! By You indeed we have reached the morning, and by You indeed we have reached the evening. By You indeed we live, and by You indeed we die, and to You is the resurrection (evening: the return)." | The ই on আপনার দ্বারাই carries the restriction that the fronted بِكَ gives. It is the literal reading; আপনারই অনুগ্রহে, "by Your grace", would be a gloss. النُّشُورُ is পুনরুত্থান and الْمَصِيرُ is প্রত্যাবর্তন, kept apart as the Hisn keeps them. |
| hisn-079 | "O Allah! You are my Lord; there is no god but You. You created me and I am Your servant. I am upon Your covenant and promise as far as I am able. I seek Your refuge from the evil of what I have done. I acknowledge to You Your favour upon me, and I acknowledge my sin too, so forgive me, for none but You forgives sins." | Five sentences at the five clauses, as the sheet says. أَبُوءُ لَكَ is "আপনার কাছে স্বীকার করি", acknowledging *to You*. |
| hisn-084 | "O Allah! I ask of You pardon and well-being in this world and the Hereafter. O Allah! I ask of You pardon and well-being in my religion, my world, my family and my wealth. O Allah! Cover my faults and keep me safe from my fears. O Allah! Protect me from in front of me, from behind me, from my right, from my left and from above me, and I seek the refuge of Your greatness from being destroyed unawares from beneath me." | وَآمِنْ رَوْعَاتِي, "make me safe from what I fear", is how the commentaries explain it, so the draft is faithful. أَنْ أُغْتَالَ مِنْ تَحْتِي keeps the Arabic, being destroyed unawares from beneath. It does not use OpenDua's "swallowed up by the earth", which is the narrator's explanation. |
| hisn-085 | "O Allah! Knower of the unseen and the seen, Creator of the heavens and the earth, Lord and Owner of everything! I bear witness that there is no god but You. I seek Your refuge from the evil of my self, from the evil of Satan and his snare, and from committing some evil against myself or dragging it to some Muslim." | وَشَرَكِهِ is "his snare", ফাঁদ, as the lead decided (the Arabic stays as OpenDua vowels it). It is not shirk. The order অদৃশ্য ও দৃশ্য is the Arabic's; the app's Quran translation of 39:46 reverses it, but that is the Quran's own sentence, not this one. |
| hisn-086 | "In the name of Allah, with whose name nothing on the earth or in the heaven can cause harm, and He is the All-Hearing, the All-Knowing." | সর্বশ্রোতা and সর্বজ্ঞ are the app's Quran translation's words for السَّمِيعُ and الْعَلِيمُ. |
| hisn-088 | "O Ever-Living, O Sustainer of everything! Through Your mercy I ask for help. Set right all my affairs, and do not leave me to myself even for the blink of an eye." | সবকিছুর ধারক is the app's Quran translation's word for الْقَيُّومُ (2:255). |
| hisn-090 | "We have reached the morning upon the fitrah of Islam, upon the word of sincerity, upon the religion of our Prophet Muhammad ﷺ and upon the way of our father Ibrahim, who was a single-minded Muslim and was not among the mushrikun." | The closing words are the Hisn's (they echo 3:67 and 3:95 but are part of the hadith, so they are not a Quran quotation). The wording মুশরিকদের অন্তর্ভুক্ত ছিলেন না is the same as the app's Quran translation of 3:95. |
| hisn-091 | "I declare Allah's purity and praise." | The Tasbeeh screen's meaning (আল্লাহর পবিত্রতা ও প্রশংসা) as a sentence. |
| hisn-092, 093 | "There is no god but Allah; He is one, He has no partner; dominion is His and praise is His, and He has power over everything." | Only শরীক changed (above). |
| hisn-094 | "I declare Allah's purity and praise, equal to the number of His creation, equal to His own pleasure, equal to the weight of His Throne and equal to the ink of His words." | |
| hisn-095 | "O Allah! I ask of You beneficial knowledge, pure provision and accepted deeds." | مُتَقَبَّلاً is a passive participle, "accepted", so কবুলকৃত is the literal word; কবুলযোগ্য, "acceptable", is the booklets' looser one. |
| hisn-096 | "I ask Allah for forgiveness and I turn to Him in repentance." | It begins with the Tasbeeh screen's wording. |
| hisn-097 | "I seek the refuge of Allah's perfect words, from the evil of what He has created." | The phrase after the verb is the same construction, in the same words, as the app's Quran translation of the identical مِنْ شَرِّ مَا خَلَقَ in 113:1–2. |

### The one জ kept for ز: ওজন (hisn-094)

وَزِنَةَ عَرْشِهِ is rendered আরশের ওজনের সমান. ওজন comes from Arabic وزن, but it is an everyday
Bengali word, not a transliterated religious term. Standard dictionaries spell it with জ, and
so does the app's own Quran translation (10 times, never ওযন), so ওযন would read as a
misspelling. This is the one deliberate exception to the য rule. If
the lead wants the rule applied without exception, the alternatives are ওযনের (strict, unusual)
or the native ভারের ("of the weight", no loanword). The rest of the text has no জ for ز or ذ:
জাহান্নাম is ج, and জীবিত, জ্ঞান, জন্য, রাজত্ব, কাজ and চিরঞ্জীব are native words.

## The Quran items

Ayat al-Kursi (hisn-075, 2:255) and al-Ikhlas, al-Falaq and an-Nas (hisn-076a/b/c, 112–114)
have **no entry** in `bn.json`. `check_items.py` requires exactly the 15 du'a ids in every
language file, so a Quran entry would fail the check, and the README and the review sheet both
say the app shows its own Quran translation for them. `AdhkarViewModel.quranText` reads each
ayah's translation from `quran.db` (`ayah_translation`) by reference, unchanged, and joins a
whole surah's ayahs with a space. The Bengali interface's default reading translation is
`bn.bengali` (`ReadingSettings.byLanguage`). So the Bengali reader sees Muhiuddin Khan's text
exactly as Tanzil publishes it (Tanzil's terms for translations: verbatim, credit the
translator), and nothing in this review touches it. The texts the page shows, as checked on 11 October 2026:

| Item | Reference | Ayahs | SHA-256 of the text the page shows |
|------|-----------|-------|------------------------------------|
| hisn-075 | 2:255 | 1 | `5f04aee67c9c4e517c4a19570d33f1b08d3253f70ef37409ea4bd8ab807443b2` |
| hisn-076a | 112 | 4 | `4f9ca6af26243bab110a81c27254add899dfc3918923fa1897f7582c4eb43d4b` |
| hisn-076b | 113 | 5 | `5e0525e1fd433b03abf45fcc6971432050c49c5dfe1e4530a3fb46aa09146ba1` |
| hisn-076c | 114 | 6 | `9d527fd912f3c5819c98f4e8c02760fe2c0224d187c6809ebdcd8716b849c3cf` |

The Bengali reader will notice two differences between the du'a pages and the Quran pages.
They are deliberate, because the Quran text cannot be edited:

- The du'a say ইলাহ (as the Tasbeeh screen does); 2:255 says উপাস্য.
- The du'a say রব (as Bengali Hisn al-Muslim booklets do); 113 and 114 say পালনকর্তা.

The isti'adhah printed before Ayat al-Kursi and the basmala before the Quls are shown in Arabic
only, in every language. That is outside this file.

## Checks

- `python3 -I tools/adhkar/check_items.py`: 0 failures. bn is listed as reviewed (shown), and
  fr, id, tr and ur as drafts. The hisnmuslim witness was not downloaded; it compares the
  Arabic only, which this review does not change.
- `./gradlew --no-daemon :shared:testAndroidHostTest --tests '*Adhkar*'`: see the commit
  report.
