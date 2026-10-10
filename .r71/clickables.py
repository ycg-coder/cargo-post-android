import os
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
xml = os.path.join(ROOT, "docs", "device-evidence", "20261009-020840", "r68-15-wb-scroll1.xml")
nodes = list(ET.parse(xml).iter("node"))
parent = {c: p for p in nodes for c in p}
print("total=%d clickable=%d" % (len(nodes), sum(1 for n in nodes if n.get("clickable") == "true")))
for i, n in enumerate(nodes):
    if n.get("clickable") == "true":
        b = n.get("bounds")
        p = parent.get(n)
        print("%3d %-28s %s naf=%s text=%r desc=%r  parent=%s %s" % (
            i, n.get("class"), b, n.get("NAF"), n.get("text"), n.get("content-desc"),
            p.get("class") if p is not None else "-", p.get("bounds") if p is not None else "-"))
print("--- kids of each clickable (depth<=2) ---")
for i, n in enumerate(nodes):
    if n.get("clickable") == "true" and n.get("bounds") in ("[17,1993][83,2104]", "[83,1993][997,2104]"):
        def walk(x, d):
            for c in x:
                print("   " * d + "%s text=%r desc=%r bounds=%s" % (
                    c.get("class"), c.get("text"), c.get("content-desc"), c.get("bounds")))
                walk(c, d + 1)
        print("node %d %s:" % (i, n.get("bounds")))
        walk(n, 1)
