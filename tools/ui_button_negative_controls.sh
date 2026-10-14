#!/usr/bin/env bash
# UI 控件规格判据簿的负控制（配 tools/check_ui_button_probes.sh）。
# 每条破口都落在 /tmp 沙盒副本上，工程真身一字不动。
# 规矩（本仓血写的）：基线不绿整份不采信；注入后要证明字节真的变了，否则"红了也不算它咬的、绿了也不证明它不咬"。
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3

SC=/tmp/ui_btn_neg.$$
PR="$SRC/tools/check_ui_button_probes.sh"
rm -rf "$SC"; mkdir -p "$SC"
cp -r "$SRC/app" "$SC/app"
cp -r "$SRC/tools" "$SC/tools"
cp "$SRC"/*.md "$SC/" 2>/dev/null
[ -f "$SC/tools/check_ui_button_probes.sh" ] || { echo "✘ 沙盒缺判据簿本身，注入等于没注入"; exit 2; }
CLEAN=/tmp/ui_btn_clean.$$; cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT
FAIL=0
PROBE="$SC/app/src/main/java/com/shanxiang/buspost"

ctl() { # $1=编号 $2=说明
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" != 0 ] && echo "$out" | grep -q "^× $1"; then
    echo "✔ $1  $2 → exit $code：$(echo "$out" | grep "^× $1" | head -1 | cut -c1-72)"
  elif [ "$code" != 0 ]; then
    echo "△ $1  $2 → exit $code，但抓到的是别的判据：$(echo "$out" | grep '^×' | head -1 | cut -c1-72)"; FAIL=1
  else
    echo "✘ $1  $2 → 负控制失效（exit 0），这条判据等于没判据"; FAIL=1
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
must_green() { # 正控制：合法的按钮编辑不该让簿红
  local out code
  out=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); code=$?
  if [ "$code" = 0 ]; then echo "✔ POS  $1 → exit 0"
  else echo "✘ POS  $1 → 本该绿却红了（exit $code）：$(echo "$out" | grep '^×' | head -2 | cut -c1-60 | tr '\n' ' ')"; FAIL=1; fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
injected() { # $1=文件 $2=要追加的破口代码；确认字节真的变了
  local f="$PROBE/$1" before after
  [ -d "$PROBE/${f#$PROBE/}" ] || mkdir -p "$(dirname "$f")"
  before=$([ -f "$f" ] && wc -c < "$f" || echo 0)
  printf '\n%s\n' "$2" >> "$f"
  after=$(wc -c < "$f")
  [ "$before" != "$after" ] || { echo "✘ 注入没改动字节（$1），这条不采信"; return 1; }
  return 0
}

BASE=$(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1); base_code=$?
echo "[基线] $(echo "$BASE" | grep '^UB-' | tr '\n' ' ' | cut -c1-150)"
[ "$base_code" = 0 ] || { echo "✘ 基线未通过（exit $base_code），负控结果一律不采信"; echo "$BASE" | grep '^×' | head -5; exit 2; }

# ① 裸 Material 按钮回来（用户点名的"同一行两种规格"的根因）
injected ui/NegctlProbeA.kt 'package com.shanxiang.buspost.ui
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
@Composable fun NegctlA() { TextButton(onClick = {}) { Text("破口") } }' \
  && ctl UB-a "有人绕过设计系统直接写 TextButton ⇒ 同行高度/字重/命中区分叉"

# ② 同一 Row 里一颗 small 一颗不 small
injected ui/NegctlProbeB.kt 'package com.shanxiang.buspost.ui
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
@Composable fun NegctlB() {
    Row {
        BusButton(text = "甲", onClick = {}, small = true, variant = BusButtonVariant.OUTLINE)
        BusButton(text = "乙", onClick = {}, small = false, variant = BusButtonVariant.OUTLINE)
    }
}' \
  && ctl UB-b "同一 Row 内按钮规格不同（正是「一排里一大一小」那种）"

# ③ 调用点自己传圆角，绕开 BusRadius
injected ui/NegctlProbeC.kt 'package com.shanxiang.buspost.ui
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import com.shanxiang.buspost.core.ui.BusButton
@Composable fun NegctlC() { BusButton(text = "丙", onClick = {}, shape = RoundedCornerShape(2.dp)) }' \
  && ctl UB-c "调用点自传 shape ⇒ 圆角不再是令牌说了算"

# ④ 调用点写死命中下限（驾驶模式 56dp 会失效）
injected ui/NegctlProbeD.kt 'package com.shanxiang.buspost.ui
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
@Composable fun NegctlD(m: Modifier = Modifier) { val x = m.defaultMinSize(minHeight = 44.dp) }' \
  && ctl UB-d "调用点写死 minHeight ⇒ BusTouch 的驾驶模式下限被绕过"

# ⑤ 对话框动作出口被复制成两份（同一问题两个答案）
before=$(wc -l < "$PROBE/core/ui/Components.kt")
printf '\n@Composable fun BusDialogAction(text: String, onClick: () -> Unit) { /* 破口：第二份出口 */ }\n' \
  >> "$PROBE/core/ui/Components.kt"
[ "$(wc -l < "$PROBE/core/ui/Components.kt")" != "$before" ] && ctl UB-e "设计系统里长出第二个对话框动作出口"
rm -rf "$SC"; cp -r "$CLEAN" "$SC"

# ⑥ 反证输入面：把 Bus 按钮调用点大面积拆掉（UB-a 的 0 会变成"没有输入"的假绿）
find "$PROBE/ui" -name "*.kt" -exec sed -i 's/BusButton(/XxxButton(/g; s/BusDialogAction(/XxxAction(/g' {} +
grep -rq "XxxButton(" "$PROBE/ui" && ctl UB-f "组件调用点被拆空 ⇒ 扫描面没了，UB-a 的 0 不算证据"
rm -rf "$SC"; cp -r "$CLEAN" "$SC"

# 正控制：改一颗按钮的文案与 onClick 内容 —— 规格没动，不该红
injected ui/NegctlProbePos.kt 'package com.shanxiang.buspost.ui
import androidx.compose.runtime.Composable
import com.shanxiang.buspost.core.ui.BusDialogAction
@Composable fun NegctlPos() { BusDialogAction(text = "正常新增的一颗", onClick = {}) }' \
  && must_green "新增一颗走设计系统的按钮（不该红；红了说明闸门在拦正常编辑）"

echo "[恢复后] $(BUSPOST_ANDROID="$SC" bash "$PR" 2>&1 | tail -1 | cut -c1-90)"
if [ "$FAIL" = 1 ]; then echo "RESULT=FAIL（有负控制没响、或正控制误响，见上方 ✘/△）"; exit 1; fi
echo "RESULT=PASS"
