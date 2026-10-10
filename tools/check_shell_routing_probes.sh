#!/usr/bin/env bash
# V-2 壳路由与分域闸判据（指令 v2.0 Phase 1 验收；审计 §29；CONTEXT_LOCK 硬规则 1）
# 用法：bash tools/check_shell_routing_probes.sh     退出码非 0 = 分域闸破了
#
# 分工：行为由 app/src/test/.../nav/ShellRoutingContractTest.kt 钉（纯函数）；
# 这里钉**结构** —— 工程没有 Robolectric，导航图渲染不出来，只能按内容/结构复核
# "决策是不是真的接到了 UI 上"（否则 ShellDecision.kt 会变成一条没人调用的绿函数）。
# 写法沿用既有教训：按内容过滤不按行号；注释行排除；计数用 -ge 下限，同一轮里抬过。
# 每条都有负控制：bash tools/shell_routing_negative_controls.sh
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

SRC=app/src/main/java/com/shanxiang/buspost
RT="$SRC/ui/BusPostRoot.kt"
SDF="$SRC/nav/ShellDecision.kt"
RS="$SRC/nav/Routes.kt"
DSF="$SRC/ui/driver/DriverMineScreen.kt"
MA="$SRC/MainActivity.kt"
NF="$SRC/notify/Notifier.kt"
AE="$SRC/notify/AlertEngine.kt"
WG="$SRC/widget/NextBusWidgetProvider.kt"
BS="$SRC/ui/user/BusScreen.kt"
BSD="$SRC/ui/user/BusDetailSlot.kt"
DMS="$SRC/ui/driver/DriverMessagesScreen.kt"
TB="$SRC/ui/common/TabBar.kt"
T=app/src/test/java/com/shanxiang/buspost/nav/ShellRoutingContractTest.kt
T2=app/src/test/java/com/shanxiang/buspost/nav/RouteDomainDeepLinkTest.kt
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'

# 先验文件存在：grep 找不到文件时输出是空的，`[ "" = 1 ]` 会报"不是整数"或直接被判成失败，
# 看起来像判据跑过了，其实是根本没跑（负控脚本的基线就是被这条救回来的）。
for f in "$RT" "$SDF" "$RS" "$DSF" "$T" "$MA" "$NF" "$AE" "$WG" "$BS" "$BSD" "$DMS" "$T2"; do
    [ -f "$f" ] || { echo "× 判据文件缺失：$f（先确认工程根/副本建对，别把缺文件读成计数 0）"; exit 2; }
done
DRV='Routes\.(DRIVER_NAV|DRIVER_MESSAGES|HANDOVER|DRIVER_EXCEPTION)'
USR='Routes\.(PARCEL|ORDERS|ADDRESS|ADDRESS_EDIT|ADDRESS_SELECT|FEEDBACK|TRACE|GOODS_DETAIL|GOODS_TRACE|SEND|BUS_DETAIL|BUS|NOTIFICATIONS)'
# 两端共用的工具页（个人资料/设置）不是业务页：司机端「我的」按 2026-10-10 用户指令收录这两个入口，
# 从 USR 里拆出来单独按字面量在册（SF4 恰好 2），不许司机目录出现第三个用户路由引用。

# SR-a 用例条数（Phase 1 之前 app/src/test 里这一域的判据是 0 条；V-3 加"无参数路由"一条 ⇒ 下限 9 → 10，同一轮抬）
SA=$(grep -cE '^[[:space:]]*@Test' "$T")
# SR-b 冷启动落点确实由纯函数决定，且 UI 里没有第二份 when
SB=$(grep -n 'startDestinationFor(' "$RT" | grep -vE "$NC" | wc -l | tr -d ' ')
SB2=$(grep -n 'val start = when' "$RT" | grep -vE "$NC" | wc -l | tr -d ' ')
# SR-c 深链只有一道闸：旧私有 deepLinkAllowed 必须已经消失，不能留两份答案
SC=$(grep -n 'deepLinkTarget(' "$RT" | grep -vE "$NC" | wc -l | tr -d ' ')
SC2=$(grep -rn 'deepLinkAllowed' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
# SR-d 决策文件是纯的：internal 函数没有被 Compose 依赖拖回来（否则又测不了）。
# 下限 2 → 4：V-2b 在同一条文件里加了 `serverSaysDriver` / `reconcileRole`（角色与后端画像同源），同一轮抬。
SD=$(grep -n 'internal fun' "$SDF" | grep -vE "$NC" | wc -l | tr -d ' ')
# SR-d2 三态签名：返回 `Boolean?` 而不是 `Boolean`。退回布尔就等于把"读不到"当成"不是司机"。
SD2T=$(grep -c 'internal fun serverSaysDriver(driverId: Long, failed: Boolean, demo: Boolean): Boolean? = when {' "$SDF")
SD3=$(grep -c 'internal fun reconcileRole(stored: Role, server: Boolean?): Role =' "$SDF")
SD2=$(grep -n 'androidx\.compose' "$SDF" | grep -vE "$NC" | wc -l | tr -d ' ')
# SR-e 用户/登录/公共目录不得引用司机域路由
SE=$(grep -rnE "$DRV" "$SRC/ui/user" "$SRC/ui/login" "$SRC/ui/common" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
USR_TOOL='Routes\.(PROFILE|SETTINGS)'
# SR-f 司机目录不得引用用户域业务页；SR-f2 唯一合法的跨域行是"用户版"出口（切**视图域**，不切账号角色）。#
# 2026-10-10 起 SR-f2 的落点由 DriverShell.kt（原底部第 4 格）改为 DriverMineScreen.kt（"我的"页菜单行）。
SF=$(grep -rnE "$USR" "$SRC/ui/driver" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
SF2=$(grep -n 'AppState.setActiveDomain(AppDomain.USER)' "$DSF" | grep -vE "$NC" | wc -l | tr -d ' ')
SF4=$(grep -rnE "$USR_TOOL" "$SRC/ui/driver" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
# SR-g 未登录不接受深链；通知构造里不得直接投壳路由
SG=$(grep -n 'if (!Prefs.loggedIn) return@LaunchedEffect' "$RT" | grep -vE "$NC" | wc -l | tr -d ' ')
SG2=$(grep -rn 'route = Routes\.\(USER_SHELL\|DRIVER_SHELL\)' "$SRC/notify" --include=*.kt | wc -l | tr -d ' ')
# SR-h 司机域路由集合的字面量是现行五条（V-3 加了 §54 的 driverException；DRIVER_GUIDE 正式导航页注册时加入，2026-10-10 补登判据字面量）
SH=$(grep -c 'val DRIVER_ONLY = setOf(DRIVER_NAV, DRIVER_GUIDE, DRIVER_MESSAGES, HANDOVER, DRIVER_EXCEPTION)' "$RS")

# ── F-3 ⑥（USER 业务闭环与分域深链复核）新增：SR-i … SR-n ─────────────────────────
# 字面量锚点一律走 cnt/cntE：带 -n 前缀再滤掉注释行，这样"把那一行注释掉"等于把它删掉。
# 负控的 drop 手法就是加 `//` 前缀，早先用裸 grep -c 的锚点因此空跑过（D-039 同一族）。
cnt()  { grep -nF "$2" "$1" | grep -vE "$NC" | wc -l | tr -d ' '; }
cntE() { grep -nE "$2" "$1" | grep -vE "$NC" | wc -l | tr -d ' '; }
# SR-i 深链前缀**只有一个来源**。以前 "buspost://app" 在 MainActivity 的私有常量、Notifier 的
# Uri.parse 字面量里各写一份，拼的一方和剥的一方没有同一份口径 ⇒ 任一侧改了就是"点了通知没反应"。
SI=$(cnt "$RS" 'const val DEEP_LINK_PREFIX = "buspost://app"')
SI2=$(grep -rn '"buspost://' "$SRC" --include=*.kt | grep -vE "$NC" | grep -v 'DEEP_LINK_PREFIX' | wc -l | tr -d ' ')
SI3=$(cnt "$MA" 'Routes.routeFromDeepLink(intent?.dataString)')
SI4=$(cnt "$RS" 'fun deepLink(route: String) = "$DEEP_LINK_PREFIX/$route"')
SI5=$(cnt "$RS" 'fun routeFromDeepLink(data: String?): String? {')
# SR-j 编码必须是**纯 Kotlin**。Routes.kt 一旦 import android.net.Uri，`unitTests.isReturnDefaultValues = true`
# 就让 Uri.encode 在 JVM 用例里返回 null，"有没有编码"当场不可观测 —— 旧那条用例拿到 `trace/null`
# 跟自己比，永远绿（本轮查出的空转判据，收口方式见 D-⑤-1 / §八 ㉙）。
SJ=$(cntE "$RS" '^import android\.net\.Uri')
SJ2=$(cnt "$RS" 'internal fun encodeSegment(raw: String): String = buildString {')
# SR-k 路径段只能由 builder 拼：手拼 "trace/$xxx" 不带编码，参数里混进 `/` 或 `?` 就把整条路由
# 搬到 DRIVER_ONLY 之外（闸门是精确集合成员判断），等于对这条深链失效。
# SK2 是 builder 调用点计数：SR-f 的正则只认 `Routes.TRACE` 这类**常量**，builder 形态
# （`Routes.trace(...)`）在它眼里是隐形的，所以这里单独钉一份，只报数不判方向。
SK=$(grep -rn '"trace/\$' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
SK2=$(grep -rnE 'Routes\.(trace|busDetail)\(' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
SK3=$(cnt "$AE" 'Routes::trace')
# SR-l 出网口（前台服务通知、桌面小组件）都走 Routes.deepLink。
# 旧 widget 写法 `putExtra("route", "bus")` 永远没人取（MainActivity 只读 intent.dataString），
# 类注释却写着"直达实时公交页" —— 那是假话，SL2 归零就是不让它换个写法回来。
SL=$(grep -rnF 'Uri.parse(Routes.deepLink(' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
SL2=$(grep -rn 'putExtra("route"' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
SL3=$(cnt "$NF" 'if (route != null) data = Uri.parse(Routes.deepLink(route))')
SL4=$(cnt "$WG" 'data = Uri.parse(Routes.deepLink(Routes.BUS))')
# SR-m 线路详情四态：判断本体在纯函数里，UI 只念结论（沿用 SR-d「抽纯函数才可测」那一招）。
# SM5 钉的是**旧抢读形态**：`load(context)` 之后立刻 `lines.firstOrNull { it.id == routeId }` ——
# load 的函数体在 work 协程里，返回时列表还没落地，所以那条语句永远读到的是上一批数据。
SM=$(cnt "$BSD" 'internal fun detailSlotOf(')
SM2=$(grep -n 'androidx\.compose' "$BSD" | grep -vE "$NC" | wc -l | tr -d ' ')
SM3=$(cnt "$BS" 'when (vm.detailSlot()) {')
SM4=$(cnt "$BS" 'LaunchedEffect(routeId) { vm.openDetail(context, routeId) }')
SM5=$(grep -rn 'lines\.firstOrNull { it\.id == routeId }' "$BS" | grep -vE "$NC" | wc -l | tr -d ' ')
# SR-f3 司机侧唯一一处跨到用户只读详情页的跳转（与 SR-f2 同一手法：在册的例外按字面量钉死，
# 多一处就变红，而不是靠"我看代码觉得只有一处"）。
SF3=$(cnt "$DMS" 'if (msg.orderNo.isNotEmpty()) nav.navigate(Routes.trace(msg.orderNo))')
# SR-n 分域深链用例簿条数（⑥ 之前这一族在 app/src/test 里是 0 条）
SN=$(grep -cE '^[[:space:]]*@Test' "$T2")
SN2=$((SA + SN))

# ── 2026-10-10（真机在环第一轮）SR-o：钉"键盘不再能盖住主操作" ────────────────────
# 成因是设备读数不是推理：targetSdk 36 强制 edge-to-edge 之后，manifest 那句
# `android:windowSoftInputMode="adjustResize"` **已经不缩放窗口了**，而全工程 `ime` 读法当时是 0 处
# ⇒ 真机（小米平板 6 / 2136x3200 @440dpi）实测登录页"登录"与《服务协议》整块被输入法盖住、
# 且一次上滑所有节点纹丝不动（`docs/device-evidence/20261010-sweep/08-ime-scroll.png`，
# 修复后同一手势把"登录"带到 y=1566：`35-login-scrolled-ime.xml`）。修法两半各自可坏，各钉一条：
#   SR-o   壳的 insets 里 `ime` 还在不在（union，不是再叠一层 imePadding —— 叠两次会多一段空白）；
#   SR-o2  登录路由还走不走这个壳（它是全工程唯一没套壳的输入密集屏）；
#   SR-o3  底栏自己那一处 navigationBars-only **本轮有意没并进来**（改它要连两个壳的布局一起重做），
#          所以按"恰好 1"钉住：谁并进来了它会变 0 而红 —— 那是逼着同轮把口径与本条注释一起改对，
#          不是拦修复（与 NAC2 的"恰好 N 摩擦设计"同一规格）。
# ⚠ 三条都**剔注释行**：负控的 `drop` 是把整行改成 `// NEG-CONTROL 摘掉：…`，
#   字面量还在文本里 —— 不剔注释的话破口会读成"还在"，负控当场变灰（F-1 ③ 那条坑的同形）。
SO_IME=$(grep -n 'navigationBars\.union(WindowInsets\.ime)' "$TB" | grep -vE "$NC" | wc -l | tr -d ' ')
# ⚠ 窗口必须封口：第一版写成"LOGIN 之后任意一条非注释 `BusRouteBody {`"，于是把登录那行注释掉之后，
#   awk 继续往下扫到 `composable(Routes.SEND) { … BusRouteBody { … } }` 又数到 1 ⇒ 负控 SR-o2 实测**不红**
#   （本轮亲眼看它灰掉一次）。现在遇到下一条 `composable(` 就停，破口才只落在登录那一格里。
SO_LOGIN=$(awk '/composable\(Routes\.LOGIN\) \{/{f=1; next} f && /composable\(/{exit} f && !/^[[:space:]]*(\/\/|\*)/ && /BusRouteBody \{/{c++; exit} END{print c+0}' "$RT")
SO_TAB=$(grep -n 'windowInsetsPadding(WindowInsets\.navigationBars)' "$TB" | grep -vE "$NC" | wc -l | tr -d ' ')

echo "SR-a 壳路由用例条数        = $SA （目标 ≥10）"
echo "SR-b startDestinationFor 调用/残留 when = $SB/$SB2 （目标 1/0）"
echo "SR-c deepLinkTarget 调用/旧闸门残留     = $SC/$SC2 （目标 1/0）"
echo "SR-d internal 函数/Compose 依赖         = $SD/$SD2 （目标 4/0）"
echo "SR-d2/d3 三态签名（Boolean?）字面量     = $SD2T/$SD3 （目标 1/1）"
echo "SR-e 用户域引用司机路由      = $SE （目标 0）"
echo "SR-f 司机域引用用户业务页/唯一在册例外  = $SF/$SF3 （目标 0/1）"
echo "SR-f2 司机壳的用户版出口      = $SF2 （目标 1：唯一合法跨域行，切的是视图域而不是账号角色；2026-10-10 起在 DriverMineScreen）"
echo "SR-f4 司机目录工具页入口      = $SF4 （目标 2：个人资料/设置，两端共用工具页在册；其余用户路由仍为 0）"
echo "SR-g 未登录深链守卫/通知投壳  = $SG/$SG2 （目标 1/0）"
echo "SR-h DRIVER_ONLY 字面量      = $SH （目标 1）"
echo "SR-i 深链前缀来源/剥离落点/两个 builder = $SI/$SI2/$SI3/$SI4/$SI5 （目标 1/0/1/1/1）"
echo "SR-j Routes.kt 的 android.net.Uri 导入/纯 Kotlin 编码 = $SJ/$SJ2 （目标 0/1）"
echo "SR-k 手拼 trace 模板/builder 调用点/方法引用 = $SK/$SK2/$SK3 （目标 0/≥11/1）"
echo "SR-l deepLink 出网口/旧 extra 残留/两处字面量 = $SL/$SL2/$SL3/$SL4 （目标 3/0/1/1）"
echo "SR-m 四态纯函数/Compose 依赖/UI 读取/接线/旧抢读 = $SM/$SM2/$SM3/$SM4/$SM5 （目标 1/0/1/1/0）"
echo "SR-n 分域深链用例条数/两簿合计 = $SN/$SN2 （目标 ≥9/≥19）"
echo "SR-o 壳里 ime 在场/登录走壳/底栏那处未并进来 = $SO_IME/$SO_LOGIN/$SO_TAB （目标 1/1/1，第三格是有意留的读数）"

fail=0
[ "$SA" -ge 10 ] || { echo "× SR-a 壳路由用例少于 10 条（删用例必须让判据变红）"; fail=1; }
[ "$SB" = 1 ] || { echo "× SR-b UI 不再经纯函数决定落点，Phase 1 判据退回不可测形态"; fail=1; }
[ "$SB2" = 0 ] || { echo "× SR-b BusPostRoot 里又出现第二份落点 when（两处答案会打架）"; fail=1; }
[ "$SC" = 1 ] || { echo "× SR-c 深链不再过闸门，通知可以绕过角色直接导航"; fail=1; }
[ "$SC2" = 0 ] || { echo "× SR-c2 旧私有 deepLinkAllowed 还在，分域闸有了两份实现"; fail=1; }
[ "$SD" = 4 ] || { echo "× SR-d ShellDecision 的四个决策函数少了（2 条壳路由 + 2 条角色同源）"; fail=1; }
[ "$SD2" = 0 ] || { echo "× SR-d 决策文件被 Compose 依赖污染，重新变成 JVM 测不了的东西"; fail=1; }
[ "$SD2T" = 1 ] || { echo "× SR-d2 serverSaysDriver 不再返回 Boolean?：三态退回布尔，'读不到'会被当成'不是司机'"; fail=1; }
[ "$SD3" = 1 ] || { echo "× SR-d3 reconcileRole 的签名/入参变了：降级判据不再由纯函数决定"; fail=1; }
[ "$SE" = 0 ] || { echo "× SR-e 用户侧页面开始引用司机域路由（硬规则 1）"; fail=1; }
[ "$SF" = 0 ] || { echo "× SR-f 司机侧页面开始引用用户域业务页（硬规则 1）"; fail=1; }
[ "$SF3" = 1 ] || { echo "× SR-f3 司机→用户只读详情的那一条不见了或多了一条：在册例外要按字面量登记，不许凭'我看代码觉得只有一处'"; fail=1; }
[ "$SF2" = 1 ] || { echo "× SR-f2 司机壳出口不再切视图域：人在用户端、后台还在轮询司机域接口（角色那一层由 RR-e2 管）"; fail=1; }
[ "$SF4" = 2 ] || { echo "× SR-f4 司机目录的用户工具页入口多了或少了（在册 = 个人资料/设置 两处，见 DriverMineScreen）"; fail=1; }
[ "$SG" = 1 ] || { echo "× SR-g 未登录也接受深链了"; fail=1; }
[ "$SG2" = 0 ] || { echo "× SR-g 通知直接把用户投进某个壳：进壳只应由角色决定"; fail=1; }
[ "$SH" = 1 ] || { echo "× SR-h DRIVER_ONLY 不再是那四条（漏一条=开闸，多一条=锁死用户入口）"; fail=1; }
[ "$SI" = 1 ] || { echo "× SR-i 深链前缀常量不在了：拼的一方与剥的一方又没有共同口径了"; fail=1; }
[ "$SI2" = 0 ] || { echo "× SR-i2 别处又手写了一遍 buspost:// 前缀（改一处坏一处，且坏的是通知点击）"; fail=1; }
[ "$SI3" = 1 ] || { echo "× SR-i3 MainActivity 不再经 Routes.routeFromDeepLink 剥前缀"; fail=1; }
[ "$SI4" = 1 ] || { echo "× SR-i4 deepLink() 的拼装体变了：出网口拼出来的串不再是闸门认识的那条路由"; fail=1; }
[ "$SI5" = 1 ] || { echo "× SR-i5 routeFromDeepLink() 的签名/返回类型变了：对不上的深链又会返回空串而不是 null"; fail=1; }
[ "$SJ" = 0 ] || { echo "× SR-j Routes.kt 又依赖 android.net.Uri：编码在 JVM 用例里重新变成不可观测的东西"; fail=1; }
[ "$SJ2" = 1 ] || { echo "× SR-j2 encodeSegment 不再是纯 Kotlin 的 buildString 实现"; fail=1; }
[ "$SK" = 0 ] || { echo "× SR-k 出现手拼 \"trace/\$…\"：参数没编码，带 / 或 ? 的编号能绕过分域闸"; fail=1; }
[ "$SK2" -ge 11 ] || { echo "× SR-k2 builder 调用点掉到 11 以下：跳转入口被删或退回手拼（本轮实测 13）"; fail=1; }
[ "$SK3" = 1 ] || { echo "× SR-k3 AlertEngine 不再用 Routes::trace 拼路由：到货提醒的深链又是手拼形态"; fail=1; }
[ "$SL" -ge 3 ] || { echo "× SR-l 走 Routes.deepLink 的出网口少于 3 处：通知或小组件退回各自手拼"; fail=1; }
[ "$SL2" = 0 ] || { echo "× SR-l2 putExtra(\"route\") 回来了：MainActivity 只读 dataString，那条 extra 永远没人取"; fail=1; }
[ "$SL3" = 1 ] || { echo "× SR-l3 通知点击的深链拼装不在这行了：点了通知到不了该到的页"; fail=1; }
[ "$SL4" = 1 ] || { echo "× SR-l4 桌面小组件不再投实时公交页（类注释那句「直达」就又变成假话）"; fail=1; }
[ "$SM" = 1 ] || { echo "× SR-m 四态判断不再是 detailSlotOf 纯函数：优先级又回 UI 里，JVM 读不到"; fail=1; }
[ "$SM2" = 0 ] || { echo "× SR-m2 BusDetailSlot 被 Compose 依赖污染：这条文件存在的全部理由是它能被用例读到"; fail=1; }
[ "$SM3" = 1 ] || { echo "× SR-m3 详情页空态不再读 vm.detailSlot()：四态又并成一句「线路信息获取失败」"; fail=1; }
[ "$SM4" = 1 ] || { echo "× SR-m4 详情页不再经 openDetail 进入：选线又落到列表落地之前"; fail=1; }
[ "$SM5" = 0 ] || { echo "× SR-m5 旧抢读形态回来了：load 返回时协程还没落地，读到的是上一批线路"; fail=1; }
[ "$SN" -ge 9 ] || { echo "× SR-n 分域深链用例少于 9 条（删用例必须让判据变红）"; fail=1; }
[ "$SN2" -ge 19 ] || { echo "× SR-n2 两本路由用例合计少于 19 条"; fail=1; }
[ "$SO_IME" = 1 ] || { echo "× SR-o 壳里不再并 ime：键盘一弹，输入密集屏的主操作就会被盖住（真机实测过的那条，不是假想）"; fail=1; }
[ "$SO_LOGIN" = 1 ] || { echo "× SR-o2 登录路由退出 BusRouteBody：它是全工程输入最密的一屏，退出等于把那个 P0 原样放回去"; fail=1; }
[ "$SO_TAB" = 1 ] || { echo "× SR-o3 底栏那一处 navigationBars-only 形状变了（现 $SO_TAB）：并进来就同轮改这条注释与 D-069 §3 的口径，不许让它悄悄变 0"; fail=1; }

if [ "$fail" = 0 ]; then echo "壳路由判据通过"; else echo "壳路由判据失败"; fi
exit "$fail"
