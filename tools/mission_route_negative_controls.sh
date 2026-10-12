#!/usr/bin/env bash
# MR-* 任务地图无路线态**探针**的负控制：逐条破口 → 确认 check_mission_route_probes.sh exit 1 且报的就是这一条。
# MR-a…MR-k 钉 ⑧（说什么、画什么）；MR-l…MR-r 钉队列 #216 的 ⑫⑬ 3D 视觉层（谁有权决定怎么画）。
# 只在 /tmp 副本上动手，工程真身不受影响（纯文本判据，不需要编译）。
# ⚠ 整目录 copy（不是只拷几个文件）：本轮新增的 MissionRouteStateContractTest.kt 若不在副本里，
#   基线会先红，后面每一条 ✔ 都是假的。
# ⚠ 每条"目标 0"的破口都配一条注入成功的证据（red() 里锚点没命中就 exit 2），
#   防止"空跑注入"（改了个不存在的位置，判据照样绿，看起来像负控失效）。
# 用法：bash tools/mission_route_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（沙盒快照取自真身），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/mr_neg.$$
PKG=com/shanxiang/buspost
PR="$SRC/tools/check_mission_route_probes.sh"
mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
cp -r "$SRC/tools" "$SC/tools"
[ -f "$SC/app/src/main/java/$PKG/ui/driver/MissionMarkers.kt" ] || { echo "✘ 副本没建起来（$SC），本轮负控全部作废"; exit 2; }
[ -f "$SC/app/src/test/java/$PKG/ui/driver/MissionRouteStateContractTest.kt" ] || { echo "✘ 副本缺本轮新用例文件，本轮负控全部作废"; exit 2; }
# ⑫⑬ 那一族（MR-l…MR-r）的三个新落点：缺任何一个，基线就会在读文件预检处 exit 2，而不是把"0 命中"演成绿
[ -f "$SC/app/src/main/java/$PKG/ui/common/Map3DVisual.kt" ] || { echo "✘ 副本缺 ui/common/Map3DVisual.kt，MR-l…MR-r 的负控全部作废"; exit 2; }
[ -f "$SC/app/src/main/java/$PKG/ui/common/BusMap.kt" ] || { echo "✘ 副本缺 ui/common/BusMap.kt，MR-l/MR-m/MR-n 的负控全部作废"; exit 2; }
[ -f "$SC/app/src/test/java/$PKG/ui/common/Map3DVisualContractTest.kt" ] || { echo "✘ 副本缺视觉层用例文件，MR-r 的负控作废"; exit 2; }
A="$SC/app/src/main/java/$PKG"
AT="$SC/app/src/test/java/$PKG"
MM="$A/ui/driver/MissionMarkers.kt"
DMM="$A/ui/driver/DriverMissionMap.kt"
WB="$A/ui/driver/WorkbenchScreen.kt"
MC="$A/ui/common/MapCanvas.kt"
V3="$A/ui/common/Map3DVisual.kt"
BM="$A/ui/common/BusMap.kt"
LOC="$A/core/loc/Locator.kt"
DLS="$A/driver/DriverLocationService.kt"
T="$AT/ui/driver/MissionRouteStateContractTest.kt"
V3T="$AT/ui/common/Map3DVisualContractTest.kt"
CLEAN=/tmp/mr_neg_clean.$$; cp -r "$SC" "$CLEAN"
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

# ---------- MR-a 行为用例 ----------
red "$T" "
i = s.find('\n    @Test')
assert i >= 0, 'ANCHOR-MISS'
s = s[:i] + '\n    //@Test' + s[i+10:]
"
ctl MR-a "⑧ 的行为用例被注释掉一条"

# ---------- MR-b 唯一出口 ----------
# 第二处出口定义（同一个问题两个答案）
red "$DMM" "
n = '@Composable\nfun DriverMissionMap('
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, 'internal fun missionRouteReasonOf(phase: com.shanxiang.buspost.core.map.PolylinePhase, failure: String?): String = \"第二套说法\"\n\n' + n, 1)
"
ctl MR-b "出现第二个'有没有路线'的出口定义"

# 页面多一个自己拼句子的读点（出口读点不再恰好两处）
red "$WB" "
n = '        val missionGuide = vm.guide'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '        val negExtra = MissionMarkers.missionRouteReasonOf(vm.routePhase, vm.routeFailure, null)\n' + n, 1)
"
ctl MR-b "页面读唯一出口的点变成三处"

# ---------- MR-c 五格说法 ----------
# ⚠ D-082 之后整族重锚：8387929（清除界面技术术语）把那五句各自改短了，判据 MR_C1…C5 当时已经
#   逐句跟着重锚，而这本破口没有 ⇒ 五条注入全部静默落空（第一条就把整本带走，后面四条连跑都没跑到）。
#   教训写进锚点本身：破口的锚点与判据的锚点是**同一处源码文本**，必须同轮换。
red "$MM" "
n = '\"正在向后端要这一班的路线，期间不画示意线。\"'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '\"正在加载…\"', 1)
"
ctl MR-c "'在问'那一格丢掉'不画示意线'的承诺"

red "$MM" "
n = '\"还没去问过路线，不是后端说没有路线，期间不画示意线。\"'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '\"还没去问过路线，期间不画示意线。\"', 1)
"
ctl MR-c "'没问过'那一格不再声明这不是后端的结论"

red "$MM" "
n = '\"这次没问到：\$failure'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '\"后端没有可用路线，\$failure', 1)
"
ctl MR-c "'这次没问到'被升格成'后端没有'"

red "$MM" "
n = '（站点与顺序仍按调度安排显示，不画示意线）'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '（不画示意线）', 1)
"
ctl MR-c "转述来源原因那一格丢掉'站点顺序仍按调度安排显示'"

red "$MM" "
n = '\"没有证据表明后端说过它有路线，暂无路线数据，不画示意线。\"'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '\"后端没有下发这一班的真实道路折线，不画示意线。\"', 1)
"
ctl MR-c "无证据兜底那一格开始替后端下结论"

# ---------- MR-d 正式可达文件里的旧那句 ----------
red "$MC" "
n = 'reason ?: \"现在没有可画的真实道路折线（具体原因未取得）。为避免显示与实际行驶无关的假路线，这里不做任何连线示意。\"'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, 'reason ?: \"后端没有下发真实道路折线，这里不做任何连线示意。\"', 1)
"
ctl MR-d "MapCanvas 的兜底又写死'后端没有下发'"

# ---------- MR-e 必填参数 / 唯一出口传参 ----------
red "$DMM" "
n = '    noRouteReason: String,\n'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '    noRouteReason: String = \"后端没有下发这一班的真实道路折线\",', 1)
"
ctl MR-e "noRouteReason 被加上默认值（页面不带出口也能画）"

red "$WB" "
n = '                                noRouteReason = MissionMarkers.missionRouteReasonOf('
assert s.count(n) == 1, 'ANCHOR-NOT-UNIQUE: %d' % s.count(n)
s = s.replace(n, '                                noRouteReason = \"这一班没有路线，请以下方列表为准\",', 1)
"
ctl MR-e "画地图那一支绕过唯一出口自己编句子"

# ⚠ D-082 之后重锚：7d8b819 卡片重排把这段右移了一级、参数换成逐行写，旧锚点（同一行带 guide=）不在了。
#   这里只需要一个"任务地图调用点长在第二处"的形状，插在哪一行不重要，钉的是 MRE_CALL 那一处。
red "$WB" "
n = '                        DriverSdkNavCard(\n'
assert s.count(n) == 1, 'ANCHOR-NOT-UNIQUE: %d' % s.count(n)
i = s.index(n)
s = s[:i] + '                        DriverMissionMap(guide = missionGuide, noRouteReason = MissionMarkers.missionRouteReasonOf(vm.routePhase, vm.routeFailure, null))\n' + s[i:]
"
ctl MR-e "任务地图出现第二个调用点"

# ---------- MR-f 相位写入 ----------
red "$WB" "
n = '        routePhase = PolylinePhase.Asking'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '        // NEG-CONTROL 摘掉在飞置位：' + n.strip(), 1)
"
ctl MR-f "装载时不再置 Asking（'在问'那一格永远读不到）"

red "$WB" "
n = '        routePhase = PolylinePhase.Asked'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '        // NEG-CONTROL 摘掉落地置位：' + n.strip(), 1)
"
ctl MR-f "落地后不再置 Asked（失败轮被念成'还没去问过'）"

red "$WB" "
n = '            routePhase = PolylinePhase.NotAsked\n            throw e'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '            throw e', 1)
"
ctl MR-f "协程取消不再退回没问过（取消被当成本轮问过）"

red "$WB" "
n = '            routeFailure = null'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '            // NEG-CONTROL 摘掉早退清原因', 1)
"
ctl MR-f "早退时不清失败原因（上一轮的'没问到'跟着下一轮一起念）"

red "$WB" "
n = '    internal var routePhase by mutableStateOf(PolylinePhase.NotAsked)'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '    var routePhase by mutableStateOf(PolylinePhase.NotAsked)', 1)
"
ctl MR-f "routePhase 不再是 VM 私有写（页面可自己置相位）"

# ---------- MR-g 早退块内的清零 ----------
red "$WB" "
n = '            routePhase = PolylinePhase.NotAsked\n            routeFailure = null\n'
assert n in s, 'ANCHOR-MISS'
m = '            error = DRIVER_UNBOUND\n            return\n        }\n'
assert m in s, 'ANCHOR-MISS-2'
s = s.replace(n, '', 1)
s = s.replace(m, m + '        routePhase = PolylinePhase.NotAsked\n        routeFailure = null\n', 1)
"
ctl MR-g "相位清零被搬到 return 之后（早退支残留上一轮状态）"

# ---------- MR-h 相位包住请求 ----------
red "$WB" "
a = '        routePhase = PolylinePhase.Asking\n'
assert a in s, 'ANCHOR-MISS'
b = '        routePhase = PolylinePhase.Asked'
assert b in s, 'ANCHOR-MISS-2'
s = s.replace(a, '', 1)
s = s.replace(b, '        routePhase = PolylinePhase.Asking\n' + b, 1)
"
ctl MR-h "'在问'置位被搬到请求之后（那一格永远不会被读到）"

# ---------- MR-i 失败支占屏 ----------
# 注入锚点 2026-10-09 重锚（工作台重排 M-1）：失败支条件已从 `vm.routeFailure != null` 扩成
# `missionGuide != null || vm.routeFailure != null`（后端把明细答成"没有"时 routeFailure 是空的，旧条件让整块静默消失），
# 旧锚在今天的源码里 0 命中 ⇒ red() 的 assert 会 ANCHOR-MISS、注入空跑，这一族就没人验过了。
red "$WB" "
i = s.find('        } else if (missionGuide != null || vm.routeFailure != null) {')
assert i >= 0, 'ANCHOR-MISS'
j = s.index('        }', s.index('RouteUnavailableState(', i))
s = s[:i] + '        // NEG-CONTROL：失败支整块不占屏（回到旧的静默消失）' + s[j:]
"
ctl MR-i "这次没问到又变回整块静默消失"

red "$WB" "
n = '        } else if (missionGuide != null || vm.routeFailure != null) {'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '        } else if (vm.guide == null) {', 1)
"
ctl MR-i "失败支的条件不再看这次问到的结果"

# ⚠ D-082 之后重锚：7d8b819 把这段整体右移（旧锚点那三级缩进对不上了），而且现在有**两条**
#   一模一样的 `onReplan = { vm.load() },`（有路线支 813 / 失败支 828）—— 按"第一处"删会删错支，
#   MRI_RETRY 数的是失败支那 12 行，删错就等于空跑。所以改成**定位到 RouteUnavailableState 之后**再删。
red "$WB" "
n = 'RouteUnavailableState('
assert s.count(n) == 1, 'ANCHOR-NOT-UNIQUE: %d' % s.count(n)
k = s.index(n)
r = 'onReplan = { vm.load() },'
assert s.count(r) == 2, 'ANCHOR-NOT-UNIQUE: %d' % s.count(r)
j = s.index(r, k)
ls = s.split('\n')
li = s[:j].count('\n')
assert r in ls[li], 'ANCHOR-MISS'
del ls[li]
s = '\n'.join(ls)
"
ctl MR-i "失败支没有重试入口"

# ---------- MR-j 画什么那一侧 ----------
red "$MM" "
n = 'if (guide.polyline.size < 2 || guide.routeUnavailableReason != null) emptyList()'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, 'if (guide.polyline.size < 2) emptyList()', 1)
"
ctl MR-j "折线闸门丢掉'后端/校验器给了原因就不画'这一半"

red "$DMM" "
n = '    val paths = MissionMarkers.paths(guide)'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, n + '\n    val negLine = listOf(MapPath(points = MissionMarkers.ordered(guide.stops).map { it.geo }))', 1)
"
ctl MR-j "任务地图页自己把任务点连成线"

# ---------- MR-k 复用而不是新造 ----------
red "$MM" "
anchor = 'object MissionMarkers {'
assert anchor in s, 'ANCHOR-MISS'
s = s.replace(anchor, anchor + '\n\n    internal enum class MissionRoutePhase { NotAsked, Asking, Asked }', 1)
"
ctl MR-k "新造第二套'班次路线相位'枚举"

red "$MM" "
n = 'import com.shanxiang.buspost.core.map.PolylinePhase\n'
assert n in s, 'ANCHOR-MISS-1'
s = s.replace(n, '', 1)
m = 'internal fun missionRouteReasonOf(phase: PolylinePhase,'
assert m in s, 'ANCHOR-MISS-2'
s = s.replace(m, 'internal fun missionRouteReasonOf(phase: Boolean,', 1)
"
ctl MR-k "任务地图出口改回布尔、不再复用 core/map 那套三态"

red "$MC" "
n = '    reason: String? = null,'
assert s.count(n) == 2, 'ANCHOR-MISS'
i = s.index(n)
s = s[:i] + '    reason: String,' + s[i+len(n):]
"
ctl MR-k "MapCanvas 两处 reason 参数形状变了"

# ==================== ⑫⑬ 3D 任务 Marker / 3D 车辆（MR-l…MR-r）====================
# 这一族的破口形状与 ⑧ 不同：⑧ 怕的是"说什么"，⑫⑬ 怕的是"谁有权决定怎么画"。
# 所以注入的都是"多出第二个出口 / 页面自己动手 / 视觉层回灌业务"，而不是改句子。

# ---------- MR-l 立体感只有一个出口 ----------
red "$V3" "
anchor = 'object Map3DVisual {'
assert anchor in s, 'ANCHOR-MISS'
s = s.replace(anchor, anchor + '\n    internal val negFlat = MarkerOptions().setFlat(true)', 1)
"
ctl MR-l "BusMap 之外又有人直接调 SDK 的 setFlat（第二个地方决定怎么画）"

red "$DMM" "
n = '@Composable\nfun DriverMissionMap('
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, 'internal fun marker3DOf(kind: Int): Float = 1f\n\n' + n, 1)
"
ctl MR-l "出现第二个 marker3DOf 出口：同一个问题两个答案"

red "$WB" "
n = '                meGps = meGps,\n'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, '', 1)
"
ctl MR-l "画地图那一支不再把定位三态交给任务地图（降级/不可用没人念）"

red "$WB" "
n = '    val serviceHeading by Running.headingDegrees.collectAsStateWithLifecycle()'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, '    // NEG-CONTROL 摘掉服务侧朝向读点：' + n.strip(), 1)
"
ctl MR-l "页面不再读服务侧那一个朝向（旋转的来源被换走）"

# ---------- MR-m 地图倾斜 ----------
red "$BM" "
n = '                    isTiltGesturesEnabled = tiltDegrees > 0f'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, '                    isTiltGesturesEnabled = true', 1)
"
ctl MR-m "倾斜手势开关与倾角脱钩（不 3D 的页面也被开手势）"

red "$DMM" "
n = '                tiltDegrees = Map3DVisual.MAP_TILT_DEGREES,'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, '                tiltDegrees = 30f,', 1)
"
ctl MR-m "任务地图自己带倾角数字，不再读那一个常量"

# ---------- MR-n 800ms 只给相机、不做逐帧插值 ----------
red "$V3" "
anchor = 'object Map3DVisual {'
assert anchor in s, 'ANCHOR-MISS'
s = s.replace(anchor, anchor + '\n    fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t', 1)
"
ctl MR-n "视觉层开始自己插值位置（指令的 800ms 只交给相机动画，0 台设备在环验不了）"

red "$BM" "
n = '                        Map3DVisual.CAMERA_ANIMATION_MS,\n'
assert s.count(n) == 3, 'ANCHOR-MISS'
s = s.replace(n, '                        1200L,\n', 1)
"
ctl MR-n "一处视口动画不再用那一个 800ms 常量"

# 只把倾角那一行搬到三次视口动画之后：其余计数不变，红只能来自顺序
red "$BM" "
n = '                if (tiltDegrees > 0f) aMap.moveCamera(CameraUpdateFactory.changeTilt(tiltDegrees))\n'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, '', 1)
m = '                        Map3DVisual.CAMERA_ANIMATION_MS,\n'
assert s.count(m) == 3, 'ANCHOR-MISS-2'
i = s.rindex(m) + len(m)
s = s[:i] + n + s[i:]
"
ctl MR-n "倾角更新被搬到视口动画之后（会被后一次相机更新覆盖）"

# ---------- MR-o 朝向只有一个来源，且不进上报报文 ----------
red "$LOC" "
n = '    fun heading(location: Location): Double? = if (location.hasBearing()) location.bearing.toDouble() else null'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, n.replace('else null', 'else 0.0'), 1)
"
ctl MR-o "定位没给朝向时又写成 0.0：正北是编出来的"

red "$LOC" "
n = '            headingDegrees = heading(l),\n'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, '', 1)
"
ctl MR-o "采样点不再把朝向带进 TrackFix（视觉层永远读不到）"

red "$LOC" "
n = '    val headingDegrees: Double? = null,'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, '    val headingDegrees: Double = 0.0,', 1)
"
ctl MR-o "TrackFix 的朝向从可空改成不可空缺省 0.0（缺失被当成正北）"

red "$DLS" "
n = '            Repo.reportLocation(step.shiftId, fix.geo, fix.speedKmh.toDouble())'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, n[:-1] + ', fix.headingDegrees)', 1)
"
ctl MR-o "上报报文里多了一个朝向字段（后端 AppDriverLocationReqVO 没有这个字段）"

# ---------- MR-p 旋转只给'我'这一格 ----------
red "$V3" "
n = '            rotateAngle = if (kind == MapMarker.KIND_ME) rotateOf(visual.headingDegrees) else null,'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, '            rotateAngle = rotateOf(visual.headingDegrees),', 1)
"
ctl MR-p "所有 marker 一起旋转：任务点/车辆被判成有朝向"

red "$MM" "
n = '    fun meMarker(me: Geo?, headingDegrees: Double? = null, degraded: Boolean = false): MapMarker? {'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, n + '\n        val negRot = Map3DVisual.rotateOf(headingDegrees)', 1)
"
ctl MR-p "装配层自己算旋转角：出口之外多出第二个答案"

# ---------- MR-q 新鲜度阈值只有一个生产者 ----------
red "$V3" "
n = '        fixAgeMs > LocationReportPolicy.MAX_FIX_AGE_MS -> GpsFixState.STALE'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, '        fixAgeMs > 30000 -> GpsFixState.STALE', 1)
"
ctl MR-q "视觉层造了第二个过期阈值（服务侧那一个不再算数）"

red "$DLS" "
n = '        headingDegrees.value = null\n'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, '', 1)
"
ctl MR-q "复位时不清朝向：停服后旧朝向还跟着转"

red "$DLS" "
n = '                    Running.headingDegrees.value = fix.headingDegrees'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, '                    // NEG-CONTROL 摘掉朝向落流：' + n.strip(), 1)
"
ctl MR-q "服务采样不再把朝向落到 Running（页面读到的永远是 null）"

# ---------- MR-r 视觉不碰业务 + 用例条数 ----------
red "$V3" "
anchor = 'object Map3DVisual {'
assert anchor in s, 'ANCHOR-MISS'
s = s.replace(anchor, anchor + '\n    internal val negLeg = DriverLegStateMapper.of(leg)', 1)
"
ctl MR-r "视觉层开始引用业务状态推进（本工程那一个生产者是 DriverLegStateMapper）"

red "$V3" "
n = 'import com.shanxiang.buspost.driver.LocationReportPolicy\n'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, n + 'import com.shanxiang.buspost.data.model.MissionStop\n', 1)
"
ctl MR-r "视觉层 import 业务模型：画法的一层开始读业务数据"

red "$BM" "
n = '                    val v3d = Map3DVisual.marker3DOf(m.kind, m.visual)'
assert s.count(n) == 1, 'ANCHOR-MISS'
s = s.replace(n, n + '\n                Running.waiting.value = \"NEG-CONTROL 画一笔顺手写服务状态\"', 1)
"
ctl MR-r "视觉层写共享状态（画的副作用回灌服务侧）"

red "$V3T" "
i = s.find('\n    @Test')
assert i >= 0, 'ANCHOR-MISS'
s = s[:i] + '\n    //@Test' + s[i+10:]
"
ctl MR-r "视觉层行为用例被注释掉一条（16 条不再是 16）"


# ── MR-s / MR-t / MR-u（2026-10-10 D-070 那三处改动的出生证明）──────────────────
# 三条各打一个可独立坏掉的点；GD/CG 是本轮新加的扫描面对象。
GD="$A/ui/driver/DriverGuideScreen.kt"
CG="$A/ui/driver/CargoGauge.kt"

# MR-s：把导航卡那一格的名字改掉（等价于"退回嵌在路线支里、不再是独立一格"）
sed -i 's#item(key = "navCard") {#item(key = "map") {#' "$WB"
ctl MR-s "导航卡不再是独立一格（名字被换掉 ⇒ 挂点回退到路线条件）"

# MR-t：地图不再现量两条自绘条的高度，退回写死一个 dp
sed -i 's#padding(top = topReserve, bottom = bottomReserve)#padding(top = 170.dp)#' "$GD"
ctl MR-t "地图退回写死避让高度：高德的放大图会重新画回标题底下"

# MR-u：仓位填充方向翻回"空余"
sed -i 's#(100f - it.toFloat())#(it.toFloat())#' "$CG"
ctl MR-u "填充用回空余本身：一件没装会被画成整车涂满"

echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
