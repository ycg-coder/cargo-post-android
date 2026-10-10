#!/usr/bin/env bash
# 赛道⑭ 离线真实道路缓存**用例**的负控制（指令 §十三 / 队列 #217 #249 / N-6）：逐条改坏源码，
# 要求 `RouteOfflineCacheContractTest`（7 条）与 `RealRoadRouteValidatorTest` 里缓存那 4 条
# **具名那一条**必须红。
#
# 分工（与 ⑫⑬ 那一族同形，见 §八 ㊲）：
#   `tools/navigation_negative_controls.sh` 的 NAB1…NAB20 / N-x 那一族 = **结构**半边（沙盒副本，
#     判"写法有没有被人换掉"：缓存读点是不是恰好一处、容器是不是并发的那一种、TTL 是不是 const…）；
#   本书 = **行为**半边（就地改源码 + gradle 真跑）——判"那 11 条用例是不是装饰"。
#   只有这一半能回答"把 fetchedAtMillis 换成现场取时，有没有人会叫"。
#
# 10 条破口与具名用例配对（⚠ 一条破口可能同时红掉多条用例，例如 B-3 会把契约那本与校验器那本一起打红；
# 判据只要求**具名那一条在场**，多红不扣分；反过来"gradle 红了但红的不是那一条"记 ✘）：
#   B-1  写入时把来源原文换成归一后的枚举名        → `命中时披露抓取时间与后端当时的来源原文`
#   B-2  过期边界从"相等即过期"松成"超过才过期"    → `过期条目读不到几何并且当场被删掉`
#   B-3  过期那一格照样把几何交出去               → `过期条目只留下记录不留几何并且读到即删`
#   B-4  后端那一位否决了仍然写进缓存             → `后端自己说没有真实道路时既不写入也删掉旧条目`
#   B-5  抓取时间改成读的那一刻现场取时           → `抓取时间来自写入那一次的时钟入参而不是读的那一刻`
#   B-6  越界时淘汰最新的一条而不是最旧的         → `缓存条数受控且淘汰最旧的一条`
#   B-7  没有缓存那格也要补一句像样的话           → `没有缓存时失败原因原样返回`
#   B-8  过期那格把后端当时的来源原文念出来       → `过期条目只留下记录不留几何并且读到即删`（与 B-3 同一条用例，咬的是另一格断言：!contains("AMAP")）
#   B-9  时长档位被抹平成恒"不足 1 分钟"           → `时长口径只分三档且负数不炸`
#   B-10 演示面来源被写进可信名单                 → `演示面来源从未进过缓存`
#
# ⚠ 覆盖面缺口（不假装这一本什么都能打破）：
#   · `ConcurrentHashMap` 换回 `LinkedHashMap`、两参 `remove(key, entry)` 换成单参 `remove(key)`
#     —— 这两条在单线程 JVM 用例里不必然红（并发用例只有 8 线程各写各的 key，撞不坏也不报错），
#     强行配一条"应该红"就是编造判据。它们由 NAB7/NAB8、NAB16 在**结构**半边钉住，行为半边记为未覆盖。
#   · `serverAvailable` 这一位的**读取时机**（Repo.kt 里排在 resolve 之前还是之后）不在本书：
#     那是 Repo 的解析路径，注入会打到网络替身那本而不是缓存那本，由 NAB15 钉住。
#
# ⚠ 本脚本会**就地临时修改源码**并在结束时原样恢复；一次只允许跑一个（negctl_lock）。
#   退出前必须看到 [恢复] cmp 一致 + 用例重新通过 + 结构判据重新通过，否则视为未收口。
# ⚠ 起跑前有一道**预检**（10 条锚点在目标文件里各恰好命中 1 次 + 10 条具名用例在对应测试文件里各命中 1 次），
#   预检不过直接 exit 5，**一条破口都不注入**：锚点若已漂，逐条注入会一边报"锚点没命中"
#   一边继续烧掉十轮 gradle，最后既没证据也没时间。
# ⚠ 跑过 `--tests` 过滤之后 test-results 目录只剩这两本 ⇒ 读 test-results 的判据簿
#   （notify_result / oss_credits）会 rc=2，必须等全量 `--rerun-tasks` 之后再单本复跑。
#   台账口径同 D-052：本簿若晚于负控全批落盘，批次那行的"共 N 本"**不含本簿**，本簿以单本实测计。
# 用法：bash tools/offline_route_cache_negative_controls.sh
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2

RR=app/src/main/java/com/shanxiang/buspost/core/map/RouteRepository.kt
OFF=app/src/main/java/com/shanxiang/buspost/data/repo/OfflineRouteDisclosure.kt
RS=app/src/main/java/com/shanxiang/buspost/core/map/RouteSource.kt
CT1=app/src/test/java/com/shanxiang/buspost/data/repo/RouteOfflineCacheContractTest.kt
CT2=app/src/test/java/com/shanxiang/buspost/core/map/RealRoadRouteValidatorTest.kt
CLS1=com.shanxiang.buspost.data.repo.RouteOfflineCacheContractTest
CLS2=com.shanxiang.buspost.core.map.RealRoadRouteValidatorTest
JVMARGS='-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=GBK'
PROBE=tools/check_navigation_probes.sh
LOGDIR=/tmp/orc_neg
mkdir -p "$LOGDIR"
BAKRR=/tmp/orc_rr.bak.$$
BAKOFF=/tmp/orc_off.bak.$$
BAKRS=/tmp/orc_rs.bak.$$

# 预检零：五个落点都要在场，缺任何一个就是基线不成立（而不是把"0 命中"演成绿）
for f in "$RR" "$OFF" "$RS" "$CT1" "$CT2"; do
  [ -f "$f" ] || { echo "✘ 缺 $f ⇒ 本书作废（不注入、不跑 gradle）"; exit 2; }
done

cp "$RR" "$BAKRR"; cp "$OFF" "$BAKOFF"; cp "$RS" "$BAKRS"
restore() { cp "$BAKRR" "$RR"; cp "$BAKOFF" "$OFF"; cp "$BAKRS" "$RS"; }
trap 'restore; rm -f "$BAKRR" "$BAKOFF" "$BAKRS"; negctl_release' EXIT

IDS=(); FILES=(); OLDS=(); NEWS=(); NAMEDS=(); NFILES=(); DESCS=()
# 锚点一律用**单引号**字面量传进来：双引号里的 `${offlineCacheAgeText(...)}` 会被 bash 先展开，
# 注入静默不发生（㊱ ⑦ 同族坑）。
reg() { IDS+=("$1"); FILES+=("$2"); OLDS+=("$3"); NEWS+=("$4"); NAMEDS+=("$5"); NFILES+=("$6"); DESCS+=("$7"); }

reg 'B-1' "$RR" \
  '                providerRaw = sourceRaw.orEmpty(),' \
  '                providerRaw = "AMAP",' \
  '命中时披露抓取时间与后端当时的来源原文' "$CT1" \
  '缓存里那份来源被换成归一后的枚举名：后端说"gaode"，本机回执成"AMAP"，等于替后端补了话'
reg 'B-2' "$RR" \
  '        if (nowMillis >= entry.expiresAtMillis) {' \
  '        if (nowMillis > entry.expiresAtMillis) {' \
  '过期条目读不到几何并且当场被删掉' "$CT2" \
  '相等这一格从过期翻成新鲜：指令原话"当前时间 < expiresAt"被松了一格，边界用例失去意义'
reg 'B-3' "$RR" \
  '            return RouteCacheRead.Stale(entry.providerRaw, entry.fetchedAtMillis, age)' \
  '            return RouteCacheRead.Hit(entry, age)' \
  '过期条目只留下记录不留几何并且读到即删' "$CT1" \
  '过期那一格照样把几何交出去：结构上"Stale 没有 route 字段"这道闸被绕过，旧的线开始替现在说话'
reg 'B-4' "$RR" \
  '        if (check is RouteCheck.Accepted && serverAvailable) {' \
  '        if (check is RouteCheck.Accepted) {' \
  '后端自己说没有真实道路时既不写入也删掉旧条目' "$CT2" \
  '后端那一位的否决被丢掉：校验器认了就写，"这一班没有真实道路"也被缓存成好像有'
reg 'B-5' "$RR" \
  '                fetchedAtMillis = nowMillis,' \
  '                fetchedAtMillis = System.currentTimeMillis(),' \
  '抓取时间来自写入那一次的时钟入参而不是读的那一刻' "$CT1" \
  '抓取时间改成现场取时：注入的时钟不再进得出结果，30 分钟那条边界从今往后永远测不到'
reg 'B-6' "$RR" \
  '            val oldest = accepted.entries.minByOrNull { it.value.sequence } ?: return' \
  '            val oldest = accepted.entries.maxByOrNull { it.value.sequence } ?: return' \
  '缓存条数受控且淘汰最旧的一条' "$CT2" \
  '越界时淘汰最新的那一条：刚抓到的路线被自己的第 13 条挤掉，司机刷新反而丢缓存'
reg 'B-7' "$OFF" \
  '    RouteCacheRead.Absent -> cause' \
  '    RouteCacheRead.Absent -> "$cause（本机稍后会补上一条缓存几何）"' \
  '没有缓存时失败原因原样返回' "$CT1" \
  '空着的那格也要补一句像样的话：本机没存过任何东西，却被读成"马上就有一条"'
reg 'B-8' "$OFF" \
  '        "$cause（本机有过一条缓存几何，是 ${offlineCacheAgeText(read.ageMillis)}前抓取的，" +' \
  '        "$cause（本机有过一条「${read.providerRaw}」给的缓存几何，是 ${offlineCacheAgeText(read.ageMillis)}前抓取的，" +' \
  '过期条目只留下记录不留几何并且读到即删' "$CT1" \
  '过期那一格把后端当时的来源原文念出来：几何都不能替现在说话了，来源跟着说就成了"路线还在"的暗示'
reg 'B-9' "$OFF" \
  '    val minutes = safe / 60_000' \
  '    val minutes = 0L' \
  '时长口径只分三档且负数不炸' "$CT1" \
  '三档被抹平成永远"不足 1 分钟"：抓取时间披露退化成一个不带信息的客套话'
reg 'B-10' "$RS" \
  '    DEMO(false, "演示数据"),' \
  '    DEMO(true, "演示数据"),' \
  '演示面来源从未进过缓存' "$CT1" \
  '演示面被写进可信名单：这一条不是"少写一行"，而是把没有的路网念成有（红线：宁可没有路线，不允许假路线）'

# 覆盖面自指读数：本书声称的"10 条配对"必须被自己数出来。注册表少一条（写漏、被编辑掉、
# 或有人往这里加了第 11 条而没改说法）都是预检与逐条注入之外**唯一**能在起跑前发现的一环——
# 不写这一格，空注册表会一路"绿"到底（预检 0/0 通过、注入 0 条、恢复后重新绿、RESULT=PASS）。
[ "${#IDS[@]}" = 10 ] || { echo "✘ 注册条数 = ${#IDS[@]}（本书头部声明的是 10 条配对）⇒ 覆盖面与说法不符，不起跑"; exit 5; }

run_cls() { # $1 = 日志名
  ./gradlew $JVMARGS :app:testDebugUnitTest --tests "$CLS1" --tests "$CLS2" > "$1" 2>&1
  echo "$?"
}
# 只读**本轮新写的**那两份 XML：上一版批次踩过 build cache 的假绿（判据 §八 ㉓），
# 陈旧 XML 会被算成"红在 []"，那一格必须显式喊出来而不是当成"没红"。
red_names() { # $1 = 起跑秒
  ORC_T0="$1" PYTHONIOENCODING=utf-8 python - <<'PY'
import glob, os, time
t0 = float(os.environ.get("ORC_T0", "0"))
out, stale = [], 0
pat = ["app/build/test-results/testDebugUnitTest/TEST-*RouteOfflineCacheContract*.xml",
       "app/build/test-results/testDebugUnitTest/TEST-*RealRoadRouteValidator*.xml"]
for p in pat:
    for fn in sorted(glob.glob(p)):
        if os.path.getmtime(fn) < t0:
            stale += 1
            continue
        import xml.etree.ElementTree as ET
        r = ET.parse(fn).getroot()
        bad = [(tc.get("name") or "")[:60] for tc in r.iter("testcase")
               if tc.find("failure") is not None or tc.find("error") is not None]
        out.append("%s tests=%s failures=%s errors=%s 红在: %s" % (
            os.path.basename(fn)[5:60], r.get("tests"), r.get("failures"), r.get("errors"), bad))
print("   " + ("\n   ".join(out) if out else "（本轮没有新的缓存用例 XML）"))
if stale:
    print("   ⚠ 有 %d 份 XML 比本次起跑更早（陈旧，未采信）" % stale)
PY
}
probe_rc() { bash "$PROBE" > "$LOGDIR/probe.log" 2>&1; echo $?; }

ok=0; bad=0; amb=0; scope=0; fail=0

# 预检一：每条锚点在目标文件里**恰好命中 1 次**，每条具名用例在对应测试文件里**恰好命中 1 次**。
# 命中 0 次 = 源码文本已经漂了（这一条的负控从来没打过）；命中 ≥2 次 = 替换会挑错那一处。
pre_bad=0
for i in "${!IDS[@]}"; do
  n=${IDS[$i]}; f=${FILES[$i]}; old=${OLDS[$i]}; named=${NAMEDS[$i]}; tf=${NFILES[$i]}
  cnt=$(ORC_F="$f" ORC_A="$old" PYTHONIOENCODING=utf-8 python - <<'PY'
import os
s = open(os.environ["ORC_F"], encoding='utf-8', newline='').read()
print(s.count(os.environ["ORC_A"]))
PY
)
  tcnt=$(ORC_T="$tf" ORC_N="$named" PYTHONIOENCODING=utf-8 python - <<'PY'
import os
s = open(os.environ["ORC_T"], encoding='utf-8', newline='').read()
print(s.count("fun `" + os.environ["ORC_N"] + "`"))
PY
)
  if [ "$cnt" != 1 ] || [ "$tcnt" != 1 ]; then
    echo "✘ 预检 $n 锚点命中=$cnt（要求 1）/ 具名用例命中=$tcnt（要求 1）  [$(basename "$f") | $(basename "$tf")]"
    pre_bad=$((pre_bad + 1))
  fi
done
echo "预检 ${#IDS[@]} 条：锚点与具名用例各恰好命中 1 次的条数 = $(( ${#IDS[@]} - pre_bad )) / ${#IDS[@]}"
if [ "$pre_bad" != 0 ]; then
  echo "✘ 预检未过 $pre_bad 条 ⇒ 未注入任何破口，本书不起跑（不烧 gradle，也不留半本账）"
  exit 5
fi

# $1 = 注册序号
b_step() {
  i="$1"
  id="${IDS[$i]}"; f="${FILES[$i]}"; old="${OLDS[$i]}"; new="${NEWS[$i]}"
  named="${NAMEDS[$i]}"; desc="${DESCS[$i]}"
  echo "== $id 破口：$desc =="
  b=$(md5sum < "$f")
  if ! python - "$f" "$old" "$new" <<'PY'
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
n = s.count(old)
assert n == 1, "ANCHOR-NOT-UNIQUE: count=%d anchor=%r" % (n, old[:70])
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
PY
  then
    echo "✘ $id 锚点没命中（或不止一处），破口根本没注入"; bad=$((bad + 1)); fail=1; return
  fi
  a=$(md5sum < "$f")
  if [ "$b" = "$a" ]; then
    echo "✘ $id 注入后文件字节未变（空跑），拒绝记成判据失效"; bad=$((bad + 1)); fail=1; return
  fi
  t0=$(date +%s)
  c=$(run_cls "$LOGDIR/$id.log"); echo "[$id 破口后] gradle exit=$c"
  if grep -q '^e: ' "$LOGDIR/$id.log"; then
    echo "✘ $id 注入后的代码不可编译（gradle 日志有 e: 行）——红是编译红，不是用例红"; bad=$((bad + 1)); fail=1
  else
    R=$(red_names "$t0"); echo "$R"
    if [ "$c" = 0 ]; then
      echo "✘ $id  源码已经改坏而用例全绿——$named 那条是装饰"; bad=$((bad + 1)); fail=1
    elif echo "$R" | grep -qF -- "$named"; then
      echo "✔ $id  → gradle exit=$c，红的正是 $named"; ok=$((ok + 1))
    else
      echo "✘ $id  gradle 是红的（exit=$c），但红的不是那一条——不能算这条破口被咬住"; bad=$((bad + 1)); fail=1
    fi
  fi
  P=$(probe_rc)
  if [ "$P" = 0 ]; then
    echo "△ $id  结构判据 rc=0：注入只动了行为、NAB-*/N-x 一个都没看见（这一格是结构半边的覆盖面，不是本书的红）"
    amb=$((amb + 1))
  else
    echo "◦ $id  同轮结构判据 rc=$P（预期非 0：注入改的就是 NAB-* 钉住的文本；红着的判据不反过来证明它有判别力）"
    scope=$((scope + 1))
  fi
  restore
  cmp -s "$BAKRR" "$RR" && cmp -s "$BAKOFF" "$OFF" && cmp -s "$BAKRS" "$RS" || {
    echo "✘ [$id 复原] 与备份不一致，必须手工核对，后面的判读全部作废"; exit 4; }
  echo "[$id 复原] cmp 一致"
}

echo "== 基线 =="
B=$(run_cls "$LOGDIR/baseline.log")
echo "  gradle exit=$B（必须 0，基线红着的 ✔ 一律不采信）"
[ "$B" = 0 ] || { echo "✘ 基线未通过，本轮负控不采信（看 $LOGDIR/baseline.log）"; exit 2; }
P0=$(probe_rc); echo "  同轮结构判据 rc=$P0（必须 0）"
[ "$P0" = 0 ] || { echo "✘ 结构基线不绿，本书不采信"; exit 2; }

for i in "${!IDS[@]}"; do b_step "$i"; done

echo "== 原样恢复 =="
restore
cmp -s "$BAKRR" "$RR" || { echo "✘ [恢复] RouteRepository 与备份不一致，不再往下判"; exit 4; }
cmp -s "$BAKOFF" "$OFF" || { echo "✘ [恢复] OfflineRouteDisclosure 与备份不一致，不再往下判"; exit 4; }
cmp -s "$BAKRS" "$RS" || { echo "✘ [恢复] RouteSource 与备份不一致，不再往下判"; exit 4; }
echo "[恢复] 三个源文件 cmp 一致"
A=$(run_cls "$LOGDIR/recover.log")
echo "[恢复后] gradle exit=$A（必须 0）"
[ "$A" = 0 ] || { echo "✘ 恢复后用例仍红，本轮未收口（看 $LOGDIR/recover.log）"; exit 2; }
P=$(probe_rc)
echo "[恢复后] $PROBE rc=$P 末行:$(tail -1 "$LOGDIR/probe.log")"
[ "$P" = 0 ] || { echo "✘ 恢复后结构判据不通过（rc=$P），本轮负控不采信"; exit 6; }
echo "行为负控（⑭ 离线缓存 ${#IDS[@]} 条破口 / 具名用例配对）= $ok ✔ / $bad ✘ / $amb △ / $scope ◦"
echo "  口径：✔ = 该破口被具名那一条用例咬住；✘ = 用例全绿或红的不是那一条；"
echo "        △ = 注入后同轮结构判据仍 rc=0（覆盖面注记，不计判据绿）；"
echo "        ◦ = 注入把同轮结构判据打红（按 §八 ㉛ ④，红着的判据不反过来证明它有判别力，也不计判据绿）。"
echo "  未覆盖（如实登记，不当成已通过）：并发容器与两参 remove 的行为半边、serverAvailable 的读取时机 —— 见本文件头部「覆盖面缺口」。"
[ "$fail" = 0 ] || { echo "RESULT=FAIL"; exit 1; }
echo "RESULT=PASS"
