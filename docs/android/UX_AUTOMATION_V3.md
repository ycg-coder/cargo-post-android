# 司机端自动化与"界面零调试物"设计（2026-10-10，用户口径 v3）

## 用户原话要点
1. 司机登录 App 应**自动开启位置上报**；**发车之后自动开始导航**。
2. 去掉界面上「展开本机引擎详情」这类东西；**测试/排障需要的信息一律进日志**，最终界面不许看到开发味的东西。
3. App 要智能、自动、方便，按 2026 年的水准。

## 三条硬约束（自动化不能越过的线）
- **A1 自动 ≠ 替调度做决定**：自动进入导航时，目的地只能取 `nextNavigableStop(guide)`（后端 `visitSequence` 里第一个未完成且有坐标的任务点）。没有这样的点，就不进导航、不"随便挑一个终点"。这条是导航赛道的硬规则 3，`check_navigation_probes.sh` 的 N 族与 `mission_route_negative_controls.sh` 都钉着它。
- **A2 自动 ≠ 假上报**：位置上报只有在拿到真实定位且班次可确定时才写库；`Running.shiftId<=0` 时照旧"位置更新但不挂班次"，不许造班次号。
- **A3 自动 ≠ 静默要权限**：没给定位权限时不能"自动"失败然后什么都不说。要么走系统授权弹窗，要么界面上只留**一句人话**（"打开定位后才能为你播报到站"），诊断细节进日志。

## 改动设计

### ① 登录即自动开位置上报（司机域）
- 落点：`ui/driver/WorkbenchScreen.kt` 的司机工作台（`vm.shift != null && !vm.demoted` 时）。
- 规则：`Prefs.role==DRIVER` 且 `ACCESS_FINE_LOCATION` 已授予 → `DriverLocationService.start(context)`；未授予 → **不自动弹**（首屏就弹会被当成骚扰），而是在"本班任务地图"卡上留一行引导，点一下才走 `askLocation`。
- 幂等：`start()` 可重复调用（服务内部按 shiftId 复位），同一次进壳只调一次（`LaunchedEffect(Unit)` + 一个 `remember` 标志）。
- 撤档降级路径不变：`vm.demoted` 时仍然 `DriverLocationService.stop`（三件同块的既有约束不许动）。

### ② 发车后自动开始导航
- 落点：发车成功回执之后（`vm.depart()` 的 `act("发车")` 成功支），而不是页面里再加一个按钮。
- 规则：发车成功 → 重读 `guide` → 若 `navigationGateOf(readiness, hasOrigin, nextNavigableStop!=null).canNavigate` 为真 → `nav.navigate(Routes.DRIVER_GUIDE)`；否则**留在工作台**，界面只更新状态标签，不跳空页。
- 起点未就绪（还没拿到定位）时：不跳导航，等 fix 到位后由状态标签自然翻成"可用"；**不许**用假起点凑一次跳转。
- 退出导航回到工作台时不重复自动跳（一次性意图标志）。

### ③ 界面零调试物：把诊断搬到日志
- 删（界面层）：导航卡的「展开更多（定位 · 路线 · 里程 · 车道）」整块折叠区、行标签「导航」里念 `gate.reason` 原文的那一格、「任务点 N 个 · 顺序由调度下发 · 道路里程接口未给 · 来源 AMAP」这一整条统计串里的工程部分。
- 留（界面层，属于诚实口径，不许删）：「直线估算，不是道路里程」「导航暂不可用：还没拿到当前位置」这类**用户能理解且必须知道**的口径句；目的地站名；剩余里程/到达时间（高德给的真值）。
- 进日志：被删掉的诊断全部改走 `Log.i("BUS_NAV"/"BUS_UI", ...)`，字段化输出（`readiness`、`gate.code`、`hasOrigin`、`nextStopId`、`provider`、`remainMeters`、`stopCount`、`routeSource`）。排障时 `adb logcat -s BUS_NAV BUS_UI BUS_LOC` 一条不漏。
- 界面上「任务点 N 个」这类统计要成人话：改成「本班 33 站 · 下一站 四公里交通换乘枢纽站」这种司机能用的信息，来源/接口那类词不出现。

## 判据与用例耦合（改之前必须先核的账）
| 要动的串/结构 | 被谁钉 | 同轮必须改 |
|---|---|---|
| 「任务点 N 个 · 顺序由调度下发 · 道路里程接口未给 · 来源 AMAP」 | `Map3DVisualContractTest`、`MissionMapTest` | 两条用例断言 |
| 「不画任何示意线」 | `check_navigation_probes.sh`、`check_mission_route_probes.sh`、`mission_route_negative_controls.sh`（注入锚点=源码文本）、`RouteOfflineCacheContractTest` | 判据 pattern + 负控锚点 + 用例 |
| 「展开更多 / 本机引擎」那一族 | 本轮已核：`app/src/test` 与 `tools/` **0 命中** ⇒ 可自由删 | — |
| 发车后自动跳导航（新行为） | 无现成判据 ⇒ 需要**新用例 + 新负控**（否则这条自动化是"没有闸门的行为"） | 新增 `DriverAutoNavContractTest` + 对应负控 |
| 登录自动开上报（新行为） | 无现成判据 ⇒ 同上 | 新增用例（权限已授予→start 被调；未授予→不调且不弹） |

## 验收（每条都要真机读数）
1. 真机 `pm clear` 后：登录司机号 → 不碰任何开关 → `adb logcat -s BUS_LOC` 出现 `WGS84→GCJ02` 出点、工作台上报卡显示真实坐标与精度。
2. 点「确认发车」→ 自动进入车道级导航页，界面出现高德真实算路的公里/分钟；`BUS_NAV` 日志里有 `gate.code` 与 `nextStopId`。
3. 全程截图逐屏：界面上 `grep` 不到「引擎 / 算路 / 回执 / 折线 / 接口 / 后端 / AMAP / 展开 / 示意线」这些词（脚本化断言，写进判据簿）。
4. 无定位权限的对照：不自动弹、不假跳，界面只有一句人话，日志里有 `readiness`/`gate.code`。
