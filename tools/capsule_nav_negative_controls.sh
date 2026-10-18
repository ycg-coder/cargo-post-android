#!/usr/bin/env bash
# 全局悬浮胶囊判据的负控制（配 tools/check_capsule_nav_probes.sh）。
# 每一格"恰好 N / 目标 0"都人为造一次破口，证明判据真的会红；注入落在 /tmp 沙盒，工程真身不动。
# 用法：bash tools/capsule_nav_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/cn_neg.$$
CLEAN=/tmp/cn_neg_clean.$$
PKG=com/shanxiang/buspost
PROBE="$SRC/tools/check_capsule_nav_probes.sh"

mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
M=$SC/app/src/main/java/$PKG
CN=$M/ui/common/CapsuleNav.kt
RT=$M/ui/BusPostRoot.kt
DS=$M/ui/driver/DriverShell.kt
US=$M/ui/user/UserShell.kt
BS=$M/ui/user/BusScreen.kt
GD=$M/ui/driver/DriverGuideScreen.kt
CT=$SC/app/src/test/java/$PKG/ui/common/CapsuleNavContractTest.kt
for f in "$CN" "$RT" "$DS" "$US" "$BS" "$GD" "$CT"; do
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

# ins 在"含 needle 的那一行"之后插一行（SELF-MATCH 那道守卫会挡掉"原地复制一行"的注入，
# 而 NC-1 / NC-3 要造的正是"多出一处"，所以走插入而不是替换）
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

# expect_red 名称 期望出现的 × 前缀
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

# NC-1 根上多画一处胶囊 → CN-a 该红
ins "$RT" '    if (splashVisible) {' '    CapsuleNavOverlay(Modifier.align(Alignment.TopCenter))'
expect_red "NC-1 第二处画胶囊" "× CN-a 根上画它的地方不是恰好一处"

# NC-2 撤掉用户壳的登记（主导航少一格）→ CN-b 该红
sub "$US" '    CapsuleActions(items)
' ''
expect_red "NC-2 少一屏登记" "× CN-b 登记胶囊的屏数不是 5"

# NC-3 有人把旧的通栏 tab 塞回来 → CN-c 该红（这条同时证明"注释里的 BusTabBar"不会被数）
ins "$DS" '    CapsuleActions(items)' '    BusTabBar(items, selected) { selected = it }'
expect_red "NC-3 旧通栏回来" "× CN-c 旧的通栏底部 tab 又出现"

# NC-4 实时公交那一格「返回」被摘 → CN-d 该红
sub "$BS" '            CapsuleAction("back", "返回", BusIcons.back, onClick = { nav.popBackStack() }),
' ''
expect_red "NC-4 撤掉返回格" "× CN-d 实时公交那一格「返回」不见了"

# NC-5 高德自带面板被关掉（回到"我们自绘一份"那一版）→ CN-f 该红
#     D-169 反转了这条的方向：以前注入的是"面板被打开"，现在注入的是"面板被关掉"。
sub "$GD" '    o.isLayoutVisible = true' '    o.isLayoutVisible = false'
expect_red "NC-5 高德面板被关掉" "× CN-f 高德自带面板没开着"

# NC-6 页面自己抄一份上限判断 → CN-e 该红
sub "$BS" 'private const val LINE_CARDS_MAX = 8' \
          'private const val LINE_CARDS_MAX = CAPSULE_MAX'
expect_red "NC-6 上限被抄第二份" "× CN-e 有页面自己抄了上限判断"

# NC-7 反证：摘掉一条用例的 @Test 注解，CN-g 必须红（证明它数的是真文件里的注解行，不是写死的常量）
sub "$CT" '    @Test
    fun `一屏不登记就没有这一栏`() {' '    fun `一屏不登记就没有这一栏`() {'
expect_red "NC-7 用例条数变了" "× CN-g 契约用例不是 7 条"

# NC-8 格子等宽下限被压回触控下限（短文案那一格又跟着内容缩）→ CN-h 该红
sub "$CN" 'internal const val CAPSULE_CELL_MIN_WIDTH_DP = 76' 'internal const val CAPSULE_CELL_MIN_WIDTH_DP = 48'
expect_red "NC-8 格子宽度下限被压小" "× CN-h 格子下限被压到"

# NC-9 有人在页面自己定格子宽度（第二套观感口径）→ CN-h 该红
ins "$BS" 'private const val LINE_CARDS_MAX = 8' 'private val cellMin = CAPSULE_CELL_MIN_WIDTH_DP.dp'
expect_red "NC-9 页面自己抄格子宽度" "× CN-h 有"

# NC-10 画的时候不抬（回到"只在让高里加数、屏幕上一格没留"那一版）→ CN-i 该红
sub "$CN" 'modifier.navigationBarsPadding().padding(bottom = capsuleBottomGap()),' 'modifier.navigationBarsPadding(),'
expect_red "NC-10 胶囊不再离底" "× CN-i 离底间距不是"

# NC-11 让高那一侧改回写死的 14dp（两处不再读同一个源）→ CN-i 该红
sub "$CN" 'fun capsuleReserve(): Dp = capsulePillHeight() + capsuleBottomGap() + 8.dp' 'fun capsuleReserve(): Dp = capsulePillHeight() + 14.dp'
expect_red "NC-11 让高与画脱钩" "× CN-i "

# NC-12 离底间距被压小（贴回屏幕下沿）→ CN-i 该红
sub "$CN" 'fun capsuleBottomGap(): Dp = 16.dp' 'fun capsuleBottomGap(): Dp = 4.dp'
expect_red "NC-12 间距被压回贴底" "× CN-i 离底间距被压到"

# NC-13 有人在页面自己引用一次离底间距（第二套口径）→ CN-i 该红
ins "$BS" 'private const val LINE_CARDS_MAX = 8' 'private val gapCopy = capsuleBottomGap()'
expect_red "NC-13 页面自己抄离底间距" "× CN-i 有"

# NC-14 自绘转向动作那一行回来（高德面板左上角已经画了同一件事）→ CN-f 该红
ins "$GD" '                if (stopLine.isNotEmpty()) {' '                    Text(turnIconSpeechOf(0) ?: "", color = Color.White)'
expect_red "NC-14 自绘转向行回来" "× CN-f 自绘转向动作那一行回来了"

# NC-15 自绘车道那一行回来 → CN-f 该红
ins "$GD" '                if (stopLine.isNotEmpty()) {' '                    Text(laneDisplayTextOf(null), color = Color.White)'
expect_red "NC-15 自绘车道行回来" "× CN-f 自绘车道那一行回来了"

# NC-16 自绘剩余里程那一格回来 → CN-f 该红
ins "$GD" '                if (stopLine.isNotEmpty()) {' '                    Text(fmtMeters(1200), color = Color.White)'
expect_red "NC-16 自绘剩余里程回来" "× CN-f 自绘剩余里程/耗时那一格回来了"

# NC-17 高德那颗返回键只回 true、不接出口（点不动的键）→ CN-f 该红
sub "$GD" '                                onExit()
' ''
expect_red "NC-17 返回键没接出口" "× CN-f 高德那颗返回键没接回本页出口"

# NC-18 反证：把顶条整块改名（顶条没了 ⇒ 上面那三个 0 就可能是"扫描瞎了"而不是"确实没有"）→ CN-f 该红
sub "$GD" 'private fun GuideTopStrip(' 'private fun GuideTopStripX('
expect_red "NC-18 顶条正对照失效" "× CN-f 顶条的定义+调用不再是 2"

# NC-19 有人在导航那一屏的胶囊里又放一颗「退出导航」（高德面板上已经有一颗真做事的）→ CN-d 该红
ins "$GD" '                key = "voice",' '                key = "exit",'
expect_red "NC-19 重复的退出键回来" "× CN-d 导航那一屏的胶囊又长出"

# NC-20 播报那一格被撤（它是这一屏唯一没有高德等价物的动作）→ CN-d 该红
sub "$GD" '                key = "voice",' '                key = "voiceMute",'
expect_red "NC-20 播报格被撤" "× CN-d 导航那一屏的播报格不是一格"

echo "RESULT ok=$ok bad=$bad miss=$MISS"
[ "$bad" = 0 ] && [ "$MISS" = 0 ] || exit 1
