#!/usr/bin/env bash
# #57「最早取货时间」的结构判据（配 tools/pickup_window_negative_controls.sh）
# 管七件事：出网值只有 Long 毫秒这一个形状、过去的整点从候选里消失（不是置灰还能点）、
# 「不填」发的是"整个键不发"、三句文案各自唯一出处、回读只认数字毫秒（不许 coerce 出 1970）、
# 回执三格齐、字段可空不许塌成 0；另加撤销出口那一枚 chip 的文案不许被改写（PW-m）。
# 用法：bash tools/check_pickup_window_probes.sh        退出码非 0 = 这一格被拆了
# 写法沿用本仓规矩：按内容过滤不按行号；每个"目标 0"配同文件正对照；下限一律等于现读（D-084 ③、D-085 ㊾①、D-086 ㊿②）。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || exit 2
M=app/src/main/java/com/shanxiang/buspost
PW=$M/ui/user/PickupWindow.kt
SD=$M/ui/user/SendScreen.kt
PS=$M/ui/user/ParcelScreen.kt
MD=$M/data/model/Models.kt
DD=$M/data/repo/DemoData.kt
CT=app/src/test/java/com/shanxiang/buspost/ui/user/PickupWindowContractTest.kt
NC='^[0-9]+:[[:space:]]*(\*|//|/\*)'

for f in "$PW" "$SD" "$PS" "$MD" "$DD" "$CT"; do
  [ -f "$f" ] || { echo "× 缺 $f：判据没有输入，本轮读数不采信"; exit 2; }
done

nc_count() { grep -n "$2" "$1" | grep -cvE "$NC" || true; }
nc_all() { grep -rn "$1" "$M" --include=*.kt | sed 's/^[^:]*:[0-9]*://' | grep -cvE '^[[:space:]]*(\*|//|/\*)' || true; }

# PW-a 这个键在生效代码里的命中面：恰好 8 行、恰好分布在 4 个文件，别的文件一处不许有
PA_LINES=$(grep -rn "earliestPickupTime" "$M" --include=*.kt | grep -cvE "$NC")
PA_FILES=$(grep -rln "earliestPickupTime" "$M" --include=*.kt | wc -l)
PA_STRAY=$(grep -rn "earliestPickupTime" "$M/data/repo/Repo.kt" "$M/ui/user/WalletRows.kt" 2>/dev/null | wc -l)
PA_PROOF=$(grep -c "earliestPickupTime" "$MD")

# PW-b 出网值只有一个写法；界面不许自己拼时间串
PB_CALL=$(nc_count "$SD" '"earliestPickupTime" to pickupSubmitMillis()')
PB_TO=$(nc_count "$SD" '"earliestPickupTime" to')
PB_STRING=$(nc_count "$SD" '"earliestPickupTime" to "')

# PW-c 时区只有一个纯层定义；本轮两个文件里不许出现设备默认时区那套
PC_DEF=$(nc_count "$PW" 'internal val PICKUP_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")')
PC_ALL=$(grep -rn 'ZoneId.of("Asia/Shanghai")' "$M" --include=*.kt | wc -l)
PC_DEVICE=$(grep -rn "Calendar.getInstance\|TimeZone.getDefault\|Date().time" "$PW" "$SD" 2>/dev/null | wc -l)
PC_DEVICE_PROOF=$(grep -c "ZonedDateTime.now(PICKUP_ZONE)" "$SD")

# PW-d 过去的整点是"消失"不是"置灰"
PD_FILTER=$(nc_count "$PW" 'filter { pickupMillisOf(now, day, it.hour) != null }')
PD_DISABLE=$(nc_count "$SD" 'enabled = ')
PD_CLICK=$(nc_count "$SD" '.clickable(onClick = onClick)')
PD_NULLGUARD=$(nc_count "$PW" 'return if (instant > now.toInstant().toEpochMilli()) instant else null')

# PW-e 三句话各自唯一出处（纯层常量），页面不许重抄
PE_UNSET=$(grep -rn '随时可取（不限制最早取货时间）' "$M" --include=*.kt | wc -l)
PE_ABSENT=$(grep -rn '后端没给这一项（下单时没填也会这样' "$M" --include=*.kt | wc -l)
PE_NOTE=$(grep -rn '填了最早取货时间，后端会按它筛' "$M" --include=*.kt | wc -l)
PE_PAGE=$( { grep -n '随时可取（不限制\|后端没给这一项（下单时\|填了最早取货时间，后端会' "$SD" "$PS"; } 2>/dev/null | grep -cvE "$NC")
PE_PAGE_PROOF=$(nc_count "$SD" 'PICKUP_DISPATCH_NOTE')

# PW-f 回读只认数字：Models 里那一处 as? Number，且不许 multiNum（这一条刻意不 grep 那个函数名：函数名的括号形状会与 Kotlin 调用点差一，实测会造出假红）
PF_NUM=$(nc_count "$MD" '(o.opt("earliestPickupTime") as? Number)')
PF_MULTI=$(nc_count "$MD" 'multiNum("earliestPickupTime")')
PF_PROOF=$(nc_count "$MD" 'earliestPickupMillis = (o.opt')

# PW-g 回执三格：纯层定义 1、页面调用 1、pickupReceipt 生效行 4（声明 / 赋值 / 判空 / 渲染）
PG_DEF=$(nc_count "$PW" 'internal fun pickupSubmitConclusion')
PG_CALL=$(nc_count "$SD" 'pickupSubmitConclusion(')
PG_STATE=$(nc_count "$SD" 'pickupReceipt')

# PW-h 用例条数 = 现读 16
PH=$(grep -c '^    @Test' "$CT")

# PW-i 字段可空、不塌 0
PI_FIELD=$(nc_count "$MD" 'val earliestPickupMillis: Long? = null')
PI_ZERO=$(nc_count "$MD" 'val earliestPickupMillis: Long = 0')
PI_PROOF=$(nc_count "$MD" 'val etaMinutes: Int?')

# PW-j 演示形态只给一单带这个键（两格都要演出来）
PJ=$(nc_count "$DD" '"earliestPickupTime" to if (i == 1)')

# PW-k 选择器接上了、且只有一个入口；chip 定义 1 + 调用 3
PK_PICKER=$(nc_count "$SD" 'PickupWindowPicker(vm)')
PK_CHIP=$(nc_count "$SD" 'PickupWindowChip')

# PW-l 列表那一枚 chip 只在有值时出现，且念的是回读文本（不许就近念成"不限制"）
PL_READ=$(nc_count "$PS" 'pickupReadbackText(it)')
PL_UNSET=$(nc_count "$PS" 'CargoChip("取件 " + PICKUP_UNSET_TEXT)')
PL_PROOF=$(nc_count "$PS" 'if (s.fresh) CargoChip')

# PW-m 那一枚"不限制"是唯一的撤销出口（选了之后必须还能一键改回不填），文案不许被改写
PM_UNDO=$(nc_count "$SD" 'PickupWindowChip("不限制", on = vm.pickupDay < 0) {')
PM_UNDO_BODY=$(grep -c 'vm.pickupDay = -1' "$SD")

# PW-o 不许用只有 API 34 才有的换算：minSdk 26，这条是本轮被 :app:lintDebug 抓出来的
PO_API34=$(grep -rn "LocalDate\.ofInstant" "$M" --include=*.kt | wc -l)
PO_PROOF=$(grep -rn "LocalDateTime\.ofInstant" "$M" --include=*.kt | wc -l)

# PW-n 出网值必须整点对齐（档位由 plusDays 给，不含分/秒）：
# 一旦这个键每次都不同，`Repo.userCreateKey`（SHA-256 覆盖报文每个键值）就会每次换一把幂等键
# ⇒ 「同一份寄货单还在创建中」那道本地闸拦不住连点两次。用例见 PickupWindowContractTest 那条对齐断言。
PN_ALIGN=$(nc_count "$PW" '.atTime(hour, 0)')
PN_TIMEUNIT=$(grep -c "plusHours(\|plusMinutes(\|plusSeconds(" "$PW")
PN_DAYSHIFT=$(grep -c "plusDays" "$PW")

fail=0

[ "$PA_LINES" = 8 ] || { echo "× PW-a 这个键在生效代码里不再是 8 行（现 $PA_LINES）：键的落点变了，本判据的其它读数不可信"; fail=1; }
[ "$PA_FILES" = 4 ] || { echo "× PW-a 命中文件数不是 4（现 $PA_FILES）：多出来的那个文件在替后端决定这一格的形状"; fail=1; }
[ "$PA_STRAY" = 0 ] || { echo "× PW-a Repo/WalletRows 里出现了这个键（现 $PA_STRAY）：正对照 $PA_PROOF 证明扫描是通的，所以这是真越界"; fail=1; }
[ "$PB_CALL" = 1 ] || { echo "× PW-b 出网值不再是唯一写法 pickupSubmitMillis()（现 $PB_CALL，键出现 $PB_TO 处）：多出来的写法会绕过'过去不发'那道闸"; fail=1; }
[ "$PB_STRING" = 0 ] || { echo "× PW-b 界面把这一键拼成了字符串（现 $PB_STRING）：后端 TimestampLocalDateTimeDeserializer 对非数字串静默返回 0，会存成 1970 年而不是报错"; fail=1; }
[ "$PC_DEF" = 1 ] || { echo "× PW-c 服务时区的唯一定义没了（现 $PC_DEF）：全工程 Asia/Shanghai 命中 $PC_ALL 处"; fail=1; }
[ "$PC_DEVICE" = 0 ] || { echo "× PW-c 用上了设备默认时区那套 API（现 $PC_DEVICE）：正对照 $PC_DEVICE_PROOF 证明扫描是通的；换时区的设备会把'今天 15:00'算成别的时刻发出去"; fail=1; }
[ "$PD_FILTER" = 1 ] || { echo "× PW-d 候选不再按'现在'过滤（现 $PD_FILTER）：过去的整点会回到候选里"; fail=1; }
[ "$PD_NULLGUARD" = 1 ] || { echo "× PW-d 档位→毫秒那一格不再拒过去（现 $PD_NULLGUARD）"; fail=1; }
[ "$PD_DISABLE" = 0 ] || { echo "× PW-d 改成'置灰还能点'了（现 $PD_DISABLE）：正对照 $PD_CLICK 证明 clickable 确实在扫范围内"; fail=1; }
{ [ "$PE_UNSET" = 1 ] && [ "$PE_ABSENT" = 1 ] && [ "$PE_NOTE" = 1 ]; } || { echo "× PW-e 三句话不再是各自唯一出处（$PE_UNSET/$PE_ABSENT/$PE_NOTE）：两处文字迟早对不上"; fail=1; }
[ "$PE_PAGE" = 0 ] || { echo "× PW-e 页面重抄了字面（现 $PE_PAGE）：正对照 $PE_PAGE_PROOF 证明页面是在引用常量"; fail=1; }
[ "$PF_NUM" = 1 ] || { echo "× PW-f 回读不再是只认数字（现 $PF_NUM）"; fail=1; }
[ "$PF_MULTI" = 0 ] || { echo "× PW-f 又用 multiNum 读这一键（现 $PF_MULTI）：它会把 '2026-10-13 15:00:00' coerce 成 0，屏幕上就是 1970 年"; fail=1; }
{ [ "$PG_DEF" = 1 ] && [ "$PG_CALL" = 1 ]; } || { echo "× PW-g 回执三格不齐（定义 $PG_DEF / 调用 $PG_CALL）"; fail=1; }
[ "$PG_STATE" = 4 ] || { echo "× PW-g pickupReceipt 的生效行不是 4（现 $PG_STATE）：声明/赋值/判空/渲染少了一环就不显示"; fail=1; }
[ "$PH" = 17 ] || { echo "× PW-h 契约用例不是 17 条（现 $PH）：下限等于现读，改了要连同本行一起改（16→17 = 整点对齐那条幂等键用例）"; fail=1; }
{ [ "$PI_FIELD" = 1 ] && [ "$PI_ZERO" = 0 ]; } || { echo "× PW-i 回读字段不再可空（声明 $PI_FIELD / 塌零 $PI_ZERO，正对照 $PI_PROOF）：没给会被显示成 1970-01-01"; fail=1; }
[ "$PJ" = 1 ] || { echo "× PW-j 演示报文里带这个键的单数不是 1（现 $PJ）：要么两格没都演到，要么在批量造数"; fail=1; }
{ [ "$PK_PICKER" = 1 ] && [ "$PK_CHIP" = 4 ]; } || { echo "× PW-k 选择器接线不齐（入口 $PK_PICKER / chip $PK_CHIP，目标 1 与 4）"; fail=1; }
{ [ "$PL_READ" = 1 ] && [ "$PL_UNSET" = 0 ]; } || { echo "× PW-l 列表那一枚 chip 不再只念回读文本（正对照 $PL_PROOF / 现 $PL_READ / 越界 $PL_UNSET）：没给会被就近念成'不限制'"; fail=1; }
{ [ "$PM_UNDO" = 1 ] && [ "$PM_UNDO_BODY" = 1 ]; } || { echo "× PW-m 「不限制」那枚撤销出口没了或改写了（chip $PM_UNDO / 清空动作 $PM_UNDO_BODY，目标 1 与 1）：选了之后退不回去"; fail=1; }

[ "$PN_ALIGN" = 1 ] || { echo "× PW-n 出网值不再整点对齐（现 $PN_ALIGN）：这一键每次点击都不同的话，本地幂等键会被顶开，连点两次拦不住"; fail=1; }
{ [ "$PN_TIMEUNIT" = 0 ] && [ "$PN_DAYSHIFT" = 3 ]; } || { echo "× PW-n 纯层里混进了分/秒级位移（分秒级=$PN_TIMEUNIT / 正对照 plusDays=$PN_DAYSHIFT）：同上，幂等键会随每次点击漂移"; fail=1; }
[ "$PO_API34" = 0 ] || { echo "× PW-o 又出现 API 34 才有的换算（现 $PO_API34）：minSdk 26 上 lint 会判 NewApi，只跑 assembleDebug 挡不住"; fail=1; }
[ "$PO_PROOF" = 2 ] || { echo "× PW-o 正对照掉了（LocalDateTime 换算现 $PO_PROOF，目标恰好 2）：上面那个 0 可能是没扫到东西"; fail=1; }
if [ "$fail" = 0 ]; then echo "取件时间窗判据通过"; else echo "取件时间窗判据失败"; fi
exit "$fail"
