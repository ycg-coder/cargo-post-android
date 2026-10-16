#!/usr/bin/env bash
# §54 异常上报**用例**的负控制：把纯决策层与页面的每个判据逐个破口 → gradle 用例必须失败。
# 与 check_exception_probes.sh / exception_negative_controls.sh 的分工：那两套证**结构**没跑调，
# 这一条证 DriverExceptionReportTest 的 7 例与 DriverExceptionContractTest 的 6 例不是装饰。
# ⚠ 本脚本会**就地临时修改源码**并在结束时原样恢复；一次只允许跑一个改源码的负控脚本，
#   跑完必须看到 [恢复] cmp 一致 + 用例重新全绿，否则视为未收口。
# 用法：bash tools/exception_test_negative_controls.sh
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2
RP=app/src/main/java/com/shanxiang/buspost/ui/driver/DriverExceptionReport.kt
DS=app/src/main/java/com/shanxiang/buspost/ui/driver/DriverExceptionScreen.kt
BAK=/tmp/exrep.bak.$$
BAK2=/tmp/exscr.bak.$$
JVMARGS='-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=GBK'
CLS='--tests com.shanxiang.buspost.ui.driver.DriverExceptionReportTest --tests com.shanxiang.buspost.ui.driver.DriverExceptionContractTest'

cp "$RP" "$BAK"; cp "$DS" "$BAK2"
restore() { cp "$BAK" "$RP"; cp "$BAK2" "$DS"; }
trap 'restore; rm -f "$BAK" "$BAK2"; negctl_release' EXIT

red() { # $1=文件 $2=python 表达式（锚点不在必须报错；注入后字节没变也算空跑，都不许往下跑）
  local before after
  before=$(md5sum < "$1")
  python - "$1" "$2" <<'PY'
import sys
p, expr = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8').read()
exec(expr)                     # 锚点漂了必须报错而不是空跑
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
  if [ ! -s "$RP" ] || [ ! -s "$DS" ]; then echo "✘ $1 源码为空，本轮不采信"; return; fi
  ./gradlew $JVMARGS :app:testDebugUnitTest $CLS > "/tmp/ex_neg_$1.log" 2>&1
  code=$?
  if [ "$code" = 0 ]; then
    echo "✘ $1  $2 → 用例仍然全绿，这条判据证明不了任何东西"
  else
    printf '✔ %s  %s → gradle exit=%s，红了：' "$1" "$2" "$code"
    # 用例名必须显式 UTF-8 打印：GBK 控制台会把中文用例名编成乱码，"红在哪几条"就成了没有证据（F-3 ③）
    PYTHONIOENCODING=utf-8 python - <<'PY'
import glob, xml.etree.ElementTree as ET
n = []; tot = 0
for fn in glob.glob("app/build/test-results/testDebugUnitTest/TEST-*DriverException*.xml"):
    r = ET.parse(fn).getroot()
    tot += int(r.get("failures")) + int(r.get("errors"))
    for tc in r.iter("testcase"):
        if tc.find("failure") is not None or tc.find("error") is not None:
            n.append(tc.get("name"))
print(" ".join(x[:24] for x in n), "｜ failures+errors =", tot)
PY
  fi
  restore
}

echo "[基线]"; ./gradlew $JVMARGS :app:testDebugUnitTest $CLS > /tmp/ex_neg_baseline.log 2>&1
BASE=$?
echo "  gradle exit=$BASE（必须 0，基线不绿的负控一律不采信）"
[ "$BASE" = 0 ] || { echo "✘ 基线未通过，负控结果不采信"; exit 2; }

# X-1 「其他」不再强制写详情：选错分类能发出一条没有任何信息的 remark
red "$RP" 'a = "    if (reason.requiresDetail && text.isEmpty()) return null"; assert a in s; s = s.replace(a + "\n", "", 1)'
case_run X-1 "选「其他」不写详情也照样构造出报文"

# X-2 拼接分隔符从全角冒号换成半角：用例钉的是逐字文本，措辞漂移必须被抓到
red "$RP" 'a = "else \"${reason.label}：$text\""; assert a in s; s = s.replace(a, "else \"${reason.label}:$text\"", 1)'
case_run X-2 "分类与详情的连接符被换掉"

# X-3 截断路径删掉：超长 remark 直接出网，后端 varchar(500) 那一侧是 1406 整笔回滚
red "$RP" 'a = "    else joined.substring(0, EXCEPTION_REMARK_MAX - 1) + \"…\""; assert a in s; s = s.replace(a, "    else joined", 1)'
case_run X-3 "remark 不再截断"

# X-4 上限从 200 抬到 2000：越界值本身没变，但客户端承诺的预算变了
red "$RP" 'a = "internal const val EXCEPTION_REMARK_MAX = 200"; assert a in s; s = s.replace(a, "internal const val EXCEPTION_REMARK_MAX = 2000", 1)'
case_run X-4 "remark 上限被抬高"

# X-5 详情不再 trim：前后各一个空格也能出网，后端落库就带脏字符
red "$RP" 'a = "    val text = detail.trim()"; assert a in s; s = s.replace(a, "    val text = detail", 1)'
case_run X-5 "详情不做 trim"

# X-6 页面不再拦终态段：已完成/已异常的段照样把异常发给后端
red "$DS" 'a = "        if (!DriverLegStateMapper.of(leg).canReportException) {"; assert a in s; s = s.replace(a, "        if (false) {", 1)'
case_run X-6 "终态段的出网守卫被摘掉"

# X-7 报文里塞自造的 type 键：后端 ReqVO 只认 driverId/legId/remark，多一个键就是假契约
# ⚠ D-135 重锚：页面里已经没有内联 mapOf（提交体由 exceptionActionBody 拼），注入点挪到这个纯函数上。
#   判的还是同一件事：报文里长出后端不认识的键 ⇒ 契约用例必须红。
red "$RP" 'a = "if (photoUrl.isEmpty()) mapOf(\"remark\" to remark)"; assert a in s; s = s.replace(a, "if (photoUrl.isEmpty()) mapOf(\"remark\" to remark, \"type\" to \"ROAD_BLOCKED\")", 1)'
case_run X-7 "报文多出后端没有的 type 键"

# X-8 回读不一致也报成功：后端没接受这一段，界面仍说"已按后端回读刷新"
red "$DS" 'a = "                    LegStatus.of(back.status) != LegStatus.EXCEPTION ->"; assert a in s; s = s.replace(a, "                    false ->", 1)'
case_run X-8 "取消回读不一致那句（谎报成功）"

echo "== 原样恢复 =="
restore
MISMATCH=0
for pair in "$BAK:$RP" "$BAK2:$DS"; do
  f=${pair%%:*}; t=${pair#*:}
  if cmp -s "$f" "$t"; then echo "[恢复] $t cmp 一致"; else echo "✘ [恢复] $t 与备份不一致，必须手工核对"; MISMATCH=1; fi
done
[ "$MISMATCH" = 0 ] || { echo "✘ 有文件没还原回去，后面的绿都是错的树"; exit 4; }
./gradlew $JVMARGS :app:testDebugUnitTest $CLS > /tmp/ex_neg_recover.log 2>&1
AFTER=$?
echo "[恢复后] gradle exit=$AFTER（必须 0）"
[ "$AFTER" = 0 ] || { echo "✘ 恢复后用例仍红，本轮未收口（看 /tmp/ex_neg_recover.log）"; exit 2; }
bash tools/check_exception_probes.sh > /tmp/ex_neg_probe_tail.log 2>&1
PROBE_RC=$?
tail -1 /tmp/ex_neg_probe_tail.log
# ⚠ 原写法 `bash … | tail -1`：管道退出码是 tail 的 0 ⇒ 本书 rc 永远是 0，判据簿红了也从码上看不出来
[ "$PROBE_RC" = 0 ] || { echo "✘ 恢复后判据簿不通过（rc=$PROBE_RC），本轮负控不采信"; exit 6; }
