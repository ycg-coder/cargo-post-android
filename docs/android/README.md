# docs/android/ — 指路牌（不是第七份台账）

指令 v2.0 §104 要求最终具备这七份文档：

```
docs/android/ANDROID_ARCHITECTURE.md
docs/android/USER_DOMAIN.md
docs/android/DRIVER_DOMAIN.md
docs/android/NAVIGATION_ARCHITECTURE.md
docs/android/ROUTING_ARCHITECTURE.md
docs/android/OFFLINE_NAVIGATION.md
docs/android/THIRD_PARTY_REUSE.md
```

**本目录不复制这七份**，理由是本工程为"同一条事实两处记"付过代价（D-021：段状态机有三张本地表，
其中两张是"看起来已经统一"的副本，最后要靠 grep 才发现）。规则、状态、判据各留**一个权威文件**，
这里只做指路牌：按 §104 的条目名找到当前真值所在。

| §104 要的文档 | 当前权威落点 | 里面有什么 |
|---|---|---|
| `ANDROID_ARCHITECTURE.md` | `CONTEXT_LOCK.md` §〇 ANDROID APP MODEL + §二 分层与职责 | 单 APK / USER / DRIVER / Shared Core 的实体对应表，以及"调度—导航—渲染"三层不得越界的边界 |
| `USER_DOMAIN.md` | `CONTEXT_LOCK.md` 硬规则 1 + `ANDROID_PARITY_AUDIT_V2.md` §55（分域探针）+ 代码 `ui/user/` | 用户域页面清单、用户与司机两个业务域的隔离判据（P-* / T-* 探针） |
| `DRIVER_DOMAIN.md` | `CONTEXT_LOCK.md` 硬规则 2/3/4 + 审计 §62/§64 + 代码 `ui/driver/`、`driver/` | 司机作业闭环、任务点业务化、段状态机单源（`DriverLegStateMapper`）、GPS 上报策略 |
| `NAVIGATION_ARCHITECTURE.md` | `CONTEXT_LOCK.md` §二 + §〇 的 ⚠ 第 2 条 + `AI_ITERATION_STATE.md` BLOCKED 段 | 导航层应当是什么、**今天为什么还不存在**（导航 SDK 未进依赖，`com/amap/api/navi/` 零类），以及"任务操作不得被导航阻塞"（指令 §64/§125） |
| `ROUTING_ARCHITECTURE.md` | **跨仓**：`../cargo-post-platform/docs/REAL_ROAD_ROUTING_ARCHITECTURE.md`（服务端侧权威，本仓 `docs/` 下**没有**同名文件）+ 本仓 `CONTEXT_LOCK.md` §三 来源白名单 | 真实道路路线从 OSM/GraphHopper 到屏幕的链路、`RouteSource.real` 白名单、`RealRoadRouteValidator` 拒收规则 |
| `OFFLINE_NAVIGATION.md` | 尚无实现，只有在册计划：`AI_ITERATION_STATE.md` 队列（MapLibre / Valhalla 为 Phase 8）+ `THIRD_PARTY_NOTICES.md` §5 | 离线部分今日**唯一已存在的东西**是内存版 `RouteRepository`（只缓存已过校验的真实路线，`MAX_ENTRIES=12`，非 Room 持久化）—— 这一点写在这里是为了别让它被误读成"已离线可用" |
| `THIRD_PARTY_REUSE.md` | `THIRD_PARTY_NOTICES.md`（仓库根，§100 要求的正是这个文件名） | 随 APK 分发的构件与许可证、专有条款、计划复用未引入项、署名欠账 |

## 什么情况下本目录该长成真文档

出现下列任一情况，就说明指路牌不够用了，再拆分：

1. 某个主题在两个权威文件里出现**不一致的描述**（先统一，再决定是否需要独立文件）。
2. 新人读完四个台账仍定位不到某层的边界（说明该层需要自己的文档，而不是继续加注释）。
3. 指令 v2.0 的 §128 验收清单进入逐项签收阶段。

维护规则：本表任何一行指向的文件改名或拆分，必须回来改这一行；
指路牌错了比没有更糟 —— 它会把人送到一份过期副本上。
