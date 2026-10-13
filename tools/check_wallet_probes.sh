#!/usr/bin/env bash
# #56「我的钱包 / 交易记录」的结构判据（配 tools/wallet_negative_controls.sh）
# 管七件事：入口恰好一个、这一版**只有读口**（写口的三个理由必须真在页面上说话）、
# 金额缺席不塌成 0、状态码不被当文案念、渠道那一格不替后端下"已扣款/是模拟的"的结论、
# `total` 不冒充全量条数、两个取数各自成格互不遮盖。
# 用法：bash tools/check_wallet_probes.sh        退出码非 0 = 这一格被拆了
# 写法沿用本仓规矩：按内容过滤不按行号；每个"目标 0"配同文件正对照；下限一律等于现读（D-084 ③、D-085 ㊾①）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
RT=$M/nav/Routes.kt
BR=$M/ui/BusPostRoot.kt
MS=$M/ui/user/MineScreen.kt
WR=$M/ui/user/WalletRows.kt
WS=$M/ui/user/WalletScreen.kt
MD=$M/data/model/Models.kt
RP=$M/data/repo/Repo.kt
CT=app/src/test/java/com/shanxiang/buspost/ui/user/WalletRowsContractTest.kt
NC='^[0-9]+:[[:space:]]*(\*|//|/\*)'

for f in "$RT" "$BR" "$MS" "$WR" "$WS" "$MD" "$RP" "$CT"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

nc_count() { grep -n "$2" "$1" | grep -cvE "$NC" || true; }
nc_all() { grep -rn "$1" "$M" --include=*.kt | sed 's/^[^:]*:[0-9]*://' | grep -cvE '^[[:space:]]*(\*|//|/\*)' || true; }

# W-a 路由 / 注册 / 入口各恰好一处
WA_CONST=$(grep -c 'const val WALLET = "wallet"' "$RT")
WA_REG=$(grep -c 'composable(Routes.WALLET)' "$BR")
WA_BODY=$(grep -c 'composable(Routes.WALLET).*BusRouteBody' "$BR")
WA_ENTRY=$(nc_count "$MS" 'nav.navigate(Routes.WALLET)')
WA_ENTRY_ALL=$(nc_all 'nav.navigate(Routes.WALLET)')

# W-b 只读：三个写口在**生效代码**里必须 0 命中，而两个读口各恰好 1
WB_RECHARGE=$(nc_all 'wallet/recharge')
WB_PAY=$(nc_all 'wallet/pay')
WB_ORDER=$(nc_all 'wallet/create-pay-order')
WB_BAL=$(nc_count "$RP" 'Api.get("$T/wallet/balance")')
WB_TXS=$(nc_count "$RP" 'Api.get("$T/wallet/transactions"')

# W-c "这一版没有写口"那句话只有一个出处（纯层常量），页面只引用
WC_NOTE=$(nc_count "$WR" 'NO_WRITE_ENTRY_NOTE')
WC_PAGE=$(nc_count "$WS" 'NO_WRITE_ENTRY_NOTE')
WC_PAGE_LITERAL=$(nc_count "$WS" '这一版没有充值与余额支付入口')

# W-d 数字码不许被当文案念：两条"未识别"分支必须在，且 status 不许用 multiStr 读
WD_UNK_STATUS=$(nc_count "$WR" '未识别的流水状态（')
WD_UNK_TYPE=$(nc_count "$WR" '未识别的流水类型（')
WD_BAD_STATUS=$(grep -c 'multiStr("status")' "$MD")

# W-e 金额缺席不塌成 0
WE_NULL=$(nc_count "$WR" '?: NOT_GIVEN')
WE_NULL_BAD=$(nc_count "$WR" '?: "¥0.00"')
WE_MULTI=$(grep -c 'multiNumOrNull("balance")\|multiNumOrNull("frozenAmount")\|multiNumOrNull("totalRecharge")\|multiNumOrNull("totalSpent")' "$MD")
WE_MULTI_BAD=$(grep -c 'multiNum("balance")\|multiNum("frozenAmount")\|multiNum("totalRecharge")\|multiNum("totalSpent")' "$MD")

# W-f 渠道那一格只照抄后端原词（不许出现"已扣款/已通过"这类推断），同段正对照证明不是没扫到
WF_LABEL=$(nc_count "$WR" '渠道（后端原词）')
WF_LIE=$(nc_count "$WR" '已扣款')
WF_LIE_ALL=$(nc_all '已通过支付宝')
WF_PROOF=$(nc_count "$WR" NOT_GIVEN)

# W-g total 不冒充全量：必须说"本次返回…未查全量"，且纯层不许出现"共 N 条"那种整单念法
WG_SCOPE=$(nc_count "$WR" '本次返回 $count 条')
WG_SCOPE2=$(nc_count "$WR" '未查全量')
WG_BAD=$(nc_count "$WR" '共 $')

# W-h 两个取数各自成格、互不遮盖
WH_BAL_ERR=$(nc_count "$WR" 'berr != null ->')
WH_TXS_ERR=$(nc_count "$WR" 'terr != null ->')
WH_BLOCK=$(nc_count "$WS" 'WalletSectionBlock(page.')

# W-i 初值必须是"还没结论"
WH_INIT=$(nc_count "$WS" 'RemoteState<WalletBalance>(loading = true)')
WH_INIT2=$(nc_count "$WS" 'RemoteState<WalletTxPage>(loading = true)')
WH_INIT_BAD=$(nc_count "$WS" 'success = true))')

# W-j 契约用例在场（下限 = 现读，等于 16；D-085 ㊾①：写 ≥ 而实测更高 = 删用例不红）
WJ=$(grep -cE '^[[:space:]]*@Test' "$CT")

echo "W-a 常量 / 注册 / BusRouteBody / 入口（本页·全工程） = $WA_CONST/$WA_REG/$WA_BODY/$WA_ENTRY/$WA_ENTRY_ALL （目标 1/1/1/1/2）"
echo "W-b 三个写口 / 两个读口                = $WB_RECHARGE+$WB_PAY+$WB_ORDER / $WB_BAL+$WB_TXS （目标 0+0+0 / 1+1）"
echo "W-c 那句话：纯层声明 / 页面引用 / 页面重抄 = $WC_NOTE/$WC_PAGE/$WC_PAGE_LITERAL （目标 1/1/0）"
echo "W-d 未识别状态 / 未识别类型 / multiStr 读码 = $WD_UNK_STATUS/$WD_UNK_TYPE/$WD_BAD_STATUS （目标 1/1/0）"
echo "W-e 没给分支 / 塌成零元 / 四个可空读法(坏形) = $WE_NULL/$WE_NULL_BAD/$WE_MULTI+$WE_MULTI_BAD （目标 ≥1/0/4+0）"
echo "W-f 渠道那一格 / 越界推断 / 同段正对照    = $WF_LABEL/$WF_LIE+$WF_LIE_ALL/$WF_PROOF （目标 1/0+0/恰好 6）"
echo "W-g 本次返回措辞 / 未查全量 / 整单念法     = $WG_SCOPE/$WG_SCOPE2/$WG_BAD （目标 1/1/0：后端 total 就是 items.size()，见 AppWalletController:101）"
echo "W-h 余额失败格 / 流水失败格 / 页面两格     = $WH_BAL_ERR/$WH_TXS_ERR/$WH_BLOCK （目标 1/1/2）"
echo "W-i 两个初值 loading / 初值写成成功        = $WH_INIT+$WH_INIT2/$WH_INIT_BAD （目标 1+1/0）"
echo "W-j 契约用例数                            = $WJ （目标 ≥16 = 现读）"

fail=0
[ "$WA_CONST" = 1 ] || { echo "× W-a 路由常量缺失或重复（$WA_CONST）"; fail=1; }
[ "$WA_REG" = 1 ] || { echo "× W-a 注册点不是恰好一处（现 $WA_REG）：这一页打不开或与全工程口径不一致"; fail=1; }
[ "$WA_BODY" = 1 ] || { echo "× W-a 有注册但没走 BusRouteBody（$WA_BODY/1）：这一页的键盘遮挡与边到端口径会和全工程不一致"; fail=1; }
[ "$WA_ENTRY" = 1 ] || { echo "× W-a 「我的」里那个入口不是恰好一条（现 $WA_ENTRY）"; fail=1; }
[ "$WA_ENTRY_ALL" = 2 ] || { echo "× W-a 全工程入口数=$WA_ENTRY_ALL（目标 2：「我的」列表 1 + 首页宫格 1，2026-10-13 合并 origin/main 时对方补了首页那一格）：这个 app 的既有口径就是同一页多面入口（NOTIFICATIONS 3 处 / PROFILE 2 / SETTINGS 3），多了第三处或首页那格没了都要重新点名"; fail=1; }
{ [ "$WB_RECHARGE" = 0 ] && [ "$WB_PAY" = 0 ] && [ "$WB_ORDER" = 0 ]; } || { echo "× W-b 写口被接进了生效代码（recharge=$WB_RECHARGE pay=$WB_PAY create-pay-order=$WB_ORDER）：那三个口今天不能接（无幂等键 / 模拟与真到账不可区分 / 查单不校验归属人），接了就是一次真扣款或重复加余额的风险"; fail=1; }
{ [ "$WB_BAL" = 1 ] && [ "$WB_TXS" = 1 ]; } || { echo "× W-b 两个读口不齐（$WB_BAL/$WB_TXS，目标 1/1）：这一页又变回死代码"; fail=1; }
[ "$WC_NOTE" = 1 ] || { echo "× W-c 那句话在纯层不再是唯一声明（现 $WC_NOTE）"; fail=1; }
[ "$WC_PAGE" = 1 ] || { echo "× W-c 页面不再引用那个常量（现 $WC_PAGE）：这句话要么没了，要么页面自己写了一份"; fail=1; }
[ "$WC_PAGE_LITERAL" = 0 ] || { echo "× W-c 页面重抄了那句话的字面（现 $WC_PAGE_LITERAL）：两处文字迟早对不上"; fail=1; }
[ "$WD_UNK_STATUS" = 1 ] || { echo "× W-d 未识别状态那一格没了（现 $WD_UNK_STATUS）：没见过的码会被就近并进成功/失败"; fail=1; }
[ "$WD_UNK_TYPE" = 1 ] || { echo "× W-d 未识别类型那一格没了（现 $WD_UNK_TYPE）"; fail=1; }
[ "$WD_BAD_STATUS" = 0 ] || { echo "× W-d 又用 multiStr 读状态码（$WD_BAD_STATUS 处）：屏幕上会把 1 念成状态"; fail=1; }
[ "$WE_NULL" -ge 1 ] || { echo "× W-e 金额缺席不再念「后端没给」（现 $WE_NULL）：没给会显示成 ¥0.00"; fail=1; }
[ "$WE_NULL_BAD" = 0 ] || { echo "× W-e 缺席被直接塌成 ¥0.00（现 $WE_NULL_BAD）"; fail=1; }
[ "$WE_MULTI" = 4 ] || { echo "× W-e 四个余额键的可空读法不齐（现 $WE_MULTI，目标 4）"; fail=1; }
[ "$WE_MULTI_BAD" = 0 ] || { echo "× W-e 余额键又出现非空 multiNum 读法（现 $WE_MULTI_BAD）"; fail=1; }
[ "$WF_LABEL" = 1 ] || { echo "× W-f 渠道那一格的标签变了（现 $WF_LABEL）：『照抄后端原词』这件事不再写在界面上"; fail=1; }
{ [ "$WF_LIE" = 0 ] && [ "$WF_LIE_ALL" = 0 ]; } || { echo "× W-f 页面开始替后端宣布扣款（本机 $WF_LIE / 全工程 $WF_LIE_ALL）：库里没有'是否模拟'那一列，这句话本机说不出"; fail=1; }
[ "$WF_PROOF" = 6 ] || { echo "× W-f 同文件正对照掉了（同文件 NOT_GIVEN 用点=$WF_PROOF，目标恰好 6）：上面那两个 0 可能是没扫到这个文件"; fail=1; }
[ "$WG_SCOPE" = 1 ] || { echo "× W-g 「本次返回 N 条」那句没了（现 $WG_SCOPE）：后端 total 就是 items.size()，念成整单条数是假话"; fail=1; }
[ "$WG_SCOPE2" = 1 ] || { echo "× W-g 「未查全量」那半句没了（现 $WG_SCOPE2）"; fail=1; }
[ "$WG_BAD" = 0 ] || { echo "× W-g 纯层出现整单条数念法（现 $WG_BAD）"; fail=1; }
{ [ "$WH_BAL_ERR" = 1 ] && [ "$WH_TXS_ERR" = 1 ]; } || { echo "× W-h 两个取数的失败格不再各自判（$WH_BAL_ERR/$WH_TXS_ERR）：一边失败会遮住另一边"; fail=1; }
[ "$WH_BLOCK" = 2 ] || { echo "× W-h 页面渲染的分区数不是 2（现 $WH_BLOCK）：某一格又不显示"; fail=1; }
{ [ "$WH_INIT" = 1 ] && [ "$WH_INIT2" = 1 ]; } || { echo "× W-i 两个初值不都是「还没结论」（$WH_INIT/$WH_INIT2）：开局就替后端宣布数字"; fail=1; }
[ "$WH_INIT_BAD" = 0 ] || { echo "× W-i 有初值被写成 success=true（现 $WH_INIT_BAD）"; fail=1; }
[ "$WJ" -ge 16 ] || { echo "× W-j 契约用例少于 16 条（现 $WJ）：缺席/码/渠道/条数这四类口径没人钉"; fail=1; }

if [ "$fail" = 0 ]; then echo "钱包页判据通过"; else echo "钱包页判据失败"; fi
exit "$fail"
