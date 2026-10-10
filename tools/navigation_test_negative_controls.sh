#!/usr/bin/env bash
# 第 8 步导航引擎**用例**的负控制：把判据层逐个破口 → gradle 用例必须红，且红在 NavigationContractTest 里。
# 与 check_navigation_probes.sh / navigation_negative_controls.sh 的分工：那两套证**结构**没跑调，
# 这一条证 45 条用例不是装饰 —— 实现坏了它们会叫。
# M1…M8 钉引擎本体与目的地选取（#49/#51），M9…M11 钉导航入口闸门（F-3 ④⑫ / D-048）：
# 把"还在等回执"或"这一单没有可导路线"伪装成可点，用例必须当场叫出来。
# M12…M15 钉 §九 路线绑定（F-3 ⑭ / V21-09）：绑定退化成只比点数、没有凭证也放行、
# 三种成因并成一句话、指纹换成不含终点的哈希 —— 这四样都是"看起来在验证其实是摆设"的形态。
# M16…M18 钉 N-2（赛道 ⑨）的两张表：砍掉一个已定义的转向码、给一个实测不存在的码编句话、
# 把表外的播报类型就近并入"导航提示" —— 这三种都不是"少写了一行"，而是把没有的念成有、把有的念成没有。
# M19…M26 钉 N-3（赛道 ⑩）的车道族：砍/编车道码、把解释不了的五条硬翻、把"没给"念成"没有车道"、
# 把短数组补齐、把没核过语义的 byte[] 说成推荐车道、把三个数的自洽检查改成恒真 —— 全是"看着更完整"的方向。
# M27…M33 钉 N-4（赛道 ⑪）的重算族：两句广播并成一句、"高德没说过"念成"当前没有偏航"、
# 给没有结局的广播补一句"重算完成"、把不带原因的回执配成"那次偏航重算成功"、把高德的 errorCode 改成
# 本机分档、把"本机不判偏航"换成一个 60 米阈值、替身引擎自己写一帧假的 —— 全是把推断写成事实的方向。
# ⚠ 本脚本会**就地临时修改源码**并在结束时原样恢复；一次只允许跑一个改源码的负控脚本，
#   跑完必须看到基线绿 + 恢复后重新全绿，否则视为未收口。
# 用法：bash tools/navigation_test_negative_controls.sh
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2
NE=app/src/main/java/com/shanxiang/buspost/core/navigation/NavigationEngine.kt
JVMARGS='-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=GBK'
# 注意：这一本只跑 CLS 里列出的类。新增用例本若忘了加进来，针对它的注入会报「用例仍然全绿」，
#   看着像负控失效，其实是根本没跑到（M37 第一次就是这么假失败的）。
CLS='--tests com.shanxiang.buspost.core.navigation.NavigationContractTest --tests com.shanxiang.buspost.core.navigation.AutoGuideDecisionTest'
XML="app/build/test-results/testDebugUnitTest/TEST-com.shanxiang.buspost.core.navigation.NavigationContractTest.xml app/build/test-results/testDebugUnitTest/TEST-com.shanxiang.buspost.core.navigation.AutoGuideDecisionTest.xml"
BAK=/tmp/nav_neg_bak.$$
mkdir -p "$BAK"; cp "$NE" "$BAK/"
trap 'cp "$BAK/NavigationEngine.kt" "$NE"; rm -rf "$BAK"; negctl_release' EXIT
restore() { cp "$BAK/NavigationEngine.kt" "$NE"; }

red() { # $1=文件 $2=python 表达式（锚点不在必须报错；注入后字节没变也算空跑，都不许往下跑）
  local before after
  before=$(md5sum < "$1")
  python - "$1" "$2" <<'PY'
import sys
p, expr = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8').read()
exec(expr)
open(p, 'w', encoding='utf-8').write(s)
PY
  [ "$?" = 0 ] || { echo "✘ 锚点没命中，破口根本没注入（$2）"; exit 2; }
  after=$(md5sum < "$1")
  # 2026-10-01 踩的坑：锚点在、但 replace 没吃到（在带 CRLF 的文件里找含换行的目标串），于是 gradle 跑的其实是**未修改**的源码，
  # 撞上 build cache 里那份绿色结果就被记成「✘ 用例仍然全绿，这条判据证明不了任何东西」——一条假发现。
  # 空跑与判据失效必须分成两种输出，前者是本书的缺陷（exit 5），后者才是用例的缺陷。
  [ "$before" != "$after" ] || { echo "✘ 注入后文件字节未变（空跑），拒绝记成判据失效：$2"; exit 5; }
}

fails() { # 失败数只从测试产物读，不读 gradle 的措辞
  python - "$1" <<'PY'
import sys, os, xml.etree.ElementTree as ET
# 汇总 CLS 里跑到的**所有**用例本：只看一份 XML 会让新加的测试本看不见，
# 于是注入明明把 gradle 打红了（exit=1），计数却还是 0/58，整条负控被报成「两者不一致」。
files = [f for f in sys.argv[1:] if os.path.exists(f)]
if not files:
    print("NOXML"); raise SystemExit
bad = tot = 0
for f in files:
    r = ET.parse(f).getroot()
    bad += int(r.get("failures") or 0) + int(r.get("errors") or 0)
    tot += int(r.get("tests") or 0)
print("%s/%s" % (bad, tot))
PY
}

case_run() { # $1=编号 $2=破口说明
  # ⚠ `local` 自带退出码 0：写成 `local code;` 再接 `code=$?` 读到的是 local 的，不是 gradle 的
  #    （本轮第一版就栽在这里：gradle 明明 FAILED，报告却写 exit=0「用例仍然全绿」）。
  #    变量先单独声明，gradle 之后**立刻**赋值，取码不能跨语句。
  local code n
  [ -s "$NE" ] || { echo "✘ $1 源码为空，本轮不采信"; return; }
  # 空跑反证（本书自己的缺陷，不是用例的）：锚点串里混进 `$` 时，bash 在**展开阶段**就报 bad substitution，
  # red 那条命令根本没执行、源码还是基线；这时 gradle 跑的是没改过的代码，全绿会被记成
  # 「✘ 用例仍然全绿，这条判据证明不了任何东西」——一条假发现。red 里的字节比对挡不住这种，因为比对压根没跑到。
  cmp -s "$BAK/NavigationEngine.kt" "$NE" && { echo "✘ $1 注入没落地：源码与基线逐字节相同（空跑），拒绝跑 gradle，更拒绝记成判据失效"; exit 5; }
  rm -f $XML
  ./gradlew $JVMARGS :app:testDebugUnitTest $CLS > "/tmp/nav_neg_$1.log" 2>&1
  code=$?
  n=$(fails $XML)
  if [ "$n" = "NOXML" ]; then
    echo "✘ $1  $2 → 没有测试产物（多半是编译破了而不是语义破了），这条不算证词"
  elif [ "$code" != 0 ] && [ "${n%/*}" != "0" ]; then
    printf '✔ %s  %s → gradle exit=%s，红了 %s 例（共 %s）\n' "$1" "$2" "$code" "${n%/*}" "${n#*/}"
  elif [ "$code" = 0 ] && [ "${n%/*}" = "0" ]; then
    echo "✘ $1  $2 → 用例仍然全绿（exit=0 failures=0），这条判据证明不了任何东西"
  else
    echo "△ $1  $2 → exit=$code 而产物 failures=${n%/*}/tests=${n#*/}：两者不一致，按未收口处理"
  fi
  restore
}

echo "[基线]"; ./gradlew $JVMARGS :app:testDebugUnitTest $CLS > /tmp/nav_neg_baseline.log 2>&1
BASE=$?
if [ "$BASE" != 0 ]; then echo "✘ 基线就红（exit=$BASE），下面每条都失去意义"; exit 1; fi
echo "  ✔ 基线 exit=0 failures=$(fails $XML)"
echo

# M1 把"高德没给步骤号"伪造成本机推进的起点（-1 → 0）
red "$NE" "
old = 'sdkStepIndex = if (curStep < 0) -1 else curStep,'
new = 'sdkStepIndex = if (curStep < 0) 0 else curStep,'
assert old in s, 'ANCHOR-MISS M1'
s = s.replace(old, new, 1)
"
case_run M1 "没给的步骤号被伪造成 0"

# M2 没给的道路名被填成一句像样的话
red "$NE" "
old = 'currentRoad = currentRoadName.orEmpty(),'
new = 'currentRoad = currentRoadName ?: \"无名道路\",'
assert old in s, 'ANCHOR-MISS M2'
s = s.replace(old, new, 1)
"
case_run M2 "空道路名被填成「无名道路」"

# M3 判序破口：缺 key 的设备在回调回来前一律显示「初始化中」，配置缺失被念成进行中
red "$NE" "
key_branch = '''    !keyConfigured -> EngineReadiness.Blocked(
        code = NavigationGateCode.SDK_UNAVAILABLE,
        reason = \"导航能力未就绪：没有配置高德 key\",
        detail = \"keyConfigured=false（缺 key 时 SDK 不会回调，导航不能算可用）\",
    )
'''
wait_branch = '''    initResult == null -> EngineReadiness.Initializing
'''
assert key_branch in s and wait_branch in s, 'ANCHOR-MISS M3'
s = s.replace(key_branch + '\n' + wait_branch, wait_branch + '\n' + key_branch, 1)
"
case_run M3 "readinessOf 的判序倒过来"

# M4 声明的坐标系换成 WGS-84：折线会在展示层被二次偏移
red "$NE" "
old = 'crsRaw = CoordinateSystem.GCJ02.name,'
new = 'crsRaw = CoordinateSystem.WGS84.name,'
assert old in s, 'ANCHOR-MISS M4'
s = s.replace(old, new, 1)
"
case_run M4 "高德折线被当成 WGS-84（二次偏移）"

# M5 绕过那唯一一道真实道路闸门：整段替换 validate 调用
# （破口本身必须能编译 —— 在函数头上插一段会留下悬空实参，那是"编译破了"的假证词，第一版就写成那样）
red "$NE" "
old = '''): RouteCheck = RealRoadRouteValidator.validate(
    sourceRaw = RouteSource.AMAP.name,
    points = points,
    roadDistanceMeters = roadDistanceMeters,
    roadEtaSeconds = roadEtaSeconds,
    crsRaw = CoordinateSystem.GCJ02.name,
)'''
new = '''): RouteCheck = RouteCheck.Accepted(
    com.shanxiang.buspost.core.map.RealRoadRoute(
        source = RouteSource.AMAP,
        crs = CoordinateSystem.GCJ02,
        points = points,
        roadDistanceMeters = roadDistanceMeters,
        roadEtaSeconds = roadEtaSeconds,
    ),
)'''
assert old in s, 'ANCHOR-MISS M5'
s = s.replace(old, new, 1)
"
case_run M5 "算路结果不过真实道路闸门，直接当 Accepted"

# M6 目的地改用数组顺序：客户端开始替调度排路线
red "$NE" "
old = 'guide.stops.filter { !it.isDone && it.hasGeo }.minByOrNull { it.visitSequence }'
new = 'guide.stops.filter { !it.isDone && it.hasGeo }.firstOrNull()'
assert old in s, 'ANCHOR-MISS M6'
s = s.replace(old, new, 1)
"
case_run M6 "目的地按数组顺序而非后端 visitSequence"

# M7 把后端标为失败的任务点也跳掉：等于客户端替调度改了路线
red "$NE" "
old = 'guide.stops.filter { !it.isDone && it.hasGeo }.minByOrNull { it.visitSequence }'
new = 'guide.stops.filter { !it.isDone && !it.isFailed && it.hasGeo }.minByOrNull { it.visitSequence }'
assert old in s, 'ANCHOR-MISS M7'
s = s.replace(old, new, 1)
"
case_run M7 "失败任务点被跳掉"

# M8 替身引擎宣称自己开始了导航
# ⚠ 锚点在 N-2（赛道 ⑨）换过一次形状：`startGpsNavi()` 多了 voiceOn 形参、返回体下移一行。
#   这条锚点是**两行**的，读的是 python 统一换行后的文本，所以 CRLF 文件里也能命中（M5/M9 同法）。
red "$NE" "
old = '''    override fun startGpsNavi(voiceOn: Boolean): NavigationSession =
        NavigationSession.Refused(blockedReadiness.userText)'''
new = '''    override fun startGpsNavi(voiceOn: Boolean): NavigationSession =
        NavigationSession.Running(naviType = 2, startedBySdk = true)'''
assert old in s, 'ANCHOR-MISS M8'
s = s.replace(old, new, 1)
"
case_run M8 "替身引擎返回 Running"

# ---- M9…M11：F-3 ④⑫ 导航入口闸门（navigationGateOf）的三条破口 ----

# M9 把"还在等高德回初始化结果"当成可点：入口与判据层重新分叉（UI 老写法犯的就是这个）
red "$NE" "
old = '''    EngineReadiness.Initializing -> NavigationGate(
        code = NavigationGateCode.INITIALIZING,'''
new = '''    EngineReadiness.Initializing -> NavigationGate(
        code = NavigationGateCode.READY,'''
assert old in s, 'ANCHOR-MISS M9'
s = s.replace(old, new, 1)
"
case_run M9 "Initializing 被念成 READY"

# M10 没有定位也让点：客户端会拿终点/默认点顶替起点（假导航的第一步）
red "$NE" "
old = '''        !hasOrigin -> NavigationGate(
            code = NavigationGateCode.NO_ROUTE,'''
new = '''        !hasOrigin -> NavigationGate(
            code = NavigationGateCode.READY,'''
assert old in s, 'ANCHOR-MISS M10'
s = s.replace(old, new, 1)
"
case_run M10 "缺起点也判可点"

# M11 Blocked 的成因码被统一顶成 ERROR：缺组件、缺 key、初始化失败分不出该修哪个
red "$NE" "
old = '        code = readiness.code,'
new = '        code = NavigationGateCode.ERROR,'
assert old in s, 'ANCHOR-MISS M11'
s = s.replace(old, new, 1)
"
case_run M11 "Blocked 不再带自己的成因码"

# ---- M12…M15：F-3 ⑭ §九 路线绑定（routeBindingOf / AcceptedRouteSession）的四条破口 ----

# M12 绑定只比点数：高德偏航重算后完全可能给出一条点数巧合相同的路径，那时它就不是同一条路了
red "$NE" "
old = '    currentPath.coordCount != accepted.coordCount || currentPath.roadDistanceMeters != accepted.roadDistanceMeters ->'
new = '    currentPath.coordCount != accepted.coordCount ->'
assert old in s, 'ANCHOR-MISS M12'
s = s.replace(old, new, 1)
"
case_run M12 "绑定丢掉道路米数那一项"

# M13 本机压根没有凭证也放行（§九 硬条件"没有 accepted route 禁止 startGpsNavi"被拆掉）。
# 破口写成恒真的 if 而不是直接删分支：删了分支后面的 accepted.routeFingerprint 就失去智能转换，
# 那是"编译破了"的假证词（M5 的教训同一条）。
red "$NE" "
old = '''    accepted == null -> RouteBinding.Unbound('''
new = '''    accepted == null -> if (true) RouteBinding.Bound else RouteBinding.Unbound('''
assert old in s, 'ANCHOR-MISS M13'
s = s.replace(old, new, 1)
"
case_run M13 "没有 accepted 记录也判成同一条"

# M14 三种成因并成一句话：现场只看到"不能导航"，查不出该重新算还是该等高德回路径
red "$NE" "
old = '        reason = \"高德当前没有路径几何，本机无法确认要导哪一条\",'
new = '        reason = \"本机还没有一次通过验证的算路，不能开始导航\",'
assert old in s, 'ANCHOR-MISS M14'
s = s.replace(old, new, 1)
"
case_run M14 "「高德没有路径」与「本机没算成过」共用一句"

# M15 指纹换成不含终点的哈希：终点变了指纹可能不变（§九 那条作废依据失效），而且这台机器上没人能解释那串字符
red "$NE" "
old = '        get() = \"to=%.6f,%.6f|pts=%d|m=%d\".format(destinationLat, destinationLng, coordCount, roadDistanceMeters)'
new = '        get() = java.security.MessageDigest.getInstance(\"SHA-1\").digest((coordCount.toString() + roadDistanceMeters).toByteArray()).toString()'
assert old in s, 'ANCHOR-MISS M15'
s = s.replace(old, new, 1)
"
case_run M15 "指纹换成哈希、终点不再进指纹"

# ---- M16…M18：N-2（赛道 ⑨）两张表的三条破口 ----

# M16 砍掉一个**已定义**的转向码（9=STRAIGHT 是 javap 实测值）：高德给了直行，页面念成"没给"
red "$NE" "
old = '    9 -> \"直行\"\n'
assert old in s, 'ANCHOR-MISS M16'
s = s.replace(old, '', 1)
"
case_run M16 "已定义的转向码 9 从表里消失"

# M17 给实测不存在的码编一句话术（55..64 在 navi-3dmap 10.0.600 里根本没有定义）
red "$NE" "
old = '    54 -> \"通过平路口\"\n'
new = '    54 -> \"通过平路口\"\n    55 -> \"并线并入直行\"\n'
assert old in s, 'ANCHOR-MISS M17'
s = s.replace(old, new, 1)
"
case_run M17 "表外的 55 被编了一个动作"

# M18 表外的播报类型就近并入"导航提示"：高德没说类型时页面会替它说一类
# 锚点带上 6 那一行：`    else -> null` 在两份文件里出现两次（转向表也有），只锚 else 会打到错的那张表
red "$NE" "
old = '    6 -> \"偏航播报\"\n    else -> null'
new = '    6 -> \"偏航播报\"\n    else -> \"导航提示\"'
assert old in s, 'ANCHOR-MISS M18'
s = s.replace(old, new, 1)
"
case_run M18 "表外与没给类型码被并成 1 号类型"

# ---- M19…M26：N-3（赛道 ⑩）车道两张表与车道帧的八条破口 ----
# 与 M16…M18 同一族形状：表还在、页面还在、编译也不报错，但"高德给了什么"被换成了"本机想它是什么"。
# 全部只动纯层 NavigationEngine.kt（本书只备份这一个文件），锚点一律不含 `$`：
# expr 是 bash 双引号串传进来的，`$code` 会被 shell 先展成空串，锚点就打不中（那会被记成"锚点没命中"而不是破口）。

# M19 砍掉一个 javap 实测存在的车道码（255=LANE_ACTION_NULL）：高德给了"无车道动作"，页面念成表外
red "$NE" "
old = '    255 -> \"LANE_ACTION_NULL\"\n'
assert old in s, 'ANCHOR-MISS M19'
s = s.replace(old, '', 1)
"
case_run M19 "已定义的车道码 255 从名字表里消失"

# M20 给实测不存在的码编一个名字（LaneAction 在这个版本里是 0..25 连号 + 255，26 不存在）
red "$NE" "
old = '    25 -> \"LANE_ACTION_TIDAL\"\n'
new = '    25 -> \"LANE_ACTION_TIDAL\"\n    26 -> \"LANE_ACTION_AHEAD_TIDAL\"\n'
assert old in s, 'ANCHOR-MISS M20'
s = s.replace(old, new, 1)
"
case_run M20 "表外的 26 被编了一个车道名"

# M21 把解释不了的那五条硬翻出来（13=LEFT_IN_AHEAD：in 指什么，包里没有任何材料说得出）
red "$NE" "
old = '    16 -> \"直行或左转或左掉头\"\n'
new = '    13 -> \"直行加左转\"\n    16 -> \"直行或左转或左掉头\"\n'
assert old in s, 'ANCHOR-MISS M21'
s = s.replace(old, new, 1)
"
case_run M21 "LEFT_IN_AHEAD 被本机硬翻成一句人话"

# M22 表外那一格被换掉：三张表行数一个都不掉，但一个高德没定义过的码会被念成一条真车道
red "$NE" "
old = 'LaneAction 表里没有的车道码'
new = '这里就不细说的车道'
assert old in s, 'ANCHOR-MISS M22'
s = s.replace(old, new, 1)
"
case_run M22 "表外车道码不再被明说是表外"

# M23 把短的那个数组补到 laneCount：三个数对不上时本机开始"凑车道"（凑出来的那条道高德没给过）
red "$NE" "
old = '    frontCodes = frontLane?.toList().orEmpty(),'
new = '    frontCodes = frontLane?.toList().orEmpty() + List(laneCount) { 0 },'
assert old in s, 'ANCHOR-MISS M23'
s = s.replace(old, new, 1)
"
case_run M23 "frontLane 被按 laneCount 补齐"

# M24 「高德没给车道」被念成「这条路没有车道」：四格里最要紧的那一格并错了对象
red "$NE" "
old = '    null -> \"高德没给车道信息（这不等于这条路没有车道）\"'
new = '    null -> \"这条路没有车道\"'
assert old in s, 'ANCHOR-MISS M24'
s = s.replace(old, new, 1)
"
case_run M24 "没给过被念成高德说没有"

# M25 数组重载那两个 byte[] 被解释成"推荐车道"：javap 只给了类型，语义没有任何来源
red "$NE" "
old = '组车道（数组重载）；它附带的两个 byte[] 语义本机没核过，不替你挑哪一组'
new = '组车道（数组重载），第一个 byte[] 就是推荐车道，走它'
assert old in s, 'ANCHOR-MISS M25'
s = s.replace(old, new, 1)
"
case_run M25 "没核过语义的 byte[] 被念成推荐车道"

# M26 三个数的自洽检查被改成恒真：数组与 laneCount 对不上时页面不再说出来
red "$NE" "
old = '    val countsAgree: Boolean get() = backgroundCount == laneCount && frontCount == laneCount'
new = '    val countsAgree: Boolean get() = true'
assert old in s, 'ANCHOR-MISS M26'
s = s.replace(old, new, 1)
"
case_run M26 "countsAgree 被改成恒真"

# ---- N-4（赛道 ⑪ 偏航重算）的 M27…M33 ----
# 这一族的破口全是"把没有的说成有"：两句广播并成一句、"高德没说过"念成"当前没有偏航"、
# 给没有结局的广播补一句"重算完成"、把不带原因的回执配成"那次偏航重算成功"、
# 把高德那个 errorCode 改成本机分档、把"本机不判偏航"换成一个 60 米阈值、替身引擎自己写一帧假的。

# M27 拥堵那句被换成偏航那句（两格并成一格：司机去检查一条根本没走过的错）
red "$NE" "
old = '        \"高德说前方拥堵，它要重新算路（拥堵在哪一段、多重，高德没说）\"'
new = '        \"高德说车辆偏离了它给的这条路线，它要重新算路（回调无入参：偏了多少米、往哪偏，高德没说）\"'
assert old in s, 'ANCHOR-MISS M27'
s = s.replace(old, new, 1)
"
case_run M27 "拥堵那一格被念成偏航（两格并成一格）"

# M28 "高德没说过"被念成"当前没有偏航"：本机替高德宣布了一个它没说过的否定结论
red "$NE" "
old = '    null -> \"高德没说过它在重新算路（偏航与拥堵都由高德自己说，本机不拿距离卡阈值判偏航）\"'
new = '    null -> \"当前没有偏航\"'
assert old in s, 'ANCHOR-MISS M28'
s = s.replace(old, new, 1)
"
case_run M28 "没给过被念成高德说没有偏航"

# M29 广播那一格被补上结局（高德只说"它要重新算路"，成没成走的是不带原因的回执）
red "$NE" "
old = '它要重新算路（回调无入参：偏了多少米、往哪偏，高德没说）\"'
new = '它要重新算路，这次会重算完成（回调无入参：偏了多少米、往哪偏，高德没说）\"'
assert old in s, 'ANCHOR-MISS M29'
s = s.replace(old, new, 1)
"
case_run M29 "偏航广播那一格被宣布重算完成"

# M30 不带原因的回执被配成因果（AMapNavi 是进程单例，那次成功未必是这次广播的成功）
red "$NE" "
old = '；这条回执不带原因，本机不说它就是刚才那次偏航或拥堵的结果\"'
new = '；它就是刚才那次偏航重算成功的结果\"'
assert old in s, 'ANCHOR-MISS M30'
s = s.replace(old, new, 1)
"
case_run M30 "算路成功回执被配成那次偏航的结果"

# M31 高德的 errorCode 被本机改成固定分档（句子里那个数不再是高德给的）
# ⚠ 这段锚点里一个 `$` 都不能有：它是 bash **双引号**串，源码那句本来带 `${display.code}`，
#   照抄进锚点会在展开阶段就死（第一轮 M31 正是这样假报了一条 ✘）。改按行前缀定位、整行换成一个写死的数。
red "$NE" "
lines = s.split('\n')
hit = [n for n, x in enumerate(lines) if x.strip().startswith('\"高德自己回了一次算路失败（errorCode=')]
assert len(hit) == 1, 'ANCHOR-MISS M31：前缀命中 %d 行，应为 1' % len(hit)
lines[hit[0]] = '        \"高德自己回了一次算路失败（errorCode=0）；这条回执不带原因，本机不猜它要重算的那条成没成\"'
s = '\n'.join(lines)
"
case_run M31 "失败回执里的 errorCode 被本机写死成 0"

# M32 "本机不判偏航"换成一个本机挑的阈值（60 米这个数没有任何来源）
red "$NE" "
old = '本机不拿距离卡阈值判偏航）\"'
new = '偏离超过 60 米才算偏航）\"'
assert old in s, 'ANCHOR-MISS M32'
s = s.replace(old, new, 1)
"
case_run M32 "偏航判定被换成本机 60 米阈值"

# M33 替身引擎自己写一帧偏航：没有高德也没有路线的一档开始报"高德说偏航了"
red "$NE" "
old = '    private val emptyRecalc = MutableStateFlow<EngineRecalcDisplay?>(null)'
new = '    private val emptyRecalc = MutableStateFlow<EngineRecalcDisplay?>(EngineRecalcDisplay.Yaw)'
assert old in s, 'ANCHOR-MISS M33'
s = s.replace(old, new, 1)
"
case_run M33 "替身引擎的重算流被填成 Yaw"

# ---- N-8（赛道 ⑯：开关那一格的话术）的 M34…M36 ----
# 这一族的三条各打中一条用例，互不重叠：M34 打"不许替高德宣告给过什么"、M35 打"开关名照抄设置页那一行"、
# M36 打"两条开关句不互相套用机制"。锚点里出现的 `$` 一律走 chr(36)：
# expr 是 bash **双引号**串，`${switchName}` 照抄进去会在展开阶段就被吃掉（M31 那一轮踩过的假报）。

# M34 出口开始替高德宣告"给过车道"：开关关着时本机并不知道高德有没有推过帧
red "$NE" "
old = '开关是关的（在「我的」里），本机'
new = '开关是关的（在「我的」里），高德给的车道由本机'
assert s.count(old) == 1, 'ANCHOR-MISS M34：命中 %d 处' % s.count(old)
s = s.replace(old, new, 1)
"
case_run M34 "开关那一格开始替高德宣告给过什么"

# M35 那句不再带开关名（司机按卡片找不到那条开关）：出口把参数丢了，两句都变成"司机开关"
red "$NE" "
old = chr(36) + '{switchName}开关是关的'
new = '司机开关是关的'
assert s.count(old) == 1, 'ANCHOR-MISS M35：命中 %d 处' % s.count(old)
s = s.replace(old, new, 1)
"
case_run M35 "那句不再点名「我的」里是哪条开关"

# M36 车道那句被硬写进共用出口：播报行开始说"不显示"，两种机制（本机没显示 / 本机没让高德播）被说成一件事
red "$NE" "
old = '，本机' + chr(36) + 'detail\"'
new = '，本机这一格不显示车道\"'
assert s.count(old) == 1, 'ANCHOR-MISS M36：命中 %d 处' % s.count(old)
s = s.replace(old, new, 1)
"
case_run M36 "两条开关句被并成同一种机制"

# M37 「自动进导航」丢掉进程内一次性：结束导航后会被立刻弹回，司机出不去（真机实测过）
red "$NE" "
old = 'canNavigate && hasNavigableStop && !alreadyOpenedThisProcess'
new = 'canNavigate && hasNavigableStop'
assert s.count(old) == 1, 'ANCHOR-MISS M37：命中 %d 处' % s.count(old)
s = s.replace(old, new, 1)
"
case_run M37 "自动进导航不再受进程内一次性约束"

echo
cmp -s "$BAK/NavigationEngine.kt" "$NE" && echo "✔ 恢复核对：与改前基线逐字节相同" || echo "✘ 恢复失败：源码与基线不一致"
./gradlew $JVMARGS :app:testDebugUnitTest $CLS > /tmp/nav_neg_after.log 2>&1
AFTER=$?
echo "[恢复后复跑] exit=$AFTER failures=$(fails $XML)"
[ "$AFTER" = 0 ] && echo "✔ 全部破口已回滚，用例重新全绿" || { echo "✘ 恢复后仍红，本轮未收口"; exit 1; }
