#!/usr/bin/env bash
# 赛道⑫⑬ 3D 视觉层**用例**的负控制（指令 §八 赛道⑫⑬ / 队列 #216 / N-5）：逐条改坏源码，要求
# `Map3DVisualContractTest` 里**具名那一条**必须红。
#
# 分工（与 ⑧ 那一族同形，见 §八 ㉛）：
#   `tools/mission_route_negative_controls.sh` 的 MR-l…MR-r = **结构**半边（判"写法有没有被人换掉"）；
#   本书 = **行为**半边（判"那 16 条用例是不是装饰"）——只有 gradle 真跑才成立。
#   一本结构簿 + 一本行为簿共同覆盖 ⑧ 与 ⑫⑬，所以本书与 `mission_route_test_negative_controls.sh`
#   分开写：那个文件头部预留了 `D-⑧-2`（MT-4 编号留空）这一格在册覆盖面缺口，把 ⑫⑬ 的破口塞进同一本
#   会把两族的收口状态互相顶掉（同一处"MT-4 留空"不能同时是⑧的缺账和⑫⑬的起点）。
#
# 16 条破口与 16 条用例**逐条一对一**（MV-1…MV-16 按用例在文件里的出现顺序编号）：
#   MV-1  rotateOf 的 null 兜底成 0f            → `朝向未给时不落到正北`（V21-15 点名的就是这个形状）
#   MV-2  摘掉显示层的 360°→0° 回折              → `朝向归一到 0 到 360 且不写出一个不存在的角`
#   MV-3  政策阈值之上再叠一个本机数字（第二个真相）→ `新鲜度三态用的是政策那个停报阈值`
#   MV-4  非 LIVE 也把旧朝向带进画面             → `非 LIVE 时不把旧朝向带进画面`
#   MV-5  兜底句改成"后端说没有"                 → `一条 fix 都没有时也不说成后端没有位置`
#   MV-6  车辆 marker 也跟着转                   → `车辆 marker 不因折线推断而旋转`
#   MV-7  任务点也跟着转（转的权利不再只给"我"）    → `只有我这格按 GPS 朝向旋转`
#   MV-8  浮空不再只给任务点                     → `只有任务点浮空其余贴地`
#   MV-9  降级态的"我"仍然压在最上面              → `当前任务压住下一任务且降级时我不冒充在线`
#   MV-10 LIVE 也要多说一句                      → `正常定位时脚注不多一句话`
#   MV-11 STALE 不转述服务原话、自己编一句         → `降级脚注转述服务原话而不是重写一句`
#   MV-12 UNAVAILABLE 把服务给的原因丢掉           → `定位不可用时说清显示的不是实时车位`
#   MV-13 下一任务不再排除已完成/失败             → `当前任务跟随后端状态下一任务取其後第一个未完成`
#   MV-14 没有后端进行中状态时不再退回导航焦点      → `没有后端进行中状态时才退回导航焦点`
#   MV-15 高亮当前任务时顺手把 kind 改成进行中      → `高亮是视觉不改动任务状态与配色`（动了业务）
#   MV-16 "我"这一格的详情把 null 朝向写成 0°      → `我这格的详情里就写明朝向有没有`
#   ⚠ 一条破口可能同时红掉多条用例（例如 MV-1 会让第 7、16 两条一起红）——判据只要求**具名那一条在场**，
#     多红不扣分；反过来"gradle 红了但红的不是那一条"记 ✘，不能算这条破口被咬住。
#
# ⚠ 本脚本会**就地临时修改源码**并在结束时原样恢复；一次只允许跑一个（negctl_lock）。
#   退出前必须看到 [恢复] cmp 一致 + 用例重新通过 + 结构判据重新通过，否则视为未收口。
# ⚠ 起跑前有一道**预检**（16 条锚点各恰好命中 1 次 + 16 条具名用例各在测试文件里命中 1 次），
#   预检不过直接 exit 5，**一条破口都不注入**。理由：锚点若已漂，逐条注入会一边报"锚点没命中"
#   一边继续烧掉十几轮 gradle，最后既没证据也没时间；先只做只读核对（㊱ ⑦ 同族：注入不发生
#   时最像"批次跑完了"，必须把不发生的原因在起跑前就说清）。
# ⚠ 跑过 `--tests` 过滤之后 test-results 目录只剩这一本 ⇒ 读 test-results 的判据簿
#   （notify_result / oss_credits）会 rc=2，必须等全量 `--rerun-tasks` 之后再单本复跑。
#   台账口径同 D-052 那条：本簿若晚于负控全批落盘，批次那行的"共 N 本"**不含本簿**，本簿以单本实测计。
# 用法：bash tools/map3d_visual_negative_controls.sh
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2

V3=app/src/main/java/com/shanxiang/buspost/ui/common/Map3DVisual.kt
MM=app/src/main/java/com/shanxiang/buspost/ui/driver/MissionMarkers.kt
CT=app/src/test/java/com/shanxiang/buspost/ui/common/Map3DVisualContractTest.kt
BAKV3=/tmp/m3v_v3.bak.$$
BAKMM=/tmp/m3v_mm.bak.$$
CLS=com.shanxiang.buspost.ui.common.Map3DVisualContractTest
JVMARGS='-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=GBK'
PROBE=tools/check_mission_route_probes.sh
LOGDIR=/tmp/m3v
mkdir -p "$LOGDIR"

# 预检零：三个落点都要在场，缺任何一个就是基线不成立（而不是把"0 命中"演成绿）
[ -f "$V3" ] || { echo "✘ 缺 $V3，MV-1…MV-12 的负控全部作废"; exit 2; }
[ -f "$MM" ] || { echo "✘ 缺 $MM，MV-13…MV-16 的负控全部作废"; exit 2; }
[ -f "$CT" ] || { echo "✘ 缺 $CT，本书作废"; exit 2; }

cp "$V3" "$BAKV3"
cp "$MM" "$BAKMM"
restore_v3() { cp "$BAKV3" "$V3"; }
restore_mm() { cp "$BAKMM" "$MM"; }
restore() { restore_v3; restore_mm; }
trap 'restore; rm -f "$BAKV3" "$BAKMM"; negctl_release' EXIT

IDS=(); FILES=(); OLDS=(); NEWS=(); NAMEDS=(); DESCS=()
# 锚点一律用**单引号**字面量传进来：双引号里的 `$holdReason` 会被 bash 先展开，注入静默不发生（㊱ ⑦）
reg() { IDS+=("$1"); FILES+=("$2"); OLDS+=("$3"); NEWS+=("$4"); NAMEDS+=("$5"); DESCS+=("$6"); }

reg 'MV-1' "$V3" \
  '        headingDegrees?.let { (((it % 360.0) + 360.0) % 360.0).toFloat() }' \
  '        headingDegrees?.let { (((it % 360.0) + 360.0) % 360.0).toFloat() } ?: 0f' \
  '朝向未给时不落到正北' 'rotateOf 把 null 兜底成 0f（=正北），"没拿到"被画成一个真实方向'
reg 'MV-2' "$V3" \
  '        val shown = normalized.roundToInt().let { if (it == 360) 0 else it }' \
  '        val shown = normalized.roundToInt()' \
  '朝向归一到 0 到 360 且不写出一个不存在的角' '摘掉显示层的 359.6°→"360°" 回折，写出一个不存在的角'
reg 'MV-3' "$V3" \
  '        fixAgeMs > LocationReportPolicy.MAX_FIX_AGE_MS -> GpsFixState.STALE' \
  '        fixAgeMs > LocationReportPolicy.MAX_FIX_AGE_MS + 75_000L -> GpsFixState.STALE' \
  '新鲜度三态用的是政策那个停报阈值' '政策阈值之上再叠一个本机数字：屏幕说"还在有效期"而服务早已停报'
reg 'MV-4' "$V3" \
  '            headingDegrees = if (state == GpsFixState.LIVE) headingDegrees else null,' \
  '            headingDegrees = headingDegrees,' \
  '非 LIVE 时不把旧朝向带进画面' '非 LIVE 也把过期那拍的朝向原样带进画面'
reg 'MV-5' "$V3" \
  '    const val NO_FIX_NOTE = "暂时没拿到当前位置：显示的是调度安排，不是实时车位"' \
  '    const val NO_FIX_NOTE = "后端说没有本机定位，任务点与车辆位置也没有"' \
  '一条 fix 都没有时也不说成后端没有位置' '本机没拿到 fix 被念成"后端说没有"'
reg 'MV-6' "$V3" \
  '            rotateAngle = if (kind == MapMarker.KIND_ME) rotateOf(visual.headingDegrees) else null,' \
  '            rotateAngle = if (kind == MapMarker.KIND_ME || kind == MapMarker.KIND_BUS_REAL) rotateOf(visual.headingDegrees) else null,' \
  '车辆 marker 不因折线推断而旋转' '车辆那一格也开始转：把折线推断的朝向当成后端给的 GPS bearing'
reg 'MV-7' "$V3" \
  '            rotateAngle = if (kind == MapMarker.KIND_ME) rotateOf(visual.headingDegrees) else null,' \
  '            rotateAngle = if (kind == MapMarker.KIND_ME || kind == MapMarker.KIND_TASK_ACTIVE) rotateOf(visual.headingDegrees) else null,' \
  '只有我这格按 GPS 朝向旋转' '旋转的权利不再只给"我"：任务点也跟着转（站点没有朝向这回事）'
reg 'MV-8' "$V3" \
  '            altitudeMeters = if (isTask) TASK_ALTITUDE_METERS else 0f,' \
  '            altitudeMeters = if (isTask || kind == MapMarker.KIND_STOP) TASK_ALTITUDE_METERS else 0f,' \
  '只有任务点浮空其余贴地' '浮空不再只给任务点：起终点被抬到半空，点位与实际位置脱开'
reg 'MV-9' "$V3" \
  '            kind == MapMarker.KIND_ME && visual.degraded -> Z_ME_DEGRADED' \
  '            kind == MapMarker.KIND_ME && visual.degraded -> Z_ME' \
  '当前任务压住下一任务且降级时我不冒充在线' '定位降级时"我"仍然压在最上面（看起来还在线）'
reg 'MV-10' "$V3" \
  '        GpsFixState.LIVE -> ""' \
  '        GpsFixState.LIVE -> "定位正常，无需说明"' \
  '正常定位时脚注不多一句话' '一切正常时也要在地图上多刷一句（正常态被写成了异常态的音量）'
reg 'MV-11' "$V3" \
  '            if (holdReason.isBlank()) STALE_NOTE else "定位降级：$holdReason"' \
  '            if (holdReason.isBlank()) STALE_NOTE else "定位已降级（本页自己的说法）"' \
  '降级脚注转述服务原话而不是重写一句' '不转述服务给的原话，页面自己另写一句'
reg 'MV-12' "$V3" \
  '            if (holdReason.isBlank()) NO_FIX_NOTE else "暂时没拿到当前位置（$holdReason）：显示的是调度安排，不是实时车位"' \
  '            if (holdReason.isBlank()) NO_FIX_NOTE else NO_FIX_NOTE' \
  '定位不可用时说清显示的不是实时车位' '服务给了原因却被丢掉，病因只剩一句兜底'
reg 'MV-13' "$MM" \
  '        val candidates = visible.indices.filter { i -> i != current && !visible[i].isDone && !visible[i].isFailed }' \
  '        val candidates = visible.indices.filter { i -> i != current }' \
  '当前任务跟随后端状态下一任务取其後第一个未完成' '下一任务不再排除已完成/失败：把办完的站点指成"下一个要去"'
reg 'MV-14' "$MM" \
  '            .let { if (it >= 0) it else visible.indexOfFirst { focusStopId != 0L && it.stationId == focusStopId } }' \
  '            .let { if (it >= 0) it else -1 }' \
  '没有后端进行中状态时才退回导航焦点' '后端没给进行中状态时整幅图不再有任何高亮（导航焦点那一支被摘掉）'
reg 'MV-15' "$MM" \
  '                kind = taskKind(stop),' \
  '                kind = if (emphasis[i] == MarkerEmphasis.CURRENT) MapMarker.KIND_TASK_ACTIVE else taskKind(stop),' \
  '高亮是视觉不改动任务状态与配色' '高亮顺手把 kind 改成"进行中"：视觉层越界替后端推进了业务状态'
reg 'MV-16' "$MM" \
  '            detail = Map3DVisual.headingText(headingDegrees),' \
  '            detail = Map3DVisual.headingText(headingDegrees ?: 0.0),' \
  '我这格的详情里就写明朝向有没有' '"我"这一格的详情把 null 朝向写成 0°，读者以为有 GPS bearing'

# 覆盖面自指读数：本书声称的"16 条一对一"必须被自己数出来。注册表少一条（写漏、被编辑掉、
# 或有人往这里加了第 17 条而没改说法）都是预检与逐条注入之外**唯一**能在起跑前发现的一环——
# 不写这一格，空注册表会一路"绿"到底（预检 0/0 通过、注入 0 条、恢复后重新绿、RESULT=PASS）。
[ "${#IDS[@]}" = 16 ] || { echo "✘ 注册条数 = ${#IDS[@]}（本书头部声明的是 16 条一对一）⇒ 覆盖面与说法不符，不起跑"; exit 5; }

run_cls() { # $1 = 日志名
  ./gradlew $JVMARGS :app:testDebugUnitTest --tests "$CLS" > "$1" 2>&1
  echo "$?"
}
# 只读**本轮新写的**那份 XML：上一版批次踩过 build cache 的假绿（判据 §八 ㉓），
# 陈旧 XML 会被算成"红在 []"，那一格必须显式喊出来而不是当成"没红"。
red_names() { # $1 = 起跑秒
  M3V_T0="$1" PYTHONIOENCODING=utf-8 python - <<'PY'
import glob, os, time
t0 = float(os.environ.get("M3V_T0", "0"))
out, stale = [], 0
for fn in sorted(glob.glob("app/build/test-results/testDebugUnitTest/TEST-*Map3DVisual*.xml")):
    if os.path.getmtime(fn) < t0:
        stale += 1
        continue
    import xml.etree.ElementTree as ET
    r = ET.parse(fn).getroot()
    bad = [(tc.get("name") or "")[:70] for tc in r.iter("testcase")
           if tc.find("failure") is not None or tc.find("error") is not None]
    out.append("tests=%s failures=%s errors=%s 红在: %s" % (r.get("tests"), r.get("failures"), r.get("errors"), bad))
print("   " + (" | ".join(out) if out else "（本轮没有新的 Map3DVisual XML）"))
if stale:
    print("   ⚠ 有 %d 份 XML 比本次起跑更早（陈旧，未采信）" % stale)
PY
}
probe_rc() { bash "$PROBE" > "$LOGDIR/probe.log" 2>&1; echo $?; }

ok=0; bad=0; amb=0; scope=0; fail=0

# 预检一：每条锚点在目标文件里**恰好命中 1 次**，每条具名用例在测试文件里**恰好命中 1 次**。
# 命中 0 次 = 源码文本已经漂了（这一条的负控从来没打过）；命中 ≥2 次 = 替换会挑错那一处。
# 两种都必须先喊出来，而不是边注入边发现。
pre_bad=0
for i in "${!IDS[@]}"; do
  n=${IDS[$i]}; f=${FILES[$i]}; old=${OLDS[$i]}; named=${NAMEDS[$i]}
  cnt=$(M3V_F="$f" M3V_A="$old" PYTHONIOENCODING=utf-8 python - <<'PY'
import os
s = open(os.environ["M3V_F"], encoding='utf-8', newline='').read()
print(s.count(os.environ["M3V_A"]))
PY
)
  tcnt=$(M3V_T="$CT" M3V_N="$named" PYTHONIOENCODING=utf-8 python - <<'PY'
import os
s = open(os.environ["M3V_T"], encoding='utf-8', newline='').read()
print(s.count("fun `" + os.environ["M3V_N"] + "`"))
PY
)
  if [ "$cnt" != 1 ] || [ "$tcnt" != 1 ]; then
    echo "✘ 预检 $n 锚点命中=$cnt（要求 1）/ 具名用例命中=$tcnt（要求 1）"
    pre_bad=$((pre_bad + 1))
  fi
done
echo "预检 ${#IDS[@]} 条：锚点与具名用例各恰好命中 1 次的条数 = $(( ${#IDS[@]} - pre_bad )) / ${#IDS[@]}"
if [ "$pre_bad" != 0 ]; then
  echo "✘ 预检未过 $pre_bad 条 ⇒ 未注入任何破口，本书不起跑（不烧 gradle，也不留半本账）"
  exit 5
fi

# $1 = 注册序号
mv_step() {
  i="$1"
  id="${IDS[$i]}"; f="${FILES[$i]}"; old="${OLDS[$i]}"; new="${NEWS[$i]}"
  named="${NAMEDS[$i]}"; desc="${DESCS[$i]}"
  echo "== $id 破口：$desc =="
  b=$(md5sum < "$f")
  if ! python - "$f" "$old" "$new" <<'PY'
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
n = s.count(old)
assert n == 1, "ANCHOR-NOT-UNIQUE: count=%d anchor=%r" % (n, old[:70])
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
PY
  then
    echo "✘ $id 锚点没命中（或不止一处），破口根本没注入"; bad=$((bad + 1)); fail=1; return
  fi
  a=$(md5sum < "$f")
  if [ "$b" = "$a" ]; then
    echo "✘ $id 注入后文件字节未变（空跑），拒绝记成判据失效"; bad=$((bad + 1)); fail=1; return
  fi
  t0=$(date +%s)
  c=$(run_cls "$LOGDIR/$id.log"); echo "[$id 破口后] gradle exit=$c"
  if grep -q '^e: ' "$LOGDIR/$id.log"; then
    echo "✘ $id 注入后的代码不可编译（gradle 日志有 e: 行）——红是编译红，不是用例红"; bad=$((bad + 1)); fail=1
  else
    R=$(red_names "$t0"); echo "$R"
    if [ "$c" = 0 ]; then
      echo "✘ $id  源码已经改坏而用例全绿——$named 那条是装饰"; bad=$((bad + 1)); fail=1
    elif echo "$R" | grep -qF -- "$named"; then
      echo "✔ $id  → gradle exit=$c，红的正是 $named"; ok=$((ok + 1))
    else
      echo "✘ $id  gradle 是红的（exit=$c），但红的不是那一条——不能算这条破口被咬住"; bad=$((bad + 1)); fail=1
    fi
  fi
  P=$(probe_rc)
  if [ "$P" = 0 ]; then
    echo "△ $id  结构判据 rc=0：注入只动了行为、MR-l…MR-r 一个都没看见（这一格是结构半边的覆盖面，不是本书的红）"
    amb=$((amb + 1))
  else
    echo "◦ $id  同轮结构判据 rc=$P（预期非 0：注入改的就是 MR-* 钉住的文本；红着的判据不反过来证明它有判别力）"
    scope=$((scope + 1))
  fi
  restore
  cmp -s "$BAKV3" "$V3" && cmp -s "$BAKMM" "$MM" || {
    echo "✘ [$id 复原] 与备份不一致，必须手工核对，后面的判读全部作废"; exit 4; }
  echo "[$id 复原] cmp 一致"
}

echo "== 基线 =="
B=$(run_cls "$LOGDIR/baseline.log")
echo "  gradle exit=$B（必须 0，基线红着的 ✔ 一律不采信）"
[ "$B" = 0 ] || { echo "✘ 基线未通过，本轮负控不采信（看 $LOGDIR/baseline.log）"; exit 2; }
P0=$(probe_rc); echo "  同轮结构判据 rc=$P0（必须 0）"
[ "$P0" = 0 ] || { echo "✘ 结构基线不绿，本书不采信"; exit 2; }

for i in "${!IDS[@]}"; do mv_step "$i"; done

echo "== 原样恢复 =="
restore
cmp -s "$BAKV3" "$V3" || { echo "✘ [恢复] Map3DVisual 与备份不一致，不再往下判"; exit 4; }
cmp -s "$BAKMM" "$MM" || { echo "✘ [恢复] MissionMarkers 与备份不一致，不再往下判"; exit 4; }
echo "[恢复] 两个源文件 cmp 一致"
A=$(run_cls "$LOGDIR/recover.log")
echo "[恢复后] gradle exit=$A（必须 0）"
[ "$A" = 0 ] || { echo "✘ 恢复后用例仍红，本轮未收口（看 $LOGDIR/recover.log）"; exit 2; }
P=$(probe_rc)
echo "[恢复后] $PROBE rc=$P 末行:$(tail -1 "$LOGDIR/probe.log")"
[ "$P" = 0 ] || { echo "✘ 恢复后结构判据不通过（rc=$P），本轮负控不采信"; exit 6; }
echo "行为负控（⑫⑬ 3D 视觉层 ${#IDS[@]} 条破口 / ${#IDS[@]} 条用例一对一）= $ok ✔ / $bad ✘ / $amb △ / $scope ◦"
echo "  口径：✔ = 该破口被具名那一条用例咬住；✘ = 用例全绿或红的不是那一条；"
echo "        △ = 注入后同轮结构判据仍 rc=0（覆盖面注记，不计判据绿）；"
echo "        ◦ = 注入把同轮结构判据打红（按 §八 ㉛ ④，红着的判据不反过来证明它有判别力，也不计判据绿）。"
[ "$fail" = 0 ] || { echo "RESULT=FAIL"; exit 1; }
echo "RESULT=PASS"
