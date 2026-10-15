#!/usr/bin/env bash
# DV-* 判据（F-2 §五 数字默认值 A/B/C/D 分类，首要修复点 = 司机运营统计）的负控制总表：
# 逐条破口 → 确认判据簿 exit 1 且报的是这一条。只在 /tmp 副本上动手，工程真身不受影响。
# 用法：bash tools/default_value_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/dv_neg.$$
PR="$SRC/tools/check_default_value_probes.sh"
A="$SC/app/src/main/java/com/shanxiang/buspost"
MD="$A/data/model/Models.kt"
RP="$A/data/repo/Repo.kt"
DM="$A/data/repo/DemoData.kt"
ES="$A/ui/driver/EarningsScreen.kt"
TSD="$SC/app/src/test/java/com/shanxiang/buspost/data"
TE="$TSD/EarningsContractTest.kt"
# F-3 ② 轮的六族判据（DV-m…DV-s）扫这几个文件
CG="$A/ui/driver/CargoGauge.kt"
WB="$A/ui/driver/WorkbenchScreen.kt"
TG="$TSD/NoProducerKeyFamilyContractTest.kt"
# F-3 ⑬ 轮的路线/轨迹别名撤读判据（DV-t…DV-t6 + GUARD3）扫这几个文件：
# 两份行为用例是 DV-t5/DV-t6 的计数对象，也是 guard_rkeys 的**反证输入地**（别名键名只在测试报文里还留着）
RGT="$TSD/repo/RouteGuideContractTest.kt"
UTT="$TSD/UserTrackingContractTest.kt"
# 队列 #262 轮的任务族判据（DV-y…DV-y6u + GUARD4a…d）扫这两个文件：
# 解析体在 MissionStop.kt/Models.kt，幻影键名只留在契约用例的 fixture 里（那里同时是 guard_ykeys 的反证输入地）
MSP="$A/data/model/MissionStop.kt"
TSDM="$SC/app/src/test/java/com/shanxiang/buspost/data/model"
DTR="$TSDM/DriverTaskRespContractTest.kt"
# 队列 #269 轮的页首计数判据（DV-z…DV-z6 + GUARD5a/b）扫这两个文件：
# 页文件是判据扫描地，契约用例既是 DV-z6 的计数对象也是 guard_zsize 的**反证输入地**
# （`assertEquals(2, vm.sends.size)` 正是"本页条数"这个数的合法用法，判据扫的是页文件而不是它）
PSU="$A/ui/user/ParcelScreen.kt"
TSDU="$SC/app/src/test/java/com/shanxiang/buspost/ui/user"
PCTU="$TSDU/ParcelCountContractTest.kt"

rm -rf "$SC"
mkdir -p "$SC/app/src"
# DV-k 是**全项目**基数护栏：沙盒必须与工程真身同扫描范围，否则副本里基数天然偏小，护栏永远撞不到线
cp -r "$SRC/app/src/main/java" "$SC/app/src/main-java-tmp" 2>/dev/null
mkdir -p "$SC/app/src/main" && mv "$SC/app/src/main-java-tmp" "$SC/app/src/main/java"
mkdir -p "$SC/app/src/test" && cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
# DV-u/v/w/x 读的是 tools/default_value_census.py 的产出：沙盒不带 tools/ 就只能落回真身那本，
# 于是"把 census 的 SHAPE 规则改坏"这类注入永远不会红 —— 判据自己不会被自己免检。
cp -r "$SRC/tools" "$SC/tools"
CS="$SC/tools/default_value_census.py"
MD="$A/data/model/Models.kt"; RP="$A/data/repo/Repo.kt"; DM="$A/data/repo/DemoData.kt"; ES="$A/ui/driver/EarningsScreen.kt"
# DV-l 与两条 GUARD 扫这个测试文件，漏 copy 会让基线直接红（脚本自带 exit 2 拦截）
[ -f "$TE" ] || { echo "✘ 沙盒缺 $TE，DV-l/GUARD 的基线不成立"; exit 2; }
[ -f "$TG" ] || { echo "✘ 沙盒缺 $TG，DV-s/GUARD2 的基线不成立"; exit 2; }
[ -f "$CG" ] && [ -f "$WB" ] || { echo "✘ 沙盒缺 CargoGauge/Workbench，DV-q/DV-q2/DV-q3 的基线不成立"; exit 2; }
[ -f "$RGT" ] && [ -f "$UTT" ] || { echo "✘ 沙盒缺 RouteGuide/UserTracking 契约用例，DV-t5/DV-t6/GUARD3 的基线不成立"; exit 2; }
[ -f "$MSP" ] && [ -f "$DTR" ] || { echo "✘ 沙盒缺 MissionStop.kt 或 DriverTaskRespContractTest.kt，DV-y…DV-y6u/GUARD4a…d 的基线不成立"; exit 2; }
[ -f "$CS" ] || { echo "✘ 沙盒缺 tools/default_value_census.py，DV-u/DV-v/DV-w/DV-x 的基线不成立"; exit 2; }
[ -f "$PSU" ] && [ -f "$PCTU" ] || { echo "✘ 沙盒缺 ParcelScreen.kt 或 ParcelCountContractTest.kt，DV-z…DV-z6/GUARD5a…b 的基线不成立"; exit 2; }
CLEAN=/tmp/dv_neg_clean.$$; cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT
# 这本原先**根本没有失败计数**：ctl 把 ✘ 打出来就完事，末尾 echo 的退出码恒 0 ⇒
# 「批跑完了、这本 rc=0」与「这本有两条判据等于没判据」可以同时成立（D-116 第⑤条登记的 fail open）。
# 现在 ✘/△ 都进 FAIL 并落进退出码，末行按 名点名变红 OK/TOTAL 打数，不再靠人眼扫日志。
FAIL=0
OK=0
TOTAL=0

ctl() { # $1=编号 $2=说明
  local out code
  TOTAL=$((TOTAL + 1))
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" = 1 ] && echo "$out" | grep -q "^× $1"; then
    OK=$((OK + 1)); echo "✔ $1  $2 → exit 1：$(echo "$out" | grep "^× $1" | head -1)"
  elif [ "$code" = 1 ]; then
    echo "△ $1  $2 → exit 1，但抓到的是别的判据：$(echo "$out" | grep '^×' | head -1)"; FAIL=1
  else
    echo "✘ $1  $2 → 负控制失效（exit $code），这条判据等于没判据"; FAIL=1
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
drop() { python - "$1" "$2" <<'PY'
import sys
p, old = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8').read()
if old not in s: sys.exit("OLD-MISS: " + old)
open(p, 'w', encoding='utf-8').write(s.replace(old, '', 1))
PY
}
# 精确替换一处（用于改 tools/default_value_census.py 的正则常数：那些行里有引号，sed 的引号嵌套会把注入本身写坏）
subst() { python - "$1" "$2" "$3" <<'PY'
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s: sys.exit("OLD-MISS: " + old)
open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
PY
}
# 只替换指定 data class 构造体内的那一处，避免全文件 sed 把别的类的同名字段一起改掉（判据会串味）
sbysed() { python - "$1" "$2" "$3" "$4" <<'PY'
import sys, re
p, cls, old, new = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
s = open(p, encoding='utf-8').read()
m = re.search(r'^data class %s\b' % cls, s, re.M)
if not m: sys.exit("CLASS-MISS: " + cls)
end = s.index('\n) {', m.start())
body = s[m.start():end]
if old not in body: sys.exit("OLD-MISS: " + old + " @" + cls)
open(p, 'w', encoding='utf-8').write(s[:m.start()] + body.replace(old, new, 1) + s[end:])
PY
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | tail -1)"
# 基线不绿就立刻收工：否则每条破口都会因为"判据本来就红"而打出 ✔，整轮负控是假的
[ "$base_code" = 0 ] || { echo "✘ 基线未通过（exit $base_code），负控结果一律不采信"; echo "$BASE" | grep '^×' | head -5; exit 2; }

sed -i 's/today = o\.multiNumOrNull("todayAmount"),/today = o.multiNum("todayAmount"),/' "$RP";            ctl DV-a "收益解析退回非可空读法：缺键塌成 0 元"
sed -i 's/today = o\.multiNumOrNull("todayAmount"),/today = o.multiNumOrNull("todayAmount", "todayEarnings"),/' "$RP"; ctl DV-b "解析层把无生产者的 todayEarnings 写回候选名"
sed -i 's/"todayAmount" to 86.5, "totalAmount" to 24680.0,/"todayAmount" to 86.5, "totalAmount" to 24680.0, "weekAmount" to 452.0,/' "$DM"; ctl DV-c "演示面重新下发后端 0 生产者的 weekAmount（demo 又成第四个真相源）"
drop "$RP" '                        weightKg = it.multiNumOrNull("weightKg"),';                              ctl DV-d "删掉一个真实键读法（契约断裂还静默：明细重量整列消失）"
sbysed "$MD" Earnings "val today: Double? = null" "val today: Double = 0.0";                                 ctl DV-e "Earnings 数值字段退回非空默认值"
sbysed "$MD" EarningItem "val amount: Double? = null" "val amount: Double = 0.0";                            ctl DV-f "EarningItem 数值字段退回非空默认值"
drop "$MD" '    val todayText: String get() = today?.let { yuan(it) } ?: "未给"';                             ctl DV-g "删掉未给文案 getter（缺失态只剩 grep 判据，JVM 断不到）"
drop "$MD" '    val amountText: String get() = amount?.let { "¥${yuan(it)}" } ?: "金额未给"';                  ctl DV-g2 "删掉明细行金额 getter"
sed -i 's/InfoRow("累计收益", e\.totalText, valueColor = t\.price)/InfoRow("累计收益", formatPrice(e.total ?: 0.0), valueColor = t.price)/' "$ES"; ctl DV-h "页面裸读可空数值并自己补 0（能编译就说明未给态已被折叠）"
inject "$MD" 'fun JSONObject.timeOf(vararg keys: String)' 'private val dvkOnlyAliasProbe = JSONObject().multiNum("weekAmount")'; ctl DV-i "无生产者键在 main 源码别处复现（DV-i 扫全项目，不只收益解析体）"
drop "$MD" '    val millis = if (v.length in 12..14 && v.all { it.isDigit() }) v.toLongOrNull() else null';   ctl DV-j "丢掉 EpochMillis 分支：后端 13 位毫秒数原样进 UI"
inject "$MD" 'fun JSONArray.lineNames(): List<String>' 'private val dvkCountProbe = JSONObject().multiNum("dvkctl")'; ctl DV-k "数值非可空读法基数回涨（F-2 收到 132，F-3 ② 收到 116，#262 收到 115）"
python - "$TE" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
                                                                                                              ctl DV-l "EarningsContractTest 被注释掉一条（缺键塌 0 复发无人报警）"
# ===== F-3 ② 轮：六个「三面全无生产者键」族 =====
sed -i 's/plate = o\.multiStr("plateNo"),/plate = o.multiStr("plate"),/' "$MD";                              ctl DV-m "档案车牌读点退回自造名（真机整列空白，判据数不到 plateNo 就红）"
sed -i 's/plate = o\.multiStr("plateNo"),/plate = o.multiStr("plateNo", "vehiclePlate"),/' "$MD";            ctl DV-m2 "把无生产者的 vehiclePlate 写回档案解析候选名"
drop "$MD" '            legs = o.multiNumOrNull("legCount")?.toInt(),';                                       ctl DV-n "删掉 Candidate 的真实键 legCount（段数整列变 null 还静默）"
sed -i 's/recommended = o\.multiBoolOrNull("chosen"),/recommended = o.multiBoolOrNull("chosen", "best"),/' "$MD"; ctl DV-n2 "方案解析体回流自造名 best"
sed -i 's/val soldOut: Boolean get() = stock != null \&\& stock <= 0/val soldOut: Boolean get() = (stock ?: 0) <= 0/' "$MD"; ctl DV-o "售罄判定绕过 null：后端没给库存就锁死下单按钮"
inject "$MD" 'fun JSONObject.timeOf(vararg keys: String)' 'private val dvo2Probe = JSONObject().multiNum("originPrice")'; ctl DV-o2 "划线原价读法回流（凭空画一条假划线价）"
inject "$MD" 'fun JSONObject.timeOf(vararg keys: String)' 'private val dvpProbe = JSONObject().multiBool("needPhoto")'; ctl DV-p "拍照要求位又被读成后端事实（缺键塌 false = 把『没要求』讲成结论）"
sed -i 's/photoUrl = o\.multiStr("photoUrl", "handoverPhotoUrl"),/photoUrl = o.multiStr("photoUrl", "handoverPhotoUrl", "needPhoto"),/' "$MD"; ctl DV-p2 "交接解析块里重新出现要求位键名"
sed -i 's/^    loadedCount: Int?,/    loadedCount: Int,/' "$CG";                                             ctl DV-q "CargoGauge 件数口径退回非空（未给会画成确定百分比）"
sed -i 's/capacityPercent = vm\.profile?.freePercent(shift.loadedCount)/capacityPercent = 100.0/' "$WB";      ctl DV-q2 "Workbench 绕过 freePercent 直填 100.0（把未给说成满仓）"
inject "$WB" '                        capacityKg = vm.profile?.capacityKg,' '                        val dvq3Used = vm.profile?.usedKg'; ctl DV-q3 "页面重新显示无生产者的已装重量"
inject "$DM" '"stock" to 40 + i * 13,' '"originPrice" to 35.0,';                                             ctl DV-r "演示面重新下发 originPrice（demo 又成第四个真相源）"
python - "$TG" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
                                                                                                              ctl DV-s "NoProducerKeyFamilyContractTest 被注释掉一条（别名回流无人报警）"
# sed 锚点被改名时抽取为空 ⇒ DV-a/DV-b/DV-d 的 0 全是假的，必须响
sed -i 's/suspend fun driverEarnings(): One<Earnings>/suspend fun earningsRenamedV21(): One<Earnings>/' "$RP"; ctl GUARD "解析体锚点失效（判据簿抓不到代码，0 不再可信）"
sed -i 's/path.endsWith("\/driver\/earnings")/path.endsWith("\/driver\/earningsX")/' "$DM";                   ctl GUARD "演示块锚点失效（DV-c 的 0 不再可信）"
# 必须换成**不含原键名子串**的名字：`s/weekAmount/weekAmountRenamed/` 这种改法 grep -oE '"weekAmount"' 照样命中，负控是假的
sed -i 's/"weekAmount"/"WkA"/g; s/"monthAmount"/"MA"/g; s/"totalWeight"/"TW"/g; s/"todayEarnings"/"TE"/g; s/"totalEarnings"/"TWE"/g; s/"orderCount"/"OC"/g; s/"fee"/"FX"/g; s/"bizType"/"BT"/g; s/"items"/"ITS"/g; s/"details"/"DTS"/g; s/"date"/"DT"/g; s/"day"/"DY"/g; s/"count"/"CNT"/g; s/"type"/"TY"/g' "$TE"; ctl GUARD "别名模式在测试源码也数不到 ⇒ grep 计数管道失效，本簿所有『预期 0』不得采信"
# DV-m/DV-m2/DV-n/DV-n2/DV-p2 全靠 sed 抽块，锚点一失效抽取就是空串，0 就不再是"归零"而是"没数到"
sed -i 's/fun parse(o: JSONObject) = DriverProfile(/fun parse(o: JSONObject) = DriverProfileV21(/' "$MD";     ctl GUARD2 "档案解析块锚点失效（DV-m/DV-m2 的数全是空的）"
# 同样必须换成不含原键名子串的名字，否则 DV-r 那种"预期 0"是假的
sed -i 's/"originPrice"/"OPriceX"/g; s/"sales"/"SlsX"/g; s/"usedKg"/"UsedX"/g; s/"verified"/"VfdX"/g; s/"capacityKg"/"CapX"/g; s/"checked"/"ChkX"/g; s/"needPhoto"/"NpX"/g; s/"photoRequired"/"PrX"/g; s/"forcePhoto"/"FpX"/g; s/"recommended"/"RcdX"/g; s/"planName"/"PnX"/g; s/"best"/"BstX"/g; s/"selected"/"SelX"/g; s/"segments"/"SegX"/g; s/"explain"/"ExpX"/g' "$TG"; ctl GUARD2 "撤键模式在 NoProducerKeyFamilyContractTest 也数不到 ⇒ DV-m2/DV-n2/DV-r 的 0 不得采信"
# ===== F-3 ⑬ 轮：路线/轨迹解析块撤掉的 12 个死别名键（DV-t…DV-t6 + GUARD3）=====
sed -i 's/val raw = o\.multiArr("steps")/val raw = o.multiArr("steps", "guidance", "instructions")/' "$RP";        ctl DV-t "steps 读法重新采纳 guidance/instructions 两个三面 0 生产者别名"
# 只在两块**外面**注入：DV-t 数不到、DV-t2 数得到 ⇒ 证明两条不是同一条判据写了两遍
inject "$MD" 'fun JSONObject.timeOf(vararg keys: String)' 'private val dvt2Probe = JSONObject().multiStr("crs")';  ctl DV-t2 "别名挪进别的解析块（换宿主也响，闭包判据独立成立）"
drop "$RP" '            roadEtaSeconds = o.multiNum("routeDurationSeconds").toInt(),';                            ctl DV-t3 "撤别名把真实键 routeDurationSeconds 一起带走（时长恒 0 还静默）"
drop "$RP" '        recommendedLane = it.multiNumOrNull("recommendedLane")?.toInt(),';                            ctl DV-t4 "navStepOf 的真实键 recommendedLane 被删（车道整列变 null 还静默）"
python - "$RGT" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
                                                                                                                  ctl DV-t5 "RouteGuideContractTest 被注释掉一条（别名不产生值只剩 grep，行为没人钉）"
python - "$UTT" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
                                                                                                                  ctl DV-t6 "UserTrackingContractTest 被注释掉一条（finished/completed 顶替 done 无人钉）"
# RB/NB 靠 sed 抽函数体：锚点一改名抽出就是空串，DV-t 的 0 与 DV-t3/t4 的数全都不再是"数出来的"
sed -i 's/private fun routeGuideOf(/private fun navGuideV21(/' "$RP";                                             ctl GUARD3 "路线块锚点失效（RB 为空 ⇒ DV-t/DV-t3 的读数不得采信）"
sed -i 's/private fun navStepOf(/private fun navStepV21(/' "$RP";                                                 ctl GUARD3 "步骤块锚点失效（NB 为空 ⇒ DV-t4 的 7 不得采信）"
# guard_rkeys 是 DV-t/DV-t2 的反证输入地：别名模式连测试源码里都数不到，就该响而不是继续判绿
sed -i 's/"guidance"/"GdnX"/g; s/"instructions"/"InsX"/g; s/"routeSource"/"RscX"/g; s/"roadDistanceMeters"/"RdmX"/g; s/"durationSeconds"/"DurX"/g; s/"crs"/"CrsX"/g; s/"offRoute"/"OffX"/g; s/"meters"/"Mtx"/g; s/"lane"/"LnX"/g; s/"seconds"/"SecX"/g' "$RGT"; sed -i 's/"finished"/"FinX"/g; s/"completed"/"CmpX"/g' "$UTT"; ctl GUARD3 "别名模式在两份契约用例里也数不到 ⇒ 反证输入没了，DV-t/DV-t2 的 0 不采信"

# ===== F-3 ⑰ 轮：census 补列（DV-u…DV-x）——注入的是**沙盒里那本 census**，不是判据簿自己 =====
# DV-u 的两支读数来自两份实现：census 的 SHAPE_* 常数 vs 判据簿里独立实现的同一套规则。
# 只改 census 那一份，等号就必须断；等号还成立说明这条判据在抄 census 自己。
subst "$CS" 'multi(?:Num|Bool)(?:OrNull)?\(|opt(?:Int|Double|Boolean|Long)\(' \
             'multi(?:Num|Bool|Str)(?:OrNull)?\(|opt(?:Int|Double|Boolean|Long)\('
                                                                                                              ctl DV-u "census 的 numbool 规则把字符串读法也算进去（与独立实现分叉=拆分列不可信）"
# 三面 0 生产者 = 后端 0 ∧ 小程序 0 ∧ 演示面 0：新读点走 OrNull，DV-k 的 116 不动，只能由 DV-v 响
inject "$MD" 'fun JSONObject.timeOf(vararg keys: String)' 'private val dvvOrphan = JSONObject().multiNumOrNull("dvvOrphanKey")'
                                                                                                              ctl DV-v "新增一个三面 0 生产者的数值读法（键 127→128、站点 152→153：天花板 2026-10-13 已收到现读）"
# DV-w 是"两个基数是不是同一个东西"的桥：只改 census 侧的 DV_K 常数，DV-k 的 grep 不动，桥必须断
subst "$CS" 'DV_K = re.compile(r"multi(?:Num|Bool)\(")' 'DV_K = re.compile(r"multi(?:Num|Bool|Str)\(")'
                                                                                                              ctl DV-w "census 的 DV_K 口径含 multiStr（116 与桥读数不再是同一件事）"
# 有 C 行没被 arr/numbool/str 接住：把 str 规则改成扫不到，拆分和就必须小于 C 总数
subst "$CS" 'SHAPE_STR = re.compile(r"multiStr\(|optString")' 'SHAPE_STR = re.compile(r"multiZz\(|optZz")'
                                                                                                              ctl DV-x "普查的 str 规则扫不到（C 行掉进 other，总数与拆分和对不上）"
# 第四面被读窄：演示面 distinct 掉到 200 以下，三面判据当场退化成两面（DV-v 会一起响，两面本来就串着）
subst "$CS" 'to\s*' 'zzToNever\s*'
                                                                                                              ctl DV-x "普查的演示面 to 写法扫不到键（三面退化两面、DV-v 的 0 也随之失真）"

# ===== 队列 #262 轮：任务族 /driver/tasks（DV-y…DV-y6u + GUARD4a…d）=====
# 与 DV-t 同一口径：**只按块判**。这些名字在别的报文里是真键（goodsName/cargoName/bizType…），
# 全 main 归零的写法本来就长红，所以这里的注入全部落在 parseTaskItem / DriverTask.parse 两块内。
inject "$MSP" 'fun parseTaskItem(o: JSONObject): MissionStop {' '            val dvyAliasProbe = o.multiNumOrNull("targetStation")'
                                                                                                              ctl DV-y "任务族解析体回流幻影键 targetStation（后端 19 键里没有它）"
drop "$MSP" '                statusName = o.multiStr("statusName"),';                                          ctl DV-y2 "撤别名把真实键 statusName 一起带走（状态名列变空还静默）"
drop "$MD" '            windowStartText = o.reportTimeOf("taskWindowStart"),';                                 ctl DV-y2b "任务窗口起始键读法被删（整段窗口说成只到结束时刻）"
subst "$MSP" '                orderNo = o.multiStr("orderNo"),' '                orderNo = o.multiStr("orderNo", "waybillNo"),'
                                                                                                              ctl DV-y3 "给单键 VO 补一个第二候选名（waybillNo 三面 0 生产者）"
inject "$MSP" 'fun parseTaskItem(o: JSONObject): MissionStop {' '            val dvyGeoProbe = o.multiNumOrNull("latitude")'
                                                                                                              ctl DV-y4 "任务族读点吃坐标：这个 VO 全文没有 latitude，画出来的点是编的"
python - "$DTR" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
                                                                                                              ctl DV-y5 "DriverTaskRespContractTest 被注释掉一条（缺键塌 0 复发无人报警）"
subst "$WB" 'DriverTaskSegment.group(vm.tasks)' 'DriverTaskSegment.groupV21(vm.tasks)'
                                                                                                              ctl DV-y6g "页面不再从唯一出口取任务段（第二套分段口径的入口形状）"
subst "$WB" 'SectionTitle("今日运营任务", subtitle = "暂无任务段")' 'SectionTitle("今日任务", subtitle = "暂无任务段")'
                                                                                                              ctl DV-y6t "无段那一支的标题被改名：后端没下发时那一格在界面上消失"
subst "$MD" 'items.groupBy { it.planId }' 'items.groupBy { it.stop.visitSequence }'
                                                                                                              ctl DV-y6p "按 visitSequence 分段：多方案混排会被并成一段（顺序口径不再是后端的）"
subst "$MD" 'groupBy { it.stop.stationId }' 'groupBy { it.stop.orderNo }'
                                                                                                              ctl DV-y6s "按订单号聚合：同站两动作散成两条任务行"
inject "$WB" 'val taskSegments = remember(vm.tasks) {' '            val dvuRegroup = vm.tasks.groupBy { it.planId }'
                                                                                                              ctl DV-y6u "页面自己重新分组：分段口径出现第二个答案"
# 三条块锚点 GUARD：sed 抽取一旦为空，DV-y/DV-y2/DV-y2b 的读数就全都不再是"数出来的"
subst "$MSP" 'fun parseTaskItem(o: JSONObject): MissionStop {' 'fun taskItemV21(o: JSONObject): MissionStop {'
                                                                                                              ctl GUARD4a "parseTaskItem 锚点失效（YSTP 为空 ⇒ DV-y/DV-y2/DV-y4 的数不可信）"
subst "$MD" 'fun parse(o: JSONObject) = DriverTask(' 'fun parseV21(o: JSONObject) = DriverTask('
                                                                                                              ctl GUARD4b "DriverTask.parse 锚点失效（YDTP 为空 ⇒ DV-y2b 的窗口计数不可信）"
subst "$MSP" 'fun parse(o: JSONObject): MissionStop {' 'fun parseV21(o: JSONObject): MissionStop {'
                                                                                                              ctl GUARD4c "MissionStop.parse 锚点失效（YSPB 为空 ⇒ DV-y3p/DV-y4p 两条反证不成立）"
# guard_ykeys 是 DV-y 的反证输入地：幻影键名连契约用例的 fixture 里都数不到，就该响而不是继续判绿
# ⚠ 新名字必须不含原键名子串，否则 grep -oE 照样命中，负控是假的
subst "$DTR" '"targetStation"' '"TSX"'
python - "$DTR" <<'PY'
import sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
for old, new in [('"taskType"', '"TTX"'), ('"bizType"', '"BTX"'), ('"cargoName"', '"CNX"'),
                 ('"goodsName"', '"GNX"'), ('"itemCount"', '"ICX"'), ('"planTime"', '"PTX"'),
                 ('"taskName"', '"TNX"'), ('"createTime"', '"CTX"'), ('"count"', '"CNTX"')]:
    s = s.replace(old, new)
open(p, 'w', encoding='utf-8').write(s)
PY
                                                                                                              ctl GUARD4d "幻影键模式在契约用例里也数不到 ⇒ 反证输入没了，DV-y 的 0 不采信"

# ===== 队列 #269 轮：包裹页页首计数（DV-z…DV-z6 + GUARD5a/b）=====
# 这一族判的是**页面层读的是哪一个数**：后端 PageResult 的 total（全量几条）还是本页条数（≤pageSize=20），
# 以及请求失败那一格是"未取到"还是被洗成"寄货 0 单"。注入全部落在 ParcelScreen.kt 与其契约用例里。
inject "$PSU" 'parcelHeaderCountText(vm.sendCount, vm.orderCount)' '                    Text("寄货 ${vm.sends.size} 单", color = t.textSecondary, fontSize = (12.5 * scale).sp)'
                                                                                                              ctl DV-z1 "页首另起一格用本页条数当总数（第 21 单起少念；失败时 items 为空就念成「寄货 0 单」）"
# ⚠ 注入的是**换入参类型的重载**：同名同签名会在 Kotlin 里直接冲突（负控就变成"编译不过"而不是"口径写第二遍"），
#   而 dvz2 数的是 `internal fun serverCount(` 的出现次数、dvz3 数的是那一条完整签名 —— 重载只让前者变 2。
inject "$PSU" 'internal fun serverCount(error: String?, total: Int): Int? =' 'internal fun serverCount(error: Throwable?, total: Int): Int? = if (error == null) total else null'
                                                                                                              ctl DV-z2 "同一口径写第二遍（换个入参类型的第二个定义点）"
subst "$PSU" 'orderCount = serverCount(o.error, o.total)' 'orderCount = if (o.error == null) o.total else null'
                                                                                                              ctl DV-z2b "商城那一格绕开唯一出口就地算（计数口径从此有两个答案）"
subst "$PSU" 'internal fun serverCount(error: String?, total: Int): Int? = if (error == null) total else null' 'internal fun serverCount(error: String?, total: Int): Int = if (error == null) total else 0'
                                                                                                              ctl DV-z3 "出口类型改成非空：请求失败被写成 0 单（§五 把 C 当 A 说的页面形状）"
subst "$PSU" 'var sendCount by mutableStateOf<Int?>(null)' 'var sendCount by mutableStateOf(0)'
                                                                                                              ctl DV-z4 "计数格退回非空初值：还没加载完就把「没问到」念成「0 单」"
subst "$PSU" 'internal fun parcelHeaderCountText(' 'internal fun parcelHeaderCountV21('
                                                                                                              ctl DV-z5 "页面不再从那一个话术出口取那一句（措辞开始两处各写一套）"
inject "$PSU" 'private fun countSegment(label: String, count: Int?): String =' 'private fun altSegment(label: String, count: Int?): String = if (count == null) "${label}未取到" else "$label $count 单"'
                                                                                                              ctl DV-z5b "「未取到」的措辞出现第二份副本（未取到态开始各处自写）"
python - "$PCTU" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
                                                                                                              ctl DV-z6 "ParcelCountContractTest 被注释掉一条（失败≠0 这件事只剩 grep 在钉）"
# 两条 GUARD：load() 块靠 sed 抽，锚点一改名抽出就是空串，DV-z2b 的 2 就不再是"数出来的"；
# guard_zsize 是 DV-z1 的反证输入地，模式连契约用例里都数不到 ⇒ 该响而不是继续判绿
subst "$PSU" 'fun load() = work {' 'fun loadRenamedV21() = work {'
                                                                                                              ctl GUARD5a "load() 块锚点失效（ZLB 为空 ⇒ DV-z2b 的 2 不可信）"
subst "$PCTU" 'vm.sends.size' 'vm.sendsCount.size'
                                                                                                              ctl GUARD5b "z1 模式在契约用例里也数不到 ⇒ 反证输入没了，DV-z1 的 0 不采信"

echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
echo "[聚合] 按名点名变红 $OK / $TOTAL 条，不成立 $((TOTAL - OK)) 条"
[ "$FAIL" = 0 ] || exit 1
