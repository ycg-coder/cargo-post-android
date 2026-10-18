#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把"本轮源码编辑造成的行号位移"机械摊到台账锚点上 —— 吃 `reanchor_ledgers.py` 交下来的那批。

为什么要有第二本（`reanchor_ledgers.py` 已经在做同一件事）：
那本按**切片文本**在工作树里找同一块，找到唯一一处才搬。2026-10-18（D-173）实测它搬成 81 条、交回 26 条"人工判"，
交回的原因全是"切片命中 41 处 / 101 处 / 0 处" —— 要么那一行太普通（`} catch (e: Exception) {`），
要么那块内容被改了。可这 26 条有位移歧义吗？没有：**本轮改动是净插入**，位移是"位置的分段常数函数"：
`git diff -U0` 每段给出 (old_len, new_len)，落在这段之后的锚点一律加 (new_len - old_len)，
落进改动段内部的**不猜**，列出来给人。两本分工：内容搬动找 `reanchor_ledgers.py`，纯插入/删除找这本。

靶子只取"判据当下念不出标识符、**且搬过去那一窗确实念得出**"的锚点，所以它天然幂等（搬完再跑就报 0 条）。
这一关不是洁癖：在册的 ID-MISS 有两类，一类是本轮位移造成的（搬了就对），一类是本轮之前就指错的
（#159 那几百条旧账）；后者搬 +55 只会把"指错的一行"换成"另一行"，台账念起来更像真的、其实更查不出来。
不限定靶子的代价同一轮实测过：一次念出"机械平移 374 条"，而真实只有 27 条。

跑法：
  python tools/reanchor_by_diff.py                # 只报要搬什么，不落盘
  python tools/reanchor_by_diff.py --apply         # 落盘（每本台账留 .bak-diffshift）
  python tools/reanchor_by_diff.py --lines f.tsv    # 强行按清单搬（文档<TAB>行号），跳过目的地验证
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
ANCHOR_RE = re.compile(r"(\b[A-Za-z0-9_]+\.kt):(\d+)(?:-(\d+))?")
DOCS = ["ANDROID_PARITY_AUDIT_V2.md", "AI_ITERATION_STATE.md", "DECISION_LOG.md",
        "CONTEXT_LOCK.md", "THIRD_PARTY_NOTICES.md",
        "docs/android/CONTRACT_EVIDENCE.md", "docs/android/ANDROID_V2.1_CHECKLIST.md"]
TOL = 3  # 与 check_doc_anchors.py 的 --tolerance 同一个口径


def git(args):
    return subprocess.run(["git"] + args, cwd=REPO, capture_output=True,
                          text=True, encoding="utf-8", errors="replace").stdout


def hunks(path):
    out = git(["diff", "-U0", "--", path])
    hs = []
    for m in re.finditer(r"^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@", out, re.M):
        a = int(m.group(1))
        b = int(m.group(2)) if m.group(2) is not None else 1
        d = int(m.group(4)) if m.group(4) is not None else 1
        hs.append((a, b, d))
    return hs


def table_for(paths):
    """basename -> (delta 函数, 改动段列表, 工作树绝对路径)。同名 .kt 只有一份，所以按 basename 索引。

    ⚠ `-U0` 下**纯插入**的段长这样：`@@ -162,0 +163,6 @@` —— 旧侧长度是 0，不是 1。
    位移按 `新长度 - 旧长度` 算（这里 = 6），而"改动段"要占一格才好用它判锚点是否落在里面（= 162..162）；
    两者共用一个 `max(b,1)` 会把 +6 算成 +5。这一版本当轮就错过一次：`Models.kt:242` 被搬去 247，
    而它真正的家是 248 —— 位移算错比不搬更坏，因为搬完它"看着对"。
    """
    t = {}
    for p in paths:
        hs = hunks(p)
        ranges = [(a, a + max(b, 1) - 1) for a, b, _d in hs]

        def delta(x, hs=hs):
            return sum(nd - ob for oa, ob, nd in hs if oa + max(ob, 1) - 1 < x)
        t[os.path.basename(p)] = (delta, ranges, os.path.join(REPO, p))
    return t


def idmiss_records(kt_names):
    """问判据本身要靶子，而不是自己再实现一套标识符归给规则（两套规则迟早对同一条锚点说两句话）。"""
    out = os.path.join(tempfile.gettempdir(), "rbad_anchor_%d.json" % os.getpid())
    cmd = [sys.executable, os.path.join(HERE, "check_doc_anchors.py"), "--strict-id",
           "--backend", os.path.join(os.path.dirname(REPO), "cargo-post-platform"),
           "--json", out, "--only", ",".join(kt_names)] + DOCS
    subprocess.run(cmd, cwd=REPO, env=dict(os.environ, PYTHONIOENCODING="utf-8"),
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
    if not os.path.exists(out):
        raise SystemExit("STOP 判据没产出判决表（check_doc_anchors.py 跑不起来）⇒ 这本没有靶子，一条都不搬")
    return json.load(open(out, encoding="utf-8"))


def head_text(rel):
    """读 HEAD 那份文件（不存在就回 None）。这本的"归谁"判断全靠它，不需要另开 worktree。"""
    out = subprocess.run(["git", "show", "HEAD:%s" % rel], cwd=REPO,
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    return out.stdout if out.returncode == 0 else None


def window_ok(lines, lo, hi, toks):
    if not lines:
        return False
    span = max(1, hi - lo + 1)
    win = "\n".join(lines[max(0, lo - 1 - TOL): lo - 1 + span + TOL])
    return all(re.search(r"\b%s\b" % re.escape(t), win) for t in toks)


def pick_targets(tab):
    """靶子 = **本轮源码编辑**弄坏的锚点，判据有三步（少一步就不是摊位移，是重指内容）：

      1. 这一条在判据当下念不出标识符（ID-MISS）；
      2. 同一条锚点在 **HEAD** 那份文档 + HEAD 那份代码里是念得出的 ⇒ 坏是我弄的，不是 #159 的旧账；
      3. 搬到 `HEAD 行号 + 位移` 之后，在当前代码那一窗**又**念得出了 ⇒ 目的地验得出。

    只做"当下 ID-MISS"这一步会误伤：本轮之前就指错的那些旧账，+55 之后常常碰巧也念得出，
    于是台账从"指错一行"变成"指得像对的另一行"，比留着更难查（实测 12 条这类候选）。
    新写的锚点（HEAD 那份文档里这一行还没有锚点）一律不碰：那是新账，该由人核语义、由 `--pin-write` 登记。
    """
    wanted = {}
    kt_cache, doc_cache, cur_doc_cache = {}, {}, {}
    for r in idmiss_records(sorted(tab)):
        toks = r.get("tokens") or []
        m = ANCHOR_RE.search(r.get("anchor", ""))
        doc = r["doc"].replace("\\", "/")
        if not m or not toks or doc not in DOCS:
            continue
        fname, lo, hi = m.group(1), int(m.group(2)), int(m.group(3) or m.group(2))
        if fname not in tab:
            continue
        delta, ranges, fp = tab[fname]
        if any(lo2 <= lo <= hi2 for lo2, hi2 in ranges):
            continue  # 落进本轮改动段内部：位移不是常数，得读原话
        if doc not in doc_cache:
            ht = head_text(doc)
            doc_cache[doc] = ht.split("\n") if ht is not None else []
        if cur_doc_cache.get(doc) is None:
            cur_doc_cache[doc] = open(os.path.join(REPO, doc), encoding="utf-8",
                                      errors="replace").read().split("\n")
        hd = doc_cache[doc]
        cd = cur_doc_cache[doc]
        if r["line"] - 1 >= len(hd) or r["line"] - 1 >= len(cd):
            continue  # HEAD 里还没有这一行 ⇒ 本轮新写的锚点，不猜
        # 配对按"同一行里同名的第几处"来，不按"第一个同名的"：一行挂两条同文件锚点很常见
        # （实测 CONTRACT_EVIDENCE.md:206 一行里 `Repo.kt:524` 与 `Repo.kt:87` 各指一处，
        #   只取第一条会让两处都搬到同一个目的地，念出 `Repo.kt:87 -> Repo.kt:701` 这种荒唐位移）。
        def occ(line_txt):
            out = []
            for mm in ANCHOR_RE.finditer(line_txt):
                if mm.group(1) == fname:
                    out.append(mm)
            return out
        cur_occ = occ(cd[r["line"] - 1])
        idx = next((j for j, mm in enumerate(cur_occ) if mm.group(0) == m.group(0)), None)
        head_occ = occ(hd[r["line"] - 1])
        if idx is None or idx >= len(head_occ):
            continue  # 两版这一行的锚点数对不上 ⇒ 不是纯位移，交回人工
        hm = head_occ[idx]
        lo0 = int(hm.group(2))
        hi0 = int(hm.group(3)) if hm.group(3) else lo0
        if lo == lo0:
            continue  # 与 HEAD 同一个数，说明这条不是被我搬坏的（是内容漂移）
        if fname not in kt_cache:
            rel = os.path.relpath(fp, REPO).replace("\\", "/")
            s = head_text(rel)
            kt_cache[fname] = s.split("\n") if s is not None else []
        if not window_ok(kt_cache[fname], lo0, hi0, toks):
            continue  # HEAD 那版本就念不出 ⇒ 旧账，不是本轮位移
        nlo, nhi = lo0 + delta(lo0), hi0 + delta(hi0)
        cur = open(fp, encoding="utf-8", errors="replace").read().split("\n")
        if not window_ok(cur, nlo, nhi, toks):
            continue  # 目的地验不出 ⇒ 那块内容真被改了，交回人工
        wanted.setdefault((doc, r["line"]), {})[m.group(0)] = (nlo, nhi)
    return wanted


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--lines", default="", help="强行按清单搬（文档<TAB>行号），跳过目的地验证")
    a = ap.parse_args()

    paths = [p for p in git(["diff", "--name-only"]).splitlines() if p.endswith(".kt")]
    if not paths:
        print("工作树没有 .kt 改动 ⇒ 没有位移可摊（这本只在改过源码的那一轮有用）")
        return 0
    tab = table_for(paths)
    for name, (_d, ranges, p) in sorted(tab.items()):
        print("%-18s 改动段=%s 位移段=%s" % (name, ranges, hunks(p)))

    if a.lines:
        wanted = {}
        for ln in open(a.lines, encoding="utf-8", errors="replace"):
            ln = ln.rstrip("\n")
            if ln:
                doc, num = ln.split("\t")
                wanted[(doc.replace("\\", "/"), int(num))] = None
        print("限定清单 %s：%d 行（不验目的地）" % (a.lines, len(wanted)))
    else:
        wanted = pick_targets(tab)
        print("靶子（判据当下 ID-MISS）中『搬过去这一窗确实念得出标识符』的：%d 行" % len(wanted))
        if not wanted:
            print("一条都没有 ⇒ 没有可机械摊平的位移（要么本轮已搬过，要么剩下的都得人工读原话）")
            return 0

    moved, human = [], []
    for doc in [d for d in git(["diff", "--name-only"]).splitlines() if d.endswith(".md")]:
        fp = os.path.join(REPO, doc)
        lines = open(fp, encoding="utf-8", errors="replace").read().split("\n")
        changed = False
        for i, ln in enumerate(lines):
            hit = wanted.get((doc.replace("\\", "/"), i + 1))
            if hit is None and not (wanted and (doc.replace("\\", "/"), i + 1) in wanted):
                continue
            for m in ANCHOR_RE.finditer(ln):
                fname, lo = m.group(1), int(m.group(2))
                hi = int(m.group(3)) if m.group(3) else None
                if fname not in tab:
                    continue
                delta, ranges, _p = tab[fname]
                if any(lo2 <= lo <= hi2 for lo2, hi2 in ranges):
                    human.append((doc, i + 1, fname, lo))
                    continue
                if isinstance(hit, dict) and m.group(0) in hit:
                    nlo, nhi = hit[m.group(0)]  # 目的地 = 上面验过的那一格，不再按当前值重算
                elif hit is None:
                    nlo = lo + delta(lo)
                    nhi = (hi + delta(hi)) if hi is not None else None
                else:
                    continue
                old_txt = m.group(0)
                new_txt = "%s:%d" % (fname, nlo) + ("" if nhi in (None, nlo) else "-%d" % nhi)
                if old_txt == new_txt:
                    continue
                ln = ln.replace(old_txt, new_txt)
                lines[i] = ln
                changed = True
                moved.append((doc, i + 1, old_txt, new_txt))
        if changed and a.apply:
            shutil.copyfile(fp, fp + ".bak-diffshift")
            with open(fp, "w", encoding="utf-8", newline="") as fh:
                fh.write("\n".join(lines))
    print("%s机械平移 %d 条；落进改动段内部、要人判 %d 条" % (
        "" if a.apply else "（--check：未落盘）", len(moved), len(human)))
    for d, l, o, n in moved:
        print("MOVE   %-30s:%-6s %s -> %s" % (d, l, o, n))
    for d, l, f, o in human:
        print("HUMAN  %-30s:%-6s %s:%d（锚点落在本轮改动段内部，位移不是常数，得读原话）" % (d, l, f, o))
    return 0


if __name__ == "__main__":
    sys.exit(main())
