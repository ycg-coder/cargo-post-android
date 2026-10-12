#!/usr/bin/env bash
# 可达性真实化探针（审计 §34 + 用户 §11 三态；契约口径 = 后端 AppSendReachabilityRespVO.java:19-56）
# 只按**内容**判据，不写行号 —— 行号会漂，内容不漂（§55 的教训）。
# 每轮收工必须实测并把数字抄进 AI_ITERATION_STATE.md 的常驻自检表；没有输出就等于没跑。
# 用法：bash tools/check_reachability_probes.sh（可用 BUSPOST_ANDROID 覆盖工程根）
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 1

A=app/src/main/java/com/shanxiang/buspost
MD="$A/data/model/Models.kt"
RP="$A/data/repo/Repo.kt"
DM="$A/data/repo/DemoData.kt"
SS="$A/ui/user/SendScreen.kt"
TST="app/src/test/java/com/shanxiang/buspost/data/repo/ReachabilityContractTest.kt"

# 注释行不算数：反例只许以注释形式存在
no_comment() { grep -vE ":[0-9]+: *(\*|//)" ; }

# R-a 界面拿布尔 reachable 分支：三态之后必须为 0（页面只许按 state 分支）
ra=$(grep -rnE "\.reachable\b" $A/ui | no_comment | wc -l)
# R-b 界面继续读旧 distanceMeters 键（后端契约里没有这个字段）
rb=$(grep -rnE "reach[?!.]+distanceMeters" $A/ui | wc -l)
# R-c 写死的 "STATION" 服务方式：不在 ServiceModeEnum 五码里，**生产代码**必须为 0（注释里的反例已被 no_comment 滤掉）
rc=$(grep -rn '"STATION"' $A | no_comment | wc -l)
# R-c2 反面用例必须还钉在测试里：fixture 里那份 "STATION" 不许被"顺手清理"掉
# （只数报文里的 serviceMode 字面量，不数注释里提到它的那些行 —— 注释会增会删，报文不会）
rc2=$(grep -c '"serviceMode":"STATION"' "$TST")
# R-d 用推荐站点名顶替用户原始地址（后端 :172 原样落库，两者不许互相覆盖）
rd=$(grep -rnE '"originalAddress"[^,]*to[^,]*(nearestStation|stationLabel|stationName|station\.name)' $A | wc -l)
# R-e 三态枚举被真实引用的点数（模型/页面/测试）
re=$(grep -rn "ReachabilityState" $A app/src/test | wc -l)
# R-f 缺键/null 的哨兵读法：multiBoolOrNull 声明 1 + Reachability 解析体内使用 1（塌成 multiBool 就等于把 UNKNOWN 当不可达）
#      原来扫全 $A 数 helper 名，F-2 第 4 步把 NearbyResult 的 inService/located 也改成 multiBoolOrNull 后由 2 涨到 4 ——
#      涨的是**另一条契约**的读法（归 B-* 那本管）。与 R-g 同一条理由：同名成员混进一条判据，数字变了看不出是谁改的。
rfd=$(grep -c 'fun JSONObject\.multiBoolOrNull' "$MD")
rfb=$(sed -n '/fun parse(o: JSONObject): Reachability {/,/^        }$/p' "$MD" | grep -c 'o\.multiBoolOrNull(')
rf=$((rfd + rfb))
# R-g 公里与步行分钟必须是可空类型，否则"接口未给"会被渲染成 0
#      只数 Reachability 类体内那两行：§38 之后 BusVehicle 也有 distanceKm: Double?（车辆离用户多远），
#      全文件计数会把两条不同契约的字段混成一条判据（数字从 2 变 3 时没人看得出是谁改的）
rg=$(sed -n '/^data class Reachability/,/^) {/p' "$MD" | grep -cE "val (walkMinutes: Int\?|distanceKm: Double\?)")
# R-h 页面直接把 walkMinutes 拼进文案（null 会打印成"null 分钟"）：必须为 0，一律走 walkText
rh=$(grep -rnE "walkMinutes\}|\$\{reach.*walkMinutes" $A/ui | wc -l)
# R-i 距离来源如实标注：只数 providerLabel 那一段（全仓"直线估算"字样太多，当判据等于没判据）
ri=$(grep -A4 "val providerLabel" "$MD" | grep -c "直线估算")
# R-j 契约测试用例数：只数**生效的**注解（行首 @Test），被注释掉的 //@Test 不算一条用例
rj=$([ -f "$TST" ] && grep -cE "^[[:space:]]*@Test" "$TST")
# R-k Repo 只有一个解析出口：数**出现次数**而不是行数（两处调用挤一行也只算两个出口）
#      KDoc 里引用类名不带括号，不算第二个出口
rk=$(grep -o "Reachability\.parse(" "$RP" | wc -l)
rkb=$(grep -nE "Reachability\(" "$RP" | grep -vc "Reachability\.parse" || true)
# R-l 演示兜底必须按真实契约形状给字段：distanceKm（外层 + 站内共 2 次）/recommendedStation/serviceMode/distanceProvider
rl=$(grep -A14 '/send/reachability' "$DM" | grep -cE '"distanceKm"|"recommendedStation"|"serviceMode"|"distanceProvider"')
# R-m 提交侧服务方式只有一个出口函数：数出现次数（定义 1 + 调用 1），同处两个调用点也必须看得见
rm=$(grep -o "pickupServiceModeOrNull" "$SS" | wc -l)
# R-n 页面 UNKNOWN 分支存在（UNKNOWN 既不能说可运也不能编原因）
rn=$(grep -c "ReachabilityState.UNKNOWN" "$SS")
# R-n2 UNKNOWN 分支里说"可运"的写法（§11 的红线：分支存在不等于分支说得对，所以单独数语义）。
#      分支体是多行的，只看 UNKNOWN 之后 6 行 —— 那里若出现"可就近/可以运输"就是说谎。
rn2=$(grep -A6 "ReachabilityState.UNKNOWN" "$SS" | grep -cE "可就近|可以运输")
# R-n3 没判定时必须保留那句免责话。
#   旧字面量"不代表车辆能到"在 D-0xx 那轮界面话术去技术化里被换成
#   `SendScreen.kt:1127` 的"本单不声称车辆可上门"（语义等价：UNKNOWN 分支不许被读成"能运"），
#   判据跟着换点名对象，**不是放松**：句子再被删掉或换成一句万能提示，这里照样红。
rn3=$(grep -c "本单不声称车辆可上门" "$SS")
# R-o REACHABLE 但不许静默：后端判定可上门却没给就近站点时，必须当场提示"请手动选择"
ro=$(grep -c "后端判定可上门，但没给出就近站点" "$SS")
# R-o2 "推荐站点不在可选列表"这句必须正好两处：不可达分支 + 可达分支各一处
#      （只数一处 = 可达场景仍然静默；数到三处 = 有人又把这句话搬进了别的分支）
ro2=$(grep -c "推荐站点不在可选列表" "$SS")
# R-p 逆地理没给文字地址时这一行不许消失（藏起来 = 让用户以为单子没有位置信息）
rp=$(grep -c "文字地址未取到" "$SS")

echo "R-a  界面按布尔 reachable 分支        : $ra   （预期 0：只许按 state 三态分支）"
echo "R-b  界面读旧 distanceMeters 键       : $rb   （预期 0：后端契约里没有）"
echo "R-c  写死的 \"STATION\" 服务方式        : $rc   （预期 0：不在 ServiceModeEnum 五码里）"
echo "R-c2 测试里的 \"STATION\" 反面 fixture  : $rc2  （预期 1：这条非法码必须继续被钉住）"
echo "R-d  站点名顶替用户原始地址           : $rd   （预期 0）"
echo "R-e  ReachabilityState 引用点         : $re   （预期 ≥10：模型 3 + 页面 6 + 测试 若干）"
echo "R-f  multiBoolOrNull（声明 $rfd + 可达性解析 $rfb）: $rf   （预期 2：Models 声明 1 + parse 使用 1）"
echo "R-g  公里/步行为可空类型              : $rg   （预期 2：walkMinutes: Int? 与 distanceKm: Double?）"
echo "R-h  页面裸拼 walkMinutes             : $rh   （预期 0：一律走 walkText）"
echo "R-i  直线来源如实标注                 : $ri   （预期 1：模型 providerLabel 里那一句）"
echo "R-j  ReachabilityContractTest 用例数  : $rj   （预期 ≥17）"
echo "R-k  Repo 解析出口（parse 次数）      : $rk   （预期 1）"
echo "R-k2 Repo 直接构造模型次数            : $rkb  （预期 0）"
echo "R-l  演示兜底真实形状字段数           : $rl   （预期 5：distanceKm 外层+站内各 1，recommendedStation/serviceMode/distanceProvider）"
echo "R-m  服务方式唯一出口函数             : $rm   （预期 2：定义 1 + buildSendForm 调用 1）"
echo "R-n  页面 UNKNOWN 分支               : $rn    （预期 ≥2：卡片分支 + serviceModeText）"
echo "R-n2 UNKNOWN 分支里的\"可运\"话术      : $rn2   （预期 0：没判定不等于能运）"
echo "R-n3 UNKNOWN 免责句（本单不声称车辆可上门）: $rn3   （预期 ≥1）"
echo "R-o  REACHABLE 没站点时的手选提示     : $ro    （预期 1：可上门却不许静默）"
echo "R-o2 \"推荐站点不在可选列表\"两处       : $ro2   （预期 2：可达 + 不可达各一处）"
echo "R-p  文字地址缺失时不藏行             : $rp    （预期 ≥1：改成\"只提交了经纬度\"）"
echo "--- 残留位置（供复核，不判红）---"
grep -rnE "\.reachable\b|reach[?!.]+distanceMeters" $A/ui || true
# 收紧扫描面之后，"另外那几处到底还在不在"必须有读数，否则 R-f 只是把看不见的东西当作不存在
echo "· multiBoolOrNull 全项目出现次数（含 NearbyResult 的 inService/located，那两处归 F-2 §四 的 B-* 判据管）："
grep -rn "multiBoolOrNull" $A | wc -l

ok=1
[ "$ra" = 0 ] || { echo "× R-a 界面退回按布尔 reachable 分支（§11 三态退化）"; ok=0; }
[ "$rb" = 0 ] || { echo "× R-b 界面还在读后端没有的 distanceMeters"; ok=0; }
[ "$rc" = 0 ] || { echo "× R-c 非法服务方式 \"STATION\" 复现（会污染 pickupServiceMode 落库）"; ok=0; }
[ "$rc2" = 1 ] || { echo "× R-c2 反面 fixture 被删，R-c 就变成没人证的空判据"; ok=0; }
[ "$rd" = 0 ] || { echo "× R-d 用户原始地址又被站点名覆盖"; ok=0; }
[ "$re" -ge 10 ] || { echo "× R-e 三态引用点掉到 10 以下"; ok=0; }
[ "$rf" = 2 ] || { echo "× R-f 缺键哨兵读法数量变了"; ok=0; }
[ "$rg" = 2 ] || { echo "× R-g 公里/步行分钟又变成非空类型（null→0 风险，§14）"; ok=0; }
[ "$rh" = 0 ] || { echo "× R-h 页面把 null 步行分钟直接拼进文案"; ok=0; }
[ "$ri" = 1 ] || { echo "× R-i 直线估算不再被如实标注"; ok=0; }
[ "${rj:-0}" -ge 17 ] || { echo "× R-j 契约测试用例被删"; ok=0; }
[ "$rk" = 1 ] || { echo "× R-k 可达性解析出现第二个出口"; ok=0; }
[ "$rkb" = 0 ] || { echo "× R-k2 Repo 又开始自己拼 Reachability 模型"; ok=0; }
[ "$rl" = 5 ] || { echo "× R-l 演示兜底形状偏离真实契约"; ok=0; }
[ "$rm" = 2 ] || { echo "× R-m 服务方式出现第二个出口"; ok=0; }
[ "$rn" -ge 2 ] || { echo "× R-n 页面不再处理 UNKNOWN（会把没判定说成可运）"; ok=0; }
[ "$rn2" = 0 ] || { echo "× R-n2 UNKNOWN 分支出现了\"可运\"话术（§11 红线）"; ok=0; }
[ "$rn3" -ge 1 ] || { echo "× R-n3 UNKNOWN 分支的免责句没了（车辆能不能到没判定，页面却可以留一句看起来像结论的话）"; ok=0; }
[ "$ro" = 1 ] || { echo "× R-o REACHABLE 分支又变成静默（没站点却不提示手选）"; ok=0; }
[ "$ro2" = 2 ] || { echo "× R-o2 \"站点不在可选列表\"的提示数量变了（可达或不可达分支少了一处）"; ok=0; }
[ "$rp" -ge 1 ] || { echo "× R-p 文字地址没取到时整行被藏了（§14 的\"没有值要说没有\"）"; ok=0; }

[ "$ok" = 1 ] && echo "可达性判据通过" || { echo "可达性判据未通过"; exit 1; }
