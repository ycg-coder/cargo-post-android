#!/usr/bin/env bash
# V2.1 点名要求普查（对应 docs/android/ANDROID_COMMAND_V2.1.md 三十五节）
# 目的：清单/台账里每一个 "已完成 / 未完成" 都能被这条命令当场复现，不靠回忆。
# 只读，不改任何源码。输出全 ASCII（控制台是 GBK，中文会花屏）。
#
# 两条防假绿装置（都是被实测坑过之后加的）：
#   1) PIPELINE 自检：计数管道若失去计数能力，grep 的 0 与"真的没有"长得一模一样 ⇒ 直接 exit 3。
#   2) 单文件 root 不传 --include：本机 grep 会把 --include 也套在命令行显式给出的文件上，
#      `.kts` 不在白名单里就会安静地返回 0（实测 compileSdk/targetSdk/navi-3dmap 三行全被吞）。
cd "$(dirname "$0")/.." || exit 2
SRC=app/src/main/java
TEST=app/src/test/java
MAN=app/src/main/AndroidManifest.xml
GRD=app/build.gradle.kts
OUT="${1:-/tmp/v21_census.txt}"
: > "$OUT"

if [ "$(printf 'aa bb\n' | grep -hoE 'bb' | wc -l | tr -d ' ')" != "1" ]; then
  echo "FATAL|PIPELINE_BROKEN|-o 计数管道不工作，本轮所有 0 一律不采信" | tee -a "$OUT"
  exit 3
fi
echo "GUARD|pipeline|measured=1|expect=1" >> "$OUT"

n() { # id label pattern [root=SRC]
  local id="$1" label="$2" pat="$3" root="${4:-$SRC}"
  if [ ! -e "$root" ]; then echo "$id|$label|ROOT_MISSING|$root" >> "$OUT"; return; fi
  local inc=(--include=*.kt --include=*.xml)
  [ -f "$root" ] && inc=()   # 显式文件不套 --include（见头部第 2 条）
  local hits files code
  hits=$(grep -rhoE "$pat" "$root" "${inc[@]}" 2>/dev/null | wc -l | tr -d ' ')
  files=$(grep -rlE "$pat" "$root" "${inc[@]}" 2>/dev/null | wc -l | tr -d ' ')
  # code= 去掉注释行之后的**生效行数**（口径与 hits 不同：hits 数出现次数、code 数行，不可互引）。
  # 加这一列的原因是被实测坑出来的：F-2 §四 修完之后 V21-23 的 hits 仍是 2，
  # 那两处是模型 KDoc 里"旧读法长什么样"的说明文字 —— 只有 hits 的清单会把修好的事读成没修。
  code=$(grep -rnE "$pat" "$root" "${inc[@]}" 2>/dev/null | grep -vE ':[0-9]+:[[:space:]]*(\*|//)' | wc -l | tr -d ' ')
  echo "$id|$label|hits=$hits|files=$files|code=$code" >> "$OUT"
}

# §一 要求的是"本轮重新扫描"这件事本身，可交付物是 tools/ 下的判据簿 ⇒ 数 .sh 不能走 grep --include=*.kt
echo "V21-01|tools/check_*probes.sh|count=$(ls tools | grep -cE '^check_.*probes\.sh$')|negative_controls=$(ls tools | grep -cE '_negative_controls\.sh$')" >> "$OUT"
n V21-02 "enum class RouteDomain"        'enum class RouteDomain'
n V21-02 "\bRouteDomain\b"                '\bRouteDomain\b'
n V21-02 "fun domainOf"                  'fun domainOf'
n V21-02 "fun canEnter"                  'fun canEnter'
n V21-02 "DRIVER_ONLY"                   'DRIVER_ONLY'
n V21-02 "USER_ONLY"                     'USER_ONLY'
n V21-02 "SHARED"                        'SHARED'
n V21-03 "旧角色污染写法(code=0 才算归零)" 'AppState\.setRole\(Role\.USER\)'
n V21-03 "enum class AppDomain"          'enum class AppDomain'
n V21-03 "activeDomain"                  'activeDomain'
n V21-03 "accountRole"                   'accountRole'
n V21-04 "startDestinationFor"           'startDestinationFor'
n V21-04 "BOOTSTRAPPING"                 'BOOTSTRAPPING'
n V21-04 "restoreSession"                'restoreSession'
n V21-04 "reconcileRole (三态)"          'reconcileRole'
n V21-04 "serverSaysDriver"              'serverSaysDriver'
n V21-05 "SessionSnapshot"               'SessionSnapshot'
n V21-05 "SessionManager"                'SessionManager'
n V21-06 "catch (x: Throwable)"          'catch \([a-z]: Throwable\)'
n V21-06 "catch (x: IOException)"        'catch \([a-z]+: IOException\)'
n V21-06 "catch (x: ApiException)"       'catch \([a-z]+: ApiException\)'
n V21-07 "attempt {"                     'attempt \{'
n V21-07 "catch (x: CancellationException)" 'catch \([a-z]+: CancellationException\)'
n V21-07 "safeApiCall"                   'safeApiCall'
n V21-07 "legacy apiCall expect 0"        '\bapiCall\b'
n V21-08 "NavigationService"             'NavigationService'
n V21-08 "NavigationSession"             'NavigationSession'
n V21-08 "NavigationEngines.create"      'NavigationEngines\.create'
n V21-08 "engine.release()"              '\.release\(\)'
n V21-09 "AcceptedRouteSession"          'AcceptedRouteSession'
n V21-09 "routeFingerprint"              'routeFingerprint'
n V21-09 "startGpsNavi"                  'startGpsNavi'
n V21-10 "generation"                    'generation'
n V21-10 "requestId"                     'requestId'
n V21-10 "errorCode"                     'errorCode'
n V21-10 "CompletableDeferred"           'CompletableDeferred'
n V21-11 "navi-3dmap in gradle"          'navi-3dmap' "$GRD"
n V21-11 "abiFilters"                    'abiFilters' "$GRD"
n V21-12 "compileSdk"                    'compileSdk' "$GRD"
n V21-12 "targetSdk"                     'targetSdk' "$GRD"
n V21-13 "RouteCacheEntry"               'RouteCacheEntry'
n V21-13 "LinkedHashMap"                 'LinkedHashMap'
n V21-13 "MAX_ENTRIES"                   'MAX_ENTRIES'
n V21-13 "expiresAt"                     'expiresAt'
n V21-13 "graphVersion"                  'graphVersion'
n V21-13 "Mutex"                         'Mutex'
n V21-13 "ConcurrentHashMap"             'ConcurrentHashMap'
n V21-13 "@Synchronized"                 '@Synchronized'
n V21-14 "NavigationSessionStore"        'NavigationSessionStore'
n V21-15 "isFromMockProvider"            'isFromMockProvider'
n V21-15 "fun heading"                   'fun heading'
n V21-16 "LocationQualitySelector"       'LocationQualitySelector'
n V21-17 "StartLocationResult"           'StartLocationResult'
n V21-17 "runCatching (main)"            'runCatching'
n V21-18 "permission launcher"           'requestMultiplePermissions|RequestPermission'
n V21-18 "BACKGROUND_LOCATION"           'BACKGROUND_LOCATION' "$MAN"
n V21-19 "primed"                        'primed'
n V21-19 "lastSeenNotifyId"              'lastSeenNotifyId'
n V21-19 "lastSeenDriverMsgId"           'lastSeenDriverMsgId'
n V21-20 "Result.retry"                  'Result\.retry'
n V21-20 "Result.failure"                'Result\.failure'
n V21-20 "Result.success"                'Result\.success'
n V21-20 "BackoffPolicy"                 'BackoffPolicy'
n V21-21 "autoRefresh"                   'autoRefresh'
n V21-21 "lastSuccessfulSyncAt"          'lastSuccessfulSyncAt'
n V21-22 "ContractParseException"        'ContractParseException'
n V21-22 "QuoteResult"                   'QuoteResult'
n V21-22 "Double? nullable amount"       'val amount: Double\?|val itemFee: Double\?|val distanceFee: Double\?'
n V21-23 'optBoolean("...", true)'       'optBoolean\("[^"]+", *true\)'
n V21-23 "inService"                     'inService'
n V21-24 "plannedMinutes"                'plannedMinutes'
n V21-24 "waitDepartureMinutes"          'waitDepartureMinutes'
n V21-24 "totalMinutes"                  'totalMinutes'
n V21-24 "plannedDurationMinutes"        'plannedDurationMinutes'
n V21-24 "walkMinutes"                   'walkMinutes'
n V21-25 "openBaidu"                     'openBaidu'
n V21-25 "CoordinateTransformService"    'CoordinateTransformService'
n V21-25 "BD09"                          'BD09|bd09'
n V21-26 "nearbyBuses(null)"             'nearbyBuses\(null\)'
n V21-27 "deepLink"                      'deepLink'
n V21-28 "uses-permission"               'uses-permission' "$MAN"
n V21-29 "UserRepository"                'UserRepository'
n V21-29 "DriverRepository"              'DriverRepository'
n V21-31 "delay(4_000)"                  'delay\(4_000\)|delay\(4000\)'
n V21-31 "stepIndex + 1"                 'stepIndex \+ 1'
n V21-31 "catch Throwable 正式代码"      'catch \([a-z]: Throwable\)'
n V21-33 "VerificationFailure"           'VerificationFailure'
n V21-33 "四类失败分型"                  'SOURCE_FAILURE|DEPENDENCY_FAILURE|ENVIRONMENT_FAILURE|DEVICE_UNAVAILABLE'
# ⚠ 上面两行**必须**是 0：F-2 第 7 步的红线就是"分型不进业务面"（构建失败发生时 App 进程不存在，
#   主面拿到它是零消费者的死类型，还会在 ApiException/NetworkException/Ack.error 旁边长出第四套真相）。
#   只看这两行的普查会把"落对了地方"读成"没做"，所以按真实落点补三行：test 源集 / tools 面 / 证据份数。
#   数 .sh 与 .txt 不能走 grep --include=*.kt（头部第 2 条同一条坑），这里一律手写计数。
echo "V21-33|VerificationFailure(test 源集)|hits=$(grep -rn 'VerificationFailure' "$TEST" --include=*.kt | wc -l | tr -d ' ')|files=$(find "$TEST" -name 'VerificationFailure*.kt' | wc -l | tr -d ' ')" >> "$OUT"
echo "V21-33|rules-table|rows=$(awk -F'\t' '!/^#/ && NF==3' tools/verification_failure_rules.txt | wc -l | tr -d ' ')|kinds=$(awk -F'\t' '!/^#/ && NF==3 {print $1}' tools/verification_failure_rules.txt | sort -u | wc -l | tr -d ' ')" >> "$OUT"
echo "V21-33|fixture-logs|total=$(ls tools/fixtures/verification-failure/*.log 2>/dev/null | wc -l | tr -d ' ')|positive=$(ls tools/fixtures/verification-failure/*.log 2>/dev/null | grep -vc 'negative-control')|consumers=$(grep -rl 'verification_failure_rules' tools app/src docs 2>/dev/null | wc -l | tr -d ' ')" >> "$OUT"
n V21-34 "SOURCE_COMPLETE"               'SOURCE_COMPLETE'
n V21-34 "DEVICE_VERIFIED"               'DEVICE_VERIFIED'
n V21-34 "DEVICE_UNAVAILABLE"            'DEVICE_UNAVAILABLE'

# 名单 = 指令 §三十 点名的 18 本 + 各轮显式补进来的新簿（⑦ VerificationFailureTest / ⑧ SafeApiCallContractTest /
#   ⑥ RouteDomainDeepLinkTest，收口 D-⑥-5：只在台账里写"新簿已建"而不进普查，present 就永远不 +1，
#   账面进度会被读成"没建"）。不在本名单的新簿不靠这里可见，由各自己的判据簿钉住 ——
#   本轮 DriverActionHonestyContractTest 走的是 `check_idempotency_probes.sh` 的 H-g/H-h/H-i（同 UserCreateIdempotencyTest 之于 U-*）。
for c in RouteDomainGuardTest ActiveDomainTest SessionBootstrapTest ApiCancellationTest \
         DemoExceptionIsolationTest NavigationSessionTest NavigationRouteBindingTest \
         AmapCalcGenerationTest RouteCacheExpiryTest RouteFingerprintTest MockLocationTest \
         LocationQualitySelectorTest NotificationDomainTest NotificationWorkerResultTest \
         TraceRefreshConcurrencyTest CoordinateTransformBaiduTest QuoteNullSafetyTest \
         NearbyBooleanContractTest VerificationFailureTest SafeApiCallContractTest \
         RouteDomainDeepLinkTest; do
  f=$(find "$TEST" -name "$c.kt" 2>/dev/null | head -1)
  if [ -n "$f" ]; then
    echo "V21-30|$c|present|tests=$(grep -cE '^[[:space:]]*@Test' "$f")" >> "$OUT"
  else
    echo "V21-30|$c|ABSENT" >> "$OUT"
  fi
done

echo "V21-32|gradlew-mode|$(stat -c '%A' gradlew 2>/dev/null)" >> "$OUT"
grep -c 'gradle' .gitignore >/dev/null 2>&1 && echo "V21-32|gitignore|present" >> "$OUT"
echo "SUMMARY|rows=$(grep -c '|' "$OUT")" >> "$OUT"
cat "$OUT"
