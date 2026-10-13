#!/usr/bin/env bash
# SR-* 壳路由**探针**的负控制：逐条破口 → 确认 check_shell_routing_probes.sh exit 1 且报的就是这一条。
# 只在 /tmp 副本上动手，工程真身不受影响（纯文本判据，不需要编译）。
# 用法：bash tools/shell_routing_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/shell_neg.$$
PKG=com/shanxiang/buspost
PR="$SRC/tools/check_shell_routing_probes.sh"
mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
[ -f "$SC/app/src/main/java/$PKG/ui/BusPostRoot.kt" ] || { echo "✘ 副本没建起来（$SC），本轮负控全部作废"; exit 2; }
A="$SC/app/src/main/java/$PKG"
RT="$A/ui/BusPostRoot.kt"
SD="$A/nav/ShellDecision.kt"
RS="$A/nav/Routes.kt"
DS="$A/ui/driver/DriverMineScreen.kt"
MS="$A/ui/user/MineScreen.kt"
WB="$A/ui/driver/WorkbenchScreen.kt"
TT="$SC/app/src/test/java/$PKG/nav/ShellRoutingContractTest.kt"
# F-3 ⑥ 新增（SR-i … SR-n 的注入点）
MA="$A/MainActivity.kt"
NF="$A/notify/Notifier.kt"
AE="$A/notify/AlertEngine.kt"
WG="$A/widget/NextBusWidgetProvider.kt"
BS="$A/ui/user/BusScreen.kt"
BSD="$A/ui/user/BusDetailSlot.kt"
DMS="$A/ui/driver/DriverMessagesScreen.kt"
TT2="$SC/app/src/test/java/$PKG/nav/RouteDomainDeepLinkTest.kt"
for f in "$MA" "$NF" "$AE" "$WG" "$BS" "$BSD" "$DMS" "$TT2"; do
    [ -f "$f" ] || { echo "✘ 沙盒副本缺 $f，本轮负控全部作废"; exit 2; }
done
CLEAN=/tmp/shell_neg_clean.$$; cp -r "$SC" "$CLEAN"
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
after() { python - "$1" "$2" "$3" <<'PY'
import sys
p, anchor, add = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read(); i = s.find(anchor)
if i < 0: sys.exit("ANCHOR-MISS: " + anchor)
j = s.index('\n', i) + 1
open(p, 'w', encoding='utf-8').write(s[:j] + add + '\n' + s[j:])
PY
}
drop() { python - "$1" "$2" <<'PY'
import sys
p, needle = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8').read()
if needle not in s: sys.exit("ANCHOR-MISS: " + needle)
open(p, 'w', encoding='utf-8').write(s.replace(needle, '// NEG-CONTROL 摘掉：' + needle.strip(), 1))
PY
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | tail -1)"
# 基线不绿就立刻收工：否则下面每一条都会因为"文件本来就不在"而打出 ✔，整轮负控是假的
[ "$base_code" = 0 ] || { echo "✘ 基线未通过，负控结果一律不采信"; exit 2; }

# SR-a 用例被注释掉但 @Test 字样还在（考行首锚定）
python - "$TT" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
ctl SR-a "壳路由用例被注释掉一条"

# SR-b 落点判断退回 UI 里的内联 when（纯函数没人调，用例就成了装饰）
python - "$RT" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
n = '    val start = startDestinationFor(Prefs.loggedIn, Prefs.role)'
assert n in s, "ANCHOR-MISS"
open(p, 'w', encoding='utf-8').write(s.replace(n, '    val start = when {\n        !Prefs.loggedIn -> Routes.LOGIN\n        else -> Routes.USER_SHELL\n    }', 1))
PY
ctl SR-b "UI 里又出现第二份落点 when"

# SR-c 分域闸被绕开：深链直接 navigate
drop "$RT" 'deepLinkTarget(route, Prefs.role)?.let { attempt { nav.navigate(it) } }'
ctl SR-c "深链不再过闸门"

# SR-c2 旧闸门复活（两份实现会打架）
after "$RT" "private var splashShownOnce = false" 'private fun deepLinkAllowed(route: String): Boolean = route !in Routes.DRIVER_ONLY || Prefs.role == Role.DRIVER'
ctl SR-c2 "旧私有 deepLinkAllowed 还在"

# SR-d 决策文件被 Compose 依赖污染（重新变得 JVM 测不了）
after "$SD" "import com.shanxiang.buspost.data.model.Role" 'import androidx.compose.runtime.Composable'
ctl SR-d "决策文件被 Compose 依赖污染"

# SR-d 两个决策函数少了一个（整函数删掉：函数计数与调用计数应当同时掉）
python - "$SD" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('/**\n * 冷启动的起点')
j = s.find('/**', i + 10)
assert i >= 0 and j > i, "ANCHOR-MISS"
open(p, 'w', encoding='utf-8').write(s[:i] + s[j:])
PY
ctl SR-d "startDestinationFor 整个函数被删"

# SR-e 用户侧页面开始引用司机域路由
after "$MS" "nav.navigate(Routes.ORDERS)" '                    nav.navigate(Routes.HANDOVER)'
ctl SR-e "用户页面直接引用司机域路由"

# SR-f 司机侧页面开始引用用户域业务页
# E-1 注：#51 把这一行改成了带 BuildConfig.DEBUG 闸门的形态，旧锚点当场变成空操作 ⇒ SR-f 负控制静默失效
#     （E-1 复跑全套结构负控时被抓到：13 条里 ✘ 1 条）。跨簿锚点也必须随代码同轮核 —— mtime 与"范围不相交"都不算证据。
# 2026-10-10 **同一条坑第二次复现**：本轮开工跑全批时 SR-f 又 ✘ —— 那句 `onNav = { if (BuildConfig.DEBUG) … },`
#   连同 `onParcel` 在 10-09 19:37 那次工作台重排里**整行没了**（现读 `grep -an 'onNav = \|onParcel' WorkbenchScreen.kt`
#   = 0 命中）⇒ `after` ANCHOR-MISS 空跑、判据照样绿，等于没注。
#   改法：**不再拿业务行当锚**，改用文件里必然存在的 `package` 行 —— 锚点越"语义巧合"越容易随重构蒸发。
after "$WB" 'package com.shanxiang.buspost.ui.driver' 'private val negSrF = Routes.PARCEL'
ctl SR-f "司机页面引用用户域业务页"

# SR-f2 司机壳出口不再切视图域（人在用户端、后台还在轮询司机域接口）。
# 2026-10-10 起出口从 DriverShell 底部第 4 格移入 DriverMineScreen 的「切换为用户版」菜单行。
# F-3 ③ 之前这一行的锚点是 `AppState.setRole(Role.USER)`；出口改成只写 activeDomain 后必须同轮换锚，
# 否则 drop 变成空操作、这条负控制静默失效（同一个坑见上面 E-1 注）。
drop "$DS" 'AppState.setActiveDomain(AppDomain.USER)'
ctl SR-f2 "用户版出口不再切视图域"

# SR-g 未登录也接受深链
drop "$RT" 'if (!Prefs.loggedIn) return@LaunchedEffect'
ctl SR-g "未登录也消费通知深链"

# SR-h 司机域集合被改动（多一条就把用户正常入口锁死）
python - "$RS" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
n = 'val DRIVER_ONLY = setOf(DRIVER_NAV, DRIVER_GUIDE, DRIVER_MESSAGES, HANDOVER, DRIVER_EXCEPTION)'
assert n in s, "ANCHOR-MISS"
open(p, 'w', encoding='utf-8').write(s.replace(n, 'val DRIVER_ONLY = setOf(DRIVER_NAV, DRIVER_MESSAGES, HANDOVER, DRIVER_EXCEPTION, SETTINGS)', 1))
PY
ctl SR-h "DRIVER_ONLY 不再是现行五条"

# SR-h 反向那一半：§54 那条被从集合里摘掉 = 用户会话能靠深链进异常上报页
python - "$RS" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
n = 'val DRIVER_ONLY = setOf(DRIVER_NAV, DRIVER_GUIDE, DRIVER_MESSAGES, HANDOVER, DRIVER_EXCEPTION)'
assert n in s, "ANCHOR-MISS"
open(p, 'w', encoding='utf-8').write(s.replace(n, 'val DRIVER_ONLY = setOf(DRIVER_NAV, DRIVER_MESSAGES, HANDOVER)', 1))
PY
ctl SR-h "DRIVER_EXCEPTION 被摘出司机域（漏一条=开闸）"

# SR-g 追加一条：通知直接投壳路由（进壳只应由角色决定）
after "$A/notify/AlertEngine.kt" "route = Routes.DRIVER_MESSAGES," 'route = Routes.DRIVER_SHELL,'
ctl SR-g "通知把会话直接投进某个壳"

# ── F-3 ⑥（SR-i … SR-n）──────────────────────────────────────────────────────
# SR-i 前缀常量整个被删（两份口径的分家就是从这一步开始的）
drop "$RS" 'const val DEEP_LINK_PREFIX = "buspost://app"'
ctl SR-i "深链前缀常量被删"

# SR-i4 拼装体改成手写的第二个口径：deepLink() 还在，但它不再是前缀的唯一来源
python - "$RS" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
n = 'fun deepLink(route: String) = "$DEEP_LINK_PREFIX/$route"'
assert n in s, "ANCHOR-MISS"
open(p, 'w', encoding='utf-8').write(s.replace(n, 'fun deepLink(route: String) = "buspost://app/$route"', 1))
PY
ctl SR-i4 "deepLink 的拼装体不再引用前缀常量"

# SR-i2 别处再手写一遍前缀（本轮修掉的那类坏：拼的一方与剥的一方口径分家）
after "$NF" 'if (route != null) data = Uri.parse(Routes.deepLink(route))' '            val legacyPrefix = "buspost://app/" + route'
ctl SR-i2 "别处又手写了一遍 buspost:// 前缀"

# SR-i3 MainActivity 退回自己剥前缀
python - "$MA" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
n = 'Routes.routeFromDeepLink(intent?.dataString)?.let { AppState.pendingRoute.value = it }'
assert n in s, "ANCHOR-MISS"
open(p, 'w', encoding='utf-8').write(s.replace(n, 'intent?.dataString?.let { AppState.pendingRoute.value = it }', 1))
PY
ctl SR-i3 "MainActivity 不再经 Routes.routeFromDeepLink"

# SR-j Routes.kt 重新依赖 android.net.Uri：编码在 JVM 用例里当场不可观测
after "$RS" 'package com.shanxiang.buspost.nav' 'import android.net.Uri'
ctl SR-j "Routes.kt 又 import 了 android.net.Uri"

# SR-k trace 的 builder 退回不编码的手拼模板
python - "$RS" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
n = 'fun trace(no: String) = "trace/" + encodeSegment(no)'
assert n in s, "ANCHOR-MISS"
open(p, 'w', encoding='utf-8').write(s.replace(n, 'fun trace(no: String) = "trace/$no"', 1))
PY
ctl SR-k "路径段退回不编码的手拼模板"

# SR-k3 AlertEngine 的到货提醒路由又变回手拼（方法引用那一处被摘掉）
drop "$AE" 'route = it.orderNo.takeIf { no -> no.isNotEmpty() }?.let(Routes::trace),'
ctl SR-k3 "AlertEngine 不再用 Routes::trace 拼路由"

# SR-l3 通知点击不拼深链了（点了没反应的那一类坏）
drop "$NF" 'if (route != null) data = Uri.parse(Routes.deepLink(route))'
ctl SR-l3 "通知点击的深链拼装被摘掉"

# SR-l4 桌面小组件退回"直达实时公交页"的假话
drop "$WG" 'data = Uri.parse(Routes.deepLink(Routes.BUS))'
ctl SR-l4 "小组件不再投实时公交页"

# SR-l2 旧 putExtra("route") 换个写法回来
after "$WG" 'data = Uri.parse(Routes.deepLink(Routes.BUS))' '            intent.putExtra("route", "bus")'
ctl SR-l2 "putExtra(\"route\") 回来了"

# SR-m2 四态判断文件被 Compose 依赖拖回去（重新变得 JVM 测不了）
after "$BSD" 'import com.shanxiang.buspost.data.model.BusLine' 'import androidx.compose.runtime.Composable'
ctl SR-m2 "BusDetailSlot 被 Compose 依赖污染"

# SR-m3 UI 不读纯函数的结论，四态又并成一句
python - "$BS" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
n = 'when (vm.detailSlot()) {'
assert n in s, "ANCHOR-MISS"
open(p, 'w', encoding='utf-8').write(s.replace(n, 'when (vm.loading) {', 1))
PY
ctl SR-m3 "详情页空态不再读 vm.detailSlot()"

# SR-m4/m5 选线又落到列表落地之前（D-⑤-1 的原始缺陷形状）
python - "$BS" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
n = 'LaunchedEffect(routeId) { vm.openDetail(context, routeId) }'
assert n in s, "ANCHOR-MISS"
open(p, 'w', encoding='utf-8').write(s.replace(n, 'LaunchedEffect(routeId) { vm.load(context) }', 1))
PY
ctl SR-m4 "详情页不再经 openDetail 进入"

after "$BS" 'if (selected?.id != routeId) load(context)' '            val legacyGrab = lines.firstOrNull { it.id == routeId }'
ctl SR-m5 "旧抢读形态回来了"

# SR-f3 司机→用户只读详情那一条被改动（在册例外按字面量钉，动了就要重新登记）
drop "$DMS" 'if (msg.orderNo.isNotEmpty()) nav.navigate(Routes.trace(msg.orderNo))'
ctl SR-f3 "司机侧唯一在册的跨域跳转不见了"

# SR-n 分域深链用例被注释掉一条（考行首锚定与下限）
python - "$TT2" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
ctl SR-n "分域深链用例被注释掉一条"

# ── SR-o … SR-o3（2026-10-10 真机那一族：键盘不许再盖住主操作）──
# 三条各钉一个可独立坏掉的点，所以各注一次（`ctl` 每次跑完自己把沙盒复位，这里不需要"恢复"步骤）。
# ⚠ `drop` 是把整行改成 `// NEG-CONTROL 摘掉：…`，字面量仍在文本里 ⇒ 判据必须剔注释才抓得到
#   （SR-o…o3 三条都按 `NC` 剔注释，这一族注入就是它们的出生证明）。
U="$A/ui/common/TabBar.kt"
# 2026-10-13 合并：壳的 insets 基数由 `navigationBars` 换成 `systemBars`，并且先落成 `val allInsets` 再喂给
# `windowInsetsPadding` ⇒ 老锚点（整串 `.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))`）0 命中。
# 注入改打**定义那一行**：摘掉它，判据的 `systemBars.union(ime)` 锚点就归 0，破口与判据仍是一对一。
drop "$U" 'val allInsets = WindowInsets.systemBars.union(WindowInsets.ime)'
ctl SR-o "壳里 ime 被摘掉：键盘弹起会重新盖住主操作"

drop "$RT" 'com.shanxiang.buspost.ui.common.BusRouteBody {'
ctl SR-o2 "登录路由退出壳：输入最密那一屏没有 ime 避让了"

drop "$U" '.windowInsetsPadding(WindowInsets.navigationBars),'
ctl SR-o3 "底栏那一处 navigationBars-only 被并走：在册读数要同轮改，不许悄悄变 0"

echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
