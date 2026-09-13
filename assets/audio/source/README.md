# Source audio

The chime's source, "Clear announce tones" from Mixkit
(https://mixkit.co/free-sound-effects/tones/, file `mixkit-clear-announce-tones-2861.wav`), is
**not kept here**: the Mixkit Sound Effects Free Licence permits its use inside an app but not
redistributing the clip on its own, and this repository is public. Download it from that page and
run `python3 tools/prepare-chime.py` to regenerate `chime.ogg` and `chime.caf`; the script records
every processing step so the result is reproducible byte for byte.

The adhan sources are Wikimedia Commons recordings under CC0 / CC BY-SA / CC BY, which do permit
redistribution; see `../README.md` for each file's page, licence and the exact ffmpeg commands.
