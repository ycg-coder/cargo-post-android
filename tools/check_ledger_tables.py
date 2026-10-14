#!/usr/bin/env python3
# 台账表格结构自检。用法：python tools/check_ledger_tables.py [文件...]
#   不给参数时检查仓库根目录那五本手写台账，并把结果写进 tools/ledger_table_report.txt。
# 为什么要有这个脚本：表格行只有两种坏法，且**拆行比并行更难发现** ——
#   ① 两行并成一行（吃掉行尾换行）；② 一行拆成多行（续行看起来就是普通正文，渲染时整张表在那一行之后静默断掉）。
#   本脚本按"同一张表内每行的**未转义**竖线数必须等于表头行的数"判定，两种坏法都能抓到。
# 注意：竖线数**不是全局常数**（三列表 4、五列表 6），且 ``\|``（代码段里的转义竖线）不计入分隔符。
import io
import os
import sys

DEFAULT_FILES = [
    "AI_ITERATION_STATE.md",
    "ANDROID_PARITY_AUDIT_V2.md",
    "CONTEXT_LOCK.md",
    "DECISION_LOG.md",
    "THIRD_PARTY_NOTICES.md",
]
REPORT = os.path.join("tools", "ledger_table_report.txt")
ESCAPED = chr(92) + "|"
NL = chr(10)


def separators(line):
    return line.replace(ESCAPED, "").count("|")


def scan(path):
    with io.open(path, encoding="utf-8") as handle:
        lines = handle.read().split(NL)
    counts = [separators(l) if l.startswith("|") else 0 for l in lines]
    flags = []
    index = 0
    while index < len(lines):
        if not lines[index].startswith("|"):
            index += 1
            continue
        end = index
        while end < len(lines) and lines[end].startswith("|"):
            end += 1
        header = counts[index]
        for row in range(index, end):
            if counts[row] != header:
                flags.append((row + 1, "cells %d vs header %d" % (counts[row], header), lines[row][:60]))
        index = end
    for row in range(1, len(lines) - 1):
        if lines[row].startswith("|") or not lines[row].strip():
            continue
        if not (lines[row - 1].startswith("|") and lines[row + 1].startswith("|")):
            continue
        if counts[row - 1] == counts[row + 1] and counts[row - 1] >= 3:
            flags.append((row + 1, "SPLIT ROW (续行不是表格行)", lines[row][:60]))
    return flags


def main(argv):
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(root)
    files = argv[1:] or DEFAULT_FILES
    missing = [f for f in files if not os.path.exists(f)]
    if missing:
        sys.stderr.write("台账文件不存在，拒绝在缺文件的情况下报绿：%s\n" % ", ".join(missing))
        return 2
    total = 0
    out = []
    for f in files:
        flags = scan(f)
        total += len(flags)
        out.append("=== %s  行数=%d  flags=%d" % (f, sum(1 for _ in io.open(f, encoding="utf-8")), len(flags)))
        for line, why, text in flags:
            out.append("    %d  %s  ::  %s" % (line, why, text))
    out.append("TOTAL FLAGS %d  （0 = 五本台账的表格行结构完好）" % total)
    print(NL.join(out))
    if not argv[1:]:  # 只在"查五本台账"时落报告；负控注入的是临时副本，不该覆盖在册报告
        with io.open(REPORT, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(NL.join(out) + NL)
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
