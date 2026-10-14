#!/usr/bin/env bash
# 真实性关键字复测（"常驻自检"表 1/2/3 行的当轮数字）。用法：bash tools/check_authenticity_counts.sh
# 本簿前面几行是**读数**（不判、恒 0 退出），最后一格 AF 是**闸**（非 0 即 exit 1）—— 两类同住一本，
# 引用时要分开抄：把整本的 rc=0 读成"所有口径都过了"会把读数当判决。
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
# AF（虚构内容面，目标 0）：2026-10-14 实测三面 —— 后端 App 面 controller 逐条列举**零新闻端点**
# （全仓 -il "news" 唯一命中是 DriverAppServiceImpl 里的 newStatus，字符串假阳性）、
# 小程序树 0 该功能、App 是唯一生产者 ⇒ 首页那 4 条"每日新闻"没有任何真相源，
# 却署真实媒体名、按当天日期排版。撤掉后这一格钉住它不许重新长回来。
# 换媒体名或换函数名都算重新长回来，所以两头都数：符号面 + 具名面。
# ⚠ 后端真上线新闻接口时，本格的释放条件是"页面读的是真实端点"，届时把具名面改成只数硬编码字面量。
AF=$(grep -rn "Repo\.news\|NewsItem\|NewsCard\|每日新闻\|新华社\|人民日报\|央视新闻\|农民日报\|人民网\|中新网\|澎湃新闻" . | wc -l | tr -d ' ')
echo "窄口径=$N  宽口径=$W  GeoMath=$G  DemoData文件=$D  假步进=$S  虚构内容面=$AF"
if [ "$AF" != 0 ]; then
    echo "× AF 虚构内容面不为 0（现 $AF）：以下内容没有任何生产者"
    grep -rn "Repo\.news\|NewsItem\|NewsCard\|每日新闻\|新华社\|人民日报\|央视新闻\|农民日报\|人民网\|中新网\|澎湃新闻" .
    exit 1
fi
echo "--- 宽口径按文件构成 ---"
grep -rniE "euclidean|haversine|straight|mock|fake|demo stepping|stepping|two.?point|连线|直线" . \
  | grep -vE "${WL[@]}" | sed -e 's#:.*##' -e 's#.*/##' | sort | uniq -c | sort -rn
