"""Prints glyph advances (in em) of some characters of a TTF, and its vertical metrics, with a small
hand-written reader (no fontTools here). Usage: python3 -I tools/watchface/ttf.py <font.ttf> [chars]"""
import struct
import sys

data = open(sys.argv[1], "rb").read()
chars = sys.argv[2] if len(sys.argv) > 2 else "0123456789:"
num_tables = struct.unpack(">H", data[4:6])[0]
tables = {}
for i in range(num_tables):
    tag, _, off, length = struct.unpack(">4sIII", data[12 + 16 * i: 28 + 16 * i])
    tables[tag.decode()] = (off, length)

upem = struct.unpack(">H", data[tables["head"][0] + 18: tables["head"][0] + 20])[0]
hhea = tables["hhea"][0]
asc, desc, gap = struct.unpack(">hhh", data[hhea + 4: hhea + 10])
n_metrics = struct.unpack(">H", data[hhea + 34: hhea + 36])[0]
hmtx = tables["hmtx"][0]
os2 = tables.get("OS/2")
cap = xh = None
if os2:
    o = os2[0]
    typo_asc, typo_desc, typo_gap = struct.unpack(">hhh", data[o + 68: o + 74])
    win_asc, win_desc = struct.unpack(">HH", data[o + 74: o + 78])
    version = struct.unpack(">H", data[o: o + 2])[0]
    if version >= 2:
        xh, cap = struct.unpack(">hh", data[o + 86: o + 90])
    print("OS/2 typo asc/desc/gap", typo_asc, typo_desc, typo_gap, "win", win_asc, win_desc, "xheight", xh, "cap", cap)
print("upem", upem, "hhea asc/desc/gap", asc, desc, gap)

# cmap: find a format 4 or 12 subtable for Unicode
cm = tables["cmap"][0]
n_sub = struct.unpack(">H", data[cm + 2: cm + 4])[0]
mapping = {}
for i in range(n_sub):
    pid, eid, off = struct.unpack(">HHI", data[cm + 4 + 8 * i: cm + 12 + 8 * i])
    sub = cm + off
    fmt = struct.unpack(">H", data[sub: sub + 2])[0]
    if fmt == 4 and not mapping:
        seg_x2 = struct.unpack(">H", data[sub + 6: sub + 8])[0]
        seg = seg_x2 // 2
        ends = struct.unpack(">%dH" % seg, data[sub + 14: sub + 14 + seg_x2])
        starts = struct.unpack(">%dH" % seg, data[sub + 16 + seg_x2: sub + 16 + 2 * seg_x2])
        deltas = struct.unpack(">%dh" % seg, data[sub + 16 + 2 * seg_x2: sub + 16 + 3 * seg_x2])
        range_off_pos = sub + 16 + 3 * seg_x2
        ranges = struct.unpack(">%dH" % seg, data[range_off_pos: range_off_pos + seg_x2])
        for c in chars:
            cp = ord(c)
            for s in range(seg):
                if starts[s] <= cp <= ends[s]:
                    if ranges[s] == 0:
                        g = (cp + deltas[s]) & 0xFFFF
                    else:
                        p = range_off_pos + 2 * s + ranges[s] + 2 * (cp - starts[s])
                        g = struct.unpack(">H", data[p: p + 2])[0]
                        if g:
                            g = (g + deltas[s]) & 0xFFFF
                    mapping[c] = g
                    break
for c in chars:
    g = mapping.get(c)
    if g is None:
        print(repr(c), "not found")
        continue
    idx = min(g, n_metrics - 1)
    adv, lsb = struct.unpack(">Hh", data[hmtx + 4 * idx: hmtx + 4 * idx + 4])
    print(repr(c), "glyph", g, "advance %.3f em" % (adv / upem), "lsb %.3f" % (lsb / upem))
