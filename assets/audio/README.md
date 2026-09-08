# Notification audio

**Status: placeholder for development. Revisit before release.**

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

Taqwa's own sound for the **Notification** level, so a prayer never sounds like a message
arriving. Synthesised, not recorded: an ascending three-note bell motif (A4, C#5, E5, 260 ms
apart), each note five inharmonic partials with long independent decays and a hair of detune for
warmth, struck a second time at 2.60 s and 0.85 of the level, 6.00 s, peak −1 dBFS. Original work,
no third-party material, no licence conditions.

Regenerate with `python3 tools/make-chime.py`, which writes all three files and copies them into
the platform trees. It replaces a 2.40 s chime at −5 dBFS that was too quiet and too brief to be
noticed at a desk; the Notification channel now vibrates alongside it on Android, and the channel
id gained a `_chime2` suffix because a channel's sound is immutable once created.
`.ogg` (Vorbis q6) for Android, `.caf` (IMA4 ADPCM, like the other clips) for iOS.

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

## Two findings that affect the design

**A 6-second takbir is not possible with a melodic adhan.** The spec originally assumed ~6s. In this recording the first complete "Allahu akbar, Allahu akbar" pair takes 15.8 seconds, and cutting at 6s truncates the muezzin mid-word. A genuinely 6-second takbir would need a plainly recited, non-melodic source — a different recording, not a different edit.

**A 30-second window cannot reach the shahada.** At this pace, 30 seconds covers the four opening takbirs and stops there. Reaching "Ashhadu an la ilaha illa Allah" would need roughly 45 seconds, which exceeds what either platform allows for a notification sound. So the two options differ in length rather than in content: two takbirs versus four.

Both are acceptable for development. If the distinction matters at release, source a faster-paced recording — or accept the difference and label it plainly.

## Before release

- [ ] Decide whether a placeholder CC0 recording is appropriate, or whether a specific muezzin should be chosen deliberately
- [ ] Independently verify the licence rather than trusting this file
- [ ] Consider offering a choice of muezzin, as the established apps do
