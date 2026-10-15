#!/usr/bin/env bash
# 演示面路径可达性判据（缺陷 #46 的收口，配 tools/demo_path_reachability_negative_controls.sh）
#
# 管的是一个问题：`DemoData` 里每条 `path.endsWith("…") -> …` 都宣称"这条接口有演示数据"，
# 但**没人请求它**的那条就是死分支 —— 后来人（和我自己的台账）会把它读成"这条通路接好了"。
# 本轮抓到的正是 `/bus/realtime`：`AI_ITERATION_STATE.md` 记了它"全工程没有任何调用点"，
# 而这条规矩此前**只活在那句注释里**（判据空转根因清单里"规矩只活在注释里"那一条），所以立成闸。
#
# 口径（三件，缺一件就是假绿）：
#   1. 请求点不只看 `Repo.kt` 里的整串：路径写成 `"$T/send/page"` / `"$M/user/get"` 两个前缀常量，
#      必须按 `Repo.kt` 里 const 的真实值展开后按后缀配对，否则 `/member/user/get` 这类会被误报成孤儿
#      （第一版就是这么误报了 5 条，逐条 grep 复核后改成按前缀展开）。
#   2. "孤儿 = 0"必须有反证：演示面分支数与请求字面量数都要数得出非 0 且各自有下限，
#      并且至少一条分支必须是"靠前缀展开才配上"的 —— 扫描面一坏就红，而不是报出一个假的 0。
#   3. 有理由留下的孤儿进 `tools/fixtures/demo_path_exempt.txt` 点名（与 doc_anchor 的在册表同一规矩：
#      要么修好，要么在册写清为什么），在册条目若已经不再是孤儿也要红（欠账不许悄悄转正）。
#
# 用法：bash tools/check_demo_path_reachability_probes.sh（可用 BUSPOST_ANDROID 覆盖工程根）
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2

DM=app/src/main/java/com/shanxiang/buspost/data/repo/DemoData.kt
RP=app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt
EX=tools/fixtures/demo_path_exempt.txt
for f in "$DM" "$RP" "$EX"; do
    [ -f "$f" ] || { echo "× 判据输入缺失：$f（扫描面塌了，本轮所有数不采信）"; exit 2; }
done

DP_OUT=$(python - "$DM" "$RP" "$EX" <<'PY'
import io, re, sys
dm_p, rp_p, ex_p = sys.argv[1], sys.argv[2], sys.argv[3]
dm = io.open(dm_p, encoding='utf-8').read()
rp = io.open(rp_p, encoding='utf-8').read()

# 前缀常量按 Repo 的真实值展开（$M / $T / ${M} / ${T}）
prefix = {}
for name in ("M", "T"):
    m = re.search(r'const val %s = "([^"]+)"' % name, rp)
    prefix[name] = m.group(1) if m else ""

demo = sorted(set(re.findall(r'path\.endsWith\("(/[^"]+)"\)', dm)))
# 请求点：Repo 是**唯一**发请求的文件（全工程 `Api.get/post/put/delete` 只有它，本轮实测）。
# 取"整条带前缀常量的字面量"而不是只认 `Api.get("…")`：`val path = if (editing) "$M/address/update" else …`
# 这种 if/else 写法第一版没认到，于是 /address/create 被削尾匹配错认成 /send/create —— 认漏要红，认错也要红。
raw_literals = re.findall(r'"((?:[^"\\]|\\.)*)"', rp)
lits = set(x for x in raw_literals if '$M' in x or '$T' in x or '${M}' in x or '${T}' in x or x.startswith('/app-api/'))

def expand(raw):
    s = re.sub(r'\$\{?(M|T)\}?', lambda m: prefix[m.group(1)], raw)
    # 去掉动态段（"$T/driver/leg/" 这种把变量拼在后面的），只留静态部分
    s = re.sub(r'\$\{?[A-Za-z_][A-Za-z0-9_]*', '', s)
    s = re.sub(r'/+', '/', s).rstrip('/')
    return s

expanded = [expand(x) for x in lits]

# 配对按 DemoData 自己的语义来：`path.endsWith(X)` 里 X 是**完整请求路径的后缀**（含 /app-api 之后的模块段），
# 所以这里必须用 endswith(整条演示路径) 比，不许先把演示路径削掉一段。
# （第一版削了首段，`/member/user/get` 变成拿 "user/get" 去配 ⇒ 把 const M 改歪都照样"配得上"，
#   负控 DP-4 就是这么抓到判据自己空转的：反证那一格数得出的 4 是假的。）
def reached(d):
    return any(e.endswith(d) for e in expanded)

direct = set(d for d in demo if any(d.lstrip('/') in x for x in lits))
with_prefix = set(d for d in demo if reached(d) and d not in direct)

exempt = set()
for line in io.open(ex_p, encoding='utf-8'):
    line = line.strip()
    if line and not line.startswith('#'):
        exempt.add(line.split()[0])

orphans = [d for d in demo if not reached(d)]
unexplained = [d for d in orphans if d not in exempt]
stale_exempt = [d for d in sorted(exempt) if d not in orphans]

print(len(demo), len(lits), len(unexplained) + len(stale_exempt), len(with_prefix))
for d in unexplained:
    print("  无解释孤儿（演示面在应答、请求侧没人发）: " + d)
for d in stale_exempt:
    print("  在册条目已转实（要么删掉这行豁免，要么它本来就配上了）: " + d)
PY
)
DP_CODE=$?
[ "$DP_CODE" = 0 ] || { echo "× 扫描跑不动（python rc=$DP_CODE），本轮读数不采信"; exit 2; }

read -r DP_BRANCH DP_REQ DP_ORPHAN DP_RESOLVED <<<"$(printf '%s\n' "$DP_OUT" | head -1)"
DP_DETAIL=$(printf '%s\n' "$DP_OUT" | tail -n +2)
DPE=$(grep -vcE '^\s*(#|$)' "$EX")

echo "DP-a 演示面分支数（扫描面底盘）   = $DP_BRANCH （目标 ≥45）"
echo "DP-b 请求字面量数（对侧底盘）     = $DP_REQ （目标 ≥55）"
echo "DP-c 无解释孤儿 + 在册已转实      = $DP_ORPHAN （目标 0；在册表 $DPE 行）"
echo "DP-d 靠前缀展开才配上的分支数     = $DP_RESOLVED （目标 ≥1：展开一坏 DP-c 就是假的）"
[ "$DP_ORPHAN" = 0 ] || printf '%s\n' "$DP_DETAIL"

fail=0
[ "$DP_BRANCH" -ge 45 ] || { echo "× DP-a 演示面分支少于 45（现 $DP_BRANCH）：endsWith 写法被改或扫描面没了，下面的 0 不可信"; fail=1; }
[ "$DP_REQ" -ge 55 ] || { echo "× DP-b 请求字面量少于 55（现 $DP_REQ）：请求侧扫描面塌了，孤儿会被算成 0"; fail=1; }
[ "$DP_RESOLVED" -ge 1 ] || { echo "× DP-d 反证掉了：没有任何分支是靠 \$M/\$T 前缀展开才配上的（现 $DP_RESOLVED），说明展开这步没上岗"; fail=1; }
[ "$DP_ORPHAN" = 0 ] || { echo "× DP-c 有 $DP_ORPHAN 条演示应答没人请求（或在册条目已转实）：死演示分支会被人读成接好的通路（#46）"; fail=1; }

if [ "$fail" = 0 ]; then echo "演示面路径可达性判据通过"; else echo "演示面路径可达性判据失败"; fi
exit "$fail"
