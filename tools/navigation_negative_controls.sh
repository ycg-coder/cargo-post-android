#!/usr/bin/env bash
# N-* 导航引擎判据的**负控制**：逐条把契约破开 → 确认 check_navigation_probes.sh exit 1 且报的就是这一条。
# 只在 /tmp 副本上动手，工程真身不受影响（纯文本判据，不需要编译）。
# 用法：bash tools/navigation_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/nav_neg.$$
PKG=com/shanxiang/buspost
PR="$SRC/tools/check_navigation_probes.sh"
mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
mkdir -p "$SC/app/build" && cp "$SRC/app/build.gradle.kts" "$SC/app/build.gradle.kts"
[ -f "$SC/app/src/main/java/$PKG/core/navigation/AmapNavigationEngine.kt" ] || { echo "✘ 副本没建起来（$SC），本轮负控全部作废"; exit 2; }
[ -f "$SC/app/build.gradle.kts" ] || { echo "✘ 副本缺 build.gradle.kts，N-i 无法负控"; exit 2; }
A="$SC/app/src/main/java/$PKG"
NE="$A/core/navigation/NavigationEngine.kt"
AM="$A/core/navigation/AmapNavigationEngine.kt"
UI="$A/ui/driver/DriverSdkNavCard.kt"
NS="$A/ui/driver/DriverNavScreen.kt"
WB="$A/ui/driver/WorkbenchScreen.kt"
BR="$A/ui/BusPostRoot.kt"
PF="$A/data/store/Prefs.kt"
GD="$A/ui/user/GoodsDetailScreen.kt"
SD="$A/ui/user/SendScreen.kt"
HM="$A/ui/user/HomeScreen.kt"
MP="$A/ui/user/MinePages.kt"
BG="$SC/app/build.gradle.kts"
TP="$SC/app/src/test/java/$PKG/core/navigation/NavigationContractTest.kt"
[ -f "$WB" ] && [ -f "$BR" ] || { echo "✘ 副本缺 WorkbenchScreen/BusPostRoot，N-n/N-p 无法负控"; exit 2; }
CLEAN=/tmp/nav_neg_clean.$$; cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

# ⚠ 三个改动都是 D-082 那 13 道空转闸门的根因，不是顺手美化：
# 1) 落空必须说出口。原来这些注入函数失败时只把 ANCHOR-MISS 打到 stderr（批日志里看不到），
#    刀没落下去 = 判据照样绿 = 日志上一句"负控制失效（exit 0）"，与"判据真的不咬"长得一模一样。
#    现在 MISS=1 由 ctl 打点，措辞与 exception/safe_call 那几本对齐。
# 2) 换行符不许改写。原来 `open(p,'w')` 默认 newline=None 会把整个文件重写成 CRLF；
#    现在读与写都 newline=''，插入行按该文件自己的行尾走。
# 3) drop 认 CRLF。`NavigationEngine.kt` 工作树是 CRLF（index 是 LF，core.autocrlf=true 检出时转的，
#    `git ls-files --eol` 实测 i/lf w/crlf），而 drop 原来只找 "\n}\n" ⇒ 六条"整块没了"的破口
#    （N-u×2 / N-z×2 / N-aa×2）当场静默空转了一整轮。
MISS=0
ins() { python - "$1" "$2" "$3" <<'PY' || MISS=1
import sys
p, anchor, add = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
i = s.find(anchor)
if i < 0: sys.exit("ANCHOR-MISS: " + anchor[:60])
NL = '\r\n' if '\r\n' in s else '\n'
j = s.index('\n', i) + 1
open(p, 'w', encoding='utf-8', newline='').write(s[:j] + add.replace('\n', NL) + NL + s[j:])
PY
}
sub() { python - "$1" "$2" "$3" <<'PY' || MISS=1
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if old not in s: sys.exit("ANCHOR-MISS: " + old[:60])
NL = '\r\n' if '\r\n' in s else '\n'
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new.replace('\n', NL), 1))
PY
}
suball() { python - "$1" "$2" "$3" <<'PY' || MISS=1
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if old not in s: sys.exit("ANCHOR-MISS: " + old[:60])
NL = '\r\n' if '\r\n' in s else '\n'
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new.replace('\n', NL)))
PY
}
# drop：从锚点那一行删到它自己那一层的收尾 `
# }`（F-3 ⑭ 的负控要验"整块没了"这种破口，sed 的范围删在 Windows 换行上不可靠）
drop() { python - "$1" "$2" <<'PY' || MISS=1
import re, sys
p, pat = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8', newline='').read()
i = s.find(pat)
if i < 0: sys.exit("ANCHOR-MISS: " + pat[:60])
m = re.compile(r'\n\}[ \t]*\r?\n').search(s, i)
if not m: sys.exit("NO-CLOSING-BRACE: " + pat[:60])
open(p, 'w', encoding='utf-8', newline='').write(s[:i] + s[m.end():])
PY
}
# dropline：只删锚点所在的那一行。N-y 的"表被砍一行"必须是真删一行 ——
# 改缩进或改成注释虽然也能让计数掉下来，但那证的是判据对空格敏感，不是判据抓得住破口。
dropline() { python - "$1" "$2" <<'PY' || MISS=1
import sys
p, needle = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8', newline='').read()
ls = s.split("\n")
k = [i for i, x in enumerate(ls) if needle in x]
if not k: sys.exit("ANCHOR-MISS: " + needle[:60])
del ls[k[0]]
open(p, 'w', encoding='utf-8', newline='').write("\n".join(ls))
PY
}

BAD=0
ctl() { # $1=编号 $2=说明
  local out code
  if [ "$MISS" = 1 ]; then
    echo "✘ $1  $2 → 锚点没命中，破口根本没注入（这条负控不成立）"
    BAD=$((BAD + 1)); MISS=0
    rm -rf "$SC"; cp -r "$CLEAN" "$SC"
    return
  fi
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" = 1 ] && echo "$out" | grep -q "^× $1"; then
    echo "✔ $1  $2 → exit 1：$(echo "$out" | grep "^× $1" | head -1)"
  elif [ "$code" = 1 ]; then
    echo "△ $1  $2 → exit 1，但抓到的是别的判据：$(echo "$out" | grep '^×' | head -1)"
  else
    echo "✘ $1  $2 → 负控制失效（exit $code），这条判据等于没判据"
    BAD=$((BAD + 1))
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

LP="$SRC/tools/check_location_probes.sh"

ctl_loc() { # $1=编号 $2=说明   —— 坐标系豁免这条线由 L 家族守，破口也必须在 L 家族上验
  local out code
  if [ "$MISS" = 1 ]; then
    echo "✘ $1  $2 → 锚点没命中，破口根本没注入（这条负控不成立）"
    BAD=$((BAD + 1)); MISS=0
    rm -rf "$SC"; cp -r "$CLEAN" "$SC"
    return
  fi
  out=$(BUSPOST_ANDROID="$SC" bash "$LP" 2>&1); code=$?
  if [ "$code" = 1 ] && echo "$out" | grep -q "^× $1"; then
    echo "✔ $1  $2 → exit 1：$(echo "$out" | grep "^× $1" | head -1)"
  elif [ "$code" = 1 ]; then
    echo "△ $1  $2 → exit 1，但抓到的是别的判据：$(echo "$out" | grep '^×' | head -1)"
  else
    echo "✘ $1  $2 → 负控制失效（exit $code），这条判据等于没判据"
    BAD=$((BAD + 1))
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# ctl_loc_keep：L-w 的闸门判据接受**两种同样合法**的形状（赋值行本身 / 包住赋值的那一层），
# 所以这一族必须有"必须不红"的反向负控（与 runcatching 簿的 ctl_keep 同一条规矩）：
# 只验红的那两条，判据若把第二种合法形状也读成没闸门，就会变成"永远红"，而永远红与判对了读数一样绿不起来、没人看得穿。
ctl_loc_keep() { # $1=编号 $2=说明   —— 期望 exit 0
  local out code
  if [ "$MISS" = 1 ]; then
    echo "✘ $1 反证 $2 → 锚点没命中，破口根本没注入（这条负控不成立）"
    BAD=$((BAD + 1)); MISS=0
    rm -rf "$SC"; cp -r "$CLEAN" "$SC"
    return
  fi
  out=$(BUSPOST_ANDROID="$SC" bash "$LP" 2>&1); code=$?
  if [ "$code" = 0 ]; then
    echo "✔ $1 反证 $2 → exit 0（合法形状没被判红）"
  else
    echo "✘ $1 反证 $2 → exit $code：$(echo "$out" | grep '^×' | head -1)（判据把合法形状读成破口 = 只会红错方向，真破口反而混在里面）"
    BAD=$((BAD + 1))
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
if [ "$base_code" = 0 ]; then echo "✔ 基线：副本原样 exit 0"; else echo "✘ 基线就红，下面每条都失去意义"; echo "$BASE" | grep '^×'; exit 1; fi

# N-b：把推进挂到本机定时器上（假导航最典型的形态）
ins "$AM" "override suspend fun calculateRoute" '    private fun tick() { delay(4_000L) }'
ctl N-b "引擎里塞一个 delay() 当推进"

# N-c：对高德折线再做一次坐标系换算（二次偏移）
ins "$NE" "fun naviRouteCheck(" 'val twice = com.shanxiang.buspost.core.loc.Locator.toGcj02(Geo(29.6, 106.6))'
ctl N-c "判据层再套一次 toGcj02"

# N-d：用直线距离当里程
ins "$NE" "fun naviRouteCheck(" 'val straightMeters = com.shanxiang.buspost.data.model.GeoMath.distance(Geo(29.6, 106.6), Geo(29.65, 106.65))'
ctl N-d "引擎里出现 GeoMath 直线里程"

# N-e：SDK 层自己再起一道 validate（同一个问题第二个答案）
ins "$AM" "private fun instance()" '    private fun selfCheck(p: List<Geo>) = RealRoadRouteValidator.validate("AMAP", p)'
ctl N-e "引擎目录里出现第二处 validate"

# N-f：发明第三种引擎实现（"用直线假装在导航"的入口）
ins "$AM" "internal const val NAVI_CLASS_NAME" 'internal class StraightLineEngine : NavigationEngine {'
ctl N-f "多出一个 NavigationEngine 实现"

# N-g：离开页面不摘监听
sub "$UI" 'onDispose { engine.release() }' 'onDispose { }'
ctl N-g "release() 不再被调用"

# N-g：目的地不再只读后端任务点（客户端自己决定去哪里）
sub "$UI" 'val nextStop = nextNavigableStop(guide)' 'val nextStop = guide.stops.firstOrNull { it.hasGeo }'
ctl N-g "目的地改成本机自选"

# N-h：readiness 退回普通属性 —— 页面只能靠轮询读
sub "$NE" '    val readiness: StateFlow<EngineReadiness>' '    val readiness: EngineReadiness'
ctl N-h "readiness 不再是 flow"

# N-i：3dmap 与 navi-3dmap 并存
ins "$BG" 'implementation("com.amap.api:navi-3dmap' '    implementation("com.amap.api:3dmap:10.0.600")'
ctl N-i "退回两个高德包并存"

# N-j：引入没核对过的 SDK 类
ins "$AM" 'import com.amap.api.navi.AMapNavi' 'import com.amap.api.navi.AMapNaviView'
ctl N-j "import 白名单外的 SDK 类"

# N-j：页面绕过判据层直接调 SDK
ins "$UI" 'import com.shanxiang.buspost.core.map.RouteCheck' 'import com.amap.api.navi.AMapNavi'
ctl N-j "引擎层之外直接 import 导航 SDK"

# N-k：把"没给"那一格换成一句看起来更完整的谎话
# ⚠ D-082 空转闸门重锚：续七把正式卡片那六处「高德没给」统一改成了「暂未知」，判据 NK 同轮改成数
#   `高德没给|暂未知` 的生效行（恰好 6），而这条破口当时没跟着动 ⇒ 它改的『高德没给』只剩两句注释，
#   被 NC 滤掉，NK 一根手指都不动。现在砍的是**生效行里那一句**（里程那一格）。
sub "$UI" 'else "暂未知" }' 'else "已到达" }'
ctl N-k "没给的字段被换成断言"

# N-l：依赖换了还留着"无需地图 SDK"这句旧断言（真实回归形态：把改回的文案写回副标题）
# ⚠ D-082 空转闸门重锚：旧锚点那句副标题已被 8387929（清除界面技术术语）改写，注入静默落空。
sub "$NS" 'else "实际道路轨迹，这一屏的转向只读它"' 'else "实际道路轨迹，无需地图 SDK"'
ctl N-l "旧断言回到代码里"

# N-a：删用例让判据层失去行为底盘
suball "$TP" '    @Test' '    // @Test 负控删除'
ctl N-a "契约测试用例被清空"

# ---- #51（旧屏转 DEBUG ONLY）新增的四条：N-m…N-p ----

# N-m：把定时器翻页塞回司机 UI（§24 那 6 处的原始形态）
ins "$NS" "fun showNextStep()" '    private fun autoTick() { delay(4_000L) }'
ctl N-m "司机 UI 里又出现 4 秒定时器翻页"

# N-n：路由注册不再带 DEBUG 闸（正式包又能进假屏）
sub "$BR" 'if (BuildConfig.DEBUG) composable(Routes.DRIVER_NAV)' 'composable(Routes.DRIVER_NAV)'
ctl N-n "旧屏路由在正式包又注册了"

# N-n：入口调用把闸拆掉（正式包跳一条没注册的路 = 运行时找不到路由）
# ⚠ D-082 空转闸门重锚：工作台那一行的参数名是 onOpenNav（旧锚点写的 onNav 不存在，注入静默落空）。
sub "$WB" 'onOpenNav = { if (BuildConfig.DEBUG) nav.navigate(Routes.DRIVER_NAV) },' 'onOpenNav = { nav.navigate(Routes.DRIVER_NAV) },'
ctl N-n "工作台入口不再带 DEBUG 闸"

# N-n：旧屏文件里的第二道自守被换成恒真
sub "$NS" 'if (!BuildConfig.DEBUG) {' 'if (false) {'
ctl N-n "旧屏文件自守没了"

# N-o：页头那句"非真实导航"被删 —— 标注数量掉到 2 以下
sub "$NS" '非真实导航：这一屏只在调试包打开' '调试屏'
ctl N-o "旧屏的显著标注被删掉"

# N-o：车道箭头的来源说明没了（猜的图形会被读成后端车道数据）
# ⚠ D-082 空转闸门重锚：那句说明现在写作「箭头图形是按名字猜的」（判据 NO2 同轮已重锚），
#   这条破口还指着旧句「本页按名字猜的」⇒ 注入静默落空。
sub "$NS" '箭头图形是按名字猜的，不算车道级引导' '箭头图形来自后端的车道级引导'
ctl N-o "车道箭头来源说明被删"

# N-p：调试页又挂回引擎卡片（工作台 + 旧屏各一个 AMapNavi 实例）
ins "$NS" 'val g = vm.guide' '        DriverSdkNavCard(guide = g, vehicleGeo = pos, onToast = { toast = it })'
ctl N-p "旧屏重新挂上引擎卡片"

# N-p：正式卡片里的高德交接被换成本机坐标（release 失去交给外部真导航的出口）
sub "$UI" 'openAmap(context, stop.geo' 'openAmap(context, Geo(0.0, 0.0)'
ctl N-p "正式卡片的交接目的地被换掉"

# N-p：外部地图代码搬回调试屏文件（正式可达的东西住在调试文件里）
ins "$NS" "private fun speak(" 'private fun openAmap(context: Context, to: Geo, name: String): String = ""'
ctl N-p "外部地图函数回到旧屏文件中"

# ---- F-3 ④⑫（导航入口 readiness 闸门 / D-048）新增的四条：N-q…N-t ----

# N-q：把构建类型当业务能力开关（release 包会对着已就绪的引擎说"不能导航"，正是要防的那种退化）
ins "$UI" 'val gate = navigationGateOf(' '    val debugOnlyEntry = !BuildConfig.DEBUG'
ctl N-q "正式导航入口又开始读 BuildConfig"

# N-r：页面不再问判据层，自己造一个"永远可点"的结论（两个答案的起点）
sub "$UI" 'val gate = navigationGateOf(' 'val gate = NavigationGate(NavigationGateCode.READY'
ctl N-r "页面不再调 navigationGateOf"

# N-r：退回旧写法 enabled = !blocked —— Initializing 被当成可点
sub "$UI" 'enabled = gate.canNavigate && !calculating' 'enabled = !blocked && !calculating'
ctl N-r "按钮 enabled 退回 !blocked（初始化中被当成可点）"

# N-r：灰按钮不再说原因（用户只能猜是 App 坏了）
# ⚠ D-082 空转闸门重锚：这一格现在是单行 `if (…) InfoRow(…)`，旧锚点带花括号的那条不存在。
# ⚠ D-177 再重锚：那一行改读 `gate.hint`（原文撤下屏），旧锚点 `gate.detail` 的字面已经不在树里 ——
#   锚点跟着改，注入的破口还是同一件事：**这一行不再说"缺什么/下一步"，只留一个空壳**。
#   名字也要跟着改准：过去写的是"不再显示具体缺什么"，而判据那句是"不再说出缺什么"，
#   对不上时 ctl 只会报 △（红了但抓到别的），这一格等于没验过。
sub "$UI" 'if (!gate.canNavigate && gate.hint.isNotEmpty()) InfoRow("下一步该做什么", gate.hint)' \
           'if (!gate.canNavigate) InfoRow("下一步该做什么", "")'
ctl "N-r 不可点时不再说出缺什么" "那一行不再读 hint（灰按钮不给原因）"

# N-s：引擎退回只挡 Blocked —— 算路与开始导航都会放行"还在等回执"的引擎
sub "$AM" 'if (!state.canNavigate) {' 'if (state is EngineReadiness.Blocked) {'
ctl N-s "引擎只挡 Blocked、放行 Initializing"

# N-t：删掉一个成因码（NO_ROUTE 被并进别的话题，缺起点与缺组件混成一句话）
sub "$NE" '    NO_ROUTE,' '    NO_ROUTE_LEGACY,'
ctl N-t "成因码少一个"

# N-t：INITIALIZING 并进 ERROR —— 把"还在等回执"念成"这手机不能导航"
sub "$NE" '        code = NavigationGateCode.INITIALIZING,' '        code = NavigationGateCode.ERROR,'
ctl N-t "初始化中被并进 ERROR"

# N-t：缺起点与缺目标点合成一个码（用户不知道该等定位还是等调度）
suball "$NE" 'code = NavigationGateCode.NO_ROUTE,' 'code = NavigationGateCode.ERROR,'
ctl N-t "NO_ROUTE 的两个产出点被合并"


# ---- 以下 N-u…N-x 是 F-3 ⑭/⑰（§九 路线绑定 / V21-13-14 读点实况）新增的负控 ----
# 这一族验的是"闸门还在，但没人走"那一类失效：结构上函数、字段、调用点都必须在，
# 少任何一处，V21-09 那两行 ❌ 就原地回来，而它不会有任何编译错误提示你。

# N-u：整块 routeBindingOf 从判据层删掉（工程没有 Robolectric，它一旦搬进 SDK 层就再没有用例能调到）
drop "$NE" 'fun routeBindingOf('
ctl N-u "绑定判据整块没了"

# N-u：§九 点名的 accepted 凭证被删（"刚验过的那条"这个概念消失）
drop "$NE" 'data class AcceptedRouteSession'
ctl N-u "accepted 凭证与指纹一起没了"

# N-v：绑定结论被算路自己吞掉（判据还在，但没人按它拦 startNavi）
sub "$AM" 'if (binding is RouteBinding.Unbound) {' 'if (false) {'
ctl N-v "绑定说不行也照样 startNavi"

# N-v：开始导航前不再重读高德此刻的路径，拿本机记忆当"现在还是那条路"
sub "$AM" 'SdkPathShape(coordCount = it.geoPoints().size, roadDistanceMeters = it.allLength)' 'SdkPathShape(coordCount = 0, roadDistanceMeters = 0)'
ctl N-v "绑定比的是写死的形状"

# N-v：凭证不再由"过闸门那一次"写入（任何一次算路都能记成已验证）
sub "$AM" 'acceptedRoute = (check as? RouteCheck.Accepted)?.let' 'acceptedRoute = AcceptedRouteSession('
ctl N-v "凭证写入点绕开真实道路闸门"

# N-v：7 个作废点一个都不清（偏航自算/超时/停止之后仍留着旧记录去 startNavi）
suball "$AM" 'acceptedRoute = null' 'acceptedRoute = acceptedRoute'
ctl N-v "记录不再作废"

# N-e3：算路出口不再走 naviRouteCheck（F-3 ⑭ 把 `return naviRouteCheck(` 改成先接住再返回，锚点同轮换成了"调用次数=1"）
sub "$AM" 'val check = naviRouteCheck(' 'val check = RouteCheck.Rejected(reason = "绕开闸门", detail = "")'
ctl N-e "高德折线不经校验直进界面（NE3 新锚点）"

# N-w：指纹换成一个没有来源的随机串（V21-13 的 graphVersion 若被照抄进代码就是这个样子）
ins "$NE" 'data class SdkPathShape' '    private val fakeFingerprint = java.util.UUID.randomUUID().toString()'
ctl N-w "用随机数冒充路线指纹"

# N-w：终点不再进凭证（§九"任务变了指纹就变"失去依据，换任务点还能沿用旧记录）
sub "$AM" 'destinationLat = to.lat' 'destinationLat = 0.0'
ctl N-w "任务变化不再改变指纹"

# ---- N-x（赛道 ⑭ 之后的口径：读口必须恰好一个，0 与 2 都算破）----
# ⑰ 那轮这一族只有两条注入，因为那时"生产代码里没人读缓存"是安全状态、目标 0；
# ⑭ 之后唯一的读口就是披露那一句，于是三条方向都要验：多一个读口、把那个读口删掉、多一个写入点。
RR="$A/core/map/RouteRepository.kt"
[ -f "$RR" ] || { echo "✘ 副本缺 RouteRepository.kt，N-x 无法负控"; exit 2; }
OFF="$A/data/repo/OfflineRouteDisclosure.kt"
[ -f "$OFF" ] || { echo "✘ 副本缺 OfflineRouteDisclosure.kt，N-x 第三支与整个 N-ab 族无法负控"; exit 2; }

# N-x：在本文件里另开第二个读口（object 内部的裸调用，NX 的第一支口径）
ins "$RR" 'object RouteRepository {' '    fun hudRead(): Any = cached("driverRoute:7")'
ctl N-x "第二个读口（object 内裸调用，NX 1→2）"

# N-x：另一支口径 —— 别处用限定写法再开一个读口
REPO="$A/data/repo/Repo.kt"
[ -f "$REPO" ] || { echo "✘ 副本缺 Repo.kt，N-x 第一支无法负控"; exit 2; }
ins "$REPO" '        val check = RouteRepository.resolve(' '        val reused = RouteRepository.cached("driverRoute:7")'
ctl N-x "第二个读口（限定写法，NX 1→2）"

# N-x：把 ⑭ 那句披露整个删掉 —— 缓存重新变成只写不读，V21-13/14 的升格依据当场失效
dropline "$OFF" 'RouteRepository.cached(key, nowMillis)'
ctl N-x "披露读点被删（NX 1→0）：本机存着什么这句话没人说了"

# N-x：第二处往缓存里写路线 = 有绕过真实道路闸门的路
ins "$AM" "private fun instance()" '    private fun secondWrite(p: List<Geo>) = com.shanxiang.buspost.core.map.RouteRepository.resolve("navi", p)'
ctl N-x "路线缓存写入点不再是唯一一处"


# ---- N-ab 是赛道 ⑭（离线真实道路缓存）新增的一族：24 个计数器，逐条破 ----
# 这一族的破口有个共同形状：**用例照样全绿、页面照样说话**，变的是"这句话还有没有唯一来源"。
# 所以每条注入只动一处，让对应的那个计数器单独掉 —— 首跑若出现"红了别的族"，就是判据串味，按 △ 处理。

# N-ab1：把唯一的读出口改名 —— cached() 还在被调用，但"只有一把"这件事再也数不出来
sub "$RR" 'fun cached(' 'fun peek('
ctl N-ab "读出口改名（NAB1 1→0）"

# N-ab2：旧的那把无状态出口回来了 —— 它给的是可变整表引用，页面能绕过过期判定直接把几何画出来
ins "$RR" '    fun cached(' '    fun snapshot(): Any? = null'
ctl N-ab "cachedAt/snapshot 回潮（NAB2 0→1）"

# N-ab3：TTL 常量不再由 const 钉住（值被挪成可变属性，每条读路线各自判新旧的第一步）
sub "$RR" 'const val MAX_AGE_MILLIS' 'val MAX_AGE_MILLIS'
ctl N-ab "TTL 定义处写法变了（NAB3 1→0）"

# N-ab4：边界从"相等即过期"改成"相等还算新鲜" —— 只改一个字符，两边都不会让任何用例变红
sub "$RR" 'if (nowMillis >= entry.expiresAtMillis)' 'if (nowMillis > entry.expiresAtMillis)'
ctl N-ab "过期边界改成严格大于（NAB4 1→0，§十三 原话是 < 才算新鲜）"

# N-ab5/6：抓取时间由生产代码现场取时 —— "什么时候抓的"被换成"什么时候读的"
sub "$RR" 'fetchedAtMillis = nowMillis' 'fetchedAtMillis = System.currentTimeMillis()'
ctl N-ab "抓取时间不再来自时钟入参（NAB5 1→0 且 NAB6 0→1）"

# N-ab8：换回裸 LinkedHashMap —— 并发写穿 size 上限，KDoc 里那句"不要裸 LinkedHashMap"就变成空话
sub "$RR" 'ConcurrentHashMap<String, RouteCacheEntry>(8)' 'LinkedHashMap<String, RouteCacheEntry>()'
ctl N-ab "生产代码回潮裸 LinkedHashMap（NAB8 0→1）"

# N-ab9/10：来源不再照抄后端原文，而用本机归一枚举顶替 —— "后端当时给的来源原文"变成机器自己的标签
sub "$RR" 'providerRaw = sourceRaw.orEmpty()' 'providerRaw = source.name'
ctl N-ab "来源原文被枚举顶替（NAB9 1→0）"

# N-ab12：过期那一格带上几何 —— "正式路线不许回过期路线"从结构性退化成约定性
sub "$RR" 'data class Stale(val providerRaw: String' 'data class Stale(val route: RealRoadRoute, val providerRaw: String'
ctl N-ab "过期格里塞进路线几何（NAB12 0→1）"

# N-ab13：写入不再看后端那一位 —— 后端说这条路线不可用时旧几何仍留在表里（D-⑱-3 回潮）
sub "$RR" 'if (check is RouteCheck.Accepted && serverAvailable)' 'if (check is RouteCheck.Accepted)'
ctl N-ab "后端否决不再拦住写入（NAB13 1→0）"

# N-ab14：那一位不再传进 resolve —— 判定与写入解耦，翻 false 也删不掉旧条目
sub "$REPO" 'serverAvailable = serverAvailable,' 'serverAvailable = true,'
ctl N-ab "serverAvailable 不再传给写入（NAB14 1→0）"

# N-ab15：把后端那一位的读取挪到 resolve 之后 —— 传进去的是上一个决定，首次失败仍会写进缓存
dropline "$REPO" 'val serverAvailable = !o.has("routeAvailable")'
ins "$REPO" '            stationGeos = stations.map { it.geo }, serverAvailable = serverAvailable,' '        val serverAvailable = !o.has("routeAvailable") || o.multiBool("routeAvailable")'
ctl N-ab "判定排到写入之后（NAB15 行序翻转为 0）"

# N-ab16：过期删除换成单参 remove —— 会把别人刚写的新条目一起删掉，读到的"没有缓存"是假的
sub "$RR" 'accepted.remove(key, entry)' 'accepted.remove(key)'
ctl N-ab "过期删除不再只删自己读到的那一份（NAB16 1→0）"

# N-ab17：越界淘汰不再按写入序号 —— ConcurrentHashMap 没有插入序，被淘汰的可能是刚写的那一条
sub "$RR" 'minByOrNull { it.value.sequence }' 'minByOrNull { it.value.fetchedAtMillis }'
ctl N-ab "淘汰键换掉（NAB17 1→0）"

# N-ab18：披露文件里出现第二个读口 —— 同一份表两处说话，两句话术可以各说各的新鲜度
ins "$OFF" 'internal fun offlineCacheAgeText' 'internal fun bannerAgeText(key: String, nowMillis: Long): String = RouteRepository.cached(key, nowMillis).let { "" }'
ctl N-ab "第二处缓存披露读点（NAB18 1→2）"

# N-ab19：披露那一半开始交几何 —— 离线几何没经过 MR/MV 族的重锚就进了句子（D-⑭-2 回潮）
ins "$OFF" 'internal fun offlineCacheAgeText' '    private val geom: MapPath? = null'
ctl N-ab "披露处交出折线/几何（NAB19 0→1）"

# N-ab20：三格并成两格 —— "有过一条但已过期"与"没有过"共用一句话（§八 ㉗）
sub "$OFF" '    is RouteCacheRead.Stale ->' '    // 与 Absent 合并：都是"这条路线不能画"'
ctl N-ab "过期格与没有格并句（NAB20 3→2）"

# N-ab21：把 F-3 ⑧ 那句"不画任何示意线"抄进披露句 —— 无路线话术的唯一出口在 MissionMarkers，说两遍就是两个答案
ins "$OFF" 'internal fun offlineCacheAgeText' '// 这里不画任何示意线，只说明本机存过什么'
ctl N-ab "无路线措辞出现第二处出口（NAB21 0→1）"

# N-ab22：把 graphVersion 接进生产代码 —— 后端 VO／小程序／DemoData 三面 0 生产者，那是凭空造的权威
ins "$RR" '    const val MAX_AGE_MILLIS' '    private val graphVersion: String? = null'
ctl N-ab "出现 graphVersion 生效行（NAB22 0→1）"

# N-ab23：缓存键里塞哈希 —— 那串字符没有任何来源能解释（与 N-w 同族，那一族只管 core/navigation）
ins "$RR" '    fun cached(' '    private fun keyHash(k: String) = k.hashCode()'
ctl N-ab "缓存侧出现哈希/随机数（NAB23 0→1）"

# N-ab24：失败原因不再经披露出口 —— 司机只看到裸 errorTextOf，本机存着什么这件事又没人说
sub "$REPO" 'routeFailureTextOf("driverRoute:$driverId", errorTextOf(e))' 'errorTextOf(e)'
ctl N-ab "披露调用点被拆掉（NAB24 1→0）"


# ---- N-2（赛道 ⑨：转向话术表 + 高德内置播报）的 N-y 一族 ----
# 这一族的破口都长得像"表还是那张表"：行数没变、页面还在，但有的码没人念了、有的码被编出来了、
# 有的句子是高德没说过而本机自己写的。所以每条都只动一处，让对应的那个计数器单独掉。

# N-y1：砍掉一个**已定义**的转向码（9=STRAIGHT 由 javap 实测）——高德给了直行，页面念成"高德没给"
dropline "$NE" '    9 -> "直行"'
ctl N-y "IconType 表少了一行（57→56）"

# N-y1：给一个实测**不存在**的码编个动作（55..64 在 navi-3dmap 10.0.600 里没有定义）
ins "$NE" '    54 -> "通过平路口"' '    55 -> "并线并入直行"'
ctl N-y "IconType 表多出一行表外码（57→58）"

# N-y12：表外码不再回 null 而是就近给一句话 —— 两张表的行数一个都没变，只有 else 的形状能抓它
sub "$NE" '    else -> null' '    else -> "直行"'
ctl N-y "表外的转向码被并入了一句已有人话（else -> null 2→1）"

# N-y2：播报类型表凭空多一个名字（NaviTTSType 实测只有 1..6）
ins "$NE" '    6 -> "偏航播报"' '    7 -> "自定义类型"'
ctl N-y "NaviTTSType 表多出一行（6→7）"

# N-y3：不带类型码那个重载没人接了 —— 高德走哪条回调不由本机决定，少接一条就静默丢一类播报
dropline "$AM" 'override fun onGetNavigationText(text: String?)'
ctl N-y "播报回调只接了一个重载（2→1）"

# N-y4：本机开始自己写句子（红线：不合成高德没说过的话）
sub "$AM" 'voiceState.value = text?.takeIf { it.isNotBlank() }?.let { NavigationVoice(typeCode = null, text = it) }' 'voiceState.value = NavigationVoice(typeCode = null, text = "前方请减速")'
ctl N-y "播报帧被本机合成句子顶掉（构造点 2→1）"

# N-y5：停止导航不清帧 —— 页面会把上一次会话的最后一句当成本次播报
sub "$AM" 'voiceState.value = null' 'voiceState.value = voiceState.value'
ctl N-y "stopNavi 不再清掉播报帧（1→0）"

# N-y6：正式导航面出现本机 TTS（旧屏那台是 DEBUG ONLY 的假导航，D-003）
ins "$UI" 'val sdkVoice by engine.voice.collectAsStateWithLifecycle()' '    private val negCtlTts = android.speech.tts.TextToSpeech(context)'
ctl N-y "release 导航面自己起了一颗 TTS（0→1）"

# N-y7：开关不再交给高德（AMapNavi 是进程单例，不显式设就沿用上一次的状态）
sub "$AM" 'navi.setUseInnerVoice(voiceOn)' 'navi.setUseInnerVoice(true)'
ctl N-y "语音开关变成写死的 true（1→0）"

# N-y8：页面不再读「我的」里那个开关，开关成了装饰
sub "$UI" 'engine.startGpsNavi(Prefs.voiceGuidance)' 'engine.startGpsNavi(true)'
ctl N-y "页面不再把 voiceGuidance 交给引擎（1→0）"

# N-y9：播报改成函数调用去问，页面只能轮询
sub "$NE" 'val voice: StateFlow<NavigationVoice?>' 'fun voiceSnapshot(): NavigationVoice?'
ctl N-y "播报不再由 flow 承载（2→1）"

# N-y10：页面不再订阅 voice flow，播报文本改成本机常量
sub "$UI" 'val sdkVoice by engine.voice.collectAsStateWithLifecycle()' 'val sdkVoice: NavigationVoice? = null'
ctl N-y "页面不再订阅 voice flow（1→0）"

# N-y11：页面不再查表，把图标码直接印成数字
sub "$UI" 'turnIconSpeechOf(frame.iconType)' 'frame.iconType.toString()'
ctl N-y "页面不再查转向码表（1→0）"


# ---- 坐标系豁免（L-b/L-b2）：第 8 步把高德折点搬进引擎，豁免必须"按数量封顶"而不是"按文件放行" ----
# 这一组是本轮实测出来的教训：第一版豁免只写了 `grep -v AmapNavigationEngine.kt`，
# 结果在豁免文件里再加一处 Geo(p.latitude, …) 判据仍然绿（rc=0）—— 等于把"出口唯一"换成"信这个文件"。

# L-b：豁免文件里多搬一处（应被 L-b2 的数量上限抓住）
ins "$AM" "private fun instance()" '    private fun extra(p: com.amap.api.navi.model.NaviLatLng) = Geo(p.latitude, p.longitude)'
ctl_loc L-b2 "豁免文件里多出第二处坐标搬运"

# L-b：豁免那一处被删掉（豁免已无必要，必须重新核归属）
sub "$AM" 'coordList.orEmpty().map { Geo(it.latitude, it.longitude) }' 'coordList.orEmpty().map { Geo(0.0, 0.0) }'
ctl_loc L-b2 "唯一的 SDK 折点搬运不见了"

# L-b：页面绕开判据层自己抄 SDK 折点（豁免扩到第二个文件）
ins "$UI" 'import com.shanxiang.buspost.core.map.RouteCheck' 'private fun bad(p: com.amap.api.navi.model.NaviLatLng) = Geo(p.latitude, p.longitude)'
ctl_loc L-b "豁免扩大到判据层之外"

# ---- L-f / L-f2（第二定位源，2026-10-10 重锚那一族的出生证明）----
# 旧判据按裸类名 `AMapLocationClient` 数，本轮实测 5 处命中全部来自 AmapCompliance.kt 的
# 静态隐私合规调用（javap 已逐签名核对：那两个类我们用的只有 static 方法）⇒ 它是**恒红的误报**，
# 而一条恒红的判据和一条没判据的判据一样危险：真破口混在里面没人看得见。
# ⚠ 这一族此前**没有任何负控**（`grep -n 'L-f' 全部负控簿` 实测 0 命中），所以先把三条补上再谈重锚可信。
AC="$A/core/navigation/AmapCompliance.kt"

# LF-a：真的起一次高德定位（实例化 + startLocation）⇒ L-f 必须红
ins "$AC" '    fun apply(context: Context) {' '    private fun secondSource(c: Context) = AMapLocationClient(c).also { it.startLocation() }'
ctl_loc L-f "真的实例化 AMapLocationClient 并 startLocation：第二个坐标源进来了"

# LF-b：只 import 该包里的另一个类（不触发 L-f 的三种形状）⇒ 单独钉 L-f2 这条新收紧的豁免边界
sub "$UI" 'import com.shanxiang.buspost.core.map.RouteCheck' 'import com.amap.api.location.AMapLocation'
ctl_loc L-f2 "第二本文件 import com.amap.api.location：豁免被扩大（这一条不该同时打红 L-f）"

# LF-keep：合规那一本原样在场时必须仍是绿的（否则上面两个 0 是"判据恒红"读出来的）
ctl_loc_keep L-f "合规文件原样在场：静态隐私开关不算第二定位源（证 L-f 的 0 不是恒红）"

# ---- L-v（地名硬编码，2026-10-08 设备实测那一族）----
# 成因不是读源码猜的：把 mock 定位打到加州（37.42,-122.16），首页顶栏念出来的是"云山村"。
# 基线写 1 而不是 0（那 1 处是输入框的 `如：…` 示例，不是渲染值），所以两个方向都要能红：
# 把村名塞回去要红（涨），把合法那一处删掉也要红（跌）。只验涨的那一半，等于判据对"恰好"不敏感。
sub "$PF" 'get() = str("currentVillage")' 'get() = str("currentVillage", "云山村")'
ctl_loc L-v "硬编码村名回到 Prefs 的默认值（就是设备实测那条成因）"

# 2026-10-13 合并：那一句示例文案从三个页面重抄收成 `SendScreen.kt` 里一个 `ADDRESS_EXAMPLE_HINT`
# （L-v 的"恰好 1"因此仍是 1，但**所在文件变了**）⇒ 注入点跟着搬到定义处，删掉定义才真的跌到 0。
dropline "$SD" 'const val ADDRESS_EXAMPLE_HINT = "如：云山村 3 组'
ctl_loc L-v "唯一合法的那 1 处（输入框示例的定义）被删，计数跌到 0"

# ---- L-w（逆地理名的 g 空值闸门，设备复拍 17:09 那一族的另一半）----
# 顶栏那句"位置未取到"只有在**没有真 fix** 时才对；一旦把闸门拆掉，没定位时县城垫子（29.5325,106.5765）
# 查出来的区县名会被无条件写进 locationName，念起来就像用户在那儿 —— 与 L-v 同族，只是生产者从"硬编码字符串"换成了"自造坐标的查询结果"。
# 注入锚点 2026-10-09 重锚：闸门已从赋值行本身搬到了包住它的那一层（`if (g == null) { 清空 } else { …赋值… }`），
# 旧锚 `if (!locationManual && g != null) locationName = rg.displayName` 在今天的源码里是 **0 命中**
# （sub 会 ANCHOR-MISS 空跑，ctl_loc 于是念成"负控制失效"——本轮抓到的就是这个洞，见 §八 ㊼）。
# ① 撤掉真闸门：那层判断换成常量，赋值改由 else 支无条件走到。
# 2026-10-13 合并后 `if (g == null) {` 在 HomeScreen 里成了**两处**（:172 是 16 格缩进的附近公交块、
# :197 才是护着 `locationName = rg.displayName` 的那一层），`sub` 只替第一处 ⇒
# 不带缩进的老锚点打到的正是**不相干那一处**，判据照旧绿，负控念成"失效（exit 0）"。
# ⇒ 锚点带上缩进（两种缩进各恰好 1 处，实测），破口才落在被判的那一行上。
# ⚠ 第二层坑（本轮实测）：`sub` 是**子串**替换，`            if (g == null) {`（12 格）同时是
#   `                if (g == null) {`（16 格）的尾串 ⇒ 只带缩进仍会先打到 :172 那一行的尾巴，
#   破口落在不相干的那一层，判据照旧绿 ⇒ 念成"负控制失效"。锚点必须**跨到上一行**才定得住身。
sub "$HM" '            }
            if (g == null) {' '            }
            if (false) {'
ctl_loc L-w "撤掉 g != null 闸门：没定位时也把垫子坐标查出的地名当用户位置念"

# ② 真闸门换成**一句尾注释**（代码里是常量 `true`，`g == null` 只剩注释里那几个字）：这一条专门验新判据剔注释那一步不是多余的。
#    不剔注释的写法在这里会读成"有闸门"⇒ exit 0 ⇒ 假绿；判据簿自己声明的那个洞只有这条负控能证它已闭合。
#    （注入文本**不能**以 `//` 开头：Git Bash 会把 argv 开头的 `//` 折成单个 `/`，注进去的那行就不是注释了，
#      本轮实测被这个坑跳过一次 ⇒ 改成"常量条件 + 尾注释"，两样都还在。）
sub "$HM" '            }
            if (g == null) {' '            }
            if (true) { // g == null 已在上面判过'
ctl_loc L-w "闸门只写在注释里、代码是常量条件：判据若按文本找 `g == null` 就会被这句话骗过去"

# ③④ 反证（必须不红）：新判据把"闸门在赋值行本身"与"闸门在包住赋值的那一层"都算合法，
#     于是这两条不是破口。本轮实测这两条各返回 exit 0，但它们当时被送进了"期待 exit 1"的 run_one，
#     读数被打成 ✘ 负控制失效（那是**驱动的标签错**，判据本身没错）——登记进 §八 ㊼。
#     这里用专门的 ctl_loc_keep 把它们钉在正确的一侧：以后判据若退化成只认一种形状，这两条会真红。
# 2026-10-13 合并：赋值行本身被改成 `if (rg != null && !locationManual) locationName = rg.displayName`
#     ⇒ 旧锚点（`if (!locationManual) locationName = …`）0 命中。反证仍然是同一件事：把它换成"闸门写在赋值行"的形状，判据必须不红。
sub "$HM" 'if (rg != null && !locationManual) locationName = rg.displayName' 'if (!locationManual && g != null) locationName = rg.displayName'
ctl_loc_keep L-w "旧写法（闸门写在赋值行本身）仍算合法：判据若只认外层块就永远红"
sub "$HM" '            }
            if (g == null) {' '            }
            if (g == null) {'
ctl_loc_keep L-w "原样副本：证明上面两条红不是判据恒红"

# ---- L-x（村庄选择器空态，撤掉硬编码填充表之后露出的同形缺口）----
# 两个方向都要红：同一句空态被复制到第二处（涨），和空态文案没了、对话框又退回只剩标题 + 关闭（跌）。
ins "$HM" 'names.take(12).forEach { name ->' '                        Text("暂无可选村庄：接口未返回站点", color = t.textSecondary, fontSize = (13 * scale).sp)'
ctl_loc L-x "同一句空态被复制进第二处（两处话术会各自漂移）"

# ⚠ D-082 空转闸门重锚：这句空态在 8387929 之后只剩「暂无可选村庄」四个字（"接口未返回站点"那半句
#   被当成技术术语清掉了），旧锚点带着那半句 ⇒ dropline 静默落空，LX 一直是 1。
dropline "$HM" '"暂无可选村庄",'
ctl_loc L-x "空态文案被删：后端没给站点时对话框又变回空白"

# ---- N-3（赛道 ⑩：真实车道信息）的 N-z 一族 ----
# 与 N-y 同一条破口形状：表还是那张表、页面还在、编译也不报错，但有的车道码没人念了、
# 有的数组被配成了对（司机开错道）、有的"高德没给"被念成"这条路没车道"。每条只动一处。

# N-z1：名字表砍掉一个 javap 实测存在的码（25=TIDAL 由 `javap -constants LaneAction` 读到）
dropline "$NE" '    25 -> "LANE_ACTION_TIDAL"'
ctl N-z "车道名字表少一行（27→26）：高德给了潮汐车道，页面念成表外"

# N-z1：名字表凭空多一条（26 在 10.0.600 里不存在，27..254 也不存在）
ins "$NE" '    25 -> "LANE_ACTION_TIDAL"' '    26 -> "LANE_ACTION_NOT_IN_JAVAP"'
ctl N-z "车道名字表多一行（27→28）：给不存在的码编名字"

# N-z6：话术表把解释不了的那五条硬翻出来（13=LEFT_IN_AHEAD：in 指什么没有材料）
ins "$NE" '    16 -> "直行或左转或左掉头"' '    13 -> "直行加左转"'
ctl N-z "车道话术表多一行（22→23）：本机替高德解释了 LEFT_IN"

# N-z6：话术表砍一行（25=潮汐车道）
dropline "$NE" '    25 -> "潮汐车道"'
ctl N-z "车道话术表少一行（22→21）"

# N-z21：表外那一格被换成一句人话 —— 三张表行数一个都不掉，只有这句字面能抓
sub "$NE" 'LaneAction 表里没有的车道码' '直行'
ctl N-z "LaneAction 表外的码被念成一条真车道（表外那一格 1→0）"

# N-z6：话术整块没了（NZ6 从 22 掉到 0；与"表被砍一行"是两种破口，砍一行由上面那条验）
drop "$NE" 'internal fun laneActionSpeechOf'
ctl N-z "车道话术表整块没了（22→0）"

# N-z8：单组那个重载没人接了（高德走哪条回调不由本机决定）
dropline "$AM" 'override fun showLaneInfo(info: AMapLaneInfo?)'
ctl N-z "showLaneInfo 只接了一个重载（2→1）"

# N-z9：hideLaneInfo 不接 —— 高德已经不显示车道，页面会留住上一组
dropline "$AM" 'override fun hideLaneInfo()'
ctl N-z "hideLaneInfo 没接（1→0）"

# N-z10：引擎里多出第五个车道帧写入点（本机造帧）
ins "$AM" "private fun instance()" '    private fun negCtlLane() { laneState.value = LaneDisplay.Single(LaneFrame(3, listOf(0), listOf(0))) }'
ctl N-z "车道帧写入点不再是那四处（4→5）"

# N-z11：把"高德说已经不显示"降级成"高德没给"（四格并成两格的真实退化形状）
sub "$AM" 'laneState.value = LaneDisplay.Cleared' 'laneState.value = null'
ctl N-z "Cleared 不再只由 hideLaneInfo 写（1→0）"

# N-z13：数组重载被 first() 塌成单组
ins "$AM" 'val groupCount = infos?.size ?: 0' '                infos?.first()?.let { laneState.value = LaneDisplay.Single(laneFrameOf(it.laneCount, it.backgroundLane, it.frontLane)) }'
ctl N-z "数组重载被 first() 冒充单组"

# N-z14：两个数组被按下标配对（frontLane[i] 是不是 backgroundLane[i] 的标记，没有任何证据）
ins "$NE" 'internal fun laneCellLabel' 'fun negCtlPair(f: LaneFrame): List<Pair<Int, Int>> = f.backgroundCodes.zip(f.frontCodes)'
ctl N-z "两个车道数组被 zip 配成同一条道（0→1）"

# N-z15：页面不再订阅 lane flow
sub "$UI" 'val sdkLane by engine.lane.collectAsStateWithLifecycle()' 'val sdkLane: LaneDisplay? = null'
ctl N-z "页面不再订阅 lane flow（1→0）"

# N-z16：页面自己造车道帧 / 自己查码表（第二个话术出口）
ins "$UI" 'val sdkLane by engine.lane.collectAsStateWithLifecycle()' '    private val negCtlFrame = LaneFrame(3, listOf(0), listOf(1))'
ctl N-z "正式导航面自己造车道帧（0→1）"

# N-z18：「我的」里那个车道开关成了装饰
sub "$UI" 'if (!Prefs.laneGuidance)' 'if (false)'
ctl N-z "正式面不再读车道开关（1→0）"

# N-z19：旧屏那台按名字猜图形的 laneIcon 进了正式面
ins "$UI" 'val sdkLane by engine.lane.collectAsStateWithLifecycle()' '    private fun laneIcon(name: String) = 0'
ctl N-z "猜图形的那台表进了正式面（0→1）"

# N-z20：车道改成函数调用去问（页面只能轮询）
sub "$NE" 'val lane: StateFlow<LaneDisplay?>' 'fun laneSnapshot(): LaneDisplay?'
ctl N-z "车道不再由 flow 承载（2→1）"

# N-z3：话术唯一出口整块没了
drop "$NE" 'internal fun laneDisplayTextOf'
ctl N-z "车道话术唯一出口没了（1→0）"

# ---- N-4（赛道 ⑪：偏航重算只能由导航引擎触发）的 N-aa 一族 ----
# 这一族的破口形状与 N-y/N-z 同一类：五格还在、页面还在、编译也不报错，但"高德要重算"这句话
# 开始由本机说（自己算垂距、自己调 reCalculateRoute、把 notify 当偏航通道），或者两句广播被并成一句、
# 回执被配成"那次偏航成功了"。每条只动一处；且都点名到具体那条 fail 消息（$1 用完整前缀），
# 免得隔壁 N-aa 变红也被读成"这条破口被抓到了"。

# N-aa1：整族类型没了（本机重新回到"偏航只能自己猜"的起点）
drop "$NE" 'sealed class EngineRecalcDisplay'
ctl "N-aa 「高德自己发起的重算」这个类型不在了" "重算那一族整块没了"

# N-aa2：话术唯一出口整块没了（页面开始自己拼第二套说法）
drop "$NE" 'internal fun engineRecalcTextOf'
ctl "N-aa 重算话术不再是唯一出口" "话术出口整块没了（1→0）"

# N-aa3：两条广播并成一格（把拥堵说成偏航，司机会去检查一条自己没走过的错）
dropline "$NE" '    data object TrafficJam : EngineRecalcDisplay()'
ctl "N-aa 重算非空格数不是 4" "拥堵那一格被并进偏航（4→3）"

# N-aa4：话术表的分支少一格（类成员还在，出口不再念它）
dropline "$NE" '    EngineRecalcDisplay.TrafficJam ->'
ctl "N-aa 话术函数的分支数不是 5" "话术分支少一格（5→4）"

# N-aa5：重算改成函数调用去问（页面只能轮询）
sub "$NE" 'val recalc: StateFlow<EngineRecalcDisplay?>' 'fun recalcSnapshot(): EngineRecalcDisplay?'
ctl "N-aa 重算不再由 flow 承载" "重算不再由 flow 承载（2→1）"

# N-aa6：null 那一格被换成一句人话 —— 分支数还是 5，只有这句字面能抓
sub "$NE" '本机不拿距离卡阈值判偏航' '当前没有偏航'
ctl "N-aa null 那一格的字面没了" "「高德没说过」被念成「没偏航」"

# N-aa7：偏航广播没人接（司机偏了路，界面一个字都不动）
dropline "$AM" 'override fun onReCalculateRouteForYaw() {'
ctl "N-aa 高德的偏航广播没接" "onReCalculateRouteForYaw 没接（1→0）"

# N-aa8：拥堵广播没人接（两条并成一条的真实退化形状）
dropline "$AM" 'override fun onReCalculateRouteForTrafficJam()'
ctl "N-aa 高德的拥堵重算广播没接" "onReCalculateRouteForTrafficJam 没接（1→0）"

# N-aa9：偏航广播只写帧、不作废凭证（下一次 startNavi 拿旧指纹比一条高德已重算的路）
sub "$AM" $'acceptedRoute = null\n                recalcState.value = EngineRecalcDisplay.Yaw' $'recalcState.value = EngineRecalcDisplay.Yaw'
ctl "N-aa 偏航广播没作废凭证" "偏航广播不作废 §九 凭证（1→0）"

# N-aa10：同上，拥堵那一条不作废（只有一条负责作废 = 另一条能带着旧凭证走下去）
sub "$AM" $'acceptedRoute = null\n                recalcState.value = EngineRecalcDisplay.TrafficJam' $'recalcState.value = EngineRecalcDisplay.TrafficJam'
ctl "N-aa 拥堵广播没作废凭证" "拥堵广播不作废 §九 凭证（1→0）"

# N-aa11：引擎里多出第六个重算帧写入点（本机造"高德要重算"这句话）
ins "$AM" "private fun instance()" '    private fun negCtlRecalc() { recalcState.value = EngineRecalcDisplay.Yaw }'
ctl "N-aa 重算帧写入点不是 5" "重算帧写入点 5→6"

# N-aa12：停止导航不清重算帧（留住上一次会话那句）
dropline "$AM" 'recalcState.value = null'
ctl "N-aa 停止导航不再清重算帧" "stopNavi 不清重算帧（1→0）"

# N-aa13：成功帧被本机分了档（那个档位没有任何来源）
sub "$AM" 'recalcState.value = EngineRecalcDisplay.EngineInitiatedSuccess(result?.errorCode ?: -1)' 'recalcState.value = EngineRecalcDisplay.EngineInitiatedSuccess(if ((result?.errorCode ?: -1) == 0) 1 else 2)'
ctl "N-aa 成功帧不再照抄高德的 errorCode" "回执的 code 被本机重新分档"

# N-aa14：失败回执被并进广播那一格（配因果的形状）
sub "$AM" 'recalcState.value = EngineRecalcDisplay.EngineInitiatedFailure(code)' 'recalcState.value = EngineRecalcDisplay.Yaw'
ctl "N-aa 失败帧写法变了" "失败回执被念成「高德说偏航了」"

# N-aa15：本机自己要求重算（把"何时重算"的决定权从高德手里抢过来）
ins "$AM" "private fun instance()" '    private fun negCtlYawRecalc() { instance()?.reCalculateRoute(1) }'
ctl "N-aa 本机开始自己要求重算/选路" "本机调 reCalculateRoute（0→1）"

# N-aa16：把 onNaviRouteNotify 当偏航通道（那份枚举实测七条里没有偏航这一类）
ins "$AM" "private fun instance()" '    private fun negCtlNotify(d: com.amap.api.navi.model.AMapNaviRouteNotifyData?) { recalcState.value = EngineRecalcDisplay.Yaw }'
ctl "N-aa 把 onNaviRouteNotify/平行路当偏航通道" "notify 被当偏航通道（0→1）"

# N-aa17：判据层开始算垂距（阈值判定的第一步就是先算一个距离）
ins "$NE" 'internal fun engineRecalcTextOf' 'fun crossTrackMetersForYaw(p: List<Geo>): Int = p.size'
ctl "N-aa 引擎层出现垂距" "引擎层出现 crossTrack（0→1）"

# N-aa18：页面不再订阅 recalc flow
sub "$UI" 'val sdkRecalc by engine.recalc.collectAsStateWithLifecycle()' 'val sdkRecalc: EngineRecalcDisplay? = null'
ctl "N-aa 页面不再订阅 recalc flow" "页面重算状态被换成本机读数（1→0）"

# N-aa19：页面不走那一句唯一话术（自己拼句子）
sub "$UI" 'InfoRow("重算", engineRecalcTextOf(sdkRecalc))' 'InfoRow("重算", if (sdkRecalc == null) "没有偏航" else "高德要重算")'
ctl "N-aa 页面不再走那一句唯一话术" "重算那一格开始本机拼句子（1→0）"

# N-aa20：页面自己造重算帧（第二个"高德说要重算"的来源）
ins "$UI" 'val sdkRecalc by engine.recalc.collectAsStateWithLifecycle()' '    private val negCtlRecalcCell = EngineRecalcDisplay.Yaw'
ctl "N-aa 页面自己造重算帧" "正式面自己造重算帧（0→1）"

# N-aa21：正式导航面出现本机那句"已偏离线路"
ins "$UI" 'InfoRow("重算", engineRecalcTextOf(sdkRecalc))' '                InfoRow("本机结论", "已偏离线路")'
ctl "N-aa 正式导航面出现本机的「已偏离线路」结论" "本机宣告偏航（0→1）"

# N-aa22：旧屏把垂距比较当成一个读数条件（阈值判定的形状回来了）
ins "$NS" 'val offRoute = roadOk && g.deviated' 'val nearRoute = vm.crossTrackMeters() < 200'
ctl "N-aa 司机目录又出现垂距阈值比较" "垂距被拿去做比较（0→1）"

# N-aa23：旧写法回来 —— 后端那个位与本机阈值 OR 在一起
sub "$NS" 'val offRoute = roadOk && g.deviated' 'val offRoute = roadOk && (g.deviated || vm.crossTrackMeters() > 60)'
ctl "N-aa 旧屏偏航不再只读后端那个位" "本地假判定与后端位 OR 在一起（1→0）"

# N-aa24：界面开始用"本机判定偏航"这句话
ins "$NS" 'val offRoute = roadOk && g.deviated' '            Text("本机判定偏航")'
ctl "N-aa 全工程又出现「本机判定偏航」的措辞" "本机宣告偏航的措辞回来（0→1）"

# ---- N-8（赛道 ⑯：四行口径与排版复核）的 N-ac 一族 ----
# 这一族的破口都不改任何逻辑：只把"谁在说话"换掉。开关关着时那一格最顺手的写法
# 恰好是越界的写法（"本机没显示高德给的车道"——那半句高德没说过），所以它必须由字面钉住，
# 而不能指望用例：句子读起来都对，错的是它替谁说了话。
# ⚠ 页面这一半的改动会同时打到几个计数器（同一段文字被 NAC2/NAC4/NAC5/NAC8/NAC10 从不同角度看），
#   所以下面每条 ctl 都写明"必须出现的正是哪一句 ×"，co-fire 的那些是同一处缺陷的另一个读数，不是误抓。

# N-ac1：纯层那个出口被删掉 —— 页面没有句子可走了，第二套说法就有了空位
dropline "$NE" 'internal fun guidanceSwitchOffText('
ctl "N-ac 开关关掉那一格不再是纯层唯一出口" "话术出口从纯层消失（1→0）"

# N-ac2：车道那一行回到"就地拼 + 替高德宣告给过车道"的旧字面
sub "$UI" 'guidanceSwitchOffText("车道级指引", "这一格不显示车道")' '"车道引导开关是关的（在「我的」里），本机没显示高德给的车道"'
ctl "N-ac 开关关着那一格又开始替高德宣告" "旧那句越界的字面回潮（NAC5 0→1，NAC2/NAC4/NAC8/NAC10 同时红）"

# N-ac3：播报那一行不走出口，页面自己留一句字面（出口还在，但这一格不再受它约束）
# ⚠ D-082 空转闸门重锚：这一行的第二参数现在是「语音播报已关闭」（旧锚点那句已被话术改写），注入静默落空。
sub "$UI" 'guidanceSwitchOffText("语音播报", "语音播报已关闭")' '"本机没让高德播报"'
ctl "N-ac 页面调用开关话术的次数不是 3" "播报那一格绕开出口（NAC2 3→2，NAC9 同红）"

# N-ac4：出口内部开始替高德说话 —— 两条开关句一起被改成"高德没给"，页面一个字都没动
sub "$NE" '开关是关的（在「我的」里），本机' '开关是关的（在「我的」里），高德没给，本机'
ctl "N-ac 开关话术出口内部替高德说了" "「本机没做」被写成「高德没给」（NAC6 0→1）"

# N-ac5：车道那一格把开关名指到旧调试屏那台（「我的」里没有"车道引导"这一行）
sub "$UI" 'guidanceSwitchOffText("车道级指引"' 'guidanceSwitchOffText("车道引导"'
ctl "N-ac 车道那一格不再点名「我的」里的「车道级指引」" "开关名指错（NAC8 1→0，NAC10 同红）"

# N-ac6：播报的 label 又被动态拼接（类型码进标签，这一屏的标签宽窄开始随回执变）
sub "$UI" '"播报",' '"播报" + "",'
ctl "N-ac 播报的 label 又被动态拼接" "静态 label 被换成拼接（NAC11 0→1）"

# N-ac7：四行里少一行 —— 播报那一格改成不点名，句子还在但没人知道说的是哪条开关
# ⚠ $1 必须是 NAC7 那句 × 的**原文前缀**：上一版写成"N-ac 四行不再各走自己的那一句"，
#   实际抓到的却是"N-ac 转向/车道/重算/播报四行不再各走自己的那一句"（现 3），ctl 的 grep 打不中
#   就记成 △"被别的判据误抓"（2026-10-06 实测）——那是簿子自己的字面错位，不是判据抓错。
sub "$UI" 'guidanceSwitchOffText("语音播报",' 'guidanceSwitchOffText(negCtlSwitchName,'
ctl "N-ac 转向/车道/重算/播报四行不再各走自己的那一句" "播报那一格不再点名（NAC7 4→3，NAC9 同红）"

# N-ac8：第三条开关行（到近作业播报）绕开纯层出口，页面自己拼那句骨架
# 这一条是 #308/#309 卡片重排把 NAC2 由 2 抬到 3 时**同轮补的**：抬数不补破口，等于新那一行没人验过。
# 破口一发生就三个读数一起红（NAC2 3→2、NAC4 0→1、NAC12 1→0），这里认的是 NAC12 那一句。
sub "$UI" 'if (!Prefs.approachVoice) guidanceSwitchOffText("到近作业播报", "不会读作业提醒")' 'if (!Prefs.approachVoice) "到近播报开关是关的（在「我的」里），本机没读作业提醒"'
ctl "N-ac 到近作业那一格不再点名「我的」里的「到近作业播报」" "第三条开关行绕开出口、页面自己写那句骨架（NAC12 1→0，NAC2/NAC4 同红）"

# N-ac9：「我的」里那一行改了名，卡片没跟着改 —— NAC12/NAC13 这对就是钉"两边逐字相等"的，
# 只改一边必须红；两边同时改（真的改名）判据抓不到，那是字面锚定的固有边界，登记时写明。
sub "$MP" 'SettingRow("到近作业播报"' 'SettingRow("到近提醒"'
ctl "N-ac「我的」里「到近作业播报」那一行的字面变了而卡片没跟着改" "卡片指过去的那条开关在「我的」里不存在（NAC13 1→0）"

# ── N-rm 到车持续提醒（D-170）：六条各拆一格，外加一条"正对照必须也红" ──────────────
AR="$A/ui/driver/ApproachReminder.kt"
AAC="$A/ui/driver/ApproachAnnouncer.kt"
GD2="$A/ui/driver/DriverGuideScreen.kt"
for f in "$AR" "$AAC" "$GD2"; do
  [ -f "$f" ] || { echo "✘ 沙盒缺 $f：N-rm 这一族的注入没有落点，本批不采信"; exit 2; }
done

# ⚠ `ctl` 的第一个参数是**判据红行的前缀**（它拿 `^× $1` 去核"红的是不是这一格"），第二个才是给人看的说明。
#   第一轮我把两者写反了：八条里六条 exit 1、报的也正是这一格，却因为前缀对不上被判成 △「被别的判据误抓」。
#   △ 不是通过 —— 这一族的计数只有 ✔ 才算证过。
sub "$AR" '    fun stopOf(' '    fun stopOfRenamed('
ctl "N-rm 「要不要提醒」的判点不再是恰好一份" "判点被改名（没有判点，或有人另起一份）"

sub "$GD2" 'val near = ApproachReminder.stopOf(feed)' ''
ctl "N-rm 读这个判点的页面不是两处" "全屏导航那一屏不再读判点"

sub "$AR" 'class ReminderTracker {' 'class ReminderTrackerX {'
ctl "N-rm 重复出声的账本不是恰好一份" "账本被改名（类名与调用点会各留一份）"

sub "$AR" 'const val MAX_REPEATS = 3' ''
ctl "N-rm 间隔/上限常数不是各一份" "上限常数被摘掉（节奏回到页面里写死）"

ins "$AAC" "private fun deliver" '    private val nextAtMs = lastAtMs + REPEAT_INTERVAL_MS'
ctl "N-rm 出声出口开始自己算时间/记账" "出口层偷算时间"

sub "$AAC" 'fun remind(text: String)' 'fun remindX(text: String)'
ctl "N-rm 重复出声的调用/出口不是 2/1" "重复出声的出口被改名"

ins "$WB" '    val reminderStop = remember(vm.guide) {' '    val reminderDialog = AlertDialog(onDismissRequest = {}) { Text("到车提醒") }'
ctl "N-rm 提醒被做成了弹窗" "模态弹窗吃掉地图手势与返回键"

# 最后这条是**反证**：把工作台那一行改回只念距离，正对照格必须红 ——
# 否则上面那两个"恰好 2"可能是扫描根本看不见这一屏（D-082 那批空转闸门的同族）。
sub "$WB" 'reminder ?: distanceText' 'distanceText'
ctl "N-rm 工作台那一行不再读提醒" "正对照失效：扫描看不见这一屏时上面那些读数不采信"

# ---------- N-wd（D-177）：司机导航卡那三族内部原文 ----------
# 这一族的破口有两种方向，各配一条：把原文**念回屏上**（那一格又变回字段名），
# 以及把原文**连同日志一起删掉**（屏上干净了，装机的人却再没处查）。
CMP2="$A/core/ui/Components.kt"
[ -f "$CMP2" ] || { echo "✘ 副本缺 $CMP2，N-wd 无法负控"; exit 2; }

sub "$UI" 'if (!gate.canNavigate && gate.hint.isNotEmpty()) InfoRow("下一步该做什么", gate.hint)' \
           'if (!gate.canNavigate) InfoRow("差在哪一步", gate.detail)'
ctl "N-wd 卡片读 hint 的次数不是 1" "旧那一格把诊断原文念回屏上了"

sub "$UI" 'android.util.Log.w("BUS_NAV", "route_check rejected: ${check.reason} | ${check.detail}")' 'Unit'
ctl "N-wd 算路被拒的原文进日志不是两处" "屏上撤了、日志也没接住（撤措辞顺手撤证据）"

sub "$UI" '                            Text(
                                check.reason,' \
           '                            Text(
                                check.reason + "（${check.detail}）",'
ctl "N-wd 又有屏上那句把 detail 拼回去了" "面向日志的那一半被拼进给用户看的那一句"

sub "$CMP2" '        else -> "来源没认出来"' '        else -> source'
ctl "N-wd 来源标签退回把原词印上屏" "认不出的枚举原样上屏"

sub "$NE" '            hint = "到窗边或开阔处站一下，或下拉刷新位置；拿到点就能算。不拿终点或默认位置替你顶一个起点。",' ''
ctl "N-wd 引擎里「下一步该做什么」那一句不再是 8 处" "某个不可点码没话可说（只报不行、不说下一步）"

sub "$NE" '        detail = "sdkPresent=false",' '        detail = "",'
ctl "N-wd 引擎原文被删了" "屏上撤词的同时把证据也扔了"

echo "---"
echo "负控制跑完：上面每行都必须是 ✔（△ 说明这条破口被别的判据误抓，✘ 说明这条判据根本不判）"
# ⚠ 退出码补登记（D-082）：这一本原来 ✘=13 仍然 exit 0 —— "批跑完了"与"闸门全是摆设"在退出码上分不出来，
# 全靠批次脚本自己再数一遍 ✘。现在 ✘ 直接进退出码（ BAD 就是本行上面那些 ✘ 的条数，只准 0）。
echo "[聚合] 本簿 ✘ 条数 = $BAD（预期 0）"
[ "$BAD" = 0 ] || exit 1
