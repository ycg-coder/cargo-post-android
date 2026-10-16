#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""uiautomator dump 的解析器 + 遍历器队列写入。

单独成文件（而不是嵌在 bash heredoc 里）的原因：含中文与反斜杠的 shell 字面量会被改字节，
上一版内嵌写法当场把 Python 源码弄坏一次；解析器还得能脱离设备离线自测。

子命令：
  parse   <dump.xml> <nodes.tsv>      → 打 "<结构哈希> <节点数> <祖先链可点数>"
  enqueue <nodes.tsv> <queue.tsv> <深度> <路径> <DENY正则>
                                       → 把本屏可点且安全的项写进遍历队列

口径（本仓踩过的坑）：可点**按祖先链**判定 —— Modifier.clickable 住在父语义节点、
label 常常是子 TextView，只看节点自己的 clickable=true 必然读成 0（上轮就这样造过一条假缺陷）。
结构哈希只吃 class+bounds，不吃文字，否则带秒数/时间的同一屏每次都被当成新屏。
"""
import hashlib
import io
import re
import sys

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except (AttributeError, ValueError):
    pass

ATTR = re.compile(r'([\w:-]+)="([^"]*)"')
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def iter_nodes(path):
    """按文档顺序产出 (depth, attrs, closing)。

    uiautomator 的 dump 是单行 XML，所以用正则扫 <node ...> / </node> 自己维护深度，
    不依赖 ElementTree 的行结构（ET 也能用，但这里要和 bash 逐行读的形态一致）。
    """
    text = io.open(path, encoding="utf-8", errors="replace").read()
    depth = 0
    for m in re.finditer(r"<(/?)node\b([^>]*?)/?>", text):
        closing, attrs = m.group(1), m.group(2)
        if closing:
            depth = max(0, depth - 1)
            continue
        d = dict(ATTR.findall(attrs or ""))
        yield depth, d
        if not (attrs or "").rstrip().endswith("/"):
            depth += 1


def parse(dump, out_tsv):
    rows = []
    anc_click = []
    for depth, a in iter_nodes(dump):
        while anc_click and len(anc_click) > depth:
            anc_click.pop()
        click = a.get("clickable") == "true"
        anc_click.append(click or bool(anc_click and anc_click[-1]))
        eff = click or bool(anc_click and anc_click[-1])
        b = BOUNDS.search(a.get("bounds", ""))
        x1, y1, x2, y2 = (int(v) for v in b.groups()) if b else (0, 0, 0, 0)
        label = a.get("text") or a.get("content-desc") or a.get("resource-id") or ""
        rows.append((depth, click, eff, label, x1, y1, x2, y2, a.get("class", "")))
    with io.open(out_tsv, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("depth\tnode_clickable\teff_clickable\tlabel\tx1\ty1\tx2\ty2\tclass\n")
        for r in rows:
            fh.write("%d\t%s\t%s\t%s\t%d\t%d\t%d\t%d\t%s\n"
                     % (r[0], r[1], r[2], r[3].replace("\t", " "), r[4], r[5], r[6], r[7], r[8]))
    struct = "|".join("%s:%d,%d,%d,%d" % (r[8], r[4], r[5], r[6], r[7]) for r in rows)
    eff_n = sum(1 for r in rows if r[2])
    print(hashlib.sha1(struct.encode("utf-8")).hexdigest()[:12], len(rows), eff_n)


def enqueue(nodes_tsv, queue_tsv, depth, path, deny):
    D = re.compile(deny)
    with io.open(queue_tsv, "a", encoding="utf-8", newline="\n") as fh:
        for line in io.open(nodes_tsv, encoding="utf-8"):
            f = line.rstrip("\n").split("\t")
            if len(f) < 9 or f[0] == "depth" or f[2] != "True":
                continue
            lbl = f[3]
            x1, y1, x2, y2 = (int(f[i]) for i in (4, 5, 6, 7))
            if not lbl or len(lbl) > 40 or D.search(lbl):
                continue                      # 无名不点、DENY 命中不点（宁可少走一屏）
            if x1 < 0 or y1 < 0 or (x2 - x1) < 20 or (y2 - y1) < 20:
                continue                      # 不像控件的碎片不点
            fh.write("%d\t%s>%s\t%d %d\n" % (int(depth) + 1, path, lbl.replace("\t", " ")[:22],
                                              (x1 + x2) // 2, (y1 + y2) // 2))


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "parse":
        parse(sys.argv[2], sys.argv[3])
    elif cmd == "enqueue":
        enqueue(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6])
    else:
        sys.stderr.write("用法: ui_nodes_parse.py parse <dump> <out.tsv> | enqueue <tsv> <queue> <depth> <path> <deny>\n")
        sys.exit(2)
