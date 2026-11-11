#!/usr/bin/env python3
"""Checks every language's string resources against the English source.

For each values-<lang>/strings.xml under the Compose resources, the Android app's resources and
the watch's two Android roots (the Galaxy watch app's and the Taqwa face's own, plan 5 Task 8):
the XML parses; the key set equals English; every string keeps the same multiset of positional
placeholders (%1$s, %2$d, ...); <plurals> carry the CLDR categories the language needs;
`ui_language` names the folder's language; apostrophes are written the way each resource system
renders them; the ** marks around words shown in bold come in pairs, in every string English marks;
the widget names the Widgets screen's steps tell the reader to look for are the names the system's
widget lists show; the iPhone's buttons those steps name in bold are the words iOS itself shows; and
no value is left identical to English unless it is on the short list of
things that are the same in every language (brand names, symbols).

Every .lproj set under iosApp (the iPhone app, its widgets, the Apple Watch app and its widgets,
and any target added later, found by itself) is checked the same way: the seven folders and no
other, each with English's tables, and each table parses and carries English's keys and
placeholders, with no value empty. On the Apple Watch targets the app's name is the iPhone's own,
and in every set a word the phone already has is the phone's.

    python3 tools/i18n-check.py            # all languages
    python3 tools/i18n-check.py fr ur      # only these
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COMPOSE = os.path.join(ROOT, "shared/src/commonMain/composeResources")
ANDROID = os.path.join(ROOT, "androidApp/src/main/res")
# What the watch system shows from the watch's own resources, in the watch's language and often
# before any feed (spec 1.3): the Galaxy watch app's name, pickers and first-run lines, and the
# Taqwa face's name and face editor names. Android roots, in ANDROID_LANGS' folders; neither
# carries ui_language, since the watch speaks the phone's (the rule below fires only where it is).
WEAR = os.path.join(ROOT, "wearApp/src/main/res")
FACE = os.path.join(ROOT, "watchface/src/main/res")

# Folder name -> language code the file must declare in ui_language. Android's resource system
# still spells Indonesian "in", the Compose resources and every other system say "id".
COMPOSE_LANGS = {"ar": "ar", "fr": "fr", "tr": "tr", "id": "id", "in": "id", "ur": "ur", "bn": "bn"}
ANDROID_LANGS = {"ar": "ar", "fr": "fr", "tr": "tr", "in": "id", "ur": "ur", "bn": "bn"}

# CLDR plural categories per language (cardinal).
PLURAL_CATEGORIES = {
    "en": {"one", "other"},
    "ar": {"zero", "one", "two", "few", "many", "other"},
    "fr": {"one", "other"},          # CLDR also has "many" for 1e6+, never reached by a minute count
    "tr": {"one", "other"},
    "id": {"other"},
    "ur": {"one", "other"},
    "bn": {"one", "other"},
}

# Values allowed to be identical to English.
SAME_OK = {
    "about_licence_value", "about_source_value", "qibla_bearing", "recitation_arriving_voice",
    "recitation_percent", "settings_version_value", "ui_language", "app_name",
    "adhan_voice_azeez", "adhan_voice_azemi", "adhan_voice_makkah", "adhan_voice_madinah", "adhan_voice_makkah_detail", "adhan_voice_madinah_detail", "adhan_voice_custom_detail", "about_website_value",
    # The watch's: the brand, and the pickers' previews in Western digits.
    "watch_face_name", "complication_mark", "complication_mark_description",
    "preview_prayer_time", "preview_day_number",
}
PLACEHOLDER = re.compile(r"%(\d+)\$[sd]")
BOLD_MARK = "**"


def apostrophe_problem(kind, value):
    """Compose Multiplatform resources are not run through aapt: a backslash-escaped apostrophe
    is printed with its backslash ("Kur\\'an"), while aapt rejects an unescaped one in the Android
    app's own resources. The typographic ’ needs no escaping anywhere and is the house style."""
    if kind == "compose" and "\\'" in value:
        return "backslash-escaped apostrophe (Compose prints the backslash; write ’ or a plain ')"
    if kind == "android" and re.search(r"(?<!\\)'", value):
        return "unescaped apostrophe (aapt rejects it; write ’ or \\')"
    return None


def bold_problem(kind, value, english=None):
    """The Compose strings mark words to show in bold with ** on each side (the Widgets screen's
    steps, `markedText`): an odd count would turn the rest of the sentence bold and print a star,
    and a translation that drops the marks English has loses the bold the reader looks for."""
    if kind != "compose":
        return None
    if value.count(BOLD_MARK) % 2:
        return "an odd number of ** marks (they come in pairs around the words in bold)"
    if english is not None and BOLD_MARK in english and BOLD_MARK not in value:
        return "English marks words in bold with ** and this translation marks none"
    return None


def load(path):
    tree = ET.parse(path)
    strings, plurals = {}, {}
    for el in tree.getroot():
        if el.tag == "string":
            strings[el.get("name")] = el.text or ""
        elif el.tag == "plurals":
            plurals[el.get("name")] = {item.get("quantity"): item.text or "" for item in el}
    return strings, plurals


def check(kind, base_dir, folder_langs, only):
    problems = 0
    notes = []
    en_s, en_p = load(os.path.join(base_dir, "values/strings.xml"))
    if kind == "android" and os.path.isdir(os.path.join(base_dir, "values-id")):
        print(f"{os.path.relpath(base_dir, ROOT)}/values-id: Android resources spell Indonesian 'in'")
        problems += 1
    for k, v in en_s.items():
        bad = apostrophe_problem(kind, v) or bold_problem(kind, v)
        if bad:
            print(f"{os.path.relpath(base_dir, ROOT)}/values/strings.xml: {k}: {bad}")
            problems += 1
    for folder, lang in sorted(folder_langs.items()):
        if only and lang not in only:
            continue
        path = os.path.join(base_dir, f"values-{folder}/strings.xml")
        rel = os.path.relpath(path, ROOT)
        if not os.path.exists(path):
            print(f"{rel}: missing")
            problems += 1
            continue
        try:
            s, p = load(path)
        except ET.ParseError as e:
            print(f"{rel}: XML does not parse: {e}")
            problems += 1
            continue
        missing = sorted(set(en_s) - set(s))
        extra = sorted(set(s) - set(en_s))
        for k in missing:
            print(f"{rel}: missing key {k}")
        for k in extra:
            print(f"{rel}: extra key {k}")
        problems += len(missing) + len(extra)
        for k, v in s.items():
            if k not in en_s:
                continue
            if sorted(PLACEHOLDER.findall(en_s[k])) != sorted(PLACEHOLDER.findall(v)):
                print(f"{rel}: {k}: placeholders differ (en {PLACEHOLDER.findall(en_s[k])}, {lang} {PLACEHOLDER.findall(v)})")
                problems += 1
            if "%%" in v or re.search(r"(?<!%\d\$)%(?![\d\s])", v.replace("%1$s%", "")):
                pass  # a lone % is a literal in Compose resources; nothing to flag
            # A note, not a failure: proper nouns, "Fajr", "Qibla" and "Notifications" are the same
            # word in several of the app's languages, and the person reading the report decides.
            if v.strip() == en_s[k].strip() and k not in SAME_OK and re.search(r"[A-Za-z]{3,}", v):
                notes.append(f"{rel}: {k}: same as English ({v[:40]!r})")
            bad = apostrophe_problem(kind, v) or bold_problem(kind, v, en_s[k])
            if bad:
                print(f"{rel}: {k}: {bad}")
                problems += 1
        if "ui_language" in s and s["ui_language"] != lang:
            print(f"{rel}: ui_language is {s['ui_language']!r}, expected {lang!r}")
            problems += 1
        for name, items in en_p.items():
            if name not in p:
                print(f"{rel}: missing plurals {name}")
                problems += 1
                continue
            want = PLURAL_CATEGORIES.get(lang, {"other"})
            have = set(p[name])
            if have != want:
                print(f"{rel}: plurals {name}: categories {sorted(have)}, expected {sorted(want)}")
                problems += 1
            for q, text in p[name].items():
                if sorted(PLACEHOLDER.findall(text)) != sorted(PLACEHOLDER.findall(en_p[name].get("other", ""))):
                    print(f"{rel}: plurals {name}[{q}]: placeholders differ")
                    problems += 1
        print(f"{rel}: {len(s)} strings, {len(p)} plurals checked" + (f", {len(notes)} same-as-English notes" if notes else ""))
        for note in notes:
            print("  note: " + note)
        notes = []
    return problems


def check_indonesian_twins(only):
    """Compose resources are looked up by `Locale.getLanguage()` on Android, which still answers
    the legacy "in" for Indonesian, and by the BCP-47 "id" on iOS, so the Indonesian strings ship
    twice under the Compose resources: values-id is the source, values-in its byte-identical twin
    (`tools/sync-indonesian.py` copies it)."""
    if only and "id" not in only:
        return 0
    src = os.path.join(COMPOSE, "values-id/strings.xml")
    twin = os.path.join(COMPOSE, "values-in/strings.xml")
    if not os.path.exists(src):
        return 0
    if not os.path.exists(twin) or open(src, "rb").read() != open(twin, "rb").read():
        print("shared/src/commonMain/composeResources/values-in/strings.xml: must be identical to values-id (run tools/sync-indonesian.py)")
        return 1
    return 0


# Every folder under iosApp that holds .lproj folders is a set: the iPhone app's, its widgets', and
# the Apple Watch targets' (plan 4), whose tables are what the watch system shows and the two lines
# the watch shows before it holds a feed, in the *watch's* language (spec 1.3). Each set has the
# seven .lproj folders and no other, each with English's tables. Build output is not a set.
IOS = os.path.join(ROOT, "iosApp")
IOS_SKIP = {"build", ".build", "DerivedData", "Pods"}
WATCH_TARGETS = ("iosApp/TaqwaWatch", "iosApp/TaqwaWatchWidgets")
# The tables each Apple Watch target must have: its own words, and its name in the watch's lists.
WATCH_TABLES = ("Localizable.strings", "InfoPlist.strings")
# The sets the app has today, which must still be found (a set gone is not a set passed).
KNOWN_SETS = ("iosApp/iosApp", "iosApp/TaqwaWidget") + WATCH_TARGETS
IOS_LANGS = ("en", "ar", "fr", "tr", "id", "ur", "bn")
# A watch word that is the phone's own: its key in the Compose resources, which it must equal.
PHONE_WORDS = {"next_prayer_widget_name": "prayer_a11y_prayer_next"}
# Where a language takes another of the phone's words: the Android app's key it must equal there.
# M8 point A4 (6 November 2026): Mohamed chose one phrase for "Next prayer" on both watches in
# Arabic, الصلاة القادمة, the phone's widget label, which the Galaxy watch's complication, face
# and Tile already say.
PHONE_WORDS_IN = {("next_prayer_widget_name", "ar"): "widget_small_label"}
# The two lines a watch shows before it holds a feed, which both watches say alike in each
# language: each Apple Watch target's equals the Galaxy watch app's own (M8 point A1, 6 November
# 2026: one spelling of the update line on both).
WATCH_NOTICES = ("open_taqwa_on_phone", "update_taqwa_on_watch")
IOS_PLACEHOLDER = re.compile(r"%(?:\d+\$)?(?:@|l{0,2}[du]|s)")
STRINGS_TOKEN = re.compile(r'\s+|/\*.*?\*/|//[^\n]*|"((?:[^"\\]|\\.)*)"|=|;', re.S)


def parse_strings(path):
    """An Apple .strings table, read without Apple's tools (CI checks strings on Linux): quoted
    keys and values, `=`, `;`, comments. Returns {key: value}, or raises ValueError."""
    text = open(path, encoding="utf-8").read()
    items, expect, key, pos = {}, "key", None, 0
    while pos < len(text):
        m = STRINGS_TOKEN.match(text, pos)
        if not m:
            raise ValueError(f"unreadable at character {pos}: {text[pos:pos + 20]!r}")
        pos = m.end()
        token = m.group(0)
        if token.isspace() or token.startswith("/*") or token.startswith("//"):
            continue
        quoted = m.group(1)
        if expect == "key" and quoted is not None:
            key, expect = quoted, "="
        elif expect == "=" and token == "=":
            expect = "value"
        elif expect == "value" and quoted is not None:
            if key in items:
                raise ValueError(f"key {key!r} twice")
            items[key] = quoted
            expect = ";"
        elif expect == ";" and token == ";":
            expect = "key"
        else:
            raise ValueError(f"expected {expect} at character {m.start()}, found {token[:20]!r}")
    if expect != "key":
        raise ValueError(f"ends inside an entry (expected {expect})")
    return items


def lproj_sets():
    """Each folder under iosApp holding .lproj folders, relative to the root, in order."""
    sets = []
    for dirpath, dirnames, _ in os.walk(IOS):
        dirnames[:] = sorted(d for d in dirnames if d not in IOS_SKIP and not d.endswith((".xcodeproj", ".xcworkspace", ".xcassets")))
        if any(d.endswith(".lproj") for d in dirnames):
            sets.append(os.path.relpath(dirpath, ROOT))
        dirnames[:] = [d for d in dirnames if not d.endswith(".lproj")]
    return sets


def check_ios_sets(only):
    problems = 0
    phone_names = {}
    for lang in IOS_LANGS:
        try:
            phone_names[lang] = parse_strings(os.path.join(ROOT, f"iosApp/iosApp/{lang}.lproj/InfoPlist.strings")).get("CFBundleDisplayName")
        except (OSError, ValueError):
            phone_names[lang] = None
    sets = lproj_sets()
    for target in KNOWN_SETS:
        if target not in sets:
            print(f"{target}: no .lproj folders")
            problems += 1
    for target in sets:
        base = os.path.join(ROOT, target)
        folders = {d[:-len(".lproj")] for d in os.listdir(base) if d.endswith(".lproj")}
        if not only:
            for lang in sorted(folders - set(IOS_LANGS)):
                print(f"{target}/{lang}.lproj: not one of the seven languages ({', '.join(IOS_LANGS)})")
                problems += 1
        if "en" not in folders:
            print(f"{target}/en.lproj: missing")
            problems += 1
            continue
        tables = sorted(f for f in os.listdir(os.path.join(base, "en.lproj")) if f.endswith(".strings"))
        for table in WATCH_TABLES if target in WATCH_TARGETS else ():
            if table not in tables:
                print(f"{target}/en.lproj/{table}: missing")
                problems += 1
        for lang in IOS_LANGS:
            if only and lang not in only:
                continue
            if lang not in folders:
                print(f"{target}/{lang}.lproj: missing")
                problems += 1
                continue
            for table in sorted(f for f in os.listdir(os.path.join(base, f"{lang}.lproj")) if f.endswith(".strings")):
                if table not in tables:
                    print(f"{target}/{lang}.lproj/{table}: extra table (en.lproj has none)")
                    problems += 1
        for table in tables:
            en_path = os.path.join(base, "en.lproj", table)
            try:
                en = parse_strings(en_path)
            except ValueError as e:
                print(f"{target}/en.lproj/{table}: does not parse: {e}")
                problems += 1
                continue
            for lang in IOS_LANGS:
                if only and lang not in only or lang not in folders:
                    continue
                rel = f"{target}/{lang}.lproj/{table}"
                try:
                    s = en if lang == "en" else parse_strings(os.path.join(ROOT, rel))
                except OSError:
                    print(f"{rel}: missing")
                    problems += 1
                    continue
                except ValueError as e:
                    print(f"{rel}: does not parse: {e}")
                    problems += 1
                    continue
                for k in sorted(set(en) - set(s)):
                    print(f"{rel}: missing key {k}")
                    problems += 1
                for k in sorted(set(s) - set(en)):
                    print(f"{rel}: extra key {k}")
                    problems += 1
                for k, v in s.items():
                    if k in en and sorted(IOS_PLACEHOLDER.findall(en[k])) != sorted(IOS_PLACEHOLDER.findall(v)):
                        print(f"{rel}: {k}: placeholders differ (en {IOS_PLACEHOLDER.findall(en[k])}, {lang} {IOS_PLACEHOLDER.findall(v)})")
                        problems += 1
                    if not v.strip():
                        print(f"{rel}: {k}: empty")
                        problems += 1
                if target in WATCH_TARGETS and table == "InfoPlist.strings" and s.get("CFBundleDisplayName") != phone_names[lang]:
                    print(f"{rel}: CFBundleDisplayName is {s.get('CFBundleDisplayName')!r}, the iPhone's is {phone_names[lang]!r}")
                    problems += 1
                for k, compose_key in PHONE_WORDS.items():
                    if k not in s:
                        continue
                    folder = "values" if lang == "en" else f"values-{lang}"
                    if (k, lang) in PHONE_WORDS_IN:
                        compose_key = PHONE_WORDS_IN[(k, lang)]
                        android_folder = "values" if lang == "en" else f"values-{'in' if lang == 'id' else lang}"
                        phone, _ = load(os.path.join(ANDROID, android_folder, "strings.xml"))
                    else:
                        phone, _ = load(os.path.join(COMPOSE, folder, "strings.xml"))
                    if s[k] != phone.get(compose_key):
                        print(f"{rel}: {k} is {s[k]!r}, the phone's {compose_key} is {phone.get(compose_key)!r}")
                        problems += 1
                if target in WATCH_TARGETS and table == "Localizable.strings":
                    wear_folder = "values" if lang == "en" else f"values-{'in' if lang == 'id' else lang}"
                    wear, _ = load(os.path.join(WEAR, wear_folder, "strings.xml"))
                    for k in WATCH_NOTICES:
                        if k in s and s[k] != wear.get(k):
                            print(f"{rel}: {k} is {s[k]!r}, the Galaxy watch's is {wear.get(k)!r} (both watches say it alike)")
                            problems += 1
            print(f"{target}/*.lproj/{table}: {len(en)} keys checked")
    return problems


IOS_WIDGET = os.path.join(ROOT, "iosApp/TaqwaWidget")

# The Widgets screen's steps name a widget by its card's name, a Compose string; each must be the
# name the system's own widget list shows for it, or the reader is sent to look for a label that is
# not there: (Compose key, Android picker label key, iPhone gallery name key). The iPhone's gallery
# listed the prayer widget as "Taqwa" until the owner renamed it "Prayer times" (31 Oct 2026), so
# that the steps read naturally with the card's own name.
WIDGET_NAMES = [
    ("widgets_prayer_name", "widget_medium_label", "home_widget_name"),
    ("widgets_ayah_name", "widget_ayah_label", "ayah_widget_name"),
]


def lproj_strings(lang):
    path = os.path.join(IOS_WIDGET, f"{lang}.lproj/Localizable.strings")
    if not os.path.exists(path):
        return None
    pairs = re.findall(r'^"([^"]+)"\s*=\s*"(.*)";\s*$', open(path, encoding="utf-8").read(), re.M)
    return dict(pairs)


def check_widget_names(only):
    problems = 0
    for lang in ["en"] + sorted(set(COMPOSE_LANGS.values())):
        if only and lang not in only:
            continue
        compose_folder = "values" if lang == "en" else f"values-{lang}"
        android_folder = "values" if lang == "en" else "values-" + next(f for f, l in ANDROID_LANGS.items() if l == lang)
        compose, _ = load(os.path.join(COMPOSE, compose_folder, "strings.xml"))
        android, _ = load(os.path.join(ANDROID, android_folder, "strings.xml"))
        ios = lproj_strings(lang)
        if ios is None:
            print(f"iosApp/TaqwaWidget/{lang}.lproj/Localizable.strings: missing")
            problems += 1
            continue
        for key, android_key, ios_key in WIDGET_NAMES:
            ours = compose.get(key)
            for system, system_key, theirs in (("Android", android_key, android), ("iPhone", ios_key, ios)):
                if system_key and ours != theirs.get(system_key):
                    print(f"{compose_folder}/strings.xml: {key} is {ours!r} but the {system} widget list says "
                          f"{theirs.get(system_key)!r} ({system_key}); the steps would name a widget that is not there")
                    problems += 1
    return problems


# The iPhone's own words for the two buttons on the way to its widget gallery from an empty part of
# the Home Screen, as SpringBoardHome titles them (keys EDIT and EDIT_MENU_ADD_WIDGET), read from
# the iOS 26.2 and 27.0 simulator runtimes on 31 Oct 2026 and seen on Dev 1's simulators. The
# corner button shows that title on iOS 18 and 26: a word in most languages, but "+" in French and
# Turkish (VoiceOver still says "Modifier", "Düzenle"). From iOS 27 it is a brush in every
# language, which the fallback line says in words. iOS 16 and 17 have a "+" in that corner that
# opens the gallery straight away, with no menu, so the plus-button step names the "+" and nothing
# else.
IOS_EDIT = {"en": "Edit", "ar": "تعديل", "bn": "এডিট", "fr": "+", "id": "Edit", "tr": "+", "ur": "ترمیم"}
IOS_ADD_WIDGET = {
    "en": "Add Widget", "ar": "إضافة أداة", "bn": "উইজেট যোগ করুন", "fr": "Ajouter un widget",
    "id": "Tambah Widget", "tr": "Araç Takımı Ekle", "ur": "ویجیٹ شامل کریں",
}


def bold_runs(value):
    return value.split(BOLD_MARK)[1::2]


def check_ios_buttons(only):
    """The iPhone steps' bold words are what the reader will look for on the phone, so each must be
    the word iOS shows in that language: the fallback line names the corner button and then Add
    Widget, and the plus-button step (iOS 16 and 17) names only the "+", since nothing more opens
    there. Every language says the same thing this way, each in its own iPhone's words."""
    problems = 0
    for lang in ["en"] + sorted(set(COMPOSE_LANGS.values())):
        if only and lang not in only:
            continue
        folder = "values" if lang == "en" else f"values-{lang}"
        compose, _ = load(os.path.join(COMPOSE, folder, "strings.xml"))
        expected = {
            "widgets_ios_fallback": [IOS_EDIT[lang], IOS_ADD_WIDGET[lang]],
            "widgets_step_ios_plus_2": ["+"],
        }
        for key, words in expected.items():
            found = bold_runs(compose.get(key, ""))
            if found != words:
                print(f"{folder}/strings.xml: {key} names {found} in bold, but the iPhone shows {words}")
                problems += 1
    return problems


if __name__ == "__main__":
    only = set(sys.argv[1:])
    total = (
        check("compose", COMPOSE, COMPOSE_LANGS, only)
        + check("android", ANDROID, ANDROID_LANGS, only)
        + check("android", WEAR, ANDROID_LANGS, only)
        + check("android", FACE, ANDROID_LANGS, only)
        + check_indonesian_twins(only)
        + check_ios_sets(only)
        + check_widget_names(only)
        + check_ios_buttons(only)
    )
    print("i18n: clean" if not total else f"i18n: {total} problems")
    sys.exit(1 if total else 0)
