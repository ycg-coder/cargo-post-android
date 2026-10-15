#!/usr/bin/env bash
# 网络卫生判据（NH-a…NH-f，缺陷 #79）的负控制：逐条破口 → 确认 exit 1 且报的就是这一条。
# 只在临时目录副本上动手，工程真身不受影响（纯文本判据，不需要编译 ⇒ 可与其他脚本并行）。
# 用法：bash tools/net_hygiene_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3

PKG=com/shanxiang/buspost
PR="$SRC/tools/check_net_hygiene_probes.sh"
SC=/tmp/nh_neg.$$
mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
A="$SC/app/src/main/java/$PKG"
API="$A/core/net/Api.kt"
REPO="$A/data/repo/Repo.kt"
TNH="$SC/app/src/test/java/$PKG/core/net/NetHygieneContractTest.kt"
for f in "$API" "$REPO" "$TNH"; do
    [ -f "$f" ] || { echo "✘ 副本缺 $f，下面每一条都会假绿"; exit 2; }
done
CLEAN=/tmp/nh_neg_clean.$$; cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

OKN=0; AMB=0; BAD=0
ctl() { # $1=判据编号 $2=说明
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" = 1 ] && echo "$out" | grep -q "^× $1"; then
    OKN=$((OKN + 1))
    echo "✔ $1  $2 → exit 1：$(echo "$out" | grep "^× $1" | head -1)"
  elif [ "$code" = 1 ]; then
    AMB=$((AMB + 1))
    echo "△ $1  $2 → exit 1，但抓到的是别的判据：$(echo "$out" | grep '^×' | head -1)"
  else
    BAD=$((BAD + 1))
    echo "✘ $1  $2 → 负控制失效（exit $code），这条判据等于没判据"
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
swap() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swap 锚点没命中，破口根本没注入（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
assert old not in new or old == new, "SELF-MATCH: 替换文本里不许带锚点原文"
open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
PY
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | tail -1)"
[ "$base_code" = 0 ] || { echo "✘ 基线未通过，负控结果一律不采信"; exit 2; }

# ---- NH-a：默认不再是 no-store ----
# 1 一刀切回归：所有 GET 都给 60 秒（那条"写完立刻回读"的病根）
swap "$API" '    if (CacheableGetPaths.any { suffix -> path.endsWith(suffix) }) "max-age=60" else "no-store"' \
            '    "max-age=60"'
ctl NH-a "白名单判定被换成一刀切 60 秒：回读又拿到旧答案"

# 2 白名单悄悄多一条（近名端点混进来）
#    ⚠ 不能用 swap：替换文本必然带着锚点那一行（追加一行的写法一定包含原文），会被 SELF-MATCH 挡掉。
python - "$API" <<'PY' || { echo "✘ 破口没注入（这条负控不成立）"; exit 2; }
import sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
old = '    "/transport/send/stations",\n)'
assert old in s, "ANCHOR-MISS: 白名单收尾那两行不在"
open(p, 'w', encoding='utf-8').write(s.replace(old, '    "/transport/send/stations",\n    "/transport/send/order-detail",\n)', 1))
PY
ctl NH-a "白名单长出一条没人审过的端点：缓存面与声明面不再相等"

# ---- NH-b：取消不再穿透 ----
# 3 退回阻塞式 execute()（调用点少一处 + 残留一条非注释 execute）
swap "$API" '            http.callCancellable(request)' '            http.newCall(request).execute()'
ctl NH-b "又有一个通路走阻塞 execute：协程取消掐不断它"

# 4 取消回调被摘（enqueue 还在，但没人 cancel Call）
swap "$API" '        cont.invokeOnCancellation { call.cancel() }' '        cont.invokeOnCancellation { Unit }'
ctl NH-b "取消只丢结果不丢请求：连接池还替已离场的页面跑完"

# 5 取消后到达的响应不再 close
swap "$API" '                if (cont.isActive) cont.resume(response) else response.close()' \
            '                if (cont.isActive) cont.resume(response) else Unit'
ctl NH-b "取消后才到的响应没人关：连接与响应体漏走"

# 6 反证那一格：把说明旧形状的注释里那句 execute() 抹掉 ⇒ 含注释的读数也该掉到 0
swap "$API" '{ newCall(request).execute() }' '{ 旧的阻塞式发请求 }'
ctl NH-b "反证掉了：模式连注释里那条旧形状都数不到，那个 0 是空的"

# ---- NH-c：会话卫生 ----
# 7 清缓存的调用点被摘
swap "$REPO" '        Api.clearCache()' '        Api.demoSession = false'
ctl NH-c "会话出口不再清磁盘缓存：换账号第一屏还是上一个人的数据"

# 8 用删目录代替 evictAll（lazy 客户端还引用这个 Cache）
swap "$API" '        runCatching { cache.evictAll() }' '        runCatching { cache.directory().deleteRecursively() }'
ctl NH-c "清缓存改成删目录：后续 GET 静默丢掉缓存，看着像清干净了"

# 9 把字段置空（换实例 ⇒ 两个 Cache 抢同一份 journal）
swap "$API" '        val cache = httpCache ?: return' '        val cache = clientCache ?: return
        httpCache = null'
ctl NH-c "clearCache 改成把字段置空：真正在用的那个客户端没被清"

# ---- NH-d：expiresTime 要能被读到 ----
# 10 续期那处绕开唯一写点，自己写 Prefs ⇒ 落盘与网络层不再同源（这一处写 0 = 从没续过期）
swap "$REPO" '        Prefs.refreshToken = p.str("refreshToken").ifEmpty { refresh }' \
              '        Prefs.expiresAt = 0L'
ctl NH-d "到期时刻长出第二个写点：一处写 0 就等于从没续过期"

# 11 发请求前不读到期时刻（那一处判断被摘）
swap "$API" '            tokenExpiresAt in 1..nowMillis && nowMillis - lastProactiveRenewAt > 60_000L' \
            '            nowMillis > 0L && nowMillis - lastProactiveRenewAt > 60_000L'
ctl NH-d "明知过期也照发：又回到等一个必然的 401 才被踢回登录"

# 12 认证接口那道护栏被摘（续期套续期）
swap "$API" '        if (attempt == 0 && !path.contains("/auth/") &&' '        if (attempt == 0 &&'
ctl NH-d "续期请求自己也会触发续期：递归"

# 13 一分钟节流被摘（时钟偏快就是续期风暴）
swap "$API" 'nowMillis - lastProactiveRenewAt > 60_000L' 'nowMillis - lastProactiveRenewAt >= 0L'
ctl NH-d "每条请求前面都先打一次续期：比原来的 401 更糟"

# ---- NH-e：行为面得有用例在册 ----
# 14 取消那一条用例被撤（形状判据还在，行为没人兜）
python - "$TNH" <<'PY' || { echo "✘ 破口文件没写成（这条负控不成立）"; exit 2; }
import sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
old = "    @Test\n    fun `协程取消要穿透到这条请求"
assert old in s, "ANCHOR-MISS: " + old
s = s.replace(old, "    // 用例被撤（判据簿得红）\n    fun `协程取消要穿透到这条请求", 1)
open(p, 'w', encoding='utf-8').write(s)
PY
ctl NH-e "网络卫生用例少一条：只剩 grep，没有真发请求的那一半"

# ---- NH-f：白名单里不许混进"写完就回读"那一族 ----
# 15 把公告那条换成未读数（条目数不变 ⇒ 只有 NH-f 该红）
swap "$API" '    "/transport/notice/list",' '    "/transport/notification/unread-count",'
ctl NH-f "未读数进了缓存面：读完之后 60 秒内看不见新消息"

echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
# 破口条数是按上面一条条数出来的（15），不是抄别的簿的数字：少一条就是有破口没跑成。
echo "[汇总] ✔=$OKN △=$AMB ✘=$BAD（期望 15/0/0）"
[ "$OKN" = 15 ] && [ "$AMB" = 0 ] && [ "$BAD" = 0 ] || { echo "× 网络卫生负控没跑满或没咬住"; exit 1; }
