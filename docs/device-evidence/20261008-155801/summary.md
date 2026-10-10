# DEVICE_STATUS: DEVICE_ONLINE（本机 x86_64 AVD，非物理机）

**这是本工程第一次拿到"设备在环"的读数。** 之前所有证据簿都是 `DEVICE_UNAVAILABLE`（见同目录上一层的 `20261008-125627/summary.md`，那里存的是 adb 原文 `adb.exe: no devices/emulators found`）。

时间口径要分开读：
- 本目录 `device-check.log` / `device-probe.log` / `hierarchy-home.xml` 是 **16:11 左右**实测（`adb devices -l` rc=0、1 台在环）。
- `login-evidence.txt` / `sweep*.txt` / `screen-*.png` 的原始读数产生于 **15:24–15:53** 那一段走查（同一台 AVD、同一个包）。
- `build-1528.log` 是这台的安装包那一次构建（15:28，BUILD SUCCESSFUL in 50s）。

## 1. 设备与包

| 项 | 实测 |
|---|---|
| 设备 | `emulator-5554` / `sdk_gphone64_x86_64` / SDK **35**（x86_64，只用模拟器；用户口径：不动共享开发机） |
| `persist.sys.timezone` | `Asia/Shanghai`（**是本轮用 `adb root` + setprop 改的**，AVD 出厂默认 `GMT`；见 §5 非缺陷①） |
| 已装包 | `versionName=1.0.0-android`、`versionCode=1`、minSdk 26 / targetSdk 34 |
| `firstInstallTime` / `lastUpdateTime` | 07:10:47 / 07:38:54（设备侧这两个时间戳是 UTC 制式，与本机 15:10/15:38 差整 8 小时 ⇒ 与登录闭环那一秒对得上） |

**三个 APK 必须分清，别把交付包和调试包混为一谈：**

| 包 | 字节 | sha256（前 8） | 用途 |
|---|---|---|---|
| `app/build/outputs/apk/debug/app-debug.apk`（本轮设备包） | **114,239,840** | `83dc930a` | 装在 AVD 上取证的那一个；备份 `D:\android-dev\dev\apk-device-20261008.apk` |
| 同路径的**前一次**产物 | 114,239,931 | `77e441fe` | baseUrl 带错 `/api` 前缀的包（病根见 §5 非缺陷④），已被原地顶掉 ⇒ 只剩日志里的字节数 |
| 台账交付包（D-064 §4） | 113,928,596 | `3bbf06f3` | 上一轮收口那一批源码的交付物，**没有装到设备上** |

⇒ 本轮设备读数**只**能证明 `83dc930a` 那一包，不能写成"交付包已真机验证"。

## 2. 登录闭环（前后端双向佐证，同一秒）

`POST /app-api/member/auth/send-sms-code {mobile:13800138001, scene:1}` → 读后端表里的验证码 → `POST /app-api/member/auth/sms-login`。App 侧进了首页（`screenshot-login-success.png` 的 label 集就是首页），后端三张表同时落行：

```
system_sms_code            id=712  create 15:39:33  code=9999 scene=1 used=1 used_time=15:39:47
system_login_log           id=5523 log_type=104 15:39:47 13800138001 result=0 127.0.0.1
system_oauth2_access_token id=108627 create_time=15:39:47 user_id=4 expires=16:09:47
```

`used=1` + `result=0` + 新增 token 三件事在同一秒，且上一条 `711`（15:24:36）仍是 `used=0` ⇒ 这一次登录是真发出去的请求，不是客户端本地状态。验证码 `9999` 是 **local profile 固定值**（后端 `application-local.yaml`），不是 App 算出来的。

## 3. 逐屏走查读数（`sweep3/4/5-report.txt`）

首页 / 商城 / 快递 / 我的 / 收货地址 / 寄件三步向导 / 实时公交都拿到了 label 与截图。关键几行：

- 首页：`乡邮智联 / 县城⇄乡村 / … / 合川区钓鱼城街道 / 27°C 阴 · 合川区钓鱼城街道 / 4日预报 / 实时公交 / 农产品 / 我的快递 / 我要寄货 / 附近公交 / 更多 / 根据合川区钓鱼城街道展示 / 已更新：15:52 / 当前不在运营时间 / 。附近共 1 条公交线路`
- `更多` → 实时公交页（`正在定位…` → `已按你的位置排序 / 15:49:12 更新 / 班次时刻 / 附近 2 个站点 / 1 条线路`）
- `我的快递` → 快递 tab（`我的包裹 / 寄货 0 单 · 商城 0 单 / 到车提醒 / 后台每 15 分钟检查一次…`）
- `我要寄货` → 三步向导第 1 步，可达性分支渲染 **`可达性暂不可用 / 后端没有返回可达性判定（不代表车辆能到） / 请手动选择取货站点；本单不声称车辆可上门`** ⇒ honest-fallback 在设备上工作（后端那一半仍挂 #278）
- 定位：AVD 默认点在加州（`39.237255,-123.150032`），本轮用 `cmd location providers` 注入合川 `30.03,106.30` 后，反编址、距离、线路数、honest-fallback 全部跟着变 ⇒ 定位链路在环上通。

App 侧崩溃计数（`logcat-sweep3.txt`，5,224,729 B 全量）：**FATAL EXCEPTION=0、UnsatisfiedLinkError=0**；`NetworkException=4` 经归因属于 Google 的 `HeterodyneSyncer`，不是本 App；`AndroidRuntime=359` 是 uiautomator 启动横幅里的字符串，不是崩溃。

## 4. 确认缺陷（本轮改了三处，见 D-065）

| # | 现象（设备实测） | 落点 | 处置 |
|---|---|---|---|
| ① | 非运营时段副标题渲染成 **`。附近共 1 条公交线路`**（裸句号开头）——前两段（服务时段/下一班）后端都没给时，第三段仍带句号和"，"前缀 | `HomeScreen` 的 `"off" -> EmptyState`；`BusScreen` 同形状 | 改成 `listOfNotNull(...).joinToString("；")`：只有真实拿到的片段才进句子，片段之间不会出现悬空标点。顺带去掉 `nb!!` |
| ② | 底栏在**商城/快递/我的**任一 tab 上按系统返回，**直接退出 App**（三次全部 `mCurrentFocus=…nexuslauncher`） | `UserShell` | 加 `BackHandler(enabled = selected != 0) { selected = 0 }`：非首页 tab 的返回 = 回首页；首页返回仍退出（那是正确行为） |
| ③ | 司机端同一形状（工作台/我的路线/我的收益/用户版） | `DriverShell` | 与 ② 同形处理；这两个文件里没用 import 而是写全限定名，因为 `DriverShell.kt:37-38` 是在册锚点，加 import 会把它们整体下移一行 |

## 5. **不是**缺陷（四条曾被我误判，逐条写清楚为什么）

① `已更新：07:39` 不是时区 bug —— 当时 AVD 的 `persist.sys.timezone=GMT`，`SimpleDateFormat` 读的是进程启动时的默认时区。App 那行代码没错；后来把设备时区改成 `Asia/Shanghai` 并重启进程，渲染就是 `已更新：15:52`。
② `约 10773.74 公里` 不是 null-island / 距离算错 —— AVD 默认 mock 定位在加州，客户端拿到那个点后算出的直线距离本来就是这个量级。注入合川点后数值跟着变。
③ `附近公交` 这个标题不可点 **是设计** —— 它是区块头；进实时公交页的入口是同一行右侧的 `更多`（实测 `更多` → 实时公交页）。
④ `NetworkException` 与 Google 同步器、`AndroidRuntime` 与 uiautomator 横幅的归因见 §3；`/api` 前缀只属于共享开发机的 nginx，本地 jar 必须用 `http://10.0.2.2:48080` 直连 —— 上一包 `77e441fe` 的失败是 baseUrl 拼错，不是网络层缺陷（`BuildConfig.BASE_URL` 实测就是这个值）。

### 5-a 一条被推翻的"缺陷"：宫格/底栏对无障碍不可见

`sweep5-report.txt` 那一遍写着 `实时公交/我的快递/首页/商城/… self.click=false ancestors_clickable=0`，我据此判"宫格与底栏在 a11y 树里不可点"。**这条判错了，是被扁平解析器骗的。** 用 `xml.etree` 带父子深度重读同一份 `hierarchy-home.xml`（45 个节点、`clickable=true` 有 12 个）：

```
d7 android.view.View clk=true bounds=[42,1148][186,1402]        text=''      ← 实时公交 tile 的可点父节点
  d8 TextView          clk=false text='实时公交'                              ← 它的 label 子节点
d6 android.view.View clk=true bounds=[0,2174][270,2319]                       ← 底栏「首页」可点节点
  d7 View      clk=false desc='首页'   /   d7 TextView clk=false text='首页'
```

⇒ **控件是可达的**，点击语义在父 `View` 上；按"文本→节点→看它自己 click 标志"去找必然找不到（这已经是本段第二次踩同一个解析器坑，第一次是"附近公交是死控件"）。
仍然留下一个**低度打磨项**（挂 #285，不判红）：可点父节点自己 `text=''`/`desc=''`，标签在子节点上，语义没有 merge 上去 ⇒ TalkBack 会先念"按钮"再单独念标签。这不影响点得到，影响读的顺序。

## 6. 环境（这一段的真值，写下来免得下一轮重新踩）

- Windows 侧的瓶颈是 **commit charge**，不是物理内存。本轮实测：Kotlin 编译守护进程 1.6 GB PM 一杀，FreeCommit 从 **0.31 → 1.98 GB**；qemu 自己占 4.6 GB PM。
- FreeCommit=0.31 GB 时起 gradle 会拿到 `os::commit_memory(...) failed; error='页面文件太小'`（**DOS errno 1455**），报的是"Unable to start the daemon process"——长得像配置错，其实是内存。**这不是 `-Dorg.gradle.jvmargs` 写错了**，同一串参数在 15:28 成功过（`build-1528.log`，BUILD SUCCESSFUL in 50s）。
- 可用的构建调用：`MSYS2_ARG_CONV_EXCL="*" ./gradlew "-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK" "-Pkotlin.daemon.jvmargs=-Xmx2048m -Dfile.encoding=GBK" -Pbuspost.baseUrl=http://10.0.2.2:48080 :app:assembleDebug`。`in-process` 那条路已实测会把 Gradle 守护进程打死，不要再用。
- Git Bash 里 `taskkill` 必须写 `//PID … //F`；单反斜杠会被 MSYS 路径转换吞掉并**静默失败**。
- `adb emu geo fix` 在这个 API 35 headless 镜像上返回 OK 但不改位置；要 `cmd location providers add-test-provider gps` + `set-test-provider-enabled` + `set-test-provider-location`。
- 后端：`yudao-server.jar`（PID 34048）在 `0.0.0.0:48080` LISTENING 实测 2 条。
