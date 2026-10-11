"""Build the Morning & Evening Adhkar resources from the OpenDua catalogue.

    python3 -I tools/adhkar/build_items.py [catalogue.json]

With a path to OpenDua's catalogue (v0.0.4, https://opendua.org/v0.0.4/catalogue.json),
the 24 entries tagged `morning-and-evening` (Hisn al-Muslim 75-98) are copied verbatim
into tools/adhkar/opendua-morning-evening.json under OpenDua's required attribution.
Without a path, that checked-in extract is the input. Either way the script then writes

    shared/src/commonMain/composeResources/files/adhkar/items.json     (19 items, book order)
    shared/src/commonMain/composeResources/files/adhkar/text/en.json   (15 du'a translations)

The Arabic, transliteration and English come from OpenDua; the sets, the daily flag,
the Quran references and the hadith sources come from the tables below (spec section
4.1, sources verified in the R1 canon notes), never from OpenDua. The Quran's own text is
never copied: the app reads it from quran.db, so the Quran items carry only their
reference (and, for Ayat al-Kursi, the isti'adhah Hisn prints before it).

Output is deterministic (sorted keys, UTF-8 without BOM, two-space indent, trailing
newline), so a rebuild from the same input is byte-identical.
"""

import json
import os
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
EXTRACT = os.path.join(HERE, "opendua-morning-evening.json")
RESOURCES = os.path.join(ROOT, "shared", "src", "commonMain", "composeResources", "files", "adhkar")
ITEMS_JSON = os.path.join(RESOURCES, "items.json")
EN_JSON = os.path.join(RESOURCES, "text", "en.json")

DATA_VERSION = "0.0.4"
TAG = "morning-and-evening"
ATTRIBUTION = (
    "OpenDua dataset v0.0.4 (https://opendua.org), by Ihsan Labs at the Human Development Fund. "
    "Based on Hisn al-Muslim by Sa'id ibn Ali ibn Wahf al-Qahtani. "
    "OpenDua editorial text and dataset structure are licensed CC BY 4.0. "
    "Recitation by Muhammad Jumah for OpenDua. "
    "OpenDua makes no ownership claim over the underlying religious texts."
)

MORNING, EVENING, BOTH = "morning", "evening", "both"
BOTH_SETS = (MORNING, EVENING)

# The 19 shipped items in the book's order (spec 4.1). Hisn 80-83, 87, 89 and 98 are
# graded weak or contested and are not shipped; nor is the evening form of 90.
# (id, OpenDua sourceReference, sets, daily counter)
ITEMS = [
    ("hisn-075", "75", BOTH_SETS, False),
    ("hisn-076a", "76", BOTH_SETS, False),
    ("hisn-076b", "76", BOTH_SETS, False),
    ("hisn-076c", "76", BOTH_SETS, False),
    ("hisn-077", "77", BOTH_SETS, False),
    ("hisn-078", "78", BOTH_SETS, False),
    ("hisn-079", "79", BOTH_SETS, False),
    ("hisn-084", "84", BOTH_SETS, False),
    ("hisn-085", "85", BOTH_SETS, False),
    ("hisn-086", "86", BOTH_SETS, False),
    ("hisn-088", "88", BOTH_SETS, False),
    ("hisn-090", "90", (MORNING,), False),  # the evening addition is shadhdh (al-Albani)
    ("hisn-091", "91", BOTH_SETS, False),
    ("hisn-092", "92", BOTH_SETS, False),
    ("hisn-093", "93", (MORNING,), False),  # "100 times when he enters the morning"
    ("hisn-094", "94", (MORNING,), False),  # after Fajr (Muslim 2726)
    ("hisn-095", "95", (MORNING,), False),  # after the salam of Fajr (Ibn Majah 925)
    ("hisn-096", "96", BOTH_SETS, True),    # 100 a day: one counter shared by both sets
    ("hisn-097", "97", (EVENING,), False),  # "three times when he enters the evening"
]

# Quran items: (surah, ayah or None for the whole surah, the prefix Hisn prints).
QURAN = {
    "hisn-075": (2, 255, "istiadhah"),
    "hisn-076a": (112, None, "basmala"),
    "hisn-076b": (113, None, "basmala"),
    "hisn-076c": (114, None, "basmala"),
}
# Hisn prints the isti'adhah before Ayat al-Kursi; it is not part of the ayah, so it is
# the one piece of Arabic a Quran item carries (taken from OpenDua, checked against this).
ISTIADHAH = "أَعُوذُ بِاللَّهِ مِنَ الشَّيطَانِ الرَّجِيمِ"
QURAN_OPEN = chr(0xFD3F)  # the ornate parenthesis OpenDua and Hisn put round Quranic text

# Spec 4.1's sources. Grading works (Sahih at-Targhib, as-Silsilah as-Sahihah) are not
# sources and the app shows no grades. Numbers the spec names only by collection come from
# the R1 canon notes: al-Hakim 1/562 (Hisn 75's own footnote), Ahmad 15360 and an-Nasa'i
# al-Kubra 9743 (Hisn 90), an-Nasa'i 'Amal al-Yawm 24 (Hisn 92).
SOURCES = {
    "hisn-075": [("quran", "2:255"), ("nasai", "al-Kubra 10731"), ("hakim", "1/562")],
    "hisn-076a": [("quran", "112"), ("abu_dawud", "5082"), ("tirmidhi", "3575")],
    "hisn-076b": [("quran", "113"), ("abu_dawud", "5082"), ("tirmidhi", "3575")],
    "hisn-076c": [("quran", "114"), ("abu_dawud", "5082"), ("tirmidhi", "3575")],
    "hisn-077": [("muslim", "2723")],
    "hisn-078": [("abu_dawud", "5068"), ("tirmidhi", "3391"), ("ibn_majah", "3868")],
    "hisn-079": [("bukhari", "6306")],
    "hisn-084": [("abu_dawud", "5074"), ("ibn_majah", "3871")],
    "hisn-085": [("tirmidhi", "3392"), ("abu_dawud", "5067")],
    "hisn-086": [("abu_dawud", "5088"), ("tirmidhi", "3388"), ("ibn_majah", "3869")],
    "hisn-088": [("hakim", "1/545")],
    "hisn-090": [("ahmad", "15360"), ("nasai", "al-Kubra 9743")],
    "hisn-091": [("muslim", "2692")],
    "hisn-092": [("nasai", "ʿAmal al-Yawm 24"), ("abu_dawud", "5077")],
    "hisn-093": [("bukhari", "6403"), ("muslim", "2691")],
    "hisn-094": [("muslim", "2726")],
    "hisn-095": [("ibn_majah", "925")],
    "hisn-096": [("bukhari", "6307"), ("muslim", "2702")],
    "hisn-097": [("abu_dawud", "3898"), ("ibn_majah", "3518")],
}
COLLECTIONS = {"quran", "bukhari", "muslim", "abu_dawud", "tirmidhi", "ibn_majah", "nasai", "hakim", "ahmad"}

# Read-through repairs of OpenDua's transliteration (its generator drops a long vowel
# where the Arabic leaves the vowel unwritten, and splits kalima as if it began with the
# preposition ka-). Each `old` must occur exactly once, so a fixed upstream text fails
# the build instead of being edited twice. The Arabic is never repaired here.
TRANSLITERATION_REPAIRS = {
    ("hisn-079", BOTH): [("adh-dhunwba", "adh-dhunūba")],
    ("hisn-085", BOTH): [("as-samawāti", "as-samāwāti"), ("ash-shayṭni", "ash-shayṭāni")],
    ("hisn-086", BOTH): [("as-smāʾi", "as-samāʾi")],
    ("hisn-088", BOTH): [("astaghythu", "astaghīthu")],
    ("hisn-090", MORNING): [("ka-limati", "kalimati")],
    ("hisn-094", BOTH): [("ka-li-mātih", "kalimātih")],
    ("hisn-097", BOTH): [("bi-ka-li-māti", "bi-kalimāti")],
}
# OpenDua's English for Hisn 96 alone is wrapped in quotation marks and spells "Allaah".
TRANSLATION_REPAIRS = {
    ("hisn-096", BOTH): [("‘I seek Allaah’s", "I seek Allah’s"), ("repentance.’", "repentance.")],
}

VARIATION_KEYS = {"standard": BOTH, "morning": MORNING, "evening": EVENING}


def fail(message):
    sys.stderr.write(f"build_items: {message}\n")
    sys.exit(1)


def write_json(path, value):
    text = json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print(f"wrote {os.path.relpath(path, ROOT)} ({len(text.encode('utf-8')):,} bytes)")


def extract(catalogue_path):
    """Copy the morning-and-evening entries out of the catalogue, verbatim."""
    with open(catalogue_path, encoding="utf-8") as f:
        catalogue = json.load(f)
    if catalogue.get("dataVersion") != DATA_VERSION:
        fail(f"expected OpenDua dataVersion {DATA_VERSION}, got {catalogue.get('dataVersion')!r}")
    entries = [e for e in catalogue["entries"] if TAG in (e.get("tags") or [])]
    refs = [e["sourceReference"] for e in entries]
    if refs != [str(n) for n in range(75, 99)]:
        fail(f"expected the 24 entries Hisn 75-98 under the {TAG} tag, got {refs}")
    write_json(EXTRACT, {
        "attribution": ATTRIBUTION,
        "dataVersion": catalogue["dataVersion"],
        "licences": catalogue["licences"],
        "released": catalogue["released"],
        "schemaVersion": catalogue["schemaVersion"],
        "source": f"https://opendua.org/v{DATA_VERSION}/catalogue.json, entries tagged {TAG}",
        "entries": entries,
    })


def recitation_of(entry, variation):
    """The one recited du'a of a variation: (dua, times)."""
    steps = [s for s in variation["steps"] if s.get("type") == "recitation"]
    if len(steps) != 1 or len(steps[0]["items"]) != 1:
        fail(f"Hisn {entry['sourceReference']} {variation['id']}: expected one recitation step with one item")
    item = steps[0]["items"][0]
    return item["dua"], item.get("times", 1)


def repaired(text, repairs, what):
    for old, new in repairs:
        if text.count(old) != 1:
            fail(f"{what}: expected {old!r} exactly once in {text!r}")
        text = text.replace(old, new)
    return text


def forms(item_id, entry, sets):
    """{key: (dua, times)} for the variations the item's sets read (standard -> both)."""
    out = {}
    for variation in entry["variations"]:
        key = VARIATION_KEYS[variation["id"]]
        if key == BOTH or key in sets:
            out[key] = recitation_of(entry, variation)
    if not out or (BOTH in out and len(out) > 1):
        fail(f"{item_id}: unexpected variations {[v['id'] for v in entry['variations']]}")
    for s in sets:
        if BOTH not in out and s not in out:
            fail(f"{item_id}: no wording for the {s} set")
    return out


def build(entries):
    by_ref = {e["sourceReference"]: e for e in entries}
    items, translations = [], {}
    for item_id, ref, sets, daily in ITEMS:
        entry = by_ref[ref]
        found = forms(item_id, entry, sets)
        counts = {times for _, times in found.values()}
        if len(counts) != 1:
            fail(f"{item_id}: morning and evening counts differ: {counts}")
        sources = SOURCES[item_id]
        if any(c not in COLLECTIONS for c, _ in sources):
            fail(f"{item_id}: unknown collection in {sources}")
        item = {
            "id": item_id,
            "hisn": int(ref),
            "sets": list(sets),
            "count": counts.pop(),
            "countDaily": daily,
            "sources": [{"collection": c, "number": n} for c, n in sources],
        }
        if item_id in QURAN:
            surah, ayah, prefix = QURAN[item_id]
            item["kind"] = "quran"
            item["quran"] = {"surah": surah, "prefix": prefix}
            if ayah is not None:
                item["quran"]["ayah"] = ayah
            if prefix == "istiadhah":
                arabic = found[BOTH][0]["arabic"]
                head = arabic[:arabic.index(QURAN_OPEN)].strip()
                if unicodedata.normalize("NFC", head) != unicodedata.normalize("NFC", ISTIADHAH):
                    fail(f"{item_id}: OpenDua's isti'adhah is {head!r}")
                item["arabic"] = {BOTH: head}
        else:
            item["kind"] = "dua"
            item["arabic"] = {k: dua["arabic"] for k, (dua, _) in found.items()}
            item["transliteration"] = {
                k: repaired(dua["transliteration"], TRANSLITERATION_REPAIRS.get((item_id, k), []),
                            f"{item_id} {k} transliteration")
                for k, (dua, _) in found.items()
            }
            translations[item_id] = {
                k: repaired(dua["translation"], TRANSLATION_REPAIRS.get((item_id, k), []),
                            f"{item_id} {k} translation")
                for k, (dua, _) in found.items()
            }
        items.append(item)
    unused = set(TRANSLITERATION_REPAIRS) | set(TRANSLATION_REPAIRS)
    unused -= {(i["id"], k) for i in items for k in i.get("transliteration", {})}
    if unused:
        fail(f"repairs for forms that are not built: {sorted(unused)}")
    return items, translations


def main():
    if len(sys.argv) > 2:
        fail("usage: build_items.py [catalogue.json]")
    if len(sys.argv) == 2:
        extract(sys.argv[1])
    with open(EXTRACT, encoding="utf-8") as f:
        source = json.load(f)
    if source["attribution"] != ATTRIBUTION or source["dataVersion"] != DATA_VERSION:
        fail(f"{EXTRACT} is not the OpenDua v{DATA_VERSION} extract")
    items, translations = build(source["entries"])
    write_json(ITEMS_JSON, {"schema": 1, "attribution": ATTRIBUTION, "items": items})
    write_json(EN_JSON, {"language": "en", "items": translations})
    morning = sum(1 for i in items if MORNING in i["sets"])
    evening = sum(1 for i in items if EVENING in i["sets"])
    print(f"{len(items)} items ({len(translations)} du'a, {len(items) - len(translations)} Quran); "
          f"{morning} morning pages, {evening} evening pages")
    return 0


if __name__ == "__main__":
    sys.exit(main())
