import os
import re
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
EV = os.path.join(ROOT, "docs", "device-evidence", "20261009-020840")
DENSITY = 2.625
FLOOR_PX = 126


def box(n):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
    return tuple(map(int, m.groups())) if m else None


def scroll_ancestors(n, parent):
    out = []
    cur = n
    while cur in parent:
        cur = parent[cur]
        if (cur.get("class") or "").endswith(("ScrollView", "HorizontalScrollView")) or \
           "Recycler" in (cur.get("class") or "") or cur.get("scrollable") == "true":
            out.append(cur)
    return out


full, clipped = [], []
for name in sorted(os.listdir(EV)):
    if not name.endswith(".xml"):
        continue
    nodes = list(ET.parse(os.path.join(EV, name)).iter("node"))
    parent = {c: p for p in nodes for c in p}
    for n in nodes:
        if n.get("clickable") != "true":
            continue
        b = box(n)
        if not b:
            continue
        w, h = b[2] - b[0], b[3] - b[1]
        if w >= FLOOR_PX and h >= FLOOR_PX:
            continue
        label = n.get("text") or n.get("content-desc") or ""
        if not label:
            for c in list(n.iter("node"))[1:]:
                label = c.get("text") or c.get("content-desc") or ""
                if label:
                    label = "~" + label
                    break
        cut = None
        for sv in scroll_ancestors(n, parent):
            s = box(sv)
            if not s:
                continue
            if b[3] > s[3] or b[1] < s[1] or b[2] > s[2] or b[0] < s[0]:
                cut = (sv.get("class"), s, b[3] - s[3])
                break
        rec = (name, b, round(w / DENSITY, 1), round(h / DENSITY, 1), label[:22], cut)
        (clipped if cut else full).append(rec)

print("=== 未被裁切、确实低于 48dp 的可点节点：%d ===" % len(full))
for name, b, wdp, hdp, label, _ in full:
    print("  %-28s %-22s %5.1fx%-5.1f dp label=%r" % (name, str(b), wdp, hdp, label))
print("=== 与滚动视口相交（读数被裁切，不能当热区结论）：%d ===" % len(clipped))
for name, b, wdp, hdp, label, cut in clipped:
    print("  %-28s %-22s %5.1fx%-5.1f dp label=%-24r 超出视口底 %d px  (viewport=%s %s)" % (
        name, str(b), wdp, hdp, label, cut[2], cut[0], cut[1]))
