#!/usr/bin/env bash
# 全功能界面遍历 + 逐屏设计自检（真机插上就能跑，不产出"我看过截图"式的口头结论）
#
# 用法（Git Bash）：
#   bash tools/walk_ui.sh                     # 从当前前台界面开始遍历（深度 3，最多 60 屏）
#   bash tools/walk_ui.sh --depth 2 --max 40  # 收紧规模
#   bash tools/walk_ui.sh --from home         # 先重启 App 再走（拿可复现起点）
#   bash tools/walk_ui.sh --routes            # 逐条深链扫路由（不注入点击，见下面"两种走法"）
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
# 两种走法各有覆盖不到的地方，不是二选一：
# · 点击遍历（默认）走的是**用户真能走到的路径**，能验"这个按钮点了到底去不去"，
#   但它依赖 `adb shell input`（HyperOS 的「USB 调试（安全设置）」一掉授权，input 就 rc=0 地静默失效）；
# · 深链扫路由（--routes）用 `am start` 的 VIEW intent 逐条进页，不需要注入点击，
#   分母就是源码里注册的路由，因此**每条都有下落**；代价是页内交互没验。
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

DEPTH=3; MAX=60; FROM=""; ROUTES_ONLY=0; KEEP_SHOTS=0; SWEEP=0; MIN_NODES=8
while [ $# -gt 0 ]; do
  case "$1" in
    --depth) DEPTH="$2"; shift 2;;
    --max) MAX="$2"; shift 2;;
    --from) FROM="$2"; shift 2;;
    --keep-shots) KEEP_SHOTS=1; shift;;
    --routes) SWEEP=1; shift;;
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
# 屏幕与热区下限必须从设备读，不许写死：layout_audit 的 --screen 决定"越过屏幕"判据的分母，
# --min-touch = 48dp × density/160 决定热区判据。写死 126px（420dpi 的换算）在 440dpi 的机上
# 会把 129px 的合格控件读成不合格，也会把 1080x2340 的机读成"没越界"。读不出来就直接退出——
# "跑不了"绝不允许被读成"没问题"。
SCR=$(   $ADB shell wm size    2>/dev/null | tr -d '\r' | grep -oE '[0-9]+x[0-9]+' | tail -1)
DEN=$(   $ADB shell wm density 2>/dev/null | tr -d '\r' | grep -oE '[0-9]+'       | tail -1)
[ -n "$SCR" ] || { echo "× 读不到屏幕尺寸（wm size 空）：越界判据没有分母，本轮不跑"; exit 2; }
[ -n "$DEN" ] || { echo "× 读不到密度（wm density 空）：48dp 换不出 px，热区判据不跑"; exit 2; }
SCRW=${SCR%x*}; SCRH=${SCR#*x}
MIN_TOUCH=$(( 48 * DEN / 160 ))
echo "[判据] 屏幕=${SCRW}x${SCRH} 密度=$DEN 48dp=${MIN_TOUCH}px"
# 预检：本地包的 BASE_URL 常是 127.0.0.1:48080 —— 手机上的 127.0.0.1 是它自己，
# 没有 `adb reverse` 就会每屏都"网络不好"，于是遍历器把错误态当成内容屏一路验下去，
# 而每一屏的几何结论还是干净的（空态布局当然干净）。这种跑法比不跑更坏，必须先堵。
BASE_URL=$(grep -E '^buspost\.baseUrl=' local.properties 2>/dev/null | head -1 | cut -d= -f2-)
case "$BASE_URL" in
  *127.0.0.1*|*localhost*)
    PORT=$(printf '%s' "$BASE_URL" | grep -oE ':[0-9]{2,5}' | head -1 | tr -d ':')
    if [ -z "$PORT" ]; then
      echo "! BASE_URL 是回环地址但读不出端口，没法建 adb reverse —— 本轮网络结论不可信"
    elif ! $ADB reverse --list 2>/dev/null | tr -d '\r' | grep -q "tcp:${PORT}"; then
      echo "[预检] BASE_URL=$BASE_URL 未反向转发 ⇒ adb reverse tcp:${PORT} tcp:${PORT}"
      $ADB reverse "tcp:${PORT}" "tcp:${PORT}" >/dev/null 2>&1 \
        && echo "[预检] 已建立（USB 断开或 adb 重启会失效，重跑本脚本会再建）" \
        || echo "! [预检] adb reverse 建不起来：手机上的 127.0.0.1 就是它自己，各屏都会报网络不好"
    else
      echo "[预检] adb reverse tcp:${PORT} 已在了"
    fi
    ;;
  "") echo "! BASE_URL 未配置（App 会直说\"未配置后端地址\"）：本轮所有取数屏都不算功能验过" ;;
  *)  echo "[预检] BASE_URL=$BASE_URL（非回环）：手机需自行可达该地址，请确认同一网络/公网可访问" ;;
esac
# 定位权限：`install -r` 之后系统会把运行时权限掉回 false，而 App 侧 `pm grant` 的**回执是 0 也不代表挂上了**
# （真机实测：grant 之后 dumpsys 仍读 granted=false，页面于是永远"位置未取到"，各屏内容全是空态）。
# 所以这里授完必须回读，读不到就明说，不许把"没定位"当成"这一屏没内容"。
LOC_STATE() { $ADB shell dumpsys package $PKG 2>/dev/null | tr -d '\r' | grep -m1 "ACCESS_FINE_LOCATION: granted=" | grep -o "granted=[a-z]*"; }
if [ "$(LOC_STATE)" != "granted=true" ]; then
  $ADB shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1
  $ADB shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION >/dev/null 2>&1
fi
echo "[预检] 定位权限回读：$(LOC_STATE)（不是 granted=true 时，各屏的'附近公交/到车/天气'都会是空态）"
[ -n "$FROM" ] && { $ADB shell am force-stop $PKG; $ADB shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 6; }

front() { $ADB shell dumpsys window 2>/dev/null | tr -d '\r' | grep -m1 -E "mCurrentFocus|mFocusedApp" | sed 's/.*=//'; }
# 输入注入是否真的落到了窗口：HyperOS 关掉「USB 调试（安全设置）」后 `input tap` 回 rc=0 但事件不派发，
# 于是遍历器"每一步都成功"地停在同一屏上。这里点一次**底部手势条**（系统吞掉、不会碰任何控件），
# 再用 MIUIInput 的派发日志判活。⚠ 探针绝不点屏幕顶部：点 (3,3) 会把通知栏拉下来，
# 之后每一枪都打在通知栏上（本轮真机就栽过：判据一路"成功"，画面其实从没变过）。
input_alive() {
  $ADB shell input tap $((SCRW / 2)) $((SCRH - 2)) >/dev/null 2>&1
  sleep 1
  local n
  n=$( $ADB logcat -d -t 200 2>/dev/null | tr -d '\r' | grep -cE "MIUIInput: \[MotionEvent\]" )
  $ADB shell cmd statusbar collapse >/dev/null 2>&1
  [ "${n:-0}" -gt 0 ]
}

# ---- 一屏的取证：dump → 结构哈希 → 几何自检 → 截图 → 日志切片 → meta。两种走法共用这一份口径 ----
n=0; skipped=0; fails=0; CURD=0; LOGCURSOR=0; thin=0; badlayout=0; PREV_HASH=""
qfile=""   # 只有点击遍历会用它；先置空，别让收尾的 rm 在 --routes 模式下撞上 set -u
SEEN=$(mktemp); : > "$SEEN"
: > "$OUT/logfindings.txt"
: > "$OUT/screens.tsv"

capture() { # $1=path标签 $2=depth [$3=预先算好的结构哈希]
  local path="$1" d="$2" h="${3:-}" slug dir NC LAYOUT_OK over
  # 目录名只用 ASCII（中文经 `tr -c` 会变成一串下划线，读的人根本分不清是哪一屏）；
  # 能读的名字写在 screens.tsv 与 summary.md 里，那里放得下中文。
  slug=$(printf '%s' "$path" | tr -c 'A-Za-z0-9_-' '_' | cut -c1-46); slug="${slug:-start}"
  dir="$OUT/$(printf '%02d' "$n")-$slug"; mkdir -p "$dir"
  cp "$OUT/tmp.xml" "$dir/dump.xml"; rm -f "$OUT/tmp.xml"
  local parsed
  parsed=$($PY tools/ui_nodes_parse.py parse "$dir/dump.xml" "$dir/nodes.tsv")
  [ -n "$h" ] || h=$(printf '%s\n' "$parsed" | awk '{print $1}')
  NC=$(printf '%s\n' "$parsed" | awk '{print $3}')
  "$PY" tools/layout_audit.py "$dir/dump.xml" "--screen=${SCRW}x${SCRH}" "--min-touch=${MIN_TOUCH}" > "$dir/layout.txt" 2>&1 || true
  # layout_audit 崩了（编码、异常 dump）也会写出一个文件，而里面没有 TOTAL_ISSUES 行 ⇒
  # grep 告警计数读成 0 = "这屏布局干净"，是空集假绿。判据必须自证跑过。
  LAYOUT_OK=1; grep -q '^TOTAL_ISSUES=' "$dir/layout.txt" || LAYOUT_OK=0
  $ADB exec-out screencap -p > "$dir/screen.png" 2>/dev/null
  # 每屏的日志证据：截图只能证明"画面在动"，不能证明"功能正常"。
  # 所以每屏之间取一次 logcat 切片：本屏新增的 E 级行、崩溃/ANR、以及网络失败标记。
  $ADB logcat -d 2>/dev/null | tr -d '\r' > "$OUT/logcat-full.tmp"
  tail -n +$((LOGCURSOR + 1)) "$OUT/logcat-full.tmp" > "$dir/logcat-slice.txt"
  LOGCURSOR=$(wc -l < "$OUT/logcat-full.tmp")
  { echo "path=$path"; echo "depth=$d"; echo "hash=$h"; echo "clickable_total=$NC"; echo "front=$(front)";
    $ADB shell dumpsys gfxinfo $PKG 2>/dev/null | tr -d '\r' | grep -m1 -E "Janky frames";
    echo "log_lines=$(wc -l < "$dir/logcat-slice.txt")";
    echo "E级=$(grep -cE '^[A-Z]/.*\(| E ' "$dir/logcat-slice.txt" 2>/dev/null)";
    echo "崩溃或ANR=$(grep -cE 'FATAL EXCEPTION|ANR in |NoSuchMethodError|DeadObject' "$dir/logcat-slice.txt" 2>/dev/null)";
    echo "网络/后端失败=$(grep -cE 'BUS_API|HTTP [45][0-9][0-9]|系统异常|code\":5|连接失败|Unable to resolve host' "$dir/logcat-slice.txt" 2>/dev/null)"; } > "$dir/meta.txt"
  grep -hE "FATAL EXCEPTION|ANR in |系统异常|BUS_API|code\":5" "$dir/logcat-slice.txt" 2>/dev/null | head -6 >> "$OUT/logfindings.txt"
  # 只数真正的告警行。早先按 `越过屏幕|重叠|热区` 数，会把"无越界 / 无兄弟重叠 / 热区达标"
  # 这行**干净结论**也数成 1 —— 于是每一屏都读成"1 条布局告警"，判据反过来骗人。
  over=$(grep -c "\[LAYOUT\]" "$dir/layout.txt" 2>/dev/null); over=${over:-0}
  # 防空集假绿：闪屏/权限弹窗只有十几个节点，跑 layout_audit 一定"无越界无重叠热区达标"，
  # 但那是"在空集上为真"。低于 MIN_NODES 的屏记为 thin 不计入到达屏数，也不当布局结论。
  if [ "$NC" -lt "$MIN_NODES" ]; then
    thin=$((thin + 1))
    printf 'thin\t%s\tclickable=%s\t前台=%s\n' "$slug" "$NC" "$(front)" >> "$OUT/thin-screens.txt"
    printf '%-3s d=%s %-46s 太薄(%s 个可点，非内容屏) 不计入\n' "-" "$d" "$slug" "$NC"
  else
    [ "$LAYOUT_OK" = 1 ] || badlayout=$((badlayout + 1))
    printf '%-3s d=%s %-28s %-46s 可点=%-3s 布局告警=%-3s %s%s\n' "$n" "$d" "$slug" "$(printf '%s' "$path" | cut -c1-44)" "$NC" "$over" "$h" \
      "$([ "$LAYOUT_OK" = 1 ] || echo '  [自检未产出读数，本屏布局结论作废]')"
  fi
  # screens.tsv 是"这一屏到底是什么"的唯一可读台账（目录名是 ASCII，中文路径只在这里）
  printf '%s\td=%s\t%s\t可点=%s\t布局告警=%s\t自检跑过=%s\t%s\n' \
    "$(printf '%02d' "$n")-$slug" "$d" "$path" "$NC" "$over" "$LAYOUT_OK" "$h" >> "$OUT/screens.tsv"
  LAST_DIR="$dir"; LAST_HASH="$h"; LAST_NC="$NC"; LAST_SLUG="$slug"
}

grab() { # 抓一次设备层级到 $OUT/tmp.xml；成功 0 / 失败 1
  $ADB shell uiautomator dump /sdcard/w.xml >/dev/null 2>&1 </dev/null
  $ADB pull /sdcard/w.xml "$OUT/tmp.xml" >/dev/null 2>&1 </dev/null
  [ -s "$OUT/tmp.xml" ]
}

if [ "$SWEEP" = 1 ]; then
  # 深链扫路由：分母 = 源码里注册的路由；带 {参数} 的用样例路由跳过（要真实 id，交给点击遍历）
  echo "[模式] 深链扫路由（不注入点击）"
  ROUTE_LIST=$($PY - <<'PY'
import re, sys
src = open('app/src/main/java/com/shanxiang/buspost/nav/Routes.kt', encoding='utf-8').read()
registered = set(re.findall(r'composable\(Routes\.([A-Z_0-9]+)', open('app/src/main/java/com/shanxiang/buspost/ui/BusPostRoot.kt', encoding='utf-8').read()))
out = []
for name, val in re.findall(r'const val ([A-Z_0-9]+)\s*=\s*"([^"]*)"', src):
    if '{' in val: continue
    if name not in registered: continue
    out.append(val)
print('\n'.join(dict.fromkeys(out)))
PY
)
  [ -n "$ROUTE_LIST" ] || { echo "× 一条路由都没从源码里读出来：分母为空，本轮不跑"; exit 2; }
  # 读列表必须挂在 **fd 3** 上：`adb shell` 会吞 stdin，用 `| while read` 的话第一条之后的
  # 路由全被 adb 喝掉，一轮只扫到一屏还"每步都成功"（本轮实测就栽在这，27 条只出 1 条）。
  # Windows 版 python 的 stdout 带 \r\n，不剥掉就成了 `login^M`，深链串跟着被污染。
  while IFS= read -r r <&3; do
    r=${r%$'\r'}
    [ -n "$r" ] || continue
    n=$((n+1))
    $ADB shell am start -a android.intent.action.VIEW -d "buspost://app/$r" -p $PKG >/dev/null 2>&1
    sleep 3
    if ! grab; then echo "[$n] $r 抓不到层级（前台=$(front)）"; fails=$((fails+1)); continue; fi
    capture "route/$r" 1
    # 深链被闸门挡下时 App 什么都不做（`deepLinkTarget` 对 shell 路由与非司机进司机域都回 null），
    # 画面还是上一屏 ⇒ 不能把它记成"这一页验过了"。与前一屏结构相同 = 没导航，单独记账。
    if [ -n "$PREV_HASH" ] && [ "$LAST_HASH" = "$PREV_HASH" ]; then
      printf '%s\t与上一屏同结构：深链未产生导航（闸门拒绝或已在该页）\n' "$r" >> "$OUT/no-move.txt"
      echo "     ↑ 未导航：画面与上一屏完全相同（闸门拒绝 / 已在该页）"
    elif grep -qxF "$LAST_HASH" "$SEEN"; then
      echo "     ↑ 与更早某屏结构相同（同名页复用）"
    fi
    PREV_HASH="$LAST_HASH"
    echo "$LAST_HASH" >> "$SEEN"
  done 3< <(printf '%s\n' "$ROUTE_LIST")
  { echo "# UI 遍历汇总 $(date -u +%FT%TZ)"; echo
    echo "- 模式：**深链扫路由**（am start VIEW，不注入点击）"; echo "- 扫过：**$n** 条路由"; echo "- 抓取失败：$fails"
    echo "- 屏幕 ${SCRW}x${SCRH}，密度 $DEN，48dp=${MIN_TOUCH}px，设备 $DEV"
    echo "- 路由分母（源码注册的 composable）：**$(list_routes | wc -l)**（带 {参数} 的路由这里不进，需要真实 id 的交给点击遍历）"
    echo "- 每屏 \`layout.txt\` = 几何自检（越界 / 兄弟文字重叠 / 热区<${MIN_TOUCH}px）"
    echo; echo "## 逐屏清单（目录 ← 到达路径）"; cat "$OUT/screens.tsv" 2>/dev/null
    echo "- **未产生导航的路由**（闸门拒绝 / 已在该页，不算验过）：$(wc -l < "$OUT/no-move.txt" 2>/dev/null || echo 0) 条"
    [ -s "$OUT/no-move.txt" ] && { echo; echo '```'; cat "$OUT/no-move.txt"; echo '```'; }
    echo; echo "## 告警类型去重计数（坐标与像素已归一）"
    grep -H "\[LAYOUT\]" "$OUT"/*/layout.txt 2>/dev/null \
      | sed "s#^$OUT/##; s/\[[0-9-]*,[0-9-]*\]//g; s/[0-9]\+/N/g" | sort | uniq -c | sort -rn | head -30
  } > "$OUT/summary.md"
else
  qfile="$OUT/queue.tsv"; : > "$qfile"
  printf '0\tstart\t\t' >> "$qfile"
  if ! input_alive; then
    echo "× 输入注入没落到窗口（HyperOS「USB 调试（安全设置）」多半掉了）：点击遍历会假成功"
    echo "  兜底：bash tools/walk_ui.sh --routes 走深链扫路由（不需要注入点击）"
    exit 3
  fi
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
    grab || { echo "[$n] 抓不到层级（前台=$(front)）"; fails=$((fails+1)); continue; }
    # 去重要在**建目录之前**判：先建目录再删，等于把同一屏的截图/日志写一遍又抹掉，
    # 而计数里已经把这一屏算进"到达屏数"（上一版就这样把 26 屏报成 26 个不同页面）。
    local_h=$($PY tools/ui_nodes_parse.py parse "$OUT/tmp.xml" "$OUT/tmp-nodes.tsv" | awk '{print $1}')
    if grep -qxF "$local_h" "$SEEN"; then skipped=$((skipped+1)); continue; fi
    echo "$local_h" >> "$SEEN"
    n=$((n+1))
    capture "$path" "$d" "$local_h"
    # 出队本屏的可点项（带 label、不在 DENY、尺寸像控件）—— 逻辑在已离线验过的 tools/ui_nodes_parse.py 里
    $PY tools/ui_nodes_parse.py enqueue "$LAST_DIR/nodes.tsv" "$qfile" "$d" "$LAST_SLUG" "$DENY"
  done

  { echo "# UI 遍历汇总 $(date -u +%FT%TZ)"; echo
    echo "- 模式：**点击遍历**"; echo "- 到达屏数（去重后）：**$n**"; echo "- 结构重复跳过：$skipped"; echo "- 抓取失败：$fails"
    echo "- 薄屏（可点<\$MIN_NODES=$MIN_NODES，闪屏/弹窗，不计入布局结论）：$thin"
    echo "- 自检未产出读数的屏（布局结论作废，必须查 \`layout.txt\` 头部）：**$badlayout**"
    echo "- 深度上限 $DEPTH，屏数上限 $MAX，设备 $DEV，屏幕 ${SCRW}x${SCRH}，密度 $DEN，48dp=${MIN_TOUCH}px"
    echo "- 路由分母（源码注册的 composable）：**$(list_routes | wc -l)**；未被遍历到达的需人工核对（遍历器只走 UI，不跳深链）"
    echo "- 每屏 \`layout.txt\` = 几何自检（越界 / 兄弟文字重叠 / 热区<${MIN_TOUCH}px）；\`nodes.tsv\` = 祖先链可点判定后的节点表"
    echo "- 跳过清单（DENY 命中，故意不点）：$(printf '%s' "$DENY" | tr '|' '、')"
    echo; echo "## 告警类型去重计数（坐标与像素已归一，不然每行都独一无二、统计不出复现度）"
    grep -H "\[LAYOUT\]" "$OUT"/*/layout.txt 2>/dev/null \
      | sed "s#^$OUT/##; s/\[[0-9-]*,[0-9-]*\]//g; s/[0-9]\+/N/g" | sort | uniq -c | sort -rn | head -30
    echo; echo "## 逐屏告警条数"
    for f in "$OUT"/*/layout.txt; do c=$(grep -c "\[LAYOUT\]" "$f" 2>/dev/null)
      [ "$c" = 0 ] || echo "$c $(echo "$f" | sed "s#^$OUT/##; s#/layout.txt##")"; done | sort -rn | head -20
  } > "$OUT/summary.md"
fi
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
echo; echo "证据目录：$OUT"; sed -n '1,10p' "$OUT/summary.md"
