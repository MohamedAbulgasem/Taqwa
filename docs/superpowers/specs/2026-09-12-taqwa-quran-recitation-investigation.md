# Taqwa slice 3: Quran recitation — investigation and proposal

Status: **for Mohamed's review, 12 September 2026.** Written while he was out. Everything marked *decide* in §0 needs his answer; everything else is a recommendation he can overrule. Nothing is built yet. Companion to the 7 September investigation (`2026-09-07-quran-text-audio-investigation.md`, §5 and §11), whose decisions this keeps unless stated.

## 0. The short version, and what I need from you

**What ships.** Recitation inside the reader and the Mushaf, downloaded on demand one surah at a time, with the current ayah lit and the page following the voice; seven reciters in a picker with a monogram and a fifteen-second preview each; background playback with lock-screen controls on both platforms; a slim player bar that stays out of the way of reading. The default reciter is Mishary Alafasy, as decided on 7 September.

**What it costs.** Nothing recurring. Audio lives on a public GitHub repository's Releases, served through GitHub's CDN at no charge. The app grows by under 1 MB (seven preview clips). A surah download runs from well under 1 MB (the short surahs) to about 58 MB (Al-Baqarah, Alafasy at 64 kbps); a whole reciter is 0.6 to 1.6 GB.

**Decide (answers by number are enough):**

1. **The seven reciters.** The set in §3 is my pick from the corpus we may redistribute. Swap any.
2. **Bitrate.** 64 kbps wherever the corpus publishes it (five of the seven), 128 for Minshawi and Shuraim who have no lower tier. We may not re-encode, so this is the only lever. Recommend yes.
3. **Hosting access.** Create the public repository `MohamedAbulgasem/taqwa-data` (empty is fine) and either install the GitHub CLI and sign in (`brew install gh && gh auth login`) or give me a fine-grained token with *Contents: read and write* on that one repository. Uploading ~6 GB of release assets is the one step I cannot do from here without it. Zero cost.
4. **The courtesy emails** drafted on 7 September (Islamic Network; Alafasy's foundation). Were they sent, and did anyone reply? Their terms permit us regardless (§2), so this does not block, but a reply is worth recording in the Attribution screen.
5. **No photos, monograms for all seven.** Only two reciters have a genuinely free portrait and the default reciter's Commons photos are laundered (§3). Agree to monograms throughout, with a photo slot in the manifest for the day a foundation grants one?
6. **Wi-Fi only by default** for surah downloads, with an "also on mobile data" switch in Settings and a one-tap override on the download sheet.
7. **The entry point.** A third round button in the reader header beside the book and "Aa" buttons (§5.1), plus "Play from here" in the ayah action row. Or the ayah row alone, for an even smaller surface?
8. **Slicing.** 3a as in §9 (everything above), then 3b for repeat, speed, sleep timer and download-whole-Quran. Agree, or pull anything forward?

If you answer 1–8 I can start the branch straight away; the data pipeline (§4) runs while the app code is written.

## 1. What was decided on 7 September and what changes now

Kept: the Islamic Network corpus as the source (the only one with a written redistribution licence); per-ayah files; hosting on a public `taqwa-data` repository; downloads per surah; background playback and lock-screen controls in the first slice; credits by name in the Attribution screen and in the player; Mishary Alafasy, Abdul Basit (murattal) and Maher Al Muaiqly in the launch set.

Changed by this brief: **seven reciters instead of three**, a **photo** for each, a **short preview** in the picker in the manner of the adhan sheet, and an explicit instruction that the feature must be **present on every Quran screen without being in your face**.

## 2. Source and licence

Research report with every measurement: `.superpowers/sdd/quran-audio-research.md` (12 September).

**Terms, verbatim from https://alquran.cloud/terms-and-conditions §IV (12 September 2026):** "Recitations are licensed to us by the reciters or their estates for free, non-commercial redistribution at the bitrates we publish. You may stream, embed and download them for personal and educational use. You may bundle them into a commercial product, but please note that copyrights lie with the reciters and they may ask you to remove the con[t]ent." §III adds that they would rather you "cache aggressively at your own edge" and asks anyone needing "full-corpus mirrors" to get in touch through the contact page.

**Verdict.** Mirroring the files on our own host for a free, ad-free app is inside the grant: redistribution is permitted, bundling is permitted even commercially, and caching downstream is invited. No attribution string is mandated for audio; we credit each reciter and the Islamic Network anyway, in the player and the Attribution screen. Two obligations follow: **keep the files at their published bitrates, byte for byte** (no re-encoding, which is why the `.taqa` container in §4 wraps the original MP3s untouched), and **be able to withdraw a reciter** if an estate asks, which the manifest design gives us without an app update. The 7 September courtesy email to the Islamic Network should also say plainly that we mirror the corpus, since §III asks mirrors to say hello.

**Provenance.** The Islamic Network per-ayah files are bit-identical to the everyayah.com corpus (decoded-audio checksums match and everyayah's ID3 comment tag survives in the files). That cuts both ways: the "licensed to us by the reciters" assertion covers a corpus they did not originate, which is the residual risk we carry knowingly; and it means the **CC BY 4.0 word-level timing data from `cpfair/quran-align`** (offsets inside each per-ayah file, 6 of our 7 reciters bit-identical or same-cut) applies directly. Word-by-word highlighting moves from "not planned" to slice 3b.

**Catalogue facts the design must respect** (all measured, details in the report):
- The CDN's bitrate folders are sometimes mislabelled: Maher Al Muaiqly's `/128` is really 64 kbps (and his `/64` folder has four missing ayahs, so we take `/128`); Saud Ash-Shuraim's `/64` is really 128 kbps. The manifest records the *true* bitrate.
- Alafasy's `/128` set was HEAD-checked file by file: all 6,236 present. Every other reported gap in the corpus manifest was re-probed live and now serves, except the two noted above.
- **Four of the seven begin at full voice on sample zero** (Alafasy, Maher, Sudais, Shuraim), the other three carry their own padding. Played back to back the clipped four sound rushed, so the manifest carries one **inter-ayah gap** per reciter (about 300 ms for those four, near zero for the rest) that the player inserts between items.
- Nothing else beats this source on licence clarity: everyayah has no licence page at all (its link has been dead for years), QUL/Quran Foundation caps caching at a week and forbids redistribution, mp3quran.net contradicts itself between pages.

## 3. The seven

Chosen from the twenty Arabic per-ayah editions for voice, breadth of style and a clean file set. Bitrates are the true measured ones; sizes are the whole Quran at that tier; Al-Baqarah is the largest single download. Alafasy stays the default, as decided on 7 September.

| # | Identifier | Reciter | Style | kbps | Whole Quran | Al-Baqarah | Word timings (3b) |
|---|---|---|---|---|---|---|---|
| 1 | `ar.alafasy` (`/64`) | Mishary Rashid Alafasy · مشاري راشد العفاسي | Murattal | 64 | 861 MB | ~58 MB | yes (verify the 64 cut matches the 128 timings) |
| 2 | `ar.abdulbasitmurattal` (`/64`) | Abdul Basit Abdus-Samad · عبد الباسط عبد الصمد | Murattal | 64 | 903 MB | ~60 MB | yes |
| 3 | `ar.mahermuaiqly` (`/128` folder) | Maher Al Muaiqly · ماهر المعيقلي | Murattal | 64 | 605 MB | ~40 MB | no |
| 4 | `ar.husary` (`/64`) | Mahmoud Khalil Al-Husary · محمود خليل الحصري | Murattal | 64 | 1,237 MB | ~85 MB | yes |
| 5 | `ar.minshawi` (`/128`) | Mohamed Siddiq Al-Minshawi · محمد صديق المنشاوي | Murattal | 128 (only tier) | 1,625 MB | ~110 MB | yes |
| 6 | `ar.abdurrahmaansudais` (`/64`) | Abdur-Rahman As-Sudais · عبد الرحمن السديس | Murattal | 64 | 612 MB | ~40 MB | yes (timings are for the 192 cut; verify) |
| 7 | `ar.saoodshuraym` (`/64` folder) | Saud Ash-Shuraim · سعود الشريم | Murattal | 128 | 1,062 MB | ~70 MB | same cut |

Total mirrored: about 6.9 GB. Two Egyptian classical voices (Husary, Minshawi), the two imams of the Haram (Sudais, Shuraim), the two most requested contemporary voices (Alafasy, Maher) and Abdul Basit. Al-Ghamdi, Shatri, Hudhaify and Ajmi are in the corpus and can be added later by a manifest change alone.

**Bitrate.** 64 kbps everywhere it is published, because the licence forbids re-encoding and 64 halves the download; Minshawi and Shuraim have no 64 kbps tier, so they ship at 128 and their sizes say so in the picker caption.

**Photos: none, by decision.** Of twenty-five reciters checked, only Husary and Minshawi have a portrait with a defensible free licence and a usable face (both public domain in Egypt, black-and-white archive photographs). Abdul Basit and Shuraim exist only in group or handshake shots. Alafasy's and Maher's Commons photos are licence laundering: studio portraits tagged "own work" by accounts that mass-upload celebrity images, one deleted as a copyright violation and re-uploaded ten days later under a new licence. Two archive photos beside five monograms would look like an accident, and a laundered portrait of the default reciter is exactly the exposure this app avoids elsewhere. So: **a calligraphic monogram for all seven**, the reciter's name in the Hafs face on a deep tinted disc, one hue per reciter, the treatment shown in the design round. If a reciter's own foundation ever grants a portrait, the manifest can carry a photo URL and the monogram gives way.

**Preview clips.** The picker plays a fifteen-second preview per reciter without a download: Al-Fatiha 1:1–1:2 from each reciter's own files, bundled at their published bitrate (about 100 KB each, ~0.7 MB for seven). The clips are the reciters' unmodified files trimmed at ayah boundaries (they are per-ayah files, so no trimming inside a phrase), the same shape as the adhan previews.

**Monograms.** 56 dp discs in the picker, 40 dp on the player bar, 54 dp square with rounded corners as lock-screen artwork; drawn at runtime from the reciter's initial in the Hafs face, so they cost no assets and scale to any density.

## 4. Data pipeline and hosting

**Repository.** `MohamedAbulgasem/taqwa-data`, public, holding only the pipeline script and a `manifest.json`; the audio is attached to GitHub Releases, one release per reciter (`audio-<reciter>-v1`), 114 assets each. Releases allow 2 GiB per file and up to 1,000 files per release; there is no published bandwidth cap for public repositories and the assets are served through GitHub's CDN. The 7 September investigation looked at Play Asset Delivery, Apple Background Assets and Cloudflare R2 and rejected them for card-on-file or platform-lock reasons; nothing has changed.

**The surah file.** One file per (reciter, surah), holding every ayah of the surah as the untouched per-ayah MP3s from the corpus (ID3 tags and all), so we redistribute exactly what we are licensed to redistribute. Not a zip: iOS has no zip reader in Foundation and Kotlin/Native would need a cinterop for one. Instead a trivially simple container, `.taqa`:

```
magic "TAQA" (4 bytes) · version u8 · reserved (3) · index length u32
index: JSON  {"reciter":"ar.alafasy","surah":2,"kbps":64,"ayahs":[{"n":1,"off":0,"len":31872},…]}
data: the MP3 files back to back, in ayah order
```

Splitting it is a few lines on both platforms; a download is one HTTP request, resumable with `Range`; the header alone (first few KB) tells the app the ayah offsets, so playback of ayah 1 can start while the rest of the file is still arriving. Each asset carries its SHA-256 in the manifest; the app verifies before marking a surah as downloaded.

**The manifest.** `manifest.json` in the repository (fetched raw, cached, refreshed at most daily when online): schema version, the reciter list (id, names in Arabic and English, style, true bitrate, inter-ayah gap in ms, monogram hue, optional photo URL, licence text), and per (reciter, surah) the asset URL, byte size and hash. Reciters can be added, and if an estate ever objects, withdrawn, without an app update; the app also ships a copy of the manifest so the picker works before the first network call.

**The pipeline** (`tools/quran-audio/`, Python, run once per reciter on this Mac): fetch the 6,236 per-ayah files at the chosen bitrate from `cdn.islamic.network` (or the archive.org mirror, which is the same files), verify each is a valid MP3 of plausible duration, pack the 114 containers, compute hashes, write the manifest, upload with `gh release upload`. Roughly 6 GB down and up; an evening.

## 5. The surface in the app

The rule from the brief: always there on the Quran screens, never shouting. Three places, and only three.

### 5.1 The header button

Reader and Mushaf headers gain a third 36 dp round button, a headphones glyph drawn on the 16-unit grid like the book and "Aa" buttons, at the end of the row. Idle it is the same secondary tint as its neighbours. Tap:

- Surah downloaded for the current reciter → playback starts from the first visible ayah and the player bar appears.
- Not downloaded → the **download sheet** (§5.4).
- Already playing this surah → the sheet expands the player (§5.3).

While the surah is playing the glyph takes the accent colour and a two-bar equaliser motion, the only animated element on the screen and the one cue that recitation is live.

### 5.2 The ayah row

The animated action row (bookmark · copy · share) gains a fourth action at its start: **Play from here**. Same glyph size, same animation, same behaviour as the header for an undownloaded surah. In Mushaf mode the tapped ayah's existing highlight already identifies the ayah; the action appears in the Mushaf's ayah sheet.

### 5.3 The player bar

A 56 dp bar pinned above the bottom inset, card surface with a hairline top edge, inset like the cards: reciter photo (40 dp round) · surah name and "Ayah 255" in the caption style · previous-ayah, play/pause, next-ayah as 44 dp targets · a thin accent progress line along the top edge showing position within the surah. Tapping the photo or the text opens the reciter picker; a downward swipe or a small "×" at the end dismisses it and stops.

The bar is a Compose overlay in the tab scaffold, so it survives navigation between Reader, Mushaf and the Quran root and hides on the Prayer and Settings tabs while playback continues in the background. When the user scrolls away from the playing ayah, a small "Following" pill appears at the top of the bar; tapping it scrolls back and re-enables following.

**Following.** The playing ayah is highlighted exactly like a tapped ayah (reader card accent; Mushaf word-run field), and the list scrolls so the ayah sits in the upper third. Following pauses while the user's finger is on the screen and for four seconds after, then resumes only if the user has not moved more than a screen away, in which case the pill offers the way back.

### 5.4 The download sheet

Title: the surah name. One row: reciter photo, name, style caption, a chevron to change reciter. Below: **Download · 24.6 MB** as the primary button, with a caption "Over Wi-Fi" or "Over mobile data" as the case may be and a switch-like tap to override this once. While downloading, the button becomes a progress bar with the running size; the sheet may be dismissed and the header button shows a thin ring of progress. Failure shows a plain sentence and a Retry. A second button, quieter, **Download the whole Quran for this reciter · 0.87 GB**, is slice 3b.

### 5.5 The reciter picker

A sheet, opened from the download sheet, the player bar or Settings › Quran › Recitation. A vertical list (seven fit without scrolling on a normal phone): 56 dp round photo · name in the row label style, Arabic name beneath under an Arabic UI, otherwise the style caption ("Murattal · 64 kbps") · the same play triangle as the adhan sheet, playing the bundled preview · a radio. A downloaded-surah count in the caption when non-zero ("12 of 114 surahs downloaded"). Picking a reciter while a surah is playing switches voice at the next ayah if that surah is downloaded for the new reciter, otherwise offers the download.

### 5.6 Settings

Settings › Quran › **Recitation**: reciter row (opens the picker), *Download over mobile data* toggle, a **Downloads** screen listing reciters with storage used and per-surah delete, and the credits line pointing to Attribution.

## 6. Playback engine

`expect class RecitationPlayer` in `shared` with one small API: `load(queue: List<AyahTrack>, startIndex)`, `play()`, `pause()`, `seekToAyah(i)`, `next()`, `previous()`, `stop()`, a `StateFlow<PlaybackState>` (ayah index, position, duration, playing, buffering) and `setNowPlaying(surah, ayah, reciter, artwork)`.

- **Android:** Media3 ExoPlayer inside a `MediaSessionService` (foreground, `mediaPlayback` type, the standard media notification with artwork and previous/play/next). The playlist is the surah's ayahs as `MediaItem`s over the container file using a custom `DataSource` that reads byte ranges from the `.taqa` (no extraction to disk). ExoPlayer plays a playlist gaplessly; per-ayah boundaries fire `onMediaItemTransition`, which is the highlight signal. Audio focus handled by the player (pause on loss, duck never; recitation should not be ducked under a notification, it pauses). Between items the player inserts the reciter's inter-ayah gap from the manifest (§2), a short silent `MediaItem` on Android and a timed pause before `advanceToNextItem` on iOS.
- **iOS:** `AVQueuePlayer` of `AVPlayerItem`s over per-ayah `AVAsset`s (the container split to per-ayah temp files on first play of a surah, or served through a custom `AVAssetResourceLoaderDelegate` reading byte ranges; the temp-file approach is simpler and the files are small). `AVAudioSession` category `playback`, `UIBackgroundModes: audio`, `MPNowPlayingInfoCenter` for artwork and text, `MPRemoteCommandCenter` for play/pause/next/previous, interruption handling for calls and the adhan.
- **The adhan.** A prayer notification's sound plays over recitation on both platforms today. Recitation pauses on audio-focus loss (Android) and on interruption (iOS), which is what the adhan triggers, then resumes only if the interruption was short and the platform says it may.

## 7. Downloads

`expect class SurahDownloader`: `download(reciter, surah)` returning a flow of progress, resumable, verifying SHA-256, writing to `files/quran/audio/<reciter>/<surah>.taqa`; `cancel`, `delete`. Android: WorkManager `CoroutineWorker` with a foreground progress notification, `HttpURLConnection` with `Range` (no Ktor; the app has no HTTP client today and one dependency for one download loop is not worth it). iOS: `URLSession` with a background configuration so a 25 MB surah finishes after the app is suspended, the delegate writes the file and posts the progress. A small `downloads` table in the existing SQLite database records what is complete, so the UI never scans the disk. Network permission: `INTERNET` is added to the Android manifest for the first time; iOS needs nothing.

## 8. Storage, sizes, limits

| Item | Size |
|---|---|
| App growth (previews + photos + code + Media3) | ~2.5 MB APK, ~1.5 MB IPA |
| Al-Fatiha | under 1 MB |
| Ya-Sin | ~6 MB |
| Al-Baqarah (Alafasy, 64 kbps) | ~58 MB |
| Whole Quran, one reciter | 0.6–1.6 GB |

Downloads live in app-private storage and are removed with the app; the Downloads screen shows the total and allows per-surah or per-reciter deletion. A device below 200 MB free refuses a download with a plain sentence.

## 9. Slicing

| Slice | Contents | Release |
|---|---|---|
| **3a** | `taqwa-data` pipeline and hosting for seven reciters; manifest; per-surah download with progress, resume and verify; reciter picker with photos and previews; header button, ayah-row action; player with ayah highlight and following; player bar; background playback and lock-screen controls; adhan interruption; Settings › Recitation with Downloads; credits | 0.11.0 (13) |
| **3b** | Word-by-word highlighting from the CC BY 4.0 `quran-align` timings (six of seven reciters); repeat ayah / range / surah; playback speed; sleep timer; download whole Quran for a reciter; continue into the next surah | later minor |

## 10. Risks

| Risk | Answer |
|---|---|
| GitHub objects to ~6 GB of release assets | It is well within their published limits and the repository is public and open source; if it ever happened, Cloudflare R2's free tier (10 GB) takes the same files with a URL change in the manifest |
| A reciter or estate asks for removal | Withdrawn from the manifest; the app hides the reciter and offers to delete the files; no app update needed |
| iOS background download quirks | Standard `URLSession` background sessions; the pattern is well trodden; verified on the iPhone 12 |
| Media3 and Compose Multiplatform versions | Media3 is Android-only and sits behind the expect/actual, so it cannot leak into common code |
| A long surah's per-ayah playlist (286 items) | ExoPlayer and AVQueuePlayer both handle playlists of that size; the queue is built lazily from the container index |
| Following fights the user's scrolling | The four-second, one-screen rule in §5.3, tested on the S23 |

## 11. Design round

Mockups of the header button, the ayah action, the player bar (incl. the “back to ayah” pill and the lock screen), the download sheet in its three states, and the picker in list and grid form: https://claude.ai/code/artifact/e72b9fd3-d64b-400c-9636-918dc6fa0d4b. My picks are at the foot of that page: both entry points, a headphones glyph, previous/next ayah on the bar, the list picker, the pill rather than a snapping scroll.

## 12. Decisions taken on 12 September (Mohamed's review)

Answers to §0, plus two design corrections. This section governs where it differs from anything above.

1. **Reciters: ten.** The seven in §3 plus Abu Bakr Ash-Shatri (`ar.shaatree`, 128 kbps, 1,448 MB), Ali Al-Hudhaify (`ar.hudhaify`, 128 kbps, 1,714 MB) and Ahmed Al-Ajmi (`ar.ahmedajamy`, 128 kbps, 1,545 MB), each at its only published tier. Saad Al-Ghamdi was asked for and **cannot be included**: he is absent from the Islamic Network catalogue, and no other source carries a licence we can rely on. About 11.6 GB mirrored in total; Alafasy remains the default.
2. **Bitrate:** 64 kbps where published, otherwise the single published tier. Confirmed.
3. **Hosting:** the public repository is `MohamedAbulgasem/Taqwa-data` (capital T); `gh` is installed and signed in on this Mac. Releases named `audio-<identifier>-v1`, one per reciter.
4. **Emails:** not yet sent. Re-issued to Mohamed on 12 September with the current facts (ten reciters, mirroring) for sending from his personal address.
5. **Monograms for all ten**, no photos. Confirmed; the manifest keeps an optional photo URL per reciter.
6. **Wi-Fi only by default**, with a per-download override and a Settings switch. Confirmed.
7. **Entry points: header button and ayah action**, with a **speaker glyph** rather than headphones in the idle state (headphones read wrong when nobody is wearing any). The live state is unchanged: accent colour and the three-bar equaliser.
8. **Slice 3a** as in §9 **plus "Download the whole Quran" for a reciter** (queue all 114 surahs, total size stated first, cancellable, survives the app being backgrounded). Word-level highlighting, repeat, speed and the sleep timer remain 3b.

**Lock-screen and media-notification artwork is the Taqwa app icon**, not the reciter's monogram; the monogram is an in-app device only. Version for 3a: **0.11.0 (13)**.

## 13. Amendments during the build (13 September)

- **Container index key** is `kbps`, not `bitrate` (§4 sketch corrected above); `TaqaIndex` and the pipeline agree.
- **Source fallback rule.** Where `cdn.islamic.network` answered a persistent HTTP 502 for an ayah (about sixty across Alafasy, Abdul Basit, Husary and Minshawi), the pipeline took that ayah from the everyayah.com mirror at the same bitrate, but only after proving for that reciter that three ayahs already held from both sources decode to identical audio, i.e. the same masters. A reciter whose mirror copy is a different encode (Shuraim) gets no fallback and would be dropped rather than patched. Recorded per reciter in the pipeline report and in `Taqwa-data`'s README.
- **Manifest in force** is whichever of the bundled and cached manifests was generated later, so a fresh install with a newer bundle is not downgraded by an old cache.
- **Speaker glyph mirrors under RTL** (Material's convention for volume icons); the equaliser does not.
- **Batch cancel** lives on the Recitation screen; the download sheet's Cancel stops the one surah it shows. Noted for 3b: a batch's sheet should offer to cancel the batch.
- **Withdrawn reciter's files** stay on disk and in the storage total but lose their card; offering to delete them is 3b.
