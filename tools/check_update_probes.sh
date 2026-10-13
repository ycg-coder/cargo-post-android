#!/usr/bin/env bash
# #48 更新通路（GitHub Release → App 内检查 → 下载自装）的结构判据
# 配 tools/update_negative_controls.sh 的行为/破口负控。
# 用法：bash tools/check_update_probes.sh        退出码非 0 = 这条通路被拆了或写歪了
# 写法：一律按内容/整串字面量过滤，不按行号（行号会漂）；每个"目标 0"都配一条同机制反证。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
MF=app/src/main/AndroidManifest.xml
FP=app/src/main/res/xml/file_paths.xml
UP=$M/core/update/AppUpdate.kt
UR=$M/core/update/AppUpdater.kt
US=$M/ui/common/UpdateSection.kt
MP=$M/ui/user/MinePages.kt
WF=.github/workflows/build-and-release.yml
GR=app/build.gradle.kts
CT=app/src/test/java/com/shanxiang/buspost/core/update/AppUpdateContractTest.kt
KT=(--include=*.kt)
NC='(^|[0-9]+:)[[:space:]]*(\*|//)'

for f in "$MF" "$FP" "$UP" "$UR" "$US" "$CT" "$WF"; do
  [ -f "$f" ] || { echo "× 缺 $f：通路不成立，本轮所有读数不采信"; exit 2; }
done

# U-a "安装未知应用"权限恰 1（多一条就是有人又开了别的安装路径），且 FileProvider 声明在位
UA=$(grep -c 'android.permission.REQUEST_INSTALL_PACKAGES' "$MF")
UB=$(grep -c 'androidx.core.content.FileProvider' "$MF")
UB2=$(grep -c 'android:authorities="${applicationId}.fileprovider"' "$MF")

# U-b file_paths 只开放 cache/apk 一个目录：多一处就是把整个缓存或外部存储挂给安装器
UC=$(grep -c '<cache-path' "$FP")
UC2=$(grep -cE '<(external-path|files-path|root-path)' "$FP")

# U-c 代码侧 authority 必须与 manifest 完全同一串（不一致就是 FileProvider 找不到路径 → 装包时崩）
UD=$(grep -c 'APPLICATION_ID}.fileprovider' "$UR")

# U-d 四个结论分支都在场（缺任何一个，就会被并成"已是最新"那种假话）
# 注释行必须滤掉（负控 U-5 实测：把 `object NoReleaseYet : UpdateOutcome()` 整行注释掉，
#   不带过滤的 `grep -cE` 照样数到 4 ⇒ "仓库还没发布过"这一格被并进"已是最新"而判据不响。
#   与天气那本 W-d 是同一个坑，本轮又踩第二次 ⇒ 凡是"数分支在场"的判据一律带这个过滤。
UE=$(grep -nE 'object NoReleaseYet|data class UpToDate|data class Available|data class Failed' "$UP" | grep -vE '^[0-9]+: *(//|\*)' | wc -l | tr -d ' ')

# U-e 界面接线：行组件被真的调用一次（定义 + 调用各 1），没调用就是"机制接了没上岗"
UF=$(grep -rn 'UpdateCheckRow()' "$M" --include=*.kt | grep -vE "$NC" | wc -l | tr -d ' ')
UF2=$(grep -c 'fun UpdateCheckRow()' "$US")

# U-f 不许走 file:// 直传（API 24+ 直接 FileUriExposedException），必须带只读授权
UG=$(grep -rn 'Uri.fromFile' "$M" --include=*.kt | wc -l | tr -d ' ')
UG2=$(grep -c 'FLAG_GRANT_READ_URI_PERMISSION' "$UR")

# U-g 不许把仓库坐标写死在源码里（只能在 build.gradle 的默认值 / local.properties / -P）
UH=$(grep -rn 'ycg-coder/cargo-post-android' "$M" --include=*.kt | wc -l | tr -d ' ')

# U-h CI：创建 Release + 写权限 + 发布后回读，三样都要在；且 tag 与 versionCode 同一个来源
# 同上：注释掉那一行也必须红（U-10 实测第一版仍绿）。
UI_=$(grep -n 'gh release create' "$WF" | grep -vE '^[0-9]+: *#' | wc -l | tr -d ' ')
UJ=$(grep -c 'contents: write' "$WF")
# ⚠ 这一条第一版我写成 `grep -c 'releases/latest'` 并猜目标 1，实测 2 —— 第二处是**注释里引用**了同一个词
#   （"releases/latest 回的不是刚发的那一版"）。计数型判据一旦把解释文字数进去就再也判不动，
#   所以改成只数真正的 API 调用行：这一行没了，回读取证整步就退化成"发出去了但没人回头看"。
UK=$(grep -c 'gh api "repos/\${GITHUB_REPOSITORY}/releases/latest"' "$WF")
UL=$(grep -c 'v${GITHUB_RUN_NUMBER}' "$WF")
UM=$(grep -c 'environmentVariable("GITHUB_RUN_NUMBER")' "$GR")  # 只数真正的取数点：注释里也提到这个词（实测 raw 计 2）

# U-i 用例数（读源码里的 @Test，与产物互证由收工全量负责）
UN=$(grep -cE '^[[:space:]]*@Test' "$CT")

echo "U-a 安装权限 / FileProvider / authorities      = $UA/$UB/$UB2 （目标 1/1/1）"
echo "U-b file_paths 开放目录 / 越界目录            = $UC/$UC2 （目标 1/0）"
echo "U-c 代码侧 authority 字面量                    = $UD （目标 1）"
echo "U-d 四种结论分支                              = $UE （目标 4：少一种就会被并成一句假话）"
echo "U-e 界面调用点 / 定义                          = $UF/$UF2 （目标 2/1）"
echo "U-f Uri.fromFile 越界 / 只读授权               = $UG/$UG2 （目标 0/1）"
echo "U-g 源码里写死仓库坐标                         = $UH （目标 0）"
echo "U-h CI 创建 / 写权限 / 回读 / tag 同来源        = $UI_/$UJ/$UK/$UL （目标 1/1/1/≥2）"
echo "U-i gradle 取 RUN_NUMBER / 用例数              = $UM/$UN （目标 1/≥11）"

fail=0
[ "$UA" = 1 ]  || { echo "× U-a 安装权限不是恰好 1（现 $UA）"; fail=1; }
[ "$UB" = 1 ]  || { echo "× U-b FileProvider 声明没了：安装包 URI 无法授权给系统安装器"; fail=1; }
[ "$UB2" = 1 ] || { echo "× U-b FileProvider authorities 不是 \${applicationId}.fileprovider：装包时会 IllegalArgumentException"; fail=1; }
[ "$UC" = 1 ]  || { echo "× U-c file_paths 开放目录数不是 1（现 $UC）"; fail=1; }
[ "$UC2" = 0 ] || { echo "× U-c file_paths 出现了 external/files/root-path：授权面从'一个 APK'扩大到'一片存储'"; fail=1; }
[ "$UD" = 1 ]  || { echo "× U-d 代码与清单的 authority 不再是同一串（现 $UD）"; fail=1; }
[ "$UE" = 4 ]  || { echo "× U-e 结论分支少了（现 $UE/4）：少的那一格会被并进'已是最新'或并进'失败'"; fail=1; }
[ "$UF" = 2 ]  || { echo "× U-f UpdateCheckRow 调用点不是 2（定义 1 + 关于页 1，现 $UF）：只定义不接线 = 机制没上岗"; fail=1; }
[ "$UF2" = 1 ] || { echo "× U-f 行组件定义不唯一（现 $UF2）"; fail=1; }
[ "$UG" = 0 ]  || { echo "× U-g 有人用 Uri.fromFile 直传安装包（现 $UG）：API 24+ 当场崩"; fail=1; }
[ "$UG2" = 1 ] || { echo "× U-h 安装 intent 不再带只读授权（现 $UG2）：系统安装器读不到那个 URI"; fail=1; }
[ "$UH" = 0 ]  || { echo "× U-i 仓库坐标被写死进源码（现 $UH）：fork/改名后这一格会一直查错仓库"; fail=1; }
[ "$UI_" = 1 ] || { echo "× U-j CI 不再创建 GitHub Release（现 $UI_）：push ≠ 用户能更新，#48 回到原点"; fail=1; }
[ "$UJ" = 1 ]  || { echo "× U-k 写权限没只给 release job（现 $UJ）：要么发不出去，要么顶层被整体放大"; fail=1; }
[ "$UK" = 1 ]  || { echo "× U-l 发布后回读那一次 API 调用不是恰好 1（现 $UK）：'发出去了'与'App 查得到'是两件事"; fail=1; }
[ "$UL" -ge 2 ] || { echo "× U-m tag 与 versionCode 的来源分叉（workflow 里 RUN_NUMBER 只出现 $UL 次）"; fail=1; }
[ "$UM" = 1 ]  || { echo "× U-n gradle 里 GITHUB_RUN_NUMBER 不是恰好 1 处（现 $UM）：versionCode 口径被动过"; fail=1; }
[ "$UN" -ge 11 ] || { echo "× U-o 判定层用例少于 11 条（现 $UN）：四个分支的区分度没人钉"; fail=1; }

if [ "$fail" = 0 ]; then echo "更新通路判据通过"; else echo "更新通路判据失败"; fi
exit "$fail"
