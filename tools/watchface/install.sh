#!/usr/bin/env bash
# Builds the debug watch app (:wearApp, with its sample feed and, inside it, the debug face signed
# with the throwaway test key) and puts both on emulator-5558 only, through push-face.sh:
#
#   tools/watchface/install.sh                 # the face made active
#   tools/watchface/install.sh --inactive      # or any of push-face.sh's arguments
#
# No Gradle install task runs, and the face is never installed by itself: since plan 5 Task 4 it
# reaches the watch inside the watch app, as its default watch face, and a face installed beside Push's
# slot is not the face the slot holds. A first install loses the face's style, colour and slots
# (pass.sh chooses them again); tools/watchface/activate.sh brings the face back to the front.
source "$(dirname "$0")/lib.sh"
exec "$HERE/push-face.sh" "$@"
