#!/usr/bin/env bash
# #39「收藏线路首页入口」的结构判据（配 tools/favorite_entry_negative_controls.sh）
# 管六件事：首页那一格恰好一个且没有登录闸门、三句文案各自唯一出处（页面不许重抄）、
# 整栏的闸门是"本机记着几条"而不是"这一轮显示出来几条"、失败格与下架格两支各只有一份、
# 纯层里不许出现同步类承诺、用例条数。
# 用法：bash tools/check_favorite_entry_probes.sh        退出码非 0 = 这一格被拆了
# 写法沿用本仓规矩：按内容过滤不按行号；每个"目标 0"配同文件正对照；下限一律等于现读（D-084 ③、D-086 ㊿②）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
FL=$M/ui/user/FavoriteLines.kt
HS=$M/ui/user/HomeScreen.kt
BS=$M/ui/user/BusScreen.kt
MD=$M/data/model/Models.kt
CT=app/src/test/java/com/shanxiang/buspost/ui/user/FavoriteLinesContractTest.kt
NC='^[0-9]+:[[:space:]]*(\*|//|/\*)'

for f in "$FL" "$HS" "$BS" "$MD" "$CT"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

nc_count() { grep -n "$2" "$1" | grep -cvE "$NC" || true; }

# FE-a 首页那一格恰好一个；旧的重复入口（与"个人中心"同为 onSelectTab(3)）已换掉；八宫格总数不变
FA_TILE=$(nc_count "$HS" 'FeatureTile(BusIcons.star, FAVORITE_ENTRY_LABEL')
FA_MORE=$(nc_count "$HS" '更多功能')
FA_TILES=$(nc_count "$HS" 'FeatureTile(BusIcons')

# FE-b 那一格不许有登录闸门：首页 gate 数从 6 格变 7 处（旧"更多功能"那一处撤掉、其余不动），
#      所以现读 7 里不含收藏格 ⇒ 收藏那一格的 onClick 必须只有 nav.navigate(Routes.BUS)
FB_GATE=$(nc_count "$HS" 'if (guest) onLoginClick')
FB_BUS=$(nc_count "$HS" 'nav.navigate(Routes.BUS)')

# FE-c 三句话各自唯一出处，页面只引用不重抄
FC_DEF=$(nc_count "$FL" 'FAVORITE_ENTRY_LABEL = "收藏线路"')
FC_REF=$(nc_count "$HS" 'FAVORITE_ENTRY_LABEL')
FC_OUTSIDE=$(grep -rn '收藏只存在这台设备上\|在线路详情里点星标' "$M" --include=*.kt | grep -vc 'FavoriteLines.kt')
FC_PROOF=$(nc_count "$FL" 'internal const val')

# FE-d 未命中那一格的两支各一份；失败原因必须真的从取数处带出来（声明/赋值/读取三处）
FD_FAIL=$(nc_count "$FL" '线路列表这一轮没取到')
FD_OFF=$(nc_count "$FL" '当前线路列表里没有')
FD_ERR=$(nc_count "$BS" 'linesError')

# FE-e 整栏闸门换成"本机记着几条"；chip 行自己的 isNotEmpty 仍在（那是嵌套，不是整栏闸门）
FE_VISIBLE=$(nc_count "$BS" 'if (favoriteRowVisible(favIds.size))')
FE_NESTED=$(nc_count "$BS" 'if (favLines.isNotEmpty())')
FE_UNRESOLVED=$(nc_count "$BS" 'favoriteUnresolvedText(favIds.size, favLines.size, vm.linesError)')

# FE-f 页面两处说明各恰好一个落点
FF_NOTE=$(nc_count "$BS" 'FAVORITE_LOCAL_NOTE')
FF_EMPTY=$(nc_count "$BS" 'favoriteEmptyText(favIds.size)')

# FE-g 纯层的生效文案里不许出现同步/恢复类承诺（后端 0 个收藏端点）
FG_SYNC=$(grep -n '同步\|恢复' "$FL" | grep -cvE "$NC")
FG_PROOF=$(nc_count "$FL" 'FAVORITE_LOCAL_NOTE = ')

# FE-h 用例条数 = 现读 9
FH=$(grep -c '^    @Test' "$CT")

fail=0

[ "$FA_TILE" = 1 ] || { echo "× FE-a 首页收藏那一格不是恰好一个（现 $FA_TILE）"; fail=1; }
[ "$FA_MORE" = 0 ] || { echo "× FE-a 旧的「更多功能」格又回来了（现 $FA_MORE）：它与「个人中心」是同一个 onSelectTab(3)，两个格子做同一件事"; fail=1; }
[ "$FA_TILES" = 8 ] || { echo "× FE-a 首页宫格总数不是 8（现 $FA_TILES）：入口面被改动，正对照是同一本里的 FeatureTile 计数"; fail=1; }
[ "$FB_GATE" = 7 ] || { echo "× FE-b 首页登录闸门数变了（现 $FB_GATE，预期恰好 7）：收藏那一格被加了闸门，而它读的是本机文件，与登录态无关"; fail=1; }
[ "$FB_BUS" = 3 ] || { echo "× FE-b 进实时公交的导航点不是 3（现 $FB_BUS）：收藏那一格进不去公交页了"; fail=1; }
{ [ "$FC_DEF" = 1 ] && [ "$FC_REF" = 1 ]; } || { echo "× FE-c 入口文案的唯一出处断了（定义 $FC_DEF / 引用 $FC_REF）"; fail=1; }
[ "$FC_OUTSIDE" = 0 ] || { echo "× FE-c 页面重抄了纯层字面（现 $FC_OUTSIDE）：两处文字迟早对不上；正对照 $FC_PROOF 证明扫描在看那个文件"; fail=1; }
{ [ "$FD_FAIL" = 1 ] && [ "$FD_OFF" = 1 ]; } || { echo "× FE-d 未命中那一格的两支不齐（失败支 $FD_FAIL / 下架支 $FD_OFF）：两支会被合并成一句，"没取到"就又被念成"没有收藏""; fail=1; }
[ "$FD_ERR" = 3 ] || { echo "× FE-d linesError 的落点不是 3（现 $FD_ERR：声明/赋值/读取）：失败原因没带进来，那一格只会说下架"; fail=1; }
{ [ "$FE_VISIBLE" = 1 ] && [ "$FE_NESTED" = 1 ]; } || { echo "× FE-e 整栏闸门形状变了（整栏 $FE_VISIBLE / 嵌套 $FE_NESTED，目标 1/1）"; fail=1; }
[ "$FE_UNRESOLVED" = 1 ] || { echo "× FE-e 页面不再拿三个数去判未命中（现 $FE_UNRESOLVED）"; fail=1; }
{ [ "$FF_NOTE" = 1 ] && [ "$FF_EMPTY" = 1 ]; } || { echo "× FE-f 页面说明落点不齐（本机存储那句 $FF_NOTE / 空态那句 $FF_EMPTY，目标 1/1）"; fail=1; }
[ "$FG_SYNC" = 0 ] || { echo "× FE-g 纯层生效文案里出现同步/恢复类承诺（现 $FG_SYNC，正对照 $FG_PROOF）：后端没有任何收藏接口，这句话是假的"; fail=1; }
[ "$FH" = 9 ] || { echo "× FE-h 契约用例不是 9 条（现 $FH）：下限等于现读，改了要连同本行一起改"; fail=1; }

if [ "$fail" = 0 ]; then echo "收藏入口判据通过"; else echo "收藏入口判据失败"; fi
exit "$fail"
