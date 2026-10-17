#!/usr/bin/env bash
# 天气提醒判据的负控制（配 tools/check_weather_alert_probes.sh）。
# 每一格"恰好 N / 目标 0"都人为造一次破口，证明它会红；注入落在 /tmp 沙盒，工程真身不动。
# 用法：bash tools/weather_alert_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/wa_neg.$$
CLEAN=/tmp/wa_neg_clean.$$
PKG=com/shanxiang/buspost
PROBE="$SRC/tools/check_weather_alert_probes.sh"

mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
M=$SC/app/src/main/java/$PKG
AE=$M/notify/AlertEngine.kt
RK=$M/data/repo/Repo.kt
LV=$M/ui/login/LoginViewModel.kt
CT=$SC/app/src/test/java/$PKG/notify/WeatherAlertContractTest.kt
for f in "$AE" "$RK" "$LV" "$CT"; do
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
  local name="$1" needle="$2"
  out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" != 0 ] && printf '%s\n' "$out" | grep -aq "$needle"; then
    echo "✔ $name：判据按预期变红"; ok=$((ok+1))
  else
    echo "✘ $name：期望红（$needle）实际 rc=$rc"; bad=$((bad+1))
    printf '%s\n' "$out" | grep -a '^×' | head -3
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# WA-1 有人把建议又抄回通知里（第二张表复活）→ WA-a 该红
ins "$AE" '        val prefix = "$glyph " + (' '        val legacy = "出行请带雨具，山货装车前请加盖苫布防淋。"'
expect_red "WA-1 第二张表复活" "× WA-a 通知里残留第二张天气"

# WA-2 推送多引用一次天气卡（两张答案的另一种长法）→ WA-a 该红
ins "$AE" '        val tips = weatherTipsFor(condition, temperature)' '        val again = weatherTipsFor(condition, temperature)'
expect_red "WA-2 建议被读两次" "× WA-a 推送引用天气卡建议的地方不是恰好一处"

# WA-3 调用处不再传当前视图域（写死用户域）→ WA-b 该红
sub "$AE" 'severity, w.text, w.temperature, w.wind, AppState.activeDomain.value,' 'severity, w.text, w.temperature, w.wind, AppDomain.USER,'
expect_red "WA-3 角色参数被写死" "× WA-b 角色这一格断了"

# WA-4 天气推送开始替角色选壳 → WA-c 该红
ins "$AE" '                                    "weather_alert".hashCode() and 0x7fffffff,' '                                    route = Routes.USER_SHELL,'
expect_red "WA-4 通知开始带 route" "× WA-c 天气推送开始带 route"

# WA-5 卡片那侧把「尘」丢了（浮尘一边响一边不响）→ WA-d 该红
sub "$RK" 'condition.contains("沙") || condition.contains("尘") ->' 'condition.contains("沙") ->'
expect_red "WA-5 现象词表单边缺项" "× WA-d 「尘」只在单边"

# WA-6 登录不再重置提醒水位（回到 0 个调用点）→ WA-e 该红
sub "$LV" '                AlertEngine.resetWatermark()' '                // 忘了重置'
expect_red "WA-6 水位重置口又没人调" "× WA-e resetWatermark 的调用点不是 1"

# WA-7 反证：摘掉一条用例的 @Test → WA-f 该红
sub "$CT" '    @Test
    fun `标题按严重度分档 不带正文`() {' '    fun `标题按严重度分档 不带正文`() {'
expect_red "WA-7 用例条数变了" "× WA-f 契约用例不是 7 条"

echo "RESULT ok=$ok bad=$bad miss=$MISS"
[ "$bad" = 0 ] && [ "$MISS" = 0 ] || exit 1
