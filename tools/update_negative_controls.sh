#!/usr/bin/env bash
# #48 更新通路**结构判据**的负控制（配 tools/check_update_probes.sh）
# 用法：bash tools/update_negative_controls.sh      每条破口都要求判据簿红、且红在那一条上
# 为什么这本必须有：U-f / U-g / U-i 是三个"目标 0"（Uri.fromFile、写死仓库坐标、file_paths 越界目录），
# 0 命中必须是"真的没有"，不能是"grep 写错了"。
# ⚠ 会就地临时改源码并在结束时原样恢复；一次只允许跑一个（negctl_lock）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
cd "$ROOT" || exit 2

MF=app/src/main/AndroidManifest.xml
FP=app/src/main/res/xml/file_paths.xml
UP=app/src/main/java/com/shanxiang/buspost/core/update/AppUpdate.kt
UR=app/src/main/java/com/shanxiang/buspost/core/update/AppUpdater.kt
US=app/src/main/java/com/shanxiang/buspost/ui/common/UpdateSection.kt
MP=app/src/main/java/com/shanxiang/buspost/ui/user/MinePages.kt
WF=.github/workflows/build-and-release.yml
GR=app/build.gradle.kts
CT=app/src/test/java/com/shanxiang/buspost/core/update/AppUpdateContractTest.kt
PROBE=tools/check_update_probes.sh
FILES=("$MF" "$FP" "$UP" "$UR" "$US" "$MP" "$WF" "$GR" "$CT")
BAKS=()
for f in "${FILES[@]}"; do
  [ -f "$f" ] || { echo "✘ 缺 $f，本书作废"; exit 2; }
  b="/tmp/upd_neg.$(basename "$f").$$"; cp "$f" "$b"; BAKS+=("$b")
done
restore() { for i in "${!FILES[@]}"; do cp "${BAKS[$i]}" "${FILES[$i]}"; done; }
trap 'restore; for b in "${BAKS[@]}"; do rm -f "$b"; done; negctl_release' EXIT

bash "$PROBE" >/dev/null 2>&1 || { echo "✘ 预检：$PROBE 起跑就不绿，基线不成立，一条破口都不注入"; exit 5; }
echo "[预检] 判据簿起跑绿"
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
  if [ "$rc" = 0 ]; then
    echo "✘ ${id} 判据仍然绿（破口没咬住）"; bad=$((bad + 1))
  elif grep -qF "$want" <<<"$out"; then
    echo "✔ ${id} 红且原因对得上（$want）"; ok=$((ok + 1))
  else
    echo "✘ ${id} 红了但不是那一条（缺 $want）"; bad=$((bad + 1))
  fi
}

# 1 摘掉安装权限 → U-a
check U-1 "× U-a" "$MF" \
  '<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />' ''
# 2 摘掉 FileProvider → U-b
check U-2 "× U-b FileProvider 声明没了" "$MF" 'android:name="androidx.core.content.FileProvider"' 'android:name="androidx.core.content.NotAProvider"'
# 3 把授权面扩大到整个外部存储 → U-c
check U-3 "× U-c file_paths 出现了 external/files/root-path" "$FP" '<cache-path' '<external-path name="all" path="." />
    <cache-path'
# 4 代码里 authority 与清单不一致 → U-d
check U-4 "× U-d 代码与清单的 authority" "$UR" '"${BuildConfig.APPLICATION_ID}.fileprovider"' '"${BuildConfig.APPLICATION_ID}.updatetmp"'
# 5 把"仓库还没发布过"并进"已最新"——#48 最容易被写歪的一格 → U-e
check U-5 "× U-e 结论分支少了" "$UP" '    object NoReleaseYet : UpdateOutcome()' '    // object NoReleaseYet : UpdateOutcome()'
# 6 关于页不再调用那一行（只留定义 = 机制接了没上岗）→ U-f
check U-6 "× U-f UpdateCheckRow 调用点不是 2" "$MP" '                    UpdateCheckRow()' ''
# 7 退回 file:// 直传 → U-g
check U-7 "× U-g 有人用 Uri.fromFile" "$UR" 'FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", apk)' 'Uri.fromFile(apk)'
# 8 安装 intent 不带只读授权 → U-h
check U-8 "× U-h 安装 intent 不再带只读授权" "$UR" 'FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION' 'FLAG_ACTIVITY_NEW_TASK'
# 9 仓库坐标写死进源码 → U-i
check U-9 "× U-i 仓库坐标被写死进源码" "$UR" '${BuildConfig.UPDATE_REPO}/releases/latest' '"ycg-coder/cargo-post-android/releases/latest"'
# 10 CI 不再创建 Release → U-j
check U-10 "× U-j CI 不再创建 GitHub Release" "$WF" 'gh release create "$TAG" app-release.apk' '# gh release create "$TAG" app-release.apk'
# 11 写权限退回 read（发不出去）→ U-k
check U-11 "× U-k 写权限没只给 release job" "$WF" '      contents: write' '      contents: read'
# 12 回读那一次调用没了 → U-l
check U-12 "× U-l 发布后回读那一次 API 调用不是恰好 1" "$WF" 'gh api "repos/${GITHUB_REPOSITORY}/releases/latest" > /tmp/latest.json' 'echo "跳过回读"'
# 13 versionCode 又写死回常量 → U-n
check U-13 "× U-n gradle 里 GITHUB_RUN_NUMBER 不是恰好 1 处" "$GR" 'providers.environmentVariable("GITHUB_RUN_NUMBER")' 'null'
# 14 判定层用例被挂起来（永远绿的那种假绿）→ U-o
check U-14 "× U-o 判定层用例少于 11 条" "$CT" \
  '    @Test
    fun `比较用 versionCode 不是版本名字符串`() {' \
  '    @org.junit.Ignore
    fun `比较用 versionCode 不是版本名字符串`() {'

restore
bash "$PROBE" >/dev/null 2>&1 && echo "[恢复] 判据簿重新通过" || { echo "✘ [恢复] 判据簿仍红"; bad=$((bad + 1)); }
same=1
for i in "${!FILES[@]}"; do
  cmp -s "${BAKS[$i]}" "${FILES[$i]}" || { echo "✘ [恢复] ${FILES[$i]} 与原样不一致"; same=0; }
done
[ "$same" = 1 ] && echo "[恢复] 全部文件与原样一致" || bad=$((bad + 1))

echo "更新通路结构负控：通过 $ok / 失败 $bad"
[ "$bad" = 0 ] && [ "$ok" = 14 ]
