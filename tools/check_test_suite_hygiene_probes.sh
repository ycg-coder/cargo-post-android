#!/usr/bin/env bash
# F-3 ⑱⑳ 测试套件卫生判据（队列 #151：指令点名的 10 本测试类逐本核"点的是哪一类缺陷"之后，
# 把由此立起来的两条纪律变成机器判据 —— **不许为凑数新建空测试类**、**不许把已有类改名冒充新增**）。
# 用法：bash tools/check_test_suite_hygiene_probes.sh     退出码非 0 = 这次收口破了
#
# 分工：每一本**行为用例**的条数仍由各域自己的判据簿钉（MR-a 12 / N-a ≥26 / DV-t5 ≥15 / …），
# 本册不重复数它们的用例，只钉三件那些簿管不到的事：
#   1. 叫 `*Test.kt` 的文件里有没有"一条 @Test 都没有"的空壳（凑数的形状）；
#   2. 有没有被 `@Ignore` 挂起来、从此永远绿的用例（假绿的形状）；
#   3. 归位表那张表本身 —— 点名的 10 个类名里 7 个"未新建"的必须继续不存在，
#      3 个同名存在的与 10 个落点类必须还在（落点被删或被改名，归位表就变成念空）。
#
# 写法沿用既有教训（对应负控制见 tools/test_suite_hygiene_negative_controls.sh）：
# · 先验目录存在 —— find 找不到目录时输出是空的，空串参与数值比较看起来像"跑过了"，其实根本没跑；
# · 每个"目标 0"都配一条**同机制反证**：同一套计数在别处必须数得出非 0，否则那个 0 只是模式没命中；
# · 反证用的读数钉**恰好**（夹具文件多一个就得显式改这里），下限型读数只用于"非零证明"，不当判据用。
set -u
ROOT="${BUSPOST_ANDROID:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT" || { echo "找不到 $ROOT"; exit 2; }

TSD=app/src/test/java/com/shanxiang/buspost
[ -d "$TSD" ] || { echo "× 测试源集目录不存在：$TSD（先确认工程根，别把缺目录读成计数 0）"; exit 2; }

# 点名名单（⑱⑳ 指令原文里的 10 个类名，逐字照抄，不改名、不意译）
ABSENT=(CStationContractTest RoutePageStateTest DriverTaskServerStateTest MissionPointMapperTest \
        NavigationReadinessTest NavigationRouteBindingTest RouteCacheGraphVersionTest)
PRESENT_NAMED=(DriverRoleIntegrityTest RouteDomainDeepLinkTest UserTrackingContractTest)
# 归位表落点（缺陷类真正住在那本里的 10 本；路径按本轮实测写，见 D-054 §2 那张表）
TARGETS=(data/NoProducerKeyFamilyContractTest.kt data/DistanceCaliberContractTest.kt \
         core/map/PolylineDecisionContractTest.kt ui/driver/DriverActionHonestyContractTest.kt \
         data/model/DriverLegRespContractTest.kt ui/driver/DriverLegStateMapperTest.kt \
         ui/driver/MissionMapTest.kt ui/driver/MissionRouteStateContractTest.kt \
         core/navigation/NavigationContractTest.kt core/map/RealRoadRouteValidatorTest.kt)

# SU-a 空壳闸：叫 *Test.kt 的文件里，@Test 计数为 0 的必须一个都没有
SUA_FILES=0; SUA_EMPTY=0; SUA_EMPTY_LIST=""
while IFS= read -r f; do
    SUA_FILES=$((SUA_FILES + 1))
    c=$(grep -cE '^[[:space:]]*@Test' "$f")
    if [ "$c" = 0 ]; then SUA_EMPTY=$((SUA_EMPTY + 1)); SUA_EMPTY_LIST="$SUA_EMPTY_LIST ${f##*/}"; fi
done < <(find "$TSD" -name '*Test.kt' | sort)

# SU-b 反证：同一套"@Test 计数"在整个源集上确实数得出"0 条"的文件（那两个是夹具/分类器，不是测试类）
# 基数 49 → 50 的唯一增量 = 赛道⑫⑬ 新增的 Map3DVisualContractTest.kt（现读 16 条 @Test，SU-a 那行 48/0 为证：
# 它是真带用例的一本，不是空壳）。这一格是"恰好 N"的摩擦设计（§八 ㉟ ⑥）：加文件必须同轮把数改对，不许调松闸。
# 基数 50 → 51 的唯一增量 = 赛道⑭ 新增的 data/repo/RouteOfflineCacheContractTest.kt（现读 **7** 条 @Test：
# 第 7 条钉"抓取时间来自写入那一次的时钟入参"，行为负控 B-5 只有靠它才有一格可红；同轮 SU-a 那行 49/**0**
# ⇒ 这一本不是空壳。两行一起读才成立，单看 51 什么都不能证明）。
# 基数 51 → 52 的唯一增量 = 队列 #262 新增的 data/model/DriverTaskRespContractTest.kt（现读 **10** 条 @Test，
# 同轮 SU-a 那行 50/**0** ⇒ 也不是空壳；这一本是 DV-y…DV-y6u 的行为半边，幻影键不塌值这件事只有它能钉）。
# 基数 52 → 53 的唯一增量 = 队列 #269 新增的 ui/user/ParcelCountContractTest.kt（现读 **4** 条 @Test：
# 137 顶本页 2 / 一支失败另一支照旧 / 后端真说 0 / 初值两格 null 且 requestCount=0；同轮 SU-a 那行 51/**0**
# ⇒ 这一本不是空壳。SU-b 的"无 @Test 夹具数"仍是 **2**（TestSupport.kt 与 verification/VerificationFailure.kt），
# 没有第三种文件混进来）。台账 D-063 §9。
# 基数 63 → 64 的唯一增量 = 司机端公告栏新增的 ui/driver/DriverNoticesContractTest.kt（gradle 产物实读
# tests=9 skipped=0 failures=0，2026-10-12T10:18:30 那一遍；同轮 SU-a 那行 62/**0** ⇒ 不是空壳，夹具数仍是 2）。
# 基数 55 → 56 的增量 = AutoGuideDecisionTest.kt（用户口径 A「自动进导航」的判定纯函数 + 四条具名用例）。
# 基数 53 → 55 的增量 = 队列 #305（到近分层）同轮新增的两本，ui/driver/ 下：
#   MissionApproachContractTest.kt —— 产物实读 tests=11 skipped=0 failures=0（2026-10-08T18:43:59 那一遍）；
#   ApproachFeedContractTest.kt    —— 产物实读 tests=11 skipped=0 failures=0（同一遍）。
# 两本一起加，所以 SU-a 那行同轮由 51/0 变 53/0：文件数 +2 而空壳仍 0，且 +2 的凭据是 gradle 产物里的
# testsuite 行，不是数 @Test 推的（数注解只证明"写了"，跑过才算数）。台账 D-068。
# 基数 56 → 61 的增量 = D-076（用户报的 App 这批）同轮新增的五本，每本的凭据都是 gradle 产物那行
# （2026-10-12T06:41 那一遍，59 份 XML 全为同一遍，无上一遍残留）：
#   data/repo/SessionRefreshContractTest.kt      tests=6 failures=0 errors=0 skipped=0（#46 静默续期）
#   ui/user/BusStateContractTest.kt              tests=7 failures=0 errors=0 skipped=0（#38 首屏状态位）
#   ui/user/ProfileAvatarContractTest.kt         tests=3 failures=0 errors=0 skipped=0（#44 头像上传）
#   notify/NotificationPrivacyContractTest.kt    tests=3 failures=0 errors=0 skipped=0（#47 锁屏隐私）
#   ui/driver/DriverWeatherContractTest.kt       tests=8 failures=0 errors=0 skipped=0（#42 天气分角色）
# 同轮 SU-a 那行 59/**0** ⇒ 五本都不是空壳（SU-c 同轮 0/**596**：@Ignore 归零且注解行数正好等于产物用例总数）；
# 基数 62 → 63 的唯一增量 = 队列 #49 内容核对后新增的 core/about/AppPoliciesContractTest.kt（产物实读 tests=8
#   failures=0，2026-10-12T09:53 那一遍）：它钉的是"隐私政策/用户协议不许与现实两张皮"，同轮 SU-c 由 0/**596** 变
#   0/**615**（+11 #48 判定层、+8 本簿）⇒ 三个数一起读才对得上。
# 基数 61 → 62 的唯一增量 = 队列 #48 新增的 core/update/AppUpdateContractTest.kt（产物实读 tests=11 failures=0，
#   2026-10-12T08:54 那一遍；SU-a 同轮由 59/0 变 60/0 ⇒ 不是空壳）。SU-b 的"无 @Test 夹具数"仍是 **2**（TestSupport.kt 与
# verification/VerificationFailure.kt），没有第三种文件混进来。台账 D-076。
SUSB_ALL=$(find "$TSD" -name '*.kt' | wc -l | tr -d ' ')
SUSB_NOTEST=0
while IFS= read -r f; do
    c=$(grep -cE '^[[:space:]]*@Test' "$f")
    [ "$c" = 0 ] && SUSB_NOTEST=$((SUSB_NOTEST + 1))
done < <(find "$TSD" -name '*.kt' | sort)

# SU-c 禁用用例闸 + 反证：@Ignore 归零；同一 grep 机制在 @Test 上数得出 476+
SUC_IGNORE=$(grep -rn '@Ignore' "$TSD" | wc -l | tr -d ' ')
SUC_TESTLINES=$(grep -rcE '^[[:space:]]*@Test' "$TSD" | awk -F: '{s+=$2} END {print s+0}')

# SU-d 点名的 7 本"未新建"必须继续不存在；3 本同名存在的必须还在
SUD_ABSENT=0
for n in "${ABSENT[@]}"; do
    [ -f "$TSD/core/$n.kt" ] || [ -f "$TSD/data/$n.kt" ] || [ -f "$TSD/data/repo/$n.kt" ] || \
    [ -f "$TSD/nav/$n.kt" ] || [ -f "$TSD/ui/driver/$n.kt" ] || [ -f "$TSD/core/map/$n.kt" ] || \
    [ -f "$TSD/core/navigation/$n.kt" ] || SUD_ABSENT=$((SUD_ABSENT + 1))
done
SUD_PRESENT=0
for n in "${PRESENT_NAMED[@]}"; do
    hit=$(find "$TSD" -name "$n.kt" | wc -l | tr -d ' ')
    [ "$hit" = 1 ] && SUD_PRESENT=$((SUD_PRESENT + 1))
done

# SU-e 归位表落点 10 本必须都在（少一本 = 那张表在念空）
SUE_PRESENT=0; SUE_MISSING=""
for t in "${TARGETS[@]}"; do
    if [ -f "$TSD/$t" ]; then SUE_PRESENT=$((SUE_PRESENT + 1)); else SUE_MISSING="$SUE_MISSING $t"; fi
done

echo "SU-a *Test.kt 文件数 / 其中 0 条 @Test 的空壳 = $SUA_FILES/$SUA_EMPTY （目标 ≥47/0：凑数空类归零）$SUA_EMPTY_LIST"
echo "SU-b 反证 源集 .kt 总数 / 无 @Test 的夹具数     = $SUSB_ALL/$SUSB_NOTEST （目标 64/2：同一机制数得出 0 条，SU-a 的 0 不是空跑）"
echo "SU-c @Ignore 生效行 / @Test 注解行（反证）      = $SUC_IGNORE/$SUC_TESTLINES （目标 0/≥476：不许有用例被挂起来永远绿）"
echo "SU-d 点名却未新建的类仍不存在 / 同名存在         = $SUD_ABSENT/$SUD_PRESENT （目标 7/3：建第 8 本空壳即红，撤掉那 3 本也红）"
echo "SU-e 归位表落点类存在                            = $SUE_PRESENT （目标 10：缺哪本会在行尾列出）$SUE_MISSING"

fail=0
[ "$SUA_FILES" -ge 47 ] || { echo "× SU-a *Test.kt 少于 47 本：有落点类被删了（归位表随之念空）"; fail=1; }
[ "$SUA_EMPTY" = 0 ] || { echo "× SU-a 出现 0 条 @Test 的 *Test.kt（=$SUA_EMPTY 本：$SUA_EMPTY_LIST）——为凑数新建的空壳"; fail=1; }
[ "$SUSB_ALL" = 64 ] || { echo "× SU-b 反证的源集文件数变了（$SUSB_ALL≠64）：先核对改动面再重测这条，别顺手改闸"; fail=1; }
[ "$SUSB_NOTEST" = 2 ] || { echo "× SU-b 反证掉了（无 @Test 的文件数=$SUSB_NOTEST≠2）：SU-a 那个 0 无法证明是归零还是没命中"; fail=1; }
[ "$SUC_IGNORE" = 0 ] || { echo "× SU-c 出现 @Ignore（$SUC_IGNORE 行）：被挂起的用例是永远绿的那种假绿"; fail=1; }
[ "$SUC_TESTLINES" -ge 476 ] || { echo "× SU-c 反证掉了（@Test 注解行数=$SUC_TESTLINES）：SU-c 的 0 无法证明机制命中过注解行"; fail=1; }
[ "$SUD_ABSENT" = 7 ] || { echo "× SU-d 点名未建的类少了一个不存在（=$SUD_ABSENT≠7）：有人新建了第 8 本凑数的类，或把已有类改了名"; fail=1; }
[ "$SUD_PRESENT" = 3 ] || { echo "× SU-d 同名存在的那 3 本不齐（=$SUD_PRESENT≠3）：归位表第一列开始念空"; fail=1; }
[ "$SUE_PRESENT" = 10 ] || { echo "× SU-e 归位表落点类缺失（=$SUE_PRESENT/10，缺：$SUE_MISSING）：缺陷类的住处被删或改名"; fail=1; }

if [ "$fail" = 0 ]; then echo "测试套件卫生判据通过"; else echo "测试套件卫生判据失败"; fi
exit "$fail"
