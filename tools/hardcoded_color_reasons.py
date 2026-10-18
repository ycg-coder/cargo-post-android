#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""HC-d 的探针：每一处 `Color(0x…)` 字面量，上面三行内必须有一条 `// 硬编码色：<理由>`。

用法：python tools/hardcoded_color_reasons.py [<源码根目录>]
输出（单行，供 bash 判据读）：`<字面量数> <带理由数> <没理由的清单，逗号分隔，空则 ->`

为什么单独一个文件而不是塞进 bash 簿：这一格的判定要按"字面量往上数三行"读上下文，
用 bash + 嵌套 heredoc 写会连穿三层转义（本仓踩过：`\\n` 被中间那层吃掉，判据自己先崩）。
"""
import io
import os
import re
import sys

PAT = re.compile(r"Color\(0x[0-9A-Fa-f]{6,8}\)")
MARK = "硬编码色："
# 注释行里举例写的 Color(0x…) 不算一处字面量（它不是画在屏幕上的颜色）
COMMENT = re.compile(r"^\s*(//|\*|/\*)")


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java/com/shanxiang/buspost"
    lit = 0
    ok = 0
    naked = []
    for dp, _, fs in os.walk(root):
        for fn in sorted(fs):
            if not fn.endswith(".kt"):
                continue
            p = os.path.join(dp, fn)
            if p.endswith(os.path.join("core", "ui", "BusTokens.kt")):
                continue
            src = io.open(p, encoding="utf-8", errors="replace").read().split("\n")
            for i, line in enumerate(src):
                if not PAT.search(line) or COMMENT.match(line):
                    continue
                lit += 1
                window = "\n".join(src[max(0, i - 3):i + 1])
                if MARK in window:
                    ok += 1
                else:
                    naked.append("%s:%d" % (os.path.relpath(p, root), i + 1))
    print("%d %d %s" % (lit, ok, ",".join(naked) if naked else "-"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
