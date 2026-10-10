#!/usr/bin/env bash
# I-*/U-*/H-* 幂等与"缺项不出网"判据的负控制总表：逐条破口 → 确认脚本 exit 1 且报的是这一条。
# 只在 /tmp 副本上动手，工程真身不受影响。用法：bash tools/idempotency_negative_controls.sh
# ⚠ 一次只跑一个会改源码的负控脚本（⑬ 的过程纪律）；跑完必须看到"[恢复后] 幂等判据通过"。
# ⚠ I-* 与 U-* 分两本判（硬规则 1），但负控共用一个沙盒：同一轮里既证明司机侧的十道锁还咬得住，
#    也证明用户侧新加的那十道不是"怎么写都绿"。
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/idem_neg.$$
PR="$SRC/tools/check_idempotency_probes.sh"
A="$SC/app/src/main/java/com/shanxiang/buspost"
RP="$A/data/repo/Repo.kt"
GA="$A/data/repo/ActionGate.kt"
WB="$A/ui/driver/WorkbenchScreen.kt"
RT="$A/ui/driver/RoutesScreen.kt"
HO="$A/ui/driver/HandoverScreen.kt"
GD="$A/ui/user/GoodsDetailScreen.kt"
SD="$A/ui/user/SendScreen.kt"
TD="$SC/app/src/test/java/com/shanxiang/buspost/data/repo"
TG="$TD/ActionGateTest.kt"
TR="$TD/DriverActionIdempotencyContractTest.kt"
TU="$TD/UserCreateIdempotencyTest.kt"
SRCT="$SRC/app/src/test/java/com/shanxiang/buspost/data/repo"
# H-* 那一族扫的是 ui/driver 下的新用例簿（I-*/U-* 的三本仍在 $TD，两条路径互不覆盖）
HTD="$SC/app/src/test/java/com/shanxiang/buspost/ui/driver"
HT="$HTD/DriverActionHonestyContractTest.kt"
# H-k/H-l（本轮 #185）扫的是模型层与司机域旧用例簿：这两本不进沙盒的话，
# 破口会打在真身上（负控只许在 /tmp 改文件），或者 HK/HL 因为文件缺失而静默变 0 假绿。
MD="$A/data/model"
MODELS="$MD/Models.kt"
# ⚠ 变量名不能叫 SD：沙盒里 $SD 已经是 ui/user/SendScreen.kt（U-f 的注入靶），
#    同名会把用户域那个靶静默换成司机域用例簿 —— 两条负控会互相顶掉而看不出来。
SDIR="$SC/app/src/test/java/com/shanxiang/buspost/driver"
DST="$SDIR/DriverStateTest.kt"

rm -rf "$SC"; mkdir -p "$A/data/repo" "$A/data/model" "$A/ui/driver" "$A/ui/user" "$TD" "$HTD" "$SDIR"
cp "$SRC/app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt" "$RP"
cp "$SRC/app/src/main/java/com/shanxiang/buspost/data/repo/ActionGate.kt" "$GA"
cp "$SRC/app/src/main/java/com/shanxiang/buspost/data/model/Models.kt" "$MODELS"
# ui/driver **整目录**进沙盒：I-e 扫的是整个 $UI，只 copy 三个文件的话，每加一个司机页面
# 基线就会凭空少一个 notSent 分支（§54 加异常上报页时就是这样把基线弄红的）。
cp "$SRC"/app/src/main/java/com/shanxiang/buspost/ui/driver/*.kt "$A/ui/driver/"
# 同一条教训直接用在 U-e 上：用户域页面也在涨（商品详情/寄货/订单…），只 copy 两个文件
# 就等于"每加一个用户页面基线自己掉一格"，负控轮还没跑就红了。
cp "$SRC"/app/src/main/java/com/shanxiang/buspost/ui/user/*.kt "$A/ui/user/"
cp "$SRCT/ActionGateTest.kt" "$TG"
cp "$SRCT/DriverActionIdempotencyContractTest.kt" "$TR"
cp "$SRCT/UserCreateIdempotencyTest.kt" "$TU"
cp "$SRC/app/src/test/java/com/shanxiang/buspost/ui/driver/DriverActionHonestyContractTest.kt" "$HT"
cp "$SRC/app/src/test/java/com/shanxiang/buspost/driver/DriverStateTest.kt" "$DST"
for f in "$RP" "$GA" "$WB" "$RT" "$HO" "$GD" "$SD" "$TG" "$TR" "$TU" "$HT" "$MODELS" "$DST"; do
    [ -f "$f" ] || { echo "✘ 沙盒缺文件：$f（基线凑不齐，下面每一条都会假绿）"; exit 2; }
done
CLEAN=/tmp/idem_neg_clean.$$; cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

ctl() { # $1=编号 $2=说明
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  # 匹配带尾随空格（"^× U-c "）而不是 "^× U-c"：U-c / U-c2 / U-c3 是三个不同判据，
  # 少一个空格，"只让 U-c2 变红"的破口会被当成 U-c 也咬住了 —— 那是假 ✔。
  if [ "$code" = 1 ] && echo "$out" | grep -q "^× $1 "; then
    echo "✔ $1  $2 → exit 1：$(echo "$out" | grep "^× $1 " | head -1)"
  elif [ "$code" = 1 ]; then
    echo "△ $1  $2 → exit 1，但抓到的是别的判据：$(echo "$out" | grep '^×' | head -1)"
  else
    echo "✘ $1  $2 → 负控制失效（exit $code），这条判据等于没判据"
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
inject() { python - "$1" "$2" "$3" <<'PY'
import sys
p, anchor, add = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read(); i = s.find(anchor)
if i < 0: sys.exit("ANCHOR-MISS: " + anchor)
j = s.index('\n', i) + 1
open(p, 'w', encoding='utf-8').write(s[:j] + add + '\n' + s[j:])
PY
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | tail -1)"
# 基线不绿就立刻收工：否则每条破口都会因为"判据本来就红"而打出 ✔，整轮负控是假的
[ "$base_code" = 0 ] || { echo "✘ 基线未通过（exit $base_code），负控结果一律不采信"; echo "$BASE" | grep '^×' | head -5; exit 2; }

# I-a/I-b 把发车从闸门上摘下来（同一出口散了，两条判据应当同时掉到 9）
sed -i 's|= gatedAction("depart:|= ungated("depart:|' "$RP"; ctl I-a "发车绕开单一出口"
# I-b 反向：凭空多出第二个走闸出口，却不在那十个动作里
inject "$RP" "private val actionGate" '    private fun extra() = gatedAction("x:1") { Ack(false) }'; ctl I-b "多出第二个闸门调用点"
# I-c 把周期位置上报接进闸门 = 重复点位被当重复动作丢掉（偷点）
sed -i 's|suspend fun reportLocation(shiftId: Long?, geo: Geo, speedKmh: Double): Ack =|suspend fun reportLocation(shiftId: Long?, geo: Geo, speedKmh: Double): Ack = gatedAction("loc:$shiftId") {|' "$RP"; ctl I-c "位置上报被接进幂等闸"
# I-d tryLock → withLock：排队等于第一次完成后照样发给后端
sed -i 's|if (!mutex.tryLock()) return GateResult.Duplicate|mutex.withLock {}|' "$GA"; ctl I-d "闸门改成排队语义"
# I-e 交接页不再认 notSent
sed -i '/ack.notSent -> "同一个段动作/s|ack\.notSent|ack.demo|' "$HO"; ctl I-e "页面认不出 notSent"
# I-f 被拦下的那一支谎称已提交后端
sed -i 's|本次请求未发给后端（同一个动作还在处理中），显示的是后端当前状态|已提交后端，班次与任务状态已按后端回读刷新|' "$WB"; ctl I-f "notSent 分支谎称已提交后端"
# I-g 旗标退回 work{} 内部才置位
python - "$RT" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
s = s.replace('        busy = true\n        work(reset = false) {', '        work(reset = false) {\n            busy = true', 1)
open(p, 'w', encoding='utf-8').write(s)
PY
ctl I-g "防连点重新依赖调度器时序"
# I-h 红线：往报文里塞后端不收的唯一键（AppDriverController 零 @Idempotent，没有这个键）
inject "$RP" "suspend fun depart(shiftId: Long)" '    private fun fake() = mapOf("clientRequestId" to System.nanoTime())'; ctl I-h "自造幂等键进报文"
# I-i Ack 上的可判别字段没了，页面只能靠文案猜
sed -i 's|val notSent: Boolean = false,|val notSentLike: Boolean = false,|' "$RP"; ctl I-i "回执不再区分没发出去与后端拒绝"
# I-j 注释掉一条用例，但 @Test 字样还在（考行首锚定）
python - "$TG" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
ctl I-j "用例被注释掉但 @Test 字样还在"
# I-j 反向：整条用例真删
python - "$TR" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('    @Test\n    fun `连点两次发车')
j = s.find('\n    @Test', i + 10)
open(p, 'w', encoding='utf-8').write(s[:i] + s[j + 1:])
PY
ctl I-j "整条仓库层用例被删（与上一条是同一判据的另一条形态）"

# ==================== U-*（F-2 第 6 步：用户端 create 幂等） ====================
# 每条都只冲着自己那条判据去；司机侧 I-a/I-b 在任何一条 U-* 破口下都必须还是 10，
# 这正是"分两本判"要证明的事：用户域出事不许把司机域的数顶掉。

# U-a 寄货下单绕开闸门（UA 2→1，UB 同时掉到 1：首步与出口数一起对不上）
sed -i 's|return gatedUserCreate(path, form) {|return ungatedSend(path, form) {|' "$RP"; ctl U-a "寄货 create 绕开用户闸"
# U-b 反向：凭空多出第三个走闸出口（UA 仍 2，UB 2→3）
inject "$RP" "private suspend fun gatedUserCreate" '    private fun extraUserCreate(path: String) = gatedUserCreate(path, mapOf()) { Ack(demo = false) }'; ctl U-b "多出第二个用户闸调用点"
# U-c 两域并成一把闸：把用户闸做成司机闸的别名（UC 2→1；顺带 U-c3 顶到 3 —— 分域已破）
sed -i 's|private val userCreateGate = ActionGate()|private val userCreateGate = actionGate|' "$RP"; ctl U-c "用户域复用司机域那把闸（实例只剩一把）"
# U-c 反向形态：删掉用户闸实例、gatedUserCreate 改指司机闸（同一判据的另一条长相）
python - "$RP" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
s = s.replace('    private val userCreateGate = ActionGate()\n', '', 1)
s = s.replace('when (val r = userCreateGate.submit(', 'when (val r = actionGate.submit(', 1)
open(p, 'w', encoding='utf-8').write(s)
PY
ctl U-c "彻底删掉用户闸实例，两处 create 全借司机闸（同一判据的另一条形态）"
# U-c2 用户路径借用司机闸但两把实例都还在：只有使用面能暴露
sed -i 's|when (val r = userCreateGate.submit(|when (val r = actionGate.submit(|' "$RP"; ctl U-c2 "用户 create 借用司机闸"
# U-c3 司机路径借用用户闸（对称方向，同一类分域破坏）
sed -i 's|when (val r = actionGate.submit(key|when (val r = userCreateGate.submit(key|' "$RP"; ctl U-c3 "司机动作借用用户闸"
# U-d 键丢掉"登录身份"这一维：换账号会继承上一个账号的 10 秒窗口（后端键里有 userId，这里没有就不同源）
sed -i '/digest.update(Prefs.mobile/d' "$RP"; ctl U-d "键少一个维度：身份不进摘要"
# U-e 商品下单页不再认 notSent（拿去和 demo 混判 = 被本地拦下也说成后端拒绝）
sed -i 's|if (ack.notSent) {|if (ack.demo) {|' "$GD"; ctl U-e "商品页认不出 notSent"
# U-f 被拦下的那一支谎称已提交后端
sed -i 's|本次点击没有发给后端；请到「我的寄货」核对|已提交后端，等待后端处理；请到「我的寄货」核对|' "$SD"; ctl U-f "用户端 notSent 分支谎称已提交后端"
# U-g 键不再只存摘要：明文手机号进进程级键表（键表活得比页面久，这是个人信息留存问题）
sed -i 's|return "create:\$hex"|return "create:$mobile"|' "$RP"; ctl U-g "键里塞明文个人信息"
# U-h 用户端用例被注释掉但 @Test 字样还在（考行首锚定，与 I-j 同一手法）
python - "$TU" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
ctl U-h "用户端用例被注释掉但 @Test 字样还在"
# U-h 反向：整条用例真删
python - "$TU" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('    @Test\n    fun `连点五次同一份订单')
j = s.find('\n    @Test', i + 10)
open(p, 'w', encoding='utf-8').write(s[:i] + s[j + 1:])
PY
ctl U-h "整条用户端用例被删（与上一条是同一判据的另一条形态）"
# U-i 新增一条下单接口却没走闸：端点数与走闸数一不等就响（这是"以后有人加 create"唯一能兜住的口子）
inject "$RP" "private val userCreateGate" '    private fun thirdCreateOrder() { val path = "$T/group/create" }'; ctl U-i "新下单接口没进对表（端点数≠走闸数）"
# U-j 是**登记项不是判据**（刻意不判红：feedback/address 两处后端零 @Idempotent，本机单方面拦就是替后端发明规则），
# 因此它没有、也不该有负控制 —— 能给它写负控的前提是它会让脚本 exit 1，而那正是我们不要的行为。
# 这条按 §八 ㉒ 的诚实纪律写在这里，不假装"22 条判据全有负控"。
echo "◦ U-j  无负控制：登记项不判红，写了负控就等于要求它判红"
# ==================== H-*（F-3 ⑦⑨：司机写路径"缺项即不出网"） ====================
# 与 I-*/U-* 的分工要分清楚：I-* 管"同一动作不重发"（闸门），H-* 管"闸门之前本机就该知道自己缺项"。
# 掉了格子的旁证逐条写在注释里，不拿它当本条的证据。

# H-a 把到站的缺项早退退化成永不成立：0L 会直接发给后端 @NotNull 的 stationId
sed -i 's|if (sid <= 0L) {|if (sid < 0L) {|' "$WB"; ctl H-a "到站不再核'后端有没有给站点编号'"
# H-b docked 不再认 notSent（同一破口会顺带把 I-e 从 6 顶到 5：那一句 ack.notSent 正是本轮新增的消费点，
#      这里采信的仍是 ^× H-b 那一行，I-e 的掉落只作为旁证登记）
sed -i 's|if (!ack.notSent) docked = !isTerminal|docked = !isTerminal|' "$WB"; ctl H-b "闸门外没有请求，画面照样翻成停靠作业"
# H-c 同一函数体内再补一次裸翻转（守卫还在，但多了一条不认 notSent 的推进）
inject "$WB" "if (!ack.notSent) docked = !isTerminal" "            docked = true"; ctl H-c "到站体内混进裸的 docked 翻转"
# H-d 核销不再判空白取件码：Api 出网前把空串整个键丢掉，发出去的是没有 pickupCode 的请求
sed -i 's|if (code.trim().isEmpty()) {|if (code.length() < 0) {|' "$WB"; ctl H-d "空白取件码照样出网"
# H-e 早退变成静默 return（司机点了没任何反应）—— 只删回执那一行，守卫结构留着
sed -i '/receipt = "后端没给本站编号/d' "$WB"; ctl H-e "早退不再留回执"
# H-f 顺手改报文：把后端的键名 stationId 改掉（早退闸门只该决定发不发，不该动 wire）
sed -i 's|"stationId" to stationId|"stationNo" to stationId|' "$RP"; ctl H-f "报文字段名被改动"
# H-g 用例被注释掉但 @Test 字样还在（考行首锚定，与 I-j/U-h 同一手法）
python - "$HT" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
ctl H-g "诚实化用例被注释掉但 @Test 字样还在"
# H-h 抽掉"真的发出去了"那一侧的对照（工作台 notSent 那条）：只剩早退断言时，
#      "这个动作永远不发"这种退化不会被任何一条判据抓到
sed -i '/拦下后只该多 5 条回读/d' "$HT"; ctl H-h "反向对照被删，早退退化成'永远不发'也测不出来"
# H-i 一条用例退回"只等 loading 转完"，不再核夹具本身有没有干净加载
python - "$HT" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
old = 'awaitLoaded(vm, expectStations = 0)'
i = s.find(old)
if i < 0: sys.exit("ANCHOR-MISS: " + old)
open(p, 'w', encoding='utf-8').write(s[:i] + 'awaitUntil { !vm.loading }' + s[i + len(old):])
PY
ctl H-i "夹具自检被用例丢掉（坏夹具会静默变成'没有站点'，把早退读成通过）"

# ---- H-j/H-k/H-l（本轮 #184/#185 的判据层；同轮反向注入） ----
# H-j 形态①：班次夹具整体退回自造别名 stations（客户端 multiArr 认别名 ⇒ gradle 全绿，只有这条会红）
sed -i 's|"stops":\[|"stations":[|g' "$HT"; ctl H-j "班次夹具退回别名 stations（别名形状证明的是'别名可用'，不是真机路径）"
# H-j 形态②：profile 夹具的车牌键退回 plate（Models.kt:1374 只认 plateNo，这一支在真机上读不到车牌）
sed -i 's|"plateNo":|"plate":|' "$HT"; ctl H-j "车牌键退回已删除的别名 plate（同一判据的另一条长相）"
# H-k 把枚举码那一支注回 isProduct：3 在后端是邮快件，没面单号的邮快件会被分流进 /driver/product-*
python - "$MODELS" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
old = 'val isProduct: Boolean get() = bizType.equals("PRODUCT", true)'
new = 'val isProduct: Boolean get() = bizType.equals("PRODUCT", true) || orderType == 3 && mailNo.isEmpty()'
if old not in s: sys.exit("ANCHOR-MISS: " + old)
open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
PY
ctl H-k "邮快件枚举码回流进商城单判据（跨业务域，硬规则 1）"
# H-k 反向形态：整个条件被换掉（比如改成只认 orderType == 4）—— 单条件锚点掉到 0
sed -i 's|val isProduct: Boolean get() = bizType.equals("PRODUCT", true)|val isProduct: Boolean get() = orderType == 4|' "$MODELS"; ctl H-k "商城单判据改成只认 orderType=4（bizType 锚点没了：后端 :352 下发的正是 bizType）"
# H-l 抽掉那两条跨域断言：源码正确但没有用例能红，改回去是静默的
sed -i '/mailNoWaybill/d' "$DST"; ctl H-l "跨业务域回归断言被删（isProduct 写错枚举码时没有红用例兜着）"

# ---- H-m（本轮 #147 收尾「任务点统一模型」的判据层；同轮反向注入） ----
# 这一族钉的是"目的地有两层派生、各认谁"，改法都很像"顺手简化一下"，所以四条各自单独注入。
# H-m1 导航目标不再从后端 currentStationId 推（换成"永远以首站为基准"这一型）：班次状态与画面会一起错位
sed -i 's|val cur = s.stations.indexOfFirst { it.id == s.currentStationId }|val cur = s.stations.indexOfFirst { it.id == s.stations.firstOrNull()?.id }|' "$WB"; ctl H-m1 "导航目标不再认后端当前站（换成线路首站基准，到站提交会永远指向第一站）"
# H-m2 形态①：把推进量写到「到站」体内 —— 业务动作顺手改本机导航视图，
#      红线"禁止 GPS/状态回写自动完成业务"反过来那一型也成立：业务动作不该反过来推动视图。
inject "$WB" "    fun arrive(stationId: Long) {" '        navOffset += 1'; ctl H-m2 "navOffset 出现第二个写点（到站里推进，全体≠体内）"
# H-m2 形态②：删掉上限归位那一行 —— 全体与体内仍然相等（都是 2），等式抓不住，靠下限抓：
#      不写这条会以为"只要写点没跑出去就没事"，而"到末站后不再归位"本身就是行为缺陷。
sed -i '/navOffset = (s.stations.size - 1).coerceAtLeast(0)/d' "$WB"; ctl H-m2 "「继续行驶」少了上限归位（写点数掉到 2：相等但少了推进语义）"
# H-m3 显示层并进"下一站"（也就是跟着导航目标走）：人还没到，进度条/第 N/M 站/到站态就先翻
sed -i 's|val currentIdx = shift.stations.indexOfFirst { it.id == shift.currentStationId }|val currentIdx = shift.stations.indexOfFirst { it.id == shift.currentStationId } + 1|' "$WB"; ctl H-m3 "显示层当前站索引被并进'下一站'（本机视图冒充后端状态）"
# H-m4 到站按钮不再显式提交页面算出的目标站：这处耦合是三方核对的结论（与小程序 arriveAtStation 同源），
#      锚点一掉就意味着有人改了它却没人重做核对
sed -i 's|onArrive = { vm.arrive(vm.targetStationId()) },|onArrive = { vm.arrive(0L) },|' "$WB"; ctl H-m4 "到站按钮提交站点来源被改（三方核对锚点消失）"
# H-m5 抽掉那条行为断言：结构判据（H-m1..H-m4）看不见"点下去之后有没有回写后端那一格"，
#      断言一掉，这层保护就只剩纸面 —— 与 H-l 同一族教训（源码对、用例不会红 = 静默）
sed -i '/一条请求都不许多发/d' "$HT"; ctl H-m5 "「继续行驶不出网/不回写」的行为断言被删"

echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
