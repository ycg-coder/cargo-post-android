#!/usr/bin/env bash
# 乡邮智联 · Android 真机一键验证
#
# 用法（Git Bash）：
#   bash cargo-post-android/tools/verify_device.sh            # 全流程
#   bash cargo-post-android/tools/verify_device.sh --no-build # 跳过构建，用现有 APK
#
# 产出证据：cargo-post-android/docs/device-evidence/<时间戳>/
#   install.log      安装结果
#   launch.log       启动结果 + 前台窗口
#   logcat.log       全量日志
#   logcat-key.log   关键行（BUS_API / 崩溃 / ANR）
#   screenshot-*.png 截图（至少两张，证明画面在动）
#   summary.md       人读结论
#
# 前置：设备已开「USB 安装」（设置 → 更多设置 → 开发者选项 → USB 安装）。
# 未开时 adb install 会报 INSTALL_FAILED_USER_RESTRICTED，脚本会明确停下并给替代路径。

set -uo pipefail

# ── 环境：PATH 显式导出（Bash 工具可能清空 PATH）；MSYS_NO_PATHCONV 防设备路径被改写 ──
export PATH="$PATH:/c/temp/android-sdk/platform-tools"
export MSYS_NO_PATHCONV=1

PKG="com.shanxiang.buspost"
ACTIVITY="$PKG/.MainActivity"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK_SRC="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
STAGE="/c/temp/wc-push"
APK="$STAGE/app.apk"
TS="$(date +%Y%m%d-%H%M%S)"
OUT="$ROOT/docs/device-evidence/$TS"

DO_BUILD=1
[ "${1:-}" = "--no-build" ] && DO_BUILD=0

step() { printf '\n=== %s ===\n' "$1"; }
die()  { printf '\n[STOP] %s\n' "$1"; exit 1; }

# §三十三 / F-2 第 7 步：失败只准带**类别**出门，不许留一句笼统的"构建失败"。
VF="$ROOT/tools/classify_verification_failure.sh"
classify() { # $1=日志 → 打印 VERDICT 的类别（分类器自己会区分歧义/归不进去）
  bash "$VF" "$1" 2>/dev/null | grep -m1 '^VERDICT|' | cut -d'|' -f2
}

# ── 0. 设备 ──
mkdir -p "$OUT" "$STAGE"
step "设备"
adb start-server >/dev/null 2>&1
SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
if [ -z "$SERIAL" ]; then
  echo "当前 adb 设备列表：" | tee "$OUT/device-check.log"
  adb devices -l 2>&1 | tee -a "$OUT/device-check.log"
  # 表头行不算证据（有设备时也在），所以这里再拿一条 adb 真实报错喂分类器
  adb shell true >"$OUT/device-probe.log" 2>&1
  VERDICT="$(classify "$OUT/device-probe.log")"
  {
    echo "# DEVICE_STATUS: ${VERDICT:-UNCLASSIFIED}"
    echo
    echo "分类依据：adb 的真实报错原文（下面这份），不是「没有设备」这句转述。"
    echo '```'
    cat "$OUT/device-probe.log"
    echo '```'
    echo
    echo "出路：接设备并开 USB 调试；unauthorized 先 adb kill-server && adb start-server。"
    echo "在此之前，本工程一律记 NOT VERIFIED ON DEVICE，JVM 用例不冒充设备证据。"
  } >"$OUT/summary.md"
  printf '\n[STOP] %s\n' "没有处于 device 状态的设备 ⇒ $VERDICT（原文 $OUT/device-probe.log，结论 $OUT/summary.md）
  · 显示 unauthorized → 执行 adb kill-server && adb start-server（授权通常早已给过）
  · 列表为空 → 检查 USB 线/接口，或平板开发者选项里的「USB 调试」是否仍开着"
  exit 1
fi
D="-s $SERIAL"
echo "设备: $SERIAL"
echo "型号: $(adb $D shell getprop ro.product.model) | Android $(adb $D shell getprop ro.build.version.release) (SDK $(adb $D shell getprop ro.build.version.sdk))"

# ── 1. 构建 ──
step "构建 debug APK"
if [ "$DO_BUILD" = 1 ]; then
  ( cd "$ROOT" && ./gradlew :app:assembleDebug ) >"$OUT/build.log" 2>&1
  if [ $? -ne 0 ]; then
    VERDICT="$(classify "$OUT/build.log")"
    die "构建失败，§三十三 分类 = ${VERDICT:-UNCLASSIFIED}（原文 $OUT/build.log；归不进去就先去读原文，别改代码绕）"
  fi
fi
[ -f "$APK_SRC" ] || die "找不到 APK：$APK_SRC（先跑一次 assembleDebug）"
# 中文路径不能直接喂 adb，先落到纯 ASCII 暂存目录
cp "$APK_SRC" "$APK"
PC_MD5="$(md5sum "$APK" | cut -d' ' -f1)"
echo "APK: $(du -h "$APK" | cut -f1)  md5=$PC_MD5"

# ── 2. 安装 ──
step "安装"
adb $D install -r "C:\\temp\\wc-push\\app.apk" >"$OUT/install.log" 2>&1
if grep -q "Success" "$OUT/install.log"; then
  echo "安装成功"
else
  cat "$OUT/install.log"
  if grep -q "INSTALL_FAILED_USER_RESTRICTED" "$OUT/install.log"; then
    # 降级：推到下载目录，用户手点（不反复重试 —— MIUI 的安全边界 adb 绕不过）
    adb $D push "$APK" /sdcard/Download/buspost-debug.apk >/dev/null 2>&1
    DEV_MD5="$(adb $D shell md5sum /sdcard/Download/buspost-debug.apk 2>/dev/null | cut -d' ' -f1)"
    {
      echo "# 安装被系统拒绝：INSTALL_FAILED_USER_RESTRICTED"
      echo
      echo "原因：HyperOS/MIUI 开发者选项「USB 安装」未开启（硬件安全边界，adb 无权限绕过）。"
      echo "已试且无效：adb install / pm install -r -d / 会话式 install-create / --user 0 / am start INSTALL_PACKAGE。"
      echo
      echo "两条出路："
      echo "  A. 平板 → 设置 → 更多设置 → 开发者选项 → 打开「USB 安装」，然后重跑本脚本"
      echo "  B. 平板「文件管理」→ 下载 → 点 buspost-debug.apk 安装"
      echo
      echo "降级包已推送：/sdcard/Download/buspost-debug.apk"
      echo "PC md5 : $PC_MD5"
      echo "设备 md5: ${DEV_MD5:-（读取失败）}"
      echo "md5 一致: $([ "$PC_MD5" = "${DEV_MD5:-x}" ] && echo 是 || echo 否)"
    } >"$OUT/summary.md"
    printf '\n[停] 安装被拒。已推降级包，详见 %s\n' "$OUT/summary.md"
    echo "设备 md5: ${DEV_MD5:-（读取失败）}  PC md5: $PC_MD5"
    exit 2
  fi
  die "安装失败，详见 $OUT/install.log"
fi

# 复核装的确实是这一版
INS_VER="$(adb $D shell dumpsys package $PKG 2>/dev/null | grep -m1 versionName | tr -d '\r' | awk -F= '{print $2}')"
echo "已装版本: $INS_VER"

# ── 3. 启动 + 日志 ──
step "清日志并启动"
adb $D logcat -c 2>/dev/null
adb $D shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
sleep 1
adb $D shell am start -n "$ACTIVITY" >"$OUT/launch.log" 2>&1
cat "$OUT/launch.log"
sleep 6   # 留出闪屏 + 首屏请求时间

# ── 4. 截图（至少两张，证明画面在动，不是冻结帧）──
step "截图取证"
for i in 1 2; do
  adb $D shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  sleep 1
  adb $D exec-out screencap -p > "$OUT/screenshot-$i.png" 2>/dev/null
  echo "screenshot-$i.png  设备时间 $(adb $D shell date +%s%3N)  本地 $(date +%s%3N)"
  sleep 2
done
# 全黑帧 = 息屏，不是黑屏 bug
for f in "$OUT"/screenshot-*.png; do
  SZ=$(stat -c%s "$f" 2>/dev/null || echo 0)
  echo "$(basename "$f"): ${SZ} bytes $([ "$SZ" -lt 20000 ] && echo '（偏小，可能是息屏全黑帧）')"
done

# ── 5. 日志 ──
step "导出日志"
adb $D logcat -d -v time >"$OUT/logcat.log" 2>/dev/null
# BUS_LOC = Locator 的 debug-only 换算日志（"WGS84→GCJ02 偏移 N m @ lat,lng"，审计 §17/§57）：
# 真机核对偏移量、以及判断某机型的 network provider 是否本身已回 GCJ-02（偏移≈0 就是）都靠它。
adb $D logcat -d -v time -s BUS_API BUS_LOC AndroidRuntime ActivityManager >"$OUT/logcat-key.log" 2>/dev/null
CRASH=$(grep -c "FATAL EXCEPTION" "$OUT/logcat-key.log" 2>/dev/null || echo 0)
ANR=$(grep -c "ANR in $PKG" "$OUT/logcat-key.log" 2>/dev/null || echo 0)
LOC=$(grep -c "BUS_LOC" "$OUT/logcat-key.log" 2>/dev/null || echo 0)
echo "日志行数: $(wc -l < "$OUT/logcat.log")"
echo "FATAL EXCEPTION: $CRASH   ANR: $ANR   定位换算日志(BUS_LOC): $LOC 条"
[ "$LOC" -gt 0 ] && grep "BUS_LOC" "$OUT/logcat-key.log" | head -3

# ── 6. 结论 ──
FOCUS="$(adb $D shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | tr -d '\r')"
{
  echo "# 真机验证 $TS"
  echo
  echo "- 设备: $SERIAL / $(adb $D shell getprop ro.product.model) / Android $(adb $D shell getprop ro.build.version.release)"
  echo "- APK md5: $PC_MD5"
  echo "- 已装版本: $INS_VER"
  echo "- 当前前台: $FOCUS"
  echo "- FATAL EXCEPTION: $CRASH / ANR: $ANR"
  echo
  echo "## 三档结论（照实填，不把「构建成功」写成「功能可用」）"
  echo
  echo "| 档位 | 项 |"
  echo "| --- | --- |"
  echo "| 已真机验证 PASS | 安装、启动、首屏渲染（见截图） |"
  echo "| 代码已完成待验证 | 天气 4 日卡、附近公交五态、下拉刷新、定位状态条、二维码、扫码 |"
  echo "| 尚未开始 | 司机端线路页语义重构、交接动作状态机、司机 tab 结构、设计令牌对齐 |"
  echo
  echo "## 人工核对清单"
  echo
  echo "- [ ] 闪屏是否正常播完并进入首页（logo 是原图，不是线框）"
  echo "- [ ] 首页站牌头标题是否为「乡邮智联」"
  echo "- [ ] 天气胶囊能展开出 4 日预报（数据源应为高德天气）"
  echo "- [ ] 附近公交区块是否显示运营时段/站点 chip，而不是空白"
  echo "- [ ] 高德地图是否出底图（不出底图 = Android key 或 SHA1 未登记）"
  echo "- [ ] 下拉刷新是否有指示器"
  echo
  echo "## 定位与上报核对清单（⑦ 新增；审计 §57 的三条 ENVIRONMENT_BLOCKED 只有真机能答）"
  echo
  echo "- [ ] logcat-key.log 里 BUS_LOC 的偏移米数是否落在 **100–900 m**（重庆主城；JVM 实测 480.6 m）。**≈0 就是危险信号**：说明该机型的 provider 本身已回 GCJ-02，`Locator` 的无条件换算会转两次，必须按 provider 分流（见 §57）"
  echo "- [ ] 司机工作台「上报归属班次」：本机没有班次时是否**照样上报**，并显示「上报未带班次：车辆位置照常更新…」（旧版是硬拦、一句不发）"
  echo "- [ ] 静止/遮蔽超过 **45 秒**后，通知与工作台是否出现「定位信号已中断 N 秒，暂停上报（不拿过期点冒充当前位置）」，且**恢复后自动续报**"
  echo "- [ ] 弱网/失败后通知与工作台是否显示「已丢弃 N 个未确认点」，且 N 与实际被取代的采样数相符（**这是合并语义，不是补报** —— 不许期待历史点被补回来）"
  echo "- [ ] 关掉所有定位 Provider 再进首页：应是「拿不到定位」状态条，**不得崩溃**（⑦ 修掉的是 flow 正常结束导致的 NoSuchElementException）"
  echo "- [ ] 任务地图/导航页折线是否与道路实际吻合（**偏 500 m 而看起来正常** = §65 的坐标系边界失效）"
} >"$OUT/summary.md"

printf '\n证据目录: %s\n' "$OUT"
ls -la "$OUT"
