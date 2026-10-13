#!/usr/bin/env bash
# #57「最早取货时间」结构判据的负控制（配 tools/check_pickup_window_probes.sh）
# 每个"目标 0 / 恰好 N"都要人为造一次破口，证明它会红；注入落在 /tmp 沙盒（整目录 copy），工程真身不动。
# ⚠ D-084/D-085 的三条规矩都在这里带上：注入落空必须说出口、锚点单行且 EOL 无关、本书自带"不存在的锚点必须被拒"的自检。
# 用法：bash tools/pickup_window_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/pw_neg.$$
CLEAN=/tmp/pw_neg_clean.$$
PKG=com/shanxiang/buspost
PROBE="$SRC/tools/check_pickup_window_probes.sh"

mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
M=$SC/app/src/main/java/$PKG
PW=$M/ui/user/PickupWindow.kt
SD=$M/ui/user/SendScreen.kt
PS=$M/ui/user/ParcelScreen.kt
MD=$M/data/model/Models.kt
RP=$M/data/repo/Repo.kt
DD=$M/data/repo/DemoData.kt
WR=$M/ui/user/WalletRows.kt
CT=$SC/app/src/test/java/$PKG/ui/user/PickupWindowContractTest.kt
for f in "$PW" "$SD" "$PS" "$MD" "$RP" "$DD" "$WR" "$CT"; do
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

check() { # $1=编号 $2=期望点名的判据 $3..=注入
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
check_miss() { # 本书自己的保险丝：不存在的锚点必须被拒
  local id="$1"; shift
  if "$@"; then
    echo "✘ $id 注入器放过了一个不存在的锚点（后面每条 ✔ 都可能是假绿）"; bad=$((bad + 1))
  else
    echo "✔ $id 不存在的锚点被注入器当场拒绝"; ok=$((ok + 1))
  fi
  MISS=0; rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# ---------- PW-a 键的落点 ----------
check PW-a1 "× PW-a 这个键在生效代码里不再是 8 行" ins_after "$RP" \
  '    suspend fun createSendOrder(form: Map<String, Any?>): Ack {' \
  '        val pwExtra = form["earliestPickupTime"]'
check PW-a2 "× PW-a Repo/WalletRows 里出现了这个键" ins_after "$WR" \
  'internal fun walletMoney(v: Double?): String = v?.let { "¥" + String.format(java.util.Locale.CHINA, "%.2f", it) } ?: NOT_GIVEN' \
  'internal const val PW_STRAY_KEY = "earliestPickupTime"'
check PW-a3 "× PW-a 命中文件数不是 4" delline "$DD" \
  '            "earliestPickupTime" to if (i == 1) System.currentTimeMillis() + 3 * 3600_000L else null,'

# ---------- PW-b 出网只有一个写法 ----------
check PW-b1 "× PW-b 出网值不再是唯一写法" sub "$SD" \
  '        "earliestPickupTime" to pickupSubmitMillis(),' \
  '        "earliestPickupTime" to pickupDay,'
check PW-b2 "× PW-b 界面把这一键拼成了字符串" sub "$SD" \
  '        "earliestPickupTime" to pickupSubmitMillis(),' \
  '        "earliestPickupTime" to "2026-10-13 15:00:00",'

# ---------- PW-c 时区源 ----------
check PW-c1 "× PW-c 服务时区的唯一定义没了" sub "$PW" \
  'internal val PICKUP_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")' \
  'internal val PICKUP_ZONE: ZoneId = ZoneId.systemDefault()'
check PW-c2 "× PW-c 用上了设备默认时区那套 API" ins_after "$PW" \
  'internal data class PickupOption(val dayOffset: Int, val hour: Int)' \
  '    private val pwWallClock = java.util.Calendar.getInstance().timeInMillis'

# ---------- PW-d 过去的整点是消失不是置灰 ----------
check PW-d1 "× PW-d 候选不再按" sub "$PW" \
  'PICKUP_HOURS.map { h -> PickupOption(day, h) }.filter { pickupMillisOf(now, day, it.hour) != null }' \
  'PICKUP_HOURS.map { h -> PickupOption(day, h) }'
check PW-d2 "× PW-d 档位→毫秒那一格不再拒过去" sub "$PW" \
  'return if (instant > now.toInstant().toEpochMilli()) instant else null' \
  'return instant'
check PW-d3 "× PW-d 改成" sub "$SD" \
  '            .clickable(onClick = onClick)' \
  '            .clickable(enabled = vm.pickupDay >= 0, onClick = onClick)'

# ---------- PW-e 三句话各自唯一出处 ----------
check_miss PW-e0 sub "$PW" '这一行在本仓根本不存在-NEG-CONTROL' 'x'
check PW-e1 "× PW-e 页面重抄了字面" sub "$SD" \
  '        Text(PICKUP_DISPATCH_NOTE, color = t.textTertiary, fontSize = (11.5 * scale).sp)' \
  '        Text("填了最早取货时间，后端会按它筛当批任务窗口", color = t.textTertiary, fontSize = (11.5 * scale).sp)'
check PW-e2 "× PW-e 三句话不再是各自唯一出处" sub "$PW" \
  'internal const val PICKUP_UNSET_TEXT = "随时可取（不限制最早取货时间）"' \
  'internal const val PICKUP_UNSET_TEXT = "随时可取"'
check PW-e3 "× PW-e 三句话不再是各自唯一出处" sub "$PW" \
  'internal const val PICKUP_ABSENT_TEXT = "后端没给这一项（下单时没填也会这样，本机分不开）"' \
  'internal const val PICKUP_ABSENT_TEXT = "未设置"'

# ---------- PW-f 回读只认数字 ----------
check PW-f1 "× PW-f 回读不再是只认数字" sub "$MD" \
  'earliestPickupMillis = (o.opt("earliestPickupTime") as? Number)?.toLong()?.takeIf { it > 0L },' \
  'earliestPickupMillis = o.multiNumOrNull("earliestPickupTime")?.toLong(),'
check PW-f2 "× PW-f 又用 multiNum 读这一键" sub "$MD" \
  'earliestPickupMillis = (o.opt("earliestPickupTime") as? Number)?.toLong()?.takeIf { it > 0L },' \
  'earliestPickupMillis = o.multiNum("earliestPickupTime").toLong(),'

# ---------- PW-g 回执三格 ----------
check PW-g1 "× PW-g 回执三格不齐" sub "$SD" \
  '                pickupReceipt = pickupSubmitConclusion(' \
  '                pickupReceipt = "已提交",'
check PW-g2 "× PW-g pickupReceipt 的生效行不是 4" delline "$SD" \
  '                    if (vm.pickupReceipt.isNotEmpty()) {'
check PW-g3 "× PW-g 回执三格不齐" sub "$PW" \
  'internal fun pickupSubmitConclusion(submitted: Long?, readback: Long?): String = when {' \
  'internal fun pwConclusionRenamedAway(submitted: Long?, readback: Long?): String = when {'

# ---------- PW-h 用例簿 ----------
check PW-h1 "× PW-h 契约用例不是 17 条" ins_after "$CT" \
  '    fun `候选只有三天`()' \
  '    @Test'

# ---------- PW-i 字段可空 ----------
check PW-i1 "× PW-i 回读字段不再可空" sub "$MD" \
  '    val earliestPickupMillis: Long? = null,' \
  '    val earliestPickupMillis: Long = 0,'

# ---------- PW-j 演示只给一单 ----------
check PW-j1 "× PW-j 演示报文里带这个键的单数不是 1" sub "$DD" \
  '"earliestPickupTime" to if (i == 1) System.currentTimeMillis() + 3 * 3600_000L else null,' \
  '"earliestPickupTime" to if (i == 1 || i == 2) System.currentTimeMillis() + 3 * 3600_000L else null,'

# ---------- PW-k 选择器接线 ----------
check PW-k1 "× PW-k 选择器接线不齐" delline "$SD" '                        PickupWindowPicker(vm)'
check PW-l1 "× PW-l 列表那一枚 chip 不再只念回读文本" sub "$PS" \
  's.earliestPickupMillis?.let { CargoChip("取件 ${pickupReadbackText(it)}") }' \
  's.earliestPickupMillis?.let { CargoChip("取件 " + PICKUP_UNSET_TEXT) }'

check PW-m1 "× PW-m 「不限制」那枚撤销出口没了或改写了" sub "$SD" \
  '                vm.pickupDay = -1' \
  '                vm.pickupDay = 0'

check PW-n1 "× PW-n 纯层里混进了分/秒级位移" sub "$PW" \
  '        .atTime(hour, 0)
        .atZone(now.zone)' \
  '        .atTime(hour, 0).plusMinutes(3)
        .atZone(now.zone)'
check PW-n2 "× PW-n 出网值不再整点对齐" sub "$PW" \
  '.atTime(hour, 0)' \
  '.atTime(hour, 30)'

echo "---"
echo "计数：按名点名变红 $ok 条 / 不成立 $bad 条"
[ "$bad" = 0 ] || echo "✘ 有破口没被自己那条判据抓住，或注入根本没落"
[ "$bad" = 0 ]
