#!/usr/bin/env bash
# #43 启动图标判据的负控制（配 tools/check_launcher_icon_probes.sh）
# 每个"目标 0 / 恰好 N"都要人为造一次破口；注入落在 /tmp 沙盒（整目录 copy），工程真身不动。
# 用法：bash tools/launcher_icon_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/ic_neg.$$
CLEAN=/tmp/ic_neg_clean.$$
PROBE="$SRC/tools/check_launcher_icon_probes.sh"

mkdir -p "$SC/app/src" "$SC/docs"
cp -r "$SRC/app/src/main" "$SC/app/src/main"
cp -r "$SRC/docs/design" "$SC/docs/design"
R=$SC/app/src/main/res
AD=$R/mipmap-anydpi-v26/ic_launcher.xml
FG=$R/drawable/ic_launcher_foreground.xml
BG=$R/drawable/ic_launcher_background.xml
CO=$R/values/colors.xml
RD=$SC/docs/design/icon-candidates/README.md
for f in "$AD" "$FG" "$BG" "$CO" "$RD"; do
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
delline() { python - "$1" "$2" <<'PY' || { MISS=1; return 1; }
import sys
p, needle = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8', newline='').read()
if s.count(needle) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(needle), needle[:60]))
i = s.find(needle); j = s.find('\n', i)
open(p, 'w', encoding='utf-8', newline='').write(s[:i] + s[j + 1:])
PY
}
ins_after() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, anchor, add = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(anchor) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(anchor), anchor[:60]))
NL = '\r\n' if '\r\n' in s else '\n'
j = s.index('\n', s.find(anchor)) + 1
open(p, 'w', encoding='utf-8', newline='').write(s[:j] + add.replace('\n', NL) + NL + s[j:])
PY
}
mkpng() { python - "$1" <<'PY' || { MISS=1; return 1; }
import os, sys
d = os.path.join(sys.argv[1], 'mipmap-hdpi')   # 参数就是 res 目录本身，别再往上一层
os.makedirs(d, exist_ok=True)
open(os.path.join(d, 'ic_launcher.png'), 'wb').write(b'\x89PNG\r\n\x1a\n' + b'0' * 16)
PY
}

check() {
  local id="$1" want="$2" rc out
  shift 2
  if ! "$@"; then
    echo "✘ $id 锚点没命中/不唯一，破口根本没注入（这条负控不成立）"
    bad=$((bad + 1)); MISS=0; rm -rf "$SC"; cp -r "$CLEAN" "$SC"; return
  fi
  out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = 0 ]; then
    echo "✘ $id 判据仍然绿（破口没咬住），这条判据等于没判据"; bad=$((bad + 1))
  elif printf '%s\n' "$out" | grep -qF "$want"; then
    echo "✔ $id 红且原因对得上（$want）"; ok=$((ok + 1))
  else
    echo "✘ $id 红了但不是那一条（缺 $want；实为 $(printf '%s\n' "$out" | grep -a '^×' | head -1)）"; bad=$((bad + 1))
  fi
  MISS=0; rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
check_miss() {
  local id="$1"; shift
  if "$@"; then
    echo "✘ $id 注入器放过了一个不存在的锚点（后面每条 ✔ 都可能是假绿）"; bad=$((bad + 1))
  else
    echo "✔ $id 不存在的锚点被注入器当场拒绝"; ok=$((ok + 1))
  fi
  MISS=0; rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# ---------- IC-a 三层接线 ----------
check IC-a1 "× IC-a 自适应图标又把背景指回那片深蓝纯色" sub "$AD" \
  '<background android:drawable="@drawable/ic_launcher_background" />' \
  '<background android:drawable="@color/bus_launcher_bg" />'
check IC-a2 "× IC-a 自适应图标三层接线不齐" delline "$AD" '    <monochrome android:drawable="@drawable/ic_launcher_foreground" />'
check IC-a3 "× IC-a 自适应图标三层接线不齐" delline "$AD" '    <foreground android:drawable="@drawable/ic_launcher_foreground" />'

# ---------- IC-b 背景层是那条满幅线性渐变 ----------
check IC-b1 "× IC-b 背景层不是那一条满幅线性渐变" sub "$BG" 'startColor="#FF0E5A52"' 'startColor="#FF123F6E"'
check IC-b2 "× IC-b 背景层不是那一条满幅线性渐变" sub "$BG" 'M0,0h108v108h-108z' 'M4,4h100v100h-100z'
check IC-b3 "× IC-b 背景层不是那一条满幅线性渐变" sub "$BG" 'android:type="linear"' 'android:type="radial"'

# ---------- IC-c 前景不许烤进底板形状 ----------
check IC-c1 "× IC-c 前景被烤进了底板形状" ins_after "$FG" \
  '    <group' \
  '    <path android:fillColor="#33000000" android:pathData="M16,0h76a16,16 0 0 1 16,16v76a16,16 0 0 1 -16,16h-76a16,16 0 0 1 -16,-16v-76a16,16 0 0 1 16,-16z"/>'
check IC-c2 "× IC-c 前景被烤进了底板形状" sub "$FG" '    <group' '    <path android:fillColor="#22000000" android:pathData="M0,0h108v108h-108z"/>'

# ---------- IC-d 安全区机制 ----------
check IC-d1 "× IC-d 安全区机制没了" sub "$FG" 'android:scaleX="0.86"' 'android:scaleX="1"'
check IC-d2 "× IC-d 安全区机制没了" sub "$FG" 'android:pivotX="54"' 'android:pivotX="0"'

# ---------- IC-e 前景只有一种白色 ----------
check IC-e1 "× IC-e 前景不再只有一种白色" sub "$FG" \
  '            android:fillColor="#FFFFFFFF"
            android:pathData="M41,64h7v4a3.5,3.5 0 0 1 -7,0z M60,64h7v4a3.5,3.5 0 0 1 -7,0z" />' \
  '            android:fillColor="#FF123F6E"
            android:pathData="M41,64h7v4a3.5,3.5 0 0 1 -7,0z M60,64h7v4a3.5,3.5 0 0 1 -7,0z" />'
check IC-e2 "× IC-e 前景不再只有一种白色" sub "$FG" 'android:fillType="evenOdd"' 'android:fillType="nonZero"'

# ---------- IC-f 旧 launcher 底色退役 ----------
check IC-f1 "× IC-f 旧 launcher 底色还在被引用" ins_after "$CO" \
  '    <color name="bus_primary">#FF1F5E9E</color>' \
  '    <color name="bus_launcher_bg">#FF123F6E</color>'

# ---------- IC-g 两件故意没做的事 ----------
check IC-g1 "× IC-g 两件故意没做的事被改动" mkpng "$R"
check IC-g2 "× IC-g 两件故意没做的事被改动" sub "$RD" '## 选定之后的落地清单' '## 选定之后随便做做'

check_miss IC-x0 sub "$FG" '这一行在本仓根本不存在-NEG-CONTROL' 'x'

echo "---"
echo "计数：按名点名变红 $ok 条 / 不成立 $bad 条"
[ "$bad" = 0 ] || echo "✘ 有破口没被自己那条判据抓住，或注入根本没落"
[ "$bad" = 0 ]
