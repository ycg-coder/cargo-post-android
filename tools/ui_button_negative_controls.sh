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
# UB-k/UB-l 读的是归档 dump 的像素：证据不跟进沙盒，基线就会因为"没证据"红，
# 那是环境红不是判据咬 ⇒ 负控结果全不采信（本仓踩过：负控批的自毁产物）。
# ⚠ 只拷这两本要读的目录，不要拷整棵 docs/device-evidence：那里有几百 MB 的历史 logcat/截图，
#   每跑一条负控都要整树还原一次，一轮 14 分钟全花在拷文件上（2026-10-18 实测）。
mkdir -p "$SC/docs/device-evidence"
cp -r "$SRC/docs/device-evidence/20261018-hotzone" "$SC/docs/device-evidence/" 2>/dev/null
cp -r "$SRC/docs/device-evidence/20261018-sweep" "$SC/docs/device-evidence/" 2>/dev/null
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

# ⑦ 低于命中下限的手搓按钮状块回来了（本轮真身里那三处的形状）
injected ui/NegctlProbeG.kt 'package com.shanxiang.buspost.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
@Composable fun NegctlG() { Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).clickable { }) }' \
  && ctl UB-g "按钮状可点块把高度写死成 46dp ⇒ BusTouch 的 48/56dp 下限整条失效"

# ⑧ 轮播视口里长出第二条可点块（在册那一条是被登记的，不是被豁免的；
#    2026-10-15 D-120 把第二条从在册里摘出去了——它从来是零调用点的死渲染件，没上过屏）
injected ui/NegctlProbeH.kt 'package com.shanxiang.buspost.ui
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable fun NegctlH() { androidx.compose.foundation.pager.HorizontalPager(
        state = null,
        modifier = Modifier.fillMaxWidth().height(40.dp),
) { p -> Row(Modifier.fillMaxWidth().clickable { }) {} } }' \
  && ctl UB-h "轮播视口数从在册 1 变 2 ⇒ 又有一条热区被视口钉住"

# UB-j：同一排里放一颗实心 + 一颗描边（D-161 用户指的那个形状）⇒ 必须红
injected ui/NegctlProbeMix.kt 'package com.shanxiang.buspost.ui
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
@Composable fun NegctlMix() {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        BusButton(text = "次要", onClick = {}, variant = BusButtonVariant.OUTLINE)
        BusButton(text = "主要", onClick = {})
    }
}'   && ctl UB-j "同排出现实心 + 描边 ⇒ 用户说的那句不搭配又回来了"

# UB-k：天气胶囊命中框的接线与证据（D-164 真机量到 91px=33dp 那一格）
WC="$PROBE/ui/common/WeatherCard.kt"
# k-① heightIn 那行被摘掉 ⇒ 33dp 原样回来
sed -i 's/\.heightIn(min = touch\.min)/.removedMinHeight(touch.min)/' "$WC"
grep -q "removedMinHeight" "$WC" && ctl UB-k "胶囊的 heightIn 被摘 ⇒ 量出来的命中框会退回 33dp"
rm -rf "$SC"; cp -r "$CLEAN" "$SC"
# k-② 顺序反了：heightIn 排到垂直留白之后 ⇒ 撑大的那块不归可点节点（看着抬了，量出来没抬）
sed -i '/\.heightIn(min = touch\.min)/d' "$WC"
sed -i 's/\.padding(horizontal = 11\.dp, vertical = 7\.dp)/.padding(horizontal = 11.dp, vertical = 7.dp)\n            .heightIn(min = touch.min)/' "$WC"
grep -A2 'vertical = 7.dp)' "$WC" | grep -q heightIn && ctl UB-k "heightIn 排到了垂直留白**之后** ⇒ 顺序判据要咬"
rm -rf "$SC"; cp -r "$CLEAN" "$SC"
# k-②b 换回 defaultMinSize（真机 v173 实测只到 38dp 的那个写法）⇒ s3 必须咬，且要单独咬得出来
sed -i 's/\.heightIn(min = touch\.min)/.heightIn(min = touch.min)\n            .defaultMinSize(minHeight = touch.min)/' "$WC"
grep -q "defaultMinSize(minHeight = touch.min)" "$WC" && ctl UB-k "胶囊里又长出 defaultMinSize 那一写法 ⇒ 被实测否掉的写法必须点名"
rm -rf "$SC"; cp -r "$CLEAN" "$SC"
# k-③ 可点节点整行被删 ⇒ click_first 读不到（撑得再高也点不着）
sed -i 's/Modifier\.clickable(onClick = onToggle)/Modifier.semantics { }/' "$WC"
grep -q "semantics { }" "$WC" && ctl UB-k "胶囊的可点节点没了 ⇒ clickable 必须在 heightIn 之前这一条要咬"
rm -rf "$SC"; cp -r "$CLEAN" "$SC"
# k-④ 证据目录被摘走：不许把"没证据"读成"没破口"
mv "$SC/docs/device-evidence/20261018-hotzone" "$SC/docs/hz_hidden" 2>/dev/null
[ -d "$SC/docs/hz_hidden" ] && ctl UB-k "归档热区证据目录不见了 ⇒ 必须红，不许当 0"
rm -rf "$SC"; cp -r "$CLEAN" "$SC"
# k-⑤ 证据被换成正读数（把改前那份的 pill 高度改成 132）：钉值要咬
sed -i 's/bounds="\[88,499\]\[992,590\]"/bounds="[88,499][992,631]"/' "$SC/docs/device-evidence/20261018-hotzone/01-v163-home-before.xml"
ctl UB-k "改前凭证的 91px 被改成别的数 ⇒ 锚点/凭证被动过，钉值这一条要咬"
rm -rf "$SC"; cp -r "$CLEAN" "$SC"
# UB-l：全量热区扫（D-165）。三条：混进一颗矮格 / 把尺子锯短 / 证据目录不见了
cp "$SC/tools/fixtures/hot_sweep_bad/wide-short-strip.xml" "$SC/docs/device-evidence/20261018-sweep/" 2>/dev/null
[ -f "$SC/docs/device-evidence/20261018-sweep/wide-short-strip.xml" ] && ctl UB-l "归档里混进一颗 904x91 的通栏矮格 ⇒ 全量扫必须点名"
rm -rf "$SC"; cp -r "$CLEAN" "$SC"
sed -i 's/argv\[0\], 132, 400, 8/argv[0], 60, 400, 8/' "$SC/tools/hot_zone_sweep.py"
grep -q "argv\[0\], 60," "$SC/tools/hot_zone_sweep.py" && ctl UB-l "扫描器的下限从 132 被锯到 60 ⇒ 那是把尺子锯短，不许读成'零缺陷'"
rm -rf "$SC"; cp -r "$CLEAN" "$SC"
mv "$SC/docs/device-evidence/20261018-sweep" "$SC/docs/sweep_hidden" 2>/dev/null
[ -d "$SC/docs/sweep_hidden" ] && ctl UB-l "全量热区的证据目录不见了 ⇒ 必须红，不许当'扫过且干净'"
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
