#!/usr/bin/env bash
# Q-* 判据（F-2 §三 QuoteResult 缺键默认值）的负控制总表：逐条破口 → 确认判据簿 exit 1 且报的是这一条。
# 只在 /tmp 副本上动手，工程真身不受影响。用法：bash tools/contract_defaults_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/cdef_neg.$$
PR="$SRC/tools/check_contract_defaults_probes.sh"
A="$SC/app/src/main/java/com/shanxiang/buspost"
MD="$A/data/model/Models.kt"
DM="$A/data/repo/DemoData.kt"
SS="$A/ui/user/SendScreen.kt"
TSD="$SC/app/src/test/java/com/shanxiang/buspost/data"
RP="$A/data/repo/Repo.kt"
BS="$A/ui/user/BusScreen.kt"
HS="$A/ui/user/HomeScreen.kt"
AK="$A/core/net/Api.kt"
TQ="$TSD/QuoteNullSafetyTest.kt"
NB="$TSD/NearbyBooleanContractTest.kt"

rm -rf "$SC"
mkdir -p "$A/data/model" "$A/data/repo" "$A/ui/user" "$A/core/net" "$TSD"
cp "$SRC/app/src/main/java/com/shanxiang/buspost/data/model/Models.kt" "$MD"
cp "$SRC/app/src/main/java/com/shanxiang/buspost/data/repo/DemoData.kt" "$DM"
cp "$SRC/app/src/main/java/com/shanxiang/buspost/ui/user/SendScreen.kt" "$SS"
# §四 的 B-* 判据扫这四个 main 文件，漏 copy 就没有基线
cp "$SRC/app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt" "$RP"
cp "$SRC/app/src/main/java/com/shanxiang/buspost/ui/user/BusScreen.kt" "$BS"
cp "$SRC/app/src/main/java/com/shanxiang/buspost/ui/user/HomeScreen.kt" "$HS"
cp "$SRC/app/src/main/java/com/shanxiang/buspost/core/net/Api.kt" "$AK"
# Q-f/B-g 与两条 GUARD 扫这两个测试文件，漏 copy 会让基线直接红（脚本自带 exit 2 拦截）
cp "$SRC/app/src/test/java/com/shanxiang/buspost/data/QuoteNullSafetyTest.kt" "$TQ"
cp "$SRC/app/src/test/java/com/shanxiang/buspost/data/NearbyBooleanContractTest.kt" "$NB"
CLEAN=/tmp/cdef_neg_clean.$$; cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

ctl() { # $1=编号 $2=说明
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
KEEPFAIL=0
keep() { # 口径（反向）控制：$1=编号前缀 $2=说明 —— 破的是**另一条契约**，本簿必须仍然 exit 0
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" = 0 ]; then
    echo "◦ $1-scope  $2 → 仍 exit 0（扫描面确实只盖本契约）"
  else
    echo "✘ $1-scope  $2 → 不该响的判据响了（这条判据统计的还是别处的东西）：$(echo "$out" | grep '^×' | head -1)"
    KEEPFAIL=$((KEEPFAIL + 1))
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
inject() { python - "$1" "$2" "$3" <<'PY'
import sys
p, anchor, add = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read(); i = s.find(anchor)
if i < 0: sys.exit("ANCHOR-MISS: " + anchor)
j = s.index('\n', i) + 1
open(p, 'w', encoding='utf-8').write(s[:j] + add + '\n' + s[j:])
PY
}
# 只替换 data class 构造体内的那一处，避免全文件 sed 把别的类的同名字段一起改掉（判据会串味）
sbysed() { python - "$1" "$2" "$3" "$4" <<'PY'
import sys, re
p, cls, old, new = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
s = open(p, encoding='utf-8').read()
m = re.search(r'^data class %s\b' % cls, s, re.M)
if not m: sys.exit("CLASS-MISS: " + cls)
end = s.index('\n) {', m.start())
body = s[m.start():end]
if old not in body: sys.exit("OLD-MISS: " + old + " @" + cls)
open(p, 'w', encoding='utf-8').write(s[:m.start()] + body.replace(old, new, 1) + s[end:])
PY
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | tail -1)"
# 基线不绿就立刻收工：否则每条破口都会因为"判据本来就红"而打出 ✔，整轮负控是假的
[ "$base_code" = 0 ] || { echo "✘ 基线未通过（exit $base_code），负控结果一律不采信"; echo "$BASE" | grep '^×' | head -5; exit 2; }

sbysed "$MD" QuoteResult "val amount: Double? = null" "val amount: Double = 0.0";                                 ctl Q-a "金额退回非空默认值（缺键会说成 ¥0）"
sed -i 's/amount = o.multiNumOrNull("amount"),/amount = o.multiNumOrNull("amount", "fee"),/' "$MD";               ctl Q-b "解析层把无生产者的 fee 写回候选名"
sed -i 's/"amount" to 6.0 + items \* 2.5/"amount" to 6.0 + items * 2.5, "fee" to 6.0/' "$DM";                     ctl Q-b2 "演示面开始下发 fee 给别名续命（demo 也是 main 源码，Q-b 同轮一起响）"
sed -i 's/fun parse(o: JSONObject): QuoteResult = QuoteResult(/fun parse(o: JSONObject, fallbackCount: Int): QuoteResult = QuoteResult(/' "$MD"; ctl Q-c1 "客户端件数兜底参数复活（覆盖后端 normalize 过的值）"
sed -i 's/itemCount = o.multiNumOrNull("itemCount")?.toInt(),/itemCount = o.multiNumOrNull("itemCount")?.toInt()?.coerceAtLeast(1),/' "$MD"; ctl Q-c2 "用 coerceAtLeast 把缺失抬成猜测值"
sed -i 's/amount = o.multiNumOrNull("amount"),/amount = o.multiNum("amount"),/' "$MD";                            ctl Q-c3 "金额读法退回 multiNum（F-2 §三 的原始破口）"
inject "$SS" "PriceText(q.amountValueText" '                            val bad = "预估 ${q.amount} 元"';          ctl Q-d "页面把可空金额裸拼进文案（null 直接印给用户）"
inject "$SS" "PriceText(q.amountValueText" '                            val legacy = "¥" + formatPrice(q.amount)'; ctl Q-d2 "页面把金额交给非空 formatPrice（能编译就说明上游已塌 0）"
delbody() { # $1=文件 $2=data class 名 $3=要删掉的那一行的固定片段 —— 只在**这个类的类体内**删
  python - "$1" "$2" "$3" <<'PY'
import sys, re
p, cls, needle = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
m = re.search(r'^data class %s\b' % cls, s, re.M)
if not m: sys.exit("CLASS-MISS: " + cls)
end = s.index('\n}', m.start())          # 类体右界：getter 在 ) { 之后，锚 ^) { 会截不到
body = s[m.start():end]
hits = [ln for ln in body.split('\n') if needle in ln]
if len(hits) != 1: sys.exit("NEEDLE-AMBIGUOUS(%d): %s @%s" % (len(hits), needle, cls))
new = '\n'.join(ln for ln in body.split('\n') if needle not in ln)
open(p, 'w', encoding='utf-8').write(s[:m.start()] + new + s[end:])
PY
}
# QuoteResult 与 EarningItem 的 getter **同名同行**（都叫 amountText、字面量一模一样），
# 早先"全文 replace 第一处"删到的其实是 EarningItem 那条（它在文件里更靠前），Q-e1 纹丝不动 ⇒ 这条负控一直是假的
delbody "$MD" QuoteResult 'val amountText: String get() = amount?.let';                          ctl Q-e1 "删掉模型的『金额未给』文案 getter（缺失态只剩 grep 判据）"
delbody "$MD" EarningItem 'val amountText: String get() = amount?.let';                           keep Q-e1 "另一条契约（Earnings）的同名 getter 被删 ⇒ Q-e1 必须**不**响：证扫描面真的收进了 QuoteResult 类体"
sed -i 's/q\.amountValueText/"11"/g; s/q\.feeBreakdownText/"件数费 ¥5"/g; s/vm\.quote!!\.amountText/"¥11"/g; s/vm\.quote?\.amountText/"¥11"/g' "$SS"; ctl Q-e2 "页面改回硬编码字面量，不再走未给 getter（¥0 复发而测试仍绿）"
python - "$TQ" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
                                                                                                                   ctl Q-f "QuoteNullSafetyTest 被注释掉一条（缺键塌 0 复发无人报警）"
sed -i 's/^data class QuoteResult(/data class QuoteRenamed(/' "$MD";                                             ctl GUARD-QR "类名锚点被改 ⇒ sed 截不到 QuoteResult 类体，Q-e1 的 3 不得采信"
sed -i 's/"fee"/"feeRenamed"/g; s/"totalPrice"/"totalPriceRenamed"/g; s/"unitFee"/"unitFeeRenamed"/g; s/"mileageFee"/"mileageFeeRenamed"/g; s/"count"/"countRenamed"/g' "$TQ"; ctl GUARD "正控制失配：别名模式在测试源码也数不到 ⇒ grep 计数管道失效，本簿所有『预期 0』不得采信"
echo "--- F-2 第 4 步 NearbyResult 缺失 boolean 默认 true（B-*）的负控制 ---"
sbysed "$MD" NearbyResult "val inService: Boolean? = null" "val inService: Boolean = true";                        ctl B-a "inService 退回非空默认 true（缺失会被说成正在运营）"
sed -i 's/inService = o.multiBoolOrNull("inService"),/inService = o.optBoolean("inService", true),/' "$RP";        ctl B-b "解析点改回 optBoolean(…, true)：§四 点名的原始破口"
inject "$AK" "    fun nested(key: String): JSONObject?" '    fun bool(key: String, def: Boolean = false): Boolean = obj?.optBoolean(key, def) ?: def'; ctl B-d "无人调用的 bool(def) 助手复活（下一个缺失默认值的入口）"
python - "$MD" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
old = '    val isOffService: Boolean get() = inService == false'
if old not in s: sys.exit("OLD-MISS: isOffService")
open(p, 'w', encoding='utf-8').write(s.replace(old, '', 1))
PY
                                                                                                                   ctl B-e1 "删掉模型的三态 getter（off 判定退回页面裸读，JVM 测不到）"
sed -i 's/r\.isOffService/!r.inService/' "$BS" "$HS";                                                              ctl B-e2 "页面不再统一走 isOffService（连带 B-e3 一起响：可空值被取反）"
# 锚点必须写 Attested：#300 把页面读的那一格改名成 isOffServiceAttested，旧字面串注入不进去 ⇒ 曾经恒绿（#315）
inject "$HS" '        if (r.isOffServiceAttested && r.vehicles.isEmpty()) return "off"' '        if (!r.inService) return "off"'; ctl B-e3 "页面直接对可空 inService 取反（缺失=未知先被当成『是』再取反）"
sed -i 's/realTransit = o.multiBool("realTransitAvailable"),/realTransit = o.multiBool("realTransitAvailable", "realTransit"),/' "$RP"; ctl B-f "自造别名键 realTransit 写回候选名（三面都无生产者）"
python - "$NB" <<'PY'
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test'); open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
                                                                                                                   ctl B-g "NearbyBooleanContractTest 被注释掉一条（缺失当『是』复发无人报警）"
# 必须换成**不含原键名子串**的名字：`s/inService/inServiceRenamed/` 这种改法 grep -oE 'inService' 照样命中，负控是假的
sed -i 's/inService/SvcFlag/g; s/located/PosOk/g; s/InService/SvcFlag/g; s/Located/PosOk/g' "$NB";                  ctl GUARD-B "正控制失配：键名在测试源码也数不到 ⇒ grep 计数管道失效，B-b/B-f 的 0 不得采信"
echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" | tail -1)"
echo "[口径控制] 反向控制失效条数 = $KEEPFAIL（预期 0）"
[ "$KEEPFAIL" = 0 ] || exit 3
