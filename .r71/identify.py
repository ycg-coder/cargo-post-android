import os
import re
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
EV = os.path.join(ROOT, "docs", "device-evidence", "20261009-020840")
WANT = {
    "r68-08-user-home.xml": [(210, 46), (385, 46), (168, 119)],
    "r68-10-mine.xml": [(992, 42)],
    "r68-19-driver-routes.xml": [(446, 27)],
    "r68-18-wb-timeline.xml": [(882, 109)],
    "r68-04-home.xml": [(970, 121)],
}


def dims(n):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return x2 - x1, y2 - y1, x1, y1, x2, y2


for name, targets in WANT.items():
    tree = ET.parse(os.path.join(EV, name))
    nodes = list(tree.iter("node"))
    parent = {c: p for p in nodes for c in p}
    seen = set()
    for i, n in enumerate(nodes):
        if n.get("clickable") != "true":
            continue
        d = dims(n)
        if not d:
            continue
        key = (d[0], d[1])
        if key not in targets or key in seen:
            continue
        seen.add(key)
        print("== %s  node#%d  bounds=%s  NAF=%s" % (name, i, n.get("bounds"), n.get("NAF")))
        kids = list(n.iter("node"))
        print("   descendants=%d  own_text=%r own_desc=%r" % (
            len(kids) - 1, n.get("text"), n.get("content-desc")))
        for c in kids[1:6]:
            print("     kid %s text=%r desc=%r bounds=%s" % (
                c.get("class"), c.get("text"), c.get("content-desc"), c.get("bounds")))
        p = parent.get(n)
        if p is not None:
            print("   parent %s bounds=%s click=%s" % (p.get("class"), p.get("bounds"), p.get("clickable")))
            # siblings of the parent or of this node: nearest text by vertical distance
            sibs = [c for c in p if c is not n]
            px = (d[2] + d[4]) / 2.0
            py = (d[3] + d[5]) / 2.0
            near = []
            for c in nodes:
                t = c.get("text") or c.get("content-desc") or ""
                if not t or c is n:
                    continue
                dd = dims(c)
                if not dd:
                    continue
                cx = (dd[2] + dd[4]) / 2.0
                cy = (dd[3] + dd[5]) / 2.0
                near.append(((cx - px) ** 2 + (cy - py) ** 2, t[:30], c.get("bounds")))
            near.sort()
            for dist, t, b in near[:4]:
                print("   near-text %r at %s (d=%.0f)" % (t, b, dist ** 0.5))
        print()
