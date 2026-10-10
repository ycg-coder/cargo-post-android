#!/usr/bin/env bash
# tools/check_verification_failure_probes.sh（VF-a…VF-g）的**负控制**（F-2 第 7 步）。
# 逐条破口 → 确认判据 exit 1 且**报的就是这一条**（按 VF-x 点名，不是"报了别的"就算过）。
# 只在临时目录副本上动手，工程真身不受影响；每条注入后整份副本从干净基线重拷。
# 用法：bash tools/verification_failure_negative_controls.sh
#
# 第 11 条是本轮**自己踩到的坑**常驻化：MARK 里带 `|`，grep 默认 BRE 会把 `|` 当字面量，
# 于是"main 里 0 命中"是假干净 —— VF-c 的反证行（同一模式在 test 源集必须数得出东西）当场抓住它。
# 这一条攻击的是**判据簿自己**，其余各条攻击的是被测对象。
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/vf_neg.$$
CLEAN=/tmp/vf_neg_clean.$$
PKG=com/shanxiang/buspost
PR="$SRC/tools/check_verification_failure_probes.sh"
# 只拷判据读得到的东西：app/build 有 501 MB，整份 app 拷进沙箱会把这一本拖死（而且拷来的产物没有一条判据读它）
mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
cp -r "$SRC/tools" "$SC/tools"
cp -r "$SRC/docs" "$SC/docs"
for need in "$SC/tools/verification_failure_rules.txt" "$SC/tools/classify_verification_failure.sh" \
            "$SC/tools/verify_device.sh" "$SC/tools/fixtures/verification-failure/source-failure-kotlinc.log" \
            "$SC/app/src/main/java/$PKG/data/repo/Repo.kt" \
            "$SC/app/src/test/java/$PKG/verification/VerificationFailureTest.kt" \
            "$SC/app/src/test/java/$PKG/verification/VerificationFailure.kt"; do
  [ -e "$need" ] || { echo "✘ 副本缺 $need（缺一样就会有一条负控假绿）"; exit 2; }
done
trap 'rm -rf "$SC" "$CLEAN" /tmp/vf_device_present.log; negctl_release' EXIT
cp -r "$SC" "$CLEAN"

RULES="$SC/tools/verification_failure_rules.txt"
CLS="$SC/tools/classify_verification_failure.sh"
VD="$SC/tools/verify_device.sh"
TST="$SC/app/src/test/java/$PKG/verification/VerificationFailureTest.kt"
KTT="$SC/app/src/test/java/$PKG/verification/VerificationFailure.kt"

restore() { rm -rf "$SC"; cp -r "$CLEAN" "$SC"; }

FAILED=0
ctl() { # $1=编号 $2=说明 $3=应当点名的判据（VF-x）
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" = 1 ] && echo "$out" | grep -q "^× $3"; then
    echo "✔ $1  $2 → exit 1：$(echo "$out" | grep "^× $3" | head -1)"
  elif [ "$code" = 1 ]; then
    echo "△ $1  $2 → exit 1，但报的是别的判据：$(echo "$out" | grep '^× ' | head -1)"
  else
    echo "✘ $1  $2 → 没变红（rc=$code），这条判据是永不为红的摆设"
    FAILED=1
  fi
  restore
}

# 基线：不动任何东西时必须全绿，否则后面每一条红都无法归因
if BUSPOST_ANDROID="$CLEAN" bash "$PR" >/dev/null 2>&1; then
  echo "✔ BASE  干净副本判据全绿"
else
  echo "✘ BASE  干净副本就红了，下面每一条红都无法归因"
  FAILED=1
fi

# 1 SOURCE 类**两条**字面量一起删（只删一条时另一条仍命中同一份样例、判据不动 —— 那是兜底行的作用，
#   所以这里以 ◦ 登记"单删一条不可见"这件事，真正的破口用两条齐删来打）
python - "$RULES" <<'PY'
import io,sys
p=sys.argv[1]; L=io.open(p,encoding='utf-8').read().split('\n')
out=[l for l in L if not ('\te: file:\t' in l or '\tUnresolved reference\t' in l)]
io.open(p,'w',encoding='utf-8',newline='').write('\n'.join(out))
PY
ctl 1 "SOURCE 类两条字面量齐删 → 编译失败样例无人认领" VF-b
# ◦ 1s 实测登记：单删一条兜底字面量时判据**不**变红（另一条仍命中同一份样例）。
# 不判红，但必须可见 —— 否则"每类至少两条"这条规则看起来像在防漏检，实际防的是"删到最后一条都没人知道"。
python - "$RULES" <<'PY'
import io,sys
p=sys.argv[1]; L=io.open(p,encoding='utf-8').read().split('\n')
io.open(p,'w',encoding='utf-8',newline='').write('\n'.join([l for l in L if '\te: file:\t' not in l]))
PY
if BUSPOST_ANDROID="$SC" bash "$PR" >/dev/null 2>&1; then
  echo "◦ 1s  单删 e: file: 后判据仍 rc=0（SOURCE 类还剩 Unresolved reference 命中同一份样例）：兜底行的设计代价，不判红"
else
  echo "◦ 1s  单删 e: file: 后判据**变红了**：说明 Unresolved reference 那条其实没在命中同一份样例，两类字面量并非同面"
fi
restore

# 2 少一列（删掉一个 TAB）⇒ 那一行变成两列，分类器按 TAB 读会读歪
python - "$RULES" <<'PY'
import io,sys
p=sys.argv[1]; s=io.open(p,encoding='utf-8').read()
s=s.replace('DEVICE_UNAVAILABLE\tno devices/emulators found\t','DEVICE_UNAVAILABLE\tno devices/emulators found')
io.open(p,'w',encoding='utf-8',newline='').write(s)
PY
ctl 2 "规则表某行少一个 TAB（三列变两列）" VF-a

# 3 出处列清空：按列结构改，不匹配中文（非 ASCII 在这个工作区的字节口径不可靠）
python - "$RULES" <<'PY'
import io,sys
p=sys.argv[1]
rows=io.open(p,encoding='utf-8').read().split('\n')
for i,l in enumerate(rows):
    if l.startswith('#') or '\t' not in l: continue
    f=l.split('\t')
    if len(f)==3 and f[1].startswith('Could not d'): f[2]=''
    rows[i]='\t'.join(f)
io.open(p,'w',encoding='utf-8',newline='').write('\n'.join(rows))
PY
ctl 3 "两行 DEPENDENCY 的出处列被抹空" VF-a

# 4 同一条字面量挂到第二个类别 ⇒ 依赖那份样例变 AMBIGUOUS
python - "$RULES" <<'PY'
import io,sys
p=sys.argv[1]; s=io.open(p,encoding='utf-8').read().rstrip('\n')+'\n'
s=s+'ENVIRONMENT_FAILURE\tCould not find\tX-probe\n'
io.open(p,'w',encoding='utf-8',newline='').write(s)
PY
ctl 4 "把 Could not find 同时挂进 ENVIRONMENT_FAILURE（跨类别重复字面量）" VF-b

# 5 红线：四类符号进 app/src/main
cat > "$SC/app/src/main/java/$PKG/ScratchVfRedLine.kt" <<'EOF'
package com.shanxiang.buspost
sealed interface VerificationFailure {
    object SourceFailure : VerificationFailure
    object DependencyFailure : VerificationFailure
    object EnvironmentFailure : VerificationFailure
    object DeviceUnavailable : VerificationFailure
}
EOF
ctl 5 "把 VerificationFailure 四类塞进 app/src/main（工程验证分型长出第四套业务真相）" VF-c

# 6 分类器里写死类别名 ⇒ 判据不再只来自那张表
python - "$CLS" <<'PY'
import io,sys
p=sys.argv[1]; s=io.open(p,encoding='utf-8').read()
assert 'worst=0' in s
s=s.replace('worst=0','if [ -n "${1:-}" ] && echo "$1" | grep -q kotlin; then echo "VERDICT|SOURCE_FAILURE|hardcoded"; fi\nworst=0',1)
io.open(p,'w',encoding='utf-8',newline='').write(s)
PY
ctl 6 "分类器脚本里硬写一个类别名（规则出现第二份真相）" VF-d

# 7 消费面退回旧形态：笼统那句 + tail -3 管道吞退出码
python - "$VD" <<'PY'
import io,sys
p=sys.argv[1]; s=io.open(p,encoding='utf-8').read()
old=s
s=s.replace('''  ( cd "$ROOT" && ./gradlew :app:assembleDebug ) >"$OUT/build.log" 2>&1
  if [ $? -ne 0 ]; then''','''  ( cd "$ROOT" && ./gradlew :app:assembleDebug ) 2>&1 | tail -3 || die "VAGUE-BUILD-FAIL"
  if [ -n "${DO_BUILD:-}" ]; then''')
assert s != old, "没匹配到构建步原文，注入无效"
io.open(p,'w',encoding='utf-8',newline='').write(s)
PY
ctl 7 "verify_device.sh 的构建步退回 tail -3 管道（|| die 永不触发）" VF-e
grep -c 'tail -3' "$SC/tools/verify_device.sh" >/dev/null && echo "◦ 7s  同一份注入里旧管道形态也回来了，VF-e 的两条一起红"

# 8 删一条用例
python - "$TST" <<'PY'
import io,sys
p=sys.argv[1]; L=io.open(p,encoding='utf-8').read().split('\n')
for i,l in enumerate(L):
    if l.strip()=='@Test':
        del L[i]; break
io.open(p,'w',encoding='utf-8',newline='').write('\n'.join(L))
PY
ctl 8 "分型用例删掉一条 @Test" VF-f

# 9 四类里改掉一个子类型名
# ⚠ 第一次实跑这一条是 **✘（rc=0 没变红）**：当时 VF-f 只数 `object … : VerificationFailure` 的**个数**，
#   改名后个数照旧是 4 ⇒ 这是一条永不为红的摆设。现在 VF-f 加了"逐名点齐"，本条才第一次有了归因对象。
#   改名同时会让 `CASES` 里的引用编译不过，所以 gradle 是第二张网 —— 但判据不许指望 gradle 来替自己看见。
python - "$KTT" <<'PY'
import io,sys
p=sys.argv[1]; s=io.open(p,encoding='utf-8').read()
assert 'object DeviceUnavailable : VerificationFailure' in s
s=s.replace('object DeviceUnavailable : VerificationFailure','object DeviceAbsent : VerificationFailure')
io.open(p,'w',encoding='utf-8',newline='').write(s)
PY
ctl 9 "VerificationFailure 的四个子类型少了一个（改名）" VF-f

# 10 规则表变成孤儿：分类器改读另一张表
python - "$CLS" <<'PY'
import io,sys
p=sys.argv[1]; s=io.open(p,encoding='utf-8').read()
assert 'RULES="$ROOT/tools/verification_failure_rules.txt"' in s
s=s.replace('RULES="$ROOT/tools/verification_failure_rules.txt"','RULES="$ROOT/tools/some_other_table.txt"')
io.open(p,'w',encoding='utf-8',newline='').write(s)
PY
ctl 10 "分类器改读另一张表（原规则表的消费者掉到下限以下）" VF-g

# 11 攻击判据簿自己：MARK 退回 BRE（| 变字面量 ⇒ main 的 0 是假干净）。
#    ⚠ 这条改的是**沙箱里的判据簿副本**，不是真身 —— 别的条攻击被测对象，这一条攻击判据本身。
PRSC="$SC/tools/check_verification_failure_probes.sh"
python - "$PRSC" <<'PY'
import io,sys
p=sys.argv[1]; s=io.open(p,encoding='utf-8').read()
assert 'grep -rnE "$MARK"' in s
s=s.replace('grep -rnE "$MARK"','grep -rn "$MARK"')
io.open(p,'w',encoding='utf-8',newline='').write(s)
PY
out=$(BUSPOST_ANDROID="$SC" bash "$PRSC" 2>&1); code=$?
if [ "$code" = 1 ] && echo "$out" | grep -q '^× VF-c'; then
  echo "✔ 11  判据簿的 MARK 用 BRE 跑（| 被当字面量，main 的 0 变成假干净） → exit 1：$(echo "$out" | grep '^× VF-c' | head -1)"
else
  echo "✘ 11  判据簿退回 BRE 后没变红（rc=$code）：反证行没兜住，这条是本轮真踩过的坑"
  FAILED=1
fi
restore

# 12 设计反证：证明"规则表不收 adb 表头行"是必要的取舍，而不是随手写的一句注释。
#    这份"设备在场"文本是**手写**的（本机没有设备，产不出真的），只用于本条，不进样例目录、不进判据。
printf 'List of devices attached\nR28T12345AB\tdevice\n' > /tmp/vf_device_present.log
python - "$RULES" <<'PY'
import io,sys
p=sys.argv[1]; s=io.open(p,encoding='utf-8').read().rstrip('\n')+'\n'
s=s+'DEVICE_UNAVAILABLE\tList of devices attached\tX-probe\n'
io.open(p,'w',encoding='utf-8',newline='').write(s)
PY
mis=$(bash "$CLS" /tmp/vf_device_present.log 2>/dev/null | grep -m1 '^VERDICT|' | cut -d'|' -f2)
if [ "$mis" = "DEVICE_UNAVAILABLE" ]; then
  echo "✔ 12  设计反证：把 adb 表头行收进规则表，一台**在场**的设备也会被判成 DEVICE_UNAVAILABLE（所以规则表只收真实报错行，不收表头）"
else
  echo "✘ 12  设计反证没打出来（判成 ${mis:-空}）：这条设计取舍现在没有证据支撑"
  FAILED=1
fi
restore
# 13 出处的**行号**指错（本轮真找到的一条：`Unrecognized VM option` 台账写 :15，分类器实测首次命中在 :12）。
#    只判"出处列非空"完全看不见这种漂移，而按错行号去读原文的人只会得出"表在瞎写"。
python - "$RULES" <<'PY'
import io,sys
p=sys.argv[1]; s=io.open(p,encoding='utf-8').read()
assert 'environment-failure-daemon-bad-jvmarg.log:12' in s
s=s.replace('environment-failure-daemon-bad-jvmarg.log:12','environment-failure-daemon-bad-jvmarg.log:15')
io.open(p,'w',encoding='utf-8',newline='').write(s)
PY
ctl 13 "出处行号指错（那一行里没有这条字面量）" VF-a
# 恢复后必须回到全绿，且恢复是逐字节比对过的
BUSPOST_ANDROID="$CLEAN" bash "$PR" >/dev/null 2>&1 && echo "✔ REC  干净基线仍全绿（注入都只在副本，真身一字未动）" || { echo "✘ REC  干净基线现在也红，判据本身出了问题"; FAILED=1; }
for pair in "tools/verification_failure_rules.txt" "tools/classify_verification_failure.sh" "tools/verify_device.sh" \
            "app/src/test/java/$PKG/verification/VerificationFailureTest.kt" "app/src/test/java/$PKG/verification/VerificationFailure.kt"; do
  cmp -s "$CLEAN/$pair" "$SRC/$pair" && echo "✔ CMP  $pair 与真身一字不差" || { echo "✘ CMP  $pair 与真身不一致"; FAILED=1; }
done
[ -f "$SRC/app/src/main/java/$PKG/ScratchVfRedLine.kt" ] && { echo "✘ 真身 main 源集被写了 ScratchVfRedLine.kt"; FAILED=1; } || echo "✔ DIR  真身 main 源集没多出文件"

if [ "$FAILED" = 0 ]; then echo "四类失败分型负控制全部生效"; else echo "有负控制没打红对应判据"; fi
exit "$FAILED"
