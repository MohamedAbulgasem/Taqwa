# Quran-audio pipeline

Mirrors the Islamic Network per-ayah recitation corpus, verifies it, packs one `.taqa` container
per (reciter, surah) with the MP3 bytes untouched, and publishes the containers on the
`Taqwa-data` GitHub releases. Each script's docstring says what it does and how to run it;
`common.py` holds the shared configuration, including the cache root `WORK`. Its default is a
scratch path from an earlier session that no longer exists, so always set `TAQWA_AUDIO_WORK` to a
scratch directory of your own, outside the repository (the four adhkar containers below are
about 59 MB together, almost all of it Al-Baqarah), for example:

    TAQWA_AUDIO_WORK=$HOME/taqwa-audio-work python3 -I adhkar_clips.py

## A published release is never changed

Every installed app holds the `manifest.json` it was built with, and refuses a fetched manifest
that moves `base` or gives a release it shipped (the same reciter id and release name) any other
size or SHA-256 for any surah (security review, 26 October 2026). So once a release has been
published, its containers stay exactly as they are. Changed audio for a reciter, even one surah
repaired, goes out under a **new release name**: add the reciter to `RELEASES` in `common.py`
(for example `"ar.alafasy": "audio-ar.alafasy-v2"`), which `publish.py`, `manifest.py` and
`status.py` all read, remove `<WORK>/state/<id>.done`, then run `publish.py` and `finalize.py` as
for a new reciter. Never reuse a release name, including one a withdrawn reciter used: older
builds still carry its hashes.

Two guards hold to this. Before it creates or uploads anything, `publish.py` refuses a reciter
whose release is already in a published manifest (the Taqwa-data clone's `manifest.json` or the
app's own copy, the two `finalize.py` writes) if any container on disk differs from it in size or
SHA-256, because `--clobber` would replace a file that apps then refuse as corrupted. As a second
signal, for a release no manifest names yet, it also refuses when `state/<id>.done` exists and a
container differs from the release's assets. `manifest.py` refuses to write a manifest that
changes the base or any published release, comparing with each copy already at its output paths
(the same two copies).

Taqwa-data has release immutability on, so a published release takes no more uploads. `publish.py`
creates each new release as a draft, uploads every container to it, checks them, and only then
publishes it. Releases published before immutability was turned on are not locked by GitHub; the
apps' own check above is what holds those.

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
