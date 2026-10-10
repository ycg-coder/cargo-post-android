#!/usr/bin/env bash
# V-2b 角色与后端画像同源判据（审计 §70 的尾巴；指令 v2.0 Phase 1 硬规则 1）
# 用法：bash tools/check_role_reconcile_probes.sh     退出码非 0 = 同源判据破了
#
# 分工：行为由三份用例钉 ——
#   app/src/test/.../nav/RoleReconcileTest.kt（三态与降级的纯函数判据）
#   app/src/test/.../ui/driver/WorkbenchRoleReconcileContractTest.kt（请求条数与角色，MockWebServer 计数）
#   app/src/test/.../core/DriverRoleIntegrityTest.kt（F-3 ③：账号角色与"在看哪个域"两层的分离规则）
# 这里钉**结构**：工程没有 Robolectric，"早退真的在四条请求之前""出口既导航又停服务"
# 这类只能按行号顺序与整行字面量复核。写法沿用既有教训：注释行排除、按内容不按行号取位置、
# 每条"恰好 N"都钉整行字面量，负控制见 tools/role_reconcile_negative_controls.sh（基线不绿则整份不采信）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

SRC=app/src/main/java/com/shanxiang/buspost
SDF="$SRC/nav/ShellDecision.kt"
REPO="$SRC/data/repo/Repo.kt"
WB="$SRC/ui/driver/WorkbenchScreen.kt"
DS="$SRC/ui/driver/DriverMineScreen.kt"
AE="$SRC/notify/AlertEngine.kt"
T1=app/src/test/java/com/shanxiang/buspost/nav/RoleReconcileTest.kt
T2=app/src/test/java/com/shanxiang/buspost/ui/driver/WorkbenchRoleReconcileContractTest.kt
T3=app/src/test/java/com/shanxiang/buspost/core/DriverRoleIntegrityTest.kt
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'

# 先验文件存在：grep 找不到文件时输出是空的，空串参与数值比较会报"不是整数"，
# 看起来像判据跑过了，其实是根本没跑（这条救过 shell_routing 的基线）。
for f in "$SDF" "$REPO" "$WB" "$DS" "$AE" "$T1" "$T2" "$T3"; do
    [ -f "$f" ] || { echo "× 判据文件缺失：$f（先确认工程根/副本建对，别把缺文件读成计数 0）"; exit 2; }
done

# RR-a 用例条数：纯函数 5 条 + 行为 4 条 + 两层分离 6 条。删用例必须让这里变红，所以是"恰好"不是"至少"以外的下限。
RA=$(grep -cE '^[[:space:]]*@Test' "$T1")
RA2=$(grep -cE '^[[:space:]]*@Test' "$T2")
RA3=$(grep -cE '^[[:space:]]*@Test' "$T3")

# RR-b 地基：后端的否定形状是 `{"code":0,"data":null}` → `Api.kt:314-315` 读成**空 JSONObject**，
# 所以判据里再要 `data 非空`（旧写法 `o.length() > 0`）就等于"撤档永不生效"。取两个函数体来看。
body() { awk -v pat="$1" 'index($0, pat) {on=1} on {print} on && /^[[:space:]]*\}[[:space:]]*$/ {exit}' "$REPO"; }
DP=$(body 'suspend fun driverProfile()')
BD=$(body 'suspend fun bindDriver()')
RB=$(printf '%s\n' "$DP" | grep -c 'o\.length() > 0')
RB2=$(printf '%s\n' "$BD" | grep -c 'o\.length() > 0')
RB3=$(printf '%s\n' "$DP" | grep -cE '^[[:space:]]*else if \(!p\.demo\) Prefs\.clearDriverBinding\(\)$')

# RR-c 早退顺序：`if (server == false)` 必须**先于** `Repo.driverShifts()`，否则守卫形同不存在。
DEN=$(grep -n 'if (server == false) {' "$WB" | head -1 | cut -d: -f1)
SFT=$(grep -n 'val s = Repo.driverShifts()' "$WB" | head -1 | cut -d: -f1)
RC=$(grep -n 'serverSaysDriver(' "$WB" | grep -vE "$NC" | wc -l | tr -d ' ')
RC2=$(grep -n 'reconcileRole(' "$WB" | grep -vE "$NC" | wc -l | tr -d ' ')
RET=$(awk -v s="${DEN:-0}" 'NR>s && /^[[:space:]]*return$/ {print NR; exit}' "$WB")

# RR-d 出口完整：降级必须"改角色 + 出司机壳 + 停位置上报"三件一起做，缺任何一件都是半条链路。
# 这里一律用**整行字面量**而不是子串：负控 M 抓到过"把整行注释掉，grep -c 照样数到 1"，
# 子串匹配对注释行没有免疫力，那条判据当时等于没判据。
RD=$(grep -c '^    LaunchedEffect(vm.demoted) {$' "$WB")
RD2=$(grep -c '^    var demoted by mutableStateOf(false)$' "$WB")
LS=$(grep -n '^    LaunchedEffect(vm.demoted) {$' "$WB" | head -1 | cut -d: -f1)
STOPIN=$(awk -v s="${LS:-0}" 'NR>s && NR<=s+6 && /DriverLocationService\.stop\(context\)/' "$WB" | wc -l | tr -d ' ')
NAVIN=$(awk -v s="${LS:-0}" 'NR>s && NR<=s+6 && /nav\.navigate\(Routes\.USER_SHELL\)/' "$WB" | wc -l | tr -d ' ')
RD4=$(grep -c 'nav.navigate(Routes.USER_SHELL) { popUpTo(Routes.DRIVER_SHELL) { inclusive = true } }' "$WB" "$DS" | awk -F: '{s+=$2} END {print s}')

# RR-e 角色只有一个来源：改角色的入口仍然只有两处（显式授予 / 档案降级），页面不得自己写 Prefs.role。
RE=$(grep -rn 'AppState\.setAccountRole(' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
# 写角色的字面量只允许两处：AppState.setAccountRole（连同视图一起改）、Repo.saveSession 的登录落盘。
# 这里必须匹配 `Prefs.role = <小写标识符>` 而不是前缀 `Prefs.role =`：
# 后者会撞上 `Prefs.role == Role.DRIVER` 这类**读**，把读成写就是判据自己失真
# （当年实测数出 4 而不是 2 就是这个原因；F-3 ③ 之后 AlertEngine 已经不再按角色读，这条写法纪律仍然留着）。
RE2=$(grep -rn 'Prefs\.role = [a-z]' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
RE3=$(grep -rn 'AppState\.setAccountRole(' "$SRC/ui/user/MineScreen.kt" "$SRC/ui/driver/WorkbenchScreen.kt" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
# RR-e2 F-3 ③ 的核心断言：**带 tab 的司机壳里一处角色写入都不许有**，切壳只准写 activeDomain。
# 三种拼写并进同一条"数 0"里是安全的：任意一种回来都立刻非 0，不存在两条破口互相顶掉的空跑
# （那种坑是"数恰好 N"时用并集，一条判据管两个键名）。
RE4=$(grep -rnE 'AppState\.setRole\(|AppState\.setAccountRole\(|Prefs\.role =' "$DS" | grep -vE "$NC" | wc -l | tr -d ' ')

# RR-f 后台提醒按"在看哪个域"分域，不再按账号角色（F-3 ③：停掉司机域轮询的前提换成了切视图）
RF=$(grep -c 'AppState\.activeDomain\.value == AppDomain\.DRIVER' "$AE")
RF2=$(grep -rn 'Prefs\.role == Role\.DRIVER' "$AE" | grep -vE "$NC" | wc -l | tr -d ' ')

echo "RR-a 用例条数（纯函数/行为/两层分离）  = $RA/$RA2/$RA3 （目标 5/4/6）"
echo "RR-b 档案两函数里旧判据残留         = $RB/$RB2 （目标 0/0）"
echo "RR-b3 新判据整行（!p.demo 才清绑定）  = $RB3 （目标 1）"
echo "RR-c 早退行号/请求行号              = ${DEN:-无}/${SFT:-无}（目标 DEN < SFT）"
echo "RR-c 纯函数调用点（判态/判角色）     = $RC/$RC2 （目标 1/1）"
echo "RR-c 早退分支里的 return 行号        = ${RET:-无}（目标 DEN+1 ≤ RET ≤ DEN+22）"
echo "RR-d demoted 出口（观测/声明/回用户壳）= $RD/$RD2/$RD4 （目标 1/1/2：司机壳出口 + 降级出口）"
echo "RR-d 降级块内 停上报/导航            = $STOPIN/$NAVIN （目标 1/1：三件事必须在同一个块里）"
echo "RR-e setAccountRole 调用点/角色写入点/两处白名单 = $RE/$RE2/$RE3 （目标 2/2/2）"
echo "RR-e2 司机壳里的角色写入 / 切视图那一行        = $RE4 （目标 0：切壳只写 activeDomain）"
echo "RR-f 提醒按域轮询的两处 / 残留的按角色轮询      = $RF/$RF2 （目标 2/0）"

fail=0
[ "$RA" = 5 ] || { echo "× RR-a 三态/降级的纯函数用例不是 5 条"; fail=1; }
[ "$RA2" = 4 ] || { echo "× RR-a 工作台行为用例不是 4 条（请求条数判据被删掉了）"; fail=1; }
[ "$RA3" = 6 ] || { echo "× RR-a 两层分离（账号角色 / 在看域）的完整性用例不是 6 条"; fail=1; }
[ "$RB" = 0 ] || { echo "× RR-b driverProfile 又要求 data 非空：后端唯一否定形状被排除，撤档不生效"; fail=1; }
[ "$RB2" = 0 ] || { echo "× RR-b bindDriver 的否定判据回退了：同一端点两套判据"; fail=1; }
[ "$RB3" = 1 ] || { echo "× RR-b3 '非演示即明确否定'那行不在了"; fail=1; }
[ -n "$DEN" ] && [ -n "$SFT" ] && [ "$DEN" -lt "$SFT" ] || { echo "× RR-c 早退不在四条司机域请求之前，撤档后照样拉司机数据"; fail=1; }
[ "$RC" = 1 ] || { echo "× RR-c serverSaysDriver 调用点不是恰好 1 处"; fail=1; }
[ "$RC2" = 1 ] || { echo "× RR-c reconcileRole 调用点不是恰好 1 处"; fail=1; }
[ -n "$RET" ] && [ "$RET" -gt "$DEN" ] && [ "$RET" -le "$((DEN + 22))" ] || { echo "× RR-c 否定分支里没有紧跟的 return（早退变成只清状态不停手）"; fail=1; }
[ "$RD" = 1 ] || { echo "× RR-d 页面不再观测 demoted：角色降了但人还留在司机壳"; fail=1; }
[ "$RD2" = 1 ] || { echo "× RR-d demoted 不再是 VM 私有写：页面可以自己置位"; fail=1; }
[ "$RD4" = 2 ] || { echo "× RR-d 回用户壳的导航语句变了（司机壳出口 + 降级出口应当是同一条字面量）"; fail=1; }
[ -n "$STOPIN" ] && [ "$STOPIN" = 1 ] || { echo "× RR-d 降级时没停位置上报：档案没了还在替旧班次报点位"; fail=1; }
[ "$NAVIN" = 1 ] || { echo "× RR-d 降级块里没有回用户壳的导航：角色降了但人还留在司机壳"; fail=1; }
[ "$RE" = 2 ] || { echo "× RR-e 改角色的入口不再是 2 处（显式授予 / 档案降级），角色有了第二套答案"; fail=1; }
[ "$RE2" = 2 ] || { echo "× RR-e 出现直接写 Prefs.role 的页面：Prefs 与 AppState 会打架"; fail=1; }
[ "$RE3" = 2 ] || { echo "× RR-e 两处白名单文件里少了(setAccountRole 被挪去别处)"; fail=1; }
[ "$RE4" = 0 ] || { echo "× RR-e2 司机壳又开始写账号角色：F-3 ③ 拆开的两层重新糊成一件事"; fail=1; }
[ "$RF" = 2 ] || { echo "× RR-f 后台提醒的司机域两处读不再按域判断（撤出司机壳后还在轮询 /driver/messages）"; fail=1; }
[ "$RF2" = 0 ] || { echo "× RR-f AlertEngine 回到按账号角色分域：切壳不切角色就停不下司机域轮询"; fail=1; }

if [ "$fail" = 0 ]; then echo "角色同源判据通过"; else echo "角色同源判据失败"; fi
exit "$fail"
