#!/usr/bin/env bash
# F-2 第 10 步：后台一轮的**失败语义**判据（NW-a…NW-i）。
# 用法：bash tools/check_notify_result_probes.sh      退出码非 0 = 这一层的契约破了
#       bash tools/notify_result_negative_controls.sh 逐条打破口，确认每条都会红
#
# 钉的东西（指令 v2.1 第 ⑩ 步原文："禁止 catch(Exception) { Result.success() }"）：
#   旧 `NotifyWorker.doWork()` 是 `attempt { AlertEngine.check(...) }` + `return Result.success()`——
#   结局被丢掉，网络失败/429/401 与"这轮确实没新消息"在 WorkManager 眼里是同一个 success()。
#   现在结局要有出口（AlertScan）、出口要分类（classifyNotifyFailure 按**类型**不按文案）、
#   分类要映射（notifyDirective 一张穷尽表）、映射要被消费（doWork 里的 when 三行）。
#
# 与其它簿的分工（口径不可互引）：
#   TH-j  钉的是 doWork 用 attempt 而不是 runCatching（取消那一半，F-1/⑨ 的红线）；
#   RC-*  钉的是全工程 runCatching 的块内含挂起调用数（⑨ 的闭包判定）；
#   NW-*  钉的是**结局有没有走到 WorkManager**（⑩ 这一轮）。NW-d 与 TH-j 有一个重叠锚点
#         （attempt 字面量），两处都留着是有意的：一本被改坏时另一本还会红。
#
# 写法沿用既有教训：先验文件存在；按内容整行字面量而非行号；注释行排除（$NC）；
# 每个"0 命中"旁边必须站着一个能数出非零的同类锚点（否则 0 可能只是管道坏了），
# 且 tools/notify_result_negative_controls.sh 要能逐条把它打红。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

S=app/src/main/java/com/shanxiang/buspost
NW="$S/notify/NotifyWorker.kt"
NO="$S/notify/NotifyOutcome.kt"
AE="$S/notify/AlertEngine.kt"
REPO="$S/data/repo/Repo.kt"
TEST=app/src/test/java/com/shanxiang/buspost/notify/NotificationWorkerResultTest.kt
XML=app/build/test-results/testDebugUnitTest/TEST-com.shanxiang.buspost.notify.NotificationWorkerResultTest.xml
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'

for f in "$NW" "$NO" "$AE" "$REPO" "$TEST"; do
    [ -f "$f" ] || { echo "× 判据文件缺失：$f（先确认工程根/沙盒副本建对，别把缺文件读成计数 0）"; exit 2; }
done

# GUARD：计数管道若失去计数能力，"0 命中"与"真的没有"输出一模一样 ⇒ 先自证 grep 还数得动
G=$(printf 'aa bb\n' | grep -c 'bb')
[ "$G" = 1 ] || { echo "× GUARD 计数管道不工作（实测 $G），本本所有 0 一律不采信"; exit 3; }
echo "GUARD 计数管道               = $G （目标 1）"

# ---------- NW-a 无条件 success 归零 ----------
NWA_UNCOND=$(grep -rn '^[[:space:]]*return Result\.success()' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
# 同一套管道必须数得出"那一条真存在的 success"，否则 NW-a 的 0 是空口的
NWA_BRANCH=$(grep -cF 'NotifyDirective.SUCCEED -> Result.success()' "$NW")
NWA_PROOF=$(grep -rn 'Result\.success()' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')

# ---------- NW-b 三个动作各被消费一次（穷尽 when，不许有第四种落点） ----------
NWB_WHEN=$(grep -cF 'when (notifyDirective(scan.outcome))' "$NW")
NWB_RETRY=$(grep -cF 'NotifyDirective.RETRY -> Result.retry()' "$NW")
NWB_FAIL=$(grep -cF 'NotifyDirective.FAIL -> Result.failure()' "$NW")
# 映射表的 when 臂必须逐个点到 7 个结局（少一个臂就是少一条被登记的映射）
NWB_TABLE=$(grep -c '^    NotifyOutcome\.' "$NO")

# ---------- NW-c 会话失效真的接了清会话出口（不是只在映射表里写了个 FAIL） ----------
NWC_WIRED=$(grep -cF 'if (scan.outcome == NotifyOutcome.SESSION_INVALID) Repo.clearSessionLocal()' "$NW")
NWC_EXIT=$(grep -cF 'fun clearSessionLocal()' "$REPO")

# ---------- NW-d 取消永不进这一层 ----------
NWd_ATTEMPT=$(grep -cF 'attempt { AlertEngine.check(applicationContext) }' "$NW")
NWd_BACK=$(grep -rn 'runCatching { AlertEngine' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
NWd_THROW=$(grep -cF 'e is CancellationException -> throw e' "$NO")
# 枚举体内不许出现 CANCELLED 这类值（出现了就是有人在 Worker 里收取消）。
# ⚠ 窗口本身要先证明"框对了"：同一 awk 窗口数得出 7 个常量，那个 0 才不是空窗口。
NWd_ENUM=$(awk '/enum class NotifyOutcome \{/,/^\}/' "$NO" | grep -cE '^    [A-Z_]+,?$')
NWd_CANCEL=$(awk '/enum class NotifyOutcome \{/,/^\}/' "$NO" | grep -c 'CANCEL')

# ---------- NW-e 一轮的结局有出口 ----------
NWE_SIG=$(grep -cF 'suspend fun check(context: Context): AlertScan {' "$AE")
# 旧签名若只出现在注释里（说明"以前是这样"），不能算残留 ⇒ 同一套 NC 排除
NWE_OLD=$(grep -nF 'suspend fun check(context: Context) {' "$AE" | grep -vE "$NC" | wc -l | tr -d ' ')
NWE_RET=$(grep -c 'return AlertScan(' "$AE")
NWE_JUDGE=$(grep -cF 'internal fun outcomeOf' "$AE")

# ---------- NW-f 分类拿得到真异常类型，不是中文文案 ----------
NWF_FIELD=$(grep -cF 'val cause: Throwable? = null' "$REPO")
NWF_PASS=$(grep -c 'error = errorTextOf(e), cause = e' "$REPO")
NWF_NOTIF=$(grep -A 10 'suspend fun notifications' "$REPO" | grep -c 'cause = e')
NWF_DRIVER=$(grep -A 10 'suspend fun driverMessages' "$REPO" | grep -c 'cause = e')
NWF_SEND=$(grep -A 10 'suspend fun sendOrders' "$REPO" | grep -c 'cause = e')
# 反证：分类若改用文案，判据里会出现 `error ==` / `message.contains` 这类字符串判据
NWF_TEXT=$(grep -rn 'error\?\.contains\|errorTextOf(e)\.contains' "$S/notify" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')

# ---------- NW-g 指令点名的那个禁止形状 ----------
NWG=$(grep -rn -A 2 'catch (e: Exception)' "$S/notify" --include=*.kt | grep -c 'Result\.success()')

# ---------- NW-h 用例真实读数（只认 gradle 产物，不认自述） ----------
if [ -f "$XML" ]; then
    NWH_T=$(sed -n 's/.*<testsuite[^>]*tests="\([0-9]*\)".*/\1/p' "$XML" | head -1)
    NWH_F=$(sed -n 's/.*<testsuite[^>]*failures="\([0-9]*\)".*/\1/p' "$XML" | head -1)
    NWH_E=$(sed -n 's/.*<testsuite[^>]*errors="\([0-9]*\)".*/\1/p' "$XML" | head -1)
else
    echo "× 预检：没有 $XML —— 用例没跑就没有读数，这里不许填 0/0/0 交差（先跑 :app:testDebugUnitTest）"; exit 2
fi
NWH_503=$(grep -c '503' "$TEST")
NWH_429=$(grep -c '429' "$TEST")
NWH_401=$(grep -c '401' "$TEST")
NWH_EMPTY=$(grep -cF '`后端回空列表是没有新消息而不是失败`' "$TEST")
NWH_OK=$(grep -cF '`正常回包并推出消息时动作是 success`' "$TEST")

echo "NW-a 无条件success/分支出口/管道自证      = $NWA_UNCOND/$NWA_BRANCH/$NWA_PROOF （目标 0/1/1）"
echo "NW-b when表/RETRY/FAIL/映射表行数         = $NWB_WHEN/$NWB_RETRY/$NWB_FAIL/$NWB_TABLE （目标 1/1/1/7）"
echo "NW-c 清会话接线/出口存在                  = $NWC_WIRED/$NWC_EXIT （目标 1/1）"
echo "NW-d attempt/回退残留/取消护栏/枚举7/CANCEL = $NWd_ATTEMPT/$NWd_BACK/$NWd_THROW/$NWd_ENUM/$NWd_CANCEL （目标 1/0/1/7/0）"
echo "NW-e check新签名/旧签名/AlertScan出口/判据  = $NWE_SIG/$NWE_OLD/$NWE_RET/$NWE_JUDGE （目标 1/0/4/1）"
echo "NW-f cause字段/传递总数/通知|司机|寄货/文案判据 = $NWF_FIELD/$NWF_PASS/$NWF_NOTIF$NWF_DRIVER$NWF_SEND/$NWF_TEXT （目标 1/3/111/0）"
echo "NW-g catch(Exception)→success()           = $NWG （目标 0）"
echo "NW-h 用例 通过/失败/错误                   = $NWH_T/$NWH_F/$NWH_E （目标 ≥14/0/0）"
echo "NW-h2 指令点名的五类各在场                  = 503:$NWH_503 429:$NWH_429 401:$NWH_401 空:$NWH_EMPTY 正常:$NWH_OK"

fail=0
[ "$NWA_UNCOND" = 0 ] || { echo "× NW-a 又有无条件 return Result.success()（$NWA_UNCOND 处）：这一轮无论发生什么都算跑好了，就是 ⑩ 修的那句假话"; fail=1; }
[ "$NWA_BRANCH" = 1 ] || { echo "× NW-a SUCCEED 分支的 success() 字面量不在位（实测 $NWA_BRANCH）"; fail=1; }
[ "$NWA_PROOF" = 1 ] || { echo "× NW-a 全工程非注释的 Result.success() 不是恰好 1（实测 $NWA_PROOF）：多出来的是谁在无条件报成功？"; fail=1; }
[ "$NWB_WHEN" = 1 ] || { echo "× NW-b doWork 不再按 notifyDirective 分支：结局走到映射表就断了"; fail=1; }
[ "$NWB_RETRY" = 1 ] || { echo "× NW-b 临时失败没有映射成 Result.retry()：弱网之后提醒要迟一个周期"; fail=1; }
[ "$NWB_FAIL" = 1 ] || { echo "× NW-b 不可恢复失败没有映射成 Result.failure()：401 会被排成下一轮继续撞"; fail=1; }
[ "$NWB_TABLE" = 7 ] || { echo "× NW-b 映射表点到结局的 when 臂不是 7 个（实测 $NWB_TABLE）：有一个结局没被登记，穷尽表就少一条依据"; fail=1; }
[ "$NWC_WIRED" = 1 ] || { echo "× NW-c 会话失效没接清会话出口：Worker 会拿死 token 每 15 分钟撞一次"; fail=1; }
[ "$NWC_EXIT" = 1 ] || { echo "× NW-c Repo.clearSessionLocal 不在了：401 的唯一本机关闭口没了"; fail=1; }
[ "$NWd_ATTEMPT" = 1 ] || { echo "× NW-d doWork 的 attempt 锚点不在位（与 TH-j 同源，两本都得红）"; fail=1; }
[ "$NWd_BACK" = 0 ] || { echo "× NW-d AlertEngine.check 回到了 runCatching（$NWd_BACK 处）"; fail=1; }
[ "$NWd_THROW" = 1 ] || { echo "× NW-d 分类器不再原样抛取消：谁把 attempt 换回 runCatching，取消就会被读成一个 failure()"; fail=1; }
[ "$NWd_ENUM" = 7 ] || { echo "× NW-d 枚举窗口只数出 $NWd_ENUM 个常量：NW-d 的 CANCEL=0 是空窗口还是真没有，说不清"; fail=1; }
[ "$NWd_CANCEL" = 0 ] || { echo "× NW-d 结局里出现了 CANCELLED 这类值：有人在 Worker 里收取消（⑨ 的红线）"; fail=1; }
[ "$NWE_SIG" = 1 ] || { echo "× NW-e check() 又不返回 AlertScan：Worker 重新拿不到结局"; fail=1; }
[ "$NWE_OLD" = 0 ] || { echo "× NW-e 旧的 Unit 签名还在（$NWE_OLD 处）"; fail=1; }
[ "$NWE_RET" = 4 ] || { echo "× NW-e AlertScan 出口不是 4 个（实测 $NWE_RET）：四种状态的 return 少了一路"; fail=1; }
[ "$NWE_JUDGE" = 1 ] || { echo "× NW-e outcomeOf 判据不见了或被改成 private（用例就无法在没有 Context 时驱动它）"; fail=1; }
[ "$NWF_FIELD" = 1 ] || { echo "× NW-f Feed.cause 字段不在位：分类只能退回中文文案"; fail=1; }
[ "$NWF_PASS" = 3 ] || { echo "× NW-f 传 cause 的 poll 路径不是 3 处（实测 $NWF_PASS）：漏的那一条在 Worker 眼里等于'没失败'"; fail=1; }
[ "$NWF_NOTIF$NWF_DRIVER$NWF_SEND" = 111 ] || { echo "× NW-f 三处逐点核对 $NWF_NOTIF/$NWF_DRIVER/$NWF_SEND 不是 1/1/1：总数对得上但落点漂了"; fail=1; }
[ "$NWF_TEXT" = 0 ] || { echo "× NW-f 出现了拿文案当分类判据的写法（$NWF_TEXT 处）"; fail=1; }
[ "$NWG" = 0 ] || { echo "× NW-g 指令点名的禁止形状又出现了：catch (e: Exception) 之后就是 Result.success()"; fail=1; }
[ "${NWH_T:-0}" -ge 14 ] || { echo "× NW-h 用例数少于 14（实测 $NWH_T）：⑩ 点名的五类之外那几条被删了？"; fail=1; }
[ "$NWH_F" = 0 ] || { echo "× NW-h 用例失败 $NWH_F 例"; fail=1; }
[ "$NWH_E" = 0 ] || { echo "× NW-h 用例错误 $NWH_E 例"; fail=1; }
[ "$NWH_503" -ge 1 ] && [ "$NWH_429" -ge 1 ] && [ "$NWH_401" -ge 1 ] && [ "$NWH_EMPTY" = 1 ] && [ "$NWH_OK" = 1 ] \
    || { echo "× NW-h2 指令点名的五类（网络/429/正常/空/会话）有用例被改名或删掉：503=$NWH_503 429=$NWH_429 401=$NWH_401 空=$NWH_EMPTY 正常=$NWH_OK"; fail=1; }

echo "SUMMARY|rows=8|NW-a=$NWA_UNCOND/$NWA_BRANCH NW-b=$NWB_WHEN/$NWB_RETRY/$NWB_FAIL NW-c=$NWC_WIRED NW-d=$NWd_ATTEMPT/$NWd_BACK/$NWd_THROW/$NWd_CANCEL NW-e=$NWE_SIG/$NWE_OLD/$NWE_RET NW-f=$NWF_PASS NW-g=$NWG NW-h=$NWH_T/$NWH_F/$NWH_E"
[ "$fail" = 0 ] && echo "RESULT=PASS" || echo "RESULT=FAIL"
exit "$fail"
