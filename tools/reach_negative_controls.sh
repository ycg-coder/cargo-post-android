#!/usr/bin/env bash
# R-* 判据的负控制总表：逐条破口 → 确认脚本 exit 1 且报的是这一条。
# 只在 /tmp 副本上动手，工程真身不受影响。用法：bash tools/reach_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/reach_neg.$$
PR="$SRC/tools/check_reachability_probes.sh"
A="$SC/app/src/main/java/com/shanxiang/buspost"
MD="$A/data/model/Models.kt"
RP="$A/data/repo/Repo.kt"
DM="$A/data/repo/DemoData.kt"
SS="$A/ui/user/SendScreen.kt"
TSD="$SC/app/src/test/java/com/shanxiang/buspost/data/repo"
TS="$TSD/ReachabilityContractTest.kt"

rm -rf "$SC"; mkdir -p "$A/data/model" "$A/data/repo" "$A/ui/user" "$TSD" "$SC/tools"
cp "$PR" "$SC/tools/"; PR="$SC/tools/check_reachability_probes.sh"
cp "$SRC/app/src/main/java/com/shanxiang/buspost/data/model/Models.kt" "$MD"
cp "$SRC/app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt" "$RP"
cp "$SRC/app/src/main/java/com/shanxiang/buspost/data/repo/DemoData.kt" "$DM"
cp "$SRC/app/src/main/java/com/shanxiang/buspost/ui/user/SendScreen.kt" "$SS"
cp "$SRC/app/src/test/java/com/shanxiang/buspost/data/repo/ReachabilityContractTest.kt" "$TS"
CLEAN=/tmp/reach_neg_clean.$$; cp -r "$SC" "$CLEAN"
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
}

RCH_BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); rch_base_code=$?
echo "[基线] $(echo "$RCH_BASE" | tail -1)"
# 基线不绿就立刻收工：否则每条破口都会因为"判据本来就红"而打出 ✔，整轮负控是假的
[ "$rch_base_code" = 0 ] || { echo "✘ 基线未通过（exit $rch_base_code），负控结果一律不采信"; echo "$RCH_BASE" | grep '^×' | head -5; exit 2; }
inject "$SS" "internal fun buildSendForm()" '    val p = if (reach?.reachable == true) 1 else 0'; ctl R-a "界面退回按布尔 reachable 分支"
inject "$SS" "internal fun buildSendForm()" '    val p = reach?.distanceMeters ?: 0';     ctl R-b "界面读后端没有的 distanceMeters"
inject "$SS" "internal fun buildSendForm()" '    val p = "STATION"';                          ctl R-c "生产代码写死非法服务方式 STATION"
sed -i 's/"serviceMode":"STATION"/"serviceMode":"NEAREST_STATION"/' "$TS";                   ctl R-c2 "非法码反面 fixture 被删"
sed -i 's|"originalAddress" to originalAddress.trim()|"originalAddress" to reach?.stationName|' "$SS"; ctl R-d "站点名顶替用户原始地址"
sed -i 's/ReachabilityState/ReachEnum/g' "$SS" "$MD" "$RP" "$TS";                            ctl R-e "三态枚举引用点掉光"
sed -i 's/multiBoolOrNull/multiBool/g' "$MD" "$RP";                                           ctl R-f "缺键被塌成布尔（UNKNOWN 消失）"
sed -i 's/val walkMinutes: Int?/val walkMinutes: Int/; s/val distanceKm: Double?/val distanceKm: Double/' "$MD"; ctl R-g "公里/步行分钟变成非空"
inject "$SS" "internal fun buildSendForm()" '    val p = "步行 ${reach?.walkMinutes} 分钟"';  ctl R-h "页面裸拼 walkMinutes"
sed -i 's/直线估算，不是道路里程/道路里程/' "$MD";                                             ctl R-i "直线来源不再如实标注"
python - "$TS" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
ctl R-j "用例被注释掉但 @Test 字样还在"
python - "$RP" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.index('\n', s.index('Reachability.parse('))
open(p, 'w', encoding='utf-8').write(s[:i] + '\n    private fun q(o: org.json.JSONObject) = Reachability.parse(o) to Reachability.parse(o)' + s[i:])
PY
ctl R-k "同一行藏了第二个解析出口"
inject "$RP" "suspend fun reachability(" '    private fun q() = Reachability(false, "", 0, null, "")'; ctl R-k2 "Repo 直接构造 Reachability 模型"
python - "$DM" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
for k in ['"distanceProvider"', '"serviceMode"', '"recommendedStation"', '"distanceKm"']:
    i = s.find(k)
    if i >= 0: s = s[:i] + s[s.find(',', i) + 1:]
open(p, 'w', encoding='utf-8').write(s)
PY
ctl R-l "演示兜底形状偏离真实契约"
python - "$SS" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
s = s.replace('"pickupServiceMode" to pickupServiceModeOrNull(),', '"pickupServiceMode" to (pickupServiceModeOrNull() ?: pickupServiceModeOrNull()),', 1)
open(p, 'w', encoding='utf-8').write(s)
PY
ctl R-m "同一行藏了第二个服务方式出口"
sed -i 's/ReachabilityState.UNKNOWN/ReachabilityState.NOT_REACHABLE/g' "$SS";               ctl R-n "页面不再处理 UNKNOWN"
inject "$SS" "ReachabilityState.UNKNOWN ->" '    val p = "车辆可就近上门"';                   ctl R-n2 "UNKNOWN 分支里说可运（§11 红线）"
sed -i 's/不代表车辆能到/暂时没数据/' "$SS";                                                   ctl R-n3 "免责话被删"
sed -i 's/后端判定可上门，但没给出就近站点/（不提）/' "$SS";                                    ctl R-o "REACHABLE 却没站点时静默"
python - "$SS" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.index('ReachabilityState.REACHABLE ->')
j = s.index('推荐站点不在可选列表', i)
k = s.rindex('\n', 0, j); e = s.index('\n', j)
s = s[:k] + s[k:e].replace('!applied && r.stationId > 0L', 'false').replace('Text("推荐站点不在可选列表，请手动选择取货站点"', 'Text("另一句话"') + s[e:]
open(p, 'w', encoding='utf-8').write(s)
PY
ctl R-o2 "可达分支的手选提示没了"
sed -i 's/文字地址未取到，本单只提交了经纬度/vm.originalAddress/' "$SS";                       ctl R-p "文字地址缺失时整行被藏/被顶替"
echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
