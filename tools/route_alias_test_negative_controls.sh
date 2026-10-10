#!/usr/bin/env bash
# F-3 ⑬/⑯ 轮**用例**的负控制（队列 #198 的行为那一半）：把解析层已撤的别名读法逐个放回去，
# 要求 `RouteGuideContractTest` / `UserTrackingContractTest` 里**具名那一条**必须红。
# 与 `default_value_negative_controls.sh`（结构那本，DV-t…GUARD3 共 9 条注入）的分工：
# 结构本只证明"键名又出现在源码里"能被 grep 数到，本书证明"别名不产生值"那 4 条断言不是装饰——
# 两本咬的是同一处破口：本书每条破口跑完，DV 结构判据也必须同时报红，否则说明两边判的不是同一件事。
#
# RA-1 steps 读法重新采纳 guidance/instructions → `只给 guidance 与 instructions 时一步都不造`
# RA-2 来源读法重新采纳 routeSource           → `routeSource 与 provider 两个别名不决定来源`
# RA-3 偏航读法重新采纳 offRoute              → `五个别名键同时出现也不产生里程时长偏航与步骤量`
# RA-4 步骤内 meters/lane/seconds 回流        → 同上那一条（三个字段各自的红，一次注入全见）
# RA-5 轨迹节点完成态重新采纳 finished        → `只给 finished 与 completed 别名的轨迹节点不算完成`
#
# ⚠ 本脚本会**就地临时修改源码**并在结束时原样恢复；一次只允许跑一个改源码的负控（negctl_lock）。
#   退出前必须看到 [恢复] cmp 一致 + 用例重新通过 + DV 结构判据重新通过，否则视为未收口。
# ⚠ 跑过 `--tests` 过滤之后 test-results 目录只剩这两本 ⇒ 读 test-results 的判据簿
#   （notify_result / oss_credits）会 rc=2，必须等全量 `--rerun-tasks` 之后再单本复跑。
# 用法：bash tools/route_alias_test_negative_controls.sh
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2

RP=app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt
BAK=/tmp/ral_bak.$$
JVMARGS='-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=GBK'
CLS='--tests com.shanxiang.buspost.data.repo.RouteGuideContractTest --tests com.shanxiang.buspost.data.UserTrackingContractTest'
PROBE=tools/check_default_value_probes.sh

cp "$RP" "$BAK"
restore() { cp "$BAK" "$RP"; }
trap 'restore; rm -f "$BAK"; negctl_release' EXIT

run_cases() { # $1 = 日志名
  ./gradlew $JVMARGS :app:testDebugUnitTest $CLS > "$1" 2>&1
  echo "$?"
}
# 红在哪几条只从 JUnit XML 读，不读 gradle 的措辞（GBK 控制台会把它打成乱码，且"失败"两字不代表红的用例）
red_names() {
  PYTHONIOENCODING=utf-8 python - <<'PY'
import glob, xml.etree.ElementTree as ET
for fn in sorted(glob.glob("app/build/test-results/testDebugUnitTest/TEST-*RouteGuideContractTest.xml")
                 + glob.glob("app/build/test-results/testDebugUnitTest/TEST-*UserTrackingContractTest.xml")):
    r = ET.parse(fn).getroot()
    bad = [(tc.get("name") or "")[:60] for tc in r.iter("testcase")
           if tc.find("failure") is not None or tc.find("error") is not None]
    print("   %s tests=%s failures=%s errors=%s 红在: %s" % (
        fn.split("TEST-")[-1][:-4], r.get("tests"), r.get("failures"), r.get("errors"), bad))
PY
}
probe_rc() { bash "$PROBE" > /tmp/ral_probe.log 2>&1; echo $?; }

# $1=编号 $2=python 破口表达式 $3=预期红在那一条用例名（子串）$4=说明
ctl() {
  local before after c R P
  echo "== $1 破口：$4 =="
  before=$(md5sum < "$RP")
  python - "$RP" "$2" <<'PY' || { echo "✘ $1 锚点没命中，破口根本没注入"; exit 2; }
import sys
p, code = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8', newline='').read()
exec(code)
open(p, 'w', encoding='utf-8', newline='').write(s)
PY
  after=$(md5sum < "$RP")
  [ "$before" != "$after" ] || { echo "✘ $1 注入后文件字节未变（空跑），拒绝记成判据失效"; exit 5; }
  c=$(run_cases "/tmp/ral_$1.log"); echo "[$1 破口后] gradle exit=$c"
  R=$(red_names); echo "$R"
  P=$(probe_rc); echo "◦ $1 同轮 DV 结构判据 rc=$P（预期非 0：两本咬的是同一处回流）"
  echo "$R" | grep -qF "$3" \
    && echo "✔ $1 别名回流 → gradle exit=$c，红的正是「$3」" \
    || echo "✘ $1 gradle exit=$c 却没红在「$3」——那条断言是装饰，本条不成立"
  [ "$P" != 0 ] || echo "✘ $1 DV 结构判据居然还是绿的 ⇒ 撤别名与行为用例判的不是同一处，本书不采信"
  restore
  cmp -s "$BAK" "$RP" || { echo "✘ [$1 复原] Repo.kt 与备份不一致"; exit 4; }
  echo "[$1 复原] cmp 一致"
}

echo "== 基线 =="
B=$(run_cases /tmp/ral_baseline.log)
echo "  gradle exit=$B（必须 0，基线红着的 ✔ 一律不采信）"
[ "$B" = 0 ] || { echo "✘ 基线未通过，本轮负控不采信（看 /tmp/ral_baseline.log）"; exit 2; }
red_names
P0=$(probe_rc); echo "  同轮 DV 结构判据 rc=$P0（必须 0）"
[ "$P0" = 0 ] || { echo "✘ 结构基线不绿，本书不采信"; exit 2; }

ctl RA-1 '
old = """        val raw = o.multiArr("steps")
"""
new = """        val raw = o.multiArr("steps", "guidance", "instructions")
"""
assert s.count(old) == 1, "ANCHOR-NOT-UNIQUE: %d" % s.count(old)
s = s.replace(old, new, 1)
' "只给 guidance 与 instructions 时一步都不造" "steps 读法重新采纳两个三面 0 生产者的步骤数组别名"

ctl RA-2 '
old = """        val providerRaw = o.multiStr("routeProvider", "dataSource")
"""
new = """        val providerRaw = o.multiStr("routeProvider", "dataSource", "routeSource")
"""
assert s.count(old) == 1, "ANCHOR-NOT-UNIQUE: %d" % s.count(old)
s = s.replace(old, new, 1)
' "routeSource 与 provider 两个别名不决定来源" "来源键重新采纳 routeSource（三方 0 命中那个）"

ctl RA-3 '
old = """            deviated = o.multiBool("deviated"),
"""
new = """            deviated = o.multiBool("deviated", "offRoute"),
"""
assert s.count(old) == 1, "ANCHOR-NOT-UNIQUE: %d" % s.count(old)
s = s.replace(old, new, 1)
' "五个别名键同时出现也不产生里程时长偏航与步骤量" "偏航位重新采纳 offRoute 别名"

ctl RA-4 '
old = """        distanceMeters = it.multiNum("distanceMeters").toInt(),
"""
new = """        distanceMeters = it.multiNum("distanceMeters", "meters").toInt(),
"""
assert s.count(old) == 1, "ANCHOR-NOT-UNIQUE: %d" % s.count(old)
s = s.replace(old, new, 1)
old2 = """        recommendedLane = it.multiNumOrNull("recommendedLane")?.toInt(),
        etaSeconds = it.multiNumOrNull("etaSeconds")?.toInt(),
"""
new2 = """        recommendedLane = it.multiNumOrNull("recommendedLane", "lane")?.toInt(),
        etaSeconds = it.multiNumOrNull("etaSeconds", "seconds")?.toInt(),
"""
assert s.count(old2) == 1, "ANCHOR2-NOT-UNIQUE: %d" % s.count(old2)
s = s.replace(old2, new2, 1)
' "五个别名键同时出现也不产生里程时长偏航与步骤量" "步骤内三个自造别名 meters/lane/seconds 一起回流（缺键会塌成第 1 车道 / 0 秒）"

ctl RA-5 '
old = """                state = stopStateOf(it.multiBool("done"), it.multiBool("current")),
"""
new = """                state = stopStateOf(it.multiBool("done", "finished"), it.multiBool("current", "completed")),
"""
assert s.count(old) == 1, "ANCHOR-NOT-UNIQUE: %d" % s.count(old)
s = s.replace(old, new, 1)
' "只给 finished 与 completed 别名的轨迹节点不算完成" "轨迹节点完成态重新采纳 finished/completed（后端只写 done）"

echo "== 原样恢复 =="
restore
cmp -s "$BAK" "$RP" || { echo "✘ [恢复] Repo.kt 与备份不一致，不再往下判"; exit 4; }
echo "[恢复] Repo.kt cmp 一致"
A=$(run_cases /tmp/ral_recover.log)
echo "[恢复后] gradle exit=$A（必须 0）"
[ "$A" = 0 ] || { echo "✘ 恢复后用例仍红，本轮未收口（看 /tmp/ral_recover.log）"; exit 2; }
red_names
P=$(probe_rc)
echo "[恢复后] $PROBE rc=$P 末行:$(tail -1 /tmp/ral_probe.log)"
[ "$P" = 0 ] || { echo "✘ 恢复后结构判据不通过（rc=$P），本轮负控不采信"; exit 6; }
echo "RESULT=PASS"
