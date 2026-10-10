#!/usr/bin/env bash
# 定位与上报链路判据（P1-3 ⑦，配合 CONTEXT_LOCK.md §八 与审计 §17/§45）
# 用法：bash tools/check_location_probes.sh        退出码非 0 = 链路破了纪律
# 判据写法沿用 ⑥ 的教训：**按内容/结构过滤，不按行号**（行号白名单会漂）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT/app/src/main/java" || { echo "找不到 $ROOT/app/src/main/java"; exit 2; }

KT=(--include=*.kt)

# L-a 坐标系换算的真实调用点：定位出口 + 折线展示视图，两处，一处不多一处不少
LA=$(grep -rn "${KT[@]}" "CoordinateTransform\.\(convert\|convertAll\)(" . \
  | grep -v "core/map/CoordinateSystem\.kt" | wc -l | tr -d ' ')

# L-b 手写 Geo(..latitude) 是否越出 Locator（排除注释行：以 `*` 开头的续行）
#
# 白名单只有一个例外，理由不是"方便"，是坐标系归属不同：
# `core/navigation/AmapNavigationEngine.kt` 里的 `Geo(it.latitude, it.longitude)` 搬运的是
# **高德 `getCoordList()` 的折点**，高德给出来就已经是 GCJ-02；在这里再走 `Locator.toGcj02`
# 才是错的（二次偏移，判据 N-c 钉它为 0）。L-b 真正要防的是"把设备定位源的原始 WGS-84 点手抄成 Geo"，
# 那种点必须只有一个出口（Locator）。例外按文件写死、并按点数封顶（L-b2），换文件搬运就红。
LB=$(grep -rn "${KT[@]}" "Geo([a-zA-Z]*\.latitude" . \
  | grep -v "core/loc/Locator\.kt" | grep -v "core/navigation/AmapNavigationEngine\.kt" \
  | grep -vE ":[0-9]+: *\*" | wc -l | tr -d ' ')

# L-b2 SDK 折点搬运处数：豁免**按数量封顶 1 处**而不是按文件放行整个文件。
# 教训：只写文件级 grep -v 时，在这个文件里再加第二处 Geo(p.latitude, p.longitude) 判据照样绿
# （本轮负控 BREAK2 实测 rc=0 暴露了这个洞）—— 按文件豁免等于把出口唯一性换成"信这个文件"。
LB2=$(grep -c "Geo([a-zA-Z]*\.latitude" com/shanxiang/buspost/core/navigation/AmapNavigationEngine.kt)

# L-c 上报报文里是否混进了客户端时间键（后端 VO 没有这个字段，带上就是伪造轨迹时间）
LC=$(grep -n -A 12 '"\$T/driver/location"' com/shanxiang/buspost/data/repo/Repo.kt \
  | grep -icE "locateTime|clientTime|gpsTime|reportTime|\"time\"")

# L-d 旧拦停文案：本机没有班次就不上报（后端 P1-H 后这是错的，不得回潮）
LD=$(grep -rn "${KT[@]}" "当前没有在途班次" . | wc -l | tr -d ' ')

# L-e 手写偏移公式（Krasovsky 常数 / 扰动函数）是否出现在 CoordinateSystem.kt 之外
LE=$(grep -rnE "${KT[@]}" "0\.00669342162296594323|transformLat|transformLng|X_PI|6378245" . \
  | grep -v "core/map/CoordinateSystem\.kt" | grep -vE ":[0-9]+: *\*" | wc -l | tr -d ' ')

# L-f 是否引入第二个定位源（3dmap jar 已内置 com.amap.api.location，再引会 duplicate class；
#     且两套坐标源混用就是 §17 的另一半成因）
# 2026-10-10 重锚（未入账那轮新增的 AmapCompliance.kt）：**类名本身不再是判据**。
#   那本文件只调 `AMapLocationClient` 的两个 **static 隐私合规开关**（javap 逐签名核对见
#   tools/fixtures/amap_compliance_javap.txt），它一个坐标都不产生；旧写法把 import、两次静态调用、
#   两条日志一起数成"第二定位源"（本轮实测 5）⇒ 那是判据不精确，不是代码回归。
#   真破口的形状是"实例化 + 起定位"，所以按那三件事数：构造器 / startLocation* / Option 配置类。
#   同时**收紧一条**（口径只抬不降）：`com.amap.api.location` 这个包只许出现在 AmapCompliance.kt 一本里，
#   别处 import 它就是第二个坐标源 —— 豁免是被点名且限死的，不是把闸门整体放松。
#   两条反证由 location_negative_controls.sh 的 LF-a（实例化并 startLocation）/ LF-b（第二本文件 import 该包）钉。
LF=$(grep -rn "${KT[@]}" -e 'AMapLocationClient(' -e '\.startLoc' -e 'AMapLocationClientOption' . | wc -l | tr -d ' ')
LF2=$(grep -rn "${KT[@]}" "com\.amap\.api\.location" . | grep -v "core/navigation/AmapCompliance\.kt" | wc -l | tr -d ' ')

# L-g 丢弃点数是否仍有出口（删掉它 = 弱网丢点重新变成静默丢）
LG=$(grep -c "已丢弃" com/shanxiang/buspost/driver/DriverLocationService.kt)

# L-h 是否有绕过 convert() 直接调底层正/反算的业务代码
LH=$(grep -rn "${KT[@]}" "wgs84ToGcj02\|gcj02ToWgs84\|bd09ToGcj02\|gcj02ToBd09" . \
  | grep -v "core/map/CoordinateSystem\.kt" | wc -l | tr -d ' ')

# L-v 地名硬编码（本轮新增，成因是设备实测不是读源码猜的）：
#     16:42 在 AVD 上把 mock 定位打到加州（37.42,-122.16），首页顶栏念出来的是 **"云山村"** ——
#     逆向地理编码什么都没给，而 `Prefs.currentVillage` 的默认值和村庄选择器的 `ifEmpty` 填充表
#     各藏了一份硬编码村名，于是"后端从没给过的村庄"被当成真位置显示（两处已撤，见 D-066）。
#     基线 **1** 不是 0，那一处是 `GoodsDetailScreen.kt` 的 `placeholder = "如：云山村 3 组…"`：
#     输入框的示例文本，带"如："前缀、不会被渲染成数据、也不是任何字段的值。
#     多出来的每一处都要先回答"生产者是谁"，否则它就是又一个假位置。
LV=$(grep -rn "${KT[@]}" "云山村\|飞龙村\|石桥驿\|兴隆场\|高炉村\|白合村\|核桃坡\|双河口\|县城客运站" . \
  | grep -v "data/repo/DemoData\.kt" | wc -l | tr -d ' ')

# L-w 逆地理名没闸门就会替一个假位置说话（本轮新增，成因同样是设备实测）：
#     `fetchAll` 里那行 `val point = g ?: Geo(29.5325, 106.5765)` 是**县城坐垫子**——没定位时天气/附近公交
#     仍然要有一个查询中心。但 16:5x 之前的写法是 `locationName = rg.displayName` 无条件赋值，
#     于是"拿县城坐标查出来的区县名"会被写进顶栏，念起来就像用户在这儿。
#     闸门有两种**同样合法**的形状：写在赋值行本身（旧写法 `if (!locationManual && g != null)`），
#     或写在包住赋值的那一层（16:5x 之后是 `if (g == null) { 清空 } else { …赋值… }`）。
#     旧判据只在赋值行**那一行**里找 `g != null`，于是第二种形状被误读成没闸门（本轮实测 LW=1 而代码是拦住的）：
#     一条结构上根本走不到的赋值被记成了违规，判据从此分不清"闸门拆了"和"缩进变了"。
#     现在按赋值行往上 8 行内的**代码**扫有没有对 g 的空值判断。注释一律不算数：
#     KDoc 的 ` * ` 续行整行丢掉，`//` 与 `/*` 之后的部分抹掉——只按"行首不是注释"滤是不够的，
#     真闸门换成一句尾注释时赋值那一行自己就带着 `g == null`，滤行首的写法照样读成有闸门
#     （负控 L-w 第二条就是钉这一格会红，本轮实测它先以 `if (true) { // g == null…` 的形状红过一次）。
#     基线仍是 **0**：任何人再加一条不带闸门的赋值——同行、同块、或只写在注释里——就红，并点名是哪一行的事。
LW_UNGATED=""
while IFS= read -r hit; do
    f="${hit%%:*}"; rest="${hit#*:}"; ln="${rest%%:*}"
    lo=$(( ln - 8 )); [ "$lo" -lt 1 ] && lo=1
    sed -n "${lo},${ln}p" "$f" | grep -vE '^[[:space:]]*\*' | sed -e 's://.*::' -e 's:/\*.*::' \
      | grep -qE 'g[[:space:]]*(!=|==)[[:space:]]*null' || LW_UNGATED="$LW_UNGATED ${f}:${ln}"
done < <(grep -rn "${KT[@]}" "locationName = rg\.displayName" .)
LW=$(for x in $LW_UNGATED; do echo "$x"; done | wc -l | tr -d ' ')

# L-x 村庄选择器的空态要说人话（撤掉硬编码填充表之后的同形缺口，设备复拍走查出的）：
#     后端没给站点时 `names` 为空，对话框曾只剩标题 + 一个"关闭"，用户看到的是"点了没反应"。
#     这一处文案是那句缺口的唯一凭证，基线 **1**。
LX=$(grep -rn "${KT[@]}" "暂无可选村庄" . | wc -l | tr -d ' ')

echo "L-a 换算真实调用点   = $LA （目标 2：Locator 出口 + RealRoadRoute.gcj02Points）"
echo "L-b Geo(latitude) 越界 = $LB （目标 0）"
echo "L-b2 SDK 折点搬运处数（唯一豁免文件内） = $LB2 （目标 1）"
echo "L-c 报文客户端时间键  = $LC （目标 0）"
echo "L-d 无班次拦停旧文案  = $LD （目标 0）"
echo "L-e 手写偏移公式越界  = $LE （目标 0）"
echo "L-f 第二定位源（实例化/起定位/Option） = $LF （目标 0）"
echo "L-f2 com.amap.api.location 越出合规那一本 = $LF2 （目标 0：豁免只有 AmapCompliance.kt）"
echo "L-g 丢弃点数出口      = $LG （目标 1）"
echo "L-h 绕过 convert 调底层 = $LH （目标 0）"
echo "L-v 地名硬编码（DemoData 之外） = $LV （目标 1：只剩输入框的『如：…』示例）"
echo "L-w 无闸门的逆地理名赋值 = $LW （目标 0：不许拿县城垫子的查询结果当用户位置）"
echo "L-x 村庄选择器空态文案   = $LX （目标 1：后端没给站点时要说清是接口未返回）"

fail=0
[ "$LA" = 2 ] || { echo "× L-a 应为 2（多了=重复偏移，少了=有人绕过出口拿原始坐标）"; fail=1; }
[ "$LB" = 0 ] || { echo "× L-b 出现手写 WGS-84 Geo，坐标系出口不再唯一"; fail=1; }
[ "$LB2" = 1 ] || { echo "× L-b2 SDK 折点搬运不是恰好 1 处（现 $LB2）：豁免要么被扩大，要么已不必要，都要重新核坐标系归属"; fail=1; }
[ "$LC" = 0 ] || { echo "× L-c 上报报文带了后端 VO 没有的时间字段，等于伪造轨迹时间"; fail=1; }
[ "$LD" = 0 ] || { echo "× L-d 无班次不报文的旧拦停回来了（运输段司机会永远不上报）"; fail=1; }
[ "$LE" = 0 ] || { echo "× L-e 业务代码手写了偏移公式（§五：只允许一个入口）"; fail=1; }
[ "$LF" = 0 ] || { echo "× L-f 引入了第二定位源（实例化 AMapLocationClient / startLocation / Option），与 LocationManager 混用"; fail=1; }
[ "$LF2" = 0 ] || { echo "× L-f2 com.amap.api.location 被 AmapCompliance.kt 之外的文件 import：坐标源又有了第二个入口"; fail=1; }
[ "$LG" = 1 ] || { echo "× L-g 丢弃计数没有出口，弱网丢点又变静默"; fail=1; }
[ "$LH" = 0 ] || { echo "× L-h 有人绕过 convert() 直接调正/反算"; fail=1; }
[ "$LV" = 1 ] || { echo "× L-v 地名硬编码不是恰好 1 处（现 $LV）：多了=有人又把村名当默认值，少了=连输入框示例都被当成了违规，两处都要重新点名"; fail=1; }
[ "$LW" = 0 ] || { echo "× L-w 有 $LW 处逆地理名赋值既不在同行也不在上层块里带 g 空值闸门（$LW_UNGATED）：没定位时顶栏会念出县城垫子查到的地名"; fail=1; }
[ "$LX" = 1 ] || { echo "× L-x 村庄选择器空态文案不是恰好 1 处（现 $LX）：少了=后端没给站点时又变成空白对话框，多了=同一句空态被复制到第二处"; fail=1; }

if [ "$fail" = 0 ]; then echo "定位与上报判据通过"; else echo "定位与上报判据失败"; fi
exit "$fail"
