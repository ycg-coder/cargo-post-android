#!/usr/bin/env bash
# 第 8 步 高德导航引擎判据（指令 v2.0 §39/§40 与硬规则"宁可暂时不能导航，不允许假导航"；DECISION_LOG D-031 + D-003；审计 §77/§78）
# 用法：bash tools/check_navigation_probes.sh      退出码非 0 = 导航引擎的契约破了
#
# N-a…N-l 钉引擎本体（#49/N-1 那轮），N-m…N-p 钉"旧屏降级"这一半（#51），
# N-q…N-t 钉"导航入口只有一个结论"这一半（F-3 ④⑫ / D-048）：
# 假步进归零、旧屏在正式包不可达、标注诚实、正式导航面只此一处、可点性不读构建类型。
#
# 分工：判据层行为由 app/src/test/.../core/navigation/NavigationContractTest.kt 钉（45 条用例，纯 JVM）；
# 这里钉**结构** —— 工程没有 Robolectric，`AmapNavigationEngine` 那一层调不到 SDK（要真机），
# 所以只能按内容复核："有没有人把定时器当推进""有没有人绕过那唯一一道真实道路闸门"
# "有没有人在引擎里做二次坐标换算""依赖有没有从顶替退化成并存"。
# 写法沿用既有教训：先验文件存在；按内容过滤注释行；计数钉**生效代码的确切字面量**而不是关键字出现次数；
# 下限用 -ge，加用例与抬下限同一轮。每条都有负控制：bash tools/navigation_negative_controls.sh
#
# 分工补记（F-3 ⑭/⑰）：N-u…N-w 钉"startNavi 之前必须先绑定刚验过的那条"这一半（V21-09），
# N-x 只登记路线缓存"有没有人真的从里面取路线"这一半（V21-13/14 本轮只买读得到/读不到，不买 TTL）。
# 分工补记（N-2 / 赛道 ⑨）：N-y 一族钉"转向话术与播报"这一半 —— 表的值域是 javap 读来的（57+6 行，
# 少一行是把"高德给了"念成"没给"，多一行是给表外的码编动作），播报帧只能来自 onGetNavigationText，
# 正式导航面一台本机 TTS 都不许有；「我的」里那个语音开关必须真的交给高德，否则它是装饰。
# 分工补记（N-3 / 赛道 ⑩）：N-z 一族钉"车道信息"这一半 —— 车道码表的值域是 javap 从 LaneAction 读来的
# （名字表 27 行 / 话术表 22 行：话术表**故意**少五条，那五条翻不出有证据的中文），车道帧只能来自
# showLaneInfo/hideLaneInfo；两个数组不许被按下标配对（zip 一出现就是把没核过的假设交给司机开车道），
# 数组重载不许塌成单组；「我的」里那个车道开关在正式面必须真的读到，否则它和语音开关一样是装饰。
#
# 分工补记（N-4 / 赛道 ⑪）：N-aa 一族钉"偏航重算只能由导航引擎说"这一半（指令 v2.1 第 ⑪ 条明文
# "只能由导航引擎触发，不得本地假判定替代后端"；DECISION_LOG D-057）。值域同样是 javap 读来的：
# `AMapNaviListener.onReCalculateRouteForYaw()` 与 `onReCalculateRouteForTrafficJam()` **两条都是无入参的
# public abstract void** ⇒ 高德只说"它要重新算路"和原因，偏了多少米、往哪偏、这次重算成没成，本机一个字都没有。
# 另外两族**故意不接**（不是漏了）：`AMapNaviRouteNotifyDataType` 实测七条（1/2/3/4/5/20/21）里**没有偏航这一类**
# ⇒ `onNaviRouteNotify` 不是偏航通道；`ReCalculateRouteType`（含 ROUTE_TYPE_YAW=1）是 `reCalculateRoute(int)` 的**入参**
# 值域，调它等于本机替高德决定何时重算。算路回执不带原因、`AMapNavi` 又是进程单例 ⇒ "广播"与"后一次算路成功"
# 之间不许配成因果（配了就是替司机说"你那次偏航已经重算好了"）。
# 旧屏那一半：本机自己挑的垂距阈值必须归零 —— 偏航判定只有两个合法来源，后端的 `deviated` 位与高德的回调。
#
# 分工补记（N-8 / 赛道 ⑯）：N-ac 一族钉"⑨–⑭ 那四行进卡片之后还在说同一件事"这一半。
# 四行各有自己的成因（表外码 / 高德没推帧 / 高德没说过 / 司机自己关了开关），所以各有自己那一句；
# 唯独"司机关了开关"那一格最容易走样 —— 它最顺手的写法是把本机的不显示说成"高德给的车道我没显示"，
# 而那半句是高德没说过的。所以这一族钉：那句话只有一个出口、出口内部不替高德说话、
# 指出去的开关名与「我的」里那一行逐字相同、label 全是静态词。
# ⚠ 这一族**不钉**任何视觉量（字号、行距、maxLines 截断）：那些要在真机上看，本机没有可读的产物。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

SRC=app/src/main/java/com/shanxiang/buspost
NENG="$SRC/core/navigation/NavigationEngine.kt"
AM="$SRC/core/navigation/AmapNavigationEngine.kt"
ND="$SRC/core/navigation"
UI="$SRC/ui/driver/DriverSdkNavCard.kt"
MP="$SRC/ui/user/MinePages.kt"
NS="$SRC/ui/driver/DriverNavScreen.kt"
WB="$SRC/ui/driver/WorkbenchScreen.kt"
BR="$SRC/ui/BusPostRoot.kt"
DD="$SRC/ui/driver"
BG=app/build.gradle.kts
TP=app/src/test/java/com/shanxiang/buspost/core/navigation/NavigationContractTest.kt
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'

# 先验文件存在：grep 找不到文件时输出为空，`[ "" = 0 ]` 会把"根本没跑"读成"真的 0 命中"
for f in "$NENG" "$AM" "$UI" "$MP" "$NS" "$WB" "$BR" "$BG" "$TP"; do
    [ -f "$f" ] || { echo "× 判据文件缺失：$f（先确认工程根/副本建对，别把缺文件读成计数 0）"; exit 2; }
done

# N-a 用例条数：判据层行为的底盘，删用例必须让这里变红
# 34→37 是 N-2（赛道 ⑨）那轮实测抬的：新增 3 条转向码表/播报类型表用例（读 XML 计数，不是数 @Test 推的）
# 37→45 是 N-3（赛道 ⑩）这轮实测抬的：新增 8 条车道表/车道帧用例。
# 45→52 是 N-4（赛道 ⑪）这轮实测抬的：新增 7 条重算族用例（五格不并格 / 两条广播不互替 / 广播不带结局 /
# 回执不配因果 / null 那一格顺带声明本机不判偏航 / 这一族里没有"数字＋米" / 替身重算流恒空）。
# 底盘读数取自测试产物本身（TEST-...NavigationContractTest.xml：tests=52 skipped=0 failures=0 errors=0，
# 2026-10-05 13:12 那份产物，`./gradlew testDebugUnitTest` rc=0），
# 不是数 `@Test` 推出来的 —— 数注解只证明"写了"，不证明"跑过"。
# 52→55 是 N-8（赛道 ⑯）这轮实测抬的：新增 3 条开关句口径用例（不替高德宣告给过什么 / 开关名照抄设置页 /
# 两条开关句不互相套用机制）。⚠ 同一轮的 XML 读数 = tests=55 failures=0 errors=0（2026-10-06 14:17，rc=0）。
NA=$(grep -cE '^[[:space:]]*@Test' "$TP")

# N-b 推进只能来自回调：引擎目录里不许有 delay / Timer / Handler / 墙钟 / stepIndex + 1
# （§24 那 6 处假步进就是这个形态，新写的引擎第一天就按 0 钉死）
# 2026-10-10 补登记：`Handler(Looper.getMainLooper()).post` 是主线程桥接（把已到达的高德回调投给主线程），
# 不是定时器/假步进 —— 推进仍由高德回调触发，post 只换线程。按精确字面量排除，其他 Handler 形态照红。
NB=$(grep -rnE 'delay\(|Timer|Handler\(|System\.currentTimeMillis|stepIndex \+ 1' "$ND" --include=*.kt | grep -vF 'Handler(Looper.getMainLooper()).post' | grep -vE "$NC" | wc -l | tr -d ' ')

# N-c 坐标系纪律（审计 §17 二次偏移）：高德 `getCoordList()` 本身就是 GCJ-02，
# 引擎里再调 Locator.toGcj02 / CoordinateTransform 会把点偏第二次，整条线看上去像"司机偏航"
NCNT=$(grep -rnE 'toGcj02|CoordinateTransform' "$ND" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')

# N-d 真实来源：引擎不许产出直线/连线/模拟/演示来源的折线，也不许用 GeoMath 当里程
ND_=$(grep -rnE 'EUCLIDEAN|STRAIGHT|MOCK|DEMO|GeoMath' "$ND" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')

# N-e 同一道闸门只有一处：`RealRoadRouteValidator.validate` 在整个 core/navigation 只许出现在 naviRouteCheck 里；
# SDK 层不许另起一次 validate（两套判据 = 同一个问题两个答案，审计 §26 的老毛病）
# NE3 从 `return naviRouteCheck(` 改成 `naviRouteCheck(`（F-3 ⑭）：算路出口现在先接住结果再 `return check`
# （要把过闸那份几何记进 §九 的凭证），钉"调用次数为 1"才还在钉同一件事 —— 换锚点必须与改代码同一轮。
NE=$(grep -rn 'RealRoadRouteValidator\.validate(' "$ND" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
NE2=$(grep -c 'RealRoadRouteValidator\.validate(' "$AM")
NE3=$(grep -c 'naviRouteCheck(' "$AM")

# N-f 工厂只有两种返回：真引擎 / 替身，没有第三种（第三种历来就是"用直线假装在导航"）
NF=$(grep -c 'return BlockedNavigationEngine(' "$AM")
NF2=$(grep -c 'return AmapNavigationEngine(' "$AM")
NF3=$(grep -rn 'class .*: NavigationEngine' "$ND" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')

# N-g 生产调用点：引擎不是摆设 —— 页面真的创建它、真的调 calculateRoute/startGpsNavi、
# 真的在离开时 release；目的地只读 nextNavigableStop（硬规则 3：调度决定去哪里）
NG=$(grep -c 'NavigationEngines.create(context)' "$UI")
NG2=$(grep -n 'engine\.calculateRoute(' "$UI" | grep -vE "$NC" | wc -l | tr -d ' ')
# N-2（赛道 ⑨）把调用换成 `engine.startGpsNavi(Prefs.voiceGuidance)`：锚点同轮换掉右括号，
# 仍钉"页面真的开始导航一次"这一件事（旧锚点会因括号里的参数变成 0，那是判据失真不是契约破了）。
NG3=$(grep -n 'engine\.startGpsNavi(' "$UI" | grep -vE "$NC" | wc -l | tr -d ' ')
NG4=$(grep -c 'onDispose { engine.release() }' "$UI")
NG5=$(grep -n 'nextNavigableStop(guide)' "$UI" | grep -vE "$NC" | wc -l | tr -d ' ')
# #51：引擎卡片的挂载点从旧屏搬到工作台（正式导航面）。钉**恰好一处**：
# 多一处就是第二个 AMapNavi 实例、少一处就是整条链路没有生产调用点。
NG6=$(grep -n 'DriverSdkNavCard(' "$WB" | grep -vE "$NC" | wc -l | tr -d ' ')

# N-h 状态只能订阅不能轮询：readiness 是 StateFlow，页面用 collectAsStateWithLifecycle 读；
# 页面里出现 delay/Timer 去"刷状态"就是把本机节奏当成引擎节奏
NH=$(grep -c 'val readiness: StateFlow<EngineReadiness>' "$NENG")
NH2=$(grep -c 'collectAsStateWithLifecycle()' "$UI")
NH3=$(grep -rnE 'delay\(|Timer|Handler\(' "$UI" | grep -vE "$NC" | wc -l | tr -d ' ')

# N-i 依赖形态：navi-3dmap **顶替** 3dmap。两者并存 = duplicate class；
# 单独再引 com.amap.api:location 也是同一颗雷（navi-3dmap 自带 location 类）
NI=$(grep -c 'com.amap.api:navi-3dmap' "$BG")
NI2=$(grep -cE '"com\.amap\.api:3dmap:' "$BG")
NI3=$(grep -cE 'implementation\("com\.amap\.api:location' "$BG")

# N-j SDK 面只出现在引擎层：com.amap.api.navi.* 的 import 白名单 = 审计 §77 javap 逐个核对过的那些类名；
# 别处（页面/Repo）直接调 SDK 会绕开判据层，白名单外的类名一律算"没核对过的 API"。
# 9→10 是 N-3（赛道 ⑩）这轮抬的，同轮加的是 `AMapLaneInfo`：取证记录 = `javap -constants` 读到的
# `backgroundLane` / `frontLane` / `laneCount` 三个 public 成员，留档 /tmp/n4_lane_javap.txt（jar sha256 前缀 9fa3cfb8）。
# ⚠ 白名单与计数必须**同一轮**动：只抬计数不登记类名，等于把"没核对过的 API"合法化。
# 2026-10-10 白名单 10→12：AmapCompliance（隐私合规前置）新增 MapsInitializer / AMapLocationClient 两个类，
# javap 实测核对 4 个静态方法签名，取证 tools/fixtures/amap_compliance_javap.txt（jar sha256 前缀 9fa3cfb8）。
SDK_LIST='com\.amap\.api\.maps\.model\.LatLng|com\.amap\.api\.navi\.AMapNavi|com\.amap\.api\.navi\.SimpleNaviListener|com\.amap\.api\.navi\.enums\.NaviType|com\.amap\.api\.navi\.enums\.PathPlanningStrategy|com\.amap\.api\.navi\.model\.AMapCalcRouteResult|com\.amap\.api\.navi\.model\.AMapLaneInfo|com\.amap\.api\.navi\.model\.AMapNaviPath|com\.amap\.api\.navi\.model\.NaviInfo|com\.amap\.api\.navi\.model\.NaviPoi|com\.amap\.api\.maps\.MapsInitializer|com\.amap\.api\.location\.AMapLocationClient'
NJBAD=$(grep -rh '^import com\.amap' "$ND" --include=*.kt | grep -vE "^import ($SDK_LIST)$" | wc -l | tr -d ' ')
[ "$NJBAD" = 0 ] || grep -rh '^import com\.amap' "$ND" --include=*.kt | grep -vE "^import ($SDK_LIST)$"
NJ=$(grep -rh '^import com\.amap' "$ND" --include=*.kt | wc -l | tr -d ' ')
# 2026-10-10 在册例外：DriverGuideScreen（正式导航页）直接持有 AMapNaviView/Listener/Options/ShowMode 四个**视图**类 ——
# 视图渲染必须活在页面层，业务逻辑仍在引擎；其余页面出现 navi import 照红。
NJ2=$(grep -rn 'import com\.amap\.api\.navi' "$SRC" --include=*.kt | grep -v "/core/navigation/" | grep -v 'ui/driver/DriverGuideScreen.kt' | wc -l | tr -d ' ')

# N-k 措辞诚实：没给就说"高德没给"（不许用直线/上一帧补数），且页面不许本机宣布"导航成功/已开始导航"
# 2026-10-12 续七重锚 + 收紧：`8387929 全面清除界面技术术语` 把正式卡片上那五句"高德没给…"
#   改写成了统一的**「暂未知」**（`DriverSdkNavCard.kt:230/361/365/367/368/371`，实测非注释恰好 6 处）。
#   语义没变：这一屏的每个数都直接取自 `sdkFrame`（高德那一帧），"没给"只能是高德没给，
#   所以"不许拿直线值/上一帧补数"这条约束仍然由**同一个出口**守着；`高德没给` 这个字面量只剩 2 处注释。
#   闸门因此从「≥5 的旧字面量」改成「恰好 6 的（高德没给|暂未知）非注释命中」——
#   少一处=那一格又开始自己补数或整行不显示，多一处=有人新加了一格要说来源。
NK=$(grep -n '高德没给\|暂未知' "$UI" | grep -vE "$NC" | wc -l | tr -d ' ')
NK2=$(grep -cE '导航成功|已开始导航|正在导航中' "$UI")

# N-l 依赖换了，旧断言必须一起消失：全工程不许再写"无需地图 SDK / 无 key、无额外依赖"
NL=$(grep -rn '无需地图 SDK\|无 key、无额外依赖' app/src/main --include=*.kt | wc -l | tr -d ' ')

# ---- 以下 N-m…N-p 是 #51（旧 DriverNavScreen 转 DEBUG ONLY，DECISION_LOG D-003）新增 ----

# N-m 假步进：整个司机 UI 目录都不许有"定时器替车往前走"的形态（含注释行的裸口径同为本表的 0）。
# 口径从"只看 DriverNavScreen.kt 一个文件"抬到目录级，是为了堵"挪个文件就算归零"。
NM=$(grep -rnE 'stepping|delay\(4_000|stepIndex \+ 1' "$DD" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')

# N-n 旧屏在正式包不可达：每一条 `Routes.DRIVER_NAV` 的**使用**都必须自己那一行就带着 DEBUG 闸；
# 路由注册 = 1 条且带闸；旧屏文件内还有第二道 `if (!BuildConfig.DEBUG)` 自守（深链/残留入口进来看不到指引）。
NN=$(grep -rn 'Routes\.DRIVER_NAV' "$SRC" --include=*.kt | grep -v 'BuildConfig.DEBUG' | wc -l | tr -d ' ')
NN2=$(grep -c 'if (BuildConfig.DEBUG) composable(Routes.DRIVER_NAV)' "$BR")
NN3=$(grep -c 'if (!BuildConfig.DEBUG) {' "$NS")

# N-o 标注诚实：旧屏必须显著写着"非真实导航"，车道箭头那处要写明是猜的（D-003 的"显著标注"半边）
NO=$(grep -c '非真实导航' "$NS")
NO2=$(grep -c '箭头图形是按名字猜的' "$NS")  # 旧句「本页按名字猜的」被 8387929 改写，语义未变（DriverNavScreen.kt:497）

# N-p 正式导航面只此一处：旧屏不再建第二个引擎实例，外部地图交接收在正式卡片里
NP=$(grep -n 'DriverSdkNavCard(' "$NS" | grep -vE "$NC" | wc -l | tr -d ' ')
NP2=$(grep -c 'openAmap(context, stop.geo' "$UI")
NP3=$(grep -c 'openBaidu(context, stop.geo' "$UI")
NP4=$(grep -c 'fun openAmap' "$NS")

# ---- 以下 N-q…N-t 是 F-3 ④⑫（导航入口 readiness 闸门，DECISION_LOG D-048）新增 ----

# N-q 构建类型不是业务能力开关：正式导航入口（工作台那张卡片）的**生效行**里 BuildConfig 必须为 0。
# 与 N-n 不冲突：那 8 处 DEBUG 闸门钉的是"假导航屏只在调试包可达"，管的是可达性，不是"能不能导航"。
# 注释行里出现 BuildConfig 是解释性的（第 79 行那句"它不读 BuildConfig"），不过滤会让判据因为一句说明而变红。
NQ=$(grep -n 'BuildConfig' "$UI" | grep -vE "$NC" | wc -l | tr -d ' ')

# N-r 入口的可点性只有一个来源：页面调 navigationGateOf 一次，按钮 enabled 读 gate.canNavigate；
# 旧写法 `enabled = !blocked` 必须归零 —— 它把 Initializing 当可点，与判据层 canNavigate 给出两个答案。
NR=$(grep -n 'navigationGateOf(' "$UI" | grep -vE "$NC" | wc -l | tr -d ' ')
NR2=$(grep -c 'enabled = gate.canNavigate && !calculating' "$UI")
NR3=$(grep -rn 'enabled = !blocked' "$DD" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
# D-177：这一格从"有没有那个 if"改成"那一行到底读的是哪一半"——
# 只钉 `InfoRow("下一步该做什么", gate.hint)` 这个字面，才能钉住"不可点时说的是下一步，不是字段名"。
NR4=$(grep -c 'InfoRow("下一步该做什么", gate.hint)' "$UI")

# N-s 引擎层与入口层同闸：算路（calculateRoute）与开始导航（startGpsNavi）各一道 `!state.canNavigate`；
# SDK 层不许再自己 `is EngineReadiness.Blocked` —— 只挡 Blocked 就是放行 Initializing。
# ⚠ 本簿对 `is EngineReadiness.Blocked` 没有文件级豁免：注入负控里有一条破口的**预期捕获者就是 NSG2**
#（`navigation_test_negative_controls.sh` 的 N-t3，把 SDK 层退回只挡 Blocked），所以这里按原样数就是红。
# 计数变量叫 NSG/NSG2 而不是 NS：$NS 是旧屏文件路径，同名会把后面四条判据的读取对象顶掉。
NSG=$(grep -n 'if (!state.canNavigate)' "$AM" | grep -vE "$NC" | wc -l | tr -d ' ')
NSG2=$(grep -rn 'is EngineReadiness.Blocked' "$AM" | grep -vE "$NC" | wc -l | tr -d ' ')

# N-t 成因码齐备且各归各位：enum 六个常量都在（少一个就有一种成因被并进别的话题），
# Blocked 的三个分支 + gate 的 INITIALIZING/NO_ROUTE 两处各自带码（共 6 处 `code = NavigationGateCode.`），
# NO_ROUTE 有两个产出点（缺起点 / 缺调度给的目标点），合成一个就说不清缺什么。
NT=$(grep -cE '^    (READY|NOT_INSTALLED|SDK_UNAVAILABLE|INITIALIZING|NO_ROUTE|ERROR),$' "$NENG")
NT2=$(grep -c 'code = NavigationGateCode\.' "$NENG")
NT3=$(grep -c 'NavigationGateCode.NO_ROUTE' "$NENG")
# NT4 单独钉 INITIALIZING 的**产出点**：NT2 只数"带码的行数"，把 Initializing 分支顶成 ERROR 时它仍是 6 ——
# 少这一条，"还在等回执被念成这手机不能导航"就只有用例在守，而结构判据要能独立变红（负控实测出来的洞）。
NT4=$(grep -c 'code = NavigationGateCode.INITIALIZING,' "$NENG")

# ---- 以下 N-u…N-w 是 F-3 ⑭（§九 导航必须绑定当前真实路线，V21-09）新增 ----

# N-u 判据层有"刚验过的那条"这个概念：指令 §九 点名的两个名字必须真在纯层里
# （住在 SDK 层就没有 JVM 用例能调到，工程没有 Robolectric —— 与 N-q…N-t 同一条分工理由）
NU=$(grep -c 'data class AcceptedRouteSession' "$NENG")
NU2=$(grep -c 'fun routeBindingOf(' "$NENG")
NU3=$(grep -n 'routeFingerprint' "$NENG" | grep -vE "$NC" | wc -l | tr -d ' ')

# N-v 绑定不是判据层的摆设：SDK 层每次开始导航前**重读**高德此刻的路径再比一次。
# 少任何一条，"startNavi 启动的就是刚验过的那条"就又回到只有愿望、没有检查（V21-09 那两行 ❌ 的原样）。
NV=$(grep -c 'routeBindingOf(accepted = acceptedRoute' "$AM")
NV2=$(grep -c 'binding is RouteBinding.Unbound' "$AM")
NV3=$(grep -c 'SdkPathShape(coordCount = it.geoPoints().size' "$AM")
# 凭证只由过闸门那一次写入；作废点 7→9 是 N-4（赛道 ⑪）这轮抬的：多出来的两处是高德自己广播要重算
# （偏航 / 拥堵）—— 那两句一出口，本机就该知道高德手里那条已经不是刚验过的了，留着凭证下一步就是拿它去 startNavi。
# 9 个作废点 = 高德自己重算 / 高德广播偏航 / 高德广播拥堵 / 算路失败 / 请求未受理 / 超时或失败 / 没有路径 / 停止导航 / 释放引擎
NV4=$(grep -c 'acceptedRoute = (check as? RouteCheck.Accepted)?.let' "$AM")
NV5=$(grep -c 'acceptedRoute = null' "$AM")

# N-w 不许伪造指纹：指令 §九 要的 routeFingerprint 只能是实测数的拼接。
# 这一条钉的是"本机想不出可信来源时，用哈希/随机数糊一个看起来像版本号的串"——
# 那正是 V21-13 的 graphVersion（三面 0 生产者）如果被照抄进代码会长成的样子。
NW=$(grep -rnE 'MessageDigest|sha1|sha256|md5|UUID|hashCode' "$ND" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
NW2=$(grep -c 'destinationLat = to.lat' "$AM")

# ---- N-x 是"有没有人真的从缓存取路线"这一问（赛道 ⑭ / D-059，V21-13/14 的升格触发器）----
# ⑰ 那轮它钉的是 **0**：三个出口（cached/cachedAt/snapshot）在生产代码里没人调，
# 于是"给没人读的缓存加过期校验＝把'还没接上'登记成'已经安全'"，TTL/graphVersion 当轮不做。
# 本轮那个前提自己到期了 —— `data/repo/OfflineRouteDisclosure.kt` 现在真的读一次缓存
# （**只披露、不供几何**），所以目标从 0 改成 **1**：这一格数的仍是读点，只是现在数的是**唯一那一个**。
# · 变 ≥2 = 有人开了第二个读口（同一个问题两个答案，§八 ㉗）；
# · 回 0 = 披露被删掉了，那 V21-13 的 TTL 又变成没有读方的装饰，本轮的升格结论得一起撤回。
# 读写两口径都只数**生效行**（NC 滤注释），且必须数**两支**：别处的限定调用 ＋ object 内部的裸调用
# （负控实测的第一版只数限定写法，注入"在本文件里另开一个 fun 读 cached(...)"直接漏 = 假绿）。
RRSRC=app/src/main/java/com/shanxiang/buspost/core/map/RouteRepository.kt
# 先验文件存在：负控副本没建全时，`[ -f ]` 会让第二支静默跳过，把"根本没跑"读成"0 个读点"
[ -f "$RRSRC" ] || { echo "× 判据文件缺失：$RRSRC（NX 的裸调用那一支会静默失效，不要把缺文件读成 0 命中）"; exit 2; }
NX=$(
    { grep -rnE 'RouteRepository\.(cached|cachedAt|snapshot)\(' app/src/main --include=*.kt | grep -vE "$NC"
      grep -nE '(^|[^.[:alnum:]_])(cached|cachedAt|snapshot)\(' "$RRSRC" \
          | grep -vE 'fun (cached|cachedAt|snapshot)\(' | grep -vE "$NC"
    } | wc -l | tr -d ' ')
NX2=$(grep -rn 'RouteRepository\.resolve(' app/src/main --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')

# ---- N-y 是 N-2（赛道 ⑨：实时 turn-by-turn 的转向话术与高德播报）新增的一族 ----
# 这一族管的还是那条老分工：表**为什么是这 57 行**只能由 javap 说（§77 的手法），
# 所以判据钉"表没被砍短、表外的码不许有名字"，而"某一行的字面是什么"由 NavigationContractTest 钉。

# N-y 两张表的行数：IconType 实测 `0..54` 连号 + 65/66 = 57，NaviTTSType 实测 1..6 = 6。
# 少一行 = 有人把已定义的码当成"高德没给"（把有念成没有）；多一行 = 有人给表外的码编了个动作（假引导）。
# 按函数名到收尾括号取段，不数整份文件：两段 `^    N -> "` 混在一起就分不出哪张表被砍了。
NY=$(sed -n '/^internal fun turnIconSpeechOf/,/^}/p' "$NENG" | grep -cE '^    [0-9]+ -> "')
NY2=$(sed -n '/^internal fun naviVoiceTypeNameOf/,/^}/p' "$NENG" | grep -cE '^    [0-9]+ -> "')

# N-y3 播报帧只有高德回调这一个来源：两个重载各构造一次、停止时清一帧、本机一个字节都不合成。
# 空文本那道闸（takeIf isNotBlank）算在构造那两行里：一句空话不该变成"高德播报了"。
NY3=$(grep -c 'override fun onGetNavigationText' "$AM")
NY4=$(grep -cF '?.takeIf { it.isNotBlank() }?.let { NavigationVoice(' "$AM")
NY5=$(grep -c 'voiceState.value = null' "$AM")
# 本机 TTS 在**正式导航面**必须为 0：旧屏那台 TextToSpeech 是 DEBUG ONLY 的假导航（D-003），
# 真导航的语音只能是高德自己的内置播报，否则本机就有了第二套"说什么"的话术
NY6=$(
    { grep -rnE 'TextToSpeech|AudioTrack|MediaPlayer|synthesize' "$ND" --include=*.kt
      grep -nE 'TextToSpeech|AudioTrack|MediaPlayer|synthesize' "$UI"
    } | grep -vE "$NC" | wc -l | tr -d ' ')

# N-y7/y8 开关真的交给高德、页面只订阅不轮询：
# `Prefs.voiceGuidance` 若不进 startGpsNavi，「我的」里那个开关就是装饰（release 里它今天什么都不做）；
# 页面读 voice 只能 collect flow，不能本机定时去问高德"刚才播了什么"
NY7=$(grep -c 'navi.setUseInnerVoice(voiceOn)' "$AM")
NY8=$(grep -c 'engine.startGpsNavi(Prefs.voiceGuidance)' "$UI")
NY9=$(grep -c 'val voice: StateFlow<NavigationVoice?>' "$NENG")
NY10=$(grep -c 'engine.voice.collectAsStateWithLifecycle()' "$UI")
NY11=$(grep -c 'turnIconSpeechOf(frame.iconType)' "$UI")
# N-y12 表外那道 `else -> null`：光数行数钉不住它 —— 有人把 else 改成一句话术，两张表照样是 57/6 行，
# 但页面从此会对一个高德没定义过的码念出一句人话（D-003 那台按字面猜图形的 `laneIcon()` 就是这个形状）。
# 所以按函数名取段各数一次收尾分支，两处都必须还在。
NY12=$(
    { sed -n '/^internal fun turnIconSpeechOf/,/^}/p' "$NENG"
      sed -n '/^internal fun naviVoiceTypeNameOf/,/^}/p' "$NENG"
    } | grep -c '^    else -> null')

# ---- N-z 是 N-3（赛道 ⑩：真实车道信息）新增的一族 ----
# 与 N-y 同一条分工：表**为什么是这些行**只能由 javap 说（`javap -constants LaneAction` 实测
# `0..25` 连号 + `255` = 27 条，⚠ 不是 0..26），"某一行的字面是什么"由 NavigationContractTest 钉。

# NZ…NZ4 车道族住在纯层：帧类型、显示态、话术唯一出口、字段搬运口。
# 进了 SDK 层就没有 JVM 用例能调到（工程无 Robolectric）—— 与 N-u 同一条分工理由。
NZ=$(grep -c 'data class LaneFrame' "$NENG")
NZ2=$(grep -c 'sealed class LaneDisplay' "$NENG")
NZ3=$(grep -c 'internal fun laneDisplayTextOf(' "$NENG")
NZ4=$(grep -c 'internal fun laneFrameOf(' "$NENG")

# NZ5 名字表 27 行 = javap 读到的 27 个常量；少一行是把高德给了的车道念成"表外"。
# NZ6 话术表**故意只有 22 行**：`LEFT_IN_AHEAD`(13)、`LEFT_IN_LEFT_LU_TURN`(14)、`RESERVED`(15)、
# `RIGHT_RU_TURN_EX`(17)、`LEFT_LU_TURN_EX`(20) 五条没有材料可依据，硬翻就是本机替高德说话。
# 这两张表的行数**不相等**本身就是判据的一半点：写成 27/27 才是造假。
NZ5=$(sed -n '/^internal fun laneActionNameOf/,/^}/p' "$NENG" | grep -cE '^    [0-9]+ -> "')
NZ6=$(sed -n '/^internal fun laneActionSpeechOf/,/^}/p' "$NENG" | grep -cE '^    [0-9]+ -> "')
NZ7=$(
    { sed -n '/^internal fun laneActionNameOf/,/^}/p' "$NENG"
      sed -n '/^internal fun laneActionSpeechOf/,/^}/p' "$NENG"
    } | grep -c '^    else -> null')

# NZ8…NZ11 车道帧的三个回调来源与四处写入点：两个重载 + hideLaneInfo + 停止清帧。
# `Cleared` 只许 hideLaneInfo 写：把它当"高德没给"用，就把"没问到"念成了"高德说没有"。
NZ8=$(grep -c 'override fun showLaneInfo' "$AM")
NZ9=$(grep -c 'override fun hideLaneInfo' "$AM")
NZ10=$(grep -c 'laneState.value' "$AM")
NZ11=$(grep -c 'laneState.value = LaneDisplay.Cleared' "$AM")

# NZ12/NZ13 数组重载只登记组数：取 first() 冒充单组会被显示成 `showLaneInfo(AMapLaneInfo)` 那条路径的产物，
# 而两条路径给的字段完整度并不相同（那两个 byte[] 的语义 javap 给不出来，D-⑩-1）。
NZ12=$(grep -c 'infos?.size ?: 0' "$AM")
NZ13=$(grep -c '\.first()' "$AM")

# NZ14 两个数组不按下标配对：`zip` / `forEachIndexed` 一出现，就是拿一个没核过的假设
# （"frontLane[i] 是 backgroundLane[i] 的推荐标记"）去替司机决定走第几条道。
NZ14=$(grep -rnE 'zip\(|forEachIndexed' "$ND" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')

# NZ15…NZ19 页面这一半：只订阅 flow、只走一个话术出口、不自己造帧、不查码表、
# 不把旧屏那台按名字猜图形的 `laneIcon()` 搬进正式面，且「我的」里那个车道开关真的被读到。
NZ15=$(grep -c 'engine\.lane\.collectAsStateWithLifecycle()' "$UI")
NZ16=$(grep -cE 'LaneFrame\(|LaneDisplay\.|laneCellLabel' "$UI")
NZ17=$(grep -c 'laneDisplayTextOf(sdkLane)' "$UI")
NZ18=$(grep -c 'if (!Prefs\.laneGuidance)' "$UI")
NZ19=$(grep -c 'laneIcon' "$UI")
NZ20=$(grep -c 'val lane: StateFlow<LaneDisplay?>' "$NENG")
# NZ21 表外那一格的**字面**还在：laneCellLabel 的第三分支是"高德给了这张表里没定义的值"唯一的说法。
# 它被换成任何一句人话（哪怕"直行"），表外码就从"异常"变成"一个动作"，而 NZ5/NZ6/NZ7 三个计数器一个都不掉。
NZ21=$(grep -c 'LaneAction 表里没有的车道码' "$NENG")

# ---- 以下 N-aa 族是 N-4（赛道 ⑪：偏航重算只能由导航引擎触发）新增 ----
# 这一族管的仍是那条老分工：值域为什么是这四格只能由 javap 说（取证 /tmp/n7_recalc_javap.txt），
# "某一格的话术长什么样"由 NavigationContractTest 钉；这里钉结构 ——
# 有没有人在本机判偏航（阈值 / 主动要求重算 / 把 notify 当偏航通道），有没有人把广播与回执配成因果。

# NAA…NAA4 判据层有"高德自己发起的重算"这个概念，且话术只有一个出口、五格都在纯层
# （进了 SDK 层就没有 JVM 用例能调到，工程无 Robolectric —— 与 N-u / N-z 同一条分工理由）
NAA=$(grep -c 'sealed class EngineRecalcDisplay' "$NENG")
NAA2=$(grep -c 'internal fun engineRecalcTextOf(' "$NENG")
NAA3=$(grep -cE '^    (data object Yaw|data object TrafficJam|data class EngineInitiatedSuccess|data class EngineInitiatedFailure)' "$NENG")
NAA4=$(sed -n '/^internal fun engineRecalcTextOf/,/^}/p' "$NENG" | grep -cE '^    (null ->|EngineRecalcDisplay\.(Yaw|TrafficJam) ->|is EngineRecalcDisplay\.EngineInitiated)')
NAA5=$(grep -c 'val recalc: StateFlow<EngineRecalcDisplay?>' "$NENG")
# NAA6 表外那一格的**字面**还在（与 NZ21 同一手法）：null 那句话同时是本机"不判偏航"的声明。
# 它被换成一句"当前没有偏航"，五格行数一根手指都不会动，而"没有回调"就被念成了"高德说没偏航"。
NAA6=$(grep -c '本机不拿距离卡阈值判偏航' "$NENG")

# NAA7…NAA8 两条广播各接一次，且各自都要先作废 §九 那份凭证：
# 高德一开口说"它要重新算路"，本机就已经知道它手里那条不是刚验过的那条了。
# 只清帧不作废凭证 = 下一次 startGpsNavi 会拿旧记录去比对一条对新路径的指纹（V21-09 的原样）。
NAA7=$(grep -c 'override fun onReCalculateRouteForYaw()' "$AM")
NAA8=$(grep -c 'override fun onReCalculateRouteForTrafficJam()' "$AM")
NAA9=$(grep -A 1 'override fun onReCalculateRouteForYaw() {' "$AM" | grep -c 'acceptedRoute = null')
NAA10=$(grep -A 1 'override fun onReCalculateRouteForTrafficJam() {' "$AM" | grep -c 'acceptedRoute = null')

# NAA11…NAA13 帧的五个写入点各归各位：两条广播各一帧、两条自发回执各一帧、停止时清一帧。
# 成功那一帧的 code 直接照抄高德的 `errorCode`（本机不重新分档）；失败那一帧只带 code，
# 高德的 errorDescription/errorDetail 可能是空串，空串进句子会被念成"失败但没有原因"。
NAA11=$(grep -c 'recalcState.value' "$AM")
NAA12=$(grep -c 'recalcState.value = null' "$AM")
NAA13=$(grep -c 'recalcState.value = EngineRecalcDisplay.EngineInitiatedSuccess(result?.errorCode ?: -1)' "$AM")
NAA14=$(grep -c 'recalcState.value = EngineRecalcDisplay.EngineInitiatedFailure(code)' "$AM")

# NAA15…NAA17 本机不越权：不主动要求重算、不把 notify 当偏航通道、引擎层不谈垂距。
# 这三条合起来就是指令第 ⑪ 条"只能由导航引擎触发，不得本地假判定"的结构侧落点 ——
# 任何一条变红都意味着有人在算路回执之外给自己造了一个偏航结论。
NAA15=$(grep -rnE 'reCalculateRoute\(|selectRoute|getNaviPaths' "$ND" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
NAA16=$(grep -rnE 'onNaviRouteNotify|AMapNaviRouteNotifyData|notifyParallelRoad' "$ND" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
NAA17=$(grep -rn 'crossTrack' "$ND" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')

# NAA18…NAA21 页面这一半：只订阅 flow、只走那一句唯一话术、不自己造帧，
# 且正式导航面不许出现本机那句"已偏离线路"（旧屏那行注释里提到它是解释为什么不写，不算）。
NAA18=$(grep -c 'engine\.recalc.collectAsStateWithLifecycle()' "$UI")
NAA19=$(grep -c 'InfoRow("重算", engineRecalcTextOf(sdkRecalc))' "$UI")
NAA20=$(grep -cE 'EngineRecalcDisplay\.' "$UI")
NAA21=$(grep -n '已偏离线路' "$UI" | grep -vE "$NC" | wc -l | tr -d ' ')

# NAA22…NAA24 旧屏（DEBUG ONLY）那一半：本机阈值不回来，偏航只读后端那一个位。
# 旧写法是把 `g.deviated` 与一个本机自己挑的垂距阈值 OR 在一起 —— 阈值没有任何来源认可过，
# 而后端那个位才是这条链路里唯一说了话的一方（指令第 ⑪ 条点名禁止的就是这种顶替）。
NAA22=$(grep -rnE 'crossTrackMeters\(\) *[<>]' "$DD" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
NAA23=$(grep -c 'val offRoute = roadOk && g.deviated' "$NS")
NAA24=$(grep -rn '本机判定偏航' app/src/main --include=*.kt | wc -l | tr -d ' ')

# ---- N-ab 是赛道 ⑭（离线真实道路缓存，V21-13/14）新增的一族 ----
# 这一族管的不是"缓存好不好用"，而是**这份缓存能不能被诚实读出来**：
# 来源原文、抓取时间、过期边界、线程安全、唯一的读出口，以及"否决先于写入"那一次决定。
# 分工照旧：行为由 `data/repo/RouteOfflineCacheContractTest.kt` 钉（纯 JVM），这里钉结构 ——
# 因为"读到过期条目还返回几何"这种事，只要有人把 [RouteCacheRead.Stale] 加回一个 route 字段就会悄悄发生，
# 而那一步在页面上看不出来。⚠ 三个文件都要先验存在（缺文件时 grep 输出空，会被读成"0 命中＝干净"）。
RRSRC=app/src/main/java/com/shanxiang/buspost/core/map/RouteRepository.kt
OFF=app/src/main/java/com/shanxiang/buspost/data/repo/OfflineRouteDisclosure.kt
REPO=app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt
for f in "$RRSRC" "$OFF" "$REPO"; do
    [ -f "$f" ] || { echo "× 判据文件缺失：$f（N-ab 那一族的读数全部不采信）"; exit 2; }
done

# NAB1…NAB2 读出口只有一把：cached()。旧写法那三把（cached/cachedAt/snapshot）留着就等于
# 有两条能绕过过期判定的路 —— 尤其 snapshot()，它给的是可变引用，页面可以直接把里面的路线画出来。
NAB1=$(grep -cF 'fun cached(' "$RRSRC")
NAB2=$(grep -cE 'fun (cachedAt|snapshot)\(' "$RRSRC")
# NAB3…NAB4 TTL 在场，且边界是"相等即过期"（指令 §十三 原话是"当前时间 < expiresAt"才算新鲜）。
# 把 `>=` 写成 `>` 只改一个字符，两边都不会让任何用例变红 —— 所以这一格必须由结构钉住。
NAB3=$(grep -cF 'const val MAX_AGE_MILLIS' "$RRSRC")
NAB4=$(grep -cF 'if (nowMillis >= entry.expiresAtMillis)' "$RRSRC")
# NAB5…NAB6 抓取时间只能来自调用方给定的那一个时钟入参。改成现场取时（System/Date/SystemClock），
# "什么时候抓的"就变成"什么时候读的"，那条 30 分钟前抓的路线会被披露成刚抓的。
NAB5=$(grep -cF 'fetchedAtMillis = nowMillis' "$RRSRC")
NAB6=$(grep -cE 'fetchedAtMillis = (System|Date|LocalDate|SystemClock|Instant)' "$RRSRC")
# NAB7…NAB8 线程安全：裸 LinkedHashMap 的插入序淘汰已经换成 ConcurrentHashMap ＋ 序号。
# 生效行口径（滤注释）：KDoc 里那两处"不要裸 LinkedHashMap"是说明文字，不能被算成回潮。
# ⚠ 这一条比 NC 多滤一层 `/**`：NC 只认 `*` 与 `//` 开头，而第 47 行那条序号说明是**单行 `/** … */`**，
# 用 NC 会把一句说明读成"生产代码又用了裸 LinkedHashMap"（实测 NC 口径 = 1、本口径 = 0）。
NCK='(^|[0-9]+:)[[:space:]]*(/\*\*?|\*|//)'
NAB7=$(grep -cF 'ConcurrentHashMap' "$RRSRC")
NAB8=$(grep -n 'LinkedHashMap' "$RRSRC" | grep -vE "$NCK" | wc -l | tr -d ' ')
# NAB9…NAB10 来源留**原文**：providerRaw 照抄后端给的那个字符串。
# 拿归一枚举顶替（source.name / source.label）就是把 "gaode"/"高德" 念成 "AMAP" —— 后端没说过那句话。
NAB9=$(grep -cF 'providerRaw = sourceRaw.orEmpty()' "$RRSRC")
NAB10=$(grep -cE 'providerRaw = .*\.source\.' "$RRSRC")
# NAB11…NAB12 过期那一格**结构上没有几何**：Stale 只带来源原文、抓取时间与时长。
# 一旦有人给它补一个 route 字段，"正式路线不允许返回过期路线"就从结构性退化成约定性。
NAB11=$(grep -cF 'data class Stale' "$RRSRC")
NAB12=$(grep -F 'data class Stale' "$RRSRC" | grep -c 'RealRoadRoute')
# NAB13…NAB15 否决先于写入（D-⑱-3）：后端 routeAvailable=false 时不写、并删旧条目。
# 旧写法是 resolve 先写、再在外面判 serverAvailable —— 于是"后端说没有"那一班在缓存里留了一条活记录。
# NAB15 钉的是**先后顺序**：判定必须发生在 resolve 之前（行号比出来），否则传进去的就是上一个决定。
NAB13=$(grep -cF 'if (check is RouteCheck.Accepted && serverAvailable)' "$RRSRC")
NAB14=$(grep -cF 'serverAvailable = serverAvailable,' "$REPO")
SA_LINE=$(grep -nF 'val serverAvailable = !o.has("routeAvailable")' "$REPO" | head -1 | cut -d: -f1)
RV_LINE=$(grep -nF 'val check = RouteRepository.resolve(' "$REPO" | head -1 | cut -d: -f1)
if [ -n "$SA_LINE" ] && [ -n "$RV_LINE" ] && [ "$SA_LINE" -lt "$RV_LINE" ]; then NAB15=1; else NAB15=0; fi
# NAB16…NAB17 两处并发细节：过期删除用两参 remove（只删自己刚读到的那一份，别删别人新写的条目），
# 淘汰按写入序号取最旧（ConcurrentHashMap 没有插入序，旧写法的"顺序"已经不存在了）。
NAB16=$(grep -cF 'accepted.remove(key, entry)' "$RRSRC")
NAB17=$(grep -cF 'minByOrNull { it.value.sequence }' "$RRSRC")
# NAB18…NAB21 披露那一半：读点只此一处、不供几何、三格各自一句、
# 并且不许把 F-3 ⑧ 那句"这里不画任何示意线"抄过来 —— 无路线话术的唯一出口在 MissionMarkers（§八 ㉗）。
NAB18=$(grep -cF 'RouteRepository.cached(key, nowMillis)' "$OFF")
NAB19=$(grep -cE 'MapPath|\.polyline|Geometry' "$OFF")
NAB20=$(grep -cE '^    (is RouteCacheRead\.Hit|is RouteCacheRead\.Stale|RouteCacheRead\.Absent ->)' "$OFF")
NAB21=$(grep -cF '不画任何示意线' "$OFF")
# NAB22…NAB24 不造权威：graphVersion 与哈希/随机数在本机没有任何生产者（后端 VO 逐字段读过），
# 接进缓存键或指纹就是凭空造一个"看起来可信的东西"（与 N-w 同族，那一族只管 core/navigation）；
# 接线点只有一处，多一处就是多一个能把缓存当路线用的入口。
NAB22=$(grep -rn 'graphVersion' app/src/main --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
NAB23=$(grep -rnE 'MessageDigest|sha1|sha256|md5|UUID|hashCode' "$RRSRC" "$OFF" | wc -l | tr -d ' ')
NAB24=$(grep -cF 'routeFailureTextOf("driverRoute:$driverId", errorTextOf(e))' "$REPO")

# ---- N-ac 是 N-8（赛道 ⑯：转向/播报/车道/偏航四行落进卡片后的口径与排版复核）新增的一族 ----
# 这一族钉的不是"好不好看"，而是**四行有没有在说同一件事**：
# ① 开关关着时那一格只说本机没做，不替高德宣告"给过"或"没给"（旧写法"本机没显示高德给的车道"就是这么越界的）；
# ② 那句话只有一个出口（与 ⑩ 车道、⑪ 重算同一条规矩），页面里不留第二份字面；
# ③ 指出去的开关名必须是「我的」里真有的那一行（"车道引导"是旧调试屏的名字）；
# ④ 播报那一格的类型名进**值**不进 label —— 这一屏的 label 全是静态词，动态词进 label 会让标签宽窄不齐。
# 分工照旧：句子本身的对错由 NavigationContractTest 钉（M34…M36 三条行为负控验它会红），这里钉结构。
NAC=$(grep -c 'internal fun guidanceSwitchOffText(' "$NENG")
# NAC2 页面调用这个出口的次数：2 → **3** 的唯一增量 = 2026-10-09 卡片重排（队列 #308/#309）时加的那条
# 「到近作业播报」开关行（DriverSdkNavCard.kt:318 读 Prefs.approachVoice，与车道/语音同一条规矩）。
# 这一格仍是"恰好 N"的摩擦设计：加一行必须同轮把数改对，并让新那一行也过 NAC12/NAC13 的名字核对。
NAC2=$(grep -c 'guidanceSwitchOffText(' "$UI")
# NAC3/NAC4 那句骨架（"开关是关的（在「我的」里）"）只许出现在纯层一处，页面一份都不许有
NAC3=$(grep -c '开关是关的（在「我的」里）' "$NENG")
NAC4=$(grep -n '开关是关的（在「我的」里）' "$UI" | grep -vE "$NC" | wc -l | tr -d ' ')
# NAC5 旧那句越界的字面（把成因写成"高德给的车道"）在正式面归零
NAC5=$(grep -n '高德给的车道' "$UI" | grep -vE "$NC" | wc -l | tr -d ' ')
# NAC6 出口自己不许替高德说话：函数体内"高德没给"/"高德给的"一个都不许出现
# ⚠ 取段按"函数名到文件尾"（这个出口是文件最后一项，没有收尾 `}` 可锚），有人往它后面追加函数时这一条会一起读到新内容，
#   那属于同一轮的结构性提醒，不是误报：往同一份文件里加话术出口本来就该重看这一族。
NAC6=$(sed -n '/^internal fun guidanceSwitchOffText/,$p' "$NENG" | grep -cE '高德没给|高德给的')
# NAC7 四行各有自己那一句：转向查表 / 车道话术 / 重算话术 / 播报开关话术，各一次
NAC7=$(grep -cE 'turnIconSpeechOf\(frame\.iconType\)|laneDisplayTextOf\(sdkLane\)|engineRecalcTextOf\(sdkRecalc\)|guidanceSwitchOffText\("语音播报"' "$UI")
# NAC8/NAC9 两条开关各自点名「我的」里那一行（车道级指引 / 语音播报），少一个就是有一格回到"没名字"或错名字
NAC8=$(grep -c 'guidanceSwitchOffText("车道级指引"' "$UI")
NAC9=$(grep -c 'guidanceSwitchOffText("语音播报"' "$UI")
# NAC12/NAC13 第三条开关行（到近作业播报）的名字核对，与 NAC8/NAC9 同一条规矩：
# 卡片指出去的名字与「我的」里那一行的字面**必须逐字相等**——只改一边就红（司机按卡片找不到那条开关）。
NAC12=$(grep -c 'guidanceSwitchOffText("到近作业播报"' "$UI")
NAC13=$(grep -c 'SettingRow("到近作业播报"' "$MP")
# NAC10 卡片里"车道引导"这个名字（旧调试屏那台开关）不许有：生效行口径
NAC10=$(grep -n '车道引导' "$UI" | grep -vE "$NC" | wc -l | tr -d ' ')
# NAC11 播报 label 回到静态词：`"播报" + (...)` 那一写法归零
NAC11=$(grep -c '"播报" +' "$UI")

# ── N-rm 到车**持续**提醒（D-170，用户口径⑤「持续提醒司机该做什么直到司机完成，但是不能影响驾驶」）──
# 这一族钉的是结构，句子的对错由 ApproachReminderContractTest 钉：
# ① "要不要提醒"只有一个判点（`stopOf`），两个面各自判一次就会一个在提醒一个说没到；
# ② 重复的节奏（间隔与上限）只有纯层那两个常数，出口层不许自己算时间；
# ③ 重复出声必须走 `ApproachAnnouncer` 那一个出口（语音→通知→只剩字 的降级只有一份）：
#    `deliver` 只许「一份定义 + 两处调用」（多一处 = 有人另开了条不出通知的路），
#    出口层也不许自己拿 `REPEAT_INTERVAL` 算时间；
# ④ 提醒是**非模态**的一行：它不许变成 Dialog —— 那正是"影响驾驶"的那一半要求。
RM="$SRC/ui/driver/ApproachReminder.kt"
WB="$SRC/ui/driver/WorkbenchScreen.kt"
AA="$SRC/ui/driver/ApproachAnnouncer.kt"
NRMA=$(grep -c '^    fun stopOf(' "$RM")
NRMA2=$(grep -rn 'ApproachReminder\.stopOf(' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
NRMB=$(grep -cE '^class ReminderTracker\b' "$RM")
NRMB2=$(grep -rn 'ReminderTracker()' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
NRMC=$(grep -c 'const val REPEAT_INTERVAL_MS' "$RM")
NRMC2=$(grep -c 'const val MAX_REPEATS' "$RM")
# 出口层不许碰时间与账本：它只负责"把这句发出去"
NRMD=$(grep -nE 'ReminderTracker|REPEAT_INTERVAL|elapsedRealtime' "$AA" | grep -vE "$NC" | wc -l | tr -d ' ')
NRME=$(grep -rn 'announcer\.remind(' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
NRME2=$(grep -c 'fun remind(' "$AA")
NRME3=$(grep -c 'deliver(' "$AA")
# 非模态：整个 ui/driver 里出现 Dialog 形状的新弹层来承载提醒 = 红（在册的扫码/公告/签名弹窗不读这一格，
# 它们不是"到车提醒"。认的是"提醒"这个词与 Dialog 出现在**同一行**）。
# ⚠ 这里原来写的是 `Dialog[^\n]*(提醒|reminder)`：grep 的字符类里 `\n` 不是换行，是"反斜杠和字母 n"，
# 于是任何带 n 的中间文本都把匹配切断 —— 负控注入那行 `val reminderDialog = AlertDialog(...) { Text("到车提醒") }`
# 里 `onDismissRequest` 的 n 正好把它掐死，判据当场不咬（2026-10-19 负控跑出来的 ✘）。grep 本来就按行匹配，
# 要"同一行"用 `.*` 就是同一行，不需要也不许再套一个假装的排除类。
NRMF=$(grep -rnE 'Dialog.*(提醒|[Rr]eminder)' "$SRC" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
# 正对照：工作台那一行确实换了话（否则上面那个"只有一个判点"的两个读数可能是扫描瞎了）
NRMG=$(grep -c 'reminder ?: distanceText' "$WB")

echo "N-a 判据层用例条数                  = $NA （目标 ≥55）"
echo "N-b 引擎内定时器/假步进（主线程桥接 post 已在册排除）= $NB （目标 0）"
echo "N-c 引擎内二次坐标换算              = $NCNT （目标 0）"
echo "N-d 引擎内假来源/直线里程           = $ND_ （目标 0）"
echo "N-e validate 出现处/SDK 层自判/唯一出口 = $NE/$NE2/$NE3 （目标 1/0/1）"
echo "N-f 工厂两种返回/NavigationEngine 实现数 = $NF/$NF2/$NF3 （目标 1/1/2）"
echo "N-g create/算路/开始/释放/目的地/页面接入 = $NG/$NG2/$NG3/$NG4/$NG5/$NG6 （目标 1/1/1/1/1/1）"
echo "N-h readiness 是 StateFlow/页面订阅/页面轮询 = $NH/$NH2/$NH3 （目标 ≥2/≥2/0）"
echo "N-i navi-3dmap/3dmap 并存/location 并存 = $NI/$NI2/$NI3 （目标 1/0/0）"
echo "N-j SDK import 总数/白名单外/引擎层之外 = $NJ/$NJBAD/$NJ2 （目标 12/0/0；10 = §77 那 9 个 + AMapLaneInfo，12 = +合规类两个）"
echo "N-k 诚实出口条数(高德没给|暂未知)/本机宣布成功 = $NK/$NK2 （目标 恰好 6 / 0）"
echo "N-l 过时断言「无需地图 SDK」残留       = $NL （目标 0）"
echo "N-m 司机 UI 目录假步进形态            = $NM （目标 0）"
echo "N-n 未加闸的 DRIVER_NAV 使用/注册带闸/文件自守 = $NN/$NN2/$NN3 （目标 0/1/1）"
echo "N-o 「非真实导航」标注/车道箭头猜测说明 = $NO/$NO2 （目标 ≥2/恰好 1）"
echo "N-p 旧屏引擎挂载/高德/百度/旧屏外部地图函数 = $NP/$NP2/$NP3/$NP4 （目标 0/1/1/0）"
echo "N-q 正式导航入口生效行里的 BuildConfig      = $NQ （目标 0）"
echo "N-r 页面 gate 调用/可点字面量/旧拼法/不可点原因块 = $NR/$NR2/$NR3/$NR4 （目标 1/1/0/1）"
echo "N-s 引擎两道 canNavigate 闸/SDK 层自判 Blocked  = $NSG/$NSG2 （目标 2/0）"
echo "N-t 成因码常量数/带码处数/NO_ROUTE 产出点/INITIALIZING 产出点 = $NT/$NT2/$NT3/$NT4 （目标 6/7/2/1；7 = Blocked 三分支 + Loading 分支 + gate 三处，LOADING 码随 readiness 轮加入，2026-10-10 补登）"
echo "N-u AcceptedRouteSession/routeBindingOf/routeFingerprint 生效行 = $NU/$NU2/$NU3 （目标 1/1/3）"
echo "N-v 绑定调用/拒收分支/重读形状/写入点/作废点 = $NV/$NV2/$NV3/$NV4/$NV5 （目标 1/1/1/1/9）"
echo "N-w 指纹里的哈希随机数/终点进凭证 = $NW/$NW2 （目标 0/1）"
echo "N-x 缓存读点/唯一写入点（⑭ 起目标 1/1）  = $NX/$NX2 （目标 1/1）"
echo "N-y IconType 表行数/NaviTTSType 表行数        = $NY/$NY2 （目标 57/6）"
echo "N-y 播报回调重载/构造点(带非空闸)/清帧/本机TTS = $NY3/$NY4/$NY5/$NY6 （目标 2/2/1/0）"
echo "N-y 开关交给高德/页面交开关/voice 是 flow/页面订阅/页面读表 = $NY7/$NY8/$NY9/$NY10/$NY11 （目标 1/1/2/1/1）"
echo "N-y 两张表的表外收尾分支                          = $NY12 （目标 2）"
echo "N-z 车道帧类型/显示态/话术出口/搬运口            = $NZ/$NZ2/$NZ3/$NZ4 （目标 1/1/1/1）"
echo "N-z 车道名字表行数/话术表行数/表外收尾分支        = $NZ5/$NZ6/$NZ7 （目标 27/22/2 —— 两表行数不等是有意的）"
echo "N-z showLaneInfo 重载/hideLaneInfo/写入点/Cleared = $NZ8/$NZ9/$NZ10/$NZ11 （目标 2/1/4/1）"
echo "N-z 数组重载只数组数/取 first 冒充单组            = $NZ12/$NZ13 （目标 1/0）"
echo "N-z 两个数组按下标配对（zip/forEachIndexed）      = $NZ14 （目标 0）"
echo "N-z 页面订阅/页面造帧/唯一话术出口/车道开关/猜图形 = $NZ15/$NZ16/$NZ17/$NZ18/$NZ19 （目标 1/0/1/1/0）"
echo "N-z lane 由 flow 承载（接口＋替身）               = $NZ20 （目标 2）"
echo "N-z 表外那一格的字面                              = $NZ21 （目标 1）"
echo "N-aa 重算族类型/话术出口/非空格数/分支数/flow 承载 = $NAA/$NAA2/$NAA3/$NAA4/$NAA5 （目标 1/1/4/5/2）"
echo "N-aa null 那一格的字面（本机不判偏航）             = $NAA6 （目标 1）"
echo "N-aa 两条广播接了几次/各自作废凭证                 = $NAA7/$NAA8/$NAA9/$NAA10 （目标 1/1/1/1）"
echo "N-aa 帧写入总数/清帧/成功帧照抄 code/失败帧只带 code = $NAA11/$NAA12/$NAA13/$NAA14 （目标 5/1/1/1）"
echo "N-aa 本机主动重算/notify 通道/引擎层垂距            = $NAA15/$NAA16/$NAA17 （目标 0/0/0）"
echo "N-aa 页面订阅/页面唯一话术/页面造帧/页面本机结论     = $NAA18/$NAA19/$NAA20/$NAA21 （目标 1/1/0/0）"
echo "N-aa 旧屏垂距阈值比较/偏航只读后端位/本机判定措辞    = $NAA22/$NAA23/$NAA24 （目标 0/1/0）"
echo "N-ab 缓存读出口一把/已删的旧出口                  = $NAB1/$NAB2 （目标 1/0）"
echo "N-ab TTL 常量/过期边界/抓取时间来自入参/现场取时    = $NAB3/$NAB4/$NAB5/$NAB6 （目标 1/1/1/0）"
echo "N-ab 并发容器出现数/旧 LinkedHashMap 生效行         = $NAB7/$NAB8 （目标 ≥1/0）"
echo "N-ab 来源原文照抄/改写成本机枚举                   = $NAB9/$NAB10 （目标 1/0）"
echo "N-ab 过期那一格的类型数/格内带几何                  = $NAB11/$NAB12 （目标 1/0）"
echo "N-ab 否决先于写入：判定/传参/行序                   = $NAB13/$NAB14/$NAB15 （目标 1/1/1）"
echo "N-ab 过期两参删除/越界按序号淘汰                   = $NAB16/$NAB17 （目标 1/1）"
echo "N-ab 披露读点/交出几何/三格各自一句/并句措辞         = $NAB18/$NAB19/$NAB20/$NAB21 （目标 1/0/3/0）"
echo "N-ab graphVersion 生效行/哈希随机数/披露调用点       = $NAB22/$NAB23/$NAB24 （目标 0/0/1）"
echo "N-ac 开关话术出口(纯层)/页面调用点/骨架在纯层/在页面  = $NAC/$NAC2/$NAC3/$NAC4 （目标 1/3/1/0）"
echo "N-ac 旧越界字面/出口体内替高德说话                   = $NAC5/$NAC6 （目标 0/0）"
echo "N-ac 四行各自出口/两条开关各自点名「我的」那一行       = $NAC7/$NAC8/$NAC9 （目标 4/1/1）"
echo "N-ac 车道那格指旧调试屏的开关名/播报 label 动态拼接    = $NAC10/$NAC11 （目标 0/0）"
echo "N-ac 到近作业播报：卡片那一行点名/「我的」那一行的字面  = $NAC12/$NAC13 （目标 1/1）"
echo "N-rm 提醒判点 定义/页面调用                      = $NRMA/$NRMA2 （目标 1/2）"
echo "N-rm 重复账本 类/页面挂载                          = $NRMB/$NRMB2 （目标 1/2）"
echo "N-rm 间隔与上限常数                                = $NRMC/$NRMC2 （目标 1/1）"
echo "N-rm 出口层自己算时间/出声出口/重复调用/remind/deliver = $NRMD/$NRME/$NRME2/$NRME3 （目标 0/2/1/3）"
echo "N-rm 提醒被做成弹窗/工作台那一行确实换话（正对照）   = $NRMF/$NRMG （目标 0/1）"

fail=0
[ "$NA" -ge 55 ] || { echo "× N-a 判据层用例少于 55 条：假步进/二次换算/闸门旁路/入口两个答案/§九 路线绑定/⑨ 转向码表/⑩ 车道表/⑪ 重算五格/⑯ 开关句口径没人钉"; fail=1; }
[ "$NB" = 0 ] || { echo "× N-b 引擎里出现定时器或 stepIndex+1：推进必须来自高德回调，否则就是假导航"; fail=1; }
[ "$NCNT" = 0 ] || { echo "× N-c 引擎做了坐标系换算：高德折线已是 GCJ-02，再偏一次整条线都是错的"; fail=1; }
[ "$ND_" = 0 ] || { echo "× N-d 引擎里出现直线/连线/模拟来源或 GeoMath 里程：正式 App 禁止假路线"; fail=1; }
[ "$NE" = 1 ] || { echo "× N-e 真实道路闸门出现第二处判断（或一处都没了）：同一个问题只能有一个答案"; fail=1; }
[ "$NE2" = 0 ] || { echo "× N-e SDK 层自己再 validate 一次：判据从此分叉，页面拿到的是两套结论"; fail=1; }
[ "$NE3" = 1 ] || { echo "× N-e 算路结果不再走 naviRouteCheck 唯一出口：高德折线可以不经校验直进界面"; fail=1; }
[ "$NF" = 1 ] || { echo "× N-f 工厂少了替身返回：不可用时会退回假导航而不是明说原因"; fail=1; }
[ "$NF2" = 1 ] || { echo "× N-f 工厂少了真引擎返回：第 8 步的产物变成摆设"; fail=1; }
[ "$NF3" = 2 ] || { echo "× N-f NavigationEngine 实现数不是 2：多了就是有人发明了第三种导航"; fail=1; }
[ "$NG" = 1 ] || { echo "× N-g 页面不再创建引擎：core/navigation 变成没人调的死代码"; fail=1; }
[ "$NG2" = 1 ] || { echo "× N-g 页面不再调用 calculateRoute：本机算路这条链路断了"; fail=1; }
[ "$NG3" = 1 ] || { echo "× N-g 页面不再调用 startGpsNavi：算完路也没人开始导航"; fail=1; }
[ "$NG4" = 1 ] || { echo "× N-g 离开页面不再 release()：SDK 回调会继续写这一屏的状态"; fail=1; }
[ "$NG5" = 1 ] || { echo "× N-g 目的地不再只读后端任务点：导航开始自己决定去哪里（违反硬规则 3）"; fail=1; }
[ "$NG6" = 1 ] || { echo "× N-g 正式导航面（工作台）的引擎卡片挂载数不是 1：0＝整条链路没有生产调用点，≥2＝同一页面起第二个 AMapNavi 实例"; fail=1; }
[ "$NH" -ge 2 ] || { echo "× N-h readiness 不再由 flow 承载：页面只能靠轮询猜引擎状态"; fail=1; }
[ "$NH2" -ge 2 ] || { echo "× N-h 页面不再订阅 readiness/guidance flow：状态来源被换成了本机读数"; fail=1; }
[ "$NH3" = 0 ] || { echo "× N-h 页面用定时器刷引擎状态：把本机节奏当成高德节奏，等于假推进"; fail=1; }
[ "$NI" = 1 ] || { echo "× N-i 导航依赖不在了：AMapNavi 类不在包里，第 8 步整体退回 BLOCKED"; fail=1; }
[ "$NI2" = 0 ] || { echo "× N-i 3dmap 与 navi-3dmap 并存：duplicate class，构建会直接崩"; fail=1; }
[ "$NI3" = 0 ] || { echo "× N-i 又单独引了 com.amap.api:location：同一颗 duplicate class 的雷"; fail=1; }
[ "$NJ" = 12 ] || { echo "× N-j SDK import 数量变了（现 $NJ）：新增的类名没有 javap 核对记录，按未核对处理（12 = §77 那 9 个 + AMapLaneInfo + 合规类两个，取证 tools/fixtures/amap_compliance_javap.txt）"; fail=1; }
[ "$NJBAD" = 0 ] || { echo "× N-j 引擎引入了白名单外的 SDK 类：禁止凭印象写 SDK API"; fail=1; }
[ "$NJ2" = 0 ] || { echo "× N-j 引擎层之外直接 import 导航 SDK：页面绕开了判据层"; fail=1; }
[ "$NK" = 6 ] || { echo "× N-k 诚实出口的处数不是恰好 6（现 $NK）：少一处=那一格的「没给」又不说了（会被直线值或上一帧顶替），多一处=有人新加了要标来源的数"; fail=1; }
[ "$NK2" = 0 ] || { echo "× N-k 页面本机宣布导航成功：开始与到达都只能由高德说"; fail=1; }
[ "$NL" = 0 ] || { echo "× N-l 「无需地图 SDK」这句旧断言还在：依赖形态已经变了，留着就是假话"; fail=1; }
[ "$NM" = 0 ] || { echo "× N-m 司机 UI 目录里又有定时器假步进（现 $NM 处）：翻页只能由司机自己点，§24 的目标就是 0"; fail=1; }
[ "$NN" = 0 ] || { echo "× N-n 有 $NN 条 DRIVER_NAV 用法没带 BuildConfig.DEBUG 闸：正式包会跳一条没注册的路"; fail=1; }
[ "$NN2" = 1 ] || { echo "× N-n 路由注册不再是那条带闸的唯一行（现 $NN2）：要么正式包又注册了旧屏，要么拆成两处管不住"; fail=1; }
[ "$NN3" = 1 ] || { echo "× N-n 旧屏文件里的第二道自守没了（现 $NN3）：残留入口/深链进来就会渲染指引"; fail=1; }
[ "$NO" -ge 2 ] || { echo "× N-o 旧屏的「非真实导航」标注少于 2 处（现 $NO）：D-003 要求页面上显著标注"; fail=1; }
[ "$NO2" = 1 ] || { echo "× N-o 车道箭头的来源说明不是恰好 1 处（现 $NO2）：按名字猜的图形会被读成后端下发的车道级引导"; fail=1; }
[ "$NP" = 0 ] || { echo "× N-p 旧屏又开始挂引擎卡片（现 $NP）：调试页与工作台各起一个引擎实例"; fail=1; }
[ "$NP2" = 1 ] || { echo "× N-p 正式卡片的高德外部地图交接不在了（现 $NP2）：release 失去交给外部真导航的出口（小程序 wx.openLocation 的等效能力）"; fail=1; }
[ "$NP3" = 1 ] || { echo "× N-p 正式卡片的百度外部地图交接不在了（现 $NP3）"; fail=1; }
[ "$NP4" = 0 ] || { echo "× N-p 外部地图代码又回到旧屏文件里（现 $NP4）：正式可达的东西不许住在调试文件中"; fail=1; }
[ "$NQ" = 0 ] || { echo "× N-q 正式导航入口又读了 BuildConfig（现 $NQ 处生效行）：构建类型被当成业务能力开关，release 包会对着就绪的引擎说不能导航"; fail=1; }
[ "$NR" = 1 ] || { echo "× N-r 页面不再只调一次 navigationGateOf（现 $NR）：可点性又回到页面自己拼条件的老路"; fail=1; }
[ "$NR2" = 1 ] || { echo "× N-r 算路按钮的 enabled 不再读 gate.canNavigate（现 $NR2）：入口与判据层会给出两个答案"; fail=1; }
[ "$NR3" = 0 ] || { echo "× N-r 司机目录里又出现 enabled = !blocked（现 $NR3）：Initializing 被当成可点，就是把未验证说成结论"; fail=1; }
[ "$NR4" = 1 ] || { echo "× N-r 不可点时不再说出缺什么（现 $NR4）：灰按钮不给原因，用户只能猜是 App 坏了"; fail=1; }
[ "$NSG" = 2 ] || { echo "× N-s 引擎里的 canNavigate 闸数不是 2（现 $NSG）：算路与开始导航少一道就会放行未就绪的引擎"; fail=1; }
[ "$NSG2" = 0 ] || { echo "× N-s SDK 层又自己判 Blocked（现 $NSG2）：只挡 Blocked 等于放行 Initializing，两层答案再次分叉"; fail=1; }
[ "$NT" = 6 ] || { echo "× N-t 成因码常量数不是 6（现 $NT）：少一个码就有一种成因被并进别的话题，修的人分不出该动哪个"; fail=1; }
[ "$NT2" = 7 ] || { echo "× N-t 带码处数变了（现 $NT2）：Blocked 三分支 + Loading 分支 + gate 三处必须各自自带成因码"; fail=1; }
[ "$NT3" = 2 ] || { echo "× N-t NO_ROUTE 产出点不是 2（现 $NT3）：缺起点与缺目标点被合成一句话，用户不知道该等定位还是等调度"; fail=1; }
[ "$NT4" = 1 ] || { echo "× N-t INITIALIZING 产出点不是 1（现 $NT4）：初始化中被并进别的码，'还在等回执'就会被念成'这手机不能导航'"; fail=1; }
[ "$NU" = 1 ] || { echo "× N-u §九 那份 accepted 凭证不在了（现 $NU）：算路验的是一条、startNavi 启动的是另一条，V21-09 原地退回 ❌"; fail=1; }
[ "$NU2" = 1 ] || { echo "× N-u 绑定判据不再住纯层（现 $NU2）：工程无 Robolectric，进了 SDK 层就没有用例能钉它"; fail=1; }
[ "$NU3" = 3 ] || { echo "× N-u routeFingerprint 生效行不是 3（现 $NU3）：定义与两处 detail 少一处，现场就按两种格式查指纹"; fail=1; }
[ "$NV" = 1 ] || { echo "× N-v SDK 层没再问一次绑定（现 $NV）：判据层有闸门但没人关，等于还是没闸"; fail=1; }
[ "$NV2" = 1 ] || { echo "× N-v 绑定被拒后不再拦住 startNavi（现 $NV2）：拒收结论被算路自己吞掉，就是假导航"; fail=1; }
[ "$NV3" = 1 ] || { echo "× N-v 开始导航前不再重读高德此刻的路径（现 $NV3）：拿本机记忆当'现在还是那条路'"; fail=1; }
[ "$NV4" = 1 ] || { echo "× N-v 凭证写入点不是 1（现 $NV4）：绕开真实道路闸门也能记成'已验证'"; fail=1; }
[ "$NV5" = 9 ] || { echo "× N-v 凭证作废点不是 9（现 $NV5）：少一处作废，就会留着一条对不上的旧记录去 startNavi"; fail=1; }
[ "$NW" = 0 ] || { echo "× N-w 导航目录里出现哈希/随机数当指纹（现 $NW）：没有任何来源能解释那串字符，正是不许造的'看起来像指纹的东西'"; fail=1; }
[ "$NW2" = 1 ] || { echo "× N-w 终点没进凭证（现 $NW2）：§九 的'任务变了指纹就变'失去依据，换任务点还能沿用旧记录"; fail=1; }
[ "$NX" = 1 ] || { echo "× N-x 从缓存读路线的生产出口数不是 1（现 $NX）：0＝⑭ 那句话术被删了、缓存重新变成只写不读；≥2＝同一个问题有两个答案（§八 ㉗），而且 TTL/graphVersion（V21-13）的登记立刻过期"; fail=1; }
[ "$NX2" = 1 ] || { echo "× N-x 路线缓存写入点不是 1（现 $NX2）：第二处写入意味着有绕过真实道路闸门往缓存里塞东西的路"; fail=1; }
[ "$NY" = 57 ] || { echo "× N-y IconType 表行数不是 57（现 $NY）：少一行就把高德明明给了的转向码念成'没给'，多一行就是给表外的码编了个动作"; fail=1; }
[ "$NY2" = 6 ] || { echo "× N-y 播报类型表行数不是 6（现 $NY2）：NaviTTSType 实测只有 1..6，多出来的名字没有任何来源"; fail=1; }
[ "$NY3" = 2 ] || { echo "× N-y 高德播报回调的重载数不是 2（现 $NY3）：带类型码与不带类型码是两条不同的路，少一条就有一类播报静默丢失"; fail=1; }
[ "$NY4" = 2 ] || { echo "× N-y 播报帧的构造点不是 2（现 $NY4）：文本不再只由高德原句构造，本机就有了合成句子的地方"; fail=1; }
[ "$NY5" = 1 ] || { echo "× N-y 停止导航不再清掉播报帧（现 $NY5）：留住最后一句，页面会把上一次会话的播报当成本次的"; fail=1; }
[ "$NY6" = 0 ] || { echo "× N-y 正式导航面出现本机 TTS/音频合成（现 $NY6）：说什么变成两套话术，那是假引导的另一半"; fail=1; }
[ "$NY7" = 1 ] || { echo "× N-y 开始导航不再把语音开关交给高德（现 $NY7）：AMapNavi 是进程单例，不显式设就沿用上一次的状态"; fail=1; }
[ "$NY8" = 1 ] || { echo "× N-y 页面不再把「我的」里的语音开关交给引擎（现 $NY8）：那个开关变成装饰，release 里它什么都不做"; fail=1; }
[ "$NY9" -ge 2 ] || { echo "× N-y 播报不再由 flow 承载（现 $NY9）：页面只能轮询去问高德刚才播了什么"; fail=1; }
[ "$NY10" = 1 ] || { echo "× N-y 页面不再订阅 voice flow（现 $NY10）：播报文本被换成了本机读数"; fail=1; }
[ "$NY11" = 1 ] || { echo "× N-y 页面不再查转向码表（现 $NY11）：图标码被直接印成数字或被就近猜成一句话术"; fail=1; }
[ "$NY12" = 2 ] || { echo "× N-y 表外收尾的 else -> null 少了（现 $NY12）：行数还是 57/6，但表外的码被改成了'有一句话'——那是给高德没定义的码编话术"; fail=1; }
[ "$NZ" = 1 ] || { echo "× N-z 车道帧类型不在纯层（现 $NZ）：工程无 Robolectric，进了 SDK 层就没有用例能钉它"; fail=1; }
[ "$NZ2" = 1 ] || { echo "× N-z 车道显示态没了（现 $NZ2）：'没给过 / 已经不显示 / 数组重载 / 单组'四种说法会被并成一格"; fail=1; }
[ "$NZ3" = 1 ] || { echo "× N-z 车道话术不再是唯一出口（现 $NZ3）：页面会拼出第二套说法，同一个车道两种措辞"; fail=1; }
[ "$NZ4" = 1 ] || { echo "× N-z SDK 字段不再只由一个搬运口进（现 $NZ4）：多一处就多一处能把'没给'补成'有一条道'"; fail=1; }
[ "$NZ5" = 27 ] || { echo "× N-z 车道名字表不是 javap 实测的 27 条（现 $NZ5）：少一行就把高德给了的码念成表外，多一行是给不存在的码编名字"; fail=1; }
[ "$NZ6" = 22 ] || { echo "× N-z 车道话术表不是 22 条（现 $NZ6）：那五条解释不了的词（13/14/15/17/20）被硬翻就是假引导"; fail=1; }
[ "$NZ7" = 2 ] || { echo "× N-z 车道两张表的表外收尾 else -> null 少了（现 $NZ7）：行数还是 27/22，但表外码被改成了'有一句人话'"; fail=1; }
[ "$NZ8" = 2 ] || { echo "× N-z showLaneInfo 的重载数不是 2（现 $NZ8）：单组与数组重载是两条不同的路，少一条就有一类车道静默丢失"; fail=1; }
[ "$NZ9" = 1 ] || { echo "× N-z 不再接 hideLaneInfo（现 $NZ9）：高德已经不显示车道，页面会留住上一组车道"; fail=1; }
[ "$NZ10" = 4 ] || { echo "× N-z 车道帧写入点不是 4（现 $NZ10）：多一处就是有人在本机造车道帧"; fail=1; }
[ "$NZ11" = 1 ] || { echo "× N-z Cleared 不再只由 hideLaneInfo 写（现 $NZ11）：'高德说没有'会被当成'高德没问过'用"; fail=1; }
[ "$NZ12" = 1 ] || { echo "× N-z 数组重载不再只登记组数（现 $NZ12）：那条路径的产物开始被解释"; fail=1; }
[ "$NZ13" = 0 ] || { echo "× N-z 引擎里出现 .first()（现 $NZ13）：把数组重载塌成单组，显示得像另一条回调路径给的数"; fail=1; }
[ "$NZ14" = 0 ] || { echo "× N-z 车道目录出现按下标配对的写法（现 $NZ14）：frontLane[i] 是不是 backgroundLane[i] 的推荐标记没有证据，猜错的方向是司机开错道"; fail=1; }
[ "$NZ15" = 1 ] || { echo "× N-z 页面不再订阅 lane flow（现 $NZ15）：车道状态来源被换成本机读数或轮询"; fail=1; }
[ "$NZ16" = 0 ] || { echo "× N-z 页面自己造车道帧或查码表（现 $NZ16）：正式面就有了第二个话术出口"; fail=1; }
[ "$NZ17" = 1 ] || { echo "× N-z 页面不再走那一句唯一话术（现 $NZ17）：车道那一格开始本机拼句子"; fail=1; }
[ "$NZ18" = 1 ] || { echo "× N-z 正式面不再读「我的」里的车道开关（现 $NZ18）：那个开关成了装饰，关掉还在报车道"; fail=1; }
[ "$NZ19" = 0 ] || { echo "× N-z 旧屏那台按名字猜图形的 laneIcon 进了正式面（现 $NZ19）：D-003 登记的假导航形状"; fail=1; }
[ "$NZ20" -ge 2 ] || { echo "× N-z 车道不再由 flow 承载（现 $NZ20）：页面只能靠轮询去问高德刚才给了哪组车道"; fail=1; }
[ "$NZ21" = 1 ] || { echo "× N-z 表外车道码那一格的字面没了（现 $NZ21）：三个表行数计数器都不会掉，但一个高德没定义过的码会被念成一条真车道"; fail=1; }
[ "$NAA" = 1 ] || { echo "× N-aa 「高德自己发起的重算」这个类型不在了（现 $NAA）：偏航又回到只有愿望、没有通道，或者被本机自己判出来"; fail=1; }
[ "$NAA2" = 1 ] || { echo "× N-aa 重算话术不再是唯一出口（现 $NAA2）：页面会拼出第二套说法，同一句偏航两种措辞"; fail=1; }
[ "$NAA3" = 4 ] || { echo "× N-aa 重算非空格数不是 4（现 $NAA3）：少一格就是把偏航与拥堵并成一格，或把回执与广播并成一格"; fail=1; }
[ "$NAA4" = 5 ] || { echo "× N-aa 话术函数的分支数不是 5（现 $NAA4）：null 那一格被并进某句人话，「没回调」就念成「高德说没偏航」"; fail=1; }
[ "$NAA5" -ge 2 ] || { echo "× N-aa 重算不再由 flow 承载（现 $NAA5）：页面只能靠轮询去问高德刚才说要重算没有"; fail=1; }
[ "$NAA6" = 1 ] || { echo "× N-aa null 那一格的字面没了（现 $NAA6）：这一族不再声明本机不判偏航，阈值随时能从句子背后长回来"; fail=1; }
[ "$NAA7" = 1 ] || { echo "× N-aa 高德的偏航广播没接（现 $NAA7）：司机偏了路，界面上一个字都不动"; fail=1; }
[ "$NAA8" = 1 ] || { echo "× N-aa 高德的拥堵重算广播没接（现 $NAA8）：另一条回调静默丢失，两格被并成一条"; fail=1; }
[ "$NAA9" = 1 ] || { echo "× N-aa 偏航广播没作废凭证（现 $NAA9）：下一步 startGpsNavi 会拿旧指纹比一条高德已重算的路（V21-09）"; fail=1; }
[ "$NAA10" = 1 ] || { echo "× N-aa 拥堵广播没作废凭证（现 $NAA10）：同上，只有一条广播负责作废就等于另一条能带着旧凭证走下去"; fail=1; }
[ "$NAA11" = 5 ] || { echo "× N-aa 重算帧写入点不是 5（现 $NAA11）：多一处就是有人在本机造「高德要重算」这句话"; fail=1; }
[ "$NAA12" = 1 ] || { echo "× N-aa 停止导航不再清重算帧（现 $NAA12）：留住上一次会话那句，会被读成这次还在重算"; fail=1; }
[ "$NAA13" = 1 ] || { echo "× N-aa 成功帧不再照抄高德的 errorCode（现 $NAA13）：本机开始给回执分档，那个档位没有来源"; fail=1; }
[ "$NAA14" = 1 ] || { echo "× N-aa 失败帧写法变了（现 $NAA14）：要么回执被并进了广播那一格，要么高德可能为空的错误文本被搬进了句子"; fail=1; }
[ "$NAA15" = 0 ] || { echo "× N-aa 本机开始自己要求重算/选路（现 $NAA15）：那是把「何时重算」的决定权从高德手里抢过来，指令第 ⑪ 条点名的假判定"; fail=1; }
[ "$NAA16" = 0 ] || { echo "× N-aa 把 onNaviRouteNotify/平行路当偏航通道（现 $NAA16）：那份枚举实测七条里没有偏航那一类"; fail=1; }
[ "$NAA17" = 0 ] || { echo "× N-aa 引擎层出现垂距（现 $NAA17）：阈值判定的第一步就是先算一个距离"; fail=1; }
[ "$NAA18" = 1 ] || { echo "× N-aa 页面不再订阅 recalc flow（现 $NAA18）：重算状态来源被换成了本机读数"; fail=1; }
[ "$NAA19" = 1 ] || { echo "× N-aa 页面不再走那一句唯一话术（现 $NAA19）：重算那一格开始本机拼句子"; fail=1; }
[ "$NAA20" = 0 ] || { echo "× N-aa 页面自己造重算帧（现 $NAA20）：正式面就有了第二个「高德说要重算」的来源"; fail=1; }
[ "$NAA21" = 0 ] || { echo "× N-aa 正式导航面出现本机的「已偏离线路」结论（现 $NAA21）：那句话只有高德或后端说得出"; fail=1; }
[ "$NAA22" = 0 ] || { echo "× N-aa 司机目录又出现垂距阈值比较（现 $NAA22）：那个阈值是本机自己挑的，没有来源认可过（指令第 ⑪ 条）"; fail=1; }
[ "$NAA23" = 1 ] || { echo "× N-aa 旧屏偏航不再只读后端那个位（现 $NAA23）：本地假判定回来了，而且和真判定 OR 在一起分不出谁说的"; fail=1; }
[ "$NAA24" = 0 ] || { echo "× N-aa 全工程又出现「本机判定偏航」的措辞（现 $NAA24）：界面开始替后端与高德宣告一个谁都没给的结论"; fail=1; }
[ "$NAB1" = 1 ] || { echo "× N-ab 缓存读出口不是恰好一把（现 $NAB1）：0＝缓存又变成只写不读（⑭ 那句披露没了）；≥2＝同一份表有两个读法，两边对'新不新鲜'的答案可以不同"; fail=1; }
[ "$NAB2" = 0 ] || { echo "× N-ab 旧的那两把出口又回来了（现 $NAB2）：cachedAt/snapshot 返回的是无状态的 Date 或整表快照，'过期了'与'本机压根没有'会被并成同一个 null"; fail=1; }
[ "$NAB3" = 1 ] || { echo "× N-ab TTL 常量不在了（现 $NAB3）：V21-13 要的那条过期时间没有唯一定义处，每条读路线各自判新旧"; fail=1; }
[ "$NAB4" = 1 ] || { echo "× N-ab 过期边界写法变了（现 $NAB4）：'相等'这一格被读成还新鲜，或整条过期分支被删；指令 §十三 的原话是 当前时间 < expiresAt 才算新鲜"; fail=1; }
[ "$NAB5" = 1 ] || { echo "× N-ab 抓取时间不再来自那个时钟入参（现 $NAB5）：用例无法固定'抓于 40 分钟前'这一格，过期只能靠真等 30 分钟"; fail=1; }
[ "$NAB6" = 0 ] || { echo "× N-ab 抓取时间改成生产代码现场取时（现 $NAB6）：写进去的那一刻不再由调用方给定，披露的那句'什么时候抓的'就没了可测来源"; fail=1; }
[ "$NAB7" -ge 1 ] || { echo "× N-ab 缓存表不再是并发容器（现 $NAB7）：算路是 suspend、读点在 UI，V21-13 点名的就是这条竞态"; fail=1; }
[ "$NAB8" = 0 ] || { echo "× N-ab 生产代码里又出现裸 LinkedHashMap（现 $NAB8）：迭代与淘汰不在同一把锁里，size 上限会被并发写穿"; fail=1; }
[ "$NAB9" = 1 ] || { echo "× N-ab 来源原文不再照抄后端那串（现 $NAB9）：披露句里那句「后端当时给的来源原文」就变成机器自己的标签，不是高德的字符串"; fail=1; }
[ "$NAB10" = 0 ] || { echo "× N-ab providerRaw 被换成本机枚举（现 $NAB10）：后端发了个本机没见过的来源名会被就近归成'高德'"; fail=1; }
[ "$NAB11" = 1 ] || { echo "× N-ab 过期那一格不是一种独立状态（现 $NAB11）：'有过一条但已过期'与'没有过'重新并成一句话（§八 ㉗）"; fail=1; }
[ "$NAB12" = 0 ] || { echo "× N-ab 过期格带上了几何（现 $NAB12）：过期即不可信，还能取出折线就是把'过期'念成'还能用'"; fail=1; }
[ "$NAB13" = 1 ] || { echo "× N-ab 写入不再同时看后端那一位（现 $NAB13）：后端说这条路线不可用时旧几何仍留在表里，'刷新过'会被读成'没刷也一样'（D-⑱-3）"; fail=1; }
[ "$NAB14" = 1 ] || { echo "× N-ab serverAvailable 不再传给 resolve（现 $NAB14）：判定与写入解耦，那位翻 false 也删不掉旧条目"; fail=1; }
[ "$NAB15" = 1 ] || { echo "× N-ab 后端那一位的读取排到了 resolve 之后（现 $NAB15）：传进去的是上一个决定，第一次失败仍会写进缓存"; fail=1; }
[ "$NAB16" = 1 ] || { echo "× N-ab 过期删除改成单参 remove（现 $NAB16）：会把别人刚写进去的新条目一起删掉，读到的'没有缓存'是假的"; fail=1; }
[ "$NAB17" = 1 ] || { echo "× N-ab 越界淘汰不再按写入序号（现 $NAB17）：并发下插入序不可靠，被淘汰的可能是刚写的那一条"; fail=1; }
[ "$NAB18" = 1 ] || { echo "× N-ab 缓存披露的读点不是恰好一处（现 $NAB18）：0＝⑭ 整轮白做；≥2＝同一份表两处说话，两句话术可以各说各的新鲜度"; fail=1; }
[ "$NAB19" = 0 ] || { echo "× N-ab 披露那一半开始交几何/折线（现 $NAB19）：离线几何进地图要给'无路线三态'加第四格并同轮重锚 MR/MV 族，不在这轮顺手做（D-⑭-2）"; fail=1; }
[ "$NAB20" = 3 ] || { echo "× N-ab 三格分支数不是 3（现 $NAB20）：命中/过期/没有必须各自一句，并格就是把两种真相念成一句"; fail=1; }
[ "$NAB21" = 0 ] || { echo "× N-ab 披露句里出现「不画任何示意线」（现 $NAB21）：那句话的出口在 missionRouteReasonOf，这里说第二遍就有两个答案（§八 ㉗）"; fail=1; }
[ "$NAB22" = 0 ] || { echo "× N-ab 生产代码里出现 graphVersion（现 $NAB22）：后端 VO／小程序／DemoData 三面 0 生产者，拿它当新鲜度校验就是凭空造权威"; fail=1; }
[ "$NAB23" = 0 ] || { echo "× N-ab 缓存键里出现哈希/随机数（现 $NAB23）：那串字符没有任何来源能解释，正是不许造的'看起来像指纹的东西'"; fail=1; }
[ "$NAB24" = 1 ] || { echo "× N-ab 失败原因不再经那一处披露出口（现 $NAB24）：司机看到的是裸 errorTextOf，本机存着什么这件事又没人说"; fail=1; }

[ "$NAC" = 1 ] || { echo "× N-ac 开关关掉那一格不再是纯层唯一出口（现 $NAC）：句子回到页面里就地拼，两条开关各说一套的可能性又回来了"; fail=1; }
[ "$NAC2" = 3 ] || { echo "× N-ac 页面调用开关话术的次数不是 3（现 $NAC2）：少一处就是有一行不再读「我的」里那个开关，多一处就是有人给同一件事造了第三种说法"; fail=1; }
[ "$NAC3" = 1 ] || { echo "× N-ac 那句骨架「开关是关的（在「我的」里）」在纯层不止一处或没了（现 $NAC3）：同一句话两个地方写，改一处漏一处"; fail=1; }
[ "$NAC4" = 0 ] || { echo "× N-ac 页面里又出现了那句骨架的字面（现 $NAC4）：出口被绕开，纯层那份改动不会再反映到这一屏"; fail=1; }
[ "$NAC5" = 0 ] || { echo "× N-ac 开关关着那一格又开始替高德宣告「高德给的车道」（现 $NAC5）：本机在这一刻说不出这句话"; fail=1; }
[ "$NAC6" = 0 ] || { echo "× N-ac 开关话术出口内部替高德说了「没给」或「给过」（现 $NAC6）：本机没做 ≠ 高德没给，这是 ⑨/⑩ 立下的那条"; fail=1; }
[ "$NAC7" = 4 ] || { echo "× N-ac 转向/车道/重算/播报四行不再各走自己的那一句（现 $NAC7）：四行并成一套措辞就是把四种成因混成一件事"; fail=1; }
[ "$NAC8" = 1 ] || { echo "× N-ac 车道那一格不再点名「我的」里的「车道级指引」（现 $NAC8）：司机按卡片找不到那条开关"; fail=1; }
[ "$NAC9" = 1 ] || { echo "× N-ac 播报那一格不再点名「我的」里的「语音播报」（现 $NAC9）：同一条卡片里有两个开关，不指名就等于让司机猜"; fail=1; }
[ "$NAC10" = 0 ] || { echo "× N-ac 正式导航面把开关说成「车道引导」（现 $NAC10）：那是旧调试屏那台的名字，「我的」里没有这一行"; fail=1; }
[ "$NAC11" = 0 ] || { echo "× N-ac 播报的 label 又被动态拼接（现 $NAC11）：这一屏的标签全是静态词，把高德的类型码塞进 label 会让标签宽窄随回执变"; fail=1; }
[ "$NAC12" = 1 ] || { echo "× N-ac 到近作业那一格不再点名「我的」里的「到近作业播报」（现 $NAC12）：卡片只说「开关是关的」，司机不知道去关的是哪一条"; fail=1; }
[ "$NAC13" = 1 ] || { echo "× N-ac「我的」里「到近作业播报」那一行的字面变了而卡片没跟着改（现 $NAC13）：两边名字逐字相等才算点名，改一边就指错地方"; fail=1; }
[ "$NRMA" = 1 ] || { echo "× N-rm 「要不要提醒」的判点不再是恰好一份（现 $NRMA）：两份判点就会一个在提醒、另一个说没到"; fail=1; }
[ "$NRMA2" = 2 ] || { echo "× N-rm 读这个判点的页面不是两处（现 $NRMA2：工作台 + 全屏导航）：少一处就是那一屏永远不提醒"; fail=1; }
[ "$NRMB" = 1 ] || { echo "× N-rm 重复出声的账本不是恰好一份（现 $NRMB）：两本账就会各响各的，响几遍没人说得清"; fail=1; }
[ "$NRMB2" = 2 ] || { echo "× N-rm 挂账本的页面不是两处（现 $NRMB2）：少一处就是那一屏只说不响"; fail=1; }
{ [ "$NRMC" = 1 ] && [ "$NRMC2" = 1 ]; } || { echo "× N-rm 间隔/上限常数不是各一份（现 $NRMC/$NRMC2）：节奏写进页面就会有两套"; fail=1; }
[ "$NRMD" = 0 ] || { echo "× N-rm 出声出口开始自己算时间/记账（现 $NRMD）：出口只管发出去，节奏归纯层——否则「响几遍」就有两个答案"; fail=1; }
{ [ "$NRME" = 2 ] && [ "$NRME2" = 1 ]; } || { echo "× N-rm 重复出声的调用/出口不是 2/1（现 $NRME/$NRME2）"; fail=1; }
[ "$NRME3" = 3 ] || { echo "× N-rm 出声降级出口不再是「一处定义 + 两处调用」（现 $NRME3）：多一处就是有人另开了一条不出通知的路"; fail=1; }
[ "$NRMF" = 0 ] || { echo "× N-rm 提醒被做成了弹窗（现 $NRMF）：用户同一句里要求「不能影响驾驶」，模态弹窗吃掉地图手势与返回键"; fail=1; }
[ "$NRMG" = 1 ] || { echo "× N-rm 工作台那一行不再读提醒（现 $NRMG）：上面那两个读数就不采信，扫描得先证明它看得见这一屏"; fail=1; }

# ---------- N-wd（D-177）：司机导航卡那三族内部原文不许上屏 ----------
# 用户口径（2026-10-19）："这个 release 版本不允许出现开发专业词汇"。前三处咽喉（更新/网络/天气）在 D-175 清了，
# 第四处就在司机天天看的导航卡上：`gate.detail`（`hasOrigin=false` 那一类）、`RouteCheck.Rejected.detail`
# （`road=…m straight=…m`、甚至 `origin.toString()`）、`SourceTag` 的 `else -> source`（认不出的枚举原样印出来）。
# ⚠ 两头各一刀：只钉"屏上干净"会把"把 detail 整个删了"读成绿 —— 那是把证据跟着措辞一起扔（D-176 同一族）。
#   所以"原文还在 detail / 还在日志"必须同时钉住。行为侧（每个不可点码都有 hint、屏上两句零内部词）
#   由 NavigationContractTest 那两条新用例钉，这里钉结构。
CMP="$SRC/core/ui/Components.kt"
DGS="$SRC/ui/driver/DriverGuideScreen.kt"
for f in "$CMP" "$DGS"; do
    [ -f "$f" ] || { echo "× 判据文件缺失：$f（先确认工程根/副本建对，别把缺文件读成计数 0）"; exit 2; }
done
NWD1=$(grep -n 'hint = ' "$NENG" | grep -vE "$NC" | wc -l | tr -d ' ')
NWD2=$(grep -n 'gate\.hint' "$UI" | grep -vE "$NC" | wc -l | tr -d ' ')
NWD3=$(grep -n 'InfoRow("差在哪一步", gate.detail)' "$UI" | grep -vE "$NC" | wc -l | tr -d ' ')
NWD4=$(grep -n 'gate\.detail' "$UI" | grep -vE "$NC" | wc -l | tr -d ' ')
NWD5=$(grep -n 'Log\.w("BUS_NAV", "\(route_check\|guide route\) rejected' "$UI" "$DGS" | grep -vE "$NC" | wc -l | tr -d ' ')
# 只数**生效代码**里把 detail 拼回屏上那一句的：日志行本身带 `${check.detail}` 是这一族正确的落点，必须排除
NWD6=$(grep -n 'check\.detail}\|result\.detail}' "$UI" "$DGS" | grep -vE "$NC" | grep -v 'Log\.' | wc -l | tr -d ' ')
NWD7=$(grep -n 'else -> source' "$CMP" | grep -vE "$NC" | wc -l | tr -d ' ')
NWD8=$(grep -n 'sourceTagLabel' "$CMP" | grep -vE "$NC" | wc -l | tr -d ' ')
NWD9=$(grep -n '"来源没认出来"' "$CMP" | grep -vE "$NC" | wc -l | tr -d ' ')
NWD10=$(grep -n 'detail = "hasOrigin=false\|detail = "sdkPresent=false\|detail = "initResult=null' "$NENG" | grep -vE "$NC" | wc -l | tr -d ' ')

printf 'N-wd hint 出口/屏上读 hint/旧那格残留/gate.detail 只在日志 : %s/%s/%s/%s（目标 8/1/0/1）\n' \
  "$NWD1" "$NWD2" "$NWD3" "$NWD4"
printf 'N-wd 拒收原文进日志/屏上拼原文/SourceTag 透原词/出口+兜底+原文 : %s/%s/%s/%s+%s+%s（目标 2/0/0/2+1+3）\n' \
  "$NWD5" "$NWD6" "$NWD7" "$NWD8" "$NWD9" "$NWD10"
[ "$NWD1" = 8 ] || { echo "× N-wd 引擎里「下一步该做什么」那一句不再是 8 处（现 $NWD1）：三处 Blocked + 五处入口结论，少一处就是某个不可点码没话可说"; fail=1; }
[ "$NWD2" = 1 ] || { echo "× N-wd 卡片读 hint 的次数不是 1（现 $NWD2）：多一处就是有人另开了一格念同一件事"; fail=1; }
[ "$NWD3" = 0 ] || { echo "× N-wd 「差在哪一步」又把诊断原文念回屏上了（现 $NWD3）：`hasOrigin=false` 这类字段名司机不该读到"; fail=1; }
[ "$NWD4" = 1 ] || { echo "× N-wd 引擎原文的出口不再是日志那一处（现 $NWD4）：撤了措辞不能顺手撤证据（D-176 同一族）"; fail=1; }
[ "$NWD5" = 2 ] || { echo "× N-wd 算路被拒的原文进日志不是两处（现 $NWD5）：卡片与引导页各有一处，少一处就是那一屏把证据扔了"; fail=1; }
[ "$NWD6" = 0 ] || { echo "× N-wd 又有屏上那句把 detail 拼回去了（现 $NWD6）：detail 按这个类自己的注释就是面向日志的那一半"; fail=1; }
[ "$NWD7" = 0 ] || { echo "× N-wd 来源标签退回把原词印上屏（现 $NWD7）：认不出来的枚举原样上屏就是这一族要修的病"; fail=1; }
{ [ "$NWD8" = 2 ] && [ "$NWD9" = 1 ]; } || { echo "× N-wd 来源措辞出口不是「一处定义 + 一处调用」或兜底句没了（现 $NWD8/$NWD9）：两屏会各说一套，或认不出来时悄悄不给标签"; fail=1; }
[ "$NWD10" = 3 ] || { echo "× N-wd 引擎原文被删了（现 $NWD10）：屏上撤词必须同轮把原文留在 detail 里，装机的人还要靠它查"; fail=1; }

if [ "$fail" = 0 ]; then echo "第 8 步导航引擎判据通过"; else echo "第 8 步导航引擎判据失败"; fi
exit "$fail"
