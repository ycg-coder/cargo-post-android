#!/usr/bin/env bash
# UI 遍历 DENY 清单判据的负控制（配 tools/check_ui_deny_list_probes.sh）
# 每刀都落在 /tmp 沙盒，工程真身不动。用法：bash tools/ui_deny_list_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/dl_neg.$$
CLEAN=/tmp/dl_neg_clean.$$
PROBE="$SRC/tools/check_ui_deny_list_probes.sh"
mkdir -p "$SC"
cp -r "$SRC/tools" "$SC/tools"
DL=$SC/tools/ui_deny_list.txt
[ -f "$DL" ] || { echo "✘ 沙盒缺 $DL"; exit 2; }
cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); base=$?
[ "$base" = 0 ] || { echo "✘ 基线未通过，负控结果一律不采信"; printf '%s\n' "$out" | grep -a '^×' | head -3; exit 2; }
echo "✔ 基线：副本原样 exit 0"

ok=0; bad=0; MISS=0
sub() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(old) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(old), old[:40]))
if old in new: sys.exit("SELF-MATCH")
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
PY
}
red() { local label="$1" needle="$2"
  out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = 0 ]; then echo "✘ $label：注入了却仍全绿 —— 这道闸是空转的"; bad=$((bad+1))
  elif ! printf '%s\n' "$out" | grep -a "^× $needle" >/dev/null; then echo "✘ $label：红了但没红在该管那格（缺 × $needle）"; bad=$((bad+1))
  else echo "✔ $label → 红在 × $needle"; ok=$((ok+1)); fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"; }

# NC-1 摘掉本轮真踩到的那条（复现我误触班次的场景）
sub "$DL" $'\n确认发车\n' $'\n' && red "NC-1 摘掉 确认发车" "DL-b1"
# NC-2 把旧集合里的一条悄悄改名
sub "$DL" $'\n解除绑定\n' $'\n解绑\n' && red "NC-2 改名 解除绑定" "DL-c "
# NC-3 整块截断清单（削到 21 条以下必须抓到）
python - "$DL" <<'PY' || MISS=1
import sys, io
p = sys.argv[1]
ls = io.open(p, encoding='utf-8', newline='').read().split('\n')
k = [i for i, x in enumerate(ls) if x.strip() and not x.startswith('#')]
del ls[k[-1]]
io.open(p, 'w', encoding='utf-8', newline='').write('\n'.join(ls))
PY
red "NC-3 削掉最后一条" "DL-a "
# NC-4 有人抄第二份硬编码 DENY（单一真值被破）
printf 'DENY=%s\n' "'退出登录|删除'" > "$SC/tools/second_copy.sh" && red "NC-4 出现第二份硬编码 DENY" "DL-d "
rm -f "$SC/tools/second_copy.sh"; cp -r "$CLEAN" "$SC"

echo
echo "负控：$ok 红 / $bad 未红，锚点未命中 $MISS"
[ "$bad" = 0 ] && [ "$MISS" = 0 ] && { echo "✅ ui_deny_list 负控：4 刀全红，各红在该管的那一格"; exit 0; }
echo "❌ ui_deny_list 负控未全数抓到破口 —— 判据不可信"; exit 1
