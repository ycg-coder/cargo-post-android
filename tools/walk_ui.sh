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

# ⚠ Git Bash / MSYS 会把形如 /sdcard/w.xml 的参数当成本地路径改写（实测变成
#   "C:/Program Files/Git/sdcard/w.xml"）⇒ uiautomator dump 写到别处、adb pull 拿不到文件，
#   整轮遍历会"每一步都成功"地什么证据都没留。关掉路径转换，别用双斜杠的土办法（漏一处就复发）。
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-/c/Users/袁/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
[ -x "$ADB" ] || ADB="$(command -v adb || true)"
[ -n "$ADB" ] || { echo "× 找不到 adb（用 ADB=/path/adb.exe 覆盖）"; exit 2; }
PY="$(command -v python || command -v python3 || true)"
[ -n "$PY" ] || { echo "× 找不到 python：nodes 解析与几何自检跑不了，不许把'跑不了'读成'没问题'"; exit 2; }

DEPTH=3; MAX=60; FROM=""; ROUTES_ONLY=0; KEEP_SHOTS=0
while [ $# -gt 0 ]; do
  case "$1" in
    --depth) DEPTH="$2"; shift 2;;
    --max) MAX="$2"; shift 2;;
    --from) FROM="$2"; shift 2;;
    --keep-shots) KEEP_SHOTS=1; shift;;
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

n=0; skipped=0; fails=0; CURD=0; LOGCURSOR=0
: > "$OUT/logfindings.txt"
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
  # 每屏的日志证据：截图只能证明"画面在动"，不能证明"功能正常"。
  # 所以每屏之间取一次 logcat 切片：本屏新增的 E 级行、崩溃/ANR、以及网络失败标记。
  $ADB logcat -d 2>/dev/null | tr -d '\r' > "$OUT/logcat-full.tmp"
  tail -n +$((LOGCURSOR + 1)) "$OUT/logcat-full.tmp" > "$dir/logcat-slice.txt"
  LOGCURSOR=$(wc -l < "$OUT/logcat-full.tmp")
  { echo "path=$path"; echo "depth=$d"; echo "hash=$h"; echo "clickable_total=$NC"; echo "front=$(front)";
    $ADB shell dumpsys gfxinfo $PKG 2>/dev/null | tr -d '\r' | grep -m1 -E "Janky frames";
    echo "log_lines=$(wc -l < "$dir/logcat-slice.txt")";
    echo "E级=$(grep -cE '^[A-Z]/.*\(| E ' "$dir/logcat-slice.txt" 2>/dev/null || echo 0)";
    echo "崩溃或ANR=$(grep -cE 'FATAL EXCEPTION|ANR in |NoSuchMethodError|DeadObject' "$dir/logcat-slice.txt" 2>/dev/null || echo 0)";
    echo "网络/后端失败=$(grep -cE 'BUS_API|HTTP [45][0-9][0-9]|系统异常|code\":5|连接失败|Unable to resolve host' "$dir/logcat-slice.txt" 2>/dev/null || echo 0)"; } > "$dir/meta.txt"
  grep -hE "FATAL EXCEPTION|ANR in |系统异常|BUS_API|code\":5" "$dir/logcat-slice.txt" 2>/dev/null | head -6 >> "$OUT/logfindings.txt"
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
# 收尾清理：设备侧与本机临时文件都不留（证据留在 $OUT 里，日志切片也留，全量 logcat 不留副本）
rm -f "$OUT/logcat-full.tmp" "$OUT/tmp.xml"
$ADB shell rm -f /sdcard/w.xml >/dev/null 2>&1
SHOTS_KB=$(du -sk "$OUT" 2>/dev/null | awk '{print $1}')
if [ "$KEEP_SHOTS" = 0 ]; then
  # 截图是这次跑里最大的一块，而判据不靠它：越界/重叠/热区都出自 dump.xml 的数字（layout.txt、nodes.tsv），
  # 功能与否出自 logcat 切片 ⇒ 结论落盘后默认删图，要人眼复核再加 --keep-shots。
  N_PNG=$(find "$OUT" -name 'screen.png' | wc -l)
  find "$OUT" -name 'screen.png' -delete
  SHOT_NOTE="已删 ${N_PNG} 张截图（判据来自 dump 的几何数字与 logcat 切片，不依赖图）；需要人眼复核用 --keep-shots"
else
  SHOT_NOTE="保留截图（--keep-shots）"
fi
{ echo; echo "- 清理：设备侧 /sdcard/w.xml 已删；本机全量 logcat 副本已删（每屏切片保留在 \`*/logcat-slice.txt\`）";
  echo "- $SHOT_NOTE";
  echo "- 汇总日志线索：\`logfindings.txt\`（$(wc -l < "$OUT/logfindings.txt" 2>/dev/null || echo 0) 行）";
  echo "- 证据目录占用：${SHOTS_KB} KB（删图后）"; } >> "$OUT/summary.md"
echo; echo "证据目录：$OUT"; sed -n '1,8p' "$OUT/summary.md"
