#!/usr/bin/env bash
# 线路详情那一格有没有真底图（缺陷 #48，配 tools/detail_map_negative_controls.sh）
#
# 背景：实时公交主图早就走 `BusMap`（高德真实底图 + marker/polyline，起不来才退回自绘示意图），
# 而**线路详情**这一格从头到尾只画那张自绘 canvas —— 它长得像高德（网格 + 折线 + 站名），
# 但底下没有地图：不能缩放、不能拖动、看不出这条路穿过什么地名。台账把它归为"对标缺口，非回归"，
# 因为功能一直在（折线数据是真的），缺的是底图这一层。
#
# 判据要钉住的是"别再退回只画 canvas"：底图必须在、自绘必须只在高德失败的支路上、
# 且两处兜底形状一致（同一条 `if (…) { RouteMap } else { BusMap }`）。
# 写法沿用既有教训：先验文件存在；每个"目标 0/1"都配同机制反证；计数用确切字面量。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

B=app/src/main/java/com/shanxiang/buspost/ui/user/BusScreen.kt
[ -f "$B" ] || { echo "× 判据输入缺失：$B"; exit 2; }

cnt() { grep -c "$2" "$1"; }

# LM-a 底图容器：主图 + 线路详情各一处
LMA=$(cnt "$B" 'BusMap(')
# LM-b 详情那一格的失败态四件：声明 / 换线复位 / 分支 / 回调
LMB=$(cnt "$B" 'detailMapFailed')
# LM-c 详情自绘必须在兜底支上（紧跟 `if (detailMapFailed) {`）
LMC=$(grep -A 1 'if (detailMapFailed) {' "$B" | grep -c 'RouteMap(')
# LM-d 反证：同一机制在主图那支也数得出（中间隔了一行注释，所以 -A 2）
LMD=$(grep -A 2 'if (mapFailed) {' "$B" | grep -c 'RouteMap(')
# LM-e 自绘调用总数：两处都该是兜底；第三处等于又开了一格"只画 canvas 的地图"
LME=$(cnt "$B" 'RouteMap(')
# LM-f 折线四态那句在详情页也得单独成句（底图没有 subtitle 的位置，撤掉就没人说"这次没问到"）。
# ⚠ 必须滤注释：`grep -n` 会把"7d8b819 把 SectionTitle(subtitle = vm.polylineSubtitle()) 撤成…"
#   那一行也算进来 —— 那是在解释**当年为什么红**，不是生效代码；拿它顶数就等于用一个注释凑够数。
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'
LMF=$(grep -n 'vm\.polylineSubtitle()' "$B" | grep -vE "$NC" | wc -l | tr -d ' ')

echo "LM-a 底图容器处数（主图/详情）        = $LMA （目标恰好 2）"
echo "LM-b 详情失败态四件                    = $LMB （目标恰好 4）"
echo "LM-c 详情自绘在兜底支 / LM-d 主图反证  = $LMC/$LMD （目标 1/1）"
echo "LM-e 自绘调用总数                      = $LME （目标恰好 2：两处都是兜底）"
echo "LM-f 折线口径句（非注释：主图 subtitle + 详情一行） = $LMF （目标恰好 2）"

fail=0
[ "$LMA" = 2 ] || { echo "× LM-a 底图容器不是两处（现 $LMA）：线路详情那一格又只剩自绘 canvas，或主图那一格被撤了"; fail=1; }
[ "$LMB" = 4 ] || { echo "× LM-b 详情的地图失败态不齐（现 $LMB，目标 4：声明/复位/分支/回调）：高德起不来时无路可退，或退了再也回不去"; fail=1; }
[ "$LMC" = 1 ] || { echo "× LM-c 详情页的自绘不在兜底支上（现 $LMC）：底图与示意图的关系又含糊了"; fail=1; }
[ "$LMD" = 1 ] || { echo "× LM-d 反证掉了（主图那支数不出兜底，现 $LMD）：LM-c 那个 1 可能是 grep 写宽了"; fail=1; }
[ "$LME" = 2 ] || { echo "× LM-e 自绘调用不是两处（现 $LME）：多一处就是又有一格承诺了地图却只画网格"; fail=1; }
[ "$LMF" = 2 ] || { echo "× LM-f 折线口径句不是两处非注释调用（现 $LMF）：详情页那句被撤了，或主图那句被拿注释顶了数"; fail=1; }

if [ "$fail" = 0 ]; then echo "线路详情底图判据通过"; else echo "线路详情底图判据失败"; fi
exit "$fail"
