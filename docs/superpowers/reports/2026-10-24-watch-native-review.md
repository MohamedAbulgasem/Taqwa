# Watch words: the native review (plan 5 Task 11)

6 November 2026, Lane P, worktree `watch-feed`, base `f601d14d` (watch-companion with P5.6 landed). Plan 5 Task 11, built up to M8 as the Lead's erratum 2 asks. Mohamed's own reading of the English and the Arabic is M8. Nothing here marks a phrase approved for him: every "draft, to native review" marker stays in its file until he answers, the five reviewed languages' markers included. He answered on 6 November 2026. Steps 4 and 5 then ran on `watch-feed` from `6f8e2cb7` (watch-companion with Ruling 71 items 2, 3 and 4a landed): his choices are applied, and every marker is gone. See "Where it stands" and "Mohamed's answer, as applied".

## Where it stands

- **Step 1, the inventory:** done. There are 817 rows in six languages, at the end of this report. Every marker that `git grep "draft, to native review" -- ':!docs/superpowers'` finds is in a row: 347 marker lines in 66 files, 0 unlisted. That is 341, plus the six App Store What's New blocks' own markers (Ruling 71 item 4b). Two kinds of hit are listed apart, below the step, because they hold no phrase.
- **Step 2, the five reviews: done, and closed by Ruling 71 item 1.** Five independent native readers, one fresh agent for each language, read the five tables whole again on 6 November 2026.
  - They returned 3 musts, 45 shoulds and 44 nits. Every must and should is applied, two of the Indonesian ones in other words, and each says why: the tilt notice in words that fit the Qibla page, and the iPhone app by Apple's own name.
  - The nits are left as notes. See "Step 2: the five independent readers" below.
  - The in-session reviews that follow stand as a first pass.
  - Every must and every should is applied: 5 musts and 20 shoulds, made as 57 edits in 26 files. Each one is listed under its language, with its reason.
  - The nits are left as notes.
  - Three checks were written first and seen red. Each turned green with the corrections (Step 4).
  - **Review round 1** found that four of the "Apple's own names" were not Apple's, and that Apple-only rows had lost Apple's word for the face. Fix round 1 checked every Apple feature name against Apple's localized guides, and fixed the names (see "Review round 1 and fix round 1" below). Two shoulds were reverted and three corrected. Four rows shown only for the Apple Watch took Apple's word for the face, two of them back to what they said before this task. Three more checks hold Apple's names, each seen red first.
- **Step 3, Mohamed's reading list (M8): answered on 6 November 2026.** The Lead posted the list as a reading sheet. Mohamed answered in chat, verbatim: "M8: approved with your recommendations (A1–A7, E4). Changes: none."
  - So every recommendation the sheet gave holds. The eight names, A8 and the English (E1 to E116) are approved as they are, and A7 changes nothing.
  - A1 to A6 and E4 are applied, each with the check written first and seen red. Each is listed with its files and lines under "Mohamed's answer, as applied" below.
  - The Lead's own call on the readers' note: the English keeps "the Watch app", as Mohamed read it.
- **Step 4, the checks:** all green again after M8, on the corrected tree with every marker removed (see "Step 4 after M8" below).
  - The marker grep, `git grep -n "draft, to native review" -- ':!docs/superpowers'`, printed 347 lines in 66 files before this step. It prints nothing now, so the cut gate's M8 is met.
  - No short form of the Qibla dial changed in any language, so Ruling 67's note had nothing to regenerate.
- **Step 5, the commit:** "Strings: Mohamed's reading of the English and the Arabic applied, and every draft marker removed (M8)", on `watch-feed`. Nothing is pushed, and the version is not bumped (1.6.0 (42) is the Lead's cut).

How the reviews were run:
- This run had no tool for starting subagents. So the five native reviews were done in this session, one language at a time and each a separate pass.
- Each pass read the whole of its table against the English and against the app's existing words in that language. These are the phone's Compose strings, its tasbeeh, Qibla and prayer names, and its earlier store copy.
- Product and platform names stay in Latin script: Taqwa, Wear OS, Galaxy Watch, Apple Watch, Digital Crown, TalkBack and VoiceOver.
  - One exception, which is the phone's: Arabic, Urdu and Bengali write the app's own name in their script, تقوى, تقویٰ and তাকওয়া. That is the phone's `app_name` in those languages and how all its earlier text there writes it, and the watch writes the phone's own strings (spec 1.3). French, Turkish and Indonesian keep Taqwa, as the phone does.
- Each finding is ranked must, should or nit, with its reason.
- Apple's features (Smart Stack, Advanced Data Protection, the watch face and its complications) take the name Apple's own localized guide gives them, on text about the Apple Watch or the iPhone. Text about Wear OS takes the watch's and Google's words.
  - The first round named these from memory, and review round 1 found four of the names wrong. Since the fix round, each is checked against Apple's localized guide on support.apple.com, read on 6 November 2026: the Apple Watch User Guide's page `guide/watch/apdecf142fb9` with its contents, and the iCloud page `108756`, in fr-fr, tr-tr, id-id and ar-sa.
  - Apple's Urdu and Bengali guides (ur-in, bn-in) are in English, so those two keep the English names.
- **The Lead has ruled on Step 2: Ruling 71 item 1, option (b), on 6 November 2026.** Five independent readers then read the tables (see "Step 2: the five independent readers" below), and reviewers do not raise Step 2's method again. What follows is the record up to that ruling.
- **Step 2 was not run as the plan writes it, and the Lead rules on it** (review round 1, Importants 1 and 3). The plan asks for five native reviewers, one subagent each. These reviews were written by the same agent that applied them, and after M8 the five languages get no other reading before the cut. The ruling is either to accept these reviews, or to have five independent readers go over the five tables. Review round 1's own reading of the watch's interface words in all five, by a separate agent, found no must. Its other point, Apple's names, is fixed below.
  - Review round 2 raised it again on both lenses, and fix round 2 had no subagent tool either. So it is still open, and no ruling is made or implied here. What the separate readings have covered so far, and the points still open for a native reader, are under "Review round 2 and fix round 2" below.
  - Review round 3 raised it a third time on both lenses, and fix round 3 had no subagent tool either. It is still open. Round 3's spec lens read the rows round 2 listed as unread and found no must. What is left unread, and round 3's nits, are under "Review round 3 and fix round 3" below.
  - Review round 4 raised it a fourth time on both lenses, and fix round 4 had no subagent tool either. Round 4's spec lens read the last rows that round 3 listed as unread, and found no must and no should. So every row has now been read by an agent other than the one that applied the corrections, and only the Lead's ruling is left. See "Review round 4 and fix round 4" below.

What counts as new:
- Plan 5 says "since plan 2's base" (`976a7042`). Since then, `main` has been merged into the branch four times (MM1 to MM4), with the 1.4.0 and 1.5.0 strings. Those were reviewed on `main`.
- So the inventory lists every key, paragraph or line that differs from the `main` merged last (`bcafdfa9`). That is exactly the watch's own words.
- The Compose `values-in` twin is checked equal to `values-id` (no difference), so its rows are `values-id`'s.
- Words that are the phone's own text, the ones the files mark "the phone's own words", are listed unmarked, so the tables show them beside the drafts.

The scratch tools are `inventory.py` and `render.py` in the session scratchpad, and they are not committed. They rebuild these tables and run the "nothing unlisted" check. They last only as long as that scratchpad does. A later session, or a restart that clears it, does not have them, so after M8's corrections they can be run again only while the scratchpad is still there.

### Step 1: what the grep finds that holds no phrase

- `wearApp/src/test/.../SystemStringsTest.kt:107` and `.../face/FaceStringsTest.kt:124`: `DRAFT = "draft, to native review (plan 5)"`. These are the rule holders that require each draft to be marked.
  - **They must change in the same commit that removes the markers at M8**, or both tests fail (P3.4's round-1 minor).
- `watchcore/src/commonTest/.../Fixtures.kt:48`: the Arabic fixture's KDoc ("a draft, to native review (plan 5), as the phone writes it"). It goes when the Arabic is read.

## Step 2: the five reviews

Where a correction changes a privacy line, its row in `tools/privacy-check.py` was changed first. The gate failed on exactly those 8 rows, and the file then made it pass. Where it changes the feed's words, the golden feed `watchcore/vectors/phone/phone-capetown-ur24.json` was regenerated (`-Ptaqwa.updateVectors`). Where it changes a caption the Qibla page draws over the dial (Urdu's `holdLevel`), the ink model was regenerated (`tools/watchface/qibla-ink.py`, then `--check`), and `QiblaFitTest` passes. No countdown changed, so `tools/watchface/phone-forms.json` is unchanged (FaceFormsTest).

The three checks written first, each seen red before the corrections:
1. `WatchWordsTest.theTwoWatchesHintsNameTheTapAlike` (Ruling 60 item 4).
   - The rule: the bezel hint and the crown hint differ only in what is turned.
   - Red on Urdu (ٹیپ کریں against تھپتھپائیں) and Bengali (চাপ দিন against ট্যাপ করুন). Green now.
2. `site/build.py --check`, `check_watch_answer`.
   - The rule: each language's support answer `#watch` holds the watch's own `face_add` and `open_taqwa_on_phone`, as `wearApp`'s resources write them.
   - Red on Turkish, Indonesian, Urdu and Bengali, which quoted a face label the watch does not show. Green now.
   - Arabic is reported as a note, not a failure (`WATCH_ANSWER_AT_M8`). Mohamed chooses the label or the quote (M8 point A2).
3. `tools/privacy-check.py`: the 8 rows whose new text the review corrected. Red, then green.

### French

Applied:
- **should** `PRIVACY.fr.md`, Backups: « qui nomme votre ville » → « qui indique votre ville ». A data copy "indicates" a city; « nommer » reads oddly. Row `3-4d-policy-backups`.
- ~~**should** `PRIVACY.fr.md`, Backups: « la protection avancée des données » → « la Protection avancée des données ».~~ **Reverted in fix round 1.** Apple's French page `108756` writes « la protection avancée des données » in lower case in its prose (« Activer la protection avancée des données pour iCloud »). The capital is only a Settings label's first letter. Same row.
- **should** `PRIVACY.fr.md`, Backups: the two new « ; » had a plain space before them. They take the policy's own U+202F, as every other « ; » there does. The gate normalises spaces, so no row changed.
- **should** `site/pages/fr/support.html`, `#watch`:
  - « vient avec l’application iPhone » → « est fournie avec l’application iPhone »: « vient avec » is a calque.
  - « Le cadran Taqwa demande Wear OS 6 » → « nécessite ».
  - « maintenez le doigt sur votre cadran …, puis maintenez-le de nouveau » → « appuyez longuement sur votre cadran …, puis appuyez de nouveau longuement dessus ». These are the watch's own words (`face_choose_it`). The old « le » could also be read as the finger.
- **should** `site/pages/fr/home.html`, On your wrist: « la Smart Stack » → « le défilement intelligent ». *Fix round 1:* the first round wrote « la Pile intelligente », which Apple does not use. Apple's French guide calls the Smart Stack « Défilement intelligent » (« Afficher des widgets opportuns dans Défilement intelligent sur l’Apple Watch »), and « le défilement intelligent » in its prose.

Nits (left as they are):
- `underAMinute` « d’ici une minute » means "within a minute", not "in under a minute". This is the Lead's call 7: the longer form does not fit the Moon's curve. It stays.
- `bezelHint` and `crownHint` « … ou touchez » name no object, as the English does not.
- `complication_day_dial` « Cadran Taqwa : cadran du jour » has U+00A0 before « : », where the phone uses U+202F. The name is hidden from the pickers.
- The listing's « Joumou’a » is spelt « Joumouʿa » in the app.
- The listing's cut drops "or a recording from your phone" from the notification bullet. That cut is Mohamed's approval of Task 10's cut.
- « lui donne » could be « lui transmet » in « Votre montre ».
- `prayer_qibla_detail` « … km de La Mecque » (Ruling 62 item 4, Ruling 63 item 3) reads naturally and stays. « à 6 600 km de La Mecque » would put « · à » after the bearing.

### Turkish

Applied:
- **must** `site/pages/tr/support.html`, `#watch`: the answer quoted “Taqwa kadranını ekle”, but the watch's button says "Taqwa saat yüzünü ekle" (`face_add`). It also said "saat kadranınıza basılı tutup", where the watch says "Saat yüzünüze dokunup basılı tutun". The paragraph now uses the watch's words: “Taqwa saat yüzünü ekle” seçeneğine dokunun, saat yüzünüze dokunup basılı tutun, "Taqwa saat yüzü Wear OS 6 gerektirir", "Her saat yüzünde".
- **should**: one word for each watch's face. The Galaxy watch and the phone's Android Settings row say "saat yüzü". The site's home row, the Play listing's wrist paragraph and both Play release notes said "kadran", and each now says "saat yüzü"/"saat yüzüne". "Kadran" stays where it means a dial ("gün kadranı").
  - *Fix round 1:* the first round also changed the App Store paragraph to "saat yüzünüzün". But Apple's own Turkish word is "saat kadranı" ("Saat kadranına komplikasyonlar ekleme"). So the text about the Apple Watch takes Apple's word: the App Store paragraph is back to "saat kadranınızın", and the iPhone's `settings_add_to_apple_watch_failed` now says "Saat kadranı eklenemedi." (it said "Saat yüzü" before this task). No screen shows both rows: the iPhone's Watch section holds only the Apple row.
- **should** `face_about` and `WatchWords` FACE.about: "Sıradaki namaz" → "Sonraki namaz". The phone (`prayer_a11y_prayer_next`), the Apple widget and the store copy all say "Sonraki namaz". The tasbeeh's "Sıradaki:" (the next phrase in a set) stays.
- **should** `watchface` `slot_left`/`slot_right`: "Sol/Sağ özellik" → "Sol/Sağ komplikasyon". This is the word the store copy and the support answer use.
- **should** `settings_watch_install`: "Saatinize yükleyin" → "Saate yükle". The section's other rows ("Taqwa saat yüzünü ekle", "Apple Watch’a ekle") and Settings' rows ("Taqwa’yı paylaş") use the bare imperative.
- **should** `PRIVACY.tr.md`, Backups: "Gelişmiş Veri Koruması'nı" → "İleri Düzey Veri Koruma'yı". *Fix round 1:* the first round wrote "Gelişmiş Veri Koruma'yı", which is not Apple's name either. Apple's Turkish page `108756` is "iCloud İçin İleri Düzey Veri Koruma'yı açma", and its Settings switch is "İleri Düzey Veri Koruma'yı Aç". Row `3-4d-policy-backups`.
- **should** `site/pages/tr/home.html`: "Smart Stack" → "Akıllı Gruplama". *Fix round 1:* the first round wrote "Akıllı Yığın", which Apple does not use. Apple's Turkish guide's page is "Apple Watch’taki Akıllı Gruplama’da uygun araç takımlarını görme".

Nits:
- "Google onun verilerini" (the About row, the home row and lede, the listing bullets) is grammatical, but "onun" for the app is heavy. "uygulamanın verilerini" would be lighter. These are eight privacy rows, so the line was left as it is.
  - *Review round 3* adds that "onun" can be read as the watch's data, the reading the Bengali should removed. It stays a nit, and it is left because Step 2 leaves every nit as a note, not because these are privacy rows (see "Review round 3 and fix round 3").
- "kutucuk" for Tile is Samsung's word. Google says "kart". Kept, since the release targets Galaxy watches.

### Indonesian

Applied:
- **must** (with `site/build.py`'s new check): the support answer quotes “Tambahkan tampilan jam Taqwa”, as Wear OS and the rest of the Indonesian copy (the listing, the release notes, the support page and `complication_day_dial`) name a watch face. But the watch itself said "wajah jam": `face_*`, `WatchWords` FACE, the phone's `settings_watch_face` and the Apple row. The watch's own five phrases and the phone's Android row now say "tampilan jam", Google's own word ("Tampilan jam yang berbeda …" on the Wear OS help page 6140435), in `values-id` and its `values-in` twin. So the Galaxy watch and its phone row use one word, and the support page names the real button.
  - *Fix round 1:* the first round also changed the iPhone's `settings_add_to_apple_watch_failed` to "Tampilan jam". That row is shown only for the Apple Watch, and Apple's Indonesian word is "wajah jam" ("Menambahkan komplikasi ke wajah jam"). It is back to "Wajah jam tidak dapat ditambahkan.", in `values-id` and `values-in`. The App Store paragraph, which said "komplikasi tampilan jam Anda" since Task 10, now says "komplikasi wajah jam Anda".
- **should** `watchface` `slot_left`/`slot_right`: "Detail kiri/kanan" → "Komplikasi kiri/kanan". This is the store copy's word.
- **should** `settings_watch_unreachable`: "Tidak dapat menjangkau jam tangan Anda" → "Tidak dapat terhubung ke jam tangan Anda". "menjangkau" is a physical reach, and this is a connection.
- **should** the turns, which are spoken to TalkBack: "Belok kanan/kiri sekitar %1$s derajat" → "Putar badan ke kanan/kiri sekitar %1$s derajat". "Belok" is for turning on a road, and here the wearer turns where they stand.
- ~~**should** `site/pages/id/home.html`: "Smart Stack" → "Tumpukan Pintar".~~ **Reverted in fix round 1.** Apple's Indonesian guide keeps the English name ("Melihat widget tepat waktu di Smart Stack di Apple Watch"), so the home row says "Smart Stack" again.

Nits:
- "dial hari" in the hidden day-dial names is not Indonesian.
- "Google dapat membawa datanya" is a calque of "carry". "meneruskan" would be more natural, but these are privacy rows, so the line was left as it is.
  - *Review round 3* adds that "datanya" can be read as the watch's data, the reading the Bengali should removed. The policy's opening has the same "datanya". It stays a nit, and it is left because Step 2 leaves every nit as a note, not because these are privacy rows (see "Review round 3 and fix round 3").
- `underAMinute` "kurang dari semenit" has no "lagi", by the Lead's call 7 for the Moon's curve.

### Urdu

Applied:
- **must** `site/pages/ur/support.html`, `#watch`: the answer quoted ”تقویٰ کا واچ فیس شامل کریں“, but the watch's button reads "تقویٰ واچ فیس شامل کریں". The quote is now the button's own words.
- **should** (Ruling 60 item 4) `crownHint`: "کراؤن گھمائیں یا تھپتھپائیں" → "کراؤن گھمائیں یا ٹیپ کریں". This is the bezel hint's word and the phone's own (`tasbeeh_hint` "… ٹیپ کریں").
- **should** `holdLevel` and the support answer: "اپنی کلائی سیدھی رکھیں" → "اپنی کلائی افقی رکھیں". "سیدھی" reads as "straight" (not bent). The compass needs the wrist level, and the phone's own line says "فون کو افقی رکھیں".
  - The ink model was regenerated with `qibla-ink.py`.
  - `QiblaFitTest` passes, 21/21, Ruling 62 item 5's tilt-notice geometry included.
- **should** the Apple Watch's update line (`TaqwaWatch` and `TaqwaWatchWidgets` `ur.lproj`): "اپ ڈیٹ کریں" → "اپڈیٹ کریں". This is the feed's and the Galaxy watch's spelling, so both watches say the line alike. The Tile's fit was measured on this spelling (`UpdateFitTest`, 11.457 em).
- **decided, unchanged** (Ruling 67 item 4, errata p5 #23): the turning dial draws "6,600 km" as "km 6,600" in right-to-left order. Read right to left, that is the number, then the unit, which is the natural Urdu order. It stays. "کلومیٹر" would fail the one-word unit's width, and "کلو میٹر" the one-word rule (errata p5 #25).

Recommended, not applied:
- (Ruling 64 item 6, errata p5 #19) In Urdu, the joins read aloud on the Apple Watch's Today, the Wear Tile and the phone's `prayerRowDescription` should be the Urdu comma "،", not ", ".
- The ruling settles Arabic and Urdu together, across all three places. So it is applied with Mohamed's Arabic answer (M8 point A5) as one change in Kotlin and Swift.

Nits:
- "قبلہ کی سمت" and "قبلے کی سمت" both appear (both are correct).
- "سیج" for the Sage colour is a transliteration.
- "موتی" (a noun) sits beside "کہربائی" (an adjective).

### Bengali

Applied:
- **must** `watchface` `slot_left`/`slot_right`: "বাঁ/ডান দিকের জটিলতা" → "বাঁ/ডান দিকের কমপ্লিকেশন". "জটিলতা" is "complication" in the sense of a problem or an illness. The store copy already says "কমপ্লিকেশন".
- **must** `site/pages/bn/support.html`, `#watch`: the answer quoted “তাকওয়া ফেস যোগ করুন”, but the watch's button reads "তাকওয়া ওয়াচ ফেস যোগ করুন". The quote is now the button's own words.
- **should** (Ruling 60 item 4) `crownHint`: "ক্রাউন ঘোরান বা ট্যাপ করুন" → "ক্রাউন ঘোরান বা চাপ দিন". This is the bezel hint's word and the phone's own (`tasbeeh_hint` "… চাপ দিন").
- **should** "its data" in six places: the About rows for Android and iPhone, the home lede and row, and both listing bullets.
  - "ঘড়ির তথ্য" said "the watch's data". The data is Taqwa's, sent to the watch.
  - The home row's "Google বা Apple এর তথ্য" could be read as "Apple's data".
  - Each now says "এর তথ্য Google …" ("its data"), as the policy's own opening does. Privacy rows `3-4f-about-online-*`, `3-4f-home-row`, `3-4g-home-lede` and `3-4l-listing-internet-*`.

Nits:
- `complication_day_dial` "তাকওয়া ঘড়ি: দিনের ডায়াল" could be "তাকওয়া ওয়াচ ফেস: …". The name is hidden from the pickers.
- "অ্যাম্বার" and "সেজ" are transliterations.
- *Review round 3:* the policy calls the watch "তাকে" twice, in "Your watch" ("আপনার ফোন তাকে দেয়") and in Backups ("আপনার iPhone তাকে … পাঠিয়েছে"). তাকে is for a person. For a thing, এটিকে or সেটিকে reads better, or ঘড়িকে, as "Your watch" already says a line later ("ঘড়িকে জানায়"). Both lines are privacy rows (`tools/privacy-check.py`).

### The rulings and errata this task answers

- **Errata p5 #2:**
  - The size tests ran with `FaceFormsTest`. `phone-forms.json` was regenerated and is unchanged, because no countdown changed.
  - `face-*.py --check` and `moon-fit.py` passed (below).
  - No correction lengthens a countdown.
  - *Ruling 71's readers* changed the Bengali countdowns (ঘঃ and মিঃ). `phone-forms.json` was regenerated with them, `scripts/face-gate.sh` passed its 12 checks again, `moon-fit.py` among them, and FaceFormsTest is green (see "Step 2: the five independent readers").
- **#6:** the notes check is keyed to the newest notes code (42, both files present), so no step expects a missing file.
- **#13 (Ruling 58 item 6):** accessibility round 2 has not merged (its branch holds only docs). WatchWords keeps the tasbeeh's spoken phrases and the turns, and they were reviewed here as drafts.
- **#16:** settled and applied (Urdu, Bengali).
- **#17:** French `prayer_qibla_detail` stays "de La Mecque".
- **#18:** the cut gate's grep is `git grep "draft, to native review" -- ':!docs/superpowers'`.
- **#19:** recommended for Urdu, and waits for Mohamed (A5).
- **#22, #23, #25:** the dial's short forms stay "km" in five languages and كم in Arabic. The Urdu order stays, and no Bengali no-break space is used.
- **#26 (Ruling 69 item 2):** the App Store Connect notes now say "credited under Settings › About Taqwa › Attribution & licences", and the Apple Watch line says it shows "Open Taqwa on your phone" until the iPhone app has run with it nearby. The file is exactly 4,000 bytes, the field's limit. The trims made room for this, and they are in the English list for Mohamed (E-notes).
  - The PRIVACY "Last updated" date moves at M10a, not here.
  - `privacy-check.py` gained one `KNOWN` entry for the new line: a quoted message, not a promise.
- **#30:** the Arabic update line's two spellings go to Mohamed (A1).
- **The Lead's 6 Nov note 4:** `1.6.0-42-appstore.txt` now carries a marker in its header: every block but English is a draft, to native review.
  - Play's two notes files are pasted whole into Play's fields, so they take no comment. Their rows are in the tables, marked "listed here".
  - *Ruling 71 item 4b:* each of the six non-English blocks now also ends with its own marker, a note in parentheses, "(A draft, to native review (plan 5 Task 11).)", as the listings mark theirs. So the cut gate's grep finds each block, 6 more lines than before. A note in parentheses is not pasted. `tools/store-listing-check.py` now skips it in a block, as it does in the listings' fields, and fails a block that holds only notes (two tests, each seen red first). The header says so.

## Step 2: the five independent readers (Ruling 71, 6 November 2026)

Ruling 71 item 1 chose option (b). The Lead's Wave 7 workflow ran five native readers, one fresh agent for each language. None of them had drafted or corrected these words. Each read its whole table, in the files on `watch-feed` at `84fda743`. It read the table against the English, against the app's own words in that language and against the platform's own terms, and returned musts, shoulds and nits, each with its reason.

This apply step, on the same head, applied every must and every should. Two were applied in other words, and each says why. The nits are left as notes. The in-session reviews above stand as a first pass. Where a reader disagrees with them, both views are recorded below, and the reader's is taken.

| Reader | Rows read | Musts | Shoulds | Nits | Applied as written | Applied in other words | Not applied |
|---|---|---|---|---|---|---|---|
| French | 141 | 0 | 12 | 9 | 12 | 0 | 0 |
| Turkish | 135 | 0 | 8 | 10 | 8 | 0 | 0 |
| Indonesian | 143 | 0 | 8 | 10 | 6 | 2 | 0 |
| Urdu | 133 | 1 | 9 | 8 | 10 | 0 | 0 |
| Bengali | 133 | 2 | 8 | 7 | 10 | 0 | 0 |
| **All five** | **685** | **3** | **45** | **44** | **46** | **2** | **0** |

What the readers found in common:
- Every draft cell in the tables is the text its file holds. The release notes' rows point at their block's tag line, and the text is on the lines after it.
- Every reader paired each approved privacy row in `tools/privacy-check.py` with its English and found the meaning exact, with Ruling 39's promise in every place it stands. So no must concerns privacy. The privacy shoulds below change wording only, and add nothing to what the policy says.
- Placeholders and their order survive in every row. Prayer names, Hijri months, digits and clocks follow the app's own rules.

How they were applied:
- There are 58 edits in 30 files, after the six privacy rows below.
- **The privacy rows, first.** Six approved rows took the readers' words in `tools/privacy-check.py` first: French `2-watch-lead-in`; Urdu `2-watch-lead-in`, `3-4c-policy-deleting` and `3-4d-policy-backups`; Bengali `2-watch-lead-in` and `3-4d-policy-backups`. The gate then failed on exactly those six, and the policies' edits made it pass.
- **The countdowns (errata p5 #2).** The Bengali countdowns changed, so `tools/watchface/phone-forms.json` was regenerated (`-Ptaqwa.updateVectors`, with FaceFormsTest). `scripts/face-gate.sh` then passed its 12 checks, `moon-fit.py` among them.
- **The golden feed.** The Urdu face line is one of the feed's words, so the Urdu golden feed `phone-capetown-ur24.json` was regenerated (WatchFeedGoldenTest).
- **The Qibla page (Rulings 66 and 67).** Two tilt notices changed, the Turkish and the Indonesian. `QiblaFitTest` and `QiblaCaptionTest` (Ruling 68 item 4's T9) pass on both. Both are in Latin script, which the host measures in Manrope, so the ink model is unchanged (`qibla-ink.py --check`: current). No short dial form changed (`distanceOnDial`, `qiblaDetailOnDial`), so Ruling 67's note had nothing to regenerate.
- **Indonesian's twin.** The one Compose row changed in Indonesian was changed in `values-id` and in its `values-in` twin, and `check-strings.sh` holds the two equal. The Wear resources have only `values-in`.
- **The face's fixed slots.** `FaceStringsTest` holds each fixed slot the pickers list to its source's name (`AS_THE_PICKERS`). So where a reader's should changed `complication_day` (French, Turkish, Urdu), `slot_day` took the same words. Two readers had filed that slot as a nit, which is applied with its should.

### French: 141 rows, 0 musts, 12 shoulds, 9 nits

Applied, all 12:
- **`crownHint`**, the Apple Watch's tasbeeh hint: « Tournez la couronne ou touchez » → « Tournez la Digital Crown ou touchez ».
  - Apple's French guide keeps the name in Latin: « faites tourner la Digital Crown vers le haut » (`apdecf142fb9`). The French store rows, and the Turkish and Indonesian hints, already say Digital Crown.
  - Ruling 60 item 4 still holds: the two watches' hints differ only in what is turned.
  - The hint is 35 characters long. The Turkish hint, 36 characters, already shows in the same one-line hint. No host test measures that hint, so the Apple Watch device pass sees it.
- **Tile:** « vignette » → « carte », in four places:
  - the Play wrist paragraph (`fr.md:43`);
  - the home row;
  - the Wear release notes;
  - the phone's release notes.
  - Google's French Wear OS help calls Tiles « Cartes » (answer `9314375`), and Samsung France calls them « cartes ».
- **`complication_day`:** « Journée de prière » → « Prières du jour ». « Journée de prière » names an observance. `slot_day` takes the same words (see above), and `slot_day_dial` stays, as a nit.
- **The home row:** « Sur les deux, Aujourd’hui, » → « Sur les deux, l’écran Aujourd’hui, ». On its own, « Aujourd’hui » read as the adverb. The support answer already says « l’écran Aujourd’hui ».
- **The iPhone's companion app:**
  - the phone's Apple row said « l’app Watch », and the support answer said « l’application Watch »;
  - both now say « l’app Apple Watch », as Apple's French guide names it (« Accédez à l’app Apple Watch sur votre iPhone. », `apd99e3c6a68`).
  - See "For the Lead, from the readers" below about the English.
- **The policy's « Votre montre »** (approved row `2-watch-lead-in`): « vos propres phrases de tasbih » → « vos propres formules de tasbih ». « Formule » is the app's own word (`tasbeeh_custom_phrase`). This changes the wording only.
- **The policy's « Ce que Taqwa ne fait jamais »:** « aucun rapport de plantage » → « aucun service de rapports de plantage ».
  - The old line said that no crash report exists. That is against the same policy's own crash-report paragraphs.
  - The line predates the watch. It is not a gate row, and the sweep stays clean.
- **The listing's notification bullet** (`fr.md:21` and `:94`): « … dont La Mecque et Médine, ou votre enregistrement) ». This is the English's "or a recording from your phone", in the app's own word (`adhan_voice_custom`).
  - **Both views:** the in-session review left it out as part of Task 10's cut, which is Mohamed's to approve. The reader found that the French on `main` lacked it before that cut, and that the cut note does not list it. The first point was checked: `bcafdfa9:docs/store/listing/fr.md:22` has no recording.
  - The reader's should is taken. Play's full description is now 3,989 of 4,000 characters.

Nits, left as notes:
- `complication_day_dial` « Cadran Taqwa : cadran du jour » says « cadran » twice, in two senses, and has U+00A0 before « : » where the phone uses U+202F. The name is hidden from the pickers.
- `slot_day_dial` « Cadran de la journée de prière » could be « Cadran des prières du jour », to match. It is hidden too.
- In the Play wrist paragraph, « sur le cadran de Taqwa » could be « sur le cadran Taqwa », as everywhere else.
- In the home row, « toujours avec les horaires » could be « le tout avec les horaires », for "all from".
- The home heading (approved row `3-4g-home-heading`), « Hors ligne par conception », is a calque of "by design"; « par principe ». Its first sentence predates the watch.
- The support answer's feed paragraph, « ou ils sont épuisés », could be « ou n’en a plus ».
- The listing's ADHKAR bullet, « et un rappel si vous voulez », could be « et un rappel facultatif » (also at `:111`).
- The listing's sources line, « Coran du Projet Tanzil », could be « texte du Coran du Projet Tanzil » (also at `:124`). It adds 9 characters, which would still fit (3,998).
- The reader also found « Retirer un » (`undoAction`) acceptable for TalkBack, and the (i) bullet's merge acceptable compression.
- The reader agrees with the earlier review on « le défilement intelligent », the lower-case « protection avancée des données », `underAMinute` (the Lead's call 7), « km de La Mecque », and round 2's open point on « vignette ».

### Turkish: 135 rows, 0 musts, 8 shoulds, 10 nits

Applied, all 8:
- **`complication_day`:** "Namaz günü" → "Günün vakitleri". "Namaz günü" read as a day of prayer. "Günün vakitleri" is the phone's own phrase (the listing's "günün vakitlerini").
- **`slot_day` and `slot_day_dial`:** → "Günün vakitleri" and "Günün vakitleri kadranı", the names the watch app gives these sources.
- **The iPhone's companion app:** "Watch uygulamasından" → "Apple Watch uygulamasından", in the phone's Apple row and in the support answer. This is how Apple's Turkish guide writes it ("iPhone’unuzda Apple Watch uygulamasını açıp").
- **`phrasesAction`:** "Bir zikir seçin" → "Zikir seç". This is the bare imperative of the actions beside it ("Bir eksilt", "Say") and of the phone's buttons.
- **`holdLevel`:** "Bileğinizi düz tutun" → "Saatinizi düz tutun", and the support answer's Qibla paragraph likewise.
  - "Bileğinizi düz" read as "keep your wrist straight". The phone says "Telefonunuzu düz tutun".
  - `QiblaFitTest` and `QiblaCaptionTest` pass. It takes two lines at sizes 1.0 and 1.3.
  - The reader's other form, "Bileğinizi yatay tutun", was not needed.
- **The home row:** "İkisinde de Bugün," → "İkisinde de Bugün ekranı,". On its own, "Bugün" read as the adverb.

Nits, left as notes:
- `countSpoken` "%1$s, toplam %2$s" could be "%1$s, hedef %2$s", as the phone's tasbeeh says "Hedef".
- `complication_day_dial` "Taqwa kadranı: gün kadranı" could be "Taqwa saat yüzü: gün kadranı". It is hidden.
- "her saat yüzüne komplikasyonlar" could be "her saat yüzü için komplikasyonlar", in the Wear notes and the home row.
- `next_prayer_widget_description` "geri sayım" could be "geri sayımı".
- `next_prayer_widget_name` "Sonraki namaz" could be "Sonraki vakit". Both are the phone's words.
- `settings_group_watch` "SAAT" could be "AKILLI SAAT".
- The wrist paragraphs say "gösterir" three times. They could say "ve Kıbleyi bulur" and "aynı vakitleri".
- The support answer's feed paragraph, "bir ay önceden", could be "önümüzdeki bir ayın …", with "bu sayede".
- The support answer's face paragraph, "her namazda", could be "her namaz vaktinde".
- The listing's notification bullet, "belirlediğiniz dakika sonra", could be "belirlediğiniz kadar dakika sonra" (also at `:94`).
- **Outside the rows, not classified:** the site pages mix straight and typographic apostrophes. WatchDateLine speaks "Pazar 18 Ekim", where Turkish says "18 Ekim Pazar".
- **Both views on "kutucuk":** the in-session nit said Google says "kart". The reader found no Turkish Wear OS help to confirm that, and has no objection to "kutucuk". It stays.
- **The reader's disclosure:** one of its greps printed one line of the earlier Turkish section before it had finished. The line was about a row already fixed.

### Indonesian: 143 rows, 0 musts, 8 shoulds, 10 nits

Applied: 6 as written, 2 in other words.
- **`holdLevel`**, applied in other words: "Pegang jam mendatar" → "Datarkan jam tangan" ("level the watch"). The old line said "grip the watch flat", which is the phone's "Pegang ponsel mendatar" for another act.
  - The reader's form, "Datarkan pergelangan", fails Ruling 68 item 4's T9. The long word "pergelangan" sits alone on the second line and meets the turning letters from font size 1.07 up: 191 caption lines, down to -1.52 units against the air T9 asks.
  - The reader's own other form, "Jaga pergelangan tetap datar", is cut at every size (T9's no-cut test, 232 cases).
  - Rulings 66 and 68 item 4 say the remedy is the words, never the dial. "Datarkan jam tangan" drops the grip the reader objected to, and says what the Turkish reader's "Saatinizi düz tutun" says.
  - It passes `QiblaFitTest` and `QiblaCaptionTest`, on two lines ("Datarkan / jam tangan"). Its least air to a turning letter is 1.62 units, the least of any caption on the page, and within T9's bound.
  - The support answer keeps "Jaga pergelangan tetap datar", where no fit applies.
- **The listing's wrist paragraphs** (Play `:40`, App Store `:113`): "Waktunya dari … selalu sama." → "Waktu salatnya dari … selalu selaras." "selalu sama" said that the two devices are the same. Play is now 3,976 of 4,000 characters, and the App Store description 3,888.
- **"layar Hari ini"**, on the home row and in the Wear release notes. On its own, "Hari ini" read as "today".
- **The iPhone's companion app**, applied in other words: the support answer's "aplikasi Watch" → "app Apple Watch". The phone's Apple row also changed, "Coba lagi dari app Watch." → "Coba lagi dari app Apple Watch.", in `values-id` and its `values-in` twin.
  - **Both views:** the reader asked for "app Watch". Apple writes "app" for its own apps, not "aplikasi", and the reader kept "Watch" because the English says it.
  - But the reader also quoted Apple's Indonesian guide, "membuka app Apple Watch di iPhone" (`apd3cd8641c2`). The French and Turkish readers found the same name in their guides.
  - So the Indonesian names the app as Apple's guide names it, as French and Turkish do. The phone's row says it as the support answer does, which is the French reader's point that the two rows name the app alike.
- **The support answer:** "selalu menghitung dengan tepat" → "selalu menghitung mundur dengan tepat", which puts the "down" back.
- **The support answer:** "atau waktunya sudah habis" → ", atau waktu salat yang tersimpan sudah habis". "waktunya sudah habis" is the idiom for "time is up".

Nits, left as notes:
- **Both views on the slots:** `slot_left`/`slot_right` say "Komplikasi kiri/kanan". Google's Indonesian help says "detail", but "Detail kiri" tells a user nothing, and the reader would keep "Komplikasi". The report's carried minor set it against Google's word, and the reader sides with keeping it. It stays.
- `settings_watch_install` "Pasang" could be "Instal", Google's button. This is optional, and the `values-in` twin would follow.
- `next_prayer_widget_description` "hitung mundur" could be "hitung mundurnya".
- In the Play wrist paragraph, "tampil di tampilan" repeats a root. It could say "muncul di", and "arah kiblat".
- The policy's opening (row 98), "Ia memakai internet", could be "Taqwa memakai internet". meta.json's privacy description, "untuk apa ia memakai internet", could be "untuk apa internet dipakai". Both are approved rows, so `privacy-check.py` would change first. This agrees with round 4's nit.
- The home heading, "Offline sejak dirancang", could be "Dirancang untuk offline". This is the first half of `3-4g`, and predates the watch.
- The support answer's Qibla paragraph, "gerakkan membentuk angka delapan", needs its object: "gerakkan jam tangan membentuk …", as the phone's `qibla_calibration_help` writes it.
- The listing's notification bullet lacks the 1.5.0 recording, as the French did. This predates the watch (`main` `bcafdfa9`). It would cost about 25 characters, and Play has 24 left after the should above, so it would need a trim elsewhere.
- The English cut note at `id.md:50`, "keeps only what … (no account, no adverts)", reads as what the bullet keeps. "drops what the opening already says" would be clearer.
- **Both views on "dial hari":** the in-session nit said it is not Indonesian. The reader finds "dial" used for a watch dial. It is hidden, and it stays.
- "membawa datanya" stays a nit, as before. `underAMinute` has no "lagi", by the Lead's call 7.

### Urdu: 133 rows, 1 must, 9 shoulds, 8 nits

Applied, all 10:
- **must:** the listing's GPL bullet (`ur.md:50` and `:121`): "ہر مآخذ" → "ہر ماخذ". ہر takes the singular, and the phone writes ماخذ. This predates the watch, and the fix is the same length.
- **`complication_day`:** "نماز کا دن" → "دن کی نمازیں", "the day's prayers". `slot_day` takes the same words (see above), and `slot_day_dial` stays, as a nit.
- **`accent_sage`:** "سیج" → "ہلکا سبز". **Both views:** the in-session review called it a nit, a transliteration. The reader ranks it a should, because سیج is an Urdu word for a bed. The reader's should is taken.
- **`face_choose_it` and FACE.chooseIt:** "چھو کر دبائے رکھیں" → "دبا کر رکھیں". This is the phone's own "touch and hold" (`onboarding_widget_body_ios` and the `widgets_step_*` rows). FaceWordsResourcesTest holds the two alike, and the Urdu golden feed was regenerated.
- **The support answer's face paragraph:** "دبائے رکھیں", twice → "دبا کر رکھیں".
- **The home row:** "دونوں پر: آج،" → "دونوں پر: ”آج“ کی اسکرین،", as the support answer writes it.
- **The Wear release notes:** "اور آج، تسبیح اور قبلہ۔" → "… اور قبلہ کی اسکرینیں۔". It is now 167 of 500 characters.
- **The policy's "Your watch"** (approved row `2-watch-lead-in`): "آپ کے علاقے کی قطب نما کی درستی" → "آپ کے علاقے کے لیے قطب نما کی تصحیح". درستی read as accuracy.
- **The policy's Backups** (`3-4d-policy-backups`): a bare خفیہ ہونا, "to become secret", twice → "خفیہ کاری سے محفوظ".
- **The policy's deleting sentence** (`3-4c-policy-deleting`): "… حذف کرنا یہ سب حذف کر دیتا ہے۔" → "… حذف کرنے سے یہ سب حذف ہو جاتا ہے۔", the shape the support answer uses.

Nits, left as notes:
- "اپڈیٹ", where the phone writes "اپ ڈیٹ". Both are correct, Apple's ur-in pages write اپڈیٹ, and the Tile's fit is measured on it. The reader agrees that the nit can be dropped, and it stays.
- `crownHint` could say "Digital Crown گھمائیں …", but only if it fits on the SE 3 (33 characters against 25). کراؤن renders the English's "the crown".
- `settings_watch_unreachable` "رسائی نہیں ہو سکی" could be "رابطہ نہیں ہو سکا".
- `complication_day_dial` "تقویٰ ڈائل: …" could be "تقویٰ فیس: …". It is hidden.
- `slot_day_dial` "نماز کے دن کا ڈائل" could be "دن کی نمازوں کا ڈائل". It is hidden. (`slot_day` was applied, above.)
- In the listing's wrist paragraphs, "ایک ہی وقت بتاتے ہیں" could be "ایک جیسے اوقات دکھاتے ہیں".
- In the support answer, "ٹھیک گنتی ہے" could be "ٹھیک ٹھیک الٹی گنتی دکھاتی ہے".
- **The reader's notes:** تقویٰ in Urdu script is the phone's `app_name`, the exception this report records above. Ruling 64 item 6's Urdu comma stays with A5. The reader agrees with the support quote's must, `crownHint`'s ٹیپ, `holdLevel`'s افقی and the km order.

### Bengali: 133 rows, 2 musts, 8 shoulds, 7 nits

Applied, all 10:
- **must:** the home row: "দুটিতেই: আজ," → "দুটিতেই: “আজ” স্ক্রিন,". Bengali has no capitals, so a bare আজ read as the adverb, and the Today screen dropped out of the sentence. The support answer writes “আজ” স্ক্রিন.
- **must:** the Wear release notes: "আর আজ, তাসবিহ ও কিবলা।" → "আর “আজ”, তাসবিহ ও কিবলা স্ক্রিন।". This names them as screens, as the Arabic and the Turkish do.
- **The countdowns** (`hoursMinutes`, `hours`, `minutes`): "%1$s ঘ %2$s মি পরে" → "%1$sঘঃ %2$sমিঃ পরে", and so on.
  - A bare ঘ or মি is no Bengali short form.
  - Google's own Bengali short units are "%dঘঃ" and "%dমিঃ", joined by a space. They are in androidx watchface-complications-data 1.3.0's `res/values-bn`, read in the Gradle cache. CLDR's bn-IN months in `phone-forms.json` abbreviate with the same visarga (সেপ্টেঃ).
  - Placeholders and their order are unchanged.
  - `phone-forms.json` was regenerated, and `face-gate.sh` passes. In `moon-fit.py`, the widest Bengali countdown is still এক মিনিটের কম সময়ে, at 112.3 degrees of the curve's 124, with 12.0 px to spare at each end. Its runs hold no glyph past their characters (0 at most, for ৯৯ঘঃ ৯মিঃ পরে), as before.
  - The debug tool `tools/watchface/bengali.sh` writes the new forms into its sample. It also writes the current under-a-minute form, where it held the first draft's.
- **`preview_countdown`:** "১ ঘ ১২ মি পরে" → "১ঘঃ ১২মিঃ পরে", the live form.
- **`face_choose_it` and FACE.chooseIt:** "স্পর্শ করে ধরে রাখুন" → "চেপে ধরে রাখুন". The phone writes it this way eight times, and so does the support answer.
- **`countAction`:** "গুনুন" → "গণনা". This is the phone's `adhkar_a11y_count`, in the same role: TalkBack's click label, and VoiceOver's hint.
- **The policy's "Your watch"** (`2-watch-lead-in`): "আপনার ফোন তাকে দেয়" → "আপনার ফোন ঘড়িকে দেয়".
- **The policy's Backups** (`3-4d-policy-backups`): "আপনার iPhone তাকে … পাঠিয়েছে iOS সেটি" → "আপনার iPhone ঘড়িতে … পাঠিয়েছে, iOS সেটি".
  - **Both views, on both policy rows:** review round 3 left তাকে as a nit. The reader ranks it a should, and it is taken.
- **`complication_day_dial`:** "তাকওয়া ঘড়ি: দিনের ডায়াল" → "তাকওয়া ওয়াচ ফেস: দিনের ডায়াল".
  - **Both views:** the in-session review called it a nit, since the name is hidden from the pickers. The reader ranks it a should, since a hidden name can still reach a screen reader. It is taken.

Nits, left as notes:
- `accent_sage` "সেজ" could be "ধূসর সবুজ".
- `complication_day`, `slot_day` and `slot_day_dial`, "নামাজের দিন", could be "দিনের নামাজ".
- `about_stays_title` and the policy's heading could take the plural "ডিভাইসগুলোতেই". These are approved rows.
- `settings_watch_unreachable` "পৌঁছানো যায়নি" could be "যোগাযোগ করা যায়নি".
- The policy's SDK line (`3-4k-policy-sdk`), "পৌঁছে দিতে পারে", could be "রিলে করতে পারে".
- In the listing's wrist paragraphs, "একই সময়" could be "একই নামাজের সময়".
- In the support answer, "ঠিকঠাক গোনে" could be "নিখুঁতভাবে গোনে".
- **The reader's notes:**
  - তাকওয়া in Bengali script is the phone's `app_name`, the exception recorded above. Every other product name stays in Latin script.
  - Apple's bn-in guide and Google's `?hl=bn` help are in English, so the only Google Bengali found is androidx's.
  - Row 129's "গোপনীয়তা নীতি -তে" is how the tables render a link: the file has no space there.

### For the Lead, from the readers (not applied)

- The English says "the Watch app" (`settings_add_to_apple_watch_failed` and the support answer). Apple's English guide, like its French, Turkish and Indonesian ones, says "the Apple Watch app".
  - French, Turkish and Indonesian now use Apple's name. The English, the Arabic (تطبيق Watch), the Urdu and the Bengali keep "Watch". The Urdu and Bengali guides are in English.
  - The English is Mohamed's (M8). No English line changed here, so his list below is as it was. Whether to put this to him with it is the Lead's call.

## M8: Mohamed's reading list (the English and the Arabic): answered 6 November 2026

### Mohamed's answer, as applied (6 November 2026)

His words, in chat, verbatim: "M8: approved with your recommendations (A1–A7, E4). Changes: none." The reading sheet he read gave one recommendation for each point (the Lead's ledger line "M8 ANSWERED", 20:04 SAST, spells each one out). Each is applied as recommended, in one commit on `watch-feed` from `6f8e2cb7`. Lines are the files' as committed.

- **The eight names:** approved as they are. `FaceStringsTest` still holds them to spec 1.3's table.
- **A1, the update line:** both watches say "حدّث تقوى على ساعتك", with the shadda alone, as the phone's "قيّم تقوى" does.
  - The feed's words took it (`shared/.../watchfeed/WatchWords.kt:185`), and so did the Galaxy watch (`wearApp/src/main/res/values-ar/strings.xml:10`). The Apple Watch's two tables already had it.
  - The Arabic fixture took it too (`watchcore/src/commonTest/.../Fixtures.kt:63`). Then `-Ptaqwa.updateVectors` rewrote the vectors that hold it: `feed-vectors.json`, `sample-feed-capetown-ar-ly12.json`, `sample-feed-capetown-ar-sa.json` and `watch-rules-vectors.json` (`:watchcore`), and the phone's golden `phone/phone-tripoli-ar-ly12.json` (`:shared`). Their diffs are the update line alone.
  - The tests that held the old spelling follow: wearApp's `AppStateTest`, `UpdateFitTest` and `TodayModelTest`, Swift's `TodayTests`, and the watch UI's `LanguagesTests`.
    - `UpdateFitTest`'s Naskh width (8.514 of the em) was measured with the kasra. A kasra is a mark, which takes no advance, so the width stands, and the test says so.
  - The check: `tools/i18n-check.py`'s `WATCH_NOTICES` (`:216`). It holds each Apple Watch target's two notices to the Galaxy watch's own, in every language. It was red on both Arabic targets first, and a new test in `tools/test_i18n_check.py` breaks it on purpose.
- **A2, the face button:** the support answer quotes the button word for word, «أضِف واجهة تقوى» (`site/pages/ar/support.html:35`).
  - `WATCH_ANSWER_AT_M8` is empty (`site/build.py:714`), so `check_watch_answer` holds Arabic as it holds the other six. It was red on Arabic first.
- **A3, complications:** "إضافات" takes the place of "مضاعفات" everywhere the watch's Arabic said it:
  - the store copy: `docs/store/listing/ar.md:42` (Play) and `:117` (App Store);
  - the release notes: `docs/store/release-notes/1.6.0-42.txt:7`, `1.6.0-100042-wear.txt:5` and `1.6.0-42-appstore.txt:10`;
  - the support page (`site/pages/ar/support.html:35`), twice, once in the singular (في الإضافة);
  - the home row (`site/pages/ar/home.html:70`), twice.
  - Both words are feminine plurals, so every sentence keeps its grammar ("تعرض إضافات تقوى"). Each store field is one character shorter.
  - The checks: `CHOSEN_WORDS`, the words Mohamed chose at M8, in `tools/store-listing-check.py:98` and `site/build.py:732`.
    - In the store copy it covers every field of both stores, the newest code's Play notes and every App Store set. Four new tests in `tools/test_store_listing_check.py` (`ChosenWords`) hold it.
    - On the site it covers the watch card and the watch answer.
    - It was red on five store places and two site places first.
  - No "مضاعف" is left outside `docs/superpowers` and the checks' own tables, but the Quran text in `quran.db`.
- **A4, "Next prayer":** the Apple Watch widget says "الصلاة القادمة", the phone's `widget_small_label`, in its name and its description (`iosApp/TaqwaWatchWidgets/ar.lproj/Localizable.strings:2` and `:5`). That is what the Galaxy watch's complication, face and Tile already said.
  - The phone's own `prayer_a11y_prayer_next` stays the phone's. So does the feed's row state `rowNext`, which is that phone string, and which both watches' Today rows say alike.
  - The check: `tools/i18n-check.py`'s `PHONE_WORDS_IN` (`:212`). It holds the Arabic widget's name to `androidApp`'s `widget_small_label`, and the other languages' to `prayer_a11y_prayer_next`, as before. It was red first, and a new test holds both.
- **A5, the spoken join:** in Arabic and Urdu, the two right-to-left interfaces, a prayer's name and its time are now joined by "، " (U+060C, then a space). This is one commit, in all three places:
  - the phone's prayer rows: `prayerRowDescription` takes `rtl` (`shared/.../feature/prayer/CountdownSpeech.kt:31`), from `isRtlLocale()` (`PrayerTimeline.kt:174`);
  - the Wear Tile: its rows, from `dayRows` (`wearApp/.../today/TodayModel.kt:80`), which the Galaxy watch's Today list shares, and its line where it says the start's time (`wearApp/.../tile/TilePlan.kt:128` and `:371`);
  - the Apple Watch's Today rows (`iosApp/WatchCore/Sources/WatchCore/Today.swift:200`).
  - Only those joins changed. The Apple Watch complication's row form, where an entry drops its countdown (`Glance.spokenWithoutCountdown`, `Glance.swift:100`), is not one of the three places, so it keeps ", ". `GlanceTests` holds that. The tasbeeh lists' "name, count" rows (`TasbeehScreen.swift`, wearApp's `PhraseList`) and the other commas keep theirs too.
  - The tests first: `CountdownSpeechTest`, wearApp's `TodayModelTest` and `TilePlanTest`, and Swift's `TodayTests`. Each was red on "العصر, ١٦:١٠" first.
  - The Tile tests that build their expected sentence now take `clockSentence`, and the watch UI's `LanguagesTests` rows take "، ".
- **A6, Apple's Arabic names:**
  - The home row now says "إضافات والحزمة المكدسة الذكية" (`site/pages/ar/home.html:70`), and `SMART_STACK_AT_M8` is empty (`site/build.py:726`). It was red first.
  - The policy's Backups sentence now says "الحماية المتقدمة للبيانات" (`PRIVACY.ar.md:73-74`). Its privacy row `3-4d-policy-backups` changed first (`tools/privacy-check.py:421-427`), with a comment that Mohamed approved the new Arabic at M8 on 6 November 2026, as the tool records M3's approval. The gate failed on that row alone, and the policy then made it pass.
  - `APPLE_WORDS` now has Arabic (`tools/store-listing-check.py:86`), so the App Store's Arabic text cannot say "Smart Stack" or "حماية البيانات المتقدمة".
- **A7, the section's verbs:** the imperatives stay. No change.
- **A8, and the English, E1 to E116:** approved as they are. No change.
- **E4, the App Review notes:**
  - Change 4 is back to its true words: "names it under the (i) (About these times says Taqwa is not affiliated with it);" (`docs/store/app-review/2026-10-30-notes-1.6.0-42.txt:19`).
  - The GitHub link to `docs/ATTRIBUTION.md` is gone from `:28`, 76 bytes. Settings › About Taqwa › Attribution & licences already names that list.
  - The file is 3,944 of the field's 4,000 bytes (`store-listing-check.py`: "3944/4000 bytes").
  - The check holds a length limit only, and no hash, so no pin follows. `docs/STORE-CHECKLIST.md:436` now gives the file's size.
- **The Lead's call:** the English keeps "the Watch app", as Mohamed read it. No change.

**Every marker removed.** The grep found 347 lines in 66 files before, and finds none now. Each one is reviewed and applied: the five languages by Step 2, and the English and the Arabic by M8. Only the marker was removed, by its form, so each file's syntax holds, and a real description that shared its line keeps it:
- 182 whole-line XML and Markdown comments were removed, and 90 trailing ones were taken off their lines.
- In the `.strings` comments, 30 lost their " (draft, …)" ending. In the six `InfoPlist.strings`, the comment now closes on its last real line.
- `WatchWords.kt` lost 11 marker lines. In 6 more, the description after the marker stays ("The new phrases, and the dial's short forms (Ruling 66).").
- In the listings, 12 note sentences went. The 6 App Store What's New notes went too, and the set's header now says the blocks were read.
- The Arabic fixture's KDoc now reads "And its Arabic, as the phone writes it."
- The two rules that required each draft to be marked (`SystemStringsTest`, `FaceStringsTest`) are now the post-M8 rule, `noPhraseIsMarkedADraftSinceTheNativeReview`. No comment in the six languages marks a draft, so a draft added later is held back. The rule was red on the old marked `values-fr` (wearApp) and `values-bn` (the face), then green.
- The descriptions that still called the words drafts now say they were read: the English resources' headers, `WatchWords`' KDoc, `privacy-check.py`'s header and the test comments.

### After M8 (Ruling 73, 6 November 2026)

The Lead's Ruling 73 took up the reviewers' carried minors, in three commits on `watch-feed` from `1ee6cb2f`: `ec2916cc` applied the ruling, and `e1483d1e` and `294ed394` are review's fixes, the BUILD-LOG's lines and the words on the Galaxy's join. Lines are the files' at `294ed394`.

- **1, the complication's join:** A5 reaches the Apple Watch complication too. Its row form, where an entry drops its countdown, now joins with "، " for a right-to-left feed (`iosApp/WatchCore/Sources/WatchCore/Glance.swift:101`, `separator` at `:113`), as the same watch's Today and the phone's rows do. So this form and Today's next-prayer lines read one join on one watch. The complication's other two joins came with Ruling 75, below, which also names what keeps ", ": the Tasbeeh rows, and the watch's own words with no feed.
  - It is the comma the Galaxy's complications put between their parts (`ComplicationFields.spokenSeparator`). Between a prayer's name and its time they put a space when this was written; Ruling 74 item 1 gave them the comma there too (`ComplicationFields.kt:302`).
  - Every other language keeps ", ". The doc comment now says the rule (`Glance.swift:22-24`).
  - The tests first: `GlanceTests` (`:82`, and `testAnArabicOrUrduRowFormIsJoinedByTheArabicComma` at `:89`). They were red on "العصر, ١٦:١٠" and "عصر, ۱۶:۱۰" first. No vector or UI test held the old line.
- **2, the phone's half of A5:** the rows now hand over the interface's language itself (`uiLanguage()`, `shared/.../feature/prayer/PrayerTimeline.kt:173`). `prayerRowDescription` decides the join from it (`CountdownSpeech.kt:33-34`), where a test can hold it.
  - `CountdownSpeechTest.theRowsJoinFollowsTheInterfacesLanguage` (`:57`) holds the Arabic and Urdu tags (`UiLanguage.of` folds by prefix) to "، " and the other five languages to ", ".
  - The mutant, a join told `rtl = false`, was red on two tests.
- **4, the reused drivers:** `docs/superpowers/reports/2026-10-24-wear-app-pass/drivers/states.sh:8` (`UPDATE=`) and `drivers/feeds/large-ar-sa.json` and `large-ar-sa-calibration.json` now say "حدّث", with the shadda alone. A rerun now matches the build. No other driver or touch file held the old spelling. The reports' prose and captured logs stay as written.
- **5, the old name's return:** privacy row `3-4d-policy-backups` (ar) now has the draft's "حماية البيانات المتقدمة" as its old text (`tools/privacy-check.py:427`). If that name comes back anywhere in `PRIVACY.ar.md`, the gate fails.
  - A scratch tree with the name added back passed the gate before, and fails it by its row now.
  - A new test, `M8` (`tools/test_privacy_check.py:543`), was red first.
- **6, the watch's own words:** the phone-words header no longer covers the watch's own phrases. A comment, `<!-- the watch's own words -->`, now heads them in all six language folders, `values-in` included: wearApp (`values-*/strings.xml:9`, before `open_taqwa_on_phone`) and the face (`watchface/src/main/res/values-*/strings.xml:6`, before `style`). No string changed.
- **3, 7 and 8:** `rowNext` stays as it was. The report's nits stay as a record. The comment lines that the M8 rewording pushed past 100 columns are rewrapped, in comment text only. The exceptions, in full:
  - The Wave 8 lanes' files, as the ruling says, reports aside. None of them holds such a line on this tree.
    - Lane P2: `AndroidWatchLinkTest.kt`, `PlayWatchTargets.kt`, `WatchRows.kt`, `WatchTargetsSource.kt`, `WatchTargetsTest.kt`, `WatchSectionTest.kt`.
    - Lane A: `iosApp/iosApp/WatchHarness.swift`, `scripts/ios-release.sh`, `scripts/release.sh`, `scripts/test-release.sh`, `tools/watchface/validate.sh`.
    - Lane W2: `tools/watchface/README.md`, `clock-test.sh`, `clock.sh`, `wearApp/build.gradle.kts`; under `wearApp/.../complication/`, `ComplicationSources.kt`, `DatesSource.kt`, `DayDialSource.kt`, `DaySource.kt`, `NextPrayerSource.kt`, `SpokenLine.kt`; `wearApp/.../qibla/WristCompass.kt`; and the tests `ClockFormTest.kt`, `ComplicationFieldsTest.kt`, `SpokenTest.kt`, `TimelineLogTest.kt`.
  - `wearApp/.../tile/TilePlan.kt:121` and `:125` (104 and 102 columns) and `wearApp/.../qibla/QiblaModelTest.kt:40` (104 columns), as the ruling skipped them. In the end no Wave 8 lane changed either file.
  - `iosApp/TaqwaWatchWidgets/ar.lproj/Localizable.strings:1` (173 columns), kept long on purpose: a `.strings` comment is one line per key in all seven languages, and the six siblings' line 1 is 117 columns.

### Ruling 75 (7 November 2026)

The Lead's Ruling 75 took up what Ruling 73's reviewers carried, on `watch-feed` from `612b1cf0`. Lines are the files' as committed. Nothing drawn changed in any of it.

- **1, the complication's other two joins:** they now take the row form's comma, `Glances.separator` (`iosApp/WatchCore/Sources/WatchCore/Glance.swift:116`, no longer private). So Today (`Today.swift:148`, `TodayTests.swift:363-364`) and the complication say one join on one watch.
  - The fallback for a feed with no spoken words (`Glance.swift:94`): "العصر ١٦:١٠، بعد ١ س ١٢ د".
  - The empty form's name and notice (`NextPrayerContent.swift:92`): "تقوى، حدّث تقوى على ساعتك".
  - The tests first, for Arabic, Urdu and English: `GlanceTests.testWithNoSpokenWordsTheScreensWordsJoinAsTheRowsFormDoes` (`:141`), red on Arabic and Urdu, and `NextPrayerContentTests.testTheEmptyFormJoinsTheNameAndTheNoticeAsTodayDoes` (`:118`), red four times, each of them with its two notices.
  - With no feed, the watch's own words keep ", " (`NextPrayerContentTests.swift:149`). The ruling is of a right-to-left feed, and WatchCore reads no watch language.
  - The Galaxy, read and not changed (Wave 9's Lane W3 has those files), on this tree: its no-spoken-words line already joins with `ComplicationFields.spokenSeparator` (`SpokenLine`, `NextPrayerSource.kt:169` as Lane W3 left it). Its empty forms join nothing: the next prayer's empty step is `NoDataComplicationData` (`ComplicationSources.kt:77`), the day dial says the notice alone (`DayDialSource.kt:55-56`), and the Tile and Today's page draw the notice alone (`TileLayout.kt:453`, `TodayPage.kt:87`). The picker's preview said "$name $time, $countdown" with ", " when this was read; Ruling 74 item 1 gave it the separator too, from the watch's own language (`nextPrayerPreview`).
- **2, Bengali:** `GlanceTests.testABengaliFeedKeepsTheLatinCommaInEveryJoin` (`:154`), on `Vectors.rules.feeds["capetown-bn"]`, holds ", " for a non-Latin left-to-right feed: the row form (`:161`), the fallback (`:163`) and the empty form (`:171`). Taking the comma from `!latinScript` in any one of the three turns only this test red: the row form 1 failure, the fallback 1, the empty form 2.
- **3, the phone's call site:** held, with no change to the app. `shared/src/iosTest/.../feature/prayer/PrayerRowLanguageTest.kt:75-85` composes `PrayerTimeline` under Arabic, Urdu, English and Bengali and pins each row's `contentDescription`.
  - The harness can do it without a new parameter: each case swaps `AppleLanguages` in the test process's argument domain, where `-AppleLanguages (ar)` on a command line lands, and puts the domain back in a `finally` (`:45`). `NSLocale.preferredLanguages` follows it, and so does the resources' language, as the name in each script shows. Nothing of it is written down.
  - The mutants at `PrayerTimeline.kt:173`: `UiLanguage.ENGLISH` is red on the Arabic and Urdu cases and `UiLanguage.ARABIC` on the English and Bengali cases, each 2 of the file's 6.
  - **The first swap leaked, and review round 2 caught it.** It wrote `AppleLanguages` with `setObject` and took it away with `removeObjectForKey`. That removal does not always reach the disk, with or without `synchronize()`, so a run of this test alone could leave `[ar]` in `test.kexe.plist`, the one file all three modules' test binaries use on the simulator, and the next test process started in Arabic. Why it misses is not proved; the tests run under `simctl spawn --standalone`, which by its help cannot connect to other services. That is what turned `int-p3`'s gate red on 7 November, 11 failures, all in Arabic, on a tree with neither this test nor a language change. A run of the whole suite could not see it, as the test clears the key before the suites that read the language.
  - `registerDefaults` does not do it either: the simulator's global domain says en-US and outranks it (a probe: `preferredLanguages` stayed `[en-US]`).
  - Two tests hold the swap itself. `theSwapWritesNothingToThePreferences` (`:93`) is red on the first swap every time, 4 runs of 4, in copies of the binary under other names so that no red run could touch `test.kexe.plist`; those copies' own files showed the race, `[ar]` left by one and `{}` by another. `theSwapEndsWithItsCase` (`:104`) is red on a swap never taken away.
  - After a run of this test alone, through Gradle, on the real code and on both mutants, `test.kexe.plist` read `{}` and had not been written since 01:58:34, before the fix. In the next process the seven suites that `int-p3` saw red, filtered, passed 38 of 38. The `{}` itself is a reviewer's `plutil -remove` at 01:58:34, of the key the first swap left.
- **Seen beside it, for the Lead, and not changed:** the Tasbeeh phrase rows join the name and the count with ", " in Arabic and Urdu on both watches (`TasbeehScreen.swift:177`, `wearApp/.../tasbeeh/PhraseList.kt:56`). The Galaxy does not use `spokenSeparator` there, so the ruling's list leaves it out, and "one join on one watch" is the complication and Today's, not this row's.

### The eight Arabic names (spec 1.3), first

| English | Arabic | Where |
|---|---|---|
| Arch | المحراب | the face editor's style |
| Ring | الحلقة | the face editor's style |
| Sun | الشمس | the face editor's style |
| Moon | القمر | the face editor's style |
| Amber | كهرماني | the face editor's colour |
| Pearl | لؤلؤي | the face editor's colour |
| Sage | ميرمية | the face editor's colour |
| Night | ليلي | the face editor's colour |

### Eight Arabic points to decide (as put to him: his answers are above)

- **A1. The update line** (errata p5 #30). The feed and the Galaxy watch say "حدِّث تقوى على ساعتك" (shadda and kasra). The Apple Watch's own strings say "حدّث تقوى على ساعتك" (shadda alone). Which one should both watches use?
- **A2. The face's button and the support page.** The watch's button reads "أضِف واجهة تقوى", but the support answer quotes «إضافة واجهة تقوى». Either the button takes the verbal noun or the quote takes the button's words.
  - `site/build.py --check` notes it until he answers (`WATCH_ANSWER_AT_M8`, which then empties).
- **A3. Complications.** The store copy, the release notes and the support page say "مضاعفات". The face editor's own slots say "الإضافة اليسرى / اليمنى". Apple's Arabic guide uses "إضافات", the face editor's word ("إدراج إضافات إلى واجهة الساعة"), and nowhere "مضاعفات". Should the store copy, the release notes and the support page take "إضافات" too, or keep "مضاعفات"? *(Fix round 1: the first round called "مضاعفات" Apple's word, which it is not.)*
- **A4. "Next prayer".** The face, the complication and the Tile say "الصلاة القادمة" (the phone's `widget_small_label`). The Apple Watch widget says "الصلاة التالية" (the phone's `prayer_a11y_prayer_next`). Both are the phone's own words. Should the watches keep each, or use one?
- **A5. Spoken joins** (Ruling 64 item 6). The Apple Watch's Today, the Wear Tile and the phone's prayer rows read ", " between the name and the time in Arabic and Urdu.
  - The Urdu review recommends "،".
  - Is it "،" for Arabic too? If so, both languages change in one commit across all three places.
- **A6. Apple's Arabic names.** Apple's Arabic guides name two features otherwise than the Arabic copy does:
  - **Smart Stack.** The site's Arabic home row keeps "Smart Stack" in Latin, and Apple's Arabic guide calls it "الحزمة المكدسة الذكية". `site/build.py --check` notes it until he answers. Then `SMART_STACK_AT_M8` empties, and `SMART_STACK`'s Arabic takes his choice.
  - **Advanced Data Protection.** The policy's Backups sentence says "حماية البيانات المتقدمة", and Apple's Arabic page `108756` says "الحماية المتقدمة للبيانات". That sentence is privacy row `3-4d-policy-backups`'s Arabic, so a change goes into the row as well.
  - French and Turkish now use Apple's own names ("défilement intelligent", "Akıllı Gruplama", "İleri Düzey Veri Koruma"), and Indonesian keeps "Smart Stack", as Apple's Indonesian guide does. Should the Arabic take Apple's names, or keep its own?
  - *(Fix round 1: the first round said French, Turkish and Indonesian all took Apple's names, but three of those names were not Apple's.)*
- **A7. The section's verbs.** The phone's Watch rows use imperatives ("ثبّت على ساعتك", "أضِف إلى Apple Watch", "تابِع على ساعتك"). Is that the form he wants for Settings rows, beside A2's choice?
- **A8. Everything else** in the Arabic table below: every phrase with its English, the policy's and the site's paragraphs, and the store copy. The countdown "بعد 1 س 12 د" is the spec's own.

### The English, once (E1 onwards)

These are the new English phrases this release ships, each once, in the order the Arabic table first meets them. The App Review notes' changes come last (E-notes).

1. Prayer times depend on where you are. Everything is calculated on your device. Only your own devices can see your location.
2. Add to Apple Watch
3. The face could not be added. Try again from the Watch app.
4. WATCH
5. Install on your watch
6. Add the Taqwa face
7. Continue on your watch
8. Couldn’t reach your watch
9. Stays on your devices
10. Only your own devices can see your location, settings, bookmarks and counts.
11. Online only for what you choose
12. The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted so only your phone and watch can read it.
13. The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted so only your phone and watch can read it.
14. Coordinates are stored on your device and used only to calculate times. Only your own devices can see your location.
15. Taqwa
16. Next prayer
17. Prayer times
18. Open Taqwa on your phone
19. Update Taqwa on your watch
20. Prayer day
21. Taqwa face: day dial
22. Dates
23. Day
24. Prayer times today
25. ASR
26. Asr
27. 16:10
28. in 1 h 12 min
29. Fajr 4:33, Sunrise 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24
30. 6 Jumada al-Ula 1448
31. 6 Jumada al-Ula
32. SUN 18 OCT
33. SUN
34. 18
35. Today’s prayers and the next one counted down
36. The next prayer and its countdown on your watch face.
37. Taqwa is your watch face.
38. Touch and hold your watch face, then choose Taqwa.
39. Couldn’t add the face. Try again later.
40. Style
41. Arch
42. Ring
43. Sun
44. Moon
45. Colour
46. Amber
47. Pearl
48. Sage
49. Night
50. Date and moon
51. Time to the next prayer
52. Prayer day dial
53. Left complication
54. Right complication
55. The next prayer, its time and the countdown.
56. Taqwa uses your location to calculate prayer times on your device. Only your own devices can see your location.
57. in %1$s h %2$s min
58. in %1$s h
59. in %1$s min
60. in under a minute
61. Turn the bezel or tap
62. Turn the crown or tap
63. Take one away
64. Choose a dhikr
65. Hold your wrist level
66. %1$s km
67. %1$s° · %2$s km
68. %1$s of %2$s
69. %1$s. Next, %2$s
70. %1$s. Round complete
71. Round complete. %1$s, round %2$s
72. %1$s, round %2$s
73. Count
74. up to 180°: Turn right about %1$s degrees
75. up to 180°: Turn left about %1$s degrees
76. _Last updated 30 October 2026. Applies to Taqwa 1.0.0 and later on Android and iOS._
77. Taqwa is a free Islamic app with no account, no ads and no analytics. It works offline. It uses the internet to download Quran recitations, and only when you ask it to. If you add Taqwa to your watch, its data may travel over the internet too, encrypted, as below.
78. ## What stays on your devices
79. Everything the app knows is stored on your own devices:
80. None of this is sent to us or to anyone else. There is no server behind Taqwa and no account to create. Deleting Taqwa from your phone and your watch deletes all of it.
81. ## Your watch
82. If you add Taqwa to your watch, your phone gives it your city, the Qibla's direction and distance, your area's compass correction, the coming month's prayer times as the app writes them, and your own tasbeeh phrases, with a random number made once on your phone that tells your watch which phone sent them. When your phone and watch are not directly connected, Google (Android) or Apple (iPhone) may carry this data over the internet, end-to-end encrypted so only your phone and watch can read it.
83. The app asks for your location only to compute prayer times and the Qibla direction, and only if you allow it. Picking a city from the built-in list works just as well and needs no permission. Your location is used on the device. Only your own devices can see your location.
84. Once you have opened anything to do with recitation, whether the reciter list, the Recitation settings or a download, the app also checks once a day for an updated list of reciters, from the same repository. That request is the same for everyone and reveals nothing but your IP address. Until you first use recitation, the app itself makes no network request at all.
85. Taqwa opts out of Android's app backup, so your location and settings are not copied to Google. On iOS, your settings, bookmarks, counts and downloaded recitations are excluded from iCloud backup; the only files of Taqwa's own that are backed up are the widgets' small caches, the upcoming prayer times and the hundred ayahs the daily-ayah widget rotates through, neither of which contains your location. If you add Taqwa to an Apple Watch, iOS keeps the last copy your iPhone sent it, which names your city, and that copy may be in your iPhone's backup. An iCloud backup is encrypted with keys Apple keeps, unless you turn on Advanced Data Protection, which encrypts it end to end; a backup to a computer is encrypted only if you choose to encrypt it.
86. - No account, sign-in or profile. - No ads and no advertising identifier. - No analytics, crash reporting or usage statistics. - No third-party SDK that talks to the internet. The Android app's one Google Play services library links your phone to your own watch, and Google may relay that link, encrypted, as above. - No sale or sharing of data, because there is none to sell or share.
87. THE QURAN • The complete Uthmani text, in the same script as the Madinah Mushaf, and the printed Mushaf's page layout if you want it. • Translations in English, Arabic tafsir, French, Turkish, Indonesian, Urdu and Bengali, shown under each ayah. • Search across the Arabic text and the translations. • Bookmarks, copy and share, and a card that takes you back to where you stopped. • Recitation by ten reciters, downloaded once per surah and played offline, with lock-screen controls; when a surah ends the next one follows. • During recitation, your phone's own voice can read the translation or tafsir aloud after each ayah, where the phone has a voice for that language. • An ayah-of-the-day widget.
88. On your wrist: on Wear OS watches such as Galaxy Watch, Taqwa shows the next prayer and its countdown on its own watch face, in complications for any face and in a Tile, counts tasbeeh with the bezel or the crown, and points to the Qibla. The times come from your phone, so your watch and your phone always agree.
89. MADE TO BE TRUSTED • No account, no adverts, no analytics and no tracking of any kind. • Your location is used on your phone to calculate times. Only your own devices can see your location. • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted. • Free and open source under the GPL, with every source and licence credited in the app.
90. ### Description (4000) Taqwa gives you the essentials of the day in one quiet place: prayer times where you are, a takbir or the adhan at each prayer, the whole Quran to read and listen to, the morning and evening adhkar, and the Qibla. It works offline, costs nothing, shows no adverts and asks for no account.
91. On your wrist: on Apple Watch, Taqwa shows the next prayer and its countdown in your watch face's complications, counts tasbeeh with the Digital Crown, and points to the Qibla. The times come from your iPhone, so your watch and your iPhone always agree.
92. MADE TO BE TRUSTED • No account, no adverts, no analytics and no tracking of any kind. • Your location is used on your phone to calculate times. Only your own devices can see your location. • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted. • Free and open source under the GPL, with every source and licence credited in the app.
93. On your wrist
94. On Galaxy Watch and other Wear OS watches, the Taqwa face in four styles, complications for any face and a Tile; on Apple Watch, complications and the Smart Stack. On both, Today, tasbeeh counted with the bezel or the crown, and the Qibla, all from your phone’s own prayer times and in your phone’s language.
95. Offline by design. Online only for what you choose.
96. There is no server behind Taqwa and no account to create. The internet is used for recitation: fetching a surah when you download it or play one not yet downloaded, and, once you have used recitation, checking once a day for an updated list of reciters. Until then, the app itself makes no network request. If you add Taqwa to your watch, Google or Apple may carry its data between your phone and watch over the internet, encrypted so only your phone and watch can read it.
97. Stays on your devices Only your own devices can see your location, settings, bookmarks and counts.
98. Online only for what you choose The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google or Apple may carry its data over the internet, encrypted so only your phone and watch can read it.
99. Taqwa on your watch
100. On a Galaxy Watch or another Wear OS watch, install Taqwa from Settings › Watch in Taqwa on the phone while the watch is nearby, or from the Play Store on the watch. On an Apple Watch, the watch app comes with the iPhone app; if it is not on the watch, install it from the Watch app on the iPhone.
101. The Taqwa face needs Wear OS 6: choose Add the Taqwa face on the watch, or touch and hold your watch face and pick Taqwa, then touch and hold it again to choose its style, colour and slots. On any face, and on an Apple Watch, Taqwa’s complications show the next prayer and its countdown. On Wear OS 3 a complication’s countdown moves only at each prayer; Today, in the watch app, always counts down exactly.
102. Your phone sends the watch its prayer times a month ahead, so the watch keeps working for weeks away from it. If the watch says “Open Taqwa on your phone”, it has no times yet, or they have run out: open Taqwa on the phone with the watch nearby. The watch speaks the language you chose in Taqwa on the phone, with its digits and its clock; the watch’s own menus follow the watch’s language.
103. The Qibla screen uses the watch’s own compass. Hold your wrist level, away from magnets, and if it asks to be calibrated, move it in a figure of eight a few times.
104. Everything Taqwa knows lives on your own devices: your location or chosen city, your settings, bookmarks, adhkar and tasbeeh counts, and downloaded recitations. There is no account and no server, so there is nothing to request or delete on our side. Uninstalling Taqwa from your phone and your watch removes all of it. The privacy policy says this in full.
105. "description": "What Taqwa stores on your devices, what it uses the internet for, and what it never does."
106. The Taqwa face in four styles, complications for any face, a Tile, and Today, Tasbeeh and Qibla. Needs Taqwa on your phone, which sends the prayer times.
107. What's new in 1.6.0: / • Taqwa on your Apple Watch: the next prayer in your complications, tasbeeh with the Digital Crown, and Qibla
108. What's new in 1.6.0: / • Taqwa on your watch: a watch face, complications and a Tile, tasbeeh and Qibla, on Wear OS watches such as Galaxy Watch
109. %1$s° · %2$s km to Makkah
110. ### Full description (4000) Taqwa gives you the essentials of the day in one quiet place: prayer times where you are, a takbir or the adhan at each prayer, the whole Quran to read and listen to, the morning and evening adhkar, and the Qibla. It works offline, costs nothing, shows no adverts and asks for no account.
111. PRAYER TIMES • Times for where you are, from your phone's location or a city you choose from more than 34,000. • Times from the timetable the mosques near you follow. Where Taqwa holds it, it reproduces it and checks it day by day against the published tables: on every day checked, no prayer starts before the published time. Where a timetable is not yet fully checked, Taqwa adds a safety margin; where mosques follow different timetables, it shows cautious times, each prayer once all have begun it; and where no timetable is known, it calculates on the safe side. • Tap the (i) on the Prayer screen to see whose times these are, and About these times for how they are made and checked. Match my mosque looks for your mosque's timetable from two times on its board, and the usual calculation methods (Muslim World League, Egyptian, Karachi, ISNA and more) and a per-prayer adjustment are there too. • Rules for high latitudes, where the night is too short for the usual angles. • A notification for each prayer: a clear tone, a takbir, or the adhan in a choice of voices, among them Makkah and Madinah, or a recording from your phone, with an optional reminder before it, an Iqama reminder a chosen number of minutes after the adhan, and a Duha reminder at its best time. • A Jumu'ah pill on Friday's Dhuhr, home-screen widgets with today's times and the countdown to the next prayer, and the Hijri date alongside the Gregorian one. • A 24-hour or 12-hour clock, as you prefer.
112. ADHKAR • The morning and evening adhkar in Arabic, with a translation in your language, each counted on screen. • The Quranic ones recited by your chosen reciter, and a reminder for each set if you want one.
113. Prayer times from Taqwa's own engine; the Qibla bearing uses the Adhan library. Quran text from the Tanzil Project. Adhkar texts from OpenDua. Recitations from the Islamic Network, licensed by the reciters for free distribution. Taqwa is not affiliated with the religious authorities whose timetables it follows.
114. ## Review notes (App Store and Play, English only) No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. Everything works offline except recitation downloads, which fetch audio from a public GitHub release on request. To see the lock-screen player, play a surah and lock the phone. Widgets are added from the home screen's widget gallery. Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it; the privacy policy is at https://taqwa.world/privacy/. | App Store Connect's Notes field carries the longer answers to App Review's information request of 23 September 2026 instead, brought up to date for the watch release: `docs/store/app-review/2026-10-30-notes-1.6.0-42.txt` (the 23 September reply and the earlier notes files stay as the record of what was sent then). Apple asked for them to stay there for future submissions, so update the newest file and the field together when a feature, a network use or a licence changes. The field holds 4,000 bytes.
115. QIBLA • A compass pointing to Makkah, with the bearing and distance, and an honest word when the phone's compass cannot be trusted.
116. TASBEEH • A dhikr counter with the post-prayer set built in.

**E-notes: the App Store Connect notes** (`docs/store/app-review/2026-10-30-notes-1.6.0-42.txt`, English only, 4,000 bytes, the field's limit, as he read it; 3,944 bytes since E4 was applied):
- The first two changes are Ruling 69 item 2's.
- The other eleven trim words so that both fit. None of them touches an approved privacy row.
- If Mohamed would rather keep a trimmed phrase, the GitHub link to `docs/ATTRIBUTION.md` (77 bytes) could go instead, since the Settings path now names the same list in the app.

1. "- Apple Watch: its app comes with the iPhone app; complications and Today show the iPhone's times." → "- Apple Watch: its app comes with the iPhone app; complications and Today show the iPhone's times once it has run with the watch nearby (until then, "Open Taqwa on your phone")."
2. "credited under About Taqwa › Attribution & licences:" → "credited under Settings › About Taqwa › Attribution & licences:"
3. "Taqwa has no account registration, login or deletion, no user-generated content, and no paid content or in-app purchases." → "Taqwa has no account (so no registration, login or deletion), no user-generated content and no in-app purchases."
4. "Taqwa reproduces it and names it under the (i) (About these times says Taqwa is not affiliated with it);" → "Taqwa reproduces it and names it under the (i), saying it is not affiliated with it;"
   - *Review round 3:* this trim changes the fact, not only the words. The (i) card (`WhoseTimesCard.kt`) names the authority but does not say Taqwa is not affiliated with it. Only About these times does (`AboutTimesScreen.kt`, lines 258 and 340). So the trimmed line is not true as it stands. The old wording is true, but it needs 20 more bytes found elsewhere in the 4,000.
5. "Taqwa has no server of its own and no analytics" → "Taqwa has no server and no analytics"
6. "The Qibla card opens the compass (physical device needed) and the Tasbeeh card opens the counter." → "The Qibla card opens the compass (needs a physical device), the Tasbeeh card the counter."
7. "Play at the top of a surah offers to download its recitation" → "Play on a surah offers to download its recitation"
8. "Taqwa is free, with no ads or purchases, so it stays within these terms." → "Free, with no ads or purchases, Taqwa stays within these terms."
9. "notifications, the adhan voice, the timetable, location," → "notifications and adhan voice, timetable, location,"
10. "next prayer and today's times, and the ayah of the day, on the Home Screen" → "next prayer, today's times and the ayah of the day on the Home Screen"
11. "a built-in city search opens instead" → "a city search opens instead"
12. "Where an authority publishes the timetable, or most local mosques" → "Where an authority publishes the timetable or most local mosques"
13. "The Quran, translations, city list and fonts are bundled." → "The Quran, translations, cities and fonts are bundled."


## Step 4: the checks

Every check ran on the corrected tree (WIP `caaf2a45`, plus this report and the build log), on 6 November 2026, between about 15:40 and 16:00 SAST:

- `scripts/check-strings.sh`: `test_i18n_check.py` PASS, then `i18n: clean`. This includes the `.lproj` parity of `TaqwaWatch`, `TaqwaWatchWidgets` and `iosApp`.
- `python3 tools/privacy-check.py`: 263 rows, 263 approved, 0 awaiting, and 238 sweep lines: clean. `tools/test_privacy_check.py`: 46 tests OK.
- `python3 tools/store-listing-check.py`: `listing: clean`.
  - The release notes: 18 files, and the newest code, 42 (1.6.0), has both.
  - The App Review notes are 4,000 of 4,000 bytes. `tools/test_store_listing_check.py` passes.
- `python3 site/build.py --check`: links, assets, structured data, sitemap and timetables all good. The new watch-answer check passes in six languages, with one note (M8): Arabic's face label (A2).
- The plan's size tests, with FaceFormsTest (errata p5 #2): `:shared:testAndroidHostTest`, all 2,426, WatchFeedBuilder's and FaceFormsTest included, 0 failures. `phone-forms.json` was regenerated and is unchanged.
- The Qibla dial (Ruling 67's note): no short form changed. Urdu's tilt notice did change, so the ink model was regenerated (`qibla-ink.py`, `--check` "the model is the fonts'"). `QiblaFitTest` passes 21/21 in `:wearApp:testDebugUnitTest` (586 tests, 0 failures).
- `scripts/face-gate.sh`: PASS, 12 checks:
  - the five generators' `--check`;
  - `hijri-fit.py`, `window-fit.py`, `slot-fit.py` and `moon-fit.py`;
  - `date-fit.py` and `date-fit-test.py`;
  - `validate.sh`, where both of Google's validators pass.
- `scripts/test.sh` whole, under the `ios-sim-tests` lock: exit 0. It ran:
  - `swift test`, every suite passed, PhoneGoldensTests on the regenerated Urdu golden included;
  - `:shared` iOS 2,403 and host 2,426;
  - `:watchcore` 200 and 233;
  - `:widgetcore` 111;
  - `:wearApp` 586;
  - 0 failures in all of them.
- `git grep "draft, to native review" -- ':!docs/superpowers'`: 341 lines in 66 files, every one in a table above.
  - It prints them all until M8. That is as the Lead's erratum 2 rules: the markers stay until Mohamed's reading.
  - Once he has answered, his corrections are applied and the markers come out in one commit. That commit also changes the two `DRAFT` rules (SystemStringsTest, FaceStringsTest), empties `WATCH_ANSWER_AT_M8` and `SMART_STACK_AT_M8` in `site/build.py` (A2, A6), rewrites the Arabic fixture's KDoc, and applies A5's joins if he chooses "،". The grep then prints nothing, which the cut gate needs.

No device was used, and no emulator or simulator was touched by hand. The plugged-in phone and the work iPhone 12 were not touched. The version was not bumped (1.6.0 (42)).

**Fix round 1 ran them again** on its WIP `62829548`, with the report and the build log edited afterwards, on 6 November 2026 between about 16:20 and 16:35 SAST:
- `scripts/check-strings.sh`: `i18n: clean`, so `values-in` still equals `values-id`.
- `python3 tools/privacy-check.py`: 263 rows, 263 approved, 0 awaiting, 238 sweep lines: clean. `tools/test_privacy_check.py`: 46 tests OK.
- `python3 tools/store-listing-check.py`: `listing: clean`, with `APPLE_WORDS` on the App Store's fields and sets. `tools/test_store_listing_check.py`: 53 tests OK, the six `AppleWords` tests included.
- `python3 site/build.py --check`: all good. It gives two notes for M8, Arabic's face label (A2) and Arabic's Smart Stack (A6).
- `scripts/test.sh` whole, under the `ios-sim-tests` lock: exit 0 in 472 s.
  - `swift test`: 285 and the other suites, 0 failures.
  - `:shared` ran fresh: iOS 2,403 (`WatchSectionTest` among them) and host 2,426. These include the Turkish and Indonesian Apple rows.
  - `:watchcore` 233 on the host, 200 on iOS. `:widgetcore` 111 and 109. `:wearApp` 586. 0 failures in all of them.
- No feed word, countdown or Qibla caption changed. So the golden feed, `phone-forms.json` and the ink model are as before, and the plan's size tests are among `:shared`'s host run.
- The marker grep: still 341 lines in 66 files. The regenerated tables list every one of them (817 rows, 0 unlisted).
- No device or emulator was used. The simulator found booted (EDCDAF37) was left as it was, and so were the plugged-in phone and the work iPhone 12.

**Ruling 71's apply step ran them again** on the readers' corrections, on 6 November 2026 between about 18:05 and 18:35 SAST. `scripts/test.sh` ran on WIP `c860127f`. The report, the build log and two test strings changed after it, and the checks they touch were run again:
- `python3 tools/privacy-check.py`: red first on exactly the six rows changed in it, then 263 rows, 263 approved, 0 awaiting, 238 sweep lines: clean. `tools/test_privacy_check.py`: 46 tests OK.
- `python3 tools/store-listing-check.py`: `listing: clean`. The French Play description is 3,989 of 4,000 characters, and the Indonesian 3,976. The App Review notes are still 4,000 of 4,000 bytes. `tools/test_store_listing_check.py`: 56 tests OK, the two new App Store notes tests among them, each seen red first.
- `scripts/check-strings.sh`: `i18n: clean`, so `values-in` still equals `values-id`.
- `python3 site/build.py --check`: links, assets, structured data, sitemap and timetables all good, with the two M8 notes (A2, A6).
- The vectors (errata p5 #2, Ruling 67's note):
  - `./gradlew :shared:testAndroidHostTest --tests '*FaceFormsTest*' --tests '*WatchFeedGoldenTest*' -Ptaqwa.updateVectors` rewrote `phone-forms.json`, for the Bengali countdowns only, and the Urdu golden feed, for its face line only.
  - `scripts/face-gate.sh`: PASS, 12 checks, with `moon-fit.py` on the new forms.
  - `qibla-ink.py --check`: current. No short dial form changed.
- The Qibla page: `QiblaCaptionTest`'s T9 failed on the Indonesian reader's two forms, as above, and passes on "Datarkan jam tangan".
- `scripts/test.sh` whole, under the `ios-sim-tests` lock: exit 0 in 502 s.
  - `swift test`: 285 and the other suites, 0 failures. PhoneGoldensTests read the regenerated Urdu golden.
  - `:shared` ran fresh: host 2,427 (WatchFeedBuilder's and FaceFormsTest included) and iOS 2,403.
  - `:watchcore` host 233, run fresh on the new golden, and iOS 200. `:widgetcore` 111 and 109. `:wearApp` 586, `QiblaFitTest` and `QiblaCaptionTest` included.
  - 0 failures in all of them, and no EOFException.
- The marker grep: 347 lines in 66 files. The regenerated tables list every one (817 rows, 0 unlisted).
- No device or emulator was used. The plugged-in phone and the work iPhone 12 were not touched. The version was not bumped.

### Step 4 after M8 (6 November 2026)

Every check ran again on the final tree: Mohamed's choices applied (WIP `f24720e3`) and every marker removed (WIP `9d1d66c7`), with this report and the build log after them. This was on 6 November 2026, between about 20:35 and 21:00 SAST.
- **Red first, on the base words** (each listed under "Mohamed's answer, as applied" above):
  - `tools/i18n-check.py`: 3 problems (A1 on both Arabic Apple Watch targets, and A4).
  - `tools/store-listing-check.py`: 5 (A3).
  - `site/build.py --check`: 4 (A2, A3 twice, A6).
  - `tools/privacy-check.py`: 1 (A6's row).
  - `CountdownSpeechTest` did not compile, as `rtl` was not there yet. wearApp's `TodayModelTest` and `TilePlanTest` failed 2 tests, and Swift's `TodayTests` 3 assertions (A5). A fourth, in `GlanceTests`, was for the complication's row form, which fix round 1 after M8 put back (below).
  - The post-M8 marker rule failed on the old marked `values-fr` and `values-bn`.
- `scripts/check-strings.sh`: `test_i18n_check.py` PASS (20 tests, the two new ones among them), then `i18n: clean`, so `values-in` still equals `values-id`. `python3 tools/i18n-check.py` alone: `i18n: clean`.
- `python3 tools/privacy-check.py`: 263 rows, 263 approved, 0 awaiting, 7 policies, 244 sweep lines: clean. `tools/test_privacy_check.py` PASS.
- `python3 tools/store-listing-check.py`: `listing: clean`, with the App Review notes at 3,944 of 4,000 bytes, and 18 release-notes files, the newest code (42, 1.6.0) with both. `tools/test_store_listing_check.py` PASS, 60 tests, the four `ChosenWords` among them.
- `python3 site/build.py --check`: 164 pages in 7 languages, and links, assets, structured data, sitemap and timetables all good. It gives no M8 note now.
- Plan 2's size tests, `./gradlew :shared:testAndroidHostTest --tests '*WatchFeedBuilder*'`: `WatchFeedBuilderTest`, 18 tests, 0 failures.
- `scripts/check-versions.sh`: the versions agree, 1.5.0 (41) on the phone, the face and iOS, and 1.5.0 (100041) on the Galaxy watch app. The cut makes them 1.6.0 (42).
- Every `tools/face-*.py --check` (arch, art, centre, moon, sun): up to date. `tools/watchface/validate.sh`: both validators pass.
- The vectors: `./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors` and `./gradlew :shared:testAndroidHostTest --tests '*WatchFeedGoldenTest*' -Ptaqwa.updateVectors` rewrote the update line alone, in five files (A1). No countdown, Qibla caption or short dial form changed, so `phone-forms.json` and the ink model are as they were, and Ruling 67's note had nothing to regenerate.
- `scripts/test.sh` whole, under the `ios-sim-tests` lock (both names, 20:49 to 20:57 SAST, taken with no wait): exit 0 in 471 s.
  - It ran `test-bump-version.sh`, `test_privacy_check.py`, the privacy gate, `test_store_listing_check.py` and the listing check, all PASS or clean.
  - `swift test`: WatchCore's 286 tests (one new) and the other suites, with 0 failures.
  - `:shared` ran fresh: host 2,428 (one new) and iOS 2,404. `:watchcore` ran fresh on the regenerated vectors, host 233 and iOS 200. `:widgetcore` was up to date, at 111 and 109. `:wearApp` ran 593 (two new), `QiblaFitTest` 21 among them.
  - There were 0 failures, no EOFException, and no flake to re-run.
- `scripts/ios-build.sh`, under the same lock: BUILD SUCCEEDED in 53 s, with every bundle rule holding (TaqwaWatchWidgets 4,412 of 5,120 KB, TaqwaWatch 12,012 of 15,360 KB), and `test-watch-bundle-signing` PASS.
- `git grep -n "draft, to native review" -- ':!docs/superpowers'`: nothing, where it printed 347 lines in 66 files before.
- No device, emulator or simulator was used by hand. The plugged-in phone, the work iPhone 12, Dev 1's emulator 5572 and the booted iPhone 17 Pro Max were not touched. The version was not bumped.

## Review round 1 and fix round 1 (6 November 2026)

Review round 1 had two lenses: the spec, behaviour and evidence lens, and the code quality, platform APIs, tests, privacy, accessibility and hygiene lens. It found 0 Critical and three Important items, all on `5c36e909`.

**Importants 1 and 3: Step 2 was not run as the plan writes it. This waits for the Lead's ruling.**
- Plan 5 Task 11 Step 2 asks for five native reviewers, one subagent each for fr, tr, id, ur and bn. The execution map's quality rule says a task runs exactly as its plan writes it, and drops no step.
- This run, and its fix round, had no tool for starting subagents. So one session reviewed all five languages and applied its own musts and shoulds.
- M8 is Mohamed's reading of the English and the Arabic only. So after this task, French, Turkish, Indonesian, Urdu and Bengali get no other native reading before the cut.
- The lane cannot fix this. The Lead rules: either accept the in-session reviews, or run five independent reviewer subagents on the five tables below.
- To help that ruling, the review's second lens read the watch's interface words itself, as a separate agent, in all five languages, and found no must:
  - `wearApp` and `watchface` `values-*/strings.xml`;
  - `WatchWords.kt` FACE and SCREENS;
  - the phone's Settings › Watch rows;
  - the `#watch` support answers;
  - the French and Indonesian policy paragraphs on the watch and backups;
  - the Turkish and Bengali long rows.
- Item 2 shows the cost of the gap: no one had checked the first round's "Apple's own names" against Apple.

**Important 2: the names given as Apple's were not all Apple's. Fixed.** Each Apple feature these drafts name was checked against Apple's own localized guide for the platform it is about, on support.apple.com, on 6 November 2026. The Wear OS words were checked against Google's Wear OS help, page 6140435.

| Feature | Language | Apple's own (where) | The first round wrote | Now |
|---|---|---|---|---|
| Smart Stack | fr | « Défilement intelligent », « le défilement intelligent » in prose (watch guide `apdecf142fb9`) | « la Pile intelligente » (home row) | « le défilement intelligent » |
| Smart Stack | tr | "Akıllı Gruplama" (`apdecf142fb9`) | "Akıllı Yığın" (home row) | "Akıllı Gruplama" |
| Smart Stack | id | "Smart Stack", kept in English (`apdecf142fb9`) | "Tumpukan Pintar" (home row) | "Smart Stack" (reverted) |
| Smart Stack | ur, bn | Apple's ur-in and bn-in guides are in English | "Smart Stack" | unchanged |
| Smart Stack | ar | "الحزمة المكدسة الذكية" | "Smart Stack" (unchanged) | M8 point A6 |
| Advanced Data Protection | fr | « la protection avancée des données », lower case in prose (iCloud page `108756`) | « la Protection avancée des données » | lower case (reverted) |
| Advanced Data Protection | tr | "İleri Düzey Veri Koruma" (`108756`; its switch "İleri Düzey Veri Koruma'yı Aç") | "Gelişmiş Veri Koruma'yı" | "İleri Düzey Veri Koruma'yı" |
| Advanced Data Protection | id | "Perlindungan Data Lanjutan" (`108756`) | unchanged | unchanged, Apple's |
| Advanced Data Protection | ar | "الحماية المتقدمة للبيانات" (`108756`) | "حماية البيانات المتقدمة" (unchanged) | M8 point A6 |
| The watch face | tr | "saat kadranı" ("Saat kadranına komplikasyonlar ekleme") | "saat yüzünüzün" (App Store paragraph); "Saat yüzü" (the iPhone's Apple row, since plan 4) | "saat kadranınızın"; "Saat kadranı eklenemedi." |
| The watch face | id | "wajah jam" ("Menambahkan komplikasi ke wajah jam") | "Tampilan jam" (the iPhone's Apple row); "tampilan jam" (App Store paragraph, since Task 10) | "Wajah jam"; "wajah jam" |
| The watch face | fr | « cadran » | « cadran » | unchanged |
| Complications | fr, tr, id | complications, komplikasyon, komplikasi | the same | unchanged |
| Complications | ar | "إضافات" ("إدراج إضافات إلى واجهة الساعة") | "مضاعفات" (store copy, notes, support) | M8 point A3 |

- The Wear OS side keeps the watch's and Google's words: Indonesian "tampilan jam" (Google's own, page 6140435) and Turkish "saat yüzü" (the Galaxy watch's own `face_*`). No screen shows both: the iPhone's Watch section holds only the Apple row, and Android's only the Wear rows.
- So each language uses one word for each watch, and the report's own rule (above, "How the reviews were run") now says so. This needs no Lead's ruling. A ruling would be needed to use one word across both platforms against Apple's own word.
- M8 points A3 and A6 were built on false facts, and are corrected: Apple's Arabic word for complications is "إضافات", and Apple's Arabic names for Smart Stack and Advanced Data Protection are given. The BUILD-LOG lines that repeated the claim are corrected too. The first commit's body keeps its words, since a reviewed commit keeps its hash. This commit's body corrects it.

The checks written first, each seen red before the fix:
1. `tools/privacy-check.py`: the French and Turkish Backups rows (`3-4d-policy-backups`) took Apple's names first. The gate failed on exactly those two rows ("2 problems"), and the policies then made it pass.
2. `tools/store-listing-check.py`: `APPLE_WORDS`, the words Apple's guides do not use, with Apple's own beside them.
   - It covers what the App Store shows: every field under `## App Store`, and each localization's block in a `-appstore.txt` set.
   - French, Turkish and Indonesian have entries. Urdu and Bengali keep the English names, as Apple's guides do. Arabic waits for M8.
   - Six new tests in `tools/test_store_listing_check.py` (`AppleWords`) were red first, one by an error because the table did not exist yet. Then the committed copy was red on `tr.md`'s and `id.md`'s App Store paragraphs. All 53 tests pass now.
3. `site/build.py --check`: `check_smart_stack`, each home page's Smart Stack named as Apple's guide in that language names it. It was red on fr, tr and id. Arabic is a note until M8 (`SMART_STACK_AT_M8`).

The Apple rows in the Compose strings (`settings_add_to_apple_watch_failed`, Turkish and Indonesian with its `values-in` twin) have no check of their own. `check-strings.sh` holds the twin equal.

**The minors left** (the spec lens's list; the second lens's minors were not passed to this round):
- Turkish `complication_day_dial` keeps "Taqwa kadranı", and Urdu's keeps "تقویٰ ڈائل". The name is hidden from the pickers.
- Turkish "Sonraki vakit" stands beside the new "Sonraki namaz".
- Urdu's update line was aligned with the Galaxy watch's "اپڈیٹ", away from the phone's "اپ ڈیٹ".
- E-notes 4 puts "not affiliated" under the (i). That is for Mohamed's English reading.
- `privacy-check.py`'s new `KNOWN` entry pins the 1.4.0-40 notes file's name, and that file has 0 bytes left.
- `check_watch_answer` skips a key that is missing from the Wear resources.
- MM4's Turkish "vb." is not answered.
- **Fixed in passing:** Step 4's sweep count. It said 237 lines, and the head has 238.
- The Indonesian Apple row's "wajah jam" was on the list too, and Important 2 fixed it.

## Review round 2 and fix round 2 (6 November 2026)

Review round 2 had the same two lenses, on `f601d14d..5f052f3b`. Both found 0 Critical and one Important, the same one, carried from round 1. Round 1's Important on Apple's names is fixed: each lens checked the names on support.apple.com itself and found them as fix round 1 gives them.

**The Important, carried: Step 2 was not run as the plan writes it. It still waits for the Lead's ruling.**
- Fix round 2 had no tool for starting subagents either. It made no ruling and ran no new review of the languages.
- The ruling is the Lead's: either accept the in-session reviews, and record that in the ledger, or have five independent readers go over the five tables.
- To help it, here is what agents other than the one that applied the corrections say they read. These are their own accounts in the ledger, and none of them found a must.
  - Review round 1, second lens, in all five languages: `wearApp` and `watchface` `values-*/strings.xml`, `WatchWords.kt`'s FACE and SCREENS, the phone's Settings › Watch rows and the `#watch` support answers. Also the French and Indonesian policy paragraphs on the watch and backups, and the Turkish and Bengali "long rows" (it does not say which).
  - Review round 2, spec lens: the French, Turkish and Indonesian watch interface rows, the French policy's new paragraphs, the French, Turkish and Indonesian `#watch` answers, every App Store "What's New" block, and fix round 1's changed lines.
  - Review round 2, quality lens: every Apple feature name, against Apple's localized guides.
- By those accounts, the rows that no other agent says it has read are mainly these:
  - the Turkish, Urdu and Bengali policies (`PRIVACY.*.md`, 11 rows each), and the parts of the French and Indonesian ones that are not about the watch or backups;
  - the store listings (`docs/store/listing/*.md`, 10 to 20 rows a language), apart from the "What's New" blocks and the Turkish and Bengali long rows;
  - the home pages' rows (`site/pages/*/home.html`, 6 a language) and `meta.json`;
  - the phone's rows outside Settings › Watch, such as the About and onboarding lines, and Play's release notes.
  - Rows guarded word for word by `tools/privacy-check.py` (the location promise of Ruling 39 among them) cannot change without a failing row. That gate holds their text, not their quality as Urdu or Bengali.
- **Open points for whichever native reading comes next** (the five readers, or the Lead's own call):
  - The Turkish "vb." in MM4's text, handed to native review and not answered. Round 2's spec lens found it natural.
  - The Indonesian support answer's "Jaga pergelangan tetap datar" beside the Qibla screen's `holdLevel`, "Pegang jam mendatar" (Ruling 68.4's hand-off).
  - The French « vignette » for a Tile. Round 2's spec lens reports that Google's and Samsung's French help say « cartes ».
  - The Indonesian face editor's slots. This review made them "Komplikasi kiri/kanan", for the store copy's word, but they were "Detail kiri/kanan" before. Round 2's quality lens reports "detail" as Google's own Indonesian word (page 6140435). By this report's own rule, Wear OS text takes Google's words, so the reader weighs the two.
  - Round 1's points still standing: Turkish `complication_day_dial` "Taqwa kadranı" and Urdu "تقویٰ ڈائل"; Turkish "Sonraki vakit" beside "Sonraki namaz"; Urdu's update line "اپڈیٹ" against the phone's "اپ ڈیٹ".

**The minors fixed by fix round 2.** These are the checks' own minors. No phrase changed, so the tables below are as they were.
- `tools/store-listing-check.py`'s `APPLE_WORDS` read Turkish capitals as casefold does, so "AKILLI YIĞIN" and "GELİŞMİŞ VERİ KORUMA" passed. It now folds Turkish I to ı and İ to i first.
  - `AppleWords.test_a_turkish_word_apple_does_not_use_fails_in_turkish_capitals` was red first (2 failing subtests). Now 54 tests pass.
- `site/build.py --check`'s Smart Stack check now reads the watch card only (the article that names Apple Watch), in any case, with the same Turkish capitals.
  - Before, Apple's own capital (« Défilement intelligent ») failed it, and a wrong name in the card passed while the right one was elsewhere on the page.
  - A missing home page, or a home page with no watch card, is now a problem, not a skip. A missing page already stopped the build before any check ran.
  - The watch answer's check now fails when the watch's resources lack `face_add` or `open_taqwa_on_phone`, where it skipped them. A missing support page is also a problem now.
  - Each case was seen red first, in a scratch copy of the site with its pages changed: the capital, the wrong card, Turkish capitals and a missing key. Then each was seen right with the fix, and the copy was checked equal to the head again.
- `WatchWordsTest`'s note named the phone's `tasbeeh_hint` but never read it. `WatchWordsResourcesTest.theWatchesTapIsThePhonesOwnWord` now holds both watches' tap word to the phone's `tasbeeh_hint`, in every `values*` directory.
  - It was red first, with Urdu's two hints changed to تھپتھپائیں for the run, while the two hints were still alike. It passes on the head.
- This report now notes that Arabic, Urdu and Bengali write the app's name in their own script, as the phone does.

**The minors left:**
- `APPLE_WORDS` refuses the French « Pile intelligente », and its message calls it "not Apple's word". Round 2's spec lens reports that it is Apple's French for the iPhone's Smart Stack (support page 118610). So the rule is too wide for text about the iPhone. No text trips it today.
- `LOCALIZATION_LANGS` pairs the App Store's localizations with languages by position.
- The site's checks have no unit tests. `site/test_*.py` run in the Pages workflow, and a new file would need a line there too.
- The native points above, which are a native reader's to weigh.
- Round 1's left minors, apart from those fixed above: E-notes 4 and 5, for Mohamed's English; `privacy-check.py`'s `KNOWN` entry pins the 1.4.0-40 notes file's name, and that file has 0 bytes left; the Compose Apple rows have no check of their own.

## Review round 3 and fix round 3 (6 November 2026)

Review round 3 had the same two lenses, on `f601d14d..1e636f7e`. Both found 0 Critical and one Important, the same one, carried a third time. Fix round 2's checks were tried again and hold: the Turkish capitals, the watch card, the missing key and the tap word.

**The Important, carried: Step 2 was not run as the plan writes it. It still waits for the Lead's ruling.**
- Fix round 3 had no tool for starting subagents either. It made no ruling and changed no phrase in any language, so the tables below are as they were.
- The ledger has no Lead's line on it since fix round 2. The ruling is the Lead's:
  - (a) accept the in-session reviews, and record that in the ledger;
  - or (b) have independent readers go over the tables, one for each language, as Step 2 asks.
- (b) can now be narrowed further. Round 3's spec lens is an agent that did not apply the corrections. It read the rows that round 2's list called unread, and found no must:
  - the Turkish, Urdu and Bengali policies in full: the opening, "Your watch", the location, recitation and Backups paragraphs, and the SDK line;
  - the new and reworded lines of the five store listings: the wrist paragraphs, the trust bullets and the reworded Play bullets;
  - the home pages' rows and `meta.json`, in all five;
  - the phone's About, onboarding and `location_privacy_note` rows, in all five;
  - Play's `1.6.0-42.txt` and `1.6.0-100042-wear.txt`, in all five.
- By the accounts in the ledger, these are now the only rows that no agent other than the one that applied the corrections says it has read:
  - the Indonesian policy outside "Your watch" and Backups, rows 97 to 101, 104, 105 and 107 of its table. Round 2's spec lens names the "fr policy" in its ledger line, so round 2's list above was too wide for the French one.
  - `WatchWords.kt`'s four COUNTDOWN rows, in all five. Round 1's second lens names FACE and SCREENS only.
  - In the Apple strings: the widget's description (`next_prayer_widget_description`) and the iPhone's location permission text (`NSLocationWhenInUseUsageDescription`), in all five. The Apple strings' other rows repeat the watch's own words in WatchWords' SCREENS, which round 1's second lens read.
- Each of these readings was one agent reading all five languages, not one reader for each. Only the ruling can accept that part of Step 2 or close it.

**Round 3's nits, left as notes.** Step 2 leaves every nit as a note. Each is also under its language above.
- Bengali: the policy calls the watch "তাকে" twice. That word is for a person.
- Turkish "onun verilerini" and Indonesian "datanya" can be read as the watch's data. That is the reading the Bengali should removed from six rows.
  - The Bengali was a should because its rows said "ঘড়ির তথ্য", "the watch's data", outright, and its home row could be read as Apple's data.
  - The Turkish and Indonesian leave the reading open, as the English "its data" does. So they stay nits.
  - The reason given above for leaving them, that these are privacy rows, is not the plan's. A privacy row can change, its row in `tools/privacy-check.py` first, as the Bengali six did.
  - A reader who changes them changes all six Turkish rows, and all seven Indonesian ones with the policy's opening (and the `values-in` twin), together with their privacy-check rows.

**Round 3's other minors, left.** This round was given the Important alone. These are the two lenses' own minors, from their ledger lines:
- E-notes 4's trim is not true: the (i) card does not say "not affiliated", and only About these times does. It is flagged in Mohamed's E-notes list above.
- The Arabic `settings_add_to_apple_watch` has had no marker since `b42ebc35`, while the other languages' rows are marked. It is in the Arabic table (row 2), so Mohamed reads it at M8, but the marker grep does not hold it.
- `WatchWords`' JUMUAH map is not in the tables. It is the phone's own words, and a test holds it.
- The scratch tools named near the top, `inventory.py` and `render.py`, are uncommitted files in this workflow's session scratchpad. A later session, or a restart that clears the scratchpad, does not have them. So "they can be run again" after M8 holds only while that scratchpad lasts.
- `site/build.py`'s Smart Stack check takes the first article that names the Apple Watch as the watch card. An earlier article that names it would make the check read the wrong card, which gives a false red.
- `folded()` and its Turkish map are written twice, in `tools/store-listing-check.py` and in `site/build.py`. And `wear_string` opens the watch's resource file without a guard.
- Indonesian `slot_left`/`slot_right` "Komplikasi kiri/kanan" goes against this report's Wear rule. Round 3's quality lens read Google's Indonesian Wear OS help (6140435) itself: "detail" for complications, "tampilan jam" for the face. This is already an open native point above.
- Carried from rounds 1 and 2: `APPLE_WORDS` refuses « Pile intelligente », a rule too wide for text about the iPhone; `LOCALIZATION_LANGS` pairs by position; the site's checks have no unit tests; `KNOWN` pins the 1.4.0-40 notes file, which has 0 bytes left; the Compose Apple rows have no check of their own; and the open native points.

## Review round 4 and fix round 4 (6 November 2026)

Review round 4 had the same two lenses, on `f601d14d..cfd147d3`. Both found 0 Critical and one Important, the same one, carried a fourth time. Their re-runs, each in its own copy of `cfd147d3`, all passed: shared 2427/0, watchcore 233/0, widgetcore 111/0, wearApp 586/0, the Swift WatchCore tests with 0 failures, privacy-check 263/263, store-listing-check clean, check-strings clean, `site/build.py --check` good with the two M8 notes, and `qibla-ink.py --check` current.

**The Important, carried: Step 2 was not run as the plan writes it. Only the Lead's ruling can close it now.**
- Fix round 4 had no tool for starting subagents either. It made no ruling and changed no phrase in any language, so the tables below are as they were.
- The ledger has no Lead's line on it, up to 17:40 on 6 November.
- Round 4's spec lens did not apply the corrections. It read the rows that round 3's list above calls unread:
  - the Indonesian policy's rows 97 to 101, 104, 105 and 107;
  - `WatchWords.kt`'s four COUNTDOWN rows, in all five;
  - the Apple strings' `next_prayer_widget_description` and `NSLocationWhenInUseUsageDescription`, in all five.
- It read them against the English and against the phone's own words: `prayer_a11y_countdown` and `prayer_a11y_countdown_soon`, `onboarding_location_body` and `location_privacy_note`. It found no must and no should.
- So round 3's list of unread rows is now empty. By the ledger's accounts, every one of the 817 rows has been read by at least one agent other than the one that applied the corrections. The narrowed (b) has nothing left to read.
- What is left is the Lead's choice:
  - (a) accept the in-session reviews, and record that in the ledger;
  - or (b) full independent readings, one reader for each language, as Step 2 asks.
- Each separate reading so far was one agent reading all five languages, not one reader for each language. Only the ruling can accept that part of Step 2 or close it. Another fix round can add only another paragraph like this one.

**Round 4's nit, left as a note.** Step 2 leaves every nit as a note.
- Indonesian policy, row 98: « Ia memakai internet » calls the app "ia", a word mostly used for a person, like the Bengali "তাকে" above. « Aplikasi ini » or « Taqwa » reads better.
  - A reader who changes it changes its row in `tools/privacy-check.py` first (line 704), as the Bengali six did.

**Round 4's other minors, left.** This round was given the Important alone. These are the two lenses' own minors, from their ledger lines:
- E-notes 4 is still untrue in the App Review notes file (`docs/store/app-review/2026-10-30-notes-1.4.0-40.txt`, line 19). The quality lens asks for it to be fixed before the cut, not only flagged. It is Mohamed's English, so it stays in his E-notes list for M8, and the cut must not ship the old wording.
- The sentence near the top said the scratch tools could be run again after M8. It now says they last only as long as the session scratchpad does. This is fixed here, in the report's text only.
- Carried from rounds 1 to 3:
  - the Indonesian slots, Komplikasi against Google's "detail";
  - the seven languages' "or" map written twice (`WatchWordsTest.kt:126` and `WatchWordsResourcesTest.kt:75`), and `folded()` written twice;
  - the Smart Stack check's first-article card pick, and `wear_string`'s unguarded open;
  - the site's checks have no unit tests, and `LOCALIZATION_LANGS` pairs by position;
  - `KNOWN` pins the 1.4.0-40 notes file, which has 0 bytes left;
  - the Compose Apple rows have no check of their own;
  - and the open native points.

## After M8: review round 1 and fix round 1 (6 November 2026)

Review round 1 after M8 read `6f8e2cb7..17201ab3` through two lenses. The native Arabic and Urdu reading and text hygiene lens found it clean, with 6 minors. The spec and evidence lens found 0 Critical, one Important and 6 minors.

**The Important: A5 went past its three places. Fixed by putting the fourth back.**
- The A5 brief changes the join "in all three places": the Apple Watch's Today, the Wear Tile and the phone's prayer rows, "Only those spoken joins". Mohamed's A5 question named the same three.
- `17201ab3` also changed the Apple Watch complication's row form, `Glance.spokenWithoutCountdown`, and left it open for the Lead. No line from the Lead in the ledger takes that place, so the brief stands.
- `Glance.swift` is now as it was on `6f8e2cb7`. A complication entry without its countdown says "العصر, ١٦:١٠" in Arabic, as before M8.
- `GlanceTests.testArabicKeepsTheNameAndTheDigits` holds that. It was red first on `17201ab3`'s code, which gave "العصر، ١٦:١٠", and green with `Glance.swift` put back.
- The A5 section above and the BUILD-LOG now say the complication keeps ", ", with nothing left open. Giving it the Arabic comma too would need a new question to Mohamed or a ruling.

**Fixed from the minors:**
- Three test comments still called the dial's short forms drafts. They no longer do: `QiblaModelTest.kt:38-39`, `WatchFeedJsonTest.kt:539` and `WatchScreenWordsTest.kt:98`.
- The App Store notes' header (`docs/store/release-notes/1.6.0-42-appstore.txt:2`) named "the other five" before the Arabic. It now names Mohamed's Arabic first. The header is not pasted.
- The A6 line's cite is `PRIVACY.ar.md:73-74`.

**Minors left:**
- The "phone's own words" header comment in `wearApp` and `watchface` `values-*` now also heads the watch's own phrases.
- Other Arabic and Urdu spoken joins keep ", ": `NextPrayerContent.swift:89`, `NextPrayerSource.kt:119`, `DatesSource.kt:77`, and the tasbeeh rows by design. None is one of A5's three places.
- The feed's `rowNext` stays "التالية" on both watches' rows, as A4 was briefed.
- The watch UI's `LanguagesTests` (`iosApp/TaqwaWatchUITests`) A1 and A5 expectations were neither built nor run. `scripts/test.sh` does not run that UI test target.
- E-notes 4's question says the GitHub link is 77 bytes. The line that went is 76. The question stays as it was put to him.
- Step 4 after M8's 164 pages come from this worktree's own generated timetable data. A fresh generate gives 168.
- The `docs/superpowers` drivers and records keep the old spelling, as records.
- Row `3-4d-policy-backups` has no guard against its old Arabic.
- Two comment lines were rewrapped without need.

**The checks, on the final tree (6 November 2026, about 21:20 to 21:41 SAST):**
- `swift test` in WatchCore: 286 tests, 0 failures.
- `scripts/check-strings.sh`: `test_i18n_check.py` PASS, then `i18n: clean`. `python3 tools/i18n-check.py`: `i18n: clean`.
- `python3 tools/privacy-check.py`: 263 rows, 263 approved, 7 policies, 244 sweep lines: clean.
- `python3 tools/store-listing-check.py`: `listing: clean`. The App Review notes are 3,944 of 4,000 bytes. `test_store_listing_check.py`: OK.
- `python3 site/build.py --check`: 164 pages in 7 languages, all good, with no M8 note.
- `./gradlew :shared:testAndroidHostTest --tests '*WatchFeedBuilder*' --rerun`: 18 tests, 0 failures.
- `scripts/check-versions.sh`: 1.5.0 (41) on the phone, the face and iOS, and 1.5.0 (100041) on the Galaxy watch app. There is no bump.
- `tools/face-arch.py`, `face-art.py`, `face-centre.py`, `face-moon.py` and `face-sun.py --check`: up to date. `tools/watchface/validate.sh`: both validators pass.
- `scripts/test.sh` whole, under `ios-sim-tests` (both names, 21:37 SAST, after a 17-minute wait): exit 0.
  - The bump, privacy and listing tests PASS, and swift 286 with 0 failures.
  - `:watchcore` ran on the changed tests: host 233 and iOS 200.
  - `:shared` (2,428 and 2,404) and `:widgetcore` (111 and 109) were up to date or from the cache. Their inputs had not changed.
  - `:wearApp` was up to date, because its comment edit compiled to the same classes. It was then re-run with `--rerun`: 593 tests, 0 failures.
- `scripts/ios-build.sh`, under the same lock: BUILD SUCCEEDED, every bundle rule holds (TaqwaWatchWidgets 4,412 of 5,120 KB, TaqwaWatch 12,012 of 15,360 KB), and `test-watch-bundle-signing` PASS.
- `git grep -n "draft, to native review" -- ':!docs/superpowers'` prints nothing.
- No device, emulator or simulator was used by hand. The version was not bumped.

## The tables: every new phrase, per language

Each table lists every row of Step 1 for its language, after the corrections above: where it is (file and key, or file and lines), the English, the draft, and whether a marker covers it. "as row N" is a phrase written again elsewhere: the feed words, the resources and the Apple strings carry the same line, and the App Store description repeats the Play paragraphs. Unmarked rows are the phone's own words, or lines a store field cannot mark (Play's release notes, listed here). The Arabic table comes first, for Mohamed.

### Arabic (Mohamed reads it, M8): 132 rows

| # | Where | English | Draft | Marker |
|---|---|---|---|---|
| 1 | compose/values-ar/strings.xml onboarding_location_body (:19) | Prayer times depend on where you are. Everything is calculated on your device. Only your own devices can see your location. | تعتمد المواقيت على موقعك. كل الحساب يجري على جهازك. لا ترى موقعك إلا أجهزتك أنت. | yes |
| 2 | compose/values-ar/strings.xml settings_add_to_apple_watch (:133) | Add to Apple Watch | أضِف إلى Apple Watch | no |
| 3 | compose/values-ar/strings.xml settings_add_to_apple_watch_failed (:135) | The face could not be added. Try again from the Watch app. | تعذّرت إضافة واجهة الساعة. حاول مجددًا من تطبيق Watch. | yes |
| 4 | compose/values-ar/strings.xml settings_group_watch (:142) | WATCH | الساعة | yes |
| 5 | compose/values-ar/strings.xml settings_watch_install (:144) | Install on your watch | ثبّت على ساعتك | yes |
| 6 | compose/values-ar/strings.xml settings_watch_face (:146) | Add the Taqwa face | أضِف واجهة تقوى | yes |
| 7 | compose/values-ar/strings.xml settings_watch_continue (:148) | Continue on your watch | تابِع على ساعتك | yes |
| 8 | compose/values-ar/strings.xml settings_watch_unreachable (:150) | Couldn’t reach your watch | تعذّر الوصول إلى ساعتك | yes |
| 9 | compose/values-ar/strings.xml about_stays_title (:165) | Stays on your devices | يبقى على أجهزتك | yes |
| 10 | compose/values-ar/strings.xml about_stays_body (:167) | Only your own devices can see your location, settings, bookmarks and counts. | لا ترى موقعك وإعداداتك وعلاماتك وعدّادك إلا أجهزتك أنت. | yes |
| 11 | compose/values-ar/strings.xml about_online_title (:169) | Online only for what you choose | متصل فقط لما تختاره | yes |
| 12 | compose/values-ar/strings.xml about_online_body_android (:171) | The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted so only your phone and watch can read it. | يُستخدم الإنترنت للتلاوات التي تختار تنزيلها، من مستودع تقوى العام على GitHub. وإن أضفت تقوى إلى ساعتك، فقد تنقل Google بياناتها عبر الإنترنت، مشفّرةً فلا يقرؤها إلا هاتفك وساعتك. | yes |
| 13 | compose/values-ar/strings.xml about_online_body_ios (:173) | The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted so only your phone and watch can read it. | يُستخدم الإنترنت للتلاوات التي تختار تنزيلها، من مستودع تقوى العام على GitHub. وإن أضفت تقوى إلى ساعتك، فقد تنقل Apple بياناتها عبر الإنترنت، مشفّرةً فلا يقرؤها إلا هاتفك وساعتك. | yes |
| 14 | compose/values-ar/strings.xml location_privacy_note (:261) | Coordinates are stored on your device and used only to calculate times. Only your own devices can see your location. | تُحفظ الإحداثيات على جهازك وتُستخدم لحساب المواقيت فقط. لا ترى موقعك إلا أجهزتك أنت. | yes |
| 15 | wearApp/res/values-ar/strings.xml app_name (:4) | Taqwa | تقوى | no |
| 16 | wearApp/res/values-ar/strings.xml complication_next_prayer (:5) | Next prayer | الصلاة القادمة | no |
| 17 | wearApp/res/values-ar/strings.xml complication_mark (:6) | Taqwa | تقوى | no |
| 18 | wearApp/res/values-ar/strings.xml complication_mark_description (:7) | Taqwa | تقوى | no |
| 19 | wearApp/res/values-ar/strings.xml tile_label (:8) | Prayer times | مواقيت الصلاة | no |
| 20 | wearApp/res/values-ar/strings.xml open_taqwa_on_phone (:10) | Open Taqwa on your phone | افتح تقوى على هاتفك | yes |
| 21 | wearApp/res/values-ar/strings.xml update_taqwa_on_watch (:11) | Update Taqwa on your watch | حدِّث تقوى على ساعتك | yes |
| 22 | wearApp/res/values-ar/strings.xml complication_day (:12) | Prayer day | يوم الصلاة | yes |
| 23 | wearApp/res/values-ar/strings.xml complication_day_dial (:13) | Taqwa face: day dial | واجهة تقوى: قرص اليوم | yes |
| 24 | wearApp/res/values-ar/strings.xml complication_dates (:14) | Dates | التواريخ | yes |
| 25 | wearApp/res/values-ar/strings.xml complication_day_title (:15) | Day | اليوم | yes |
| 26 | wearApp/res/values-ar/strings.xml complication_day_description (:16) | Prayer times today | مواقيت الصلاة اليوم | yes |
| 27 | wearApp/res/values-ar/strings.xml preview_prayer_label (:17) | ASR | العصر | yes |
| 28 | wearApp/res/values-ar/strings.xml preview_prayer_name (:18) | Asr | العصر | yes |
| 29 | wearApp/res/values-ar/strings.xml preview_prayer_time (:19) | 16:10 | 16:10 | yes |
| 30 | wearApp/res/values-ar/strings.xml preview_countdown (:20) | in 1 h 12 min | بعد 1 س 12 د | yes |
| 31 | wearApp/res/values-ar/strings.xml preview_day_times (:21) | Fajr 4:33, Sunrise 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24 | الفجر 4:33، الشروق 6:01، الظهر 12:33، العصر 16:10، المغرب 19:04، العشاء 20:24 | yes |
| 32 | wearApp/res/values-ar/strings.xml preview_hijri (:22) | 6 Jumada al-Ula 1448 | 6 جمادى الأولى 1448 | yes |
| 33 | wearApp/res/values-ar/strings.xml preview_hijri_day_month (:23) | 6 Jumada al-Ula | 6 جمادى الأولى | yes |
| 34 | wearApp/res/values-ar/strings.xml preview_gregorian (:24) | SUN 18 OCT | الأحد 18 أكتوبر | yes |
| 35 | wearApp/res/values-ar/strings.xml preview_weekday (:25) | SUN | الأحد | yes |
| 36 | wearApp/res/values-ar/strings.xml preview_day_number (:26) | 18 | 18 | yes |
| 37 | wearApp/res/values-ar/strings.xml tile_description (:27) | Today’s prayers and the next one counted down | صلوات اليوم والعد التنازلي للصلاة القادمة | yes |
| 38 | wearApp/res/values-ar/strings.xml face_add (:30) | Add the Taqwa face | أضِف واجهة تقوى | yes |
| 39 | wearApp/res/values-ar/strings.xml face_about (:32) | The next prayer and its countdown on your watch face. | الصلاة القادمة وعدّها التنازلي على واجهة ساعتك. | yes |
| 40 | wearApp/res/values-ar/strings.xml face_active (:34) | Taqwa is your watch face. | تقوى هي واجهة ساعتك. | yes |
| 41 | wearApp/res/values-ar/strings.xml face_choose_it (:36) | Touch and hold your watch face, then choose Taqwa. | المس واجهة ساعتك مطوّلًا، ثم اختر تقوى. | yes |
| 42 | wearApp/res/values-ar/strings.xml face_failed (:38) | Couldn’t add the face. Try again later. | تعذّرت إضافة الواجهة. حاول مجددًا لاحقًا. | yes |
| 43 | watchface/res/values-ar/strings.xml watch_face_name (:4) | Taqwa | تقوى | no |
| 44 | watchface/res/values-ar/strings.xml slot_next_prayer (:5) | Next prayer | الصلاة القادمة | no |
| 45 | watchface/res/values-ar/strings.xml style (:7) | Style | النمط | yes |
| 46 | watchface/res/values-ar/strings.xml style_arch (:8) | Arch | المحراب | yes |
| 47 | watchface/res/values-ar/strings.xml style_ring (:9) | Ring | الحلقة | yes |
| 48 | watchface/res/values-ar/strings.xml style_sun (:10) | Sun | الشمس | yes |
| 49 | watchface/res/values-ar/strings.xml style_moon (:11) | Moon | القمر | yes |
| 50 | watchface/res/values-ar/strings.xml accent (:12) | Colour | اللون | yes |
| 51 | watchface/res/values-ar/strings.xml accent_amber (:13) | Amber | كهرماني | yes |
| 52 | watchface/res/values-ar/strings.xml accent_pearl (:14) | Pearl | لؤلؤي | yes |
| 53 | watchface/res/values-ar/strings.xml accent_sage (:15) | Sage | ميرمية | yes |
| 54 | watchface/res/values-ar/strings.xml accent_night (:16) | Night | ليلي | yes |
| 55 | watchface/res/values-ar/strings.xml slot_day (:17) | Prayer day | يوم الصلاة | yes |
| 56 | watchface/res/values-ar/strings.xml slot_dates (:18) | Dates | التواريخ | yes |
| 57 | watchface/res/values-ar/strings.xml slot_dates_short (:19) | Date and moon | التاريخ والقمر | yes |
| 58 | watchface/res/values-ar/strings.xml slot_countdown (:20) | Time to the next prayer | الوقت المتبقي على الصلاة القادمة | yes |
| 59 | watchface/res/values-ar/strings.xml slot_day_dial (:21) | Prayer day dial | قرص يوم الصلاة | yes |
| 60 | watchface/res/values-ar/strings.xml slot_left (:22) | Left complication | الإضافة اليسرى | yes |
| 61 | watchface/res/values-ar/strings.xml slot_right (:23) | Right complication | الإضافة اليمنى | yes |
| 62 | iosApp/TaqwaWatch/ar.lproj/Localizable.strings open_taqwa_on_phone (:2) | Open Taqwa on your phone | افتح تقوى على هاتفك | yes |
| 63 | iosApp/TaqwaWatch/ar.lproj/Localizable.strings update_taqwa_on_watch (:5) | Update Taqwa on your watch | حدّث تقوى على ساعتك | yes |
| 64 | iosApp/TaqwaWatch/ar.lproj/InfoPlist.strings CFBundleDisplayName (:2) | Taqwa | تقوى | no |
| 65 | iosApp/TaqwaWatchWidgets/ar.lproj/Localizable.strings next_prayer_widget_name (:2) | Next prayer | الصلاة التالية | no |
| 66 | iosApp/TaqwaWatchWidgets/ar.lproj/Localizable.strings next_prayer_widget_description (:5) | The next prayer, its time and the countdown. | الصلاة التالية ووقتها والعد التنازلي. | yes |
| 67 | iosApp/TaqwaWatchWidgets/ar.lproj/Localizable.strings open_taqwa_on_phone (:8) | Open Taqwa on your phone | افتح تقوى على هاتفك | yes |
| 68 | iosApp/TaqwaWatchWidgets/ar.lproj/Localizable.strings update_taqwa_on_watch (:11) | Update Taqwa on your watch | حدّث تقوى على ساعتك | yes |
| 69 | iosApp/TaqwaWatchWidgets/ar.lproj/InfoPlist.strings CFBundleDisplayName (:2) | Taqwa | تقوى | no |
| 70 | iosApp/iosApp/ar.lproj/InfoPlist.strings NSLocationWhenInUseUsageDescription (:4) | Taqwa uses your location to calculate prayer times on your device. Only your own devices can see your location. | يستخدم تقوى موقعك لحساب مواقيت الصلاة على جهازك. لا ترى موقعك إلا أجهزتك أنت. | yes |
| 71 | WatchWords.kt COUNTDOWN.hoursMinutes (:70) | in %1$s h %2$s min | بعد %1$s س %2$s د | no |
| 72 | WatchWords.kt COUNTDOWN.hours (:71) | in %1$s h | بعد %1$s س | no |
| 73 | WatchWords.kt COUNTDOWN.minutes (:72) | in %1$s min | بعد %1$s د | no |
| 74 | WatchWords.kt COUNTDOWN.underAMinute (:73) | in under a minute | بعد أقل من دقيقة | no |
| 75 | WatchWords.kt FACE.add (:122) | Add the Taqwa face | أضِف واجهة تقوى | yes |
| 76 | WatchWords.kt FACE.about (:123) | as row 39 | as row 39 | yes |
| 77 | WatchWords.kt FACE.active (:124) | Taqwa is your watch face. | تقوى هي واجهة ساعتك. | yes |
| 78 | WatchWords.kt FACE.chooseIt (:125) | as row 41 | as row 41 | yes |
| 79 | WatchWords.kt FACE.failed (:126) | as row 42 | as row 42 | yes |
| 80 | WatchWords.kt SCREENS.openOnPhone (:192) | Open Taqwa on your phone | افتح تقوى على هاتفك | yes |
| 81 | WatchWords.kt SCREENS.updateOnWatch (:193) | Update Taqwa on your watch | حدِّث تقوى على ساعتك | yes |
| 82 | WatchWords.kt SCREENS.bezelHint (:194) | Turn the bezel or tap | أدِر الإطار أو اضغط | yes |
| 83 | WatchWords.kt SCREENS.crownHint (:195) | Turn the crown or tap | أدِر التاج أو اضغط | yes |
| 84 | WatchWords.kt SCREENS.undoAction (:196) | Take one away | أنقِص واحدة | yes |
| 85 | WatchWords.kt SCREENS.phrasesAction (:197) | Choose a dhikr | اختر ذكرًا | yes |
| 86 | WatchWords.kt SCREENS.holdLevel (:198) | Hold your wrist level | أبقِ معصمك مستويًا | yes |
| 87 | WatchWords.kt SCREENS.distanceOnDial (:199) | %1$s km | %1$s كم | yes |
| 88 | WatchWords.kt SCREENS.qiblaDetailOnDial (:200) | %1$s° · %2$s km | %1$s° · %2$s كم | yes |
| 89 | WatchWords.kt SCREENS.countSpoken (:201) | %1$s of %2$s | %1$s من %2$s | yes |
| 90 | WatchWords.kt SCREENS.nextSpoken (:202) | %1$s. Next, %2$s | %1$s. التالي: %2$s | yes |
| 91 | WatchWords.kt SCREENS.setCompleteSpoken (:203) | %1$s. Round complete | %1$s. اكتملت الجولة | yes |
| 92 | WatchWords.kt SCREENS.roundSpoken (:204) | Round complete. %1$s, round %2$s | اكتملت الجولة. %1$s، الجولة %2$s | yes |
| 93 | WatchWords.kt SCREENS.nameRoundSpoken (:205) | %1$s, round %2$s | %1$s، الجولة %2$s | yes |
| 94 | WatchWords.kt SCREENS.countAction (:206) | Count | عُدّ | yes |
| 95 | WatchWords.kt SCREENS.turnRight (:207) | up to 180°: Turn right about %1$s degrees | up to 90°: استدر يمينًا حوالي %1$s درجة / up to 105°: استدر يمينًا حوالي %1$s درجات / up to 180°: استدر يمينًا حوالي %1$s درجة | yes |
| 96 | WatchWords.kt SCREENS.turnLeft (:212) | up to 180°: Turn left about %1$s degrees | up to 90°: استدر يسارًا حوالي %1$s درجة / up to 105°: استدر يسارًا حوالي %1$s درجات / up to 180°: استدر يسارًا حوالي %1$s درجة | yes |
| 97 | PRIVACY.ar.md :3-3 | _Last updated 30 October 2026. Applies to Taqwa 1.0.0 and later on Android and iOS._ | _آخر تحديث: 30 أكتوبر 2026. تنطبق على تقوى 1.0.0 وما بعده على أندرويد وiOS._ | no |
| 98 | PRIVACY.ar.md :5-8 | Taqwa is a free Islamic app with no account, no ads and no analytics. It works offline. It uses the internet to download Quran recitations, and only when you ask it to. If you add Taqwa to your watch, its data may travel over the internet too, encrypted, as below. | تقوى تطبيق إسلامي مجاني بلا حساب ولا إعلانات ولا تحليلات. يعمل دون اتصال بالإنترنت. ويستخدم الإنترنت لتنزيل تلاوات القرآن، وفقط حين تطلب ذلك. وإن أضفت تقوى إلى ساعتك، فقد تنتقل بياناته عبر الإنترنت أيضًا، مشفّرةً، كما يأتي أدناه. | yes |
| 99 | PRIVACY.ar.md :10-10 | ## What stays on your devices | ## ما يبقى على أجهزتك | yes |
| 100 | PRIVACY.ar.md :12-13 | Everything the app knows is stored on your own devices: | كل ما يعرفه التطبيق مخزّن على أجهزتك أنت: | yes |
| 101 | PRIVACY.ar.md :21-23 | None of this is sent to us or to anyone else. There is no server behind Taqwa and no account to create. Deleting Taqwa from your phone and your watch deletes all of it. | لا يُرسل شيء من هذا إلينا ولا إلى أي أحد آخر. لا خادم وراء تقوى ولا حساب تنشئه. وحذف تقوى من هاتفك وساعتك يحذف كل ذلك. | yes |
| 102 | PRIVACY.ar.md :28-28 | ## Your watch | ## ساعتك | yes |
| 103 | PRIVACY.ar.md :30-35 | If you add Taqwa to your watch, your phone gives it your city, the Qibla's direction and distance, your area's compass correction, the coming month's prayer times as the app writes them, and your own tasbeeh phrases, with a random number made once on your phone that tells your watch which phone sent them. When your phone and watch are not directly connected, Google (Android) or Apple (iPhone) may carry this data over the internet, end-to-end encrypted so only your phone and watch can read it. | إن أضفت تقوى إلى ساعتك، يعطيها هاتفك مدينتك، واتجاه القبلة ومسافتها، وتصحيح البوصلة في منطقتك، ومواقيت الصلاة للشهر القادم كما يكتبها التطبيق، وأذكار التسبيح التي كتبتها بنفسك، ومعها رقم عشوائي يُنشأ مرة واحدة على هاتفك، تعرف به ساعتك أيّ هاتف أرسلها. وحين لا يكون هاتفك وساعتك متصلين اتصالًا مباشرًا، قد تنقل Google (أندرويد) أو Apple (iPhone) هذه البيانات عبر الإنترنت، مشفّرةً من طرف إلى طرف فلا يقرؤها إلا هاتفك وساعتك. | yes |
| 104 | PRIVACY.ar.md :39-42 | The app asks for your location only to compute prayer times and the Qibla direction, and only if you allow it. Picking a city from the built-in list works just as well and needs no permission. Your location is used on the device. Only your own devices can see your location. | يطلب التطبيق موقعك فقط لحساب مواقيت الصلاة واتجاه القبلة، وفقط إن سمحت له. واختيار مدينة من القائمة المضمّنة يفي بالغرض تمامًا ولا يحتاج إلى أي إذن. يُستخدم موقعك على الجهاز. لا ترى موقعك إلا أجهزتك أنت. | yes |
| 105 | PRIVACY.ar.md :56-59 | Once you have opened anything to do with recitation, whether the reciter list, the Recitation settings or a download, the app also checks once a day for an updated list of reciters, from the same repository. That request is the same for everyone and reveals nothing but your IP address. Until you first use recitation, the app itself makes no network request at all. | وبعد أن تستخدم التلاوة، يتحقق التطبيق مرة في اليوم من وجود قائمة قرّاء محدّثة، من المستودع نفسه. وهذا الطلب واحد للجميع ولا يكشف سوى عنوان IP الخاص بك. وحتى تستخدم التلاوة للمرة الأولى، لا يرسل التطبيق نفسه أي طلب عبر الشبكة على الإطلاق. | yes |
| 106 | PRIVACY.ar.md :74-82 | Taqwa opts out of Android's app backup, so your location and settings are not copied to Google. On iOS, your settings, bookmarks, counts and downloaded recitations are excluded from iCloud backup; the only files of Taqwa's own that are backed up are the widgets' small caches, the upcoming prayer times and the hundred ayahs the daily-ayah widget rotates through, neither of which contains your location. If you add Taqwa to an Apple Watch, iOS keeps the last copy your iPhone sent it, which names your city, and that copy may be in your iPhone's backup. An iCloud backup is encrypted with keys Apple keeps, unless you turn on Advanced Data Protection, which encrypts it end to end; a backup to a computer is encrypted only if you choose to encrypt it. | يستثني تقوى نفسه من النسخ الاحتياطي للتطبيقات في أندرويد، فلا يُنسخ موقعك ولا إعداداتك إلى Google. وعلى iOS، تُستثنى إعداداتك وعلاماتك وعدّاداتك والتلاوات المنزّلة من نسخ iCloud الاحتياطي؛ وكل ما يُنسخ احتياطيًا من ملفات تقوى نفسه هو ذاكرتا الأدوات الصغيرتان: مواقيت الصلاة القادمة، والآيات المئة التي تتناوب عليها أداة «آية اليوم»، وليس في أيٍّ منهما موقعك. وإن أضفت تقوى إلى Apple Watch، يحتفظ iOS بآخر نسخة أرسلها إليها iPhone، وفيها اسم مدينتك، وقد تكون تلك النسخة في النسخ الاحتياطي لجهاز iPhone. والنسخ الاحتياطي على iCloud مشفّر بمفاتيح تحتفظ بها Apple، إلا إن فعّلت حماية البيانات المتقدمة فيصبح مشفّرًا من طرف إلى طرف؛ أما النسخ الاحتياطي على الكمبيوتر فلا يُشفَّر إلا إن اخترت ذلك. | yes |
| 107 | PRIVACY.ar.md :86-91 | - No account, sign-in or profile. - No ads and no advertising identifier. - No analytics, crash reporting or usage statistics. - No third-party SDK that talks to the internet. The Android app's one Google Play services library links your phone to your own watch, and Google may relay that link, encrypted, as above. - No sale or sharing of data, because there is none to sell or share. | - لا حساب ولا تسجيل دخول ولا ملف شخصي. - لا إعلانات ولا معرّف إعلاني. - لا تحليلات ولا تقارير أعطال ولا إحصاءات استخدام. - لا مكتبات من طرف ثالث تتصل بالإنترنت. ومكتبة خدمات Google Play الوحيدة في تطبيق أندرويد تربط هاتفك بساعتك أنت، وقد تنقل Google هذا الاتصال، مشفّرًا، كما سبق. - لا بيع للبيانات ولا مشاركة لها، لأنه لا بيانات تُباع أو تُشارك. | yes |
| 108 | docs/store/listing/ar.md :23-30 | THE QURAN • The complete Uthmani text, in the same script as the Madinah Mushaf, and the printed Mushaf's page layout if you want it. • Translations in English, Arabic tafsir, French, Turkish, Indonesian, Urdu and Bengali, shown under each ayah. • Search across the Arabic text and the translations. • Bookmarks, copy and share, and a card that takes you back to where you stopped. • Recitation by ten reciters, downloaded once per surah and played offline, with lock-screen controls; when a surah ends the next one follows. • During recitation, your phone's own voice can read the translation or tafsir aloud after each ayah, where the phone has a voice for that language. • An ayah-of-the-day widget. | القرآن الكريم • النص العثماني كاملًا بخط مصحف المدينة، مع تخطيط الصفحات التقليدي حين تريد القراءة كما في المصحف المطبوع. • التفسير الميسّر وترجمات بالإنجليزية والفرنسية والتركية والإندونيسية والأردية والبنغالية تحت كل آية. • بحث في النص العربي وفي الترجمات. • علامات مرجعية، ونسخ ومشاركة، وبطاقة تعيدك إلى حيث توقفت. • تلاوة بأصوات عشرة قرّاء، تُنزَّل مرة واحدة لكل سورة وتُشغَّل دون إنترنت مع أزرار التحكم في شاشة القفل؛ وحين تنتهي السورة تتبعها التي بعدها. • أثناء التلاوة، يستطيع صوت هاتفك نفسه أن يقرأ الترجمة أو التفسير بعد كل آية، إن كان في الهاتف صوت لتلك اللغة. • أداة «آية اليوم» للشاشة الرئيسية. | no (English changed, this language not) |
| 109 | docs/store/listing/ar.md :42-42 | On your wrist: on Wear OS watches such as Galaxy Watch, Taqwa shows the next prayer and its countdown on its own watch face, in complications for any face and in a Tile, counts tasbeeh with the bezel or the crown, and points to the Qibla. The times come from your phone, so your watch and your phone always agree. | على معصمك: في ساعات Wear OS مثل Galaxy Watch، يعرض تقوى الصلاة التالية وعدّها التنازلي على واجهته الخاصة، وفي مضاعفات لأي واجهة، وفي مربّع، ويعدّ التسبيح بالإطار الدوّار أو التاج، ويدلّك على القبلة. تأتي المواقيت من هاتفك، فتتّفق ساعتك وهاتفك دائمًا. | no |
| 110 | docs/store/listing/ar.md :44-48 | MADE TO BE TRUSTED • No account, no adverts, no analytics and no tracking of any kind. • Your location is used on your phone to calculate times. Only your own devices can see your location. • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted. • Free and open source under the GPL, with every source and licence credited in the app. | صُنع ليُوثَق به • لا حساب، ولا إعلانات، ولا تحليلات، ولا تتبّع من أي نوع. • موقعك يُستخدم على هاتفك لحساب المواقيت. لا ترى موقعك إلا أجهزتك أنت. • يستخدم التطبيق الإنترنت للتلاوة: الصوت الذي تطلبه، وفحص يومي لقائمة القرّاء بعد أن تستخدمها. وإن أضفت تقوى إلى ساعتك، فقد تنقل Google بياناتها عبر الإنترنت، مشفّرةً. • مجاني ومفتوح المصدر برخصة GPL، وكل مصدر ورخصة مذكوران في التطبيق. | no |
| 111 | docs/store/listing/ar.md :52-52 | (Plan 5 Task 10's cut, which makes room for the wrist paragraph within Play's 4,000 characters, for Mohamed's approval: the website and support lines are left out, since both stores show them in their own fields, and so is the line of interface languages, which the App Store lists itself; and the Mushaf bullet says the same in fewer words.) | (Plan 5 Task 10's cut, which makes room for the wrist paragraph within Play's 4,000 characters, for Mohamed's approval: the website and support lines are left out, since both stores show them in their own fields, and so is the line of interface languages, which the App Store lists itself. The wrist paragraph, and the location bullet and the one after it, are a draft, to native review (plan 5 Task 11).) | yes |
| 112 | docs/store/listing/ar.md :86-87 | ### Description (4000) Taqwa gives you the essentials of the day in one quiet place: prayer times where you are, a takbir or the adhan at each prayer, the whole Quran to read and listen to, the morning and evening adhkar, and the Qibla. It works offline, costs nothing, shows no adverts and asks for no account. | ### Description (4000) يجمع تقوى أساسيات يومك في مكان واحد هادئ: مواقيت الصلاة حيث أنت، والتكبير أو الأذان عند كل صلاة، والقرآن كاملًا للقراءة والاستماع، وأذكار الصباح والمساء، والقبلة. يعمل دون إنترنت، ولا يكلّف شيئًا، ولا يعرض إعلانات، ولا يطلب حسابًا. | no |
| 113 | docs/store/listing/ar.md :98-105 | as row 108 | as row 108 | no (English changed, this language not) |
| 114 | docs/store/listing/ar.md :117-117 | On your wrist: on Apple Watch, Taqwa shows the next prayer and its countdown in your watch face's complications, counts tasbeeh with the Digital Crown, and points to the Qibla. The times come from your iPhone, so your watch and your iPhone always agree. | على معصمك: في Apple Watch، يعرض تقوى الصلاة التالية وعدّها التنازلي في مضاعفات واجهة ساعتك، ويعدّ التسبيح باستخدام Digital Crown، ويدلّك على القبلة. تأتي المواقيت من iPhone لديك، فتتّفق ساعتك وiPhone دائمًا. | no |
| 115 | docs/store/listing/ar.md :119-123 | MADE TO BE TRUSTED • No account, no adverts, no analytics and no tracking of any kind. • Your location is used on your phone to calculate times. Only your own devices can see your location. • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted. • Free and open source under the GPL, with every source and licence credited in the app. | صُنع ليُوثَق به • لا حساب، ولا إعلانات، ولا تحليلات، ولا تتبّع من أي نوع. • موقعك يُستخدم على هاتفك لحساب المواقيت. لا ترى موقعك إلا أجهزتك أنت. • يستخدم التطبيق الإنترنت للتلاوة: الصوت الذي تطلبه، وفحص يومي لقائمة القرّاء بعد أن تستخدمها. وإن أضفت تقوى إلى ساعتك، فقد تنقل Apple بياناتها عبر الإنترنت، مشفّرةً. • مجاني ومفتوح المصدر برخصة GPL، وكل مصدر ورخصة مذكوران في التطبيق. | no |
| 116 | docs/store/listing/ar.md :127-127 | (Its own text since 1.6.0, as each store names only its own watch (App Store Review Guideline 2.3.10): the Play full description above, with the Apple Watch paragraph in place of the Wear OS one, and Apple in place of Google in the bullet after the location one.) | (Its own text since 1.6.0, as each store names only its own watch (App Store Review Guideline 2.3.10): the Play full description above, with the Apple Watch paragraph in place of the Wear OS one, and Apple in place of Google in the bullet after the location one. A draft, to native review (plan 5 Task 11), as above.) | yes |
| 117 | site/pages/ar/home.html :69 | On your wrist | على معصمك | yes |
| 118 | site/pages/ar/home.html :70 | On Galaxy Watch and other Wear OS watches, the Taqwa face in four styles, complications for any face and a Tile; on Apple Watch, complications and the Smart Stack. On both, Today, tasbeeh counted with the bezel or the crown, and the Qibla, all from your phone’s own prayer times and in your phone’s language. | على Galaxy Watch وساعات Wear OS الأخرى: واجهة تقوى بأربعة أنماط، ومضاعفات لأي واجهة، ومربّع؛ وعلى Apple Watch: مضاعفات وSmart Stack. وعلى كلتيهما: شاشة اليوم، والتسبيح بالإطار أو التاج، والقبلة، وكلها من مواقيت هاتفك نفسها وبلغة هاتفك. | yes |
| 119 | site/pages/ar/home.html :105 | Offline by design. Online only for what you choose. | دون اتصال بالتصميم. ومتصل فقط لما تختاره. | yes |
| 120 | site/pages/ar/home.html :106 | There is no server behind Taqwa and no account to create. The internet is used for recitation: fetching a surah when you download it or play one not yet downloaded, and, once you have used recitation, checking once a day for an updated list of reciters. Until then, the app itself makes no network request. If you add Taqwa to your watch, Google or Apple may carry its data between your phone and watch over the internet, encrypted so only your phone and watch can read it. | لا خادم وراء تقوى ولا حساب تنشئه. يستخدم التطبيق الإنترنت للتلاوة: جلب السورة حين تنزّلها أو تشغّل سورة لم تنزّلها بعد، والتحقق مرة في اليوم من وجود قائمة قرّاء محدّثة بعد أن تستخدم التلاوة. وحتى تستخدم التلاوة للمرة الأولى، لا يرسل التطبيق نفسه أي طلب عبر الشبكة. وإن أضفت تقوى إلى ساعتك، فقد تنقل Google أو Apple بياناتها بين هاتفك وساعتك عبر الإنترنت، مشفّرةً فلا يقرؤها إلا هاتفك وساعتك. | yes |
| 121 | site/pages/ar/home.html :110 | Stays on your devices Only your own devices can see your location, settings, bookmarks and counts. | يبقى على أجهزتك لا ترى موقعك وإعداداتك وعلاماتك وعدّادك إلا أجهزتك أنت. | yes |
| 122 | site/pages/ar/home.html :111 | Online only for what you choose The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google or Apple may carry its data over the internet, encrypted so only your phone and watch can read it. | متصل فقط لما تختاره يُستخدم الإنترنت للتلاوات التي تختار تنزيلها، من مستودع تقوى العام على GitHub. وإن أضفت تقوى إلى ساعتك، فقد تنقل Google أو Apple بياناتها عبر الإنترنت، مشفّرةً فلا يقرؤها إلا هاتفك وساعتك. | yes |
| 123 | site/pages/ar/support.html :33 | Taqwa on your watch | تقوى على ساعتك | yes |
| 124 | site/pages/ar/support.html :34 | On a Galaxy Watch or another Wear OS watch, install Taqwa from Settings › Watch in Taqwa on the phone while the watch is nearby, or from the Play Store on the watch. On an Apple Watch, the watch app comes with the iPhone app; if it is not on the watch, install it from the Watch app on the iPhone. | على Galaxy Watch أو أي ساعة Wear OS أخرى، ثبّت تقوى من الإعدادات › الساعة في تقوى على الهاتف والساعة قريبة منه، أو من متجر Play على الساعة. وعلى Apple Watch، يأتي تطبيق الساعة مع تطبيق iPhone؛ وإن لم يكن على الساعة فثبّته من تطبيق Watch على iPhone. | yes |
| 125 | site/pages/ar/support.html :35 | The Taqwa face needs Wear OS 6: choose Add the Taqwa face on the watch, or touch and hold your watch face and pick Taqwa, then touch and hold it again to choose its style, colour and slots. On any face, and on an Apple Watch, Taqwa’s complications show the next prayer and its countdown. On Wear OS 3 a complication’s countdown moves only at each prayer; Today, in the watch app, always counts down exactly. | تحتاج واجهة تقوى إلى Wear OS 6: اختر «إضافة واجهة تقوى» على الساعة، أو المس واجهة ساعتك مطوّلًا واختر تقوى، ثم المسها مطوّلًا مرة أخرى لتختار نمطها ولونها وخاناتها. وعلى أي واجهة، وعلى Apple Watch، تعرض مضاعفات تقوى الصلاة التالية وعدّها التنازلي. وعلى Wear OS 3 لا يتقدّم العدّ التنازلي في المضاعفة إلا عند كل صلاة؛ أما شاشة «اليوم» في تطبيق الساعة فتعدّ دائمًا بدقة. | yes |
| 126 | site/pages/ar/support.html :36 | Your phone sends the watch its prayer times a month ahead, so the watch keeps working for weeks away from it. If the watch says “Open Taqwa on your phone”, it has no times yet, or they have run out: open Taqwa on the phone with the watch nearby. The watch speaks the language you chose in Taqwa on the phone, with its digits and its clock; the watch’s own menus follow the watch’s language. | يرسل هاتفك إلى الساعة مواقيت الصلاة لشهر مقبل، فتبقى الساعة تعمل أسابيع بعيدًا عنه. وإن قالت الساعة «افتح تقوى على هاتفك» فليست لديها مواقيت بعد، أو نفدت: افتح تقوى على الهاتف والساعة قريبة منه. وتتحدّث الساعة باللغة التي اخترتها في تقوى على الهاتف، بأرقامها ونظام وقتها؛ أما قوائم الساعة نفسها فتتبع لغة الساعة. | yes |
| 127 | site/pages/ar/support.html :37 | The Qibla screen uses the watch’s own compass. Hold your wrist level, away from magnets, and if it asks to be calibrated, move it in a figure of eight a few times. | تستخدم شاشة «القبلة» بوصلة الساعة نفسها. أبقِ معصمك مستويًا بعيدًا عن المغناطيس، وإن طلبت المعايرة فحرّكه على شكل الرقم 8 بضع مرات. | yes |
| 128 | site/pages/ar/support.html :43 | Everything Taqwa knows lives on your own devices: your location or chosen city, your settings, bookmarks, adhkar and tasbeeh counts, and downloaded recitations. There is no account and no server, so there is nothing to request or delete on our side. Uninstalling Taqwa from your phone and your watch removes all of it. The privacy policy says this in full. | كل ما يعرفه تقوى يبقى على أجهزتك أنت: موقعك أو المدينة التي اخترتها، وإعداداتك، وعلاماتك، وعدّاد الأذكار والتسبيح، والتلاوات المنزّلة. لا حساب ولا خادم، فليس لدينا ما يُطلب أو يُحذف. وإزالة تقوى من هاتفك وساعتك تزيل كل ذلك. و سياسة الخصوصية تبيّن هذا كاملًا. | yes |
| 129 | site/pages/ar/meta.json :28 | "description": "What Taqwa stores on your devices, what it uses the internet for, and what it never does." | "description": "ما يخزّنه تقوى على أجهزتك، وما يستخدم الإنترنت لأجله، وما لا يفعله أبدًا." | no |
| 130 | docs/store/release-notes/1.6.0-100042-wear.txt :4 <ar> | The Taqwa face in four styles, complications for any face, a Tile, and Today, Tasbeeh and Qibla. Needs Taqwa on your phone, which sends the prayer times. | واجهة تقوى بأربعة أنماط، ومضاعفات لأي واجهة، ومربّع، وشاشات اليوم والتسبيح والقبلة. يحتاج إلى تقوى على هاتفك، فهو الذي يرسل مواقيت الصلاة. | listed here |
| 131 | docs/store/release-notes/1.6.0-42-appstore.txt :8 (Arabic) | What's new in 1.6.0: / • Taqwa on your Apple Watch: the next prayer in your complications, tasbeeh with the Digital Crown, and Qibla | الجديد في الإصدار 1.6.0: / • تقوى على Apple Watch: الصلاة التالية في مضاعفات ساعتك، والتسبيح باستخدام Digital Crown، والقبلة | yes |
| 132 | docs/store/release-notes/1.6.0-42.txt :5 <ar> | What's new in 1.6.0: / • Taqwa on your watch: a watch face, complications and a Tile, tasbeeh and Qibla, on Wear OS watches such as Galaxy Watch | الجديد في الإصدار 1.6.0: / • تقوى على ساعتك: واجهة ساعة، ومضاعفات، ومربّع، والتسبيح والقبلة، على ساعات Wear OS مثل Galaxy Watch | listed here |

### French: 141 rows

| # | Where | English | Draft | Marker |
|---|---|---|---|---|
| 1 | compose/values-fr/strings.xml onboarding_location_body (:21) | Prayer times depend on where you are. Everything is calculated on your device. Only your own devices can see your location. | Les horaires dépendent de votre position. Tout est calculé sur votre appareil. Seuls vos propres appareils peuvent voir votre position. | yes |
| 2 | compose/values-fr/strings.xml settings_add_to_apple_watch (:129) | Add to Apple Watch | Ajouter à l’Apple Watch | yes |
| 3 | compose/values-fr/strings.xml settings_add_to_apple_watch_failed (:131) | The face could not be added. Try again from the Watch app. | Impossible d’ajouter le cadran. Réessayez depuis l’app Apple Watch. | yes |
| 4 | compose/values-fr/strings.xml settings_group_watch (:138) | WATCH | MONTRE | yes |
| 5 | compose/values-fr/strings.xml settings_watch_install (:140) | Install on your watch | Installer sur votre montre | yes |
| 6 | compose/values-fr/strings.xml settings_watch_face (:142) | Add the Taqwa face | Ajouter le cadran Taqwa | yes |
| 7 | compose/values-fr/strings.xml settings_watch_continue (:144) | Continue on your watch | Continuez sur votre montre | yes |
| 8 | compose/values-fr/strings.xml settings_watch_unreachable (:146) | Couldn’t reach your watch | Impossible de joindre votre montre | yes |
| 9 | compose/values-fr/strings.xml about_stays_title (:162) | Stays on your devices | Tout reste sur vos appareils | yes |
| 10 | compose/values-fr/strings.xml about_stays_body (:164) | Only your own devices can see your location, settings, bookmarks and counts. | Seuls vos propres appareils peuvent voir votre position, vos paramètres, vos signets et vos compteurs. | yes |
| 11 | compose/values-fr/strings.xml about_online_title (:166) | Online only for what you choose | En ligne seulement pour ce que vous choisissez | yes |
| 12 | compose/values-fr/strings.xml about_online_body_android (:168) | The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted so only your phone and watch can read it. | Internet sert aux récitations que vous choisissez de télécharger, depuis le dépôt public de Taqwa sur GitHub. Si vous ajoutez Taqwa à votre montre, Google peut acheminer ses données par Internet, chiffrées pour que seuls votre téléphone et votre montre puissent les lire. | yes |
| 13 | compose/values-fr/strings.xml about_online_body_ios (:170) | The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted so only your phone and watch can read it. | Internet sert aux récitations que vous choisissez de télécharger, depuis le dépôt public de Taqwa sur GitHub. Si vous ajoutez Taqwa à votre montre, Apple peut acheminer ses données par Internet, chiffrées pour que seuls votre téléphone et votre montre puissent les lire. | yes |
| 14 | compose/values-fr/strings.xml location_privacy_note (:266) | Coordinates are stored on your device and used only to calculate times. Only your own devices can see your location. | Les coordonnées sont enregistrées sur votre appareil et servent uniquement à calculer les horaires. Seuls vos propres appareils peuvent voir votre position. | yes |
| 15 | compose/values-fr/strings.xml prayer_qibla_detail (:631) | %1$s° · %2$s km to Makkah | %1$s° · %2$s km de La Mecque | yes |
| 16 | wearApp/res/values-fr/strings.xml app_name (:4) | Taqwa | Taqwa | no |
| 17 | wearApp/res/values-fr/strings.xml complication_next_prayer (:5) | Next prayer | Prochaine prière | no |
| 18 | wearApp/res/values-fr/strings.xml complication_mark (:6) | Taqwa | Taqwa | no |
| 19 | wearApp/res/values-fr/strings.xml complication_mark_description (:7) | Taqwa | Taqwa | no |
| 20 | wearApp/res/values-fr/strings.xml tile_label (:8) | Prayer times | Horaires de prière | no |
| 21 | wearApp/res/values-fr/strings.xml open_taqwa_on_phone (:10) | Open Taqwa on your phone | Ouvrez Taqwa sur votre téléphone | yes |
| 22 | wearApp/res/values-fr/strings.xml update_taqwa_on_watch (:11) | Update Taqwa on your watch | Mettez à jour Taqwa sur votre montre | yes |
| 23 | wearApp/res/values-fr/strings.xml complication_day (:12) | Prayer day | Prières du jour | yes |
| 24 | wearApp/res/values-fr/strings.xml complication_day_dial (:13) | Taqwa face: day dial | Cadran Taqwa : cadran du jour | yes |
| 25 | wearApp/res/values-fr/strings.xml complication_dates (:14) | Dates | Dates | yes |
| 26 | wearApp/res/values-fr/strings.xml complication_day_title (:15) | Day | Jour | yes |
| 27 | wearApp/res/values-fr/strings.xml complication_day_description (:16) | Prayer times today | Horaires de prière du jour | yes |
| 28 | wearApp/res/values-fr/strings.xml preview_prayer_label (:17) | ASR | ASR | yes |
| 29 | wearApp/res/values-fr/strings.xml preview_prayer_name (:18) | Asr | Asr | yes |
| 30 | wearApp/res/values-fr/strings.xml preview_prayer_time (:19) | 16:10 | 16:10 | yes |
| 31 | wearApp/res/values-fr/strings.xml preview_countdown (:20) | in 1 h 12 min | dans 1 h 12 min | yes |
| 32 | wearApp/res/values-fr/strings.xml preview_day_times (:21) | Fajr 4:33, Sunrise 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24 | Fajr 4:33, Lever du soleil 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24 | yes |
| 33 | wearApp/res/values-fr/strings.xml preview_hijri (:22) | 6 Jumada al-Ula 1448 | 6 Joumada al-Oula 1448 | yes |
| 34 | wearApp/res/values-fr/strings.xml preview_hijri_day_month (:23) | 6 Jumada al-Ula | 6 Joumada al-Oula | yes |
| 35 | wearApp/res/values-fr/strings.xml preview_gregorian (:24) | SUN 18 OCT | DIM 18 OCT | yes |
| 36 | wearApp/res/values-fr/strings.xml preview_weekday (:25) | SUN | DIM | yes |
| 37 | wearApp/res/values-fr/strings.xml preview_day_number (:26) | 18 | 18 | yes |
| 38 | wearApp/res/values-fr/strings.xml tile_description (:27) | Today’s prayers and the next one counted down | Les prières du jour et le compte à rebours de la prochaine | yes |
| 39 | wearApp/res/values-fr/strings.xml face_add (:30) | Add the Taqwa face | Ajouter le cadran Taqwa | yes |
| 40 | wearApp/res/values-fr/strings.xml face_about (:32) | The next prayer and its countdown on your watch face. | La prochaine prière et son compte à rebours sur votre cadran. | yes |
| 41 | wearApp/res/values-fr/strings.xml face_active (:34) | Taqwa is your watch face. | Taqwa est votre cadran. | yes |
| 42 | wearApp/res/values-fr/strings.xml face_choose_it (:36) | Touch and hold your watch face, then choose Taqwa. | Appuyez longuement sur votre cadran, puis choisissez Taqwa. | yes |
| 43 | wearApp/res/values-fr/strings.xml face_failed (:38) | Couldn’t add the face. Try again later. | Impossible d’ajouter le cadran. Réessayez plus tard. | yes |
| 44 | watchface/res/values-fr/strings.xml watch_face_name (:4) | Taqwa | Taqwa | no |
| 45 | watchface/res/values-fr/strings.xml slot_next_prayer (:5) | Next prayer | Prochaine prière | no |
| 46 | watchface/res/values-fr/strings.xml style (:7) | Style | Style | yes |
| 47 | watchface/res/values-fr/strings.xml style_arch (:8) | Arch | Arche | yes |
| 48 | watchface/res/values-fr/strings.xml style_ring (:9) | Ring | Anneau | yes |
| 49 | watchface/res/values-fr/strings.xml style_sun (:10) | Sun | Soleil | yes |
| 50 | watchface/res/values-fr/strings.xml style_moon (:11) | Moon | Lune | yes |
| 51 | watchface/res/values-fr/strings.xml accent (:12) | Colour | Couleur | yes |
| 52 | watchface/res/values-fr/strings.xml accent_amber (:13) | Amber | Ambre | yes |
| 53 | watchface/res/values-fr/strings.xml accent_pearl (:14) | Pearl | Perle | yes |
| 54 | watchface/res/values-fr/strings.xml accent_sage (:15) | Sage | Sauge | yes |
| 55 | watchface/res/values-fr/strings.xml accent_night (:16) | Night | Nuit | yes |
| 56 | watchface/res/values-fr/strings.xml slot_day (:17) | Prayer day | Prières du jour | yes |
| 57 | watchface/res/values-fr/strings.xml slot_dates (:18) | Dates | Dates | yes |
| 58 | watchface/res/values-fr/strings.xml slot_dates_short (:19) | Date and moon | Date et lune | yes |
| 59 | watchface/res/values-fr/strings.xml slot_countdown (:20) | Time to the next prayer | Temps avant la prochaine prière | yes |
| 60 | watchface/res/values-fr/strings.xml slot_day_dial (:21) | Prayer day dial | Cadran de la journée de prière | yes |
| 61 | watchface/res/values-fr/strings.xml slot_left (:22) | Left complication | Complication de gauche | yes |
| 62 | watchface/res/values-fr/strings.xml slot_right (:23) | Right complication | Complication de droite | yes |
| 63 | iosApp/TaqwaWatch/fr.lproj/Localizable.strings open_taqwa_on_phone (:2) | as row 21 | as row 21 | yes |
| 64 | iosApp/TaqwaWatch/fr.lproj/Localizable.strings update_taqwa_on_watch (:5) | as row 22 | as row 22 | yes |
| 65 | iosApp/TaqwaWatch/fr.lproj/InfoPlist.strings CFBundleDisplayName (:2) | Taqwa | Taqwa | no |
| 66 | iosApp/TaqwaWatchWidgets/fr.lproj/Localizable.strings next_prayer_widget_name (:2) | Next prayer | Prochaine prière | no |
| 67 | iosApp/TaqwaWatchWidgets/fr.lproj/Localizable.strings next_prayer_widget_description (:5) | The next prayer, its time and the countdown. | La prochaine prière, son heure et le compte à rebours. | yes |
| 68 | iosApp/TaqwaWatchWidgets/fr.lproj/Localizable.strings open_taqwa_on_phone (:8) | as row 21 | as row 21 | yes |
| 69 | iosApp/TaqwaWatchWidgets/fr.lproj/Localizable.strings update_taqwa_on_watch (:11) | as row 22 | as row 22 | yes |
| 70 | iosApp/TaqwaWatchWidgets/fr.lproj/InfoPlist.strings CFBundleDisplayName (:2) | Taqwa | Taqwa | no |
| 71 | iosApp/iosApp/fr.lproj/InfoPlist.strings NSLocationWhenInUseUsageDescription (:4) | Taqwa uses your location to calculate prayer times on your device. Only your own devices can see your location. | Taqwa utilise votre position pour calculer les horaires de prière sur votre appareil. Seuls vos propres appareils peuvent voir votre position. | yes |
| 72 | WatchWords.kt COUNTDOWN.hoursMinutes (:77) | in %1$s h %2$s min | dans %1$s h %2$s min | yes |
| 73 | WatchWords.kt COUNTDOWN.hours (:78) | in %1$s h | dans %1$s h | yes |
| 74 | WatchWords.kt COUNTDOWN.minutes (:79) | in %1$s min | dans %1$s min | yes |
| 75 | WatchWords.kt COUNTDOWN.underAMinute (:80) | in under a minute | d’ici une minute | yes |
| 76 | WatchWords.kt FACE.add (:130) | Add the Taqwa face | Ajouter le cadran Taqwa | yes |
| 77 | WatchWords.kt FACE.about (:131) | as row 40 | as row 40 | yes |
| 78 | WatchWords.kt FACE.active (:132) | Taqwa is your watch face. | Taqwa est votre cadran. | yes |
| 79 | WatchWords.kt FACE.chooseIt (:133) | as row 42 | as row 42 | yes |
| 80 | WatchWords.kt FACE.failed (:134) | as row 43 | as row 43 | yes |
| 81 | WatchWords.kt SCREENS.openOnPhone (:220) | as row 21 | as row 21 | yes |
| 82 | WatchWords.kt SCREENS.updateOnWatch (:221) | as row 22 | as row 22 | yes |
| 83 | WatchWords.kt SCREENS.bezelHint (:222) | Turn the bezel or tap | Tournez la lunette ou touchez | yes |
| 84 | WatchWords.kt SCREENS.crownHint (:223) | Turn the crown or tap | Tournez la Digital Crown ou touchez | yes |
| 85 | WatchWords.kt SCREENS.undoAction (:224) | Take one away | Retirer un | yes |
| 86 | WatchWords.kt SCREENS.phrasesAction (:225) | Choose a dhikr | Choisir un dhikr | yes |
| 87 | WatchWords.kt SCREENS.holdLevel (:226) | Hold your wrist level | Gardez le poignet à plat | yes |
| 88 | WatchWords.kt SCREENS.distanceOnDial (:227) | %1$s km | %1$s km | yes |
| 89 | WatchWords.kt SCREENS.qiblaDetailOnDial (:228) | %1$s° · %2$s km | %1$s° · %2$s km | yes |
| 90 | WatchWords.kt SCREENS.countSpoken (:229) | %1$s of %2$s | %1$s sur %2$s | yes |
| 91 | WatchWords.kt SCREENS.nextSpoken (:230) | %1$s. Next, %2$s | %1$s. Ensuite, %2$s | yes |
| 92 | WatchWords.kt SCREENS.setCompleteSpoken (:231) | %1$s. Round complete | %1$s. Série terminée | yes |
| 93 | WatchWords.kt SCREENS.roundSpoken (:232) | Round complete. %1$s, round %2$s | Série terminée. %1$s, série %2$s | yes |
| 94 | WatchWords.kt SCREENS.nameRoundSpoken (:233) | %1$s, round %2$s | %1$s, série %2$s | yes |
| 95 | WatchWords.kt SCREENS.countAction (:234) | Count | Compter | yes |
| 96 | WatchWords.kt SCREENS.turnRight (:235) | up to 180°: Turn right about %1$s degrees | up to 180°: Tournez à droite d’environ %1$s degrés | yes |
| 97 | WatchWords.kt SCREENS.turnLeft (:236) | up to 180°: Turn left about %1$s degrees | up to 180°: Tournez à gauche d’environ %1$s degrés | yes |
| 98 | PRIVACY.fr.md :3-3 | _Last updated 30 October 2026. Applies to Taqwa 1.0.0 and later on Android and iOS._ | _Dernière mise à jour le 30 octobre 2026. S’applique à Taqwa 1.0.0 et aux versions ultérieures sur Android et iOS._ | no |
| 99 | PRIVACY.fr.md :5-9 | Taqwa is a free Islamic app with no account, no ads and no analytics. It works offline. It uses the internet to download Quran recitations, and only when you ask it to. If you add Taqwa to your watch, its data may travel over the internet too, encrypted, as below. | Taqwa est une application islamique gratuite, sans compte, sans publicité et sans outil d’analyse. Elle fonctionne hors ligne. Elle utilise Internet pour télécharger les récitations du Coran, et uniquement lorsque vous le lui demandez. Si vous ajoutez Taqwa à votre montre, ses données peuvent aussi transiter par Internet, chiffrées, comme expliqué plus bas. | yes |
| 100 | PRIVACY.fr.md :11-11 | ## What stays on your devices | ## Ce qui reste sur vos appareils | yes |
| 101 | PRIVACY.fr.md :13-14 | Everything the app knows is stored on your own devices: | Tout ce que l’application sait est enregistré sur vos propres appareils : | yes |
| 102 | PRIVACY.fr.md :22-25 | None of this is sent to us or to anyone else. There is no server behind Taqwa and no account to create. Deleting Taqwa from your phone and your watch deletes all of it. | Rien de tout cela ne nous est envoyé, ni à qui que ce soit d’autre. Il n’y a aucun serveur derrière Taqwa et aucun compte à créer. Supprimer Taqwa de votre téléphone et de votre montre supprime tout cela. | yes |
| 103 | PRIVACY.fr.md :31-31 | ## Your watch | ## Votre montre | yes |
| 104 | PRIVACY.fr.md :33-40 | If you add Taqwa to your watch, your phone gives it your city, the Qibla's direction and distance, your area's compass correction, the coming month's prayer times as the app writes them, and your own tasbeeh phrases, with a random number made once on your phone that tells your watch which phone sent them. When your phone and watch are not directly connected, Google (Android) or Apple (iPhone) may carry this data over the internet, end-to-end encrypted so only your phone and watch can read it. | Si vous ajoutez Taqwa à votre montre, votre téléphone lui donne votre ville, la direction et la distance de la qibla, la correction de boussole de votre région, les horaires de prière du mois à venir tels que l’application les écrit, et vos propres formules de tasbih, avec un nombre aléatoire créé une seule fois sur votre téléphone, qui indique à votre montre quel téléphone les a envoyés. Quand votre téléphone et votre montre ne sont pas connectés directement, Google (Android) ou Apple (iPhone) peut acheminer ces données par Internet, chiffrées de bout en bout pour que seuls votre téléphone et votre montre puissent les lire. | yes |
| 105 | PRIVACY.fr.md :44-48 | The app asks for your location only to compute prayer times and the Qibla direction, and only if you allow it. Picking a city from the built-in list works just as well and needs no permission. Your location is used on the device. Only your own devices can see your location. | L’application ne demande votre position que pour calculer les horaires de prière et la direction de la qibla, et seulement si vous l’y autorisez. Choisir une ville dans la liste intégrée fonctionne tout aussi bien et ne nécessite aucune autorisation. Votre position est utilisée sur l’appareil. Seuls vos propres appareils peuvent voir votre position. | yes |
| 106 | PRIVACY.fr.md :64-69 | Once you have opened anything to do with recitation, whether the reciter list, the Recitation settings or a download, the app also checks once a day for an updated list of reciters, from the same repository. That request is the same for everyone and reveals nothing but your IP address. Until you first use recitation, the app itself makes no network request at all. | Une fois que vous avez ouvert quoi que ce soit qui touche à la récitation, que ce soit la liste des récitateurs, les paramètres de Récitation ou un téléchargement, l’application vérifie aussi une fois par jour s’il existe une liste de récitateurs plus récente, depuis le même dépôt. Cette requête est la même pour tout le monde et ne révèle rien d’autre que votre adresse IP. Tant que vous n’avez pas utilisé la récitation une première fois, l’application elle-même ne fait aucune requête réseau. | yes |
| 107 | PRIVACY.fr.md :87-97 | Taqwa opts out of Android's app backup, so your location and settings are not copied to Google. On iOS, your settings, bookmarks, counts and downloaded recitations are excluded from iCloud backup; the only files of Taqwa's own that are backed up are the widgets' small caches, the upcoming prayer times and the hundred ayahs the daily-ayah widget rotates through, neither of which contains your location. If you add Taqwa to an Apple Watch, iOS keeps the last copy your iPhone sent it, which names your city, and that copy may be in your iPhone's backup. An iCloud backup is encrypted with keys Apple keeps, unless you turn on Advanced Data Protection, which encrypts it end to end; a backup to a computer is encrypted only if you choose to encrypt it. | Taqwa n’utilise pas la sauvegarde d’applications d’Android : votre position et vos paramètres ne sont donc pas copiés chez Google. Sur iOS, vos paramètres, vos signets, vos compteurs et vos récitations téléchargées sont exclus de la sauvegarde iCloud ; les seuls fichiers propres à Taqwa qui soient sauvegardés sont les petits caches des widgets, les prochains horaires de prière et les cent versets que le widget du verset du jour fait défiler, dont aucun ne contient votre position. Si vous ajoutez Taqwa à une Apple Watch, iOS conserve la dernière copie que votre iPhone lui a envoyée, qui indique votre ville, et cette copie peut figurer dans la sauvegarde de votre iPhone. Une sauvegarde iCloud est chiffrée avec des clés que conserve Apple, sauf si vous activez la protection avancée des données, qui la chiffre de bout en bout ; une sauvegarde sur un ordinateur n’est chiffrée que si vous choisissez de la chiffrer. | yes |
| 108 | PRIVACY.fr.md :101-107 | - No account, sign-in or profile. - No ads and no advertising identifier. - No analytics, crash reporting or usage statistics. - No third-party SDK that talks to the internet. The Android app's one Google Play services library links your phone to your own watch, and Google may relay that link, encrypted, as above. - No sale or sharing of data, because there is none to sell or share. | - Aucun compte, aucune connexion, aucun profil. - Aucune publicité et aucun identifiant publicitaire. - Aucun outil d’analyse, aucun service de rapports de plantage, aucune statistique d’utilisation. - Aucun SDK tiers qui communique avec Internet. La seule bibliothèque des services Google Play de l’application Android relie votre téléphone à votre propre montre, et Google peut relayer ce lien, chiffré, comme indiqué plus haut. - Aucune vente ni aucun partage de données, puisqu’il n’y en a aucune à vendre ou à partager. | yes |
| 109 | docs/store/listing/fr.md :14-15 | ### Full description (4000) Taqwa gives you the essentials of the day in one quiet place: prayer times where you are, a takbir or the adhan at each prayer, the whole Quran to read and listen to, the morning and evening adhkar, and the Qibla. It works offline, costs nothing, shows no adverts and asks for no account. | ### Full description (4000) Taqwa réunit l’essentiel de la journée en un seul endroit paisible : des horaires de prière là où vous êtes, un takbir ou l’adhan à chaque prière, le Coran entier à lire et à écouter, les adhkar du matin et du soir, et la qibla. Gratuite, sans publicité ni compte, elle fonctionne hors ligne. | no |
| 110 | docs/store/listing/fr.md :17-22 | PRAYER TIMES • Times for where you are, from your phone's location or a city you choose from more than 34,000. • Times from the timetable the mosques near you follow. Where Taqwa holds it, it reproduces it and checks it day by day against the published tables: on every day checked, no prayer starts before the published time. Where a timetable is not yet fully checked, Taqwa adds a safety margin; where mosques follow different timetables, it shows cautious times, each prayer once all have begun it; and where no timetable is known, it calculates on the safe side. • Tap the (i) on the Prayer screen to see whose times these are, and About these times for how they are made and checked. Match my mosque looks for your mosque's timetable from two times on its board, and the usual calculation methods (Muslim World League, Egyptian, Karachi, ISNA and more) and a per-prayer adjustment are there too. • Rules for high latitudes, where the night is too short for the usual angles. • A notification for each prayer: a clear tone, a takbir, or the adhan in a choice of voices, among them Makkah and Madinah, or a recording from your phone, with an optional reminder before it, an Iqama reminder a chosen number of minutes after the adhan, and a Duha reminder at its best time. • A Jumu'ah pill on Friday's Dhuhr, home-screen widgets with today's times and the countdown to the next prayer, and the Hijri date alongside the Gregorian one. • A 24-hour or 12-hour clock, as you prefer. | HORAIRES DE PRIÈRE • Les horaires là où vous êtes, par la localisation du téléphone ou une ville parmi plus de 34 000. • Les horaires du calendrier que suivent les mosquées proches de chez vous. Là où Taqwa le détient, elle le reproduit et le vérifie jour après jour d’après les tableaux publiés : pour chaque jour vérifié, aucune prière ne commence avant l’heure publiée. Là où un calendrier n’est pas encore entièrement vérifié, Taqwa ajoute une marge de sécurité ; là où les mosquées suivent des calendriers différents, elle affiche des horaires par précaution, chaque prière une fois que tous l’ont commencée ; et là où aucun calendrier n’est connu, elle calcule avec prudence. • Le (i) de l’écran Prière indique de qui viennent ces horaires et comment ils sont vérifiés. « Aligner sur ma mosquée » cherche le calendrier de votre mosquée d’après deux horaires de son tableau ; les méthodes de calcul habituelles (Ligue islamique mondiale, égyptienne, Karachi, ISNA…), un ajustement par prière et des règles pour les hautes latitudes sont aussi proposés. • Une notification par prière (tonalité claire, takbir ou adhan, voix au choix dont La Mecque et Médine, ou votre enregistrement) et des rappels facultatifs avant, à l’iqama (délai au choix) et à la Douha. • Une pastille Joumou’a sur le Dhuhr du vendredi, des widgets d’écran d’accueil avec les horaires du jour et le compte à rebours, la date hégirienne à côté de la grégorienne, et le format 24 ou 12 heures. | no |
| 111 | docs/store/listing/fr.md :24-31 | THE QURAN • The complete Uthmani text, in the same script as the Madinah Mushaf, and the printed Mushaf's page layout if you want it. • Translations in English, Arabic tafsir, French, Turkish, Indonesian, Urdu and Bengali, shown under each ayah. • Search across the Arabic text and the translations. • Bookmarks, copy and share, and a card that takes you back to where you stopped. • Recitation by ten reciters, downloaded once per surah and played offline, with lock-screen controls; when a surah ends the next one follows. • During recitation, your phone's own voice can read the translation or tafsir aloud after each ayah, where the phone has a voice for that language. • An ayah-of-the-day widget. | LE CORAN • Le texte othmanien complet, dans la graphie du Mushaf de Médine, et, au choix, la mise en page du Mushaf imprimé. • Des traductions en anglais, français, turc, indonésien, ourdou et bengali, et un tafsir en arabe, sous chaque verset. • La recherche dans le texte arabe et les traductions. • Signets, copie et partage, et une carte qui vous ramène là où vous vous êtes arrêté. • Dix récitateurs, chaque sourate téléchargée une fois et lue hors ligne, avec les commandes de l’écran verrouillé ; les sourates s’enchaînent. • Pendant la récitation, la voix du téléphone peut lire la traduction ou le tafsir après chaque verset, s’il a une voix pour cette langue. • Un widget du verset du jour. | no |
| 112 | docs/store/listing/fr.md :36-38 | ADHKAR • The morning and evening adhkar in Arabic, with a translation in your language, each counted on screen. • The Quranic ones recited by your chosen reciter, and a reminder for each set if you want one. | ADHKAR • Les adhkar du matin et du soir en arabe, traduits dans votre langue et comptés à l’écran. • Ceux du Coran récités par votre récitateur, et un rappel si vous voulez. | no |
| 113 | docs/store/listing/fr.md :43-43 | On your wrist: on Wear OS watches such as Galaxy Watch, Taqwa shows the next prayer and its countdown on its own watch face, in complications for any face and in a Tile, counts tasbeeh with the bezel or the crown, and points to the Qibla. The times come from your phone, so your watch and your phone always agree. | Au poignet : sur les montres Wear OS comme la Galaxy Watch, la prochaine prière et son compte à rebours sur le cadran de Taqwa, en complications sur tout cadran et dans une carte, le tasbih compté à la lunette ou à la couronne, et la qibla. Les horaires viennent de votre téléphone : montre et téléphone sont toujours d’accord. | no |
| 114 | docs/store/listing/fr.md :45-49 | MADE TO BE TRUSTED • No account, no adverts, no analytics and no tracking of any kind. • Your location is used on your phone to calculate times. Only your own devices can see your location. • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted. • Free and open source under the GPL, with every source and licence credited in the app. | CONÇUE POUR ÊTRE DIGNE DE CONFIANCE • Sans mesure d’audience ni suivi d’aucune sorte. • Votre position sert, sur votre téléphone, à calculer les horaires. Seuls vos propres appareils peuvent voir votre position. • Internet sert à la récitation : l’audio demandé et, dès lors, une vérification quotidienne de la liste des récitateurs. Si vous ajoutez Taqwa à votre montre, Google peut acheminer ses données par Internet, chiffrées. • Gratuite et libre (GPL), avec chaque source et licence créditée dans l’application. | no |
| 115 | docs/store/listing/fr.md :51-51 | Prayer times from Taqwa's own engine; the Qibla bearing uses the Adhan library. Quran text from the Tanzil Project. Adhkar texts from OpenDua. Recitations from the Islamic Network, licensed by the reciters for free distribution. Taqwa is not affiliated with the religious authorities whose timetables it follows. | Horaires du moteur propre à Taqwa ; qibla par la bibliothèque Adhan ; Coran du Projet Tanzil ; adhkar d’OpenDua ; récitations de l’Islamic Network, concédées par les récitateurs pour une diffusion gratuite. Taqwa n’est pas affiliée aux autorités religieuses dont elle suit les calendriers. | no |
| 116 | docs/store/listing/fr.md :53-53 | (Plan 5 Task 10's cut, which makes room for the wrist paragraph within Play's 4,000 characters, for Mohamed's approval: the website and support lines are left out, since both stores show them in their own fields, and so is the line of interface languages, which the App Store lists itself; and the Mushaf bullet says the same in fewer words.) | (Plan 5 Task 10's cut, which makes room for the wrist paragraph within Play's 4,000 characters, for Mohamed's approval: it replaces P5.9's: the line of interface languages is left out too, which the App Store lists itself, beside P5.9's own cuts (the opening's last sentence, the Mushaf and recitation bullets in fewer words, and the website and support lines left out); and, as French needs more room: the high-latitude rules join the methods bullet, the 24- or 12-hour clock the Jumu'ah bullet, the (i) bullet says in fewer words that it shows whose times these are and how they are checked, the bookmarks, recitation and adhkar bullets and the sources line are shorter, the trust section's first bullet keeps only what the opening does not already say (no account, no adverts), and the wrist paragraph is written in French's shorter listing style. The wrist paragraph, the reworded lines, and the location bullet and the one after it are a draft, to native review (plan 5 Task 11).) | yes |
| 117 | docs/store/listing/fr.md :87-88 | ### Description (4000) Taqwa gives you the essentials of the day in one quiet place: prayer times where you are, a takbir or the adhan at each prayer, the whole Quran to read and listen to, the morning and evening adhkar, and the Qibla. It works offline, costs nothing, shows no adverts and asks for no account. | ### Description (4000) Taqwa réunit l’essentiel de la journée en un seul endroit paisible : des horaires de prière là où vous êtes, un takbir ou l’adhan à chaque prière, le Coran entier à lire et à écouter, les adhkar du matin et du soir, et la qibla. Gratuite, sans publicité ni compte, elle fonctionne hors ligne. | no |
| 118 | docs/store/listing/fr.md :90-95 | as row 110 | as row 110 | no |
| 119 | docs/store/listing/fr.md :97-104 | as row 111 | as row 111 | no |
| 120 | docs/store/listing/fr.md :109-111 | as row 112 | as row 112 | no |
| 121 | docs/store/listing/fr.md :116-116 | On your wrist: on Apple Watch, Taqwa shows the next prayer and its countdown in your watch face's complications, counts tasbeeh with the Digital Crown, and points to the Qibla. The times come from your iPhone, so your watch and your iPhone always agree. | Au poignet : sur l’Apple Watch, la prochaine prière et son compte à rebours dans les complications de votre cadran, le tasbih compté avec la Digital Crown, et la qibla. Les horaires viennent de votre iPhone : montre et iPhone sont toujours d’accord. | no |
| 122 | docs/store/listing/fr.md :118-122 | MADE TO BE TRUSTED • No account, no adverts, no analytics and no tracking of any kind. • Your location is used on your phone to calculate times. Only your own devices can see your location. • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted. • Free and open source under the GPL, with every source and licence credited in the app. | CONÇUE POUR ÊTRE DIGNE DE CONFIANCE • Sans mesure d’audience ni suivi d’aucune sorte. • Votre position sert, sur votre téléphone, à calculer les horaires. Seuls vos propres appareils peuvent voir votre position. • Internet sert à la récitation : l’audio demandé et, dès lors, une vérification quotidienne de la liste des récitateurs. Si vous ajoutez Taqwa à votre montre, Apple peut acheminer ses données par Internet, chiffrées. • Gratuite et libre (GPL), avec chaque source et licence créditée dans l’application. | no |
| 123 | docs/store/listing/fr.md :124-124 | as row 115 | as row 115 | no |
| 124 | docs/store/listing/fr.md :126-126 | (Its own text since 1.6.0, as each store names only its own watch (App Store Review Guideline 2.3.10): the Play full description above, with the Apple Watch paragraph in place of the Wear OS one, and Apple in place of Google in the bullet after the location one.) | (Its own text since 1.6.0, as each store names only its own watch (App Store Review Guideline 2.3.10): the Play full description above, with the Apple Watch paragraph in place of the Wear OS one, and Apple in place of Google in the bullet after the location one. A draft, to native review (plan 5 Task 11), as above.) | yes |
| 125 | docs/store/listing/fr.md :147-148 | ## Review notes (App Store and Play, English only) No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. Everything works offline except recitation downloads, which fetch audio from a public GitHub release on request. To see the lock-screen player, play a surah and lock the phone. Widgets are added from the home screen's widget gallery. Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it; the privacy policy is at https://taqwa.world/privacy/. \| App Store Connect's Notes field carries the longer answers to App Review's information request of 23 September 2026 instead, brought up to date for the watch release: `docs/store/app-review/2026-10-30-notes-1.6.0-42.txt` (the 23 September reply and the earlier notes files stay as the record of what was sent then). Apple asked for them to stay there for future submissions, so update the newest file and the field together when a feature, a network use or a licence changes. The field holds 4,000 bytes. | ## Review notes (App Store and Play, English only) No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. Everything works offline except recitation downloads, which fetch audio from a public GitHub release on request. To see the lock-screen player, play a surah and lock the phone. Widgets are added from the home screen's widget gallery. Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it; the privacy policy is at https://taqwa.world/privacy/. | no |
| 126 | site/pages/fr/home.html :69 | On your wrist | Au poignet | yes |
| 127 | site/pages/fr/home.html :70 | On Galaxy Watch and other Wear OS watches, the Taqwa face in four styles, complications for any face and a Tile; on Apple Watch, complications and the Smart Stack. On both, Today, tasbeeh counted with the bezel or the crown, and the Qibla, all from your phone’s own prayer times and in your phone’s language. | Sur la Galaxy Watch et les autres montres Wear OS, le cadran Taqwa en quatre styles, des complications pour tout cadran et une carte ; sur l’Apple Watch, des complications et le défilement intelligent. Sur les deux, l’écran Aujourd’hui, le tasbih compté à la lunette ou à la couronne, et la qibla, toujours avec les horaires de votre téléphone et dans sa langue. | yes |
| 128 | site/pages/fr/home.html :105 | Offline by design. Online only for what you choose. | Hors ligne par conception. En ligne seulement pour ce que vous choisissez. | yes |
| 129 | site/pages/fr/home.html :106 | There is no server behind Taqwa and no account to create. The internet is used for recitation: fetching a surah when you download it or play one not yet downloaded, and, once you have used recitation, checking once a day for an updated list of reciters. Until then, the app itself makes no network request. If you add Taqwa to your watch, Google or Apple may carry its data between your phone and watch over the internet, encrypted so only your phone and watch can read it. | Il n’y a aucun serveur derrière Taqwa et aucun compte à créer. Internet sert à la récitation : récupérer une sourate quand vous la téléchargez ou en écoutez une pas encore téléchargée, et, une fois que vous avez utilisé la récitation, vérifier une fois par jour s’il existe une liste de récitateurs plus récente. Tant que vous n’avez pas utilisé la récitation, l’application elle-même ne fait aucune requête réseau. Si vous ajoutez Taqwa à votre montre, Google ou Apple peut acheminer ses données entre votre téléphone et votre montre par Internet, chiffrées pour que seuls votre téléphone et votre montre puissent les lire. | yes |
| 130 | site/pages/fr/home.html :110 | Stays on your devices Only your own devices can see your location, settings, bookmarks and counts. | Tout reste sur vos appareils Seuls vos propres appareils peuvent voir votre position, vos paramètres, vos signets et vos compteurs. | yes |
| 131 | site/pages/fr/home.html :111 | Online only for what you choose The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google or Apple may carry its data over the internet, encrypted so only your phone and watch can read it. | En ligne seulement pour ce que vous choisissez Internet sert aux récitations que vous choisissez de télécharger, depuis le dépôt public de Taqwa sur GitHub. Si vous ajoutez Taqwa à votre montre, Google ou Apple peut acheminer ses données par Internet, chiffrées pour que seuls votre téléphone et votre montre puissent les lire. | yes |
| 132 | site/pages/fr/support.html :33 | Taqwa on your watch | Taqwa sur votre montre | yes |
| 133 | site/pages/fr/support.html :34 | On a Galaxy Watch or another Wear OS watch, install Taqwa from Settings › Watch in Taqwa on the phone while the watch is nearby, or from the Play Store on the watch. On an Apple Watch, the watch app comes with the iPhone app; if it is not on the watch, install it from the Watch app on the iPhone. | Sur une Galaxy Watch ou une autre montre Wear OS, installez Taqwa depuis Paramètres › Montre dans Taqwa sur le téléphone, la montre à proximité, ou depuis le Play Store de la montre. Sur une Apple Watch, l’application de la montre est fournie avec l’application iPhone ; si elle n’est pas sur la montre, installez-la depuis l’app Apple Watch de l’iPhone. | yes |
| 134 | site/pages/fr/support.html :35 | The Taqwa face needs Wear OS 6: choose Add the Taqwa face on the watch, or touch and hold your watch face and pick Taqwa, then touch and hold it again to choose its style, colour and slots. On any face, and on an Apple Watch, Taqwa’s complications show the next prayer and its countdown. On Wear OS 3 a complication’s countdown moves only at each prayer; Today, in the watch app, always counts down exactly. | Le cadran Taqwa nécessite Wear OS 6 : choisissez « Ajouter le cadran Taqwa » sur la montre, ou appuyez longuement sur votre cadran et choisissez Taqwa, puis appuyez de nouveau longuement dessus pour choisir son style, sa couleur et ses emplacements. Sur tout cadran, et sur l’Apple Watch, les complications de Taqwa montrent la prochaine prière et son compte à rebours. Sous Wear OS 3, le compte à rebours d’une complication n’avance qu’à chaque prière ; l’écran Aujourd’hui de l’application de la montre compte toujours juste. | yes |
| 135 | site/pages/fr/support.html :36 | Your phone sends the watch its prayer times a month ahead, so the watch keeps working for weeks away from it. If the watch says “Open Taqwa on your phone”, it has no times yet, or they have run out: open Taqwa on the phone with the watch nearby. The watch speaks the language you chose in Taqwa on the phone, with its digits and its clock; the watch’s own menus follow the watch’s language. | Votre téléphone envoie à la montre ses horaires de prière un mois à l’avance : la montre continue de fonctionner des semaines loin de lui. Si la montre affiche « Ouvrez Taqwa sur votre téléphone », elle n’a pas encore d’horaires, ou ils sont épuisés : ouvrez Taqwa sur le téléphone, la montre à proximité. La montre parle la langue choisie dans Taqwa sur le téléphone, avec ses chiffres et son format d’heure ; les menus propres à la montre suivent la langue de la montre. | yes |
| 136 | site/pages/fr/support.html :37 | The Qibla screen uses the watch’s own compass. Hold your wrist level, away from magnets, and if it asks to be calibrated, move it in a figure of eight a few times. | L’écran Qibla utilise la boussole de la montre. Gardez le poignet à plat, loin des aimants, et si elle demande à être calibrée, faites-lui décrire un huit plusieurs fois. | yes |
| 137 | site/pages/fr/support.html :43 | Everything Taqwa knows lives on your own devices: your location or chosen city, your settings, bookmarks, adhkar and tasbeeh counts, and downloaded recitations. There is no account and no server, so there is nothing to request or delete on our side. Uninstalling Taqwa from your phone and your watch removes all of it. The privacy policy says this in full. | Tout ce que Taqwa sait se trouve sur vos propres appareils : votre position ou la ville que vous avez choisie, vos paramètres, vos signets, vos compteurs d’adhkar et de tasbih, et les récitations téléchargées. Il n’y a ni compte ni serveur, il n’y a donc rien à demander ou à supprimer de notre côté. Désinstaller Taqwa de votre téléphone et de votre montre efface tout. La politique de confidentialité le dit en détail. | yes |
| 138 | site/pages/fr/meta.json :28 | "description": "What Taqwa stores on your devices, what it uses the internet for, and what it never does." | "description": "Ce que Taqwa enregistre sur vos appareils, ce pour quoi elle utilise Internet, et ce qu’elle ne fait jamais." | no |
| 139 | docs/store/release-notes/1.6.0-100042-wear.txt :7 <fr-FR> | The Taqwa face in four styles, complications for any face, a Tile, and Today, Tasbeeh and Qibla. Needs Taqwa on your phone, which sends the prayer times. | Le cadran Taqwa en quatre styles, des complications pour tout cadran, une carte, et Aujourd’hui, Tasbih et Qibla. Nécessite Taqwa sur votre téléphone, qui envoie les horaires de prière. | listed here |
| 140 | docs/store/release-notes/1.6.0-42-appstore.txt :13 (French) | What's new in 1.6.0: / • Taqwa on your Apple Watch: the next prayer in your complications, tasbeeh with the Digital Crown, and Qibla | Nouveautés de la 1.6.0 : / • Taqwa sur votre Apple Watch : la prochaine prière dans vos complications, le tasbih avec la Digital Crown, et la qibla | yes |
| 141 | docs/store/release-notes/1.6.0-42.txt :9 <fr-FR> | What's new in 1.6.0: / • Taqwa on your watch: a watch face, complications and a Tile, tasbeeh and Qibla, on Wear OS watches such as Galaxy Watch | Nouveautés de la 1.6.0 : / • Taqwa sur votre montre : un cadran, des complications et une carte, le tasbih et la qibla, sur les montres Wear OS comme la Galaxy Watch | listed here |

### Turkish: 135 rows

| # | Where | English | Draft | Marker |
|---|---|---|---|---|
| 1 | compose/values-tr/strings.xml onboarding_location_body (:21) | Prayer times depend on where you are. Everything is calculated on your device. Only your own devices can see your location. | Namaz vakitleri konumunuza bağlıdır. Her şey cihazınızda hesaplanır. Konumunuzu yalnızca kendi cihazlarınız görebilir. | yes |
| 2 | compose/values-tr/strings.xml settings_add_to_apple_watch (:131) | Add to Apple Watch | Apple Watch’a ekle | yes |
| 3 | compose/values-tr/strings.xml settings_add_to_apple_watch_failed (:133) | The face could not be added. Try again from the Watch app. | Saat kadranı eklenemedi. Apple Watch uygulamasından yeniden deneyin. | yes |
| 4 | compose/values-tr/strings.xml settings_group_watch (:140) | WATCH | SAAT | yes |
| 5 | compose/values-tr/strings.xml settings_watch_install (:142) | Install on your watch | Saate yükle | yes |
| 6 | compose/values-tr/strings.xml settings_watch_face (:144) | Add the Taqwa face | Taqwa saat yüzünü ekle | yes |
| 7 | compose/values-tr/strings.xml settings_watch_continue (:146) | Continue on your watch | Saatinizden devam edin | yes |
| 8 | compose/values-tr/strings.xml settings_watch_unreachable (:148) | Couldn’t reach your watch | Saatinize ulaşılamadı | yes |
| 9 | compose/values-tr/strings.xml about_stays_title (:163) | Stays on your devices | Cihazlarınızda kalır | yes |
| 10 | compose/values-tr/strings.xml about_stays_body (:165) | Only your own devices can see your location, settings, bookmarks and counts. | Konumunuzu, ayarlarınızı, yer imlerinizi ve sayaçlarınızı yalnızca kendi cihazlarınız görebilir. | yes |
| 11 | compose/values-tr/strings.xml about_online_title (:167) | Online only for what you choose | Yalnızca seçtikleriniz için çevrimiçi | yes |
| 12 | compose/values-tr/strings.xml about_online_body_android (:169) | The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted so only your phone and watch can read it. | İnternet, Taqwa’nın GitHub’daki herkese açık deposundan indirmeyi seçtiğiniz tilavetler için kullanılır. Taqwa’yı saatinize eklerseniz Google onun verilerini internet üzerinden taşıyabilir; veriler şifrelidir, yalnızca telefonunuz ve saatiniz okuyabilir. | yes |
| 13 | compose/values-tr/strings.xml about_online_body_ios (:171) | The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted so only your phone and watch can read it. | İnternet, Taqwa’nın GitHub’daki herkese açık deposundan indirmeyi seçtiğiniz tilavetler için kullanılır. Taqwa’yı saatinize eklerseniz Apple onun verilerini internet üzerinden taşıyabilir; veriler şifrelidir, yalnızca telefonunuz ve saatiniz okuyabilir. | yes |
| 14 | compose/values-tr/strings.xml location_privacy_note (:267) | Coordinates are stored on your device and used only to calculate times. Only your own devices can see your location. | Koordinatlar cihazınızda saklanır ve yalnızca vakitleri hesaplamak için kullanılır. Konumunuzu yalnızca kendi cihazlarınız görebilir. | yes |
| 15 | wearApp/res/values-tr/strings.xml app_name (:4) | Taqwa | Taqwa | no |
| 16 | wearApp/res/values-tr/strings.xml complication_next_prayer (:5) | Next prayer | Sonraki vakit | no |
| 17 | wearApp/res/values-tr/strings.xml complication_mark (:6) | Taqwa | Taqwa | no |
| 18 | wearApp/res/values-tr/strings.xml complication_mark_description (:7) | Taqwa | Taqwa | no |
| 19 | wearApp/res/values-tr/strings.xml tile_label (:8) | Prayer times | Namaz vakitleri | no |
| 20 | wearApp/res/values-tr/strings.xml open_taqwa_on_phone (:10) | Open Taqwa on your phone | Telefonunuzda Taqwa’yı açın | yes |
| 21 | wearApp/res/values-tr/strings.xml update_taqwa_on_watch (:11) | Update Taqwa on your watch | Saatinizde Taqwa’yı güncelleyin | yes |
| 22 | wearApp/res/values-tr/strings.xml complication_day (:12) | Prayer day | Günün vakitleri | yes |
| 23 | wearApp/res/values-tr/strings.xml complication_day_dial (:13) | Taqwa face: day dial | Taqwa kadranı: gün kadranı | yes |
| 24 | wearApp/res/values-tr/strings.xml complication_dates (:14) | Dates | Tarihler | yes |
| 25 | wearApp/res/values-tr/strings.xml complication_day_title (:15) | Day | Gün | yes |
| 26 | wearApp/res/values-tr/strings.xml complication_day_description (:16) | Prayer times today | Bugünün namaz vakitleri | yes |
| 27 | wearApp/res/values-tr/strings.xml preview_prayer_label (:17) | ASR | İKİNDİ | yes |
| 28 | wearApp/res/values-tr/strings.xml preview_prayer_name (:18) | Asr | İkindi | yes |
| 29 | wearApp/res/values-tr/strings.xml preview_prayer_time (:19) | 16:10 | 16:10 | yes |
| 30 | wearApp/res/values-tr/strings.xml preview_countdown (:20) | in 1 h 12 min | 1 sa 12 dk sonra | yes |
| 31 | wearApp/res/values-tr/strings.xml preview_day_times (:21) | Fajr 4:33, Sunrise 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24 | Sabah 4:33, Güneş 6:01, Öğle 12:33, İkindi 16:10, Akşam 19:04, Yatsı 20:24 | yes |
| 32 | wearApp/res/values-tr/strings.xml preview_hijri (:22) | 6 Jumada al-Ula 1448 | 6 Cemaziyelevvel 1448 | yes |
| 33 | wearApp/res/values-tr/strings.xml preview_hijri_day_month (:23) | 6 Jumada al-Ula | 6 Cemaziyelevvel | yes |
| 34 | wearApp/res/values-tr/strings.xml preview_gregorian (:24) | SUN 18 OCT | PAZ 18 EKİ | yes |
| 35 | wearApp/res/values-tr/strings.xml preview_weekday (:25) | SUN | PAZ | yes |
| 36 | wearApp/res/values-tr/strings.xml preview_day_number (:26) | 18 | 18 | yes |
| 37 | wearApp/res/values-tr/strings.xml tile_description (:27) | Today’s prayers and the next one counted down | Bugünün vakitleri ve sonrakine geri sayım | yes |
| 38 | wearApp/res/values-tr/strings.xml face_add (:30) | Add the Taqwa face | Taqwa saat yüzünü ekle | yes |
| 39 | wearApp/res/values-tr/strings.xml face_about (:32) | The next prayer and its countdown on your watch face. | Sonraki namaz ve geri sayımı saat yüzünüzde. | yes |
| 40 | wearApp/res/values-tr/strings.xml face_active (:34) | Taqwa is your watch face. | Saat yüzünüz Taqwa. | yes |
| 41 | wearApp/res/values-tr/strings.xml face_choose_it (:36) | Touch and hold your watch face, then choose Taqwa. | Saat yüzünüze dokunup basılı tutun, ardından Taqwa’yı seçin. | yes |
| 42 | wearApp/res/values-tr/strings.xml face_failed (:38) | Couldn’t add the face. Try again later. | Saat yüzü eklenemedi. Daha sonra yeniden deneyin. | yes |
| 43 | watchface/res/values-tr/strings.xml watch_face_name (:4) | Taqwa | Taqwa | no |
| 44 | watchface/res/values-tr/strings.xml slot_next_prayer (:5) | Next prayer | Sonraki vakit | no |
| 45 | watchface/res/values-tr/strings.xml style (:7) | Style | Stil | yes |
| 46 | watchface/res/values-tr/strings.xml style_arch (:8) | Arch | Kemer | yes |
| 47 | watchface/res/values-tr/strings.xml style_ring (:9) | Ring | Halka | yes |
| 48 | watchface/res/values-tr/strings.xml style_sun (:10) | Sun | Güneş | yes |
| 49 | watchface/res/values-tr/strings.xml style_moon (:11) | Moon | Ay | yes |
| 50 | watchface/res/values-tr/strings.xml accent (:12) | Colour | Renk | yes |
| 51 | watchface/res/values-tr/strings.xml accent_amber (:13) | Amber | Kehribar | yes |
| 52 | watchface/res/values-tr/strings.xml accent_pearl (:14) | Pearl | İnci | yes |
| 53 | watchface/res/values-tr/strings.xml accent_sage (:15) | Sage | Adaçayı | yes |
| 54 | watchface/res/values-tr/strings.xml accent_night (:16) | Night | Gece | yes |
| 55 | watchface/res/values-tr/strings.xml slot_day (:17) | Prayer day | Günün vakitleri | yes |
| 56 | watchface/res/values-tr/strings.xml slot_dates (:18) | Dates | Tarihler | yes |
| 57 | watchface/res/values-tr/strings.xml slot_dates_short (:19) | Date and moon | Tarih ve ay evresi | yes |
| 58 | watchface/res/values-tr/strings.xml slot_countdown (:20) | Time to the next prayer | Sonraki vakte kalan süre | yes |
| 59 | watchface/res/values-tr/strings.xml slot_day_dial (:21) | Prayer day dial | Günün vakitleri kadranı | yes |
| 60 | watchface/res/values-tr/strings.xml slot_left (:22) | Left complication | Sol komplikasyon | yes |
| 61 | watchface/res/values-tr/strings.xml slot_right (:23) | Right complication | Sağ komplikasyon | yes |
| 62 | iosApp/TaqwaWatch/tr.lproj/Localizable.strings open_taqwa_on_phone (:2) | Open Taqwa on your phone | Telefonunuzda Taqwa’yı açın | yes |
| 63 | iosApp/TaqwaWatch/tr.lproj/Localizable.strings update_taqwa_on_watch (:5) | as row 21 | as row 21 | yes |
| 64 | iosApp/TaqwaWatch/tr.lproj/InfoPlist.strings CFBundleDisplayName (:2) | Taqwa | Taqwa | no |
| 65 | iosApp/TaqwaWatchWidgets/tr.lproj/Localizable.strings next_prayer_widget_name (:2) | Next prayer | Sonraki namaz | no |
| 66 | iosApp/TaqwaWatchWidgets/tr.lproj/Localizable.strings next_prayer_widget_description (:5) | The next prayer, its time and the countdown. | Sonraki namaz, vakti ve geri sayım. | yes |
| 67 | iosApp/TaqwaWatchWidgets/tr.lproj/Localizable.strings open_taqwa_on_phone (:8) | Open Taqwa on your phone | Telefonunuzda Taqwa’yı açın | yes |
| 68 | iosApp/TaqwaWatchWidgets/tr.lproj/Localizable.strings update_taqwa_on_watch (:11) | as row 21 | as row 21 | yes |
| 69 | iosApp/TaqwaWatchWidgets/tr.lproj/InfoPlist.strings CFBundleDisplayName (:2) | Taqwa | Taqwa | no |
| 70 | iosApp/iosApp/tr.lproj/InfoPlist.strings NSLocationWhenInUseUsageDescription (:4) | Taqwa uses your location to calculate prayer times on your device. Only your own devices can see your location. | Taqwa, namaz vakitlerini cihazınızda hesaplamak için konumunuzu kullanır. Konumunuzu yalnızca kendi cihazlarınız görebilir. | yes |
| 71 | WatchWords.kt COUNTDOWN.hoursMinutes (:84) | in %1$s h %2$s min | %1$s sa %2$s dk sonra | yes |
| 72 | WatchWords.kt COUNTDOWN.hours (:85) | in %1$s h | %1$s sa sonra | yes |
| 73 | WatchWords.kt COUNTDOWN.minutes (:86) | in %1$s min | %1$s dk sonra | yes |
| 74 | WatchWords.kt COUNTDOWN.underAMinute (:87) | in under a minute | bir dakika içinde | yes |
| 75 | WatchWords.kt FACE.add (:138) | Add the Taqwa face | Taqwa saat yüzünü ekle | yes |
| 76 | WatchWords.kt FACE.about (:139) | as row 39 | as row 39 | yes |
| 77 | WatchWords.kt FACE.active (:140) | Taqwa is your watch face. | Saat yüzünüz Taqwa. | yes |
| 78 | WatchWords.kt FACE.chooseIt (:141) | as row 41 | as row 41 | yes |
| 79 | WatchWords.kt FACE.failed (:142) | as row 42 | as row 42 | yes |
| 80 | WatchWords.kt SCREENS.openOnPhone (:240) | Open Taqwa on your phone | Telefonunuzda Taqwa’yı açın | yes |
| 81 | WatchWords.kt SCREENS.updateOnWatch (:241) | as row 21 | as row 21 | yes |
| 82 | WatchWords.kt SCREENS.bezelHint (:242) | Turn the bezel or tap | Çerçeveyi çevirin veya dokunun | yes |
| 83 | WatchWords.kt SCREENS.crownHint (:243) | Turn the crown or tap | Digital Crown’u çevirin veya dokunun | yes |
| 84 | WatchWords.kt SCREENS.undoAction (:244) | Take one away | Bir eksilt | yes |
| 85 | WatchWords.kt SCREENS.phrasesAction (:245) | Choose a dhikr | Zikir seç | yes |
| 86 | WatchWords.kt SCREENS.holdLevel (:246) | Hold your wrist level | Saatinizi düz tutun | yes |
| 87 | WatchWords.kt SCREENS.distanceOnDial (:247) | %1$s km | %1$s km | yes |
| 88 | WatchWords.kt SCREENS.qiblaDetailOnDial (:248) | %1$s° · %2$s km | %1$s° · %2$s km | yes |
| 89 | WatchWords.kt SCREENS.countSpoken (:249) | %1$s of %2$s | %1$s, toplam %2$s | yes |
| 90 | WatchWords.kt SCREENS.nextSpoken (:250) | %1$s. Next, %2$s | %1$s. Sıradaki: %2$s | yes |
| 91 | WatchWords.kt SCREENS.setCompleteSpoken (:251) | %1$s. Round complete | %1$s. Tur tamamlandı | yes |
| 92 | WatchWords.kt SCREENS.roundSpoken (:252) | Round complete. %1$s, round %2$s | Tur tamamlandı. %1$s, %2$s. tur | yes |
| 93 | WatchWords.kt SCREENS.nameRoundSpoken (:253) | %1$s, round %2$s | %1$s, %2$s. tur | yes |
| 94 | WatchWords.kt SCREENS.countAction (:254) | Count | Say | yes |
| 95 | WatchWords.kt SCREENS.turnRight (:255) | up to 180°: Turn right about %1$s degrees | up to 180°: Yaklaşık %1$s derece sağa dönün | yes |
| 96 | WatchWords.kt SCREENS.turnLeft (:256) | up to 180°: Turn left about %1$s degrees | up to 180°: Yaklaşık %1$s derece sola dönün | yes |
| 97 | PRIVACY.tr.md :3-3 | _Last updated 30 October 2026. Applies to Taqwa 1.0.0 and later on Android and iOS._ | _Son güncelleme 30 Ekim 2026. Android ve iOS'ta Taqwa 1.0.0 ve sonrası için geçerlidir._ | no |
| 98 | PRIVACY.tr.md :5-8 | Taqwa is a free Islamic app with no account, no ads and no analytics. It works offline. It uses the internet to download Quran recitations, and only when you ask it to. If you add Taqwa to your watch, its data may travel over the internet too, encrypted, as below. | Taqwa; hesabı, reklamı ve analizi olmayan ücretsiz bir İslami uygulamadır. Çevrimdışı çalışır. İnterneti Kur'an tilavetlerini indirmek için kullanır, o da yalnızca siz istediğinizde. Taqwa'yı saatinize eklerseniz, verileri de aşağıda anlatıldığı gibi şifreli olarak internet üzerinden iletilebilir. | yes |
| 99 | PRIVACY.tr.md :10-10 | ## What stays on your devices | ## Cihazlarınızda kalanlar | yes |
| 100 | PRIVACY.tr.md :12-13 | Everything the app knows is stored on your own devices: | Uygulamanın bildiği her şey kendi cihazlarınızda saklanır: | yes |
| 101 | PRIVACY.tr.md :21-23 | None of this is sent to us or to anyone else. There is no server behind Taqwa and no account to create. Deleting Taqwa from your phone and your watch deletes all of it. | Bunların hiçbiri bize ya da bir başkasına gönderilmez. Taqwa'nın arkasında bir sunucu yok, açılacak bir hesap da yok. Taqwa'yı telefonunuzdan ve saatinizden silmek bunların hepsini siler. | yes |
| 102 | PRIVACY.tr.md :28-28 | ## Your watch | ## Saatiniz | yes |
| 103 | PRIVACY.tr.md :30-35 | If you add Taqwa to your watch, your phone gives it your city, the Qibla's direction and distance, your area's compass correction, the coming month's prayer times as the app writes them, and your own tasbeeh phrases, with a random number made once on your phone that tells your watch which phone sent them. When your phone and watch are not directly connected, Google (Android) or Apple (iPhone) may carry this data over the internet, end-to-end encrypted so only your phone and watch can read it. | Taqwa'yı saatinize eklerseniz telefonunuz ona şehrinizi, kıblenin yönünü ve uzaklığını, bölgenizin pusula düzeltmesini, önümüzdeki ayın namaz vakitlerini uygulamanın yazdığı biçimde ve kendi tesbih ifadelerinizi, bunları hangi telefonun gönderdiğini saatinize bildiren ve telefonunuzda bir kez oluşturulan rastgele bir sayıyla birlikte verir. Telefonunuz ve saatiniz doğrudan bağlı olmadığında Google (Android) ya da Apple (iPhone) bu verileri internet üzerinden taşıyabilir; veriler uçtan uca şifrelidir, böylece yalnızca telefonunuz ve saatiniz okuyabilir. | yes |
| 104 | PRIVACY.tr.md :39-42 | The app asks for your location only to compute prayer times and the Qibla direction, and only if you allow it. Picking a city from the built-in list works just as well and needs no permission. Your location is used on the device. Only your own devices can see your location. | Uygulama konumunuzu yalnızca namaz vakitlerini ve kıble yönünü hesaplamak için, yalnızca siz izin verirseniz ister. Uygulamayla gelen listeden bir şehir seçmek de aynı işi görür ve hiçbir izin gerektirmez. Konumunuz cihaz üzerinde kullanılır. Konumunuzu yalnızca kendi cihazlarınız görebilir. | yes |
| 105 | PRIVACY.tr.md :57-61 | Once you have opened anything to do with recitation, whether the reciter list, the Recitation settings or a download, the app also checks once a day for an updated list of reciters, from the same repository. That request is the same for everyone and reveals nothing but your IP address. Until you first use recitation, the app itself makes no network request at all. | Tilavetle ilgili herhangi bir şeyi bir kez açtıktan sonra — kari listesi, Tilavet ayarları ya da bir indirme — uygulama ayrıca günde bir kez, aynı depodan güncellenmiş bir kari listesi olup olmadığını kontrol eder. Bu istek herkes için aynıdır ve IP adresinizden başka hiçbir şeyi ele vermez. Tilaveti ilk kez kullanana kadar uygulamanın kendisi hiçbir ağ isteği yapmaz. | yes |
| 106 | PRIVACY.tr.md :77-84 | Taqwa opts out of Android's app backup, so your location and settings are not copied to Google. On iOS, your settings, bookmarks, counts and downloaded recitations are excluded from iCloud backup; the only files of Taqwa's own that are backed up are the widgets' small caches, the upcoming prayer times and the hundred ayahs the daily-ayah widget rotates through, neither of which contains your location. If you add Taqwa to an Apple Watch, iOS keeps the last copy your iPhone sent it, which names your city, and that copy may be in your iPhone's backup. An iCloud backup is encrypted with keys Apple keeps, unless you turn on Advanced Data Protection, which encrypts it end to end; a backup to a computer is encrypted only if you choose to encrypt it. | Taqwa, Android'in uygulama yedeklemesinin dışında kalır; böylece konumunuz ve ayarlarınız Google'a kopyalanmaz. iOS'ta ayarlarınız, yer imleriniz, sayaçlarınız ve indirdiğiniz tilavetler iCloud yedeklemesinin dışında tutulur; Taqwa'nın kendi dosyalarından yedeklenen tek şey widget'ların küçük önbellekleri, yani yaklaşan namaz vakitleri ile günün ayeti widget'ının sırayla gösterdiği yüz ayettir. Bunların hiçbiri konumunuzu içermez. Taqwa'yı bir Apple Watch'a eklerseniz iOS, iPhone'unuzun ona gönderdiği ve şehrinizin adını taşıyan son kopyayı saklar; bu kopya iPhone'unuzun yedeğinde bulunabilir. iCloud yedeği, anahtarlarını Apple'ın sakladığı bir şifrelemeyle korunur; İleri Düzey Veri Koruma'yı açarsanız uçtan uca şifrelenir. Bilgisayardaki bir yedek ise yalnızca siz seçerseniz şifrelenir. | yes |
| 107 | PRIVACY.tr.md :88-93 | - No account, sign-in or profile. - No ads and no advertising identifier. - No analytics, crash reporting or usage statistics. - No third-party SDK that talks to the internet. The Android app's one Google Play services library links your phone to your own watch, and Google may relay that link, encrypted, as above. - No sale or sharing of data, because there is none to sell or share. | - Hesap, oturum açma ya da profil yok. - Reklam ve reklam tanımlayıcısı yok. - Analiz, çökme raporlaması ya da kullanım istatistiği yok. - İnternete bağlanan üçüncü taraf SDK yok. Android uygulamasındaki tek Google Play hizmetleri kütüphanesi telefonunuzu kendi saatinize bağlar; Google bu bağlantıyı, yukarıda anlatıldığı gibi şifreli olarak aktarabilir. - Veri satışı ya da paylaşımı yok, çünkü satılacak veya paylaşılacak veri yok. | yes |
| 108 | docs/store/listing/tr.md :17-22 | PRAYER TIMES • Times for where you are, from your phone's location or a city you choose from more than 34,000. • Times from the timetable the mosques near you follow. Where Taqwa holds it, it reproduces it and checks it day by day against the published tables: on every day checked, no prayer starts before the published time. Where a timetable is not yet fully checked, Taqwa adds a safety margin; where mosques follow different timetables, it shows cautious times, each prayer once all have begun it; and where no timetable is known, it calculates on the safe side. • Tap the (i) on the Prayer screen to see whose times these are, and About these times for how they are made and checked. Match my mosque looks for your mosque's timetable from two times on its board, and the usual calculation methods (Muslim World League, Egyptian, Karachi, ISNA and more) and a per-prayer adjustment are there too. • Rules for high latitudes, where the night is too short for the usual angles. • A notification for each prayer: a clear tone, a takbir, or the adhan in a choice of voices, among them Makkah and Madinah, or a recording from your phone, with an optional reminder before it, an Iqama reminder a chosen number of minutes after the adhan, and a Duha reminder at its best time. • A Jumu'ah pill on Friday's Dhuhr, home-screen widgets with today's times and the countdown to the next prayer, and the Hijri date alongside the Gregorian one. • A 24-hour or 12-hour clock, as you prefer. | NAMAZ VAKİTLERİ • Telefonunuzun konumundan ya da 34.000’den fazla şehir arasından seçeceğiniz bir şehirden, bulunduğunuz yere göre vakitler. • Yakınınızdaki camilerin uyduğu takvime göre vakitler. Taqwa bu takvime sahipse onu yeniden hesaplar ve yayımlanan tablolarla gün gün karşılaştırır: karşılaştırılan her günde hiçbir namaz yayımlanan vakitten önce başlamaz. Takvim henüz tam doğrulanmadıysa Taqwa bir güvenlik payı ekler; camiler farklı takvimlere uyuyorsa ihtiyatlı vakitler gösterir, yani her namaz hepsinde vakit girdikten sonra görünür; hiçbir takvim bilinmiyorsa vakitleri ihtiyatlı biçimde hesaplar. • Vakitlerin kime ait olduğunu görmek için Namaz ekranındaki (i) simgesine dokunun; nasıl hesaplanıp doğrulandıkları “Bu vakitler hakkında” ekranında anlatılır. “Camimle eşleştir”, caminizin panosundaki iki vakitten takvimini bulmaya çalışır; alışılmış hesaplama yöntemleri (Dünya İslam Birliği, Mısır, Karaçi, ISNA vb.), her namaz için ayrı düzeltme ve yüksek enlem kuralları da mevcuttur. • Her namaz için bir bildirim: net bir uyarı tonu, bir tekbir ya da aralarında Mekke ve Medine’nin de bulunduğu seslerden birinin okuduğu ezan veya telefonunuzdaki bir kayıt; dilerseniz öncesinde bir hatırlatma, ezandan belirlediğiniz dakika sonra bir kamet hatırlatması ve en faziletli vaktinde bir kuşluk namazı hatırlatmasıyla. • Cuma günleri öğle vaktinde Cuma etiketi, günün vakitlerini ve sonraki vakte geri sayımı gösteren ana ekran widget’ları, miladi tarihin yanında hicri tarih ve 24 ya da 12 saat biçimi. | no |
| 109 | docs/store/listing/tr.md :24-31 | THE QURAN • The complete Uthmani text, in the same script as the Madinah Mushaf, and the printed Mushaf's page layout if you want it. • Translations in English, Arabic tafsir, French, Turkish, Indonesian, Urdu and Bengali, shown under each ayah. • Search across the Arabic text and the translations. • Bookmarks, copy and share, and a card that takes you back to where you stopped. • Recitation by ten reciters, downloaded once per surah and played offline, with lock-screen controls; when a surah ends the next one follows. • During recitation, your phone's own voice can read the translation or tafsir aloud after each ayah, where the phone has a voice for that language. • An ayah-of-the-day widget. | KUR’AN • Medine Mushafı ile aynı hatta eksiksiz Osmanî metin ve dilerseniz basılı Mushaf’ın sayfa düzeni. • Her ayetin altında gösterilen Türkçe, İngilizce, Fransızca, Endonezce, Urduca ve Bengalce mealler ile Arapça tefsir. • Arapça metinde ve meallerde arama. • Yer imleri, kopyalama ve paylaşma, bir de sizi kaldığınız yere geri götüren bir kart. • On karinin tilaveti; her sure için bir kez indirilir, çevrimdışı çalar, kilit ekranı denetimleriyle; bir sure bitince sonraki başlar. • Tilavet sırasında telefonunuzun kendi sesi, o dil için telefonda bir ses varsa, her ayetten sonra meali ya da tefsiri sesli okuyabilir. • Günün ayeti widget’ı. | no |
| 110 | docs/store/listing/tr.md :43-43 | On your wrist: on Wear OS watches such as Galaxy Watch, Taqwa shows the next prayer and its countdown on its own watch face, in complications for any face and in a Tile, counts tasbeeh with the bezel or the crown, and points to the Qibla. The times come from your phone, so your watch and your phone always agree. | Bileğinizde: Galaxy Watch gibi Wear OS saatlerde Taqwa, sonraki namazı ve geri sayımını kendi saat yüzünde, her saat yüzüne eklenebilen komplikasyonlarda ve bir kutucukta gösterir; tesbihi çerçeve ya da taçla sayar, Kıbleyi gösterir. Vakitler telefonunuzdan gelir; saatiniz ve telefonunuz hep aynı vakti gösterir. | no |
| 111 | docs/store/listing/tr.md :45-49 | MADE TO BE TRUSTED • No account, no adverts, no analytics and no tracking of any kind. • Your location is used on your phone to calculate times. Only your own devices can see your location. • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted. • Free and open source under the GPL, with every source and licence credited in the app. | GÜVENİLMEK İÇİN YAPILDI • Analiz yok, hiçbir türden takip yok. • Konumunuz vakitleri hesaplamak için telefonunuzda kullanılır. Konumunuzu yalnızca kendi cihazlarınız görebilir. • İnternet tilavet için kullanılır: istediğiniz ses kayıtları ve, tilaveti kullandıktan sonra, kari listesinin günde bir kez denetlenmesi. Taqwa’yı saatinize eklerseniz Google onun verilerini internet üzerinden şifreli olarak taşıyabilir. • GPL ile ücretsiz ve açık kaynak; her kaynak ve lisans uygulama içinde anılır. | no |
| 112 | docs/store/listing/tr.md :53-53 | (Plan 5 Task 10's cut, which makes room for the wrist paragraph within Play's 4,000 characters, for Mohamed's approval: the website and support lines are left out, since both stores show them in their own fields, and so is the line of interface languages, which the App Store lists itself; and the Mushaf bullet says the same in fewer words.) | (Plan 5 Task 10's cut, which makes room for the wrist paragraph within Play's 4,000 characters, for Mohamed's approval: the website and support lines are left out, since both stores show them in their own fields, and so is the line of interface languages, which the App Store lists itself; and the Mushaf bullet says the same in fewer words; and, as Turkish needs more room: the high-latitude rules join the methods bullet, the 24- or 12-hour clock the Jumu'ah bullet, and the trust section's first bullet keeps only what the opening does not already say (no account, no adverts). Since 1.5.0's longer notification bullet (main, merged at MM4), the methods bullet ends its list with 'vb.' for 've daha fazlası'. The wrist paragraph, the reworded lines, and the location bullet and the one after it are a draft, to native review (plan 5 Task 11).) | yes |
| 113 | docs/store/listing/tr.md :87-88 | ### Description (4000) Taqwa gives you the essentials of the day in one quiet place: prayer times where you are, a takbir or the adhan at each prayer, the whole Quran to read and listen to, the morning and evening adhkar, and the Qibla. It works offline, costs nothing, shows no adverts and asks for no account. | ### Description (4000) Taqwa, günün temel ihtiyaçlarını tek bir sakin yerde toplar: bulunduğunuz yerin namaz vakitleri, her vakitte bir tekbir ya da ezan, okumak ve dinlemek için Kur’an’ın tamamı, sabah ve akşam zikirleri ve Kıble. Çevrimdışı çalışır, hiçbir ücret istemez, reklam göstermez ve hesap açtırmaz. | no |
| 114 | docs/store/listing/tr.md :90-95 | as row 108 | as row 108 | no |
| 115 | docs/store/listing/tr.md :97-104 | as row 109 | as row 109 | no |
| 116 | docs/store/listing/tr.md :116-116 | On your wrist: on Apple Watch, Taqwa shows the next prayer and its countdown in your watch face's complications, counts tasbeeh with the Digital Crown, and points to the Qibla. The times come from your iPhone, so your watch and your iPhone always agree. | Bileğinizde: Apple Watch’ta Taqwa, sonraki namazı ve geri sayımını saat kadranınızın komplikasyonlarında gösterir; tesbihi Digital Crown ile sayar, Kıbleyi gösterir. Vakitler iPhone’unuzdan gelir; saatiniz ve iPhone’unuz hep aynı vakti gösterir. | no |
| 117 | docs/store/listing/tr.md :118-122 | MADE TO BE TRUSTED • No account, no adverts, no analytics and no tracking of any kind. • Your location is used on your phone to calculate times. Only your own devices can see your location. • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted. • Free and open source under the GPL, with every source and licence credited in the app. | GÜVENİLMEK İÇİN YAPILDI • Analiz yok, hiçbir türden takip yok. • Konumunuz vakitleri hesaplamak için telefonunuzda kullanılır. Konumunuzu yalnızca kendi cihazlarınız görebilir. • İnternet tilavet için kullanılır: istediğiniz ses kayıtları ve, tilaveti kullandıktan sonra, kari listesinin günde bir kez denetlenmesi. Taqwa’yı saatinize eklerseniz Apple onun verilerini internet üzerinden şifreli olarak taşıyabilir. • GPL ile ücretsiz ve açık kaynak; her kaynak ve lisans uygulama içinde anılır. | no |
| 118 | docs/store/listing/tr.md :126-126 | (Its own text since 1.6.0, as each store names only its own watch (App Store Review Guideline 2.3.10): the Play full description above, with the Apple Watch paragraph in place of the Wear OS one, and Apple in place of Google in the bullet after the location one.) | (Its own text since 1.6.0, as each store names only its own watch (App Store Review Guideline 2.3.10): the Play full description above, with the Apple Watch paragraph in place of the Wear OS one, and Apple in place of Google in the bullet after the location one. A draft, to native review (plan 5 Task 11), as above.) | yes |
| 119 | docs/store/listing/tr.md :147-148 | ## Review notes (App Store and Play, English only) No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. Everything works offline except recitation downloads, which fetch audio from a public GitHub release on request. To see the lock-screen player, play a surah and lock the phone. Widgets are added from the home screen's widget gallery. Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it; the privacy policy is at https://taqwa.world/privacy/. \| App Store Connect's Notes field carries the longer answers to App Review's information request of 23 September 2026 instead, brought up to date for the watch release: `docs/store/app-review/2026-10-30-notes-1.6.0-42.txt` (the 23 September reply and the earlier notes files stay as the record of what was sent then). Apple asked for them to stay there for future submissions, so update the newest file and the field together when a feature, a network use or a licence changes. The field holds 4,000 bytes. | ## Review notes (App Store and Play, English only) No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. Everything works offline except recitation downloads, which fetch audio from a public GitHub release on request. To see the lock-screen player, play a surah and lock the phone. Widgets are added from the home screen's widget gallery. Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it; the privacy policy is at https://taqwa.world/privacy/. | no |
| 120 | site/pages/tr/home.html :69 | On your wrist | Bileğinizde | yes |
| 121 | site/pages/tr/home.html :70 | On Galaxy Watch and other Wear OS watches, the Taqwa face in four styles, complications for any face and a Tile; on Apple Watch, complications and the Smart Stack. On both, Today, tasbeeh counted with the bezel or the crown, and the Qibla, all from your phone’s own prayer times and in your phone’s language. | Galaxy Watch ve diğer Wear OS saatlerde dört stilde Taqwa saat yüzü, her saat yüzüne komplikasyonlar ve bir kutucuk; Apple Watch’ta komplikasyonlar ve Akıllı Gruplama. İkisinde de Bugün ekranı, çerçeve ya da taçla sayılan tesbih ve Kıble; hepsi telefonunuzun kendi namaz vakitleriyle ve telefonunuzun dilinde. | yes |
| 122 | site/pages/tr/home.html :105 | Offline by design. Online only for what you choose. | Tasarımı gereği çevrimdışı. Yalnızca seçtikleriniz için çevrimiçi. | yes |
| 123 | site/pages/tr/home.html :106 | There is no server behind Taqwa and no account to create. The internet is used for recitation: fetching a surah when you download it or play one not yet downloaded, and, once you have used recitation, checking once a day for an updated list of reciters. Until then, the app itself makes no network request. If you add Taqwa to your watch, Google or Apple may carry its data between your phone and watch over the internet, encrypted so only your phone and watch can read it. | Taqwa'nın arkasında bir sunucu yok, açılacak bir hesap da yok. İnternet tilavet için kullanılır. Bir sureyi indirdiğinizde ya da henüz indirmediğiniz bir sureyi dinlediğinizde onu alır; tilaveti kullanmaya başladıktan sonra da günde bir kez güncellenmiş bir kari listesi olup olmadığını kontrol eder. Tilaveti ilk kez kullanana kadar uygulamanın kendisi hiçbir ağ isteği yapmaz. Taqwa'yı saatinize eklerseniz Google ya da Apple onun verilerini telefonunuzla saatiniz arasında internet üzerinden taşıyabilir; veriler şifrelidir, yalnızca telefonunuz ve saatiniz okuyabilir. | yes |
| 124 | site/pages/tr/home.html :110 | Stays on your devices Only your own devices can see your location, settings, bookmarks and counts. | Cihazlarınızda kalır Konumunuzu, ayarlarınızı, yer imlerinizi ve sayaçlarınızı yalnızca kendi cihazlarınız görebilir. | yes |
| 125 | site/pages/tr/home.html :111 | Online only for what you choose The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google or Apple may carry its data over the internet, encrypted so only your phone and watch can read it. | Yalnızca seçtikleriniz için çevrimiçi İnternet, Taqwa'nın GitHub'daki herkese açık deposundan indirmeyi seçtiğiniz tilavetler için kullanılır. Taqwa'yı saatinize eklerseniz Google ya da Apple onun verilerini internet üzerinden taşıyabilir; veriler şifrelidir, yalnızca telefonunuz ve saatiniz okuyabilir. | yes |
| 126 | site/pages/tr/support.html :33 | Taqwa on your watch | Saatinizde Taqwa | yes |
| 127 | site/pages/tr/support.html :34 | On a Galaxy Watch or another Wear OS watch, install Taqwa from Settings › Watch in Taqwa on the phone while the watch is nearby, or from the Play Store on the watch. On an Apple Watch, the watch app comes with the iPhone app; if it is not on the watch, install it from the Watch app on the iPhone. | Galaxy Watch’ta ya da başka bir Wear OS saatte Taqwa’yı, saat yakındayken telefondaki Taqwa’nın Ayarlar › Saat bölümünden ya da saatin Play Store’undan yükleyin. Apple Watch’ta saat uygulaması iPhone uygulamasıyla birlikte gelir; saatte yoksa iPhone’daki Apple Watch uygulamasından yükleyin. | yes |
| 128 | site/pages/tr/support.html :35 | The Taqwa face needs Wear OS 6: choose Add the Taqwa face on the watch, or touch and hold your watch face and pick Taqwa, then touch and hold it again to choose its style, colour and slots. On any face, and on an Apple Watch, Taqwa’s complications show the next prayer and its countdown. On Wear OS 3 a complication’s countdown moves only at each prayer; Today, in the watch app, always counts down exactly. | Taqwa saat yüzü Wear OS 6 gerektirir: saatte “Taqwa saat yüzünü ekle” seçeneğine dokunun ya da saat yüzünüze dokunup basılı tutun ve Taqwa’yı seçin; stilini, rengini ve yuvalarını seçmek için yeniden dokunup basılı tutun. Her saat yüzünde ve Apple Watch’ta Taqwa’nın komplikasyonları sonraki namazı ve geri sayımını gösterir. Wear OS 3’te bir komplikasyonun geri sayımı yalnızca her namazda ilerler; saat uygulamasındaki Bugün ekranı ise her zaman tam sayar. | yes |
| 129 | site/pages/tr/support.html :36 | Your phone sends the watch its prayer times a month ahead, so the watch keeps working for weeks away from it. If the watch says “Open Taqwa on your phone”, it has no times yet, or they have run out: open Taqwa on the phone with the watch nearby. The watch speaks the language you chose in Taqwa on the phone, with its digits and its clock; the watch’s own menus follow the watch’s language. | Telefonunuz namaz vakitlerini saate bir ay önceden gönderir; saat telefondan uzakta haftalarca çalışmaya devam eder. Saat “Telefonunuzda Taqwa’yı açın” diyorsa henüz vakitleri yoktur ya da vakitleri bitmiştir: saat yakındayken telefonda Taqwa’yı açın. Saat, telefondaki Taqwa’da seçtiğiniz dili, rakamlarıyla ve saat biçimiyle konuşur; saatin kendi menüleri saatin dilini izler. | yes |
| 130 | site/pages/tr/support.html :37 | The Qibla screen uses the watch’s own compass. Hold your wrist level, away from magnets, and if it asks to be calibrated, move it in a figure of eight a few times. | Kıble ekranı saatin kendi pusulasını kullanır. Saatinizi düz ve mıknatıslardan uzak tutun; kalibrasyon isterse birkaç kez havada sekiz çizin. | yes |
| 131 | site/pages/tr/support.html :43 | Everything Taqwa knows lives on your own devices: your location or chosen city, your settings, bookmarks, adhkar and tasbeeh counts, and downloaded recitations. There is no account and no server, so there is nothing to request or delete on our side. Uninstalling Taqwa from your phone and your watch removes all of it. The privacy policy says this in full. | Taqwa'nın bildiği her şey kendi cihazlarınızda kalır: konumunuz ya da seçtiğiniz şehir, ayarlarınız, yer imleriniz, zikir ve tesbih sayaçlarınız ile indirdiğiniz tilavetler. Hesap da sunucu da olmadığı için bizim tarafımızda istenecek ya da silinecek bir şey yoktur. Taqwa'yı telefonunuzdan ve saatinizden kaldırmak bunların hepsini siler. Gizlilik politikası bunu bütünüyle anlatır. | yes |
| 132 | site/pages/tr/meta.json :28 | "description": "What Taqwa stores on your devices, what it uses the internet for, and what it never does." | "description": "Taqwa'nın cihazlarınızda ne sakladığı, interneti ne için kullandığı ve asla yapmadıkları." | no |
| 133 | docs/store/release-notes/1.6.0-100042-wear.txt :10 <tr-TR> | The Taqwa face in four styles, complications for any face, a Tile, and Today, Tasbeeh and Qibla. Needs Taqwa on your phone, which sends the prayer times. | Dört stilde Taqwa saat yüzü, her saat yüzüne komplikasyonlar, bir kutucuk; Bugün, Tesbih ve Kıble ekranları. Namaz vakitlerini gönderen Taqwa’nın telefonunuzda olması gerekir. | listed here |
| 134 | docs/store/release-notes/1.6.0-42-appstore.txt :18 (Turkish) | What's new in 1.6.0: / • Taqwa on your Apple Watch: the next prayer in your complications, tasbeeh with the Digital Crown, and Qibla | 1.6.0 sürümündeki yenilikler: / • Taqwa Apple Watch’unuzda: komplikasyonlarınızda sonraki namaz, Digital Crown ile tesbih ve Kıble | yes |
| 135 | docs/store/release-notes/1.6.0-42.txt :13 <tr-TR> | What's new in 1.6.0: / • Taqwa on your watch: a watch face, complications and a Tile, tasbeeh and Qibla, on Wear OS watches such as Galaxy Watch | 1.6.0 sürümündeki yenilikler: / • Taqwa saatinizde: saat yüzü, komplikasyonlar ve kutucuk, tesbih ve Kıble; Galaxy Watch gibi Wear OS saatlerde | listed here |

### Indonesian: 143 rows

| # | Where | English | Draft | Marker |
|---|---|---|---|---|
| 1 | compose/values-id/strings.xml onboarding_location_body (:19) | Prayer times depend on where you are. Everything is calculated on your device. Only your own devices can see your location. | Waktu salat bergantung pada posisi Anda. Semuanya dihitung di perangkat Anda. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda. | yes |
| 2 | compose/values-id/strings.xml settings_add_to_apple_watch (:124) | Add to Apple Watch | Tambahkan ke Apple Watch | yes |
| 3 | compose/values-id/strings.xml settings_add_to_apple_watch_failed (:126) | The face could not be added. Try again from the Watch app. | Wajah jam tidak dapat ditambahkan. Coba lagi dari app Apple Watch. | yes |
| 4 | compose/values-id/strings.xml settings_group_watch (:133) | WATCH | JAM TANGAN | yes |
| 5 | compose/values-id/strings.xml settings_watch_install (:135) | Install on your watch | Pasang di jam tangan Anda | yes |
| 6 | compose/values-id/strings.xml settings_watch_face (:137) | Add the Taqwa face | Tambahkan tampilan jam Taqwa | yes |
| 7 | compose/values-id/strings.xml settings_watch_continue (:139) | Continue on your watch | Lanjutkan di jam tangan Anda | yes |
| 8 | compose/values-id/strings.xml settings_watch_unreachable (:141) | Couldn’t reach your watch | Tidak dapat terhubung ke jam tangan Anda | yes |
| 9 | compose/values-id/strings.xml about_stays_title (:156) | Stays on your devices | Tetap di perangkat Anda | yes |
| 10 | compose/values-id/strings.xml about_stays_body (:158) | Only your own devices can see your location, settings, bookmarks and counts. | Hanya perangkat Anda sendiri yang dapat melihat lokasi, pengaturan, penanda, dan hitungan Anda. | yes |
| 11 | compose/values-id/strings.xml about_online_title (:160) | Online only for what you choose | Online hanya untuk yang Anda pilih | yes |
| 12 | compose/values-id/strings.xml about_online_body_android (:162) | The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted so only your phone and watch can read it. | Internet dipakai untuk tilawah yang Anda pilih untuk diunduh, dari repositori publik Taqwa di GitHub. Jika Anda menambahkan Taqwa ke jam tangan Anda, Google dapat membawa datanya lewat internet, terenkripsi sehingga hanya ponsel dan jam tangan Anda yang dapat membacanya. | yes |
| 13 | compose/values-id/strings.xml about_online_body_ios (:164) | The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted so only your phone and watch can read it. | Internet dipakai untuk tilawah yang Anda pilih untuk diunduh, dari repositori publik Taqwa di GitHub. Jika Anda menambahkan Taqwa ke jam tangan Anda, Apple dapat membawa datanya lewat internet, terenkripsi sehingga hanya ponsel dan jam tangan Anda yang dapat membacanya. | yes |
| 14 | compose/values-id/strings.xml location_privacy_note (:254) | Coordinates are stored on your device and used only to calculate times. Only your own devices can see your location. | Koordinat disimpan di perangkat Anda dan hanya dipakai untuk menghitung waktu salat. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda. | yes |
| 15 | wearApp/res/values-in/strings.xml app_name (:4) | Taqwa | Taqwa | no |
| 16 | wearApp/res/values-in/strings.xml complication_next_prayer (:5) | Next prayer | Salat berikutnya | no |
| 17 | wearApp/res/values-in/strings.xml complication_mark (:6) | Taqwa | Taqwa | no |
| 18 | wearApp/res/values-in/strings.xml complication_mark_description (:7) | Taqwa | Taqwa | no |
| 19 | wearApp/res/values-in/strings.xml tile_label (:8) | Prayer times | Waktu salat | no |
| 20 | wearApp/res/values-in/strings.xml open_taqwa_on_phone (:10) | Open Taqwa on your phone | Buka Taqwa di ponsel Anda | yes |
| 21 | wearApp/res/values-in/strings.xml update_taqwa_on_watch (:11) | Update Taqwa on your watch | Perbarui Taqwa di jam tangan Anda | yes |
| 22 | wearApp/res/values-in/strings.xml complication_day (:12) | Prayer day | Hari salat | yes |
| 23 | wearApp/res/values-in/strings.xml complication_day_dial (:13) | Taqwa face: day dial | Tampilan jam Taqwa: dial hari | yes |
| 24 | wearApp/res/values-in/strings.xml complication_dates (:14) | Dates | Tanggal | yes |
| 25 | wearApp/res/values-in/strings.xml complication_day_title (:15) | Day | Hari | yes |
| 26 | wearApp/res/values-in/strings.xml complication_day_description (:16) | Prayer times today | Waktu salat hari ini | yes |
| 27 | wearApp/res/values-in/strings.xml preview_prayer_label (:17) | ASR | ASHAR | yes |
| 28 | wearApp/res/values-in/strings.xml preview_prayer_name (:18) | Asr | Ashar | yes |
| 29 | wearApp/res/values-in/strings.xml preview_prayer_time (:19) | 16:10 | 16:10 | yes |
| 30 | wearApp/res/values-in/strings.xml preview_countdown (:20) | in 1 h 12 min | 1 j 12 mnt lagi | yes |
| 31 | wearApp/res/values-in/strings.xml preview_day_times (:21) | Fajr 4:33, Sunrise 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24 | Subuh 4:33, Terbit 6:01, Dzuhur 12:33, Ashar 16:10, Maghrib 19:04, Isya 20:24 | yes |
| 32 | wearApp/res/values-in/strings.xml preview_hijri (:22) | 6 Jumada al-Ula 1448 | 6 Jumadilawal 1448 | yes |
| 33 | wearApp/res/values-in/strings.xml preview_hijri_day_month (:23) | 6 Jumada al-Ula | 6 Jumadilawal | yes |
| 34 | wearApp/res/values-in/strings.xml preview_gregorian (:24) | SUN 18 OCT | MIN 18 OKT | yes |
| 35 | wearApp/res/values-in/strings.xml preview_weekday (:25) | SUN | MIN | yes |
| 36 | wearApp/res/values-in/strings.xml preview_day_number (:26) | 18 | 18 | yes |
| 37 | wearApp/res/values-in/strings.xml tile_description (:27) | Today’s prayers and the next one counted down | Salat hari ini dan hitung mundur ke salat berikutnya | yes |
| 38 | wearApp/res/values-in/strings.xml face_add (:30) | Add the Taqwa face | Tambahkan tampilan jam Taqwa | yes |
| 39 | wearApp/res/values-in/strings.xml face_about (:32) | The next prayer and its countdown on your watch face. | Salat berikutnya dan hitung mundurnya di tampilan jam Anda. | yes |
| 40 | wearApp/res/values-in/strings.xml face_active (:34) | Taqwa is your watch face. | Taqwa adalah tampilan jam Anda. | yes |
| 41 | wearApp/res/values-in/strings.xml face_choose_it (:36) | Touch and hold your watch face, then choose Taqwa. | Sentuh lama tampilan jam Anda, lalu pilih Taqwa. | yes |
| 42 | wearApp/res/values-in/strings.xml face_failed (:38) | Couldn’t add the face. Try again later. | Tampilan jam tidak dapat ditambahkan. Coba lagi nanti. | yes |
| 43 | watchface/res/values-in/strings.xml watch_face_name (:4) | Taqwa | Taqwa | no |
| 44 | watchface/res/values-in/strings.xml slot_next_prayer (:5) | Next prayer | Salat berikutnya | no |
| 45 | watchface/res/values-in/strings.xml style (:7) | Style | Gaya | yes |
| 46 | watchface/res/values-in/strings.xml style_arch (:8) | Arch | Lengkung | yes |
| 47 | watchface/res/values-in/strings.xml style_ring (:9) | Ring | Cincin | yes |
| 48 | watchface/res/values-in/strings.xml style_sun (:10) | Sun | Matahari | yes |
| 49 | watchface/res/values-in/strings.xml style_moon (:11) | Moon | Bulan | yes |
| 50 | watchface/res/values-in/strings.xml accent (:12) | Colour | Warna | yes |
| 51 | watchface/res/values-in/strings.xml accent_amber (:13) | Amber | Ambar | yes |
| 52 | watchface/res/values-in/strings.xml accent_pearl (:14) | Pearl | Mutiara | yes |
| 53 | watchface/res/values-in/strings.xml accent_sage (:15) | Sage | Hijau sage | yes |
| 54 | watchface/res/values-in/strings.xml accent_night (:16) | Night | Malam | yes |
| 55 | watchface/res/values-in/strings.xml slot_day (:17) | Prayer day | Hari salat | yes |
| 56 | watchface/res/values-in/strings.xml slot_dates (:18) | Dates | Tanggal | yes |
| 57 | watchface/res/values-in/strings.xml slot_dates_short (:19) | Date and moon | Tanggal dan fase bulan | yes |
| 58 | watchface/res/values-in/strings.xml slot_countdown (:20) | Time to the next prayer | Waktu menuju salat berikutnya | yes |
| 59 | watchface/res/values-in/strings.xml slot_day_dial (:21) | Prayer day dial | Dial hari salat | yes |
| 60 | watchface/res/values-in/strings.xml slot_left (:22) | Left complication | Komplikasi kiri | yes |
| 61 | watchface/res/values-in/strings.xml slot_right (:23) | Right complication | Komplikasi kanan | yes |
| 62 | iosApp/TaqwaWatch/id.lproj/Localizable.strings open_taqwa_on_phone (:2) | Open Taqwa on your phone | Buka Taqwa di ponsel Anda | yes |
| 63 | iosApp/TaqwaWatch/id.lproj/Localizable.strings update_taqwa_on_watch (:5) | as row 21 | as row 21 | yes |
| 64 | iosApp/TaqwaWatch/id.lproj/InfoPlist.strings CFBundleDisplayName (:2) | Taqwa | Taqwa | no |
| 65 | iosApp/TaqwaWatchWidgets/id.lproj/Localizable.strings next_prayer_widget_name (:2) | Next prayer | Salat berikutnya | no |
| 66 | iosApp/TaqwaWatchWidgets/id.lproj/Localizable.strings next_prayer_widget_description (:5) | The next prayer, its time and the countdown. | Salat berikutnya, waktunya, dan hitung mundur. | yes |
| 67 | iosApp/TaqwaWatchWidgets/id.lproj/Localizable.strings open_taqwa_on_phone (:8) | Open Taqwa on your phone | Buka Taqwa di ponsel Anda | yes |
| 68 | iosApp/TaqwaWatchWidgets/id.lproj/Localizable.strings update_taqwa_on_watch (:11) | as row 21 | as row 21 | yes |
| 69 | iosApp/TaqwaWatchWidgets/id.lproj/InfoPlist.strings CFBundleDisplayName (:2) | Taqwa | Taqwa | no |
| 70 | iosApp/iosApp/id.lproj/InfoPlist.strings NSLocationWhenInUseUsageDescription (:4) | Taqwa uses your location to calculate prayer times on your device. Only your own devices can see your location. | Taqwa memakai lokasi Anda untuk menghitung waktu salat di perangkat Anda. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda. | yes |
| 71 | WatchWords.kt COUNTDOWN.hoursMinutes (:91) | in %1$s h %2$s min | %1$s j %2$s mnt lagi | yes |
| 72 | WatchWords.kt COUNTDOWN.hours (:92) | in %1$s h | %1$s j lagi | yes |
| 73 | WatchWords.kt COUNTDOWN.minutes (:93) | in %1$s min | %1$s mnt lagi | yes |
| 74 | WatchWords.kt COUNTDOWN.underAMinute (:94) | in under a minute | kurang dari semenit | yes |
| 75 | WatchWords.kt FACE.add (:146) | Add the Taqwa face | Tambahkan tampilan jam Taqwa | yes |
| 76 | WatchWords.kt FACE.about (:147) | as row 39 | as row 39 | yes |
| 77 | WatchWords.kt FACE.active (:148) | as row 40 | as row 40 | yes |
| 78 | WatchWords.kt FACE.chooseIt (:149) | as row 41 | as row 41 | yes |
| 79 | WatchWords.kt FACE.failed (:150) | as row 42 | as row 42 | yes |
| 80 | WatchWords.kt SCREENS.openOnPhone (:260) | Open Taqwa on your phone | Buka Taqwa di ponsel Anda | yes |
| 81 | WatchWords.kt SCREENS.updateOnWatch (:261) | as row 21 | as row 21 | yes |
| 82 | WatchWords.kt SCREENS.bezelHint (:262) | Turn the bezel or tap | Putar bezel atau ketuk | yes |
| 83 | WatchWords.kt SCREENS.crownHint (:263) | Turn the crown or tap | Putar Digital Crown atau ketuk | yes |
| 84 | WatchWords.kt SCREENS.undoAction (:264) | Take one away | Kurangi satu | yes |
| 85 | WatchWords.kt SCREENS.phrasesAction (:265) | Choose a dhikr | Pilih zikir | yes |
| 86 | WatchWords.kt SCREENS.holdLevel (:266) | Hold your wrist level | Datarkan jam tangan | yes |
| 87 | WatchWords.kt SCREENS.distanceOnDial (:267) | %1$s km | %1$s km | yes |
| 88 | WatchWords.kt SCREENS.qiblaDetailOnDial (:268) | %1$s° · %2$s km | %1$s° · %2$s km | yes |
| 89 | WatchWords.kt SCREENS.countSpoken (:269) | %1$s of %2$s | %1$s dari %2$s | yes |
| 90 | WatchWords.kt SCREENS.nextSpoken (:270) | %1$s. Next, %2$s | %1$s. Berikutnya, %2$s | yes |
| 91 | WatchWords.kt SCREENS.setCompleteSpoken (:271) | %1$s. Round complete | %1$s. Putaran selesai | yes |
| 92 | WatchWords.kt SCREENS.roundSpoken (:272) | Round complete. %1$s, round %2$s | Putaran selesai. %1$s, putaran %2$s | yes |
| 93 | WatchWords.kt SCREENS.nameRoundSpoken (:273) | %1$s, round %2$s | %1$s, putaran %2$s | yes |
| 94 | WatchWords.kt SCREENS.countAction (:274) | Count | Hitung | yes |
| 95 | WatchWords.kt SCREENS.turnRight (:275) | up to 180°: Turn right about %1$s degrees | up to 180°: Putar badan ke kanan sekitar %1$s derajat | yes |
| 96 | WatchWords.kt SCREENS.turnLeft (:276) | up to 180°: Turn left about %1$s degrees | up to 180°: Putar badan ke kiri sekitar %1$s derajat | yes |
| 97 | PRIVACY.id.md :3-3 | _Last updated 30 October 2026. Applies to Taqwa 1.0.0 and later on Android and iOS._ | _Terakhir diperbarui 30 Oktober 2026. Berlaku untuk Taqwa 1.0.0 dan versi setelahnya di Android dan iOS._ | no |
| 98 | PRIVACY.id.md :5-9 | Taqwa is a free Islamic app with no account, no ads and no analytics. It works offline. It uses the internet to download Quran recitations, and only when you ask it to. If you add Taqwa to your watch, its data may travel over the internet too, encrypted, as below. | Taqwa adalah aplikasi Islami gratis tanpa akun, tanpa iklan, dan tanpa analitik. Aplikasi ini berfungsi offline. Ia memakai internet untuk mengunduh tilawah Al-Qur'an, dan hanya ketika Anda memintanya. Jika Anda menambahkan Taqwa ke jam tangan Anda, datanya juga dapat dikirim lewat internet, secara terenkripsi, seperti dijelaskan di bawah. | yes |
| 99 | PRIVACY.id.md :11-11 | ## What stays on your devices | ## Apa yang tetap di perangkat Anda | yes |
| 100 | PRIVACY.id.md :13-14 | Everything the app knows is stored on your own devices: | Semua yang diketahui aplikasi ini tersimpan di perangkat Anda sendiri: | yes |
| 101 | PRIVACY.id.md :22-25 | None of this is sent to us or to anyone else. There is no server behind Taqwa and no account to create. Deleting Taqwa from your phone and your watch deletes all of it. | Tidak satu pun dari semua itu dikirim kepada kami atau kepada orang lain. Tidak ada server di balik Taqwa dan tidak ada akun yang perlu dibuat. Menghapus Taqwa dari ponsel dan jam tangan Anda menghapus semuanya. | yes |
| 102 | PRIVACY.id.md :31-31 | ## Your watch | ## Jam tangan Anda | yes |
| 103 | PRIVACY.id.md :33-39 | If you add Taqwa to your watch, your phone gives it your city, the Qibla's direction and distance, your area's compass correction, the coming month's prayer times as the app writes them, and your own tasbeeh phrases, with a random number made once on your phone that tells your watch which phone sent them. When your phone and watch are not directly connected, Google (Android) or Apple (iPhone) may carry this data over the internet, end-to-end encrypted so only your phone and watch can read it. | Jika Anda menambahkan Taqwa ke jam tangan Anda, ponsel Anda memberinya kota Anda, arah dan jarak kiblat, koreksi kompas di daerah Anda, waktu salat sebulan ke depan sebagaimana ditulis aplikasi, dan kalimat tasbih Anda sendiri, beserta sebuah angka acak yang dibuat sekali di ponsel Anda, yang memberi tahu jam tangan Anda ponsel mana yang mengirimnya. Saat ponsel dan jam tangan Anda tidak terhubung langsung, Google (Android) atau Apple (iPhone) dapat membawa data ini lewat internet, terenkripsi end-to-end sehingga hanya ponsel dan jam tangan Anda yang dapat membacanya. | yes |
| 104 | PRIVACY.id.md :43-46 | The app asks for your location only to compute prayer times and the Qibla direction, and only if you allow it. Picking a city from the built-in list works just as well and needs no permission. Your location is used on the device. Only your own devices can see your location. | Aplikasi ini meminta lokasi Anda hanya untuk menghitung waktu salat dan arah kiblat, dan hanya jika Anda mengizinkannya. Memilih kota dari daftar bawaan sama baiknya dan tidak memerlukan izin apa pun. Lokasi Anda dipakai di perangkat. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda. | yes |
| 105 | PRIVACY.id.md :62-67 | Once you have opened anything to do with recitation, whether the reciter list, the Recitation settings or a download, the app also checks once a day for an updated list of reciters, from the same repository. That request is the same for everyone and reveals nothing but your IP address. Until you first use recitation, the app itself makes no network request at all. | Setelah Anda membuka apa pun yang berkaitan dengan tilawah, baik daftar qari, pengaturan Tilawah, maupun sebuah unduhan, aplikasi juga memeriksa sekali sehari apakah ada daftar qari yang diperbarui, dari repositori yang sama. Permintaan itu sama untuk semua orang dan tidak mengungkapkan apa pun selain alamat IP Anda. Sampai Anda pertama kali memakai tilawah, aplikasi ini sendiri tidak melakukan permintaan jaringan sama sekali. | yes |
| 106 | PRIVACY.id.md :83-92 | Taqwa opts out of Android's app backup, so your location and settings are not copied to Google. On iOS, your settings, bookmarks, counts and downloaded recitations are excluded from iCloud backup; the only files of Taqwa's own that are backed up are the widgets' small caches, the upcoming prayer times and the hundred ayahs the daily-ayah widget rotates through, neither of which contains your location. If you add Taqwa to an Apple Watch, iOS keeps the last copy your iPhone sent it, which names your city, and that copy may be in your iPhone's backup. An iCloud backup is encrypted with keys Apple keeps, unless you turn on Advanced Data Protection, which encrypts it end to end; a backup to a computer is encrypted only if you choose to encrypt it. | Taqwa menolak ikut serta dalam pencadangan aplikasi Android, jadi lokasi dan pengaturan Anda tidak disalin ke Google. Di iOS, pengaturan, penanda, hitungan, dan tilawah yang Anda unduh dikecualikan dari cadangan iCloud; berkas milik Taqwa sendiri yang dicadangkan hanyalah cache kecil milik widget, yaitu waktu salat yang akan datang dan seratus ayat yang diputar bergiliran oleh widget ayat harian, dan keduanya tidak memuat lokasi Anda. Jika Anda menambahkan Taqwa ke Apple Watch, iOS menyimpan salinan terakhir yang dikirim iPhone Anda ke jam itu, yang menyebut kota Anda, dan salinan itu mungkin ada di cadangan iPhone Anda. Cadangan iCloud dienkripsi dengan kunci yang disimpan Apple, kecuali Anda menyalakan Perlindungan Data Lanjutan, yang mengenkripsinya secara end-to-end; cadangan di komputer hanya dienkripsi jika Anda memilih untuk mengenkripsinya. | yes |
| 107 | PRIVACY.id.md :96-102 | - No account, sign-in or profile. - No ads and no advertising identifier. - No analytics, crash reporting or usage statistics. - No third-party SDK that talks to the internet. The Android app's one Google Play services library links your phone to your own watch, and Google may relay that link, encrypted, as above. - No sale or sharing of data, because there is none to sell or share. | - Tanpa akun, tanpa proses masuk, tanpa profil. - Tanpa iklan dan tanpa pengenal iklan. - Tanpa analitik, pelaporan kerusakan, atau statistik penggunaan. - Tanpa SDK pihak ketiga yang berhubungan dengan internet. Satu-satunya pustaka layanan Google Play di aplikasi Android menghubungkan ponsel Anda dengan jam tangan Anda sendiri, dan Google dapat meneruskan sambungan itu, terenkripsi, seperti di atas. - Tanpa penjualan atau pembagian data, karena memang tidak ada yang bisa dijual atau dibagikan. | yes |
| 108 | docs/store/listing/id.md :14-19 | PRAYER TIMES • Times for where you are, from your phone's location or a city you choose from more than 34,000. • Times from the timetable the mosques near you follow. Where Taqwa holds it, it reproduces it and checks it day by day against the published tables: on every day checked, no prayer starts before the published time. Where a timetable is not yet fully checked, Taqwa adds a safety margin; where mosques follow different timetables, it shows cautious times, each prayer once all have begun it; and where no timetable is known, it calculates on the safe side. • Tap the (i) on the Prayer screen to see whose times these are, and About these times for how they are made and checked. Match my mosque looks for your mosque's timetable from two times on its board, and the usual calculation methods (Muslim World League, Egyptian, Karachi, ISNA and more) and a per-prayer adjustment are there too. • Rules for high latitudes, where the night is too short for the usual angles. • A notification for each prayer: a clear tone, a takbir, or the adhan in a choice of voices, among them Makkah and Madinah, or a recording from your phone, with an optional reminder before it, an Iqama reminder a chosen number of minutes after the adhan, and a Duha reminder at its best time. • A Jumu'ah pill on Friday's Dhuhr, home-screen widgets with today's times and the countdown to the next prayer, and the Hijri date alongside the Gregorian one. • A 24-hour or 12-hour clock, as you prefer. | WAKTU SALAT • Waktu untuk tempat Anda, dari lokasi ponsel atau kota pilihan Anda di antara lebih dari 34.000 kota. • Waktu menurut jadwal yang diikuti masjid di sekitar Anda. Jika Taqwa memiliki jadwal itu, Taqwa menghitungnya ulang dan mencocokkannya hari demi hari dengan jadwal terbitannya: pada setiap hari yang diperiksa, tidak ada salat yang dimulai sebelum waktu yang diterbitkan. Jika suatu jadwal belum diperiksa sepenuhnya, Taqwa menambahkan margin pengaman; jika masjid-masjid mengikuti jadwal yang berbeda, Taqwa menampilkan waktu ihtiyat, yaitu setiap salat setelah waktunya masuk di semua jadwal itu; dan jika tidak ada jadwal yang diketahui, Taqwa menghitung dengan memilih yang lebih aman. • Ketuk (i) di layar Salat untuk melihat asal waktu ini dan cara pemeriksaannya. “Cocokkan dengan masjid saya” mencari jadwal masjid Anda dari dua waktu di papannya, dan metode perhitungan yang biasa (Liga Muslim Dunia, Mesir, Karachi, ISNA, dan lainnya), penyesuaian per salat, serta aturan untuk lintang tinggi juga tersedia. • Notifikasi tiap salat: nada jelas, takbir, atau azan Makkah, Madinah, dan lainnya, serta pengingat opsional sebelumnya, saat iqamah (menitnya Anda atur), dan saat Dhuha. • Tanda Jumat pada Dzuhur hari Jumat, widget layar utama dengan waktu salat hari ini dan hitung mundur ke salat berikutnya, tanggal Hijriah di samping tanggal Masehi, serta format 24 atau 12 jam. | no |
| 109 | docs/store/listing/id.md :21-28 | THE QURAN • The complete Uthmani text, in the same script as the Madinah Mushaf, and the printed Mushaf's page layout if you want it. • Translations in English, Arabic tafsir, French, Turkish, Indonesian, Urdu and Bengali, shown under each ayah. • Search across the Arabic text and the translations. • Bookmarks, copy and share, and a card that takes you back to where you stopped. • Recitation by ten reciters, downloaded once per surah and played offline, with lock-screen controls; when a surah ends the next one follows. • During recitation, your phone's own voice can read the translation or tafsir aloud after each ayah, where the phone has a voice for that language. • An ayah-of-the-day widget. | AL-QUR’AN • Teks Utsmani lengkap, dengan huruf yang sama seperti Mushaf Madinah, serta, bila Anda mau, tata letak halaman Mushaf cetak. • Terjemahan bahasa Inggris, Prancis, Turki, Indonesia, Urdu, dan Bengali, serta tafsir Arab, di bawah setiap ayat. • Pencarian pada teks Arab dan terjemahannya. • Penanda, salin dan bagikan, serta kartu untuk kembali ke tempat terakhir Anda berhenti. • Tilawah oleh sepuluh qari, diunduh sekali per surah dan diputar offline, dengan kendali di layar terkunci; surah berikutnya langsung menyusul. • Selama tilawah, suara ponsel Anda dapat membacakan terjemahan atau tafsir setelah setiap ayat, jika ponsel punya suara untuk bahasa itu. • Widget ayat hari ini. | no |
| 110 | docs/store/listing/id.md :30-31 | QIBLA • A compass pointing to Makkah, with the bearing and distance, and an honest word when the phone's compass cannot be trusted. | KIBLAT • Kompas ke arah Makkah, dengan arah dan jarak, serta penjelasan jujur ketika kompas ponsel tidak bisa dipercaya. | no |
| 111 | docs/store/listing/id.md :33-35 | ADHKAR • The morning and evening adhkar in Arabic, with a translation in your language, each counted on screen. • The Quranic ones recited by your chosen reciter, and a reminder for each set if you want one. | ZIKIR • Zikir pagi dan petang dalam bahasa Arab, dengan terjemahan dalam bahasa Anda, masing-masing dihitung di layar. • Ayat Al-Qur’an di dalamnya dibacakan qari pilihan Anda, dengan pengingat bila Anda mau. | no |
| 112 | docs/store/listing/id.md :37-38 | TASBEEH • A dhikr counter with the post-prayer set built in. | TASBIH • Penghitung zikir dengan rangkaian zikir setelah salat yang sudah tersedia. | no |
| 113 | docs/store/listing/id.md :40-40 | On your wrist: on Wear OS watches such as Galaxy Watch, Taqwa shows the next prayer and its countdown on its own watch face, in complications for any face and in a Tile, counts tasbeeh with the bezel or the crown, and points to the Qibla. The times come from your phone, so your watch and your phone always agree. | Di pergelangan tangan: di jam tangan Wear OS seperti Galaxy Watch, salat berikutnya dan hitung mundurnya tampil di tampilan jam Taqwa, di komplikasi untuk tampilan jam apa pun, dan di kartu; tasbih dihitung dengan bezel atau tombol putar; kiblat pun ditunjukkan. Waktu salatnya dari ponsel Anda, jadi jam tangan dan ponsel selalu selaras. | no |
| 114 | docs/store/listing/id.md :42-46 | MADE TO BE TRUSTED • No account, no adverts, no analytics and no tracking of any kind. • Your location is used on your phone to calculate times. Only your own devices can see your location. • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted. • Free and open source under the GPL, with every source and licence credited in the app. | DIBUAT UNTUK DIPERCAYA • Tanpa analitik dan tanpa pelacakan dalam bentuk apa pun. • Lokasi Anda dipakai di ponsel Anda untuk menghitung waktu salat. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda. • Internet dipakai untuk tilawah: audio yang Anda minta, dan pemeriksaan harian daftar qari setelah Anda memakainya. Jika Anda menambahkan Taqwa ke jam tangan, Google dapat membawa datanya lewat internet, secara terenkripsi. • Gratis dan sumber terbuka (GPL), dengan setiap sumber dan lisensi disebutkan di aplikasi. | no |
| 115 | docs/store/listing/id.md :48-48 | Prayer times from Taqwa's own engine; the Qibla bearing uses the Adhan library. Quran text from the Tanzil Project. Adhkar texts from OpenDua. Recitations from the Islamic Network, licensed by the reciters for free distribution. Taqwa is not affiliated with the religious authorities whose timetables it follows. | Waktu salat dari mesin Taqwa sendiri; arah kiblat dari pustaka Adhan; teks Al-Qur’an dari Tanzil Project; teks zikir dari OpenDua; tilawah dari Islamic Network, dilisensikan para qari untuk penyebaran gratis. Taqwa tidak berafiliasi dengan otoritas keagamaan yang jadwalnya diikutinya. | no |
| 116 | docs/store/listing/id.md :50-50 | (Plan 5 Task 10's cut, which makes room for the wrist paragraph within Play's 4,000 characters, for Mohamed's approval: the website and support lines are left out, since both stores show them in their own fields, and so is the line of interface languages, which the App Store lists itself; and the Mushaf bullet says the same in fewer words.) | (Plan 5 Task 10's cut, which makes room for the wrist paragraph within Play's 4,000 characters, for Mohamed's approval: it replaces P5.9's: the line of interface languages is left out too, which the App Store lists itself, beside P5.9's own cuts (the city, Mushaf, recitation and tasbeeh bullets in fewer words, and the website and support lines left out); and, as Indonesian needs more room: the high-latitude rules join the methods bullet, the 24- or 12-hour clock the Jumu'ah bullet, the (i) bullet says in fewer words that it shows whose times these are and how they are checked, the translations, bookmarks, read-aloud, Qibla, adhkar and GPL bullets and the sources line are shorter, and the trust section's first bullet keeps only what the opening does not already say (no account, no adverts), and the city bullet (P5.9's) and the wrist paragraph are written more shortly. The wrist paragraph, the reworded lines, and the location bullet and the one after it are a draft, to native review (plan 5 Task 11).) | yes |
| 117 | docs/store/listing/id.md :84-85 | ### Description (4000) Taqwa gives you the essentials of the day in one quiet place: prayer times where you are, a takbir or the adhan at each prayer, the whole Quran to read and listen to, the morning and evening adhkar, and the Qibla. It works offline, costs nothing, shows no adverts and asks for no account. | ### Description (4000) Taqwa menghadirkan hal-hal pokok dalam hari Anda di satu tempat yang tenang: waktu salat di tempat Anda berada, takbir atau azan pada setiap salat, Al-Qur’an lengkap untuk dibaca dan didengarkan, zikir pagi dan petang, serta kiblat. Berfungsi offline, tidak berbayar, tanpa iklan, dan tidak meminta akun. | no |
| 118 | docs/store/listing/id.md :87-92 | as row 108 | as row 108 | no |
| 119 | docs/store/listing/id.md :94-101 | as row 109 | as row 109 | no |
| 120 | docs/store/listing/id.md :103-104 | as row 110 | as row 110 | no |
| 121 | docs/store/listing/id.md :106-108 | as row 111 | as row 111 | no |
| 122 | docs/store/listing/id.md :110-111 | as row 112 | as row 112 | no |
| 123 | docs/store/listing/id.md :113-113 | On your wrist: on Apple Watch, Taqwa shows the next prayer and its countdown in your watch face's complications, counts tasbeeh with the Digital Crown, and points to the Qibla. The times come from your iPhone, so your watch and your iPhone always agree. | Di pergelangan tangan: di Apple Watch, salat berikutnya dan hitung mundurnya tampil di komplikasi wajah jam Anda; tasbih dihitung dengan Digital Crown; kiblat pun ditunjukkan. Waktu salatnya dari iPhone Anda, jadi jam tangan dan iPhone selalu selaras. | no |
| 124 | docs/store/listing/id.md :115-119 | MADE TO BE TRUSTED • No account, no adverts, no analytics and no tracking of any kind. • Your location is used on your phone to calculate times. Only your own devices can see your location. • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted. • Free and open source under the GPL, with every source and licence credited in the app. | DIBUAT UNTUK DIPERCAYA • Tanpa analitik dan tanpa pelacakan dalam bentuk apa pun. • Lokasi Anda dipakai di ponsel Anda untuk menghitung waktu salat. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda. • Internet dipakai untuk tilawah: audio yang Anda minta, dan pemeriksaan harian daftar qari setelah Anda memakainya. Jika Anda menambahkan Taqwa ke jam tangan, Apple dapat membawa datanya lewat internet, secara terenkripsi. • Gratis dan sumber terbuka (GPL), dengan setiap sumber dan lisensi disebutkan di aplikasi. | no |
| 125 | docs/store/listing/id.md :121-121 | as row 115 | as row 115 | no |
| 126 | docs/store/listing/id.md :123-123 | (Its own text since 1.6.0, as each store names only its own watch (App Store Review Guideline 2.3.10): the Play full description above, with the Apple Watch paragraph in place of the Wear OS one, and Apple in place of Google in the bullet after the location one.) | (Its own text since 1.6.0, as each store names only its own watch (App Store Review Guideline 2.3.10): the Play full description above, with the Apple Watch paragraph in place of the Wear OS one, and Apple in place of Google in the bullet after the location one. A draft, to native review (plan 5 Task 11), as above.) | yes |
| 127 | docs/store/listing/id.md :144-145 | ## Review notes (App Store and Play, English only) No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. Everything works offline except recitation downloads, which fetch audio from a public GitHub release on request. To see the lock-screen player, play a surah and lock the phone. Widgets are added from the home screen's widget gallery. Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it; the privacy policy is at https://taqwa.world/privacy/. \| App Store Connect's Notes field carries the longer answers to App Review's information request of 23 September 2026 instead, brought up to date for the watch release: `docs/store/app-review/2026-10-30-notes-1.6.0-42.txt` (the 23 September reply and the earlier notes files stay as the record of what was sent then). Apple asked for them to stay there for future submissions, so update the newest file and the field together when a feature, a network use or a licence changes. The field holds 4,000 bytes. | ## Review notes (App Store and Play, English only) No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. Everything works offline except recitation downloads, which fetch audio from a public GitHub release on request. To see the lock-screen player, play a surah and lock the phone. Widgets are added from the home screen's widget gallery. Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it; the privacy policy is at https://taqwa.world/privacy/. | no |
| 128 | site/pages/id/home.html :69 | On your wrist | Di pergelangan tangan | yes |
| 129 | site/pages/id/home.html :70 | On Galaxy Watch and other Wear OS watches, the Taqwa face in four styles, complications for any face and a Tile; on Apple Watch, complications and the Smart Stack. On both, Today, tasbeeh counted with the bezel or the crown, and the Qibla, all from your phone’s own prayer times and in your phone’s language. | Di Galaxy Watch dan jam tangan Wear OS lainnya: tampilan jam Taqwa dalam empat gaya, komplikasi untuk tampilan jam apa pun, dan kartu. Di Apple Watch: komplikasi dan Smart Stack. Di keduanya: layar Hari ini, tasbih yang dihitung dengan bezel atau tombol putar, dan kiblat, semuanya dari waktu salat ponsel Anda sendiri dan dalam bahasa ponsel Anda. | yes |
| 130 | site/pages/id/home.html :105 | Offline by design. Online only for what you choose. | Offline sejak dirancang. Online hanya untuk yang Anda pilih. | yes |
| 131 | site/pages/id/home.html :106 | There is no server behind Taqwa and no account to create. The internet is used for recitation: fetching a surah when you download it or play one not yet downloaded, and, once you have used recitation, checking once a day for an updated list of reciters. Until then, the app itself makes no network request. If you add Taqwa to your watch, Google or Apple may carry its data between your phone and watch over the internet, encrypted so only your phone and watch can read it. | Tidak ada server di balik Taqwa dan tidak ada akun yang perlu dibuat. Internet dipakai untuk tilawah: mengambil surah saat Anda mengunduhnya atau memutar surah yang belum diunduh, dan, setelah Anda memakai tilawah, memeriksa sekali sehari apakah ada daftar qari yang diperbarui. Sampai Anda pertama kali memakai tilawah, aplikasi Taqwa sendiri tidak melakukan permintaan jaringan. Jika Anda menambahkan Taqwa ke jam tangan Anda, Google atau Apple dapat membawa datanya antara ponsel dan jam tangan Anda lewat internet, terenkripsi sehingga hanya ponsel dan jam tangan Anda yang dapat membacanya. | yes |
| 132 | site/pages/id/home.html :110 | Stays on your devices Only your own devices can see your location, settings, bookmarks and counts. | Tetap di perangkat Anda Hanya perangkat Anda sendiri yang dapat melihat lokasi, pengaturan, penanda, dan hitungan Anda. | yes |
| 133 | site/pages/id/home.html :111 | Online only for what you choose The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google or Apple may carry its data over the internet, encrypted so only your phone and watch can read it. | Online hanya untuk yang Anda pilih Internet dipakai untuk tilawah yang Anda pilih untuk diunduh, dari repositori publik Taqwa di GitHub. Jika Anda menambahkan Taqwa ke jam tangan Anda, Google atau Apple dapat membawa datanya lewat internet, terenkripsi sehingga hanya ponsel dan jam tangan Anda yang dapat membacanya. | yes |
| 134 | site/pages/id/support.html :33 | Taqwa on your watch | Taqwa di jam tangan Anda | yes |
| 135 | site/pages/id/support.html :34 | On a Galaxy Watch or another Wear OS watch, install Taqwa from Settings › Watch in Taqwa on the phone while the watch is nearby, or from the Play Store on the watch. On an Apple Watch, the watch app comes with the iPhone app; if it is not on the watch, install it from the Watch app on the iPhone. | Di Galaxy Watch atau jam tangan Wear OS lain, pasang Taqwa dari Pengaturan › Jam tangan di Taqwa pada ponsel saat jam tangan ada di dekatnya, atau dari Play Store di jam tangan. Di Apple Watch, aplikasi jam tangan ikut terpasang bersama aplikasi iPhone; jika belum ada di jam tangan, pasang dari app Apple Watch di iPhone. | yes |
| 136 | site/pages/id/support.html :35 | The Taqwa face needs Wear OS 6: choose Add the Taqwa face on the watch, or touch and hold your watch face and pick Taqwa, then touch and hold it again to choose its style, colour and slots. On any face, and on an Apple Watch, Taqwa’s complications show the next prayer and its countdown. On Wear OS 3 a complication’s countdown moves only at each prayer; Today, in the watch app, always counts down exactly. | Tampilan jam Taqwa memerlukan Wear OS 6: pilih “Tambahkan tampilan jam Taqwa” di jam tangan, atau sentuh lama tampilan jam Anda lalu pilih Taqwa, kemudian sentuh lama lagi untuk memilih gaya, warna, dan slotnya. Di tampilan jam apa pun, dan di Apple Watch, komplikasi Taqwa menampilkan salat berikutnya dan hitung mundurnya. Di Wear OS 3, hitung mundur pada komplikasi hanya bergerak setiap kali waktu salat masuk; layar Hari ini di aplikasi jam tangan selalu menghitung mundur dengan tepat. | yes |
| 137 | site/pages/id/support.html :36 | Your phone sends the watch its prayer times a month ahead, so the watch keeps working for weeks away from it. If the watch says “Open Taqwa on your phone”, it has no times yet, or they have run out: open Taqwa on the phone with the watch nearby. The watch speaks the language you chose in Taqwa on the phone, with its digits and its clock; the watch’s own menus follow the watch’s language. | Ponsel Anda mengirim waktu salat ke jam tangan untuk sebulan ke depan, jadi jam tangan tetap berfungsi berminggu-minggu jauh dari ponsel. Jika jam tangan menampilkan “Buka Taqwa di ponsel Anda”, berarti belum ada waktu salat, atau waktu salat yang tersimpan sudah habis: buka Taqwa di ponsel saat jam tangan ada di dekatnya. Jam tangan memakai bahasa yang Anda pilih di Taqwa pada ponsel, dengan angka dan format jamnya; menu milik jam tangan sendiri mengikuti bahasa jam tangan. | yes |
| 138 | site/pages/id/support.html :37 | The Qibla screen uses the watch’s own compass. Hold your wrist level, away from magnets, and if it asks to be calibrated, move it in a figure of eight a few times. | Layar Kiblat memakai kompas jam tangan sendiri. Jaga pergelangan tetap datar dan jauh dari magnet, dan jika diminta kalibrasi, gerakkan membentuk angka delapan beberapa kali. | yes |
| 139 | site/pages/id/support.html :43 | Everything Taqwa knows lives on your own devices: your location or chosen city, your settings, bookmarks, adhkar and tasbeeh counts, and downloaded recitations. There is no account and no server, so there is nothing to request or delete on our side. Uninstalling Taqwa from your phone and your watch removes all of it. The privacy policy says this in full. | Semua yang diketahui Taqwa ada di perangkat Anda sendiri: lokasi atau kota pilihan Anda, pengaturan, penanda, hitungan zikir dan tasbih, serta tilawah yang sudah diunduh. Tidak ada akun dan tidak ada server, jadi tidak ada apa pun yang bisa diminta atau dihapus di pihak kami. Mencopot Taqwa dari ponsel dan jam tangan Anda menghapus semuanya. Kebijakan privasi menjelaskan hal ini selengkapnya. | yes |
| 140 | site/pages/id/meta.json :28 | "description": "What Taqwa stores on your devices, what it uses the internet for, and what it never does." | "description": "Apa yang disimpan Taqwa di perangkat Anda, untuk apa ia memakai internet, dan apa yang tidak pernah dilakukannya." | no |
| 141 | docs/store/release-notes/1.6.0-100042-wear.txt :13 <id> | The Taqwa face in four styles, complications for any face, a Tile, and Today, Tasbeeh and Qibla. Needs Taqwa on your phone, which sends the prayer times. | Tampilan jam Taqwa dalam empat gaya, komplikasi untuk tampilan jam apa pun, kartu, serta layar Hari ini, Tasbih, dan Kiblat. Memerlukan Taqwa di ponsel Anda, yang mengirimkan waktu salat. | listed here |
| 142 | docs/store/release-notes/1.6.0-42-appstore.txt :23 (Indonesian) | What's new in 1.6.0: / • Taqwa on your Apple Watch: the next prayer in your complications, tasbeeh with the Digital Crown, and Qibla | Yang baru di versi 1.6.0: / • Taqwa di Apple Watch Anda: salat berikutnya di komplikasi Anda, tasbih dengan Digital Crown, dan kiblat | yes |
| 143 | docs/store/release-notes/1.6.0-42.txt :17 <id> | What's new in 1.6.0: / • Taqwa on your watch: a watch face, complications and a Tile, tasbeeh and Qibla, on Wear OS watches such as Galaxy Watch | Yang baru di versi 1.6.0: / • Taqwa di jam tangan Anda: tampilan jam, komplikasi dan kartu, tasbih dan kiblat, di jam tangan Wear OS seperti Galaxy Watch | listed here |

### Urdu: 133 rows

| # | Where | English | Draft | Marker |
|---|---|---|---|---|
| 1 | compose/values-ur/strings.xml onboarding_location_body (:19) | Prayer times depend on where you are. Everything is calculated on your device. Only your own devices can see your location. | نماز کے اوقات آپ کے مقام پر منحصر ہیں۔ سارا حساب آپ کے فون پر ہوتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ | yes |
| 2 | compose/values-ur/strings.xml settings_add_to_apple_watch (:126) | Add to Apple Watch | Apple Watch میں شامل کریں | yes |
| 3 | compose/values-ur/strings.xml settings_add_to_apple_watch_failed (:128) | The face could not be added. Try again from the Watch app. | واچ فیس شامل نہیں ہو سکا۔ Watch ایپ سے دوبارہ کوشش کریں۔ | yes |
| 4 | compose/values-ur/strings.xml settings_group_watch (:135) | WATCH | گھڑی | yes |
| 5 | compose/values-ur/strings.xml settings_watch_install (:137) | Install on your watch | اپنی گھڑی پر انسٹال کریں | yes |
| 6 | compose/values-ur/strings.xml settings_watch_face (:139) | Add the Taqwa face | تقویٰ واچ فیس شامل کریں | yes |
| 7 | compose/values-ur/strings.xml settings_watch_continue (:141) | Continue on your watch | اپنی گھڑی پر جاری رکھیں | yes |
| 8 | compose/values-ur/strings.xml settings_watch_unreachable (:143) | Couldn’t reach your watch | آپ کی گھڑی تک رسائی نہیں ہو سکی | yes |
| 9 | compose/values-ur/strings.xml about_stays_title (:158) | Stays on your devices | آپ کے آلات پر رہتا ہے | yes |
| 10 | compose/values-ur/strings.xml about_stays_body (:160) | Only your own devices can see your location, settings, bookmarks and counts. | آپ کا مقام، ترتیبات، محفوظ آیات اور گنتی صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ | yes |
| 11 | compose/values-ur/strings.xml about_online_title (:162) | Online only for what you choose | آن لائن صرف اس کے لیے جو آپ چنیں | yes |
| 12 | compose/values-ur/strings.xml about_online_body_android (:164) | The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted so only your phone and watch can read it. | انٹرنیٹ اُن تلاوتوں کے لیے استعمال ہوتا ہے جو آپ خود ڈاؤن لوڈ کرنا چاہیں، GitHub پر تقویٰ کے عوامی مخزن سے۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو Google اس کا ڈیٹا انٹرنیٹ کے ذریعے پہنچا سکتا ہے، خفیہ کاری کے ساتھ، تاکہ اسے صرف آپ کا فون اور آپ کی گھڑی پڑھ سکیں۔ | yes |
| 13 | compose/values-ur/strings.xml about_online_body_ios (:166) | The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted so only your phone and watch can read it. | انٹرنیٹ اُن تلاوتوں کے لیے استعمال ہوتا ہے جو آپ خود ڈاؤن لوڈ کرنا چاہیں، GitHub پر تقویٰ کے عوامی مخزن سے۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو Apple اس کا ڈیٹا انٹرنیٹ کے ذریعے پہنچا سکتا ہے، خفیہ کاری کے ساتھ، تاکہ اسے صرف آپ کا فون اور آپ کی گھڑی پڑھ سکیں۔ | yes |
| 14 | compose/values-ur/strings.xml location_privacy_note (:254) | Coordinates are stored on your device and used only to calculate times. Only your own devices can see your location. | آپ کے مقام کا طول و عرض آپ کے فون پر محفوظ رہتا ہے اور صرف اوقات کے حساب کے لیے استعمال ہوتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ | yes |
| 15 | wearApp/res/values-ur/strings.xml app_name (:4) | Taqwa | تقویٰ | no |
| 16 | wearApp/res/values-ur/strings.xml complication_next_prayer (:5) | Next prayer | اگلی نماز | no |
| 17 | wearApp/res/values-ur/strings.xml complication_mark (:6) | Taqwa | تقویٰ | no |
| 18 | wearApp/res/values-ur/strings.xml complication_mark_description (:7) | Taqwa | تقویٰ | no |
| 19 | wearApp/res/values-ur/strings.xml tile_label (:8) | Prayer times | نماز کے اوقات | no |
| 20 | wearApp/res/values-ur/strings.xml open_taqwa_on_phone (:10) | Open Taqwa on your phone | اپنے فون پر تقویٰ کھولیں | yes |
| 21 | wearApp/res/values-ur/strings.xml update_taqwa_on_watch (:11) | Update Taqwa on your watch | اپنی گھڑی پر تقویٰ اپڈیٹ کریں | yes |
| 22 | wearApp/res/values-ur/strings.xml complication_day (:12) | Prayer day | دن کی نمازیں | yes |
| 23 | wearApp/res/values-ur/strings.xml complication_day_dial (:13) | Taqwa face: day dial | تقویٰ ڈائل: دن کا ڈائل | yes |
| 24 | wearApp/res/values-ur/strings.xml complication_dates (:14) | Dates | تاریخیں | yes |
| 25 | wearApp/res/values-ur/strings.xml complication_day_title (:15) | Day | دن | yes |
| 26 | wearApp/res/values-ur/strings.xml complication_day_description (:16) | Prayer times today | آج کے نماز کے اوقات | yes |
| 27 | wearApp/res/values-ur/strings.xml preview_prayer_label (:17) | ASR | عصر | yes |
| 28 | wearApp/res/values-ur/strings.xml preview_prayer_name (:18) | Asr | عصر | yes |
| 29 | wearApp/res/values-ur/strings.xml preview_prayer_time (:19) | 16:10 | 16:10 | yes |
| 30 | wearApp/res/values-ur/strings.xml preview_countdown (:20) | in 1 h 12 min | 1 گھنٹے 12 منٹ میں | yes |
| 31 | wearApp/res/values-ur/strings.xml preview_day_times (:21) | Fajr 4:33, Sunrise 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24 | فجر 4:33، طلوعِ آفتاب 6:01، ظہر 12:33، عصر 16:10، مغرب 19:04، عشاء 20:24 | yes |
| 32 | wearApp/res/values-ur/strings.xml preview_hijri (:22) | 6 Jumada al-Ula 1448 | 6 جمادی الاول 1448 | yes |
| 33 | wearApp/res/values-ur/strings.xml preview_hijri_day_month (:23) | 6 Jumada al-Ula | 6 جمادی الاول | yes |
| 34 | wearApp/res/values-ur/strings.xml preview_gregorian (:24) | SUN 18 OCT | اتوار 18 اکتوبر | yes |
| 35 | wearApp/res/values-ur/strings.xml preview_weekday (:25) | SUN | اتوار | yes |
| 36 | wearApp/res/values-ur/strings.xml preview_day_number (:26) | 18 | 18 | yes |
| 37 | wearApp/res/values-ur/strings.xml tile_description (:27) | Today’s prayers and the next one counted down | آج کی نمازیں اور اگلی نماز کی الٹی گنتی | yes |
| 38 | wearApp/res/values-ur/strings.xml face_add (:30) | Add the Taqwa face | تقویٰ واچ فیس شامل کریں | yes |
| 39 | wearApp/res/values-ur/strings.xml face_about (:32) | The next prayer and its countdown on your watch face. | اگلی نماز اور اس کی الٹی گنتی آپ کے واچ فیس پر۔ | yes |
| 40 | wearApp/res/values-ur/strings.xml face_active (:34) | Taqwa is your watch face. | تقویٰ آپ کا واچ فیس ہے۔ | yes |
| 41 | wearApp/res/values-ur/strings.xml face_choose_it (:36) | Touch and hold your watch face, then choose Taqwa. | اپنے واچ فیس کو دبا کر رکھیں، پھر تقویٰ منتخب کریں۔ | yes |
| 42 | wearApp/res/values-ur/strings.xml face_failed (:38) | Couldn’t add the face. Try again later. | واچ فیس شامل نہیں ہو سکا۔ بعد میں دوبارہ کوشش کریں۔ | yes |
| 43 | watchface/res/values-ur/strings.xml watch_face_name (:4) | Taqwa | تقویٰ | no |
| 44 | watchface/res/values-ur/strings.xml slot_next_prayer (:5) | Next prayer | اگلی نماز | no |
| 45 | watchface/res/values-ur/strings.xml style (:7) | Style | انداز | yes |
| 46 | watchface/res/values-ur/strings.xml style_arch (:8) | Arch | محراب | yes |
| 47 | watchface/res/values-ur/strings.xml style_ring (:9) | Ring | حلقہ | yes |
| 48 | watchface/res/values-ur/strings.xml style_sun (:10) | Sun | سورج | yes |
| 49 | watchface/res/values-ur/strings.xml style_moon (:11) | Moon | چاند | yes |
| 50 | watchface/res/values-ur/strings.xml accent (:12) | Colour | رنگ | yes |
| 51 | watchface/res/values-ur/strings.xml accent_amber (:13) | Amber | کہربائی | yes |
| 52 | watchface/res/values-ur/strings.xml accent_pearl (:14) | Pearl | موتی | yes |
| 53 | watchface/res/values-ur/strings.xml accent_sage (:15) | Sage | ہلکا سبز | yes |
| 54 | watchface/res/values-ur/strings.xml accent_night (:16) | Night | رات | yes |
| 55 | watchface/res/values-ur/strings.xml slot_day (:17) | Prayer day | دن کی نمازیں | yes |
| 56 | watchface/res/values-ur/strings.xml slot_dates (:18) | Dates | تاریخیں | yes |
| 57 | watchface/res/values-ur/strings.xml slot_dates_short (:19) | Date and moon | تاریخ اور چاند | yes |
| 58 | watchface/res/values-ur/strings.xml slot_countdown (:20) | Time to the next prayer | اگلی نماز تک باقی وقت | yes |
| 59 | watchface/res/values-ur/strings.xml slot_day_dial (:21) | Prayer day dial | نماز کے دن کا ڈائل | yes |
| 60 | watchface/res/values-ur/strings.xml slot_left (:22) | Left complication | بائیں کمپلیکیشن | yes |
| 61 | watchface/res/values-ur/strings.xml slot_right (:23) | Right complication | دائیں کمپلیکیشن | yes |
| 62 | iosApp/TaqwaWatch/ur.lproj/Localizable.strings open_taqwa_on_phone (:2) | Open Taqwa on your phone | اپنے فون پر تقویٰ کھولیں | yes |
| 63 | iosApp/TaqwaWatch/ur.lproj/Localizable.strings update_taqwa_on_watch (:5) | Update Taqwa on your watch | اپنی گھڑی پر تقویٰ اپڈیٹ کریں | yes |
| 64 | iosApp/TaqwaWatch/ur.lproj/InfoPlist.strings CFBundleDisplayName (:2) | Taqwa | تقویٰ | no |
| 65 | iosApp/TaqwaWatchWidgets/ur.lproj/Localizable.strings next_prayer_widget_name (:2) | Next prayer | اگلی نماز | no |
| 66 | iosApp/TaqwaWatchWidgets/ur.lproj/Localizable.strings next_prayer_widget_description (:5) | The next prayer, its time and the countdown. | اگلی نماز، اس کا وقت اور الٹی گنتی۔ | yes |
| 67 | iosApp/TaqwaWatchWidgets/ur.lproj/Localizable.strings open_taqwa_on_phone (:8) | Open Taqwa on your phone | اپنے فون پر تقویٰ کھولیں | yes |
| 68 | iosApp/TaqwaWatchWidgets/ur.lproj/Localizable.strings update_taqwa_on_watch (:11) | Update Taqwa on your watch | اپنی گھڑی پر تقویٰ اپڈیٹ کریں | yes |
| 69 | iosApp/TaqwaWatchWidgets/ur.lproj/InfoPlist.strings CFBundleDisplayName (:2) | Taqwa | تقویٰ | no |
| 70 | iosApp/iosApp/ur.lproj/InfoPlist.strings NSLocationWhenInUseUsageDescription (:4) | Taqwa uses your location to calculate prayer times on your device. Only your own devices can see your location. | تقویٰ آپ کے مقام سے آپ ہی کے آلے پر نماز کے اوقات نکالتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ | yes |
| 71 | WatchWords.kt COUNTDOWN.hoursMinutes (:98) | in %1$s h %2$s min | %1$s گھنٹے %2$s منٹ میں | yes |
| 72 | WatchWords.kt COUNTDOWN.hours (:99) | in %1$s h | %1$s گھنٹے میں | yes |
| 73 | WatchWords.kt COUNTDOWN.minutes (:100) | in %1$s min | %1$s منٹ میں | yes |
| 74 | WatchWords.kt COUNTDOWN.underAMinute (:101) | in under a minute | ایک منٹ سے کم میں | yes |
| 75 | WatchWords.kt FACE.add (:154) | Add the Taqwa face | تقویٰ واچ فیس شامل کریں | yes |
| 76 | WatchWords.kt FACE.about (:155) | as row 39 | as row 39 | yes |
| 77 | WatchWords.kt FACE.active (:156) | Taqwa is your watch face. | تقویٰ آپ کا واچ فیس ہے۔ | yes |
| 78 | WatchWords.kt FACE.chooseIt (:157) | as row 41 | as row 41 | yes |
| 79 | WatchWords.kt FACE.failed (:158) | as row 42 | as row 42 | yes |
| 80 | WatchWords.kt SCREENS.openOnPhone (:280) | Open Taqwa on your phone | اپنے فون پر تقویٰ کھولیں | yes |
| 81 | WatchWords.kt SCREENS.updateOnWatch (:281) | Update Taqwa on your watch | اپنی گھڑی پر تقویٰ اپڈیٹ کریں | yes |
| 82 | WatchWords.kt SCREENS.bezelHint (:282) | Turn the bezel or tap | بیزل گھمائیں یا ٹیپ کریں | yes |
| 83 | WatchWords.kt SCREENS.crownHint (:283) | Turn the crown or tap | کراؤن گھمائیں یا ٹیپ کریں | yes |
| 84 | WatchWords.kt SCREENS.undoAction (:284) | Take one away | ایک کم کریں | yes |
| 85 | WatchWords.kt SCREENS.phrasesAction (:285) | Choose a dhikr | ذکر منتخب کریں | yes |
| 86 | WatchWords.kt SCREENS.holdLevel (:286) | Hold your wrist level | اپنی کلائی افقی رکھیں | yes |
| 87 | WatchWords.kt SCREENS.distanceOnDial (:287) | %1$s km | %1$s km | yes |
| 88 | WatchWords.kt SCREENS.qiblaDetailOnDial (:288) | %1$s° · %2$s km | %1$s° · %2$s km | yes |
| 89 | WatchWords.kt SCREENS.countSpoken (:289) | %1$s of %2$s | %2$s میں سے %1$s | yes |
| 90 | WatchWords.kt SCREENS.nextSpoken (:290) | %1$s. Next, %2$s | %1$s۔ اگلا: %2$s | yes |
| 91 | WatchWords.kt SCREENS.setCompleteSpoken (:291) | %1$s. Round complete | %1$s۔ چکر مکمل | yes |
| 92 | WatchWords.kt SCREENS.roundSpoken (:292) | Round complete. %1$s, round %2$s | چکر مکمل۔ %1$s، چکر %2$s | yes |
| 93 | WatchWords.kt SCREENS.nameRoundSpoken (:293) | %1$s, round %2$s | %1$s، چکر %2$s | yes |
| 94 | WatchWords.kt SCREENS.countAction (:294) | Count | گنیں | yes |
| 95 | WatchWords.kt SCREENS.turnRight (:295) | up to 180°: Turn right about %1$s degrees | up to 180°: تقریباً %1$s درجے دائیں مڑیں | yes |
| 96 | WatchWords.kt SCREENS.turnLeft (:296) | up to 180°: Turn left about %1$s degrees | up to 180°: تقریباً %1$s درجے بائیں مڑیں | yes |
| 97 | PRIVACY.ur.md :3-3 | _Last updated 30 October 2026. Applies to Taqwa 1.0.0 and later on Android and iOS._ | _آخری تازہ کاری: 30 اکتوبر 2026۔ اینڈرائیڈ اور iOS پر تقویٰ 1.0.0 اور اس کے بعد کے ورژنوں پر لاگو۔_ | no |
| 98 | PRIVACY.ur.md :5-9 | Taqwa is a free Islamic app with no account, no ads and no analytics. It works offline. It uses the internet to download Quran recitations, and only when you ask it to. If you add Taqwa to your watch, its data may travel over the internet too, encrypted, as below. | تقویٰ ایک مفت اسلامی ایپ ہے، جس میں نہ کوئی اکاؤنٹ ہے، نہ اشتہار اور نہ تجزیات۔ یہ آف لائن کام کرتی ہے۔ انٹرنیٹ کو یہ قرآن کی تلاوتیں ڈاؤن لوڈ کرنے کے لیے استعمال کرتی ہے، اور وہ بھی صرف اُس وقت جب آپ کہیں۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو اس کا ڈیٹا بھی انٹرنیٹ کے ذریعے، خفیہ کاری کے ساتھ، منتقل ہو سکتا ہے، جیسا کہ نیچے بیان ہے۔ | yes |
| 99 | PRIVACY.ur.md :11-11 | ## What stays on your devices | ## جو کچھ آپ کے آلات پر رہتا ہے | yes |
| 100 | PRIVACY.ur.md :13-14 | Everything the app knows is stored on your own devices: | ایپ جو کچھ جانتی ہے وہ سب آپ کے اپنے آلات پر محفوظ ہے: | yes |
| 101 | PRIVACY.ur.md :22-24 | None of this is sent to us or to anyone else. There is no server behind Taqwa and no account to create. Deleting Taqwa from your phone and your watch deletes all of it. | اس میں سے کچھ بھی ہمیں یا کسی اور کو نہیں بھیجا جاتا۔ تقویٰ کے پیچھے کوئی سرور نہیں اور نہ کوئی اکاؤنٹ بنانا ہے۔ تقویٰ کو اپنے فون اور گھڑی سے حذف کرنے سے یہ سب حذف ہو جاتا ہے۔ | yes |
| 102 | PRIVACY.ur.md :30-30 | ## Your watch | ## آپ کی گھڑی | yes |
| 103 | PRIVACY.ur.md :32-38 | If you add Taqwa to your watch, your phone gives it your city, the Qibla's direction and distance, your area's compass correction, the coming month's prayer times as the app writes them, and your own tasbeeh phrases, with a random number made once on your phone that tells your watch which phone sent them. When your phone and watch are not directly connected, Google (Android) or Apple (iPhone) may carry this data over the internet, end-to-end encrypted so only your phone and watch can read it. | اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو آپ کا فون اسے آپ کا شہر، قبلہ کی سمت اور فاصلہ، آپ کے علاقے کے لیے قطب نما کی تصحیح، آنے والے مہینے کے نماز کے اوقات جیسے ایپ انہیں لکھتی ہے، اور آپ کے اپنے تسبیح کے کلمات دیتا ہے، اور ساتھ ایک بے ترتیب نمبر بھی جو آپ کے فون پر ایک ہی بار بنتا ہے اور گھڑی کو بتاتا ہے کہ یہ سب کس فون نے بھیجا۔ جب آپ کا فون اور گھڑی براہِ راست جڑے نہ ہوں تو Google (اینڈرائیڈ) یا Apple (iPhone) یہ ڈیٹا انٹرنیٹ کے ذریعے پہنچا سکتا ہے، شروع سے آخر تک خفیہ کاری کے ساتھ، تاکہ اسے صرف آپ کا فون اور آپ کی گھڑی پڑھ سکیں۔ | yes |
| 104 | PRIVACY.ur.md :42-45 | The app asks for your location only to compute prayer times and the Qibla direction, and only if you allow it. Picking a city from the built-in list works just as well and needs no permission. Your location is used on the device. Only your own devices can see your location. | ایپ آپ کا مقام صرف نماز کے اوقات اور قبلہ کی سمت نکالنے کے لیے مانگتی ہے، اور صرف تب جب آپ اجازت دیں۔ ایپ میں شامل فہرست سے کوئی شہر چن لینا بھی اتنا ہی کام دیتا ہے اور اس کے لیے کسی اجازت کی ضرورت نہیں۔ آپ کا مقام آلے ہی پر استعمال ہوتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ | yes |
| 105 | PRIVACY.ur.md :61-65 | Once you have opened anything to do with recitation, whether the reciter list, the Recitation settings or a download, the app also checks once a day for an updated list of reciters, from the same repository. That request is the same for everyone and reveals nothing but your IP address. Until you first use recitation, the app itself makes no network request at all. | جب آپ ایک بار تلاوت سے متعلق کوئی چیز کھول لیں — قاریوں کی فہرست، تلاوت کی ترتیبات، یا کوئی ڈاؤن لوڈ — تو ایپ دن میں ایک بار اسی مخزن سے قاریوں کی تازہ فہرست بھی دیکھ لیتی ہے۔ یہ درخواست سب کے لیے ایک جیسی ہے اور آپ کے IP پتے کے سوا کچھ ظاہر نہیں کرتی۔ جب تک آپ پہلی بار تلاوت استعمال نہ کریں، ایپ خود کوئی نیٹ ورک درخواست کرتی ہی نہیں۔ | yes |
| 106 | PRIVACY.ur.md :81-90 | Taqwa opts out of Android's app backup, so your location and settings are not copied to Google. On iOS, your settings, bookmarks, counts and downloaded recitations are excluded from iCloud backup; the only files of Taqwa's own that are backed up are the widgets' small caches, the upcoming prayer times and the hundred ayahs the daily-ayah widget rotates through, neither of which contains your location. If you add Taqwa to an Apple Watch, iOS keeps the last copy your iPhone sent it, which names your city, and that copy may be in your iPhone's backup. An iCloud backup is encrypted with keys Apple keeps, unless you turn on Advanced Data Protection, which encrypts it end to end; a backup to a computer is encrypted only if you choose to encrypt it. | تقویٰ اینڈرائیڈ کے ایپ بیک اپ سے باہر رہتا ہے، اس لیے آپ کا مقام اور آپ کی ترتیبات Google کو نقل نہیں ہوتیں۔ iOS پر آپ کی ترتیبات، محفوظ آیات، گنتی اور ڈاؤن لوڈ کی ہوئی تلاوتیں iCloud بیک اپ سے خارج ہیں؛ تقویٰ کی اپنی فائلوں میں سے صرف وہ بیک اپ ہوتی ہیں جو ویجٹ کے چھوٹے ذخیرے ہیں — آنے والی نمازوں کے اوقات اور وہ سو آیات جن میں سے روزانہ کی آیت کا ویجٹ باری باری دکھاتا ہے — اور ان میں سے کسی میں آپ کا مقام نہیں ہوتا۔ اگر آپ تقویٰ کو Apple Watch میں شامل کریں تو iOS وہ آخری نقل رکھتا ہے جو آپ کے iPhone نے اسے بھیجی، جس میں آپ کے شہر کا نام ہے، اور وہ نقل آپ کے iPhone کے بیک اپ میں ہو سکتی ہے۔ iCloud کا بیک اپ ایسی خفیہ کاری سے محفوظ ہوتا ہے جس کی چابیاں Apple کے پاس رہتی ہیں، الا یہ کہ آپ Advanced Data Protection آن کریں، جس سے وہ شروع سے آخر تک خفیہ کاری سے محفوظ ہو جاتا ہے؛ کمپیوٹر پر لیا گیا بیک اپ صرف تب خفیہ کاری سے محفوظ ہوتا ہے جب آپ خود یہ چنیں۔ | yes |
| 107 | PRIVACY.ur.md :94-100 | - No account, sign-in or profile. - No ads and no advertising identifier. - No analytics, crash reporting or usage statistics. - No third-party SDK that talks to the internet. The Android app's one Google Play services library links your phone to your own watch, and Google may relay that link, encrypted, as above. - No sale or sharing of data, because there is none to sell or share. | - نہ کوئی اکاؤنٹ، نہ سائن اِن، نہ پروفائل۔ - نہ اشتہار، نہ کوئی اشتہاری شناخت کنندہ۔ - نہ تجزیات، نہ کریش رپورٹنگ، نہ استعمال کے اعداد و شمار۔ - کوئی ایسا فریقِ ثالث SDK نہیں جو انٹرنیٹ سے بات کرے۔ اینڈرائیڈ ایپ کی واحد Google Play سروسز لائبریری آپ کے فون کو آپ کی اپنی گھڑی سے جوڑتی ہے، اور Google یہ رابطہ، خفیہ کاری کے ساتھ، آگے پہنچا سکتا ہے، جیسا کہ اوپر بیان ہوا۔ - ڈیٹا کی نہ کوئی فروخت نہ اشتراک، کیونکہ بیچنے یا بانٹنے کو کچھ ہے ہی نہیں۔ | yes |
| 108 | docs/store/listing/ur.md :25-32 | THE QURAN • The complete Uthmani text, in the same script as the Madinah Mushaf, and the printed Mushaf's page layout if you want it. • Translations in English, Arabic tafsir, French, Turkish, Indonesian, Urdu and Bengali, shown under each ayah. • Search across the Arabic text and the translations. • Bookmarks, copy and share, and a card that takes you back to where you stopped. • Recitation by ten reciters, downloaded once per surah and played offline, with lock-screen controls; when a surah ends the next one follows. • During recitation, your phone's own voice can read the translation or tafsir aloud after each ayah, where the phone has a voice for that language. • An ayah-of-the-day widget. | قرآن • مکمل عثمانی متن، مصحف مدینہ ہی کے خط میں، اور جب آپ چھپے ہوئے مصحف کی طرح پڑھنا چاہیں تو روایتی صفحاتی ترتیب کے ساتھ۔ • انگریزی، فرانسیسی، ترکی، انڈونیشی، اردو اور بنگالی تراجم اور عربی تفسیر، ہر آیت کے نیچے۔ • عربی متن اور تراجم میں تلاش۔ • محفوظات، نقل اور شیئر، اور ایک کارڈ جو آپ کو وہیں واپس لے جاتا ہے جہاں آپ رکے تھے۔ • دس قاریوں کی تلاوت، ہر سورت ایک بار ڈاؤن لوڈ ہو کر آف لائن چلتی ہے، لاک اسکرین کے بٹنوں کے ساتھ؛ ایک سورت ختم ہو تو اگلی شروع ہو جاتی ہے۔ • تلاوت کے دوران آپ کے فون کی اپنی آواز ہر آیت کے بعد ترجمہ یا تفسیر پڑھ کر سنا سکتی ہے، بشرطیکہ فون میں اس زبان کی آواز موجود ہو۔ • روزانہ ایک آیت کا ویجٹ۔ | no (English changed, this language not) |
| 109 | docs/store/listing/ur.md :44-44 | On your wrist: on Wear OS watches such as Galaxy Watch, Taqwa shows the next prayer and its countdown on its own watch face, in complications for any face and in a Tile, counts tasbeeh with the bezel or the crown, and points to the Qibla. The times come from your phone, so your watch and your phone always agree. | آپ کی کلائی پر: Galaxy Watch جیسی Wear OS گھڑیوں پر تقویٰ اگلی نماز اور اس کی الٹی گنتی اپنے واچ فیس پر، کسی بھی فیس کی کمپلیکیشنز میں اور ایک ٹائل میں دکھاتا ہے، بیزل یا کراؤن گھما کر تسبیح گنتا ہے، اور قبلے کی سمت بتاتا ہے۔ اوقات آپ کے فون سے آتے ہیں، اس لیے آپ کی گھڑی اور فون ہمیشہ ایک ہی وقت بتاتے ہیں۔ | no |
| 110 | docs/store/listing/ur.md :46-50 | MADE TO BE TRUSTED • No account, no adverts, no analytics and no tracking of any kind. • Your location is used on your phone to calculate times. Only your own devices can see your location. • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted. • Free and open source under the GPL, with every source and licence credited in the app. | بھروسے کے لیے بنایا گیا • نہ اکاؤنٹ، نہ اشتہار، نہ تجزیات، نہ کسی قسم کی نگرانی۔ • آپ کا مقام آپ کے فون پر اوقات کے حساب کے لیے استعمال ہوتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ • انٹرنیٹ تلاوت کے لیے استعمال ہوتا ہے: وہ آڈیو جو آپ خود مانگیں، اور تلاوت استعمال کرنے کے بعد قاریوں کی فہرست کی روزانہ جانچ۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو Google اس کا ڈیٹا انٹرنیٹ کے ذریعے، خفیہ کاری کے ساتھ، پہنچا سکتا ہے۔ • مفت اور اوپن سورس، GPL لائسنس کے تحت، اور ہر ماخذ اور لائسنس کا اعتراف ایپ کے اندر موجود ہے۔ | no |
| 111 | docs/store/listing/ur.md :54-54 | (Plan 5 Task 10's cut, which makes room for the wrist paragraph within Play's 4,000 characters, for Mohamed's approval: the website and support lines are left out, since both stores show them in their own fields, and so is the line of interface languages, which the App Store lists itself; and the Mushaf bullet says the same in fewer words.) | (Plan 5 Task 10's cut, which makes room for the wrist paragraph within Play's 4,000 characters, for Mohamed's approval: the website and support lines are left out, since both stores show them in their own fields, and so is the line of interface languages, which the App Store lists itself. The wrist paragraph, and the location bullet and the one after it, are a draft, to native review (plan 5 Task 11).) | yes |
| 112 | docs/store/listing/ur.md :84-85 | ### Description (4000) Taqwa gives you the essentials of the day in one quiet place: prayer times where you are, a takbir or the adhan at each prayer, the whole Quran to read and listen to, the morning and evening adhkar, and the Qibla. It works offline, costs nothing, shows no adverts and asks for no account. | ### Description (4000) تقویٰ دن کی بنیادی ضرورتیں ایک پُرسکون جگہ پر جمع کر دیتا ہے: جہاں آپ ہیں وہاں نماز کے اوقات، ہر نماز پر تکبیر یا اذان، پڑھنے اور سننے کے لیے پورا قرآن، صبح و شام کے اذکار، اور قبلہ۔ یہ آف لائن چلتا ہے، اس کی کوئی قیمت نہیں، نہ اشتہار دکھاتا ہے، نہ اکاؤنٹ مانگتا ہے۔ | no |
| 113 | docs/store/listing/ur.md :96-103 | as row 108 | as row 108 | no (English changed, this language not) |
| 114 | docs/store/listing/ur.md :115-115 | On your wrist: on Apple Watch, Taqwa shows the next prayer and its countdown in your watch face's complications, counts tasbeeh with the Digital Crown, and points to the Qibla. The times come from your iPhone, so your watch and your iPhone always agree. | آپ کی کلائی پر: Apple Watch پر تقویٰ اگلی نماز اور اس کی الٹی گنتی آپ کے واچ فیس کی کمپلیکیشنز میں دکھاتا ہے، Digital Crown گھما کر تسبیح گنتا ہے، اور قبلے کی سمت بتاتا ہے۔ اوقات آپ کے iPhone سے آتے ہیں، اس لیے آپ کی گھڑی اور iPhone ہمیشہ ایک ہی وقت بتاتے ہیں۔ | no |
| 115 | docs/store/listing/ur.md :117-121 | MADE TO BE TRUSTED • No account, no adverts, no analytics and no tracking of any kind. • Your location is used on your phone to calculate times. Only your own devices can see your location. • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted. • Free and open source under the GPL, with every source and licence credited in the app. | بھروسے کے لیے بنایا گیا • نہ اکاؤنٹ، نہ اشتہار، نہ تجزیات، نہ کسی قسم کی نگرانی۔ • آپ کا مقام آپ کے فون پر اوقات کے حساب کے لیے استعمال ہوتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ • انٹرنیٹ تلاوت کے لیے استعمال ہوتا ہے: وہ آڈیو جو آپ خود مانگیں، اور تلاوت استعمال کرنے کے بعد قاریوں کی فہرست کی روزانہ جانچ۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو Apple اس کا ڈیٹا انٹرنیٹ کے ذریعے، خفیہ کاری کے ساتھ، پہنچا سکتا ہے۔ • مفت اور اوپن سورس، GPL لائسنس کے تحت، اور ہر ماخذ اور لائسنس کا اعتراف ایپ کے اندر موجود ہے۔ | no |
| 116 | docs/store/listing/ur.md :125-125 | (Its own text since 1.6.0, as each store names only its own watch (App Store Review Guideline 2.3.10): the Play full description above, with the Apple Watch paragraph in place of the Wear OS one, and Apple in place of Google in the bullet after the location one.) | (Its own text since 1.6.0, as each store names only its own watch (App Store Review Guideline 2.3.10): the Play full description above, with the Apple Watch paragraph in place of the Wear OS one, and Apple in place of Google in the bullet after the location one. A draft, to native review (plan 5 Task 11), as above.) | yes |
| 117 | docs/store/listing/ur.md :146-147 | ## Review notes (App Store and Play, English only) No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. Everything works offline except recitation downloads, which fetch audio from a public GitHub release on request. To see the lock-screen player, play a surah and lock the phone. Widgets are added from the home screen's widget gallery. Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it; the privacy policy is at https://taqwa.world/privacy/. \| App Store Connect's Notes field carries the longer answers to App Review's information request of 23 September 2026 instead, brought up to date for the watch release: `docs/store/app-review/2026-10-30-notes-1.6.0-42.txt` (the 23 September reply and the earlier notes files stay as the record of what was sent then). Apple asked for them to stay there for future submissions, so update the newest file and the field together when a feature, a network use or a licence changes. The field holds 4,000 bytes. | ## Review notes (App Store and Play, English only) No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. Everything works offline except recitation downloads, which fetch audio from a public GitHub release on request. To see the lock-screen player, play a surah and lock the phone. Widgets are added from the home screen's widget gallery. Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it; the privacy policy is at https://taqwa.world/privacy/. | no |
| 118 | site/pages/ur/home.html :69 | On your wrist | آپ کی کلائی پر | yes |
| 119 | site/pages/ur/home.html :70 | On Galaxy Watch and other Wear OS watches, the Taqwa face in four styles, complications for any face and a Tile; on Apple Watch, complications and the Smart Stack. On both, Today, tasbeeh counted with the bezel or the crown, and the Qibla, all from your phone’s own prayer times and in your phone’s language. | Galaxy Watch اور دوسری Wear OS گھڑیوں پر: چار انداز میں تقویٰ کا واچ فیس، کسی بھی فیس کے لیے کمپلیکیشنز اور ایک ٹائل؛ Apple Watch پر: کمپلیکیشنز اور Smart Stack۔ دونوں پر: ”آج“ کی اسکرین، بیزل یا کراؤن سے گنی جانے والی تسبیح، اور قبلہ، سب آپ کے فون کے اپنے اوقات سے اور آپ کے فون کی زبان میں۔ | yes |
| 120 | site/pages/ur/home.html :105 | Offline by design. Online only for what you choose. | بنیادی طور پر آف لائن۔ آن لائن صرف اس کے لیے جو آپ چنیں۔ | yes |
| 121 | site/pages/ur/home.html :106 | There is no server behind Taqwa and no account to create. The internet is used for recitation: fetching a surah when you download it or play one not yet downloaded, and, once you have used recitation, checking once a day for an updated list of reciters. Until then, the app itself makes no network request. If you add Taqwa to your watch, Google or Apple may carry its data between your phone and watch over the internet, encrypted so only your phone and watch can read it. | تقویٰ کے پیچھے کوئی سرور نہیں اور نہ کوئی اکاؤنٹ بنانا ہے۔ انٹرنیٹ تلاوت کے لیے استعمال ہوتا ہے۔ جب آپ کوئی سورۃ ڈاؤن لوڈ کریں یا کوئی ایسی سورۃ چلائیں جو ابھی ڈاؤن لوڈ نہیں ہوئی، تو وہ لائی جاتی ہے؛ اور ایک بار تلاوت استعمال کرنے کے بعد دن میں ایک بار قاریوں کی تازہ فہرست دیکھی جاتی ہے۔ جب تک آپ پہلی بار تلاوت استعمال نہ کریں، ایپ خود کوئی نیٹ ورک درخواست نہیں کرتی۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو Google یا Apple اس کا ڈیٹا آپ کے فون اور گھڑی کے درمیان انٹرنیٹ کے ذریعے پہنچا سکتا ہے، خفیہ کاری کے ساتھ، تاکہ اسے صرف آپ کا فون اور آپ کی گھڑی پڑھ سکیں۔ | yes |
| 122 | site/pages/ur/home.html :110 | Stays on your devices Only your own devices can see your location, settings, bookmarks and counts. | آپ کے آلات پر رہتا ہے آپ کا مقام، ترتیبات، محفوظ آیات اور گنتی صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ | yes |
| 123 | site/pages/ur/home.html :111 | Online only for what you choose The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google or Apple may carry its data over the internet, encrypted so only your phone and watch can read it. | آن لائن صرف اس کے لیے جو آپ چنیں انٹرنیٹ اُن تلاوتوں کے لیے استعمال ہوتا ہے جو آپ خود ڈاؤن لوڈ کرنا چاہیں، GitHub پر تقویٰ کے عوامی مخزن سے۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو Google یا Apple اس کا ڈیٹا انٹرنیٹ کے ذریعے پہنچا سکتا ہے، خفیہ کاری کے ساتھ، تاکہ اسے صرف آپ کا فون اور آپ کی گھڑی پڑھ سکیں۔ | yes |
| 124 | site/pages/ur/support.html :33 | Taqwa on your watch | آپ کی گھڑی پر تقویٰ | yes |
| 125 | site/pages/ur/support.html :34 | On a Galaxy Watch or another Wear OS watch, install Taqwa from Settings › Watch in Taqwa on the phone while the watch is nearby, or from the Play Store on the watch. On an Apple Watch, the watch app comes with the iPhone app; if it is not on the watch, install it from the Watch app on the iPhone. | Galaxy Watch یا کسی دوسری Wear OS گھڑی پر تقویٰ، گھڑی قریب ہو تو فون پر تقویٰ کی ترتیبات › گھڑی سے، یا گھڑی کے Play Store سے انسٹال کریں۔ Apple Watch پر گھڑی کی ایپ iPhone ایپ کے ساتھ آتی ہے؛ اگر گھڑی پر نہ ہو تو iPhone کی Watch ایپ سے انسٹال کریں۔ | yes |
| 126 | site/pages/ur/support.html :35 | The Taqwa face needs Wear OS 6: choose Add the Taqwa face on the watch, or touch and hold your watch face and pick Taqwa, then touch and hold it again to choose its style, colour and slots. On any face, and on an Apple Watch, Taqwa’s complications show the next prayer and its countdown. On Wear OS 3 a complication’s countdown moves only at each prayer; Today, in the watch app, always counts down exactly. | تقویٰ کے واچ فیس کے لیے Wear OS 6 ضروری ہے: گھڑی پر ”تقویٰ واچ فیس شامل کریں“ چنیں، یا اپنے واچ فیس کو دبا کر رکھیں اور تقویٰ چنیں، پھر اس کا انداز، رنگ اور خانے چننے کے لیے اسے دوبارہ دبا کر رکھیں۔ کسی بھی فیس پر، اور Apple Watch پر، تقویٰ کی کمپلیکیشنز اگلی نماز اور اس کی الٹی گنتی دکھاتی ہیں۔ Wear OS 3 پر کمپلیکیشن کی الٹی گنتی صرف ہر نماز پر آگے بڑھتی ہے؛ گھڑی کی ایپ میں ”آج“ کی اسکرین ہمیشہ ٹھیک گنتی ہے۔ | yes |
| 127 | site/pages/ur/support.html :36 | Your phone sends the watch its prayer times a month ahead, so the watch keeps working for weeks away from it. If the watch says “Open Taqwa on your phone”, it has no times yet, or they have run out: open Taqwa on the phone with the watch nearby. The watch speaks the language you chose in Taqwa on the phone, with its digits and its clock; the watch’s own menus follow the watch’s language. | آپ کا فون گھڑی کو ایک مہینہ آگے تک نماز کے اوقات بھیجتا ہے، اس لیے گھڑی فون سے دور بھی ہفتوں چلتی رہتی ہے۔ اگر گھڑی پر ”اپنے فون پر تقویٰ کھولیں“ آئے تو اس کے پاس ابھی اوقات نہیں یا ختم ہو چکے ہیں: گھڑی قریب رکھ کر فون پر تقویٰ کھولیں۔ گھڑی وہی زبان بولتی ہے جو آپ نے فون پر تقویٰ میں چنی ہے، اس کے ہندسوں اور وقت کے انداز کے ساتھ؛ گھڑی کے اپنے مینو گھڑی کی زبان میں رہتے ہیں۔ | yes |
| 128 | site/pages/ur/support.html :37 | The Qibla screen uses the watch’s own compass. Hold your wrist level, away from magnets, and if it asks to be calibrated, move it in a figure of eight a few times. | قبلہ کی اسکرین گھڑی کا اپنا قطب نما استعمال کرتی ہے۔ کلائی افقی اور مقناطیس سے دور رکھیں، اور اگر قطب نما درست کرنے کو کہے تو چند بار کلائی کو 8 کے ہندسے کی شکل میں گھمائیں۔ | yes |
| 129 | site/pages/ur/support.html :43 | Everything Taqwa knows lives on your own devices: your location or chosen city, your settings, bookmarks, adhkar and tasbeeh counts, and downloaded recitations. There is no account and no server, so there is nothing to request or delete on our side. Uninstalling Taqwa from your phone and your watch removes all of it. The privacy policy says this in full. | تقویٰ جو کچھ جانتا ہے وہ سب آپ کے اپنے آلات پر رہتا ہے: آپ کا مقام یا چنا ہوا شہر، آپ کی ترتیبات، محفوظ آیات، اذکار اور تسبیح کی گنتی، اور ڈاؤن لوڈ کی ہوئی تلاوتیں۔ نہ کوئی اکاؤنٹ ہے نہ کوئی سرور، اس لیے ہماری طرف کچھ مانگنے یا حذف کرانے کو ہے ہی نہیں۔ تقویٰ کو اپنے فون اور گھڑی سے ہٹا دینے سے یہ سب چلا جاتا ہے۔ رازداری کی پالیسی میں یہ پوری تفصیل سے لکھا ہے۔ | yes |
| 130 | site/pages/ur/meta.json :28 | "description": "What Taqwa stores on your devices, what it uses the internet for, and what it never does." | "description": "تقویٰ آپ کے آلات پر کیا محفوظ رکھتا ہے، انٹرنیٹ کو کس کام کے لیے استعمال کرتا ہے، اور کیا کبھی نہیں کرتا۔" | no |
| 131 | docs/store/release-notes/1.6.0-100042-wear.txt :16 <ur> | The Taqwa face in four styles, complications for any face, a Tile, and Today, Tasbeeh and Qibla. Needs Taqwa on your phone, which sends the prayer times. | چار انداز میں تقویٰ واچ فیس، کسی بھی فیس کے لیے کمپلیکیشنز، ایک ٹائل، اور آج، تسبیح اور قبلہ کی اسکرینیں۔ آپ کے فون پر تقویٰ ہونا ضروری ہے، جو نماز کے اوقات بھیجتا ہے۔ | listed here |
| 132 | docs/store/release-notes/1.6.0-42-appstore.txt :28 (Urdu) | What's new in 1.6.0: / • Taqwa on your Apple Watch: the next prayer in your complications, tasbeeh with the Digital Crown, and Qibla | 1.6.0 میں نیا کیا ہے: / • آپ کی Apple Watch پر تقویٰ: کمپلیکیشنز میں اگلی نماز، Digital Crown سے تسبیح، اور قبلہ | yes |
| 133 | docs/store/release-notes/1.6.0-42.txt :21 <ur> | What's new in 1.6.0: / • Taqwa on your watch: a watch face, complications and a Tile, tasbeeh and Qibla, on Wear OS watches such as Galaxy Watch | 1.6.0 میں نیا کیا ہے: / • آپ کی گھڑی پر تقویٰ: واچ فیس، کمپلیکیشنز اور ٹائل، تسبیح اور قبلہ، Galaxy Watch جیسی Wear OS گھڑیوں پر | listed here |

### Bengali: 133 rows

| # | Where | English | Draft | Marker |
|---|---|---|---|---|
| 1 | compose/values-bn/strings.xml onboarding_location_body (:19) | Prayer times depend on where you are. Everything is calculated on your device. Only your own devices can see your location. | নামাজের সময় আপনার অবস্থানের উপর নির্ভর করে। সব হিসাব আপনার ডিভাইসেই হয়। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। | yes |
| 2 | compose/values-bn/strings.xml settings_add_to_apple_watch (:126) | Add to Apple Watch | Apple Watch-এ যোগ করুন | yes |
| 3 | compose/values-bn/strings.xml settings_add_to_apple_watch_failed (:128) | The face could not be added. Try again from the Watch app. | ওয়াচ ফেস যোগ করা যায়নি। Watch অ্যাপ থেকে আবার চেষ্টা করুন। | yes |
| 4 | compose/values-bn/strings.xml settings_group_watch (:135) | WATCH | ঘড়ি | yes |
| 5 | compose/values-bn/strings.xml settings_watch_install (:137) | Install on your watch | আপনার ঘড়িতে ইনস্টল করুন | yes |
| 6 | compose/values-bn/strings.xml settings_watch_face (:139) | Add the Taqwa face | তাকওয়া ওয়াচ ফেস যোগ করুন | yes |
| 7 | compose/values-bn/strings.xml settings_watch_continue (:141) | Continue on your watch | আপনার ঘড়িতে চালিয়ে যান | yes |
| 8 | compose/values-bn/strings.xml settings_watch_unreachable (:143) | Couldn’t reach your watch | আপনার ঘড়িতে পৌঁছানো যায়নি | yes |
| 9 | compose/values-bn/strings.xml about_stays_title (:158) | Stays on your devices | আপনার ডিভাইসেই থাকে | yes |
| 10 | compose/values-bn/strings.xml about_stays_body (:160) | Only your own devices can see your location, settings, bookmarks and counts. | আপনার অবস্থান, সেটিংস, বুকমার্ক ও গণনা শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। | yes |
| 11 | compose/values-bn/strings.xml about_online_title (:162) | Online only for what you choose | শুধু আপনার বেছে নেওয়া কাজেই অনলাইন | yes |
| 12 | compose/values-bn/strings.xml about_online_body_android (:164) | The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted so only your phone and watch can read it. | ইন্টারনেট লাগে আপনি যে তিলাওয়াতগুলো ডাউনলোড করতে চান সেগুলোর জন্য, GitHub-এ তাকওয়ার উন্মুক্ত রিপোজিটরি থেকে। তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্য Google ইন্টারনেটের মাধ্যমে বহন করতে পারে, এনক্রিপ্ট করা অবস্থায়, যাতে শুধু আপনার ফোন ও ঘড়িই তা পড়তে পারে। | yes |
| 13 | compose/values-bn/strings.xml about_online_body_ios (:166) | The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted so only your phone and watch can read it. | ইন্টারনেট লাগে আপনি যে তিলাওয়াতগুলো ডাউনলোড করতে চান সেগুলোর জন্য, GitHub-এ তাকওয়ার উন্মুক্ত রিপোজিটরি থেকে। তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্য Apple ইন্টারনেটের মাধ্যমে বহন করতে পারে, এনক্রিপ্ট করা অবস্থায়, যাতে শুধু আপনার ফোন ও ঘড়িই তা পড়তে পারে। | yes |
| 14 | compose/values-bn/strings.xml location_privacy_note (:254) | Coordinates are stored on your device and used only to calculate times. Only your own devices can see your location. | স্থানাঙ্ক আপনার ডিভাইসেই জমা থাকে এবং শুধু সময় হিসাব করতে ব্যবহৃত হয়। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। | yes |
| 15 | wearApp/res/values-bn/strings.xml app_name (:4) | Taqwa | তাকওয়া | no |
| 16 | wearApp/res/values-bn/strings.xml complication_next_prayer (:5) | Next prayer | পরবর্তী নামাজ | no |
| 17 | wearApp/res/values-bn/strings.xml complication_mark (:6) | Taqwa | তাকওয়া | no |
| 18 | wearApp/res/values-bn/strings.xml complication_mark_description (:7) | Taqwa | তাকওয়া | no |
| 19 | wearApp/res/values-bn/strings.xml tile_label (:8) | Prayer times | নামাজের সময় | no |
| 20 | wearApp/res/values-bn/strings.xml open_taqwa_on_phone (:10) | Open Taqwa on your phone | আপনার ফোনে তাকওয়া খুলুন | yes |
| 21 | wearApp/res/values-bn/strings.xml update_taqwa_on_watch (:11) | Update Taqwa on your watch | আপনার ঘড়িতে তাকওয়া আপডেট করুন | yes |
| 22 | wearApp/res/values-bn/strings.xml complication_day (:12) | Prayer day | নামাজের দিন | yes |
| 23 | wearApp/res/values-bn/strings.xml complication_day_dial (:13) | Taqwa face: day dial | তাকওয়া ওয়াচ ফেস: দিনের ডায়াল | yes |
| 24 | wearApp/res/values-bn/strings.xml complication_dates (:14) | Dates | তারিখ | yes |
| 25 | wearApp/res/values-bn/strings.xml complication_day_title (:15) | Day | দিন | yes |
| 26 | wearApp/res/values-bn/strings.xml complication_day_description (:16) | Prayer times today | আজকের নামাজের সময় | yes |
| 27 | wearApp/res/values-bn/strings.xml preview_prayer_label (:17) | ASR | আসর | yes |
| 28 | wearApp/res/values-bn/strings.xml preview_prayer_name (:18) | Asr | আসর | yes |
| 29 | wearApp/res/values-bn/strings.xml preview_prayer_time (:19) | 16:10 | ১৬:১০ | yes |
| 30 | wearApp/res/values-bn/strings.xml preview_countdown (:20) | in 1 h 12 min | ১ঘঃ ১২মিঃ পরে | yes |
| 31 | wearApp/res/values-bn/strings.xml preview_day_times (:21) | Fajr 4:33, Sunrise 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24 | ফজর ৪:৩৩, সূর্যোদয় ৬:০১, যোহর ১২:৩৩, আসর ১৬:১০, মাগরিব ১৯:০৪, এশা ২০:২৪ | yes |
| 32 | wearApp/res/values-bn/strings.xml preview_hijri (:22) | 6 Jumada al-Ula 1448 | ৬ জমাদিউল আউয়াল ১৪৪৮ | yes |
| 33 | wearApp/res/values-bn/strings.xml preview_hijri_day_month (:23) | 6 Jumada al-Ula | ৬ জমাদিউল আউয়াল | yes |
| 34 | wearApp/res/values-bn/strings.xml preview_gregorian (:24) | SUN 18 OCT | রবি ১৮ অক্টো | yes |
| 35 | wearApp/res/values-bn/strings.xml preview_weekday (:25) | SUN | রবি | yes |
| 36 | wearApp/res/values-bn/strings.xml preview_day_number (:26) | 18 | ১৮ | yes |
| 37 | wearApp/res/values-bn/strings.xml tile_description (:27) | Today’s prayers and the next one counted down | আজকের নামাজ ও পরবর্তী নামাজের কাউন্টডাউন | yes |
| 38 | wearApp/res/values-bn/strings.xml face_add (:30) | Add the Taqwa face | তাকওয়া ওয়াচ ফেস যোগ করুন | yes |
| 39 | wearApp/res/values-bn/strings.xml face_about (:32) | The next prayer and its countdown on your watch face. | আপনার ওয়াচ ফেসে পরবর্তী নামাজ ও তার কাউন্টডাউন। | yes |
| 40 | wearApp/res/values-bn/strings.xml face_active (:34) | Taqwa is your watch face. | তাকওয়া আপনার ওয়াচ ফেস। | yes |
| 41 | wearApp/res/values-bn/strings.xml face_choose_it (:36) | Touch and hold your watch face, then choose Taqwa. | আপনার ওয়াচ ফেস চেপে ধরে রাখুন, তারপর তাকওয়া বেছে নিন। | yes |
| 42 | wearApp/res/values-bn/strings.xml face_failed (:38) | Couldn’t add the face. Try again later. | ওয়াচ ফেস যোগ করা যায়নি। পরে আবার চেষ্টা করুন। | yes |
| 43 | watchface/res/values-bn/strings.xml watch_face_name (:4) | Taqwa | তাকওয়া | no |
| 44 | watchface/res/values-bn/strings.xml slot_next_prayer (:5) | Next prayer | পরবর্তী নামাজ | no |
| 45 | watchface/res/values-bn/strings.xml style (:7) | Style | স্টাইল | yes |
| 46 | watchface/res/values-bn/strings.xml style_arch (:8) | Arch | খিলান | yes |
| 47 | watchface/res/values-bn/strings.xml style_ring (:9) | Ring | বলয় | yes |
| 48 | watchface/res/values-bn/strings.xml style_sun (:10) | Sun | সূর্য | yes |
| 49 | watchface/res/values-bn/strings.xml style_moon (:11) | Moon | চাঁদ | yes |
| 50 | watchface/res/values-bn/strings.xml accent (:12) | Colour | রং | yes |
| 51 | watchface/res/values-bn/strings.xml accent_amber (:13) | Amber | অ্যাম্বার | yes |
| 52 | watchface/res/values-bn/strings.xml accent_pearl (:14) | Pearl | মুক্তা | yes |
| 53 | watchface/res/values-bn/strings.xml accent_sage (:15) | Sage | সেজ | yes |
| 54 | watchface/res/values-bn/strings.xml accent_night (:16) | Night | রাত | yes |
| 55 | watchface/res/values-bn/strings.xml slot_day (:17) | Prayer day | নামাজের দিন | yes |
| 56 | watchface/res/values-bn/strings.xml slot_dates (:18) | Dates | তারিখ | yes |
| 57 | watchface/res/values-bn/strings.xml slot_dates_short (:19) | Date and moon | তারিখ ও চাঁদ | yes |
| 58 | watchface/res/values-bn/strings.xml slot_countdown (:20) | Time to the next prayer | পরবর্তী নামাজ পর্যন্ত বাকি সময় | yes |
| 59 | watchface/res/values-bn/strings.xml slot_day_dial (:21) | Prayer day dial | নামাজের দিনের ডায়াল | yes |
| 60 | watchface/res/values-bn/strings.xml slot_left (:22) | Left complication | বাঁ দিকের কমপ্লিকেশন | yes |
| 61 | watchface/res/values-bn/strings.xml slot_right (:23) | Right complication | ডান দিকের কমপ্লিকেশন | yes |
| 62 | iosApp/TaqwaWatch/bn.lproj/Localizable.strings open_taqwa_on_phone (:2) | Open Taqwa on your phone | আপনার ফোনে তাকওয়া খুলুন | yes |
| 63 | iosApp/TaqwaWatch/bn.lproj/Localizable.strings update_taqwa_on_watch (:5) | as row 21 | as row 21 | yes |
| 64 | iosApp/TaqwaWatch/bn.lproj/InfoPlist.strings CFBundleDisplayName (:2) | Taqwa | তাকওয়া | no |
| 65 | iosApp/TaqwaWatchWidgets/bn.lproj/Localizable.strings next_prayer_widget_name (:2) | Next prayer | পরবর্তী নামাজ | no |
| 66 | iosApp/TaqwaWatchWidgets/bn.lproj/Localizable.strings next_prayer_widget_description (:5) | The next prayer, its time and the countdown. | পরবর্তী নামাজ, তার সময় ও কাউন্টডাউন। | yes |
| 67 | iosApp/TaqwaWatchWidgets/bn.lproj/Localizable.strings open_taqwa_on_phone (:8) | Open Taqwa on your phone | আপনার ফোনে তাকওয়া খুলুন | yes |
| 68 | iosApp/TaqwaWatchWidgets/bn.lproj/Localizable.strings update_taqwa_on_watch (:11) | as row 21 | as row 21 | yes |
| 69 | iosApp/TaqwaWatchWidgets/bn.lproj/InfoPlist.strings CFBundleDisplayName (:2) | Taqwa | তাকওয়া | no |
| 70 | iosApp/iosApp/bn.lproj/InfoPlist.strings NSLocationWhenInUseUsageDescription (:4) | Taqwa uses your location to calculate prayer times on your device. Only your own devices can see your location. | তাকওয়া আপনার ডিভাইসেই নামাজের সময় হিসাব করতে আপনার অবস্থান ব্যবহার করে। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। | yes |
| 71 | WatchWords.kt COUNTDOWN.hoursMinutes (:105) | in %1$s h %2$s min | %1$sঘঃ %2$sমিঃ পরে | yes |
| 72 | WatchWords.kt COUNTDOWN.hours (:106) | in %1$s h | %1$sঘঃ পরে | yes |
| 73 | WatchWords.kt COUNTDOWN.minutes (:107) | in %1$s min | %1$sমিঃ পরে | yes |
| 74 | WatchWords.kt COUNTDOWN.underAMinute (:108) | in under a minute | এক মিনিটের কম সময়ে | yes |
| 75 | WatchWords.kt FACE.add (:162) | Add the Taqwa face | তাকওয়া ওয়াচ ফেস যোগ করুন | yes |
| 76 | WatchWords.kt FACE.about (:163) | as row 39 | as row 39 | yes |
| 77 | WatchWords.kt FACE.active (:164) | Taqwa is your watch face. | তাকওয়া আপনার ওয়াচ ফেস। | yes |
| 78 | WatchWords.kt FACE.chooseIt (:165) | as row 41 | as row 41 | yes |
| 79 | WatchWords.kt FACE.failed (:166) | as row 42 | as row 42 | yes |
| 80 | WatchWords.kt SCREENS.openOnPhone (:300) | Open Taqwa on your phone | আপনার ফোনে তাকওয়া খুলুন | yes |
| 81 | WatchWords.kt SCREENS.updateOnWatch (:301) | as row 21 | as row 21 | yes |
| 82 | WatchWords.kt SCREENS.bezelHint (:302) | Turn the bezel or tap | বেজেল ঘোরান বা চাপ দিন | yes |
| 83 | WatchWords.kt SCREENS.crownHint (:303) | Turn the crown or tap | ক্রাউন ঘোরান বা চাপ দিন | yes |
| 84 | WatchWords.kt SCREENS.undoAction (:304) | Take one away | একটি কমান | yes |
| 85 | WatchWords.kt SCREENS.phrasesAction (:305) | Choose a dhikr | একটি জিকির বেছে নিন | yes |
| 86 | WatchWords.kt SCREENS.holdLevel (:306) | Hold your wrist level | কব্জি সমান করে রাখুন | yes |
| 87 | WatchWords.kt SCREENS.distanceOnDial (:307) | %1$s km | %1$s km | yes |
| 88 | WatchWords.kt SCREENS.qiblaDetailOnDial (:308) | %1$s° · %2$s km | %1$s° · %2$s km | yes |
| 89 | WatchWords.kt SCREENS.countSpoken (:309) | %1$s of %2$s | %2$s-এর মধ্যে %1$s | yes |
| 90 | WatchWords.kt SCREENS.nextSpoken (:310) | %1$s. Next, %2$s | %1$s। এরপর %2$s | yes |
| 91 | WatchWords.kt SCREENS.setCompleteSpoken (:311) | %1$s. Round complete | %1$s। রাউন্ড সম্পূর্ণ | yes |
| 92 | WatchWords.kt SCREENS.roundSpoken (:312) | Round complete. %1$s, round %2$s | রাউন্ড সম্পূর্ণ। %1$s, রাউন্ড %2$s | yes |
| 93 | WatchWords.kt SCREENS.nameRoundSpoken (:313) | %1$s, round %2$s | %1$s, রাউন্ড %2$s | yes |
| 94 | WatchWords.kt SCREENS.countAction (:314) | Count | গণনা | yes |
| 95 | WatchWords.kt SCREENS.turnRight (:315) | up to 180°: Turn right about %1$s degrees | up to 180°: ডানে প্রায় %1$s ডিগ্রি ঘুরুন | yes |
| 96 | WatchWords.kt SCREENS.turnLeft (:316) | up to 180°: Turn left about %1$s degrees | up to 180°: বাঁয়ে প্রায় %1$s ডিগ্রি ঘুরুন | yes |
| 97 | PRIVACY.bn.md :3-3 | _Last updated 30 October 2026. Applies to Taqwa 1.0.0 and later on Android and iOS._ | _সর্বশেষ হালনাগাদ ৩০ অক্টোবর ২০২৬। অ্যান্ড্রয়েড ও iOS-এ তাকওয়া 1.0.0 এবং তার পরের সংস্করণের ক্ষেত্রে প্রযোজ্য।_ | no |
| 98 | PRIVACY.bn.md :5-9 | Taqwa is a free Islamic app with no account, no ads and no analytics. It works offline. It uses the internet to download Quran recitations, and only when you ask it to. If you add Taqwa to your watch, its data may travel over the internet too, encrypted, as below. | তাকওয়া একটি বিনামূল্যের ইসলামিক অ্যাপ — কোনো অ্যাকাউন্ট নেই, বিজ্ঞাপন নেই, অ্যানালিটিক্স নেই। এটি ইন্টারনেট ছাড়াই চলে। কুরআনের তিলাওয়াত ডাউনলোড করতে এটি ইন্টারনেট ব্যবহার করে, আর তা কেবল আপনি চাইলে। তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্যও ইন্টারনেটের মাধ্যমে, এনক্রিপ্ট করা অবস্থায়, যেতে পারে, যেমনটি নিচে বলা আছে। | yes |
| 99 | PRIVACY.bn.md :11-11 | ## What stays on your devices | ## আপনার ডিভাইসে যা থাকে | yes |
| 100 | PRIVACY.bn.md :13-14 | Everything the app knows is stored on your own devices: | অ্যাপটি যা কিছু জানে তার সবই আপনার নিজের ডিভাইসগুলোতে জমা থাকে: | yes |
| 101 | PRIVACY.bn.md :22-24 | None of this is sent to us or to anyone else. There is no server behind Taqwa and no account to create. Deleting Taqwa from your phone and your watch deletes all of it. | এর কিছুই আমাদের কাছে বা অন্য কারও কাছে পাঠানো হয় না। তাকওয়ার পিছনে কোনো সার্ভার নেই, খোলার মতো কোনো অ্যাকাউন্টও নেই। আপনার ফোন ও ঘড়ি থেকে তাকওয়া মুছে ফেললে এর সবই মুছে যায়। | yes |
| 102 | PRIVACY.bn.md :29-29 | ## Your watch | ## আপনার ঘড়ি | yes |
| 103 | PRIVACY.bn.md :31-37 | If you add Taqwa to your watch, your phone gives it your city, the Qibla's direction and distance, your area's compass correction, the coming month's prayer times as the app writes them, and your own tasbeeh phrases, with a random number made once on your phone that tells your watch which phone sent them. When your phone and watch are not directly connected, Google (Android) or Apple (iPhone) may carry this data over the internet, end-to-end encrypted so only your phone and watch can read it. | তাকওয়া আপনার ঘড়িতে যোগ করলে আপনার ফোন ঘড়িকে দেয় আপনার শহর, কিবলার দিক ও দূরত্ব, আপনার এলাকার কম্পাস সংশোধন, অ্যাপ যেভাবে লেখে সেভাবে সামনের এক মাসের নামাজের সময়, আর আপনার নিজের তাসবিহর বাক্যগুলো, সঙ্গে একটি এলোমেলো সংখ্যা, যা আপনার ফোনে একবারই তৈরি হয় এবং ঘড়িকে জানায় কোন ফোন এগুলো পাঠিয়েছে। আপনার ফোন ও ঘড়ি সরাসরি যুক্ত না থাকলে Google (অ্যান্ড্রয়েড) বা Apple (iPhone) এই তথ্য ইন্টারনেটের মাধ্যমে বহন করতে পারে, এন্ড-টু-এন্ড এনক্রিপ্ট করা অবস্থায়, যাতে শুধু আপনার ফোন ও ঘড়িই তা পড়তে পারে। | yes |
| 104 | PRIVACY.bn.md :41-44 | The app asks for your location only to compute prayer times and the Qibla direction, and only if you allow it. Picking a city from the built-in list works just as well and needs no permission. Your location is used on the device. Only your own devices can see your location. | অ্যাপটি আপনার অবস্থান চায় কেবল নামাজের সময় ও কিবলার দিক হিসাব করতে, আর কেবল আপনি অনুমতি দিলে। অ্যাপের ভিতরে দেওয়া তালিকা থেকে একটি শহর বেছে নিলেও ঠিক ততটাই কাজ চলে, আর তাতে কোনো অনুমতি লাগে না। আপনার অবস্থান ডিভাইসেই ব্যবহৃত হয়। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। | yes |
| 105 | PRIVACY.bn.md :60-64 | Once you have opened anything to do with recitation, whether the reciter list, the Recitation settings or a download, the app also checks once a day for an updated list of reciters, from the same repository. That request is the same for everyone and reveals nothing but your IP address. Until you first use recitation, the app itself makes no network request at all. | তিলাওয়াত-সংক্রান্ত কিছু একবার খুললে — তা কারিদের তালিকা হোক, তিলাওয়াতের সেটিংস হোক বা কোনো ডাউনলোড হোক — অ্যাপটি দিনে একবার একই রিপোজিটরি থেকে কারিদের হালনাগাদ তালিকাও দেখে নেয়। সেই অনুরোধ সবার জন্য একই এবং আপনার আইপি ঠিকানা ছাড়া আর কিছুই প্রকাশ করে না। আপনি প্রথমবার তিলাওয়াত ব্যবহার না করা পর্যন্ত অ্যাপটি নিজে কোনো নেটওয়ার্ক অনুরোধই করে না। | yes |
| 106 | PRIVACY.bn.md :80-88 | Taqwa opts out of Android's app backup, so your location and settings are not copied to Google. On iOS, your settings, bookmarks, counts and downloaded recitations are excluded from iCloud backup; the only files of Taqwa's own that are backed up are the widgets' small caches, the upcoming prayer times and the hundred ayahs the daily-ayah widget rotates through, neither of which contains your location. If you add Taqwa to an Apple Watch, iOS keeps the last copy your iPhone sent it, which names your city, and that copy may be in your iPhone's backup. An iCloud backup is encrypted with keys Apple keeps, unless you turn on Advanced Data Protection, which encrypts it end to end; a backup to a computer is encrypted only if you choose to encrypt it. | তাকওয়া অ্যান্ড্রয়েডের অ্যাপ ব্যাকআপ থেকে নিজেকে সরিয়ে রাখে, তাই আপনার অবস্থান ও সেটিংস Google-এ কপি হয় না। iOS-এ আপনার সেটিংস, বুকমার্ক, গণনা ও ডাউনলোড করা তিলাওয়াত iCloud ব্যাকআপ থেকে বাদ দেওয়া থাকে; তাকওয়ার নিজের ফাইলের মধ্যে যা ব্যাকআপ হয় তা কেবল উইজেটের ছোট ক্যাশ — সামনের নামাজের সময় আর আজকের-আয়াত উইজেট যে একশো আয়াত ঘুরিয়ে দেখায় সেগুলো — আর এর কোনোটিতেই আপনার অবস্থান থাকে না। তাকওয়া কোনো Apple Watch-এ যোগ করলে আপনার iPhone ঘড়িতে সর্বশেষ যে কপিটি পাঠিয়েছে, iOS সেটি রেখে দেয়, যাতে আপনার শহরের নাম থাকে, আর সেই কপি আপনার iPhone-এর ব্যাকআপে থাকতে পারে। iCloud ব্যাকআপ এমন চাবি দিয়ে এনক্রিপ্ট করা থাকে যা Apple রাখে, যদি না আপনি Advanced Data Protection চালু করেন, যা একে এন্ড-টু-এন্ড এনক্রিপ্ট করে; কম্পিউটারে নেওয়া ব্যাকআপ কেবল আপনি বেছে নিলেই এনক্রিপ্ট হয়। | yes |
| 107 | PRIVACY.bn.md :92-98 | - No account, sign-in or profile. - No ads and no advertising identifier. - No analytics, crash reporting or usage statistics. - No third-party SDK that talks to the internet. The Android app's one Google Play services library links your phone to your own watch, and Google may relay that link, encrypted, as above. - No sale or sharing of data, because there is none to sell or share. | - কোনো অ্যাকাউন্ট, সাইন ইন বা প্রোফাইল নেই। - কোনো বিজ্ঞাপন নেই, বিজ্ঞাপনের কোনো শনাক্তকারীও নেই। - কোনো অ্যানালিটিক্স, ক্র্যাশ রিপোর্টিং বা ব্যবহারের পরিসংখ্যান নেই। - ইন্টারনেটের সঙ্গে কথা বলে এমন কোনো তৃতীয় পক্ষের SDK নেই। অ্যান্ড্রয়েড অ্যাপের একমাত্র Google Play পরিষেবা লাইব্রেরিটি আপনার ফোনকে আপনার নিজের ঘড়ির সঙ্গে যুক্ত করে, আর Google সেই সংযোগ, এনক্রিপ্ট করা অবস্থায়, পৌঁছে দিতে পারে, যেমনটি ওপরে বলা আছে। - কোনো তথ্য বিক্রি বা ভাগ করা হয় না, কারণ বিক্রি বা ভাগ করার মতো কিছুই নেই। | yes |
| 108 | docs/store/listing/bn.md :26-33 | THE QURAN • The complete Uthmani text, in the same script as the Madinah Mushaf, and the printed Mushaf's page layout if you want it. • Translations in English, Arabic tafsir, French, Turkish, Indonesian, Urdu and Bengali, shown under each ayah. • Search across the Arabic text and the translations. • Bookmarks, copy and share, and a card that takes you back to where you stopped. • Recitation by ten reciters, downloaded once per surah and played offline, with lock-screen controls; when a surah ends the next one follows. • During recitation, your phone's own voice can read the translation or tafsir aloud after each ayah, where the phone has a voice for that language. • An ayah-of-the-day widget. | কুরআন • মদিনা মুসহাফের লিপিতে পূর্ণ উসমানি পাঠ, আর ছাপা মুসহাফের মতো পড়তে চাইলে ঐতিহ্যবাহী পৃষ্ঠাবিন্যাস। • ইংরেজি, আরবি তাফসির, ফরাসি, তুর্কি, ইন্দোনেশীয়, উর্দু ও বাংলা অনুবাদ, প্রতিটি আয়াতের নিচে। • আরবি পাঠ ও অনুবাদ জুড়ে খোঁজা। • বুকমার্ক, কপি ও শেয়ার, আর যেখানে থেমেছিলেন সেখানে ফিরিয়ে নেওয়ার একটি কার্ড। • দশজন কারির তিলাওয়াত — প্রতিটি সূরা একবার ডাউনলোড করে ইন্টারনেট ছাড়াই শোনা যায়, লক স্ক্রিন থেকে নিয়ন্ত্রণসহ; একটি সূরা শেষ হলে পরেরটি শুরু হয়। • তিলাওয়াতের সময় আপনার ফোনের নিজের কণ্ঠ প্রতিটি আয়াতের পরে অনুবাদ বা তাফসির পড়ে শোনাতে পারে, যদি ফোনে সেই ভাষার কণ্ঠ থাকে। • আজকের আয়াতের উইজেট। | no (English changed, this language not) |
| 109 | docs/store/listing/bn.md :45-45 | On your wrist: on Wear OS watches such as Galaxy Watch, Taqwa shows the next prayer and its countdown on its own watch face, in complications for any face and in a Tile, counts tasbeeh with the bezel or the crown, and points to the Qibla. The times come from your phone, so your watch and your phone always agree. | আপনার কব্জিতে: Galaxy Watch-এর মতো Wear OS ঘড়িতে তাকওয়া নিজের ওয়াচ ফেসে, যেকোনো ফেসের কমপ্লিকেশনে আর একটি টাইলে পরবর্তী নামাজ ও তার কাউন্টডাউন দেখায়, বেজেল বা ক্রাউন ঘুরিয়ে তাসবিহ গোনে, আর কিবলার দিক দেখায়। সময়গুলো আসে আপনার ফোন থেকে, তাই আপনার ঘড়ি আর ফোন সবসময় একই সময় দেখায়। | no |
| 110 | docs/store/listing/bn.md :47-51 | MADE TO BE TRUSTED • No account, no adverts, no analytics and no tracking of any kind. • Your location is used on your phone to calculate times. Only your own devices can see your location. • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted. • Free and open source under the GPL, with every source and licence credited in the app. | বিশ্বাসযোগ্য করে বানানো • অ্যাকাউন্ট নেই, বিজ্ঞাপন নেই, অ্যানালিটিক্স নেই, কোনো ধরনের ট্র্যাকিং নেই। • আপনার অবস্থান সময় হিসাব করতে আপনার ফোনেই ব্যবহৃত হয়। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। • ইন্টারনেট ব্যবহার হয় তিলাওয়াতের জন্য: আপনি যে অডিও চান, আর তিলাওয়াত একবার ব্যবহারের পর দিনে একবার কারিদের তালিকা যাচাই। তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্য Google ইন্টারনেটের মাধ্যমে, এনক্রিপ্ট করা অবস্থায়, বহন করতে পারে। • বিনামূল্যে ও GPL লাইসেন্সে ওপেন সোর্স; প্রতিটি উৎস ও লাইসেন্সের স্বীকৃতি অ্যাপের ভিতরেই দেওয়া। | no |
| 111 | docs/store/listing/bn.md :55-55 | (Plan 5 Task 10's cut, which makes room for the wrist paragraph within Play's 4,000 characters, for Mohamed's approval: the website and support lines are left out, since both stores show them in their own fields, and so is the line of interface languages, which the App Store lists itself; and the Mushaf bullet says the same in fewer words.) | (Plan 5 Task 10's cut, which makes room for the wrist paragraph within Play's 4,000 characters, for Mohamed's approval: the website and support lines are left out, since both stores show them in their own fields, and so is the line of interface languages, which the App Store lists itself. The wrist paragraph, and the location bullet and the one after it, are a draft, to native review (plan 5 Task 11).) | yes |
| 112 | docs/store/listing/bn.md :85-86 | ### Description (4000) Taqwa gives you the essentials of the day in one quiet place: prayer times where you are, a takbir or the adhan at each prayer, the whole Quran to read and listen to, the morning and evening adhkar, and the Qibla. It works offline, costs nothing, shows no adverts and asks for no account. | ### Description (4000) তাকওয়া দিনের অপরিহার্য জিনিসগুলো এক শান্ত জায়গায় এনে দেয়: আপনি যেখানে আছেন সেখানকার নামাজের সময়, প্রতি ওয়াক্তে তাকবীর বা আযান, পড়া ও শোনার জন্য পুরো কুরআন, সকাল ও সন্ধ্যার আযকার, আর কিবলা। ইন্টারনেট ছাড়াই চলে, কোনো খরচ নেই, বিজ্ঞাপন দেখায় না, অ্যাকাউন্টও চায় না। | no |
| 113 | docs/store/listing/bn.md :97-104 | as row 108 | as row 108 | no (English changed, this language not) |
| 114 | docs/store/listing/bn.md :116-116 | On your wrist: on Apple Watch, Taqwa shows the next prayer and its countdown in your watch face's complications, counts tasbeeh with the Digital Crown, and points to the Qibla. The times come from your iPhone, so your watch and your iPhone always agree. | আপনার কব্জিতে: Apple Watch-এ তাকওয়া আপনার ওয়াচ ফেসের কমপ্লিকেশনে পরবর্তী নামাজ ও তার কাউন্টডাউন দেখায়, Digital Crown ঘুরিয়ে তাসবিহ গোনে, আর কিবলার দিক দেখায়। সময়গুলো আসে আপনার iPhone থেকে, তাই আপনার ঘড়ি আর iPhone সবসময় একই সময় দেখায়। | no |
| 115 | docs/store/listing/bn.md :118-122 | MADE TO BE TRUSTED • No account, no adverts, no analytics and no tracking of any kind. • Your location is used on your phone to calculate times. Only your own devices can see your location. • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted. • Free and open source under the GPL, with every source and licence credited in the app. | বিশ্বাসযোগ্য করে বানানো • অ্যাকাউন্ট নেই, বিজ্ঞাপন নেই, অ্যানালিটিক্স নেই, কোনো ধরনের ট্র্যাকিং নেই। • আপনার অবস্থান সময় হিসাব করতে আপনার ফোনেই ব্যবহৃত হয়। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। • ইন্টারনেট ব্যবহার হয় তিলাওয়াতের জন্য: আপনি যে অডিও চান, আর তিলাওয়াত একবার ব্যবহারের পর দিনে একবার কারিদের তালিকা যাচাই। তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্য Apple ইন্টারনেটের মাধ্যমে, এনক্রিপ্ট করা অবস্থায়, বহন করতে পারে। • বিনামূল্যে ও GPL লাইসেন্সে ওপেন সোর্স; প্রতিটি উৎস ও লাইসেন্সের স্বীকৃতি অ্যাপের ভিতরেই দেওয়া। | no |
| 116 | docs/store/listing/bn.md :126-126 | (Its own text since 1.6.0, as each store names only its own watch (App Store Review Guideline 2.3.10): the Play full description above, with the Apple Watch paragraph in place of the Wear OS one, and Apple in place of Google in the bullet after the location one.) | (Its own text since 1.6.0, as each store names only its own watch (App Store Review Guideline 2.3.10): the Play full description above, with the Apple Watch paragraph in place of the Wear OS one, and Apple in place of Google in the bullet after the location one. A draft, to native review (plan 5 Task 11), as above.) | yes |
| 117 | docs/store/listing/bn.md :147-148 | ## Review notes (App Store and Play, English only) No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. Everything works offline except recitation downloads, which fetch audio from a public GitHub release on request. To see the lock-screen player, play a surah and lock the phone. Widgets are added from the home screen's widget gallery. Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it; the privacy policy is at https://taqwa.world/privacy/. \| App Store Connect's Notes field carries the longer answers to App Review's information request of 23 September 2026 instead, brought up to date for the watch release: `docs/store/app-review/2026-10-30-notes-1.6.0-42.txt` (the 23 September reply and the earlier notes files stay as the record of what was sent then). Apple asked for them to stay there for future submissions, so update the newest file and the field together when a feature, a network use or a licence changes. The field holds 4,000 bytes. | ## Review notes (App Store and Play, English only) No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. Everything works offline except recitation downloads, which fetch audio from a public GitHub release on request. To see the lock-screen player, play a surah and lock the phone. Widgets are added from the home screen's widget gallery. Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it; the privacy policy is at https://taqwa.world/privacy/. | no |
| 118 | site/pages/bn/home.html :69 | On your wrist | আপনার কব্জিতে | yes |
| 119 | site/pages/bn/home.html :70 | On Galaxy Watch and other Wear OS watches, the Taqwa face in four styles, complications for any face and a Tile; on Apple Watch, complications and the Smart Stack. On both, Today, tasbeeh counted with the bezel or the crown, and the Qibla, all from your phone’s own prayer times and in your phone’s language. | Galaxy Watch ও অন্যান্য Wear OS ঘড়িতে: চারটি স্টাইলে তাকওয়া ওয়াচ ফেস, যেকোনো ফেসের জন্য কমপ্লিকেশন, আর একটি টাইল; Apple Watch-এ: কমপ্লিকেশন ও Smart Stack। দুটিতেই: “আজ” স্ক্রিন, বেজেল বা ক্রাউন ঘুরিয়ে গোনা তাসবিহ, আর কিবলা — সবই আপনার ফোনের নিজের নামাজের সময় থেকে, আপনার ফোনের ভাষায়। | yes |
| 120 | site/pages/bn/home.html :105 | Offline by design. Online only for what you choose. | গোড়া থেকেই অফলাইন। শুধু আপনার বেছে নেওয়া কাজেই অনলাইন। | yes |
| 121 | site/pages/bn/home.html :106 | There is no server behind Taqwa and no account to create. The internet is used for recitation: fetching a surah when you download it or play one not yet downloaded, and, once you have used recitation, checking once a day for an updated list of reciters. Until then, the app itself makes no network request. If you add Taqwa to your watch, Google or Apple may carry its data between your phone and watch over the internet, encrypted so only your phone and watch can read it. | তাকওয়ার পিছনে কোনো সার্ভার নেই, খোলার মতো কোনো অ্যাকাউন্টও নেই। ইন্টারনেট ব্যবহার হয় তিলাওয়াতের জন্য: আপনি কোনো সূরা ডাউনলোড করলে বা ডাউনলোড হয়নি এমন কোনো সূরা শুনলে সেটি আনা, আর একবার তিলাওয়াত ব্যবহারের পর দিনে একবার কারিদের হালনাগাদ তালিকা দেখে নেওয়া। আপনি প্রথমবার তিলাওয়াত ব্যবহার না করা পর্যন্ত অ্যাপটি নিজে কোনো নেটওয়ার্ক অনুরোধ করে না। তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্য Google বা Apple আপনার ফোন ও ঘড়ির মধ্যে ইন্টারনেটের মাধ্যমে বহন করতে পারে, এনক্রিপ্ট করা অবস্থায়, যাতে শুধু আপনার ফোন ও ঘড়িই তা পড়তে পারে। | yes |
| 122 | site/pages/bn/home.html :110 | Stays on your devices Only your own devices can see your location, settings, bookmarks and counts. | আপনার ডিভাইসেই থাকে আপনার অবস্থান, সেটিংস, বুকমার্ক ও গণনা শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। | yes |
| 123 | site/pages/bn/home.html :111 | Online only for what you choose The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google or Apple may carry its data over the internet, encrypted so only your phone and watch can read it. | শুধু আপনার বেছে নেওয়া কাজেই অনলাইন ইন্টারনেট লাগে আপনি যে তিলাওয়াতগুলো ডাউনলোড করতে চান সেগুলোর জন্য, GitHub-এ তাকওয়ার উন্মুক্ত রিপোজিটরি থেকে। তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্য Google বা Apple ইন্টারনেটের মাধ্যমে বহন করতে পারে, এনক্রিপ্ট করা অবস্থায়, যাতে শুধু আপনার ফোন ও ঘড়িই তা পড়তে পারে। | yes |
| 124 | site/pages/bn/support.html :33 | Taqwa on your watch | আপনার ঘড়িতে তাকওয়া | yes |
| 125 | site/pages/bn/support.html :34 | On a Galaxy Watch or another Wear OS watch, install Taqwa from Settings › Watch in Taqwa on the phone while the watch is nearby, or from the Play Store on the watch. On an Apple Watch, the watch app comes with the iPhone app; if it is not on the watch, install it from the Watch app on the iPhone. | Galaxy Watch বা অন্য কোনো Wear OS ঘড়িতে, ঘড়ি কাছে থাকলে ফোনের তাকওয়ার সেটিংস › ঘড়ি থেকে, কিংবা ঘড়ির Play Store থেকে তাকওয়া ইনস্টল করুন। Apple Watch-এ ঘড়ির অ্যাপটি iPhone অ্যাপের সঙ্গেই আসে; ঘড়িতে না থাকলে iPhone-এর Watch অ্যাপ থেকে ইনস্টল করুন। | yes |
| 126 | site/pages/bn/support.html :35 | The Taqwa face needs Wear OS 6: choose Add the Taqwa face on the watch, or touch and hold your watch face and pick Taqwa, then touch and hold it again to choose its style, colour and slots. On any face, and on an Apple Watch, Taqwa’s complications show the next prayer and its countdown. On Wear OS 3 a complication’s countdown moves only at each prayer; Today, in the watch app, always counts down exactly. | তাকওয়া ওয়াচ ফেসের জন্য Wear OS 6 লাগে: ঘড়িতে “তাকওয়া ওয়াচ ফেস যোগ করুন” বেছে নিন, অথবা আপনার ওয়াচ ফেস চেপে ধরে তাকওয়া বেছে নিন, তারপর আবার চেপে ধরে এর স্টাইল, রং ও স্লট বেছে নিন। যেকোনো ফেসে, আর Apple Watch-এ, তাকওয়ার কমপ্লিকেশন পরবর্তী নামাজ ও তার কাউন্টডাউন দেখায়। Wear OS 3-এ কমপ্লিকেশনের কাউন্টডাউন শুধু প্রতি নামাজে এগোয়; ঘড়ির অ্যাপের “আজ” স্ক্রিন সবসময় ঠিকঠাক গোনে। | yes |
| 127 | site/pages/bn/support.html :36 | Your phone sends the watch its prayer times a month ahead, so the watch keeps working for weeks away from it. If the watch says “Open Taqwa on your phone”, it has no times yet, or they have run out: open Taqwa on the phone with the watch nearby. The watch speaks the language you chose in Taqwa on the phone, with its digits and its clock; the watch’s own menus follow the watch’s language. | আপনার ফোন ঘড়িকে এক মাস আগাম নামাজের সময় পাঠায়, তাই ফোন থেকে দূরে থেকেও ঘড়ি কয়েক সপ্তাহ চলতে থাকে। ঘড়িতে “আপনার ফোনে তাকওয়া খুলুন” দেখালে এর কাছে এখনো সময় নেই, বা সেগুলো ফুরিয়ে গেছে: ঘড়ি কাছে রেখে ফোনে তাকওয়া খুলুন। ঘড়ি সেই ভাষায় কথা বলে যা আপনি ফোনের তাকওয়ায় বেছে নিয়েছেন, এর অঙ্ক ও সময়ের ধরনসহ; ঘড়ির নিজের মেনুগুলো ঘড়ির ভাষা মেনে চলে। | yes |
| 128 | site/pages/bn/support.html :37 | The Qibla screen uses the watch’s own compass. Hold your wrist level, away from magnets, and if it asks to be calibrated, move it in a figure of eight a few times. | কিবলা স্ক্রিন ঘড়ির নিজের কম্পাস ব্যবহার করে। কব্জি সমান করে চুম্বক থেকে দূরে রাখুন, আর ক্যালিব্রেট করতে বললে কয়েকবার কব্জি ইংরেজি আট সংখ্যার মতো করে ঘোরান। | yes |
| 129 | site/pages/bn/support.html :43 | Everything Taqwa knows lives on your own devices: your location or chosen city, your settings, bookmarks, adhkar and tasbeeh counts, and downloaded recitations. There is no account and no server, so there is nothing to request or delete on our side. Uninstalling Taqwa from your phone and your watch removes all of it. The privacy policy says this in full. | তাকওয়া যা কিছু জানে সবই আপনার নিজের ডিভাইসগুলোতে থাকে: আপনার অবস্থান বা বেছে নেওয়া শহর, আপনার সেটিংস, বুকমার্ক, আযকার ও তাসবিহর গণনা, আর ডাউনলোড করা তিলাওয়াত। কোনো অ্যাকাউন্ট নেই, কোনো সার্ভারও নেই, তাই আমাদের কাছে চাওয়ার বা মুছতে বলার মতো কিছুই নেই। আপনার ফোন ও ঘড়ি থেকে তাকওয়া সরিয়ে ফেললে সবই মুছে যায়। গোপনীয়তা নীতি -তে এটি পুরোপুরি বলা আছে। | yes |
| 130 | site/pages/bn/meta.json :28 | "description": "What Taqwa stores on your devices, what it uses the internet for, and what it never does." | "description": "তাকওয়া আপনার ডিভাইসে কী জমা রাখে, কোন কাজে ইন্টারনেট ব্যবহার করে, আর কী কখনোই করে না।" | no |
| 131 | docs/store/release-notes/1.6.0-100042-wear.txt :19 <bn-BD> | The Taqwa face in four styles, complications for any face, a Tile, and Today, Tasbeeh and Qibla. Needs Taqwa on your phone, which sends the prayer times. | চারটি স্টাইলে তাকওয়া ওয়াচ ফেস, যেকোনো ফেসের জন্য কমপ্লিকেশন, একটি টাইল, আর “আজ”, তাসবিহ ও কিবলা স্ক্রিন। আপনার ফোনে তাকওয়া থাকা দরকার, যা নামাজের সময় পাঠায়। | listed here |
| 132 | docs/store/release-notes/1.6.0-42-appstore.txt :33 (Bengali) | What's new in 1.6.0: / • Taqwa on your Apple Watch: the next prayer in your complications, tasbeeh with the Digital Crown, and Qibla | 1.6.0 সংস্করণে যা নতুন: / • আপনার Apple Watch-এ তাকওয়া: কমপ্লিকেশনে পরবর্তী নামাজ, Digital Crown দিয়ে তাসবিহ, আর কিবলা | yes |
| 133 | docs/store/release-notes/1.6.0-42.txt :25 <bn-BD> | What's new in 1.6.0: / • Taqwa on your watch: a watch face, complications and a Tile, tasbeeh and Qibla, on Wear OS watches such as Galaxy Watch | 1.6.0 সংস্করণে যা নতুন: / • আপনার ঘড়িতে তাকওয়া: ওয়াচ ফেস, কমপ্লিকেশন ও টাইল, তাসবিহ ও কিবলা, Galaxy Watch-এর মতো Wear OS ঘড়িতে | listed here |
