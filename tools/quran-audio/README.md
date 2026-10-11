# Quran-audio pipeline

Mirrors the Islamic Network per-ayah recitation corpus, verifies it, packs one `.taqa` container
per (reciter, surah) with the MP3 bytes untouched, and publishes the containers on the
`Taqwa-data` GitHub releases. Each script's docstring says what it does and how to run it;
`common.py` holds the shared configuration, including the cache root `WORK`. Its default is a
scratch path from an earlier session that no longer exists, so always set `TAQWA_AUDIO_WORK` to a
scratch directory of your own, outside the repository (the four adhkar containers below are
about 59 MB together, almost all of it Al-Baqarah), for example:

    TAQWA_AUDIO_WORK=$HOME/taqwa-audio-work python3 -I adhkar_clips.py

## Adhkar first-run clips

The four Quranic items of the Morning & Evening Adhkar (2:255 and Surahs 112, 113, 114) are
bundled in Alafasy's voice under
`shared/src/commonMain/composeResources/files/adhkar/clips/ar.alafasy/` so the first session
has sound before anything is downloaded. To regenerate them, run `python3 -I adhkar_clips.py`
from this folder: it looks for `ar.alafasy-002.taqa`, `-112`, `-113` and `-114` in
`<WORK>/containers/ar.alafasy/`, downloads any that are missing from the release the shipped
`manifest.json` names (into that cache, never the repository), refuses a container whose size
or SHA-256 differs from the manifest, and writes each clip as the raw bytes of the wanted
ayahs in order, with no re-encoding. The output is a pure function of the containers, so a
re-run changes nothing. `python3 -I check_adhkar_clips.py` re-derives every clip from the
containers with its own reader, compares byte for byte, cross-checks the decoded length with
ffmpeg and exits 1 on any difference. Provenance, containers and byte ranges are recorded in
`docs/licences/Alafasy-adhkar-clips.txt`; if a clip is ever re-cut, update the sizes and
hashes there from the script's output.
