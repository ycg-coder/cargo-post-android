#!/usr/bin/env bash
# 界面性能与触控形状判据（UH-a…UH-g）的负控制：逐条破口 → 确认 exit 1 且报的就是这一条。
# 只在临时目录副本上动手，工程真身不受影响（纯文本判据，不需要编译 ⇒ 可与其他脚本并行）。
# 用法：bash tools/ui_hygiene_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3

PKG=com/shanxiang/buspost
PR="$SRC/tools/check_ui_hygiene_probes.sh"
SC=/tmp/uh_neg.$$
mkdir -p "$SC/app/src/main"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
A="$SC/app/src/main/java/$PKG"
PRIM="$A/core/ui/Primitives.kt"
COREUI="$A/core/ui"
PICK="$A/ui/user/MapPointPickerScreen.kt"
WB="$A/ui/driver/WorkbenchScreen.kt"
GOODS="$A/ui/user/GoodsScreen.kt"
MINE="$A/ui/user/MinePages.kt"
DBITS="$A/ui/driver/DriverBits.kt"
for f in "$PRIM" "$PICK" "$WB" "$GOODS" "$MINE" "$DBITS"; do
    [ -f "$f" ] || { echo "✘ 副本缺 $f，下面每一条都会假绿"; exit 2; }
done
CLEAN=/tmp/uh_neg_clean.$$; cp -r "$SC" "$CLEAN"
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
assert old not in new or old == new, "SELF-MATCH: 替换文本里不许带锚点原文"
open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
PY
}
swapall() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swapall 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
open(p, 'w', encoding='utf-8').write(s.replace(old, new))
PY
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | tail -1)"
[ "$base_code" = 0 ] || { echo "✘ 基线未通过，负控结果一律不采信"; exit 2; }

# 1 原语被改名（页面那 7 处调用跟着红不了，但"这一族只有唯一出处"这条就断了）→ UH-a
#    ⚠ 替换文本不许含被判图案 `fun BusIconTap(` —— 那会让破口自己顶掉自己（swap 里有 SELF-MATCH 断言）。
swap "$PRIM" 'fun BusIconTap(' 'fun IconTapBoxRenamed('
ctl UH-a "图标热区原语改名：各屏又会各自画一个点不中的小图标"

# 2 页面里长出一颗 18dp 的裸可点图标 → UH-b
python - "$A" <<'PY' || { echo "✘ 破口文件没写成（这条负控不成立）"; exit 2; }
import io, os, sys
p = os.path.join(sys.argv[1], "ui", "user", "NegctlTapProbe.kt")
io.open(p, "w", encoding="utf-8").write(
    "package com.shanxiang.buspost.ui.user\n"
    "import androidx.compose.foundation.clickable\n"
    "import androidx.compose.foundation.layout.Box\n"
    "import androidx.compose.foundation.layout.size\n"
    "import androidx.compose.runtime.Composable\n"
    "import androidx.compose.ui.Modifier\n"
    "import androidx.compose.ui.unit.dp\n"
    "@Composable fun NegctlTapProbe() {\n"
    "    Box(Modifier.size(18.dp).clickable { })\n"
    "}\n")
PY
ctl UH-b "页面回到 18dp 裸可点图标：指尖点不中，还会误触旁边那颗"

# 3 "撑命中区"这套写法在核心层整体消失 → UH-b 的反证必须红（否则上面那个 0 是空的）
#    ⚠ 这是一族跨文件的写法：swapall 只能改单个文件，判据数的是整个 core/ui 目录 ⇒ 这里必须走目录遍历。
python - "$COREUI" <<'PY' || { echo "✘ 反证注入没落到实处（这条负控不成立）"; exit 2; }
import io, os, sys
root = sys.argv[1]
n = 0
for dp, _, fs in os.walk(root):
    for fn in fs:
        if not fn.endswith(".kt"):
            continue
        p = os.path.join(dp, fn)
        s = io.open(p, encoding="utf-8", errors="replace").read()
        if "defaultMinSize" not in s:
            continue
        io.open(p + ".tmp", "w", encoding="utf-8", newline="").write(s.replace("defaultMinSize", "minSizeRenamed"))
        os.replace(p + ".tmp", p)
        n += 1
assert n >= 3, "core/ui 里只有 %d 个文件带 defaultMinSize，反证注入面不对" % n
PY
ctl UH-b "反证掉了：core/ui 里再没有 defaultMinSize，那个 0 数的是不存在的东西"

# 4 一处懒列表丢了 key → UH-c
swap "$GOODS" 'itemsIndexed(vm.shown.chunked(goodsCols), key = { _, row -> row.joinToString("-") { it.id.toString() } })' \
              'itemsIndexed(vm.shown.chunked(goodsCols))'
ctl UH-c "商城那一行没有 key 了：排序后按位置复用，选中态与入场动画错位"

# 5 入场动画退回 remember → UH-d 两条同时红
swap "$PRIM" '    var visible by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }' \
             '    var visible by remember { mutableStateOf(false) }'
ctl UH-d "「已入场」改回 remember：列表项随滚动进出组合就被丢掉，每滚回来重播一遍"

# 6 收集器又长在列表项的参数里 → UH-e
swap "$WB" '                        waiting = serviceWaiting,' \
           '                        waiting = Running.waiting.collectAsStateWithLifecycle().value,'
ctl UH-e "报告卡那一格自己收流：滚动一次多挂一批收集器"

# 7 选点页不再释放 MapView → UH-f
swap "$PICK" '            owned?.onDestroy()' '            owned?.toString()'
ctl UH-f "MapView 只创建不销毁：高德那块 SurfaceView 每开一次漏一份"

# 8 驾驶模式那一行不再读这份内边距（抄回裸字面量）→ UH-g 两个读数一起动（消费 3→2、裸抄 0→1）
swap "$DBITS" '.padding(busRowPadding)' '.padding(horizontal = 14.dp, vertical = 13.dp)'
ctl UH-g "有一行把内边距抄回裸字面量：它不会跟着令牌动，同一张卡里就会有一行顶到卡的圆边上（D-172 真机那一形）"

# 9 令牌本体被改名（三处消费一起断）→ UH-g 的"恰好一份定义"该红
swapall "$PRIM" 'val busRowPadding' 'val busRowPaddingRenamed'
ctl UH-g "行内边距的单一真源没了：定义数与消费数同时落 0，那一格等于回到各写各的"

echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
