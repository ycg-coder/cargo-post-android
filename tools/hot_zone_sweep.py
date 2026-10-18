#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""归档 dump 的通栏热区扫描（D-165）。

为什么单独要一本：D-164 之前所有热区判据都长在**源码形状**上（`.height(N.dp)` 计数），
而真机量出来"形状矮的那格达标、形状看不出的那格 33dp"。这一本不看源码，只看量出来的框。

口径（两条都是本轮真机踩出来的）：
  · 通栏 = 宽 ≥ --min-width（默认 400px）：这类格子写的是"点这一整条"，矮了就是点不着；
    窄格子（图标按钮）由 tools/layout_audit.py 的"两个方向都矮才算"那条管，两本合起来才盖全。
  · 贴底存疑 = 可点格的底边**贴着滚动容器底**（默认 8px 内）：那是被视口裁掉的半截，
    不是热区病（首页「步行导航」在屏底念 116px、往上滚一格就 132px —— 拿它当缺陷会编出假案）。

用法： hot_zone_sweep.py DIR [--floor 132] [--min-width 400] [--clip-tol 8]
只读数、不判定；判定在 tools/check_ui_button_probes.sh 的 UB-l。
"""
import io
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hot_zone_probe import nodes  # noqa: E402

FULL = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def scan(path, floor, min_w, clip_tol):
    ns = nodes(path)
    inner = [n["b"] for n in ns if n["b"] - n["t"] < 2000]
    maxb = max(inner) if inner else 0
    short, clipped = [], []
    for n in ns:
        if not n["click"] or n["h"] >= floor or n["w"] < min_w:
            continue
        line = "%dx%d @ (%d,%d)-(%d,%d) %s" % (
            n["w"], n["h"], n["l"], n["t"], n["r"], n["b"], (n["text"] or "-")[:24])
        (clipped if maxb and maxb - n["b"] <= clip_tol else short).append(line)
    return len(ns), short, clipped


def main(argv):
    if not argv:
        print("用法: hot_zone_sweep.py DIR [--floor 132] [--min-width 400] [--clip-tol 8]")
        return 2
    d, floor, min_w, clip_tol = argv[0], 132, 400, 8
    rest = argv[1:]
    for i, a in enumerate(rest):
        if a == "--floor":
            floor = int(rest[i + 1])
        elif a == "--min-width":
            min_w = int(rest[i + 1])
        elif a == "--clip-tol":
            clip_tol = int(rest[i + 1])
    if not os.path.isdir(d):
        print("SWEEP dir_missing " + d)
        return 2
    files = sorted(f for f in os.listdir(d) if f.endswith(".xml"))
    if not files:
        print("SWEEP no_xml_in_dir " + d)
        return 2
    t_short = t_clip = 0
    for f in files:
        n, short, clipped = scan(os.path.join(d, f), floor, min_w, clip_tol)
        t_short += len(short)
        t_clip += len(clipped)
        print("SWEEP %s nodes=%d short=%d clipped=%d" % (f, n, len(short), len(clipped)))
        for x in short:
            print("DEFECT %s %s" % (f, x))
        for x in clipped:
            print("CLIPPED %s %s" % (f, x))
    print("TOTAL files=%d floor=%d min_width=%d short=%d clipped=%d" % (
        len(files), floor, min_w, t_short, t_clip))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
