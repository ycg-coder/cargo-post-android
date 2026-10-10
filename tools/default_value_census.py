#!/usr/bin/env python3
"""F-2 步 5：把全项目"带默认值的读法"按 A/B/C/D 分类，产出可复核的证据表。

分类口径（判据在 tools/check_default_value_probes.sh 里钉住）：
  A 真零值   ：后端 VO 用原始类型声明该字段（int/long/double/boolean）→ 报文里恒有值，0 是真实业务值。
  B 缺字段   ：后端 VO 用包装类型声明（Integer/Long/Double/Boolean/BigDecimal…）→ Jackson 允许该键缺席，
              此时非可空读法会把"没给"读成 0/false，属于把 B 当 A 说，必须改可空或走"未给"文案。
  C 异常/无生产者：键在后端 0 命中（自造别名或客户端兜底字段），或读法处在 catch/降级分支里 → 不能显示成真实值。
  D UI 占位  ：UI 层字面量默认（?: 0、coerceAtLeast 等）只许用于布局，不许冒充业务结论。

外部真实生产者（高德/OpenWeather 报文）单独记 E 类：它们确实是真来源，但键同样可能缺席，
所以按 B 的判据处理（缺键不许读成 0℃/0 级风）。

F-3 ⑰（2026-10-03）补三列，把 `D-②-4`（在册"32 处 C"不可复现）从"只能引用来历不明的数"变成逐字可复现：
  · `demo_keys`   —— 第四面：`Demo*.kt` 实际写出去的键（`"k" to` 与 `.put("k",` 两种写法）。
                     "三面 0 生产者"从前只有后端/小程序两facet 落进了 JSON，演示面这一列是空的，
                     所以 §二之二 那句"C∩数值布尔=25、其中非可空=19"没法被脚本自证。
  · `shape`       —— 逐行形状（arr / numbool / str / other）+ `numbool_nonnull`（不含 OrNull 的那一支），
                     规则照抄 `docs/android/DEFAULT_VALUE_AUDIT.md` §二之二，改了规则要同时改台账。
  · `dvk_bridge`  —— DV-k 的 grep 口径与本脚本的行口径差多少、差在哪两行（实测 115 vs 113），
                     没有这一列，两个数各自成立却对不上，读者只能挑一个。

沙盒用法（负控簿在 /tmp 副本上跑）：BUSPOST_ANDROID=沙盒根 BUSPOST_WORK=真 work 根。
⚠ 后端/小程序两面在沙盒里不存在 ⇒ 不传 BUSPOST_WORK 会让所有键变成"后端 0 命中"，
   C 类凭空涨满。本脚本在这种情况下**直接报错退出**，不出半张表。

用法：python tools/default_value_census.py [--write]
输出路径：--write 落 `DV_CENSUS_JSON` 指定的文件，未指定则是 `%TEMP%/default_value_census.json`。
"""
import os
import re
import sys
import json
import collections

ANDROID = os.path.abspath(os.environ.get("BUSPOST_ANDROID")
                           or os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
WORK = os.path.abspath(os.environ.get("BUSPOST_WORK") or os.path.join(ANDROID, ".."))
BACKEND = os.path.join(WORK, "cargo-post-platform")
MINI = os.path.join(WORK, "cargo-post-miniprogram")
SRC = os.path.join(ANDROID, "app", "src", "main", "java", "com", "shanxiang", "buspost")

PRIMITIVE = {"int", "long", "double", "float", "boolean", "short", "byte", "char"}
WRAPPER = {
    "Integer": "int", "Long": "long", "Double": "double", "Float": "float",
    "Boolean": "boolean", "Short": "short", "Byte": "byte", "Character": "char",
    "String": "string", "BigDecimal": "number", "BigInteger": "number",
    "LocalDateTime": "time", "LocalDate": "time", "LocalTime": "time",
}

FIELD_DECL = re.compile(
    r"^\s*(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?"
    r"([A-Za-z_][A-Za-z0-9_<>, .?]*?)\s+([a-zA-Z_][A-Za-z0-9_]*)\s*(?:=[^;]*)?;"
)
READ_SITE = re.compile(r"multi(?:Num|Bool|Str|Int|Arr)(?:OrNull)?\([^)]*\)")
OPT_SITE = re.compile(r"\bopt(?:Boolean|Int|Long|Double|Number|String)\([^)]*\)")
KEY_IN_SITE = re.compile(r'"([A-Za-z][A-Za-z0-9_]*)"')
COMMENT = re.compile(r"^\s*(\*|//|/\*)")

# 演示面 writer 的两种写法：`j("k" to v, …)` 的 map 形与 `o.put("k", v)` 的直接形
DEMO_TO = re.compile(r'"([A-Za-z][A-Za-z0-9_]*)"\s*to\s*')
DEMO_PUT = re.compile(r'\.put\(\s*"([A-Za-z][A-Za-z0-9_]*)"')

# 形状拆分规则 = DEFAULT_VALUE_AUDIT.md §二之二（逐字照抄，别在这里单方面改口径）
SHAPE_ARR = re.compile(r"multiArr\(|optJSONArray")
SHAPE_NUMBOOL = re.compile(r"multi(?:Num|Bool)(?:OrNull)?\(|opt(?:Int|Double|Boolean|Long)\(")
SHAPE_STR = re.compile(r"multiStr\(|optString")
# "非可空"= numbool 里不含 OrNull 的子集（opt* 一律有默认值，天然非可空）
NUMBOOL_NONNULL = re.compile(r"multi(?:Num|Bool)\(|opt(?:Int|Double|Boolean|Long|Number)\(")
# DV-k 的 grep 口径（`multi(Num|Bool)\(` + 只排注释开头的行），用来把两个基数接起来
DV_K = re.compile(r"multi(?:Num|Bool)\(")
DV_K_SKIP = re.compile(r"^\s*(\*|//)")


def walk(root, suffix):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in {".git", "build", "node_modules", "dist"}]
        for fn in filenames:
            if fn.endswith(suffix):
                yield os.path.join(dirpath, fn)


def backend_decls():
    """键名 -> [(decl_kind, file:line)]，decl_kind ∈ primitive/wrapper/other"""
    idx = collections.defaultdict(list)
    for path in walk(BACKEND, ".java"):
        base = os.path.basename(path)
        if base.endswith("Impl.java") or "/test/" in path.replace("\\", "/"):
            continue
        try:
            with open(path, "r", encoding="utf-8", errors="replace") as fh:
                lines = fh.readlines()
        except OSError:
            continue
        for i, line in enumerate(lines, 1):
            if COMMENT.match(line):
                continue
            m = FIELD_DECL.match(line)
            if not m:
                continue
            typ, name = m.group(1).strip(), m.group(2)
            plain = typ.split("<")[0].strip()
            if plain in PRIMITIVE:
                kind = "primitive"
            elif plain in WRAPPER:
                kind = "wrapper"
            else:
                kind = "object"
            idx[name].append((kind, f"{base}:{i}", typ))
    return idx


def mini_reads():
    """小程序侧真实读点键名集合（用于判断键是不是三方都在用）"""
    hit = set()
    for path in walk(MINI, ".js"):
        p = path.replace("\\", "/")
        if "/node_modules/" in p or p.endswith(".test.js"):
            continue
        try:
            with open(path, "r", encoding="utf-8", errors="replace") as fh:
                text = fh.read()
        except OSError:
            continue
        hit.update(re.findall(r"\.([a-zA-Z][A-Za-z0-9_]*)\b", text))
    return hit


def demo_writers():
    """演示面（第四个真相源）实际写出去的键：`Demo*.kt` 里的 `"k" to` 与 `.put("k",`。

    扫描范围由内容决定（文件名前缀），不手挑行号；注释行按 COMMENT 口径排除。
    """
    keys = collections.defaultdict(list)
    files, to_n, put_n = [], 0, 0
    for path in walk(SRC, ".kt"):
        base = os.path.basename(path)
        if not base.startswith("Demo"):
            continue
        files.append(base)
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            for i, line in enumerate(fh, 1):
                if COMMENT.match(line):
                    continue
                for k in DEMO_TO.findall(line):
                    to_n += 1
                    keys[k].append("%s:%d" % (base, i))
                for k in DEMO_PUT.findall(line):
                    put_n += 1
                    keys[k].append("%s:%d" % (base, i))
    summary = {"files": files, "to_writes": to_n, "put_writes": put_n, "distinct": len(keys)}
    return keys, summary


def shape_of(sites):
    """一行里可能有几处读法：按 §二之二 的先后顺序（arr → numbool → str → 其余）取一个形状。"""
    text = " ".join(sites)
    if SHAPE_ARR.search(text):
        return "arr"
    if SHAPE_NUMBOOL.search(text):
        return "numbool"
    if SHAPE_STR.search(text):
        return "str"
    return "other"


def main():
    # 台账里的复现命令是裸 `python tools/...`：Windows 下重定向到文件会按 GBK 编码 stdout，
    # JSON 里的中文与 ⇒/≤ 之类符号会当场 UnicodeEncodeError（本轮实测踩到）。
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass
    decls = backend_decls()
    mini = mini_reads()
    demo, demo_summary = demo_writers()
    # 两面空了就不是"这张表结论是 C 满屏"，而是沙盒/路径没接上 ⇒ 拒答，别出表
    if not decls:
        sys.exit("BACKEND-DECLS-EMPTY: %s 扫不到字段声明（沙盒要传 BUSPOST_WORK）" % BACKEND)
    if not mini:
        sys.exit("MINI-READS-EMPTY: %s 扫不到读点" % MINI)
    if not demo:
        sys.exit("DEMO-WRITERS-EMPTY: %s 下找不到 Demo*.kt 的写键" % SRC)
    rows = []
    dvk_sites = {}
    for path in walk(SRC, ".kt"):
        rel = os.path.relpath(path, SRC).replace("\\", "/")
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            for lineno, line in enumerate(fh, 1):
                if not DV_K_SKIP.match(line) and DV_K.search(line):
                    dvk_sites["%s:%d" % (rel, lineno)] = line.strip()[:160]
                if COMMENT.match(line):
                    continue
                sites = READ_SITE.findall(line) + OPT_SITE.findall(line)
                if not sites:
                    continue
                keys = []
                for s in sites:
                    for k in KEY_IN_SITE.findall(s):
                        if k not in keys:
                            keys.append(k)
                if not keys:
                    continue
                probe = []
                for k in keys:
                    d = decls.get(k)
                    if d:
                        kinds = {x[0] for x in d}
                        where = "; ".join(f"{x[1]}({x[2]})" for x in d[:2])
                        cls = "A" if kinds == {"primitive"} else (
                            "B" if "wrapper" in kinds or "object" in kinds else "A")
                    else:
                        cls = "?"
                        where = "后端 0 命中"
                    probe.append((k, cls, where, k in mini))
                # 任一候选键可能被后端省略 → 整处读法按最危险的那一支归类
                worst = "B" if any(p[1] == "B" for p in probe) else (
                    "A" if any(p[1] == "A" for p in probe) else "C")
                rows.append({
                    "site": f"{rel}:{lineno}",
                    "text": line.strip()[:160],
                    "keys": probe,
                    "class": worst,
                    "shape": shape_of(sites),
                    "numbool_nonnull": bool(NUMBOOL_NONNULL.search(" ".join(sites))),
                    # 第四面：这一行的候选键里有几个是演示面自己写的
                    "demo_keys": [k for k in keys if k in demo],
                })

    tally = collections.Counter(r["class"] for r in rows)
    shape_tally = collections.Counter((r["class"], r["shape"]) for r in rows)
    c_shape = collections.Counter()
    for r in rows:
        if r["class"] == "C":
            c_shape[r["shape"]] += 1
            if r["shape"] == "numbool" and r["numbool_nonnull"]:
                c_shape["numbool_nonnull"] += 1
    # 三面 0 生产者 = 后端 VO 0 命中 ∧ 小程序 0 读点 ∧ 演示面 0 写键（§一 那句"三面"的逐字口径）
    # 按**键**算集合、按**站点**去重计数：一行里两个键都无生产者只算一处，否则 186 这种数会随键数漂。
    tfz = collections.defaultdict(lambda: {"sites": set(), "nb_sites": set(), "c_sites": set()})
    for r in rows:
        nb = r["shape"] == "numbool" and r["numbool_nonnull"]
        for key_name, _cls, where, in_mini in r["keys"]:
            if where != "后端 0 命中" or in_mini or key_name in demo:
                continue
            e = tfz[key_name]
            e["sites"].add(r["site"])
            if nb:
                e["nb_sites"].add(r["site"])
            if r["class"] == "C":
                e["c_sites"].add(r["site"])
    tfz_sites = {s for v in tfz.values() for s in v["sites"]}
    tfz_nb_sites = {s for v in tfz.values() for s in v["nb_sites"]}
    tfz_c_sites = {s for v in tfz.values() for s in v["c_sites"]}
    c_keys = collections.Counter()
    for r in rows:
        if r["class"] == "C":
            for key_name, _cls, where, in_mini in r["keys"]:
                if where == "后端 0 命中":
                    c_keys[(key_name, in_mini)] += 1
    # DV-k（grep 行）与本脚本（要有字面量键才成行）两个基数之间的差额，逐点列出来
    row_sites = {r["site"] for r in rows}
    only_grep = [{"site": s, "text": dvk_sites[s]} for s in sorted(dvk_sites) if s not in row_sites]
    dvk_bridge = {
        "dvk_grep_lines": len(dvk_sites),
        "census_dvk_shape_rows": sum(1 for r in rows if DV_K.search(r["text"])),
        "delta": len(only_grep),
        "only_in_grep": only_grep,
        "note": "delta 行是 DV-k 的 grep 数到、本脚本没当成读法的行。本轮实测那 2 行是 "
                "`fun JSONObject.multiNum/multiBool` 两个**辅助函数声明本身** ⇒ DV-k 的 115 = 113 处读法 + 2 行声明，"
                "跨轮引用 115 时别再把它读成'115 处读法'。",
    }
    out = {
        "tally": dict(tally),
        "shape_tally": {"%s/%s" % k: v for k, v in sorted(shape_tally.items())},
        "c_shape": dict(c_shape),
        "demo_writers": demo_summary,
        "three_facet_zero": {
            "keys": len(tfz),
            "sites": len(tfz_sites),
            "sites_in_c_rows": len(tfz_c_sites),
            "numeric_bool_nonnull_sites": len(tfz_nb_sites),
            "detail": [{"key": k, "sites": sorted(v["sites"]),
                        "numbool_nonnull_sites": sorted(v["nb_sites"])}
                       for k, v in sorted(tfz.items())],
        },
        "dvk_bridge": dvk_bridge,
        "rows": rows,
        "c_no_producer": [{"key": k, "mini": m, "sites": n} for (k, m), n in sorted(c_keys.items())],
    }
    payload = json.dumps(out, ensure_ascii=False, indent=1)
    if "--write" in sys.argv:
        # 判据簿（check_default_value_probes.sh）要在同一轮里读这份 JSON。
        # ⚠ 走文件而不是走管道：Windows 的 python stdin 默认按 GBK 解码，管道里的中文源码行会被悄悄改字符；
        # ⚠ 路径用 **argv**（--out）传，不用环境变量：MSYS bash 把 env 里的非 ASCII 路径转坏了
        #   （本轮实测 `袁` 变成 `Ԭ` ⇒ open 报 FileNotFoundError，普查看起来像"跑不通"）。
        if "--out" in sys.argv:
            dest = sys.argv[sys.argv.index("--out") + 1]
        else:
            dest = os.environ.get("DV_CENSUS_JSON") or os.path.join(
                os.environ.get("TEMP", "/tmp"), "default_value_census.json")
        with open(dest, "w", encoding="utf-8") as fh:
            fh.write(payload)
        print("WROTE", dest, "rows=", len(rows), "tally=", dict(tally),
              "c_shape=", dict(c_shape), "three_facet_zero=", len(tfz),
              "dvk_bridge=", dvk_bridge["dvk_grep_lines"], "/", dvk_bridge["census_dvk_shape_rows"],
              "/delta", dvk_bridge["delta"])
    else:
        print(payload)


if __name__ == "__main__":
    main()
