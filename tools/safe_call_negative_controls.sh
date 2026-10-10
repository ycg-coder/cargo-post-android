#!/usr/bin/env bash
# SA-a…SA-f + SA-e2/SA-e3/SA-e4（tools/check_safe_call_probes.sh）的**负控制**（F-2 第 8 步的配套簿，
#   F-3 第 5 步在同本续加 19…26 号破口；编号只往后追加，历史那 18 条的号不改 —— 台账按号引用它们）。
# 逐条破口 → 确认判据 exit 1 且**报的就是这一条**（按 SA-x 点名，不是"报了别的"就算过）。
# 只在临时目录副本上动手，工程真身不受影响；每条注入后整份副本从干净基线重拷。
# 用法：bash tools/safe_call_negative_controls.sh
#
# 本本有几条**故意打到自己头上**（17 改判据簿的 $NC、18 打空锚点）：
# SA-* 里一半的闸门是"目标 0"，而"扫到 0"和"扫坏了所以 0"输出一模一样 —— 只有反证闸门会红，
# 所以反证闸门必须有对应的破口来证明它真的会红，否则整本判据是摆设。
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/sa_neg.$$
CLEAN=/tmp/sa_neg_clean.$$
PKG=com/shanxiang/buspost
PR="$SRC/tools/check_safe_call_probes.sh"
mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
cp -r "$SRC/tools" "$SC/tools"          # 17 号要改判据簿自己，不拷 tools 就只能改真身
for need in "$SC/app/src/main/java/$PKG/core/net/Api.kt" \
            "$SC/app/src/main/java/$PKG/data/repo/Repo.kt" \
            "$SC/app/src/main/java/$PKG/ui/user/BusScreen.kt" \
            "$SC/app/src/main/java/$PKG/core/loc/Locator.kt" \
            "$SC/app/src/main/java/$PKG/core/map/PolylineDecision.kt" \
            "$SC/app/src/test/java/$PKG/core/net/SafeApiCallContractTest.kt" \
            "$SC/app/src/test/java/$PKG/core/map/PolylineDecisionContractTest.kt" \
            "$SC/tools/check_safe_call_probes.sh"; do
  [ -e "$need" ] || { echo "✘ 副本缺 $need（缺一样就会有一条负控假绿）"; exit 2; }
done
trap 'rm -rf "$SC" "$CLEAN" /tmp/sa_device_present.log; negctl_release' EXIT
cp -r "$SC" "$CLEAN"

API="$SC/app/src/main/java/$PKG/core/net/Api.kt"
REPO="$SC/app/src/main/java/$PKG/data/repo/Repo.kt"
BUS="$SC/app/src/main/java/$PKG/ui/user/BusScreen.kt"
LOC="$SC/app/src/main/java/$PKG/core/loc/Locator.kt"
TST="$SC/app/src/test/java/$PKG/core/net/SafeApiCallContractTest.kt"
PDEC="$SC/app/src/main/java/$PKG/core/map/PolylineDecision.kt"
PTEST="$SC/app/src/test/java/$PKG/core/map/PolylineDecisionContractTest.kt"
BOOK="$SC/tools/check_safe_call_probes.sh"

FAILED=0
N_OK=0 N_BAD=0
ctl() { # $1=说明 $2=应当点名的判据（SA-x） $3=跑哪一本判据（默认原判据簿；17 号要跑**副本里被改过的那本**）
  # ⚠ 匹配要带尾随空格：`^× SA-e` 不带空格会把 `× SA-e2 …` 也算成"报的就是这一条"（F-3 ⑤ 加了同族编号之后
  #   这是一个真实的假绿来源——打破 SA-e3 的破口会被记成 SA-e 的 ✔）。
  local out code book="${3:-$PR}"
  out=$(BUSPOST_ANDROID="$SC" bash "$book" 2>&1); code=$?
  if [ "$code" = 1 ] && echo "$out" | grep -q "^× $2 "; then
    echo "✔ $2  $1 → exit 1：$(echo "$out" | grep "^× $2 " | head -1)"; N_OK=$((N_OK + 1))
  elif [ "$code" = 1 ]; then
    echo "△ $2  $1 → exit 1，但报的是别的判据：$(echo "$out" | grep '^× ' | head -1)"
    N_BAD=$((N_BAD + 1)); FAILED=1
  else
    echo "✘ $2  $1 → 没变红（rc=$code），这条判据是永不为红的摆设"
    N_BAD=$((N_BAD + 1)); FAILED=1
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# 注入器：锚点没命中就当场 exit 2 ——「以为注入了、其实树没动」是本工作区踩过两次的假绿来源（D-037/F2-7i）
# ⚠ 替换文本**不得再包含锚点原文**（SELF-MATCH），否则判据会数到注释里那句，破口看起来还在 ⇒ 假绿。
swap() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swap 锚点没命中，破口根本没注入（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
assert old not in new, "SELF-MATCH: 替换文本里还留着锚点，判据会读到注释"
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
PY
}
# grow：往锚点后面**追加**一份（专打"长出第二个 helper"这类重复）——这类注入必须保留锚点，所以不查 SELF-MATCH
grow() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ grow 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p, old, add = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
assert s.count(old) == 1, "ANCHOR-NOT-UNIQUE: 锚点出现多次，追加会改错位置"
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, old + add, 1))
PY
}

# 基线：不动任何东西时必须全绿，否则后面每一条红都无法归因
if BUSPOST_ANDROID="$CLEAN" bash "$PR" >/dev/null 2>&1; then
  echo "✔ BASE  干净副本判据全绿"
else
  echo "✘ BASE  干净副本就红了，下面每一条红都无法归因"
  FAILED=1
fi

# ── SA-a helper 本体 ────────────────────────────────────────────────────────
# 1 签名暗改：把非挂起 lambda 换成挂起 lambda。DEF 那条整行字面量就断了，而 awk 取体用的前缀锚点还在 ⇒
#   只有 SA-a 红。这正是 F-2 ⑧ 现场证伪过的那个猜测（`ApiTest.kt:171` 证明非挂起形参就能包挂起调用），
#   所以"改回挂起形参"是一次真实的回退，不是假设。
swap "$API" 'suspend inline fun <T> safeApiCall(block: () -> T): ApiResult<T> = try {' \
           'suspend inline fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> = try {'
ctl "helper 签名改成挂起形参（第二份真相长出来）" SA-a

# 2 第二份同签名 helper：DEF 从 1 变 2。判据说的是"定义不是那一条"，重复定义同样是那一条的漂移。
grow "$API" 'suspend inline fun <T> safeApiCall(block: () -> T): ApiResult<T> = try {' \
  $'\nsuspend inline fun <T> safeApiCall(block: () -> T): ApiResult<T> = try {'
ctl "同一条 helper 定义长出第二份（两套名字各走一半的前奏）" SA-a

# ── SA-b 体内：取消 / 四类 / 不兜底 ─────────────────────────────────────────
# 3 摘掉取消出口并把它收成一个普通失败：B_CANC 与 B_THROW 同时归 0（两条都属 SA-b，不是两条判据）。
swap "$API" $'    ApiResult.Success(block())\n} catch (e: CancellationException) {\n    throw e\n}' \
           $'    ApiResult.Success(block())\n} catch (e: CancellationException) {\n    ApiResult.NetworkError(e.message, e)\n}'
ctl "helper 把协程取消收成一次网络失败（§七 明令禁止的那条链路）" SA-b

# 4 401 并进传输失败：四类少一类，剩下的靠 message 顶包。
swap "$API" $'} catch (e: UnauthorizedException) {\n    ApiResult.Unauthorized(e.message)\n}' \
           $'} catch (e: java.net.SocketTimeoutException) {\n    ApiResult.NetworkError(e.message, e)\n}'
ctl "四类里 401 那一类被合并（登录态失效会被说成网络不好）" SA-b

# 5 长出兜底 catch：程序错误从此领到一张"业务失败"的通行证（§六）。
#   这一条是**追加**而不是替换，所以走 grow（保留锚点）——SELF-MATCH 闸门是为"把破口写成注释"设的，
#   不能把它当成"追加型注入"的通行证，所以这里显式用另一种注入器，而不是放宽 swap 的断言。
grow "$API" '    ApiResult.NetworkError(e.message ?: "网络异常", e)' \
  $'\n} catch (e: Exception) {\n    ApiResult.NetworkError(e.message, e)'
ctl "体内加回 catch(Exception) 兜底（NPE 会被念成一次网络失败）" SA-b

# ── SA-c 迁移点 ─────────────────────────────────────────────────────────────
# 6 页面消费点退回裸调用：USE 从 6 掉到 5、C_VM 归 0，两条都属 SA-c。
swap "$BUS" 'val result = safeApiCall { Repo.linePolyline(id) }' \
           'val result = runCatching { Repo.linePolyline(id) }'
ctl "页面的折线消费点退回 runCatching（真实修复在消费侧断线）" SA-c

# 7 某个迁移点被还原成 attempt：SA-c 点名"②天气段不见了"，同时 SA-d 的 Repo 残留也会红
#   （一条破口打两条判据是这里的真实关系：还原一个网络读，既丢分类又长回旧 helper）。
swap "$REPO" 'val forecast = safeApiCall {' 'val forecast = attempt {'
ctl "迁移点②天气段还原成 attempt（同轮应看到 SA-d 的 Repo 残留也红）" SA-c

# ── SA-d 伪装形状 ───────────────────────────────────────────────────────────
# 7b F-2 ⑨ 的豁免面点名判据：把 Repo 里唯一那处 attempt（预报解析段）改个名。
#    D_ATTEMPT_REPO 仍是 1，只有整行字面量能发现"这 1 处不再是 ⑨ 那一处"——
#    没有这条，"=1" 这个新闸门可以用来藏任意一处 attempt。
swap "$REPO" 'val forecast = attempt {' 'val fcRenamed = attempt {'
ctl "Repo 那处 attempt 被换名（数量没变）：豁免必须点名，不能只数个数" SA-d

# 8 折线出口改回"失败洗成空列表"：D_EMPTY 1、D_THROW 0，两条都属 SA-d，且这一句就是本轮修的假话本身。
swap "$REPO" 'mapOf("routeId" to routeId)) }.getOrThrow()' \
             'mapOf("routeId" to routeId)) }.getOrNull() ?: return emptyList()'
ctl "折线失败又被洗成空列表（本轮真实修复点被还原）" SA-d

# 9 签名改成可空：调用方拿 null 就当"没有轨迹"，getOrThrow 的前提没了。
swap "$REPO" 'suspend fun linePolyline(routeId: String): List<Geo> {' \
             'suspend fun linePolyline(routeId: String): List<Geo>? {'
ctl "linePolyline 签名被改成不抛的可空形状" SA-d

# 10 顺手删掉一个非 Repo 的 attempt（定位取点）：SA-d 的全 main 下限 11→10。
#    这条防的是"迁移时被扫到一起改"——下限等于实测值（⑧ 的 7 处 + ⑨ 迁入的 4 处），所以删一处就红。
swap "$LOC" 'attempt { locations(context).first() }' 'runCatching { locations(context).first() }'
ctl "Worker/定位那几处 attempt 被顺手改掉一处（下限 11→10）" SA-d

# ── SA-e 页面文案 ───────────────────────────────────────────────────────────
# 11 独立状态字段被删：失败又要去挤共享的 vm.error。
swap "$BUS" '    var polylineUnconfirmed by mutableStateOf<String?>(null)' \
             '    private val polylineNotice = 0'
ctl "页面删掉独立的折线状态字段" SA-e

# 12 复位点少一处：上一次的失败会挂在下一条线路上（E_RESET 目标 2）。
swap "$BUS" 'polylineUnconfirmed = null' 'polylineUnconfirmed = polylineUnconfirmed'
ctl "换线时不复位上一次的失败（两处复位少了一处）" SA-e

# 13 硬编码那句假话回来：E_OLD_REASON 1 / E_NEW_REASON 0 / E_REASON 1，三条都属 SA-e。
swap "$BUS" 'reason = if (vm.polyline.size >= 2) null else vm.polylineReason(' \
             'reason = if (vm.polyline.size >= 2) null else "后端没有这条线路的真实道路折线",'
ctl "请求失败又被念成「后端没有这条线路的轨迹」" SA-e

# 14 副标题退回只看点数：没问过的线路也会被说成"后端未回传"。
swap "$BUS" 'subtitle = vm.polylineSubtitle(),' \
             'subtitle = if (vm.polyline.size >= 2) "真实道路折线" else "后端未回传该线路折线",'
ctl "副标题退回点数分支（没问过也说「后端未回传」）" SA-e

# ── SA-e2 三态是单一真相（F-3 ⑤）───────────────────────────────────────────
# 19 三格**数量不变、只改措辞**：把 NotAsked 那一格写成"后端未回传该线路折线"。
#    枚举还是三格、两个纯函数还在、两处 delegate 也没动 ⇒ 只数结构的判据全绿，
#    抓住它的是"那句话在全树恰好 1 份"。这一条是整族里最隐蔽的一种回退（本轮修的就是这句话本身）。
swap "$PDEC" '    phase == PolylinePhase.NotAsked -> "还没去问这条线路的折线"' \
             '    phase == PolylinePhase.NotAsked -> "后端未回传该线路折线"'
ctl "没问过那一格被写成「后端未回传」（三格数量没变，只有份数判据抓得到）" SA-e2

# 20 第二份真相：纯函数一字未动，页面副标题退回自己内联一份 when。
#    P_DELEG_SUB 0 + 全树那份文案 2 份，两条都属 SA-e2 —— 两处顺序各写一遍，下一轮就是两个答案。
swap "$BUS" 'fun polylineSubtitle(): String = polylineSubtitleOf(polylinePhase, polyline.size, polylineUnconfirmed)' \
            'fun polylineSubtitle(): String = when { polyline.size >= 2 -> "真实道路折线"; polylineUnconfirmed != null -> "这次没问到折线，可重试"; else -> "后端未回传该线路折线" }'
ctl "页面自己内联一份三态顺序（纯函数还在但没人调）" SA-e2

# 21 只改函数体、不动签名：摘掉 `!silent`。签名那条整行锚点一字未变，
#    于是"按需查询"变成 15s 轮询风暴而判据看不见 —— 这条破口是**写 SA-e2 时才发现判据漏了它**，
#    补的体判据（P_ASK_BODY）就是为它存在的。
swap "$PDEC" '    !silent && !selectedRouteId.isNullOrEmpty()' \
             '    selectedRouteId?.isNotEmpty() == true'
ctl "「该不该问」的函数体被换掉（签名没变，静默轮询也会去查折线）" SA-e2

# ── SA-e3 装载侧（F-3 ⑤）────────────────────────────────────────────────────
# 22 首屏只选中、不去问折线 = **D-042 在册那个缺陷的本体**。
#    这条不是假设：改回它之后整本书只有 SA-e3 一条红（其余全绿），说明旧判据确实允许这个缺陷存在。
swap "$BUS" 'if (shouldAskPolyline(selected?.id, silent)) loadPolyline()' \
            '// 负控 22：首屏只选中，不去问折线'
ctl "首屏自动选中之后不去问折线（在册缺陷复活）" SA-e3

# 23 双条件闸拆掉一半（只留序号）：取消选中后迟到的几何照样写回画面。
swap "$BUS" 'if (mySeq != polylineSeq || selected?.id != id) return@work' \
            'if (mySeq != polylineSeq) return@work'
ctl "写回前只比序号、不比线路编号（迟到几何画进空画面）" SA-e3

# 24 请求被取消却仍说"还在问"：相位退回 Asking 而不是 NotAsked。
#    E3_CANCEL 0 / E3_NOTASKED 2 / E3_ASKING 2 三条一起红 —— 取消不是失败这一条（§七）在页面上的落点。
swap "$BUS" 'if (mySeq == polylineSeq) polylinePhase = PolylinePhase.NotAsked' \
            'if (mySeq == polylineSeq) polylinePhase = PolylinePhase.Asking'
ctl "协程取消后相位不回退，屏幕永远挂着「正在要折线」" SA-e3

# ── SA-e4 三态行为簿（F-3 ⑤）────────────────────────────────────────────────
# 25 新用例簿删一条 @Test（15→14）：与 15 号同族，但打的是本轮那本。
swap "$PTEST" $'    @Test\n' $'    // @Test（负控 25：删一条用例）\n'
ctl "三态行为簿被删掉一条用例（15→14）" SA-e4

# 26 用例**条数没变**、断言被抽空：把"穷举 12 组合只许 1 个出口"改成恒真。
#    这一条防的是"用例行还在、性质没了"——数 @Test 的判据对它完全无感。
swap "$PTEST" '        assertEquals(1, hits)' '        assertTrue(hits >= 0)'
ctl "穷举那条断言被改成恒真（条数没变、性质没了）" SA-e4

# ── SA-f 契约用例 ───────────────────────────────────────────────────────────
# 15 注释掉一条 @Test：用例数 12→11。删用例必须让判据变红，这是"用例簿会不会悄悄瘦身"的唯一哨。
swap "$TST" $'    @Test\n' $'    // @Test（负控：删一条用例）\n'
ctl "契约用例被删掉一条（12→11）" SA-f

# 16 「请求不出网」断言被删一条：3→2。取消/缺陷/未绑定三条都靠这类断言才不是空话。
swap "$TST" '        assertEquals(0, server.requestCount)' '        assertEquals(1, server.requestCount)'
ctl "「请求没出网」的断言少了一条（3→2）" SA-f

# ── 打到判据簿自己头上 ──────────────────────────────────────────────────────
# 17 把 $NC（注释排除式）写成匹配一切：于是所有"生效代码计数"都变 0，SA-d/SA-e 那一串"目标 0"全绿，
#    只有反证闸门（LEGACY_PROOF>0）会红。这条是整本的保险丝：没有它，"把判据改宽"与"破口修干净"输出一样。
# ⚠ 本条第一次跑时报了 rc=0，原因不是判据弱，而是**注入改的是副本里的那本簿、跑的却是真身那本**
#   （`PR=$SRC/tools/...`）。打到判据簿自己头上的破口必须连"跑哪一本"一起改，否则这条永远是假红/假绿的摆设。
swap "$BOOK" "NC='(^|[0-9]+:)[[:space:]]*(\\*|//)'" "NC='.*'"
ctl "判据簿的注释排除式被改成匹配一切（所有 0 都变成假干净）" SA-a "$BOOK"

# 18 空锚点自检：拿一个树里不存在的字符串去 swap，注入器必须当场 exit 2 而不是"静默跳过、后面全绿"。
if python - "$API" <<'PY' 2>/dev/null
import sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
assert '这一句在树里根本不存在-NEG-CONTROL' in s, "ANCHOR-MISS"
PY
then
  echo "✘ 空锚点 注入器放过了一个不存在的锚点（后面每条「✔」都可能是假绿）"
  FAILED=1
else
  echo "✔ 空锚点  不存在的锚点被注入器当场拒绝（exit≠0，破口没被静默跳过）"
fi

# 恢复自检：全部注入还原后基线必须重新全绿，否则"红过"不能归因给破口
if BUSPOST_ANDROID="$SC" bash "$PR" >/dev/null 2>&1; then
  echo "✔ RESTORE  还原后基线重新全绿"
else
  echo "✘ RESTORE  还原后仍然红 —— 沙盒没还原干净，本轮所有 ✔ 都要重跑"
  FAILED=1
fi

echo "计数（本脚本自己累加，不是事后 grep 推算）：破口打破 $N_OK 条 / 没打破 $N_BAD 条；自检行 3 条（BASE、空锚点、RESTORE）"
if [ "$FAILED" = 0 ]; then echo "safeApiCall 负控制全部打破判据"; else echo "safeApiCall 负控制有判据打不破"; fi
exit "$FAILED"
