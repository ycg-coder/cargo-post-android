#!/usr/bin/env bash
# 抓一屏取证：uiautomator dump + 截图 + 可读节点清单 + 布局几何自检。
# 用法：bash tools/ui_capture.sh <屏名> [输出目录]
#   环境变量：BUSPOST_ADB=adb 全路径；BUSPOST_SERIAL=设备序列号（多设备时必须给）
# 控制台是 GBK，中文会糊，所以读数一律看 $n.audit.txt（几何结论）与 $n.txt（节点清单）。
set -uo pipefail
export MSYS_NO_PATHCONV=1   # 不设的话 Git Bash 会把 /sdcard/ui.xml 改写成 Windows 路径，pull 静默失败
export PYTHONIOENCODING=utf-8  # 重定向到 .audit.txt 时按 UTF-8 写，否则中文落盘成 GBK 读不回来

# adb 不在这台机器的 PATH 里（台账登记过的事实），所以自己找；找不到就停下说实话，
# 不许让后面的 dump/pull 静默失败产出一张空屏当成"设备已验"。
ADB="${BUSPOST_ADB:-$(command -v adb 2>/dev/null || true)}"
if [ -z "$ADB" ]; then
  for c in /d/Android/Sdk/platform-tools/adb.exe \
           /c/temp/android-sdk/platform-tools/adb.exe \
           "$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe"; do
    [ -x "$c" ] && ADB="$c" && break
  done
fi
[ -n "$ADB" ] || { echo "NO_ADB：PATH、BUSPOST_ADB 与三个已知 SDK 目录都没找到 adb.exe，本脚本无法取证"; exit 6; }

# 多设备时 adb 会直接报错，所以序列号必须显式：给了就用，没给且只有一台才自动选。
if [ -n "${BUSPOST_SERIAL:-}" ]; then
  DEV=(-s "$BUSPOST_SERIAL")
else
  n=$("$ADB" devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
  [ "$n" = 1 ] || { echo "DEVICE_AMBIGUOUS：在线设备 $n 台，请给 BUSPOST_SERIAL"; exit 7; }
  DEV=()
fi
dsh() { "$ADB" ${DEV[@]+"${DEV[@]}"} shell "$@"; }

n="$1"
# 默认落 docs/device-evidence/<时间戳>/：取证必须进仓可追溯，不许散在临时目录
d="${2:-docs/device-evidence/$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$d"

# 几何口径必须**现量**，不许写死：48dp 的像素下限随密度变。
# 旧版把 AVD 的 1080x2400 / 420dpi(126px) 写死在命令行里，换到 2136x3200@440dpi 的小米平板上，
# "热区不足"按 126px 判、真下限是 132px ⇒ 25~32dp 的破口会被静默放过（本轮实测到这条差异）。
SZ=$(dsh 'wm size' | tr -d '\r' | awk -F': ' '/Physical size|Override size/{print $2}' | tail -1)
DP=$(dsh 'wm density' | tr -d '\r' | awk -F': ' '/Physical density|Override density/{print $2}' | tail -1)
[ -n "$SZ" ] || { echo "NO_SCREEN_SIZE：wm size 读不到，几何判据不采信"; exit 8; }
[ -n "$DP" ] || { echo "NO_DENSITY：wm density 读不到，48dp 下限算不出来"; exit 9; }
MIN_TOUCH=$(python -c "print(int(48*${DP}/160+0.5))")
echo "取证设备 screen=$SZ density=${DP}dpi ⇒ 48dp=${MIN_TOUCH}px"

"$ADB" ${DEV[@]+"${DEV[@]}"} shell "uiautomator dump /sdcard/ui.xml" >/dev/null 2>&1 \
  || { sleep 1; "$ADB" ${DEV[@]+"${DEV[@]}"} shell "uiautomator dump /sdcard/ui.xml" >/dev/null 2>&1 || true; }
# ⚠ 两个坑都是本轮实测到的，不是防御性冗余：
#   ① Git Bash 会把**单独成词**的 /sdcard/ui.xml 改写成 C:/Files/Git/sdcard/ui.xml，
#      于是 dump 落在宿主机、pull 拿到空文件 ⇒ 屏幕命令必须整条加引号传给 shell；
#   ② 反过来，本地目标路径必须给 adb 一个 Windows 形状的路径（cygpath -w），
#      否则报 "cannot create file/directory '/c/temp/…'"。
"$ADB" ${DEV[@]+"${DEV[@]}"} pull /sdcard/ui.xml "$(cygpath -w "$d/$n.xml")" >/dev/null 2>&1
[ -s "$d/$n.xml" ] || { echo "NO_XML_DUMP：$d/$n.xml 是空的或没拉下来（动画中的屏常给不出节点）——本屏取证不成立，不许只拿 PNG 交差"; exit 10; }
"$ADB" ${DEV[@]+"${DEV[@]}"} exec-out screencap -p > "$d/$n.png"
# ⚠ 给 Windows python 的三条路径必须是 Windows 形状：Git Bash 的 /c/… 或 /tmp/… 它解析不到
#   （本轮实测：报 FileNotFoundError，但 PNG 已经拉下来了，很容易当成"这一屏没几何问题"）。
python tools/layout_audit.py "$(cygpath -w "$d/$n.xml")" \
  --screen="$SZ" --min-touch="$MIN_TOUCH" --list="$(cygpath -w "$d/$n.txt")" > "$d/$n.audit.txt" 2>&1
awk '/^TOTAL_ISSUES|^== |LISTING/' "$d/$n.audit.txt"
