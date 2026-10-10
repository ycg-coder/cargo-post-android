#!/usr/bin/env bash
# §六/§七/§三十一 判据（tools/check_throwable_probes.sh，TH-a…TH-l）的**负控制**：
# 逐条破口 → 确认判据 exit 1 且报的就是这一条。只在临时目录副本上动手，工程真身不受影响
# （纯文本判据，不需要编译 ⇒ 可与其他脚本并行）。
# 用法：bash tools/throwable_negative_controls.sh
#
# 为什么这一簿比别家长：TH-e 的判据是 python 扫全树算出来的，"扫到 0 处"和"扫对了 0 处"输出一样，
# 所以这里既要有"真破口必须变红"，也要有"扫描面/豁免面一动就红"的反证（SHAPED 下限、SKIPPED 恰好 1）。
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码（或在改过的树上做沙盒快照），两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/th_neg.$$
CLEAN=/tmp/th_neg_clean.$$
PKG=com/shanxiang/buspost
PR="$SRC/tools/check_throwable_probes.sh"
mkdir -p "$SC/app/src/main" "$SC/app/src/test"
cp -r "$SRC/app/src/main/java" "$SC/app/src/main/java"
cp -r "$SRC/app/src/test/java" "$SC/app/src/test/java"
# TH-k 的 release 字面量在构建脚本里：不复制过来，注入就改不到（更糟：改的是真身）
cp "$SRC/app/build.gradle.kts" "$SC/app/build.gradle.kts"
[ -f "$SC/app/src/main/java/$PKG/core/net/Attempt.kt" ] || { echo "✘ 副本没建起来（$SC），本轮负控全部作废"; exit 2; }

A="$SC/app/src/main/java/$PKG"
API="$A/core/net/Api.kt"
ATT="$A/core/net/Attempt.kt"
REPO="$A/data/repo/Repo.kt"
SVM="$A/ui/common/ScreenViewModel.kt"
LV="$A/ui/login/LoginViewModel.kt"
DLS="$A/driver/DriverLocationService.kt"
NW="$A/notify/NotifyWorker.kt"
APP="$A/BusPostApp.kt"
NAV="$A/ui/driver/DriverSdkNavCard.kt"
LOC="$A/core/loc/Locator.kt"
BG="$SC/app/build.gradle.kts"
TD="$SC/app/src/test/java/$PKG/core/net/DemoExceptionIsolationTest.kt"
TCF="$SC/app/src/test/java/$PKG/core/net/ApiCancellationTest.kt"

for f in "$API" "$ATT" "$REPO" "$SVM" "$LV" "$DLS" "$NW" "$APP" "$NAV" "$LOC" "$BG" "$TD" "$TCF"; do
    [ -f "$f" ] || { echo "✘ 负控文件缺失：$f（副本没复制全，下面每一条都会假绿）"; exit 2; }
done
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT
cp -r "$SC" "$CLEAN"
[ -f "$CLEAN/app/build.gradle.kts" ] || { echo "✘ 干净副本没建成，本轮负控全部作废"; exit 2; }

ctl() { # $1=编号 $2=说明
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" = 1 ] && echo "$out" | grep -q "^× $1"; then
    echo "✔ $1  $2 → exit 1：$(echo "$out" | grep "^× $1" | head -1)"
  elif [ "$code" = 1 ]; then
    echo "△ $1  $2 → exit 1，但抓到的是别的判据：$(echo "$out" | grep '^×' | head -1)"
  else
    echo "✘ $1  $2 → 负控制失效（exit $code），这条判据等于没判据"
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# ⚠ 注入后的文本**不得再包含锚点原文**：把 `if (!demoAllowed) throw e` 换成
# `// NEG-CONTROL 摘掉：if (!demoAllowed) throw e` 会让 TH-b 数到注释里的字符串，破口看起来还在 ⇒ 假绿。
swap() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swap 锚点没命中，破口根本没注入（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
assert old not in new, "SELF-MATCH: 替换文本里还留着锚点，判据会读到注释"
open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
PY
}
swap_all() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swap_all 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
open(p, 'w', encoding='utf-8').write(s.replace(old, new))
PY
}
kill_test() { python - "$1" <<'PY' || { echo "✘ kill_test 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p = sys.argv[1]; s = open(p, encoding='utf-8').read()
i = s.find('\n    @Test')
assert i >= 0, "ANCHOR-MISS: @Test"
open(p, 'w', encoding='utf-8').write(s[:i] + '\n    //@Test' + s[i + 10:])
PY
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | tail -1)"
# 基线不绿就立刻收工：否则每条都会因为"文件本来就不在"打出 ✔，整轮负控是假的
[ "$base_code" = 0 ] || { echo "✘ 基线未通过，负控结果一律不采信"; exit 2; }

# ── TH-a 行为底盘 ────────────────────────────────────────────────────────────
kill_test "$TD";  ctl TH-a "演示隔离用例被注释掉一条（7→6）"
kill_test "$TCF"; ctl TH-a "取消用例被注释掉一条（5→4）"

# ── TH-b 回落面与开关闸一一对应 ──────────────────────────────────────────────
swap "$API" '            if (!demoAllowed) throw e' '            // NEG-CONTROL：这道正式包闸门被摘掉了'
ctl TH-b "execute 的 demoAllowed 闸被摘：正式包也能回落演示数据"

# ── TH-c / TH-l 兜底出口与回落形状 ───────────────────────────────────────────
swap "$API" '        } catch (e: IOException) {' '        } catch (e: Throwable) {'
ctl TH-c "Api 的原始 IO 出口退回 catch(Throwable)：程序错误会被静默"

swap "$API" '        } catch (e: NetworkException) {' '        } catch (e: RuntimeException) {'
ctl TH-l "execute 的传输失败出口换成 RuntimeException：回落面不再是钉住的那两类"

# ── TH-d Repo 配对 ──────────────────────────────────────────────────────────
swap "$REPO" '    } catch (e: Exception) {
        One(null, error = errorTextOf(e))' '    } catch (e: RuntimeException) {
        One(null, error = errorTextOf(e))'
ctl TH-d "Repo 一处 Exception 出口改成窄类型：与取消出口不再配对（38/37）"

# ── TH-e 三件事：吞取消、扫描面缩水、豁免面变大 ──────────────────────────────
swap "$REPO" '    } catch (e: CancellationException) {
        throw e' '    } catch (e: CancellationException) {
        // NEG-CONTROL：这一处不再穿透，取消被收成一次普通失败'
ctl TH-e "摘掉一处 throw e（子句字面量还在，TH-d 数得照样对）：只有逐点配对该红"

swap "$REPO" '    } catch (e: Exception) {
        One(null, error = errorTextOf(e))' '    // NEG-CONTROL：整处 Exception 出口被删掉
    } catch (e: RuntimeException) {
        One(null, error = errorTextOf(e))'
ctl TH-e "删掉一处挂起形状的 Exception 出口：扫描面 48→47 要被下限抓住"

swap "$NAV" '    "已唤起外部地图"' '    Repo.negControlProbe()'
ctl TH-e "给唯一免检点塞进挂起调用：豁免面 1→0 必须当场重审（顺带它自己也没配对）"

# ── TH-f attempt：helper 自己先穿透 / 调用点数 ───────────────────────────────
swap "$ATT" '} catch (e: CancellationException) {' '} catch (e: TimeoutCancellationException) {'
ctl TH-f "attempt 只穿透超时取消：其余取消被收进 Result，helper 退化成 runCatching"

swap "$APP" 'attempt { AlertEngine.check(this@BusPostApp) }' 'runCatching { AlertEngine.check(this@BusPostApp) }'
ctl TH-f "前台轮询退回 runCatching：attempt 调用点 11→10（F-2 ⑨ 迁了 4 处取消可达作用域后的实测基线，判据下限同为 11）"

# ── TH-g 挂起形状 runCatching 残留 ──────────────────────────────────────────
swap "$LOC" 'attempt { locations(context).first() }' 'runCatching { locations(context).first() }'
ctl TH-g "定位取点退回 runCatching：点名形状 list 里必须抓到"

# ── TH-h 缺陷话术 ───────────────────────────────────────────────────────────
swap_all "$LV" '客户端缺陷' '加载失败'
ctl TH-h "登录页把缺陷说成普通失败：对着 bug 让用户重试"

swap "$DLS" '"客户端缺陷：' '"上报失败：'
ctl TH-h "位置上报不再区分缺陷与弱网：对着 bug 退避重试"

# ── TH-i work() 的取消出口（缩进与全名都钉死了）──────────────────────────────
swap "$SVM" '            } catch (e: kotlinx.coroutines.CancellationException) {' '            } catch (e: CancellationException) {'
ctl TH-i "work() 的子句改成短名（逐点配对仍算穿透）：只有整行字面量能发现它不再是那条锚点"

# ── TH-j Worker 的 doWork ───────────────────────────────────────────────────
swap "$NW" 'attempt { AlertEngine.check(applicationContext) }' 'runCatching { AlertEngine.check(applicationContext) }'
ctl TH-j "Worker 又吞取消：被停止的一轮会谎报「这轮跑过了」"

# ── TH-k 演示开关接线 ───────────────────────────────────────────────────────
swap "$API" 'NetTestOverride.demoAllowed ?: BuildConfig.DEMO_ALLOWED' 'BuildConfig.DEMO_ALLOWED'
ctl TH-k "测试缝被摘：JVM 用例再也拨不动演示开关（判据与用例的接线断在读取点）"

swap "$BG" 'buildConfigField("boolean", "DEMO_ALLOWED", "false")' 'buildConfigField("boolean", "DEMO_ALLOWED", "true")'
ctl TH-k "release 的 DEMO_ALLOWED 变 true：正式包带演示回落出厂"

echo "── 附：基线复跑（确认每轮恢复都干净）──"
BASE2=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code2=$?
if [ "$code2" = 0 ]; then echo "✔ 恢复后基线仍为绿：$(echo "$BASE2" | tail -1)"; else
    echo "✘ 恢复后基线红：$(echo "$BASE2" | grep '^×' | head -3 | tr '\n' ' ')（说明某轮恢复没还原，前面的 ✔ 全部要重看）"; exit 1; fi
