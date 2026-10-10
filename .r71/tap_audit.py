import io
import os
import re
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
EV = os.path.join(ROOT, "docs", "device-evidence", "20261009-020840")
DENSITY = 2.625  # 420dpi, measured in D-067 (1080px / 2.625 = 411dp wide)
FLOOR_PX = 126   # 48dp

rows = []
for name in sorted(os.listdir(EV)):
    if not name.endswith(".xml"):
        continue
    tree = ET.parse(os.path.join(EV, name))
    nodes = list(tree.iter("node"))
    parent = {c: p for p in nodes for c in p}
    for n in nodes:
        if n.get("clickable") != "true":
            continue
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        w, h = x2 - x1, y2 - y1
        # label = own text/desc, else first descendant text/desc
        label = n.get("text") or n.get("content-desc") or ""
        if not label:
            for c in n.iter("node"):
                if c is n:
                    continue
                t = c.get("text") or c.get("content-desc") or ""
                if t:
                    label = "~" + t
                    break
        under = w < FLOOR_PX or h < FLOOR_PX
        if under:
            rows.append((name, w, h, round(w / DENSITY, 1), round(h / DENSITY, 1),
                         n.get("NAF") or "-", label[:26]))

print("clickable nodes below 48dp floor: %d" % len(rows))
tot = 0
for name in sorted(os.listdir(EV)):
    if name.endswith(".xml"):
        tot += sum(1 for _ in ET.parse(os.path.join(EV, name)).iter("node"))
print("frames=%d total_nodes=%d" % (len([f for f in os.listdir(EV) if f.endswith('.xml')]), tot))
for r in sorted(rows, key=lambda t: (t[1] * t[2])):
    print("  %-30s %4dx%-4d = %5.1fx%-5.1f dp NAF=%s label=%r" % r)
