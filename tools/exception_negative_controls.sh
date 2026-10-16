#!/usr/bin/env bash
# §54 异常上报**探针**的负控制：逐条破口 → 确认 check_exception_probes.sh exit 1 且报的就是这一条。
# 只在临时目录副本上动手，工程真身不受影响（纯文本判据，不需要编译 ⇒ 可与其他脚本并行）。
# 用法：bash tools/exception_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/ex_neg.$$
PKG=com/shanxiang/buspost
PR="$SRC/tools/check_exception_probes.sh"
mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
[ -f "$SC/app/src/main/java/$PKG/ui/driver/DriverExceptionScreen.kt" ] || { echo "✘ 副本没建起来（$SC），本轮负控全部作废"; exit 2; }
A="$SC/app/src/main/java/$PKG"
RP="$A/ui/driver/DriverExceptionReport.kt"
DS="$A/ui/driver/DriverExceptionScreen.kt"
RS="$A/ui/driver/RoutesScreen.kt"
RQ="$A/nav/Routes.kt"
RT="$A/ui/BusPostRoot.kt"
AS="$A/core/AppState.kt"
TR="$SC/app/src/test/java/$PKG/ui/driver/DriverExceptionReportTest.kt"
TC="$SC/app/src/test/java/$PKG/ui/driver/DriverExceptionContractTest.kt"
CLEAN=/tmp/ex_neg_clean.$$; cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

for f in "$RP" "$DS" "$RS" "$RQ" "$RT" "$AS" "$TR" "$TC"; do
    [ -f "$f" ] || { echo "✘ 负控文件缺失：$f（副本没复制全，下面每一条都会假绿）"; exit 2; }
done

ctl() { # $1=编号 $2=说明
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" = 1 ] && echo "$out" | grep -q "^× $1"; then
    echo "✔ $1  $2 → exit 1：$(echo "$out" | grep "^× $1" | head -1)"
  elif [ "$code" = 1 ]; then
    echo "△ $1  $2 → exit 1，但抓到的是别的判据：$(echo "$out" | grep '^×' | head -1)"
  else
    echo "✘ $1  $2 → 负控制失效（exit $code），这条判据等于没判据"
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

swap() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swap 锚点没命中，破口根本没注入（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
PY
}
drop() { python - "$1" "$2" <<'PY' || { echo "✘ drop 锚点没命中，破口根本没注入（这条负控不成立）"; exit 2; }
import sys
p, needle = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8').read()
assert needle in s, "ANCHOR-MISS: " + needle[:60]
open(p, 'w', encoding='utf-8').write(s.replace(needle, '// NEG-CONTROL 摘掉：' + needle.strip(), 1))
PY
}
kill_test() { python - "$1" <<'PY' || { echo "✘ kill_test 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test')
assert i >= 0, "ANCHOR-MISS: @Test"
open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | tail -1)"
# 基线不绿就立刻收工：否则每条都会因为"文件本来就不在"打出 ✔，整轮负控是假的
[ "$base_code" = 0 ] || { echo "✘ 基线未通过，负控结果一律不采信"; exit 2; }

kill_test "$TR"; ctl EX-a "纯函数用例被注释掉一条"
kill_test "$TC"; ctl EX-a "契约用例被注释掉一条"

# ⚠ D-135 重锚：提交体改走 exceptionActionBody 这个纯函数出口（照片键的纪律要能被 JVM 用例钉住）。
#   旧锚点匹配不上 ⇒ drop 报 ANCHOR-MISS 并退 2（本轮实测：整本在 EX-b 那一站就断在这里）。
drop "$DS" 'val ack = Repo.legAction(LegDriverAction.EXCEPTION.api, leg.legId, exceptionActionBody(remark, uploaded))'
ctl EX-b "页面不再经 Repo 单一出口"

after_line() { python - "$1" "$2" "$3" <<'PY'
import sys
p, anchor, add = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read(); i = s.find(anchor)
assert i >= 0, "ANCHOR-MISS: " + anchor[:60]
j = s.index('\n', i) + 1
open(p, 'w', encoding='utf-8').write(s[:j] + add + '\n' + s[j:])
PY
}
after_line "$DS" 'val ack = Repo.legAction(' '                Api.post("/app-api/transport/driver/leg/exception", mapOf("legId" to leg.legId))'
ctl EX-b "司机页面自己 Api.post（绕过幂等闸与 401 收口）"

after_line "$DS" 'val ack = Repo.legAction(' 'val invented = mapOf("type" to "ROAD_BLOCKED", "reasonType" to 3)'
ctl EX-c "报文里出现自造的 type 键（后端 ReqVO 没有这个字段）"

swap "$TC" 'assertEquals(setOf("driverId", "legId", "remark"), keysOf(body))' 'assertEquals(setOf("driverId", "legId", "remark", "type"), keysOf(body))'
ctl EX-c "契约测试不再钉住后端认识的三个键"

swap "$RP" 'ROAD_BLOCKED("道路阻断"),' 'ROAD_BLOCKED("路面中断"),'
ctl EX-d "§54 的分类标签被改写"

swap "$RP" $'    HANDOVER_IMPOSSIBLE("无法交接"),\n' ''
ctl EX-d "§54 的七项少了一项"

swap "$RP" 'if (photoUrl.isEmpty()) mapOf("remark" to remark)' \
     'if (photoUrl.isEmpty()) mapOf("remark" to remark, "photoUrl" to photoUrl)'
ctl EX-k "空串也写 photoUrl：事件里会出现一条指向空地址的凭证"

after_line "$DS" 'val ack = Repo.legAction(' '        val inlinePhoto = mapOf("photoUrl" to uploaded)'
ctl EX-k "页面自己内联拼 photoUrl：同一份键两处可以各写各的"

swap "$RP" 'internal const val EXCEPTION_REMARK_MAX = 200' 'internal const val EXCEPTION_REMARK_MAX = 2000'
ctl EX-e "remark 上限被抬到后端列宽之外（超限=1406=整笔回滚）"

swap "$RP" 'else joined.substring(0, EXCEPTION_REMARK_MAX - 1) + "…"' 'else joined'
ctl EX-e "截断路径被删（超长 remark 直接出网）"

swap "$RQ" 'const val DRIVER_EXCEPTION = "driverException"' 'const val DRIVER_EXCEPTION = "driverException/{legId}"'
ctl EX-f "路由常量字面量被改动（带参数会绕过精确匹配的分域闸）"

# ⚠ D-082 之后重锚：这一行现在是 `composable(Routes.DRIVER_EXCEPTION) { … BusRouteBody { DriverExceptionScreen() } }`
#   （D-076 那轮把注册包进了 BusRouteBody），旧锚点整句匹配不上 ⇒ drop 静默落空、EX-f 根本不判。
#   锚点只指到 `{` 为止：里面套什么由 BusRouteBody 说，这里钉的是"导航图那一行还在不在生效行里"。
drop "$RT" 'composable(Routes.DRIVER_EXCEPTION) {'
ctl EX-f "异常页没注册进导航图"

drop "$RS" 'onException = { AppState.openException(leg.legId); nav.navigate(Routes.DRIVER_EXCEPTION) },'
ctl EX-f "线路台的异常上报入口没了"

drop "$AS" 'fun openException(legId: String) {'
ctl EX-f "段号进入意图的函数被删"

swap "$DS" 'receipt = "没有可选中的运输段，本次未发给后端"' 'receipt = "上报成功"'
swap "$DS" 'receipt = if (reason == null) "先选一项异常情况，本次未发给后端"' 'receipt = if (reason == null) "上报成功"'
swap "$DS" 'else "选「其他」必须写清具体情况，本次未发给后端"' 'else "上报成功"'
# ⚠ D-082 之后重锚 + 判据随动（EG4/EG5）：这句在 10-12 话术收紧时改短了，旧锚点带着
#   「本次点击未发给后端」那半句 ⇒ swap 静默落空。就算把锚点对上，EG 那条腿也看不见这一格
#   （它数的是"未发给后端"四个字，而这一格现在说的是"以下为当前状态"），所以判据改成按 **分支** 钉。
swap "$DS" 'ack.notSent -> "同一个异常上报还在处理中，以下为当前状态"' 'ack.notSent -> "同一个异常上报已提交后端，以下为当前状态"'
ctl EX-g "把没发出去的说成已提交（假上报）"

swap "$DS" '"异常已提交，但后端回读仍是「${back.statusLabel}」，可能未被接受，请下拉刷新核对"' '"异常已提交后端，状态已更新"'
ctl EX-g "后端没接受也照样显示已更新"

after_line "$DS" 'legs = feed.items' 'legs = legs.map { it.copy(status = 99) }'
ctl EX-h "本机自封段状态为异常（§26/§27 的老毛病）"

swap "$DS" 'if (!DriverLegStateMapper.of(leg).canReportException) {' 'if (false) {'
ctl EX-i "出网前的终态守卫被摘掉：已完成的段也能把异常发给后端"

swap "$DS" 'val canSend = leg != null && DriverLegStateMapper.of(leg).canReportException && vm.reason != null' 'val canSend = leg != null && vm.reason != null'
ctl EX-i "提交按钮不再判 canReportException"

drop "$RS" 'if (flow.canReportException) {'
ctl EX-i "线路台入口不再判终态段：每一段都长出「上报异常」"

swap "$DS" 'clickable(enabled = flow.canReportException, onClick = onSelect)' 'clickable(onClick = onSelect)'
ctl EX-i "选段列表不再禁用终态段：能选中一段不可报的"

# ---- EX-j（缺陷 #43：段序是单内序号，跨单重号）----
swap "$DS" 'legOptionTitle(leg, flow.canReportException)' '"第 ${leg.seq} 段 · ${leg.statusLabel}"'
ctl EX-j "选段行退回手写的「第 N 段」：两张单的两行又都念「第 1 段」"

swap "$DS" '${legReceiptText(back)}' '第 ${back.seq} 段「${back.statusLabel}」'
ctl EX-j "提交回执不再带订单标识：回读那句认不出是哪张单的段"

swap "$RP" '?: "单号未给 · "' '?: ""'
ctl EX-j "缺单号的兜底句被删：没单号时悄悄退回只念段序（缺陷换面孔回来）"

echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
