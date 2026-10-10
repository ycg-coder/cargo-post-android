import io
import os
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
xml = os.path.join(ROOT, "docs", "device-evidence", "20261009-020840", "r68-15-wb-scroll1.xml")
tree = ET.parse(xml)
target = "[17,1993][83,2104]"
nodes = list(tree.iter("node"))
for i, n in enumerate(nodes):
    if n.get("bounds") == target:
        print("HIT index=%d total_nodes=%d" % (i, len(nodes)))
        for j in range(max(0, i - 2), min(len(nodes), i + 5)):
            m = nodes[j]
            print("%3d %s text=%r desc=%r click=%s longclick=%s naf=%s bounds=%s%s" % (
                j, m.get("class"), m.get("text"), m.get("content-desc"),
                m.get("clickable"), m.get("long-clickable"), m.get("NAF"), m.get("bounds"),
                "  <<== TARGET" if j == i else ""))
        print("--- parent chain ---")
        # ElementTree has no parent links; rebuild map
        parent = {c: p for p in nodes for c in p}
        cur = n
        depth = 0
        while cur in parent and depth < 6:
            cur = parent[cur]
            depth += 1
            print("  up%d %s text=%r desc=%r click=%s bounds=%s" % (
                depth, cur.get("class"), cur.get("text"), cur.get("content-desc"),
                cur.get("clickable"), cur.get("bounds")))
        break
else:
    print("NO HIT; listing all clickable nodes")
    for i, n in enumerate(nodes):
        if n.get("clickable") == "true":
            print("%3d %s text=%r desc=%r bounds=%s" % (
                i, n.get("class"), n.get("text"), n.get("content-desc"), n.get("bounds")))
