#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""通栏可点格的热区探针（D-164）。为什么要有它：判据簿原来只看**源码形状**
（`.height(N.dp)` 的视口数），而真机 dump 量出来的命中框和那个形状不是一回事 ——
2026-10-18 实测：首页公告轮播的视口写的是 28dp，可点框实际 132px=48dp（本来就达标，
D-UI-1 把它记成了欠账）；同一屏的天气胶囊没有任何 `.height()`，实际只有 91px=33dp。
所以这一本只认**量出来的像素**。

  --source            源码接线：胶囊的可点节点要有 `heightIn(min = touch.min)`，且必须在
                      `.padding(垂直留白)` **之前**、在 `clickable` **之后**。
                      为什么不是 defaultMinSize：真机 v173 上同一处写的就是 defaultMinSize，
                      量出来 105px=38dp（没到 48dp）；s3 那一格就是钉"不许换回去"。
  --dump DIR          归档 dump 里逐个锚点报命中框高度（px）：
                      carousel = 条上文字前导两个空格（源码写死 `"  ${title}"`，全 dump 唯一，
                                 不依赖后台数据）；pill = 文字恰为「4日预报」（代码字面量）

只读数、不判定；判定在 tools/check_ui_button_probes.sh 的 UB-k。
"""
import io
import os
import re
import sys

PILL = "app/src/main/java/com/shanxiang/buspost/ui/common/WeatherCard.kt"
NODE = re.compile(r"<node\b([^>]*?)/?>")
BND = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
# 锚点：名字 -> 判定函数（对 node 的 text）
ANCHORS = [
    ("carousel", lambda s: s.startswith("  ") and s.strip() != ""),
    ("pill", lambda s: s.strip() == "4日预报"),
]


def read(path):
    return io.open(path, encoding="utf-8", errors="replace").read()


def source():
    if not os.path.exists(PILL):
        print("SRC file_missing:" + PILL)
        return
    body = read(PILL)
    lines = body.split("\n")
    s1 = body.count("val touch = LocalBusTouch.current")
    s2 = body.count(".heightIn(min = touch.min)")
    s3 = body.count(".defaultMinSize(minHeight = touch.min)")
    # 顺序：minHeight 那一行必须早于垂直留白的 .padding(horizontal = 11.dp, vertical = 7.dp)
    i_min = next((i for i, l in enumerate(lines) if ".heightIn(min = touch.min)" in l), -1)
    i_pad = next((i for i, l in enumerate(lines) if ".padding(horizontal = 11.dp, vertical = 7.dp)" in l), -1)
    order = 1 if (i_min >= 0 and i_pad >= 0 and i_min < i_pad) else 0
    # 可点节点本身：clickable 必须在 minHeight 之前（否则撑大的那块点不着）
    i_click = next((i for i, l in enumerate(lines) if "Modifier.clickable(onClick = onToggle)" in l), -1)
    click_first = 1 if (i_click >= 0 and i_min >= 0 and i_click < i_min) else 0
    print("SRC s1=%d s2=%d s3=%d order=%d click_first=%d min_line=%d pad_line=%d click_line=%d" % (
        s1, s2, s3, order, click_first, i_min + 1, i_pad + 1, i_click + 1))


def nodes(path):
    out = []
    for m in NODE.finditer(read(path)):
        a = m.group(1)

        def attr(name):
            mm = re.search(name + r'="([^"]*)"', a)
            return mm.group(1) if mm else ""

        mm = BND.match(attr("bounds"))
        if not mm:
            continue
        l, t, r, b = [int(x) for x in mm.groups()]
        out.append({"l": l, "t": t, "r": r, "b": b, "w": r - l, "h": b - t,
                    "click": attr("clickable") == "true", "text": attr("text")})
    return out


def host_height(ns, pred):
    anchors = [n for n in ns if pred(n["text"])]
    if not anchors:
        return None
    a = anchors[0]
    hosts = [n for n in ns if n["click"]
             and n["l"] <= a["l"] and n["t"] <= a["t"] and n["r"] >= a["r"] and n["b"] >= a["b"]]
    if not hosts:
        return ("no_host", len(anchors), 0, 0)
    host = min(hosts, key=lambda n: n["w"] * n["h"])
    return (host["h"], len(anchors), len(hosts), host["w"])


def dump_dir(d):
    files = sorted(f for f in os.listdir(d) if f.endswith(".xml"))
    if not files:
        print("DUMP no_xml_in_dir")
        return
    for f in files:
        ns = nodes(os.path.join(d, f))
        for name, pred in ANCHORS:
            got = host_height(ns, pred)
            if got is None:
                print("DUMP %s %s absent" % (f, name))
            elif got[0] == "no_host":
                print("DUMP %s %s no_host anchors=%d" % (f, name, got[1]))
            else:
                h, anchors, hosts, w = got
                print("DUMP %s %s h=%d w=%d anchors=%d hosts=%d" % (f, name, h, w, anchors, hosts))


if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] not in ("--source", "--dump"):
        print("用法: hot_zone_probe.py --source | --dump DIR")
        sys.exit(2)
    if sys.argv[1] == "--source":
        source()
    else:
        if len(sys.argv) < 3 or not os.path.isdir(sys.argv[2]):
            print("DUMP dir_missing")
            sys.exit(2)
        dump_dir(sys.argv[2])
