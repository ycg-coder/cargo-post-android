#!/usr/bin/env bash
# 表单控件判据（#283 / #288 / #289，配合审计 §86 与 D-067）
# 用法：bash tools/check_form_field_probes.sh      退出码非 0 = 登录页那三格改回来的风险没人守
#
# 这一簿的每一格都不是"读源码觉得应该这样"，而是**模拟器像素读数**定的目标值：
# 取证脚本 D:\android-dev\dev\notice_probe2.py + focus-probe.log，改前/改后同一 x/y 带对照，
# 1080x2400 / density 420（1dp = 2.625px），设备 emulator-5554，包 fb1a5fca。
# 判据写法沿用既有教训：**按内容/结构过滤，不按行号**——函数体边界按 grep 现算。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
C="$ROOT/app/src/main/java/com/shanxiang/buspost/core/ui/Components.kt"
L="$ROOT/app/src/main/java/com/shanxiang/buspost/ui/login/LoginScreen.kt"
[ -f "$C" ] && [ -f "$L" ] || { echo "找不到 Components.kt / LoginScreen.kt"; exit 2; }

# 取 @Composable 函数体：起点按 `fun 名字(` 现找，终点是下一个 `^@Composable`（不写死行号）
body() {
  local fn=$1 s n
  s=$(grep -n "^fun ${fn}(" "$C" | head -1 | cut -d: -f1)
  [ -n "$s" ] || { echo "NOFUNC"; return 1; }
  n=$(awk -v s="$s" 'NR>s && /^@Composable/{print NR; exit}' "$C")
  [ -n "$n" ] || n=$(wc -l < "$C")
  sed -n "${s},$((n - 1))p" "$C"
}
FIELD=$(body BusTextField)
BTN=$(body BusButton)

# FF-a 输入框描边：field 底色 (247,250,253) 与 paper (245,248,252) 实测只差 **1.02:1**，
#     改前同一行只有这两段值 —— 也就是"肉眼等于没有输入框"。描边是唯一的可视边界，只允许一处。
FA=$(printf '%s' "$FIELD" | grep -c '\.border(')

# FF-b 圆角口径：clip 与 border 必须共用 RoundedCornerShape(BusRadius.field)（实测 2 处）。
#     只给一处 = 直角的描边画在圆角填充外面，改前没这个洞，别把它改回来。
FB=$(printf '%s' "$FIELD" | grep -c 'RoundedCornerShape(BusRadius\.field)')

# FF-c 回执出口：LoginViewModel 有 4 个 notice 写入口，改前 **0 个渲染点**（验证码发没发用户都不知道）。
#     设备实测：点「获取」后屏幕上出现了「验证码已发送」[53,1829][239,1892]，这一格第一次说话。
FC=$(grep -c 'vm\.notice' "$L")

# FF-d 自造时效：后端配置 expire-times 是 10m，而 Repo.sendSmsCode 把响应体整个丢掉，
#     客户端从没拿到过期时间 —— 「5 分钟内有效」是工程外猜的，0 处才叫诚实。
FD=$(grep -rn "分钟内有效" "$ROOT/app/src" | wc -l | tr -d ' ')

# FF-e "整块板调淡"的禁用态：改前 board = primary.copy(alpha=0.45)，白字落在 (155,191,226) 上，
#     设备实测字与板 **1.37:1** —— 等于把按钮名字抹掉。alphaMod 这个变量名就是那次实现的化石。
FE=$(grep -c 'alphaMod' "$C")

# FF-f 聚焦态必须是**每个框自己的**状态：设备实测聚焦手机号后该行描边从 3px(199,213,224) 变 6px(46,123,191)，
#     同一屏没聚焦的验证码框仍是 3px —— 若换成共享状态，两个框会一起亮，用户就分不清在哪个框里打字。
FF=$(printf '%s' "$FIELD" | grep -c 'onFocusEvent')

# FF-g 禁用态可读：灰板 t.track + 正常灰字 t.textSecondary（实测板 (228,235,242) vs 字 (90,86,75) = **6.09:1**）。
#     两支必须都在：只留灰板不留灰字 = 白字压灰板（1.15:1），只留字不换板 = 灰字压蓝板。
FG1=$(printf '%s' "$BTN" | grep -c 'if (!enabled) t\.track')
FG2=$(printf '%s' "$BTN" | grep -c '!enabled -> t\.textSecondary')

# FF-h 孤链回归：改前「验证码登录」bounds [53,1666][208,1772] = **59.0 x 40.4dp、中心 x=130**（贴着 20dp 边距），
#     外层 Row 写的却是 Arrangement.End —— 那一行没有 fillMaxWidth，右对齐空转，肉眼就是"左贴边的小链"。
#     修后同一节点 [413,1685][667,1811] = 96.8 x **48.0dp**、中心 x=540 = 屏宽/2。
#     口径：这一格钉的是**登录页**不许再有"没有宽度支撑的 End 对齐"。真要做右对齐，先给那行 fillMaxWidth，
#     再带设备复拍来改这一格的目标值；不要去改成数 Arrangement.Center 的处数，那个数会被合法的新行顶掉。
FH=$(grep -c 'Arrangement\.End' "$L")

echo "FF-a 输入框描边处数        = $FA （目标 1：field/paper 只差 1.02:1，没描边等于没有输入框）"
echo "FF-b 圆角口径一致          = $FB （目标 2：clip 与 border 共用 BusRadius.field）"
echo "FF-c notice 渲染点         = $FC （目标 1：验证码回执唯一的出口）"
echo "FF-d 自造时效文案          = $FD （目标 0：客户端从没拿到过期时间）"
echo "FF-e alphaMod 淡出禁用态    = $FE （目标 0：实测 1.37:1 的写法不得回潮）"
echo "FF-f 逐框聚焦状态          = $FF （目标 1：只亮用户正在打字的那个框）"
echo "FF-g 禁用态灰板/灰字        = $FG1/$FG2 （目标 1/1：实测 6.09:1）"
echo "FF-h 贴边孤链旧对齐        = $FH （目标 0：整行居中、热区 48dp）"

fail=0
[ "$FA" = 1 ] || { echo "× FF-a 描边不是恰好 1 处（现 $FA）：0=输入框又看不见边界，>1=同一个框叠了两条线"; fail=1; }
[ "$FB" = 2 ] || { echo "× FF-b 圆角口径不是 2 处（现 $FB）：描边与裁剪开始各画各的形状"; fail=1; }
[ "$FC" = 1 ] || { echo "× FF-c notice 渲染点不是恰好 1 处（现 $FC）：0=回执又没人念，>1=同一句回执重复渲染"; fail=1; }
[ "$FD" = 0 ] || { echo "× FF-d 有 $FD 处自造时效：后端给的是 10m 且响应体并未解析，写在屏幕上就是假承诺"; fail=1; }
[ "$FE" = 0 ] || { echo "× FF-e alphaMod 回来了（$FE 处）：整块板调淡会把文字一起洗掉"; fail=1; }
[ "$FF" = 1 ] || { echo "× FF-f 聚焦事件不是恰好 1 处（现 $FF）：聚焦态不再属于单个输入框"; fail=1; }
[ "$FG1" = 1 ] && [ "$FG2" = 1 ] || { echo "× FF-g 禁用态两支不齐（板 $FG1 / 字 $FG2）：少任何一支都回到 <1.5:1"; fail=1; }
[ "$FH" = 0 ] || { echo "× FF-h 贴边孤链对齐回来了（$FH 处）：切换链接又变成 40dp 高的小点"; fail=1; }

if [ "$fail" = 0 ]; then echo "表单控件判据通过"; else echo "表单控件判据失败"; fi
exit "$fail"
