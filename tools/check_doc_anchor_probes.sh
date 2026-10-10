#!/usr/bin/env bash
# 台账锚点判据簿（F-3 ⑰，2026-10-03）。管的是"台账里那句 `Models.kt:874` 今天还指不指得到东西"。
#   背景：`D-③-5` 说旧口径的 ID-MISS 不能当闸门——标识符按**表格单元**取词，一格并列几个锚点时
#   A 被要求包含 B 的名字，8 本台账实测 612 条全是假报。现在标识符按**最近锚点**分配、
#   每条锚点有与行号无关的 `aid`，`--pin` 才第一次变成能用的闸：新增即红、在册的悄悄消失也红。
# 口径（下面这排数字是**闸门下限**，不是任何一次的实测值；实测值由 DA-a…DA-f 每轮自己打）：
#   anchors≥1216  ID-MISS≤286  FILE-NOT-FOUND≤15  OUT-OF-RANGE=0  带标识符≥578 且其中 OK≥277
#   下限的出处（F-3 ⑰ 那一轮实测）：anchors=1216  records=1216  OK 915 / ID-MISS 286 / FILE-NOT-FOUND 15 / OUT-OF-RANGE 0
#   distinct aid 967；带标识符的锚点 578，其中判 OK 的 277  ← DA-f 的反证输入，少了它 DA-e 的"只降不升"没有意义
# 在册表 `tools/fixtures/doc_anchor_pins.txt`：**262 → 252**（2026-10-09，D-068 / §八 ㊼）。
#   本轮真修的是当时**新增**的那 18 条（台账里的 `Models.kt:1374`/`Repo.kt:879` 这类行号被源码改动顶漂，
#   逐条 grep 出真实行号后重锚，一条一处一地点名，见 D-068）。另外 **消失** 的那 10 条本轮没动过：
#   它们在某轮源码漂移后窗口重新命中了自己的标识符 ⇒ 不再可疑。**成因未逐条归因**（不猜是哪一轮），
#   闸门只认"在册的这次不再可疑 ⇒ 要人下调在册表"这一句，所以这里就是那次下调。
#   ⚠ 252 条仍全是 ID-MISS/FILE-NOT-FOUND 在册等消化 ⇒ 这次"绿"读的是**闸门对齐**，不是"台账锚点都对"。
#   内容级重锚仍归 #159。
# 用法：bash tools/check_doc_anchor_probes.sh（可用 BUSPOST_ANDROID / BUSPOST_WORK 覆盖根）
set -u
SELF="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="${BUSPOST_ANDROID:-$SELF}"
# 后端是同级的另一个仓：负控沙盒只 copy 本工程的 app/src + docs，不指回真身的话
# 所有 `*.java` 锚点都会成 FILE-NOT-FOUND，DA-c 的 15 就成了"沙盒缺仓"而不是"台账写错文件名"。
WORK="${BUSPOST_WORK:-$(cd "$SELF/.." && pwd)}"
cd "$ROOT" || exit 1

AT="$ROOT/tools/check_doc_anchors.py"
[ -f "$AT" ] || AT="$SELF/tools/check_doc_anchors.py"
PIN="tools/fixtures/doc_anchor_pins.txt"
DOCS=(ANDROID_PARITY_AUDIT_V2.md AI_ITERATION_STATE.md DECISION_LOG.md CONTEXT_LOCK.md
      THIRD_PARTY_NOTICES.md docs/android/CONTRACT_EVIDENCE.md docs/android/ANDROID_V2.1_CHECKLIST.md)
for d in "${DOCS[@]}"; do
  [ -f "$ROOT/$d" ] || { echo "× 台账 $d 不在 $ROOT 下（判据簿的扫描面塌了，本轮所有数不采信）"; exit 1; }
done
[ -f "$ROOT/$PIN" ] || { echo "× 在册表 $PIN 读不到 ⇒ 闸门没有输入，DA-d 的绿是假的"; exit 1; }

# JSON 路径不能让 bash 变量中转中文：默认 GBK 的 stdout 会把用户名（`袁`）改成 `Ԭ` ⇒ open FileNotFound
ANCHOR_JSON=$(PYTHONIOENCODING=utf-8 python -c 'import tempfile,os;print(os.path.join(tempfile.gettempdir(),"da_anchor_probe_%d.json"%os.getpid()))')
OUT=$(PYTHONIOENCODING=utf-8 python "$AT" --strict-id --pin "$PIN" \
        --backend "$WORK/cargo-post-platform" --json "$ANCHOR_JSON" "${DOCS[@]}" 2>&1)
tool_rc=$?
FIELDS=$(PYTHONIOENCODING=utf-8 python -c "$(cat <<'PY'
import json, sys
try:
    with open(sys.argv[1], encoding="utf-8", errors="replace") as fh:
        recs = json.load(fh)
except Exception:
    sys.exit(3)
import collections
vt = collections.Counter(r["verdict"] for r in recs)
tok = [r for r in recs if r["tokens"]]
print("\t".join(str(x) for x in [
    len(recs), vt.get("OK", 0), vt.get("ID-MISS", 0),
    vt.get("FILE-NOT-FOUND", 0), vt.get("OUT-OF-RANGE", 0), vt.get("DUP", 0),
    len({r["aid"] for r in recs}), len({r["aid"] for r in recs if r["verdict"] != "OK"}),
    len(tok), sum(1 for r in tok if r["verdict"] == "OK")]))
PY
)" "$ANCHOR_JSON" 2>/dev/null)
reader_rc=$?
rm -f "$ANCHOR_JSON"

gate_line=$(printf '%s\n' "$OUT" | grep '^\[闸门\]' | tail -1)
agg_line=$(printf '%s\n' "$OUT" | grep '^\[聚合\]' | tail -1)
gate_pinned=$(printf '%s' "$gate_line" | sed -n 's/.*在册 \([0-9]\+\) 个 aid.*/\1/p')
gate_new=$(printf '%s' "$gate_line" | sed -n 's/.*新增 \([0-9]\+\) 个.*/\1/p')
gate_gone=$(printf '%s' "$gate_line" | sed -n 's/.*消失 \([0-9]\+\) 个.*/\1/p')
agg_anchors=$(printf '%s' "$agg_line" | sed -n 's/.*anchors=\([0-9]\+\).*/\1/p')
pin_rows=$(grep -cv '^#' "$ROOT/$PIN")

if [ "$reader_rc" = 0 ]; then
  IFS=$'\t' read -r da_records da_ok da_miss da_nf da_oor da_dup da_aids da_susp_aids \
      da_tokn da_tok_ok <<< "$FIELDS"
else
  da_records=-1; da_ok=-1; da_miss=-1; da_nf=-1; da_oor=-1; da_dup=-1
  da_aids=-1; da_susp_aids=-1; da_tokn=-1; da_tok_ok=-1
fi
: "${gate_pinned:=-1}"; : "${gate_new:=-1}"; : "${gate_gone:=-1}"; : "${agg_anchors:=-1}"

printf 'DA-a  判决表逐条对齐/锚点总数   : records=%s（聚合 %s）  OK=%s  distinct aid=%s  （预期 records==聚合 且 ≥1216）\n' \
    "$da_records" "$agg_anchors" "$da_ok" "$da_aids"
printf 'DA-b  OUT-OF-RANGE（行号越过 EOF）: %s  DUP=%s  （预期 0：台账指到不存在的行就是说谎）\n' "$da_oor" "$da_dup"
printf 'DA-c  FILE-NOT-FOUND             : %s  （闸门基线 ≤15，此处打的是本轮实测）\n' "$da_nf"
printf 'DA-d  在册闸门                   : 在册=%s 新增=%s 消失=%s 可疑 aid=%s 在册表行数=%s  （预期 新增=0 且 消失=0）\n' \
    "$gate_pinned" "$gate_new" "$gate_gone" "$da_susp_aids" "$pin_rows"
printf 'DA-e  ID-MISS（内容已漂或本就错）: %s  （闸门基线 ≤286，此处打的是本轮实测；#159 重锚一条就降一条，不许涨）\n' "$da_miss"
printf 'DA-f  反证：带标识符锚点/其中 OK : %s / %s  （预期 ≥578 且 ≥277：分配规则一坏，DA-e 的 0 就是假的）\n' \
    "$da_tokn" "$da_tok_ok"

ok=1
if [ "$reader_rc" != 0 ]; then echo "× 锚点工具跑不通（reader rc=$reader_rc, tool rc=$tool_rc）：沙盒缺 tools/、--backend 没接上或台账文件名变了，DA-a…DA-f 全部不采信"; ok=0; fi
# 工具只会用 0/1 表达"有没有判决要人看"；其它退出码（traceback、argparse 错）才是簿本身坏了
{ [ "$tool_rc" = 0 ] || [ "$tool_rc" = 1 ]; } || { echo "× 锚点工具异常退出 rc=$tool_rc（不是 0/1 的判决码），本簿读数不采信"; ok=0; }
[ "$da_records" = "$agg_anchors" ] || { echo "× DA-a 判决表条数与工具自报的 anchors 不等 ⇒ 有锚点被静默丢掉，闸门看不到它"; ok=0; }
[ "$da_records" -ge 1216 ] || { echo "× DA-a 锚点总数低于本轮实测 1216（台账里的「文件:行」被人删掉或写坏成扫不到，锚点复核就没了输入）"; ok=0; }
[ "$da_oor" = 0 ] || { echo "× DA-b 有锚点行号超过文件行数：台账指的是不存在的行"; ok=0; }
[ "$da_nf" -le 15 ] || { echo "× DA-c FILE-NOT-FOUND 回涨（本轮实测 $da_nf > 闸门基线 15）：新增的文件名写错或文件已删"; ok=0; }
[ "$gate_new" = 0 ] || { echo "× DA-d 出现不在册的可疑锚点：新增即红，要么修好要么进在册表，不许悄悄挂着"; ok=0; }
[ "$gate_gone" = 0 ] || { echo "× DA-d 在册锚点本次不再可疑：真修好了就下调在册表，别让它悄悄消失"; ok=0; }
[ "$gate_pinned" = "$pin_rows" ] || { echo "× DA-d 工具读到的在册数与文件行数不等：在册表里有重复或坏行"; ok=0; }
[ "$da_miss" -le 286 ] || { echo "× DA-e ID-MISS 回涨（本轮实测 $da_miss > 闸门基线 286）：改过源码却没人重量台账锚点"; ok=0; }
[ "$da_tokn" -ge 578 ] || { echo "× DA-f 带标识符的锚点低于 578 ⇒ 分配规则被改坏或 STOP 名单吞掉了名字，此时 DA-e 的 0 不可信"; ok=0; }
[ "$da_tok_ok" -ge 277 ] || { echo "× DA-f 带标识符且判 OK 的锚点低于 277 ⇒ 正控制失效，本簿没有能判绿的样本"; ok=0; }
[ "$da_dup" = 0 ] || echo "　（DA-b 附注：DUP=$da_dup 只提示同名文件在两处，不判红）"

[ "$ok" = 1 ] || exit 1
# 收尾这句过去把 anchors/越界/ID-MISS 写死成"本轮实测值"：台账只要再加一条锚点，这句就当场变成假话（闸门本身还在响，但没人读闸门）。
# ⇒ 改成打印本次真实读数；下限仍由 DA-a/DA-e 那几道闸管。
echo "台账锚点判据通过（七本台账 anchors=$da_records 越界 $da_oor 在册闸门 $gate_pinned 个 aid 全对齐，ID-MISS $da_miss 只升不降由 #159 逐条消化）"
