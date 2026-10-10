#!/usr/bin/env bash
# 收工顺序的执行器（判据 §八 ㉔ ⑤：负控全批在前、全量用例在后）。
# 为什么要有这个文件：那条顺序原先也只写在台账里，靠记性 —— 而"负控批还在跑就起全量 gradle"
# 正好是 2026-10-01 那批假读数的成因之一。这里把它变成"等锁释放才放行，锁没放就一直等"。
# 用法：bash tools/after_negctl_run_full_tests.sh   （后台跑，输出在 /tmp/after_negctl.log）
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT" || exit 2
LOCK="${NEGCTL_LOCK_DIR:-/tmp/negctl.lock}"
# ⚠ 结束标记**不写死本数**：批次脚本按 glob `tools/*_negative_controls.sh` 数本，本脚本也必须按同一个 glob 推期望值。
#   上一版这里钉的是字符串「共 16 本」，F-2 ⑧ 新写第 17 本（safe_call）之后它永远不会匹配，
#   接力会一路等到 MAX_WAIT 然后 exit 8 —— 而"等不到"看起来像"批次还没跑完"，不是像"口径过期了"。
EXPECTED_BOOKS=$(ls tools/*_negative_controls.sh 2>/dev/null | wc -l | tr -d ' ')
[ "$EXPECTED_BOOKS" -ge 1 ] || { echo "[异常] glob tools/*_negative_controls.sh 数出 0 本，不接力"; exit 9; }
END_MARK="共 $EXPECTED_BOOKS 本"
BATCH_LOG=/tmp/final_neg_batch.log
MAX_WAIT=${MAX_WAIT:-5400}
waited=0

echo "[等待] $(date '+%F %T') 锁=$LOCK 批次日志=$BATCH_LOG 期望本数=$EXPECTED_BOOKS"
while :; do
  if grep -q -- "$END_MARK" "$BATCH_LOG" 2>/dev/null; then
    echo "[放行] 批次已打印「$END_MARK」，用时 ${waited}s（$(date '+%T')）"
    break
  fi
  if [ ! -e "$LOCK" ]; then
    echo "[异常] 锁目录不存在且批次未打印结束标记 —— 不猜测，直接停，请人工核对 $BATCH_LOG"
    exit 7
  fi
  if [ "$waited" -ge "$MAX_WAIT" ]; then
    echo "[超时] 等待 ${MAX_WAIT}s 批次仍未结束，放弃自动接力（不并发跑 gradle）"
    exit 8
  fi
  sleep 20; waited=$((waited + 20))
done

# 再核一遍：批次数出的 rc 行必须是 EXPECTED_BOOKS 条，且 rc=0 也是 EXPECTED_BOOKS 条。
# ⚠ 这几处 grep 一律带 -a：批次日志里 eta 那一本的末行是被截断的 UTF-8 多字节，grep 于是把整份日志
#   判成二进制——实测（01:21 那份日志与当场复现）扫描结束时补一句 "Binary file … matches"，
#   这句**混在读数列表里**、还会被下一段管道当成一条读数往下传（`grep -c` 不受影响，所以数字是对的、
#   列表是脏的——这种"一半能用一半污染"的读数最容易骗人）。-a 之后两句都不出现。
n_readings=$(grep -a -c ' rc=' "$BATCH_LOG")
n_zero=$(grep -a -c ' rc=0 ' "$BATCH_LOG")
echo "[批次读数核对] rc 行 $n_readings / 其中 rc=0 → $n_zero（期望 $EXPECTED_BOOKS / $EXPECTED_BOOKS）"
grep -a ' rc=' "$BATCH_LOG"
if [ "$n_readings" -ne "$EXPECTED_BOOKS" ] || [ "$n_zero" -ne "$EXPECTED_BOOKS" ]; then
  # 不拦：rc≠0 的本子多半是"基线不绿自拒"，这时候**恰恰要跑全量**才能看出树脏在哪一行。
  # 但也不许让它悄悄过去——把不合格的本子逐本点名，收口时按名字补单本复跑。
  echo "[警告] 批次不成立：rc 行 $n_readings（期望 $EXPECTED_BOOKS）、rc=0 $n_zero（期望 $EXPECTED_BOOKS）。以下各本必须还原后单本复跑，复跑读数才算数："
  grep -a ' rc=' "$BATCH_LOG" | grep -a -v ' rc=0 '
  echo "[警告] 全量用例照跑（01:20 那次就是靠它把脏树钉红的），但台账里不得引用本轮批次为『$EXPECTED_BOOKS 本全绿』"
fi

echo "[全量] $(date '+%T') 起 :app:testDebugUnitTest --rerun-tasks"
./gradlew "-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK" \
  -Dfile.encoding=GBK :app:testDebugUnitTest --rerun-tasks > /tmp/final_fulltest.log 2>&1
GR=$?
echo "[全量] gradle rc=$GR  $(date '+%T')"
grep -E "BUILD (SUCCESSFUL|FAILED)|actionable tasks" /tmp/final_fulltest.log | tail -3

DIR=app/build/test-results/testDebugUnitTest
echo "--- XML 逐份（类名 / tests / failures / errors / skipped / mtime）---"
python - "$DIR" <<'PY'
import sys, os, glob, re, datetime
d = sys.argv[1]
files = sorted(glob.glob(os.path.join(d, "TEST-*.xml")))
tot = 0
rows = []
for f in files:
    s = open(f, encoding="utf-8", errors="replace").read()
    m = re.search(r"<testsuite\b[^>]*>", s)
    if not m:
        print("NO-TESTSUITE", f); continue
    tag = m.group(0)
    def attr(k):
        mm = re.search(k + r'="([^"]*)"', tag)
        return mm.group(1) if mm else "?"
    t = int(attr("tests")); fa = int(attr("failures")); er = int(attr("errors")); sk = int(attr("skipped"))
    tot += t
    rows.append((attr("name"), t, fa, er, sk,
                 datetime.datetime.fromtimestamp(os.path.getmtime(f)).strftime("%H:%M:%S")))
for r in rows:
    print("%-58s tests=%-3s fail=%s err=%s skip=%s %s" % r)
print("类数 %d 例数合计 %d failures %d errors %d skipped %d" % (
    len(rows), tot, sum(r[2] for r in rows), sum(r[3] for r in rows), sum(r[4] for r in rows)))
mt = sorted({r[5] for r in rows})
print("mtime 集合", mt)
PY
echo "[结束] $(date '+%F %T')"
