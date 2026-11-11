#!/usr/bin/env bash
# Stores any feed file on emulator-5558 as the watch app's own feed, and has the face bind its slots
# again: a phone's golden feed (watchcore/vectors/phone/), or one written by hand:
#
#   tools/watchface/store.sh watchcore/vectors/phone/phone-capetown-ur24.json
#   KEEP_FEED=1 tools/watchface/shoot.sh /tmp/shots arch-ur 16:47 awake    # shoot it as stored
#   tools/watchface/feed.sh                                                # the sample again
#
# The file goes byte for byte, unchanged and never re-dated, so the feed's own days must hold the
# moment you shoot (the phone's goldens hold a month from the mock day before). Like arabic.sh, it
# writes the debug build's files/feed.json (run-as), stops the watch app so that its store reads
# the file again, and has the face bind its slots again (lib.sh rebind_face), so that it asks its
# sources for their data; it installs nothing. Set the clock first (tools/watchface/clock.sh): a source answers from the time it is asked
# at. First it removes files/feed.json.delivery (rm -f), the delivery of a phone feed the watch held
# (plan 2 Task 6), so that a feed stored by hand never carries the delivery of the one it replaced:
# removed before the feed is put in place, the order of WatchLinkContract.STORE_STEPS. A hand-stored
# feed carries no delivery, so a paired phone that is running replaces it at the watch's next
# handshake. tools/watchface/store-test.sh is its test.
source "$(dirname "$0")/lib.sh"
if [ "$#" -ne 1 ]; then
    echo "usage: $0 <feed file>" >&2
    exit 2
fi
FEED="$1"
[ -f "$FEED" ] || { echo "no file $FEED" >&2; exit 1; }
need_emulator

wadb shell run-as "$APP_PACKAGE" rm -f files/feed.json.delivery
wadb shell run-as "$APP_PACKAGE" sh -c "'cat > files/feed.json'" < "$FEED"
wadb shell am force-stop "$APP_PACKAGE"
rebind_face
sleep 3
wadb shell input keyevent KEYCODE_WAKEUP
echo "stored: $FEED; the face was bound again"
