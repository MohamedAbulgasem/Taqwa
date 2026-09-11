# Taqwa: a choice of adhan

Status: **decided 12 September 2026** by Claude on Mohamed's brief ("find two more great redistributable adhan options; keep the current one as default; a setting"). He tests the result in the morning.

## 1. Goal

One new setting, **Adhan**, on the Notifications screen: which recording the Takbir and Adhan sound levels use, for every prayer. Three voices; the one shipped since 0.1.0 stays the default and nothing changes for anyone who never opens the setting.

Non-goals: per-prayer voices (one voice for all five, like every established app's default arrangement); downloading voices at runtime (the app is offline and has no server); changing the four sound levels or the chime.

## 2. The recordings

Sourcing is in `.superpowers/sdd/adhan-research.md` (16 candidates, measured not heard). The bar: a licence granted by someone who plausibly holds the rights, a complete Sunni-form adhan, a solo voice that survives a phone speaker. That bar eliminated every Freesound and archive.org hit, the aladhan.com CDN (no licence at all), and two Commons files that carry the ID3 tags of the albums they were ripped from.

| id | Shown as | Source | Licence | Character |
|---|---|---|---|---|
| `ORIGINAL` | Original / الأصلي | "Beautiful adhan", Adam-synagda, Wikimedia Commons | CC0 | melismatic, 2:34, the default |
| `AZEEZ` | Aaqib Azeez / عاقب عزيز | "The Adhan – Muslim Call to Prayer – Aaqib Azeez", Wikimedia Commons | CC BY-SA 4.0 | plain and brisk, 1:27; the only other complete general adhan with a named reciter |
| `AZEMI` | Besim Azemi / بسيم عظمي | "Eid al-Fitr Fajr azan at Malmö Mosque", Islamic Center Malmö, via Commons (YouTube CC review 2020) | CC BY 3.0 | live Balkan maqam, ~4:00; the only licence audited end to end |

**Amendment rule for the third voice.** The Malmö recording is a Fajr adhan and contains *as-salatu khayrun min an-nawm* twice. It ships as a general voice only if that pair of phrases can be cut on clean phrase boundaries, verified against a speech-recognition transcript with word timestamps and by silence detection; otherwise the third voice is "Azan.ogg" by Andrewler (CC BY-SA 4.0, 3:03, same style and same provenance profile as the original) under id `ANDREWLER`, and this table is amended.

Every voice yields the same three clips as the original, produced by the pipeline in `assets/audio/README.md`: `takbir` (one complete "Allahu akbar, Allahu akbar" pair), `adhan-30s` (as much of the opening as fits under the 30 s notification cap, cut on a phrase boundary), `adhan-full` (preview only). All loudness-matched to −16 LUFS, −1.5 dBTP, mono 44.1 kHz; `.ogg` for Android, `.caf` IMA4 for the two notification clips on iOS and AAC `.m4a` for the full preview. Names: `adhan_<id>_30s`, `adhan_<id>_full`, `takbir_<id>` on Android (`adhan-<id>-30s.caf`, `adhan-<id>-full.m4a`, `takbir-<id>.caf` on iOS); the original keeps its existing file names.

Licences: CC BY-SA 4.0 obliges credit, a licence link, and releasing the trimmed clips under the same licence, which `assets/audio/README.md` states per file; CC BY 3.0 obliges credit only. Attribution goes in the app's Attribution screen (one line per voice), `docs/ATTRIBUTION.md` and the README.

## 3. Model and persistence

`domain/AdhanVoice` enum in the order above, `DEFAULT = ORIGINAL`. `NotificationSettings.voice: AdhanVoice = AdhanVoice.ORIGINAL`, persisted under `adhan_voice` in DataStore, read with the same `toEnumOr` fallback every other enum uses, so an absent or unknown value is the original.

`SoundAssets` becomes keyed on `(sound, voice)`: `androidRawResourceName(sound, voice)`, `iosResourceFileName(sound, voice)`, the two preview variants, and `duration(sound, voice)` with the measured length of every clip. SILENT and NOTIFICATION ignore the voice. The sound sheet's subtitles read their seconds from `SoundAssets.duration` instead of the private constants.

## 4. Scheduling

`ScheduledNotification.voice: AdhanVoice`. The planner copies the setting onto every entry. Android: the channel id encodes the voice because a channel's sound is immutable: `channelId(prayer, sound, voice)` appends `_<voice>` for TAKBIR and ADHAN when the voice is not the original, so existing channels keep their ids and nothing churns on upgrade; `allChannelIdsFor` enumerates every voice so `deleteStaleChannels` still sweeps; the receiver reads the voice from a new `EXTRA_VOICE`, defaulting to the original if absent (an alarm scheduled before the upgrade). `LocalizedNotificationCopy.channelName` is unchanged. iOS: `soundFor(sound, voice)`.

Changing the voice reschedules, like changing a sound does.

## 5. The screen

Notifications screen, a card between "Remind me before" and the per-prayer card, one row **Adhan** / «الأذان» whose value is the voice's name. It opens a sheet titled the same, laid out like the sound sheet: one row per voice with its name, a caption (reciter or source and length, e.g. "Plain and brisk · 1:27"), a play button that plays that voice's **complete** adhan through `SoundPreviewPlayer.play(sound = ADHAN, voice)`, and a radio. Picking closes the sheet; closing stops the preview.

The sound sheet's play buttons for Takbir and Adhan play the currently chosen voice. The footnote is unchanged.

## 6. Tests

`commonTest`: default voice when the key is absent or garbage; channel ids are unchanged for the original voice and unique per (prayer, sound, voice) otherwise; `allChannelIdsFor` contains every id `channelId` can produce; `SoundAssets` returns a name and a duration for every (sound, voice) except SILENT and every notification clip is ≤ 30 s; the planner stamps the voice on prayer entries and on reminders; `NotificationSettings` round-trips through the repository with the voice.

Device: emulator sheet screenshots (English and Arabic), each preview audible (logcat shows MediaPlayer start), a scheduled notification on the S23 for a non-original voice posts on the new channel and plays it.

## 7. Release

Ships as **0.9.0 (11)** after Mohamed has listened to all three on the phone.
