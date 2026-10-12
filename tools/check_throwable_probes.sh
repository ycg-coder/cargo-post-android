#!/usr/bin/env bash
# 异常可信化判据（指令 v2.1 §六 / §七 / §三十一；DECISION_LOG D-034；审计 §80）
# 用法：bash tools/check_throwable_probes.sh      退出码非 0 = 这一层的契约破了
#
# 这一层错了，上面所有页面的「空态 / 错误条 / 演示标签 / 取消后不再写画面」都会跟着错：
#   §六  —— 演示构建的失败回落面只准是「传输失败」与「业务失败」两类真实后端问题；
#           IllegalStateException / NullPointerException / ClassCastException / LinkageError 一律原样抛出。
#   §七  —— 协程取消不是失败：catch(Exception) 与 runCatching 都会接住 CancellationException，
#           于是「页面已经离开」被写成一次「加载失败」，还继续往已离场的 StateFlow 上写。
# 行为由 app/src/test/.../core/net/{DemoExceptionIsolationTest,ApiCancellationTest}.kt 钉；
# 这里钉**结构**（工程无 Robolectric，UI 那半边渲染不出来，只能按内容复核出口还在不在）。
# 写法沿用既有教训：先验文件存在；按内容不按行号；注释行排除；"恰好 N"钉整行字面量；
# "目标为 0"的判据由 tools/throwable_negative_controls.sh 反向证明同一条 grep 能命中非零。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

S=app/src/main/java/com/shanxiang/buspost
API="$S/core/net/Api.kt"
ATT="$S/core/net/Attempt.kt"
AG="$S/data/repo/ActionGate.kt"
REPO="$S/data/repo/Repo.kt"
SVM="$S/ui/common/ScreenViewModel.kt"
LV="$S/ui/login/LoginViewModel.kt"
DLS="$S/driver/DriverLocationService.kt"
NW="$S/notify/NotifyWorker.kt"
APP="$S/BusPostApp.kt"
NAV="$S/ui/driver/DriverSdkNavCard.kt"
LOC="$S/core/loc/Locator.kt"
HAND="$S/ui/driver/HandoverScreen.kt"
BG=app/build.gradle.kts
TD=app/src/test/java/com/shanxiang/buspost/core/net/DemoExceptionIsolationTest.kt
TC=app/src/test/java/com/shanxiang/buspost/core/net/ApiCancellationTest.kt
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'

# 先验文件存在：grep 找不到文件时输出为空，`[ "" = 0 ]` 会把"根本没跑"读成"真的 0 命中"
for f in "$API" "$ATT" "$AG" "$REPO" "$SVM" "$LV" "$DLS" "$NW" "$APP" "$NAV" "$LOC" "$HAND" "$BG" "$TD" "$TC"; do
    [ -f "$f" ] || { echo "× 判据文件缺失：$f（先确认工程根/副本建对，别把缺文件读成计数 0）"; exit 2; }
done

# TH-a 行为底盘：两个类的用例条数（删用例必须让这里变红）
TA=$(grep -cE '^[[:space:]]*@Test' "$TD")
TA2=$(grep -cE '^[[:space:]]*@Test' "$TC")

# TH-b 演示回落面闭合：每一处 DemoData.respond 都必须自带 `if (!demoAllowed) throw e` 那道闸
TB=$(grep -n 'DemoData\.respond(' "$API" | grep -vE "$NC" | wc -l | tr -d ' ')
TB2=$(grep -c 'if (!demoAllowed) throw e' "$API")

# TH-c §三十一 静态闸：正式代码不许 catch(Throwable) 吞程序错误。
# 白名单只有两处，且都不是"兜底"：Attempt.kt 是**收成 Result 交给调用方**（自己先穿透取消），
# ActionGate.kt 是**清理锁后原样 rethrow**。其余文件一律 0（含 Api.kt —— 它就是那个曾被洗成演示数据的地方）。
TC_ALL=$(grep -rn 'catch (e: Throwable)' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
TC_API=$(grep -n 'catch (e: Throwable)' "$API" | grep -vE "$NC" | wc -l | tr -d ' ')
TC_WL=$(grep -rn 'catch (e: Throwable)' "$ATT" "$AG" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')

# TH-d Repo 的取消穿透配对（整行字面量计数，教训②：数关键字出现次数会越数越松）
TD1=$(grep -cE '^ +\} catch \(e: Exception\) \{$' "$REPO")
TD2=$(grep -cE '^ +\} catch \(e: CancellationException\) \{$' "$REPO")

# TH-e 逐点配对，且**扫描面由内容决定**（不靠手写文件清单——那会把没列进去的文件变成永久盲区）。
# 判据只针对 §三十一 的原话："suspend 代码不能吞 CancellationException"。所以每个 `catch (e: Exception)`
# 先看它的 try 首段里有没有挂起点（Repo./Api./delay(/await(/first(/fetch(/block(/animateScrollToPage/…），
# 有挂起点就必须在同一条 catch 链里先有一个抛出取消的出口；没有挂起点的（唤起外部地图那种）单独点名打出来，
# 数量钉死为 1 —— 想扩大豁免面就得当场改这一行，改的人就不得不说为什么。
TE=$(python - "$S" <<'PY'
import os, re, sys
root = sys.argv[1]
SEP = chr(92)
SUSPEND = re.compile(r'(\bRepo\.[A-Za-z]|\bApi\.[A-Za-z]|\bdelay\(|\.await\(|\.first\(|AlertEngine\.check|calculateRoute|'
                     r'uploadPhoto|withContext\(|\.collect\(|\bfetch\(|\bblock\(|animateScrollToPage)')
CANC = re.compile(r'catch \(e: (?:kotlinx\.coroutines\.)?CancellationException\) \{')
EXC = re.compile(r'^\s*\}?\s*catch \(e: Exception\) \{')
FIRSTCL = re.compile(r'^\s*\}?\s*(?:catch|finally)\b')
total = shaped = viol = skipped = orphan = 0
for dp, _, fns in os.walk(root):
    for fn in sorted(fns):
        if not fn.endswith('.kt'):
            continue
        p = os.path.join(dp, fn).replace(SEP, '/')
        lines = open(p, encoding='utf-8').read().split('\n')
        for i, line in enumerate(lines):
            if not EXC.match(line):
                continue
            total += 1
            t = None
            for j in range(i - 1, max(-1, i - 201), -1):
                if 'try {' in lines[j]:
                    t = j
                    break
            if t is None:
                orphan += 1
                print("UNPAIRED %s:%d (no try found)" % (p.replace(root + '/', ''), i + 1))
                continue
            k = i
            for j in range(t + 1, i + 1):
                if FIRSTCL.match(lines[j]):
                    k = j
                    break
            if not SUSPEND.search('\n'.join(lines[t + 1:k])):
                skipped += 1
                print("SKIPPED %s:%d" % (p.replace(root + '/', ''), i + 1))
                continue
            shaped += 1
            c = None
            for j in range(t, i):
                if CANC.search(lines[j]):
                    c = j
                    break
            # HomeScreen 的轮播是**有条件**重抛（用户手指抢走滚动时要继续跑），也算穿透：
            # 判据要的是"取消不被收成失败"，不是"必须写成裸 throw e 那一行"。
            if not (c is not None and any('throw e' in x for x in lines[c + 1:i])):
                viol += 1
                print("UNPAIRED %s:%d" % (p.replace(root + '/', ''), i + 1))
print("TOTAL %d SHAPED %d SKIPPED %d ORPHAN %d VIOLATIONS %d" % (total, shaped, skipped, orphan, viol))
PY
)
TE_N=$(printf '%s\n' "$TE" | awk '/^TOTAL/ {print $10}')
TE_S=$(printf '%s\n' "$TE" | awk '/^TOTAL/ {print $4}')
TE_K=$(printf '%s\n' "$TE" | awk '/^TOTAL/ {print $6}')

# TH-f 通用出口 attempt：调用点数下限 + helper 自己先穿透取消
# ⚠ 下限 12→7 是 F-2 ⑧ 的**实测结果**不是放松判据，差额逐处归因（D-042）：迁移前 12 取自 F-2 ⑦ 收工产物
#   /tmp/vf_probe_check_throwable_probes.log（2026-09-30 23:30:30，同一条判据、同一个扫描面）；本轮迁到 safeApiCall
#   的 5 个 Repo 点**全部**原本是 `attempt { … }.getOrNull()`（12−2=10 那个推算是错的：以为只有两处未读数来自
#   attempt，实际 logout/天气/折线也在里面），非 Repo 的 7 处（Worker/定位/引擎/开屏预取/交接照片）一字未动
#   ⇒ 12 − 5 = 7。attempt 自己的定义与那 7 处一个没少；迁移前的 12 留在 D-034，不就地改历史读数。
# ⚠ 下限 7→11 是 F-2 ⑨ 的实测结果：把 4 个"取消可达作用域"里的 runCatching 迁到 attempt
#   （Repo.kt openMeteoWeather 的预报解析 / AmapNavigationEngine.calculateRoute 的算路受理 /
#    BusPostRoot 深链 navigate / DriverNavScreen TTS 设语种），逐点理由见 docs/android/RUNCATCHING_AUDIT.md。
TF=$(grep -rn 'attempt {' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
TF2=$(grep -c 'catch (e: CancellationException)' "$ATT")

# TH-g §三十一 静态闸：挂起代码里不许再有 runCatching 兜住可挂起的调用（逐个形状点名）
TG=0
for p in 'runCatching \{ Repo\.' 'runCatching \{ Api\.' 'runCatching \{ AlertEngine' \
         'runCatching \{ engine\.calculateRoute' 'runCatching \{ it\.await\(\)' \
         'runCatching \{ locations\(context\)\.first\(\)' 'runCatching \{ pager\.animateScroll'; do
    n=$(grep -rnE "$p" "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
    TG=$((TG + n))
done

# TH-h 客户端缺陷要有声音：类型化失败走文案，缺陷进日志且写明"不是网络问题"
TH=$(grep -c '客户端缺陷' "$LV")
TH2=$(grep -c '客户端缺陷' "$DLS")
TH3=$(grep -c 'android.util.Log.e(TAG' "$LV")

# TH-i work() 这一台"所有页面的漏斗"必须自己穿透取消（load() 早就有，work() 是本轮补的）
TI=$(grep -cE '^            \} catch \(e: kotlinx\.coroutines\.CancellationException\) \{$' "$SVM")

# TH-j 提醒轮询的两个调用点：取消穿出，不再被 runCatching 收成"这一轮跑过了"
TJ=$(grep -c 'attempt { AlertEngine.check' "$NW")
TJ2=$(grep -c 'attempt { AlertEngine.check' "$APP")
TJ3=$(grep -rn 'runCatching { AlertEngine.check' "$S" --include=*.kt | wc -l | tr -d ' ')

# TH-k 演示开关接线不能被反接：Api 读的是 NetTestOverride ?: BuildConfig，release 那格恒 false
TK=$(grep -c 'NetTestOverride.demoAllowed ?: BuildConfig.DEMO_ALLOWED' "$API")
TK2=$(grep -c 'buildConfigField("boolean", "DEMO_ALLOWED", "false")' "$BG")

# TH-l §六 的正反两面：出网那两个函数（execute / upload）只准按**类型**回落，
# 回落面必须恰好是「传输失败 ×2 + 业务失败 ×2」，且 Api.kt 里再没有一个兜底的 Exception/Throwable 出口。
# ⚠ 锚点带 8 空格缩进：Api.kt 还有第三对类型化出口属于 `safeApiCall`（顶格，无缩进），
#   F-2 ⑧ 之前它**一个调用点都没有**、现在有 6 个（Repo 5 个迁移点 + BusScreen 1 个消费点，计数归判据簿 SA-* 判红）
#   ——把两对混成一个数，就等于让"helper 的回落面"和"execute/upload 的演示回落面"互相顶包。
TL=$(grep -cE '^ {8}\} catch \(e: NetworkException\) \{$' "$API")
TL2=$(grep -cE '^ {8}\} catch \(e: ApiException\) \{$' "$API")
TL3=$(grep -nE 'catch \((e: )?(Exception|Throwable|RuntimeException)\)' "$API" | grep -vE "$NC" | wc -l | tr -d ' ')

# 只打数不判红（下一轮的输入，不是本轮的结论）
# ⚠ Z1 的口径在 F-2 ⑧ 换过一次：改名前 grep 'apiCall' 只会命中旧名（safeApiCall 里那个大写 A 不同形），
#   改名后它只会命中新名 ⇒ 新旧两个数**不可互引**，所以这里拆成两行分别打数：
#   Z1=新名 safeApiCall 的出现次数（判据簿 SA-* 才判红），Z1b=旧名 apiCall 独立单词的残留（应为 0）。
Z1=$(grep -rn 'safeApiCall' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
Z1b=$(grep -rnE '(^|[^a-zA-Z])apiCall' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
Z2=$(grep -rn 'runCatching' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
Z3=$(grep -rn 'catch (e: Throwable)' "$S" --include=*.kt | wc -l | tr -d ' ')

echo "TH-a 行为用例条数（隔离/取消）      = $TA/$TA2 （目标 ≥7/≥5）"
echo "TH-b DemoData 回落点/自带开关闸     = $TB/$TB2 （目标 4/4）"
echo "TH-c catch(Throwable) 全 main/Api.kt/白名单 = $TC_ALL/$TC_API/$TC_WL （目标 2/0/2）"
echo "TH-d Repo Exception/取消配对         = $TD1/$TD2 （目标 38/38）"
echo "TH-e 未配对的 Exception 出口         = $TE_N （目标 0）｜挂起形状/豁免 = $TE_S/$TE_K （目标 ≥48/3：豁免只有那两处 startActivity + 一处纯 JSON 解析）"
echo "TH-f attempt 调用点/helper 穿透      = $TF/$TF2 （目标 ≥7/1）"
echo "TH-g 挂起形状 runCatching 残留       = $TG （目标 0）"
echo "TH-h 缺陷话术（登录/上报/日志）      = $TH/$TH2/$TH3 （目标 ≥2/1/≥2）"
echo "TH-i work() 的取消出口（缩进整行）   = $TI （目标 1）"
echo "TH-j AlertEngine 两个 attempt/残留   = $TJ/$TJ2/$TJ3 （目标 1/1/0）"
echo "TH-k 演示开关接线/release 恒 false   = $TK/$TK2 （目标 1/1）"
echo "TH-l Api 回落面（传输/业务/兜底出口）= $TL/$TL2/$TL3 （目标 2/2/0）"
echo "［只打数］safeApiCall 出现(main)=$Z1 · 旧名 apiCall 残留=$Z1b · runCatching 全 main=$Z2 · catch(Throwable) 含注释=$Z3"
printf '%s\n' "$TE" | grep -aE '^(UNPAIRED|SKIPPED|ORPHAN)' | sed 's/^/   /'

fail=0
[ "$TA" -ge 7 ]  || { echo "× TH-a 演示隔离用例少于 7 条：程序缺陷会不会进 DemoData 没人钉"; fail=1; }
[ "$TA2" -ge 5 ] || { echo "× TH-a 取消用例少于 5 条：取消被收成失败这件事没人钉"; fail=1; }
[ "$TB" = 4 ]    || { echo "× TH-b DemoData 回落点不是 4 处：要么有人新加了一条兜底路径，要么开关闸被摘"; fail=1; }
[ "$TB2" = 4 ]   || { echo "× TH-b 回落点与 \`if (!demoAllowed) throw e\` 不再一一对应：正式包也能回落演示数据"; fail=1; }
[ "$TC_ALL" = 2 ] || { echo "× TH-c 正式代码里出现第 3 处 catch(Throwable)：程序错误会被静默（§三十一）"; fail=1; }
[ "$TC_API" = 0 ] || { echo "× TH-c Api.kt 又回到 catch(Throwable)：缺陷会被洗成演示数据"; fail=1; }
[ "$TC_WL" = 2 ]  || { echo "× TH-c 白名单两处（Attempt/ActionGate）形状变了，请连同本判据一起复核"; fail=1; }
[ "$TD1" = "$TD2" ] || { echo "× TH-d Repo 的 Exception 出口与取消出口不再配对（$TD1/$TD2）：有人新增了吞取消的 catch"; fail=1; }
[ "$TD1" -ge 38 ] || { echo "× TH-d Repo 的取消穿透少于 38 处：读接口的取消会被写成一次「加载失败」"; fail=1; }
[ "$TE_N" = 0 ]  || { echo "× TH-e 有 $TE_N 处挂起代码的 Exception 出口前面没有取消出口：CancellationException 被收成了普通失败"; fail=1; }
[ "$TE_S" -ge 48 ] || { echo "× TH-e 扫到的挂起形状 Exception 出口少于 48 处：判据的扫描面缩水了（删了 catch 还是改了扫描规则？）"; fail=1; }
# 基数 1 → 2 的增量 = 用户端"步行导航"那一格（`git log` 追到 7d8b819 全面视觉升级那轮，本轮 2026-10-12 复核补登记）。
#   两处豁免的形状**完全相同**：try 段里只有一个 `ctx.startActivity(Intent(ACTION_VIEW, …))`，
#   一个挂起点都没有 ⇒ §三十一 那条"suspend 不许吞取消"在这里没有适用对象，
#   所以它不是"把吞异常合法化"，而是"这一格根本不在扫描语义里"：
#     · `ui/driver/DriverSdkNavCard.kt:468` 唤起外部地图（ActivityNotFound 先单独接住）
#     · `ui/user/BusScreen.kt:838` 步行导航唤起高德，失败退网页版
#   豁免面继续**逐处点名**（数量钉死恰好 2）：再加第三处就得当场改这一行并说清为什么。
# 基数 2 → 3 的增量 = `core/update/AppUpdate.kt` 的 JSON 解析那一处：
#   `parseRelease` 是**普通函数**（不是 suspend、块内无任何挂起点），catch 完直接 `return null` 交给
#   `decideUpdate` 落成"没查到"，所以 §三十一"suspend 不许吞取消"在这里同样没有适用对象。
#   仍然逐处点名、数量钉死恰好 3：再加第四处就得当场改这一行并说明为什么。
[ "$TE_K" = 3 ]  || { echo "× TH-e 免检（无挂起点）的 Exception 出口不是 3 处：豁免面变大了，请连同 D-034 一起复核"; fail=1; }
[ "$TF" -ge 11 ] || { echo "× TH-f attempt 调用点少于 11：有人把取消可达作用域里的挂起形状改回了 runCatching（F-2 ⑨ 迁移后实测基线=11，7→11 的 4 处逐点归因见本簿上方注释与 D-043）"; fail=1; }
[ "$TF2" = 1 ]   || { echo "× TH-f attempt 自己不再先穿透取消：它退化成另一个 runCatching"; fail=1; }
[ "$TG" = 0 ]    || { echo "× TH-g 挂起代码里残留 $TG 处 runCatching：取消会被读成一次普通失败"; fail=1; }
[ "$TH" -ge 2 ]  || { echo "× TH-h 登录页不再区分「网络失败/客户端缺陷」：缺陷被洗成一句普通提示"; fail=1; }
[ "$TH2" = 1 ]   || { echo "× TH-h 位置上报不再把缺陷与弱网分开：对着 bug 退避重试"; fail=1; }
[ "$TH3" -ge 2 ] || { echo "× TH-h 缺陷分支不进日志：堆栈丢了，缺陷再也复现不了"; fail=1; }
[ "$TI" = 1 ]    || { echo "× TH-i work() 的取消出口没了：所有页面的加载漏斗都会吞取消"; fail=1; }
[ "$TJ" = 1 ]    || { echo "× TH-j Worker 的 doWork 又吞取消：被停止的一轮会谎报「这轮跑过了」"; fail=1; }
[ "$TJ2" = 1 ]   || { echo "× TH-j 前台 20 秒轮询又吞取消"; fail=1; }
[ "$TJ3" = 0 ]   || { echo "× TH-j AlertEngine.check 回到 runCatching"; fail=1; }
[ "$TK" = 1 ]    || { echo "× TH-k 演示开关的读取点被改写（测试缝与构建开关的接线断了）"; fail=1; }
[ "$TK2" = 1 ]   || { echo "× TH-k release 的 DEMO_ALLOWED 不再是硬编码 false：正式包可能带演示回落"; fail=1; }
[ "$TL" = 2 ]    || { echo "× TH-l Api 的传输失败回落面不是 2 处（execute/upload 各一）：回落形状被改了"; fail=1; }
[ "$TL2" = 2 ]   || { echo "× TH-l Api 的业务失败回落面不是 2 处（execute/upload 各一）：回落形状被改了"; fail=1; }
[ "$TL3" = 0 ]   || { echo "× TH-l Api 又长出兜底的 Exception/Throwable 出口：程序缺陷会被洗成演示数据（§六）"; fail=1; }

if [ "$fail" = 0 ]; then echo "异常可信化判据通过（§六 缺陷不进演示数据 / §七 取消不被吞）"; else echo "异常可信化判据失败"; fi
exit "$fail"
