#!/usr/bin/env bash
# §54 司机异常上报判据（指令 v2.0 §54；后端契约 AppDriverLegActionReqVO / DriverAppServiceImpl#legAction；DECISION_LOG D-029）
# 用法：bash tools/check_exception_probes.sh      退出码非 0 = §54 的契约破了
#
# 分工：报文与回执由 app/src/test/.../ui/driver/DriverException{Report,Contract}Test.kt 钉（行为）；
# 这里钉**结构** —— 工程没有 Robolectric，页面渲染不出来，只能按内容复核"那条唯一出口还在不在、
# 有没有人偷偷自造 type 键、失败分支的措辞有没有被换成一句谎话"。
# 写法沿用既有教训：先验文件存在；按内容过滤不按行号；注释行排除（关键字型判据会把"说明为什么改"的注释
# 当成破口）；计数用 -ge 下限，加用例与抬下限同一轮。每条都有负控制：bash tools/exception_negative_controls.sh
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

SRC=app/src/main/java/com/shanxiang/buspost
RP="$SRC/ui/driver/DriverExceptionReport.kt"
SC="$SRC/ui/driver/DriverExceptionScreen.kt"
RS="$SRC/ui/driver/RoutesScreen.kt"
RT="$SRC/ui/BusPostRoot.kt"
RQ="$SRC/nav/Routes.kt"
AS="$SRC/core/AppState.kt"
TR=app/src/test/java/com/shanxiang/buspost/ui/driver/DriverExceptionReportTest.kt
TC=app/src/test/java/com/shanxiang/buspost/ui/driver/DriverExceptionContractTest.kt
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'

# 先验文件存在：grep 找不到文件时输出为空，`[ "" = 0 ]` 会把"根本没跑"读成"真的 0 命中"
for f in "$RP" "$SC" "$RS" "$RT" "$RQ" "$AS" "$TR" "$TC"; do
    [ -f "$f" ] || { echo "× 判据文件缺失：$f（先确认工程根/副本建对，别把缺文件读成计数 0）"; exit 2; }
done

# EX-a 用例条数（行为判据的底盘，删用例必须让这里变红）
EA=$(grep -cE '^[[:space:]]*@Test' "$TR")
EA2=$(grep -cE '^[[:space:]]*@Test' "$TC")

# EX-b 单一出口：页面只走 Repo.legAction，绝不自己 Api.post（自己发就绕过 V-1 幂等闸与 401 收口）
EB=$(grep -n 'Repo\.legAction(' "$SC" | grep -vE "$NC" | wc -l | tr -d ' ')
EB2=$(grep -rn 'Api\.post' "$SRC/ui/driver" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')

# EX-c 报文只有后端认识的键：自造 type / exceptionType / reasonType 一律 0；契约测试钉住三键集合
EC=$(grep -rnE '"(type|exceptionType|reasonType|exception_type)"' "$SRC/ui/driver" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
EC2=$(grep -c 'setOf("driverId", "legId", "remark")' "$TC")

# EX-d §54 的七个分类：条目数与标签字面量都要在（少一项是漏需求，多一项是发明后端不认识的话术）
ED=$(grep -cE '^[[:space:]]*[A-Z_]+\("[^"]+"\)(,|;)$' "$RP")
ED_MISS=0
for label in 车辆故障 道路阻断 收件人不在 货物异常 无法交接 天气影响 其他; do
    grep -q "\"$label\"" "$RP" || ED_MISS=$((ED_MISS + 1))
done

# EX-e remark 上限与截断路径（真实落点是两个 varchar(500)，超限=1406=整笔回滚）
# 锚点必须到行尾：`grep '… = 200'` 是子串匹配，改成 2000 一样命中，负控第一次跑就在这条上打了 ✔ 假绿
EE=$(grep -cE 'internal const val EXCEPTION_REMARK_MAX = 200$' "$RP")
EE2=$(grep -c 'substring(0, EXCEPTION_REMARK_MAX - 1)' "$RP")

# EX-f 路由四处齐全：常量、进分域闸集合、导航图注册、页面入口（缺任一处就是"写了但进不去"或"进去了没闸"）
EF=$(grep -c 'const val DRIVER_EXCEPTION = "driverException"' "$RQ")
EF2=$(grep -n 'composable(Routes.DRIVER_EXCEPTION)' "$RT" | grep -vE "$NC" | wc -l | tr -d ' ')
EF3=$(grep -n 'AppState\.openException(' "$RS" | grep -vE "$NC" | wc -l | tr -d ' ')
EF4=$(grep -n 'fun openException(' "$AS" | grep -vE "$NC" | wc -l | tr -d ' ')

# EX-g 诚实回执：三条"未发给后端"（没选段/没选分类/闸门拦下）+ 回读不一致那句，且不许出现"上报成功"
# 按内容过滤注释行（⑬ 的教训：注释里写"回执必须说未发给后端"是因果链，不能让它顶替生效代码的那一句）
EG=$(grep -n '未发给后端' "$SC" | grep -vE "$NC" | wc -l | tr -d ' ')
EG2=$(grep -n '可能未被接受' "$SC" | grep -vE "$NC" | wc -l | tr -d ' ')
EG3=$(grep -n '上报成功' "$SC" | grep -vE "$NC" | wc -l | tr -d ' ')
# EG4/EG5 被本地幂等闸拦下那一格（ack.notSent）：**按分支钉，不按措辞钉**。
#   D-082 的 EX-g 第四条破口当时不红，根因不是注入没落（它落了），是 10-12 话术收紧把那句里的
#   「本次点击未发给后端」改短成了「以下为当前状态」⇒ EG 这条"未发给后端"的腿从此看不见这一格，
#   把拦下说成已提交时 EG/EG3 一根手指都不动。措辞还会再改，"拦下了却宣布已提交"这件事不许回来，
#   所以这里钉的是：这一格必须在（=1），且它自己那句里不许出现"已提交/成功"（=0）。
EG4=$(grep -n 'ack\.notSent ->' "$SC" | grep -vE "$NC" | wc -l | tr -d ' ')
EG5=$(grep -n 'ack\.notSent ->' "$SC" | grep -vE "$NC" | grep -cE '已提交|成功')

# EX-h 状态归后端：页面不许本机 copy(status=…) 自封已异常（审计 §26/§27 的老毛病，新页面第一天就钉）
EH=$(grep -n '\.copy(' "$SC" | grep -vE "$NC" | wc -l | tr -d ' ')

# EX-i 终态段不接受上报：页面与入口都过 DriverLegStateMapper 的闸
# 计数钉的是**生效代码的确切字面量**，不是关键字出现次数。教训：`canReportException` 在页面里出现 10 次
# （选中态、配色、文案都在用），负控删掉两处守卫后计数仍然 ≥2，判据永远不会红——比没有判据更危险。
EI=$(grep -n 'if (!DriverLegStateMapper.of(leg).canReportException) {' "$SC" | grep -vE "$NC" | wc -l | tr -d ' ')
EI2=$(grep -n 'val canSend = leg != null && DriverLegStateMapper.of(leg).canReportException && vm.reason != null' "$SC" | grep -vE "$NC" | wc -l | tr -d ' ')
EI3=$(grep -n 'if (flow.canReportException) {' "$RS" | grep -vE "$NC" | wc -l | tr -d ' ')
EI4=$(grep -n 'clickable(enabled = flow.canReportException' "$SC" | grep -vE "$NC" | wc -l | tr -d ' ')

# EX-j 跨单段序重号的消歧（缺陷 #43）：`legSequence` 是**每张单内**的序号，两张单的段在列表里
#   会同名"第 1 段"，而异常上报是对一段的状态写入 —— 报错对象撤不回来。
#   钉三件事：选段行与回执各走那两个带订单标识的函数（=1/1）；页面里不许再留手写的
#   `第 ${…} 段` 直出串（=0，那正是缺陷写法）；缺单号那句兜底不许被删（=1，
#   删了就会悄悄退回"只念第 N 段"——缺陷换一副面孔回来）。
EJ=$(grep -n 'legOptionTitle(leg' "$SC" | grep -vE "$NC" | wc -l | tr -d ' ')
EJ2=$(grep -n 'legReceiptText(back)' "$SC" | grep -vE "$NC" | wc -l | tr -d ' ')
EJ3=$(grep -n '第 ${.*} 段' "$SC" | grep -vE "$NC" | wc -l | tr -d ' ')
EJ4=$(grep -cF '?: "单号未给 · "' "$RP")
# EX-k 现场照片的键纪律（队列 #60 / 台账 D-135）：后端那一格是「没带就不写」。
#   ① 空串不写键这条判断必须在（=1）：删掉它，事件里就会出现一条指向空地址的照片，
#      事后查的人会说「司机传了张空照片」，而真相是他没传 / 没传上去；
#   ② 页面里不许内联拼 "photoUrl" 键（=0）：同一份键两处各拼一份就是两条可以各自漂移的契约，
#      与后台 api 层今天刚清掉的「同端点两个包装」同病。
EK=$(grep -cF 'if (photoUrl.isEmpty()) mapOf("remark" to remark)' "$RP")
EK2=$(grep -n '"photoUrl" to' "$SC" | grep -vE "$NC" | wc -l | tr -d ' ')

# EX-m 选片**取消不许清掉已选**（真机抓的，队列 #60 / 台账 D-138）：
#   在"已选 1 张，点击重选"那一格再点一次 → 系统选片器起来 → 按返回 → 回调收到的是 null。
#   第一版写的是 `picked = it`，于是刚拍的那张被静默抹掉，页面回到"没附照片"那一形，
#   而司机以为自己还带着照片 —— 提交出去就是又一条"凭证没带上"，与我们刚修完的 #60 同一格。
#   正确形状是 `picked = it ?: picked`（这一格里 `it` 与 `picked` 都是 Uri?，判据按这一处的实际形状认）。
# EX-n 照片回执要带原因（真机抓的：段已转异常、提交机会用掉了，只剩这句话能说明为什么没带上）
EN=$(grep -cF 'fun photoDropText(why: String): String' "$RP")
EN2=$(grep -cF 'photoDropped -> photoDropText(uploadWhy)' "$SC")
EN3=$(grep -cF 'exceptionOrNull()' "$SC")
EM=$(grep -cF 'picked = it ?: picked' "$SC")
EM2=$(grep -n 'PickVisualMedia())' "$SC" | grep -vE "$NC" | grep -cF '{ picked = it }')

echo "EX-a 用例条数（纯函数/契约）        = $EA/$EA2 （目标 ≥14/≥6）"
echo "EX-b legAction 出口/页面自发热请求   = $EB/$EB2 （目标 1/0）"
echo "EX-c 自造 type 键/三键集合契约        = $EC/$EC2 （目标 0/1）"
echo "EX-d 分类条目数/缺失标签数            = $ED/$ED_MISS （目标 7/0）"
echo "EX-e 上限常量/截断路径                = $EE/$EE2 （目标 1/1）"
echo "EX-f 路由常量/注册/入口/意图          = $EF/$EF2/$EF3/$EF4 （目标 1/1/1/1）"
echo "EX-g 未发给后端/回读不一致/谎报成功   = $EG/$EG2/$EG3 （目标 ≥3/1/0）"
echo "EX-g 幂等闸拦下那一格：分支在/句里宣布已提交 = $EG4/$EG5 （目标 1/0）"
echo "EX-h 本机自改状态的 copy()            = $EH （目标 0）"
echo "EX-i 终态守卫字面量（出网前/按钮/入口/选段） = $EI/$EI2/$EI3/$EI4 （目标 1/1/1/1）"
echo "EX-j 消歧调用（选段行/回执）·页面残留直出串·缺单号兜底 = $EJ/$EJ2/$EJ3/$EJ4 （目标 1/1/0/1）"
echo "EX-k 照片键纪律（空串不写键 / 页面不内联 photoUrl） = $EK/$EK2 （目标 1/0）"
echo "EX-m 选片取消保留已选（保留写法 / 裸赋值残留）      = $EM/$EM2 （目标 1/0）"
echo "EX-n 照片回执带原因（纯函数/上岗/原因取自 Result）  = $EN/$EN2/$EN3 （目标 1/1/1）"

fail=0
# ⚠ 下限 10 → 12（2026-10-16，D-135）：#60 给照片键纪律补了两条具名用例，文件实读 12 条 @Test。
#   不抬的话"注释掉一条用例"这种破口注进去仍然绿 —— 本轮就是负控自己报出来才看见的
#   （EX-a 第一条：✘ 负控制失效（exit 0））。下限只按实跑的条数抬，不按估、不按"大概十几条"。
[ "$EA" -ge 14 ] || { echo "× EX-a 纯函数用例少于 14 条（含 #43 三条消歧 + #60 两条照片纪律 + 两条照片回执带原因）"; fail=1; }
[ "$EA2" -ge 6 ] || { echo "× EX-a 契约用例少于 6 条（报文与回执没人钉）"; fail=1; }
[ "$EB" = 1 ]    || { echo "× EX-b 异常上报不再走 Repo.legAction 单一出口（幂等闸与 401 收口都会被绕过）"; fail=1; }
[ "$EB2" = 0 ]   || { echo "× EX-b 司机页面开始自己 Api.post：绕过闸门、绕过会话失效处理"; fail=1; }
[ "$EC" = 0 ]    || { echo "× EX-c 报文里出现自造 type 键：后端 ReqVO 没有这个字段（§54 明令禁止）"; fail=1; }
[ "$EC2" = 1 ]   || { echo "× EX-c 契约测试不再钉住三键集合，报文内容没人守"; fail=1; }
[ "$ED" = 7 ]    || { echo "× EX-d 分类条目不是 7 项（§54 逐字列了七项）"; fail=1; }
[ "$ED_MISS" = 0 ] || { echo "× EX-d 有分类标签被改写或缺失（$ED_MISS 项）"; fail=1; }
[ "$EE" = 1 ]    || { echo "× EX-e remark 上限常量被改动（真实落点是 varchar(500)，客户端 200）"; fail=1; }
[ "$EE2" = 1 ]   || { echo "× EX-e 截断路径没了：超长 remark 会让后端整笔回滚"; fail=1; }
[ "$EF" = 1 ]    || { echo "× EX-f 路由常量不在了"; fail=1; }
[ "$EF2" = 1 ]   || { echo "× EX-f 异常页没注册进导航图：入口点了没反应"; fail=1; }
[ "$EF3" = 1 ]   || { echo "× EX-f 线路台不再有异常上报入口"; fail=1; }
[ "$EF4" = 1 ]   || { echo "× EX-f 进入意图函数没了：段号只能靠猜，会报错段"; fail=1; }
[ "$EG" -ge 3 ]  || { echo "× EX-g 「未发给后端」的分支少了：失败会被说成已提交（宁可没有上报，不允许假上报）"; fail=1; }
[ "$EG2" = 1 ]   || { echo "× EX-g 回读不一致那句没了：后端没接受也照样显示成功"; fail=1; }
[ "$EG3" = 0 ]   || { echo "× EX-g 页面出现「上报成功」：本机无权宣布后端接受了"; fail=1; }
[ "$EG4" = 1 ]   || { echo "× EX-g 本地幂等闸拦下那一格（ack.notSent 分支）不在了：一个字节都没发出去的情况又没地方说话了"; fail=1; }
[ "$EG5" = 0 ]   || { echo "× EX-g 拦下那一格开始宣布「已提交/成功」：本机把没发出去的说成已提交（宁可没有上报，不允许假上报）"; fail=1; }
[ "$EH" = 0 ]    || { echo "× EX-h 页面本机改写段状态（§26/§27：状态一律以回读为准）"; fail=1; }
[ "$EI" = 1 ]  || { echo "× EX-i 页面出网前的终态守卫没了：已完成的段也能把异常发给后端"; fail=1; }
[ "$EI2" = 1 ] || { echo "× EX-i 提交按钮不再判 canReportException：守卫只剩一条路径，按钮照样点得动"; fail=1; }
[ "$EI3" = 1 ] || { echo "× EX-i 线路台入口不再判 canReportException：终态段也长出「上报异常」按钮"; fail=1; }
[ "$EI4" = 1 ] || { echo "× EX-i 选段列表不再按 canReportException 禁用：司机能选中一段不可报的"; fail=1; }
[ "$EJ" = 1 ]  || { echo "× EX-j 选段行不再走 legOptionTitle：跨单重号的段又会都念「第 1 段」"; fail=1; }
[ "$EJ2" = 1 ] || { echo "× EX-j 提交回执不再带订单标识：回读那句仍然认不出是哪张单"; fail=1; }
[ "$EJ3" = 0 ] || { echo "× EX-j 页面里回来了手写的「第 N 段」直出串（$EJ3 处）：段序是单内序号，跨单必歧义"; fail=1; }
[ "$EJ4" = 1 ] || { echo "× EX-j 缺单号的兜底句被删：没单号时会退回只念段序，正是缺陷 #43 的写法"; fail=1; }
[ "$EK" = 1 ]  || { echo "× EX-k 「照片没传上就不写 photoUrl」这条判断没了：事件里会留下指向空地址的凭证"; fail=1; }
[ "$EK2" = 0 ] || { echo "× EX-k 页面里又内联拼了 photoUrl 键（$EK2 处）：一份键两处拼，改一处漏一处"; fail=1; }
[ "$EM" = 1 ]  || { echo "× EX-m 选片回调不再保留已选（真机抓的：重开选片器按返回就把刚拍的那张清了，页面回到「没附照片」那一形）"; fail=1; }
[ "$EM2" = 0 ] || { echo "× EX-m 页面里回来了「取消就把 picked 覆盖成 null」的裸赋值（$EM2 处）：司机以为还带着照片"; fail=1; }
[ "$EN" = 1 ]  || { echo "× EX-n 那句带原因的纯函数没了：回执会退回只念「没传上去」，谁都不知道为什么"; fail=1; }
[ "$EN2" = 1 ] || { echo "× EX-n 页面不再走 photoDropText（$EN2 处）：那句话被就地重写，纯函数用例就管不到运行时"; fail=1; }
[ "$EN3" = 1 ] || { echo "× EX-n 页面把上传失败的原因丢了（exceptionOrNull 命中 $EN3）：司机看到的回执与排查无关"; fail=1; }

if [ "$fail" = 0 ]; then echo "§54 异常上报判据通过"; else echo "§54 异常上报判据失败"; fi
exit "$fail"
