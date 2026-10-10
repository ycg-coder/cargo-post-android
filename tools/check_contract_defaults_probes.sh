#!/usr/bin/env bash
# F-2 §三/§四/§五「缺失字段被默认值伪装」判据簿。
#   §三 = QuoteResult 金额四字段缺键塌 0.0（本轮 2026-09-30 收口）
#   §四 = Repo.optBoolean(…, true) 缺失态冒充"是"（F-2 第 4 步，本簿预留 B-* 段）
#   §五 = 全项目数字默认值的 A(真零值)/B(缺字段)/C(网络异常)/D(UI 占位) 分类（第 5 步，预留 D-* 段）
# 契约口径 = 2026-09-30 三方实测：后端 producer（AppSendQuoteRespVO.java:13-25 + AppSendController.java:506-513
#   + CargoPricingService.java:45-75）、小程序 consumer（send.wxml:282-287）、演示面 writer（DemoData.kt:472-476）。
# 只按**内容**判据，不写行号（§55）；每轮收工必须实测并把数字抄进 AI_ITERATION_STATE.md 的常驻自检表。
# 用法：bash tools/check_contract_defaults_probes.sh（可用 BUSPOST_ANDROID 覆盖工程根）
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 1

A=app/src/main/java/com/shanxiang/buspost
MD="$A/data/model/Models.kt"
DM="$A/data/repo/DemoData.kt"
SS="$A/ui/user/SendScreen.kt"
TQ="$ROOT/app/src/test/java/com/shanxiang/buspost/data/QuoteNullSafetyTest.kt"
NB="$ROOT/app/src/test/java/com/shanxiang/buspost/data/NearbyBooleanContractTest.kt"
# 只取 QuoteResult 的 parse 体：把别名键写进别的解析函数不归本条管（那是各自契约的判据）
QP=$(sed -n '/fun parse(o: JSONObject): QuoteResult/,/^    }$/p' "$MD")
# 只取演示面 /send/quote 分支的报文形状
DQ=$(sed -n '/path.endsWith("\/send\/quote")/,/^            }$/p' "$DM")
# 只取 QuoteResult 的**整个类体**（构造参数 + 类内 getter）：右端必须锚到 ^}，
# 锚 ^) { 只截到构造参数，getter 那三行不在里面 ⇒ Q-e1 恒 0（本簿 Q-a 用 ^) { 是对的，它只数构造参数）
QR=$(sed -n '/^data class QuoteResult/,/^}/p' "$MD")
guard_qr=$(printf '%s\n' "$QR" | wc -l | tr -d ' ')

# ---- GUARD：grep 计数管道一旦失效，下面所有"预期 0"会集体变成假绿（v2.1 普查簿实测过的同一类事故）----
# 判据文件先验存在再计数：grep 找不到文件时输出是空的，"预期 0" 那几条会集体假绿（壳路由那本踩过，规矩已升册）
for f in "$MD" "$DM" "$SS" "$TQ" "$NB"; do
  [ -e "$f" ] || { echo "× GUARD 判据文件缺失：$f ⇒ 本簿所有『预期 0』一律不采信"; exit 2; }
done
[ -d "$A" ] || { echo "× GUARD 扫描目录缺失：$A ⇒ 本簿一律不采信"; exit 2; }
guard_pipeline=$(printf 'aa bb\n' | grep -hoE 'bb' | wc -l | tr -d ' ')
# 正控制：别名模式必须在测试源码（负控输入所在地，不在判据扫描范围内）数得到，数不到就判红而不是判绿
guard_alias=$(grep -rhoE '"(fee|totalPrice|unitFee|mileageFee|count)"' "$TQ" 2>/dev/null | wc -l | tr -d ' ')

# Q-a QuoteResult 五个数值字段全是可空声明（amount/itemFee/distanceFee/itemCount/distanceKm）
qa=$(sed -n '/^data class QuoteResult/,/^) {/p' "$MD" | grep -cE 'val (amount|itemFee|distanceFee|itemCount|distanceKm): (Double\?|Int\?)')
# Q-b parse 体里的自造别名键 —— 三面实测无生产者：后端 VO 没这些字段、小程序不读、演示面不写
qb=$(printf '%s' "$QP" | grep -oE '"(fee|totalPrice|unitFee|mileageFee|count)"' | wc -l | tr -d ' ')
# Q-b2 演示面也不许下发这些键（否则"无生产者"论据当场失效，等于给别名续命）
qb2=$(printf '%s' "$DQ" | grep -oE '"(fee|totalPrice|unitFee|mileageFee|count)"' | wc -l | tr -d ' ')
# Q-c 客户端自造件数的兜底通道必须关闭：fallbackCount 参数 + coerceAtLeast 归一化
qc1=$(grep -rn 'fallbackCount' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' ')
qc2=$(printf '%s' "$QP" | grep -c 'coerceAtLeast')
# Q-c2 五个字段的读法一律 multiNumOrNull：退回 multiNum 就是把"没给"读成 0
qc3=$(printf '%s' "$QP" | grep -oE 'o\.multiNum\(' | wc -l | tr -d ' ')
# Q-d 页面把可空金额裸拼进文案（$模板里直读 .amount/.itemFee/.distanceFee/.itemCount 而不走 getter）
qd=$(grep -rnE '\$\{?(q|it|vm\.quote!!)\.(amount|itemFee|distanceFee|itemCount)\b' $A/ui --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' ')
# Q-d2 页面把金额交给非空形参的 formatPrice（formatPrice(v: Double) 接不了 null，说明上游已经塌过 0）
qd2=$(grep -rnE 'formatPrice\((q|it|vm\.quote!!)\.amount\)' $A/ui --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' ')
# Q-e 诚实文案 getter 的声明处与页面使用处（getter 落在模型上是为了能被 JVM 断言，见 distanceCaliberText 同条理由）
#      两条都收成"QuoteResult / SendScreen 各自的范围"：§五 那一轮给 EarningItem 也起了个叫 amountText 的 getter，
#      全文计数于是由 3 涨到 4、页面由 4 涨到 5 —— 涨的是**另一条契约**（DV-g2 管它）。
#      同名成员混进一条判据，数字变了看不出是谁改的（R-g / 本簿 B-e 同一条理由），且 ≥4 那条会悄悄靠别人凑数。
qe1=$(printf '%s' "$QR" | grep -cE 'val (amountText|amountValueText|feeBreakdownText): String')
# 扫描面由"$A/ui 全目录"收成 SendScreen 一个文件（EarningsScreen 的 item.amountText 属于 §五 那条契约，归 DV-h 管）。
# 注释行过滤器要跟着换形状：单文件 grep 输出是 `行号:内容`（没有文件名前缀），
# 沿用 `:[0-9]+: *(\*|//)` 会一条都滤不掉 ⇒ 两行注释被数进用例（实测 4 → 6）
qe2=$(grep -nE 'amountValueText|feeBreakdownText|\.amountText' "$SS" | grep -vE '^[0-9]+:[[:space:]]*(\*|//)' | wc -l | tr -d ' ')
# Q-f 本轮契约测试用例数（①真实键照常 ②缺键不显示免费 ③显式 null ④五个别名不采纳 ⑤0 与缺失分开 ⑥空报文 ⑦真 0 件）
qf=$([ -f "$TQ" ] && grep -cE "^[[:space:]]*@Test" "$TQ" || echo 0)

# ---- 以下 B-* 是 F-2 第 4 步（NearbyResult 严禁缺失 boolean 默认 true）新增的判据 ----
# 契约口径（2026-09-30 三方实测）：后端 AppBusNearbyRespVO:45 located / :65 inService（AppBusServiceImpl:256/:481/:532 赋值），
# 小程序按三态读（bus/index.js:240 `!= null ? : null`、:288/:291；index/index.js:539/:554 `=== false ? 'off' : 'empty'`），
# 演示面 /bus/nearby 报文两个键都不写 ⇒ 缺失只能是 UNKNOWN(null)。
NB="$ROOT/app/src/test/java/com/shanxiang/buspost/data/NearbyBooleanContractTest.kt"
# B-a 两个 boolean 声明为可空
ba=$(sed -n '/^data class NearbyResult/,/^) {/p' "$MD" | grep -cE 'val (inService|located): Boolean\?')
# B-b "缺失就当 true"的读法归零（§四 的原始破口形状）
bb=$(grep -rnE 'optBoolean\([^)]*, *true\)' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' ')
# B-c 剩下的 optBoolean 总数（只登记不判红：默认 false 的那一处属于 §五 的 A/B 分类，见本簿末尾残留）
bc=$(grep -rn 'optBoolean' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' ')
# B-d 无人调用的 bool(def) 助手不得复活（它是下一个"缺失当默认值"的入口）
bd=$(grep -rn 'fun bool(key: String' $A --include=*.kt | wc -l | tr -d ' ')
# B-e 三态判定收进模型 getter，页面只认 isOffService
be1=$(grep -c 'val isOffService: Boolean' "$MD")
be2=$(grep -rn 'isOffService' $A/ui --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' ')
be3=$(grep -rn '!r\.inService' $A/ui --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' ')
# B-f 自造别名键 realTransit 归零（真实键只有 realTransitAvailable：VO:50 + service:276/:287 + mp index.js:553 + DemoData:612）
bf=$(grep -rn '"realTransit"' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' ')
# B-g 本轮契约测试用例数（走 MockWebServer 真信封，不是只测构造默认值）
bg=$([ -f "$NB" ] && grep -cE "^[[:space:]]*@Test" "$NB" || echo 0)
# 正控制：这两个键名必须在测试源码里数得到
guard_b=$(grep -rhoE 'inService|located' "$NB" 2>/dev/null | wc -l | tr -d ' ')

echo "Q-a  QuoteResult 可空数值字段        : $qa  （预期 5：amount/itemFee/distanceFee/itemCount/distanceKm）"
echo "Q-b  parse 体里的自造别名键          : $qb  （预期 0：后端/小程序/演示面三面都无生产者）"
echo "Q-b2 演示面 quote 报文的别名键       : $qb2 （预期 0：demo 不得成为第四个真相源）"
echo "Q-c1 fallbackCount 兜底参数          : $qc1 （预期 0：客户端件数不许替后端说话）"
echo "Q-c2 parse 体内 coerceAtLeast        : $qc2 （预期 0）"
echo "Q-c3 parse 体内 multiNum( 非可空读法 : $qc3 （预期 0：缺键不许读成 0）"
echo "Q-d  页面裸拼可空金额字段            : $qd  （预期 0：一律走 amountText/amountValueText/feeBreakdownText）"
echo "Q-d2 页面把金额喂给非空 formatPrice  : $qd2 （预期 0：能编译就说明上游已塌 0）"
echo "Q-e1 模型未给文案 getter             : $qe1 （预期 3，只数 QuoteResult 类体；EarningItem 的同名 getter 归 DV-g2）"
echo "Q-e2 页面使用未给 getter             : $qe2 （预期 4，只数 SendScreen：试算卡 2 + 核对页 1 + 车票副券 1）"
echo "Q-f  QuoteNullSafetyTest 用例数      : $qf  （预期 ≥7）"
echo "GUARD 计数管道正控制                 : pipeline=$guard_pipeline（预期 1） 别名模式在测试源码=$guard_alias（预期 ≥5）"
echo "B-a  NearbyResult 可空 boolean       : $ba  （预期 2：inService/located）"
echo "B-b  optBoolean(…, true) 读法        : $bb  （预期 0：§四 的原始破口形状）"
echo "B-c  剩余 optBoolean 总数（只登记）   : $bc  （残留，见本簿末尾，不判红）"
echo "B-d  Api 的 bool(def) 助手           : $bd  （预期 0：main+test 实测 0 调用点，已删）"
echo "B-e1 模型 isOffService getter        : $be1 （预期 1）"
echo "B-e2 页面使用 isOffService           : $be2 （预期 2：线路页 + 首页）"
echo "B-e3 页面直接判 !r.inService         : $be3 （预期 0：缺失会被读成『是』再取反，等于把没数据说成运营中）"
echo "B-f  自造别名键 \"realTransit\"        : $bf  （预期 0：三面都无生产者）"
echo "B-g  NearbyBooleanContractTest 用例  : $bg  （预期 ≥6）"
echo "GUARD-B 键名在测试源码的命中          : $guard_b（预期 ≥10）"
echo "--- 残留（本轮范围外，供 §五/下一轮复核，不判红）---"
# 收紧扫描面之后，"被排除的那几处到底还在不在"必须留读数，否则判据只是把看不见的东西当作不存在
echo "· 全 Models.kt 的 amountText 家族 getter（含 §五 的 EarningItem 那一条，归 DV-g2 管）：$(grep -cE 'val (amountText|amountValueText|feeBreakdownText): String' "$MD")"
echo "· 全 ui 的未给 getter 使用点（含 EarningsScreen 那一条，归 DV-h 管）：$(grep -rnE 'amountValueText|feeBreakdownText|\.amountText' $A/ui --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' ')"
echo "· SendScreen 的 fresh 仍用 optBoolean 默认 false（方向是保守侧，但缺键≠真不新鲜，属 §五 B 类）："
grep -rn 'optBoolean' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' '
echo "· Repo 里「缺键即视为可用」的 has() 短路（routeAvailable 有生产者 AppDriverRouteRespVO:39，但缺失态被并给 true）："
grep -rn '!o\.has("routeAvailable") ||' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | wc -l | tr -d ' '
echo "· multiBool(…) 非可空读法总量（§五 要逐个分 A/B 类，本轮只处理 §四 点名的两个 true）："
grep -rn 'multiBool(' $A --include=*.kt | grep -vE ':[0-9]+: *(\*|//)' | grep -vc 'multiBoolOrNull' | tr -d ' '
echo "· Api 信封助手仍带非空默认值（str/int/dbl，bool 已删）："
grep -cE 'fun (str|int|dbl)\(key: String, def' "$A/core/net/Api.kt"

ok=1
[ "$guard_pipeline" = 1 ] || { echo "× GUARD grep -o 计数管道不工作，本簿所有『预期 0』一律不采信"; ok=0; }
[ "$guard_alias" -ge 5 ] || { echo "× GUARD 别名模式在测试源码 0 命中：计数失效，Q-b/Q-b2 的 0 是假绿"; ok=0; }
[ "$qa" = 5 ] || { echo "× Q-a QuoteResult 数值字段退回非空默认值（缺键会说成 ¥0 / 0 件）"; ok=0; }
[ "$qb" = 0 ] || { echo "× Q-b 自造别名键回到 QuoteResult 解析（fee/totalPrice/unitFee/mileageFee/count 无生产者）"; ok=0; }
[ "$qb2" = 0 ] || { echo "× Q-b2 演示面开始下发别名键（给『无生产者』论据续命）"; ok=0; }
[ "$qc1" = 0 ] || { echo "× Q-c1 fallbackCount 复活（后端 normalize 过的件数被客户端值覆盖）"; ok=0; }
[ "$qc2" = 0 ] || { echo "× Q-c2 解析层用 coerceAtLeast 把缺失抬成客户端猜测值"; ok=0; }
[ "$qc3" = 0 ] || { echo "× Q-c3 金额读法退回 multiNum（缺键塌 0.0，F-2 §三 的原始破口）"; ok=0; }
[ "$qd" = 0 ] || { echo "× Q-d 页面裸拼可空金额（null 会被打印进文案，或上游已塌成 0）"; ok=0; }
[ "$qd2" = 0 ] || { echo "× Q-d2 页面把金额交给非空 formatPrice（说明缺失已在某处被伪装成数字）"; ok=0; }
[ "$guard_qr" -ge 20 ] || { echo "× GUARD-QR 没截到 QuoteResult 类体（sed 锚点失效，Q-e1 的 3 不采信）"; ok=0; }
[ "$qe1" = 3 ] || { echo "× Q-e1 模型的『未给』文案 getter 被删（缺失态又只剩 grep 判据）"; ok=0; }
[ "$qe2" = 4 ] || { echo "× Q-e2 页面不再使用未给文案 getter（¥0 复发而测试仍是绿的）"; ok=0; }
[ "$qf" -ge 7 ] || { echo "× Q-f QuoteNullSafetyTest 用例被删（缺键塌 0 复发无人报警）"; ok=0; }
[ "$ba" = 2 ] || { echo "× B-a NearbyResult 的 inService/located 退回非空默认值（缺失会被说成『运营中/定位成功』）"; ok=0; }
[ "$bb" = 0 ] || { echo "× B-b 又出现 optBoolean(…, true)：§四 点名的『缺失当是』破口复发"; ok=0; }
[ "$bd" = 0 ] || { echo "× B-d 无人调用的 bool(def) 助手复活（给下一个缺失默认值留入口）"; ok=0; }
[ "$be1" = 1 ] || { echo "× B-e1 模型的三态 getter 被删（off 判定回到页面裸读，JVM 测不到）"; ok=0; }
[ "$be2" = 2 ] || { echo "× B-e2 页面不再统一走 isOffService（首页与线路页会各自漂一套缺失口径）"; ok=0; }
[ "$be3" = 0 ] || { echo "× B-e3 页面又直接判 !r.inService（可空值取反 = 缺失被当成『是』）"; ok=0; }
[ "$bf" = 0 ] || { echo "× B-f 自造别名键 realTransit 回到 main（真实键只有 realTransitAvailable）"; ok=0; }
[ "$bg" -ge 6 ] || { echo "× B-g NearbyBooleanContractTest 用例被删（缺失当『是』复发无人报警）"; ok=0; }
[ "$guard_b" -ge 10 ] || { echo "× GUARD-B 键名在测试源码 0 命中：计数管道失效，B-b/B-f 的 0 不采信"; ok=0; }

[ "$ok" = 1 ] && echo "缺键默认值判据通过（§三 QuoteResult 五字段可空 + 别名键归零 + 兜底通道关闭 + §四 NearbyResult boolean 三态）" || { echo "缺键默认值判据未通过"; exit 1; }
