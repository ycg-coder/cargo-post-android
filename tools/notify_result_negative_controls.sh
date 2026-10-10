#!/usr/bin/env bash
# NW-a…NW-h（tools/check_notify_result_probes.sh）的**负控制**（F-2 第 10 步的配套簿）。
# 用法：bash tools/notify_result_negative_controls.sh
#
# 这一簿要回答的正是 ⑩ 那一层最容易假绿的地方：NW-a/NW-d/NW-f/NW-g/NW-h 里有五个"目标 0"，
# 而"扫到 0"与"扫坏了所以 0"输出一模一样。每条破口都必须把**它点名的那条**打红，
# 另外还要两条反向（注释里提到禁止形状必须不算残留）与两条预检（文件不在场必须是 exit 2）。
#
# 只在临时目录副本上动手（含 app/src/test 的那本用例与 gradle 产物 XML，NW-h 读的就是这两份）。
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码，两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3

SC=/tmp/nw_neg.$$
CLEAN=/tmp/nw_neg_clean.$$
PKG=com/shanxiang/buspost
PR="$SRC/tools/check_notify_result_probes.sh"
XMLREL=app/build/test-results/testDebugUnitTest/TEST-com.shanxiang.buspost.notify.NotificationWorkerResultTest.xml

mkdir -p "$SC/app/src/main" "$SC/app/src/test" "$SC/app/build/test-results/testDebugUnitTest"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
[ -f "$SRC/$XMLREL" ] || { echo "✘ 真身没有 $XMLREL —— NW-h 的读数没有来源，先跑 :app:testDebugUnitTest"; exit 2; }
cp "$SRC/$XMLREL" "$SC/$XMLREL"

A="$SC/app/src/main/java/$PKG"
NW="$A/notify/NotifyWorker.kt"
NO="$A/notify/NotifyOutcome.kt"
AE="$A/notify/AlertEngine.kt"
REPO="$A/data/repo/Repo.kt"
TEST="$SC/app/src/test/java/$PKG/notify/NotificationWorkerResultTest.kt"
for f in "$NW" "$NO" "$AE" "$REPO" "$TEST" "$SC/$XMLREL"; do
    [ -f "$f" ] || { echo "✘ 负控文件缺失：$f（副本没复制全，下面每一条都会假绿）"; exit 2; }
done
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT
cp -r "$SC" "$CLEAN"
[ -f "$CLEAN/$XMLREL" ] || { echo "✘ 干净副本没建成，本轮负控全部作废"; exit 2; }

FAILED=0
N_OK=0 N_BAD=0

run() { out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?; }
ctl() { # $1=说明 $2=应当点名的判据（NW-x）
    run
    if [ "$code" = 1 ] && printf '%s\n' "$out" | grep -q "^× $2"; then
        echo "✔ $2  $1 → exit 1：$(printf '%s\n' "$out" | grep "^× $2" | head -1)"; N_OK=$((N_OK + 1))
    elif [ "$code" = 1 ]; then
        echo "△ $2  $1 → exit 1，但报的是别的判据：$(printf '%s\n' "$out" | grep '^× ' | head -1)"
        N_BAD=$((N_BAD + 1)); FAILED=1
    else
        echo "✘ $2  $1 → 没变红（exit $code），这条判据是永不为红的摆设"
        N_BAD=$((N_BAD + 1)); FAILED=1
    fi
    rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
# 预检类：输入不在场时必须是 exit 2（"跑不了"），不能被读成 exit 1 的普通破口，更不能是 0
ctl_pre() { # $1=说明 $2=期望输出里的关键词
    run
    if [ "$code" = 2 ] && printf '%s\n' "$out" | grep -q "$2"; then
        echo "✔ 预检  $1 → exit 2 且没有输出任何计数行"; N_OK=$((N_OK + 1))
    else
        echo "✘ 预检  $1 → 期望 exit 2，实测 exit $code：$(printf '%s\n' "$out" | head -1)"
        N_BAD=$((N_BAD + 1)); FAILED=1
    fi
    rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
# 反向负控：注入一个"注释里提到禁止形状"的文本，判据必须**保持全绿**，
# 且 $2 里给的字面量必须出现在输出里（否则这条是"什么都没发生"的空跑，不算证据）。
ctl_keep() { # $1=说明 $2=必须出现在输出里的字面量
    run
    if [ "$code" = 0 ] && ! printf '%s\n' "$out" | grep -q '^× ' && printf '%s\n' "$out" | grep -q "$2"; then
        echo "✔ 不误报  $1 → 判据仍全绿，且注入确实被读到（$(printf '%s\n' "$out" | grep -m1 "$2")）"; N_OK=$((N_OK + 1))
    elif [ "$code" = 0 ]; then
        echo "✘ 不误报  $1 → 判据绿了，但输出里找不到 $2：注入没进扫描面，这条是空跑"; N_BAD=$((N_BAD + 1)); FAILED=1
    else
        echo "✘ 不误报  $1 → 判据被判红（应绿）：$(printf '%s\n' "$out" | grep '^× ' | head -2 | tr '\n' ' ')"
        N_BAD=$((N_BAD + 1)); FAILED=1
    fi
    rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# 注入器：锚点没命中就当场 exit 2 ——「以为注入了、其实树没动」是本工作区踩过两次的假绿来源（D-037）
# ⚠ 替换文本不得再包含锚点原文（SELF-MATCH），否则判据会读到注释里那句，破口看起来还在 ⇒ 假绿。
# ⚠ Git Bash 会把**以 `/` 开头的参数**当路径折叠（`// 注释` 传进 python 就变成 `/ 注释`，2026-10-01 实测）：
#   注释类注入一律以空格缩进开头，判据的 NC 排除照样认；这里再加一道硬闸，宁可这条负控不成立也不静默注错。
swap() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swap 锚点没命中，破口根本没注入（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
assert old not in new, "SELF-MATCH: 替换文本里还留着锚点"
assert not new.startswith("/"), "SLASH-MANGLED: 替换文本以 / 开头，八成是 shell 把 // 折成了 /（给注释加缩进）"
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
PY
}
# after：在唯一那一行后面插一段（可多行）。锚点必须全文件唯一，否则插错位置就成了"假破口"。
after() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ after 锚点没命中/不唯一（这条负控不成立）"; exit 2; }
import sys
p, needle, add = sys.argv[1], sys.argv[2], sys.argv[3]
assert not add.startswith("/"), "SLASH-MANGLED: 插入文本以 / 开头，八成是 shell 把 // 折成了 /（给注释加缩进）"
ls = open(p, encoding='utf-8', newline='').read().split("\n")
k = [i for i, x in enumerate(ls) if needle in x]
assert len(k) == 1, "ANCHOR-NOT-UNIQUE(%s): %s" % (len(k), needle[:60])
ls[k[0] + 1:k[0] + 1] = add.split("\n")
open(p, 'w', encoding='utf-8', newline='').write("\n".join(ls))
PY
}
drop_line() { python - "$1" "$2" <<'PY' || { echo "✘ drop 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p, needle = sys.argv[1], sys.argv[2]
ls = open(p, encoding='utf-8', newline='').read().split("\n")
k = [i for i, x in enumerate(ls) if needle in x]
assert k, "ANCHOR-MISS: " + needle[:60]
del ls[k[0]]
open(p, 'w', encoding='utf-8', newline='').write("\n".join(ls))
PY
}

# ── NW-a 无条件的"这轮跑好了" ───────────────────────────────────────────────
# 1 本轮修的假话本身：结局算出来了，却不被分支消费。
swap "$NW" 'return when (notifyDirective(scan.outcome)) {' 'return Result.success() // 探针：结局又被丢掉'
ctl "doWork 回到无条件 success（⑩ 修的那句假话原样复活）" NW-a

# ── NW-b 三个动作的接线 ─────────────────────────────────────────────────────
# 2 临时失败不再 retry：弱网之后提醒要迟一个整周期，而日志写着成功。
swap "$NW" 'NotifyDirective.RETRY -> Result.retry()' 'NotifyDirective.RETRY -> Result.success()'
ctl "RETRY 分支被改成 success（同轮 NW-a 的全工程自证也会红：多了一条非分支 success）" NW-b

# 3 映射表少一条臂：NOT_LOGGED_IN 不再被点名（穷尽 when 在真树上会编译不过，这里只证判据会红）
drop_line "$NO" '    NotifyOutcome.NOT_LOGGED_IN,'
ctl "映射表少一条 when 臂（结局与动作之间断了一格）" NW-b

# ── NW-c 会话失效的真实出口 ─────────────────────────────────────────────────
# 4 只把 401 翻成 failure 不清会话：WorkManager 会拿死 token 每 15 分钟再撞一次。
swap "$NW" 'if (scan.outcome == NotifyOutcome.SESSION_INVALID) Repo.clearSessionLocal()' \
           '            // 探针：401 不清会话'
ctl "401 没接清会话出口" NW-c

# ── NW-d 取消这一层 ─────────────────────────────────────────────────────────
# 5 分类器把取消收成结局：⑨ 的红线，取消被读成"本地数据坏了"。
swap "$NO" 'e is CancellationException -> throw e' \
           'e is CancellationException -> NotifyOutcome.UNRECOVERABLE_LOCAL'
ctl "分类器不再原样抛取消" NW-d

# 6 结局里出现 CANCELLED：有人在 Worker 里收取消（同轮 NW-d 的枚举窗口自证 7→8 也会红）
after "$NO" '    UNRECOVERABLE_LOCAL,' '    CANCELLED,'
ctl "枚举里加了 CANCELLED（证明 NW-d 那个 CANCEL=0 不是空窗口）" NW-d

# ── NW-e 一轮的结局有没有出口 ───────────────────────────────────────────────
# 7 check() 退回 Unit：Worker 重新拿不到结局，只能一律 success。
swap "$AE" 'suspend fun check(context: Context): AlertScan {' 'suspend fun check(context: Context) {'
ctl "check() 又不返回 AlertScan" NW-e

# 8 少一路出口：通知开关关掉时又变成裸 return（与"没新消息"共用一个形状）
swap "$AE" 'if (!Prefs.notifyEnabled) return AlertScan(NotifyOutcome.NOTIFICATION_DISABLED, announced = 0)' \
           'if (!Prefs.notifyEnabled) return'
ctl "四种状态的 AlertScan 出口少了一路" NW-e

# ── NW-f 分类拿得到类型 ─────────────────────────────────────────────────────
# 9 三处轮询少一处：寄货侧（到站提醒那条）不把异常带出来。
swap "$REPO" 'Feed(emptyList(), error = errorTextOf(e), cause = e)' \
             'Feed(emptyList(), error = errorTextOf(e))'
ctl "三处轮询有一处不传 cause（这条打到第一处 sendOrders，总数与落点同时红）" NW-f

# 10 落点漂：通知侧不传，非轮询的公告侧传 —— 总数仍是 3，只有逐点窗口会红。
swap "$REPO" $'        Feed(p.list.map { NotifyMsg.parse(it) }, p.demo, total = p.total)\n    } catch (e: CancellationException) {\n        throw e\n    } catch (e: Exception) {\n        Feed(emptyList(), error = errorTextOf(e), cause = e)' \
             $'        Feed(p.list.map { NotifyMsg.parse(it) }, p.demo, total = p.total)\n    } catch (e: CancellationException) {\n        throw e\n    } catch (e: Exception) {\n        Feed(emptyList(), error = errorTextOf(e))'
after "$REPO" 'suspend fun notices(): Feed<Notice> = try {' \
              '        Feed(emptyList(), error = errorTextOf(e), cause = e) // 探针：落点漂到公告侧'
ctl "cause 总数对得上但落点漂了（通知侧没了、公告侧多出来）" NW-f

# ── NW-g 指令点名的禁止形状 ───────────────────────────────────────────────────
# 11 catch(Exception) 之后紧跟 Result.success()：一条破口同时打 NW-g 与 NW-a（这是它的真实关系）。
after "$NW" 'override suspend fun doWork(): Result {' \
            $'        try { throw Exception("探针：后端不可用") } catch (e: Exception) {\n            return Result.success()\n        }'
ctl "指令禁止的形状回来了（同轮 NW-a 的无条件 success 也会红）" NW-g

# ── NW-h 用例读数与点名 ─────────────────────────────────────────────────────
# 12 指令点名的"空列表→success"那条用例被改名：判据必须认名字，不能只数条数。
swap "$TEST" 'fun `后端回空列表是没有新消息而不是失败`()' 'fun `空列表用例改个名`()'
ctl "五类点名的用例被改名（只数 @Test 条数抓不到）" NW-h

# ── 反向：注释里提到禁止形状，不算残留 ────────────────────────────────────────
# 13 NC 排除失效就会在这里红：KDoc 里再抄一遍旧写法，判据仍须全绿。
echo "注入自证（13）：改前的旧签名字面量次数 = $(grep -cF 'suspend fun check(context: Context) {' "$AE")"
after "$AE" 'object AlertEngine {' '    // 探针：旧写法是 suspend fun check(context: Context) { —— 只在注释里'
echo "注入自证（13）：改后文件内该字面量次数 = $(grep -cF 'suspend fun check(context: Context) {' "$AE")（判据须仍读成 0）"
ctl_keep "注释里提到旧签名（反注释的排除若失效，NW-e 的旧签名计数会变 1）" 'NW-e check新签名/旧签名/AlertScan出口/判据  = 1/0/4/1'

# 14 反向：注释里提一句无条件 success，NW-a 的两条计数都不许动
after "$NO" 'enum class NotifyDirective { SUCCEED, RETRY, FAIL }' \
            '    // 探针：旧写法是 return Result.success()（注释，不该被数进 NW-a）'
ctl_keep "注释里提到 return Result.success()" 'NW-a 无条件success/分支出口/管道自证      = 0/1/1'

# ── 预检：输入不在场必须是 exit 2 ────────────────────────────────────────────
# 15 判据文件缺失（ctl_pre 会把整份沙盒从干净基线重拷，所以不需要手工放回）
mv "$NW" "$NW.gone"
ctl_pre "NotifyWorker.kt 不在场（不能把缺文件读成计数 0）" "判据文件缺失"

# 16 gradle 产物缺失：用例没跑就没有读数
mv "$SC/$XMLREL" "$SC/$XMLREL.gone"
ctl_pre "test-results XML 不在场（不许填 0/0/0 交差）" "用例没跑就没有读数"

echo "----------------------------------------"
run
if [ "$code" = 0 ]; then
    echo "✔ 末行恢复基线 → 判据簿 exit 0（RESULT=PASS）"
else
    echo "✘ 末行基线不绿（exit $code）——沙盒恢复失败，本轮负控的其余读数都不能引用"
    printf '%s\n' "$out" | grep '^× ' | head -3
    FAILED=1
fi
echo "SUMMARY|book=notify_result|破口=12 反向=2 预检=2|命中=$N_OK 失效=$N_BAD"
[ "$FAILED" = 0 ] && echo "RESULT=PASS" || echo "RESULT=FAIL"
exit "$FAILED"
