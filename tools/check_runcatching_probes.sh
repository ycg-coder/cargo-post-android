#!/usr/bin/env bash
# F-2 第 9 步：`runCatching` 的协程取消形状判据（RC-a…RC-e）。
# 用法：bash tools/check_runcatching_probes.sh     退出码非 0 = 这一层的契约破了
#       bash tools/runcatching_negative_controls.sh 逐条打破口，确认每条都会红
#
# 钉的东西（指令 §三十一 / F-2 ⑨）：`runCatching` 捕获 Throwable，里面包含 CancellationException。
# 块体一旦含挂起调用，"页面已离开 / Worker 已停 / 外层超时"就会被收进 Result，被下游读成一次普通失败。
# 这类点必须为 0 —— 但**不是**把在册的每一个 runCatching 删光来得到 0（指令原文禁止"为了得到 0 而误删合法 runCatching"）：
# 绝大多数块体是纯同步的（JSON 解析、SDK 句柄、Handler/Intent），删了就是把错误处理删了。
#
# 与 TH-g 的分工（不要混成一个数）：TH-g 是**点名形状**（7 条正则，逐条形如 `runCatching \{ Repo\.`），
# RC-b 是**闭包判定**：从全工程 98 个 `suspend fun` 名 + 一组无歧义挂起入口求交集，块体级扫描，
# 并区分 `Api.get(`（真挂起）与 `main.post(`（Handler）/ `sp.getString`（SharedPreferences）。
# 检测器实现见 tools/runcatching_detector.py，它同时给出免疫前提 runBlocking / suspendCancellableCoroutine 的计数。
#
# 写法沿用既有教训：先验文件存在；按内容整行字面量而非行号；注释行排除（$NC）；
# "0 命中"必须由负控制反证同一条管道能命中非零；两套口径（出现次数 / 生效行 / 生效文件）不可互引。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

S=app/src/main/java/com/shanxiang/buspost
DET="$ROOT/tools/runcatching_detector.py"
DOC=docs/android/RUNCATCHING_AUDIT.md
REPO="$S/data/repo/Repo.kt"
NAV="$S/core/navigation/AmapNavigationEngine.kt"
ROOTUI="$S/ui/BusPostRoot.kt"
DNS="$S/ui/driver/DriverNavScreen.kt"
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'

for f in "$REPO" "$NAV" "$ROOTUI" "$DNS" "$DOC"; do
    [ -f "$f" ] || { echo "× 判据文件缺失：$f（先确认工程根/副本建对，别把缺文件读成计数 0）"; exit 2; }
done

# GUARD：计数管道若失去计数能力，"0 命中"与"真的没有"输出一模一样 ⇒ 先自证 grep 还数得动
G=$(printf 'aa bb\n' | grep -c 'bb')
[ "$G" = 1 ] || { echo "× GUARD 计数管道不工作（实测 $G），本本所有 0 一律不采信"; exit 3; }
echo "GUARD 计数管道               = $G （目标 1）"

PY=$(command -v python || command -v python3)
[ -n "$PY" ] || { echo "× 找不到 python：RC-b/RC-d 的闭包判定无法执行，不许把'跑不了'读成 0"; exit 2; }
DET_OUT=$("$PY" "$DET" "$S" 2>&1); det_code=$?
[ "$det_code" = 0 ] || { echo "× 检测器退出码 $det_code，本本不采信：$DET_OUT"; exit 2; }
SUM=$(printf '%s\n' "$DET_OUT" | grep '^SUMMARY|')
[ -n "$SUM" ] || { echo "× 检测器没有打 SUMMARY 行（管道或脚本变了形），本本不采信"; exit 2; }
echo "检测器原始行                 = $SUM"

num() { printf '%s\n' "$SUM" | tr '|' '\n' | grep "^$1=" | cut -d= -f2; }
D_SITES=$(num sites); D_VIOL=$(num violations); D_CAND=$(num candidates)
D_NAMES=$(num suspend_names); D_RB=$(num run_blocking); D_SCC=$(num scc)

# ---------- RC-a 基数与口径 ----------
RA_ACTIVE=$(grep -rn 'runCatching' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
RA_FILES=$(grep -rn 'runCatching' "$S" --include=*.kt | grep -vE "$NC" | cut -d: -f1 | sort -u | wc -l | tr -d ' ')
RA_RAW=$(grep -ro 'runCatching' "$S" --include=*.kt | wc -l | tr -d ' ')
RA_ALLFILES=$(grep -rl 'runCatching' "$S" --include=*.kt | wc -l | tr -d ' ')
# 反证（同一条管道必须数得出非零，否则那个"31"可能是管道坏了）：同一 NC 打在 attempt 上
RA_PROOF=$(grep -rn 'attempt {' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')

# ---------- RC-c 本轮迁走的 4 处：逐点钉整行字面量 ----------
CC1=$(grep -cF 'val forecast = attempt {' "$REPO")
CC2=$(grep -cF 'val accepted = attempt {' "$NAV")
CC3=$(grep -cF 'deepLinkTarget(route, Prefs.role)?.let { attempt { nav.navigate(it) } }' "$ROOTUI")
CC4=$(grep -cF 'if (ready.value) attempt { engine?.setLanguage(Locale.CHINESE) }' "$DNS")
# 这三处**只准**在注释里再提 runCatching：字面量若从代码里回来，上面的 attempt 计数就会掉，两条一起红
CC_BACK=$(grep -rn 'runCatching { nav.navigate(it)' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
CC_BACK2=$(grep -rn 'runCatching { engine?.setLanguage' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')

# ---------- RC-e 逐点分类表：42 行 = 4 已迁 + 38 保持（⑨ 的 30 + ⑭ 一处 + N-2 一处 + 2026-10-10 未入账那一片六处）----------
# 口径：保持行取标记块（不含表头/分隔行）；已迁行写在标记块上方，按"本轮动作"列的字面量数。
KEEP=$(awk '/<!-- RC-ROWS-BEGIN -->/,/<!-- RC-ROWS-END -->/' "$DOC" | grep -c '^| .* | 保持 |')
DOC_MIG=$(grep -c '^| .* | 已迁 attempt |' "$DOC")
ROWS=$((KEEP + DOC_MIG))
MIG_PROOF=$(grep -c '已迁 attempt\| RC-ROWS-BEGIN' "$DOC")

echo "RC-a 生效行/生效文件/出现次数/含注释文件 = $RA_ACTIVE/$RA_FILES/$RA_RAW/$RA_ALLFILES （目标恰好 39/18/60/26）"
echo "RC-a2 反证：同一 NC 管道的 attempt 行数   = $RA_PROOF （必须 >0，否则 RC-a 的数不可信）"
echo "RC-b 块内含挂起调用（unsafe/violation）  = $D_VIOL （目标 0）"
echo "RC-b2 检测器 sites/候选待复核/挂起名面     = $D_SITES/$D_CAND/$D_NAMES"
echo "RC-c 四处迁移点字面量                     = $CC1$CC2$CC3$CC4 / 回退残留 $CC_BACK/$CC_BACK2 （目标 1111 / 0/0）"
echo "RC-d 免疫前提 runBlocking/suspendCancellableCoroutine = $D_RB/$D_SCC （目标 0/0）"
echo "RC-e 分类表行/其中已迁                    = $ROWS/$DOC_MIG （目标 42/4）"

fail=0
# 基数 38 → 39 的逐点归因（2026-10-12 续七，按本簿自己的规矩"连同 RUNCATCHING_AUDIT 第三节一起数"）：
#   +3 = `7d8b819`（全面视觉升级 + 步行导航 + 收藏线路）在 **`ui/user/BusScreen.kt` 一个文件里**新加三处
#          （`git show 7d8b819` 里 `+` 行含 runCatching 的恰好 3 条，逐条读 diff 确认都落在 BusScreen）；
#   -2 = 本轮摘掉其中包着 suspend 调用的那两处（`Repo.productOrders/sendOrders` 自己回 Feed(error) 且
#          内部已穿透取消，外层 runCatching 收下的就是取消 ⇒ RC-b 实测点名的 violations=2）。
#   ⇒ 38 + 3 - 2 = 39；剩下的那一处是 `BusScreen.kt` 里 `val millis = runCatching {` 的**非挂起**解析，
#     它同时就是"生效文件 17 → 18"的唯一增量（BusScreen.kt 以前没有生效行）。
[ "$RA_ACTIVE" = 39 ] || { echo "× RC-a 生效 runCatching 行不是恰好 39（实测 $RA_ACTIVE）：F-2 ⑨ 迁走 4 处后是 30，F-3 ⑭ 读回 SDK 当前路径 +1 ⇒ 31，N-2 把语音开关交给高德 +1 ⇒ 32，2026-10-10 收未入账那一片 +6 ⇒ 38（AmapNavigationEngine.kt 的 prepare() released 出口摘监听 +1、loadOnce() 预加载 .so +1；新一屏 ui/driver/DriverGuideScreen.kt 的 AndroidView factory/update/onDispose 两步 +4）；再变就要连同 docs/android/RUNCATCHING_AUDIT.md 第三节与 D-043/D-053 一起逐点归因"; fail=1; }
# 基数 17 → 18：唯一增量同上，`ui/user/BusScreen.kt`（7d8b819 那轮带进来的那一处非挂起解析）。
[ "$RA_FILES" = 18 ]   || { echo "× RC-a 生效文件数不是恰好 18（实测 $RA_FILES）：16→17 的唯一增量是新增那本 ui/driver/DriverGuideScreen.kt；BusPostRoot.kt 的那一处仍是迁走的（少的那一个文件是谁？）"; fail=1; }
# 基数 56 → 58：+3（7d8b819 的三处）−2（本轮摘掉的整行）+1（本轮注释里点名 runCatching 说明为什么摘）。
#   出现次数与生效行**不可互引**（口径含注释与同行两次出现），所以两行分开打数。
# 基数 56 → 58 → 60：D-076 续七把 TraceScreen/BusScreen 那两处摘掉后是 58；本轮 #48 又在
#   `ui/common/UpdateSection.kt` 的**注释**里点名 runCatching 两次（"为什么这里必须用 attempt 而不是 runCatching"），
#   生效行与生效文件都没变（39/18）⇒ 这本账要区分"代码里真用"与"注释里解释为什么不用"。
#   含注释文件数 24 → 25 → 26 的第二次增量就是这一本 UpdateSection.kt。
[ "$RA_RAW" = 60 ]     || { echo "× RC-a 出现次数不是恰好 60（实测 $RA_RAW）：口径含注释与同行两次出现，与生效行不可互引（⑩ NotifyOutcome.kt 的 KDoc +1 ⇒ 47；⑭ 新增行 +1 ⇒ 48；N-2 的 setUseInnerVoice +1 ⇒ 49；2026-10-10 的 6 处生效 + DriverGuideScreen 类注释里点名 runCatching 那 1 行 ⇒ 56）"; fail=1; }
# 基数 24 → 25：唯一增量仍是 `ui/user/BusScreen.kt`（现在既有那处非挂起解析，也有注释点名它）。
[ "$RA_ALLFILES" = 26 ] || { echo "× RC-a 含注释文件数不是恰好 26（实测 $RA_ALLFILES）：23→24 的唯一增量是 ui/driver/DriverGuideScreen.kt；生效文件仍须是 17"; fail=1; }
[ "$RA_PROOF" -gt 0 ]  || { echo "× RC-a2 反证失败：同一套管道连 attempt 都数不出来，那 RC-a 的 38 不可信"; fail=1; }
[ "$D_VIOL" = 0 ]      || { echo "× RC-b 有 $D_VIOL 处 runCatching 块内含挂起调用：取消会被收进 Result 读成一次普通失败（明细看上面 VIOLATION 行）"; fail=1; }
[ "$D_SITES" = "$RA_ACTIVE" ] || { echo "× RC-b2 检测器 sites($D_SITES) 与 grep 生效行($RA_ACTIVE) 不一致：两套机制有一边失真，两边的数都不能用"; fail=1; }
[ "$CC1" = 1 ] || { echo "× RC-c Repo 预报解析不再是 attempt：取消会被读成'预报没数据'"; fail=1; }
[ "$CC2" = 1 ] || { echo "× RC-c 算路受理不再是 attempt：取消会被读成'算路请求没被受理'"; fail=1; }
[ "$CC3" = 1 ] || { echo "× RC-c 深链跳转不再是 attempt：LaunchedEffect 里的取消被吞"; fail=1; }
[ "$CC4" = 1 ] || { echo "× RC-c TTS 设语种不再是 attempt：离开页面时的取消被吞"; fail=1; }
[ "$CC_BACK" = 0 ] || { echo "× RC-c 有 nav.navigate 回到 runCatching（$CC_BACK 处）"; fail=1; }
[ "$CC_BACK2" = 0 ] || { echo "× RC-c 有 engine?.setLanguage 回到 runCatching（$CC_BACK2 处）"; fail=1; }
[ "$D_RB" = 0 ]  || { echo "× RC-d 出现了 runBlocking（$D_RB）：'非 suspend 函数不可能挂起'这条免疫前提作废，32 点'保持'的分类必须整表重做"; fail=1; }
[ "$D_SCC" = 0 ] || { echo "× RC-d 出现了 suspendCancellableCoroutine（$D_SCC）：同上，免疫前提作废"; fail=1; }
[ "$D_NAMES" -ge 98 ] || { echo "× RC-d 挂起名面小于 98（实测 $D_NAMES）：闭包判定的输入面缩水了（删了 suspend 函数还是改了收集规则？）"; fail=1; }
[ "$ROWS" = 42 ] || { echo "× RC-e 分类表不是 42 行（实测 $ROWS）：34（F-2 ⑨）+ 1（F-3 ⑭ 读回 SDK 当前路径）+ 1（N-2 把语音开关交给高德）+ 6（2026-10-10：AmapNavigationEngine 的摘监听/预加载 .so + 新一屏 DriverGuideScreen 的 factory/update/onDispose 两步）；在册面再变就得逐点归因，判据与账一起改"; fail=1; }
[ "$DOC_MIG" = 4 ] || { echo "× RC-e 表里标'已迁 attempt'的不是 4 行（实测 $DOC_MIG）：修改点与本轮结论不一致"; fail=1; }

echo "SUMMARY|rows=6|RC-a=$RA_ACTIVE RC-a2=$RA_PROOF RC-b=$D_VIOL RC-b2=$D_SITES/$D_CAND RC-c=$CC1$CC2$CC3$CC4 RC-d=$D_RB/$D_SCC RC-e=$ROWS/$DOC_MIG"
[ "$fail" = 0 ] && echo "RESULT=PASS" || echo "RESULT=FAIL"
exit "$fail"
