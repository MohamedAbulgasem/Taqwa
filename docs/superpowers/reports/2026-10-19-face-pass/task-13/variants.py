"""Ruling 25's audit: every Variant in watchface.xml, by its parent element, mode and target."""
import sys
import xml.etree.ElementTree as ET
from collections import Counter
root = ET.parse(sys.argv[1]).getroot()
parents = {c: p for p in root.iter() for c in p}
count, bad = Counter(), []
for v in root.iter("Variant"):
    p = parents[v]
    count[(p.tag, v.get("mode"), v.get("target"))] += 1
    if p.tag in ("Font", "Stroke") or v.get("mode") != "AMBIENT" or v.get("target") != "alpha":
        bad.append((p.tag, v.attrib))
print("Variants: %d" % sum(count.values()))
for (tag, mode, target), n in sorted(count.items()):
    print("  %-12s %s %s x%d" % (tag, mode, target, n))
hands = [(p.tag, p.get("resource"), p.get("alpha"), v.get("value")) for v in root.iter("Variant")
         for p in [parents[v]] if p.tag.endswith("Hand")]
for h in hands:
    print("  hand %s %s alpha=%s always-on=%s" % h)
both = [p.tag for p in root.iter() if any(c.tag == "Variant" for c in p) and any(c.tag == "Transform" and c.get("target") == "alpha" for c in p)]
print("an alpha Variant and an alpha Transform on one element: %d" % len(both))
print("outside the rule (in a Font or a Stroke, or not AMBIENT alpha): %d %s" % (len(bad), bad))
sys.exit(1 if bad or both else 0)
