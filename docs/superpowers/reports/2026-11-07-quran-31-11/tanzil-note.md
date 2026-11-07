# Draft courtesy note to Tanzil (not sent)

For Mohamed to send if he wishes, through Tanzil's contact page on tanzil.net or by email. Nobody here has
sent it or will. It assumes 1.5.1 has reached the stores and the website; if it goes earlier, change "now says" to
"will say from version 1.5.1".

---

**Subject:** Taqwa's use of the Tanzil Quran Text (Uthmani, version 1.1)

Assalamu alaykum,

I make Taqwa, a free prayer-times and Quran app for Android and iPhone (taqwa.world; its source is public at
github.com/MohamedAbulgasem/Taqwa). It has no ads, purchases, accounts or tracking. It carries your Quran Text
v1.1, the Uthmani text for reading and the Simple Clean text for search, credits the Tanzil Project with a link
to tanzil.net, and keeps your copyright block, unedited, in the app's database and in the source.

A reader recently showed us that the app drew the small alef (U+0670) outside some words, ضَلَـٰلٍ at 31:11
among them. Your text was right: the fault was in how the KFGQPC Uthmanic Hafs typeface placed the alef. While
fixing it we compared what the app holds with your download and found that our credit, which said "reproduced
verbatim", was wrong. I would like to tell you exactly what we do differently and ask whether you are content
with it.

**What the app stores**, compared with the file we download:

1. Every small high rounded zero (U+06DF) is stored as U+0652, because this typeface draws U+0652 as the round
   sign over a silent letter. That is 3,988 marks in 2,240 ayahs. Since U+0652 is also your sukun, the two signs
   are the same character in what we store, and in what copying or sharing an ayah gives.
2. The basmala is taken out of the first ayah of each surah that carries it in your file and shown on its own
   line above the surah. That line is always Al-Fatihah's spelling (1:1), so the shadda your file writes on the
   basmala's first letter in at-Tin (95) and al-Qadr (97) is not shown.

**What the app draws**, without changing what it stores:

3. In 322 words (1,333 occurrences) whose last letter, one of ب ت ث ش ف ق ك ل ن ى, comes straight after a
   tatweel and a small alef, every tatweel before a small alef is left out on screen. With it, the typeface
   joins the tatweel and the last letter into one glyph and misplaces the alef, in all but 13 of them on the
   baseline outside the word.
   Drawn this way, each of those words is spelled as in your download without the tatweel option, apart from
   change 1 in two occurrences. Copying or sharing an ayah still gives the stored text.

The app's credit now says, in English (and the same in its other six languages): "Tanzil Quran Text v1.1,
Creative Commons Attribution 3.0. Changed in three ways: the silent-letter sign takes the Hafs typeface's form,
the basmala stands above its surah instead of in the first ayah (in Al-Fatihah it is still the first ayah), and
some words are drawn without the connecting stroke (tatweel) before a small alef so the typeface puts the alef
in its place." Our website says the same more briefly, and the repository's ATTRIBUTION.md in full.

Your notice asks that the text not be changed, so I would rather ask than assume. Are you content with these
changes? If you would like any of them done differently, or the credit worded differently, please tell me and
we will follow your wishes.

One small thing we noticed on the way, in case it is useful: the download page gives the same file for
`tatweel=false` as for `tatweel=true`; only leaving the option out gives the text without the tatweel before
the small alef.

Jazakum Allahu khayran for your work on the text.

Mohamed Abulgasem
Taqwa · support@taqwa.world
