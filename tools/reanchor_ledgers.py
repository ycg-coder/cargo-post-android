#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""台账锚点重量器（配 tools/check_doc_anchor_probes.sh 用）。

为什么要有这一本：`check_doc_anchors.py` 只**报**漂移（DA-d 新增 / DA-e ID-MISS 回涨），
修得靠人逐条搬行号。改一次源码就可能几十条要搬，手搬会漏、会把裸编号（`Repo.kt:1310/727`
里那三个不带文件名的续号）也顺手"平移"错 —— 2026-10-15 实测过：naive 数字正则会把
`**65**`、`字符串族 11` 这类**计数**当行号改掉。所以本工具：

  1. 只搬**受检锚点**（`check_doc_anchors.py` 认的那些 `file.kt:NN[-MM]`），裸编号一律不碰；
  2. 搬法 = 拿 HEAD 那份源文件切出被指片段，在工作树里找**同一切片** ⇒ 纯位移才改；
  3. 找不到切片（那块真被改了）就**不动**，列进"要人工判"清单，绝不猜；
  4. 改完必须让 tokens 落在窗口里（与判据同一口径），不满足的同样退回人工；
  5. 全或无：任何一条断言（原文恰好命中一次 / 行数与行尾守恒）不成立就整本不落盘。

用法：
  python tools/reanchor_ledgers.py --check     # 只报要搬什么，不落盘
  python tools/reanchor_ledgers.py --apply     # 落盘（每本台账留 .bak-reanchor）
"""
import argparse
import collections
import json
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
DOCS = ["ANDROID_PARITY_AUDIT_V2.md", "AI_ITERATION_STATE.md", "DECISION_LOG.md", "CONTEXT_LOCK.md",
        "THIRD_PARTY_NOTICES.md", "docs/android/CONTRACT_EVIDENCE.md", "docs/android/ANDROID_V2.1_CHECKLIST.md"]
PIN = os.path.join("tools", "fixtures", "doc_anchor_pins.txt")
EXT = r"(?:kt|java|xml|gradle|kts|sh|py)"
ANCHOR = re.compile(r"([A-Za-z0-9_.\-]+\.%s):(\d+)(?:-(\d+))?" % EXT)
TOL = 3  # 与 check_doc_anchors.py 的 --tolerance 默认值一致


def run(cmd, cwd=None):
    return subprocess.run(cmd, cwd=cwd or REPO, capture_output=True, text=True, encoding="utf-8", errors="replace")


def head_text(rel):
    r = run(["git", "show", "HEAD:%s" % rel])
    return r.stdout.replace("\r\n", "\n").split("\n") if r.returncode == 0 else None


def index_sources():
    """锚点里的文件名 → 仓库内实际路径（与 check_doc_anchors.py 一样按 basename 找）。"""
    idx = {}
    for base, dirs, files in os.walk(REPO):
        dirs[:] = [d for d in dirs if d not in (".git", "build", ".gradle", "node_modules", "tmp")]
        for f in files:
            if f.endswith((".kt", ".java", ".xml", ".gradle", ".kts", ".sh", ".py")):
                idx.setdefault(f, os.path.relpath(os.path.join(base, f), REPO).replace("\\", "/"))
    return idx


def drifted_records(json_path):
    recs = json.load(open(json_path, encoding="utf-8"))
    pinned = set()
    p = os.path.join(REPO, PIN)
    if os.path.exists(p):
        for line in open(p, encoding="utf-8", errors="replace"):
            if line.strip() and not line.startswith("#"):
                pinned.add(line.split("\t")[0])
    out = []
    for r in recs:
        if r["verdict"] in ("OK", "FILE-NOT-FOUND") or r["aid"] in pinned:
            continue
        out.append(r)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    if not (args.check or args.apply):
        print("要么 --check 要么 --apply"); return 2

    tmp = os.path.join(os.environ.get("TEMP", "/tmp"), "reanchor_records.json")
    run(["python", os.path.join("tools", "check_doc_anchors.py"), "--strict-id", "--pin", PIN,
         "--backend", os.path.join(os.path.dirname(REPO), "cargo-post-platform"),
         "--json", tmp] + DOCS)
    if not os.path.exists(tmp):
        print("× 判据工具没产出判决 JSON，本轮不搬"); return 1

    idx = index_sources()
    src_cache, head_cache = {}, {}

    def src(rel, head=False):
        key = rel
        c = head_cache if head else src_cache
        if key not in c:
            if head:
                c[key] = head_text(rel)
            else:
                p = os.path.join(REPO, rel)
                c[key] = open(p, encoding="utf-8", errors="replace").read().replace("\r\n", "\n").split("\n") \
                    if os.path.exists(p) else []
        return c[key]

    moves, manual = [], []
    for r in drifted_records(tmp):
        m = ANCHOR.search(r["anchor"])
        if not m:
            continue
        fname, a, b = m.group(1), int(m.group(2)), m.group(3)
        hi = int(b) if b else a
        rel = idx.get(fname)
        if not rel:
            manual.append((r, "工作树里找不到该文件"))
            continue
        hs = src(rel, head=True)
        cs = src(rel)
        if hs is None or a > len(hs):
            manual.append((r, "HEAD 里该文件不存在或行号越界"))
            continue
        want = [l.strip() for l in hs[a - 1:hi] if l.strip()]
        if not want:
            manual.append((r, "被指的片段是空行，无法按内容定位"))
            continue
        stripped = [l.strip() for l in cs]
        hits = [i + 1 for i in range(len(stripped) - len(want) + 1) if stripped[i:i + len(want)] == want]
        # 第 2 趟：切片命中多处（`}`、`val etaMinutes = …` 这类短行到处都有）⇒ 用判据自己的
        # 口径消歧：只保留"tokens 全落在 ±tolerance 窗口里"的那些命中，唯一才敢搬。
        if len(hits) > 1:
            def ok(pos):
                w = "\n".join(cs[max(0, pos - 1 - TOL):min(len(cs), pos + (hi - a) + TOL)])
                return all(t in w for t in r["tokens"])
            keep = [h for h in hits if ok(h)]
            if len(keep) == 1:
                hits = keep
            elif len(keep) > 1:
                # 第 2b 趟：内容完全相同的行在文件里出现多次（`etaMinutes = o.multiNumOrNull(…)`
                # 这种一行在四个 parse 块里长得一模一样），标识符窗口分不开它们。
                # 这时只有"离旧位置最近、且第二名差得够远"才算同一次位移；否则退回人工，
                # 让人去读那一段到底是哪个类的 parse —— 猜错就是把 A 类的读点说成 B 类。
                near = sorted(keep, key=lambda x: abs(x - a))
                if len(near) < 2 or abs(near[1] - a) - abs(near[0] - a) >= 15:
                    hits = [near[0]]
        # 第 3 趟：一块被改掉（0 命中）时，退到"旧位置附近找唯一含全部标识符的窗口"。
        # 只认唯一落点：附近有第二处就把这条退回人工，绝不猜。
        if not hits and r["tokens"]:
            near = [i + 1 for i in range(max(0, a - 251), min(len(cs), a + 250))
                    if all(t in cs[i] for t in r["tokens"])]
            if len(near) == 1:
                hits = near
        if len(hits) != 1:
            manual.append((r, "切片命中 %d 处（0=那块被改了，>1=不唯一）" % len(hits)))
            continue
        new_a = hits[0]
        new_hi = new_a + (hi - a)
        lo = max(0, new_a - 1 - TOL)
        window = "\n".join(cs[lo:min(len(cs), new_hi + TOL)])
        missing = [t for t in r["tokens"] if t not in window]
        # 第 4 趟：片段自己搬到了，但判给它的标识符原本靠**邻近行**凑齐（台账一句话里并列几个字段，
        # 分配器把它们都记给最近那个锚点）。这时把锚点扩成"覆盖这些标识符的最小连续段"，
        # 前提是段不长（≤40 行）且就在旧位置附近（±40 行）——否则退回人工，绝不猜。
        if missing and b is None and r["tokens"]:
            pos = []
            for t in r["tokens"]:
                at = [i + 1 for i in range(max(0, new_a - 61), min(len(cs), new_a + 60)) if t in cs[i]]
                if not at:
                    pos = None
                    break
                pos.append(min(at, key=lambda x: abs(x - new_a)))
            if pos:
                span_lo, span_hi = min(pos), max(pos)
                w2 = "\n".join(cs[max(0, span_lo - 1 - TOL):min(len(cs), span_hi + TOL)])
                if span_hi - span_lo <= 40 and abs(span_lo - a) <= 40 and all(t in w2 for t in r["tokens"]):
                    moves.append((r["doc"], r["line"], r["anchor"], "%s:%d-%d" % (fname, span_lo, span_hi), r["aid"]))
                    continue
        if missing:
            manual.append((r, "搬过去仍缺标识符 %s" % "/".join(missing[:4])))
            continue
        new_anchor = "%s:%d%s" % (fname, new_a, ("-%d" % new_hi) if b else "")
        if new_anchor == r["anchor"]:
            continue
        moves.append((r["doc"], r["line"], r["anchor"], new_anchor, r["aid"]))

    by_doc = collections.OrderedDict()
    for doc, line, old, new, aid in moves:
        by_doc.setdefault(doc, []).append((line, old, new, aid))

    for doc, items in by_doc.items():
        print("%s：%d 条" % (doc, len(items)))
        for line, old, new, aid in items:
            print("   %-26s -> %-26s  %s:%d" % (old, new, os.path.basename(doc), line))
    print("要人工判（不自动搬）：%d 条" % len(manual))
    for r, why in manual:
        print("   %-30s %-24s %s  ⇒ %s" % (r["anchor"], "%s:%d" % (os.path.basename(r["doc"]), r["line"]), r["verdict"], why))

    if args.check or not moves:
        print("（--check：未落盘）")
        return 0

    for doc, items in by_doc.items():
        p = os.path.join(REPO, doc)
        raw = open(p, "rb").read()
        cr, lf = raw.count(b"\r"), raw.count(b"\n")
        lines = raw.decode("utf-8").split("\n")
        for line, old, new, aid in items:
            text = lines[line - 1]
            if text.count(old) != 1:
                print("× %s:%d 里 %r 命中 %d 次 —— 整本不落盘" % (doc, line, old, text.count(old)))
                return 1
            lines[line - 1] = text.replace(old, new, 1)
        out = "\n".join(lines).encode("utf-8")
        if out.count(b"\r") != cr or out.count(b"\n") != lf:
            print("× %s 行尾或行数不守恒 —— 整本不落盘" % doc)
            return 1
        shutil.copy2(p, p + ".bak-reanchor")
        tmp_path = p + ".tmp-reanchor"
        with open(tmp_path, "wb") as fh:
            fh.write(out)
        os.replace(tmp_path, p)
        print("落了 %s：%d 条（备份 .bak-reanchor）" % (doc, len(items)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
