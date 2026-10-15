#!/usr/bin/env bash
# 线路详情底图判据（LM-a…LM-f）的负控制：逐条破口 → 确认 check_detail_map_probes.sh exit 1 且报的就是这一条。
# 只在临时目录副本上动手，工程真身不受影响（纯文本判据，不需要编译 ⇒ 可与其他脚本并行）。
# 用法：bash tools/detail_map_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3

PKG=com/shanxiang/buspost
PR="$SRC/tools/check_detail_map_probes.sh"
SC=/tmp/lm_neg.$$
mkdir -p "$SC/app/src/main"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
B="$SC/app/src/main/java/$PKG/ui/user/BusScreen.kt"
[ -f "$B" ] || { echo "✘ 副本没建起来（$B），本轮负控全部作废"; exit 2; }
CLEAN=/tmp/lm_neg_clean.$$; cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

ctl() { # $1=判据编号 $2=说明
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

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | tail -1)"
[ "$base_code" = 0 ] || { echo "✘ 基线未通过，负控结果一律不采信"; exit 2; }

# 1 详情页退回"只画那张自绘 canvas"（底图容器整个摘掉）→ LM-a
swap "$B" '                    BusMap(
                        markers = detailMarkers,' '                    BusMapX(
                        markers = detailMarkers,'
ctl LM-a "详情页的底图容器被摘掉：这一格又只剩示意图"

# 2 兜底分支的判据被换成常量 → LM-c
swap "$B" '                if (detailMapFailed) {' '                if (false) {'
ctl LM-c "详情自绘不再挂在失败态上：底图与示意图的关系又含糊了"

# 3 换线不再复位失败态（退了再也回不去）→ LM-b
swap "$B" '    LaunchedEffect(routeId) { detailMapFailed = false }' '    // 复位摘掉'
ctl LM-b "换一条线路不再给高德重试机会：一次失败永久停在示意图"

# 4 反证自身：把**主图**那支的兜底摘掉 → LM-d 必须红，否则 LM-c 的那个 1 只是 grep 写宽了
#    （第一版这条破口打的是"删掉那行注释"，结果 -A 2 的窗口照样数到 RouteMap ⇒ 假失效；
#     反证的破口必须打在**被判的那个形状**上，不是打在它旁边的注释上。）
swap "$B" '                    RouteMap(
                        polyline = vm.polyline,
                        stations = line?.stations' '                    RouteMapX(
                        polyline = vm.polyline,
                        stations = line?.stations'
ctl LM-d "反证掉了：主图那支数不出兜底形状，LM-c 的 1 就不再可信"

# 5 又开第三格"承诺地图却只画 canvas" → LM-e
swap "$B" '                if (detailMapFailed) {' '                RouteMap(
                    polyline = vm.polyline,
                    stations = line.stations,
                    vehicles = vm.vehiclesOf(line),
                    me = vm.me,
                    height = 120.dp,
                )

                if (detailMapFailed) {'
ctl LM-e "页面里多出第三处自绘调用：那一格没有底图，也没有失败态可言"

# 6 详情页那句折线口径被撤 → LM-f
swap "$B" '                    vm.polylineSubtitle(),
                    color = t.textTertiary,' '                    "示意图",
                    color = t.textTertiary,'
ctl LM-f "详情页不再报'还没问/正在问/这次没问到/后端回了空'：四种情况在底图上长成一个样"

echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
