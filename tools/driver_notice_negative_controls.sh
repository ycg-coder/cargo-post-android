#!/usr/bin/env bash
# 司机端「平台公告」结构判据的负控制（配 tools/check_driver_notice_probes.sh）
# 用法：bash tools/driver_notice_negative_controls.sh
# 为什么这本必须有：DN-c / DN-d / DN-f / DN-g 有四个"目标 0"——
# 0 命中必须是"真的没有"，不能是 grep 写错了或者被反证一起抹掉；每个都要人为造一次破口证明它会红。
# ⚠ 会就地临时改源码并在结束时原样恢复；一次只允许跑一个（negctl_lock）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2

M=app/src/main/java/com/shanxiang/buspost
WB=$M/ui/driver/WorkbenchScreen.kt
DN=$M/ui/driver/DriverNotices.kt
HS=$M/ui/user/HomeScreen.kt
CT=app/src/test/java/com/shanxiang/buspost/ui/driver/DriverNoticesContractTest.kt
PROBE=tools/check_driver_notice_probes.sh
FILES=("$WB" "$DN" "$HS" "$CT")
BAKS=()
for f in "${FILES[@]}"; do
  [ -f "$f" ] || { echo "✘ 缺 $f，本书作废"; exit 2; }
  b="/tmp/dn_neg.$(basename "$f").$$"; cp "$f" "$b"; BAKS+=("$b")
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

# 1 工作台压根不取数（这一格又变回死的）→ DN-a
check DN-1 "× DN-a 工作台的 Repo.notices() 不是恰好一次" "$WB" \
  '        if (withNotices) platformNotices = Repo.notices().toState()' ''
# 2 页面绕过判定层自己造结论 → DN-b
check DN-2 "× DN-b 页面没调用 noticeBanner" "$WB" \
  '    val notice = noticeBanner(vm.platformNotices)' \
  '    val notice = NoticeBanner(true, false, "公告", "平台公告", "", false, emptyList())'
# 3 失败被写成"暂无公告"（本项目最经典的那类洗白）→ DN-c
check DN-3 "× DN-c 司机端出现了「暂无公告」" "$DN" \
  '            title = "平台公告没取到",' \
  '            title = "暂无公告",'
# 4 页面里多出一份措辞（两处各自漂移的开端）→ DN-d
check DN-4 "× DN-d 页面自己拼了" "$WB" \
  '                    item(key = "notice") {
' \
  '                    item(key = "notice") {
                        Text("平台公告", color = t.ink)
'
# 5 公告失败并进取消息之间那条整页 error → DN-f
check DN-5 "× DN-f 公告被并进整页 error" "$WB" \
  '        error = listOfNotNull(p.error, s.error, k.error, t.error, g.error).firstOrNull()' \
  '        error = listOfNotNull(p.error, s.error, k.error, t.error, g.error, platformNotices.error).firstOrNull()'
# 6 初值从"还没结论"改成"成功且为空"（开局就替后端说没有公告）→ DN-g
check DN-6 "× DN-g 公告初值不再是「还没结论」" "$WB" \
  '    var platformNotices by mutableStateOf(RemoteState<List<Notice>>(loading = true))' \
  '    var platformNotices by mutableStateOf(RemoteState<List<Notice>>(data = emptyList(), success = true))'
# 7 点开只剩第一条，其余公告被藏掉 → DN-b（正文列表那一环）
check DN-7 "× DN-b 正文列表没被渲染" "$WB" \
  '                    noticeDetailLines(notice.notices).forEach { (head, body) ->' \
  '                    noticeDetailLines(notice.notices.take(1)).forEach { (head, body) ->'
# 8 反证自身：失败措辞整套删掉 → DNC_PROOF 掉出 2，那个 0 就不再可信
check DN-8 "× DN-c 反证掉了" "$DN" \
  'private const val FAILED_TAG = "没取到"' \
  'private const val FAILED_TAG = "取数中断"'
# 9 反证自身：措辞被拆开拼接（grep 数不到，但屏幕上还是那句话）→ DND_PROOF 掉出 2
check DN-9 "× DN-d 反证掉了" "$DN" \
  '        title = "平台公告 ${list.size} 条",' \
  '        title = "平台" + "公告 ${list.size} 条",'
# 10 「演示公告」标签被抄进页面（第二套说法）→ DN-e
check DN-10 "× DN-e 「演示公告」出处=2" "$WB" \
  '            BusTag(banner.tag, tone = if (banner.failed) BusTone.DANGER else BusTone.CLAY)' \
  '            BusTag(banner.tag, tone = if (banner.failed) BusTone.DANGER else BusTone.CLAY)
            BusTag("演示公告")'
# 11 契约用例被摘掉一条 → DN-h
check DN-11 "× DN-h 契约用例少于 9 条" "$CT" \
  '    @Test
    fun `失败时不报条数`() {' \
  '    fun `失败时不报条数`() {'

# 12 作业动作后的回读也去取公告（点一次作业多一条 REST）→ DN-i
check DN-12 "× DN-i 作业动作后那一轮不再取公告的表态没了" "$WB"   '        syncFromServer(withNotices = false)'   '        syncFromServer(withNotices = true)'
# 13 取数被写成无条件（withNotices 闸门形同虚设）→ DN-i
check DN-13 "× DN-i 取数没被 withNotices 圈住" "$WB"   '        if (withNotices) platformNotices = Repo.notices().toState()'   '        platformNotices = Repo.notices().toState()'

# ---- DN-14…DN-18：用户侧公告正文这一格（缺陷 #44，判据 DN-j）----
# 14 点轮播条又把人丢进读另一张表的消息中心 → DN-j 接线
check DN-14 "× DN-j 首页轮播不再把整条公告交给阅读框" "$HS" \
  'NoticeCarouselStrip(vm.notices) { readingNotice = it }' \
  'NoticeCarouselStrip(vm.notices) { nav.navigate(Routes.MESSAGES) }'
# 15 阅读框只念标题，接口给的 content 不进屏幕 → DN-j 正文
check DN-15 "× DN-j 阅读框不再渲染公告正文" "$HS" \
  'n.content.ifBlank { "（这条公告没有正文）" },' \
  'n.title,'
# 16 正文不能滚：长公告的超出部分被 AlertDialog 剪掉 → DN-j 可滚
check DN-16 "× DN-j 阅读框的正文不能滚" "$HS" \
  '                        .padding(top = 4.dp)
                        .verticalScroll(rememberScrollState()),' \
  '                        .padding(top = 4.dp),'
# 17 上一代零调用点的渲染件回来（同一屏两套公告条，屏幕读的与被判据数的是两件东西）→ DN-j 死码
check DN-17 "× DN-j 上一代零调用点的轮播渲染件回来了" "$HS" \
  'private fun NoticeCarouselStrip(notices: List<Notice>, onClick: (Notice) -> Unit) {' \
  'private fun NoticeCarousel(notices: List<Notice>, onClick: () -> Unit) {
}

private fun NoticeCarouselStrip(notices: List<Notice>, onClick: (Notice) -> Unit) {'
# 18 反证自身：活着的那件渲染件被改名（DN-j 的"死码 0"就不再可信）→ DN-j 反证
check DN-18 "× DN-j 反证掉了" "$HS" \
  'private fun NoticeCarouselStrip(notices: List<Notice>, onClick: (Notice) -> Unit) {' \
  'private fun NoticeCarouselStripV2(notices: List<Notice>, onClick: (Notice) -> Unit) {'

restore
bash "$PROBE" >/dev/null 2>&1 && echo "[恢复] 判据簿重新通过" || { echo "✘ [恢复] 判据簿仍红"; bad=$((bad + 1)); }
same=1
for i in "${!FILES[@]}"; do
  cmp -s "${BAKS[$i]}" "${FILES[$i]}" || { echo "✘ [恢复] ${FILES[$i]} 与原样不一致"; same=0; }
done
[ "$same" = 1 ] && echo "[恢复] 全部文件与原样一致" || bad=$((bad + 1))

# 18 条 = DN-1…DN-7（七条真破口）+ DN-8/DN-9（两条"反证自身"）+ DN-10/DN-11（出处与用例数）
#        + DN-12/DN-13（取数时机）+ DN-14…DN-18（用户侧正文那一格：接线/正文/可滚/死码 + 一条反证自身）。
# 期望值按上面一条条数出来，不抄上一本的数字（上一本就是因为没跟着数，红了一轮才被发现）。
echo "司机端公告栏结构负控：通过 $ok / 失败 $bad"
[ "$bad" = 0 ] && [ "$ok" = 18 ]
