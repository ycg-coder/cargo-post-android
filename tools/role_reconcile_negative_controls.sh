#!/usr/bin/env bash
# RR-* 角色同源**探针**的负控制：逐条破口 → 确认 check_role_reconcile_probes.sh exit 1 且报的就是这一条。
# 只在 /tmp 副本上动手，工程真身不受影响（纯文本判据，不需要编译）。
# ⚠ 整目录 copy（不是只拷三个文件）：本轮新增的 WorkbenchRoleReconcileContractTest.kt / RoleReconcileTest.kt
#   如果不在副本里，基线会先红，后面每一条 ✔ 都是假的。
# 用法：bash tools/role_reconcile_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/role_neg.$$
PKG=com/shanxiang/buspost
PR="$SRC/tools/check_role_reconcile_probes.sh"
mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
cp -r "$SRC/tools" "$SC/tools"
[ -f "$SC/app/src/main/java/$PKG/nav/ShellDecision.kt" ] || { echo "✘ 副本没建起来（$SC），本轮负控全部作废"; exit 2; }
[ -f "$SC/app/src/test/java/$PKG/ui/driver/WorkbenchRoleReconcileContractTest.kt" ] || { echo "✘ 副本缺新用例文件，本轮负控全部作废"; exit 2; }
[ -f "$SC/app/src/test/java/$PKG/core/DriverRoleIntegrityTest.kt" ] || { echo "✘ 副本缺 F-3 ③ 的两层分离用例（RR-a3 会假报），本轮负控全部作废"; exit 2; }
A="$SC/app/src/main/java/$PKG"
AT="$SC/app/src/test/java/$PKG"
REPO="$A/data/repo/Repo.kt"
SD="$A/nav/ShellDecision.kt"
WB="$A/ui/driver/WorkbenchScreen.kt"
DS="$A/ui/driver/DriverMineScreen.kt"
MS="$A/ui/user/MineScreen.kt"
AE="$A/notify/AlertEngine.kt"
T1="$AT/nav/RoleReconcileTest.kt"
T2="$AT/ui/driver/WorkbenchRoleReconcileContractTest.kt"
T3="$AT/core/DriverRoleIntegrityTest.kt"
CLEAN=/tmp/role_neg_clean.$$; cp -r "$SC" "$CLEAN"
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
s = open(p, encoding='utf-8').read()
exec(expr)
open(p, 'w', encoding='utf-8').write(s)
PY
  [ "$?" = 0 ] || { echo "✘ 锚点没命中，破口根本没注入（$2）"; exit 2; }
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | tail -1)"
# 基线不绿就立刻收工：否则下面每条都会因为"锚点本来就不在"打出 ✔，整轮负控是假的
[ "$base_code" = 0 ] || { echo "✘ 基线未通过，负控结果一律不采信"; exit 2; }

# RR-a 用例被注释掉一条（考行首锚定）
red "$T1" "
i = s.find('\n    @Test')
assert i >= 0, 'ANCHOR-MISS'
s = s[:i] + '\n    //@Test' + s[i+10:]
"
ctl RR-a "三态纯函数用例被注释掉一条"

# RR-a 反向那一半：行为用例少一条（请求条数判据被删=早退没人管了）
red "$T2" "
i = s.rfind('\n    @Test')
assert i >= 0, 'ANCHOR-MISS'
s = s[:i] + '\n    //@Test' + s[i+10:]
"
ctl RR-a "工作台行为用例被注释掉一条"

# RR-b 旧判据回填：`data 非空` 一加回去，后端唯一的否定形状就被排除（撤档永不生效）
red "$REPO" "
n = 'else if (!p.demo) Prefs.clearDriverBinding()'
assert s.count(n) >= 1, 'ANCHOR-MISS'
s = s.replace(n, 'else if (!p.demo && o.length() > 0) Prefs.clearDriverBinding()', 1)
"
ctl RR-b "driverProfile 又要求 data 非空"

# RR-b2 bindDriver 同一端点第二处判据回退
red "$REPO" "
n = 'if (!p.demo) Prefs.clearDriverBinding()'
assert s.count(n) >= 1, 'ANCHOR-MISS'
s = s.replace(n, 'if (!p.demo && o.length() > 0) Prefs.clearDriverBinding()', 1)
"
ctl RR-b "bindDriver 的否定判据回退"

# RR-b3 否定条件被改成"演示才算"
red "$REPO" "
n = 'else if (!p.demo) Prefs.clearDriverBinding()'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, 'else if (p.demo) Prefs.clearDriverBinding()', 1)
"
ctl RR-b3 "'非演示即明确否定'那行被改掉"

# RR-c 早退被搬到四条请求之后：先补一条更靠前的 driverShifts，再删掉原来那条
red "$WB" "
n = '        val p = Repo.driverProfile()'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, n + '\n        val s = Repo.driverShifts()', 1)
m = '        val s = Repo.driverShifts()\n        val k = Repo.driverPickups()'
assert m in s, 'ANCHOR-MISS-2'
s = s.replace(m, '        val k = Repo.driverPickups()', 1)
"
ctl RR-c "早退不再先于 driverShifts"

# RR-c 否定分支里的 return 被摘掉（清完状态还继续往下走）
red "$WB" "
n = '            error = DRIVER_UNBOUND\n            return'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '            error = DRIVER_UNBOUND', 1)
"
ctl RR-c "否定分支没有紧跟的 return"

# RR-c 调用点变成两处（同一件事两份答案）
red "$WB" "
n = '        val stored = Prefs.role'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '        reconcileRole(Prefs.role, server)\n' + n, 1)
"
ctl RR-c "reconcileRole 出现第二个调用点"

# RR-d 页面不再观测 demoted（角色降了但人还留在司机壳）
red "$WB" "
n = '    LaunchedEffect(vm.demoted) {'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '    // NEG-CONTROL 摘掉：' + n.strip(), 1)
"
ctl RR-d "demoted 没人观测"

# RR-d 降级块里少了"停位置上报"（档案没了还在替旧班次报点位）
red "$WB" "
i = s.find('LaunchedEffect(vm.demoted)')
assert i >= 0, 'ANCHOR-MISS'
j = s.index('DriverLocationService.stop(context)', i)
s = s[:j] + '// NEG-CONTROL 摘掉停上报' + s[j + len('DriverLocationService.stop(context)'):]
"
ctl RR-d "降级块里没停位置上报"

# RR-d 降级块里少了导航（只改角色不出壳）
red "$WB" "
i = s.find('LaunchedEffect(vm.demoted)')
assert i >= 0, 'ANCHOR-MISS'
n = 'nav.navigate(Routes.USER_SHELL) { popUpTo(Routes.DRIVER_SHELL) { inclusive = true } }'
j = s.index(n, i)
s = s[:j] + '// NEG-CONTROL 摘掉导航' + s[j + len(n):]
"
ctl RR-d "降级块里没有回用户壳的导航"

# RR-e 页面开始自己写角色（绕过 AppState 镜像，两处答案会打架）
red "$MS" "
n = '@Composable'
assert n in s, 'ANCHOR-MISS'
i = s.index(n)
s = s[:i] + 'private fun negControlWriteRole(v: com.shanxiang.buspost.data.model.Role) { Prefs.role = v }\n\n' + s[i:]
"
ctl RR-e "页面直接写 Prefs.role"

# RR-e 改角色的入口多了一处（进司机域变成自动的，硬规则 1）
red "$MS" "
n = 'AppState.setAccountRole(Role.DRIVER)'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, n + '\n        AppState.setAccountRole(Role.DRIVER)', 1)
"
ctl RR-e "setAccountRole 入口不再是两处"

# RR-e2 司机壳又开始写账号角色（F-3 ③ 拆开的两层糊回去：切"用户版"顺手 setAccountRole）。
# 2026-10-10 起锚点换成 DriverMineScreen 切换菜单行里的 setActiveDomain —— 注入后必须在
# check_role_reconcile_probes.sh 的 RR-e2（司机壳角色写入 = 0）上变红，锚点丢失要报 ANCHOR-MISS。
red "$DS" "
n = 'AppState.setActiveDomain(AppDomain.USER)'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, n + '\n        AppState.setAccountRole(Role.USER)', 1)
"
ctl RR-e2 "司机壳里出现角色写入"

# RR-f 后台提醒回到按账号角色分域（切壳就停不下司机域轮询，只能再改角色）
red "$AE" "
n = 'AppState.activeDomain.value == AppDomain.DRIVER'
assert s.count(n) == 2, 'ANCHOR-MISS'
s = s.replace(n, 'Prefs.role == Role.DRIVER')
"
ctl RR-f "提醒不再按在看哪个域分域"

# RR-a3 两层分离的完整性用例被注释掉一条
red "$T3" "
i = s.find('\n    @Test')
assert i >= 0, 'ANCHOR-MISS'
s = s[:i] + '\n    //@Test' + s[i+10:]
"
ctl RR-a "角色/视图两层分离的用例被注释掉一条"

# SR 家族的既有用例数没被动过 —— 反向确认本轮改动没把壳路由判据挤掉
echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
