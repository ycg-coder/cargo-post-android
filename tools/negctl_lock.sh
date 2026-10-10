#!/usr/bin/env bash
# 负控簿的互斥锁（F-2 第 7 步收工时踩出来的，2026-10-01 / D-039）。
#
# 为什么需要：*_negative_controls.sh 会**就地改 app/src 再原样恢复**，两本并行时彼此的
# red()/inject() 与 restore 会互相覆盖。实测代价（不是推理）：
#   · 一批 16 本从 23:30 跑到 00:21:23，另一批从 00:03:22 并行起跑；
#   · role_reconcile_test 被踩成 rc=2「恢复后基线不绿」；
#   · shell_routing_test 的 M-4/M-5 注入直接抛 AssertionError（锚点被同行还原掉了），
#     而 case_run 不看注入的退出码，继续拿**未修改**的源码跑 gradle，撞上 FROM-CACHE 的绿色结果，
#     于是把"我没打破口"打印成了「✘ 用例仍然全绿，这条判据证明不了任何东西」——
#     一条**假发现**，指控的还是一本其实生效的判据。
# 各簿注释里原来那句"一次只允许跑一个改源码的负控脚本"**没有任何机制执行**，这个文件补上机制。
#
# 用法（簿里两行，放在 cd "$ROOT" 之后、备份之前）：
#   . "$ROOT/tools/negctl_lock.sh"
#   negctl_lock "$(basename "$0")" || exit 3
# ⚠ 簿自己已有 `trap '…恢复备份…' EXIT`；本文件的 negctl_lock 只在**它自己拿到锁**时登记 trap，
#   簿里若在其后再登记自己的 trap，就把锁的清理顶掉了 —— 所以簿要把 release 合进自己的 trap：
#   trap 'restore; negctl_release' EXIT

NEGCTL_LOCK_DIR="${NEGCTL_LOCK_DIR:-/tmp/negctl.lock}"

# cygwin 的 $$ 与 Windows 的 PID 不同号；要能按"这进程还在不在"判掉陈旧锁，就得记 WINPID
negctl_winpid() {
  ps -l -p "$$" //W | awk 'NR==2 {print $4}'
}

# 严格判定：只认 tasklist 按 PID 过滤后那一行的 PID 字段（不用 grep -c，否则会撞上内存列里的同数字串）
negctl_alive() {
  local wp="$1"
  [ -n "$wp" ] || return 1
  tasklist //FI "PID eq $wp" //NH 2>/dev/null | awk '{print $2}' | grep -qx "$wp"
}

negctl_take() { # $1=脚本名
  mkdir "$NEGCTL_LOCK_DIR" 2>/dev/null || return 1
  negctl_winpid > "$NEGCTL_LOCK_DIR/pid"
  printf '%s  %s\n' "$1" "$(date '+%F %T')" > "$NEGCTL_LOCK_DIR/owner"
  return 0
}

negctl_release() {
  [ -f "$NEGCTL_LOCK_DIR/pid" ] || return 0
  [ "$(cat "$NEGCTL_LOCK_DIR/pid" 2>/dev/null)" = "$(negctl_winpid)" ] && rm -rf "$NEGCTL_LOCK_DIR"
  return 0
}

negctl_lock() {
  local who="${1:-负控簿}" owner owner_pid
  # 批次已经持锁时（run_all_negative_controls_batch.sh 会 export NEGCTL_LOCK_HELD=1），
  # 簿直接继承这把锁而不重复取 —— 否则批内第一本就会把后面 15 本全 [STOP] 掉。
  if [ "${NEGCTL_LOCK_HELD:-0}" = 1 ]; then
    echo "[锁] 由批次持有（NEGCTL_LOCK_HELD=1），本簿沿用不另取"
    return 0
  fi
  if negctl_take "$who"; then
    trap 'negctl_release' EXIT
    return 0
  fi
  owner=$(cat "$NEGCTL_LOCK_DIR/owner" 2>/dev/null || echo '(锁目录里没有 owner 文件)')
  owner_pid=$(cat "$NEGCTL_LOCK_DIR/pid" 2>/dev/null || echo '?')
  if ! negctl_alive "$owner_pid"; then
    echo "[接管陈旧锁] 持有进程已不在：winpid=$owner_pid（$who 接手；上一位：$owner）"
    rm -rf "$NEGCTL_LOCK_DIR"
    negctl_take "$who" || { echo "[STOP] 陈旧锁接管失败：目录还在 $NEGCTL_LOCK_DIR"; return 1; }
    trap 'negctl_release' EXIT
    return 0
  fi
  echo "[STOP] 已有负控脚本在跑，锁被占着：$NEGCTL_LOCK_DIR"
  echo "       持有者 winpid=$owner_pid  $owner"
  echo "       负控簿会就地改源码，并行必然互相踩出假红/假绿（本轮 00:02:54 之后那批读数就是这么废的）。"
  echo "       等它跑完；确认那个进程真的死了再 rm -rf $NEGCTL_LOCK_DIR。"
  echo "       （另开 NEGCTL_LOCK_DIR=… 等于自愿放弃互斥，后果自担）"
  return 3
}
