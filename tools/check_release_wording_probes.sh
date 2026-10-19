#!/usr/bin/env bash
# 更新公告与"发的包不许出现开发词"的静态闸（配 tools/release_wording_negative_controls.sh）。
#
# 用户口径（2026-10-19）：「更新弹窗要写上更新公告」+「这个 release 版本不允许出现开发专业词汇」。
# 两句话各自有一个最容易被写歪的方向：
#   RW-a 公告通路：公告的原件在仓库 `RELEASE_NOTES.md`，CI 按**本次 tag** 取那一节写进 Release 正文。
#        以前这里固定发一句"自动发布：本次运行 NNN"，于是 App 里那格"更新公告"永远没内容可读；
#        而"取不到就抄最近一节"会把上一版的公告顶到这一版上 —— 那是把没做过的事说成做了。
#   RW-b 两处弹窗：冷启动那一次与「设置 → 关于 → 检查更新」那一次必须读**同一个出口**，
#        否则两屏对同一条公告说两种话（本工程反复出现的那类"一个动作两种反馈"）。
#        空的要说"没写"，长的要说明截了多少，不许安静地 `.take(500)`。
#   RW-c 开发词：`decideUpdate` 里那几句 `Failed(...)` 是要上屏的，所以函数体里的字符串字面量
#        不许带 HTTP/接口/报文/APK/infocode/GitHub；结果码改进 logcat（`BUS_UPDATE`）。
#        ⚠ 这一格配了正对照（RC-POS）：同一个文件里那些词确实还在（常量名、注释、日志），
#          所以那个 0 是"字面量里没有"，不是"这个文件根本没这些字"。
#   RW-d 可执行反证：判据**真的跑一次**取公告脚本 —— 命中的那一版必须出正文、
#        没写的版本必须退 1 并落"没写公告"的占位、`v21` 不许命中 `v214`（词边界）。
#
# 用法：bash tools/check_release_wording_probes.sh
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
RN=RELEASE_NOTES.md
PICK=tools/release_notes_pick.py
YML=.github/workflows/build-and-release.yml
AU=$M/core/update/AppUpdate.kt
AUP=$M/core/update/AppUpdater.kt
UD=$M/core/update/UpdateDialog.kt
US=$M/ui/common/UpdateSection.kt

for f in "$RN" "$PICK" "$YML" "$AU" "$AUP" "$UD" "$US"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

# `-e` 是必需的：下面有几条模式本身以 `--` 开头（grep 会把它当成自己的选项）
cnt() { grep -c -e "$1" "$2" 2>/dev/null | tr -d ' '; }
one() { grep -c -E -e "$1" "$2" 2>/dev/null | tr -d ' '; }

# ---------- RW-a 公告通路 ----------
RA_SECT=$(one '^## v[0-9]' "$RN")
RA_PICK=$(cnt '^def main' "$PICK")
RA_TAKE=$(cnt 'release_notes_pick.py' "$YML")
RA_FILE=$(cnt '--notes-file .release-notes.md' "$YML")
RA_GUARD=$(cnt '-s .release-notes.md' "$YML")
RA_OLD=$(cnt '--notes "自动发布' "$YML")

# ---------- RW-b 两处弹窗同一个出口 ----------
RB_DEF=$(cnt '^internal fun announcementText(body: String): String' "$AU")
RB_MAX=$(cnt 'ANNOUNCEMENT_MAX = 1200' "$AU")
RB_USE=$(one 'announcementText\(' "$UD")
RB_USE2=$(one 'announcementText\(' "$US")
RB_LABEL=$(cnt '更新公告' "$UD")
RB_LABEL2=$(cnt '更新公告' "$US")
RB_CUT=$(one 'body\.take\([0-9]+\)' "$UD")
RB_CUT2=$(one 'body\.take\([0-9]+\)' "$US")

# ---------- RW-c 上屏那几句不含开发词 ----------
RC_BODY=$(sed -n '/^internal fun decideUpdate/,/^}/p' "$AU")
RC_BODY_LINES=$(printf '%s' "$RC_BODY" | wc -l | tr -d ' ')
RC_BAD=$(printf '%s' "$RC_BODY" | grep -oE '"[^"]*"' | grep -cE 'HTTP|接口|报文|APK|infocode|GitHub' | tr -d ' ')
RC_LOG=$(cnt 'BUS_UPDATE' "$AUP")
# 正对照：同一个文件里这些词确实还在（资源名常量），少了它 RC_BAD 的 0 就成了"这文件没这几个字"
RC_POS=$(cnt 'APK_ASSET_NAME' "$AU")

# ---------- RW-d 真跑一次取公告 ----------
NEWEST=$(grep -oE '^## v[0-9]+' "$RN" | head -1 | sed 's/^## //')
RD_HIT=0; RD_MISS=0; RD_NEAR=0
if [ -n "$NEWEST" ]; then
  python "$PICK" --tag "$NEWEST" --file "$RN" --out tmp/rw_hit.txt >/dev/null 2>&1
  [ $? = 0 ] && [ -s tmp/rw_hit.txt ] && ! grep -q '没有写更新公告' tmp/rw_hit.txt && RD_HIT=1
fi
python "$PICK" --tag v999999 --file "$RN" --out tmp/rw_miss.txt >/dev/null 2>&1
[ $? != 0 ] && grep -q '没有写更新公告' tmp/rw_miss.txt && RD_MISS=1
# 词边界：`v21` 不许命中 `v214`（命中了就等于把别的版本的公告发给这一版）
python "$PICK" --tag v21 --file "$RN" --out tmp/rw_near.txt >/dev/null 2>&1
[ $? != 0 ] && RD_NEAR=1
rm -f tmp/rw_hit.txt tmp/rw_miss.txt tmp/rw_near.txt

printf 'RW-a 小节/取脚本main/CI取数/CI正文/CI兜底/旧固定语 : %s/%s/%s/%s/%s/%s（目标 >=1/1/1/1/1/0）\n' \
  "$RA_SECT" "$RA_PICK" "$RA_TAKE" "$RA_FILE" "$RA_GUARD" "$RA_OLD"
printf 'RW-b 定义/上限/两处调用/两处标题/安静切刀 : %s/%s/%s+%s/%s+%s/%s+%s\n' \
  "$RB_DEF" "$RB_MAX" "$RB_USE" "$RB_USE2" "$RB_LABEL" "$RB_LABEL2" "$RB_CUT" "$RB_CUT2"
printf 'RW-c 函数体行数/带开发词的字面量/降级进日志/正对照 : %s/%s/%s/%s（目标 >=10/0/1/>=1）\n' \
  "$RC_BODY_LINES" "$RC_BAD" "$RC_LOG" "$RC_POS"
printf 'RW-d 命中/占位/词边界 : %s/%s/%s（目标 1/1/1）\n' "$RD_HIT" "$RD_MISS" "$RD_NEAR"

fail=0
[ "$RA_SECT" -ge 1 ] || { echo "× RW-a RELEASE_NOTES.md 里一节公告都没有（现 $RA_SECT）：公告通路没有源头，弹窗永远读不到话"; fail=1; }
[ "$RA_PICK" = 1 ] || { echo "× RW-a 取公告的脚本不像一份可执行脚本（main $RA_PICK）：CI 那一步会拿不到正文"; fail=1; }
[ "$RA_TAKE" = 1 ] || { echo "× RW-a CI 不再调取公告脚本（现 $RA_TAKE）：Release 正文回到写死那一句，App 里那格又空了"; fail=1; }
[ "$RA_FILE" = 1 ] || { echo "× RW-a CI 不再用 --notes-file 发正文（现 $RA_FILE）：取到的公告进不了 Release"; fail=1; }
[ "$RA_GUARD" = 1 ] || { echo "× RW-a CI 少了正文文件非空那道兜底（现 $RA_GUARD）：通路坏了时会发出一份正文缺失的 Release"; fail=1; }
[ "$RA_OLD" = 0 ] || { echo "× RW-a 那句写死的「自动发布：本次运行」回来了（现 $RA_OLD）：它占着公告那一格，谁都不会再往里写正文"; fail=1; }
[ "$RB_DEF" = 1 ] || { echo "× RW-b 公告口径不是恰好一份实现（现 $RB_DEF）：两处弹窗迟早对同一条公告说两种话"; fail=1; }
[ "$RB_MAX" = 1 ] || { echo "× RW-b 展示上限那一格被改没了或长出两份（现 $RB_MAX）：截断要有一个说得清的数字"; fail=1; }
{ [ "$RB_USE" = 1 ] && [ "$RB_USE2" = 1 ]; } || { echo "× RW-b 两处弹窗不再共用同一出口（$RB_USE/$RB_USE2）：同一件事两种反馈"; fail=1; }
{ [ "$RB_LABEL" -ge 1 ] && [ "$RB_LABEL2" -ge 1 ]; } || { echo "× RW-b 有一处弹窗不再标「更新公告」（$RB_LABEL/$RB_LABEL2）：用户不知道下面那段是什么"; fail=1; }
{ [ "$RB_CUT" = 0 ] && [ "$RB_CUT2" = 0 ]; } || { echo "× RW-b 又出现安静截断 body.take(N)（$RB_CUT/$RB_CUT2）：不说明截了多少，用户会以为那就是全部公告"; fail=1; }
[ "$RC_BODY_LINES" -ge 10 ] || { echo "× RW-c 没锚到 decideUpdate 函数体（现 $RC_BODY_LINES 行）：下面那个 0 是空的，不采信"; fail=1; }
[ "$RC_BAD" = 0 ] || { echo "× RW-c 上屏那几句里还有 $RC_BAD 处开发词（HTTP/接口/报文/APK/infocode/GitHub）：这是要发的包"; fail=1; }
[ "$RC_LOG" = 1 ] || { echo "× RW-c 检查更新失败不再写日志（现 $RC_LOG）：屏上撤了码，装机的人就没处可查了——撤的是措辞不是证据"; fail=1; }
[ "$RC_POS" -ge 1 ] || { echo "× RW-c 正对照没了（APK_ASSET_NAME 现 $RC_POS）：RC_BAD 的 0 失去意义，这一格不再能区分『改干净』与『锚点漂了』"; fail=1; }
[ "$RD_HIT" = 1 ] || { echo "× RW-d 用最新一节的 tag 真跑一次取公告没拿到正文（tag=${NEWEST:-无}）：CI 那一步会走占位分支"; fail=1; }
[ "$RD_MISS" = 1 ] || { echo "× RW-d 没写过公告的版本不再落『没写公告』的占位（现 $RD_MISS）：通路坏了会静默发一份空正文"; fail=1; }
[ "$RD_NEAR" = 1 ] || { echo "× RW-d 词边界没了：v21 命中了 v214 那一节（现 $RD_NEAR）——等于把别的版本的公告发给这一版"; fail=1; }

# ---------- RW-e 网络那一咽喉：上屏句不带开发词 ----------
# `errorTextOf` 把这里的 message 送到 20+ 屏的 ErrorState 上，所以这一族的字面量是全工程最贵的一处。
# ⚠ 认的是**旧串消失 + 新出口在岗**两对：只数"没有 HTTP"会把"整个错误通路被删了"也读成绿。
API=$M/core/net/Api.kt
[ -f "$API" ] || { echo "× 缺 $API：判据没有输入，本轮读数不采信"; exit 2; }
RE_OLD_JARGON=$(one '（HTTP |外部接口 |未配置后端地址|响应格式异常|请求失败（' "$API")
RE_RAW_MSG=$(cnt 'NetworkException(e.message' "$API")
RE_NETFAIL=$(one 'netFail\(' "$API")
RE_APIFAIL=$(one 'apiFail\(' "$API")
RE_LOG_SINK=$(cnt 'Log.w(TAG, "BUS_NET' "$API")
RE_KICK=$(one 'throw kick\(' "$API")

printf 'RW-e 旧开发词/原始异常文案/netFail/apiFail/日志出口/401 出口 : %s/%s/%s/%s/%s/%s（目标 0/0/14/3/>=2/>=2）\n' \
  "$RE_OLD_JARGON" "$RE_RAW_MSG" "$RE_NETFAIL" "$RE_APIFAIL" "$RE_LOG_SINK" "$RE_KICK"
[ "$RE_OLD_JARGON" = 0 ] || { echo "× RW-e Api 里还有 $RE_OLD_JARGON 处旧的开发词上屏句：这一族会漏到 20+ 屏的失败态上"; fail=1; }
[ "$RE_RAW_MSG" = 0 ] || { echo "× RW-e 有 $RE_RAW_MSG 处把传输层的原始文案直接当上屏话（OkHttp 那句带 IP/端口/毫秒）"; fail=1; }
# 14 / 3 是**本轮实测**（各含定义那一行：netFail 13 调用 + 1 定义，apiFail 2 调用 + 1 定义）。
# 第一版按"应该有几处"写成 15/5 —— 那是猜的，读数一出来就红给自己看（判据要量产物，不量愿望）。
[ "$RE_NETFAIL" = 14 ] || { echo "× RW-e 走 netFail 出口的不再是 14 处（现 $RE_NETFAIL）：变了要连这一格与理由一起改，别只改代码"; fail=1; }
[ "$RE_APIFAIL" = 3 ] || { echo "× RW-e 走 apiFail 出口的不再是 3 处（现 $RE_APIFAIL）：与上一条同族，日志那一半会漏"; fail=1; }
# 钉**恰好两颗**而不是"≥1"：负控 RW-9 撤掉的就是其中一颗，用 ≥1 它照样绿（本轮实测咬不住）。
[ "$RE_LOG_SINK" = 2 ] || { echo "× RW-e 网络失败写日志的出口不是那两颗（现 $RE_LOG_SINK）：屏上撤了码，装机的人就没处可查了"; fail=1; }
[ "$RE_KICK" -ge 2 ] || { echo "× RW-e 401 那两条出口没了（现 $RE_KICK）：上面那几个 0 就成了『整条错误通路被删』，不是『改干净』"; fail=1; }

if [ "$fail" = 0 ]; then
  echo "更新公告与发布文案判据通过（$RA_SECT 节公告 / 两处弹窗同一出口 / 上屏几句零开发词 / 取公告真跑 $RD_HIT$RD_MISS$RD_NEAR / 网络咽喉 $RE_NETFAIL+$RE_APIFAIL 处走双出口）"
  exit 0
fi
exit 1
