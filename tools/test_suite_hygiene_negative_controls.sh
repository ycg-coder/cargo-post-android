#!/usr/bin/env bash
# SU-* 测试套件卫生判据的负控制（F-3 ⑱⑳ / 队列 #151）：逐条破口 → 确认
# `check_test_suite_hygiene_probes.sh` exit 1 且报的就是这一条。
# 只在 /tmp 副本上动手，工程真身不受影响（纯文本判据，不需要编译）。
#
# ⚠ 整目录 copy（不是只拷几个文件）：本册的破口会**新建/改名测试源集里的文件**，
#   副本缺任何一本落点类，基线就会先红，后面每条 ✔ 都是假的。
# ⚠ 每条注入都先确认锚点命中（`red()` 里 ANCHOR-MISS 直接 exit 2），防"空跑注入"。
# ⚠ 取退出码一律 `out=$(...); rc=$?`，不许用管道（管道拿的是 tail 的 0，这条踩过两次）。
#
# 7 条破口对应 5 条判据：
#   P1 新建一本 0 条 @Test 的空壳 *Test.kt        → SU-a（凑数空类）
#   P2 给夹具 TestSupport.kt 加一条 @Test          → SU-b（反证掉了，SU-a 的 0 不可信）
#   P3 把一条真用例挂上 @Ignore                    → SU-c（永远绿的那种假绿）
#   P4 新建点名却"不该建"的 NavigationReadinessTest → SU-d（为凑数建第 8 本）
#   P5 把同名存在的 DriverRoleIntegrityTest 改名    → SU-d（把已有类改名冒充/撤走）
#   P6 删掉一本归位表落点类 MissionMapTest.kt       → SU-e（表念空）
#   P7 落点类改名（PolylineDecisionContractTest）   → SU-e（改名冒充的那一侧）
# 用法：bash tools/test_suite_hygiene_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/su_neg.$$
PKG=com/shanxiang/buspost
PR="$SRC/tools/check_test_suite_hygiene_probes.sh"
mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
cp -r "$SRC/tools" "$SC/tools"
AT="$SC/app/src/test/java/$PKG"
[ -d "$AT" ] || { echo "✘ 副本没建起来（$SC），本轮负控全部作废"; exit 2; }
[ -f "$AT/ui/driver/MissionMapTest.kt" ] || { echo "✘ 副本缺落点类，本轮负控全部作废"; exit 2; }
CLEAN=/tmp/su_neg_clean.$$; cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

ctl() { # $1=编号 $2=说明
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

red() { # $1=文件 $2=python 表达式（锚点不在必须报错，不许空跑）
  python - "$1" "$2" <<'PY'
import sys
p, expr = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8', newline='').read()
exec(expr)
open(p, 'w', encoding='utf-8', newline='').write(s)
PY
  [ "$?" = 0 ] || { echo "✘ 锚点没命中，破口根本没注入（$2）"; exit 2; }
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(printf '%s' "$BASE" | tail -1)"
# 基线不绿就立刻收工：否则下面每条都会因为"锚点本来就不在"打出 ✔，整轮负控是假的
[ "$base_code" = 0 ] || { echo "✘ 基线未通过，负控结果一律不采信"; exit 2; }

# ---------- P1 空壳 *Test.kt ----------
cat > "$AT/ui/driver/NegPadContractTest.kt" <<'EOF'
package com.shanxiang.buspost.ui.driver

// 负控制注入：一本一条 @Test 都没有的"点名类"，专门用来证明 SU-a 有判别力。
class NegPadContractTest
EOF
[ -f "$AT/ui/driver/NegPadContractTest.kt" ] || { echo "✘ P1 破口没写进去"; exit 2; }
ctl SU-a "新建一本 0 条 @Test 的空壳（为凑数建类）"

# ---------- P2 反证掉 ----------
red "$AT/TestSupport.kt" "
i = s.find('\nclass ')
assert i >= 0, 'ANCHOR-MISS'
s = s[:i] + '\n// NEG-CONTROL 给夹具补一条 @Test：' + '\n    @Test\n' + s[i:]
"
ctl SU-b "无 @Test 的夹具数不再等于 2（反证掉了 ⇒ SU-a 的 0 不可信）"

# ---------- P3 @Ignore ----------
red "$AT/ui/driver/MissionMapTest.kt" "
n = '    @Test\n'
assert s.count(n) >= 1, 'ANCHOR-MISS'
s = s.replace(n, '    @Ignore\n' + n, 1)
"
ctl SU-c "把一条真用例挂起来（从此永远绿）"

# ---------- P4 建了第 8 本点名的类 ----------
cat > "$AT/core/navigation/NavigationReadinessTest.kt" <<'EOF'
package com.shanxiang.buspost.core.navigation

// 负控制注入：⑱⑳ 点名但本轮判定"不该新建"的那一本，建出来就要被 SU-d 抓到。
class NavigationReadinessTest {
    fun placeholder() = 1
}
EOF
[ -f "$AT/core/navigation/NavigationReadinessTest.kt" ] || { echo "✘ P4 破口没写进去"; exit 2; }
ctl SU-d "点名为凑数新建了一本类（归位表第四列被推翻）"

# ---------- P5 同名类改名 ----------
mv "$AT/core/DriverRoleIntegrityTest.kt" "$AT/core/DriverRoleIntegrityRenamedTest.kt" 2>/dev/null
[ -f "$AT/core/DriverRoleIntegrityTest.kt" ] || true
ctl SU-d "把同名存在的那本改名（冒充与撤走是同一件事的两面）"

# ---------- P6 删落点类 ----------
rm -f "$AT/ui/driver/MissionMapTest.kt"
ctl SU-e "删掉一本归位表落点类"

# ---------- P7 落点类改名 ----------
mv "$AT/core/map/PolylineDecisionContractTest.kt" "$AT/core/map/RoutePageStateTest.kt" 2>/dev/null
ctl SU-e "把落点类改名成点名类（改名冒充新增的那一侧）"

echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1 | tail -1)"
