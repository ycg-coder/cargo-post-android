#!/usr/bin/env bash
# V-1 司机动作幂等判据（指令 §52；审计 §73；CONTEXT_LOCK §八）
#   + F-2 第 6 步 用户端 create 幂等判据（U-*；本本 I-* 一条未动）
#   + F-3 第 7/9 步 司机写路径"缺项即不出网"判据（H-*；I-*/U-* 同样一条未动）
# 用法：bash tools/check_idempotency_probes.sh     退出码非 0 = 幂等纪律破了
# 写法沿用既有教训：**按内容/结构过滤，不按行号**（行号会漂，内容不漂）；
# 注释行一律排除，否则文档里"提到某个字段名"会被当成"发了那个字段"。
# 每条都有负控制：bash tools/idempotency_negative_controls.sh —— 不会变红的判据等于没写。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

SRC=app/src/main/java/com/shanxiang/buspost
REPO="$SRC/data/repo/Repo.kt"
GATE="$SRC/data/repo/ActionGate.kt"
UI="$SRC/ui/driver"
T_GATE=app/src/test/java/com/shanxiang/buspost/data/repo/ActionGateTest.kt
T_REPO=app/src/test/java/com/shanxiang/buspost/data/repo/DriverActionIdempotencyContractTest.kt

NC=':[0-9]+: *(\*|//)'   # 注释行：以 * 或 // 开头的源码行不算代码

# 司机一次性动作的函数名清单（判据范围）。新增写接口必须同时进这张表，否则 I-a 与 I-b 对不上就会报红。
WRITE_FUNCS='depart|arrive|pickupConfirm|deliver|pickupVerify|productLoad|productDeliver|legAction|confirmHandover|readDriverMessage'

# I-a 十个写函数各自的第一步就是交给闸门（按"签名后的前两行"判，不看行号）
IA=$(grep -A2 -E "^    suspend fun ($WRITE_FUNCS)\(" "$REPO" | grep -c 'gatedAction(')
# I-b 闸门调用点总数（= 定义之外真正走闸的出口数）
IB=$(grep -o 'gatedAction("' "$REPO" | wc -l | tr -d ' ')
# I-c 有意豁免：周期位置上报**不得**被幂等闸吞掉（吞点=偷点，新鲜度与丢点可见由 ⑦ 的合并闸负责）
IC=$(grep -A6 'suspend fun reportLocation(' "$REPO" | grep -c 'gatedAction(')
# I-d 闸门必须是"丢弃"语义：tryLock 一次，绝不能退回 Mutex.withLock（排队 = 第一次完成后照发）
ID=$(grep -o 'tryLock()' "$GATE" | wc -l | tr -d ' ')
ID2=$(grep -o '\.withLock(' "$GATE" | wc -l | tr -d ' ')
# I-e 页面必须能区分"被本地闸拦下"与"已提交后端"：五个回执点一支不漏
# （§54 起多一个异常上报页，同一轮把下限从 4 抬到 5 —— 下限跟着生效代码走，不是随手放宽；
#   F-3 ⑦⑨ 工作台多出一个**非文案**的 notSent 消费者（`if (!ack.notSent) docked = ...`，视图推进也算回执），
#   同一轮把 5 抬到 6 —— 不抬的话这一族判据会把"认得出 notSent"读成"只有文案认得出"。）
IE=$(grep -rn 'ack\.notSent' "$UI" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
# I-f 被拦下的那一支绝不能写"已提交后端"（宁可说"没发出去"，不许假装成功）
IF=$(grep -rn 'ack\.notSent' "$UI" --include=*.kt | grep -vE "$NC" | grep -c '已提交后端')
# I-g 按钮旗标不得在 work {} 内部才置位（旧形态的先后手取决于调度器语义，本机无 Robolectric 证不了）
IG=$(grep -rn -A1 'work(reset = false) {' "$UI" --include=*.kt | grep -cE '(busy|legBusy) = true')
# I-h 红线：绝不把后端不收的唯一键塞进报文（§52 + CONTEXT_LOCK"绝不自己假造 API"）
IH=$(grep -rn 'clientRequestId\|requestId\|bizNo\|idempotencyKey\|X-Request-Id' app/src/main --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
# I-i Ack 上必须有 notSent 这个可判别的字段（页面靠它分支，不是靠文案猜）
II=$(grep -c 'val notSent: Boolean = false' "$REPO")
# I-j 用例锚点：闸门 5 条 + 仓库/网络 6 条
IJ=$(cat "$T_GATE" "$T_REPO" | grep -cE '^[[:space:]]*@Test')

# ---------- F-2 第 6 步：用户端 create 幂等（I-* 那十条是司机域的，一条都不动） ----------
# 分两本判的原因不是"懒得合并"，是**硬规则 1**：司机域与用户域的写路径不许共用一个闸门、
# 也不许共用一条判据 —— 合并之后"司机侧还是 10 个出口"这种数就会被用户侧的新出口顶掉，
# 两边互相解释，红的时候说不清是哪一侧破的。
T_USER=app/src/test/java/com/shanxiang/buspost/data/repo/UserCreateIdempotencyTest.kt
USER_CREATE_FUNCS='createProductOrder|createSendOrder'

# 扫描面按**函数体**收（§八 ㉒ ①：同名成员会把两条契约并成一条），不按行号、不靠固定 -A 窗口
fn_body() {
  awk -v name="$1" '
    $0 ~ ("^    suspend fun " name "\\(") { grab = 1 }
    grab { print }
    grab && /^    \}/ { grab = 0 }
  ' "$REPO"
}

# U-a 两处 create 的第一步就是交给用户闸（函数体内必须出现 gatedUserCreate）
UA=$(for f in $USER_CREATE_FUNCS; do fn_body "$f" | grep -c 'gatedUserCreate(path, '; done | awk '{s+=$1} END{print s+0}')
# U-b 用户闸的调用点总数（声明之外只有这两处；多了=凭空多设出口，少了=有人绕闸）
UB=$(grep -o 'gatedUserCreate(path, ' "$REPO" | wc -l | tr -d ' ')
# U-c 分域两把闸：Repo 上 ActionGate 实例必须恰好 2（一域一把，共用一把下单会去挡司机动作）
UC=$(grep -o '= ActionGate()' "$REPO" | wc -l | tr -d ' ')
# U-c2/U-c3 两把闸各自的使用面：声明 1 + 唯一出口里 1 = 2。任何第三个函数想借用另一域的闸都会让计数变 3
UC2=$(grep -o 'userCreateGate' "$REPO" | wc -l | tr -d ' ')
UC3=$(grep -o 'actionGate' "$REPO" | wc -l | tr -d ' ')
# U-d 键的三个维度都在源码里：端点、登录身份、整份报文（排序后喂进摘要）
UD=$(grep -o 'digest\.update(path\|digest\.update(Prefs\.mobile\|body\.entries\.sortedBy' "$REPO" | wc -l | tr -d ' ')
# U-e 用户端页面必须认得出 notSent：商品下单 + 寄货下单两支
UE=$(grep -rn 'ack\.notSent' "$SRC/ui/user" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
# U-f 被拦下的那一支绝不能写"已提交后端/下单成功"
UF=$(grep -rn -A3 'ack\.notSent' "$SRC/ui/user" --include=*.kt | grep -vE "$NC" | grep -c '已提交后端\|下单成功\|寄货成功')
# U-g 收货人姓名/手机号/地址不得以明文进键（键表是进程级、活得比页面久；只准存摘要）
UG=$(grep -o 'return "create:\$hex"' "$REPO" | wc -l | tr -d ' ')
# U-h 用户端 create 的用例条数（下限随用例走，删一条就响）
#      2026-10-08：10 → 11。本轮新增的是具名行为用例「商城单回执只认 orderNo…」（CONTRACT_EVIDENCE §10.5），
#      它在撤读 amount/totalPrice/no **之前**会拿到 SC-DEAD / 66.0 ⇒ 先红后绿、不是空壳。
#      ⚠ 这不是抬闸值：闸仍然"只认一个数"，而这一格刚被一次合法新增证活过（真加用例时它确实红了）——与 SU-b 52→53 同形。
UH=$(grep -cE '^[[:space:]]*@Test' "$T_USER")
# U-i 对账：用户域"下单类" create 端点数 == 走闸数（有人新增一条下单接口没走闸，这里立刻不等）
UI_ORDER=$(grep -o 'val path = "\$T/[a-z-]*/create"' "$REPO" | wc -l | tr -d ' ')
# U-j 只登记不判红的残留面：用户域还有两处 create **有意不走闸** —— 后端在这两个接口上**零** @Idempotent
# （`AppFeedbackController` / `AppAddressController` 全类无该注解），本机单方面拦就是替后端发明规则；
# 而且它们的重复提交不产生"两张单"那种脏数据。⚠ 但必须继续可见：数字涨了就是有人把新的下单接口写成了别的样子。
UI_ALL=$(grep -oE '"\$(T|M)/[a-z-]+/create"' "$REPO" | wc -l | tr -d ' ')

# ---------- F-3 第 7/9 步：司机写路径"缺项即不出网"（H-*；I-*/U-* 一条不动） ----------
# 这一族判的不是闸门（I-* 管"同一动作不重发"），而是闸门**之前**那一步：
# 本机明知缺了字段还往网上发，后端只回一条必被拒的错误，可司机在画面上看到的是一次"到站/核销"。
# ⚠ 判据只到"结构在不在"这一层：真正的"没发出去"由 H-g 那本用例用 server.requestCount 钉，
#    结构判据不能被当成行为证据（§八 ㉘ ⑦：在范围内不等于指着对的内容）。
WB="$UI/WorkbenchScreen.kt"
HT=app/src/test/java/com/shanxiang/buspost/ui/driver/DriverActionHonestyContractTest.kt

# 按函数体收，不按行号也不靠固定 -A 窗口（§八 ㉒ ①：同名成员会把两条契约并成一条）
vm_body() {
  awk -v name="$1" '
    $0 ~ ("^    fun " name "\\(") { grab = 1 }
    grab { print }
    grab && /^    \}/ { grab = 0 }
  ' "$WB"
}

# H-a 到站：站点编号缺项必须早退（后端 AppDriverArriveReqVO 的 stationId 是 @NotNull，0L 发出去只是必被拒的一跳）
HA=$(vm_body arrive | grep -c 'if (sid <= 0L) {')
# H-b 到站：docked 只许在**真发出去**那一支翻（notSent 一个字节都没上网络，画面不许先行推进）
HB=$(vm_body arrive | grep -c 'if (!ack.notSent) docked = !isTerminal')
# H-c 与 H-b 同一处的反面：函数体内不得再有"裸的 docked 翻转"（不认 notSent 的那一型）
HC=$(vm_body arrive | grep -cE '^[[:space:]]*docked = ')
# H-d 取件核销：空白取件码必须早退（Api 出网前会把空串整个键丢掉，后端收到的是没有 pickupCode 的请求）
HD=$(vm_body verify | grep -c 'if (code.trim().isEmpty()) {')
# H-e 两处早退都必须留一句回执，不许静默 return（静默 = 司机以为点中了却没任何反应）
HE1=$(vm_body arrive | grep -c 'receipt = "后端没给本站编号')
HE2=$(vm_body verify | grep -c 'receipt = "取件码还没填')
# H-f 早退闸门不许顺手改报文：两处后端字段名必须还是原来那两个键
HF=$(grep -o '"stationId" to stationId\|"pickupCode" to pickupCode' "$REPO" | wc -l | tr -d ' ')
# H-g 诚实化用例条数（下限随用例走，删一条就响）
#     4→5：#147 收尾补的那条「继续行驶只推进导航目标」（H-m 那一族的**行为**半边，见 H-m5）
HG=$(grep -cE '^[[:space:]]*@Test' "$HT")
# H-h 反向对照：三条用例各自钉住"真的发出去了"那一侧（早退不许退化成"这个动作永远不发"）
# H-h 反向对照：三条用例各自钉住"真的发出去了"那一侧（早退不许退化成"这个动作永远不发"）
#   锚点数字 11→12（2026-10-12 D-080）：司机工作台进入/刷新那一轮多了 1 条公告请求（6 读 + 1 写 + 5 读）。
#   ⚠ 这一格是"逐字锚点"，改测试里的数字必须同轮改这里 —— 它同时也是在提醒：动作后的回读仍只有 5 条。
HH=$(grep -c '12, server.requestCount' "$HT")
# H-i 夹具自检：定义 1 + 五条用例各调一次。坏夹具会静默变成"没有站点"，把早退读成通过
HI=$(grep -c 'awaitLoaded' "$HT")

# H-j 夹具的键形状必须就是后端**实际下发**的那一套（F-3 ⑦⑨ 本轮补丁 #184）：
#     `stops`（AppDriverShiftRespVO:34）、`stationId`/`stationName`（AppDriverStationRespVO:11-13）、
#     `plateNo`（AppDriverProfileRespVO:19，Models.kt:1374 现在只认这一个键）。
#     客户端 multiArr("stations","stops",…) / multiStr("plateNo","plate",…) 认别名 ⇒ 别名夹具**同样全绿**，
#     但它证明的是"别名可用"，不是"真机那条路走得通"；F-3 ② 正在删无生产者别名，删完真机会坏而用例照绿。
HJ_STOPS=$(grep -o '"stops":\[' "$HT" | wc -l | tr -d ' ')
HJ_ALIAS=$(grep -o '"stations":\[\|"plate":\|"senderName":\|"id":' "$HT" | wc -l | tr -d ' ')
HJ_PLATE=$(grep -o '"plateNo":' "$HT" | wc -l | tr -d ' ')
# H-k 商城单判据只准剩 bizType 一个条件（F-3 ⑦⑨ 本轮补丁 #185）：
#     后端 ORDER_TYPE_CARGO=2 / POSTAL=3 / PRODUCT=4（DriverAppServiceImpl:101-105），
#     商城单的 bizType 恒为 "PRODUCT"（:352），小程序 workbench.js:885 也只认 bizType。
#     ⚠ 这条是**跨业务域**的口子，不是口径分歧：旧写法 `|| orderType == 3 && mailNo.isEmpty()`
#        把一张没面单号的邮快件判成商城单 ⇒ 装车走 /driver/product-load、妥投走 /driver/product-deliver，
#        那两个接口按 ProductOrderDO 查单（硬规则 1：用户端与司机端/业务域必须分开）。
MODELS="$SRC/data/model/Models.kt"
# ⚠ 两条都要先剔注释行：本类 :1485 的 KDoc 里**逐字写着**旧表达式 `|| orderType == 3 && mailNo.isEmpty()`
#    （那就是"为什么删掉它"的证据），不剔的话文档提一次就等于代码错一次 —— 沿用本本开头那条写法纪律。
NCM='^[[:space:]]*(\*|//|/\*)'
HK_PROD=$(grep -vE "$NCM" "$MODELS" | grep -c 'val isProduct: Boolean get() = bizType.equals("PRODUCT", true)')
HK_LEAK=$(grep -vE "$NCM" "$MODELS" | grep -o 'orderType == 3 && mailNo' | wc -l | tr -d ' ')
# H-l 走查出来的那一支必须有红用例兜着：只改源码不补断言，下次有人把枚举码"顺手加回去"是静默的
DS=app/src/test/java/com/shanxiang/buspost/driver/DriverStateTest.kt
HL_GUARD=$(grep -o 'mailNoWaybill\|realProduct' "$DS" | wc -l | tr -d ' ')

# ---------- H-m「任务点统一模型」（#147 收尾）：目的地这一层**有几处、各认谁** ----------
# 这一族不新增行为，是把已经跑通的分派**钉住**：本机"任务点"其实是两层派生，各司其职——
#   ① 导航目标 targetIndex() = 后端 currentStationId 推出的下一站 + 本机 navOffset；
#   ② 显示层 currentIdx = 只认后端 currentStationId（进度条、"第 N/M 站"、每站到站态）。
# 为什么要拆着钉：把②并进①（"界面跟着导航目标走"）看着只是少一个变量，实际会让司机还没到站
# 画面就显示"已到第 3/5 站"——那是本机视图冒充后端状态，与"I-e/I-f 那族不许谎报"同一族红线。
# 为什么要单独钉 navOffset 的写点：红线"禁止 GPS 自动完成业务"要求推进只由司机点「继续行驶」触发，
# 写点一旦散开（位置回调、页面初始化都能改它），距离/目标就会随GPS自己走。
# ⚠ H-m4 钉的是**耦合的方向**：到站提交的就是页面算出的导航目标，这与小程序
#   `workbench.js:797-810 arriveAtStation()` 用 `points[this.data.targetIndex].stationId` 同源；
#   改这一处必须先重做三方核对，不许顺手"修"成后端当前站（那会分叉，且后端逐站校验会拒——见 :902 一带）。
HM_TGT=$(vm_body targetIndex | grep -c 'indexOfFirst { it.id == s.currentStationId }$')
# 声明行 `private var navOffset = 0` 是初值不是写点，剔掉；剩下的必须全在 continueToNextStation 体内
HM_NAV_ALL=$(grep -vE "$NCM" "$WB" | grep -v 'var navOffset' | grep -o 'navOffset = \|navOffset += ' | wc -l | tr -d ' ')
HM_NAV_IN=$(vm_body continueToNextStation | grep -o 'navOffset = \|navOffset += ' | wc -l | tr -d ' ')
# ⚠ 锚点必须把原式**收口**（`}$` 或后面紧跟 `,`）：不收口的话 `…currentStationId } + 1` 这种
#    "在原式后面加一格"的破口会照样命中 —— 本族第一版就是这么写的，注入 `+ 1` 后判据仍然绿，
#    是负控制把那一次记成 ✘（失效）才暴露出来的；判据自己不会发现自己太宽。
HM_CUR=$(grep -vE "$NCM" "$WB" | grep -c 'val currentIdx = shift.stations.indexOfFirst { it.id == shift.currentStationId }$')
HM_ARR=$(grep -vE "$NCM" "$WB" | grep -c 'onArrive = { vm.arrive(vm.targetStationId()) },')
# H-m5 上面四条是**结构**半边（写点在哪、认的是哪个键），行为半边得有用例钉着：
#      "点了「继续行驶」之后，后端给的当前站没被本机回写、也没多打一条请求"——这一句只有 requestCount 能证。
HM_NET=$(grep -c '一条请求都不许多发' "$HT")

echo "I-a 写函数首步走闸        = $IA （目标 10）"
echo "I-b 闸门调用点            = $IB （目标 10）"
echo "I-c 位置上报被误接进闸门    = $IC （目标 0：有意豁免，接进去就是偷点）"
echo "I-d tryLock/.withLock(     = $ID/$ID2 （目标 1/0：丢弃而非排队）"
echo "I-e 页面 notSent 分支       = $IE （目标 6：工作台文案+视图推进/线路/交接两处/异常上报）"
echo "I-f notSent 分支谎称已提交  = $IF （目标 0）"
echo "I-g 旗标在 work 内才置位    = $IG （目标 0）"
echo "I-h 自造幂等键进报文        = $IH （目标 0）"
echo "I-i Ack.notSent 字段声明    = $II （目标 1）"
echo "I-j 幂等用例条数            = $IJ （目标 11）"
echo "U-a create 首步走用户闸     = $UA （目标 2：商城下单 + 寄货下单）"
echo "U-b 用户闸调用点            = $UB （目标 2）"
echo "U-c Repo 上的闸门实例       = $UC （目标 2：一域一把）"
echo "U-c2 userCreateGate 出现    = $UC2 （目标 2：声明 1 + 唯一出口 1）"
echo "U-c3 actionGate 出现        = $UC3 （目标 2：跨域借用会顶到 3）"
echo "U-d 键的维度（端点/身份/报文）= $UD （目标 3）"
echo "U-e 用户端 notSent 分支     = $UE （目标 2）"
echo "U-f 用户端谎称已提交        = $UF （目标 0）"
echo "U-g 键只存摘要的返回点      = $UG （目标 1）"
echo "U-h 用户端 create 用例条数   = $UH （目标 11）"
echo "U-i 下单端点/走闸数         = $UI_ORDER/$UB （目标 2/2 且两者必须相等）"
echo "U-j 用户域 create 端点总数   = $UI_ALL （登记不判红：其中 2 处后端零 @Idempotent，刻意不走闸）"
echo "H-a 到站缺站点编号的早退    = $HA （目标 1）"
echo "H-b 到站 docked 认 notSent  = $HB （目标 1）"
echo "H-c 到站内裸翻 docked        = $HC （目标 0）"
echo "H-d 核销空白取件码早退      = $HD （目标 1）"
echo "H-e 两处早退的回执            = $HE1/$HE2 （目标 1/1）"
echo "H-f 早退未改动报文字段名      = $HF （目标 2）"
echo "H-g 诚实化用例条数            = $HG （目标 5）"
echo "H-h 反向对照（真发出去那一侧）= $HH （目标 3）"
echo "H-i 夹具自检定义+调用         = $HI （目标 6）"
echo "H-j 夹具键形状 stops/别名/plateNo = $HJ_STOPS/$HJ_ALIAS/$HJ_PLATE （目标 3/0/1：只准后端实际下发的键）"
echo "H-k isProduct 单条件/枚举码回流 = $HK_PROD/$HK_LEAK （目标 1/0）"
echo "H-l 跨域回归断言锚点          = $HL_GUARD （目标 5：邮快件不进商城域 + 真商城单照旧）"
echo "H-m1 导航目标认后端当前站     = $HM_TGT （目标 1：targetIndex 的基序号来自 currentStationId）"
echo "H-m2 navOffset 写点 全体/体内 = $HM_NAV_ALL/$HM_NAV_IN （目标 3/3 且相等：推进只有一个入口，非 GPS 自动）"
echo "H-m3 显示层当前站索引          = $HM_CUR （目标 1：进度/第N站只认后端，不并成 targetIndex）"
echo "H-m4 到站按钮提交导航目标      = $HM_ARR （目标 1：与小程序 arriveAtStation 同源，改它要先重做三方核对）"
echo "H-m5 推进不动后端/不出网的断言  = $HM_NET （目标 1：H-m 那一族的行为半边）"

fail=0
[ "$IA" = 10 ] || { echo "× I-a 有司机写路径没经单一出口（少了=有人绕开闸门）"; fail=1; }
[ "$IB" = 10 ] || { echo "× I-b 闸门调用点不是 10（多了=凭空多设出口，两处语义会打架）"; fail=1; }
[ "$IC" = 0 ] || { echo "× I-c 周期位置上报被接进了幂等闸，重复点位会被当重复动作丢掉"; fail=1; }
[ "$ID" = 1 ] || { echo "× I-d 闸门不再用 tryLock，重复提交会被排队而不是丢弃"; fail=1; }
[ "$ID2" = 0 ] || { echo "× I-d 出现了 Mutex.withLock：排队等于第一次完成后照发给后端"; fail=1; }
[ "$IE" = 6 ] || { echo "× I-e 回执里认不出 notSent 的分支变少了，被拦下会被说成已提交"; fail=1; }
[ "$IF" = 0 ] || { echo "× I-f 被拦下的请求在界面上谎称已提交后端"; fail=1; }
[ "$IG" = 0 ] || { echo "× I-g 按钮旗标又回到 work{} 内置位，防连点依赖调度器"; fail=1; }
[ "$IH" = 0 ] || { echo "× I-h 往报文里塞了后端不收的唯一键字段（后端的键由服务端解析器自算，任何版本都不收客户端传的键）"; fail=1; }
[ "$II" = 1 ] || { echo "× I-i Ack 不再带 notSent，页面无法区分'没发出去'和'后端拒绝'"; fail=1; }
[ "$IJ" = 11 ] || { echo "× I-j 幂等用例不是 11 条（删用例必须让判据变红）"; fail=1; }
[ "$UA" = 2 ] || { echo "× U-a 有用户端 create 没经闸门（少了=绕闸直发，多了=凭空多设出口）"; fail=1; }
[ "$UB" = 2 ] || { echo "× U-b 用户闸调用点不是 2"; fail=1; }
[ "$UC" = 2 ] || { echo "× U-c 闸门实例数不是 2：并成一把就跨域互相挡，拆成三把就是有人另开了一条写路径"; fail=1; }
[ "$UC2" = 2 ] || { echo "× U-c2 userCreateGate 使用面变了（$UC2）：用户路径借用司机闸，分域就破了"; fail=1; }
[ "$UC3" = 2 ] || { echo "× U-c3 actionGate 使用面变了（$UC3）：司机路径借用用户闸，同上"; fail=1; }
[ "$UD" = 3 ] || { echo "× U-d 键的维度少了一个：少端点会'商城单挡寄货单'，少身份会'换账号继承窗口'，少报文会'改了数量还被拦'"; fail=1; }
[ "$UE" = 2 ] || { echo "× U-e 用户端认不出 notSent 的页面变少，被拦下的点击会被说成已下单"; fail=1; }
[ "$UF" = 0 ] || { echo "× U-f 被拦下的点击在用户端界面上谎称已提交"; fail=1; }
[ "$UG" = 1 ] || { echo "× U-g 键不再是纯摘要返回（$UG）：明文收货信息会进进程级键表"; fail=1; }
[ "$UH" = 11 ] || { echo "× U-h 用户端 create 用例不是 11 条（删用例必须让判据变红）"; fail=1; }
[ "$UI_ORDER" = 2 ] || { echo "× U-i 用户域'下单类' create 端点数不是 2（$UI_ORDER）：新增了下单接口没进对表"; fail=1; }
[ "$UI_ORDER" = "$UB" ] || { echo "× U-i 下单端点数($UI_ORDER) 与走闸数($UB) 不等：有人绕闸直发 Api.post"; fail=1; }
# U-j 不判红（后端在这两处零 @Idempotent，本机单方面拦就是替后端发明规则），但**必须可见**：
# 只登记不判 = 数字变了没人看，所以这里把"预期 4"写成一条软提示，变了就打印，不变不响。
[ "$UI_ALL" = 4 ] || echo "! U-j 用户域 create 端点总数变成 $UI_ALL（预期 4）：不判红，但要逐条核后端那两处的 @Idempotent 是否已加"

# ---------- H-*（F-3 ⑦⑨）----------
[ "$HA" = 1 ] || { echo "× H-a 到站不再核'后端有没有给站点编号'（0L 会被发给 @NotNull 的 stationId，司机看到的是后端拒绝而不是本机缺项）"; fail=1; }
[ "$HB" = 1 ] || { echo "× H-b docked 不再认 notSent：一个字节都没上网络的那次点击仍会把画面翻成停靠作业"; fail=1; }
[ "$HC" = 0 ] || { echo "× H-c 到站函数体里出现裸的 docked 翻转（幂等闸拦下也照翻，与回执'显示的是后端当前状态'矛盾）"; fail=1; }
[ "$HD" = 1 ] || { echo "× H-d 核销不再判空白取件码（Api 会把空串整个键丢掉，发出去的是没有 pickupCode 的请求）"; fail=1; }
[ "$HE1" = 1 ] || { echo "× H-e 到站早退变成静默 return（司机点了没任何反应）"; fail=1; }
[ "$HE2" = 1 ] || { echo "× H-e 核销早退变成静默 return，同上"; fail=1; }
[ "$HF" = 2 ] || { echo "× H-f 报文字段名被改动（$HF）：早退闸门只该决定发不发，不该顺手改 wire（后端 ReqVO 的键是 stationId/pickupCode）"; fail=1; }
[ "$HG" = 5 ] || { echo "× H-g 诚实化用例不是 5 条（删用例必须让判据变红）"; fail=1; }
[ "$HH" = 3 ] || { echo "× H-h '真的发出去了'那一侧的对照掉了：早退判据会退化成'这个动作永远不发'也测不出来"; fail=1; }
[ "$HI" = 6 ] || { echo "× H-i 夹具自检没被五条用例都调上（$HI）：坏夹具会静默变成'没有站点'，把早退读成通过"; fail=1; }
[ "$HJ_STOPS" = 3 ] || { echo "× H-j 夹具里 stops 的形状数变了（$HJ_STOPS）：三站/空数组/单站三处班次夹具少一处，多半是有人把它退回别名 stations"; fail=1; }
[ "$HJ_ALIAS" = 0 ] || { echo "× H-j 夹具里又出现无生产者别名（$HJ_ALIAS 处 stations/id/plate/senderName）：别名形状绿着也只证明'别名可用'，不证明真机路径"; fail=1; }
[ "$HJ_PLATE" = 1 ] || { echo "× H-j profile 夹具的车牌键不再是 plateNo（$HJ_PLATE）：AppDriverProfileRespVO:19 与 Models.kt:1374 都只有这一个键"; fail=1; }
[ "$HK_PROD" = 1 ] || { echo "× H-k isProduct 不再是'只认 bizType'那一行（$HK_PROD）：后端商城单的唯一判据是 bizType=PRODUCT（DriverAppServiceImpl:352）"; fail=1; }
[ "$HK_LEAK" = 0 ] || { echo "× H-k 枚举码 3（邮快件）又回流进商城单判据（$HK_LEAK）：没面单号的邮快件会被送进 /driver/product-*，跨业务域（硬规则 1）"; fail=1; }
[ "$HL_GUARD" = 5 ] || { echo "× H-l 跨业务域回归断言锚点掉了（$HL_GUARD）：只剩源码正确、没有用例能红，改回去是静默的"; fail=1; }

[ "$HM_TGT" = 1 ] || { echo "× H-m1 导航目标不再由后端 currentStationId 推出（$HM_TGT）：那一层一旦换成本机排序或 GPS 就近站，'页面算目的地'就成了没有源头的自算"; fail=1; }
[ "$HM_NAV_ALL" = 3 ] || { echo "× H-m2 navOffset 写点总数不是 3（$HM_NAV_ALL）：多了=被别处（初始化/位置回调）改写，目标站会随 GPS 自己走；少了=推进或末站归位被删"; fail=1; }
[ "$HM_NAV_IN" = 3 ] || { echo "× H-m2 「继续行驶」体内少了推进（$HM_NAV_IN）：司机点下去不再换导航目标，或上限归位被删（会一直指向同一站）"; fail=1; }
[ "$HM_NAV_ALL" = "$HM_NAV_IN" ] || { echo "× H-m2 体内 $HM_NAV_IN 条、全文件 $HM_NAV_ALL 条：有写点跑到 continueToNextStation 外面去了，推进不再是司机的一次显式动作"; fail=1; }
[ "$HM_CUR" = 1 ] || { echo "× H-m3 显示层的当前站索引不再只认后端 currentStationId（$HM_CUR）：并进导航目标就等于本机视图冒充后端状态（人还没到，画面已到第 N 站）"; fail=1; }
[ "$HM_ARR" = 1 ] || { echo "× H-m4 到站按钮提交的站点来源改了（$HM_ARR）：这一处与小程序 workbench.js:797-810 同源，改它必须先重做三方核对，不许顺手改"; fail=1; }
[ "$HM_NET" = 1 ] || { echo "× H-m5 「继续行驶不动后端当前站/不出网」那条行为断言没了：结构判据看不见回写，这条一掉就等于那层保护只剩纸面"; fail=1; }

if [ "$fail" = 0 ]; then echo "幂等判据通过"; else echo "幂等判据失败"; fi
exit "$fail"
