#!/usr/bin/env bash
# 全功能界面遍历 + 逐屏设计自检（真机插上就能跑，不产出"我看过截图"式的口头结论）
#
# 用法（Git Bash）：
#   bash tools/walk_ui.sh                     # 从当前前台界面开始遍历（深度 3，最多 60 屏）
#   bash tools/walk_ui.sh --depth 2 --max 40  # 收紧规模
#   bash tools/walk_ui.sh --from home         # 先重启 App 再走（拿可复现起点）
#   bash tools/walk_ui.sh --list-routes       # 只从源码列路由与覆盖分母，不碰设备
#
# 每屏产出（docs/ui-walk/<时间戳>/<NN>-<slug>/）：
#   dump.xml          uiautomator 原始层级
#   nodes.tsv         解析后的节点表（含祖先深度、可点总数）
#   layout.txt        tools/layout_audit.py 的几何自检（越界 / 兄弟文字重叠 / 热区<48dp）
#   screen.png        截图
#   meta.txt          从哪一屏点哪个控件到达、前台窗口、gfxinfo 掉帧
#
# 汇总：summary.md（含"到达屏数 / 路由分母 / 未验到清单"）+ coverage.txt
#
# 三条设计上的硬规矩（都是本仓踩过的坑）：
# ① 可点节点按**祖先链**判定，不是"节点自己 clickable=true"——Modifier.clickable 住在父语义节点、
#    label 是子 TextView，扁平匹配必然读出 0（上轮就这样造过一条假缺陷写进台账）。
# ② 每屏同时报 clickable 节点**总数**；某屏突然读成 0 = 抓取失败，不是"这屏没按钮"。
# ③ 危险动作不许点：命中 DENY 表的控件一律跳过并记进报告。遍历的目的是看布局，
#    不是把登录态、充值单、删除操作真的执行一遍。
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-/c/Users/袁/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
[ -x "$ADB" ] || ADB="$(command -v adb || true)"
[ -n "$ADB" ] || { echo "× 找不到 adb（用 ADB=/path/adb.exe 覆盖）"; exit 2; }
PY="$(command -v python || command -v python3 || true)"
[ -n "$PY" ] || { echo "× 找不到 python：nodes 解析与几何自检跑不了，不许把'跑不了'读成'没问题'"; exit 2; }

DEPTH=3; MAX=60; FROM=""; ROUTES_ONLY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --depth) DEPTH="$2"; shift 2;;
    --max) MAX="$2"; shift 2;;
    --from) FROM="$2"; shift 2;;
    --list-routes) ROUTES_ONLY=1; shift;;
    *) echo "未知参数：$1"; exit 2;;
  esac
done

M=app/src/main/java/com/shanxiang/buspost
PKG=com.shanxiang.buspost

list_routes() { # 路由分母从源码来，不手写清单
  grep -oE "composable\((Routes\.[A-Z_0-9]+)" "$M/ui/BusPostRoot.kt" | sed 's/composable(//' | sort -u
}

if [ "$ROUTES_ONLY" = 1 ]; then list_routes; exit 0; fi

STAMP=$(date +%Y%m%d-%H%M%S); OUT="docs/ui-walk/$STAMP"
mkdir -p "$OUT" || exit 2
cd "$ROOT" || exit 2

# 不许点的动作（命中即跳过并记账）。宁可少走一屏，也不要在遍历里真把钱花了 / 把号退了。
DENY='退出登录|注销|删除|确认收货|确认支付|立即支付|充值|立即更新|清空|解除绑定|提交并支付|签收确认'

$ADB wait-for-device || { echo "× 设备没上来"; exit 2; }
DEV=$($ADB devices | sed -n '2p' | awk '{print $1}')
[ -n "$DEV" ] || { echo "× adb devices 里没有设备"; exit 2; }
echo "[设备] $DEV  密度=$($ADB shell wm density | tr -d '\r' | tail -1)  尺寸=$($ADB shell wm size | tr -d '\r' | tail -1)"
[ -n "$FROM" ] && { $ADB shell am force-stop $PKG; $ADB shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 6; }

front() { $ADB shell dumpsys window 2>/dev/null | tr -d '\r' | grep -m1 -E "mCurrentFocus|mFocusedApp" | sed 's/.*=//'; }
hashdump() { $PY tools/ui_nodes_parse.py parse "$1" "${1%.xml}.nodes.tsv" | awk '{print $1}'; }
parse_nodes() { $PY tools/ui_nodes_parse.py parse "$1" "$2" | awk '{print $3}'; }

SEEN=$(mktemp); : > "$SEEN"
qfile="$OUT/queue.tsv"; : > "$qfile"
printf '0\tstart\t\t' >> "$qfile"

n=0; skipped=0; fails=0; CURD=0
while [ -s "$qfile" ] && [ "$n" -lt "$MAX" ]; do
  IFS=$'\t' read -r d path coords << EOF
$(tail -1 "$qfile")
EOF
  sed -i '$d' "$qfile"          # LIFO：深度优先，兄弟项留到回溯时再点，坐标才不会串屏
  [ "$d" -gt "$DEPTH" ] && continue
  if [ -n "$coords" ]; then
    # 回溯到该项所属的屏：下一项深度比当前浅多少，就按几次 Back（不然坐标会落在别的屏上）
    steps=$((CURD - d)); [ "$steps" -lt 0 ] && steps=0
    i=0; while [ "$i" -lt "$steps" ]; do $ADB shell input keyevent 4 >/dev/null 2>&1; i=$((i+1)); done
    [ "$steps" -gt 0 ] && sleep 1
    set -- $coords
    $ADB shell input tap "$1" "$2" >/dev/null 2>&1
    sleep 2                       # 给导航动画与首屏取数留时间
  fi
  CURD=$d
  $ADB shell uiautomator dump /sdcard/w.xml >/dev/null 2>&1
  $ADB pull /sdcard/w.xml "$OUT/tmp.xml" >/dev/null 2>&1
  [ -f "$OUT/tmp.xml" ] || { echo "[$n] 抓不到层级（前台=$(front)）"; fails=$((fails+1)); continue; }
  slug=$(printf '%s' "$path" | tr -c 'A-Za-z0-9_-' '_' | cut -c1-46); slug="${slug:-start}"
  dir="$OUT/$(printf '%02d' "$n")-$slug"; mkdir -p "$dir"
  cp "$OUT/tmp.xml" "$dir/dump.xml"; rm -f "$OUT/tmp.xml"
  h=$(hashdump "$dir/dump.xml" | awk '{print $1}')
  if grep -qxF "$h" "$SEEN"; then skipped=$((skipped+1)); rm -rf "$dir"; continue; fi   # 不额外按 Back：下一项的深度差会自己回溯
  echo "$h" >> "$SEEN"
  n=$((n+1))
  NC=$(parse_nodes "$dir/dump.xml" "$dir/nodes.tsv")
  "$PY" tools/layout_audit.py "$dir/dump.xml" > "$dir/layout.txt" 2>&1 || true
  $ADB exec-out screencap -p > "$dir/screen.png" 2>/dev/null
  { echo "path=$path"; echo "depth=$d"; echo "hash=$h"; echo "clickable_total=$NC"; echo "front=$(front)";
    $ADB shell dumpsys gfxinfo $PKG 2>/dev/null | tr -d '\r' | grep -m1 -E "Janky frames"; } > "$dir/meta.txt"
  over=$(grep -cE "越过屏幕|重叠|热区" "$dir/layout.txt" 2>/dev/null || echo 0)
  printf '%-3s d=%s %-46s 可点=%-3s 布局告警=%-3s %s\n' "$n" "$d" "$slug" "$NC" "$over" "$h"
  # 出队本屏的可点项（带 label、不在 DENY、尺寸像控件）—— 逻辑在已离线验过的 tools/ui_nodes_parse.py 里
  $PY tools/ui_nodes_parse.py enqueue "$dir/nodes.tsv" "$qfile" "$d" "$slug" "$DENY"
done

{ echo "# UI 遍历汇总 $(date -u +%FT%TZ)"; echo
  echo "- 到达屏数（去重后）：**$n**"; echo "- 结构重复跳过：$skipped"; echo "- 抓取失败：$fails"
  echo "- 深度上限 $DEPTH，屏数上限 $MAX，设备 $DEV"
  echo "- 路由分母（源码注册的 composable）：**$(list_routes | wc -l)**；未被遍历到达的需人工核对（遍历器只走 UI，不跳深链）"
  echo "- 每屏 \`layout.txt\` = 几何自检（越界 / 兄弟文字重叠 / 热区<48dp）；\`nodes.tsv\` = 祖先链可点判定后的节点表"
  echo "- 跳过清单（DENY 命中，故意不点）：$(printf '%s' "$DENY" | tr '|' '、')"
  echo; echo "## 逐屏告警计数"; grep -H -E "越过屏幕|重叠|热区" "$OUT"/*/layout.txt 2>/dev/null | cut -d/ -f3- | sort | uniq -c | sort -rn | head -20
} > "$OUT/summary.md"
cp "$SEEN" "$OUT/hashes.txt"; rm -f "$SEEN" "$qfile"
echo; echo "证据目录：$OUT"; sed -n '1,8p' "$OUT/summary.md"
