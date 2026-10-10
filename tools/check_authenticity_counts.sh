#!/usr/bin/env bash
# 真实性关键字复测（"常驻自检"表 1/2/3 行的当轮数字）。用法：bash tools/check_authenticity_counts.sh
# 教训沿用：**不要用 eval 拼 pattern** —— eval 会把 `haversine(...)` 当命令执行，
# 报错方式是 "command not found" + 输出 0 行，看起来像"违规已归零"。
set -u
cd "${BUSPOST_ANDROID:-$(pwd)}"/app/src/main/java || exit 2
WL=(-e "core/map/(RealRoadRouteValidator|CoordinateSystem|RouteSource)\.kt"
    -e "NavIcon\.STRAIGHT|enum class NavIcon" -e "isFakeBoldText"
    -e "MockWebServer" -e "WeatherCard\.kt" -e "DemoData\.kt"
    -e "Repo\.kt:.*(mock 标注|适合当日运输)"
    -e "Models\.kt:[0-9]+: *(//|\*|/\*)"
    -e "Models\.kt:.*(Haversine 直线距离|mock|直线估算|不是道路里程|路网不可用)")
N=$(grep -rniE "euclidean|haversine|straight|mock|fake|demo stepping" . | grep -vE "${WL[@]}" | wc -l | tr -d ' ')
W=$(grep -rniE "euclidean|haversine|straight|mock|fake|demo stepping|stepping|two.?point|连线|直线" . | grep -vE "${WL[@]}" | wc -l | tr -d ' ')
G=$(grep -rn "GeoMath" . | wc -l | tr -d ' ')
D=$(grep -rln "DemoData" . | wc -l | tr -d ' ')
# S（§24 假步进）：#51 起口径从"只看 DriverNavScreen.kt 一个文件"抬成**整个司机 UI 目录**，
# 否则把定时器挪到隔壁文件就"归零"了；含注释行的裸计数，目标 0。
S=$(grep -rn "stepping\|delay(4_000\|stepIndex + 1" com/shanxiang/buspost/ui/driver --include=*.kt | wc -l | tr -d ' ')
echo "窄口径=$N  宽口径=$W  GeoMath=$G  DemoData文件=$D  假步进=$S"
echo "--- 宽口径按文件构成 ---"
grep -rniE "euclidean|haversine|straight|mock|fake|demo stepping|stepping|two.?point|连线|直线" . \
  | grep -vE "${WL[@]}" | sed -e 's#:.*##' -e 's#.*/##' | sort | uniq -c | sort -rn
