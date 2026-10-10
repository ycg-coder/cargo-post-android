#!/usr/bin/env bash
# 一次性把整批负控（`tools/*_negative_controls.sh`，本数由 glob 数出、结束行打印，不写死）**按顺序**跑完，
# 逐本记 rc 与 ✔/✘/△/◦ 计数
# （⑲ ③：改了源码文本就要核所有簿的锚点，不能只核本轮新写的那本）。
# ⚠ 2026-10-01 补的机制：本脚本自己先取 negctl 锁，并 export NEGCTL_LOCK_HELD=1 让批内的簿沿用同一把。
#   之前"一次只跑一本"只写在注释里、没有任何东西执行它：那一晚 23:30 起跑的批次被我另起的一批（00:03:22）
#   并行踩住了，role_reconcile_test 被记成 rc=2 假红，shell_routing_test 的 M-4/M-5 注入被同行还原掉
#   （AssertionError）之后还继续跑 gradle，撞上 build cache 的绿色结果，被打印成一条**假发现**
#   「✘ 用例仍然全绿，这条判据证明不了任何东西」。细节见 DECISION_LOG D-039 与判据 §八 ㉓。
# 用法：bash tools/run_all_negative_controls_batch.sh <日志文件>
set -u
OUT="${1:-/tmp/allneg.txt}"
cd "$(dirname "$0")/.." || exit 1
. tools/negctl_lock.sh
negctl_lock "run_all_negative_controls_batch.sh" || exit 3
export NEGCTL_LOCK_HELD=1
: > "$OUT"
total=0
rc_bad=""; cross_bad=""
for b in tools/*_negative_controls.sh; do
  total=$((total + 1))
  log="/tmp/$(basename "$b" .sh).run.log"
  t0=$(date +%s)
  echo "=== $(basename "$b") 开始 $(date +%H:%M:%S)" | tee -a "$OUT"
  if bash "$b" > "$log" 2>&1; then rc=0; else rc=$?; fi
  ok=$(grep -c '^✔' "$log"); bad=$(grep -c '^✘' "$log"); amb=$(grep -c '^△' "$log"); scope=$(grep -c '^◦' "$log")
  echo "$(basename "$b") rc=$rc ✔=$ok ✘=$bad △=$amb ◦=$scope 用时$(( $(date +%s) - t0 ))s 末行:$(tail -1 "$log" | cut -c1-60)" | tee -a "$OUT"
  [ "$rc" = 0 ] || rc_bad="$rc_bad $(basename "$b")(rc=$rc)"
  [ "$bad" = 0 ] || cross_bad="$cross_bad $(basename "$b")(✘=$bad)"
done
# 聚合退出码（2026-10-01 ⑰⑱ 实测补的机制）：这一行不存在时，两本 rc=2（test-results 不在场）
# 加 eta 一本 ✘=2 被整批 exit 0 洗成"全绿"，而 ✘ 的意思恰恰是"这条判据等于没判据"。
if [ -n "$rc_bad" ] || [ -n "$cross_bad" ]; then
  echo "[聚合] rc≠0 的本：$rc_bad" | tee -a "$OUT"
  echo "[聚合] 自带 ✘ 的本：$cross_bad" | tee -a "$OUT"
  echo "=== 批次结束 $(date +%H:%M:%S)，共 $total 本" | tee -a "$OUT"
  exit 1
fi
echo "=== 批次结束 $(date +%H:%M:%S)，共 $total 本" | tee -a "$OUT"
