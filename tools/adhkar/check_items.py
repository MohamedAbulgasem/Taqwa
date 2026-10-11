"""Check the adhkar resources against the spec's table and against the Hisn text.

    python3 -I tools/adhkar/check_items.py [hisnmuslim-chapter-27.json]

The argument is hisnmuslim.com's chapter 27, "أذكار الصباح والمساء"
(http://www.hisnmuslim.com/api/ar/27.json, UTF-8 with a BOM). It is a verification
witness only: it carries no licence, so it is never checked in. Without it the
structural checks still run and the text comparison is skipped with a warning; a path
that is given but does not exist is a failure. Exit status 1 on any failure.

Structure (always): the 19 ids in the book's order with the spec's kind, sets, count,
daily flag and Quran reference; 18 morning and 15 evening pages; sources from the nine
known collections; every set of every du'a resolves to an Arabic text, a transliteration
and an English translation (the 15 du'a ids, and only those, in text/en.json); no empty
strings; every Arabic string carries tashkeel (U+064B-U+0652); the Arabic is OpenDua's,
byte for byte (tools/adhkar/opendua-morning-evening.json); no file starts with a BOM.

Languages (always): every text/<lang>.json present (a language may have no file) is
{"language": "<lang>", "items": ...} carrying exactly the 15 du'a ids, each with exactly
its Arabic's keys (morning/evening or both), no empty or padded string, every string NFC
and no ASCII apostrophe (typographic ’ only).

Text (with the witness): each du'a's morning or "both" Arabic is compared word by word,
letter and mark, with the dhikr of the hisnmuslim entry of the same ID; the
isti'adhah before Ayat al-Kursi likewise; every count with the entry's REPEAT.

Known legitimate differences, allowed (and printed whenever the witness is read):
1. hisnmuslim.com wraps the dhikr in ((...)), follows it with count notes in
   parentheses such as (ثلاثَ مرَّاتٍ), and adds the evening wording in
   [وإذا أمسى قال: ...] notes. All three are stripped before comparing.
2. Those evening notes have no tashkeel, so the evening forms are not compared with
   them mark for mark. Instead: the evening Arabic must be OpenDua's own evening
   variation, its letters must equal the note applied to the morning wording (ANCHORS),
   every word it shares with the morning form must carry exactly the morning form's
   marks, and its remaining words must be the reviewed EVENING_ONLY_WORDS.
3. Whitespace: hisnmuslim.com puts a space before some commas and doubles some spaces.
4. OpenDua's fuller vocalisation and punctuation, item by item (ALLOWED below): Hisn 77
   writes the kasra under the hamza of illa; Hisn 78 has a comma after namutu; Hisn 90
   writes the fatha before the alif of asbahna and the sukun on the shin of
   al-mushrikin. None changes a letter (the check enforces that) or the reading, so
   OpenDua's text is kept. A listed difference that no longer occurs is a failure too.
"""

import difflib
import json
import os
import re
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
EXTRACT = os.path.join(HERE, "opendua-morning-evening.json")
RESOURCES = os.path.join(ROOT, "shared", "src", "commonMain", "composeResources", "files", "adhkar")
ITEMS_JSON = os.path.join(RESOURCES, "items.json")
TEXT_DIR = os.path.join(RESOURCES, "text")
EN_JSON = os.path.join(TEXT_DIR, "en.json")

ATTRIBUTION = (
    "OpenDua dataset v0.0.4 (https://opendua.org), by Ihsan Labs at the Human Development Fund. "
    "Based on Hisn al-Muslim by Sa'id ibn Ali ibn Wahf al-Qahtani. "
    "OpenDua editorial text and dataset structure are licensed CC BY 4.0. "
    "Recitation by Muhammad Jumah for OpenDua. "
    "OpenDua makes no ownership claim over the underlying religious texts."
)

M, E = "morning", "evening"
ME = (M, E)
# The spec's table (section 4.1), independent of build_items.py:
# (id, hisn, kind, sets, count, countDaily, (surah, ayah, prefix) or None)
EXPECTED = [
    ("hisn-075", 75, "quran", ME, 1, False, (2, 255, "istiadhah")),
    ("hisn-076a", 76, "quran", ME, 3, False, (112, None, "basmala")),
    ("hisn-076b", 76, "quran", ME, 3, False, (113, None, "basmala")),
    ("hisn-076c", 76, "quran", ME, 3, False, (114, None, "basmala")),
    ("hisn-077", 77, "dua", ME, 1, False, None),
    ("hisn-078", 78, "dua", ME, 1, False, None),
    ("hisn-079", 79, "dua", ME, 1, False, None),
    ("hisn-084", 84, "dua", ME, 1, False, None),
    ("hisn-085", 85, "dua", ME, 1, False, None),
    ("hisn-086", 86, "dua", ME, 3, False, None),
    ("hisn-088", 88, "dua", ME, 1, False, None),
    ("hisn-090", 90, "dua", (M,), 1, False, None),
    ("hisn-091", 91, "dua", ME, 100, False, None),
    ("hisn-092", 92, "dua", ME, 10, False, None),
    ("hisn-093", 93, "dua", (M,), 100, False, None),
    ("hisn-094", 94, "dua", (M,), 3, False, None),
    ("hisn-095", 95, "dua", (M,), 1, False, None),
    ("hisn-096", 96, "dua", ME, 100, True, None),
    ("hisn-097", 97, "dua", (E,), 3, False, None),
]
COLLECTIONS = {"quran", "bukhari", "muslim", "abu_dawud", "tirmidhi", "ibn_majah", "nasai", "hakim", "ahmad"}

# Known difference 4: (hisnmuslim words, OpenDua words, why it is not a real difference).
ALLOWED = {
    77: [("إلاَّ", "إِلاَّ", "OpenDua writes the kasra under the hamza of illa")],
    78: [("", "،", "OpenDua puts a comma after namutu; the Hisn print has one only in the evening note")],
    90: [
        ("أَصْبَحْنا", "أَصْبَحْنَا", "OpenDua writes the fatha before the alif"),
        ("الْمُشرِكِينَ", "الْمُشْرِكِينَ", "OpenDua writes the sukun on the shin"),
    ],
}
# Known difference 2: the morning words (letters only) each [وإذا أمسى قال: ...] note
# replaces, in the notes' order; None means the note is the whole evening wording.
ANCHORS = {
    "hisn-077": [
        "أصبحنا وأصبح الملك لله",
        "رب أسألك خير ما في هذا اليوم وخير ما بعده وأعوذ بك من شر ما في هذا اليوم وشر ما بعده",
    ],
    "hisn-078": [None],
}
# The evening words that do not occur in the morning form, read by eye.
EVENING_ONLY_WORDS = {
    "hisn-077": ["أَمْسَيْنَا", "وَأَمْسَى", "هَذِهِ", "اللَّيْلَةِ", "بَعْدَهَا"],
    "hisn-078": ["الْمَصِيرُ"],
}

TASHKEEL = {chr(c) for c in range(0x064B, 0x0653)}
UNWRITTEN = TASHKEEL | {chr(0x0670), chr(0x0640)}  # marks, dagger alif, tatweel: not letters
PUNCTUATION = "،؛:.*"
QURAN_OPEN = chr(0xFD3F)  # the ornate parenthesis that opens Quranic text

failures = []


def fail(message):
    failures.append(message)
    print(f"FAIL {message}")


def nfc(text):
    return unicodedata.normalize("NFC", text)


def tokens(text):
    """Words and punctuation marks, whitespace-insensitive."""
    return re.sub(f"([{PUNCTUATION}])", r" \1 ", text).split()


def skeleton(text):
    """The letters alone: no marks, no punctuation, single spaces."""
    bare = "".join(c for c in unicodedata.normalize("NFD", text) if c not in UNWRITTEN)
    return " ".join(re.sub(f"[{PUNCTUATION}]", " ", nfc(bare)).split())


def words(text):
    return [t for t in tokens(text) if t not in PUNCTUATION]


def load(path):
    with open(path, "rb") as f:
        raw = f.read()
    if raw.startswith(b"\xef\xbb\xbf"):
        fail(f"{os.path.relpath(path, ROOT)} starts with a BOM")
    # utf-8-sig: the BOM is recorded above; reading past it lets the other checks still run.
    return json.loads(raw.decode("utf-8-sig"))


def walk_strings(value, where):
    if isinstance(value, str):
        if not value or value != value.strip():
            fail(f"{where}: empty or padded string {value!r}")
    elif isinstance(value, dict):
        for k, v in value.items():
            walk_strings(v, f"{where}.{k}")
    elif isinstance(value, list):
        for i, v in enumerate(value):
            walk_strings(v, f"{where}[{i}]")


def text_for(forms, s):
    return forms.get("both", forms.get(s))


def check_forms(item_id, sets, forms, what):
    keys = set(forms)
    if not (keys == {"both"} or (keys and keys <= set(sets))):
        fail(f"{item_id}: {what} keys {sorted(keys)} do not fit sets {list(sets)}")
    for s in sets:
        if text_for(forms, s) is None:
            fail(f"{item_id}: no {what} for the {s} set")


def opendua_forms(extract):
    """{hisn: {both|morning|evening: arabic}} from the checked-in OpenDua extract."""
    out = {}
    for entry in extract["entries"]:
        forms = {}
        for v in entry["variations"]:
            step = next(s for s in v["steps"] if s.get("type") == "recitation")
            forms[{"standard": "both"}.get(v["id"], v["id"])] = step["items"][0]["dua"]["arabic"]
        out[int(entry["sourceReference"])] = forms
    return out


def check_structure(items_doc, en_doc, extract):
    walk_strings(items_doc, "items.json")
    walk_strings(en_doc, "en.json")
    if set(items_doc) != {"schema", "attribution", "items"} or items_doc["schema"] != 1:
        fail(f"items.json top level: {sorted(items_doc)} schema {items_doc.get('schema')}")
    if items_doc["attribution"] != ATTRIBUTION:
        fail("items.json attribution is not OpenDua's required text")
    items = items_doc["items"]
    ids = [i["id"] for i in items]
    if ids != [e[0] for e in EXPECTED]:
        fail(f"ids {ids} are not the spec's 19 in book order")
    morning = sum(1 for i in items if M in i["sets"])
    evening = sum(1 for i in items if E in i["sets"])
    if (morning, evening) != (18, 15):
        fail(f"{morning} morning and {evening} evening pages, expected 18 and 15")
    od = opendua_forms(extract)
    by_id = {i["id"]: i for i in items}
    dua_ids = []
    for item_id, hisn, kind, sets, count, daily, quran in EXPECTED:
        item = by_id.get(item_id)
        if item is None:
            continue
        got = (item.get("hisn"), item.get("kind"), tuple(item.get("sets", ())), item.get("count"), item.get("countDaily"))
        if got != (hisn, kind, sets, count, daily):
            fail(f"{item_id}: (hisn, kind, sets, count, countDaily) = {got}, expected {(hisn, kind, sets, count, daily)}")
        sources = item.get("sources") or []
        if not sources or any(set(s) != {"collection", "number"} or s["collection"] not in COLLECTIONS for s in sources):
            fail(f"{item_id}: bad sources {sources}")
        arabic = item.get("arabic", {})
        for key, text in arabic.items():
            if not any(c in TASHKEEL for c in text):
                fail(f"{item_id}: {key} Arabic has no tashkeel")
        if kind == "quran":
            surah, ayah, prefix = quran
            expected_quran = {"surah": surah, "prefix": prefix, **({"ayah": ayah} if ayah else {})}
            if item.get("quran") != expected_quran:
                fail(f"{item_id}: quran {item.get('quran')}, expected {expected_quran}")
            reference = f"{surah}:{ayah}" if ayah else str(surah)
            if sources[:1] != [{"collection": "quran", "number": reference}]:
                fail(f"{item_id}: first source should be quran {reference}")
            keys = {"id", "hisn", "kind", "sets", "count", "countDaily", "quran", "sources"}
            if prefix == "istiadhah":
                keys.add("arabic")
                head = od[hisn]["both"]
                if arabic != {"both": head[:head.index(QURAN_OPEN)].strip()}:
                    fail(f"{item_id}: the isti'adhah is not OpenDua's")
            if set(item) != keys:
                fail(f"{item_id}: keys {sorted(item)}, expected {sorted(keys)}")
        else:
            dua_ids.append(item_id)
            keys = {"id", "hisn", "kind", "sets", "count", "countDaily", "arabic", "transliteration", "sources"}
            if set(item) != keys:
                fail(f"{item_id}: keys {sorted(item)}, expected {sorted(keys)}")
            check_forms(item_id, sets, arabic, "Arabic")
            check_forms(item_id, sets, item.get("transliteration", {}), "transliteration")
            if set(item.get("transliteration", {})) != set(arabic):
                fail(f"{item_id}: transliteration keys differ from the Arabic's")
            for key, text in arabic.items():
                if text != od[hisn].get(key):
                    fail(f"{item_id}: {key} Arabic is not OpenDua's {key} variation byte for byte")
            translation = en_doc.get("items", {}).get(item_id, {})
            check_forms(item_id, sets, translation, "English translation")
            if set(translation) != set(arabic):
                fail(f"{item_id}: en.json keys {sorted(translation)} differ from the Arabic's {sorted(arabic)}")
            if E in arabic:
                check_evening_marks(item_id, arabic)
    if en_doc.get("language") != "en" or set(en_doc) != {"language", "items"}:
        fail(f"en.json top level {sorted(en_doc)} language {en_doc.get('language')!r}")
    if sorted(en_doc.get("items", {})) != sorted(dua_ids) or len(dua_ids) != 15:
        fail(f"en.json has {sorted(en_doc.get('items', {}))}, expected the 15 du'a ids {sorted(dua_ids)}")
    print(f"structure: {len(items)} items, {len(dua_ids)} du'a, {morning} morning pages, {evening} evening pages")


def check_languages(items_doc, en_doc):
    """Every text/<lang>.json present carries every du'a id with exactly its Arabic's keys."""
    duas = {i["id"]: i for i in items_doc["items"] if i.get("kind") == "dua"}
    languages = []
    for name in sorted(n for n in os.listdir(TEXT_DIR) if n.endswith(".json")):
        lang, where = name[:-len(".json")], f"text/{name}"
        languages.append(lang)
        doc = en_doc if lang == "en" else load(os.path.join(TEXT_DIR, name))
        if lang != "en":  # check_structure has walked en.json already
            walk_strings(doc, where)
        if set(doc) != {"language", "items"} or doc.get("language") != lang:
            fail(f"{where}: top level {sorted(doc)} language {doc.get('language')!r}, expected {lang!r}")
        items = doc.get("items", {})
        missing, extra = sorted(set(duas) - set(items)), sorted(set(items) - set(duas))
        if missing or extra:
            fail(f"{where}: missing du'a ids {missing}, ids that are not du'a {extra}")
        for item_id, item in duas.items():
            forms = items.get(item_id)
            if forms is None:
                continue
            if not isinstance(forms, dict) or set(forms) != set(item["arabic"]):
                fail(f"{where}: {item_id} keys {sorted(forms) if isinstance(forms, dict) else forms!r}, "
                     f"expected the Arabic's {sorted(item['arabic'])}")
                continue
            for key, text in forms.items():  # walk_strings has reported empty and padded strings
                if not isinstance(text, str):
                    fail(f"{where}: {item_id} {key} is not a string")
                elif unicodedata.normalize("NFC", text) != text:
                    fail(f"{where}: {item_id} {key} is not NFC")
                elif "'" in text:
                    fail(f"{where}: {item_id} {key} has an ASCII apostrophe; use ’")
    print(f"languages: {' '.join(languages)}; each file present carries the {len(duas)} du'a ids with their Arabic's keys")


def check_evening_marks(item_id, arabic):
    """Known difference 2: shared words carry the morning marks; the rest were read by eye."""
    marks_of = {}
    for w in words(arabic[M]):
        marks_of.setdefault(skeleton(w), set()).add(nfc(w))
    only = []
    for w in words(arabic[E]):
        known = marks_of.get(skeleton(w))
        if known is None:
            only.append(nfc(w))
        elif nfc(w) not in known:
            fail(f"{item_id}: evening word {w} is marked differently from the morning's {sorted(known)}")
    expected = sorted({nfc(w) for w in EVENING_ONLY_WORDS.get(item_id, [])})
    if sorted(set(only)) != expected:
        fail(f"{item_id}: evening-only words {sorted(set(only))}, reviewed list {expected}")
    else:
        print(f"ok {item_id} evening: shared words marked as the morning; evening-only words {' '.join(expected)}")


def dhikr_of(raw):
    """Known difference 1: the words between (( and )), without notes."""
    text = re.sub(r"\[[^\]]*\]", " ", raw)
    if "((" in text:
        text = text[text.index("((") + 2:text.rindex("))")]
    text = re.sub(r"\([^()]*\)", " ", text)
    return " ".join(text.split()).rstrip(" .")


def evening_notes(raw):
    return [note.split(":", 1)[1].strip() for note in re.findall(r"\[([^\]]*)\]", raw) if ":" in note]


def compare(label, hisn, witness, ours):
    """Word-by-word comparison; only the item's ALLOWED differences pass."""
    a, b = tokens(nfc(witness)), tokens(nfc(ours))  # canonically equivalent mark orders are the same text
    allowed = ALLOWED.get(hisn, [])
    used = set()
    ok = True
    for op, i1, i2, j1, j2 in difflib.SequenceMatcher(None, a, b, autojunk=False).get_opcodes():
        if op == "equal":
            continue
        theirs, mine = " ".join(a[i1:i2]), " ".join(b[j1:j2])
        match = next((k for k, (x, y, _) in enumerate(allowed) if nfc(x) == nfc(theirs) and nfc(y) == nfc(mine)), None)
        if match is not None and skeleton(theirs) == skeleton(mine):
            used.add(match)
            print(f"  allowed {label}: hisnmuslim {theirs or '(nothing)'} -> OpenDua {mine} ({allowed[match][2]})")
            continue
        ok = False
        detail = " ".join(f"U+{ord(c):04X}" for c in theirs) + " -> " + " ".join(f"U+{ord(c):04X}" for c in mine)
        fail(f"{label}: hisnmuslim {theirs!r} vs OpenDua {mine!r} [{detail}]\n"
             f"    hisnmuslim: {' '.join(a)}\n    OpenDua:    {' '.join(b)}")
    return ok, used


def check_witness(path, items_doc):
    with open(path, "rb") as f:
        doc = json.loads(f.read().decode("utf-8-sig"))
    chapter = {e["ID"]: e for e in next(iter(doc.values()))}
    used_allowed = {}
    for item in items_doc["items"]:
        hisn, item_id = item["hisn"], item["id"]
        entry = chapter.get(hisn)
        if entry is None:
            fail(f"{item_id}: no hisnmuslim entry {hisn}")
            continue
        if entry["REPEAT"] != item["count"]:
            fail(f"{item_id}: count {item['count']}, hisnmuslim REPEAT {entry['REPEAT']}")
        raw = entry["ARABIC_TEXT"]
        if item["kind"] == "quran":
            if "arabic" in item:
                ok, _ = compare(f"{item_id} isti'adhah", hisn, raw[:raw.index(QURAN_OPEN)], item["arabic"]["both"])
                if ok:
                    print(f"ok {item_id} isti'adhah: identical to hisnmuslim")
            continue
        arabic = item["arabic"]
        key = "both" if "both" in arabic else M
        ok, used = compare(f"{item_id} {key}", hisn, dhikr_of(raw), arabic[key])
        used_allowed.setdefault(hisn, set()).update(used)
        if ok:
            print(f"ok {item_id} {key}: identical to hisnmuslim" + (" after the allowed differences" if used else ""))
        if E in arabic:
            notes, anchors = evening_notes(raw), ANCHORS.get(item_id, [])
            if len(notes) != len(anchors):
                fail(f"{item_id}: {len(notes)} evening notes, {len(anchors)} anchors")
                continue
            expected = skeleton(dhikr_of(raw))
            for note, anchor in zip(notes, anchors):
                if anchor is None:
                    expected = skeleton(note)
                elif expected.count(anchor) != 1:
                    fail(f"{item_id}: anchor {anchor!r} is not once in the morning wording")
                else:
                    expected = expected.replace(anchor, skeleton(note))
            if skeleton(arabic[E]) != expected:
                fail(f"{item_id} evening letters differ from the hisnmuslim note\n"
                     f"    hisnmuslim: {expected}\n    OpenDua:    {skeleton(arabic[E])}")
            else:
                print(f"ok {item_id} evening: letters identical to the hisnmuslim note")
        elif evening_notes(raw) and E in item["sets"]:
            fail(f"{item_id}: hisnmuslim has an evening wording the item does not carry")
    for hisn, allowed in ALLOWED.items():
        for k, (x, y, _) in enumerate(allowed):
            if k not in used_allowed.get(hisn, set()):
                fail(f"Hisn {hisn}: allowed difference {x!r} -> {y!r} no longer occurs; remove it")
    print(f"witness: {len(chapter)} hisnmuslim entries read, counts and Arabic compared")


def main():
    if len(sys.argv) > 2:
        print("usage: check_items.py [hisnmuslim-chapter-27.json]")
        return 2
    items_doc, en_doc, extract = load(ITEMS_JSON), load(EN_JSON), load(EXTRACT)
    check_structure(items_doc, en_doc, extract)
    check_languages(items_doc, en_doc)
    witness = sys.argv[1] if len(sys.argv) == 2 else None
    if witness is None:
        print("WARNING: hisnmuslim chapter JSON not given; the Arabic was NOT compared with the Hisn text")
    elif not os.path.isfile(witness):
        # A path that was meant to be checked against and is not there must not pass as a skipped check.
        fail(f"hisnmuslim chapter JSON not found at {witness}; the Arabic was NOT compared with the Hisn text")
    else:
        check_witness(witness, items_doc)
    print(f"check_items: {len(failures)} failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
