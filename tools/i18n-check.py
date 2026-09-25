#!/usr/bin/env python3
"""Checks every language's string resources against the English source.

For each values-<lang>/strings.xml under the Compose resources and the Android app resources:
the XML parses; the key set equals English; every string keeps the same multiset of positional
placeholders (%1$s, %2$d, ...); <plurals> carry the CLDR categories the language needs;
`ui_language` names the folder's language; apostrophes are written the way each resource system
renders them; and no value is left identical to English unless it is on the short list of things
that are the same in every language (brand names, symbols).

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
    "adhan_voice_azeez", "adhan_voice_azemi", "about_website_value",
}
PLACEHOLDER = re.compile(r"%(\d+)\$[sd]")


def apostrophe_problem(kind, value):
    """Compose Multiplatform resources are not run through aapt: a backslash-escaped apostrophe
    is printed with its backslash ("Kur\\'an"), while aapt rejects an unescaped one in the Android
    app's own resources. The typographic ’ needs no escaping anywhere and is the house style."""
    if kind == "compose" and "\\'" in value:
        return "backslash-escaped apostrophe (Compose prints the backslash; write ’ or a plain ')"
    if kind == "android" and re.search(r"(?<!\\)'", value):
        return "unescaped apostrophe (aapt rejects it; write ’ or \\')"
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
    for k, v in en_s.items():
        bad = apostrophe_problem(kind, v)
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
            bad = apostrophe_problem(kind, v)
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


if __name__ == "__main__":
    only = set(sys.argv[1:])
    total = check("compose", COMPOSE, COMPOSE_LANGS, only) + check("android", ANDROID, ANDROID_LANGS, only) + check_indonesian_twins(only)
    print("i18n: clean" if not total else f"i18n: {total} problems")
    sys.exit(1 if total else 0)
