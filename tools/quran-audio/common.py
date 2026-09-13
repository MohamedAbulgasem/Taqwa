"""Shared configuration and helpers for the Taqwa Quran-audio pipeline.

The pipeline mirrors the Islamic Network per-ayah recitation corpus
(https://cdn.islamic.network/quran/audio/{folder}/{id}/{globalAyah}.mp3),
verifies every file, packs one `.taqa` container per (reciter, surah) with the
MP3 bytes untouched, and publishes the containers as GitHub Release assets.

Nothing here re-encodes audio: the licence permits redistribution only "at the
bitrates we publish", so the downloaded bytes are copied verbatim into the
container.
"""

import json
import os
import sys
import time
import urllib.request

UA = "Taqwa-mirror/0.1 (free open-source app; github.com/MohamedAbulgasem/Taqwa)"

WORK = os.environ.get(
    "TAQWA_AUDIO_WORK",
    "/private/tmp/claude-501/-Users-mohamedabulgasem-Desktop-Workspace-apps/"
    "bbf8ad7a-0b95-43b1-89c1-af0f595af640/scratchpad/corpus",
)

REPO = "MohamedAbulgasem/Taqwa-data"
BASE = "https://github.com/MohamedAbulgasem/Taqwa-data/releases/download/"

# id, CDN folder, true measured kbps, inter-ayah gap ms, monogram hue,
# English name, Arabic name.  Order is the manifest order.
# The order here is the order the app lists reciters in: manifest.py writes it as
# is and the picker draws the manifest as is. Mohamed's order of 13 September.
RECITERS = [
    ("ar.alafasy",            "64",  64,  300, "amber", "Mishary Rashid Alafasy",     "مشاري راشد العفاسي"),
    ("ar.shaatree",           "128", 128,  80, "teal",  "Abu Bakr Ash-Shatri",        "أبو بكر الشاطري"),
    ("ar.mahermuaiqly",       "128", 64,  300, "plum",  "Maher Al Muaiqly",           "ماهر المعيقلي"),
    ("ar.abdulbasitmurattal", "64",  64,   80, "moss",  "Abdul Basit Abdus-Samad",    "عبد الباسط عبد الصمد"),
    ("ar.minshawi",           "128", 128,  80, "sky",   "Mohamed Siddiq Al-Minshawi", "محمد صديق المنشاوي"),
    ("ar.husary",             "64",  64,   80, "clay",  "Mahmoud Khalil Al-Husary",   "محمود خليل الحصري"),
    ("ar.saoodshuraym",       "64",  128, 300, "rose",  "Saud Ash-Shuraim",           "سعود الشريم"),
    ("ar.abdurrahmaansudais", "64",  64,  300, "olive", "Abdur-Rahman As-Sudais",     "عبد الرحمن السديس"),
    ("ar.hudhaify",           "128", 128,  80, "sand",  "Ali Al-Hudhaify",            "علي الحذيفي"),
    # The 64 kbps folder, not the 128: the 128 edition is broken at source in several places
    # (9:62 is an MPEG-video fragment, 50:9 and 50:10 are stubs, three ayahs of 77 are 32 kbps
    # at 11 kHz) and the 64 kbps folder is the same recording, whole - cross-correlation 0.95 on
    # the ayahs compared, 13 September.
    ("ar.ahmedajamy",         "64",  64,   80, "slate", "Ahmed Al-Ajmi",              "أحمد العجمي"),
]

STYLE = "murattal"


def reciter(rid):
    for r in RECITERS:
        if r[0] == rid:
            return r
    raise SystemExit(f"unknown reciter {rid}")


def outdir(rid):
    r = reciter(rid)
    return os.path.join(WORK, f"{rid}-{r[1]}")


def packdir(rid):
    return os.path.join(WORK, "containers", rid)


def surahs():
    """[(surah number, ayah count)] from the alquran.cloud meta endpoint, cached."""
    path = os.path.join(WORK, "meta.json")
    if not os.path.exists(path):
        req = urllib.request.Request(
            "https://api.alquran.cloud/v1/meta", headers={"User-Agent": UA}
        )
        with urllib.request.urlopen(req, timeout=60) as r:
            data = r.read()
        os.makedirs(WORK, exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)
    meta = json.load(open(path))
    refs = meta["data"]["surahs"]["references"]
    out = [(s["number"], s["numberOfAyahs"]) for s in refs]
    assert len(out) == 114 and sum(n for _, n in out) == 6236
    return out


def surah_ranges():
    """{surah: (first global ayah, last global ayah)}."""
    rs, n = {}, 1
    for s, count in surahs():
        rs[s] = (n, n + count - 1)
        n += count
    return rs


def log(msg):
    line = f"{time.strftime('%Y-%m-%d %H:%M:%S')} {msg}"
    print(line, flush=True)
    with open(os.path.join(WORK, "pipeline.log"), "a") as f:
        f.write(line + "\n")


def die(msg):
    log("FATAL " + msg)
    sys.exit(1)
