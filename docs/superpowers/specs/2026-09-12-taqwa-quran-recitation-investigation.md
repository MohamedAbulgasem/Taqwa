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

## 14. Round two — 13 September (Mohamed's six asks after a day with 0.11.0)

Built on branch `recitation-2`. Each subsection is one ask, the decision taken, and where it
lives. §2's licence position and §4's container are unchanged except where §14.6 says.

### 14.1 The surah as one clock

**Ask.** Progress against the whole surah, with elapsed and total time, "like a music player".

**Decision.** `SurahTimeline`: every queue item's length in ms — ayahs *and* the reciter's gaps —
in queue order, with `startOf`, `elapsed(index, positionMs)`, `indexAt(ms)` and `snapToAyah`.
The lengths are **estimated from the container, not measured**: an ayah's audio bytes (its
`len` less the ID3 tag at its head, read as ten bytes per ayah through one open handle) over the
bit-rate the index records, `bytes × 8 / kbps` ms. Measured against ffprobe on the corpus the
error is under 30 ms an ayah for a constant-bit-rate file. Both players build the timeline from
the same container and there is deliberately **no refinement from measured durations**, so the
app's bar and the service's lock-screen bar are on one clock. Where an item's measured length
differs from its slot, the position is *scaled into the slot* (`fitToSlot`) rather than clamped:
the clock runs a shade fast or slow through that ayah and never stalls or jumps at a seam.

`PlaybackState` carries `surahPositionMs`/`surahDurationMs` beside the ayah pair; `barFraction`
reads them when present. `formatClock` prints `m:ss`, or `h:mm:ss` for both ends once the surah
runs an hour, in the locale's digits and padded with its zero.

**Android.** The app reads the timeline at `load` and sends it to the service with the queue's
text (`ARG_TIMELINE`, a `LongArray`). `AyahPlayer` reports `getCurrentPosition`, `getDuration`,
`getBufferedPosition` and their content twins on the surah's clock while the timeline matches
the loaded queue, so the notification and lock screen show the whole surah; `seekTo(ms)` snaps
to the start of the ayah under the thumb; `seekBack`/`seekForward` move by ayah. The app's
`MediaController` therefore reads surah-level numbers too, and derives the within-ayah position
the previous-button rule needs (`withinAyah`), with a duration check (`onSurahClock`) so a
session without a clock is still read correctly.

**iOS.** Same timeline in `MPNowPlayingInfoCenter`; the gap — a `delay`, not an item — is
timed with a monotonic mark; `changePlaybackPositionCommand` is enabled with the same snap.

**Verified.** Emulator: Al-Baqarah by Al Muaiqly 1:41:59, by Ash-Shatri 1:58:20; `dumpsys
media_session` position 53,896 ms at ayah 7 (queue item 12), i.e. the surah's clock, not the
item's.

### 14.2 The bar

**Ask.** Bigger previous / play / next so a thumb does not hit the wrong one; the card may grow.

**Decision.** 76 dp (was 56): a hairline, a 19 dp clock row — elapsed, a 3 dp accent line
filling from the reading edge, total — and a 56 dp transport row. Previous, play and next are
48 dp targets (were 44) with 22 dp skip glyphs (18) and a 40 dp play disc (34); the dismiss
cross stays smaller at 40 dp so it is the hardest of the four to hit by accident. The monogram
sits in a 46 dp box that leaves room for the incoming-voice ring (§14.3). `PlayerBarHeight`
drives the reader's and Mushaf's bottom clearance, so nothing else had to move. Titles
ellipsise rather than clip. The clocks use tabular figures.

### 14.3 Picking a voice that does not have the playing surah

**Ask.** Today the tap does nothing visible; only stopping the bar gets the download offered.
Offer the download while the old voice carries on, then switch when it lands.

**Decision.** `pickReciter` persists the choice, drops any switch still waiting, and then, for
a voice without this surah, opens the download sheet for it — the same sheet, with one more
sentence: *"Al Muaiqly keeps playing until it arrives."* on the Ready face, and *"Al Muaiqly
keeps playing. The voice changes to Ash-Shatri as soon as it lands."* on the Running face
(`DownloadSheetState.playingMeanwhile`). The bar names the voice being heard and draws the
header's 2 dp ring around its monogram while the chosen voice's copy arrives
(`BarState.incoming`). `confirmDownload` records the pending play with `follow = true` when
the surah is being recited; when the library reports the surah, the new voice starts **at the
ayah being heard at that moment**, and paused if the listener had paused. Cancelling the
download leaves the choice of voice as made and the old voice playing; dismissing the bar drops
the pending switch (the download itself continues, as every download does).

**Verified.** Emulator: Al Muaiqly playing Al-Baqarah, pick Ash-Shatri → sheet with the
sentence → confirm → ring on the monogram → 108 MB later the bar and the session metadata read
Ash-Shatri, at the ayah being heard.

### 14.4 The same voice, paused

**Ask.** Tapping the current reciter in the picker while paused should resume.

**Decision.** `pickReciter` on the voice already loaded: paused → `play()`; playing → nothing.
The picker stays open, as it always did after a pick. Verified on the emulator through the
session's state: PAUSED → PLAYING on the tap.

### 14.5 Previews over a recitation

**Ask.** A preview played over the surah; it should pause the surah and resume it after.

**Decision.** `previewReciter` pauses a playing recitation and remembers that it did
(`pausedForPreview`); the recitation is given back when the clip ends — `ClipPlayer.play` now
takes an `onEnd`, reported by `MediaPlayer`'s completion listener and `AVAudioPlayer`'s
delegate, with the 17 s timeout as the backstop — when the picker closes, or when a pick leaves
the old voice playing. It is *not* given back when the reader presses play on the bar (their
press is the resume) or when a pick starts a new voice. A preview over a recitation that was
already paused resumes nothing. Verified on the emulator: PAUSED on the triangle, PLAYING 17 s
later.

### 14.6 Reciter order, and Al-Ajmi

**Order.** Alafasy, Ash-Shatri, Al Muaiqly, Abdul Basit, Al-Minshawi, Al-Husary, Ash-Shuraim,
As-Sudais, Al-Hudhaify, then Al-Ajmi. The app draws the manifest in its own order, so the order
is the pipeline's reciter table (`common.py`); published to Taqwa-data and bundled the same
afternoon, so 0.11.0 installs pick it up on their next daily refresh.

**Al-Ajmi.** The 128 kbps edition (everyayah's *ketaballah* set, byte-identical to the CDN's)
is broken at source in several places: 9:62 is an MPEG-video fragment, 50:10 a quarter-second
stub, 50:9 a 1.7 s stub that passed the length check, three ayahs of 77 are 32 kbps at 11 kHz,
and hundreds are variable-bit-rate averaging 117–134 kbps. The 64 kbps folder is the same
recording (cross-correlation 0.95 on the ayahs compared) and whole where the 128 is broken, so
**Al-Ajmi ships at 64 kbps**, like four of the other nine. Two pipeline safeguards came out of
finding this: `repair.py` now probes what the CDN serves before counting it recovered (a 200
with no audio stream had it looping), and has a `--substitute` last resort that takes an ayah
from the reciter's other published bit-rate after proving the same edition on three controls;
`pack.py` probes every file's bit-rate and writes an ayah's own `kbps` into the index when it is
further than 2 kbps from the reciter's, which `TaqaAyah.kbps` reads for the length estimate. The
container format is unchanged — the key is optional and 0.11.0 ignores it.

### 14.8 After the whole-branch review (13 September, evening)

The review (`.superpowers/sdd/recitation-r2-review.md`, 1 critical / 6 important / 9 minor)
changed four things above; the text above is left as written and corrected here.

- **The container carries each ayah's measured length.** §14.1's estimate stays only for the
  nine containers published before this date. `verify.py` now measures every file by a full
  decode — ffprobe's duration is read off the file's own header, and a tenth of Al-Ajmi's
  64 kbps files carry a header claiming fifteen times their real length — and `pack.py`
  writes the result as `ms` on every ayah of the index (`TaqaAyah.ms`). Per-ayah `kbps`, the
  first fix, is gone: it measured the wrong quantity. The estimate for the older containers
  also leaves out the Xing/Info frame after the ID3 tag; against a decode it still runs about
  1 % long (LAME encoder delay and padding, which both players trim), so on those nine the
  displayed total is some twenty seconds long over Al-Baqarah and the line ends a fraction
  short of full. `fitToSlot` keeps that continuous, and refuses to scale by a "measured"
  platform length more than twice off its slot, which is what a lying header looks like. A
  slot is never shorter than 250 ms.
- **The switch cannot be lost.** Re-picking the voice a switch is waiting on keeps it; picking
  a voice whose copy is already arriving (a batch, a dismissed sheet) arms one without a
  confirm; a switch whose recitation has since ended or been dismissed starts nothing; the
  supersede happens before the settings write suspends. A preview claims its row before its
  clip is read and inherits the pause of the preview it replaces.
- **A pick of a different downloaded voice while paused starts it** (the reviewer's minor 12
  asked for it to stay paused). Kept on purpose: §14.4 exists because a tap on a picker row
  while nothing is playing must produce a voice, and that holds for a new voice as much as
  for the current one. The follow switch is different — it happens later, with no tap.
- **`--substitute` is a human's call.** `finish.py` no longer passes it; what it proves is
  three control ayahs of equal length, not the same take, and it says so and logs every
  substitution for someone to listen to. The pipeline's gate is one function, `verify.probe`:
  a decoded length over 0.3 s, an average bit-rate between 24 and 400 kbps, and — where other
  reciters' lengths are on disk (`state/durations-<id>.json`, medians) — within a factor of
  three of them. That last rule is what catches a 1.7 s stub that plays.
- `AyahPlayer` also reports buffered percentage, total buffered duration and zero seek
  increments on the surah clock, and caches its queue; the Android `publish()` lost a gap
  branch the wrapper makes unreachable; the iOS gap resumes from where a pause froze it.

### 14.7 Not done in this round

- Scrubbing on the bar itself. The drag-down-to-dismiss gesture owns the bar's vertical axis
  and the line is 3 dp; the lock screen has the scrub.
- Refining the clock from measured durations (see §14.1 for why not).
- Cancelling a switch's download from the bar; the sheet's Cancel does it.
- Re-packing the nine published reciters with measured lengths (a gigabyte of upload each);
  their clocks estimate, about 1 % long.

## 15. Round three — 13 September, evening (three more asks after 0.13.0)

### 15.1 Previous and next move by surah

**Ask.** Next and previous should go to the next and previous surah, like a music player's
track buttons; a press-and-hold does the ayah. The same on the lock screen.

**Decision.** On the bar, a tap on previous or next is the neighbouring surah from its first
ayah, and a long press is the ayah move it used to be, with the platform's long-press haptic
so the finger knows which it got; a screen reader gets the hold as a custom action. Previous is
literally the previous surah — no "restart the surah if more than three seconds in" rule,
which would have made the button restart Al-Baqarah almost every time — and at the ends of the
Quran the buttons do nothing. Both go through the same path as a tap on a surah's header, so a
neighbour that is not on the phone is offered on the sheet, or fetched without asking (§15.3).

**Lock screen.** The system's previous and next are surah moves: on Android `AyahPlayer`
reports the press to the app as a session broadcast (`SURAH_PREVIOUS` / `SURAH_NEXT`) and the
controller decides; on iOS the track commands do the same through `RecitationPlayer.skips`.
A lock screen cannot long-press, so on Android the notification carries two extra buttons,
"Previous ayah" and "Next ayah" (Media3 media-button preferences in the secondary slots,
labels sent by the app in its own language); headset rewind and fast-forward move by ayah as
before. iOS shows either track buttons or interval buttons, never both, so there the ayah is
reached by the seek bar, which snaps to ayahs.

**The page follows.** A reader on the surah that was playing is taken to the one now playing,
at its first ayah, exactly as the "Next" row at the foot of a surah would take them; a reader on
some other surah is left where they are. The Mushaf follows by page on its own. While the next
surah is being fetched without asking, the bar's monogram carries the same ring the reciter
switch draws (§14.3), so the tap is seen to have done something.

**Verified.** Emulator: bar next offered Ali 'Imran on the sheet, the confirm with the switch on
fetched it and started it; the system's previous (`KEYCODE_MEDIA_PREVIOUS`) went back to
Al-Baqarah; a long press on next moved one ayah; the notification's two extra buttons moved an
ayah each way; a further next fetched An-Nisa with no sheet, started it 75 s later and the reader
followed it.

### 15.2 Air over the clock

The clock row grows from 19 to 26 dp with a 5 dp inset above the clocks; the bar is 83 dp.

### 15.3 Downloading without asking

**Ask.** The sheet on every surah that is not on the phone gets annoying; offer "always
download, don't ask again", and decide whether it starts ticked.

**Decision.** The download sheet carries a switch, *Download future surahs without asking*,
**ticked the first time** and thereafter in whatever position the reader last confirmed it; it
is written on confirm (either button), not on a tap alone. Settings › Quran › Recitation has
the same switch as *Download without asking*, so it can be turned off again. Ticked by default
because a sheet on every surah is the thing most people will not want, and the first sheet still
appears — it is the moment the reader learns the size and the Wi-Fi rule — so nothing is fetched
that they did not confirm once.

With it on: Play, the header button or a surah skip on a surah not on the phone fetches it at
once and plays it the moment it lands, the header's ring being the only thing that moves in
between; picking a new voice while another plays fetches its copy and switches when it lands,
with the ring on the monogram (§14.3) and no sheet. The Wi-Fi rule still applies. A refusal —
no Wi-Fi, no network, no room — is the one thing the reader must see, so the sheet opens itself
on the failure face, once per failure, with the sentence and its Retry or mobile-data override.

### 15.4 Saying what the bar is waiting for

**Ask.** Next on a surah that is not on the phone, with "without asking" on, gave no sign
of anything until the surah landed and the voice moved.

**Decision.** A status strip slides in above the clock while the bar is waiting on a download
and goes when it lands: *Next: Al-Ma'idah · 38 %* after a skip, *Ash-Shatri · 38 %* when a
new voice's copy of the surah playing is on its way, the name in the accent and the percentage
in tabular figures. The bar grows by the strip (`playerBarHeight(bar)`), and the reader's and
Mushaf's clearance grow with it; the ring on the monogram stays. `BarState.incoming` became
`IncomingDownload(surah, reciter, fraction)` so the strip can name what is coming. Verified on
the emulator: next on An-Nisa showed *Next: Al-Ma'idah · 0 %* at once and counted up.

### 15.5 Back to the ayah being recited

**Ask.** Leaving the reader — back to the Quran root, or into another surah — loses the place;
the bar should take you back to the recited ayah. And a tap on the media notification or the
lock-screen player should open the app on the recited surah with the ayah highlighted, in the
mode the reader uses (translation or Mushaf), on both platforms.

**Decision.** The bar's two taps split where the eye already splits them: the **monogram** is
the voice and opens the reciter picker; the **surah and ayah** are the place and open it. "Open
it" means: a screen already showing the recited surah (its reader, or the Mushaf) scrolls to
the ayah and re-arms following, exactly as the "Back to ayah" pill does; any other screen is
replaced (another reader or Mushaf) or pushed (from the root, or from another tab after
selecting Quran) with the reader the user reads in — `recitationTarget`, the widget's target
without its selection, since the recitation's own highlight marks the ayah and moves with it.

**Android.** The media session carries a session activity: the app's launcher intent with an
`open_playing` extra. `MainActivity` turns it into `LaunchRequests.openPlaying()`, a request
resolved by `App` once it is in front — the voice keeps moving while the app comes up, so it is
"the recited ayah", not a reference. Verified on the emulator from the Prayer tab and from the
home screen.

**iOS.** The lock screen's Now Playing opens the app with no word about why, so there is no tap
to hear. The app coming to the front **while a recitation is playing** opens the recited ayah
instead (`foregroundReturnsToRecitation`, iOS only); paused, it opens where it was left, so
opening the app for the prayer times with a surah paused in the background moves nothing.

## 16. Round four — 14 September (after 0.18.1)

### 16.1 Playing on into the next surah

**Ask.** "Automatically proceed to the next surah when the current one finishes — currently it
just stops on surah end."

**Decision.** A surah that plays out is followed by the next one — the literal neighbour, from
its first ayah — after a one-second breath, in the chosen voice, through the same rules as the
bar's Next (§15.1) except that nothing is *asked*. The next surah on the phone plays. One that is
not is fetched only under "without asking" (§15.3): the bar goes, the header's ring shows it
arriving, and it starts the moment it lands. With downloads on request the recitation simply
ends where a tap would have opened the sheet, because a sheet nobody asked for, over whatever
screen they happen to be on, is no answer to a phone that has gone quiet; Next is one tap away.
An-Nas ends the recitation. The reader on the surah that ended follows to the new one, as it
does for a skip.

**How.** The platform players no longer tear themselves down at the last ayah. They hold the
ended surah — the bar paused with its line full, the notification and the audio session still
up — for a few seconds and report the end (`RecitationPlayer.surahEnds`), and the controller
decides, as it does for every other move. A decision to go on is a plain `load` into the same
session: the notification's title changes and nothing flickers, audio focus is never given up
and taken back (which would let a paused podcast in for a second), and on iOS the audio session
stays active, the one way a transition in the background is reliable there (a background task
covers the split of the next container). A decision to stop is the same teardown as before, made
explicit; and should no decision arrive, the hold lapses into that teardown by itself, so the
surah-ended-with-a-notification-forever the old teardown was written against cannot come back.
Anything that moves the recitation during the breath — a seek back into the surah from the lock
screen, a tap on another ayah, the bar's × — wins over the advance.

### 16.2 The bar after a widget tap

**Ask.** Opening an ayah from the home-screen widget and pressing Play on its card played the
surah with no bar; the bar appeared only after leaving the Quran root for another tab and coming
back.

**Cause.** The widget's path pushed the Quran root and the reader on top of whatever tab was
showing (the D3 rule that one Back should reach the surah list), while the bar is drawn only
while the Quran tab is *current*, and `Navigator.currentTab` reads the bottom of the stack —
still Prayer. `Navigator.openReading` now does what the notification's path already did:
from another tab the stack is replaced with the Quran root first, and the reader goes on top of
it; both entry points share it.

### 16.3 One notification for a batch

**Ask.** "Download the whole Quran" put a notification in the shade for every surah in flight,
two at a time, plus the summary line — could it be one long notification?

**Decision.** Yes, and it is an Android detail rather than a design one: WorkManager posts each
worker's foreground notification under the id the worker names, and the workers named one id per
surah. Every download worker now uses **one** id. While more than one surah of a voice is in
flight — now, or at any moment during the transfer — each worker writes the same batch line,
"Mishary Rashid Alafasy · 12 of 114 surahs", with the bar counting surahs the phone now has, so
the shade holds one entry and nothing flickers; the last surah of a batch keeps the batch line.
A surah downloading on its own keeps its own line, "Al-Baqarah · 9.3 of 58.2 MB". The separate,
non-ongoing summary is gone (a process start still sweeps the id range it used, once, for a
summary a 0.19.0 batch may have left standing). When the last worker finishes, WorkManager takes
the notification down with the foreground service, as before. iOS shows no download
notifications and is untouched.

### 16.4 Picking a voice never starts or resumes a recitation

**Ask.** Choosing a reciter from Settings resumed a paused recitation, or one that had been
left alone. A pick should change the voice; only a recitation that is playing should go on
playing, in the new voice.

**Decision.** §14.4's "the same voice, paused — resume it" is withdrawn. A pick persists the
choice and then: nothing loaded, nothing happens; the same voice, nothing happens; another
voice with the surah on the phone swaps at the current ayah and keeps the reader's state —
playing stays playing, paused stays paused; another voice without the surah is offered or
fetched as before, and takes over in the state the old voice was left in. The one resume left
is the preview's: a recitation the *audition* paused gets its resume back on the pick, because
the reader was listening before the clip.

### 16.5 Prayer times that follow a journey while the app is closed

**Ask** (15 September, from the iPhone). Three hundred kilometres from home, the adhan kept
coming ten minutes early for a day; opening the app fixed it.

**Cause.** The stored location was refreshed from GPS on two triggers only: the app coming to
the foreground and the timezone changing. Every background wake-up that rebuilt the plan — the
iOS refresh task, Android's prayer alarm and the twelve-hourly top-up — planned against the
stored coordinates. On iOS the notifications are local notifications armed days ahead, so with
no code running nothing could move them; on Android the alarm receiver only rebuilt the plan
when the window had drained. Three hundred kilometres east or west is about twelve minutes.

**Decision.** The background wake-ups now read the position the phone *last knew* — no fix is
asked for, nothing lights up, and a closed app under "While Using" may not be allowed a fix
anyway — and when it is more than the 5 km recompute distance from the stored place, the
location is re-resolved and the plan rebuilt for it. A manually picked city is never touched,
as before. Android's prayer alarm rebuilds the plan when the phone has moved even if the window
is full, so a journey is caught by the next prayer; iOS's refresh task is requested six hours
out instead of a day, and catches a journey whenever iOS chooses to run it — usually within a
day for an app used daily, never on a phone that never opens it. Significant-change monitoring
under "Always" location, which would wake the app on every cell change, is the proper answer
for iOS and stays a later, opt-in decision.

**Verified.** Unit tests for the four triggers, the manual city and `hasMoved`. Simulator:
app open at London, `simctl location set` to Cairo, then the harness's `refresh` ran the
background task's body and Settings › Location read a Cairo district with no fix asked for. The
emulator could not stand in for Android (its coarse providers never produce a fix), so the
LoopPhone ran the debug harness's `location` command: with the process still alive after a
recent foreground use, the cache answers and the alarm-time refresh works; from a cold process,
or one never opened since install, Android 16 withholds the cache from a "while in use"
permission (`lastKnown=null`) and the stored place stands. So on Android the fix covers the day
of the journey while the app has been used recently, and a fully closed app still waits for its
next open — closing that needs `ACCESS_BACKGROUND_LOCATION`, a permission Play reviews hard,
and is the same opt-in decision as iOS's "Always". The task's own result also changed: it used to report failure whenever the plan came out empty, which is every reader with
notifications off, and iOS grants a task that keeps failing less often; it now reports whether
the work ran.

## 17. Round five — 18 September (the closed test's first feedback, after 1.0.0 (28))

Five testers on Play's closed track sent six things. None of them is about recitation alone, but
this is the living spec, so they are recorded here.

### 17.1 A preview that made no sound

A tester reported that the play buttons in the notification-sound sheet did nothing. They work:
the preview plays with `USAGE_NOTIFICATION_EVENT` on purpose, so that it is heard at exactly the
loudness the real notification will have, and his phone was on silent. The honest preview stays.
What changed is that the sheet says why it is quiet, and **only when it is**: an accent-coloured
line under the options while `notificationSoundsMuted()` is true — the ringer on silent or
vibrate, the notification volume at zero, or Do Not Disturb filtering. It is polled every 1.5 s
while the sheet is open, so it goes the moment the volume key is pressed. A permanent hint was
rejected outright: a line that is always there is a line nobody reads. iOS gives an app no way
to read the mute switch, so it answers false there and shows nothing.

### 17.2 Widgets that would not widen

`widget_small_info.xml` and `widget_medium_info.xml` carried `maxResizeWidth`/`Height` of 250 dp.
On a phone whose four cells are wider than that (most of them) the launcher refused any widening
at all, which a tester filmed. The ayah widget already had `0dp`, no cap, and the Glance layout
has always chosen its form from `LocalSize`; the prayer widgets now have `0dp` too. At full
width the small widget becomes the two-column card.

### 17.3 What an Arabic hit means

An Arabic search reads the ayah table alone, so its rows showed the verse and no translation.
`QuranRootViewModel.withTranslations` fills the reader's translation in afterwards, one
`translationTexts` read per surah touched. Not under an Arabic interface: whoever reads the
Arabic line needs no second one.

### 17.4 Lighting the matched words — and a search that had been half blind

The brief was "with care; if it breaks letter joining, leave it out". Two decisions follow from
that. **Colour only** — the accent, no weight change, which would re-measure the line. And
**whole words**: the search matches a substring («رحمن» finds «ٱلرَّحْمَـٰنِ») but a style boundary
inside an Arabic word is a shaping boundary on some engines, so the span goes round the whole
word and a boundary only ever falls on a space. Checked close up on Android (Minikin) and iOS
(Skia): the lit word joins exactly like its neighbours.

Finding the word was the real work. The search runs over `ayah.text_search`, Tanzil's plain
text, and the row shows `text_uthmani`; they are different *spellings*, not one text with and
without marks — «العالمين»/«ٱلْعَـٰلَمِينَ», «الصلاة»/«ٱلصَّلَوٰةَ», two words «يا أيها» for one
«يَـٰٓأَيُّهَا». No fold of one gives the other (a fold-only matcher missed 27 % of words), so
`ArabicWordAlignment` walks the two texts together on a skeleton — marks, every alef, hamza, waw
and yaa, and doubled letters removed — joining runs of up to three words where the orthographies
divide differently, and resynchronising one word ahead where even the skeletons disagree
(«وَيَبْصُۜطُ»/«ويبسط»). `ArabicSearchDbTest` holds all 6,236 ayahs to walking to the end of
both texts together; an ayah that did not would light nothing rather than the wrong word.
`SearchHit.matchedWords` carries the positions and `highlightArabicWords` is pure formatting.
Because the row is one line cut at its end, a match more than two words in opens the line two
words before it, behind an ellipsis.

Measuring that turned up a bug shipped in 1.0.0: `text_search` keeps «أ إ آ ى» as Tanzil
publishes them, the query was folded to «ا ي», and the row was matched **unfolded** — so any
query containing one of those letters («إياك», «موسى», «على», «أنزل») found nothing at all. The
row is now folded by the same rule as the query, once per process.

### 17.5 A tap on the line

The bar's line takes a tap and nothing about it looks different: the 3 dp line sits inside an
invisible box the height of the clock row and exactly as wide as the line, measured from the
reading edge (the right one under an Arabic UI). `RecitationController.seekToFraction` turns the
fraction into surah time and `RecitationPlayer.seekToSurahTime` does what the lock screen's
scrub already did — it lands on the **start of the ayah** holding that moment
(`SurahTimeline.snapToAyah`, §14.1), never inside a word. On Android the seek travels through
the media session to `AyahPlayer.seekTo`, the one place that rule already lived. The box also
carries progress semantics with `setProgress`, so a screen reader can move it.

### 17.6 Tahajjud, quietly

Optional, off by default, and nowhere but Settings › Notifications: a card below the five
prayers with a toggle ("When the last third of the night begins") and, only while it is on, a
sound row offering Silent, Notification and Takbir — not the adhan, which is not called for a
night prayer. It is not on the timeline or in the widgets.

The night runs from Maghrib to the Fajr that follows; the last third opens two thirds of the way
through (`NightThirds`). The planner adds one `NotificationKind.TAHAJJUD` entry a night, carrying
`Prayer.FAJR` (the clock time its body quotes), planned only strictly inside the night so that
crossed high-latitude times schedule nothing. It costs one slot a day: the iOS window goes from
twelve days to ten, or six to five with reminders. On Android it has channels of its own
(`tahajjud_<sound>`, named "Tahajjud · Notification"), because the system's channel list is
where a person silences one kind of notification and keeps another; the alarm intent carries the
kind, and an alarm set by an older build reads as a prayer. Its channels are swept on every
plan that does not use them, so switching it off takes its entry out of system settings too. The body names Fajr by its one name
in the language, not the paired «Fajr · الفجر» a title wears. Verified by firing one through the
real scheduler, alarm and receiver on the emulator (debug harness `tahajjud`), and by reading
iOS's pending requests on the simulator (`taqwa://recite/pending?prefix=TAHAJJUD`: nine, inside
a total of 57).

### 17.7 Two small things the morning after (19 September, build 30)

A row's subtitle sat directly under its label, line box on line box; `RowSubtitleGap` (4 dp) now
separates them in `TaqwaRow` and in the sound and voice sheets' rows, so every two-line row in
the app breathes the same. And a Quran search now belongs to one visit to the tab: it still
survives the walk into a hit and back (spec 2b §2.1), which never leaves the tab, but going to
Prayer or Settings clears it, so coming back shows the surah list and not an old result.

## 18. A tester's phone restarted — 21 September (1.0.0 (30) on Android 16)

A tester's LoopDL loopTwo (Android 16) restarted itself half an hour after Taqwa was installed,
and the report blamed a notification flood and bitmap payloads from this app. It was right about
both, though not about which notification: the device's own log and crash record, read over adb,
show two separate faults, thirteen minutes apart.

### 18.1 The download notification was posted on every progress event

Between 20:00 and 20:13 Android shed Taqwa's notifications 115 times for exceeding five posts a
second, and every one was id 770000 — the download notification of §16.3, not the media one the
report named. `DownloadLoop` reported progress "every 250 ms or 256 KB, whichever comes first",
which on good Wi-Fi is the bytes, many times a second; `SurahDownloadWorker` answered every
report with `setForeground`, which WorkManager turns into two enqueues (`startForeground` and
`notify`); and two workers at a time did this into one id. In a batch the line reads "12 of 114
surahs" for minutes, so almost all of it said nothing new. Reproduced on the Android 16
emulator with the old build: 174 posts in 40 s, shed 7 times.

Four changes. The loop reports on time alone (every 500 ms; the part is still flushed every
256 KB, since that is what a resume continues from). Every worker's progress goes through one
`PostGate` for the process: post only when what the shade shows has changed, and at most once
every two seconds across all workers — two, because one `setForeground` is three enqueues. A
worker announces itself at its start only when no other worker is holding the notification, or
when its surah is 8 MB or more and may need the foreground for itself; a short surah fits
inside the ten minutes WorkManager gives a plain worker. And the refusals — no room, mobile
data not allowed — are checked **before** the worker says anything: when the emulator's disk
filled, forty queued workers each posted "downloading", failed a millisecond later and handed
over, 76 posts in one second.

Measured on the emulator through a proxy on the Mac, a far faster link than a phone's (a whole
reciter, 584 MB, in 84 s): the old build shed 7 times in 40 s and posted continuously; the new
one posts about twice a second in all, nearly all of it WorkManager's own three enqueues when a
worker takes the foreground over from one that finished, and was shed 6 times in the whole
run, each a duplicate of the same line. The full-disk burst went from 76 posts in a second to
8. At the tester's pace — a surah every seven seconds — the hand-overs are far apart and nothing
is near the limit. **What would make it zero on any link** is structural and not done: one
long-lived worker that owns the notification for the whole batch, with the surah workers
posting nothing, in place of one foreground worker per surah.

### 18.2 Every queue item carried the app icon as a bitmap

At 20:18:32 playback began; the log has `Dead object in setQueue … running out of binder buffer`
from our process, and three seconds later `system_server` died in
`MediaSessionRecord.pushQueueUpdate` → `QueueItem.writeToParcel` → `Bitmap.writeToParcel` with
"Could not write bitmap blob file descriptor", which restarted the device.

§12 put the launcher icon on every `MediaItem` as `artworkData`, 30 KB of PNG, reasoning that one
array shared by every item never crosses the binder. That missed what Media3 does next: it
mirrors the queue into the *platform* session, and for every item with `artworkData` it decodes
the PNG and attaches the **bitmap** to the platform `QueueItem`
(`MediaSessionLegacyStub.updateQueue`, read in the 1.11.1 sources). 512 × 512 ARGB is a
megabyte; Al-Baqarah's queue is 571 items; each queue change pushed that to every controller —
SystemUI, the phone's own now-playing surface — as one ashmem file descriptor per item.
Android should not let an app's payload take `system_server` down, but the payload was ours.

Two changes, either of which alone prevents it. The artwork is written once to a PNG in the
cache directory and named on each item by **URI** (`AppIconArtwork.uri`); the one bitmap the
notification and the lock screen need is loaded from it, for the item playing. It is a
`content://` URI served by `ArtworkProvider` — read-only, exported, one fixed path, the icon
every launcher already shows — because the first attempt, a `file://` path into the cache, was
published to SystemUI as the art URI and SystemUI logged a `FileNotFoundException` on every
metadata update: it draws the lock screen from another process and may not read our cache. And the platform
session publishes **no queue at all**: the media notification controller's commands are what
Media3 gives the platform session, and `onConnect` withholds `COMMAND_GET_TIMELINE` from it.
A surah's queue is its ayahs and the silences between them, hundreds of rows that all read
"Al-Baqarah" — nothing a car, a watch or the system's player can use. The app's own controller
keeps the timeline; it is how the bar knows which ayah is playing.

Verified on the Android 16 emulator with Al-Baqarah, the 571-item case: `dumpsys media_session`
reports `queue size=0`; no `Dead object in setQueue`, no `TransactionTooLarge`, no bitmap-blob
error; SystemUI logs no load failure; the media notification still carries its large icon and
was posted 12 times in 30 s; `content read` as the shell uid gets the PNG and any other path
gets `FileNotFoundException`. Not seen with eyes: the emulator's screen capture returned blank
frames all session, so the artwork on the lock screen is confirmed by those dumps, not a picture.

## 19. The Mushaf page on a short screen — 21 September (1.0.0 (32))

On a tester's loopTwo the last four lines of a full Mushaf page were drawn on top of each other.
The phone is 1021 × 1900 px at 400 dpi — 408 × 760 dp, 1.86 : 1 where every device this was
built on is 2.1–2.2 : 1 — with a three-button navigation bar, and the player bar was up.

The page's font size came from the frame's **width** alone (`fittedSize`), every row was given a
fixed 1.9× that size, and nothing asked whether fifteen of them fit the frame's **height**. There
they needed about 608 dp and had 474. A `Column` measures its children in order against what is
left, so the last rows were handed almost nothing and their text, which a `Canvas` does not clip,
landed on the rows above. Reproduced exactly on the emulator set to the phone's size, density
and navigation mode (`wm size 1021x1900`, `wm density 400`, the three-button overlay).

`fitToHeight`, a pure rule beside `fittedSize`, now holds the page to the height as well, in three
steps, each taken only if the one before is not enough: keep the size and the 1.9× line box;
keep the size and close the lines up, no tighter than 1.6× (the printed page is set closer than
1.9×, and the reader loses nothing but air); then shrink the size in the same half-steps until
the rows fit at the tightest box. Below 14 sp the page scrolls at its width-fitted size instead,
as it already does sideways. Surah bands (a fixed 40 dp) and the gaps pages 1–2 keep are taken
out of the room first, and a pixel a row is held back for rounding. The frame's height is only
known where the frame is laid out, so the page is now measured inside a `BoxWithConstraints` on
the frame itself; sideways the height is no limit and the width alone sets the page, as before.

Checked on the emulator at the loopTwo's shape (page 3 with the bar up, light and dark; page
604 with its three bands and three basmalas; pages 77 and 187), at the S23's shape (unchanged
without the player bar; with it the lines close a little — by the arithmetic that case was
already a few dp short before), in landscape (scrolls, unchanged), and on the iPhone 17 Pro
simulator (unchanged without the bar, closed up with it). Not checked: a short iPhone — the
iPhone SE simulator needs a tap this session had no permission to make — though the rule and
the composable are the same code on both platforms. Noted and not changed: while an ayah is
selected its action pill straddles the frame's bottom edge, and on a page with no slack it
covers most of the fifteenth line until it is dismissed.
