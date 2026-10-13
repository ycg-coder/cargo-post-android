#!/usr/bin/env bash
# #43 启动图标（方向 2：车头 + 弧线）的结构判据（配 tools/launcher_icon_negative_controls.sh）
# 管七件事：三层接线齐且背景不再是纯色、渐变两端是选定那两色、前景不烤圆角也不铺满整幅、
# 安全区机制（group 缩放 + 支点）在册、前景只用一种白色（这条同时是"monochrome 可以复用前景"的依据）、
# 旧的深蓝 launcher 底色不再被引用、以及两件**故意没做**的事（各密度位图、商店 1024 图）不许被说成做了。
# 用法：bash tools/check_launcher_icon_probes.sh        退出码非 0 = 这一格被拆了
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main
AD=$M/res/mipmap-anydpi-v26/ic_launcher.xml
FG=$M/res/drawable/ic_launcher_foreground.xml
BG=$M/res/drawable/ic_launcher_background.xml
CO=$M/res/values/colors.xml
SP=$M/java/com/shanxiang/buspost/core/ui/SplashOverlay.kt
RD=docs/design/icon-candidates/README.md

for f in "$AD" "$FG" "$BG" "$CO" "$SP" "$RD"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

# IC-a 三层接线
AA_BG=$(grep -c '<background android:drawable="@drawable/ic_launcher_background"' "$AD")
AA_FG=$(grep -c '<foreground android:drawable="@drawable/ic_launcher_foreground"' "$AD")
AA_MONO=$(grep -c '<monochrome android:drawable="@drawable/ic_launcher_foreground"' "$AD")
AA_OLD=$(grep -c '@color/bus_launcher_bg' "$AD")

# IC-b 背景层是满幅线性渐变，两端是选定那两色
AB_GRAD=$(grep -c 'android:type="linear"' "$BG")
AB_START=$(grep -c 'startColor="#FF0E5A52"' "$BG")
AB_END=$(grep -c 'endColor="#FF17A07A"' "$BG")
AB_FULL=$(grep -c 'M0,0h108v108h-108z' "$BG")

# IC-c 前景不烤圆角、不铺满整幅（圆角与遮罩是系统的事）：
#      "烤底板"的真实形状是一圈半径 >=16 的大圆角弧（画布 108 的圆角底板必然这么大），
#      本作品自己的圆角最大只有 9 ⇒ 这条既挡底板也不误伤图形。
AC_ROUNDED=$(grep -cE 'a1[6-9],|a[2-9][0-9],' "$FG")
AC_FULLRECT=$(grep -c 'M0,0h108' "$FG")
AC_PROOF=$(grep -c 'fillColor="#FFFFFFFF"' "$FG")

# IC-d 安全区机制：整组按 0.86 缩放、支点在画布中心（数值核过：最远点距心 32.51 ≤ 33）
AD_SCALE=$(grep -c 'android:scaleX="0.86"' "$FG")
AD_PIVOT=$(grep -c 'android:pivotX="54"' "$FG")
AD_PIVOTY=$(grep -c 'android:pivotY="54"' "$FG")

# IC-e 前景只有一种白色 ⇒ 镂空靠 evenOdd（透明洞），不是画成背景色；这是 monochrome 复用前景的依据
AE_WHITE=$(grep -o 'fillColor="#[^"]*"' "$FG" | grep -vc '#FFFFFFFF')
AE_EVEN=$(grep -c 'fillType="evenOdd"' "$FG")

# IC-f 旧的深蓝 launcher 底色彻底退役（生效引用为 0；App 主色板另有 bus_primary，不受影响）
AF_OLD=$(grep -rn 'android:drawable="@color/bus_launcher_bg"\|name="bus_launcher_bg"' "$M" | wc -l)
AF_PROOF=$(grep -c 'name="bus_primary"' "$CO")

# IC-g 两件故意没做的事：各密度位图（minSdk 26 不需要）与商店 1024 图（还没有）
AG_DIRS=$(ls "$M/res" | grep -c '^mipmap-')
AG_PNG=$(find "$M/res" -name 'ic_launcher*.png' | wc -l)
AG_README=$(grep -c '落地清单' "$RD")

fail=0

{ [ "$AA_BG" = 1 ] && [ "$AA_FG" = 1 ] && [ "$AA_MONO" = 1 ]; } || { echo "× IC-a 自适应图标三层接线不齐（底 $AA_BG / 前 $AA_FG / 单色 $AA_MONO，目标 1/1/1）"; fail=1; }
[ "$AA_OLD" = 0 ] || { echo "× IC-a 自适应图标又把背景指回那片深蓝纯色（现 $AA_OLD）"; fail=1; }
{ [ "$AB_GRAD" = 1 ] && [ "$AB_START" = 1 ] && [ "$AB_END" = 1 ] && [ "$AB_FULL" = 1 ]; } || { echo "× IC-b 背景层不是那一条满幅线性渐变（linear $AB_GRAD / 两端 $AB_START+$AB_END / 满幅 $AB_FULL）"; fail=1; }
{ [ "$AC_ROUNDED" = 0 ] && [ "$AC_FULLRECT" = 0 ]; } || { echo "× IC-c 前景被烤进了底板形状（圆角 $AC_ROUNDED / 整幅矩形 $AC_FULLRECT）：遮罩是系统的事，画进资源里会被二次裁切；正对照白色路径 $AC_PROOF 条证明扫描在看这个文件"; fail=1; }
{ [ "$AD_SCALE" = 1 ] && [ "$AD_PIVOT" = 1 ] && [ "$AD_PIVOTY" = 1 ]; } || { echo "× IC-d 安全区机制没了（缩放 $AD_SCALE / 支点 $AD_PIVOT,$AD_PIVOTY）：站点圆点与弧线末端会被圆形遮罩切掉"; fail=1; }
{ [ "$AE_WHITE" = 0 ] && [ "$AE_EVEN" = 1 ]; } || { echo "× IC-e 前景不再只有一种白色（异色 $AE_WHITE / evenOdd $AE_EVEN）：一旦有第二层不透明色，monochrome 复用前景就会糊成一坨（旧前景正是这个形状）"; fail=1; }
{ [ "$AF_OLD" = 0 ] && [ "$AF_PROOF" = 1 ]; } || { echo "× IC-f 旧 launcher 底色还在被引用（现 $AF_OLD，正对照 bus_primary $AF_PROOF）"; fail=1; }
{ [ "$AG_DIRS" = 1 ] && [ "$AG_PNG" = 0 ] && [ "$AG_README" = 2 ]; } || { echo "× IC-g 两件故意没做的事被改动（mipmap 目录 $AG_DIRS / 位图 $AG_PNG / README 落地清单 $AG_README，目标 1/0/2）：minSdk 26 不需要各密度位图，商店 1024 图还没有——都不许被写成已完成"; fail=1; }

if [ "$fail" = 0 ]; then echo "启动图标判据通过"; else echo "启动图标判据失败"; fi
exit "$fail"
