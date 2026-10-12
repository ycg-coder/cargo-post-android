#!/usr/bin/env bash
# #55「查件票面」结构判据的负控制（配 tools/check_parcel_ticket_probes.sh）
# 为什么这本必须有：PT-b / PT-d / PT-e / PT-f / PT-g / PT-h / PT-i / PT-j / PT-k 里有多个"目标 0"或"恰好 N"——
# 0 命中必须是"真的没有"，不能是 grep 形状写错、段没取到、或反证被一起抹掉；每条都要人为造一次破口证明它会红。
# ⚠ 沙盒是**整目录 copy**（D-084 那条教训：只拷几个文件的沙盒会在新增文件时让基线先红），
#   而且注入器落空与"判据不判"必须分成两种话 —— 落空打"锚点没命中"，不许混进"判据仍然绿"。
# 用法：bash tools/parcel_ticket_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/pt_neg.$$
CLEAN=/tmp/pt_neg_clean.$$
PKG=com/shanxiang/buspost
PROBE="$SRC/tools/check_parcel_ticket_probes.sh"

mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
M=$SC/app/src/main/java/$PKG
RT=$M/nav/Routes.kt
BR=$M/ui/BusPostRoot.kt
TS=$M/ui/user/TraceScreen.kt
PT=$M/ui/user/ParcelTicket.kt
PS=$M/ui/user/ParcelTicketScreen.kt
MD=$M/data/model/Models.kt
RP=$M/data/repo/Repo.kt
DD=$M/data/repo/DemoData.kt
CT=$SC/app/src/test/java/$PKG/ui/user/ParcelTicketContractTest.kt
for f in "$RT" "$BR" "$TS" "$PT" "$PS" "$MD" "$RP" "$DD" "$CT"; do
  [ -f "$f" ] || { echo "✘ 沙盒缺 $f：判据扫得到、副本没有，本轮负控全部作废"; exit 2; }
done
cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); base=$?
if [ "$base" != 0 ]; then echo "✘ 基线未通过，负控结果一律不采信"; printf '%s\n' "$out" | grep -a '^×' | head -5; exit 2; fi
echo "✔ 基线：副本原样 exit 0"

MISS=0
ok=0; bad=0
# 注入器：读写都 newline=''（不改行尾），锚点必须**恰好一次**，否则记 MISS（D-084 第 ①② 条）
sub() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(old) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(old), old[:60]))
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
PY
}
# 删行：整行连行尾一起删（撤注册、撤入口这类破口）
delline() { python - "$1" "$2" <<'PY' || { MISS=1; return 1; }
import sys
p, needle = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8', newline='').read()
i = s.find(needle)
if i < 0 or s.count(needle) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(needle), needle[:60]))
j = s.find('\n', i)
open(p, 'w', encoding='utf-8', newline='').write(s[:i] + s[j + 1:])
PY
}
ins_after() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, anchor, add = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(anchor) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(anchor), anchor[:60]))
NL = '\r\n' if '\r\n' in s else '\n'
j = s.index('\n', s.find(anchor)) + 1
open(p, 'w', encoding='utf-8', newline='').write(s[:j] + add.replace('\n', NL) + NL + s[j:])
PY
}

check() { # $1=编号 $2=期望点名的判据（× PT-x 前缀） $3..=注入（inject-file-anchor-new）
  local id="$1" want="$2" ores rc out
  shift 2
  if ! "$@"; then
    echo "✘ $id 锚点没命中/不唯一，破口根本没注入（这条负控不成立）"
    bad=$((bad + 1)); MISS=0
    rm -rf "$SC"; cp -r "$CLEAN" "$SC"
    return
  fi
  out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = 0 ]; then
    echo "✘ $id 判据仍然绿（破口没咬住），这条判据等于没判据"; bad=$((bad + 1))
  elif printf '%s\n' "$out" | grep -qF "$want"; then
    echo "✔ $id 红且原因对得上（$want）"; ok=$((ok + 1))
  else
    echo "✘ $id 红了但不是那一条（缺 $want；实为 $(printf '%s\n' "$out" | grep -a '^×' | head -1)）"; bad=$((bad + 1))
  fi
  MISS=0
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# del_after：删掉**唯一锚点之后**第一处含 needle 的整行。
# 本页三处 `BackToTraceRow { nav.navigate(Routes.trace(no)) }` 里有两处缩进完全相同
# （Failed / Absent 两格），按"第一处"或按字面唯一性都没法只拆其中一格 —— 与 MR-i 那次同形。
del_after() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, anchor, needle = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(anchor) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(anchor), anchor[:60]))
k = s.find(anchor)
j = s.find(needle, k)
if j < 0: sys.exit("NEEDLE-MISS: " + needle[:60])
ls = s.split('\n')
li = s[:j].count('\n')
if needle not in ls[li]: sys.exit("NEEDLE-LINE-MISS")
del ls[li]
open(p, 'w', encoding='utf-8', newline='').write('\n'.join(ls))
PY
}

# check_miss：本书自己的自检 —— 拿一个树里不存在的锚点去注入，必须被记成"锚点没命中"，
# 而不是"判据仍然绿"。这两句话必须能分开说，否则"注入没发生"最像"批次跑完了"（D-084 第 ① 条）。
check_miss() {
  local id="$1"; shift
  if "$@"; then
    echo "✘ $id 注入器放过了一个不存在的锚点（后面每条 ✔ 都可能是假绿）"; bad=$((bad + 1))
  else
    echo "✔ $id 不存在的锚点被注入器当场拒绝（破口没被静默跳过）"; ok=$((ok + 1))
  fi
  MISS=0
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# ---------- PT-a 路由常量 / 注册 / BusRouteBody ----------
check PT-a1 "× PT-a 路由常量" sub "$RT" 'const val PARCEL_TICKET = "parcelTicket/{no}"' 'const val PARCEL_TICKET_V2 = "parcelTicketV2/{no}"'
check PT-a2 "× PT-a 单号构造器不在了" sub "$RT" 'fun parcelTicket(no: String) = "parcelTicket/" + encodeSegment(no)' 'fun parcelTicket(no: String) = "parcelTicket/" + no'
check PT-a3 "× PT-a 注册点不是恰好一处" delline "$BR" '            Routes.PARCEL_TICKET,'
check PT-a4 "× PT-a 有注册没走 BusRouteBody" sub "$BR" 'BusRouteBody { ParcelTicketScreen(' 'Box { ParcelTicketScreen('

# ---------- PT-b 入口 ----------
check PT-b1 "× PT-b 追踪页那个入口不是恰好一条" delline "$TS" '                        onClick = { nav.navigate(Routes.parcelTicket(no)) },'
check PT-b2 "× PT-b 全工程入口数=2" ins_after "$TS" \
  '                        onClick = { nav.navigate(Routes.parcelTicket(no)) },' \
  '                    BusButton(text = "再看一次票面", onClick = { nav.navigate(Routes.parcelTicket(no)) }, fullWidth = false)'

# ---------- PT-c 死通路接活 ----------
check PT-c1 "× PT-c 真调用数=0" sub "$PS" 'Repo.parcelTopology(number).toState()' 'Repo.trackParcel(number).toState()'
check PT-c2 "× PT-c 真调用数=2" ins_after "$PS" '            val s = Repo.parcelTopology(number).toState()' \
  '            val again = Repo.parcelTopology(number).toState()'

# ---------- PT-d 措辞只在纯层 ----------
check_miss PT-d0-self sub "$PS" 'private const val SCREEN_TIP_NOT_IN_TREE' 'x'
check PT-d2 "× PT-d 页面自己写了措辞" sub "$PS" '                    ErrorState(page.body, onRetry = { vm.load(no) })' '                    ErrorState("票面没取到，请稍后再试", onRetry = { vm.load(no) })'
check PT-d3 "× PT-d 反证掉了" delline "$PT" 'private const val FAILED_TITLE = "票面没取到"'

# ---------- PT-e 口径只在纯层、页面不许自己拼数 ----------
check PT-e1 "× PT-e 页面重抄了换乘等待口径" sub "$PS" '                TicketCard("整单", ticketHeaderRows(tr))' \
  '                TicketCard("整单（含每次 20 分钟换乘等待）", ticketHeaderRows(tr))'
check PT-e2 "× PT-e 页面自己拼了公里数" sub "$PS" '                        TicketCard("整单", ticketHeaderRows(tr))' \
  '                        TicketCard("整单", ticketHeaderRows(tr) + ("总里程" to "27.4 km"))'
check PT-e3 "× PT-e 纯层口径分支不齐" sub "$PT" '"（含估算段，不是真实道路里程）"' '"（含估算段）"'
check PT-e4 "× PT-e 纯层口径分支不齐" sub "$PT" '"（各段真实道路里程相加）"' '"（真实里程）"'
check PT-e5 "× PT-e 纯层口径分支不齐" sub "$PT" '"（口径未给）"' '"（不知道）"'
check PT-e6 "× PT-e 纯层口径分支不齐" sub "$PT" '"（后端口径含每次换乘 20 分钟；换乘次数未给，是否已计入未知）"' '"（不写字数也无所谓）"'

# ---------- PT-f 交接只认真键（段内计数） ----------
check PT-f1 "× PT-f 交接的真键读法不齐" sub "$MD" 'fromPlate = o.multiStr("fromPlateNo")' 'fromPlate = o.multiStr("fromPlate", "vehiclePlate")'
check PT-f2 "× PT-f 交接的真键读法不齐" sub "$MD" 'time = o.reportTimeOf("handoverCompletedAt", "handoverStartedAt", "arrivedAt")' 'time = o.timeOf("handoverTime", "confirmTime")'
check PT-f3 "× PT-f 交接解析里出现白名单外的键" sub "$MD" 'toDriver = o.multiStr("toDriverName")' 'toDriver = o.multiStr("toDriverName", "nextDriver")'
ITEM_ANCHOR=$('printf' 'itemCount = o.multiNumOrNull("itemCount")?.toInt(),\n            statusName = o.multiStr("statusName"),')
check PT-f4 "× PT-f 反证掉了（同段 itemCount" sub "$MD" "$ITEM_ANCHOR" 'itemCount = o.multiStr("itemCount"),
            statusName = o.multiStr("statusName"),'

# ---------- PT-g 时间线真键与时刻归一 ----------
check PT-g1 "× PT-g 时间线真键/归一函数不齐" sub "$RP" 'label = it.multiStr("eventTypeName", "label", "title", "statusLabel", "nodeName")' 'label = it.multiStr("label", "title", "statusLabel", "nodeName")'
check PT-g2 "× PT-g Repo 里又出现裸切串" sub "$RP" 'time = it.nodeTimeOf("eventTime", "time", "createTime", "nodeTime"),' 'time = it.multiStr("eventTime", "time", "createTime", "nodeTime").substringAfter('"'"' '"'"').take(5),'
check PT-g3 "× PT-g 反证掉了（归一函数里那处切串" sub "$MD" 'fun JSONObject.nodeTimeOf(vararg keys: String): String = ts(multiStr(*keys)).substringAfter('"'"' '"'"').take(5)' 'fun JSONObject.nodeTimeOf(vararg keys: String): String = ts(multiStr(*keys))'

# ---------- PT-h 可空键不许塌成结论 ----------
check PT-h1 "× PT-h 可空键又被塌成 0" sub "$MD" 'minutes = o.multiNumOrNull("durationMinutes")?.toInt(),' 'minutes = o.multiNum("durationMinutes").toInt(),'
check PT-h2 "× PT-h 可空读法不齐" sub "$MD" 'val minutes: Int?,' 'val minutes: Int,'
check PT-h3 "× PT-h 可空键又被塌成 0" sub "$MD" "$ITEM_ANCHOR" 'itemCount = o.multiNum("itemCount").toInt(),
            statusName = o.multiStr("statusName"),'
check PT-h4 "× PT-h 可空读法不齐" sub "$MD" 'val itemCount: Int?,' 'val itemCount: Int,'
check PT-h5 "× PT-h chosen 缺席那一格没了" sub "$PT" 'null -> "后端没说（chosen 未给）"' 'null -> "后端标为不推荐"'

# ---------- PT-i 四格与兜底出口 ----------
check PT-i1 "× PT-i 四格 enum 声明不再是那四个值" sub "$PT" 'enum class ParcelTicketKind { Loading, Failed, Absent, Shown }' 'enum class ParcelTicketKind { Loading, Failed, Shown }'
check PT-i2 "× PT-i 四格出口不齐" sub "$PT" '        return ParcelTicketPage(ParcelTicketKind.Absent, ABSENT_TITLE, ABSENT_BODY, t, demo = state.demo)' '        return ParcelTicketPage(ParcelTicketKind.Shown, "票面", ABSENT_BODY, t, demo = state.demo)'
check PT-i3 "× PT-i 「回追踪页」出口不是恰好三处" del_after "$PS" '                ParcelTicketKind.Failed -> {' 'BackToTraceRow { nav.navigate(Routes.trace(no)) }'

check PT-i4 "× PT-i 兜底出口那个 composable 不在了" sub "$PS" 'private fun BackToTraceRow(onOpen: () -> Unit) {' 'private fun BackToTraceUnused(onOpen: () -> Unit) {'

# ---------- PT-j 初值 ----------
check PT-j1 "× PT-j 初值不再是「还没结论」" sub "$PS" 'var state by mutableStateOf(RemoteState<ParcelTrace>(loading = true))' 'var state by mutableStateOf(RemoteState<ParcelTrace>(data = null, success = true))'
check PT-j2 "× PT-j 初值被写成带数据" sub "$PS" 'var state by mutableStateOf(RemoteState<ParcelTrace>(loading = true))' 'var state by mutableStateOf(RemoteState<ParcelTrace>(data = ParcelTrace(orderNo = "x", status = 0, goodsName = "", nodes = emptyList(), legs = emptyList(), vehiclePlate = "", driverName = "", driverMobile = "", targetStation = "", etaMinutes = null, dataSource = "", vehicleGeo = null, polyline = emptyList(), stations = emptyList()), success = true))'

# ---------- PT-k 演示分面 ----------
check PT-k1 "× PT-k 演示分面闸门不齐" sub "$DD" '                val topo = path.endsWith("/send/topology")' '                val topo = true'
check PT-k2 "× PT-k 演示分面那道闸门整句不见了" sub "$DD" '                    if (!topo) return@let it' '                    if (false) return@let it'
check PT-k3 "× PT-k 演示交接段又发自造键 handoverTime" sub "$DD" '            "handoverCompletedAt" to "2026-09-28 11:05", "itemCount" to 6,' '            "handoverTime" to "2026-09-28 11:05", "itemCount" to 6,'
check PT-k4 "× PT-k 演示交接段的真键" sub "$DD" '"stationName" to "双河口换乘站", "fromPlateNo" to "渝A·8K231", "toPlateNo" to "渝B·5M107",' '"stationName" to "双河口换乘站", "fromPlate" to "渝A·8K231", "toPlate" to "渝B·5M107",'

# ---------- PT-l 用例簿 ----------
# ⚠ 改名不动 `@Test` 计数（PT-l 数的是注解行），所以这条必须把注解本身注释掉 —— 两行锚点才唯一。
check PT-l1 "× PT-l 契约用例少于 15 条" sub "$CT" "$(printf '%s\n%s' '    @Test' '    fun `取失败那一格绝不念成后端没给内容`')" "$(printf '%s\n%s' '    // @Test 负控注释掉一条' '    fun `取失败那一格绝不念成后端没给内容`')"
check PT-l2 "× PT-i 「回追踪页」出口不是恰好三处" delline "$PS" '                        BackToTraceRow { nav.navigate(Routes.trace(no)) }'

echo "---"
echo "计数：按名点名变红 $ok 条 / 不成立 $bad 条"
[ "$bad" = 0 ] || echo "✘ 有破口没被自己那条判据抓住，或注入根本没落"
[ "$bad" = 0 ]
