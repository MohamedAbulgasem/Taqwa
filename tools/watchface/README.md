# tools/watchface

How the Taqwa face (`:watchface`) and the watch app's sample feed (`:wearApp` debug) are checked on
the Wear OS emulator and by Google's validators. Every script drives `emulator-5558` (the AVD
`Taqwa_Wear_OS_6`) and nothing else, and every install names it, because a phone may be plugged in.
A time such as `14:58` is that time on the mock day the sample feed and the mockups share, Sunday
18 October 2026 in Cape Town. Each script says how to use it at its top.

| Script | What it does |
|---|---|
| `emulator.sh` | Boots the emulator, or reuses it; takes it off the charger and wakes it |
| `scripts/wear-old-emulator.sh <30\|33>` | Boots the older watches plan 5 Task 12 runs on, `Taqwa_Wear_OS_3` on `emulator-5568` and `Taqwa_Wear_OS_4` on `emulator-5566`, each cut off from the paired phone at every boot; `WATCH=emulator-5568 tools/watchface/clock.sh …` sets their clocks |
| `install.sh` | Builds the debug watch app, with the debug face inside it, and puts both on `emulator-5558` through `push-face.sh` |
| `push-face.sh` | The one way the face reaches the emulator since plan 5 Task 4: builds the watch app with the face bundled as its default watch face (`-Ptaqwa.faceApk=<face.apk>` for a face built elsewhere), removes the face and the app, installs the app, waits up to 120 s for the system's Watch Face Push service to install the face into Taqwa's slot, stores the sample feed, and makes the face active (`--inactive` leaves it as the system left it); prints the face's version code and installer. Nothing installs the face by itself any more: a face installed beside Push's slot is not the face the slot holds, so the tools that rewrite the feed (`hijri.sh`, `arabic.sh`, `bengali.sh`, `weekday.sh`, `store.sh`) have the face bind again with the debug build's `DEBUG_REBIND` (`lib.sh` `rebind_face`) and install nothing. A first install loses the face's style, colour and slots: `pass.sh` chooses them again |
| `check-bundle.sh` | Checks the face a built watch app carries: `assets/default_watchface.apk` is `world.taqwa.app.watchfacepush.taqwa` at the app's version code minus 100000, signed with the certificate given and never with the app's key, with a `default_wf_token` that is not empty, and passed by the Push validator; every check prints its line and the summary comes last, on an unsigned app (every release build an agent makes) or an unsigned or unreadable face too. `check-bundle-test.sh` is its test: the debug face (the test key) in small watch apps `aapt2` links in a scratch directory, unsigned and signed with a scratch key, no device |
| `activate.sh` | Makes the Taqwa face the watch's face, with no taps |
| `choose.sh` | Chooses a style (`arch`, `ring`, `sun`, `moon`) or a colour (`amber`, `pearl`, `sage`, `night`) in the face editor, or fills a wearer's slot (`left`, `right`: `battery`, `steps`, `daydate`, `timedate`, `sunrise` and the other sources `editor.py` names, or `empty`) from the system's chooser, by the editor's accessibility text (`editor.py`; `editor.py outlines <png>` prints the slots the editor outlines and saves its slot page) |
| `clock.sh` | Sets the clock to a time on the mock day, or `auto`; `WATCH=emulator-5566` or `WATCH=emulator-5568` sets the Wear OS 4 or Wear OS 3 emulator's instead, and there it also turns off the watch's own time sync (`clockwork_auto_time 2`), which would move a set clock within three minutes; `auto` puts that and `auto_time` back as it found them; `clock-test.sh` is its test, with a fake adb and no device |
| `feed.sh` | Stores the sample feed for the day of a time |
| `app.sh` | Opens the watch app afresh on a page (`today`, `tasbeeh`, `qibla`: the `world.taqwa.app.wear.PAGE` extra) and shoots it, `WAIT` seconds later (3); what the page says is its accessibility tree (`uiautomator dump`) |
| `shoot.sh` | Screenshots the face at a minute, awake and always-on (`KEEP_FEED=1` keeps the stored feed; `SAMPLE=en12`, `ar-ly12` or `ar-sa` stores the debug build's other samples: English on twelve hours, Arabic in Libya on twelve hours, Arabic in Saudi Arabia) |
| `hijri.sh` | Stores the sample with a Hijri date of your choosing on every day, and has the face bind again, to see a long month or another language's form on the arch |
| `arabic.sh` | Stores the sample rewritten in Arabic (the names, the countdown's phrases, the dates, the rtl flag), in Libya's digits or Saudi Arabia's (`sa`), and has the face bind again; `SAMPLE=ar-ly12` or `ar-sa` is the committed way to the same |
| `bengali.sh` | Stores the sample rewritten in Bengali (the phone's names, plan 2's drafted countdown phrases, the dates), in Bengali digits or Western ones (`latn`), and has the face bind again: the Moon's যোহর and the other left-to-right curves in the Bengali UI font |
| `store.sh` | Stores any feed file byte for byte as the watch app's feed (a phone's golden feed in `watchcore/vectors/phone/`, `phone-capetown-ur24.json` the first Urdu one), removing the delivery of a phone feed the watch held first, and has the face bind again; set the clock first, and shoot it with `KEEP_FEED=1`; `store-test.sh` is its test, with a fake adb and no device. With the phone emulator paired and running (`emulator-5562`, plan 2 Task 7), the watch's next handshake has the phone replace a hand-stored feed, so stop the phone by its serial first (`adb -s emulator-5562 emu kill`; plan 2 Task 6) |
| `hijri-fit.py` | Checks every Hijri day and month in the seven languages against Arch's crown, in the steps the face picks by length (`face-centre.py`), at each of the line's places; fails if one is cut or comes within 7 px of the lit stroke |
| `weekday.sh` | Stores the sample with a weekday and a day number of your choosing on every day, and has the face bind again, to see the Moon's date window with a long weekday (`الخميس`) beside a wide day (`28`) |
| `window-fit.py` | Checks every weekday the phone writes (`phone-forms.json`, or another with `forms=`) beside every day of the month in each of its digit sets in the Moon's date window, the weekday scaled to its room as the face scales it (`face-moon.py`); fails if a weekday comes within 3 px of its day or is cut |
| `date-fit.py` | Checks the centre's lines in the phone's own words on Arch, Ring and Sun (`phone-forms.json`, or another with `forms=`): every Gregorian date (each weekday, day and month, in each digit set of each tag) and every countdown (each phrase with every hour to 99 and minute) drawn whole in the line box (`face-centre.py` `CENTRE_LINE_FONT`, `LINE_BOX`), and on Arch each date 7 px or more from the lit stroke (`hijri-fit.py`'s curve), the right-to-left date in the step the face picks by its length and its month (`ARCH_GREGORIAN_STEPS`, Ruling 51a: 19 characters or more 11.25 units, else August as أغسطس 13, else 14), which it checks `watchface.xml` draws by reading each step's Group and its expression; needs the watch's Bengali font and `hb-shape` |
| `date-fit-test.py` | `date-fit.py`'s tests: its rule for Arch's right-to-left date, its reading of the face's expressions, and its check of the face's steps on the face as committed and on faces made wrong on purpose; no font or emulator |
| `phone-forms.json` | The phone's forms the fit tools read: for each tag the face is measured for, its interface language and direction, the digit sets it can send, CLDR's weekdays and months as the feed writes them, and the countdown's phrases. Written by shared's `FaceFormsTest` from the phone's own code: `./gradlew :shared:testAndroidHostTest --tests '*FaceFormsTest*' -Ptaqwa.updateVectors`, then the fit tools on it |
| `moon-fit.py` | Checks the Moon's curved lines against every form the phone can send: each Hijri day and month in the seven languages inside its 104-degree path (and with a wearer's slot filled its 120-degree one, clear of the slots' boxes), each countdown (`phone-forms.json`'s phrases in every language and digit set, or another file's with `forms=`) inside its 124-degree path, each prayer's name beside its clock's widest time clear of the date window, and every curved run, either way, within its `PAD` of glyphs past its characters, none for a run without (`face-moon.py` `PADDED`, Ruling 37; needs `hb-shape`); widths in the face's fonts turned into degrees by radii measured on the emulator (its `CALIBRATION`) |
| `slot-fit.py` | Checks the wearer's slots against the values the watch's sources write (counts, per cents, times, dates, temperatures), each scaled to its room as the face scales it (`face-centre.py`, and on the Moon's curved bottom `face-moon.py`'s rooms); fails if one comes out wider than its room or would end in an ellipsis, and says how far a scaled Moon value's end lies off the baseline |
| `qibla-ink.py` | The Qibla page's glyph ink in the watch's own Arabic and Bengali fonts, for the Wear app's host tests (`QiblaFitTest`, through `HostInk`; Ruling 66 item 4, Ruling 67): the digits and marks the phone writes and each font's own no-break space (held to the font's cmap by `hb-info`, as minikin reads coverage), the dial's unit and letters (`WatchWords.kt`'s short forms, the phone's `qibla_cardinal_*`) and the captions drawn over a detail, shaped by `hb-shape` in Android Studio's layoutlib copies of Noto Naskh Arabic Bold (compact and elegant) and Noto Sans Bengali (both, at 600 and 800), into `wearApp/src/test/resources/world/taqwa/app/wear/qibla/fallback-ink.json`. When a short form, a dial letter or a caption changes in Arabic, Urdu or Bengali (a native reviewer's form, say), run `python3 -I tools/watchface/qibla-ink.py`, commit the model with the words, and run `./gradlew :wearApp:testDebugUnitTest`; `--check` fails while the committed model is not what the fonts give |
| `ref.py` | Draws the mockup from `faces.js` at the emulator's 454 pixels (`screen=g-today`, `g-tasbeeh`, `g-tasbeeh-list`, `g-qibla` or `g-tile` draws one of the watch app's screens from its `SCREENS` instead, with `count=`, `round=`, `flash=part\|set`, `heading=` and `t=`; `rtl` draws Today's ring anticlockwise, as a right-to-left feed has it; `textmask=<png>` writes a second picture of where the drawing's words lie, since the screens are drawn in English and on 24 hours only, so that a shot in another language or clock is compared by its geometry; `sec=` puts the Moon's hands at a second; `face=moon-d comps` is the Moon of option (d) with its two slots, from `moon-slot-choice.html` beside `faces.js`, with `v4=`, `v5=` and `hijri=`; the Moon's always-on hands are Ruling 41's, filled at `face-moon.py`'s `A_HANDS`, and `hands=outline` draws the approved drawing's outlines; its moon window is Ruling 46's always-on, the fill black and the hairline kept, and `window=filled` draws the approved drawing's fill) |
| `compare.py` | Mockup and screenshot side by side, with the pixels that differ (and `undither` for always-on frames; `mask=` leaves `ref.py`'s `textmask` out of the count, and `mirror` turns the shot left to right first, for a screen whose whole layout mirrors in right to left, such as the Tile's rows) |
| `rows.py`, `lit.py` | Where a colour's pixels lie; how much of the screen is lit (any channel of the face's colour over 8; Google's limit is 15%), and each part's colour. `undither` measures the emulator's always-on frame as the face draws it: twice its lit pixels, since the emulator dithers it as a strict checkerboard, and lit at 8 or more, since it draws it at 0.85 of the face's colours |
| `sun.py` | The Sun style's dial in numbers, in a screenshot or a mockup: where the ring's stretches begin and end, where each mark is and its colour, and where the sun is, as angles on the dial |
| `preview.py` | The face picker's preview, from an awake screenshot |
| `talkback.sh` | TalkBack on and off, a walk through the face's items with what TalkBack says for each, from its own log (its Diagnosis mode on), and how often it speaks with nothing touched (Task 12); `tap2 x y`, TalkBack's double tap ("activate"), and `actions`, the gesture that opens TalkBack's menu, where an item's actions are (plan 3 Task 4, below) |
| `validate.sh` | Google's wff-validator, and Watch Face Push's validator on the debug face and on the release (a scratch copy signed with the test key, since the release is unsigned); every check must pass |
| `lib-validators.sh` | The two validators, pinned by SHA-256 and fetched once into `~/.cache/taqwa-watchface-tools` (Google tags no wff-validator version, so the hash is the record), for `validate.sh` and `push-token.sh` (sourced) |
| `push-token.sh` | Watch Face Push's validation token for a signed face APK, for `world.taqwa.app`: written into a file, never printed; exits 1, with the file empty, unless the validator passes the APK (an unsigned one never does) |
| `get-system-fonts.sh` | Pulls the watch's own fonts the fit tools' `fonts=` reads (`NotoSansBengaliUI-VF.ttf`) from `emulator-5558` into `~/.cache/taqwa-watchface-tools/fonts`, each checked against the SHA-256 the script pins; `--check` checks the cache alone, with no device |
| `log.sh`, `ttf.py`, `get-noto.sh` | The emulator's log, a font's metrics, and the Noto Sans Arabic files as Google publishes them |
| `subset-noto.sh` | Subsets those files to what the face can be sent (`subset-noto.py` says what), with Urdu's ۴ ۶ ۷ in their Urdu forms in every weight, into `watchface/src/main/res/font` (`noto_sans_arabic_subset_v2_light.ttf`, `_semibold.ttf`, `_bold.ttf`; a changed file takes a new name, since the watch keeps a font by its name), with a pinned fontTools of its own |

A verification, start to end:

```sh
tools/watchface/emulator.sh
tools/watchface/install.sh                                 # the app and its face, made active
tools/watchface/choose.sh style arch accent amber
tools/watchface/shoot.sh /tmp/shots arch 14:58            # arch-awake.png, arch-aod.png
python3 -I tools/watchface/ref.py /tmp/shots/ref.png face=arch t=14:58
python3 -I tools/watchface/compare.py /tmp/shots/ref.png /tmp/shots/arch-awake.png /tmp/shots/side.png
tools/watchface/validate.sh
python3 -I tools/watchface/hijri-fit.py                    # after a change to the centre stack or its fonts
python3 -I tools/watchface/window-fit.py                   # after a change to the Moon's date window
python3 -I tools/watchface/moon-fit.py                     # after a change to the Moon's curved lines
python3 -I tools/watchface/date-fit.py                     # after a change to the centre's date line or countdown
python3 -I tools/watchface/date-fit-test.py                # with it, after a change to Arch's right-to-left date steps
tools/watchface/clock.sh auto                              # leave the emulator on automatic time
```

A long Hijri date on the emulator, before it comes round in the calendar:

```sh
tools/watchface/hijri.sh '29 Jumada al-Akhirah'
KEEP_FEED=1 tools/watchface/shoot.sh /tmp/shots long 14:58 awake
tools/watchface/feed.sh                                    # the sample again
```

The pictures the face shows are drawn by `tools/face-art.py`, the Arch's lit arch by
`tools/face-arch.py`, the Sun's dial (slot 7, and its frame) by `tools/face-sun.py`, the Moon's
six blocks (its dial, slots 0, 2, 3 and 6, and its hands) by `tools/face-moon.py`, and the centre
Arch, Ring and Sun share (the dates, the time, the prayer line and the countdown), Ring's ring and
the wearer's two slots by `tools/face-centre.py`; each writes the same output on every run, and
`--check` says whether the face is up to date with it. `face-art.py` compares its pictures by what
they decode to (pixels, mode and size), because the PNG encoder writes the same pixels as other
bytes on another machine, and it rewrites only a picture that changed. CI runs the four XML checks,
the release build and `validate.sh --schema-only`; `scripts/face-gate.sh` runs all five checks, the
fit tools and `validate.sh` before every release (below).

The Sun at three times, set beside its mockup in numbers:

```sh
tools/watchface/choose.sh style sun accent amber
for t in 14:58 04:10 22:30; do
  tools/watchface/shoot.sh /tmp/shots sun-${t/:/} $t
  python3 -I tools/watchface/ref.py /tmp/shots/ref-${t/:/}.png face=sun t=$t
  python3 -I tools/watchface/sun.py /tmp/shots/ref-${t/:/}.png /tmp/shots/sun-${t/:/}-awake.png
done
```

The Moon at the mockup's 10:10 and at 14:58, with the drawing's yearless Hijri date. A source answers
from the time it is asked at, so the clock is set just before the face binds again:

```sh
tools/watchface/choose.sh style moon accent amber
tools/watchface/clock.sh 10:09 && tools/watchface/hijri.sh '6 Jumada al-Ula' 10:10
KEEP_FEED=1 tools/watchface/shoot.sh /tmp/shots moon-1010 10:10:36 awake
python3 -I tools/watchface/ref.py /tmp/shots/ref-1010.png face=moon t=10:10 sec=36
python3 -I tools/watchface/compare.py /tmp/shots/ref-1010.png /tmp/shots/moon-1010-awake.png /tmp/shots/side.png
```

The Moon's date window with the longest weekday beside the widest day, in Saudi Arabia's digits:

```sh
tools/watchface/clock.sh 10:09 && SAMPLE=ar-sa tools/watchface/weekday.sh 'الخميس' '٢٠' 10:10
KEEP_FEED=1 tools/watchface/shoot.sh /tmp/shots thu-20 10:10:36 awake
python3 -I tools/watchface/window-fit.py show=الخميس,٢٠     # the scale and the gap it should come out at
tools/watchface/feed.sh                                    # the sample again
```

The face in another language or clock is the debug build's other samples; the face reads the
phone's direction, clock and digits from slot 7, so nothing else changes:

```sh
SAMPLE=ar-ly12 tools/watchface/shoot.sh /tmp/shots ring-ar 14:58          # Libya, twelve hours
python3 -I tools/watchface/ref.py /tmp/shots/ref-ar.png face=ring t=14:58 lang=ar clock=12
SAMPLE=ar-sa tools/watchface/shoot.sh /tmp/shots ring-sa 14:58            # Arabic-Indic digits
python3 -I tools/watchface/ref.py /tmp/shots/ref-sa.png face=ring t=14:58 lang=ar-SA
```

The wearer's slots, as the plan's pass fills them, with values of other widths, and empty again:

```sh
tools/watchface/choose.sh left battery right steps
tools/watchface/shoot.sh /tmp/shots arch-comps 14:58
python3 -I tools/watchface/ref.py /tmp/shots/ref-comps.png face=arch t=14:58 comps
tools/watchface/choose.sh left daydate right timedate        # "Fri 23", "10:58"
python3 -I tools/watchface/slot-fit.py show=12,345            # how a value comes out, scaled
python3 -I tools/watchface/slot-fit.py                        # every common value fits
tools/watchface/choose.sh left empty right empty
```

The Moon with its two slots filled (option (d)): the lower half re-laid, against the drawing, then
where the editor outlines the slots:

```sh
tools/watchface/choose.sh style moon left battery right steps
tools/watchface/shoot.sh /tmp/shots moon-comps 10:10
python3 -I tools/watchface/ref.py /tmp/shots/ref-moon-comps.png face=moon-d t=10:10 sec=0 comps v4=82 v5=0
python3 -I tools/watchface/compare.py /tmp/shots/ref-moon-comps.png /tmp/shots/moon-comps-awake.png /tmp/shots/side.png
python3 -I tools/watchface/editor.py outlines /tmp/shots/moon-outlines.png
```

What TalkBack says on the face, in order, with the wearer's slots filled (TalkBack's Diagnosis mode
on once, in TalkBack's developer settings; shoot the face with TalkBack off, since that mode draws a
log over the screen):

```sh
tools/watchface/choose.sh style moon left battery right steps   # the editor, TalkBack off
tools/watchface/talkback.sh on
tools/watchface/talkback.sh idle 10     # 0: nothing is said again while no one touches the watch
tools/watchface/talkback.sh walk 7      # the time, the prayer and its countdown, the dates, the slots
tools/watchface/talkback.sh off
```

## The watch app's screens (plan 3)

The debug build's receiver (`DebugFeedReceiver`) shows the app's states without a usable feed, with
three extras beside `epoch` and `sample`:

| Extra | What it does | Its line |
|---|---|---|
| `--ez clear true` | Forgets the feed, the update notice and the delivery (`FeedStore.clear`), holds the sample so that the process's next start does not put it back (`files/sample.held`), and has the complications and the Tile draw again: every page and the Tile say "Open Taqwa on your phone" | `CLEARED` |
| `--ei version 2` | Offers the sample in that format: the store turns it away, so the watch says "Update Taqwa on your watch", and whatever was stored (a feed, or none) stays | `NEWER_VERSION for epoch …, version 2` |
| `--es city <name>` | Stores the sample with that city, for the longest city's shots | `ACCEPTED for epoch …, a city of n characters` |

A plain `DEBUG_FEED` stores the sample again and lifts the hold. The states, on every page:

```sh
tools/watchface/app.sh today /tmp/shots/today.png
adb -s emulator-5558 shell am broadcast -a world.taqwa.app.wear.DEBUG_FEED --ez clear true
adb -s emulator-5558 shell am broadcast -a world.taqwa.app.wear.DEBUG_FEED --ei version 2
adb -s emulator-5558 shell am broadcast -n world.taqwa.app/world.taqwa.app.wear.DebugFeedReceiver \
    -a world.taqwa.app.wear.DEBUG_FEED                    # the sample again
```

The bezel is the emulator's rotary, `input rotaryencoder scroll --axis SCROLL,<value>` (the shell
refuses `-a SCROLL=1`); Settings' own list turning with it shows that the events reach the app in
front. **A negative value is clockwise**: it turns a list down, as a Galaxy's bezel turned
clockwise does, and Compose hands it to the app as positive pixels (`verticalScrollPixels`, the
axis's value times `-scaledVerticalScrollFactor`). On the tasbeeh counter (plan 3 Task 6, 30
October) `SCROLL,-1` counts one and `SCROLL,1` takes one away, and `SCROLL,-3` is one turn of
three clicks. The emulator's own rotary control is a knob in its extended-controls window, which
has no console command (`adb emu help` lists none), so the sign was checked against the lists
the system and Today turn with it.

The tasbeeh counter, as Task 6 checked it: the counts are in the app's own preferences
(`run-as world.taqwa.app cat shared_prefs/tasbeeh.xml`, `count.<id>` as `"<count>,<round>"`),
which read the count without a screen reader. A haptic is read back with `dumpsys
vibrator_manager`, whose history lists each one as `finished` or `cancelled_superseded`. A
part's flash lasts 240 ms, so its shot is taken in the same shell as the click
(`input rotaryencoder scroll --axis SCROLL,-6; sleep 0.1; screencap -p /sdcard/f.png`); a
screencap straight after the click is taken before the frame that draws it.

**TalkBack on the counter.** "Swipe down then right" (`talkback.sh actions`) opens the TalkBack
menu with "Actions" first whenever the focused item has actions; `tap2` opens them ("Take one
away", "Choose a dhikr"). A double tap activates the item TalkBack has focused, wherever it
lands, so `walk 1` moves to the next action before `tap2`. On 30 October TalkBack asked for phone
access five times in one session, after a window change: each prompt is cancelled with a plain
`input tap 152 373` (never "Continue"), and while one is up it takes the bezel, so a count that
does not land means a prompt is in front (`dumpsys activity activities | grep topResumedActivity`). A swipe right from the screen's left edge closes the app on Today: `input swipe 5 227 420
227 300` did so every time on 30 October, in English and in Arabic, while `20 227 420 227 200`
did two or three times in four, in either language.

A screen in another language or clock is set beside its drawing by its geometry, its words masked,
and its words are read from the accessibility tree:

```sh
SAMPLE=ar-sa tools/watchface/feed.sh && tools/watchface/clock.sh 14:58
tools/watchface/app.sh today /tmp/shots/today-sa.png
python3 -I tools/watchface/ref.py /tmp/shots/ref-today.png screen=g-today t=14:58 rtl textmask=/tmp/shots/mask.png
python3 -I tools/watchface/compare.py /tmp/shots/ref-today.png /tmp/shots/today-sa.png /tmp/shots/side.png mask=/tmp/shots/mask.png diff
```

**What the mask leaves compared.** Each line of the drawing's words is masked over its own box and
over the room another language's words may take on its rows (`ref.py`'s `MASK_SCRIPT`):

| The line | Its room across |
|---|---|
| Set from its start or its end (the Tile's and the list's names and times) | Two and a half times its English width, inwards |
| Centred away from the screen's middle (the Qibla dial's N, E, S and W) | Two and a half times its width, about its own centre |
| Centred on the middle (every other line, which `LineText` may set as wide as the chord) | The whole screen |

That room stops half a unit inside the inner edge of the screen's ring (`INSIDE`, from `faces.js`'s
own figures): 86.3 units from the middle for Today's ring, 85 for the counter's with the set's
flash, 81.0 for the Qibla dial's ticks and its Kaaba marker's halo. The list and the Tile have no
ring, and their room stops at the screen's edge. So everything the drawings light that is not a
word is compared: the rings whole, the dial's rim, ticks, needle and marker, Today's chevron, the
counter's part dots, the Tile's pips, and the list's pills' left ends and their edges above and
below their lines. What stays hidden:
- a word's own box, wherever it lies: the ticks just behind a cardinal, and the counter's ring
  and flash under "Turn the bezel or tap" (a count of 0);
- the inside of the ring on a centred line's rows: the needle, where a heading turns it down
  through the bearing and the distance (true 340 and 23, Task 7's shots, keep it clear), and a
  cardinal that a heading of 0, 90, 180 or 270 sets on the middle column, whose room is then a
  centred line's;
- on the list, the pill under its name and count, its right-hand end with them.

Measured on 30 October: the full-width bands this replaces hid 56% of `g-qibla`'s marks other than
its words (heading 340), the whole Kaaba marker among them and 54% of the amber needle and marker,
and all of Today's ring between "ASR IN" and "16:10". These hide 0.5% of `g-qibla`'s (the ticks
behind its letters), none of the needle or the marker, and none of `g-today`'s (either direction),
`g-tasbeeh`'s or `g-tile`'s at their defaults. Every pixel of the drawings' own words stays masked.

**TalkBack on the app's screens.** TalkBack 16 for Wear (the emulator's, 30 October) moves through
a screen with one-finger swipes, left and right for the previous and the next item, up and down for
the navigation focus. Its "Customize gestures" list gives the actions menu no gesture of its own:
five of its eight angle gestures are "Tap to assign", right then up and right then down turn the
volume, and "Swipe down then right" opens the TalkBack menu. On a phone that menu lists the focused
item's actions when it has any; Today has none, so whether the watch's does is Task 6 Step 5's to
see, on the counter, whose actions they are. So:

- `talkback.sh actions` draws "Swipe down then right" (down the middle from 110 to 300, then
  right to 397, its points 20 ms apart, through one console session) and prints what TalkBack
  said: on Today, "TalkBack menu" and its first item, "Read from top".
- `talkback.sh tap2 x y` is a double tap through one console session (port 5558, after `auth`
  with `~/.emulator_console_auth_token`), since separate `adb emu` calls each start adb and open
  the console again. Measured with `getevent -lt` on 30 October: each tap held 54 and 62 ms, and
  125 ms between the first lift and the second touch, inside TalkBack's 300 ms window and above
  its 40 ms minimum. On the TalkBack menu it activated "Read from top". The first time it does,
  TalkBack shows its one-time continuous-reading tutorial (BACK closes it), and may ask for phone
  access: that prompt is cancelled, never granted.

**The Tile (plan 3 Task 8).** The system's debug surface adds it and takes it away. An added Tile
comes first, one swipe left of the face (`input swipe 400 227 60 227 200`). Wear OS 6's surface
has no `show-tile` ("Unrecognized operation show-tile"):

```sh
adb -s emulator-5558 shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
    --es operation add-tile --ecn component world.taqwa.app/world.taqwa.app.wear.tile.TodayTileService
adb -s emulator-5558 shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
    --es operation remove-tile --ecn component world.taqwa.app/world.taqwa.app.wear.tile.TodayTileService
```

The Tile is asked for again whenever a feed is stored (`feed.sh`, `DEBUG_FEED`), a newer format is
offered (`--ei version 2`) or the store is cleared, so the clock is set first and the feed stored
after it. Its words are in the accessibility tree (`uiautomator dump`), as the app's are. Its
countdown is a dynamic value the system moves on by itself: to see it move with the app's process
dead, kill the process as the app's own user (`run-as world.taqwa.app kill -9 $(pidof
world.taqwa.app)`), since `am kill` leaves a process whose Tile service is bound. The renderer may
bind the service again at once, which starts the process anew (`Start proc … for service
TodayTileService` in the log), so check `pidof` through the minute. The renderer gives a Tile 200
dynamic nodes; over them it logs `Dynamic type expression limit reached` and keeps the static value
(`TilePlan`'s KDoc).

**The Tile's refresh (plan 5 Task 12 Step 4).** The system asks for the Tile again
`TilePlan.freshnessMillis` after its last request, by itself, whether the Tile is visible or not: on an awake
emulator, 300 s, 720 s and 2,700 s came within 0.1 s, on `TaqwaTile`'s `Tile:` line. Google's reference calls
the interval inexact and the update not guaranteed, so this is what was seen and not a promise. Its timer runs on real time, so `clock.sh` does not
move it, though it does move the renderer's entry changes (`ProtoTilesPlTileContentEmitter`, an RTC alarm).
The usual freshness is 45 minutes. On an ordinary feed, a shorter one exists only before the feed's last start: set the clock
there first, then `add-tile`, and leave the clock alone.

**The watch in Arabic (plan 3 Task 8 Step 5, and Task 9).** Settings › System › Language says only
"The watch language is the same as your phone's" on this image (no phone is paired, and it is a
user build with no root). The system language is set as Settings' own picker sets it, with plan 2's
`SetLocale.java` (`docs/superpowers/reports/2026-10-23-phone-feed-builder/urdu/`), run as the shell
user, which holds `CHANGE_CONFIGURATION`:

```sh
SDK=~/Library/Android/sdk; OUT=$(mktemp -d)
javac --release 11 -cp $SDK/platforms/android-36/android.jar -d $OUT \
    docs/superpowers/reports/2026-10-23-phone-feed-builder/urdu/SetLocale.java
$SDK/build-tools/37.0.0/d8 --output $OUT $OUT/SetLocale.class
adb -s emulator-5558 push $OUT/classes.dex /data/local/tmp/taqwa-setlocale.dex
adb -s emulator-5558 shell app_process -cp /data/local/tmp/taqwa-setlocale.dex / SetLocale ar-EG
# … the shots, then English again, as plan 1 left it:
adb -s emulator-5558 shell app_process -cp /data/local/tmp/taqwa-setlocale.dex / SetLocale en-US
adb -s emulator-5558 shell settings delete system system_locales
adb -s emulator-5558 shell rm /data/local/tmp/taqwa-setlocale.dex
```

The system turns Arabic at once: the face's description and the Timer tile are in Arabic, and the
Tile carousel scrolls the other way, so the Tile is found by what it says (its city), not by a
fixed number of swipes. On 30 October the Tile renderer laid a Row out left to right on the Arabic
watch, as on the English one: the Tile's rows follow the feed's direction alone (`TodayTileService`'s
`HOST_RTL`).

**The full pass (plan 3 Task 9, 31 October).** Its shots, walks and scripts' recipe are in
`docs/superpowers/reports/2026-10-24-wear-app-pass/`. What it taught about driving the emulator:

- **The face first, by the HOME intent.** The HOME key on the face opens the app list; on any other
  screen it goes to the face. `adb -s emulator-5558 shell am start -W -a android.intent.action.MAIN
  -c android.intent.category.HOME` goes to the face from anywhere, and stays there when it is in front.
- **A Tile request is held for about 20 s after the last.** A feed stored within that time of the one
  before is drawn by the Tile only once the hold ends, so a Tile shot straight after a store shows the
  previous feed's Tile (on 31 October, three of four did). Store, wait 22 s, then swipe to the Tile.
- **The Tile is found by its words, and tried again from the face.** One swipe from the face does not
  always land on it: on 31 October the first try or two landed on the face itself or on the Timer tile,
  and the third on the Tile, in English and in Arabic. So each try goes to the face, wakes the screen,
  swipes, and reads the tree for a word the Tile must show (its city, or the notice); a try that does
  not find it starts again from the face.
- **A phone's golden feed for the app's pages alone** goes in as `store.sh` writes it (`run-as … sh -c
  'cat > files/feed.json'`, after `rm -f files/feed.json.delivery`, then `am force-stop`), without
  `store.sh`'s face install, which only the face needs. The same way, a hand-made copy of a sample shows
  what the emulator cannot make: its compass is never at LOW accuracy, so "Compass needs calibrating"
  (Low(CALIBRATION)) was seen by a copy whose `ui.screens.interference` holds the calibration title,
  under a 600 µT field.
- **The watch's font size** is Settings › Accessibility › Text size › Font size, its "+" pressed
  until it reads "Largest": `font_scale` goes 1.0 ("Default"), 1.06, 1.12, 1.18 and 1.24. The page is
  drawn again at each press, so the "+" is found in the tree again before the next. Above 1.24 only
  `settings put system font_scale 1.3` reaches, and `settings put system font_scale 1.0` (or
  "Default") puts it back. "Display size" sits above it on the same page and is left alone
  (`wm density` stays 320).
- **TalkBack from page to page.** A one-finger walk never leaves a page: on Today it goes from the
  ring through the six rows and the city to the end and starts again at Fajr; on Tasbeeh the counter
  is the page's one item. TalkBack's way to the next page is a two-finger swipe, which the console's
  one finger cannot draw. A swipe sent with `adb shell input swipe` reaches the app, not TalkBack, as
  a sighted wearer's would, so it turns the pager with TalkBack on: each page's title is said as it
  comes ("Tasbeeh", "Qibla", "Today", or the phone's words). The counter's two actions are reached
  through `actions` and `tap2`, as above; a menu that opens late lands the next `tap2` on "Read from
  top", so check that `actions` printed "Actions" before tapping.
- **The Tile with the renderer late** (Ruling 65): the renderer's next change is the pending
  `*alarm*:ProtoTilesTimelineMgr` in `dumpsys alarm` (its `origWhen` and `window`), and
  `dumpsys deviceidle force-idle` holds it for as long as the doze is forced (`unforce` after).

## The release: the face's key, its signature, its token and the gate

The face is signed with Mohamed's face key, never the app's (spec 3.1), and only he signs with it.
Agents use the throwaway test key (`~/.android/taqwa-face-debug.keystore`, made by
`scripts/face-debug-key.sh`) and keys made in a scratch directory, never `~/keys`.

| Script | Who, when | What it does |
|---|---|---|
| `scripts/make-face-key.sh` | Mohamed, once | Makes `~/keys/taqwa-face.jks` (alias `taqwa-face`, RSA 4096, 10000 days), its password typed twice and written nowhere; refuses to overwrite; prints the certificate's SHA-256 and two warnings (back it up twice; nobody can reset a lost face key). The Lead writes that SHA-256 into `watchface/face-release-cert.sha256` (plan 5 Task 17, M1) |
| `scripts/face-gate.sh` | before every release | The five generators' `--check`; `hijri-fit.py`, `window-fit.py`, `slot-fit.py`, `moon-fit.py`, `date-fit.py` and `date-fit-test.py`, with `fonts=` the pinned cache (it refuses to start while a pinned font is missing or changed); `validate.sh`. Exits 1 if any fails, or if a check changed a tracked file |
| `scripts/release-face.sh` | Mohamed, each release | Builds `:watchface:assembleRelease` with `-Ptaqwa.unsignedRelease=true` (the face never reads it; it keeps `keystore.properties` unread), signs it into `build/face-release/watchface-release-signed.apk` (on a terminal apksigner itself asks for the password, once, never echoed, with `--ks-pass stdin`; a terminal whose output is piped or redirected is refused, since apksigner would echo there; a piped password is read with `--ks-pass file:/dev/stdin`, with no prompt), verifies it, and deletes it again unless its one certificate is the pin. `TAQWA_FACE_KS`, `TAQWA_FACE_ALIAS` and `TAQWA_FACE_CERT_FILE` name another key and pin; `TAQWA_FACE_KS_PASS_ENV` (the password from a variable) is for the test key and scratch keys alone, and is refused for a key in `~/keys` |
| `tools/watchface/push-token.sh` | the release path | The token for the signed face, made from that very APK, which the watch app bundles beside it (plan 5 Task 4) |
| `scripts/test-face-release.sh` | after a change to any of these | Their tests, with scratch keys and the test key only |

The fit tools were calibrated on the emulator's own Bengali font, so the gate runs on the Mac with
that font pinned by hash (the Lead's call 4), rather than in CI with a font fetched from elsewhere:

```sh
tools/watchface/get-system-fonts.sh        # once, with emulator-5558 up (under the wear-5558 lock)
scripts/face-gate.sh                       # then before each release, with no device
bash scripts/test-face-release.sh          # after a change to the release scripts
```

A changed emulator font stops the gate until the fit tools are measured again on it and its new
hash is pinned in `get-system-fonts.sh`.
