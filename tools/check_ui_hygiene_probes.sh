#!/usr/bin/env bash
# 界面性能与触控形状判据（缺陷 #77，配 tools/ui_hygiene_negative_controls.sh）
# 用法：bash tools/check_ui_hygiene_probes.sh      退出码非 0 = 这一族被拆了
#
# 这一本管五件"看着没事、用着有事"的事，全部是 v86/v95 真机那两轮翻出来的形状：
#   UH-a/b 图标热区：页面里 `Modifier.size(18.dp).clickable { … }` 这种"要点 6mm 见方"的可点块
#          （刷新、清空搜索、编辑、删除…）→ 全部走 `BusIconTap`：视觉不放大，命中区撑到令牌下限。
#   UH-c   懒列表 key：没有 key 的行在排序/筛选后会整行重建，滚动状态与入场动画全部错位。
#   UH-d   入场动画：`AnimatedEnter` 的"已入场"若用 `remember`，列表项随滚动进出组合就会被丢掉
#          ⇒ 每滚回来重播一遍阶梯动画（订单列表实测）。必须 `rememberSaveable`。
#   UH-e   收集器：在 `item { … }` 里就地 `flow.collectAsStateWithLifecycle().value` ⇒ 那一格每次
#          进组合挂一串收集器（工作台报告卡曾经九条）。一个流一个收集器，收在屏幕顶层。
#   UH-f   MapView 释放：选点页自己 `MapView(ctx).apply{onCreate;onResume}` 而从不 onPause/onDestroy
#          ⇒ 高德那块 SurfaceView 每开一次漏一份。
#
# 写法沿用本仓规矩：先验文件存在；"目标 0"一律配同机制反证；窗口型判定交给 python（grep 数不到跨行链）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

M=app/src/main/java/com/shanxiang/buspost
PRIM=$M/core/ui/Primitives.kt
PICK=$M/ui/user/MapPointPickerScreen.kt
BUSMAP=$M/ui/common/BusMap.kt
WB=$M/ui/driver/WorkbenchScreen.kt
for f in "$M" "$PRIM" "$PICK" "$BUSMAP" "$WB"; do
    [ -e "$f" ] || { echo "× 判据输入缺失：$f（扫描面塌了，本轮读数不采信）"; exit 2; }
done

cnt() { grep -c "$2" "$1"; }

# UH-a `BusIconTap` 原语在位（定义 1）且页面真的在用它（≥7：两处清空搜索、刷新×2、编辑、删除、性别… ）
UHA_DEF=$(grep -c 'fun BusIconTap(' "$PRIM")
UHA_USE=$(grep -rn 'BusIconTap(' "$M/ui" --include=*.kt | wc -l | tr -d ' ')

# UH-b 页面里"小图标直接 clickable 且命中区没撑"的处数（python 按 ±6 行窗口判 modifier 链）
UHB=$(python - "$M" <<'PY'
import io, os, re, sys
root = sys.argv[1]
skip = os.path.join(root, "core", "ui")
SIZE = re.compile(r"\.size\((\d+(?:\.\d+)?)\.dp\)")
n = 0
for dp, _, fs in os.walk(root):
    if dp.startswith(skip):
        continue
    for fn in sorted(fs):
        if not fn.endswith(".kt"):
            continue
        src = io.open(os.path.join(dp, fn), encoding="utf-8", errors="replace").read().split("\n")
        for i, l in enumerate(src):
            if ".clickable" not in l or l.strip().startswith(("//", "*")):
                continue
            win = "\n".join(src[max(0, i - 6):i + 4])
            if "defaultMinSize" in win or "BusIconTap" in win:
                continue
            m = SIZE.search(win)
            if m and 0 < float(m.group(1)) < 48:
                n += 1
print(n)
PY
)
# 反证：同一套"撑命中区"的写法在核心层数得动（core/ui 里 defaultMinSize ≥10 处），
# 否则 UH-b 那个 0 只是"这套规矩在仓里根本不存在"。
UHB_PROOF=$(grep -rn 'defaultMinSize' "$M/core/ui" --include=*.kt | wc -l | tr -d ' ')

# UH-c 懒列表**全部**带 key：带 key 的处数必须等于调用点总数（少一处就是有人新加了一列不带 key 的）
#      反证 = 调用点总数本身 ≥10（否则"相等"可以是两个 0，什么也没判）
UHC_KEYED=$(grep -rnE '^\s*(items|itemsIndexed)\(' "$M/ui" --include=*.kt | grep -c 'key =')
UHC_ALL=$(grep -rnE '^\s*(items|itemsIndexed)\(' "$M/ui" --include=*.kt | wc -l | tr -d ' ')

# UH-d 入场动画的"已入场"必须活过列表回收
UHD_SAVE=$(grep -c 'rememberSaveable { mutableStateOf(false) }' "$PRIM")
UHD_OLD=$(grep -c 'var visible by remember { mutableStateOf(false) }' "$PRIM")

# UH-e 收集器不许长在列表项里
UHE_INLINE=$(grep -rn 'collectAsStateWithLifecycle()\.value' "$M" --include=*.kt | wc -l | tr -d ' ')
UHE_TOP=$(grep -rn 'collectAsStateWithLifecycle()' "$M" --include=*.kt | wc -l | tr -d ' ')

# UH-f MapView 释放：选点页自己收口，容器层也在位
UFF_PICK=$(cnt "$PICK" '?.onDestroy()')
UFF_BUS=$(cnt "$BUSMAP" 'mapView?.onDestroy()')

echo "UH-a BusIconTap 定义/页面使用       = $UHA_DEF/$UHA_USE （目标 1/≥7）"
echo "UH-b 页面小图标裸 clickable        = $UHB （目标 0；反证 core/ui defaultMinSize=$UHB_PROOF，目标 ≥10）"
echo "UH-c 懒列表带 key / 全部调用点      = $UHC_KEYED/$UHC_ALL （目标 两者相等 且 总数 ≥10）"
echo "UH-d 入场 rememberSaveable / 旧形状  = $UHD_SAVE/$UHD_OLD （目标 1/0）"
echo "UH-e 项内就地收流 / 顶层收集器总数   = $UHE_INLINE/$UHE_TOP （目标 0/≥20）"
echo "UH-f 选点页 onDestroy / BusMap 同款  = $UFF_PICK/$UFF_BUS （目标 1/≥1）"

fail=0
[ "$UHA_DEF" = 1 ] || { echo "× UH-a 图标热区原语不唯一或缺席（现 $UHA_DEF）：各屏又会各画一个点不中的小图标"; fail=1; }
[ "$UHA_USE" -ge 7 ] || { echo "× UH-a 页面没在用 BusIconTap（现 $UHA_USE 处）：原语成了摆设"; fail=1; }
[ "$UHB" = 0 ] || { echo "× UH-b 页面里有 $UHB 处小图标直接 clickable 且命中区没撑到下限：指尖点不中，还会误触旁边那颗"; fail=1; }
[ "$UHB_PROOF" -ge 10 ] || { echo "× UH-b 反证掉了（core/ui 的 defaultMinSize=$UHB_PROOF）：那个 0 可能是这套写法整个不在了"; fail=1; }
[ "$UHC_ALL" -ge 10 ] || { echo "× UH-c 反证掉了（懒列表调用点总数=$UHC_ALL，目标 ≥10）：相等可以是两个 0，那什么都没判"; fail=1; }
[ "$UHC_KEYED" = "$UHC_ALL" ] || { echo "× UH-c 有 $((UHC_ALL - UHC_KEYED)) 处懒列表没有 key（$UHC_KEYED/$UHC_ALL）：排序、筛选或整列替换后按位置复用，选中态与入场动画都会错位"; fail=1; }
[ "$UHD_SAVE" = 1 ] || { echo "× UH-d 入场动画的'已入场'不再是 rememberSaveable（现 $UHD_SAVE）：滚回来就重播"; fail=1; }
[ "$UHD_OLD" = 0 ] || { echo "× UH-d 旧的 \`remember\` 形状回来了（现 $UHD_OLD 处）：列表回收会把它丢掉，动画随之重播"; fail=1; }
[ "$UHE_INLINE" = 0 ] || { echo "× UH-e 有 $UHE_INLINE 处在列表项/参数里就地 collect 流：那一格每进一次组合就多挂一批收集器"; fail=1; }
[ "$UHE_TOP" -ge 20 ] || { echo "× UH-e 反证掉了（全工程顶层收集器=$UHE_TOP）：那个 0 可能是收集器整套没了"; fail=1; }
[ "$UFF_PICK" = 1 ] || { echo "× UH-f 选点页不再 onDestroy 那张 MapView（现 $UFF_PICK）：高德的 SurfaceView 每开一次漏一份"; fail=1; }
[ "$UFF_BUS" -ge 1 ] || { echo "× UH-f 反证掉了（BusMap 的释放=$UFF_BUS）：上面那个 1 可能只是文件写错了名"; fail=1; }

if [ "$fail" = 0 ]; then echo "界面性能与触控形状判据通过"; else echo "界面性能与触控形状判据失败"; fi
exit "$fail"
