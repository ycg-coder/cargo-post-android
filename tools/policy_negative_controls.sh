#!/usr/bin/env bash
# #49 政策正文页**结构判据**的负控制（配 tools/check_policy_probes.sh）
# 用法：bash tools/policy_negative_controls.sh
# 为什么这本必须有：PL-c / PL-f / PL-g / PL-h 里有四个"目标 0"——
# 0 命中必须是"真的没有"，不能是 grep 写错了；每个都要人为造一次破口证明它会红。
# ⚠ 会就地临时改源码并在结束时原样恢复；一次只允许跑一个（negctl_lock）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2

M=app/src/main/java/com/shanxiang/buspost
RT=$M/nav/Routes.kt
BR=$M/ui/BusPostRoot.kt
LS=$M/ui/login/LoginScreen.kt
MP=$M/ui/user/MinePages.kt
PS=$M/ui/user/PolicyScreen.kt
AP=$M/core/about/AppPolicies.kt
PROBE=tools/check_policy_probes.sh
FILES=("$RT" "$BR" "$LS" "$MP" "$PS" "$AP")
BAKS=()
for f in "${FILES[@]}"; do
  [ -f "$f" ] || { echo "✘ 缺 $f，本书作废"; exit 2; }
  b="/tmp/pol_neg.$(basename "$f").$$"; cp "$f" "$b"; BAKS+=("$b")
done
restore() { for i in "${!FILES[@]}"; do cp "${BAKS[$i]}" "${FILES[$i]}"; done; }
trap 'restore; for b in "${BAKS[@]}"; do rm -f "$b"; done; negctl_release' EXIT

bash "$PROBE" >/dev/null 2>&1 || { echo "✘ 预检：$PROBE 起跑就不绿，一条破口都不注入"; exit 5; }
echo "[预检] 判据簿起跑绿"
ok=0; bad=0

inject() {
  python3 - "$1" "$2" "$3" <<'INJPY'
import sys
p, a, b = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(a) != 1:
    sys.exit(9)
open(p, 'w', encoding='utf-8', newline='').write(s.replace(a, b, 1))
INJPY
}

check() {
  local id="$1" want="$2"; shift 2
  restore
  if ! inject "$@"; then
    echo "✘ ${id} 锚点没命中（不是恰好 1 次），本次未注入 ⇒ 判据未被验证"; bad=$((bad + 1)); return
  fi
  local out rc
  out=$(bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = 0 ]; then echo "✘ ${id} 判据仍然绿（破口没咬住）"; bad=$((bad + 1))
  elif grep -qF "$want" <<<"$out"; then echo "✔ ${id} 红且原因对得上（$want）"; ok=$((ok + 1))
  else echo "✘ ${id} 红了但不是那一条（缺 $want）"; bad=$((bad + 1)); fi
}

# 1 路由常量重名/被删 → PL-a（删掉 TERMS 常量这一行）
check P-1 "× PL-a" "$RT" '    const val TERMS = "terms"' ''
# 2 只注册一条路由 → PL-b
check P-2 "× PL-b 注册点不是两条" "$BR" \
  '        composable(Routes.TERMS) { com.shanxiang.buspost.ui.common.BusRouteBody { PolicyScreen(PolicyKind.TERMS) } }' ''
# 3 注册了但没走 BusRouteBody → PL-b2（键盘遮挡/边到端口径掉队）
check P-3 "× PL-b 有注册没走 BusRouteBody" "$BR" \
  '        composable(Routes.PRIVACY) { com.shanxiang.buspost.ui.common.BusRouteBody { PolicyScreen(PolicyKind.PRIVACY) } }' \
  '        composable(Routes.PRIVACY) { PolicyScreen(PolicyKind.PRIVACY) }'
# 4 给协议页加角色闸门 → PL-c（用户在同意之前就该看正文，这一条必须红）
check P-4 "× PL-c 有人给协议页加了角色" "$RT" \
  '    val DRIVER_ONLY = setOf(DRIVER_NAV, DRIVER_GUIDE, DRIVER_MESSAGES, HANDOVER, DRIVER_EXCEPTION)' \
  '    val DRIVER_ONLY = setOf(DRIVER_NAV, DRIVER_GUIDE, DRIVER_MESSAGES, HANDOVER, DRIVER_EXCEPTION, PRIVACY, TERMS)'
# 4b 反证自身：把 DRIVER_ONLY 定义整行删掉 → PLC_PROOF=0，判据必须报"反证掉了"而不是"闸门没加"
check P-4b "× PL-c 反证掉了" "$RT" '    val DRIVER_ONLY = setOf(' '    val DRIVER_ONLYX = setOf('
# 5 登录页《隐私政策》链接被并回勾选（点了只切勾选、看不到正文）→ PL-d
check P-5 "× PL-d 登录页链接数不是 3" "$LS" \
  '                    PolicyLink("《隐私政策》", Routes.PRIVACY)' ''
# 6 关于页入口撤掉 → PL-e
check P-6 "× PL-e 关于页入口不是两条" "$MP" \
  '                        onClick = { nav.navigate(Routes.PRIVACY) },' \
  '                        onClick = { },'
# 7 正文被抄进页面（两处各自漂移的开端）→ PL-h
# ⚠ 2026-10-14：注入的那句原来不带句末标点，而 PL-h 的"一句正文"已从"≥12 个连续汉字"换成
#   "字面量里有句末标点（。！？）"——汉字区间与长度类写法都依赖 locale（`LC_ALL=C` 按字节、
#   `C.UTF-8` 按字符，runner 上汉字区间数出 0 却念成"正文被删空"），只有标点字面量两边同一个数。
#   ⇒ 破口按新定义补上句号；否则不是判据松了，是这条注入不再属于它声称要抓的那一类。
#   确实窄了一格：**抄一句不带句末标点的话**现在数不到，换来的是"CI 与本机同一个数"。
check P-7 "× PL-h 页面里出现了" "$PS" \
  '        BackTopBar(title = doc.title, subtitle = doc.subtitle)' \
  '        BackTopBar(title = doc.title, subtitle = "本应用不会向第三方出售你的个人信息，详见下方说明。")'
# 7b 反证自身：从数据源删掉一句正文（那句带句号，PROSE 少 1）→ PLH_PROOF 变 42 ≠ 恰好 43
#     第一版这里配的是"≥20 下限"，实测删一句后判据仍绿（24≥20）⇒ 那个"页面 0 命中"没人能证明不是空跑；
#     下限型反证只能证"机制数得动"，当不了逐条改动的 tripwire，所以把反证侧改成精确数。
check P-7b "× PL-h 反证掉了" "$AP" \
  '                    "手机号与短信验证码：用来建立账号与会话；验证码由平台后端下发与校验。",' \
  '                    "手机号与验证码",'
# 8 免责句被抄成第二份 → PL-f
check P-8 "× PL-f 免责句出现" "$LS" \
  'private fun PolicyLink(name: String, route: String) {' \
  'private fun PolicyLinkDup(name: String, route: String) = "说明：以上是本应用的功能级服务与数据说明，不构成法律意见" // 复制了一份'
# 9 服务边界句被抄到司机界面（同一条规则两处各自维护）→ PL-g
check P-9 "× PL-g 服务边界句出现" "$MP" \
  '            SectionTitle("关于")' \
  '            SectionTitle("关于") // 不提供网上预约乘车'

restore
bash "$PROBE" >/dev/null 2>&1 && echo "[恢复] 判据簿重新通过" || { echo "✘ [恢复] 判据簿仍红"; bad=$((bad + 1)); }
same=1
for i in "${!FILES[@]}"; do
  cmp -s "${BAKS[$i]}" "${FILES[$i]}" || { echo "✘ [恢复] ${FILES[$i]} 与原样不一致"; same=0; }
done
[ "$same" = 1 ] && echo "[恢复] 全部文件与原样一致" || bad=$((bad + 1))

# 11 = P-1…P-9 加两条反证自身（P-4b、P-7b）。第一版这里写死 10：
# 后来把 P-7b 的判据从"≥20 下限"换成"恰好 25"、它才第一次真被咬住，数目就变了没人核对 ⇒ 期望值必须逐条数出来。
echo "政策正文页结构负控：通过 $ok / 失败 $bad"
[ "$bad" = 0 ] && [ "$ok" = 11 ]
