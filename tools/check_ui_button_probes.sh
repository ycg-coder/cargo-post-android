#!/usr/bin/env bash
# UI 控件规格一致性判据簿（#27/#28，2026-10-14）。
# 管的是用户 2026-10-14 点名的那件事：**同一界面、同一行的按钮大小形状必须统一**。
# 本轮的实测起点（有凭证，不是编的）：改动前裸 Material 按钮 34 处、同 Row 混用 2 处；
# 现在都是 0。"目标 0" 的每一格都由 tools/ui_button_negative_controls.sh 反证过它会咬。
#
# 为什么不钉像素/字号/行距：本机无设备，那一半只能记 NOT VERIFIED ON DEVICE（见 CONTEXT_LOCK §八 ㊵ 的旧决定）。
# 这里全部钉的是**写法层**——写法层能静态判，且正是"分叉"发生的地方。
set -u
SELF="$(cd "$(dirname "$0")/.." && pwd)"
# 负控沙盒靠这个变量把扫描面指到副本上；不接的话沙盒里量的还是真身，
# 六条破口会全部"失效"而其实是判据没跟着移动（tools/ui_button_negative_controls.sh 第一轮就是这么抓到的）
ROOT="${BUSPOST_ANDROID:-$SELF}"
cd "$ROOT" || exit 1
M=app/src/main/java/com/shanxiang/buspost
[ -d "$M/ui" ] || { echo "× 扫描面塌了：$M/ui 不在 ⇒ 下面所有 0 都是假的"; exit 1; }

# 只算调用点：设计系统本体（core/ui/）允许出现 Material 原语，它就是包这一层的

ok=1
ub_a=$(grep -rn --include=*.kt -E "(^|[^A-Za-z])(TextButton|OutlinedButton|FilledTonalButton|ElevatedButton|MaterialButton|Button)\(" "$M" 2>/dev/null \
  | grep -v "$M/core/ui/" | grep -vE "^[^:]+:[0-9]+: *(//|\*)" | wc -l)
printf 'UB-a 裸 Material 按钮调用点        : %s （目标 0；本轮改动前实测 34）\n' "$ub_a"

# 解释器按本仓既有写法现找（`check_runcatching_probes.sh` / `check_oss_credits_probes.sh` 同一手法）：
# runner 上可能只有 python3，而 `python` 不存在时 heredoc 会展开成空 ⇒ UB-b 读成空串，
# `[ "$ub_b" = 0 ]` 当场红 —— 那是"跑不了"被读成"有破口"，两样都不许。
PY=$(command -v python || command -v python3)
[ -n "$PY" ] || { echo "× 找不到 python：UB-b 的同行检测无法执行，不许把'跑不了'读成 0"; exit 2; }

ub_b=$("$PY" - <<'PY'
import io, os, re
ROOT = "app/src/main/java/com/shanxiang/buspost"
ROW = re.compile(r"^\s*(?:\w+\.)*Row\s*[({]")
BTN = re.compile(r"(^|[^A-Za-z])(BusButton|BusDialogAction|TextButton|OutlinedButton|Button)\s*\(")
SMALL = re.compile(r"small\s*=\s*(true|false)")
n = 0
for dp, _, fs in os.walk(ROOT):
    for fn in sorted(fs):
        if not fn.endswith(".kt"):
            continue
        p = os.path.join(dp, fn)
        src = io.open(p, encoding="utf-8", errors="replace").read().split("\n")
        for i, l in enumerate(src):
            if not ROW.search(l):
                continue
            d = 0
            end = i
            for j in range(i, min(len(src), i + 90)):
                d += src[j].count("{") - src[j].count("}")
                end = j
                if d <= 0 and j > i:
                    break
            ent = []
            for k in range(i, end + 1):
                m = BTN.search(src[k])
                if not m:
                    continue
                # 参数读到**自己这对括号闭合**为止（上一版用固定 8 行窗口，
                # 把 TraceScreen 那颗多行 onClick 的 small=true 漏在窗口外 ⇒ 报了个假阳性）
                depth = 0
                w = []
                for q in range(k, min(len(src), k + 40)):
                    depth += src[q].count("(") - src[q].count(")")
                    w.append(src[q])
                    if depth <= 0 and q >= k:
                        break
                blk = "\n".join(w)
                sm = SMALL.search(blk)
                kind = m.group(2).replace("BusDialogAction", "Bus")
                # BusDialogAction 在设计系统里就是一颗 small=true 的 BusButton（TEXT/PRIMARY 档），
                # 调用点不会也不该再写 small= ⇒ 按定义算，否则它和 small=true 的兄弟会被误报成"两种规格"
                small = "true" if m.group(2) == "BusDialogAction" else (sm.group(1) if sm else "def")
                ent.append((kind, small))
            if len(ent) >= 2 and (len({e[0] for e in ent}) > 1 or len({e[1] for e in ent}) > 1):
                n += 1
print(n)
PY
)
printf 'UB-b 同一 Row 内按钮混用          : %s （目标 0：种类不同或 small 不同都算；改动前实测 2）\n' "$ub_b"

ub_c=$(grep -rn --include=*.kt -A8 "BusButton(\|BusDialogAction(" "$M" 2>/dev/null | grep -v "$M/core/ui/" \
  | grep -cE "shape\s*=\s*(RoundedCornerShape|CircleShape|BusRadius)")
printf 'UB-c 调用点自己传 shape            : %s （目标 0：圆角只能由 BusRadius 令牌在设计系统里定）\n' "$ub_c"

ub_d=$(grep -rn --include=*.kt -E "defaultMinSize\(\s*minHeight\s*=\s*[0-9]" "$M" 2>/dev/null | grep -v "$M/core/ui/" | wc -l)
printf 'UB-d 调用点写死命中下限            : %s （目标 0：下限只能来自 BusTouch/LocalBusTouch）\n' "$ub_d"

ub_e=$(grep -c "fun BusDialogAction(" "$M/core/ui/Components.kt" 2>/dev/null)
ub_e_ok=$([ "$ub_e" = 1 ] && echo 1 || echo 0)
printf 'UB-e 设计系统里有且仅有一个对话框动作出口 : %s （目标 1）\n' "$ub_e"

ub_f=$(grep -rn --include=*.kt -c "BusButton(\|BusDialogAction(" "$M"/ui "$M"/core/update 2>/dev/null | awk -F: '{s+=$2} END {print s+0}')
printf 'UB-f 反证输入面：Bus 按钮调用点总数 : %s （下限 120；把组件全删空会让 UB-a 的 0 变成假绿）\n' "$ub_f"

[ "$ub_a" = 0 ] || { echo "× UB-a 有裸 Material 按钮 ⇒ 同一行必然出现两种高度/字重/命中区"; ok=0; }
[ "$ub_b" = 0 ] || { echo "× UB-b 有同一 Row 内按钮混用：这就是「一排按钮一大一小」的形状"; ok=0; }
[ "$ub_c" = 0 ] || { echo "× UB-c 调用点绕开令牌自己传圆角"; ok=0; }
[ "$ub_d" = 0 ] || { echo "× UB-d 调用点写死命中高度，驾驶模式的 56dp 下限会失效"; ok=0; }
[ "$ub_e_ok" = 1 ] || { echo "× UB-e 对话框动作出口没了或被重复定义（重复定义=同一问题两个答案）"; ok=0; }
[ "$ub_f" -ge 120 ] || { echo "× UB-f Bus 按钮调用点掉到 120 以下 ⇒ 扫描面或组件被拆，UB-a 的 0 不再有意义"; ok=0; }

[ "$ok" = 1 ] || exit 1
echo "UI 控件规格判据通过（裸 Material 按钮 $ub_a、同行混用 $ub_b、自传 shape $ub_c、写死下限 $ub_d，Bus 按钮调用点 $ub_f 处）"
