#!/usr/bin/env bash
# 跑全量单元测试（Windows / Git Bash）
#
# 为什么需要这个脚本：本机用户名与工程路径都含中文（C:\Users\袁\...\Desktop\测试\...）。
# Gradle 把测试 worker 的 classpath 写进 @argfile（UTF-8），而 java 启动器按 GBK 读，
# 中文路径整条失效 → 大量 NoClassDefFoundError（kotlin/*、okhttp3/*）。这不是断言失败，
# 是环境假失败，会让人误判"测试挂了"。
#
# 三处关键点，缺一个就复现假失败：
#   1. GRADLE_USER_HOME 指向 subst 出来的 ASCII 盘符（Y:\.gradle）
#   2. -Dmaven.repo.local 指向 ASCII 镜像（Y:\.m2\repository）
#      —— mavenLocal() 供的 okhttp / kotlin-stdlib 本来落在 C:\Users\袁\.m2，必须改掉
#   3. 工程根也从 ASCII 盘符进（Z:\cargo-post-android）
#
# 前置（管理员 PowerShell 执行一次，开机后需重做）：
#   subst Y: C:\Users\袁
#   subst Z: C:\Users\袁\Desktop\测试
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Microsoft/jdk-21.0.12.8-hotspot}"
export GRADLE_USER_HOME="Y:/.gradle"
REPO_ASCII='Y:\.m2\repository'

cd /z/cargo-post-android

./gradlew :app:testDebugUnitTest \
  -Pbuspost.demo=true \
  -Dmaven.repo.local="$REPO_ASCII" \
  --console=plain

# ---- 结果核对：不要只看 BUILD SUCCESSFUL，要看聚合数字 ----
REPORT_DIR="Z:/cargo-post-android/app/build/test-results/testDebugUnitTest"
python - "$REPORT_DIR" <<'PY'
import re, sys, glob, os, time
d = sys.argv[1]
t = f = e = bad = 0
newest = 0
for p in glob.glob(os.path.join(d, 'TEST-*.xml')):
    newest = max(newest, os.path.getmtime(p))
    x = open(p, encoding='utf-8', errors='replace').read()
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', x)
    if m:
        t += int(m.group(1)); f += int(m.group(3)); e += int(m.group(4))
    if '<failure' in x or '<error' in x:
        bad += 1
print('tests=%d failures=%d errors=%d badfiles=%d  report=%s'
      % (t, f, e, bad, time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(newest))))
# 假失败守卫：全是 NoClassDefFoundError 就说明 classpath 又坏了
if bad:
    raise SystemExit('❌ 有失败用例，见 ' + d)
PY
