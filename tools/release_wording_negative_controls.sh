#!/usr/bin/env bash
# 更新公告与发布文案判据的负控制（配 tools/check_release_wording_probes.sh）。
# 用法：bash tools/release_wording_negative_controls.sh   每条破口都要求判据簿红，全绿才通过
#
# 为什么这本特别要"可执行"的那几条（RW-6 / RW-7）：
#   RW-a 与 RW-d 钉的是同一件事的两面 —— 静态那几格只证明"CI 里写着要取公告"，
#   真正会骗人的是**取错了版**：`v21` 命中 `v214` 那一节，屏上就把上一版的公告当这一版发出去，
#   而那正是"把没做过的事说成做了"。所以词边界与占位这两条必须真跑一次脚本才作数。
#
# ⚠ 会就地临时改源码并在结束时原样恢复；一次只允许跑一个（negctl_lock）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2

M=app/src/main/java/com/shanxiang/buspost
YML=.github/workflows/build-and-release.yml
AU=$M/core/update/AppUpdate.kt
AUP=$M/core/update/AppUpdater.kt
UD=$M/core/update/UpdateDialog.kt
US=$M/ui/common/UpdateSection.kt
PICK=tools/release_notes_pick.py
API=$M/core/net/Api.kt
AR=$M/ui/user/ArrangementRows.kt
PS=$M/ui/user/ParcelTicketScreen.kt
FILES=("$YML" "$AU" "$AUP" "$UD" "$US" "$PICK" "$API" "$AR" "$PS")
PROBE=tools/check_release_wording_probes.sh
BAK=/tmp/rw_negctl.bak.$$

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

# RW-1 正文通路整个退回写死那一句 → RA_FILE 0 / RA_OLD 1
check RW-1 "× RW-a" "$YML" \
  '--notes-file .release-notes.md' \
  '--notes "自动发布：本次运行 $GITHUB_RUN_NUMBER。"'

# RW-2 「设置 → 关于」那一处不再走同一个出口（两屏对同一条公告两种说法）→ RB_USE2 0
check RW-2 "× RW-b" "$US" \
  'Text(announcementText(o.release.body), lineHeight = 19.sp)' \
  'Text(o.release.body, lineHeight = 19.sp)'

# RW-3 安静截断回来（不说明截了多少）→ RB_CUT2 1
check RW-3 "× RW-b" "$US" \
  'Text(announcementText(o.release.body), lineHeight = 19.sp)' \
  'Text(o.release.body.take(600), lineHeight = 19.sp)'

# RW-4 上屏那句带回结果码 → RC_BAD 1
check RW-4 "× RW-c" "$AU" \
  'return UpdateOutcome.Failed("更新服务器这会儿没回话。稍后再点一次「检查更新」。")' \
  'return UpdateOutcome.Failed("更新服务没有返回内容（HTTP $httpCode）")'

# RW-5 屏上撤了码、日志也没接住 → RC_LOG 0
check RW-5 "× RW-c" "$AUP" \
  'android.util.Log.w("BUS_UPDATE", "检查更新没成 httpCode=${raw.code} error=${raw.error} 屏上那句=${outcome.reason}")' \
  'Unit'

# RW-6 词边界破了：`v21` 会命中 `v214` 那一节 → RD_NEAR 0（把别的版本的公告发给这一版）
check RW-6 "× RW-d" "$PICK" \
  'want = re.compile(r"(?<![0-9A-Za-z])" + re.escape(tag) + r"(?![0-9A-Za-z])")' \
  'want = re.compile("^" + re.escape(tag))'

# RW-7 取不到那一节时不再说实话（把"没人写"说成"没有更新"）→ RD_MISS 0
check RW-7 "× RW-d" "$PICK" \
  'MISSING = "这一版没有写更新公告。' \
  'MISSING = "本次没有更新。'

restore
# RW-8 把传输层的原始文案放回上屏那句（OkHttp 那句带 IP/端口/毫秒会直接印在失败态上）→ RE_RAW_MSG 1
check RW-8 "× RW-e" "$API" \
  '            throw netFail(
                "照片传不出去：连不上站点服务器。请换一张能上网的卡或 Wi-Fi 再试。",
                "upload connect: ${e.javaClass.simpleName} ${e.message}",
            )' \
  '            throw NetworkException(e.message ?: "上传连接失败", e)'

# RW-9 屏上撤了码、日志也没接住（网络这一路）→ RE_LOG_SINK 1
check RW-9 "× RW-e" "$API" \
  '    private fun netFail(userWords: String, detail: String): NetworkException {
        Log.w(TAG, "BUS_NET $detail")' \
  '    private fun netFail(userWords: String, detail: String): NetworkException {
        // 日志撤了'

# ---------- RW-f 那一族：横屏上弹窗装不下，两颗按钮就摸不到（真机量到过） ----------
# 这三条是**从症状倒过来**写的：不是"少了 weight 所以红"，而是"少了/放错了这一行，
# 393dp 高的那一屏就会把「稍后再说」「立即更新」顶出去"。所以撤行与调序两条都要能红。

# RW-10 冷启动弹窗的公告框退回写死高度 → RF_W1 0
check RW-10 "× RW-f" "$UD" \
  '                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 190.dp)' \
  '                        .fillMaxWidth()
                        .heightIn(max = 190.dp)'

# RW-11 「设置 → 关于」那一处同样退回写死高度 → RF_W2 0（两处同一个病，只修一处等于没修）
check RW-11 "× RW-f" "$US" \
  '                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 200.dp)' \
  '                            .fillMaxWidth()
                            .heightIn(max = 200.dp)'

# RW-12 两行调个顺序（封顶排在吃剩余空间之前）：weight 拿到的上限已经是 190dp，等于没写 → RF_ORDER1 0
check RW-12 "× RW-f" "$UD" \
  '                        .weight(1f, fill = false)
                        .heightIn(max = 190.dp)' \
  '                        .heightIn(max = 190.dp)
                        .weight(1f, fill = false)'

# ---------- RW-g 那一族：缺席措辞（D-178） ----------
# 三条各咬一个方向：旧词回来 / 唯一出口没了（整套兜底被删）/ 票面又一行念两遍。
check RW-13 "× RW-g 屏上还留着" "$AR" \
  'internal const val NOT_GIVEN = "系统没给这一项"' \
  'internal const val NOT_GIVEN = "后端没给"'

check RW-14 "× RW-g 缺席措辞的唯一出口不是恰好一份" "$AR" \
  'internal const val NOT_GIVEN = "系统没给这一项"' \
  ''

check RW-15 "× RW-g 票面又出现" "$PS" \
  'TicketCard("分段", listOf("分段" to NOT_GIVEN))' \
  'TicketCard("分段", listOf("后端没给" to NOT_GIVEN))'

# 最后一条注入之后必须再还原一次：`check` 是在**注入前**还原的，收尾不还原就会把破口留在工作树里
restore
bash "$PROBE" >/dev/null 2>&1 && echo "[恢复] 判据簿重新通过" || echo "[恢复] × 判据簿恢复后仍然红，工作树可能没回到原样"
n=0
for i in "${!FILES[@]}"; do
  cmp -s "${FILES[$i]}" "$BAK.$i" || { echo "× ${FILES[$i]} 与原样不一致"; n=$((n + 1)); }
done
[ "$n" = 0 ] && echo "[恢复] ${#FILES[@]} 份文件与原样一致"

echo "更新公告与发布文案负控：通过 $ok / 失败 $bad"
[ "$bad" = 0 ] || exit 1
