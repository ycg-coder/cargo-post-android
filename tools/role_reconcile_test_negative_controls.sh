#!/usr/bin/env bash
# V-2b 角色同源**用例**的负控制：把判据逐个破口 → gradle 用例必须失败。
# 与 check_role_reconcile_probes.sh / role_reconcile_negative_controls.sh 的分工：那两套证**结构**没跑调，
# 这一条证 RoleReconcileTest 的 5 例与 WorkbenchRoleReconcileContractTest 的 4 例（外加被本轮纠正方向的
# DomainSplitTest 两条、F-3 ③ 新增的 DriverRoleIntegrityTest 六例）不是装饰。
# ⚠ 本脚本会**就地临时修改源码**并在结束时原样恢复；一次只允许跑一个改源码的负控脚本，
#   跑完必须看到基线绿 + 恢复后重新全绿，否则视为未收口。
# 用法：bash tools/role_reconcile_test_negative_controls.sh
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2
REPO=app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt
SD=app/src/main/java/com/shanxiang/buspost/nav/ShellDecision.kt
WB=app/src/main/java/com/shanxiang/buspost/ui/driver/WorkbenchScreen.kt
AD=app/src/main/java/com/shanxiang/buspost/core/AppDomain.kt
AS=app/src/main/java/com/shanxiang/buspost/core/AppState.kt
JVMARGS='-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=GBK'
CLS='--tests com.shanxiang.buspost.nav.RoleReconcileTest --tests com.shanxiang.buspost.ui.driver.WorkbenchRoleReconcileContractTest --tests com.shanxiang.buspost.data.repo.DomainSplitTest --tests com.shanxiang.buspost.core.DriverRoleIntegrityTest'
BAK=/tmp/rr_neg_bak.$$
mkdir -p "$BAK"; cp "$REPO" "$SD" "$WB" "$AD" "$AS" "$BAK/"
trap 'cp "$BAK/Repo.kt" "$REPO"; cp "$BAK/ShellDecision.kt" "$SD"; cp "$BAK/WorkbenchScreen.kt" "$WB"; cp "$BAK/AppDomain.kt" "$AD"; cp "$BAK/AppState.kt" "$AS"; rm -rf "$BAK"; negctl_release' EXIT
restore() { cp "$BAK/Repo.kt" "$REPO"; cp "$BAK/ShellDecision.kt" "$SD"; cp "$BAK/WorkbenchScreen.kt" "$WB"; cp "$BAK/AppDomain.kt" "$AD"; cp "$BAK/AppState.kt" "$AS"; }

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

case_run() { # $1=编号 $2=破口说明
  local code
  if [ ! -s "$REPO" ] || [ ! -s "$SD" ] || [ ! -s "$WB" ] || [ ! -s "$AD" ] || [ ! -s "$AS" ]; then echo "✘ $1 源码为空，本轮不采信"; return; fi
  ./gradlew $JVMARGS :app:testDebugUnitTest $CLS > "/tmp/rr_neg_$1.log" 2>&1
  code=$?
  if [ "$code" = 0 ]; then
    echo "✘ $1  $2 → 用例仍然全绿，这条判据证明不了任何东西"
  else
    printf '✔ %s  %s → gradle exit=%s，红了：' "$1" "$2" "$code"
    # ⚠ 必须显式 UTF-8：本机控制台是 GBK，python 把 XML 里读出的中文用例名按 GBK 编一遍就成乱码，
    #   负控日志里"红在哪几条用例"那句正是收口要抄进台账的证据，乱码等于没证据（2026-10-01 / F-3 ③）。
    PYTHONIOENCODING=utf-8 python - <<'PY'
import glob, xml.etree.ElementTree as ET
n = []; tot = 0
for fn in glob.glob("app/build/test-results/testDebugUnitTest/TEST-*.xml"):
    if "RoleReconcile" not in fn and "DomainSplitTest" not in fn and "DriverRoleIntegrityTest" not in fn: continue
    r = ET.parse(fn).getroot()
    tot += int(r.get("failures")) + int(r.get("errors"))
    for tc in r.iter("testcase"):
        if tc.find("failure") is not None or tc.find("error") is not None:
            n.append(tc.get("name"))
print(" ".join(x[:26] for x in n), "｜ failures+errors =", tot)
PY
  fi
  restore
}

echo "[基线]"; ./gradlew $JVMARGS :app:testDebugUnitTest $CLS > /tmp/rr_neg_baseline.log 2>&1
BASE=$?
echo "  gradle exit=$BASE（必须 0，基线不绿的负控一律不采信）"
[ "$BASE" = 0 ] || { echo "✘ 基线未通过，负控结果不采信"; exit 2; }

# RRt-1 地基回退：把 `data 非空` 加回否定判据 —— 后端唯一的否定形状又被排除（撤档不生效）
red "$REPO" "
n = 'else if (!p.demo) Prefs.clearDriverBinding()'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, 'else if (!p.demo && o.length() > 0) Prefs.clearDriverBinding()', 1)
"
case_run RRt-1 "否定判据回退成 data 非空"

# RRt-2 早退手软：清完状态不 return，四条请求照发
red "$WB" "
n = '            error = DRIVER_UNBOUND\n            return'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '            error = DRIVER_UNBOUND', 1)
"
case_run RRt-2 "否定分支不再 return"

# RRt-3 三态退回两态：把'读不到'当成'不是司机'（一次抖动就把司机踢出司机端）
red "$SD" "
n = '    failed || demo -> null'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '    failed || demo -> false', 1)
"
case_run RRt-3 "失败/演示被当成明确否定"

# RRt-4 降级变成双向：档案说有就自动把人搬进司机域（硬规则 1）
red "$SD" "
n = 'internal fun reconcileRole(stored: Role, server: Boolean?): Role =\n    if (stored == Role.DRIVER && server == false) Role.USER else stored'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, 'internal fun reconcileRole(stored: Role, server: Boolean?): Role =\n    if (server == true) Role.DRIVER else if (stored == Role.DRIVER && server == false) Role.USER else stored', 1)
"
case_run RRt-4 "reconcileRole 开始升级角色"

# RRt-5 判了不落地：算出该降级却不写角色
red "$WB" "
n = '        if (next != stored) AppState.setAccountRole(next)'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '        // NEG-CONTROL 不落地', 1)
"
case_run RRt-5 "降级结论不写回角色"

# RRt-6 未知当否定：连请求失败都清绑定（DomainSplitTest 的那条反例守卫就是钉这个）
red "$REPO" "
n = '    } catch (e: Exception) {\n        One(null, error = errorTextOf(e))\n    }\n\n    // 注意：shifts / pickups'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '    } catch (e: Exception) {\n        Prefs.clearDriverBinding()\n        One(null, error = errorTextOf(e))\n    }\n\n    // 注意：shifts / pickups', 1)
"
case_run RRt-6 "请求失败也清绑定"

# ─── F-3 ③ 两处分两层：纯函数规则（RRt-7/8）与 AppState 接线（RRt-9/10）各钉一次 ───
# RRt-7 视图闸门放开：非司机账号也能切进司机域（硬规则 1 的界面版失守）
red "$AD" "
n = '    if (requested == AppDomain.DRIVER && accountRole != Role.DRIVER) AppDomain.USER else requested'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '    requested', 1)
"
case_run RRt-7 "domainFor 不再拦非司机切司机视图"

# RRt-8 撤档后视图留在司机域：账号已无档案，界面还接着按司机链路跑
red "$AD" "
n = '    if (newRole == Role.DRIVER) current else AppDomain.USER'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '    current', 1)
"
case_run RRt-8 "domainAfterRoleChange 撤档不再带视图离开司机域"

# RRt-9 规则在、接线绕开它：setActiveDomain 直接写值，闸门形同虚设
red "$AS" "
n = '        _activeDomain.value = domainFor(value, Prefs.role)'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '        _activeDomain.value = value', 1)
"
case_run RRt-9 "setActiveDomain 绕过 domainFor 闸门"

# RRt-10 改角色不联动视图：降级后角色回到用户，界面还停在司机壳（串域回来了）
red "$AS" "
n = '        _activeDomain.value = domainAfterRoleChange(value, _activeDomain.value)'
assert n in s, 'ANCHOR-MISS'
s = s.replace(n, '        // NEG-CONTROL 视图不跟着角色离开司机域', 1)
"
case_run RRt-10 "setAccountRole 不再将视图带出司机域"

echo "[恢复] 重新跑基线"
./gradlew $JVMARGS :app:testDebugUnitTest $CLS --rerun-tasks > /tmp/rr_neg_restore.log 2>&1
R=$?
echo "  gradle exit=$R（必须 0）"
cmp -s "$BAK/Repo.kt" "$REPO" && cmp -s "$BAK/ShellDecision.kt" "$SD" && cmp -s "$BAK/WorkbenchScreen.kt" "$WB" \
  && cmp -s "$BAK/AppDomain.kt" "$AD" && cmp -s "$BAK/AppState.kt" "$AS" \
  && echo "  cmp 五个源文件与备份一致" || echo "  ✘ 源码与备份不一致，必须人工核对"
[ "$R" = 0 ] || { echo "✘ 恢复后基线不绿，本轮负控不采信"; exit 2; }
