#!/usr/bin/env bash
# 玻璃拟态铺开的判据（配 tools/glass_surface_negative_controls.sh）。
# 用户口径："对齐 2026 年 app 趋势，拟态玻璃都可以用上，让我们 app 稍微高级点又实用"。
#
# "又实用"这一半是本簿的存在理由：玻璃感只给**浮起来的面**，内容卡维持不透明。
#   GL-a 高光那一道只有一份实现（`glassSheen`），浮面与内容卡各就各位；
#   GL-b 浮面（胶囊 / 顶栏 / 通栏条 / 到站格 / 选点面板）用 `glassmorphic` 的调用点恰好 5；
#   GL-c 内容卡 `BusCard` 的底面仍是不带 alpha 的实色（`.background(bg.value, shape)`），
#        它只许多吃那一道 1dp 高光，不许吃半透明 —— 正文压在半透明上就要赌对比度；
#   GL-d 透明度那两个数（0.88 / 0.93）只许写在 `glassmorphic` 一份里，页面不许各自抄 alpha；
#   GL-e 深色模式的高光比浅色的弱（0.16 vs 0.80）：白线在深底上是一条划痕，不是玻璃。
#
# 用法：bash tools/check_glass_surface_probes.sh
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
FB=$M/core/ui/FluidBackground.kt
CP=$M/core/ui/Components.kt
CN=$M/ui/common/CapsuleNav.kt
TB=$M/ui/driver/DriverBits.kt
GD=$M/ui/driver/DriverGuideScreen.kt
PK=$M/ui/user/MapPointPickerScreen.kt
CMT='(^[^:]+:[0-9]+:[[:space:]]*(//|\*|/\*))|(^[0-9]+:[[:space:]]*(//|\*|/\*))'

for f in "$FB" "$CP" "$CN" "$TB" "$GD" "$PK"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

one_count() { grep -nE "$1" "$2" 2>/dev/null | grep -vE "$CMT" | wc -l | tr -d ' '; }

# GL-a 一份实现 + 用量
GA_DEF=$(one_count '^fun Modifier\.glassSheen\(' "$FB")
GA_USE=$(grep -rnE '\.glassSheen\(' "$M" --include=*.kt 2>/dev/null | grep -vE "$CMT" | grep -v 'fun Modifier.glassSheen' | wc -l | tr -d ' ')

# GL-b 浮面用 glassmorphic 的调用点（定义那一处不算）
GB_USE=$(grep -rnE '\.glassmorphic\(' "$M" --include=*.kt 2>/dev/null | grep -vE "$CMT" | grep -v 'fun Modifier.glassmorphic' | wc -l | tr -d ' ')
GB_CAPSULE=$(one_count '\.glassmorphic\(' "$CN")
GB_TOPBAR=$(one_count '\.glassmorphic\(' "$CP")
GB_STICKY=$(one_count '\.glassmorphic\(' "$TB")
GB_ARRIVED=$(one_count '\.glassmorphic\(' "$GD")
GB_PICKER=$(one_count '\.glassmorphic\(' "$PK")

# GL-c 内容卡维持实色底
GC_FILL=$(one_count '\.background\(bg\.value, shape\)' "$CP")
GC_SHEEN_CARD=$(one_count '\.glassSheen\(t, shape\)' "$CP")

# GL-d 透明度只写在 FluidBackground 这一份里（里面三处：玻璃面两档 + 高光两档）
# ⚠ 认的是**那两对数值**，不是"任何带 isDark 的 alpha"：`MapCanvas.kt` 里也有两处按主题分档的 alpha，
#   那是自绘地图的网格线与路线高光，跟表面玻璃没关系 —— 用形状去认就会把它误判成抄档（第一轮就误在这里）。
GD_ALPHA=$(one_count 'alpha = if \(tokens\.isDark\)' "$FB")
GD_OUT=$(grep -rnE '0\.88f else 0\.93f|0\.16f else 0\.80f' "$M" --include=*.kt 2>/dev/null | grep -vE "$CMT" | grep -v 'core/ui/FluidBackground.kt' | wc -l | tr -d ' ')
GD_LEAK=$(grep -rnE 'glassmorphic\([^)]*alpha' "$M" --include=*.kt 2>/dev/null | grep -vE "$CMT" | wc -l | tr -d ' ')

# GL-e 深浅两档的高光值
GE_LIGHT=$(one_count '0\.16f else 0\.80f' "$FB")

fail=0

[ "$GA_DEF" = 1 ] || { echo "× GL-a 边缘高光不是恰好一份实现（现 $GA_DEF）：两处实现就会长成两套厚薄"; fail=1; }
[ "$GA_USE" = 4 ] || { echo "× GL-a 吃到高光的面不是 4（现 $GA_USE：内容卡 / 通栏条 / 到站格 / 选点面板）：铺开铺漏了这一格就红"; fail=1; }
[ "$GB_USE" = 5 ] || { echo "× GL-b 用玻璃浮面的地方不是 5（现 $GB_USE）：浮面与内容面要分得开，多一处就是有人把内容卡也调成半透明"; fail=1; }
{ [ "$GB_CAPSULE" = 1 ] && [ "$GB_TOPBAR" = 1 ] && [ "$GB_STICKY" = 1 ] && [ "$GB_ARRIVED" = 1 ] && [ "$GB_PICKER" = 1 ]; } || { echo "× GL-b 五个浮面缺一个（胶囊 $GB_CAPSULE 顶栏 $GB_TOPBAR 通栏 $GB_STICKY 到站 $GB_ARRIVED 选点 $GB_PICKER）"; fail=1; }
[ "$GC_FILL" = 1 ] || { echo "× GL-c 内容卡的实色底被改掉了（现 $GC_FILL）：卡片一旦半透明，正文就要跟背后的流体背景赌对比度"; fail=1; }
[ "$GC_SHEEN_CARD" = 1 ] || { echo "× GL-c 内容卡没吃到那道高光（现 $GC_SHEEN_CARD）：玻璃感靠边缘，不靠把卡色调淡"; fail=1; }
[ "$GD_ALPHA" = 3 ] || { echo "× GL-d 透明度档位不是那三处（现 $GD_ALPHA：玻璃面 2 + 高光 1）：改档要连同本行一起改"; fail=1; }
[ "$GD_OUT" = 0 ] || { echo "× GL-d 有 $GD_OUT 处在 FluidBackground 之外抄了按主题分档的 alpha：质感要单一真值"; fail=1; }
[ "$GD_LEAK" = 0 ] || { echo "× GL-d 有页面在调用点抄 alpha（现 $GD_LEAK）"; fail=1; }
[ "$GE_LIGHT" = 1 ] || { echo "× GL-e 深浅两档高光压成了同一个值（现 $GE_LIGHT）：白线在深色主题上就是划痕"; fail=1; }

if [ "$fail" = 0 ]; then
  echo "玻璃表面判据通过（实现 $GA_DEF/高光面 $GA_USE/浮面 $GB_USE/内容卡实色 $GC_FILL/alpha 单源 $GD_ALPHA）"
else
  echo "玻璃表面判据失败"
fi
exit "$fail"
