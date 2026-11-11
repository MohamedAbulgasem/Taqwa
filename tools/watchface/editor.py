"""Chooses the Taqwa face's style or colour in the watch's own face editor on emulator-5558, the way
a wearer does (long-press, the pencil, the option's page, the list), finding every control by its
accessibility text in a uiautomator dump rather than by where it was last time. Run by choose.sh:

    python3 -I tools/watchface/editor.py style arch|ring|sun|moon
    python3 -I tools/watchface/editor.py accent amber|pearl|sage|night
    python3 -I tools/watchface/editor.py left|right <source>
    python3 -I tools/watchface/editor.py outlines <out.png>

The third fills one of the wearer's two slots from the system's complication chooser, as a wearer
does on the editor's last page. The sources are the names in SOURCES: battery, steps, next, taqwa,
daydate, datemonth, timedate, weekday, sunrise, countdown, alarm, moonphase, shortcut and empty.
The last opens that page, prints each slot the editor outlines (its label and its box) and saves
the page as a picture, to see that the outlines lie where the face draws the slots.

It checks that the editor shows the new choice before it leaves, and says so; it stops with a
message if anything it looks for is not there.
"""
import os
import re
import subprocess
import sys
import time

SERIAL = "emulator-5558"
ADB = os.path.join(os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk")),
                   "platform-tools", "adb")

# The editor's pages in the face's order (UserConfigurations), and each option's display name
# (watchface/src/main/res/values/strings.xml).
PAGES = {
    "style": (0, {"arch": "Arch", "ring": "Ring", "sun": "Sun", "moon": "Moon"}),
    "accent": (1, {"amber": "Amber", "pearl": "Pearl", "sage": "Sage", "night": "Night"}),
}
# The wearer's slots, on the editor's third page: which outline is which, by where it starts (the
# left one's box starts at x 110, the right one's at 234), and the chooser's rows for each source
# (the emulator's own, besides Taqwa's: values of every width, for tools/watchface/slot-fit.py's
# check on the device).
SLOTS = {"left": lambda box: box[0] < 200, "right": lambda box: box[0] >= 200}
SOURCES = {"battery": "Battery, Wear OS", "steps": "Step count, Wear OS", "next": "Next prayer, Taqwa",
           "taqwa": "Taqwa, Taqwa", "daydate": "Day and date, Wear OS", "datemonth": "Date and month, Wear OS",
           "timedate": "Time and date, Wear OS", "weekday": "Day of the week, Wear OS",
           "sunrise": "Sunrise and sunset, Wear OS", "countdown": "Countdown to date, Clock", "alarm": "Alarm, Clock",
           "moonphase": "Moon phase, Clock", "shortcut": "App shortcut, Wear OS", "empty": "Empty"}
NODE = re.compile(r'<node [^>]*?(?:text="([^"]*)")[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')


def adb(*args, capture=False):
    run = subprocess.run([ADB, "-s", SERIAL] + list(args), capture_output=True, text=True)
    if run.returncode != 0:
        sys.exit("adb %s failed: %s" % (" ".join(args), run.stderr.strip()))
    return run.stdout if capture else None


def nodes():
    """Every node of the screen as (label, (x0, y0, x1, y1)): its text, else its description."""
    for attempt in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/taqwa-ui.xml", capture=True)
        xml = adb("shell", "cat", "/sdcard/taqwa-ui.xml", capture=True)
        if "<hierarchy" in xml:
            break
        time.sleep(1)
    out = []
    for m in NODE.finditer(xml):
        label = m.group(1) or m.group(2)
        if label:
            out.append((label, tuple(int(v) for v in m.group(3, 4, 5, 6))))
    return out


def find(label, among=None, exact=True):
    for text, box in (among if among is not None else nodes()):
        if (text == label) if exact else (label in text):
            return box
    return None


def tap(box):
    adb("shell", "input", "tap", str((box[0] + box[2]) // 2), str((box[1] + box[3]) // 2))


def wait_for(label, exact=True, seconds=8):
    deadline = time.time() + seconds
    while time.time() < deadline:
        box = find(label, exact=exact)
        if box:
            return box
        time.sleep(0.7)
    sys.exit("the editor did not show %r" % label)


def to_face():
    """Backs out of the editor, its lists and the picker until the watch face itself is showing."""
    for _ in range(6):
        labels = {text for text, _ in nodes()}
        if not labels & {"Customize Watch Faces", "Watch face picker", "Click to select an option", "Complications"}:
            return
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1.5)
    sys.exit("could not get back to the watch face")


def page_index():
    """Which of the editor's pages is showing (0 first), from its own "N of M in list" description;
    the editor opens on the page it was last left at."""
    for text, _ in nodes():
        m = re.search(r"(\d+) of (\d+) in list", text)
        if m:
            return int(m.group(1)) - 1
    return None


def go_to_page(page):
    for _ in range(6):
        here = page_index()
        if here is None:
            sys.exit("the editor does not say which page it is on")
        if here == page:
            return
        if here < page:
            adb("shell", "input", "swipe", "400", "227", "60", "227", "300")   # on to the next page
        else:
            adb("shell", "input", "swipe", "60", "227", "400", "227", "300")   # back to the one before
        time.sleep(2)
    sys.exit("could not reach the editor's page %d" % (page + 1))


def current_value(names):
    """The option the page shows now: a node whose text is one of the names, below the centre."""
    for text, box in nodes():
        if text in names and box[1] > 300:
            return text, box
    return None, None


def fill_slot(which, source):
    """Fills the wearer's slot which (left or right) with source from the chooser."""
    want = SOURCES[source]
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    time.sleep(1)
    to_face()
    adb("shell", "input", "swipe", "227", "227", "227", "227", "1500")    # long-press: the picker
    tap(wait_for("Edit watch face"))
    wait_for("Customize Watch Faces")
    time.sleep(1.5)
    go_to_page(2)
    outlines = [(t, b) for t, b in nodes() if b != (0, 0, 454, 454) and "in list" not in t and t != "Customize Watch Faces"]
    slot = next((b for t, b in outlines if SLOTS[which](b)), None)
    if slot is None:
        sys.exit("the editor shows no %s slot" % which)
    adb("shell", "input", "tap", str((slot[0] + slot[2]) // 2), str((slot[1] + slot[3]) // 2))
    wait_for("Complications")
    time.sleep(1)
    for attempt in range(16):
        row = find(want)
        if row and row[1] > 60 and row[3] < 440:
            tap(row)
            break
        adb("shell", "input", "swipe", "227", "380", "227", "200", "400")
        time.sleep(1.2)
    else:
        sys.exit("%r is not in the chooser" % want)
    time.sleep(3)
    wait_for("Customize Watch Faces")
    shown = [t for t, b in nodes() if SLOTS[which](b) and b != (0, 0, 454, 454) and "in list" not in t]
    print("%s slot: %s (%s)" % (which, source, ", ".join(shown) or "nothing shown"))
    adb("shell", "input", "keyevent", "KEYCODE_BACK")                   # leaves the editor, keeping it
    time.sleep(2)
    to_face()


def outlines(out):
    """The editor's slot page: each outline the editor draws, and the page as a picture in out."""
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    time.sleep(1)
    to_face()
    adb("shell", "input", "swipe", "227", "227", "227", "227", "1500")    # long-press: the picker
    tap(wait_for("Edit watch face"))
    wait_for("Customize Watch Faces")
    time.sleep(1.5)
    go_to_page(2)
    time.sleep(1.5)
    for t, b in nodes():
        if b != (0, 0, 454, 454) and "in list" not in t and t != "Customize Watch Faces":
            print("outline %r at %s" % (t, b))
    with open(out, "wb") as f:
        f.write(subprocess.run([ADB, "-s", SERIAL, "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout)
    print("the slot page is in %s" % out)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(2)
    to_face()


def main():
    if len(sys.argv) == 3 and sys.argv[1] in SLOTS and sys.argv[2] in SOURCES:
        fill_slot(sys.argv[1], sys.argv[2])
        return
    if len(sys.argv) == 3 and sys.argv[1] == "outlines":
        outlines(os.path.abspath(sys.argv[2]))
        return
    if len(sys.argv) != 3 or sys.argv[1] not in PAGES or sys.argv[2] not in PAGES[sys.argv[1]][1]:
        sys.exit(__doc__)
    page, options = PAGES[sys.argv[1]]
    want = options[sys.argv[2]]
    names = set(options.values())

    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    time.sleep(1)
    to_face()
    adb("shell", "input", "swipe", "227", "227", "227", "227", "1500")    # long-press: the picker
    tap(wait_for("Edit watch face"))
    wait_for("Customize Watch Faces")
    time.sleep(1.5)
    go_to_page(page)
    value, box = current_value(names)
    if value is None:
        sys.exit("no %s value on the editor's page %d" % (sys.argv[1], page + 1))
    if value != want:
        tap(box)                                                          # the list of options
        wait_for("Click to select an option")
        # The list opens at the current choice: look, then scroll towards its start a few times,
        # then towards its end, until the option is on the screen.
        scrolls = [None] + [("227", "200", "227", "380")] * 3 + [("227", "380", "227", "200")] * 6
        for scroll in scrolls:
            if scroll:
                adb("shell", "input", "swipe", *scroll, "400")
                time.sleep(1.2)
            row = None
            for text, b in nodes():
                if text == want and b[1] > 120 and (b[3] - b[1]) < 80:
                    row = b
            if row and row[3] < 440:
                tap(row)
                break
        else:
            sys.exit("%r is not in the list" % want)
        time.sleep(2.5)
        value, _ = current_value(names)
    if value != want:
        sys.exit("the editor shows %r, not %r" % (value, want))
    print("%s: %s" % (sys.argv[1], want))
    to_face()


if __name__ == "__main__":
    main()
