#!/usr/bin/env bash
# T-* 判据的负控制总表：逐条破口 → 确认 check_eta_probes.sh exit 1 且报的是这一条。
# 只在 /tmp 副本上动手，工程真身不受影响。用法：bash tools/eta_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/eta_neg.$$
PR="$SRC/tools/check_eta_probes.sh"
A="$SC/app/src/main/java/com/shanxiang/buspost"
MD="$A/data/model/Models.kt"
RP="$A/data/repo/Repo.kt"
DM="$A/data/repo/DemoData.kt"
AL="$A/notify/AlertEngine.kt"
WG="$A/widget/NextBusWidgetProvider.kt"
BS="$A/ui/user/BusScreen.kt"
HS="$A/ui/user/HomeScreen.kt"
PS="$A/ui/user/ParcelScreen.kt"
SS="$A/ui/user/SendScreen.kt"
RS="$A/ui/driver/RoutesScreen.kt"
DN="$A/ui/driver/DriverNavScreen.kt"
TV="$A/ui/common/TraceView.kt"
TSD="$SC/app/src/test/java/com/shanxiang/buspost/data"
TS="$TSD/BusEtaContractTest.kt"
TSS="$TSD/SendEtaContractTest.kt"
TDC="$TSD/DistanceCaliberContractTest.kt"

rm -rf "$SC"
# ⚠ D-082 之后改成**整目录 copy**（原来这里逐文件 cp，共 12 个文件 + 3 本 test）。
#   后果实测在 10-12 那批：`T-z` 数的是 `ui/` 全域那七处如实文案，而 10-12 补进去的
#   `ui/user/TraceScreen.kt`（里程/时长 各一处）**不在这张清单里** ⇒ 沙盒只读 5、真身读 7 ⇒
#   基线当场红、整本"结果一律不采信"。这不是判据坏，是沙盒没照到判据的扫描面 ——
#   本仓老规矩的原话是"沙盒必须整目录 copy，只拷两三个文件的负控会在你新增第四个文件时让基线先红"。
mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
for need in "$MD" "$RP" "$DM" "$AL" "$WG" "$BS" "$HS" "$PS" "$SS" "$RS" "$DN" "$TV" \
            "$TS" "$TSS" "$TDC" "$TSD/MinutesContractTest.kt" "$A/ui/user/TraceScreen.kt"; do
    [ -f "$need" ] || { echo "✘ 沙盒缺 $need（判据扫得到、副本没有 ⇒ 基线的数不是真数）"; exit 2; }
done
CLEAN=/tmp/eta_neg_clean.$$; cp -r "$SC" "$CLEAN"
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
inject() { python - "$1" "$2" "$3" <<'PY'
import sys
p, anchor, add = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read(); i = s.find(anchor)
if i < 0: sys.exit("ANCHOR-MISS: " + anchor)
j = s.index('\n', i) + 1
open(p, 'w', encoding='utf-8').write(s[:j] + add + '\n' + s[j:])
PY
# ⚠ 锚点没命中必须**当场停**，不能让脚本接着往下跑：T-e / T-w2 就是这样被误诊了三轮
#   （F-2 ⑩ 把 `check(context)` 的返回类型改成 `AlertScan` 后锚点死了，注入一直是空跑，
#    ctl 却把它报成"判据无判别力 ✘"——D-⑰⑱-1 因此一直挂着"待修判据"而不是"待修锚点"）。
[ "$?" = 0 ] || { echo "✘ inject 锚点没命中（上面 ANCHOR-MISS 那行）⇒ 破口根本没落地，本书结论不采信：要修的是锚点不是判据"; exit 2; }
}
# 只替换 data class 构造体内的那一处，避免全文件 sed 把别的类的同名字段一起改掉（判据会串味）
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
[ "$?" = 0 ] || { echo "✘ sbysed 锚点/类名没命中（上面 CLASS-MISS / OLD-MISS 那行）⇒ 破口根本没落地，本书结论不采信"; exit 2; }
}

ETA_BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); eta_base_code=$?
echo "[基线] $(echo "$ETA_BASE" | tail -1)"
# 基线不绿就立刻收工：否则每条破口都会因为"判据本来就红"而打出 ✔，整轮负控是假的
[ "$eta_base_code" = 0 ] || { echo "✘ 基线未通过（exit $eta_base_code），负控结果一律不采信"; echo "$ETA_BASE" | grep '^×' | head -5; exit 2; }
inject "$BS" "stubValue = v.etaValueText" '        val eta = v.etaMinutes ?: 0';                                    ctl T-a "把没给 ETA 塌成 0"
sed -i 's/multiNumOrNull/multiNum/g' "$MD";                                                                          ctl T-b "可空哨兵读法整体退回 multiNum"
sed -i 's/val etaMinutes: Int?/val etaMinutes: Int/' "$MD";                                                           ctl T-c "车辆到站分钟变成非空类型"
inject "$BS" "stubValue = v.etaValueText" '        val fake = 1 - v.etaMinutes!! / 40';                              ctl T-d1 "客户端自己编进度百分比"
sed -i 's/progress = v.roadProgress ?: -1f/progress = 55f/' "$BS";                                                    ctl T-d2 "进度条改回写死的数字"
# T-e 的落点从 notify 挪到桌面卡片：F-2 ⑩ 之后 AlertEngine 里已经没有 `a.etaMinutes` 这个读法
#（到站提醒改读 carrierEtaMinutes，见 T-w1/T-w2），而 T-e 的扫描面是 notify+widget+data ——
# 卡片是这一族今天唯一还在用 `etaMinutes` 的地方，破口写在这里才既咬得住又仍然能编译。
inject "$WG" 'views.setTextViewText(R.id.widget_eta_unit, vehicle.etaUnitText)' '            val etaMinutes = vehicle.etaMinutes ?: 0
            if (etaMinutes > 0) views.setTextViewText(R.id.widget_eta, "约 $etaMinutes 分钟到站")';              ctl T-e "又拿 0 当『还没到时间』（桌面卡片这一路：null ?: 0 之后 0 与『没给』同形）"
sed -i 's/"2" -> VehicleStatus.OFF_DUTY/"2" -> VehicleStatus.OFF_DUTY\n            "3" -> VehicleStatus.NO_LOCATION/' "$MD"; ctl T-f1 "码表里补回后端不会发的整数码 3"
sed -i 's/"2" -> VehicleStatus.OFF_DUTY/"2" -> VehicleStatus.ARRIVED/' "$MD";                                         ctl T-f2 "停用车又被说成已到站"
sed -i 's/"RUNNING" -> VehicleStatus.RUNNING/"RUNNING" -> VehicleStatus.RUNNING\n            "在途" -> VehicleStatus.RUNNING/' "$MD"; ctl T-g "中文状态串又被当成码表输入"
inject "$RP" "suspend fun nearbyBuses(" '    private fun q(o: org.json.JSONObject) = BusVehicle.parse(o) to BusVehicle.parse(o)'; ctl T-h1 "同一行藏了第二个解析出口"
inject "$RP" "suspend fun nearbyBuses(" '    private fun z() = BusVehicle("1", "渝A·1", null, null, "", "", null, "", "", "")';    ctl T-h2 "Repo 直接构造 BusVehicle"
sed -i 's/\.byUserDistance()/.sortedBy { it.distanceKm }/' "$HS";                                                     ctl T-i "排序口径换回 distanceKm（null 排最前）"
sed -i 's/\.put("progress"/.put("prog"/' "$DM";                                                                       ctl T-j1 "演示面 keys 改名（lines 端点不再照抄契约）"
sed -i 's/"routeName" to line,/"routeName" to line, "routeId" to "K1",/' "$DM";                                       ctl T-j2 "演示面又发自造的车辆 routeId"
sed -i 's/"routeProvider", "SCHEDULE"/"routeProvider", "REAL"/' "$DM";                                                ctl T-j3 "routeProvider 写回自造码"
python - "$DM" <<'PY'
import sys, re
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.index('private fun vehicleJson'); j = s.index('\n    }\n', i)
body = s[i:j].replace('JSONObject.NULL', '5')
open(p, 'w', encoding='utf-8').write(s[:i] + body + s[j:])
PY
                                                                                                                       ctl T-j4 "演示面把『没给』写成数字，显式 null 契约态消失"
sed -i 's/base.put("etaMinutes"/base.put("etaMinuteX"/' "$DM";                                                        ctl T-j5 "演示面 etaMinutes 写入点改名/多出第二个"
python - "$TS" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
                                                                                                                       ctl T-k "用例被注释掉但 @Test 字样还在"
inject "$HS" "stubColor = if (running && v.etaMinutes" '                    Text("还有 ${v.etaMinutes} 分钟")';         ctl T-l "页面裸拼 null ETA"
sed -i 's/暂无到站预估/再说一句/' "$MD";                                                                               ctl T-m "『没给』的如实文案被删"
sed -i 's/vehicle.etaValueText/"0"/' "$WG";                                                                           ctl T-n "桌面卡片与页面口径脱钩"
sed -i 's/mapNotNull/map/' "$AL";                                                                                     ctl T-o1 "到站提醒不再滤掉没给 ETA 的行"
sed -i 's/\([eE]\)taMinutes != null/\1taMinutes != -1/g' "$HS" "$PS";                                                     ctl T-o2 "页面不再先判 null（没给也参与比较；carrier 前缀的大写 E 也要覆盖）"
sed -i 's/multiStr("busId", /multiStr("vehicleNo", /' "$MD";                                                           ctl T-p "真实键 busId 读法被换掉（车辆编号静默变空）"
sed -i 's/multiStr("plateNo")/multiStr("vehiclePlate")/' "$MD";                                                        ctl T-p2 "司机档案车牌真键被换回自造候选名（合并数会让这条注入空跑，D-040）"
sed -i 's/审计 §68/审计 六八/' "$MD";                                                                                  ctl T-q "§68 数据源缺口记录被删"
echo "--- ⑫ 货运侧新增判据的负控制 ---"
sbysed "$MD" SendOrder "val etaMinutes: Int?" "val etaMinutes: Int";                                                   ctl T-r1 "SendOrder 班次 ETA 退回非空类型"
sbysed "$MD" SendOrder "val carrierEtaMinutes: Int? = null" "val carrierEtaMinutes: Int = 0";                          ctl T-r1 "SendOrder 承运直线估算 ETA 退回非空默认值"
sbysed "$MD" TransportLeg "val etaMinutes: Int?" "val etaMinutes: Int";                                                ctl T-r2 "段契约 etaMinutes 退回非空（这个键后端根本不发）"
sbysed "$MD" ParcelTrace "val etaMinutes: Int?" "val etaMinutes: Int";                                                 ctl T-r3 "轨迹 etaMinutes 退回非空"
sbysed "$MD" RouteGuide "val etaMinutes: Int?" "val etaMinutes: Int";                                                  ctl T-r4 "导航全程时长退回非空（会显示『全程约 0 分钟』）"
sbysed "$MD" RoutePreview "val distanceKm: Double? = null" "val distanceKm: Double = 0.0";                             ctl T-r5 "路线预估里程退回 0 km 默认值"
inject "$RP" "suspend fun routePreview(pickupStationId" '    private fun bad(o: org.json.JSONObject) = o.multiNum("etaMinutes")'; ctl T-s "解析层又用 multiNum 读到站分钟（缺键读成 0）"
# E-1 注：这条的注入锚点原本是 multiNumOrNull("etaMinutes", "eta")，别名删掉后那条 sed 变成空操作 ⇒ 负控制静默失效
#      （本轮第一次跑就是 ✘ 1 条被抓到）。锚点改到"删完别名后的新形状"上；注释必须单独成行，
#      写在行内会把行尾的 ctl T-t 一起注释掉 —— 那又是同一种失效。）
sed -i 's/multiNumOrNull("etaMinutes")?.toInt()/multiNumOrNull("etaMinutes", "eta", "carrierEtaMinutes")?.toInt()/g' "$RP";       ctl T-t "班次 ETA 又把承运直线估算当别名读"
inject "$PS" "s.etaMinutes?.let {" '                    val late = s.etaMinutes > 0';                                  ctl T-u "页面又把 0 当成『还没到时间』"
sed -i 's/车距站约 \$it 分钟（直线估算）/车距站约 $it 分钟/' "$PS";                                                     ctl T-v "承运分钟的『直线估算』口径被删"
inject "$PS" "val eta = a.carrierEtaMinutes" '                val shown = a.carrierEtaMinutes?.let { "约 $it 分钟" }';  ctl T-v2 "新增一处把承运分钟说成到站时间、却不带口径的展示"
sed -i 's/Repo.sendOrders()/Repo.orderBlocks()/' "$AL";                                                               ctl T-w1 "到站提醒的数据源离开 /send/page"
inject "$AL" "val send = Repo.sendOrders()" '            val back = Repo.myArrangements()';                                        ctl T-w2 "提醒又读回没有 ETA 键的 /send/arrangements（§68 复发）"
python - "$TSS" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
                                                                                                                       ctl T-x "货运侧契约测试被注释掉"
sed -i 's/carrierLocationSource != "REAL_STALE"/carrierLocationSource == "REAL_STALE"/' "$PS";                         ctl T-y "位置过期的单又被当成可信到站提醒"
# ⚠ D-082 之后重锚（这条是第 21 格，当时被"基线红"挡在下面没暴露）：破口替换的那四个词是 10-12 之前的旧话术
#   （到站分钟未下发 / 未给到站分钟 / 里程未给 / 时长未给），今天树里是 时间暂未知 / 里程暂未知 / 时长暂未知 /
#   全程时长暂未知 ⇒ sed 一个都没换 = **空跑**，而空跑在日志上的表现与"判据不判"同形（✘ 负控制失效 exit 0）。
#   多条并列时把长的排前面：`时长暂未知` 是 `全程时长暂未知` 的子串，顺序反了会留下半句"全程已到达"。
find "$A" -name '*.kt' -print0 | xargs -0 sed -ri 's/全程时长暂未知|时间暂未知|里程暂未知|时长暂未知/已到达/g';            ctl T-z "货运侧『没给』的如实文案被删（缺失又变静默）"
echo "--- ⑬ 精确到站分钟 + 三个 distanceKm 口径新增判据的负控制 ---"
sbysed "$MD" BusVehicle "val etaToNextStationMinutes: Double? = null" "val etaToNextStationMinutes: Double = 0.0";      ctl T-aa "精确到站分钟退回非空默认值（没给会说成约 0 分钟到下一站）"
sed -i 's/Math.max(1, kotlin.math.ceil(/kotlin.math.ceil(/' "$MD";                                                   ctl T-ab "丢掉『至少 1 分钟』的向上取整口径（0.2 分钟会显示成 0）"
python - "$MD" <<'PY'
import sys, re
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
m = re.search(r'^data class BusLine\b', s, re.M)
old = 'distanceKm = o.multiNumOrNull("distanceKm"),'
i = s.index(old, m.start())
open(p, 'w', encoding='utf-8').write(s[:i] + 'distanceKm = o.multiNumOrNull("distanceKm", "distance"),' + s[i + len(old):])
PY
                                                                                                                      ctl T-ac "线路里程又把米制的 distance 当公里读（自造别名键复发）"
sbysed "$MD" QuoteResult "val distanceKm: Double? = null" "val distanceKm: Double = 0.0";                             ctl T-ad1 "计价里程退回非空默认值（缺坐标会说成 0 km）"
sbysed "$MD" TransportLeg "val durationMinutes: Int? = null" "val durationMinutes: Int = 0";                          ctl T-ad2 "段计划时长退回非空默认值（⑫ 残留复发）"
inject "$BS" '"全程 " + (line.distanceKm?.let' '                    val bad = "${line.distanceKm} 公里"';               ctl T-ae "界面裸读 .distanceKm 不判 null（null 被拼进文案）"
python - "$TDC" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
                                                                                                                      ctl T-af "⑬ 里程口径契约测试被注释掉"
sed -i 's/\.put("etaToNextStationMinutes"/.put("etaToNextStationMinutesX"/' "$DM";                                    ctl T-ag "演示面不再下发精确到站分钟（新字段在演示路径上永不为真）"
sed -i 's/BusTag(it.positionOnlyTag, tone = BusTone.CLAY)/BusTag("在途 ${it.nextStation}", tone = BusTone.CLAY)/' "$RS"; ctl T-ah "只有经纬度的位置报文又被界面宣布成『在途』"
echo "--- E-1 自造别名 eta 归零新增判据（T-ai/T-ai2/T-ai3）的负控制 ---"
sed -i 's/multiNumOrNull("etaMinutes")?.toInt()/multiNumOrNull("etaMinutes", "eta")?.toInt()/g' "$MD";                ctl T-ai "解析层又把自造别名 eta 写回候选名（Models 侧四处读法）"
sed -i 's/else o.multiNumOrNull("etaMinutes")?.toInt()/else o.multiNumOrNull("etaMinutes", "eta")?.toInt()/' "$RP";   ctl T-ai "路线引导的回退分支又接受自造别名（Repo 侧）"
inject "$MD" "fun JSONObject.multiNum(vararg keys: String): Double {" 'private val ETA_ALIAS_PROBE = "eta"';        ctl T-ai2 "main 源码里凭空多出一个裸 eta 字面量（闭包判据）"
sed -i 's/"etaMinutes" to 18/"etaMinutes" to 18, "eta" to 18/g' "$DM";                                              ctl T-ai3 "演示面开始下发 eta 键给别名续命（连带让 T-ai2 一起响：演示面也在 main 源码里）"
echo "--- F-2 步 2 Minutes 家族别名归零新增判据（T-aj…T-aj6 + GUARD）的负控制 ---"
sed -i 's/multiNum("plannedMinutes").toInt()/multiNum("plannedMinutes", "offsetMinutes").toInt()/' "$MD";        ctl T-aj "站点计划分钟又接受第二候选名 offsetMinutes（后端/小程序/演示面三面都无生产者）"
inject "$MD" "fun JSONObject.multiNum(vararg keys: String): Double {" 'private val MINUTES_ALIAS_PROBE = "totalMinutes"'; ctl T-aj2 "main 源码里凭空多出一个已删别名键字面量（闭包判据：不挂在读法调用上也响）"
sed -i 's/"durationMinutes" to 86/"durationMinutes" to 86, "totalMinutes" to 86/' "$DM";                          ctl T-aj3 "演示面开始下发 totalMinutes 给别名续命（T-aj2 的\"无生产者\"论据当场失效，连带一起响）"
sed -i 's/plannedMinutes = o.multiNum("plannedMinutes").toInt(),/plannedMinutes = 0,/' "$MD";                     ctl T-aj4 "后端实发的 plannedMinutes 不再被读（缺键塌 0 复发，连带 T-aj5 总量下降一起响）"
python - "$TSD/MinutesContractTest.kt" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
                                                                                                                    ctl T-aj6 "Minutes 契约测试被注释掉一条（别名复发无人报警）"
sed -i 's/"minutes"/"minutesRenamed"/g; s/"offsetMinutes"/"offsetMinutesRenamed"/g; s/"waitMinutes"/"waitMinutesRenamed"/g; s/"totalMinutes"/"totalMinutesRenamed"/g' "$TSD/MinutesContractTest.kt"; ctl T-aj "GUARD 正控制失配：别名模式在测试源码也数不到 ⇒ grep 计数管道已失效，本族所有\"预期 0\"不得采信"
echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
