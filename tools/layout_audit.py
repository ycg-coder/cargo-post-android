#!/usr/bin/env python3
"""uiautomator dump 的布局几何自检：越过屏幕、兄弟文字重叠、可点热区不足 48dp。

用法：python tools/layout_audit.py <dump.xml> [<dump2.xml> ...]
屏幕尺寸默认取 dump 里最大 bounds，可用 --screen 1080x2400 覆盖。
48dp 在 420dpi（1 dp = 2.625 px）下是 126 px，可用 --min-touch 覆盖。
"""
import re
import sys
import xml.etree.ElementTree as ET

NODE = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def parse(path):
    tree = ET.parse(path)
    out = []

    def walk(node, depth, chain):
        a = node.attrib
        m = NODE.search(a.get("bounds", ""))
        if m:
            l, t, r, b = (int(x) for x in m.groups())
            rec = {
                "depth": depth,
                "chain": chain,
                "cls": a.get("class", ""),
                "text": (a.get("text") or "").strip(),
                "desc": (a.get("content-desc") or "").strip(),
                "clickable": a.get("clickable") == "true",
                "bounds": (l, t, r, b),
                "visible": a.get("visible-to-user", "true") == "true",
            }
            out.append(rec)
            chain = chain + [id(rec)]
            depth += 1
        for c in list(node):
            walk(c, depth, chain)

    walk(tree.getroot(), 0, [])
    return out


def area(x):
    l, t, r, b = x
    return max(0, r - l) * max(0, b - t)


def inter(x, y):
    l = max(x[0], y[0]); t = max(x[1], y[1])
    r = min(x[2], y[2]); b = min(x[3], y[3])
    return (l, t, r, b) if r > l and b > t else None


def label(n):
    who = n["text"] or n["desc"] or n["cls"].split(".")[-1]
    return f"{who!r} [{n['bounds'][0]},{n['bounds'][1]}][{n['bounds'][2]},{n['bounds'][3]}]"


def audit(path, screen, min_touch):
    nodes = parse(path)
    if not nodes:
        print(f"{path}: 空 dump")
        return 0
    W, H = screen if screen else (
        max(n["bounds"][2] for n in nodes),
        max(n["bounds"][3] for n in nodes),
    )
    issues = []
    leaves = [n for n in nodes if n["visible"]]

    for n in leaves:
        l, t, r, b = n["bounds"]
        if l < 0 or t < 0 or r > W or b > H:
            if n["text"] or n["desc"] or n["clickable"]:
                issues.append(f"越过屏幕({W}x{H}): {label(n)}")

    for n in leaves:
        l, t, r, b = n["bounds"]
        if not n["clickable"]:
            continue
        if (b - t) < min_touch and (r - l) < min_touch:
            issues.append(
                f"热区不足 {min_touch}px: {label(n)} = {r - l}x{b - t}")

    txt = [n for n in leaves if n["text"]]
    for i in range(len(txt)):
        for j in range(i + 1, len(txt)):
            a, b = txt[i], txt[j]
            if id(a) in b["chain"] or id(b) in a["chain"]:
                continue
            ov = inter(a["bounds"], b["bounds"])
            if not ov:
                continue
            shared = area(ov)
            smaller = min(area(a["bounds"]), area(b["bounds"]))
            if smaller and shared / smaller > 0.25:
                issues.append(
                    f"兄弟文字重叠 {shared}px² ({shared * 100 // smaller}%): "
                    f"{label(a)} × {label(b)}")

    zero = [n for n in txt if (n["bounds"][2] - n["bounds"][0]) <= 0
            or (n["bounds"][3] - n["bounds"][1]) <= 0]
    for n in zero:
        issues.append(f"文字零尺寸: {label(n)}")

    print(f"== {path}: {len(nodes)} 节点 / {len(txt)} 段文字 / 屏幕 {W}x{H} ==")
    if issues:
        for line in issues:
            print("  [LAYOUT] " + line)
    else:
        print("  无越界 / 无兄弟重叠 / 热区达标")
    return len(issues)


def listing(path, out_file):
    nodes = parse(path)
    with open(out_file, "w", encoding="utf-8", newline="\n") as fh:
        for n in nodes:
            l, t, r, b = n["bounds"]
            who = n["text"] or n["desc"] or "-"
            fh.write(
                f"{'  ' * min(n['depth'], 8)}{who[:60]} | {n['cls'].split('.')[-1]}"
                f"{' C' if n['clickable'] else ''} | {r - l}x{b - t} @ {l},{t}\n"
            )
    print(f"LISTING -> {out_file} ({len(nodes)} 节点)")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    screen = None
    min_touch = 126
    list_to = None
    for a in sys.argv[1:]:
        if a.startswith("--screen="):
            w, h = a.split("=", 1)[1].split("x")
            screen = (int(w), int(h))
        if a.startswith("--min-touch="):
            min_touch = int(a.split("=", 1)[1])
        if a.startswith("--list="):
            list_to = a.split("=", 1)[1]
    if not args:
        print(__doc__)
        return 2
    if list_to:
        listing(args[0], list_to)
    total = sum(audit(p, screen, min_touch) for p in args)
    print(f"TOTAL_ISSUES={total}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
