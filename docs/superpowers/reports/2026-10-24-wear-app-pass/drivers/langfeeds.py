"""The Qibla page's words in each of the seven languages, as hand-made feeds (Ruling 68 item 4).

The debug build's samples are English and Arabic alone (SampleFeed.NAMES), so for the captions over a
turning dial in the other languages this writes the English sample (watchcore/vectors) with the
Qibla page's words put in from the phone's strings (shared composeResources) and the watch's own
(WatchWords.kt), the language's direction and digits, and Cape Town's 6,600 km as the phone writes it
(DigitGrouping's mark). Only what the Qibla page reads is changed; the feed is written into the work
directory and put on the watch by run-as, as large.sh puts its feeds, never into an APK (Ruling 55
item 6).

    python3 -I langfeeds.py <out dir>     # writes qibla-<form>.json for every form below

Two more, for the device log of the detail's air (errata p3 #18, Ruling 67 item 3): qibla-bn-west-4400
(Bengali in Western digits, 4,400 km, the sweep's tightest case at 1.3), and qibla-en-long (English
with the phone's long phrase "Makkah · %1$s km away" put in the dial's short form, so that no size
keeps the air and Ruling 66 item 5's fade acts at some headings).
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", "..", "..", ".."))
STRINGS = os.path.join(REPO, "shared", "src", "commonMain", "composeResources")
WATCH_WORDS = os.path.join(REPO, "shared", "src", "commonMain", "kotlin", "world", "taqwa", "app", "watchfeed", "WatchWords.kt")
SAMPLE = os.path.join(REPO, "watchcore", "vectors", "sample-feed-capetown.json")

# form: (strings folder, WatchWords block, ui.language, rtl, digit zero, the mark between thousands)
FORMS = {
    "en": ("values", "ENGLISH", "en", False, "0", ","),
    "fr": ("values-fr", "FRENCH", "fr", False, "0", " "),
    "tr": ("values-tr", "TURKISH", "tr", False, "0", "."),
    "id": ("values-id", "INDONESIAN", "id", False, "0", "."),
    "ar-sa": ("values-ar", "ARABIC", "ar-SA", True, "٠", "٬"),
    "ur": ("values-ur", "URDU", "ur", True, "0", ","),
    "bn": ("values-bn", "BENGALI", "bn", False, "০", ","),
}
# The page's words: the feed's field, and the phone's string it carries.
PHONE = {
    "qibla": "qibla_title", "facingQibla": "qibla_facing_qibla", "distance": "qibla_distance",
    "bearing": "qibla_bearing", "qiblaDetail": "prayer_qibla_detail",
    "needsCalibrating": "qibla_needs_calibrating", "interference": "qibla_interference_title",
    "bestEffort": "qibla_best_effort_title", "noSensor": "qibla_no_sensor_title",
}
WATCH = ["holdLevel", "distanceOnDial", "qiblaDetailOnDial"]
CARDINALS = ["qibla_cardinal_north", "qibla_cardinal_east", "qibla_cardinal_south", "qibla_cardinal_west"]


def phone(folder, key):
    xml = open(os.path.join(STRINGS, folder, "strings.xml"), encoding="utf-8").read()
    m = re.search(r'<string name="%s">(.*?)</string>' % key, xml)
    if not m:
        sys.exit("langfeeds.py: %s has no %s" % (folder, key))
    return m.group(1).replace("\\'", "'")


def watch(block, field):
    text = open(WATCH_WORDS, encoding="utf-8").read()
    screens = text[text.index("private val SCREENS"):]
    start = screens.index("UiLanguage.%s to WatchScreenWords(" % block)
    end = screens.find("UiLanguage.", start + 1)
    m = re.search(r'%s = "([^"]*)"' % field, screens[start:end if end > 0 else len(screens)])
    if not m:
        sys.exit("langfeeds.py: no %s for %s" % (field, block))
    return m.group(1).replace("\\$", "$")


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for name, (folder, block, language, rtl, zero, mark) in FORMS.items():
        feed = json.load(open(SAMPLE, encoding="utf-8"))
        screens = feed["ui"]["screens"]
        for field, key in PHONE.items():
            screens[field] = phone(folder, key)
        for field in WATCH:
            screens[field] = watch(block, field)
        screens["cardinals"] = [phone(folder, k) for k in CARDINALS]
        feed["ui"]["language"] = language
        feed["ui"]["rtl"] = rtl
        feed["ui"]["digitZero"] = zero
        digits = "".join(chr(ord(zero) + int(c)) if c.isdigit() else c for c in "6" + mark + "600")
        feed["place"]["qiblaKm"] = digits
        path = os.path.join(out, "qibla-%s.json" % name)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(feed, f, ensure_ascii=False, separators=(",", ":"))
        print("%s: holdLevel %r, facing %r, %s km" % (os.path.basename(path), screens["holdLevel"], screens["facingQibla"], digits))
        if name == "bn":
            feed["ui"]["digitZero"] = "0"
            feed["place"]["qiblaKm"] = "4,400"
            with open(os.path.join(out, "qibla-bn-west-4400.json"), "w", encoding="utf-8") as f:
                json.dump(feed, f, ensure_ascii=False, separators=(",", ":"))
        if name == "en":
            screens["distanceOnDial"] = screens["distance"]
            with open(os.path.join(out, "qibla-en-long.json"), "w", encoding="utf-8") as f:
                json.dump(feed, f, ensure_ascii=False, separators=(",", ":"))


if __name__ == "__main__":
    main()
