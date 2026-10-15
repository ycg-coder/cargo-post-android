#!/usr/bin/env bash
# 四条口径判据（KL-a…KL-e）的负控制：逐条破口 → 确认 check_caliber_probes.sh exit 1 且报的就是这一条。
# 只在临时目录副本上动手，工程真身不受影响（纯文本判据，不需要编译 ⇒ 可与其他脚本并行）。
# 用法：bash tools/caliber_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3

PKG=com/shanxiang/buspost
PR="$SRC/tools/check_caliber_probes.sh"
SC=/tmp/kl_neg.$$
mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
A="$SC/app/src/main/java/$PKG"
GS="$A/ui/user/GoodsScreen.kt"
DM="$A/ui/driver/DriverMessagesScreen.kt"
NS="$A/ui/user/NotificationScreen.kt"
BS="$A/ui/user/BusScreen.kt"
T="$SC/app/src/test/java/$PKG/ui/user/GoodsScopeContractTest.kt"
for f in "$GS" "$DM" "$NS" "$BS" "$T"; do
    [ -f "$f" ] || { echo "✘ 副本缺 $f，下面每一条都会假绿"; exit 2; }
done
CLEAN=/tmp/kl_neg_clean.$$; cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

ctl() { # $1=判据编号 $2=说明
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" = 1 ] && echo "$out" | grep -q "^× $1"; then
    echo "✔ $1  $2 → exit 1：$(echo "$out" | grep "^× $1" | head -1)"
  elif [ "$code" = 1 ]; then
    echo "△ $1  $2 → exit 1，但抓到的是别的判据：$(echo "$out" | grep '^×' | head -1)"
  else
    echo "✘ $1  $2 → 负控制失效（exit $code），这条判据等于没判据"
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
swap() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swap 锚点没命中，破口根本没注入（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
PY
}
swapall() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swapall 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
open(p, 'w', encoding='utf-8').write(s.replace(old, new))
PY
}
kill_test() { python - "$1" <<'PY' || { echo "✘ kill_test 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test')
assert i >= 0, "ANCHOR-MISS: @Test"
open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | tail -1)"
[ "$base_code" = 0 ] || { echo "✘ 基线未通过，负控结果一律不采信"; exit 2; }

# 1 列表源退回"只拿已加载那几页" → KL-a（源=0 且旧直排=1 双红）
swap "$GS" 'val pool = if (needsCatalog) catalog ?: allProducts else allProducts' 'val pool = allProducts'
ctl KL-a "排序/筛选又只在已加载的几页里排，界面却念成全店结果"

# 2 取全量的触发点摘掉 → KL-a
swap "$GS" 'if (vm.needsCatalog) vm.ensureCatalog()' 'if (false) vm.ensureCatalog()'
ctl KL-a "needsCatalog 成了摆设：判定在、没人按它去取全量"

# 3 病句回到生效代码 → KL-b（标题句=0 且『没货』非注释=1）
swap "$GS" '"在架清单里没有这一类",' '"这类山货暂时没货",'
ctl KL-b "空态又替后端的库存字段宣布『卖完了』"

# 4 反证自身：把注释里那两处『没货』一起抹掉 → KL-b 的反证必须红，否则第 3 条的 0 数的是不存在的东西
swapall "$GS" '没货' '售罄'
ctl KL-b "『没货』整句被删（连解释为什么改的注释一起删）：上面那个 0 不再可信"

# 5 司机消息页头退回本页自算 → KL-c
swap "$DM" '"$unread 条未读",' '"${vm.shown.count { !it.read }} 条未读",'
ctl KL-c "页头的未读数改成拿已加载的消息自己数：翻页就跳，与 tab 角标两套账"

# 6 用户侧分段丢了『本页』限定词 → KL-c2
swap "$NS" '"本页未读 ${vm.items.count { !it.read }}"' '"未读 ${vm.items.count { !it.read }}"'
ctl KL-c2 "同屏两个未读数又长得一模一样（裸『未读 N』回到生效代码）"

# 7 搜索框又承诺全网 → KL-d
swap "$BS" '"在附近线路里搜名称"' '"搜索线路名称"'
ctl KL-d "搜索框不再说明只覆盖半径内那几条线路"

# 8 搜不到又静默 → KL-d
swap "$BS" 'title = "附近线路里没搜到「${vm.lineQuery}」",' 'title = "没搜到",'
ctl KL-d "未命中那句被换成不含范围的话：整块列表静默消失，谁也没说为什么"

# 9 口径用例被摘掉一条 → KL-e
kill_test "$T"
ctl KL-e "口径用例少于 5 条：这三句话的分工没人钉"

echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
