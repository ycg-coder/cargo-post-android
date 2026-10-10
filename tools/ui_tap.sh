#!/usr/bin/env bash
# 按**可见文字或朗读名**点一下：从最近一次 uiautomator dump 的 xml 里找节点，点它 bounds 的中心。
# 用法：BUSPOST_SERIAL=<序列号> bash tools/ui_tap.sh "<text 或 content-desc 的子串>" [第几台设备的 xml]
# 为什么不用 `input text`：坐标写死在脚本里就等于假设屏幕布局不变，平板/手机/字号一换全错。
# 找不到就停下说实话（exit 3），不许"没点上也当前进走了"——那会把上一屏的截图当成这一屏的证据。
set -uo pipefail
export MSYS_NO_PATHCONV=1
ADB="${BUSPOST_ADB:-/d/Android/Sdk/platform-tools/adb.exe}"
DEV=(); [ -n "${BUSPOST_SERIAL:-}" ] && DEV=(-s "$BUSPOST_SERIAL")
want="$1"
xml="${2:-/sdcard/ui.xml}"

TMPW="$(cygpath -w /tmp/_tap.xml)"     # ⚠ Git Bash 的 /tmp 对 Windows python 不是同一路径，
"$ADB" ${DEV[@]+"${DEV[@]}"} shell "uiautomator dump /sdcard/_tap.xml" >/dev/null 2>&1
"$ADB" ${DEV[@]+"${DEV[@]}"} pull /sdcard/_tap.xml "$TMPW" >/dev/null 2>&1
[ -s /tmp/_tap.xml ] || { echo "NO_XML_DUMP：这一屏拿不到节点，先确认画面是否停在动画上"; exit 4; }

xy=$(python - "$want" "$TMPW" <<'PY'
import sys, re, xml.etree.ElementTree as ET
want, path = sys.argv[1], sys.argv[2]
N = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
hits = []
for e in ET.parse(path).iter():
    a = e.attrib
    if want in (a.get("text") or "") or want in (a.get("content-desc") or ""):
        m = N.search(a.get("bounds", ""))
        if m:
            l, t, r, b = (int(x) for x in m.groups())
            hits.append((l, t, r, b, a.get("class", ""), (a.get("text") or a.get("content-desc") or "")[:24]))
if not hits:
    sys.exit("NOMATCH")
# 精确匹配优先：本轮实测踩过——点「登录」时先命中了「验证码登录」那个模式切换链接，
# 表单被打回密码态，我却以为"已经点过登录了"，再往下的截图就全是上一屏的。
exact = [h for h in hits if h[5] == want]
pick = exact or hits
if len(pick) > 1:
    print("AMBIGUOUS %d 处（exact=%d）：" % (len(pick), len(exact)), file=sys.stderr)
    for h in pick: print("   ", h, file=sys.stderr)
l, t, r, b = pick[0][:4]
print("%d %d" % ((l + r) // 2, (t + b) // 2))
PY
) || { echo "NOMATCH：当前屏没有含「$want」的节点（这一屏没到 / 文案不是这句）"; exit 3; }
[ -n "$xy" ] || { echo "NOMATCH"; exit 3; }
echo "点击「$want」→ $xy"
"$ADB" ${DEV[@]+"${DEV[@]}"} shell input tap $xy
