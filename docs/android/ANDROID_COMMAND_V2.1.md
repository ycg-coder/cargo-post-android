# Autonomous Engineering Command v2.1（Android 域）— 指令原文落档

created_at: 2026-09-30 17:4x +08:00（本文件写出时间，不是指令发出时间）
received_at: 2026-09-30（本轮会话内送达）
source: ChatGPT conversation
source_locator: 会话记录 1b28f920-9bf2-42d8-8147-f534664f61a4.jsonl 第 36019 行 / role=user / 正文 17904 字符
status: ACTIVE
verbatim: true —— 两条 MARKER 之间即指令正文，未重组、未改写、未增删节号、未改标点、未规范化空白
line_endings: 原样保留（会话正文本身是 CRLF：`\r\n` 1574 处、裸 `\n` 0 处 ⇒ 逐字节写出，未转成 LF）
body_sha256_as_stored: 4f9e226072b5c00b363383e2ff2317978a85f44078e7259e925c8c0d6bdca8e7
body_sha256_lf_normalized: 8488822aa81b58863f8d3d5e8a9e91e24508efa3dc4e7e60c0410b8c8a198ef5（同一正文把 CRLF 折成 LF 后的哈希，供跨平台比对用）
section_count: 35（实测正则 `^([一二三四五六七八九十百]{1,4})、` = 35，首 `一`、末 `三十五`）

## 这份文件存在的唯一理由

- 终结「段号凭记忆」。`CONTEXT_LOCK.md` §十 记过两次现场事故：按记忆写 §125 而原文是 §100；
  写过 §166–167 而那份文档最大段号只有 130。此后**引用本指令任何小节，先在本文件 grep 到标题原文，再照抄编号与标题**。
- 完成度只走 `ANDROID_V2.1_CHECKLIST.md` 的 COMMAND_ID，不写裸 §xxx。
- ⚠ 与 v2.0（桌面 docx，§0–§130，审计 §70 做过三色对表）是**两版不同的指令**：
  v2.0 用阿拉伯数字段号、v2.1 用中文数字小节号，两套编号**不可互相引用**。

## 与本轮新指令（F-2，二十节）的关系

- F-2 是**追加**的作战单，不是 v2.1 的替换：它把 v2.1 里已完成的条目（二～五、八～十二、十三～十八、二十～二十三、
  二十五～二十七、二十九、三十一～三十四的一部分）标成"不要重做"，并把剩余项重排成 18 步执行序。
- 因此下面正文**一字未动**；F-2 的逐项对应与状态在 `ANDROID_V2.1_CHECKLIST.md`。

<!-- BEGIN VERBATIM -->
你现在继续基于当前最新 Android 工程执行。

不要重新设计整个 App。
不要推翻现有 USER / DRIVER 双域。
不要修改微信小程序。
不要为了新增功能破坏已经完成的真实道路、GraphHopper、GPS、任务状态、防直线规则。

本轮目标不是“增加很多页面”，而是：

> 把当前 Android 源码里已经存在的架构缺陷、状态污染、导航生命周期、网络异常、缓存真实性、坐标边界和发布配置问题彻底收口。

绝对原则继续保持：

REAL ROAD ONLY
NO STRAIGHT LINE
NO FAKE NAVIGATION
NO FAKE ETA
NO FAKE LANE
NO DEMO RELEASE
SERVER AUTHORITATIVE

==================================================
一、先重新扫描当前工程，不要相信旧报告
===================

读取：

CONTEXT_LOCK.md
AI_ITERATION_STATE.md
DECISION_LOG.md

重点读取：

app/src/main/java/com/shanxiang/buspost/ui/BusPostRoot.kt
app/src/main/java/com/shanxiang/buspost/nav/ShellDecision.kt
app/src/main/java/com/shanxiang/buspost/ui/driver/DriverShell.kt
app/src/main/java/com/shanxiang/buspost/ui/driver/WorkbenchScreen.kt
app/src/main/java/com/shanxiang/buspost/ui/driver/DriverSdkNavCard.kt
app/src/main/java/com/shanxiang/buspost/core/navigation/AmapNavigationEngine.kt
app/src/main/java/com/shanxiang/buspost/core/navigation/NavigationEngine.kt
app/src/main/java/com/shanxiang/buspost/core/map/RouteRepository.kt
app/src/main/java/com/shanxiang/buspost/core/loc/Locator.kt
app/src/main/java/com/shanxiang/buspost/core/loc/DriverLocationService.kt
app/src/main/java/com/shanxiang/buspost/core/net/Api.kt
app/src/main/java/com/shanxiang/buspost/data/repo/Repo.kt
app/src/main/java/com/shanxiang/buspost/data/store/Prefs.kt
app/src/main/java/com/shanxiang/buspost/ui/common/ScreenViewModel.kt
app/src/main/java/com/shanxiang/buspost/notify/AlertEngine.kt
app/src/main/java/com/shanxiang/buspost/notify/NotifyWorker.kt

然后全项目搜索：

catch (e: Throwable)
catch (e: Exception)
runCatching
DemoData
delay(
stepIndex
fake
mock
straight
Haversine
0.0
optBoolean
minutes
eta
route
navigation
setRole(Role.USER)
deepLink
SharedPreferences

==================================================
二、P0：彻底修复 USER / DRIVER 域隔离
===========================

当前 ShellDecision 只限制 DRIVER_ONLY，没有 USER_ONLY。

这意味着：

DRIVER 登录
+
外部 deep link
+
直接进入普通 USER 路由

仍存在可能性。

必须建立正式：

RouteDomain

例如：

USER
DRIVER
SHARED

然后给每条路由明确归属。

例如：

USER_ONLY:
SEND
BUS
ORDERS
PARCEL
NOTIFICATIONS
PROFILE
ADDRESS
ADDRESS_SELECT
FEEDBACK
SETTINGS
GOODS_DETAIL
GOODS_TRACE
ORDER_DETAIL
ADDRESS_EDIT
TRACE
BUS_DETAIL

DRIVER_ONLY:
DRIVER_SHELL
DRIVER_MESSAGES
HANDOVER
DRIVER_EXCEPTION
以及所有未来司机业务路由

SHARED:
LOGIN
必要的通用设置页面

必须实现：

fun domainOf(route: String): RouteDomain

fun canEnter(
route: String,
activeDomain: AppDomain
): Boolean

任何 deep link、通知、外部 Intent、导航动作都必须经过这个 guard。

如果 DRIVER 当前仍允许查看 USER 域：

不要修改 account role，
只允许进入一个受控的 UserMode。

如果产品决定 DRIVER 完全不能进入 USER 域，
则彻底拦截。

以当前产品需求：

“用户也是 App，司机也是 App，方便统一管理”

推荐：

一个 APK
两个业务域
账号角色保持真实
Active UI Domain 可以临时变化

不要把 account role 改成 USER。

==================================================
三、P0：修复 DriverShell 的严重角色污染
===========================

当前 DriverShell 存在：

AppState.setRole(Role.USER)

这会导致：

账号本身其实仍然是 DRIVER
但本地 UI 认为自己已经是 USER

同时后台：

DriverLocationService
Driver notifications
Driver state

仍然可能继续运行。

这是错误的两个状态源。

改成：

AccountRole
ActiveDomain

例如：

enum class AppRole {
USER,
DRIVER
}

enum class AppDomain {
USER,
DRIVER
}

Session：

accountRole = DRIVER

UI：

activeDomain = USER

绝不修改：

Prefs.role = USER

司机的 accountRole 永远由服务器角色决定。

如果 DRIVER 有“用户版”入口：

只切换：

activeDomain = USER

后台司机服务继续知道：

accountRole = DRIVER

退出用户域后：

activeDomain = DRIVER

如果司机正在：

导航
驾驶班次
进行任务

切换 USER MODE 时至少给出明确保护逻辑：

有活动导航/在途任务时不得静默切换。

可以：

“当前正在执行司机任务，切换用户版不会停止定位和任务。”

或者直接禁止。

不要自动停止司机后台服务。

==================================================
四、P0：Root 启动角色不能只相信本地 Prefs
===========================

当前：

startDestinationFor(
Prefs.loggedIn,
Prefs.role
)

仍然会直接根据本地角色创建 Shell。

修改为：

SESSION_BOOTSTRAPPING

启动流程：

App
↓
读取本地 token
↓
显示 Loading/Splash
↓
restoreSession
↓
调用真实 /auth/me / profile
↓
获得真实 accountRole
↓
更新 SessionManager
↓
RoleRouter
↓
UserShell / DriverShell

在服务器确认之前不要进入正式业务 Shell。

异常时分级：

token 无效：
→ LOGIN

网络失败但本地 session 尚未失效：
→ 保留上次安全状态
→ 显示“正在恢复会话”
→ 不要随便降级 DRIVER→USER

服务器明确说没有 driver profile：
→ DRIVER 才可以降级 USER

未知：
→ UNKNOWN
→ 不允许擅自改变角色

必须保留：

“未知不等于否定”

这条规则。

==================================================
五、P0：重新整理 SessionManager
========================

不要让：

Prefs
AppState
Repo
各自维护一套角色。

建立唯一：

SessionManager

至少：

SessionState
SessionUser
AccountRole
ActiveDomain

例如：

data class SessionSnapshot(
val loggedIn: Boolean,
val userId: Long?,
val accountRole: AppRole?,
val driverId: Long?,
val activeDomain: AppDomain,
)

明确：

accountRole = 服务端身份
activeDomain = 当前 UI

这样后续：

USER
DRIVER
DISPATCHER
ADMIN

不会继续混在一起。

==================================================
六、P0：修复 Api.kt 的 Throwable 吞异常问题
================================

当前 debug/demo 分支有：

catch (e: Throwable)

禁止。

绝不能让：

IllegalStateException
NullPointerException
ClassCastException
LinkageError
AssertionError
程序错误

进入 DemoData。

只能捕获明确允许模拟的 transport/business failure。

推荐：

catch (e: CancellationException) {
throw e
}

catch (e: ApiException) {
if (demoAllowed && demoEligible(e)) {
return DemoData.respond(...)
}
throw e
}

catch (e: IOException) {
if (demoAllowed && demoEligible(e)) {
return DemoData.respond(...)
}
throw e
}

其他 Throwable：

直接抛出。

尤其：

OutOfMemoryError
LinkageError
AssertionError

绝对不能 demo fallback。

==================================================
七、P0：所有 suspend 层正确处理 CancellationException
===========================================

当前：

ScreenViewModel.work()

存在：

catch (Exception)

必须先：

catch (CancellationException) {
throw e
}

再：

catch (Exception)

同时审计 Repo.kt 所有：

catch(Exception)

和：

runCatching

只要在 suspend 上下文：

CancellationException 必须穿透。

禁止页面退出以后：

ViewModel 收到 cancellation
↓
被当成 error
↓
UI 显示失败

必须把 cancellation 与业务失败完全区分。

给常用网络请求建立统一 helper：

safeApiCall()

确保：

CancellationException
不会被吞掉。

==================================================
八、P0：重新设计 AMap Navigation 生命周期
==============================

当前 DriverSdkNavCard：

remember {
NavigationEngines.create(context)
}

然后：

DisposableEffect
↓
release()

这是不适合真正司机导航的。

导航引擎不是普通页面 UI 状态。

建立：

NavigationService

由：

Application / Driver domain lifecycle

持有。

推荐：

Singleton
或者
DriverNavigationController

页面只观察：

StateFlow

不要拥有：

AMapNavi

页面离开：

不自动 stop navigation

只有明确：

停止导航

才 stop。

建立：

NavigationSession

必须至少包含：

sessionId
taskId
routeFingerprint
startedAt
naviType
status

例如：

data class NavigationSession(
val sessionId: String,
val taskId: Long,
val routeFingerprint: String,
val startedAt: Long,
val status: NavigationStatus,
)

==================================================
九、P0：导航必须绑定当前真实路线
=================

当前：

calculateRoute()

得到 route

之后：

startGpsNavi()

没有明确保证：

startNavi()

启动的就是刚刚验证过的 route。

必须建立：

AcceptedRouteSession

流程：

calculate
↓
validator
↓
Accepted Route
↓
保存 routeFingerprint
↓
start navigation
↓
验证当前 session routeFingerprint
↓
允许启动

没有 accepted route：

禁止 startGpsNavi()。

任务发生变化：

routeFingerprint 变化
↓
旧导航 session 作废
↓
要求重新算真实道路

不能继续沿用旧路线。

==================================================
十、P0：修复 AMap 算路回调关联
===================

当前 pending：

CompletableDeferred<CalcOutcome>

只有一个槽。

这是可以暂时串行化的。

但必须增加：

requestId
generation

每一次 calculateRoute：

生成：

UUID

保存：

pendingRequestId

SDK 回调只能完成当前 generation。

同时：

onCalculateRouteSuccess()

必须验证：

errorCode == 0

否则：

RouteCheck.Rejected

禁止：

“成功回调”
但是 errorCode != 0
仍然读取 naviPath。

另外在发起新路线前：

清理旧的 route state

并确认读取到的：

naviPath

属于当前请求。

如果 SDK 没有可靠 request correlation：

保持“一次只允许一个请求”

并增加严格 generation guard。

宁可失败，不要读旧 path。

==================================================
十一、P1：AMap SDK 版本审计
===================

当前工程：

navi-3dmap:10.0.600_3dmap10.0.600

先不要盲目升级。

执行：

1. 检查本地真实 AAR/JAR
2. 检查 Maven 依赖树
3. 下载/验证官方当前版本
4. javap 当前实际 class
5. 逐个对比：
   AMapNavi
   SimpleNaviListener
   AMapCalcRouteResult
   NaviInfo
   AMapNaviPath
   lane APIs
   navigation view APIs
6. 写兼容 adapter
7. 再升级

官方当前 Android 导航 SDK 已是：

V11.3.100

官方同时说明：

V11.2.000 起不支持 32 位。

因此升级后必须同时检查：

abiFilters
armeabi-v7a
arm64-v8a

不能只改版本号。

最终目标：

如果真实设备验证通过：

升级到当前可兼容版本。

如果升级导致当前工程破坏：

暂时保持 10.0.600，
但记录：

AMAP_NAV_VERSION_BLOCKED

不能假装完成。

==================================================
十二、P1：真正处理 Android 16 配置
========================

当前：

compileSdk 35
targetSdk 34

检查整个工程是否可以安全提升到：

compileSdk 36
targetSdk 36

先分析：

AndroidManifest
Foreground Service
location
notification
PendingIntent
background launch
deep link
receiver

尤其 DriverLocationService。

如果兼容：

直接升级。

如果有第三方依赖阻塞：

记录真实 blocker。

不要为了“版本数字好看”强制升级导致 runtime crash。

==================================================
十三、P1：RouteRepository 不能继续无限期内存缓存
=================================

当前：

LinkedHashMap
MAX_ENTRIES=12

但是：

1. 没有 TTL
2. 没有 graphVersion 强校验
3. 没有 canonical fingerprint
4. 进程重启全部丢失
5. 非线程安全

改成最少：

RouteCacheEntry

包含：

fingerprint
source
graphVersion
origin
waypoints
destination
profile
distance
duration
fetchedAt
expiresAt
route

fingerprint 必须：

origin
+
waypoints
+
destination
+
profile
+
source
+
graphVersion

组成。

cached()

必须检查：

当前时间 < expiresAt

以及：

graphVersion 当前一致。

不一致：

cache miss

正式路线不允许返回过期路线。

第一阶段可以继续内存缓存，
但是必须线程安全。

优先：

Mutex

或者：

ConcurrentHashMap

不要裸 LinkedHashMap。

==================================================
十四、P1：路线缓存要区分“真实路线”和“导航会话”
==========================

不要让：

RouteCache

承担：

NavigationSession

两个职责。

拆成：

RouteRepository
NavigationSessionStore

RouteRepository：

“这条真实道路是什么”

NavigationSessionStore：

“司机现在正在导航哪条路线”

这样：

刷新 UI
不会导致
导航 session 丢失。

==================================================
十五、P1：Locator GPS 完整性
=====================

当前增加：

Location.isFromMockProvider

正式司机定位：

如果 mock：

不要作为正常司机轨迹上传。

至少：

mark suspicious

再由业务决定是否拒绝上传。

还要保存：

provider
accuracy
speed
bearing
timestamp
elapsedRealtime

heading()

不要返回：

0

代表 unknown。

改成：

Float?

因为：

0° 本身是合法北向。

没有 bearing：

null

==================================================
十六、P1：GPS provider 去重
=====================

当前：

GPS provider
NETWORK provider

可能重复发送两个位置。

建立：

LocationQualitySelector

优先：

更高 accuracy
更新鲜
速度/方向数据更完整

并加：

dedup distance
dedup time

司机轨迹不要因为 NETWORK/GPS 来回切换产生明显抖动。

==================================================
十七、P1：DriverLocationService.start() 禁止吞异常
=========================================

当前：

runCatching

启动失败后调用方拿不到明确原因。

改成：

sealed result：

StartLocationResult

Started
PermissionDenied
BackgroundStartRestricted
NotificationDenied
ServiceStartFailed

页面显示真正原因。

不要：

catch
→ nothing
→ 司机以为 GPS 已开启

==================================================
十八、P1：权限流程完善
============

检查：

FINE_LOCATION
COARSE_LOCATION
BACKGROUND_LOCATION
POST_NOTIFICATIONS
FOREGROUND_SERVICE_LOCATION

司机首次进入司机域：

明确展示：

定位权限
精准定位
通知权限
后台持续记录说明

不要一次打开 App 就全部弹。

按实际操作触发。

尤其：

后台定位

必须给出明确引导。

==================================================
十九、P1：AlertEngine 完全按域隔离
========================

当前 AlertEngine 一开始就调用：

Repo.notifications()
Repo.unreadCount()

之后才判断 DRIVER。

改成：

USER：

只调用 USER notification

DRIVER：

只调用 DRIVER message / notification

不要让 DRIVER 每次 Worker 都打 USER notification API。

同时检查：

lastSeenNotifyId
lastSeenDriverMsgId

不要使用一次性：

primed

直接把历史消息全部标掉。

正确策略：

首次安装：

可以记录 firstInstallAt

之后只处理：

id > watermark

并且 watermark 只能在确认处理结果后推进。

==================================================
二十、P1：NotifyWorker 不能所有错误都 Result.success()
===========================================

检查：

doWork()

如果网络失败：

不要：

Result.success()

应该：

网络临时失败：
Result.retry()

认证彻底失效：
Result.failure()

业务无新消息：
Result.success()

并配置：

BackoffPolicy.EXPONENTIAL

避免网络故障造成无意义高频重试。

==================================================
二十一、P1：TraceScreen 修复刷新一致性
==========================

检查自动刷新：

while(autoRefresh) {
delay(...)
load()
}

必须避免：

自动刷新
+
手动刷新

同时执行多个请求。

加入：

Mutex

或者：

Atomic in-flight guard

新请求开始时：

取消/替换旧请求

不要让旧请求最后返回以后覆盖新数据。

同时：

updatedAgo

只能记录：

lastSuccessfulSyncAt

不能在失败请求后刷新“更新时间”。

UI 分成：

lastSuccessfulSyncAt
lastAttemptAt

用户只看到成功数据距今多久。

==================================================
二十二、P1：Repo parser 严禁“缺失就当 0”
=============================

重点审计：

QuoteResult

当前：

amount
itemFee
distanceFee

缺失时可能变：

0.0

必须改成：

Double?

或者：

ContractParseException

只有后端明确：

0

才可以是真 0。

同样检查：

ETA
distance
duration
count
stock

缺失 != 0

==================================================
二十三、P1：NearbyResult 严禁缺失 boolean 默认 true
========================================

当前：

inService = optBoolean(..., true)
located = optBoolean(..., true)

改成：

缺失：
null / unknown

只有后端明确 true：

true

后续 UI：

UNKNOWN
不要展示：

“运营中”
“已定位”

==================================================
二十四、P1：分钟字段五组 alias 做最终 contract audit
======================================

逐个检查后端：

plannedMinutes
minutes
offsetMinutes

waitDepartureMinutes
waitMinutes

totalMinutes
minutes
durationMinutes

plannedDurationMinutes
durationMinutes

walkMinutes

每一个别名必须找到：

producer

controller
VO
DTO
service
或者真实 mini-program 消费

找到才保留。

找不到就删除 alias。

禁止：

“可能以后会有”

parser 必须反映真实 contract。

==================================================
二十五、P1：百度外跳坐标修复
===============

当前：

openBaidu()

直接把 Geo 当百度坐标。

但项目坐标业务边界不是这样。

必须：

GCJ02
↓
BD09
↓
Baidu URI

统一走：

CoordinateTransformService

禁止在：

DriverSdkNavCard

手工加偏移量。

==================================================
二十六、P1：Splash 取消无意义附近公交请求
=========================

当前：

Repo.nearbyBuses(null)

启动时无定位也去请求。

改成：

Splash 只负责：

session restore
基础资源准备

附近公交等定位数据：

有首次 location
再请求

不要让闪屏等待：

地图
公交
定位

全部回来。

闪屏只保留合理上限。

==================================================
二十七、P1：Deep Link 安全
===================

不要：

deepLink string
↓
直接 nav.navigate()

必须：

parse
↓
whitelist route
↓
domain guard
↓
argument validation
↓
navigate

未知：

ignore

非法参数：

ignore

DRIVER → USER domain：

根据 ActiveDomain policy 决定

不能让外部 Intent 自己决定 Shell。

==================================================
二十八、P2：Manifest 权限审计
====================

逐项搜索真实使用：

ACTIVITY_RECOGNITION
WAKE_LOCK
BACKGROUND_LOCATION
RECEIVE_BOOT_COMPLETED
CAMERA
queries

没有真实使用：

删除权限。

需要真实使用：

写明对应 feature。

不要为了“以后可能用”长期保留高敏权限。

==================================================
二十九、P2：Repo 按领域拆分
=================

当前 Repo.kt 太大。

不要一次性重写。

逐步增加：

UserRepository
DriverRepository
OrderRepository
ParcelRepository
RouteRepository
LocationRepository
NotificationRepository

旧 Repo 先作为 façade。

逐步迁移。

不要一次大重构把现有业务打崩。

==================================================
三十、P2：建立严格测试
============

至少新增：

RouteDomainGuardTest
ActiveDomainTest
SessionBootstrapTest
ApiCancellationTest
DemoExceptionIsolationTest
NavigationSessionTest
NavigationRouteBindingTest
AmapCalcGenerationTest
RouteCacheExpiryTest
RouteFingerprintTest
MockLocationTest
LocationQualitySelectorTest
NotificationDomainTest
NotificationWorkerResultTest
TraceRefreshConcurrencyTest
CoordinateTransformBaiduTest
QuoteNullSafetyTest
NearbyBooleanContractTest

==================================================
三十一、硬门禁
=======

加入静态检查：

formal route
不能出现：

Haversine
straight line
mock geometry
demo geometry

作为正式路线来源。

导航不能出现：

delay(4000)
stepIndex + 1

正式代码不能：

catch(Throwable)
吞掉程序错误。

suspend 代码不能：

吞 CancellationException。

司机不能：

AppState.setRole(Role.USER)

这种 account role 污染必须清零。

==================================================
三十二、Build 配置
============

检查并修正：

gradlew executable permission

同时不要把：

.gradle
build/
app/build/
APK
mapping
intermediates
generated

作为源码发布包的一部分。

检查 .gitignore。

源码 ZIP 必须尽量只保留：

src
gradle
gradle wrapper
build files
docs
scripts

不要继续把巨量构建产物一起打包。

==================================================
三十三、验证流程
========

严格按：

1. static audit
2. patch
3. unit tests
4. lint
5. assembleDebug
6. 如果可用则 assembleRelease
7. real device verification

构建失败必须区分：

SOURCE_FAILURE
DEPENDENCY_FAILURE
ENVIRONMENT_FAILURE
DEVICE_UNAVAILABLE

不能笼统写：

“构建失败”

更不能把未运行测试写成通过。

==================================================
三十四、阶段结束状态
==========

全部完成后更新：

CONTEXT_LOCK.md
AI_ITERATION_STATE.md
DECISION_LOG.md

增加：

CURRENT_ANDROID_STATUS

必须分别记录：

SOURCE_COMPLETE
BUILD_VERIFIED
DEVICE_VERIFIED

三者不能混写。

只有源码完成：

不能写：

DEVICE_VERIFIED

没有实机：

必须写：

NOT VERIFIED ON DEVICE

==================================================
三十五、这轮不要做
=========

不要本轮直接：

1. 大规模迁移 Compose
2. 重写整个 Repository
3. 一次性加入 Valhalla
4. 一次性加入完整 MapLibre Navigation
5. 重做微信小程序
6. 修改核心调度算法
7. 改 GraphHopper 后端核心实现
8. 用任何 fake route 顶功能空缺

本轮重点就是：

“把当前 Android 已经写出来的功能变得可信、稳定、可继续扩展。”

执行原则：

Inspect
→ Patch
→ Test
→ Lint
→ Verify
→ Checkpoint
→ Continue

不要每修一个小问题就停下来等待确认。
<!-- END VERBATIM -->

## 复核命令（任何时候可重跑，证明这份落档没被动过）

```bash
cd cargo-post-android
# 1) 小节数（应为 35）
grep -acE '^([一二三四五六七八九十百]{1,4})、' docs/android/ANDROID_COMMAND_V2.1.md
# 2) 首末节号（应为 一 / 三十五）
grep -aoE '^[一二三四五六七八九十百]{1,4}、' docs/android/ANDROID_COMMAND_V2.1.md | sed -n '1p;$p'
# 3) 正文哈希：抠出两条 MARKER 之间的内容原样算（含 CRLF）
awk '/BEGIN VERBATIM/{f=1;next} /END VERBATIM/{f=0} f' docs/android/ANDROID_COMMAND_V2.1.md | sha256sum
# 4) 取某一节原文（例：第六节标题与其后 12 行）
grep -n -A12 '^六、' docs/android/ANDROID_COMMAND_V2.1.md
```
