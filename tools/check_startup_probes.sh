#!/usr/bin/env bash
# 启动路径 / 首屏取数 / release 裁剪面（缺陷 #78，配 tools/startup_negative_controls.sh）
#
# 三格各自的病根：
# ① 启动路径上五件事全在主线程（`super.onCreate` / 高德合规 / Prefs.init / 通知渠道 / HTTP 缓存 / 会话恢复），
#    其中 `WorkManager.getInstance()` 那一件是首次调用才整包初始化（建库 + 恢复未完成任务），
#    而它的结果没有任何人需要在第一帧之前读到 ⇒ 它不该占主线程。分步计时进 logcat（`BUS_BOOT`），
#    没有分步读数时"打开慢半拍"只能靠猜，而猜错的方向是把 `Prefs.init` 挪后台 ——
#    那会让紧接着的 `Repo.restoreSession()` 读到空会话，比慢更糟。
# ② 闪屏自己在根上另发两笔首屏请求、只用来推进度条，结果丢在地上；首页自己发的正是同一对，
#    而且带坐标与半径 —— URL 不同，连 #79 之后的白名单磁盘缓存都对不上 ⇒ 冷启动白问两笔，
#    司机端第一屏更是两笔都用不上。现在阶段③改等"首屏那一轮真的收场"。
# ③ release 的 keep 规则文件里原本只有一行外来工程的占位（`# WatchCam watch app …`），规则数 0。
#    本格把它换成**有出处**的规则：native 侧按名字找类（扫 .so 数出来的），
#    WorkManager 的 worker 类名是写进数据库再反射实例化的（manifest 规则覆盖不到），
#    枚举常量名参与业务判定。`isMinifyEnabled` 今天仍是 false —— 开它的前置条件是"裁剪包过真机走查"，
#    这一格由 ST-e 钉住，不许悄悄翻。
#
# 写法沿用既有教训：先验输入文件；每个"目标 0"配同机制反证；计数只读产物或确切字面量；注释一律滤掉。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

M=app/src/main/java/com/shanxiang/buspost
APP=$M/BusPostApp.kt
ROOTUI=$M/ui/BusPostRoot.kt
HOME=$M/ui/user/HomeScreen.kt
WB=$M/ui/driver/WorkbenchScreen.kt
ST=$M/core/AppState.kt
SPLASH=$M/core/ui/SplashOverlay.kt
PGR=app/proguard-rules.pro
GRAD=app/build.gradle.kts
for f in "$APP" "$ROOTUI" "$HOME" "$WB" "$ST" "$SPLASH" "$PGR" "$GRAD"; do
    [ -f "$f" ] || { echo "× 判据输入缺失：$f"; exit 2; }
done

NC='(^|[0-9]+:)[[:space:]]*(\*|//)'
cnt() { grep -c "$2" "$1"; }
ncnt() { grep -n "$2" "$1" | grep -vE "$NC" | wc -l | tr -d ' '; }

# ---------- ST-a 闪屏不再替首屏重复取数 ----------
STA_PREFETCH=$(ncnt "$ROOTUI" 'async { attempt { Repo\.')
# 反证：同一族写法在首页里数得出（数不出就说明模式写宽/写窄，那个 0 是空的）
STA_HOME=$(cnt "$HOME" 'async { Repo\.')
STA_MARK=$(grep -rn 'markFirstScreenLoaded' "$M" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
STA_READ=$(cnt "$ROOTUI" 'AppState\.firstScreenLoaded')
STA_DECL=$(cnt "$ST" 'val firstScreenLoaded')

# ---------- ST-b 闪屏有兜底出口 ----------
STB_FALLBACK=$(cnt "$SPLASH" 'delay(4000)')
STB_READY=$(cnt "$ROOTUI" 'splashReady = true')
STB_PROGRESS=$(cnt "$ROOTUI" 'splashProgress = 100')

# ---------- ST-c 主线程那件事与分步计时 ----------
STC_IO_WRAP=$(cnt "$APP" 'withContext(Dispatchers.IO) { NotifyWorker.schedule')
STC_SCHEDULE=$(ncnt "$APP" 'NotifyWorker\.schedule')
# 反证：同一条 grep 机制在"没被 IO 包住"的形状上数得出东西（这里拿 scope.launch 本身当正对照）
STC_LAUNCH=$(cnt "$APP" 'scope.launch')
STC_MARKS=$(cnt "$APP" 'bootMark')
STC_TAG=$(cnt "$APP" 'BUS_BOOT')
# 目标 0：会话恢复不许跑到 Prefs.init 之前（顺序错了就是"永远读不到已登录"）。
# ⚠ 只看非注释行，并且取**首次**出现：BusPostApp 的 KDoc 里按讲解顺序提到了这两个名字，
#   不滤注释、或用最后一次出现，这条读的都是文档而不是代码（判据命中自己的老形）。
STC_ORDER=$(grep -n 'Prefs\.init(this)\|Repo\.restoreSession()' "$APP" | grep -vE "$NC" |
    awk -F: '{ if ($0 ~ /Prefs\.init/) { if (!p) p = $1 } else { if (!r) r = $1 } } END { print (p && r && r < p) ? 1 : 0 }')

# ---------- ST-d keep 规则面 ----------
# 只数规则文件里"以 - 开头的行"：注释行以 `#` 起头，不会被算进来（第一版误以为会，
# 差点把计数源改成 build 产物 configuration.txt —— 那是**陈旧产物**会随上一次构建状态漂，
# 判据簿在沙盒里根本没有 build 目录，同一本簿两个读数就是假闸）。
STD_RULES=$(grep -cE '^-(keep|keepclassmembers|dontwarn)' "$PGR")
# 反证：同一套"以 - 开头的规则行"里，只被行数闸门管着、没被逐条点名管着的 `keepattributes`
# 必须数得出非零。少了这一格，下面那个 `-ge 8` 可以被四条 dontwarn 加一行什么撑过去，
# 而点名规则全被删也照样"够 8 行"。
STD_ATTR=$(grep -c '^-keepattributes' "$PGR")
STD_FOREIGN=$(grep -ci 'watchcam' "$PGR")
# 反证：文件里确实有中文说明（外来占位行被换掉之后，如果整个文件只剩规则、解释没了，也说明改动没落对地方）
STD_DOC=$(grep -c '^#' "$PGR")
STD_AMAP=$(grep -c '^-keep class com\.amap\.api\.\*\*' "$PGR")
STD_NAVI=$(grep -c '^-keep class com\.autonavi\.\*\*' "$PGR")
STD_IDST=$(grep -c '^-keep class com\.alibaba\.idst\.\*\*' "$PGR")
STD_WORKER=$(grep -c '^-keep class com\.shanxiang\.buspost\.notify\.NotifyWorker' "$PGR")
# 两个枚举**必须分开两条写**：`…model.{Role,NavIcon}` 那种并列是 R8 解析不了的语法
# （实测 `minifyReleaseWithR8` 报 "Expected field or method name" 指在那一行），所以这里数 2。
STD_ENUM=$(grep -c '^-keepclassmembers enum com\.shanxiang\.buspost\.data\.model\.' "$PGR")

# ---------- ST-e 开裁剪那道闸（今天必须是"没开"，且必须有取证动作在册） ----------
# ⚠ 判据跑的是**沙盒副本**（负控那本只 copy 这几样），所以这里一律用 $ROOT 相对路径，
#   不写 "$SELF/…"：否则 BUSPOST_ANDROID 指到哪都数的同一个真身，ST-e 那条破口就咬不住。
STE_MINIFY_OFF=$(cnt "$GRAD" 'isMinifyEnabled = false')
STE_MINIFY_ALL=$(cnt "$GRAD" 'isMinifyEnabled')
# 取证脚本必须在场（它就是把 release 的 dex 逐字节扫一遍的那只手；缺了它，翻开关的人只能靠猜）
STE_EVIDENCE=$( [ -f "$ROOT/tools/release_minify_evidence.sh" ] && echo 1 || echo 0)

echo "ST-a 闪屏内预取(非注释) / 首页同族反证      = $STA_PREFETCH/$STA_HOME （目标 0/4）"
echo "ST-a 首屏收场写入点 / 根上读 / 声明         = $STA_MARK/$STA_READ/$STA_DECL （目标 3/1/1：1 定义 + 首页与工作台两处写）"
echo "ST-b 4 秒兜底 / 就绪写入 / 进度 100         = $STB_FALLBACK/$STB_READY/$STB_PROGRESS （目标 1/1/1）"
echo "ST-c Worker 走 IO / 出现次数 / launch 反证   = $STC_IO_WRAP/$STC_SCHEDULE/$STC_LAUNCH （目标 1/1/≥2）"
echo "ST-c 分步计时处数 / 标签 / 顺序颠倒          = $STC_MARKS/$STC_TAG/$STC_ORDER （目标 ≥5/3/0）"
echo "ST-d keep 规则行 / 外来项目名 / 说明行        = $STD_RULES/$STD_FOREIGN/$STD_DOC （目标 12/0/≥10）"
echo "ST-d 点名规则（amap/navi/idst/worker/枚举两行）  = $STD_AMAP/$STD_NAVI/$STD_IDST/$STD_WORKER/$STD_ENUM （目标 1/1/1/1/2）"
echo "ST-d keepattributes                         = $STD_ATTR （目标 ≥2）"
echo "ST-e minify=false 处数 / isMinifyEnabled 总数 / 取证脚本在场 = $STE_MINIFY_OFF/$STE_MINIFY_ALL/$STE_EVIDENCE （目标 2/2/1）"

fail=0
[ "$STA_PREFETCH" = 0 ] || { echo "× ST-a 闪屏里又长出'取了就丢'的请求（现 $STA_PREFETCH）：冷启动白问两笔，首屏那一轮还得再问一遍"; fail=1; }
[ "$STA_HOME" = 4 ] || { echo "× ST-a 反证掉了（首页同族写法现 $STA_HOME，期望 4：nb/bl/notices/products）：那个 0 可能是模式写窄了"; fail=1; }
[ "$STA_MARK" = 3 ] || { echo "× ST-a 首屏收场信号不是 1 定义 + 2 写入（现 $STA_MARK）：少一边就是那一侧的闪屏只能等 4 秒兜底"; fail=1; }
[ "$STA_READ" = 1 ] || { echo "× ST-a 根导航没在读 firstScreenLoaded（现 $STA_READ）：信号没人接，阶段③退回不会点亮"; fail=1; }
[ "$STA_DECL" = 1 ] || { echo "× ST-a firstScreenLoaded 声明数异常（现 $STA_DECL）"; fail=1; }
[ "$STB_FALLBACK" = 1 ] || { echo "× ST-b 那条 4 秒自动进入没了（现 $STB_FALLBACK）：取数挂住时用户被关在闪屏外，没有出口"; fail=1; }
[ "$STB_READY" = 1 ] || { echo "× ST-b 就绪写入不是恰好一处（现 $STB_READY）：又多了一条'谁都能宣布已就绪'的通路"; fail=1; }
[ "$STB_PROGRESS" = 1 ] || { echo "× ST-b 进度到 100 的写入不是恰好一处（现 $STB_PROGRESS）"; fail=1; }
[ "$STC_IO_WRAP" = 1 ] || { echo "× ST-c WorkManager 调度没被压在 IO 上（现 $STC_IO_WRAP）：首次初始化回到主线程"; fail=1; }
[ "$STC_SCHEDULE" = 1 ] || { echo "× ST-c Worker 调度出现不止一处（现 $STC_SCHEDULE）：又多一次冷启动排队"; fail=1; }
[ "$STC_LAUNCH" -ge 2 ] || { echo "× ST-c 反证掉了（scope.launch 现 $STC_LAUNCH）：这条书的后台通路可能整块不在"; fail=1; }
[ "$STC_MARKS" -ge 5 ] || { echo "× ST-c 分步计时少于 5 处（现 $STC_MARKS）：启动慢 again 时又没有读数可看，只能猜"; fail=1; }
[ "$STC_TAG" = 3 ] || { echo "× ST-c BUS_BOOT 标签不是三处（现 $STC_TAG：KDoc 承诺的那句 + bootMark + Worker 那条自测）：少一处就是日志通道与注释里说的 tag 不一致，取不到数"; fail=1; }
[ "$STC_ORDER" = 0 ] || { echo "× ST-c restoreSession 排到了 Prefs.init 之前（现 $STC_ORDER）：会话永远读成空，等于每次都要重登"; fail=1; }
# 规则行**按实测钉死 12 条**（4 条 -keep 类 + 2 条枚举成员 + 2 条 keepattributes + 4 条 dontwarn）。
# 不写成 -ge：加一条必须连理由一起加（每条都要点名它的反射入口），减一条更要紧 ——
# 这条一红就是"有人删了别人看不懂的规则"，那是只有下一次发布才会炸的那种改动。
[ "$STD_RULES" = 12 ] || { echo "× ST-d keep 规则行不是 12（现 $STD_RULES）：那条外来占位行回来了、有人删了有出处的规则，或加了没点名的规则"; fail=1; }
[ "$STD_FOREIGN" = 0 ] || { echo "× ST-d 规则里还留着别的工程的名字（现 $STD_FOREIGN）：这份文件到底在说哪个工程，两个答案"; fail=1; }
[ "$STD_DOC" -ge 10 ] || { echo "× ST-d 规则的解释行少于 10（现 $STD_DOC）：每条反射入口都要点名，不然下轮没人敢删"; fail=1; }
for v in "$STD_AMAP" "$STD_NAVI" "$STD_IDST" "$STD_WORKER"; do
    [ "$v" = 1 ] || { echo "× ST-d 有一条点名规则不在场或变成多处（现 $v）：amap/autonavi/idst/NotifyWorker，少一条都是'开裁剪那天才炸'的那种"; fail=1; }
done
[ "$STD_ENUM" = 2 ] || { echo "× ST-d 枚举那两条点名规则不是 2（现 $STD_ENUM）：并列写法 R8 解析不了（实测报错），少一条就是 Role/NavIcon 里有一族的名字会被缩写"; fail=1; }
[ "$STD_ATTR" -ge 2 ] || { echo "× ST-d keepattributes 少于两条（现 $STD_ATTR）：行号或泛型签名被抹，线上栈与高德回调成废字"; fail=1; }
[ "$STE_MINIFY_OFF" = 2 ] || { echo "× ST-e isMinifyEnabled = false 不是两处（现 $STE_MINIFY_OFF）：debug 与 release 各一处，多一处说明有人另开了构建面"; fail=1; }
[ "$STE_MINIFY_ALL" = 2 ] || { echo "× ST-e isMinifyEnabled 总处数不是 2（现 $STE_MINIFY_ALL）：与上一条互为反证"; fail=1; }
[ "$STE_EVIDENCE" = 1 ] || { echo "× ST-e 产物级取证脚本不在场（tools/release_minify_evidence.sh）：没人扫过 dex 就翻 isMinifyEnabled，等于拿发布包试错"; fail=1; }

if [ "$fail" = 0 ]; then echo "启动与裁剪面判据通过"; else echo "启动与裁剪面判据失败"; fi
exit "$fail"
