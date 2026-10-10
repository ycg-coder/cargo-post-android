#!/usr/bin/env bash
# §三十三 / F-2 第 7 步 四类失败分型判据（VF-a…VF-g）。
# 用法：bash tools/check_verification_failure_probes.sh     退出码非 0 = 分型纪律破了
# 每条都有负控制：bash tools/verification_failure_negative_controls.sh —— 不会变红的判据等于没写。
#
# 本本只判**结构与真实样例**，不重跑 gradle（构建期失败无法在测试期复现）：
# 唯一规则表 tools/verification_failure_rules.txt / 分类器 tools/classify_verification_failure.sh /
# 六份本工作区当场产出的样例日志 tools/fixtures/verification-failure/ / 消费面 tools/verify_device.sh。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

RULES="tools/verification_failure_rules.txt"
CLS="tools/classify_verification_failure.sh"
FXDIR="tools/fixtures/verification-failure"
VD="tools/verify_device.sh"
MAIN="app/src/main"
KT="app/src/test/java/com/shanxiang/buspost/verification/VerificationFailure.kt"
TST="app/src/test/java/com/shanxiang/buspost/verification/VerificationFailureTest.kt"
NEGFX="negative-control-tests-failed-not-a-build-failure.log"
MARK='SOURCE_FAILURE|DEPENDENCY_FAILURE|ENVIRONMENT_FAILURE|DEVICE_UNAVAILABLE'   # 只准配 grep -E 用：BRE 下 | 是字面量，两个 0 都会是假干净

fail=0
# GUARD：计数管道若失去计数能力，"0 命中"与"真的没有"长得一模一样 ⇒ 先自证 grep 还数得动
G=$(printf 'aa bb\n' | grep -c 'bb')
[ "$G" = 1 ] || { echo "× GUARD 计数管道不工作（实测 $G），本本所有 0 一律不采信"; exit 3; }
echo "GUARD 计数管道            = $G （目标 1）"

# ---------- VF-a 规则表结构 ----------
VFA_M=$(awk -F'\t' '!/^#/ && NF==3 {print $1}' "$RULES" | sort -u | wc -l | tr -d ' ')
VFA_R=$(awk -F'\t' '!/^#/ && NF==3' "$RULES" | wc -l | tr -d ' ')
VFA_BAD=$(awk -F'\t' '!/^#/ && NF!=3' "$RULES" | wc -l | tr -d ' ')
VFA_NOPROV=$(awk -F'\t' '!/^#/ && NF==3 && $3==""' "$RULES" | wc -l | tr -d ' ')
# 出处列**自己也会烂**：本轮 `Unrecognized VM option` 写的行号是 15，分类器实测首次命中在 12。
# 只判"出处列非空"看不见这种漂移，所以逐行回到样例里核：那一份在不在、那一行里到底有没有这条字面量。
# 非 ASCII 不比较（这个工作区的中文按字节匹配不可靠）：只在 ASCII 的 `:` 上切，只认 `.log` 这个 ASCII 尾巴。
VFA_NOFILE=0 VFA_CITOK=0 VFA_CITBAD=0
while IFS=$'\t' read -r cm cl cp cx; do
  case "$cm" in ''|'#'*) continue ;; esac
  [ -z "$cx" ] || continue
  case "$cp" in *.log*) ;; *) continue ;; esac
  ref=${cp#*:}
  file=${ref%%:*}
  [ -f "$FXDIR/$file" ] || { VFA_NOFILE=$((VFA_NOFILE + 1)); echo "  ! 出处指向的样例不存在：$file"; continue; }
  case "$ref" in *:*) ;; *) continue ;; esac        # 只登文件名、没写行号：只核文件在不在
  line=${ref##*:}
  if LC_ALL=C awk -v n="$line" -v lit="$cl" 'NR==n { if (index($0, lit) > 0) print "OK"; exit }' "$FXDIR/$file" | grep -q OK; then
    VFA_CITOK=$((VFA_CITOK + 1))
  else
    VFA_CITBAD=$((VFA_CITBAD + 1)); echo "  ! 出处行号指错：$cm 的字面量「$cl」写的是 $file:$line，那一行里没有它"
  fi
done < "$RULES"
# ---------- VF-b 分类器对六份真实样例的判决 ----------
VB_OK=0 VB_BAD=0 VB_TOTAL=0 VB_MARKS=""
for f in "$FXDIR"/*.log; do
  case "$(basename "$f")" in "$NEGFX") continue ;; esac
  VB_TOTAL=$((VB_TOTAL + 1))
  out=$(bash "$CLS" "$f" 2>/dev/null); rc=$?
  got=$(printf '%s\n' "$out" | grep -m1 '^VERDICT|' | cut -d'|' -f2)
  VB_MARKS="$VB_MARKS$got"$'\n'
  want=$(basename "$f" | sed -e 's/^source-failure.*/SOURCE_FAILURE/' -e 's/^dependency-failure.*/DEPENDENCY_FAILURE/' \
                             -e 's/^environment-failure.*/ENVIRONMENT_FAILURE/' -e 's/^device-unavailable.*/DEVICE_UNAVAILABLE/')
  if [ "$rc" = 0 ] && [ "$got" = "$want" ]; then VB_OK=$((VB_OK + 1))
  else VB_BAD=$((VB_BAD + 1)); echo "  ! 样例判错：$(basename "$f") rc=$rc 判成 $got，文件名声明 $want"; fi
done
VB_NEG=$(bash "$CLS" "$FXDIR/$NEGFX" 2>/dev/null | grep -c '^VERDICT|UNCLASSIFIED')
VB_NEGRC=0
bash "$CLS" "$FXDIR/$NEGFX" >/dev/null 2>&1 || VB_NEGRC=$?
# 四类是否各有至少一份真实样例撑着（删掉某一类的证据就红）—— 复用上面那一次分类结果，不再跑第二遍
VB_COV=$(printf '%s' "$VB_MARKS" | sort -u | grep -v '^UNCLASSIFIED$' | grep -v '^$' | wc -l | tr -d ' ')
# ---------- VF-c 红线：分型不进业务面 ----------
VC_MAIN=$(grep -rnE "$MARK" "$MAIN" --include=*.kt | wc -l | tr -d ' ')
VC_TYPE=$(grep -rn 'VerificationFailure' "$MAIN" | wc -l | tr -d ' ')
# 0 命中的反证：同一套模式在 test 源集必须数得出东西（否则是 grep 坏了，不是主面干净）
VC_PROOF=$(grep -rnE "$MARK" app/src/test --include=*.kt | wc -l | tr -d ' ')
# ---------- VF-d 业务错误通路没长出第四套 ----------
VD_APIEX=$(grep -rn 'class ApiException' "$MAIN" --include=*.kt | wc -l | tr -d ' ')
VD_NETEX=$(grep -rn 'class NetworkException' "$MAIN" --include=*.kt | wc -l | tr -d ' ')
VD_ACK=$(grep -rn 'data class Ack' "$MAIN" --include=*.kt | wc -l | tr -d ' ')
# 分类器里不写类别名 ⇒ 判据只可能来自那张表
VD_CLS=$(grep -cE "$MARK" "$CLS")
# ---------- VF-e 消费面：真有一句带类别的结论 ----------
VE_REF=$(grep -c 'classify_verification_failure' "$VD")
VE_VAGUE=$(grep -c '构建失败，先修编译错误' "$VD")
VE_TAILPIPE=$(grep -c 'assembleDebug ) 2>&1 | tail -3' "$VD")
# ---------- VF-f 类型与用例 ----------
# 两条一起才有意义：总数 4 防"凭空多一类"，逐名点齐防"改掉一个子类型名"。
# ⚠ 这里的四个名字**不是**第二份真相：VF-d 禁的是把**分类关键词**（类别的字面量）写死在脚本里，
#   那会让改表不生效；而 `SourceFailure` 这类是 Kotlin 的**声明身份**，规则表里没有、也无从推出。
#   改名是一次跨文件重构（CASES、用例、本判据都得一起改），本判据的作用就是让这次重构无法静默发生。
VF_CASES=$(grep -cE 'object [A-Za-z]+ : VerificationFailure' "$KT")
VF_NAMES=$(grep -cE 'object (SourceFailure|DependencyFailure|EnvironmentFailure|DeviceUnavailable) : VerificationFailure' "$KT")
VF_TESTS=$(grep -cE '^[[:space:]]*@Test' "$TST")
# ---------- VF-g 规则表不是孤儿文件 ----------
VG_CONS=$(grep -rl 'verification_failure_rules' tools app/src docs 2>/dev/null | wc -l | tr -d ' ')
# 光数消费者文件数兜不住"分类器悄悄改读另一张表"（总数只从 4 掉到 3，仍在下限之上），
# 所以单独钉死分类器那一行**赋值**（不是注释里的提及）：表名换了这里就归 0。
VG_CLSREF=$(grep -cF 'RULES="$ROOT/tools/verification_failure_rules.txt"' "$CLS")

echo "VF-a 规则表类别数          = $VFA_M （目标 4）"
echo "VF-a 规则表行数            = $VFA_R （下限 14；本轮实测 16）"
echo "VF-a 字段数不对的行        = $VFA_BAD （目标 0）/ 出处为空的行 = $VFA_NOPROV （目标 0）"
echo "VF-a 出处行号回核           = 指对 $VFA_CITOK / 指错 $VFA_CITBAD / 样例缺失 $VFA_NOFILE （目标 错与缺都为 0）"
echo "VF-b 真实样例判对           = $VB_OK/$VB_TOTAL，判错 $VB_BAD （目标 全对）"
echo "VF-b 用例失败样例拒绝分类   = $VB_NEG （目标 1，rc=$VB_NEGRC 目标 4）"
echo "VF-b 四类被样例覆盖         = $VB_COV （目标 4）"
echo "VF-c main 里四类符号        = $VC_MAIN （目标 0）/ VerificationFailure = $VC_TYPE （目标 0）"
echo "VF-c 同一模式在 test 源集    = $VC_PROOF （反证：必须 > 0，否则上面的 0 是 grep 坏了）"
echo "VF-d 业务三套错误通路        = $VD_APIEX/$VD_NETEX/$VD_ACK （目标 1/1/1，不许长出第四套）"
echo "VF-d 分类器内写死的类别名    = $VD_CLS （目标 0：判据只来自规则表）"
echo "VF-e verify_device 引用分类器 = $VE_REF （目标 ≥1）"
echo "VF-e 笼统『构建失败，先修编译错误』= $VE_VAGUE （目标 0）/ 旧 tail -3 管道形态 = $VE_TAILPIPE （目标 0）"
echo "VF-f 四类 object / 逐名点齐 / 用例条数 = $VF_CASES / $VF_NAMES / $VF_TESTS （目标 4 / 4 / 8）"
echo "VF-g 规则表的消费者文件数     = $VG_CONS （目标 ≥3）"
echo "VF-g 分类器读的就是这张表     = $VG_CLSREF （目标 1：赋值行，注释里的提及不算）"

[ "$VFA_M" = 4 ] || { echo "× VF-a 类别数不是 4：少一类就是 §三十三 没落全，多一类就是有人自造第五种分型"; fail=1; }
[ "$VFA_R" -ge 14 ] || { echo "× VF-a 规则表行数掉到 $VFA_R（下限 14）：某一类的兜底字面量被删了"; fail=1; }
[ "$VFA_BAD" = 0 ] || { echo "× VF-a 有 $VFA_BAD 行不是三列：分类器按 TAB 读，少列的行会被静默丢掉"; fail=1; }
[ "$VFA_NOPROV" = 0 ] || { echo "× VF-a 有 $VFA_NOPROV 行没写出处：没出处的字面量等于「猜来的文案」"; fail=1; }
[ "$VFA_NOFILE" = 0 ] || { echo "× VF-a 有 $VFA_NOFILE 行的出处指向不存在的样例：样例被删而表没跟着删，这一行就没人再核过"; fail=1; }
[ "$VFA_CITBAD" = 0 ] || { echo "× VF-a 有 $VFA_CITBAD 行的出处**行号指错**：行号错了这条证据就废了，读的人按行号看不到那句原文"; fail=1; }
[ "$VB_BAD" = 0 ] && [ "$VB_OK" -ge 4 ] || { echo "× VF-b 有真实样例判错或判据太少（对 $VB_OK / 错 $VB_BAD）"; fail=1; }
[ "$VB_NEG" = 1 ] || { echo "× VF-b 『用例失败』那份样例被分进类了（$VB_NEG）：分类器在过度命中，笼统结论会换个形式回来"; fail=1; }
[ "$VB_NEGRC" = 4 ] || { echo "× VF-b 那份样例的退出码是 $VB_NEGRC 而不是 4（UNCLASSIFIED）：归不进去时必须拒绝给类别"; fail=1; }
[ "$VB_COV" = 4 ] || { echo "× VF-b 样例只覆盖 $VB_COV 类：某一类从此没有真实证据撑着"; fail=1; }
[ "$VC_MAIN" = 0 ] || { echo "× VF-c 四类符号进了 app/src/main（$VC_MAIN 处）：工程验证分型不许变成第四套业务错误"; fail=1; }
[ "$VC_TYPE" = 0 ] || { echo "× VF-c VerificationFailure 进了 app/src/main：构建失败发生时 App 进程不存在，主面拿到它是零消费者的死类型"; fail=1; }
[ "$VC_PROOF" -gt 0 ] || { echo "× VF-c 反证失败：同一模式在 test 源集也数不出东西，说明上面那两个 0 不可信"; fail=1; }
[ "$VD_APIEX" = 1 ] && [ "$VD_NETEX" = 1 ] && [ "$VD_ACK" = 1 ] || { echo "× VF-d 业务错误通路不再是三套（$VD_APIEX/$VD_NETEX/$VD_ACK）"; fail=1; }
[ "$VD_CLS" = 0 ] || { echo "× VF-d 分类器里写死了类别名（$VD_CLS 处）：规则就有了第二份真相，改表不生效"; fail=1; }
[ "$VE_REF" -ge 1 ] || { echo "× VF-e verify_device.sh 不再引用分类器：四类退回台账里的手写口径"; fail=1; }
[ "$VE_VAGUE" = 0 ] || { echo "× VF-e 笼统的『构建失败，先修编译错误』回来了（§三十三 明令禁止）"; fail=1; }
[ "$VE_TAILPIPE" = 0 ] || { echo "× VF-e 构建输出又用 tail -3 收尾：pipeline 的退出码是 tail 的 0，|| die 永不触发，构建失败会被无声吞掉"; fail=1; }
[ "$VF_CASES" = 4 ] || { echo "× VF-f VerificationFailure 的子类型不是 4 个（$VF_CASES）：与规则表对不上，用例「规则表里的类别恰好是指令点名的四类」也会红"; fail=1; }
[ "$VF_NAMES" = 4 ] || { echo "× VF-f 四个子类型名没点齐（$VF_NAMES/4）：改名会让 CASES、用例、本判据三处脱节，静默改名等于类型面少一类而计数看不出来"; fail=1; }
[ "$VF_TESTS" = 8 ] || { echo "× VF-f 分型用例不是 8 条（$VF_TESTS）：删用例必须让判据变红"; fail=1; }
[ "$VG_CONS" -ge 3 ] || { echo "× VF-g 规则表只剩 $VG_CONS 个消费者：分类器与用例都不读它了，等于表变成孤儿"; fail=1; }
[ "$VG_CLSREF" = 1 ] || { echo "× VF-g 分类器读规则表的那行赋值不见了（$VG_CLSREF）：改读别的表时判据会跟着一起改版，规则就没人对账了"; fail=1; }

if [ "$fail" = 0 ]; then echo "四类失败分型判据通过"; else echo "四类失败分型判据失败"; fi
exit "$fail"
