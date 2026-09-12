# Taqwa slice 3: Quran recitation — investigation and proposal

Status: **for Mohamed's review, 12 September 2026.** Written while he was out. Everything marked *decide* in §0 needs his answer; everything else is a recommendation he can overrule. Nothing is built yet. Companion to the 7 September investigation (`2026-09-07-quran-text-audio-investigation.md`, §5 and §11), whose decisions this keeps unless stated.

## 0. The short version, and what I need from you

**What ships.** Recitation inside the reader and the Mushaf, downloaded on demand one surah at a time, with the current ayah lit and the page following the voice; seven reciters in a picker with a photo and a fifteen-second preview each; background playback with lock-screen controls on both platforms; a slim player bar that stays out of the way of reading. The default reciter is Mishary Alafasy, as decided on 7 September.

**What it costs.** Nothing recurring. Audio lives on a public GitHub repository's Releases, served through GitHub's CDN at no charge. The app grows by under 1 MB (seven preview clips and seven small photos). A surah download is 0.2 MB (Al-Kawthar) to ~25 MB (Al-Baqarah) at 64 kbps.

**Decide (answers by number are enough):**

1. **The seven reciters.** The set in §3 is my pick from the corpus we may redistribute. Swap any.
2. **Bitrate.** 64 kbps for everyone (a full reciter is roughly 0.6–0.9 GB; Al-Baqarah ~25 MB) rather than 128 where it exists (double). Recommend 64: on a phone speaker or earbuds the difference is small, and half the download is a real gift on mobile data.
3. **Hosting access.** Create the public repository `MohamedAbulgasem/taqwa-data` (empty is fine) and either install the GitHub CLI and sign in (`brew install gh && gh auth login`) or give me a fine-grained token with *Contents: read and write* on that one repository. Uploading ~6 GB of release assets is the one step I cannot do from here without it. Zero cost.
4. **The courtesy emails** drafted on 7 September (Islamic Network; Alafasy's foundation). Were they sent, and did anyone reply? Their terms permit us regardless (§2), so this does not block, but a reply is worth recording in the Attribution screen.
5. **Photos.** For reciters without a genuinely free photo, a calligraphic monogram of the name in the amber palette instead of a portrait. Agree? (§3 says who has one.)
6. **Wi-Fi only by default** for surah downloads, with an "also on mobile data" switch in Settings and a one-tap override on the download sheet.
7. **The entry point.** A third round button in the reader header beside the book and "Aa" buttons (§5.1), plus "Play from here" in the ayah action row. Or the ayah row alone, for an even smaller surface?
8. **Slicing.** 3a as in §9 (everything above), then 3b for repeat, speed, sleep timer and download-whole-Quran. Agree, or pull anything forward?

If you answer 1–8 I can start the branch straight away; the data pipeline (§4) runs while the app code is written.

## 1. What was decided on 7 September and what changes now

Kept: the Islamic Network corpus as the source (the only one with a written redistribution licence); per-ayah files; hosting on a public `taqwa-data` repository; downloads per surah; background playback and lock-screen controls in the first slice; credits by name in the Attribution screen and in the player; Mishary Alafasy, Abdul Basit (murattal) and Maher Al Muaiqly in the launch set.

Changed by this brief: **seven reciters instead of three**, a **photo** for each, a **short preview** in the picker in the manner of the adhan sheet, and an explicit instruction that the feature must be **present on every Quran screen without being in your face**.

## 2. Source and licence

*(Filled from the 12 September research report, `.superpowers/sdd/quran-audio-research.md`.)*

@@SOURCE@@

## 3. The seven

@@RECITERS@@

**Preview clips.** The picker plays a fifteen-second preview per reciter without a download: Al-Fatiha 1:1–1:2 from each reciter's own files, bundled at their published bitrate (about 100 KB each, ~0.7 MB for seven). The clips are the reciters' unmodified files trimmed at ayah boundaries (they are per-ayah files, so no trimming inside a phrase), the same shape as the adhan previews.

**Photos.** Each reciter has a 96 dp circular portrait in the picker and a 40 dp one on the player bar and lock screen. Sources and licences per reciter are in the table; the files are bundled at 192 px WebP (~10 KB each). Where no free photo exists, a monogram: the reciter's name in the Hafs face on the card surface, amber on dark, the same treatment as the Tasbeeh chips. Nothing scraped from a news site or a YouTube thumbnail, whatever the temptation.

## 4. Data pipeline and hosting

**Repository.** `MohamedAbulgasem/taqwa-data`, public, holding only the pipeline script and a `manifest.json`; the audio is attached to GitHub Releases, one release per reciter (`audio-<reciter>-v1`), 114 assets each. Releases allow 2 GiB per file and up to 1,000 files per release; there is no published bandwidth cap for public repositories and the assets are served through GitHub's CDN. The 7 September investigation looked at Play Asset Delivery, Apple Background Assets and Cloudflare R2 and rejected them for card-on-file or platform-lock reasons; nothing has changed.

**The surah file.** One file per (reciter, surah), holding every ayah of the surah as the untouched per-ayah MP3s from the corpus, so we redistribute exactly what we are licensed to redistribute. Not a zip: iOS has no zip reader in Foundation and Kotlin/Native would need a cinterop for one. Instead a trivially simple container, `.taqa`:

```
magic "TAQA" (4 bytes) · version u8 · reserved (3) · index length u32
index: JSON  {"reciter":"ar.alafasy","surah":2,"bitrate":64,"ayahs":[{"n":1,"off":0,"len":31872},…]}
data: the MP3 files back to back, in ayah order
```

Splitting it is a few lines on both platforms; a download is one HTTP request, resumable with `Range`; the header alone (first few KB) tells the app the ayah offsets, so playback of ayah 1 can start while the rest of the file is still arriving. Each asset carries its SHA-256 in the manifest; the app verifies before marking a surah as downloaded.

**The manifest.** `manifest.json` in the repository (fetched raw, cached, refreshed at most daily when online): schema version, the reciter list (id, names in Arabic and English, style, bitrate, photo credit, licence text), and per (reciter, surah) the asset URL, byte size and hash. Reciters can be added, and if an estate ever objects, withdrawn, without an app update; the app also ships a copy of the manifest so the picker works before the first network call.

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

- **Android:** Media3 ExoPlayer inside a `MediaSessionService` (foreground, `mediaPlayback` type, the standard media notification with artwork and previous/play/next). The playlist is the surah's ayahs as `MediaItem`s over the container file using a custom `DataSource` that reads byte ranges from the `.taqa` (no extraction to disk). ExoPlayer plays a playlist gaplessly; per-ayah boundaries fire `onMediaItemTransition`, which is the highlight signal. Audio focus handled by the player (pause on loss, duck never; recitation should not be ducked under a notification, it pauses).
- **iOS:** `AVQueuePlayer` of `AVPlayerItem`s over per-ayah `AVAsset`s (the container split to per-ayah temp files on first play of a surah, or served through a custom `AVAssetResourceLoaderDelegate` reading byte ranges; the temp-file approach is simpler and the files are small). `AVAudioSession` category `playback`, `UIBackgroundModes: audio`, `MPNowPlayingInfoCenter` for artwork and text, `MPRemoteCommandCenter` for play/pause/next/previous, interruption handling for calls and the adhan.
- **The adhan.** A prayer notification's sound plays over recitation on both platforms today. Recitation pauses on audio-focus loss (Android) and on interruption (iOS), which is what the adhan triggers, then resumes only if the interruption was short and the platform says it may.

## 7. Downloads

`expect class SurahDownloader`: `download(reciter, surah)` returning a flow of progress, resumable, verifying SHA-256, writing to `files/quran/audio/<reciter>/<surah>.taqa`; `cancel`, `delete`. Android: WorkManager `CoroutineWorker` with a foreground progress notification, `HttpURLConnection` with `Range` (no Ktor; the app has no HTTP client today and one dependency for one download loop is not worth it). iOS: `URLSession` with a background configuration so a 25 MB surah finishes after the app is suspended, the delegate writes the file and posts the progress. A small `downloads` table in the existing SQLite database records what is complete, so the UI never scans the disk. Network permission: `INTERNET` is added to the Android manifest for the first time; iOS needs nothing.

## 8. Storage, sizes, limits

| Item | Size |
|---|---|
| App growth (previews + photos + code + Media3) | ~2.5 MB APK, ~1.5 MB IPA |
| Al-Fatiha | ~0.3 MB |
| Ya-Sin | ~5 MB |
| Al-Baqarah (64 kbps) | ~25 MB |
| Whole Quran, one reciter (64 kbps) | 0.6–0.9 GB |

Downloads live in app-private storage and are removed with the app; the Downloads screen shows the total and allows per-surah or per-reciter deletion. A device below 200 MB free refuses a download with a plain sentence.

## 9. Slicing

| Slice | Contents | Release |
|---|---|---|
| **3a** | `taqwa-data` pipeline and hosting for seven reciters; manifest; per-surah download with progress, resume and verify; reciter picker with photos and previews; header button, ayah-row action; player with ayah highlight and following; player bar; background playback and lock-screen controls; adhan interruption; Settings › Recitation with Downloads; credits | 0.11.0 (13) |
| **3b** | Repeat ayah / repeat range / repeat surah; playback speed; sleep timer; download whole Quran for a reciter; continue into the next surah; ayah-level share of the audio clip | later minor |
| **3c** (if ever) | Word-level highlighting: needs timing data we cannot yet redistribute (§2) | not planned |

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

A mockup page for the picker, the player bar and the header button follows this document (link to be added), so the visual direction is settled before the plan is written.
