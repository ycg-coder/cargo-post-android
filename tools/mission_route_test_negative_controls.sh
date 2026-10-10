#!/usr/bin/env bash
# 任务地图无路线态**用例**的负控制（F-3 ⑧ / 队列 #148）：改坏源码，要求 `MissionRouteStateContractTest`
# 里**具名那一条**必须红。与 `mission_route_negative_controls.sh`（结构那本，27 条注入）的分工：
# 结构本判"写法有没有被人换掉"，本书判"那 12 条用例是不是装饰"——只有gradle真跑才成立。
#
# MT-1 摘掉失败原因的落地（routeFailure 永远为 null）→ `路线请求失败时问过这一事实与失败原因都留下`
# MT-2 把"没问过"那一格换成写死的"后端没有下发…"        → `任何一格都不许说后端没有下发`
# MT-3 早退支不再清相位与失败（把两行删掉）              → `上一轮留下的问过与失败在早退时一起清零`
#   ⚠ 本书第一版把 MT-3 的具名用例写成了 `撤档早退时路线状态退回没问过`，实测要求**错了**（打印 ✘）：
#   新 VM 的相位初值本就是 NotAsked，删掉那两行它照样读到 NotAsked ⇒ 咬得住的只有跨轮残留那一条。已就地改判。
#
# ⚠ 本书**故意不设 MT-4**（把折线闸门 `routeUnavailableReason != null` 那一半摘掉）：
#   实测推演过——失败轮与"问到任务点但没回折线"轮里 `guide.polyline` 本来就是空的，
#   `paths()` 仍返回空 ⇒ 行为用例一条都不会红。这一支只有结构判据 MR-j 钉得住，
#   所以按 §八 ㉚ ① 的口径如实记 ◦ 覆盖面，不造一条注定绿的"负控"。
#
# ⚠ 本脚本会**就地临时修改源码**并在结束时原样恢复；一次只允许跑一个（negctl_lock）。
#   退出前必须看到 [恢复] cmp 一致 + 用例重新通过 + 结构判据重新通过，否则视为未收口。
# ⚠ 跑过 `--tests` 过滤之后 test-results 目录只剩这几本 ⇒ 读 test-results 的判据簿
#   （notify_result / oss_credits）会 rc=2，必须等全量 `--rerun-tasks` 之后再单本复跑。
# 用法：bash tools/mission_route_test_negative_controls.sh
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2

WB=app/src/main/java/com/shanxiang/buspost/ui/driver/WorkbenchScreen.kt
MM=app/src/main/java/com/shanxiang/buspost/ui/driver/MissionMarkers.kt
BAKW=/tmp/mr_wb.bak.$$
BAKM=/tmp/mr_mm.bak.$$
CLS=com.shanxiang.buspost.ui.driver.MissionRouteStateContractTest
JVMARGS='-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=GBK'
PROBE=tools/check_mission_route_probes.sh

cp "$WB" "$BAKW"
cp "$MM" "$BAKM"
restore_wb() { cp "$BAKW" "$WB"; }
restore_mm() { cp "$BAKM" "$MM"; }
restore() { restore_wb; restore_mm; }
trap 'restore; rm -f "$BAKW" "$BAKM"; negctl_release' EXIT

# 通用：跑这本书那一类，把"红在哪几条"用 UTF-8 打出来（GBK 控制台会把中文用例名编成乱码）
run_cls() { # $1 = 日志名
  ./gradlew $JVMARGS :app:testDebugUnitTest --tests "$CLS" > "$1" 2>&1
  echo "$?"
}
red_names() {
  PYTHONIOENCODING=utf-8 python - <<'PY'
import glob, xml.etree.ElementTree as ET
for fn in sorted(glob.glob("app/build/test-results/testDebugUnitTest/TEST-*MissionRouteState*.xml")):
    r = ET.parse(fn).getroot()
    bad = [(tc.get("name") or "")[:60] for tc in r.iter("testcase")
           if tc.find("failure") is not None or tc.find("error") is not None]
    print("   %s tests=%s failures=%s errors=%s 红在: %s" % (
        fn.split("TEST-")[-1][:-4], r.get("tests"), r.get("failures"), r.get("errors"), bad))
PY
}
probe_rc() { bash "$PROBE" > /tmp/mr_test_probe.log 2>&1; echo $?; }

echo "== 基线 =="
B=$(run_cls /tmp/mr_test_baseline.log)
echo "  gradle exit=$B（必须 0，基线红着的 ✔ 一律不采信）"
[ "$B" = 0 ] || { echo "✘ 基线未通过，本轮负控不采信（看 /tmp/mr_test_baseline.log）"; exit 2; }
P0=$(probe_rc); echo "  同轮结构判据 rc=$P0（必须 0）"; [ "$P0" = 0 ] || { echo "✘ 结构基线不绿，本书不采信"; exit 2; }

# ---------- MT-1 ----------
echo "== MT-1 破口：routeFailure 永远为 null（失败原因落不了地）=="
wb=$(md5sum < "$WB")
python - "$WB" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8', newline='').read()
old = '        routeFailure = g.error\n'
assert s.count(old) == 1, "ANCHOR-NOT-UNIQUE: %d" % s.count(old)
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, '        routeFailure = null\n', 1))
PY
[ "$?" = 0 ] || { echo "✘ MT-1 锚点没命中，破口根本没注入"; exit 2; }
wa=$(md5sum < "$WB")
[ "$wb" != "$wa" ] || { echo "✘ MT-1 注入后文件字节未变（空跑），拒绝记成判据失效"; exit 5; }
c=$(run_cls /tmp/mr_test_mt1.log); echo "[MT-1 破口后] gradle exit=$c"
R=$(red_names); echo "$R"
P=$(probe_rc); echo "◦ MT-1  同轮结构判据 rc=$P（预期非 0：MR-f 钉的就是 g.error 那一行的落地）"
if [ "$c" = 0 ]; then
  echo "✘ MT-1  失败原因不再落地而用例全绿——那条断言是装饰"
elif echo "$R" | grep -q '路线请求失败时问过这一事实与失败原因都留下'; then
  echo "✔ MT-1  摘掉失败原因 → gradle exit=$c，红的正是 路线请求失败时问过这一事实与失败原因都留下"
else
  echo "✘ MT-1  gradle 是红的（exit=$c），但红的不是那一条——不能算这条破口被咬住"
fi
echo "== MT-1 破口复原（MT-2 的基线必须落在干净的树上）=="
restore_wb; cmp -s "$BAKW" "$WB" || { echo "✘ [MT-1 复原] WorkbenchScreen 与备份不一致"; exit 4; }
echo "[MT-1 复原] cmp 一致"

# ---------- MT-2 ----------
echo "== MT-2 破口：把'没问过'那一格换成写死的'后端没有下发…'=="
mb=$(md5sum < "$MM")
python - "$MM" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8', newline='').read()
old = '            "这一班的路线还没去问过（不是后端说没有路线），这里不画任何示意线。"\n'
assert s.count(old) == 1, "ANCHOR-NOT-UNIQUE: %d" % s.count(old)
open(p, 'w', encoding='utf-8', newline='').write(
    s.replace(old, '            "后端没有下发这一班的真实道路折线。"\n', 1))
PY
[ "$?" = 0 ] || { echo "✘ MT-2 锚点没命中，破口根本没注入"; exit 2; }
ma=$(md5sum < "$MM")
[ "$mb" != "$ma" ] || { echo "✘ MT-2 注入后文件字节未变（空跑），拒绝记成判据失效"; exit 5; }
c=$(run_cls /tmp/mr_test_mt2.log); echo "[MT-2 破口后] gradle exit=$c"
R=$(red_names); echo "$R"
P=$(probe_rc); echo "◦ MT-2  同轮结构判据 rc=$P（预期非 0：MR-c 钉的是那一格的整行字面量）"
if [ "$c" = 0 ]; then
  echo "✘ MT-2  没问到又被念成后端说没有，而用例全绿——那两格断言是装饰"
elif echo "$R" | grep -q '任何一格都不许说后端没有下发'; then
  echo "✔ MT-2  字面量回流 → gradle exit=$c，红的正是 任何一格都不许说后端没有下发"
else
  echo "✘ MT-2  gradle 是红的（exit=$c），但红的不是那一条——不能算这条破口被咬住"
fi
echo "== MT-2 破口复原 =="
restore_mm; cmp -s "$BAKM" "$MM" || { echo "✘ [MT-2 复原] MissionMarkers 与备份不一致"; exit 4; }
echo "[MT-2 复原] cmp 一致"

# ---------- MT-3 ----------
echo "== MT-3 破口：早退支不再清相位与失败（上一轮的'问过'跟着下一轮一起念）=="
wb=$(md5sum < "$WB")
python - "$WB" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8', newline='').read()
old = ('            routePhase = PolylinePhase.NotAsked\n'
       '            routeFailure = null\n')
assert s.count(old) == 1, "ANCHOR-NOT-UNIQUE: %d" % s.count(old)
open(p, 'w', encoding='utf-8', newline='').write(
    s.replace(old, '            // NEG-CONTROL: 早退支不再清相位与失败\n', 1))
PY
[ "$?" = 0 ] || { echo "✘ MT-3 锚点没命中，破口根本没注入"; exit 2; }
wa=$(md5sum < "$WB")
[ "$wb" != "$wa" ] || { echo "✘ MT-3 注入后文件字节未变（空跑），拒绝记成判据失效"; exit 5; }
c=$(run_cls /tmp/mr_test_mt3.log); echo "[MT-3 破口后] gradle exit=$c"
R=$(red_names); echo "$R"
P=$(probe_rc); echo "◦ MT-3  同轮结构判据 rc=$P（预期非 0：MR-f/MR-g 钉的是那两行的存在与份数）"
# MT-3（本轮 #191 就地改判）：本书第一版在这里要求红的是 `撤档早退时路线状态退回没问过`，实测**要求错了**
#   （那条 ✔ 没成立、打印的是 ✘）——新 VM 的 `routePhase` 初值本来就是 NotAsked，把早退支那两行删掉
#   它照样读到 NotAsked。真正咬得住这条破口的是**跨轮残留**那一条：第一轮留下 Asked/失败，第二轮撤档才见得出清没清。
#   ⇒ 期望改成具名 `上一轮留下的问过与失败在早退时一起清零`，并把"早退那条不红"如实记成 ◦ 覆盖面。
if [ "$c" = 0 ]; then
  echo "✘ MT-3  早退不清状态而用例全绿——跨轮残留那条断言是装饰"
elif echo "$R" | grep -q '上一轮留下的问过与失败在早退时一起清零'; then
  echo "✔ MT-3  早退不清零 → gradle exit=$c，红的正是 上一轮留下的问过与失败在早退时一起清零"
  echo "$R" | grep -q '撤档早退时路线状态退回没问过' || \
    echo "◦ MT-3  覆盖面：撤档早退时路线状态退回没问过 在这条破口下不红（新 VM 初值已是 NotAsked，清不清都读得到）——它钉的是'早退不许把相位置成问过'，不是'早退要清零'"
else
  echo "✘ MT-3  gradle 是红的（exit=$c），但红的不是那一条——不能算这条破口被咬住"
fi
echo "== MT-3 破口复原 =="
restore_wb; cmp -s "$BAKW" "$WB" || { echo "✘ [MT-3 复原] WorkbenchScreen 与备份不一致，必须手工核对"; exit 4; }
echo "[MT-3 复原] cmp 一致"

echo "== 原样恢复 =="
restore
cmp -s "$BAKW" "$WB" || { echo "✘ [恢复] WorkbenchScreen 与备份不一致，不再往下判"; exit 4; }
cmp -s "$BAKM" "$MM" || { echo "✘ [恢复] MissionMarkers 与备份不一致，不再往下判"; exit 4; }
echo "[恢复] 两个源文件 cmp 一致"
A=$(run_cls /tmp/mr_test_recover.log)
echo "[恢复后] gradle exit=$A（必须 0）"
[ "$A" = 0 ] || { echo "✘ 恢复后用例仍红，本轮未收口（看 /tmp/mr_test_recover.log）"; exit 2; }
P=$(probe_rc)
echo "[恢复后] $PROBE rc=$P 末行:$(tail -1 /tmp/mr_test_probe.log)"
[ "$P" = 0 ] || { echo "✘ 恢复后结构判据不通过（rc=$P），本轮负控不采信"; exit 6; }
echo "RESULT=PASS"
