# Taqwa — Slices 2 and 3: Quran text and audio. Investigation and draft plan

Status: **draft for review, 7 September 2026.** Written overnight to be iterated on and locked in before implementation. Nothing here is built yet; the one thing that must be tried on a phone before the plan is final is called out in §2.4.

Companion page: five candidate Quran fonts rendering Al-Fatiha and Ayat al-Kursi on Taqwa's own card, light and dark, with a size slider: https://claude.ai/code/artifact/8285000b-5b1a-4de8-978c-0db25544db32

---

## 0. The short version

**What slice 2 should be.** A Quran reader in the Taqwa idiom: one surah at a time, each ayah as a block of Uthmani Arabic in a proper Mushaf typeface, with the translation beneath it and, when switched on, a transliteration line between the two. Surah list with juz and search, last-read position, bookmarks, a font-size control, and a translation picker. Everything bundled; nothing fetched.

**What slice 3 should be.** Recitation of that same surah by a small set of reciters, ayah by ayah, with the current ayah highlighted and the list following along, background playback with lock-screen controls, repeat, and per-surah downloads so the audio works offline once fetched. Audio is downloaded on demand, never bundled, and hosted where it costs nothing.

**Recommendations, in one table.**

| Decision | Recommendation | Why |
|---|---|---|
| Arabic text | Tanzil Uthmani v1.1, with pause marks | The reference text every serious app uses; CC BY-ND, so attribution and no edits; ~1.4 MB |
| Typeface | **KFGQPC Uthmanic Hafs** (v22) as the reading font; Amiri Quran held as the fallback | It is the Madinah Mushaf hand, free to distribute unmodified; Amiri Quran is OFL and renders the same text beautifully if KFGQPC misbehaves on a device |
| Layout | Ayah-by-ayah list, translation beneath | Fits translations and transliteration naturally; the page-accurate Mushaf mode is a later addition, not the first release |
| Translations at launch | English (Saheeh International), Arabic tafsir (Muyassar), Indonesian, Urdu, Bengali, Turkish, French, plus English transliteration | Covers the largest Muslim language groups; all available from Tanzil or QUL for non-commercial use; ~1 to 2 MB each |
| Storage | One bundled SQLite database via SQLDelight; search scans a normalised column in Kotlin | Read-only, ~15 to 25 MB, works identically on both platforms. (FTS5 was the original plan and was dropped in 2b: Android's framework SQLite has no FTS5 module.) |
| Audio source | Per-ayah MP3 from the Islamic Network archive (the `alquran.cloud` corpus, which is the everyayah set) | The only source with a written licence that allows free non-commercial redistribution |
| Audio hosting | Public `taqwa-data` GitHub repository, files attached to Releases | Zero cost, no bandwidth cap, 2 GiB per file; per-surah zips per reciter |
| Reciters at launch | Two or three: Mahmoud Khalil Al-Husary (murattal), Mishary Alafasy, Abdul Basit (murattal) | Most requested, all in the permitted corpus, one Egyptian classical voice and one contemporary |
| Playback | Own `expect`/`actual` over Media3 (Android) and AVQueuePlayer (iOS) | Same pattern as the rest of the app; the KMP player libraries are young and none owns lock-screen controls on both platforms |

**The things only you can decide** are collected in §10.

---

## 1. The Arabic text

### 1.1 Sources

| Source | What it is | Licence | Notes |
|---|---|---|---|
| **Tanzil** (tanzil.net) | The de-facto reference digital Quran text since 2008; Uthmani and Simple variants; v1.1 (Feb 2021) | Creative Commons BY-ND 3.0: copy and distribute verbatim, no changes, credit "Tanzil Project" and link to tanzil.net | Quran.com itself credits Tanzil for its text. Options at download time: pause marks, sajdah signs, rub-el-hizb signs, tatweel under superscript alef. Formats: text, XML, SQL |
| **QUL Quran scripts** (qul.tarteel.ai) | Thirty script variants from Quran.com's data: Uthmani, Uthmani simple, Imlaei, IndoPak, QPC Hafs (the King Fahd Complex's own Unicode text), QPC glyph codes for the page fonts, tajweed-annotated | Varies by resource; the site says so and does not print a licence per item | Same Uthmani text lineage; the "QPC Hafs" script is the one meant to pair with the KFGQPC Hafs font |
| **King Fahd Complex** (qurancomplex.gov.sa) | The origin of the Madinah Mushaf text and fonts | Free to use, copy and distribute unmodified; no sale, no alteration | Their copyright page was unreachable tonight (connection refused); the terms above are as quoted by the ScanCode licence database and by mirrors |

**Recommendation:** Tanzil Uthmani with pause marks and sajdah signs, no tatweel option. Its licence is unambiguous, its attribution is a line in the Attribution screen we already have, and its text is what the fonts below were tested against. Keep the QUL "QPC Hafs" script downloaded alongside as a check: if the KFGQPC font renders any Tanzil sequence badly, that script is the encoding the font's authors intended.

### 1.2 Two encodings to be aware of

The overnight comparison surfaced one concrete difference between fonts. Tanzil writes the end-of-ayah as U+06DD (the ornament) followed by Arabic-Indic digits, and Amiri Quran composes those into one roundel with the number inside. The KFGQPC Hafs font instead draws **bare Arabic-Indic digits as the roundel** (in a Mushaf, digits appear nowhere else), and treats U+06DD as a separate ornament, so the Tanzil sequence produced two roundels. The fix is trivial (drop U+06DD for that font) but it means the ayah marker is font-specific and must be handled in one place in the renderer. The comparison page now does exactly that.

### 1.3 Search text

Searching needs a second, normalised copy of every ayah: no harakat, no small signs, alef variants folded, ta marbuta and ha kept distinct. Tanzil's "Simple Clean" text is very close to this and can be the source column; normalisation of the user's query happens in code. Matching that column happens in Kotlin (see the 2b design): FTS5 was the original plan, but Android's framework SQLite is built without that module.

---

## 2. Typography

### 2.1 Candidates

| Font | Kind | Licence | Size | Verdict from the comparison page |
|---|---|---|---|---|
| **KFGQPC Uthmanic Script Hafs v22** | Unicode Naskh, the Madinah Mushaf hand | KFGQPC: free to use, copy and distribute unmodified; no sale, no modification | 298 KB | Signs sit where the printed Mushaf puts them; slightly heavy on screen; ayah marker from bare digits (§1.2). The safest choice for readers who know the physical Mushaf |
| **Amiri Quran 1.003** | Unicode Naskh by Khaled Hosny, Quran-specific subset with a taller line for pause marks | SIL OFL 1.1 | 137 KB | Lighter, more elegant, composes the ayah roundel correctly, full Uthmani sign set. Not the hand people see in print |
| **Digital Khatt v2** | Variable font matching the 1420H Madinah Mushaf; parametric, designed for justified page layout | OFL 1.1 for the old-Madina repository; v2 licence not printed on QUL | OTF | Made for page-accurate typesetting with its own justification engine; over-engineered for an ayah list, worth a look for a Mushaf mode |
| **QPC V2 / V4 page fonts** | One font per page (V2: 604 files; V4: 47) where each glyph is a whole word | KFGQPC terms as above; Quran.com's own tutorial "strongly recommends against" storing them locally | Tens of MB | Pixel-exact Mushaf pages. Requires the QUL layout data and glyph-code script rather than Unicode text. The route for a later Mushaf mode, not for slice 2 |
| Scheherazade New, Noto Naskh Arabic | General Arabic text fonts | OFL | — | Baseline. Quranic signs collide; the tone is a UI font |
| The phone's default Arabic | Whatever the OEM ships | — | — | The control, and the reason the app must bundle a font: this is what Samsung, Google and the LoopPhone each render differently today |

### 2.2 Recommendation

KFGQPC Hafs as the one reading font. One font, no font picker, consistent with the "one accent, one idea" design. Amiri Quran stays in the repository as the tested fallback; switching is a one-line change if a device problem appears.

### 2.3 Sizes and the translation beneath

A reading size of 26 to 28 px Arabic against the 15 sp translation reads well on the comparison page; the app should offer roughly five Arabic sizes (22 to 40 sp) and scale the translation more gently. Line height for Uthmani text needs about 2.0 times the font size to keep stacked signs off the line above; the card padding and ayah spacing in the mockups assume that.

### 2.4 The one spike before the plan is final

The comparison page proves the fonts and the text. It does not prove Compose. On Android, Compose text is shaped by the platform (HarfBuzz through Minikin), so a bundled font should behave, but the LoopPhone and the S23 have already shown OEM differences in Arabic handling once. On iOS, Compose Multiplatform draws text itself through Skia (SkParagraph, HarfBuzz shaping, Skia milestone 150 in 1.12) rather than through UIKit, which is good news for consistency but has not been exercised with a Quran font's dense mark positioning. The 1.12 release notes mention RTL fixes for combining marks in text fields, which is a sign the path has had attention, not proof.

**First task of the implementation, before any UI is built:** a throwaway screen that renders Al-Fatiha and Ayat al-Kursi in KFGQPC Hafs and Amiri Quran on the LoopPhone, the S23, the iPhone 12 and the emulator, at three sizes, light and dark. Half a day. If either font misplaces a sign on either platform, that decides the font before anything depends on it.

---

## 3. Translations and transliteration

### 3.1 Licensing reality

| Source | Terms | Practical meaning |
|---|---|---|
| **Tanzil translations** | Non-commercial use only; link back to Tanzil's translation page when bundling more than three; translator credited | Taqwa is non-commercial and stays so under GPL-3.0 with no monetisation, so this fits; the link goes in the Attribution screen |
| **QUL translations** (204, 22 word-by-word) | Vary per resource; the FAQ says review each; individual pages did not show licence fields tonight (two returned server errors) | Usable case by case; prefer the ones that also exist on Tanzil so the terms are known |
| **Saheeh International** | Published by Dar Abul-Qasim; distributed by Tanzil under the non-commercial terms above; some copies carry a "no changes, notify the publisher" note | Take it from Tanzil, verbatim, credited |
| **The Clear Quran** (Mustafa Khattab) | Copyright Al-Furqaan Foundation, all rights reserved; authorised publishers listed | Quran.com hosts it under an arrangement of its own. **Do not bundle without written permission.** Worth an email; it is the most readable modern English translation |
| **Quran Foundation API content** (Quran.com) | Developer terms allow content inside an app's experience, but forbid caching longer than a week without their sync API and forbid redistribution as data | Incompatible with an offline app that ships translations inside it; the API is not the route |
| **Quranic Arabic Corpus** (word by word, morphology) | GNU GPL | Compatible with our GPL-3.0 code if ever used for a word-by-word feature; not needed for slice 2 |

### 3.2 Recommended launch set

| Language | Translation | Source | Why this one |
|---|---|---|---|
| English | Saheeh International | Tanzil | The most widely used modern English translation; clear terms |
| Arabic | Tafsir al-Muyassar | Tanzil ("Arabic tafsir") | An Arabic reader wants the meaning, not a translation; Muyassar is the King Fahd Complex's own brief tafsir |
| Indonesian | Kementerian Agama | Tanzil | The largest Muslim country; the official translation |
| Urdu | Junagarhi or Maududi | Tanzil | Both present; pick one at review |
| Bengali | Muhiuddin Khan | Tanzil | Standard |
| Turkish | Diyanet İşleri | Tanzil | Official |
| French | Muhammad Hamidullah | Tanzil | Standard, covers North and West Africa |
| Transliteration | English transliteration | Tanzil (`en.transliteration`) or QUL | The "same reading in Latin letters" you asked for; QUL also has a word-level one if we later do word-by-word |

Seven translations plus transliteration is roughly 12 to 16 MB uncompressed in the database, less once SQLite is compressed inside the APK and IPA. Others can ship as downloadable packs later from the same data repository as the audio.

### 3.3 Footnotes

Saheeh International and several others carry footnotes. Tanzil strips them; QUL keeps them in three formats. For slice 2, no footnotes: the text is cleaner, the database smaller, and nobody has asked. Revisit if a translation reads badly without them.

---

## 4. The reading experience

### 4.1 Layout options

**A. Ayah list, translation beneath (recommended).** Surah title block (name, Arabic name, meaning, ayah count, Makki or Madani), the basmala set as its own line, then one block per ayah: Arabic right-aligned in the Mushaf font, the ayah number as the roundel inside the text, transliteration in a quiet grey if enabled, translation in the app's caption style. Tap an ayah for actions: play from here, bookmark, share, copy. This is what Quran.com, Tarteel and most readers do, and it is the only layout in which a translation and a transliteration both sit naturally.

**B. Mushaf page mode.** The 604 pages exactly as printed, using QUL's layout data and either the QPC page fonts or Digital Khatt. Beloved by memorisers, useless for translations, and heavy: fonts, layout tables, and a justification problem. A later addition, offered as a toggle from the same surah screen, not slice 2.

**C. Side-by-side columns.** Arabic and translation in two columns. Works on tablets and desktops, cramped on phones. Not for us.

### 4.2 What the reader screen carries

- Surah picker: list of 114 with Arabic name, transliterated name, meaning, ayah count, revelation place; jump by juz; search box that searches surah names and ayah text.
- Reader: the ayah list above; sticky surah header; a small "juz · hizb · page" caption; continues into the next surah at the end.
- Settings on the screen itself: Arabic size, translation, transliteration on or off. Kept in the sheet the sound picker already uses, not in Settings.
- Last read: one position, restored on return; a "Continue reading" card at the top of the Quran tab.
- Bookmarks: a flat list with the ayah's first words and the surah name.
- Sharing: ayah text plus translation plus reference as plain text.

### 4.3 What it deliberately does not carry in slice 2

Tafsir beyond Muyassar, word-by-word, tajweed colouring, mushaf pages, khatm plans and streaks (slice 4), notes, highlights in colours, night reading themes beyond the two we have.

### 4.4 Arabic-first details already decided by the rest of the app

The reader inherits Manrope for Latin, the OS face for Arabic UI, one accent, hairline cards, no ripples, and the two themes. The Mushaf font is used only for Quran text, never for UI. Under an Arabic UI the transliteration line is off by default and the translation defaults to Muyassar; under English it defaults to Saheeh International with transliteration off.

---

## 5. Audio

### 5.1 Where recitations can legally come from

| Source | Terms as written | Fit |
|---|---|---|
| **Islamic Network / alquran.cloud** (`cdn.islamic.network`, the everyayah corpus) | "Recitations are licensed to us by the reciters or their estates" and are "licensed for free, non-commercial redistribution at the bitrates we publish"; copyrights remain with the reciters, who may ask for removal; credit the reciter; "cache aggressively at your own edge" | **The only source with a written redistribution licence.** Non-commercial fits. "At the bitrates we publish" means we must host their files as they are, not re-encode them |
| **everyayah.com** | The largest per-ayah archive (44+ reciters, 32 to 192 kbps, per-ayah MP3s with timing files), mirrored on the Internet Archive; no licence stated anywhere on the site | Practically the same files as the row above; treat the Islamic Network terms as the licence and everyayah/archive.org as the download mirror |
| **QUL / Quran Foundation** (Quran.com's recitations, 133 resources, 59 with word timestamps) | Developer terms permit audio inside an app experience but cap caching at one week and forbid redistribution as data | Streaming with a weekly refresh is allowed; an offline library we host is not. The **segment timestamps** for word highlighting are a genuine asset if we ever want word-level highlighting for reciters we source elsewhere |
| **mp3quran.net** | Surah-length files, 230 reciters, signed agreements with reciters; one page says personal and educational use, the contact page says everything may be copied | Contradictory. Ask before relying on it |
| **King Fahd Complex audio** | Their own recordings (Ali Al-Hudhaify, Ibrahim Al-Akhdar and others), distributable unmodified | A clean second source for two classical reciters |

**Recommendation:** source from the Islamic Network corpus, credit each reciter by name in the Attribution screen and in the player, keep the files at their published bitrate, and send one courtesy email to the Islamic Network team saying what Taqwa is and which reciters it carries. If we want Alafasy specifically, his own foundation has historically allowed free distribution but an email is cheap insurance.

### 5.2 Sizes, from the Internet Archive mirror tonight

Full Quran, per-ayah MP3, one reciter:

| Reciter and bitrate | Size |
|---|---|
| Husary murattal 64 kbps | 1.23 GB |
| Husary murattal 128 kbps | 2.47 GB |
| Alafasy (standard set) | 865 MB |
| Abdul Basit murattal | 906 MB |
| As-Sudais | 612 MB |
| Maher Al Muaiqly 64 kbps | 614 MB |
| Minshawi murattal 128 kbps | 1.67 GB |
| Hudhaify 32 kbps | 332 MB |
| Ghamdi 40 kbps | 447 MB |

So a reciter is 0.6 to 1.3 GB at the bitrate we are allowed to redistribute. That settles two things: audio is never bundled, and downloads are per surah (Al-Baqarah is about 100 MB at 64 kbps; most surahs are under 5 MB).

### 5.3 Hosting at zero cost

| Option | Cost | Limits | Verdict |
|---|---|---|---|
| **GitHub Releases on a public `taqwa-data` repository** | Free | 2 GiB per file, 1,000 files per release, no stated bandwidth or total-size limit; served through a CDN | **Recommended.** One release per reciter, one zip per surah (114 assets), plus a JSON manifest with sizes and checksums. The data repository can be public even while the app repository stays private; it holds only data and attributions |
| **Play Asset Delivery + Apple-hosted Background Assets** | Free | Play: 1.5 GB per pack, 30 GB on-demand total, non-game apps allowed, packs may be evicted by the user. Apple: 200 GB per app, 200 packs, but Apple-hosted packs need **iOS 26**; On-Demand Resources (iOS 16 to 25) are deprecated, capped at 512 MB per pack, and purgeable | Attractive later, especially on Android; not a single mechanism across our iOS 16+ floor. Not for slice 3 |
| **Cloudflare R2 free tier** | Free up to 10 GB stored, egress free | Needs an account and a card on file even at zero | A fallback if GitHub ever objects; not first choice |
| **Stream straight from `cdn.islamic.network`** | Free to us | Third-party availability; they ask for aggressive caching | Fine as a streaming fallback before a surah is downloaded, and a good first-iteration shortcut; not the offline story |

### 5.4 Playback design

- **Per-ayah files, played as a queue.** Media3's ExoPlayer plays a playlist of local files gaplessly; AVQueuePlayer does the same on iOS. Per-ayah files make "repeat this ayah", "repeat this range", "play from here" and highlighting trivial, at the cost of ~6,236 small files per reciter on disk, which is why downloads are per-surah zips extracted into a per-reciter folder.
- **Highlight and follow.** The current ayah's block takes the accent; the list scrolls to keep it visible unless the user has scrolled away, in which case a small "return to playing ayah" pill appears.
- **Background and lock screen.** Android: a `MediaSessionService` with the standard media notification. iOS: the audio background mode, `MPNowPlayingInfoCenter` and remote command handlers. Both show surah name, reciter, ayah number and the Taqwa mark.
- **Controls.** Play/pause, previous and next ayah, repeat (off, ayah, surah), a sleep timer (off, 15, 30, 60 minutes, end of surah). Speed control is deliberately absent; recitation is not a podcast.
- **Downloads.** Per surah, per reciter, resumable, with a "download whole Quran for this reciter" action that queues all 114 and shows the total size first. Wi-Fi only by default with an override. Deleting is per reciter or per surah from a storage screen.
- **Adhan overlap.** A prayer notification sound must not fight a playing recitation: pause recitation for the notification's duration, then resume. This is a small but real detail that most apps get wrong.

### 5.5 Player implementation

Own `expect`/`actual`, as with location, sensors and notifications. The KMP player libraries (KMedia, KMP-Player, mediamp) are young, none of them handles the media notification and lock-screen controls the way we need on both platforms, and the actual code is about two files per platform. Media3 is already the Android standard; AVQueuePlayer is a few hundred lines with the now-playing centre.

---

## 6. Data and storage

- **One SQLite database**, built by a script in `tools/` from Tanzil downloads and QUL exports, committed as a build artefact under `shared/src/commonMain/composeResources/files/` (or the platform assets), copied to app storage on first launch. Read-only. Roughly 15 to 25 MB.
- **Tables:** `surah` (metadata, names in Arabic and Latin, revelation place, page and juz starts), `ayah` (surah, number, Uthmani text, search text, juz, hizb, page, sajdah flag), `translation` (id, language, name, translator, licence, attribution), `ayah_translation` (translation id, surah, ayah, text), `transliteration`, and — in the original plan only — an FTS5 virtual table over the search text; that table was dropped in 2b because Android's SQLite has no FTS5.
- **SQLDelight 2.x** for typed queries in `commonMain`; it runs on Android's bundled SQLite and on iOS's native SQLite. (It also parses FTS5 virtual tables in `.sq` files, but Android's SQLite cannot execute them — see 2b.)
- **User data** (last read, bookmarks, chosen translation, font size, downloaded audio index) lives beside the existing settings in DataStore, not in the read-only database.
- **Audio files** in the app's files directory under `quran/audio/<reciter>/<surah>/<ayah>.mp3`, with a small index in DataStore so the UI can show what is available without scanning the disk.
- **Widget mirror** is untouched by slice 2. Slice 3 might later add "now playing" to the widgets; not planned.

---

## 7. Architecture inside the app

- `shared/feature/quran/` — surah list, reader, search, bookmarks, reading settings; `QuranRepository` over the SQLDelight database.
- `shared/quran/` (domain) — `Ayah`, `Surah`, `Translation`, `ReadingPosition`, text normalisation for search, the ayah-marker rule per font.
- `shared/audio/` — `RecitationPlayer` (`expect`), `RecitationDownloader` (Ktor client with per-platform engines), `RecitationCatalog` (the manifest from the data repository, cached, refreshed at most daily and only when audio is opened).
- `TaqwaTabBar` gains its third tab, **Al Quran**, with the open-book glyph from the icon sheet; `Tab.QURAN(Screen.Quran)`; the settings screen gains a "Quran" group (translation, transliteration, downloads and storage).
- New dependencies: SQLDelight runtime and drivers, Ktor client core plus OkHttp and Darwin engines, Media3 (Android only). All open source, all free, none of them phones home.

---

## 8. Proposed slicing

| Slice | Contents | Ships as |
|---|---|---|
| **2a** | Font spike; data pipeline and bundled database; Al Quran tab; surah list; reader with Arabic, translation, transliteration; reading settings sheet; last-read; attribution updates | A complete offline Quran reader |
| **2b** | Search (Arabic and translation), bookmarks, share, juz navigation, "continue reading" card | Same app, faster to navigate |
| **2c** (optional, later) | Mushaf page mode with QUL layouts and Digital Khatt or QPC fonts | A toggle on the reader |
| **3a** | `taqwa-data` repository with two or three reciters; catalog; per-surah download and delete; player with queue, highlight, follow, background and lock-screen controls; adhan pause | A listening Quran, offline once downloaded |
| **3b** | Repeat modes, sleep timer, whole-Quran download queue, storage screen, streaming fallback before download | Polish |

Each is shippable on its own, and 2a is where the design review with device screenshots happens, exactly as slice 1 was done.

---

## 9. Risks and how each is retired

| Risk | Retired by |
|---|---|
| Compose on iOS mispositions Quranic marks in the chosen font | The §2.4 spike on real devices, first |
| A translation's terms turn out stricter than Tanzil's summary | Every bundled text carries its licence text in the database and the Attribution screen; anything doubtful is left out until answered |
| A reciter or estate objects | Files are downloaded, not bundled, so a reciter can be withdrawn from the catalog without an app update; credits are prominent |
| GitHub's tolerance for a multi-gigabyte data repository | Stay within the written limits (2 GiB per file, no bandwidth cap); the Cloudflare R2 fallback exists |
| Database size pushing the app past the 200 MB mobile-data warning | Text is ~25 MB; the app stays near 60 MB |
| Long-list performance with the Mushaf font | `LazyColumn` per surah (at most 286 items) and text measured once per size; the spike measures scroll on the LoopPhone |
| Search feeling wrong in Arabic | Normalisation rules are unit-tested against known queries ("الحمد" must find Al-Fatiha with or without harakat, with or without hamza on the alef) |
| GPL code plus non-free data | Data is not code; each dataset keeps its own licence; the repository documents that split, as the README already does for the adhan recordings |

---

## 10. Questions for you

1. **Translations:** is the launch set in §3.2 right for you, and which Urdu (Junagarhi or Maududi)? Should I email Al-Furqaan about The Clear Quran?
2. **Reciters:** the three proposed (Husary, Alafasy, Abdul Basit), or others you would want from the permitted list (Sudais, Minshawi, Shatri, Maher Al Muaiqly, Hudhaify, Ghamdi are all there)?
3. **Font:** happy with KFGQPC Hafs as the single reading font, pending the device spike, or do you want the Amiri Quran look instead? The comparison page shows both.
4. **Transliteration default:** off unless turned on, or on for English users?
5. **Mushaf page mode:** confirm it is out of slice 2 and a later toggle.
6. **Data repository:** a public `MohamedAbulgasem/taqwa-data` repository holding audio zips and the manifest, while the app stays private. Any objection to that being public?
7. **Emails:** shall I draft the two courtesy emails (Islamic Network, Alafasy's foundation) for you to send from your address?
8. **Tab name:** "Al Quran" as in the mockup, or "Quran"? The Arabic is "القرآن" either way.

If these come back answered, the next step is the writing-plans pass for slice 2a, then the font spike as its Task 1.

---

## Sources consulted

- Tanzil text licence and download options: https://tanzil.net/docs/text_license and https://tanzil.net/download/ ; translations and their non-commercial terms: https://tanzil.net/trans/
- QUL resource index, FAQ and documentation: https://qul.tarteel.ai/ , https://qul.tarteel.ai/faq , https://qul.tarteel.ai/docs , mushaf layout model https://qul.tarteel.ai/docs/mushaf-layout , QPC Hafs font https://qul.tarteel.ai/resources/font/245 (file: https://static-cdn.tarteel.ai/qul/fonts/UthmanicHafs_V22.ttf)
- KFGQPC font licence summary: https://scancode-licensedb.aboutcode.org/kfgqpc-uthmanic-script-hafs.html ; font mirror with licence pointer: https://github.com/nuqayah/qpc-fonts
- Amiri and Amiri Quran: https://github.com/aliftype/amiri , Google Fonts listing https://fonts.google.com/specimen/Amiri+Quran
- Digital Khatt: https://github.com/DigitalKhatt , old Madina font (OFL): https://github.com/DigitalKhatt/oldmadinafont
- Quran Foundation developer terms and audio API: https://api-docs.quran.foundation/legal/developer-terms/ , https://api-docs.quran.foundation/docs/sdk/javascript/audio/ , font rendering tutorial https://api-docs.quran.foundation/docs/tutorials/fonts/font-rendering/ ; Quran.com terms: https://quran.com/terms-and-conditions and about page https://quran.com/about-us
- Islamic Network / alquran.cloud terms: https://alquran.cloud/terms-and-conditions
- everyayah reciter list: https://everyayah.com/recitations_ayat.html ; Internet Archive mirror with sizes: https://archive.org/details/quran-every-ayah
- Quranic Arabic Corpus licence: https://corpus.quran.com/license.jsp
- Google Play size limits: https://support.google.com/googleplay/android-developer/answer/9859372 ; Play Asset Delivery: https://developer.android.com/guide/playcore/asset-delivery
- Apple-hosted asset pack limits: https://developer.apple.com/help/app-store-connect/reference/app-uploads/apple-hosted-asset-pack-size-limits/ ; On-Demand Resources limits: https://developer.apple.com/help/app-store-connect/reference/app-uploads/on-demand-resources-size-limits
- GitHub Releases limits: https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases
- Compose Multiplatform 1.12 text and Skia notes: https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html
- KMP player libraries surveyed: https://github.com/moonggae/KMedia , https://klibs.io/project/RufenKhokhar/KMP-Player , https://github.com/open-ani/mediamp
- SQLDelight with FTS5 in KMP: https://fedetorresdev.com/mastering-lightning-fast-mobile-searches-in-kmp-apps-a-guide-for-sqlite-and-fts/

---

## 11. Decisions taken on 7 September (morning review)

Answers to §10, recorded so the plan can be written against them.

1. **Translations:** the §3.2 set is confirmed; Urdu is **Junagarhi**. An email to Al-Furqaan about The Clear Quran is to be drafted (see the emails file beside this document).
2. **Reciters at launch:** **Mishary Alafasy, Abdul Basit (murattal), Maher Al Muaiqly.** Husary drops out of the launch set.
3. **Font:** **KFGQPC Uthmanic Hafs**, pending the device spike.
4. **Transliteration:** off by default, switched on from the reader's own sheet.
5. **Mushaf mode is in slice 2, not later.** A full-Arabic reading mode with no translation, as close to reading from a book as we can make it, is a core requirement for Arabic readers. The design round offers two forms (a justified book scroll and true printed pages); the book scroll ships with the reader and the printed pages follow as soon as the layout data is in.
6. **Data repository:** a public `taqwa-data` repository for audio and the manifest is fine while the app repository stays private.
7. **Emails:** yes, draft them for Mohamed to send.
8. **Tab name:** **"Quran"** (Arabic "القرآن").

Also from the same review: the tab bar icons are too small at 16 dp, and the unselected grey reads as disabled on the light theme. Both are addressed in the Quran tab design round: 22 dp glyphs and the secondary text colour for unselected tabs.

Design round for the tab: https://claude.ai/code/artifact/5748bf9a-e0c0-492f-8205-93604f6efb68

## 12. Font spike result (7 September, evening)

Run on a throwaway branch (`spike/quran-font`, worktree `Taqwa-spike`, not merged) on the Samsung S23 (dark), the Pixel 8 Pro emulator (light), the iPhone 12 (dark) and the iPhone 17 Pro simulator (light and dark), with Tanzil Uthmani text for Al-Fatiha, 2:255, 2:282 and 2:1 to 2:5 at 22, 28 and 36 sp. Full report and about seventy screenshots in the session scratchpad (`font-spike-report.md`, `spike-*.png`).

- **KFGQPC Uthmanic Hafs is confirmed as the reading font.** Every mark sits correctly on all four devices; Android and iOS render identically (Skia plus HarfBuzz on both). Crisp at 36 sp on the 600 dpi S23. The 286-row list scrolls at p99 9 to 10 ms on the S23.
- **One text fix is required.** Tanzil encodes the silent-alef sign as U+06DF (small high rounded zero); this font draws it as a full-height inline ring that splits the word (visible in 2:282 and 2:5). Mapping U+06DF to U+0652 in the data pipeline renders the correct small circle. This is a pipeline rule, not a renderer hack.
- **Ayah marker rule for Hafs:** bare Arabic-Indic digits preceded by a non-breaking space (U+00A0), so the roundel never wraps onto a line of its own. Do not use U+2060; neither font has the glyph.
- **Line height:** 2.0 times the font size is safe for both fonts; Hafs could go to about 1.8. Amiri Quran must not go below 2.0 because its pause marks come within a few pixels of the line above.
- **Compose defaults were enough:** no `softWrap`, `platformStyle` or `includeFontPadding` changes, no clipping of tall marks.
- **Amiri Quran** also renders the text correctly as-is on both platforms and remains the tested fallback.
- **Data note:** the alquran.cloud API prefixes 2:1 with the basmala; the pipeline strips it (Tanzil's own files do not have this issue).
