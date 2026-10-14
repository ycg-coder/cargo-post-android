#!/usr/bin/env bash
# 真实性簿 AF 格（虚构内容面，目标 0）的负控制。配 tools/check_authenticity_counts.sh
# 用法：bash tools/authenticity_negative_controls.sh
# 为什么这本必须有：AF 是本轮新加的"目标 0"——0 命中必须是"真的没有"，
# 不能是 pattern 写错了、中文串被 shell 吃了、或者扫描根指错了目录。三种失效都长得和"干净"一模一样。
# 两条注入各自只碰一头（符号面 / 具名面），证明两头都在岗：只钉一头的话，
# 换函数名或换媒体名重新长回来就看不见。
# ⚠ 会就地临时改源码并在结束时原样恢复；一次只允许跑一个（negctl_lock）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2

REPO=app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt
PROBE=tools/check_authenticity_counts.sh
FILES=("$REPO")
BAKS=()
for f in "${FILES[@]}"; do
  [ -f "$f" ] || { echo "✘ 缺 $f，本书作废"; exit 2; }
  b="/tmp/af_neg.$(basename "$f").$$"; cp "$f" "$b"; BAKS+=("$b")
done
restore() { for i in "${!FILES[@]}"; do cp "${BAKS[$i]}" "${FILES[$i]}"; done; }
trap 'restore; for b in "${BAKS[@]}"; do rm -f "$b"; done; negctl_release' EXIT

bash "$PROBE" >/dev/null 2>&1 || { echo "✘ 预检：$PROBE 起跑就不绿，一条破口都不注入"; exit 5; }
BASE=$(bash "$PROBE" 2>&1 | head -1)
ok=0; bad=0

inject() {
  python3 - "$1" "$2" "$3" <<'INJPY'
import sys
p, a, b = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(a) != 1:
    sys.exit(9)
open(p, 'w', encoding='utf-8', newline='').write(s.replace(a, b, 1))
INJPY
}

check() {
  local id="$1" want="$2"; shift 2
  restore
  if ! inject "$@"; then
    echo "✘ ${id} 锚点没命中（不是恰好 1 次），本次未注入 ⇒ 判据未被验证"; bad=$((bad + 1)); return
  fi
  local out rc
  out=$(bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = 0 ]; then echo "✘ ${id} 判据仍然绿（破口没咬住）"; bad=$((bad + 1))
  elif grep -qF "$want" <<<"$out"; then echo "✔ ${id} 红且原因对得上（$want）"; ok=$((ok + 1))
  else echo "✘ ${id} 红了但不是那一条（缺 $want）"; bad=$((bad + 1)); fi
}

ANCHOR='    suspend fun notices(): Feed<Notice> = try {'

# 1 只把符号面加回来（没有任何媒体名）→ AF 必须红
check AF-1 "× AF 虚构内容面不为 0" "$REPO" "$ANCHOR" \
  '    // 恢复一条走 Repo.news 的旧通路
    suspend fun notices(): Feed<Notice> = try {'
# 2 只把具名面加回来（没有任何符号名）→ AF 仍必须红：换名字重新长回来也要看得见
check AF-2 "× AF 虚构内容面不为 0" "$REPO" "$ANCHOR" \
  '    // 来源署名：新华社
    suspend fun notices(): Feed<Notice> = try {'
# 3 反向对照：注入一条**两个都不含**的普通注释 → AF 必须仍然绿。
#   这一条是防"看见任何中文注释就红"的误报面：AF 数的是虚构内容面，不是"Repo.kt 里出现了注释"。
restore
if ! inject "$REPO" "$ANCHOR" '    // 只数虚构面，普通注释不该进这一格
    suspend fun notices(): Feed<Notice> = try {'; then
  echo "✘ AF-3 锚点没命中（不是恰好 1 次），本次未注入 ⇒ 反证未被验证"; bad=$((bad + 1))
else
  out=$(bash "$PROBE" 2>&1); rc=$?
  line3=$(bash "$PROBE" 2>&1 | head -1)
  if [ "$rc" = 0 ]; then echo "✔ AF-3 普通注释不被误报（$line3）"; ok=$((ok + 1))
  else echo "✘ AF-3 误报：一条不含虚构符号也不含媒体名的注释把 AF 判红了"; bad=$((bad + 1)); fi
fi
restore

bash "$PROBE" >/dev/null 2>&1; PRC=$?
NOW=$(bash "$PROBE" 2>&1 | head -1)
if [ "$PRC" = 0 ] && [ "$NOW" = "$BASE" ]; then
  echo "✔ AF-4 还原后与基线逐字相同（$NOW）"; ok=$((ok + 1))
else
  echo "✘ AF-4 还原后变了：rc=$PRC 基线[$BASE] 现在[$NOW]"; bad=$((bad + 1))
fi

echo "RESULT=$([ "$bad" = 0 ] && echo PASS || echo FAIL)  ✔=$ok ✘=$bad"
[ "$bad" = 0 ]
