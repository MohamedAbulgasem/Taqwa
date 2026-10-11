# French adhkar review (fr)

File reviewed: `shared/src/commonMain/composeResources/files/adhkar/text/fr.json`, the draft of
11 October 2026 (branch `adhkar-12h`, 88bbc27e).

Reviewer: Claude (an AI model), 11 October 2026, at the lead's request. This is a second machine
reading against the Arabic, not a native French speaker's reading. If the owner wants a native
reader as well, these notes are what that reader should start from.

Verdict: ships with the three corrections below. Every item was read against the shipped Arabic
in `items.json`, not only against the English. `"reviewed"` is flipped to `true` in a separate
commit, together with moving `fr` from `DRAFTS` to `REVIEWED` in `AdhkarTranslationsTest`, as
`tools/adhkar/README.md` requires.

## What the French was checked against

- **The Arabic** of each du'a, as shipped in `items.json` (OpenDua's text, 15 du'a, 17 wordings).
- **The app's French strings** (`values-fr/strings.xml`). The app writes **Allah**, not Dieu.
  Its Tasbeeh meanings are reused word for word: *Gloire et louange à Allah* (091, 094),
  *Toute louange revient à Allah* (077), *Il n’y a de dieu qu’Allah* (077, 092, 093, and *il n’y
  a de dieu que Toi* in 079 and 085), *Je demande pardon à Allah* (096). The app also writes
  *dhikr*, *Adhkar du matin / du soir*, *Maghrib*, *Fajr*, *Asr*.
- **The app's French Quran**, Hamidullah (`fr.hamidullah` in `quran.db`), wherever the du'a uses a
  phrase the Quran also uses. The draft already uses Hamidullah's wording in five places: *le
  Vivant… qui subsiste par lui-même* (2:255), *l’Audient, l’Omniscient* (2:127, 2:137), *Abraham*,
  *du nombre des associateurs* (3:67, 16:120, 16:123), *la royauté* (64:1, 67:1). One correction
  below brings 078 in line with Hamidullah's 67:15, which has the same Arabic.

## The Quran items

Ayat al-Kursi (hisn-075, 2:255) and the three Quls (hisn-076a/b/c, surahs 112, 113 and 114) have
**no entry** in `fr.json`, and they must not have one. The page reads their translation from
`quran.db` at run time (`AdhkarViewModel.quranText`, through `QuranRepository.translationText` /
`translationTexts`), in the translation the reader has chosen. A French reader's default is
`fr.hamidullah` (`ReadingSettings.byLanguage`). The text is drawn as stored; a whole surah's ayat
are joined with a single space. So the French reader sees Hamidullah's text verbatim, which is what
Tanzil's verbatim-only terms require (`translation.licence`: "non-commercial use, verbatim, credit
the translator"). It is never a new or edited translation. I read the four passages in `quran.db`
(2:255, 112:1–4, 113:1–5, 114:1–6: sixteen ayat), and they are present and complete.
`AdhkarQuranReferencesTest` pins the references.

Ayat al-Kursi's istiʿādhah (the item's only Arabic of its own) is shown in Arabic only. No
translation is drawn for it, in any language.

`tools/adhkar/README.md` ("The Quran items need no entry; the app shows its own Quran translation
for them") and `TRANSLATIONS-FOR-REVIEW.md` ("The four Quran items … have no entry") both say this.
The JSON itself cannot say it: `check_items.py` allows only `language`, `reviewed` and `items` at
the top level.

## Corrections (three, all in fr.json)

| Item | Draft | Corrected | Why |
|------|-------|-----------|-----|
| hisn-078 morning | … et vers Toi est la résurrection. | … et vers Toi est la **R**ésurrection. | وَإِلَيْكَ النُّشُورُ is the Quran's own phrase (67:15, وإليه النشور). The app's Hamidullah writes it *Vers Lui est la Résurrection*, with a capital, as French Islamic usage writes the eschatological Resurrection (*le Jour de la Résurrection*). The page should match the app's Quran for the same words. |
| hisn-088 | **Arrange** pour moi toutes mes affaires | **Améliore** pour moi toutes mes affaires | أَصْلِحْ لِي: *arranger* is colloquial in French (*ça m’arrange*, "that suits me") and reads oddly in a prayer. *Améliore pour moi…* is the established French rendering of أصلح لي in du'a booklets (*Ô Allah, améliore pour moi ma religion…*), and it keeps لي as *pour moi*. |
| hisn-094 | Gloire et louange à Allah, **selon** le nombre de Ses créatures, Son propre agrément, le poids de Son Trône et l’encre de Ses paroles. | Gloire et louange à Allah, **à la mesure du** nombre de Ses créatures, **de** Son propre agrément, **du** poids de Son Trône et **de** l’encre de Ses paroles. | عَدَدَ … وَرِضَا … وَزِنَةَ … وَمِدَادَ are four measures ("as many as, as much as"). *Selon* means "according to" and loses that sense of quantity; *selon le poids de Son Trône* is not idiomatic. *À la mesure de* carries all four measures, and the preposition is repeated before each so that each one reads on its own. |

Literal English back-translations of the corrected texts:

- **hisn-078 morning**: "O Allah, it is by You that we have reached the morning and by You that we
  have reached the evening. By You we live and by You we die, and towards You is the Resurrection."
- **hisn-088**: "O Living One, O You who subsist by Yourself, it is by Your mercy that I implore help.
  Improve for me all my affairs and do not abandon me to myself, be it only for the time of a blink
  of an eye."
- **hisn-094**: "Glory and praise to Allah, to the measure of the number of His creatures, of His own
  good pleasure, of the weight of His Throne and of the ink of His words."

## Every item

"Pass" means the draft is faithful to the Arabic, written in the established French Islamic
terms, natural, consistent with the app, and correctly punctuated.

| Item | Verdict | Notes |
|------|---------|-------|
| hisn-077 morning | pass | *Nous voici au matin, et au matin la royauté appartient à Allah* keeps the parallel أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ (the dominion too has entered the morning), as the sheet asks. *Le bien de ce jour* for خَيْرَ مَا فِي هَذَا الْيَوْمِ: French *le bien de ce jour* already means the good this day holds, so *ce qu’il y a en* is not needed. سُوءِ الْكِبَرِ is *les maux de la vieillesse* (Hisn vowels الكِبَر, old age, not الكِبْر, pride). |
| hisn-077 evening | pass | As the morning wording, with *au soir* and *cette nuit / ce qui la suit* (feminine agreement correct). |
| hisn-078 morning | corrected | See above. *C’est par Toi que…* is the literal "by You" (no gloss such as "by Your grace"), and it reads naturally in French. |
| hisn-078 evening | pass | وَإِلَيْكَ الْمَصِيرُ is *et vers Toi est le retour*. Hamidullah gives the same Arabic as *C’est à Toi que sera le retour* (2:285), so it is the same noun. |
| hisn-079 | pass | Five sentences at the five clauses. وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ is *Je reste fidèle à Ton pacte et à Ta promesse*: the possessives are the Arabic's own -ka, and both readings in the commentaries (the covenant I made with You / what You promised) survive. أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ is *Je reconnais devant Toi Ton bienfait envers moi*. |
| hisn-084 | pass | الْعَفْوَ وَالْعَافِيَةَ is *le pardon et la préservation*, the usual French pair for this hadith wording. The draft keeps أَنْ أُغْتَالَ مِنْ تَحْتِي as *pour ne pas être anéanti à l’improviste par en bas* (the shared reading, being destroyed unawares from beneath), not the narrator's gloss "swallowed by the earth". The directions follow the Arabic's order. |
| hisn-085 | pass | The lead's decision: شَرَكِهِ (as shipped) is *ses pièges*, "his snares", not shirk. فَاطِرَ السَّمَوَاتِ وَالْأَرْضِ is *Créateur des cieux et de la terre*, as Hamidullah 39:46. *Satan* for الشَّيْطان is established French usage, and Hamidullah uses it too beside *le Diable*. |
| hisn-086 | pass | *Avec le nom duquel* is correct French after a preposition and noun. Hamidullah writes السَّمِيعُ الْعَلِيمُ as *l’Audient, l’Omniscient*. |
| hisn-088 | corrected | See above. *Ô Toi qui subsistes par Toi-même* matches Hamidullah's 2:255 (*Celui qui subsiste par lui-même*), the Ayat al-Kursi page the same set shows. |
| hisn-090 (morning only) | pass | كَلِمَةِ الْإِخْلاَصِ is the literal *la parole de la sincérité*, with no gloss, as the sheet chose. *Abraham* and *du nombre des associateurs* are Hamidullah's. ﷺ follows the Prophet's name, as in the other four drafts. *L’islam* is lowercase, which is French typography for the religion. |
| hisn-091 | pass | The app's Tasbeeh meaning, verbatim. |
| hisn-092 | pass | Matches 077's wording of the same words. |
| hisn-093 | pass | Identical to 092, as the Arabic is. |
| hisn-094 | corrected | See above. It starts with the app's *Gloire et louange à Allah*. |
| hisn-095 | pass | *Une science utile, une subsistance pure et une œuvre acceptée*: the usual French terms (رزقا طيبا is *pure*, good and lawful). |
| hisn-096 | pass | The app's *Je demande pardon à Allah*, then *je reviens à Lui repentant* for أَتُوبُ إِلَيْهِ. |
| hisn-097 | pass | *Je cherche refuge auprès des paroles parfaites d’Allah contre le mal de ce qu’Il a créé.* |

## Conventions confirmed

- **Allah** untranslated, as the app's French strings and Hamidullah write it. **Seigneur** is
  رب. Allah is addressed as *Tu / Toi / Te / Ton / Ta / Ses / Lui / Il*, capitalised; other people's
  pronouns are lowercase (*ses pièges*, Satan's).
- **Ô** with its circumflex (modern French). Hamidullah's file writes *O*; that is his text and it
  stays as it is.
- **Punctuation**: typographic apostrophes only (validator-checked). No `: ; ! ?` and no
  guillemets, so no U+202F is needed.
- **Grammatical gender**: the French is in the masculine generic (*Tu m’as créé*, *Ton
  serviteur*, *anéanti*), as the Arabic is (عَبْدُكَ) and as French du'a booklets print it. A woman
  reading would say *créée*, *Ta servante*, *anéantie*. The page cannot know who is reading, and no
  draft marks both forms. I did not change this.
- **أعوذ** is *je cherche refuge* throughout the du'a. Hamidullah's Quls on the pages before them
  say *Je cherche protection*. Both are established French. The du'a are consistent with each other
  and the Quran's text is not ours to change, so I left both as they are.

## Not checked

- How TalkBack and VoiceOver read ﷺ inside a French sentence, which is the same question for every
  drafted language. That is an accessibility check on a device, not a translation question.
- The page layout of the longest French text (hisn-077, about 570 characters) at large font sizes.
- Nothing here changes the English: `en.json` hisn-085 still says "shirk", against the lead's
  decision. That belongs to the English file's owner.
