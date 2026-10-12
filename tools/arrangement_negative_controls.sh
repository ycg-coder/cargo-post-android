#!/usr/bin/env bash
# #54「我的乘车安排」结构判据的负控制（配 tools/check_arrangement_probes.sh）
# 用法：bash tools/arrangement_negative_controls.sh
# 为什么这本必须有：AR-b / AR-c / AR-d / AR-e / AR-f / AR-g / AR-h 里有多个"目标 0 或恰好 1"——
# 0 命中必须是"真的没有"，不能是 grep 形状写错或反证被一起抹掉；每个都要人为造一次破口证明它会红。
# ⚠ 会就地临时改源码并在结束时原样恢复；一次只允许跑一个（negctl_lock）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2

M=app/src/main/java/com/shanxiang/buspost
BR=$M/ui/BusPostRoot.kt
MS=$M/ui/user/MineScreen.kt
AS=$M/ui/user/ArrangementsScreen.kt
AR=$M/ui/user/ArrangementRows.kt
MD=$M/data/model/Models.kt
CT=app/src/test/java/com/shanxiang/buspost/ui/user/ArrangementRowsContractTest.kt
PROBE=tools/check_arrangement_probes.sh
FILES=("$BR" "$MS" "$AS" "$AR" "$MD" "$CT")
BAKS=()
for f in "${FILES[@]}"; do
  [ -f "$f" ] || { echo "✘ 缺 $f，本书作废"; exit 2; }
  b="/tmp/ar_neg.$(basename "$f").$$"; cp "$f" "$b"; BAKS+=("$b")
done
restore() { for i in "${!FILES[@]}"; do cp "${BAKS[$i]}" "${FILES[$i]}"; done; }
trap 'restore; for b in "${BAKS[@]}"; do rm -f "$b"; done; negctl_release' EXIT

bash "$PROBE" >/dev/null 2>&1 || { echo "✘ 预检：$PROBE 起跑就不绿，一条破口都不注入"; exit 5; }
echo "[预检] 判据簿起跑绿"
ok=0; bad=0

inject() {
  python3 - "$1" "$2" "$3" <<'INJPY'
import sys
p, a, b = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(a) != 1:
    sys.exit(9)
open(p, 'w', encoding='utf-8', newline='').write(s.replace(a, b, 1))
INJPY
}

check() {
  local id="$1" want="$2"; shift 2
  restore
  if ! inject "$@"; then
    echo "✘ ${id} 锚点没命中（不是恰好 1 次），本次未注入 ⇒ 判据未被验证"; bad=$((bad + 1)); return
  fi
  local out rc
  out=$(bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = 0 ]; then echo "✘ ${id} 判据仍然绿（破口没咬住）"; bad=$((bad + 1))
  elif grep -qF "$want" <<<"$out"; then echo "✔ ${id} 红且原因对得上（$want）"; ok=$((ok + 1))
  else echo "✘ ${id} 红了但不是那一条（缺 $want）"; bad=$((bad + 1)); fi
}

# 1 页面压根不取数（这一格又变回死代码）→ AR-c
check AR-1 "× AR-c 真调用数=0" "$AS" \
  '            val s = Repo.myArrangements().toState()' ''
# 2 注册点撤了：页面存在但打不开 → AR-a
check AR-2 "× AR-a 注册点不是恰好一处" "$BR" \
  '        composable(Routes.ARRANGEMENTS) { com.shanxiang.buspost.ui.common.BusRouteBody { ArrangementsScreen() } }' ''
# 3 注册了但没走 BusRouteBody（键盘遮挡/边到端口径掉队）→ AR-a
check AR-3 "× AR-a 有注册没走 BusRouteBody" "$BR" \
  '        composable(Routes.ARRANGEMENTS) { com.shanxiang.buspost.ui.common.BusRouteBody { ArrangementsScreen() } }' \
  '        composable(Routes.ARRANGEMENTS) { ArrangementsScreen() }'
# 4 入口撤掉：页面在路由表里但用户找不到 → AR-b
check AR-4 "× AR-b 「我的」里那个入口不是恰好一条" "$MS" \
  '                    nav.navigate(Routes.ARRANGEMENTS)' ''
# 5 页面自己写措辞（两处各自漂移的开端）→ AR-d
check AR-5 "× AR-d 页面自己写了措辞" "$AS" \
  '                    title = page.title,' \
  '                    title = "还没有乘车安排",'
# 6 反证自身：纯层那句"还没有乘车安排"整体搬家 → ARD_PROOF 掉出 1，那个 0 就不再可信
check AR-6 "× AR-d 反证掉了" "$AR" \
  'private const val EMPTY_TITLE = "还没有乘车安排"' \
  'private const val EMPTY_TITLE = "本周没有排好的班次"'
# 7 这一页开始报分钟数（该端点根本不返回 ETA）→ AR-e
check AR-7 "× AR-e 这一页出现了「分钟」" "$AS" \
  '                        Text(page.title, color = t.textSecondary, fontSize = (13 * scale).sp)' \
  '                        Text("预计 8 分钟到站", color = t.textSecondary, fontSize = (13 * scale).sp)'
# 8 反证自身：同文件那个正对照标签被改掉 → 说明"分钟 0 命中"可能只是过滤器没在看这个文件
check AR-8 "× AR-e 反证掉了" "$AR" \
  '    "承运车辆" to a.plate.ifBlank { NOT_GIVEN },' \
  '    "车" to a.plate.ifBlank { NOT_GIVEN },'
# 9 方案状态码又被当文案念（屏幕上会出现"调度方案：2"）→ AR-f
check AR-9 "× AR-f 又用 multiStr 读方案状态码" "$MD" \
  '            planStatusCode = o.multiNumOrNull("planStatus")?.toInt(),' \
  '            planStatusCode = o.multiStr("planStatus").toIntOrNull(),'
# 10 失败态被并进在飞态（某一格静默消失）→ AR-g
check AR-10 "× AR-g 四格出口不齐" "$AS" \
  '                ArrangementsKind.Failed -> ErrorState(page.body, onRetry = { vm.load() })' \
  '                ArrangementsKind.Loading -> ErrorState(page.body, onRetry = { vm.load() })'
# 11 初值从"还没结论"改成"成功且为空"（开局替后端说没有安排）→ AR-h
check AR-11 "× AR-h 初值被写成空集合" "$AS" \
  '    var state by mutableStateOf(RemoteState<List<Arrangement>>(loading = true))' \
  '    var state by mutableStateOf(RemoteState<List<Arrangement>>(data = emptyList(), success = true))'
# 12 契约用例被摘掉一条 → AR-i
check AR-12 "× AR-i 契约用例少于 13 条" "$CT" \
  '    @Test
    fun `六个方案状态码逐条对上后端枚举`() {' \
  '    fun `六个方案状态码逐条对上后端枚举`() {'

restore
bash "$PROBE" >/dev/null 2>&1 && echo "[恢复] 判据簿重新通过" || { echo "✘ [恢复] 判据簿仍红"; bad=$((bad + 1)); }
same=1
for i in "${!FILES[@]}"; do
  cmp -s "${BAKS[$i]}" "${FILES[$i]}" || { echo "✘ [恢复] ${FILES[$i]} 与原样不一致"; same=0; }
done
[ "$same" = 1 ] && echo "[恢复] 全部文件与原样一致" || bad=$((bad + 1))

# 12 条 = AR-1…AR-5 + AR-7 + AR-9…AR-12（十条真破口）+ AR-6/AR-8（两条"反证自身"）。
# 期望值逐条数出来，不抄上一本的数字。
echo "乘车安排页结构负控：通过 $ok / 失败 $bad"
[ "$bad" = 0 ] && [ "$ok" = 12 ]
