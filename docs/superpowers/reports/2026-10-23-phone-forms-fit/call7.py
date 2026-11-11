import importlib.util, math, os, sys
REPO, fonts = sys.argv[1], sys.argv[2]
spec = importlib.util.spec_from_file_location("moon_fit", os.path.join(REPO, "tools/watchface/moon-fit.py"))
MF = importlib.util.module_from_spec(spec); spec.loader.exec_module(MF)
f = MF.font_files(fonts)
path = MF.COUNT_PATH[1] - MF.COUNT_PATH[0]
room = path - 2 * math.degrees(3 / MF.M.R_COUNT)
print("the Moon's countdown path %.1f deg, its room at min=3 px %.1f deg" % (path, room))
for lang, first, short in [("fr", "dans moins d’une minute", "d’ici une minute"),
                           ("id", "kurang dari semenit lagi", "kurang dari semenit"),
                           ("bn", "এক মিনিটের কম সময়ে".replace("মিনিটের", "মিনিটেরও"), "এক মিনিটের কম সময়ে")]:
    a1 = MF.along(first, "count", False, f)[1]
    a2 = MF.along(short, "count", False, f)[1]
    print("  %s  first draft %-26s %6.1f deg   shorter (call 7) %-22s %6.1f deg, %.1f px to spare at each end" % (
        lang, first, a1, short, a2, (path - a2) / 2 * math.pi / 180 * MF.M.R_COUNT))
