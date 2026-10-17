#!/usr/bin/env bash
# 玻璃表面判据的负控制（配 tools/check_glass_surface_probes.sh）。
# 每一格"恰好 N / 目标 0"都人为造一次破口，证明判据真的会红；注入落在 /tmp 沙盒，工程真身不动。
# 用法：bash tools/glass_surface_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/gl_neg.$$
CLEAN=/tmp/gl_neg_clean.$$
PKG=com/shanxiang/buspost
PROBE="$SRC/tools/check_glass_surface_probes.sh"

mkdir -p "$SC/app/src/main"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
M=$SC/app/src/main/java/$PKG
FB=$M/core/ui/FluidBackground.kt
CP=$M/core/ui/Components.kt
TB=$M/ui/driver/DriverBits.kt
GD=$M/ui/driver/DriverGuideScreen.kt
PK=$M/ui/user/MapPointPickerScreen.kt
for f in "$FB" "$CP" "$TB" "$GD" "$PK"; do
  [ -f "$f" ] || { echo "✘ 沙盒缺 $f：判据扫得到、副本没有，本轮负控全部作废"; exit 2; }
done
cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); base=$?
if [ "$base" != 0 ]; then echo "✘ 基线未通过，负控结果一律不采信"; printf '%s\n' "$out" | grep -a '^×' | head -5; exit 2; fi
echo "✔ 基线：副本原样 exit 0"

MISS=0; ok=0; bad=0
sub() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(old) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(old), old[:60]))
if old in new: sys.exit("SELF-MATCH: 替换文本里还留着锚点")
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
PY
}
ins() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, needle, line = sys.argv[1], sys.argv[2], sys.argv[3]
lines = open(p, encoding='utf-8', newline='').read().split('\n')
hits = [i for i, l in enumerate(lines) if needle in l]
if len(hits) != 1: sys.exit("NEEDLE-NOT-UNIQUE(%d): %s" % (len(hits), needle[:60]))
lines.insert(hits[0] + 1, line)
open(p, 'w', encoding='utf-8', newline='').write('\n'.join(lines))
PY
}
expect_red() {
  local name="$1" needle="$2" want_rc="${3:-1}"
  out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = "$want_rc" ] && printf '%s\n' "$out" | grep -aq "$needle"; then
    echo "✔ $name：判据按预期变红（rc=$rc）"; ok=$((ok+1))
  else
    echo "✘ $name：期望 rc=$want_rc 且「$needle」实际 rc=$rc"; bad=$((bad+1))
    printf '%s\n' "$out" | grep -a '^×' | head -3
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# NC-1 高光被加成一个同名重载（不改默认参数、另开一份）→ GL-a 该红
ins "$FB" 'fun Modifier.glassSheen(' 'fun Modifier.glassSheen(tokens: BusTokens): Modifier = this.border(1.dp, Brush.verticalGradient(colors = listOf(Color.White, Color.Transparent)), RoundedCornerShape(0.dp))'
expect_red "NC-1 边缘高光有第二份口径" "× GL-a 边缘高光不是恰好一份实现"

# NC-2 内容卡没吃到高光（撤掉 BusCard 那一句）→ GL-a 用量与 GL-c 都该红
sub "$CP" '            .glassSheen(t, shape)
' ''
expect_red "NC-2 内容卡丢掉那道高光" "× GL-c 内容卡没吃到那道高光"

# NC-3 内容卡的底面被改成半透明 → GL-c 红（这一条是"高级但不能牺牲对比度"的那道线）
sub "$CP" '.background(bg.value, shape)' '.background(bg.value.copy(alpha = 0.6f), shape)'
expect_red "NC-3 内容卡被调成半透明" "× GL-c 内容卡的实色底被改掉了"

# NC-4 到站格退回实色条（浮面少一格）→ GL-b 红
sub "$GD" '            .glassmorphic(
                t,
                shape = RoundedCornerShape(BusRadius.m),
                shadowElevation = BusElevation.floating,
                tint = if (t.isDark) t.cardWarm else t.goldLight,
            )' '            .background(t.goldLight)'
expect_red "NC-4 到站格退回实色" "× GL-b 五个浮面缺一个"

# NC-5 通栏条自己抄一份玻璃 alpha → GL-d 红
ins "$TB" '            .glassSheen(t, topShape)' '            val why = tint.copy(alpha = if (t.isDark) 0.88f else 0.93f)'
expect_red "NC-5 页面抄玻璃透明度档位" "× GL-d 有"

# NC-6 深浅两档高光压成同一个值 → GL-e 红
sub "$FB" 'Color.White.copy(alpha = if (tokens.isDark) 0.16f else 0.80f)' 'Color.White.copy(alpha = 0.80f)'
expect_red "NC-6 深色主题用了浅色那道白边" "× GL-e 深浅两档高光压成了同一个值"

# NC-7 判据没有输入（玻璃那一份实现被搬走）→ fail-closed rc=2
rm -f "$FB"
expect_red "NC-7 缺 FluidBackground" "× 缺" 2

echo "RESULT ok=$ok bad=$bad miss=$MISS"
[ "$bad" = 0 ] && [ "$MISS" = 0 ] || exit 1
