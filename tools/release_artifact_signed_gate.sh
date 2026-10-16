#!/usr/bin/env bash
# 发布包签名取证（队列 #90 / 台账 D-139）。
#
# 这一格为什么必须存在：`build-and-release.yml` 今天**没有任何一步**验"发出去的产物是签过名的"。
# 上传与建 Release 那两步挑的都是 `ls app/build/outputs/apk/release/*.apk | head -1` ——
# 目录里有几只挑几只、挑到哪只都不管。而 `app/build.gradle.kts` 在缺签名材料时是**只 warn 一句、
# 照样产出未签名 APK**（本机实测过：配置文件里的 storeFile 写成子目录相对路径，jks 其实与它平级放，
# 旧解析只认 CI 那种写法 ⇒ 本地 release 一直是未签名的）。两头一接，就是"未签名的包被当成发布包发出去"，
# 而下游症状是用户装不上 / App 内自更新静默失败 —— 没有任何一条错误写着"因为没签名"。
#
# 判据三条，缺一条就红（全部 fail closed，"找不到工具"也算红）：
#   ① release 目录里**必须恰好一只** .apk，且名字就是 app-release.apk（App 侧 pickApk 认这个名）；
#   ② apksigner 必须验得过（未签名/签名损坏 ⇒ 红）；
#   ③ 证书 SHA-256 必须等于在册指纹。换 key 不是"再签一次"那么简单：**老用户必须先卸载**才能装上新签名的包，
#      本地数据全丢，而 App 内那条自更新通路只会静默失败。所以这一格钉的是"同一把 key"，不是"有 key"。
#
# 用法：check_release_artifact_signed.sh <release 目录> <在册 SHA-256（40+ 位十六进制）>
# 退出码：0 = 三条全过；1 = 任一条不过（逐条点名）；2 = 判据没跑起来（目录不存在 / 找不到 apksigner）。
set -u

DIR="${1:-}"
PIN="${2:-}"
if [ -z "$DIR" ] || [ -z "$PIN" ]; then
    echo "× 用法：$0 <release 目录> <在册 SHA-256>"; exit 2
fi
if [ ! -d "$DIR" ]; then
    echo "× release 目录不存在：$DIR —— 这一步没验成，不许读成「没有未签名的包」"; exit 2
fi
case "$PIN" in
    *[!0-9a-fA-F]*) echo "× 在册指纹不是十六进制：$PIN"; exit 2;;
esac
if [ "${#PIN}" -lt 40 ]; then
    echo "× 在册指纹只有 ${#PIN} 位（SHA-256 该是 64 位）—— 钉值的形状本身就不对，不许拿去比对"; exit 2
fi

APKSIGNER="${APKSIGNER:-}"
if [ -z "$APKSIGNER" ]; then
    for home in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
        [ -n "$home" ] || continue
        # 两种形状都要认：CI（ubuntu）里是 `apksigner` 这个可执行 shell 脚本，
        # 本机 Git Bash 里只有 `apksigner.bat` 带得上执行位 —— 只认前者的话本地永远"找不到 apksigner"，
        # 而这本判据 rc=2 的意义就是"没跑起来"，不能被平台差异念成"验过了"。
        for c in "$home"/build-tools/*/apksigner "$home"/build-tools/*/apksigner.bat; do
            [ -x "$c" ] && APKSIGNER="$c"
        done
    done
fi
if [ -z "$APKSIGNER" ]; then
    command -v apksigner >/dev/null 2>&1 && APKSIGNER="$(command -v apksigner)"
fi
if [ -z "$APKSIGNER" ]; then
    # 兜底认三个**默认安装位置**（Windows / macOS / GitHub Linux runner）。
    # ⚠ 不去读 local.properties 的 sdk.dir：那是 **Java properties 转义**的字面量
    #   （本机实测存的是 `C\:\\Users\\u8881\\AppData...`，中文被写成 \uXXXX），shell 直接拼出来的是一条不存在的宏路径，
    #   会把"找不到 apksigner"念成"找到了但验不过"—— 那是这本判据最坏的一种坏法。
    for sd in "$HOME/AppData/Local/Android/Sdk" "$HOME/Library/Android/sdk" "/usr/local/lib/android/sdk"; do
        [ -d "$sd/build-tools" ] || continue
        for c in "$sd"/build-tools/*/apksigner "$sd"/build-tools/*/apksigner.bat; do
            # 可执行位优先；**但 Windows SDK 只发 .bat，而 Git Bash 给它不算 +x**（本机实测：
            # build-tools/*/apksigner.bat 全部 -x=no，而按路径直接调用是能的）。
            # 只认 -x 的话，本机永远"找不到 apksigner"⇒ 这本判据在开发机上恒 rc=2，
            # 而 rc=2 长得太像"没跑起来所以不算红"，正是最容易被放过去的一种坏。
            if [ -x "$c" ]; then APKSIGNER="$c"; elif [ -f "$c" ]; then APKSIGNER="${APKSIGNER:-$c}"; fi
        done
    done
fi
if [ -z "$APKSIGNER" ]; then
    echo "× 找不到 apksigner（ANDROID_HOME / ANDROID_SDK_ROOT / PATH 三处都没有）—— 判据没跑起来，rc=2 不是通过"; exit 2
fi

N=$(find "$DIR" -maxdepth 1 -name '*.apk' | wc -l | tr -d ' ')
echo "[现读] $DIR 里的 .apk 只数 = $N（apksigner = $APKSIGNER）"
if [ "$N" != 1 ]; then
    echo "× 恰好一只才算得清「发布的是哪只」：现在 $N 只 —— 上传那一步是 ls *.apk | head -1，它分不清"; exit 1
fi
APK="$DIR/app-release.apk"
if [ ! -f "$APK" ]; then
    echo "× 那一只不叫 app-release.apk（App 侧按这个名挑可安装资源）：$(find "$DIR" -maxdepth 1 -name '*.apk')"; exit 1
fi

OUT="$("$APKSIGNER" verify --print-certs "$APK" 2>&1)"
RC=$?
if [ "$RC" != 0 ]; then
    echo "× apksigner 验签不过（rc=$RC）：这只包**没签名或签名坏了**，不许进任何发布通路"
    echo "$OUT" | sed -n '1,6p'
    exit 1
fi
echo "$OUT" | sed -n 's/^certificate DN: /DN = /p;s/^.*SHA-256 digest: /证书 SHA-256 = /p' | head -4
if ! printf '%s' "$OUT" | grep -qi "SHA-256 digest: $PIN"; then
    echo "× 证书不等于在册指纹 $PIN —— 换 key 意味着老用户必须卸载重装（本地数据全丢），"
    echo "  而 App 内「检查更新」只会静默失败。这不是「再签一次」能糊过去的一格。"; exit 1
fi
SZ=$(wc -c < "$APK" | tr -d ' ')
echo "OK: 产物已签名，且证书 SHA-256 == 在册值（$APK，$SZ 字节）"
exit 0
