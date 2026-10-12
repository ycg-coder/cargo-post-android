#!/usr/bin/env bash
# F-2 第 8 步：`safeApiCall()` 落点与 5 个迁移点的判据（SA-a…SA-f）；
# F-3 第 5 步在同一条判据簿上续加 SA-e2/SA-e3/SA-e4（折线三态的单一真相、装载侧、行为簿）。
# 用法：bash tools/check_safe_call_probes.sh     退出码非 0 = 这一层的契约破了
# 每条 0 命中都有反证，每条都有负控制：bash tools/safe_call_negative_controls.sh
#
# 本本钉的是**结构与形状**，行为由 app/src/test/.../core/net/SafeApiCallContractTest.kt 钉（SA-f 数它）。
# 为什么两层都要：工程无 Robolectric，`BusScreen` 那两处文案在 JVM 里渲染不出来；
# 而"helper 还在、但没人调用"这件事（F-2 ⑧ 之前的真实状态：生产调用点 0）只有静态判据看得见。
#
# 写法沿用既有教训：先验文件存在；按内容整行字面量而非行号；注释行排除（$NC）；
# "0 命中"必须给同机制的反证；子串陷阱（`apiCall` ⊂ `safeApiCall`）单独钉一条。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

S=app/src/main/java/com/shanxiang/buspost
API="$S/core/net/Api.kt"
REPO="$S/data/repo/Repo.kt"
BUS="$S/ui/user/BusScreen.kt"
PDEC="$S/core/map/PolylineDecision.kt"
TST="app/src/test/java/com/shanxiang/buspost/core/net/SafeApiCallContractTest.kt"
PTEST="app/src/test/java/com/shanxiang/buspost/core/map/PolylineDecisionContractTest.kt"
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'

for f in "$API" "$REPO" "$BUS" "$PDEC" "$TST" "$PTEST"; do
    [ -f "$f" ] || { echo "× 判据文件缺失：$f（先确认工程根/副本建对，别把缺文件读成计数 0）"; exit 2; }
done

# GUARD：计数管道若失去计数能力，"0 命中"与"真的没有"长得一模一样 ⇒ 先自证 grep 还数得动
G=$(printf 'aa bb\n' | grep -c 'bb')
[ "$G" = 1 ] || { echo "× GUARD 计数管道不工作（实测 $G），本本所有 0 一律不采信"; exit 3; }
echo "GUARD 计数管道             = $G （目标 1）"

# ---------- SA-a helper 本体与旧名 ----------
DEF=$(grep -cF 'suspend inline fun <T> safeApiCall(block: () -> T): ApiResult<T> = try {' "$API")
# 旧名必须**独立单词**：`apiCall` 是 `safeApiCall` 的子串？不是（大小写不同形），但写成子串模式
# 会把 `xxapiCall` 这类一并算进来，而且将来若有人写 `val apiCall = ::safeApiCall` 就分不清了。
LEGACY=$(grep -rnE '(^|[^a-zA-Z])apiCall' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
# 0 命中的反证：同一套管道在新名上必须数得出东西，否则那个 0 是 grep 坏了
LEGACY_PROOF=$(grep -rnE '(^|[^a-zA-Z])safeApiCall' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')

# ---------- SA-b helper 体内：取消穿透 / 四类 / 没有兜底 ----------
# 取函数体：从定义行到第一个空行（safeApiCall 是表达式函数，整段没有空行；空行是它与下一条 KDoc 的分界）
BODY=$(awk '/^suspend inline fun <T> safeApiCall\(/,/^$/' "$API")
[ -n "$BODY" ] || { echo "× SA-b 取不到 safeApiCall 函数体（定义形状变了，下面的判据全部无法归因）"; exit 4; }
B_CANC=$(printf '%s\n' "$BODY" | grep -c 'catch (e: CancellationException) {')
B_THROW=$(printf '%s\n' "$BODY" | grep -c '^    throw e$')
B_TYPED=$(printf '%s\n' "$BODY" | grep -cE 'catch \(e: (UnauthorizedException|ApiException|NetworkException|IOException)\) \{')
B_SWEEP=$(printf '%s\n' "$BODY" | grep -cE 'catch \((e: )?(Exception|Throwable|RuntimeException)\)')
# 0 命中的反证：同一兜底模式在 Repo.kt 数得出 38 处（F-1 那轮的配对面），说明模式本身是活的
B_SWEEP_PROOF=$(grep -nE 'catch \((e: )?(Exception|Throwable|RuntimeException)\)' "$REPO" | grep -vE "$NC" | wc -l | tr -d ' ')

# ---------- SA-c 五个迁移点各自在位 ----------
USE=$(grep -rn 'safeApiCall {' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
C_LOGOUT=$(grep -cF 'safeApiCall { Api.post("$M/auth/logout") }' "$REPO")
C_WEATHER=$(grep -cF 'val forecast = safeApiCall {' "$REPO")
C_POLY=$(grep -cF 'safeApiCall { Api.get("$T/bus/line-polyline"' "$REPO")
C_UNREAD=$(grep -cF 'suspend fun unreadCount(): Long? = safeApiCall {' "$REPO")
C_DUNREAD=$(grep -cF 'return safeApiCall {' "$REPO")
C_VM=$(grep -cF 'safeApiCall { Repo.linePolyline(id) }' "$BUS")

# ---------- SA-d "失败洗成无害值"的旧形状 ----------
D_EMPTY=$(grep -rn '?: return emptyList()' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
D_EMPTY_PROOF=$(grep -c 'emptyList()' "$REPO")
# Repo 的 attempt 调用点：⑧ 归零 ⇒ F-2 ⑨ 新增 1 处 ⇒ 这里从"=0"收成"恰好 1 处 + 整行点名"。
# 反证用全 main 的 attempt 数：那 7 处（Worker/定位/引擎/开屏预取…）一个都不该被顺手删掉。
# ⚠ 这个 7 是**实测且已归因**的，不是顺手把下限调绿（12→7 的差额每一处都有出处，见 D-042）：
#   迁移前 12 = F-2 ⑦ 收工产物 /tmp/vf_probe_check_throwable_probes.log（2026-09-30 23:30:30，同一条判据同一个扫描面）；
#   本轮迁走的 5 个点**全部**原本是 `attempt { … }.getOrNull()`（不是只迁 2 处，那个 12−2=10 的推算是错的），
#   非 Repo 的 7 处本轮一字未动 ⇒ 12 − 5 = 7，与实测吻合，没有未归因的缺失。
# 为什么 ⑨ 这 1 处不算破坏 ⑧ 的契约（归因见 D-043）：⑧ 要钉的是"网络读只走 safeApiCall，四类失败分类不许丢"，
#   而 ⑨ 迁的是**取消语义**——落在 `openMeteoWeather` 里的 daily 预报解析段，块体不碰网络
#   （数据是上面 `Api.rawGet` 已经取回的 `text`），它要的出口只有"取消穿出 / 解析失败回空预报"两种，
#   没有分类可丢；用 attempt 而非内联 try/catch 是为了和全工程其余 10 点同形（TH-f 的下限钉的就是这个数）。
# ⚠ 这不是把 0 放宽成"随便几处"：多一处 attempt 进 Repo（网络读退回旧 helper、分类丢失）会红，
#   那一处被改名或被删掉也会红——safe_call_negative_controls.sh 第 7 条与第 7b 条各打破一次。
# 全 main 的下限同轮从 7 抬到 11：11 = ⑧ 的 7 处 + ⑨ 迁进取消可达作用域的 4 处
#   （Repo 预报解析 / 算路受理 / 深链跳转 / TTS 设语种，逐点归因见 docs/android/RUNCATCHING_AUDIT.md 第二节）。
#   这是**实测抬高**，不是把下限放松：抬完之后"顺手删掉一处"照样红（负控第 10 条）。
D_ATTEMPT_REPO=$(grep -rn 'attempt {' "$REPO" | grep -vE "$NC" | wc -l | tr -d ' ')
D_ATTEMPT_FORECAST=$(grep -cF 'val forecast = attempt {' "$REPO")
D_ATTEMPT_MAIN=$(grep -rn 'attempt {' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
# 基数 1 → 2 的增量 = D-076 #46 的续期那一处（`Repo.kt:140`）。形状与 ⑨ 禁的那种不同：
#   它落到 `?: return false`（续期失败就说失败，调用方照原路把 401 交给上层清盘），
#   不是 ⑨ 点名的"失败洗成空列表/空数据"那一类；`attempt` 本身 rethrow CancellationException
#   （`core/net/Attempt.kt:20-21`），所以取消也不会被吞。
#   豁免面照旧**按整行字面量点名**（D_ATTEMPT_REFRESH），只数个数不算数。
D_ATTEMPT_REFRESH=$(grep -cF 'Api.post("$M/auth/refresh-token"' "$REPO")

D_THROW=$(grep -rn 'getOrThrow()' "$REPO" | grep -vE "$NC" | wc -l | tr -d ' ')
D_SIG=$(grep -cF 'suspend fun linePolyline(routeId: String): List<Geo> {' "$REPO")

# ---------- SA-e 页面把"没问到"与"后端回空"分开说 ----------
E_DECL=$(grep -cF 'var polylineUnconfirmed by mutableStateOf<String?>(null)' "$BUS")
E_RESET=$(grep -cF 'polylineUnconfirmed = null' "$BUS")
E_CLASS=$(grep -cF 'polylineUnconfirmed = when (result) {' "$BUS")
E_REASON=$(grep -cF 'vm.polylineReason(' "$BUS")
E_SUB=$(grep -cF 'subtitle = vm.polylineSubtitle(),' "$BUS")
# 旧形状：把会抛的读法直接赋给 polyline（没有分类），以及硬编码那句"后端没有轨迹"
E_OLD_CALL=$(grep -cF 'polyline = Repo.linePolyline(id)' "$BUS")
E_OLD_SUB=$(grep -cF 'subtitle = if (vm.polyline.size >= 2)' "$BUS")
E_OLD_REASON=$(grep -cF 'reason = if (vm.polyline.size >= 2) null else "' "$BUS")
E_NEW_REASON=$(grep -cF 'reason = if (vm.polyline.size >= 2) null else vm.polylineReason(' "$BUS")

# ---------- SA-e2 折线三态是**单一真相**（F-3 ⑤）----------
# 为什么这一族不并进 SA-e：SA-e 管的是"F-2 ⑧ 那两句文案还在不在原位"，SA-e2 管的是"三格的**优先顺序**有几份"。
# 顺序这种东西天生只能钉一次：把它抄进 @Composable / ViewModel 两遍，就是下一轮"两处答案不一致"的来处
# （§八 ⑤"第二份真相"那一族）。所以判据的形状是：新文件里恰好一份 + 页面里恰好 0 份 + 全树恰好 1。
P_PHASE=$(grep -cF 'internal enum class PolylinePhase' "$PDEC")
# 三格各一条：删掉 Asking（把"在飞"退回两态）或改名都会红
P_CELLS=$(grep -cE '^    (NotAsked|Asking|Asked),$' "$PDEC")
P_FUN_SUB=$(grep -cF 'internal fun polylineSubtitleOf(phase: PolylinePhase, pointCount: Int, failure: String?): String = when {' "$PDEC")
P_FUN_REASON=$(grep -cF 'internal fun polylineReasonOf(phase: PolylinePhase, failure: String?, whenEmpty: String): String = when {' "$PDEC")
P_FUN_ASK=$(grep -cF 'internal fun shouldAskPolyline(selectedRouteId: String?, silent: Boolean): Boolean =' "$PDEC")
# ⚠ 签名在位不等于规则在位：把函数体里的 `!silent` 摘掉，"按需查询"就变成 15s 轮询风暴，而上面那条整行锚点一字未动。
#   这一条是本轮写负控时才想到的（负控第 26 条打的就是它）——只钉签名的判据会被"改函数体"这种破口放过。
P_ASK_BODY=$(grep -cF '!silent && !selectedRouteId.isNullOrEmpty()' "$PDEC")
# 那句假话的**唯一出口**：全树只许有一份，而且必须数得到它在决策文件里（否则"1"可能只是页面里那份）
P_EMPTY_TEXT_ALL=$(grep -rn '"后端未回传该线路折线"' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
P_EMPTY_TEXT_PDEC=$(grep -nF '"后端未回传该线路折线"' "$PDEC" | grep -vE "$NC" | wc -l | tr -d ' ')
# 页面退回自带文案（第二份真相长出来）的旧形状：两处 delegate 各恰好一条，旧那条内联写法归零
P_DELEG_REASON=$(grep -cF 'fun polylineReason(whenEmpty: String): String = polylineReasonOf(polylinePhase, polylineUnconfirmed, whenEmpty)' "$BUS")
P_DELEG_SUB=$(grep -cF 'fun polylineSubtitle(): String = polylineSubtitleOf(polylinePhase, polyline.size, polylineUnconfirmed)' "$BUS")
P_OLD_LET=$(grep -rn '?.let { "这次没问到该线路的折线（' "$S" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
P_OLD_LET_PDEC=$(grep -nF '"这次没问到该线路的折线（$failure）' "$PDEC" | grep -vE "$NC" | wc -l | tr -d ' ')

# ---------- SA-e3 装载侧：首屏要问、迟到不得覆盖、取消不算问过 ----------
# 首屏自动选中却不去问折线，就是 D-042 在册那条假话的**根因**（问都不问，文案再诚实也只能说"没问过"）。
E3_AUTOASK=$(grep -cF 'if (shouldAskPolyline(selected?.id, silent)) loadPolyline()' "$BUS")
E3_SEQ_DECL=$(grep -cF 'private var polylineSeq = 0' "$BUS")
E3_SEQ_BUMP=$(grep -cF 'val mySeq = ++polylineSeq' "$BUS")
# 整行钉：序号与编号**两条都在**才算守住（小程序 index.js:339 同法）。只留一条的两种破口各有其害：
#   只留编号 → A→B→A 快切时旧的**失败**会盖掉新的成功；只留序号 → 取消选中后迟到的几何会画在空画面上。
E3_GUARD=$(grep -cF 'if (mySeq != polylineSeq || selected?.id != id) return@work' "$BUS")
E3_ASKING=$(grep -cF 'polylinePhase = PolylinePhase.Asking' "$BUS")
E3_ASKED=$(grep -cF 'polylinePhase = PolylinePhase.Asked' "$BUS")
# 复位点 3 处：取消选中、换线、请求被取消（第三处见下条点名）
E3_NOTASKED=$(grep -cF 'polylinePhase = PolylinePhase.NotAsked' "$BUS")
E3_CANCEL=$(grep -cF 'if (mySeq == polylineSeq) polylinePhase = PolylinePhase.NotAsked' "$BUS")
E3_CATCH=$(grep -cF 'catch (e: kotlinx.coroutines.CancellationException) {' "$BUS")

# ---------- SA-e4 三态的行为簿（工程无 Robolectric，只能钉纯函数那一半）----------
E4_TESTS=$(grep -cE '^[[:space:]]*@Test' "$PTEST")
# 三格各被**整句**断言过一次：只测 `whenEmpty` 那一格的行为簿等于没测本轮的修复
E4_CELLS=$(grep -cE '"(还没去问这条线路的折线|正在向后端要这条线路的折线…|后端未回传该线路折线)"' "$PTEST")
# 穷举 12 种组合后"后端未回传"只出现 1 次——这条才是"唯一出口"的行为侧证据（结构侧是上面 P_EMPTY_TEXT_ALL）
E4_EXIT=$(grep -cF 'assertEquals(1, hits)' "$PTEST")

# ---------- SA-f 契约用例簿 ----------
F_TESTS=$(grep -cE '^[[:space:]]*@Test' "$TST")
F_CANC=$(grep -c 'is CancellationException' "$TST")
F_TYPES=$(grep -cE 'is ApiResult\.(BusinessError|NetworkError|Unauthorized)' "$TST")
F_NOREQ=$(grep -c '0, server.requestCount' "$TST")

echo "SA-a helper 定义 / 旧名 apiCall 残留 / 反证 = $DEF / $LEGACY / $LEGACY_PROOF （目标 1 / 0 / >0）"
echo "SA-b 体内取消出口 / throw e / 四类 / 兜底 = $B_CANC/$B_THROW/$B_TYPED/$B_SWEEP （目标 1/1/4/0）"
echo "SA-b 兜底模式在 Repo 的反证                = $B_SWEEP_PROOF （目标 >0，否则上面那个 0 不可信）"
echo "SA-c 使用点合计                            = $USE （目标 6：Repo 5 + BusScreen 1）"
echo "SA-c 五个迁移点点名                        = logout/weather/linePolyline/unread/driverUnread = $C_LOGOUT/$C_WEATHER/$C_POLY/$C_UNREAD/$C_DUNREAD （目标各 1）"
echo "SA-c 页面消费点                            = $C_VM （目标 1）"
echo "SA-d 伪装形状 ?: return emptyList()        = $D_EMPTY （目标 0）/ 反证 emptyList() 在 Repo = $D_EMPTY_PROOF"
echo "SA-d Repo 的 attempt 残留 / 预报点名 / 续期点名 / 全 main = $D_ATTEMPT_REPO/$D_ATTEMPT_FORECAST/$D_ATTEMPT_REFRESH/$D_ATTEMPT_MAIN （目标 2/1/1/≥11）"
echo "SA-d linePolyline 出口 getOrThrow / 签名    = $D_THROW/$D_SIG （目标 1/1）"
echo "SA-e 页面状态：声明/复位/分类/文案        = $E_DECL/$E_RESET/$E_CLASS/$E_REASON/$E_SUB （目标 1/2/1/2/1）"
echo "SA-e 旧形状三处（必须归零）                = $E_OLD_CALL/$E_OLD_SUB/$E_OLD_REASON （目标 0/0/0）"
echo "SA-e 新形状 reason 走 polylineReason        = $E_NEW_REASON （目标 1，反证上面那个 0）"
echo "SA-e2 决策层：枚举/三格/三个纯函数/问的体  = $P_PHASE/$P_CELLS/$P_FUN_SUB/$P_FUN_REASON/$P_FUN_ASK/$P_ASK_BODY （目标 1/3/1/1/1/1）"
echo "SA-e2 「后端未回传」全树份数 / 在决策文件  = $P_EMPTY_TEXT_ALL/$P_EMPTY_TEXT_PDEC （目标 1/1）"
echo "SA-e2 页面只接线（两处 delegate / 旧内联） = $P_DELEG_REASON/$P_DELEG_SUB/$P_OLD_LET （目标 1/1/0）/ 反证内联在决策文件 = $P_OLD_LET_PDEC （目标 1）"
echo "SA-e3 首屏问 / 序号声明 / 自增 / 双条件闸  = $E3_AUTOASK/$E3_SEQ_DECL/$E3_SEQ_BUMP/$E3_GUARD （目标 1/1/1/1）"
echo "SA-e3 相位写入 Asking/Asked/NotAsked/取消  = $E3_ASKING/$E3_ASKED/$E3_NOTASKED/$E3_CANCEL/$E3_CATCH （目标 1/1/3/1/1）"
echo "SA-e4 三态行为簿：条数 / 三格整句 / 唯一出口 = $E4_TESTS/$E4_CELLS/$E4_EXIT （目标 ≥15/≥3/1）"
echo "SA-f 用例条数 / 取消 / 四类 / 不出网断言    = $F_TESTS/$F_CANC/$F_TYPES/$F_NOREQ （目标 12/≥2/≥3/≥3）"

fail=0
[ "$DEF" = 1 ] || { echo "× SA-a helper 定义不是那一条（$DEF）：签名被改或第二份 helper 长出来了"; fail=1; }
[ "$LEGACY" = 0 ] || { echo "× SA-a 旧名 apiCall 又出现在生效代码里（$LEGACY 处）：改名没收干净，两套名字会各走一半"; fail=1; }
[ "$LEGACY_PROOF" -gt 0 ] || { echo "× SA-a 反证失败：同一模式连新名都数不出来，那个 0 不可信"; fail=1; }
[ "$B_CANC" = 1 ] || { echo "× SA-b helper 体内没有唯一的取消出口（$B_CANC）：§七 禁止的那条链路会在这里复活"; fail=1; }
[ "$B_THROW" = 1 ] || { echo "× SA-b 取消子句不再紧跟 \`throw e\`（$B_THROW）：穿透被改成了一次普通失败"; fail=1; }
[ "$B_TYPED" = 4 ] || { echo "× SA-b 分类子句不是 4 条（$B_TYPED）：401/业务/传输少一类就会互相顶包"; fail=1; }
[ "$B_SWEEP" = 0 ] || { echo "× SA-b 体内长出兜底 catch（$B_SWEEP）：程序错误会被发一张业务失败的通行证（§六）"; fail=1; }
[ "$B_SWEEP_PROOF" -gt 0 ] || { echo "× SA-b 反证失败：兜底模式在 Repo 也数不出东西，那个 0 是判据坏了"; fail=1; }
[ "$USE" = 6 ] || { echo "× SA-c 使用点不是 6（实测 $USE）：指令要求的是「只迁 5 个高风险点 + 页面那 1 个消费点」，多迁=扩大战线，少迁=没收口"; fail=1; }
[ "$C_LOGOUT" = 1 ] || { echo "× SA-c 迁移点①退出登录不见了：会话撤销失败又回到静默分支"; fail=1; }
[ "$C_WEATHER" = 1 ] || { echo "× SA-c 迁移点②天气段不见了（这一处只是形状归一，但形状也是判据）"; fail=1; }
[ "$C_POLY" = 1 ] || { echo "× SA-c 迁移点③线路折线不见了：真实修复点被还原，失败会重新洗成空列表"; fail=1; }
[ "$C_UNREAD" = 1 ] || { echo "× SA-c 迁移点④用户未读数不见了"; fail=1; }
[ "$C_DUNREAD" = 1 ] || { echo "× SA-c 迁移点⑤司机未读数不见了"; fail=1; }
[ "$C_VM" = 1 ] || { echo "× SA-c 页面的折线消费点不再走 helper：抛出的失败会直冲 work()，页面从此说不出「这次没问到」"; fail=1; }
[ "$D_EMPTY" = 0 ] || { echo "× SA-d 长出 $D_EMPTY 处 \`?: return emptyList()\`：失败被洗成「没有内容」，空态会说假话"; fail=1; }
[ "$D_EMPTY_PROOF" -gt 0 ] || { echo "× SA-d 反证失败：emptyList() 在 Repo 都数不出来，上面那个 0 不可信"; fail=1; }
[ "$D_ATTEMPT_REPO" = 2 ] || { echo "× SA-d Repo 的 attempt 是 $D_ATTEMPT_REPO 处（D-076 之后实测 2 = 预报解析 + 续期）：多出来的是网络读退回旧 helper（分类丢失），少掉的是那两处点名被换形"; fail=1; }
[ "$D_ATTEMPT_REFRESH" = 1 ] || { echo "× SA-d 续期那处 attempt 的字面量不见了（=$D_ATTEMPT_REFRESH≠1）：第二格豁免要么被扩大，要么已被换形"; fail=1; }
[ "$D_ATTEMPT_FORECAST" = 1 ] || { echo "× SA-d Repo 唯一的 attempt 不是 ⑨ 点名的预报解析段（整行字面量不见了）：豁免面必须点名，否则'有 1 处'会被用来藏任意 1 处"; fail=1; }
[ "$D_ATTEMPT_MAIN" -ge 11 ] || { echo "× SA-d 全 main 的 attempt 只剩 $D_ATTEMPT_MAIN（下限 11 = F-2 ⑧ 的 7 处 + ⑨ 迁入的 4 处，逐点归因见 docs/android/RUNCATCHING_AUDIT.md 第二节）：Worker/定位/引擎那几处被顺手删了"; fail=1; }
[ "$D_THROW" = 1 ] || { echo "× SA-d linePolyline 的出口不是 getOrThrow（Repo 内 $D_THROW 处）：失败与后端回空又混成一个值"; fail=1; }
[ "$D_SIG" = 1 ] || { echo "× SA-d linePolyline 的签名被改成「不抛」的形状（$D_SIG）：这一处修复的前提没了"; fail=1; }
[ "$E_DECL" = 1 ] || { echo "× SA-e 页面没有独立的折线状态字段：失败又要去挤共享的 vm.error，别的接口失败会被说成「这条线路没轨迹」"; fail=1; }
[ "$E_RESET" = 2 ] || { echo "× SA-e 折线状态复位点数不对（$E_RESET，目标 2：取消选中与换线各一次）：上一次的失败会挂在下一条线路上"; fail=1; }
[ "$E_CLASS" = 1 ] || { echo "× SA-e 不再按分类记原因（$E_CLASS）：四类失败又塌成一句没有信息量的文案"; fail=1; }
[ "$E_REASON" = 2 ] || { echo "× SA-e 走 polylineReason 的文案位点不是 2（$E_REASON）：两处有一处退回硬编码「后端没有轨迹」"; fail=1; }
[ "$E_SUB" = 1 ] || { echo "× SA-e 副标题退回写死的那句（$E_SUB）：没问过的线路也会被说成「后端未回传」"; fail=1; }
[ "$E_OLD_CALL" = 0 ] || { echo "× SA-e 页面把会抛的读法直接赋给 polyline（旧形状复活）：失败会冒到 work()，共享 error 被写成折线的病因"; fail=1; }
[ "$E_OLD_SUB" = 0 ] || { echo "× SA-e 副标题又只看折线点数分支：只数得出「有没有」，数不出「问没问过」"; fail=1; }
[ "$E_OLD_REASON" = 0 ] || { echo "× SA-e 出现了硬编码 reason 字符串（$E_OLD_REASON）：请求失败会被念成「后端没有这条线路的轨迹」——本轮修的就是这句假话"; fail=1; }
[ "$E_NEW_REASON" = 1 ] || { echo "× SA-e 反证失败：新形状那句不在了，上面那两个 0 是文案整块被删"; fail=1; }
[ "$P_PHASE" = 1 ] || { echo "× SA-e2 相位枚举不是那一条（$P_PHASE）：三态长出了第二份定义"; fail=1; }
[ "$P_CELLS" = 3 ] || { echo "× SA-e2 相位不是三格（$P_CELLS）：少一格就回到「没问」与「后端回空」共用一支的老形状"; fail=1; }
[ "$P_FUN_SUB" = 1 ] || { echo "× SA-e2 副标题的三态顺序没有唯一出处（$P_FUN_SUB）：顺序一旦被抄进第二处，两处会给出两个答案"; fail=1; }
[ "$P_FUN_REASON" = 1 ] || { echo "× SA-e2 缺失说明的三态顺序没有唯一出处（$P_FUN_REASON）：同上"; fail=1; }
[ "$P_FUN_ASK" = 1 ] || { echo "× SA-e2 「该不该出网」的判断没有出处（$P_FUN_ASK）：静默轮询与空编号两条禁令退回调用点各自决定"; fail=1; }
[ "$P_ASK_BODY" = 1 ] || { echo "× SA-e2 「该不该出网」的函数体被换掉（$P_ASK_BODY）：签名还在，但 15s 静默轮询也会去查折线，「按需查询」名存实亡"; fail=1; }
[ "$P_EMPTY_TEXT_ALL" = 1 ] || { echo "× SA-e2 那句「后端未回传该线路折线」在全树有 $P_EMPTY_TEXT_ALL 份：0 份=诚实文案整块被删，≥2 份=顺序有了第二份真相"; fail=1; }
[ "$P_EMPTY_TEXT_PDEC" = 1 ] || { echo "× SA-e2 反证失败：那一份不在决策文件里，上面那个 1 数到的是别处（页面内联或注释）"; fail=1; }
[ "$P_DELEG_REASON" = 1 ] || { echo "× SA-e2 页面的 reason 不再单纯接线（$P_DELEG_REASON）：文案顺序回到 ViewModel 里就没人钉得住它"; fail=1; }
[ "$P_DELEG_SUB" = 1 ] || { echo "× SA-e2 页面的副标题不再单纯接线（$P_DELEG_SUB）：同上"; fail=1; }
[ "$P_OLD_LET" = 0 ] || { echo "× SA-e2 旧的失败文案内联写法又出现（$P_OLD_LET 处）：三格与失败原因重新各说各的"; fail=1; }
[ "$P_OLD_LET_PDEC" = 1 ] || { echo "× SA-e2 反证失败：内联那句在决策文件里也数不出来，上面那个 0 是判据坏了"; fail=1; }
[ "$E3_AUTOASK" = 1 ] || { echo "× SA-e3 首屏自动选中之后没有去问折线：本轮收口的那句假话（没问却说「后端未回传」）就是从这里来的"; fail=1; }
[ "$E3_SEQ_DECL" = 1 ] || { echo "× SA-e3 折线请求序号字段不在了（$E3_SEQ_DECL）：迟到的响应失去可比较的对象"; fail=1; }
[ "$E3_SEQ_BUMP" = 1 ] || { echo "× SA-e3 发请求前不再推进序号（$E3_SEQ_BUMP）：下面的双条件闸变成恒真的摆设"; fail=1; }
[ "$E3_GUARD" = 1 ] || { echo "× SA-e3 写回前的「序号 + 当前编号」双条件闸不是那一条（$E3_GUARD）：少任何一条都有真实害处（A→B→A 快切时旧失败盖掉新成功 / 取消后迟到几何画进空画面）——把一条属于**另一条线路**的真实折线画在当前线路上是假路线红线"; fail=1; }
[ "$E3_ASKING" = 1 ] || { echo "× SA-e3 请求在飞时不再置 Asking（$E3_ASKING）：在飞与「还没去问」又长得一样"; fail=1; }
[ "$E3_ASKED" = 1 ] || { echo "× SA-e3 成功写回后不再置 Asked（$E3_ASKED）：「后端未回传」那一格永远出不来，等于把三态收成两态"; fail=1; }
[ "$E3_NOTASKED" = 3 ] || { echo "× SA-e3 相位复位点不是 3（$E3_NOTASKED，目标：取消选中 / 换线 / 请求被取消）：少一处就会把上一条线路的「问过」挂到下一条上"; fail=1; }
[ "$E3_CANCEL" = 1 ] || { echo "× SA-e3 取消不再把相位退回 NotAsked（$E3_CANCEL）：换页回来屏幕会一直挂着「正在向后端要折线…」，而请求早就没了"; fail=1; }
[ "$E3_CATCH" = 1 ] || { echo "× SA-e3 装载处不再显式分 cancel（$E3_CATCH）：取消被 work() 之外的人当成一次普通失败"; fail=1; }
[ "$E4_TESTS" -ge 15 ] || { echo "× SA-e4 三态行为簿只剩 $E4_TESTS 条（基线 15，F-3 ⑤ 实测）：删用例必须让判据变红"; fail=1; }
[ "$E4_CELLS" -ge 3 ] || { echo "× SA-e4 三格里有没被整句断言的（$E4_CELLS）：只测「后端回空」那一格的行为簿等于没测本轮修复"; fail=1; }
[ "$E4_EXIT" = 1 ] || { echo "× SA-e4 那条「穷举 12 组合、后端未回传只许 1 个出口」的断言不在了（$E4_EXIT）：默认分支写宽是最隐蔽的一种回退"; fail=1; }
[ "$F_TESTS" = 12 ] || { echo "× SA-f 契约用例不是 12 条（$F_TESTS）：删用例必须让判据变红"; fail=1; }
[ "$F_CANC" -ge 2 ] || { echo "× SA-f 取消穿透只被断言了 $F_CANC 次：块内抛出与真实协程取消是两条不同的路"; fail=1; }
[ "$F_TYPES" -ge 3 ] || { echo "× SA-f 四类失败只点了 $F_TYPES 处：分类错一类这里看不出来"; fail=1; }
[ "$F_NOREQ" -ge 3 ] || { echo "× SA-f 「请求不出网」的断言只剩 $F_NOREQ 处：取消/缺陷/未绑定三条都靠它才不是空话"; fail=1; }

if [ "$fail" = 0 ]; then echo "safeApiCall 落点判据通过"; else echo "safeApiCall 落点判据失败"; fi
exit "$fail"
