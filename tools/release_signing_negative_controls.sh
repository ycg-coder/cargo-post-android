#!/usr/bin/env bash
# `tools/release_artifact_signed_gate.sh` 的配对反证（队列 #90 / 台账 D-139）。
#
# 每一把都指定"必须红在哪一格"，并且**都要用同一个判据跑**（不重写一遍逻辑，重写的那份会先漂）。
# 正对照用真产物：没有真产物就明读 "跳过"并让整本 rc=2 —— 一台从没验过真包的判据不许念成"全绿"。
#
# 用法：bash tools/release_signing_negative_controls.sh   （在工程根跑）
set -u
cd "$(dirname "$0")/.." || exit 2

G=tools/release_artifact_signed_gate.sh
DIR=app/build/outputs/apk/release
APK="$DIR/app-release.apk"
PIN=32d02b9700ffb2a244f363e28e507009514db8afedfe84a38834b0130045b39f
BADPIN=0000000000000000000000000000000000000000000000000000000000000000

AS="${APKSIGNER:-}"
if [ -z "$AS" ]; then
    for c in "${ANDROID_HOME:-}"/build-tools/*/apksigner "${ANDROID_HOME:-}"/build-tools/*/apksigner.bat \
              "${ANDROID_SDK_ROOT:-}"/build-tools/*/apksigner "${ANDROID_SDK_ROOT:-}"/build-tools/*/apksigner.bat; do
        [ -x "$c" ] && AS="$c"
    done
fi
if [ -z "$AS" ] && command -v apksigner >/dev/null 2>&1; then AS="$(command -v apksigner)"; fi
if [ -z "$AS" ]; then
    # 与判据同一套兜底（三个默认安装位置）；**不读 local.properties 的 sdk.dir**，那里是 Java properties 转义串。
    for sd in "$HOME/AppData/Local/Android/Sdk" "$HOME/Library/Android/sdk" "/usr/local/lib/android/sdk"; do
        [ -d "$sd/build-tools" ] || continue
        for c in "$sd"/build-tools/*/apksigner "$sd"/build-tools/*/apksigner.bat; do
            # 可执行位优先；**但 Windows SDK 只发 .bat，而 Git Bash 给它不算 +x**（本机实测：
            # build-tools/*/apksigner.bat 全部 -x=no，而按路径直接调用是能的）。
            # 只认 -x 的话，本机永远"找不到 apksigner"⇒ 这本判据在开发机上恒 rc=2，
            # 而 rc=2 长得太像"没跑起来所以不算红"，正是最容易被放过去的一种坏。
            if [ -x "$c" ]; then AS="$c"; elif [ -f "$c" ]; then AS="${AS:-$c}"; fi
        done
    done
fi
if [ -z "$AS" ]; then
    echo "× 找不到 apksigner ⇒ 这本反证一次都判不动（rc=2 是故意的：判据说不动 ≠ 全绿）"
    exit 2
fi
export APKSIGNER="$AS"

if [ ! -f "$APK" ]; then
    echo "× 没有真产物 $APK —— 先 ./gradlew :app:assembleRelease（本地要签名材料齐备）再来跑这本"
    exit 2
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
pass=0; fail=0

run_case() { # $1=期望 rc $2=说明 $3...=命令
    local want="$1" desc="$2"; shift 2
    local out code
    out="$("$@" 2>&1)"; code=$?
    if [ "$code" = "$want" ]; then
        echo "✔ $desc → rc=$code（与预期一致）"
        pass=$((pass + 1))
    else
        echo "✘ $desc → 期望 rc=$want，实跑 rc=$code"
        echo "    读数：$(printf '%s' "$out" | head -1)"
        fail=$((fail + 1))
    fi
}

# ── K1 正对照：真产物 + 在册指纹 ⇒ 必须绿 ────────────────────────────────────
run_case 0 "K1 真产物配在册指纹（这一把不绿，后面每一把的红都可能是'判据本身坏了'）" \
    bash "$G" "$DIR" "$PIN"

# ── K2 指纹被换 ⇒ 必须红在"不等于在册值"那一格 ──────────────────────────────
run_case 1 "K2 证书与在册指纹不符（换 key = 老用户必须卸载重装，App 内自更新只会静默失败）" \
    bash "$G" "$DIR" "$BADPIN"

# ── K3 目录里两只 apk ⇒ 必须红在"恰好一只" ──────────────────────────────────
mkdir -p "$TMP/two"
cp "$APK" "$TMP/two/app-release.apk"; cp "$APK" "$TMP/two/second.apk"
run_case 1 "K3 产物目录两只 .apk（上传那一步是 ls *.apk | head -1，它分不清挑的是哪只）" \
    bash "$G" "$TMP/two" "$PIN"

# ── K4 未签名 ⇒ 必须红（apksigner 验不过）───────────────────────────────────
mkdir -p "$TMP/unsigned"
python3 - "$TMP/unsigned/app-release.apk" <<'PY'
import sys, zipfile
# 一只"长得像 APK 但没签名块"的 fixture：判据要的是 apksigner 这一步红，不是 zip 结构红
with zipfile.ZipFile(sys.argv[1], "w") as z:
    z.writestr("AndroidManifest.xml", "not-signed")
    z.writestr("classes.dex", "x")
PY
run_case 1 "K4 未签名产物（gradle 在缺签名材料时只 warn 一句照样出包，这一格就是接住它的）" \
    bash "$G" "$TMP/unsigned" "$PIN"

# ── K5 名字不对 ⇒ 必须红在"那一只不叫 app-release.apk" ───────────────────────
mkdir -p "$TMP/rename"
cp "$APK" "$TMP/rename/app-v9-release.apk"
run_case 1 "K5 产物被改名（App 侧 pickApk 按固定名挑，名字变了用户那一格就挑不到）" \
    bash "$G" "$TMP/rename" "$PIN"

# ── K6 判据没跑起来 ⇒ rc=2，不许读成通过 ────────────────────────────────────
run_case 2 "K6 release 目录不存在（数到 0 只包不是'没有未签名的包'）" \
    bash "$G" "$TMP/nope" "$PIN"
# ⚠ 这一格要把**三个来源全关掉**才算"判据没跑起来"：显式 APKSIGNER、ANDROID_HOME/ANDROID_SDK_ROOT、
#   以及三个默认安装位置（后者挂在 $HOME 上，所以 HOME 也得指到一个空目录）。
#   少关一个，这一把就会绿 —— 反证要钉的是"工具不在场时判据必须拒绝表态"，不是"路径恰好没导"。
mkdir -p "$TMP/empty_home"
run_case 2 "K7 找不到 apksigner（判据空转必须 rc=2，不能被念成绿）" \
    env -u APKSIGNER ANDROID_HOME= ANDROID_SDK_ROOT= HOME="$TMP/empty_home" PATH=/usr/bin:/bin \
    bash "$PWD/tools/release_artifact_signed_gate.sh" "$PWD/$DIR" "$PIN"

echo "[读数] 通过 $pass 把 / 失效 $fail 把"
[ "$fail" = 0 ] || { echo "❌ 有 $fail 把反证没红在该管的一格"; exit 1; }
echo "✅ $pass 把全部红/绿在各自该在的一格"
