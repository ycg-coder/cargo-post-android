#!/usr/bin/env bash
# #54 用户端「我的乘车安排」的结构判据（配 tools/arrangement_negative_controls.sh）
# 管的是四件事：死通路真的接上了、入口在不在、判定只在纯层、后端给的数字码没被当文案念出去。
# 用法：bash tools/check_arrangement_probes.sh        退出码非 0 = 这一格被拆了
# 写法：按内容过滤不按行号；每个"目标 0"都配一条同机制反证；注释里点名某句话**不算**把那句话写进了产品。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
RT=$M/nav/Routes.kt
BR=$M/ui/BusPostRoot.kt
MS=$M/ui/user/MineScreen.kt
AS=$M/ui/user/ArrangementsScreen.kt
AR=$M/ui/user/ArrangementRows.kt
MD=$M/data/model/Models.kt
RP=$M/data/repo/Repo.kt
CT=app/src/test/java/com/shanxiang/buspost/ui/user/ArrangementRowsContractTest.kt
NC='^[0-9]+:[[:space:]]*(\*|//|/\*)'

for f in "$RT" "$BR" "$MS" "$AS" "$AR" "$MD" "$RP" "$CT"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

# 单文件：grep -n 的输出行首是 `行号:`，注释行紧跟 `*` / `//` / `/*`
nc_count() { grep -n "$2" "$1" | grep -cvE "$NC" || true; }
# 全工程：grep -rn 的形状是 `文件:行:内容`，先剥掉 `文件:行:` 再套同一个注释过滤器。
# 本仓 grep 用相对路径、文件名里不含冒号，所以这种剥法是准的；
# 直接对 -rn 的输出用 NC 会一条都不命中（那是"注释过滤器形状错了"，不是"这句话不存在"）。
nc_all() {
  grep -rn "$1" "$M" --include=*.kt | sed 's/^[^:]*:[0-9]*://' | grep -cvE '^[[:space:]]*(\*|//|/\*)' || true
}

# AR-a 路由常量 / 注册点 / 走没走 BusRouteBody（键盘遮挡与边到端口径要跟全工程一致）
ARA_CONST=$(grep -c 'const val ARRANGEMENTS = "arrangements"' "$RT")
ARA_REG=$(grep -c 'composable(Routes.ARRANGEMENTS)' "$BR")
ARA_BODY=$(grep -c 'composable(Routes.ARRANGEMENTS).*BusRouteBody' "$BR")

# AR-b 入口恰好一个（只认非注释行：注释里提一次路由名不算第二个入口）
ARB=$(nc_count "$MS" 'nav.navigate(Routes.ARRANGEMENTS)')
ARB_ALL=$(nc_all 'nav.navigate(Routes.ARRANGEMENTS)')

# AR-c 死通路接活：Repo 里那条定义此前**零消费者**，现在必须恰好一个真调用
ARC_DEF=$(grep -c 'suspend fun myArrangements()' "$RP")
ARC_CALL=$(nc_all 'Repo.myArrangements()')
ARC_PAGE=$(nc_count "$AS" 'Repo.myArrangements()')

# AR-d 措辞只在纯层：页面不许自己写"还没有乘车安排 / 没取到"（两处各自漂移的开端）
ARD=$(nc_count "$AS" 还没有乘车安排)
ARD2=$(nc_count "$AS" 没取到)
ARD_PROOF1=$(nc_count "$AR" 还没有乘车安排)
ARD_PROOF2=$(nc_count "$AR" 没取到)

# AR-e 这一页不显示任何分钟数：`/send/arrangements` 那十一个键里没有 etaMinutes（审计 §68）。
#   0 命中的反证：同一个键名在模型层数得出（字段仍在，只是这一页不念它）。
ARE=$(nc_count "$AR" 分钟)
ARE2=$(nc_count "$AS" 分钟)
#   反证用**同一机制、同一个文件**里的正对照：`承运车辆` 这一行标签必须数得出恰好 1，
#   否则"分钟 0 命中"就可能是过滤器根本没在看这个文件（而不是这一页真的不念 ETA）。
ARE_PROOF=$(nc_count "$AR" 承运车辆)

# AR-f 数字码不得被当文案念：Models 里那条 multiStr("planStatus") 必须已经换成按码读
ARF_BAD=$(grep -c 'multiStr("planStatus")' "$MD")
ARF=$(grep -c 'planStatusCode = o.multiNumOrNull("planStatus")?.toInt()' "$MD")

# AR-g 四格各有出口（少一格就是把某一态静默并进另一态）
ARG_L=$(grep -c 'ArrangementsKind.Loading' "$AS")
ARG_F=$(grep -c 'ArrangementsKind.Failed' "$AS")
ARG_E=$(grep -c 'ArrangementsKind.Empty' "$AS")
ARG_S=$(grep -c 'ArrangementsKind.Shown' "$AS")

# AR-h 初值必须是"还没结论"，不能是"成功且为空"
ARH=$(grep -c 'RemoteState<List<Arrangement>>(loading = true)' "$AS")
ARH_BAD=$(grep -c 'RemoteState<List<Arrangement>>(data = emptyList' "$AS")

# AR-i 契约用例在场
ARI=$(grep -cE '^[[:space:]]*@Test' "$CT")

echo "AR-a 常量 / 注册 / BusRouteBody    = $ARA_CONST/$ARA_REG/$ARA_BODY （目标 1/1/1）"
echo "AR-b 入口（本页 / 全工程）          = $ARB/$ARB_ALL （目标 1/1）"
echo "AR-c 定义 / 真调用 / 页面内         = $ARC_DEF/$ARC_CALL/$ARC_PAGE （目标 1/1/1：此前是零消费者的死代码）"
echo "AR-d 页面自带措辞 / 纯层反证        = $ARD+$ARD2/$ARD_PROOF1+$ARD_PROOF2 （目标 0/1+1：措辞只在纯层）"
echo "AR-e 本页「分钟」/ 同文件正对照       = $ARE+$ARE2/$ARE_PROOF （目标 0/恰好 1：这一页不念 ETA）"
echo "AR-f multiStr 读码(坏形) / 按码读    = $ARF_BAD/$ARF （目标 0/1）"
echo "AR-g 四格出口 L/F/E/S               = $ARG_L/$ARG_F/$ARG_E/$ARG_S （目标 1/1/1/1）"
echo "AR-h 初值 loading / 初值写成空       = $ARH/$ARH_BAD （目标 1/0）"
echo "AR-i 契约用例数                      = $ARI （目标 ≥13）"

fail=0
[ "$ARA_CONST" = 1 ] || { echo "× AR-a 路由常量缺失或重复（$ARA_CONST）"; fail=1; }
[ "$ARA_REG" = 1 ] || { echo "× AR-a 注册点不是恰好一处（现 $ARA_REG）：这一页打不开"; fail=1; }
[ "$ARA_BODY" = 1 ] || { echo "× AR-a 有注册没走 BusRouteBody（$ARA_BODY/1）：这一页会跟全工程口径不一致"; fail=1; }
[ "$ARB" = 1 ] || { echo "× AR-b 「我的」里那个入口不是恰好一条（现 $ARB）：页面存在但用户找不到"; fail=1; }
[ "$ARB_ALL" = 1 ] || { echo "× AR-b 全工程入口数=$ARB_ALL（目标 1）：多了就是第二处导航各走各的"; fail=1; }
[ "$ARC_DEF" = 1 ] || { echo "× AR-c Repo 里 myArrangements 的定义不见了（现 $ARC_DEF）"; fail=1; }
[ "$ARC_CALL" = 1 ] || { echo "× AR-c 真调用数=$ARC_CALL（目标恰好 1）：0 = 这一格又变回死代码，>1 = 同一条通路两个消费者各自取数"; fail=1; }
[ "$ARC_PAGE" = 1 ] || { echo "× AR-c 页面里的取数点不是恰好一处（现 $ARC_PAGE）"; fail=1; }
[ "$ARD" = 0 ] && [ "$ARD2" = 0 ] || { echo "× AR-d 页面自己写了措辞（$ARD/$ARD2）：两处文字迟早和纯层对不上"; fail=1; }
[ "$ARD_PROOF1" = 1 ] && [ "$ARD_PROOF2" = 1 ] || { echo "× AR-d 反证掉了（纯层 $ARD_PROOF1/$ARD_PROOF2，目标 1/1）：上面那个 0 数的是不存在的东西"; fail=1; }
[ "$ARE" = 0 ] && [ "$ARE2" = 0 ] || { echo "× AR-e 这一页出现了「分钟」（$ARE/$ARE2）：该端点根本不返回 ETA，写了就是凭空造数"; fail=1; }
[ "$ARE_PROOF" = 1 ] || { echo "× AR-e 反证掉了（纯层「承运车辆」=$ARE_PROOF，目标恰好 1）：那个 0 可能是过滤器没在看这个文件"; fail=1; }
[ "$ARF_BAD" = 0 ] || { echo "× AR-f 又用 multiStr 读方案状态码（$ARF_BAD 处）：屏幕上会出现「调度方案：2」这种念法"; fail=1; }
[ "$ARF" = 1 ] || { echo "× AR-f 按码读那行不在了（现 $ARF）：状态列会退回念数字"; fail=1; }
{ [ "$ARG_L" = 1 ] && [ "$ARG_F" = 1 ] && [ "$ARG_E" = 1 ] && [ "$ARG_S" = 1 ]; } || { echo "× AR-g 四格出口不齐（$ARG_L/$ARG_F/$ARG_E/$ARG_S）：某一态被静默并进另一态"; fail=1; }
[ "$ARH" = 1 ] || { echo "× AR-h 初值不再是「还没结论」（现 $ARH）：开局就替后端说了没有安排"; fail=1; }
[ "$ARH_BAD" = 0 ] || { echo "× AR-h 初值被写成空集合（$ARH_BAD 处）"; fail=1; }
[ "$ARI" -ge 13 ] || { echo "× AR-i 契约用例少于 13 条（现 $ARI）：四格与码/没给那几格的分工没人钉"; fail=1; }

if [ "$fail" = 0 ]; then echo "乘车安排页判据通过"; else echo "乘车安排页判据失败"; fi
exit "$fail"
