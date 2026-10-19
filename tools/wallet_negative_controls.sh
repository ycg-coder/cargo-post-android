#!/usr/bin/env bash
# #56「我的钱包」结构判据的负控制（配 tools/check_wallet_probes.sh）
# 每个"目标 0 / 恰好 N"都要人为造一次破口，证明它会红；注入落在 /tmp 沙盒（整目录 copy），工程真身不动。
# ⚠ D-085 ㊾①：注入器落空必须说出口（`|| { MISS=1; return 1; }`），并且本簿自带"不存在的锚点必须被拒"的自检。
# 用法：bash tools/wallet_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/w_neg.$$
CLEAN=/tmp/w_neg_clean.$$
PKG=com/shanxiang/buspost
PROBE="$SRC/tools/check_wallet_probes.sh"

mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
M=$SC/app/src/main/java/$PKG
RT=$M/nav/Routes.kt
BR=$M/ui/BusPostRoot.kt
MS=$M/ui/user/MineScreen.kt
HS=$M/ui/user/HomeScreen.kt
WR=$M/ui/user/WalletRows.kt
WS=$M/ui/user/WalletScreen.kt
MD=$M/data/model/Models.kt
RP=$M/data/repo/Repo.kt
CT=$SC/app/src/test/java/$PKG/ui/user/WalletRowsContractTest.kt
for f in "$RT" "$BR" "$MS" "$WR" "$WS" "$MD" "$RP" "$CT"; do
  [ -f "$f" ] || { echo "✘ 沙盒缺 $f：判据扫得到、副本没有，本轮负控全部作废"; exit 2; }
done
cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); base=$?
if [ "$base" != 0 ]; then echo "✘ 基线未通过，负控结果一律不采信"; printf '%s\n' "$out" | grep -a '^×' | head -5; exit 2; fi
echo "✔ 基线：副本原样 exit 0"

MISS=0; ok=0; bad=0
sub() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(old) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(old), old[:60]))
if old in new: sys.exit("SELF-MATCH: 替换文本里还留着锚点")
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
PY
}
delline() { python - "$1" "$2" <<'PY' || { MISS=1; return 1; }
import sys
p, needle = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8', newline='').read()
if s.count(needle) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(needle), needle[:60]))
i = s.find(needle); j = s.find('\n', i)
open(p, 'w', encoding='utf-8', newline='').write(s[:i] + s[j + 1:])
PY
}
ins_after() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, anchor, add = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(anchor) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(anchor), anchor[:60]))
NL = '\r\n' if '\r\n' in s else '\n'
j = s.index('\n', s.find(anchor)) + 1
open(p, 'w', encoding='utf-8', newline='').write(s[:j] + add.replace('\n', NL) + NL + s[j:])
PY
}

check() { # $1=编号 $2=期望点名的判据 $3..=注入
  local id="$1" want="$2" rc out
  shift 2
  if ! "$@"; then
    echo "✘ $id 锚点没命中/不唯一，破口根本没注入（这条负控不成立）"
    bad=$((bad + 1)); MISS=0; rm -rf "$SC"; cp -r "$CLEAN" "$SC"; return
  fi
  out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = 0 ]; then
    echo "✘ $id 判据仍然绿（破口没咬住），这条判据等于没判据"; bad=$((bad + 1))
  elif printf '%s\n' "$out" | grep -qF "$want"; then
    echo "✔ $id 红且原因对得上（$want）"; ok=$((ok + 1))
  else
    echo "✘ $id 红了但不是那一条（缺 $want；实为 $(printf '%s\n' "$out" | grep -a '^×' | head -1)）"; bad=$((bad + 1))
  fi
  MISS=0; rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
check_miss() { # 本书自己的保险丝：不存在的锚点必须被拒
  local id="$1"; shift
  if "$@"; then
    echo "✘ $id 注入器放过了一个不存在的锚点（后面每条 ✔ 都可能是假绿）"; bad=$((bad + 1))
  else
    echo "✔ $id 不存在的锚点被注入器当场拒绝"; ok=$((ok + 1))
  fi
  MISS=0; rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# ---------- W-a 路由 / 注册 / 入口 ----------
check W-a1 "× W-a 路由常量" sub "$RT" 'const val WALLET = "wallet"' 'const val WALLET_V2 = "walletV2"'
check W-a2 "× W-a 注册点不是恰好一处" delline "$BR" '        composable(Routes.WALLET) { com.shanxiang.buspost.ui.common.BusRouteBody { WalletScreen() } }'
check W-a3 "× W-a 有注册但没走 BusRouteBody" sub "$BR" 'composable(Routes.WALLET) { com.shanxiang.buspost.ui.common.BusRouteBody { WalletScreen() } }' 'composable(Routes.WALLET) { WalletScreen() }'
check W-a4 "× W-a 「我的」里那个入口不是恰好一条" sub "$MS" 'nav.navigate(Routes.WALLET)' 'nav.navigate(Routes.ARRANGEMENTS)'
# 2026-10-13 合并后基线变成"两处入口"（「我的」列表 1 + 首页宫格 1），W-a 的 per-file 那一格仍钉 MineScreen=1。
# 老注入往 MineScreen 里再加一条 ⇒ 先咬的是 per-file 那条，念不到"全工程"这一格，破口与判据错位。
# 现在把第三处放进**首页**：MineScreen 仍是 1，只有 WA_ENTRY_ALL 会红，点名的正是这一格。
check W-a5 "× W-a 全工程入口数=" ins_after "$HS" 'if (guest) onLoginClick?.invoke() else nav.navigate(Routes.WALLET)' \
  '                nav.navigate(Routes.WALLET) // 负控：同一页第三处入口'

# ---------- W-b 只读：写口不许被接进来 ----------
check W-b1 "× W-b 写口被接进了生效代码" ins_after "$RP" '    suspend fun walletBalance(): One<WalletBalance> = try {' \
  '    suspend fun walletRecharge(amount: String): One<WalletBalance> = try { Api.post("$T/wallet/recharge", mapOf("amount" to amount)); One(null) } catch (e: Exception) { One(null) }'
check W-b2 "× W-b 两个读口不齐" sub "$RP" 'Api.get("$T/wallet/balance")' 'Api.get("$T/wallet/balance-v2")'
check W-b3 "× W-b 两个读口不齐" delline "$RP" '        val p = Api.get("$T/wallet/transactions", mapOf("limit" to limit.toString()))'

# ---------- W-c 那句话只有一个出处 ----------
check_miss W-c0 sub "$WR" '这一句在本仓根本不存在-NEG-CONTROL' 'x'
check W-c1 "× W-c 页面重抄了那句话的字面" sub "$WS" '                NO_WRITE_ENTRY_NOTE,' '                "这一版没有充值与余额支付入口：本机不接写口。",'
check W-c2 "× W-c 页面不再引用那个常量" sub "$WS" '                NO_WRITE_ENTRY_NOTE,' '                "",'
check W-c3 "× W-c 那句话在纯层不再是唯一声明" sub "$WR" 'internal const val NO_WRITE_ENTRY_NOTE =' 'internal const val WRITE_GATE_NOTE_MOVED ='

# ---------- W-d 码不许被当文案念 ----------
check W-d1 "× W-d 未识别状态那一格没了" sub "$WR" '    else -> "未识别的流水状态（$code）"' '    else -> "失败"'
check W-d2 "× W-d 未识别类型那一格没了" sub "$WR" '    else -> "未识别的流水类型（$type）"' '    else -> "消费"'
check W-d3 "× W-d 又用 multiStr 读状态码" sub "$MD" 'statusCode = o.multiNumOrNull("status")?.toInt(),' 'statusCode = o.multiStr("status").toIntOrNull(),'

# ---------- W-e 金额缺席不塌成 0 ----------
check W-e1 "× W-e 金额缺席不再念「系统没给这一项」" sub "$WR" 'internal fun walletMoney(v: Double?): String = v?.let { "¥" + String.format(java.util.Locale.CHINA, "%.2f", it) } ?: NOT_GIVEN' \
  'internal fun walletMoney(v: Double?): String = "¥0.00"'
check W-e2 "× W-e 四个余额键的可空读法不齐" sub "$MD" 'balance = o.multiNumOrNull("balance"),' 'balance = o.multiNum("balance"),'
check W-e3 "× W-e 四个余额键的可空读法不齐" sub "$MD" 'frozenAmount = o.multiNumOrNull("frozenAmount"),' 'frozenAmount = o.multiNum("frozenAmount"),'

# ---------- W-f 渠道那一格不替后端下结论 ----------
check W-f1 "× W-f 渠道那一格的标签变了" sub "$WR" '"渠道（后端原词）" to tx.payMethod.ifBlank { NOT_GIVEN },' '"渠道" to tx.payMethod.ifBlank { NOT_GIVEN },'
check W-f2 "× W-f 页面开始替后端宣布扣款" sub "$WR" '"渠道（后端原词）" to tx.payMethod.ifBlank { NOT_GIVEN },' '"渠道" to (tx.payMethod.ifBlank { NOT_GIVEN } + "，已通过支付宝扣款"),'
check W-f3 "× W-f 同文件正对照掉了" sub "$WR" 'null -> NOT_GIVEN' 'null -> "系统没给这一项"'

# ---------- W-g total 不冒充全量 ----------
check W-g1 "× W-g 「本次返回 N 条」那句没了" sub "$WR" 'internal fun walletTxScopeText(count: Int, limit: Int): String =
    "本次返回 $count 条（后端按 limit=$limit 取，未查全量，不代表总共只有这些）"' \
  'internal fun walletTxScopeText(count: Int, limit: Int): String = "共 $count 条"'
check W-g2 "× W-g 「未查全量」那半句没了" sub "$WR" '未查全量，不代表总共只有这些' '，就是全部'

# ---------- W-h 两个取数各自成格 ----------
check W-h1 "× W-h 两个取数的失败格不再各自判" sub "$WR" '        berr != null -> WalletSection(WalletCell.Failed, "余额", failedBody(berr), null)' '        false -> WalletSection(WalletCell.Failed, "余额", failedBody("x"), null)'
check W-h2 "× W-h 页面渲染的分区数不是 2" delline "$WS" '            WalletSectionBlock(page.txs, vm, scale, t) {'

# ---------- W-i 初值 ----------
check W-i1 "× W-i 两个初值不都是「还没结论」" sub "$WS" 'var balance by mutableStateOf(RemoteState<WalletBalance>(loading = true))' 'var balance by mutableStateOf(RemoteState<WalletBalance>(success = true))'
check W-i2 "× W-i 有初值被写成 success=true" sub "$WS" 'var txs by mutableStateOf(RemoteState<WalletTxPage>(loading = true))' 'var txs by mutableStateOf(RemoteState<WalletTxPage>(success = true))'

# ---------- W-j 用例簿 ----------
check W-j1 "× W-j 契约用例少于 16 条" sub "$CT" '    @Test
    fun `余额四个键都按可空读，缺席不显示零元`()' '    // @Test 负控注释掉一条
    fun `余额四个键都按可空读，缺席不显示零元`()'

echo "---"
echo "计数：按名点名变红 $ok 条 / 不成立 $bad 条"
[ "$bad" = 0 ] || echo "✘ 有破口没被自己那条判据抓住，或注入根本没落"
[ "$bad" = 0 ]
