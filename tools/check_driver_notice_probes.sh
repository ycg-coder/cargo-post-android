#!/usr/bin/env bash
# 司机端「平台公告」那一栏的结构判据（配 tools/driver_notice_negative_controls.sh）
# 管的是三件事：公告这条现成通路真的接到了司机端、措辞只有一个出处、公告失败没被并进整页 error。
# 用法：bash tools/check_driver_notice_probes.sh      退出码非 0 = 这一格被拆了
# 写法：按内容过滤不按行号；每个"目标 0"都配一条同机制反证（同一模式在判定那侧必须数得出非 0）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
WB=$M/ui/driver/WorkbenchScreen.kt
DN=$M/ui/driver/DriverNotices.kt
HS=$M/ui/user/HomeScreen.kt
BR=$M/ui/BusPostRoot.kt
CT=app/src/test/java/com/shanxiang/buspost/ui/driver/DriverNoticesContractTest.kt
# grep -n 单文件输出的行首是"行号:"，注释行紧跟 `*` / `//` / `/*`：注释里提到某句话不算把那句话写进了产品
NC='^[0-9]+:[[:space:]]*(\*|//|/\*)'

for f in "$WB" "$DN" "$HS" "$BR" "$CT"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

# 非注释行里某句话的出现次数
nc_count() { grep -n "$2" "$1" | grep -cvE "$NC" || true; }

# DN-a 取数点：工作台恰好一次；用户端首页与闪屏预取各一次（三处 = 一条通路三个消费者，没有第二套公告来源）
DNA_WB=$(grep -c 'Repo.notices()' "$WB")
DNA_HS=$(grep -c 'Repo.notices()' "$HS")
DNA_BR=$(grep -c 'Repo.notices()' "$BR")

# DN-b 渲染点：占格、判定调用、正文列表各一处（少一处就是某一环断了）
DNB_ITEM=$(grep -c 'item(key = "notice")' "$WB")
DNB_BANNER=$(grep -c 'noticeBanner(vm.platformNotices)' "$WB")
DNB_DETAIL=$(grep -c 'noticeDetailLines(notice.notices)' "$WB")

# DN-c 失败与"没有"不得同句：司机端这两个文件里不许出现"暂无公告"这句话。
#   0 命中的反证：同一套写法在判定那侧数得出"没取到"（2 处：失败栏标题 + FAILED_TAG）。
DNC_NO=$(nc_count "$WB" 暂无公告)
DNC_NO2=$(nc_count "$DN" 暂无公告)
DNC_PROOF=$(nc_count "$DN" 没取到)

# DN-d 措辞只有一个出处：页面里不许自己拼"平台公告 …"这类句子（判定文件里恰好 2 处：失败标题 + 条数标题）
DND=$(nc_count "$WB" '"平台公告')
DND_PROOF=$(nc_count "$DN" '"平台公告')

# DN-e 演示公告的标签只有一个出处（页面不自己判 demo）
DNE=$(grep -rn '"演示公告"' "$M" --include=*.kt | grep -cvE ':[[:space:]]*(\*|//|/\*)')
DNE_ALL=$(grep -rn '"演示公告"' "$M" --include=*.kt | wc -l | tr -d ' ')

# DN-f 公告失败只影响自己那一栏：整页 error 的汇总行里不得出现公告
DNF=$(grep 'error = listOfNotNull' "$WB" | grep -c 'tices')
DNF_PROOF=$(grep -c 'error = listOfNotNull' "$WB")

# DN-g 初值必须是"还没结论"（loading），不能是"空"也不能是"失败"
DNG=$(grep -c 'RemoteState<List<Notice>>(loading = true)' "$WB")
DNG_BAD=$(grep -c 'RemoteState<List<Notice>>(error = ' "$WB")

# DN-i 取数时机：只有进入/刷新那一轮取公告，作业动作后的回读不取（否则点一次作业多一条 REST）。
#   行为半边由 `DriverActionHonestyContractTest` 的「6 读 + 1 写 + 5 读」钉着：动作后那轮仍是 5 条。
DNI_ON=$(grep -c 'syncFromServer(withNotices = true)' "$WB")
DNI_OFF=$(grep -c 'syncFromServer(withNotices = false)' "$WB")
DNI_GATE=$(grep -c 'if (withNotices) platformNotices = Repo.notices().toState()' "$WB")

# DN-h 契约用例在场
DNH=$(grep -cE '^[[:space:]]*@Test' "$CT")

echo "DN-a 工作台 / 首页 / 闪屏 各一次取数 = $DNA_WB/$DNA_HS/$DNA_BR （目标 1/1/1）"
echo "DN-b 占格 / 判定 / 正文列表         = $DNB_ITEM/$DNB_BANNER/$DNB_DETAIL （目标 1/1/1）"
echo "DN-c 司机端「暂无公告」/ 反证       = $DNC_NO+$DNC_NO2/$DNC_PROOF （目标 0/2：失败与真没有必须两句）"
echo "DN-d 页面自带措辞 / 反证            = $DND/$DND_PROOF （目标 0/恰好 2：措辞只在判定文件）"
echo "DN-e 「演示公告」出处               = $DNE （目标 1；全书命中 $DNE_ALL）"
echo "DN-f 公告混进整页 error / 反证      = $DNF/$DNF_PROOF （目标 0/1）"
echo "DN-g 初值 loading / 初值写成失败     = $DNG/$DNG_BAD （目标 1/0）"
echo "DN-i 入口取 / 动作不取 / 闸门        = $DNI_ON/$DNI_OFF/$DNI_GATE （目标 1/1/1）"
echo "DN-h 契约用例数                     = $DNH （目标 ≥9）"

fail=0
[ "$DNA_WB" = 1 ] || { echo "× DN-a 工作台的 Repo.notices() 不是恰好一次（现 $DNA_WB）：接了两次就是重复 REST，一次都没接就是这一格又是死的"; fail=1; }
[ "$DNA_HS" = 1 ] || { echo "× DN-a 用户端首页的取数点变了（现 $DNA_HS）：公告通路出现第二套来源或被撤掉"; fail=1; }
[ "$DNA_BR" = 1 ] || { echo "× DN-a 闪屏预取点没了（现 $DNA_BR）：首屏预热与冷启动口径断了"; fail=1; }
[ "$DNB_ITEM" = 1 ] || { echo "× DN-b 公告占格不是恰好一处（现 $DNB_ITEM）"; fail=1; }
[ "$DNB_BANNER" = 1 ] || { echo "× DN-b 页面没调用 noticeBanner（现 $DNB_BANNER）：判定层成了摆设，措辞会退回页面里各写一份"; fail=1; }
[ "$DNB_DETAIL" = 1 ] || { echo "× DN-b 正文列表没被渲染（现 $DNB_DETAIL）：点开只剩一条标题，其余公告被藏掉"; fail=1; }
[ "$DNC_NO" = 0 ] && [ "$DNC_NO2" = 0 ] || { echo "× DN-c 司机端出现了「暂无公告」（$DNC_NO/$DNC_NO2）：弱网时这一句会把故障洗成「平台没话说」"; fail=1; }
[ "$DNC_PROOF" = 2 ] || { echo "× DN-c 反证掉了（「没取到」非注释出现数=$DNC_PROOF）：上面那个 0 可能是因为失败措辞整体没了"; fail=1; }
[ "$DND" = 0 ] || { echo "× DN-d 页面自己拼了 $DND 处「平台公告」措辞：两处措辞迟早和判定层对不上"; fail=1; }
[ "$DND_PROOF" = 2 ] || { echo "× DN-d 反证掉了（判定文件里「平台公告」=$DND_PROOF，目标恰好 2）：那个 0 数的是不存在的东西"; fail=1; }
[ "$DNE" = 1 ] || { echo "× DN-e 「演示公告」出处=$DNE（目标 1）：演示与真实公告要么混为一谈，要么多了第二套说法"; fail=1; }
[ "$DNF" = 0 ] || { echo "× DN-f 公告被并进整页 error（$DNF 处）：一条附属信息取不到不该让工作台报「加载失败」"; fail=1; }
[ "$DNF_PROOF" = 1 ] || { echo "× DN-f 反证掉了（汇总行本身=$DNF_PROOF）：那个 0 可能是整页 error 没了"; fail=1; }
[ "$DNG" = 1 ] || { echo "× DN-g 公告初值不再是「还没结论」（现 $DNG）：改成空列表就等于开局替后端说了「没有公告」"; fail=1; }
[ "$DNG_BAD" = 0 ] || { echo "× DN-g 公告初值被写成失败态（$DNG_BAD 处）：还没发请求就顶一条错误条"; fail=1; }
[ "$DNI_ON" = 1 ] || { echo "× DN-i 入口/刷新那轮取公告的调用点不是恰好一处（现 $DNI_ON）"; fail=1; }
[ "$DNI_OFF" = 1 ] || { echo "× DN-i 作业动作后那一轮不再取公告的表态没了（现 $DNI_OFF）：公告会跟着每次作业发 REST"; fail=1; }
[ "$DNI_GATE" = 1 ] || { echo "× DN-i 取数没被 withNotices 圈住（现 $DNI_GATE）：闸门写成了无条件，等于每轮回读都多发一条"; fail=1; }
[ "$DNH" -ge 9 ] || { echo "× DN-h 契约用例少于 9 条（现 $DNH）：三种说法的分工没人钉"; fail=1; }

if [ "$fail" = 0 ]; then echo "司机端公告栏判据通过"; else echo "司机端公告栏判据失败"; fi
exit "$fail"
