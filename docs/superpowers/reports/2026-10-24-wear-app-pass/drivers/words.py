"""Prints the words of a uiautomator dump in tree order: each node's text, content-desc and
state description where present, with its bounds, one node a line."""
import sys
import xml.etree.ElementTree as ET


def walk(node, depth, out):
    a = node.attrib
    parts = []
    for key in ("text", "content-desc", "state-description"):
        v = a.get(key, "")
        if v:
            parts.append("%s=%r" % (key, v))
    flags = [k for k in ("scrollable", "clickable", "focused") if a.get(k) == "true"]
    if parts or flags:
        out.append("%s%s %s %s" % ("  " * depth, " ".join(parts), a.get("bounds", ""), ",".join(flags)))
    for child in node:
        walk(child, depth + 1, out)


def main():
    try:
        root = ET.parse(sys.argv[1]).getroot()
    except Exception as e:  # a dump taken mid-transition
        print("unreadable dump: %s" % e)
        return
    out = []
    walk(root, 0, out)
    print("\n".join(out))


main()
