#!/usr/bin/env python3
"""Writes the Apple Watch's art from the app's own (plan 4, Task 4), so the watch never carries a
copy that has drifted from the phone's:

- iosApp/WatchShared/WatchAssets.xcassets/TaqwaArch.imageset/TaqwaArch.svg: the Taqwa arch, from
  androidApp/src/main/res/drawable/ic_launcher_monochrome.xml (the themed icon's single-colour
  silhouette): its paths and stroke, on its viewBox 0 0 1024 1024. The vector's group transform
  (a scale about the centre) is applied to the coordinates and the stroke width, so the SVG holds
  plain paths with no transform for Xcode's renderer to interpret. It is a template image that
  keeps its vector data: the watch tints it (amber, or the face's colour) and scales it sharp;
- the asset catalogs' Contents.json files;
- iosApp/TaqwaWatch/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png: the iPhone's 1024 px
  icon, byte for byte, for platform watchos.

    python3 tools/watch-art.py           # write them
    python3 tools/watch-art.py --check   # exit 1, naming each file, if any is not what it writes

The source vector may hold only what this tool understands (paths, a uniform-scale group); it
stops on anything else rather than draw a different arch.
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = "androidApp/src/main/res/drawable/ic_launcher_monochrome.xml"
PHONE_ICON = "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"
SHARED_CATALOG = "iosApp/WatchShared/WatchAssets.xcassets"
ARCH_SET = SHARED_CATALOG + "/TaqwaArch.imageset"
WATCH_CATALOG = "iosApp/TaqwaWatch/Assets.xcassets"
ICON_SET = WATCH_CATALOG + "/AppIcon.appiconset"
A = "{http://schemas.android.com/apk/res/android}"

# The number of parameters each path command takes.
ARITY = {"m": 2, "l": 2, "h": 1, "v": 1, "c": 6, "s": 4, "q": 4, "t": 2, "a": 7, "z": 0}
TOKEN = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


def number(value):
    """A coordinate as the SVG writes it: three decimals at most, no trailing zeros."""
    text = f"{round(value, 3):.3f}".rstrip("0").rstrip(".")
    return "0" if text in ("-0", "") else text


def scaled_path(data, scale, pivot_x, pivot_y):
    """The path data with a uniform scale about (pivot_x, pivot_y) applied to every coordinate."""
    tokens = TOKEN.findall(data)
    if "".join(tokens).replace(" ", "") != re.sub(r"[\s,]", "", data):
        sys.exit(f"{SOURCE}: path data this tool cannot read: {data!r}")
    out, i, command = [], 0, None
    while i < len(tokens):
        if tokens[i].isalpha():
            command = tokens[i]
            i += 1
            out.append(command)
            if command in "Zz":
                continue
        elif command is None or command in "Zz":
            sys.exit(f"{SOURCE}: path data without a command: {data!r}")
        n = ARITY[command.lower()]
        args = [float(t) for t in tokens[i:i + n]]
        if len(args) != n:
            sys.exit(f"{SOURCE}: '{command}' needs {n} numbers in {data!r}")
        i += n
        relative = command.islower()

        def x(v):
            return v * scale if relative else pivot_x + scale * (v - pivot_x)

        def y(v):
            return v * scale if relative else pivot_y + scale * (v - pivot_y)

        kind = command.lower()
        if kind == "h":
            values = [x(args[0])]
        elif kind == "v":
            values = [y(args[0])]
        elif kind == "a":
            rx, ry, rotation, large, sweep, ex, ey = args
            values = [rx * scale, ry * scale, rotation, large, sweep, x(ex), y(ey)]
        else:
            values = [x(v) if k % 2 == 0 else y(v) for k, v in enumerate(args)]
        out.extend(number(v) for v in values)
    return " ".join(out)


def colour(value):
    if not re.fullmatch(r"#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})", value or ""):
        sys.exit(f"{SOURCE}: a colour this tool cannot read: {value!r}")
    if len(value) == 9:  # #AARRGGBB
        return None if value[1:3] == "00" else "#" + value[3:]
    return value


def arch_svg():
    root = ET.parse(os.path.join(ROOT, SOURCE)).getroot()
    width, height = root.get(A + "viewportWidth"), root.get(A + "viewportHeight")
    if (width, height) != ("1024", "1024"):
        sys.exit(f"{SOURCE}: the viewport is {width} x {height}, not 1024 x 1024")
    groups = list(root)
    if len(groups) != 1 or groups[0].tag != "group":
        sys.exit(f"{SOURCE}: expected one group around the paths")
    group = groups[0]
    allowed = {A + n for n in ("scaleX", "scaleY", "pivotX", "pivotY")}
    if set(group.attrib) - allowed:
        sys.exit(f"{SOURCE}: the group has attributes this tool does not apply: {sorted(set(group.attrib) - allowed)}")
    scale_x, scale_y = float(group.get(A + "scaleX", "1")), float(group.get(A + "scaleY", "1"))
    if scale_x != scale_y:
        sys.exit(f"{SOURCE}: the group's scale is not uniform ({scale_x}, {scale_y})")
    pivot_x, pivot_y = float(group.get(A + "pivotX", "0")), float(group.get(A + "pivotY", "0"))
    shapes = []
    for path in group:
        if path.tag != "path":
            sys.exit(f"{SOURCE}: a {path.tag} this tool does not draw")
        known = {A + n for n in ("pathData", "strokeColor", "strokeWidth", "strokeLineCap",
                                 "strokeLineJoin", "fillColor")}
        if set(path.attrib) - known:
            sys.exit(f"{SOURCE}: a path attribute this tool does not draw: {sorted(set(path.attrib) - known)}")
        attrs = [f'd="{scaled_path(path.get(A + "pathData"), scale_x, pivot_x, pivot_y)}"']
        fill = colour(path.get(A + "fillColor", "#00000000"))
        attrs.append(f'fill="{fill or "none"}"')
        if path.get(A + "strokeColor"):
            stroke = colour(path.get(A + "strokeColor"))
            if stroke:
                attrs.append(f'stroke="{stroke}"')
                attrs.append(f'stroke-width="{number(float(path.get(A + "strokeWidth", "1")) * scale_x)}"')
                attrs.append(f'stroke-linecap="{path.get(A + "strokeLineCap", "butt")}"')
                attrs.append(f'stroke-linejoin="{path.get(A + "strokeLineJoin", "miter")}"')
        shapes.append("  <path " + " ".join(attrs) + "/>")
    return "\n".join([
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">',
        f"  <!-- Written by tools/watch-art.py from {SOURCE}: edit that, then run the tool. -->",
        *shapes,
        "</svg>",
    ]) + "\n"


def contents(document):
    """A Contents.json as Xcode writes one."""
    return (json.dumps(document, indent=2, separators=(",", " : ")) + "\n").encode()


XCODE = {"author": "xcode", "version": 1}


def outputs():
    with open(os.path.join(ROOT, PHONE_ICON), "rb") as f:
        icon = f.read()
    return {
        SHARED_CATALOG + "/Contents.json": contents({"info": XCODE}),
        ARCH_SET + "/Contents.json": contents({
            "images": [{"filename": "TaqwaArch.svg", "idiom": "universal"}],
            "info": XCODE,
            "properties": {"preserves-vector-representation": True, "template-rendering-intent": "template"},
        }),
        ARCH_SET + "/TaqwaArch.svg": arch_svg().encode(),
        WATCH_CATALOG + "/Contents.json": contents({"info": XCODE}),
        ICON_SET + "/Contents.json": contents({
            "images": [{"filename": "app-icon-1024.png", "idiom": "universal", "platform": "watchos",
                        "size": "1024x1024"}],
            "info": XCODE,
        }),
        ICON_SET + "/app-icon-1024.png": icon,
    }


def main(argv):
    check = argv[1:] == ["--check"]
    if argv[1:] and not check:
        sys.exit("usage: watch-art.py [--check]")
    stale = []
    for rel, data in outputs().items():
        path = os.path.join(ROOT, rel)
        current = open(path, "rb").read() if os.path.exists(path) else None
        if current == data:
            continue
        if check:
            stale.append(rel)
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)
        print(f"wrote {rel}")
    if stale:
        for rel in stale:
            print(f"{rel}: not what tools/watch-art.py writes; run python3 tools/watch-art.py")
        return 1
    if check:
        print("watch-art: up to date")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
