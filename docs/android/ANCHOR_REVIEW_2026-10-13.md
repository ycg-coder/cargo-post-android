# 台账锚点复核（2026-10-13，D-095）——本轮处置与 #159 的输入

这份是 `ANCHOR_REVIEW_2026-10-12.md` 的续页。它**不是判据**，是「哪些锚点本轮改了、哪些本轮没动、为什么没动」的点名清单；判据在 `tools/check_doc_anchor_probes.sh`。

## 本轮基线（改之前，真身实测）

- `anchors=1438 OK=1006 ID-MISS=417 FILE-NOT-FOUND=15 OUT-OF-RANGE=0 带标识符=692 其中判 OK=260 可疑 aid=367`
- 在册表 `tools/fixtures/doc_anchor_pins.txt` 252 行 ⇒ 新增可疑 125 / 在册但这次不再可疑 10。

## 分类口径（可复现，不靠手感）

用**台账原句里贴着锚点的反引号代码片段**当判据（片段须含 `[A-Za-z]{4,}`、在该文件出现 ≤6 次、不是锚点文件自己的名字、离锚点 ≤90 字）：

| 分类 | 判据 | 本轮条数 | 处置 |
|---|---|---|---|
| **真漂移** | 片段**不命中**旧行窗口(±3)、**命中**候选行，且候选行距旧锚点 ≤150 | 114 | 逐条改行号（本轮入仓 65 条） |
| **锚点本就对** | 片段**命中旧行窗口** ⇒ 那一行确实还指着句子说的东西 | 11 | **不改**，登记为在册债（工具误配标识符） |
| **判不了** | 句中没有可定位片段 / 标识符已不在该文件 / 文件两仓都找不到 | 242 | 不改，留在 #159 |

两条**工具侧假报机制**（本轮没改工具，先点名，改它要配它自己的负控）：

1. 裸文件名提及没被抹掉：`Repo.kt:587` 被判给的词是 `Models`（原句写「`Models.kt` 四处 + `Repo.kt:587/947`」，`Models.kt` 后面没有行号 ⇒ `ANCHOR` 那条抹除规则没命中它）；
2. 与锚点文件同名的对象名：`AlertEngine.kt:69` 被判给 `AlertEngine`，而全文件只有 `object AlertEngine {`（第 20 行）有这个词 ⇒ 逼它落进窗口等于要求每个锚点都指向声明行。实测这两类共摘掉 **12 条** ID-MISS 记录（417→406），同时「带标识符」692→679 —— 也就是说这条改的是**精度**，不是把闸门放宽。

## 本轮入仓的重锚（65 条，逐条含片段证据）

| 台账位置 | 旧锚点 | 新锚点 | 证据片段 |
|---|---|---|---|
| `AI_ITERATION_STATE.md:219` | `Repo.kt:780` | `Repo.kt:830` | `C1 唯一候选行` |
| `AI_ITERATION_STATE.md:264` | `BusScreen.kt:262` | `BusScreen.kt:269` | `loadMyShippingOrders()` |
| `AI_ITERATION_STATE.md:1652` | `AppState.kt:124` | `AppState.kt:190` | `Prefs.role =` |
| `AI_ITERATION_STATE.md:2424` | `DriverMissionMap.kt:71` | `DriverMissionMap.kt:88` | `BuildConfig.AMAP_KEY.isNotBlank()` |
| `AI_ITERATION_STATE.md:2639` | `SendScreen.kt:209` | `SendScreen.kt:225` | `fresh = o.optBoolean("fresh")` |
| `AI_ITERATION_STATE.md:2641` | `Models.kt:469` | `Models.kt:497` | `plannedMinutes` |
| `AI_ITERATION_STATE.md:3662` | `Repo.kt:95` | `Repo.kt:107` | `C1 唯一候选行` |
| `ANDROID_PARITY_AUDIT_V2.md:355` | `Models.kt:387-391` | `Models.kt:482-486` | `freightStatusLabel` |
| `ANDROID_PARITY_AUDIT_V2.md:381` | `Prefs.kt:159-181` | `Prefs.kt:151-173` | `driverShiftId` |
| `ANDROID_PARITY_AUDIT_V2.md:789` | `Models.kt:1138-1141` | `Models.kt:1169-1172` | `fromLongitude` |
| `ANDROID_PARITY_AUDIT_V2.md:789` | `Models.kt:1153` | `Models.kt:1168` | `fromLatitude` |
| `ANDROID_PARITY_AUDIT_V2.md:850` | `Models.kt:715-725` | `Models.kt:834-844` | `byUserDistance()` |
| `ANDROID_PARITY_AUDIT_V2.md:1267` | `Api.kt:105` | `Api.kt:178` | `C1 唯一候选行` |
| `ANDROID_PARITY_AUDIT_V2.md:1328` | `AlertEngine.kt:53` | `AlertEngine.kt:78` | `Routes.DRIVER_MESSAGES` |
| `ANDROID_PARITY_AUDIT_V2.md:1864` | `DemoData.kt:425` | `DemoData.kt:490` | `Reachability` |
| `ANDROID_PARITY_AUDIT_V2.md:1938` | `AppSendOrderRespVO.java:96` | `AppSendOrderRespVO.java:100` | `C1 唯一候选行` |
| `ANDROID_PARITY_AUDIT_V2.md:1940` | `AppSendOrderRespVO.java:106` | `AppSendOrderRespVO.java:110` | `C1 唯一候选行` |
| `ANDROID_PARITY_AUDIT_V2.md:2220` | `DriverMissionMap.kt:71` | `DriverMissionMap.kt:88` | `C1 唯一候选行` |
| `ANDROID_PARITY_AUDIT_V2.md:2362` | `MineScreen.kt:164` | `MineScreen.kt:201` | `nav.navigate(Routes.DRIVER_SHELL)` |
| `CONTEXT_LOCK.md:215` | `DriverMissionMap.kt:71` | `DriverMissionMap.kt:88` | `BuildConfig.AMAP_KEY.isNotBlank()` |
| `CONTEXT_LOCK.md:1958` | `Models.kt:469` | `Models.kt:497` | `plannedMinutes` |
| `CONTEXT_LOCK.md:1995` | `Models.kt:535` | `Models.kt:560` | `enum class LocationSource` |
| `DECISION_LOG.md:1012` | `DriverMissionMap.kt:71` | `DriverMissionMap.kt:88` | `BuildConfig.AMAP_KEY.isNotBlank()` |
| `DECISION_LOG.md:1514` | `Api.kt:60` | `Api.kt:82` | `suspend inline` |
| `DECISION_LOG.md:1622` | `Models.kt:469` | `Models.kt:497` | `plannedMinutes` |
| `DECISION_LOG.md:2174` | `MinePages.kt:171` | `MinePages.kt:226` | `password = ""` |
| `DECISION_LOG.md:2402` | `Repo.kt:725` | `Repo.kt:787` | `carrierLocationSource` |
| `DECISION_LOG.md:2403` | `Repo.kt:740` | `Repo.kt:805` | `carrierTaskStatus` |
| `DECISION_LOG.md:2603` | `WorkbenchScreen.kt:180` | `WorkbenchScreen.kt:238` | `if (next != stored) AppState.setAccountR` |
| `DECISION_LOG.md:2640` | `AppState.kt:124` | `AppState.kt:179` | `Prefs.role` |
| `DECISION_LOG.md:2640` | `Repo.kt:171` | `Repo.kt:202` | `Prefs.role` |
| `DECISION_LOG.md:3028` | `BusScreen.kt:91` | `BusScreen.kt:240` | `nearbyBuses` |
| `DECISION_LOG.md:3126` | `MainActivity.kt:60` | `MainActivity.kt:65` | `routeFromDeepLink` |
| `DECISION_LOG.md:3175` | `BusPostRoot.kt:87` | `BusPostRoot.kt:99` | `deepLinkTarget` |
| `DECISION_LOG.md:3541` | `AmapNavigationEngine.kt:357` | `AmapNavigationEngine.kt:471` | `navi.naviPath` |
| `DECISION_LOG.md:4809` | `Models.kt:1138-1141` | `Models.kt:1168-1171` | `fromLatitude` |
| `DECISION_LOG.md:4839` | `Repo.kt:873` | `Repo.kt:949` | `unreadCount` |
| `DECISION_LOG.md:5502` | `Repo.kt:1344` | `Repo.kt:1420` | `Api.upload` |
| `DECISION_LOG.md:5574` | `BusScreen.kt:224` | `BusScreen.kt:302` | `loadMyShippingOrders()` |
| `DECISION_LOG.md:5624` | `BusScreen.kt:990` | `BusScreen.kt:1055` | `C1 唯一候选行` |
| `DECISION_LOG.md:5839` | `BusScreen.kt:262` | `BusScreen.kt:269` | `loadMyShippingOrders()` |
| `DECISION_LOG.md:6031` | `Models.kt:1816` | `Models.kt:1956` | `Arrangement` |
| `DECISION_LOG.md:6275` | `Repo.kt:780` | `Repo.kt:830` | `C1 唯一候选行` |
| `ANDROID_V2.1_CHECKLIST.md:103` | `Models.kt:469` | `Models.kt:497` | `plannedMinutes` |
| `ANDROID_V2.1_CHECKLIST.md:107` | `Repo.kt:453` | `Repo.kt:492` | `userCreateGate` |
| `CONTRACT_EVIDENCE.md:148` | `Repo.kt:869-871` | `Repo.kt:950-952` | `unread-count` |
| `CONTRACT_EVIDENCE.md:150` | `Models.kt:1264` | `Models.kt:1406` | `actionRequired` |
| `CONTRACT_EVIDENCE.md:179` | `Models.kt:753` | `Models.kt:725` | `waitDepartureMinutes` |
| `CONTRACT_EVIDENCE.md:180` | `Models.kt:757` | `Models.kt:811` | `etaToNextStationMinutes` |
| `CONTRACT_EVIDENCE.md:182` | `Models.kt:763` | `Models.kt:736` | `stopsToNearestStation` |
| `CONTRACT_EVIDENCE.md:184` | `Models.kt:1381` | `Models.kt:1506` | `plannedDurationMinutes` |
| `CONTRACT_EVIDENCE.md:187` | `Repo.kt:1111` | `Repo.kt:1195` | `roadEtaSeconds` |
| `CONTRACT_EVIDENCE.md:194` | `AppSendLegRespVO.java:13-37` | `AppSendLegRespVO.java:62-83` | `durationMinutes` |
| `CONTRACT_EVIDENCE.md:197` | `Repo.kt:1110` | `Repo.kt:1235` | `nextStopRoadDistanceMeters` |
| `CONTRACT_EVIDENCE.md:213` | `Repo.kt:817` | `Repo.kt:897` | `multiBoolOrNull("inService")` |
| `CONTRACT_EVIDENCE.md:215` | `Repo.kt:816` | `Repo.kt:894` | `realTransitAvailable` |
| `CONTRACT_EVIDENCE.md:268` | `Repo.kt:1174` | `Repo.kt:1264` | `carrierLongitude` |
| `CONTRACT_EVIDENCE.md:271` | `Repo.kt:727` | `Repo.kt:789` | `carrierLocationSource` |
| `CONTRACT_EVIDENCE.md:272` | `Repo.kt:739` | `Repo.kt:804` | `lastReportTime` |
| `CONTRACT_EVIDENCE.md:273` | `Repo.kt:740` | `Repo.kt:805` | `carrierTaskStatus` |
| `CONTRACT_EVIDENCE.md:274` | `Repo.kt:741` | `Repo.kt:806` | `carrierArrivedTime` |
| `CONTRACT_EVIDENCE.md:275` | `Repo.kt:746` | `Repo.kt:809` | `carrierArrived` |
| `CONTRACT_EVIDENCE.md:276` | `Repo.kt:725` | `Repo.kt:786` | `positionSource` |
| `CONTRACT_EVIDENCE.md:434` | `Repo.kt:780` | `Repo.kt:830` | `C1 唯一候选行` |
| `CONTRACT_EVIDENCE.md:536` | `BusScreen.kt:237-239` | `BusScreen.kt:243-245` | `Repo.busLines(geo)` |

## 第二轮候选 58 条：本轮**不入仓**的理由

第二轮把取片段的范围从「贴着锚点 ±60 字」放宽到「整句、按离锚点由近及远」，条数从 56 涨到 114。
抽读时抓到一条会把锚点搬错的：`ANDROID_PARITY_AUDIT_V2.md:2450` 的 `DriverAppServiceImpl.java:1498`，那句讲的是 `if (curSeq < maxSeq) throw LEG_TRANSITION_ILLEGAL` 这个守卫（全文件该常量只出现在 **1577** 行），而放宽后的规则给它挑了 1529 行的 `case "exception" -> reportLegException(...)` —— 同一句里另一小段的代码。**证据强度不够就不改**：这一批留在下面当 #159 的输入，逐条人工判。

| 台账位置 | 旧锚点 | 机械候选 | 证据片段 |
|---|---|---|---|
| `AI_ITERATION_STATE.md:136` | `BusScreen.kt:657-659` | `BusScreen.kt:664-666` | `favLines.isNotEmpty()` |
| `AI_ITERATION_STATE.md:675` | `NotifyWorker.kt:24` | `NotifyWorker.kt:19` | `success` |
| `AI_ITERATION_STATE.md:1070` | `WorkbenchScreen.kt:795-853` | `WorkbenchScreen.kt:710-768` | `DriverTask` |
| `AI_ITERATION_STATE.md:1519` | `BusScreen.kt:207` | `BusScreen.kt:190` | `Asked` |
| `AI_ITERATION_STATE.md:1555` | `DriverSdkNavCard.kt:80-84` | `DriverSdkNavCard.kt:157-161` | `warm` |
| `AI_ITERATION_STATE.md:2200` | `AlertEngine.kt:33` | `AlertEngine.kt:136` | `grep` |
| `AI_ITERATION_STATE.md:2466` | `WorkbenchScreen.kt:434-440` | `WorkbenchScreen.kt:542-548` | `runCatching` |
| `AI_ITERATION_STATE.md:2645` | `BusScreen.kt:134` | `BusScreen.kt:190` | `Asked` |
| `AI_ITERATION_STATE.md:2646` | `Repo.kt:312` | `Repo.kt:409` | `.getOrDefault` |
| `AI_ITERATION_STATE.md:2872` | `ApiTest.kt:171` | `ApiTest.kt:167` | `inline` |
| `AI_ITERATION_STATE.md:3062` | `Repo.kt:466` | `Repo.kt:472` | `ActionGate` |
| `AI_ITERATION_STATE.md:3064` | `Repo.kt:1160` | `Repo.kt:1289` | `actionGate` |
| `AI_ITERATION_STATE.md:3512` | `MineScreen.kt:164` | `MineScreen.kt:112` | `Repo.bindDriver()` |
| `AI_ITERATION_STATE.md:3754` | `AppDriverController.java:258-263` | `AppDriverController.java:268-271` | `AppDriverPositionRespVO` |
| `AI_ITERATION_STATE.md:3976` | `DemoData.kt:644` | `DemoData.kt:672` | `/bus/realtime` |
| `AI_ITERATION_STATE.md:4117` | `DriverAppServiceImpl.java:1657` | `DriverAppServiceImpl.java:1688` | `setPhotoUrl` |
| `AI_ITERATION_STATE.md:4247` | `AlertEngine.kt:53` | `AlertEngine.kt:78` | `Routes.DRIVER_MESSAGES` |
| `ANDROID_PARITY_AUDIT_V2.md:426` | `AlertEngine.kt:25` | `AlertEngine.kt:39` | `Repo.unreadCount()` |
| `ANDROID_PARITY_AUDIT_V2.md:482` | `WorkbenchScreen.kt:178` | `WorkbenchScreen.kt:292` | `error = listOfNotNull(p.error, s.err` |
| `ANDROID_PARITY_AUDIT_V2.md:483` | `DriverMessagesScreen.kt:85` | `DriverMessagesScreen.kt:59` | `AppState.driverUnread` |
| `ANDROID_PARITY_AUDIT_V2.md:515` | `HandoverServiceImpl.java:222-223` | `HandoverServiceImpl.java:328-329` | `time` |
| `ANDROID_PARITY_AUDIT_V2.md:595` | `DriverLocationService.kt:93` | `DriverLocationService.kt:130` | `Prefs.driverId > 0` |
| `ANDROID_PARITY_AUDIT_V2.md:639` | `HandoverScreen.kt:132-133` | `HandoverScreen.kt:66-67` | `DemoNote` |
| `ANDROID_PARITY_AUDIT_V2.md:1108` | `MinePages.kt:171` | `MinePages.kt:92` | `DemoData` |
| `ANDROID_PARITY_AUDIT_V2.md:1792` | `AlertEngine.kt:48-53` | `AlertEngine.kt:78-83` | `route = Routes.DRIVER_MESSAGES` |
| `ANDROID_PARITY_AUDIT_V2.md:1962` | `Models.kt:739` | `Models.kt:801` | `routeId` |
| `ANDROID_PARITY_AUDIT_V2.md:2450` | `DriverAppServiceImpl.java:1498` | `DriverAppServiceImpl.java:1529` | `case "exception" -> reportLegExcepti` |
| `ANDROID_PARITY_AUDIT_V2.md:3306` | `WorkbenchScreen.kt:795-853` | `WorkbenchScreen.kt:710-768` | `DriverTaskSegment.group` |
| `CONTEXT_LOCK.md:1129` | `Bits.kt:39` | `Bits.kt:45` | `DemoData` |
| `CONTEXT_LOCK.md:1960` | `DemoData.kt:644` | `DemoData.kt:672` | `/bus/realtime` |
| `CONTEXT_LOCK.md:1963` | `Repo.kt:587` | `Repo.kt:645` | `QuoteResult` |
| `CONTEXT_LOCK.md:1969` | `BusScreen.kt:134` | `BusScreen.kt:259` | `if (shouldAskPolyline(selected?.id, ` |
| `CONTEXT_LOCK.md:1971` | `Repo.kt:312` | `Repo.kt:392` | `runCatching` |
| `DECISION_LOG.md:326` | `WorkbenchScreen.kt:213` | `WorkbenchScreen.kt:343` | `GeoMath` |
| `DECISION_LOG.md:582` | `DriverAppServiceImpl.java:1657` | `DriverAppServiceImpl.java:1688` | `setPhotoUrl` |
| `DECISION_LOG.md:1533` | `WorkbenchScreen.kt:434-440` | `WorkbenchScreen.kt:542-548` | `runCatching` |
| `DECISION_LOG.md:1704` | `Repo.kt:1160` | `Repo.kt:1289` | `actionGate` |
| `DECISION_LOG.md:2049` | `Bits.kt:39` | `Bits.kt:45` | `DemoData` |
| `DECISION_LOG.md:2417` | `Repo.kt:1174` | `Repo.kt:1185` | `geoOf` |
| `DECISION_LOG.md:2602` | `MineScreen.kt:97-98` | `MineScreen.kt:118-119` | `setAccountRole(Role.DRIVER)` |
| `DECISION_LOG.md:2604` | `BusPostRoot.kt:116` | `BusPostRoot.kt:141` | `AppState.setActiveDomain(AppDomain.o` |
| `DECISION_LOG.md:2731` | `Api.kt:232` | `Api.kt:211` | `Repo` |
| `DECISION_LOG.md:3126` | `Notifier.kt:69` | `Notifier.kt:92` | `Routes` |
| `DECISION_LOG.md:3385` | `Repo.kt:1097` | `Repo.kt:1167` | `routeGuideOf` |
| `DECISION_LOG.md:4809` | `Models.kt:1153` | `Models.kt:1168` | `fromLatitude` |
| `DECISION_LOG.md:5332` | `Notifier.kt:88` | `Notifier.kt:78` | `notify()` |
| `DECISION_LOG.md:5611` | `WorkbenchScreen.kt:573` | `WorkbenchScreen.kt:620` | `RequestPermission()` |
| `DECISION_LOG.md:5825` | `Models.kt:994` | `Models.kt:1003` | `val carrierEtaMinutes: Int? = null,` |
| `DECISION_LOG.md:6425` | `AlgorithmOrderDTO.java:74` | `AlgorithmOrderDTO.java:80` | `readyTime` |
| `DECISION_LOG.md:6525` | `BusScreen.kt:657-659` | `BusScreen.kt:664-666` | `favLines.isNotEmpty()` |
| `THIRD_PARTY_NOTICES.md:206` | `WorkbenchScreen.kt:434-440` | `WorkbenchScreen.kt:542-548` | `runCatching` |
| `ANDROID_V2.1_CHECKLIST.md:126` | `DriverShell.kt:38` | `DriverShell.kt:24` | `AppState.setActiveDomain(AppDomain.U` |
| `CONTRACT_EVIDENCE.md:131` | `Models.kt:1275` | `Models.kt:1410` | `levelOf` |
| `CONTRACT_EVIDENCE.md:195` | `Models.kt:1161` | `Models.kt:1105` | `/send/legs` |
| `CONTRACT_EVIDENCE.md:196` | `Models.kt:816` | `Models.kt:854` | `meters` |
| `CONTRACT_EVIDENCE.md:206` | `Repo.kt:580` | `Repo.kt:558` | `amount` |
| `CONTRACT_EVIDENCE.md:214` | `Repo.kt:826` | `Repo.kt:906` | `located` |
| `CONTRACT_EVIDENCE.md:216` | `Repo.kt:813` | `Repo.kt:893` | `buses` |

## 改过的句子里**没被复核**的裸行号（工具不查、本轮也没动）

`File.kt:NNN` 之外的 `:NNN` 形式（同句里跟着的第二个坐标）不在判据扫描面内。本轮入仓的 65 条重锚里，有 **27** 句同时带着这类裸号 ⇒ 它们与刚改过的锚点之间可能已经不一致，#159 要连句子一起读，不能只数锚点。

| 台账位置 | 本轮改的锚点 | 句内未复核裸号 |
|---|---|---|
| `DECISION_LOG.md:2402` | `Repo.kt:725`→`Repo.kt:787` | :727 |
| `DECISION_LOG.md:2403` | `Repo.kt:740`→`Repo.kt:805` | :746 :114 |
| `DECISION_LOG.md:3126` | `MainActivity.kt:60`→`MainActivity.kt:65` | :100 :127 |
| `DECISION_LOG.md:5839` | `BusScreen.kt:262`→`BusScreen.kt:269` | :266 :271 :269 :273 |
| `CONTRACT_EVIDENCE.md:215` | `Repo.kt:816`→`Repo.kt:894` | :372 |
| `CONTRACT_EVIDENCE.md:271` | `Repo.kt:727`→`Repo.kt:789` | :1205 :543 :473 :114 :148 |
| `CONTRACT_EVIDENCE.md:272` | `Repo.kt:739`→`Repo.kt:804` | :124 :1759 :122 |
| `CONTRACT_EVIDENCE.md:273` | `Repo.kt:740`→`Repo.kt:805` | :215 :171 |
| `CONTRACT_EVIDENCE.md:274` | `Repo.kt:741`→`Repo.kt:806` | :1230 :1759 :159 |
| `AI_ITERATION_STATE.md:2641` | `Models.kt:469`→`Models.kt:497` | :500 :1337 :1381 |
| `ANDROID_PARITY_AUDIT_V2.md:355` | `Models.kt:387-391`→`Models.kt:482-486` | :393 :110 :181 |
| `ANDROID_PARITY_AUDIT_V2.md:850` | `Models.kt:715-725`→`Models.kt:834-844` | :436 |
| `CONTEXT_LOCK.md:1958` | `Models.kt:469`→`Models.kt:497` | :18 :500 :1337 :1381 |
| `CONTEXT_LOCK.md:1995` | `Models.kt:535`→`Models.kt:560` | :554 :543 :632 :2824 :2523 :2535 |
| `DECISION_LOG.md:1622` | `Models.kt:469`→`Models.kt:497` | :500 |
| `DECISION_LOG.md:3028` | `BusScreen.kt:91`→`BusScreen.kt:240` | :124 |
| `ANDROID_V2.1_CHECKLIST.md:103` | `Models.kt:469`→`Models.kt:497` | :500 :1337 :1381 :18 |
| `ANDROID_V2.1_CHECKLIST.md:107` | `Repo.kt:453`→`Repo.kt:492` | :1160 :506 :516 :558 :559 :488 :464 |
| `CONTRACT_EVIDENCE.md:150` | `Models.kt:1264`→`Models.kt:1406` | :1275 :1276 :1277 :16 :19 :40 :34 :37 |
| `CONTRACT_EVIDENCE.md:179` | `Models.kt:753`→`Models.kt:725` | :136 :720 |
| `CONTRACT_EVIDENCE.md:182` | `Models.kt:763`→`Models.kt:736` | :667 |
| `CONTRACT_EVIDENCE.md:187` | `Repo.kt:1111`→`Repo.kt:1195` | :1157 :78 :83 |
| `CONTRACT_EVIDENCE.md:194` | `AppSendLegRespVO.java:13-37`→`AppSendLegRespVO.java:62-83` | :42 |
| `CONTRACT_EVIDENCE.md:197` | `Repo.kt:1110`→`Repo.kt:1235` | :81 :717 |
| `CONTRACT_EVIDENCE.md:213` | `Repo.kt:817`→`Repo.kt:897` | :532 :469 :69 :52 |
| `CONTRACT_EVIDENCE.md:268` | `Repo.kt:1174`→`Repo.kt:1264` | :102 :476 :439 :262 :371 :30 :106 :70 |
| `ANDROID_PARITY_AUDIT_V2.md:1938` | `AppSendOrderRespVO.java:96`→`AppSendOrderRespVO.java:100` | :249 |


## 改完之后的真身实测（不是沙盒预测值）

```
```

· 判据簿退出码：**1（仍红）**，红的只有 DA-e 那一格：`ID-MISS 321 > 闸门 286`。**下限一个字没动。**
· 转绿的格子：DA-f（`带标识符其中判 OK 260 → 356`，闸门 ≥277）、DA-d（在册表与真实可疑集对齐后 `新增=0 消失=0`）。
· 在册表 `252 → 282` 行：**下调 62 条**（本轮真修好的）、**新登记 92 条**（本轮判不了的，等 #159 消化）。
· 台账侧实际改动：65 条重锚映射落在 **110 行 / 114 个锚点串**（同一锚点在多处被引用时一起改，保持一致）。
· `FILE-NOT-FOUND` 仍是 15（= 闸门上限，未涨）、`OUT-OF-RANGE` 仍是 0。

## 配套改的一处负控注入

`tools/doc_anchor_negative_controls.sh:89` 原来靠 `sed 's/Models\.kt:469/…/'` 制造「在册锚点本次不再可疑」那条破口，而本轮把 `ANDROID_V2.1_CHECKLIST.md:103` 的 `Models.kt:469` 改成了 `497` ⇒ 旧注入串会**静默落空**（sed 找不到就不改，负控照样打 ✔，等于这条破口没上岗）。实测对照：同一份台账副本上，旧串 `:469` 改出 **0** 行差异、新串 `:497` 改出 **1** 行差异。已把注入串跟着改成 `497` 并 `bash -n` 过。
