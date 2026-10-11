# tools/adhkar

The content of the Morning & Evening Adhkar: 19 items of Hisn al-Muslim's chapter
"أذكار الصباح والمساء" (Hisn 75–98, the undisputed ones only), built from the OpenDua
dataset and checked against the Hisn text.

| File | What it is |
|------|------------|
| `build_items.py` | Extracts OpenDua's 24 morning-and-evening entries and builds the two resources |
| `check_items.py` | Checks the resources against the spec's table and the hisnmuslim.com text, and every language file |
| `embed_test_fixture.py` | Copies the two resources into `AdhkarFixtures.kt`, which the catalogue tests parse (`Res.readBytes` does not run in tests) |
| `opendua-morning-evening.json` | The 24 entries (Hisn 75–98), verbatim, under OpenDua's attribution: the build's input |
| `TRANSLATIONS-FOR-REVIEW.md` | The five drafted translations beside the Arabic and the English, one section per language, for the reviewers to mark up |
| `../../shared/src/commonMain/composeResources/files/adhkar/items.json` | The 19 items, book order (schema 1) |
| `../../shared/src/commonMain/composeResources/files/adhkar/text/en.json` | English for the 15 du'a items (OpenDua's), `"reviewed": true` |
| `../../shared/src/commonMain/composeResources/files/adhkar/text/{bn,fr,id,tr,ur}.json` | Bengali, French, Indonesian, Turkish and Urdu for the 15 du'a items: drafts, `"reviewed": false` until their reviewer flips it (see Translations below) |

Licence: `docs/licences/OpenDua-CC-BY-4.0.txt` (CC BY 4.0, attribution verbatim).

## Running

```sh
# from the repository root
python3 -I tools/adhkar/build_items.py <catalogue.json>   # extract + build
python3 -I tools/adhkar/build_items.py                    # rebuild from the checked-in extract
python3 -I tools/adhkar/check_items.py <hisnmuslim-27.json>
python3 -I tools/adhkar/embed_test_fixture.py             # after a rebuild: refresh the tests' copy
```

The scripts use the standard library only. The build is deterministic (sorted keys,
UTF-8 without BOM, two-space indent, trailing newline): a rebuild from the same input
is byte-identical. `check_items.py` exits 1 on any failure; without the hisnmuslim file
it runs the structural checks only and prints a warning that the Arabic was not
compared (a path that is given but missing is a failure, not a warning). It also checks
`text/en.json` and every other `text/<lang>.json` present: a `"reviewed"` true or false
(`en.json` true), exactly the 15 du'a ids, each with exactly its Arabic's keys
(`morning`/`evening` for Hisn 77 and 78, `morning` for Hisn 90, `both` otherwise), no empty
string, NFC, and typographic apostrophes only; it prints which languages are reviewed
(shown) and which are drafts (not shown). A language may have no file.

## Inputs

- **OpenDua catalogue** v0.0.4 (released 9 September 2026), by Ihsan Labs at the Human
  Development Fund: https://opendua.org/v0.0.4/catalogue.json (from
  https://opendua.org/downloads; the copy used on 11 October 2026 has SHA-256
  `231dfca7b1d0b5bea8ead8125c8d556121a115c40c9e348e08f4b3ede5194958`). Only the entries
  tagged `morning-and-evening` are kept, in `opendua-morning-evening.json`.
- **hisnmuslim.com chapter 27**, http://www.hisnmuslim.com/api/ar/27.json (SHA-256 of
  the copy used: `64cd10f23a2e98c8bf17aac1efa2ccbd268bece799b91dbf1e3b91ffeec8dbc1`).
  A **verification witness only**: it carries no licence, so it is never checked in and
  nothing from it ships. Download it to a scratch folder to run the check.

What comes from where: the Arabic, the English and the transliteration from OpenDua;
the counts from OpenDua's `times` (absent means once); the sets, the daily flag, the
Quran references and the hadith sources from the tables in `build_items.py` (spec
§4.1, numbers verified in the R1 research notes), never from OpenDua.

## Decisions (11 October 2026)

**The Arabic is OpenDua's, byte for byte.** The check found four differences from the
hisnmuslim.com text, none a real difference: each is a mark or a comma OpenDua writes
and hisnmuslim.com leaves out, with the same letters and the same reading. OpenDua's
fuller text is kept, and `check_items.py` allows exactly these four (it fails if one
disappears or a new one appears):

| Hisn | hisnmuslim.com | OpenDua (shipped) | Difference |
|------|----------------|-------------------|------------|
| 77 | إلاَّ | إِلاَّ | kasra under the hamza of *illā* (implicit in إ) |
| 78 | نَمُوتُ وَإِلَيْكَ | نَمُوتُ، وَإِلَيْكَ | a comma (the Hisn's own evening note has it) |
| 90 | أَصْبَحْنا | أَصْبَحْنَا | fatha before the long alif |
| 90 | الْمُشرِكِينَ | الْمُشْرِكِينَ | sukun on the shin |

The R1 notes quote these openings as hisnmuslim.com prints them, so they agree with the
left column; no word or vowel differs, so the brief's "prefer the hisnmuslim wording"
rule did not apply.

**Evening wordings** (Hisn 77, 78). hisnmuslim.com gives them only as unvowelled notes
(`[وإذا أمسى قال: …]`), so the check compares their letters with the note applied to the
morning wording, requires every word they share with the morning form to carry the
morning form's marks, and lists the evening-only words for review: أَمْسَيْنَا وَأَمْسَى
هَذِهِ اللَّيْلَةِ بَعْدَهَا (77) and الْمَصِيرُ (78), each read and correct. Hisn 78
keeps النشور in the morning and المصير in the evening, as the Hisn prints.

**Shape.** OpenDua's `standard` variation becomes `"both"`, `morning`/`evening` keep
their names. Hisn 90 ships in the morning only (its evening addition is graded shādhdh),
so its one wording is stored under `"morning"`, not `"both"`: it says أصبحنا and must
never be shown in the evening. The three Quls (one OpenDua entry, Hisn 76) become
`hisn-076a/b/c`, 3× each. No Quranic text is copied: Quran items carry a reference
(`quran.surah`, `ayah`, `prefix`), and Ayat al-Kursi carries only the istiʿādhah Hisn
prints before it (`أَعُوذُ بِاللَّهِ مِنَ الشَّيطَانِ الرَّجِيمِ`, OpenDua's, identical
to hisnmuslim.com).

**Sources** follow the spec's table. Where the spec names a collection without a number,
the number is the one the R1 notes verified: al-Hakim 1/562 for Hisn 75 (Hisn's own
footnote; the spec lists al-Hakim beside an-Nasa'i al-Kubra 10731), Ahmad 15360 and
an-Nasa'i al-Kubra 9743 for Hisn 90, an-Nasa'i ʿAmal al-Yawm 24 for Hisn 92. Grading
works (Sahih at-Targhib, as-Silsilah as-Sahihah) are not sources and are not listed; the
app shows no grades.

**A source's number is digits and reference punctuation only** (`2:255`, `1/562`). The app
shows a reference as the collection's name in the interface language and the number in its
digits, and nothing else in the number is translated, so a word there would reach an
Arabic, Urdu or Bengali line in Latin letters (the store screenshots for 1.1.0 showed
«النسائي al-Kubra 10731»). Where a citation names the book as well as the author, the book
is a collection of its own with a `source_<collection>` string in every language:
an-Nasa'i's as-Sunan al-Kubra is `nasai_kubra` (10731, 9743) and his ʿAmal al-Yawm
wa-l-Layla `nasai_amal` (24). The collection ids are `quran`, `bukhari`, `muslim`,
`abu_dawud`, `tirmidhi`, `ibn_majah`, `nasai`, `nasai_kubra`, `nasai_amal`, `hakim` and
`ahmad`; `build_items.py` stops on an unknown one or a worded number, `check_items.py`
fails on either, and `AdhkarReferencesTest` holds the bundled file to both and to a name in
`sourceNameRes` for every collection it cites. A new collection needs its id in both
scripts' `COLLECTIONS`, a `source_<id>` string in all eight folders and a line in
`sourceNameRes` (`AdhkarPage.kt`).

**Counts and sets** match the spec, OpenDua's `times` and hisnmuslim.com's `REPEAT` for
all 19 items. Morning: 18 pages; evening: 15. Hisn 93, 94, 95 and 90 are morning only,
97 evening only, and 96 (100 a day) is one counter shared by both sets
(`countDaily: true`).

**Not shipped** (contested grading): Hisn 80, 81, 82, 83 (حسبي الله), 87, 89, 98, and the
evening form of 90. They stay in the extract, which is OpenDua's chapter verbatim.

**Read-through repairs** (in `build_items.py`, each must match exactly once, so an
upstream fix fails the build instead of being applied twice). OpenDua's transliteration
generator drops a long vowel where the Arabic leaves it unwritten and splits *kalima*
as if it began with the preposition *ka-*:

| Item | OpenDua | Shipped |
|------|---------|---------|
| hisn-079 | adh-dhunwba | adh-dhunūba |
| hisn-085 | as-samawāti | as-samāwāti |
| hisn-085 | ash-shayṭni | ash-shayṭāni |
| hisn-086 | as-smāʾi | as-samāʾi |
| hisn-088 | astaghythu | astaghīthu |
| hisn-090 | ka-limati | kalimati |
| hisn-094 | ka-li-mātih | kalimātih |
| hisn-097 | bi-ka-li-māti | bi-kalimāti |

English: Hisn 96 alone is wrapped in ‘…’ and spells "Allaah"; shipped as "I seek
Allah’s forgiveness and I turn to Him in repentance." Hisn 85 is published as "the evil
and shirk of the devil" under an Arabic that reads وَشَرَكِهِ, "his snares"; shipped as
"the evil and snares of the devil", so the English says what the Arabic on screen says
(the owner's decision, 11 October 2026; the five translations follow the same reading).
Both are `TRANSLATION_REPAIRS` in `build_items.py`, so a rebuild writes the shipped
`en.json` byte for byte. Every other English text is OpenDua's as published. Left as published, for a reviewer to decide: Hisn 79's "I abide
to Your covenant" and "the evil of which I have committed", and Hisn 90's "Ibraheem".

**Translations** (bn, fr, id, tr, ur). OpenDua publishes English only, so the other five
languages were drafted by Claude on 11 October 2026 from the Arabic, with OpenDua's
English as a cross-check: "Allah" untranslated (আল্লাহ, اللہ), ﷺ after the Prophet's
name, the language's usual word for رب, no explanatory additions, the Arabic's sentence
boundaries, typographic apostrophes. They are drafts: `TRANSLATIONS-FOR-REVIEW.md` sets
each one beside the Arabic and the English, with the choices a reviewer should weigh,
and a language ships only after one reviewer has read it. A reviewer's corrections go
straight into `text/<lang>.json`; of the files in `text/`, `build_items.py` writes only
`en.json`. Indonesian is `id.json` only: the `values-in` twin is an Android resource
rule, not a file rule. The Quran items need no entry; the app shows its own Quran
translation for them.

**The release switch is `"reviewed"`.** Every file in `text/` travels in every build, so
each carries `"reviewed": true|false`, and `AdhkarCatalogue.loadText` reads a file whose
flag is not `true` exactly as a missing one: the reader never sees a draft. `en.json`
(OpenDua's published English, written by `build_items.py`) is `true`; the five drafts are
`false`. When a language's reviewer has passed it and the corrections are in, flipping its
flag to `true` ships it, one language at a time, and nothing else does; the same change
moves the language from `DRAFTS` to `REVIEWED` in `AdhkarTranslationsTest`, which asserts
every draft loads as null and every reviewed language loads.

## Updating to a new OpenDua release

Change `DATA_VERSION` in `build_items.py` and `ATTRIBUTION` in both scripts (and the
licence file), run the build with the new catalogue, then the check with a fresh copy of
the hisnmuslim chapter. A repair OpenDua has fixed upstream stops the build; delete it
from the table. A new difference from the hisnmuslim text stops the check; resolve it by
the rule above and record it here.
