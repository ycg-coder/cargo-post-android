#!/usr/bin/env bash
# 用户域 / 司机域分域探针（硬规则 1；CONTEXT_LOCK §八 ⑦；审计 §29/§64）
# 每轮收工必须实测并把数字抄进 AI_ITERATION_STATE.md 的常驻自检表 —— 没有输出就等于没跑。
# 用法：bash tools/check_domain_probes.sh（可用 BUSPOST_ANDROID 覆盖工程根）
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT/app/src/main/java" || exit 1

PKG=com/shanxiang/buspost
USER_DOMAIN="$PKG/ui/user $PKG/ui/login $PKG/ui/common $PKG/widget $PKG/qs $PKG/nav"
REPO="$PKG/data/repo/Repo.kt"
ALERT="$PKG/notify/AlertEngine.kt"

# 名字要写全，不能用 `Repo\.driver` 前缀草草了事：Repo.product( / Repo.productsPage( 是商城接口，
# 前缀式 pattern 会把用户域的正常调用报成违规，判据一旦误报就没人再看它。
DRV='Repo\.(driverProfile|driverShifts|driverPickups|driverTasks|driverEarnings|driverHandovers|driverLegs|'
DRV="$DRV"'driverPosition|driverMessages|driverUnreadCount|readDriverMessage|routeGuide|depart|arrive|pickupConfirm|'
DRV="$DRV"'productLoad|productDeliver|deliver|reportLocation|legAction|confirmHandover|currentLeg)'
# 字面量拼装 driverId 的写法（⑥ 之前有 19 处）：全部改走 driverIdParam() 后必须归零
PAT='mapOf("driverId" to driverId)'

pa=$(grep -rnE "$DRV|/driver/" $USER_DOMAIN | wc -l)
pa2=$(grep -rn "Running\.\|Prefs\.driver" $USER_DOMAIN | wc -l)
pb=$(grep -F -c "$PAT" "$REPO")
pb2=$(grep -c driverIdParam "$REPO")
pc=$(grep -rnE 'AppState\.setAccountRole\(|AppState\.setActiveDomain\(' $PKG --include=*.kt | grep -vE '(^|[[:space:]])(\*|//)' | wc -l)
pd=$(grep -rn DRIVER_ONLY $PKG | wc -l)
pe=$(grep -c 'route = Routes.DRIVER_MESSAGES' "$ALERT")
pf=$(grep -rn DRIVER_UNBOUND $PKG | wc -l)

echo "P-a  用户域司机读点         : $pa   （预期 0）"
echo "P-a2 用户域在途/司机档案读点 : $pa2   （预期 0）"
echo "P-b  driverId 字面量拼装     : $pb   （预期 0）"
echo "P-b2 driverIdParam 引用      : $pb2   （只 echo 不判成败。构成 = 1 KDoc + 1 声明 + 19 调用点；profile/shifts/pickups/earnings 这四个身份引导接口**按设计不带** driverId 参数，见 AI_ITERATION_STATE 常驻自检表 ⑥ 那一行：给它们加守卫就永远绑定不上。历史上 21→22→21 两次都是调用点增减，不是漂移）"
echo "P-c  角色/视图两层的写入点    : $pc   （预期 5，F-3 ③ 之后分两层：改角色 2 处 = MineScreen 显式授予 + WorkbenchScreen 档案降级；改视图 3 处 = BusPostRoot 登录落壳 + MineScreen 进司机端 + DriverMineScreen 出司机端「切换为用户版」（2026-10-10 起出口由 DriverShell 底部第 4 格移入「我的」页）。司机壳里 0 处改角色，见 tools/check_role_reconcile_probes.sh 的 RR-e2）"
echo "P-d  深链分域闸              : $pd   （预期 3 = Routes 声明 1 + ShellDecision 判据 1 + 其 KDoc 引用 1；V-2 把闸从 BusPostRoot 抽成纯函数后，判据在 nav/ShellDecision.kt，结构复核见 tools/check_shell_routing_probes.sh）"
echo "P-e  司机通知落司机页        : $pe   （预期 1）"
echo "P-f  未绑定文案引用点        : $pf   （只 echo 不判成败。构成 8 = Repo 的映射+异常类+常量 3 处 + ShellDecision 的 reconcileRole KDoc 1 处 + WorkbenchScreen 的 import+撤档错误态 2 处 + MineScreen 的 import+使用 2 处）"
echo "--- AlertEngine 通知路由（司机分支 return 之前那段里不得出现 trace/，行号会漂故只打印供人眼复核）---"
grep -n 'route = ' "$ALERT"
[ "$pa" = 0 ] && [ "$pa2" = 0 ] && [ "$pb" = 0 ] && [ "$pe" = 1 ] || { echo "分域判据失败"; exit 1; }
echo "分域判据通过"
