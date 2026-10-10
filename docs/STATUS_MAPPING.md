# 状态码单一映射（审计 §18 / §8）

一句话规则：**状态码只有一个真值来源，就是后端枚举；Android 侧只有 `ServerStatus.kt` 这一张表。**
页面不许写 `status == 4` / `status >= 4` / `statusName.contains("完成")`，后端给了 `statusName` 就先用后端的，
本地表只负责两件事：**没给名字时兜底文案**，以及**回答"这个码能做什么"**。

- 表本体：`app/src/main/java/com/shanxiang/buspost/data/model/ServerStatus.kt`
- 判据（数字 + 退出码，按内容不按行号）：`tools/check_status_probes.sh`
- 钉住表格的契约测试：`app/src/test/java/com/shanxiang/buspost/data/StatusMappingContractTest.kt`
- 后端源码根：`cargo-post-platform/yudao-module-transport/src/main/java/cn/iocoder/yudao/module/transport/`

---

## 0. 为什么必须有这份文档（本轮实测的两处"注释骗人"）

后端的 **枚举** 和 **VO 注释** 不一致，注释是错的，枚举才是契约。已实测确认的两处：

| 位置 | 注释怎么写 | 枚举实际 | 后果（照注释写的代码） |
| --- | --- | --- | --- |
| `AppSendLegRespVO.java:22` | `0待分配 1已分配 2运输中 3已到达 4已交接 5异常` | `TransportLegStatusEnum` 0..11 + 99 | `4 = 前往起点` 被显示成"已完成"（`TraceView` 旧代码 `leg.status >= 4`） |
| `AppDriverHandoverRespVO.java:52` | 与 `TransportHandoverStatusEnum` 0..7 不同 | 见 §5 | 交接卡状态文案错位（P2-1 已登记，未改后端行为） |

> 所以：**任何"照 @Schema 注释排出来的表"都不可信**。核码只认 `enums/**` 下的枚举文件本身。
> 这两处注释错误已登记在 `AI_ITERATION_STATE.md`，属于后端文档问题，不在客户端"顺手改后端"。

## 1. 读法约定（与小程序同构）

小程序是 `o.statusName || statusText(o.status)`；Android 同构：

```kotlin
val statusLabel: String get() = statusName.ifEmpty { XxxStatus.labelOf(status) }
```

1. 后端 `*.nameOf(status)` 会填 `statusName`（所有 App 侧 VO 都填），**优先用它**；
2. 本地表只兜底，兜底口径必须与后端中文名一致（`StatusMappingContractTest` 逐码钉死）；
3. **未知码**：文案 = `状态暂不可用`（`STATUS_UNAVAILABLE_LABEL`），配色 = `NEUTRAL`。
   绝不回落成"0 的文案"，也不显示"完成/在途"这类强语义；
4. **缺键 ≠ 0**（§14）：`TransportLeg.parse` 与 `Repo.traceOf` 里 `status` 缺键读成 `-1`（未给），
   模型自己用 `-1` 当"后端没给"的哨兵，`0` 则是后端真实的第一个码。

## 2. 货运寄货单 `TransportOrderStatusEnum`（`/send/*`、`ParcelTrace`、`SendOrder`）

来源 `enums/dispatch/TransportOrderStatusEnum.java:19-45`；Android = `FreightOrderStatus`。
**审核前置三态 6/7/8 的码值比它们的流程位置大**，任何按数值排序流程的写法都会读反。

| 码 | 后端枚举 | 中文 | tone | 允许 | 禁止 |
| --- | --- | --- | --- | --- | --- |
| 0 | CREATED | 已创建 | NEUTRAL | 查看、取消 | 显示轨迹、催单 |
| 1 | POOLED | 已入池 | PRIMARY | 查看 | 显示承运车（还没派） |
| 2 | ASSIGNED | 已分配 | PRIMARY | 查看承运信息 | 显示实时位置 |
| 3 | DEPARTED | 已发车 | PRIMARY | 看轨迹/司机、联系司机 | 取消 |
| 4 | COMPLETED | 已完成 | OK | 看凭证 | **终态**：任何改状态的动作 |
| 5 | CANCELLED | 已取消 | DANGER | 看原因 | **终态**：任何改状态的动作 |
| 6 | PENDING_REVIEW | 待审核 | WARN | 等待 | 承诺可运 |
| 7 | WAITING_CUSTOMER_ACTION | 待客户操作 | WARN | 引导送到就近站点、确认 | 当作"已完成"（旧表就错在这里） |
| 8 | READY_FOR_POOL | 待入池 | WARN | 等待调度 | 显示承运车 |
| 9 | PARTIALLY_COMPLETED | 部分完成 | PRIMARY | 看分段进度 | 当成整单完成 |
| 10 | IN_TRANSIT | 运输中 | PRIMARY | 看实时轨迹 | 取消 |
| 11 | TRANSFERRING | 换乘中 | PRIMARY | 看交接 | 取消 |
| 12 | DELIVERING | 派送中 | PRIMARY | 联系司机、准备取货 | 取消 |
| 13 | EXCEPTION | 异常 | DANGER | 看原因、联系客服 | 显示正常进度 |

模型出口：`ParcelTrace.statusLabel/.tone/.cancelled`、`SendOrder.statusLabel/.statusTone/.isFinalStatus/.onTheWay/.needsCustomerAction`。
`isFinal` = 只有 4/5（抄后端 `isFinal()`）；`isOnTheWay` = 3/9/10/11/12。

**两套码不能混**：`ParcelTrace` 同时承载 `/send/track`（本表 0..13）和 `/product-order/trace`（§3 的 0..3）。
由取数处声明来源：`Repo.traceOf(..., fromProductTrace = true)`，页面不许猜。

## 3. 商城订单 `ProductOrderStatusEnum`（`/product-order/*`）

来源 `enums/transport/ProductOrderStatusEnum.java:14-20`；Android = `ProductOrderStatus`。

| 码 | 中文 | tone | 允许 | 禁止 |
| --- | --- | --- | --- | --- |
| 0 | 待发货 | WARN | 取消（`canCancel`） | 看溯源 |
| 1 | **已发货** | PRIMARY | 看溯源（`canTrace`）、实时位置 | 取消 |
| 2 | 已完成 | OK | 看溯源与交付凭证（`canTrace`/`isCompleted`） | 取消 |
| 3 | 已取消 | DANGER | 看原因 | 任何推进 |

> 旧本地表把 `1` 写成"配送中"，于是 `OrdersScreen` 的圆点、颜色、提示语一起错位（§33）。
> 现在 `ProductOrder.statusLabel/.statusTone/.onTheWay/.canTrace/.canCancel/.isCompleted` 是唯一出口。

## 4. 运输段 `TransportLegStatusEnum`（`/driver/legs`、`/send/legs`、`TransportLeg`）

来源 `enums/dispatch/TransportLegStatusEnum.java:24-36`；Android = `LegStatus`。

| 码 | 中文 | tone | 段语义 | 司机侧允许 |
| --- | --- | --- | --- | --- |
| 0 | 已规划 | NEUTRAL | 未开始 | — |
| 1 | 已分配 | NEUTRAL | 未开始 | `accept` |
| 2 | 司机已接单 | PRIMARY | 未开始 | `navigate`（**不能直接 `arrive-origin`**：2→5 不在 TRANSITIONS 里） |
| 3 | 等待发车 | PRIMARY | 未开始 | `navigate` |
| 4 | 前往起点 | PRIMARY | **未开始** | `arrive-origin` |
| 5 | 已到达起点 | PRIMARY | 未开始 | `load` |
| 6 | 装货中 | PRIMARY | 未开始 | `start` |
| 7 | 运输中 | PRIMARY | `isActive` | `arrive-dest` |
| 8 | 已到达终点 | PRIMARY | `isActive` | 需交接 → `handover-start`；不需交接 → 再发一次 `arrive-dest` 转派送。**不能 `complete`**：`canTransit(8, 11) = false` |
| 9 | 交接中 | WARN | `isActive` | `handover-confirm`（必须带凭证照片，见 §7） |
| 10 | 派送中 | PRIMARY | `isActive` | `complete` |
| 11 | 已完成 | OK | `isDone`（**唯一完成码**） | 无（终态） |
| 99 | 异常 | DANGER | 需人工 | 由调度重排，不许客户端自行"重试到 0" |

- 合法流转表在后端 `TransportLegStatusEnum.TRANSITIONS:42-55`（`canTransit:58-66`，同码幂等放行）；**客户端只读不推**。
- 后端动作集：`accept / navigate / arrive-origin / load / start / arrive-dest / handover-start / handover-confirm / complete / exception`
  （`DriverAppServiceImpl.java:1482-1500`）。中间段直接 `complete` 会被服务端守卫拒绝（`DriverAppServiceImpl.java:1538-1546`，
  `MultiLegServiceImpl.advanceLegStatus:700` 在 `:714` 调 `canTransit`，不过就抛 `LEG_TRANSITION_ILLEGAL`）。
- **本轮已修的假语义**：`TraceView` 旧 `leg.status >= 4 → 已完成`、`== 3 → 在途`；`RoutesScreen` 旧私有表
  `4 已完成 / 5 异常`（第三张表，已删除）。现在 `TransportLeg.statusLabel/.statusTone/.done/.active` 是唯一出口。
- **`exception` 的客户端闸门（§54，指令 v2.0）**：`DriverLegStateMapper.kt:88` 的
  `canReportException = status != null && status != COMPLETED && status != EXCEPTION` —— 已完成段与**已异常段都不再重复上报**。
  这条守卫不是多余的：后端 `canTransit` 对**同码幂等放行**（`:58-66`），99→99 不会拒，所以重复上报真能写进去；
  防重只能靠映射器这一侧 + `ActionGate` 的 10 s 窗口，**回读仍是 99 时页面说"可能未被接受"而不是"已处理"**。
- **§26/§27/§28 已收口**：段动作一律走 `ui/driver/DriverLegStateMapper.kt`（`LegDriverAction` + `LegFlowState`），
  它只回答"当前码允许哪一步、时间线走到第几步、没有动作时为什么"，**不计算下一状态**；
  POST 之后必回读（`RoutesViewModel.action` → `GET /driver/legs`，`HandoverViewModel.advanceLeg` → `GET /driver/current-leg`），
  回读没变就明说"可能未被接受"。旧的 `RoutesScreen.nextStatus()` / `HandoverScreen.legStep()` 已删除，
  判据 `tools/check_status_probes.sh` 的 S-la/S-lb 归 0。
- 小程序 `pages/driver/handover/handover.js:92-106` 的表与映射器逐步一致，唯一差异是它把"8 且不需交接"排成 `complete`
  —— 那一格后端会拒（见上行），所以映射器改成再发一次 `arrive-dest`。

## 5. 交接 `TransportHandoverStatusEnum` / 任务点 `TaskItemStatusEnum`

`TransportHandoverStatusEnum.java:24-31` → Android `HandoverStatus`：
0 等待交接 / 1 前序已到达 / 2 接收方待接 / 3 交接中 / **4 交接完成** / 5 交接超时 / 6 已取消 / 7 异常。
只有 4 是 `isDone`；**5/7 不是完成**，界面不得给"完成"色（§25 的收货确认门禁也看这张表）。

`TaskItemStatusEnum.java:24-32` → Android `TaskItemStatus`（`MissionStop` 出口）：
0 待执行 / 1 行驶中 / 2 已到站 / 3 上车中 / 4 下车中 / 5 揽收中 / 6 派送中 / **7 已完成** / **8 失败**。

| 码 | 允许 | 禁止 |
| --- | --- | --- |
| 0 | 开始执行 | 标成已完成 |
| 1..6 | 执行当前动作、上报位置 | 用"完成"色（`isActive`） |
| 7 | 归档、看凭证 | 再次执行（终态） |
| 8 | 看失败原因、上报调度 | 当作另一种完成 |

动作名 `ACTION_NAMES`（`DriverAppServiceImpl.java:123`）：0 出发 / 1 接客 / 2 送客 / 3 派送 / 4 揽收 / 5 返回 / **6 经停**。
> 后端 VO 注释只列到 5，`MissionStop` 旧表也漏了 `6 经停`（本轮补齐，表只留 `TaskItemStatus.ACTION_NAMES` 一份）。

## 6. 审核 `ReviewStatusEnum`

`enums/order/ReviewStatusEnum.java:20-24` → Android `ReviewStatus`：
0 待审核 / 1 审核通过 / **2 需客户操作** / 3 需人工审核 / 4 审核不通过。

出口：`SendOrder.needsCustomerAction`（码 2 + 有服务点）、`ReviewMapper.reviewTitle/reviewHint`、
`ReviewCard` 的边框/图标色、`SendScreen` 审核结果卡（这三处本轮改为 `when (ReviewStatus.of(status))`，不再写 1/2/3/4）。
原因码文案仍只在 `ReviewMapper.REASON_TEXT_MAP`（与后端 `ReviewReasonCodeEnum` 对齐）。

## 7. 尚未收进单一映射的码空间（诚实登记，不算已完成）

| 空间 | 现状 | 计划 |
| --- | --- | --- |
| 班次 `SHIFT_STATUS_NAMES` | 后端 0 未发车 / 1 在途 / 2 已完成；Android 文案是 待发车 / 进行中 / 已完成。**码值一致，只差中文措辞**，且 `DriverStateTest:38-40` 钉住了旧措辞 | 单独一轮统一措辞并同步测试，不混进 §18 |
| 司机任务点 `MissionStop.status` 三态 | `parse` 缺键读 `-1`，`statusLabel` 对 `-1` 返回空串（让页面显示"未标注"），只有后端真给了表外码才说"状态暂不可用" | 保持；`StatusMappingContractTest` 钉住这条区分 |
| 车辆运行态 `VehicleStatus` | Android 自有枚举（RUNNING/IDLE/ARRIVED/NO_LOCATION/UNKNOWN），由 `LocationSource` + 定位新鲜度推导，不是后端整数码 | 与 §43/§46 `LocationQuality` 一起复核 |
| `ProductOrder.parse` / `SendOrder.parse` 的 `status` 缺键 | 仍是 `multiNum` 默认 `0`（= 待发货 / 已创建），不是"未给" | §6 三态契约测试轮统一成 `-1` 哨兵 |
| 通知语义级别 `NotificationLevelEnum` | Android 用 `MsgLevel` 字符串解析，未建码表 | §38 消息中心轮 |

## 8. 验证分层（不得谎报）

- **CODE**：`ServerStatus.kt` + 12 处调用点改道（本轮清单见 `AI_ITERATION_STATE.md` 的 §18 DONE 段）。
- **TEST**：`StatusMappingContractTest` 逐码钉 0..13 / 0..11+99 / 0..3 / 0..4 / 0..8 / 0..7，
  含三条反例（`4 已完成`、`7 待客户操作`、`leg 4 前往起点` 不是完成）与"未知码 = 状态暂不可用"。
- **静态判据**：`tools/check_status_probes.sh` 全绿 = 页面里不再有 `status == N` / 第二张 `when (status)` 表 /
  `statusLabel.contains("完成")` 这类 substring 判断。
- **DEVICE**：未跑真机。文案与配色的最终确认需要真机截图对照（`docs/device-evidence/`），本轮只能到 CODE+TEST。
- **E2E**：未跑。司机段状态机（§25/§28）落地后才有意义，因为现在推进仍是客户端本地算的。
