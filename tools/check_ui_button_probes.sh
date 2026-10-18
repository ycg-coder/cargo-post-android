#!/usr/bin/env bash
# UI 控件规格一致性判据簿（#27/#28，2026-10-14）。
# 管的是用户 2026-10-14 点名的那件事：**同一界面、同一行的按钮大小形状必须统一**。
# 本轮的实测起点（有凭证，不是编的）：改动前裸 Material 按钮 34 处、同 Row 混用 2 处；
# 现在都是 0。"目标 0" 的每一格都由 tools/ui_button_negative_controls.sh 反证过它会咬。
#
# 为什么不钉像素/字号/行距：那些是"分叉"发生的地方之外的东西，写法层能静态判。
# ⚠ 2026-10-18 更正：**热区的像素这一半现在钉得住**，但不是从源码判 —— 是从归档的真机 dump 量（UB-k）。
#   起因正是这条旧理由让我把"28dp 视口"当成了热区欠账记在 D-UI-1 上：真机量出来那条的可点框是 132px=48dp，
#   而同一屏**源码里根本没有 `.height()`** 的天气胶囊只有 91px=33dp。写法层看不见这件事，像素才看得见。
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

# UB-g / UB-h：命中下限这一族。UB-d 钉的是 defaultMinSize 写死数字，这两格钉同一族里
# 用 .height(N.dp) 把"按钮状可点块"卡到 48dp 以下的写法 —— 本轮实测的三处（登录闸门 46dp 的渐变块、
# 站牌配色格 46dp、体验模式/获取那类无垂直热区的文字块）全是这一形。
# 视口（HorizontalPager 的 modifier 参数）单独一格 UB-h：抬它改的是排版而不是热区写法，
# 本机无设备 ⇒ 只能登记成在册残留（D-UI-1），但**数量**能钉死，新增一条就红。
ub_gh=$("$PY" - <<'PY'
import io, os, re
ROOT = "app/src/main/java/com/shanxiang/buspost"
SKIP = os.path.join(ROOT, "core", "ui")
H = re.compile(r"\.height\((\d+(?:\.\d+)?)\.dp\)")
g = []
g2 = []
for dp, _, fs in os.walk(ROOT):
    if dp.startswith(SKIP):
        continue
    for fn in sorted(fs):
        if not fn.endswith(".kt"):
            continue
        p = os.path.join(dp, fn)
        src = io.open(p, encoding="utf-8", errors="replace").read().split("\n")
        for i, l in enumerate(src):
            m = H.search(l)
            if not m:
                continue
            h = float(m.group(1))
            if h <= 0 or h >= 48 or "Spacer(" in l:
                continue
            win = "\n".join(src[max(0, i - 8): i + 9])
            if ".clickable" not in win:
                continue
            if l.strip().startswith("modifier = Modifier"):
                g2.append("%s:%d" % (p, i + 1))
            elif ".clip(" in win or ".background(" in win:
                g.append("%s:%d" % (p, i + 1))
print(len(g))
print(len(g2))
for x in g:
    print("G " + x, file=__import__("sys").stderr)
for x in g2:
    print("H " + x, file=__import__("sys").stderr)
PY
)
ub_g=$(head -1 <<<"$ub_gh"); ub_h=$(sed -n '2p' <<<"$ub_gh")
printf 'UB-g 低于 48dp 的手搓按钮状块      : %s （目标 0；本轮改动前实测 1 处 + 同类文字块 3 处）\n' "$ub_g"
printf 'UB-h 低于 48dp 的轮播视口          : %s （在册恰好 1 = 首页公告条的 28dp **视口**；⚠ 它是排版不是热区病，见下面 UB-k）\n' "$ub_h"

[ "$ub_a" = 0 ] || { echo "× UB-a 有裸 Material 按钮 ⇒ 同一行必然出现两种高度/字重/命中区"; ok=0; }
[ "$ub_b" = 0 ] || { echo "× UB-b 有同一 Row 内按钮混用：这就是「一排按钮一大一小」的形状"; ok=0; }
[ "$ub_c" = 0 ] || { echo "× UB-c 调用点绕开令牌自己传圆角"; ok=0; }
[ "$ub_d" = 0 ] || { echo "× UB-d 调用点写死命中高度，驾驶模式的 56dp 下限会失效"; ok=0; }
[ "$ub_e_ok" = 1 ] || { echo "× UB-e 对话框动作出口没了或被重复定义（重复定义=同一问题两个答案）"; ok=0; }
[ "$ub_f" -ge 120 ] || { echo "× UB-f Bus 按钮调用点掉到 120 以下 ⇒ 扫描面或组件被拆，UB-a 的 0 不再有意义"; ok=0; }
[ "$ub_g" = 0 ] || { echo "× UB-g 有低于 48dp 的手搓按钮状块：驾驶模式 56dp 下限对它完全不生效"; ok=0; }
[ "$ub_h" = 1 ] || { echo "× UB-h 轮播视口数不是恰好 1（现 $ub_h）：多了=新增长在这种视口里的可点块；少了=那条视口被抬高了（那要连排版决定一起说清，不许顺手改闸）。⚠ 2026-10-15 由 2 降到 1 不是抬高：第二条是首页那个**零调用点**的旧轮播渲染件（42dp 视口从来没上过屏），随 D-120 一起删掉。⚠ 2026-10-18 更正记账：剩下这一条（HomeScreen 公告条 28dp 视口）**不是热区欠账** —— 真机 v163 dump 量到它的可点框是 132px=48dp（D-UI-1 当年把它记成欠账是把"视口矮"当成了"点不着"）；UB-k 钉的就是量出来的像素"; ok=0; }

# UB-i 一排里的"第二颗"必须吃 TONAL（真机 2026-10-18 用户："工作台的确认发车和扫码作业和这个界面不太搭配"）
#     实心 PRIMARY 旁边挂一颗只剩描边的 OUTLINE ⇒ 两颗差一个视觉重量档，看着像"一颗按钮 + 一个没画完的框"
ub_i_def=$(grep -cE '^enum class BusButtonVariant' "$M/core/ui/Components.kt")
ub_i_tonal=$(grep -cE 'TONAL' "$M/core/ui/Components.kt")
ub_i_board=$(grep -cE 'BusButtonVariant\.TONAL -> t\.primaryLight' "$M/core/ui/Components.kt")
ub_i_scan=$(grep -A12 'text = "扫码作业"' "$M/ui/driver/WorkbenchScreen.kt" | grep -c 'variant = BusButtonVariant.TONAL')
ub_i_bad=$(grep -A12 'text = "扫码作业"' "$M/ui/driver/WorkbenchScreen.kt" | grep -c 'BusButtonVariant.OUTLINE')
printf 'UB-i 档位定义/TONAL 出现/板色/扫码那颗 : %s/%s/%s/%s （坏形 %s，目标 1/≥3/1/1 且坏形 0）
' "$ub_i_def" "$ub_i_tonal" "$ub_i_board" "$ub_i_scan" "$ub_i_bad"
[ "$ub_i_def" = 1 ] || { echo "× UB-i 档位枚举不是恰好一份（现 $ub_i_def）：两套档位表就是同一问题两个答案"; ok=0; }
[ "$ub_i_board" = 1 ] || { echo "× UB-i TONAL 的板色没接进 when（现 $ub_i_board）：档位加了但画不出来"; ok=0; }
[ "$ub_i_scan" = 1 ] || { echo "× UB-i 工作台「扫码作业」没吃 TONAL（现 $ub_i_scan）：它左边那颗是实心 PRIMARY，两颗差一个重量档就是用户说的那句不搭配"; ok=0; }
[ "$ub_i_bad" = 0 ] || { echo "× UB-i 工作台「扫码作业」又长回描边档（现 $ub_i_bad）：同上一条"; ok=0; }

# UB-j 同一排里"实心 + 描边"的混档 = 0（D-161 用户那句"和这个界面不太搭配"的通用形状）
#     唯一豁免：那颗描边档是 DEBUG-only（调试包才画出来），司机/乘客在正式包里看不见它 ⇒ 不构成一排的搭配问题。
#     豁免要**点名**：整行块里必须出现 BuildConfig.DEBUG，否则不认。
ub_j=$("$PY" - <<'PYJ'
import io, os, re
ROOT = "app/src/main/java/com/shanxiang/buspost"
ROW = re.compile(r"^\s*(?:\w+\.)*Row\s*[({]")
BTN = re.compile(r"(^|[^A-Za-z])BusButton\s*\(")
VAR = re.compile(r"variant\s*=\s*BusButtonVariant\.(\w+)")
n = 0
for dp, _, fs in os.walk(ROOT):
    for fn in sorted(fs):
        if not fn.endswith(".kt"):
            continue
        pth = os.path.join(dp, fn)
        src = io.open(pth, encoding="utf-8", errors="replace").read().split("\n")
        for i, line in enumerate(src):
            if not ROW.search(line):
                continue
            d, end = 0, i
            for j in range(i, min(len(src), i + 120)):
                d += src[j].count("{") - src[j].count("}")
                end = j
                if d <= 0 and j > i:
                    break
            block = "\n".join(src[i:end + 1])
            variants = []
            for k in range(i, end + 1):
                if not BTN.search(src[k]):
                    continue
                depth = 0
                w = []
                for q in range(k, min(len(src), k + 45)):
                    depth += src[q].count("(") - src[q].count(")")
                    w.append(src[q])
                    if depth <= 0 and q >= k:
                        break
                m = VAR.search("\n".join(w))
                variants.append(m.group(1) if m else "PRIMARY")
            if len(variants) < 2:
                continue
            if "PRIMARY" in variants and ("OUTLINE" in variants or "GHOST" in variants):
                if "BuildConfig.DEBUG" in block:
                    continue
                n += 1
                print("MIX %s:%d %s" % (pth, i + 1, variants))
print(n)
PYJ
)
ub_j_bad=$(printf '%s\n' "$ub_j" | grep -c '^MIX' | tr -d ' ')
ub_j_n=$(printf '%s\n' "$ub_j" | tail -1)
printf 'UB-j 同排"实心+描边"混档          : %s （目标 0；豁免=DEBUG-only 那一处，现点名 %s 条）\n' "$ub_j_n" "$ub_j_bad"
[ "$ub_j_bad" = 0 ] || { echo "× UB-j 有 $ub_j_bad 排把实心主色板和描边档并排放：这就是用户说的那句不搭配（第二颗该吃 TONAL）"; printf '%s\n' "$ub_j" | grep '^MIX' | head -5; ok=0; }

# UB-k 通栏可点格**量出来的**命中框（探针 tools/hot_zone_probe.py，只认像素不认写法）
#     为什么要新增这一格：UB-g/UB-h 都在源码形状上找"矮"，而真机 2026-10-18 打脸 ——
#     形状矮的那条（公告条 28dp 视口）命中框实际 132px=48dp，形状上什么都看不出的天气胶囊实际 91px=33dp。
#     写法层看不见这件事，像素看得见 ⇒ 把像素钉进判据，用归档 dump 当凭证。
KDIR=docs/device-evidence/20261018-hotzone
if [ ! -d "$KDIR" ]; then
  echo "× UB-k 归档热区证据目录不在（$KDIR）：判据不许因为没证据就把读数当 0"; ok=0
  ub_kd=""
else
  ub_kd=$("$PY" tools/hot_zone_probe.py --dump "$KDIR" 2>&1)
fi
ub_ks=$("$PY" tools/hot_zone_probe.py --source 2>&1 | head -1)
ub_k_s1=$(printf '%s' "$ub_ks" | sed -n 's/.*s1=\([0-9]*\).*/\1/p')
ub_k_s2=$(printf '%s' "$ub_ks" | sed -n 's/.* s2=\([0-9]*\).*/\1/p')
ub_k_s3=$(printf '%s' "$ub_ks" | sed -n 's/.* s3=\([0-9]*\).*/\1/p')
ub_k_order=$(printf '%s' "$ub_ks" | sed -n 's/.*order=\([0-9]*\).*/\1/p')
ub_k_click=$(printf '%s' "$ub_ks" | sed -n 's/.*click_first=\([0-9]*\).*/\1/p')
ub_k_pill_b=$(printf '%s\n' "$ub_kd" | grep before | grep 'pill h=' | sed -n 's/.*h=\([0-9]*\).*/\1/p' | head -1)
ub_k_caro_b=$(printf '%s\n' "$ub_kd" | grep before | grep 'carousel h=' | sed -n 's/.*h=\([0-9]*\).*/\1/p' | head -1)
ub_k_after_n=$(printf '%s\n' "$ub_kd" | grep after | grep -c 'pill h=')
ub_k_after_min=$(printf '%s\n' "$ub_kd" | grep after | grep 'pill h=' | sed 's/.*h=\([0-9]*\).*/\1/' | sort -n | head -1)
printf 'UB-k 胶囊接线 touch/heightIn/未回潮/顺序/可点在前 : %s/%s/%s/%s/%s （目标 1/1/0/1/1）\n' "$ub_k_s1" "$ub_k_s2" "$ub_k_s3" "$ub_k_order" "$ub_k_click"
printf 'UB-k 量出来：改前胶囊 %s px、改后最小 %s px（改前须恰好 91=33dp，改后须有 ≥1 份且 ≥132px）\n' "$ub_k_pill_b" "$ub_k_after_min"
printf 'UB-k 量出来：公告条命中框 %s px（须恰好 132 —— 这条是"视口矮≠点不着"的凭证，防它再被记成欠账）\n' "$ub_k_caro_b"
[ "$ub_k_s1" = 1 ] || { echo "× UB-k 胶囊没读 BusTouch 令牌（现 $ub_k_s1）：驾驶模式的 56dp 下限对它不生效"; ok=0; }
[ "$ub_k_s2" = 1 ] || { echo "× UB-k 胶囊没有 heightIn(min = touch.min) 那一行（现 $ub_k_s2）：33dp 的命中框会原样回来"; ok=0; }
[ "$ub_k_s3" = 0 ] || { echo "× UB-k 胶囊换回了 defaultMinSize（现 $ub_k_s3）：真机 v173 用 defaultMinSize 只量到 105px=38dp，没到下限 —— 这条是被实测否掉的写法，不许再回来"; ok=0; }
[ "$ub_k_order" = 1 ] || { echo "× UB-k heightIn 排在了垂直留白**之后**：撑大的那块不在可点节点上，看着抬了量出来没抬"; ok=0; }
[ "$ub_k_click" = 1 ] || { echo "× UB-k clickable 排在了 heightIn 之后：撑出来的高度归不了可点节点"; ok=0; }
[ "$ub_k_pill_b" = 91 ] || { echo "× UB-k 改前凭证读数不是恰好 91（现 '$ub_k_pill_b'）：归档 dump 被动过，或探针的锚点定位失效了（锚点=文字「4日预报」）"; ok=0; }
[ "$ub_k_caro_b" = 132 ] || { echo "× UB-k 公告条凭证读数不是恰好 132（现 '$ub_k_caro_b'）：锚点（前导两个空格的那行字）失效或那条排版被改；若是排版被改，要连 UB-h 一起说清"; ok=0; }
[ "$ub_k_after_n" -ge 1 ] || { echo "× UB-k 没有改后的真机 dump（*after*.xml）：这一格是**屏幕复验**判据，没证据就是没做，不许靠源码接线自证"; ok=0; }
[ -n "$ub_k_after_min" ] && [ "$ub_k_after_min" -ge 132 ] || { echo "× UB-k 改后命中框最小 $ub_k_after_min px < 132（48dp@2.75）：抬没生效，或被滚动视口裁到了（取证要让目标完整可见，见 D-164 的测量法坑）"; ok=0; }

# UB-l 归档 dump 的**全量**通栏热区扫（D-165；探针 tools/hot_zone_sweep.py）
#     UB-k 管的是"我量过的那一格别再退回去"，这一格管的是"别处再长出一颗 33dp 的必须咬"。
#     口径两条都是从真机踩出来的：通栏（宽≥400）才算"点这一整条"的承诺；贴滚动容器底的是被视口裁的半截，不算病。
SDIR=docs/device-evidence/20261018-sweep
if [ ! -d "$SDIR" ]; then
  echo "× UB-l 全量热区证据目录不在（$SDIR）：没证据就是没扫，不许读成 0"; ok=0
  ul_files=0; ul_short=-1
else
  ul_files=$(ls -1 "$SDIR"/*.xml 2>/dev/null | wc -l | tr -d ' ')
  ul_out=$("$PY" tools/hot_zone_sweep.py "$SDIR" 2>&1)
  ul_short=$(printf '%s\n' "$ul_out" | sed -n 's/^TOTAL .* short=\([0-9-]*\).*/\1/p' | head -1)
fi
ul_floor=$(grep -c "floor, min_w, clip_tol = argv\[0\], 132, 400, 8" tools/hot_zone_sweep.py)
ul_bad=$("$PY" tools/hot_zone_sweep.py tools/fixtures/hot_sweep_bad 2>&1 | sed -n 's/^TOTAL .* short=\([0-9-]*\).*/\1/p' | head -1)
ul_bad_named=$("$PY" tools/hot_zone_sweep.py tools/fixtures/hot_sweep_bad 2>&1 | grep -c '^DEFECT .* 904x91 ')
ul_good=$("$PY" tools/hot_zone_sweep.py tools/fixtures/hot_sweep_ok 2>&1 | sed -n 's/^TOTAL .* short=\([0-9-]*\).*/\1/p' | head -1)
# 覆盖下限：光"份数够"不算覆盖 —— 12 张首屏也能凑出 17 份。D-166 把两类曾经没扫的面（滚动区下方、弹层）
# 钉成**必须有的命名**，谁把它们从归档里删掉，这一格就红，而不是退回到"看起来扫过了"。
ul_scroll=$(printf '%s\n' "$ul_out" | grep -c 'SWEEP [^ ]*scroll[^ ]*\.xml ')
ul_layer=$(printf '%s\n' "$ul_out" | grep -cE 'SWEEP [^ ]*(sheet|dialog)[^ ]*\.xml ')
ul_user=$(printf '%s\n' "$ul_out" | grep -c 'SWEEP [^ ]*user-')
ul_drv=$(printf '%s\n' "$ul_out" | grep -c 'SWEEP [^ ]*driver-')
printf 'UB-l 归档 dump 份数/下限 132 在册      : %s / %s （目标 ≥10 且恰好 1）\n' "$ul_files" "$ul_floor"
printf 'UB-l 覆盖面：滚动后 %s 弹层 %s 用户端 %s 司机端 %s （目标 ≥2/≥2/≥4/≥4）\n' "$ul_scroll" "$ul_layer" "$ul_user" "$ul_drv"
printf 'UB-l 全量扫：通栏矮格 %s（目标 0）；反证件 %s→须恰好 1 且点名 904x91（现 %s/%s）；正件 %s（目标 0）\n' \
  "$ul_short" "$ul_bad" "$ul_bad" "$ul_bad_named" "$ul_good"
[ "$ul_floor" = 1 ] || { echo "× UB-l 扫描器的下限/宽度默认值被改（现 $ul_floor）：把 132 调小或把 400 调大，都能让全量扫'零缺陷'——那是把尺子锯短"; ok=0; }
[ "$ul_files" -ge 10 ] || { echo "× UB-l 归档 dump 只有 $ul_files 份（下限 10）：两端各 4 个主 tab + 详情格，少一份就是有一片屏没人量过"; ok=0; }
[ "$ul_short" = 0 ] || { echo "× UB-l 全量扫出 $ul_short 颗通栏矮格：写的是'点这一整条'，量出来却够不到 48dp（点名见 tools/hot_zone_sweep.py 的 DEFECT 行）"; printf '%s\n' "$ul_out" | grep '^DEFECT' | head -5; ok=0; }
[ "$ul_bad" = 1 ] || { echo "× UB-l 反证件没数出恰好 1（现 '$ul_bad'）：扫描器瞎了，上面那个 0 就是假的"; ok=0; }
[ "$ul_bad_named" = 1 ] || { echo "× UB-l 反证件没点名那颗 904x91（现 $ul_bad_named）：只报个数不报是哪一格，等于不判据"; ok=0; }
[ "$ul_good" = 0 ] || { echo "× UB-l 正件被误报 $ul_good 颗：同一把尺子在合规几何上不该响"; ok=0; }
[ "$ul_scroll" -ge 2 ] || { echo "× UB-l 归档里滚动后的证据只有 $ul_scroll 份（下限 2）：首屏干净不代表折叠在下面的格子也干净——D-164 那颗 33dp 胶囊就在首屏，而屏底的按钮要滚上来才量得准"; ok=0; }
[ "$ul_layer" -ge 2 ] || { echo "× UB-l 归档里弹层/对话框证据只有 $ul_layer 份（下限 2）：弹层是手搓按钮最容易长回来的地方，没证据就是没扫"; ok=0; }
[ "$ul_user" -ge 4 ] || { echo "× UB-l 用户端证据 $ul_user 份（下限 4）"; ok=0; }
[ "$ul_drv" -ge 4 ] || { echo "× UB-l 司机端证据 $ul_drv 份（下限 4）：两端的热区下限不同（驾驶模式 56dp），只扫一端不算扫过全工程"; ok=0; }

[ "$ok" = 1 ] || exit 1
echo "UI 控件规格判据通过（裸 Material 按钮 $ub_a、同行混用 $ub_b、自传 shape $ub_c、写死下限 $ub_d、低热区手搓块 $ub_g，Bus 按钮调用点 $ub_f 处；归档 $ul_files 份真机 dump 全量热区扫 通栏矮格 $ul_short 颗，胶囊命中框改后 ≥132px）"
