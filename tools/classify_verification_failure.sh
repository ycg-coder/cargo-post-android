#!/usr/bin/env bash
# §三十三 / F-2 第 7 步：把一份构建或验证的输出**归入四类失败之一**，归不进去就明说"归不进去"。
# 它存在的唯一理由：台账硬规则不许再写笼统的一句"构建失败"（CONTEXT_LOCK §八 ㉒、D-037 同一条）。
#
# 用法：
#   bash tools/classify_verification_failure.sh <日志文件> [更多日志文件...]
#   cat build.log | bash tools/classify_verification_failure.sh -
#
# 退出码（多文件时取最坏的一次）：
#   0 = 恰好命中一类
#   3 = 命中两个以上类别 = **歧义**：绝不按规则表的先后顺序替人选一个
#   4 = 一条没命中 = **UNCLASSIFIED**：绝不猜一个"最像的"
#   2 = 用法错 / 规则表或日志读不到（这是环境缺陷，不在要分的那四类里）
#
# 判据全在唯一规则表 tools/verification_failure_rules.txt 里，本脚本**不含任何类别专属关键词**
# （VF-d 数的就是这件事），否则规则有了第二份真相、改表不生效。
# 同一张表也被 app/src/test/java/com/shanxiang/buspost/verification/VerificationFailure.kt 读。
#
# 写法上唯一一处非直觉得 pay off：全程**一次 grep**、其余在 bash 内做。
# 逐条字面量各起一个 grep 的版本，一份 148 KB 的样例要起 32 个进程，判据簿跑一轮 18 秒、
# 负控簿一轮十分钟以上（本机实测）—— 判据簿贵到没人愿意全批复跑，就等于没有判据。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
RULES="$ROOT/tools/verification_failure_rules.txt"
[ -r "$RULES" ] || { echo "READ_FAIL|规则表 $RULES 读不到（这是环境缺陷；本脚本不写类别名，判据只来自那张表）"; exit 2; }

# ── 规则表读一次，装进数组 ──
M_ARR=() L_ARR=() P_ARR=() N=0 SEEN="" Kinds=0
while IFS=$'\t' read -r m l p rest; do
  case "$m" in ''|'#'*) continue ;; esac
  [ -n "$rest" ] && { echo "RULES_BAD|第 $((N + 1)) 行不止三列：$m"; exit 2; }
  N=$((N + 1)); M_ARR[$N]="$m"; L_ARR[$N]="$l"; P_ARR[$N]="$p"
  case "
$SEEN
" in *"
$m
"*) : ;; *) SEEN="$SEEN$m"$'\n'; Kinds=$((Kinds + 1)) ;; esac
  # 元字符要**加引号**才按字面匹配：写成 *?* 的话 ? 本身就是通配，任何字面量都会被当成违例（本轮踩过）。
  case "$l" in *'['* | *'?'* | *'*'* | *'|'* | *'\'* | *'{'* | *'}'*) echo "RULES_BAD|字面量含 glob 元字符，case 会走样：$l"; exit 2 ;; esac
done < "$RULES"
# 非 ASCII 检查用一条 grep 做完（case 里写 [! -~] 这种范围在本机 bash 直接语法报错）。
# LC_ALL=C 让方括号范围按**字节**解释，才会真的把 UTF-8/GBK 的多字节序列挑出来。
if LC_ALL=C grep -q -a '[^ -~]' <(awk -F'\t' '!/^#/ && NF==3 {print $2}' "$RULES"); then
  echo "RULES_BAD|有字面量含非 ASCII 字符：混合编码日志上会安静读成 0，而读成 0 与真的没有长得一样"; exit 2
fi
[ "$N" -gt 0 ] || { echo "RULES_BAD|规则表一行都读不出来"; exit 2; }
[ "$Kinds" = 4 ] || { echo "RULES_BAD|类别数不是 4（实测 $Kinds）"; exit 2; }

classify_one() { # $1=日志路径或 -
  local log="$1" tmp="" lits matched rec lineno text i
  if [ "$log" = "-" ]; then tmp=$(mktemp); cat > "$tmp"; log="$tmp"; fi
  [ -r "$log" ] || { echo "READ_FAIL|$log"; [ -n "$tmp" ] && rm -f "$tmp"; return 2; }

  lits=$(mktemp)
  for ((i = 1; i <= N; i++)); do printf '%s\n' "${L_ARR[$i]}"; done > "$lits"
  # 混合编码（GBK 字节 + EF BF BD 替换符）会被 grep 当二进制、只回一句 "Binary file … matches" ⇒ 一律 -a
  matched=$(grep -a -n -F -f "$lits" "$log" 2>/dev/null)
  rm -f "$lits"

  local HITS=() FIRST=()
  for ((i = 1; i <= N; i++)); do HITS[$i]=0; FIRST[$i]=""; done
  while IFS= read -r rec; do
    [ -n "$rec" ] || continue
    lineno=${rec%%:*}; text=${rec#*:}
    for ((i = 1; i <= N; i++)); do
      case "$text" in *"${L_ARR[$i]}"*)
        HITS[$i]=$((HITS[$i] + 1))
        [ -z "${FIRST[$i]}" ] && FIRST[$i]="$lineno"
        ;;
      esac
    done
  done <<< "$matched"

  local count=0 single="" list="" note=""
  SEEN=""
  for ((i = 1; i <= N; i++)); do
    [ "${HITS[$i]}" -gt 0 ] || continue
    printf 'RULE|%s|%s|hits=%s|first_line=%s|出处=%s\n' "${M_ARR[$i]}" "${L_ARR[$i]}" "${HITS[$i]}" "${FIRST[$i]}" "${P_ARR[$i]}"
    case "$note" in "") case "${P_ARR[$i]}" in 未实测*) note="${L_ARR[$i]}" ;; esac ;; esac
    case "
$SEEN
" in *"
${M_ARR[$i]}
"*) : ;;
      *) SEEN="$SEEN${M_ARR[$i]}"$'\n'; count=$((count + 1)); single="${M_ARR[$i]}"; list="$list${M_ARR[$i]} " ;;
    esac
  done

  if [ "$count" = 0 ]; then
    echo "VERDICT|UNCLASSIFIED|四类字面量一条都没命中：不许猜，去读原文，再决定是否给规则表加一行"
    [ -n "$tmp" ] && rm -f "$tmp"; return 4
  fi
  if [ "$count" -gt 1 ]; then
    printf 'VERDICT|AMBIGUOUS|同一份日志命中 %s 个类别（%s），分类器不替人选\n' "$count" "$list"
    [ -n "$tmp" ] && rm -f "$tmp"; return 3
  fi
  printf 'VERDICT|%s|按上面 RULE 行的 first_line 去读原文\n' "$single"
  [ -n "$note" ] && printf 'NOTE|首次命中|%s 这条字面量在本工程从没产出过（出处列标 未实测），结论得再核一遍原文\n' "$note"
  [ -n "$tmp" ] && rm -f "$tmp"
  return 0
}

FILES=()
for a in "$@"; do FILES+=("$a"); done
if [ "${#FILES[@]}" = 0 ]; then
  if [ -t 0 ]; then echo "用法：bash tools/classify_verification_failure.sh <日志文件>|-   （- 表示读 stdin）"; exit 2; fi
  FILES=(-)
fi

worst=0
for f in "${FILES[@]}"; do
  echo "=== $f ==="
  classify_one "$f"; rc=$?
  [ "$rc" -gt "$worst" ] && worst=$rc
done
exit "$worst"
