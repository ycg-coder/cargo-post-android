#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""台账 `文件.kt:行号` 锚点复核器。

用途：改过 Models.kt / Repo.kt 这类被台账大量点名的文件后，行号会整体漂移。
本脚本把文档里每条锚点解析出来，与磁盘当前内容比对，输出：
  OUT-OF-RANGE  锚点行号超过文件实际行数（一定是错的）
  FILE-NOT-FOUND 两个仓里找不到同名文件（文件名写错或文件已删）
  DUP          同名文件在两个仓/两处目录都有（取索引里的第一个，只提示不判红）
  ID-MISS     --strict-id：判给**这个锚点**的标识符在它指向的行±tolerance 里一个都没出现
  其余逐条打印 `文档行号 aid=… 锚点 当前那一行的内容`，由人工判是否还对得上。

F-3 ⑰（2026-10-03）按 `D-③-5` 的修法改了两处，让这个数**能当闸门**：
  · 标识符**按锚点分配**（`assign_tokens`）：旧口径按整个表格单元取词，一格并列几个锚点时
    A 被要求包含 B 的名字 ⇒ 8 本台账实测 612 条 ID-MISS、抽查全是假报。
  · 每条锚点有稳定 `aid`（文档名 + 锚点原文，**不含行号**）：`--pin` 拿它比在册表，
    新增即红、在册的悄悄消失也红；`--json` 逐锚点落盘判决，`--pin-write` 重抄在册表。

用法：
  python tools/check_doc_anchors.py docs/android/CONTRACT_EVIDENCE.md
  python tools/check_doc_anchors.py --only Models.kt,Repo.kt docs/android/DEFAULT_VALUE_AUDIT.md
  python tools/check_doc_anchors.py --quiet-ok docs/android/CONTRACT_EVIDENCE.md   # 只打可疑的
  python tools/check_doc_anchors.py --strict-id --pin tools/fixtures/doc_anchor_pins.txt \
      --json /tmp/anchors.json <七本台账>
"""
import argparse
import hashlib
import json
import os
import re
import sys

REPO_CANDIDATES = [
    "app/src/main/java/com/shanxiang/buspost",
    "app/src/test/java/com/shanxiang/buspost",
    "yudao-module-transport/src/main/java",
    "yudao-module-transport/src/test/java",
]
# 后端是同级的另一个仓（本脚本默认在 cargo-post-android 下运行）
BACKEND_DEFAULT = "../cargo-post-platform"
ANCHOR = re.compile(r"([A-Za-z0-9_]+\.(?:kt|java)):(\d+)(?:-(\d+))?")
TOKEN = re.compile(r"[A-Za-z][A-Za-z0-9_]{4,}")
# 同一条锚点在文档里出现多次时，代表它的是最坏的那个判决（闸门比的是 aid 集合，不是出现次数）
RANK = {"OK": 0, "ID-MISS": 1, "FILE-NOT-FOUND": 2, "OUT-OF-RANGE": 3}
# 文档里到处出现的泛指词，当"内容指纹"没有判别力
STOP = {"keys", "parse", "multi", "null", "true", "false", "data", "list", "code",
        "impl", "controller", "respvo", "reqvo", "string", "integer", "boolean",
        "bigdecimal", "double", "private", "class", "const", "vo", "get", "set",
        "schema", "jackson", "sql", "admin", "item", "items", "json", "obj"}


def build_index(roots):
    """整仓扫（跳过 build/target/.git），不然 framework 里的 CommonResult/PageResult 会假报找不到。"""
    idx = {}
    for root in roots:
        for dirpath, dirnames, files in os.walk(root):
            norm = dirpath.replace("\\", "/")
            if "/build/" in norm or "/target/" in norm or "/.git/" in norm:
                dirnames[:] = []
                continue
            for f in files:
                if f.endswith((".kt", ".java")):
                    idx.setdefault(f, []).append(os.path.join(dirpath, f))
    return idx


def aid_of(doc, anchor):
    """锚点标识符：由「文档名 + 锚点原文」导出，**不含行号**，所以台账行整体漂移时 ID 不变。
    有 ID 才能把 ID-MISS 当闸门（在册的那几个不许悄悄变多，也不许悄悄变少而没人下调在册数）。"""
    key = "%s|%s" % (os.path.basename(doc), anchor)
    return hashlib.sha1(key.encode("utf-8")).hexdigest()[:8]


def assign_tokens(text, centers, table_row):
    """把一行里的标识符**按锚点分配**（D-③-5 的修法）。

    旧口径把标识符按整个表格单元取，再要求它出现在该格**每一个**锚点的 ±tolerance 窗口里
    ⇒ 一格并列几个锚点时，A 锚点被要求包含 B 锚点的名字，假报为主（实测 8 本 612 条）。
    规则（逐字可复现）：
      1. 先把锚点本身抹成等长空格 —— `Models.kt:276` 里的 "Models" 是文件名不是那一行的内容指纹；
      2. 取词范围：表格行取第一列（字段名列），非表格行取整行；
      3. 每个标识符归**中心点距离最近**的那个锚点，同距归靠前的那个。
    """
    if not centers:
        return {}
    body = ANCHOR.sub(lambda m: " " * len(m.group(0)), text)
    if table_row:
        parts = body.split("|")
        if len(parts) <= 2 or parts[0].strip() != "":
            return {}
        lo = len(parts[0]) + 1
        pool = [(m.group(0), lo + m.start()) for m in TOKEN.finditer(parts[1])]
    else:
        pool = [(m.group(0), m.start()) for m in TOKEN.finditer(body)]
    out = {}
    for tok, off in pool:
        if tok.lower() in STOP or tok.endswith((".kt", ".java")):
            continue
        c = off + len(tok) / 2.0
        i = min(range(len(centers)), key=lambda k: (abs(centers[k] - c), k))
        out.setdefault(i, []).append(tok)
    return out


def load_pin(path):
    """在册表：每行 `AID<TAB>verdict<TAB>anchor`（`--pin-write` 产出，人只负责下调不负责重抄）。"""
    pinned = {}
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if not line.strip() or line.startswith("#"):
                continue
            cols = line.rstrip("\n").split("\t")
            pinned[cols[0]] = cols[1] if len(cols) > 1 else ""
    return pinned


def main():
    # 与 census 同一条教训：Windows 下裸跑会把中文/⇒ 撞 GBK stdout 编不过（本轮在 census 上实测到）
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass
    ap = argparse.ArgumentParser()
    ap.add_argument("docs", nargs="+")
    ap.add_argument("--root", default=".")
    ap.add_argument("--backend", default=BACKEND_DEFAULT, help="后端仓路径（找 *.java 锚点用）")
    ap.add_argument("--only", default="", help="逗号分隔的文件名过滤，如 Models.kt,Repo.kt")
    ap.add_argument("--quiet-ok", action="store_true", help="只打印可疑与已核对清单外的行")
    ap.add_argument("--width", type=int, default=90)
    ap.add_argument("--strict-id", action="store_true",
                    help="机检：分配到**本锚点**的标识符必须出现在目标行±tolerance 内，否则记 ID-MISS")
    ap.add_argument("--tolerance", type=int, default=3)
    ap.add_argument("--json", dest="json_out", default="", help="逐锚点判决落盘（含 aid/verdict/tokens）")
    ap.add_argument("--pin", default="", help="在册 ID 闸门：新增即红，在册的消失了也红（要人下调在册数）")
    ap.add_argument("--pin-write", dest="pin_write", default="", help="把本次的可疑锚点写成在册表")
    args = ap.parse_args()
    # 台账里的复现命令有时不带 PYTHONIOENCODING：Windows 重定向到文件会按 GBK 打印，中文与 ⇒ 会炸
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

    roots = [os.path.abspath(args.root), os.path.abspath(os.path.join(args.root, args.backend))]
    idx = build_index(roots)
    lines_cache = {}
    only = {s.strip() for s in args.only.split(",") if s.strip()}

    stats = {"anchors": 0, "out_of_range": 0, "not_found": 0, "printed": 0, "miss": 0}
    records = []
    for doc in args.docs:
        with open(doc, encoding="utf-8", errors="replace") as fh:
            for lineno, text in enumerate(fh, 1):
                hits = list(ANCHOR.finditer(text))
                if not hits:
                    continue
                table_row = text.lstrip().startswith("|")
                # 锚点中心（等长替换不改变偏移）：assign_tokens 用它把标识符归给最近的那个锚点
                centers = [m.start() + len(m.group(0)) / 2.0 for m in hits]
                tok_map = assign_tokens(text, centers, table_row) if args.strict_id else {}
                for ai, m in enumerate(hits):
                    fname, a, b = m.group(1), int(m.group(2)), m.group(3)
                    if only and fname not in only:
                        continue
                    stats["anchors"] += 1
                    aid = aid_of(doc, m.group(0))
                    hi = int(b) if b else a
                    toks = tok_map.get(ai, [])
                    rec = {"aid": aid, "doc": doc, "line": lineno, "anchor": m.group(0),
                           "verdict": "OK", "tokens": toks}
                    paths = idx.get(fname)
                    if not paths:
                        stats["not_found"] += 1
                        rec["verdict"] = "FILE-NOT-FOUND"
                        print("%s:%d  aid=%s  FILE-NOT-FOUND  %s" % (doc, lineno, aid, m.group(0)))
                        records.append(rec)
                        continue
                    if len(paths) > 1:
                        print("%s:%d  DUP(%d)  %s -> %s" % (doc, lineno, len(paths), m.group(0),
                                                            " | ".join(paths)))
                    p = paths[0]
                    if p not in lines_cache:
                        with open(p, encoding="utf-8", errors="replace") as s:
                            lines_cache[p] = s.read().splitlines()
                    src = lines_cache[p]
                    if hi > len(src):
                        stats["out_of_range"] += 1
                        rec["verdict"] = "OUT-OF-RANGE"
                        print("%s:%d  aid=%s  OUT-OF-RANGE  %s （该文件只有 %d 行）"
                              % (doc, lineno, aid, m.group(0), len(src)))
                        records.append(rec)
                        continue
                    if args.strict_id:
                        lo = max(0, a - 1 - args.tolerance)
                        hi2 = min(len(src), hi + args.tolerance)
                        window = "\n".join(src[lo:hi2])
                        if toks and not any(t in window for t in toks):
                            stats["miss"] += 1
                            rec["verdict"] = "ID-MISS"
                            print("%s:%d  aid=%s  ID-MISS  %s  窗口内没有 %s（这些名字是判给它的，"
                                  "归最近的锚点）"
                                  % (doc, lineno, aid, m.group(0), "/".join(toks[:4])))
                        records.append(rec)
                        continue
                    # ⚠ 顺序本身就是个坑：旧实现把 --quiet-ok 放在 ID 检查**前面**，
                    #   于是 `--quiet-ok --strict-id` 的 miss 恒等于 0（台账里那些 "miss=0" 是这么来的，
                    #   它当时根本没判）。现在 quiet 只压"行内容"那行输出，判决照算。
                    if args.quiet_ok:
                        records.append(rec)
                        continue
                    body = src[a - 1].strip()[: args.width]
                    stats["printed"] += 1
                    print("%s:%d  aid=%s  %s:%d-%d  | %s" % (doc, lineno, aid, fname, a, hi, body))
                    records.append(rec)

    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8") as fh:
            json.dump(records, fh, ensure_ascii=False, indent=1)
    # 同一个 aid 可能出现多次（同一条锚点在文档里引用了几处）：取最坏的那个判决代表它
    worst = {}
    for r in records:
        if r["verdict"] == "OK":
            continue
        cur = worst.get(r["aid"])
        if cur is None or RANK[r["verdict"]] > RANK[cur["verdict"]]:
            worst[r["aid"]] = r
    if args.pin_write:
        with open(args.pin_write, "w", encoding="utf-8") as fh:
            fh.write("# 由 check_doc_anchors.py --pin-write 生成，人只下调不重抄\n")
            fh.write("# aid\tverdict\t锚点\t文档:行\t判给它的标识符\n")
            for aid in sorted(worst):
                r = worst[aid]
                fh.write("%s\t%s\t%s\t%s:%d\t%s\n" % (aid, r["verdict"], r["anchor"],
                                                      os.path.basename(r["doc"]), r["line"],
                                                      "/".join(r["tokens"][:4])))
        print("[在册] 写入 %s（%d 个可疑 aid，来自 %d 条可疑出现）"
              % (args.pin_write, len(worst), len([r for r in records if r["verdict"] != "OK"])))
    gate = 0
    if args.pin:
        pinned = load_pin(args.pin)
        new = [aid for aid in sorted(worst) if aid not in pinned]
        gone = [aid for aid in sorted(pinned) if aid not in worst]
        for aid in new:
            gate += 1
            r = worst[aid]
            print("× 新增可疑锚点 aid=%s %s %s（%s:%d）不在在册表 %s 里"
                  % (aid, r["verdict"], r["anchor"], os.path.basename(r["doc"]), r["line"], args.pin))
        for aid in gone:
            gate += 1
            print("× 在册锚点 aid=%s 本次不再可疑（%s）⇒ 真的修好了就下调在册表，别让它悄悄消失"
                  % (aid, pinned[aid]))
        if not args.strict_id:
            print("[闸门] ⚠ 没传 --strict-id，ID-MISS 这一类本轮不产生，在册里那部分不会被复核")
        print("[闸门] 在册 %d 个 aid，本次可疑 %d 个 aid，新增 %d 个，消失 %d 个"
              % (len(pinned), len(worst), len(new), len(gone)))

    print("[聚合] anchors=%d out_of_range=%d not_found=%d miss=%d printed=%d suspicious=%d"
          % (stats["anchors"], stats["out_of_range"], stats["not_found"], stats["miss"],
             stats["printed"], len([r for r in records if r["verdict"] != "OK"])))
    if gate or stats["out_of_range"]:
        return 1
    # --pin 在场时 ID-MISS 由闸门管（在册的放行、新增的红）；不在场才用"miss 必须为 0"这条硬闸
    if args.strict_id and stats["miss"] and not args.pin:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
