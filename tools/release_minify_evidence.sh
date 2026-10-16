#!/usr/bin/env bash
# release 裁剪面的**产物级**取证（缺陷 #78 第三格的另一只手，配 tools/check_startup_probes.sh 的 ST-d/ST-e）
#
# 为什么需要这个脚本而不是只读 `app/proguard-rules.pro`：
# keep 规则写没写是一回事，**R8 跑完之后那些类还在不在包里**是另一回事。
# 判据簿扫的是源码文本，而这个脚本扫的是 APK 里 `classes*.dex` 的字节（类型描述符 `Lcom/…;`），
# 也就是"计数只读产物"那条老规矩在裁剪这一格上的落点。
#
# 今天 release 是 `isMinifyEnabled = false` ⇒ 本脚本读到的必是"全量未裁剪"的那一份，
# 它的作用因此是**基线**：先在不开裁剪的包上确认这几处类名与描述符都扫得到、脚本本身没写错；
# 等谁把开关打开，同一条命令立刻变成"少一条 keep 规则就红"的那道闸。
#
# 用法：
#   bash tools/release_minify_evidence.sh [apk 路径]
#   缺省去找 app/build/outputs/apk/release/app-release*.apk；一个都没有就退 2（不装绿）。
# 退出码：0 = 必留类全在；1 = 有必留类不见了（点名是哪条 keep 规则管的事）；2 = 没有可扫的产物。
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 2

APK="${1:-}"
if [ -z "$APK" ]; then
    APK=$(ls -1t app/build/outputs/apk/release/*-release*.apk 2>/dev/null | head -1)
fi
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    echo "× 没有可扫的 release 产物（先跑 assembleRelease，或把 apk 路径作为参数传进来）"
    exit 2
fi
echo "[产物] $APK  $(wc -c < "$APK") B"
# R8 到底跑没跑：mapping.txt 在场才算跑了（不看构建日志里那一行，日志会滞后）
if [ -f app/build/outputs/mapping/release/mapping.txt ]; then
    echo "[R8] mapping.txt 在场 ⇒ 这个包是裁剪过的，下面的读数就是 keep 规则的真值"
    MINIFIED=1
else
    echo "[R8] 没有 mapping.txt ⇒ 未裁剪（isMinifyEnabled=false 的当前形态），本读数是基线"
    MINIFIED=0
fi
MAP=app/build/outputs/mapping/release/mapping.txt

PY=python3
command -v "$PY" >/dev/null 2>&1 || PY=python
"$PY" - "$APK" "$MAP" "$MINIFIED" <<'PY'
import io, os, re, sys, zipfile
try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

apk, mapping, minified = sys.argv[1], sys.argv[2], sys.argv[3] == "1"

# ── 第一组：类**名字**必须在 dex 里原样存在（谁按名字找它，每条都注明） ──────────
NEED = [
    ("com/amap/api/navi/AMapNavi",
     "本工程用 Class.forName(\"com.amap.api.navi.AMapNavi\") 问导航组件在不在；"
     "R8 裁掉它不会报错，只会把\"有\"读成\"没有\" ⇒ -keep class com.amap.api.** { *; }"),
    ("com/amap/api/maps/AMap",
     "地图主接口，native 侧按名字回调 ⇒ -keep class com.amap.api.** { *; }"),
    ("com/autonavi/base",
     "扫 .so 数出来的 JNI 类描述符命中前缀（ae/base/amap 三族）⇒ -keep class com.autonavi.** { *; }"),
    ("com/autonavi/ae",
     "同上，120 处命中里最大的一族 ⇒ -keep class com.autonavi.** { *; }"),
    ("com/alibaba/idst",
     "语音/导航 TTS 侧的 JNI 入口 ⇒ -keep class com.alibaba.idst.** { *; }"),
    ("com/shanxiang/buspost/notify/NotifyWorker",
     "WorkManager 把 worker 类名写进数据库、之后反射实例化（manifest 规则覆盖不到）"
     "⇒ -keep class com.shanxiang.buspost.notify.NotifyWorker { <init>(...); }"),
    ("com/shanxiang/buspost/BusPostApp",
     "manifest 声明的 Application，由系统按名字实例化（AGP 自动规则负责，这里当反证用）"),
]
PROOF = "com/shanxiang/buspost/MainActivity"

# ── 第二组：枚举**不能**按类名查 ────────────────────────────────────────────
# `-keepclassmembers enum …` 保的是**成员名**，类名本身照样允许改（实测 mapping.txt 里
# `…model.Role -> q8.t0`，而 `DRIVER -> DRIVER` 原样留着）。所以这一族的真值只能在 mapping 里读：
# 盘上存的是 `Role.DRIVER.name` 那个字符串，它变了就再也 valueOf 不回来。
MAPPING = [
    ("com.shanxiang.buspost.data.model.Role", ["DRIVER", "USER"],
     "Prefs.role 从盘上读字符串再 Role.valueOf ⇒ -keepclassmembers enum …model.Role { *; }"),
    ("com.shanxiang.buspost.data.model.NavIcon", ["STRAIGHT", "UTURN_LEFT"],
     "后端给的图标字面量直接落在枚举常量名上 ⇒ -keepclassmembers enum …model.NavIcon { *; }"),
]

z = zipfile.ZipFile(apk)
dexes = [n for n in z.namelist() if re.match(r'^classes[\d]*\.dex$', n)]
if not dexes:
    sys.exit("× APK 里没有 classes*.dex，这个产物不可扫")
blob = b"".join(z.read(n) for n in dexes)
print("[dex] %s 合计 %d B" % (",".join(sorted(dexes)), len(blob)))

missing = []
for desc, why in NEED:
    hit = blob.find(("L" + desc).encode("ascii")) >= 0
    print("%s %s" % ("✔" if hit else "×", desc))
    if not hit:
        missing.append((desc, why))

# 反证：描述符检索机制本身要好使 —— 拿一个必然存在的类当正对照
proof = blob.find(("L" + PROOF).encode("ascii")) >= 0
print("[反证] MainActivity 描述符扫到 = %s" % proof)
if not proof:
    sys.exit("× 反证掉了：连 manifest 主 Activity 都扫不到，说明上面的\"在不在\"没有参考价值")

if not minified:
    print("[mapping] 未裁剪 ⇒ 没有 mapping.txt，第二组（枚举成员名）本轮无从判，跳过")
else:
    # mapping.txt 的形态：类行顶格 `完整类名 -> 混淆名:`，成员行缩进 4 空格，
    # 而 R8 在中间穿插**顶格的 `# {…}` 元数据注释行**（第一版按"缩进行"抓块，
    # 抓到第一条注释就断，于是把明明在场的 DRIVER/USER 读成"被改名"—— 假红比漏红更难查）。
    lines = io.open(mapping, encoding='utf-8', errors='replace').read().split('\n')
    for cls, consts, why in MAPPING:
        head = cls + " -> "
        start = None
        for i, ln in enumerate(lines):
            if ln.startswith(head) and ln.endswith(":") and not ln.startswith(" "):
                start = i
                break
        if start is None:
            print("× %s：mapping.txt 里找不到这个类的条目（被合并了？规则没生效）" % cls)
            missing.append((cls, why))
            continue
        body = []
        for ln in lines[start + 1:]:
            if ln.startswith("# {") or ln.startswith("    "):
                body.append(ln)
            elif ln == "":
                continue
            else:
                break
        text_body = "\n".join(body)
        bad = [c for c in consts
               if not re.search(r'\b%s -> %s$' % (c, c), text_body, re.M)]
        print("%s %s 常量名 %s（块内 %d 行）" % ("✔" if not bad else "×", cls.split('.')[-1],
              "全部原样" if not bad else "被改名：" + ",".join(bad), len(body)))
        if bad:
            missing.append((cls, why))
    # 反证：行号/源文件名也真留下了（否则"诊断进 logcat"这条在 release 包里是空的）
    src = sum(1 for ln in lines if ln.startswith('# {"id":"sourceFile"'))
    print("[反证] mapping 里 sourceFile 注释条数 = %d（>0 才说明 -keepattributes SourceFile 生效）" % src)
    if src == 0:
        missing.append(("sourceFile", "-keepattributes SourceFile,LineNumberTable 没生效"))

for desc, why in missing:
    print("× 必留项不见了：%s\n  该由这条规则保：\n    %s" % (desc, why))
if missing:
    sys.exit(1)
print("release 必留项全部在场（dex %d 项 + mapping %d 项 + 2 项反证）"
      % (len(NEED), len(MAPPING) if minified else 0))
PY
rc=$?
if [ "$rc" = 0 ] && [ "$MINIFIED" = 0 ]; then
    echo "[提示] 现在是未裁剪基线。开 isMinifyEnabled=true 之后必须重跑本脚本，"
    echo "       并且把包真机装上走一遍核心屏 —— dex 里「在」不等于运行时「能用」。"
fi
exit "$rc"
