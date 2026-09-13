# Notification audio

**Status:** three voices since 0.9.0 (12 September 2026). The original is the default; two alternatives are offered under Settings, Notifications, Adhan.

## Source

`adhan-source-cc0.ogg` — "Beautiful adhan" by Adam-synagda, from Wikimedia Commons.

- **Licence:** CC0 1.0 Universal Public Domain Dedication
- **Attribution required:** No
- **Commercial use and redistribution:** Permitted
- **Page:** https://commons.wikimedia.org/wiki/File:Beautiful_adhan.ogg
- **Duration:** 2:34, Ogg Vorbis, stereo 44.1 kHz
- **Retrieved:** 2026-09-06

Chosen because it is a CC0 dedication by the person who made the recording. That is the only kind of free licence worth relying on here.

### Rejected candidate

A CC0-labelled adhan on Freesound (`sonically_sound/sounds/639494`) was rejected. Its own description states it was extracted from YouTube. Someone who did not create a recording cannot validly dedicate it to the public domain, so the CC0 label carries no weight. A licence is only as good as the uploader's right to grant it.

## The chime (`chime.*`)

The **Notification** level's tone, so a prayer never sounds like a message arriving: "Clear
announce tones" from Mixkit (https://mixkit.co/free-sound-effects/tones/), Mixkit Sound Effects
Free Licence, which permits use in apps without attribution but not redistribution of the clip on
its own. Mohamed picked it on 8 September 2026 over the synthesised bell motif that preceded it
(and the softer chime before that). The source WAV is not kept in the repository (its licence forbids redistributing it on its own; see `source/README.md`); `tools/prepare-chime.py`
folds it to mono, trims the silence, fades the tail, normalises the peak to −1 dBFS (3.79 s) and
writes `.ogg` (Vorbis q6) for Android and `.caf` (IMA4 ADPCM, like the other clips) for iOS, copying
both into the platform trees. Android's Notification channel id suffix is `_chime3` (a channel keeps
the sound it was created with, so every change of this file bumps the suffix and retires the old id);
that channel also vibrates.

## Derived clips

Cut on silence-detected phrase boundaries, not arbitrary timestamps, so no clip ends mid-word.

| File | Content | Duration |
|---|---|---|
| `takbir.*` | One complete "Allahu akbar, Allahu akbar" pair | 15.80s |
| `adhan-30s.*` | The full four-takbir opening sequence | 29.95s |
| `adhan-full.*` | The complete recording, loudness-matched; **preview only** (the sound sheet's play button), never a notification sound. `.ogg` for Android, AAC `.m4a` for iOS | 154s |

`.ogg` for Android, `.caf` (IMA4 ADPCM) for iOS. Mono 44.1 kHz, loudness-normalised to −16 LUFS with a −1.5 dBTP ceiling so neither clip is jarring at night.

Both sit under the 30-second cap that both platforms enforce on notification sounds.

### Reproduce

```bash
ffmpeg -i adhan-source-cc0.ogg -ss 0.893 -to 16.70 \
  -af "afade=t=out:st=15.3:d=0.5,loudnorm=I=-16:TP=-1.5:LRA=11" \
  -ac 1 -ar 44100 -c:a libvorbis -q:a 4 takbir.ogg

ffmpeg -i adhan-source-cc0.ogg -ss 0.893 -to 30.85 \
  -af "afade=t=out:st=29.3:d=0.6,loudnorm=I=-16:TP=-1.5:LRA=11" \
  -ac 1 -ar 44100 -c:a libvorbis -q:a 4 adhan-30s.ogg
```

Swap `-c:a libvorbis -q:a 4` for `-c:a adpcm_ima_qt` and the `.caf` extension to produce the iOS variants.

## The other two voices (12 September 2026)

Chosen from sixteen candidates in `.superpowers/sdd/adhan-research.md`. Every candidate was
measured, and the three finalists were transcribed with word timestamps (Whisper large-v3-turbo,
Arabic) so that phrase structure and cut points rest on something better than a description. The
transcripts are in the research report.

### Aaqib Azeez (`*_azeez.*`) — CC BY-SA 4.0

- **Source:** "The Adhan – Muslim Call to Prayer – Aaqib Azeez", uploaded by Atcovi to Wikimedia
  Commons, 2020-12-23, own work; used as the Sunni example on the English Wikipedia *Adhan* article.
- **Page:** https://commons.wikimedia.org/wiki/File:The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3
- **Licence:** Creative Commons Attribution-ShareAlike 4.0, https://creativecommons.org/licenses/by-sa/4.0/
- **Attribution required:** yes. **Share-alike:** the clips below are modified (trimmed, faded,
  loudness-normalised, limited) and are themselves released under CC BY-SA 4.0.
- **Source file:** MP3 134 kbps, 44.1 kHz stereo, 1:27, plain unmelismatic delivery, peak −0.1 dBFS.
- **Transcript check:** complete 15-phrase Sunni adhan; first "Allahu akbar, Allahu akbar" pair
  0.61–5.43 s; four takbirs end 11.11 s; both shahadas end 26.66 s.

### Besim Azemi, Malmö Mosque (`*_azemi.*`) — CC BY 3.0

- **Source:** "Eid al-Fitr Fajr azan at Malmö Mosque – 19 August 2012", published by Islamic Center
  Malmö on its own YouTube channel under YouTube's CC BY licence and imported to Wikimedia Commons,
  where a licence reviewer confirmed the CC BY at source on 2020-02-12 (archive snapshot on the file
  page). Muezzin named in the description: Besim Azemi. Live in the mosque.
- **Page:** https://commons.wikimedia.org/wiki/File:Eid_al-Fitr_Fajr_azan_at_Malm%C3%B6_Mosque_-_19_August_2012.webm
- **Licence:** Creative Commons Attribution 3.0 Unported, https://creativecommons.org/licenses/by/3.0/
- **Attribution required:** yes. No share-alike.
- **Source file:** Opus 134 kbps in WebM, 48 kHz stereo, 4:07 including a lead-in and the muezzin's
  salawat after the adhan.
- **Modification that matters:** the recording is a Fajr adhan. To serve as a general voice the two
  *as-salatu khayrun min an-nawm* phrases (183.3–202.4 s of the source, located by transcript and
  silence detection) are removed with a 0.6 s crossfade inside the 2 s room-tone pauses on either
  side (181.2–183.3 s and 202.4–204.1 s), and the recording ends before the salawat (fade from
  209.4 s of the edited timeline). What remains is the standard 15-phrase adhan.
- **Transcript check:** first takbir pair 0–3.67 s; four takbirs end 23.29 s; la ilaha illallah ends
  ~231 s of the source.

### Pipeline for both

Peak-limited as well as loudness-matched, because the Azeez source already sits at full scale and
the +4 dB it needs would otherwise clip; the limiter runs after the resample back to 44.1 kHz,
since `loudnorm` works at 192 kHz internally and a limiter placed before the resample let the
Vorbis encode overshoot to +0.2 dBTP.

```bash
LN="loudnorm=I=-16:TP=-2:LRA=11,aresample=44100,alimiter=limit=0.63:attack=5:release=80:level=false"
ENC="-ac 1 -ar 44100 -c:a libvorbis -q:a 4"

# Aaqib Azeez
ffmpeg -i source-azeez.mp3 -ss 0.35 -to 5.95  -af "afade=t=out:st=5.1:d=0.5,$LN"   $ENC takbir_azeez.ogg
ffmpeg -i source-azeez.mp3 -ss 0.35 -to 27.9  -af "afade=t=out:st=26.9:d=0.65,$LN" $ENC adhan_azeez_30s.ogg
ffmpeg -i source-azeez.mp3 -ss 0.35 -to 86.5  -af "$LN"                            $ENC adhan_azeez_full.ogg

# Besim Azemi: audio extracted from the WebM from 4.9 s (ffmpeg -ss 4.9 -vn -c:a flac), then
ffmpeg -i source-azemi.flac -filter_complex \
  "[0:a]atrim=0:182.25,asetpts=N/SR/TB[a];[0:a]atrim=203.25:233.0,asetpts=N/SR/TB[b];[a][b]acrossfade=d=0.6:c1=tri:c2=tri" \
  -c:a flac spliced-azemi.flac
ffmpeg -i spliced-azemi.flac -af "afade=t=out:st=209.4:d=1.5,$LN"                  $ENC adhan_azemi_full.ogg
ffmpeg -i source-azemi.flac -to 24.7 -af "afade=t=out:st=23.9:d=0.8,$LN"          $ENC adhan_azemi_30s.ogg
ffmpeg -i source-azemi.flac -to 5.2  -af "afade=t=out:st=4.5:d=0.7,$LN"           $ENC takbir_azemi.ogg

# iOS: .caf IMA4 for the two notification clips, AAC 64 kbps .m4a for the full preview
ffmpeg -i X.ogg -c:a adpcm_ima_qt X.caf
ffmpeg -i adhan_V_full.ogg -c:a aac -b:a 64k adhan_V_full.m4a
```

| Clip | Duration | Integrated | True peak |
|---|---|---|---|
| `takbir_azeez` | 5.60 s | −16.3 LUFS | −3.7 dBTP |
| `adhan_azeez_30s` (four takbirs + both shahadas) | 27.55 s | −15.9 LUFS | −3.7 dBTP |
| `adhan_azeez_full` | 86.15 s | −16.3 LUFS | −3.4 dBTP |
| `takbir_azemi` | 5.20 s | −16.9 LUFS | −3.7 dBTP |
| `adhan_azemi_30s` (four takbirs) | 24.70 s | −15.5 LUFS | −3.6 dBTP |
| `adhan_azemi_full` | 211.40 s | −15.9 LUFS | −3.6 dBTP |

The sources are not kept in the repository (1.4 MB and 4.2 MB); both pages above are the record.

### Rejected this round, for the record

- "Call to prayer from the Prophet's Mosque" (Commons, CC BY 3.0 via Freesound): still carries the ID3
  tags of the commercial album it was ripped from. Not redistributable whatever the tag says.
- "Call to prayer by Sabah Fakhry" (Commons, "public domain"): source field says YouTube.
- "AzaanMaahur" (Commons, CC BY-SA 4.0, performer's own upload): the cleanest recording found, but the
  Shia form of the adhan. Kept in mind only for an explicitly labelled option.
- "Azan.ogg" by Andrewler (Commons, CC BY-SA 4.0): a complete adhan in the same style as the original,
  but forensically indistinguishable from it in codec, loudness and floor, so it adds no character and
  doubles the same provenance question. The fallback if either voice above fails by ear.
- Everything on Freesound and archive.org (field ambience or self-applied "public domain" marks) and
  the aladhan.com CDN (famous muezzins, no licence statement at all).

## Two findings that affect the design

**A 6-second takbir is not possible with a melodic adhan.** The spec originally assumed ~6s. In this recording the first complete "Allahu akbar, Allahu akbar" pair takes 15.8 seconds, and cutting at 6s truncates the muezzin mid-word. A genuinely 6-second takbir would need a plainly recited, non-melodic source — a different recording, not a different edit.

**A 30-second window cannot reach the shahada.** At this pace, 30 seconds covers the four opening takbirs and stops there. Reaching "Ashhadu an la ilaha illa Allah" would need roughly 45 seconds, which exceeds what either platform allows for a notification sound. So the two options differ in length rather than in content: two takbirs versus four.

Both are acceptable for development. If the distinction matters at release, source a faster-paced recording — or accept the difference and label it plainly.

## Before release

- [x] Offer a choice of muezzin (12 September 2026, above)
- [ ] The original file's CC0 rests on an anonymous "own work" declaration with no muezzin or mosque named; the two new voices each name a person or an organisation. If the original is ever challenged, Aaqib Azeez becomes the default.
