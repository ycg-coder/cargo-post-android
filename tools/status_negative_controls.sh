#!/usr/bin/env bash
# 状态映射判据（tools/check_status_probes.sh）的配对负控制 —— 补 D-CE-4：
# 这本判据从建立起就没有配对负控，它对 HomeScreen/BusScreen 的"恰好 2"一类锚点只能证明"复跑没红"，不能证明"判得动"。
# 同一轮把六处"松闸"收到现读（S-e ≥6→=10、S-i ≥20→=65、S-j ≥1→=4、S-k ≥15→=17、S-lc ≤3→≤2、S-ld 判定 ≥2 与话术 ≥3 不一致→都取 3），
# 否则下面这些破口根本没法构造（现读离闸门太远，删一处不红 = D-084 根因 #3 的上限/下限两种形态）。
# 用法：bash tools/status_negative_controls.sh
set -u
SRC="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
. "${BASH_SOURCE[0]%/*}/negctl_lock.sh"
negctl_lock "$(basename "$0")" || exit 3
SC=/tmp/st_neg.$$
CLEAN=/tmp/st_neg_clean.$$
PROBE="$SRC/tools/check_status_probes.sh"

mkdir -p "$SC/app/src"
cp -r "$SRC/app/src/main" "$SC/app/src/main"
cp -r "$SRC/app/src/test" "$SC/app/src/test"
mkdir -p "$SC/docs"
cp "$SRC/docs/STATUS_MAPPING.md" "$SC/docs/STATUS_MAPPING.md"
A=$SC/app/src/main/java/com/shanxiang/buspost
BS=$A/ui/user/BusScreen.kt
HS=$A/ui/user/HomeScreen.kt
SS=$A/data/model/ServerStatus.kt
MD=$A/data/model/Models.kt
DD=$A/data/repo/DemoData.kt
HO=$A/ui/driver/HandoverScreen.kt
DE=$A/ui/driver/DriverExceptionScreen.kt
RT=$A/ui/driver/RoutesScreen.kt
MP=$A/ui/driver/DriverLegStateMapper.kt
TV=$A/ui/common/TraceView.kt
RM=$A/core/utils/ReviewMapper.kt
TST=$SC/app/src/test/java/com/shanxiang/buspost/data/StatusMappingContractTest.kt
DOC=$SC/docs/STATUS_MAPPING.md
for f in "$BS" "$HS" "$SS" "$MD" "$DD" "$HO" "$DE" "$RT" "$TV" "$MP" "$RM" "$TST" "$DOC"; do
  [ -f "$f" ] || { echo "✘ 沙盒缺 $f：判据扫得到、副本没有，本轮负控全部作废"; exit 2; }
done
cp -r "$SC" "$CLEAN"
trap 'rm -rf "$SC" "$CLEAN"; negctl_release' EXIT

out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); base=$?
if [ "$base" != 0 ]; then echo "✘ 基线未通过，负控结果一律不采信"; printf '%s\n' "$out" | grep -a '^×' | head -5; exit 2; fi
echo "✔ 基线：副本原样 exit 0"

MISS=0; ok=0; bad=0
# subst1：替换该文件里的**第一处**（同形多处时不假装唯一），落空必须报 OLD-MISS
subst1() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if old not in s: sys.exit("OLD-MISS: " + old[:60])
if old in new: sys.exit("SELF-MATCH: 替换文本里还留着锚点")
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
PY
}
ins_after() { python - "$1" "$2" "$3" <<'PY' || { MISS=1; return 1; }
import sys
p, anchor, add = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if s.count(anchor) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(anchor), anchor[:60]))
NL = '\r\n' if '\r\n' in s else '\n'
j = s.index('\n', s.find(anchor)) + 1
open(p, 'w', encoding='utf-8', newline='').write(s[:j] + add.replace('\n', NL) + NL + s[j:])
PY
}
delline() { python - "$1" "$2" <<'PY' || { MISS=1; return 1; }
import sys
p, needle = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8', newline='').read()
if s.count(needle) != 1: sys.exit("ANCHOR-NOT-UNIQUE(%d): %s" % (s.count(needle), needle[:60]))
i = s.find(needle); j = s.find('\n', i)
open(p, 'w', encoding='utf-8', newline='').write(s[:i] + s[j + 1:])
PY
}

# delall：删掉某文件里**每一处**匹配行（S-le2 的判据是"该文件还剩 ≥1 个回读点"，换一处不算破口）
delall() { python - "$1" "$2" <<'PY2' || { MISS=1; return 1; }
import sys
p, needle = sys.argv[1], sys.argv[2]
lines = open(p, encoding='utf-8', newline='').readlines()
kept = [l for l in lines if needle not in l]
if len(kept) == len(lines): sys.exit("OLD-MISS: " + needle[:60])
open(p, 'w', encoding='utf-8', newline='').writelines(kept)
PY2
}
killread() { delall "$1" 'Repo.driverLegs()'; delall "$1" 'Repo.currentLeg()'; }

check() {
  local id="$1" want="$2" rc out
  shift 2
  if ! "$@"; then
    echo "✘ $id 锚点没命中/不唯一，破口根本没注入（这条负控不成立）"
    bad=$((bad + 1)); MISS=0; rm -rf "$SC"; cp -r "$CLEAN" "$SC"; return
  fi
  out=$(BUSPOST_ANDROID="$SC" bash "$PROBE" 2>&1); rc=$?
  if [ "$rc" = 0 ]; then
    echo "✘ $id 判据仍然绿（破口没咬住），这条判据等于没判据"; bad=$((bad + 1))
  elif printf '%s\n' "$out" | grep -qF "$want"; then
    echo "✔ $id 红且原因对得上（$want）"; ok=$((ok + 1))
  else
    echo "✘ $id 红了但不是那一条（缺 $want；实为 $(printf '%s\n' "$out" | grep -a '^×' | head -1)）"; bad=$((bad + 1))
  fi
  MISS=0; rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}
check_miss() {
  local id="$1"; shift
  if "$@"; then
    echo "✘ $id 注入器放过了一个不存在的锚点（后面每条 ✔ 都可能是假绿）"; bad=$((bad + 1))
  else
    echo "✔ $id 不存在的锚点被注入器当场拒绝"; ok=$((ok + 1))
  fi
  MISS=0; rm -rf "$SC"; cp -r "$CLEAN" "$SC"
}

# ---------- S-a / S-b / S-c：三张"必须为 0"的表 ----------
# ⚠ S-a 的正则是 `[^a-zA-Z]status (==|>=|<=) 数字` 或 `.status == 数字`：
#   注入 `orderStatus == 4` 这种"前面贴着字母"的形状**不会命中**（第一版就这么假红过一次），
#   要么写裸 `status >= 4`，要么写 `o.status == 4`。
check S-a1 "× S-a 又出现了按数字比段状态" ins_after "$BS" '    var lines by mutableStateOf<List<BusLine>>(emptyList())' '    private fun done4(status: Int) = status >= 4'
check S-a2 "× S-a 又出现了按数字比段状态" ins_after "$HS" 'private fun VehicleRow(' '    val stEq = if (status == 4) 1 else 0'
check S-b1 "× S-b 文案子串判状态复现" subst1 "$TV" 'BusTag(leg.statusLabel, tone = leg.statusTone)' 'BusTag(if (leg.statusLabel.contains("完成")) "已完成" else leg.statusName, tone = leg.statusTone)'
check S-c1 "× S-c 页面自建码表复现" ins_after "$BS" '    var lines by mutableStateOf<List<BusLine>>(emptyList())' '    private fun labelOf(o: Leg) = when (o.status) {
        0 -> "待" }'

# ---------- S-c2 / S-c3 / S-d：三个"恰好 N" ----------
check S-c21 "× S-c2 车辆运行态 when 数量变了" subst1 "$HS" 'when (v.status)' 'when (v.dataSource)'
check S-c31 "× S-c3 审核码入口减少" subst1 "$RM" 'ReviewStatus.of(status)' 'ReviewStatus.ofCode(status)'
check S-d1 "× S-d 单一映射表被增删（枚举数不为 6）" subst1 "$SS" 'enum class LegStatus' 'sealed class LegStatus'

# ---------- S-e / S-f / S-g / S-h ----------
check S-e1 "× S-e 未知码占位文案引用点掉到 10 以下" subst1 "$MD" 'STATUS_UNAVAILABLE_LABEL' 'UNAVAILABLE_TEXT_V2'
check S-f1 "× S-f 错的段码注释又回来了" ins_after "$DD" '    private fun j(vararg pairs: Pair<String, Any?>): JSONObject {' '        val legacy = "待派单"'
check S-g1 "× S-g DemoData 第二张货运表复现" ins_after "$DD" '    private fun j(vararg pairs: Pair<String, Any?>): JSONObject {' '        fun legacy2(x: Int) = when (x) { 0 -> "已创建"; else -> "?" }'
check S-h1 "× S-h 缺键哨兵数量变化（§14 三态退化风险）" subst1 "$MD" 'has("status")' 'opt("status")'

# ---------- S-i / S-j / S-k ----------
check S-i1 "× S-i 枚举引用点掉到" subst1 "$DE" 'LegStatus.of(back.status) != LegStatus.EXCEPTION' 'back.statusLabel != "异常"'
check S-j1 "× S-j 文档缺失或段码表被删" subst1 "$DOC" 'TransportLegStatusEnum' 'LegStatusEnumV2'
check S-k1 "× S-k 契约测试用例被删" subst1 "$TST" '    @Test' '    //k0 负控：注释掉一条'

# ---------- S-la / S-lb / S-lc / S-ld / S-le / S-le2 ----------
check S-la1 "× S-la 客户端又在算下一状态" ins_after "$RT" 'private fun VehicleCard(v: BusVehicle) {' '    val nextStatus = 3'
check S-lb1 "× S-lb 本机改写段状态" ins_after "$RT" 'private fun VehicleCard(v: BusVehicle) {' '    val copyStatus = leg.copy(status = 3)'
check S-lc1 "× S-lc 段动作字面量散到了映射器之外" ins_after "$BS" '    var lines by mutableStateOf<List<BusLine>>(emptyList())' '    private val legLiteral = "arrive-dest"'
check S-ld1 "× S-ld 司机页面退回本地状态判断" subst1 "$RT" 'DriverLegStateMapper.' 'LegStateMapperV2.'
check S-le1 "× S-le 段动作入口数量变了（核对是否漏了回读）" subst1 "$HO" 'val ack = Repo.legAction(' 'val ack2 = Repo.legAction('
check S-le21 "× S-le2 这些页面写完不回读" killread "$DE"

# S-m：手动地址查公交页的三态。这一页旧写法只有"加载中"一格，失败与"没解析出来"都是整屏空白。
AS=$A/ui/user/AddressSearchScreen.kt
[ -f "$AS" ] || { echo "✘ 沙盒缺 $AS：S-m 的注入等于没注入"; exit 2; }
check S-m1 "× S-m 手动地址查公交页的错误出口不是恰好两处" delline "$AS" 'item { ErrorState(e, onRetry = { vm.search() }) }'
# S-m3（D-173 第三格）：把"没问到"那一格的 ErrorState 摘掉 ⇒ 数量与内容两关都要响
check S-m3 "× S-m 『没问到』那一格没把原因带出来" delline "$AS" '                ErrorState(vm.searchFailure, onRetry = { vm.search() })' 
check S-m2 "× S-m 这一页又长出自己画的加载块" ins_after "$AS" '    var searched by mutableStateOf(false)' '    private val handSpunLoader = CircularProgressIndicator()'

check_miss S-x0 subst1 "$SS" '这一行在本仓根本不存在-NEG-CONTROL' 'x'

echo "---"
echo "计数：按名点名变红 $ok 条 / 不成立 $bad 条"
[ "$bad" = 0 ] || echo "✘ 有破口没被自己那条判据抓住，或注入根本没落"
[ "$bad" = 0 ]
