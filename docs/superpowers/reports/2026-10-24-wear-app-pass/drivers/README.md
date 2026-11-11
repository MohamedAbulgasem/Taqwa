# The pass's drivers

The scripts that drove plan 3 Task 9's pass on `emulator-5558` (`../README.md`), committed under
Ruling 68 item 2 (e) so that the pass's rows can be shot again with them, as the Qibla rows are
after Ruling 66. They are the pass's own scripts with their paths changed: each finds this
folder, the repository and `tools/watchface` from where it lives, and writes into a work
directory, `PASS_WORK` (default `$TMPDIR/taqwa-wear-app-pass`), never into the repository, but
for `assemble.sh`. `pullfound.sh`, `restore.sh` and `state.sh` were also corrected after Ruling
68's review, and `restore.sh` again after Ruling 68 item 4's review round 1, so that a turn is put
back as found without a hand on the paired setup (below).

They drive `emulator-5558` alone, through `tools/watchface`'s `feed.sh`, `clock.sh`, `app.sh` and
`talkback.sh`, and the console (`adb emu`, each call bounded at 10 s, since it has hung before
while `adb shell` kept working).

## A turn on the watch

The execution map's rules hold (its Devices section): the `wear-5558` lock for the whole turn,
the watch put back as found before it is released, install with `adb -s emulator-5558 install -r`
only, stop by serial only.

```sh
mkdir ~/.cache/taqwa-locks/wear-5558.lock && echo "<lane, task, step, start>" > ~/.cache/taqwa-locks/wear-5558.lock/owner
tools/watchface/emulator.sh                       # boots it, or reuses it
export PASS_WORK=<a scratch directory>
D=docs/superpowers/reports/2026-10-24-wear-app-pass/drivers
$D/pullfound.sh                                   # the state found: state.txt, the app, the face, the app's own data
adb -s emulator-5558 shell run-as world.taqwa.app sh -c "'date +%s > files/refresh.asked'"   # else the install's start shakes hands
adb -s emulator-5558 install -r wearApp/build/outputs/apk/debug/wearApp-debug.apk
# ... the rows to shoot, below ...
$D/restore.sh                                     # puts back what pullfound.sh found; prints STATE AS FOUND
rm -r ~/.cache/taqwa-locks/wear-5558.lock
```

`restore.sh` first checks that `found/` is what `pullfound.sh` wrote (the record, the app, and each
folder's list with every file it names) and stops before the watch is touched if it is not, since an
unset or mistyped `PASS_WORK` gives an empty `found/`, from which it would delete the app's
`feed.json` and `files/` (Ruling 68 item 4). Then it takes TalkBack off, font scale 1.0, the Tile
away, the console's sensors and automatic time back, and puts the app back:

- **The found app**, unless it is the one installed (by its APK's sha1): an `install -r -d` over
  another build starts the app (`MY_PACKAGE_REPLACED`).
- **Every start it makes finds `refresh.asked` at now.** The app shakes hands at each start while
  the watch holds no delivery (the sample feed has none) and its stamp is more than a minute old,
  and a paired phone running Taqwa (`emulator-5562`) then sends its own feed (Ruling 50's heal). So
  the stamp is set before the install and before each stop and start, the found stamp is never
  written while a start may follow (the system may start the app again after a stop, to bind the
  face's complications), and it goes back at the end.
- **The app is started by name until it reads its face as found** (`DEBUG_REBIND`, which also has
  the complications and the Tile drawn again from the store; since plan 5 Tasks 4-5 the face is
  Watch Face Push's, inside the app): each start with the found files and the stamp, given 20 s
  after its face's "TaqwaFace capability" line (the profile installer writes
  `files/profileInstalled` 5 to 6 s after the start), then `files/face-activation-spent` read
  against `found/`'s. For a minute or so after an install the system answers that the Taqwa face is
  not active, so such a start writes "seen 41 inactive" and tells the phone there is a face to add
  (`taqwa_face_addable`); the app is started again 20 s on, up to eight times, until it reads
  "seen 41 active" and takes the capability back. A face that never reads as found (a turn that
  chose another face) stops it NOT AS FOUND; `tools/watchface/activate.sh` puts the Taqwa face back.
- **Then the found files once more**, after the starts have written theirs, with the app left
  running and settled, as the found watch had it: in `files/` and `shared_prefs/` every file
  `pullfound.sh` found (`found/files.list`, `found/shared_prefs.list`), byte for byte, and no other.
  So the stamp `complications.asked`, which every Tile request writes, a held sample, the tasbeeh
  counts and a delivery's leftovers go, and a folder found empty or absent is removed. A stopped app
  would shake hands at its next start with the found stamp, older than a minute.

`no_backup/` and `cache/` (the app's per-install copy of its bundled face, `FaceStage`) are not put
back: the found build's first start after the install writes them as it does after any install.

It deletes `system_locales`, takes a "left as found" shot a few seconds after the watch is back on
its face (`found/left-as-found.png`), and then checks three things, printing STATE AS FOUND only when
all hold (else STATE NOT AS FOUND, exit 1): `state.sh` against the record (the app by its APK's sha1,
not its install path, which every `install -r` changes; the app's pending alarms alone, since the
alarm manager's removal history keeps a line for each alarm a force stop removed); every file of
the app's own data against `found/`'s by its sha1, which `state.sh`, naming them, cannot see; and no
handshake sent and no feed taken in the log since it began. `test-restore.sh` holds all of it with
every adb call to a stub watch whose app does what Taqwa's does at a start on a paired watch.

## The scripts

| Script | What it shoots or does |
|---|---|
| `lib.sh` | Sourced by the rest: the paths, `w` (adb on 5558), `con` (the console, bounded), `shot`, `dump`, `rotary`, `field` (the magnetic field for a heading), `store` (a sample at a time), `clock`, `face`, `tile_shot` |
| `w` | `adb -s emulator-5558`, for a shell |
| `state.sh` | The watch's state as the locks' rule names it (read-only) |
| `pullfound.sh`, `restore.sh` | The state a turn finds, and putting it back |
| `test-restore.sh` | `restore.sh` stops before the watch on an incomplete `found/`; on a complete one it puts a stub watch back as found (stamped starts, the face read as found, every file's bytes), and says NOT AS FOUND for a file kept wrong or a face that never settles; no device (a stub adb) |
| `matrix.sh <en\|ar> <en\|en12\|ar-ly12\|ar-sa> [today\|tasbeeh\|qibla]` | Step 1's matrix for one feed, with the watch in that language; `qibla` is Searching (true 340), Aligned (true 23), Low and BestEffort |
| `tile.sh <en\|ar> <feed>...` | Step 1's Tile at 14:58 and 06:40 (the Tile added first, with `DEBUG_SURFACE`'s `add-tile`, as `tools/watchface/README.md` gives it) |
| `compare-all.sh > $PASS_WORK/logs/compare.tsv` | Every matrix shot against its drawing in `../ref/`, words masked |
| `refs-tasbeeh.sh` | The Tasbeeh drawings and masks again (`ref.py`), into `$PASS_WORK/ref` |
| `states.sh <en\|ar>` | Step 2's states on every page and the Tile (no paired phone running) |
| `r58.sh <golden>...` | Ruling 58 item 5's watch half: the phone's goldens on the three pages |
| `tb.sh <feed> [today\|tasbeeh\|qibla\|tile]`, `tb-panes.sh <feed>` | Step 3: what TalkBack says, from its own log |
| `large.sh <label> <en\|en12\|ar-sa>` | Step 4 at the font scale set before it (`settings put system font_scale`), with the longest city, from `feeds/` |
| `fixcheck.sh` | Today's list end at 1.0, 1.24 and 1.3 |
| `qibla-extra.sh stale\|tilt <label>` | Ruling 62 item 5's carried minors |
| `r65.sh [late\|doze\|talkback]` | Ruling 65 item 4: the Tile with the renderer late |
| `assemble.sh` | Copies everything shot into this report's folder, over what is there; for a partial re-shoot copy only its rows |
| `words.py`, `sheet.py`, `measure.py` | A dump's words in tree order; a contact sheet; the ink box of a screen's biggest line |
| `feeds/` | The hand-made feeds of Step 4: the samples with the longest city the bundle names (and a calibration-needed form) |

The watch in Arabic (`matrix.sh ar …`) is set with `SetLocale.java`, as `tools/watchface/README.md`
("The watch in Arabic") gives it, and set back to en-US the same way before `restore.sh`, which
deletes `system_locales` and checks the language against the record.

## The Qibla rows again (Ruling 68 item 4)

As shot on 6 November (`../qibla-r68/README.md`), with the build that holds R66 and Ruling 68 item 4:

```sh
for F in en en12 ar-ly12 ar-sa; do $D/matrix.sh en $F qibla; done     # then the watch in Arabic (SetLocale ar-EG), the same, and back
settings put system font_scale 1.0 (then 1.24, 1.3); for F in en en12 ar-sa; do $D/large.sh <label> $F qibla; done
$D/captions.sh 1.0; $D/captions.sh 1.3                                  # the seven languages' captions over a turning dial (langfeeds.py)
$D/captions.sh tilted <label>                                           # the tilted shots alone
$D/captions.sh sweep <label> <feed> <true heading>...                   # upright, for the detail's air on the pixels
python3 -I $D/airpx.py <shot.png>...                                    # the air on the drawn pixels
```

`large.sh <label> <feed> qibla` shoots the Qibla page's four states alone. `captions.sh` and
`large.sh` put their hand-made feeds without removing the delivery the watch holds: with the paired
phone (`emulator-5562`) running Taqwa, a watch that holds none echoes none, and the phone's next
send replaces the feed (Ruling 50), which voided the first run of 6 November. The hand-made feeds of
`feeds/` carry the dial's short forms since then (`distanceOnDial`, `qiblaDetailOnDial`); before,
an Arabic one fell back to English "km". A tilted watch is given its field in its own tilted axes
(`captions.sh`'s `tilted_field`), so the tilt-compensated heading is the one asked.
