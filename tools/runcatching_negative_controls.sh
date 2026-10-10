#!/usr/bin/env bash
# F-2 第 9 步判据（tools/check_runcatching_probes.sh，RC-a…RC-e）的**负控制**。
# 用法：bash tools/runcatching_negative_controls.sh
#
# 这一簿要回答两件事（指令 §三十一："0 命中必须能反证"）：
#   1) RC-b 报的 `violations=0` 是"扫对了才 0"，不是"扫不到所以 0" ⇒ 每条破口都要真打破它。
#   2) 检测器的两条区分规则（**同行前缀不计**、**接收者不匹配只算 CANDIDATE**）不能退化成
#      "名字交集"——那正是早前把 `main.post(` 与 `sp.getString(` 读成挂起的那版逻辑。
#      所以除了"必须红"，这里还有两条"必须不红"的反向负控（ctl_keep）。
#
# 只在临时目录副本上动手（含 docs/ 与 tools/detector，RC-e/RC-b 读的就是这两份）。
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 负控会就地改源码；两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3

SC=/tmp/rc_neg.$$
CLEAN=/tmp/rc_neg_clean.$$
PKG=com/shanxiang/buspost
PR="$SRC/tools/check_runcatching_probes.sh"

mkdir -p "$SC/app/src/main" "$SC/tools" "$SC/docs/android"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp "$SRC/tools/runcatching_detector.py" "$SC/tools/runcatching_detector.py"
cp "$SRC/docs/android/RUNCATCHING_AUDIT.md" "$SC/docs/android/RUNCATCHING_AUDIT.md"

A="$SC/app/src/main/java/$PKG"
API="$A/core/net/Api.kt"
REPO="$A/data/repo/Repo.kt"
ROOTUI="$A/ui/BusPostRoot.kt"
DNS="$A/ui/driver/DriverNavScreen.kt"
NAV="$A/core/navigation/AmapNavigationEngine.kt"
DOC="$SC/docs/android/RUNCATCHING_AUDIT.md"
DET="$SC/tools/runcatching_detector.py"
for f in "$API" "$REPO" "$ROOTUI" "$DNS" "$NAV" "$DOC" "$DET"; do
    [ -f "$f" ] || { echo "✘ 负控文件缺失：$f（副本没复制全，下面每一条都会假绿）"; exit 2; }
done
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT
cp -r "$SC" "$CLEAN"
[ -f "$CLEAN/docs/android/RUNCATCHING_AUDIT.md" ] || { echo "✘ 干净副本没建成，本轮负控全部作废"; exit 2; }

# 跑沙盒里的判据簿：$1=期望退出码（1=抓到这条破口，2=预检拒绝读数）
run() { out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?; }
ctl() { # $1=判据编号 $2=说明
    run
    if [ "$code" = 1 ] && printf '%s\n' "$out" | grep -q "^× $1"; then
        echo "✔ $1  $2 → exit 1：$(printf '%s\n' "$out" | grep "^× $1" | head -1)"
    elif [ "$code" = 1 ]; then
        echo "△ $1  $2 → exit 1，但抓到的是别的判据：$(printf '%s\n' "$out" | grep '^×' | head -1)"
    else
        echo "✘ $1  $2 → 负控制失效（exit $code），这条判据等于没判据"
    fi
    rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
# 预检类：输入不在场时必须是 exit 2（"跑不了"），不能被读成 exit 1 的普通破口，更不能是 0
ctl_pre() { # $1=说明（期望输出里出现的关键词由 $2 给）
    run
    if [ "$code" = 2 ] && printf '%s\n' "$out" | grep -q "$2"; then
        echo "✔ 预检  $1 → exit 2 且没有输出任何计数行"
    else
        echo "✘ 预检  $1 → 期望 exit 2，实测 exit $code：$(printf '%s\n' "$out" | head -1)"
    fi
    rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
# 反向负控：注入一个"看着像挂起但其实不是"的形状，判据必须**保持全绿**，
# 且 $2 里给的字面量必须出现在输出里（否则这条是"什么都没发生"的空跑，不算证据）。
ctl_keep() { # $1=说明 $2=必须出现在输出里的字面量
    run
    if [ "$code" = 0 ] && ! printf '%s\n' "$out" | grep -q '^× ' && printf '%s\n' "$out" | grep -q "$2"; then
        echo "✔ 不误报  $1 → 判据仍全绿，且注入确实被读到（$(printf '%s\n' "$out" | grep -m1 "$2")）"
    elif [ "$code" = 0 ]; then
        echo "✘ 不误报  $1 → 判据绿了，但输出里找不到 $2：注入没进扫描面，这条是空跑"
    else
        echo "✘ 不误报  $1 → 判据被判红（应绿）：$(printf '%s\n' "$out" | grep '^×' | head -2 | tr '\n' ' ')"
    fi
    rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# ⚠ 注入后的文本不得再包含锚点原文：否则"把挂起调用注释掉"会被 grep 数成还在场 ⇒ 假绿。
swap() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swap 锚点没命中，破口根本没注入（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
assert old not in new, "SELF-MATCH: 替换文本里还留着锚点，判据会读到注释"
open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
PY
}
swap_all() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swap_all 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
open(p, 'w', encoding='utf-8').write(s.replace(old, new))
PY
}
# 行首前缀注入：锚点必须**原地保留**（这里要测的正是"同行前缀不计"），所以不适用 SELF-MATCH
pre() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ pre 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
assert new.startswith(old) is False and old in new, "PRE-FORM: 新文本必须以锚点结尾，前缀加在前面"
open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
PY
}
append_line() { python - "$1" "$2" <<'PY' || { echo "✘ append_line 失败（这条负控不成立）"; exit 2; }
import sys
p, line = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8').read()
open(p, 'w', encoding='utf-8').write(s.rstrip("\n") + "\n\n" + line + "\n")
PY
}
drop_line() { python - "$1" "$2" <<'PY' || { echo "✘ drop 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p, needle = sys.argv[1], sys.argv[2]
ls = open(p, encoding='utf-8').read().split("\n")
k = [i for i, x in enumerate(ls) if needle in x]
assert k, "ANCHOR-MISS: " + needle[:60]
del ls[k[0]]
open(p, 'w', encoding='utf-8').write("\n".join(ls))
PY
}

run
echo "[基线] $(printf '%s\n' "$out" | tail -2 | tr '\n' ' ')"
# 基线不绿就立刻收工：否则每条都会因为"文件本来就不在"打出 ✔，整轮负控是假的
# （判据簿只读不写，所以基线跑完不需要恢复；恢复只发生在每次注入之后）
[ "$code" = 0 ] || { echo "✘ 基线未通过，负控结果一律不采信"; exit 2; }

# ── RC-a 基数：在册面动了必须红（三套口径各钉一个数）───────────────────────
append_line "$REPO" 'private val negCtlRcExtra: Int = runCatching { 1 }.getOrDefault(0)'
ctl RC-a "多出一处生效 runCatching：基数 32→33（纯同步块，RC-b 照样 0，所以只有基数能抓）"

# ── RC-a 前两轮各新增的那一处：它们必须真在扫描面里，而不是只活在注释里 ──────
swap "$NAV" 'val currentPath = runCatching { navi.naviPath }' 'val currentPath = attempt { navi.naviPath }'
ctl RC-a "F-3 ⑭ 新增点 AmapNavigationEngine.kt:357 换成 attempt：基数 32→31 且出现次数 49→48 必须红（不红就是那个 +1 没被数到）"

# N-2（赛道 ⑨）新增的那一处：`attempt` 是 suspend，非挂起的 startGpsNavi 用不了它，所以它必须留在 runCatching 面里
swap "$NAV" 'runCatching { navi.setUseInnerVoice(voiceOn) }' 'attempt { navi.setUseInnerVoice(voiceOn) }'
ctl RC-a "N-2 新增点 AmapNavigationEngine.kt:367 换成 attempt：基数 32→31 同样必须红"

# ── RC-b 闭包判定：块内挂起调用必须抓到 ────────────────────────────────────
swap "$API" 'runCatching { JSONObject(text) }' 'runCatching { delay(1); JSONObject(text) }'
ctl RC-b "块内塞 stdlib 挂起入口 delay：violations 0→1（无歧义名单那条腿）"

swap "$API" 'runCatching { JSONObject(text) }' 'runCatching { Api.get(text) }'
ctl RC-b "块内塞项目内挂起函数 Api.get：violations 0→1（Owner.name 匹配声明者那条腿）"

# ── RC-b 反向：两条区分规则不许退化成名字交集 ───────────────────────────────
pre "$API" 'runCatching { JSONObject(text) }' 'withTimeoutOrNull(1) { null } ?: runCatching { JSONObject(text) }'
ctl_keep "挂起名出现在 runCatching 的**同行前缀**：块体从 { 之后取，这类点不该算 violation（历史上就是这里报的假阳性）" 'sites=32|violations=0|candidates=0'

swap "$API" 'runCatching { JSONObject(text) }' 'runCatching { Other.openMeteoWeather(g) }'
ctl_keep "同名挂起函数挂在**不匹配的接收者**上（真身是 Repo.openMeteoWeather）：只进 CANDIDATE，不进 VIOLATION" 'violations=0|candidates=1'

# ── RC-c 四处迁移点：整行字面量，不是"这文件里有 attempt"────────────────────
swap "$ROOTUI" 'deepLinkTarget(route, Prefs.role)?.let { attempt { nav.navigate(it) } }' \
               'deepLinkTarget(route, Prefs.role)?.let { runCatching { nav.navigate(it) } }'
ctl RC-c "深链那处退回 runCatching：CC3 掉、且回退残留计数必须抓住"

swap "$REPO" 'val forecast = attempt {' 'val fcNeg = attempt {'
ctl RC-c "变量名一改（仍是 attempt，取消语义没坏）：钉的是整行字面量，不是'这文件里有 attempt'"

swap "$DNS" 'if (ready.value) attempt { engine?.setLanguage(Locale.CHINESE) }' \
            'if (ready.value) runCatching { engine?.setLanguage(Locale.CHINESE) }'
ctl RC-c "TTS 设语种退回 runCatching：CC_BACK2 必须从 0 变 1"

# ── RC-d 免疫前提：那两条"结构免疫"的腿一旦非 0，32 点分类整表作废 ──────────
append_line "$API" 'private fun negCtlRcRb(): Int = runBlocking { 1 }'
ctl RC-d "出现 runBlocking：非 suspend 函数也能挂起，'保持'那 32 点的前提不再成立"

append_line "$API" 'private suspend fun negCtlRcScc(): Int = suspendCancellableCoroutine { }'
ctl RC-d "出现 suspendCancellableCoroutine：同上，桥接函数会把取消送进非 suspend 调用栈"

# ── RC-e 分类表：在册面与"本轮动作"列都要独立判红 ───────────────────────────
drop_line "$DOC" '| MainActivity.kt:68 | A/D | 否 | 否 | 保持 |'
ctl RC-e "删掉一行分类表：在册面 36→35（逐点审计少了一个人）"

drop_line "$DOC" '| core/navigation/AmapNavigationEngine.kt:357 | E | 否 | 否 | 保持 |'
ctl RC-e "删掉 F-3 ⑭ 新增的那一行分类表：在册 36→35 必须红 —— 判据数到 32 处，逐点账只有 31 行就说明有一处没人认领"

drop_line "$DOC" '| core/navigation/AmapNavigationEngine.kt:367 | E | 否 | 否 | 保持 |'
ctl RC-e "删掉 N-2（赛道 ⑨）新增的那一行：在册 36→35 同样必须红 —— 这一处正是'用不了 attempt 所以留在 runCatching'的那个形状，没人认领就等于它不存在"

swap "$DOC" '| core/code/QrCode.kt:33 | A | 否 | 否 | 保持 |' '| core/code/QrCode.kt:33 | A | 否 | 否 | 已迁 attempt |'
ctl RC-e "把一行 保持 改写成 已迁 attempt：总行 36 不变，但已迁列 4→5 必须单独抓"

# ── RC-a2 反证的反证：同一套管道若数不出 attempt，32 就不能信 ───────────────
python - "$A" <<'PY' || { echo "✘ attempt 扫描没找到任何 attempt {（这条负控不成立）"; exit 2; }
import os, sys
root = sys.argv[1]; n = 0
for d, _, fs in os.walk(root):
    for f in fs:
        if not f.endswith(".kt"):
            continue
        p = os.path.join(d, f)
        s = open(p, encoding='utf-8').read()
        if 'attempt {' in s:
            open(p, 'w', encoding='utf-8').write(s.replace('attempt {', 'runCatching {'))
            n += 1
assert n, "ANCHOR-MISS: attempt {"
sys.stderr.write("改了 %d 个文件\n" % n)
PY
ctl RC-a2 "把全工程 attempt 调用点整体退回 runCatching：RC-a2 的管道自证必须红（顺带 RC-b/RC-c 一起红）"

# ── 预检：输入不在场时必须是"跑不了"，不许是 0 ────────────────────────────
rm -f "$DET"; ctl_pre "检测器 python 脚本不在场：不许把'跑不了'读成 violations=0" '检测器'
mv "$SC/app/src/main/java/$PKG/data/repo/Repo.kt" "$SC/Repo.kt.bak"
ctl_pre "判据点名的源文件缺失：先验文件存在，缺文件不能读成计数 0" '判据文件缺失'

echo "── 附：基线复跑（确认每轮恢复都干净）──"
run
if [ "$code" = 0 ]; then echo "✔ 恢复后基线仍为绿：$(printf '%s\n' "$out" | tail -1)"; else
    echo "✘ 恢复后基线红：$(printf '%s\n' "$out" | grep '^×' | head -3 | tr '\n' ' ')（说明某轮恢复没还原，前面的 ✔ 全部要重看）"; exit 1; fi
