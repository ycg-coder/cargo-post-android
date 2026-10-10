#!/usr/bin/env bash
# 壳路由**用例**的负控制：把 ShellDecision.kt 的两个决策逐个破口 → gradle 用例必须失败。
# 与 check_shell_routing_probes.sh 的分工：探针证结构没跑调，这条证那 10 条用例不是装饰。
# ⚠ 本脚本会**就地临时修改源码**并在结束时原样恢复；一次只允许跑一个改源码的负控脚本，
#   跑完必须看到 [恢复] cmp 一致 + 用例重新全绿，否则视为未收口。
# 用法：bash tools/shell_routing_test_negative_controls.sh
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2
SD=app/src/main/java/com/shanxiang/buspost/nav/ShellDecision.kt
RS=app/src/main/java/com/shanxiang/buspost/nav/Routes.kt
BAK=/tmp/shelldecision.bak.$$
BAK2=/tmp/shellroutes.bak.$$
JVMARGS='-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=GBK'
CLS=com.shanxiang.buspost.nav.ShellRoutingContractTest

cp "$SD" "$BAK"
cp "$RS" "$BAK2"
restore() { cp "$BAK" "$SD"; cp "$BAK2" "$RS"; }
trap 'restore; rm -f "$BAK" "$BAK2"; negctl_release' EXIT

red() { # $1=文件 $2=python 表达式（锚点不在必须报错；注入后字节没变也算空跑，都不许往下跑）
  local before after
  before=$(md5sum < "$1")
  python - "$1" "$2" <<'PY'
import sys
p, expr = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8').read()
exec(expr)                     # 每个 expr 自己 assert 锚点在，锚点漂了必须报错而不是空跑
open(p, 'w', encoding='utf-8').write(s)
PY
  [ "$?" = 0 ] || { echo "✘ 锚点没命中，破口根本没注入（$2）"; exit 2; }
  after=$(md5sum < "$1")
  # 2026-10-01 实测的坑：这句话原本只有 assert 一半的保护，注入未生效时仍然继续跑 gradle，
  # 于是**未修改**的源码撞上 build cache 里那份绿色结果，被打印成「✘ 用例仍然全绿，这条判据证明不了任何东西」。
  # 空跑是本书的缺陷（exit 5），判据不判才是用例的缺陷，两者必须分开。
  [ "$before" != "$after" ] || { echo "✘ 注入后文件字节未变（空跑），拒绝记成判据失效：$2"; exit 5; }
}

case_run() { # $1=编号 $2=破口说明
  local code
  if [ ! -s "$SD" ]; then echo "✘ $1 源码为空，本轮不采信"; return; fi
  ./gradlew $JVMARGS :app:testDebugUnitTest --tests "$CLS" > "/tmp/shell_neg_$1.log" 2>&1
  code=$?
  if [ "$code" = 0 ]; then
    echo "✘ $1  $2 → 用例仍然全绿，这条判据证明不了任何东西"
  else
    printf '✔ %s  %s → gradle exit=%s，红了：' "$1" "$2" "$code"
    # 用例名必须显式 UTF-8 打印：GBK 控制台会把中文用例名编成乱码，"红在哪几条"就成了没有证据（F-3 ③）
    PYTHONIOENCODING=utf-8 python - <<'PY'
import glob, xml.etree.ElementTree as ET
n = []
tot = 0
for fn in glob.glob("app/build/test-results/testDebugUnitTest/TEST-*ShellRoutingContractTest.xml"):
    r = ET.parse(fn).getroot()
    tot += int(r.get("failures")) + int(r.get("errors"))
    for tc in r.iter("testcase"):
        if tc.find("failure") is not None or tc.find("error") is not None:
            n.append(tc.get("name"))
print(" ".join(x[:22] for x in n), "｜ failures+errors =", tot)
PY
  fi
  restore
}

echo "[基线]"; ./gradlew $JVMARGS :app:testDebugUnitTest --tests "$CLS" > /tmp/shell_neg_baseline.log 2>&1
BASE=$?
echo "  gradle exit=$BASE（必须 0，基线不绿的负控一律不采信）"
# ⚠ 这句话 2026-10-01 之前只印不判：那晚基线红着（ShellDecision.kt 被**同一本簿的另一个并行副本**改着），
#   下面 7 条照跑照打，看起来像一轮完整的负控。不绿的基线一律在第一条破口之前退出。
[ "$BASE" = 0 ] || { echo "✘ 基线未通过，本轮负控不采信（先看 /tmp/shell_neg_baseline.log 红的是哪条用例）"; exit 2; }

# M-1 未登录不再优先落登录页：残留 role=DRIVER 的陌生人被直接放进司机工作台
red "$SD" 'assert "!loggedIn -> Routes.LOGIN" in s; s = s.replace("    !loggedIn -> Routes.LOGIN\n", "", 1)'
case_run M-1 "删掉未登录分支"

# M-2 角色与壳的对应关系接反：司机进了用户壳（分域看起来还在，实际串了）
red "$SD" 'a = "    role == Role.DRIVER -> Routes.DRIVER_SHELL"; assert a in s; s = s.replace(a, "    role == Role.DRIVER -> Routes.USER_SHELL", 1)'
case_run M-2 "司机角色落到用户壳"

# M-3 壳路由不再拒绝深链：一条旧通知就能把整个会话搬进另一个壳
red "$SD" 'a = "    route == Routes.USER_SHELL || route == Routes.DRIVER_SHELL -> null"; assert a in s; s = s.replace(a + "\n", "", 1)'
case_run M-3 "壳路由接受深链"

# M-4 分域闸整个消失：用户会话被旧通知送进司机工作台/交接页
red "$SD" 'a = "    role != Role.DRIVER && route in Routes.DRIVER_ONLY -> null"; assert a in s; s = s.replace(a + "\n", "", 1)'
case_run M-4 "司机域路由对用户会话放行"

# M-5 闸写反成"不分角色一律拒"：司机点自己的提醒通知永远进不了消息页（过度收紧也是破口）
red "$SD" 'a = "    role != Role.DRIVER && route in Routes.DRIVER_ONLY -> null"; assert a in s; s = s.replace(a, "    route in Routes.DRIVER_ONLY -> null", 1)'
case_run M-5 "司机域路由对司机会话也拒"

# M-6 陌生路由在这里被静默吞掉：用户点了通知没反应，比崩更难查
red "$SD" 'a = "    else -> route"; assert a in s; s = s.replace(a, "    else -> null", 1)'
case_run M-6 "所有非司机域深链一律吞掉"

# M-7（V-3 新增）司机域路由被加上路径参数：精确集合匹配失效，带参深链绕过分域闸
red "$RS" 'a = "const val DRIVER_EXCEPTION = \"driverException\""; assert a in s; s = s.replace(a, "const val DRIVER_EXCEPTION = \"driverException/{legId}\"", 1)'
case_run M-7 "司机域路由变成带参数的路由（闸门口径被绕）"

echo "== 原样恢复 =="
restore
MISMATCH=0
if cmp -s "$BAK" "$SD"; then echo "[恢复] ShellDecision cmp 一致"; else echo "✘ [恢复] ShellDecision 与备份不一致，必须手工核对"; MISMATCH=1; fi
if cmp -s "$BAK2" "$RS"; then echo "[恢复] Routes cmp 一致"; else echo "✘ [恢复] Routes 与备份不一致，必须手工核对"; MISMATCH=1; fi
[ "$MISMATCH" = 0 ] || { echo "✘ 有文件没还原回去，后面的绿都是错的树"; exit 4; }
./gradlew $JVMARGS :app:testDebugUnitTest --tests "$CLS" > /tmp/shell_neg_recover.log 2>&1
AFTER=$?
echo "[恢复后] gradle exit=$AFTER（必须 0）"
[ "$AFTER" = 0 ] || { echo "✘ 恢复后用例仍红，本轮未收口（看 /tmp/shell_neg_recover.log）"; exit 2; }
bash tools/check_shell_routing_probes.sh > /tmp/shell_neg_probe_tail.log 2>&1
PROBE_RC=$?
tail -1 /tmp/shell_neg_probe_tail.log
# ⚠ 原来这行写成 `bash … | tail -1`：管道的退出码是 tail 的 0，本书的 rc 因此永远是 0，
#   判据簿红了也没人能从退出码看出来（同一形状的坑在 verify_device.sh 里已修过一次，判据 §八 ㉓ ⑤）。
[ "$PROBE_RC" = 0 ] || { echo "✘ 恢复后判据簿不通过（rc=$PROBE_RC），本轮负控不采信"; exit 6; }
