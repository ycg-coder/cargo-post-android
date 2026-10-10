#!/usr/bin/env bash
# OS-a…OS-f（tools/check_oss_credits_probes.sh）的**负控制**（F-2 第 ⑭/⑮ 步的配套簿）。
# 用法：bash tools/oss_credits_negative_controls.sh
#
# 这一簿要回答的是 ⑭⑮ 最容易假绿的三件事：
#   1) "名单写了 17 条"与"矩阵真有 17 行"是两个不同的数，谁都可能只改一边就过去；
#   2) 矩阵里的整数是**抄** census 的，抄错或包重打后忘同步，文档就自说自话（OS-d2 专门钉这个）；
#   3) OS-e/OS-d2 的目标里有一堆 0 与"对不上=0"，而"扫不到"与"真的对得上"输出一模一样。
# 所以每条破口都必须把**它点名的那条**打红；另外两条反向（注释/叙述里提到禁止写法不许算残留，
# 且必须看得见注入进了扫描面），四条预检（APK/用例产物/矩阵/census 脚本不在场必须是 exit 2）。
#
# ⚠ Git Bash 会把以 `/` 开头的实参当路径折叠成 `/`（2026-10-01 实测踩过），所以注入的注释行一律
#   以空格缩进开头，swap/after 里还有一条 SLASH-MANGLED 断言兜着。
# ⚠ APK 是 114MB 的只读输入，不往沙盒里拷：BUSPOST_APK 直接指真身。破口都打在源码/矩阵/census 上，
#   指真身不会污染它；P1 那条则故意把 BUSPOST_APK 指到一个不存在的路径来验 exit 2。
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码，两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3

SC=/tmp/oss_neg.$$
CLEAN=/tmp/oss_neg_clean.$$
PKG=com/shanxiang/buspost
PR="$SRC/tools/check_oss_credits_probes.sh"
MATRIXREL=docs/android/THIRD_PARTY_RUNTIME_MATRIX.md
XMLREL=app/build/test-results/testDebugUnitTest/TEST-com.shanxiang.buspost.core.about.OpenSourceCreditsContractTest.xml
REAL_APK=${BUSPOST_APK:-$SRC/app/build/outputs/apk/debug/app-debug.apk}
APKARG="$REAL_APK"

mkdir -p "$SC/app/src/main" "$SC/app/src/test" "$SC/app/build/test-results/testDebugUnitTest" \
         "$SC/docs/android" "$SC/tools"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
cp "$SRC/$MATRIXREL" "$SC/$MATRIXREL"
cp "$SRC/tools/apk_oss_census.py" "$SC/tools/apk_oss_census.py"
[ -f "$SRC/$XMLREL" ] || { echo "✘ 真身没有 $XMLREL —— OS-f 的读数没有来源，先跑 :app:testDebugUnitTest"; exit 2; }
cp "$SRC/$XMLREL" "$SC/$XMLREL"
[ -f "$REAL_APK" ] || { echo "✘ 真身没有 $REAL_APK —— OS-e/OS-d2 没有'进 APK'这回事，先跑 assembleDebug"; exit 2; }

A="$SC/app/src/main/java/$PKG"
CRED="$A/core/about/OpenSourceCredits.kt"
SCREEN="$A/ui/user/OssLicensesScreen.kt"
ROOTUI="$A/ui/BusPostRoot.kt"
MINE="$A/ui/user/MinePages.kt"
MATRIX="$SC/$MATRIXREL"
CEN="$SC/tools/apk_oss_census.py"
TEST="$SC/app/src/test/java/$PKG/core/about/OpenSourceCreditsContractTest.kt"
for f in "$CRED" "$SCREEN" "$ROOTUI" "$MINE" "$MATRIX" "$CEN" "$TEST" "$SC/$XMLREL"; do
    [ -f "$f" ] || { echo "✘ 负控文件缺失：$f（副本没复制全，下面每一条都会假绿）"; exit 2; }
done
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT
cp -r "$SC" "$CLEAN"
[ -f "$CLEAN/$XMLREL" ] || { echo "✘ 干净副本没建成，本轮负控全部作废"; exit 2; }

FAILED=0
N_OK=0 N_BAD=0

run() { out=$(BUSPOST_ANDROID="$SC" BUSPOST_APK="$APKARG" bash "$PR" 2>&1); code=$?; }
ctl() { # $1=说明 $2=应当点名的判据（OS-x）
    run
    if [ "$code" = 1 ] && printf '%s\n' "$out" | grep -q "^× $2"; then
        echo "✔ $2  $1 → exit 1：$(printf '%s\n' "$out" | grep "^× $2" | head -1)"; N_OK=$((N_OK + 1))
    elif [ "$code" = 1 ]; then
        echo "△ $2  $1 → exit 1，但报的是别的判据：$(printf '%s\n' "$out" | grep '^× ' | head -1)"
        N_BAD=$((N_BAD + 1)); FAILED=1
    else
        echo "✘ $2  $1 → 没变红（exit $code），这条判据是永不为红的摆设"
        N_BAD=$((N_BAD + 1)); FAILED=1
    fi
    rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
# 预检类：输入不在场时必须是 exit 2（"跑不了"），不能被读成 exit 1 的普通破口，更不能是 0
ctl_pre() { # $1=说明 $2=期望输出里的关键词
    run
    if [ "$code" = 2 ] && printf '%s\n' "$out" | grep -q "$2"; then
        echo "✔ 预检  $1 → exit 2 且没有输出任何计数行"; N_OK=$((N_OK + 1))
    else
        echo "✘ 预检  $1 → 期望 exit 2，实测 exit $code：$(printf '%s\n' "$out" | head -1)"
        N_BAD=$((N_BAD + 1)); FAILED=1
    fi
    rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
# 反向负控：注入一个"叙述里提到禁止写法"的文本，判据必须**保持全绿**，
# 且 $2 这条正则必须能在输出里数到那个非零自证行（否则这条是"什么都没发生"的空跑）。
ctl_keep() { # $1=说明 $2=必须匹配上的输出行（正则）
    run
    if [ "$code" = 0 ] && ! printf '%s\n' "$out" | grep -q '^× ' && printf '%s\n' "$out" | grep -qE "$2"; then
        echo "✔ 不误报  $1 → 判据仍全绿，且注入确实被读到（$(printf '%s\n' "$out" | grep -m1 -E "$2")）"; N_OK=$((N_OK + 1))
    elif [ "$code" = 0 ]; then
        echo "✘ 不误报  $1 → 判据绿了，但输出里匹配不到 $2：注入没进扫描面，这条是空跑"; N_BAD=$((N_BAD + 1)); FAILED=1
    else
        echo "✘ 不误报  $1 → 判据被判红（应绿）：$(printf '%s\n' "$out" | grep '^× ' | head -2 | tr '\n' ' ')"
        N_BAD=$((N_BAD + 1)); FAILED=1
    fi
    rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# 注入器：锚点没命中就当场 exit 2 ——「以为注入了、其实树没动」是本工作区踩过两次的假绿来源（D-037）
# ⚠ 替换文本不得再包含锚点原文（SELF-MATCH），也不得以 / 开头（SLASH-MANGLED）。
swap() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swap 锚点没命中，破口根本没注入（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
assert old not in new, "SELF-MATCH: 替换文本里还留着锚点"
assert not new.startswith("/"), "SLASH-MANGLED: 替换文本以 / 开头，八成是 shell 把 // 折成了 /（给注释加缩进）"
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
PY
}
after() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ after 锚点没命中/不唯一（这条负控不成立）"; exit 2; }
import sys
p, needle, add = sys.argv[1], sys.argv[2], sys.argv[3]
ls = open(p, encoding='utf-8', newline='').read().split("\n")
k = [i for i, x in enumerate(ls) if needle in x]
assert len(k) == 1, "ANCHOR-NOT-UNIQUE(%s): %s" % (len(k), needle[:60])
assert not add.startswith("/"), "SLASH-MANGLED: 插入文本以 / 开头（给注释加缩进）"
ls[k[0] + 1:k[0] + 1] = add.split("\n")
open(p, 'w', encoding='utf-8', newline='').write("\n".join(ls))
PY
}
drop_line() { python - "$1" "$2" <<'PY' || { echo "✘ drop 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p, needle = sys.argv[1], sys.argv[2]
ls = open(p, encoding='utf-8', newline='').read().split("\n")
k = [i for i, x in enumerate(ls) if needle in x]
assert k, "ANCHOR-MISS: " + needle[:60]
del ls[k[0]]
open(p, 'w', encoding='utf-8', newline='').write("\n".join(ls))
PY
}
# 只在某个函数体窗口内删第一行命中：`val nav = LocalNavController.current` 在 MinePages.kt 里有 3 处，
# 直接 drop_line 会删到别的屏，那条负控就成了"假破口"（红的是别的原因）。
drop_in_window() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ drop_in_window 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p, start, needle = sys.argv[1], sys.argv[2], sys.argv[3]
ls = open(p, encoding='utf-8', newline='').read().split("\n")
k = [i for i, x in enumerate(ls) if start in x]
assert len(k) == 1, "START-NOT-UNIQUE(%s): %s" % (len(k), start[:60])
j = k[0]
while j < len(ls) and not ls[j].startswith("}"):
    j += 1
hit = [i for i in range(k[0], j) if needle in ls[i]]
assert hit, "WINDOW-MISS: %s 在 %s 的窗口内没找到" % (needle[:40], start[:40])
del ls[hit[0]]
open(p, 'w', encoding='utf-8', newline='').write("\n".join(ls))
PY
}

# ── OS-a ⑭ 点名的三行署名 ───────────────────────────────────────────────────
# 1 许可那一行改成不含 "Open Database License"：ODbL 署名义务缺一条腿
swap "$CRED" 'const val OSM_DATA_LICENSE = "数据许可：Open Database License (ODbL)"' \
             'const val OSM_DATA_LICENSE = "数据许可见许可证台账"'
ctl "署名三行里的 ODbL 许可那行被改成含糊说法" OS-a

# ── OS-b 入口可达的四条接线 ─────────────────────────────────────────────────
# 2 设置页那一行整行没了：署名在代码里，用户走不到
drop_line "$MINE" 'onClick = { nav.navigate(Routes.OSS_LICENSES) }'
ctl "设置页的入口行被删（⑭ 要的最小入口没了）" OS-b

# 3 路由不注册：路由表有名字，压栈时找不到屏
# 2026-10-10 改锚：旧写法用 `swap` 去换那一整行字面量，而那一行已被平板壳改成
#   `composable(Routes.OSS_LICENSES) { BusRouteBody { OssLicensesScreen() } }` ⇒ swap 会 ANCHOR-MISS 空跑，
#   这条注入就此变成"永远不红"的假注入。改成按行删（锚只点到路由名，壳怎么套都删得掉）。
drop_line "$ROOTUI" 'composable(Routes.OSS_LICENSES)'
ctl "BusPostRoot 不再注册这一屏" OS-b

# 4 屏不引用数据源，而是自己抄了一份字面量：同轮 OS-b 的"屏内重复文案=0"也会红
swap "$SCREEN" 'OpenSourceCredits.OSM_ATTRIBUTION,' \
               '"© OpenStreetMap contributors",'
ctl "屏里自己抄署名字面量（两处文案会各自漂移）" OS-b

# 5 设置页函数体里那句取 nav 的被删（窗口口径，不是整文件计数）
drop_in_window "$MINE" 'fun SettingsScreen() {' 'val nav = LocalNavController.current'
ctl "设置页拿不到 nav（入口点了没反应）" OS-b

# ── OS-c 名单在册面 ─────────────────────────────────────────────────────────
# 6 往内置名单里加一条没有 class_defs 证据的组件：名单与矩阵各说各话
after "$CRED" 'com.journeyapps:zxing-android-embedded:4.3.0' \
'        OssLibrary("探针组件（没有 APK 证据）", "com.google.zxing:core:3.5.3", "Apache License 2.0", "探针前缀/ 打包类"),'
ctl "内置名单被加了一条（条数口径必须跟着动）" OS-c

# ── OS-d 矩阵在册面 ─────────────────────────────────────────────────────────
# 7 矩阵里删掉 Okio 那一行：数据源说 17 条内置，矩阵只剩 16 行
drop_line "$MATRIX" '| Okio（传递依赖） |'
ctl "矩阵少了一行内置组件" OS-d

# 8 表头被改动一格（⑮ 点名的列名）：三份表格的表头不再是那张
swap "$MATRIX" '| Component | Gradle dependency | Actual APK | License | NOTICE |' \
               '| Component | Gradle dependency | APK? | License | NOTICE |'
ctl "⑮ 点名的表头字面量被改掉" OS-d

# ── OS-d2 矩阵里的数字 ↔ census 当下读数 ────────────────────────────────────
# 9 把 OkHttp 的打包类数抄错：文档自说自话，包里的数其实还是 319
swap "$MATRIX" '`okhttp3/` 319' '`okhttp3/` 999'
ctl "矩阵抄了个 census 数不出来的数字" OS-d2

# 10 census 有条"期望 0"的前缀，矩阵第四节却把这行删了：零实测没登记
drop_line "$MATRIX" '`org/maplibre/` 打包类 0'
ctl "census 有前缀、矩阵没行（少一行就是没登记）" OS-d2

# ── OS-e APK 实测面 ─────────────────────────────────────────────────────────
# 11 测量器少测一条前缀：INCLUDED 与"前缀行数自证"同时掉，矩阵那行失去依据
drop_line "$CEN" '"okio（okhttp 传递）", "okio/", None),'
ctl "census 前缀表被删了一条（测量面缩水）" OS-e

# 12 把一条"期望 0"的前缀指到真进了包的 okio/：反证位必须响
swap "$CEN" '("osmdroid（未内置，期望 0）", "org/osmdroid/", None),' \
            '("osmdroid（未内置，期望 0）", "okio/", None),'
ctl "'期望 0' 的前缀被指到真有字节码的包" OS-e

# ── OS-f 用例点名面 ─────────────────────────────────────────────────────────
# 13 点名的用例被改名：只数条数（9 例）抓不到，必须靠字面量
swap "$TEST" 'fun `高德 SDK 记在专有栏而不是开源栏`' 'fun `高德 SDK 那条被改名了`'
ctl "点名的用例被改名（条数没变，只有名字能抓）" OS-f

# ── 反向：注释/叙述里提到禁止写法，不算残留，但必须看得见注入进了扫描面 ──────
# 14 屏的 KDoc 里提一句署名文案：OS-b 的"屏内重复文案 0"不该因此红，而 OS-b2 自证行要从 0 变 1
after "$SCREEN" ' * 免得同一段 ODbL 文案在这里改一遍、在许可证台账里又改一遍，两处各自漂移。' \
' * 探针：这里提到 © OpenStreetMap 只是叙述，不该被算成屏内重复文案。'
ctl_keep "屏的注释里提到署名文案（不该判红）" '^OS-b2.*= 1 '

# 15 矩阵正文（非表格行）里用一次 INCLUDED_IN_APP（：表格两栏计数不该跟着动
after "$MATRIX" '这一节的每一条，census 的 `class_defs` 计数实测为 **0**。把这里的任何一行写进第二节，就是把"参考过"谎报成"分发过"。' \
'探针叙述：这一版把 INCLUDED_IN_APP（ 这个词也写在正文里，表格那两栏的计数不该跟着变。'
ctl_keep "矩阵正文里提到 INCLUDED_IN_APP（（不该判红）" '^OS-d3.*= 1 '

# ── 预检：输入不在场必须是 exit 2，不能被读成"计数 0" ───────────────────────
# 16 APK 不在场
APKARG="$SC/app/build/outputs/apk/debug/nope.apk"
ctl_pre "APK 不在场（'没打出来'不许读成'里面没有'）" '预检：没有'
APKARG="$REAL_APK"

# 17 用例产物不在场
rm -f "$SC/$XMLREL"
ctl_pre "用例 XML 不在场（没跑就没有读数）" '预检：没有'

# 18 矩阵不在场
rm -f "$MATRIX"
ctl_pre "矩阵文档不在场（判据文件缺失）" '判据文件缺失'

# 19 census 脚本不在场
rm -f "$CEN"
ctl_pre "census 脚本不在场（OS-d2/OS-e 没有读数来源）" '预检：没有 tools/apk_oss_census.py'

# ── 收尾：干净树必须复绿，否则上面所有"红了"都可能只是沙盒坏了 ───────────────
run
if [ "$code" = 0 ]; then
    echo "✔ 收尾复跑：干净副本 RESULT=PASS（exit 0），本轮 $((N_OK + N_BAD)) 条读数的地基是完好的"
else
    echo "✘ 收尾复跑没绿（exit $code）—— 本轮负控全部作废：$(printf '%s\n' "$out" | grep '^× ' | head -2 | tr '\n' ' ')"
    FAILED=1
fi

echo "SUMMARY|book=oss_credits|破口=13 反向=2 预检=4|命中=$((N_OK)) 失效=$((N_BAD))"
[ "$FAILED" = 0 ] && echo "RESULT=PASS" || echo "RESULT=FAIL"
exit "$FAILED"
