#!/usr/bin/env bash
# #42 天气分角色**结构判据**的负控制（配 tools/check_weather_probes.sh）
# 用法：bash tools/weather_negative_controls.sh      每条破口都要求判据簿红，全绿才通过
#
# 为什么要这本：#42 是**收紧型**改动（把一份文案拆成两份，并把司机端那一栏接上）。
# 只断言"允许侧"的用例钉不住它——所以每一条破口都要能让具名判据红，
# 尤其是 W-c / W-h 那两个 0 命中项（0 命中必须是"真的没有"，不能是"grep 写错了"）。
#
# ⚠ 会就地临时改源码并在结束时原样恢复；一次只允许跑一个（negctl_lock）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2

REPO=app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt
WB=app/src/main/java/com/shanxiang/buspost/ui/driver/WorkbenchScreen.kt
WC=app/src/main/java/com/shanxiang/buspost/ui/common/WeatherCard.kt
HOME=app/src/main/java/com/shanxiang/buspost/ui/user/HomeScreen.kt
PROBE=tools/check_weather_probes.sh
BAK=/tmp/weather_negctl.bak.$$

for f in "$REPO" "$WB" "$WC" "$HOME"; do
  [ -f "$f" ] || { echo "✘ 缺 $f，本书作废"; exit 2; }
done
[ -f "$PROBE" ] || { echo "✘ 缺 $PROBE，本书作废"; exit 2; }

cp "$REPO" "$BAK.repo"; cp "$WB" "$BAK.wb"; cp "$WC" "$BAK.wc"; cp "$HOME" "$BAK.home"
restore() { cp "$BAK.repo" "$REPO"; cp "$BAK.wb" "$WB"; cp "$BAK.wc" "$WC"; cp "$BAK.home" "$HOME"; }
trap 'restore; rm -f "$BAK.repo" "$BAK.wb" "$BAK.wc" "$BAK.home"; negctl_release' EXIT

# 预检：判据簿起跑必须绿，否则下面每一条"红"都分不清是破口还是基线本身坏了
bash "$PROBE" >/dev/null 2>&1 || { echo "✘ 预检：$PROBE 起跑就不绿，基线不成立，不注入破口"; exit 5; }
echo "[预检] 判据簿起跑绿"

ok=0; bad=0

# 注入器：按 python 精确替换（次数不恰好 1 就记异常，不做"大概改上了"的乐观注入）
inject() { # inject <file> <old> <new>
  python - "$1" "$2" "$3" <<'PY'
import sys
p, a, b = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(a) != 1:
    sys.exit(9)
open(p, 'w', encoding='utf-8', newline='').write(s.replace(a, b))
PY
}

check() { # check <编号> <期望红的判据行> <文件> <old> <new>
  local id="$1" want="$2" file="$3" old="$4" new="$5"
  restore
  if ! inject "$file" "$old" "$new"; then
    echo "✘ ${id} 锚点没命中（不是恰好 1 次），本次未注入 ⇒ 判据未被验证"
    bad=$((bad + 1)); return
  fi
  local out rc
  out=$(bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = 0 ]; then
    echo "✘ ${id} 判据仍然绿（破口没咬住）"; bad=$((bad + 1))
  elif grep -q "$want" <<<"$out"; then
    echo "✔ ${id} 红且原因对得上（$want）"; ok=$((ok + 1))
  else
    echo "✘ ${id} 红了但不是那一条（缺 $want）"; bad=$((bad + 1))
  fi
}

# WS-1 旧的单份货运文案回潮（重新加一个不分角色的 helper） → W-c
check WS-1 "× W-c" "$REPO" \
  '    private fun weatherText(code: Int): String = when (code) {' \
  '    private fun farmTips(condition: String, temp: Int): String = weatherTipsFor(condition, temp).first

    private fun weatherText(code: Int): String = when (code) {'

# WS-2 司机那份只填了一个生产者（open-meteo 那一路漏） → W-b2
check WS-2 "× W-b" "$REPO" \
  '            tips = userTip,
            driverTips = driverTip,
            forecast = forecast,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }' \
  '            tips = userTip,
            forecast = forecast,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }'

# WS-3 司机端那一栏从工作台上摘掉（整行注释 = 代码上这一栏没了） → W-d1/W-d2
# ⚠ 这一条同时是给判据簿自己上的负控：W-d 早期写成 `grep -c`，不过滤注释行，注入后判据仍绿（实测）。
#     现在 W-d 按"注释行"过滤，摘掉那一栏必须红。
check WS-3 "× W-d" "$WB" \
  '                    item(key = "weather") { DriverWeatherNotice(driverWx, driverWxLine) }' \
  '                    // item(key = "weather") { DriverWeatherNotice(driverWx, driverWxLine) }'

# WS-4 显示没走 lineOf：直接读字段，mock 空态会被显示出来 → W-f
check WS-4 "× W-f" "$WB" \
  '                val driverWxLine = DriverWeatherTip.lineOf(driverWx)' \
  '                val driverWxLine = driverWx?.driverTips.orEmpty()'

# WS-5 闸门从回读里摘掉（机制没上岗） → W-e
check WS-5 "× W-e" "$WB" \
  '        if (wxGeo != null && DriverWeatherTip.shouldFetch(weatherAtElapsed, nowMs)) {' \
  '        if (wxGeo != null && nowMs > 0L) {'

# WS-6 页面里写第二个 15 分钟 → W-g
check WS-6 "× W-g" "$WB" \
  'private fun callDriver(context: Context, mobile: String) {' \
  'private val driverWeatherEveryMs = 15 * 60 * 1000L

private fun callDriver(context: Context, mobile: String) {'

# WS-7 用户端首页卡改读司机那份（两屏说同一句话） → W-h
check WS-7 "× W-h" "$WC" \
  '                        weather.tips,' \
  '                        weather.driverTips,'

restore
# W-k（D-173 限流当天的来源可见性）三条
check WK-1 "× W-k" "$REPO" \
  'return null to amapWeatherFailureOf(live)' \
  'return null'
check WK-2 "× W-k" "$REPO" \
  'internal fun amapWeatherFailureOf(live: JSONObject): String =' \
  'internal fun amapWeatherFailureOfRenamed(live: JSONObject): String ='
check WK-3 "× W-k" "$WC" \
  'if (weather.source.isEmpty()) null else "来源 ${weather.source}",' \
  'if (false) null else "",'

# W-l（D-173 第二格：逆地理为什么没给区域码）四条
# WL-1 高德逆地理限流那一支退回裸 EMPTY：原因在 return 那一刻被丢掉 → L2/L3
check WL-1 "× W-l" "$REPO" \
  '                return ReverseGeo.EMPTY.copy(failure = amapInfocodeText("高德逆地理", root.optString("infocode")))' \
  '                return ReverseGeo.EMPTY'
# WL-2 同一个 infocode 出现第二份说法 → L1
check WL-2 "× W-l" "$REPO" \
  '    internal fun amapInfocodeText(service: String, code: String): String = when (code) {' \
  '    internal fun amapInfocodeTextAlt(service: String, code: String): String = "$service $code"

    internal fun amapInfocodeText(service: String, code: String): String = when (code) {'
# WL-3 首页那一个天气调用点不再透传原因 → L4
check WL-3 "× W-l" "$HOME" \
  'val w = Repo.weather(g, rg.adcode, rg.failure)' \
  'val w = Repo.weather(g, rg.adcode)'
# WL-4 "空区域码的两种成因"分话被绕过，服务商的原因直接顶掉中性那句 → L5
check WL-4 "× W-l" "$REPO" \
  'amapWeather(adcode, noAdcodeReasonOf(noAdcodeReason))' \
  'amapWeather(adcode, noAdcodeReason)'

# ⚠ 复查前必须先 restore：check() 是在**开头**还原的，最后一条注入会一直留在工作树里，
#   不先还原就判"源码未回到原样"——那是本书自己的假失败（D-173 加第三条负控当天撞上）。
restore
bash "$PROBE" >/dev/null 2>&1 && echo "[恢复] 判据簿重新通过" || { echo "✘ [恢复] 判据簿仍红，源码未回到原样"; bad=$((bad + 1)); }
cmp -s "$BAK.repo" "$REPO" && cmp -s "$BAK.wb" "$WB" && cmp -s "$BAK.wc" "$WC" && cmp -s "$BAK.home" "$HOME" \
  && echo "[恢复] 四份文件与原样一致" || { echo "✘ [恢复] 与原样不一致"; bad=$((bad + 1)); }

echo "天气结构负控：通过 $ok / 失败 $bad"
[ "$bad" = 0 ] && [ "$ok" = 14 ]
