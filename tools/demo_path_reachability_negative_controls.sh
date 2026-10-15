#!/usr/bin/env bash
# 演示面路径可达性判据（DP-a…DP-d）的负控制：逐条破口 → 确认 probe exit 1 且报的就是这一条。
# 只在临时目录副本上动手，工程真身不受影响（纯文本判据，不需要编译 ⇒ 可与其他脚本并行）。
# 用法：bash tools/demo_path_reachability_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3

PKG=com/shanxiang/buspost
PR="$SRC/tools/check_demo_path_reachability_probes.sh"
SC=/tmp/dp_neg.$$
mkdir -p "$SC/app/src/main" "$SC/tools/fixtures"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/tools" "$SC/tools" 2>/dev/null
cp "$SRC/tools/fixtures/demo_path_exempt.txt" "$SC/tools/fixtures/demo_path_exempt.txt"
[ -f "$PR" ] || { echo "✘ 判据脚本不在 $PR，本轮负控全部作废"; exit 2; }

DM="$SC/app/src/main/java/$PKG/data/repo/DemoData.kt"
RP="$SC/app/src/main/java/$PKG/data/repo/Repo.kt"
EXF="$SC/tools/fixtures/demo_path_exempt.txt"
for f in "$DM" "$RP" "$EXF"; do
    [ -f "$f" ] || { echo "✘ 副本缺 $f，下面每一条都会假绿"; exit 2; }
done
CLEAN=/tmp/dp_neg_clean.$$; cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

# 一次性破口：把文件里某串换掉（要求原串至少出现 1 次，否则视为"注入根本没落"直接收工）
subst() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ 破口锚点没命中（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
open(p, 'w', encoding='utf-8').write(s.replace(old, new))
PY
}
add_branch() { python - "$1" "$2" <<'PY' || { echo "✘ 破口插入点没命中（这条负控不成立）"; exit 2; }
import sys
p, block = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8').read()
anchor = '            path.endsWith("/notification/page")'
assert anchor in s, "ANCHOR-MISS: notification/page"
open(p, 'w', encoding='utf-8').write(s.replace(anchor, block + '\n' + anchor, 1))
PY
}
ctl() { # $1=判据编号 $2=说明
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" = 1 ] && echo "$out" | grep -q "^× $1"; then
    echo "✔ $1  $2 → exit 1：$(echo "$out" | grep "^× $1" | head -1)"
  elif [ "$code" = 1 ]; then
    echo "△ $1  $2 → exit 1，但抓到的是别的判据：$(echo "$out" | grep '^×' | head -1)"
  else
    echo "✘ $1  $2 → 负控制失效（exit $code），这条判据等于没判据"
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | tail -1)"
[ "$base_code" = 0 ] || { echo "✘ 基线未通过，负控结果一律不采信"; exit 2; }

# DP-1 死演示分支回来了（本轮真修的那条）→ DP-c
add_branch "$DM" '            path.endsWith("/bus/realtime") -> a(j("routeId" to "K1"))'
ctl DP-c "把没人请求的 /bus/realtime 演示分支加回来"

# DP-2 在册豁免被抹掉（欠账不该消失，要么修好要么留名）→ DP-c
# ⚠ 破口串不能以 `/` 开头当参数传：Git Bash 会把 `/get-…` 这种 argv 当路径换算成 `C:/Program Files/Git/…`
#   （2026-10-15 实踩），所以这里锚在不带前导斜杠的那半截上，替换结果同样把在册条目作废。
subst "$EXF" "get-subscribe-template-list" "#get-subscribe-template-list"
ctl DP-c "在册条目从豁免表里被删掉"

# DP-3 豁免表里塞一条根本不存在的孤儿（在册条目已转实的反向验证）→ DP-c
subst "$EXF" "get-subscribe-template-list" "get-subscribe-template-list
/this/path/has/no/demo/branch"
ctl DP-c "豁免表里多出一条演示面压根没应答的路径"

# DP-4 前缀展开不上岗：把 const M 改歪，四条靠展开才配上的分支全成孤儿 → DP-d（DP-c 同红）
subst "$RP" 'const val M = "/app-api/member"' 'const val M = "/app-api/memberX"'
ctl DP-d 'Repo 的前缀常量被改歪：$M/$T 展开一坏，DP-c 的 0 就是假的'

# DP-5 演示面扫描面塌了（endsWith 整体改名）→ DP-a 的地板必须拦住假 0
subst "$DM" 'path.endsWith(' 'path.endsWidth('
ctl DP-a "DemoData 里 endsWith 写法整体被改：分支数掉下来，孤儿数会假成 0"

# DP-6 请求侧扫描面塌了（两条前缀常量的引用整体改名，字面量一条都认不到）→ DP-b
# ⚠ 判据现在认的是"含 $M/$T 或 /app-api/ 开头的整串字面量"（不再按 `Api.get(` 认），
#   所以破口必须打在常量引用上：第一版打在 `Api.get(` 上，改名后计数纹丝不动 ⇒ 空转负控。
subst "$RP" '$M/' '$PfxM/'
subst "$RP" '$T/' '$PfxT/'
ctl DP-b "Repo 里 \$M/\$T 的引用整体改名：对侧底盘空了，孤儿同样会假成 0"

echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
