#!/usr/bin/env bash
# DA-* 判据簿（F-3 ⑰ 台账锚点闸门，`D-③-5` 的反证）负控制总表：
# 逐条破口 → 确认 `tools/check_doc_anchor_probes.sh` exit 非 0 且报的是这一条。
# 只在 /tmp 副本上动手（docs/、app/src、tools/ 都拷一份），工程真身不受影响。
# 用法：bash tools/doc_anchor_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改文档与工具，两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3

SC=/tmp/da_neg.$$
PR="$SRC/tools/check_doc_anchor_probes.sh"
rm -rf "$SC"
mkdir -p "$SC"
# 沙盒必须与真身同扫描面：判据簿读的是七本台账 + 两个源码集 + tools/（在册表与工具本体都在 tools/ 下）。
# 少拷其一，FILE-NOT-FOUND 与 anchors 基数就都不再是本轮量到那几个数。
cp -r "$SRC/app/src" "$SC/app_src_tmp" 2>/dev/null
mkdir -p "$SC/app" && mv "$SC/app_src_tmp" "$SC/app/src"
cp -r "$SRC/docs" "$SC/docs"
cp -r "$SRC/tools" "$SC/tools"
cp "$SRC"/*.md "$SC/"
TN="$SC/THIRD_PARTY_NOTICES.md"
CL="$SC/CONTEXT_LOCK.md"
CK="$SC/docs/android/ANDROID_V2.1_CHECKLIST.md"
AT="$SC/tools/check_doc_anchors.py"
PINF="$SC/tools/fixtures/doc_anchor_pins.txt"
[ -f "$TN" ] && [ -f "$CL" ] && [ -f "$CK" ] || { echo "✘ 沙盒缺台账（THIRD_PARTY_NOTICES/CONTEXT_LOCK/V2.1 核对表），注入无处落"; exit 2; }
[ -f "$AT" ] || { echo "✘ 沙盒缺 tools/check_doc_anchors.py，DA-f 那条改不了工具就等于没改"; exit 2; }
[ -f "$PINF" ] || { echo "✘ 沙盒缺在册表 tools/fixtures/doc_anchor_pins.txt，DA-d 的闸门没有输入"; exit 2; }
CLEAN=/tmp/da_neg_clean.$$; cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT
FAIL=0

ctl() { # $1=编号 $2=说明
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" != 0 ] && echo "$out" | grep -q "^× $1"; then
    echo "✔ $1  $2 → exit $code：$(echo "$out" | grep "^× $1" | head -1)"
  elif [ "$code" != 0 ]; then
    echo "△ $1  $2 → exit $code，但抓到的是别的判据：$(echo "$out" | grep '^×' | head -1)"
  else
    echo "✘ $1  $2 → 负控制失效（exit 0），这条判据等于没判据"; FAIL=1
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
must_green() { # 正控制：这一改**不该**让簿红，红了就说明闸门把正常的台账编辑也判成破口
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" = 0 ]; then
    echo "✔ POS  $1 → exit 0：$(echo "$out" | grep '^DA-' | tr '\n' ' ')"
  else
    echo "✘ POS  $1 → 本该绿却红了（exit $code）：$(echo "$out" | grep '^×' | head -2 | tr '\n' ' ')"; FAIL=1
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
subst() { python - "$1" "$2" "$3" <<'PY'
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s: sys.exit("OLD-MISS: " + old)
open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
PY
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | grep '^DA-' | tr '\n' ' ')"
# 基线不绿就立刻收工：否则每条破口都会因为"判据本来就红"而打出 ✔，整轮负控是假的
[ "$base_code" = 0 ] || { echo "✘ 基线未通过（exit $base_code），负控结果一律不采信"; echo "$BASE" | grep '^×' | head -5; exit 2; }

# 注入行都写成表格行：--strict-id 的标识符取自**第一个单元**，锚点放同一单元才归给这个锚点
printf '| DvProbeOutOfRangeMarker Models.kt:999999 | 负控注入：行号越过 EOF |\n' >> "$TN"
  ctl DA-b "台账指到不存在的行（越界锚点必须是硬破口）"
printf '| DvProbeNoSuchFileMarker NoSuchLedgerFile.kt:12 | 负控注入：文件名写错 |\n' >> "$TN"
  ctl DA-c "FILE-NOT-FOUND 回涨到 16（本轮基线 15，只降不升）"
printf '| DvProbeDriftedIdentifierZzz Repo.kt:453 | 负控注入：标识符不在那一行 |\n' >> "$TN"
  ctl DA-e "ID-MISS 回涨一条就到 274（闸门 273，2026-10-14 由 286 下调）：改过源码却没人重量台账锚点"
# 在册表少一条：同一批可疑锚点立刻变成"新增"，闸门比的是 aid 集合而不是文档
python - "$PINF" <<'PY'
import sys
p = sys.argv[1]; L = open(p, encoding='utf-8').read().split('\n')
for i, l in enumerate(L):
    if l and not l.startswith('#'):
        del L[i]; break
open(p, 'w', encoding='utf-8').write('\n'.join(L))
PY
  ctl DA-d "在册表被删一条 ⇒ 仍可复现的可疑锚点被判成新增（闸门不许被抄近路）"
# 反向：锚点从台账里消失（行号被改成没有行号的形式），在册条目悄悄不见也必须响
# ⚠ 这条原先硬写 `Models.kt:497`，两轮都撞上同一件事：**被点名的那条锚点被人真修好了**，
#   aid 就不再在册，抹掉它当然不会"消失" ⇒ 负控静默失效（2026-10-15 是 sed 空转，本轮 D-173 是修好致空）。
#   所以这里**不再点名**：从在册表里现挑一条 CONTEXT_LOCK.md 的可疑锚点来抹，
#   挑不到就当场 STOP —— 宁可不跑，也不要一把永远绿的空刀。
VICTIM=$(python - "$PINF" "$CL" <<'PY'
import sys, re
pin, target = sys.argv[1], sys.argv[2].replace("\\", "/").split("/")[-1]
hit = ""
for ln in open(pin, encoding="utf-8"):
    if ln.startswith("#"):
        continue
    f = ln.rstrip("\n").split("\t")
    if len(f) < 4:
        continue
    doc_line = f[3]
    if doc_line.rsplit(":", 1)[0].split("/")[-1] != target:
        continue
    m = re.search(r"\b[A-Za-z0-9_]+\.kt:\d+(?:-\d+)?\b", f[2])
    if m:
        hit = m.group(0)
        break
print(hit)
PY
)
[ -n "$VICTIM" ] || { echo "STOP 在册表里没有 CONTEXT_LOCK.md 的可疑锚点，这条负控没有靶子（要么全修好了，要么在册表被清空）"; exit 6; }
echo "  靶子（本轮在册的一条）: $VICTIM"
subst "$CL" "$VICTIM" '被负控抹掉的锚点'
  ctl DA-d "在册锚点本次不再可疑（锚点被抹掉而不是被修好）"
# 扫描面塌掉：文档里的 `.kt:` 全被写坏成扫不到 ⇒ 锚点总数掉下来，闸门看不到任何一条
# 扫描面塌掉：**七本台账一起**把 `.kt:` 写坏成扫不到 ⇒ 锚点总数掉下来，DA-a 才响。
# 只改 CONTEXT_LOCK 一本时 anchors 仍 >1216（本轮基数 1457，闸门 1216，中间有 241 的余量），
# 抓到它的是 DA-d 的"消失"而不是 DA-a 的"没有输入" —— 那条注入证明不了它标注的那道闸（原来是 △，现在补齐）。
for d in "$SC"/*.md "$SC"/docs/android/*.md; do
  [ -f "$d" ] && sed -i 's/\.kt:/\.ktZ/g; s/\.java:/\.javaZ/g' "$d"
done
  ctl DA-a "台账锚点被写坏成扫不到（anchors 从 1216 掉下来=复核没有输入）"
# 反证判据：把工具取标识符的正则改坏，miss 会掉到 0 看着更绿，只有 DA-f 会响
subst "$AT" 'TOKEN = re.compile(r"[A-Za-z][A-Za-z0-9_]{4,}")' 'TOKEN = re.compile(r"zzNeverToken")'
  ctl DA-f "标识符分配被改成恒空（ID-MISS 的 0 是假的，DA-e 失去判别力）"
# 正控制：aid = 文档名 + 锚点原文，**不含行号**。整篇台账行号位移不该新增/消灭任何在册条目
sed -i '1s/^/\n/' "$TN"
  must_green POS "台账顶部插一行让所有行号位移：aid 不变、在册不该响"

echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
if [ "$FAIL" = 1 ]; then echo "RESULT=FAIL（有负控制没响或正控制误响，见上方 ✘）"; exit 1; fi
echo "RESULT=PASS"
