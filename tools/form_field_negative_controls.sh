#!/usr/bin/env bash
# FF-a…FF-h（tools/check_form_field_probes.sh）的**负控制**（#283/#288/#289 那一轮 UI 补丁的配套簿）。
#
# 和别的簿不一样的一点：这一轮的判据目标是**设备像素读数**定的（改前 1.02:1 / 1.37:1、59.0x40.4dp，
# 改后三段边界值 / 6.09:1 / 96.8x48.0dp），所以每条注入都对应"把那次改回来的写法"，不是随便找个字符串破坏。
# 逐条破口 → 确认判据 exit 1 且**报的就是这一条**（按 FF-x 点名；红名单不唯一的两条已如实写明）。
# 只在临时目录副本上动手，工程真身不受影响；每条注入后整份副本从干净基线重拷。
# 用法：bash tools/form_field_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
# 互斥锁：负控会就地改源码，两本并行必然互相踩出假红/假绿（2026-10-01 / D-039）
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/ff_neg.$$
CLEAN=/tmp/ff_neg_clean.$$
PKG=com/shanxiang/buspost
PR="$SRC/tools/check_form_field_probes.sh"
mkdir -p "$SC/app"
cp -r "$SRC/app/src" "$SC/app/src"
cp -r "$SRC/tools" "$SC/tools"        # 10 号要打判据簿自己，不拷 tools 就只能改真身
for need in "$SC/app/src/main/java/$PKG/core/ui/Components.kt" \
            "$SC/app/src/main/java/$PKG/ui/login/LoginScreen.kt" \
            "$SC/app/src/main/java/$PKG/ui/login/LoginViewModel.kt" \
            "$SC/tools/check_form_field_probes.sh"; do
  [ -e "$need" ] || { echo "✘ 副本缺 $need（缺一样就会有一条负控假绿）"; exit 2; }
done
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT
cp -r "$SC" "$CLEAN"

CMP="$SC/app/src/main/java/$PKG/core/ui/Components.kt"
SCR="$SC/app/src/main/java/$PKG/ui/login/LoginScreen.kt"
VM="$SC/app/src/main/java/$PKG/ui/login/LoginViewModel.kt"
BOOK="$SC/tools/check_form_field_probes.sh"

FAILED=0
N_OK=0 N_BAD=0
redlist() { echo "$1" | grep '^× ' | sed -E 's/^× ([^ ]+).*/\1/' | sort | tr '\n' ',' ; }
ctl() { # $1=说明 $2=必须点名的判据 $3=完整红名单（逗号结尾，缺省=只有 $2）$4=跑哪一本判据（10 号要跑**副本里被改过的那本**）
  # ⚠ 匹配要带尾随空格：`^× FF-a ` 不带空格会把 `× FF-a2 …` 也算成"报的就是这一条"。
  local out code red book="${4:-$PR}"
  out=$(BUSPOST_ANDROID="$SC" bash "$book" 2>&1); code=$?
  red=$(redlist "$out")
  local want="${3:-$2,}"
  if [ "$code" = 1 ] && echo "$out" | grep -q "^× $2 " && [ "$red" = "$want" ]; then
    echo "✔ $2  $1 → exit 1，红名单=$red：$(echo "$out" | grep "^× $2 " | head -1)"; N_OK=$((N_OK + 1))
  elif [ "$code" = 1 ]; then
    echo "△ $2  $1 → exit 1 但红名单是 $red（期望 $want）：$(echo "$out" | grep '^× ' | head -1)"
    N_BAD=$((N_BAD + 1)); FAILED=1
  else
    echo "✘ $2  $1 → 没变红（rc=$code），这条判据是永不为红的摆设"
    N_BAD=$((N_BAD + 1)); FAILED=1
  fi
  rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
# 注入器：锚点没命中就当场 exit 2 ——「以为注入了、其实树没动」是本工作区踩过两次的假绿来源（D-037/F2-7i）
# ⚠ 替换文本**不得再包含锚点原文**（SELF-MATCH），否则判据会数到注释里那句，破口看起来还在 ⇒ 假绿。
swap() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ swap 锚点没命中，破口根本没注入（这条负控不成立）"; exit 2; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
assert s.count(old) == 1, "ANCHOR-NOT-UNIQUE: 锚点出现 %d 次，替换会改错位置" % s.count(old)
assert old not in new, "SELF-MATCH: 替换文本里还留着锚点，判据会读到它"
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
PY
}
# grow：往锚点**后面追加**（专打"长出一行/一块"这类回归）——这类注入必须保留锚点原文，所以不查 SELF-MATCH。
# 和 safe_call_negative_controls.sh 里同名 helper 同一个理由：回退写法天然包含现写法。
grow() { python - "$1" "$2" "$3" <<'PY' || { echo "✘ grow 锚点没命中（这条负控不成立）"; exit 2; }
import sys
p, old, add = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, "ANCHOR-MISS: " + old[:60]
assert s.count(old) == 1, "ANCHOR-NOT-UNIQUE: 锚点出现 %d 次，追加会改错位置" % s.count(old)
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, old + add, 1))
PY
}

# 基线：不动任何东西时必须全绿，否则后面每一条红都无法归因
if BUSPOST_ANDROID="$CLEAN" bash "$PR" >/dev/null 2>&1; then
  echo "✔ BASE  干净副本判据全绿"
else
  echo "✘ BASE  干净副本就红了，下面每一条红都无法归因"
  FAILED=1
fi

# ── 1 FF-a：把描边整块删掉 = 回到 #283 改前那一版 ──────────────────────────
# 这一刀必然同时断 FF-b（被删的那 9 行里就有第二个 RoundedCornerShape(BusRadius.field)），
# 所以期望红名单是 FF-a + FF-b —— 不是"注入不干净"，是"删边框这个回归本来就同时破坏两格"，如实登记。
swap "$CMP" \
'                .border(
                    if (focused || error != null) 2.dp else 1.dp,
                    when {
                        error != null -> t.danger
                        focused -> t.accent
                        else -> t.lineDeep
                    },
                    RoundedCornerShape(BusRadius.field),
                )
' \
'                .padding(0.dp)
'
ctl "输入框描边整块删掉（回到只有填充的那一版）" FF-a FF-a,FF-b,

# ── 2 FF-b：描边还在，但形状不再跟裁剪共用同一个圆角 ──────────────────────
swap "$CMP" '                    RoundedCornerShape(BusRadius.field),' \
            '                    RoundedCornerShape(BusRadius.m),'
ctl "描边换成另一个圆角（直角的线画在圆角填充外面）" FF-b

# ── 3 FF-c：删掉 notice 的渲染块 = 回到"有写入口、0 渲染点" ────────────────
# ⚠ 锚点必须含 Text：LoginViewModel 与注释里都有 `notice`，只锚 `vm.notice` 会数到别处（假绿）。
swap "$SCR" \
'            vm.notice?.let {
                Text(
                    it,
                    color = t.goldDeep,
                    fontSize = (12 * scale).sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
' \
'            // 回执不渲染
'
ctl "notice 渲染块整块删除（验证码发没发没人说）" FF-c

# ── 4 FF-d：把自造时效写回去 ──────────────────────────────────────────────
# 后端配置 expire-times 是 10m，而客户端把响应体整个丢掉、从没拿到过期时间；
# 「5 分钟内有效」是上一轮真被写上屏幕、又被撤掉的那句话。
swap "$VM" '验证码已发送"' '验证码已发送，5 分钟内有效"'
ctl "自造验证码时效回潮（后端值从未被解析）" FF-d

# ── 5 FF-e：把"整块板调淡"的写法引回来 ────────────────────────────────────
# 实测过：primary.copy(alpha=0.45) 落在 paper 上是 (155,191,226)，白字压上去只有 1.37:1。
# 一次注入不够：那一版是"声明 alphaMod"+"板色套 alpha"两行一起写的，只写一行就编译不过，
# 那不是回退而是凭空造的红。锚点各自唯一，两步都命中才继续。
grow "$CMP" '    val dimmed = !enabled || loading' '
    val alphaMod = if (dimmed) 0.45f else 1f'
swap "$CMP" '.background(if (filled) board else Color.Transparent, shape)' \
            '.background(if (filled) board.copy(alpha = alphaMod) else Color.Transparent, shape)'
ctl "禁用态回到 alpha 淡出（设备实测 1.37:1 的那一版）" FF-e

# ── 6 FF-f：撤掉逐框聚焦事件 ──────────────────────────────────────────────
# 实测过：聚焦手机号后该行描边从 3px(199,213,224) 变 6px(46,123,191)，同屏未聚焦的验证码框仍是 3px。
swap "$CMP" 'Modifier.fillMaxWidth().onFocusEvent { focused = it.isFocused }' \
            'Modifier.fillMaxWidth()'
ctl "聚焦事件撤掉（描边不再响应正在打字的那个框）" FF-f

# ── 7 FF-g 板：禁用态灰板撤掉 = 灰字压主题板 ──────────────────────────────
swap "$CMP" 'val board = if (!enabled) t.track else when (variant) {' \
            'val board = when (variant) {'
ctl "禁用态不换灰板（灰字压在主题板上）" FF-g

# ── 8 FF-g 字：字色回到 onPrimary = 白字压灰板 ────────────────────────────
swap "$CMP" '        !enabled -> t.textSecondary' '        !enabled -> t.onPrimary'
ctl "禁用态不换灰字（白字压在灰板上）" FF-g

# ── 9 FF-h：整行居中的切换链接回到没有宽度支撑的 End ──────────────────────
# 实测过：那一行不带 fillMaxWidth 时 Arrangement.End 空转，节点塌成 59.0x40.4dp 的贴边小链。
swap "$SCR" \
'                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,' \
'                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,'
ctl "切换链接的右对齐空转回来了（贴边小链）" FF-h

# ── 10 判据簿自己：证明"目标 0"那几格不是摆设 ─────────────────────────────
# FF-d / FF-e / FF-h 的目标都是 0，而"扫到 0"和"扫坏了所以恒 0"输出一模一样。
# 这一条不动源码，只把**副本里**判据簿 FF-e 的断言左值改成不可能满足：书若还绿，说明闸门根本没接上电。
swap "$BOOK" '[ "$FE" = 0 ]' '[ "$FE" = 9 ]'
ctl "把 FF-e 的断言左值改成永不成立（反证闸门带电）" FF-e FF-e, "$BOOK"

echo "—— 负控制：✔ $N_OK / △ $N_BAD ——"
[ "$FAILED" = 0 ] && echo "form_field 负控制全部按名点名变红" || echo "form_field 负控制有假绿风险"
exit "$FAILED"
