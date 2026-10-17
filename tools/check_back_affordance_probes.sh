#!/usr/bin/env bash
# 判据簿：每个整屏路由都要有**看得见**的返回出口（用户 2026-10-15 指令："我希望所有功能界面都有返回键或手势返回"）。
#
# 为什么要有这一本：`BackTopBar` 这个原语早就存在，但**没有任何一格数过"哪些屏用了它"** ——
# 结果就是扫码/拍照/交接这类整屏页一台不响，用户按系统返回键之外无路可走。
# 规矩只写在注释里等于没有规矩（D-082 那一轮的同一课），所以这一格立成闸。
#
# ⚠ 判据必须按**函数体**数，不能按文件数：`MinePages.kt` 一个文件里住着 Address/Feedback/Profile/Settings
#   四屏，文件级"有 6 处 BackTopBar"会让其中任何一屏缺出口都读成"有"（本轮实测过这个假象）。
# ⚠ 空集不算通过：解析器一坏，"缺失 = 0"与"一条都没扫到"长得一模一样 ⇒ BA-d 是反证格。
set -u
SELF="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="${BUSPOST_ANDROID:-$SELF}"
cd "$ROOT" || exit 1
SRC=app/src/main/java/com/shanxiang/buspost

PY=$(PYTHONIOENCODING=utf-8 python - "$SRC" <<'PY'
import os, re, sys
src = sys.argv[1]
# 壳层与登录页不是"推上来的整屏"，没有可退的一格；DEBUG-only 的路由由 BuildConfig 那条判断挡着
SHELLS = {"DRIVER_SHELL", "USER_SHELL", "LOGIN"}
root = os.path.join(src, "ui")
text = open(os.path.join(root, "BusPostRoot.kt"), encoding="utf-8", errors="replace").read()
dests = {}
for m in re.finditer(r"composable\(Routes\.([A-Z_]+)\)(.{0,220}?)\n", text, re.S):
    d, tail = m.group(1), m.group(2)
    if d in SHELLS:
        continue
    f = re.search(r"([A-Z][A-Za-z0-9]*Screen)\(", tail)
    if f:
        dests.setdefault(d, f.group(1))
bodies = {}
for base, dirs, files in os.walk(root):
    for fn in files:
        if not fn.endswith(".kt"):
            continue
        p = os.path.join(base, fn)
        lines = open(p, encoding="utf-8", errors="replace").read().splitlines()
        for i, l in enumerate(lines):
            m2 = re.match(r"^(?:internal |private |public )?fun ([A-Z][A-Za-z0-9]*Screen)\(", l)
            if not m2:
                continue
            j = i + 1
            while j < len(lines) and lines[j] != "}":
                j += 1
            bodies.setdefault(m2.group(1), []).append("\n".join(lines[i:j + 1]))
MARK = re.compile(r"BackTopBar\(|BusBackArrow\(|onBack\s*=\s*\{|CapsuleActions\(")
missing, hit = [], 0
for d in sorted(dests):
    scr = dests[d]
    ok = any(MARK.search(b) for b in bodies.get(scr, []))
    if ok:
        hit += 1
    else:
        missing.append(d)
print("DESTS=%d" % len(dests))
print("HIT=%d" % hit)
print("BODIES=%d" % sum(len(v) for v in bodies.values()))
print("MISSING=%s" % ",".join(missing))
PY
)
eval "$(printf '%s\n' "$PY" | sed -n 's/^\([A-Z]*\)=\(.*\)$/\1="\2"/p')"

# 单一真源：箭头只许有 BusBackArrow 这一份实现，ui/ 里不许再手搓一个
BA_ARROW_DEF=$(grep -rn "fun BusBackArrow" "$SRC" | wc -l | tr -d ' ')
BA_ROUGH=$(grep -rn "Icons\.\(Rounded\|AutoMirrored\|Filled\)\.ArrowBack" "$SRC/ui" --include=*.kt | wc -l | tr -d ' ')
BA_SIGN=$(grep -rn "onBack = { nav.popBackStack() }" "$SRC/ui" --include=*.kt | wc -l | tr -d ' ')

echo "BA-a 整屏路由目的地数        = $DESTS （目标 24：新增一屏就得同轮登记，不许悄悄扩大扫描面）"
echo "BA-b 有可见出口的屏数        = $HIT （目标 23 = 24 − 下面那一条豁免）"
echo "BA-c 无出口名单              = ${MISSING:-（空）} （目标恰好 DRIVER_NAV，按名排序）"
echo "BA-d 反证：扫到的屏函数体数   = $BODIES （目标 ≥30：数得出 0 缺口的解析器必须也数得出这么多体）"
echo "BA-e 箭头实现份数 / ui 里手搓 = $BA_ARROW_DEF/$BA_ROUGH （目标 1/0：BusIcons.back 是唯一来源）"
echo "BA-f 挂上的出口数            = $BA_SIGN （目标 6：BackTopBar 自己接的 1 + 司机消息 1 + 交接 1 + 寄货三步 3）"

fail=0
[ "$DESTS" = 24 ] || { echo "× BA-a 整屏路由数不是 24（$DESTS）：要么新增了屏没验出口，要么解析面塌了"; fail=1; }
[ "$HIT" = 23 ] || { echo "× BA-b 有出口的屏不是 23（$HIT）：有整屏路由拿不出看得见的退出口"; fail=1; }
[ "$MISSING" = "DRIVER_NAV" ] || { echo "× BA-c 无出口名单变了（$MISSING）：现在只剩 DEBUG-only 一条豁免（BUS 与 DRIVER_GUIDE 自 D-156 起由悬浮胶囊给出口），动它要写理由"; fail=1; }
[ "${BODIES:-0}" -ge 30 ] || { echo "× BA-d 只扫到 $BODIES 个屏函数体：解析器没上岗，上面那个缺口读数不采信"; fail=1; }
[ "$BA_ARROW_DEF" = 1 ] || { echo "× BA-e BusBackArrow 有 $BA_ARROW_DEF 份定义：返回键的形状要单一真源"; fail=1; }
[ "$BA_ROUGH" = 0 ] || { echo "× BA-e ui/ 里出现 $BA_ROUGH 处手搓 ArrowBack：绕开 BusIcons.back 就没有统一形状与命中下限"; fail=1; }
[ "$BA_SIGN" = 6 ] || { echo "× BA-f 出口挂点不是 6（$BA_SIGN）：五处屏内箭头 + BackTopBar 自己那一处，少一处就是有人撤了退出口"; fail=1; }

[ "$fail" = 0 ] && echo "返回出口判据通过" || echo "返回出口判据失败"
exit $fail
