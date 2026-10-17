#!/usr/bin/env bash
# 配 tools/check_back_affordance_probes.sh 的负控制（#80：每个整屏路由都要有看得见的返回出口）。
# 破口都落在 /tmp 沙盒副本上，工程真身一字不动。
# 两条本轮立下的规矩写在这里：① 注入一律走 subst()/injected()，锚点不在就 OLD-MISS 当场报错——
# 不许再出现"sed 打到别的文件上空转、把 ✔ 记成判据有效"（D-116 第 ④ 条就是这么抓出来的）；
# ② 每条破口都要证明字节真的变了，否则红了不算它咬的。
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3

SC=/tmp/back_aff_neg.$$
# ⚠ PR 必须指沙盒副本而不是真身：BA-d 那把刀改的就是**判据自己的解析器**，
#   跑真身的话注入永远咬不到（本轮第一版就是这么把 ✔ 写成 ✘ 的）。
PR="$SC/tools/check_back_affordance_probes.sh"
rm -rf "$SC"; mkdir -p "$SC"
cp -r "$SRC/app" "$SC/app"
cp -r "$SRC/tools" "$SC/tools"
cp "$SRC"/*.md "$SC/" 2>/dev/null
[ -f "$SC/tools/check_back_affordance_probes.sh" ] || { echo "✘ 沙盒缺判据簿本身，注入等于没注入"; exit 2; }
CLEAN=/tmp/back_aff_clean.$$; cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT
FAIL=0
OK=0
P="$SC/app/src/main/java/com/shanxiang/buspost"

ctl() { # $1=编号 $2=说明
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" != 0 ] && echo "$out" | grep -q "^× $1"; then
    OK=$((OK + 1)); echo "✔ $1  $2 → exit $code：$(echo "$out" | grep "^× $1" | head -1 | cut -c1-76)"
  elif [ "$code" != 0 ]; then
    echo "△ $1  $2 → exit $code，但抓到的是别的判据：$(echo "$out" | grep '^×' | head -1 | cut -c1-76)"; FAIL=1
  else
    echo "✘ $1  $2 → 负控制失效（exit 0），这条判据等于没判据"; FAIL=1
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
must_green() {
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" = 0 ]; then OK=$((OK + 1)); echo "✔ POS  $1 → exit 0"
  else echo "✘ POS  $1 → 本该绿却红了（exit $code）：$(echo "$out" | grep '^×' | head -2 | cut -c1-60 | tr '\n' ' ')"; FAIL=1; fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
subst() { python - "$1" "$2" "$3" <<'PY'
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s: sys.exit("OLD-MISS: " + old[:60])
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
PY
}
delline() { python - "$1" "$2" <<'PY'
import sys
p, old = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8').read()
if s.count(old) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(old), old[:60]))
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, "", 1))
PY
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | grep '^BA-' | tr '\n' ' ')"
[ "$base_code" = 0 ] || { echo "✘ 基线未通过（exit $base_code），负控结果一律不采信"; echo "$BASE" | grep '^×' | head -3; exit 2; }
echo "✔ 基线：副本原样 exit 0"

# BA-a 凭空多一条整屏路由却没验出口
subst "$P/ui/BusPostRoot.kt" '        composable(Routes.WALLET)' \
'        composable(Routes.BACK_PROBE_FAKE) { WalletScreen() }
        composable(Routes.WALLET)'; ctl BA-a "新增一屏未登记（扫描面被悄悄扩大）"

# BA-b 把交接屏的箭头撤掉：那屏重新变成"只能靠系统返回键"
delline "$P/ui/driver/HandoverScreen.kt" '            onBack = { nav.popBackStack() },
'; ctl BA-b "撤掉交接屏的返回出口"

# BA-c 豁免名单被改名（DRIVER_NAV → DRIVER_NAVX）：名单必须跟着响，不然豁免成了永久通行证。
# 2026-10-17（D-156）换的靶子：原来这条打的是 BUS，而 BUS 自本轮起由悬浮胶囊给出口、已经不在豁免名单里，
# 改名只会让 BA-a 的屏数跟着变 ⇒ 拿 DRIVER_NAV（现在唯一那条豁免，DEBUG-only）当靶子才还在判同一件事。
subst "$P/ui/BusPostRoot.kt" 'composable(Routes.DRIVER_NAV)' 'composable(Routes.DRIVER_NAVX)'; ctl BA-c "豁免名单里的屏被改名"

# BA-d 反证：解析器扫不到任何函数体时，"缺口=0"这种读数必须不采信
subst "$SC/tools/check_back_affordance_probes.sh" 'for base, dirs, files in os.walk(root):' \
'for base, dirs, files in os.walk("/nonexistent-back-probe"):'; ctl BA-d "解析器下岗（空集不许冒充通过）"

# BA-e 单一真源被破：第二份 BusBackArrow 定义 / ui 里手搓箭头
subst "$P/ui/driver/HandoverScreen.kt" 'fun HandoverScreen(' \
'fun BusBackArrow(onBack: () -> Unit, tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White) {}
fun HandoverScreen('; ctl BA-e "又写了一份 BusBackArrow 定义"
injected_arrow() { printf '\n// NEG-CONTROL\nval backProbeRough = androidx.compose.material.icons.Icons.Rounded.ArrowBack\n' >> "$1"; }
injected_arrow "$P/ui/driver/DriverMessagesScreen.kt"; ctl BA-e "ui 里手搓 ArrowBack 绕开 BusIcons.back"

# BA-f 寄货第三步的箭头被撤：出口挂点数掉下来
subst "$P/ui/user/SendScreen.kt" '                    onBack = { nav.popBackStack() },
' ''; ctl BA-f "撤掉寄货某一步的返回出口"

# 正控制：改一句副标题（合法的台账式文案编辑）不该让簿红
subst "$P/ui/driver/HandoverScreen.kt" 'subtitle = "换乘站把货交给下一班车，拍照留证后可追溯责任段"' \
'subtitle = "换乘站把货交给下一班车，拍照留证后可追溯责任段（文案微调）"'; must_green "改副标题不动出口"

echo "----"
echo "返回出口结构负控：通过 $OK / 失败 $( [ "$FAIL" = 0 ] && echo 0 || echo 有失效 )"
[ "$FAIL" = 0 ] || exit 1
echo "✅ back_affordance 负控：七条破口各自红在该管的那一格，正控制也没误伤"
