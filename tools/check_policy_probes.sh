#!/usr/bin/env bash
# #49 之后补的"隐私政策 / 用户协议正文页"结构判据（配 tools/policy_negative_controls.sh）
# 管的是三件事：入口在不在、正文只有一个出处、登录勾选闸门没被入口改动弄丢。
# 用法：bash tools/check_policy_probes.sh        退出码非 0 = 这一格被拆了
# 写法：按内容/整串过滤，不按行号；每个"目标 0"都配一条同机制反证（同一模式在数据源那侧必须数得出非 0）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
RT=$M/nav/Routes.kt
BR=$M/ui/BusPostRoot.kt
LS=$M/ui/login/LoginScreen.kt
LV=$M/ui/login/LoginViewModel.kt
MP=$M/ui/user/MinePages.kt
PS=$M/ui/user/PolicyScreen.kt
AP=$M/core/about/AppPolicies.kt
CT=app/src/test/java/com/shanxiang/buspost/core/about/AppPoliciesContractTest.kt
KT=(--include=*.kt)
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'
# 「一句正文」= 字符串字面量里有句末标点（。！？）。用它判"正文有没有被抄进页面"。
# ⚠ 2026-10-14 换写法：原来是 `"[^"]*[一-龥]{12,}[^"]*"`（≥12 个连续汉字），
#   那是**依赖 locale 的字节区间**：实测本机 `LC_ALL=C` 下过配成 64、`C.UTF-8` 下才是 25，
#   而 GitHub runner 上直接数出 **0**（PL-h 读成 0/0），把"这句判据没人验"伪装成"正文被删空"。
#   换成多字节**字面量**取数后：`LC_ALL=C` 与 `C.UTF-8` 都是 43/0，两种 locale 一字不差
#   （同一本里 PL-f/PL-g 那种字面串 grep 在 CI 一直是好的 ⇒ 坏的只有"区间"这一件事）。
#   代价：句末标点是"一句"的定义，改正文时这个数要同轮改（原 25 → 现 43），与 SU-b 同一套摩擦设计。
PROSE='"[^"]*。[^"]*"|"[^"]*！[^"]*"|"[^"]*？[^"]*"'

for f in "$RT" "$BR" "$LS" "$MP" "$PS" "$AP" "$CT"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

# PL-a 两条路由常量各恰好 1
PLA=$(grep -c 'const val PRIVACY = "privacy"' "$RT")
PLA2=$(grep -c 'const val TERMS = "terms"' "$RT")

# PL-b 注册点：两个都要在，且都走 BusRouteBody（键盘/边到边口径与全工程一致）
PLB=$(grep -cE 'composable\(Routes\.(PRIVACY|TERMS)\)' "$BR")
PLB2=$(grep -E 'composable\(Routes\.(PRIVACY|TERMS)\)' "$BR" | grep -c 'BusRouteBody')

# PL-c 这两页**不设角色/登录闸门**（用户在同意之前就得看到正文）。
#   0 命中的反证：同一套写法在司机域路由上数得到（DRIVER_ONLY 那确实是一道闸）。
PLC=$(grep -cE 'DRIVER_ONLY|USER_ONLY' "$RT" | head -1)
PLC_IN=$(grep -A 4 'val DRIVER_ONLY' "$RT" | grep -cE 'PRIVACY|TERMS')
PLC_PROOF=$(grep -c 'DRIVER_ONLY = setOf' "$RT")

# PL-d/e 入口：登录页两个可点链接（定义 1 + 调用 2）；关于页两行
PLD=$(grep -c 'PolicyLink(' "$LS")
PLE=$(grep -cE 'onClick = \{ nav\.navigate\(Routes\.(PRIVACY|TERMS)\) \}' "$MP")

# PL-f/g 正文只有一个出处：这两句只许出现在数据源里一次
PLF=$(grep -rn '不构成法律意见' "$M" --include=*.kt | wc -l | tr -d ' ')
PLG=$(grep -rn '不提供网上预约乘车' "$M" --include=*.kt | wc -l | tr -d ' ')

# PL-h 页面不许自己抄正文（0 命中 + 同模式在数据源侧数得出）。
#   反证那一侧钉**恰好 25**（2026-10-12 实测）而不是"≥20"：负控 P-7b 实测过 ——
#   写 ≥20 时从数据源删掉一句正文，判据仍读 24 通过 ⇒ 那个"页面 0 命中"就再没人能证明不是空的。
#   代价是改正文要同轮改这个数（与 SU-b 同一套摩擦设计）。
PLH=$(grep -oE "$PROSE" "$PS" | wc -l | tr -d ' ')
PLH_PROOF=$(grep -oE "$PROSE" "$AP" | wc -l | tr -d ' ')

# PL-i 内容契约用例在场（判定"说明与现实两张皮"的那本）
PLI=$(grep -cE '^[[:space:]]*@Test' "$CT")

# PL-j 登录勾选闸门没被入口改动弄丢：canSubmit 仍然要求 agreed
PLJ=$(grep -c 'agreed' "$LV")

echo "PL-a 路由常量 privacy/terms        = $PLA/$PLA2 （目标 1/1）"
echo "PL-b 注册点 / 走 BusRouteBody      = $PLB/$PLB2 （目标 2/2）"
echo "PL-c 闸门集合里出现这两条 / 反证    = $PLC_IN/$PLC_PROOF （目标 0/1：正文必须在登录前后都打得开）"
echo "PL-d 登录页 PolicyLink             = $PLD （目标 3：定义 1 + 两个链接）"
echo "PL-e 关于页入口                    = $PLE （目标 2）"
echo "PL-f 免责句出处                    = $PLF （目标 1：只有 AppPolicies 写它）"
echo "PL-g 服务边界句出处                = $PLG （目标 1）"
echo "PL-h 页面里的正文句 / 反证         = $PLH/$PLH_PROOF （目标 0/恰好 43；换 locale 无关写法后由 25 重测为 43，见文件头 PROSE 注释）"
echo "PL-i 内容契约用例数                = $PLI （目标 ≥8）"
echo "PL-j 登录 ViewModel 的 agreed 引用  = $PLJ （目标 ≥3：状态 + 提交判定 + 用例可读）"

fail=0
[ "$PLA" = 1 ] && [ "$PLA2" = 1 ] || { echo "× PL-a 路由常量缺失或重复（$PLA/$PLA2）"; fail=1; }
[ "$PLB" = 2 ] || { echo "× PL-b 注册点不是两条（现 $PLB）：某一页打不开"; fail=1; }
[ "$PLB2" = 2 ] || { echo "× PL-b 有注册没走 BusRouteBody（$PLB2/2）：这两页会跟全工程口径不一致（键盘遮挡/边到边）"; fail=1; }
[ "$PLC_IN" = 0 ] || { echo "× PL-c 有人给协议页加了角色/登录闸门：用户在同意之前看不到正文"; fail=1; }
[ "$PLC_PROOF" = 1 ] || { echo "× PL-c 反证掉了（DRIVER_ONLY 定义数=$PLC_PROOF）：上面那个 0 可能是闸门本身没了"; fail=1; }
[ "$PLD" = 3 ] || { echo "× PL-d 登录页链接数不是 3（现 $PLD）：《服务协议》《隐私政策》要各自可点，勾与看必须分开"; fail=1; }
[ "$PLE" = 2 ] || { echo "× PL-e 关于页入口不是两条（现 $PLE）：登录后就找不到正文了"; fail=1; }
[ "$PLF" = 1 ] || { echo "× PL-f 免责句出现 $PLF 处：正文被抄成第二份，迟早和第一份对不上"; fail=1; }
[ "$PLG" = 1 ] || { echo "× PL-g 服务边界句出现 $PLG 处：同一条硬规则两份文字各自漂移"; fail=1; }
[ "$PLH" = 0 ] || { echo "× PL-h 页面里出现了 $PLH 句正文：渲染层开始自带文案（数据源那份就成了摆设）"; fail=1; }
[ "$PLH_PROOF" = 43 ] || { echo "× PL-h 反证掉了：数据源侧正文句数不是恰好 43（现 $PLH_PROOF）——少了=正文被删空、那个 0 不可信，多了=有人往数据源外另加了一份，都要同轮改这个数"; fail=1; }
[ "$PLI" -ge 8 ] || { echo "× PL-i 内容契约用例少于 8 条（现 $PLI）：说明与现实两张皮没人钉"; fail=1; }
[ "$PLJ" -ge 3 ] || { echo "× PL-j 登录页的 agreed 只剩 $PLJ 处引用：勾选闸门被顺手拆了"; fail=1; }

if [ "$fail" = 0 ]; then echo "政策正文页判据通过"; else echo "政策正文页判据失败"; fi
exit "$fail"
