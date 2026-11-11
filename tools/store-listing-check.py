#!/usr/bin/env python3
"""Checks every docs/store/listing/<lang>.md against the stores' field limits and rules, and the
release notes and App Review notes that go with them.

    python3 tools/store-listing-check.py          # all languages, the notes too
    python3 tools/store-listing-check.py fr ur    # only these listings

A field is a `### Heading (limit)` followed by its text up to the next heading; lines in
parentheses are notes and are skipped. A field holding nothing but notes fails as empty (the App
Store description was once only "(The same text as the Play full description above.)"). Keywords
are also checked for spaces after commas, and are counted in UTF-8 bytes, not characters: App
Store Connect Help gives their limit as "up to 100 bytes", and an Arabic or Urdu letter takes two,
a Bengali one three.

Each store names only its own watch (plan 5 Task 10). App Store Review Guideline 2.3.10 forbids
naming other mobile platforms in App Store metadata, and build 34 was rejected once already; on
Play, an Android phone cannot use an Apple Watch. So no field under `## App Store` names Android,
Wear OS, Galaxy, Samsung, Google Play, the Play Store or Google (whose relay is Play's to name,
R63a), and no field under `## Google Play` names the Apple Watch, watchOS, the Digital Crown or
Apple, in any case ("android", "WEAR OS"). The names stay in Latin script in every language, so the
rule reads all seven. A name ends only at a letter
that is not Latin: Arabic and Urdu write a conjunction or a preposition onto it (ar.md's "وiPhone",
so "وGalaxy Watch", "بـGoogle"), and Bengali an ending ("Apple Watchএ"), and Python counts those
letters as word characters. A longer Latin word ("Googleplex") is no name. The shared review notes
and the screenshot captions are no store's field, and are not held to it.

What the App Store shows (its fields and its "What's New" sets) names Apple's features as Apple's
own localized guide does (APPLE_WORDS): Turkish "saat kadranı" and Indonesian "wajah jam" for the
watch face, where Wear OS says "saat yüzü" and "tampilan jam", and Smart Stack as Apple names it.
Every store's text, and the newest code's release notes, take the words Mohamed chose at M8
(CHOSEN_WORDS): Arabic says إضافات for complications, never مضاعفات.

With no languages named, it also checks:
- the App Store Connect notes file en.md's review notes point to (the backticked
  docs/store/app-review/<file>.txt, not the newest by name), which goes into App Review's Notes
  field whole: at most 4,000 UTF-8 bytes, and the App Store's names only;
- every docs/store/release-notes/<version>-<code>[-...].txt: Play's tag template, one block for
  each of the seven languages, each at most 500 characters; but a <version>-<code>-appstore.txt
  (main's separate App Store sets since 1.4.0 (40)) holds one `=== <localization> ===` block for
  each of the seven App Store localizations, each at most 4,000 characters (the What's New field),
  naming only the App Store's platforms;
- the newest phone code's two files, <V>-<C>.txt and the Wear OS track's <V>-<100000+C>-wear.txt,
  both there and naming no Apple Watch; and that C is no older than androidApp/build.gradle.kts's
  versionCode, with the same version once they are equal, so a code bump-version.sh sets cannot
  reach a store without its notes (erratum p5 #6: keyed to the newest notes code, since the bump
  comes only at the cut).
"""
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIELD = re.compile(r"^### (.+?) \((\d+)\)\s*$")
STORE = re.compile(r"^## (.+?)\s*$")
NOTES_LIMIT = 4000  # App Store Connect's Notes field, in UTF-8 bytes
RELEASE_NOTES_LIMIT = 500  # Play's release notes, per language, in characters
# Play's tag template, in the order every file since 1.0.0 (30) writes it.
RELEASE_NOTES_TAGS = ["en-GB", "ar", "fr-FR", "tr-TR", "id", "ur", "bn-BD"]
WEAR_CODE_OFFSET = 100000  # the Wear OS app's code is the phone's plus this (spec 2.1)
PHONE_NOTES = re.compile(r"^(\d+\.\d+\.\d+)-(\d+)\.txt$")
# main's App Store "What's New" sets (since 1.4.0 (40)): one `=== <localization> ===` block each, in
# this order, each at most the What's New field's 4,000 characters.
APPSTORE_NOTES = re.compile(r"^\d+\.\d+\.\d+-\d+-appstore\.txt$")
APPSTORE_LOCALIZATIONS = ["English (UK)", "Arabic", "French", "Turkish", "Indonesian", "Urdu", "Bengali"]
APPSTORE_WHATS_NEW_LIMIT = 4000
NOTES_POINTER = re.compile(r"`(docs/store/app-review/[^`]+\.txt)`")

# What each store's own text may not name: the other platform's watch, store and company.
NOT_ON = {
    "App Store": ["Android", "Wear OS", "Galaxy", "Samsung", "Google Play", "Play Store", "Google"],
    "Google Play": ["Apple Watch", "watchOS", "Digital Crown", "Apple"],
}


# What the App Store shows names Apple's features as Apple's own guide does in that language, not
# as Wear OS or a translator would (plan 5 Task 11, review round 1). Each pair is a word Apple does
# not use and Apple's own, from Apple's localized Apple Watch User Guide on support.apple.com
# (guide/watch/apdecf142fb9, "See timely widgets in the Smart Stack", and its contents' "Add
# complications to the watch face") and its iCloud page 108756 (Advanced Data Protection), read
# on 6 November 2026 in fr-fr, tr-tr, id-id and ar-sa. Apple's Urdu and Bengali guides (ur-in,
# bn-in) are in English, so those languages keep the English names. Arabic takes Apple's names
# for the Smart Stack and Advanced Data Protection, as Mohamed chose at M8 (6 November 2026, point
# A6).
APPLE_WORDS = {
    "ar": [("Smart Stack", "الحزمة المكدسة الذكية"), ("حماية البيانات المتقدمة", "الحماية المتقدمة للبيانات")],
    "fr": [("Pile intelligente", "défilement intelligent")],
    "tr": [("saat yüz", "saat kadranı"), ("Akıllı Yığın", "Akıllı Gruplama"),
           ("Gelişmiş Veri Koruma", "İleri Düzey Veri Koruma")],
    "id": [("tampilan jam", "wajah jam"), ("Tumpukan Pintar", "Smart Stack")],
}
LANGUAGE_NAMES = {"ar": "Arabic", "fr": "French", "tr": "Turkish", "id": "Indonesian"}

# The words Mohamed chose for a language at M8 (6 November 2026), in every store's text, about
# either watch: each pair is a word that goes and the one he chose. Arabic says إضافات for
# complications (point A3), the face editor's own word and Apple's ("إدراج إضافات إلى واجهة
# الساعة"), and never مضاعفات, which is also the medical word.
CHOSEN_WORDS = {"ar": [("مضاعف", "إضافات")]}
LOCALIZATION_LANGS = dict(zip(APPSTORE_LOCALIZATIONS, ["en", "ar", "fr", "tr", "id", "ur", "bn"]))


def folded(text, lang):
    """text in one case for comparing, as lang writes its capitals: Turkish capitals dotless ı as I
    and dotted i as İ, which casefold alone reads as i and i with a combining dot."""
    if lang == "tr":
        text = text.replace("I", "ı").replace("İ", "i")
    return text.casefold()


def not_apples(text, lang):
    """Messages for each word in text that Apple's own guide in lang does not use, in any case."""
    return [f"'{wrong}' is not Apple's word (Apple's {LANGUAGE_NAMES[lang]}: '{apple}')"
            for wrong, apple in APPLE_WORDS.get(lang, [])
            if folded(wrong, lang) in folded(text, lang)]


def not_chosen(text, lang):
    """Messages for each word in text that Mohamed's choice at M8 replaced in lang."""
    return [f"'{wrong}' is not the word Mohamed chose (M8: '{chosen}')"
            for wrong, chosen in CHOSEN_WORDS.get(lang, [])
            if folded(wrong, lang) in folded(text, lang)]


def names_in(text, store):
    """The other platform's names text holds, each once, in any case, between letters that are not
    Latin: an Arabic, Urdu or Bengali letter written onto a name does not hide it, and a longer Latin
    word is no name."""
    return [name for name in NOT_ON[store]
            if re.search(r"(?<![A-Za-z])" + re.escape(name) + r"(?![A-Za-z])", text, re.IGNORECASE)]


def check(path):
    problems = 0
    lang = os.path.basename(path)[:-3]
    lines = open(path, encoding="utf-8").read().split("\n")
    store = None
    i = 0
    while i < len(lines):
        s = STORE.match(lines[i])
        if s:
            store = s.group(1)
            i += 1
            continue
        m = FIELD.match(lines[i])
        if not m:
            i += 1
            continue
        name, limit = m.group(1), int(m.group(2))
        body = []
        i += 1
        while i < len(lines) and not lines[i].startswith("#"):
            body.append(lines[i])
            i += 1
        text = "\n".join(body).strip()
        text = "\n".join(l for l in text.split("\n") if not (l.startswith("(") and l.endswith(")"))).strip()
        keywords = name.startswith("Keywords")
        n = len(text.encode("utf-8")) if keywords else len(text)
        flag = "" if n <= limit else "  <-- OVER"
        if n > limit:
            problems += 1
        if keywords and ", " in text:
            print(f"{lang}: {name}: a space after a comma wastes characters")
            problems += 1
        print(f"{lang}: {name:20s} {n:4d}/{limit}{' bytes' if keywords else ''}{flag}")
        where = f"{lang}: {store}: {name}" if store else f"{lang}: {name}"
        if not text:
            print(f"{where}: empty (only notes), so nothing would be pasted")
            problems += 1
        if store in NOT_ON:
            found = names_in(text, store)
            if found:
                print(f"{where}: names {', '.join(found)} (each store names only its own watch)")
                problems += 1
        if store == "App Store":
            for message in not_apples(text, lang):
                print(f"{where}: {message}")
                problems += 1
        for message in not_chosen(text, lang):
            print(f"{where}: {message}")
            problems += 1
    return problems


def app_code(root):
    """androidApp's (versionCode, versionName)."""
    with open(os.path.join(root, "androidApp/build.gradle.kts"), encoding="utf-8") as f:
        text = f.read()
    code = re.search(r"^\s*versionCode = (\d+)\s*$", text, re.M)
    name = re.search(r'^\s*versionName = "([^"]+)"\s*$', text, re.M)
    return int(code.group(1)), name.group(1)


def notes_file(root):
    """The App Store Connect notes file en.md's review notes point to, relative to root."""
    with open(os.path.join(root, "docs/store/listing/en.md"), encoding="utf-8") as f:
        pointers = NOTES_POINTER.findall(f.read())
    return pointers[-1] if pointers else None


def check_notes(root=ROOT):
    """The notes file en.md points to, against the Notes field's 4,000 bytes and the App Store's
    platform rule."""
    rel = notes_file(root)
    if rel is None:
        print("app-review: en.md's review notes point to no docs/store/app-review/ file")
        return 1
    path = os.path.join(root, rel)
    if not os.path.exists(path):
        print(f"app-review: {os.path.basename(rel)}, which en.md points to, is not there")
        return 1
    data = open(path, "rb").read()
    n = len(data)
    over = n > NOTES_LIMIT
    print(f"app-review: {os.path.basename(rel)} {n:4d}/{NOTES_LIMIT} bytes{'  <-- OVER' if over else ''}")
    problems = 1 if over else 0
    found = names_in(data.decode("utf-8"), "App Store")
    if found:
        print(f"app-review: {os.path.basename(rel)}: names {', '.join(found)} (App Store text)")
        problems += 1
    return problems


def check_appstore_notes(name, text):
    """One App Store "What's New" set: each localization's block there, within the field's 4,000
    characters, and naming no other platform (App Store Review Guideline 2.3.10). A line in
    parentheses is a note, as in the listings' fields (as each block's draft marker was until M8,
    Ruling 71 item 4b): it is not pasted, so it is not the block's text, and a block of notes alone
    is empty."""
    problems = 0
    blocks = dict(re.findall(r"^=== (.+?) ===\n(.*?)(?=^=== |\Z)", text, re.S | re.M))
    for loc in APPSTORE_LOCALIZATIONS:
        if loc not in blocks:
            print(f"release-notes: {name}: no === {loc} === block")
            problems += 1
            continue
        body = "\n".join(l for l in blocks[loc].strip().split("\n")
                         if not (l.startswith("(") and l.endswith(")"))).strip()
        if not body:
            print(f"release-notes: {name}: {loc} is empty")
            problems += 1
        n = len(body)
        if n > APPSTORE_WHATS_NEW_LIMIT:
            print(f"release-notes: {name}: {loc}  {n}/{APPSTORE_WHATS_NEW_LIMIT}  <-- OVER")
            problems += 1
        for message in not_apples(body, LOCALIZATION_LANGS[loc]) + not_chosen(body, LOCALIZATION_LANGS[loc]):
            print(f"release-notes: {name}: {loc}: {message}")
            problems += 1
    found = names_in(text, "App Store")
    if found:
        print(f"release-notes: {name}: names {', '.join(found)} (App Store's notes)")
        problems += 1
    return problems


def check_release_notes(root=ROOT):
    """Every release-notes file's seven blocks at 500 characters, and the newest phone code's two
    files, against the phone's version lines."""
    problems = 0
    folder = os.path.join(root, "docs/store/release-notes")
    files = sorted(os.path.basename(p) for p in glob.glob(os.path.join(folder, "*.txt")))
    for name in files:
        with open(os.path.join(folder, name), encoding="utf-8") as f:
            text = f.read()
        if APPSTORE_NOTES.match(name):
            problems += check_appstore_notes(name, text)
            continue
        blocks = dict(re.findall(r"<([A-Za-z-]+)>\n?(.*?)\n?</\1>", text, re.S))
        for tag in RELEASE_NOTES_TAGS:
            if tag not in blocks:
                print(f"release-notes: {name}: no <{tag}> block")
                problems += 1
                continue
            n = len(blocks[tag].strip())
            if n > RELEASE_NOTES_LIMIT:
                print(f"release-notes: {name}: {tag}  {n}/{RELEASE_NOTES_LIMIT}  <-- OVER")
                problems += 1
        extra = sorted(set(blocks) - set(RELEASE_NOTES_TAGS))
        if extra:
            print(f"release-notes: {name}: blocks Play's template has no place for: {', '.join(extra)}")
            problems += 1

    phone = [(int(m.group(2)), m.group(1)) for m in map(PHONE_NOTES.match, files)
             if m and int(m.group(2)) < WEAR_CODE_OFFSET]
    code, version = app_code(root)
    if not phone:
        print(f"release-notes: none for the phone; its code is {code}")
        return problems + 1
    newest, newest_version = max(phone)
    pair = [f"{newest_version}-{newest}.txt", f"{newest_version}-{WEAR_CODE_OFFSET + newest}-wear.txt"]
    before = problems
    for name in pair:
        if name not in files:
            print(f"release-notes: {name} is missing (the newest code's {'phone' if name == pair[0] else 'Wear OS'} notes)")
            problems += 1
            continue
        with open(os.path.join(folder, name), encoding="utf-8") as f:
            text = f.read()
        found = names_in(text, "Google Play")
        if found:
            print(f"release-notes: {name}: names {', '.join(found)} (Play's notes)")
            problems += 1
        for tag, block in re.findall(r"<([A-Za-z-]+)>\n?(.*?)\n?</\1>", text, re.S):
            for message in not_chosen(block, tag.split("-")[0]):
                print(f"release-notes: {name}: {tag}: {message}")
                problems += 1
    if newest < code:
        print(f"release-notes: androidApp's code is {code} ({version}), and the newest notes are for {newest}: "
              f"write {version}-{code}.txt and {version}-{WEAR_CODE_OFFSET + code}-wear.txt")
        problems += 1
    elif newest == code and newest_version != version:
        print(f"release-notes: the notes for code {code} are {newest_version}'s, and androidApp says {version}")
        problems += 1
    elif problems == before:
        print(f"release-notes: {len(files)} files; the newest code, {newest} ({newest_version}), has both")
    return problems


def main(argv, root=ROOT):
    only = set(argv)
    total = 0
    for path in sorted(glob.glob(os.path.join(root, "docs/store/listing/*.md"))):
        if only and os.path.basename(path)[:-3] not in only:
            continue
        total += check(path)
    if not only:
        total += check_notes(root)
        total += check_release_notes(root)
    print("listing: clean" if not total else f"listing: {total} problems")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
