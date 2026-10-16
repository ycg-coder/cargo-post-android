#!/usr/bin/env bash
# F-2 第 ⑭/⑮ 步：App 内 OSM/ODbL 署名入口 + 第三方运行时矩阵的判据（OS-a…OS-f）。
# 用法：bash tools/check_oss_credits_probes.sh     退出码非 0 = 这一层的契约破了
#       bash tools/oss_credits_negative_controls.sh 逐条打破口，确认每条都会红
#
# 钉的东西（指令 v2.1 ⑭⑮ 原文）：
#   ⑭ "补最小入口（设置 → 关于乡邮智联 → 地图与开源许可），至少显示 © OpenStreetMap contributors、
#       'OpenStreetMap 数据用于道路/地图相关功能'、'数据许可：Open Database License (ODbL)'，并列出实际进 APK 的 OSS"
#       —— "入口可达"与"文案在场"都要有，缺一侧就是摆设；
#   ⑮ 表头 | Component | Gradle dependency | Actual APK | License | NOTICE |，REFERENCE_ONLY 与 INCLUDED_IN_APP 分开，
#       "APK 没打进去就不许写 INCLUDED_IN_APP" —— 所以 OS-e 直接读 APK 的 class_defs，不读任何自述。
#
# 与其它簿的分工：THIRD_PARTY_NOTICES.md 是许可证台账（人读的），本簿钉的是"台账、代码、APK 三方对得上"。
# 写法沿用既有教训：先验文件存在；整行字面量不用行号；注释行排除（NC）；"0 命中"必须旁边站一个数得出非零的同形锚点；
# APK / 用例产物不在场一律 exit 2 —— "没打出来"不许读成"里面没有"。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

S=app/src/main/java/com/shanxiang/buspost
CRED="$S/core/about/OpenSourceCredits.kt"
SCREEN="$S/ui/user/OssLicensesScreen.kt"
ROUTES="$S/nav/Routes.kt"
ROOTUI="$S/ui/BusPostRoot.kt"
MINE="$S/ui/user/MinePages.kt"
MATRIX=docs/android/THIRD_PARTY_RUNTIME_MATRIX.md
TEST=app/src/test/java/com/shanxiang/buspost/core/about/OpenSourceCreditsContractTest.kt
XML=app/build/test-results/testDebugUnitTest/TEST-com.shanxiang.buspost.core.about.OpenSourceCreditsContractTest.xml
APK=${BUSPOST_APK:-app/build/outputs/apk/debug/app-debug.apk}
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'

for f in "$CRED" "$SCREEN" "$ROUTES" "$ROOTUI" "$MINE" "$MATRIX" "$TEST"; do
    [ -f "$f" ] || { echo "× 判据文件缺失：$f（先确认工程根/沙盒副本建对，别把缺文件读成计数 0）"; exit 2; }
done

G=$(printf 'aa bb\n' | grep -c 'bb')
[ "$G" = 1 ] || { echo "× GUARD 计数管道不工作（实测 $G），本本所有 0 一律不采信"; exit 3; }
echo "GUARD 计数管道               = $G （目标 1）"

# ---------- OS-a 三条署名文案在数据源里逐字在场 ----------
OSA_ATTR=$(grep -cF 'const val OSM_ATTRIBUTION = "© OpenStreetMap contributors"' "$CRED")
OSA_USAGE=$(grep -cF 'const val OSM_USAGE = "OpenStreetMap 数据用于道路/地图相关功能"' "$CRED")
OSA_LIC=$(grep -cF 'const val OSM_DATA_LICENSE = "数据许可：Open Database License (ODbL)"' "$CRED")
OSA_WHERE=$(grep -cF 'const val OSM_WHERE' "$CRED")
# 反证：同一套 grep 在同一个文件里必须数得出 4 条 OSM_* 常量，否则上面那个 1 可能是管道坏了
OSA_PROOF=$(grep -c 'const val OSM_' "$CRED")

# ---------- OS-b 入口可达：路由、注册、设置页那一行、屏真的渲染数据源 ----------
OSB_ROUTE=$(grep -cF 'const val OSS_LICENSES = "ossLicenses"' "$ROUTES")
# 2026-10-10 重锚：上一轮（未入账那段）给每条路由套了 `BusRouteBody` 平板壳，注册行变成了
#   `composable(Routes.OSS_LICENSES) { BusRouteBody { OssLicensesScreen() } }`，旧的字面整行锚当场读 0
#   （本轮实测 OS-b 那一格 1/0/1/1/1/0 —— 路由名、入口行、nav、屏引用全在，只有"注册"这格是判据瞎了）。
#   改成**按内容判**：同一行里既有这条路由的注册、又有这块屏的调用点，中间夹什么壳都算；
#   删掉整行仍然读 0 ⇒ 由 oss_credits_negative_controls.sh 第 3 条注入钉（锚点同轮改成今天的形状）。
OSB_HOST=$(grep -cE 'composable\(Routes\.OSS_LICENSES\).*OssLicensesScreen\(\)' "$ROOTUI")
OSB_ENTRY=$(grep -cF 'onClick = { nav.navigate(Routes.OSS_LICENSES) }' "$MINE")
# ⚠ 口径：整文件数 `LocalNavController.current` 会把别的屏算进来（MinePages.kt 里共 3 处），
#   这里要的是"设置页那一个 composable 拿得到 nav"，所以取 SettingsScreen 函数体窗口，目标 1。
OSB_LOCAL=$(awk '/^fun SettingsScreen\(\) \{/,/^\}/' "$MINE" | grep -cF 'val nav = LocalNavController.current')
OSB_USE=$(grep -cF 'OpenSourceCredits.OSM_ATTRIBUTION' "$SCREEN")
# 署名文案只许有一份：屏里再抄一份字面量，改台账时就会漏改这里（目标 0）。
# ⚠ 注释里"提到"这句署名不算抄写（同 NW 簿的 NC 口径），所以按整行排除注释；
#   被排除掉的那几行单独数成 OSB_DUP_CMT 打出来，负控才知道注入真进了扫描面（否则 0 是空口）。
OSB_DUP=$(grep -nF '© OpenStreetMap' "$SCREEN" | grep -vE "$NC" | wc -l | tr -d ' ')
OSB_DUP_CMT=$(grep -nF '© OpenStreetMap' "$SCREEN" | grep -cE "$NC")

# ---------- OS-c 名单在册面 ----------
# 口径：awk 窗口取列表体内的 `OssLibrary(` 行数；窗口先自证数得出非零，那个"专有 1 条"才有意义
OSC_INCL=$(awk '/val included: List<OssLibrary> = listOf\(/,/^\s*\)$/' "$CRED" | grep -c 'OssLibrary(')
OSC_PROP=$(awk '/val proprietary: List<OssLibrary> = listOf\(/,/^\s*\)$/' "$CRED" | grep -c 'OssLibrary(')
OSC_REF=$(awk '/val referenceOnly: List<String> = listOf\(/,/^\s*\)$/' "$CRED" | grep -cE '^\s+"')
OSC_TOTAL=$(grep -c 'OssLibrary(' "$CRED")

# ---------- OS-e APK 实测：census 直接读 class_defs，不读任何自述 ----------
# 放在 OS-d2 之前：那一节要把矩阵里的数字与**同一份** census 读数对齐，两次跑包会数出两个世界的数。
PY=$(command -v python || command -v python3)
[ -n "$PY" ] || { echo "× 找不到 python：OS-e 的 APK 实测无法执行，不许把'跑不了'读成 0"; exit 2; }
[ -f tools/apk_oss_census.py ] || { echo "× 预检：没有 tools/apk_oss_census.py —— OS-d2/OS-e 没有读数来源（沙盒副本要把这个脚本一起拷上）"; exit 2; }
[ -f "$APK" ] || { echo "× 预检：没有 $APK —— 没打出包来就没有'进 APK'这回事（先跑 assembleDebug，或 BUSPOST_APK=… 指定）"; exit 2; }
CEN=$(PYTHONIOENCODING=utf-8 "$PY" tools/apk_oss_census.py "$APK" --strings 2>&1); cen_code=$?
# census 的退出码分两类，不许混：1 = **反证位红了**（有条"期望 0"的前缀在包里真有字节码），
# 那是这层的契约破了，要作为 OS-e 的失败报出去；≥2 = 包打不开/头解析不出数 = 没读数，exit 2。
[ "$cen_code" -le 1 ] || { echo "× OS-e census 退出码 $cen_code（读不出数），本本不采信：$(printf '%s\n' "$CEN" | tail -3 | tr '\n' ' ')"; exit 2; }
OSE_CEN_GUARD=$(printf '%s\n' "$CEN" | grep -c '^× 反证位')
# 表头行 `PREFIX|name|prefix|defined|referenced|verdict` 也是 `^PREFIX|`，混进计数就会 28→29。
# ⚠ 这里显式剔掉表头，而不是让每条 grep 各自小心：OS-d2 的反向循环读的是同一份 CENP。
CENP=$(printf '%s\n' "$CEN" | grep '^PREFIX|' | grep -v '^PREFIX|name|')
OSE_INCLUDED=$(printf '%s\n' "$CENP" | grep -c 'INCLUDED_IN_APP')
OSE_NOT=$(printf '%s\n' "$CENP" | grep -c 'NOT_IN_APK')
# 反证位：label 里写着"期望 0"的那些前缀，打包类必须真的是 0（非 0 就是矩阵把参考过的写成了内置）
OSE_EXPECT_ZERO=$(printf '%s\n' "$CENP" | grep '期望 0' | grep -c 'INCLUDED_IN_APP')
# 自证：census 确实数得出"内置"这一侧（19 条），否则 OSE_NOT=9 可能只是前缀表空了
OSE_PROOF=$(printf '%s\n' "$CENP" | grep -c .)
OSE_OSM=$(printf '%s\n' "$CEN" | grep '^STRING|© OpenStreetMap contributors|' | cut -d'|' -f3)
OSE_ODBL=$(printf '%s\n' "$CEN" | grep '^STRING|ODbL|' | cut -d'|' -f3)
OSE_DEXDEF=$(printf '%s\n' "$CEN" | grep -o '打包类(defined) = [0-9]*' | cut -d= -f2 | tr -d ' ')

# ---------- OS-d 名单 ↔ 矩阵：每条内置坐标都得在矩阵里有一行 ----------
COORDS=$(grep -o '"[a-z0-9_.-]*\.[a-z0-9_-]*:[a-zA-Z0-9_.-]*:[0-9][^"]*"' "$CRED" | tr -d '"')
OSD_TOTAL=$(printf '%s\n' "$COORDS" | grep -c .)
OSD_MISS=0
MISSING_LIST=""
while IFS= read -r c; do
    [ -n "$c" ] || continue
    if ! grep -qF "\`$c\`" "$MATRIX"; then
        OSD_MISS=$((OSD_MISS + 1)); MISSING_LIST="$MISSING_LIST $c"
    fi
done <<< "$COORDS"
OSD_HEAD=$(grep -cF '| Component | Gradle dependency | Actual APK | License | NOTICE |' "$MATRIX")
# ⚠ 口径：数的是**表格单元里的写法** `INCLUDED_IN_APP（` / `REFERENCE_ONLY（`（带全角括号），且只数表格行；
#   正文里那些"`INCLUDED_IN_APP`："式的叙述不算——否则同一份文档换个说法就把计数打乱。
OSD_INCL=$(grep -E '^\|' "$MATRIX" | grep -c 'INCLUDED_IN_APP（')
OSD_REF=$(grep -E '^\|' "$MATRIX" | grep -c 'REFERENCE_ONLY（')
# 自证：正文（非表格行）里提到这个词的次数单独打出来。它在干净树里是 0，负控注入一行叙述就会变 1，
# 而上面两个数不动——这才说明"注释/叙述不误报"不是靠没扫到。
OSD_PROSE=$(grep -vE '^\|' "$MATRIX" | grep -c 'INCLUDED_IN_APP（')
# 反证：矩阵里必须真有一条"打包类 0"的实测行（REFERENCE_ONLY 全靠它），数不出来就是空表。
# 只数表格行：正文里也会出现"打包类 0"这个词，混着数就成了 9+叙述。
OSD_ZERO=$(grep -E '^\|' "$MATRIX" | grep -c '打包类 0')

# ---------- OS-d2 矩阵里的每一个数字，都得是当下这个包数出来的（双向对齐） ----------
# 为什么要有这一条：第二节的整数是抄 census 的，抄错了（或包重打后忘了同步）文档就自说自话。
# 正向：矩阵表格里每个"`前缀` 数字"都要在 census 里有同一前缀且数相等；
# 反向：census 输出的每条前缀都要在矩阵里以"同前缀 + 同数"出现（少一行就是没登记）。
OSD2_BAD=0; OSD2_LIST=""; OSD2_PAIRS=0
while IFS= read -r pair; do
    [ -n "$pair" ] || continue
    p=$(printf '%s' "$pair" | awk '{print $1}' | tr -d '`')
    n=$(printf '%s' "$pair" | awk '{for(i=2;i<=NF;i++){t=$i; gsub(/[^0-9]/,"",t); if(t!=""){print t; exit}}}')
    OSD2_PAIRS=$((OSD2_PAIRS + 1))
    c=$(printf '%s\n' "$CENP" | awk -F'|' -v p="$p" '$3==p {print $4}')
    if [ -z "$c" ]; then OSD2_BAD=$((OSD2_BAD + 1)); OSD2_LIST="$OSD2_LIST $p(矩阵有、census 无此前缀)"
    elif [ "$c" != "$n" ]; then OSD2_BAD=$((OSD2_BAD + 1)); OSD2_LIST="$OSD2_LIST $p(矩阵$n/实测$c)"; fi
done <<< "$(grep -E '^\|' "$MATRIX" | grep -oE '`[a-z][a-z0-9_./]*/`[^0-9]*[0-9][0-9,]*')"
OSD2_REV=0; OSD2_REVLIST=""
while IFS='|' read -r _ _ pfx dn _; do
    [ -n "$pfx" ] || continue
    if ! grep -E '^\|' "$MATRIX" | tr -d ',' | grep -qE "\`$pfx\`[^0-9]*$dn([^0-9]|$)"; then
        OSD2_REV=$((OSD2_REV + 1)); OSD2_REVLIST="$OSD2_REVLIST $pfx=$dn"
    fi
done <<< "$CENP"

echo "OS-a 三行署名/口径行/管道自证            = $OSA_ATTR$OSA_USAGE$OSA_LIC/$OSA_WHERE/$OSA_PROOF （目标 111/1/4）"
echo "OS-b 路由/注册/设置入口/窗口nav/屏引用/屏内重复文案 = $OSB_ROUTE/$OSB_HOST/$OSB_ENTRY/$OSB_LOCAL/$OSB_USE/$OSB_DUP （目标 1/1/1/1/1/0）"
echo "OS-b2 屏内注释提到署名的行数（被排除面，注入自证用）  = $OSB_DUP_CMT （目标 0，非判据）"
echo "OS-c 内置/专有/未进包/字面量总次数        = $OSC_INCL/$OSC_PROP/$OSC_REF/$OSC_TOTAL （目标 17/1/6/19）"
echo "OS-d 坐标漏登/总数/表头/内置行/参考行/零证据行 = $OSD_MISS/$OSD_TOTAL/$OSD_HEAD/$OSD_INCL/$OSD_REF/$OSD_ZERO （目标 0/18/3/18/10/9）"
echo "OS-d3 叙述里提到的 INCLUDED_IN_APP（（被排除面，注入自证用） = $OSD_PROSE （目标 0，非判据）"
echo "OS-d2 矩阵↔census 对不上的（正/反）与对上的对数 = $OSD2_BAD/$OSD2_REV/$OSD2_PAIRS （目标 0/0/28）"
echo "OS-e APK 实测 INCLUDED/NOT/期望0却内置/前缀行数/署名串/ODbL串/打包类 = $OSE_INCLUDED/$OSE_NOT/$OSE_EXPECT_ZERO/$OSE_PROOF/$OSE_OSM/$OSE_ODBL/$OSE_DEXDEF （目标 19/9/0/28/≥1/≥1/>0）"

fail=0
[ "$OSA_ATTR" = 1 ] || { echo "× OS-a ⑭ 点名的第一行署名不在位（实测 $OSA_ATTR）"; fail=1; }
[ "$OSA_USAGE" = 1 ] || { echo "× OS-a 'OpenStreetMap 数据用于道路/地图相关功能'不在位（实测 $OSA_USAGE）"; fail=1; }
[ "$OSA_LIC" = 1 ] || { echo "× OS-a '数据许可：Open Database License (ODbL)'不在位（实测 $OSA_LIC）：ODbL §4 的署名义务少了许可那一行"; fail=1; }
[ "$OSA_WHERE" = 1 ] || { echo "× OS-a 数据来源口径行没了：只剩三行字，说不清这署名为什么该在 App 里"; fail=1; }
[ "$OSA_PROOF" = 4 ] || { echo "× OS-a 管道自证失败：同一文件里 OSM_* 常量不是 4 条（实测 $OSA_PROOF），上面那三个 1 不可信"; fail=1; }
[ "$OSB_ROUTE" = 1 ] || { echo "× OS-b 路由常量不在位：入口无从跳转"; fail=1; }
[ "$OSB_HOST" = 1 ] || { echo "× OS-b BusPostRoot 没注册这一路由：路由表有名字但压栈时找不到屏"; fail=1; }
[ "$OSB_ENTRY" = 1 ] || { echo "× OS-b 设置页那一行没了：署名在代码里但用户走不到（⑭ 要的是'最小入口'）"; fail=1; }
[ "$OSB_LOCAL" = 1 ] || { echo "× OS-b 设置页函数体内拿不到 nav（窗口实测 $OSB_LOCAL，目标 1）：入口点了没反应"; fail=1; }
[ "$OSB_USE" = 1 ] || { echo "× OS-b 屏不再渲染数据源的署名常量：改台账时这一屏不会跟着变"; fail=1; }
[ "$OSB_DUP" = 0 ] || { echo "× OS-b 屏里自己抄了一份 OSM 署名字面量（$OSB_DUP 处）：两处文案会各自漂移"; fail=1; }
[ "$OSC_INCL" = 17 ] || { echo "× OS-c 内置开源组件不是 17 条（实测 $OSC_INCL）：依赖动了没同步名单与矩阵"; fail=1; }
[ "$OSC_PROP" = 1 ] || { echo "× OS-c 专有组件不是 1 条（实测 $OSC_PROP）：高德 SDK 被并进 OSS 名单了？"; fail=1; }
[ "$OSC_REF" = 6 ] || { echo "× OS-c 未进包条目不是 6 条（实测 $OSC_REF）：'参考过'那一栏被删空了就成了摆设"; fail=1; }
[ "$OSC_TOTAL" = 19 ] || { echo "× OS-c 文件里 OssLibrary( 不是 19 次（实测 $OSC_TOTAL）：口径=数据类声明 1 + 内置 17 + 专有 1（那条跨 5 行也只算 1 次），与逐条窗口口径不可互引"; fail=1; }
[ "$OSD_MISS" = 0 ] || { echo "× OS-d 有内置坐标在矩阵里没有行：$MISSING_LIST"; fail=1; }
[ "$OSD_TOTAL" = 18 ] || { echo "× OS-d 名单坐标总数不是 18（实测 $OSD_TOTAL）：抽取规则或名单变了，两栏必须一起复核（17 内置 + 1 专有）"; fail=1; }
[ "$OSD_HEAD" = 3 ] || { echo "× OS-d 矩阵表头不是 3 张（实测 $OSD_HEAD）：⑮ 要求的表头少了一节（二/三/四各一张）"; fail=1; }
[ "$OSD_INCL" = 18 ] || { echo "× OS-d 矩阵 INCLUDED_IN_APP（ 的表格写法不是 18（实测 $OSD_INCL）"; fail=1; }
[ "$OSD_REF" = 10 ] || { echo "× OS-d 矩阵 REFERENCE_ONLY（ 的表格写法不是 10（实测 $OSD_REF）"; fail=1; }
[ "$OSD_ZERO" = 9 ] || { echo "× OS-d 矩阵表格里'打包类 0'的实测行不是 9（实测 $OSD_ZERO）：REFERENCE_ONLY 全靠这些数，数不出来就是空表"; fail=1; }
# OS-d2 双向对齐：矩阵的数字必须是这个包数出来的，反过来 census 每条前缀都必须有登记行。
# 只钉"对不上 0"不够——两栏同时被删空也会是 0，所以配对数必须是 28。
[ "$OSD2_PAIRS" = 28 ] || { echo "× OS-d2 从矩阵表格里只抽出 $OSD2_PAIRS 组（前缀,数)（目标 28 = 第二/三节 19 条内置前缀 + 第四节 9 条零实测前缀）：抽取面变小了，下面那两个 0 就没有依据"; fail=1; }
[ "$OSD2_BAD" = 0 ] || { echo "× OS-d2 矩阵里有 $OSD2_BAD 组数字与 census 当下读数不一致：$OSD2_LIST"; fail=1; }
[ "$OSD2_REV" = 0 ] || { echo "× OS-d2 census 有 $OSD2_REV 条前缀在矩阵里没有对应行（少一行就是没登记）：$OSD2_REVLIST"; fail=1; }
[ "$OSE_INCLUDED" = 19 ] || { echo "× OS-e APK 里 INCLUDED_IN_APP 前缀不是 19（实测 $OSE_INCLUDED）：矩阵第二/三节与真包对不上"; fail=1; }
[ "$OSE_NOT" = 9 ] || { echo "× OS-e APK 里 NOT_IN_APK 前缀不是 9（实测 $OSE_NOT）：census 前缀表被改动过，矩阵第四节的 0 就没了依据"; fail=1; }
[ "$OSE_PROOF" = 28 ] || { echo "× OS-e census 只输出 $OSE_PROOF 条前缀行：前缀表缩水了，上面 INCLUDED/NOT 两个数都不可信"; fail=1; }
[ "$OSE_EXPECT_ZERO" = 0 ] || { echo "× OS-e 有'期望 0'的前缀在 APK 里真有字节码（$OSE_EXPECT_ZERO 条）：那就是把参考过写成内置"; fail=1; }
# 与 census 自己的反证位互证：两个口径来自同一份读数但算法不同（一个数 verdict，一个数脚本打的 × 行）
[ "$OSE_CEN_GUARD" = 0 ] || { echo "× OS-e census 自己就报了 $OSE_CEN_GUARD 条反证位红：$(printf '%s\n' "$CEN" | grep '^× 反证位' | head -1)"; fail=1; }
[ "${OSE_DEXDEF:-0}" -gt 0 ] || { echo "× OS-e 打包类数为 0：dex 解析没读到东西，本节的数全部不采信"; fail=1; }
[ "${OSE_OSM:-0}" -ge 1 ] || { echo "× OS-e APK 里没有 '© OpenStreetMap contributors' 这串字节（实测 $OSE_OSM）：源码补了入口但没重打包，用户手里那份还是旧的"; fail=1; }
[ "${OSE_ODBL:-0}" -ge 1 ] || { echo "× OS-e APK 里没有 ODbL 字样（实测 $OSE_ODBL）：同上，必须重打 APK"; fail=1; }

# ---------- OS-f 用例真实读数（只认 gradle 产物，不认自述） ----------
# 同 NW-h：产物缺就先真跑一次无过滤全量（队列 #96）。这两本是上一批里唯一不是代码红的两本红，
# 根因是**批自己删产物**，不是判据松；用快照还原等于拿陈旧证据冒充新鲜读数，禁止。
bash tools/ensure_test_xml.sh "$XML" || true
if [ -f "$XML" ]; then
    OSF_T=$(sed -n 's/.*<testsuite[^>]*tests="\([0-9]*\)".*/\1/p' "$XML" | head -1)
    OSF_F=$(sed -n 's/.*<testsuite[^>]*failures="\([0-9]*\)".*/\1/p' "$XML" | head -1)
    OSF_E=$(sed -n 's/.*<testsuite[^>]*errors="\([0-9]*\)".*/\1/p' "$XML" | head -1)
else
    echo "× 预检：没有 $XML —— 用例没跑就没有读数，这里不许填 0/0/0 交差（先跑 :app:testDebugUnitTest）"; exit 2
fi
OSF_NAME1=$(grep -cF 'fun `OSM 署名三行逐字钉住`' "$TEST")
OSF_NAME2=$(grep -cF 'fun `参考过的东西不许混进内置名单`' "$TEST")
OSF_NAME3=$(grep -cF 'fun `高德 SDK 记在专有栏而不是开源栏`' "$TEST")
OSF_NAME4=$(grep -cF 'fun `署名口径是数据来源而非内置瓦片`' "$TEST")
OSF_NAME5=$(grep -cF 'fun `传递依赖也按实测进名单`' "$TEST")
echo "OS-f 用例 通过/失败/错误                 = $OSF_T/$OSF_F/$OSF_E （目标 ≥9/0/0）"
echo "OS-f2 点名用例在场                       = $OSF_NAME1$OSF_NAME2$OSF_NAME3$OSF_NAME4$OSF_NAME5 （目标 11111）"
[ "$OSF_T" -ge 9 ] || { echo "× OS-f 用例数少于 9（实测 $OSF_T）：⑭ 点名的署名/分类/传递依赖三类核对被删了？"; fail=1; }
[ "$OSF_F" = 0 ] || { echo "× OS-f 用例失败 $OSF_F 例"; fail=1; }
[ "$OSF_E" = 0 ] || { echo "× OS-f 用例错误 $OSF_E 例"; fail=1; }
[ "$OSF_NAME1$OSF_NAME2$OSF_NAME3$OSF_NAME4$OSF_NAME5" = 11111 ] || { echo "× OS-f2 有用例被改名或删掉（实测 $OSF_NAME1$OSF_NAME2$OSF_NAME3$OSF_NAME4$OSF_NAME5）：只数条数抓不到"; fail=1; }

echo "SUMMARY|rows=7|OS-a=$OSA_ATTR$OSA_USAGE$OSA_LIC/$OSA_PROOF OS-b=$OSB_ROUTE/$OSB_HOST/$OSB_ENTRY/$OSB_DUP OS-c=$OSC_INCL/$OSC_PROP/$OSC_REF OS-d=$OSD_MISS/$OSD_TOTAL/$OSD_HEAD/$OSD_ZERO OS-d2=$OSD2_BAD/$OSD2_REV/$OSD2_PAIRS OS-e=$OSE_INCLUDED/$OSE_NOT/$OSE_EXPECT_ZERO/$OSE_OSM OS-f=$OSF_T/$OSF_F/$OSF_E"
[ "$fail" = 0 ] && echo "RESULT=PASS" || echo "RESULT=FAIL"
exit "$fail"
