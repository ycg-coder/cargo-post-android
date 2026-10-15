#!/usr/bin/env bash
# 网络卫生四格（缺陷 #79，配 tools/net_hygiene_negative_controls.sh）
#
# 背景（真机 + 审计两侧都碰过）：
# 1. 网络拦截器给**每一条 GET** 都盖 `max-age=60` ⇒ "写完立刻回读"那一族会读 60 秒前的旧答案。
#    全站状态口径是"以回读为准"（§26/§27），司机点完"到达"那轮回读若命中缓存，界面就把刚才那次动作抹了。
# 2. `newCall(request).execute()` 阻塞在 IO 线程里，协程取消时没人 cancel Call ⇒ 已离场的页面还占着连接池。
# 3. 退登/401 只清 token 与档案，`cacheDir/http_cache` 里上一位的班次、订单、地址还活着 ⇒ 换账号第一屏能看到别人的数据。
# 4. 后端一直在发 `expiresTime`，本机存了却从没读过 ⇒ 明知过期也照发，等一个必然的 401 才被踢回登录页。
#
# 本簿钉的是**形状**（用例钉的是行为，见 NetHygieneContractTest / SessionRefreshContractTest）。
# 写法沿用既有教训：先验输入文件；每个"目标 0"配同机制反证；计数用确切字面量并按注释过滤。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

A=app/src/main/java/com/shanxiang/buspost/core/net/Api.kt
R=app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt
TN=app/src/test/java/com/shanxiang/buspost/core/net/NetHygieneContractTest.kt
TS=app/src/test/java/com/shanxiang/buspost/data/repo/SessionRefreshContractTest.kt
M=app/src/main/java/com/shanxiang/buspost
for f in "$A" "$R" "$TN" "$TS"; do
    [ -f "$f" ] || { echo "× 判据输入缺失：$f"; exit 2; }
done

NC='(^|[0-9]+:)[[:space:]]*(\*|//)'
cnt() { grep -c "$2" "$1"; }
ncnt() { grep -n "$2" "$1" | grep -vE "$NC" | wc -l | tr -d ' '; }
# 白名单块（从声明到顶格的右括号）：块内计数才等于"真进了缓存面的端点"
wl() { sed -n '/private val CacheableGetPaths/,/^)/p' "$A" | grep -cE "$1"; }

# ---------- NH-a GET 缓存策略按白名单给，默认 no-store ----------
NHA_WL=$(wl '^    "/')
NHA_MAX=$(cnt "$A" '"max-age=60"')
NHA_NOSTORE=$(cnt "$A" '"no-store"')
NHA_POLICY=$(cnt "$A" 'cachePolicyFor(')

# ---------- NH-b 协程取消穿透到 socket ----------
NHB_SITE=$(cnt "$A" '\.callCancellable(')
NHB_ON=$(cnt "$A" 'invokeOnCancellation { call.cancel() }')
NHB_CLOSE=$(cnt "$A" 'else response.close()')
NHB_EXEC=$(grep -rn '\.execute()' "$M" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
# 反证：同一条模式不过滤注释时数得到旧形状那一句（证明不是模式写窄了才 0）
NHB_EXEC_ALL=$(grep -rn '\.execute()' "$M" --include=*.kt | wc -l | tr -d ' ')

# ---------- NH-c 换账号/退登要把磁盘响应缓存清掉 ----------
NHC_DEF=$(cnt "$A" 'fun clearCache()')
NHC_CALL=$(cnt "$R" 'Api.clearCache()')
NHC_EVICT=$(cnt "$A" 'cache.evictAll()')
# 0 目标：`http` 是 by lazy，客户端长期引用同一个 Cache 对象。删目录或把字段置 null
# 会让后续 GET 静默丢掉缓存（OkHttp 把缓存标成 fatal），看着像"清得很干净"其实是把功能改没了。
NHC_DELREC=$(cnt "$A" 'deleteRecursively')
NHC_NULLCACHE=$(cnt "$A" 'httpCache = null')
NHC_INSTALL=$(cnt "$A" 'httpCache?.let { cache(it) }')
NHC_LOGOUT=$(grep -A 8 'fun clearSessionLocal' "$R" | grep -c 'Api.clearCache()')
NHC_PREFS=$(grep -A 8 'fun clearSessionLocal' "$R" | grep -c 'Prefs.clearSession()')

# ---------- NH-d expiresTime 要能被网络层读到 ----------
NHD_SINGLE=$(cnt "$R" 'Prefs.expiresAt =')
NHD_SET=$(cnt "$R" 'setSessionExpiry')
NHD_TOKEN=$(ncnt "$R" 'Api.tokenExpiresAt')
# ⚠ 必须滤注释：Api.kt 里 `Prefs.expiresAt` 只在字段说明里出现过一次，不滤的话这条 0 目标会当场红，
#   而红的原因是一条注释 —— 与"网络层不许自己读盘"这条规矩无关。
NHD_NOTIN_API=$(ncnt "$A" 'Prefs.expiresAt')
# 反证：同一条 NC 过滤在 Api.kt 里数得出"1 声明 + 1 读"，证明上面那个 0 不是过滤写宽了
NHD_DECLREAD=$(ncnt "$A" 'tokenExpiresAt')
NHD_READ=$(cnt "$A" 'tokenExpiresAt in 1..nowMillis')
NHD_GUARD_AUTH=$(cnt "$A" 'attempt == 0 \&\& !path.contains("/auth/")')
NHD_GUARD_RATE=$(cnt "$A" 'lastProactiveRenewAt > 60_000L')

# ---------- NH-e 用例在册（形状能改对，行为得有用例兜） ----------
NHE_NH=$(cnt "$TN" '@Test')
NHE_SR=$(cnt "$TS" '@Test')

# ---------- NH-f 白名单里不许混进"写完就回读"那一族 ----------
NHA_DIRTY=$(wl 'unread|handover|messages|product-order|/legs|current-leg')
# 反证：同一块用同一机制数得出 browse 类那一条
NHA_CLEAN=$(wl 'notice')

echo "NH-a 白名单条数 / 60 秒支 / no-store 支 / 判定函数处数 = $NHA_WL/$NHA_MAX/$NHA_NOSTORE/$NHA_POLICY （目标 8/1/1/2）"
echo "NH-b callCancellable 处数 / cancel / close / 残留 execute = $NHB_SITE/$NHB_ON/$NHB_CLOSE/$NHB_EXEC （目标 4/1/1/0，反证含注释 $NHB_EXEC_ALL）"
echo "NH-c clearCache 定义/调用/evictAll/两处出口 = $NHC_DEF/$NHC_CALL/$NHC_EVICT/$NHC_LOGOUT+$NHC_PREFS （目标 1/1/1/1+1）"
echo "NH-c 禁删目录 / 禁置空 = $NHC_DELREC/$NHC_NULLCACHE （目标 0/0，反证装缓存那一处 $NHC_INSTALL）"
echo "NH-d 到期写点唯一/单函数 3 处/网络层字段 2 处/Api 不读盘 = $NHD_SINGLE/$NHD_SET/$NHD_TOKEN/$NHD_NOTIN_API （目标 1/3/2/0，反证 Api 里声明+读 $NHD_DECLREAD）"
echo "NH-d 读那一处 / 两道护栏 = $NHD_READ/$NHD_GUARD_AUTH+$NHD_GUARD_RATE （目标 1/1+1）"
echo "NH-e 网络卫生用例 / 会话续期用例 = $NHE_NH/$NHE_SR （目标恰好 5/9）"
echo "NH-f 白名单里混进的回读类端点 = $NHA_DIRTY （目标 0，反证同机制数得出 notice $NHA_CLEAN）"

fail=0
[ "$NHA_WL" = 8 ] || { echo "× NH-a 白名单不是 8 条（现 $NHA_WL）：又变成一刀切，或新端点没经判定就进了缓存面"; fail=1; }
[ "$NHA_MAX" = 1 ] || { echo "× NH-a 60 秒字面不是恰好一处（现 $NHA_MAX）：多处等于有人另开了第二条缓存策略"; fail=1; }
[ "$NHA_NOSTORE" = 1 ] || { echo "× NH-a no-store 默认支不见了或变成多处（现 $NHA_NOSTORE）：默认不再是 no-store"; fail=1; }
[ "$NHA_POLICY" = 2 ] || { echo "× NH-a 判定函数不是 1 定义 + 1 调用（现 $NHA_POLICY）：多一个调用点就是第二条缓存策略通路"; fail=1; }
[ "$NHB_SITE" = 4 ] || { echo "× NH-b callCancellable 不是 1 定义 + 3 调用点（现 $NHB_SITE）：有请求通路绕过了可取消出口"; fail=1; }
[ "$NHB_ON" = 1 ] || { echo "× NH-b 取消回调没了（现 $NHB_ON）：协程取消不再掐断 Call，离场页面还占着连接池"; fail=1; }
[ "$NHB_CLOSE" = 1 ] || { echo "× NH-b 取消后到达的响应没被 close（现 $NHB_CLOSE）：连接与响应体泄漏"; fail=1; }
[ "$NHB_EXEC" = 0 ] || { echo "× NH-b 还有非注释的 execute()（现 $NHB_EXEC）：阻塞式发请求又回来了，取消穿不透"; fail=1; }
[ "$NHB_EXEC_ALL" = 1 ] || { echo "× NH-b 反证掉了（含注释的 execute() 现 $NHB_EXEC_ALL，期望 1）：那条旧形状说明都不在，0 可能是模式写窄"; fail=1; }
[ "$NHC_DEF" = 1 ] || { echo "× NH-c clearCache 定义数异常（现 $NHC_DEF）"; fail=1; }
[ "$NHC_CALL" = 1 ] || { echo "× NH-c 会话出口没调 clearCache（现 $NHC_CALL）：换账号还能看见上一个人的数据"; fail=1; }
[ "$NHC_EVICT" = 1 ] || { echo "× NH-c 清理动作不是 evictAll（现 $NHC_EVICT）"; fail=1; }
[ "$NHC_DELREC" = 0 ] || { echo "× NH-c clearCache 里出现删目录（现 $NHC_DELREC）：lazy 客户端还引用这个 Cache，删了等于把缓存弄残"; fail=1; }
[ "$NHC_NULLCACHE" = 0 ] || { echo "× NH-c 把 httpCache 置空（现 $NHC_NULLCACHE）：换实例会让两个 Cache 抢同一份 journal"; fail=1; }
[ "$NHC_INSTALL" = 1 ] || { echo "× NH-c 反证掉了（装缓存那一处现 $NHC_INSTALL）：上面两个 0 可能是没客户端可比"; fail=1; }
[ "$NHC_LOGOUT" = 1 ] || { echo "× NH-c clearSessionLocal 体内没有 clearCache（现 $NHC_LOGOUT）：调点在别处，退登那条路没走"; fail=1; }
[ "$NHC_PREFS" = 1 ] || { echo "× NH-c clearSessionLocal 体内没有 Prefs.clearSession（现 $NHC_PREFS）：清缓存与清档案被拆开了"; fail=1; }
[ "$NHD_SINGLE" = 1 ] || { echo "× NH-d 到期落盘不止一个写点（现 $NHD_SINGLE）：两处分别写就会有一处忘"; fail=1; }
[ "$NHD_SET" = 3 ] || { echo "× NH-d setSessionExpiry 不是 1 定义 + 2 调用（现 $NHD_SET）：登录或续期有一处没接上"; fail=1; }
[ "$NHD_TOKEN" = 2 ] || { echo "× NH-d 网络层到期字段在 Repo 不是 2 处非注释（现 $NHD_TOKEN）：写入与清零少一处"; fail=1; }
[ "$NHD_NOTIN_API" = 0 ] || { echo "× NH-d 网络层直接读 Prefs.expiresAt（现 $NHD_NOTIN_API）：又多一条到期时刻的真值来源"; fail=1; }
[ "$NHD_DECLREAD" = 2 ] || { echo "× NH-d 反证掉了（Api 里 tokenExpiresAt 非注释现 $NHD_DECLREAD，期望 2：1 声明 + 1 读）：那个 0 可能是 NC 过滤写宽了"; fail=1; }
[ "$NHD_READ" = 1 ] || { echo "× NH-d 发请求前没人读过到期时刻（现 $NHD_READ）：又回到明知过期也照发"; fail=1; }
[ "$NHD_GUARD_AUTH" = 1 ] || { echo "× NH-d 认证接口那道护栏没了（现 $NHD_GUARD_AUTH）：续期套续期"; fail=1; }
[ "$NHD_GUARD_RATE" = 1 ] || { echo "× NH-d 一分钟节流没了（现 $NHD_GUARD_RATE）：时钟偏快时每条请求都先打一次续期"; fail=1; }
[ "$NHE_NH" = 5 ] || { echo "× NH-e 网络卫生用例不是 5 条（现 $NHE_NH）：形状有判据、行为得有用例兜"; fail=1; }
[ "$NHE_SR" = 9 ] || { echo "× NH-e 会话续期用例不是 9 条（现 $NHE_SR）：主动续期那三条被撤了"; fail=1; }
[ "$NHA_DIRTY" = 0 ] || { echo "× NH-f 白名单里混进回读类端点（现 $NHA_DIRTY）：写完再读会拿到 60 秒前的旧状态"; fail=1; }
[ "$NHA_CLEAN" = 1 ] || { echo "× NH-f 反证掉了（同机制数不出 notice，现 $NHA_CLEAN）：NH-f 那个 0 可能是块没截到"; fail=1; }

if [ "$fail" = 0 ]; then echo "网络卫生判据通过"; else echo "网络卫生判据失败"; fi
exit "$fail"
