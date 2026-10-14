#!/usr/bin/env bash
# 交接签收签名（#20 阶段 B）判据的负控制，配 tools/check_envelope_probes.sh 的 E-i / E-i2 / E-i3 三格。
# 只管这几格（同簿的 E-a…E-h 至今没有负控，那是既有缺口，记在台账里，不冒充本轮补完）。
# 每个"目标 0 / 恰好 N"都人为造一次破口证明它会红；注入落在 /tmp 沙盒，工程真身不动。
# 用法：bash tools/envelope_signature_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/env_sig_neg.$$
CLEAN=/tmp/env_sig_clean.$$
PKG=com/shanxiang/buspost
PROBE="$SRC/tools/check_envelope_probes.sh"

mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
M=$SC/app/src/main/java/$PKG
MD=$M/data/model/Models.kt
RP=$M/data/repo/Repo.kt
HD=$M/ui/driver/HandoverScreen.kt
SG=$M/ui/driver/HandoverSignature.kt
CT=$SC/app/src/test/java/$PKG/ui/driver/HandoverSignatureContractTest.kt
for f in "$MD" "$RP" "$HD" "$SG" "$CT"; do
  [ -f "$f" ] || { echo "✘ 沙盒缺 $f：判据扫得到、副本没有，本轮负控全部作废"; exit 2; }
done
cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

base_out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); base=$?
if [ "$base" != 0 ]; then echo "✘ 基线未通过，负控结果一律不采信"; printf '%s\n' "$base_out" | grep -a '^×' | head -5; exit 2; fi
echo "✔ 基线：副本原样 exit 0"

MISS=0; ok=0; bad=0
sub() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if old not in s: sys.exit("ANCHOR-MISS: " + old[:60])
NL = '\r\n' if '\r\n' in s else '\n'
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new.replace('\n', NL), 1))
PY
}
delline() { python - "$1" "$2" <<'PY' || { MISS=1; return 1; }
import sys
p, needle = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8', newline='').read()
NL = '\r\n' if '\r\n' in s else '\n'
ls = s.split(NL)
k = [i for i, x in enumerate(ls) if needle in x]
if not k: sys.exit("ANCHOR-MISS: " + needle[:60])
del ls[k[0]]
open(p, 'w', encoding='utf-8', newline='').write(NL.join(ls))
PY
}
check() { # $1=编号 $2=期望点名的判据 $3..=注入
  local id="$1" want="$2" rc out
  shift 2
  if ! "$@"; then
    echo "✘ $id 锚点没命中，破口根本没注入（这条负控不成立）"
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

check EI-1 "× E-i 模型不再解析后端 signUrl" sub "$MD" \
  'signUrl = o.multiStr("signUrl", "handoverSignUrl"),' \
  'signUrl = o.multiStr("signatureUrl", "sign_url"),'
check EI-2 "× E-i 页面不再渲染后端回读的 signUrl" delline "$HD" '                    url = task.signUrl,'
check EI-3 "× E-i 签名上传失败不再拦停提交" sub "$HD" \
  '                if (HandoverSignature.shouldBlockSubmit(signed, signUploaded)) {' \
  '                if (false) {'
check EI-4 "× E-i 签成门槛的常量不齐" sub "$SG" \
  '    const val MIN_STROKES = 2' \
  '    val minStrokes = 2'
check EI-5 "× E-i2 提交口把照片与签名并成一格" sub "$RP" \
  '                        "remark" to remark, "signUrl" to signUrl,' \
  '                        "remark" to remark,'
# ⚠ 破口要打在 `@Test` 那一行本身：E-i3 的取数是 `grep -cE '^[[:space:]]*@Test'`，
#   删测试函数体而留着注解，计数一字不变 ⇒ 第一版这条就是那样写的，念成"判据仍然绿"（假红因，真缺陷在注入）。
check EI-6 "× E-i3 签名纯层用例少于 12 条" delline "$CT" '    @Test'
check_miss EI-0 sub "$SG" '这一行在本仓根本不存在-NEG-CONTROL' 'x'

echo "----"
echo "签名判据负控：通过 $ok / 失败 $bad"
[ "$bad" = 0 ] && [ "$ok" = 7 ]
