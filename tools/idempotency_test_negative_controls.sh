#!/usr/bin/env bash
# 幂等**用例**的负控制：把闸门改成"永远放行"，要求 gradle 测试必须失败。
# 与 check_idempotency_probes.sh 的分工：探针证语义没跑调，这条证 11 条用例不是装饰。
# IT-2（本轮 #185）是同一本书里的第二条破口：把邮快件枚举码注回 PickupTask.isProduct，
#   要求 DriverStateTest 那条分流断言必须红 —— 结构判据（H-k）只看得见源码，
#   "用例真能红"这一半只有靠改坏源码再跑 gradle 才成立。
# IT-3（本轮 #147 收尾）第三条：把 targetIndex() 里的 `+ navOffset` 摘掉（「继续行驶」点了不动目标站），
#   要求诚实化那本新加的 `继续行驶只推进导航目标_后端当前站与网络都不动` 必须红。
#   ⚠ 这条破口下 H-m1..H-m4 四条结构判据**全部照绿**（写点还在、键还认 currentStationId），
#   能红的只有行为用例 —— 这正是 H-m 那一族要补行为半边（H-m5）的理由。
# ⚠ 本脚本会**就地临时修改源码**并在结束时原样恢复，一次只允许跑一个；
#   退出前必须看到 [恢复] cmp 一致 + 用例重新通过，否则视为未收口。
# 用法：bash tools/idempotency_test_negative_controls.sh
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2
GA=app/src/main/java/com/shanxiang/buspost/data/repo/ActionGate.kt
MD=app/src/main/java/com/shanxiang/buspost/data/model/Models.kt
WB=app/src/main/java/com/shanxiang/buspost/ui/driver/WorkbenchScreen.kt
BAK=/tmp/actiongate.bak.$$
BAKM=/tmp/models.bak.$$
BAKW=/tmp/workbench.bak.$$
JVMARGS='-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=GBK'

cp "$GA" "$BAK"
cp "$MD" "$BAKM"
cp "$WB" "$BAKW"
restore_gate() { cp "$BAK" "$GA"; }
restore_models() { cp "$BAKM" "$MD"; }
restore_wb() { cp "$BAKW" "$WB"; }
restore() { restore_gate; restore_models; restore_wb; }
trap 'restore; rm -f "$BAK" "$BAKM" "$BAKW"; negctl_release' EXIT

GT='--tests com.shanxiang.buspost.data.repo.ActionGateTest --tests com.shanxiang.buspost.data.repo.DriverActionIdempotencyContractTest'
echo "== 基线 =="   # 2026-10-01 补：本书原先**没有基线这一步**，基线红着也能一路打到"破口后仍红"，那条 ✔ 就失去意义了
./gradlew $JVMARGS :app:testDebugUnitTest $GT > /tmp/idem_test_baseline.log 2>&1
BASE=$?
echo "  gradle exit=$BASE（必须 0，基线不绿的负控一律不采信）"
[ "$BASE" = 0 ] || { echo "✘ 基线未通过，本轮负控不采信（看 /tmp/idem_test_baseline.log）"; exit 2; }

echo "== 破口：闸门永远返回 Sent（既不 tryLock 也不设窗口）=="
before=$(md5sum < "$GA")
python - "$GA" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
a = '        if ((heldUntil[key] ?: 0L) > now()) return GateResult.Duplicate'
b = '        if (!mutex.tryLock()) return GateResult.Duplicate'
assert a in s and b in s, "ANCHOR-MISS"
s = s.replace(a, '        // NEG-CONTROL: 窗口检查被摘掉', 1).replace(b, '        // NEG-CONTROL: 锁检查被摘掉', 1)
open(p, 'w', encoding='utf-8').write(s)
PY
[ "$?" = 0 ] || { echo "✘ IT-1 锚点没命中，破口根本没注入"; exit 2; }
after=$(md5sum < "$GA")
# 空跑与判据失效必须分开：注入未生效时跑的是未修改的源码，撞上 build cache 的绿色会被读成"用例证明不了任何东西"
[ "$before" != "$after" ] || { echo "✘ IT-1 注入后文件字节未变（空跑），拒绝记成判据失效"; exit 5; }

./gradlew $JVMARGS :app:testDebugUnitTest \
  --tests "com.shanxiang.buspost.data.repo.ActionGateTest" \
  --tests "com.shanxiang.buspost.data.repo.DriverActionIdempotencyContractTest" > /tmp/idem_test_neg.log 2>&1
code=$?
echo "[破口后] gradle exit=$code"
# ⚠ 判定行必须带 `✔` / `✘` 前缀：批处理 `run_all_negative_controls_batch.sh` 是按这两个标记数行的，
#   本书早先只打印 `[破口后] gradle exit=1`（其实红了）⇒ 全批记成 `✔=0 ✘=0` 的**假空跑**。
if [ "$code" = 0 ]; then
  echo "✘ IT-1  闸门永远返回 Sent（窗口检查与锁检查一起摘掉）→ 用例仍然全绿，这 11 条用例证明不了任何东西"
else
  echo "✔ IT-1  闸门永远返回 Sent（窗口检查与锁检查一起摘掉）→ gradle exit=$code，红了："
  # 用例名必须显式 UTF-8 打印：GBK 控制台会把中文用例名编成乱码，"红在哪几条"就成了没有证据（F-3 ③）
  PYTHONIOENCODING=utf-8 python - <<'PY'
import glob, xml.etree.ElementTree as ET
f = e = 0
for fn in glob.glob("app/build/test-results/testDebugUnitTest/TEST-*Idempotency*.xml") + \
          glob.glob("app/build/test-results/testDebugUnitTest/TEST-*ActionGate*.xml"):
    r = ET.parse(fn).getroot()
    f += int(r.get("failures")); e += int(r.get("errors"))
    for tc in r.iter("testcase"):
        bad = tc.find("failure") is not None or tc.find("error") is not None
        if bad: print("   红了：", (tc.get("name") or "")[:60])
print("   failures+errors =", f + e)
PY
fi

# ⚠ IT-1 的破口必须先复原再进 IT-2：本书第一轮就踩过这个次序 —— IT-2 的"基线"是在
#   ActionGate 还被摘掉（`mutex.unlock()` 没人持锁 → IllegalStateException）的树上跑的，
#   于是 `DriverStateTest > 发车后状态由回读的班次决定` FAILED、脚本按纪律 exit 2。
#   基线红着不采信是对的，但那一次红的是上一条破口的残留，不是 IT-2 的证据。
echo "== IT-1 破口复原（IT-2 的基线必须落在干净的树上）=="
restore_gate
if cmp -s "$BAK" "$GA"; then echo "[IT-1 复原] ActionGate cmp 一致"; else echo "✘ [IT-1 复原] ActionGate 与备份不一致，IT-2 不采信"; exit 4; fi

echo "== IT-2 基线：装载分流那条断言所在的用例必须先绿 =="
./gradlew $JVMARGS :app:testDebugUnitTest --tests "com.shanxiang.buspost.driver.DriverStateTest" > /tmp/idem_test_it2_base.log 2>&1
B2=$?
echo "  gradle exit=$B2（必须 0，基线红着的 ✔ 一样失去意义）"
[ "$B2" = 0 ] || { echo "✘ IT-2 基线未通过，本轮负控不采信（看 /tmp/idem_test_it2_base.log）"; exit 2; }

echo "== IT-2 破口：把邮快件枚举码 3 注回 isProduct（没面单号的邮快件会被分流进商城域）=="
mb=$(md5sum < "$MD")
python - "$MD" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
old = '    val isProduct: Boolean get() = bizType.equals("PRODUCT", true)'
new = '    val isProduct: Boolean get() = bizType.equals("PRODUCT", true) || orderType == 3 && mailNo.isEmpty()'
assert old in s, "ANCHOR-MISS: isProduct"
open(p, 'w', encoding='utf-8', newline='\n').write(s.replace(old, new, 1))
PY
[ "$?" = 0 ] || { echo "✘ IT-2 锚点没命中，破口根本没注入"; exit 2; }
ma=$(md5sum < "$MD")
[ "$mb" != "$ma" ] || { echo "✘ IT-2 注入后文件字节未变（空跑），拒绝记成判据失效"; exit 5; }

./gradlew $JVMARGS :app:testDebugUnitTest \
  --tests "com.shanxiang.buspost.driver.DriverStateTest" \
  --tests "com.shanxiang.buspost.ui.driver.DriverActionHonestyContractTest" > /tmp/idem_test_it2_neg.log 2>&1
c2=$?
echo "[IT-2 破口后] gradle exit=$c2"
RED=$(PYTHONIOENCODING=utf-8 python - <<'PY'
import glob, xml.etree.ElementTree as ET
for fn in sorted(glob.glob("app/build/test-results/testDebugUnitTest/TEST-*.xml")):
    if "DriverStateTest" not in fn and "DriverActionHonesty" not in fn: continue
    r = ET.parse(fn).getroot()
    bad = [(tc.get("name") or "")[:50] for tc in r.iter("testcase")
           if tc.find("failure") is not None or tc.find("error") is not None]
    print("   %s failures=%s errors=%s 红在: %s" % (fn.split("TEST-")[-1][:-4], r.get("failures"), r.get("errors"), bad))
PY
)
echo "$RED"
if [ "$c2" = 0 ]; then
  echo "✘ IT-2  枚举码回流后用例仍全绿——那条断言是装饰"
elif echo "$RED" | grep -q '揽件单类型决定作业要求'; then
  echo "✔ IT-2  枚举码回流 → gradle exit=$c2，且红的正是 揽件单类型决定作业要求（isProduct 分流断言）"
else
  echo "✘ IT-2  gradle 是红的（exit=$c2），但红的不是那一条断言——不能算这条破口被咬住"
fi
# 如实登记覆盖范围，不假装两本都红：诚实化那本的五条用例不读 isProduct（走的是 arrive/verify/导航推进三支），
# 覆盖"邮快件不许进商城域"这一支的是 DriverStateTest:134。
echo "◦ IT-2  覆盖面：DriverActionHonestyContractTest 在这条破口下不红（那五条用例不读 isProduct）"

# ⚠ 同一条次序教训第三次生效：IT-2 的枚举码破口必须先复原，IT-3 的基线才落在干净的树上。
echo "== IT-2 破口复原（IT-3 的基线必须落在干净的树上）=="
restore_models
if cmp -s "$BAKM" "$MD"; then echo "[IT-2 复原] Models cmp 一致"; else echo "✘ [IT-2 复原] Models 与备份不一致，IT-3 不采信"; exit 4; fi

echo "== IT-3 基线：诚实化那本（含新加的导航推进用例）必须先绿 =="
./gradlew $JVMARGS :app:testDebugUnitTest --tests "com.shanxiang.buspost.ui.driver.DriverActionHonestyContractTest" > /tmp/idem_test_it3_base.log 2>&1
B3=$?
echo "  gradle exit=$B3（必须 0）"
[ "$B3" = 0 ] || { echo "✘ IT-3 基线未通过，本轮负控不采信（看 /tmp/idem_test_it3_base.log）"; exit 2; }

echo "== IT-3 破口：把 targetIndex() 里的 + navOffset 摘掉（点「继续行驶」导航目标不再往后走）=="
wb=$(md5sum < "$WB")
python - "$WB" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8', newline='').read()
old = '        return ((if (cur >= 0) cur + 1 else 0) + navOffset).coerceIn(0, (s.stations.size - 1).coerceAtLeast(0))\n'
new = '        return ((if (cur >= 0) cur + 1 else 0)).coerceIn(0, (s.stations.size - 1).coerceAtLeast(0))\n'
assert old in s, "ANCHOR-MISS: targetIndex 的 navOffset 那一行"
assert s.count(old) == 1, "ANCHOR-NOT-UNIQUE: %d" % s.count(old)
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
PY
[ "$?" = 0 ] || { echo "✘ IT-3 锚点没命中，破口根本没注入"; exit 2; }
wa=$(md5sum < "$WB")
[ "$wb" != "$wa" ] || { echo "✘ IT-3 注入后文件字节未变（空跑），拒绝记成判据失效"; exit 5; }

./gradlew $JVMARGS :app:testDebugUnitTest \
  --tests "com.shanxiang.buspost.ui.driver.DriverActionHonestyContractTest" > /tmp/idem_test_it3_neg.log 2>&1
c3=$?
echo "[IT-3 破口后] gradle exit=$c3"
RED3=$(PYTHONIOENCODING=utf-8 python - <<'PY'
import glob, xml.etree.ElementTree as ET
for fn in sorted(glob.glob("app/build/test-results/testDebugUnitTest/TEST-*.xml")):
    if "DriverActionHonesty" not in fn: continue
    r = ET.parse(fn).getroot()
    bad = [(tc.get("name") or "")[:60] for tc in r.iter("testcase")
           if tc.find("failure") is not None or tc.find("error") is not None]
    print("   %s tests=%s failures=%s errors=%s 红在: %s" % (fn.split("TEST-")[-1][:-4], r.get("tests"), r.get("failures"), r.get("errors"), bad))
PY
)
echo "$RED3"
if [ "$c3" = 0 ]; then
  echo "✘ IT-3  摘掉 + navOffset 后用例仍全绿——那条导航推进断言是装饰"
elif echo "$RED3" | grep -q '继续行驶只推进导航目标'; then
  echo "✔ IT-3  摘掉 + navOffset → gradle exit=$c3，且红的正是 继续行驶只推进导航目标（导航目标那一层）"
else
  echo "✘ IT-3  gradle 是红的（exit=$c3），但红的不是那一条断言——不能算这条破口被咬住"
fi
# 同一轮把结构判据的读数也打出来：这条破口下 H-m1..H-m4 应当**全绿**（写点与键都还在），
# 这就是"结构判据看不见行为"的实证，不是给判据簿抹黑。
bash tools/check_idempotency_probes.sh > /tmp/idem_test_it3_probe.log 2>&1
P3=$?
echo "◦ IT-3  同轮结构判据读数：check_idempotency_probes rc=$P3（预期 0 —— 破口在语义上，不在写法上）"
grep '^×' /tmp/idem_test_it3_probe.log | head -3

echo "== IT-3 破口复原 =="
restore_wb
if cmp -s "$BAKW" "$WB"; then echo "[IT-3 复原] WorkbenchScreen cmp 一致"; else echo "✘ [IT-3 复原] WorkbenchScreen 与备份不一致，必须手工核对"; exit 4; fi

echo "== 原样恢复 =="
restore
if cmp -s "$BAK" "$GA"; then echo "[恢复] ActionGate cmp 一致"; else echo "✘ [恢复] ActionGate 与备份不一致，必须手工核对（不再往下判，否则后面的绿都是错的树）"; exit 4; fi
if cmp -s "$BAKM" "$MD"; then echo "[恢复] Models cmp 一致"; else echo "✘ [恢复] Models 与备份不一致，必须手工核对（同上）"; exit 4; fi
if cmp -s "$BAKW" "$WB"; then echo "[恢复] WorkbenchScreen cmp 一致"; else echo "✘ [恢复] WorkbenchScreen 与备份不一致，必须手工核对（同上）"; exit 4; fi
./gradlew $JVMARGS :app:testDebugUnitTest \
  --tests "com.shanxiang.buspost.data.repo.ActionGateTest" \
  --tests "com.shanxiang.buspost.data.repo.DriverActionIdempotencyContractTest" \
  --tests "com.shanxiang.buspost.driver.DriverStateTest" \
  --tests "com.shanxiang.buspost.ui.driver.DriverActionHonestyContractTest" > /tmp/idem_test_recover.log 2>&1
AFTER=$?
echo "[恢复后] gradle exit=$AFTER  （必须为 0）"
[ "$AFTER" = 0 ] || { echo "✘ 恢复后用例仍红，本轮未收口（看 /tmp/idem_test_recover.log）"; exit 2; }
bash tools/check_idempotency_probes.sh > /tmp/idem_test_probe_tail.log 2>&1
PROBE_RC=$?
tail -1 /tmp/idem_test_probe_tail.log
# ⚠ 原写法 `bash … | tail -1` 让本书的退出码恒为 tail 的 0（判据 §八 ㉓ ⑤ 那条老坑的第三种形态）
[ "$PROBE_RC" = 0 ] || { echo "✘ 恢复后判据簿不通过（rc=$PROBE_RC），本轮负控不采信"; exit 6; }
