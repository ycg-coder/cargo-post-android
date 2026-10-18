#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""弹窗有没有画在主题里面（D-168）。

为什么要有它：D-163 把 UpdateDialog 的六处字面量色换成 `t.ink / t.danger / …`，
判据 HC-b/HC-c 当场念绿（0 处字面量、五个令牌都在）。但**绿的是写法**——
`MainActivity` 里那句 `UpdateDialog(...)` 当时写在 `BusAppTheme { }` **外面**，
读到的 `t` 是 `compositionLocalOf { busTokens(BusTheme.BLUE, false) }` 那份**默认浅色令牌**，
于是"深色下读不出字"这条根本没被治到，而"深色站牌 / 字号放大"对整个弹窗都不生效。
真机 v183 的提示弹窗截图是凭证：背后是夜间导航，弹窗是一块刺眼的白卡。

读数（只读不判）：
  SCOPE theme_block=<start>-<end> dialogs=<n> inside=<n> outside=<n>
"""
import io
import re
import sys

MAIN = "app/src/main/java/com/shanxiang/buspost/MainActivity.kt"
THEME = re.compile(r"BusAppTheme\s*\(")
DIALOG = re.compile(r"UpdateDialog\s*\(")


def block_end(lines, i):
    depth = 0
    started = False
    for j in range(i, len(lines)):
        depth += lines[j].count("{") - lines[j].count("}")
        if "{" in lines[j]:
            started = True
        if started and depth <= 0:
            return j
    return len(lines) - 1


def main():
    try:
        lines = io.open(MAIN, encoding="utf-8").read().split("\n")
    except IOError:
        print("SCOPE file_missing " + MAIN)
        return 2
    t_idx = [i for i, l in enumerate(lines) if THEME.search(l)]
    d_idx = [i for i, l in enumerate(lines) if DIALOG.search(l)]
    if not t_idx:
        print("SCOPE theme_block=none dialogs=%d inside=0 outside=%d" % (len(d_idx), len(d_idx)))
        return 0
    s = t_idx[0]
    e = block_end(lines, s)
    inside = sum(1 for i in d_idx if s < i <= e)
    print("SCOPE theme_block=%d-%d dialogs=%d inside=%d outside=%d" % (
        s + 1, e + 1, len(d_idx), inside, len(d_idx) - inside))
    return 0


if __name__ == "__main__":
    sys.exit(main())
