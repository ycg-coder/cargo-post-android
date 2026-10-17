#!/usr/bin/env bash
# 天气提醒消息的契约判据（D-158，配 tools/weather_alert_negative_controls.sh）。
#
# 管六件事：
#   WA-a 天气→建议这张表**只有一张**（推送不许再抄一份 when）；
#   WA-b 推送真的按当前角色取那一份说法（参数在定义里、也在调用处传了）；
#   WA-c 天气推送仍然**不带 route**（角色裁决归 startDestination，与 SR-g / D-100 同源）；
#   WA-d 现象词表在通知与卡片两侧是同一批（浮尘/冰雹不许一边响一边不响）；
#   WA-e 提醒水位重置口有调用点（长期 0 处 = 换账号后新账号收不到提醒）；
#   WA-f 契约用例条数（下限等于现读）。
#
# 写法沿用本仓规矩：按内容过滤不按行号；每个「目标 0」配同文件正对照；grep 一律过注释这一道。
# 用法：bash tools/check_weather_alert_probes.sh        退出码非 0 = 这一格被拆了
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
AE=$M/notify/AlertEngine.kt
RK=$M/data/repo/Repo.kt
CT=app/src/test/java/com/shanxiang/buspost/notify/WeatherAlertContractTest.kt
NC='^[0-9]+:[[:space:]]*(\*|//|/\*)'
# grep -rn 的输出前缀是 `path:NN:`，与 grep -n 的 `NN:` 不一样，两种都要滤
CMT='(^[^:]+:[0-9]+:[[:space:]]*(//|\*|/\*))|(^[0-9]+:[[:space:]]*(//|\*|/\*))'

for f in "$AE" "$RK" "$CT"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

cnt() { grep -n "$2" "$1" | grep -cvE "$NC" || true; }
# 从「含 $2 的那一行」往下数 $3 行里匹配 $4 的行数；找不到锚点行就返回 -1（调用方必须把它当红）
window() {
  local line
  line=$(grep -n "$2" "$1" | head -1 | cut -d: -f1)
  [ -z "$line" ] && { echo "-1"; return; }
  awk -v s="$line" -v w="$3" 'NR>s && NR<=s+w' "$1" | grep -c "$4" || true
}

# WA-a 只有一张表：推送引用天气卡那份，且旧那份字面量在 notify/ 里归零
AA_REF=$(cnt "$AE" 'weatherTipsFor(')
AA_OLD=$(grep -rn '出行请带雨具\|路面可能湿滑\|请关注天气变化\|注意防寒保暖' "$M/notify" --include=*.kt | grep -vE "$CMT" | wc -l | tr -d ' ')
AA_PROOF=$(grep -rn '出行前留意山区路况' "$M/data/repo" --include=*.kt | wc -l | tr -d ' ')

# WA-b 角色真的分了两份
AB_PARAM=$(cnt "$AE" 'domain: AppDomain')
AB_ARG=$(cnt "$AE" 'AppState\.activeDomain\.value,')

# WA-c 天气那一推送不带 route（窗口法：从 Channels.WEATHER 那行数起 12 行）
AC_ROUTE=$(window "$AE" 'Channels\.WEATHER,' 12 'route =')
AC_PUSH=$(window "$AE" 'Channels\.WEATHER,' 12 'body,')

# WA-d 现象词两侧同一批
AD_DUST_AE=$(cnt "$AE" '"尘"')
AD_DUST_RK=$(cnt "$RK" '"尘"')
AD_HAIL_AE=$(cnt "$AE" '"冰雹"')
AD_HAIL_RK=$(cnt "$RK" '"冰雹"')

# WA-e 水位重置口有调用点（定义那处不算）
AE_CALL=$(grep -rn 'resetWatermark' "$M" --include=*.kt | grep -v 'notify/AlertEngine.kt' | grep -vE "$NC" | wc -l | tr -d ' ')

# WA-f 用例条数
AF=$(grep -c '^    @Test' "$CT")

fail=0

[ "$AA_REF" = 1 ] || { echo "× WA-a 推送引用天气卡建议的地方不是恰好一处（现 $AA_REF）：多一处就是又起了第二张表"; fail=1; }
[ "$AA_OLD" = 0 ] || { echo "× WA-a 通知里残留第二张天气→建议表（现 $AA_OLD 处旧字面量，正对照 $AA_PROOF）：两处一漂移，同一场雨会说两句不同的话"; fail=1; }
[ "$AA_PROOF" -ge 1 ] || { echo "× WA-a 反证失效：扫描在 data/repo 里也数不出天气文案（现 $AA_PROOF），上面那个 0 不采信"; fail=1; }
{ [ "$AB_PARAM" = 1 ] && [ "$AB_ARG" = 1 ]; } || { echo "× WA-b 角色这一格断了（定义 $AB_PARAM / 调用传参 $AB_ARG，目标 1/1）：推送会退回一份正文喊两种人"; fail=1; }
[ "$AC_ROUTE" = 0 ] || { echo "× WA-c 天气推送开始带 route 了（现 $AC_ROUTE，-1=锚点行没找到）：通知不该替角色决定进哪个壳（SR-g / D-100 同源）"; fail=1; }
[ "$AC_PUSH" -ge 1 ] || { echo "× WA-c 反证失效：窗口里连推送正文都没扫到（现 $AC_PUSH），上面那个 0 不采信"; fail=1; }
{ [ "$AD_DUST_AE" -ge 1 ] && [ "$AD_DUST_RK" -ge 1 ]; } || { echo "× WA-d 「尘」只在单边（通知 $AD_DUST_AE / 卡片 $AD_DUST_RK）：浮尘会一边响一边不响"; fail=1; }
{ [ "$AD_HAIL_AE" -ge 1 ] && [ "$AD_HAIL_RK" -ge 1 ]; } || { echo "× WA-d 「冰雹」只在单边（通知 $AD_HAIL_AE / 卡片 $AD_HAIL_RK）：强对流会「通知拉警报、卡片说普通话」"; fail=1; }
[ "$AE_CALL" = 1 ] || { echo "× WA-e resetWatermark 的调用点不是 1（现 $AE_CALL）：0 = 换账号后新账号收不到提醒，>1 = 别处也在重置水位（同一个问题两个答案）"; fail=1; }
[ "$AF" = 7 ] || { echo "× WA-f 契约用例不是 7 条（现 $AF）：下限等于现读，改了要连同本行一起改"; fail=1; }

if [ "$fail" = 0 ]; then
  echo "天气提醒判据通过（一张表 $AA_REF / 角色 $AB_PARAM+$AB_ARG / 无 route $AC_ROUTE / 尘 $AD_DUST_AE-$AD_DUST_RK / 水位 $AE_CALL / 用例 $AF）"
else
  echo "天气提醒判据失败"
fi
exit "$fail"
