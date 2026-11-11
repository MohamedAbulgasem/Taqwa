"""For Ruling 51 item 1: candidate ARCH_GREGORIAN_STEPS tables, each measured with date-fit.py's own
model (its box bound, then row by row near 7 px), at each step's own box and baseline as
face-centre.py places it, over every right-to-left Gregorian form in phone-forms.json; and, for each
right-to-left tag, the share of the dates of 2027 (in the tag's first digit set) each step draws,
and how often the date's size changes from one day to the next (P2.2 review round 1: the size a
phone's date takes may change overnight, which a reader with low vision notices).

    python3 -I tables.py <repo> <dir holding NotoSansBengaliUI-VF.ttf>
"""
import collections, datetime, importlib.util, json, os, sys
REPO, fonts = sys.argv[1], sys.argv[2]
spec = importlib.util.spec_from_file_location("date_fit", os.path.join(REPO, "tools/watchface/date-fit.py"))
D = importlib.util.module_from_spec(spec); spec.loader.exec_module(D)
K = D.K
locales = json.load(open(os.path.join(REPO, "tools/watchface/phone-forms.json"), encoding="utf-8"))["locales"]
texts = sorted({"%s %s %s" % (wd, D.in_set(day, ds), m) for loc in locales.values() if loc["rtl"]
                for ds in loc["digitSets"] for wd in loc["weekdays"] for m in loc["months"] for day in range(1, 32)})
TABLES = {
    "today (one size)": [(None, 14 * K)],
    "2 steps, 14u kept where it can be": [(13, 14 * K), (None, 11.25 * K)],
    "3 steps (built)": [(13, 14 * K), (18, 13 * K), (None, 11.25 * K)],
    "2 steps, no 14u": [(18, 13 * K), (None, 11.25 * K)],
    # P2.2 review round 1: 14u wherever a length allows it (bylen.txt: 14 and 15 characters, and 19
    # or more, are under 7 px at 14u), so no table by length keeps 14u on more days; it steps up again.
    "non-monotonic, 14u at 16-18 too": [(13, 14 * K), (15, 13 * K), (18, 14 * K), (None, 11.25 * K)],
}
year = [datetime.date(2027, 1, 1) + datetime.timedelta(d) for d in range(365)]
for name, table in TABLES.items():
    D.FC.ARCH_GREGORIAN_STEPS = table
    steps = D.arch_steps(fonts)
    worst = {}
    under = 0
    for t in texts:
        most, line = D.step_for(steps, t)
        base = line.baselines[("arch", False)]
        c = D.box_bound(t, line, base)
        if c < 7.5:
            c = D.exact(t, line, base)
        under += c < 7
        w = D.layout(t, line)[0]
        k = (most, line.size)
        old = worst.get(k, (99, "", 0, "", 0))
        worst[k] = (min(old[0], c), t if c < old[0] else old[1], max(old[2], w), t if w > old[2] else old[3], old[4] + 1)
    print("%s: %d forms under 7 px" % (name, under))
    for (most, size), (c, ct, w, wt, n) in sorted(worst.items(), key=lambda kv: (kv[0][0] is None, kv[0][0] or 0)):
        print("  %-14s %5.2f px (%5.2f u) %6d forms; least room %5.1f px (%s); widest %5.1f px (%s)" % (
            "up to %d" % most if most else "longer", size, size / K, n, c, ct, w, wt))
    # the dates of 2027 each step draws, by tag (its first digit set): Sunday is weekdays[0]
    share = collections.Counter()
    for tag, loc in locales.items():
        if not loc["rtl"]:
            continue
        ds = loc["digitSets"][0]
        c = collections.Counter()
        sizes = []
        for d in year:
            t = "%s %s %s" % (loc["weekdays"][(d.weekday() + 1) % 7], D.in_set(d.day, ds), loc["months"][d.month - 1])
            sizes.append(D.step_for(steps, t)[1].size / K)
            c[sizes[-1]] += 1
        changes = sum(a != b for a, b in zip(sizes, sizes[1:]))
        print("    %-6s %s;  size changes overnight %3d times" % (
            tag, "  ".join("%5.2f u: %3d days" % (u, c[u]) for u in sorted(c, reverse=True)), changes))
    sys.stdout.flush()
