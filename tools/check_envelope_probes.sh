#!/usr/bin/env bash
# 契约信封判据（P2-1，配合审计 §22/§23/§52/§66 与 CONTEXT_LOCK.md §八 ⑨）
# 用法：bash tools/check_envelope_probes.sh      退出码非 0 = 契约纪律破了
# 写法沿用 ⑥⑦ 的教训：**按内容/结构过滤，不按行号**（行号白名单会漂）。
# 每条都要有负控制：脚本自己必须能对"人为破口"报红，否则它只是装饰。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT/app/src/main/java" || { echo "找不到 $ROOT/app/src/main/java"; exit 2; }

KT=(--include=*.kt)
API=com/shanxiang/buspost/core/net/Api.kt
REPO=com/shanxiang/buspost/data/repo/Repo.kt
STATE=com/shanxiang/buspost/core/AppState.kt
HANDOVER=com/shanxiang/buspost/ui/driver/HandoverScreen.kt
MODELS=com/shanxiang/buspost/data/model/Models.kt
SIG=com/shanxiang/buspost/ui/driver/HandoverSignature.kt
SIGTEST=$ROOT/app/src/test/java/com/shanxiang/buspost/ui/driver/HandoverSignatureContractTest.kt
# 输入面守卫：这两个文件被搬走时，E-i 的每一格都会静默读 0 —— 而"读 0"与本簿要抓的"机制被拆"同形。
for f in "$SIG" "$SIGTEST"; do
  [ -f "$f" ] || { echo "× E-i 缺输入 $f：这一格的 0 是扫描面塌了，不是机制被拆"; exit 2; }
done

# E-a 未读角标只能有一个写入口：AppState 之外的 `.unread.value =` / `.driverUnread.value =` 必须为 0
#     （§52 的病根就是三个页面各写一次，恒 0 的那个把真值抹掉 → 红点"自己闪没"）
EA=$(grep -rn "${KT[@]}" "\.\(unread\|driverUnread\)\.value\s*=" . \
  | grep -v "core/AppState\.kt" | grep -vE ":[0-9]+: *\*" | wc -l | tr -d ' ')

# E-b 标量端点的读法必须走 Payload.longOf / Payload.strOf（先裸标量、后对象键）。
# 基数 3 → 5 的增量 = D-076 #46：登录与续期两条都要落 access token 的到期时刻，
#   后端 `AppAuthLoginRespVO.java:28 expiresTime` 是 LocalDateTime，而 yudao 用
#   `TimestampLocalDateTimeSerializer`（`YudaoJacksonAutoConfiguration.java:62`）序列化成 **epoch 毫秒**，
#   所以 `Payload.longOf("expiresTime")` 是这条的正确读法（两处：`Repo.kt:151` 登录存盘、`Repo.kt:195` 续期存盘）；
#   非数字/缺键时 `longOf` 只回 null ⇒ 落 0，用例 `expiresTime 不是数字时不得崩，也不得编一个到期时刻` 钉的就是这一格。
EB=$(grep -nE "longOf\(|strOf\(" "$REPO" | wc -l | tr -d ' ')

# E-c 旧的"按对象键读裸标量"写法不得残留（后端根本没给 count/unreadCount/url 这些键）
EC=$(grep -rn "${KT[@]}" "multiNum(\"count\", \"unreadCount\")" . | wc -l | tr -d ' ')

# E-d 交接状态不得在本机改写：后端 confirmHandover 是唯一权威（§26 修改方案 ②）
ED=$(grep -n "copy(status = \"已交接\"" "$HANDOVER" | wc -l | tr -d ' ')

# E-e 交接状态文案必须优先读后端的 statusName（status 是 0..7 的数字，直接渲染就是"0/4"）
#     注意只钉 HandoverTask 那一处：`status = o.multiStr("statusName",…)`；
#     另有一处同形读法在别的模型里（`statusLabel = …`），不是本判据的对象。
EE=$(grep -c 'status = o\.multiStr("statusName", "statusLabel", "status")' "$MODELS")

# E-f 凭证照片必须有回读出口：模型解析 photoUrl + 页面渲染 photoUrl，两处都在才算闭环（需求 §7）
EF=$(grep -rn "${KT[@]}" "photoUrl = o\.multiStr(\"photoUrl\"" com/shanxiang/buspost/data/model \
  | wc -l | tr -d ' ')
EF2=$(grep -c "url = task\.photoUrl" "$HANDOVER")

# E-g 照片上传失败必须拦停提交（后端对已完成的交接幂等 return，先提交就再也补不进凭证）
#     钉整句而不是"交接未提交"四个字——演示分支的文案里也有这四个字，会被误计。
EG=$(grep -c "凭证照片上传失败，交接未提交" "$HANDOVER")

# E-h 标量出口真的被填上了（toPayload 的非对象/非数组分支必须带 scalar）
EH=$(grep -c "scalar = data" "$API")

echo "E-a 角标外部直写        = $EA （目标 0）"
echo "E-b Repo 标量读法调用点  = $EB （目标 5：unreadCount / driverUnreadCount / uploadPhoto / 登录 expiresTime / 续期 expiresTime）"
echo "E-c 旧对象键读裸标量残留  = $EC （目标 0）"
echo "E-d 本机改写交接状态      = $ED （目标 0）"
echo "E-e 状态优先读 statusName = $EE （目标 1）"
echo "E-f 凭证照片回读出口      = $EF/$EF2 （目标 1/1：模型解析 + 页面渲染）"
echo "E-g 上传失败拦停文案      = $EG （目标 1）"
echo "E-h toPayload 填 scalar   = $EH （目标 1）"

fail=0
[ "$EA" = 0 ] || { echo "× E-a 有页面直接写角标，未读又将多源打架（§52 回潮）"; fail=1; }
[ "$EB" = 5 ] || { echo "× E-b 标量读法调用点不是 5（少了=有人退回对象键读法，多了=凭空多打接口）"; fail=1; }
[ "$EC" = 0 ] || { echo "× E-c 又用对象键去读后端裸标量，未读会重新恒 0"; fail=1; }
[ "$ED" = 0 ] || { echo "× E-d 本机把交接标成已交接，后端失败也会显示成功"; fail=1; }
[ "$EE" = 1 ] || { echo "× E-e 交接状态不再优先读 statusName，界面会渲染出数字状态"; fail=1; }
[ "$EF" = 1 ] || { echo "× E-f 模型不再解析后端 photoUrl，§7 的回读半边断了"; fail=1; }
[ "$EF2" = 1 ] || { echo "× E-f 页面不再渲染后端回读的 photoUrl，凭证有库也看不见"; fail=1; }
[ "$EG" = 1 ] || { echo "× E-g 照片上传失败不再拦停提交，凭证会永久丢失"; fail=1; }
[ "$EH" = 1 ] || { echo "× E-h 信封不再保留标量，§22 的恒 0/恒空全部回潮"; fail=1; }

# E-i 签收签名（#20 阶段 B）：模型解析 + 页面渲染 + 上传拦停 + 门槛在纯层，各钉一格。
#    为什么不并进 E-f：照片与签名是两列、两种语义（"货长什么样" / "谁认了这笔交接"），
#    并成一格就会让"照片在、签名没了"读起来和"两个都在"一样 —— V034 分列就是为了分开说（D-099）。
EI_MODEL=$(grep -c 'signUrl = o\.multiStr("signUrl"' "$MODELS")
EI_RENDER=$(grep -c "url = task\.signUrl" "$HANDOVER")
EI_BLOCK=$(grep -c "HandoverSignature.shouldBlockSubmit(signed" "$HANDOVER")
EI_GATE=$(grep -cE 'const val MIN_POINTS_PER_STROKE|const val MIN_STROKES' "$SIG")
# 同文件正对照：证明上面那几格不是"没扫到这个文件"
EI_PROOF=$(grep -c "InkPoint" "$SIG")
# E-i2 提交口两格必须分开传：并成一列就等于回到 V034 之前
EI_PHOTOKEY=$(grep -c '"photoUrl" to photoUrl,' "$REPO")
EI_SIGNKEY=$(grep -c '"signUrl" to signUrl,' "$REPO")
# E-i3 纯层用例条数（下限 = 现读 12，只许升不许降；D-085 ㊾①同一条规矩）
EI_TEST=$(grep -cE '^[[:space:]]*@Test' "$SIGTEST")

echo "E-i 签名 模型/渲染/拦停/门槛/正对照   = $EI_MODEL/$EI_RENDER/$EI_BLOCK/$EI_GATE/$EI_PROOF （目标 1/1/1/2/≥3：正对照是**下界**，只要证明扫描看得到那个文件；现读 3）"
echo "E-i2 提交口两格分开传（photo / sign） = $EI_PHOTOKEY/$EI_SIGNKEY （目标 1/1）"
echo "E-i3 签名纯层契约用例数               = $EI_TEST （目标 ≥12 = 现读）"

[ "$EI_MODEL" = 1 ] || { echo "× E-i 模型不再解析后端 signUrl：签名有没有入库在 App 侧失去出口"; fail=1; }
[ "$EI_RENDER" = 1 ] || { echo "× E-i 页面不再渲染后端回读的 signUrl：签名图有库也看不见"; fail=1; }
[ "$EI_BLOCK" = 1 ] || { echo "× E-i 签名上传失败不再拦停提交：已完成的交接补不进签名，凭证永久丢（与照片同一条事故路径）"; fail=1; }
[ "$EI_GATE" = 2 ] || { echo "× E-i 签成门槛的常量不齐（现 $EI_GATE/2）：少一个就是有人把「几划算签」搬回 UI 各写一套"; fail=1; }
[ "$EI_PROOF" -ge 3 ] || { echo "× E-i 同文件正对照掉了（InkPoint 在纯层出现 $EI_PROOF 次，现读 3）：上面那几个读数可能是没扫到这个文件"; fail=1; }
{ [ "$EI_PHOTOKEY" = 1 ] && [ "$EI_SIGNKEY" = 1 ]; } || { echo "× E-i2 提交口把照片与签名并成一格了（$EI_PHOTOKEY/$EI_SIGNKEY，目标 1/1）"; fail=1; }
[ "$EI_TEST" -ge 12 ] || { echo "× E-i3 签名纯层用例少于 12 条（现 $EI_TEST）：门槛、拦停、四分支回执、回读缺键没人钉"; fail=1; }

if [ "$fail" = 0 ]; then echo "契约信封判据通过"; else echo "契约信封判据失败"; fi
exit "$fail"
