#!/usr/bin/env bash
# 启动与裁剪面判据（ST-a…ST-e，缺陷 #78）的负控制：逐条破口 → 确认 exit 1 且报的就是这一条。
# 只在临时目录副本上动手，工程真身不受影响（纯文本判据，不需要编译 ⇒ 可与其他脚本并行）。
# 用法：bash tools/startup_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3

PKG=com/shanxiang/buspost
PR="$SRC/tools/check_startup_probes.sh"
SC=/tmp/st_neg.$$
mkdir -p "$SC/app" "$SC/tools"
cp -r "$SRC/app/src" "$SC/app/src"
cp "$SRC/app/build.gradle.kts" "$SC/app/build.gradle.kts"
cp "$SRC/app/proguard-rules.pro" "$SC/app/proguard-rules.pro"
cp "$SRC/tools/release_minify_evidence.sh" "$SC/tools/release_minify_evidence.sh"
A="$SC/app/src/main/java/$PKG"
APP="$A/BusPostApp.kt"
ROOTUI="$A/ui/BusPostRoot.kt"
HOME="$A/ui/user/HomeScreen.kt"
SPLASH="$A/core/ui/SplashOverlay.kt"
PGR="$SC/app/proguard-rules.pro"
GRAD="$SC/app/build.gradle.kts"
EVI="$SC/tools/release_minify_evidence.sh"
for f in "$APP" "$ROOTUI" "$HOME" "$SPLASH" "$PGR" "$GRAD" "$EVI"; do
    [ -f "$f" ] || { echo "✘ 副本缺 $f，下面每一条都会假绿"; exit 2; }
done
CLEAN=/tmp/st_neg_clean.$$; cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

OKN=0; AMB=0; BAD=0
ctl() { # $1=判据编号 $2=说明
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" = 1 ] && echo "$out" | grep -q "^× $1"; then
    OKN=$((OKN + 1))
    echo "✔ $1  $2 → exit 1：$(echo "$out" | grep "^× $1" | head -1)"
  elif [ "$code" = 1 ]; then
    AMB=$((AMB + 1))
    echo "△ $1  $2 → exit 1，但抓到的是别的判据：$(echo "$out" | grep '^×' | head -1)"
  else
    BAD=$((BAD + 1))
    echo "✘ $1  $2 → 负控制失效（exit $code），这条判据等于没判据"
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
swap() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swap 锚点没命中，破口根本没注入（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
assert old not in new or old == new, "SELF-MATCH: 替换文本里不许带锚点原文"
open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
PY
}
swapall() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swapall 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
open(p, 'w', encoding='utf-8').write(s.replace(old, new))
PY
}
# 追加一行的注入必须走这个：替换文本必然包含锚点原文，`swap` 的 SELF-MATCH 断言会把它当自匹配挡掉
inject() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ inject 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
PY
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | tail -1)"
[ "$base_code" = 0 ] || { echo "✘ 基线未通过，负控结果一律不采信"; exit 2; }

# ---- ST-a：闪屏又替首屏白问一遍 ----
# 1 丢弃式预取回到根上（破口 = 在 splashShownOnce 之后插回原来那两笔）
inject "$ROOTUI" '        splashShownOnce = true' \
    '        splashShownOnce = true
        val negA = async { attempt { Repo.notices() } }
        negA.await()'
ctl ST-a "闪屏里再长出一笔取了就丢的请求：冷启动白问一次，首页那一轮还得再问"

# 2 首页那一轮的收场信号被摘（司机端就只剩 4 秒兜底）
swap "$HOME" '        AppState.markFirstScreenLoaded()' ''
ctl ST-a "首屏收场没人报了：阶段③永远不亮，用户对着闪屏等兜底计时"

# 3 根上不再读那个信号（写入点还在，但没人接）
swap "$ROOTUI" 'AppState.firstScreenLoaded' 'AppState.foreground'
ctl ST-a "信号没人接：闪屏的就绪条件退回''，进度条变成自说自话"

# ---- ST-b：闪屏的兜底与就绪口径 ----
# 4 4 秒自动进入被撤 ⇒ 取数挂住就没有出口
swap "$SPLASH" 'delay(4000)' 'delay(0)'
ctl ST-b "兜底计时被撤：后端不回答时用户被永久关在闪屏外"

# 5 多开一条"谁都能宣布已就绪"的通路
inject "$ROOTUI" '        splashProgress = 33' \
    '        splashProgress = 33
        splashReady = true'
ctl ST-b "就绪变成两处写入：数据还没回来就能点亮''轻触进入''"

# ---- ST-c：主线程那件事与分步计时 ----
# 6 WorkManager 初始化回到主线程
swap "$APP" 'withContext(Dispatchers.IO) { NotifyWorker.schedule(this@BusPostApp) }' \
            'NotifyWorker.schedule(this@BusPostApp)'
ctl ST-c "首次 WorkManager 初始化回到主线程：冷启动第一帧前多等一整包"

# 7 分步计时整体改名（下次再喊"打开慢"时没有读数可看）
swapall "$APP" 'bootMark(' 'markRenamed('
ctl ST-c "分步计时被摘：启动慢 again 时只能靠猜（猜错的方向是把 Prefs.init 挪后台）"

# 8 会话恢复跑到 Prefs.init 之前（那等于每次都要重登）
inject "$APP" '        Prefs.init(this)' \
    '        Repo.restoreSession()
        Prefs.init(this)'
ctl ST-c "restoreSession 排到 Prefs.init 之前：SharedPreferences 还没装载，会话永远读成空"

# ---- ST-d：keep 规则面 ----
# 9 别的工作区的占位注释回来
inject "$PGR" '# cargo-post-android 的 R8/ProGuard 规则' \
    '# WatchCam watch app - Phase 4.1 无需额外规则
# cargo-post-android 的 R8/ProGuard 规则'
ctl ST-d "外来项目的占位行回到规则文件：这份规则到底管哪个工程，两个答案"

# 10 worker 那条点名规则被删（改名后从数据库恢复任务时 ClassNotFoundException）
swap "$PGR" '-keep class com.shanxiang.buspost.notify.NotifyWorker { <init>(...); }' ''
ctl ST-d "NotifyWorker 的 keep 没了：裁剪包重启后从库里恢复任务就找不到类，到车提醒静默失效"

# 11 规则被成片注释掉（先四条 dontwarn，再两条 keepattributes ⇒ 行数掉到闸门以下）
swapall "$PGR" '-dontwarn' '#dontwarn'
swapall "$PGR" '-keepattributes' '#keepattributes'
ctl ST-d "规则成片被注释而不是按 missing_rules 点名补：行数闸门与 keepattributes 那格同时红"

# ---- ST-e：开裁剪那道闸 ----
# 12 枚举那两条被并成一条"好看"的写法（实测 R8 直接报语法错，但判据得先看见它少了哪条）
swap "$PGR" '-keepclassmembers enum com.shanxiang.buspost.data.model.NavIcon { *; }' ''
ctl ST-d "枚举点名规则从两条掉到一条：NavIcon 那族常量名没了保护，后端给的 LEFT/RIGHT 读不回来"

# 13 有人把 release 的开关翻了（本簿唯一的"不许悄悄翻"格）
#    ⚠ 锚点必须带上 release 那一段独有的注释行：两处 `isMinifyEnabled = false` 字面一模一样，
#    只锚那一行的话 `swap` 命中的是 **debug** 那处 ⇒ 注入落进错的支（本轮实测踩过：判据照样红，
#    但红的原因与这条破口想证的事不相干；两处都改成 true 才会同时动 STD 两个计数）。
swap "$GRAD" '            isMinifyEnabled = false' '            isMinifyEnabled = true'
swap "$GRAD" '            isMinifyEnabled = false' '            isMinifyEnabled = true'
ctl ST-e "release 的 isMinifyEnabled 被翻成 true：没重跑产物取证与真机走查就开裁剪"

# 14 取证脚本被删（翻开关的人失去唯一那只扫 dex 的手）
rm -f "$EVI"
ctl ST-e "产物级取证脚本不在场：没人扫过 dex 就翻开关，等于拿发布包试错"

echo "[汇总] ✔=$OKN △=$AMB ✘=$BAD（期望 14/0/0）"
[ "$OKN" = 14 ] && [ "$AMB" = 0 ] && [ "$BAD" = 0 ] || { echo "× 启动与裁剪面负控没跑满或没咬住"; exit 1; }
