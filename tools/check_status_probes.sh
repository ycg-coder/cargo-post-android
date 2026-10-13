#!/usr/bin/env bash
# 状态码单一映射探针（审计 §18 / §8；docs/STATUS_MAPPING.md）
# 只按**内容**判据，不写行号 —— 行号会漂，内容不漂（§55 的教训）。
# 每轮收工必须实测并把数字抄进 AI_ITERATION_STATE.md 的常驻自检表；没有输出就等于没跑。
# 用法：bash tools/check_status_probes.sh（可用 BUSPOST_ANDROID 覆盖工程根）
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 1

A=app/src/main/java/com/shanxiang/buspost
UI_CORE="$A/ui $A/core"
SS="$A/data/model/ServerStatus.kt"
DOC="docs/STATUS_MAPPING.md"
TST="app/src/test/java/com/shanxiang/buspost/data/StatusMappingContractTest.kt"
MAPPER="$A/ui/driver/DriverLegStateMapper.kt"

# 注释行不算数：§28 收工后，反例只许以注释形式存在（"这里曾经是 status >= 4"）
no_comment() { grep -vE ":[0-9]+: *(\*|//)" ; }

# 司机段状态机里残留的数字比较（§25/§28）—— §28 落地后必须为 0
sa=$(grep -rnE "\.(status|orderStatus) (==|>=|<=|!) =? ?[0-9-]|[^a-zA-Z]status (==|>=|<=) [0-9-]" $UI_CORE | no_comment | wc -l)
# 用文案子串猜状态（"包含'完成'就算完成"）——必须是 0
sb=$(grep -rn "statusLabel\.contains(\|statusName\.contains(" $A/ui $A/data | no_comment | wc -l)
# 页面自己 when(数字) 排第二张码表 —— 必须是 0（按枚举 keyed 的 when 是合法的唯一表）
sc=$(grep -rnEA1 "when \(([a-zA-Z.]*status)\)( \{)?$" $UI_CORE | grep -E "[0-9]+ ->" | wc -l)
# 车辆运行态 when(v.status)：VehicleStatus 是 Android 自有枚举，不是后端整数码，允许存在
sc2=$(grep -rnE "when \(v\.status\)" $A/ui | wc -l)
# 审核码必须走 ReviewStatus.of(...)
sc3=$(grep -rn "ReviewStatus\.of(status)" $A | wc -l)
sd=$(grep -c "^enum class" "$SS")
se=$(grep -rn "STATUS_UNAVAILABLE_LABEL" $A | wc -l)
# 旧第三张表（0 待派单/…/4 已完成）的文字指纹，只允许出现在"说明它错"的注释里
sf=$(grep -rn "待派单" $A | wc -l)
# DemoData 里那份重复的货运表（0 -> "已创建"; 1 -> "待审核"; …）
sg=$(grep -rn '0 -> "已创建"' $A | wc -l)
# 缺 status 键读成 -1 的哨兵点（§14）
sh=$(grep -rn 'has("status")\|has("orderStatus")' $A | wc -l)
si=$(grep -rnE "(LegStatus|FreightOrderStatus|ProductOrderStatus|TaskItemStatus|ReviewStatus|HandoverStatus)\." $UI_CORE | wc -l)
sj=$([ -f "$DOC" ] && grep -c "TransportLegStatusEnum" "$DOC")
sk=$([ -f "$TST" ] && grep -c "@Test" "$TST")
# ---- §26/§27/§28 专属：客户端不算下一状态 ----
# 本机自造下一状态 / 按钮序号冒充状态机：必须为 0
la=$(grep -rnE "nextStatus|legStep|LEG_ACTIONS" $A/ui $A/data | no_comment | wc -l)
lb=$(grep -rn "copy(status = \|copy(status=" $A/ui | no_comment | wc -l)
# 段动作字符串只许出现在映射器与 Repo 各一处
lc=$(grep -rln '"arrive-dest"\|"handover-confirm"\|"arrive-origin"' $A | wc -l)
# 两个司机页面都必须走映射器
ld=$(grep -rl "DriverLegStateMapper\." $A/ui/driver | wc -l)
le=$(grep -rn "val ack = Repo.legAction(" $A/ui | no_comment | wc -l)
# 每处段动作写操作之后必须回读（§26/§27：状态一律以回读为准）。缺文件直接 exit 2，
# 否则 `grep -c` 对不存在的文件返回空，会被读成"回读点是 0"这种假红/假绿。
ler=""
for f in DriverExceptionScreen HandoverScreen RoutesScreen; do
    p="$A/ui/driver/$f.kt"
    [ -f "$p" ] || { echo "× S-le 判据文件缺失：$p（先确认工程根，别把缺文件读成没回读）"; exit 2; }
    [ "$(grep -n "Repo\.driverLegs()\|Repo\.currentLeg()" "$p" | no_comment | wc -l)" -ge 1 ] || ler="$ler $f"
done

echo "S-a  段状态机残留数字比较   : $sa   （预期 0：§28 后只许以注释形式提到旧写法）"
echo "S-b  文案子串猜状态         : $sb   （预期 0）"
echo "S-c  页面 when(数字) 排码表 : $sc   （预期 0）"
echo "S-c2 车辆运行态 when(v.status): $sc2 （预期 2：HomeScreen + BusScreen，是 Android 自有枚举不是后端码）"
echo "S-c3 审核码走 ReviewStatus.of: $sc3  （预期 6：ReviewMapper 2 + ReviewCard 2 + SendScreen 2）"
echo "S-d  ServerStatus 枚举数     : $sd   （预期 6：货运/段/商城/审核/任务点/交接）"
echo "S-e  未知码占位文案引用点    : $se   （预期 ≥10：2026-10-13 收到现读）"
echo "S-f  错注释文字指纹 待派单   : $sf   （预期 0）"
echo "S-g  DemoData 重复货运表     : $sg   （预期 0）"
echo "S-h  缺键 -1 哨兵点          : $sh   （预期 3：TransportLeg / MissionStop / Repo.traceOf）"
echo "S-i  页面/核心引用枚举点数   : $si   （预期 ≥65：现读 65，随改道上升；原 ≥20 离实测太远）"
echo "S-j  STATUS_MAPPING.md 段码表: $sj   （预期 ≥4：现读 4）"
echo "S-k  StatusMappingContractTest 用例数: $sk   （预期 ≥17：现读 17）"
echo "S-la 自造下一状态（nextStatus/legStep/LEG_ACTIONS）: $la   （预期 0，§26/§27/§28）"
echo "S-lb 本机 copy(status=...) 改写: $lb   （预期 0，§26/§27）"
echo "S-lc 段动作字面量出现的文件数: $lc   （预期 ≤2：现读 2 = 映射器 + DemoData 的演示报文；原 ≤3 多留一格，加第三处也不红）"
echo "S-ld 司机页引用 DriverLegStateMapper 的文件数: $ld   （预期 ≥3：RoutesScreen + HandoverScreen + DriverExceptionScreen）"
echo "S-le 页面直调 Repo.legAction 点数: $le   （预期 3，且每处后面都必须回读）"
echo "S-le2 不回读的写操作页        : ${ler:-无} （预期 无）"
echo "--- 残留段状态机位置（供复核，不判红）---"
grep -rnE "\.(status|orderStatus) (==|>=|<=|!) =? ?[0-9-]|[^a-zA-Z]status (==|>=|<=) [0-9-]" $UI_CORE | no_comment || true

ok=1
[ "$sb" = 0 ] || { echo "× S-b 文案子串判状态复现"; ok=0; }
[ "$sc" = 0 ] || { echo "× S-c 页面自建码表复现"; ok=0; }
[ "$sc2" = 2 ] || { echo "× S-c2 车辆运行态 when 数量变了（核对是否有人把后端码混进来）"; ok=0; }
[ "$sc3" = 6 ] || { echo "× S-c3 审核码入口减少"; ok=0; }
[ "$sd" = 6 ] || { echo "× S-d 单一映射表被增删（枚举数不为 6）"; ok=0; }
[ "$se" -ge 10 ] || { echo "× S-e 未知码占位文案引用点掉到 10 以下（2026-10-13 现读 10，原地板 ≥6 离实测太远，删四处也不红）"; ok=0; }
[ "$sf" = 0 ] || { echo "× S-f 错的段码注释又回来了"; ok=0; }
[ "$sg" = 0 ] || { echo "× S-g DemoData 第二张货运表复现"; ok=0; }
[ "$sh" = 3 ] || { echo "× S-h 缺键哨兵数量变化（§14 三态退化风险）"; ok=0; }
[ "$si" -ge 65 ] || { echo "× S-i 枚举引用点掉到 65 以下（现读 65，原地板 ≥20 形同不判）：有页面退回本地判断"; ok=0; }
[ "${sj:-0}" -ge 4 ] || { echo "× S-j 文档缺失或段码表被删（现读 4 处 TransportLegStatusEnum）"; ok=0; }
[ "${sk:-0}" -ge 17 ] || { echo "× S-k 契约测试用例被删（现读 17 条，原地板 ≥15 允许偷偷少两条）"; ok=0; }
[ "$sa" = 0 ] || { echo "× S-a 又出现了按数字比段状态"; ok=0; }
[ "$la" = 0 ] || { echo "× S-la 客户端又在算下一状态（§26/§27/§28 回潮）"; ok=0; }
[ "$lb" = 0 ] || { echo "× S-lb 本机改写段状态（§26/§27 回潮）"; ok=0; }
[ "$lc" -le 2 ] || { echo "× S-lc 段动作字面量散到了映射器之外（现读 2 个文件，原天花板 ≤3 多留一格）"; ok=0; }
[ "$ld" -ge 3 ] || { echo "× S-ld 司机页面退回本地状态判断（现读 3 个页面；原判定 ≥2 与话术 ≥3 不一致，本轮对齐成 3）"; ok=0; }
[ "$le" = 3 ] || { echo "× S-le 段动作入口数量变了（核对是否漏了回读）"; ok=0; }
[ -z "$ler" ] || { echo "× S-le2 这些页面写完不回读：$ler（本机自封状态 = 假闭环）"; ok=0; }

[ "$ok" = 1 ] || { echo "状态映射判据失败"; exit 1; }
echo "状态映射判据通过"
