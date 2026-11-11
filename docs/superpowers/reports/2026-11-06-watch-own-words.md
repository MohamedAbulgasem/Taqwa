# The watch apps' own words, in seven languages (plan 5 Task 8)

Lane W, branch `watch-wear`, base `f0c60459` (watch-companion after P5.5; `git merge --ff-only watch-companion` was already up to date). No version bump: the watch ships as 1.6.0 (42), and the files stay at main's 1.5.0 (41) until the cut.

## What the task found, and what it made

Spec 1.3's list of what the watch system shows from the watch's own resources, and where each stands:

| Words | Where | Before this task | Now |
|---|---|---|---|
| The app's name, the complication and Tile picker entries and their previews, "Open Taqwa on your phone", "Update Taqwa on your watch", Task 5's `face_*` | `wearApp/src/main/res/values-{ar,fr,tr,in,ur,bn}` | All 28 keys in all six (plan 3 Task 4, P3.8, P5.5), held by `SystemStringsTest`, but not read by `tools/i18n-check.py` | Read by `i18n-check.py` too: clean |
| The face's name, and the face editor's style, colour and slot names | `watchface/src/main/res` | English only | Six more languages, drafts marked; `FaceStringsTest` and `i18n-check.py` hold them |
| The watchOS app's name, the gallery name and description, the two first-run lines | `iosApp/TaqwaWatch`, `iosApp/TaqwaWatchWidgets` `.lproj` | Seven languages (plan 4), checked as two named targets | Checked as two of the four sets found by themselves |
| Any permission prompt | the system's | Push's `SET_PUSHED_WATCH_FACE_AS_ACTIVE` prompt is the system's own text; the watchOS app asks for none | Unchanged |

The watchOS Swift code looks up only `open_taqwa_on_phone`, `update_taqwa_on_watch` (`WatchShared/OwnWords.swift`), `next_prayer_widget_name` and `next_prayer_widget_description` (`NextPrayerWidget.swift`), and `CFBundleDisplayName`. All of them are in the tables, in all seven languages.

### The string check (`tools/i18n-check.py`, `scripts/check-strings.sh`)

- **Two new Android roots:** `wearApp/src/main/res` and `watchface/src/main/res`. They use `ANDROID_LANGS`, so Indonesian is `values-in`. They have no `ui_language`: the rule fires only where the key is, and the watch speaks the phone's language.
- **A new rule for every Android root:** a `values-id` folder fails, because Android resources spell Indonesian `in`.
- **Every `iosApp/**/<lang>.lproj` set is found by itself.** Today that is `iosApp/iosApp`, `TaqwaWidget`, `TaqwaWatch` and `TaqwaWatchWidgets`. Build output, `.xcodeproj`, `.xcworkspace` and `.xcassets` are skipped. Each set must have:
  - the seven folders and no other;
  - every English table in each folder, and no table English lacks;
  - in each table, English's keys and placeholders, with no value empty.
- **The rules kept from before:**
  - the four sets the app has today must still be found;
  - each Apple Watch target must have both of its tables;
  - its `CFBundleDisplayName` must be the iPhone's;
  - a word the phone already has must be the phone's.
- **`SAME_OK` gains** `watch_face_name`, `complication_mark`, `complication_mark_description`, `preview_prayer_time` and `preview_day_number`.
- **`tools/test_i18n_check.py` (new) is the check's own test.** It has 18 cases, each on a scratch copy of the tree's string resources, with the check copied in beside them. `scripts/check-strings.sh` runs it first, and so does CI's `strings` job. Python's standard library only.

### The face's words (`watchface/src/main/res/values-*/strings.xml`)

- **The phone's own words:** `watch_face_name` is androidApp's `app_name`, and `slot_next_prayer` is `widget_small_label`.
- **Named as the pickers name them:** the fixed slots the pickers also list, `slot_next_prayer`, `slot_day` and `slot_dates`, carry the watch app's names for those sources.
- **Drafts:** every other phrase.
- **Spec 1.3's Arabic names**, for Mohamed to read: المحراب, الحلقة, الشمس, القمر; كهرماني, لؤلؤي, ميرمية, ليلي.
- **The slot names follow the system's own word for a complication**, read from emulator-5558's `ClockworkSysUiGoogle.apk` (`add_complication`, `complications_name`): ar إضافة, fr complication, tr özellik, id detail, ur کمپلیکیشن, bn জটিলতা.

| key | en | ar | fr | tr | id | ur | bn |
|---|---|---|---|---|---|---|---|
| `watch_face_name` | Taqwa | تقوى | Taqwa | Taqwa | Taqwa | تقویٰ | তাকওয়া |
| `style` | Style | النمط | Style | Stil | Gaya | انداز | স্টাইল |
| `style_arch` | Arch | المحراب | Arche | Kemer | Lengkung | محراب | খিলান |
| `style_ring` | Ring | الحلقة | Anneau | Halka | Cincin | حلقہ | বলয় |
| `style_sun` | Sun | الشمس | Soleil | Güneş | Matahari | سورج | সূর্য |
| `style_moon` | Moon | القمر | Lune | Ay | Bulan | چاند | চাঁদ |
| `accent` | Colour | اللون | Couleur | Renk | Warna | رنگ | রং |
| `accent_amber` | Amber | كهرماني | Ambre | Kehribar | Ambar | کہربائی | অ্যাম্বার |
| `accent_pearl` | Pearl | لؤلؤي | Perle | İnci | Mutiara | موتی | মুক্তা |
| `accent_sage` | Sage | ميرمية | Sauge | Adaçayı | Hijau sage | سیج | সেজ |
| `accent_night` | Night | ليلي | Nuit | Gece | Malam | رات | রাত |
| `slot_next_prayer` | Next prayer | الصلاة القادمة | Prochaine prière | Sonraki vakit | Salat berikutnya | اگلی نماز | পরবর্তী নামাজ |
| `slot_day` | Prayer day | يوم الصلاة | Journée de prière | Namaz günü | Hari salat | نماز کا دن | নামাজের দিন |
| `slot_dates` | Dates | التواريخ | Dates | Tarihler | Tanggal | تاریخیں | তারিখ |
| `slot_dates_short` | Date and moon | التاريخ والقمر | Date et lune | Tarih ve ay evresi | Tanggal dan fase bulan | تاریخ اور چاند | তারিখ ও চাঁদ |
| `slot_countdown` | Time to the next prayer | الوقت المتبقي على الصلاة القادمة | Temps avant la prochaine prière | Sonraki vakte kalan süre | Waktu menuju salat berikutnya | اگلی نماز تک باقی وقت | পরবর্তী নামাজ পর্যন্ত বাকি সময় |
| `slot_day_dial` | Prayer day dial | قرص يوم الصلاة | Cadran de la journée de prière | Namaz günü kadranı | Dial hari salat | نماز کے دن کا ڈائل | নামাজের দিনের ডায়াল |
| `slot_left` | Left complication | الإضافة اليسرى | Complication de gauche | Sol özellik | Detail kiri | بائیں کمپلیکیشن | বাঁ দিকের জটিলতা |
| `slot_right` | Right complication | الإضافة اليمنى | Complication de droite | Sağ özellik | Detail kanan | دائیں کمپلیکیشن | ডান দিকের জটিলতা |

`FaceStringsTest` (`wearApp`, new, 7 tests) holds these rules:
- every name the face shows (its manifest's label, and every `displayName` and `screenReaderText` in `watchface.xml`) is in English, and all seven languages have English's keys;
- Indonesian is `values-in` alone;
- the phone's words are the phone's, and the fixed slots are named as the pickers name them;
- the Arabic style and colour names are spec 1.3's table;
- every other phrase sits under the draft marker;
- no straight apostrophe.

`wearApp/build.gradle.kts` makes the face's strings and its manifest inputs of the test task, and passes `taqwa.faceRes`.

## Test first

- **Red first:**
  - `python3 tools/test_i18n_check.py`: 15 of 15 failed for the missing feature: exit 0 where 1 was expected, or the named line absent. The two known-set cases, added after, failed 2 of 2. One more case, a table English lacks, was added at the end for a path no case had reached, and it passed on the check as written.
  - `FaceStringsTest`: 7 of 7 failed (`FileNotFoundException` for `values-ar`, and no `values-in`).
- **Step 1:** `scripts/check-strings.sh` FAILED, naming every missing watch translation: `watchface/src/main/res/values-{ar,bn,fr,in,tr,ur}/strings.xml: missing`, `i18n: 6 problems`. The wearApp root and the four `.lproj` sets were already clean.
- **Step 3:** `scripts/check-strings.sh` PASSED: `test_i18n_check.py: PASS` (18/0), `i18n: clean`. `FaceStringsTest` passed 7/0.
- **Mutants:** three in `FaceStringsTest`, all killed:
  - the Arabic face name as "Taqwa";
  - القمر made الهلال;
  - the Turkish draft marker removed.

## Step 4: on emulator-5558, the watch in Arabic, then Bengali

The step held `wear-5558` from 01:24 to 01:41 SAST. `phone-5562` was not needed: there was no paired step, and it stayed running and paired. `tools/watchface/push-face.sh` put this commit's face on: the watch app installed fresh, its face (41) installed by `com.google.android.wearable.dwf.receiver` and made active. The face in the slot carries the six languages (`aapt2 dump resources` of the pulled APK). The watch's language was set with `SetLocale.java` (`docs/superpowers/reports/2026-10-23-phone-feed-builder/urdu/`), as plan 2's Urdu shots did: Wear OS 6's Settings take the language from the phone.

| Seen | Arabic (ar-EG) | Bengali (bn-BD) | Shots in `2026-11-06-watch-own-words/` |
|---|---|---|---|
| The face's name in the face picker | تقوى | তাকওয়া | `ar-02-picker`, `bn-02-picker` |
| The editor's style page and its list | النمط: المحراب, الحلقة, الشمس, القمر | স্টাইল: খিলান, বলয়, সূর্য, চাঁদ | `ar-05-editor`, `ar-07`, `ar-08`, `bn-08`, `bn-09`, `bn-10` |
| The editor's colour page and its list | اللون: كهرماني, لؤلؤي, ميرمية, ليلي | রং: অ্যাম্বার, মুক্তা, সেজ, রাত | `ar-09`, `ar-10`, `ar-11`, `bn-11`, `bn-12`, `bn-13` |
| The complication chooser's Taqwa entries | التواريخ / الصلاة القادمة / تقوى, each "· تقوى" | তারিখ / পরবর্তী নামাজ / তাকওয়া, each "· তাকওয়া" | `ar-14-chooser-taqwa-4`, `-9`, `bn-25-chooser-taqwa-4`, `-8` |
| The app's name in the app list | تقوى (its description) | তাকওয়া | `ar-18-launcher`, `bn-31-launcher` |
| The Tile picker's entry | مواقيت الصلاة | নামাজের সময় | `ar-21-tile-picker-2`, `bn-34-tile-picker-2` |
| The face itself | the phone's English (THU 5 NOV, FAJR 5:22, "in 5 h 52 min") | the same | `ar-17-face`, `bn-36-face` |

- **Nothing was clipped.** The editor's accessibility text uses the face's names too, for example "خلفية شاشة الساعة المحراب من النوع النمط" and "খিলান ওয়াচফেস স্টাইল". The style, colour and slots were never changed: lists opened and closed with Back, the chooser left with Back.
- **Put back as found.** Found: app `109521c0` (100041), face 41 `f13548e6` active, Arch, Amber, slots empty, the sample feed `7e35da12`, en-US, `system_locales` null, TalkBack off, font 1.0, automatic time. The steps:
  1. en-US, then `system_locales` deleted, and the dex removed;
  2. the found APK reinstalled as a first install, and its face 41 made active by `activate.sh`;
  3. `feed.sh`'s sample stored (`7e35da12`), and the paired phone's `feed.json.delivery` removed;
  4. the app opened once, so that its face memory reads "seen 41 active" as found.

  `state.sh` then equalled the record found, but for the time. `refresh.asked` is the app's own newer ask. One BACK from the notification list had left sysui's face zoomed out (`mWallpaperZoomOut` 0.2); opening the list and closing it with a swipe down set it back to 0.0 (`put-back-face.png`).

## The other checks

- **The face's validators** (`tools/watchface/validate.sh`): `wff-validator` and `validator-push-cli` 1.1.0-alpha01 both pass, 10 of 10 checks on debug and release.
- **The face generators:** `face-arch.py`, `face-centre.py`, `face-moon.py` and `face-sun.py --check` all report ok. `watchface.xml` and every picture are unchanged, so the always-on lit share (`lit.py`) is unchanged.
- **Lint:** `:watchface:lintDebug`, `:watchface:lintRelease` and `:wearApp:lintDebug` report no issues.
- **`scripts/test.sh` whole**, under `ios-sim-tests` (01:42 to 01:45 SAST): exit 0.
  - versions agree, and `test-bump-version.sh` passes;
  - `privacy-check` is strict and clean, with 263 rows (all approved), 7 policies and 236 sweep lines;
  - the listing checks are clean, and `swift test` reports 0 failures;
  - `:wearApp:testDebugUnitTest` ran with 572 tests and 0 failures, `FaceStringsTest` among them. There was no EOFException.
  - `:shared`, `:widgetcore` and `:watchcore` were up to date from this tree's earlier green runs (host 2379 / 111 / 233, iOS 2355 / 109 / 200). This task changed none of their inputs.

## The errata and rulings this task touched

1. **`git merge --ff-only watch-companion`:** already up to date at `f0c60459`.
2. **The marker and the i18n rules:**
   - **The marker:** the drafts carry the repository's marker `<!-- draft, to native review (plan 5) -->`, which `SystemStringsTest` holds, Ruling 58 item 6 names and `git grep "draft, to native review"` finds. They do not carry Task 8's "(plan 5 Task 11)". One block marker covers each file's drafts, and the test reads each string's nearest comment.
   - **Indonesian:** `values-in` alone in Android resources, and a new check fails a `values-id` there. No Compose resource changed, so no `values-id` twin changed.
   - **Apostrophes:** none in any new phrase.
   - **The figure of eight:** no watch-own phrase names it, so the Latin 8 rule has nothing to apply to.
3. **Errata p5:** no entry names Task 8.
   - #12 (Ruling 58 item 6) is Task 11's. It should now name three draft places: `WatchWords.kt`, `wearApp`'s `values-*`, and `watchface`'s `values-*`. The iOS `.lproj` comments say "(draft, to native review (plan 5))" too.
   - #1(c), #15, #16, #18, #21 and #24 are the feed's words, reviewed in Task 11. They are not the watch's own resources.
4. **The rulings:**
   - **39:** no new phrase speaks of location, and `privacy-check.py` runs strict in `scripts/test.sh`.
   - **41, 42, 43:** no face drawing, slot order or reading order changed.
   - **55 item 6:** no test vector reaches an APK. The new test reads resource files only.
   - **The quiet amber style:** no drawn change.
5. **Version:** not bumped.
6. **WIP:** `c6482cf9` (red tests and the check) and `9cda0a6e` (the face's six languages), folded into the task's one commit.
7. **Devices:** emulator-5558 only, under its lock, put back. emulator-5562, KB2604110095, 5572 and the work iPhone 12 were not touched.

## For the Lead and Task 11

1. **Task 11's inventory:** add `watchface/src/main/res/values-*/strings.xml`, 17 drafts in each of six languages. The face's name and "Next prayer" are the phone's own words.
2. **For the reviewers of P5.5's `face_*` drafts**, the system's own words for "watch face" on this image (`carousel_accessibility_watch_face_label`):
   - fr Cadran and ur واچ فیس, which the drafts already use;
   - tr Kadran, where the drafts say "saat yüzü";
   - id Tampilan jam, where they say "wajah jam";
   - bn ওয়াচফেস, where they say "ওয়াচ ফেস";
   - ar خلفية شاشة الساعة, where they say "واجهة".
3. **Bengali `complication_dates` and `slot_dates` (তারিখ)** read the same as the system's own Date source ("তারিখ, Wear OS") in the chooser. A reviewer may prefer a plural.
4. **On Wear OS 6, the editor's slot page and the chooser show the system's words** ("إدراج إضافة", "الإضافات"), not `slot_left` or `slot_right`. Those names are still drafted, for any system or screen reader that reads them.
5. **The Tile picker's preview** is the one English picture in every language. That is by plan: P5.12 refreshes `tile_preview.png` from the English 14:58 shot. Only the entry's name is in the watch's language. Per-language previews would be a new call.
