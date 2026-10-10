# 司机端 UI 优化 · 功能验证报告（2026-10-09，v2 修订）

> 验证原则：**「能用」不只是「存在」** —— 每一项必须 logcat 日志 + 模拟器截图双重佐证。
> 环境：模拟器 emulator-5554（Android 15 / API 35，x86_64 + arm64 转译），包名 `com.shanxiang.buspost`，
> 演示会话注入（`-Pbuspost.demo=true` 出包，token=`demo-verification-token`，role=DRIVER）。
>
> **v2 修订说明**：v1 结论「高德导航在模拟器上结构性不可用」被本轮实测**推翻**。
> 真相是四个可修复的问题（见 §三），全部修复后**车道级导航在模拟器上真实跑通**（截图 34）。

## 一、结论矩阵（v2）

| # | 功能 | 结论 | 关键证据 |
|---|------|------|----------|
| 1 | 司机工作台新版 UI（站牌头 + 固定底部操作栏 + 懒加载分区） | ✅ 能用 | 截图 20/21/28/30 |
| 2 | 驾驶模式三联动（大字 + 夜间暗色 + 胶囊转白底深墨） | ✅ 能用 | 截图 30 vs 31 |
| 3 | 驾驶模式持久化（强杀重启） | ✅ 能用 | `Prefs drivingMode=true` 存活；截图 32 |
| 4 | 扫码作业全链路（弹窗→扫码页→权限→相机→预览→取消回传） | ✅ 能用 | 截图 23-27；`dumpsys media.camera: Device 1 is open` |
| 5 | 高德导航引擎初始化（隐私合规 + 库加载 + `onInitNaviSuccess`） | ✅ 能用（修复后） | logcat `libAMapSDK_NAVI...so ... ok`；闸门离开 `initResult=null` |
| 6 | 车道级导航真实渲染 + 高德算路 | ✅ 能用（模拟器） | 截图 34：真实瓦片 + 路线 + 车标 + 路况条 + **剩余 142.4 公里 / 114 分钟（SDK 回值）** |
| 7 | 引擎状态跨页面重建稳定 | ✅ 能用（修复后） | 滚动离开再返回，闸门不再退回 `initResult=null` |
| 8 | 位置上报（前台服务 + GPS 采样 + 上报后端） | ✅ 能用 | 「最新坐标 30.32750, 107.35460（GPS ±5m）」「已上报次数 6 · 1 秒前成功」 |
| 9 | 导航**性能**（启动到可用 ~25-30s，转译执行） | ⚠️ 模拟器独有缺陷 | 41MB .so 经 ARM 转译，首启 ANR 过一次 |
| 10 | 导航终验（流畅度/真 GPS/语音/车道帧） | ⏸ 待真机 | 真机 a267dbb 离线；模拟器坐标是注入的 |

## 二、本轮修复的四个真实缺陷（v1 未发现，全部影响真机）

### 1. 高德隐私合规接口从未调用（影响：所有设备）
- 高德日志明示：`E navi: 确保调用SDK任何接口前先调用 updatePrivacyShow、updatePrivacyAgree…若未正确设置有崩溃风险`
- 全工程 0 次调用。**修复**：新增 `core/navigation/AmapCompliance.kt`（`MapsInitializer` +
  `AMapLocationClient` 两套合规接口，javap 逐签名核对），在 `BusPostApp.onCreate` **第一行**调用。
- 修复后 logcat：`I BUS_NAV: AMap 隐私合规已设置…`，隐私警告从有到 **0**。

### 2. `com.amap.api.location.APSService` 未在 manifest 声明（影响：所有设备）
- 实测 logcat：`Unable to start service Intent { cmp=…/com.amap.api.location.APSService } not found`
  → 高德内部定位失败（`AmapErr #1201`）→ 导航拿不到起点。
- 类在包里（`unzip -l` 核对过），只是没声明。**修复**：manifest 加
  `<service android:name="com.amap.api.location.APSService" />`，重打包后报错归零。

### 3. `initResult` 存在引擎实例里，而它是进程级事实（影响：所有设备）
- `onInitNaviSuccess` **一生只回调一次**，引擎实例却随页面进出/长列表滚动重建（`remember {}`）；
  实例重建后必然错过回调 → 卡片把「已就绪」念成「初始化中」、「开始车道级导航」**永久置灰**。
- 模拟器实测复现：滚动返回后卡片退回 `initResult=null`。
- **修复**：新增进程级 `NaviInitState`（唯一写入点是高德回调），实例初值从它读取；
  并补契约测试 `初始化结论写入进程级后任何读者可读`。修复后滚动往返状态稳定为 `Ready`。
- 注：`release()` 本就不 `AMapNavi.destroy()`（代码注释自己写明"进程单例"）——init 结论存实例
  与这一事实自相矛盾，本次修正即对齐。

### 4. 模拟器专属：ABI 被第三方依赖带偏（不影响真机）
- APK 里高德 .so 只有 `arm64-v8a`/`armeabi-v7a`（高德导航 SDK **不出 x86 库**），但
  `libandroidx.graphics.path.so` 四 ABI 全带 → 系统判 `primaryCpuAbi=x86_64` → App 以纯
  x86_64 跑，高德 arm 库被无视 → 加载失败（v1 误判为"结构性不可用"）。
- **修复（不改代码）**：`adb install -r --abi arm64-v8a app-debug.apk` 强制 ABI
  （模拟器有 arm64→x86_64 转译：`ro.dalvik.vm.isa.arm64=x86_64`、`libndk_translation.so`）。
  之后 `nativeloader: Load …/libAMapSDK_NAVI_v10_0_600.so … : ok`。

## 三、模拟器上跑通导航的完整链路（全部实测）

1. `install -r --abi arm64-v8a` → `primaryCpuAbi=arm64-v8a`
2. 启动 → 合规日志（BUS_NAV）→ 23~29s 后 `libAMapSDK_NAVI…so : ok`
3. `pm grant` 定位权限 + `adb emu geo fix 107.35 30.33` 灌坐标
4. 工作台开「位置上报（前台服务）」→ `最新坐标 30.32750,107.35460（GPS ±5m）`、上报成功
5. 导航卡闸门 → **「导航可用」**（Ready + 有起点 + 有坐标任务点）
6. 点「开始车道级导航」→ **AMapNaviView 真实渲染**：垫江街道瓦片、蓝色路线、转向箭头、
   车头朝上车标、罗盘、路况条；信息条显示 SDK 回值「无名道路 · 下一段 建新路 /
   **142.4 公里 / 114 分钟**」；车道缺失诚实标注（截图 34）

## 四、遗留与未验证（如实）

1. **真机终验（Task #7）**：流畅度、真实 GPS、语音播报、车道帧、偏航重算——这些只有在
   真机（arm64 原生）上才有意义。真机 a267dbb 恢复在线后按 §三 步骤重跑一遍。
2. **模拟器性能**：首启加载 41MB 转译库约 25-30s，出现过一次 ANR；这是转译固有代价，
   不代表真机表现。若要在演示中用模拟器导航，先预热一次再进页面。
3. 扫码**识码解码**未验（相机已真打开，虚拟场景无真实二维码）。
4. 演示库为空的接口走 DemoData 兜底，页面已显式标注「演示数据」——答辩时主动说明。

## 五、截图索引（/c/temp/wb-shots/）

| 文件 | 内容 |
|------|------|
| 30/31-header.png | 驾驶模式关/开对比（胶囊白底深墨 + 大字 + 夜间） |
| 32-after-restart.png | 强杀重启后三联动恢复（持久化） |
| 27-scanner-active.png | 扫码页相机实时取景 + 扫描线 |
| 33-state.png | 位置上报开启（开关 on + 上报计数） |
| 34-navpage.png | **车道级导航真实渲染**（本报告核心证据） |
