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

**What the app stores**, compared with the file we download: your Uthmani text, byte for byte, with one change. The
basmala is taken out of the first ayah of each surah that carries it in your file and shown on its own line above the
surah. That line is always Al-Fatihah's spelling (1:1), so the shadda your file writes on the basmala's first letter in
at-Tin (95) and al-Qadr (97) is not shown. Copying or sharing an ayah gives this stored text. (Until this version the
app also stored every small high rounded zero, U+06DF, as U+0652. It no longer does.)

**What the app draws**, without changing what it stores: the KFGQPC Hafs typeface was made for the King Fahd Complex's
own encoding of the Madinah Mushaf, which writes some of the same signs with other code points. So, on the way to the
screen only, the app converts those signs to that encoding, so that the typeface draws each one as the 1405 AH Madinah
print our pages follow has it:

1. The sukun (U+0652) is drawn as U+06E1 and the small high rounded zero (U+06DF) as U+0652, which this typeface draws
   as the round sign over a silent letter.
2. Every tatweel directly before a small alef (U+0640 U+0670) is left out, as tanzil.net itself shows the text. With
   it, the typeface misplaced the alef, often on the baseline outside the word (ضَلَـٰلٍ at 31:11 among them).
3. After a lam, U+0640 U+0654 U+064E U+0627 is drawn as U+0623 U+0653, the lam-alef with hamza and madda (277 words,
   ٱلْـَٔاخِرَةِ among them).
4. A tanwin before the small meem of iqlab is drawn as one vowel with that meem, as the print writes iqlab. Every
   other tanwin is drawn in the staggered form (U+0657, U+065E, U+0656), unless a throat letter or alef wasla is read
   next.
5. In 44 words, the Complex's spelling of that word, 43 of them as the 1405 print writes them:
   - 2:72: the hamza on the line (U+0621).
   - 2:97: a tatweel after the lam.
   - 52:37: U+06DC for U+06E3. The typeface has no glyph for U+06E3 and drew a dotted circle.
   - The hamza below its seat in 14 words (U+064A or U+0648 with U+0655).
   - U+06E4 over the 26 words of the 15 sajdahs that the Complex's text marks. (The 1405 print also draws the line
     over لِلَّهِ in 27:25 and لَا in 84:21; the app does not.)

   The 44th is 12:11: U+06EC for U+06EB, which the typeface also has no glyph for. U+06EC draws the solid dot of the
   Complex's current print; the 1405 print's hollow lozenge is in no Hafs typeface.
6. The small seen of a sakta (U+06DC), which your text writes apart after its word (مَنْ ۜ رَاقٍ in 75:27, and in 18:1,
   36:52, 69:28 and 83:14), is drawn on that word, without the space before it, where the print sets it.

What is read does not change: the letters as read, the pause signs (apart from where that one seen sits) and the word
division are kept, and the code points listed above are the only ones that change on screen.

The app's credit now says, in English (and the same in its other six languages): "Tanzil Quran Text v1.1, Creative
Commons Attribution 3.0. Kept as Tanzil publishes it, except that the basmala stands above its surah instead of in the
first ayah (in Al-Fatihah it is still the first ayah); copy and share give this text. On screen only, its marks are
drawn in the Madinah Mushaf's form, written for the King Fahd Complex's Hafs typeface: the sukun, the silent-letter
sign, tanwin and iqlab take the Mushaf's shapes; the connecting stroke (tatweel) before a small alef is left out;
lam-alef carries its hamza and madda; the hamza stands alone on the line in 2:72 and below its seat in 14 words; 2:97
gains a connecting stroke; the small seen of a sakta sits on its word; 12:11 and 52:37 take the Complex's signs; and
the words of each sajdah carry a line over them." Our website says the same more briefly, and the repository's
ATTRIBUTION.md in full.

Your notice asks that the text not be changed, so I would rather ask than assume. Are you content with the
basmala's place in what we store, and with how we draw the text? If you would like any of it done differently, or
the credit worded differently, please tell me and we will follow your wishes.

One small thing we noticed on the way, in case it is useful: the download page gives the same file for
`tatweel=false` as for `tatweel=true`; only leaving the option out gives the text without the tatweel before
the small alef.

Jazakum Allahu khayran for your work on the text.

Mohamed Abulgasem
Taqwa · support@taqwa.world
