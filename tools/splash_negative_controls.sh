#!/usr/bin/env bash
# 闪屏版面判据的负控制（配 tools/check_splash_probes.sh）。
# 用法：bash tools/splash_negative_controls.sh   每条破口都要求判据簿红，全绿才通过
#
# 这一屏的每条判据都对应一次**用户看着真机截图说出来的话**，所以每条都要能红：
#   标语换句 / 车兼任进度条 / 站牌是终点 / 退场三层 / 就绪格不被切 / 版本号读真值。
# 尤其 SPL-6 与 SPL-7 是收紧侧：钉死高与写死默认值这两件事在源码里都"看着正常"，
# 只有真机截图能看出来 —— 所以闸必须能在有人把它们改回去时立刻红。
#
# ⚠ 会就地临时改源码并在结束时原样恢复；一次只允许跑一个（negctl_lock）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2

M=app/src/main/java/com/shanxiang/buspost
SP=$M/core/ui/SplashOverlay.kt
ROOTUI=$M/ui/BusPostRoot.kt
FILES=("$SP" "$ROOTUI")
PROBE=tools/check_splash_probes.sh
BAK=/tmp/splash_negctl.bak.$$

for f in "${FILES[@]}"; do
  [ -f "$f" ] || { echo "✘ 缺 $f，本书作废"; exit 2; }
done
[ -f "$PROBE" ] || { echo "✘ 缺 $PROBE，本书作废"; exit 2; }

for i in "${!FILES[@]}"; do cp "${FILES[$i]}" "$BAK.$i"; done
restore() { for i in "${!FILES[@]}"; do cp "$BAK.$i" "${FILES[$i]}"; done; }
trap 'restore; for i in "${!FILES[@]}"; do rm -f "$BAK.$i"; done; negctl_release' EXIT

bash "$PROBE" >/dev/null 2>&1 || { echo "✘ 预检：$PROBE 起跑就不绿，基线不成立，不注入破口"; exit 5; }
echo "[预检] 判据簿起跑绿"

ok=0; bad=0

inject() { # inject <file> <old> <new>
  python - "$1" "$2" "$3" <<'PY'
import sys
p, a, b = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(a) != 1:
    sys.exit(9)
open(p, 'w', encoding='utf-8', newline='').write(s.replace(a, b))
PY
}

check() { # check <编号> <期望红的判据行> <文件> <old> <new>
  local id="$1" want="$2" file="$3" old="$4" new="$5"
  restore
  if ! inject "$file" "$old" "$new"; then
    echo "✘ ${id} 锚点没命中（不是恰好 1 次），本次未注入 ⇒ 判据未被验证"
    bad=$((bad + 1)); return
  fi
  local out rc
  out=$(bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = 0 ]; then
    echo "✘ ${id} 判据仍然绿（破口没咬住）"; bad=$((bad + 1))
  elif grep -q "$want" <<<"$out"; then
    echo "✔ ${id} 红且原因对得上（$want）"; ok=$((ok + 1))
  else
    echo "✘ ${id} 红了但不是那一条（缺 $want）"; bad=$((bad + 1))
  fi
}

# SPL-1 旧那句平台定位回来（与新标语同一件事说两遍）→ SA_LEAD 0 / SA_OLD 1
check SPL-1 "× SP-a" "$SP" \
  'withStyle(SpanStyle(color = Color.White.copy(alpha = 0.74f))) { append("赋能城乡融合的") }' \
  'withStyle(SpanStyle(color = Color.White.copy(alpha = 0.74f))) { append("面向城乡融合的客货邮一体化运力协同平台") }'

# SPL-2 车不再由真实进度驱动（退回一个自演的数）→ SB_DRIVE 0
check SPL-2 "× SP-b" "$SP" \
  '            progress.coerceIn(0, 100) / 100f,' \
  '            1f,'

# SPL-3 这一屏又长出一根独立进度条（车 + 细条＝同一件事两遍）→ SB_BAR 1
check SPL-3 "× SP-b" "$SP" \
  '            Spacer(Modifier.weight(1.45f))' \
  '            androidx.compose.material3.LinearProgressIndicator(progress = { 0.5f })
            Spacer(Modifier.weight(1.45f))'

# SPL-4 车不再停在站牌脚下（100% 失去落点）→ SB_STOP 0
check SPL-4 "× SP-b" "$SP" \
  '        val stopX = headLeft - 8.dp.toPx() - busW' \
  '        val stopX = startX'

# SPL-5 退场少一层（品牌不散，整屏一起闪一下）→ SD_BRAND 0
check SPL-5 "× SP-d" "$SP" \
  '            launch { brand.animateTo(0f, tween(180, easing = FastOutLinearInEasing)) }' \
  '            launch { brand.animateTo(1f, tween(180, easing = FastOutLinearInEasing)) }'

# SPL-6 就绪那一格又钉死高 34dp（真机上胶囊被齐根切掉——v214 底片）→ SE_MIN 0 / SE_DEAD 1
check SPL-6 "× SP-e" "$SP" \
  '                    .heightIn(min = 44.dp),' \
  '                    .height(34.dp),'

# SPL-7 版本号参数带回默认值（会过期的默认值安静地撒谎）→ SC_NODEF 0 / SC_FAKE 1
check SPL-7 "× SP-c" "$SP" \
  '    version: String,' \
  '    version: String = "v1.0.0",'

# SPL-8 调用点不再把包里的真版本传进去 → SC_PASS 0
check SPL-8 "× SP-c" "$ROOTUI" \
  '        version = "v" + com.shanxiang.buspost.BuildConfig.VERSION_NAME,' \
  '        // 版本号那一格不传：让它自己想办法'

restore
bash "$PROBE" >/dev/null 2>&1 && echo "[恢复] 判据簿重新通过" || echo "[恢复] × 判据簿恢复后仍然红，工作树可能没回到原样"
n=0
for i in "${!FILES[@]}"; do
  cmp -s "${FILES[$i]}" "$BAK.$i" || { echo "× ${FILES[$i]} 与原样不一致"; n=$((n + 1)); }
done
[ "$n" = 0 ] && echo "[恢复] ${#FILES[@]} 份文件与原样一致"

echo "闪屏版面负控：通过 $ok / 失败 $bad"
[ "$bad" = 0 ] || exit 1
