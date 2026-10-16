# AI_ITERATION_STATE — 自主迭代状态板

> 读法：`CURRENT` 是此刻正在做的事；`DONE` 已验证；`BLOCKED` 卡在环境/依赖/真机；`NEXT` 是既定顺序。
> 每轮迭代结束必须更新本文件，禁止"做完了但没记录"。

最后更新：2026-10-16（傍晚收工，D-135 … D-138 · 司机异常照片两端进仓并在**真机**上跑出第一条新缺陷（取消选片清掉已拍那张）、后台 49 个写口里 9 个钉了服务端幂等键、告警队列补 ADMIN 未读口径、敏感字段唯一掩码出口、静态闸 14 本；#96 负控批自毁产物已修；**两条我自己信错的前提**：① 本机一直"以为没有 maven"，于是 `0c7a47f2` 那版后端**没编译过就推上去了**（CI backend=failure 实测）；② 并行那条线照我自己契约里写死的表名建了第二套日历 ⇒ 一个业务两张表，已立第 14 本闸并写明归属）


## CURRENT（2026-10-16 下午 · 台账 D-135 … D-136 · 两端 + 并行线撞车已记）

- **本地能编后端了**（此前那句"只能靠 CI 验"是我自己的错判）：`/c/Users/袁/apache-maven-3.9.16/bin/mvn -B -pl yudao-module-transport test`
  ⇒ BUILD SUCCESS，**模块 387 用例 / 0 失败 0 错误 / 43 个类**（`TownEventCalendarServiceImplTest` 7、`DriverAppServiceImplTest` 41、
  `SensitiveDataMaskTest` 4——这 4 条以前从没真跑过，因为编译就断了）。**新规矩：每个后端提交推之前先本地编译该模块并读 `MVN_RC`。**
- **红是被 CI 记着的，不是我说的**：run 38869518615（head `0c7a47f2`）= backend failure，其余 changes/drift-check/frontend 三作业 success。
  修在 `064d66b8`（一个词：`UNKNOWN_SHAPE_KEEP` 声明成了 `String`）。教训同形于前端那条"ts:check 绿 ≠ 模板验过"：**判据脚本读的是源码文本，它绿不等于能编译**。
- **#60 客户端这一半第一次在真机上跑过**（v106→v107，同签名 release 覆盖装，`firstInstallTime` 仍是 10-15 ⇒ 没卸载、数据没清）：
  选片器在 HyperOS 上可用（`com.android.photopicker`，单选回值无额外确认），选完念"已选 1 张，点击重选"；
  **咬到一条真缺陷**：重开选片器再按返回 ⇒ 回调给 null、旧代码 `picked = it` 把刚拍的那张静默清掉，页面回到"从没选过"那一形。
  已改 `it ?: picked` 并同序列重跑（凭证 `tmp/device_v107_cancel_keeps_photo.png`）。EX-m 两格进判据簿，两把反证都红在该管的一格
  （第一版反证用的是 drop，**drop 只给锚点那几个字加注释、行尾原样留着 ⇒ 判据照样绿**，是假反证，换 swap 才真红）。
  ⚠ **提交那一格仍未验**：按下会往共享云写一条真异常（段转异常 + 通知管理员），而接受 `photoUrl` 的后端在 PR #195 上没部署；等合入后跑。
- **#96 修完并实测**：新 helper `tools/ensure_test_xml.sh`（缺产物就真跑一次**无过滤**全量，禁快照还原；必须走 Y:/Z: ASCII runner，
  裸 gradlew 在中文路径下是环境假失败）。验证形状：删掉一本的 XML（74→73）再跑那本书 ⇒ 自动全量 29s、`tests=762 failures=0 errors=0`、
  XML 回 74、`NW-h 14/0/0`、rc=0。
- **#90 收口**（`a04a355`，台账 D-139）：新闸 `tools/release_artifact_signed_gate.sh` 三条 fail closed（恰好一只 / 验签过 / 证书等于在册指纹），
  排在"扫包取证"之后、上传之前；发布那一步的 `ls *.apk | head -1` 改成固定名 + 存在性检查。
  反证 7 把本机全跑在真产物上（K1 绿、K2–K5 红、K6–K7 rc=2）；K7 第一版是空转的——判据找工具的三个来源我只关了前两个。
  ⚠ 文件名**故意不带 `check_` 前缀**：verify 作业的 `for s in tools/check_*.sh` 没有 release 产物，被 glob 抓到就会以 rc=2 逼我加第三本豁免。
- **装包这一格**：`pm install -r -t /data/local/tmp/xxx.apk` 两轮都是直接 `Success`，**MIUI 那个"继续安装"倒计时对话框只在 `adb install` 流式安装时才出现**
  —— 我先按配方写了点框脚本，结果是空转的一份代码；这条记下来省下一轮。
- **并行那条线（AI 辅助）已经在同一个工作树里动了码**：`yudao-module-ai-assist/`（10 个文件，0 条用例）、`V050__ai_event_calendar.sql`、
  `views/transport/ai/`、`api/transport/ai/`，还改了 root `pom.xml` / `yudao-server/pom.xml` / `db-migrate-guarded.yml`（+3 probe）。
  这些都**没提交**，我这一轮也没有替她提交任何东西（`git add` 全程走显式路径）。撞车处置见 m195 `docs/AI_ASSIST_BOUNDARY.md` §6.1 与新闸 `check_business_table_single_owner.py`。
- **静态闸现读 14 本 / 13 个 CI 步骤**（`scripts/` 实数：`grep -oE 'scripts/[a-z_0-9]+\.py'` 去重 = 14）。第 14 本认的是"同一个业务对象两张表 / 两个同名 DO"，
  K1–K6 六把刀；工作树现读判 2 组（真撞车），干净 HEAD 导出判 0 组（CI 看到的是这一面）。
- **推送与 PR**：分支 `ci/admin-gates-2026-10-16` 已推到 `5abba2f9`（`git ls-remote` 实测 == 本地 HEAD）；
  PR #195 提交数在推之前实读 **23**（`gh pr view 195 --json commits`），这轮又加 3 ⇒ 待 CI 复跑后重读。**master 未动、未合、未写库**。
- **仍在册**：#93 的维护页那一半（页面在并行那一套里，要先改指向 `/transport/town-event/*`）、
  #94/#95 两项非 AI、#71 生产排序键（算法码按用户明令不提交）、#25/#31/#81 等人拍板；#98 等并行那条线按契约 §6.1 收手。
- **`tmp/` 不许随手清**（我早先把它列进"收尾要清的垃圾"，实测推翻）：`cargo-post-android/tmp/` 210 个文件被 `DECISION_LOG.md` 引用 **171 处**，
  那是留档证据而不是垃圾。要清得先迁进受版本控制的证据目录。


## 上一轮 CURRENT（2026-10-16 后台与 CI 收工 · 台账 D-127 … D-134）

- **这一轮的面**：`cargo-post-platform` 的 web 后台 + CI + 算法度量台，不是 App。App 那一半仍以
  2026-10-15 那块为准（下面的读数没有一条是从那里推出来的）。
- **推送这一格本轮成立了**（网络恢复；先实测 `git ls-remote --heads origin` rc=0 才动）：
  分支 `ci/admin-gates-2026-10-16` 已推上去 ⇒ **PR #195**（base master，**12** 个提交，`mergeable=MERGEABLE`；合入前的 CI 作业在跑，本轮不替它报结果）。
  早先那一次读数是 `ls-remote` rc=**128** / `Failed to connect to github.com:443`、`gh` 走 `127.0.0.1:443`
  （hosts 把 api.github.com 指到本机）——**那是环境红，不是代码红**，改判要重新实测，不能沿用。
  **没有强推 master**：本地相对 `origin/master` 是 30 behind / 7 ahead，快进不成立，所以走分支 + PR。
  **合 PR = 触发云端部署**（`deploy-dev.yml`）；写库仍在 `AUTO_DB_MIGRATE` 与 guarded 两道闸后面。
- **云上没被我写过**：所有共享库动作只走 `db-migrate-guarded.yml`（`mode=check` 只读 / `mode=apply` 要
  `confirm=APPLY`）；`deploy-dev.yml` 的迁移已收进 `AUTO_DB_MIGRATE=1` 这一道闸后面。
- **本轮关掉两块**：#67 五处不可逆写口全部补前置确认（每句话对着后端读过：审核通过会推微信给用户与司机、人车绑定会先把别人那一对解掉、重调度找不到备选就把方案置异常并通知管理员）；
  #68 三态在册欠账 **24 → 0**（13 个列表页 + dispatch 6 处 + 表单/详情 5 处），金额与里程收成一条 `moneyText/distanceText`（缺值念「没给」、真零才念 0.00），api 层清掉 7 个死导出与 3 组同端点重复包装。
  ⚠ 后端幂等键那一半**仍没批**：`controller/admin/**` 那 40 个写接口至今零 `@Idempotent`，前端确认与 `:loading` 是唯一闸 —— 这句不许被读成"已经双保险"。
- **仍在册**：#71 生产排序键缺"未分配"那一维（要动算法生产码并跑 A/B，而 `algorithm/**` 按用户明令不提交）、#59 与 #60 要后端先定契约、#25 与 #31 承接上一轮。
- **静态闸现读 11 本**（`ci.yml` drift-check，其中新写的三本连 `--self-test` 一起跑）：SQL 参数量 / 菜单主键 / 搜索重置 / 写按钮在飞 / 站点字典 / 写死路由 / catch 留可见出口 / 墙钟假进度 / 不可逆写口先确认 / api 死导出与重复出口 / 金额缺值不折叠。
- **算法侧**：`algorithm/**` 与 `docs/api/algorithm-api.yaml` 的改动**按用户明令不提交**，
  `sql/mysql/transport-schema.sql` 那 3 行新列跟未提交的 Java DO 字段是同一块活，也留在工作树里；
  `src/assets/audio/response.mp3` 的删除不是我这一轮动的，未替它做决定。


## 上一轮 CURRENT（2026-10-15 真机第二轮收工 · 台账 D-115）

- **发版**：`v77`（PR #15 + #16 已合 main@2989dfe）。第一次 dispatch 被 release 的**扫包取证**拦下
  （`isPrivateHost()` 里的环回主机名字面量 = 包内残留本机地址），改按四段数字判后重投通过。
  产物侧凭证：解 `classes*.dex` 数到新符号 `isPrivateHost`/`onMobileChange` 各 1 命中、四个禁词命中 0。
  **待用户装**：APK 已 push 到手机 `/sdcard/Download/xiangyou-v77.apk`（HyperOS 只能本人点装），
  或冷启动走 App 内自更新弹窗（D-111 恢复的那条通道）。
- **读数**：编译 SUCCESSFUL；`tests=706 failures=0 errors=0 badfiles=0`；**36 本判据簿红 0**（含新本）；
  新本负控 7 刀全红且各红在该管的那一格；真机用户端 16 屏 + 司机端 8 屏 `layout_audit TOTAL_ISSUES=0`。
- **在册 OPEN 三条**：#42 遍历器 DENY 要补「确认发车」「开始车道级导航」（`walk_ui.sh` 还在未合并的 `tools/ui-walk-harness`/PR #12 上）；
  #43 司机异常上报跨单段序歧义（后端 `legSequence` 每单各自从 1 起，两行都念"第 1 段"）；
  #25/#31 承接上一轮。
- **本轮证伪掉的两条假结论（别再当真账）**：① App 没有 `/app/ship/order-item/cancel` 这个调用，
  真实 `PUT /product-order/cancel` 云端是活的；② 司机端 9 个接口早就带 `driverId`，未绑定直接抛 `DRIVER_UNBOUND`。
- **还欠一次**：42 本负控在合并后未整批复跑（上一批 23:47–03:19 完成，5 本红为已知顺序/环境红：
  `doc_anchor`/`envelope_signature`/`idempotency_test`/`notify_result`/`oss_credits`，均早于本轮改动）。

> 上一轮：2026-10-14（傍晚续，D-110 · **用户报的"网络不好 + 新功能没通路"分成两个根因，算法时限口径收成一套**）：
- ①"没通路"= 装的是 **v43**（03:00:30Z 打的），而 PR #4…#9 都在 03:58Z 之后才进 main ⇒ 不是通路没接，是二进制里没有。
  已重新派发 release ⇒ **v54 已发布**（`app-release.apk` 109,936,996 B，versionCode 54>43 ⇒ D-105 那条自更新通道第一次被真实消费）。
- ②"网络不好"根因在**库结构不在网络**：同一批 GET 实测，云上 `notice/list`、`product/list` = `code:0`，
  而 `send/stations`、`bus/nearby`、`bus/lines` = **HTTP 200 + `{"code":500,"msg":"系统异常"}`**；本机 48080 上同样请求全 `code:0`
  ⇒ 服务活着、老端点正常、只有站点域的新列读不到。对照 `V033__station_pickup_attrs.sql` 的自述（补联系人/电话/营业时间/电商站点四列，
  并明写"未执行之前会因缺列报错"）⇒ **待执行清单 V028/V030/V031/V032/V033/V034/V035**（`V029` 刻意不在内：裸 ALTER 第二次必红）。
  我不碰共享库；用户执行后可在线复验这三条端点。措辞侧确认不是 bug：HTTP 200+code500 念后端原文"系统异常"，
  只有 HTTP 5xx 才映射"服务暂时不可用"，IOException 才是"网络连接失败" ⇒ 用户看到的"网络不好"来自装包时正赶上服务重启窗口。
- ③ 算法侧（用户授权我按效果决定）：实测 `app/solver.py` **零**时间管线、OR-Tools 一批最多被调 `次数×5s`
  （`limit=1→wall=2.01s / 5→10.00s / 10→20.00s`）⇒ 声明的 5.0s 被精确翻倍。做法：**计数是终止的正常依据、
  墙钟是请求边界的真保险丝**（HACO 钳剩余量 / 每次搜索领剩余量 / HYBRID 两臂共享一只表），
  不可中断的固定开销不藏（`OVERALL_LIMIT_S`/`ELAPSED_MS`/`OVERALL_FUSE_TRIPPED`）；
  `ITERATION_COUNT` 从 0 条断言在读变成有读者（haco 版本可行解必须 ≥1 代完整迭代）。
  口径唯一权威说明 = `docs/algorithm-integration.md` §时限与终止口径，把第三个数（HTTP 层 10s 任务级超时）也收进同一套。
  读数：`pytest -m "not slow"` **820 passed / 3 skipped**；新簿 3 passed（含一条结构断言，不靠毫秒）；基准 16 passed。
  ⇒ **PR #193 已开未合**（合 = 重启云服务，等用户点头）。本轮算法改动全程在 worktree 做，后端主树 106 个他人未提交文件一字未动。
- ⚠ 云上没有一条读数能证明迁移已跑 ⇒ 用户执行 SQL 之前，"App 在云上能用"这句话只对公告/商城两域成立，站点与公交域不成立。

最后更新：2026-10-14（傍晚，D-109 · **功能通路第三查：手动地址查公交页原来只有"加载中"一格**）：
- 扫法（可复现）：`*Screen.kt` 里"渲染 BusPage/BusLazyPage" + "有 Repo 读点或 load()" + "整文件零 ErrorState/.error"。
  第一版口径宽（12 本命中，多数是脚手架/纯渲染器，错误在别处出口），收窄后只剩两本：
  `ui/user/AddressSearchScreen.kt` 真缺陷、`ui/driver/DriverMineScreen.kt` 唯一的 `Repo.` 是 `logout()`（写操作，不是缺陷）。
- 缺陷：该页读 `Repo.forwardGeo` + `Repo.nearbyBuses`，**失败时 `vm.error` 有值但页面一个字不念**、
  "没解析出来"同样是空白、加载态还是手搓圆点卡 ⇒ 用户看到的等价于"按钮没反应"。
  与 S-le"写完必须回读"是同一族的另一半：**读不到也要说一句**。
- 改：加 `searched` 区分"还没查过/查了没结果"，三格各一句（`LoadingState` / `ErrorState(onRetry=…)` / `EmptyState`），
  未查过也给引导不为空白；手搓转圈与三个只服务它的 import 删掉。
- 新闸 **S-m**：`ErrorState` 恰好 1 / `EmptyState` 恰好 4（还没查过·没解析出来·无公交·无揽收点，不许并格）/
  `LoadingState` 恰好 1 / `CircularProgressIndicator` = 0；配 `status_negative_controls.sh` 两条注入各咬 ✔（整本 23 条点名变红 / 0 不成立）。
  ⚠ S-m 的 `EmptyState` 目标值我第一次写成 **2**、实测 **4** —— "判据下限只能实测不许推算"这条第五次被踩，
  这次是负控的"基线自拒"在落盘前拦住（前四次：D-082 / D-084 / #51 / ⑪）。
- 终值：判据 **35 本全 rc=0**；`assembleDebug` + `testDebugUnitTest --rerun-tasks` ⇒ **68 份 XML / 706 例 / 0 失败 / 0 错误**
  （`45 tasks executed`）；APK **117,358,172 B @16:08 / sha256 前缀 `7cf186bc`**（与 15:45 那份 `ff08bd1c…` **同字节数而 sha 变**，
  第七次实测"字节数不是内容指纹"）；`check_ledger_tables.py` TOTAL FLAGS **0**；锚点无新增（七本台账里指向该页的 `:行号` 现读 0 处）。
  设备仍未接 ⇒ 该页三态的视觉那一半 `NOT VERIFIED ON DEVICE`。

最后更新：2026-10-14（下午续二，D-108 · **UI 规格第二刀：热区收进令牌 + 无生产者的新闻面撤除 + 三处死码**）：
- **同一题目的下半**：D-107 收"种类"，这一刀收**热区**与"这句话有没有生产者"。实测起点 `.height(<48.dp)` 且同链
  `.clickable` 的手搓按钮状块 1 处（登录闸门 46dp 渐变 CTA）+ 3 处文字块热区不足 ⇒ 全部走 `BusButton` /
  `BusDialogAction` / `LocalBusTouch` 下限；UB-f 调用点 128→133。
- **虚构新闻面已撤除**：后端 App 面 controller 逐条列举**零新闻端点**（全仓 `grep -rli news` 唯一命中是 `newStatus`
  字符串假阳性）、小程序 0 该功能、App 是唯一生产者 ⇒ `Repo.news()` / `NewsItem` / `NewsCard` / 首页那一块全删。
  新闸 **AF 格**（符号面＋具名面两头各钉一头）配新簿 `tools/authenticity_negative_controls.sh`（4 ✔，RESULT=PASS）。
  ⚠ `check_authenticity_counts.sh` 从此两类同住（前几行读数、AF 是闸），整本 rc=0 不等于"所有口径都过了"。
- **新闸 UB-g / UB-h**：低于 48dp 的手搓按钮状块 = 0；低于 48dp 的**轮播视口恰好 2** = 在册残留 `D-UI-1`
  （抬它改的是排版，无设备不许断言 ⇒ 只钉数量）。UI 负控整本 8 ✔ + 正控制绿。
- **写后回读三条**：地址 `remove`/`setDefault` 由本机 `filter`/`copy` 自封改成回读；地址页进页必取
  （旧写法 `if (vm.items.isEmpty()) load()` 让"编辑退回来"永远看不见刚存的那条）；资料页改为 `Repo.refreshUser`
  回读，**回读失败时成功语降级**成"已提交，但资料没回读到"。
- ⚠ **#32 第三项被自己的判据驳回**："闪屏丢弃式预取"撤掉后 DN-a / SA-d / TH-f 三本同轮变红 ⇒ 它是被在册闸
  钉住的冷启动预热，一字未改还原。**#31 里那句"后端无此口径"也撤销**：它与"库存未给/位置来源未给/新鲜度未给"同族。
- **终值**：判据 35 本 / 负控 42 本；`assembleDebug` + `testDebugUnitTest --rerun-tasks` ⇒ **68 份 XML / 706 例 / 0 失败**
  （`55 tasks executed`）；`lintDebug` 只红在 `PropertyEscape` 一条（本机文件、不在库里，与 D-095 那遍同形）；
  APK **117,358,172 B @15:45 / sha256 `ff08bd1c…`**、条目 918（与 `d7d2d801` 114,208,801 差 +3,149,371，跨六天合并，
  **未归因，挂 #140**）；表格自检 TOTAL FLAGS **0**；锚点 records 1457 / ID-MISS **264**（闸门 273 未下调）/ 在册 **239** /
  DA-b 0 / DA-c 15 / DA-f 705÷426。设备：本轮未接 ⇒ 视觉与热区那一半全部 `NOT VERIFIED ON DEVICE`。
- ⚠ **#191 仍未合**（等用户点头，合 = 云部署）；#159 仍 OPEN（现 239 条在册）。

最后更新：2026-10-14（下午续，D-107 · **UI 规格统一第一刀 + 功能通路复核登记四条欠账**）：
- **题目**：用户要求"同一界面/同一行按钮大小形状统一"。实测起点 **34 处**裸 Material 按钮全在
  `AlertDialog` 的 confirm/dismiss 槽（13 个文件）⇒ 与同行走设计系统的主按钮是两套高度与命中区。
- **做法**：设计系统新增唯一出口 `BusDialogAction`（内部是 `BusButton` 的 TEXT 档），34 处收进去；
  `UpdateDialog` 那颗默认尺寸的"立即更新"也一并统一。新闸 `check_ui_button_probes.sh`（UB-a…UB-f）
  只钉写法层、**不钉像素**；配对负控 `ui_button_negative_controls.sh` = **6 ✔ + 正控制绿，RESULT=PASS**。
- **终值**：`assembleDebug` + `testDebugUnitTest --rerun-tasks` ⇒ **68 份 XML / 706 例 / 0 失败 / 0 错误**（`45 tasks executed`）；
  判据簿现读 **35 本全 rc=0**；`check_ledger_tables.py` TOTAL FLAGS 0；锚点 records 1457 / ID-MISS 273（本轮正文零新增行号引用）。
- **功能通路复核**（同一轮的另一半）登记成 #29–#32 四条在册欠账，其中两条我复验过原文：
  `Repo.news()` 是零网络请求 + `delay(300)` 的 4 条硬编码新闻（还署"新华社/人民日报"的名）；
  资料/密码/地址/反馈六处写完直接念"已保存"，`Repo` 只读 `.demo` 不读后端 `data:Boolean`
  ⇒ S-le"写操作必须回读"这道在册闸漏了这几面。
- **有意没做**：11 个硬编码色值**没有一个在 `BusTheme` 里** ⇒ 换成令牌会改外观，本机无真机 ⇒ 不在这里断言"换了更统一"，
  留给设计拍板 + 上机核对。
- ⚠ **#191 仍未合**（等用户点头，合 = 云部署）；#159 仍 OPEN（248 条在册）。

最后更新：2026-10-14（中午续，D-105 · **更新通道第一次真发布 + 本机后端未提交批次筛过一遍开 PR #191**）：
- **自更新通路现在有真读数了**：派发一次 `workflow_dispatch` ⇒ Release **乡邮智联 v43**（非 draft、非 prerelease），
  资产 `app-release.apk` **109,953,380 B** `state=uploaded`；`releases/latest` 由 **404 变 200**；
  出包后回读那一步原话"回读通过：latest = v43，且带 app-release.apk"。签名材料零残留那道闸也过了。
  ⚠ **但"能在内部收到并装上"还差一次换签安装**：设备上是 debug 包，与 release 不同源 ⇒ 直接点更新必然
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`；弹框/进度/安装器三格仍是 `NOT VERIFIED ON DEVICE`（本机无设备）。
- **本机后端那 106 项未提交**按"只提交该提交的"筛成两笔，开 **PR #191**：`mvn` **367 例 0 失败**、
  无缓存树上 `pytest -m "not slow"` **796 passed / 2 skipped / 0 failed**（改前 10 failed —— 题集 cache 没入库，
  改成按 `_stable_seed` 现算而不是 skip）。**没提交**：341MB/692MB 的 pkl、训练 zip 与解压重复、
  `benchmarks/lab/cache/` 482 个跑出来的 JSON（已 .gitignore，实测它比当前构建器旧）、`.github/skills` 等本地 agent 工具、
  六个一次性刷子脚本、`miniprogram/`/`css/`/`js/`、以及 `response.mp3` 的删除（留着单独立一笔）。
  台账那条"5 个未提交测试文件归属"到此结案：属于 #191 的后端契约面。
- ⚠ **#191 合 master 会触发云部署**，且 dev/local 固定验证码从 8888 变 **888888** ⇒ 部署后队友登录要用 6 位。

最后更新：2026-10-14（上午续，D-104 + 后端 #23 · **收口两件事：#159 台账锚点内容级重锚搬回 117 条；充值页欠账的后端那半落地**）：
- **后端 PR #190**（分支 `feat/pay-hardening`，worktree 起于 master）：`createPayOrder` 加客户端幂等键
  （同一 `(user, requestNo)` 不新增订单行，待支付重发调起参数、已支付回执 alreadyPaid、换金额换渠道直接拒），
  `queryAlipayOrder` 加归属校验（不是他名下的单与不存在的单同一个读数 `not_found`，且这支会触发放款）；
  新增迁移 V035 补列 + 唯一索引（`information_schema` 守卫，可重复执行，**不在 CI 白名单 ⇒ 需人工执行**）。
  实测 `mvn -o -B -pl yudao-module-transport test` ⇒ **335 例 / 0 失败 / 0 错误**（提交面 325 + 新簿 10），
  并按突变复跑证明新用例不是空跑：摘掉两处闸门 ⇒ 该簿 **6 条转红**，随后已复原（grep 零残留）。
  ⚠ **合 master 会触发云部署**，这一步等用户确认；App 侧写口仍不接（无商户资质）。
- **#159 台账锚点**：ID-MISS **388 → 273**（搬回 117 条：机械规则过的 83 + 人工按原句定的 34），
  在册表 **283 → 248** 行对齐，DA-f 里"判 OK"从 302 涨到 417。闸门 286 → 273 是**按实测下调**，
  锚点总数 / 越界 / 找不到文件三项一个没动 ⇒ 不是把判据改松。
  **不搬的 12 条逐条写了理由**（6 条句子讲的就是"当时锚错了"的病历、3 条候选不止一处、1 条指 KDoc、1 条会同句撞块、
  1 条是工具把类名判给锚点的假报 —— 最后这条登记为工具侧待办）。
  ⚠ **#159 仍 OPEN**：248 条在册等消化，本轮只清掉最容易的一批。
- **CI 豁免的理由改写了**：实测 CI 那一格读 DA-c = 407（本机 15），因为 CI 只 checkout App 一个仓、
  没有后端那一面 ⇒ 留在名单上的理由只剩**环境**，"#159 未清"这半已不成立。
  顺带修掉一条**空转的负控**：DA-a 那条注入原先只写坏一本台账，距 1216 闸门还有 241 余量，
  实际打红的是 DA-d ⇒ 改成七本一起写坏，负控从 △ 变 ✔（复跑 7 ✔ / 0 ✘ / 0 △，`RESULT=PASS`）。
- **本轮改动面**：Java 4 个文件 + V035 + 1 份新测试簿（后端仓）；App 仓只动 6 本台账文本、
  在册表、`check_doc_anchor_probes.sh`、`doc_anchor_negative_controls.sh`、`build-and-release.yml` 的豁免文案
  ⇒ **零 Kotlin 源码改动**，用例面沿用上一轮实测 706 例 0 失败，本轮不重报 gradle 读数。
- **NEXT（顺序不变）**：① 等用户对 PR #190 的合并决定（合 = 触发部署）；② 队友执行 V028/V030–V035；
  ③ 真机那一格（用户 10-14 明示"先不真机检验"）；④ #159 剩余 248 条在册 + 上面那条工具侧假报。

最后更新：2026-10-13（下午续，D-093 · **web 后台第二批：用户钱包只读页，"能改钱"那个口刻意不接**）：
最后更新：2026-10-13（下午续二，D-094 + D-095 · **收口这一轮：后端两笔缺陷落地 + 台账锚点重量一次 + 39 本负控整批跑完**）：
本轮把 App 与 web 两侧欠的账一次推到底，读数如下（**每条都有命令输出，没输出的不写**）。
- **后端（`cargo-post-platform`，本地现读 12 笔未 push）**：D-090 后台编辑订单抹数据（`9f1fa41f`）、
  D-093 钱包只读页（`ee16acc7`）、**D-094 线路站序"先删后判"（`9f828276`）** —— 不带 `stations` 的整线保存会删光这条线全部经停站还报成功；
  改成"没带就不改、显式 `[]` 才是清空"，配 `RouteServiceImplStationsTest` 3 例，**红先证后绿**（撤回补丁 ⇒ Mockito `Never wanted here` 在 `:68`）。
  收口时独立复跑：`mvn -o -q -pl yudao-module-transport test` ⇒ `MVN_EXIT=0`、本轮重写 39 份报告 `tests=359 failures=0 errors=0`。
- **台账锚点（D-095）**：`check_doc_anchor_probes.sh` 自 D-086 起就红 ⇒ `doc_anchor` 那本书的 8 条负控一直是死的。
  基线 `ID-MISS=417 / 带标识符 692 中判 OK 260 / 可疑 aid 367`；用"原句里贴着锚点的代码片段"当判据逐条判，
  **65 条真漂移**入仓（110 行 / 114 个锚点串），改完真身实测 `ID-MISS 321`、`带标识符中判 OK **361**`（≥277 ⇒ **DA-f 转绿**）、
  `FNF 15`（未涨）、`OOR 0`（未新增）；在册表用 `--pin-write` 对齐真实可疑集 `252 → 281`（下调 64、新登记 93）。
  ⚠ **DA-e 仍红：321 > 闸门 286，下限一个字没动**。第三轮把证据提到最严后对最终 284 条逐条分档
  （句中无可定位片段 130 / 片段 ±120 行内无命中 141 / 候选成立 5 / 本就命中旧行 5 / 文件找不到 3），
  那 5 条里 3 条已改、2 条**故意不改**（一处是我自己拿它当反例引用）
  ⇒ 这 35 格只能逐条读句子，#159 继续开着；输入与反例都在 `docs/android/ANCHOR_REVIEW_2026-10-13.md`。
  配套：`doc_anchor_negative_controls.sh:89` 的注入串跟着改（`Models.kt:469 → 497`），
  实测旧串在这份台账上改出 **0** 行差异、新串 **1** 行 ⇒ 不改就是静默落空的死破口。
- **负控整批 39 本**：36 绿、3 本 rc=2（`doc_anchor` 基线红自拒；`notify_result`/`oss_credits` 是 `--tests` 把 `app/build/test-results/` 洗掉）。
  批后跑全量尾巴 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --rerun-tasks` ⇒ 67 份新报告、**694 例 0 失败 0 错误**；
  两本复跑 `notify_result ✔=17 rc=0`、`oss_credits ✔=20 rc=0`。
  ⚠ 全量尾巴退出码 1，红在 `local.properties:13 PropertyEscape` 一条（**本机文件、不在库里**，且当前 `\u8881` 写法是解析成 `袁` 所必需的，
  照 lint 建议改会把 SDK 路径改坏）⇒ 按 §八 的规矩比 id 列表不比 rc，本轮 id 列表就这一条，与历史环境红同形。
- **§6.1 商店位图补上**：`tools/render_launcher_store_png.py` 读**同一份 VectorDrawable 的 pathData**（scale/pivot 从 XML 读不写死）
  渲 1024×1024 无 alpha；四道自查闸逐条负测过（安全圆越界 358.9px / evenOdd 洞没挖开 / pathData 退化 / 无 `<group>` 各自 rc=1，基线与还原 rc=0）。
  诚实边界：像素级不保证与 Android 系统渲染器一致，同源的是**几何**不是位。
- **仍未收口的格子（照实写）**：① 真机那一格仍是 `NOT VERIFIED ON DEVICE`（本机无设备）；
  ② `V028`（钱包两张表）与 `V031`（钱包菜单）**不在 CI 迁移白名单**，队友执行前"云上那一页能看到数据"不成立；
  ③ 两仓本地已提交未 push（写这一段时现读 App 28 笔 / 后端 12 笔；本轮收口提交后再读一次，以读数值为准）；
  ④ `plannedMinutes`/`plannedDurationMinutes` "缺键当 0" 与三个表单 lost update 已排进收口后的功能队列（第 1、2 项）；
  ⑤ `#47 分组 hub` 按"五本台账与产品文档里查不到任何出处"结案为**不是欠账**（不发明需求），本轮不再挂账。

Java 一字未改，全部在 `yudao-ui-admin-vue3` + 一条菜单 SQL。契约逐行读 `WalletController`：
`GET /account/get` 走 `getOrCreateAccount` ⇒ **这个"查询"有写副作用**（账户不存在就建一条，不能当只读探测）；
`GET /transaction/page` 直接返回 `PageResult<WalletTransactionDO>`，`total` 是真分页总数（与 App 侧那个 `items.size()` 不是一回事）；
`POST /adjust-balance` 参数是 `@RequestParam`（发 body 会参数缺失）、**没有幂等键**、`amount == 0` 时两支都不走却返回"调整成功"。
⇒ `api/transport/wallet` **刻意不导出 `adjustBalance()`**，页面顶部一条 `el-alert` 把"这一页只提供查询、不提供改账"和那两条理由写在屏上，
`V031` 菜单也**不给** `transport:wallet:adjust` 配按钮；四格口径与 #56 同一条（缺席≠¥0.00、失败≠没有、空态分三种、Epoch 毫秒格式化）。
**V031 为什么不能直接往基线加一行**：`transport-menu.sql:130-146` 是裸 `INSERT ... VALUES`（固定主键），
而 `deploy-dev.yml:200` 每次 push master **无条件重放**它 ⇒ 追加一行会让第二次部署红在迁移步（与 V029→V030 同一个理由），
所以写成 `WHERE NOT EXISTS` + `INSERT IGNORE`。⚠ 但"可重复执行"只核了**语句形状**——本机没有 mysql 客户端（`which mysql` 空），**没跑过第二遍**，不写成已验证。
**ts:check 做过一次自证**：全项目 `npm run ts:check`（vue-tsc --noEmit）**TS_RC=0 / 0 error**，
可"0 error"在文件根本没进编译程序时也一样成立 ⇒ 往新页面塞一行类型错，ts:check **当场报 `views/transport/wallet/index.vue(84,7)` 三条错、RC=2**，
还原后回到 RC=0，`grep -rn __negctl src/` = **0** 证明残留清干净 ⇒ 这一页确实被类型检查扫过。
部署前置：`V031` 与它依赖的 `V028` **都不在** CI 白名单（现读 V018/V019/V021/V022/V024/V025）
⇒ 在有人执行这两份 SQL 之前"云上打开这一页能看到数据"这句不成立，页面会报错而不是显示空表（与 D-086 那格同一条账）。
仍欠：**这一页没有被浏览器打开过**（本机没有跑起来的前端 + 后端 + 已建表的库）；改账口要不要做是用户/队友的决定；
web 侧**没有任何自动化判据/负控**（App 那套机制在这儿还不存在）⇒ 本轮凭证只有 ts:check 一份；
39 本 App 负控整批未重跑；`#47` 口径与 `assets/logo.png` 换不换仍等用户；两仓本地已提交未 push（现读 App 27、后端 11）。
NEXT：① 39 本负控整批重跑 → ② web 侧继续（要么给后台补一套结构判据，要么按同一口径再补 1-2 个"看着有其实是空的"页面）→ ③ 等 #47 口径。
<!-- 上一条 D-092（四笔 ✘ 归因 + D-CE-4 关掉）及更早的「最后更新」段落保留为历史，不覆盖 -->
最后更新：2026-10-13（下午，D-092 · **四笔挂了整天的 ✘ 全部归因完毕，D-CE-4 关掉**）：
`default_value ✘=3` + `idempotency ✘=1` 从 D-082 起每轮都写"仍未归因"，逐条查完的结论是**四条里没有一条是判据真的不判**：
`DV-y6t` 锚点被文案改写顶掉（批日志第 55 行就有 `OLD-MISS`，现话术是「暂无任务段」）；
`I-f` 同因且更糟——那本书三条页面话术注入全用**裸 `sed -i`**，落空一声不吭（现话术已缩短成「未发给后端，显示的是当前状态」）；
`DV-v` 是**上限高于实测**（判据 ≤132/165/33/42，census 现读 **127/152/32/38** ⇒ 注入一个孤立键离天花板还差 4~12 格）；
`DV-h` 是**那一次运行本身失败**（判据末尾只有 exit 0/1，254 不可能是它给的结论；两次独立复刻 + 本轮整本重跑都正常红 ⇒ 没复现就不写"已修"）。
修完实测：`idempotency` rc=0 **✔=44 ✘=0 △=0**、`default_value` rc=0 **✔=70 ✘=0 △=0**。⇒ **"整批里有未归因 ✘"这句从本轮起不再成立**。
**D-CE-4 关掉**：新建 `tools/status_negative_controls.sh`（20 条破口 + 基线 + 锚点自检）**rc=0 ✔=22 ✘=0**。
配负控的前置动作是**收紧松闸**，这就是"一本书没有配对负控"的真实代价：`S-e ≥6`(现读 10)、`S-i ≥20`(现读 **65**，差 45 格)、
`S-j ≥1`(4)、`S-k ≥15`(17)、`S-lc ≤3`(2)，还有一处**判定与话术不一致**（`S-ld` 话术"≥3"、判定 `-ge 2` ⇒ 对齐成 3）；
同轮把 `DV-k ≤111` 收到现读 **107**。
**新负控第一版 20 条里 8 条不成立，全是这本书自己的毛病**（§八 (53)）：① 注入形状必须命中判据的正则——
`if (orderStatus == 4)` 对 `[^a-zA-Z]status (==|>=|<=) 数字` 不命中（字母贴着 status），判据正确地不红却被误记成"不判"；
② `@Test` → `//@Test` 对 `grep -c "@Test"` 完全无效（字样还在）——idem 的 `H-g` 早就登记过这一形状，新书第一次还是踩；
③ SELF-MATCH 第三次挡掉合法注入（`enum class X` → `// enum class X` 含原锚点）⇒ 改名类破口要换成不含锚点的写法；
④ 地板不收，破口就构造不出来 ⇒ **"有 65 处引用"这件事在收闸之前从来没被量过**。
**读数**：判据全批 **34 本 = 33 绿 / 1 红**（红的仍是 doc_anchor，#51 三条路等用户定）；负控本数 glob 现读 **39 本**；
本轮**没动 `app/src/**`**（只改 tools 与台账）⇒ 不重跑用例与打包，产物仍是 D-091 那份（67 类 / 694 例 / 0 失败、APK `117,227,100 B` / `d62b8288…`）。
仍欠：**39 本负控整批未重跑**（新加的 status/favorite/launcher 三本只在单独跑时绿过；上一遍整批是 D-087 那次 36 本、2h49m）；
`DV-h` 没复现也没修；收紧地板的代价已生效（以后删一处枚举引用、注释一条用例都得同轮重登）；
`#47` 口径与 `assets/logo.png` 换不换仍等用户；两仓本地已提交未 push（本条落地前现读 App 25 / 后端 10）。
NEXT：① 39 本负控整批重跑（把三本新书纳入常态批次）→ ② web 后台：钱包 `/adjust-balance` 那个"能改钱"的口没有页面 → ③ 等 #47 口径。
<!-- 上一条 D-091（图标换向 + 收尾必须带 lint）及更早的「最后更新」段落保留为历史，不覆盖 -->
最后更新：2026-10-13（中午续，D-091 · **#43 后半：图标换成方向 2（车头 + 弧线），过程里被自己的判据与 lint 各抓一次**）：
`ic_launcher_foreground.xml` 整幅重画（白色车头 + 两个 evenOdd 镂空车灯 + 弧线路线 + 站点圆点）、
新增 `ic_launcher_background.xml`（满幅线性渐变 `#0E5A52 → #17A07A`，就是选那两色）、`ic_launcher.xml` 背景改指它、
退役的 `bus_launcher_bg` 从 colors.xml 删掉（App 主色板另有 `bus_primary`，不受影响）。
**纠正两条我自己写错的判断**：① README 说"monochrome 复用前景会糊成一坨"是没核的推测——旧前景糊是因为里面有**第二层不透明深蓝**，
新前景只有一种白色、镂空是透明洞 ⇒ 复用前景正确，改为用判据 IC-e 钉住"复用成立的前提"（不许出现第二种 fillColor）；
② "各密度位图/round 要补"不成立（minSdk 26），IC-g 反过来把**故意没做的两件事**（各密度位图、商店 1024 图）钉成可见状态。
**形状没被眼睛看过**：本机无可视化面（in-app browser `visibilityState=hidden`、无 SVG 光栅化工具）⇒ 只有数值验证：
按 `scale 0.86 / pivot(54,54)` 变换后逐点算，最远点（站点圆点边缘）距心 **32.51 ≤ 33** 安全圆 ⇒ 圆/圆角遮罩都切不到内容；
包围盒 x 27.3..78.6 / y 30.8..82.9 全在画布内，光学中心偏下 2.8dp（弧线把重心往下带，刻意没调）；真机桌面仍 `NOT VERIFIED ON DEVICE`。
**lint 抓出一条上两轮漏的**：`PickupWindow.kt:107 LocalDate.ofInstant` 是 **API 34**（min 26）⇒ NewApi error。
根因是收尾命令组合：D-087/088/090 我跑的是 `assembleDebug + testDebugUnitTest`，**没带 lintDebug** ⇒ 694 例全绿挡不住一个 API 违规。
改成 `LocalDateTime.ofInstant(...).toLocalDate()`，并加判据 **PW-o**（全仓该 API 名 0 命中 + 正对照恰好 2）。
⚠ 同族小坑两条：注释里原样写那个 API 名会被 grep 数进去（"0 命中"变 1）；`local.properties:13` 的 `PropertyEscape` 是**本机文件、不在 VCS**，
改它有写坏 SDK 路径的风险 ⇒ 不动它，登记"本机 lint 常态 1 条环境红"，**以后比 lint 要比 error 的 id 列表，不比 rc**。
**判据自己被抓两次**：IC-g 期望值凭手感写 1、现读 2（新书第一次跑就红在自己写的数字上，§八 ㊿ 第三次兑现）；
IC-c1 注入"大圆角底板"而判据只认 `rx=` ⇒ 红了隔壁 IC-e ⇒ 不是撤破口，是把判据改成"不许出现半径 ≥16 的圆角弧"（本作品最大圆角 9）；
另 `mkpng` 把目录建到 `res/` 上一层 ⇒ 判据扫不到 = **"注入位置错"这一族 D-084 的新变体**（同样靠"仍然绿"暴露）。
**读数**：`assembleDebug + testDebugUnitTest + lintDebug --rerun-tasks` **55 tasks executed**、用例 **67 类 / 694 例 / 0 失败**，
但整条命令 **RC=1**（lint 剩 1 error = PropertyEscape 环境红；我那条 NewApi 已消失，error 2→1）；
新判据 `check_launcher_icon_probes.sh`（IC-a…IC-g）rc=0；新负控 `launcher_icon_negative_controls.sh` **rc=0 ✔=17（16 条破口 + 基线 + 锚点自检）✘=0 △=0**；
判据全批 **34 本 = 33 绿 / 1 红**（红的仍是 doc_anchor）；APK **117,227,100 B** / `d62b8288…`，对 D-088 **+1,434 B ⇒ 增量未归因**；
树指纹 files=**204** / `63782b9c…`（+1 = 背景层 drawable）。
仍欠：桌面真机效果未验；`assets/logo.png`（启动页/登录页用的**用户原图**，KDoc 明写"不重绘/不复刻/不抠底"）与新图标不同源——
**换不换它是用户的决定，我不重绘用户提供的原图**；商店 1024 图仍缺（本机无矢量光栅化工具，不该拿再生成的概念稿冒充导出图）；
`default_value ✘=3`/`idempotency ✘=1` 未归因；本地已提交未 push（现读 App 22 个、后端 10 个）。
NEXT：① 等 #47 口径（唯一还堵着的功能项）；② web 后台：钱包 `/adjust-balance` 那个"能改钱"的口没有页面；
③ 收尾命令从此固定带 `lintDebug`，并把"比 id 列表不比 rc"写进规矩。
<!-- 上一条 D-090（web 后台第一批：编辑订单抹数据）及更早的「最后更新」段落保留为历史，不覆盖 -->
最后更新：2026-10-13（中午，D-090 · **转做 web 后台第一批，抓到一条真在抹数据的缺陷**）：
App 这批只剩两件等拍板（#43 选型、#47 口径），按"别忘了 web 端"转做 `yudao-ui-admin-vue3` 的 transport 模块。
**清点结果（只认 `cargo-post-platform` 这一棵树）**：19 个 api 模块**全部有消费者**、views 里 `request.*` **0 命中**（HTTP 全走 api 层）、
TODO/mock/假数据/生产 `console.log` **0 命中**、21 条菜单 SQL 组件路径逐个存在、8 个全 CRUD 控制器**都有页面** ⇒ 这一片是干净的。
**真缺陷**：后台"编辑订单"静默抹子表数据。根因与 App 侧 #57 同一族——`TransportOrderRespVO` 只回读部分子表字段，
而 `OrderForm.vue` 是 `resetForm()` 打回默认 → `{...formData, ...响应}` → 整个 PUT 回去，于是"没回读"被当成"用户清空了"：
① `updatePostalOrder:553` 守卫看 `mailNo == null`，表单发的是**空串** ⇒ 放行 ⇒ **承运商被写成空串、件数被 `!= null ? : 1` 兜成 1**（库里 5 件变 1 件）；
② `updateCargoOrder:529` 把缺键的 `freshFlag` 判成 **false** ⇒ 生鲜单静默降级（生鲜正是承运审核走人工的开关）；
③ 客运三键整块不回读 ⇒ `updatePassengerOrder:513` 因 `passengerCount==null` 直接 return（**后台改客运单的人数/联系人根本改不动**），
而一旦填了人数，联系人就被空串抹掉。
改法：RespVO 补 6 键 + `toVO` 按 orderType 三分支（新增客运分支、注入 `PassengerOrderMapper`）+ 更新侧一律"没带就不改"（默认值只留在创建路径）。
**读数**：`TransportOrderServiceImplTest` 17→**19 例**，两条新用例**先红后绿**——改回旧写法重跑，红的正是被抹掉的两个值
（`expected: <null> but was: <1>` / `expected: <null> but was: <false>`）；恢复后 19 例全绿 / `BUILD SUCCESS`；`mvn -o -pl yudao-module-transport compile` rc=0。
⚠ 后端工作树有 **47 个别人的未提交改动**（algorithm/**、application-*.yaml、VehicleForm.vue、一个被删的 mp3）⇒ 只 `git add` 我改的 4 个路径，**没有 `add -A`**；
提交 `9f1fa41f` 本地未 push（推 master 触发云端部署）。
**实测否掉一条子代理结论**（记下来，因为差点照着改）："派单方案表『里程口径』那列因 RespVO 没有 `routeProvider` 而永远 warning" —— 不成立：
那列读 `/plan/page`，而 `DispatchController:81` 该端点**直接返回 `PageResult<DispatchPlanDO>`**，DO 有 `routeProvider:36` 且 `:3792` 真写入 ⇒ 列是真值。
真实漂移只剩形状：详情用的 `DispatchPlanRespVO` 缺 `score`/`routeProvider`，TS 反过来缺 5 个键，而 `/plan/get` 在 views 里 **0 调用者** ⇒ 当下不可见，登记不当 bug 修。
**web 侧仍在册缺口**：① `WalletController`（含 `/adjust-balance`，**能改钱**）没有后台页面 —— 全 CRUD 控制器只有它没页；
② 3 个空壳目录（`api/transport/resource`、`api/transport/settlement`、`views/transport/settlement`）+ 12 个落在非空目录里的 `.gitkeep` 残留；
③ `expiry`、`dispatch-center` 两页没有自己的 api 模块（复用别的，不是缺陷，但按"一页一模块"写判据会假红）。
仍欠：本轮**没跑 App 侧判据与用例**（`app/src` 一字未动，上一份读数仍是 D-088 的 67 类 / 694 例 / 0 失败 + 判据 33 本 = 32 绿 / 1 红）；
后台侧**没有自动化判据**（App 那套 check_*/负控机制在 web 端还不存在）⇒ 本轮的"绿"只有 mvn 一份凭证；
`default_value ✘=3`/`idempotency ✘=1` 仍未归因；两仓本地已提交未 push（App 21 个、后端 10 个）。
NEXT：① web 后台——先给"改钱"的钱包口补带权限的页面（或明确它不该存在）；② 清 15 处 `.gitkeep`/空壳目录残留；
③ 等 #43 图标选型与 #47 口径；两件等到之前不再在 App 侧猜需求。
<!-- 上一条 D-089（#43 图标候选 + #47 查无定义）及更早的「最后更新」段落保留为历史，不覆盖 -->
最后更新：2026-10-13（上午续，D-089 · **#43 前半交付：三个图标方向等挑；#47「分组 hub」查无定义 ⇒ 不猜**）：
NEXT 首项 `#47 分组 hub` 在 `DECISION_LOG` / 审计 / `docs/product/FEATURE_MARKING_SPEC_2026-10-12.md` 里
`grep "分组 hub|hub 分组|分组页"` = **0 命中**（`#47` 只在状态板自己的 NEXT 行出现过）⇒ 按"宁可空着也不猜需求"本轮**不实现**，
登记成等用户一句话口径：是要一个"把所有入口按分组列在一页"的更多功能页，还是把首页宫格按业务分组重排（两者做法相反）。
⚠ 这一格与 #39 直接冲突：D-088 刚把首页那个与"个人中心"同做 `onSelectTab(3)` 的重复"更多功能"格换成收藏入口
⇒ 若用户要的是 hub 页，入口得**新增**（宫格变 9 格或再换一格），**不能**退回那个重复格。这一格现在是**故意留的已知缺口**。
**#43 前半**（用户明确要求"我出 3 个候选给他挑"）：交付 `docs/design/icon-candidates/`（README + 3 张 1024×1024 概念稿，721,519 / 678,925 / 933,620 B）：
① 站牌+货箱（延续深蓝白品牌，48dp 下空心站牌板会糊）② 车头+弧线（最干净、小尺寸最清楚，但**没有货/邮语义**且渐变底与 `bus_launcher_bg #123F6E` 不搭）
③ 单线字形（最有辨识度、适合独立 monochrome 层，但左半边读不出"车"、米白底在深色桌面发灰）。
⚠ **三张都不能直接出厂**：带"Qoder AI 生成"水印、画面被烤成圆角方块，而自适应图标要**满幅 108dp**（圆角与遮罩由系统裁）；README 写了选定后一次做完的四件事。
**纠正两条我自己的过期账**：① 按记忆以为"图标文档还没改"，实测 `PRODUCTIZATION_COMPLETE.md §6.1:397-404` **10-12 就改对了**，
且 `minSdk = 26`（`app/build.gradle.kts:74`）⇒ **"补各密度位图 + ic_launcher_round"这两项根本不需要**（API 26+ 只认 adaptive icon，
`roundIcon` 与 `icon` 同指一个 mipmap 是正常做法）⇒ 记忆条目已就地改成真实剩余项：选方向 / 前景层 108dp 满幅重画 /
**monochrome 单独画**（现在 `<monochrome>` 复用前景，Android 13 themed icon 会糊成一坨）/ 收敛第二套标志 `assets/logo.png`（87,912 B，启动页+登录页共用）；
② 同轮把 D-087 里"负控整批**第一次**跑完"那句改成"重跑"——**史上第一次整批是 D-082（10-12 22:06，那时 33 本）**，
本轮是之后的首次重跑（现读 36 本），欠的那格是"没重跑"不是"没跑过"。
**又踩一次 §八 ㊿①**：这次不在判据簿里，而在 `python -c "…"` 的双引号里放了反引号包着的 git 命令 ⇒ bash 直接执行了它，
把要写进台账的句子洗成半句话（数字倒是"对上了"，因为那真是命令输出）。⇒ **这条规矩适用于本工作区任何 shell 命令行，不只判据簿**。
**读数**：本轮**没动 `app/src`**，所以不重跑用例（跑了也不会有新信息，产物仍是 D-088 那一份 67 类 / 694 例 / 0 失败）；
判据全批现读 **33 本 = 32 绿 / 1 红**（红的仍是 `check_doc_anchor_probes.sh`），与 D-088 收工时一字不差；
README 里那两条关键引用**没被机器核**（`check_doc_anchors.py` 对这份新文档报 `anchors=0`，它不扫这个目录），
是**直接读文件**核的：`app/build.gradle.kts:74 minSdk = 26` ✓、`PRODUCTIZATION_COMPLETE.md:397-404` ✓。
仍欠：#47 等口径；#43 后半等挑；`D-CE-4`（`check_status_probes.sh` 没有配对负控）、`default_value ✘=3`/`idempotency ✘=1` 未归因、
doc_anchor 在册红；两仓本地已提交**未 push**（现读 App 仓 20 个、后端仓 9 个未推提交）。
NEXT：等 #47 口径 / 等图标选型 → 两件都在等的时候，转去做**不依赖外部条件**的面（web 后台管理平台的完善排在 App 这批之后，
但 App 这批现在只剩这两件等拍板的事 ⇒ 下一轮先做 web 后台，不再在 App 侧猜需求）。
<!-- 上一条 D-088（#39 收藏入口）及更早的「最后更新」段落保留为历史，不覆盖 -->
最后更新：2026-10-13（上午，D-088 · **#39 收藏线路首页入口做完，用例先红抓住的是我自己纯层里的洞**）：
契约三件事：收藏是**纯本机**（`Prefs.kt:233-259` 逗号分隔 id 存 DataStore；`yudao-module-transport` 的 app 端点包、`yudao-module-member`、
transport 建表 SQL 三处 **0 命中** ⇒ 界面没资格说"已同步/换机恢复"；⚠ `ruoyi-vue-pro.sql:2667` 那条"收藏"是上游自带的后台菜单种子，**不是接口证据**）；
线路**名字只在** `GET /app-api/transport/bus/lines`（`Repo.kt:888-893`）里、本地只有 id ⇒ 首页那一格不显名字也不显计数（显名字要为它多取一跳，会撞 MockWebServer 按序队列那几本计数类）；
`BusScreen.kt:715-717` 旧闸门是 `favLines.isNotEmpty()` ⇒ 列表取失败时整栏消失，**"没取到"被界面念成"没有收藏"**（D-079/D-080/#42 同族）。
落地：纯层 `ui/user/FavoriteLines.kt`（三句文案唯一出处 + `favoriteRowVisible`/**整栏闸门改成"本机记着几条"**/`favoriteUnresolvedText` 两支）；
首页那一格用现成 `FeatureTile` 且**不设登录闸门**（读的是本机文件），并把与"个人中心"同做 `onSelectTab(3)` 的重复格"更多功能"换掉（宫格总数仍 8，不加第九格）；
`BusScreen` 新增 `linesError`（旧写法只取 `lf.items`，把 `Feed.error` 洗掉了），那一栏拆成"本机 N 条 / 这一轮显示 M 条 / 为什么显示不出来"。
**用例先红**：`favoriteUnresolvedText(3,1,"")` 第一版走"下架"支——纯层用 `linesError != null` 判失败，而**空串是"失败但没原因"**；
修的是纯层（`isNullOrBlank` 归失败支 + 补"没给出原因"），**不是放宽断言**；第二次跑 9 例全绿。
**工具自纠两条（§八 (52)）**：① Kotlin 块注释**可以嵌套**——KDoc 里那种 `` `.../controller/app/**` `` 路径通配串里的 `/*` 会再开一层，
末尾 `*/` 只闭合内层 ⇒ 整本 "Unclosed comment"，而高亮看着正常；为这条写的自查扫描器**第一版误报 178/180 本**（注释态里仍按字符串规则跳），
修好后现读 180 本 unclosed=0 ⇒ **"用来证明没问题的脚本"第一次大面积红要先怀疑脚本**；
② **逐本日志在批跑完前是不完整的**：我先读 `d088_neg_throwable.log` 把它记成 ✘=1（跨本回归假象），批跑完再数是 ✔=18 ✘=0 △=1（与 D-087 整批一字不差）；
另 `FE-d1` 锚点在我改纯层那句话后落空（当场报 MISS 没静默，D-084 ⑥ 同族）⇒ 规矩：**改纯层文案与负控锚点必须同一次编辑里对**。
**新登记缺口**：`check_status_probes.sh` **没有配对负控簿** ⇒ 它对 HomeScreen 那个"恰好 2"只能证明"复跑没红"，不能证明"判得动"。
**读数**：新用例簿 **9 例**；`check_favorite_entry_probes.sh`（FE-a…FE-h）rc=0；`favorite_entry_negative_controls.sh` **rc=0 ✔=16（14 条破口 + 基线 + 锚点自检）✘=0 △=0**；
`SU-a` 66→67、`SU-b` 68→69、`@Test` 行 684→694；受影响负控逐本复跑 `contract_defaults` 22✔、`eta` 61✔、`navigation` 143✔、`safe_call` 29✔、`shell_routing` 32✔、
`driver_notice` 13✔、`throwable` 18✔(△=1 在册)、`test_suite_hygiene` 7✔，全部 ✘=0；判据全批 **33 本 = 32 绿 / 1 红**（红的仍是 doc_anchor，#51 三条路等用户定）；
全量 `--rerun-tasks` **rc=0 / 45 tasks executed / 67 类 694 例 0 失败**（67 份 XML mtime 全为 09:02:13；685→694 = 新簿 9 条）；
APK **117,225,666 B** / mtime `09:02:06` / sha256 `1df8353d…`，对 D-087 **+16,384 B ⇒ 增量未归因**（没做类数差集，不写"收藏值多少字节"也不写回归），
符号级 `Lcom/shanxiang/buspost/ui/user/FavoriteLinesKt;` 在 `classes13.dex` = **2**；树指纹 files=**203** / `4c5ee97a…`（D-087 是 201/`e76e2220…`）。
仍欠：一律 `NOT VERIFIED ON DEVICE`（首页那一格没在真机上看过）；**后端没有收藏接口这件事只能披露不能修**（真同步要加表 + 端点 + 归属，是要用户/队友拍板的共享面）；
`default_value ✘=3`/`idempotency ✘=1` 仍未归因；两仓本地已提交**未 push**。
NEXT：① #47 分组 hub → ② #43 图标（我出 3 个候选）→ 之后是 web 后台管理平台。
<!-- 上一条 D-087（#57 取件时间窗）及更早的「最后更新」段落保留为历史，不覆盖 -->
最后更新：2026-10-13（早，D-087 · **#57 取件时间窗做完，另外把从 D-068 欠了五轮的“负控整批未跑”还上了**）：
`earliestPickupTime` 这一格此前在 App 一条代码都没有（`buildSendForm()` 17 个键里没它），逐行读生产者读出四件事：
**① 只写不读** —— `AppSendOrderCreateReqVO.java:76` 收、`TransportOrderServiceImpl.java:149` 落库，而 `AppSendOrderRespVO`（`/send/page` 与 `/send/track` 共用 `toRespVO`）**整本没这个键**
⇒ 用户填完在“我的寄货”里查不回来，本轮把它补成可读（后端单独一个 commit，⚠ **推后端仓 = 触发云端部署**）；
**② 它会真的改派单**（不是备注）—— `DispatchServiceImpl.java:4076-4090` `filterByTaskWindow`（调用点 `:591` 在 `doCreateSmartPlan` 里）
晚于窗口结束就本批不派并记理由串，`:3513` 又作为 `readyTime` 下发算法；**③ 出网只能是数字毫秒** —— `TimestampLocalDateTimeDeserializer.java:24-27`
对非数字串**不报错、静默返回 0** ⇒ 发“2026-10-13 15:00:00”不会 400，会安静存成 1970 年（回读侧同理：**只认 `Number`**，`multiNum` 把串 coerce 成 0 是同一病的另一半）；
**④ 时区不用猜**：`docker-compose.yml:21`/`Dockerfile:12`/JDBC 三处都是 Asia/Shanghai ⇒ 本机按 `PICKUP_ZONE` 生成。
⚠ **一句账面结论被代码否掉**：`V024:10` 注释写着入池 `ORDER BY earliest_pickup_time`、索引也建了，但全仓 `orderByAsc(...getEarliestPickupTime)` = **0 处**
⇒ 这句不当结论引用。小程序对这个键 **0 命中** ⇒ 无界面形状可对标，字段语义只有“下限”，所以界面不做“上午/下午”那种假装成区间的选项。
落地：纯层 `ui/user/PickupWindow.kt`（过去的整点**从候选里消失**而不是置灰、提交前再拒一次过去/零/负、三格文案、只认数字的回读）
+ `SendScreen` 的 `PickupWindowPicker`（「不限制」是唯一撤销出口；草稿只存“第几天+整点”不存绝对毫秒，隔夜恢复自动过期）
+ 成功页单独一行回执把“发没发/后端回读没回读”分开说 + `SendOrder.earliestPickupMillis`（可空）与包裹卡那枚**只在有值时出现**的 chip
+ 演示报文只给第 2 单带这个键（“有值”与“没给”两格都演到）。
**一条结构性后果而不是文案**：`Repo.userCreateKey` 是 SHA-256 覆盖报文每个键值 ⇒ 这一键只要含“现在”的分秒，每次点击都换一把幂等键，
「同一份寄货单还在创建中」那道本地闸就会被顶开；本轮取值按整点对齐（`plusDays` + `atTime(hour, 0)`）所以闸仍然有效，
这个属性现在有判据（PW-n）也有具名用例（第 17 条），负控两条各抓一种破法（混进分/秒位移、不再整点对齐）。
**负控整批重跑（史上首次是 D-082 的 33 本，本轮是那之后的首次重跑，现读 36 本）**：`run_all_negative_controls_batch.sh` 现读 **36 本**（05:04→07:53，约 2h49m，退出码 1，逐本读数 `/tmp/d087_allneg.log`）：
**33 本 rc=0 且 ✘=0**（含新建 `pickup_window` ✔=28、`navigation` ✔=143、`mission_route` ✔=52、`eta` ✔=61、`wallet` ✔=29、`parcel_ticket` ✔=42）；
**2 本 rc=0 但自带 ✘**：`default_value` ✘=3、`idempotency` ✘=1 —— 就是 D-085/D-086 记的那两笔，**整批跑完没有把它们解释掉也没变多**；
**3 本 rc=2**：`notify_result`/`oss_credits` 的红因是**产物不在场**（就地 `--tests` 过滤的几本把 `test-results/` 洗掉，这两本进门先要求指定 XML 在场、缺就拒绝而不填 0）
⇒ 收尾重跑全量恢复产物后复跑：`notify_result` rc=0 ✔=17 ✘=0（破口=12 反向=2 预检=2|命中=16 失效=0）、`oss_credits` rc=0 ✔=20 ✘=0（13/2/4|命中=19 失效=0）
⇒ “rc=2 是产物问题”这句现在有实测；`doc_anchor` rc=2 ✘=1 是 #51 在册红的另一种形状（`DA-f` 正控掉了），仍等用户定路线。
**新负控抓到自己在两条（§八 (51)）**：① `PW-k2` 与 `PW-m1` 注入的是**同一段代码**（一个破口起了两个编号）⇒ 撤重复那条，
撤之前先问“这条抓的是哪一格”；② `sub` 的 SELF-MATCH 保护挡掉了一条**合法**注入（想在 `.atZone(now.zone)` 后接 `.plusMinutes(3)`，新文本必然含旧锚点）
⇒ 换两行锚点绕开，代价是锚点开始吃行尾形状（CRLF 树上会当场 MISS，不会静默）。
**读数**：新用例 **17 例**；`check_pickup_window_probes.sh`（PW-a…PW-n）rc=0；`pickup_window_negative_controls.sh` **rc=0 ✔=28（26 条破口 + 基线 + 锚点自检）✘=0 △=0**；
`SU-a` 65→66、`SU-b` 67→68（唯一增量 = 新用例簿）；同轮修掉 hygiene 那本里一条**消息里的反引号被 bash 当命令替换执行**（⚠ 这是 §八 ㊿① 的同族，
发生在 D-086 之前就存在的那本旧簿上：它打出一行“No such file or directory”，而消息里那个文件名被吞掉，台账里留下的“新增 ，”就是它）；
判据全批（产物恢复后）**32 本 = 31 绿 / 1 红**；全量 `--rerun-tasks` **rc=0 / BUILD SUCCESSFUL in 1m 12s / 45 tasks executed**，
产物 **66 类 / 685 例 / 0 失败 / 0 错误**（66 份 XML mtime 全为 08:20:43；668→685 = 新簿 17 条，其余 65 类一字未变）；
APK **117,209,282 B** / mtime `08:20:35` / sha256 `828bd62d…`：对 D-086 那包**字节相同而 sha 不同** ⇒ 同字节只说明体积没变，
符号级凭证是 `Lcom/shanxiang/buspost/ui/user/PickupWindowKt;` 在 `classes13.dex` = **2**；树指纹 files=**201** / sha256 `e76e2220…`
（D-086 是 199/`abb6706…`，+2 = 新纯层与新用例簿）。
仍欠：**没有一条真接口回包**（“填了它真的被挪出当批”要真调度日志、“回读键真回来了”要云上部署那枚后端 commit）；一律 `NOT VERIFIED ON DEVICE`；
后端那三条未动（`@Future` 缺席、App 没有 `latestDeliveryTime` 入口⇒`expired` 那一支对用户端单**永不生效**、班次窗口无 app-api 生产者）；
`default_value ✘=3`/`idempotency ✘=1` 仍未归因；本地已提交**未 push**（App 两个 commit + 后端一个，推后端仓要单独提醒会触发云端部署）。
NEXT：① #39 收藏首页入口（契约已先行取证：收藏是纯本地 CSV `Prefs.kt:233-259`、后端 **0** 个收藏端点、`HomeScreen.kt` 对 favorite **0** 命中、
四本锚点落在 HomeScreen 的判据簿要逐本复跑）→ ② #47 分组 hub → ③ #43 图标（我出 3 个候选）→ 之后是 web 后台管理平台。
<!-- 上一条 D-086（#56 钱包）及更早的「最后更新」段落保留为历史，不覆盖 -->
最后更新：2026-10-13（凌晨续二，D-086 · **#56 钱包 / 交易记录做完，但先推翻了一条台账前提**）：
"钱包支付代码就绪"那句（含 `docs/product/FEATURE_MARKING_SPEC_2026-10-12.md:17-19` 与我的记忆）指的是
**`cargo-post-platform/android-app/` 那份陈旧副本**（其 `WalletScreen.kt:98` 还会在充值成功后自己插一条流水），
正式仓 `cargo-post-android` 里 wallet 0 命中 ⇒ 本轮按"从零接"做，记忆已就地纠正。
这一版**只读**（余额四格 + 流水列表 + 两个取数各自成格），三个写口刻意不接且**理由写在屏上**：
`/recharge` 无幂等键（重复点击重复加余额）、`WalletServiceImpl:53-58` 把模拟到账写成 `pay_method=alipay`+成功
而 `V028` 没有"来源/是否模拟"列（⇒ 界面没有任何依据能标"模拟模式"，硬标就是本机替后端下结论）、
`query-pay-order`/`PayService:182-200` 不校验归属人且 status=0 会放款。
四条如实口径：缺席金额念「后端没给」不显示 ¥0.00、`status` 按 `V028:31` 注释出码表且未知码原样带出、
渠道那一格只照抄后端原词不补"已扣款"、`total` 就是 `items.size()` ⇒ 只说"本次返回 N 条（未查全量）"。
读数：`WalletRowsContractTest` 16 例；新判据 `check_wallet_probes.sh`（W-a…W-j）rc=0；新负控
`wallet_negative_controls.sh` **✔=29（28 条破口 + 基线）✘=0 △=0**；同轮重登 `SU-a` 64→65、`SU-b` 66→67
（`test_suite_hygiene` 负控复跑 7✔/0✘）；判据全批 **31 本 = 30 绿 / 1 红**（红的仍是 `check_doc_anchor_probes.sh`）；
全量 `--rerun-tasks` rc=0 / 45 tasks executed / **65 类 668 例 0 失败**；APK 117,209,282 B /
sha256 `59086c9e…`（对上一包 +32,768 B 登记为增量未归因）。
⚠ 新簿又抓到两条工具自身的洞（§八 ㊿）：判据 `echo` 里的反引号会被 bash 当命令替换执行；
"恰好 N"的期望值凭手感写会与判据空转同类。仍欠：没有真接口回包、一律 `NOT VERIFIED ON DEVICE`、
三个写口与后端那三条等用户/队友决定、负控全批 35 本未重跑、本地已提交**未 push**。
NEXT：③ `earliestPickupTime` 取件时间窗 → ④ #39 收藏首页入口 → ⑤ #47 分组 hub → ⑥ #43 图标（我出 3 个候选）
→ 之后是 web 后台管理平台。
<!-- 上一条 D-085（#55 票面）及更早的「最后更新」段落保留为历史，不覆盖 -->
最后更新：2026-10-13（凌晨续，D-085 · **NEXT 首项做完：#55 查件票面**）：`Repo.parcelTopology()`（`Repo.kt:918`，
`GET /app-api/transport/send/topology`）此前全仓 0 消费者 ⇒ 补的是读它的那一页（判定全在纯层 `ui/user/ParcelTicket.kt`，
页面 `ParcelTicketScreen.kt` 只渲染，入口是追踪页那一行「看联运票面」、全工程恰好一个导航调用点）。
逐字段读生产者读出**三处真接口下永远念空/念错**：交接的车牌与时刻认的是自造键（后端发 `fromPlateNo`/`toPlateNo`/
`handoverCompletedAt`）、时间线认 `label/title`（后端发 `eventTypeName`/`detail`）、时刻裸切串把 Epoch 毫秒切成
"17590" 上屏。口径四条：总时长含"每次换乘 +20 分钟"（@Schema 没写）、里程必须随段 `navigationSource` 标估算、
`candidates` 缺失只说"没给"、**非归属人后端返回全 null 空结构而不是错误码** ⇒ 页面第四格 `Absent` 只说"这次没给内容"，
绝不念成"包裹不存在"；`Failed`/`Absent`/`Shown` 三格都留「回追踪页」出口（判据钉恰好 3 处，这是用户要求的
"任务出岔子必须有东西兜底"的落点）。
读数：新簿 `check_parcel_ticket_probes.sh`（PT-a…PT-l）rc=0 + 新负控 `parcel_ticket_negative_controls.sh`
**✔=41 ✘=0 △=0**；`ParcelTicketContractTest` 15 例；判据全批现读 **30 本 = 29 绿 / 1 红**（红的仍是
`check_doc_anchor_probes.sh`，#51 三条路等用户定）；受本轮源码文本影响的 4 本旧负控逐本复跑全绿
（`contract_defaults` 22✔、`eta` 61✔、`test_suite_hygiene` 7✔、`reach` 由 ✘=1 转 ✘=0 —— 顺手把 D-082 那三笔
未归因 ✘ 里的一笔归了因：它的 `sed` 落空了一整轮、被记成"判据不判"，登记 `D-CE-2`）；
全量 `--rerun-tasks` **rc=0 / 45 tasks executed / 64 类 652 例 0 失败**（637→652 唯一增量 = 新增 15 条用例）。
同轮重登：`SU-a` 63→64、`SU-b` 65→66、`T-ad1` 6→7；`DemoData` 交接段改发真键、拓扑信封只挂 topology 分支。
⚠ 本轮写新簿时**又踩了 D-084 刚记过的两条**（注入器 `|| MISS=1` 之后仍返回 0；`PT-l` 下限写 ≥14 而实测 15），
两次都是被自己写的负控当场抓出来的 ⇒ §八 **㊾** 补三条新形状（别名要白名单不要黑名单 / enum 声明行要单独钉 /
布尔闸门要钉整句）。仍欠：没有一条真接口回包（票面"看到 n 段真交接"未被演示）、一律 `NOT VERIFIED ON DEVICE`、
负控全批 34 本只重跑了 5 本、`default_value`/`idempotency` 两笔 ✘ 仍未归因、本地已提交**未 push**。
NEXT：① 钱包/交易记录（必须标"模拟模式"）② `earliestPickupTime` 取件时间窗 ③ #39 收藏首页入口
④ #47 分组 hub ⑤ #43 图标（我出 3 个候选给用户挑）⑥ App 这批之后才是 web 后台管理平台的完善。
<!-- 上一条 D-084（负控 20 格）及更早的「最后更新」段落保留为历史，不覆盖 -->
最后更新：2026-10-13（凌晨，D-084 · **D-082 那 20 格全部消化 + 新增 3 格**：13 道"空转闸门"回查后只有 1 道真是判据弱，
其余 12 道是**刀没落下去**——6 道因为 `NavigationEngine.kt` 工作树是 CRLF 而 `drop` 只找 `"\n}\n"`（`git ls-files --eol` =
`i/lf w/crlf`，"全仓 LF"这个前提作废），6 道是判据同轮重锚了、负控锚点没人跟（8387929 话术 + `onOpenNav` + `BusRouteBody`）。
机制补了三层：注入器读写一律 `newline=''`、`drop` 认 `\n\}\r?\n`、**落空当场打专属 ✘ 且本簿 ✘ 进退出码**
（原来 ✘=13 仍然 exit 0）。同族另修：SA-d/TH-f 两处 `-ge 11` 而实测 14 ⇒ 两本同轮改**恰好 14** 并把 14 处逐点登记
（第三处增量落在 git 起点之前 ⇒ **未归因**，不编）；`runcatching` 反向负控不再写死 `sites=32`，改自锚基线 SUMMARY；
`eta` 沙盒从"逐文件 cp"改**整目录 copy**（缺 `TraceScreen.kt` 让基线读 5、真身读 7），修绿后底下又露出第 21 格
（T-z 破口替换的四个词是旧话术 = 空跑）；`NavigationContractTest` 补一条断言（M25 当时**没有任何用例能红**，
按本仓规矩补用例不撤破口，**没有新增 @Test ⇒ 637 不变**）；`navigation_test` 的 `fails()` 只传 `$1` 而 `$XML` 是两份产物
⇒ M37 被记成 △，改 `"$@"` 后单条复跑 ✔。
**读数**：动过的 10 本负控逐本跑，`navigation` 143✔/0✘/0△、`eta` 61/0/0、`mission_route` 52/0/0、`safe_call` 29/0/0、
`exception` 21/0/0、`runcatching` 20/0/0、`notify_result` 17/0/0、`oss_credits` 20/0/0、`throwable` 18/0/1△(既存)、
`shell_routing_test` 7/0/0、`mission_route_test` 3/0/0＋◦4、`navigation_test` 38✔/0✘/1△→ 该 △ 事后定位为读数器缺陷并转 ✔。
全量 `:app:testDebugUnitTest :app:assembleDebug --rerun-tasks` **rc=0 / BUILD SUCCESSFUL in 52s / 45 tasks executed**，
产物聚合 **63 类 / 637 例 / failures 0 / errors 0 / skipped 0**（63 份 XML、timestamp 63 个唯一值
`2026-10-12T17:14:13.939Z…17:14:14.033Z`）⇒ 不是残留。`app/src/main` 一字未动、一律 **NOT VERIFIED ON DEVICE**。
⚠ 本轮**没有重跑负控全批 33 本**（只重跑动过的 10 本），台账不许写成"负控全批绿"；`check_doc_anchor_probes.sh`
那本在册红仍**等用户定**（#51 三条路）；本地已提交**未 push**（用户口径：全部任务完成后一起推，推前给看 diff）。
完整记录在 `DECISION_LOG.md` **D-084**、判据教训 `CONTEXT_LOCK.md` §八 **㊽**。
<!-- 上一条 D-081（#54 乘车安排）及更早的「最后更新」段落保留为历史，不覆盖 -->
最后更新：2026-10-12（晚第三轮，D-081 · #54 用户端「我的乘车安排」：`Repo.myArrangements()`（`Repo.kt:679`，`GET /send/arrangements`）**通路一直是活的、全仓零消费者** ⇒ 补的是读它的那一页，不新造第二套来源。先逐字段核生产者（`AppSendController.arrangements()` 只 set 十一个键）再动手，定下三条硬口径：① **这一页不显示任何分钟数**（该端点没有 `etaMinutes` 键，§68；到车提醒走 `/send/page` 的 `carrierEtaMinutes`，且须标「直线估算」）；② **`planStatus` 是 Integer 不是文案** —— 旧 `multiStr` 会把码念成「调度方案：2」，改成 `planStatusCode: Int?` + 照抄后端 `DispatchPlanStatusEnum` 六值的映射（未知码原样带出、**null 不许塌成 0**；顺带记下 VO 的 @Schema 只写了 0..3 而枚举实际有六个值）；③ 站点/车牌没给就念「后端没给」（站点被删时后端那两格是 null，不兜站名、不留空白行）。判定在纯层 `ui/user/ArrangementRows.kt`（四格 `Failed/Loading/Empty/Shown`，**「没取到」绝不念成「还没有乘车安排」** —— 与 D-080/#38/#42 同族），页面 `ArrangementsScreen.kt` 只渲染，`Routes.ARRANGEMENTS` 走 `BusRouteBody`、「我的」里恰好一个入口。新簿 `tools/check_arrangement_probes.sh`（AR-a…AR-i）绿 + 新负控 `tools/arrangement_negative_controls.sh` **12/12**。两处判据自纠值得记：AR-e 的反证原先拿模型里的 `val etaMinutes: Int?` 数，实测该串在 Models 有 **6 处**（删掉 Arrangement 那一处照样 `-ge 1` 通过）⇒ 改成**同文件正对照**（「承运车辆」那一行必须数得出恰好 1，它证明的是过滤器真的在看这个文件）；结构簿第一版 `nc_all()` 对 `grep -rn` 的输出套了 `^[0-9]+:` 形状的注释过滤器 ⇒ 一条都不命中、报出「真调用数=0」的假红，改成先用 `sed` 剥掉 `文件:行:` 两段再套过滤器（两种输出形状对应两种过滤器，簿里有注释）。**判据当场咬住一处真重复（不是误报）**：`PL-g` 立刻红 —— 我在新页空态里顺手重抄了「不提供网上预约乘车」这条硬规则，同一句两份文字迟早各自漂移 ⇒ 删掉这一页的重复、那句只留在 `AppPolicies`，并把用例从「要求这一页也写着它」改成**「要求这一页不许再抄一份」**（第一次改完仍红：注释里还留着原话）。台账随动：SU-b 64→**65**（唯一增量 `ArrangementRowsContractTest.kt`，产物 tests=13，SU-a 同轮 63/**0**）。读数：新用例 **13 例**、`BusEtaContractTest` 仍 13 例绿；全量 `:app:testDebugUnitTest :app:assembleDebug --rerun-tasks` = **BUILD SUCCESSFUL / 45 tasks executed**，**63 类 / 637 例 / 0 失败 / 0 错误**（63 份 XML timestamp 全为 2026-10-12T11:13）；判据全批 29 本 = **28 绿 / 1 红**（红的仍是台账锚点那本 #51）。后端实测（本机 local）`GET /app-api/transport/send/arrangements` 未带登录态 = `{"code":401,"msg":"账号未登录"}` ⇒ 路由在、要登录；**「看到 n 条真安排」这一格仍未被演示**（需要一个带 orderType=1 且 status∈{2,3,4} 的登录用户，本机没验到）。CURRENT：本轮全部 `NOT VERIFIED ON DEVICE`；负控整批（现 33 本，实测 `ls tools/*_negative_controls.sh | wc -l`）与真机复拍仍在册；V029 待队友在云上库执行；CI `set -o pipefail` 仍压在 #50；#48 只有代码侧（push 后才谈得上「用户能更新」）。仍欠一格已登记：debug 演示面的 `/send/arrangements` 写的是自造键（没有后端真给的上下车站与 `planStatus`）⇒ 演示包里这一页会有几行念「后端没给」，把演示面改成与契约同键是独立收尾。NEXT：用户端 `/send/topology` 死代码补查件票面 → 钱包/交易记录（须标「模拟模式」）→ `earliestPickupTime` 取件时间窗 → #39 收藏首页入口 → #47 分组 hub 设计 → #43 等图标稿。）
最后更新：2026-10-12（晚第二轮，D-079 + D-080：App 侧补齐两格"看着有、其实是空的"——**① 服务协议/隐私政策正文页**（D-079）：此前登录页只有一个勾选框、点它只翻 `agreed` 而**没有任何正文可看**，现在 `core/about/AppPolicies.kt` 是 16 组分条正文的唯一出处（`UPDATED_ON` 单点），`ui/user/PolicyScreen.kt` 只渲染，`Routes.PRIVACY/TERMS` 两条注册都走 `BusRouteBody`，登录页两个可点链接把"勾"与"看"分开、关于页两行入口，且这两页**不设角色/登录闸门**（同意之前就得读得到）；新判据簿 `tools/check_policy_probes.sh`（PL-a…PL-j）绿 + 新负控 `tools/policy_negative_controls.sh` **11/11**，其中 P-7b 第一轮是**绿的**（`PLH_PROOF ≥20` 这种下限反证删一句正文读 24 照样过 ⇒ 那个"页面 0 命中"没人能证明不是空跑），改成钉**恰好 25** 才第一次被破口咬住 —— **下限型反证只能证"机制数得动"，当不了逐条改动的 tripwire**；内容契约那本第一版还把自己的诚实否定句（"不提供网上预约乘车""没有接入任何广告 SDK"）打红两条，改成 bannedPromises/notEnabled/**mustState**（禁肯定式 + 要求否定句在场）才对。**② 司机端平台公告栏**（D-080）：后端 `/notice/list` 与用户端首页通路本来就活着，司机端一条都不显示 ⇒ 接现成的、不造第二套来源；判定全在纯函数 `ui/driver/DriverNotices.kt`（`noticeBanner(RemoteState)`），**"这一轮没取到"与"后端确实没有公告"必须是两格**（前者占格给重试、后者整栏不出现），初值写 `loading=true` 而不是空列表；取数挂在 `syncFromServer(withNotices)`，只有进入/下拉刷新/失败重试那一轮取，作业动作后的回读不取。⚠ 本轮最贵的实测打回：第一版挂在**每轮**回读上，全量跑出 **12 例红**且红因分两种——`DriverActionHonestyContractTest` 报 `expected:<5> but was:<6>`（条数），另两类 7 条报 `TimeoutCancellationException: Timed out waiting for 5000 ms`（`MockWebServer` 按序回填，第 6 跳没有响应 ⇒ 挂在超时而不是差一，只看红因会误诊成协程问题）。修法不动判据强度：加 `withNotices` 闸门 + 三个类计数同轮逐条重登（5→6、11→12、6→7，早退支仍 1 条），并登记口径**"改会被 `server.requestCount` 钉住的取数点，先跑那三个类再看判据簿"**——结构判据只看文本，能证明"接线在"，永远证明不了"没多跳"。随动重登：SU-b 63→**64**（唯一增量 `DriverNoticesContractTest.kt`，产物 tests=9，SU-a 同轮 62/**0**）、`check_idempotency_probes.sh` 的 H-h 逐字锚点 `11,`→`12,`（仍 3 条）。后端实测（本机 local，只读）`GET /app-api/transport/notice/list` = `{"code":0,"msg":"","data":[]}` ⇒ 通路真、库里 0 条 ⇒ 这一格现在正确地不出现，"司机真看到公告"连演示都没有，登记为未验。读数：新簿 `check_driver_notice_probes.sh`（DN-a…DN-i）绿 + 新负控 `driver_notice_negative_controls.sh` **13/13**；全量 `--rerun-tasks` = **62 类 / 624 例 / 0 失败 / 0 错误**（62 份 XML timestamp 全为 2026-10-12T10:34）；判据全批 28 本 = **27 绿 / 1 红**。CURRENT/仍欠：红的仍是 `check_doc_anchor_probes.sh`（#51，三条路线待用户定）；两格全部 `NOT VERIFIED ON DEVICE`；CI 的 `set -o pipefail` 还压在 #50；#48 只有代码侧（要 push + Actions 出 Release 才谈得上"用户能更新"，本轮按用户口径**只本地 commit、push 前给看 diff**）；负控整批（现 31 本）与真机复拍仍在册；V029 待队友在云上库执行。NEXT：用户端"我的乘车安排"接回 `Repo.kt:723 /send/arrangements` 死通路（同一条纪律：先跑被它钉住的计数类）→ #39 收藏首页入口 → #47 分组 hub → #43 等图标稿。）
最后更新：2026-10-12（D-076 续二～续六：App 这批用户报的 8 条已收口 7 条 —— 验证码 4..6 位契约、登录态静默续期、实时公交首屏状态位、首页失败态、地图"我的位置"两层开关、头像更换入口、通知锁屏隐私，以及本轮 **#42 天气按角色分文案**：`WeatherInfo` 加 `driverTips`，`farmTips`（六条清一色货运口径）换成顶层纯函数 `weatherTipsFor(condition,temp)` 出两份说法（霾并入低能见度档），两个天气生产者同时填两份；新簿 `ui/driver/DriverWeatherTip.kt` 管 15 分钟闸门与"没取到就不出这一栏"；司机工作台 `item(key = "weather")` 接在 `syncFromServer()` **尾部**（不另开 `work()`，因为 `ScreenViewModel.kt:38` 进门清 error ⇒ 会把本班真失败原因抹掉，与 #38 同形病）。后端天气接口**有意不加**（App 直连高德 Web key REST/open-meteo，绕进后端只会抹平来源标签，同 #41 那条理由）。读数：`DriverWeatherContractTest` 8 例，全量 `--rerun-tasks` = **59 类 / 596 例 / 0 失败**（59 份 XML timestamp 全为 06:41，无上一遍残留）；新判据簿 `tools/check_weather_probes.sh` 14 项绿 + 新负控簿 `tools/weather_negative_controls.sh` **7/7 红且原因对得上**（负控全批因此 28→29 本，仍欠整批）。两条判据自纠：W-d 原来 `grep -c` 不过滤注释行 ⇒ 把那一栏整行注释掉判据仍绿；注释过滤器第一版写成 `:[0-9]+:`（那是 `-rn` 的形状，单文件 `-n` 只有一处冒号）⇒ 改 `^[0-9]+:` 才咬住。CURRENT：#43 图标等用户给设计稿；本轮末全批 26 本 = **25 绿 / 1 红**（新增 check_update_probes），红的仍是台账锚点那本；**#48 已做完代码侧（D-078，只走 GitHub Releases，用户口径）**：`core/update/AppUpdate.kt`（四分支判定，`NoReleaseYet` 与 `UpToDate` 分开）+ `AppUpdater.kt`（FileProvider/缓存目录/按 size 核对/授权缺失跳系统页）+ 关于页 `UpdateCheckRow()` + gradle versionCode 从 `GITHUB_RUN_NUMBER` 派生 + CI 只给 release job `contents: write` 并"创建 Release 后再回读 `releases/latest` 取证"；新判据簿 `check_update_probes.sh`（15 项）+ 负控 `update_negative_controls.sh` **14/14**。→ NEXT：**push 前给用户看 diff**（选定的提交方式），推完再手工触发一次 release 才能真验"用户能更新"；然后 #39 首页收藏入口 → #47 分组 hub 设计 → #43 等图标稿。仍欠：**判据红账只剩 1 本**（全批 25 本复测 = **24 绿 / 1 红**，见 D-076 续七与续七 补二：清了 reachability/throwable/eta/navigation/safe_call/mission_route/runcatching 七本，并修掉三处真回归 —— `BusScreen.kt:277` 的 `loadMyShippingOrders()` 外层 runCatching 吞取消、`BusScreen.kt` 主路径上折线四态说明整块消失、`TraceScreen.kt` 段里程/段时长 `dist > 0` 才 append 让"没下发"与"真 0 公里"同形；红的仍是台账锚点那本，**已否掉"按候选表批量重锚"这条路**（三条反例证明它会把对的锚点改错，见 `docs/android/ANCHOR_REVIEW_2026-10-12.md`），装 CI 的 `set -o pipefail` 因此还压在 #50，且要先定"比率闸门 / 逐条人工重锚 / 83 条进在册表"三条路）；**真机复拍**（本轮全部 `NOT VERIFIED ON DEVICE`，含"15 分钟内不发第二次 REST"）、`tools/check_*.sh` 全批与 29 本负控整批、V029 由队友在云上库执行。）
<!-- 上一条 D-076（①②③ 那批）及更早的「最后更新」段落保留为历史，不覆盖 -->

最后更新：2026-10-12（D-076 用户报的三条"用不了"落到三个不同病根并全部收口到判据层：① **会话不是没持久化**，是 access token 1800s 到期后 App 从不调后端已有的 `/member/auth/refresh-token` ⇒ `saveSession` 现落 `refreshToken/expiresAt`，`Api.rawExecute` 在 401 分支**先静默续一次再重试**（`attempt<1`，续期接口自身走 `/auth/` 那支故结构上不可能递归；演示回落造出的 token 被 `if (p.demo) return false` 挡掉）；② 实时公交"每次都是网络不好"是并发 `work(reset=false)` 把 `loading` 抢先写 false（`ScreenViewModel.kt:38-42`）⇒ `state()` 现要求**有真实失败原因才许报 error**；③ 首页"没有附近公交卡片"是失败态**死代码**（`nearby ?: return "loading"` 抢在 error 判定之前，而 `Repo.kt:873` 失败必然 nearby==null）⇒ 失败态改为可达。**基类 `work()` 原样未动**（全仓 30 个 `reset=false` 调用点多数是用户动作，依赖"动作即清错误/停 loading"）。读数：新用例 `SessionRefreshContractTest` 6 例 + `BusStateContractTest` 5 例；全量 `:app:testDebugUnitTest --rerun-tasks` = **56 类 / 578 例 / 0 失败**，`GRADLE_EXIT=0`；能红证明=把两处判据逐字还原改前后 `5 tests completed, 2 failed`（红因与用例名一一对上），`RESTORE_DIFF=0`。**本轮明确未验**：无设备在环 ⇒ 三条一律 `NOT VERIFIED ON DEVICE`（"30 分钟后不必重登"的实际体验没有真机复拍）；`tools/check_*.sh` 判据簿与 `*_negative_controls.sh` 负控批**本轮未跑**，属在册欠账。用户本轮新报的其余 7 条（我的位置 / 实时车辆 / 天气文案 / 图标 / 头像 / 验证码位数 / 通知形态+更新通路）已逐条取证登记，其中"高德没有就算了，自家的用司机端实时位置""图标我出 3 个候选""走 GitHub Release + App 内检查更新"三条已取到用户决策。）
<!-- 上一条 D-072 及更早的「最后更新」段落保留为历史，不覆盖 -->

最后更新：2026-10-10（D-072 真实形态切换：清单补 `ACCESS_WIFI_STATE`/`CHANGE_WIFI_STATE` 后高德定位从「每秒 AmapErr #1901」变成 `BUS_LOC` 每 5s 出点；真机跑通真实登录（`13800138001/123456`，旧 `demoMobile=13800004021` 是库里不存在的账号）、真实算路（driver 103／33 个带坐标经停／`来源 AMAP`／车道卡「2.8 公里 进入 兴塘路」+ `11.5 公里 13 分钟`）、真实逆地理与天气（`南岸区南山街道`、`22°C 雾`，Web key REST `infocode=10000`）；`buspost.baseUrl` 指本机 48080 并扫 dex 取证；`DEMO_ALLOWED=false`。判据 24 本 23 绿 1 红（红的是台账锚点那本，已登记）；`uses-permission` 13→15 已同步 V21-28 行。仍欠：用户端逐屏观感、ETA 换算、商品图占位、附近公交线名重复、28 本负控全批、把两个 WiFi 权限钉进判据（配能红的负控）。）
<!-- 上一条 D-071 及更早的「最后更新」段落保留为历史，不覆盖 -->
最后更新：2026-10-10（本轮：**D-069 真机在环第一天** —— 协议 v2 阶段一审计落文档 + 开工 7 本红判据全部归因 +
一片未入账改动补登记 + 一个 P0 修完并复拍；完整记录在 `DECISION_LOG.md` D-069、
审计报告 `docs/android/PRODUCT_UX_AUDIT_V2.md`、证据 `docs/device-evidence/20261010-sweep/`（30 屏）。
**下一轮 = D-069 §5 的 NEXT**（第一条：台账锚点统一重锚 —— 本轮收工时 `DA-d 新增可疑 16`、`DA-e ID-MISS 302 > 286` 仍红，
是**在册欠账**不是新缺陷；第二条：报告 §三 的 P1 逐条动手，其中"导航卡挂在有路线支 ⇒ 真实会话进不去导航"最值钱）。
三件必须知道的当前事实：① **设备换成真机**（小米平板 6 / arm64-v8a / Android 16 / 2136x3200@440dpi ⇒ 48dp=132px），
用户口径"不再跑模拟器"；② 导航与轨迹上报**第一次有真机读数**（引擎 `Ready` 约 200ms，模拟器那 23–29s 是转译代价）；
③ 本轮**没有跑负控全批 28 本**（只跑定向 9 条 + 两本单簿），而这笔欠账已经咬人两次——
`SR-f` 与 `OS-b` 的注入因锚点随重构蒸发而**静默失效**，本轮各修一次。
⚠ 下面紧跟着的"最后更新：2026-09-30（本轮：V-2b …）"那块起是**历史原话，按台账纪律不回改**。）

最后更新：2026-09-30（本轮：**V-2b 冷启动角色与后端画像同源** ——
完整记录在 `DONE` 段"V-2b 冷启动角色与后端画像同源"一节、审计 §76、`DECISION_LOG.md` D-030；
**下一轮 = 路线图第 8 步（导航引擎，仍 BLOCKED）**；上一轮 V-3 的记录在 DONE 的"V-3 §54"一节、审计 §75、D-029。
⚠ 下面紧跟着的"最后更新：2026-09-29（本轮：V-2 …）"那块是 **V-2 那一轮留下的原话，按台账纪律不回改**
（它当时写的"下一轮是 V-3"是真的）；**本轮的当前值一律看下面的状态板**。）

最后更新：2026-09-29（本轮：**V-2 Phase 1 壳路由与分域闸用例** ——
完整记录在 `DONE` 段"V-2 壳路由与分域闸（Phase 1 验收）"一节、审计 §74、`DECISION_LOG.md` D-028；
**下一轮是 V-3（指令 §54 司机异常上报，第一步是读后端 enum 与 endpoint），尚未开工**。）
- **先核现状再动手**（不是照台账"Phase 1 验收"四个字开工，是对着代码数出来的）：
  ① Phase 1 那条"司机端不能看到用户首页"的**判定发生在 `NavHost` 的导航图里**，而工程**没有 Robolectric**
  ⇒ JVM 用例渲染不出导航图，唯一可测的形态是把决策从 @Composable 里抽出来；
  ② 两条决策原先都是内联的：`BusPostRoot` 里的 `val start = when {...}` 和文件级私有 `deepLinkAllowed`
  —— 实测 `USER_SHELL|DRIVER_SHELL|startDestination` 在 `app/src/test` 命中 **0**（本轮开工时复跑仍 0）；
  ③ 全工程的**跨域导航点只有两处，且都是故意的**：`MineScreen.kt:112`（入口，先 `Repo.bindDriver()` 才切角色）与
  `DriverShell.kt:39`（"用户版"出口，切角色的同时导航）⇒ 用例绝不能把这两条断成违规，判据必须允许这一条出口存在；
  ④ 第三条行为（`Prefs.role` 与后端档案不一致时以档案为准）**已经有主**：`DomainSplitTest` 的
  `切换司机模式先过后端档案` —— 本轮**没有再写一遍**，两份真相迟早对不上。
- **修法（"结构收敛"不是"重构 UI"，逐行看得出）**：新建 `nav/ShellDecision.kt` 两个 `internal` 纯函数
  （`startDestinationFor(loggedIn, role)` / `deepLinkTarget(route, role)`），`BusPostRoot` 只是改成调用它们
  （+2 行 import、2 行调用），**Compose 结构、导航图、页面一行没动**；旧私有 `deepLinkAllowed` 删除（行为逐条等价：
  壳路由不接受深链、`DRIVER_ONLY` 在用户会话拒绝、陌生路由**照样放行**交给 `NavHost` + `runCatching`）；
  顺带把 `Routes.kt` KDoc 里那个**已经指向不存在函数**的注释改成 `deepLinkTarget`（过期注释也是假台账）。
- **证据**：新增 `nav/ShellRoutingContractTest` **9 例全绿**；全量 `testDebugUnitTest --rerun-tasks` →
  **24 类 / 253 tests / 0 failures / 0 errors / 0 skipped**（XML 最新 20:53:59，**+1 类 +9 例全部可归因**）、
  `assembleDebug` executed（APK 20:54:54 / **57,941,374 B**，与 V-1 **同字节** ⇒ 本轮新增一条纪律：**同字节不等于没变**，
  改用符号级复核：APK 里 **14 个 dex 全扫**，`ShellDecisionKt` 命中 **3**（类 + 两个方法引用），
  旧符号 `deepLinkAllowed` 命中 **0**）、`lintDebug` executed → **0 error**（**46 Warning + 31 Information**，
  XML 97,380 B；⚠ V-1 记的"txt 31,126 B"与本轮 XML **不是同一个产物口径**，没复核清楚之前不许当同一条比较）、
  **9 套探针全部 exit 0**（新：`check_shell_routing_probes.sh` 的 SR-a…SR-h 十三条判据）、
  结构负控 **12 条实测 12 ✔ / 0 ✘ / 0 △**、用例负控 **6 条 6 ✔**（破口 → gradle exit 1 且红的是对应用例，
  `cmp` 证原样恢复 → 复跑全绿）、真实性口径**一条没涨**：窄 **9** / 宽 **44** / `GeoMath` **27** / `DemoData` **3** 文件 /
  §24 假步进 **6** 处未修 / 安全六条 **0·4·0·0·0·0**。
- **本轮最值钱的两条过程纪律（都是被自己的回归抓出来的，不是先想好的）**：
  ① **负控脚本必须先验基线**：`cp -r app/src/main/java $SC/app/src/main/java` 在父目录不存在时报
  "cannot create directory"，此后打印的 12 条 `✔` 全是"文件本来就不在"造出来的**假绿**
  —— 现在基线 `exit≠0` 立刻 `exit 2`，并且先断言副本里的文件真的在；
  ② **交替 pattern 忘了 `-E` 就是恒 0**：`grep -rn "$DRV"` 里 `|` 在 BRE 是字面量 ⇒ S-e/S-f 永远显示"违规 0"，
  看着像红线守住了。GNU grep 3.0 实测同一条 pattern：**加 `-E` 命中 1，不加命中 0**。
  ⇒ **凡是"0 命中"的判据，先问它能不能命中非 0**（本轮的注入式负控就是干这件事的）。
  另有一条同类：判据脚本必须先验自己的输入文件存在，否则 `grep` 的空输出会被读成"计数 0 = 通过"
  （本轮还抓到变量名撞车：`SD=文件路径` 被 `SD=$(grep -c … "$SD")` 覆写，之后拿数字当文件名读）。
- **尾巴（在册未修）**：**冷启动不做档案复核** ⇒ 本机角色残留 `DRIVER`、而站点已撤档的人仍会落进司机壳。
  这不是假数据：`WorkbenchScreen.kt:525` 显示 `ErrorState(DRIVER_UNBOUND)` + 重试，且守卫保证**0 条司机请求**
  （`DomainSplitTest` 钉着），但**人还在错误的壳里**，唯一出口是"用户版"tab。要修得在会话恢复里加一次
  `/driver/profile` 复核并据此降级角色 —— 那是**新机制**，不在验收轮里顺手加，登记为 **V-2b**（下一轮或 V-3 之后做）。
  另 ⑬ 的四条仍在册：6 处自造别名 `"eta"`、`QuoteResult` 三个金额 null→0.0、`Repo.kt` 的 `optBoolean(…, true)`、
  §24 假步进 **6 处**；V-1 的用户端 `code=900` 专属分支也还在册（审计 §73 尾部）。
  ⚠ **上面这一段是 V-2 那一轮的读数，其中"§24 假步进 6 处"已于 2026-09-30 #51 归零**（机器物理删除 + 判据口径抬到目录级，
  见 DONE 的「#51」一节与审计 §78）；**历史数字不回改，但当前值只看状态板与 §八 ⑲**。

上一轮（**V-1 司机动作幂等（指令 §52）**）的七条后端契约事实、分层理由与全部证据在
`DONE` 段"V-1 司机动作幂等"一节与审计 §73、`DECISION_LOG.md` D-027；这里只留两条别轮抄错的口径：
**APK 字节数只当场量、不从上一遍抄**（⑬→V-1 那次 57,941,278→57,941,374 的"+100 B"就是抄错的产物，
两个读数可能分属不同中间构建，只有对应当前产物的那个算数），以及"闸门只防**重复点击**不防**重复进程**"
（杀进程后 10 秒保留窗失效；后端没有客户端唯一键，不许在客户端造假持久化）。

上一轮（**第 ⑬ 轮：精确到站 ETA + 3 个 `distanceKm` 的口径**）的六条后端契约事实、修法与全部证据在
`DONE` 段"⑬"一节与审计 §72、`DECISION_LOG.md` D-026；这里只留两条别轮抄错的口径：**窄/宽口径那次 +2/+5 全是
说明性注释**（不是新几何出口，为归零去扩白名单或删注释就是把假话说干净），以及 `Models.kt` 的
`distanceCaliberText` 从 `:1641` 漂到 `:1649` —— **行号会漂，按内容找**。

上一轮（**第 ⑫ 轮：货运侧 ETA 家族 null→0 收口 + §68 到站提醒数据源迁移**）的证据与四条契约事实在 `DONE` 段"⑫"一节与审计 §71；
第 0 轮（**指令 v2.0 到货后的基线重建 + 逐条对表，业务代码零改动**）的结论在 `DECISION_LOG.md` D-024 + 审计 §70 +
`CONTEXT_LOCK.md` §〇/§九，这里只留基线数字备查：`app/src/main/java` **71** 个 `.kt`、测试 **20** 文件 / **19** 类 / 生效 `@Test` **215**
（`TestSupport.kt` 0 例，所以文件数比类数多 1）、依赖 **22** 条、`app/libs` 不存在；三条最重要的对表结果：
**§52 幂等在工程里是 0 处**、**§3/§4 要的 `roles: Set<AppRole>` 后端根本没这个键**（司机身份只能由 `AppDriverProfileRespVO.driverId` 证明）、
**§100 的 `THIRD_PARTY_NOTICES.md` 此前根本不存在**（建档时顺带暴露 **OSM/ODbL 署名在 App 内命中 0**）。


---

## 状态板（指令 v2.0 §106 要求的八个字段，2026-09-29 建）

> 只放**可核对的当前值**；解释一律在下面的 `CURRENT` / `DONE` / `BLOCKED` 段，别在这里重述一遍造成两份真相。

```
CURRENT_WAVE:   ANDROID-DOMAIN-2 **队列 F-2 第 8 步（`safeApiCall()` 落点 + 只迁 5 个高风险点，指令 v2.1 §六 / §七 / V21-07）已完成并移入 DONE**；
                下一轮 = **F-2 第 9 步：剩余 `runCatching` 协程审计**（动的基数是 **34 生效行**，不是 44 出现次数、不是 43 行——
                三个数口径不同，抄数必须连口径一起抄，见本文件 F-1/F-2 段与 D-042）；
                ⚠ 本轮改的**不止 `tools/` 面**：`app/src/main` 动了 3 个文件（`Api.kt` 改名 + KDoc、`Repo.kt` 五处迁移点、
                `BusScreen.kt` 折线状态与两句文案）⇒ 生产行为**有两处真实变化**（折线失败不再洗成空列表、
                退出登录的失败分类可见），但**真机一字未验**（`NOT VERIFIED ON DEVICE`），本轮**没有**产出新的 release 证据
COMPLETED:      本轮（F-2 ⑧）**把 §七 点名的那个 helper 从"工程里早就有、但没人调用"变成真的有生产调用点**——
                走的是 `apiCall` → `safeApiCall` 的**改名 + 接线**，不是新写第二份真相：
                ① **改名不改签名**（本轮先否掉的是我自己上一轮的猜测）：`ApiTest.kt:171` 那条现成用例证明
                `inline` + **非挂起**形参包得住挂起调用 ⇒ 签名原样保留；改形参会一次制造几十个编译点，
                而指令明写"不要为了一个 helper 制造 38 个编译问题"。**真正的病根是休眠**：改名前它自己的判据数得出定义行 `1`、
                生产调用点 **0**（F-1 那轮的 `Z1b` 就是这条证据），本轮把"0"变成"6 个使用点"。
                ② **只迁 5 个高风险点**，且台账不许记成"修了 5 个缺陷"——**真实修复 2 处**（`linePolyline` 失败不再被洗成空列表、
                出口改 `getOrThrow()` 原样回抛同一个类型；`logout` 的会话撤销失败分类可见，**清本地仍无条件先做**——会话卫生优先于分类）
                + **形状归一 3 处**（天气预报段、`unreadCount`、`driverUnreadCount`：旧的 `attempt { … }.getOrNull()` 里 `longOf`
                本来就不抛 ⇒ 对外行为**一字未变**，换的只是出口形状）；`driverUnreadCount` 保留 `driverId <= 0 → null 且不出网`
                那道显式门（`ScalarEnvelopeContractTest` 有一条用例钉着它，**先核契约再动手**、不顺手统一）；
                `attempt`（`core/net/Attempt.kt`，收 `Result`、服务非网络读：Worker / 定位 / 引擎 / 开屏预取 / 交接照片）**保留**——
                两个 helper 不重复，SA-d 那条 `≥7` 下限就是替这句话站岗的。
                ③ **页面把"这次没问到"与"后端回空"分开说**：`BusScreen` 新增 `polylineUnconfirmed`（**不复用共享 `vm.error`**，
                否则别的接口失败会被念成"这条线路没轨迹"）+ `polylineReason()` / `polylineSubtitle()`；
                旧形状 `reason = if (size >= 2) null else "后端没有…"` **归零**——本轮唯一那句假话就是它。
                ④ **判据簿 `tools/check_safe_call_probes.sh`：15 行读数 / 34 道 SA 闸门 + 1 道 GUARD，rc=0**；
                每个"0"都配同机制反证（旧名 `apiCall` 0 ↔ 新名 9、体内兜底 catch 0 ↔ 同一模式在 Repo 数出 38、
                `?: return emptyList()` 0 ↔ `emptyList()` 在 Repo 24、旧形状 0/0/0 ↔ 新形状那句 1）；
                第 **17** 本负控 `tools/safe_call_negative_controls.sh` **17 条破口 + 3 条自检行 = ✔ 20 / ✘ 0 / △ 0**（rc=0，80 s）。
                ⑤ ⚠ **本轮最实的一条是又被自己的判据抓住一次推算**：`attempt` 下限我按"12 − 2 = 10"设进**两本**簿，两本**同时红**、实测 **7**。
                没有随手改数，先归因再动判据：迁移前 12 取自 ⑦ 收工留档产物 `/tmp/vf_probe_check_throwable_probes.log`
                （2026-09-30 23:30:30，同一条判据、同一个扫描面），5 个迁移点**全部**原本是 `attempt`（不是只 2 处）⇒ `12 − 5 = 7` 与实测吻合、
                非 Repo 那 7 处本轮一字未动 ⇒ 两本下限同改成**已归因的实测值 `≥7`**，负控仍一键打破（`Locator` 那处换成 `runCatching` ⇒ 7→6 当场红 ✔）。
                **"判据下限只能实测、不许推算"升为永久规矩**（同类错的第三次：D-034 的"✔=18"推算、F2-7i 的注入空跑、本轮的下限推算——
                三次都不是代码错，是我把"我以为的差值"当成了"量出来的基线"）。
                ⑥ **三条"永远绿 / 永不匹配"当场清掉**：SA 簿一条**死闸门**（`echo "…": fail=1;` 用冒号代替分号 ⇒ `echo` 把 `:` 与 `fail=1`
                当参数打印，`fail` 永不被置上）、负控第 17 条**打的是沙盒副本那本簿、`ctl` 跑的却是真身**（破口根本没进被执行的那份 ⇒
                `ctl` 加第三参指定跑哪一本；`verification_failure` 那本早用 `$PRSC` 处理过同一件事，**我没抄它的机制**）、
                接力脚本 `END_MARK` **写死「共 16 本」**（登记第 17 本那天就永不匹配，会一路等到 `MAX_WAIT` 再 exit 8，
                而"等不到"看起来像"批次还没跑完"、不像"口径过期了"）⇒ 批次与接力现在**共用同一个 glob** 推本数，两边都不写死。
                **实测**：负控全批 **`共 17 本`、逐本 rc=0**（02:39:11→03:10:35；合并 ✔=309 / ✘=0 / △=0 / ◦=3，**按本相加**不是换口径）；
                全量 **38 类 / 368 tests / 0 failures / 0 errors / 0 skipped**（`--rerun-tasks`、`24 actionable tasks: 24 executed` 无 FROM-CACHE、
                38 份 XML mtime 集合只有 `03:11:16`）；`:app:assembleDebug` rc=0 / APK **114,437,929 B**（03:12:23）；
                lint 第一次 `lintReportDebug` 报 **UP-TO-DATE** ⇒ **不拿旧产物充当本轮读数**，就地 `--rerun-tasks` 重跑得 98,753 B /
                **0 error** / 46 Warning + 32 Information = 78；`for b in tools/check_*.sh` **17 本全部 rc=0**（收工复跑 03:24，55 s）；
                census rc=0 / **118 行**：`V21-07` `attempt {` 生效 **7**（hits 8 那 1 行是 Repo KDoc 里的"旧写法"原文）· `safeApiCall` 生效 **9** ·
                旧名 `apiCall` **0**；`V21-30` 名单 **20** 名（= 指令 §三十 点名的 **18** 本 + ⑦/⑧ 各加进对表的 2 本）里 present **6** / ABSENT **14**
                （新增 `SafeApiCallContractTest|present|tests=12`）；⚠ **⑧ 开工时把它当成"V21-30 点名的那本"是口径读错** —— 它**不在**指令那 18 本里，
                是本步按 §七 新立、再**加进**对表的（与 ⑦ 那本 `VerificationFailureTest` 同一处理方式）；
                ⚠ census 里 `V21-22 ContractParseException` 由 `1/1` 涨到 `2/2` **不是长出了第四套真相**：多出来的是本轮 `Api.kt:70` 那句 **KDoc**
                （`code=0` 两处都是注释行），旧树副本 `/tmp/rr_repro.32486` 里那份 `Api.kt` 对该词实测 **0 命中**、同版脚本除本轮那两行外**逐行相同**
                ⇒ 归因到注释、不归因到异常出口；
                ⚠ **`NOT VERIFIED ON DEVICE`**：`app/src/main` 动 3 个文件、**两处真实行为变化**全部只有 JVM 证据，
                那两句文案在 JVM 里渲染不出来（这是 SA-e 存在的理由，也是它的**上限**）；release 仍 **BLOCKED**（不绕 HTTPS 闸门、签名文件不进源码工程）。
                以下保留 **F-2 ⑦** 那一遍的 COMPLETED 原文（永久记录见 DONE「F-2 ⑦」/ D-039 ~ D-041 / 判据 §八 ㉓）：
                本轮（F-2 ⑦）**把"构建失败必须分四类"从台账里的一句自觉，变成一张可复跑的规则表 + 一个真实消费者**：
                ① **唯一规则表** `tools/verification_failure_rules.txt`（TAB 三列：类别 / 字面量 / 出处；**16 行 / 4 类**，
                每条字面量都带出处，`未实测:` 的 7 条**明写没产出过**、不冒充实测）；
                ② **分类器** `tools/classify_verification_failure.sh`（一次 `grep -a -F -f` 扫完 + bash 内归属，
                输出 `RULE|类别|字面量|hits=|first_line=|出处=` 与 `VERDICT|类别`；**多命中报 AMBIGUOUS rc=3、零命中报 UNCLASSIFIED rc=4 且不猜**；
                脚本正文**不含任何类别名**，VF-d 数这个）；
                ③ **七份本工作区当场产出的样例**进 `tools/fixtures/verification-failure/`（**cmp 与 `/tmp` 原件一字不差**，
                含一份"用例失败但不是构建失败"的反例，分类器对它 rc=4），出处列的行号**回到样例里逐行回核**；
                ④ **落点偏离指令字面之处（本轮唯一的刻意偏差，写在类型 KDoc 第一句）**：`sealed interface VerificationFailure`
                **不进 `app/src/main`**，落在 test 源集 + **8 条用例**——构建失败发生时 App 进程不存在，主面拿到它是零消费者的死类型，
                还会在 `ApiException` / `NetworkException` / `Ack.error` 旁长出第四套业务真相（正是本步骤禁止的）；
                ⑤ **真实消费者接线**：`verify_device.sh` 的构建分支与无设备分支都改走分类器 ⇒ 顺带修掉一条**既成缺陷**：
                旧形态 `( ./gradlew :app:assembleDebug ) 2>&1 | tail -3 || die "构建失败…"` 里 pipeline 的退出码是 `tail` 的 **0**，
                **`|| die` 从不触发**，构建失败后会继续安装旧 APK 并把结论写成设备验证（VF-e 现在钉这条形状，负控第 7 条打它）；
                **实测**：新簿 `check_verification_failure_probes.sh` **17 条读数 rc=0**；负控 `verification_failure_negative_controls.sh`
                **13 条破口逐条点名各自判据**（0 ✘ / 0 △ / 1 ◦）；全量 **37 类 / 356 tests / 0 failures**（37 份 XML mtime **23:17:16**，
                机器比对：上一轮基线 36/348 ⇒ `ADDED {VerificationFailure: 8}`、`REMOVED {}`、`CHANGED {}`）；
                `:app:lintDebug :app:assembleDebug` **rc=0**、**48 actionable tasks: 3 executed / 45 up-to-date** ⇒ **没有重新打包**（照实记），
                15 个 dex 里本轮六条新串 **0** 命中而 `gatedUserCreate 4 · userCreateGate 1 · tryLock 10` 命中（**先证方法再信 0**）；
                ⚠ 三条在册未收：**`assembleRelease` 仍 rc=1**（`app/build.gradle.kts:171` HTTPS 闸门，**没加** `-Pbuspost.allowInsecureRelease=true` 绕；
                分类器对那份日志给的是 **UNCLASSIFIED**，四类都不认领一条工程自设的策略闸门 —— 这是设计意图不是漏判，见 D-039）；
                台账里"十三本负控"这个数字**已过期**（实测 `tools/*_negative_controls.sh` **16** 本，口径=文件名匹配）；
                **本轮第一次实跑负控时抓到两条自己的假话**（✘ 第 9 条永不为红、△ 第 10 条报的不是本条判据）⇒ 判据当场补强成
                "子类型**逐名点齐**"与"分类器读表的那行**赋值**单独钉"，第 13 条则是把本轮真找到的**出处行号指错**（`:15` vs 实测 `:12`）固化成机器闸
                ⚠ **`NOT VERIFIED ON DEVICE`**：本步骤全在工程验证面，设备上没有任何可验行为；无设备分支本轮第一次留下产物目录
                （`docs/device-evidence/20260930-232805/`，`# DEVICE_STATUS: DEVICE_UNAVAILABLE` + adb 报错原文）
                以下保留 **F-2 ⑥** 那一遍的 COMPLETED 原文（永久记录见 DONE「F-2 ⑥」/ D-038 / 判据 §八 ㉒）：
                本轮（F-2 ⑥）**把"本机拦下"与"后端拒绝"分开**——复用既有 `ActionGate`（**没重写闸门、没新增第三种 `GateResult`**），
                在**用户域另开一把实例**（硬规则 1：两域不共用一把闸，共用过一次下单就会去挡司机动作）：
                ① **先核契约再动手**（不是本机发明的规则）：`AppProductOrderController.java:38` 与 `AppSendController.java:120`
                都是 `@Idempotent(timeout = 10, SECONDS, keyResolver = UserIdempotentKeyResolver.class, message = "订单正在创建中，请勿重复提交")`，
                键 = `MD5(方法签名 + 参数 + userId + userType)`（`UserIdempotentKeyResolver.java:21-25`），异常即删键（`Idempotent.java:61`
                `deleteKeyWhenException = true`），重复请求由 `IdempotentAspect.java:61` 抛 **900**+那句后端原文；
                全后端 `@Idempotent` **只有这两处**（第三处命中是 `package-info.java:8` 的注释）⇒ `AppAddressController.java:32`
                与 `AppFeedbackController.java:28` 两个 create **零注解**，**刻意不走闸**（U-j 登记项，单方面拦=替后端发明规则）；
                ② **落码**：`Repo.userCreateKey(path, body)` 按后端那三个维度喂 SHA-256（**只存摘要**，收货人姓名/手机号/地址不进进程级键表）、
                `gatedUserCreate` 唯一出口、两处 create 首步交闸；被拦下返回 `Ack(notSent = true)` 的**空回执**（不许带回上一次的订单号）；
                `SendScreen.kt:395` / `GoodsDetailScreen.kt:115` 在**读回列表之前**早退（否则会把上一次点击的单子算到这一次头上）；
                ③ **用例 `UserCreateIdempotencyTest` 10 例**（指令点名的两条都在里面且是实测：`连点五次同一份订单只有第一次真的出网`
                → `server.requestCount == 1`；`业务失败后立刻允许重试` → 第二次真出网、拿到单号）；
                ④ **判据簿扩到 22 条读数**：`I-* 10 条一条未动`（司机侧仍 `I-a 10 · I-b 10`）+ 新 **`U-* 12 条`**
                （`U-a 2 · U-b 2 · U-c 2 · U-c2 2 · U-c3 2 · U-d 3 · U-e 2 · U-f 0 · U-g 1 · U-h 10 · U-i 2/2`，U-j 4 登记）；
                负控 **24 ✔ / 0 ✘ / 0 △ + 1 ◦**（◦ 就是 U-j：不判红的登记项**没有也不该有**负控）；
                ⚠ **分域的实证**：把 U-a 打穿（寄货 create 绕闸）后单跑判据 ⇒ `I-a 10 / I-b 10` 一字未动、只有 `U-a 1 / U-b 1` 变红
                —— 两本判据互不顶数，这才是"分两本"的目的，不是排版偏好；
                ⑤ **本轮抓到并纠正一处环境假绿**：`packageDebug` 打印 **UP-TO-DATE**、`BUILD SUCCESSFUL`，但 `outputs/apk/debug/app-debug.apk`
                仍是 **19:33:42** 那份，`unzip` 出的 15 个 dex 里 `userCreateGate` **0** 命中（旧串 `gatedAction` 4 命中，证明 grep 方法本身有效）——
                同轮 `intermediates/dex/debug/mergeProjectDexDebug` 是 **21:08:18** 且**有新符号** ⇒ **陈旧点在打包环节的 up-to-date 记账，不在编译**。
                `:app:assembleDebug --rerun-tasks` 重打（21:13:59 / 113,863,001 B）后包内实测 `userCreateGate 1 · gatedUserCreate 4 · "create:" 1 ·
                两条新文案各 1`，司机侧旧串仍在，`UserCreateIdempotencyTest` **0**（测试类不进包）、`Lokhttp3/mockwebserver/` 类型引用 **0**
                （`mockwebserver` 只是 okhttp 库名字表里的一个串，依赖声明是 `testImplementation`，未漏进包）。
                ⇒ **口径升级**：§八 ㉒ ④ 原来只说"APK 新鲜度只认包内内容"，本轮把它补成**"up-to-date / executed / BUILD SUCCESSFUL 三种打印都不算证明，
                必须在 dex 里 grep 本轮新串"**（决策与字节证据另记 **D-038**）；
                **实测**：全量 **36 类 / 348 tests / 0 failures / 0 errors / 0 skipped**（35 份 XML mtime 21:07:20，逐份读 `testsuite` 属性自相加，不读 gradle 汇总行）；
                `assembleDebug` + `lintDebug` **RC=0**，lint **0 errors / 46 warnings**（XML 78 条 = Warning 46 + Information 32）；
                判据簿与负控簿**同轮复跑**（改完 `× U-c2/U-c3` 两句方向说明后再跑一次）：`bash -n` 双过、判据 RC=0、负控 RC=0；
                ⚠ **本轮新增的一条测试侧修正**：`TestHost.resetActionGate()` 由"写死两个字段名"改成**按字段类型扫 `ActionGate`**，
                因为闸门是 `object Repo` 的进程级状态、Gradle 全类共用一个 JVM，写死名单那一版**本轮就漏过一把新闸**
                （`SendOrderTest` 八个用例里六个提交**内容完全相同**的 `form`，不复位就会被自己的闸吞掉）；
                ⚠ 未做/在册：`Api.kt` 的取消在 JVM 用例里不可确定性驱动 ⇒ 取消放闸只在 `ActionGateTest` 钉、不在用户域重复（假确定性）；
                真机连点、900 的实际文案渲染一律 `NOT VERIFIED ON DEVICE`
                以下保留 **F-2 ①–⑤ 那一遍**的 COMPLETED 原文（永久记录见 DONE「F-2 ①–⑤」/ 审计 §八 ㉒ / D-035 · D-036）：
                本轮（F-2 ①–⑤）**把"后端没给"与"后端给了零"分开**——五步全部落码 + 四本判据簿跑到 exit 0：
                ① **落档**：`docs/android/ANDROID_COMMAND_V2.1.md`（27,323 B / 1,618 行 / **35** 节，逐字节回环校验通过）
                + `docs/android/ANDROID_V2.1_CHECKLIST.md`（V21-01…35 逐节对表，含"F-2 十八步 ↔ V21 行"映射与**两条指令的冲突点**）
                + `tools/v21_requirement_census.sh`（需求普查 **113 行**，本轮给它补了 `code=` 列：`hits` 数出现次数、`code` 数滤注释后的生效行）；
                ② **§二十四 Minutes 别名**：五处第二候选名逐个走"后端 VO 声明 + producer 赋值 + 小程序消费点 + 演示面下发"**四面取证**后
                **删读法**（⚠ **不是给演示面补键** —— 补键等于把自造键洗成第四个真相源，同 D-033）；新立 **T-aj 家族**：
                `T-aj 0 · T-aj2 0 · T-aj3 0 · T-aj4 7 · T-aj5 16 · T-aj6 6`，GUARD 10；`MinutesContractTest` **6** 例；
                ③ **§三 QuoteResult**：五个数值字段全 `Double?`/`Int?` + 缺键走 `multiNumOrNull`，界面只走三个 `*Text` getter；
                ④ **§四 NearbyResult**：`inService`/`located` 由 `optBoolean(…, true)` 改 `Boolean? = null`，`isOffService` 只在 `== false` 时为真；
                ⑤ **§五 数字默认值普查**：`tools/default_value_census.py` 实测 **472** 处 ⇒ **B 398 / C 74 / A 0**
                （**A=0 是结构性事实**：后端 App RespVO 全用包装类型，"给了 0"与"没给"在报文上长得一样 ⇒ 任何把缺键读成 `0` 的写法都是冒充），
                本轮只收 **Earnings 一族**（不扩大战线），`check_default_value_probes.sh` 13 条读数全对 + GUARD `1/29/34/7`、`EarningsContractTest` **8** 例。
                **⚠ 与指令字面的一处刻意偏差已写进清单**：§三 要 UI 显示"暂无报价"，实际文案是**"金额未给"/"未给"** ——
                前者暗示"后端算过了、结论是没有报价"，后者才是"这个键不在报文里"；APK 内实测"暂无报价" **0 处**（不静默替换、不谎称照抄过）。
                **本轮真正的收获在三处判据串味上（改的都是扫描面，不是期望数字）**：`R-f` 被 §四 的 `multiBoolOrNull` 顶到 4 ⇒ 收回"声明 1 + `Reachability` 解析体 1"=**2**；
                `Q-e1` 把 `EarningItem` 的同名 getter 数进来 ⇒ 收回 **`QuoteResult` 类体**=**3**；
                ⚠ **原有那条 Q-e1 负控本身就是假绿** —— 整文件 `replace(…,1)` 删到的是**另一条契约**那行（`EarningItem` 在文件更前面、逐字相同），
                破口从未打进被判定量 ⇒ 新增类体内删行的 `delbody()`，并同轮补**反向（口径）控制** `keep`：删 `EarningItem` 同名 getter 时 Q-e1 **必须不响**；
                簿子标记因此扩成 `✔ 破口 / ✘ 未响 / △ 存疑 / ◦ 反向控制`，末行 `[口径控制] 反向控制失效条数 = 0`（非 0 走 `exit 3`）。
                **四条工具自误（都升为永久规矩）**：① 漏一个分号 ⇒ `ctl` 被当**参数**吃掉、那条控制**从未执行**（✔ 21 而非 22 才发现）
                ⇒ **改完脚本对 `tools/*.sh` 全量 `bash -n` + 每本簿 ✔ 条数与簿头声明的破口条数对账**；
                ② **APK 新鲜度只认包内内容**：报了"12 executed"的构建仍留着旧包（114,437,316 B / 16:45:11 / 里面查不到本轮字样），
                `--rerun-tasks` 才得到 113,863,001 B / **19:33:42** / sha1 `7fd54a4a…`，`unzip` 15 个 dex grep 到"金额未给"×2；
                ③ **注释过滤器的形状要跟 grep 输出形状配对**：`:[0-9]+: *(\*|//)` 只对 `-r` 的前缀输出成立，单文件 `grep -n` 得用 `^[0-9]+:[[:space:]]*(\*|//)`（用错那次 Q-e2 读到 6 而非 4）；
                ④ **"0 命中"要有出生证明**：本族 8 个 0 各配 GUARD（别名模式必须在**测试源码**——扫描面之外——数得到），否则计数管道失效时全部 0 一起变假绿。
                **实测**：四本判据簿（DV / Q+B / R / T）**同轮复跑全部 exit 0**；负控批处理 **15 本按顺序串行跑完、逐本 rc=0**
                （`bash tools/run_all_negative_controls_batch.sh /tmp/allneg_f2_5.txt`，19:50:32 起、20:17:32 止，批处理日志与 15 份单本日志都留档）：
                `contract_defaults 22 ✔+1 ◦`（末行 `[口径控制] 反向控制失效条数 = 0（预期 0）`）/ `default_value 16` / `eta 60` /
                `exception 21` / `exception_test 8` / `idempotency 11` / `idempotency_test 0`（这本**不用 ✔ 标记**，它的三行是
                `[破口后] gradle exit=1` / `[恢复] cmp 一致` / `[恢复后] gradle exit=0`，rc=0 来自簿内闸门 ⇒ **0 不等于"没做负控"**）/
                `navigation 27` / `navigation_test 10` / `reach 21` / `role_reconcile 13` / `role_reconcile_test 6` /
                `shell_routing 13` / `shell_routing_test 7` / `throwable 19` ⇒ **逐本相加 ✔=254 / ✘=0 / △=0 / ◦=1**；
                ⚠ **这个 254 只有"按本抄"才站得住**：换口径就换数 —— 批处理用 `grep -c '^✔'`（254），
                按"整行含 ✔"数是 **256**（多算 `navigation` 那句图例「上面每行都必须是 ✔」，它同时含 ✔/△/✘ 三个标记 ⇒
                按标记 grep 还会凭空读出 **1 条 ✘、1 条 △**，看起来像"有负控没响"），按"含 ✔ 且去掉图例行"是 **255**
                （`navigation_test` 那条基线行前面有两个空格，`^✔` 数不到它）⇒ **总数不是判据，那 15 行才是**；
                ⚠ **第二层工具自误**（这次是我自己的计数命令）：那 4 本"用例级"负控的日志是**混合编码**文件
                （脚本自己的 echo 是 UTF-8，嵌进去的 gradle/Kotlin 用例名是 GBK）⇒ `grep` 把它们当**二进制**，
                `grep '✔' \| 计数` 那种管道只收到一句 `Binary file … matches`，于是 `exception_test` / `role_reconcile_test` /
                `shell_routing_test` 各被读成 **1**（真实 8 / 6 / 7）。收法是**按字节模式数**（`\xe2\x9c\x94`，必要时 `-a`），
                数完再与批处理留档对账 —— 与 §八 ㉑ 第②条同一条规矩的第三种形状：**计数之前先证 grep 把这个文件当文本还是二进制**；
                全量 **35 类 / 338 tests / 0 failures / 0 errors / 0 skipped**；lint **0 error / 46 Warning / 32 Information**；
                ⚠ **§五 剩下的 32 处 C 类逐族重核没做**（天气 3 / 距离 5 / 车道 12 / 商品·车辆·清单 7 / `Repo.kt:608`·`:997` / 草稿 `?: 0L`），
                `DV-k=132` 是全项目下限、**只许往下走**；§二十四 那五处的**别名已删净**，但其中 `plannedMinutes`（`Models.kt:532 Int = 0` / `:500 multiNum(…).toInt()`）
                与 `plannedDurationMinutes`（`:1337` / `:1381`）**读法形状仍是 B 类缺键当 0**，落在 DV-k 基线内、归 §五 下一轮（本轮不顺手改）；
                ⚠ E-1 / #51 / N-1 / F-1 的 COMPLETED 全文不在这里重述，永久记录见 DONE 的对应各节与审计 §79 / §78 / §77 / §80
                以下保留 **F-1 那一遍**的 COMPLETED 原文（永久记录见 DONE「F-1」/ 审计 §80 / D-034）：
                本轮（F-1 · 指令 v2.1 §六/§七/§三十一 异常可信化）**先把"失败"拆成两类，再让"取消"不再是失败**——
                **动手前量到的三份事实决定了范围**：全 main 里生效的 `catch (e: Throwable)` 只有 **2** 处且两处都合法
                （`Attempt.kt:22` 兜住一切除取消之外的事 / `ActionGate.kt:63` 清锁后原样 rethrow），
                `catch (Exception|Throwable)` 含注释 **69** 处、去掉注释 **49** 处，其中**挂起形状 48 + 无挂起点 1**；
                §七 点名要的那个 unified helper **工程里早就有** —— `apiCall`（`Api.kt:60`，`suspend inline`、先穿透取消、五类 typed 分支、
                `ApiTest.kt:165` 有"只分类不吞异常"一条用例），但**生产调用点实测 0** ⇒ 本轮**没把它接进任何路径**
                （接它要同时改 38 处读接口的返回形状，那是另一轮的范围），只登记"已存在但没人用"、**也不删**（删它是减量不是收口）。
                **§六 那一半（缺陷不许进演示数据）**：`Api.execute` 的演示回落面由"兜 Throwable"收成**只兜 `NetworkException` / `ApiException`**
                两类，其余原样抛出 ⇒ `IllegalState / NPE / ClassCast / LinkageError / OOM / AssertionError` 再也不会被写成
                "后端给了演示数据"（回落一次还把堆栈丢掉，缺陷再也复现不了）；`DemoData` 的 **4** 处回落点与 **4** 道
                `if (!demoAllowed) throw e` **一一对应**（TH-b 4/4），release 的 `DEMO_ALLOWED` 仍是硬编码 `false` 且**读取点只有一个**（TH-k 1/1）。
                **§七 那一半（取消不是失败）**：新 helper 定名 `attempt {}` 而不是 `safeApiCall`（它的使用面超出网络层），
                与 `runCatching` **唯一**区别是先 `catch (e: CancellationException) { throw e }`；`Repo` 的 **38** 处
                `catch (e: Exception)` 是**逐点配对**不是一把 sed；UI/服务侧按"取消到底从哪来"分**三种**收法
                （换 `attempt` / 在既有出口前补穿透 / **把整圈 `runCatching` 仪式直接摘掉** —— `WorkbenchScreen.kt:434-440`
                那一圈的唯一实际作用就是在半空中接一次取消，给它补取消出口反而是给不存在的通路开药）；
                `HomeScreen` 轮播**故意不是裸 `throw e`**（手指抢走滚动时的取消必须咽下去，否则轮播会把自己停掉，写成"Job 真被取消才 rethrow"）；
                `load()` 与 `work()` 这台"所有页面的漏斗"**同一轮都补**了取消出口（TH-i 1）。
                **用例（§三十 点名的那两个类）**：`DemoExceptionIsolationTest` **7** + `ApiCancellationTest` **5** = **+12**，
                每条"缺陷/取消被错收"都配一条"真失败照常工作"，否则**"整条兜底删掉"也能骗绿**；
                ⚠ 取消用例的**诚实契约**：kotlinx 在边界处会做堆栈恢复，逃出去的是**同类副本 + 探针在 cause 链里**，
                所以钉的是"类型同一性 + cause 链含探针 + `requestCount == 0`"，**不写"是同一个对象"那种证不到的话**。
                **判据**：新立 **TH-a…TH-l**（12 行读数 / **27** 道闸门；实测 a 7/5 · b 4/4 · c 2/0/2 · d 38/38 · e 0‖48/1 · f 12/1 ·
                g 0 · h 6/1/2 · i 1 · j 1/1/0 · k 1/1 · l 2/2/0）+ 新负控簿 **18 条破口**（批处理实测 **✔ 行 19** = 18 破口 + 1 条恢复自检）。
                三条**新方法**（都是复测或负控抓出来的，不是先想清楚的）：
                ① **"配对"既不数关键字出现次数、也不靠手写文件清单** —— 第二版扫一份 14 个文件的清单，结果把
                `DriverSdkNavCard.kt:291`（唤起外部地图、try 里**零挂起点**）判成违例，而清单外的文件**永远是盲区**
                ⇒ 改成**扫描面由内容决定**（逐个 `catch (e: Exception)` 看它 try **首段**有没有挂起点），
                并把**违例 / 挂起形状 / 免检三个数一起打** —— 只打违例数的判据，在"规则被写宽"与"破口被删干净"两种情况下输出一模一样；
                ② **三个"目标 0"各有出生证明**：TH-c 的 Api=0 由"IOException 出口退回 Throwable"打出 exit 1、
                TH-e 的违例 0 由"摘掉一处 `throw e` 但保留子句字面量"打出（那一次 **TH-d 照样 38/38 绿** ⇒ 正好证"数子句"
                与"逐点配对"不是同一条判据）、TH-l 的兜底 0 由"`NetworkException` 出口换成 `RuntimeException`"打出；
                ③ **负控脚本自己也要防"注释顶替生效代码"**：`swap` 现在强制 `assert old not in new`（V-3 与 ⑳ 都栽过一次，
                本轮把它写成脚本里的机器闸，而不是一句提示）。
                **⑲ 的升级规矩本轮照做，且这是历来最大的一次锚点扰动面**（`Repo.kt`/`Api.kt` 被 52 处改写命中）⇒
                **十三本负控全批复跑**（实测墙钟 16:41–16:58 ≈ **17** 分钟，按 `/tmp/*_neg_*.log` 的 mtime 清点）；
                跑完**必须重测全量**（就地簿带 `--tests` 过滤会把 `test-results/` 洗成只剩一类 ⇒ 上一轮那个"299"当场**失去产物支撑**）。
                ⚠ **收台账这一遍自己又抓到四条"自述与实测不符"**（逐条当轮改掉，明细 D-034 末尾）：
                ① 负控条数**又是推算的**（台账写 ✔=18，留档行数实测 **19**）；② `idempotency_test` 那本的 **✔=0 是标记体系不同、不是没跑**
                （它打印的是 `[破口后] gradle exit=1` / `[恢复] cmp 一致` / `[恢复后] exit=0`）；
                ③ **比对基线的类名写错会造出假差异**（手写成 `MissionMapContractTest` / `ProductOrderContractTest`，真实名没有 `Contract`）；
                ④ **台账把 `WorkbenchScreen` 写成"先穿透取消"，实测那一处是"整圈仪式摘掉"** —— ⚠ 这一条**任何判据都抓不到**：
                TH-d/TH-e 只看 `catch (e: Exception)` 的形状，"**本来就没有 catch**"这种收法在判据里是隐形的，只能靠读到自己写的那句假话。
                **实测**：`:app:testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL in 57s`、**`24 actionable tasks: 24 executed`**、
                **31 类 / 311 tests / 0 failures / 0 errors / 0 skipped**（31 份 XML mtime 全为 **17:02:02**，逐份读出再自相加）；
                **+12 的归因是机器比对**：新增类只有那两本（7 / 5）、消失类 `[]`、**既有 29 类逐份条数一字未变**（`CHANGED: []`）。
                APK **114,437,316 B** / mtime **16:45:11**（16:4x 那次 `:app:assembleDebug :app:lintDebug` → `48 actionable tasks: 12 executed / 36 up-to-date`）；
                收工复检同一组任务 → **`48 actionable tasks: 1 executed / 47 up-to-date`** ⇒ **没有重新打包**（照实记 UP-TO-DATE），
                而这份 UP-TO-DATE 恰好是"盘上这个包与当前源码内容自洽"的**机器证明**（gradle 按内容哈希判，配合各就地簿末尾的 `cmp 一致`）；
                lint **0 error / 46 Warning / 32 Information = 78**（`lint-results-debug.xml` 98,753 B / **16:47:20**）与 §79 那轮**逐项相同**
                ⇒ 本轮 17 个文件的改动**没有引入任何新的 lint 类别**（新文件 `Attempt.kt` 在 `location/@file` 里 0 点名）；
                **十三套探针全 rc=0**、**负控十三本 rc 全 0 且 `✘=△=0`**。
                ⚠ **设备侧一字未验**（`NOT VERIFIED ON DEVICE`）：真实的取消来自"手指划走页面 / Worker 被系统停止 / 进程被杀"，
                JVM 用例只能造 `cancelAndJoin` 与 `withTimeoutOrNull` 两种，"页面离开后不再往 StateFlow 上写"没有设备证据。
                ⚠ E-1 / #51 / N-1 的 COMPLETED 全文不在这里重述，永久记录见 DONE 的对应三节与审计 §79 / §78 / §77
IN_PROGRESS:    **F-2 ⑧ 的收工台账（2026-10-01 03:2x）**：改动面与证据已按 §三十三 顺序 3→7 跑齐（负控全批 → 全量用例 → lint/打包 → 判据簿 → 台账），
                余下的只有台账誊写本身 ⇒ 这半段**不再起 gradle**、`app/src/main` 与 `tools/` **一字不动**（三态读数见 `CURRENT_ANDROID_STATUS`）。
                ⚠ 台账里两处"本轮证据比上一轮弱"必须照实写、不许抹平：① **+12 例只归因到"新簿 12 例 + 总数差恰好等于它"**，
                没能做逐类名机器比对（⑦ 那 37 份 XML 已被覆盖、工程无 git，手上没有 ⑦ 的逐类名快照）；
                ② `app/src/main` 的 mtime 有相当一部分是**就地型负控的注入与还原**（02:52–03:06 那批），不能拿来报改动面。
                以下保留 **F-2 ⑦** 那一遍的 IN_PROGRESS 原文（该轮已收工，永久记录见 DONE「F-2 ⑦」/ D-039 ~ D-041）：
                本轮（F-2 ⑦）的**收工验证**在跑（§三十三 顺序 3→7：负控 → 全量用例 → lint/打包 → 判据簿 → 台账）；
                本轮**源码与脚本改动面已完成**（三态读数见 `CURRENT_ANDROID_STATUS`），剩下的只有"证据按顺序补齐"。
                ⚠ **2026-09-30 深夜登记的两条排队项，本轮（2026-10-01 / D-040）第①条已从"注释"升级成"机制"**：
                ① 负控批原先**没有互斥锁** —— 上一批被"停止"后它的子进程仍在跑，与本批并行 ⇒ 两本**就地改源码**的簿互相踩，
                `role_reconcile` 与 `role_reconcile_test` 各报一次 **`✘ 基线未通过`（rc=2）**；同一窗口里 `shell_routing_test` 的
                **M-4** 被读成"判据不判"，事后按日志时间戳查明是**注入根本没生效**（锚点被同行还原掉 ⇒ `AssertionError`，而簿子不看注入退出码，
                拿未修改源码撞上 `FROM-CACHE` 的绿色）。⇒ **这些读数一律不采信**（历史数字不回改，只在原处加日期标注）。
                现在的机制：`tools/negctl_lock.sh`（16/16 本已挂，抢不到锁 `[STOP]` + rc=3、持有者已死则接管陈旧锁、
                批次持锁时 `NEGCTL_LOCK_HELD=1` 让批内沿用同一把），`red()` 补"锚点没命中 **exit 2** / 注入后字节没变（空跑）**exit 5**"，
                簿尾 `| tail -1` 换成"重定向后取 `$?`，判据簿 rc≠0 就 **exit 6**"（同一条坑的第三种形状）。
                ② 就地型负控会把自己的 gradle 用例数与 `test-results/` 洗掉 ⇒ **收工顺序"负控在前、全量用例在后"**
                这轮也做成了机制：`tools/after_negctl_run_full_tests.sh` 等锁释放**且**批次打印"共 16 本"、再核 `rc=0` 行数=**16** 才起第二个 gradle。
                ⚠ ~~**修锁后的串行 16 本复跑仍在跑**（00:35:40 起，截至 00:50 完成 2 本）~~
                **2026-10-01 01:20:15 已跑完**：末行"共 16 本"、逐本读数表与 `✔` 口径拆分见本文件负控节（合并读数 **289 ✔ / 0 ✘ / 0 △**）；
                全量用例 01:25:50 那遍 **37 类 / 356 / 0 失败**（`TEST_STATUS` 已更新）。
                ⚠ **批次结论不是"16 本全绿"**：`shell_routing_test` 在 01:15:05 被**新立的基线闸门**拦下（rc=2 自拒），
                根因是**树里带着一行没还原的负控注入**（`nav/ShellDecision.kt:36`）—— 这是本轮唯一一件"机制第一次上岗就抓到真东西"的事，
                还原 + 复跑 + 台账更正全过程见 **D-041** 与 §八 ㉔ ⑦；`DECISION_LOG.md` 里 D-040 那句"两批并行没把源码留在破口状态"**已就地标注证伪**。
                ⇒ **F-2 ⑦ 到此收工**（⑦ 的 18 步里没有"设备"这一步，`NOT VERIFIED ON DEVICE` 一字未改）。
                **下一轮 = ⑧**，开工前先跑 `bash tools/check_shell_routing_probes.sh` 与全量用例各一遍确认树是干净的（本轮的教训就是"探针全绿不证树干净"）。
BLOCKED:        ~~#2 高德导航 SDK 未进依赖（jar 内 `com/amap/api/navi/` 零类）⇒ §18 NavigationEngine / Phase 3–5 全挂~~
                **本轮（N-1）解除**：依赖顶替为 `com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600`，`core/navigation/`
                两层引擎 + 调用点 + 用例 + 判据 + 两类负控全部落地（N-a…N-l 实测见 DONE 的 N-1 一节）。
                ⚠ **解除不等于收口**：替换出来的新偏差是**这一层的运行时行为零验证**（见 DEVICE_STATUS 与 §九），
                而且"**后端不下发逐转向**"这半条**没有变** —— 屏幕上的转向只有两个来源（后端 `routeGuide` 的段/站、
                本机高德），**不存在第三个来源**，也不许拿本机引擎去顶替后端线路事实
                ENVIRONMENT_BLOCKED 工作区非 git 仓库（git rev-parse 报 fatal）⇒ §121 的 checkpoint commit 做不了，不 git init 冒充
                ENVIRONMENT_BLOCKED 真机项（§119/§120）全部未验证 ⇒ 规则 L：NOT VERIFIED ON DEVICE
                **F-1 按 §三十三 的四类分型记账（本轮没有"笼统一句构建失败"）**：
                ① `SOURCE_FAILURE` **无** —— 本轮 gradle 三条命令全部 rc=0（`testDebugUnitTest --rerun-tasks` / `assembleDebug` / `lintDebug`），
                   没有任何一次失败需要归因到源码；
                ② `DEPENDENCY_FAILURE` **无** —— 依赖坐标实测仍 **20** 条、本轮零新增构件（新 helper 是自己写的 `core/net/Attempt.kt`）；
                ③ `ENVIRONMENT_FAILURE` **两条在册**：(a) 本机非 git 仓库 ⇒ §121 checkpoint 做不了（`git rev-parse` 报 fatal，**不 git init 冒充**）；
                   (b) **用户名含非 ASCII 路径**会让 Gradle 测试工作进程起不来，绕法是命令行加 `-Dfile.encoding=GBK`
                   —— 这是**环境**缺陷：**没有**改任何源码或脚本来"绕过"它（硬规则：wrapper/环境失败不许改代码绕），
                   所有 gradle 读数都带这个开关、抄数时也要连开关一起抄；
                ④ `DEVICE_UNAVAILABLE` **就是本机现状** —— `verify_device.sh --no-build` rc=1、设备列表空白 ⇒
                   F-1 的 52 处改动**全部只有 JVM 侧证据**（见 DEVICE_STATUS 那五条新增未验项）。
                **本轮实跑 `assembleRelease` 才坐实的一条**（⚠ 我一开始写成"台账从没记过这条闸门"，**被 grep 推翻**：
                闸门本身三处记过 —— 本文件"生产化阶段"一节第 3 行、`DECISION_LOG.md:85`、审计 §1104；
                **没记的是"release APK 从未真正产出过"这件事**，以及内网开关的名字
                `-Pbuspost.allowInsecureRelease`（四本台账 `grep` **0 命中**））：
                `assembleRelease` **本轮第一次实跑即被挡在配置期**
                （`app/build.gradle.kts:171`：`release 包必须使用 HTTPS（当前 http://1.15.29.107/api）`，exit 1）。
                ⚠ **没有加 `-Pbuspost.allowInsecureRelease=true` 绕过** —— 那条内网开关正是"release 关闭 cleartext HTTP"
                这条红线要拦的东西；要产出 release 得先把 `buspost.baseUrl` 换成真实 https 域名 ⇒ **记 BLOCKED，不记通过**
                ⚠ V-1 的**真机半边仍未验**（本条不是 BLOCKED 而是范围界定）：闸门在进程内存里，
                **杀进程重启后 10 秒保留窗即失效**，"重复点击"防住了、"重复进程"防不住 —— 要防后者得问后端要键，
                而后端没有（本轮已实测），所以这条只能记着，不许在客户端造假持久化
                ~~⚠ V-2 的尾巴 V-2b：冷启动不复查 `/driver/profile`~~ **本轮已修**（机制见 COMPLETED 与 DONE 的 V-2b 一节）；
                留下的半边是**真机未验**：撤档账号在真实后端上冷启动是否只发一条请求并被弹回用户壳，
                JVM 侧靠 MockWebServer 的 `requestCount` 钉住，**设备上没有验过**
                ⚠ V-3 的同一条界线：**异常是否真落库只验到"回读段状态"**（本环境无后端在环）。页面说的是
                "后端回读：第 N 段「异常」"，**没有**说"调度已收到"——后端把 remark 写进哪张表、通知谁，仍是未验事实
                ⚠ **#51 新登记的一条功能缺口（不是 bug，是契约缺字段带来的刻意不做）**：**线路页的"导航"入口只在 DEBUG 可达**，
                release 那一屏没有导航按钮 —— `TransportLeg` 只有站名、**零坐标字段**（§36 在册偏差的本体），
                拼不出 `destination=lat,lng`。**解锁条件**：后端下发段几何或站点坐标（§36/§44 同一条路），届时放出并同轮补判据；
                **不许**拿站名反查一个点补上（那是 §36 禁止的补法）。代价照实记：司机在线路页要导航得先回工作台那一节
NEXT:           ~~路线图第 8 步（导航引擎，"先 `:app:dependencies` 再 `javap`，拉不到就维持 BLOCKED"）~~ **上轮完成（N-1）**
                → ~~队列 **#51 旧 `DriverNavScreen` 转 DEBUG ONLY**（按硬规则**不删文件**；它同时是 §24 假步进 6 处的宿主，
                两条一起收才有意义）→ §24 假步进 6 处 → 0~~ **本轮完成（#51：6 → 0，降级未删文件，明细 DONE 的 #51 一节 / 审计 §78）**
                → ~~队列 **E-1 解析层 6 处自造别名 `"eta"`**（`RouteGuide` 那处读的是导航引擎自己的报文，
                改前必须先核那份契约，不许凭键名猜）~~ **本轮完成（E-1：6 → 0，三方实测 + 三条判据 + 4 条用例，
                明细 DONE 的「E-1」一节 / 审计 §79 / D-033 / 判据 §八 ⑳）**
                → ~~队列 **F-1 指令 v2.1 §六/§七/§三十一：程序缺陷不进 DemoData、取消不被吞~~**
                **本轮完成（F-1：`Api` 回落面收成两类 + `attempt{}` 落地 + Repo 38/38 逐点配对 + 12 条新用例 +
                TH-a…TH-l 判据 + 18 条破口负控，明细 DONE 的「F-1」一节 / 审计 §80 / D-034 / 判据 §八 ㉑）**
                → ~~队列 **F-2 第 1–5 步：v2.1 落档 + §三 `QuoteResult` 缺键塌 0.0 + §四 `optBoolean(…, true)` + §二十四 Minutes 别名 + §五 数字默认值普查**~~
                **本轮完成（F-2 ①–⑤：四本判据簿 exit 0 + `T-aj`/`Q-*`/`B-*`/`DV-*` 三族新立 + 27 条新用例 + 普查 472/B 398/C 74/A 0，
                明细 DONE 的「F-2」一节 / D-035 / D-036 / `docs/android/ANDROID_V2.1_CHECKLIST.md`）**
                → ~~队列 **F-2 第 6 步：用户端 `create` 幂等**（V-1 用户侧镜像，HTTP 200 + `code=900` 被当失败那条）——
                **复用既有 `ActionGate`，不重写闸门**（V-1 的 `I-*` 那本只管司机侧，用户侧要新立判据）；
                配套 `UserCreateIdempotencyTest`：**连点 5 次只 1 次 POST**、**失败后必须允许重试**（闸门不许把失败钉死）~~
                **本轮完成（F-2 ⑥：用户域另开一把 `ActionGate` 实例 + 两处 create 走闸 + 键与后端同三维 + 10 例新用例 +
                `U-*` **12 条读数**（同簿 22 条 = `I-*` 10 + `U-*` 12）与 **13 条负控**（全簿 25 行 = 24 ✔ + 1 ◦），
                明细 DONE 的「F-2 ⑥」一节 / D-038 / 判据 §八 ㉒ + ⑭ 的 U-* 段）**
                → ~~队列 **F-2 第 7 步：`sealed interface VerificationFailure` 四类**（`SourceFailure` / `DependencyFailure` /
                `EnvironmentFailure` / `DeviceUnavailable`，V21-33）—— **只用于工程验证与自检输出，不许塞进业务错误通路**
                （业务侧的失败已经有 `ApiException` / `NetworkException` / `Ack.error` 三套，再来一套就是第四份真相）~~
                **本轮完成（F-2 ⑦：唯一规则表 + 分类器 + 七份本工作区产出的真实样例（含一份 rc=4 反例）+
                test 源集类型与 **8 例** + `VF-a…VF-g` **17 条读数** rc=0 + **13 条负控全点名** +
                `verify_device.sh` 两个分支接上分类器（并修掉 `| tail -3 || die` 那条**从不触发**的旧闸门）；
                ⚠ 落点与指令字面有一处刻意偏差（不进 `app/src/main`），明细 DONE 的「F-2 ⑦」一节 / D-039 / 判据 §八 ㉓）**
                → ~~F-2 余下步骤 7–18 中排在前面者~~ **在册顺序**：
                → ~~**下一轮 = ⑧** `safeApiCall()` **只迁 5 个高风险点**~~ **本轮完成（F-2 ⑧：`apiCall` → `safeApiCall` 改名不改签名 +
                5 点迁完（真实修复 2 / 形状归一 3）+ 页面折线状态与两句文案 + `SafeApiCallContractTest` 12 例 + SA-a…SA-f 判据簿 +
                第 17 本负控 17 条破口；⚠ 开工时那句"`apiCall` 生产调用点现为 **0**"是本轮的**起点证据**而不是漏检，
                明细 DONE 的「F-2 ⑧」一节 / D-042 / 判据 §八 ㉕）**
                → **下一轮 = ⑨** 剩余 `runCatching` 协程审计：动的基数是 ⑧ 开工前同轮实测的三种口径
                **44 出现次数（`grep -o`）/ 43 行（`grep -n`）/ 34 生效行（滤注释后）/ 22 文件**
                ⇒ 与 F-1 那遍记的"**全 main 剩 34**"**不是矛盾而是两条判据**（34 是滤注释的行数、44 是一行可含两次的出现次数），
                以后抄这个数**必须连口径一起抄**；第 9 步要动的基数是 **34**，不是 44
                （**⑧ 收工复跑 `check_throwable_probes.sh` 打印的"只打数"那行 `runCatching` 全 main = 34，与这个基数一字不差 ⇒ ⑨ 开工不必重测基数，只需按文件拆**）；
                ⑩ `NotifyWorker` 重试语义（`NotifyWorker.kt:19` 无条件 `success` 改掉）+ `NotificationWorkerResultTest` →
                ⑭ OSM/ODbL App 内可见署名 + `ThirdPartyNoticeTest` → ⑮ `./gradlew dependencies` 与 APK 实际依赖对表；
                **第 8 步的下半边有硬门槛**：`Initializing` 的等待上限、`getPathRetainDistance/Time` 的单位、
                `getCurStep()` 的语义，都要**真机在环**才定得出来，本机不许编数（编了就是假判据）
TEST_STATUS:    **44 类 / 438 tests / 0 failures / 0 errors / 0 skipped**（**F-3 ⑤ 收工读数（2026-10-02 10:08:30）**：
                负控全批放行后**单发起** `:app:testDebugUnitTest --rerun-tasks` → **`BUILD SUCCESSFUL in 51s` / rc=0 / `24 actionable tasks: 24 executed`**
                （`/tmp/after_negctl.log`，10:07:40 起跑、10:08:32 结束、`AFTER_RC=0`），`test-results/testDebugUnitTest/` **44 份 XML 的 mtime 集合只有一个值 `['10:08:30']`**，逐份读 `testsuite` 属性自相加（不抄控制台摘要）。
                **438 − 423 = 15** 恰等于本轮新簿 `PolylineDecisionContractTest` 的条数（423 是 **④⑫ 那一遍**的读数，D-048 第 2 节，历史值不回改）；
                ⚠ **其余 43 类逐份一字未变本轮没做机器比对**（工程无 git、上一轮的 43 份 XML 已被覆盖）⇒ 归因只到"总数差 = 新用例数"，不到"其余未变"（与 F-2 ⑧、F-3 ④⑫ 同口径）。
                ⚠ **这一行不是每轮都更新**：F-2 ⑧ 之后各轮（含 F-3 ②③ 的 43 类 / 416）只把读数写进各自的 `CURRENT_ANDROID_STATUS` 块 ⇒ 中间值不在这行上，抄数要连块一起读。
                **以下保留 F-2 ⑧ 那一遍的读数（38 类 / 368，历史值不回改）**：
                （**F-2 ⑧ 收工读数（2026-10-01 03:11:16）**：由 `tools/after_negctl_run_full_tests.sh` 接在负控批次（`共 17 本`，03:10:35 结束）之后单发起，
                同一条带 GBK 配方的命令 `:app:testDebugUnitTest --rerun-tasks` → **`BUILD SUCCESSFUL in 30s` / rc=0 / `24 actionable tasks: 24 executed`（无 FROM-CACHE）**，
                `test-results/testDebugUnitTest/` **38 份 XML 的 mtime 集合只有 `03:11:16`**（一份都没被就地型负控洗掉 ⇒ 那几本的还原是干净的），逐份读 `testsuite` 属性自相加。
                **+12 例的归因（356 → 368）**：新增簿**只有** `SafeApiCallContractTest` **12**（SA-f 钉的那本；本轮另点名四本：
                `ApiTest` 11 / `VerificationFailureTest` 8 / `DemoExceptionIsolationTest` 7 / `ApiCancellationTest` 5）；
                ⚠ **这条归因比上一轮弱，照实记**：`368 − 12 = 356` 与 ⑦ 收工读数逐字相符、类数 37→38 也对得上，
                但**没能**做"其余 37 类逐份一字未变"的机器比对——⑦ 那 37 份 XML 已被本轮覆盖、工程无 git、手上没有 ⑦ 的逐类名快照
                ⇒ 本轮只能说"总数差恰好等于新簿例数"，**不能**说"其余各类一字未变"（这条在 ⑧ 不可证，不许拿上一轮的措辞套）。
                **以下保留 F-2 ⑦ 那一遍的读数（37 类 / 356，历史值不回改）：**
                （**F-2 ⑦ 收工读数（2026-10-01 01:25:50）**：`./gradlew "-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK" -Dfile.encoding=GBK :app:testDebugUnitTest --rerun-tasks`
                → **BUILD SUCCESSFUL in 38s / rc=0 / `24 actionable tasks: 24 executed`**，`test-results/testDebugUnitTest/` **37 份 XML mtime 全为 01:25:50**（mtime 集合只有一个值），
                逐份读 `testsuite` 属性自相加（㉒ ⑤ 的两件都核：executed 数与产物在盘）。这一遍是由 `tools/after_negctl_run_full_tests.sh` 接在负控批次之后自动起的
                （等锁释放 + 批次打印"共 16 本"，实测等待 1900s，日志 `/tmp/after_negctl.log`）。
                **+8 例逐条可归因（348 → 356，机器比对不是回忆）**：新增簿**只有 `VerificationFailureTest` 8 例**（F-2 ⑦ 的四类分型，VF-f 钉的 4/4/8），
                `REMOVED: {}`、`CHANGED: {}` ⇒ 既有 36 类逐份条数一字未变。⚠ 这次比对**两侧同剥 `Test` 后缀**（上一轮的 `RepoTest` 假差异就是这么造出来的），
                剥完 36→37 只多出一个键，比对本身不再需要特例表。
                ⚠ **同一命令在 01:20:31→01:21:30 那一遍是 rc=1 / `BUILD FAILED in 57s` / `24 actionable tasks: 24 executed` / 37 份 XML mtime 01:21:27 / 356 例里 1 failure**，
                红的那一份是 `ShellRoutingContractTest`（tests=10 fail=1）。**那不是回归，是本册 ⑦ 抓出"树里带着一行未还原的负控注入"的证据**
                （`nav/ShellDecision.kt:36` 缺 `role != Role.DRIVER &&` 守卫，01:25:00 按簿内 from 串还原），全过程见 **D-041** 与 §八 ㉔ ⑦；
                还原后的复跑：定向 `:app:testDebugUnitTest --tests …ShellRoutingContractTest` rc=0 / 10 例 0 失败（01:23:02），
                `shell_routing_test` 单本复跑 **✔=7 ✘=0**、两文件 `cmp 一致`、`[恢复后] gradle exit=0`（01:25:00）。
                **以下保留 F-2 ①–⑤ 那一遍之前的全部历史读数 —— F-2 ⑥ 那一遍（36 类 / 348）：**
                （**F-2 ⑥ 收工读数（2026-09-30 21:07:20）**：`./gradlew "-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK" :app:testDebugUnitTest --rerun-tasks`
                → **BUILD SUCCESSFUL in 41s / rc=0 / `24 actionable tasks: 24 executed`**，`test-results/testDebugUnitTest/` **36 份 XML mtime 全为 21:07:20**，
                逐份读 `testsuite` 属性自相加（㉒ ⑤：`executed` 数与"产物在不在"是两件事，本轮两件都核）。
                **+10 例逐条可归因（338 → 348，机器比对不是回忆）**：新增簿**只有 `UserCreateIdempotencyTest` 10 例**，
                `REMOVED: {}`、`CHANGED: {}` ⇒ 既有 35 类逐份条数一字未变。
                ⚠ **本轮为"进程级闸门状态"付过一次学费后改的是测试侧复位**：`TestHost.resetActionGate()` 原先写死两个字段名，
                新增第二把闸后**写死的那版本轮就漏过一把闸**（`SendOrderTest` 八个用例里六个提交内容完全相同的 `form`，
                不复位就会被自己的闸吞掉）⇒ 改成**按字段类型筛 `ActionGate`**，生产代码上**没有**开测试专用的口子。
                **以下保留 F-2 ①–⑤ 那一遍的读数（35 类 / 338）：****35 类 / 338 tests / 0 failures / 0 errors / 0 skipped**
                （**F-2 ①–⑤ 收工读数（2026-09-30）**：`./gradlew "-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK" -Dfile.encoding=GBK :app:testDebugUnitTest --rerun-tasks`
                → **BUILD SUCCESSFUL in 40s / rc=0 / `24 actionable tasks: 24 executed`**，**35 份 XML 的 mtime 全为 20:30:14**，逐份读出再自相加（不抄控制台摘要）。
                **+27 例逐条可归因（311 → 338，机器比对不是回忆）**：新增簿**只有四本** —— `MinutesContractTest` **6** /
                `QuoteNullSafetyTest` **7** / `NearbyBooleanContractTest` **6** / `EarningsContractTest` **8** = **27**；
                `REMOVED: {}`、`CHANGED: {}` ⇒ 既有 31 类逐份条数一字未变。
                ⚠ **比对第一次又造出一次假差异**：把 XML 类名剥掉 `Test` 后缀去对旧表，`RepoTest` 就变成"新增 `Repo` / 消失 `RepoTest`"——
                **归一化键名本身就是差异来源**，F-1 那次是手写错类名，这次是代码里的一行 `replace('Test','')`；第二次跑用原样类名才是零假差异。
                **35 行逐类表（加总 338）**：`ActionGate 5 / ApiCancellation 5 / Api 11 / BusEtaContract 13 / CoordinateTransform 12 /
                DemoExceptionIsolation 7 / DistanceCaliberContract 4 / DomainSplit 7 / DriverActionIdempotencyContract 6 /
                DriverExceptionContract 6 / DriverExceptionReport 7 / DriverLegRespContract 6 / DriverLegStateMapper 11 / DriverState 11 /
                **EarningsContract 8** / LocationReportPolicy 15 / LocatorCoordinate 6 / LoginViewModel 6 / **MinutesContract 6** /
                MissionMap 18 / NavigationContract 19 / **NearbyBooleanContract 6** / ProductOrder 7 / **QuoteNullSafety 7** /
                ReachabilityContract 17 / RealRoadRouteValidator 16 / RepoTest 11 / RoleReconcile 5 / RouteGuideContract 12 /
                ScalarEnvelopeContract 17 / SendEtaContract 12 / SendOrder 8 / ShellRoutingContract 10 / StatusMappingContract 17 /
                WorkbenchRoleReconcileContract 4`
                ⚠ **这一遍差点跑不出来，三条过程教训必须一起抄**：
                ① **不与负控批处理并行**：20:18 那一遍与批处理末两本（`shell_routing_test` 20:14:14–20:15:39 / `throwable` 20:15:39–20:17:32）撞车，
                   六个 worker 全打 `Could not write standard input … 管道正在被关闭`，而 `--rerun-tasks` 照样报 "24 executed" ——
                   **executed 不等于跑出结果**：那一遍 `test-results/` 里只有批处理残留的**一份** XML。
                ② **`./gradlew --stop` 之后连红三遍** ⇒ 排除"守护进程状态坏了"，问题在**起来的那台守护进程的参数**。
                ③ **根因是字节级可证的**（不是猜的）：worker 的 classpath 走 @argfile `~/.gradle/.tmp/gradle-worker-classpath*txt`。
                   今天 **16:09:55–20:15:22** 那批 argfile 是 **GBK**（`袁` = `D4AC`，25,821 B）⇒ worker 起得来（17:02 的 311、19:17 的 338、批处理全部都在这里）；
                   **20:19:16 起**那批是 **UTF-8**（`袁` = `E5A489`，26,019 B）⇒ `java.exe` 的 native launcher 按 **ANSI 码页 936** 解 argfile，
                   非 ASCII 路径（`C:\Users\袁\…` 与工程目录里的"测试"）被解坏 ⇒ `ClassNotFoundException: worker…GradleWorkerMain`。
                   切换点 = `gradle.properties` 的 `org.gradle.jvmargs=… -Dfile.encoding=UTF-8`：**客户端命令行的 `-Dfile.encoding=GBK` 不会进守护进程的 JVM 参数**，
                   只有"守护进程是以什么参数起来的"算数 —— 16:10 那台守护进程恰好是 GBK，一路服务到 20:15；我 20:18 重跑时新建的是 UTF-8 ⇒ 假红。
                   **处置只动命令行、没动工程里的 `gradle.properties`**（那是配置而不是绕码，且会改变所有人的构建形状）：
                   加 `-Dorg.gradle.jvmargs="… -Dfile.encoding=GBK"` 后同一遍立刻 rc=0，argfile 实测回到 25,821 B 的 GBK 形状、35 份 XML 齐。
                   ⇒ **抄 TEST_STATUS 必须连这串 jvmargs 一起抄**，否则下一轮拿到的是"同样命令、结果全红"。）
                   ⇒ 这一条已升为永久规矩 **§八 ㉒ ⑤**，决策与字节证据另记 **D-037**（含"混合编码日志要把 grep 当字节工具用"那条计数口径）。
                以下保留 **F-1 那一次**的读数（永久记录见 DONE「F-1」/ 审计 §80 / D-034）：
                **31 类 / 311 tests / 0 failures / 0 errors / 0 skipped**
                （**F-1 收工读数**：`:app:testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL in 57s`、
                **`24 actionable tasks: 24 executed`**、rc=0；**31 份 XML 的 mtime 全为 17:02:02**，逐份读出再自相加（不抄控制台摘要）。
                ⚠ 这一遍**必须**重跑：本轮五套**就地**用例级负控（异常 / 幂等 / 壳路由 / 导航 / 角色）在 16:41–16:58 之间带 `--tests`
                过滤跑过，会把 `test-results/` 洗成只剩一类 ⇒ **上一轮"299"那份读数已失去产物支撑**，不许沿用、也不许拿 `from-cache` 当"重跑过"。
                **+12 例逐条可归因，而且是机器比对出来的**（299 → 311）：新增类**只有** `DemoExceptionIsolationTest` **7** 与
                `ApiCancellationTest` **5**，消失类 `[]`，**既有 29 类逐份条数一字未变**（`CHANGED: []`）；
                ⚠ 第一次比对把基线类名手写成 `MissionMapContractTest` / `ProductOrderContractTest`（真实名**没有** `Contract` 后缀），
                于是打出"新增 5 类 / 消失 3 类"的**假差异** —— **"无孤儿类"这个结论依赖基线表的键名**，键名写错时它以"多算 8 条"的形式骗人。
                ⚠ 新簿的条数与判据下限是**同一轮**钉的（TH-a `≥7 / ≥5` 就是这两本），沿用 ⑬ 那条"加用例必须同轮抬 pin"的教训。）
                **F-1 的逐类表（31 行，加总 311，与 XML 逐份一致）**：`LocatorCoordinate 6 / CoordinateTransform 12 /
                RealRoadRouteValidator 16 / NavigationContract 19 / **ApiCancellation 5** / Api 11 / **DemoExceptionIsolation 7** /
                ScalarEnvelopeContract 17 / BusEtaContract 13 / DistanceCaliberContract 4 / SendEtaContract 12 /
                StatusMappingContract 17 / DriverLegRespContract 6 / ActionGate 5 / DomainSplit 7 /
                DriverActionIdempotencyContract 6 / ReachabilityContract 17 / RepoTest 11 / RouteGuideContract 12 /
                DriverState 11 / LocationReportPolicy 15 / RoleReconcile 5 / ShellRoutingContract 10 /
                DriverExceptionContract 6 / DriverExceptionReport 7 / DriverLegStateMapper 11 / MissionMap 18 /
                WorkbenchRoleReconcileContract 4 / LoginViewModel 6 / ProductOrder 7 / SendOrder 8`
                （⚠ **这张表里的类名是本轮从 31 份 XML 逐份抄下来的**，不是回忆 —— 上面那次假差异就是回忆出来的）
                以下保留 **E-1** 那一次的读数（永久记录见 DONE「E-1」与审计 §79 七）：
                **29 类 / 299 tests / 0 failures / 0 errors / 0 skipped**
                （**E-1 收工读数，两次都有产物**：`:app:testDebugUnitTest :app:assembleDebug --rerun-tasks` →
                **BUILD SUCCESSFUL / RC=0 / 43 actionable tasks: 43 executed**（04:08，本轮是**全量重跑**不是增量），
                随后**五套**就地用例级负控（异常 / 幂等 / 壳路由 / 导航 / 角色，逐套都带 `--tests` 过滤）把 `test-results/` 洗成 1 份 ⇒ 末遍再 `--rerun-tasks` 复跑
                → **BUILD SUCCESSFUL / 24 actionable tasks: 24 executed**，**29 份 XML 的 mtime 全为 04:21:33**，
                逐份读出再自相加（不抄控制台摘要）⇒ 上面这 299 是**跑完所有负控之后**的那一遍，不是负控之前的旧数）
                **+4 例逐条可归因**（295 → 299，本轮加的就是这 4 条，一类都没新增）：
                `BusEtaContractTest` **13**（12→13，新用例 `自造别名 eta 与 minutes 不是契约键…`）/
                `SendEtaContractTest` **12**（9→12，订单与段、轨迹、路线引导各一条）/
                `DistanceCaliberContractTest` **4**（⑬ 的那批，本轮未动，逐份复看确认没被我的 sed 波及）；
                ⚠ 判据下限与用例数是**同一轮**抬的（T-k 12→**≥13**、T-x 9→**≥12**）——
                §72 记过一次"加用例不抬 pin ⇒ 负控从红变灰"，本轮不再犯；
                ⚠ **并行 gradle 造出来的假红**那条永久规矩沿用 N-1/#51 的教训：本轮所有单类调用与全量调用**一律串行**，
                一台机器同一时刻只有一个 gradle 调用（细节见 §八 ⑱ ⑧）
BUILD_STATUS:   **F-2 ⑦（2026-10-01 00:0x）读数**：`./gradlew -Dorg.gradle.jvmargs="-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK" -Dfile.encoding=GBK :app:assembleDebug :app:lintDebug`
                → **BUILD SUCCESSFUL / rc=0 / `48 actionable tasks: 3 executed, 45 up-to-date`**。
                ⚠ **这一轮"没重打包"是对的、不是遗漏**：本轮改动**全在 test 源集与 `tools/` + `docs/`**，`app/src/main` 一字未动
                （判据 `VF-c` 钉 main 里四类符号 **0** / `VerificationFailure` **0** 个文件）⇒ 盘上 APK 仍是 F-2 ⑥ 那份
                **21:13:59 / 113,863,001 B**、lint 三份仍是 **21:09:49**（XML **98,753 B** / html 258,534 B / txt 31,452 B，
                **0 error / 46 Warning / 32 Information = 78**）—— 两个 mtime 都**早于本轮**，照实记 UP-TO-DATE，
                **不写成"重新构建通过"**（同一台机器上"改一行注释也会让刚验过的包作废"那条 ㉒ ④ 的教训反过来用：没改 main 就不该假装包是新的）。
                **dex 侧**（2026-10-01 收工复验，`unzip` 出 **15** 个 dex 拼接后 `grep -a -o … | wc -l`，**出现次数**口径）：
                本轮新串 `VerificationFailure` **0** / `SourceFailure` **0** / `EnvironmentFailure` **0** —— **这是设计结论不是失败**：类型在 **test** 源集，
                测试类不进包（同批 F-2 ⑥ 已用 `UserCreateIdempotencyTest` **0** 演示过同一个形状）；
                ⚠ **0 命中先证方法**：**同一批 dex、同一脚本**里已知旧串数到 `userCreateGate` **1**（命中 1 份）·
                `gatedUserCreate` **4**（1 份）· `DriverLegStateMapper` **5**（1 份），所以那三个 0 是"确实不在包内"，不是 grep 坏了。
                ⚠ §三十三 第 6 步 `:app:assembleRelease` **rc=1**：被工程自带的 HTTPS 闸门挡在**配置期**（`app/build.gradle.kts:171`），
                **没有**加 `-Pbuspost.allowInsecureRelease=true` 绕过；本轮把这份日志交给新分类器判读 ⇒ 输出 **`UNCLASSIFIED`**，
                **这是设计意图**（策略拒绝不属于 SOURCE/DEPENDENCY/ENVIRONMENT/DEVICE 四类），
                ⇒ **没有为了让它落进某一类而往规则表加行**。release 仍 `BLOCKED`（见 BLOCKED 那节）。
                **以下保留 F-2 ⑥（21:1x）那一遍的读数，原文一字未动：**
                `./gradlew -Dorg.gradle.jvmargs="-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK" -Dfile.encoding=GBK :app:assembleDebug :app:lintDebug`
                → **BUILD SUCCESSFUL / rc=0 / `48 actionable tasks: 11 executed, 37 up-to-date`**（21:08 那一遍）。
                ⚠ **这一遍暴露出一条新口径（本轮最重要的过程发现，已升入 §八 ㉒ ④ 加强版）**：打印里 `packageDebug` 先报 executed、
                复跑又报 UP-TO-DATE，而**盘上那份 APK 仍是 19:33:42 的 `7fd54a4a…`**，`unzip` 出 15 个 dex 全扫 `userCreateGate` **0 命中**
                （新符号只在 `mergeProjectDexDebug` 里，当时 mtime 21:08:18 —— **进了中间产物、没进包**）
                ⇒ **`UP-TO-DATE` / `executed` / `BUILD SUCCESSFUL` 三种打印都不是"包内新鲜"的证明**，必须在 dex 里 grep 到本轮新串才算。
                处置：`:app:assembleDebug --rerun-tasks`（**`37 actionable tasks: 37 executed`**）逼出真重打包，
                得到 APK **113,863,001 B** / mtime **21:13:59** / sha1 **`03ab61726030a19f431da275f0bfe3deaa976d2c`**（15 个 dex）。
                ⚠ 与 19:33 那份**同字节（113,863,001 B）而不同哈希** ⇒ **体积相同不等于内容相同**，与 #51/E-1 的"字节可比性"口径同源；
                ⚠ `--rerun-tasks` 把中间产物重写了（现 mtime 21:13:53），"有符号没进包"那份现场**取不回来**
                ⇒ 只记为**当时观察到、事后不可复现**，不作为已证归因（本机无 git，这是每次都提醒的那条）。
                **包内实测**（本轮新串 + 已知旧串**同批一起数**，用来先证计数方法没坏）：
                `userCreateGate` **1** · `gatedUserCreate` **4** · `create:` **1** · `Lcom/shanxiang/buspost/data/repo/ActionGate;` **2** ·
                旧代码 `actionGate` **1** / `gatedAction` **4** ⇒ 司机域没被用户域的改动顶掉（硬规则 1 的两侧同时在包里）；
                文案 `"本次点击没有发给后端"` **2**（`SendScreen.kt` / `GoodsDetailScreen.kt` 各一份）；
                **0 命中三条**：`UserCreateIdempotencyTest` **0**、`DriverActionIdempotencyContractTest` **0**、`Lokhttp3/mockwebserver/` **0**
                —— 按 ㉒ ④ 的反证规矩读：同一脚本、同一批 dex 里已知串数到了非 0，所以这三个 0 是"确实不在包内"，不是工具坏了；
                ⚠ 后端 900 那句 `"订单正在创建中，请勿重复提交"` 包内也 **0**，而**这是对的**：它是后端 `message`，本机只透出、不内置，
                把它写成"包内应命中"就是把契约抄成了字符串。
                lint（21:09:49 三份同批：XML **98,753 B** / html 258,534 B / txt 31,452 B）→
                **0 error / 46 Warning / 32 Information = 78**、**10 个类别** ⇒ **与 F-2 ①–⑤、F-1 逐项相同，⑥ 没引入新类别**；
                本轮改动的生产文件被点名 **`SendScreen.kt` 7 处读数 / 6 个不同位置**
                （`AutoboxingStateCreation` :132/:427 + `DefaultLocale` :502/:586/:675/:879/**:879 重复**）——
                全表"完全相同的 `(id,file,line,message)`"重复读数实测 **1** 条，就是这条 :879，
                与上一轮"`grep -c` 数 9 / ElementTree 数 8"是同一现象；行号相对 19:27 那份整体下移（:421→:427、:496→:502 …）
                是 notSent 分支在它们上头插了行，**不是新增问题**。`Repo.kt` / `ActionGate.kt` / `GoodsDetailScreen.kt` **0 点名**。
                依赖坐标实测仍 **20** 条（`sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"[^"]*:[^"]*:[^"]*"'` → 20），
                `app/build.gradle.kts` mtime **09-30 00:40:50**、**`gradle.properties` mtime 09-29 02:29:42 ⇒ 均一字未动**
                （本轮照旧只在**命令行**给 `-Dorg.gradle.jvmargs`，没把环境缺陷写进仓库配置）；
                新测试类引入的 `mockwebserver` 是 `testImplementation`（包内 0 条类型描述符，见上），**没有新增依赖坐标**。
                ⚠ `:app:assembleRelease` **仍是 exit 1**，被工程自带的 HTTPS 闸门挡在**配置期** ⇒ 见上面 BLOCKED，不许写成"release 构建通过"。
                以下保留 **F-2 ①–⑤ 那一遍**（2026-09-30 20:3x）的读数（永久记录见 DONE「F-2 ①–⑤」/ 审计 §81 / D-035 · D-036）：
                `./gradlew "-Dorg.gradle.jvmargs=… -Dfile.encoding=GBK" -Dfile.encoding=GBK :app:assembleDebug :app:lintDebug`
                → **BUILD SUCCESSFUL in 4s / rc=0 / `48 actionable tasks: 4 executed, 44 up-to-date`** ⇒ **没有重新打包**（照实记 UP-TO-DATE）。
                APK `app/build/outputs/apk/debug/app-debug.apk` **113,863,001 B** / mtime **19:33** / sha1 **`7fd54a4a54acde1560c07dbed3a650e30b617f4e`**
                （与 §八 ㉒ 里那条"APK 新鲜度只认包内内容"同一口径复验：**`unzip` 出 15 个 dex 全扫**，本轮字面量实测
                **"金额未给" 2 / "后端无此口径" 1 / "未给" 36 / "暂无报价" 0** ⇒ 包内内容与 19:33 那一遍**一致**，
                而 19:33 那遍是本轮收工包；这次 UP-TO-DATE 因此是"盘上这个包与当前源码内容自洽"的机器证明
                —— 各就地负控簿末尾都打了 `cmp 一致`，四本判据簿批后复跑仍全部 rc=0，两条证据互相咬合）。
                lint **0 error / 46 Warning / 32 Information = 78**、**10 个类别**（`lint-results-debug.xml` **98,753 B** / mtime **19:27**，
                html 258,534 B / txt 31,452 B 同批）⇒ **与 F-1 逐项相同，本轮 7 个生产文件的改动没有引入新的 lint 类别**；
                ⚠ 但**本轮改动的文件在 lint 里被点名 8 处**（用 ElementTree 逐 `location/@file` 数，不是 `grep -c` 的 9 —— 那个 9 里有一条重复位置）：
                `AutoboxingStateCreation` 3（`SendScreen.kt:132/:421`、`HomeScreen.kt:129`）+ `DefaultLocale` 5（`SendScreen.kt:496/:580/:669/:873`、`HomeScreen.kt:1003`），
                **两类都是既有类别**（`Models.kt` / `Repo.kt` / `DemoData.kt` / `EarningsScreen.kt` / `BusScreen.kt` 本轮 0 点名）——
                抄成"本轮文件 0 点名"就是假话，抄成"新增类别"也是假话，**8 处 / 2 类**才是实测。
                依赖坐标实测仍 **20** 条（`sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"[^"]*:[^"]*:[^"]*"'` → 20），
                `app/build.gradle.kts` mtime **09-30 00:40**、**`gradle.properties` mtime 09-29 02:29 ⇒ 一字未动**
                （⚠ 这一条特意量：修编码问题时的处置是**在命令行加 `-Dorg.gradle.jvmargs`**，**没有**去改工程里的 `gradle.properties` ——
                那是把环境缺陷写进仓库配置、改变所有人的构建形状，属于"改配置绕过环境问题"，与"wrapper 下载失败不许改代码绕"是同一条红线）；
                以下保留 **F-1** 那一次的读数（永久记录见 DONE「F-1」/ 审计 §80 / D-034）：
                **F-1（2026-09-30 本轮）读数**：16:4x 那次 `:app:assembleDebug :app:lintDebug` → **BUILD SUCCESSFUL / RC=0 /
                `48 actionable tasks: 12 executed / 36 up-to-date`**，APK **114,437,316 B** / mtime **16:45:11**；
                lint **0 error / 46 Warning / 32 Information = 78**（`lint-results-debug.xml` **98,753 B** / mtime **16:47:20**，
                html 258,534 B / txt 31,452 B 同批）⇒ 与 §79 那轮**逐项相同**，**本轮 17 个文件的改动没有引入任何新的 lint 类别**
                （新文件 `core/net/Attempt.kt` 与两份新测试在 lint XML 的 `location/@file` 里 **0 点名**）。
                全量用例那一遍另跑 `:app:testDebugUnitTest --rerun-tasks` → **`24 actionable tasks: 24 executed` / 57s / rc=0**
                （⚠ 这一遍**重编了**：本轮 52 处 catch/attempt 改写命中 `Repo.kt`/`Api.kt`，编译任务不可能 UP-TO-DATE ——
                "跑过测试"与"重编过"这一遍是同一条证据，不是两遍）。
                **收工复检**（所有负控簿跑完、就地文件 `cp` 恢复之后）再跑 `:app:assembleDebug :app:lintDebug` →
                **`BUILD SUCCESSFUL in 3s` / `48 actionable tasks: 1 executed / 47 up-to-date`** ⇒ **没有重新打包**，照实记 UP-TO-DATE；
                ⚠ **但这一遍不是走过场**：gradle 的 UP-TO-DATE 是按**输入内容哈希**给的，而本轮五套就地负控在打包之后又改过、
                再用 `cp` 恢复过真实源码 ⇒ "打包相关任务全部 UP-TO-DATE"正是**盘上这份 16:45 的包与当前源码内容自洽**的机器证明
                （配合各簿末尾的 `cmp 一致`；**mtime 变了不等于内容变了**，与 #51/E-1 同一口径）。
                ⚠ `:app:assembleRelease` **仍是 exit 1**，被工程自带的 HTTPS 闸门挡在**配置期** ⇒ 见上面 BLOCKED，不许写成"release 构建通过"。
                以下保留 **E-1（上一轮）** 的真重打包读数（永久记录见 DONE「E-1」/审计 §79 七）：
                `:app:testDebugUnitTest :app:assembleDebug --rerun-tasks` →
                **BUILD SUCCESSFUL in 1m34s / RC=0 / 43 actionable tasks: 43 executed**（**那轮是全量重跑，不是增量**），
                APK **113,863,001 B** / mtime **04:08:39**。
                ⚠ **与 #51 那次的 126,516,687 B 相差 12,653,686 B，这两个数不可比**：#51 是
                `7 executed / 36 up-to-date` 的**增量包**，本轮是 43/43 的全量包，口径不同。
                方向与"`3dmap` 已被 `navi-3dmap` 顶替、旧 `.so` 只可能活在增量产物里"一致
                （transforms 缓存里两件 `libAMapSDK_MAP_v10_0_600.so` 实测 raw 33,410,540 B / deflate 合计 **14,377,503 B**，
                与差额同量级、差 **1,723,817 B ≈ 12%**）；本轮当前包的 `merged_native_libs` / `stripped_native_libs`
                里**只有** `libAMapSDK_NAVI_*`（arm64 41,461,168 + v7a 27,076,716），已无 MAP 那件 —— 这是"今天这张图是干净的"的实测。
                ⇒ **只记为可复核的解释，不记为已证归因**：上一轮的包已被覆盖、本机无 git，决定性证据取不回来。
                **永久口径：跨轮引用 APK 字节必须同时记 `N executed` 数，否则体积差会被读成功能变化。**
                以下保留 **#51** 那一次的读数（增量口径）：`:app:testDebugUnitTest :app:assembleDebug` → **BUILD SUCCESSFUL / RC=0**、
                `7 actionable tasks: 7 executed / 36 up-to-date`，APK **126,516,687 B** / mtime **02:52:37**。
                ⚠ 与上一轮**同字节**属预期（本轮零新增文件、没动依赖、没动体积相关开关），而**同字节不等于没重打包** ——
                重跑与否只认那 `7 executed`；末遍 `--rerun-tasks` 只跑测试任务、没有再打包，所以盘上这份包仍与当前源码自洽
                （用例级负控"改—恢复"后的 `NavigationEngine.kt` 已由 `cmp` 证与基线**逐字节相同**、实测字节数 **9,756 B** 与本轮改动前一致，
                故它的 mtime **02:59:07** 晚于本轮那五个源文件的改动时间**不是破绽** —— 那是 `cp` 原样恢复留下的时间戳）。
                以下保留 **N-1 那一次**的读数：`:app:assembleDebug` → **BUILD SUCCESSFUL / exit 0**，APK **126,516,687 B** / mtime **2026-09-30 01:12:54**
                （⚠ 那一次的 `executed` 计数**没有留档**，所以"重跑过"不由 gradle 报语作证，由**产物本身**作证：
                这份包里有 `libAMapSDK_NAVI_v10_0_600.so` 与 **15** 个 dex，而换依赖前的包没有 —— 它只能是换依赖之后打的。
                收工终检（02:0x）再跑 `assembleDebug` → **37 actionable tasks: 37 UP-TO-DATE ⇒ 没有重新打包**，
                照实记 UP-TO-DATE；`NavigationEngine.kt` mtime **01:34:23 晚于 APK** 不是破绽 —— 那是用例级负控用 `cp`
                **原样恢复**留下的时间戳（`cmp` 已证逐字节相同），gradle 按内容哈希判，所以 UP-TO-DATE 与它是自洽的）；
                `:app:lintDebug` → **BUILD SUCCESSFUL / exit 0**，本轮 01:41:07 那次真重跑产出三份报告，
                终检再跑 → **28 actionable tasks: 1 executed / 27 up-to-date**、XML mtime **未变（01:41:07 / 98,753 B）**；
                XML 解析（`ElementTree` 逐条数 `issue@severity`）= **0 error / 46 Warning / 32 Information = 78**；
                ⚠ **本轮新增/改动的文件在 lint XML 里 0 点名**（`core/navigation/*`、`DriverSdkNavCard.kt` 逐条查
                `location/@file` 全部 False；导航相关只有既有的 `DriverNavScreen.kt` **2** 条 Information 级
                `AutoboxingStateCreation`，属 §24 那笔在册旧账）
                ⚠ `:app:assembleRelease` **exit 1**，见上面 BLOCKED 那条 —— 不许写成"release 构建通过"
                **APK 体积归因（本轮改成实测，不再是量级推断）**：包内 `lib/` 解压后 **77,129,060 B**，其中高德
                `libAMapSDK_NAVI_v10_0_600.so`（arm64 41,461,168 + armeabi-v7a 27,076,716）**68,537,884 B**、
                `libneonui_shared.so` + `libneonuijni_public.so` **8,553,784 B**；换依赖前那件 `3dmap-10.0.600.jar`
                带的是 `libAMapSDK_MAP_v10_0_600.so` 两 ABI 共 **33,410,540 B** ⇒ **仅原生库净增 43,681,128 B**（占差额 63.7%），
                其余 **24,894,185 B 是残差**（差额减原生库）—— ⚠ **上一轮包里的 dex/assets 没留档，分不出增量**，
                本轮只测了**当前包**的绝对量：dex **15** 份（`classes.dex` 解压 42,929,392 B）、assets 428 条 / 22,545,847 B；
                全包解压后 **166,142,033 B**、压缩后 **126,516,687 B**。⚠ `.so` 是 **jar 内 `lib/<abi>/`** 带进来的
                （两个 pom 都写 `<packaging>jar</packaging>`，navi jar 2,403 条 / 104,351,090 B 未压缩），别按 AAR 解释
                dex 符号复核（Python 逐份扫 **15** 个 dex，**口径 = 类型描述符 `L…;`**）：
                `Lcom/shanxiang/buspost/core/navigation/AmapNavigationEngine;` **1**（classes15）/
                `…core/navigation/NavigationEngine;` **2**、`…NavigationEngines;` **2**、`…BlockedNavigationEngine;` **1** /
                `…core/navigation/NavigationGuidance;` **2**、`EngineReadiness;` **2**、`NavigationSession;` **2** /
                `Lcom/shanxiang/buspost/ui/driver/DriverSdkNavCardKt;` **1**（classes3；⚠ 顶层 `@Composable` 的文件 facade
                带 `Kt` 后缀，扫 `DriverSdkNavCard;` 会得 **0** 并误判成"没打进包"）/
                SDK 侧 `Lcom/amap/api/navi/AMapNavi;` **3**、`model/AMapNaviPath;` **3**、`model/NaviInfo;` **3**、
                `model/NaviPoi;` **3**、`AMapCalcRouteResult;` **3**、`SimpleNaviListener;` **3**、
                `enums/PathPlanningStrategy;` **1**（classes14）/ 地图侧仍在包里：`maps/MapView;` **2**、`maps/AMap;` **3**、
                `maps/MapsInitializer;` **2**、`maps/model/LatLng;` **4**，`assets/amap_sdk_shaders` **5** 条 / 4,302 B
                ⚠ **两个口径不可互相引用**（描述符口径数类型，裸名口径还会数到常量池里的方法名与字符串：
                同一份包里裸名 `calculateDriveRoute` **3**、`onInitNaviSuccess` **3**、`serverSaysDriver` **2**），
                历史行里的数字都是描述符口径；历史 dex 份数是 **14**，本轮起是 **15**，跨轮不可直接比
CURRENT_ANDROID_STATUS:  L-1／㊼ 到近三档声道分层 ＋ 布局走查第一次给"工具读数"下判决（2026-10-09 02:43:47 APK 终值 → 02:08:40 设备取证 → 05:01:18 判据全批起跑 → 05:05:32 终值；台账 D-068 / 审计 §87 / §八 ㊼ / 新文件登记 `THIRD_PARTY_NOTICES.md` 的 L-1 那一节 / 证据 `docs/device-evidence/20261009-020840/`）
                **这一轮真正买到的两件**：① "说不说、说什么"从 Compose 里搬进了 **JVM 可测的纯层** —— 新 main **3** 本（`MissionApproach.kt` 阈值/相位/句子 ＋ `ApproachTracker`、`ApproachFeed.kt` 帧装配、`ApproachAnnouncer.kt` 只管"用什么出声"）＋ 新 test **2** 本（行首四空格缩进的 `@Test` 现读 **11 ＋ 11** 条），出口层**自己不写任何阈值**是这一族分层的定义；② `tools/layout_audit.py` 第一次进环，两条可疑读数**都回同一份 dump 的节点属性对质**后一条被否、一条成立（见下）。
                **三档声道逐档降级、每一档都回写屏上**：系统 TTS ⇒（初始化失败 / 无中文数据 / `speak()` 返回失败）通知栏 `Channels.SHIFT`（HIGH，会震会响）⇒（通知也被系统关掉）只剩屏幕那一行并明写"本机没出声：既没有可用语音，通知也被系统关着"。**这一句为真的前提是 `NotificationManagerCompat.areNotificationsEnabled()` 的读数**，不是"我以为会响"。开关话术**必须与「我的」那一行逐字同名**才算点名：判据 `NAC12 / NAC13` 各钉 **1** 处（本轮为此新加的两格，各配一条会红的注入、实测 `exit 1`）。
                **布局走查的两条读数，一条我否掉了、一条立成新缺陷**：**否掉**的是"页首标题与副标题兄弟重叠"—— 回读同一帧两个节点：都是 `android.widget.TextView`、下面那个还 `clickable=true`，PNG 上两行明显分开 ⇒ **工具局限、不算缺陷、0 行代码为它改动**（留下的规矩：兄弟重叠要连 `class` ＋ `clickable` ＋ bounds 一起看，单看 bounds 相交不够；这一格**故意没改工具**，改了就没有"工具也会报错"这份证据）。**成立**的是 `r68-15` 那个「旧导航页（调试）」入口：`clickable="true"` 而 `text` / `content-desc` **全空**、`bounds=[17,1993][83,2104]` ⇒ **66×111 px = 25.1×42.3 dp**，两个维度都在 48dp 下限以下且 TalkBack 念不出这是哪儿 ⇒ 立 **#313**；与 D-067 的 `FF-h`（切换链接塌成 **59.0×40.4dp**）同族，但那一条在登录页、这一条在司机侧调试入口。⚠ **两半证据缺一不可**：只给 bounds 算得出尺寸认不出是哪一格，只给 PNG 看得出样子给不出可复核的数字。
                **收工时红的五本判据簿全是本轮自己造的，终值全绿且没有放松任何断言**：`MR-i`（工作台重排把失败支条件由 `routeFailure != null` **扩成** `missionGuide != null || vm.routeFailure != null`，因为后端会把班次明细答成 `{"code":0,"data":null}`，旧条件让整块导航面**无声消失**；锚点按今天的形状重钉、窗口 `s+8`→`s+10`，目标仍 **1/1** 与 **1/1/1**）· `NAC2 2→3`（"恰好 N"的摩擦设计：加那一行开关就必须同轮把数改对，脚本里同轮写下增量出处）· `SU-b 53→55`（被本轮新建的 **2** 本 test 推动的重锚，**不是抬基线**）· `L-w`（**这条红是判据自己读错了**：闸门在上层块而赋值行不带闸门，旧写法只在同一行找 ⇒ 实测 `LW=1` 而代码是拦住的；判据改成"赋值行**或**上层块都算"，**这是缩小判据面**，所以两边都证：两条非法形状 `exit 1` ＋ 两条合法形状 `exit 0`）· `DA`（**18** 处陈旧锚点逐条 grep 现读源码重锚 ⇒ 终值 新增 **0**、消失 **0**、`ID-MISS` 286→**278**；闸 **286** 一个字没抬）。
                ⚠ **两处读数自纠，都是我的读法而不是代码**：① 一次性驱动脚本的 `run_one` 只认"必须红"那个方向，于是两条**应当不红**的反证被打印成 `✘ 负控制失效` ⇒ 同一个 `✘` 字样两种含义；已给常设簿补 `ctl_loc_keep()` ＋ 两条反证，重跑两边都 `✔ exit 0`；② 我此前口头说过"判据 **24** 本"，本轮 `ls tools/check_*probes.sh | wc -l` 现读是 **23** ⇒ **24 是没量过的记忆**，而 §86 那句 `24/24` 用的是**另一把尺子**（含 `check_form_field_probes.sh`），两个数**不互引**。本轮判据全批：`SUMMARY start=05:01:18 books=23 rc0=23 red=0 end=05:05:32`（`C:/temp/r69_pass.log` 末行）。
                ⚠ **跨簿影响这一格是"没兑现"，不是"做到了"**：上一轮在**本簿** D-067 那一块的 `NEXT` 第三条写死了"下一次收工必跑负控全批 **28** 本"，本轮**只跑了三本单簿 ＋ 一次定向锚点审计**（`books=28` / 语料 **232** 个文件 / live **243** / DEAD **1**）⇒ 台账里**不许**出现"负控全批绿"这句。替代办法**能答**"我改的源码文本被哪几本负控当字面锚点"，**答不了**"某本簿注入面里别的形状有没有被我这几行连带打红" —— 后者只有批能给，缺口挂 NEXT 第一条。那 **1** 条 DEAD（`verification_failure_negative_controls.sh` 里那句 `'DEVICE_UNAVAILABLE\tno devices/emulators found\t'`）**只登记不修**：它要么是字面串该更新、要么是注入器本来就造不出这句，**两者的修法不同**。
                **本轮自己欠下的两笔都在新建的那本出口层里（#314）**：`AnnounceChannel`（`TTS` / `NOTIFICATION` / `TEXT_ONLY`）**声明了 0 个读取点**、屏上走的是 `_status` 的自由文本，而那行的注释自己写着"屏上要说清楚" ⇒ 声明与实现两套口径；本机 TTS 与高德内置语音是**两个引擎**、导航中两路声音会叠着说、**没有仲裁**（要读高德的 `isTtsPlaying()`，而它在 x86_64 那侧至今没有设备证据）。⚠ 死声明**本轮不删**是权衡不是遗漏：交付包与 **26** 张设备读数都挂在同一版源码上，删一行就要重打包并重对字节/dex 那一格。
                **用例与包（口径分开，不许并成一个数）**：debug 测试产物 **53** 份 XML / **560** tests / failures **0**；release **47** 份 / **476** / failures **5**（`Earnings 1 / Minutes 1 / NearbyBoolean 1 / NoProducerKeyFamily 2`，D-054 那族在册旧红，**没让它进 debug 那句 0 failures**）。canonical 包 `app-debug.apk` **114,551,597 B** @ **02:43:47** / `86c8e8df78d2dc1c…`（本轮那 **5＋4＋2** 格描述符命中就是从这颗包的 **16** 个 dex 读出来的，两半证据同包）；⚠ **26 张设备读数挂在联调包** **114,551,589 B** / `4fb22eec…` 上（差 **8 B**、是两个文件）。体积对 D-067 交付 `d7d2d801` 的差 **+342,796 B** 登记为**「增量未归因」**（#140 / `D-⑧-1` 同族）：本轮新增 3 本 main、无新依赖、无新资源，但旧包已被覆盖且本机无 git ⇒ **不做差量实验就不写归因**。
                **三态分写**：`SOURCE_COMPLETE`（三本新类 ＋ 两本新用例）＋ `BUILD_VERIFIED`（debug 那份 0 failures 的产物）＋ `DEVICE_VERIFIED` **部分** —— 本轮设备那 **26** 张买到的是**布局/触控判决**，不是"到近那句话在屏上响过"；`AnnounceChannel` 与两路仲裁都在**未证**那一格（#314）。`release` 仍 **BLOCKED**（HTTPS 闸门，未变）；**GIT** 仍 **ENVIRONMENT_BLOCKED**。
                **NEXT（按用户指定顺序，不得打乱）**：① **补跑负控全批 28 本**并落批次日志（本簿那一笔欠账，红在那句承诺而不是代码）；② **#313** 那个入口的**热区与朗读名一起补**（只补尺寸不补 `content-desc` 等于换一半）；③ **#314** 到近出口层那两笔（死声明要么按通道渲染要么删、仲裁等 `.so` 那侧的设备证据）；④ **#245 / #140 / #159 / #278–#281 / #283 / #284 / #296 / #299 / #304 / #306 / #307** 原样挂着；⑤ 额定载重重复呈现与"—"那一处措辞仍按上一轮的"推迟"登记，**不新开账**。
CURRENT_ANDROID_STATUS:  #283/#288/#289 表单控件三格收到"改前改后成对设备像素读数"级（2026-10-08 18:0x 取证 → 18:27:35 APK 终值 → 18:53:58 装机回读 → 19:0x 落档；台账 D-067 / 审计 §86 / §八 ㊻ / 证据 `docs/device-evidence/20261008-181700`）
                **这一轮的口径升级只有一句话**：前三轮设备在环买到的是"句子对不对、返回键退到哪"，本轮买的是**控件边界本身** —— 源码写了描边 ≠ 屏幕上有边界，所以每一格都要**同一 x/y 带的改前读数与改后读数并列**，只写改后那一栏不算数（`1dp = 2.625px`，`emulator-5554` / 1080x2400 / density 420）。
                **三格成对读数**（全部来自 `contrast-L.log`，17 行）：① 输入框边界 改前 y=1342 只有 `(245,248,252)`×`(247,250,253)` = **1.02:1** 且 **0 个边框段**，改后同一行多出 `(199,213,224)` 的 3px 描边段而 1.02 那一格**一字未变** ⇒ 描边是唯一可视边界（`FF-a` = 1）；
                ② 禁用「登录」按钮 改前板 `(155,191,226)`、界内**最亮** `(200,220,239)` = **1.37:1**，改后板 `(228,235,242)`、界内**最暗** `(90,86,75)` = **6.09:1** ⇒ 灰板与灰字**两支必须齐**（`FF-g` = 1/1）；
                ③ 切换登录方式那一格 改前 `[53,1666][208,1772]` = **59.0×40.4dp**、中心 **x=130**（贴 20dp 边距），改后 `[413,1685][667,1811]` = **96.8×48.0dp**、中心 **x=540 = 屏宽/2** ⇒ 没有宽度支撑的右对齐空转不许回来（`FF-h` = 0）。
                **本轮被证据推翻的是我自己的读法，不是代码**：第一版对比脚本对禁用态只取界内**最暗**像素 —— 改后那版（灰字压灰板）读出 6.09:1 看着正常，可**改前那版是白字比蓝板更亮**，最暗像素就是板自己 ⇒ "板 = 字"、ratio 恒 **1.00**、两版读数一样，这条判据**当场没有分辨力**。若我抄了那个 1.00，台账会写成"改前只有 1.00:1"，而那是**坏读法造出来的假事实**。
                改法：最亮/最暗各取一次、**取离板色更远的那个**，两个 ratio 一起进日志。同一脚本另一处坏读法：用"既非底也非填充"粗筛边框会把 x≈89..93 的**字形抗锯齿边缘**当边框 ⇒ 改成「x≥53 且既非 `paper` 也非 `field` 的**第一段**」。⇒ 这是"目标值类读数必须先证明这条读法能分辨改前改后"，与"目标 0 要反证"同一条规矩的两个方向。
                **#288 的验收形状**：`LoginViewModel` 有 **4** 个 `notice` 写入口而改前渲染点 **0** 个，改后 `FF-c` = **1**，且成功支「验证码已发送」`[53,1829][239,1892]` 与失败支「先填写 11 位手机号」`[53,1829][320,1892]` 都有 bounds、**同一帧互斥**（失败支那一帧成功句命中 **0** 次）。⚠ "互斥"不是顺手记的：判据只数渲染点处数，若实现是"两处各渲染一半"，成功支同样能读到字，而失败时会把两句一起念出来。
                **`FF-d` = 0 的诚实性依据**：后端 `expire-times` 是 **10m**，而 `Repo.sendSmsCode` 把响应体整个丢掉、客户端**从未**拿到过期时间 ⇒ 屏幕不许承诺客户端不知道的事；负控第 4 条就是把那句自造时效等回去。
                **#289 只有设备能守**：聚焦手机号行后该行描边 **3px `(199,213,224)` → 6px `(46,123,191)`**，同屏未聚焦的验证码框仍 3px。若把 `focused` 提成两个框共享的状态，源码里 `onFocusEvent` 处数不变、`FF-f` 不红，但屏幕上两个框一起亮 ⇒ 用户分不清自己在哪个框里打字。
                **两本新簿**：`tools/check_form_field_probes.sh`（FF-a…FF-h，读数 `1 / 2 / 1 / 0 / 0 / 1 / 1/1 / 0`，rc=**0**）＋ `tools/form_field_negative_controls.sh`（**✔ 10 / △ 0**、rc=**0**，含第 10 条打**副本里判据簿自身**的 `[ "$FE" = 0 ]` → `[ "$FE" = 9 ]` 反证）。⚠ 第一版在 `SELF-MATCH` 闸门上崩（把回退写成引用一个已不存在的变量名），**报错的是注入器不是判据 ⇒ 本轮修的是注入、不是断言**：新增追加型注入器 `grow()`（允许保留锚点、只要求锚点唯一，与 `safe_call_negative_controls.sh` 同名 helper 同理由），把回退写成"先声明、再套用它"两步。第 1 条红名单是 **`FF-a,FF-b,`** 两条而不是单条：删描边那一刀必然同时删掉第二个 `RoundedCornerShape(BusRadius.field)` —— 那是"删边框"这个回归本来的样子，**如实登记，不放松判据去凑单条红**。
                ⚠ **两处自纠先于读数**（详见 D-067 §0）：① 上一段草稿把交付包指纹写成 `fb1a5fca`，本轮两条独立命令现读为 **`d7d2d801…0703b`**，并核 `grep -c` 于五本台账与 `docs/android` 全 **0** 命中 ⇒ 错串从未入册（不需要改历史，需要的是**指纹必须连取它的命令原文与纳入文件口径一起抄**）；② 草稿写过"error 分支也实测渲染"而盘上**没有任何产物**支撑 ⇒ 当场补拍（`error_probe.py` ＋ `error-probe.log`）才落成上表。**"我看到过" ≠ "我留了档"**，凡"也实测了"的并列句每条都要指得到一个文件名。
                **跨簿影响 = 定向实测，不跑全批负控**：本轮改动面是 `core/ui/Components.kt`（`BusButton` 禁用态那几行文本）＋ `ui/login/LoginScreen.kt` ＋ `ui/login/LoginViewModel.kt`，按"锚点是**源码文本**不是符号名"逐本核对注入锚点，唯一把这三位当锚点的是 `throwable_negative_controls.sh` ⇒ **单本复跑 18 ✔、rc=0**。⚠ **负控 28 本全批本轮未跑**，台账按「批内 28 本（不含本轮新簿）＋ 单本实测 rc=0」**两行分开记，不并成"全批绿"**。
                #282 **由 PENDING 改 WITHDRAWN**（不是缺陷，且 **0 行代码为它改动**）：本轮去**基准仓**对原文而不是对自己的记忆 —— `cargo-post-miniprogram/pages/index/index.wxml` 的 `top-sub` 与 `pages/login/login.wxml` 的 `hero-sub` 都是 `客货邮 · 城乡直达`，小程序分享标题是 `咱村的客货邮——公交带货、快递进村、农产品出山`，App 侧在 `core/ui/SplashOverlay.kt` ⇒ 两句**分别**服务"这是哪儿"与"这东西干什么"，各有出处。这是"看着不一致先对原文"这一族**第三次**（前两次 §八 ㉘/㊺），三次都是**同一类错误**。
                **终值读数**：构建 **BUILD SUCCESSFUL in 13s**、**37 actionable tasks：4 executed / 33 up-to-date**（⚠ 只 `:app:assembleDebug`，与 §85 那 43 张任务图不同口径，**两串数字不许互引**；33 up-to-date **不许写成"重新构建通过"**）；APK **114,208,801 B** @ `2026-10-08 18:27:35` / sha256 **`d7d2d8017e059ed9ae9942b876a2b84c5a162a5b7e8a007c3859090c9220703b`**；六包对照 `3bbf06f3` 113,928,596（D-064）/ `83dc930a` 114,239,840 / `240e5016` 114,517,119（**未上设备**）/ `3ab4ace0` 114,516,928 / `8899e8b0` 114,517,352（D-066 交付 ＝ 本轮**改前基线**）/ **`d7d2d801` 114,208,801（本轮）**。
                ⚠ 体积 **−308,551 B** 登记为**「增量未归因」**：本轮只动 3 个源文件文本、无新依赖、无新资源，旧包已被覆盖且本机无 git ⇒ 既不写成已证的体积优化、也不写成回归（同输入重打会因 dex/打包重排而摆动，见 D-045 §2）。
                装机回读 `lastUpdateTime=2026-10-08 10:27:57` 而**同一次回读**里 `date` = `Thu Oct 8 18:53:58 CST 2026` ⇒ 整 **8 小时**（设备按 GMT 念）⇒ **对账必须连 `date` 一起抄**，否则会把"刚装上"读成"八小时前装的"。判据面全批 **24/24 rc=0**（`ls tools/check_*.sh | wc -l` = **24**，含本轮新簿）；证据 **34** 个文件（`cp -p` 保时间戳）进 `docs/device-evidence/20261008-181700/`（`screen-L-*.png` / `ui-L-*.xml` / `contrast-L.log` / `focus-probe.log` / `notice-probe.log` / `notice-probe2.log` / `error-probe.log` / `install-L.txt` / `build-L.txt` / `dbg_labels.json` ＋ 三个 `.py` 探针原件）。
                **三态分写**：`SOURCE_COMPLETE` ＋ `BUILD_VERIFIED` ＋ **`DEVICE_VERIFIED` PASS**（#283/#288/#289 三格成对读数，每支配一条方法学自纠）；`release` 仍 **BLOCKED**；**GIT** 仍 **ENVIRONMENT_BLOCKED**；新挂 **#294**（`ui/` 内 `BusTextField(` 共 **29** 个调用点，只有 `ui/driver/DriverExceptionScreen.kt` 传非空 `error`，⇒ danger 红描边只在那一格有过像素机会，目前仍**只有源码证据**）；**#245** 那半格（`isFromMockProvider` 全 main **0** 读取点）继续在册；旧账 **#140 / #159 / #278 / #279–#281 / #284** 原样；TalkBack 朗读顺序仍只有形状证据。
                **NEXT**：① #294 补拍；② #245；③ **下一次收工必跑负控全批 28 本**，按固定链「负控在前 → 接力全量 `--rerun-tasks` → rc≠0 的单本补跑 → 终值全量」，批次日志要先核它确实打印了"共 28 本"再 `cp` 成 `/tmp/final_neg_batch.log`（接力脚本硬编码这个文件名，否则 `exit 7`）。
CURRENT_ANDROID_STATUS:  #270/#273/#274/#285/#286 设备轨道收工：AVD 第一次在环 → 三处硬编码村名撤读 → 三种定位形状各拍一张（2026-10-08 14:4x 冷启 → 15:39:47 登录闭环 → 16:11 设备闸门 → 17:14 APK 终值 → 17:26 无 fix／17:44 有 fix 正控／17:5x 服务范围外 → 17:58:40 负控批停；台账 D-065＋D-066 / 审计 §84＋§85 / §八 ㊹＋㊺ / 证据 `docs/device-evidence/20261008-155801` 与 `20261008-172000`）
                ⚠ **本块同时补 D-065 那一轮的欠账**：那轮只落了 D-065 / 审计 §84 / §八 ㊹ 三本，`CURRENT_ANDROID_STATUS` 与状态板都没写 ⇒ 两轮的读数在这里一次落，历史行不回改。
                **`DEVICE_VERIFIED` 三步走完**：前八轮 **BLOCKED** → D-065 **PARTIAL**（`tools/verify_device.sh` 第一次给 **DEVICE_ONLINE**：`device-check.log` rc=**0**、
                `emulator-5554 / sdk_gphone64_x86_64 / SDK 35` 一台在环，但三处补丁还没复拍）→ 本轮 **PASS**。⚠ 装机时间戳那格属环境：设备侧 `lastUpdateTime` 按 GMT 制式念，
                与本机钟面差整 8 小时，靠的正是 D-065 §5 ① 那条"进程启动时才读默认时区"。
                **这一轮的价值不在改了几行，而在第一次能拿设备读数核对前八轮的推断** ⇒ 当场收回 **3** 条误判，最重的一条是**我自己造的假缺陷**：
                `sweep5-report.txt` 那句 `实时公交… self.click=false ancestors_clickable=0` 被我写成"宫格与底栏对无障碍不可达"；带父子深度重读**同一份** xml
                （45 节点 / `clickable=true` **12** 个）证明 `Modifier.clickable` 住在**父**语义节点、label 是**子** `TextView`，扁平正则**必然**读出 **0**
                ⇒ 假缺陷已撤回、**0 行代码为它改动**；规矩改写为"读 a11y 树必须带父子深度并同时报 `clickable=true` 总数"（同族第二次，第一次见审计 §83）。
                **三处补丁（动机全部是设备读数，不是读源码猜的）**：L-v —— `Prefs.currentVillage` 的默认值**和**村庄选择器的 `ifEmpty` 填充表**各藏了一份硬编码村名**
                （16:42 把定位打到加州，顶栏念的是 `云山村`）⇒ 两处都撤，顶栏改成"三截兜底"那一句；
                L-w —— `fetchAll` 里那个"县城坐垫子"查询中心本只能用于查询，但顶栏那句是**无条件赋值** ⇒ 无 fix 时把垫子的逆地理结果念成用户位置，
                补 `g != null` 闸门后垫子不进顶栏；L-x —— 撤掉填充表后点顶栏只剩标题＋"关闭"（"点了没反应"），补那句 `暂无可选村庄：接口未返回站点`。
                三处都是**行数中性**写法，在册锚点一行没动（本轮**新写锚点 0 个**）。
                **判据与负控（都是实测）**：`bash tools/check_location_probes.sh` 现 **12 条全绿 rc=0**（L-a 2 / L-b 0 / L-b2 1 / L-c 0 / L-d 0 / L-e 0 / L-f 0 / L-g 1 / L-h 0 / **L-v 1 / L-w 0 / L-x 1**）；
                **5** 条新破口在 `tools/navigation_negative_controls.sh` 里由 `ctl_loc` 逐条配对，**全批终值 134 ✔ / 0 ✘ / 0 △**、rc=**0**、耗时 **3214 s**（17:05:06→17:58:40，
                日志 `D:\android-dev\dev\negctl-nav-L.txt`；⚠ 中途 108 行那一次我只报过"在跑"，终值等批停才抄 —— 批没停就没有终值，这是那条老规矩的又一次兑现）。
                **三种定位形状各拍一张，缺一条不许算合格**（都挂在 `8899e8b0` 这一包上，每支都配一条 `dumpsys location` 系统层回读，不靠"我注入了什么"当证据）：
                ① **无 fix**（`location_mode 0`）⇒ 顶栏 `位置未取到` ＋ `定位不可用，附近公交与天气暂按县城中心展示`、逆地理垫子地名 **0 命中** ⇒ **L-w PASS**（诚实回落）；
                ② **有 fix 且在服务区内**（合川 `30.03,106.30`；回读 `Location[gps 30.030498,106.300498 hAcc=5.0 …]` **无 `mock` 标记**）⇒ 顶栏 `合川区钓鱼城街道` ＋
                `附近共 1 条公交线路` ＋ 点顶栏后**条目 2** = `合川汽车客运中心 / 步步高公交站`，与后端那两站**逐字同名** ⇒ **L-x 正控 PASS**；
                ③ **有 fix 但在服务范围外**（北京 `39.90,116.40`，后端 `located=true`）⇒ 顶栏念真实逆地理名 `东城区东华门街道`、线路那一行**不出现**、
                点顶栏 ⇒ `空态=['暂无可选村庄：接口未返回站点']`、**条目 0** ⇒ **L-x 空态 PASS**（这一支才是那句"有 fix 但站点为空"）；
                ④ D-065 那两处旧补丁复拍（挂在 `3ab4ace0`）⇒ 副标题无裸 `。` 前缀、非首 tab 按系统返回**回首 tab**（在首 tab 仍退出，那是正确行为） ⇒ **PASS**。
                **后端真值把界面那两句话的生产者各自对上**：`/transport/bus/nearby` 无坐标回 `nearbyStations:[] lineCount:0 dataSource=NONE located=false`、
                合川回 **2** 站（3.92 km／5.57 km）`lineCount=1`、北京回 `located=true` 而站点为空 ⇒ ①③ 那两句都是**真话**，② 那句线路数与后端 `lineCount=1` 同源。
                ⚠ 这同时**反证**了 16:42 那次界面上能出现线路句 = 当时 App 确实拿到了 fix ⇒ 我先前"无 fix 也会出现那句"的前提是错的，本轮用 curl 否掉（不是靠记忆改口径）。
                **本轮最重的一条读数推翻的是我自己的环境结论**：D-065 / 审计 §84 写死的那句"`adb emu geo fix` 假成功、必须走 `cmd location providers`"**方向正好反过来** ——
                那一族把真 GNSS provider 顶掉之后，本 boot 后期它自己滑成 `enabled=false / allowed=false / last location=null`，passive 里只剩 mock 默认点
                `37.42,-122.16 hAcc=100.0`（带 `mock` 标记），而 `Locator.usable()` 只认 `network`／`gps` 两个名字 ⇒ App 如实回 `UNAVAILABLE`。
                **这不是 App 缺陷，是注入通路自己塌了**（dwalk12／13／14 三次同形，我先前把它当成"定位链路坏在客户端"）。正解两步：`remove-test-provider gps` 恢复真 provider，
                再 `geo fix`，且**必须落在 App 正在 `requestLocationUpdates` 的窗口里** —— 没人订阅时 HAL 是 `mStarted=false`／`ProviderRequest[OFF]`，喂进去的点被**静默丢弃**
                （dwalk18 实测：喂 **20** 次、`et` 一个数没动）。⚠ 又一种"回 OK ≠ 改成了"：`geo fix -122.1600 37.4200` 的**负经度**被 adb 当参数吃掉 ⇒
                本轮改用**境内但服务范围外**的北京（正数坐标、逆地理有答复、后端给空站点），形状照样成立。
                ⇒ 规矩：**"哪条注入命令生效"是 boot 级状态，不是脚本常量**；台账不许再写"必须用某一条命令"。
                **BUILD_VERIFIED**：**BUILD SUCCESSFUL in 38s**、**43 actionable tasks: 8 executed / 35 up-to-date**（17:14）。⚠ **本轮没有重跑全量用例**：
                三处补丁全是页面兜底与文案，它们的验证就是上面那三支设备读数；用例基数仍是 D-064 那一遍的 **51 类 / 538 例 / 0 / 0 / 0**，这一格**不冒充本轮新读数**。
                **APK（本轮交付终值）**：**114,517,352 B**、sha256 前缀 `8899e8b0`、mtime **17:14**，17:25 `adb install` 上设备 ⇒ 本轮**所有**设备读数都挂这一包。
                **五包对照**：`3bbf06f3` **113,928,596**（D-064 交付）/ `83dc930a` **114,239,840**（D-065 首轮走查在跑那包）/ `240e5016` **114,517,119**（D-065 重打，**从未上设备**）/
                `3ab4ace0` **114,516,928**（16:52 装，④ 复拍用）/ **`8899e8b0` 114,517,352（本轮，①②③ 都挂在它上面）**。
                **残留（都不判红、如实挂账）**：① **#245 仍不闭环**，而本轮恰好演示了它的代价 —— `isFromMockProvider` 全 `app/src/main` **0** 读取点，
                我能分辨"这个点是不是模拟的"只靠 `dumpsys` 里那个 `mock` 标记，**App 自己从来没看过这一位**；现在设备在环，这一格第一次**可测**。
                ② 一笔**误归因自纠**：`reshoot-lx2/3/4.txt` 那三张把空态标成"有 fix 但落在服务范围外（加州点）"，而它们的顶栏是 `位置未取到` ⇒ 当时**根本没有 fix**，
                真正测到的形状是"无 fix ⇒ 空态"；旧文件**一字不改**（它们是注入通路塌掉那三遍的过程证据），那一格由上面 ③ 重拍补齐 ⇒ **场景标签必须由系统层回读支撑**。
                ③ **#285** 只剩 TalkBack 朗读顺序（可点父节点自己 `text=''`/`desc=''`，标签在子节点上，语义没 merge 上去 —— 本轮只有形状证据，没有朗读录音）。
                ④ `release` 仍 **BLOCKED**（HTTPS 闸门保持）、**GIT** 仍 **ENVIRONMENT_BLOCKED**（三仓无 git，改动前后靠盘上备份与时间戳对账）、
                **#278** 未编译未在线复验、旧挂账 **#140 / #159 / #279–#284** 原样。
                **写后终检**（台账写完现读，rc 全是实测）：判据面 **23/23 rc=0**（22 本 `tools/check_*probes.sh` ＋ 补跑 `check_authenticity_counts.sh`）；
                `check_doc_anchor_probes.sh` rc=**0** —— records **1304** / OK **1003** / **DA-d 在册 262、新增 0、消失 0** / **DA-e ID-MISS 286**（= 闸值，未涨未降）/
                **DA-f 614 与 313**（≥578/≥277）/ FILE-NOT-FOUND **15**（≤15）/ 越界 **0** / 表格自检 **TOTAL FLAGS 0**（五本行数 4205 / 3629 / 1896 / 5104 / 796）。
                ⚠ **records 由 1302 涨到 1304 不是本轮块写的**：本轮新块 49 行逐行 grep `文件:行号` 现读 **0** 命中（核对表 §9 同读 **0**），
                那 **+2** 条是 D-065 落档时写进 DECISION_LOG 的（同一条 grep 在 4997 行之后现读 **2** 命中，两条都判 OK）⇒ "本轮 0 新锚点"这句话靠的是**逐行现读**，不是靠 DA-d 的"新增 0"（DA-d 只盯可疑 aid，判 OK 的新锚点它不叫）。
                **NEXT**：`#245`（mock 定位那一半 —— 设备在环了，先有读取点才谈得上放行与否）、`#278`（后端可达性过滤要真 Maven 构建＋部署＋在线复验）、
                `#140`、`#159`（含 `D-G3b-1` 那 **3** 处内联）、`#282`/`#283`（走查低度项：Splash 两套口径、登录输入框无可视边界）。
CURRENT_ANDROID_STATUS:  #277/#275/#270f（HTTP 层契约对表收尾 ＋ 两族创建回执 Ack 收口 ＋ 站点可达性那一格在后端）收工（2026-10-08 12:2x 起手 → 12:50:47 APK 终值 → 12:52:04 构建日志 → 12:56:27 设备闸门；台账 D-064 / 审计 §83 / §八 ㊸ / 证据 CONTRACT_EVIDENCE §十）
                **这一轮买到的是真后端**：48080 实测 **LISTENING = 2** ⇒ §十 的读数是真 HTTP 回执，不是"读源码猜的"（前七轮那句"后端读数全部来自读源码"本轮作废）。
                非零值逐个归因 **13** 处；换真 id 重跑 **8** 行里 **5** 行为零，零分成"状态机拒 / 业务规则拒 / 契约不匹配"三类，**只有后两类算缺陷**。
                **SOURCE_COMPLETE = 三条缺陷**：① 寄货单 Ack 在读 **4** 个三面 0 生产者的键（`bizOrderNo/no/code/fee|amount`）⇒ 金额恒塌 `0.0`；
                ② 商城单 Ack 读 `amount/totalPrice`，而创建回执 VO **只有 `id` 与 `orderNo`** ⇒ 同一个错法；两处都收成"只认生产者键"，
                并**同轮换形状**（测试夹具两处 ＋ `DemoData` 回落 `fee`→`totalAmount`）—— 只改解析会让"后端没给"与"回落没这个键"在同一行互相掩护。
                ⚠ 这两条**不是当下可见的错**：`Ack.amount` 没有页面消费点（成功页副券读的是**报价**那一路）⇒ 修的是**埋着的错**，台账不许写成"用户看到的金额修好了"。
                ③ 后端 `/send/stations` 与下单面的可达性**不同源**：未删除站点 **1814** 条里 `vehicle_access=0` 的 **9** 条（id **1–8、101**）恰好排在最前，
                而下单用 `StationAccessUtil.vehicleAccessible()` 硬校验 ⇒ 用户在"第一屏"选站点就撞 `1005016009`。
                **⚠ 这一条只做到"源码级修改"：本机没有 Maven、后端仓库无 wrapper、在跑的 `yudao-server.jar` 是 2025-09-13 的产物 ⇒ 未编译、未在线复验**（单开 **#278**）。
                同一格的正控（本轮能给的运行时证据）：可达站点对 **102→103** 打**未打补丁**的 jar，`/send/create` 回 **code=0**、
                `{"id":251,"orderNo":"TP20261008122257715","status":8,"totalAmount":8.76,"reviewStatus":1,…}` ⇒ **下单闭环通**，卡点只在**选择面**。
                **BUILD_VERIFIED**：rc=**0** / `BUILD SUCCESSFUL in 1m 49s` / **53 actionable tasks: 15 executed, 38 up-to-date**；
                用例 **51 类 / 538 例 / 0 failures / 0 errors / 0 skipped**（XML mtime **秒级单值 12:50:58**；⚠ **538 = 在册 536 ＋ 本轮 2** 条具名负控用例）；
                日志这次落在**仓库内** `docs/build-evidence/20261008-contract-round.log`（3,253 B @12:52:04）。
                ⚠ **lint 那一格是复用产物**：Error **0** / Warning **46** / Information **29**、xml 94,681 B，但**三份报告产物 mtime 都还是 11:08:01**
                （本轮 `lintAnalyzeDebug*` 重跑、`lintReportDebug` 报 **UP-TO-DATE**）⇒ "跑过这个 task" ≠ "重写了这个产物"（㊸ ⑦）。
                **判据面 23/23 rc=0**（22 本 `tools/check_*probes.sh` ＋ 补跑 `check_authenticity_counts.sh`：窄口径 10 / 宽口径 49 / GeoMath 27 / 假步进 0）。
                ⚠ 起稿时报成 **22/22** 是**换了通配基数**、不是少了一本红（㊸ ⑥）。同轮 **U-h 10 → 11**：那条"恰好 N"的摩擦设计又被真实改动证活了一次，
                三句取证齐了才动那个数（**不是抬闸值**，闸值仍只认一个数）；定向 grep 确认没有别的簿要跟着重锚。表格自检 **TOTAL FLAGS 0**。
                **APK（本轮交付终值）**：**113,928,596 B** / sha256 `3bbf06f341acd64a1c14d30d8b30524909bda1a0f43b3f7c0881c181ff86208c` @**2026-10-08 12:50:47**；
                条目 **896**、未压缩合计 **166,336,481 B**、`testzip()` = **None**。输入归因：`app/src` 里 12:00 之后动过的 `.kt` 只有 **4** 份
                （DemoData 12:25:03 / SendOrderTest 12:25:42 / UserCreateIdempotencyTest 12:47:49 / Repo 12:48:44），**全部早于打包时刻** ⇒ 四处源码改动都在包里。
                ⚠ 与 ㊷ 那一包**文件字节数相同而 sha 不同**（未压缩 **166,336,545 → 166,336,481**，**−64 B**）⇒ D-063 §8 ⑤"同字节 ≠ 没变"**第二次拿到正样本**；
                上一包（04:07:01 / `06f562a4…`）被原地顶掉 ⇒ 历史那一半仍不可复测，`#140` 保持原状。
                **DEVICE_VERIFIED = BLOCKED**：设备闸门 rc=**1** ⇒ `DEVICE_UNAVAILABLE`、**0 台在环**，证据 `docs/device-evidence/20261008-125627`
                （同批 `20261008-125616` 那一遍的 rc 当时被 `tail` 吃掉、没读到，见 D-064 §6 ③）。⚠ **后端起来了 ≠ 设备在环**，这两格本轮各是各的读数。
                **写后终检**：**23/23 本判据 rc=0**、表格自检 **TOTAL FLAGS 0**、锚点簿 records **1302** / **DA-d 在册 262、新增 0、消失 0** /
                **DA-e ID-MISS 286**（= 闸值，**未涨未降**）/ **DA-f 614 与 313**（≥578/≥277）；本轮台账**新写 0 个锚点**（㊷ ④ 那条纪律的第二次"根本不写"）。
                **残留**：`D-CE-1`（Ack 那一行**没有结构探针覆盖**，只有本轮那条具名行为用例钉得住它）；**追踪解析别名族**新挂账
                （`Repo` 现读 **108** 处多键调用、其中 **48** 处别名读法，追踪那一段占 **31** 处，`bizOrderNo` 平台三面 **0 生产者**而客户端还读 **1** 处）
                —— 要**逐 VO 出 verdict**，本轮一行没动，挂 #141/#159 那条线。
                **NEXT**：`#278`（后端可达性过滤要真 Maven 构建 ＋ 重新部署 ＋ 在线复验，才能声称"禁行区站点从选择面消失"）、`#140`、`#159`（含 `D-G3b-1` 那 **3** 处内联）、
                `#245`（mock 定位那一半）、`#269` 残留（网络层缺键回落的形状）、`#270`/`#273`/`#274`（设备轨道）。
CURRENT_ANDROID_STATUS:  #269（G-3b 寄/购计数诚实化）收工（2026-10-06 22:4x 判据现读 → 23:17:32 用例终值 → 23:18:28 设备闸门 → 2026-10-07 04:07 干净重打包；台账 D-063 / 审计 §82 / §八 ㊷）
                **口径**：两格的初值是"未取到"而不是 **0** —— 只有请求成功才写数，写的是后端分页总数那个**已经在树上的键**
                ⇒ 本轮 **0 处后端改动、0 个新接口**（两个 controller 的返回语句是逐行读出来的，不是调出来的）。
                **SOURCE_COMPLETE**：寄/购那一页收口；⚠ 同一条规则在"我的"页仍是 **3** 处手写内联 ⇒ 登记 `D-G3b-1` 挂 #159，**没顺手改**
                —— 那个文件在册锚点多、行号一漂就顶穿 DA-e 的 **286**，而顶穿之后能做的只有下调闸值 = 改窄闸门。
                **BUILD_VERIFIED = 三遍，不是"唯一采信遍"**：第一遍 `BUILD FAILED in 1m 10s`（新用例的具名实参照抄了邻近调用点的名字）→
                第二遍 `BUILD FAILED in 20s`（`:app:testDebugUnitTest FAILED`，同一遍仍产出了 23:16:04 那一包）→
                第三遍 `BUILD SUCCESSFUL in 23s`、**53 actionable tasks: 6 executed / 47 up-to-date**、rc=**0**；
                用例 **536 条 0 failures 0 errors 0 skipped**（**51** 个类、`TEST-*.xml` mtime **单值 23:17:32**；⚠ **536 = 在册 532 ＋ 本轮 4**）/
                lint **75** = Warning **46** ＋ Information **29**、**0 error**（与 ㊶ **同数** ⇒ 不构成回归；起稿时误抄成 **78/32**，那是 **㊵** 那一遍的数，已在 D-063 §7 ⑤ 登记）。
                **判据面**：DV 那本新增一族、整本 rc=**0**，配 **10** 条注入的负控整本 **70 ✔ / 0 △ / 0 ✘**；⚠ **本族只按页判** —— 全项目归零会把网络层那个"缺键才数本页"的合法回落一起判死。
                **APK**：盘上可测的是干净重打那一包 **113,928,596 B** @2026-10-07 04:07:01 / sha256 `06f562a4…`；
                增量那一包（23:16:04 / **114,516,464 B**）**已不在盘上 ⇒ 只登记、不再声称可复测**（读数只活在 `%TEMP%` 等于没留档，㊷ ⑪）。
                **包内字节账（本轮新学的口径）**：数据末与中央目录之间那段 **4,096 B** 是 **APK Signing Block**（内含 `APK Sig Block 42`）、**不是洞**
                ⇒ 旧稿"干净重打 hole_total = 0"是量错的；相邻两包 Σuncompressed **+29,100 / Σcompressed −73 B** 而文件差 **+587,868 B**
                ⇒ ㊶ ⑨ 那条"免费的确定性正控"**收窄到同一任务态**：包体字节数不当"改了/没改"的证据，改没改看 **entry 级差集（896 → 896、本轮改过 6 个 dex）＋
                `TREEV2 files=249 sha256=de7d9526…`**（㊶ 是 **248 / d6e7ea61…** ⇒ **＋1 新文件 / 0 删除**）。
                ⚠ 上面那串 TREEV2 是**构建时**的读数：落档之后又动了一本判据脚本的基线（SU-b **52 → 53**，被本轮新建的那本测试类顶红的，属"预期内的红"），
                现读 **249 / e1c1334c…** ⇒ **文件数没变、sha 变了**，而变处一半是表格自检**每次重写**的那份生成物 ⇒ **"这一包出自这一棵树"从此不按 TREEV2 的 sha 判**，
                只按 `app/src` 的 mtime 判（23:17:33 之后现读 **0 命中**）。逐条取证在 D-063 §9。
                **DEVICE_VERIFIED = BLOCKED**：设备闸门 rc=**1** ⇒ `DEVICE_UNAVAILABLE`、**0 台在环**，证据 `docs/device-evidence/20261006-231828`；
                48080 **0 监听** ⇒ 后端读数**全部来自读源码**、**0 次真机联调**。
                ⚠ **装了 Android Studio ≠ 设备在环**：本轮四次冷启 AVD 都没进系统（两种 `-gpu` 形态停在 QEMU 里、`-show-kernel` 那遍 exit **21**、限内存那遍 exit **137**），
                而包内 x86 / x86_64 的 `lib/` 只带 `libandroidx.graphics.path.so` ⇒ 高德的导航 `.so` 确实是 arm-only，x86_64 模拟器**永远走不到导航那一格**；
                设备轨道单开 **#270**，**`DEVICE_VERIFIED` 不因"装了 Studio"而改变状态**。
                **交付（2026-10-08 11:0x 收尾重跑）**：两遍 gradle **BUILD SUCCESSFUL in 1m 23s（11 executed / 42 up-to-date）** 与 **in 4s（1 executed / 52 up-to-date）**，
                用例 **51 类 / 536 例 / 0 / 0 / 0**（XML mtime 秒级单值 **11:07:04**）、lint **75** = Warning **46** ＋ Information **29**（xml 94,681 B @11:08:01，与 §6 那遍同字节同条数）、
                ⚠ **APK 未重打** —— `:app:assembleDebug` 报 **UP-TO-DATE**，交付仍是 **113,928,596 B / `06f562a4…`** @04:07:01 那一包；
                日志这次落在仓库内 `docs/build-evidence/20261008-closing.log`（㊷ ⑪ 的改法兑现）。逐条见 D-063 §10。
                **写后终检**：**23/23 本判据 rc=0**（其中 SU-b 那一格本轮先红后对，D-063 §9）、表格自检 **TOTAL FLAGS 0**、
                锚点簿 records **1302** / OK **1001** / **新增 0 / 消失 0 / ID-MISS 286（= 闸值、未涨未降）/ DA-f 614 与 313**；
                锚点纪律是**本轮 0 个新锚点**（非要写就紧跟一个"该窗口里真有的标识符"当见证，旁白另起一行）。
                **NEXT**：`#140`（口径已闭环、历史那一半仍未闭环）、`#159`（连同 `D-G3b-1` 那 **3** 处内联一起做）、`#245`（mock 定位那一半）、
                `#269` 残留（网络层缺键回落那一形状，本轮不判它）、`#270`（首个真 `DEVICE_*` 读数或如实登记 AVD 起不来的原因）。
CURRENT_ANDROID_STATUS:  G 批（G-1…G-4）收工（2026-10-06 16:0x 起手 → 20:56:25 APK 终值 → 21:03:34 设备闸门；台账 D-062 / 审计 §81 / §八 ㊶）
                **SOURCE_COMPLETE = 四格**：G-1 段坐标撤读四个三面 0 生产者的自造别名、改按后端真键的可空读法（缺键 = null，不再塌 0.0），
                DV-k 基线 **115 → 111**（收紧）＋ 判据簿 5 处字面锚与 DV-w 桥 **111 = 109 ＋ 2** 同步重锚；
                G-2 `DriverTask` 按后端 19 个真字段收（幻影键族 0 读取点、货名不再显示、窗口不许回落 `createTime`），并在 `WorkbenchScreen.kt:710-768`
                画出"今日运营任务"（同站多动作由 `DriverTaskSegment.group` 聚一条，空态那句写"任务段接口未给"）；
                G-3 演示数据 debug/release 都默认 **false**，5 条红**全部在 test 源码里修**（用例内显式开闸 ＋ 类级复位钩子），
                **闸值、闸门读点、`wrap()` 的抛错一字未动**；G-4 页面容器 26 个调用点里 **24** 个并到 12.dp（⚠ **0 判据**，只到"改过"）。
                **BUILD_VERIFIED**（唯一采信遍）：rc=**0** / `BUILD SUCCESSFUL in 1m 32s` / **`53 actionable tasks: 53 executed`** /
                用例 **532 条 0 failures 0 errors 0 skipped**（**50** 个类，`TEST-*.xml` mtime 20:56:34）/ lint **75** = Warning **46** ＋ Information **29**、**0 error**。
                **判据面**：**23/23 rc=0**；DV-k 现读 **111**、census 口径桥 **111 = 109 行读法 ＋ 2 行函数声明**；V2.1 核对表 **119** 行 rc=0；表格自检 **TOTAL FLAGS 0**。
                **APK**：**113,928,596 B** @2026-10-06 20:56:25.897 / sha256 `571c3cbaecae15c59e6bf83c696a032e6f30b7e73279572c4c717d151eb9a44f`；
                与 20:46:13 那一包**逐字节同、sha256 同**（20:5x 之后只动了 test 源码 ⇒ 免费的确定性正控）；
                `TREEV2 files=248 sha256=d6e7ea61…`，改前 `07cff2c1…`、**同文件数 ⇒ 本轮 0 新增文件 / 0 删除文件**（无 git，增删都不可回滚，所以这条只报"没有"）。
                **DEVICE_VERIFIED = BLOCKED**：设备闸门跑了，rc=**1** ⇒ `DEVICE_UNAVAILABLE`、**0 台在环**，证据 `docs/device-evidence/20261006-210334`；
                48080 未监听 ⇒ 本轮后端读数**全部来自读源码**（后端 VO 的字段是逐条数出来的），**0 处后端改动、0 次真机联调**。
                ⚠ **本轮四条自伤全部登记**（前三条在 D-062 §7，第四条在 D-062 §9 ＋ 审计 §81 ④ ＋ ㊶ ⑪）：① 并发把就地注入的负控簿再起一次 ⇒ **8 条假红**（批停没停只按 driver PID 判）；
                ② gradle 的 JVM 参数没整体加引号被拆词 ⇒ `Unknown command-line option '-X'`（第三次）；③ GBK 乱码把打印里的"小于等于"读成"大于等于"，
                差点去改一台没坏的机器 ⇒ 闸值/判据的原文只能来自脚本文件本身（㊶ ⑧）。
                ④ **落档那一步自己把锚点账写红了**：㊶ 写后锚点簿首跑 rc=**1**（新增 **5** 个不在册 aid、ID-MISS **293** > 闸值 **286**）⇒ 每条新锚点后面补一个**它自己窗口里真有的标识符**当见证、
                旁白另起一行；**没动 286 那道闸、没重抄在册表**，复跑 rc=**0**（逐条读数与修法只记在 D-062 §9）。
                **写后终检**：收尾一整遍 **25 项 / FAILS=0**，逐本 rc 只记在 D-062 §9，本块不复述；APK **未重打** —— 那之后动的全是 `.md`，包体仍是上面那一包。
                **NEXT**：队列仍是"已走完 ＋ 在册旧账"。本轮新增挂账 5 条（§九）：`D-G4-1`（间距无判据）、`D-G3-1`（五格只在演示形态下被量）、
                `D-U1-1`（未路由的那份重复设置页，无 git 不删）、`D-G1-1`（折线与坐标两族混名未撤）、`D-G2-1`（别的模型里同名幻影键）；
                旧账：`#269`（寄件页计数诚实化）、`#140`（APK 历史增量归因）、`#159`（逐字段生产者证据的内容级重锚）、`#245`（mock 定位那一半）。

CURRENT_ANDROID_STATUS:  赛道⑯（N-8）收工（2026-10-06 14:0x 现状复核 → 15:48:31 唯一采信的那遍终值链 → 15:55:12 APK 终值；台账分次落档（DECISION_LOG D-061 → 本簿 → CONTEXT_LOCK ㊵/§九 → 核对表 V21-08 复测格），⚠ 写后终检的逐本 rc 只记在 D-061 §10，本块不复述那批读数以免同一读数出现两个出处）；⚠ **本块不用 mtime 当改动面证据** —— 本轮跑过两本负控（结构簿整本 131 条、行为簿 39 条），被注入过又复原的那两个文件的 mtime 全落进窗口（㊱ ⑧ 第四次兑现）；`D-N8-1` 登记的正是"起手没打改前整树指纹"，改动面本轮只有**字面量前后对照**（改前原文逐字留在负控簿的注入串里）＋ 判据目标值前后（用例 52→55、NAC 族 0 行→5 行）

**SOURCE_COMPLETE = ⑯ 那三格已收**：车道/播报两格的开关话术收进纯层唯一出口 `guidanceSwitchOffText(switchName, detail)`（骨架在纯层 **1**、页面调用点 **2**、页面自己拼 **0**）；车道那句点名改成「我的」里真有的那一行（**车道级指引**，旧句指的"车道引导"只存在于旧调试屏）；播报标签回到静态词（`"播报" + (` 生效 **0**）；四行各走自己那一句（**4**）。**0 个新文件 / 0 本新用例簿 / 未新增任何一格 UI / 未动任何一处调用点线程**（⑯ 的三条授权边界本轮全在）
**BUILD_VERIFIED**（唯一采信遍）：`:app:testDebugUnitTest --rerun-tasks` rc=0、`BUILD SUCCESSFUL in 38s`、**`24 actionable tasks: 24 executed`**、XML **49 类 / 522 例 / 0 failures / 0 errors / 0 skipped**、49 份 XML 的 mtime 集合 = **单值 15:49:21**；⚠ **522 = D-059 的 519 ＋ 本轮 3**（三条新例全在判据层那一本，55 例那本）；lint rc=0 / **`28 actionable tasks: 28 executed`** / 报告 98,754 B / **issue 78（Error 0 / Warning 46 / Information 32）**
**判据面**：`check_navigation_probes.sh` rc=0，N-a **55（≥55）**；N-ac 一族 **5 行 / 11 个计数器**现读 `1/2/1/0`、`0/0`、`4/1/1`、`0/0`（四行各自出口、两条开关各自点名「我的」那一行）；判据簿 **23 本全 rc=0**（`notify_result` / `oss_credits` 两本批内 rc=2 → 终值后单本补跑 rc=0，两行都写不合并）；census / 表格自检 / 文档锚点探针各 rc=0
**负控面**：结构 **7 条**（N-ac1…N-ac7）＋ 行为 **3 条**（M34…M36）**各红各的具名用例、互不重叠**（M34→`开关关掉那一格不替高德宣告给过什么`、M35→`开关名照抄设置页那一行不指错开关`、M36→`两条开关句不互相套用对方的机制`）；行为簿全批 **39 ✔ / 0 △ / 0 ✘**、基线与恢复后复跑同为 `exit=0 / failures=0/55`、恢复核对"与改前基线逐字节相同"；结构簿整本重跑 **131 ✔ / 0 △ / 0 ✘** rc=0（上一窗口那条 △ 查明是**簿子自己的 `ctl` 串与判据 × 句不同串**，判据没抓错，已换成 × 原文前缀并写明理由）
                ⚠ **本轮三次自伤全部登记**（都写进 D-061 §7，因为它们会重演）：① `-Dorg.gradle.jvmargs` 未加引号被 shell 拆词 ⇒ 三步 gradle 各 rc=1、报 `Unknown command-line option '-X'`（**调用方式错，不是构建红**，㊴ ② 同族）；② **同一脚本被重复后台起了一次**（第三次踩），两条链写同一批日志 ⇒ 两遍全作废、其中还混进一条无法归因的 `BUILD FAILED / 16 actionable tasks: 16 executed`；③ 两处**量具错报**：lint `b.count(b'<issue>')` 读出假 0（标签带属性，正确式子 `<issue[\s>]` ⇒ 78），`N-a` 那行打印写 `≥52` 而闸值早是 `-ge 55`（只改打印字面，不动计数器与 × 句 ⇒ 修前的负控读数仍有效）
                **APK**：`assembleDebug --rerun-tasks` rc=0 / **`37 actionable tasks: 37 executed`** / **113,928,596 B** @2026-10-06 15:55:06 / sha256 `5dca59c79a03d471962fbc55dc2086f75e766523190bb56f003ba1381681f21d`；
                对 D-059 那一包（**113,928,596 B** / sha256 `3bfa396a3a7069adf4546c22557f50adf9b7e776bc184559df81a8ca0e0afee8`）本轮第一次做到**条目级差量**（`zipfile` 逐条比 `file_size` 与 `CRC`）：条目 **896 → 896**（无增无减）、变化的条目只有 **2** 个 —— `classes3.dex` 未压缩 708,088 → 708,144（**+56**）、`classes7.dex` 78,196 → 78,448（**+252**）、未压缩合计 **+308 B**，而**压缩后总长两包相同**（⚠ "一样大"绝不读成"包没变"，㊳ ⑧ 第三次兑现）；
                dex 类清单新旧各 **16 个 dex / 1,383 个 App 类**、**集合差 入 0 / 出 0**（两份清单 md5 同为 `422d5a531abce287bf68053e32ef3918`）⇒ 与"0 新文件、UI 未扩"互相印证；⚠ 交错那两遍留下一枚 **114,235,306 B** @15:43:50 的包（比终值大 **306,710 B**）**来源不可归因，登记 `D-N8-3` 并作废**，`#140` 的历史那一半仍不闭环
                **DEVICE_VERIFIED = BLOCKED**（设备闸门本轮**跑了**：rc=**1** ⇒ `DEVICE_UNAVAILABLE`，**0 台在环**，adb 原文 `adb.exe: no devices/emulators found`，原文落在 `docs/device-evidence/20261006-160924/`）⇒ 这一格是「本轮实测：无设备」，既不是「本轮未测」也不是复用旧读数；排版那一族（字号 / 行距 / `maxLines` 截断 / 长句换行）**一条断言都没写**，全部 `NOT VERIFIED ON DEVICE`；release 仍 `BLOCKED`（HTTPS 闸门，未绕）；GIT 仍 `ENVIRONMENT_BLOCKED`（未 `git init`）
                **本轮不做（三条，各写代价）**：① `laneGuidance` **不接进引擎**（接了就是行为变化：车道帧从此受本机开关影响；⑯ 只授权统一话术）⇒ 登记 `D-N8-2`；② 转向那行的"表外"句子**不再包一层出口**（D-055 定的是码表返回 `null`、由页面明说"不在这张表里"，并进开关出口会把两种成因说成一件）；③ 无路线三态**不加第四格**（`D-N6-1` 那条离线几何的决定不属于 UI 细节，别混着做）
                **NEXT**：**队列已走完**（④→⑯，⑯ 是最后一个）。剩下的是在册旧账与三态里仍未到位的部分：**`#140`**（APK 体积归因，本轮把"哪两个条目涨多少"补上了，还缺同输入重打那一半）、**`#159`**（CONTRACT_EVIDENCE §七 的 67 条 App 锚点内容级重锚）、**`#245`**（V21-15 mock 定位那一半，`isFromMockProvider` 全 main **0** 读取点）、`D-N6-1`…`D-N6-5`、`D-N8-1`…`D-N8-3`、`D-⑪-1/-2`、`D-⑧-1/-2`、`D-⑱-1/-2`；**DEVICE / RELEASE / GIT 三项不因本轮而改变状态**
CURRENT_ANDROID_STATUS:  赛道⑮（N-7）MapLibre / Valhalla 备用 = **取证完成、verdict `ANDROID_N7_⑮_BLOCKED`**（2026-10-06 11:4x 起手取证 → 11:5x gradle 终读；台账 D-060 → 本簿 → CONTEXT_LOCK ㊴ → 许可证 §5 侧记，写后终检的逐本 rc 只记在 D-060 §10）；⚠ **本轮改动面 = 0 个源文件 / 0 行第三方代码 / 0 个新依赖 / 0 条许可证新增 / 构建脚本一字未动**，证据由构建系统盖章：`BUILD SUCCESSFUL in 5s`、**43 actionable tasks: 43 up-to-date（executed = 0）**、APK **113,928,596 B @2026-10-06 10:49 / sha256 前缀 `3bfa396a3a7069ad`** 与 D-059 终值**同串**
                **SOURCE_COMPLETE = 不适用（本轮不写代码）**；买到的是一格**决定性读数**：挡路的不是镜像、不是许可证、不是仓库闸门，也不是 duplicate class，而是
                  ① **本工程的 Kotlin 2.0.21 读不了这两个构件**（真跑编译器，不是推断）：
                     `java -cp kotlin-compiler-embeddable-2.0.21.jar;… K2JVMCompiler -no-stdlib -cp <stdlib;android-35.jar;构件 classes.jar>` ⇒
                     `error: incompatible classes were found in dependencies … use '-Xskip-metadata-version-check' to suppress errors` /
                     `class 'org.maplibre.android.camera.CameraPosition' … actual metadata version is 2.2.0, but the compiler version 2.0.0 can read versions up to 2.1.0` / **GL_RC=1**；
                     nav 侧 `metadata is 2.4.0` ＋ `unresolved reference 'JsonKt'` ×2 / **NAV_RC=1**。唯一压制开关属于**禁止的那一类"放宽校验"** ⇒ 不加。
                     ⚠ 同一台编译器前两次 `NoClassDefFoundError`（缺 stdlib、缺 coroutines）是**我的调用方式错**，跑通之前的读数一律不作证据（㊴ ②）。
                  ② **构件确实拉得到**（所以"备用"不是被网络挡住的）：aliyun public 镜像本轮点名 19 条路径重试遍 **19/19 HTTP 200**；
                     Central 侧 `android-sdk` **107** 版（9.2.1→**13.6.1**，lastUpdated 20260929）、`navigation-core-android` **16** 版（5.0.0…5.0.0-pre14，20260826）；
                     ⚠ `navigation-core-android-5.0.0.aar` 首探 **404**、随后 **3×200** ⇒ 记**镜像抖动**两个方向都不写死。minSdk 两侧都 **23**（工程 **26** ⇒ 不抬高）。
                  ③ **duplicate class 这一格答案是"不撞"**：gl **534** ＋ nav **228** 类全在 `org/maplibre/`，高德 **1,855** 类（`com/amap` 1,363 ＋ `com/autonavi` 459 ＋ `com/alibaba` 33）⇒ **python 集合差 0**；
                     soname 也无同名（`libmaplibre.so` × 4 ABI vs 高德 6 个 `.so`）。⚠ N-i（`navi-3dmap` 顶替 `3dmap`）**维持 1/0/0 未动**，两问不互用。
                  ④ **"备用引擎"在结构上是封死的，不是"接上就行"**（三处现读）：`NavigationEngines.create(context)` 无参数无注入点（UI 调用点 **1**）；
                     判据 **N-f 目标 1/1/2** 把 `NavigationEngine` 实现数钉成恰好 2；`naviRouteCheck()` 进闸门时 **`sourceRaw = RouteSource.AMAP.name` 是写死的字面量**（全 main 该串 **1** 处，生产调用点 **1**、用例 **6**）
                     ⇒ 第二个引擎要么把几何**署名成高德路网**（正是 `RealRoadRouteValidator` 要防的那种不实），要么另立来源；而改闸门目标 = 放宽校验，本轮禁止。
                  ⑤ **两侧白名单对称、但没有生产者**：Android `RouteSource.VALHALLA(real=true)` 与后端 `RealRoadProviders.VALHALLA` 都在（**不是缺项**），
                     但 `yudao-module-transport/src/main` 里 `RealRoadProviders.VALHALLA` 引用 **0** 处（对照 `AMAP` **5**、`MIXED` **3**），`setRouteProvider(` 7 处实写值为 `"AMAP"`/`"EUCLIDEAN_FALLBACK"`/`"none"`/`"SCHEDULE"` 或透传 ⇒
                     **标签通道"允许"，服务端"从不发"**。后端 VO 也没有 `graphVersion`／路网版本（D-059 已证 0 生产者）⇒ 换任何引擎，"这条线用哪版图算的"无人可证。
                  ⑥ **台账 §5 那句"第一阶段只留 `OfflineRoutingEngine` 接口"从未落地，本轮也不补写**：全仓 grep **3** 处全在 `THIRD_PARTY_NOTICES.md` 与其两份备份，`app/docs/tools` **0** 处 ⇒ 记 `PRODUCER_NOT_FOUND`（㊴ ⑦）。
                **三方对照（本轮不写代码，只记"这一格三方各是什么"）**：小程序 = **N/A**（无端上 routing 引擎，路线只从后端 `routeProvider` 来）；
                后端 = 白名单收 VALHALLA 但 **0 生产者**、VO 无版本凭证；Android = 唯一闸门 `RealRoadRouteValidator`，来源非 `real` 即 `Rejected(reason)`，页面"路线不可用 + 原因 + 重试"。
                ⚠ **仍未知 / 未测出**：GraphHopper 的 Central 检索**两次超时（curl exit 28），一次都没成功** ⇒ 该格写"没量到"，不写"没有"也不写"有"；
                `android-sdk-opengl` 那个变体的清单**没下载**（Vulkan-required 是否有旁路未验）；AAR 内 `.so` 的真实体积压缩后贡献、以及 `libmaplibre.so` 在真机上的可用性均未测。
                **解锁条件（BLOCKED 要写缺什么，按顺序四件）**：① Kotlin 2.0.21 → **≥2.2 / ≥2.4**（同轮带 KGP＋compose 插件＋AGP 兼容矩阵，并全量重跑 **49 类 / 519 例**）——这是**工具链升级**，独立一轮的工程量；
                ② 体积预算：nav 5.0.0 的 pom 把 gl 钉在 13.5.0（**197,373,956 B**，vs 13.6.1 = 18,451,147 B，差 **10.7 倍**）＋ 4 ABI `libmaplibre.so` raw ≈ **48.9 MB** 且**无 abiFilters**；
                ③ `android.hardware.vulkan.version` **required="true"** 会是本 App 合并清单里**第一个过滤机型的特性**（现 3 条全 `required="false"`），而 **0 台真机在场**；
                ④ Valhalla 在 Maven Central **无路由构件**（numFound=2 都无关，`org/com/io` 三前缀 404）⇒ "设备端 Valhalla"意味着自带 NDK 原生构建＋离线数据，本轮不猜。
                **NEXT**：**下一轮 = 赛道⑯ UI 细节（队列 #219，最后做，不扩 UI）**。⚠ 赛道⑮ **不并入**任何"顺手做"：它要求的是工具链升级那一轮，与 UI 那一轮没有交集。
                  开工前仍需算的一笔旧账（⑭ 遗留，未因本轮变化）：**`D-N6-1`（把离线几何接进任务地图会给 F-3 ⑧ 的"无路线三态"加第四格，`MR`/`MV` 两族判据要同轮重锚）是"接不接"的决定，不属于 UI 细节**，别混着做。
                  常驻未闭环：#140（APK 历史增量归因；本轮新增一格"executed = 0 且 sha256 同串"可作为"0 改动"的旁证，但历史那一半仍未闭环）、
                  #159（CONTRACT_EVIDENCE §七 67 条 App 锚点内容级重锚）、#245（V21-15 mock 定位那一半 `isFromMockProvider` 全 main **0** 读取点）、
                  `DEVICE_VERIFIED`（0 台在场）／`RELEASE`（HTTPS 闸门保持）／`GIT=ENVIRONMENT_BLOCKED`。
CURRENT_ANDROID_STATUS:  赛道⑭（N-6）收工（2026-10-06 07:52 第一份 ⑭ 侧现读判据 → 10:59:29 终值链结束；台账分次落档（DECISION_LOG D-059 → 本簿 → CONTEXT_LOCK ㊳/§九 → 核对表 → 许可证），写后终检的逐本 rc 只记在 D-059 §10，⚠ 本块不复述那批读数以免同一读数出现两个出处）；⚠ **本块不用 mtime 当改动面证据** —— 本轮跑过负控批 **08:36:59 → 10:41:15**，`app/src` 一批文件的 mtime 落进窗口（㊱ ⑧ 第三次兑现）；改动面证据只有字节数 / 内容指纹 / `cmp`：整树指纹 **files=166 sha256=3c338019…** 在 10:46:44 与 10:59:29 **两遍同串**（补跑之后复量，两本读测试产物的负控没在真身留一个字节）
                **SOURCE_COMPLETE**：本轮买的是**"缓存的第一问不是'存多久'而是'有没有人读'——读点一出现，挡 TTL／线程安全／过期不交几何的那四件就同时失效，于是四件一起做；而'不允许返回过期路线'只有落在类型上才算做到"**；UI 未扩（**无新按钮 / 无新屏 / 无新路由**，披露文本走 `One.error` 既有通路）。
                  ① **契约重读把本轮自己那句"不做"否掉了**：后端 `AppDriverRouteRespVO` 本轮**逐字段**重读（不是引用上一轮台账）——**没有 `graphVersion`、没有 origin/waypoints/destination/profile 那组指纹入参、没有任何新鲜度／有效期键** ⇒ 指令 §十三 点名的两件里只有"provider ＋ 抓取时间"有可信生产者。⚠ **F-3 ⑰ 那句"只写不读"本轮当场失效**：`N-x 缓存读点/唯一写入点` 由 **0/1** 变 **1/1**，判据目标同轮改成"⑭ 起目标 1/1"——**这是同轮改判据不是漏改**（留着旧目标就是把一个正确现状判成破口），理由连"读点出现"一起写进脚本；旧行"给没人读的表加过期时间＝把'还没接上'登记成'已经安全'"**不撤销**（它当时是对的），但**不再适用**。
                  ② **同一个数据位可以有两个形状，两个都得留**：后端 `routeProvider` 给的是**原文**（实测样例 `"gaode"`），`RouteSource` 是**归一后的枚举**（"gaode" / "AMAP" / "高德" 都塌成同一个 `AMAP`）——判"这是不是真实道路"要枚举，回执"后端当时说了什么"要原文，**用枚举顶替原文＝替后端补了它没说过的话**（与转向码／播报那一族同形）⇒ 缓存条目里 `route.source` 与 `providerRaw` 并排，`N-ab 来源原文照抄/改写成本机枚举 = 1/0`。
                  ③ **形状（结构上拿不到，而不是约定上别拿）**：`RouteCacheRead = Hit(entry, ageMillis) / Absent / Stale(providerRaw, fetchedAtMillis, ageMillis)` 三态各自独立不许并格，⚠ **`Stale` 故意不带几何字段**（`N-ab 过期那一格的类型数/格内带几何 = 1/0`）⇒ 调用方**拿不到**过期几何，不靠自觉；**一把读出口** `cached(key, nowMillis)`（旧写法那三把里"无状态 Date"与"整表快照"两把本轮**没建**，它们会把"过期了"与"本机压根没有"并成同一个 `null`）；TTL `MAX_AGE_MILLIS` = 30 分钟且 KDoc 明写**这个数值没有外部权威来源**（本机自定口径 ⇒ 台账与用例都不许写成"路网数据的有效期"），边界是"当前时间 ≥ 过期时间即过期"、**相等不给情面**；抓取时间只来自**写入那一次的时钟入参**（B-5 一注入就红 ⇒ 时钟入参不是为了省一次取时，是为了让 30 分钟这条边界第一次进得了可测范围）；**否决先于写入**（`resolve` 收 `serverAvailable` 入参，来源不可信／几何不过／后端自己说这一班没有真实道路**三种不过都删旧条目**）⇒ 把 `D-⑱-3` 那个"校验器认可就先写进缓存"的潜在缺口关掉，Repo 侧"取 `serverAvailable` 必须排在 `resolve` 之前"是**行号比出来的先后顺序**判据；并发按指令两个优先项之一做 = `ConcurrentHashMap` ＋ `AtomicLong` 写入序号（不选 `Mutex` 的理由进 KDoc：它会把一次缓存读变成挂起函数、逼调用链一起挂起；⚠ 换容器同时失去插入序 ⇒ "最旧"必须有显式定义，所以淘汰与两参 `remove(key, entry)` 是**同轮**改的，不是两步）；披露唯一出口 `routeFailureTextOf` 三格各一句、**不供几何**（`N-ab 披露读点/交出几何/三格各自一句/并句措辞 = 1/0/3/0`），`Absent` 那格**原样返回失败原因**（缓存空着却说"本机存过一条……"就是编造），时长口径三档 ＋ 负数先夹 0，且**不抄** F-3 ⑧ 那句"这里不画任何示意线"（无路线话术的唯一出口在别处，抄过来就是同一句话两个生产者）。
                  ④ ⚠ **没做的东西要说清是哪几件**：本轮**没有**撤任何函数；"不做"的五件是 `graphVersion`（三面 0 生产者，`N-ab graphVersion 生效行/哈希随机数/披露调用点 = 0/0/1` 钉住）、canonical fingerprint（那五个入参没有一个有生产者；哈希／随机数在缓存与披露两侧 = 0，与 `N-w` 同族：**指纹的可信度来自被测对象，不来自长度**）、`NavigationSessionStore`（V21-14 仍 ❌，**旧理由"读方为 0"本轮失效、换成"为一个只披露不画线的读点造第二个存储，拆出来那份仍然不能供几何"**）、离线几何接进任务地图（登记 `D-N6-1`）、写磁盘／DataStore（指令那一问的对象就是内存缓存，进程重启后是 `Absent`）。
                **BUILD_VERIFIED**（终值 = 负控之后的最后一遍，不是最好看的那一遍）：debug variant **49 个类 / 519 例 / 0 failures / 0 errors / 0 skipped**（接力脚本 `after_negctl_run_full_tests.sh` **10:41:16 → 10:42:03 rc=0**、`--rerun-tasks` ⇒ `BUILD SUCCESSFUL in 45s` / **24 actionable tasks: 24 executed**，**49 份 XML 的 mtime 集合唯一 `['10:41:59']`** ⇒ 整批是同一次跑出来的、没有旧产物混进来）；逐本现读 `RouteOfflineCacheContractTest` **7/0/0/0**、`RealRoadRouteValidatorTest` **18/0/0/0**，对照 `NavigationContractTest` **52/0/0/0**、`Map3DVisualContractTest` **16/0/0/0**；⚠ **519 = 上一轮 510 ＋7（新簿）＋2（旧本新增两条）**，两个来源独立对上 `SU-c` 现读的 **519**（不是回忆）。
                  ⚠ **release variant 未跑**（`D-⑱-1` 那 **5** 例红的成因仍在，本轮只跑 debug），`assembleRelease` 另被 HTTPS 闸在配置期挡住：**rc=1 @10:49:18**，原话逐字 = `release 包必须使用 HTTPS（当前 http://1.15.29.107/api）。内网测试请显式加 -Pbuspost.allowInsecureRelease=true。`（`BUILD FAILED in 4s`），**没有**传那个属性去绕 ⇒ 两件事不许并成一句"release 也好了"。
                  **lint**：`lintDebug --rerun-tasks` 10:46:45 → 10:48:25 **rc=0** ⇒ `lint-results-debug.xml` **98,754 B**、`<issue>` **78** = Warning **46** ＋ Information **32**、Error **0**。⚠ 这组字节数与 D-058 那份**完全同值**（本轮动的是 `core/map` 与 `data/repo` 两族，没新增 lint 可见的形状）——"同值"是读数，**不是**"所以没回归"的结论。
                  **打包**：APK **113,928,596 B @10-06 10:49:09 / sha256 `3bfa396a3a7069adf4546c22557f50adf9b7e776bc184559df81a8ca0e0afee8`**，对本轮起手前 `cp -p` 留档的 D-058 包（**113,928,596 B** / `96c7849c6da30d7e70db9a6fa4edb9a3ae69b98f53698697b3202b35852d7958`）**Δ = +0 而 sha256 变了**（⚠ **连续第 5 个未归因读数**：⑨ ＋16,384 → ⑩ ＋0 而 sha 变 → ⑪ ＋33,688 → ⑫⑬ −17,304 → 本轮 ＋0 而 sha 变；`#140` / `D-⑧-1` 同族，不做差量实验就不写归因；⚠ "一样大"**不许**读成"包没变"，也不许读成优化）。本轮**先把上一轮的包留档再打新包**——那正是 `#140` 历史那一半曾经丢过的原因。
                  **dex 类清单差**（同一口径：全类名描述符、python **集合差**）：旧包 16 dex / defined **1,378** / 清单 md5 `513f615714c1eca9f0822a7e8b1ca882`，新包 16 dex / defined **1,383** / md5 `422d5a531abce287bf68053e32ef3918` ⇒ **入 6 / 出 1**；入榜逐条具名 `core/map/RouteCacheEntry`、`core/map/RouteCacheRead` 与 `RouteCacheRead$Absent` / `$Hit` / `$Stale`、`data/repo/OfflineRouteDisclosureKt`；出榜具名 `core/map/RouteRepository$CachedRoute`。⚠ **出榜那一条正是被三态顶掉的那一格** ⇒ 这是这一族里**唯一一条被包内事实独立佐证的形状主张**（前三轮同口径都是"出 0"，本轮首次出榜非 0）。
                  **判据面**：`ls tools/check_*.sh` = **23** 本（其中 `check_*probes.sh` **22** 本），终值树逐本 **rc=0（23/23，无一本非 0）**；`python tools/check_ledger_tables.py` rc=**0** / **TOTAL FLAGS 0**（那是写台账**之前**的一次）；`bash tools/v21_requirement_census.sh` rc=**0** ⇒ `V21-13` 侧 `RouteCacheEntry 4/1/4 · expiresAt 4/1/3 · MAX_ENTRIES 2/1/2 · ConcurrentHashMap 8/2/6 · @Synchronized 0/0/0 · Mutex 7/2/3 · LinkedHashMap 2/1/1 · graphVersion 3/2/0`，`V21-14 NavigationSessionStore 0/0/0`，`V21-15 isFromMockProvider 0/0/0 · fun heading 2/2/2`；`check_authenticity_counts.sh` rc=**0** ⇒ **窄 10 / 宽 49 / `GeoMath` 27 / `DemoData` 文件 6 / 假步进 0**，宽口径按文件构成 **18 行加总正好 49**（`ParcelScreen 7 / WorkbenchScreen 5 / MissionMarkers 5 / DriverNavScreen 4 / Repo 3 / PolylineDecision 3 / NavigationEngine 3 / MapCanvas 3 / HomeScreen 3 / BusScreen 3 / AmapNavigationEngine 2 / AlertEngine 2 / TraceView 1 / SendScreen 1 / Models 1 / DriverSdkNavCard 1 / DriverMissionMap 1 / BusMap 1`）——**五个绝对值与 D-056 / D-057 在册那两遍逐项相同**（窄 10 / 宽 49 / `GeoMath` 27 / `DemoData` 文件 6 / 假步进 0）⇒ 本轮**没有开新的直线展示出口**（本轮那两个新文件在构成里**根本不出现**）。⚠ **构成表的对照只到"点名的格子"**：D-056 / D-057 那两遍只把 `NavigationEngine` 判据层 **3** / SDK 层 **2** 两处点名在册，**完整 18 行构成最近的一份是 F-3 ⑤ 那遍的 48 行表**，两者差项实测 = `NavigationEngine 2 → 3`（转向／车道两张表的注释行，⑨ 那一轮加的）；⚠ **另一笔断链如实登记**：**D-058（⑫⑬）那一遍没有留这四个数** ⇒ "⑫⑬ 有没有变"归不出来，只能说"⑭ 与 ⑩⑪ 同串"。
                  **判据扩建（结构 NAB1…NAB24 / 9 行打印 ＋ 同轮两处基数）**：终值逐行实测都等于脚本目标——`N-x 1/1`（目标本轮由 `0/1` 改）、`读出口一把/已删的旧出口 = 1/0`、`TTL 常量/过期边界/抓取来自入参/现场取时 = 1/1/1/0`、`并发容器出现数/旧 LinkedHashMap 生效行 = 4/0`、`来源原文照抄/改写成本机枚举 = 1/0`、`过期那一格的类型数/格内带几何 = 1/0`、`否决先于写入：判定/传参/行序 = 1/1/1`、`过期两参删除/越界按序号淘汰 = 1/1`、`披露读点/交出几何/三格各自一句/并句措辞 = 1/0/3/0`、`graphVersion 生效行/哈希随机数/披露调用点 = 0/0/1`；`SU-b` 源集文件数 **50 → 51**（唯一增量＝本轮那本新用例文件，理由连"现读 **7** 条 `@Test`、`SU-a` 那行 49/**0** 是反证"一起写进脚本），两个下限 `≥47` / `≥476` **一个字没抬**；终值 `SU-a 49/0 · SU-b 51/2 · SU-c 0/519 · SU-d 7/3 · SU-e 10`。
                  **负控面（负控在前、终值在后）**：全批 **27 本**，窗口 **08:36:59 → 10:41:15**，聚合按逐本 27 行现读加总 **✔=632 / ✘=2 / △=18 / ◦=22**；逐本 rc **25 本 = 0**、**2 本 = 2**（`notify_result` / `oss_credits`，末行原话 `✘ 真身没有 app/build/test-results/testDebugUnitTest/TE…`）——⚠ 与 D-058 **同族的第二次发生**：批内任何一本 `--tests` 过滤都会清空那目录，**这是前置未满足不是破口**；终值全量（10:42:03）**之后**各单本补跑 **rc=0**：`notify_result` 10:52:30→10:53:34（末行前一行原话 `SUMMARY|book=notify_result|破口=12 反向=2 预检=2|命中=16 失效=0`，末行 `RESULT=PASS`）、`oss_credits` →10:59:28（`SUMMARY|book=oss_credits|破口=13 反向=2 预检=4|命中=19 失效=0`，末行 `RESULT=PASS`）——**"批内 rc=2 ＋ 补跑 rc=0"两行都写，不合并成"全批 rc=0"**。⚠ **批次计数口径**：上一轮 26 本、本轮 **27** 本，增量就是那本新落盘的行为负控（glob 在批次起跑前已命中它，与接力脚本 `END_MARK` 同一把 glob）。本族行为簿（**270 行 / 16,790 B / sha256 前缀 `4e9b373ff9fa9a0e` / CR=0**）**B-1…B-10 十条**：单跑 `[行为簿 rc=0] 08:35:09` 与批内 **10:13:17 → 10:18:52**（336s）**同一组读数 `10 ✔ / 0 ✘ / 5 △ / 5 ◦`**、末行 `RESULT=PASS`；预检现读 `锚点与具名用例各恰好命中 1 次的条数 = 10 / 10`。
                **本轮自己付的账 ＝ 五笔（前四笔是量具与工具，第五笔是账本自己的编号）**：① ⚠⚠ **一笔假警报来自我自己的比较器**：链上 `[10/10]` 打印"指纹与补跑前不同串——必须逐文件找出差异"，`od -c` 现读两份文件行尾分别是 `\r\n` 与 `\n`（同一份 python 输出，先前那次用 `>` 重定向、这次用 `>>` 追加，Windows 文本模式翻译换行符），`tr -d '\r'` 之后**逐字同串** ⇒ "补跑没动过真身"这个结论成立，但它是**修好量具之后**才成立的（链上那一格已改成先 `tr -d '\r'` 再比）。⚠ 假警报比没有警报更贵——它会在台账里留下一句"仓库变了"的假话。② ⚠ **census 的 `code=` 那一列把单行 `/** … */` 算成生效行**：census 给 `LinkedHashMap hits=2/files=1/code=1`，而 `RouteRepository.kt` 里那两处**全在注释里** ⇒ 真实生效行 **0**（判据簿另立一把更严的注释过滤器，给的正是 `4/0`）；⇒ 台账规则定为**这一族"生效行"以判据簿为准、census 那一列同时抄并标明它偏高 1**（`MAX_ENTRIES 2/1/2` 与 `ConcurrentHashMap 8/2/6` 因同一盲区也要减看）。本轮**没改 census**（一改就要同轮重跑全部依赖它的台账行）。③ ⚠ **同字节数 ≠ 同内容**：上一窗口笔记里两处 sha16（那本用例簿 `001ab38c4a919e92`、卫生判据簿 `e803fc61a05ff929`）与现读（`21ee4709b636fbba`、`468acf79c8b3236c`）**不同**，而两份的**行数与字节数完全相同**——是"改到位之后长度恰好没变"，不是"数错了"；处置：**台账只写现读，笔记里那两个值不采信、也不并列成"两个来源"**。④ **一笔闸门叫不应的漂移**：V21-13 那格旧写法把两处声明锚成行号，⑰ 之后漂过一次、本轮之后又在别的行，而锚点探针对那一格**只判"在范围内"** ⇒ **它不会叫**；本轮**撤掉**那两处行号锚、位置证据交给判据簿的整行字面量（与 D-058 撤 V21-15 锚同一处置），`#159` 的内容级重锚照旧欠着；⚠ 本轮**没有新写行号锚**，所以漂移面没有变大。⑤ ⚠ **在册编号轴撞名（本轮改名自纠）**：起手把本轮四件写成 `D-⑭-2`…`D-⑭-5`，与 **F-3 的第 ⑭ 项**（D-053 写的 `D-⑭-1`）排成同族——两套编号轴各有一个"⑭"（F-3 的项序 vs 路线图赛道序），而 `D-⑭-1` 讲的正是**同一个缓存**的 TTL 半边 ⇒ 同一 namespace 里会被下一轮读成"同族递进"，而它们连指令出处都不同。处置＝**同轮自纠、只动本轮自己写的字**：改成 `D-N6-1`…`D-N6-5`，共 **12** 处（本文件 **7** ＋ DECISION_LOG **4** ＋ 核对表 **1**），逐串计数对齐后才落盘；`D-⑭-1` 的登记格**一字未动**（现读它的出现次数仍是"登记 1 ＋ 引用 7"，引用全部来自本轮新写的说明），其 TTL 半边另以 〔〕 注记形式标为"被本轮顶替"。
                **DEVICE_VERIFIED**：**未到位**（`bash tools/verify_device.sh` **10:52:28 → 10:52:30 rc=1 / DEVICE_UNAVAILABLE**，`List of devices attached` 为**空**，新证据 `docs/device-evidence/20261006-105228/device-probe.log`）⇒ 那句披露在真机窄屏上的可读性与换行、进程被杀／重建之后"还剩几条"（本机用例里 `clear()` 是唯一清空路径，真机生命周期没有证据）、"30 分钟"这个**本机自定口径**在真实路网更新频率下够不够、`serverAvailable` 在真后端 `routeAvailable=false` 时的实际占比（本轮只有替身驱动的夹具证据），**四格全是 `NOT VERIFIED ON DEVICE`**；整体记 **`ANDROID_N6_⑭_PARTIAL`**，不写百分比。
                **在册不当已解决（OPEN_DEFECT BEFORE = AFTER ＋ 新增 5 项）**：本轮**新增** `D-N6-1`（缓存里的几何**没接进任务地图**，唯一读点只披露不画线 ⇒ V21-14 仍 ❌ 且**理由换了**）、`D-N6-2`（**10 条行为破口里只有 5 条同时被结构半边看见**：B-3 / B-7 / B-8 / B-9 / B-10 注入后 `NAB-*` 与 `N-x` 全部 rc=0 ⇒ 披露层**措辞类**破口目前只有行为半边可辨；待修给那两条披露判据补反证口径，**同轮抬负控**）、`D-N6-3`（census `code=` 与判据簿对同一符号给出 1 与 0 的**工具口径差**）、`D-N6-4`（`RouteOfflineCacheContractTest` 第 6 条并发用例**没有被任何注入咬过**——单线程 JVM 撞不坏，强配一条"应当红"就是编造判据 ⇒ 结构半边钉住、行为半边未证，**不算双向配对**；`serverAvailable` 的**读取时机**同判，由行号顺序那条钉）、`D-N6-5`（编号轴撞名与本簿的改名账）；⚠ 另登记一笔**读数断链**（authenticity 四个数在 D-058 那遍没留）。仍在册 `#140`（APK **Δ = +0 而 sha 变**，连续第 **5** 个未归因读数）、`#159`、`#245`（V21-15 mock 那一半 `isFromMockProvider` 仍 **0/0/0**）、`D-⑭-1`（**TTL／线程安全半边本轮作废**、`graphVersion` 与 canonical fingerprint 那半边**依旧 0 生产者**、仍不做 ⇒ V21-13 只到 ⚠ 不给 ✅）、`D-⑧-1`、`D-⑧-2`、`D-⑪-2`、`D-⑪-3`、`D-⑫⑬-1…-7`、`D-⑱-1`、`D-⑱-2`、`D-⑱-3`（**本轮把 `resolve` 改成收 `serverAvailable` 入参 ⇒ 那一笔的成因已被关掉**，登记格不撤销、按"收口于 ⑭"改读）、DEVICE/RELEASE/GIT。
                **硬规则自查（逐条实测）**：源码树 `*.jks` / `*.keystore` = **0**；main 里 `127.0.0.1` / `localhost` = **0**；HTTPS 闸门原文仍在 `app/build.gradle.kts`（**11,008 B / 226 行 / `1a65667d76a63683`**，与 D-058 那遍**同串 = 一字未动**）、`gradle.properties` **920 B / 13 行 / `a7d8573167ee9880`** 同串，且**未传** `-Pbuspost.allowInsecureRelease`；`git rev-parse --is-inside-work-tree` = `fatal: not a git repository` ⇒ **没有 `git init` 造假历史**；本轮**新增依赖 0 / 新增第三方构件 0 / 新增 UI 组件 0 / 改后端 0 行**；`tools/*.py` **10** 本未增（`tools/check_*.sh` = 23，含本轮扩写的那本判据簿与新落盘的那本负控）。⚠ **调用点线程一字未动**：`routeGuide()` 仍是 suspend、`routeFailureTextOf` 非挂起，**没有**为披露加 `Thread{}` 或改 dispatcher（本轮动的是数据层与判据层，不是 UI 重构）；披露层不 import 任何 Android / 高德类型，结构上拿不到 `MapPath` / `.polyline` / `Geometry`。
                **NEXT**：**下一轮 = 赛道⑮ MapLibre / Valhalla 备用方案（队列 #218）**，依赖取证优先、编译困难就 BLOCKED。⚠ 开工前先算两笔账：① **哪些判据的整行字面量指在本轮改过的那两本文件上**（`RouteRepository.kt` 里的三态与缓存条目、`Repo.kt` 里那一个披露调用点）——本轮 NAB 那 9 行打印读数 ＋ `N-x` 那一行都长在它们身上，动文本就要同轮重锚；② **`D-N6-1` 是下一轮之前必须回答的设计问题**：把离线几何接进任务地图会给 F-3 ⑧ 的"无路线三态"加**第四格**，`MR` / `MV` 两族判据（⑫⑬ 刚收口）要同轮重锚——这是"接不接"的决定，**不属于** MapLibre 那一问，别混进依赖取证里顺手做。其后：**#219 赛道⑯ UI 细节**（最后做，不扩 UI）。
                  ⚠ 本文件「## NEXT（用户指定顺序，不得打乱）」那一节是 **⑦ 收工时的历史快照**，里面那句"下一轮 = ⑨"指的是 **F-2 的第 9 步**（早已收口）⇒ 抄当前活一律以本块为准，别从那节抄顺序。

CURRENT_ANDROID_STATUS:  赛道⑫⑬（N-5）收工（2026-10-05 15:5x – 10-06 00:09:18 终值链结束，台账落档与写后终检至 00:58:39（写后终检八本：7 本 rc=0、`verify_device.sh` rc=1 = DEVICE_UNAVAILABLE，逐本读数现记于 DECISION_LOG D-058 §10，⚠ 本块不复述数字以免同一读数出现两个出处）；⚠ **本块不用 mtime 当改动面证据** —— 本轮又跑过负控批（**21:49:30 → 23:53:32**）＋ 本簿自己也在改源码，`app/src` 那一批文件的 mtime 全落进窗口（㊱ ⑧ 第二次兑现：`Map3DVisual.kt` / `MissionMarkers.kt` 的 mtime 被 `[0/8]` 的负控复原推到 00:01:34，而字节与 sha 未变）；改动面证据只有字节数 / 内容指纹 / `cmp`）
                **SOURCE_COMPLETE**：本轮买的是**"3D 立体感"这一族里能被本机证据钉住的只有"谁有权决定怎么画"，而"朝向没给"被画成正北是这一族唯一真正在撒谎的地方**；UI 未扩（**无新按钮 / 无新屏 / 无新路由**，正式任务地图页只把既有那五个格子接上视觉层的形状出口）。
                  ① **取证只从这颗 jar 的签名与资产在场性来**（同一颗 `navi-3dmap` 10.0.600 = 50,991,398 B / sha256 `9fa3cfb81ff7…` / 2,403 条目 / 1,855 个 `.class`，本轮没换依赖）：真正动用的只有 `MarkerOptions.perspective/setFlat/zIndex/rotateAngle/altitude` 五个 ＋ `CameraUpdateFactory.changeTilt` ＋ `UiSettings.setTiltGesturesEnabled`（⚠ 后三个是 **float** 不是 double）；而 `GL3DModel` / `GLTFOverlayOptions` **类在**（条目名 `GL3DModel*` 命中 **2**；并把 `GLTFOverlay` 一族并进来数 = **5**）、**资产不在**（条目名里 `.obj`/`.glb`/`.gltf` = **0**，把 **1,855** 个 `.class` 的原始字节逐个读出来再找 `.obj`/`.glb`/`.gltf`/`.fbx` 仍是 **0**），`vertexData(List<Float>, List<Float>)` 要的是裸顶点＋法线 ⇒ **用它就是自造几何**，按"未做"登记。
                  ② **朝向只有一个生产者**：本机 `Location.hasBearing()`；后端 `AppDriverLocationReqVO` / `AppDriverRouteRespVO` 逐字段读下来**没有任何朝向字段**，`RouteGuide.headingDegrees` 是折线几何**推断**的 ⇒ 指令 ⑬ 那句"车辆按 GPS bearing 旋转"在车载那一格**没有可信来源**，本轮只做"我"这一格（`MR-p` 钉整行、用例 6/7 钉边界、MV-6/MV-7 双向负控）。
                  ③ **形状只有一个出口**：`Map3DVisual.marker3DOf(kind, visual)`（`MR-l` 实测 = 目标 **1/2/5/0** ＝ 视觉出口对象 1 / 出口函数 2（`marker3DOf` + `gpsFixVisual`）/ 那五个 SDK 立体调用只在 `BusMap` 内 **5** / `BusMap` 之外 **0**）；页面不许各自 `setFlat`/`altitude`/`rotate`。新鲜度三态与**服务侧停报阈值同源**（`LocationReportPolicy.MAX_FIX_AGE_MS` 现读 **45,000 ms**，`MR-q` 钉整行 = **1/1/1/1**、视觉层自造毫秒数 **0**）；脚注只**转述**服务原话、LIVE 返回**空串**（`MR-c`/用例 10/11/12）。
                  ④ ⚠ **撤掉的东西要说清撤的是哪半**：本轮**没有**撤任何函数；"不做"的三件是 marker 逐帧插值（`animateVehicle`/`lerp` 生效行 **0** ＋ 含注释反证 **1**，`MR-n` 实测 **0/1**）、`GL3DModel` 建模、上报报文带朝向（`MR-o` 钉"上报行带朝向 = **0**"，反证服务侧 **3** 处）。
                **BUILD_VERIFIED**（终值 = 负控之后的最后一遍，不是最好看的那一遍）：debug variant **48 个类 / 510 例 / 0 failures / 0 errors / 0 skipped**（`[2/8]` `--rerun-tasks` rc=0，`BUILD SUCCESSFUL in 46s`、**24 actionable tasks: 24 executed**，XML mtime 集合唯一 `['00:02:19']` ⇒ 整批是同一次跑出来的；本轮那本 `Map3DVisualContractTest` = **16/0/0/0**，对照 `NavigationContractTest` = **52/0/0/0**）；
                  ⚠ **release variant 未跑**（`D-⑱-1` 那 **5** 例红的成因仍在，本轮引用的是上一轮只跑 debug 的读数），`assembleRelease` 另被 HTTPS 闸在配置期挡住：**rc=1 @00:04:55**，原话逐字 = `release 包必须使用 HTTPS（当前 http://1.15.29.107/api）。内网测试请显式加 -Pbuspost.allowInsecureRelease=true。`（`BUILD FAILED in 4s`）⇒ 两件事不许并成一句"release 也好了"，**没有**传 `-Pbuspost.allowInsecureRelease=true`。
                  **lint**：`lint-results-debug.xml` **98,754 B** @10-06 00:03:57，`<issue>` 总数 **78** = Warning **46** ＋ Information **32**，Error **0**（`[3/8]` 显式跑了 `--rerun-tasks`，`BUILD SUCCESSFUL in 1m 35s`，rc=0 —— 第一次若报 UP-TO-DATE 就不算本轮读数）。
                  **打包**：APK **113,928,596 B @10-06 00:04:46 / sha256 `96c7849c6da30d7e…`**，对本轮开工前留档的 D-057 那一包（`n5_apk_prev_D057.apk` **113,945,900 B** / sha256 `3cf3c1ed05710164…` @10-05 15:45:35）**Δ = −17,304 B**（⚠ 包变小了；这个 Δ **仍不归因**，`#140` / `D-⑧-1` 同族：不做差量实验就不写"这 17 KB 是哪个类"）。
                  **dex 类清单**：提取器**方法学验证在前**（重导留存的上轮包 = **1,371 条 / md5 `7a022c22f9eeab4e57c8987e922840ac`**；D-057 那行记的是"留存的上一轮包重导 **1,366 条** / md5 `30c488351f942f5b…`"**＋**"入 **5** 出 **0**"⇒ 1,366＋5 = 1,371 **条数对得上**；⚠ 但 D-057 **没有**记自己那一包的 md5 串，所以**串对不上**，这一格如实记成缺项、不写成"方法学已完全复现"）；新包 **1,378 条 / md5 `513f615714c1eca9f0822a7e8b1ca882`**，差集（python **集合差**，口径见 D-058 §10 ＋ §八 ㊱ ⑫：旧写法 `comm` 两份输入没按同一键预排序，计数与具名两格会自相矛盾）**出 0 / 入 7**，入榜**逐条具名**（全在 `com/shanxiang/buspost/ui/common/`）：`GpsFixState`、`GpsFixVisual`、`Map3DVisual`、`Map3DVisual$WhenMappings`、`Marker3D`、`MarkerEmphasis`、`MarkerVisual`；⚠ 测试那本按**描述符**点名 `Lcom/shanxiang/buspost/ui/common/Map3DVisualContractTest;` 在 16 个 dex 里合计命中 **0** = 真没进包。
                  **判据面**：`ls tools/check_*.sh` = **23** 本（其中 `check_*probes.sh` **22** 本），终值树逐本 **rc=0（23/23 全 0，无点名）**；`python tools/check_ledger_tables.py` rc=**0** / **TOTAL FLAGS 0**；`bash tools/v21_requirement_census.sh` rc=**0** ⇒ `V21-15|isFromMockProvider|hits=0|files=0|code=0`、`V21-15|fun heading|hits=2|files=2|code=2`；`check_authenticity_counts.sh` rc=**0**。
                  **判据扩建（结构 MR-l…MR-r 一组 7 条 / 15 行打印读数）**：终值树 15 行**逐条实测 = 脚本目标**：`MR-l 1/2/5/0`、`MR-l 1/1/1`、`MR-m 1/1/1/1/1`、`MR-n 1/3/3/1`、`MR-n 0/1`、`MR-n 行号 208/214`（严格递增那条，倾角先行 < 视口动画）、`MR-o 1/1/1`、`MR-o 0/1`、`MR-o 0/3`、`MR-p 1/3`、`MR-p 0/3`、`MR-q 1/1/1/1`、`MR-r 0/0/7`、`MR-r 0/23/16`、`MR-r 0/10`；结构注入 **22** 条（MR-l 4 / MR-m 2 / MR-n 3 / MR-o 4 / MR-p 2 / MR-q 3 / MR-r 4），`mission_route_negative_controls.sh` 全簿由 ⑧ 的 **27** 长到 **49** ——批内实测 **rc=0 ✔=49 ✘=0 △=0 ◦=0**（703s，22:37:46→22:49:29，末行"[恢复后] 任务地图无路线态判据通过"）＋ **批后单跑 rc=0 ✔=49 ✘=0 △=0 ◦=0**（00:30:56→00:41:22，`/tmp/n7l_mission_route_single.log`；跑完 00:42:55 整树指纹复量仍 = **164 / `1b6d1ff3…`** 同串 —— 49 次注入一个字节没留下）。
                  **行为负控（本圈新增那一本）**：`tools/map3d_visual_negative_controls.sh`（**278 行 / 18,066 B / sha256 前缀 `9d11900d880a2223`** / CR=0）—— **MV-1…MV-16 与 16 条用例逐条一对一**；基线双绿（`gradle exit=0` **且** MR 结构判据 rc=0）→ 起跑前**预检 16/16**（锚点各恰好命中 1 次、具名用例各命中 1 次，不过就 exit 5 且一条不注入）→ md5 变了才继续 → 红的必须含具名那一条 → `grep '^e: '` 先排除编译红 → `cmp` 复原 → 恢复后重新双绿。**首跑实测 `RESULT=FAIL`：13 ✔ / 3 ✘**（MV-2 / MV-13 / MV-15，三条都是**我的用例在骗我**：`contains("0°")` 被 `"朝向 360°（GPS）"` 的子串混过去；下一任务的夹具让"排除已完成/失败"这条规则**注入不可见**；CURRENT 恰好落在本来就 `KIND_TASK_ACTIVE` 那一站，改色看不出来）⇒ **只改测试**（16 条数 / 用例名 / 全部注入锚点一字未动，两个源文件指纹复量一致 `91904e0707d30015` / `0f5f50e83680f4fb`），单类 **16 tests / 0 failures**（21:48），随后**整批负控与终值链重跑**。改后实测：批内 **rc=0 / ✔=16 / ✘=0 / △=13 / ◦=3**（435s，22:30:30→22:37:46）＋紧贴终值 `[0/8]` 再跑一次 **rc=0 / 16 ✔ / 0 ✘ / 13 △ / 3 ◦**、末行 **`RESULT=PASS` @00:01:34**（3 条 ◦ = MV-3/MV-6/MV-7：注入同时把 MR-* 打红；13 条 △ = 结构判据 rc=0，覆盖面归结构半边）。
                  **负控面（负控在前、终值在后）**：本圈**重跑 1 次全批**（因为那三条 ✘ 之后测试文件被改，旧批不充当终值）：**21:49:30 → 23:53:32，共 26 本**，聚合 **✔=601 / ✘=2 / △=13 / ◦=17**，批次入口按聚合闸门 **exit 1**；逐本 rc：**24 本 = 0**，**2 本 = 2**（`notify_result` / `oss_credits`，末行原话 `✘ 真身没有 app/build/test-results/testDebugUnitTest/TE…`）；⚠ **口径本轮变了**：`tools/map3d_visual_negative_controls.sh` 此刻已在 `tools/` 里、批次入口展开 glob ⇒ **它已被计入批内**（共 **26** 本，不是上一轮口径的"25 本不含本簿"），链上第 0 步还会紧贴终值再单跑一次；那两本读测试产物的负控在批内因 `--tests` 过滤清空 `test-results/` 而 rc=2（**这是前置未满足，不是破口**），终值全量（00:02:23）之后各单本补跑 **rc=0**：`notify_result` 00:16:35→00:18:08（`SUMMARY|book=notify_result|破口=12 反向=2 预检=2|命中=16 失效=0`、`RESULT=PASS`）、`oss_credits` 00:18:10→00:25:52（`SUMMARY|book=oss_credits|破口=13 反向=2 预检=4|命中=19 失效=0`、`RESULT=PASS`）—— **"批内 rc=2 ＋ 补跑 rc=0"两行都写，不合并成"全批 rc=0"**；补跑之后 `app/src` 树指纹复算 **164 文件 / `1b6d1ff3487cc6f3…`**，与 `[1/8]` 逐字同串 ⇒ 补跑没动过真身。
                **本轮自己付的账 ＝ 三笔（前两笔是"闸把我拦住"，第三笔是"闸没拦住我"）**：① 我给 `Map3DVisual.kt` 写的 KDoc 里出现了 `Running.waiting` ⇒ 分域闸 `P-a2` 由 **0 变 1**（`check_domain_probes.sh` rc=1）—— 它数的是 `ui/common` 里的 `Running\.`、**不过滤注释**；修的是**源码那句措辞**，**没有**给探针加"排除注释"的过滤（那等于为了让闸门变绿而缩小判据）；终值 `P-a 0 / P-a2 0`。② 新增那本用例文件让 `SU-b` 的"测试文件恰好 **49**"变红 ⇒ 同轮把基数改成 **50** 并把归因（含"现读 16 条 @Test、`SU-a` 那行是反证"）写进脚本；两个下限 `≥47` / `≥476` **一个字没抬**；终值 `SU-a 48/0`、`SU-b 50/2`、`SU-c 0/510`、`SU-d 7/3`、`SU-e 10`、`MR-a 12`。③ **本圈新写那本行为负控首跑 `RESULT=FAIL`（13 ✔ / 3 ✘）**：三条 ✘ 全在测试文件里 —— 子串断言被"多一个合法值"混过去、夹具让注入不可见、CURRENT 落在本来就同色的那一格；处置是**只改测试并整批重跑**，不抬闸、不把 ✘ 解释成"这条不必钉"。⇒ 这三条同时证伪了我自己在用例表里写下的三句"这一条钉这种坏事"：**没跑过注入的用例数只是一个数字**。
                  ⚠⚠ **新增在册项 `D-⑫⑬-6`（"没有闸门会叫的漂移"又一笔，且本轮数出了两档）**：`ANDROID_V2.1_CHECKLIST` 的 V21-15 那一格挂着一个指 `Locator.kt` 第 217 行的旧行号锚，而本轮之后 `fun heading` 在**第 228 行**；`check_doc_anchors.py` **全绿不叫** —— 那格锚点**没带任何 assigned token**（`tokens=[]` ⇒ 不做 ID 校验）。修法是**撤掉那个行号锚**（位置改由 `MR-o` 的整行字面量钉）：**补一个新行号锚会把同一次漂移再演一遍**，而新 aid 在 `--pin` 口径下正是"要被当成可疑"的那一类。⚠ 同轮把 `Locator.kt:` 这一族**八处全走查**：**对得上 1（`CONTEXT_LOCK.md` 那一格、指 `Locator.kt` 第 103 行，落在 `BuildConfig.DEBUG` 那块内部、打印本体在 106）／漂了 7** —— 除 `:217`（差 11 行且指到 `accuracyOf`）外，`RUNCATCHING_AUDIT.md` 那 6 处**一律下漂 +1…+3**（`:204→207`、`:129→131`、`:133→135`、`:165→166`、`:166→169`、`:171→173`）；这 6 处的锚点都在 129…207 区间，而本轮对 `Locator.kt` 唯一的改动在第 228 行 ⇒ **漂移早于本轮**，本轮只是第一次数出来。⚠ 更安静的一档：`docs/android/RUNCATCHING_AUDIT.md` **根本不在** `check_doc_anchor_probes.sh` 的七本名单里（脚本里那份 DOCS 名单现读七本）⇒ 那 6 处不是"不带 token 不校验"，是**整本没人读**；本轮**没有**把它加进名单（一加就有六行红，而那六行是上一轮就漂的、与本轮指令无关），如实登记在 `D-⑫⑬-7`。
                **DEVICE_VERIFIED**：**未到位**（`bash tools/verify_device.sh` → **rc=1 / DEVICE_UNAVAILABLE**，`adb` 设备列表为**空**，新证据 `docs/device-evidence/20261006-000915/device-probe.log`）⇒ 35° 倾角的实际观感、8 米浮空在乡道尺度上会不会脱离底图、marker 动画表现、脚注在 7 档宽度下的排版，**四格全是 `NOT VERIFIED ON DEVICE`**；整体记 **`ANDROID_N5_⑫⑬_PARTIAL`**，不写百分比。
                **在册不当已解决（OPEN_DEFECT BEFORE = AFTER ＋ 新增 7 项）**：本轮**新增** `D-⑫⑬-1`（车辆那一格的朝向**没有生产者**，指令 ⑬ 那半边未实现，折线推断不算 GPS bearing）、`D-⑫⑬-2`（`GL3DModel` / `GLTFOverlayOptions` **有类无资产** ⇒ "真 3D 模型"这一格按未做登记）、`D-⑫⑬-3`（3D 观感四格**本机零证据**：倾角 35° / 浮空 8 m / 相机 800 ms / marker 逐帧插值刻意不做）、`D-⑫⑬-4`（改动面**没有逐文件改前基线**，起手只有整树指纹；负控之后 mtime 整族作废）、`D-⑫⑬-5`（V21-15 只收口了**一半**：`heading()` 那半 DONE，mock 那半 `isFromMockProvider` 现读 **0** = 从未检查过，仍是 ❌ 且不在本轮指令范围）、`D-⑫⑬-6`（`Locator.kt:` 那一族八处走查：对得上 1／漂了 7，V21-15 那格本轮已撤锚）、`D-⑫⑬-7`（`RUNCATCHING_AUDIT.md` 整本不在文档锚点探针名单 ⇒ 它那 6 处没人核）；仍在册 `#140`（APK **Δ −17,304 B** 是**连续第 4 个**未归因读数：⑨ +16,384 → ⑩ +0 而 sha 变 → ⑪ +33,688 → ⑫⑬ −17,304；⚠ 这一轮是**负数**，"包变小了"同样不许写成优化）、`#159`、`D-⑪-2`（本轮另加两笔同族 `D-⑫⑬-6`／`D-⑫⑬-7`，两种静法见 CONTEXT_LOCK 的 §九）、`D-⑪-3`、`D-⑱-1`、`D-⑱-2`、`D-⑱-3`、`D-⑧-1`、`D-⑧-2`、DEVICE/RELEASE/GIT。⚠ 另登记两笔**读数口径**缺口：旧结构簿 `mission_route_negative_controls.sh` 的 ✘ 计数器与退出码缺口（本圈在**新簿**里修好了，旧簿未动）＋ 台账里那条过期的 `packageDebug` 读数。
                **硬规则自查（逐条实测）**：源码树 `*.jks` / `*.keystore` = **0**（签名备份仍在工程外那一个目录，与前几轮同格）；main 里 `127.0.0.1` / `localhost` = **0**；HTTPS 闸门原文仍在 `app/build.gradle.kts`（**11,008 B / 226 行 / `1a65667d76a63683` @09-30 00:40:50**）且**未传** `-Pbuspost.allowInsecureRelease`；`git rev-parse --is-inside-work-tree` = `fatal: not a git repository` ⇒ **没有 `git init` 造假历史**；本轮**新增依赖 0 / 新增第三方构件 0**（那颗 jar 一字未动，sha256 前缀现读同串 `9fa3cfb81ff7…`；`app/build.gradle.kts`、`gradle.properties` **920 B / 13 行 / `a7d8573167ee9880` @09-29 02:29:42** 一字未动；显式三段坐标去重现读仍 **20** 条，`compose-bom … mockwebserver` 一条不多一条不少）；`tools/*.py` **10** 本未增。⚠ 视觉层不 import 任何业务模型/契约类型（`MR-r` 钉 import 业务模型 **0**（反证 BusMap＋出口对照 **10** 处）），也不写任何共享状态（生效写点 **0**，反证服务侧 **23**）。
                **NEXT**：**下一轮 = 赛道⑭ 离线真实道路缓存（队列 #217 / N-6）**。⚠ 开工前先数"有多少本判据的锚点指着 `ui/common/Map3DVisual.kt` 与 `ui/driver/MissionMarkers.kt`"（本轮新写那一本就钉 **16** 个整行字面量，`mission_route` 结构簿钉 **13** 行读数 —— 下一轮只要动这两本文件的文本，就要同轮核**两本**）；⑭ 那一族的第一步是**读现状**：今天 `N-x` 那格读的是"缓存写了但没人读"（`0/1`），给一份没人读的缓存加 TTL 就是"把还没接上登记成已经安全"（`D-⑱-3` 同族）。
                  其后按顺序：**#218 赛道⑮ MapLibre / Valhalla**（依赖取证优先，编译困难就 BLOCKED）→ **#219 赛道⑯ UI 细节**（最后做，不扩 UI）。
                  ⚠ 本文件「## NEXT（用户指定顺序，不得打乱）」那一节是 **⑦ 收工时的历史快照**，里面那句"下一轮 = ⑨"指的是 **F-2 的第 9 步**（早已收口）⇒ 抄当前活一律以本块为准，别从那节抄顺序。

CURRENT_ANDROID_STATUS:  赛道⑪（N-4）收工（2026-10-05 12:5x – 15:5x；⚠ **本块不再用 mtime 当写入顺序旁证** —— 本轮跑过负控批，`app/src` 里 **12** 个源文件的 mtime 被批内"注入—复原"刷进了 14:12:58 → 15:33:20 这个窗口（其中 **11** 个本轮一字未动），"文件写于几点"这一族证据在批后**作废**；改动面只认字节数 / 内容指纹 / `cmp`：终值树指纹 **`files=162 sha256=919278aa538dadd8a6441ad2731d262073b35cd09dbc4e3c5fd59cd40609393b`**（15:43:15 与落账阶段 16:40:11 两遍**同串** ⇒ 终值全量之后 `app/src` 一字未动），本轮动 **5** 个文件、新增 **0** 个）
                **SOURCE_COMPLETE**：本轮买的是**"高德那句'它要重新算路'与那句'它算完了'是两条互不相干的广播，本机不许把这两条配成因果"，同时把工程里唯一那处本机偏航假判定撤读**；UI 未扩（正式导航卡片只多**重算** 1 格读数，落在既有那段"高德实时回执（最近一帧）"里 —— **无新按钮 / 无新屏 / 无新路由**）。
                  ① **取证五条签名事实 + 一处"清单里没有"**：`onReCalculateRouteForYaw()` 与 `onReCalculateRouteForTrafficJam()` 在 `AMapNaviListener` 里是 `public abstract void`、**零入参**（偏了多少米、往哪偏、拥堵在哪一段、多重，高德一个字都没给 ⇒ 那一格只能复述"它说了"）；`AMapNaviRouteNotifyDataType` 七个常量全量 = `1 / 2 / 3 / 4 / 5 / 20 / 21`，⚠ **值域里没有"偏航"这一类**（`6..19` 也不存在）⇒ `onNaviRouteNotify` 本轮**没接**，而这是**"清单里没有"不是"绝不会通知"**（真机零样本，只当不接的依据，不当结论）；`ReCalculateRouteType` 那 **14** 个常量是 `reCalculateRoute(int)` 的**入参**值域（本机**主动**重算才挑原因码），**不是回调的输出**；算路成功 / 失败**各两支**重载；`AMapNavi` 是**进程单例** ⇒ 一次广播与一次回执**不共用任何关联键**（无 requestId / 无 generation / 无序号），"把这两条配起来"在本机就是**编因果**。留档 `/tmp/n7_recalc_javap.txt` 现读 **82** 行 / **3,894 B** / **CR=55、CRLF=55** —— 混合行尾 ⇒ 行数只能 python 字节计数（§八 ㉟ ⑨ 那个坑第三次遇到）。
                  ② **形状（三层各只干一件事）**：判据层（现读 **792** 行 / 39,761 B，D-056 终值 707 / 33,859 ⇒ **+85 / +5,902**）= `sealed class EngineRecalcDisplay { Yaw / TrafficJam / EngineInitiatedSuccess(code) / EngineInitiatedFailure(code) }` ＋ 唯一话术出口 `engineRecalcTextOf` ＋ 接口新增 `val recalc: StateFlow<EngineRecalcDisplay?>` ⇒ **五格（含 `null`）各一句话、谁都不替对方说话**：`null` 那句顺带声明"本机不拿距离卡阈值判偏航"，两支回执各把高德给的 `errorCode` **原样**念出来（不做默认、不改写）并明说"这条回执不带原因"；⚠ 两个 `EngineInitiated*` 的类型里**没有** yaw / jam 的任何引用 ⇒ **配对在类型上就写不出来**（⑨"表外只能 `null`"、⑩"四个'没有'是四个类型"，这是同一招式的第三次落地）；SDK 层（**464** / 25,852 B）只搬运不加工；正式卡片（**350** / 18,083 B）只订阅那一个 flow、只调那一个话术出口，注释明写"本页面不测距"。
                  ③ **新建测试类 0、既有簿增补 7 例**（45→**52**）：五格各自一句话 / 偏航与拥堵是两格 / 两条广播句子里不许出现结局 / 高德自己回的算路结果不配成偏航重算成功 / `null` 那格顺带声明本机不判偏航 / 这一族里没有本机阈值也没有本机偏离结论 / 替身引擎的重算流恒空 —— 七例各钉一种坏事；用例簿现读 **886** 行 / 45,706 B（`split('\n')` 口径会给 **887** ⇒ 两口径都记、不许互引）。行为负控 **M27–M33** 逐条从 gradle 现读红 **2/2/1/1/1/2/1**（不是"每条大约红一例"的心算），基线 `exit=0 failures=0/52` → 七次破口全红 → `cmp` 证复原逐字节相同 → 复跑 `exit=0 failures=0/52`。
                  ④ ⚠ **撤掉的东西要说清撤的是哪半**：DEBUG 旧屏 `offRoute` 那条本机 **60 米垂距阈值**本轮撤读，现读该表达式只剩 `roadOk && g.deviated`（第 **216** 行，偏航结论只由 `/driver/route` 下发的 `deviated` 决定）；被撤的 `crossTrackMeters()`（第 **124** 行）**函数本体仍在**、仍是那个纯几何函数 —— 撤的是**消费点**不是工具，下一轮别以为换算函数被删了。方向也要写清：不是"本机不再算距离"，是"**本机不算了去替高德下结论**"。⚠ 那台旧屏自己的行内说法（第 **874** 行那句"垂距 57 m 仍判不成偏航"）本轮**未动** —— 它描述的是已经撤掉的那条阈值的后果，是 DEBUG 文案、不是正式面口径。
                **BUILD_VERIFIED**：debug variant **47 类 / 494 例 / 0 failures / 0 errors / 0 skipped**（`testDebugUnitTest --rerun-tasks` → rc=0 / `BUILD SUCCESSFUL in 1m 19s`，47 份 XML 的 mtime 集合唯一 `['15:43:56']`）；**与批次前那一遍的对照**：对 D-056 的 487 例是 **+7**，全部落在 `NavigationContractTest` 一本里（逐类现读 **52 / 0 / 0 / 0**）⇒ **类数 47 不变 = 没有为凑数新建测试类**这一条是现读不是主张。
                  ⚠ **release variant 本轮未跑**：`D-⑱-1` 那 **5** 例红的成因仍在，且本轮引用的是**上一轮读数**（只跑 debug），而 `assembleRelease` 另被 HTTPS 闸在配置期挡住（rc=1 / `BUILD FAILED in 3s` / 那句 `release 包必须使用 HTTPS（当前 http://1.15.29.107/api）` 逐字照抄，**没有**传 `-Pbuspost.allowInsecureRelease` 去绕）—— 两件事不许并成一句"release 也好了"。
                  **lint**：txt **366 LF / 31,452 B** / 末行 **`0 errors, 46 warnings`**，XML **98,753 B**，`<issue>` **78** = Warning 46 ＋ Information 32 ＋ Error 0，Top `AutoboxingStateCreation 32 / DefaultLocale 10 / UnusedResources 10 / HardcodedText 8 / GradleDependency 7` **与 D-056 逐项相同**；⚠ 旧口径 `count('<issue')` 在同一份 XML 上得 **79**（把 `<issues>` 容器算进去），本轮另有一处对 txt 的正则把 `AutoboxingStateCreation` 数成 **33** 而 XML 真值 **32** ⇒ 两口径都记、**只抄 XML 那套**。
                  **打包**：APK **113,945,900 B @15:45:35 / sha256 `3cf3c1ed05710164be989e86264e82c248fb3331e2e3268cad2cb91b5828cee3`**，对**本轮开工前就留档**的 D-056 那一包（`/tmp/n4_apk_retained.apk` 113,912,212 B）差 **+33,688 B** ⇒ **未归因**（本轮**没做差量实验**，既不写体积优化也不写回归；`#140` 不闭环）。⚠ 这是连续第三个未归因读数（⑨ +16,384 B、⑩ +0 B 而 sha 变、⑪ +33,688 B）⇒ 只够支撑"打包/dex 对齐量子"那个**假设**继续挂着。⚠ 这一格差集做得成，靠的正是 D-056 §10 立的"打完包立刻留档上一版包"那条机制 —— **上一轮的机制第一次让下一轮受益**。
                  **dex 与类清单（差集做得成）**：提取器**方法学验证在前** —— 拿留存的上轮包重导 = **1,366 条 / md5 `30c488351f942f5bdd7edba309872c96`**，与 D-056 §5 记的串**逐字同串** ⇒ 提取器同输入同输出、有资格报差集；新包 **1,371 条 / md5 `7a022c22f9eeab4e57c8987e922840ac`**，差集 **出 = 0 / 入 = 5** 且五条**全部具名**（`EngineRecalcDisplay` ＋ `$Yaw` / `$TrafficJam` / `$EngineInitiatedSuccess` / `$EngineInitiatedFailure`）⇒ 与 §2 预告的"新增 **5** 个类型"逐条对上，本轮**没有**留下"增量归不到名字"；`OS-e` 打包类 **29,847**、**16** dex。⚠ 另两个基线同时在账：对 ⑪ 开工前（10:40）那份快照 1,359 是 **+12**（含 ⑩ 那 7 条），对 D-052 基线 1,353 是 **+18** —— 三个数各是不同两遍之间的事，**不许互抄**。
                  **判据面**：`ls tools/check_*.sh` = **23** 本，终值树逐本 **rc=0**（留档 `n4_t3_check_*.log` 共 **23** 份，15:45–15:48）；`python tools/check_ledger_tables.py` rc=0 / **TOTAL FLAGS 0**；`bash tools/v21_requirement_census.sh` rc=0 / 末行 `SUMMARY rows=119`（现读第 **120** 行）；导航簿末行"第 8 步导航引擎判据通过"，N-a **52**（下限同轮抬到 **≥52**，抬的依据是 §上那七例、**不是**把下限调低）、N-j **10/0/0**（与 D-056 逐字相同，本轮**不需要**动 —— 这一族不需要新的 SDK 类型）、⚠ **N-v 由 D-056 的 `1/1/1/1/7` 抬到 `1/1/1/1/9`**（作废点 7 → **9**：两条重算广播各加一处），N-u / N-w / N-x / N-y / N-z 各行照旧；`check_authenticity_counts.sh` rc=0：窄 **10** / 宽 **49** / `GeoMath` **27** / `DemoData` 文件 **6** / 假步进 **0** —— 与 D-056 同串。
                  **判据扩建（N-aa 一组，**7** 行 echo / **24** 个数，结构注入 **24** 条全命中）**：族类型/话术出口/非空格数/分支数/flow 承载 `1/1/4/5/2` · `null` 那一格的字面 `1` · 两条广播各接几次/各自作废凭证 `1/1/1/1` · 帧写入总数/清帧/成功帧照抄 code/失败帧只带 code `5/1/1/1` · 本机主动重算/把 notify 当偏航通道/引擎层自己算垂距 `0/0/0` · 页面订阅/页面唯一话术/页面造帧/页面下本机结论 `1/1/0/0` · 旧屏垂距阈值比较/偏航只读后端位/本机判定措辞 `0/1/0`。⚠ 其中"清帧 = **1**"与"`reCalculateRoute` 调用点 = **0**"两格是本族**只有它们会红**的坏事：前者防有人在本机造第 6 个重算帧，后者防本机自己发起重算再把它念成偏航的结局。结构簿批内 **rc=0 / ✔=102**（D-056 是 78，**78＋24=102** 逐本对得上，簿内聚合行现读"共 102 条注入（破口 100 ＋ 反向 2）"）⚠ **单跑同一本** `grep -c "✔"` 会得 **103** —— 收尾那句里也带一个 ✔，两个口径不可互引；行为簿批内 **rc=0 / ✔=35**（28＋7＝35）。
                  **负控面（负控在前、终值在后）**：25 本全批 14:12:58 → 15:33:20，rc=0 **23** / rc=2 **2**，聚合 ✔ **563** / ✘ **2** / △ **0** / ◦ **14**（**逐本行相加**，`共 25 本` 同遍核）⚠ **批日志本身没有聚合 ✔ 行**（只有 `[聚合] rc≠0 的本` / `[聚合] 自带 ✘ 的本` 两行 ＋ 末行"共 25 本"）⇒ 那四个数是本轮把表块 **25** 行逐本相加得到的；**把 `=== 尾部 6 行 ===` 那块重复的两行一起加会得 27 本 / ✔ 603 / ◦ 15** —— 本轮真实踩到过一次。那两本 rc=2（`notify_result` ✔=0/✘=1 用时 1s、`oss_credits` ✔=0/✘=1 用时 2s）末行都是"✘ 真身没有 `app/build/test-results/…`" —— 前面 `--tests` 过滤把 XML 目录清剩两三份，两本都**拒绝编读数** ⇒ 这两条 ✘ **不是"判据通过"**；两本还原后单本复跑（跑在**终值全量之前**的 15:35–15:43，依据 = 现读这两本各自 `gradlew` 调用 **0** 次 ⇒ 复跑不会重跑用例、不违反"负控在前"）rc=0：`notify_result` ✔=**17**（`破口=12 反向=2 预检=2｜命中=16 失效=0`，62s）、`oss_credits` ✔=**20**（`SUMMARY 破口=13 反向=2 预检=4｜命中=19 失效=0`，325s）⇒ 账面只能写 **"23 本批内绿 ＋ 2 本单本复跑绿"**，**不写"25 本全绿"**（沿用 D-056 §9 那句，两种口径不许并抄）。`doc_anchor_negative_controls.sh` 批内 **rc=0 / ✔=8**（137s / `RESULT=PASS`）⇒ 跨簿重锚做在批次**之前**，连续第二轮批内绿，顺序本身被这一格证明。
                **本轮自己付的第三笔"锚点账"，加一笔"没有闸门会叫的漂移"**：本轮改了 `core/navigation` 那两个文件的**文本** ⇒ 按 §八 ㉞ ④ 同轮核所有簿（那一条规矩的**第三次兑现**）。开工第一遍 `check_doc_anchor_probes.sh`（rc=**1**）：DA-a `records=1239 / OK=937 / distinct aid=988`、DA-b `越界 0 / DUP 0`、DA-c `15`（只降不升）、DA-d `在册=262 新增=1 消失=0 可疑 aid=263`、DA-e `ID-MISS=287`（闸 ≤286）、DA-f 反证 `580 / 278` ⇒ 亮的那条具名 `aid=7b98aa74` 指向 SDK 层文件的第 **310** 行（出处是台账第 **3541** 行）而它不在在册表里 —— **一处行号漂移同时点着两格**（新增可疑 ＋ ID-MISS 越闸），这是"行号进 `aid` 是双刃剑"的第三次兑现。本轮账单：**1** 条新增 aid ＋ **12** 行走查账 ＋ **3** 处台账指针 ＋ **1** 次批次不成立，与 D-055 的"2＋3＋1"、D-056 那一笔同族 ⇒ **这一族连续三轮都要付**；规矩仍然照 ㉞ ④：**动那两个文件之前先数有多少簿的锚点指着它们**。复位后终值（15:46 现读）：`OK=938`（**＋1**）、`新增=0 消失=0 可疑 aid=262`、`ID-MISS=286`（回到闸内）、反证 `580 / 279`（**＋1**）⇒ 一条重锚的净效果是 **OK ＋1 与 ID-MISS −1 两格同时动**。⚠ 那 **12** 处里 `:355` 与 `:711` 那一对在 **±3 容差**下**侥幸还绿**，本轮仍然把它们复位了 —— **绿不等于对**。
                  ⚠⚠ **新增在册项 `D-⑪-2`（本轮的"无闸漂移"）**：`docs/android/RUNCATCHING_AUDIT.md` 第三节的逐点账**不在 `check_doc_anchors.py` 的七本台账扫描名单里**，而 `check_runcatching_probes.sh` 对这本**只数行数**（RC-e）⇒ 那 **12** 个行号漂了**没有任何一版闸门会叫**，本轮是**人工**逐条 grep 复位的（250→297、208→255、310→357、320→367、321→368、330→377、349→399、355→405、395→445、696→714、702→720、711→729；那本 **92** 行被改写、字节 18,303 → **18,725（+422）**、LF 154 → **153**，少的那一行是顶部两行括注并成一行、**没有丢事实**）。这是"判据覆盖不到的地方，账面会安静地变脏"的**第四个**实例（与 `D-⑬⑭⑯⑰-1` 同族：那一族说"锚点不在在册表"，本条说"整张表不在扫描名单"）。
                **DEVICE_VERIFIED**：**未到位**（`bash tools/verify_device.sh --no-build` → **rc=1 / DEVICE_UNAVAILABLE**，`adb` 设备列表为**空**，新证据 `docs/device-evidence/20261005-155030/`：`device-check.log` **55 B** / `device-probe.log` **37 B** / `summary.md` **372 B**）⇒ 整体记 **`ANDROID_N4_⑪_PARTIAL` ＋ `NOT VERIFIED ON DEVICE`**，**不写百分比**。本轮全部结论只在 JVM 层与 javap 文本层成立。**特别写明三条无设备证据**：① 真机到底回调 `onCalculateRouteSuccess` 的**哪一个**重载（本机只接了带 `AMapCalcRouteResult` 那支）；② `int[]` 那一支会不会**同时**回调（若同时，同一格被后写的那条覆盖，而覆盖顺序本机不知道）；③ 一次偏航广播之后**是否一定有**一次回执（若没有，那一格会长期停在 `Yaw`，而页面念的是"它要重新算路"、不是结局）。
                **在册不当已解决（OPEN_DEFECT BEFORE = AFTER ＋ 3，不写清零、不写百分比）**：本轮**新增 3 项** —— `D-⑪-1`（`onCalculateRouteSuccess(int[])` / `onCalculateRouteFailure(int)` 两支**未接线**：接它需要先有一条"两条都回调时同一格怎么写、谁覆盖谁"的规则，而这条规则**没有任何一份本机材料能定** ⇒ 与其编规则，不如登记不接并把理由写在注释里）与 `D-⑪-2`（上面那笔无闸漂移）、`D-⑪-3`（`docs/android/` 那几本不在 `check_ledger_tables.py` 的表格自检名单里，本轮写坏 V21 那一行没人叫，靠人工走查到）。BEFORE = D-056 终值 **11 项 ＋ 3 格**，AFTER = **14 项 ＋ 3 格**：`#140`、`#159`、`D-⑧-1`、`D-⑧-2`、`D-⑭-1`、`D-⑰-1`、`D-⑬⑭⑯⑰-1`、`D-⑱-1`、`D-⑱-2`、`D-⑱-3`、`D-⑩-1`、**`D-⑪-1`、`D-⑪-2`、`D-⑪-3`**，另加 DEVICE / GIT 两格。⚠ §7 那**五件**是**新事实 / 新口径**，不是缺陷项，不许混进"新增缺陷"的计数。
                **硬规则自查（逐条实测）**：源码树 `*.jks` / `*.keystore` = **0**（签名备份在工程外 `../buspost-signing-backup`）；main 里 `127.0.0.1` / `localhost` = **0**；`Bearer ` 命中 **2**（`Api.kt` 两处 `header("Authorization", "Bearer $it")` **变量插值**，不是字面量凭据）；HTTPS 闸门原文仍在 `app/build.gradle.kts` 第 **171** 行且**没绕**；**没有**假路线、**没有**假导航（偏航那一族的写入点只有两条广播 ＋ 两条回执 ＋ 一处清帧，N-aa 实测 **5 / 1**）；**没有**本地阈值冒充引擎（那处 60 米阈值本轮撤读，N-aa 第 7 行 `0/1/0`）；**没有**为凑数新建测试类（类数 **47** 不变）；**没有**扩 UI；**没有**在正式导航面加本机 TTS（main 全文 `TextToSpeech` 现读 **8** 处 / **7** 行：**5** 行仍在 DEBUG 旧屏、**2** 行是禁止性注释 ⇒ 出现次数 / 生效行 / 文件数三个数不可互减）；**没有** `git init` 造假历史（`git rev-parse --is-inside-work-tree` 仍 `fatal: not a git repository` ⇒ `GIT=ENVIRONMENT_BLOCKED`）。
                **NEXT**：**下一轮 = 赛道⑫⑬ 3D 任务 Marker / 3D 车辆（队列 #216 / N-5，纯视觉层）**。⚠ 开工前先数"有多少本判据的锚点指着 `core/navigation` 那两个文件"—— 这一族**连续三轮**都要付锚点账（D-055 记 2＋3＋1、D-056 记一笔、本轮 1＋12＋3＋1）；3D 那一族主要动任务地图与卡片层，**能不碰那两个文件就少付一笔**（碰了就要同轮核所有簿，不许"顺手改完再说"）。
                  其后按顺序：**#217 赛道⑭ 离线真实道路缓存**（provider ＋ 抓取时间；⚠ 今天 `N-x 0/1` 说明这份缓存**只写不读**，`D-⑱-3` 与它同族 —— 给没人读的缓存加 TTL 就是"把还没接上登记成已经安全"）→ #218 赛道⑮ MapLibre / Valhalla（依赖取证优先，编译困难就 BLOCKED）→ #219 赛道⑯ UI 细节（最后做，不扩 UI）。
                  ⚠ 队列里的**在册项**不因本轮收口而消失：`#140`（本轮 **+33,688 B** 是**第三个**未归因读数，体积那一半不闭环）、`#159`（那 **12** 条历史错位锚仍在册，本轮没"顺手改对"）、`D-⑱-1`（那五本演示面用例的 variant 闸门）、`D-⑱-2`、`D-⑱-3`、`D-⑧-1`、`D-⑧-2`、`D-⑰-1`、`D-⑬⑭⑯⑰-1`、`D-⑩-1`、**新增 `D-⑪-1` / `D-⑪-2`**。本块**不新增 `文件.kt:行号` 形态的锚点**（行号一律写成"第 N 行"）⇒ #159 那笔重锚债不因本轮记账变大（终检 DA-d `新增=0`、DA-e `286` 已现读）。
CURRENT_ANDROID_STATUS:  赛道⑩（N-3）收工（2026-10-05 09:2x – 12:4x；写入顺序旁证（**不当改动面证据**，工程无 git）：`AmapNavigationEngine.kt` 09:54:47 → `DriverSdkNavCard.kt` 09:57:19 → `NavigationContractTest.kt` 09:59:11 → `check_navigation_probes.sh` 10:04:44 → `navigation_negative_controls.sh` 10:05:32 → `navigation_test_negative_controls.sh` 10:17:14 → `runcatching_negative_controls.sh` 与 `RUNCATCHING_AUDIT.md` **同一秒 10:34:44**（跨簿重锚那一遍，文档与簿子同轮改）→ **`NavigationEngine.kt` 11:30:56**；⚠ 最后那个 mtime **不是本轮内容写入时刻**：M19…M26 那八条行为注入落的正是判据层那一本（脚本 `NE=` 指向它、`restore()` 从备份 `cp` 回真身），而那批 10:38:48 起、11:54:45 止 ⇒ 11:30:56 是**还原写回**（与 D-055 那一遍的 00:29:49 同一形状）—— 只能说"改过"，不能说"改成什么"；改动面主凭证 = 内容指纹 `files=162 sha256=7089591423e3a1d56ca320b2b6b7c9b8c98db3d45082128cbc9e86d6f290cb5d`，⚠ 这一串本轮**现读了三次**（批次启动后 10:40 / 批次结束后 11:55 / 终值全量之后 12:16）**三次同串** ⇒ 25 本负控批确实没把源码留在被注入过的状态（这是"负控在前、终值在后"能成立的**前置凭证**，不是收尾时补的一句安心话）；对 D-055 记的 `5fa87a2d…` 是**同文件数、不同内容串**（本轮改 4 个 `app/src` 文件、**新增 0 本** ⇒ 真加了源文件 `files` 必须涨到 163）；负控全批 10:38:48→11:54:45、两本单本复跑 11:57 / 12:07、终值全量 11:59:04→**XML mtime 集合唯一 `12:00:15`**、lint＋打包 12:08:03→12:10:27（**另起一条命令**，与 `test` 那条分开）、判据 23 本 12:11–12:13、设备闸门 12:14:57 与 12:15:26（**实际跑了两遍，终值只引用后一份**）、D-056 写入 12:24→12:27 之间（两本备份的字节数把这一格夹住：531,351 前 / 559,618 后）、§八 ㉟＋§九 12:32:15、V21-08/09 就地复测 12:34:01、D-056 前三处自纠 12:35:16、NOTICES 末段 12:36:37、**D-056 第四处自纠 12:42:17（现读 560,363 B / 4,052 行 / 孤立 CR 0）**、本块与 DONE 于 12:44:57 写入、随后在同一条物理行内做过一次时间更正（现读 665,310 B / 3,888 行 / 孤立 CR 0 @12:46:18）⇒ ⚠ **自指仍在册**（同 `D-⑱-2` 那一族）：这几句自己的字节数写完又变，结构凭证只认写后终检那两格 —— `TOTAL FLAGS 0`（`tools/ledger_table_report.txt` 现读 `AI_ITERATION_STATE.md 行数=3888 flags=0`）与 DA 的 `新增=0 消失=0 / 286`；三态分写、不混写、不用百分比；明细 = **D-056**，教训 = **§八 ㉟**）
                **SOURCE_COMPLETE**：本轮买的是**"高德的车道帧第一次只从 javap 常量清单抄、两个数组不许按下标配对，而'没有车道'被拆成四件不同的事"**；UI 未扩（正式导航卡片只多**车道** 1 格读数，落在既有那段"高德实时回执（最近一帧）"里 —— **无新按钮 / 无新屏 / 无新路由**）。
                  ① **取证三张表 + 两处读不到**：`LaneAction` **27** 个 `public static final int` = `0..25` **+ `LANE_ACTION_NULL = 255`**，⚠ **不是 0..26**（`26..254` 在这颗 jar 里根本不存在，而 **255 正是"这一格没有车道"这个语义本身**）；车道**话术表 22** 条 = 比名字表**少 5 条**（`13/14/15/17/20` 那五个名字本机解释不了，表里留 `null` 由页面念"高德给了一个我们解释不了的车道码（N）"）⇒ **27 与 22 两数不等是有意的**，判据 N-z 把两个数**分别**钉住，谁把它们"补齐成一样"就红；`AMapLaneInfo` 的字段与数组形状来自 javap，`showLaneInfo` **2** 个重载（单组 / 数组）＋ `hideLaneInfo` **1**；`AMapNaviView` 车道相关方法数 **0** ⇒ 内置视图那条路自己画车道、**不经过**我们这一层，本轮没有把它接进来（接进来等于给"页面造车道帧"开第二条口）。⚠ **两处读不到语义**：数组重载里那两个 `byte[]` 到底是不是"第 i 格是否推荐"，以及 `setLaneTypeId` / `isRecommended` / `getLaneTypeIdArray` 那一族 —— **没有任何一份本机证据能回答** ⇒ 代码**不消费**，只做"两个数组长度是否相等"这一件事（`countsAgree` 相等也只说"对齐了"，不说"哪条道推荐"），登记 **`D-⑩-1`**。
                  ② **形状（三层各只干一件事）**：判据层（现读 **707** 行 / 33,859 B）= `data class LaneFrame` ＋ `sealed interface LaneDisplay { Single / Groups / Cleared }` ＋ `laneActionNameOf` **27** 行 + `else -> null` ＋ `laneActionSpeechOf` **22** 行 + `else -> null` ＋ `laneCellLabel`（**一个码 → 一句话**，三格共用）＋ `laneDisplayTextOf`（**一帧 → 一句话**）＋ 接口新增 `lane: StateFlow<LaneFrame?>` —— 四个"没有"必须是**四个不同的类型**而不是一句 `null` 加注释：`null` = 本机没收到过车道帧、`Cleared` = 高德主动说这没有车道、`Groups(n)` = 收到多组但不替用户挑、`Single` = 单组，并成一个就是 §八 ㉛"没问到 ≠ 没有"在车道层的复发。SDK 层（**414** 行）只搬运：两个 `showLaneInfo` 重载**都**接、数组重载**不**取 `first()` 冒充单组、两个数组**不** `zip` / `forEachIndexed`、`hideLaneInfo` 写 `Cleared`、`stopNavi()` 清帧（**1** 处）；页面（**343** 行）只 `collectAsStateWithLifecycle()` 订阅并调那**唯一**一个话术出口，开关关着时**那一格不出现**而不是显示成"高德说没有"。替身引擎的 `lane` 是常空 flow、不产生任何 `LaneFrame` ⇒ 没有第二条话术。
                  ③ **新建测试类 0、既有簿增补 8 例**（37→**45**）：名字表逐条对 javap（255 必须在表内、26 必须不在）/ 表外一个不编 / 解释不了那五个码不许被硬翻成人话 / 两个数组不配对不补齐 / "本机没收到" ≠ "高德说没有" / 多组不替用户挑 / 长度不一致要能指出来 —— 八例各钉一种坏事；用例那本现读 **760** 行 / 39,027 B，⚠ **它没有开工前读数**（09:48 那份备份 `n4_before/` 只存了三本源文件）⇒ 这一格**只报现值、不报差**，"加了 8 例"由 XML 现读 45 与上面那条清单同 md5 承担。同轮行为负控 **M19…M26** 红 **1/1/1/1/1/2/1/1** 例（M24 一次注入打破两条断言所以是 2，不是"每条注入大约红一例"的心算），八条跑完 `✔ 恢复核对：与改前基线逐字节相同`，`[恢复后复跑] exit=0 failures=0/45`。
                  ④ ⚠ **两处作用域结论，不许读成全工程结论**：清帧口径 `stopNavi()` **清** / `release()` **不**清（两个口径不是同一句话）；"正式导航面 0 台本机 TTS"仍不等于全工程 0 台（旧屏那 5 行 D-003 的形状本轮未动，沿用 D-055 §7 第 4 条）。
                **BUILD_VERIFIED**：debug variant **47 类 / 487 例 / 0 failures / 0 errors / 0 skipped**（`testDebugUnitTest --rerun-tasks` → rc=0 / `BUILD SUCCESSFUL in 1m 12s` / `24 actionable tasks: 24 executed`，47 份 XML 的 mtime 集合唯一 `['12:00:15']`）；**与批次前那一遍的对照（本轮新记的一手）**= 10:36 那遍的逐类清单与终值清单**逐字同串**（同 md5 `31edc716c3ee3dbc366c71fd5cb4a81a`、同 487 例）⇒ "25 本负控批没动源码内容"除了树指纹，还有一道**独立**凭证（用例集合本身）。对 D-055 基线（47 类 / 479 例）：类数不变、总数 **+8** 且完全落在 `NavigationContractTest` **37→45**；⚠ **不是逐名比对** —— D-055 只落了清单 md5，逐类清单文件在上一轮 `/tmp` 里已没了，"无新增类/无消失类"是由"类数相同 ＋ +8 被这一行完全解释"**闭合推**出来的；本轮起把逐类清单**落档**（`n4_final_list.txt`），下一轮做得成逐名差。
                  ⚠ **release variant 本轮未跑**：`D-⑱-1` 那 **5** 例红的成因仍在，且本轮引用的是**上一轮读数**（只跑 debug），而 `assembleRelease` 另被 HTTPS 闸在配置期挡住 —— 两件事不许并成一句"release 也好了"。
                  **lint**：txt **31,452 B** / 末行 **`0 errors, 46 warnings`**，XML **98,753 B**，`<issue>` **78** = Warning 46 ＋ Information 32，Top `AutoboxingStateCreation 32 / DefaultLocale 10 / UnusedResources 10 / HardcodedText 8 / GradleDependency 7` ⇒ **与 D-055 终值逐项相同**；⚠ 本轮改了源码，"没涨"是实测结论而不是预期。
                  **打包**：APK **113,912,212 B @12:09:29 / sha256 `161f34abc383c6b8e23322e4136fa69e619f8cf03e6e412b621a34f42dda1736`**。⚠ 对上一包**字节数完全相同（+0 B）而 sha256 变了**：上一轮那一格是 +16,384 B、本轮是 +0 B，两轮都改了源码 ⇒ 这是 §八 ㉞ ⑨"打包/dex 对齐量子"那条**假设**的第二个读数，本轮**仍没做差量实验** ⇒ 体积继续登记为**未归因**（`#140` 不闭环）；本轮真正归因得动的是类计数（下一行）。
                  **dex 与类清单（差集做得成）**：**16 dex / defined 29,835 → 29,842（+7）/ referenced 36,690 → 36,703（+13）/ 去重 31,901 → 31,908（+7）**；**方法学验证在前**：拿留存的上轮包重导 = **1,359 条 / md5 `849f8708fecd93a6c8c7c8b4532bc186`**，与 D-055 §5 记的两级读数**逐字同串** ⇒ 提取器有资格报差集；新包 **1,366 条 / md5 `30c488351f942f5bdd7edba309872c96`**，差集 **出=0 / 入=7**，七条**全部具名**（`LaneFrame`、`LaneDisplay`、`LaneDisplay$Cleared`、`LaneDisplay$Groups`、`LaneDisplay$Single` ＋ 判据层文件门面那两个脱糖 lambda 壳）：本轮新增具名类型 **5** 个 ⇒ 应当 +5 起步，多出的两条**归到了源码写法**（不是猜）—— `laneDisplayTextOf` 里两处 `joinToString("、") { laneCellLabel(it) }`，selector 是 `crossinline`，编译器留函数对象、D8 再脱糖成壳 ⇒ **defined 的 +7 逐条指到名字，本轮没有新增"未归因"**（`D-⑧-1` 是**历史**那一半，仍不闭环，本轮没碰）。⚠ **本轮新加的一手机制**：打完包立刻把这一包复制留档（`n4_apk_retained.apk`，现读 113,912,212 B）⇒ "上一版包被下一次 assemble 覆盖"这个根因不再复发；`#140` 与 `D-⑧-1` 的历史那一半之所以闭不了环，缺的就是这一手。
                  **判据面**：`ls tools/check_*.sh` = **23** 本，终值树逐本 **rc=0**（留档 `n4_probe_check_*.log` 共 **23** 份）；导航簿 N-a `45`（下限同轮抬到 **≥45**，抬的依据是 §上那八例）、N-j `10/0/0`、N-z 八组读数见下面判据那一格、N-y 四行照旧；authenticity rc=0：窄 **10** / 宽 **49** / `GeoMath` 27 / `DemoData` 文件 6 / 假步进 **0** ⇒ 与 D-055 终值**逐项相同**，且宽口径按文件构成里判据层仍 **3**、SDK 层仍 **2** ⇒ **本轮新增的车道话术没有引入 `straight` 一族关键字**；`v21_requirement_census.sh` rc=0 / `SUMMARY｜rows=119`，V21-08 四行（`NavigationService` 0/0/0、`NavigationSession` 18/3/18、`NavigationEngines.create` 1/1/1、`engine.release()` 1/1/1）与 V21-09 三行（`AcceptedRouteSession` 6/2/4、`routeFingerprint` 6/2/3、`startGpsNavi` 8/3/5）**一字未变** —— 车道那一格不进这三个符号，所以"没变"是量出来的。
                  **判据扩建（N-z 一组，结构注入 18 条全命中）**：帧类型/显示态/话术出口/搬运口 `1/1/1/1` · 名字表/话术表/表外收尾分支 `27/22/2`（含"名字表少一行 27→26"、"多一行 27→28"、"话术表多一行 22→23"、"少一行"、"整块没了 22→0"五支）· `showLaneInfo` 重载 / `hideLaneInfo` / 写入点 / `Cleared` `2/1/4/1`（＋ 写入点 4→5 = 有人在本机造车道帧、`Cleared` 不再只由 `hideLaneInfo` 写、数组重载被 `first()` 冒充单组三支）· 数组重载只数组数/取 first `1/0` · **按下标配对 `zip`/`forEachIndexed` = 0** · 页面订阅/页面造帧/唯一话术出口/车道开关/猜图形那台表 `1/0/1/1/0` · lane 由 flow 承载 `2` · 表外那一格的字面 `1`。⚠ **N-j 9→10 与新增 `AMapLaneInfo` 是同一轮改的**，白名单只加了这一个名字 —— **没有**为了让 N-j 保持 9 而改用全限定名绕过 import（那等于把这条判据换成一条不会响的）。
                  **负控面（负控在前、终值在后）**：25 本全批 10:38:48→11:54:45，rc=0 **23** / rc=2 **2**，聚合 ✔ **532** / ✘ **2** / △ **0** / ◦ **14**（**逐本行相加**，`共 25 本` 同遍核）；那两本 rc=2（`notify_result`、`oss_credits`）末行都是"✘ 真身没有 `app/build/test-results/…`"—— 前面 `--tests` 过滤把 XML 目录清剩两三份，两本**拒绝编读数**、直接 exit 2 ⇒ 这两条 ✘ **不是"判据通过"**；两本还原后**单本复跑**（跑在终值全量**之后**，依据 = 现读这两本各自 `gradlew` 调用 **0** 次 ⇒ 不会重跑用例，不违反"负控在前"）`notify_result` rc=0 / `破口=12 反向=2 预检=2｜命中=16 失效=0`、`oss_credits` rc=0 / `破口=13 反向=2 预检=4｜命中=19 失效=0` ⇒ 账面口径写"**23 本批内绿 ＋ 2 本单本复跑绿**"，**不写"25 本全绿"**。本轮新增注入所在四本：`navigation_negative_controls.sh`（现 **451** 行 / 24,551 B）批内 rc=0 / ✔=**78**、`navigation_test_negative_controls.sh` ✔=**28**（M1…M26 ＋ 基线 ＋ 恢复）、`runcatching_negative_controls.sh` ✔=**20**、`throwable_negative_controls.sh` ✔=**19**。⚠ **与 D-055 不同的一格**：`doc_anchor_negative_controls.sh` 本轮**在批内就 rc=0 / ✔=8**（D-055 那一遍它是 rc=2）—— 差别不是运气，是本轮把跨簿重锚做在批次**之前** ⇒ **顺序本身被这一格证明了，不是靠事后补**。⚠ 抄计数只能抄批次行／`SUMMARY` 行，不能拿 `grep -c` 那个字符当本数（那本簿自己的收尾句里也带它）。
                **本轮自己付的第二笔"锚点账"，以及一笔在账上多出来的自纠**：本轮改源码文本 ⇒ 同轮核所有簿（D-055 §㉞ ④ 那条规矩的**第二次兑现**）。受影响的另一对仍是 `runcatching`：`docs/android/RUNCATCHING_AUDIT.md` 里 **9** 行行号（203→250、161→208、263→310、273→320、274→321、283→330、300→349、306→355、346→395）＋ 顶部那句括注 ＋ 簿子里 **2** 条 `drop_line` 字面串与 **2** 条 `ctl RC-a` 描述（154 行 / 18,303 B 与 216 行 / 13,145 B，**行数一字未增删**）；台账指针重锚 = `DECISION_LOG.md` **3** 个数值写在 **4** 处 ＋ `CONTEXT_LOCK.md` **1** 处，写前整本备份（`n4_anchor_fix/`）、写时带命中次数守卫（预期 2/1/1，不符不落盘）、写后临时文件 ＋ `os.replace` ⇒ 两本行数 **3,925 / 1,550 一字未增删**。重锚后 `check_doc_anchor_probes.sh` rc=0：DA-a `records=1239 OK=938 distinct aid=988` · DA-b 越界 0 / DUP 0 · DA-c `15`（上限 15）· **DA-d 在册=262 新增=0 消失=0** · **DA-e `286`**（等于闸值、未涨）· DA-f `580 / 279`。
                  ⚠ **已在册但本轮故意没动的旧锚**：走查现读页面 **6** 条、SDK 层 **4** 条、判据层 **2** 条指针的行号**在开工前就已错位**（例：某条说旧屏那一支在第 68 行，真身读第 71 行）。本轮**没有**"顺手改对"：改对会让 DA-d 的"在册锚点消失即红"亮起来并需要同轮 `--pin-write` 下调在册表 ⇒ 那是 **#159 的活**，混进本轮就变成"顺手把账面改平"（这一格 12 条已在 §九 立项行里写实在册）。
                  ⚠⚠ **一次量具坏了的自纠**（真实发生，不写成"没发生"）：早先用 `grep -c` 配一个 CR 字符量行尾，对判据层报过 **707**，而同一个量具对另一份已知含 **3** 个 CR 的文件报 **0** —— 两个相反方向的错都出现了 ⇒ `grep` 在本机 Git Bash 下**不是**行尾计数工具；改 python 字节计数重量：四个源文件 **CRLF=0 / 孤立 CR=0**。早先记的"判据层 34,547 B"与终值 **33,859 B** 差 **688 B**：三次树指纹同串证明**这一变化发生在批次之前**、行数 707 未变、判据与话术串逐条现读都在 ⇒ 归因为编辑工具重写时把**混合行尾归一**（某次 python 写入没带 `newline=''`）。**结论不是"字节数错了没关系"，而是：同一个量不许混用两个量具，跨量具出现的差必须先复现再落账**（已并进 §八 ㉟）。
                  ⚠ **D-056 落档那三遍里另有四处是我自己现读抓出来的**（都是"账面写得比证据齐"那一族）：① §9 引了一条不存在的编号"NZ18"，真名是 **N-z19**（而 N-z18 钉的是「我的」那台车道开关）；② §4 那格把 27→26 的结构注入写成"把 255 念成表外"，簿子里落的其实是删掉 `25 -> "LANE_ACTION_TIDAL"` 那一行，**255 那一支是行为负控 M19**；③ §6 跨簿那格没点名是哪两本，按 `check_runcatching_probes.sh` 的 mtime（10-04 23:34:09，本轮未动）把"判据册本身没被改过"这条钉死后才补上文件名；④ §0 用例那一格的"行数不变而字节涨"**没有产物支撑**（本轮唯一可核的开工前基线 `n4_before/` 里没有测试源集那本），已删成"只报现值、不报差"。四处都在**同一物理行内**改、行数 **4,052 一字未增删**（现读 560,363 B / CR 0）。
                **DEVICE_VERIFIED**：**未到位**（`bash tools/verify_device.sh --no-build` → **rc=1 / DEVICE_UNAVAILABLE**，`adb` 设备列表为空）⇒ 整体记 **`ANDROID_N3_⑩_PARTIAL`**，不写百分比。本轮全部结论只在 JVM 层与 javap 文本层成立。**特别写明四条无设备证据**：① 真实道路上高德**先给哪个** `showLaneInfo` 重载（两个都接了，一次实机样本都没有）；② `hideLaneInfo` 的实际**时机与频次**；③ `LaneAction` 的**取值分布**（今天只有表、没有样本）；④ 那两个 `byte[]` 到底对应什么（`D-⑩-1`）⇒ SDK 层那本文件整本仍只由 grep 判据 ＋ 结构负控钉住，一律 `NOT VERIFIED ON DEVICE`。证据 `docs/device-evidence/20261005-121526/`（本轮跑了 12:14:57 与 12:15:26 **两遍**，终值只引用后一份，前一份**不并计**）。
                **在册不当已解决（OPEN_DEFECT BEFORE = AFTER － 1 的反向，不写清零、不写百分比）**：本轮**新增 1 项 `D-⑩-1`**（那两个数组的语义与 `setLaneTypeId` / `isRecommended` / `getLaneTypeIdArray` 那一族**无证据、0 真机样本** ⇒ 代码不消费它，只核长度）。BEFORE = D-055 终值 **10** 项 ＋ 3 格，AFTER = **11 项 ＋ 3 格**：`#140`、`#159`、`D-⑧-1`、`D-⑧-2`、`D-⑭-1`、`D-⑰-1`、`D-⑬⑭⑯⑰-1`、`D-⑱-1`、`D-⑱-2`、`D-⑱-3` ＋ **`D-⑩-1`** ＋ DEVICE / RELEASE / GIT。⚠ 两条**只登记、不判平**：APK 体积 **+0 B 仍未归因**（并入 `#140` 的"体积"半边，不做差量实验就不写归因）；**release variant 本轮未复测**（`D-⑱-1` 那 5 例仍是上一轮读数，这是**明知故用的旧数**，必须在册上写明而不是默默沿用）。
                **硬规则自查（逐条实测）**：源码树 `*.jks` / `*.keystore` = **0**（签名备份在工程外 `../buspost-signing-backup`）；main 里 `127.0.0.1` / `localhost` = **0**；`Bearer ` 命中 **2**（`Api.kt` 两处 `header("Authorization", "Bearer $it")` **变量插值**，不是字面量凭据）；HTTPS 闸门原文仍在 `app/build.gradle.kts` 第 **171** 行、本轮**未传** `-Pbuspost.allowInsecureRelease`；`git rev-parse --is-inside-work-tree` = `fatal: not a git repository` ⇒ **GIT=ENVIRONMENT_BLOCKED，没有 `git init` 造假历史**；声明坐标逐条现读仍 **20** 条（`compose-bom … mockwebserver` 一条不多一条不少）、jar 仍是**同一颗** navi-3dmap 10.0.600（50,991,398 B / sha256 `9fa3cfb8…` 与 D-055 引用串一致）、`app/build.gradle.kts` 11,008 B @09-30 00:40:50 与 `gradle.properties` 920 B @09-29 02:29:42 **一字未动** ⇒ **无新增依赖、无新增第三方构件**；`tools/*.py` **10** 本 / `tools/check_*.sh` **23** 本 / `tools/*_negative_controls.sh` **25** 本；包级证据 `check_oss_credits_probes.sh` rc=0（`OS-d2 0/0/28`、`OS-e` 末项打包类 **29,842** ⇒ 许可证那张表对的是**当前这一包**，不是上一轮的数）；本轮**没有自己造过一组车道**、**没有按下标猜配过两个数组**、**没有把表外车道码念成一句真车道话术**、**没有在正式导航面加本机 TTS**（这四条分别是 §4 结构注入与 §3 行为注入打红的对象，不是表态）。
                **NEXT**：**下一轮 = 赛道⑪ 偏航重算（队列 #215 / N-4）**。那一族的签名取证本轮已顺手做完并留档（`/tmp/n5_yaw_javap.txt`，**39** 行：`onReCalculateRouteForYaw()` **无入参**、另有 `onReCalculateRouteForTrafficJam()`、`reCalculateRoute(int)`、算路成功/失败各 **2** 个重载），**代码一字未动** —— ⚠ 动它要把 SDK 层的回调数与 **N-j 白名单同轮一起抬**，而这两轮已经各付过一次这种重锚账单（D-055：3 处台账 ＋ 2 本其它簿；本轮：4 处 ＋ 1 处 ＋ `runcatching` 那 9 行）⇒ **下一轮开工前先数一遍"有多少簿的锚点指着 `core/navigation` 那两个文件"，再决定动不动**。
                  其后按顺序：**#216 赛道⑫⑬ 3D 任务 Marker / 3D 车辆**（纯视觉层）→ **#217 赛道⑭ 离线真实道路缓存**（provider ＋ 抓取时间；⚠ 今天 `N-x 0/1` 说明这份缓存**只写不读**，`D-⑱-3` 与它同族）→ #218 赛道⑮ MapLibre / Valhalla（依赖取证优先，编译困难就 BLOCKED）→ #219 赛道⑯ UI 细节（最后做，不扩 UI）。
                  ⚠ 队列里的**在册项**不因本轮收口而消失：`#159`（本轮又把它的具体度抬了一层 —— 那 **12** 条历史错位锚已在册）、`D-⑱-1`（那五本演示面用例的 variant 闸门）、`D-⑱-3`、`#140`（APK 历史与体积两半）、`D-⑧-1`、`D-⑧-2`、`D-⑰-1`、`D-⑬⑭⑯⑰-1`、`D-⑱-2`。本块同样**不新增 `文件.kt:行号` 形态的锚点**（行号一律写成"第 N 行"）⇒ #159 那笔重锚债不因本轮记账变大（终检 DA-e 仍 `286`）。
CURRENT_ANDROID_STATUS:  赛道⑨（N-2）收工（2026-10-04 22:0x – 10-05 02:0x；写入顺序旁证（**不当改动面证据**，工程无 git）：`AmapNavigationEngine.kt` 22:54:13 → `DriverSdkNavCard.kt` 23:02:19 → `NavigationContractTest.kt` 23:03:54 → `check_navigation_probes.sh` 23:18:36 → `navigation_test_negative_controls.sh` 23:20:17 → `runcatching_negative_controls.sh` 23:21:30 → `navigation_negative_controls.sh` 23:23:04 → `check_runcatching_probes.sh` 23:34:09 → **`NavigationEngine.kt` 00:29:49**；⚠ 最后那个 mtime **不是本轮内容写入时刻**：M16…M18 三条注入落的正是这本（脚本头部 `NE=` 指向它、`trap` 与 `restore()` 从备份 `cp` 回真身），而那本簿 00:26:18 起、212 s 后结束 ⇒ 00:29:49 是**还原写回**（与 ⑬⑭⑯⑰ 那遍 `Repo.kt` 13:08:00 同一形状）—— 只能说"改过"，不能说"改成什么"；改动面主凭证 = 内容指纹 `files=162 sha256=5fa87a2dd4c49657829e07936bcd8b79d993a473ac3354583ca38bd93537a772`，对上一轮的 `42863d1c…` 是**同文件数、不同内容串**（本轮改 4 个 `app/src` 文件、**新增 0 本** ⇒ 若真加了源文件，`files` 必须涨到 163）；终值全量 00:55:08、lint+打包 01:12:50→01:15:01（**另起一条命令**，与 `test` 那条分开）、设备闸门 01:17:54、D-055 落档 01:30:57、状态板＋DONE 01:50:22、§八 ㉞＋§九 01:53:32、V21-08/09 就地复测 01:55:32、NOTICES 末段 02:04:29（**含一处自纠**：那份 `.pyc` 的日期抄成了本轮日期，实测应为 2026-10-02 10:56:00，已在同一物理行内改正、86,256 B / CR=1 / LF=665 一字不变）、D-055 的 §10（台账落档这一段自己的账）02:08:41、写后终检末遍 02:08:59（`TOTAL FLAGS 0`、DA rc=0、anchors 1239 / ID-MISS 286 未涨）；三态分写、不混写、不用百分比；明细 = **D-055**，教训 = **§八 ㉞**）
                **SOURCE_COMPLETE**：本轮买的是**"高德的转向码与播报句子"第一次只从 javap 常量清单抄、表外一格都不编**；UI 未扩（正式导航卡片只多**转向 / 播报**两格读数，且都落在既有那段"高德实时回执（最近一帧）"里 —— **无新按钮 / 无新屏 / 无新路由**）。
                  ① **取证两张表**：`IconType` **57** 个 `public static final int` = `0..54` 连号 **+ 65（MERGE_LEFT）+ 66（MERGE_RIGHT）**，⚠ **不是 0..56**（`55/56/57..64` 在 navi-3dmap 10.0.600 里根本没有）；`NaviTTSType` **6** 个 = `1..6`（含 `NAVI_YAW = 6`）；同一份 javap 日志里 `public static final int` 共 **63** 行 = 57 ＋ 6，两个数各被 N-y 钉住。
                    ⇒ "按 0..56 生成一张连续表"**能编译、也能过话术用例**，只在"值域逐条对 javap"这一层才露出来 —— 这就是 M17 拿**表外的 55** 去验的理由，也是这一族**第一个可复跑证据**。
                  ② **形状（三层各只干一件事）**：判据层两张纯表 + `else -> null`（`null` 逼页面自己说出"不在这张表里"，于是"编一个"在类型上就写不出来）＋ 新类型 `NavigationVoice(typeCode: Int?, text: String)`（类型码可空 = 高德那个重载**本来就不带码**，不许把"没带码"读成某个具体类型）＋ 接口新增 `val voice: StateFlow<NavigationVoice?>` ＋ `startGpsNavi` **多一个入参** `voiceOn: Boolean`（**无默认值、无重载** ⇒ 只有这一个出口）；SDK 层两个 `onGetNavigationText` 重载**都**转发成 `NavigationVoice`（构造点 **2**）、`stopNavi` 清帧（**1**：留住最后一句 = 高德已经不说了而页面还在说）；页面 `collectAsStateWithLifecycle()` 订阅、表外印"高德给了一个不在这张表里的转向码（N）"、**开关关着**时印"本机没让高德播报（开关在「我的」里）"而**不是**"高德没给"。替身引擎只回 `Blocked` 那一句、`voice` 常空 ⇒ 没有第二条话术。
                  ③ **新建测试类 0、既有簿增补 3 例**（34→**37**）：`转向码表的话术是逐字钉住的` / `IconType 实测值域一个不缺且表外一个不编` / `播报类型码表外与没给都不并入别的类型` —— 三例各钉一种坏事；同轮行为负控 **M16 红 2 例、M17 红 1 例、M18 红 1 例**（"红了 N 例"从 gradle 输出现读，**不是**"每条注入大约红一例"的心算），复原后 `exit=0 / failures=0/37`，并有 `cmp` 逐字节"与改前基线相同"的恢复核对。
                  ④ ⚠ **"正式导航面 0 台本机 TTS"是作用域结论，不是全工程结论**：main 全文检索 `TextToSpeech` 命中 **7** 行 = **5** 行在旧屏 `DriverNavScreen.kt`（DEBUG ONLY 那台，D-003 立的，本轮未动）＋ **2** 行是两份导航源文件里"本机不建 TextToSpeech"的**注释** ⇒ 把 7 读成"本轮违背了自己的红线"是错的，把 7 藏起来不抄也是错的。
                **BUILD_VERIFIED**：debug variant **47 类 / 479 例 / 0 failures / 0 errors / 0 skipped**（`testDebugUnitTest --rerun-tasks` → rc=0 / `BUILD SUCCESSFUL in 1m 11s` / `24 actionable tasks: 24 executed`，47 份 XML 的 mtime 集合唯一 `['00:55:08']`）；**逐类差**（本轮清单 md5 `5d359a03ef077d40eb61f951fa3a7669` 对基线 `aa4f516f3333ee68543148a1f60e3eb6`）= 类数不变、无新增类也无消失类，`diff` **只有**一行 `NavigationContractTest` 34→**37** ⇒ +3 逐条归因、没有余数。
                  ⚠ **release variant 本轮未跑**：`D-⑱-1` 那 **5** 例红的成因仍在（五本演示面用例自己没有 variant 闸门），而 `assembleRelease` 另被 HTTPS 闸在配置期挡住 —— **两件事不许并成一句"release 也好了"**。
                  **lint**：txt **31,500 B** / 末行 **`0 errors, 46 warnings`**，XML **98,809 B**，`<issue>` **78** = Warning **46** ＋ Information **32** / Error **0**，Top `AutoboxingStateCreation 32 / DefaultLocale 10 / UnusedResources 10 / HardcodedText 8 / GradleDependency 7` ⇒ 与 D-054 终值**逐项相同**；⚠ **本轮改了源码**，所以"没涨"是**实测结论**而不是预期（D-054 那一遍是因为没改源码才预期相同）。
                  **打包**：APK **113,912,212 B @01:13:50 / sha256 `96d7904b4552e9cd2a0997f4aae94a1f4705b398a407d26ceca4d0e211c4128e`**，对上一包（`a00e1f930404a8960f2e984f1904aa8d064ed12542236bd03ff2273c28b46721`）为 **+16,384 B** —— ⚠ D-044→D-045 那一格**也是 +16,384 B**，两轮源码毫不相干却是同一个量级 ⇒ 更像打包/dex 的对齐量子而不是"本轮代码的体积"；本轮**没做差量实验** ⇒ 体积这一半登记为**未归因**（不写体积优化、也不写回归），`#140` 的历史那一半仍未闭环。
                  **dex 与类清单（旧包还在 ⇒ 差集做得成）**：16 dex / defined **29,834 → 29,835** / referenced 36,688 → 36,690（去重 31,900 → 31,901）；**方法学验证在前**：拿留存的上轮包 `a00e1f93` 重导 = **1,358 条 / md5 `19f4bf8e9493e7ba184a7b4c65c8e1ed`**，与 D-053 记的两级读数**逐字同串** ⇒ 提取器有资格报差集；新包 **1,359 条 / md5 `849f8708fecd93a6c8c7c8b4532bc186`**，差集 **出=0 / 入=1**，那一条就是 `com/shanxiang/buspost/core/navigation/NavigationVoice` ⇒ **本轮 dex 增量逐条指到描述符**（把 D-053 那句"没有新类型就不该有增量"反过来用一次：**有**新类型时增量必须指得到名字，指不到就是计数与源码脱钩）。⚠ 我第一次读这一格把 defined 差算成 **+6**，原因是拿了 D-053 时代**更早**那一格 29,829 当基线 ⇒ **跨轮抄的每个数都要先确认它是哪一遍、什么口径**（§八 ㉜ ⑤ 同族，本轮第二次踩、这次是自己抓自己）。
                  **判据面**：`ls tools/check_*.sh` **23** 本（22 本 `*probes.sh` ＋ `check_authenticity_counts.sh`）终值树逐本 **rc=0**（留档 `/tmp/n3_probe_*.log` **23** 份）；导航簿末行"第 8 步导航引擎判据通过"，N-a `37`（下限同轮抬到 **≥37**，抬的依据是 §上面那三例）· N-j `9/0/0` · N-u `1/1/3` · N-v `1/1/1/1/7` · N-w `0/1` · N-x `0/1` · N-y `57/6` · `2/2/1/0` · `1/1/2/1/1` · `2`；authenticity rc=0：窄 **9 → 10** / 宽 **48 → 49** / `GeoMath` 27 / `DemoData` 文件 6 / 假步进 **0** —— ⚠ 那两个 **+1 是同一处、且是本轮自己造的**：转向表的**注释行**逐字抄了 SDK 常量名（含 `STRAIGHT = 9`）被 `straight` 命中 ⇒ **不是**直线回退复活，也**没有**为它加白名单（加了就等于把这条关键字计数调松）；`v21_requirement_census.sh` rc=0 / `SUMMARY|rows=119`，⚠ 其中 `NavigationSession` 那一格 `hits=18 / files=3 / **code 17 → 18**`：+1 由**换行**造成（替身引擎那一支原本**一行含两次出现**，加了 `voiceOn` 入参后折成两行 ⇒ 出现次数不变、生效行 ＋1），**不是新增使用点** —— 证据 = 负控沙盒留下的上一轮副本 `/tmp/nw_neg.49233`（405 行那一版）逐行比过；V21-09 三行（`AcceptedRouteSession` 6/2/4、`routeFingerprint` 6/2/3、`startGpsNavi` 8/3/5）**一字未变**（签名多一个入参不改这三个数）。
                  **负控面（负控在前、终值在后）**：25 本全批 23:43:01 → **00:53:56**，rc=0 **22** / rc=2 **3**，聚合 ✔ **498** / ✘ **3** / △ **0** / ◦ **14**（逐本行相加，`本数=25` 同遍核）；那三条 rc=2 **都不是"判据通过"** —— `doc_anchor`（自身基线闸红，见下面"锚点账"那一格）、`notify_result` 与 `oss_credits`（批跑时 `test-results/testDebugUnitTest/` 里只有 **2** 份 XML，是前面 `--tests` 过滤留下的 ⇒ 两本**拒绝编读数**、直接 exit 2）；三本还原后**单本复跑 rc=0**（doc_anchor **8** 个 ✔ = DA-a…DA-f 六条破口 ＋ POS；notify 命中 **16** / 失效 **0**；oss 命中 **19** / 失效 **0**），依据 = 实测这两本各自调 `gradlew` **0** 次 ⇒ 复跑不会重跑用例、不违反"负控在前" ⇒ 账面口径写"**22 本批内绿 ＋ 3 本单本复跑绿**"，**不写"25 本全绿"**。本轮新增注入所在四本：`navigation_negative_controls.sh` ✔=**60**（新 13 条）、`navigation_test_negative_controls.sh` ✔=**20**（M1…M18 ＋ 基线 ＋ 恢复后）、`runcatching_negative_controls.sh` ✔=**20**、`throwable_negative_controls.sh` ✔=**19**。⚠ 抄 ✔ 只能抄 `SUMMARY`／批次行：`navigation` 那本的日志里 `✔` 字符出现 **61** 次，多出的一个是簿子自己那句"上面每行都必须是 ✔"。
                **本轮自己付的一次"锚点账"**（"改了源码文本 ⇒ 同轮核所有簿的注入锚点"这条指令第一次由**我自己**买单）：批内 `DA-d 在册=262 新增=1`、`DA-e ID-MISS 287`（闸 ≤286），红的 aid 指着 SDK 层"启动前重读路径"那一支的第 **241** 行 —— 本轮给那层加了播报转发与清帧，那一支移到了第 **263** 行；`aid` 的口径 = 文档名 ＋ 锚点原文，而锚点原文**含源码行号**（不含台账行号）⇒ 同一条指针**同时**算"新增可疑"（DA-d）和"ID-MISS 回涨"（DA-e）。处置 = **三处就地重锚**（同一物理行内改、不裂表：241→263 在两本台账共 **3** 处、`RouteBinding` 245→**266**、`startGpsNavi` 接口行 400→**519**），写前整本备份、写时带命中次数守卫（预期 2/1/1，不符就不落盘）、写后经临时文件 ＋ `os.replace` 换入 ⇒ 两本行数 **3,805 / 1,511 一字未增删**，终值 **DA-d `在册=262 新增=0 消失=0` / DA-e `286`** 回到闸内。
                  ⚠ **没选的两条路**：① 删掉那条指针 —— 撞 DA-d 的"消失即红"，删等于抹证据；② 把闸值从 ≤286 调到 ≤287 —— 那是把判据改松，正面撞 §八 ㉚"下限只能实测、不许为变绿而改判据"。同轮被牵连另两本（`check_runcatching_probes.sh` / `runcatching_negative_controls.sh`）也已就地重锚并复跑 rc=0：RC-a `31/16/48/23`→**`32/16/49/23`**、RC-b2 `31/0/98`→**`32/0/98`**、RC-e `35/4`→**`36/4`**（RC-a2 11、RC-b 0、RC-c `1111`、RC-d `0/0` 未变），**没有动过任何一条判据的目标值**。
                **DEVICE_VERIFIED**：**未到位**（`bash tools/verify_device.sh --no-build` → **rc=1 / DEVICE_UNAVAILABLE**，0 台在环，新证据 `docs/device-evidence/20261005-011754/`）⇒ 整体记 **`ANDROID_N2_⑨_PARTIAL`**，不写百分比。本轮**三条无设备证据**（写明，免得判据面被读成运行时已验）：① 高德真机到底回调**哪一个** `onGetNavigationText` 重载（两个都接了，本机没有一次实机样本）；② `setUseInnerVoice(true)` 是否真的自己出声（开关单向交给 SDK，效果没人看见）；③ 真实道路里 `IconType` 的**取值分布**（今天只有表、没有样本）⇒ 引擎层那本整本仍只由 grep 判据 ＋ 结构负控钉住，一律 `NOT VERIFIED ON DEVICE`。
                **在册不当已解决（OPEN_DEFECT BEFORE = AFTER，不写清零、不写百分比）**：本轮**未新增缺陷项** —— §7 那四件是**新事实/新口径**（"高德没给"与"本机没让说"必须分开、`IconType` 非连续、行号进 `aid` 是双刃剑、旧屏那台 TTS 还在），不是缺陷。仍在册 **10 项 ＋ 3 格**：`#140`、`#159`、`D-⑧-1`、`D-⑧-2`、`D-⑭-1`、`D-⑰-1`、`D-⑬⑭⑯⑰-1`、`D-⑱-1`、`D-⑱-2`、`D-⑱-3` ＋ DEVICE / RELEASE / GIT。
                  ⚠ 两条**只登记、不判红**的口径：本轮 APK **+16,384 B 未归因**（并入 `#140` 那一族的"体积"半边 —— 不做差量实验就不写归因）；**release variant 本轮未复测**（`D-⑱-1` 的 5 例仍是上一轮读数，这一条是**明知故用的旧数**，必须在册上写明而不是默默沿用）。
                **硬规则自查（逐条实测）**：源码树 `*.jks` / `*.keystore` = **0**（签名材料仍在工程外那一个目录）；main 里 `127.0.0.1` / `localhost` = **0**；`Bearer ` 命中 **2**（`Api.kt` 的 `header("Authorization", "Bearer $token")` **变量插值**，不是字面量凭据）；HTTPS 闸门原文仍在 `app/build.gradle.kts` 第 **171** 行、本轮**未传** `-Pbuspost.allowInsecureRelease`；`git rev-parse --is-inside-work-tree` = `fatal: not a git repository` ⇒ **没有 `git init` 造假历史**；声明坐标仍 **20** 条、`app/build.gradle.kts` 与 `gradle.properties` 一字未动 ⇒ **无新增依赖、无新增第三方构件**（包级证据：`check_oss_credits_probes.sh` rc=0，`OS-d2 0/0/28`、`OS-e` 打包类 **29,835** ⇒ 许可证那张表对的是**当前这一包**）；本轮**未造 Demo 功能**、**未拿纯函数用例冒充运行时半边**、**没有自己合成过一句播报**。
                **NEXT**：**下一轮 = 赛道⑩ 真实车道信息（队列 #214 / N-3）**。车道那族的取证本轮已顺手做完并留档（`/tmp/n3_lane_javap.txt`，**105 行**：`AMapLaneInfo` 字段清单、`LaneAction` **27** 条 = `0..25` ＋ `LANE_ACTION_NULL = 255`、`showLaneInfo` **两个**重载 ＋ `hideLaneInfo` ＋ `onReCalculateRouteForYaw`、以及 `AMapNaviView` 的车道方法数 **0**），**代码一字未动** —— ⚠ 动它要把 N-j 的 SDK import 从 **9** 抬到 **10** 并**同轮重锚那一格**；本轮已经实测过一次这种重锚的账单（**2** 本其它簿的注入锚点 ＋ **3** 处台账指针 ＋ **1** 次批次不成立）⇒ 下一轮开工前先数一遍"有多少簿的锚点指着 `core/navigation` 那两个文件"，再决定动不动。
                  其后按顺序：**#215 赛道⑪ 偏航重算**（只用引擎回调 `onReCalculateRouteForYaw`，移除任何本地阈值判定）→ **#216 赛道⑫⑬ 3D 任务 Marker / 3D 车辆**（纯视觉层）→ **#217 赛道⑭ 离线真实道路缓存**（provider ＋ 抓取时间；⚠ 今天 `N-x 0/1` 说明这份缓存**只写不读**，`D-⑱-3` 与它同族）→ #218 赛道⑮ MapLibre / Valhalla（依赖取证优先，编译困难就 BLOCKED）→ #219 赛道⑯ UI 细节（最后做，不扩 UI）。
                  ⚠ 队列里的**在册项**不因本轮收口而消失：`D-⑱-1`（给那五本演示面用例补 variant 闸门 —— 用例侧机制改动）、`D-⑱-3`（缓存写序与 `routeAvailable` 的先后）、`#140`、`#159`、`D-⑱-2`（把 `.md:NN` 自指纳入判据口径或改成内容锚点）。本块同样**不新增 `文件.kt:行号` 形态的锚点**（行号一律写成"第 N 行"或单独 `:NNN`）⇒ #159 那笔重锚债不因本轮记账变大。
CURRENT_ANDROID_STATUS:  F-3 ⑱⑳ 收工（2026-10-04 16:2x–17:2x；⚠ **本轮 `app/src` 一字未动** —— 开工第一读与收工最后一读逐字同串 `files=162 sha256=42863d1c…`，且与 D-053 终值同串 ⇒ 写入顺序旁证只剩两本新判据册（`check_test_suite_hygiene_probes.sh` 16:56:43 → `test_suite_hygiene_negative_controls.sh` 16:57:12）与本块；"25 本负控全批未复跑"这一格登记的是**有理由的未做**（理由 = 上面那个哈希，不是 mtime、不是"改动面与判据面不相交"的推断），**不构成下轮可以不跑的先例**，明细 D-054 §0；负控只跑了新册那一条（✔=7 / ✘=0 / △=0 / rc=0）；终值全量**拆成两条命令**（`test` 那条被 release 红截断在 `:app:testReleaseUnitTest`，`lintDebug`/`assembleDebug` 只有第二条才真执行）；设备闸门 17:04:06；三态分写、不混写、不用百分比；明细 = **D-054**，教训 = **§八 ㉝**）
                **SOURCE_COMPLETE**：本轮买的是**"不许为凑数新建空测试类，也不许把已有类改名冒充新增"这条历来只写在台账里的约束，第一次变成有闸可咬的判据**；产出形态是**归位表 + 新册**，不是新测试类 —— **新建测试类 0 / 新建判据册 1**。
                  ① **归位表**（D-054 §2，10 行逐本本轮现读，路径与例数都不是回忆）：10 本点名类里 **3 本同名已存在**
                    （`DriverRoleIntegrityTest` core 6 例 / `RouteDomainDeepLinkTest` nav 9 例 / `UserTrackingContractTest` data 13 例）、
                    **6 本点名的那一类缺陷早已落在 10 本别名类里**（`NoProducerKeyFamilyContractTest` 7 ＋ `DistanceCaliberContractTest` 4 / `PolylineDecisionContractTest` 15 /
                    `DriverActionHonestyContractTest` 5 ＋ `DriverLegRespContractTest` 6 ＋ `DriverLegStateMapperTest` 11 / `MissionMapTest` 18 ＋ `MissionRouteStateContractTest` 12 /
                    `NavigationContractTest` 34 里的 readiness 那 11 条与 binding 那 7 条 / `RealRoadRouteValidatorTest` 16 里的 2 条具名）、**1 本不能建**
                    （`RouteCacheGraphVersionTest`：`graphVersion` 与 TTL 三面 **0 生产者**，V21-13 / V21-14 仍 ❌ ⇒ 硬建只能自造键名，那是第四个真相源）。
                  ⚠ 这张表的"例数"是 `@Test` 注解的**源码计数**（与 census 同口径），本轮与 XML 用例数**恰好同为 476** —— 两口径等值是**实测**不是恒等（D-053 §5 记过二者差 12 的时刻）。
                  ② **新册** `tools/check_test_suite_hygiene_probes.sh`（**92 行 / 6,900 B / CR 0**）判据 **SU-a…SU-e**，每个"目标 0"配同机制反证：
                    空壳类 **0**（反证：源集 `.kt` 49 / 其中无 `@Test` 的夹具 **2**）、`@Ignore` 生效行 **0**（反证：`@Test` 注解行 **476**）、点名而未新建的 7 本继续不存在 **7** ＋ 同名 3 本仍在 **3**、归位表落点类存在 **10**；
                    负控 `tools/test_suite_hygiene_negative_controls.sh`（**117 行 / 5,665 B / CR 0**）**P1…P7 全 ✔**。⚠ **P7 一条同时打红 SU-d 与 SU-e** ⇒ "改名冒充新增"在这一族里不是两种坏事，是**同一件事的两面**（撤走证据 ＋ 冒充新增），只钉一面会留另一面。
                    ⚠ SU-d/SU-e 钉的是**名字**，不是内容 ⇒ 归位表里那些别名类的**具名用例与例数**才是实质证据，两件事都要抄，**不许只抄 ✔=7**。
                  ③ **三个否定式新事实**（D-054 §6）：a) `MissionMarkers.paths()` 的第二支（折线 ≥2 且原因非空）**在生产链路里没有生产者** —— 全树 main 对 guide 调 `.copy(` 的生效行 **0**，`routeGuideOf` 里 `accepted` 与 `unavailable` 互斥 ⇒ 原因非空必然折线为空；
                    b) 因此 F-3 ⑧ 留给 `D-⑧-2` 的那个补法（"加一条带折线又带原因的报文"）**不成立**：真实解析器会让 `polyline` 归零（`RouteGuideContractTest` 里 `routeAvailable=false` 那条的断言就是 `polyline.isEmpty()`），只能 `.copy()` 手工搭 ⇒ 造出来的用例**在任何改动下都不会红** ⇒ **MT-4 继续留空**，`D-⑧-2` 性质由"缺行为证据"更正为"**行为证据在当前模型下不可得**"（更正，**不是销号**）；
                    c) F-3 ⑱⑳ 那 10 本与 V21-30 普查那 **21** 名**只有 2 个交集**（`RouteDomainDeepLinkTest` present / `NavigationRouteBindingTest` ABSENT）⇒ 以后写"点名测试类"必须同时写清**是哪份名单**，否则 present 8/21 会被读成"10 本里已落 8 本"。
                  ⚠ 本块行号一律写成"不与文件名相邻"的形式（`routeGuideOf` **:1103** 这种），**不新增 `文件:行号` 锚点** ⇒ #159 那 67 条重锚债不因本轮记账变大。
                **BUILD_VERIFIED（五项实测，终值 = 最后一遍，不是最好看的那一遍）**：
                  ① **全量用例** `./gradlew test`（双 variant，`--rerun-tasks`）→ **rc=1 / `BUILD FAILED in 2m 52s` / 76 actionable tasks: 76 executed**，红在 `:app:testReleaseUnitTest`（= `D-⑱-1`，不是回归）；
                    **debug 47 份 XML / 476 tests / 0 failures / 0 errors / 0 skipped**（mtime 集合唯一 `16:49:40`）、**release 476 / 5 failures / 0 errors**（mtime 唯一 `16:49:52`）⇒ 这一格本轮起**分两行抄**，不许并成一句"全量 476 例"。
                  ② **lint** `lintDebug --rerun-tasks`（第二条命令）→ rc=0：txt **31,452 B @16:53:46 / `0 errors, 46 warnings`**；XML **98,753 B** / `<issue>` 元素 **78** = Warning **46** ＋ Information **32** / Error **0**；
                    Top `AutoboxingStateCreation 32 / DefaultLocale 10 / UnusedResources 10 / HardcodedText 8 / GradleDependency 7` —— **与 D-053 终值那一格逐项相同**（源码未动，"相同"是预期的，仍按实测抄）。
                  ③ **打包** `assembleDebug --rerun-tasks`（同第二条命令，`48 actionable tasks: 48 executed` / `BUILD SUCCESSFUL in 1m 24s`）→ APK **113,895,828 B @16:53:06 / sha256 `a00e1f930404a8960f2e984f1904aa8d064ed12542236bd03ff2273c28b46721`**；
                    本轮 16:49:28 那一包与 16:53:06 那一包**同字节同 sha256**（mtime 不同）⇒ "同输入重打确定性"**第三次**样本，且这次跨了两条 gradle 命令；与 D-053 记的那格**同字节同哈希** ⇒ 本轮**没有体积增量可归因、也没有回归可归因**，**#140 的历史那一半仍未闭环**。
                  ④ **判据面** `ls tools/check_*.sh` **22 → 23 本**，全部在终值树上 **rc=0**（逐本 rc 与末行 `/tmp/f151_probes.log`，23 行）；authenticity rc=0：窄 **9** / 宽 **48** / `GeoMath` **27** / `DemoData` 文件 **6** / 假步进 **0**（逐字同 D-053）；
                    `v21_requirement_census.sh` rc=0 / `SUMMARY｜rows=119` / V21-30 present **8** / ABSENT **13**（名单与账面均无变化）；`python tools/check_ledger_tables.py` **TOTAL FLAGS 0**（写台账前那一遍：五本 3,743 / 3,283 / 1,484 / 3,677 / 615 行）。
                  ⑤ **设备** `bash tools/verify_device.sh --no-build` → **rc=1 / DEVICE_UNAVAILABLE**，新证据 `docs/device-evidence/20261004-170406/`；本机 `adb` 仍不在 PATH（由脚本自己定位 SDK）。
                  ⚠ **两条过程事故**（D-054 §7，都是自己的读数骗自己）：比对 lint 先拿了**更旧那一格**（77 = 46 ＋ 31）据此以为本轮 +1，回查 D-053 终值那格已是 78 ⇒ 差异来自**时刻与口径**，不是源码；`./gradlew test` 那一条被 release 红截断，`lintDebug`/`assembleDebug` 在那一遍里**根本没执行**（日志无这两条任务行），差点写成"一条命令全过"。
                **DEVICE_VERIFIED**：**未到位**（0 台在环，同⑤）⇒ 整体记 **`ANDROID_F3_⑱⑳_PARTIAL`**，不写百分比。
                  本轮**特别要写**这一格：SU-* 与归位表都是**源码文本层面**的判据，"某个状态今天有没有生产者"这类结论**不因它们而变成运行时已验** —— `RouteBinding` 的 `Unbound` 真实发生率、GPS 精度、前台服务续报、深链落地仍一律 `NOT VERIFIED ON DEVICE`。
                **在册不当已解决（OPEN_DEFECT BEFORE/AFTER，不写百分比、不写清零）**：BEFORE = 上一轮终值 **8** 项在册（`D-⑧-2`、`D-⑭-1`、`D-⑬⑭⑯⑰-1`、`D-⑰-1`、`D-⑦⑨-1`、`D-⑦⑨-2`、`#140`、`#159`）＋ DEVICE / RELEASE / GIT **3** 格环境阻塞；
                  AFTER = **10 项 ＋ 3 格**：**新增 `D-⑱-1`**（release 演示面用例红由账面 **3** 复测为 **5**，五条同根因 `IllegalStateException: 生产构建禁止演示数据`、用例侧**无 variant 闸门**；口径是"**漏记了 2 条**"不是"新出 2 条" —— F-3 ②–⑰ 各轮只跑 debug variant，从没复测过 release）；
                  **新增 `D-⑱-2`**（`.md:NN` 这类**台账行号自指不在任何判据口径里**：`check_doc_anchors.py` 的 `ANCHOR` 只匹配 `.kt`/`.java` ⇒ 本轮实测本文件那两处 `:2812`/`:2824`（F-3 ⑤ 就地重锚过的一格）**已经不含** `sourceOf`（现读 `sourceOf` 那两行正文彼此仍相距 12 行，与旧引用的间距一致，但位置已不是那两个数），而 DA 簿一路不报 —— 同 `D-⑬⑭⑯⑰-1` 那一族"漂了没东西响"；本轮**没有**按推断去改指针，也**不在这里写新行号**（写了就是再造一条同样没人数的指针），只登记）；
                  **新增 `D-⑱-3`**（`Repo.kt` 里 `RouteRepository.resolve(...)` **:1119** 跑在 `serverAvailable` 闸门 **:1129** **之前** ⇒ 后端明确说"这一版没有真实道路"而校验器仍 `Accepted` 时，那条路线**照样进 `accepted` 缓存**；今天**不成立为缺陷**，因为 `cached()` / `cachedAt()` 在 main 里读点 **0**（本轮实测：仅 `RealRoadRouteValidatorTest` 5 处读它），RouteRepository 的生产触点只有 `resolve` 那 **1** 处 ⇒ 登记为**潜在**：将来任何人不经 `routeAvailable` 直接读缓存，就把"后端说没有"变成了"本地有一条可用路线"）；
                  **更正** `D-⑧-2` 的性质为"当前模型下行为证据不可得"（仍**在册**，MT-4 留空）；其余 5 项与 3 格**原样**。本轮**销号 0 项** —— 没有源码改动就没有可销的证据。
                **硬规则自查（逐条实测）**：源码树 `*.jks` / `*.keystore` = **0**（签名材料仍在工程外那一个目录）；main 里 `127.0.0.1` / `localhost` = **0**；HTTPS 闸门原文仍在 `app/build.gradle.kts` **第 171 行**、**未传** `-Pbuspost.allowInsecureRelease`（`assembleRelease` 仍配置期红，本轮未放宽）；
                  `git rev-parse --is-inside-work-tree` = `fatal: not a git repository` ⇒ **没有 `git init` 造假历史**；`app/build.gradle.kts` 与 `gradle.properties` 字节未变 ⇒ **无新增依赖、无新增第三方构件**；本轮**未动任何 Compose UI**、**未造 Demo 功能**、**未拿纯函数用例冒充行为半边**。
                **NEXT（F-3 队列，⑱⑳ 已收口）**：状态板那条 F-3 队列快照（② → ③ → ④⑫ → ⑤ → ⑥ → ⑦⑨ → ⑧ → ⑩⑪⑮ → ⑬⑭⑯⑰ → ⑱⑳）**点名的项到此排完**；⚠ 那份快照本身**不含 ① 与 ⑲** ⇒ 本轮不替它们做"已做"的主张，要接必须先回指令原文重新立项。
                  其后按顺序是：**`D-⑱-1`**（给那五本演示面用例补 variant 闸门 —— 用例侧机制改动，需单独一轮）、**`D-⑱-3`**（缓存写入与 `routeAvailable` 的先后，先证"有没有人将来要读"再决定动不动）、`#140`（APK 历史增量归因）、`#159`（`CONTRACT_EVIDENCE.md` §七 的 67 条内容级重锚 ＋ 286 条具名 ID-MISS 池）、**`D-⑱-2`**（把 `.md:NN` 自指纳入判据口径或改成内容锚点 —— 工具改动）。
                  ⚠ 上一块（⑬⑭⑯⑰）里那句"当前项 = ⑱⑳（队列 **#151**）"是**那一轮当时的值，按纪律不回改**；抄当前活一律以本块为准。本文件「## NEXT（用户指定顺序，不得打乱）」那节仍是 ⑦ 收工时的历史快照，别从那节抄顺序。
CURRENT_ANDROID_STATUS:  F-3 ⑬⑭⑯⑰ 收工（2026-10-03 06:2x–13:5x；写入顺序旁证（**不当改动面证据**，工程无 git）：`AmapNavigationEngine.kt` 09:55:24 → `NavigationContractTest.kt` 09:58:37 → `check_navigation_probes.sh` 10:11:19 → `navigation_test_negative_controls.sh` 10:23:18 → `RouteGuideContractTest.kt` 10:33:08 → `UserTrackingContractTest.kt` 10:34:43 → `route_alias_test_negative_controls.sh` 10:49:10 → `eta_negative_controls.sh` 10:57:48 → 两件工具 11:31:53 / 11:41:51 → `check_doc_anchor_probes.sh` 12:09:39 → RC 三本 12:19:02–12:21:44 → `Models.kt` 12:49:21 → `Repo.kt` 13:08:00；⚠ 最后两个 mtime 与 RA 簿那遍**就地复原**同一秒（13:08:00）⇒ "最后一次编辑时间"已被负控覆盖，只能说"改过"，不能说"改成什么"；负控全批 12:23:11→13:19:41（3,390 s / 24 本 / 22 本 rc=0）、批内 rc=2 的两本单本补跑到 13:39:16、行为负控两本 13:00:56 与 13:08:00、终值全量 13:29:24→13:40:09、设备闸门 13:40:06、DA 簿去缩进后单本复跑 13:51:34；三态分写、不混写、不用百分比；明细 = **D-053**，教训 = **§八 ㉜**）
                **SOURCE_COMPLETE**：本轮买的是两件事 —— **"这个别名键到底有没有生产者"从一次性 grep 变成逐行可复现的列**，以及**"这条导航会话绑的还是不是当前这一条路线"第一次有一个可判定的结论（而不是愿望）**。
                  ⚠ **本块行号一律写成"不与文件名相邻"的形式**（`routeBindingOf` **:241** 这种），**不新增 `文件:行号` 锚点** ⇒ #159 那 67 条重锚债不因为本轮记账而变大；内容属实的锚点只写在 D-053 §1/§4 与 §九 那三行，落笔前逐条回源码重读过。
                  ⑬⑯ 死别名撤读 **10 处**（`Repo.kt` 8 + `Models.kt` 2；diff 现数删 8 / 增 16 与删 2 / 增 7 ⇒ **净 +13 行、0 个新逻辑分支**，多的全是"为什么撤"的注释）：
                    名单变窄 9 处（`done`/`finished`/`completed` → 只读 `done` **:710**；`steps`/`guidance`/`instructions` → 只读 `steps` **:1107**；`routeProvider` 四候选 → 两个有生产者的 **:1117**；
                    `routeDistanceMeters` 三候选 → 两个 **:1123**；`routeDurationSeconds` 三候选 → 一个 **:1124**；`coordinateSystem`/`crs` → 一个 **:1125**；`distanceMeters`/`distance` → 一个 **:1141**；`deviated`/`offRoute` → 一个 **:1157**；`distance`/`meters` → 一个 `Models.kt` **:847**）；
                    第 10 处是**形状改变**（本轮唯一"删掉一个读法"而不是"改窄一个名单"）：送达可达性那支从"读 `distance`/`distanceMeters` 两个死别名"改成显式 `distanceMeters = null` **:2013**，界面继续读后端真给的 `distanceText` ⇒
                    **DV-c 实测 0**（无生产者键不再被读进非可空槽），且这一处由行为用例钉（`UserTrackingContractTest` 12→**13** 例），不是只由 grep 钉。
                  ⑭ 绑定：判据层新增会话凭证 `AcceptedRouteSession` **:197**、SDK 侧形状 `SdkPathShape` **:221**（点数 + 道路米数两件一起进）、结论体 `RouteBinding` **:224**（`Bound` **:225** / `Unbound` **:227**）、
                    纯函数 `routeBindingOf` **:241**（三支成因 **:245 / :250 / :257** 分开说：没凭证 / SDK 没给出点集 / 起终点已不是同一条；收敛 **:262**）；
                    SDK 层**每次开始导航前重读** `navi.naviPath`（**:241** 取 `geoPoints().size` 与 `allLength`）→ 交纯函数比一次（**:244**）→ `Unbound` 即 `NavigationSession.Refused`（**:245-246**），旧引擎那一支仍只有 `Refused`（`startGpsNavi` **:400**）⇒
                    **红线没被"顺手优化"**：绑定失败**没有**任何补几何、退回上一条约、或"看起来还能导航"的分支（N-w 的 `0/1` 就是钉"指纹不许用哈希糊一个"）。
                  ⑰ 两件工具：`default_value_census.py` 逐行补 `shape` / `numbool_nonnull` / `demo_keys` 三列 + `shape_tally` / `c_shape` / `c_no_producer` 聚合（**D-②-4 闭环：那一格从不可复现变成逐字可复现**）；
                    `check_doc_anchors.py` 改成**按锚点分配标识符**（`assign_tokens` **:74**）且 **`aid` 不含行号**（`aid_of` **:67**）+ `--pin` / `--pin-write` 闸门（**D-③-5 闭环：ID-MISS 从候选清单变成能当闸**）。
                **BUILD_VERIFIED**（终值 = **最后一遍**，13:29:24→13:40:09）：`testDebugUnitTest + assembleDebug + lintDebug --rerun-tasks` → rc=0 / `BUILD SUCCESSFUL in 1m 56s` / **53 actionable tasks: 53 executed**、
                  **47 份 XML / 476 tests / 0 failures / 0 errors / 0 skipped**、mtime 集合唯一 `['13:30:21']`。**476 − 464（D-052）= +12 做到了逐类归因**（上一轮只能报总数差）：
                  `NavigationContractTest` 26→**34**、`RouteGuideContractTest` 12→**15**、`UserTrackingContractTest` 12→**13**，其余 44 类**逐类名单相同**（基线清单 `/tmp/f150_perclass.txt`，47 行 / md5 `aa4f516f3333ee68543148a1f60e3eb6`）。
                  打包 **53 executed** ⇒ APK 是本轮重打的那个字节对象：**113,895,828 B @13:30:12 / sha256 `a00e1f930404a8960f2e984f1904aa8d064ed12542236bd03ff2273c28b46721`**；
                    ⚠ **与 D-045 那格字节数相同、sha256 不同** ⇒ "尺寸不是同一性"第七次复现，跨轮引用必须连 sha256 + 树指纹 + 产出命令一起抄；相对 D-052 的 114,199,320 为 **−303,492 B** ⇒ **未归因**（本轮没做差量实验，不写优化也不写回归）。
                  dex census **16 dex / defined 29,834 / referenced 36,688（去重 31,900）**；⚠ **`D-⑧-1` 本轮在描述符级闭环**：重打包**之前**先把 04:50 那个包用同一条命令重导，得 **1,353 条 / md5 `80d4a600b2d7d3657f229e4b0b69f6cb`** ——
                    与 D-052 记的**两级读数逐字同串**（先证明提取器有资格报差集，再比新包）⇒ 新包 **1,358 行 / md5 `19f4bf8e9493e7ba184a7b4c65c8e1ed`**、差集 **出=0 / 入=5**，五条全具名（`AcceptedRouteSession`、`RouteBinding`、`RouteBinding$Bound`、`RouteBinding$Unbound`、`SdkPathShape`）
                    ⇒ defined **+5 全量归因**；⚠ **两级都要抄**：只抄条数就看不见"一进一出"那种互相抵消（这正是 D-052 那一格卡住的原因）。
                  lint XML **98,753 B @13:31:19** / **`<issue>` 元素 78** = Warning **46** + Information **32** / Error **0**；同一份文件 `count('<issue')` 仍给 **79**（根标签被数进去）⇒ 本轮起**只报 78 并带分项和**。
                  判据面：`ls tools/check_*.sh` **22 本在终值树上全 rc=0**（含本轮新簿 `check_doc_anchor_probes.sh` 98 行 / 7,296 B）；`v21_requirement_census.sh` rc=0 / `SUMMARY｜rows=119` / present **8** / ABSENT **13** ⇒ **名单无变化**，
                    但**逐条重测的生效行数变了**（`AcceptedRouteSession` 出现 6 / 生效 **4** / 文件 2；`routeFingerprint` 6 / **3** / 2；`startGpsNavi` 8 / **5** / 3；`RouteCacheEntry` / `expiresAt` / `NavigationSessionStore` 仍 **0/0/0**；`graphVersion` 2 / **0** / 2 —— 两条都是注释）。
                  负控：批次 **24 本 / 22 本 rc=0**，`notify_result` 与 `oss_credits` 批内 **rc=2**（前置未满足：批内日志里没有它们要的 test-results XML 真身）⇒ 终值树上**单本补跑 rc=0**（✔=17 / 57 s；✔=20 / 301 s）⇒ 台账口径只能写"22 本 rc=0 + 两本延后补跑 rc=0"，**不许写"24 本全绿"**；
                    ⚠ **`D-⑰⑱-1` 销号**：`eta_negative_controls.sh` 单本 **✔=61 / ✘=0 / 387 s**，`T-e` 与 `T-w2` 两条注入**都命中**了（上一轮那 ✘2 是"注入无判别力"，不是代码回归）；
                    结构簿 `navigation_negative_controls.sh`（306 行 / 16,143 B）与 `doc_anchor_negative_controls.sh`（103 行 / 5,871 B，8 条破口 + 1 条 POS 全 ✔）各在批内与批后各跑一次同值；
                    行为负控 `navigation_test_negative_controls.sh` ✔=**17**（M12 红 1 例 / M13 红 2 例 / M14 红 1 例 / M15 红 3 例，用例基数 **34**，每条"红的必须是具名用例"+ `cmp` 复原 + `[恢复后] exit=0 failures=0/34`）；
                    `route_alias_test_negative_controls.sh`（152 行 / 8,042 B）**RA-1…RA-5 各 ✔**，每格红的是**该键对应的行为用例**，且每格都打印 `◦ 同轮 DV 结构判据 rc=1`（结构红是预期，**不反过来当判别力证据**，§八 ㉛ ④ 同族）。
                  authenticity rc=0：窄 **9** / 宽 **48** / `GeoMath` **27** / `DemoData` 文件 **6** / 假步进 **0**。⚠ **5→6 调查过才写**：第 6 个是本轮写在 `NavigationEngine.kt` **:205** 那段 **KDoc 里的"禁止用演示数据"提及**，
                    脚本口径仍是 `app/src/main/java` 的文件计数 ⇒ 这是**说明性命中**，不是新增演示面；不改 pattern、不为它扩白名单（§八 ㉛ ⑤"换词不是消罪"的反向情形：**加说明也不该被读成加数据**，所以要写清是哪一个文件哪一行）。
                  RC 那族被本轮源码改动抬起（**跨簿影响是实测出来的，不是 predicted**）：RC-a **31/16/48/23**（出现次数 / 生效文件 / 宽口径 / 含注释文件）、RC-a2 **11**、RC-b **0**、RC-b2 **31/0/98**、RC-c **1111 / 0/0**、RC-d **0/0**、RC-e **35/4**、detector sites **31**；
                    ⚠ 抬基线同轮补了**两条新注入**（只改数不补破口 = 把闸门改成恒绿），并把 `runcatching_negative_controls.sh` 里那句**字面量 `sites=30`**（`ctl_keep` 的期望串）一起改对 —— 它旧值会让那条控制在数变之后**静默空跑**。
                  树指纹 **files=162 sha256=`42863d1c941598a2578f942893f9ae0e38ec614cf11e1fef5506f8129cfe7422`** —— 与 D-052 的 `42f5bbf9…` **不同串，且这正是不该有的相反情形**：本轮**真的改了 `app/src`**，
                    所以"变了"是预期的、"没变"才要查（`tools/` 与 `docs/` 不在这个 hash 作用域里，§八 ㉛ ⑧ 同口径）⇒ 判据簿改动另抄行数/字节/CR（见 DONE 那行与 D-053 §4）。
                **DEVICE_VERIFIED**：**未到位**（0 台在环，`bash tools/verify_device.sh --no-build` **rc=1 / DEVICE_UNAVAILABLE**，新证据 `docs/device-evidence/20261003-134005/`）。
                  本轮**特别**要写这一格：⑭ 那支 `Unbound` 的**真实发生率**在 JVM 里**永远读不到** —— `navi.naviPath` 在真导航中何时非空、SDK 的 `geoPoints()` 是否总与刚验过的路径一致、被拒时屏幕说什么，全都没有实机证据；
                  本轮买的只是"代码里存在一条会拒的路径，且那条路径不补几何"（结构 + 34 条 JVM 用例）。⇒ 整体记 **`ANDROID_F3_⑬⑭⑯⑰_PARTIAL`**，不写百分比。
                **在册不当已解决**：**新增** `D-⑭-1`（⑭ 只买绑定半边，TTL/graphVersion 半边三面 0 生产者 ⇒ N-x `0/1` 先把话说破）、`D-⑬⑭⑯⑰-1`（`RUNCATCHING_AUDIT.md` 逐点账锚点**不在任何在册表里** ⇒ 漂了没东西响）、`D-⑰-1`（`--pin-write` 夹具整文件 CRLF，"当前可用"实测、"为什么可用"未证）；
                  **销号** `D-⑰⑱-1`（eta 两条注入重锚后有判别力）、`D-⑧-1`（描述符级闭环，**只到这一层** —— 见下一行的历史半边）；
                  仍在册 `#140`（APK **历史**增量那一半仍无可比旧产物）、`#159`（286 条具名 ID-MISS 池 + 67 条内容级重锚）、`D-⑧-2`、`D-⑦⑨-1`、`D-⑦⑨-2`、DEVICE/RELEASE/GIT。
                **硬规则自查（逐条实测）**：源码树 `*.jks` / `*.keystore` = **0**（签名备份仍在工程外 1 个目录）；main 里 `127.0.0.1` / `localhost` = **0**；HTTPS 闸门原文仍在 `app/build.gradle.kts` **第 171 行**、**未传** `-Pbuspost.allowInsecureRelease`；
                  ⚠ **凭证字面量这一格本轮抄两种口径**：main 里 `Bearer ` 命中 **2**（`Api.kt` 第 311 / 419 行，是 `header("Authorization", "Bearer $token")` 的**变量插值**），而"字面量 token"仍是 **0** ⇒ 只写"0"就是挑好看的数，两个都要落档；
                  `git rev-parse --is-inside-work-tree` = `fatal: not a git repository` ⇒ **没有 `git init` 造假历史**；`app/build.gradle.kts` **11,008 B @09-30 00:40:50**、`gradle.properties` **920 B @09-29 02:29:42**、声明坐标 **20** 条 ⇒ **无新增依赖、无新增第三方构件**（许可证侧只追加"自写新文件"登记）。
                **NEXT（F-3 队列，⑬⑭⑯⑰ 已收口）**：**当前项 = ⑱⑳**（队列 **#151**：10 本测试类 + 全量验证 + 四份台账落档；⚠ 那 10 本先逐本核"点名的是哪一类缺陷"，不许为凑数新建空测试类，也不许把已有类改名冒充新增）；
                  其后回到 `#140` / `#159` 两笔在册债；本轮**没有**给出的东西一律留在 §九 与 D-053 §5，不排进"顺手做完"。
                  ⚠ 本文件「## NEXT（用户指定顺序，不得打乱）」那一节是 **⑦ 收工时的历史快照**，里面那句"下一轮 = ⑨"指的是 **F-2 的第 9 步**（早已收口）⇒ 抄当前活一律以本块为准，别从那节抄顺序。
CURRENT_ANDROID_STATUS:  F-3 ⑧ 收工（2026-10-03 02:5x–05:3x，源码改动止于 03:15:39 那一批、判据簿末次改动 03:30:49、行为负控簿 05:01:17；负控全批 03:35:30→04:37:36（3,680 s / 21 本 / 19 本 rc=0）、deferred 两本与结构负控单本复跑到 05:17、行为负控 MT 三本 05:01:48→05:03:17、终值全量 05:03:58、打包 05:13:39（lint 报告 04:51:22）、设备闸门 05:18:50；三态分写、不混写、不用百分比；明细 = **D-052**，教训 = **§八 ㉛**）
                **SOURCE_COMPLETE**：本轮买的是**"后端没有路线"那一支从"页面自己编一句、顺手用直线补个形状"变成"一个出口、五格说法、每一格都明说我这次不画线"**，并把"没问到"和"没有"拆成两格。
                  ⚠ **本块行号一律写成"不与文件名相邻"的形式**（`routePhase` **:124** 这种），**不新增 `文件:行号` 锚点** ⇒ #159 那 67 条重锚债不因为本轮记账而变大。
                  唯一出口：`ui/driver/MissionMarkers.kt`（172 行 / 9,076 B；137→172 ⇒ **+35 −0，本轮没删过一行**）的 `missionRouteReasonOf(phase, failure, roadReason)`（**:80**）五格 **:82/:84/:86/:88/:90** =
                    在问 / 没问过 / 没问到 / 后端给的 / 兜底，判据 **MR-c 字面量 1/1/1/1/1** 且出口里"不画任何示意线"生效行 **= 5**（每格自带"不画线"承诺，不是只在兜底那一格说）；
                    折线闸门 **:49** 两半都在（`polyline.size < 2` **与** `routeUnavailableReason != null` → `emptyList()`），`MapPath(` **:51** 是全树唯一出现处 ⇒ **MR-j 1/0**：任务地图页自己**连不成线**（"不许直线补几何"在这一层才有可测形状）。
                  页面拿不到句子就画不出图：`DriverMissionMap.kt`（189 / 8,428；+7 −4）的 `noRouteReason: String`（**:60**）**必填、无默认值**（**MR-e 1/0/1** = 必填 1 / 带默认值 0 / "带默认值"写法反证 1），
                    出口传参 1 处、绕过出口自己编句子的写点 **0**、地图调用点 1 处；`MapCanvas.kt`（251 / 10,640 / **CRLF，CR=251 = 整文件**）`RouteMap` 的 `reason`（**:63**）与 `RouteUnavailableState` 的 `reason`（**:194**）两处可空形状（**MR-k 1/2**），
                    兜底句 **:234** 只在 `reason=null` 时读，且句子自己写明"具体原因未取得"。
                  相位与残留：`WorkbenchScreen.kt`（1,298 / 61,812；+66 −15）VM 两个私有写 `routePhase`（**:124**，初值 NotAsked）/`routeFailure`（**:128**）—— 装载置 `Asking` **:226**、落地置 `Asked` **:233**、
                    协程取消与撤档各退回 `NotAsked`（**MR-f 1/1/2/1**），早退块内相位/原因/guide 三件清零齐全（**MR-g 1/1/1**），写入行号严格包住那条请求 **226 < 228 < 233 且窗口内请求 = 1**（**MR-h**）；
                    失败支 `else if (vm.routeFailure != null)` **:771** + `RouteUnavailableState` **:773-775** 走唯一出口（**MR-i 1/1 + 1/1/1**：占屏、走出口、给重试）。
                  ⚠ **为什么复用 `core/map/PolylineDecision.kt` 的 `PolylinePhase` 而不是新造一套**：**MR-k 1/2/0**（定义 1 / 复用 import 2 / **第二套枚举 0**）—— §八 ㉗"同一个问题不许有两个答案"在这一格落地；改回布尔也红（结构负控里那条 `exit 1` 在案）。
                  ⚠ **"没问到"不等于"没有"的可测落点**：正式链里 `RouteGuide.EMPTY` 只有**一个**生产点（`Repo.routeGuide()` 的 catch 支），后端真说"没有道路"时必带非空 `routeUnavailableReason` ⇒ "EMPTY 且 reason=null" 本身就是"这次没问到"的证据；
                    据此 **MR-d** 把旧那句写死的"后端没有下发"在**正式可达 4 文件**里归零（**0**），并用 DEBUG 屏同机制 **3** 处作反证（0 不是 grep 写错，是"这套句子只剩那一支到得了正式链"）。
                  ⚠ 新文案统一用"示意线 / 折线"这两个**不在 authenticity pattern 里**的词 ⇒ **换词不是消罪**："每格都拒绝画线"的证据只由 MR-c 那个 **5** 给，**不由**宽口径 48 不变来给（见下面 BUILD_VERIFIED 的归因段）。
                **BUILD_VERIFIED**（终值 = **最后一遍**，不是最好看的那一遍）：`:app:testDebugUnitTest --rerun-tasks` 05:03:17→05:03:58 → `BUILD SUCCESSFUL in 39s` / rc=0 / **24 actionable tasks: 24 executed**、
                  **47 份 XML / 464 tests / 0 failures / 0 errors / 0 skipped**、mtime 集合唯一值 `['05:03:55']`（`/tmp/f8_final_readings.log`）。**464 − 452（D-051）= 12** 恰等于新簿 `MissionRouteStateContractTest`（实测 `tests=12 fail=0 err=0 skip=0`）、
                  类数 **46→47** 同向对上；⚠ 其余 46 类**没做**机器比对（工程无 git、上一版清单已被覆盖）⇒ 只算"总数差对上"，**不能说"回归已证明"**。⚠ 那 **24** 与 ⑦⑨ 的 **53** 不是同一测量（那遍把 `assembleDebug + lintDebug` 并进同一条命令）。
                  ⚠ 中间还有一遍 04:37:41→04:38:34（51s、同样 47 XML / 464，`/tmp/f8_after_negctl.log`）**不作数**：它在两本 deferred 补跑之前，也不是行为负控 MT 之后的那遍。
                  打包：**05:13 那遍 `48 actionable tasks: 1 executed, 47 up-to-date`** ⇒ ⚠ 说明 APK 就是 **04:50 那个字节对象**（**114,199,320 B / sha256 `4dfe63621aed10cf801a272b55e75dd157005da4c423fe600b1721c45f43f80b`**），
                    **不能**写成"重打一遍得到同一个文件"（那半要 `--rerun-tasks` 重打包才算，见 #140）。相对 ⑦⑨ 的 113,895,828 **增 303,492 B** ⇒ 这次是**内容变了**，不是那族"字节数相同、sha 各异"的第七次。
                  dex census（对**终值包**，`/tmp/f87_dex_census.log`）**16 dex / defined 29,829 / referenced 36,680（去重 31,895）** vs D-051 的 29,828 / 36,677 / 31,894 ⇒ **+1 / +3 / +1**；
                    ⚠ **D-051 那条"main 没新增类型 ⇒ defined 不该变"的推断本轮被自己的实测证伪**：本轮 main 同样只改函数体，唯一的新 `.kt` 在 test 源集且 dex 点名 **0（ABSENT）**，可 defined 还是动了。
                    归因**到族归不到描述符**：`MissionMarkers` 只有 **2** 个描述符（`MissionMarkers` + `…$ordered$$inlined$sortedBy$1`，**没有 `MissionMarkersKt` facade** —— 该文件顶层只有 `object`），
                    而 `WorkbenchScreenKt$WorkbenchScreen$5$$ExternalSyntheticLambda0…16` 那一族现数 **17** 个合成壳 ⇒ +1 极可能是新构起的 lambda/合成壳，但**没有改动前的类清单可比** ⇒ 如实记 **`D-⑧-1` 未归因**，不写"就是那个壳"；
                    本轮起把 **1,353** 个 app 描述符清单归档（`/tmp/f87_dex_classlist.txt`）作**下一轮的基线**（下一轮再动 main 就能把这格关掉）。
                  lint XML **98,753 B @04:51:22** / **issue 元素 78** = Warning **46** + Information **32**、**Error 0**，新簿与三个改动文件在报告里 **0 点名**；⚠ 同一份文件 `count('<issue')` 仍给 **79** —— §八 ㉚ ⑦ 已定性为**计数式错**（根标签被数进去），本轮照原口径复现一次、**不改数**。
                  判据面：本簿 `check_mission_route_probes.sh`（176 行 / 16,994 B / LF）**MR-a…MR-k 共 11 条探针 / 17 个打印读数**，每一个"0"都配同机制反证；`ls tools/check_*.sh` **20→21** 本，**21 本在终值树上全 rc=0**（逐本 rc 在 `/tmp/f8_terminal_chain.log`）；
                    `v21_requirement_census.sh` rc=0、`SUMMARY|rows=119`、名单 **21** / `|present|` **8** / ABSENT **13** —— 与 D-051 **逐项同值** ⇒ 本轮无名单变化；新簿不进那份名单是**设计不是漏**（由 MR-* 钉，同 `DriverActionHonestyContractTest` 之于 `H-*`）。
                  结构负控 `mission_route_negative_controls.sh`（276 行 / 11,655 B）：**27 条注入 ✔=27 ✘=0 △=0 ◦=0**，批内（04:04:46→04:08:29 / 222 s）与批后**单本复跑**（05:17，`/tmp/f8_mr_neg_final.log`）**同值**，
                    `[基线]` 与 `[恢复后]` 各打印一次"任务地图无路线态判据通过"。⚠ **批次账是一条链不是三个数**：全批 **21 本 / 19 本 rc=0**，`notify_result`、`oss_credits` 批内各 **rc=2**（前置未满足：真身没有 test-results XML）⇒ **不得写成"21 本全绿"**；
                    两本在终值树上单本补跑 **rc=0**（命中 **16** / **19**，失效 **0**）；`eta ✔=59 ✘=2` 仍是在册旧项 `D-⑰⑱-1` 的**复现**，不是本轮新增回归。
                    ⚠ **本轮三个口径并存**：`ls tools/*_negative_controls.sh` = **22**（新簿计入）、松散 glob `*negative_controls*.sh` = **23**（多算批次本 `run_all_negative_controls_batch.sh` 自身）、批次实际跑 = **21** 本 ⇒ 三个数**不可互抄**。
                  行为负控 **MT-1 / MT-2 / MT-3**（`mission_route_test_negative_controls.sh` 168 行 / 10,489 B @05:01:17，`RESULT=PASS`）每条都是"基线 gradle exit=0 **且** 同轮结构判据 rc=0 → 注入 → 红的必须是**具名用例** → `cmp` 复原"：
                    MT-1 让 `routeFailure` 永远为 null → `exit=1`、红 **2** 条（含 `路线请求失败时问过这一事实与失败原因都留下`）；MT-2 把"没问过"那一格换成写死的"后端没有下发…" → `exit=1`、红的正是 `任何一格都不许说后端没有下发`（红 **3** 条）；
                    MT-3 早退不再清相位与失败 → `exit=1`、红的正是 `上一轮留下的问过与失败在早退时一起清零`（红 **1** 条）。三条之间**先 `cmp` 复原再取下一条基线**。
                    ⚠ 每条里"同轮结构判据 **rc=1**"都记 **◦** 不记 ✔：注入让 MR-* 变红是**预期**，但**红着的结构判据不能反过来证明结构判据有判别力** —— 那一半由上面 27 条结构注入给（§八 ㉛ ④）。
                    ⚠ **没有 MT-4**：`撤档早退时路线状态退回没问过` 在 MT-3 那条破口下**不红**（VM 初值本来就是 NotAsked，清不清都读得到）⇒ 它钉的是"早退不许把相位置成问过"，不是"早退要清零"；这一格**覆盖面缺口**按 §八 ㉛ ③ 在脚本头部**预留 ID** 并登记 **`D-⑧-2`**，不假装三条负控覆盖了四条断言。
                  authenticity（`check_authenticity_counts.sh` rc=0）：窄 **9** / 宽 **48** / `GeoMath` **27** / `DemoData` 文件 **5** / 假步进 **0** —— 与 F-3 ⑤ 那遍**一字未变**；宽口径逐文件 **18 行加总正好 48**（本轮 `MissionMarkers.kt` 占 **5**，它早就在表里）。
                    ⚠ **+0 是实测出来的，不是"本轮没写文案"**：4 个改动文件的新增行里只有 `MapCanvas.kt` 那 **1** 行带宽口径关键字，而它**替换掉**的旧行本来就带同一个关键字 ⇒ `diff` 现数 新增 **1** / 被删 **1** = 净 0；其余三个文件新增行命中 **0**（+35 / +7 / +66 行）。
                  树指纹 **files=162 sha256=`42f5bbf946818654c31574ec311ef61289ea79732b7b58b5aa3cfbbd441b8fd5`**（161→162 = 本轮唯一新源文件；**03:39 与 05:13 两次同值** ⇒ 批次那 19 本 + 27 条结构注入 + MT 三条的就地改写**全部逐字节复原**）。
                    ⚠ **指纹口径 = `Path('app/src').rglob('*')` 跳过 `build`/`.git` ⇒ `tools/` 与 `docs/` 不在这个 hash 里**（§八 ㉛ ⑧）⇒ "指纹没变"**不能**用来证明判据簿没动，判据簿动了要另外抄行数/字节/CR。
                    ⚠ **分叉写法**：**下一轮改 `app/src` 内容 ⇒ 此值必变**；**下一轮一字未改而它变了 ⇒ 有工具在动内容**，按 §八 ㉘ ⑩ 从备份比对并回。
                **DEVICE_VERIFIED**：**未到位**（0 台在环，`bash tools/verify_device.sh --no-build` **rc=1 / DEVICE_UNAVAILABLE**，新证据 `docs/device-evidence/20261003-051850/`，`adb` 列表为空）。
                  三问**一次都没看过**：无路线态那一块在屏幕上排成几行、被折叠或被读屏截断后用户还剩什么、失败支给的那个重试入口点下去是不是真的重新发出一条请求（`MR-h` 断的是**本机相位写入行号包住请求**，不是手指）——
                  本轮用例断的是**相位分支与请求份数**（JVM），不是屏幕；地图画布上"什么线都没有"这件事**没有任何实机像素级证据**。⇒ 整体记 **`ANDROID_F3_⑧_PARTIAL`**，不写百分比。
                **在册不当已解决**：`D-⑧-1`（dex defined **+1** / referenced **+3** / 去重 **+1**，**归因到族归不到描述符**）、`D-⑧-2`（行为负控覆盖面：`撤档早退…退回没问过` 那条**没有对应注入**）、
                  `D-⑦⑨-1`（后端 `shifts()` 对解析不出 operating route 的司机返回全部启用班次 ⇒ 后端侧，本机不自造第二道过滤）、`D-⑦⑨-2`（`photoRequired = !isProduct` 比后端与小程序都严 ⇒ 未修、留给用户裁决）、
                  #140（APK 历史增量那半未闭环 —— 本轮的 `47 up-to-date` 又把这一格顶到眼前）、#159（67 条 App 锚点未内容级重锚）、`D-⑰⑱-1`（`eta ✘=2` 复现）、DEVICE/RELEASE/GIT；
                  ⚠ **本轮无销号项**（#148 的"说什么、画什么"收了，但它不给任何在册项关账）。
                **硬规则自查（逐条实测，不凭记忆）**：`find . -name "*.jks" -o -name "*.keystore"` = **0**；`app/src/main` 里 `127.0.0.1` / `localhost` = **0**；main 源集 token / `Bearer …` / `secret=` / `password=` 字面量 = **0**；
                  HTTPS 闸门原文仍在 `app/build.gradle.kts:171`、**未传** `-Pbuspost.allowInsecureRelease`；`git rev-parse --is-inside-work-tree` = `fatal: not a git repository` ⇒ **没有 `git init` 造假历史**；
                  `app/build.gradle.kts` **11,008 B / 09-30 00:40:50**、`gradle.properties` **920 B / 09-29 02:29:42** 一字未动（**无新增依赖、无新增第三方构件**）⇒ 许可证侧只追加"自写新文件"登记（见 `THIRD_PARTY_NOTICES.md` 末段）。
                **NEXT（F-3 队列，⑧ 已收口）**：**当前项 = ⑬⑭⑯⑰** 导航契约 / 路线绑定 / 缓存指纹（队列 **#150**：`docs/android/ANDROID_V2.1_CHECKLIST.md` 里 **V21-09 那两行 ❌** 至今没关 + `D-⑰⑱-1` 那两枚 `eta ✘=2` 要一起判掉；
                  缓存指纹那一半先只做"读得到/读不到"，不许在没取到指纹时显示一个看起来像指纹的东西）；其后 ⑱⑳（**#151**：10 本测试类 + 全量 + 四份台账）；`#140`、`#159`、`D-⑧-1`、`D-⑧-2` 仍在册。
                  ⚠ 本文件「## NEXT（用户指定顺序，不得打乱）」那一节是 **⑦ 收工时的历史快照**，里面那句"下一轮 = ⑨"指的是 **F-2 的第 9 步**（早已收口）⇒ 抄当前活一律以本块为准，别从那节抄顺序。
CURRENT_ANDROID_STATUS:  F-3 ⑦⑨ 收工（2026-10-02 23:0x–2026-10-03 02:2x，源码改动止于 02:00:51 那一批、判据簿末次改动 02:24:04；负控批内 00:44:55→01:32:28、批后单本复跑到 02:0x、行为负控 IT 三本到 02:01:10 之后的那一遍、deferred 两本补跑在终值遍之前、终值全量 02:10→02:13:17、设备闸门 02:14:10；三态分写、不混写、不用百分比；明细 = **D-051**，教训 = **§八 ㉚**）
                **SOURCE_COMPLETE**：本轮买的是**"缺项即不出网"从"我看代码觉得会早退"变成 `server.requestCount` 断言**，并顺带抓出一条**跨业务域真缺陷**。
                  落点（行号按 02:13:17 终值树**现读**，不是从上轮台账抄）：`ui/driver/WorkbenchScreen.kt`（1,247 行 / 58,376 B）`navOffset` :128、`targetIndex()` :234-238（基序号 :236、`+ navOffset` :237）、
                  `targetStationId()` :240、`continueToNextStation()` :297-309（**三个写点** :301 换班次归零 / :302 推进 / :304 末站归位）、`onArrive = { vm.arrive(vm.targetStationId()) }` :649、
                  显示层 `currentIdx` :886（进度条、"第 N/M 站"、每站到站态都读它，**只认后端** `currentStationId`）；
                  `data/model/Models.kt`（2,065 行 / 106,787 B）`PickupTask.isProduct` :1491 回到后端唯一判据 `bizType=PRODUCT`（`DriverAppServiceImpl:352`），同族 `photoRequired` :1494 **本轮没动** ⇒ 记 `D-⑦⑨-2`。
                  **任务点是两层派生、不许并成一层**：导航目标 =（后端 `currentStationId` 推出的下一站）+ 本机 `navOffset`，显示当前站只认后端 ⇒ 并层的失败形状是"人还没到、画面已显示到第 3/5 站"=
                  **本机视图冒充后端状态**（与 I-e/I-f 同一红线）；`navOffset` 写点份数由 `H-m2 3/3` 钉住 —— "禁止 GPS 自动完成业务"在这一层才有可测形状（写点一旦散进位置回调/页面初始化，目标就随 GPS 自己走）。
                  ⚠ **一条改判**："页面算目的地"曾是疑点（`D-④⑫-2`），实测小程序 `workbench.js:796-811 arriveAtStation()` 用的是同一个 `points[targetIndex].stationId`，而后端 `arrive()` 逐站校验会拒掉错的那一支
                  ⇒ 属三方**同源**不是缺陷；遂用 `H-m4` 钉**耦合方向**并写明"改它必须先重做三方核对"，`D-④⑫-2` 由此销号。
                  ⚠ **`isProduct` 那条是真缺陷**：旧写法挂着 `orderType == 3 && mailNo.isEmpty()` 的第二条件 ⇒ **没面单号的邮快件**被当商城单，装车走 `product-load`、妥投走 `product-deliver`（按 `ProductOrderDO` 查单）
                  = 硬规则 1 的跨域，不是显示错。结构判据 `H-k` 只钉"写法回到单条件 + 枚举码不许回流"，**IT-2 才证明"改回去会有用例红"**。
                **BUILD_VERIFIED**（终值 = **最后一遍**，不是最好看的那一遍）：`assembleDebug + testDebugUnitTest + lintDebug` **同一条命令** 02:10→02:13:17 → `BUILD SUCCESSFUL in 2m 30s` / rc=0 / **53 actionable tasks: 53 executed**、
                  **46 份 XML / 452 tests / 0 failures / 0 errors / 0 skipped**、mtime 集合唯一值 `['02:12:25']`（`/tmp/f86_gradle_final.log`）。⚠ 那 53 与 D-050 的 24 **不是同一测量**（那遍只跑 `testDebugUnitTest`），不可直接比。
                  ⚠ 中间还有一遍 02:01→02:03（`/tmp/f86_gradle_mid.log`，同样 53/53）**不作数**：它之后跑了两本 deferred 负控补跑，且 02:10 才是终值树上的一遍。
                  **452 − 447（D-050）= 5** 恰等于新簿 `DriverActionHonestyContractTest`（XML 实测 `tests=5 fail=0 err=0 skip=0`）、类数 **45→46** 同向对上；⚠ 其余 45 类**没做**机器比对（工程无 git、上一版清单已被覆盖）⇒ 只算"总数差对上"，**不能说"回归已证明"**。
                  APK **113,895,828 B @02:12:08 / sha256 `fbe111b1ffff4a2a8407e0bd29aa108160ce93f965ecd29804e442122441ff0e`** —— ⚠ **字节数与 D-046/D-047/D-050 完全相同、sha 各异（第六次）** ⇒ "字节数不单独当变更证据，必须连 sha256 + 树指纹成对抄"再复现一次。
                  dex census（对**终值包**，`/tmp/f86_dex_census.log`）**16 dex / defined 29,828 / referenced 36,677（去重 31,894）** —— 与 D-050 **逐项相同且这一格归因得清**：本轮 main 源集**没有新增类型**
                  （唯一的新 `.kt` 在 test 源集，`Models.kt`/`WorkbenchScreen.kt` 都只改函数体）⇒ defined 不该变，实测确实没变。描述符点名：`…ui/driver/DriverActionHonestyContractTest;` **0（ABSENT，test 源集不进包）**、
                  `…WorkbenchViewModel;` **3**、`…PickupTask;` **8**（`classes3`×4 / `classes4`×3 / `classes12`×1）。
                  lint XML **98,753 B @02:13:17** / **issue 元素 78** = Warning **46** + Information **32**、**Error 0**，新簿在报告里 **0 点名**；⚠ **D-049/D-050 挂着的那笔"79 vs 78 不自洽"本轮结掉**：
                  `count('<issue')` 把根标签 `<issues format="6" by="lint 8.7.3">` 数了进去（同一份文件实测 79 / `re.findall(r'<issue[\s>]')` = 78 / 分项 46+32=78）⇒ 三轮**条数本来就一致**，那 1 条差是**计数式错**不是少了一条 issue（D-051 §7 作废登记 + §八 ㉚ ⑦）。
                  判据面：`check_idempotency_probes.sh`（273 行 / 25,730 B / LF）**本轮新增 18 行读数 rc=0**（`H-a 1 · H-b 1 · H-c 0 · H-d 1 · H-e 1/1 · H-f 2 · H-g 5 · H-h 3 · H-i 6 · H-j 3/0/1 · H-k 1/0 · H-l 5 ·
                    H-m1 1 · H-m2 3/3 · H-m3 1 · H-m4 1 · H-m5 1`，`/tmp/f86_probes_baseline3.log`），`I-*`/`U-*` 一族一字未动（`I-j 11`、`U-h 10`）；台账段又复跑一遍（`/tmp/f86_probes_final.log`）**逐行同值**，
                    唯一差别是把 `H-m4`/`H-m5` 两行的**打印序**换成编号序（原脚本把 5 印在 4 前面 —— 读数没错，但顺序读起来像漏号）。
                  结构负控 `idempotency`：**✔=44 ✘=0 △=0 ◦=1**（`/tmp/f86_negctl_hm3.log`，`[基线]` 与 `[恢复后]` 各打印"幂等判据通过"）。⚠ **账面是一条链不是三个数**：24（D-050）→ 38（本轮**批内**，`/tmp/f80_neg_batch.log` 00:44:55→01:32:28 共 20 本 / 18 本 rc=0）→
                    44（本轮**批后**追加 `H-m` 六条注入 ⇒ **不得写成"批内 44"**）。
                  ⚠ **`H-m3` 的第一版负控是失效的**（`/tmp/f86_negctl_hm.log` 第 44 行 `✘ H-m3 … 负控制失效（exit 0）`）：锚点没做行尾收口，注入 `… currentStationId } + 1` 之后 `grep -c` 照样命中 ⇒ 破口生效而读数不变 = **假绿**；
                    修法 = 锚点收成 `… currentStationId }$`、`onArrive` 那条要求紧跟 `,`；复跑 **43→44 ✔** ⇒ 进 §八 ㉚ ①（"改坏它"要按**最像对的改法**设计，只试"整行删掉"会漏掉这一族）。
                  行为负控 **IT-1 / IT-2 / IT-3** 三条各带"基线必须绿 → 注入 → 要求红的正是**具名用例** → `cmp` 复原"（`/tmp/f86_negctl_it3.log`，末尾 `SCRIPT_RC=0`）：
                    IT-1 摘掉 `ActionGate` 的窗口检查与 `tryLock` → `gradle exit=1`、**10 条红**；IT-2 把枚举码 3 注回 `isProduct` → `exit=1`，红的正是 `DriverStateTest > 揽件单类型决定作业要求`（failures=1）而 honesty 那本 **0 红**
                    ⇒ 如实记 `◦ IT-2 覆盖面`，不假装两本都咬住；**IT-3 摘掉 `targetIndex()` 里的 `+ navOffset`** → `exit=1`，红的正是 `继续行驶只推进导航目标_后端当前站与网络都不动`，而**同一棵树的结构判据 rc=0**
                    ⇒ 这条是"结构半边与行为半边分工"的**实测证据**（`H-m1/H-m2` 看不见"点了没反应"这种语义破口；§八 ㉚ ④）。三条之间**先复原再取下一条基线**（D-050 那轮 IT-2 跑在残留树上的坑，本轮 `cmp` 逐份证一致）。
                  判据簿面：**20 本 `check_*.sh` 在终值树上全 rc=0**（`/tmp/f86_probe_books.log`，逐本 rc + 末行都留了）；`v21_requirement_census.sh` rc=0、`SUMMARY|rows=119`、V21-30 名单 **21** / `|present|` **8** / ABSENT 13
                    （`D-⑥-5` 由此销号：名单补进 `RouteDomainDeepLinkTest` ⇒ 20→21、present 7→8、rows 118→119，两份数同轮重抄）；⚠ 本轮新簿 `DriverActionHonestyContractTest` **不进那份名单是设计不是漏**，
                    由 `H-g/H-h/H-i/H-j/H-k/H-l/H-m` 钉住（同 `UserCreateIdempotencyTest` 之于 `U-*`），这句话写在 `tools/v21_requirement_census.sh` 的注释里。
                  `check_shell_routing_probes.sh` 复跑（`/tmp/f86_sr_recheck.log`）`SR-e = 0` ⇒ `D-⑥-2` 的"不加第二道闸"维持（#147 **没有**新增 user→driver 的 `navigate` 调用点，新用例只调 `vm.arrive/verify/continueToNextStation`）。
                  ⚠ **deferred 两本**（`/tmp/f86_negctl_deferred.log`）：`notify_result` rc=0 命中=16 失效=0、`oss_credits` rc=0 命中=19 失效=0（批内那两本各 rc=2）⇒ **不许写成"负控全批 20 本全绿"**；
                    `eta ✔=59 ✘=2` 仍是在册旧项 `D-⑰⑱-1` 的**复现**，不是本轮新增回归。
                  树指纹 **files=161 sha256=`969caf6371c0b6ac8550a0071d100cadc37180113649d9cd856bdb08122a1cb4`**（`/tmp/f86_fingerprint.log`；160→161 = 本轮唯一新文件，归因完整）。
                  ⚠ **分叉写法**：**若下一轮改 `app/src` 内容 ⇒ 此值必变**（D-050 那条预测本轮就是被自己改源码如期证伪的）；**若下一轮一字未改而它变了 ⇒ 有工具在动内容**，按 §八 ㉘ ⑩ 从备份比对并回。
                **DEVICE_VERIFIED**：**未到位**（0 台在环，`bash tools/verify_device.sh --no-build` **rc=1 / DEVICE_UNAVAILABLE**，新证据 `docs/device-evidence/20261003-021410/`，`adb` 列表为空）。
                  三问**一次都没看过**：邮快件装车在无相机权限时会不会被本机自己卡住（`D-⑦⑨-2`）、点「继续行驶」画面上的导航目标是否真的换了、缺项回执在屏幕上显示的原文长什么样 ——
                  本轮用例断的是**请求份数与本机视图分支**（JVM），不是屏幕；拍照链路一次都没实机验过。⇒ 整体记 **`ANDROID_F3_⑦⑨_PARTIAL`**，不写百分比。
                **在册不当已解决**：`D-⑦⑨-1`（后端 `shifts()` 对"解析不出 operating route"的司机返回**全部启用班次**，`DriverAppServiceImpl:195-211` + `:1871-1882` 的 `orElse(null)` ⇒ **后端侧**、本机按"后端是唯一真值"照单显示、**不自造第二道过滤**）、
                  `D-⑦⑨-2`（`photoRequired = !isProduct` 比后端与小程序**都严**：邮快件也被强制拍照；小程序 `workbench.js:889-893` 是 `order.orderType !== 3` 才拍，后端 `DriverAppServiceImpl:1049-1051` 只对货运抛 `DRIVER_CARGO_PHOTO_REQUIRED`
                  ⇒ **未修、留给用户裁决**，改流程不属于本工程单方面可定）、#140（APK 历史增量那半未闭环）、#159（67 条 App 锚点未内容级重锚）、`D-⑰⑱-1`、DEVICE/RELEASE/GIT；
                  **本轮销号** `D-④⑫-2`（`H-m1…H-m5` + IT-3）、`D-⑥-5`（进普查名单）；**同轮重判维持** `D-⑥-2`（`SR-e=0` 复测）。
                **硬规则自查（逐条实测，不凭记忆）**：`find . -name "*.jks" -o -name "*.keystore"` = **0**；`app/src/main` 里 `127.0.0.1` / `localhost` = **0**；main 源集 token / `Bearer …` / `secret=` / `password=` 字面量 = **0**；
                  HTTPS 闸门原文仍在 `app/build.gradle.kts:171`、**未传** `-Pbuspost.allowInsecureRelease`；`git rev-parse --is-inside-work-tree` = `fatal: not a git repository` ⇒ **没有 `git init` 造假历史**；
                  `app/build.gradle.kts` **11,008 B / 09-30 00:40:50**、`gradle.properties` **920 B / 09-29 02:29:42** 一字未动（**无新增依赖、无新增第三方构件**）⇒ 许可证侧只追加"自写新文件"登记（见 `THIRD_PARTY_NOTICES.md` 末段）。
                **NEXT（F-3 队列，⑦⑨ 已收口）**：**当前项 = ⑧** 任务地图无路线态收口（队列 #148：`DriverMissionMap` / `MissionMarkers` 在"后端没有路线"那一支现在**说什么、画什么** —— 不许用直线补几何，也不许把"没问到"说成"没有"）；
                  其后 ⑬⑭⑯⑰ 导航契约 / 路线绑定 / 缓存指纹（#150，含 V21-09 那两行 ❌）、⑱⑳ 10 本测试类 + 全量 + 四份台账（#151）；`#140`、`#159` 仍在册。
                  ⚠ 本文件「## NEXT（用户指定顺序，不得打乱）」那一节是 **⑦ 收工时的历史快照**，里面那句"下一轮 = ⑨"指的是 **F-2 的第 9 步**（早已收口）⇒ 抄当前活一律以本块为准，别从那节抄顺序。
CURRENT_ANDROID_STATUS:  F-3 ⑥ 收工（2026-10-02 17:3x–19:5x，源码改动止于 17:45:18 与 19:20:14 那两次写入，负控全批 18:18:25→19:08:50、批后补跑到 19:19:41、终值全量 19:23:45、打包/lint 到 19:23:06、设备闸门 19:16:32；三态分写、不混写、不用百分比；明细 = **D-050**，教训 = **§八 ㉙**）
                **SOURCE_COMPLETE**：本轮买的是**"入口只有一处"这类话从口头变成 grep 可测**。
                  出网口全部收进 `nav/Routes.kt`：前缀 `DEEP_LINK_PREFIX`（**:50**，全树唯一）、`deepLink()`（**:53**，唯一拼装体）、`routeFromDeepLink()`（**:59-61**，不匹配前缀 → `null`）、
                  `trace()` **:67** / `busDetail()` **:68** 两个 builder 都过纯 Kotlin `encodeSegment`（**:82**）。
                  四个消费点各一处：`MainActivity.kt:72`、`notify/Notifier.kt:69`/`:100`、`widget/NextBusWidgetProvider.kt:80`、`notify/AlertEngine.kt:96`/`:127`；
                  分域闸仍是**唯一**一道：`nav/ShellDecision.kt:35` `deepLinkTarget`（两支 `-> null` 在 **:36/:37**）+ 唯一消费点 `ui/BusPostRoot.kt:87`。
                  **`D-⑤-1` 收口**：新 `ui/user/BusDetailSlot.kt`（51 行 / 2,206 B，`enum class DetailSlot` :17 四格 + `internal fun detailSlotOf` :39，优先级 **:46-50**，本文件**零 Compose**），
                  选线改到列表落地之后（`BusScreen.kt:145-158` + `openDetail()` **:168-169**），页面只读一个结论（`when (vm.detailSlot())` **:593-600**）。
                  ⚠ **为什么编码不是显示细节**：`unitTests.isReturnDefaultValues = true` 会让任何经过 `android.net.Uri` 的编码在 JVM 用例里静默返回默认值 ⇒ 用例绿、判据绿、线上带 `%2F` 的参数劈成两段。
                    现由 `SR-j`（`Routes.kt` 里 `import android.net.Uri` = **0**）+ 用例"带参数的司机域路由就是开闸形状""参数里的分隔符不会多出路径段"钉住；`DRIVER_ONLY`（:43）四条路由因此**必须无参数**。
                **BUILD_VERIFIED**（终值 = **最后一遍**，不是最好看的那一遍）：`:app:testDebugUnitTest --rerun-tasks` 19:23:06→19:23:45 → `BUILD SUCCESSFUL in 37s` / rc=0 / `24 actionable tasks: 24 executed`、
                  **45 份 XML / 447 tests / 0 failures / 0 errors / 0 skipped**、mtime 集合唯一值 `['19:23:42']`（`/tmp/f6_xml_agg.log`）。
                  ⚠ 中间还有一遍 19:09:09→19:10:27（同样 45/447/0/0/0、`['19:10:24']`）**不作数**：它之后跑了带 `--tests` 过滤的两本补跑（会洗 `test-results/`）且 19:20:14 有源码写入。
                  **447 − 438（D-049）= 9** 恰等于新簿 `RouteDomainDeepLinkTest`（XML 实测 `tests=9 fail=0 err=0 skip=0`）；其余 44 类无机器比对（工程无 git）⇒ 只算"总数差对上"。
                  APK **113,895,828 B @19:21:24 / sha256 `0e0a064689f9e44b471ad5798e3a88d4d15b420948c22d13cd04229771c24d9a`**（`assembleDebug --rerun-tasks` rc=0、**37/37 executed**）
                  —— ⚠ 与前四轮记录的**字节数第五次完全相同、sha 各异**；census **16 dex / defined 29,828 / referenced 36,677（去重 31,894）**，对 D-049 的 29,825 是 **+3**，
                  按描述符只点名 2 个（`Lcom/shanxiang/buspost/ui/user/DetailSlot;` `classes13.dex`×3、`…BusDetailSlotKt;` ×2），**第三个未归因**（#140 同格）；`…RouteDomainDeepLinkTest;` 在包里 **ABSENT**（test 源集，符合预期）。
                  lint `--rerun-tasks` rc=0 / **28/28 executed** / XML 98,753 B @19:23 / `<issue>` **78** = Warning **46** + Information **32** / **Error 0**，本轮三个新符号 **0 点名**
                  （⚠ D-049 记的"79 = 46+32"**分项不自洽**，旧报告已被覆盖 ⇒ 那 1 条差**跨轮不可归因**，不写成改善）。
                  判据面：`check_shell_routing_probes.sh` **16 行读数 rc=0**（`SR-i 1/0/1/1/1`、`SR-j 0/1`、`SR-k 0/13/1`、`SR-l 3/0/1/1`、`SR-m 1/0/1/1/0`、`SR-n 9/19`、`SR-f 0/1`）；
                  结构负控 `shell_routing` **✔=29 ✘=0 △=0**（221 s，本轮新增 16 条注入）；**20 本 `check_*.sh` 终值树上全 rc=0**（`/tmp/f6_probe_sweep.log`）；`v21_requirement_census.sh` rc=0 `SUMMARY|rows=118`；
                  负控全批 **BATCH_RC=1**（`共 20 本`）：18 本 rc=0，`eta ✔=59 ✘=2` 是在册旧项 `D-⑰⑱-1`（复现、未修），`notify_result`/`oss_credits` 各 rc=2（这条纪律**第四次**踩中），
                  批后单本补跑 `notify_result` **rc=0 命中=16 失效=0**、`oss_credits` **rc=0 命中=19 失效=0**（两本 RESULT=PASS）⇒ **不得写成"20 本全绿"**。
                  树指纹 **files=160 sha256=`b87ad1ab6148831a2507657822390eb299e53ff40652c0f6915503dc979fb48a`**（158→160 = 本轮两个新文件；命令原文 = D-045 第 2 节那段 python）。
                  ⚠ 分叉写法：**若下一轮改 `app/src` 内容 ⇒ 此值必变**（D-049 那条"应仍为 368b2f…"的预测本轮就是被自己改源码证伪的）；**若下一轮一字未改而指纹变了 ⇒ 有工具在动内容**，按 §八 ㉘ ⑩ 从备份比对并回。
                  ⚠ **环境级一次**（不是用例失败）：`testDebugUnitTest` 起跑前连红 **4** 次，报 `Could not write standard input to Gradle Test Executor N / 管道正在被关闭`；
                    定性 = Gradle 8.9 的 worker `@argfile` 用守护 JVM 默认字符集（JDK 18+ = UTF-8）写盘、Windows `java.exe` 按 cp936 解释 ⇒ 用户名"袁"乱码（判据字节：UTF-8 含 `e8 a2 81`、GBK 含 `d4 ac`）；
                    处置 = **只在命令行**加 `-Dfile.encoding=GBK`，`gradle.properties`/构建脚本/源码/用例一字未动（"环境坏了不许改代码绕过"）。
                **DEVICE_VERIFIED**：**未到位**（0 台在环，`verify_device.sh --no-build` rc=1 / `DEVICE_UNAVAILABLE`，新证据 `docs/device-evidence/20261002-191632/`）。
                  `buspost://app/…` 从通知与小组件点进来究竟落哪一屏、非司机角色点司机域深链是否真的停在原页、四态在屏幕上各长什么样 —— **一次都没看过**，两问都只有 JVM 与 grep 证据。⇒ 整体记 `ANDROID_F3_⑥_PARTIAL`。
                **在册不当已解决**：`D-⑥-1`（`deepLinkTarget` 对陌生路由放行 = 设计，已钉但不改白名单）、`D-⑥-2`（NavHost 对 `DRIVER_ONLY` 的**注册**无角色闸，实测 `SR-e=0` ⇒ 不加第二道闸以免两份真相；#147 引入司机页跳转时同轮重判）、
                  `D-⑥-3`（`Notifier.ongoing` 投司机壳、过闸后回本角色壳 = 效果正确，文案不改因需真机）、`D-⑥-4`（:144 那句注释里的假符号名**本轮已修**，并按"改注释也当改源码文本"跑了字面量重叠扫描 **0/3,427** + 判据簿改前后 `diff` 逐字节相同）、
                  #140（dex +3 只归因 2/3）、#159（67 条 App 锚点未重锚）、`D-⑰⑱-1`、DEVICE/RELEASE/GIT。
                ⚠ **台账侧本轮抓到的一条假话（不是源码缺陷，如实记）**：D-049 §5 `D-⑤-4` 那格写"本轮已在状态板补该列"，而现读 `grep -an ParcelScreen.kt AI_ITERATION_STATE.md` = **0 命中**
                  ⇒ 那张宽口径逐文件构成表**上一轮从未落进状态板**（那句是"写早了的话"；是否与 ㉘ ⑩ 那次整行替换丢内容同源**无法证实**，按未归因登记）。本轮真落档 ↓（18 行，分项和 = 7+5+5+4+15+6+6 = **48** = 总数，这是自证）。
                **台账终检（22:29:34 那遍 = 22:25:07 的同值复现；本句这次行内替换之后还会各跑一遍，以 `_last` 那三本日志为准 —— 若那一遍读数不同就必须再改本行，不许沿用旧值**：五本 `check_ledger_tables.py` rc=0 / **TOTAL FLAGS 0**（`/tmp/f6_ledger_tables_last.log`：行数 3506 / 3283 / 1391 / 3252 / 524，本块自己那 4 行算在内；1391 含本轮追加的 §八 ㉙ ⑧ 那 4 行）；
                七本 `check_doc_anchors.py` rc=0 / `[聚合] anchors=1209 out_of_range=0 not_found=15 miss=0`（同一串，`/tmp/f6_ledger_anchors_last.log`）；
                与 `/tmp/f6_ledger_bak/` 快照行级 multiset 比对 **纯丢失行 = 0**（`/tmp/f6_ledger_multiset_diff_last.log`，脚本 `/tmp/f6_multiset_diff.py`、similarity 阈值 0.7；2 行"旧行不在当前集合"实测均为就地改行：V-2 判据行 1,889→3,197 字符（**现行号 1802**，本块那 4 行把它后移了 4 —— 写这行时记的 1798 已被自己证伪）、`DECISION_LOG.md` 第 1825 行那条跨台账锚 163→218 字符）；
                判据簿复跑 `check_shell_routing_probes.sh` rc=0 与 19:2x 那份逐字节同，`v21_requirement_census.sh` rc=0 `V21-30` 名单 20 / present 7 / ABSENT 13（**新簿不在名单里，present 未 +1** ⇒ 记 `D-⑥-5`）。
                宽口径逐文件命中构成（`check_authenticity_counts.sh` 实抄，读数时刻 19:2x、终值树；下一轮要归因就 diff 这张表，不是 diff 那个总数）：

| 文件 | 命中 |
|---|---|
| `ParcelScreen.kt` | 7 |
| `WorkbenchScreen.kt` | 5 |
| `MissionMarkers.kt` | 5 |
| `DriverNavScreen.kt` | 4 |
| `Repo.kt` | 3 |
| `PolylineDecision.kt` | 3 |
| `MapCanvas.kt` | 3 |
| `HomeScreen.kt` | 3 |
| `BusScreen.kt` | 3 |
| `NavigationEngine.kt` | 2 |
| `AmapNavigationEngine.kt` | 2 |
| `AlertEngine.kt` | 2 |
| `TraceView.kt` | 1 |
| `SendScreen.kt` | 1 |
| `Models.kt` | 1 |
| `DriverSdkNavCard.kt` | 1 |
| `DriverMissionMap.kt` | 1 |
| `BusMap.kt` | 1 |

                （窄口径 **9** / `GeoMath` **27** / `DemoData` 文件 **5** / 假步进 **0** 与 D-049 逐项相同；这些命中全是**禁止性说明与远近文案标注**那一族，不是伪造几何 —— 口径只能抬高不能放宽。）
CURRENT_ANDROID_STATUS:  F-3 ⑤ 收工（2026-10-02 09:0x–10:5x，源码/判据止于 09:11，负控全批 10:07:39 结束、终值全量 10:08:32、打包与 lint 到 10:22:58、设备闸门 10:25:55；三态分写、不混写、不用百分比；明细 = **D-049**，教训 = **§八 ㉘**）

                **SOURCE_COMPLETE**：本轮买的是**"线路页折线"从一句话说得通收成三格读得出**。
                  新 `core/map/PolylineDecision.kt`（69 行 / 4,183 B / 09:00:58）：`internal enum class PolylinePhase`（**:18**）三格
                  `NotAsked`（:20）/ `Asking`（:23）/ `Asked`（:26）+ `shouldAskPolyline()`（:37）+ `polylineSubtitleOf()`（:47）+ `polylineReasonOf()`（:61）——
                  **不持状态、不落盘、没有 flow**，那句"后端未回传该线路折线"（:52）**只在 `Asked` 下可达**；页面两个 wrapper（`BusScreen.kt:194/:210`）只 delegate。
                  ⚠ **本轮自己踩的那条 KDoc 反噬**：在决策文件的 KDoc 里用 ASCII 引号抄了那句成品文案 ⇒ `grep -cF` 立刻 2 份、`P_EMPTY_TEXT_PDEC` 红
                  （`grep -cF` **不过滤注释行**）；"恰好 1 份"类判据必须走 `grep -nF … | grep -vE "$NC" | wc -l`（`SA-e2` 现口径）。
                **比文案更严重的那半边**：迟到回包会把 **A 线的真实折线写进 B 线屏幕**（不需要假数据就成立，正面撞 D-003 那侧的"宁可没有路线，不允许假路线"）。
                  现由 `BusScreen.kt:342` 的 `if (mySeq != polylineSeq || selected?.id != id) return@work` **双条件闸**挡住：序号（:111 声明 / :172 自增）挡同线旧回执，
                  线路编号挡切线后的旧请求；相位写入 `Asking`:173 / `Asked`:188 / 取消退回 `NotAsked`:192。**负控 23 号钉的是"只留半个闸"**（条件数也会假装成立）。
                **BUILD_VERIFIED**（终值，负控全批之后）：`:app:testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL in 51s` / rc=0 / `24 actionable tasks: 24 executed`、
                  **44 份 XML / 438 tests / 0 failures / 0 errors / 0 skipped**、mtime 集合唯一值 `['10:08:30']`（`/tmp/after_negctl.log` @10:08:33、`AFTER_RC=0`）。
                  新簿 `PolylineDecisionContractTest` **15 例**（12 组合穷举 + `assertEquals(1, hits)` 钉"唯一出口"是**性质**不是条数）。
                  APK **113,895,828 B @10:21:54 / sha256 `c82980329a7037ace44cdee39903fcbf6251e624cc43b5fbc76c3d3ac01fe4cc`**；
                  census **16 dex / defined 29,825 / referenced 36,662（去重 31,890）**；**本轮代码进包已用字节证实**：`PolylineDecision` `classes8.dex`(3)/`classes13.dex`(1)、`polylinePhase` `classes13.dex`(1)；
                  lint XML **98,753 B @10:22:58 / 79 issues = Warning 46 + Information 32 / Error 0**，`PolylineDecision`、`BusScreen` **0 点名**
                  （⚠ 第一次读到的是 `lintReportDebug UP-TO-DATE` 的**上一轮报告** ⇒ 报告类读数必须 `--rerun-tasks` 之后才算）。
                  判据面：`check_safe_call_probes.sh` **21 行读数 rc=0**（`SA-e2 … = 1/3/1/1/1/1`、`SA-e2 全树份数/决策文件 = 1/1`、`SA-e3 首屏问/序号/自增/双条件闸 = 1/1/1/1`、
                  `SA-e4 三态行为簿 = 15/4/1`）；结构负控 `safe_call` **✔=29 ✘=0**、破口 **26 条 + 自检 3 条**（195 s）；**20 本 `check_*.sh` 复跑全 rc=0**（`/tmp/final_probe_sweep.log` @10:18:46）、
                  `v21_requirement_census.sh` rc=0 `SUMMARY|rows=118`；authenticity **窄 9 / 宽 48 / `GeoMath` 27 / `DemoData` 文件 5 / 假步进 0**（本轮 46→48 的**归因已实测**，见下方宽口径那一行）。
                  负控全批 **BATCH_RC=1**（`共 20 本`，`/tmp/final_neg_batch.log` @10:07:39）：18 本 rc=0，`eta ✔=59 ✘=2` 是在册旧项 `D-⑰⑱-1`（复现、未修），
                  `notify_result`/`oss_credits` 各 rc=2 是"依赖产物的簿排在产物被 `--tests` 洗掉之前"，批后单本补跑 **✔=17 / ✔=20、两本 RESULT=PASS**（这条纪律**第三次**踩中）⇒ **不得写成"20 本全绿"**。
                  编译期一次真失败已分型：`BusScreen.kt:98:9 Property 'public' exposes its 'internal' type 'PolylinePhase'`（`/tmp/f3_5_compile1.log`）→
                  `classify_verification_failure.sh` 打 `RULE|SOURCE_FAILURE|…hits=1|first_line=30` + `VERDICT|SOURCE_FAILURE`；处置是把**属性**改 `internal`，**不是**把枚举提 public（那会让内部相位变成对外契约 = `D-⑤-2`）。
                  树指纹 **files=158 sha256=`368b2fc986d848fa031887f4c1892a391d0aa57eb129d6802b8133fa0b53cb27`**（156→158 = 本轮两个新文件；算法与命令原文见 D-045 第 2 节，本轮同一条命令）。
                **DEVICE_VERIFIED**：**未到位**（0 台在环，`verify_device.sh --no-build` rc=1 / `DEVICE_UNAVAILABLE`，新证据 `docs/device-evidence/20261002-102553/`）。
                  三态在屏幕上**一次都没看过** —— "正在要折线"长什么样、切线后旧回执是否真的没画出来，两问都只有 JVM 与 grep 证据。⇒ 整体记 `ANDROID_F3_PARTIAL`。
                **在册不当已解决**：`D-⑤-1`（`BusDetailScreen` 同形状缺陷：异步 `load()` 未落地就 `lines.firstOrNull { it.id == routeId }` ⇒ 标题/折线可能不是那条线路，排 ⑥）、
                  `D-⑤-2`（`polylinePhase` 可见性口径）、`D-⑤-3`（**台账锚点多轮漂移**：本轮 12 处 `BusScreen.kt:*` 全部错位、两条跨台账锚点早已漂而 `check_doc_anchors.py` 不报）、
                  `D-⑤-4`（宽口径 46→48 此前**无逐文件构成在册** ⇒ 本轮起把 18 文件构成落档，见下表）、#140（APK 增量 585,162 B 未归因，产物已被覆盖）、`D-⑰⑱-1`、DEVICE/RELEASE/GIT。 ⚠ **台账侧本轮唯一缺陷（不是源码缺陷，如实记）**：状态板 `safeApiCall` 那一行被**整行替换**写丢了两个单元 + 1,300 余字 SA-a…SA-f 读数， 靠 `check_ledger_tables.py` 的 **TOTAL FLAGS 0→2** 抓到、按 **05:28:12** 的 `cp -p` 快照**按格并回**（规矩 = §八 ㉘ ⑩，证据 = D-049 §8-bis）； 终检五遍：10:53:16 **红（2）** → 10:56:25 / 10:58:50 / 10:59:57 / 11:01:07 **绿（0）**，锚点簿 `out_of_range=0 / miss=0`，`not_found` 14→**15**（多出的那条是本段同族自噬，已在 8-bis 点名）
CURRENT_ANDROID_STATUS:  F-3 ④⑫ 收工（2026-10-02 02:1x–05:4x，源码/构建部分止于 05:10，台账自检跑到第五遍；三态分写、不混写、不用百分比；明细 = **D-048**，教训 = **§八 ㉗**）
                **SOURCE_COMPLETE**：本轮只买一件事 —— **"这一屏能不能点导航"从两处拼法收成一处**。
                  新增 `enum NavigationGateCode`（`core/navigation/NavigationEngine.kt:25`，六格 `READY / NOT_INSTALLED / SDK_UNAVAILABLE / INITIALIZING / NO_ROUTE / ERROR`）
                  与纯派生函数 `navigationGateOf(readiness, hasOrigin, hasNavigableDestination)`（**:116-148**：`Blocked` 原样透传 `code` :122、
                  `Initializing` 独立成格 :127-131、缺起点 :134-138 与"调度没给可导目标点" :140-144 各产一个 `NO_ROUTE`）；
                  `readinessOf()`（:238）三个 Blocked 分支分别给 `NOT_INSTALLED`（:244）/ `SDK_UNAVAILABLE`（:250）/ `ERROR`（:258）。
                  **gate 不持状态、不落盘、没有 flow** ⇒ 它是同一份真相的**读法**、不是第二份真相（这句必须写着，否则下一轮看到多出来的类型会以为是叠层）。
                  **断点的形状**：判据层对 `Initializing` 说"不可点"，页面旧写法 `enabled = !blocked && …` 对它说"可点"（`blocked` 为假）——两边各自都"对"，
                  司机会点一个引擎还没答应的按钮。现在引擎侧两道闸改读同一个 `state.canNavigate`（`AmapNavigationEngine.kt:427` 算路 / `:195` 起步），
                  SDK 层不再自己判 `is EngineReadiness.Blocked`（`N-s 2/0`）⇒ **回执里那句 reason 与按钮上那句原因是同一句话**（用例 `替身引擎拒绝算路的原因与入口闸门一致`）。
                  UI 一处入口：`ui/driver/DriverSdkNavCard.kt:157-161` 取 gate，**五条出口**全部派生 —— 底色 `warm`（:86）、tag `gate.userText`（:95-102）、
                  不可点时副标题 `gate.detail`（:104-106）、`enabled = gate.canNavigate && !calculating`（:129）、不可点原因块（:157）；
                  **这一屏 `BuildConfig` 生效行 = 0**（`N-q`）。⚠ **旧假导航屏那 8 处 `BuildConfig.DEBUG` 一处没拆**（D-003 红线，`N-n` 钉），
                  与 `N-q` 是**两条**判据、**不许合并成"DEBUG 归零"**。
                  用例 `NavigationContractTest` **19 → 26**（新小节"一之二、导航入口闸门"7 条）；判据 **十六条 → 二十行**（新增 `N-q`/`N-r`/`N-s`/`N-t`，含 `NT4`）；
                  结构负控 **27 → 34** 条破口；用例级 **M1…M8 → M1…M11**。
                  **没动后端、没动微信小程序、没新增页面 / Demo 功能、没新增三方依赖、零新增文件**（树指纹 `files=156` 与 D-047 同数）
                  ⇒ `THIRD_PARTY_NOTICES.md` 与运行矩阵本轮**无需新条目**（写出来是为了让下一轮看得见"我核过，不是漏了"）；
                  HTTPS 闸门 `app/build.gradle.kts:171` 原文未动、**未传** `-Pbuspost.allowInsecureRelease=true`、`gradle.properties` 未动（GBK 配方只加在命令行）；
                  工程内 `.jks`/`.keystore` **0** 命中、`app/src/main` 对 `127.0.0.1` / `localhost` **0** 命中、没有 `git init`。
                  ⚠ **本轮唯一用户可见文字变化**：可点态 tag 由页面自写的"内置导航可用"改成 `NavigationGate.userText` 的**"导航可用"**
                  （不可点侧统一前缀 `导航暂不可用：`，用例钉住）—— 工程无 Robolectric ⇒ 这句在 JVM 里渲染不出来，只有静态判据与断言两层证据。
                **BUILD_VERIFIED（debug 面）**：三段一体 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --rerun-tasks` →
                  **`BUILD SUCCESSFUL in 2m 12s` / rc=0 / `53 actionable tasks: 53 executed`**（`/tmp/final_full_run2.log` @04:47:40）；
                  **43 份 XML / 423 tests / 0 failures / 0 errors / 0 skipped**（mtime 集合只有一个值 **04:46:37**，逐份读 `testsuite` 属性自相加）；
                  `NavigationContractTest.xml` **`tests=26 failures=0 errors=0 skipped=0`**；
                  APK **113,895,828 B @ 04:46:39**、sha256 `2c80b14f8ba9b0a708c6c113c0866e6d99ca2c5c6d1ad08793bde129ccda7fdb`
                  —— ⚠ **字节数第四次与 D-045/046/047 完全相同、sha 各异** ⇒ "字节数不单独当变更证据、必须连 sha256 + 树指纹成对抄"再复现一次；
                  本轮另多一条**反向**教训：03:37:54 与 04:46:39 两遍**同输入重打的 sha 完全相同** ⇒ 摆动**不是每次都发生**，
                  **不许**反过来拿"这次一样"当"打包已确定"的证据；树指纹 `app/src` **files=156 sha256=051cfa8a8e02c0e3f7d38b20fe9ba6e08788f8c1116ee62084ac087bee2e2a28**；
                  lint **0 errors / 46 warnings**；
                  **20 本 `tools/check_*.sh` 全部 rc=0**（`/tmp/final_probe_batch.log`），导航簿二十行读数
                  `N-a 26 · N-b 0 · N-c 0 · N-d 0 · N-e 1/0/1 · N-f 1/1/2 · N-g 1/1/1/1/1/1 · N-h 2/2/0 · N-i 1/0/0 · N-j 9/0/0 · N-k 7/0 · N-l 0 · N-m 0 · N-n 0/1/1 · N-o 2/1 · N-p 0/1/1/0 · N-q 0 · N-r 1/1/0/1 · N-s 2/0 · N-t 6/6/2/1`；
                  负控全批 `共 20 本`、**`BATCH_RC=1`**（**不是**"全绿"，红在哪逐条写清）：`eta ✔=59 ✘=2` = 在册旧项 `D-⑰⑱-1`（复现、未修），
                  `notify_result` / `oss_credits` 各 rc=2 是簿内"基线不绿则整份不采信"那道闸门在拦（批内就地型那几本的 `--tests` 过滤把 `test-results/` 洗了），
                  终值全量重跑后**单本补跑** ✔=17 / ✔=20、两本都 `RESULT=PASS`；其余 17 本批次内即 `rc=0 ✔>0 ✘=0 △=0`（含 `navigation ✔=35`、`navigation_test ✔=13`）；
                  ⚠ **批次前后 `app/src` 指纹逐字节一致**，但有 **9 个源文件的 mtime 落在批次时间里** —— 那是还原用的 `cp` 留的、**不是改动**
                  ⇒ **"mtime 不能当改动面证据"第三次实测到**（§八 ⑲ ③ 同源）。
                  导航三本单簿：结构负控 **✔=35（1 基线 + 34 破口）/ ✘=0 / △=0**；用例级 **基线 `exit=0 failures=0/26` → M1…M11 十一次全红**
                  （逐条 failures+errors = 1/1/2/1/4/1/1/1/3/2/2，每次都红在 `NavigationContractTest`）→ `✔ 恢复核对：与改前基线逐字节相同` → 恢复后复跑 `exit=0 failures=0/26`。
                **release / device / git（各自单算，不许顶替上面两态）**：release 仍 **BLOCKED**（`app/build/outputs/apk/` 下只有 `debug`，
                  `assembleRelease` 被 HTTPS 闸门挡在配置期，**没有**用 `-Pbuspost.allowInsecureRelease=true` 绕过）；
                  **DEVICE_VERIFIED = 0 条** —— 本轮收工重跑 `bash tools/verify_device.sh --no-build` → **rc=1 / `DEVICE_UNAVAILABLE`**，
                  `List of devices attached` **空** + adb 原文 `adb.exe: no devices/emulators found`，证据 `docs/device-evidence/20261002-050958/`、`20261002-051008/`
                  （同一条命令连跑两遍：第二遍只为把 rc 单独抓出来，两份内容一样）⇒
                  **闸门在真机上没点过一次**：`Initializing` 到 `Ready` 之间那一段时间按钮是否真的灰着、tag 念的是哪句、`gate.detail` 那串技术文本在屏幕上长什么样，
                  **全部只有 JVM 断言**；GIT 仍 **ENVIRONMENT_BLOCKED**（`fatal: not a git repository`，未 `git init` 造假历史）。
                **本轮新增 OPEN_DEFECT 2 条**：`D-④⑫-1` 不可点时副标题打的是 `gate.detail`（`initResult=null` / `hasOrigin=false（不用终点或默认点顶替）` 这类技术串），
                  给司机看是否过于工程口吻**本轮未判**；`D-④⑫-2` `hasNavigableDestination` 由页面 `nextNavigableStop(guide)` 供给，
                  **"页面算目的地"这一层没被判据钉**（只钉了它等于 1 处调用）⇒ ⑦⑨ 任务点统一模型收口时一并核。全文在 **D-048 第 7 节**最后一行。
                **整体状态**：**④⑫ 收口**（④⑫ 指令原话的三件齐：入口按 readiness 决定能不能点、不可用时说得出具体缺什么、`BuildConfig.DEBUG` 不当业务能力开关；
                  ⑫ 明写"不接完整导航，只做 readiness"⇒ 应用级 `NavigationService` 单例（V21-08）与 `AcceptedRouteSession` + `routeFingerprint`（V21-09）**一字未动**，两行仍 ❌）；
                  F-3 全队列未收（⑤ / ⑥ / ⑦⑨ / ⑧ / ⑬⑭⑯⑰ / ⑱⑳ 仍在，`D-②-1…4` 的"真待收口 23"也在后续项）
                  ⇒ 按指令 ⑳ 记 **`ANDROID_F3_PARTIAL`**，**不写百分比**。台账自检（表格 + 锚点 + 判据簿复跑）的读数在 **D-048 第 8 节**，
                  那一节是本轮台账**最后一次写入**（14 本判据簿把三份台账文件名写进了脚本，改台账会推动读数）。
                  ⚠ 上面那句"最后一次写入"**当轮就被自己推翻并改写了**：§8 之后又补了三段（第二/三/四遍读数）+ `CONTEXT_LOCK` §八 ㉗ ⑩，
                  并用**第五遍复跑验证了 §8 里写下的预测**（预测 = 该节自身 +3 行 ⇒ 下一遍应读到 `DECISION_LOG` 行数 2914、`anchors` 1168、`not_found` 14）——**实测逐字命中**，
                  ⇒ 台账自检只有 `TOTAL FLAGS` / `out_of_range` / `miss` 三项是不变量，"行数"与 `not_found` 会随解释文字自指地变大（已升 **§八 ㉗ ⑩**）。
                  ⚠ 本块这两行落笔后，`AI_ITERATION_STATE.md` 的行数不再是 D-048 第 8 节记的那个值：**第六遍实测 3384 → 3388**（本块净增 4 行，`wc -l` 与工具读数一致），
                  而同一遍的 `anchors` 1168 / `not_found` 14 / `out_of_range` 0 / `miss` 0 **一字未变**（diff 只有两条 not_found 的行号各 +4，即上面那四处插入把下方内容整体推下去）——这就是 ㉗ ⑩ 说的"自指只动行数、不动不变量"。
                **NEXT（F-3 队列，④⑫ 已收口）**：**当前项 = ⑤** 线路页折线三态与文案诚实化（队列 #144：首屏自动选中线路却**不拉折线**、把"没问"说成"没有"那句**同族假话**，
                  `SA-e` 至今允许它存在、D-042 登记在册）；其后 ⑥ USER 闭环与分域深链、⑦⑨ DRIVER 逐按钮对真后端 + 任务点统一模型（顺带收 `D-④⑫-2`）、
                  ⑧ 任务地图无路线态、⑬⑭⑯⑰ 导航契约与路线绑定（V21-09 那两行 ❌）、⑱⑳ 收尾；`#140`（APK 历史增量归因）与 `#159`（67 条锚点内容级重锚）仍在册。
                ⚠ **本轮造成的"历史行漂移"集中登记（一条都不回改 —— 那些是各自当轮的实测值；下一轮不许当现状读）**：
                  ① **`NavigationContractTest` 19 条 / 判据"十六条" / 结构负控"27 条" / 用例级"M1…M8"** 这四组旧口径**按内容**分布在这些地方
                    （本轮上方新块让本文件行号整体 +6，所以这里**不写行号** —— 这正是 §八 ㉗ ⑤ 要求的行为）：
                    本文件 F-2 ⑧ 块里「导航簿 N-a…N-p 十六条复抄」那一行、`N-a 判据层用例条数 = 19` 那一行、常驻自检表「第 8 步导航引擎」那一行、
                    #51 / D-032 那段"结构负控 **18 → 27 条破口**"那一行；`CONTEXT_LOCK.md` 第二节"导航层"那条（本轮已在旧句**下方追加**新口径、旧句一字未删）；
                    `THIRD_PARTY_NOTICES.md` 里"27 条"那一处（本轮**没改**它：那是三方许可证清单，不是状态板）。现状读 **D-048 第 7 节**那张表。
                  ② 本文件「F-3 ③ 收工」那块里的 NEXT「**当前项 = ④⑫**」是 ③ 收工那一刻的指针，现状看上面这一整块。
                  ③ ⚠ **本轮自己犯的那一次、也是最该带走的一条**：D-048 第 1 节首版把 `NavigationEngine.kt` 的七个锚点抄成 `:113-145 / :241 / :247 / :255 / :125 / :132 / :138`，
                    收工重测**全部少 3 行** —— 因为同一轮里我改了这份文件的 KDoc（净增 3 行）。**`check_doc_anchors.py` 一条都没报**（旧行号仍在文件范围内）
                    ⇒ §八 ㉖ ① 那条"改了源码文本就把每一本簿真跑一遍"**要连台账里指向这份文本的锚点一起算**；处置 = 就地改正 + D-048 第 7 节加一行登记这次改正，已升入 **§八 ㉗ ⑤**。
                  ④ 本文件 `TEST_STATUS` 头行本轮**改了**（38/368 → 43/423，并在下一行标明"以下保留 F-2 ⑧ 那一遍的读数"）：
                    这不是回改历史 —— F-2 ⑧ 那一段原文一字未动，改的是**"当前值"这一格该写谁**（此前 F-2 ⑨–⑱ 与 F-3 ②③ 六轮都没更新它，中间值只在自己块里，那一格一直停在 ⑧）。
CURRENT_ANDROID_STATUS:  F-3 ③ 收工（2026-10-01 19:0x–22:15，三态分写、不混写、不用百分比）
                **SOURCE_COMPLETE**：③ 指令原话点名的四件（删 `DriverShell.kt` 的角色写入 / 引入两层状态 / "任何情况下 accountRole 不允许因 UI 页面切换而修改" /
                  `DriverRoleIntegrityTest`）**全部落地**。
                  新增 `core/AppDomain.kt`（40 行 / 2,166 B）：`AppDomain.of`（:21，冷启动默认壳）+ `domainFor`（:29-30，**司机视图只对确实是司机的账号开放**，回用户域永远允许）+
                  `domainAfterRoleChange`（:39-40，**只跟着离开司机域、绝不跟着进入**），三个都是纯函数、`internal`。
                  `AppState` 加 `_activeDomain`（:27-28 初值跟 `Prefs.role`，**进程级、不落盘**）与两个唯一写入口：`setActiveDomain`（:112-114，写之前过 `domainFor` 闸）、
                  `setAccountRole`（:123-126，写 `Prefs.role` 之后过 `domainAfterRoleChange`）；**死镜像 `AppState.role` 已删**（它与 `Prefs.role` 是两份真相）。
                  动线逐处落点：`DriverShell.kt:38` 由 `AppState.setRole(Role.USER)` 换成 `AppState.setActiveDomain(AppDomain.USER)`（:37 `if (it == 3) {` 与 :39 出壳导航一字未动）、
                  显式授予拆两行 `MineScreen.kt:97-98`、档案降级 `WorkbenchScreen.kt:180`、登录落壳 `BusPostRoot.kt:116`、
                  **后台提醒的分域依据从"账号角色"换成"在看哪个域"** `AlertEngine.kt:45/:57`（这一条才是"点用户版能真的停下司机域轮询"的功能本身）。
                  新增 `app/src/test/…/core/DriverRoleIntegrityTest.kt`（107 行 / 4,759 B / **6 例**：切回用户版只改视图不动账号角色 / 非司机账号切不出司机视图 /
                  撤档降级时视图离开司机域 / 后端授予身份不会自动把视图搬进司机域 / 撤档之后切不回司机视图直到身份重新被授予 / 两层各自的真值表）。
                  ⚠ **没有新建一个叫 `AccountRole` 的类型**（登记 `D-③-4`）：账号角色那份事实仍由 `Prefs.role` 承担，再包一层同名类型只会多出一个可写入口，与"不叠第二份真相"冲突。
                  判据同轮扩建：`RR-b3`、**`RR-e2 0/1`**（司机壳里的角色写入 0 处 / 切视图那一行 1 处 —— 换字面量之后"改回 `setRole`"这件事的正面闸）、
                  **`RR-f 2/0`**（按域轮询两处 / 残留按角色轮询 0 处）、`RR-e` 期望 **3/2/3→2/2/2**、`RR-c` 改成**相对行号**判据（`DEN<SFT`、`RET∈DEN+1..DEN+22`）、
                  `SR-f2` 锚点换成新字面量整行、`P-c` 改成"两层写入点合并数"、负控补 `RRt-7…RRt-10` 四条（"规则在"与"接线没绕开"分两层钉）、备份/`cmp` 面从 3 个源文件扩到 **5 个**。
                  **没动后端、没动微信小程序、没新增页面 / Demo 功能、没新增三方依赖（`app/build.gradle.kts` 依赖声明 26 行不变、`THIRD_PARTY_NOTICES.md` 与运行矩阵本轮未动 = 没有可署名的新东西）、
                  HTTPS 闸门 `app/build.gradle.kts:171` 原文未动、未传 `-Pbuspost.allowInsecureRelease=true`、工程内 `.jks`/`.keystore` **0** 命中（签名属性在工程外 `:50`）、
                  `app/src/main` 对 `127.0.0.1` / `localhost` **0** 命中、没有 `git init`。**
                **BUILD_VERIFIED（debug 面）**：三段分别 `BUILD SUCCESSFUL` —— `:app:testDebugUnitTest` 45s / 24 tasks（`/tmp/f3_3b_fulltests.log` @21:20）、
                  `:app:assembleDebug` 38s / 37（`/tmp/f3_3b_assemble.log` @21:21）、`:app:lintDebug` 1m32s / 28（`/tmp/f3_3b_lint.log` @21:31）；
                  用例 **43 类 / 416 例 / failures 0 / errors 0 / skipped 0**（计数只读 `app/build/test-results/testDebugUnitTest/*.xml`，最新 mtime 21:20:43；
                  42→43 = 那本新测试类，其 XML 实测 `tests=6 failures=0 errors=0`，条数由判据 `RR-a` 第三列 =6 钉住）；
                  lint **78 issues：Error 0 / Warning 46 / Information 32**（`lint-results-debug.xml` 98,753 B @21:31:01 ⇒ 与 D-046 的 78 同形，本轮净增 **0**）；
                  APK **113,895,828 B @ 21:21:23**、sha256 `c11db0d3806c74aeca3762d2f3f8640deb08ae3a5d9b1e80a1c6a15285f9af95`
                  —— ⚠ **与 D-046 17:33 那份字节数完全相同、sha 不同**（`e3d387e7…`）：**同一对形状第二次实测到** ⇒ 字节数不单独当变更证据，必须连 sha256 + 树指纹成对抄；
                  `app/src` 树指纹 **files=156 sha256=ad418a2477257f3f27b663bdac4810c85758348ba6945c4107fc2b0f259496f2**（命令同 D-045 第 2 节，154→156 = 本轮两个新文件）。
                  判据面：**19 本 `tools/check_*probes.sh` 逐本 rc=0**（20:12–20:13，逐本留档 `/tmp/probe_check_*.log` 19 份；`tools/` 现数恰 19 本，D-046 说的"20 本"那本不在这个命名集合里）。
                  分域簿：判定数 `P-a 0 · P-a2 0 · P-b 0 · P-e 1` 全中；观察值 `P-b2 21 · P-c 5 · P-d 3 · P-f 8`。
                  ⚠ **闸门末行只有那一条 `[ $pa=0 ] && [ $pa2=0 ] && [ $pb=0 ] && [ $pe=1 ] || exit 1`** —— `P-c`/`P-d` 虽然印了"预期 5 / 预期 3"，
                  但**脚本根本不判它们**（本轮实测：末行里没有 pc/pd），所以那四个数怎么变这本都不会红 ⇒ 抄数必须连**构成**一起抄（构成已在脚本 echo 里逐处点名）。
                  角色同源簿：`RR-a 5/4/6 · RR-b 0/0 · RR-b3 1 · RR-c 181/197 + 1/1 + RET 194 · RR-d 1/1/2 + 1/1 · RR-e 2/2/2 · RR-e2 0 · RR-f 2/0`，末行"角色同源判据通过"。
                  源码侧对账：`Prefs.role =` main 源集实测 **2 处**（`AppState.kt:190` 唯一入口内部 + `Repo.kt:171` 登录落盘）、**页面 0 处**；
                  `setRole(` 在 main+test 只剩 **3 处且全是注释/KDoc**（`AppDomain.kt:8`、`AppState.kt:110`、`MineScreen.kt:87`，**可执行调用 0**）；
                  V2.1 普查同形状 `V21-03|旧角色污染写法|hits=2|files=2|code=0`（**code=0** 才算真归零）。
                ⚠ **本轮负控的形状**（结构 13 本 + 本轮两本 + 用例级 5 本，逐本 ✔/✘ 表在 `D-047` 第 2 节）：结构 13 本除 eta 外全 `✘=0`，
                  **eta 仍 `✔=59 ✘=2`**（`T-e`/`T-w2` ⇒ `D-⑰⑱-1` 维持 2 条，本轮未动）；本轮新/改的两本 `role_reconcile 16 ✔`、`shell_routing 13 ✔`；
                  用例级 `role_reconcile_test` **10 ✔（`RRt-1…10` 逐条 gradle 真红，failures+errors 1/2/3/2/1/2/3/2/2/1）+ `EXIT=0` + `cmp 五个源文件与备份一致`**，
                  exception 8 / navigation 10 / shell_routing 7 / idempotency **1 条破口注入后红 10 例**（还原后 `cmp 一致` + rc=0）。
                  ⚠ **21:00 那批两本红是"顺序红"不是"判据红"**：`notify_result` / `oss_credits` 原文各 `真身没有 …XML —— NW-h / OS-f 的读数没有来源，先跑 :app:testDebugUnitTest`
                  （这两本的判据读测试真身，必须排在 21:20 的全量之后）⇒ 21:23 / 21:29 复跑各自 `✘=0`；
                  **没有把 21:00 那两份红算进"负控全绿"，也没有为了变绿去改判据**（"依赖产物的簿排最后"这条纪律第二次实测到，第一次是 D-046 的 16:59 批）。
                **release / device / git（各自单算，不许顶替上面那两态）**：release **BLOCKED** —— `app/build/outputs/apk/` 下只有 `debug`，产物从未产出、`lintRelease` 未跑；
                  **DEVICE_VERIFIED = 0 条** —— 22:15:01 `bash tools/verify_device.sh --no-build` → `List of devices attached` **空** ⇒ `[STOP] … DEVICE_UNAVAILABLE`，
                  新证据 `docs/device-evidence/20261001-221502/`（`device-probe.log` / `summary.md`）；
                  ⚠ "真机上点一次『用户版』"这件事从没做过：壳真的换了没有、司机域轮询真的停了没有、`DriverLocationService` 真的被停掉没有、切壳后角标与页面栈长什么样，**全部只有 JVM 断言**；
                  本轮新写的 6 例都在 JVM 里跑，**没有任何一条是渲染验证**。GIT **ENVIRONMENT_BLOCKED** —— `fatal: not a git repository`。
                **本轮新增 OPEN_DEFECT 5 条（D-③-1…5，全文在 `DECISION_LOG.md` D-047 第 6 节）**：
                  ① `RRt-2` 基线偶发（同一条注入两次读到 3 与 2；隔离复测 + 两次 `--rerun-tasks` 干净基线均 0 红 ⇒ 判为偶发、**机制未定位**，
                     **没有**靠放大 `awaitUntil` 超时把它抹掉；证据 `/tmp/flake_1.log`、`/tmp/flake_2.log`、`/tmp/rrt2_runA.log`）；
                  ② **echo-only 的数不可归因**：`P-b2` / `P-c` / `P-d` / `P-f` 的"上一轮是多少"这条链断了（② 那两份批次日志只存逐本 rc、不存逐本读数），
                     本轮能核的只有**现状构成**（脚本输出里逐处点名）⇒ 登记为归属未定，并把"每轮把这几个数连构成一起落同一份日志"排进 ⑱⑳；
                  ③ `V21-31` 的 `haversine` **口径不可复现**：旧记 11 命中 / 6 文件但命令原文没留档，本轮 `-i` = 15/7、区分大小写 = 1/1 ⇒ 三组数互不可比；
                     逐条读下来 15 处**全是口径声明或"假来源显式降级"映射**（`RouteSource.kt:46 "haversine" to EUCLIDEAN`、`Models.kt:2266 …"（直线估算，不是道路里程）"`），
                     **没有一处把 Haversine 当道路里程用** ⇒ 事实清楚但**数字不可比**，那一格**保留 ⚠ 不升 ✅**；
                  ④ `AccountRole` 这个类型名没有落地（用 `Prefs.role` 兼任，见上面那条 ⚠）—— 若下一轮要求字面同名，先解决"谁是唯一写入口"再改名。
                  ⑤ ⚠ **`--strict-id` 的 ID-MISS 数不能当跨轮闸门**（登记 `D-③-5`，它同时**作废**本轮 §4 终检 ⑤ 里"每轮连抄 ID-MISS 更便宜"那条建议）：
                     在册"6 条"是 `docs/android/ANDROID_V2.1_CHECKLIST.md` **单本**口径（本轮复跑仍 `anchors=32 miss=6`）；同一命令换成 8 本台账与 docs 是 **612 条**（A 类 98 缺的是锚点自己的文件名 / B 类 239 / C 类 275），
                     而 C 类抽查后**仍是假报**：工具按**整个表格单元**取标识符、却要求它们出现在**每一个锚点**的 ±3 窗口 ⇒ 一格并列多锚点必然红（`DECISION_LOG.md` D-047 §4 里"`--strict-id` 那一遍抓到 6 条 ID-MISS"那一格有 6 个锚点；本文件常驻自检表"分域"行三个写入点锚点互缺另两个的文件名）。**引用按内容、不写行号**：本轮边写边漂。
                     **不做的事**：不为了把这个数压下去去拆表格单元、也不引它当"锚点健康度"。处置 = #159 退回"本轮新写锚点逐条人核"；要成为判据得先按锚点分配标识符（工具改动，排 ⑰）。
                **收工终检（02:24 → 02:46 三遍：`/tmp/f3_3d_*`、`/tmp/f3_3e_*`、`/tmp/f3_3f_*` + `/tmp/f3_3g_*`）**：19 本 `check_*probes.sh` 逐本 **rc=0**，
                  **相邻三份日志逐本 `diff` 全部无差异**（19/19 相同 @02:38:00，以及 @02:46:48 `f3_3g` 对 `f3_3e`/`f3_3f` 各 0 处差异），
                  `P-*` 与 `RR-*` 共 **16 行**与 22:26 那份**逐字节相同（可比 16 / 不同 0）**、`DV-k` 仍 **116**、`runcatching`/`notify_result`/`oss_credits` 末行 `RESULT=PASS`；
                  `check_ledger_tables.py` 五本 **TOTAL FLAGS 0**；`check_doc_anchors.py`（默认口径）终态：`AI_ITERATION_STATE 189 / not_found 2`、`CONTEXT_LOCK 74 / not_found 0`（73→74 = D-047 那条新写的 `tools/check_doc_anchors.py:119-126`）、
                  `DECISION_LOG 172 / out_of_range 0 / miss 0 / not_found 4` —— ⚠ `not_found` 由 3 涨到 **4**，第 4 条是**本轮自己照抄的那串省略号路径** `.../idempotent/package-info.java:8`（同一已知形状，不是新缺陷；教训 = 台账引用文件要写全名）。
                  ⚠ 复跑之所以要跑到"写完再验"的最后一刻：`grep -l` 实测 **12 本判据簿**把这三份台账文件名写进了脚本，改台账**有可能**推动读数。**本段是台账最后一次写入**，
                  其后的终态那一份 = `/tmp/f3_3h_final_books.log`（写完本段之后跑），预期与 `/tmp/f3_3e_*`～`/tmp/f3_3g_*` 逐字节相同。
                **整体状态**：**③ 收口**（指令原话的四件齐）；F-3 全队列未收（④⑫ / ⑤ / ⑥ / ⑦⑨ / ⑧ / ⑬⑭⑯⑰ / ⑱⑳ 仍在，`D-②-1…4` 的"真待收口 23"也仍在后续项）
                  ⇒ 按指令 ⑳ 记 **`ANDROID_F3_PARTIAL`**，**不写百分比**。明细见 **D-047**。
                **NEXT（F-3 队列，③ 已收口）**：**当前项 = ④⑫** 导航入口 readiness 闸门（把 `BuildConfig.DEBUG` 从业务开关改成能力判据）+ ⑤ 线路页折线三态与文案诚实化；
                  ③ 不并进来的两条：字符串族剩余 11 处的难点是**端点归属**不是默认值（随 ⑥⑦）、`Models.kt:234 role` 恒走 `fallbackRole`（= `D-②-2` / `D-③-4`，要先定"档案即身份"的端点归属）。
                ⚠ **本轮造成的"历史行漂移"集中登记（一条都不回改 —— 那些是各自当轮的实测值，回改会把"谁推翻谁"这条链抹掉；下一轮不许当现状读）**。
                  行号是写下这一句那一刻的值，**按内容找、不要按行号找**：本文件本轮自己在 :632 插了 58 行、在 :1594 并掉了 5 行，
                  所以下面每个行号都可能随下一次编辑再漂一次（这正是"绝对行号是读数不是判据"那条纪律的又一次自证）。
                  ① **现状式**表述"那行是 `AppState.setRole(Role.USER)`"—— 源码里那一行现在读 `AppState.setActiveDomain(AppDomain.USER)`：
                    本文件 :21（V-2 那段"跨域导航点只有两处"，顺带"切角色的同时导航"这个描述也已经不成立 —— 出口现在只切视图）、
                    :2270（V-2b 记录）、:3003（§64 三处串域那节）、`ANDROID_PARITY_AUDIT_V2.md:1789`、`:2363`、
                    `DECISION_LOG.md:2267`、`:2348`、`:2542`、`:2551`、`:2554`、`docs/android/RUNCATCHING_AUDIT.md:138`、`:145`
                    （⚠ `:145` 是 ② 轮刚补的"现实测在 `:38`"，那一行**现在内容也变了**：行号仍对、字面量已不是 `setRole`）。
                  ② "当前项 = ③ / 待清空 `DriverShell.kt:38`"那几条 NEXT 指针：本文件 :757、:806、:832、:839 —— ③ 已收口，现状看上面那一块。
                  ③ ⚠ **这批漂移机器测不出来**：`check_doc_anchors.py` 只判"行号在范围内 + 该行含该字面量"，
                    而本轮改的是**同一行号的字面量**，所以那 13 处锚点里凡是只写 `DriverShell.kt:37/:38` 而不带 `setRole` 文本的，都会**安静地继续绿**
                    （与 `D-⑩⑪⑮-3` 同族）。要做的是内容级重锚，排在 #159（`CONTRACT_EVIDENCE.md` §七 那 67 条 App 锚点）之后按同一方法推广，本轮不顺手做。
CURRENT_ANDROID_STATUS:  F-3 ② 收工（2026-10-01 15:2x–18:10，三态分写、不混写、不用百分比；以下保留原文，顺序已被上一块顶替）
                **SOURCE_COMPLETE**：在册"32 处 C 站点"本轮被实测推翻，重建基数 **C=65**（census `rows=467 / B=402 / C=65 / A=0`，
                  形态 字符串 38 / 数值布尔 25（严格非可空 19）/ 数组 2；文件分布 `Repo.kt` 31 / `Models.kt` 27 / `SendScreen.kt` 7）。
                  本轮收口的是**六族**（商品 `Product` / 司机档案 `DriverProfile` / 揽件 `PickupOrder.checked` / 交接 `HandoverTask.needPhoto` /
                  候选方案 `Candidate` / `steps`·车道族），改法只有两种：**三方核到真键的改成只读那一个真键**
                  （`Models.kt:796 plate = o.multiStr("plateNo")`、`:1402 multiNumOrNull("cargoCapacityKg")`、`:1407 multiNumOrNull("cargoCapacity")`、`Repo.kt:1168 multiNumOrNull("recommendedLane")`），
                  **三面 0 生产者的改成可空 + "未给"态**（`Models.kt:1807 needPhoto: Boolean? = null`、`:1479 checked`、`:1178 recommended`、`:1179 legs`、`:313 stock`、`:310 originPrice`、`:315 sales`、`:1349/:1351/:1353/:1355`）。
                  ⚠ **没有把任何别名换成另一个猜的键名、没有删字段**；`usedKg`/`verified` 是**直接撤掉读法**而不是改名
                  （`AppDriverProfileRespVO.java:11-23` 全文 7 个键，"已装重量/认证状态"在重量口径上三方都没有生产者）。
                  生产源码动 **8 个 main 文件**：`Models.kt`、`Repo.kt`、`DemoData.kt` + 消费侧 `WorkbenchScreen.kt:566-579`、`GoodsDetailScreen.kt:79/138/219/229`、
                  `HandoverScreen.kt:260`、`CargoGauge.kt:45-90`、`DriverNavScreen.kt:480`（跨簿文案重锚）。
                  ⚠ **改动面按内容证据取（`F-3 ②` 注释标记 + 消费侧签名实测），不按 mtime**：本工程有几本负控簿是"就地改 `app/src` 真身 + 备份还原"的写法
                  （`shell_routing_test_negative_controls.sh` 末行就是 `cmp 三个源文件与备份一致`），mtime 会把那些还原动作也算成"本轮改过"。
                  新增 **1 本测试类**（`NoProducerKeyFamilyContractTest`，7 例 / 12,163 B，MockWebServer 真信封）、**2 个新节**
                  （`DEFAULT_VALUE_AUDIT.md` §二之二 + §四之一~之三、`CONTRACT_EVIDENCE.md` §九 + 九.1）。
                  **没动后端、没动微信小程序、没新增页面 / Demo 功能、HTTPS 闸门 `app/build.gradle.kts:171` 一字未动、没有 `git init`。**
                **BUILD_VERIFIED（debug 面）**：`:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --rerun-tasks` → `BUILD SUCCESSFUL in 2m 44s`、
                  `53 actionable tasks: 53 executed`（`/tmp/f3_2_final_gradle.log` @17:35）；用例 **42 类 / 410 例 / failures 0 / errors 0 / skipped 0**
                  （计数只读 `app/build/test-results/testDebugUnitTest/*.xml`，mtime 17:33:46；类数 41→42 = 新簿 +7 例，该 XML 实测 `tests=7 failures=0`）；
                  lint **78 issues：Error 0 / Warning 46 / Information 32**（`lint-results-debug.xml` mtime 17:35）；
                  APK **113,895,828 B @ 17:33**、sha256 `e3d387e79a3812519d00018eafb778184c4db7b2901b601f45b1d878b62d075d` ——
                  ⚠ **与 D-045 14:44 那份字节数相同、sha 不同**（`bbe81edc…`）⇒ 又一次实测证明"字节数不单独当变更证据"，必须连 sha256 + 树指纹成对抄；
                  `app/src` 树指纹 **files=154 sha256=f1d4a7db106609d1ae79a7017c6aa82735a3839fa6c4a44ba82475ba71e68c14**（命令同 D-045 第 2 节，153→154 = 新簿）；
                  `apk_oss_census.py` 对 17:33 产物 = **16 dex / 打包类(defined) 29,818 / 引用类型 36,637（去重 31,880）**，与 D-045 逐项相同 ⇒ **本轮对 dex 净增 0**
                  （新增用例在 test 源集，不进包）；`OS-d2 0/0/28`、`OS-e` 打包类 29,818 ⇒ 第三方类数表对的仍是当前包。
                  判据面：`tools/check_*.sh` 20 本逐本 `rc=0 / ✘=0`（`/tmp/f3_2_check_all.log`，合计 18:05:51；**台账动过之后又复跑两遍**：
                  两份 docs 改完一遍 `/tmp/f3_2g_closeout.log` 结束 18:43:25、**最后一次台账写入之后再一遍** `/tmp/f3_2g_final_books.log` 结束 18:54:28，
                  两遍都是 `20/20 本 rc=0 ✘=0` —— 之所以要重跑，是因为本轮动过 `RUNCATCHING_AUDIT.md` 而 `check_runcatching_probes.sh` 会读它那 30 行表；
                  第一遍那组数（18:0x）不代表终态）。⚠ 18:54 那一份是**判据终态凭证**，此后只动过状态板与本条日志两处台账
                  （实测：`grep -rn 'AI_ITERATION_STATE\|CONTEXT_LOCK\.md\|DECISION_LOG' tools/*.sh` 的**非注释**命中 = **0**，
                  12 本 `check_*.sh` 里的引用全在头注里 ⇒ 真读这三本的只有 `tools/check_ledger_tables.py`）⇒ 那之后的终态凭证是**结构自检**那一遍，
                  别再拿 18:54 的数说"台账是最后一版"；**`DV-k` 132→116（只降不升）**、`DV-p 0`、`DV-p2 0`；
                  真实性计数 窄 9 / 宽 46 / `GeoMath` 27 / DemoData 文件 5 / **假步进 0**；`check_doc_anchors.py`：`CONTRACT_EVIDENCE.md`
                  `anchors=304 out_of_range=0 not_found=3 miss=0`（3 条都是台账自己的省略号路径）、`DEFAULT_VALUE_AUDIT.md` `anchors=44 out_of_range=0 miss=0`；
                  `check_ledger_tables.py` 五本 **FLAGS=0**（末次实测 19:00:46，`TOTAL FLAGS 0`），七份 **CRLF=0**
                  （终检实测**行数**：`AI_ITERATION_STATE.md` 3,210 / `DECISION_LOG.md` 2,589 / `CONTEXT_LOCK.md` 1,251 /
                  `DEFAULT_VALUE_AUDIT.md` 204 / `CONTRACT_EVIDENCE.md` 320 / `RUNCATCHING_AUDIT.md` 150 / `ANDROID_V2.1_CHECKLIST.md` 126；
                  后四本的字节数 **23,331 / 56,904 / 17,354 / 35,514 B** 本轮之后没再动过，可直接引）。
                  ⚠ **三本手写台账的字节数不钉在这里**：`DECISION_LOG.md` 在 18:0x 量到 275,788 B、18:5x 278,544 B、19:00 278,747 B ——
                  每往里加一条这数就变（**自指**），状态板自己同理 ⇒ 下一轮要引用**先重量**，别拿其中两串互相做差；
                  结构完好的权威凭证是 `check_ledger_tables.py` 的 `TOTAL FLAGS 0` 与 `tools/ledger_table_report.txt`，不是字节数。
                ⚠ **本轮最要紧的一条读数纪律：负控批次不成立时，批内所有读数一起作废** —— 16:59 那批 20 本聚合红
                  （`/tmp/final_neg_batch.log`：`[聚合] rc≠0 的本： navigation(rc=1) notify_result(rc=2) oss_credits(rc=2)` + `[聚合] 自带 ✘ 的本： eta(✘=3) navigation(✘=1) notify_result(✘=1) oss_credits(✘=1)`），
                  根因是**批次期间我还在改源码与判据**（N-o2 锚点重锚、T-p 拆 tp/tp2）⇒ 那是"改到一半"的红，不是判据坏。
                  处置：还原到终态后**单本复跑**（`/tmp/rerun_negctl_batch.log` 末行 `=== 单本复跑结束 共 5 本` @17:30:57）：navigation `rc=0 ✔=27 ✘=0` /
                  eta `rc=0 ✔=59 ✘=2`（只剩 T-e、T-w2 ⇒ **D-⑰⑱-1 由 3 条缩到 2 条**）/ notify_result `破口=12 反向=2 预检=2 命中=16 失效=0` + `RESULT=PASS` /
                  oss_credits `破口=13 反向=2 预检=4 命中=19 失效=0` + `RESULT=PASS` / default_value `rc=0 ✔=31 ✘=0`。
                  **台账只引这一组数**，本轮**不许**被引用成"20 本批次全绿"（这是 D-041"三禁"的第三种违反形状：批期间改源码）。
                  缩掉的那条 T-p 也**不是"修好了"**而是**判据本来就没判别力**：旧写法把 `busId` 与 `plateNo` 合成一条计数（基线 3），
                  换成 `plateNo` 真键读法后基线涨到 4 ⇒ "删掉 busId 读法"的注入照样 ≥3 ⇒ **注入空跑**（D-040 同族第二形状：**一条判据管两个键名，两条破口会互相顶掉**）；
                  已拆成 `tp`(≥1)/`tp2`(≥3) 两条闸 + 负控簿补 `plateNo→vehiclePlate` 破口，复跑两条均 ✔。
                **release / device / git（各自单算，不许顶替上面那两态）**：release **BLOCKED** —— `app/build/outputs/apk/` 下只有 `debug`，产物从未产出；
                  DEVICE **NOT VERIFIED ON DEVICE = 0 条** —— `bash tools/verify_device.sh --no-build` 实测 `List of devices attached` **空** ⇒ `[STOP] … DEVICE_UNAVAILABLE`，
                  新证据 `docs/device-evidence/20261001-180907/`（`device-check.log` / `device-probe.log` / `summary.md`）；本轮新增/改动的四处 UI 分支
                  （"已认证/未认证"、"空余仓位（件数未给）"、"库存未给"、拍照要求位不显示）在 JVM 里**从没渲染过**。
                  GIT **ENVIRONMENT_BLOCKED** —— `fatal: not a git repository`。
                  secret 巡检（硬规则"源码不得出现真实 token/密码/secret/release keystore"）：工程内 `.jks`/`.keystore` **0** 命中，
                  `app/src` + `tools` + `docs/android` 无 `key|secret|passwd|password|token = "≥12 位字面量"` 形状命中，
                  签名属性在工程外（`app/build.gradle.kts:50` 默认 `../buspost-signing-backup/keystore.properties`）。
                **本轮新增 OPEN_DEFECT 4 条（D-②-1…4，全文与"为什么这轮不修"在 `CONTRACT_EVIDENCE.md` 九.1）**：
                  ① census 的 B 口径是"后端**全模块**有同名声明"而非"本端点 RespVO 有该键" ⇒ B 低估缺席比例（`routeName`/`shiftCode` 被 `MonitoringShiftRespVO.java:17/:20`、
                    `MonitoringMapDataRespVO.java:45` 顶成 B，而 profile 端点那 7 个键里没有）；同一条坑的第二形状：`DriverAppServiceImpl.java:167-190` 的 `fillVehicle` 只在
                    `ifPresent` 时调用 ⇒ 三个"真键"在无活跃绑定那次**真的不发**；
                  ② `Models.kt:227` 的 `multiStr("role", "userRole")` 三面 **0 生产者** ⇒ `Account.role` 恒走 `fallbackRole`（收口方式 = ③ 的 `AccountRole`/`ActiveDomain` 分离，本轮刻意不动）；
                  ③ 字符串族剩余 **11** 处未收（寄/收件人 4、线路与车辆 4、区域与空态 3；难点是**端点归属**不是默认值）⇒ 排 ⑥⑦；C 类 65 处的"真待收口"是 **23** 处（E 31 / D 11 不算客户端缺陷）；
                  ④ 在册"32 处"**不可复现**（census 从未保留那一列）⇒ 台账禁止再写"32 收掉了 N 处"这种减法结论；补列属工具改动，排 ⑰。
                **整体状态**：② 的**六族收口 = 收口**，② 的**全量清零 = 未完成**（基数已推翻为 65、真待收口 23）⇒ 按指令 ⑳ 记 **`ANDROID_F3_PARTIAL`**，不写百分比。明细见 **D-046**。
                **NEXT（F-3 队列，② 的六族已收口）**：**当前项 = ③** `AccountRole`/`ActiveDomain` 分离 + 清空 `DriverShell.kt:38`
                  （② 剩下的"真待收口 23"不并进 ③：字符串族 11 处的难点是**端点归属**不是默认值 ⇒ 随 ⑥⑦ 收；
                  `Models.kt:234 role` 恒走 `fallbackRole` = `D-②-2`，它的收口方式就是 ③，本轮刻意没动）→
                  ④⑫ 导航入口 readiness 闸门 → ⑤ 线路页折线三态 → ⑥ USER 闭环 → ⑦⑨ DRIVER 闭环 + 任务点 mapper →
                  ⑧ 任务地图无路线态 → ⑬⑭⑯⑰ 引擎契约/路线绑定/缓存指纹（含 `D-②-4` census 补列、里程别名 5、`steps` 族 5、`Repo.kt:1154` crs）→
                  ⑱⑳ 10 本测试类 + 全量 + 四份台账。
                ⚠ **"32 处"这个数在本文件下面的历史行里还有 6 处**（`:239`、`:726`、`:757`、`:768`、`:789`、`:1509`、`:1634`、`:1780`），
                  按 `D-②-4` 一律改读为：**重建基数 C=65 / 数值布尔非可空 19（形态 字符串 38 · 数组 2）/ E 31 · D 11 · 真待收口 23**；
                  历史行**不回改**（它们是那一轮的实测值，改动会把"谁推翻谁"这条链抹掉），但**下一轮不许把那些行当现状用**。
CURRENT_ANDROID_STATUS:  F-3 ⑩⑪⑮ 收工（2026-10-01 13:5x–14:55，三态分写、不混写、不用百分比；以下保留原文，顺序已被上一块顶替）
                **SOURCE_COMPLETE**：用户页那三格（车辆位置 / 上报时间 / 当前任务）的读取侧打通，根因是**客户端一行都没读**，
                  不是"后端没给"——寄货侧 `carrier*`、商城侧 `current*`、作业侧 `carrierTaskStatus/carrierArrivedTime/carrierArrived`
                  在 `Repo.kt:traceOf` 里全部零引用，页面把"我没读"渲染成"它没有"。生产源码动 **3 个文件**
                  （`Models.kt`：新增 `LocationSource` 枚举并吸收私有 `sourceOf`、`reportTimeOf` helper（`:130`，**不回落 `createTime`**）、
                  `ParcelTrace` 三格；`Repo.kt`：`:725/:726/:727/:739/:740/:741/:746` 七处读取；`TraceView.kt`：`:122-143` 三态文案），
                  新增 **1 本测试类**（`UserTrackingContractTest` 12 例 / 182 行）、**1 本工具**（`tools/check_doc_anchors.py`）、
                  **1 个新节**（`CONTRACT_EVIDENCE.md` §八 + 八.1）。删除的只有一个客户端自造别名 `positionSource`
                  （两侧 0 生产者），**没有删任何后端字段、没动后端、没动 UI 布局、没动 HTTPS 闸门**。明细见 **D-045**。
                **BUILD_VERIFIED（debug 面）**：`:app:testDebugUnitTest --rerun-tasks` → **41 类 / 403 例 / failures 0 / errors 0 / skipped 0**
                  （XML mtime 14:23:58，批次末行 `[结束] 14:24:03`、`AFTER_EXIT=0`；类数 40→41 的唯一归因是新簿 +12 例）；
                  `:app:assembleDebug :app:lintDebug --rerun-tasks` → `BUILD SUCCESSFUL in 2m 24s` / exit 0（`/tmp/f3_final_build.log:77`）、
                  lint **Error 0 / Warning 46 / Information 32 = 78**（`lint-results-debug.xml` mtime 14:45）、
                  APK **113,895,828 B @ 14:44**（对 D-044 的 113,879,444 B 是 **+16,384 B**）、
                  sha256 `bbe81edc011e2f56535c8222763a27ef9f8e95cb95153718922505e6841bcab0`、
                  ⚠ `app/src` 树指纹本轮起是 **153 个源文件 / `845a088fce6bd7248b42cb6ad72f4bcbf38a1de57137976ea2c9f4ef923c863f`**，
                  **命令原文已写进 D-045 第 2 节**——上一轮那串 `6e263735675020c1…`（146 文件）的算法没记在案，**跨轮不可比对，别再拿这两个数互相引用**。
                  判据面：`tools/check_*.sh` 20 本全 `rc=0 / ✘=0`；`DV-k=132`（只许降，本轮未升）；
                  census `rows=474 / B=400 / C=74`（D-044 是 472/398/74 ⇒ **+2 行全落 B、C 集合一条没动**，
                  新增读点 `Repo.kt:1325/728/741/747` 都有真实生产者）；真实性计数 窄 9 / 宽 46 / `GeoMath` 27 / **假步进 0**；
                  负控 20 本 18 本 `rc=0`，`notify_result`/`oss_credits` 两本先 `rc=2`（自家预检：TEST XML 不在就不许交差）、
                  接力全量跑完单独复跑 `RESULT=PASS`；⚠ `eta` 本 `rc=0` 但 `✘=2` ⇒ **D-⑰⑱-1 本轮复现，未修**。
                **release / device / git（各自单算，不许顶替上面那两态）**：
                  release **BLOCKED** —— 产物从未产出（HTTPS 闸门 `app/build.gradle.kts:171` 一字未动、未加
                  `-Pbuspost.allowInsecureRelease=true`、签名文件不在工程内）。
                  DEVICE **NOT VERIFIED ON DEVICE = 0 条** —— `bash tools/verify_device.sh --no-build` 本轮实测
                  `List of devices attached` 空 ⇒ `[STOP] DEVICE_UNAVAILABLE`，新证据 `docs/device-evidence/20261001-145515/`（三份文件）。
                  `TraceView.kt:122-143` 那两句新文案在 JVM 里从没渲染过，长什么样、会不会压坏行高都没看过。
                  ⚠ 本轮直接敲 `adb` 是 `command not found`（PATH 里没有），能出读数只因为脚本自己解析 SDK 路径 ⇒
                  别把"命令行 adb 可用"当环境事实写进下一轮。
                  GIT **ENVIRONMENT_BLOCKED** —— `fatal: not a git repository`，没有 `git init` 造假历史。
                **本轮新增 OPEN_DEFECT 4 条（D-⑩⑪⑮-1…4，全文与"为什么这轮不修"在 `CONTRACT_EVIDENCE.md` 八.1）**：
                  ① 演示面没种 `carrierTaskStatus`/`carrierArrivedTime` ⇒ 演示包"当前任务"恒念"后端未下发"（`grep -rn carrierTaskStatus app/src/main` = 4 命中 / 3 文件，`DemoData.kt` 0 处）；
                  ② 寄货 `/send/track` 无上报时间键（`PRODUCER_NOT_FOUND`）⇒ 用户页上报时间恒"未给"；
                  ③ §七 指向 `Models.kt`/`Repo.kt` 的 **67 条** App 锚点本轮只做到"行号范围合法"，**未逐条内容重锚**（确证漂移 3 条 `Models.kt:745/753/757` → `:761/772/776`）；
                  ④ 新契约面**只有用例在钉、没有 `tools/check_*.sh` 锚点簿**（`check_location_probes.sh` 的 L-a…L-h 管的是换算与上报报文，覆盖不到读取侧）。
                **标识更正（防止下一轮按旧名去找）**：私有 `sourceOf` 已并入 `LocationSource`，所以
                  `AI_ITERATION_STATE.md:2812`、`:2824`（**F-3 ⑤ 就地重锚**，原写 `:2523`/`:2535`）与工程根的 `ANDROID_PARITY_AUDIT_V2.md:841`（**不在 `docs/android/` 下**）里写的 `sourceOf` 是**历史名**，
                  现在唯一的解析点是 `LocationSource.of`（`Models.kt:557`）、唯一的拼码点是 `LocationSource.knownLabel`（`:543-552`）。
                **NEXT（F-3 队列，⑩⑪⑮ 已收）**：② 32 处 C 站点逐族（与 D-⑩⑪⑮-3 的锚点内容重锚同批）→ ③ `AccountRole`/`ActiveDomain`
                  + 清空 `DriverShell.kt:38` → ④⑫ 导航入口 readiness 闸门 → ⑤ 线路页折线三态 → ⑥ USER 闭环 →
                  ⑦⑨ DRIVER 闭环 + 任务点 mapper → ⑧ 任务地图无路线态 → ⑬⑭⑯⑰ 引擎契约/绑定/缓存 → ⑱⑳ 10 本测试类 + 全量 + 四份台账。
                ⚠ 上面这串 NEXT 已被 **F-3 ② 轮（2026-10-01 15:2x–18:10）**顶替：那一行的"② 32 处 C 站点"实测**不可复现**、基数重建为 **C=65**，
                  六族已收口 ⇒ **当前项 = ③**；以本文件最上一块与 **D-046** 为准。
CURRENT_ANDROID_STATUS:  F-2 ⑰⑱ 收工（2026-10-01 12:10–13:10，三态分写、不混写、不用百分比）
                **SOURCE_COMPLETE**：⑰⑱ 只做"不扩大 UI + 全量验证 + 回归扫描 + 落档"。生产源码本轮**只动 1 个文件 1 处**
                  （`ui/login/LoginViewModel.kt`：`onDone` 之前显式 `busy = false`，`finally` 仍管失败支）；
                  工具侧动 1 本（`tools/run_all_negative_controls_batch.sh` 补聚合退出码，`bash -n` 通过）；
                  **没有**新增页面 / Demo 功能 / 后端改动 / 测试类。明细见 `DECISION_LOG.md` **D-044**。
                **BUILD_VERIFIED（debug 面）**：`:app:testDebugUnitTest --rerun-tasks` → **40 类 / 391 例 / failures 0 / errors 0 / skipped 0**
                  （12:53，改前同一条命令是 12:49 **failures 1**，那 1 例是 `LoginViewModelTest.kt:84`，D-044 第 3 节写了为什么它不是 flake）；
                  `:app:assembleDebug :app:lintDebug --rerun-tasks` rc=0 → APK **113,879,444 B @ 13:02:18**
                  （sha256 `43a4389b83811f21d33e40fbc3af08fd…`，`app/src` 树指纹 `6e263735675020c1…`，146 个源文件）、
                  lint **0 errors / 46 warnings**；同输入重打（13:04:37）**字节数与 sha256 完全一致** ⇒ 打包确定性成立，
                  此前"+301,791 B"与本轮"−869,452 B"两次摆动都不能归因给源码（D-044 未覆盖的那半：12:05 那份产物已被覆盖、无法回比，
                  所以 #140 只能记"同输入重打=同产物"这一半已证，历史增量归因**仍未闭环**）。
                **release / device / git（各自单算，不许顶替上面那两态）**：
                  release **BLOCKED** —— 产物从未产出，HTTPS 闸门 `app/build.gradle.kts:171` 一字未动、没加
                  `-Pbuspost.allowInsecureRelease=true`、签名文件不在工程内；`:app:testReleaseUnitTest` 有产物读数
                  **40 类 / 391 例 / failures 3**（12:03:21，红的三条用例名都含"演示"，属在册演示面口径差）。
                  DEVICE **NOT VERIFIED ON DEVICE** —— `adb devices` 空 + `verify_device.sh` ⇒ `DEVICE_UNAVAILABLE`，
                  证据 `docs/device-evidence/20261001-130439/`；本轮 391 例全是 JVM 证据。
                  GIT **ENVIRONMENT_BLOCKED** —— `fatal: not a git repository`，没有 `git init` 造假历史。
                **回归扫描（⑰ 要求"不修也要显式登记"）— 本轮实测的 OPEN_DEFECT 基数**：
                  §五 C 类无生产者的数值/布尔非可空读法 **32**（`Models.kt` 18 + `Repo.kt` 11 + `SendScreen.kt` 3；
                  census 同轮读数 `rows=472 / B=398 / C=74`，`DV-k=132` rc=0）· D-⑯-1…8 **8 条全部未划掉** ·
                  `PRODUCER_NOT_FOUND` **6 处** · 司机壳角色写 `DriverShell.kt:38`（**台账里的 :37 是漂的行号**）·
                  导航入口 DEBUG-only 实测 7 处落点全在司机侧（`BusPostRoot:138`、`RoutesScreen:206,348`、
                  `WorkbenchScreen:627,684,963,1069`），`ui/user/BusScreen.kt` **0 命中** ⇒ "线路页导航入口"这句台账描述是**错位描述** ·
                  `Prefs.kt:53` · `AlertEngine.resetWatermark()` 调用点 **0** · `sessionExpired()` main 内 **1 处=自身声明**（死函数）·
                  **新增两条**：`eta_negative_controls.sh` 的 **T-e / T-w2 负控制失效**（打破口后判据仍 exit 0 ⇒ 等于没判据，未修）、
                  负控批次此前**没有聚合退出码**（本轮已补机制）。逐条与处置见 D-044 第 5、8 节。
                **NEXT（用户 F-3 指令的 ①–⑳ 顺序，不重复 F-1/F-2 ①–⑱）**：② 32 处 C 站点逐族收口 →
                  ③ `AccountRole`/`ActiveDomain` 拆分 + 清空 `DriverShell.kt:38` → ④⑫ 导航入口改 readiness 闸门（含 `NO_ROUTE`）→
                  ⑤ 线路页折线三态 → ⑥ USER 闭环 → ⑦⑨ DRIVER 闭环 + 任务点 mapper → ⑧ 任务地图无路线态 →
                  ⑩⑪⑮ 用户实时物流 + GPS 链路（`geoOf` 的 `carrierLongitude/carrierLatitude` 键缺配是根因）→
                  ⑬⑭⑯⑰ 引擎契约/路线绑定/缓存指纹 → ⑱⑳ 10 本测试类 + 全量 + 四份台账。
                ⚠ 这串 NEXT 是 ⑰⑱ 那一轮的队列快照：② 已在 2026-10-01 18:1x 收口六族（"32 处"那个基数被推翻、重建为 65），
                  ⑩⑪⑮ 也已收 ⇒ **当前项 = ③**，以本文件最上一块与 **D-046** 为准。

CURRENT_ANDROID_STATUS:  指令 v2.1 §三十四 要求的**三态分写**（2026-10-01，F-2 ⑧ 收工；以下保留原文，顺序已被上一块顶替）
                **SOURCE_COMPLETE**：F-2 第 8 步在源码/脚本层已落完 —— `app/src/main` **3** 个文件（`Api.kt` 改名 `safeApiCall` + KDoc、
                  `Repo.kt` 五个迁移点、`BusScreen.kt` 折线状态与两句文案），配套 `SafeApiCallContractTest` **12** 例、
                  判据簿 `check_safe_call_probes.sh` **15 行读数 / 34 道 SA 闸门 + 1 GUARD，rc=0**、第 **17** 本负控
                  `safe_call_negative_controls.sh` **17 条破口 ✔=20 / ✘=0 / △=0**（rc=0）。
                  ⚠ 只覆盖第 8 步：F-2 的 **⑨–⑱** 与 §五 那 **32** 处 C 站点仍在册未做；本轮新登记的"线路页首屏不拉折线却说后端未回传"
                  那条缺陷**一字未动**（动它就是扩大战线，指令禁止）。
                **BUILD_VERIFIED**：**只有 debug 面** —— `:app:assembleDebug` rc=0（`BUILD SUCCESSFUL in 1m 11s`、48 tasks：10 executed / 38 up-to-date、
                  APK **114,437,929 B** / mtime 03:12:23）、`:app:lintDebug --rerun-tasks` rc=0（28/28 executed，
                  `lint-results-debug.xml` **98,753 B / mtime 03:15:06**、**0 error** / 46 Warning + 32 Information = 78）；
                  用例面 **38 类 / 368 例 / 0 失败**（03:11:16，见 `TEST_STATUS`）。
                  ⚠ **release 不是 BUILD_VERIFIED**：`:app:assembleRelease` **未复跑**（HTTPS 闸门 `app/build.gradle.kts:171` 一字未动），
                  结论沿用 rc=1 / 分类器 `UNCLASSIFIED` / **`BLOCKED`**；**没有**加 `-Pbuspost.allowInsecureRelease=true` 绕，
                  **没有**把签名文件放进源码工程。
                **DEVICE_VERIFIED**：**NOT VERIFIED ON DEVICE** —— 本轮改的是**生产行为**（两处真实变化），设备侧一字未验，三条待验项登记在下面 `DEVICE_STATUS`：
                  ① 折线请求失败时屏幕上到底念"这次没问到（可重试）"还是"后端未回传该线路折线"——工程无 Robolectric，
                     Compose 文案在 JVM 里渲染不出来，**只有静态判据**（这是 SA-e 的存在理由，也是它的**上限**）；
                  ② `logout` 分类可见后 `Log.w` 的实际输出、以及"清本地无条件先做"与打日志的先后顺序在真机上未验；
                  ③ 天气段与两个未读数只是形状归一（对外行为一字未变），但它们所在的整条链路
                     （401 / 业务失败 / 断网 / 缺陷四类各说什么话）在设备上一条都没验过。
                ⚠ 这三态里"源码写完"永远**不包含**"设备上对"，也**不包含**"release 能打包"。
                以下保留 **F-2 ⑦** 那一遍的三态原文（历史值不回改；永久记录见 DONE「F-2 ⑦」/ D-039 ~ D-041）：
                **SOURCE_COMPLETE**：F-2 第 7 步在源码/脚本层已落完 —— 唯一规则表 `tools/verification_failure_rules.txt`（**16** 行 / **4** 类、
                  出处逐行回核指对 **9** 错 **0**）+ bash 分类器 `tools/classify_verification_failure.sh` + **test 源集**类型与
                  `VerificationFailureTest` **8** 例 + 判据簿 **18 行读数 rc=0** + 负控 **13 ✔ / 0 ✘ / 0 △ + 1 ◦**。
                  ⚠ `app/src/main` 侧 **0** 命中是**本步的设计**（第四套真相红线，判据 `VF-c`/`VF-d` 钉住），**不是"未完成"**；
                  而"SOURCE_COMPLETE"也**只覆盖第 7 步**：F-2 的 ⑧–⑱ 与 §五 那 **32** 处 C 站点仍在册未做。
                **BUILD_VERIFIED**：**只有 debug 面** —— `:app:assembleDebug` 与 `:app:lintDebug` rc=0（APK 21:13:59 / 113,863,001 B、
                  lint XML 98,753 B / **0 error** / 46 Warning + 32 Information = 78）；
                  ⚠ **release 不是 BUILD_VERIFIED**：`:app:assembleRelease` **rc=1**（HTTPS 闸门 `app/build.gradle.kts:171` 配置期拒绝），
                  分类器判 **`UNCLASSIFIED`**（策略拒绝不属四类），**没有绕闸门** ⇒ 记 `BLOCKED`。
                **DEVICE_VERIFIED**：**NOT VERIFIED ON DEVICE** —— 本轮真机 **0** 台在环（见下面 `DEVICE_STATUS` 那一节的最新一份产物），
                  分型行为本身在真机上也没验过：`adb` 不可用与"没设备"是两件事（前者 rc=127、后者 `no devices/emulators found`），
                  分类器读的是**报错原文**而不是转述。
                ⚠ 这三态里"源码写完"永远**不包含**"设备上对"，也**不包含**"release 能打包"。
                > **2026-10-01 01:29 收工终检复跑（F-2 ⑦ 的"证据补齐"那半，三态各加一行读数，历史值不回改）**
                > · **SOURCE_COMPLETE**：`app/src/main` 本轮**净改动 0 行** —— 唯一的 main 侧写操作是 01:25:00 把
                >   `nav/ShellDecision.kt:36` 的 `role != Role.DRIVER &&` 守卫**还原**（脏态 4,374 B → 4,397 B），
                >   还原后的字节与 F-2 ⑥ 收工那一份同形；脚本侧新增 `tools/negctl_lock.sh`（75 行）与
                >   `tools/after_negctl_run_full_tests.sh`（84 行，本轮又补了 `grep -a` + 批次不成立时逐本点名警告），**两个都是自写 bash、不进 APK**。
                > · **用例面**：`:app:testDebugUnitTest --rerun-tasks` **rc=0 / BUILD SUCCESSFUL in 38s / `24 actionable tasks: 24 executed` /
                >   37 份 XML mtime 全 01:25:50 / 356 tests / 0 failures / 0 errors / 0 skipped**（同一命令 01:21:27 那遍是 rc=1 / 1 failure，
                >   红在 `ShellRoutingContractTest` —— 那是脏树读数，见 D-041，不许当回归抄）。
                > · **BUILD_VERIFIED 的复跑读数（01:28:33）**：`assembleDebug + lintDebug` **rc=0 / BUILD SUCCESSFUL in 1m12s /
                >   48 actionable tasks: 3 executed, 45 up-to-date** ⇒ **`packageDebug` UP-TO-DATE = 没有重新打包**，
                >   盘上 APK 仍是 **09-30 21:13:59 / 113,863,001 B / sha1 `03ab61726030a19f431da275f0bfe3deaa976d2c`（一字未变）**；
                >   `lintDebug` 这一遍**执行了**，但 `lintReportDebug` 报 UP-TO-DATE ⇒ 三份 lint 产物 mtime **仍是 09-30 21:09:49**
                >   （98,753 B / 0 error / 46 Warning + 32 Information = 78）⇒ **那份 XML 描述的是 21:09 的树**，
                >   本轮从它里面读 `ShellDecision.kt` **0 点名**只能算"旧报告里没点名"，**不能算本轮证据**。
                >   ⚠ 反面证据链（为什么"没重打包"不等于"APK 与源码不一致"）：`compileDebugKotlin` 在 01:25:33 **真重编过**
                >   （`ShellDecisionKt.class` mtime 01:25:33），而 01:28 那遍 `dexBuilderDebug` / `mergeProjectDexDebug` / `packageDebug`
                >   **三个都 UP-TO-DATE** ⇒ gradle 按输入内容哈希判定"当前 main 源码编出的字节，已经在盘上那份 APK 里"。
                >   这条判定依赖 gradle 的快照机制（本机无 git 无法独立复核），所以它**支持**"包没落伍"，**不构成**"重打包验过"。
                > · **DEVICE_VERIFIED**：**仍然 NOT VERIFIED ON DEVICE**，本轮**不新增**待验项也不**销掉**任何一条 ——
                >   被还原的那一行正是深链角色守卫，它的设备证据从 V-2/V-2b 起就是 **0**（见下面两条在册未验项），本轮没有改变这个数。
                > · `:app:assembleRelease` **未复跑**（闸门未动、结论沿用 rc=1 / `UNCLASSIFIED` / `BLOCKED`，**没有**加 `-Pbuspost.allowInsecureRelease=true`）。
DEVICE_STATUS:  **设备在环（2026-10-08 D-065＋D-066 两轮的当前值，前八轮这一格全是 `DEVICE_UNAVAILABLE`）**：
                `bash tools/verify_device.sh` 第一次给出 **DEVICE_ONLINE** —— `device-check.log` rc=**0**、`List of devices attached` 里 **1** 台
                （`emulator-5554 / sdk_gphone64_x86_64 / SDK 35`，AVD `buspost_api35` x86_64），证据 `docs/device-evidence/20261008-155801/`（三份 log 的 mtime **16:11**）。
                ⚠ **闸门产物只有 D-065 那一遍**：本轮（D-066）**没有重跑 `verify_device.sh`**，那三支定位形状与两处旧补丁复拍的证据是
                `docs/device-evidence/20261008-172000/` 里的走查读数（每支配一条 `dumpsys location` 系统层回读），两者不可互相顶替。
                **`DEVICE_VERIFIED` = PASS 的第一次**：①②③ 三种定位形状（无 fix ／ 有 fix 且在服务区内 ／ 有 fix 但在服务范围外）＋ ④ D-065 两处旧补丁复拍都有读数，
                全部挂在 **`8899e8b0`（114,517,352 B @17:14，17:25 `adb install`）** 这一包上；**交付包 ≠ 调试包**，`240e5016` 那一包**从未上设备**。
                **D-067 轮（2026-10-08 18:2x–18:5x）设备仍在环，但形状换了**：本轮读数不是定位形状而是**控件像素**，全部挂在
                **`d7d2d801`（114,208,801 B @18:27:35）** 上，证据 `docs/device-evidence/20261008-181700/`（**34** 个文件，`cp -p` 保时间戳，含三个 `.py` 探针原件）。
                ⚠ **本轮同样没有重跑 `verify_device.sh`** —— 那一格产物（`device-check.log`）只在 D-065 那一遍存在，目录名 `20261008-181700` 里 **0** 个 `device-check`；
                ⇒ "设备在环"这一句本轮的依据是 `install-L.txt` 的 `adb install` 成功回读 ＋ 截图/UI dump 文件本身在场，**不是**闸门脚本的 `DEVICE_ONLINE` 分类，两者不许互相顶替。
                装机回读那格又演示了一次时区坑：`lastUpdateTime=2026-10-08 10:27:57` 而**同一次回读**的 `date` = `18:53:58 CST` ⇒ 整 **8 小时**，对账要连 `date` 一起抄。
                ⚠ **本轮新增的"未验"一格是 #294**：红色 `error` 描边在 `ui/driver/DriverExceptionScreen.kt`（`ui/` 内 **29** 个 `BusTextField(` 调用点里唯一传非空 `error` 的那一格）
                仍**只有源码证据**，没有成对像素读数 —— 登录页那三格能拍到是因为它们的错误支/成功支都在同一屏。
                ⚠ **仍然没验的四格（照实登记，不许被那句 PASS 覆盖）**：① `release` **0** 次上设备（HTTPS 闸门保持，仍 **BLOCKED**）；
                ② 高德**导航**那一格在 x86_64 模拟器上**永远走不到**（包内 `.so` 是 arm-only，这是 ABI 事实不是本轮缺口）；
                ③ **#245** —— `isFromMockProvider` 全 `app/src/main` **0** 读取点，App 自己看不见"这个点是不是模拟的"，本轮设备在环才让它**第一次可测**；
                ④ TalkBack 实际朗读顺序（#285 只剩这一格，本轮只有 a11y 树的形状证据、没有朗读样本）。
                **以下保留 F-2 ⑧ 那一遍的原文（历史值不回改）：**
                NOT VERIFIED ON DEVICE（**F-2 ⑧ 收工复测（2026-10-01 03:33:42 那一份产物）**：`bash tools/verify_device.sh --no-build` →
                **rc=1**、分类器按 adb 原文判 **`DEVICE_UNAVAILABLE`**，三份证据落在 `docs/device-evidence/20261001-033342/`
                （`device-check.log` 55 B / `device-probe.log` 37 B / `summary.md` 372 B，首行 `# DEVICE_STATUS: DEVICE_UNAVAILABLE`，
                正文 `adb.exe: no devices/emulators found` 原文）。⚠ **本轮与 ⑦ 的差别是实质性的**：⑦ 一字未改 `app/src/main` ⇒ 没有新的待验行为；
                本轮动了 **3** 个 main 文件、**两处真实行为变化** ⇒ 新增待验 **3** 条（见上面 `CURRENT_ANDROID_STATUS` 的 ①②③），
                其中第 ① 条（折线失败时屏幕上到底念哪句）**是判据结构上永远够不到的那一层**——工程无 Robolectric，SA-e 只能钉"分支与文案位点在位"，
                钉不了"屏幕上念的就是这句"。
                **以下保留 F-2 ⑦ 那一遍的收工复测原文（历史值不回改）：**
                **F-2 ⑦ 收工复测（2026-09-30 23:28:05 那一份产物）**：`bash tools/verify_device.sh --no-build` →
                **rc=1**、分类器按 adb 原文判 **`DEVICE_UNAVAILABLE`**，证据三份落在 `docs/device-evidence/20260930-232805/`
                （`device-check.log` 55 B / `device-probe.log` 37 B / `summary.md` 372 B，`summary.md` 首行
                `# DEVICE_STATUS: DEVICE_UNAVAILABLE`、正文含 `adb.exe: no devices/emulators found` 原文）。
                ⚠ **本轮（F-2 ⑦）新增未验项，一条**：`app/src/main` 一字未动 ⇒ 没有新的运行期行为待验，
                但**"分型只用于工程验证"这条本身也只在本机验过** —— 真机上构建失败/无设备时屏幕上到底是什么、
                以及 `verify_device.sh` 接了分类器之后的退出码语义在别的 adb 状态（`unauthorized` / `offline`）下对不对，**都没有设备证据**；
                规则表里 **7** 行出处仍是 `未实测:`（只登记不判红）。
                **以下保留 F-1（17:10:52）那一批的原文，一字未动：**
                **F-1 收工复测 17:10:52**：`bash tools/verify_device.sh --no-build` → **rc=1**、
                `List of devices attached` **空白**、`[STOP] 没有处于 device 状态的设备。`）；
                **F-1 新增未验项，五条**（本轮 52 处 catch/attempt 改写**全部只有 JVM 侧证据**）：
                ① 真实取消的**来源**只有一个在设备上才成立——手指划走页面 / `NotifyWorker` 被系统停止 / 进程被杀，
                JVM 用例只能造 `cancelAndJoin` 与 `withTimeoutOrNull` 两种形状；
                ② "页面离开后**不再往已离场的 StateFlow 上写**"这件事在本机不可观测（用例证的是"异常没被写成失败"，
                不是"屏幕上没有闪一下"）；
                ③ 缺陷原样抛出后**系统在屏幕上做什么**（ANR 对话框 / 直接崩 / 被上层 `CoroutineExceptionHandler` 接住）
                **没有验过** ⇒ 本轮把"登录页/上报"两条文案分开了，但**崩给谁看**是设备问题，不是判据问题；
                ④ `runCatching` 在 main 仍剩 **34** 处（Z2 只打数不判红），其中哪些真的跑在挂起通路上**只能靠设备/后端在环**才穷得尽；
                ⑤ §二十 的 Worker 重试语义（真失败回 `retry`）**没做**，`Result.success` 在真机上被系统重启后的行为因此也仍是旧行为。
                （以下保留 N-1 / V-2b / V-2 / V-1 那几批未验项，原文一字未动：）
                实测 `bash tools/verify_device.sh --no-build` → `[STOP] 没有处于 device 状态的设备。`
                **exit 1**；⚠ **那一轮**另量到一件小事：直接在 Git Bash 里敲 `adb` 是 **command not found（rc=127）**，
                设备列表要靠脚本自己按 SDK 路径调 adb —— "我这边 adb 报空"这句话必须指明是哪一种空）；
                地图渲染 / AMAP key / GPS 精度 / 前台服务续报 / 弱网停报 全未验；
                **N-1（第 8 步）新增未验项，四条**：① `onInitNaviSuccess/Failure` 到底回不回、缺 key/无网/离线时
                **静默不回调**能持续多久 ⇒ `Initializing` 与"永远不回来"在 JVM 层**不可分**，这层因此**故意没有**
                "初始化等待上限"那个数（编一个就是假判据）；② `NaviInfo.getPathRetainDistance/Time` 的**单位**与
                `getCurStep()` 的**语义**（javap 只给类型不给口径，审计 §77 二；本机只用它做 `hasStepProgress`，
                不进业务状态）；③ 算路 20 秒上限在真实网络下的取值、`startNavi(NaviType.GPS)` 在没先算过路时的行为；
                ④ ⚠ **包内高德原生库换了名字**：本轮起是 `libAMapSDK_NAVI_v10_0_600.so` + `libneonui_*`
                （`lib/` 解压后 77,129,060 B），`libAMapSDK_MAP_v10_0_600.so` **不再在包里** ——
                地图类（`maps/MapView;` 2 份 dex）与 `assets/amap_sdk_shaders`（5 条）都还在，
                但**地图还能不能渲染只有真机能判**，`BusMap` / `DriverMissionMap` 因此重新回到未验状态；
                V-2b 的未验项仍在册（撤档账号冷启动是否**只发一条** `/driver/profile`、角色是否真降回 USER、
                是否真被弹回用户壳、`buspost://app/driver_*` 深链的 Intent 落地）；
                V-2 的未验项仍在册（真实通知点击的深链落地只有 JVM 侧纯函数判据）；
                V-1 的未验项仍在册：连点两下真实按钮的视觉反馈、被拦下那一支文案在窄屏上是否折行 ——
                **JVM 侧证据不等于设备证据**）；
                **#51 新增未验项三条（本轮全部结论只到"结构 + JVM 用例"这一层）**：
                ① **闸门本身没在设备上验过** —— 正式包里点那三个旧入口是不是真的只落到 `DebugEntryClosed()` 那一屏、
                残留深链（`buspost://app/driver_nav` 一类）在 release 会不会绕开注册闸；
                ② **外部地图交接的两条真机分支**（装了高德 App 走 `callnative=1` / 没装走应用商店兜底 / 一个地图 App 都没有时
                那句"唤起失败"文案）—— `Uri` 拼法与 `ActivityNotFoundException` 分支都只做过代码读证；
                ③ 目的地为空时"交给高德/交给百度"的禁用态与那两行说明文案在**窄屏是否折行/截断**。
                ⚠ 这三条**不许**被本轮"27 条结构负控全 ✔"的证词顺带盖掉：结构判据钉的是"代码里怎么写的"，不是"屏上长什么样"；
                **E-1 新增未验项两条（本轮全部结论只到"读法 + JVM 用例 + 判据"这一层）**：
                ① **屏上有没有因此少一条信息** —— 六处读法只有在"后端真下发 `eta`/`minutes` 键"时才会吐出数字，
                而三份实测（后端 `"eta"` **0** / 小程序两棵树 **0** / `DemoData` **0**）都说没有这种下发 ⇒ **预期无变化**；
                但"预期无变化"不等于"屏上验过"：`TraceView.kt:64-66`、`RoutesScreen.kt:151/242/314` 这几处在真机上
                到底显示数字还是 `"—"` / `"后端未给 ETA"` / `"到站分钟未下发"`，**一个读数都没有**；
                ② **演示面那条分支照常与否**：`DemoData` 只写 `etaMinutes`（实测 144/258/334/346/532… 无 `"eta"`）⇒ 判据 T-ai3 = 0，
                但"演示构建里那一行仍显示约 N 分钟"同样只有设备/演示包能证，本轮没跑。
                ⚠ 这两条**不许**被"299 例全绿 / T-ai 家族 0-0-0"顺带盖掉：**读法判据钉的是报文怎么被解析，不是屏幕长什么样**
```

**§八 探针 E-1 复跑（2026-09-30 05:0x–05:2x，五本台账写完之后再跑一遍才算收口）**：
本轮改的是 6 处读法 + 4 条用例 + `check_eta_probes.sh` / `eta_negative_controls.sh`，之后又写了五本台账 ⇒
这一遍同时核两件事：**代码没被改台账带跑**，与**别簿的注入锚点没被本轮的文案改动作废**（D-033 第 6 条那条雷，本轮是复验过的、不是假设过的）。
- **十二套探针全部 rc=0**（`out=$(bash "$s" 2>&1); rc=$?` 逐条实抄末行，"管道取码"那条坑本轮没有再犯）：
  `分域判据通过 / 契约信封判据通过 /` **`ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径 + E-1 别名键归零）`** `/ §54 异常上报判据通过 /
  幂等判据通过 / 定位与上报判据通过 / 第 8 步导航引擎判据通过 / 可达性判据通过 / 角色同源判据通过 / 壳路由判据通过 / 状态映射判据通过`；
  外加 `check_authenticity_counts.sh rc=0` 打的数：窄 **9** / 宽 **45** / `GeoMath` **27** / `DemoData` **3** 文件 / **假步进 0**
  —— 五个数与 #51 那一遍**逐字相同**（E-1 只动"报文怎么被读"，没碰几何、没碰演示面，这是它应该不变的部分）。
  ⚠ eta 末行多出的那半截 `+ E-1 别名键归零` **不是台账里的转述**，是写进判据脚本的结论句：它在
  `check_eta_probes.sh` 的末行、`eta_negative_controls.sh` 的 `[基线]` 与 `[恢复后]` **三处同字出现**才算这条判据真接上了。
- **T-ai 家族三条复抄**（都是 0，且都是"有资格报 0"的那三条）：`T-ai 0`（别名键在读法里 6→0）· `T-ai2 0`（main 源码里的裸 `"eta"` 字面量，闭包判据）·
  `T-ai3 0`（演示面里不许长出 `"eta"` 生产者）；同轮抬的两个下限照常命中：`T-k 13`、`T-x 12`（与 XML 逐份计数一致）。
  **反证仍在册**：同一串 pattern 打在改前整树副本上返回 **6**、打当前树返回 **0**（审计 §79 六① —— "0"的出生证明是那个"6"）。
- **七本结构负控**：`eta 54` / `navigation 27` / `shell_routing 13` / `role_reconcile 13` / `exception 21` / `idempotency 11` / `reachability 21`，
  七本 **✘=0 △=0、rc 全 0**；每本开头 `[基线] …判据通过`、结尾 `[恢复后] …判据通过` 两端都在，
  "基线不绿则整份不采信"的闸门本轮一次也没被绕开。**`eta` 那本 54 条里 4 条是本轮新增**（两条打 `T-ai`：解析层候选名写回、`routeGuideOf` 回退分支写回；
  一条打 `T-ai2`：`ETA_ALIAS_PROBE = "eta"`；一条打 `T-ai3`：`DemoData` 里加 `"eta" to 18`），其余 50 条本轮**没碰** ——
  ⚠ 这半句**不是靠回忆**：`grep -n 'T-ai' tools/eta_negative_controls.sh` 实测 **5 行**（1 行小节标题 + 4 行注入命令 173/174/175/176），
  本轮往这本簿里加的注入命令就是这 4 行，多一条都没有 ⇒ "只加了 4 条"由脚本自身的锚点行数作证。
  ⚠ **计数是 `grep -c '^✔'` 数留档的全量输出得到的**：这些脚本本身**不打汇总行**，上一版我用 `printf` 只留了 400 字节，
  结果**一个数都没抄到**（本轮第二次"拿推算当实测"的同类错，改法是整本落盘再数，不是给脚本加汇总行 —— 脚本没变，变的是我取证的方式）。
- **台账结构自检真拦下来过一次**：`python tools/check_ledger_tables.py` 最终 **TOTAL FLAGS 0 / rc=0**
  （`AI_ITERATION_STATE 2117 行 / ANDROID_PARITY_AUDIT_V2 3127 / CONTEXT_LOCK 905 / DECISION_LOG 1484 / THIRD_PARTY_NOTICES 175`）。
  第一次跑报的是 **`TOTAL FLAGS 1`**、`AI_ITERATION_STATE.md:789 cells 3 vs header 4` ⇒
  我自己把"全量用例"那一行写成了**三条物理行**（正是 §79 与台账自检口径反复写的那条坏法："一行拆成三条物理行，渲染时整张表在那一行之后静默断掉，肉眼看不出来"，
  本轮现场复现了一次）。修法是把 789–791 **并回一条物理行**，**证据一字未删**、只改行的物理形状；并完行数 2119 → **2117**、`flags=0`。
  ⚠ 这条自检的价值就在这儿：**它拦的是我自己刚敲进去的那两行换行**，不是历史遗留。
  ⚠ **行数会因本节而变大**：上面那组是"检查那一刻"的读数（插入本节之前），把本节写进去之后再复跑一次是
  `TOTAL FLAGS 0 / rc=0`、`AI_ITERATION_STATE.md 行数=2151 flags=0`（其余四本不变）；随后为本节补的三条口径（39/9 分钟、五套就地簿、终检 2117→2151）
  又把它长到 **2155 行 / flags 仍 0**。**行数抄的是读数时刻，不是"最后一次大小"** —— 这行自己也会让行数再变，属于已知且可复核。
- **标题不变量按前缀量**：`grep -c "^## "` = **6**；`^## CURRENT` / `^## DONE` / `^## BLOCKED` / `^## NEXT` / `^## 状态板` / `^## 常驻自检` 各 **1**；
  新条目唯一性 `^### E-1 ` = **1**、`^### #51 ` = **1**、`^## 79\.` = **1**、`^## D-033` = **1**、`# ⑳` = **1**；
  `grep -c "§八 探针 E-1 复跑"` = **2** —— **正文块只有 1 个**（本节开头），另一次是 DONE「E-1」一节末尾的指路引用，
  与 V-2 那节"1 个正文块 + 1 次引用"同款写法，不是重复块。
  （`#51` 那一节标题本轮被我的 Edit 吃断过一次 —— old_string 含标题前缀、new_string 漏写，留下半行悬空。这条检查存在的理由就是那次。）
- **依赖与安全组未受影响（照实复量）**：`sed -n '/^dependencies/,/^}/p' app/build.gradle.kts` 逐条数 → **20** 条（与 N-1/#51 相同，E-1 没动依赖），
  **新增文件 0 个**（`find app/src tools docs -newermt "2026-09-30 00:00" -type f` 只列到被改过的源文件与判据脚本，无新路径），
  `THIRD_PARTY_NOTICES.md` §1–§4 四张许可证表一字未动 —— "没有新依赖"由**表没变**作证，不由自述作证。
  ⚠ 收尾时删掉了自己临时落在 `tools/` 的一份解码草稿（`_tmp_e1_tail.txt`）：**它只要留下，上面那句"新增文件 0 个"当场就是假台账**，
  而 `tools/` 恰是"判据家族数量"被抄进台账的目录，多一个文件还会让"12 套"这类计数下次复测时不可比。

**§八 探针 N-1 复跑（2026-09-30 01:5x–02:0x，台账写完之后再跑一遍才算收口）**：
**十二套全部 rc=0**（比上一轮多一套 `check_navigation_probes.sh`）：`check_authenticity_counts / domain /
envelope / eta / exception / idempotency / location / **navigation** / reachability / role_reconcile /
shell_routing / status`（逐条实抄末行：`分域判据通过 / 契约信封判据通过 / ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径）/
§54 异常上报判据通过 / 幂等判据通过 / 定位与上报判据通过 / **第 8 步导航引擎判据通过** / 可达性判据通过 /
角色同源判据通过 / 壳路由判据通过 / 状态映射判据通过`；`check_authenticity_counts.sh` 只打印计数不打结论，
它的"通过"是**数字本身**）。取码一律 `out=$(bash "$s" 2>&1); rc=$?`（见下面 V-2b 那条教训，本轮没有再犯）。

- **N-a…N-l（新簿 · 第 8 步导航引擎，逐条实抄）**：
  `N-a 判据层用例条数 = 19`（≥19，与 XML `NavigationContractTest` **19** 一致）·
  `N-b 引擎内定时器/假步进 = 0`（`delay(`/`Timer`/`stepIndex + 1` 在这一层零 —— 有就等于假导航）·
  `N-c 引擎内二次坐标换算 = 0`（`getCoordList()` 已是 GCJ-02，再套 `toGcj02`/`CoordinateTransform` 就是 §17 的二次偏移）·
  `N-d 引擎内假来源/直线里程 = 0` ·
  `N-e validate 出现处/SDK 层自判/唯一出口 = 1/0/1`（真实道路闸门**只此一道**）·
  `N-f 工厂两种返回/NavigationEngine 实现数 = 1/1/2`（**没有第三种实现** —— 有第三种就会有人拿"直线 + 定时器"冒充它）·
  `N-g create/算路/开始/释放/目的地/页面接入 = 1/1/1/1/1/1`（引擎只被 new 出来没人调 = 第 8 步没落地）·
  `N-h readiness 是 StateFlow/页面订阅/页面轮询 = 2/2/0`（轮询 0：用定时器的节奏猜引擎的节奏就是不实）·
  `N-i navi-3dmap/3dmap 并存/location 并存 = 1/0/0`（**顶替不是并存**，并存就是 duplicate class）·
  `N-j SDK import 总数/白名单外/引擎层之外 = 9/0/0`（高德类型只许住在 `AmapNavigationEngine.kt`）·
  `N-k 「高德没给」条数/本机宣布成功措辞 = 7/0`（≥5/0 —— 失败必须能说出是谁没给，本机不许宣布成功）·
  `N-l 过时断言「无需地图 SDK」残留 = 0`（接了 SDK 之后那句就是假话，本轮同轮改成如实陈述）。
- **负控两套**：结构 **18 条 ✔ / 0 ✘ / 0 △**（15 条打 N-*，3 条专打 L-b/L-b2 那条坐标豁免 ——
  文件级 `grep -v` 是**无界豁免**，所以豁免改成"恰好 1 处"计数）；
  用例级 **M1…M8 全 ✔**：`[基线] gradle exit=0 / failures=0/19` → 八次破口**每次 exit=1 且红在
  `NavigationContractTest`**（M5 红 **4** 例、其余各 **1** 例）→ `[恢复核对] 与改前基线逐字节相同` →
  `[恢复后复跑] exit=0 failures=0/19` → `✔ 全部破口已回滚，用例重新全绿`（`NAV_NEG_TEST_EXIT=0`）。
  ⚠ 第一版用例级负控把**七次真红**报成"用例仍然全绿"，根因是 `local code; code=$?` 取到的是 `local` 的退出码（§77 四 ②）。
- **真实性与安全组本轮读数**（`check_authenticity_counts.sh` 实抄）：窄口径 **9**（未变）/ 宽口径 **44 → 49** /
  `GeoMath` **27**（未变 ⇒ **本轮没有新增任何直线计算点**）/ `DemoData` **3** 文件（未变）/ 假步进 **6 处未修**
  （`DriverNavScreen.kt :103/:146/:180/:182/:183/:343`，§24 目标 0，禁止标成已修）。
  ⚠ **宽口径 +5 逐条可归因，且五条全是禁止性说明不是伪造几何**：`AmapNavigationEngine.kt:37`（"更不用站点连线顶替"）、
  `:261`（"替身不会偷偷变成用直线假装在导航"）、`NavigationEngine.kt:48`（"不用直线距离或经验值补"）、
  `:184`（"不做连线示意"）、`DriverSdkNavCard.kt:165`（界面文案"高德没给（不用直线距离补）"）——
  前四条是注释、第五条是**用户可见的口径声明**。**没有**为了把它压回 44 去扩白名单，也没有删注释。
  安全组复跑：`app/src/main` 凭证字面量 **0**、源码树 `*.jks`/`*.keystore` **0**、`HttpLoggingInterceptor` **0**、
  `app/src/main` 里 `127.0.0.1|localhost` **0**、`.kts` 里 **4 行**（`:18/:22` 注释 + `:167/:168` 构建期拦截）、
  release 合并清单 `usesCleartextTraffic="false"`（`app/src/release/AndroidManifest.xml:10`；debug `:9` 才 true）、
  依赖坐标 **20** 条未变、高德 key 仍在**被忽略的** `local.properties:21`（32 位十六进制，未进源码）。

**N-1-d 收台账复跑（2026-09-30，写完上面所有台账之后）**：这一段改的是 **`.md` 与 `tools/check_ledger_tables.py`，
`app/src` 一字未动** ⇒ 上一段的 295 例 / XML mtime 02:00:02 / APK 126,516,687 B 仍是当前源码的产物（UP-TO-DATE 口径见 §六），
但探针与结构自检照例复跑一遍：
- **十二套探针仍全 rc=0**（`for s in tools/check_*_probes.sh; do out=$(bash "$s" 2>&1); rc=$?; …` → 11 条 `rc=0` +
  `check_authenticity_counts.sh rc=0`，末行逐条与上面一致），计数复抄 **窄 9 / 宽 49 / `GeoMath` 27 / `DemoData` 3 / 假步进 6**
  —— **与台账写完前完全相同**，说明本轮"体积归因改成实测 + 表格修复"没有碰到任何判据范围。
- **台账结构自检（本轮新立）**：`python tools/check_ledger_tables.py` → 五本台账 **TOTAL FLAGS 0 / rc=0**（报告
  `tools/ledger_table_report.txt`）。判据本身先负控过：M1 拆行 / M2 剥掉一个转义反斜杠 / M3 两行并一行，
  **各自 rc=1**，未注入的原件副本 rc=0。据此修掉 **6 个坏表行**（`CONTEXT_LOCK.md` §九 一行被拆成三条物理行 · 至少坏了一轮；
  另 **5 行**把未转义的裸竖线写进代码段 → 整行变成 5~8 格：常驻自检表的 R/I/SR 三行 + §九 两行）。规矩记 §六 V-1 ④。
- **标题不变量按前缀量**：`grep -c "^## 常驻自检"` = **1**、`grep -c "^## "` = **6**、
  `^## CURRENT / DONE / BLOCKED / NEXT` 各 **1**。⚠ 第一遍我用 `grep -c "^## DONE$"` 量到 **0** ——
  真标题是 `## DONE（已验证，非自述）`，**判据形态写错造出的是假红，不是台账坏了**（与 §77 四"0 命中三种失效"同族）。
- **APK 体积归因由量级话换成实测**（明细审计 §77 七）：高德 `.so` 净增 **+43,681,128 B**（占 +68,575,313 B 的 63.7%），
  其余 **24,894,185 B 是残差**（上一轮包的 dex/assets 没留档，**增量分不出来**）；四本台账同轮改口径，
  原来那句"约 500 行 Kotlin 撑不起 68 MB"作废。

**§八 探针 #51 复跑（2026-09-30 03:0x–03:1x，台账写完之后再跑一遍才算收口）**：
这一段改的是 **五本 `.md` 台账**，`app/src` 与 `tools/*.sh` 在探针跑完之后**一字未动** ⇒ 下面每一条读数
与台账写完**之前**那一遍**逐字相同**，这正是这一遍复跑要证的事（"改台账改坏了判据"是这工程犯过两次的错）。
- **十二套探针全部 rc=0**（`for s in tools/check_*_probes.sh; do out=$(bash "$s" 2>&1); rc=$?; …` 逐条实抄末行）：
  `分域判据通过 / 契约信封判据通过 / ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径）/ §54 异常上报判据通过 / 幂等判据通过 /
  定位与上报判据通过 / **第 8 步导航引擎判据通过** / 可达性判据通过 / 角色同源判据通过 / 壳路由判据通过 / 状态映射判据通过`，
  外加 `check_authenticity_counts.sh rc=0` 打的数：窄 **9** / 宽 **45** / `GeoMath` **27** / `DemoData` **3** 文件 / **假步进 0**。
- **导航簿 N-a…N-p 十六条复抄**（目标值全中）：`19 / 0 / 0 / 0 / 1-0-1 / 1/1/2 / 1-1-1-1-1-1 / 2-2-0 / 1-0-0 / 9-0-0 / 7-0 / 0 /
  **0 / 0-1-1 / 2-1 / 0-1-1-0**`（后四条是本轮新增 N-m/N-n/N-o/N-p；N-f 那一位打的是"工厂两种返回 / 实现恰好 2"）。
- **用例级负控先跑、探针后跑**（一次只跑一个 gradle 调用）：`RC=0` + 基线 `exit=0 failures=0/19` + M1…M8 **八次全红且红在
  `NavigationContractTest`**（M5 红 4 例）+ `✔ 恢复核对：与改前基线逐字节相同` + `[恢复后复跑] exit=0 failures=0/19`；
  恢复之后**立刻**复跑 `check_navigation_probes.sh` / `check_location_probes.sh` → 双双 rc=0。
  ⚠ 负控的 `--tests` 过滤跑会把 `test-results/` 清成只剩 1 份 XML ⇒ 全量 295 的读数是**之后重跑** `--rerun-tasks`
  （`24 actionable tasks: 24 executed`、29 份 XML mtime **03:07:26**）拿到的，**不是**沿用负控之前那一遍。
- **台账结构自检**：`python tools/check_ledger_tables.py` → 五本台账 **TOTAL FLAGS 0 / rc=0**
  （`AI_ITERATION_STATE 2008 行 / ANDROID_PARITY_AUDIT_V2 2969 / CONTEXT_LOCK 846 / DECISION_LOG 1427 / THIRD_PARTY_NOTICES 160`）。
  本轮新写了 §九 三行、§八 ⑲ 一整块、常驻自检表四条改写 ⇒ **表格行结构是当轮改出来的，不是没动过的**，这一遍是有效检验。
- **标题不变量按前缀量**：`grep -c "^## 常驻自检"` = **1**、`"^## CURRENT"` / `"^## DONE"` / `"^## BLOCKED"` / `"^## NEXT"` /
  `"^## 状态板"` 各 **1**、`grep -c "^## "` = **6**；新条目唯一性：`^## D-032` = 1、`^## 78\.` = 1、`# ⑲` = 1。
- **依赖与安全组未受影响（照实复量）**：`sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"…:…:…"'` → **20** 条（未变），
  本轮**新增文件 0 个**（`find -newermt` 实测只碰了 5 个源文件 + 3 个判据脚本 + 5 本台账），
  `THIRD_PARTY_NOTICES.md` §1–§4 四张许可证表**一字未动** —— "没有新依赖"这件事由**表没变**作证，不由自述作证。

**§八 探针 V-2b 复跑（2026-09-30 00:0x，四本台账写完之后再跑一遍才算收口）**：
**十一套全部 exit 0**（比上一轮多一套 `check_role_reconcile_probes.sh`）：`check_authenticity_counts / domain /
envelope / eta / exception / idempotency / location / reachability / **role_reconcile** / shell_routing / status`
（逐条实抄末行：`分域判据通过 / 契约信封判据通过 / ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径）/ §54 异常上报判据通过 /
幂等判据通过 / 定位与上报判据通过 / 可达性判据通过 / **角色同源判据通过** / 壳路由判据通过 / 状态映射判据通过`；
`check_authenticity_counts.sh` 只打印计数不打结论，所以它的"通过"是**数字本身**，不是末行一句话）。
⚠ **本轮先修了"exit=0"这个读数自己**：第一遍跑的是 `bash "$s" 2>&1 | tail -6` 再打印 `[exit=$?]` ——
`$?` 取的是**管道尾命令 `tail` 的退出码**，那一版表里的 11 个"exit=0"**全部不作证**。
重跑改成 `out=$(bash "$s" 2>&1); rc=$?` 才拿到真值。"每项都要留输出"里的输出**包括退出码**，取错退出码等于没取。

- **RR-a…RR-e（新簿 · 角色同源，逐条实抄）**：
  `RR-a 用例条数（纯函数/行为）= 5/4`（与 XML 逐类读数一致：`RoleReconcileTest` **5**、`WorkbenchRoleReconcileContractTest` **4**）·
  `RR-b 档案两函数里旧判据残留 = 0/0`（`driverProfile` 与 `bindDriver` **两个函数体内** `o.length() > 0` 各 0 次 ——
  **awk 抠函数体再数，不数整文件**，否则函数上方那段"原来的 `length() > 0` 已被推翻"的 KDoc 会把判据顶成假红）·
  `RR-b3 新判据整行（!p.demo 才清绑定）= 1` ·
  `RR-c 早退行号/请求行号 = 180/196`（目标 **DEN < SFT**：降级早退必须排在 `Repo.driverShifts()` 之前）·
  `RR-c 纯函数调用点（判态/判角色）= 1/1` · `RR-c 早退分支里的 return 行号 = 193`（窗口 `DEN+1 ≤ RET ≤ DEN+22`）·
  `RR-d demoted 出口（观测/声明/回用户壳）= 1/1/2`（**2 = `DriverShell.kt` 那条既有出口 + 本轮降级出口**）·
  `RR-d 降级块内 停上报/导航 = 1/1`（`DriverLocationService.stop(context)` 与 `nav.navigate(Routes.USER_SHELL)`
  必须落在**同一个块窗口**里 —— 分开写就是"降级了但还在替已撤档的班次上报"）·
  `RR-e setRole 调用点/角色写入点/三处白名单 = 3/2/3`。
- **SR-d 2→4**（本轮往 `nav/ShellDecision.kt` 加了两个 `internal` 纯函数，下限不跟着抬就是"新函数不受册"）
  + 同轮新写 **SR-d2 / SR-d3**：钉两个函数的**整行签名**字面量。实测
  `SR-d internal 函数/Compose 依赖 = 4/0`、`SR-d2/d3 三态签名（Boolean?）字面量 = 1/1`。
  为什么光数函数个数不够：把返回类型从 `Boolean?` 退回 `Boolean`（也就是把"未知"和"否定"重新合并）
  不减函数、不改调用点，SR-d 老写法照样 4/0 —— **三态是这一轮的全部实质内容，签名不钉就等于没钉**。
- 真实性与安全组**一条都没涨**：窄口径 **9** / 宽口径 **44** / `GeoMath` **27** / `DemoData` **3** 文件 /
  假步进 **6 处未修**（`DriverNavScreen.kt :103/:146/:180/:182/:183/:343`，§24 目标 0，禁止标成已修）；
  宽口径按文件构成与上一轮**逐项相同**（`WorkbenchScreen.kt` 仍是 **5** —— 本轮往工作台加的是角色判定，
  不是第二个里程展示口；`Repo.kt` 仍是 **3**）。安全组复跑：`app/src/main` 里 `127.0.0.1|localhost` **0 命中**、
  源码树 `*.jks` **0**、凭证字面量 **0**、release manifest `usesCleartextTraffic="false"`（`app/src/release/AndroidManifest.xml:10`）。
- **台账写完之后的终检复跑（2026-09-30，`CONTEXT_LOCK.md` §六/§八 ⑰/§九 + `DECISION_LOG.md` D-030 +
  `THIRD_PARTY_NOTICES.md` §6 三本都落笔之后再跑）**：判据 10 套 `check_*_probes.sh` **逐条实测 `rc=0`**
  （取码一律 `out=$(bash "$s" 2>&1); rc=$?`），末行逐字 = `分域判据通过 / 契约信封判据通过 /
  ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径）/ §54 异常上报判据通过 / 幂等判据通过 / 定位与上报判据通过 /
  可达性判据通过 / 角色同源判据通过 / 壳路由判据通过 / 状态映射判据通过`；
  第 11 套 `check_authenticity_counts.sh` 打出的是**数**：窄 **9** / 宽 **44** / `GeoMath` **27** /
  `DemoData` **3** 文件 / 假步进 **6**，宽口径按文件构成与上表**逐项相同**（`DriverNavScreen.kt` 8 仍是第一位）。
  **设备侧实测**：`bash tools/verify_device.sh` → `rc=1`、`List of devices attached` 之后**空行**、
  `[STOP] 没有处于 device 状态的设备` ⇒ V-2b 的真机半边维持 `NOT VERIFIED ON DEVICE`，这一条不是"过了"。
  **依赖侧实测**：`sed -n '/^dependencies/,/^}/p' app/build.gradle.kts | grep -c '"[^"]*:[^"]*:[^"]*"'` → **20**，
  `app/build.gradle.kts` mtime `Sep 29 02:06`（本轮一字未动）⇒ `THIRD_PARTY_NOTICES.md` 上表不加行，
  只按 §6 的判据补一行实测数并登记 5 个新文件（2 测试 + 3 脚本，全部自写、不随 APK 分发）。

负控**两套都跑到原样恢复为止**（一次只跑一个，用例级那套会**就地改源码**，所以那段时间不碰这**五个**文件——
`data/repo/Repo.kt`、`nav/ShellDecision.kt`、`ui/driver/WorkbenchScreen.kt` ＋ **2026-10-01 F-3 ③ 新加的**
`core/AppDomain.kt`、`core/AppState.kt`）：

| 脚本 | 类型 | 本轮实测 |
| --- | --- | --- |
| `role_reconcile_negative_controls.sh` | 结构（临时目录注破口，可与构建并行） | **13 条 ✔ / 0 ✘ / 0 △**（2026-09-30 那遍），`[基线] 角色同源判据通过` + `[恢复后] 角色同源判据通过`；沙箱按 V-3 那条教训**整目录拷**（`cp -r app/src/main/java` + `app/src/test/java` + `tools`）。〔**2026-10-01 F-3 ③ 复跑：16 条 ✔ / 0 ✘ / 0 △**（新增的 3 条是 **RR-e2 司机壳里塞回角色写入** / **RR-f 提醒不再按在看哪个域分域** / **RR-a 把两层分离的 6 条用例注释掉一条**），本行原读数保留〕 |
| `role_reconcile_test_negative_controls.sh` | 用例（**就地改五个源文件再恢复**，要跑 gradle） | 2026-09-30 那遍 **RRt-1…RRt-6 全 ✔**（逐条 **1/2/3/2/1/2**）＋ `cmp 三个源文件与备份一致`。〔**2026-10-01 F-3 ③ 实测：RRt-1…RRt-10 全 ✔ / ✘ 0**，逐条 failures+errors **1/2/3/2/1/2/3/2/2/1**；`[基线] gradle exit=0` → 十次必须红（exit=1，且红在它对应的那几条用例上）→ `[恢复] gradle exit=0` + **`cmp 五个源文件与备份一致`**，留档 `/tmp/negctl_role_cases2.log`。⚠ RRt-2 那遍第一次读到 **3** 而不是 2：多出来的是 `切换司机模式先过后端档案`，隔离复测（注入→读 XML→`cp` 还原＋`md5sum` 证一致）后两次 `--rerun-tasks` 基线都是 **0**、单独注入只红 **2** 条 ⇒ 判为**一次偶发**、机制未定位，按观察项登记在册（见本文件末尾 F-3 ③ 块），**没有**靠放大 `awaitUntil` 超时把它抹掉〕 |

**RRt 破口的落点**（2026-09-30 那遍 6 条；**F-3 ③ 起 10 条**，每条都要红在**它对应的那件事**上，红了别的用例不算）：
RRt-1 把否定判据退回"`data` 非空" → 红在 `撤档后只发档案这一条请求并把角色降回用户`（1）；
RRt-2 删掉否定分支里的 `return` → 红在撤档用例 + `档案接口失败时不降级也不少发请求`（2）；
RRt-3 把"失败/演示"当成明确否定 → 红在 `失败与演示都是未知而不是否定` + `演示回落不算后端否定` + 失败轮次那条（3）；
RRt-4 让 `reconcileRole` 开始升级 → 红在 `降级是单向的：永远不会把用户升成司机` + `档案授予身份时不把用户角色升级成司机`（2）；
RRt-5 降级结论不写回 `AppState.setRole` → 红在撤档用例（1）；
RRt-6 请求失败也清绑定 → 红在 `档案接口失败时保留既有司机身份`（DomainSplit）+ 工作台失败轮次（2）。
**F-3 ③ 新加的四条（分工：7/8 打"规则本体"，9/10 打"接线"——只写纯函数而 UI 不调它，前两条永远绿）**：
RRt-7 `domainFor` 不再拦"非司机账号切司机视图" → 红在 `非司机账号切不出司机视图` + `撤档之后切不回司机视图，直到身份重新被授予` + `两层各自的真值表`（3）；
RRt-8 `domainAfterRoleChange` 撤档不再带视图离开司机域 → 红在 `撤档降级时视图离开司机域` + `两层各自的真值表`（2）；
RRt-9 `setActiveDomain` 绕开 `domainFor` 直接写值 → 红在 `非司机账号切不出司机视图` + `撤档之后切不回司机视图，直到身份重新被授予`（2）；
RRt-10 `setAccountRole` 不再把视图带出司机域 → 只红在 `撤档降级时视图离开司机域`（1）。
⚠ RRt-9 与 RRt-7 红的是**同一批用例**，这不代表其中一条是空的：7 改的是规则、9 改的是调用点，
   把 9 单独拿掉（规则留着、接线绕开）照样红 ⇒ 两条**互不顶数**，这正是把它们分成两条而不是写一条"闸门存在"的原因。
⚠ 控制台是 GBK，中文用例名在输出里是**乱码**（上面这些名字是按 XML/源码回填的，不是终端里读到的）；
**判定读的是 exit 与 failures+errors 计数**，不是那行乱码 —— 抄数时不许把乱码当"没有红"，也不许把红写成绿。
〔**2026-10-01 F-3 ③ 改读**：上面这句"读不到就不抄"已经被消掉了——打印中文用例名的**四本**案例级负控
（`role_reconcile_test` / `exception_test` / `shell_routing_test` / `idempotency_test`）的报表 python 全部加了
`PYTHONIOENCODING=utf-8`（Windows 控制台默认 GBK，python 把 utf-8 用例名编出去就成不可逆的乱码，`iconv` 与 GBK 解码都救不回来），
复跑后逐条中文落点**在终端里直接可读**（见 `/tmp/negctl_role_cases2.log`、`/tmp/negctl_case_exception_test.log`）。
⚠ `navigation_test` 那本**不点名打印用例**（它只打计数），所以不在这四本里 —— 别说成"全部负控都改好了"。
原句保留是因为它记的是当时真实的处置纪律：**乱码不是"没有红"**〕

⚠ **本轮三条"判据/负控自己出问题"，都比代码问题贵**（都写进 `CONTEXT_LOCK.md` §八 与审计 §76）：
① **"预期 0 / 预期恰好 N"的负控必须钉整行**：RR-d 的"demoted 没人观测"那条第一次注破口后脚本 **exit 0**（该红不红），
   因为 `grep -c 'LaunchedEffect(vm.demoted)'` 把**被注释掉的那一行**也数了进去 ⇒ 改成整行锚定
   `^    LaunchedEffect(vm.demoted) {$` 之后，这条与 RR-d 另两条才真的红过；
② **跨文件的"恰好 N"要写清是哪几个文件**：RR-d 的 `RD4` 期望 2，第一版只扫 `WorkbenchScreen.kt` 得 **1**
   （另一处"回用户壳"在 `DriverShell.kt`）⇒ 判据改成**两文件合数**，**不是把期望从 2 降到 1**（降期望就是把判据改松凑绿）；
③ **`grep` 会把"读"也算成"写"**：RR-e 的 `Prefs\.role =` 实测 **4** 而不是 2，多出来的是 `AlertEngine.kt:155/:44` 的
   `Prefs.role ==`（比较不是赋值）⇒ 收紧成 `Prefs\.role = [a-z]`，并把这条坑写进脚本注释。
**§八 探针 V-3 复跑（2026-09-29 23:0x–23:13，四本台账写完之后再跑一遍才算收口）**：
**十套全部 exit 0**：`check_authenticity_counts / domain / envelope / eta / exception / idempotency / location /
reachability / shell_routing / status`（逐条实抄输出：`分域判据通过 / 契约信封判据通过 /
ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径）/ **§54 异常上报判据通过** / 幂等判据通过 / 定位与上报判据通过 /
可达性判据通过 / 壳路由判据通过 / 状态映射判据通过`）。本轮新增与因本轮而抬动的数字：

- **EX-a…EX-i（新簿，9 条 21 个判定）**：`EX-a 7/6`（纯函数/契约用例条数，与 XML 逐类读数一致）·
  `EX-b 1/0`（`legAction` 出口 1 处 / 页面自发热请求 0）· `EX-c 0/1`（自造 `type` 键 0 / 三键集合契约 1）·
  `EX-d 7/0`（分类 7 条 / 缺失标签 0）· `EX-e 1/1`（上限常量与截断路径各 1）· `EX-f 1/1/1/1`（路由常量/注册/入口/意图）·
  `EX-g 5/1/0`（"未发给后端"5 处、回读不一致分支 1 处、谎报成功 0）· `EX-h 0`（本机自改状态 `copy()`）·
  `EX-i 1/1/1/1`（终态守卫四条字面量：出网前 / 按钮 / 入口 / 选段）。
- **S-ld 2→3、S-le 2→3**（新增第四张司机页必须同轮抬，否则"≥3"是拿旧账凑的）+ 新写 **S-le2**（每处写操作之后必须有回读，
  本轮实测"不回读的页 = 无"）。S-la/S-lb 仍 **0/0**（异常上报没有引入第二份状态机）。
- **I-e 4→5**（回执点现在是工作台/线路/交接两处/异常上报五个；下限跟着生效代码走，注释里写了为什么不是 4）。
- **SR-a 9→10**（`ShellRoutingContractTest` 加"司机域路由必须无参数"那条，闸门口径不能被参数绕）。
- 真实性与安全组**一条都没涨**：窄口径 **9** / 宽口径 **44** / `GeoMath` **27** / `DemoData` **3** 文件 /
  假步进 **6 处未修**（`:103/:146/:180/:182/:183/:343`，§24 目标 0，禁止标成已修）；
  安全组：源码树 `*.jks` **0**、`app/src/main` 里 `127.0.0.1|localhost` **0**、`app/build.gradle.kts` **4**
  （`:18/:22` 注释 + `:167/:168` 构建期拦截）、凭证字面量 **0**、`usesCleartextTraffic="true"` **0**、
  `HttpLoggingInterceptor` **0**。

负控制**八套全部跑完并原样恢复**（一次只跑一个，每条都等到 `[恢复后]` 才起下一个）：

| 脚本 | 类型 | 本轮实测 |
| --- | --- | --- |
| `exception_negative_controls.sh` | 结构（临时目录注破口，可与构建并行） | **21 条 ✔ / 0 ✘ / 0 △**，基线与恢复后各判通过一次 |
| `exception_test_negative_controls.sh` | 用例（**就地改源码再恢复**，要跑 gradle） | **X-1…X-8 全 ✔**：每条破口 gradle 必须红且红的是对应用例，两份文件 `cmp` 证原样恢复、`[恢复后] gradle exit=0`、末行"§54 异常上报判据通过" |
| `shell_routing_negative_controls.sh` | 结构 | **13 条 ✔** |
| `shell_routing_test_negative_controls.sh` | 用例 | **M-1…M-7 全 ✔**（红了 7 次，failures+errors 逐条 2/2/1/1/1/3/1；`[恢复] ShellDecision cmp 一致` + `[恢复] Routes cmp 一致` + `[恢复后] gradle exit=0`） |
| `idempotency_negative_controls.sh` | 结构 | **11 条 ✔**（沙箱本轮改为**整目录拷** `ui/driver/*.kt`，只拷三个页会产出假绿 —— 抬到 5 的 I-e 下限在缺文件的树上永远不满足） |
| `idempotency_test_negative_controls.sh` | 用例 | 破口后 **10 条用例红**，`[恢复] cmp 一致`、`[恢复后] gradle exit=0`、末行"幂等判据通过" |
| `eta_negative_controls.sh` | 结构 | **50 条 ✔**（本轮补了"基线不绿不采信"闸门） |
| `reach_negative_controls.sh` | 结构 | **21 条 ✔**（同上补了基线闸门） |

⚠ **2026-10-01 补记（D-040 / 判据 §八 ㉔）—— 上面这张表的历史读数不回改，但抄它的方式要改**：
表头那句"**一次只跑一个**"当时**没有任何机制执行**，所以 09-30 深夜真的两批并行了 18 分钟（16 本全批 23:30:50→00:21:23 与第二批 00:03:22 起），
重叠窗口里 `shell_routing_test` 曾被复跑读成 **✔=6 ✘=1**（✘ 指着 **M-4**）、`role_reconcile_test` 读成 **rc=2**「恢复后基线不绿」。
事后按日志时间戳查明**两条都是假读数**：M-4/M-5 的注入被同行还原掉（`AssertionError`），而簿子当时**不看注入的退出码**，
继续拿未修改的源码跑 gradle ⇒ 撞上 `FROM-CACHE` 的绿色 ⇒ 把"我没打破口"打印成了"这条判据证明不了任何东西"；
被点名的 `ShellRoutingContractTest.kt:109 / :116 / :128` 三条判据其实都是生效的。⇒ **推翻的是抄数方式，不是这张表里的代码结论**。
现在有机制了：`tools/negctl_lock.sh` 抢 `/tmp/negctl.lock`（抢不到 `[STOP]` + rc=3）、`red()` 补了"锚点没命中 exit 2 /
注入后字节没变（空跑）exit 5"、簿尾 `| tail -1` 换成"重定向后取 `$?`，判据簿 rc≠0 就 exit 6"。
修锁后的**串行 16 本复跑读数**由 `tools/run_all_negative_controls_batch.sh` 落在 `/tmp/final_neg_batch.log`（末行"共 16 本"），
⚠ **跑完之前这里不写"16 本全绿"**；全量用例由 `tools/after_negctl_run_full_tests.sh` 接在批次之后跑（顺序也是这轮才做成机制的）。
> **2026-10-01 01:20:15 批次结束，上面的闸门条件已满足，这条限制就地解除。** 但结论**不是"16 本全绿"**：
> 批内是 **15 本 rc=0 + 1 本 rc=2 自拒**（`shell_routing_test` 的"基线不绿不采信"闸门在 01:15:05 拦下的，
> 拦的是**树里带着一行没还原的注入**，不是并行踩车 —— 全过程见 D-041 / §八 ㉔ ⑦），还原后单本复跑 rc=0 / ✔=7。

**串行 16 本实测读数**（同一把锁全程持有，00:35:40 → 01:20:15，逐本 rc 行取自 `/tmp/final_neg_batch.log`）：

| 脚本 | rc | ✔ | ✘ | △ | ◦ | 用时 |
| --- | --- | --- | --- | --- | --- | --- |
| `contract_defaults_negative_controls.sh` | 0 | 22 | 0 | 0 | 1 | 181s |
| `default_value_negative_controls.sh` | 0 | 16 | 0 | 0 | 0 | 101s |
| `eta_negative_controls.sh` | 0 | 60 | 0 | 0 | 0 | 770s |
| `exception_negative_controls.sh` | 0 | 21 | 0 | 0 | 0 | 200s |
| `exception_test_negative_controls.sh` | 0 | 8 | 0 | 0 | 0 | 226s |
| `idempotency_negative_controls.sh` | 0 | 24 | 0 | 0 | 1 | 129s |
| `idempotency_test_negative_controls.sh` | 0 | 1 | 0 | 0 | 0 | 63s |
| `navigation_negative_controls.sh` | 0 | 27 | 0 | 0 | 0 | 145s |
| `navigation_test_negative_controls.sh` | 0 | 10 | 0 | 0 | 0 | 160s |
| `reach_negative_controls.sh` | 0 | 21 | 0 | 0 | 0 | 76s |
| `role_reconcile_negative_controls.sh` | 0 | 13 | 0 | 0 | 0 | 58s |
| `role_reconcile_test_negative_controls.sh` | 0 | 6 | 0 | 0 | 0 | 191s |
| `shell_routing_negative_controls.sh` | 0 | 13 | 0 | 0 | 0 | 48s |
| `shell_routing_test_negative_controls.sh` | **2** | 0 | 1 | 0 | 0 | 12s（基线不绿，自拒） |
| —— 还原后单本复跑（01:25:00） | 0 | 7 | 0 | 0 | 0 | M-1…M-7 逐条红 |
| `throwable_negative_controls.sh` | 0 | 19 | 0 | 0 | 0 | 124s |
| `verification_failure_negative_controls.sh` | 0 | 21 | 0 | 0 | 1 | 182s |

⚠ **`✔` 这一列的口径必须先说清再抄**：批处理数的是 `grep -c '^✔'` —— **以 ✔ 开头的行数**，里面**混着门禁/核对行**，
不是"破口条数"。本轮实测拆分：**282 行 ✔ = 266 行编号控制 + 16 行门禁行**，门禁行集中在四本里
（`contract_defaults` 3 GUARD / `default_value` 3 GUARD / `navigation_test` 2 句"恢复核对+全部破口已回滚" / `verification_failure` 8 = BASE 1 + CMP 5 + DIR 1 + REC 1）。
其余各本 0 门禁行。⚠ 三个容易读错的数：**`idempotency_test` 只有 1 条 ✔**（IT-1 一条闸门破口同时打掉 10 条用例，1 条 ≠ 覆盖薄）；
**`navigation_test` 的 10 = M1…M8 + 上面那 2 句门禁**；**`eta` 的 60 = 09-30 那批的 54 + F-2 ② 新立的 T-aj 家族 6**（实测 `grep -a -c '^✔ T-aj'` = 6，不是新加了一整个判据族）。
`✘` 批内合计 **1**（就是那行自拒），`△` 合计 **0**，`◦`（信息行，不判红）合计 **3**。
**合并读数以还原后那次单本复跑替换自拒行**：282 − 0 + 7 = **289 ✔ / 0 ✘ / 0 △**，`rc≠0` 的本子 **0** 个。

⚠ **本轮两条"判据自己出问题"（都比代码问题贵）**，都记进 `CONTEXT_LOCK.md` §八 ⑯ 与审计 §75：
① **"关键字出现次数"当判据等于没判**：EX-i 第一版数 `canReportException` 出现次数（页面里 10 次），
把四道守卫全删掉探针照样 exit 0。改法是钉**四条生效代码的确切字面量**各 = 1，负控一条条拆才打得红。
② **测试不许拿被自己约束的常量当期望值**：`buildExceptionRemark` 的截断用例原本写
`assertEquals(EXCEPTION_REMARK_MAX, remark.length)` —— 负控 X-4 把上限抬到 2000，这条**照样全绿**。
改成字面量 `200`（并在注释里写清 200 的来源：`varchar(500)` 减去服务层前缀与余量），X-4 才真的红。

**收工台账结构自检（本轮实跑）**：`grep -c "^## 常驻自检"` = **1**；`grep -c "^## "` = **6**（无重复标题块）；
写台账前已留带时间戳基座 `AI_ITERATION_STATE.md.bak-20260929-231001`（本工作区无 git，这是唯一回滚点）。

**§八 探针 ⑫ 复跑（2026-09-29）**：
`check_domain / envelope / eta / location / reachability / status` **6 套全部 exit 0**
（末行"分域判据通过 / 契约信封判据通过 / **ETA 判据通过（公交侧 + 货运侧）** / 定位与上报判据通过 / 可达性判据通过 / 状态映射判据通过"），
`eta_negative_controls` **41 条全 ✔**（0 ✘ / 0 △，基线与恢复后各判通过）、`reach_negative_controls` **21 条全 ✔**（基线与恢复后均通过）。
⚠ 负控制本轮抓到**一条永远不可能变红的判据**：T-t 写成 `grep 'multiNumOrNull\("etaMinutes", …\)'`，
BRE 里 `\(`/`\)` 是**分组**而不是字面圆括号，破口注入后 exit 0 —— 已改 `-F`（固定串）并复跑证明它现在真能报红。
同轮 T-v 从"≥3"改成**实测 5 处**并新增 **T-v2 闭包条**（出分钟行必须同行带口径），因为只数总数会漏掉"新增一处不带标注的展示"。
`check_eta_probes.sh` 尾部的打印残留归零了一处：`Arrangement` 的 import 消费点由 1 → **0**（§68 迁移完成），
`TransportLeg` 的 `distanceKm/durationMinutes` 仍是非空默认值 **2**（同病不同契约，交给 ⑬/下一轮，见审计 §71 残留）。

**⑫ 收工最后一遍（写完四本台账之后复跑，数字与台账一致才算收口）**：上表三行自检重测 **窄口径 9 / 宽口径 42 / `GeoMath` 27**，
`CONTEXT_LOCK.md` §八 ① 那段还停在 ⑩/⑪ 的 30 行、§55 C 还写 4 行 —— 已把 ⑫ 的实测数与逐文件构成补进去（历史行保留，按轮次追加）。
安全组同遍复跑：`app/src/main` 的 `127.0.0.1|localhost` **0**、`app/build.gradle.kts` **4**（`:18/:22` 注释 + `:167/:168` 构建期拦截）、
`*.jks|*.keystore` **0**、`HttpLoggingInterceptor` **0**；假步进 **6 处**未变（§24 仍未修）。
⚠ 抄数自纠一处：本表"假导航步进"原写 `:342`，`grep -n` 现场是 **`:343`**（`:103/:146/:180/:182/:183/:343`），
`CONTEXT_LOCK.md` §八 ④ 记的才是对的 —— **行号不一致时以当场 `grep -n` 输出为准，别在两份台账里互相抄**。

**§八 探针 ⑬ 复跑（2026-09-29，写完四本台账之后再跑一遍才算收口）**：
`check_domain / envelope / eta / location / reachability / status` **6 套全部 exit 0**，
ETA 那套末行已变成"**ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径）**"；
`eta_negative_controls` 扩到 **50 条**逐条破口，实测 **50 ✔ / 0 ✘ / 0 △**（基线与恢复后各判通过一次），
`reach_negative_controls` 仍 **21 ✔**。本轮判据数字（全表见"常驻自检"的 ETA 行）：
T-a 0 · T-b **30** · T-c **9** · T-k **12** · T-m 4 · T-n 3 · T-p 3 · T-r preview 2/2 · T-s 0 · T-t 0 · T-u 0 ·
T-v 5 · T-v2 0 · T-x 9 · T-y 3 · T-z **10** · **T-aa 1 · T-ab 1 · T-ac 0 · T-ad1 6 · T-ad2 2 · T-ae 0 · T-af 4 · T-ag 1 · T-ah 0**。
⑫ 留在尾部的两条残留：`TransportLeg` 的 `distanceKm/durationMinutes` **由"打印"升格成判红（T-ad2=2）**，
`Arrangement` 的 ui/notify import 消费点本轮复跑是 **0**（纯兼容字段，没人读）；
尾部现在打印的是**新的残留：候选名里的自造别名 `"eta"` 6 处**（`Models.kt` 四处 + `Repo.kt:623/947`，其中 `Repo.kt:939`
读的是导航引擎自己的报文，**改之前必须先核那份契约**，不许顺手"兼容"掉）。
常驻自检三行：宽口径 **42 → 44**（新增 2 行都是本轮写的**口径标注**：`Models.kt` 的 `distanceCaliberText` 与
`SendScreen.kt` 它上方的来源注释）、窄口径 **9 不变**、`GeoMath` **27 不变**（本轮没有新算一次直线）、
`DemoData` 引用仍是 **3 个文件**、安全组 `*.jks` 0 / `app/src/main` 里 `127.0.0.1|localhost` 0 / `HttpLoggingInterceptor` 0 /
`build.gradle.kts` 仍 4 行（2 注释 + 2 构建期拦截）、假步进仍 **6 处**（§24 未修，禁止标成已修）。
⚠ **本轮两条"判据自己出问题"**（都比代码问题更贵，因为它们让后面的人以为有人在守）：
① `T-k ≥9` 被我加完用例后变成**假绿**（实测 11、12 都 ≥9，探针照样 exit 0）—— 它的负控制第一次跑就报 ✘，
根因不是控制写错，是**判据太松**；已把下限抬到 ≥12，并立规矩：**`-ge` 型计数判据必须在加用例的同一轮抬下限**。
② `T-ah` 第一次响，**响的是我自己写的注释**（注释里引用了旧文案 `"在途 ${nextStation}"` 来解释为什么改）。
处置是按内容剥注释行（`grep -vE ':[0-9]+: *(\*|//)'`，与 T-s/T-v 同写法），**不是把注释删掉** ——
用删说明换绿，等于把这轮的因果链抹掉，下一轮就只剩一个"对的结果"而没人知道这里曾经错过。
⚠ **本轮台账自身出的一次事故（必须记，因为它会让"抄数"抄到不存在的内容）**：写 ⑬ 的 DONE 段时发现
`AI_ITERATION_STATE.md` 里有**两整块重复内容** —— `常驻自检 → DONE → BLOCKED → NEXT` 被完整复制了一遍（785 行），
`CURRENT` 的 `V-1/V-2/V-3 + 已知冲突 + ⑧ + ⑦` 又在文件尾部重复了一遍（47 行）。两份都是逐字节相同，
所以任何"按行号引用本文件"的做法会指到错的那一份。已按内容删除后一份，删前用 `difflib` 证过两块**逐行相同**（不是我以为相同），
删后文件 966 行、每个 `##` 标题只出现一次。**从本轮起收工多加一步**：
`grep -c "^## 常驻自检" AI_ITERATION_STATE.md` 必须是 **1**，`wc -l` 与本轮记录的行数对得上才算台账干净。
根因是**同时跑了两个会临时改源码的负控脚本**（一份前台超时被转后台、一份又后台起），
在它们重叠的窗口里做的源码读取与写入都不可信 —— 负控脚本自己会 `sed` 破口再恢复，两个一起跑就是互相覆盖。
⇒ **规矩：负控脚本一次只跑一个，等它打印 `[恢复后] … 判据通过` 之后再碰源码。**

**§八 探针 V-2 复跑（2026-09-29 21:27–21:35，四本台账写完之后再跑一遍才算收口）**：
`check_domain / envelope / eta / idempotency / location / reachability / status / shell_routing` **8 套全部 exit 0**，
外加 `check_authenticity_counts.sh` exit 0（9 个脚本；末行依次为"分域判据通过 / 契约信封判据通过 /
ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径）/ 幂等判据通过 / 定位与上报判据通过 / 可达性判据通过 /
状态映射判据通过 / **壳路由判据通过**"）。壳路由那套是本轮新建，13 个判定的当轮数字：
**SR-a 9 · SR-b 1/0 · SR-c 1/0 · SR-d 2/0 · SR-e 0 · SR-f 0 · SR-f2 1 · SR-g 1/0 · SR-h 1**。
全量用例 `:app:testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL / GRADLE_EXIT=0 / 24 actionable tasks: 24 executed**，
24 份 XML 的 mtime **全部 21:29:32**（是新跑的那一遍，不是上一轮的缓存），重读合计
**24 类 / 253 tests / 0 failures / 0 errors / 0 skipped**，其中 `ShellRoutingContractTest` = **9**（与 SR-a 一致）。
构建 `:app:assembleDebug :app:lintDebug` → BUILD SUCCESSFUL、GRADLE_EXIT=0，但这一遍是
**48 actionable tasks: 1 executed / 47 up-to-date** ⇒ **没有重新打包**：盘上 APK 仍是本轮 20:54 那一次的产物
（57,941,374 B），lint 三份产物仍是 20:56 那一遍的。照实记 UP-TO-DATE，**不写成"重新构建通过"**。
dex 符号复核（Python 逐份扫 14 个 dex 的字节）：`ShellDecisionKt` **3**、`startDestinationFor` **2**、
`deepLinkTarget` **2**、旧函数名 `deepLinkAllowed` **0** ⇒ 分域闸在包里只有一份答案。
⚠ 这一步**第一次跑出来全是 0**，根因是 `strings: command not found`（Git Bash 里没有这个工具）——
**命令不存在时的空输出与"真的 0 命中"在 stdout 上不可区分**，这正是 §六 V-2 ② 那条"先验文件存在"的
**第四个方向：先验命令存在**。改 Python 重测才拿到上面那串数字；失败的这份输出**不采信、不进判据**。

结构负控收工再复跑一遍 `bash tools/shell_routing_negative_controls.sh` → **exit 0**：`[基线] 壳路由判据通过` +
**12 条逐条破口全 ✔ / 0 ✘ / 0 △**（SR-a 注释掉一条用例、SR-b 第二份 `when`、SR-c 深链不过闸、SR-c2 旧闸门还在、
SR-d 决策文件被 Compose 污染 / 函数被删、SR-e 用户页引用司机路由、SR-f 司机页引用用户业务页、
SR-f2 出口不同步切角色、SR-g 未登录消费深链 / 通知直投壳、SR-h `DRIVER_ONLY` 改字面量）+ `[恢复后] 壳路由判据通过`
（**先验基线、再验恢复**这两步是本轮新写进脚本的，缺任何一步这份负控都不采信）。

**收工台账结构自检（本轮实跑，输出照抄）**：`grep -c "^## 常驻自检"` = **1**；`## ` 标题无重复；
`grep -c "§八 探针 V-2 复跑"` = **1 个正文块**（全文另有一次命中是 TEST_STATUS 那行的指路引用，不是重复块）。
**本轮起不再往台账里抄行数/字节数** —— 写这句话本身会让文件变长，而 ⑬ ② 早就定过"行数不是不变量、唯一性才是"。
自检**抓到两处本轮自己写的缺陷**：常驻自检表的新两行
（壳路由行、全量用例行）行尾**都漏了收尾的 `|`** —— 肉眼扫过去完全看不出来，已补齐（各 +1 字节）。
⚠ **另一类缺陷本轮如实登记但没有"修"**：表格里凡是把 ERE pattern 原样写进代码块的地方
（`(^|[0-9]+:)[[:space:]]*(\*|//)`、`(A|B)` 这种带裸 `|` 的），渲染时**会把一行切成多列**（实测：全量用例行 4 段、
壳路由行 8 段、⑩ 可达性行 6 段 —— 后者是**历史遗留，非本轮引入**）。
**处置是登记而不是改写**：这些 pattern 是判据本体，为了让表格好看而加转义，会让下一轮的人抄到一条与脚本里不同的命令
（GFM 里 `\|` 在代码块内是否还原成 `|` 取决于渲染器，不可依赖）。
⇒ 规矩：**要抄的 pattern 一律去 `tools/*.sh` 里抄，表格只写判据 ID 与数字**（本轮起新行照此写）。

**§八 探针 V-1 复跑（2026-09-29，写完四本台账之后再跑一遍才算收口）**：
`check_domain / envelope / eta / idempotency / location / reachability / status` **7 套全部 exit 0**
（末行"分域判据通过 / 契约信封判据通过 / ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径）/ **幂等判据通过** /
定位与上报判据通过 / 可达性判据通过 / 状态映射判据通过"）。幂等那套是本轮新建，十条数字见"常驻自检"的 V-1 行
（I-a 10 · I-b 10 · I-c 0 · I-d 1/0 · I-e 4 · I-f 0 · I-g 0 · I-h 0 · I-i 1 · I-j 11）；
`idempotency_negative_controls.sh` **12 条实测 12 ✔ / 0 ✘ / 0 △**（基线与恢复后各判通过一次；
⚠ **这条"12"是 V-1 当时的登记值，本轮不回改** —— F-2 ⑥ 复跑实测该簿 `I-*` 破口 **11 条**，
口径注解与全值（**24 ✔ + 1 ◦**）见 DONE「F-2 ⑥」/ D-038，以及 V-1 那节"本轮新增文件"清单末尾的同题注解）；
`idempotency_test_negative_controls.sh` 把保留窗与 `tryLock` 两行就地删掉后 gradle 报 **10 条用例红**，
`cmp` 证原样恢复、复跑全绿、探针仍 exit 0。
真实性口径本轮**一条都没涨**（改用新脚本 `bash tools/check_authenticity_counts.sh` 一把跑，不再手抄 pattern）：
窄 **9** / 宽 **44**（逐文件构成加总 =44：`DriverNavScreen 8 / ParcelScreen 7 / WorkbenchScreen 5 / MissionMarkers 5 /
Repo 3 / MapCanvas 3 / HomeScreen 3 / SendScreen 2 / BusScreen 2 / AlertEngine 2 / TraceView 1 / Models 1 /
DriverMissionMap 1 / BusMap 1`）/ `GeoMath` **27** / `DemoData` **3** 文件 / 假步进 **6 处**（§24 仍未修，禁止标成已修）。
安全组六条逐条复跑：**0 · 4 · 0 · 0 · 0 · 0**（`.kts` 那 4 行仍是 `:18/:22` 注释 + `:167/:168` 构建期拦截；
第 6 条裸数 `AMAP_KEY` 时那句 `grep -v` 是判据的一部分，省掉就变 2 命中、看着像红线破了）。
⚠ **本轮把 ⑬ 立的"台账干净"自检又跑了一遍**：`grep -c "^## 常驻自检"` = **1**、`grep -c "^## "` = **6**。
写台账期间发生过一次**自己制造的合并行**：一次编辑吃掉行尾换行，把"签名文件 / 日志拦截器"那行并进了"本机地址"行 ——
靠结构自检（表内每行必须以 `| ` 开头、且行内不得出现 ` || `）抓到并拆回。这是 ⑫/⑬ 反复记的那条
"**语义判据与 `assembleDebug` 不可互相替代 / 编辑吃掉换行时探针毫无反应**"第一次发生在**台账文件本身**上 ⇒
台账也是产物，改完要用**结构**判据复核，不能只用眼睛看。


**⚠ 本轮推翻我自己写进台账的一条方法论（⑪ 的"时间戳纪律"）**：
⑪ 记的是"**每个构建产物各自对时间戳**，产物必须晚于最后一次源码编辑"。本轮照这条查，立刻得出矛盾：
`assembleDebug` 报 UP-TO-DATE，但 APK 是 13:32:48、最后一次 main 源码编辑是 13:56:54（`DemoData` 注释）——按时间戳判语就是"APK 是旧的"。
真因：**构建缓存会保留产物原时间戳**，mtime 只能说明"这个任务没重跑"，不能说明"产物不对应当前输入"。
我又用 APK 字节 SHA 变化来判"陈旧"，同样不成立：**APK 的 zip 条目内嵌打包时间，同样的源码重新打包字节哈希本来就会变**
（实测 `--rerun-tasks` 前后 `6e7ee85c… → e70ac37d…`，而源码一字未改）。
⇒ 有效的判据只有两条：**看 gradle 报的是 `executed` 还是 `up-to-date`**，需要无歧义时用 `--rerun-tasks` 逼它真跑一遍。
本轮因此把 test / lint / assembleDebug 全部 `--rerun-tasks` 重跑（共 71 个任务 executed），三个产物时间戳（14:56–14:58）
自然全部晚于源码，但**结论成立靠的是"任务被真实执行"，不是靠读时钟**。⑪ 那段话按此理解引用。

**⑬ 收工最后一遍（两套负控脚本全部跑完并原样恢复之后复跑，2026-09-29 19:00 之后）**：
`check_domain / envelope / eta / location / reachability / status` **6 套逐条重跑全部 exit 0**（ETA 末行"ETA 判据通过（公交侧 + 货运侧 +
⑬ 里程口径）"、分域末行"分域判据通过"、可达性末行"可达性判据通过"；`check_eta_probes.sh` 尾部打印的 `Arrangement.etaMinutes`
消费点仍是 **0**，即那 6 处 `"eta"` 别名残留本轮没扩、也没人顺手"兼容"掉）。
负控：**ETA 侧 50 ✔ / 0 ✘ / 0 △**（`/tmp/eta_neg_13f.log`，首行 `[基线] …判据通过`、末行 `[恢复后] …判据通过`、`EXIT=0`），
可达性侧 **21 ✔ / 0 ✘**（`/tmp/p_reachneg.log`，同样基线与恢复后各判通过一次）—— 这一遍是**串着跑**的（前一个打印 `[恢复后]` 才起下一个），
就是为遵守本轮立的那条"一次只跑一个"。
常驻自检逐条复测：**窄口径 9**（逐行核对构成未变：`MissionMarkers:97/101` + `WorkbenchScreen:209/220` 是带标注的远近出口与其取数/调用，
`AlertEngine:90`、`HomeScreen:233`、`ParcelScreen:340/441/442` 是 ⑫ 写的口径说明注释）、**宽口径 44**
（按文件：`DriverNavScreen 8 / ParcelScreen 7 / WorkbenchScreen 5 / MissionMarkers 5 / HomeScreen 3 / MapCanvas 3 / Repo 3 /
SendScreen 2 / BusScreen 2 / AlertEngine 2 / DriverMissionMap 1 / TraceView 1 / BusMap 1 / Models 1`，加总正好 44）、
`GeoMath` **27**、`DemoData` 引用仍是 **3 个文件**（`Api.kt / DemoData.kt / Bits.kt`，UI 与 Repo 无直接引用）、
假步进 **6 处**（`:103/:146/:180/:182/:183/:343`，**行号与条数一字未变 ⇒ §24 仍未修**）。
安全组：`app/src/main` 里 `127.0.0.1|localhost` **0**、`*.jks|*.keystore` **0**、`HttpLoggingInterceptor` **0**、
凭证字面量 **0**、`app/build.gradle.kts` **4 行**（`:18/:22` 注释 + `:167/:168` 构建期拦截）。
⚠ **本遍新抄到的一条"台账写法不精确"**（不是新违规，是抄数的人会抄出假象）：本表安全组那行原来只写语义
（"`AMAP_KEY` 字面量 … 全部 0 命中"）没写命令，照字面按**裸 token** 跑 `grep -rn "AMAP_KEY" app/src/main` 实测是 **2 命中** ——
`AndroidManifest.xml:55` 的注释与 `DriverMissionMap.kt:88` 的 `BuildConfig.AMAP_KEY.isNotBlank()`，两处都是**引用**不是密钥值。
**判据本体没坏**：`ANDROID_PARITY_AUDIT_V2.md` §55 E 那条命令自带 `grep -v "@string/amap_key\|BuildConfig.AMAP_KEY"`，
本轮照原样复跑就是 **0**；简写的这一行已改为指回 §55 E 的六条命令（key 真值只在 `local.properties` 的 `amap.key`，
经 `app/build.gradle.kts:74` 注入 `buildConfigField`/`resValue`）。
⇒ 与本轮已有的那条"行号会漂、按内容找"同类的另一条：**0 命中必须连 pattern 一起抄**，否则下一个人按裸 token 数出 2，
要么以为红线破了，要么把判据"修"成他数得出的那个数。改的是台账的表述，不是判据、不是数字。
构建与用例：`testDebugUnitTest + assembleDebug + lintDebug` 合跑 **`53 actionable tasks: 1 executed, 52 up-to-date` / `BUILD SUCCESSFUL`**
—— 全 UP-TO-DATE 就是"负控脚本恢复后的源码与已验证产物逐字节一致"的证明（gradle 按输入内容哈希判定），
测试 XML 逐份加总 **`suites=21 tests=233 skipped=0 failures=0 errors=0`**（21 份 mtime 全部 17:58:22），APK **57,941,278 B**。
台账自检：`grep -c "^## 常驻自检"` = **1**，`^## CURRENT / DONE / BLOCKED / NEXT` 各 **1**。
行数只作快照：去重后 966 → 写本段时 1007 → 补完上面两条口径说明后再量 **1041**（1039/1040 都是中途读数）——
**行数不是不变量，"每个标题恰好出现一次"才是**；本文件里任何行数都只代表"当时实测"，引用前重跑 `wc -l`。

**编号消歧（本文件早就存在的两套圈号，读错会误事）**：`NEXT` 段的 ①–⑯ 是**用户指定的路线图位次**（不得打乱），
而标题里的 ⑤⑥⑦⑨⑩⑪⑫ 是**迭代轮次计数**。两套同号不同义 —— 例如 `NEXT` 第 8 行写"⑧ AMap Navigation → CURRENT"，
而真实轮次 ⑧ 早已过去、当前轮次要做的下一件事是 ⑬。以后引用路线图位次一律写"路线图 第 N 步"，引用轮次写"第 ⑫ 轮"。

---

## CURRENT（2026-10-14 真机第一轮收工 · 台账 D-114）

**这一轮第一次把"运行时那一格"接上真机（小米13 / 1080x2400 / 440dpi ⇒ 48dp=132px），
判据面与业务面各收一批。** 屏幕与热区下限改为从设备现读，不再写死 126px。

- **首页附近公交**：半径不再恒 15km（实测 15km=188 条线路 / 5km=104 条，真机读到 81 条 / 260 站）；
  形态按用户口径改成**一条线路一张卡、首页三张**，其余写在"附近还有 78 条线路，点「更多」看全部"；
  线路签此前点不动（`/bus/nearby` 的 `lines[]` 根本没有 id）⇒ 首页同时取 `/bus/lines` 按名对 id。
- **步行到站**：挑站从"时间轴第一个 800m 内 / 否则起点站"改成按真实远近；距离与时间改用
  高德 `direction/walking` 的**真实路线**（直线 440m ⇒ 步行 1237m/990s），`WalkRoute.routed` 是三态
  不是布尔（R-a 判据当场把我第一版的布尔打红）。真机验证高德落在"步行"、终点"文峰公社(公交站)"。
- **线路详情页补收藏入口**（星标只在实时公交页内嵌详情里有）；实测"已收藏"+ 实时公交页"本机 1 条"。
- **商品图两条病根**：App 把 emoji 当 URL 喂加载器（只认 http(s)/绝对路径，相对按站点根补全）；
  云上种子是 `http://1.15.29.107/...` 明文 IP，release 禁明文 ⇒ 同一张图走 `https://api.qwer2333.com/images/...`
  实测 200/image/jpeg/209156B。**种子换 https 是后端数据面的活，已进欠账**（本机库已就地补齐以便真机验证）。
- **验证码**：云上现在就是 **888888**（新号实测 send → sms-login 拿到 token / userId=42）；队友输 8888 得到
  "验证码不存在"是旧值。自动更新按用户指示恢复全构建提示，只保留"同一 tag 只拦一次"（实测二次冷启动 0 弹窗）。
- **遍历器四课**（全是"每步都成功、结果全假"）：`adb shell` 吞 stdin ⇒ 列表挂 fd 3；去重必须提前到建目录前；
  告警计数会把"无越界/无重叠/热区达标"读成 1 ⇒ 只数 `[LAYOUT]` 并要求 `TOTAL_ISSUES=` 存在；
  探针点 (3,3) 会拉下通知栏（叠加本机 60 秒息屏）⇒ 改点底部手势条 + `statusbar collapse` + 定位权限**回读**。
  新增 `--routes`：深链扫路由，27 条全到，6 条被闸门挡下单记 `no-move.txt`。
- **判据/用例**：35 本 rc=0；`TH-e` 豁免面 3→2（startActivity 的 catch 收窄）连同归因改目标并复跑配对负控；
  七本活台账锚点按 diff 净增减机械平移（备份 + `os.replace` + 核 CR/行数），ID-MISS 264（上限 273，降 9），
  在册表重生成 239；用例 706 条 failures=0（先删旧 XML，且必须走 `tools/run-unit-tests.sh` 的 ASCII 盘符路径）。
- **两条环境级**：本地增量构建会"up-to-date"地**发旧包**（APK mtime 16:08、dex 里读不到新符号，而 rc=0）
  ⇒ 凭证只能是在 APK 的 dex 里读到新符号；后端 nearby 把同一 station id 按线路拆多行 + 本机库有
  `?????????` 双编码历史行 ⇒ 合并与洗数据是后端的活。
- **下一轮首项**：42 本负控全量批补跑（本轮跑到第 4 本被我停下以免把注入态提交）；
  `--routes` 之外还要把带 `{参数}` 的 7 条详情路由用真实 id 扫一遍。

## CURRENT

**下一轮：队列首项 = 其余 `Minutes` 家族的第二候选名 5 处**（`Models.kt` 479 `plannedMinutes` / 729 `waitDepartureMinutes` /
1110 `totalMinutes` / 1328 `plannedDurationMinutes` / 1802 `walkMinutes`）—— ⚠ **必须逐端点重核契约，不许把 E-1 那句
"别名键没有生产者"抄过来当结论**（`waitMinutes` 这类完全可能是小程序真在用的键；手里没有那份实测就是猜，
而"猜出来的兼容"正是 E-1 刚删掉的那个东西）。
AMap Navigation 的**运行时那一半**仍要等一台真机，本机不许编数 —— **#51 新加的 DEBUG 闸、E-1 改过的六处读法、F-1 改的 17 个生产文件
（含整圈仪式摘掉的 `WorkbenchScreen.kt`）都在同一未验状态**（2026-09-30 17:10:52 复测 `verify_device.sh --no-build` → rc=1、`List of devices attached` 为空）

本轮（**F-1 异常可信化：§六 缺陷类 Throwable 不得喂演示数据 + §七 取消不许被收成失败**）**已完成并搬进下方 DONE 的「F-1」一节**；
实测记录见该节、审计 §80、`DECISION_LOG.md` D-034、判据 `CONTEXT_LOCK.md` §八 ㉑。
**开工前先读这四条**（本轮的坑同样不在代码量，在"判据与负控会不会自己骗自己"）：
① **"配对"既不数关键字出现次数，也不靠手写文件清单**：TH-e 第一版扫一份 14 个文件的清单，结果
`DriverSdkNavCard.kt:291`（唤起外部地图，try 里**一个挂起点都没有**）被判成违例，而清单外的文件永远是盲区。
改成**扫描面由内容决定**：每个 `catch (e: Exception)` 先看它 try 的**首段**有没有挂起点（`Repo.` / `Api.` / `delay(` /
`.await(` / `.first(` / `fetch(` / `block(` / `animateScrollToPage` …），有才要求同链先有取消出口；
并且**"扫到多少挂起形状 48""免检几处 1"两个数跟违例数一起打** —— 只打违例数的判据，在"规则被写宽"与"破口被删干净"
两种情况下输出一模一样（§八 ⑳ 那条"这个键谁在写"在这里的同形：**这个 catch 真的接住过东西吗**）；
② **三个"目标 0"各有出生证明**（18 条负控逐条打，不是"脚本看起来会红"）：TH-c 的 Api.kt=0 由"把 IOException 出口改回
`catch(Throwable)`"打出 exit 1；TH-e 的违例 0 由"摘掉一处 `throw e`、**保留子句字面量**"打出 —— 那一次 **TH-d 照样 38/38 绿**，
正好证"数子句"与"逐点配对"不是同一条判据；TH-l 的兜底 0 由"`NetworkException` 出口换成 `RuntimeException`"打出；
③ **注释不许顶替生效代码，负控脚本自己也要防**：`swap` 现在强制 `assert old not in new` —— 把 `if (!demoAllowed) throw e`
换成"同样字符串的注释"，TH-b 会数到注释里那一句，破口看着还在 ⇒ 假绿。同一条坑 V-3 / ⑳ 都犯过，本轮写成脚本里的机器闸；
④ **台账自述可能判据抓不到**：本轮最实的一条。`WorkbenchScreen.kt:549-555` 的收法是**整圈 `runCatching` 仪式摘掉**，
既不留 `catch (e: CancellationException)` 子句、也不留 `attempt {` ⇒ 在 TH-d 的"数子句"与 TH-e 的"逐点配对"里**全都隐形**；
而 DONE 草稿把它写成"先穿透取消"，属于自述不实（"本来就没有 catch"这种改法只能靠**读代码**发现，不能靠数）。
同批还有两处计数是**推算**出来的（负控 `✔=18` 实测 **19 行** = 18 破口 + 1 自检行；`idempotency_test` 的 `✔=0` 会被读成"没跑负控"，
实际是那一本的破口Marks 用的是别的符号）⇒ 全部改成留档产物实测值，见 D-034「后果与遗留」。

E-1（解析层自造 ETA 别名键 6 处 → 0）的记录在 DONE 的「E-1」一节与审计 §79、`DECISION_LOG.md` D-033、判据 §八 ⑳；
**它那四条纪律 F-1 一条没破，第④条本轮又被它自己抓了一次**（负控次数与 marker 语义两处推算 ⇒ 实测）：
① **"读起来像兼容"不等于有来源** —— 判据从此要多问一句"这个键，谁在写"，§38 的 `null → 0` 红线**结构上抓不到**这类破口；
② **"目标为 0"的判据必须先证明自己命中过非零**（同一条 grep 打在改动前的整树副本上返回 **6** 才算出生证明，F-1 的 TH-c/TH-e/TH-l 三条照此办）；
③ **改源码文本 ⇒ 同轮核所有簿的注入锚点或直接复跑所有簿**（F-1 全复跑实测：**13 套探针 rc=0** + **13 本负控 rc=0**，
其中就地用例级负控会把 `test-results/` 洗成 1 份，所以全量数必须在负控**之后**重聚合）；
④ **台账次数/时长只准抄留档产物**（那轮是"40 次 / 7 分钟"，本轮是"✔=18"，同一种病）。

#51（旧 `DriverNavScreen` 转 DEBUG ONLY + §24 假步进 6 → 0）的记录在 DONE 的「#51」一节与审计 §78、`DECISION_LOG.md` D-032、判据 §八 ⑲；
**它那三条纪律 F-1 同样一条没破**（第③条在 E-1 那一轮被扩宽成"核所有簿的锚点"）：
① **降级任何一条入口之前，先量它身上还挂着什么能力**：`grep -rn "DriverSdkNavCard("` 实测全工程唯一的引擎调用点就在要被关掉的旧屏里
⇒ 直接上闸会**静默**拿掉 release 的导航能力（编译过、十二套探针绿、295 例全绿，少的是一整个功能）。
**收法**：把"生产调用点"编成会红的数（N-g 第六位重锚成"`WorkbenchScreen.kt` 里恰好 1"）——**"能力还在不在"也要有判据，不能只靠人记得**；
② **"归零"必须伴随口径变严**：假步进 6 → 0 的同一轮，把那条判据从"只看旧屏一个文件"抬成**整个 `ui/driver` 目录**含注释裸计数，
否则"挪个文件"就等于修好了。⚠ 第一次复测量到的是 **2**，命中的是本轮自己新写的注释里的字面 token ⇒ **改措辞，不改口径**；
③ **不能凭 mtime 主张"这块没改，所以那条负控不用跑"**：`NavigationEngine.kt` 的 mtime 晚于上一次用例级负控实跑，
而 mtime 证不了"上次跑时的树就是现在这样" ⇒ **照跑**（八次破口全红 + `cmp` 逐字节恢复）。
这条已升为 §八 ⑲ ③ 的永久规矩、E-1 那一轮又由 ③ 那条升级为"核所有簿的锚点"。附带量到的一件事：`--tests` 过滤跑完会把 `test-results/` 清成只剩 1 份 XML，
**先前那次 295 的读数就此失去产物支撑** ⇒ 重跑全量，而不是沿用旧数、也不是拿 `from-cache` 当"重跑过"
（E-1 照做重聚合出 299；**F-1 同样照做**：负控跑完后再 `--rerun-tasks` 重聚合 → **31 类 / 311 例**，31 份 XML 的 mtime 全为 **17:02:02**，
逐份读出再自相加，不抄控制台摘要）。

N-1（路线图第 8 步 · 可插拔导航引擎）的记录在 DONE 的「N-1」一节与审计 §77、`DECISION_LOG.md` D-031、判据 §八 ⑱；
**它那四条纪律仍然有效**（#51 / E-1 / F-1 三轮一条都没违反，且第①条正是 #51"先 grep 再上闸"的方法来源）：
① **SDK 有什么能力，只由实际解析到的那份 jar 上的 `javap` 定，不由印象或文档定**：`AMapNavi` **没有公开 `isInited()`**
（`grep -i init javap_AMapNavi.txt` 实测 **0 命中**），所以"初始化成功与否"这一层**问不出来** —— 三态 `Initializing` 不是
保守，是被实测逼出来的；硬规则里那句"不能自行编造 `AMap3DModel` 之类不存在的 API"在这一轮是**执行动作**而不是口号。
② **策略/字段口径的取舍要留取证**：`DRIVING_AVOID_CONGESTION` 是靠 `javap -v` 扫 `Deprecated` 属性选出来的
（`DRIVING_DEFAULT` / `DRIVING_SHORTEST_DISTANCE` 都带、它不带），不是"随便挑一个"；反过来
`getPathRetainDistance/Time` 的**单位**、`getCurStep()` 的**语义** javap 给不出来 ⇒ 这两条**只登记为未验**，
不写成"米/秒"这种看起来像事实的话。
③ **替身引擎不是"降级可用"，是"如实不可用"**：工厂只有两种返回（N-f 1/1/2），`BlockedNavigationEngine` 每个出口
回同一句真实原因、`guidance` 恒 null。**一旦有第三种返回，就会有人拿"直线 + 定时器"填进去** —— 那正是硬规则禁止的
假导航，也是 M1…M8 里 M5 那一次破口要打的位置。
④ **接了能力必须同轮改声明**：`DriverNavScreen` 原本写着"无需地图 SDK"，接了 SDK 之后这句**变成假话**；
只改代码不改声明会留下**假台账**，所以本轮改的是那句陈述并新写 N-l 钉残留 = 0（与 V-3 的"过期注释也是假台账"同族）。

V-2b（两轮前 · 冷启动角色与后端画像同源）的记录在 DONE 的「V-2b」一节与审计 §76、`DECISION_LOG.md` D-030；
**它的四条纪律仍然有效**：
① **"未知"与"否定"必须是两个值，而"否定长什么样"只能由后端定**：本轮实测后端的否定是 `{"code":0,"data":null}`，
而 `Api.kt:486-487` 把它读成**空 `JSONObject`** ⇒ 工程里原有的 `data 非空` 前置条件不是保守，是**把唯一能生效的那条
清绑定分支变成不可达**（撤档永不生效）；
② **判据的入参必须是调用方当场观测得到的量** —— 本轮第一版发明了 `ProfileAnswer` + `deniedByServer` 中间态，
读完 `Api` 之后**当场整块回退**（要调用方猜的中间态就是第二个真相源，实测现在全工程 0 命中）；
③ **守卫挡不住的接口只能靠早退**：`driverShifts()` / `driverPickups()` **不吃** `driverIdParam()`（后端按登录会员
识别身份），所以"撤档了还照发四条司机请求"这件事 ⑥ 那套守卫**结构上管不到**，只有 `return` 管得到
（判据 RR-c 因此钉相对行号而不是关键字）；
④ **降级与升级不是同一条通道的两个方向**：只允许降级（硬规则 1），进司机域必须是用户的显式动作 + `bindDriver` 成功。

V-3（指令 §54 司机异常上报）**已完成并搬进下方 DONE 的「V-3 §54 司机异常上报」一节**；
实测记录见该节、审计 §75、`DECISION_LOG.md` D-029。**开工前先读那节的五条**（本轮踩的，与代码无关但更贵）：
① **§54 要求"先读取真实 enum，不要自己发一套后端无法接受的 type"** —— 本轮先把后端读死（异常类型 enum **不存在**、
`AppDriverLegActionReqVO` 是四个键、`transport_leg.remark` **不是异常落点**），才允许出现"前端分类 + 自由文本 remark"这个形态；
② **后端没有的东西，客户端可以自己做，但只能做"措辞"不能做"接口"**：不新增 endpoint、不新增键、不改 URL 口径；
③ **"关键字出现次数"不能当判据**（EX-i 第一版把四道守卫全删也不红）—— 要钉**生效代码的确切字面量**；
④ **测试不许拿被自己约束的常量当期望值**（截断用例原本 `assertEquals(EXCEPTION_REMARK_MAX, …)`，把上限抬到 2000 照样绿）；
⑤ **新增一条司机域路由要同轮动五处**（`Routes` 常量、`DRIVER_ONLY` 集合、`DRV` 深链正则、SR-h 锚点、SR-a 下限）。

V-2（Phase 1 壳路由与分域闸用例）的记录在 DONE 的「V-2」一节与审计 §74、D-028；它的四条纪律仍然有效：
① **验收"看到/看不到"这类判据落在导航图里时，先问能不能抽成纯函数**，别为了测 UI 引 Robolectric；
② **一条行为只让一个类钉**；③ **"0 命中"的判据必须先证它能命中非 0**；④ **负控脚本先验基线**，基线不绿一律不采信后面的 ✔。

⑬（精确到站 ETA + 三个 `distanceKm` 口径）的记录在 DONE 的"⑬"一节与审计 §72、D-026；它的四条尾巴里
`QuoteResult` 三个金额字段缺键塌成 0.0、6 处自造别名 `"eta"` **仍未修**（V-3 范围外，别顺手"兼容"掉）。

**V-1 / V-2 / V-2b / V-3 由指令 v2.0 对表新增（2026-09-29 扫描实测，审计 §70）**：
（这一批用 `V-` 前缀而不是继续排圈号 —— 圈号 ⑭⑮⑯ 已被**路线图位次**占用（离线缓存 / MapLibre / UI 细节），
  再当轮次号用一次，就正好犯本节下面那条"两套同号不同义"记的错。）

- ~~**V-1 §52 司机动作幂等**~~ **已完成**：原判断"`clientRequestId|requestId|Mutex|withLock|actionLock|inFlight`
  `app/src/main` 命中 0"是对的，但**修法不是"Mutex"** —— 见 DONE 的 V-1 段：`withLock` 是排队，重复请求会照发给后端；
  真正用的是 `tryLock` + 成功保留窗，并且**先证明了后端没有可复用的键**才动手。
- ~~**V-2 Phase 1 验收用例**~~ **已完成**：登记时写的"三条行为"两条落成纯函数用例、一条确认已有主。
  实际形态：`nav/ShellDecision.kt` 两个 `internal` 纯函数（**不是** `RouteGuard` 那个设想的名字，也不新增导航中间件），
  `BusPostRoot` 改成调用它们并删掉私有 `deepLinkAllowed`；用例 9 条 + 判据 SR-a…SR-h + 两套负控（结构 12 / 用例 6）。
  "司机壳可达页集合不含用户首页"这条**没有**建成"集合相等"式判据 —— 那需要在测试里再抄一份导航图（第二个真相源），
  改成了**目录级结构探针**（S-e/S-f：用户目录引用司机域路由 0、司机目录引用用户业务页 0）+ 唯一合法出口点名（S-f2）。
- ~~**V-2b 冷启动的角色复核**~~ **N-1 的前一轮完成（2026-09-30 00:0x）**：登记时写的"`startDestinationFor` 只看 `Prefs.role`、
  冷启动没有任何地方再问一次 `/driver/profile`"**核实成立**，但**登记时设想的修法不是病根** —— 病根是
  `Repo.bindDriver()` 那句 `o.length() > 0` 把后端唯一的否定形状（`data:null` → `Api` 的空对象）排除在判据外，
  **撤档在客户端本来就不可能生效**，多问一次也一样问不出结果。实际形态：`nav/ShellDecision.kt` 两个**三态**纯函数 +
  工作台一个复核点 + **只降级不升级** + 撤档账号**实测只发 1 条请求**（`server.requestCount = 1`）。
  ⚠ **残留边界（不许写成已修）**：复核点在**进壳之后**（`WorkbenchViewModel.syncFromServer()`），不是 `startDestination`
  之前，所以撤档残留 DRIVER 的人**仍会先落进司机壳一次**再被弹回用户壳 —— 见 DONE 的 V-2b 一节尾巴③。
- ~~**V-3 §54 异常上报**~~ **前三轮完成（2026-09-29，审计 §75 / D-029）**：登记时写的是"`canReportException` 已算出但 `.actionable` 零消费者、`Repo` 里没有任何
  exception 调用 ⇒ 状态机说'可报异常'而界面无入口"，**读码核实成立**。第一步按 §54 读了后端：
  **异常类型 enum 在后端不存在**（`yudao-module-transport` 全模块），服务层**早就有** `case "exception"`
  （`DriverAppServiceImpl.java:1498`）但**控制器没接出来** ⇒ 审计 §21 记的 `MISSING` 是真的；本轮按
  "后端没有的安卓可以自己写、但要符合架构要求"补了 `@PostMapping("/leg/exception")`（`AppDriverController.java:226-230`，
  两行、复用既有 `@Valid AppDriverLegActionReqVO`）—— **跨仓、未提交（无 git）、未部署 ⇒ 端到端未验**。
  `AppDriverLegActionReqVO` **四个键** `driverId/legId/photoUrl/remark`（**没有 `action` 键**，动作名在 **URL path** 上）、
  Android 发三个 ——
  所以形态是**前端分类措辞 + 自由文本 `remark`**；**缺的是类型码表，不是端点**，
  代价与边界记 D-029 与审计 §75；**没有自造 type 键、没有自造接口**。
- ~~**⑧ 路线图第 8 步 AMap Navigation**~~ **本轮（N-1）完成**：登记时写的是"`BLOCKED`（依赖缺失已取证，不做假实现）"，
  **这一半被本轮解除** —— `com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600` 已进 `debugRuntimeClasspath`
  （jar 50,991,398 B / 2,403 entries，`<packaging>jar</packaging>`），依赖到位后按 §55 的顺序先 `javap` 核签名再动代码。
  实际形态：`core/navigation/`（纯判据层 `NavigationEngine.kt` + 唯一碰 SDK 的 `AmapNavigationEngine.kt`），
  判据 **N-a…N-l 十二条**（N-a 19 条用例 / N-i 顶替式并存 **1/0/0** / N-j 9/0/0 / N-h StateFlow 2/2/0 / N-l 过期声明残留 **0**），
  结构负控 **18 条全 ✔** + 用例级负控 **M1…M8 全 ✔**（`cmp` 逐字节回验恢复）；
  全量 **29 类 / 295 例 / 0 failure 0 error**（`24 executed`，29 份 XML mtime 02:00:02），
  APK **126,516,687 B**（较 V-2b 基线 57,941,374 B **+68,575,313 B ≈ +68.6 MB**，实测归因见 BUILD_STATUS）。
  ⚠ **没变的那一半**：运行时行为**零验证**（无真机），"后端不下发逐转向"仍是**这半条没有第三个来源**——
  见 DEVICE_STATUS 四条未验项与 BLOCKED 本节改写。
- **#51 旧 `DriverNavScreen` 转 DEBUG ONLY + §24 假步进 6 处 → 0** —— **下一轮**（用户硬规则"**不要直接删除文件**"，
  所以是**降级入口**而不是删除）：N-1 之后 `core/navigation` 已经给了真路线，那个 `stepping / delay(4_000L) / stepIndex + 1`
  的自走页面**从"唯一能跑的东西"降级成"调试用的假步进"**，它的存在不再有任何不可替代性；
  §24 当前命中 **6 处**（本轮复跑仍是 6，目标 0），判据是 §55 D。

**已知冲突，登记但不在本轮改后端**：`AppDriverHandoverRespVO:52` 的 `@Schema` 注释写"0待确认 1已确认 2有争议"，
与 `TransportHandoverStatusEnum`(0..7) 不符 —— 表里以后端**枚举**为准，注释错误只在 Android 侧记账。
同类的还有 ⑩ 抓到的三处（审计 §34 台账与后端 `@Schema` 都少记了 `distanceProvider` 的 `EUCLIDEAN` 与 `reasonCode` 的
`ROAD_UNREACHABLE`；§34 修改方案 ③ 建议拿站点名填 `originalAddress`
与 `AppSendOrderCreateReqVO.java:60` 的注释直接冲突），处置记录见审计 §34"本轮状态"段与 §66、`DECISION_LOG.md` D-022。
⑪ 又添两例同族（**后端文档与实现不一致，只在 Android 侧记账**）：`AppBusNearbyRespVO.java:128-129` 的 `@Schema`
仍写 `routeProvider` 是"AMAP / EUCLIDEAN"，而实现 `AppBusServiceImpl:722/:730` 发的是 `SCHEDULE`、`:758-759`
已经不再把估算打成 `EUCLIDEAN` —— 模型 `providerText` 按**实现**给文案，并保留"来源未识别（X）"分支兜住文档里那些码。

**⑧ AMap Navigation（路线图第 8 项）—— 依赖与判据层本轮（N-1）已收口，剩下的全是运行时那一半**

⑦ 收口后，链路上下游的状态是：**定位已可信**（GCJ-02 单出口）、**道路已可信**（`RealRoadRouteValidator` + 来源白名单）、
**上报已可信**（不重放、过期即停报）。**本轮补上了第四层**：路线与逐转向**有真实来源**（SDK 引擎），但它**只在源码里可信**。

上一版这里写的"动工前必须做的事"①②**本轮已做完并留档**（① `dependencies` 实测解析到
`com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600`；② 逐个 `javap` 核对真实签名后**才**建 `core/navigation`，结果记 D-031），
③"拉不到依赖 → 维持 `BLOCKED`"那一支**没有走**：依赖拉到了 ⇒ `DriverNavScreen` 的假步进**从"唯一能跑的东西"降级成"调试用的假步进"**，
这正是 #51 成立的前提。下一轮开工前必须复核的三件事：
① **§24 假步进 6 处 → 0** 只在"旧页面转 DEBUG ONLY"这一件事里顺手归零；**不许**为了探针变红去扩白名单，
   也不许把 DEBUG 入口做成 release 里也能点（那条要同时过 §55 A 的安全组）。
② N-1 钉住的是**静态形态**（判据层 19 例 + N-a…N-l 十二条 + 两套负控），而 `Initializing` 的**上限时长**、
   `getPathRetainDistance/Time` 的**单位**、`getCurStep()` 的**语义**三条**本机实测不出来**（无真机），
   已记在 DEVICE_STATUS 的四条未验项里 —— 后续任何一轮都不许在那三条上编数。
③ 硬规则"宁可暂时不能导航，不允许假导航"在本轮之后有了**新的具体含义**：引擎在、依赖在，
   所以"不可用"**只能**来自替身引擎如实回绝（N-f 工厂只有两种返回），不再来自"写死一个定时器顶上"。

**⑦ 遗留的真机项（不在本轮能解决的范围）**：见 §57 三行 `ENVIRONMENT_BLOCKED` ——
① OEM 网络定位若本身回 GCJ-02，`Locator` 的无条件换算会**转两次**（必须按 provider 分流，凭猜加白名单就是假精确）；
② 45 秒停报的实际表现、"已丢弃 N 个未确认点"是否真出现在通知栏；
③ 无 Robolectric，`fixOf` / `trackFixes` / 前台服务重试环零覆盖（补法记在 §60 第 7 项）。

---

## 常驻自检（每轮开工先跑，输出必须留档）

| 检查 | 命令（完整版在 `ANDROID_PARITY_AUDIT_V2.md` §55 / `CONTEXT_LOCK.md` §八） | 2026-09-29 实测 |
|---|---|---|
| 真实性关键字（窄口径，§55 C） | `grep -rniE "euclidean\|haversine\|straight\|mock\|fake\|demo stepping" app/src/main/java \| grep -vE -e <白名单>` | **9 行**（⑩/⑪ 基线 4；⑫ +5；**⑬ 复跑仍 9 ⇒ 本轮没有开新的距离展示出口**）：原有 4 行仍是 `MissionMarkers.kt:97/101` 带"仅表示远近，不是道路里程"标注的**唯一直线展示出口** + `WorkbenchScreen.kt:209/220` 它的 private 取数与调用；**新增 5 行全部是 ⑫ 写的口径说明注释**（`AlertEngine:90`、`HomeScreen:233`、`ParcelScreen:340/441/442`），内容都在说"这个分钟数是 Haversine 直线 ÷ 25km/h 估的，不许当道路 ETA"—— 是**禁止性说明**，不是伪造几何。**没有**为了让它归零去扩白名单或删注释；下一步若要收敛，正确做法是把"注释行"这条规则从 `Models.kt` 一段推广成通用规则，且必须连同宽口径一起说明 |
| 真实性关键字（宽口径，含"直线/连线/stepping"，§八 ①） | 同上，pattern 加 `stepping\|two.?point\|连线\|直线` | **44 行**（⑩/⑪ 基线 30 → ⑫ 42 → **⑬ 44**）。按文件构成：`DriverNavScreen 8 / ParcelScreen 7 / WorkbenchScreen 5 / MissionMarkers 5 / Repo 3 / MapCanvas 3 / HomeScreen 3 / SendScreen 2 / BusScreen 2 / AlertEngine 2 / TraceView 1 / Models 1 / DriverMissionMap 1 / BusMap 1`。⚠ **抄数时必须同时抄"是哪一轮 + 白名单是哪 8 条"**：本轮 +2 的两行是 `Models.kt` 的 `distanceCaliberText`（计价里程口径文案，写在 :1641、复核时已漂到 :1649 —— **这一轮自己又演示了一次"行号会漂，按内容找"**）与 `SendScreen.kt:559` 它上方的来源注释；违规部分（§24 假步进 4 行 `stepping`）**数字未变**。不带白名单的同 pattern 是 89 行量级，两个数不可比。**#51 复跑（2026-09-30）：44 → 49（N-1）→ 本轮 45**，`DriverNavScreen 8 → 4` 且**其余 16 个文件逐项未变** ⇒ 那 −4 全部由被删掉的假步进机器带走，**归因是实测的、不是推断的**；剩下那 4 行全是禁止性说明（"旧字段是直线值，不显示""这里不用站点连线顶替"）。窄口径 **9** / `GeoMath` **27** / `DemoData` **3** 三个数本轮**一字未变** ⇒ 这一轮既没开新的直线展示出口，也没新增直线计算点。⚠ **2026-10-01 01:55 复跑（`bash tools/check_authenticity_counts.sh`，rc=0）：窄 9 / 宽 44 / `GeoMath` 27 / `DemoData` 文件 4 / 假步进 0 —— 白名单九条一字未改（脚本 mtime 仍是 #51 那轮的 09-30 02:46、1,800 B）**。**宽 45 → 44 的 −1 归因到文件粒度**：本轮逐文件构成 17 行加总正好 44（`ParcelScreen 7 / WorkbenchScreen 5 / MissionMarkers 5 / DriverNavScreen 4 / Repo 3 / MapCanvas 3 / HomeScreen 3 / NavigationEngine 2 / BusScreen 2 / AmapNavigationEngine 2 / AlertEngine 2 / TraceView 1 / SendScreen 1 / Models 1 / DriverSdkNavCard 1 / DriverMissionMap 1 / BusMap 1`），与「⑬ 的 14 行表 + N-1 的 +3 文件（`NavigationEngine` 2 / `AmapNavigationEngine` 2 / `DriverSdkNavCard` 1 = 49）+ #51 的 `DriverNavScreen 8→4` = 45」这条**台账链**逐项对得上，唯一差项是 **`SendScreen` 2 → 1**（该文件 mtime 09-30 20:50 = F-2 ⑥ 窗口；现在只剩 `:593  if (r.estimated) "路网暂不可用，当前为直线估算"` 这一条，那是**禁止性说明**、不是直线几何）—— ⚠ **只能归到文件、归不到行**：本工程无 git，那一行原来是什么文本没有留档，**"少了一行"是实测，"少的是哪一行"不是**。`DemoData` **3 → 4** 是**口径漂移不是内容增长**：第四个是 `ui/common/Bits.kt:39`（注释"演示数据横幅已移除，但保留 DemoData.kt 作为离线兜底"，**mtime 09-28 23:58 早于"3 文件"那次读数**）⇒ 只能说明历史那三次的 D 与现在脚本的 `grep -rln "DemoData" app/src/main/java \| wc -l` 不是同一个扫描面，**登记为待核尾巴，不许写成"本轮没涨"**。⚠ **本轮自己又踩了一次"三个口径互引"**：收工自检时先用手写 `grep -rniE "euclidean\|haversine\|straight" app/src/main --include=*.kt` 量出 **46 行（不带白名单）/ 34 行（滤注释行）**，这两个数与脚本的 9/44 是**三套口径**（扫描面 `app/src/main` vs `app/src/main/java`、pattern 少 `mock\|fake\|连线\|直线`、白名单有无），**都不采信、只采信脚本那一行输出**。⚠ **F-2 ⑧ 收工复跑（2026-10-01 03:51，脚本原样跑 `tools/check_authenticity_counts.sh`）：宽口径 44 → 46**， 窄 **9** / `GeoMath` **27** / `DemoData` 文件 **4** / 假步进 **0** 四项**一字未变**；那 **+2 逐行归因是实测的**，而且**两套口径给出同一个 +2**（先说口径，因为这两个绝对值不可互引）： ① 脚本自己的"宽口径按文件构成"（带那 9 条白名单）本轮 17 个文件加总正好 46，与 ⑦ 那份 17 行表**逐项对 比只有一项不同 —— `BusScreen.kt 2 → 4`**；② 对照旧树快照 `/tmp/rr_repro.32486`（mtime 00:07:14，早于 ⑧ 的 02:06 编辑窗口）逐文件跑 **不带白名单** 的同 pattern，得 `BusScreen.kt 2 → 4`、`Api.kt 1 → 1`、`Repo.kt 7 → 7` ⇒ 差值仍是 +2。 ② 里那两个"看起来该有数"的文件在脚本构成中是缺席或更小的，原因实测到了具体白名单条目：`Api.kt` 唯一 那 1 行 raw 命中（`:178` 提到 `MockWebServer`）被 `MockWebServer` 那条白名单滤掉 ⇒ **脚本的按文件构成里根本没有 `Api.kt`**；`Repo.kt` 7 → 3 是被"mock 标注 / 适合当日运输"那条滤掉 4 行。⇒ **绝对值分口径、归因结论一致**， 不许把 46 与 120（同一 pattern 不带白名单的全树数）混着抄；上面那句"不带白名单是 89 行量级"是 ⑩–⑬ 期的旧读数， 本轮同 pattern 实测 **120** ⇒ 与 89 之间那 31 行 **归不到轮次**（中间各轮没有这一项的留档、旧读数当时的扫描面也无法复核）， 只能登记成"两个读数、不可互引"，**不许写成"本轮涨了 31"也不许写成"本轮没涨"**。 那两条新命中就是本轮那两句诚实文案 （`:161` KDoc「…两者都不画站点连线」与 `:165`「这次没问到该线路的折线…这里不拿站点连线顶替」），**都是禁止性表述、不是新增直线展示出口**。 ⚠⚠ **一次现成的"数字撞车"**：上面 ⑦ 那句手写无白名单的读数**也是 46** —— 与本轮脚本的 46 **口径完全不同、数值巧合**， **绝不许读成"44 → 44 → 46 里那个 46 已经出现过、所以本轮没涨"**（这正是 §八 ㉑"两个 dex 口径不可互引"与 ⑦ 那条三口径警告的同族陷阱） ⚠⚠ **F-3 ⑤ 收工复跑（2026-10-02 10:1x，`bash tools/check_authenticity_counts.sh` rc=0，/tmp/f3_5_auth_final.log）：窄 **9** / 宽 **46 → 48** / `GeoMath` **27** / `DemoData` 文件 **5** / 假步进 **0**。**本轮起逐文件构成已落档**（18 行加总正好 48：`ParcelScreen 7 / WorkbenchScreen 5 / MissionMarkers 5 / DriverNavScreen 4 / Repo 3 / PolylineDecision 3 / MapCanvas 3 / HomeScreen 3 / BusScreen 3 / NavigationEngine 2 / AmapNavigationEngine 2 / AlertEngine 2 / TraceView 1 / SendScreen 1 / Models 1 / DriverSdkNavCard 1 / DriverMissionMap 1 / BusMap 1`）。⚠ **这正是要落它的原因**：46 那一遍**没有构成在册**（`D-⑤-4`），所以 46→48 **只能归到"新增 `PolylineDecision.kt` 3 行 + `BusScreen.kt` 2→3"这一层，不能做逐项减法**；`DemoData` 文件 4 → 5 同样**归不到轮次**（④⑫ 那遍的状态板 668–742 段实测**没有任何 authenticity 读数**，本轮 grep 过）。三条新命中实测内容全是**禁止性说明**（`PolylineDecision.kt:58/:65/:67` 两句"这里不拿站点连线顶替"、`BusScreen.kt:206` 上方 KDoc"三格都不画站点连线"），**没有开新的直线展示出口** ⇒ 窄口径 **9** 一字未变即为证。下一轮起这一行**可以直接逐项 diff**。 |
| 直线距离调用点 | `grep -rn "GeoMath" app/src/main/java` | **27 行**（⑦ 后 22，⑪ 27，⑫ 27，**⑬ 复跑仍 27 ⇒ 本轮没有新增任何直线计算点**）。按文件构成与 ⑪ 逐项一致：`DemoData 11 / BusScreen 3 / RealRoadRouteValidator 3 / Locator 3 / DriverNavScreen 2 / MapCanvas 2 / WorkbenchScreen 1 / Repo 1 / Models 1`。⑫/⑬ 都只在**读后端已经算好的值**（`carrierEtaMinutes` 出自服务端 `GeoDistanceUtil`、`distanceKm` 出自 `AppBusLineRespVO:27-28` 等），客户端没有新算一次 —— 这正是"窄/宽口径涨了、③ 没涨"应当长什么样的一次对照：**① 抓"说了假话"，③ 抓"算了直线"，两条不可互相替代** |
| **定位是否又放行了业务** | `grep -rn "canArrive\|ARRIVE_RADIUS_METERS" app/src/main/java` | **0 命中**（§32 判据；加回门槛会立刻非零） |
| **旧直线字段是否又被当里程** | `grep -rn "nextStationDistanceMeters" app/src/main/java/com/shanxiang/buspost/ui` | **0 命中**（§63 判据；`data` 层保留 1 声明 + 1 解析是刻意的兼容） |
| 假导航步进 | **旧口径（单文件）**：`grep -n "stepping\|delay(4_000\|stepIndex + 1" app/src/main/java/com/shanxiang/buspost/ui/driver/DriverNavScreen.kt`；**#51 起抬成目录级（判据 N-m）**：在 `app/src/main/java` 下 `grep -rn "stepping\|delay(4_000\|stepIndex + 1" com/shanxiang/buspost/ui/driver --include=*.kt`（含注释行的裸计数，目标 0） | 历史（2026-09-29 ⑬ 收工）：**6 处命中**（:103 声明 / :146 `stepIndex + 1` / :180 `LaunchedEffect(vm.stepping)` / :182 `while (vm.stepping)` / :183 `delay(4_000L)` / :343 开关 UI）。**2026-09-30 #51 复跑：目录级口径实测 0** ⇒ §24 归零。那 6 处机器**物理删除**（`var stepping` + 4 秒 `LaunchedEffect` 循环 + DEBUG 那个"逐条推进"开关），`next()` 收窄成 `showNextStep()`（司机自己翻页，不推进车的位置与班次状态），**九条白名单一字未改**；口径同时**抬高**（单文件 → 整个 `ui/driver` 目录），否则"把定时器挪到隔壁文件"就等于归零。⚠ **归零当轮第一次量到的是 2 不是 0**，命中的是本轮自己新写的注释里那两个字面 token ⇒ 改的是**措辞**、不是口径（这条教训见 §八 ⑲ ②，全过程见审计 §78 四 / D-032 第 6 点）。旧口径那次的记录保留，不许回改历史数字。⚠ **本轮修掉了这条判据自己的一个假绿**：台账原文写成 `… \| … \| grep -n "…" ui/driver/DriverNavScreen.kt`，而同一围栏里前一条 `cd app/src/main/java`，于是这条在不存在的路径上跑、输出 "No such file or directory" 并被读成 **0 命中** —— 判据的路径/锚点写错时，报错方式是"安静地没有命中"，与"真的修好了"在输出上长得一模一样。凡是"预期 0"的判据，必须同时证它**在坏输入下会报红**（负控制），否则 0 只证明命令没跑到东西 |
| 任务点字段是否仍被消费 | `grep -rn "MissionStop" app/src/main/java/com/shanxiang/buspost \| wc -l` = 20；`grep -rn "timelineOf\|\.ordered(" ui/driver \| wc -l` = 3 | 下限判据（§62）：掉下去说明有人绕开后端序号自己排序 |
| 演示面 | `grep -rln "DemoData" app/src/main/java` | **3 个文件**（Api.kt / DemoData.kt / Bits.kt），UI/Repo 无直接引用 |
| **分域（⑥ 新增，硬规则 1）** | `bash tools/check_domain_probes.sh`（§55 H） | **2026-10-01 F-3 ③ 实测：P-a 0 · P-a2 0 · P-b 0 · P-b2 21 · P-c 5 · P-d 3 · P-e 1 · P-f 8**，末行"分域判据通过"（退出码 0）。P-b=0 的含义是"`driverId=0` 的司机 URL 构造不出来"，不是"构造了但被拦"；**不得**给 profile/shifts/pickups/earnings 这四个身份引导接口加守卫，否则永远绑定不上。⚠ **本簿的闸门那行只判 `P-a/P-a2/P-b/P-e`**，P-b2/P-c/P-d/P-f 只 echo —— 所以这四个数变了簿子不会红，抄数必须连构成一起抄。**四个数为什么这轮动**：P-c **2→5** 是 ③ 把"切换入口"拆成两层，逐处可核（改角色 2：`MineScreen.kt:97` 显式授予 + `WorkbenchScreen.kt:180` 档案降级；改视图 3：`BusPostRoot.kt:116` 登录落壳 + `MineScreen.kt:98` 进司机端 + `DriverShell.kt:38` 出司机端「用户版」），**两层的真正判据在 `check_role_reconcile_probes.sh`**（`RR-e 2/2/2` / `RR-e2 0` / `RR-f 2/0`），不在本簿。 ⚠ **P-d 2→3 与 P-f 5→8 这两处归不到本轮，也归不到某一轮 —— 如实记"归属未定"**：能核的是**现状构成**（P-d 三条 = `Routes.kt:45` 声明 + `ShellDecision.kt:31` KDoc 引用 + `ShellDecision.kt:37` 判定；P-f 八条 = `Repo.kt:65/:77/:80` 三处 + `ShellDecision.kt:66` KDoc 一处 + `WorkbenchScreen.kt:83/:193` 两处 + `MineScreen.kt:51/:94` 两处），不能核的是**它们分别在哪一轮涨的** —— 无 git，而 ② 收工那两份留档（`/tmp/f3_2g_closeout.log`、`/tmp/f3_2g_final_books.log`，18:43/18:54）只存了逐本 rc、没存逐本读数，所以"上一轮是多少"这条链在 P-d/P-f 上是断的；最近一份有记录的值来自审计 §29/§64 那一轮（`P-b2 21 / P-c 2 / P-d 2 / P-f 5`，本文件 DONE 的 ⑥ 那块）。 ⇒ **echo-only 的数只有连续抄才有意义**：这条已经排进本轮观察项（见下面 F-3 ③ 块的 `D-③-2`），下一轮开工第一件事是把这 8 个数当基线抄一次，之后每轮连着抄。 P-b2 的 **21→22** 那一半**有留档归因**（P2-1 新增 `driverUnreadCount()` 这个调用点，脚本注释与本簿 ⑥ 块都记着）； 而 **22→21** 这一半**本轮拿不出证据**：判据脚本注释里那句"调用点增减"是我这一轮补写的推断，不是当轮实测登记 —— 已按 `D-③-2` 记为归属未定，**不许当成已解释**。 历史台账里 `P-b2 22 / P-c 2 / P-d 2 / P-f 5` 那一份**按现状改读本行，不回改历史行** |
| **定位与上报（⑦ 新增，硬规则 10 + §17/§45）** | `bash tools/check_location_probes.sh`（§55 I） | **L-a 2 · L-b 0 · L-c 0 · L-d 0 · L-e 0 · L-f 0 · L-g 1 · L-h 0**，末行"定位与上报判据通过"（退出码 0）。L-a 是**恰好 2**不是"至少"（多一处换算＝转两次）；L-g 数的是 `DriverLocationService.kt` 里"已丢弃"的出现次数（通知侧那一个拼装点，**工作台 InfoRow 不在计数内**，别说成"全工程只有一处"）；负控制实测 L-b/L-e 会被人为进一步破口并 exit 1 |
| **可达性三态（⑩ 新增，§34+§11+§14）** | `bash tools/check_reachability_probes.sh`（§55 L）+ `bash tools/reach_negative_controls.sh` | **R-a 0 · R-b 0 · R-c 0 · R-c2 1 · R-d 0 · R-e 25 · R-f 2 · R-g 2 · R-h 0 · R-i 1 · R-j 17 · R-k 1 · R-k2 0 · R-l 5 · R-m 2 · R-n 3 · R-n2 0 · R-n3 2 · R-o 1 · R-o2 2 · R-p 1**，末行"可达性判据通过"（退出码 0）；负控制 21 条逐条破口**全 ✔**。**R-c 与 R-c2 是互锁的一对**（生产代码 `"STATION"` 归零 + 测试报文留一份反面 fixture，删了 fixture 这条判据就没人证）；R-j 只数**行首生效的** `@Test`、R-k/R-m 数**出现次数不是行数** —— 这两处口径都是负控制"失效"之后才改对的，别退回 `grep -c`；R-n2/R-o/R-p 是**语义**判据（分支存在不等于分支说得对）。⚠ 21 条全绿**不等于编译通过**：同轮实测到误删一个换行让全部探针通过而 `compileDebugKotlin` 失败。**⑪ 复跑仍全绿（R-g 由 2→2 但口径变了）**：`BusVehicle` 新增可空 `distanceKm` 后，整文件 `grep -cE "val (walkMinutes: Int\?\|distanceKm: Double\?)"` 会数到 3，R-g 于是改成**只数 `data class Reachability` 类体** —— 这是**收紧**而不是放宽预期数字（把一个契约的字段从另一条判据里摘出去），负控制复跑 **21 ✔**。**F-2 第 4 步复跑（2026-09-30，同轮 15 本批处理实测 rc=0）**：`R-f` 一度读到 **4** —— §四 把 `inService`/`located` 换成 `multiBoolOrNull` 之后，"整文件数这个 helper"多吸进两处**别的契约**的读法 ⇒ 扫描面收回**"声明 1 + `Reachability` 解析体 1"** = **2**。**数字与 ⑪ 相同、口径不同**（现在它只可能数到本契约的读法），涨出去那两处由 `B-*` 那本簿逐条钉住 —— **改的是扫描面，不是期望数字**（沿用本行 ⑪ 的 R-g 先例），复跑实测 `R-f multiBoolOrNull（声明 1 + 可达性解析 1）: 2` 且本簿 rc=0、负控 **21 ✔ / 0 ✘ / 0 △** |
| **ETA（⑪ 公交侧 + ⑫ 货运侧 + ⑬ 里程口径，§38+§14+§68/§69/§71/§35）** | `bash tools/check_eta_probes.sh` + `bash tools/eta_negative_controls.sh` | **T-a 0 · T-b 30 · T-c 9 · T-d1 0 · T-d2 2 · T-e 0 · T-f1 0 · T-f2 1 · T-g 0 · T-h1 2 · T-h2 0 · T-i 7 · T-j1 1 · T-j2 0 · T-j3 1 · T-j4 12 · T-j5 1 · T-k 13 · T-l 0 · T-m 4 · T-n 3 · T-o1 1 · T-o2 3 · T-p 3 · T-q 1 · T-r send 2/2 · leg 1/1 · trace 1/1 · guide 1/1 · preview 2/2 · T-s 0 · T-t 0 · T-u 0 · T-v 5 · T-v2 0 · T-w1 1 · T-w2 0 · T-x 12 · T-y 3 · T-z 10 · T-aa 1 · T-ab 1 · T-ac 0 · T-ad1 6 · T-ad2 2 · T-ae 0 · T-af 4 · T-ag 1 · T-ah 0 · **T-ai 0 · T-ai2 0 · T-ai3 0**（T-ai 家族 2026-09-30 E-1 新立，其余为 ⑬ 收工复跑实测），末行"ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径 + E-1 别名键归零）"（退出码 0）；负控制 **54 条逐条破口全 ✔（0 ✘ / 0 △）**，基线与恢复后各判通过一次（E-1 同轮 +4 条，全打 T-ai 家族）。⑬ 新增 8 条：**T-aa**(精确到站分钟可空声明 =1)、**T-ab**(`Math.max(1, ceil)` 与小程序 `pages/bus/detail.js:132-137` 同口径 =1，0.2 分钟不许显示 0)、**T-ac**(里程/时长读法里的自造别名键 =0)、**T-ad1**(`distanceKm` 可空声明 =6：车辆/线路/段/试算/路线预估/可达性 —— **"六个"只有逐类体数才是六个**，整文件数会把注释也算进去)、**T-ad2**(段里程与计划时长可空 =2，**从 ⑫ 的"打印不判红"残留升格成判据**)、**T-ae**(界面裸读 `.distanceKm` 未判 null =0，闭包判据)、**T-af**(`DistanceCaliberContractTest` 用例 ≥4)、**T-ag**(演示面 lines 下发精确到站分钟 =1)、**T-ah**(界面把 `/driver/position` 报文说成"在途" =0，该 VO 只有 3 个键、没有 status/nextStation)。四处 ⑫ 口径变化仍要连同理由抄：**T-o2 5→3**（改判 `carrierEtaMinutes != null`，负控 sed 同步改 `s/\([eE]\)taMinutes != null/`，否则小写 pattern 匹配不到、控制假绿）、**T-b 19→22→30**（货运侧 +3、⑬ 里程侧 +8 处 `multiNumOrNull` 读法）、**T-v 钉成 =5 并加 T-v2**（总数拦不住"新增一处不带口径的展示"，T-v2 逐行配对）、**T-t 原写法是死判据**（BRE 里 `\(`/`\)` 是分组不是字面括号，永远 0；改 `-F` 后负控制才第一次真报红）。⚠ **T-k 被 ⑬ 自己改成过一次假绿**：给 `BusEtaContractTest` 加用例（9→12）而 pin 仍写 `-ge 9` ⇒ 加用例不报错、删用例也不报错；**"≥N" 型判据在同一轮加测试时必须把 N 抬上去**（⑬ 当时 `-ge 12`/实测 12 ⇒ **E-1 同轮抬到 `-ge 13`**、`T-x` 抬到 `-ge 12`，两份 XML 实测 13 / 12）。残留（打印不判红，**在册待逐端点重核，不许抄 E-1 的结论**）：其余 `Minutes` 家族第二候选名 **5** 处（`Models.kt` 479 `plannedMinutes` / 729 `waitDepartureMinutes` / 1110 `totalMinutes` / 1328 `plannedDurationMinutes` / 1802 `walkMinutes`）〔**F-2 第 2 步已收：2026-09-30 四面取证后逐处删别名，新立 T-aj 家族把"多候选读法"钉成 0；行号是 ⑬ 那一秒的，按内容找不要按行号找**〕。**已修**：ETA 候选名里那 **6** 处自造别名 `"eta"` → **0**（E-1，T-ai/T-ai2/T-ai3 三条判据钉住；反证打在改动前那棵整树副本 `/tmp/ledger_replay` 上同一 grep 返回 **6** ⇒ 这三个 0 与"判据写错了"可区分，本轮确实写错过一次：正则手滑成双引号 ⇒ 永远 0 ⇒ 永远绿）、`Arrangement.etaMinutes` 的 ui/notify 消费点 **0**（⑫ 迁移后归零，现为纯兼容字段） |
| **状态映射 / 段状态机（⑨ 新增，§18+§8+§10+§26–§28）** | `bash tools/check_status_probes.sh`（§55 K） | **S-a 0 · S-b 0 · S-c 0 · S-c2 2 · S-c3 6 · S-d 6 · S-e 10 · S-f 0 · S-g 0 · S-h 3 · S-i 64 · S-j 4 · S-k 17 · S-la 0 · S-lb 0 · S-lc 2 · S-ld 3 · S-le 3 · S-le2 无**，末行"状态映射判据通过"（退出码 0）。**⑩ 复跑仍全绿，未漂移**。要点：**S-la/S-lb 归零才是真收口**（V-3 新增第四张司机页也没有引入第二份状态机）；**S-ld/S-le 由 2→3 是 V-3 同轮抬的**（`DriverExceptionScreen` 既引用映射器也是第 3 个 `Repo.legAction` 调用点）—— 下限跟着生效代码走，**不是把判据改松**；同轮新写 **S-le2**：逐页验"写操作之后必须回读"，缺文件直接 `exit 2`（`grep -c` 对不存在的文件返回空，会被读成"没人回读"这种假红/假绿）；S-le 从 3→2 是 §39 的假 `legId` 被换成 `/driver/arrive`，两处后面都必须紧跟回读；S-c 只数**数字分支**的 `when(…status)`；S-h=3 是"缺键必须 -1 而不是 0"的三个哨兵点 |
| **信封 / 角标 / 凭证照片（P2-1 新增，§22/§23/§52/§7）** | `bash tools/check_envelope_probes.sh`（§55 J） | **E-a 0 · E-b 3 · E-c 0 · E-d 0 · E-e 1 · E-f 1/1 · E-g 1 · E-h 1**，末行"契约信封判据通过"（退出码 0）。E-b/E-e/E-g/E-h 都是**恰好**不是"至少"；负控制实测 E-a/E-b/E-c/E-e 会被人为破口报红。**必须在 Git Bash 里跑**：`python subprocess.run(["bash", …])` 在 Windows 上解析到 WSL 转发，输出乱码且 exit 码全假 |
| **司机动作幂等（V-1 新增，指令 §52 / 审计 §73 / D-027）** | `bash tools/check_idempotency_probes.sh` + `bash tools/idempotency_negative_controls.sh` + `bash tools/idempotency_test_negative_controls.sh` | **I-a 10 · I-b 10 · I-c 0 · I-d 1/0 · I-e 5 · I-f 0 · I-g 0 · I-h 0 · I-i 1 · I-j 11**，末行"幂等判据通过"（退出码 0）；破口负控 **11 条实测 11 ✔ / 0 ✘ / 0 △**（基线与恢复后各判通过一次）；**用例级负控**：把 `ActionGate` 的保留窗与 `tryLock` 两行就地删掉后 `testDebugUnitTest` **10 条红**，`cmp` 证恢复一致后复跑全绿，再跑 `check_idempotency_probes.sh` 仍 exit 0。**V-3 同轮改了两处，都是判据而不是代码**：**I-e 4→5**（回执点现在是 工作台 / 线路 / 交接两处 / **异常上报**，五个写路径各自都要能说出"未发给后端"—— 下限跟着生效代码走，注释里写清了为什么不再是 4）；负控沙箱**由"拷三个司机页"改成整目录拷 `ui/driver/*.kt` + 逐文件预检 `exit 2`**，因为只拷部分目录会让抬到 5 的下限在缺文件的树上永远不满足，那次实测是**基线红着打印了 11 个 ✔** ⇒ 脚本开头另加"基线不通过则一律不采信"的闸门（`eta`/`reach` 两套同轮补了同一道闸门，复跑 50 ✔ / 21 ✔）。口径要点（都是负控失效之后才改对的）：**I-a/I-b 都是"恰好 10"不是"至少"** —— 多一个 `gatedAction(` 就是凭空多设一个出口，两处语义会打架；**I-c 是反向判据**（周期位置上报**必须不被**闸门吞掉，接进去=⑦ 的合并/新鲜度/丢点可见全部失效，"防重复"不该吞点），它是防止下一轮把"幂等"理解成"所有写路径都上闸"的那条线；**I-d 数 `tryLock()` = 1 且 `.withLock(` = 0**（`withLock` 是排队，第一次完成后第二次照发，等于没修 §52）；**I-e/I-f/I-g 是语义/闭包判据**（回执分支存在 ≠ 分支说得对：被拦下那一支不许出现"已提交后端"；`busy = true` 必须在 `work {}` **之前**，旧形态的先后手取决于 `viewModelScope` 的 `Main.immediate`，**本机无 Robolectric 证不了，所以宁可判结构**）；**I-h 是红线**（报文里出现 `clientRequestId`/`requestId`/`bizNo`/`idempotencyKey`/`X-Request-Id` 任一个 =0，后端 11 个司机 ReqVO 逐字段数过、`@Idempotent` 只在两个 `create` 上，客户端造键就是 §123 禁止的假兼容）；**I-j 数行首生效的 `@Test`**（沿用 R-j 的口径，`grep -c` 会把注释里的 `@Test` 也算进来）。⚠ **I-h 第一次量到 1 命中，命中的是我自己写的 KDoc**（说明"为什么不用 `clientRequestId`"）—— 处置是**给判据加注释行排除**（`grep -vE ':[0-9]+: *(\*\|//)'`），**不是删注释**，；**F-3 ⑦⑨ 同轮扩 `H-a…H-m5` 十八行读数**（`H-a 1 · H-b 1 · H-c 0 · H-d 1 · H-e 1/1 · H-f 2 · H-g 5 · H-h 3 · H-i 6 · H-j 3/0/1 · H-k 1/0 · H-l 5 · H-m1 1 · H-m2 3/3 · H-m3 1 · H-m4 1 · H-m5 1`，`I-*`/`U-*` 一族一字未动）；结构负控 **24→38→44 ✔**（38 是本轮**批内**、不含批后追加的 `H-m` 六条 ⇒ 抄数必须连「哪一批」一起抄，写成「批内 44」就是假话）；行为负控 **IT-1 / IT-2 / IT-3** 三条各自「红的正是具名用例」，其中 **IT-3 的红只有行为半边看得见、同一棵树结构判据 rc=0** ⇒ 这两本不互相替代（§八 ㉚ ④）。⚠ **`H-m3` 第一版负控是假绿**（锚点未行尾收口：注入 `… currentStationId } + 1` 之后 `grep -c` 仍命中、读数不变），复跑把锚点收成 `}$` 后 43→44 ✔ ⇒ §八 ㉚ ①与 ⑬ 的 T-ah 同一条规矩 |
| **任务地图无路线态（F-3 ⑧ 新增，指令 v2.0 §六 的 ⑧ / 硬规则"宁可暂时不能导航，不允许假导航" / D-052 / 判据 §八 ㉛）** | `bash tools/check_mission_route_probes.sh` + `bash tools/mission_route_negative_controls.sh` + `bash tools/mission_route_test_negative_controls.sh` | **MR-a 12 · MR-b 1/2 · MR-c 1/1/1/1/1 + 5 · MR-d 0/3 · MR-e 1/0/1 + 1/0/1 · MR-f 1/1/2/1 + 1/1/1 · MR-g 1/1/1 · MR-h 226/228/233（窗口内请求 1）· MR-i 1/1 + 1/1/1 · MR-j 1/1 + 1/0 · MR-k 1/2/0 + 1/2**，共 **11 条探针 / 17 个读数**，末行"任务地图无路线态判据通过"（exit 0）；结构负控 **27 条 = 27 ✔ / 0 ✘ / 0 △ / 0 ◦**（批内 04:04:46→04:08:29 与批后单本复跑 05:17 **同值**，基线与恢复后各判通过一次）；行为负控 **MT-1 / MT-2 / MT-3** `RESULT=PASS`（红的分别 **2 / 3 / 1** 条且每次都含具名用例，逐条 `cmp` 复原后才取下一条基线）。**四条口径要点**：① **MR-d 那个 0 是本轮唯一"目标为 0、靠反证才敢写"的读数** —— 反证用**同一句 grep 机制**打在 DEBUG 屏上的 **3** 处（那三处在正式链外），所以这个 0 与"pattern 写错了"可区分；② **MR-c 末位那个 5 才是"每格都拒绝画线"的证据**，宽口径 authenticity **48 不变不是**（新文案改用"示意线 / 折线"，这两个词不在 pattern 里 ⇒ **换词不是消罪**，见 §八 ㉛ ⑤）；③ **MR-h 是相对行号判据**（`Asking < 请求 < Asked` 严格递增 **且** 窗口内请求恰好 1），钉绝对行号等于钉本轮那一秒（沿用 RR-c 的先例）；④ **MT 三条里"同轮结构判据 rc=1"一律记 `◦` 不记 `✔`** —— 注入让 MR-* 变红是**预期**，但**红着的判据不能反过来证明判据有判别力**（§八 ㉛ ④），那一半由那 27 条结构注入给。⚠ **在册覆盖面缺口 `D-⑧-2`**：**没有 MT-4**，`撤档早退时路线状态退回没问过` 在 MT-3 破口下**不红**（VM 初值本就是 `NotAsked`，清不清都读得到）⇒ 该断言只有结构半边（`MR-f` 的 2），行为半边**没人证**；已按 §八 ㉛ ③ 在脚本头部**预留 ID** 登记 |
| **§54 异常上报（V-3 新增，指令 v2.0 §54 / 审计 §75 / D-029）** | `bash tools/check_exception_probes.sh` + `bash tools/exception_negative_controls.sh` + `bash tools/exception_test_negative_controls.sh` | **EX-a 7/6 · EX-b 1/0 · EX-c 0/1 · EX-d 7/0 · EX-e 1/1 · EX-f 1/1/1/1 · EX-g 5/1/0 · EX-h 0 · EX-i 1/1/1/1**（9 条 21 个判定），末行"§54 异常上报判据通过"（exit 0）；结构负控 **21 条 ✔ / 0 ✘ / 0 △**，用例负控 **X-1…X-8 全 ✔**（每条 gradle 必须红且红的是对应用例，`cmp` 证原样恢复、`[恢复后] gradle exit=0`）。四条口径要点：① **EX-i 数的是四条生效代码的确切字面量各 =1，不是关键字出现次数** —— 第一版数 `canReportException`（页面里 10 次），把四道守卫全删探针照样 exit 0，是负控打出来的假绿；② **EX-c 是红线**（报文里出现 `type` 键 =0，后端 `AppDriverLegActionReqVO` 四个键里没有异常类型，`exception` 走的是既有 `action` 值 + 自由文本 `remark`）；③ **EX-g 的 5/1/0 是语义/闭包判据**（"未发给后端"5 处、回读不一致分支 1 处、谎报成功 0 —— 分支存在不等于分支说得对）；④ **EX-e 用 `-E` 且行尾锚死** `internal const val EXCEPTION_REMARK_MAX = 200$` —— 原来 `grep '= 200'` 会把 2000 也数成命中，抬上限不变红 |
| **壳路由与分域闸（V-2 新增，指令 v2.0 Phase 1 验收 / 审计 §74 / D-028）** | `bash tools/check_shell_routing_probes.sh` + `bash tools/shell_routing_negative_controls.sh` + `bash tools/shell_routing_test_negative_controls.sh` | **SR-a 10 · SR-b 1/0 · SR-c 1/0 · SR-c2 0 · SR-d 4/0 · SR-d2/d3 1/1 · SR-e 0 · SR-f 0 · SR-f2 1 · SR-g 1/0 · SR-h 1**（2026-09-30 V-2b 收工复跑实测），末行"壳路由判据通过"（退出码 0）；结构负控 **13 条逐条破口 = 13 ✔ / 0 ✘ / 0 △**（基线与恢复后各判通过一次）；**用例级负控 7 条 M-1…M-7**：把 `nav/ShellDecision.kt` 的两个纯函数逐个破口（删 `!loggedIn` 分支 / DRIVER 落到用户壳 / 删壳路由拒绝 / 删 `DRIVER_ONLY` 闸门 / 闸门对所有角色过严 / `else -> null` 吞掉陌生路由 / **司机域路由改成带参数以绕开集合相等判定**），每次 `testDebugUnitTest` 必须红且红的是对应用例，`cmp` 证原样恢复后复跑全绿（本轮实测 failures+errors 逐条 **2/2/1/1/1/3/1**）。**SR-a 由 9→10、结构负控由 12→13 都是 V-3 注册 `DriverException` 那条路由时同轮抬的** —— 新增一条司机域路由要同时动 五 处（`Routes` 常量、`DRIVER_ONLY` 集合字面量、`DRV` 深链正则、SR-h 锚点、用例条数下限），漏任何一处就是"路由能进壳但闸门口径漂移"。**三条口径都是这一轮踩出来的，必须连同理由抄**：① **凡是"预期 0"的判据，先证它能命中非 0** —— SR-e/SR-f 第一次跑就是 `0/0`，看着像红线守住了，实际是 pattern 里的 `\|` 在 **BRE** 下是字面字符（GNU grep 3.0 实测：同串带 `-E` 命中 1、不带命中 0）⇒ 判据改成 `grep -rnE`；② **判据文件先验存在再计数** —— `grep` 找不到文件时输出是空的，`[ "" = 0 ]` 会被读成"通过"，那次负控脚本就是在没拷全的目录树上打印了 12 个 ✔（`cp -r app/src/test $SC/app/src/test` 会产出 `test/test/…`），修法是在**探针脚本内部**加文件存在预检 + `exit 2`，并在负控里加"基线不通过则一律不采信"的闸门；③ **注释行不算代码** —— 负控的 `drop` 把代码改写成 `// NEG-CONTROL 摘掉：<needle>`，`grep -c` 照样数到（SR-c/SR-f2/SR-g 当时全假绿），所以这几条改走 `grep -n … \| grep -vE "$NC"`，且 `NC` 必须是 `(^\|[0-9]+:)[[:space:]]*(\*\|//)` 才同时吃得下带行号与不带行号两种输出。另：**SR-f2 给合法跨域点留"恰好 1"个名额**而不是归零 —— `ui/driver/DriverShell.kt` 的"用户版"出口是真业务（且必须紧跟 `AppState.setRole(Role.USER)`），判据写成 0 就是把系统判成违规。**没有**为了变绿去删 `Routes.kt` 里那条指向已删函数的过期注释，而是把它改指 `deepLinkTarget`（过期注释也是假台账）。**V-2b 同轮把 SR-d 由 2→4**（本轮往 `nav/ShellDecision.kt` 加了两个 `internal` 三态纯函数，下限不跟着抬就是"新函数不受册"），并同轮新写 **SR-d2 / SR-d3** 钉两个函数的**整行签名**—— **只数函数个数证不到三态**：把返回类型从 `Boolean?` 退回 `Boolean`（也就是把"未知"与"否定"重新合并）不减函数、 不改调用点，SR-d 老写法照样 4/0 〔**2026-10-02 F-3 ⑥ 同轮扩建（D-050 / 判据 §八 ㉙）**：簿由 11 行读数扩到 **16 行**，新增 `SR-i…SR-n` 六条 —— **SR-i 前缀常量/剥离落点/两个 builder = 1/0/1/1/1**（第二份 `buspost://` 字面量归零，拼的一方与剥的一方必须同源）、**SR-j `Routes.kt` 的 `import android.net.Uri` = 0 / 纯 Kotlin `encodeSegment` = 1**（`isReturnDefaultValues = true` 下任何过 `Uri` 的编码在 JVM 里静默返回默认值 ⇒ 用例绿、判据绿、线上带 `/` 的参数劈成两段，所以"零 Android 依赖"在这本簿里是**可测性质**）、**SR-k 手拼 `"trace/$…"` 模板 / builder 调用点 / `Routes::trace` 方法引用 = 0/13/1**、**SR-l 走 `Routes.deepLink` 的出网口 / 旧 `putExtra("route")` / 通知那行 / 小组件那行 = 3/0/1/1**、**SR-m 四态纯函数 `detailSlotOf` / 其 Compose 依赖 / UI 读取点 / `openDetail` 接线 / 旧的落地前抢读 = 1/0/1/1/0**（第五位 0 钉的是 `D-⑤-1` 那个形状不许回来）、**SR-n `RouteDomainDeepLinkTest` 用例条数 / 两本路由簿合计 = 9/19**（下限，删用例即红）；`SR-f` 由 `0` 扩成 **`0/1`** —— 司机→用户**只读详情**（`BusDetailScreen`）那条在册例外按字面量登记，不靠"我看代码觉得只有一处"。本轮实测 `bash tools/check_shell_routing_probes.sh` **rc=0 / 16 行读数 / 末行"壳路由判据通过"**（终值树 + 台账段复跑 `/tmp/f6_ledger_sr_recheck.log` @22:0x，与 19:2x 那份逐字节同）；结构负控 `shell_routing_negative_controls.sh` **✔=29 ✘=0 △=0**（221 s，本轮 +16 条破口）。⚠ **本轮踩到的两条新口径**：① 新写的 16 条破口里 **4 条一开始是空跑**（`SR-k3` 取错锚点行号 + `SR-l3/SR-l4/SR-f3` 的 `drop()` 只给行加 `//` 前缀，而 `cnt()` 用裸 `grep -cF` **不过滤注释行** ⇒ 注入生效、读数不变、负控报 ✔）—— 修法是按 ㉘ ④ 补 `NC` 过滤（**收紧不是放宽**）而不是把期望值改掉，复跑才得 29 ✔；② `ctl SR-i` 那次报的红**实属 `SR-i4`** ⇒ 破口编号与它打破的判据编号必须实测对上，对不上就是"我以为钉住了"〕 |
| **角色同源（V-2b 新增，审计 §76 / D-030）** | `bash tools/check_role_reconcile_probes.sh` + `bash tools/role_reconcile_negative_controls.sh` + `bash tools/role_reconcile_test_negative_controls.sh` | **2026-10-01 F-3 ③ 收工实测：RR-a 5/4/6 · RR-b 0/0 · RR-b3 1 · RR-c 181/197, 1/1, RET 194 · RR-d 1/1/2, 1/1 · RR-e 2/2/2 · RR-e2 0 · RR-f 2/0**（末行"角色同源判据通过"，exit 0；V-2b 那一轮的旧读数是 `RR-a 5/4 · RR-c 180/196, 1/1, RET 193 · RR-e 3/2/3`，RR-a 第三列与 RR-e2/RR-f 三条**都是 ③ 新加的**，按现状改读、旧行不回改）。结构负控 **16 ✔ / 0 ✘ / 0 △**（基线与恢复后各判通过一次，沙箱**整目录拷**；旧值 13 条）；用例负控 **RRt-1…RRt-10 全 ✔**（`[基线] gradle exit=0` → 十次红 exit=1、failures+errors 逐条 **1/2/3/2/1/2/3/2/2/1** → `[恢复] gradle exit=0` + `cmp 五个源文件与备份一致`，留档 `/tmp/negctl_role_cases2.log`）。**四条口径要点 + ③ 加的两条**：① **RR-b 用 awk 抠 `driverProfile` / `bindDriver` 两个函数体再数** `o.length() > 0`，**不数整文件** —— 函数上方那段"原判据已被推翻"的 KDoc 里出现了这个字面量，整文件数会把它算成残留（假红），而残留真被写回来时又可能落在函数外（假绿）；② **RR-c 是相对行号判据**（本轮 `DEN=181 < SFT=197`、`DEN+1 ≤ RET=194 ≤ DEN+22`）—— **钉绝对行号等于钉本轮那一秒**，改一次格式就红一次；要证的是"早退排在 `Repo.driverShifts()` 之前"这个次序；③ **RR-d 把"停上报 + 弹壳"钉在同一个块窗口里**，两者分开写就是"降了级还在替已撤档的班次上报位置"（这条与硬规则 10 / §17 是同一件事）；`RD4` 的 **2** 是 `WorkbenchScreen.kt` + `DriverShell.kt` **两个文件合数**（第一版只扫一个文件得 1，改的是**扫描范围**而不是**期望值** —— 把期望从 2 降到 1 就是凑绿）；④ **RR-e 的 `Prefs\.role =` 会把比较也数进去**（`AlertEngine.kt` 里 `Prefs.role ==` 的两处 ⇒ 实测 4 而不是 2），收紧成 `Prefs\.role = [a-z]`；同一条坑在 RR-d 也犯过一次：`grep -c 'LaunchedEffect(vm.demoted)'` 数到了**被注释掉的那一行**，导致那条负控该红不红（exit 0），改成整行锚定 `^    LaunchedEffect(vm.demoted) {$` 才红；⑤ **③ 新增：判据分"规则在不在"和"接线有没有绕开"两层**（`RRt-7/8` 打 `AppDomain.kt` 的两个纯函数、`RRt-9/10` 打 `AppState.kt` 的两个 setter）—— 只钉规则的话，"函数留着、调用点改成直写 `_activeDomain.value`"这一手永远不会红，那正是本次要防的那类假绿；⑥ **③ 新增：改判据字面量必须同轮换负控锚点**（`SR-f2` 从 `setRole(Role.USER)` 换成 `setActiveDomain(AppDomain.USER)` 的同一轮补了 `RR-e2`，否则"改回 setRole"从此无人看管；同轮 `v21_requirement_census.sh:48` 的 label 改成"旧角色污染写法(code=0 才算归零)"，**pattern 一字未动** —— 改 label 是让人读得懂，改 pattern 才是动判据） |
| **第 8 步导航引擎（N-1 新增，路线图第 8 步 / 审计 §77 / D-031）** | `bash tools/check_navigation_probes.sh` + `bash tools/navigation_negative_controls.sh` + `bash tools/navigation_test_negative_controls.sh` | **N-a 19 · N-b 0 · N-c 0 · N-d 0 · N-e 1/0/1 · N-f 1/1/2 · N-g 1/1/1/1/1/1 · N-h 2/2/0 · N-i 1/0/0 · N-j 9/0/0 · N-k 7/0 · N-l 0**（2026-09-30 01:5x 收工实测），末行"第 8 步导航引擎判据通过"（exit 0）；结构负控 **18 条 ✔ / 0 ✘ / 0 △**（15 条打 N-*，3 条专打 L-b/L-b2 那条坐标豁免）；用例级负控 **M1…M8 全 ✔**：基线 `exit=0 / failures=0/19` → 八次破口全部 `gradle exit=1` **且红在 `NavigationContractTest`**（M5 红 4 例、其余各 1 例）→ `cmp` 证恢复后与基线**逐字节相同** → 复跑全绿。**四条口径要点**：① **推进只能来自回调**（N-b 定时器 / `stepIndex + 1` = 0，N-h 页面轮询 = 0）—— 用定时器的节奏去猜引擎的节奏就是假导航；② **N-f 是"实现数恰好 2"**，防的是"第三种返回"：多一个替身，就会有人拿直线加定时器冒充它；③ **N-j 把 SDK 类型关进一个文件**（9 处 import / 白名单外 0 / 引擎层之外 0）—— UI 一旦能直接 `import com.amap.*`，真实道路闸门就能被绕开；④ **N-k / N-l 是语义判据**（"高德没给"这类如实文案 7 处、本机自己宣布导航成功 0 处、过时陈述"无需地图 SDK"残留 0）：分支存在不等于分支说得对，接了 SDK 又不改声明就是假话。⚠ 判据与工具自己踩的八条坑记在审计 §77 四（`Class.forName` 是抛不是回 false / `local code; code=$?` 取错码 / 文件级 `grep -v` 是无界豁免 / 破编译的变异不算证词 / 近似切脚本的自检会静默骗人 / 没核出处就"修正"有出处的数 / 并行 gradle 造出假红）。**#51 同轮扩建（2026-09-30，D-032 / 判据 §八 ⑲）：簿子由 12 条扩到 16 条** —— **N-m 司机 UI 目录假步进形态 = 0**（目录级口径，换文件躲不过）· **N-n 未加闸的 DRIVER_NAV 使用 / 注册带闸 / 文件自守 = 0/1/1**（闸与用**同一物理行**，因为跨行的 `if` 单行 grep 判不出来）· **N-o 「非真实导航」标注 / 车道箭头猜测说明 = 2/1**（D-003 那句"显著标注"落在界面上）· **N-p 旧屏引擎挂载 / 高德 / 百度 / 旧屏外部地图函数 = 0/1/1/0**（能力是**搬走**不是复制：正式面上交接两个、旧屏里 `open*` 归零）。**N-g 第六位重锚**：由"全工程 `DriverSdkNavCard(` ≥1"改成"`WorkbenchScreen.kt` 里**恰好 1**"，两个方向都红（0 = 正式包没有导航面，≥2 = 两处各 new 一个引擎抢那个进程级单回调）—— **收紧的正当性来自"旧结构当场是红的"这个实测**。结构负控 **18 → 27 条**（本轮实测 `✔ 基线：副本原样 exit 0` + **27 ✔ / 0 ✘ / 0 △**）；用例级负控 **照跑没有跳过**（基线 `exit=0 failures=0/19` → M1…M8 八次全红且红在 `NavigationContractTest`、M5 红 4 例 → `cmp` 恢复逐字节相同 → 复跑 exit=0），理由是**"改动范围与判据范围不相交"不能凭 mtime 主张**（那条升为 §八 ⑲ ③ 永久规矩）。明细审计 §78 六/七。**F-3 ④⑫ 同轮扩建（2026-10-02 02:1x–04:5x，D-048 / 判据 §八 ㉗）：簿子由 16 条扩到 20 行读数、用例 19 → 26、结构负控 27 → 34、用例级 M1…M8 → M1…M11** —— **N-q 正式导航入口生效行里的 BuildConfig = 0**（构建类型不是能力开关；旧假导航屏那 8 处 `BuildConfig.DEBUG` 是 D-003 红线要求的、一处没拆，仍由 **N-n** 钉 ⇒ **这两条判据不许合并成"DEBUG 归零"**）· **N-r 页面 gate 调用 / 可点字面量 / 旧拼法 / 不可点原因块 = 1/1/0/1**（第三位钉的是**旧写法** `enabled = !blocked` 不许回来，不是钉新写法存在）· **N-s 引擎两道闸 / SDK 层自己判 Blocked = 2/0**（回执那句 reason 与入口闸门从此同源）· **N-t 成因码常量数 / 带码处数 / `NO_ROUTE` 产出点 / `INITIALIZING` 产出点 = 6/6/2/1**（`NT4` 点名 `code = NavigationGateCode.INITIALIZING,` 恰好 1 —— 破口 `Initializing → ERROR` **只有它会红**，`NT2` 那种"数行数"的判据会被"改名不改行数"骗过去）。本轮实测：结构负控 **✔=35（1 基线 + 34 破口）/ ✘=0 / △=0**，用例级基线 `exit=0 failures=0/26` → M1…M11 十一次全红（逐条 failures+errors = 1/1/2/1/4/1/1/1/3/2/2）→ `cmp` 证还原逐字节相同 → 复跑 `exit=0 failures=0/26`。**本轮新增的口径要点**：`navigationGateOf()`（`core/navigation/NavigationEngine.kt:116-148`）是"这一屏能不能点 + 不能点说什么"的**唯一**出口，UI 五条出口（底色 / tag / 副标题 / `enabled` / 原因块）全部从它派生 ⇒ 一个**纯派生**函数不构成第二份真相（不持状态、不落盘、没有 flow）；`INITIALIZING`（还在等回执）与 `NO_ROUTE`（这一单没有可导路线）分格是**顺序判据** —— 引擎侧坏了不许被念成"调度还没给任务点"，否则司机会在页面上等一个后端永远不会来的点 |
| **异常可信化（F-1 新增，指令 v2.1 §六/§七/§三十一 / 审计 §80 / D-034 / 判据 §八 ㉑）** | `bash tools/check_throwable_probes.sh` + `bash tools/throwable_negative_controls.sh` | **TH-a 7/5 · TH-b 4/4 · TH-c 2/0/2 · TH-d 38/38 · TH-e 0（挂起形状 48 / 免检 1）· TH-f 12/1 · TH-g 0 · TH-h 6/1/2 · TH-i 1 · TH-j 1/1/0 · TH-k 1/1 · TH-l 2/2/0**（2026-09-30 17:0x 收工实测，12 行读数 / **27** 道闸门），末行"异常可信化判据通过（§六 缺陷不进演示数据 / §七 取消不被吞）"（exit 0）；负控 **18 条破口**，批处理按留档行数实测 **✔ 行 19**（18 破口 + 1 条"恢复后基线仍绿"自检）、`✘=0 △=0`；只打数不判红：`apiCall` 定义与使用(main) **1**（⇒ 生产调用点 **0**）· `runCatching` 全 main **34** · `catch(Throwable)` 含注释 **4** / 生效 **2**。**五条口径要点（都是本轮踩出来的）**：① **TH-e 的扫描面由内容决定**，不数关键字、不靠手写文件清单 —— 第一版数 `CancellationException` 出现次数（V-3 已证这种数法永远绿），第二版扫 14 个文件的清单（**误判** `DriverSdkNavCard.kt:291` 那处零挂起点的 catch，而清单外的文件永远盲区）⇒ 现在逐个 `catch (e: Exception)` 看它 try **首段**有没有挂起点，并且**违例 / 挂起形状 / 免检三个数一起打**（只打违例的判据，在"规则被写宽"与"破口被删干净"两种情况下输出一模一样）；② **TH-d 与 TH-e 是两条判据不是同一条** —— 负控里有一条"摘掉一处 `throw e` 但保留子句字面量"，那一次 TH-d 照样 38/38 绿、只有 TH-e 红（数子句对写法敏感，逐点配对对语义敏感）；③ **注释行不算生效代码**，且**负控脚本自己也要防**：`swap` 现在强制 `assert old not in new`，否则把 `if (!demoAllowed) throw e` 换成同样字符串的注释，TH-b 会数到注释里那句 ⇒ 破口看着还在、判据是假绿；④ **TH-l 的锚点必须带缩进**（`^ {8}\} catch \(e: NetworkException\) \{$`）—— `apiCall` 那对同类型子句在**列 0**，不带缩进会数成 3/3 把"回落面恰好两类"这件事读错；⑤ **就地簿的 ✔ 计数不可跨簿比较** —— `idempotency_test` 那本 **✔=0** 是因为它**不打印 ✔ 标记**（它的标记是 `[破口后] gradle exit=1` / `[恢复] cmp 一致` / `[恢复后] exit=0`），rc=0 来自簿内闸门；按标记 grep 出来的计数，**先证那本簿用这套标记** |
| **§三 `QuoteResult` 缺键塌 0 + §四 `NearbyResult` boolean 三态（F-2 第 3–4 步新增，指令 v2.1 §三/§四 / D-035 / 判据 §八 ㉒）** | `bash tools/check_contract_defaults_probes.sh` + `bash tools/contract_defaults_negative_controls.sh` | **Q-a 5 · Q-b 0 · Q-b2 0 · Q-c1 0 · Q-c2 0 · Q-c3 0 · Q-d 0 · Q-d2 0 · Q-e1 3 · Q-e2 4 · Q-f 7** ＋ **B-a 2 · B-b 0 · B-c 1 · B-d 0 · B-e1 1 · B-e2 2 · B-e3 0 · B-f 0 · B-g 6**，GUARD-B **29**，末行"缺键默认值判据通过（§三 QuoteResult 五字段可空 + 别名键归零 + 兜底通道关闭 + §四 NearbyResult boolean 三态）"（exit 0）；负控 **22 条破口全 ✔ / 0 ✘ / 0 △ ＋ 1 条 `◦` 反向口径控制、`KEEPFAIL=0`**（非 0 走 **`exit 3`**）。**本轮最重要的一条是"那条负控从来就是假的"**：`Q-e1` 的破口用整文件 `replace(old,'',1)` 删行，而 `EarningItem` 在**同一个文件更前面**有一条逐字相同的 getter ⇒ 删到的从来不是被判定量，破口从未打进去、判据照样绿 —— 这类假绿**在旧标记体系里不可见**（`✔` 是"脚本没报错"，不是"破口生效了"）。修法两条且都**只收紧扫描面**：① 新增 `delbody()`（在**类体内**删那一行）；② 补**反向口径控制 `keep`**：删**别的契约**的同名成员时，本条**必须不响**（响了说明判据收的是成员名而不是契约）。标记因此扩成 `✔ 破口 / ✘ 未响 / △ 存疑 / ◦ 反向控制`。⚠ 同一条坑另在两处被抓到：`R-f` 被 §四 的 `multiBoolOrNull` 顶到 4 ⇒ 收回"声明 1 + `Reachability` 解析体 1"=**2**（见上面可达性行）；`B-c` 那 **1** 是收紧扫描面后仍打的**残留读数**（`SendScreen.kt:225` 的 `fresh = o.optBoolean("fresh")`，默认值方向是保守侧 `false`，但"缺键 ≠ 真不新鲜"，属 §五 B 类、**本轮不判红也不删**）。⚠ **收紧扫描面的同一轮必须把"被摘出去的那些到底还在不在"打出来**（本簿末尾三条：`Models.kt` 的 `amountText` 家族 getter **4**（含 §五 `EarningItem` 那条，归 DV-g2 管）/ 全 `ui` 的未给 getter 使用点 **5**（含 `EarningsScreen`，归 DV-h 管）/ 上面那条 `fresh`）—— 否则 R-f 只是把看不见的东西当作不存在。口径要点：**Q-a 数的是 `QuoteResult` 类体里的可空声明 =5**（三个金额 + 两个整数），整文件数会把 `EarningItem.amount` 也算进去 ⇒ 那条是 §五 的契约，不是这一条的判据 |
| **§五 数字/布尔默认值普查（F-2 第 5 步新增，指令 v2.1 §五 / D-036 / 判据 §八 ㉒）** | `python tools/default_value_census.py --write` + `bash tools/check_default_value_probes.sh` + `bash tools/default_value_negative_controls.sh` | 普查 **472** 处 ⇒ **B 398 / C 74 / A 0**（`DEFAULT_VALUE_AUDIT.md` 的分类定义是 A/B/C/D/E 五类，本轮 census 只把 **B/C/A** 三个数钉进台账与判据；D（UI 占位）与 E（外部真实生产者）**未单独计数，不说成已盘过**）；探针 **DV-a 0 · DV-b 0 · DV-c 0 · DV-d 12 · DV-e 9 · DV-f 2 · DV-g 8 · DV-g2 2 · DV-h 0 · DV-i 0 · DV-j 1 · DV-k 132 · DV-l 8**，GUARD **1/29/34/7**，末行"数字默认值判据通过"（exit 0）；负控 **16 条 ✔ / 0 ✘ / 0 △**。**`A=0` 是结构性事实不是巧合**：后端 App 侧 RespVO 字段清一色包装类型（census 扫后端全模块字段声明，primitive **0** 命中）⇒ 报文里"给了 0"与"没给"长得一模一样，任何把缺键读成 `0` 的写法都是冒充。**DV-k=132 是全项目下限、只许往下走**，本轮只收 Earnings 一族（其余 32 处 C 类在册未动）。⚠ **本轮新增的一条永久规矩：`hits` 会把"修好的事"读成"没修"** —— 普查每行原来只有出现次数，`runCatching` 实测 **44 次 / 43 行 / 34 生效行 / 22 文件**，三个口径差 10；判据与第 9 步的基数**必须取"滤注释后的生效行数"**（脚本因此新增 `code=` 列），而 `code=` 的注释过滤器形状**要与 grep 输出形状配对**：`-r` 带文件名前缀用 `:[0-9]+: *(\*\|//)`，单文件 `grep -n` 是 `行号:内容` 得用 `^[0-9]+:[[:space:]]*(\*\|//)`（用错那次 Q-e2 读到 6 而非 4） |
| **§二十四 `Minutes` 家族第二候选名（F-2 第 2 步新增，指令 v2.1 §二十四 / D-035；簿在 `check_eta_probes.sh` 内，与上面 ETA 行同簿）** | `bash tools/check_eta_probes.sh`（读 **T-aj** 家族那 6 行）+ `bash tools/eta_negative_controls.sh` | **T-aj 0 · T-aj2 0 · T-aj3 0 · T-aj4 7 · T-aj5 16 · T-aj6 6**，GUARD **10**；负控本批复跑 **60 ✔ / 0 ✘ / 0 △**（rc=0）。五处第二候选名逐个走"后端 VO 声明 + producer 赋值 + 小程序消费点 + 演示面下发"**四面取证**后**删读法**，⚠ **处置不是给 `DemoData` 补键** —— 补键等于把客户端自造的键洗成第四个真相源（同 D-033）。**别名删净 ≠ 默认值形状修净**：那五处里 `plannedMinutes`（`Models.kt:497 Int = 0` / `:500 multiNum(…).toInt()`）与 `plannedDurationMinutes`（`:1337` / `:1381`）**读法形状仍是 B 类"缺键当 0"**，落在 `DV-k` 基线内、归 §五 下一轮，**本轮不顺手改**（改法与删键是两件事，混在一起会把"删别名"这件事的判据搞成不可归因） |
| **用户端 create 幂等（F-2 第 6 步新增，指令 v2.1 第 6 步 / v2.0 §52 的用户侧镜像 / D-038；簿与上面 V-1 行**同簿不同族**，`U-*` 是用户域、`I-*` 是司机域）** | `bash tools/check_idempotency_probes.sh` + `bash tools/idempotency_negative_controls.sh` + `bash tools/idempotency_test_negative_controls.sh` | **U-a 2 · U-b 2 · U-c 2 · U-c2 2 · U-c3 2 · U-d 3 · U-e 2 · U-f 0 · U-g 1 · U-h 10 · U-i 2/2 · U-j 4（登记不判红）**，同簿 `I-*` 十条**一字未动**（`I-a 10 · I-b 10 · I-c 0 · I-d 1/0 · I-e 5 · I-f 0 · I-g 0 · I-h 0 · I-i 1 · I-j 11`），末行"幂等判据通过"（exit 0）；负控 **25 行控制 = 24 ✔（11 条 `I-*` + 13 条 `U-*`）/ 0 ✘ / 0 △ + 1 ◦**，基线与恢复后各判通过一次。口径要点（都是本轮实测出来的，不是设计意图）：① **两把闸不是一个闸的两个用法** —— `U-c` 数 `= ActionGate()` 实例必须**恰好 2**（一域一把，硬规则 1），并成一把时 `U-c` 与 `U-c3` 一起红；实证是**打穿 U-a 时 I-a/I-b 仍 10/10**，两本判据互不顶数，这就是"分两本"的目的；② **键与后端同维度不同值** —— `U-d` 钉三个维度（端点 `digest.update(path)` / 身份 `Prefs.mobile` / 整份报文 `body.entries.sortedBy`，抄 `UserIdempotentKeyResolver.java:21-25`），但**值不可能等于服务端那个 MD5**（本机拿不到 `userId/userType` 的服务端表示，猜一个就是假造）⇒ 本机放行≠后端放行，所以用例里"后端回 900 原样透出那句话"必须存在；③ **U-g 钉"键只存摘要"** —— 键表是进程级、活得比页面久，把收货人姓名/手机号/地址写进明文就是个人信息留存（用例用 `^create:[0-9a-f]{64}$` + 四段明文都不是子串钉死）；④ **U-j 是登记项不是判据，因此没有也不该有负控**（`feedback`/`address` 两处 create 后端**零** `@Idempotent`，单方面拦=替后端发明规则），写在簿里是"数字涨了要逐条重核后端注解"，不写成"22 条全有负控"；⑤ **`U-i` 第一次量到 1/2 是判据写错不是代码写错** —— 两处 create 现在都调 `Api.post(path, …)`，字面 grep 只命中未走闸的那条 ⇒ 改判**端点声明面** `val path = "$T/…/create"` 并与走闸数**对账**（"以后有人加下单接口没走闸"唯一能兜住的口子）；⑥ **负控断言要锚分隔符** —— `grep -q "^× U-c"` 会把 `× U-c2 …` 也算命中 ⇒ 假 ✔，现在统一 `"^× $1 "`；沙盒同轮改成 `cp ui/user/*.kt` **整目录**（`I-e` 那次"部分 copy 把下限变成沙盒巧合"的教训在用户域原样重演）；⑦ **测试侧复位按字段类型扫**（`TestHost.resetActionGate()` 由写死名单改成筛 `ActionGate` 类型字段），写死那版本轮就漏过一把新闸 —— 闸门挂在 `object Repo` 上是进程级，Gradle 全类共用一个 JVM，`SendOrderTest` 八个用例里六个提交**内容完全相同**的 `form`，不复位就会被自己的闸吞掉。**同轮全簿 collateral**：`for b in tools/check_*_probes.sh` **14 本全部 RC=0**（新增 `U-*` 没把别的簿顶红）。**包内新鲜度另见 `ANDROID_PACKAGING_FRESHNESS` 那条**（本轮 `packageDebug` 报 UP-TO-DATE 而包里查不到本轮符号，`--rerun-tasks` 才有真包） |
| **APK 打包新鲜度（F-2 第 6 步新增，判据 §八 ㉒ ④ 的加强版）** | `unzip -o app/build/outputs/apk/debug/*.apk 'classes*.dex'` 后**按字节** grep 本轮新串 + 一条已知旧串（先证方法再信 0） | `:app:assembleDebug`（21:08）打印 `packageDebug` 执行过、`BUILD SUCCESSFUL`，但盘上 APK 仍是 **19:33:42 / 113,863,001 B**、15 个 dex 里 `userCreateGate` **0** 命中，而同轮 `intermediates/dex/debug/mergeProjectDexDebug` 是 **21:08:18 且含新符号** ⇒ **陈旧在打包环节的 up-to-date 记账，不在编译**。`:app:assembleDebug --rerun-tasks`（**37 actionable tasks: 37 executed**，**21:13:59 / 113,863,001 B**）后包内实测 `userCreateGate 1 · gatedUserCreate 4 · "create:" 1 · 本轮两条新文案各 1`，旧串 `gatedAction 4 · 本次请求未发给后端 1` 仍在（**分域在包里也成立**），`UserCreateIdempotencyTest` **0**（测试类不进包）、`Lokhttp3/mockwebserver/` 类型描述符 **0**（`mockwebserver` 只在 okhttp 库自带的名字表里出现过一次，依赖是 `testImplementation`，未漏进包）。⇒ **规矩：`UP-TO-DATE` / `executed` / `BUILD SUCCESSFUL` 三种打印都不是"包内新鲜"的证明，必须在 dex 里 grep 到本轮新串才算**；而 **0 命中先证方法**（同一批 grep 若连已知旧串都数不到，那是工具坏了） |
| **构建失败四类分型（F-2 第 7 步新增，指令 v2.1 §三十三 / V21-33 / D-039 / 判据 §八 ㉓）** | `bash tools/check_verification_failure_probes.sh` + `bash tools/verification_failure_negative_controls.sh`（样例：`tools/fixtures/verification-failure/`，规则表：`tools/verification_failure_rules.txt`，分类器：`tools/classify_verification_failure.sh`） | 探针 **rc=0**、输出 **18 行 = GUARD 1 + VF 读数 16 + 结论 1**：**VF-a 类别 4 · 行数 16（下限 14）· 字段数不对 0 / 出处为空 0 · 出处行号回核 指对 9 / 指错 0 / 样例缺失 0**，**VF-b 真实样例判对 6/6（判错 0）· 用例失败那份拒绝分类 1（分类器 rc=4）· 四类被样例覆盖 4**，**VF-c `app/src/main` 四类符号 0 / `VerificationFailure` 0（反证：同一模式在 test 源集 10）**，**VF-d 业务三套错误通路 1/1/1 · 分类器内写死的类别名 0**，**VF-e `verify_device.sh` 引用分类器 1 · 笼统那句"构建失败，先修编译错误" 0 / 旧 `tail -3` 管道形态 0**，**VF-f 四类 object / 逐名点齐 / 用例条数 4 / 4 / 8**，**VF-g 规则表消费者 6（≥3）· 分类器读的就是这张表 1**；负控 **13 ✔ / 0 ✘ / 0 △ + 1 ◦**、收尾逐份 `cmp 一致` 且真身目录没多出文件。⚠ **首轮负控自己抓到两条判据缺陷**（不是注入失效）：`✘ 9` 把 `object EnvironmentFailure` **改名**而 `VF-f` 只数个数所以不红 ⇒ 改成**逐名点齐**；`△ 10` 换规则表路径响的是别的判据（消费者下限 `>=3` 太松）⇒ 新增**精确钉赋值行**（`RULES="$ROOT/tools/verification_failure_rules.txt"` 恰好 1）。**两条都是"只数个数对'改名/换目标'无感"的同一个形状**，修法一律**收紧判据**而不是放宽期望。⚠ **出处列自己也会烂**：`Unrecognized VM option` 表里写 `:15`、实测在 `:12` ⇒ 新增 VF-a 逐行回核闸（`awk NR==n` + `index($0,lit)`）+ 负控制 13；"非空"判据看不见行号漂移。⚠ **落点偏离指令字面并已登记**：四类**不进 `app/src/main`**（构建失败时 App 进程不存在 ⇒ 无运行期消费者；且业务侧已有 `ApiException`/`NetworkException`/`Ack.error` 三套，再放一套就是**第四份真相**，由 VF-d 钉成 1/1/1），真实消费者是 bash 分类器，Kotlin 侧只在 **test 源集**留 `sealed interface` + 四个 `object` 当类型层证据（V21-30 点名的本就是 `VerificationFailureTest`）。⚠ **`assembleRelease` 本轮 rc=1 而分类器给 `UNCLASSIFIED` 是设计意图**（HTTPS 闸门 `app/build.gradle.kts:171` 属**策略拒绝**，不是四类构建失败），**没有**为让它归类而加规则行 |
| **`safeApiCall` 落点（F-2 第 8 步新增，指令 v2.1 §六 / §七 / V21-07 / D-042 / 判据 §八 ㉕）** | `bash tools/check_safe_call_probes.sh` + `bash tools/safe_call_negative_controls.sh` | 探针 **rc=0**、输出 **15 行 = GUARD 1 + SA 读数 13 + 结论 1**、闸门 **34 道**（实测 `[ "$…" ] ‖` 共 35 条，多的一条是 GUARD 自证）：**SA-a 定义 1 / 旧名 0 / 反证 9**，**SA-b 体内 1/1/4/0**（取消出口 · `throw e` · 四类 · 兜底；兜底模式在 Repo 的反证 **38**），**SA-c 使用点 6**（Repo 5 逐点点名 `1/1/1/1/1` + 页面消费点 1），**SA-d 伪装形状 0**（反证 `emptyList()` 在 Repo **24**）· `attempt` Repo **0** / 全 main **≥7** · `getOrThrow` **1** / 签名 **1**，**SA-e 页面 1/2/1/2/1**（声明 · 复位 · 分类 · 两处文案 · 副标题）+ 旧形状 **0/0/0** + 新形状那句 **1**，**SA-f 用例 12 / 取消 2 / 四类 3 / 不出网 3**；负控 **17 条破口 + 3 条自检行 = ✔ 20 / ✘ 0 / △ 0**、rc=0、80 s。**五条口径要点（都是本轮实测出来的）**：① **下限只能实测、不许推算** —— 我按"12 − 2 = 10"把下限写进两本簿，两本**同时红**、实测 **7**；先归因（迁移前 12 来自 ⑦ 留档产物 `/tmp/vf_probe_check_throwable_probes.log` 23:30:30，同判据同扫描面；5 个迁移点**全部**原本是 `attempt { … }.getOrNull()`；非 Repo 那 7 处一字未动 ⇒ `12 − 5 = 7`）再改数，**"我以为的差值"不是"量出来的基线"**（同类错第三次，已升为永久规矩）；② **每个"0"配同机制反证**，否则"grep 坏了"与"真的没有"在输出上长得一模一样；③ **死闸门**：`echo "…": fail=1;`（冒号代替分号）让那条闸门**永不生效**，负控没跑之前它是静默的 ⇒ 新簿写完必须**逐条打红一遍**才算装配完成；④ **打到判据簿自己头上的破口，必须连"执行哪一本"一起换** —— 第 17 条注入的是沙盒副本那本簿、`ctl` 跑的却是真身 ⇒ 永远 rc=0；`ctl` 因此加第三参（`verification_failure` 那本早用 `$PRSC` 处理过同一件事，**当时没抄它的机制**）；⑤ **结构层与行为层分两本钉** —— 工程无 Robolectric，`BusScreen` 那两句文案在 JVM 里渲染不出来（行为侧只能靠 `SafeApiCallContractTest` 12 例 + 静态判据），而"helper 还在、但没人调用"（⑧ 之前的真实状态：生产调用点 **0**）**只有静态判据看得见**。 ~~⚠ **在册缺陷（本轮新登记、一字未动）**：线路页首屏 `load()` 自动选中线路却**不拉折线** ⇒ `polylineSubtitle()` 那句"后端未回传该线路折线"在**问过之前**就说了出口，与本轮修的那句同族（把"没问"说成"没有"）；SA-e 那两条（`E_SUB=1` 与 `E_OLD_SUB=0`）**都允许它存在**，所以它只有这段文字记账，动它=扩大战线~~ **已修（2026-10-02 / F-3 ⑤ / D-049）**：首屏 `BusScreen.kt:347` 跟着选中问一次 + 相位收成三格（`core/map/PolylineDecision.kt:18-26`），那句"后端未回传该线路折线"现在**只在 `Asked` 下可达**，判据同轮**抬高**为 `SA-e3 首屏问 = 1`（不是维持 `E_SUB=1/E_OLD_SUB=0`）。⚠ **本轮判据规模读数**（`check_safe_call_probes.sh` rc=0 @10:18:24）：输出 **21 行**、闸门 **60 道**（`grep -cE '^\[ "\$'` 实测；本轮新增 **24** 条引用 `E2_/E3_/E4_/P_/D_ASK` 变量、旧 36），结构负控 **✔=29 ✘=0**、**26 条破口 + 3 条自检行**（`/tmp/safe_call_negative_controls.run.log` @10:00:15，195 s）⇒ 上面 ⑧ 那一遍的 15 行 / 34 道 / 17+3 是**历史值不回改**，两遍不可互引 |
| **`runCatching` 取消形状（F-2 第 9 步新增，指令 v2.1 §七 / §三十一 / D-043 / 判据 §八 ㉖）** | `bash tools/check_runcatching_probes.sh` + `bash tools/runcatching_negative_controls.sh`（闭包检测器 `tools/runcatching_detector.py`，逐点账 `docs/android/RUNCATCHING_AUDIT.md`） | 探针 **rc=0**，读数 **GUARD 1 + 检测器原始行 1 + RC 读数 6 + SUMMARY + RESULT**：**RC-a 生效行/生效文件/出现次数/含注释文件 = 30/16/46/22**（本轮前 34/17/44/22；⚠ **出现次数 +2 与生效行 −4 方向相反不是矛盾** —— 删 4 处代码里的 `runCatching`、留 4 段说明为什么换形状的注释，注释里共提到 6 次），**RC-a2** 同管道反证 `attempt` 行 = **11**（>0 才允许采信那个 30），**RC-b violations = 0**（= 指令要的 suspend cancellation violation），**RC-b2 sites/候选/挂起名面 = 30/0/98**（检测器 sites 与 grep 生效行**必须相等**，两套机制一边失真则两边的数都不能用），**RC-c 四处字面量 = 1111 / 回退残留 0/0**，**RC-d `runBlocking`/`suspendCancellableCoroutine` = 0/0**，**RC-e 分类表 34 行 / 已迁 4**；负控 rc=0 —— **12 条破口全打破 + 2 条反向"必须不红" + 2 条预检**（预检：检测器不在场 / 源文件缺失 ⇒ **exit 2**，"跑不了"绝不被读成 0），末行"恢复后基线仍为绿"。**四条口径要点**：① **分类按包围边界，不按外层签名** —— `Runnable{}` / `Handler.post{}` / `remember{}` / `AndroidView factory` / `onClick: () -> Unit` / `internal var launch: ((String) -> Unit)?` 这些**非 suspend lambda 会截断取消可达性**，而 `let`/`run`/`also`/`forEach`/`onFailure` 是 inline 会透传 ⇒ 34 点里只有 **4** 点够得着取消，其余 **30 点保持**（删它们等于把错误处理删了，指令禁止"为了得到 0 而误删合法 runCatching"）；② 那 30 点的"结构免疫"靠两条腿：全工程 `runBlocking = 0` 且 `suspendCancellableCoroutine = 0` ⇒ 非 suspend 成员函数不可能挂起 —— **RC-d 就是钉这两条腿的**，任一非 0 则第三节整表重做，不是"一个计数超了"；③ **检测器必须核接收者**：`Api.get(` 是真挂起，`main.post(` 是 Handler、`sp.getString(` 是 SharedPreferences（前两个是朴素名字交集曾经报出的假阳性），块体从 `{` **之后**取 ⇒ 同行前缀不计；④ 四处都用既有通用出口 `core/net/Attempt.kt` 的 `attempt`（与 `runCatching` 唯一差别 = 取消穿透，都捕到 Throwable、都回 `Result`、下游照旧 `.getOrDefault`）⇒ **对外行为一字未变**，所以本轮没动任何用例断言。⚠ **本轮最大的一条自我错误（已修，规矩入 §八 ㉖）**：跨簿影响我按推理写成"SA 锚点指 `Repo.kt:445`、与本轮 `:359` 不是同一行 ⇒ 无需改；SA 簿复跑确认 rc=0"，实跑是 **rc=1**（`× SA-d Repo 又出现 1 处 attempt`），破口在 SA-d 数**整本文件**的 `D_ATTEMPT_REPO`（⑧ 的不变量 0）而不是行号 ⇒ 同轮把 "=0" 收成 **恰好 1 处 + 整行字面量点名**、全 main 下限 **7→11**（⑧ 的 7 + ⑨ 的 4，逐点归因）、SA 负控**新增第 7b 条**（数量不变只换名 ⇒ 只有点名锚点会红）；SA 负控复跑 **18/18** 打破、RESTORE `11/11/11/11`，TH 负控 19 ✔、SR 负控 13 ✔，三本恢复后基线均绿。**证据**：`:app:compileDebugKotlin` rc=0（`BUILD SUCCESSFUL in 27s`，15 tasks：1 executed / 14 up-to-date，`$TEMP/f2_9_compile.log`）；17 本 `check_*.sh` 全量复扫（唯一的红就是 SA-d，已按上面处置）。**三态**：生产源码 4 文件 ⇒ SOURCE_COMPLETE；只验到 debug 编译面 ⇒ BUILD_VERIFIED(debug)，全量 `test`/`lintDebug`/`assembleDebug` 留到 ⑱；取消行为只有 JVM 静态判据 + `ApiCancellationTest` 那一层证据 ⇒ **DEVICE_VERIFIED = NOT VERIFIED ON DEVICE**（深链跳转、TTS 设语种两句在真机上没念过）|
| **导航会话绑定当前路线（F-3 ⑬⑭⑯⑰ 新增，硬规则"宁可暂时不能导航，不允许假导航" / D-053 / 判据 §八 ㉜）** | `bash tools/check_navigation_probes.sh` + `bash tools/navigation_negative_controls.sh` + `bash tools/navigation_test_negative_controls.sh` | **N-a 34**（用例下限同轮到 34）· **N-u `1/1/3`**（`AcceptedRouteSession` 定义 1 / `routeBindingOf` 定义 1 / `routeFingerprint` 生效行 3）· **N-v `1/1/1/1/7`**（SDK 层绑定调用 1 / `Unbound` 拒收分支 1 / 重读形状 1 / 凭证写入点 1 / **作废点 7**：高德自算、算路失败、请求未受理、超时、没有路径、停止导航、释放引擎）· **N-w `0/1`**（指纹里哈希/随机数 0 / 终点进凭证 1）· **N-x `0/1`**（缓存三出口生产调用点 **0** / 写入点 1）；末行"导航引擎判据通过"（exit 0）。结构负控 306 行 / 16,143 B 批内与批后**同值**；行为负控 ✔=**17**，M12 红 **1** 例 / M13 红 **2** 例 / M14 红 **1** 例 / M15 红 **3** 例（每条都含具名用例，`cmp` 复原后 `[恢复后] exit=0 failures=0/34`）。**四条口径要点**：① **N-v 那支重读是"每次开始导航前"而不是"验路那一刻"** —— 只验一次就是拿旧凭证为新路线开绿灯（V21-09 两行 ❌ 的原样）；② **N-w 的 0 是红线的形状**：`routeFingerprint` 只能是实测数拼接，本机想不出来时**不许**用 `sha256`/`UUID`/`hashCode` 糊一个像版本号的串；③ **N-x 的 `0/1` 不是漏，是本轮只买半边的凭据** —— 缓存只写不读，给没人读的缓存加 TTL 就是"把还没接上登记成已经安全"（`D-⑭-1`）；④ **7 个作废点必须逐名列举**，写成"若干处清空"就退化成计数判据（同一模式在 `I-e`/`H-*` 上抓过两次） |
| **台账锚点结构与死别名撤读（F-3 ⑬⑭⑯⑰ 新增，指令 §五 字段兼容 / 硬规则"不许替后端猜键名" / D-053 / 判据 §八 ㉜）** | `bash tools/check_doc_anchor_probes.sh` + `bash tools/doc_anchor_negative_controls.sh` + `python tools/check_doc_anchors.py --pin tools/fixtures/doc_anchor_pins.txt` + `bash tools/route_alias_test_negative_controls.sh` | **DA-a `records=1216（聚合 1216）OK=915 distinct aid=967`** · **DA-b `0 / DUP=0`** · **DA-c `15`**（只降不升）· **DA-d `在册=262 新增=0 消失=0 可疑 aid=262 在册表行数=262`** · **DA-e `286`** · **DA-f `578/277`**；末行 rc=0。负控 **8 条破口 + 1 条 POS**（POS = 台账顶部插一行让所有行号位移而 `aid` 不变 ⇒ 在册不许响；实测绿）。行为负控 **RA-1…RA-5 各 ✔**（红的正是"那个键"对应的具名用例）+ 每格 `◦ 同轮 DV 结构判据 rc=1` + `RESULT=PASS`。**五条口径要点**：① **`aid` 不含行号**是这本闸门的承重结构 —— 否则每轮记账都会把在册池顶大（#159 同族）；② **可疑锚点只有两种合法收口**：改成内容属实的锚点，或 `--pin-write` 正式登记；**把行号抹掉**会撞 DA-d 的"消失即红"；③ **DA-f 是"归零判据的恒真检测"**：把取标识符的正则改坏，ID-MISS 会掉到 0 看着最绿 ⇒ 每条"目标 0"都要带一格"它为什么不是恒真"；④ **数 CR 只能逐字节**：`grep -c $'\r'` 对整本纯 LF 文件报出"等于行数"的假数 ⇒ 换行符读数全部按 `b.count(b'\r')` 重抄；⑤ **DV-w 那架跨口径桥**（grep **116** = census 行 **114** + delta **2**，delta 具名 = 逐名列两条函数声明）⇒ 两口径同值时**必须写等式**，否则下一轮把两种含义当一个数用 |
| **重算那一族的因果闸（赛道⑪ N-4 新增，指令 v2.1 §八 的 ⑪ / 硬规则"只能由导航引擎触发，不得本地假判定替代后端" / D-057 / 判据 §八 ㊱）** | `bash tools/check_navigation_probes.sh` + `bash tools/navigation_negative_controls.sh` + `bash tools/navigation_test_negative_controls.sh` | **N-aa 七行 / 二十四个数**：族类型/话术出口/非空格数/分支数/flow 承载 `1/1/4/5/2` · `null` 那一格的字面 `1` · 两条广播各接几次/各自作废凭证 `1/1/1/1` · 帧写入总数/清帧/成功帧照抄 code/失败帧只带 code `5/1/1/1` · 本机主动重算/把 notify 当偏航通道/引擎层自己算垂距 `0/0/0` · 页面订阅/页面唯一话术/页面造帧/页面下本机结论 `1/1/0/0` · 旧屏垂距阈值比较/偏航只读后端位/本机判定措辞 `0/1/0`；同轮 **N-a 52**（下限抬到 ≥52 的依据是本轮那七例）、**N-v 作废点 7 → 9**（两条广播各加一处）、**N-j 10/0/0 未动**；末行"第 8 步导航引擎判据通过"（exit 0）；结构注入 **24** 条全命中（批内 ✔=102 ＋ 簿内聚合行，单本 `grep -c` 口径是 103，两口径不可互引）、行为注入 **M27–M33** 红 2/2/1/1/1/2/1。⚠ **这一行与上面"第 8 步导航引擎"那一行、与"导航会话绑定当前路线"那一行是同一本判据册的不同族**：三行各自记的历史读数（N-a 19 → 34 → 37 → 45 → **52**）**都不回改**，只在本行记当前值 |
| **⑫⑬ 3D 视觉层（N-5 新增，指令 v2.1 §八 的 ⑫⑬ / D-058 / 判据 §八 ㊲）** | `bash tools/check_mission_route_probes.sh` ＋ `bash tools/mission_route_negative_controls.sh` ＋ `bash tools/map3d_visual_negative_controls.sh` ＋ `bash tools/check_domain_probes.sh`（`P-a2` 那格会把注释里的 `Running.` 也算进去，改措辞而不是加过滤） | 本轮实测：**MR-l…MR-r 15 行打印读数全部等于目标**（`MR-l 1/2/5/0`、`MR-n 0/1`、`MR-o 0/3`、`MR-p 1/3`、`MR-q 1/1/1/1`、`MR-r 0/23/16` 等）；结构注入 **49** 条（含本族 22 条）批内 **✔=49 / ✘=0**；行为 **MV-1…MV-16 一对一**，批内与紧贴终值两次都是 **16 ✔ / 0 ✘ / 13 △ / 3 ◦**、`RESULT=PASS`；用例 **16 条 / 0 失败**（类数 47→48）。⚠ **改了 `Map3DVisualContractTest.kt` 的任何一条断言就必须重跑第三本**：本圈首跑它给了 3 ✘，而三条 ✘ 全是断言自己骗人（子串匹配、夹具让注入不可见）—— 只跑 gradle 全量是看不出来的，全量永远绿。 |
| **⑭ 离线真实道路缓存与失败披露（赛道⑭ N-6 新增，指令 v2.1 §十三 / V21-13 / D-059 / 判据 §八 ㊳）** | `bash tools/check_navigation_probes.sh`（读 `N-x` 与 `N-ab` 那 **10** 行；⚠ `N-x` 的目标本轮由 `0/1` 改成 `1/1` —— ⑭ 起了一个真实读点，**这是同轮改判据不是漏改**：留着旧目标就是把一个正确现状判成破口，理由连"读点出现"一起写在脚本里）＋ `bash tools/offline_route_cache_negative_controls.sh` ＋ `bash tools/v21_requirement_census.sh`（这一族的"生效行"**以判据簿为准**，census 那一列同时抄、并标明它偏高 1） | 终值实测：`N-x 1/1`；`N-ab` 九行 = `读出口一把/旧出口 1/0`、`TTL 常量/过期边界/抓取来自入参/现场取时 1/1/1/0`、`并发容器出现数/旧 LinkedHashMap 生效行 4/0`、`来源原文/改写枚举 1/0`、`过期格类型数/格内带几何 1/0`、`否决先于写入 判定/传参/行序 1/1/1`、`两参删除/序号淘汰 1/1`、`披露读点/交出几何/三格各一句/并句措辞 1/0/3/0`、`graphVersion 生效行/哈希随机数/披露调用点 0/0/1`，末行"第 8 步导航引擎判据通过"（exit 0）；行为簿 **B-1…B-10** 单跑（`[行为簿 rc=0] 08:35:09`）与批内（10:13:17 → 10:18:52，336s）**同一组读数 10 ✔ / 0 ✘ / 5 △ / 5 ◦**、末行 `RESULT=PASS`。⚠ **三条必须连着抄的口径**：① **那 5 △ 是结构半边的覆盖面缺口**（B-3 / B-7 / B-8 / B-9 / B-10 注入后 `NAB-*` 与 `N-x` 全部 rc=0 ⇒ 披露层**措辞类**破口目前只有行为半边可辨，登记 `D-N6-2`；那 5 ◦ 是注入把结构判据打红，按 §八 ㉛ ④ 记 `◦` 不记 `✔`——红着的判据不反过来证明它有判别力）；② **`D-N6-4`：新簿第 6 条（并发读写）没有被任何注入咬过**——单线程 JVM 撞不坏，硬配一条"应当红"就是编造判据 ⇒ 只算"结构半边钉住、行为半边未证"，**不许写成双向配对**；③ **census 与判据簿对同一符号给出 1 与 0**（`LinkedHashMap` 两处都在注释里，census 的过滤器不滤单行 `/** … */`，`D-N6-3`）⇒ 台账**两个数都抄并注明谁偏高**，只抄好看的那个就是假账 |
| **表单控件三格（#283/#288/#289 新增，D-067 / 审计 §86 / 判据 §八 ㊻）** | `bash tools/check_form_field_probes.sh` ＋ `bash tools/form_field_negative_controls.sh` | **2026-10-08 19:0x 实测：判据 rc=0，读数 `FF-a 1 / FF-b 2 / FF-c 1 / FF-d 0 / FF-e 0 / FF-f 1 / FF-g 1/1 / FF-h 0`；负控 ✔ 10 / △ 0、rc=0**（含第 10 条打**副本里判据簿自身**的 `[ "$FE" = 0 ]` → `= 9` 反证，证那三个"目标 0"不是永不为红的摆设）。⚠ **这一簿的每一格目标值都是设备像素读数定的**，不是读源码觉得应该这样：改前 y=1342 只有 `(245,248,252)`×`(247,250,253)` = **1.02:1** 且 0 个边框段（⇒ `FF-a` 只能 1 处）、禁用按钮白字压蓝板 **1.37:1**（⇒ `FF-g` 灰板与灰字两支必须齐，`1/1`）、切换链接塌成 **59.0×40.4dp** 贴边小链（⇒ `FF-h` 只能 0 处）。⚠ **两条不许合并的口径**：`FF-d`（自造时效 = 0）钉的是**客户端诚实性** —— 后端 `expire-times` 是 10m 而 `Repo.sendSmsCode` 把响应体整个丢掉，屏幕上写"5 分钟内有效"就是假承诺；它**不是**文案偏好检查，别为了加一句提示语去放宽它。`FF-f`（`onFocusEvent` = 1）钉的是**逐框**聚焦：把 `focused` 提成两框共享状态时源码处数不变、这一格不红，只有设备读数能守（聚焦行描边 3px `(199,213,224)` → 6px `(46,123,191)`，同屏未聚焦框仍 3px）。⚠ **本轮被证据推翻的是读法不是代码**：第一版脚本对禁用态只取 bounds 内**最暗**像素，而改前那版是白字比板亮 ⇒ "板 = 字"、ratio 恒 **1.00**、改前改后读数一样 ⇒ 这条判据当场没有分辨力；改成"最亮/最暗各取、取离板更远的那个"并把两个 ratio 一起进日志。**目标值类读数必须先证明这条读法能分辨改前改后**，与"目标 0 要反证"是同一条规矩的两个方向。⚠ **负控第 1 条红名单是 `FF-a,FF-b,` 两条而非单条**（删描边那一刀必然同时删掉第二个 `RoundedCornerShape(BusRadius.field)`）—— 那是回归本来的形状，**如实登记，不放松判据去凑单条红**。⚠ 跨簿影响：本轮三个文件（`core/ui/Components.kt` / `ui/login/LoginScreen.kt` / `ui/login/LoginViewModel.kt`）只被 `throwable_negative_controls.sh` 当注入锚点，单本复跑 **18 ✔ / rc=0**；**28 本负控全批本轮未跑**。证据 `docs/device-evidence/20261008-181700/`（34 文件）。**#294 挂账**：`ui/` 内 **29** 个 `BusTextField(` 调用点只有 `ui/driver/DriverExceptionScreen.kt` 传非空 `error`，那一格的 danger 红描边仍**只有源码证据** |
| **到近三档声道 ＋ 布局走查工具（L-1／㊼ 新增，D-068 / 审计 §87 / 判据 §八 ㊼）** | `bash tools/check_navigation_probes.sh`（读 `N-ac` 与 `NAC12/NAC13` 那几行）＋ `bash tools/check_location_probes.sh` ＋ `bash tools/check_mission_route_probes.sh` ＋ `bash tools/check_test_suite_hygiene_probes.sh` ＋ `bash tools/ui_capture.sh` 后再 `python tools/layout_audit.py <dump 目录>` | **2026-10-09 收工现读（`C:/temp/r69_pass.log`，全批 `books=23 rc0=23 red=0`）**：`N-ac 开关话术出口(纯层)/页面调用点/骨架在纯层/在页面 = 1/3/1/0`、`NAC12/NAC13「到近作业播报」= 1/1`；`MR-i` 两支条件各 **1** ＋ 块内 `RouteUnavailableState/后端那句/retry 入口 = 1/1/1`；`L-w = 0`；`SU-a 53/0 · SU-b 55/2 · SU-c 0/560 · SU-d 7/3 · SU-e 10`。⚠ **四格读数里有三格本轮重锚过，每一格都带同轮会红的配对注入**：`NAC2` 由 **2→3** 的唯一增量是卡片重排加的那条「到近作业播报」开关行（**"恰好 N"的摩擦设计：加一行就必须同轮把数改对**）；`SU-b 53→55` 是本轮新建 **2** 本 test 源集文件推出来的现读，**不是抬基线**（目标值只能等于现读）；`MR-i` 的 `else if` 由 `vm.routeFailure != null` **扩成** `missionGuide != null \|\| vm.routeFailure != null`（后端把班次明细答成 `{"code":0,"data":null}` 时 `routeFailure` 是空的，旧条件让整块导航面**无声消失**），块窗口 `s+8`→`s+10`、`reason` 改读后端那句 `missionGuide?.routeUnavailableReason`。⚠ **`L-w` 这一格是本轮唯一"判据读错代码"的**：`HomeScreen` 的闸门写在**上层块** `if (g == null) {` 而赋值行本身不带空值闸门，旧写法只在同一行里找 ⇒ 实测 `LW=1` 而代码是拦住的；判据改成"赋值行**或**上层块带闸门都算" —— ⚠ **这是缩小判据面，所以必须两边都证**：两条非法形状（撤闸门 / 闸门只写在尾注释里）实测 `exit 1`，两条合法形状（闸门在赋值行本身 / 闸门原样在）实测 `exit 0`（`C:/temp/r69_lw_keep.log`）。⚠ **反证的打印字样会骗人**：一次性驱动脚本的 `run_one` 只认"必须红"，于是两条**应当不红**的反证被标成 `✘ 负控制失效` —— 同一个 `✘` 两种含义；常设簿因此新增 `ctl_loc_keep()`（期望 `exit 0`），重跑两边 `✔ exit 0`。**"目标 0"与"目标不为 0"的反证方向不同，标签必须分开**。⚠ **`layout_audit.py` 的两条读数一条是假的**：「页首标题与副标题兄弟重叠」回读同一帧节点（两个都是 `android.widget.TextView`、下面那个 `clickable=true`，PNG 上两行明显分开）⇒ **工具局限、不算缺陷、0 行代码为它改动**，规矩是"兄弟重叠要连 `class` ＋ `clickable` ＋ bounds 一起看"；`r68-15` 那一格（`clickable=true`、`text`/`content-desc` 全空、`bounds=[17,1993][83,2104]` ⇒ **66×111 px = 25.1×42.3 dp**，420dpi 下 1dp=2.625px ⇒ 下限 126px）是**真缺陷** ⇒ **#313**。⚠ **口径不可互引**：本轮"判据 **23** 本"是 `ls tools/check_*probes.sh \| wc -l` 的现读，§86 那句 `24/24` 用的是含 `check_form_field_probes.sh` 的另一把尺子；证据面那 **26** 张（PNG＋同名 xml）挂的是联调包 `4fb22eec…`（**114,551,589 B**），**不挂** canonical `86c8e8df…`（**114,551,597 B**）—— 差 8 B、是两个文件 |
| 本机地址 | `grep -rn "127.0.0.1\|localhost" app/src/main` | 0 命中；`.kts` 里的 4 行是注释 + 构建期拦截（`--include` 会漏掉 `.kts`，必须单独 grep）。**2026-10-01 01:55 复跑：main 侧仍 0 命中（grep rc=1，不是"空输出被读成通过"——rc 一起抄），`.kts` 那 4 行行号未漂（`:18/:22` 注释 + `:167/:168` 构建期拦截）** |
| 签名文件 / 日志拦截器 / 凭证字面量 / key 字面量 | **照抄 `ANDROID_PARITY_AUDIT_V2.md` §55 E 的六条命令**（`grep -rn "127\.0\.0\.1\|localhost" app/src/main --include=*.kt --include=*.xml`、`grep -n … app/build.gradle.kts`、`find . -name "*.jks" -o -name "*.keystore"`、`grep -rn "HttpLoggingInterceptor" app/src/main`、`grep -rniE "(access.?token\|apikey\|api_key\|secret\|password\|passwd)\s*[:=]\s*\"[A-Za-z0-9_\-]{8,}\"" app/src/main --include=*.kt`、`grep -rn "AMAP_KEY\|amap_key" app/src/main --include=*.kt --include=*.xml \| grep -v "@string/amap_key\|BuildConfig.AMAP_KEY"`） | **全部 0 命中**（2026-09-29 ⑬ 收工最后一遍逐条复跑），`.kts` 那条是 **4 行**（`:18/:22` 注释 + `:167/:168` 构建期拦截）。⚠ **key 那条的 `grep -v` 是判据的一部分，不能省**：省掉后按裸 token 数 `AMAP_KEY` 实测 **2 命中**（清单注释 + `DriverMissionMap.kt:71` 的 `BuildConfig.AMAP_KEY.isNotBlank()`，都不是密钥值），看着像红线破了。本表原来只写语义没写命令，就是为这个在收工时被自己误读了一次 ⇒ **抄"0 命中"必须连 pattern 一起抄**。⚠ **2026-10-01 01:55 六条逐条复跑（rc 与行数同抄）**：main 本机地址 **0 命中（rc=1）** · `.kts` **4 行** · `find . -name "*.jks" -o -name "*.keystore"` → **0** · `HttpLoggingInterceptor` **0** · **严格**凭证 pattern（`\{8,\}`）**0 命中（rc=1）** · key 字面量过滤后 **0（rc=1）**。**两处与上面历史读数的差，必须连口径一起抄，不许互引**：① 把严格 pattern 的下限 `\{8,\}` 放宽成 `*`（允许空串）才出现 **1** 命中 = `ui/user/MinePages.kt:173  password = ""`，那是"改完密码后把表单三个字段一起清空"的赋值行（同批 `passwordConfirm = ""` / `smsCode = ""`，全是空串），**且该文件 mtime 09-28 23:59、不是本轮加的** ⇒ 红线判据取 `\{8,\}` 那版、**0 命中成立**，"1 命中"只属于放宽口径，两个数不可互引；② `AMAP_KEY` **裸口径本轮实测 5 行**（`AndroidManifest.xml:55` 注释 + `:58  android:value="@string/amap_key"` + `AmapNavigationEngine.kt:61` + `:269` + `DriverMissionMap.kt:71`），而上面记的"裸口径 2 命中"是 ⑬ 那轮的数 ⇒ **中间 N-1 往 `AmapNavigationEngine` 加了两处 `BuildConfig.AMAP_KEY.isNotBlank()`**，**裸口径不是常数、不能当判据**，它只是"过滤步有没有漏"的反证；`grep -v` 之后 5 行全被 `@string/amap_key\|BuildConfig.AMAP_KEY` 吃掉 ⇒ **仍然 0 个密钥字面量** |
| 全量用例 | `./gradlew "-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK" -Dfile.encoding=GBK :app:testDebugUnitTest --rerun-tasks`（⚠ **F-2 起这串 jvmargs 是命令的一部分**：守护进程的 `file.encoding` 决定它写给测试 worker 的 @argfile 用 GBK 还是 UTF-8，UTF-8 那遍 `java.exe` 按 ANSI 码页解不开非 ASCII 路径 ⇒ 六个 worker 全起不来、`--rerun-tasks` 照样报 "24 executed"、`test-results/` 里一份 XML 都没有。只加客户端那半 `-Dfile.encoding=GBK` **不顶用**。过程与字节级证据在状态板 `TEST_STATUS`，规矩升为 §八 ㉒ ⑤）；**37 个测试类**（F-2 ⑦ 新增 `VerificationFailureTest`；F-2 ⑥ 新增 `UserCreateIdempotencyTest`；F-2 ①–⑤ 新增 `MinutesContractTest`/`QuoteNullSafetyTest`/`NearbyBooleanContractTest`/`EarningsContractTest`；**F-1 起 31 个测试类**：那轮新增 `DemoExceptionIsolationTest` / `ApiCancellationTest`；以下为历史累计 ——**29 个测试类**，⑦ 新增 `LocationReportPolicyTest`/`CoordinateTransformTest`/`LocatorCoordinateTest`，P2-1 新增 `ScalarEnvelopeContractTest`，⑨ 新增 `StatusMappingContractTest`/`DriverLegStateMapperTest`/`DriverLegRespContractTest`，⑩ 新增 `ReachabilityContractTest`，⑪ 新增 `BusEtaContractTest`，⑫ 新增 `SendEtaContractTest`，⑬ 新增 `DistanceCaliberContractTest`，V-1 新增 `ActionGateTest`/`DriverActionIdempotencyContractTest`，V-2 新增 `ShellRoutingContractTest`，**V-3 新增 `DriverExceptionReportTest`/`DriverExceptionContractTest`**，**V-2b 新增 `RoleReconcileTest`/`WorkbenchRoleReconcileContractTest`**，**N-1 新增 `NavigationContractTest`**） | **356 tests / 0 failures / 0 errors / 0 skipped**（**F-2 ⑦ 收工 01:25:50**：`BUILD SUCCESSFUL in 38s`、**`24 actionable tasks: 24 executed`**、**37 份 XML mtime 全为 01:25:50**；**+8 全部归因于新簿 `VerificationFailureTest`**（两侧同剥 `Test` 后缀做机器比对：`ADDED={VerificationFailure: 8}`、`REMOVED={}`、`CHANGED={}`）；⚠ **同一命令 01:21:27 那遍 rc=1 / `BUILD FAILED in 57s` / 356 例中 1 failure，红在 `ShellRoutingContractTest`（tests=10 fail=1）**——那是"树里带着一行未还原的负控注入"的读数、不是回归，见 D-041 与 §八 ㉔ ⑦。**以下保留 F-2 ⑥ 那一遍的读数：348 tests / 0 failures / 0 errors / 0 skipped**（**F-2 ⑥ 收工 21:07:20**：`BUILD SUCCESSFUL in 41s`、**`24 actionable tasks: 24 executed`** 且 **36 份 XML 真在盘上**（㉒ ⑤：`executed` 数与产物数是两件事，本轮两件都核）；**+10 全部归因于新簿 `UserCreateIdempotencyTest`**，其余 **35 类逐份计数一字未变**（机器比对 XML 逐类计数与 F-2 ①–⑤ 那张 35 类表，不是回忆）。**以下保留 F-2 ①–⑤ 那一遍的读数：338 tests / 0 failures / 0 errors / 0 skipped**（**20:30:14**：`BUILD SUCCESSFUL in 40s`、**`24 actionable tasks: 24 executed`**、35 份 XML 逐份读出再自相加；**+27 逐条可归因**：新增簿**只有四本** 6 / 7 / 6 / 8，`REMOVED: {}`、`CHANGED: {}`；⚠ 第一次机器比对因把类名剥掉 `Test` 后缀而造出"新增 `Repo` / 消失 `RepoTest`"的**假差异** —— 与 F-1 那次"手写错类名"是同一条坑的两种触发方式，**归一化键名本身也是差异来源**。**以下保留 F-1 那一遍的读数（永久记录见 DONE「F-1」/ 审计 §80）：311 tests / 0 failures / 0 errors / 0 skipped**（**F-1 收工 17:02:02**：`:app:testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL in 57s`、**`24 actionable tasks: 24 executed`**、31 份 XML 逐份读出再自相加；**+12 是机器比对归因的**：新增类**只有** `DemoExceptionIsolation` **7** 与 `ApiCancellation` **5**、消失类 `[]`、**既有 29 类逐份条数一字未变**（`CHANGED: []`）。⚠ 第一次比对把基线类名手写成 `MissionMapContractTest` / `ProductOrderContractTest`（真实名没有 `Contract`）⇒ 打出"新增 5 类 / 消失 3 类"的**假差异**，**"无孤儿类"这个结论依赖键名**。⚠ 本轮**必须**重跑而不是沿用 299：五套就地负控带 `--tests` 过滤会把 `test-results/` 洗成只剩一类，旧读数**失去产物支撑**。**F-1 的 31 行逐类表在状态板 `TEST_STATUS`**，本轮起以那张为准。**以下保留 E-1 那一遍的读数（永久记录见 DONE「E-1」/审计 §79 七）：** 299 tests / 0 failures / 0 errors / 0 skipped（E-1 收工：`43 actionable tasks: 43 executed`、29 份 XML mtime **04:21:33**；上一轮 N-1/#51 为 295）（⑥ 10 类 105；⑦ 13 类 140；P2-1 14 类 157；⑨ 17 类 189；⑩ 18 类 206；⑪ 19 类 215；⑫ 20 类 224；⑬ 21 类 233；V-1 23 类 244；V-2 24 类 253；V-3 26 类 267；V-2b 28 类 276（那次 `--rerun-tasks` 报 **24 executed**、28 份 XML mtime **23:56:47**）；**N-1 29 类 295**，收工终检 `--rerun-tasks` 报 **24 actionable tasks: 24 executed**、**29** 份 XML mtime 全为 **02:00:02**）。**逐类表（每轮抄这张，总数对不上时用它定位）**：`LocatorCoordinate 6 / CoordinateTransform 12 / RealRoadRouteValidator 16 / **NavigationContract 19** / Api 11 / ScalarEnvelopeContract 17 / BusEtaContract **13** / DistanceCaliberContract 4 / SendEtaContract **12** / StatusMappingContract 17 / DriverLegRespContract 6 / ActionGate 5 / ShellRoutingContract **10** / **DriverExceptionReport 7** / **DriverExceptionContract 6** / **RoleReconcile 5** / **WorkbenchRoleReconcileContract 4** / DomainSplit 7 / DriverActionIdempotencyContract 6 / ReachabilityContract 17 / RepoTest 11 / RouteGuideContract 12 / DriverState 11 / LocationReportPolicy 15 / DriverLegStateMapper 11 / MissionMap 18 / LoginViewModel 6 / ProductOrder 7 / SendOrder 8`（加总 **299**，与 XML 逐份计数一致；本轮 29 份 XML，表里 29 行）。**E-1 的 +4 逐条可归因**：`BusEtaContract` **12→13**（别名键那条）、`SendEtaContract` **9→12**（订单/段、轨迹、路线引导各一条），其余 **27** 类逐份计数一字未变（`NavigationContract` 19 / `ShellRoutingContract` 10 / `RoleReconcile` 5 / `WorkbenchRoleReconcileContract` 4 / `DomainSplit` 7 逐份复核过 —— ⚠ **这条不是回忆，是机器比对**：把 29 份 XML 的逐类计数与 §78 那轮的逐类表（295 那份）对着跑，**全表只有两行不同**（`BusEtaContractTest` 12→13、`SendEtaContractTest` 9→12），29 个类名两侧**一一对上、无孤儿**）。**N-1 的 +19 逐条可归因**：新增簿 `NavigationContractTest` **19**，其余 28 类**一条都没变**（`DomainSplitTest` 7 / `ShellRoutingContractTest` 10 / `RoleReconcileTest` 5 / `WorkbenchRoleReconcileContractTest` 4 逐份复核）。**+14 逐条可归因**：新簿 7 + 6，加 `ShellRoutingContract` **9→10**（V-3 注册新路由时同轮加的那条"司机域路由必须无参数"）。 **+9 同样逐条可归因（V-2b）**：`RoleReconcileTest` **5** + `WorkbenchRoleReconcileContractTest` **4** = 9， `DomainSplitTest`（**7**）与 `ShellRoutingContractTest`（**10**）**条数未变** —— 前者本轮只**改写**了一条用例 （`档案接口没有内容时保留既有司机身份` 的前提 `data:null = 未知` 被后端源码证伪，改成 HTTP 500 那条，**没删、没减量**）。 连续四轮"没有无法归因的余数"（⑫ 那次差 2 条已永久记在案，不许回改历史数字）。**本轮改了生产写路径（异常上报走 `Repo.legAction`），按 V-1 那条硬规矩跑了全量**：没有跨类污染，`ActionGate`/`Idempotency` 两类仍各自 5/6 全绿。**新机制必须跑全量；引入跨用例存活的进程状态，必须同轮给测试侧复位**（`TestHost.resetActionGate()` 仍在册）。计数只从 `app/build/test-results/testDebugUnitTest/*.xml` 逐份读、自己加总，不抄控制台摘要。（上一轮那份逐类表被本轮覆盖 —— 是**同一条抄数项换到新一轮的值**，不是删证据。）**新鲜度只认 gradle 报的 `executed` 与内容哈希**：收工那遍 `testDebugUnitTest + assembleDebug + lintDebug` **BUILD SUCCESSFUL / exit 0**，但打包与 lint 是 **53 actionable tasks: 1 executed / 1 from cache / 51 up-to-date** ⇒ **没有重新打包**：盘上 APK 仍是本轮 22:33:05 那份（**57,941,374 B**，与 V-1/V-2 末次读数**同字节**），lint 三份产物仍是 22:34 那批（`lint-results-debug.xml` 98,753 B = **46 Warning + 32 Information = 78 / 0 error**）。照实记 UP-TO-DATE / FROM-CACHE，**不写成"重新构建通过"**；**V-2b 复跑（2026-09-30 00:0x）两个都真重跑了**： `:app:assembleDebug` **37 actionable tasks: 3 executed**（`mergeProjectDexDebug` + `packageDebug` + `assembleDebug`） ⇒ APK mtime **23:59:33** / **57,941,374 B**（与上面几轮**同字节**，但**同字节只说明体积没变**，重跑与否只认 `executed`）； `:app:lintDebug` **28 actionable tasks: 8 executed** ⇒ 三份产物 mtime **00:01:16**，XML 解析 **0 error / 46 Warning / 32 Information = 78**，**本轮改动的五个文件（`WorkbenchScreen.kt` / `ShellDecision.kt` / `Repo.kt` / `RoleReconcileTest.kt` / `WorkbenchRoleReconcileContractTest.kt`）在 lint XML 的 `location/@file` 里 0 点名**； ⚠ `:app:assembleRelease` **exit 1** —— 被工程自带的 HTTPS 闸门挡在**配置期**（`app/build.gradle.kts:171`）， **没有加 `-Pbuspost.allowInsecureRelease=true` 绕过**，见 BLOCKED 新增那条；⚠ `from cache` 与 `up-to-date` 不同：前者是输出从构建缓存按输入内容哈希取回、后者是任务跳过，两者都**只说明"与已验证输入一致"**，不说明"这一遍重跑过"。符号级复核（口径必须写清）：**APK 里 14 个 dex 全扫，按类型描述符 `Lcom/shanxiang/…` 数** → `DriverExceptionScreen` **27**（classes3 26 + classes5 1）/ `DriverExceptionReport` **2** / `LegDriverAction` **5** / `DriverLegStateMapper` **4** / `ShellDecision` **3** / 旧符号 `deepLinkAllowed` **0**。⚠ **同一份 APK 换"裸类名"口径扫，`DriverExceptionScreen` 是 128**（多出来的全是常量池里的方法名与字符串）——**两个口径不可互相引用**，历史行里的 3/27 都是描述符口径。（历史：⑫ 那次 `cleanTest testDebugUnitTest` 仍报 UP-TO-DATE、`--rerun` 才红；⑬ 21 份 XML 17:58:22；V-1 23 份 20:00:21；V-2 24 份 20:53:59；**V-2b 28 份 23:56:47**。） **V-2b 符号级复核（本轮那份真重打包的 APK，14 个 dex 全扫，两个口径分开写）**：类型描述符口径 `Lcom/shanxiang/buspost/nav/ShellDecisionKt;` = **3**（classes3 / classes5 / classes9）；**裸名口径**（不可与上面互引） `serverSaysDriver` **2**、`reconcileRole` **2**、`demoted` **1**（classes3，即 `WorkbenchViewModel` 所在那份）、 `WorkbenchViewModel` **1**、`DriverUnboundException` **1**（classes10）。⚠ **本轮又演示了一次"两个口径不可互引"**： `ShellDecisionKt` 裸名是 **3**（与描述符同数，因为它是纯文件级 facade），而 `serverSaysDriver` 是 **2**（只在调用它的那两份里）， 拿 3 去对比历史的 2 会得出"符号变多了"的错误结论 |

---

## DONE（已验证，非自述）
### 赛道⑭（N-6）把"缓存会过期"从一句主张收成三种类型：**读点先出现**、**过期那一格结构上就交不出几何**、**后端那句原文不许塌成枚举**（指令 v2.1 §十三 ＋ V21-13 升格 / 硬规则"宁可没有路线，不允许假路线""不造第二条真相、不扩 UI、无设备不判运行时" / 判据 §八 ㊳ / D-059，本轮完成 · 2026-10-06）

**本轮买的东西一句话**：F-3 ⑰ 曾用"这缓存没人读"挡掉 **TTL／线程安全／过期不交几何／来源与抓取时间** 那四件，那句话**当时是对的**——而本轮第一个真实读点出现（`N-x` 由 **0/1** 变 **1/1**），四件事就同时不再能挡；仍然挡得住的只剩两件（**图版本**与**第二份存储**），因为后端 VO、小程序、演示面三面逐字段读下来 **0 生产者**。

- **契约先行（逐字段重读，不引用上一轮台账）**：`AppDriverRouteRespVO` **没有** `graphVersion`、**没有** origin/waypoints/destination/profile 那组指纹入参、**没有任何新鲜度／有效期键** ⇒ 指令点名的两件里只有"provider ＋ 抓取时间"有可信生产者。⚠ 于是这一格的交付形状是"**四件一起做、两件仍不做**"，而不是"缓存收口了"。
- **"不允许返回过期路线"落在类型上才算做到**：`RouteCacheRead = Hit(entry, ageMillis) / Absent / Stale(providerRaw, fetchedAtMillis, ageMillis)`，⚠ **`Stale` 故意不带几何字段**（`N-ab 过期那一格的类型数/格内带几何 = 1/0`）⇒ 调用方**拿不到**，不是"拿到但被要求别用"。同一招式第三次落地（⑨"表外只能 `null`"、⑩"四个'没有'是四个类型"）；约定式形状（回 `null` 让调用方自觉）在这一族已被证伪过两次。
- **同一个数据位可以有两个形状，选哪个取决于要说什么话**：后端 `routeProvider` 给的是**原文**（实测样例 `"gaode"`），`RouteSource` 是**归一枚举**（"gaode" / "AMAP" / "高德" 都塌成 `AMAP`）——判定"是不是真实道路"要枚举、回执"后端当时说了什么"要原文，**塌成枚举就是替后端补了它没说过的话** ⇒ 缓存条目里 `route.source` 与 `providerRaw` 并排（`N-ab 来源原文照抄/改写成本机枚举 = 1/0`）。
- **TTL 与边界**：`MAX_AGE_MILLIS` = 30 分钟，KDoc 明写**这个数值没有外部权威来源**（后端不发新鲜度键）⇒ 是本机自定口径，台账与用例**都不许**写成"路网数据的有效期"；边界"当前时间 ≥ 过期时间即过期"，**相等不给情面**（指令原话是"当前时间 < `expiresAt` 才算新鲜"）。
- **时钟入参不是为了省一次取时，是为了让边界可测**：抓取时间只来自**写入那一次的** `nowMillis`（`N-ab ... 现场取时 = 0`），`cached(key, nowMillis)` 同样收时钟入参——B-5 那条注入（改成现场取时）之后用例**立刻红**，这就是"30 分钟"第一次进得了可测范围的直接证据。凡"新鲜度"类判据，先看时钟是从哪一刻取的：**现场取时的实现永远测不出过期**。
- **否决先于写入（把 `D-⑱-3` 那个潜在缺口关掉）**：`resolve` 收 `serverAvailable` 入参，写不写在同一个决定里出；**三种不过**（来源不可信／几何不过／后端自己说这一班没有真实道路）都删旧条目，否则"刷新过了"会被读成"没刷新也一样"。Repo 侧"取 `serverAvailable` 必须排在 `resolve` 之前"是**行号比出来的先后顺序**判据（`N-ab 否决先于写入 = 1/1/1`）——只判"两个语句都在"就是没判。
- **换容器与换淘汰依据是同一件事的两半**：指令给两个优先项，本轮按 `ConcurrentHashMap` ＋ `AtomicLong` 写入序号兑现（不选 `Mutex` 的理由进 KDoc：一次缓存读会变成挂起函数、逼调用链一起挂起）⇒ 换了容器就同时失去插入序，"最旧"**必须**有显式定义，所以淘汰改成 `minByOrNull { sequence }`、两处删除改成两参 `remove(key, entry)`（只删自己刚读到的那一份）——**同轮**改完，不是分两步。旧结论"无 TTL、非线程安全"本轮作废。
- **一把读出口 ＋ 一处披露**：`cached()` 是唯一读取入口（`N-ab 读出口一把/已删的旧出口 = 1/0`；旧写法那三把里的"无状态 `Date`"与"整表快照"本轮**没建**，它们会把"过期了"与"本机压根没有"并成同一个 `null`）；披露唯一出口 `routeFailureTextOf` 三格各一句、**不供几何**（`1/0/3/0`），`Absent` 那格**原样返回失败原因**（缓存空着却要说"本机存过一条……"就是编造），时长口径三档 ＋ 负数先夹 0；且**不抄** F-3 ⑧ 那句"这里不画任何示意线"（无路线话术的唯一出口在 `MissionMarkers`，抄过来就是同一句话两个生产者）。
- **用例 0 → 7（新建 `RouteOfflineCacheContractTest`）＋ 旧本 16 → 18**：新簿七例各钉一种坏事（原文照抄 / 空缓存不编 / 过期只留记录不留几何且读到即删 / 抓取时间来自时钟入参 / 演示面来源从未进过缓存 / 并发不越界不留坏条目 / 时长三档不炸）；旧本新写 2 条（后端自己说没有时既不写入也删旧、过期条目读不到几何且当场被删），另 2 条是**重锚**到新类型上——**重锚不算新增**，条数账只算 2（全量 510 → **519** = ＋7 新簿 ＋2 旧本，两个来源独立对上 `SU-c` 现读的 519）。
- **判据 NAB1…NAB24（9 行打印，终值逐行 = 目标）＋ 行为 B-1…B-10 一对一**：行为那本 **270 行 / 16,790 B / CR=0**，基线双绿 → 预检 **10/10**（锚点与具名用例各恰好命中 1 次，不过就 exit 5 且一条不注入）→ **md5 变了才跑** → 红的必须含具名那一条 → `grep '^e: '` 先排除编译红 → `cmp` 复原才取下一条 → 末尾恢复基线＋结构判据复跑；单跑（08:35:09）与批内（10:13:17 → 10:18:52）**同一组读数 `10 ✔ / 0 ✘ / 5 △ / 5 ◦`**。⚠ **5 △ 就是本轮的覆盖面账**：披露层措辞类破口（三格并句、过期格念出来源原文）在结构半边**看不见** ⇒ 登记 `D-N6-2`，待修要给那两条判据补反证口径并**同轮抬负控**，不许只改判据不改正证。
- ⚠ **本轮三笔自己付的账，前两笔都是量具而不是代码**：① 链上 `[10/10]` 那句"指纹与补跑前不同串"是**我的比较器坏了**——`>` 与 `>>` 两种重定向在 Windows 文本模式下写出 LF 与 CRLF 两份文件，`tr -d '\r'` 之后**逐字同串**（`TREE files=166 sha256=3c338019…` 两遍同串），结论"补跑没动过真身"是**修好量具之后**才成立的；⚠ 假警报比没有警报更贵，它会在台账里留下一句"仓库变了"的假话。② **census 的 `code=` 把单行 `/** … */` 算成生效行**：它给 `LinkedHashMap code=1`，判据簿给生效行 **0**，真相是两处都在注释里（过滤器只滤 `*` 与 `//` 开头）⇒ 本轮**没改 census**（一改就要同轮重跑依赖它的全部台账行），改为另立一把更严的注释过滤器、**两个数同时抄并标明谁偏高**。③ **同字节数 ≠ 同内容**：上一窗口笔记里两处 sha16 与现读**不同**而行数与字节数**完全相同**（"改到位之后长度恰好没变"）⇒ **台账只写现读，笔记那两个值不采信、也不并列成"两个来源"**。
- ⚠ **第五笔账是账本自己的**：起手把本轮四件写成 `D-⑭-2`…`D-⑭-5`，与 **F-3 的第 ⑭ 项**（D-053 的 `D-⑭-1`）撞成同族——两套编号轴各有一个"⑭"（F-3 的项序 vs 路线图赛道序），而 `D-⑭-1` 讲的正是**同一个缓存**的 TTL 半边。处置＝**同轮自纠、只动本轮自己写的字**：改 `D-N6-1`…`D-N6-5` 共 **12** 处（本文件 7 ＋ DECISION_LOG 4 ＋ 核对表 1），逐串计数对齐后才落盘；`D-⑭-1` 的登记格**一字未动**，其被顶替的半边以 〔〕 注记补在原行末（**原句不回改**）。命名冲突本身登记为 `D-N6-5`。
- **三态**：**SOURCE_COMPLETE**（缓存读点 1／写点 1、TTL 与"相等即过期"、过期格结构上无几何且读到即删、抓取时间来自写入那一次的时钟入参、来源原文照抄、否决先于写入含行序、序号淘汰 ＋ `MAX_ENTRIES`、披露唯一出口三格各一句且不供几何、用例 ＋7 ＋2、判据 9 行打印全等于目标）＋ **BUILD_VERIFIED**（debug variant **49 类 / 519 例 / 0 failures / 0 errors / 0 skipped**，49 份 XML mtime 集合唯一；lint **error 0**、78 条 = 46 Warning ＋ 32 Information；23 本判据 rc=0；census rc=0；APK **113,928,596 B** / sha256 `3bfa396a…`，对 D-058 留档包 **Δ = +0 而 sha256 变**＝连续第 **5** 个未归因读数，`#140` 不闭环；dex defined **1,378 → 1,383**、**入 6 / 出 1**，出榜那条正是被三态顶掉的旧 `CachedRoute` 格）＋ **DEVICE_VERIFIED 未到位**（`verify_device.sh` **rc=1 / DEVICE_UNAVAILABLE**，证据 `docs/device-evidence/20261006-105228/`）⇒ 披露句在真机窄屏的排版、进程被杀重建后"还剩几条"、30 分钟这个本机自定口径够不够真实路网更新频率、`serverAvailable` 在真后端 `routeAvailable=false` 时的实际占比**四格全 `NOT VERIFIED ON DEVICE``。⚠ **release 半边照旧**：只跑 debug，`D-⑱-1` 那 **5** 例连续第四轮沿用旧读数；`assembleRelease` **rc=1**（HTTPS 闸门原话逐字引在 D-059 §5，**未加** `-Pbuspost.allowInsecureRelease=true`）——两件事不许并成一句"release 也好了"。V21-13 **❌ → ⚠**（不给 ✅：图版本没做、几何兜底与设备证据都没有）、V21-14 **仍 ❌**（旧理由失效、换 `D-N6-1` 这一条）、V21-15 mock 那一半照旧 ❌（`#245`）。

### 赛道⑫⑬（N-5）把"3D 立体感"收成"谁有权决定怎么画"：朝向没给不许变正北、旋转只给本机那一格、`GL3DModel` 有类无资产所以不做（指令 v2.1 §八 的 ⑫⑬ / 硬规则"不造第二条真相、不扩 UI、无设备不判运行时" / 判据 §八 ㊲ / D-058，本轮完成 · 2026-10-05 → 10-06）

**本轮买的东西一句话**：这一族里能被本机证据钉住的只有三件事——**形状由谁产出、角度从哪来、层级谁压谁**；"看起来立体不立体"在 0 台设备在环的机器上一句都证不出来。而唯一一处真正在撒谎的地方是**把"没有朝向"画成 0°（正北）**：速度可以拿 0 当"没给"（静止真的会 0），朝向不行 —— `hasBearing()` 为 false 时任何旋转都是编的。

- **取证只从这颗 jar 的签名与资产在场性来（同一颗 navi-3dmap 10.0.600 = 50,991,398 B / sha256 `9fa3cfb81ff7…` / 2,403 条目 / 1,855 class，本轮没换依赖）**：动用五个 ＋ 两个 —— `perspective(boolean)` / `setFlat(boolean)` / `zIndex(float)` / `rotateAngle(float)` / `altitude(float)`（⚠ 后三个是 **float**，所以视觉层的 `zIndex` / `altitudeMeters` 定成 `Float`）＋ `CameraUpdateFactory.changeTilt(float)` 与 `UiSettings.setTiltGesturesEnabled(boolean)`；`GL3DModel` / `GLTFOverlayOptions` **类在（条目名命中 2；连 `GLTFOverlay` 一族并数 = 5）而资产不在**：条目名里 `.obj` / `.glb` / `.gltf` **0**，把 **1,855** 个 `.class` 的原始字节逐个读出来找 `.obj` / `.glb` / `.gltf` / `.fbx` 仍是 **0**（两套口径都要跑 —— 只报"名字里没有"的话，"名字里没有"与"代码里不认"是两件事）。
- **朝向的两种"没有"被拆成两格**：`rotateOf(null) = null`（不是 0f）、`headingText(null)` 明说"朝向未给（定位源没有给出 bearing）"；上游 `Locator.heading` 同轮由 `Double … else 0.0` 改成 **`Double?` / else `null`**（现读第 **228** 行）—— 两端各钉一条、中间不兜底（`MR-o` 钉整行 = **1/1/1** ＋ 旧形状 `bearing.toDouble() else 0.0` 全树 **0**、反证含注释 **1**）。
- **旋转与浮空的权利都只给有生产者的那一格**：`rotateAngle = if (kind == KIND_ME) rotateOf(…) else null`（用例 6/7、MV-6/MV-7）；`altitudeMeters = if (isTask) 8f else 0f`（只有任务点浮空，起终点/车辆贴地）；层级 普通 2 / 下一任务 5 / 当前任务 9 / 车辆 12 / 我 15，**定位降级时"我"沉到 1**（不冒充在线车辆）。
- **新鲜度不新建第二个时钟、脚注不重写服务原话**：`gpsFixStateOf` 用的就是 `LocationReportPolicy.MAX_FIX_AGE_MS`（现读 **45,000 ms**），视觉层自造毫秒数 **0**（`MR-q` = **1/1/1/1**）；`footnoteOf(LIVE, …)` 返回**空串**、`STALE` 拼的是那句等待原因的**原话**、`UNAVAILABLE` 无原因时才用本层兜底句且只说"本机没拿到"（用例 10/11/12/5）。
- **视觉层不许碰业务**：`TaskState` / `OrderState` / `LegState` 三个名字在 `Map3DVisual` / `BusMap` 生效行 **0**（对照组 `DriverLegStateMapper` 那本 **7** 条，`MR-r = 0/0/7`）；`(Running|Repo).… =` 的写点 **0**（反证服务侧 **23**，`MR-r = 0/23/16`）；`import …data.model|core.` 只 **1** 个（那个新鲜度阈值，`MR-r` 钉业务模型 import **0**／BusMap＋出口对照 **10**）。用例 15 钉的是"高亮顺手把 `kind` 改成进行中"那一刻——视觉层替后端推进业务状态。
- **用例 0 → 16（新建 `Map3DVisualContractTest`）、结构注入 22 条 ＋ 行为注入 16 条一对一**：类数 **47 → 48**（终值 48 类 / 510 例）；结构那本判"写法有没有被人换掉"（MR-l…MR-r，全簿 27 → **49**），新写的 `tools/map3d_visual_negative_controls.sh` 判"那 16 条用例是不是装饰"（只有 gradle 真跑才成立）—— 结构那本批内 **rc=0 / ✔=49 / ✘=0 / △=0 / ◦=0**（703s，末行"[恢复后] 任务地图无路线态判据通过"）；行为那本**批内** rc=0 / 16 ✔ / ✘=0 / △=13 / ◦=3（435s）＋ **紧贴终值**（`[0/8]`）同一组读数、`RESULT=PASS @00:01:34`。
- ⚠ **三处自己踩红，前两处是闸拦住了我、第三处是闸没拦住我**：① KDoc 里写了 `Running.waiting` ⇒ 分域闸 `P-a2` **0 变 1**（**改源码措辞**，不给探针加"排除注释"的过滤）；② 新增那本用例文件 ⇒ `SU-b` 的"恰好 **49**"红 ⇒ 同轮抬到 **50** 并写下归因（下限 `≥47` / `≥476` 一字未抬）；③ **本圈那本行为负控首跑 `RESULT=FAIL`（13 ✔ / 3 ✘：MV-2 / MV-13 / MV-15）**——源码一个字都没坏，坏在三条断言上：`contains("0°")` 被注入后的整句 `"朝向 360°（GPS）"` 用**子串**混过去；`status=(7,2,0,8)` 那组夹具让"排除已完成/失败"这条规则**注入前后指向同一站**（倒是把邻居用例 14 打红了）；`status=2` 那一站本来就 `KIND_TASK_ACTIVE`，把 `kind` 改色**看不出来**。⇒ 只改测试（整句比对 / 夹具换成 `(2,7,8,0)` / 补一段"焦点落在未开始那站 = CURRENT"），**16 条数、用例名、全部注入锚点未动，两个源文件指纹复量一致**，单类 16 tests / 0 failures，随后整批负控＋终值链**重跑**（旧批不充当终值）。**这三条 ✘ 是本轮最有价值的产出：它们证伪了我写在用例表里的三句"这条钉这种坏事"。**
- ⚠ **一笔闸门叫不应的漂移 ＋ "撤锚而非补锚"**（新增在册项 `D-⑫⑬-6`）：V21-15 那格挂的那个旧行号锚（指 `Locator.kt` 第 217 行）与本轮之后的真身（第 **228** 行）差 **11** 行且指到了另一个函数（`accuracyOf`），`check_doc_anchors.py` 因为那格 `tokens=[]` 而**不做 ID 校验**、全绿不叫。本轮**撤掉**那个行号锚（位置交给 `MR-o` 的整行字面量），**没有**补一个新锚 —— 补锚会把同一次漂移再演一遍，而新 aid 在 `--pin` 口径下正是要被当成可疑的那一类。⚠ 同轮走查 `Locator.kt:` 八处 = **对得上 1 / 漂了 7**，其中 `RUNCATCHING_AUDIT.md` 那 6 处**整本不在探针名单里**（详见状态块那条与 D-058 §6）。
- ⚠ **本圈新写的那本行为负控，判读口径三条**：**红的必须含具名那一条**（多红不扣分，"红了但不是那条"记 ✘）；**先排除编译红**（`grep '^e: '`）；**注入把结构判据打红一律记 ◦ 不记 ✔**（红着的判据不能反过来证明它有判别力）。⚠ 计数口径本轮变了：本簿落盘在批次重跑**之前** ⇒ 它**在批内**（共 **26** 本，批次入口展开 glob），不再适用 D-052 那句"晚于批次落盘就不算"；链上第 0 步紧贴终值还会再单跑一次。
- **三态**：**SOURCE_COMPLETE** ＋ **BUILD_VERIFIED**（debug variant **48 类 / 510 例 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 46 warnings / 32 information（`<issue>` 78）**；APK **113,928,596 B / sha256 `96c7849c6da30d7e…`**，对留档上轮包 **Δ −17,304 B**；类清单差集 入 **7**（逐条具名）/ 出 **0**）＋ **NOT VERIFIED ON DEVICE**（35° 观感 / 8 m 浮空 / marker 动画 / 脚注排版）＋ **BLOCKED**（release HTTPS 闸门、`GIT=ENVIRONMENT_BLOCKED`、`#140`）。整体 **`ANDROID_N5_⑫⑬_PARTIAL`**，不写百分比。

### 赛道⑪（N-4）把"高德说它在重算"与"高德说它算完了"接成两条互不相干的广播：配对在类型上写不出来、工程里唯一那处本机 60 米阈值同轮撤读（指令 v2.1 §八 的 ⑪ / 硬规则"只能由导航引擎触发，不得本地假判定替代后端" / 判据 §八 ㊱ / D-057，本轮完成 · 2026-10-05）

**本轮买的东西一句话**：`onReCalculateRouteForYaw()` 与 `onReCalculateRouteForTrafficJam()` **零入参**、算路成功 / 失败回执**既不带原因也没有关联键**，而 `AMapNavi` 是**进程单例**（别的调用方发起的算路也会走到同一条回执）⇒ 把一次回执配成"那次偏航的结果"是**编因果**，不是推断 —— 于是"重算"那一族在类型上只能是**五格各一句话**，而工程里那处"本机自己量 60 米垂距判偏航"在同轮被撤读。

- **取证只从这颗 jar 的签名来（同一颗 navi-3dmap 10.0.600，本轮没换依赖）**：两条重算广播在 `AMapNaviListener` 里是 `public abstract void`、**零入参**（偏了多少米、往哪偏、拥堵在哪一段，一个字都没给）；  `AMapNaviRouteNotifyDataType` 七常量全量 = `AVOID_RESTRICT_AREA 1 / FORBIDDEN_AREA 2 / ROAD_CLOSED_AREA 3 / AVOID_JAM_AREA 4 / DISPATCH 5 / CHANGE_MAIN_ROUTE 20 / GPS_SIGNAL_WEAK 21`，  ⚠ **值域里没有"偏航"这一类**（`6..19` 也不存在）⇒ `onNaviRouteNotify` 本轮**没接**；`ReCalculateRouteType` 那 14 个常量是 `reCalculateRoute(int)` 的**入参**值域**不是回调输出**，而本机一次都不主动重算（调用点实测 **0**）；  留档 `/tmp/n7_recalc_javap.txt` 现读 **82** 行 / **3,894 B** / CR=55（混合行尾 ⇒ `grep -c` 这类量具在这份文件上不可信）。
- **五格各一句话、谁都不替对方说话**：`EngineRecalcDisplay = Yaw / TrafficJam / EngineInitiatedSuccess(code) / EngineInitiatedFailure(code)` ＋ flow 为 `null` 那一格 —— `null` 那句顺带声明"本机不拿距离卡阈值判偏航"，  两支回执各把高德给的 `errorCode` **原样**念出来并明说"这条回执不带原因"；⚠ 两个 `EngineInitiated*` 的类型里**没有** yaw / jam 的任何引用 ⇒ **配对在类型上就写不出来**（⑨ 表外只能 `null`、⑩ 四个"没有"是四个类型，这是同一招式的第三次落地）。
- **广播一到就作废路线凭证**：SDK 层两条广播各写一格并同处把已验的那条路线凭证置空 ⇒ `N-v` 的作废点由 D-056 的 **7** 抬到本轮现读 **9**（`1/1/1/1/9`）；  `pending == null` 那两支原来是裸 `return`（等于把高德主动做过的事吞掉），本轮改成"写一格再 return"，句子仍不判成偏航的结局；`stopNavi()` **清**这一帧（实测 **1** 处）而 `release()` **不**清 —— 作用域写在同一句、与 ⑩ 那条车道帧同口径但不同句话。
- **本地假判定撤读（队列里那句"不得本地假判定替代后端"在本机侧的兑现）**：DEBUG 旧屏 `offRoute` 那条 **60 米垂距阈值**撤掉，现读只剩 `roadOk && g.deviated`（第 **216** 行，偏航结论只由 `/driver/route` 下发的 `deviated` 决定）；  ⚠ 撤的是**消费点**不是工具 —— `crossTrackMeters()`（第 **124** 行）作为纯函数仍在；方向也要写清：不是"本机不再算距离"，是"**本机不算了去替高德下结论**"。
- **用例 45 → 52、结构注入 24 条全命中**：**新建测试类 0**（类数 **47** 不变、+7 全落在 `NavigationContractTest`），七例各钉一种坏事；行为负控 **M27–M33** 逐条从 gradle 现读红 **2/2/1/1/1/2/1**  （基线 `exit=0 failures=0/52` → 七次破口全红 → `cmp` 证复原逐字节相同 → 复跑 `exit=0 failures=0/52`）；判据新增 **N-aa 一组 7 行 / 24 个数**，其中"清帧 = 1"与"`reCalculateRoute` 调用点 = 0"两格是**只有它们会红**的那两类坏事。
- ⚠ **M31 差点是假绿**：注入锚点里含 `${display.code}`，写在 bash 双引号串里被当成变量展开 ⇒ **注入静默不发生、判据不判**。修法是换一条**不含 `$`** 的行前缀锚，并给脚本加一道 `cmp -s` 字节守卫（内容没变就 exit 5）—— 记进 §八 ㊱。
- ⚠ **第三次付"锚点账"，并新增一笔无闸漂移 `D-⑪-2`**：改 `core/navigation` 的文本 ⇒ 同轮核所有簿，账单 **1** 条新增 aid ＋ **12** 行走查账 ＋ **3** 处台账指针 ＋ **1** 次批次不成立；复位后 `OK 937→938`、`ID-MISS 287→286`（回到闸内）。  而 `RUNCATCHING_AUDIT.md` 第三节那张逐点账**不在 `check_doc_anchors.py` 的七本扫描名单**里、`check_runcatching_probes.sh` 对它**只数行数** ⇒ 那 12 个行号漂了**没有任何一版闸门会叫**，本轮是**人工**逐条复位的  （"判据覆盖不到的地方，账面会安静地变脏"的第四个实例）。
- ⚠ **本轮新增在册项 `D-⑪-3`（第五笔"没有闸门会叫的漂移"，与 `D-⑪-2` 同族但更窄）**：`docs/android/` 那几本根本不在 `check_ledger_tables.py` 的名单里（名单只有仓库根五本），本轮往 V21-10 那一行抄量具输出原文时，把 `GUARD|pipeline|measured=1|expect=1` 的三个裸竖线带进了单元格 ⇒ 那一行的分隔符由 7 变 10、现读 cells=11；而五本聚合 `TOTAL FLAGS 0` 当时是**真的 0** —— 不是检查器坏了，是它没看这一本。改法按该本在册惯例把三处转义（回到 7 / cells=8，状态格**仍 ⚠**、右边结论格一字未改、净 +3 B），并对该本**单独再跑一遍**检查器取 flags=0；"扩名单"与"单元格内未转义竖线即红"两条修法本轮都没做（任一都要同轮抬一次负控，不能只改判据不改正证）⇒ 登记为在册不当已解决，见 D-057 §8、§9、§10 ③。
- **三态**：**SOURCE_COMPLETE** ＋ **BUILD_VERIFIED**（debug variant **47 类 / 494 例 / 0 failures / 0 errors / 0 skipped**；lint **0 errors, 46 warnings**、`<issue>` 78；APK **113,945,900 B / sha256 `3cf3c1ed…`**，对留存的上轮包 **+33,688 B 未归因**；  类清单差集**出 0 / 入 5** 且五条全部具名；**23 本判据簿逐本 rc=0** ＋ `TOTAL FLAGS 0` ＋ 负控 **"23 本批内绿 ＋ 2 本单本复跑绿"**）／**DEVICE_VERIFIED 未到位**（rc=1 / DEVICE_UNAVAILABLE ⇒ `ANDROID_N4_⑪_PARTIAL` ＋ `NOT VERIFIED ON DEVICE`，不写百分比）／  **RELEASE 与 GIT 仍 BLOCKED**（HTTPS 闸未绕、`GIT=ENVIRONMENT_BLOCKED`）。在册由 D-056 的 **11 项 ＋ 3 格** 涨到 **14 项 ＋ 3 格**（新增 `D-⑪-1`、`D-⑪-2`、`D-⑪-3`），**没有清零、没有百分比**。
- ⚠ **台账写完后端面又扫出两条实测，改的都是判据口径不是源码**（已回写 `CONTEXT_LOCK.md` ㊱ ⑭⑮ ＋ §九 那 **6** 行；DL §10 里"§九 **4** 行"同轮更正为 **6**）：① 全工程现读 **54** 本 `.md`（排除 build 目录；再排除 `backup/` 与 `.gradle/` 是 **40** 本）里 **22** 本含 CR 字节，而**这 22 本的"孤立 CR"（CR 减 CRLF）之和 = 0**、CRLF 合计 **1,639** —— `docs/android/ANDROID_COMMAND_V2.1.md` 整本 **1,618** 行全 CRLF、**20** 本 device-evidence `summary.md` 各 1 处、`THIRD_PARTY_NOTICES.md` 1 处 ⇒ 前几轮"那一处是孤立 CR"的定性**作废**（历史原句按纪律不回改，只在 ㊱ ⑭ 加限定），根因是文本模式写 `open(...,'w',encoding='utf-8')` **忽略** `newline=''` 而自行做行尾翻译；② 把 `check_ledger_tables.py` 显式指向 `docs/android/RUNCATCHING_AUDIT.md`（18,725 B / **153** 行）⇒ **rc=1 / flags=1**（第 **73** 行那条 HTML 注释被判 SPLIT ROW），对 **2026-10-01** 旧副本（**144** 行 / 16,730 B）跑同一条**同样 flags=1**（第 **72** 行）⇒ `D-⑪-3` 的"扩名单"修法**当场有实测反例**（那红早于本轮），本轮**两条待修都没做**。写后终检（在追加那 6 行**之后**现读，不是沿用追加前那一遍）：`check_doc_anchor_probes.sh` **rc=0**（records=1239 / 在册=262 / **新增=0 / 消失=0** / ID-MISS=286 / 反证 580 与 279）、五本聚合 **TOTAL FLAGS 0**（`CONTEXT_LOCK.md 行数=1624 flags=0`）、`docs/android/ANDROID_V2.1_CHECKLIST.md` **单独**跑 **flags=0**、`check_navigation_probes.sh` **rc=0**（N-aa 7 行 / 24 个数全中）。
### 赛道⑩（N-3）把高德的车道帧接成人话：把"没有车道"拆成四件事、把"按下标配对两个数组"做成类型上写不出来的形状（指令 v2.1 §八 的 ⑩ / 硬规则"不造第二条真相、不扩 UI、无设备不判运行时" / 判据 §八 ㉟ / D-056，本轮完成 · 2026-10-05）

**本轮买的东西一句话**：屏幕上那句"直行车道"只能来自**逐条对过 javap 常量清单**的那张表，而**"本机没收到车道" "高德说这没有车道" "收到多组但本机不替你挑" "收到单组"从这一轮起是四个不同的类型** ——
尤其：**没有任何证据说高德那两个数组按下标对应，所以代码就不许配对**（长度不一致就明说不一致，一致也只说"对齐了"，不说"哪条道推荐"）。

- **取证只从 jar 的常量清单来**：`LaneAction` **27** 个 `public static final int` = `0..25` ＋ `LANE_ACTION_NULL = 255`（⚠ **不是 0..26**：`26..254` 在 navi-3dmap 10.0.600 里根本不存在，而 **255 正是"这一格没有车道"这个语义本身**）、
  车道**话术表 22** 条 = 比名字表**少 5 条**（`13/14/15/17/20` 那五个码高德给了名字但本机解释不了，表里留 `null` 由页面明说"解释不了"）、`showLaneInfo` **2** 个重载 ＋ `hideLaneInfo` **1**、`AMapNaviView` 车道方法数 **0**（内置视图那条路不经过我们这一层 ⇒ 没接，接了就是给"页面造车道帧"开第二条口）。
  ⇒ **"把两张表补齐成一样长"能编译、也能过话术用例**，只在"话术表恰好 22 条"这一层才露出来 —— N-z 把 **27 与 22 分开钉**、M21 就是那次硬翻（把 `LEFT_IN_AHEAD` 本机翻成人话）。
- **四个"没有"是四个类型，不是一句 `null` 加注释**：`LaneDisplay = Single / Groups(n) / Cleared / (flow 为 null)`；`Cleared` **只由 `hideLaneInfo` 写**（写入点 4 处被钉死，多一处就是有人在本机造车道帧）；
  `stopNavi()` 清帧 **1** 处而 `release()` **不**清 —— 作用域写在同一句里。三层形状：判据层 **707** 行 / 33,859 B（两张表 + `laneCellLabel` + `laneDisplayTextOf` 共 **1** 个话术出口）· SDK 层 **414** 行（只搬运）· 页面 **343** 行（只订阅，开关关着时**那一格不出现**而不是显示成"高德说没有"）；新类型 **5** 个（`LaneFrame` ＋ `LaneDisplay` 三个 case）。
- **UI 未扩**：正式导航卡片多 **1** 格读数（车道），落在既有那段"高德实时回执（最近一帧）"里 ⇒ 无新按钮、无新屏、无新路由。⚠ 这条不是表态：N-z 那 **18** 条结构注入里"页面不再订阅 lane flow""正式面自己造帧""旧屏那台**按名字猜图形**的 `laneIcon` 进了正式面"三条，就是把这一格打回 0 来验的。
- **用例**：`NavigationContractTest` 37→**45**（八例各钉一种坏事）；行为负控 **M19…M26** 红 **1/1/1/1/1/2/1/1** 例（M24 一次注入打破两条断言所以是 **2**，从 gradle 输出现读，不是"每条注入大约红一例"的心算），
  八条跑完 `✔ 恢复核对：与改前基线逐字节相同` ＋ `[恢复后复跑] exit=0 failures=0/45`。⚠ 本轮**没有新增测试类**（类数 **47** 不变），+8 完全落在这一本。
- **包级证据（差集做得成，且增量逐条指到名字）**：16 dex / defined **29,835 → 29,842（+7）**；**方法学验证在前** —— 拿留存的上轮包重导 = **1,359 条 / md5 `849f8708fecd93a6c8c7c8b4532bc186`**（与 D-055 §5 记的两级读数**逐字同串**）⇒ 提取器有资格报差集；
  新包 **1,366 条 / md5 `30c488351f942f5bdd7edba309872c96`**，差集 **出=0 / 入=7**、七条**全部具名**（五个新类型 ＋ `laneDisplayTextOf` 里两处 `crossinline` selector 被 D8 脱糖成的两个壳）⇒ **本轮没有新增"未归因"**。
  ⚠ APK 对上一包 **+0 B 而 sha256 变了**（上一轮是 +16,384 B）：这是"对齐量子"那条假设的**第二个读数**，本轮仍没做差量实验 ⇒ 体积继续**未归因**、`#140` 不闭环；**本轮新加的机制是打完包立刻留档那一份包**，下一轮的差集不再依赖"上一版包没被覆盖"这个运气。
- **本轮自己付的第二笔"锚点账"**：`RUNCATCHING_AUDIT.md` 的 **9** 行行号 ＋ 括注 ＋ 簿子里 **4** 条字面串（与台账 **4+1** 处指针**同轮**重锚，两本行数 3,925 / 1,550 一字未增删）⇒ 终值 **DA-d 新增=0 消失=0 / DA-e 286**（等于闸值、未涨）。
  ⚠ **与 D-055 不同的一格**：`doc_anchor_negative_controls.sh` 本轮**在批内就 rc=0 / ✔=8**（上一遍它是 rc=2）—— 因为跨簿重锚做在批次**之前** ⇒ **"负控在前、终值在后"这个顺序本轮是被证明的，不是被声明的**。
  ⚠ **走查出的 12 条历史错位锚本轮故意没动**（页面 6 / SDK 4 / 判据 2）：改对它们会点亮 DA-d 的"消失即红"并需要同轮 `--pin-write` ⇒ 那是 **#159 的活**，混进本轮就变成"顺手把账面改平"。
- **一次量具坏了的自纠**（写进教训，不写成"没发生"）：`grep -c` 配 CR 字符量行尾时对一份已知含 **3** 个 CR 的文件报 **0**、对判据层报 **707** —— 两个相反方向的错都出现 ⇒ **同一台机器上 grep 不是行尾计数工具**；改 python 字节计数重量，四个源文件 **CRLF 0 / 孤立 CR 0**。
  另有一处"34,547 B vs 终值 33,859 B 差 688 B"：三次树指纹同串（10:40 / 11:55 / 12:16）证明**变化发生在批次之前**、行数未变、判据串逐条现读都在 ⇒ 归因为某次 python 写入没带 `newline=''` 把混合行尾归一。**规矩不是"字节数错了没关系"，而是同一个量不许混用两个量具、跨量具出现的差必须先复现再落账**。
- **运行时那一半仍 `NOT VERIFIED ON DEVICE`**（0 台在环，证据 `docs/device-evidence/20261005-121526/`；本轮实际跑了两遍 12:14:57 / 12:15:26，终值只引用后一份）。**四条具体未知**：① 真机到底**先给哪个** `showLaneInfo` 重载；② `hideLaneInfo` 的时机与频次；
  ③ `LaneAction` 的**取值分布**（今天只有表、没有样本）；④ 那两个 `byte[]` 到底对应什么（`D-⑩-1`）⇒ 引擎层整本仍只由 grep 判据 ＋ 结构负控钉住。
- **只登记、不判平的一格新增**：`D-⑩-1`（数组语义与 `setLaneTypeId` / `isRecommended` / `getLaneTypeIdArray` 那一族**无证据、0 真机样本** ⇒ 代码不消费）⇒ 在册由 **10 项＋3 格** 变 **11 项＋3 格**（**BEFORE = AFTER － 1**，不写清零）。
  ⚠ 方向要说清：**猜错的代价是司机开错道，"少说一句"的代价只是信息不全** —— 所以这一轮买的是"禁得住"，而**"证得明"要等真机样本或官方口径**（§八 ㉟ ③）。
### 赛道⑨（N-2）高德的转向码与播报句子：把"表外编一个动作"和"替高德说话"做成类型上写不出来的形状（指令 v2.1 §八 的 ⑨ / 硬规则"不造第二条真相、不扩 UI、无设备不判运行时" / 判据 §八 ㉞ / D-055，本轮完成 · 2026-10-04 → 10-05）

**本轮买的东西一句话**：屏幕上那句"左转"只能来自**逐条对过 javap 常量清单**的那张表（表外只能印"高德给了一个不在这张表里的转向码（N）"），
那句"高德在播报"只能来自高德原句（本机没起 TTS、没合成过一句），而**"本机没让高德说"与"高德没给"从这一轮起是两格不同的话**。

- **取证只从 jar 的常量清单来**：`IconType` **57** 个 `public static final int` = `0..54` **+ 65/66**（⚠ **不是 0..56**，`55/56/57..64` 在 navi-3dmap 10.0.600 里根本不存在）、
  `NaviTTSType` **6** 个 = `1..6`（含 `NAVI_YAW = 6`）、`AMapNaviListener` 的 `onGetNavigationText` **两个**重载、`setUseInnerVoice(boolean)` 存在；
  同一份 javap 日志里 `public static final int` 共 **63** 行 = 57 ＋ 6，两个数各被 **N-y** 钉住。⇒ **"按连续区间补一张表"能编译、能过话术用例**，
  只在"值域逐条对 javap"这一层才露出来 —— M17 就是把**表外的 55** 当反例来验的，这一族今天有了**第一个可复跑证据**。
- **三层各只干一件事**（行数/字节本轮重量过）：判据层两张纯表 + `else -> null`（**525** 行 / 24,782 B）· SDK 层两个重载**都**转发（构造点 **2**）＋ `stopNavi` 清帧（**1**；留住最后一句 = 高德已经不说了而页面还在说）**365** 行 / 19,079 B · 页面只订阅 `engine.voice` 并查表 **334** 行 / 16,518 B；
  新类型只有 **1** 个 `NavigationVoice(typeCode: Int?, text: String)`（类型码可空 = 高德那个重载**本来就不带码**，不许把"没带码"读成某个具体类型），
  `startGpsNavi` 只多 **1** 个**无默认值、无重载**的 `voiceOn: Boolean` ⇒ 全工程只有这一个出口；替身引擎只回 `Blocked` 那一句、`voice` 常空 ⇒ **没有第二条话术**。
- **UI 未扩**：正式导航卡片多 **2** 格读数（转向 / 播报），都落在既有那段"高德实时回执（最近一帧）"里 ⇒ 无新按钮、无新屏、无新路由。
  ⚠ 这条不是表态：N-y 那 **13** 条注入里"页面不再订阅 voice（1→0）"与"页面不再查表（1→0）"两条，就是把这两格打回 0 来验的。
- **用例**：`NavigationContractTest` 34→**37**（新增三例各钉一种坏事：话术逐字 / 值域不缺不编 / 表外与没给都不并入别的类型）；
  逐类差 = 类数不变、无新增类也无消失类，`diff` **只有一行** ⇒ **+3 逐条归因、没有余数**；行为负控 **M16 红 2 例 / M17 红 1 例 / M18 红 1 例**（从 gradle 输出现读，不是"每条注入大约红一例"的心算），
  复原后 `exit=0 / failures=0/37` ＋ `cmp` 证"与改前基线逐字节相同"。
- **包级证据（旧包还在 ⇒ 差集做得成）**：16 dex / defined **29,834 → 29,835**；**方法学验证在前** —— 拿留存的上轮包重导 = **1,358 条 / md5 `19f4bf8e9493e7ba184a7b4c65c8e1ed`**，
  与 D-053 记的两级读数**逐字同串** ⇒ 提取器有资格报差集；新包 **1,359 条**，差集 **出=0 / 入=1**，那一条就是 `…core/navigation/NavigationVoice`
  ⇒ 这是 D-053 那句"没有新类型就不该有增量"的**反向用法**：**有**新类型时增量必须指得到名字，指不到就是计数与源码脱钩。
- **本轮自己付的一次"锚点账"**（"改了源码文本 ⇒ 同轮核所有簿的注入锚点"第一次由**我自己**买单）：批内 `DA-d 在册=262 新增=1`、`DA-e 287`（闸 ≤286），
  红的 aid 指着 SDK 层"启动前重读路径"那一支的第 **241** 行 —— 本轮给那层加了播报转发与清帧，那一支移到第 **263** 行；`aid` = 文档名 ＋ 锚点原文，而原文**含源码行号**（不含台账行号）
  ⇒ 同一条指针**同时**算"新增可疑"和"ID-MISS 回涨"。处置 = **三处就地重锚**（同一物理行内改、不裂表：241→263 共 **3** 处、245→**266**、400→**519**），
  写前整本备份、写时带命中次数守卫（预期 2/1/1，不符就不落盘）、写后临时文件 ＋ `os.replace` ⇒ 两本行数**一字未增删**，终值 **`DA-d 新增=0 消失=0` / `DA-e 286`**。
  ⚠ **没选的两条路**：删指针（撞 DA-d 的"消失即红"，删等于抹证据）、把闸值放宽到 ≤287（那是把判据改松，正面撞 §八 ㉚）。
  同轮被牵连的 `RC` 族也已就地重锚并复跑 rc=0：RC-a `31/16/48/23`→**`32/16/49/23`**、RC-b2 `31/0/98`→**`32/0/98`**、RC-e `35/4`→**`36/4`** ⇒ **判据目标值一条都没动**。
- **运行时那一半仍 `NOT VERIFIED ON DEVICE`**（0 台在环，证据 `docs/device-evidence/20261005-011754/`）。三条具体未知：① 真机到底回调**哪一个** `onGetNavigationText` 重载（两个都接了，**一次实机样本都没有**）；
  ② `setUseInnerVoice(true)` 是否真的自己出声（开关单向交给 SDK，效果没人看见）；③ 真实道路里 `IconType` 的**取值分布**（今天只有表、没有样本）⇒ 引擎层整本仍只由 grep 判据 ＋ 结构负控钉住。
- **两条口径必须分开抄**：① **"正式导航面 0 台本机 TTS"是作用域结论**，main 全文检索 `TextToSpeech` 命中 **7** 行 = **5** 行在 DEBUG ONLY 旧屏（`DriverNavScreen.kt`，D-003 立的，本轮未动）＋ **2** 行是"本机不建 TextToSpeech"的**注释**
  ⇒ 把 7 读成"本轮违背红线"是错的，把 7 藏起来不抄也是错的；② authenticity 窄 **9→10** / 宽 **48→49** 那两个 **+1 是同一处、且本轮自己造的**（转向表的注释行逐字抄了 SDK 常量名，含 `STRAIGHT = 9`）
  ⇒ **不是**直线回退复活，也**没有**为它加白名单（加了就等于把这条关键字计数调松）。
- **只登记、不判平的两格**：APK **113,912,212 B / +16,384 B 未归因**（D-044→D-045 那一格**也是** +16,384 B，两轮源码毫不相干却是同一个量级 ⇒ 更像打包/dex 的对齐量子；本轮没做差量实验就不写归因，`#140` 仍未闭环）；
  **release variant 本轮未复测** ⇒ `D-⑱-1` 那 **5** 例红仍是上一轮读数，这一条是**明知故用的旧数**，必须写在册上写明，不能默默沿用。
### F-3 ⑬⑭⑯⑰ 导航契约 / 路线绑定 / 缓存指纹：把"别名有没有生产者"变成列、把"会话绑哪条路线"变成三态（指令 v2.0 §六 的 ⑬⑭⑯⑰ / 硬规则 §五 字段兼容 + "宁可暂时不能导航，不允许假导航" / 判据 §八 ㉜ / D-053，本轮完成 · 2026-10-03）

**本轮买的东西一句话**：一个键名"大概是有生产者的"不再能进解析层（三面 0 生产者的别名 **10 处**撤掉，其中 1 处改成显式 `null`），一条导航会话"应该还是刚才那条路线"也不再能当结论（`RouteBinding` 三态 + SDK 层每次启动前重读一次 `navi.naviPath`）。

- **判据**：`check_navigation_probes.sh` 加 **N-u / N-v / N-w / N-x**（读数 `1/1/3`、`1/1/1/1/7`、`0/1`、`0/1`，N-a 用例下限同轮抬到 **34**）；新簿 **`check_doc_anchor_probes.sh`**（DA-a…DA-f，98 行 / 7,296 B）把"台账里的锚点"第一次变成有闸的结构；`check_default_value_probes.sh` 加 **DV-u / DV-v / DV-w / DV-x + GUARD4**（DV-v `keys 132 / sites 165 / C 行 33 / 非可空数值布尔 42`，DV-x `rows 466 / C 64 / 演示面键 216-1 文件 / c_no_producer 78`）。
- **行为用例**：`NavigationContractTest` 26→**34**、`RouteGuideContractTest` 12→**15**、`UserTrackingContractTest` 12→**13** ⇒ 全量 **47 类 / 476 例 / 0 failures / 0 errors / 0 skipped**，+12 **逐类对上**（不再是"总数差对上"）。
- **同轮负控**：结构 `navigation_negative_controls.sh`（M 族对应的注入）+ 行为 `navigation_test_negative_controls.sh`（**M12…M15**）+ `route_alias_test_negative_controls.sh`（**RA-1…RA-5**）+ `doc_anchor_negative_controls.sh`（**8 破口 + 1 POS**）；`eta_negative_controls.sh` 的 **`T-e` / `T-w2` 重锚后有判别力** ⇒ `D-⑰⑱-1` 销号（单本 ✔=61 / ✘=0 / 387 s）。
- **跨簿影响是实测的**：本轮改 `app/src` 让 RC 那族四条基线漂移（RC-a `30/16/47/23`→**`31/16/48/23`**、RC-e `34`→**`35`**、detector `30`→**`31`**），处置 = 抬基线 **同时**补两条新注入 + 修掉一处旧的字面量期望串 `sites=30`（它在数变后会**静默空跑**）；`RUNCATCHING_AUDIT.md` 是 `RC-e` 的**输入**，改完当场复跑那本簿（rc=0 / `35/4`）。
- **产物落档（给下一轮的可比物）**：类名清单 `/tmp/f150_perclass.txt`（47 行 / md5 `aa4f516f3333ee68543148a1f60e3eb6`）与 dex 描述符清单（新包 **1,358 行 / md5 `19f4bf8e9493e7ba184a7b4c65c8e1ed`**）；提取器**先复现** D-052 的 1,353 / `80d4a600…` 再报差集 ⇒ `D-⑧-1` 在描述符级闭环。
- **只买半边的地方明写**：V21-13 的 TTL / `graphVersion` 三面 0 生产者 ⇒ 不实现，`N-x 0/1` 先把话说破（`D-⑭-1`）；`RUNCATCHING_AUDIT.md` 的逐点账锚点**没有闸门**（`D-⑬⑭⑯⑰-1`）；pins 夹具整文件 CRLF 的机制未证（`D-⑰-1`）。
### F-3 ⑧ 任务地图无路线态收口：把"后端没有路线"从**页面各编一句**收成**一个出口五格说法**，并把"没问到"和"没有"拆成两格（指令 v2.0 §六 的 ⑧ / 硬规则"宁可暂时不能导航，不允许假导航" / 判据 §八 ㉛ / D-052，本轮完成 · 2026-10-03）

**本轮买的东西一句话**：任务地图在"这一轮没有可信道路折线"的时候，**说什么**和**画什么**都不再由页面自己决定 —— 句子只有 `MissionMarkers.missionRouteReasonOf()`（**:80**）一个出口，折线只有闸门 **:49** 一个产地，
而"这次根本没去问"和"后端说没有这条路"是**两格不同的话**，不许合并、更不许拿后者当前者（后者要后端真的给过原因才能说）。

| 五格里每一格 | 由什么决定 | 实测判据 | 为什么必须是这一格 |
|---|---|---|---|
| 在问 | `routePhase = Asking`（装载时置 **:226**，行号严格在那条请求 **:228** 之前） | `MR-c` 该格字面量 **1** + `MR-f` Asking **1** + `MR-h` **226/228/233** 严格递增且窗口内请求 **1** | "在问"必须能被读到：置位搬到请求之后就永远读不到（结构负控里那条 `exit 1` 在案） |
| 没问过 | `PolylinePhase.NotAsked`（VM 初值 **:124**，取消 **:230** 与撤档早退各回一次） | `MR-f` 退回 NotAsked **2** + `MR-g` 早退块内相位/原因/guide 清零 **1/1/1** | 上一轮的"问过"跟着下一轮一起念 = 把历史状态说成本轮状态；`MT-3` 证明这条**有用例咬**（红的正是 `上一轮留下的问过与失败在早退时一起清零`） |
| 没问到 | `RouteGuide.EMPTY` **且** `routeUnavailableReason == null` | `MR-d` 正式可达 4 文件里旧那句写死的"后端没有下发"归零 **0**，DEBUG 屏同机制 **3** 处作反证 | ⚠ **这是本轮最硬的一格**：正式链里 `RouteGuide.EMPTY` 只有 `Repo.routeGuide()` 的 catch 支**一个**生产点，后端真拒的时候必带非空 reason ⇒ "EMPTY 且 reason=null" 本身就是"没问到"的证据，**说成"后端没有道路"就是替后端下结论** |
| 后端给的 | `routeUnavailableReason`（非空才读，页面不解释） | `MR-j` 折线闸门行 **1**（两半都在：`size < 2` **与** `reason != null` → `emptyList()`） | 后端给了原因就照说；本机不自造原因，也不因为"有原因"就去找条线画 |
| 兜底 | 出口里唯一一处 `reason=null` 分支（`MapCanvas.kt` **:234**） | `MR-k` MapCanvas 兜底句 **1** / 可空 `reason` 参数 **2** | 兜底句自己写明"具体原因未取得"，不假装知道原因；⚠ 它是**页面级兜底**，不是第五种业务结论 —— 正式链到不了它（`MR-d 0`） |

**"不许直线补几何"在这一层才有可测形状**：`MapPath(` 全树唯一出现在出口 **:51**，任务地图页出现 **0** 处构造折线（`MR-j 1/0`）⇒ 页面**拿不到**把任务点连起来的语法；
`noRouteReason: String`（`DriverMissionMap.kt` **:60**）**必填、无默认值**（`MR-e 1/0/1`）⇒ 页面**拿不到**不经过出口的句子。两处 0 各配同机制反证（反证行号在判据簿读数里），不是"grep 写错了所以 0"。
⚠ **文案统一改用"示意线 / 折线"这两个不在 authenticity pattern 里的词 ⇒ 换词不是消罪**："每格都拒绝画线"的证据只由 `MR-c` 那个**"不画任何示意线"生效行 = 5** 给，**不由**宽口径 48 不变来给。
⚠ **复用 `PolylinePhase` 而不是新造一套**：`MR-k 1/2/0`（定义 1 / 复用 import 2 / 第二套枚举 **0**）—— §八 ㉗"同一个问题不许有两个答案"在这一格落地，改回布尔会被结构负控打破。

**判据与负控**：`bash tools/check_mission_route_probes.sh`（176 行 / 16,994 B / LF）**MR-a…MR-k 共 11 条探针 / 17 个读数**，rc=0，末行"任务地图无路线态判据通过"；
`bash tools/mission_route_negative_controls.sh`（276 行 / 11,655 B）**27 条注入 ✔=27 ✘=0 △=0 ◦=0**（批内 04:04:46→04:08:29 与批后单本复跑 05:17 **同值**）；
`bash tools/mission_route_test_negative_controls.sh`（168 行 / 10,489 B）**MT-1/MT-2/MT-3** `RESULT=PASS`。
⚠ **两条如实的缺账**：`D-⑧-2` —— **没有 MT-4**，`撤档早退时路线状态退回没问过` 在 MT-3 破口下不红（VM 初值已是 NotAsked，清不清都读得到），这一格覆盖面缺口按 §八 ㉛ ③ 在脚本头部**预留 ID** 登记，不假装三条覆盖四条断言；
`D-⑧-1` —— dex defined **29,828 → 29,829（+1）**、referenced +3、去重 +1，**归因到族归不到描述符**（`MissionMarkers` 无 facade、`WorkbenchScreenKt$…ExternalSyntheticLambda` 一族现数 17），**D-051 那条"main 没新增类型 ⇒ defined 不该变"的推断本轮被自己证伪**。

### F-3 ⑦⑨ DRIVER 逐按钮对真后端：把"缺项即不出网"从读代码变成**请求计数**，并抓出一条跨业务域真缺陷（指令 v2.0 §六 的 ⑦⑨ / 硬规则 1 与"禁止 GPS 自动完成业务" / 判据 §八 ㉚ / D-051，本轮完成 · 2026-10-03）

**本轮买的东西也是一句话**：司机在工作台上点「到站」「核销」「继续行驶」时，**本机自己知道该不该发这一跳**，而且"该不该发"不是靠人读代码认定，是靠 `server.requestCount` 数出来的。

| 这一跳为什么不许发 | 怎么证的（实测，不是"我看代码觉得会早退"） |
|---|---|
| 「到站」拿不到站点编号 | 后端 `AppDriverArriveReqVO.stationId` 是 `@NotNull` ⇒ 发出去只是**必被拒的一跳**，回执还会显示后端拒绝原因，掩盖真实病因（本机造了个缺项请求）。用例断"点完之后 `requestCount` 增量 = 0"，**连动作后的回读都不发** |
| 取件码为空 / 纯空格 | `pickupCode` 走后端取件码校验，空串发出去必被拒 ⇒ 两条各一条早退用例；**反方向对照**是"填了码报文形状原样不变"（不许为了过闸把报文改成后端认不出的样子） |
| 幂等闸拦下的那次 | 对照是"只多 5 条回读、不多 1 条写"⇒ 闸门语义与"缺项早退"是两件事，各钉各的 |
| 夹具形状 | 夹具同时换成**后端真实生产者键**（`stops` / `stationId` / `plateNo`）⇒ 别名形状的绿从此不算数（与 F-2 那两轮"自造别名键"同一条红线） |

**`isProduct` 那条是真缺陷，不是用例形状问题**（本轮唯一一处生产行为变化）：旧写法挂着第二个条件 `orderType == 3 && mailNo.isEmpty()`，于是**没面单号的邮快件**被判成商城单 ⇒ 装车走 `/driver/product-load`、妥投走 `/driver/product-deliver`，
而那两个接口按 `ProductOrderDO` 查单 —— 这是**硬规则 1 的跨业务域**（用户域/司机域之上的第二层：货运、快递、商城三条业务线各走各的接口），不是显示错。后端商城单的唯一判据是 `bizType=PRODUCT`（`DriverAppServiceImpl:352`）。
`Models.kt:1494` 回到单条件；`DriverStateTest > 揽件单类型决定作业要求`（:115 起）补两条跨域断言（:133-134 没面单号的邮快件不许进商城接口、:138 真商城单照旧）。
结构判据 `H-k 1/0` 只钉"写法回到单条件 + 枚举码不许回流"，**IT-2 才证明"改回去会有用例红"** —— 两本账缺一本，这句话就只是注释。

**任务点统一模型 = 两层派生，不许并成一层**：

| 层 | 认谁 | 判据 | 为什么不能并 |
|---|---|---|---|
| 导航目标（`targetIndex()` :234-238） | 后端 `currentStationId` 推出的下一站 **+ 本机 `navOffset`** :237 | `H-m1 1`（那一句在）+ `H-m2 3/3`（`navOffset` 的**三个写点全在** `continueToNextStation()` :301/:302/:304，声明行 :128 不算写点） | 并层的失败形状是"人还没到、画面已显示到第 3/5 站"= **本机视图冒充后端状态**；写点一旦散进位置回调或页面初始化，目标就会随 GPS 自己走 ⇒ "禁止 GPS 自动完成业务"在这一层才有**可测形状** |
| 显示当前站（`currentIdx` :886） | **只认后端** `currentStationId` | `H-m3 1`（锚点以 `}$` 收尾；第一版没收口 ⇒ 注入 `… } + 1` 后读数不变 = 假绿，见 §八 ㉚ ①） | 进度条、"第 N/M 站"、每站到站态都读它，让它读本机偏移就是拿视图当状态 |
| 「到站」提交的站点（`onArrive` :649） | `vm.targetStationId()`（**页面算目的地**） | `H-m4 1` 钉**耦合方向** | 曾是疑点（`D-④⑫-2`），实测与小程序 `workbench.js:796-811 arriveAtStation()` 同源，后端 `arrive()` 逐站校验会拒错的那一支 ⇒ **三方同源不是缺陷**；改它必须先重做三方核对 |
| 「继续行驶」的行为 | 只推进导航目标，**后端当前站与网络都不动** | `H-m5 1` + 用例 `继续行驶只推进导航目标_后端当前站与网络都不动` + **IT-3** | ⚠ IT-3 摘掉 `+ navOffset` 时结构判据 **rc=0 而 gradle 红 1 条** ⇒ "点了没反应"这类语义破口结构层面**看不见**，这是两本账分工的实测凭证（§八 ㉚ ④） |

**判据与负控**：`bash tools/check_idempotency_probes.sh`（273 行 / 25,730 B / LF）rc=0 / **本轮新增 18 行读数** `H-a…H-m5`（`I-*`/`U-*` 一族一字未动）；
`bash tools/idempotency_negative_controls.sh`（260 行 / 20,035 B）**✔=44 ✘=0 △=0 ◦=1** —— 账面是一条链不是三个数：**24（D-050）→ 38（本轮批内，`H-a…H-l`）→ 44（本轮批后追加 `H-m` 六条）**，写成"批内 44"就是假话；
`bash tools/idempotency_test_negative_controls.sh`（217 行 / 13,869 B）**IT-1 / IT-2 / IT-3** 三条各带"基线绿 → 注入 → 红的正是具名用例 → `cmp` 复原"，且**相邻两条之间先复原再取下一条基线**（D-050 那轮 IT-2 跑在残留树上的坑，本轮修在流程里而不是修在记忆里）。
⚠ `H-m3` 第一版负控**失效**（`/tmp/f86_negctl_hm.log` 第 44 行 `✘ … 负控制失效（exit 0）`）⇒ 锚点未行尾收口，"改坏它必须红"里的"改坏"要按**最像对的改法**设计（加一格、加一个条件），只试"整行删掉"会漏掉这一族。

**终值（最后一遍，不是最好看的那一遍）**：`assembleDebug + testDebugUnitTest + lintDebug` 同一条命令 02:10→02:13:17 rc=0 / **53/53 executed** / **46 份 XML、452 tests、0 failures、0 errors、0 skipped**（mtime 唯一值 `['02:12:25']`）；
**452 − 447 = 5** = 新簿条数、类数 45→46 同向，其余 45 类**没做**机器比对（工程无 git）⇒ 只算总数差对上。APK **113,895,828 B / sha256 `fbe111b1…41ff0e`**（字节数**第六次**与历史相同、sha 各异）；
dex **defined 29,828 与 D-050 逐项相同且归因得清**（main 源集零新类型）；lint **issue 元素 78** = 46 + 32、**Error 0** —— ⚠ D-049/D-050 那笔"79 vs 78 不自洽"**是计数式把根标签数了进去**，三轮条数本来就一致（D-051 §7 作废登记）。
树指纹 **files=161 sha256=`969caf63…2a1cb4`**。⚠ 本环境 `testDebugUnitTest` 起跑仍需命令行 `-Dfile.encoding=GBK`（Gradle worker `@argfile` 字符集，见 D-050 §8 与项目记忆），**未改 `gradle.properties`、未改用例**。

**没做 / 不做（逐条写理由）**：`D-⑦⑨-1` 后端 `shifts()` 对未绑定线路的司机返回**全部启用班次**（`DriverAppServiceImpl:195-211` 只在 operating route 非 null 时过滤，`:1871-1882` 无生效车辆或线路绑定时 `orElse(null)`）⇒ **后端侧**，本机按"后端是唯一真值"照单显示、**不自造第二道过滤**；
`D-⑦⑨-2` `photoRequired = !isProduct`（`Models.kt:1521`）比后端与小程序**都严**（小程序 `workbench.js:889-893` 是邮快件有面单不强制拍，后端只对货运抛 `DRIVER_CARGO_PHOTO_REQUIRED`）⇒ **未修、留给用户裁决**，改作业流程不属于本工程单方面可定；
`D-⑥-5` 与 `D-④⑫-2` 本轮销号，`D-⑥-2` 按约定同轮重判（`SR-e=0`）维持"不加第二道闸"；#140、#159、`D-⑰⑱-1`、DEVICE/RELEASE/GIT 仍在册。
**DEVICE_VERIFIED 未到位**（0 台在环，02:14:10 实测 `DEVICE_UNAVAILABLE`，证据 `docs/device-evidence/20261003-021410/`）：邮快件装车在无相机权限时会不会被本机卡住、点「继续行驶」画面导航目标是否真的换了、缺项回执在屏上的原文长什么样 —— **一次都没看过**，
本轮用例断的是**请求份数与本机视图分支**（JVM），不是屏幕 ⇒ 整体记 `ANDROID_F3_⑦⑨_PARTIAL`，不写百分比。

### F-3 ⑥ 深链的"只有一个出口"从口头主张收成 grep 可测；详情页四态在**列表落地之后**才允许回答（指令 v2.0 §六 的 ⑥ / 审计 硬规则 1 / 判据 §八 ㉙ / D-050，本轮完成 · 2026-10-02）

**本轮买的东西只有一句话**：`buspost://…` 从哪儿拼出来、谁剥前缀、过不过那道分域闸、详情页到底挑哪一条线路 —— 这四问各自**只剩一个出口**，且每个"只剩一个"都能被一条判据打死，不靠人读代码数。

| 出口 | 唯一性怎么证的（实测，不是"我写的就是唯一"） |
|---|---|
| 前缀与拼装 | `nav/Routes.kt` 里 `DEEP_LINK_PREFIX`:50 / `deepLink()`:53 / `routeFromDeepLink()`:59-61；`SR-i 1/0/1/1/1` 的第二位 0 = **别处再手写一遍 `buspost://` 就红** |
| 出网口四个消费点 | `MainActivity.kt:72`、`notify/Notifier.kt:69`/`:100`、`widget/NextBusWidgetProvider.kt:80`、`notify/AlertEngine.kt:96`/`:127`；`SR-l 3/0/1/1`（后两位钉"通知那行"与"小组件那行"这两个**具体位置**，只数总次数会随重构漂走） |
| 分域闸 | `nav/ShellDecision.kt:35 deepLinkTarget(route, role)` 两支 `-> null`（:36 壳路由丢弃 / :37 非司机角色命中 `DRIVER_ONLY`）+ 唯一消费点 `ui/BusPostRoot.kt:87`；`SR-c 1/0` 的第二位 0 = 不许有第二份答案 |
| 详情页选线 | 新 `ui/user/BusDetailSlot.kt`（51 行 / 零 Compose）：`detailSlotOf` :39，优先级 :46-50 四格各说各话；`SR-m 1/0/1/1/0` 末位 0 = `D-⑤-1` 那个"异步 `load()` 未落地就 `lines.firstOrNull { it.id == routeId }`"的抢读形状**不许回来** |

**编码为什么算业务正确性而不算显示细节**（本轮最想留的一条）：本工程 `unitTests.isReturnDefaultValues = true` ⇒ 任何经过 `android.net.Uri` 的编码在 JVM 用例里**静默返回默认值**，于是"用例绿、判据绿、线上带 `%2F` 的参数劈成两段"是可以同时成立的。所以 `Routes.kt` 的编码走纯 Kotlin `encodeSegment`(:82，`RESERVED_OTHER`:99 / `HEX`:101)，并由 `SR-j 0/1` 把"这个文件不许 `import android.net.Uri`"钉成判据；`DRIVER_ONLY`(:43) 四条路由因此**必须无参数** —— 带参数的司机域路由会让闸门那个**精确集合成员**判断直接落空，用例"带参数的司机域路由就是开闸形状"钉的就是这一手。

**用例**：新簿 `nav/RouteDomainDeepLinkTest.kt` **9 例**（往返同一性、非本应用深链不落路由层、司机域深链拼装后仍是同一条、开闸形状、分隔符不多出路径段、未保留字符集、四态各说各话、前台服务通知不构成免闸入口、小组件过闸落实时公交页）；`SR-n 9/19` 是**下限**（删用例即红），不是"我数了 9 条"。

**判据与负控**：`bash tools/check_shell_routing_probes.sh` rc=0 / **16 行读数**（终值树 @19:2x 与台账段复跑 `/tmp/f6_ledger_sr_recheck.log` @22:0x **逐字节同**）；`bash tools/shell_routing_negative_controls.sh` **✔=29 ✘=0 △=0**（221 s，本轮 +16 条破口）。⚠ **新写的 16 条里 4 条一开始是空跑**（`SR-k3` 取错锚点行号 + `SR-l3/SR-l4/SR-f3` 的 `drop()` 只给行加 `//`，而裸 `grep -cF` **照样计数**）—— 按 ㉘ ④ 把 `cnt()` 收紧成"先过 `NC` 滤注释行"才真红；另 `ctl SR-i` 报的红**实属 `SR-i4`** ⇒ 破口编号与判据编号要实测对上。这两条都是 §八 ㉙ ①②。

**终值（最后一遍，不是最好看的那一遍）**：`:app:testDebugUnitTest --rerun-tasks` 19:23:06→19:23:45 rc=0 / `24 actionable tasks: 24 executed` / **45 份 XML、447 tests、0 failures、0 errors、0 skipped**（`447 − 438 = 9` 恰等于新簿条数）；APK 113,895,828 B @19:21:24 / sha256 `0e0a0646…c24d9a` / 16 dex / defined 29,828（对 ⑤ 的 29,825 是 **+3，只点名 2 个**，#140 同格）；lint 28/28 executed、XML 98,753 B @19:23:03、**78 issues = Warning 46 + Information 32 / Error 0**，本轮三个新符号 0 点名；20 本 `check_*.sh` 终值树全 rc=0，负控全批 **BATCH_RC=1**（18 本绿、`eta ✔=59 ✘=2` 是在册旧项 `D-⑰⑱-1`、`notify_result`/`oss_credits` 各 rc=2 是"依赖产物的簿排在产物被 `--tests` 洗掉之前"，批后单本补跑两本 PASS ⇒ **不得写成 20 本全绿**，这条纪律第四次踩中）；树指纹 **files=160 sha256=`b87ad1ab…`**。
一次环境级红（**不是用例失败**）：`testDebugUnitTest` 连红 4 次报"管道正在被关闭"，定性为 Gradle worker `@argfile` 字符集错配（JDK 18+ 默认 UTF-8 写盘 / Windows `java.exe` 按 cp936 解释用户名"袁"），处置只在命令行加 `-Dfile.encoding=GBK`，构建脚本与源码一字未动。

**没做 / 不做**：`D-⑥-1` 陌生路由放行 = 设计（已钉不改白名单）、`D-⑥-2` NavHost 注册层不加第二道闸（`SR-e=0` 现状无 user 调用点，加闸就是两份真相；#147 引入司机页跳转时同轮重判）、`D-⑥-3` `Notifier.ongoing` 投司机壳（过闸回本角色壳，效果正确；文案要真机）、`USER_ONLY` 依然不存在（双向闸是 #147 的范围，本轮只把"司机→用户"那条**只读详情例外**按字面量登记成 `SR-f 0/1`）。
**DEVICE_VERIFIED 未到位**（0 台在环，19:16:32 实测 `DEVICE_UNAVAILABLE`，证据 `docs/device-evidence/20261002-191632/`）：点了通知到底落哪一屏、非司机点司机域深链是否真的停在原页、四态在屏幕上各长什么样 —— **一次都没看过** ⇒ 整体记 `ANDROID_F3_⑥_PARTIAL`。


### F-2 ⑧ `safeApiCall()` 落点：**改名**而不是新写第二份真相，配套把"helper 在但没人调用"钉成判据（指令 v2.1 §六 / §七 / V21-07 · V21-30 / 判据 §八 ㉕ / D-042，本轮完成 · 2026-10-01）

**先回答"改签名还是改名"这一问**（§七 那句"落一个 `safeApiCall()`"有两种读法，选错的代价不一样，所以先证伪再动手）：

| 候选 | 证据（实测，不是"我觉得"） | 结论 |
|---|---|---|
| 新写一个 `safeApiCall`（照指令字面"新增"） | `Api.kt` 里 `apiCall` 的定义体**就是**四类分型 + 取消透传，再写一份 = **第二份真相**，F-2 全篇在治这个 | **否** |
| 把形参改成 `suspend () -> T`（我上一轮的判断："非挂起形参包不住挂起调用"） | **这条是错的**：`ApiTest.kt:167` 那条现成用例（`inline` + 非挂起形参里直接调 `Repo.xxx()`）本轮实测仍编译通过、`ApiTest` 11 例全绿 | **否**，且这条猜测被自己的判据簿证伪并登记在 D-042 §1 |
| **改名 `apiCall` → `safeApiCall`、签名一字不动** | 定义行计数 GUARD=1 / 新名 9 处 / 旧名残留 **0**（反证由 SA-a 同机制给出）；改形参会一次制造几十个编译点，而 38 个 Repo 读接口是指令明令不许为 helper 动工的 | **是**（本轮实际做法，KDoc 写清它只分四类） |

⚠ **真正的病根不是签名，是休眠**：改名前 `apiCall` 的生产调用点 **0**（定义数得出、消费者数不出）。所以这一轮的产出不只是名字，
而是**让它真的有 5 个调用点** + 一本能数出"调用点为 0"的判据簿（SA-c）+ 一条把调用点打掉就红的负控。

**`attempt`（`core/net/Attempt.kt`）保留、不并入**：它收 `Result`、服务非网络读（Worker / 定位 / 引擎 / 开屏预取 / 交接照片），
`safeApiCall` 服务网络读、要的是"**哪一类**失败"。两者不是重复 helper —— 这句话在本工程里不是注释，是 **SA-d 的 `≥7` 下限**在替它站岗。

### 五个迁移点分两级，台账不许记成"修了 5 个缺陷"

| 级别 | 点位 | 改了什么 | 对外行为 |
|---|---|---|---|
| **真实修复 2** | `linePolyline` | 失败不再被洗成空列表，改 `getOrThrow()` 原样回抛同一个类型 | **变了**（不再"没问到"伪装成"后端没有"） |
| **真实修复 2** | `logout` | 后端撤销会话失败现在分类可见并打日志；**清本地仍无条件先做**（会话卫生优先于分类） | **变了**（失败可见） |
| **形状归一 3** | 天气预报段、`unreadCount`、`driverUnreadCount` | 旧的 `attempt { … }.getOrNull()` 里 `longOf` 本来就不抛 ⇒ 换的只是出口形状 | **一字未变**（不许写成"修了三个"） |

`driverUnreadCount` 保留 `Prefs.driverId <= 0 → null 且不出网` 那道显式门：`ScalarEnvelopeContractTest` 有一条用例钉着它，
改成穿透会让那条红 —— **先核契约再动手**，不是顺手统一。页面侧 `BusScreen` 新增 `polylineUnconfirmed`（**不复用共享 `vm.error`**：
别的接口失败会把折线的病因覆盖掉），`polylineReason()` / `polylineSubtitle()` 把"这次没问到（可重试）"与"后端未回传该线路折线"分开说；
旧形状 `reason = if (size >= 2) null else "后端没有…"` 归零 —— **本轮唯一那句假话就是它**。

### ⚠ 本轮最实的一条：判据下限我**推算**错了，被自己的判据当场抓住

新簿 `SA-d` 与旧簿 `TH-f` 都按 "`12 − 2 = 10`" 设下限，两本**同时红**，实测是 **7**。差 5 处、我只归因得出 2 处 ——
按纪律这时**不许把下限随手改成 7 了事**，必须先解释缺口。归因链三段全是实测/留档：

| 数 | 出处 | 是什么 |
| --- | --- | --- |
| **12** | `/tmp/vf_probe_check_throwable_probes.log`，mtime **2026-09-30 23:30:30** | F-2 ⑦ 收工时 `TH-f attempt 调用点/helper 穿透 = 12/1`，**同一条判据、同一个扫描面**（迁移前） |
| **5** | 本轮 5 个迁移点 | 我上轮以为"只有两处未读数来自 `attempt`"，**实际 5 处全部**原本是 `attempt { … }.getOrNull()` |
| **7** | 本轮实测 | 非 Repo 的 7 处（`BusPostApp:37`、`Locator:204`、`NotifyWorker:23`、`BusPostRoot:98/99`、`DriverSdkNavCard:134`、`HandoverScreen:147`）本轮一字未动 |

⇒ `12 − 5 = 7` 与实测吻合：**没有未归因缺失，也没有"树被顺手改过"**。两本下限同时改成已归因的实测值 `≥7`，注释里写明这条链；
负控仍一键打破（把 `Locator` 那处换成 `runCatching` ⇒ 7→6 当场红，实测 ✔）。
**教训升格为永久规矩（§八 ㉕）**：**判据下限只能实测，不许推算** —— 同类错的第三次（D-034 的"✔=18"推算、F2-7i 的注入空跑、本轮的下限推算），
三次都不是代码错，是**我把"我以为的差值"当成了"量出来的基线"**。

### 另外三条"永远绿 / 永不匹配"（都在负控跑起来之前是静默的）

- **SA 判据簿有一条死闸门**：`[ "$C_VM" = 1 ] || { echo "…": fail=1; }` —— 冒号代替分号让 `echo` 把 `:` 和 `fail=1` 当成参数打印，
  `fail` **永远不会被置上**。修法就是把 `:` 换成 `;`。**新簿写完必须逐条打红一遍才算装配完成**。
- **负控第 17 条第一次报 rc=0**：它注入的是**沙盒副本里的那本判据簿**，`ctl` 跑的却是**真身那本**（`PR=$SRC/tools/…`）
  ⇒ 破口根本没进被执行的那份。改成 `ctl` 第三参指定要跑哪一本。`verification_failure_negative_controls.sh` 早就用 `$PRSC` 处理过同一件事，
  **我没抄它的机制** —— "打到判据簿自己头上"的破口必须连"执行哪一本"一起换，否则这条永远是摆设。
- **接力脚本的 `END_MARK` 写死「共 16 本」**：新写第 17 本之后**永不匹配**，会一路等到 `MAX_WAIT` 再 exit 8，
  而"等不到"看起来像"批次还没跑完"、不像"口径过期了"。现在批次与接力**共用同一个 glob** 推 `EXPECTED_BOOKS`
  （`ls tools/*_negative_controls.sh | wc -l`），两边都不写死本数 —— **写死的本数在扩容那天必然失效**。

### 实测读数（证据都在文件里，不靠这段文字的自述）

- 新用例 `app/src/test/java/com/shanxiang/buspost/core/net/SafeApiCallContractTest.kt` **12 例**（本轮复测 **10,418 B / 214 行**，mtime 02:21:19）。
  ⚠ 包路径在 `core.net`（与被测 helper 同侧），**不是** ⑦ 那个 `verification` —— 抄 V21-30 那行时别按上轮的路径拼。
- SA 判据簿 `tools/check_safe_call_probes.sh`（**13,222 B / 142 行**，`bash -n` ok）：**15 行读数 / 34 道闸门，rc=0**。
  关键读数：GUARD `1`；helper 定义 `1` / 旧名残留 `0`（反证新名 `9`）；体内 `1/1/4/0`（兜底反证 Repo `38`）；
  使用点 `6`（Repo 5 + 页面 1）、五点点名 `1/1/1/1/1`；`?: return emptyList()` `0`（反证 `emptyList()` 在 Repo `24`）；
  `attempt` Repo `0` / 全 main `7`；`getOrThrow` `1` / 签名 `1`；页面状态 `1/2/1/2/1`；旧形状 `0/0/0`、新形状 `1`；
  用例 `12 / 取消 2 / 四类 3 / 不出网 3`（留档 `/tmp/f2_8_sa_probe.log`）。
- SA 负控 `tools/safe_call_negative_controls.sh`（**14,024 B / 207 行**，`bash -n` ok）：**17 条破口 + 3 条自检行（BASE / 空锚点 / RESTORE）**，
  实测 **✔=20 ✘=0 △=0**、rc=0、用时 80 s（留档 `/tmp/f2_8_sa_negctl.log`）。计数由脚本自己累加打印，**不是事后 grep 推算**。
- **负控全批**：`共 17 本`（02:39:11 → 03:10:35），逐本 **rc=0**，合并 **✔=309 / ✘=0 / △=0 / ◦=3**（`/tmp/final_neg_batch.log`）。
  就地型那几本的还原有 `cmp` 读数（`/tmp/shell_routing_test_negative_controls.run.log` 末三行
  "[恢复] ShellDecision cmp 一致 / [恢复] Routes cmp 一致 / [恢复后] gradle exit=0"）；
  批次之外又补了一次残留扫描：`app/src/main` 里 `NEG-CONTROL` / `negControlProbe` **0 命中**，
  D-041 那个出事锚点 `ShellDecision.kt:36` 现值是 `role != Role.DRIVER && route in Routes.DRIVER_ONLY -> null`（原样）。
- **全量用例**（接力脚本在批次结束后单发起，`--rerun-tasks`）：`BUILD SUCCESSFUL in 30s`、**24 actionable tasks: 24 executed**（无 FROM-CACHE）、
  **38 类 / 368 例 / 0 failures / 0 errors / 0 skipped**，XML mtime 集合只有 `03:11:16`（一份都没被负控洗过）。
  本轮点名：`SafeApiCallContractTest` **12**、`ApiTest` **11**、`DemoExceptionIsolationTest` **7**、`ApiCancellationTest` **5**、`VerificationFailureTest` **8**。
- **构建**：`:app:assembleDebug` rc=0 / `BUILD SUCCESSFUL in 1m 11s`（48 tasks：10 executed / 38 up-to-date）、`app-debug.apk` **114,437,929 B** mtime **03:12:23**。
- **lint**：第一次 `lintReportDebug` 是 **UP-TO-DATE**（XML 还是 09-30 21:09:49 那份）⇒ **不拿旧产物充当本轮读数**，
  就地 `:app:lintDebug --rerun-tasks` 重跑：`BUILD SUCCESSFUL in 1m 17s`、28 tasks 全 executed，
  `lint-results-debug.xml` **98,753 B / mtime 03:15:06**，**0 error** / 46 Warning + 32 Information = 78（与上一轮同数）。
- **判据簿 collateral**：`for b in tools/check_*.sh` **17 本全部 rc=0**（本轮新增那本没把别的簿顶红）。
- **核对表 census**（`tools/v21_requirement_census.sh`，rc=0，rows=118）：
  `V21-07` `attempt {` hits=**8** / files=**7** / **生效 7**（含注释那 1 行是 Repo KDoc 里写的"旧写法"原文）·
  `safeApiCall` hits=14 / 生效 9 · 旧名 `apiCall` **0**；`V21-30` 名单 **20** 名（指令点名的 18 本 + ⑦/⑧ 各加一本）里 present **6** / ABSENT 14（新增 `SafeApiCallContractTest` 12 例）。
  ⚠ **唯一的"新增行"只有那一本**：⑦ 与 ⑧ 两份产物 `diff` 实测，其余变化全是**计数**（`V21-01` 判据簿 15→16 / 负控 16→17、`V21-07` 三处、`V21-22` 1→2、`V21-33` consumers 6→7）。
  ⚠ **`SUMMARY|rows=118` 与 `wc -l` 的 119 是同一个产物的两个口径**（末行自己不算进 rows）——抄哪个要写清，别让它变成"下一轮差一行"的假异常。留档 `/tmp/f2_8_ledger_census.log`。
  ⚠ census 本轮另有两处增量，都已归因、都不算 ⑧ 的内容：`V21-22 ContractParseException` 1/1→**2/2** 来自本轮 `Api.kt:70` 新 KDoc
  （旧副本 0 命中、`Models.kt` mtime 早于 ⑦ 读数、两处 `code=0` 皆注释）；`V21-33 fixture consumers` 6→**7** —— 该判据数的是**扫描面里提到 `verification_failure_rules` 的文件数**，
  多的那一个正是 `docs/android/ANDROID_V2.1_CHECKLIST.md` **自己新写到了那张表**（普查当时量到该文件 mtime 00:06:02，**晚于** ⑦ 23:34 那次 census），
  ⇒ 是"**一个既有文件多了一次提及**"，既不是"多扫了一个目录"、也不是长出第四套真相。⚠ **该文件本轮 03:45 又被台账收口改过、mtime 已推进**，
  那个 `00:06:02` 只能从留档读、**盘上已不可复核**（无 git ⇒ 这类"当时量到的 mtime"必须随读数一起写进台账，否则下一轮无从对账）。
- **改动面**：新增 **3**（`SafeApiCallContractTest.kt` / `check_safe_call_probes.sh` / `safe_call_negative_controls.sh`，字节与行数见上，全部**不进 APK**）；
  改动 **9**（`Api.kt` 02:06:37、`Repo.kt`、`BusScreen.kt` 02:15:03、`ApiTest.kt` 02:17:08、`v21_requirement_census.sh` 02:18:50、
  `check_safe_call_probes.sh`、02:30:42 的 `check_throwable_probes.sh`、`throwable_negative_controls.sh` 02:30:45、
  `after_negctl_run_full_tests.sh` 02:38:57、`run_all_negative_controls_batch.sh` 02:39:02）。
  ⚠ 另有一批 main 源码 mtime 落在 **02:52–03:06**（`ShellDecision.kt` / `Routes.kt` / `ActionGate.kt` / `DriverException*.kt` /
  `NavigationEngine.kt` / `WorkbenchScreen.kt` / `Repo.kt`）—— **那是就地型负控的注入与还原，不是本轮的功能改动**，
  按 mtime 报改动面会把它们错算进来；所以这份清单是按"我改了什么"数、mtime 只用来交叉核对（**两边都有读数时，别把机制的痕迹写成决定**）。
- **台账收口复跑（03:37–04:02，纯文档轮）**：判据簿 **17 本 rc=0**（逐本留档 `/tmp/f2_8_ledger_probe_check_*.log`，本轮那本尾部 `SA-e 0/0/0 · SA-e 新形状 1 · SA-f 12/2/3/3`）·
  真实性脚本 **窄 9 / 宽 46 / GeoMath 27 / DemoData 文件 4 / 假步进 0**，与 ⑦ 的差**只有 `BusScreen.kt 2 → 4`**（两遍 17 文件构成表**逐项 diff** 实测，16 项未变，不是 `46−44` 反推）·
  census rc=0 / rows=118 · 安全组从工程根 **0 / 4 / 0 / 0** + release cleartext `true` **0** / debug **1** ·
  表格自检（无参数，五本）**TOTAL FLAGS 0 / rc=0** · `app/src` 本轮 **0 个文件**被碰（`find app/src -newermt "2026-10-01 03:37"` → **0**）。
  ⚠ 这一遍**没有复跑 gradle**：盘上"树干净"的证据仍是 03:11:16 那遍 **38 类 / 368 例 / 0 失败**（**旧读数**，本轮只动台账）。
  ⚠⚠ 本轮自己把状态板"真实性关键字（宽口径）"那一行**追加成了 6 个物理行**（表格在那里会静默断掉，`check_ledger_tables.py` 报 `cells 3 vs header 4`），
  并回时又用文本模式写回、让整本从 **LF 翻成 CRLF**（440,679 → 443,725 B）—— 两次都被当场抓出并还原，完整过程与规矩写在 **D-042 第 5 节末两条**（"改手写台账要么用编辑工具、要么显式换行 + 改完比字节数与 CRLF 计数"）。

### 后果与已知未收（照实登记，不写"完成"）

- **设备侧仍 `NOT VERIFIED ON DEVICE`**：本轮 12 条新用例全部只有 JVM 证据；`BusScreen` 那两句文案在 JVM 里渲染不出来
  （这是 SA-e 存在的理由，也是它的**上限**）。release 仍 **BLOCKED**（不绕 HTTPS 闸门、签名文件不进源码工程）。
- **在册未做**：F-2 ⑨–⑱（`runCatching` 审计、`NotifyWorker` 无条件 success、`DEVICE_UNAVAILABLE`、OSM 署名、
  依赖/APK 对照、契约证据文档、全量汇报）；§五 那 **32** 处 C 站点与 `plannedMinutes` / `plannedDurationMinutes`。
- **本轮新登记、一字未动的缺陷**（动它=扩大战线）：线路页首屏 `load()` 自动选中线路但**不拉折线**，
  于是 `polylineSubtitle()` 里"后端未回传该线路折线"那一句在**问过之前**就说了出口 ——
  与本轮修的那句是同一族（**把"没问"说成"没有"**）。修法要么首屏跟着选中拉一次，要么副标题再分一个"还没问"态；
  现在两处判据（SA-e 的 `E_SUB=1` 与 `E_OLD_SUB=0`）都**允许这个缺陷存在**，所以它只有这一段文字记账。

### F-2 ⑦ 四类构建失败分型：一张规则表 + 一个分类器 + 七份真实样例，落点在**有消费者的那一侧**（指令 v2.1 §三十三 / V21-33 · V21-30 / 判据 §八 ㉓ / D-039，本轮完成 · 2026-09-30）

**先回答"落在哪"这一问**（这一步真正的决策，不做完再补说明）：§三十三 要的是"构建失败必须区分四类，不能笼统写构建失败"。
指令字面给了类型名（`sealed interface VerificationFailure`），但**没规定它活在哪个源集**。三种候选逐个用"谁读它"筛：

| 候选落点 | 有没有真实消费者 | 结论 |
|---|---|---|
| `app/src/main`（随包分发的类型） | **无** —— 构建失败发生时 App 进程不存在，没有任何一处客户端代码能观察到它；且紧挨 `ApiException` / `NetworkException` / `Ack.error` 长出**第四套业务失败真相**，正是 F-2 ⑦ 自己禁止的 | **否**，本轮唯一的刻意偏离，写在类型 KDoc 第一段 + 本段 + D-039，不静默替换 |
| `tools/` 的一张表 + 一个 bash 分类器 | **有**：`verify_device.sh` 的构建分支与无设备分支（本轮接上），台账的分类读数由它出 | **是**（判据的数据源） |
| `app/src/test` 的 `sealed interface` + 用例 | **有**：8 条用例在测试期读**同一张表**，钉住"类别恰好四类 / 字面量全 ASCII / 不跨类重复 / 每类都有实测出处 / 样例只落一类 / 少一列就拒载" | **是**（结构性质）；⚠ V21-30 清单行**本来就点名 `VerificationFailureTest`**，所以这个落点与指令自洽，不是回避 |

**分类的数据只有一份**：`tools/verification_failure_rules.txt`（TAB 三列 16 行 / 4 类，**出处列每行都填**，`未实测:` 的 7 行明写"本工程从没产出过这条文案"）。
匹配语义两侧可证等价：字面量只准**纯文本子串**且**全 ASCII** ⇒ bash `grep -aF` ≡ Kotlin `String.contains`；前提由用例逐行验（含"不含正则元字符"），bash 侧由 VF-a 再钉一遍，分类器自己也拒绝加载含元字符或非 ASCII 的表行。

**七份样例 = 六份正面 + 一份反例**（逐份的命令、字节、sha1、`cmp` 结果、命中字面量与首次行号都在 `tools/fixtures/verification-failure/README.md`）：
worker 那份 **148,548 B**（`GradleWorkerMain` 命中 **72** 次）与"用例失败"那份**都带 `> Task :app:testDebugUnitTest FAILED`**，
而前者判 `ENVIRONMENT_FAILURE`、后者 rc=**4** 拒绝给类别 ⇒ 这四条禁忌里"不许用 `> Task … FAILED` 分类"第一次有了两份日志的实证，不再是一句口头约束。

**四条分类禁忌**（写进规则表头部注释，不是散在对话里）：不用 `> Task … FAILED`、不用 adb 表头 `List of devices attached`（有设备时也在；
负控第 12 条把这条收进表做成反证，一台**在场**的设备会被判成 `DEVICE_UNAVAILABLE`）、混合编码日志一律 `grep -a`、`未实测:` 出处的字面量首次命中要打印 NOTE。

**实测**（`NOT VERIFIED ON DEVICE` —— 本步骤全在工程验证面，设备上没有可验行为）：

- 新用例 `VerificationFailureTest` **8 例 / 0 失败**（类名 `verification.VerificationFailureTest`，XML 里 8 条）。
- 全量 `:app:testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL in 33s`、**`24 actionable tasks: 24 executed`**、
  **37 份 XML mtime 全为 23:17:16**，逐份读 `testsuite` 属性自相加 ⇒ **37 类 / 356 tests / 0 failures / 0 errors / 0 skipped**。
  **+8 的归因是机器比对**：把 XML 逐类计数与**从台账文本当场解析出来的**基线表（`35 行逐类表` + F-2 ⑥ 那条"新簿 10 例"）对着跑
  ⇒ `ADDED {VerificationFailure: 8}`、`REMOVED {}`、`CHANGED {}`。⚠ 第一次比对我**手抄**基线，漏了 `SendOrder 8` 造出假差异
  —— 与 F-1/E-1 那两次"类名写错/剥掉 `Test` 后缀"是同一条坑的第三种触发方式：**基线必须由脚本从台账读，不能由我复述**。
- 新判据簿 `check_verification_failure_probes.sh`：**17 条读数 rc=0**（GUARD + `VF-a…VF-g`），耗时 **9–10 s**。
  ⚠ 第一版跑 **18 s**：逐条字面量起一个 grep，16 行 × 6 份样例 = 上百个进程，Windows 上进程开销就是主要成本；
  改成"一次 `grep -a -F -f` 扫完 + bash 内归属"，并把规则表加载提到脚本级、VF-b 与 VF-b 覆盖率**复用同一次分类结果** ⇒ 9 s。
- 新负控簿 `verification_failure_negative_controls.sh`：**13 条破口 = 13 ✔ / 0 ✘ / 0 △ + 1 ◦**，`bash -n` 通过、rc=**0**，
  收尾 `REC` + 五份 `cmp 一致` + `DIR 真身 main 源集没多出文件`。⚠ **第一次实跑是 rc=1**：
  `✘ 第 9 条`（改一个子类型名 → 判据不动）与 `△ 第 10 条`（分类器改读另一张表 → 红的是 VF-b 不是 VF-g）。
  两条都不是"注入没生效"，是**判据本身数错了东西**：VF-f 只数 `object … : VerificationFailure` 的**个数**（改名后个数照旧 4），
  VF-g 的"消费者文件数 ≥3"对"4 掉到 3"这种破口天然无感 ⇒ 补成"**逐名点齐 4/4**"+"分类器读表那行**赋值**单独钉 =1"，
  并新增第 13 条把本轮真找到的**出处行号指错**固化成判据。
- `:app:lintDebug :app:assembleDebug` **rc=0 / `BUILD SUCCESSFUL in 24s` / `48 actionable tasks: 3 executed, 45 up-to-date`**
  ⇒ **没有重新打包**：盘上 APK 仍是 **21:13:59 / 113,863,001 B**、lint 三份产物仍是 **21:09:49** 那批
  （`lint-results-debug.xml` 98,753 B、ElementTree 解析 **0 error / 46 Warning / 32 Information = 78**，与 F-2 ⑥ **逐项同数**）。
  ⚠ 这里 `lintAnalyzeDebugUnitTest` **执行了**而 `lintReportDebug` **UP-TO-DATE** ⇒ 照实记"报告未重写、读数沿用同输入的一致性"，
  **不写成"本轮重算 lint"**。本轮 `app/src/main` 一字未动，所以这份"未重写"是自洽的而不是漏跑。
- **包内新鲜度（按 §八 ㉒ ④ 加强版：三种打印都不算证明，必须在 dex 里数）**：`unzip` 出 **15** 个 dex（合并 65,505,264 B），
  本轮六条新串 `SOURCE_FAILURE / DEPENDENCY_FAILURE / ENVIRONMENT_FAILURE / DEVICE_UNAVAILABLE / VerificationFailure / ScratchVfCapture`
  全 **0** 命中；**先证方法**：同一批 grep 里 `gatedUserCreate` **4**、`userCreateGate` **1**、`tryLock` **10**、`本次请求未发给后端` **1**
  ⇒ 那个 0 是真的（测试类与 tools 面不进包，录制用的临时 `ScratchVfCapture.kt` 也已删净：`find app/src tools -name 'Scratch*'` = **0**）。
- `verify_device.sh --no-build` **rc=1** ⇒ 本轮第一次留下**产物目录** `docs/device-evidence/20260930-232805/`
  （`device-check.log` 55 B / `device-probe.log` 37 B / `summary.md` 372 B），`summary.md` 首行 `# DEVICE_STATUS: DEVICE_UNAVAILABLE`，
  分类依据是 **adb 的报错原文** `adb.exe: no devices/emulators found`，不是"没有设备"这句转述。
- `bash tools/v21_requirement_census.sh` **rc=0 / 117 行**（上一轮 113）：`V21-33|main 面 hits=0`（**这条 0 正是红线本体**）、
  新补三行 `VerificationFailure(test 源集) hits=21 / files=2`、`rules-table rows=16 kinds=4`、`fixture-logs total=7 positive=6 consumers=6`，
  并把 `VerificationFailureTest` 加进 V21-30 的类存在性对表（`present|tests=8`）。
- 全量 collateral：`for b in tools/check_*.sh` **16 本全部 rc=0**（55 s 跑完，新簿没把别的簿顶红）。

**本轮顺手复核出的一条台账自相矛盾（不回改历史数字，只加日期标注）**：F-2 ⑥ 的 COMPLETED 段写"全量 36 类 / 348 tests（**35** 份 XML …）"，
同一轮的 DONE 段写"**36** 份 XML"（:1146 那句实测）。两处相差一份，DONE 那处是逐份读出后的数、COMPLETED 那处是笔误。
本轮实测 37 类对应 **37 份 XML** ⇒ 与 DONE 的口径连续。

**已知未收（登记，不说成已完成）**：① **`assembleRelease` 仍 rc=1**（`app/build.gradle.kts:171` HTTPS 闸门）。
本轮把它当作**分类器的真实输入**跑了一遍：分类器给 **`UNCLASSIFIED` rc=4** —— 四类都不认领"工程自己设的策略闸门"，
这是**设计意图**（宁可不给类别，也不把"配置期被红线拦住"洗成 `SOURCE_FAILURE`）；**没加** `-Pbuspost.allowInsecureRelease=true` 绕过。
② `未实测:` 的 7 行字面量（javac 的 `error: cannot find symbol`、`Could not GET`、`SDK location not found`、`Java heap space`、
`device unauthorized`、`No connected devices!`）**没有样例撑着**，用例只许"每类至少一条实测"，所以它们合法但**弱**；
③ **规则表只覆盖本机会产出的文案**：CI/Linux 上的 gradle 输出形状（如 `> Task` 前缀、编码）不同，迁过去要重录样例；
④ `VF-b` 的"六份正面样例"是**下限式**判据（`VB_OK>=4` 且 `VB_BAD=0`），删掉第五、第六份不会红 —— 兜住它的是"四类各有至少一份"的覆盖行，不是份数；
⑤ **真机侧一字未验**，`DEVICE_UNAVAILABLE` 的判定只到"adb 报错原文进了产物目录"这一层。

### F-2 ⑥ 用户端 `create` 幂等：两把域闸 + 键与后端同维度 + 拦下就说"没发出去"（指令 v2.1 第 6 步 / v2.0 §52 的用户侧镜像 / 判据 §八 ㉒ / D-038，本轮完成 · 2026-09-30）

**动手前先量后端契约**（"绝不自己假造 API"在这一条上是硬约束：幂等规则是后端的，不是本机发明的）：

| 后端事实 | 出处（逐字读过） | 本机因此怎么做 |
|---|---|---|
| 只有两个 create 有幂等 | 全后端 `@Idempotent` 命中 **3** 处，其中 `package-info.java:8` 是注释 ⇒ 真实 **2** 处：`AppProductOrderController.java:38`、`AppSendController.java:120` | 只把这两条写路径接闸；**其余 create 一律不接** |
| 窗口 10 秒 | 两处都是 `timeout = 10, timeUnit = TimeUnit.SECONDS` | 闸门成功后保留 **10 s**（与司机域 `ActionGate` 同一个类、同一口径，没重写） |
| 键的维度 | `UserIdempotentKeyResolver.java:21-25`：`MD5(方法签名 + 参数串 + userId + userType)` | 键 = SHA-256(`path` + `Prefs.mobile` + **整份报文按 key 排序**)；**维度同源、值不可能同**（本机拿不到服务端 `userId/userType` 的表示，猜一个就是假造） |
| 重复的代价 | `IdempotentAspect.java:50-52`：`setIfAbsent` 失败 ⇒ `ServiceException(REPEATED_REQUESTS=900, idempotent.message())`，message 原文"订单正在创建中，请勿重复提交" | 本机拦下**不发**，后端回 900 时**原样透出那句话**（用例第 5 条）；⚠ 本机放行 ≠ 后端放行，这条必须留着 |
| 异常即放 | `Idempotent.java:61` `deleteKeyWhenException() default true` | 业务异常/网络异常后**立刻放闸**（用例第 4 条：失败后第二次真出网）——否则用户 10 秒下不了单，而后端随时收 |
| 另两个 create | `AppAddressController.java:32`、`AppFeedbackController.java:28` **零** `@Idempotent` | **刻意不走闸**，只由 **U-j** 登记为"4 处 create 里 2 处无闸"；单方面拦就是替后端发明规则（且它们不产生"两张单"那种脏数据） |

**落点**（生产源 **3** 个文件）：`Repo.kt:508` 第二把 `ActionGate` 实例 `userCreateGate`、`:475-493` 键（`digest.update(path)` / `update(Prefs.mobile)` / `body.entries.sortedBy` 三个维度，null 与空串用 `update(1)`/`update(2)` 分开，**只存摘要**——键表是进程级的，收货人姓名/手机号/地址进明文就是个人信息留存）、`:501` 唯一出口 `gatedUserCreate`、`:529/:532` 商城下单、`:573/:576` 寄货下单；`GoodsDetailScreen.kt:115`、`SendScreen.kt:530` 两处 `if (ack.notSent) { … return@work }`，**位置在"回读列表"之前**（继续往下走就会把上一次点击的单号张冠李戴给这一次）。

**司机域一字未动**：`actionGate`（`Repo.kt:1325`）、`gatedAction`（`:1162`）、十个走闸出口、`I-*` 十条读数全部保持原值。分域不是排版：`UserCreateIdempotencyTest` 第 8 条用反射数 `Repo` 上 `ActionGate` 类型的字段 ⇒ 恰好 `["actionGate","userCreateGate"]`，**并成一把就红**。

**实测**（`NOT VERIFIED ON DEVICE`：真机连点、900 的实际渲染证不到）：

- 新用例 `UserCreateIdempotencyTest` **10 例 / 0 失败**。指令点名的两条是硬断言：`连点五次同一份订单只有第一次真的出网` ⇒ `server.requestCount == 1` + `notSent` 支 **4** 条；`业务失败后立刻允许重试` ⇒ 重试真出网并拿到 `SC-OK`（`requestCount == 2`）。另有：被拦下的那次回执是**空的**（`orderNo=""`、`demo=false`，不张冠李戴也不谎称演示）、改数量/改地址必须出网（本机闸门不许比后端更严）、两个端点各走各的窗口、三维度键（`^create:[0-9a-f]{64}$` + 四段明文个人信息**都不是子串** + null≠"" + 与 map 顺序无关）、900 原样透出且**不再由本机代拦**、演示回落那次确实出过网所以随后点击仍被拦、**3 个并发 async 只出 1 个请求**（钉"丢弃"不是"排队"，`setBodyDelay(600ms)` 制造在途）。
- 全量 `:app:testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL in 41s`、**24 actionable tasks: 24 executed**，**且产物齐**（`§八 ㉒ ⑤`：`executed` 数不等于产物）：`test-results/testDebugUnitTest/` **36 份 XML**、mtime 全为 **21:07:20**，逐份读 `testsuite` 属性自相加 ⇒ **36 类 / 348 tests / 0 failures / 0 errors / 0 skipped**（上一轮 35 类 / 338 ⇒ **+1 类 / +10 例**，与用例簿对得上）。
- 判据簿 `check_idempotency_probes.sh`：**22 条读数**（`I-*` **10** 条 + `U-*` **12** 条；⚠ 与负控的"11 条 `I-*` 破口"是两件事 —— 一条读数可对应多条破口）`bash -n` 通过、**RC=0**、末行"幂等判据通过"（2026-09-30 **21:42** 台账轮里复跑照旧 RC=0）。新行了值：`U-a 2 · U-b 2 · U-c 2 · U-c2 2 · U-c3 2 · U-d 3 · U-e 2 · U-f 0 · U-g 1 · U-h 10 · U-i 2/2 · U-j 4`。⚠ **U-i 第一次跑出 1/2 是我自己写的判据错了**（两处 create 现在都调 `Api.post(path, …)`，字面 grep 只命中 `feedback/create`）⇒ 改成判**端点声明面**（`val path = "$T/…/create"`）并与走闸数**对账**，另立 **U-j** 只登记不判红。
- 负控簿 `idempotency_negative_controls.sh`：**25 行控制 = 24 ✔ / 0 ✘ / 0 △ + 1 ◦**（11 条 `I-*` + 13 条 `U-*` + 1 条 ◦），基线与恢复后各判通过一次。**◦ 那条是 U-j**：不判红的登记项**没有也不该有**负控——能给它写负控的前提是它会让脚本 `exit 1`，而那正是我们不要的行为（按诚实纪律写在簿内，不假装"22 条全有负控"）。
- **分域的实证**（不是设计意图，是量出来的）：把 U-a 打穿（寄货 create 绕闸）后单跑判据 ⇒ `I-a 10 / I-b 10` 一字未动，只有 `U-a 1 / U-b 1` 红。
- 全簿 collateral：`for b in tools/check_*_probes.sh` **14 本全部 RC=0**（新增判据没把别的簿顶红）。
- **台账往返之后的复检（21:42 那一遍，不是沿用上面那条）**：改完五本台账（本文件 / `CONTEXT_LOCK.md` ⑭+§九+㉒④加强版 /
  `DECISION_LOG.md` D-038 / `THIRD_PARTY_NOTICES.md` / `docs/android/` 两份）再跑一遍 14 本判据簿 ⇒ **全部 rc=0**
  （有判据簿会 grep 台账文本，所以这一遍是**必须**的，不是走过场）；表格自检 `python tools/check_ledger_tables.py`
  ⇒ 五本 **flags=0**（`ANDROID_PARITY_AUDIT_V2 3283 / CONTEXT_LOCK 1049 / DECISION_LOG 1756 / THIRD_PARTY_NOTICES 262` 行；
  ⚠ **本文件的行数不写**：写进行数里的那一刻它又变了，这种自指数字只会下一轮就假 —— 只报"跑过、flags=0"）。
  ⚠ **源码树在 21:13:59 打包之后一字未动**（`find app/src -type f -newermt "2026-09-30 21:13:59"` → **空**）
  ⇒ 那份验过包内内容的 APK 与当前源码仍自洽；本轮台账改动**没有**触碰任何生产源，所以不需要重打包
  （对比上面"已知未收"第 ④ 条：那正是"改了注释却没重打包"会造成的暗账）。
- `:app:assembleDebug :app:lintDebug` **RC=0**；lint 末行 **`0 errors, 46 warnings`**（XML 78 条 = Warning 46 + Information 32、**10 个类别**，三份产物 mtime 全为 **21:09:49**，与上一轮 F-2 ①–⑤ **逐项同数**，本轮没引入新告警项）。
  ⚠ **本轮改动文件在 lint 里的点名是 `SendScreen.kt` 7 处读数 / 6 个不同位置**（`AutoboxingStateCreation` `:132`/`:427` + `DefaultLocale` `:502`/`:586`/`:675`/`:879`/**`:879` 重复**）；
  `Repo.kt` / `ActionGate.kt` / `GoodsDetailScreen.kt` **0 点名**。全表"完全相同的 `(id,file,line,message)`"重复读数实测 **1** 条，就是这条 `:879`
  —— 与 F-2 ①–⑤ 那轮"`grep -c` 数 9 / ElementTree 数 8"是同一现象，**不是本轮新增的问题**；行号相对 19:27 那份整体下移（`:421`→`:427`、`:496`→`:502` …）
  是被我插入的 notSent 分支顶了行。**抄成"本轮文件 0 点名"是假话，抄成"新增类别"也是假话，7 处 / 6 位 / 2 类（都是既有类）才是实测。**

**本轮两条新口径（都是被自己的工具骗出来之后改的，见 D-038）**：

1. **`UP-TO-DATE` / `executed` / `BUILD SUCCESSFUL` 三种打印都不是包内新鲜的证明**：21:08 那次 `assembleDebug` 打印 `packageDebug` 执行过，但 `outputs/apk/debug/app-debug.apk` 仍是 **19:33:42 / 113,863,001 B**，`unzip` 出的 15 个 dex 里 `userCreateGate` **0** 命中；同时 `intermediates/dex/debug/mergeProjectDexDebug` 是 **21:08:18** 且**有新符号** ⇒ 陈旧在打包环节的 up-to-date 记账，不在编译。`:app:assembleDebug --rerun-tasks`（**37 actionable tasks: 37 executed**，21:13:59）后包内实测 `userCreateGate 1 · gatedUserCreate 4 · "create:" 1 · 两条新文案各 1`，旧串 `gatedAction 4 · 本次请求未发给后端 1` 仍在（分域在包里也成立），`UserCreateIdempotencyTest` **0**（测试类不进包）。⚠ 先证方法再信 0：同一批 grep 若连已知串都数不到，0 就是工具坏了而不是内容旧——这次 `gatedAction 4` 命中，所以那个 0 是真的。
2. **判据编号互为前缀时，负控断言必须锚定分隔符**：`ctl` 原来用 `grep -q "^× $1"`，于是 `× U-c2 …` 会被当成"U-c 也咬住了"⇒ **假 ✔**。改成 `grep -q "^× $1 "`（编号后必须有空格），三条 `U-c/U-c2/U-c3` 各有自己的破口、互不顶名。同轮把沙盒从"只拷两个用户页"改成 **`cp ui/user/*.kt` 整目录**（`I-e` 那次的教训在用户域原样重演：用户页还在涨，部分 copy 会把"下限"变成"沙盒的巧合"）。

**已知未收（登记，不说成已完成）**：① ~~取消路径的放闸**不在用户域重复钉**（`Api.kt:239/:295` 在 `withContext(Dispatchers.IO)` 里阻塞 `execute()`，JVM 用例无法确定性地"在途取消"；语义钉在 `ActionGateTest`，两域同一个类）~~ **已收口（2026-10-16 D-124）**：三条发请求通路改走 `callCancellable`（`enqueue` + `invokeOnCancellation { call.cancel() }`），"在途取消"从此能被 JVM 用例**确定性**钉住 —— `NetHygieneContractTest` 压一条 30 秒延迟的响应，实测取消在数百毫秒内收场；`ActionGateTest` 那两域的放闸语义不动；
② `app/build/outputs/apk` 只有单份通用包（无分 ABI apk）；③ 真机连点与 900 文案渲染 `NOT VERIFIED ON DEVICE`；
④ **`ActionGate.kt:13` 那句类注释现在是过时的** —— 写的是"**司机动作**的本地幂等闸（V-1 / 指令 §52）"，而这个类今天被**两把实例**共用
（`U-c` 实测实例数 2）。**本轮没顺手改它**：改注释也是**内容改动**，会让刚立起来的"包内新鲜度"证据（21:13:59 那份 `03ab6172…`）作废，
必须重打包 + 重扫 dex 才能继续声称自洽 —— 这正是 §八 ㉒ ④ 加强版的代价，**登记给下一轮连同重跑一起收**，不留"改了文档但产物已过期"的暗账。
⑤ `packageDebug` 那条 up-to-date 记账**为什么**与包内容不一致，根因未证（本轮只排除到"不在编译环节"，`--rerun-tasks` 后现场即被重写，本机无 git 取不回来）。


### F-2 ①–⑤ "缺失字段被默认值伪装"：v2.1 落档 + §三 `QuoteResult` + §四 `NearbyResult` + §五 数字默认值普查（指令 v2.1 §三 · §四 · §五 · §二十四 / 判据 §八 ㉒ / D-035 · D-036，本轮完成 · 2026-09-30）

- **动手前先量到的三份事实决定了范围**：① `QuoteResult` 的五个数值字段全是**非空 + `multiNum`**（缺键 → `0.0`，界面就打印 `¥0`，
  与"后端报价 0 元"在屏幕上**长得一模一样**）；② `Repo.kt` 的两处 `optBoolean(…, true)` 把"没给"说成"正在运营 / 已定位"，
  而**演示面根本不写这两个键** ⇒ 演示包永远看不出破口；③ §五 普查 `472` 处带默认值读法里 **A 类 = 0**
  （后端 App RespVO 字段清一色 `Long/Integer/BigDecimal/Boolean`，census 扫后端全模块 primitive 声明命中 **0**）
  ⇒ 这不是个别笔误，是**结构性**的：每一个非可空读法都站在"后端可以不给"的字段上。
- **§三（`QuoteResult`）**：五字段 `Double?`/`Int?` + `multiNumOrNull` 哨兵；`fallbackCount` 兜底通道与 `coerceAtLeast` 一并归零
  （**客户端件数不许替后端说话**）；界面三个出口 `amountText`/`amountValueText`/`feeBreakdownText`。
  ⚠ **与指令字面的一处刻意偏差**：指令要"暂无报价"，实测文案是**"金额未给"/"未给"**（APK 内"暂无报价" **0** 处）——
  "暂无报价"暗示"后端算过、结论是没有报价"，那是**另一种事实**；偏差写进清单，不静默替换。
- **§四（`NearbyResult`）**：`inService`/`located` → `Boolean? = null`，`isOffService` 只在 `== false` 为真。
  四面取证：后端 `AppBusNearbyRespVO.java:65/:45` + `AppBusServiceImpl:481/:532/:256`，小程序按**三态**读
  （`pages/bus/index.js:240`、`:288/:291`；`pages/index/index.js:539/:554`），演示面**两个键都不写**。
  `located` **没有任何 UI 消费点仍改可空** —— 默认值本身就是谎言，不需要"有人读"才配修。
- **§二十四（Minutes 五处别名）**：自造第二候选名**删净**（`T-aj/T-aj2/T-aj3` 全 **0**），逐键证据进 `CONTRACT_EVIDENCE.md`。
  ⚠ E-1 那条"可能是小程序真在用的"**被本轮证实了一半**：`walkMinutes` 确实是真键（`AppSendReachabilityRespVO:32`）⇒
  处置是**保留真实键、只删别名**，没有照 E-1 的处置把整条读法拆掉。
- **§五（普查 + Earnings 一族）**：`default_value_census.py` → **472 / B 398 / C 74 / A 0**，明细 `DEFAULT_VALUE_AUDIT.md`；
  本轮只收 `/transport/driver/earnings`（C 类里唯一"整屏结论都来自无生产者键"的端点：`weekAmount`/`monthAmount`/`totalWeight`
  **后端全模块 0 命中、只有 `DemoData.kt` 在发** ⇒ "本周 0 元""今日 0 kg"是谎报），其余 **32 处** C 类**在册未动**（不扩大战线）。
- **本轮最有价值的部分是三处"判据串味"与一条假负控**（都在收台账这一遍实测抓到，明细 D-036）：
  `R-f` 被 §四 的 `multiBoolOrNull` 顶到 4（涨的两处归 `B-*`）⇒ 收回"声明 1 + `Reachability` 解析体 1"；
  `Q-e1` 把 `EarningItem` 的同名 getter 数进来 ⇒ 收回 `QuoteResult` **类体**；
  ⚠ **那条 Q-e1 负控从来就是假绿** —— 整文件 `replace(…,1)` 删到的是另一条契约那行，**破口从未打进被判定量**
  ⇒ 新增 `delbody()`（类体内删）+ **反向口径控制 `keep`**（删别的契约同名 getter 时本条**必须不响**），
  簿子标记扩成 `✔/✘/△/◦` 并加 `exit 3` 闸门。**三条都只收紧扫描面，没有一条把期望数字改松**（沿用 ⑪ 的 R-g 先例）。
- **工具自误三条**（都升为 §八 ㉒ 永久规矩）：漏分号 ⇒ `ctl` 被当参数吃掉、那条控制**从未执行**（✔ 少 1 才发现）
  ⇒ 改完脚本 `bash -n tools/*.sh` 全量扫 + 每本簿 ✔ 条数与簿头声明的破口条数**对账**；
  **APK 新鲜度只认包内内容**（"12 executed"配的是 16:45 的旧包，`--rerun-tasks` 才换出 19:33 那份）；
  注释过滤器形状要与 grep 输出形状配对（`-r` 带文件名前缀 vs 单文件 `行号:内容`）。
- **实测**：四本判据簿（`DV` / `Q+B` / `R` / `T`）**同轮复跑全部 rc=0**；负控 15 本批处理逐本留档（`/tmp/allneg_f2_5.txt`，
  本轮实测 `contract_defaults 22 ✔ + 1 ◦ KEEPFAIL=0` / `default_value 16 ✔` / `eta 60 ✔` / `exception 21 ✔` / `exception_test 8 ✔` /
  `idempotency 11 ✔` / `idempotency_test rc=0（该簿不打印 ✔ 标记）` / `navigation* 27 ✔ + 10 ✔` / `reach 21 ✔` / `role_reconcile 13 ✔`，
  全量与末三本读数见状态板）；全量用例、lint、APK 三项收工读数见 `TEST_STATUS` / `BUILD_STATUS`；
  ⚠ **一字未验**：`NOT VERIFIED ON DEVICE` —— 缺键报文在真机上渲染成哪几个字，JVM 用例只能证 getter 返回值，证不到屏幕。

### F-1 程序缺陷不再被写成"后端给了演示数据"，协程取消不再被写成"加载失败"（指令 v2.1 §六 · §七 · §三十一 / 审计 §80 / D-034 / 判据 §八 ㉑，本轮完成 · 2026-09-30）

- **这一条为什么在下面四层而不是页面上**：页面上的空态、错误条、"演示"标签、"取消之后不再刷新"全是**下游读数**，
  上游只有三种收法决定它们长什么样：`Api` 的回落面（缺陷会不会被洗成 DemoData）、`Repo` 的出口（失败与取消是不是同一个词）、
  `work()/load()` 这台漏斗（取消有没有被收成一次失败）。所以本轮**一行 Compose 都没改**（§三十五 明写本轮不做大规模迁移）。
- **动手前量的三份事实**（当轮 grep，含注释/不含注释两个口径分开写）：生效的 `catch (e: Throwable)` 全 main **2** 处
  （`Attempt.kt:22` / `ActionGate.kt:63`，两处都合法且理由不同）；`catch (…Exception|Throwable)` 含注释 **69** 行、
  去注释 **49** 处；这 49 处里**挂起形状 48 + 无挂起点 1**（唯一免检那处是 `DriverSdkNavCard.kt:291`，唤起外部地图）。
  ⇒ "把 49 处一律加取消出口"是**给不存在的通路开药**，扫描面必须由内容决定。
- **三条被实测推翻的前提**（照实记，不顺着指令原文写台账）：
  ① 指令 §七 说"build a unified helper (`safeApiCall()`)" —— 实测**这个形状工程里早就有**：`apiCall`（`Api.kt:60`、
    `suspend inline`、先穿透取消、五类 typed 分支、`ApiTest.kt:165` 一条 JVM 用例），但**生产调用点 0**
    ⇒ 本轮**没有**"采用"它（接它要同时改 38 处返回形状，那是另一轮），也**没有删**它（删是减量不是收口），
    新立的 helper 定名 `attempt`（用场超出网络层）。**判据里 Z1=1 就是它自己的定义行**，"已存在但没人用"这件事从此有数。
  ② `catch (e: Exception)` **接不住** `CancellationException` 是直觉，实测**接得住**（它继承 `Exception`）⇒
    "我只捕 Exception 所以取消自然穿透"这句在 48 处里全是假话，这也是本轮 52 处改写的根据。
  ③ `Locator` 那处改完**可观测差为 0**：外层是 `withTimeoutOrNull`，它会把取消**强行折成超时**，即使内层吞了
    `TimeoutCancellationException` 也一样 ⇒ 这条**不是猜**，用一条特征化用例钉住（改前改后输出相同）。
    ⚠ 硬要写"这样更正确"就是假台账 —— 写的是"语义上不再谎报，屏幕上没有任何可观测差别"。
- **做法（三层，每层都有会红的数）**：`Api.execute` 回落面收窄成 `NetworkException` / `ApiException` 两类（TH-l 2/2/0），
  其余原样抛（TH-c 2/0/2）；`attempt{}` 落地 + 12 个调用点（TH-f 12/1）；`Repo` 38 处**逐点**配对（TH-d 38/38）；
  UI/服务侧三种收法分开（换 `attempt` / 补穿透出口 / **整圈 `runCatching` 仪式摘掉**，见 D-034 第 5 点）；
  `load()` 与 `work()` 同一轮都有取消出口（TH-i 1）；AlertEngine 两处轮询走 `attempt`（TH-j 1/1/0）；
  演示开关接线与 release 恒 `false` 钉死（TH-k 1/1）；缺陷话术与日志分开（TH-h 6/1/2）。
- **改动面清点**（与 `THIRD_PARTY_NOTICES.md` §6 F-1 段同一张清单，两条腿 + 一次作差）：
  新自写文件 **5** 个（`core/net/Attempt.kt` **24 行 / 1314 B，唯一随 APK 分发的一个**、`DemoExceptionIsolationTest` 7 例、
  `ApiCancellationTest` 5 例、`tools/check_throwable_probes.sh`、`tools/throwable_negative_controls.sh`）；
  **既有生产文件改动 17 个** = 三条 glob（`attempt {` 7 / 生效 CE 子句 4 / `if (!demoAllowed) throw e` 1）去重
  ∪ 编辑窗口 08:13–08:28 的 mtime ⇒ 并集 16，再用"注释里引用 §三十一 的文件"作差补出第 **17** 个 `WorkbenchScreen.kt`。
  ⚠ **这一步不是形式主义**：那第三种收法（整圈仪式摘掉）**同时不在** glob 清单和 mtime 窗口里，
  只按"数得出来的清单"报数就会少一个真实改动 —— 已升为 §八 ㉑ 第④条。
  依赖坐标实测仍 **20** 条、`app/build.gradle.kts` mtime **00:40:50**（本轮一字未动）、第三方源码引入 **0** 行 ⇒ 四张许可证表不动。
- **用例 12 条**（§三十 点名的两个类）：`DemoExceptionIsolationTest` **7** + `ApiCancellationTest` **5**，
  每条"缺陷/取消被错收"配一条"真失败照常工作"（否则"整条兜底删掉"也能骗绿）；
  取消用例的**诚实契约** = 类型同一性 + cause 链含探针 + `requestCount == 0`（kotlinx 边界堆栈恢复会给**同类副本**，
  所以不许写"是同一个对象"）。
- **判据与负控**：TH-a…TH-l **12 行读数 / 27 道闸门**（a 7/5 · b 4/4 · c 2/0/2 · d 38/38 · e 0‖48/1 · f 12/1 · g 0 ·
  h 6/1/2 · i 1 · j 1/1/0 · k 1/1 · l 2/2/0），`bash tools/check_throwable_probes.sh` → rc=0；
  `bash tools/throwable_negative_controls.sh` → **18 条破口**，批处理实测 **✔ 行 19**（18 + 1 条恢复自检行）、`✘=△=0`。
  **跨簿纪律照 ⑲ 的升级版执行**：本轮 `Repo.kt`/`Api.kt` 被 52 处改写命中 ⇒ **十三本负控全批复跑**（16:41–16:58 ≈ **17** 分钟）。
- **实测凭证**（细节在审计 §80 七）：全量 `--rerun-tasks` **`24 actionable tasks: 24 executed`**、
  **31 类 / 311 tests / 0 f / 0 e / 0 s**（31 份 XML mtime **17:02:02**，逐份加总）；**+12 机器比对**可归因
  （新增只有那两本，消失类 `[]`、既有 29 类一字未变）；APK **114,437,316 B** / **16:45:11**，收工复检
  **`48 tasks: 1 executed / 47 up-to-date`** ⇒ 没有重新打包，而这份 UP-TO-DATE 是"包与当前源码内容自洽"的机器证明；
  lint **0 / 46 / 32 = 78**（XML 98,753 B / 16:47:20）与上一轮逐项相同；**十三套探针全 rc=0**。
- **本轮自抓的缺陷**（判据与负控 4 条在审计 §80 六；台账自述 4 条在 D-034 末尾，这里只列名字不复述）：
  TH-e 的扫描面两版错法（关键字计数 → 手写清单 → 内容决定）、awk 字段号错位、python 往 GBK 控制台写中文导致
  `Binary file matches` 把破口清单**整段吞掉**、"免检面"必须出数；
  ✔ 条数又推算了一次（18→实测 19）、`idempotency_test` 的 ✔=0 是标记体系不同、比对基线类名写错造出假差异、
  **台账把 `WorkbenchScreen` 写成"先穿透取消"而实测是"整圈仪式摘掉"**（⚠ 这类"本来就没有 catch"的收法在任何判据里都是隐形的，
  只能靠读到自己写的假话 —— 这是本轮唯一一条**判据抓不到、只有读代码抓到**的缺陷）。
- **没做（在册尾巴，逐条都是"没做"不是"不需要做"）**：`apiCall` 生产调用点仍 **0**；`runCatching` 在 main 仍剩 **34** 处
  （本轮只收"try 首段有挂起点"那批，`WorkbenchScreen.kt:1082` 的 `callDriver` 这类纯同步形状记为**未收**而不是"已判无害"）；
  **§二十 的 Worker 重试语义没做**（真失败应回 `Result.retry` 而不是 `success`）；**§三十三 的四类失败分型没落进代码**
  （本轮落的是"缺陷 vs 失败"两类 + 取消不是失败，四分类仍只是台账口径）；`QuoteResult` 缺键塌 `0.0`、
  `Repo.optBoolean(…, true)` 两处仍在；**设备侧一字未验**（`NOT VERIFIED ON DEVICE`，五条新增未验项见 DEVICE_STATUS）；
  `release` 仍 `BLOCKED`（HTTPS 闸门，配置期 exit 1）、git 仍 `ENVIRONMENT_BLOCKED`。

### E-1 解析层自造的 ETA 别名键 `"eta"` / `"minutes"` 6 处 → 0（队列首项 / 审计 §79 / D-033 / 判据 §八 ⑳，本轮完成 · 2026-09-30）

- **这条为什么和 §38 那一族不是一回事**：`multiNumOrNull("etaMinutes", "eta", "minutes")` 不是把 `null` 说成一个数，
  而是在 `etaMinutes` 缺键时读到**一个确实存在的数** ⇒ `null → 0` 那条红线**结构上抓不到它**。
  它读起来又完全像"防御性兼容"，所以前四轮都写成"以后再说"。
- **判据问的新问题：这个键谁在写**（三方实测，全部当轮跑）：后端 `cgp-team/cargo-post-platform` 里 `"eta"` 作为
  JSON 键 **0** 处、`etaMinutes` **13** 处；小程序**两棵树**里 `"eta"` **0** 处；本机 `DemoData` **0** 处
  ⇒ 第二个候选名**没有任何生产者**，它是客户端自己起的名字，也就是**第四个真相源**。
- **做法（6 处读法收窄成单键，字段一个没删）**：`Models.kt` 车辆 `:721`、`SendOrder` `:937`、`TransportLeg` `:1056`、
  `Arrangement` `:1623` + `Repo.kt` 轨迹 `:592`、路线引导 `:962`；`RouteGuide` 的 KDoc 按实测重写（后端**从不**下发
  `etaMinutes`，那个键是导航引擎自己报文里的形状），**保留** `etaMinutes` 兜底读法但把"它是谁给的"写清楚 ——
  删掉它会让演示面少一条能力，那是功能倒退不是真实化。
- **三条新判据 + 两条同轮抬高的 pin**：`T-ai`（别名候选键写法 = **0**）· `T-ai2`（闭包：全树把 `eta` 当键名读的位置 = **0**，
  必须先有 `grep -n` 的输出形状才能排除注释）· `T-ai3`（**反诱惑**：演示面永远不许长出 `"eta"` 生产者）；
  `T-k` 12 → **≥13**、`T-x` 9 → **≥12**（与两份 XML 的 13 / 12 一致）。用例 +4：`BusEtaContractTest` 12→**13**、
  `SendEtaContractTest` 9→**12**，每条"只带别名 ⇒ 必须 null"都配一条"真实键照常工作"。
- **"0 命中"要有资格被报出来**：反证打在**改动前那棵整树副本**（`/tmp/ledger_replay`，mtime 09-29 21:18）上 ——
  同一条 grep 返回 **6**（当前树 **0**）。本轮就靠这一次反证抓到自己的正则写错（手滑成双引号 ⇒ 永不命中 ⇒ 永远绿）。
- **本轮真正的产出是五条"判据与负控自己的缺陷"被抓现行**（明细审计 §79 六）：
  ① 新正则双引号手滑（靠打旧树抓到）；② 闭包判据缺 `grep -n`（被自己写的说明注释算成 2、探针 rc=1，**修输入形状、不删注释**）；
  ③ `T-t` 负控被本轮删动作废（锚点就是被删那行）⇒ `✔=53 ✘=1` 当场报出，修锚点后 **54 ✔**；
  ④ `SR-f` 负控被 **#51** 作废且是**跨簿**（上一轮只复跑自己那本）⇒ 静默绿了一整轮，修后 **13 ✔**；
  ⑤ 台账里"负控成本 40 次 / 7 分钟"是**推算**，按 `/tmp/*_neg_*.log` 留档体数清点是 **39 次 / 约 9 分钟** ⇒
  **永久口径：次数与时长只准抄留档产物，不准抄心算**。
  ⇒ **§78 那条永久规矩被实测写窄并当场升级**：锚点是**源码文本**不是符号名，任何改写都会作废它，
  包括上一轮改的、在另一个文件里的 ⇒ **改源码文本要同轮核所有簿的注入锚点（或直接复跑所有簿）**。
- **实测**：全量 `:app:testDebugUnitTest :app:assembleDebug --rerun-tasks` → `BUILD SUCCESSFUL in 1m 34s`、
  **`43 actionable tasks: 43 executed`**、**29** 份 XML **299** tests / **0** failures / **0** errors / **0** skipped（mtime 04:21:33）；
  APK **113,863,001 B**（⚠ 与 §78 的 126,516,687 B **跨口径不可比**，本轮才有 43/43 全量口径；解释登记为"可复核"不是"已证"）；
  十二套探针全 rc=0（ETA 末行已变成长版"… + E-1 别名键归零"）；结构负控七套 `eta 54 / navigation 27 / shell_routing 13 /
  role_reconcile 13 / exception 21 / idempotency 11 / reachability 21` 全 `✘=0 △=0`；
  就地五套用例级负控（异常 X-1…X-8 / 幂等 1 / 壳路由 M-1…M-7 / 导航 M1…M8 / 角色 RRt-1…RRt-6）**实跑 39 次 gradle 调用**且逐条 ✔，
  恢复后 `cmp 一致` + `exit=0`；真实性窄 **9** / 宽 **45** / `GeoMath` **27** / `DemoData` **3** 文件 / 假步进 **0** 一字未动；
  依赖坐标 **20** 条未变、**新增文件 0 个**（四张许可证表一字未动）。
  ⚠ 上面这组是**改代码那一时点**的读数；**五本台账写完之后的终检复跑**（同一批判据、同一棵树，只多了台账）记在本文件
  **"§八 探针 E-1 复跑"**那一节 —— 十二套 rc=0、七本结构负控 `✘=0 △=0`、`TOTAL FLAGS 0`，
  且那一遍**真的拦下过一次** `flags=1`（我自己把"全量用例"那行写成三条物理行）。两遍都在册，后一遍是收口凭证。
- **没做（在册尾巴）**：其余 `Minutes` 家族第二候选名 **5 处**（`Models.kt` 479 / 729 / 1110 / 1328 / 1802）**一处没动** ——
  逐端点重核契约是下一轮队首，**不许把 E-1 的结论抄过去**；`QuoteResult` 的"缺键折成 `0.0`"同类形状仍在；
  `Repo.optBoolean(…, true)` 两处仍在（`Repo.kt:716 inService` / `:689 located`）；**设备侧一字未验**；
  `release` 产物仍 `BLOCKED`；git checkpoint 仍 `ENVIRONMENT_BLOCKED`。

### #51 旧 `DriverNavScreen` 降级为 DEBUG ONLY + §24 假步进 6 → 0（D-003 执行 / 审计 §78 / D-032 / 判据 §八 ⑲，本轮完成 · 2026-09-30）

- **本轮最值钱的一条认识**：**"删掉假导航"和"保住导航能力"是有先后顺序的两件事**。动手前 `grep -rn "DriverSdkNavCard("`
  量到全工程**只有 1 个生产调用点，而它正好挂在要被关掉的旧屏里** ⇒ 直接上闸会让 release 失去唯一能驱动本机高德引擎的入口，
  而且**失去得很安静**：编译过、十二套探针绿、295 例全绿，少的是一整个功能。
  ⇒ 收法是把"生产调用点"编成会红的数（N-g 第六位由"≥1"重锚成"`WorkbenchScreen.kt` 里**恰好 1**"）。
- **契约复核读出的三条前置事实**（决定这一轮长成什么样）：① 小程序司机"导航"的语义是**交给外部地图**
  （`wx.openLocation` / `openAmapNav`，`utils/nav.js`、`routes.js:160`）⇒ 只留内嵌引擎不算对齐，交接必须在 release 里活着；
  ② `nextNavigableStop(guide)` 是唯一的目的地口径 ⇒ 交接按钮不许自选点；
  ③ **`TransportLeg` 只有站名、零坐标字段**（§36 在册偏差本体）⇒ 线路页那个入口在 release 里拼不出
  `destination=lat,lng`，**只能 DEBUG-only 并把理由写在代码旁边**（功能少一条比功能假一条好）。
- **做法（降级，不是删除）**：文件本体 / `Routes.DRIVER_NAV` 常量 / `onNav`+`onOpenNav` 形参全留；
  **物理删除的只有那套假步进机器**（`var stepping` + 4 秒 `LaunchedEffect` 循环 + DEBUG"逐条推进"开关），
  `next()` → `showNextStep()`（只翻页，不推进车的位置与班次状态）；正式导航面重挂工作台
  （`DriverMissionMap` + `DriverSdkNavCard` + 卡片底部"交给高德 / 交给百度"），`openAmap/openBaidu/view` 三个 helper
  **搬进** `DriverSdkNavCard.kt`（旧屏里归零，判据 N-p 0/1/1/0）；三处入口 + 路由注册上 `BuildConfig.DEBUG` 闸，
  外加组件自守 `if (!BuildConfig.DEBUG) { DebugEntryClosed(); return }`（**两道闸不是三道**：不拆 debug source set，
  因为"搬文件"与"不删除文件"这条硬规则冲突）。文案三处标注"非真实导航 / 车道箭头是按名字猜的"（N-o 2/1）。
- **闸与用写成同一物理行**（判据形状倒逼的写法）：`if (BuildConfig.DEBUG) composable(Routes.DRIVER_NAV) { DriverNavScreen() }`。
  跨行的 `if { … }` 单行 grep 判不出"每一处使用点都带闸"。代价是几行偏长，收益是**拆闸必须改那一行、改了就红**。
- **§24 归零 + 口径同时抬高**：假步进那条判据由"只看旧屏一个文件"抬成 **整个 `ui/driver` 目录**含注释裸计数（新判据 N-m），
  **九条白名单一字未改**。⚠ 归零当轮第一次量到的是 **2**，命中的是本轮新写 KDoc 里的字面 `stepping` 与 `delay(4_000`
  ⇒ 改**措辞**、不改口径（与"0 命中有三种失效"同族而方向相反：**判据抓到写反的注释，说明它在起作用**）。
- **判据与负控**：导航簿 **12 → 16 条**（新增 N-m `0` / N-n `0/1/1` / N-o `2/1` / N-p `0/1/1/0`，全部实测在目标），
  结构负控 **18 → 27 条破口**（`✔ 基线：副本原样 exit 0` + **27 ✔ / 0 ✘ / 0 △**；新增九条里最值钱的三条：
  定时器塞回引擎 / 注册处去闸 / 交接目的地换成 `Geo(0.0, 0.0)`）；
  **用例级负控照跑**（基线 `exit=0 failures=0/19` → M1…M8 八次全红且红在 `NavigationContractTest`、M5 红 4 例、其余各 1 例
  → `cmp` 恢复逐字节相同 → 复跑 `exit=0 failures=0/19` → `RC=0`）——
  **新立永久规矩（§八 ⑲ ③）：不得用"改动范围与判据范围不相交"当跳过负控的理由，除非那个不相交本身是实测出来的**
  （本轮该文件 mtime 01:34 晚于上次实跑，mtime 证不了"上次跑时的树就是现在这样"）。
- **实测证据（全部当轮实跑）**：`:app:compileDebugKotlin` rc=0（1 executed / 14 up-to-date）；
  **十二套探针全部 rc=0**（末行逐条：`分域判据通过 / 契约信封判据通过 / ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径）/
  §54 异常上报判据通过 / 幂等判据通过 / 定位与上报判据通过 / 第 8 步导航引擎判据通过 / 可达性判据通过 / 角色同源判据通过 /
  壳路由判据通过 / 状态映射判据通过` + `check_authenticity_counts.sh` 打的数：窄 **9** / 宽 **45** / `GeoMath` **27** /
  `DemoData` **3** 文件 / **假步进 0**）；
  全量 `:app:testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL / RC=0 / 24 actionable tasks: 24 executed**，
  **29 份 XML mtime 全为 03:07:26**、逐份读出相加 = **29 类 / 295 tests / 0 failures / 0 errors / 0 skipped**
  （`NavigationContractTest` 19 / `ShellRoutingContractTest` 10 / `DomainSplitTest` 7 / `RoleReconcileTest` 5 逐份复核，**本轮一条用例都没加也没删**）；
  ⚠ 这一段中间有一次**读数被自己的工具链洗掉**：用例级负控的 `--tests` 过滤跑完，`test-results/` 目录里**只剩 1 份 XML**
  （过滤跑会清目录），先前那次 `295` 的读数失去产物支撑 ⇒ 处理是**重跑全量**而不是沿用旧数，
  也不是拿第一次那次 `1 from-cache / 23 up-to-date` 当"重跑过"（缓存还原的产物只证输入哈希相同）。
  APK **126,516,687 B** / mtime **02:52:37**（本轮真重打包那一次；随后只有负控的"改—恢复"，`cmp` 已证逐字节相同，
  故末遍 `--rerun-tasks` 未重打包，APK 与当前源码自洽）；依赖坐标实测仍 **20** 条，**本轮新增文件 0 个**（§1–§4 四张许可证表一字未动）。
- **尾巴（在册，不得当作已解决）**：① **闸门本身零真机验证** —— 正式包点旧入口是否真只看到 `DebugEntryClosed` 那一屏、
  外部地图在装了/没装高德 App 两条分支、目的地为空时按钮禁用态在窄屏是否折行，全 `NOT VERIFIED ON DEVICE`（本机无设备在环）；
  ② **线路页 release 导航入口没做**，解锁条件是后端补段几何或站点坐标（§36/§44 同一条路），**不许**拿站名反查补；
  ③ §77 二那两条单位/语义口径一字未改，仍未验；④ `release` 产物仍 `BLOCKED`（https baseUrl → keystore）、
  git checkpoint 仍 `ENVIRONMENT_BLOCKED`。



**这一轮真正的产出不是"接了高德"，是把"接了高德"这件事拆成 19 条判据层用例 + 12 条判据 + 两类负控，
并且把上一轮遗留的那句 BLOCKED 换成了一句可以核对的真话。**

一、**开工先量的三件事**（全部当场实测，不采信台账转述）：
1. **导航类到底在不在包里**：上一轮在册的是 `3dmap:10.0.600` jar 里 `com/amap/api/navi/` **零类** ⇒ 那条 BLOCKED 是真的；
   本轮换成 `com.amap.api:navi-3dmap:10.0.600_3dmap10.0.600`（jar **50,991,398 B**、2,403 条 / 104,351,090 B 未压缩），
   两份 pom 都是 `<packaging>jar</packaging>`（⚠ 别按 AAR 解释：`.so` 是 jar 内 `lib/<abi>/` 带进来的）。
2. **API 到底长什么样**：对**实际解析到的那份 jar** 逐个 `javap`（取证留在 `C:/temp/amap-navi-probe-20260930/`），
   核对过的签名只有：`AMapNavi.getInstance(Context)`（`throws AMapException`）、`add/removeAMapNaviListener`、
   `calculateDriveRoute(NaviPoi, NaviPoi, List<NaviPoi>, int)`（五个重载里只取这一条，返回 boolean = **受理**不是成功）、
   `getNaviPath()`、`AMapNaviPath.getCoordList()/getAllLength()/getAllTime()`、`startNavi(int)`、`stopNavi()`、
   `NaviInfo.getCurStep()/getPathRetainDistance()/getPathRetainTime()/getCurStepRetainDistance()/getCurrentRoadName()/getNextRoadName()/getIconType()/getRouteRemainLightCount()`、
   `AMapCalcRouteResult.getErrorCode()/getErrorDescription()/getErrorDetail()`、`NaviPoi(String,LatLng,String)`、
   `NaviType` 是 int 常量（NONE/GPS/EMULATOR/CRUISE）。
   ⚠ **`AMapNavi` 没有公开 `isInited()`**（`grep -i init javap_AMapNavi.txt` **0 命中**）⇒ "回调还没回"在 SDK 层问不出来，
   所以就绪态必须是**三态**而不是布尔；编一个 `isInited()` 就是硬规则禁止的"自造 API"。
3. **策略取哪一个有取证**：`javap -v` 扫常量池的 `Deprecated` 属性 —— `DRIVING_DEFAULT` / `DRIVING_SHORTEST_DISTANCE` /
   `DRIVING_MULTIPLE_PRIORITY_SPEED_COST_DISTANCE` / `DRIVING_MULTIPLE_SHORTEST_TIME_DISTANCE` **都带**，
   `DRIVING_AVOID_CONGESTION`(=4) **没带** ⇒ 取它；多路径不取（`getNaviPath()` 读回的是"选中那条"，选路口径真机未验，
   不引入未验证的分支）。

二、**落地形态与边界**（"宁可暂时不能导航，不允许假导航"在这一层的具体形状）：
- **两层**：`core/navigation/NavigationEngine.kt` 是**纯判据层**（零 `android.*`、零 `com.amap.*` —— 本机没有 Robolectric，
  判据只有抽成纯函数才可能被 JVM 覆盖）+ `AmapNavigationEngine.kt` 是**全工程唯一**碰 SDK 的文件（N-j 9/0/0）；
- **推进只来自回调**：`readiness` / `guidance` 都是 `StateFlow`，SDK 推、页面 `collectAsStateWithLifecycle` 订阅
  （N-h 2/2/0，**轮询 0**）；`onNaviInfoUpdate` 是唯一写 `guidance` 的地方 —— **没有定时器、没有 `stepIndex + 1`**（N-b 0）；
- **一道闸门**：算路结果只走 `naviRouteCheck` → `RealRoadRouteValidator.validate(sourceRaw=AMAP, crsRaw=GCJ02)`
  （N-e 1/0/1），SDK 层**自己不许判**（自判 = 0）；拿不到就回 `Rejected` 带真实原因，
  不用后端 `routeGuide` 的折线、不用站点连线顶替；
- **坐标只搬运**：`getCoordList()` 本身就是 GCJ-02 ⇒ `geoPoints()` 只做 `NaviLatLng → Geo`，
  这条路上**不许**再套 `Locator.toGcj02` / `CoordinateTransform`（N-c 0）；
- **一次只飞一个请求**：回执是进程级单回调，只有一个等待槽，并发算路直接回"已有一次算路在等高德回执"
  （超时冒充引擎故障是一种不实）；`CALC_TIMEOUT_MS = 20_000L` 是**算路**上限，**初始化**没有上限 ——
  那个数要真机才定得出来；
- **工厂只有两种返回**（N-f 1/1/2）：可用给真引擎，不可用给 `BlockedNavigationEngine`（每个出口回同一句真实原因、
  `guidance` 恒 null）。**没有第三种实现** —— 有第三种，就会有人拿"直线 + 定时器"冒充它；
- **`release()` 不调 `AMapNavi.destroy()`**：它是进程单例，页面退出就销毁会把下一次算路一起带走；
- **`nextNavigableStop` 不跳过 FAILED 段**：司机要能开到异常点处理，跳过它等于替后端决定"这段不用去了"；
- **调用点** `ui/driver/DriverSdkNavCard.kt`（N-g 六类调用点各 ≥1）：`DriverNavScreen` 的转向**仍只读后端下发折线**，
  SDK 产物住在卡片里 —— **本机引擎不取代后端 `routeGuide` 那条线路事实**（N-l 钉旧文案"无需地图 SDK"残留 0）。

三、**证据（2026-09-30 实测）**：`NavigationContractTest` **19** 例全绿；全量 `--rerun-tasks` **29 类 / 295 tests /
0 failures / 0 errors / 0 skipped**（**24 actionable tasks: 24 executed**，29 份 XML mtime **02:00:02**）；
**12 套探针全 rc=0**（新簿 navigation 末行"第 8 步导航引擎判据通过"）；结构负控 **18 ✔ / 0 ✘ / 0 △**；
用例级负控 **M1…M8 全 ✔**（基线 `exit=0 failures=0/19` → 八次破口全部 `exit=1` 且红在 `NavigationContractTest`，
M5 红 4 例、其余各 1 例 → `cmp` 证与基线**逐字节相同** → 复跑全绿）；
`assembleDebug` rc=0（APK **126,516,687 B** / 01:12:54）、`lintDebug` rc=0（**0 error** / 46 Warning / 32 Information），
`core/navigation/*` 与 `DriverSdkNavCard.kt` 在 lint XML 里 **0 点名**；
dex 描述符口径（**15** 个 dex）：`AmapNavigationEngine;` **1** / `NavigationEngine;` `NavigationEngines;` **2**、
`BlockedNavigationEngine;` **1** / `DriverSdkNavCardKt;` **1**（⚠ 扫不带 `Kt` 的名字会得 **0** 并误判成"没打进包"）/
SDK 侧 `AMapNavi;` **3**、`model/AMapNaviPath;` **3**、`NaviInfo;` **3**、`NaviPoi;` **3**、`AMapCalcRouteResult;` **3**、
`SimpleNaviListener;` **3**、`enums/PathPlanningStrategy;` **1**；地图类与着色器资源仍在包里
（`maps/MapView;` **2**、`maps/AMap;` **3**、`assets/amap_sdk_shaders` **5** 条）。
**本轮新增/改动文件**（按 CONTEXT_LOCK 纪律逐条登记，均为本项目自写、不随包分发第三方源码）：
新增 `app/src/main/java/com/shanxiang/buspost/core/navigation/NavigationEngine.kt`、
`…/core/navigation/AmapNavigationEngine.kt`、`app/src/main/java/com/shanxiang/buspost/ui/driver/DriverSdkNavCard.kt`、
`app/src/test/java/com/shanxiang/buspost/core/navigation/NavigationContractTest.kt`、
`tools/check_navigation_probes.sh`、`tools/navigation_negative_controls.sh`、`tools/navigation_test_negative_controls.sh`；
改动 `app/build.gradle.kts`（依赖顶替，坐标数仍 **20**）、`ui/driver/DriverNavScreen.kt`（只改**声明**，假步进 6 处**未动**）、
`core/map/RealRoadRouteValidator.kt`（拒绝文案跟随来源）⇒ 同步 `THIRD_PARTY_NOTICES.md` §3（真实版本 + AMap 专有许可证）与 §6。

四、**尾巴（不许写成已修）**：① **运行时零验证**（`verify_device.sh --no-build` 实测 **exit 1**、设备列表空）：
`onInitNaviSuccess` 回不回、静默不回调持续多久、`getPathRetainDistance/Time` 的**单位**、`getCurStep()` 的**语义**、
后台定位、TTS、导航视图，全 `NOT VERIFIED ON DEVICE`；② **初始化等待上限故意没有** —— JVM 层分不清
"还在初始化"与"永远不回来"，编一个数就是假判据；③ **包内高德原生库换了名字**
（`libAMapSDK_NAVI_*` + `libneonui_*` 取代 `libAMapSDK_MAP_*`；`lib/` 解压 **77,129,060 B**，
其中导航 `.so` **68,537,884 B**）⇒ **地图还能不能渲染只有真机能判**，`BusMap` / `DriverMissionMap` 重新回到未验状态；
APK 较换依赖前 +68,575,313 B（≈ +68.6 MB，**跨轮对照、非受控 A/B**）；④ 专有许可证（`The AMap Software License,
Version 1.0`）随包分发这条义务已回填 `THIRD_PARTY_NOTICES.md` §3，**不等于**许可证条款已被逐条审阅；
⑤ `#51` 旧屏转 DEBUG ONLY、§24 假步进 6 处、`release` 产物、git checkpoint **一律未动**。

### V-2b 冷启动角色与后端画像同源（指令 v2.0 对表新增 / 审计 §76 / D-030，本轮完成 · 2026-09-30）

**这一轮真正修掉的不是"少一次请求"，是一条把后端唯一的否定形状排除在判据之外的前置条件。**

一、**开工先核的四方事实**（全部当场读源码，不采信台账转述）：
1. 撤档账号后端回的是 **`{"code":0,"data":null}`**：`DriverAppServiceImpl.profile():167-181` 取 `currentDriverOrNull()`，
   为 null 就 `success(null)`；`currentDriverOrNull():1746-1756` 三条空路径（memberId 为空 / mobile 空白 / 查无司机行）。
2. `/driver/profile` **要鉴权**（`AppDriverController.java:41-44` 这个端点没有 `@PermitAll`）⇒ 它回 401 走的是 `Api` 的
   会话失效分支，与"这账号不是司机"是两回事，不许混进否定。
3. **Android 侧把 `data:null` 读成空对象而不是 null**：`Api.kt:314-315`
   `if (data == null || data === JSONObject.NULL) return Payload(JSONObject(), null, demo = false)`
   ⇒ 调用方观测到的是 `obj` 非 null、`length()==0`、`driverId=0`、`demo=false`、`error=null`。
4. **小程序对同一端点是 `if (!profile) … return`**（`pages/driver/workbench/workbench.js:155-175`；
   `utils/api.js:66-96` 直接 resolve `body.data`）⇒ 三方对齐里 **Android 是偏离的那一方**，
   后端并没有"少给一个字段"，所以本轮**没有**新造契约，只改判据。

二、**本轮自己立、本轮自己拆的设计**：第一版让 `Repo.driverProfile()` 返回四态 `ProfileAnswer`，纯函数多收一个
`deniedByServer`。读完 ①③ 之后发现"这轮算不算有答复"**调用方当场就能观测**（`failed` / `demo` / `driverId`），
中间类型是把 `Repo` 内部的判定搬到调用方再判一遍 —— **整块回退**，`One<DriverProfile>` 原样保留。
实测现在全工程 `ProfileAnswer|deniedByServer` **0 命中**。
留下的教训：**判据的入参必须是能观测的量**；要调用方猜的中间态一进门就是第二个真相源。

三、**被那条前置条件挡住的两个真实缺陷**（都是本轮才现形）：
1. `Repo.bindDriver()` 的 `if (!p.demo && o.length() > 0)`：`data:null` 恰恰是 `length()==0` ⇒ 清绑定分支**不可达**，
   **撤档在客户端永远不生效**，本机 `Prefs.driverId` 会一直留着上一个账号的值并继续拿它拼司机请求。
2. 工作台即便拿到"没有档案"也照发 `shifts / pickups / tasks / routeGuide`：`driverShifts()` / `driverPickups()`
   **不走** `driverIdParam()` 守卫（后端按登录会员识别身份，小程序 `utils/api.js:313-324` 同样不传 `driverId`）
   ⇒ ⑥ 那套分域守卫**结构上挡不住这两个接口**，只有早退挡得住。本轮之后：撤档账号**实测 `server.requestCount = 1`**。

四、**落地形态**（"后端没有的东西安卓可以自己写，但只能写措辞不能写契约"这条纪律的具体执行 —— 本轮连措辞都没新增，
错误文案仍复用 `DRIVER_UNBOUND`）：
- `nav/ShellDecision.kt` 两个 `internal` 三态纯函数（沿用 V-2 的抽取理由：无 Robolectric ⇒ 决策抽成纯函数才有 JVM 覆盖）：
  `serverSaysDriver(driverId, failed, demo)`（**失败与演示都返回 `null`=未知**，`未知不等于否定`）、
  `reconcileRole(stored, server)`（**只降级、不升级** —— 硬规则 1）；
- `Repo.driverProfile()` / `Repo.bindDriver()` **同一端点只允许一条判据**：`driverId>0 → 写入`，否则 `!p.demo → clearDriverBinding()`；
- `WorkbenchViewModel.syncFromServer()`：`档案 → 判态 → reconcileRole → setRole → 否定则清 profile/shift/pickups/tasks/guide、
  清 Running.shiftId 与 Prefs.driverShiftId、error=DRIVER_UNBOUND、demoted=true、return`（实测行号 180/193/196，RR-c 钉相对次序）；
- @Composable 侧出口 `LaunchedEffect(vm.demoted)`：**先 `DriverLocationService.stop(context)` 再 `navigate(USER_SHELL)` +
  `popUpTo(DRIVER_SHELL){inclusive}`** —— 沿用 `DriverShell` tab-3 那条既有跨域写法，**没有新增第三种跨域点**；
- **两条不许越界的分支**（各有用例）：档案**失败**时不降级、不少发请求、不清绑定（一次抖动不许把跑班司机锁在司机壳外面，
  用例 `档案接口失败时不降级也不少发请求` 实测 requestCount 仍 **5**、角色仍 DRIVER、`demoted=false`）；
  档案**授予**身份时也不升级角色（`档案授予身份时不把用户角色升级成司机`：角色仍 USER，但 `Prefs.driverId` 被正当写入）。

五、**证据（本轮实测）**：`:app:compileDebugKotlin` **BUILD SUCCESSFUL / 11s**（1 executed / 14 up-to-date）；
收口全量 `:app:testDebugUnitTest --rerun-tasks` **24 actionable tasks: 24 executed**，28 份 XML mtime 全为 **23:56:47**
⇒ **28 类 / 276 tests / 0 failures / 0 errors / 0 skipped**（**+2 类 / +9 例逐条可归因**，`DomainSplitTest` 与
`ShellRoutingContractTest` 条数未变）；**11 套探针全 exit 0**（含新簿 RR，见上面"§八 探针 V-2b 复跑"）；
负控结构 **13 ✔ / 0 ✘**、用例 **RRt-1…RRt-6 全 ✔**；`:app:assembleDebug` **真重打包**（`packageDebug` executed，
APK 23:59:33 / 57,941,374 B）；`:app:lintDebug` **真重跑**（8 executed，**0 error / 46 Warning / 32 Information**，
本轮改动的五个文件在 lint XML 里 **0 点名**）；dex 描述符口径 `Lcom/shanxiang/buspost/nav/ShellDecisionKt;` **3** /
裸名 `serverSaysDriver` **2**、`reconcileRole` **2**、`demoted` **1**、`DriverUnboundException` **1**。
**本轮新增文件（按 CONTEXT_LOCK 纪律逐条登记，均非三方代码）**：
`app/src/test/java/com/shanxiang/buspost/nav/RoleReconcileTest.kt`、
`app/src/test/java/com/shanxiang/buspost/ui/driver/WorkbenchRoleReconcileContractTest.kt`、
`tools/check_role_reconcile_probes.sh`、`tools/role_reconcile_negative_controls.sh`、
`tools/role_reconcile_test_negative_controls.sh` ⇒ `THIRD_PARTY_NOTICES.md` **本轮零新增三方代码，不改动**。

六、**尾巴三条（不许写成已修）**：
① **真机半边未验**：`adb devices` → `List of devices attached` 空；`bash tools/verify_device.sh --no-build` 实测
   `[STOP] 没有处于 device 状态的设备。` **exit 1** ⇒ 撤档账号在**真实后端**上的冷启动行为与 `buspost://` 深链落地仍是
   `NOT VERIFIED ON DEVICE`，本轮只有 JVM + MockWebServer 证据；
② **`assembleRelease` 从未产出过 release APK**：本轮第一次实跑即被工程自带的 HTTPS 闸门挡在**配置期**
   （`app/build.gradle.kts:171`：`release 包必须使用 HTTPS（当前 http://1.15.29.107/api）`，exit 1）。
   **没有用 `-Pbuspost.allowInsecureRelease=true` 绕过**（那正是"release 关闭 cleartext HTTP"要拦的东西）。
   ⚠ 更正一处我本轮先写下的错话："四本台账从没记过这条闸门"**不成立** —— 闸门在本文件"生产化阶段"、
   `DECISION_LOG.md:85`、审计 §1104 三处都记过；**真正没记的是"release APK 从未产出过"**与开关名
   `-Pbuspost.allowInsecureRelease`（`grep` 四本台账 **0 命中**）⇒ 本轮补记为 BLOCKED，不记通过；
③ **降级时机仍在"进壳之后"**：复核点在 `WorkbenchViewModel.syncFromServer()`，不是 `startDestinationFor` 之前
   （RR-c 实测两个纯函数各只有 **1** 个调用点）⇒ 撤档残留 DRIVER 的人**仍会先落进司机壳一次**再被弹回用户壳。
   要做到"冷启动第一眼就在用户壳"得在导航图之外先起一次网络往返，那是**第二次机制改动**，本轮不做、也不谎称做到。
### V-3 §54 司机异常上报（指令 v2.0，本轮完成 · 2026-09-29）

**第一步是读后端，不是画页面**（§54 原话："先读取真实 enum，不要自己发一套后端无法接受的 type"）。六条实测事实，
全部来自 `cargo-post-platform` 当前源码（文件:行号当场 `grep -n` 得到，不是照台账抄）：

1. **异常类型 enum 不存在** —— `yudao-module-transport` 全模块找 `ExceptionType`/`AbnormalReason`/异常码表：**零命中**。
   所以 §54 要的"真实 enum"**后端没有**，不是我没找到。
2. **服务层早就有这条能力，控制器没接出来**：`DriverAppServiceImpl.java:1498` 是
   `case "exception" -> reportLegException(leg, reqVO.getRemark())`（段置 99、订单置 `EXCEPTION(13)`、记 `ORDER_EXCEPTION`
   事件、向管理端与货主双向推通知 —— 审计 §21/§41 当年据此记的是 `MISSING`：**端点在 controller 里不存在**）。
   本轮按"后端没有对应的东西安卓可以自己写，只是要符合架构要求"**补了两行**：
   `AppDriverController.java:226-230` 的 `@PostMapping("/leg/exception")` → `driverAppService.legAction("exception", reqVO)`，
   **复用既有 `@Valid AppDriverLegActionReqVO`，不新增字段、不新增服务层逻辑**。
   ⚠ **该改动跨仓、未提交（本工作区无 git）、未部署** ⇒ "异常真落库/真推通知"**端到端未验**，见下面尾巴①。
   其余动作是**一个动作一个 endpoint**（`/leg/accept` … `/leg/complete`，`:163-224`）⇒ **动作名走 URL path，不在报文里**
   （Android 侧 `Repo.kt:1494`：`Api.post("$T/driver/leg/$action", driverIdParam() + mapOf("legId" to legId) + extra)`）。
3. 请求契约 `AppDriverLegActionReqVO.java:9-23` 是**四个键**：`driverId` / `legId`（**唯一 `@NotNull`**）/ `photoUrl` / `remark`，
   且 `remark` 的 `@Schema` 原话就是"备注（**异常/争议原因**）"—— §54 的自由文本**有既有落点，不是新造字段**。
   异常上报发其中**三个**（`driverId + legId + remark`，**不带** `photoUrl`：异常动作不需要凭证照片）。
   ⚠ **本轮在这条上错过一次数**：登记时写成"VO 里有 `action` 键"，实际 **VO 没有 `action`**（它在 path 上）——
   契约键名与位置必须**逐字段读文件**，不能按 `Repo.legAction(action, …)` 的形参名倒推。
   EX-c 数的是**报文体**里的键，所以 path 上有 `exception` 不会让它变红 —— 这正是它该有的行为。
4. **`remark` 的真实落点是两张 `varchar(500)`，不是 `transport_leg.remark`**（细节与推导在审计 §75 事实 4，这里只抄结论）：
   `reportLegException` 把文本拼进 `orderEventService.record(...)` → `transport_order_event.detail varchar(500)`，
   和 `sendToAdmin(...)` → `transport_user_notification.content varchar(500)`；
   而 `MultiLegServiceImpl` 里 `remark` **零命中**（`applyLegStatus:933` 只写 `status` / `actual*`）——
   ⚠ **上一轮"`transport_leg.remark varchar(255)` 是落点"的判断本轮被证伪**，登记在册，别再抄回去。
   客户端预算取 **200**：越界不是截断，是严格模式 **MySQL 1406 → 整笔事务回滚**（"报了异常，段状态也没改成")。
5. `TransportLegStatusEnum` 的 `canTransit:58-66` 对**同码幂等放行** ⇒ 99→99 **不会拒**，
   重复上报真能写进去，所以"已异常段不再重复上报"**只能客户端守**（不是多余的防御）。
6. `99 异常` 的后续是**调度重排**，客户端没有"重试到 0"的合法路径（`docs/STATUS_MAPPING.md` §4 本轮补了这一条口径）。

**形态决策（D-029）**：做**前端 7 类分类 + 自由文本 `remark`**，`remark` 里带上前缀成"分类：细节"的可读文本；
**不新增 endpoint、不新增字段、不改 URL 口径** —— 这是"后端没有的东西安卓可以自己写，只是要符合架构要求"的边界：
可以自己写**措辞与守卫**，不能自己写**契约**。上限 `EXCEPTION_REMARK_MAX = 200` 的推导写在注释里
（`varchar(500)` 减服务层前缀 ≈40 减余量），超长的后果不是截断而是 **MySQL 1406 整事务回滚**，所以必须客户端先截。

**实现（4 个新文件 + 3 个脚本，全部本轮自写）**：
`ui/driver/DriverExceptionReport.kt`（`internal` 纯决策：7 类 + `buildExceptionRemark` + `exceptionDetailBudget`，零 Compose 依赖）/
`ui/driver/DriverExceptionScreen.kt`（页面，出网**只有一条路** `Repo.legAction(LegDriverAction.EXCEPTION.api, legId, mapOf("remark" to remark))`
⇒ V-1 闸门与 401 处理不可能被绕）/ 两处入口（工作台卡片 + 线路流水口 `RoutesScreen`，均过 `canReportException`）/
`app/src/test/…/DriverExceptionReportTest.kt` **7 例** + `DriverExceptionContractTest.kt` **6 例**（MockWebServer 真报文：
`requestCount` 断言、只有 `remark`、失败与超时的诚实分支）/
`tools/check_exception_probes.sh`（EX-a…EX-i，9 条 21 个判定）+ `tools/exception_negative_controls.sh`（21 条结构破口）+
`tools/exception_test_negative_controls.sh`（X-1…X-8，**就地改源码再原样恢复**，要跑 gradle，一次只跑一个）。
**许可证台账**：以上 7 个文件**全部本轮自写**，未复制任何第三方源码片段、未引入新依赖
⇒ `THIRD_PARTY_NOTICES.md` **本轮零条目**（指令 §100 的口径：只登记实际提取/引入的东西，不登记"我们自己敲的"）。

**诚实性（"宁可没有上报，不允许假上报"）落成了四条可核对的点**：
① 未发给后端的两条路径（闸门拦下 / 无段可选）+ 请求异常，回执文案都是**"未发给后端"**（EX-g 数到 5 处）；
② POST 之后**必回读** `Repo.driverLegs()`，回读不是 99 就说**"可能未被接受，请下拉刷新核对"**（不是"上报成功"）；
③ 全页面**没有**"上报成功"字样（EX-g 谎报 = 0，用例逐条 `assertFalse(contains("上报成功"))`）；
④ 本机**不改段状态**（EX-h `copy(status=` 归 0），"异常已生效"这句话只能来自后端回读。

**判据与负控**（数字全在本文件"§八 探针 V-3 复跑"与"常驻自检"的 V-3 行，这里只留两条**踩坑**）：
- EX-i 第一版数 `canReportException` 出现次数（页面 10 次）⇒ **把四道守卫全删，探针照样 exit 0**。
  改成钉四条生效代码的确切字面量各 = 1，负控才能一条条打红。**"关键字计数"型判据等于没判**。
- `exception_test_negative_controls.sh` 的 **X-4**（把上限抬到 2000）**第一次跑全绿** ——
  因为我的截断用例写的是 `assertEquals(EXCEPTION_REMARK_MAX, remark.length)`，**拿被自己约束的常量当期望值 = 同义反复**。
  改成字面量 `200` 后 X-4 才真的红。**判据的红必须由"坏代码"触发，不能由"数字变了"触发。**

**四条尾巴（如实登记，不在本轮修）**：
① **整条链路端到端未验**：本轮补的 `/leg/exception` **未提交（本工作区无 git）、未部署**，且没有后端在环 ——
   所以"异常真转 99、真推通知"目前只有**契约测试里的 MockWebServer 报文**为证。
   ⚠ 引用本节时**不许**说成"上报已跑通"；页面文案也照此口径（只说"后端回读：第 N 段「异常」"，**不说**"调度已收到"）。
② **`remark` 的落库位置是**读源码**得到的（两张 `varchar(500)`，见上面事实 4），不是运行时验证得到的** ——
   没有后端在环，所以"事件表里真有那行 detail、通知表里真有那条 content"仍是**未验**；
   客户端预算 200 是按"前缀最坏约 40 字"推的，**前缀真实长度只有跑一次真后端才能确认**。
③ **"报给调度"的能力在服务层是有的**（段 99 + 订单 `EXCEPTION(13)` + `ORDER_EXCEPTION` 事件 + 管理端与货主双向推通知，
   审计 §21 实测），**但那是后端的既有实现，不是本轮新增**；异常**类型码表**后端仍没有，
   要不要造由后端决定 —— 客户端不自造接口（硬规则：不重新设计后端）。
④ 深链 `buspost://app/driver_exception` 的**真机落地未验**（与 V-2 同一条 ENVIRONMENT_BLOCKED）。

### V-2 壳路由与分域闸（指令 v2.0 Phase 1 验收，本轮完成 · 2026-09-29）

**开工时先核的现状（四条，全部当场 `grep` 实测，不是照台账抄）**：

1. Phase 1 那句"司机端不能看到用户首页"的**判定地点是 `NavHost` 的导航图**（`BusPostRoot` 里那一串 `composable(...)` +
   `startDestination`），而工程**没有 Robolectric** ⇒ JVM 用例渲染不出导航图。登记时写的"0 条用例"确实还是 0：
   `USER_SHELL|DRIVER_SHELL|startDestination` 在 `app/src/test` **命中 0**。
2. 两条决策原先**都是内联在 @Composable 里的**：`val start = when { !Prefs.loggedIn -> … }` 和文件级私有
   `deepLinkAllowed(route)` —— 不是"有函数没测"，是**根本没有可调用的函数**。
3. 全工程的**跨域导航点只有 2 处，且两处都是故意的**：`ui/user/MineScreen.kt:112`（进司机端入口，先 `Repo.bindDriver()`）
   与 `ui/driver/DriverShell.kt:39`（"用户版"出口，`AppState.setRole(Role.USER)` + 导航）。
   ⇒ 任何"用户目录不许出现 `DRIVER_SHELL` 字样"式的判据都会把真实业务判成违规，**判据必须给这条出口留名额**。
4. 登记为第三条行为的"`Prefs.role` 与后端档案不一致时以档案为准"**已经有主**：
   `DomainSplitTest.切换司机模式先过后端档案`（`driverId<=0` 不回调、角色不切、`Prefs.driverId` 清零；
   给了档案才切角色）。本轮**没有再写一遍**——两份判据日后必然一处改一处不改。

**修法（结构收敛，UI 一行没动）**：新建 `app/src/main/java/com/shanxiang/buspost/nav/ShellDecision.kt`，
两个 `internal` 纯函数：

- `startDestinationFor(loggedIn: Boolean, role: Role)`：**未登录永远 `LOGIN`**，即使本机残留 `role=DRIVER`；
- `deepLinkTarget(route: String, role: Role): String?`：返回 `null` 表示"留在当前页"。三道判断**故意不合并**——
  两个壳路由不接受深链（进壳只由角色决定）；`Routes.DRIVER_ONLY` 在用户会话拒绝；**陌生路由放行**，
  由 `NavHost` 抛、调用方 `runCatching` 兜（在这里静默吞掉会变成"点了通知没反应"，比崩更难查）。
- 两条都**只读 `Prefs`，不读 `AppState.role`**：后者本身是前者的镜像，多引一个源就会让
  "冷启动落在哪个壳"和"当前页面认为自己是哪个壳"有两个答案。

`BusPostRoot` 的改动只有 **+2 行 import、2 行调用**（`val start = startDestinationFor(Prefs.loggedIn, Prefs.role)`、
`deepLinkTarget(route, Prefs.role)?.let { runCatching { nav.navigate(it) } }`），旧私有 `deepLinkAllowed` 删除，
行为逐条等价。顺带修了 `Routes.kt:34` 那条 KDoc —— 它还指着**已经不存在的** `BusPostRoot.deepLinkAllowed`
（**过期注释也是假台账**，尤其是指路牌式的"见 X 函数"）。

**判据（新脚本 `tools/check_shell_routing_probes.sh`，SR-a…SR-h 共 13 条，实测全绿）**：
SR-a 用例条数 ≥9 / SR-b `startDestinationFor` 调用点 1 且 UI 内联 `when` 0 / SR-c `deepLinkTarget` 调用点 1、
旧 `deepLinkAllowed` 残留 0（SR-c2）/ SR-d `internal fun` 2 且 `androidx.compose` 依赖 0（决策文件必须保持可测）/
SR-e `ui/user`+`ui/login`+`ui/common` 引用司机域路由 **0** / SR-f `ui/driver` 引用用户业务页 **0**、
SR-f2 司机壳"用户版"出口 `AppState.setRole(Role.USER)` **恰好 1**（唯一合法跨域行，且必须跟着切角色）/
SR-g 未登录深链守卫 1、通知里直接投壳路由 0（SR-g2）/ SR-h `DRIVER_ONLY` 字面量仍是那三条。
同轮配套：`tools/shell_routing_negative_controls.sh`（结构 12 条，**12 ✔ / 0 ✘ / 0 △**）、
`tools/shell_routing_test_negative_controls.sh`（用例 6 条，**6 ✔**，破口 → gradle exit 1 → `cmp` 证原样恢复 → 复跑全绿）。

**用例（`app/src/test/.../nav/ShellRoutingContractTest`，9 例）**：未登录永远进登录页（两种残留角色都试）/
已登录按角色落对应壳 + 两壳必须不同 / 退登录后 `Prefs.clearSession()` 让残留角色回不到司机壳 /
壳路由不接受深链（两角色 × 两壳）/ `DRIVER_ONLY` 就是那三条且不含任何用户域路由 /
司机域深链在用户会话被拒、在司机会话原样放行（正反两条都要，否则"一律拒"也能绿）/
用户域深链两个会话都放行 / 陌生路由交给导航图不静默吞掉。

**证据（全部当场实测，逐条可复跑）**：
`:app:compileDebugKotlin` **executed / GRADLE_EXIT=0**（改完第一件事就是编译）→
`:app:testDebugUnitTest --rerun-tasks` → **24 类 / 253 tests / 0 failures / 0 errors / 0 skipped**
（24 actionable tasks: 24 executed，XML 最新 20:53:59；**+1 类 / +9 例全部可归因**于新类）→
`:app:assembleDebug :app:lintDebug` → BUILD SUCCESSFUL（**12 executed / 36 up-to-date**），
APK 20:54:54 **57,941,374 B**、lint **0 error**（XML 97,380 B：46 Warning + 31 Information）。
9 套探针（含两套新脚本的宿主）全部 **exit 0**；真实性口径**一条没涨**：窄 **9** / 宽 **44** / `GeoMath` **27** /
`DemoData` **3** 文件 / §24 假步进 **6** 处未修；安全六条 **0·4·0·0·0·0**。

**本轮自己抓出来的四条过程纪律（都是回归抓的，不是先想好的）**：

1. **负控脚本必须先验基线**。第一版 `cp -r app/src/main/java $SC/app/src/main/java` 在父目录不存在时报
   "cannot create directory"，之后打印的 12 条 `✔` 全是"文件本来就不在"造成的**假绿**——
   那一轮表面上"负控通过"，实际上**一条都没判**。现在：先断言副本文件存在，基线 `exit≠0` 立刻 `exit 2`。
2. **交替 pattern 忘了 `-E` 就是恒 0**。`grep -rn "$DRV" …`（`DRV` 内含 `|`）在 BRE 里把 `|` 当字面量 ⇒
   SR-e/SR-f **永远显示违规 0**，看着像红线守住了。GNU grep 3.0 同一条 pattern 实测：**加 `-E` 命中 1、不加命中 0**。
   ⇒ 凡是"预期 0"的判据，**必须先证明它能命中非 0**（本轮 12 条结构负控里 4 条是专门干这件事的）。
3. **计数判据要按"注释行"过滤，且 `-n` 与 `-rn` 两种输出形态都要过滤**。负控把代码改成
   `// NEG-CONTROL 摘掉：deepLinkTarget(...)` 后探针仍绿 —— 因为原 NC pattern 写成 `:[0-9]+: *(…)`，
   而 `grep -n` 单文件输出是 `81:        …`（**行号前面没有冒号**）。改成 `(^|[0-9]+:)[[:space:]]*(\*|//)` 后 SR-c/SR-f2/SR-g 三条才变红。
4. **字节数相同不等于没变**。本轮 APK 与 V-1 末次读数都是 57,941,374 B，若按"抄上一轮数字"的习惯就会写成
   "与上轮一致 ⇒ 未引入变化"。改用**符号级复核**：把 APK 里 **14 个 dex 全扫一遍**，
   `ShellDecisionKt` 命中 **3**（类 + 两个方法引用），旧符号 `deepLinkAllowed` 命中 **0**；
   中间产物 `ShellDecisionKt.class` 1,746 B / 20:53:48。**"产物对不对"要看符号与内容哈希，不看总大小。**

**本轮台账自身的一次全损与恢复（比代码出错更贵的那类事故，必须记）**

写完上面"常驻自检"那两行之后，一次"就地重写台账"的 Python 编辑用了 `io.open(path, 'w', encoding=<打错的编码名>)`，
而 **截断发生在 codec 校验之前** —— 文件先被清成 **0 字节**，随后才抛 `LookupError`。
`AI_ITERATION_STATE.md`（约 165 KB 手写台账，**本工作区无版本库**，见 §九 `ENVIRONMENT_BLOCKED` 那行）当场全损。

恢复路径与判据（不是"看着差不多"）：CLI 的 file-history 基座给回 **132,332 B** 的破坏前快照，再逐条重放本会话
记录在案的 **72 次编辑**，得到 **164,888 B / 1,308 行**；再应用本轮两行自检表改写 → 现值 **165,127 B / 1,309 行**。
三次重放异常各自都对上了原因，不是"跳过"：11:25:41 那次编辑**快照里已经含**；12:24:00 那次的内容**在 166–170 行已存在**；
12:16:57 那次本身就复现出一条 `SyntaxError`（与当时一致 ⇒ 重放是忠实的）。
核验方式是拿**破坏前实测读到的行号 + 锚点文本**逐一对上：`88 BLOCKED` / `97 NEXT` / `101 TEST_STATUS` /
`106 BUILD_STATUS` / `114 DEVICE_STATUS` / `168 §八 探针 V-1` / `235 ## CURRENT` / `302 ## 常驻自检` /
`318–324` 自检行 —— **本轮写这三段台账之前又跑了一遍，全部命中**。
⚠ **这串行号是当时那一轮的快照值，台账每轮都会挪位**（N-1 之后 `## CURRENT` 已经在 506 行）⇒ 复跑时先
`grep -n "^## \|§八 探针" AI_ITERATION_STATE.md` 现取行号再判命中，**照抄这张表去比对会得到一串假"不命中"**。
重放头两轮踩的坑也记在这里，因为它们都会**静默毁数据**：
① `subprocess.run(['bash', …])` 在 Windows 上默认解析到 **WSL 的 bash**，72 次操作全部 rc=1 ⇒ 必须给绝对路径
`C:\Program Files\Git\bin\bash.exe`；② `cp *.md "$SB"/` 把**已经变 0 字节的现场**盖掉了快照 ⇒ 恢复类操作
**先看源文件字节数再拷贝**（拷贝前先 `rm -f` 目标那份）；③ Windows 的 Python 看不见 `/tmp`，且 heredoc 里的
非 ASCII 路径会被 GBK 控制台改写 ⇒ 路径只能从 `os.getcwd()` / `os.environ` 取，别把中文用户名写进字面量。

⇒ 三条永久规矩（已同步进 `DECISION_LOG` D-028 与 `CONTEXT_LOCK` §六 V-2 ⑤）：
**动台账之前先 `cp -p` 一份带时间戳的基座**；**永不 `open(台账, 'w')`** —— 一律写 `*.tmp` 再 `os.replace`
（原子替换，中途报错时原件还在）；**恢复必须自带判据**（字节 / 行数 / 行号+锚点 当轮实测三项），
无判据的恢复等于二次破坏。**本轮实际损失为零，但代价是这一轮的一半时间花在恢复上 —— 这类成本不进代码 diff，所以只能进台账。**
工程根现留有本轮的基座 `AI_ITERATION_STATE.md.bak-*` 与 `CONTEXT_LOCK.md.bak-*`（**无版本库 ⇒ 它们是唯一的回滚点，
收工不要删**；`tools/_tmp_*.md` 那四份草稿是本轮写的行文本，已按规矩删除）。

**尾巴**：**V-2b 冷启动不做档案复核**（详见状态板 BLOCKED 与本节上面第 4 条）—— 残留 `DRIVER` 角色 + 已撤档的账号
仍会落进司机壳；现状给的是 `ErrorState(DRIVER_UNBOUND)` + 重试且 **0 条司机请求**，是诚实的错误态而非假空列表，
但人在错误的壳里，唯一出口是"用户版"tab。修法需要**新机制**（会话恢复期一次 `/driver/profile` 复核 + 据此降级角色），
不在验收轮里顺手加，另轮决定。
另：`lint-results-debug.xml`（97,380 B，46 W + 31 I）与 V-1 记的"报告 txt 31,126 B"**不是同一个产物口径**，
本轮只登记自己的实测值，不写"与上一轮相同/不同"的结论 —— 那需要先确认两轮读的是同一个文件。

### V-1 司机动作幂等（指令 §52，本轮完成 · 2026-09-29）

**指令原话与本轮的落点**：§52 要的是"司机动作幂等，防止重复提交造成状态错乱"。
开工前 `clientRequestId|requestId|Mutex|withLock|actionLock|inFlight` 在 `app/src/main` 命中 **0**、
司机写路径 **9** 条（本轮收口后是 10 条，多的那条是 `readDriverMessage`）⇒ 这是一条 P0 `CONTRACT_MISMATCH`。
用户本轮的授权是"**后端没有对应的东西安卓可以自己写，只是要符合架构要求**"，
所以**第一步不是写代码，是把"后端到底有没有"数清楚**（下面五条事实全部对着后端源码逐字段核过，
2026-09-29 本轮复确认）。

**后端五条契约事实（决定了修法，不是决定了解释）**：
① **`AppDriverController.java` 的 `@Idempotent` 命中 0**（`grep -c` 现场 = `0`）；整个 `yudao-module-transport`
   里这个注解只出现在 `AppProductOrderController.create` 与 `AppSendController.create`（`timeout = 10, SECONDS`）
   ⇒ "**司机侧一个幂等键都没有**"不是推测。
② 框架的键是**服务端自己算的**：`md5(methodName + argsStr + userId + userType)`
   （`IdempotentAspect`）⇒ 客户端**没有可传入的唯一键**，能选的只有"自己发不发这一次"。
③ 11 个司机 ReqVO 逐字段数过，`clientRequestId` / `requestId` / `bizNo` **一个都没有**
   ⇒ 在报文里塞这种键，后端只会当多余字段忽略，界面却会以为防住了 —— 这就是 §123 禁止的假兼容，
   本轮把它写成红线判据 **I-h**（命中必须为 0）。
④ 重复请求框架回的是 `REPEATED_REQUESTS` = **900**（`GlobalErrorCodeConstants.java:36`、
   `IdempotentAspect.java:61`），**HTTP 仍是 200 信封**；且 `deleteKeyWhenException = true`
   ⇒ 直接推出两条设计约束：**业务失败必须能立即重试**（异常即释放），以及"**被拦下**"这件事
   在信封层是"成功返回 + 一个业务码"，不能靠 HTTP 状态判。
⑤ **重复提交在本工程不是"多一条日志"，是真的改坏数据**（逐条读实现，四条款条有 file:line）：
   `depart` 会把执行记录**倒回在途**（`DriverAppServiceImpl:861-868`，`arriveTime=null`、`currentStationId` 回首站）；
   `pickup-confirm` 的允许集合 `PICKUP_CONFIRM_STATUSES = List.of(0,1,2,3)`（`:118`）里含 3，
   所以 3→3 的 CAS 会**成功**，随后 `incrementLoadedCount*`（`:1078-1087`）**再把已装件数 +1**，容量被虚占；
   `leg/complete` 与交接 `arrive-dest` 对"同状态"是 **skip 而不是抛**
   （`MultiLegServiceImpl:706-710`、`DriverAppServiceImpl:1595-1597`、`HandoverServiceImpl:107-145`），
   跳过不是失败 ⇒ 后面 `orderEventService.record` **无条件 insert**（`OrderEventServiceImpl:41-58`，
   `transport_order_event` 无唯一索引），每重发一次多一条事件 + 多条推送。
   **例外也照实记**：非交接的 `arrive-dest` 第二次会撞 `TransportLegStatusEnum:53` 抛 `LEG_TRANSITION_ILLEGAL`
   —— 后端自己拦住了，不产生脏数据，但司机会看到一条莫名其妙的报错。
   ⚠ 这条例外一开始被我写成"整族都会留脏数据"，**KDoc 里那句被逐条改成了带 file:line 的四条 + 一条例外**；
   台账里的因果链必须比代码更精确，否则下一轮会拿它当"已经防住了"的依据。

**落点选择（"符合架构要求"具体指什么）**：
· **只能在 `Repo` 层**：壳切换用 `popUpTo (…) { inclusive = true }`，页面的 `busy` 随 ViewModel 一起销毁；
  `object Repo` 是**进程单例**，是唯一还活着的层 —— 把闸门放 UI 等于放在一个会被销毁的地方。
· **不引 DI 框架、不加新依赖**（工程无 Hilt，协程 1.9.0 已在依赖里）：`ActionGate` 就是个普通类 +
  `ConcurrentHashMap<String, Mutex>`，按 key 分桶，**不同班次/段/订单互不阻塞**。
· **语义是"丢弃"不是"排队"**：`Mutex.withLock` 会把第二次提交排进队列、等第一次完成后**照样发给后端**
  —— 那正是 §52 要避免的副作用，所以只用 `tryLock()`（判据 **I-d** 同时钉 `tryLock`=1 与 `.withLock(`=0，
  防的就是"下一轮觉得排队更友好"）。
· **成功后仍持有 10 秒**（`SUCCESS_HOLD_MS = 10_000L`）：时长**抄后端 `@Idempotent(timeout = 10)` 的口径**，
  不是拍脑袋；补的是"协程取消会释放锁、但请求可能已在后端生效"那个缺口。
· **异常立即释放**：等价于后端 `deleteKeyWhenException = true`；否则一次网络抖动把这个动作永久锁死。
· **键按后端实体命名，不按页面**：`depart:$shiftId`、`arrive:$shiftId:$stationId`、`pickup-confirm:$orderId`、
  `deliver:$orderId`、`pickup-verify:$orderId`、`product-load:$orderId`、`product-deliver:$orderId`、
  **`leg/$action:$legId`**（`action` 必须在键里，否则同一段的 `start` 与 `complete` 互相吞掉）、
  `handover-confirm:$handoverId`、`read-message:$id`。
· **有意豁免两处并写成反向判据**：`reportLocation` **不得**上闸（⑦ 已负责合并/新鲜度/丢点可见，
  幂等闸吞掉周期点＝**偷点**，比"重复上报"更坏）、`uploadPhoto` 不上闸（一次性字节流，
  且它的"防重复"应在调用方 `busy`）。I-c 就是把"下一轮把幂等理解成所有写路径"这条路堵死。
· **诚实回执**：`Ack` 新增 `notSent: Boolean = false`（`Repo.kt:107`），四个消费点各加**第一支**分支
  （`WorkbenchScreen.runAction`、`RoutesScreen`、`HandoverScreen` 两处），文案是
  "**本次请求未发给后端**（同一个动作还在处理中），显示的是后端当前状态" ——
  既不许说"已提交后端"（判据 **I-f** 钉这条），也不许说"失败"。回读仍照常执行（那是后端的真相）。
· **顺手修掉一个不可测的写法**：`act()` / `photoPicked()` 原来在 `work {}` **内部**才置 `busy = true`，
  它是否防得住连点取决于 `viewModelScope` 的 `Main.immediate` 语义，**本机无 Robolectric 证不了**
  ⇒ 改成进 `work` 前置位，并用判据 **I-g** 钉住结构（"结构可判、调度不可判时判结构"）。

**新增/改动的文件**：
`core`→`data/repo/ActionGate.kt`（新，含上述 KDoc）、`data/repo/Repo.kt`（`notSent` + `actionGate` +
`gatedAction` 单一出口 + 10 条写路径改造，:1002/:1004/:1010/:1014/:1018/:1022/:1026/:1030/:1034/:1065/:1070/:1099）、
`ui/driver/WorkbenchScreen.kt`、`ui/driver/RoutesScreen.kt`、`ui/driver/HandoverScreen.kt`（回执分支 + 旗标位置）、
`app/src/test/.../data/repo/ActionGateTest.kt`（新 5 例）、`.../DriverActionIdempotencyContractTest.kt`（新 6 例）、
`app/src/test/.../TestSupport.kt`（`TestHost.resetActionGate()`）、`tools/check_idempotency_probes.sh`（新）、
`tools/idempotency_negative_controls.sh`（新 12 条）、`tools/idempotency_test_negative_controls.sh`（新）、
`tools/check_authenticity_counts.sh`（新，把每轮手抄的真/窄宽口径 grep 固化成脚本）。
⚠ **【F-2 ⑥ 追加的口径注解，不改上面的历史数字】**：上面这句"新 12 条"与下面"12 ✔"是 **2026-09-29 V-1 当时登记的条数**，
而 V-3 把负控沙盒改成整目录拷贝之后、以及 **2026-09-30 F-2 ⑥ 本轮复跑**，实测都是 **11 条 `I-*` 破口**。
本轮**没有**回改这两处历史数字（改历史会让引用它们的审计段落集体漂移，且手里没有 V-1 当时的运行产物可证），
处置是：**条数一律以脚本当下的打印为准，凡引用"12"必须带日期与主题**（口径同 §八 ㉑）。
本轮实测全值：**25 行控制 = 24 ✔ / 0 ✘ / 0 △ + 1 ◦**（11 条 `I-*` + 13 条 `U-*` + `◦ U-j`），见 DONE「F-2 ⑥」与 D-038。

**为什么用例判的是 `server.requestCount` 而不是返回值**：闸门坏了也照样能回一个 `Ack`，
只有"**网络层到底发没发**"才是 §52 的语义。`ActionGateTest` 用 `CompletableDeferred` + 注入时钟做到无 sleep；
`DriverActionIdempotencyContractTest` 用 MockWebServer 计数：并发 `depart(9101)` → **恰好 1 个请求**且其中一个
`notSent=true`、body 逐字节不变（`driverId:7002`、`shiftId:9101`）；不同班次两个都发；
`reportLocation` 连调两次 → **两次都上网络**（反向验证 I-c 的那一半）；业务错误后重试 → 2 个请求；
`legAction` 按 action 分键；`readDriverMessage` 的 PUT 也走闸。
⚠ `Repo` 是进程单例 ⇒ 各用例必须用**互不相同的 id**（9101/9102/9301/9401/9402/9501/9601），
这条不是风格问题，是下面那个回归的解药之一。

**验证证据（本轮实测，不是"应该能跑"）**：
· `./gradlew :app:testDebugUnitTest --rerun :app:assembleDebug :app:lintDebug` → **BUILD SUCCESSFUL，`GRADLE_EXIT=0`**
  （⚠ 本轮在此之前的第一次是**用 `| tail -25` 判成败**的，管道退出码是 `tail` 的，于是把一次真实的
  `BUILD FAILED` 读成了"exit 0" —— 之后一律 `> log 2>&1; echo GRADLE_EXIT=$?`。这条已进 `CONTEXT_LOCK.md` §六）。
· 测试产物：`suites=23 tests=244 skipped=0 failures=0 errors=0`，23 份 XML mtime 一致（中途 20:00:21，
  收工那次 20:22:49）；**逐类加总 = 244，+11 全部可归因**（`ActionGateTest` +5、`DriverActionIdempotencyContractTest` +6）。
· 构建产物：APK **57,941,374 B**（⑬ 57,941,278 ⇒ **+96 B**）；lint `0 errors, 46 warnings`（txt 31,126 B，与 ⑬ 同字节数）。
· 收工复跑：`53 actionable tasks: **2 executed / 51 up-to-date**`，executed 的正是
  `:app:testDebugUnitTest`（`--rerun`）与 `:app:lintDebug`，其余 `compileDebugKotlin`/`packageDebug`/`assembleDebug`/
  `lintAnalyzeDebug*`/`lintReportDebug` 全 UP-TO-DATE ⇒ 按内容哈希证"产物对应当前源码"。
· 7 套探针 exit 0；`idempotency_negative_controls.sh` **12 ✔ / 0 ✘ / 0 △**（V-1 当时登记值；⚠ F-2 ⑥ 复跑实测该簿 `I-*` **11 条**，见上面"本轮新增文件"清单末尾的注解与 D-038）；
  `idempotency_test_negative_controls.sh`：删窗口/锁 → **10 条用例红**，`cmp` 证原样恢复 → 复跑全绿 → 探针仍 exit 0。
· 真实性与安全组：窄 9 / 宽 44 / `GeoMath` 27 / `DemoData` 3 文件 / 假步进 6 处未修 / 安全六条 0·4·0·0·0·0。

**本轮被自己抓到的两件事（比代码更贵，都立成了规矩）**：
① **新机制必须跑全量，只跑自己的新用例不算验证。** 闸门是进程单例 ⇒ 10 秒保留窗**跨测试类存活**，
   跑完新两类再跑老类时 `ScalarEnvelopeContractTest.kt:191` 直接 NPE（那次 `confirmHandover` 被本地闸吞了）。
   **只跑新那两类永远是绿的。** 处置：`TestHost.resetActionGate()` 用反射清 `locks`/`heldUntil`
   （与工程里既有的 `installPrefs` 同一手法），**没有**放宽闸门、**没有**在生产 `Repo` 上开一个只为测试的 `reset`。
   ⇒ 规矩：**引入跨用例存活的进程状态，必须同轮给测试侧复位**；每轮收工必须跑 `--rerun` 全量。
② **I-h 第一次量到 1 命中，命中的是我自己写的 KDoc**（说明"为什么不用 `clientRequestId`"）。
   处置是给判据加注释行排除（`grep -vE ':[0-9]+: *(\*|//)'`），**不是删注释** —— 与 ⑬ 的 T-ah 同一条规矩：
   用删说明换绿，等于把这轮的因果链抹掉。
   同轮另两处负控制修正：一条 sed 写成了 `s|= gatedAction("depart:= …` 这种永不匹配的形态
   （**静默失效的控制比没有控制更坏**，因为它报告"测过了"），改成 `s|= gatedAction("depart:|= ungated("depart:|`；
   一条编号写成 `I-j2` 而判据名是 `I-j` ⇒ 控制报 △（抓到的是别的判据）。

**尾巴（在册未修，下一轮别当成已修）**：
· **`900` 的用户端语义**：用户侧 `create` 真重复时后端回 HTTP 200 + `code=900`（"正在处理中"），
  Android 现在把它当普通错误文案显示。司机侧本轮已用本地闸门绕开，但**用户侧仍需一条"900＝已被后端挡住，去回读"**
  的专属分支 —— 这不是"加错误提示"，是把"到底成没成"讲对，需单独一轮（先核 `Api.rawExecute` 的信封读法再动）。
· **闸门只防"重复点击"，不防"重复进程"**：进程被杀重启后保留窗归零。要真防后者**必须后端给键**，
  而后端没有（本轮实测）⇒ 这条只能记着，**绝不允许**在客户端造假持久化（写个 SharedPreferences 记"已提交过"
  就是新的假话来源）。
· **`uploadPhoto` 未上闸**是有意的，但它与 `confirmHandover` 之间仍有"上传成功、提交被拦"的组合，
  真机上要看是否留下孤儿文件（需真机验证，规则 L）。
· §24 假步进 **6 处**、6 处自造别名 `"eta"`、`QuoteResult` 金额 null→0、`Repo.optBoolean(…, true)`、
  OSM/ODbL 应用内署名 0 —— 全是前轮在册项，本轮一项未动。

### ⑬ 精确到站 ETA + 三个 `distanceKm` 的口径（§35 未收口项 + 本轮读码新发现两处，本轮完成）

**这一轮六条契约事实，其中两条推翻了自己之前写进台账的话**（照例先把这几条记在改动前面）：
① **同一个键名在两个端点说的是两件事**：`etaMinutes` 在 `/bus/lines` 上（`AppBusServiceImpl:130-132`）是
   "**跑完剩余线路**要几分钟"= 剩余进度 × 班次计划时长，在 `/bus/nearby` 上（`fillEta:726-731`）是"**到下一站**要几分钟"。
   旧界面把两个端点喂进同一个 `etaCaptionText`，于是 lines 的车被说成"约 64 分钟到下一站"，而 64 分钟其实是全程剩余。
   ⚠ **这条不是"改成可空"能修的** —— 值一直都在、都只是"没给时才该空"，错的是把两个口径当同一个量。
   唯一可观测的判别子是 **`progress` 只在 lines 形状里出现**（`AppBusNearbyRespVO.NearbyBus:81-150` 逐字段核过，没有这个键），
   所以分家只能按"**能观测到的报文形状**"来判，不能按键名猜 ⇒ 立进 `CONTEXT_LOCK.md` §四。
② **精确到站分钟从来没进过模型**：`AppBusRespVO:49-50` 的 `etaToNextStationMinutes`（`Double`，小数分钟）才是"到下一站"的精确值，
   ⑬ 之前 Android 只能退到整数 `etaMinutes`。取整口径按小程序 `pages/bus/detail.js:132-137` 同式 `Math.max(1, ceil(…))` ——
   0.2 分钟要显示"1 分钟"而不是"0"，这条由 T-ab 钉住（"向上取整口径唯一"）。
③ **线路里程的真实键是 `distanceKm`（公里）**：`AppBusLineRespVO:27-28` 是 `Double`、`AppBusServiceImpl:179` 原样透传
   `route.getDistanceKm()`、上游 `MonitoringServiceImpl:126` 可空；而旧解析读的是 `distance|meters` ——
   **契约里根本没有的键**，所以恒 0，`BusScreen` 的"全程"永远显示"—"（后端真给的里程被丢掉）。
   更糟的是 `distance` 在 `BusLine` 那一族里是**米** ⇒ 留别名等于"缺公里键就拿米当公里"。
④ **段里程/段时长两个键都可空**（`AppDriverLegRespVO.java:58-62` = `BigDecimal distanceKm` + `Integer durationMinutes`），
   ⑫ 把这两个键列为"打印残留 2"，本轮收口成判红的 T-ad2=2；语义上 `durationMinutes` 是**计划**耗时不是剩余，文案写"计划 N 分钟"。
⑤ **`QuoteResult.distanceKm` 既可空、本身就是估算**：`AppSendQuoteRespVO.java:21-22` 的 `@Schema` 原文
   "计价里程(km)：**直线距离 × 路网系数**；站点缺坐标时为 null" ⇒ 界面固定写"计价里程 X km（直线距离×路网系数，非道路里程）"，
   缺值写"计价里程未给"。**这是 ⑫ 给承运分钟立的规矩（§71）第一次推广到"里程"这一族**。
⑥ **⚠ 推翻 ⑫ 自己写的一条结论**：⑫ 的 CURRENT 段记 `GET /transport/driver/position` "端点不存在"。实测**存在**
   （`AppDriverController.java:268-271`），问题是 `AppDriverPositionRespVO` **只有 `longitude`/`latitude`/`dataSource` 三个键**，
   而 `RoutesScreen:144` 过去无条件渲染 `"在途 ${nextStation}"` —— 站名恒空、"在途"是**本机替后端宣布的**。
   ⇒ 本轮没有为它编状态，而是加 `positionOnlyTag` 只念报文真有的东西，并用 T-ah=0 钉住"不许把位置报文说成在途"。
   这条的价值不亚于任何一处代码修改：**"端点不存在"是一个会让人放弃核对的错误结论**，真去 grep 一遍后端就发现它是有的。

**修法（口径全部落在模型 getter 上，因为本工程无 Robolectric，界面字符串只能这样被测到）**：
- `data/model/Models.kt`：`BusVehicle` 新增 `etaToNextStationMinutes: Double? = null`（解析**只认这一个键名**，
  绝不把 `etaMinutes` 列进它的候选名 —— 两者口径不同）、派生 `nextStopMinutes`（精确值优先，否则**只有 nearby 形状**
  〔`progress == null`〕才允许把 `etaMinutes` 当到站分钟）、`remainingRouteMinutesText`（lines 形状的如实说法：
  "剩余全程约 N 分钟（按线路进度与班次计划时长估算，不是到下一站）"）、`positionOnlyTag`；
  `etaValueText/etaUnitText/etaCaptionText` 三个 getter 按端点分支，**lines 又没精确值时大数字位空着（"—"）**，
  不摆一个口径错的数（"宁可大数字空着，不许摆一个口径错的数"）；
  `BusLine.distanceKm: Double? = null`（旧 `distanceMeters` **不删**，兼容旧报文，界面一律改读 `distanceKm`）、
  `TransportLeg.distanceKm: Double? = null` + `durationMinutes: Int? = null` + `legPlanText`（缺哪个就不提哪个，两个都缺整行不显示）、
  `QuoteResult.distanceKm: Double? = null` + `distanceCaliberText`、`RoutePreview.parse(o)`（Repo 不再就地拼候选键名，
  收掉"米 vs 公里"那处自造别名）。
- 界面四处：`BusScreen:495` 全程里程、`SendScreen:559` 计价里程、`TraceView:186` 段卡里程、`RoutesScreen:144` 位置标签，
  全部改成"没给就不显示 / 说未给"。**没有新增任何展示面**（§72 一开头就核过 `TransportLeg` 那两个字段当时**没有消费者**，
  所以只修类型与解析）。
- `data/repo/Repo.kt`：`BusLine`/`TransportLeg`/`QuoteResult` 的读法改真实键 + `multiNumOrNull`；
  `DemoData` 给 lines 形状下发一次 `etaToNextStationMinutes`（T-ag=1，新字段在演示形状里也必须出现一次，否则判据测不到它活着）。

**测试**：`BusEtaContractTest` 9 → **12 例**（新增"同名键两端点语义分家"、"lines 缺精确值时大数字为空"、
"位置报文不被念成在途"），新建 `data/DistanceCaliberContractTest` **4 例**（12.4 km 换算、缺键 null、
`legPlanText` 缺一个只提另一个、计价里程必须带口径）。全量 **21 类 233 tests 0 失败**（原 20 类 224）。

**判据**：`tools/check_eta_probes.sh` 新增 **T-aa(1) · T-ab(1) · T-ac(0) · T-ad1(6) · T-ad2(2) · T-ae(0) · T-af(≥4) · T-ag(1) · T-ah(0)**，
其中 T-ad1 与 T-ae 分别是"**按 `data class` 类体数**"和"**按行配对（闭包）**"两条老写法的第一次复用
（"六个"只有逐类体数才是六个；只数总数拦不住"新增一处不判空的展示"）；`T-c 8 → 9` 是 `BusVehicle` 多了第 9 个可空数值字段；
判据行末改成"ETA 判据通过（公交侧 + 货运侧 + ⑬ 里程口径）"。`tools/eta_negative_controls.sh` 扩到 **50 条**。
**本轮两次被自己的判据咬**（两次都比代码问题更贵，详见"常驻自检"与 §六）：
① `T-k` 的 pin 还是 `-ge 9`，而我刚给同一个测试类加到 12 例 ⇒ "删掉一条用例"的负控制从红变灰，
**判据静默失效**；已抬到 `-ge 12`，并立规矩"`-ge` 型计数判据必须在加用例的同一轮抬下限"。
② `T-ah` 第一次响，**响的是我自己写的注释**（注释里引用了旧文案 `"在途 ${nextStation}"` 来解释为什么改）——
处置是按内容剥注释行（`grep -vE ':[0-9]+: *(\*|//)'`），**不是删注释**：用删说明换绿，等于把这轮的因果链抹掉。
另有两条**路径/锚点写错**的判据（`CONTEXT_LOCK.md` §八 ④ 假步进、⑥ 安全组）在本轮照抄时撞见，已改正并记下：
判据写错路径时的报错方式是"安静地没有命中"，与"真的修好了"在输出上长得一模一样。

**验证证据**（全部实测，gradle 输出为唯一真值）：`testDebugUnitTest --rerun` → 任务 **executed**、
`app/build/test-results/testDebugUnitTest/*.xml` 21 份 mtime 全部 **17:58:22**、逐份加总
**`suites=21 tests=233 skipped=0 failures=0 errors=0`**（逐类分布抄在"常驻自检"末行）；
`:app:assembleDebug` executed → APK **17:58:10 / 57,941,278 B**；`:app:lintDebug` executed →
报告 **17:50:46**（txt 31,126 B），末行 **`0 errors, 46 warnings`**；6 套探针 exit 0；
`eta_negative_controls` **50 ✔ / 0 ✘ / 0 △**（基线与恢复后各判通过一次；负控全部跑完之后又串着复跑一遍，仍 **50 ✔ / 0 ✘ / 0 △**
且六套探针重跑全 exit 0 —— 逐条输出见本节末尾的"⑬ 收工最后一遍"）、`reach_negative_controls` **21 ✔**；
安全组 `*.jks|*.keystore` 0、`app/src/main` 里 `127.0.0.1|localhost` 0、`HttpLoggingInterceptor` 0、
key/凭证**字面量**0（⚠ 按裸 token `grep -rn "AMAP_KEY" app/src/main` 是 2 处引用，别把引用当密钥，见"常驻自检"安全组行的口径说明）、
`app/build.gradle.kts` 4 行（2 注释 + 2 构建期拦截）、release 清单 `usesCleartextTraffic="false"`；
假步进 **6 处**未变（§24 仍未修）。

⚠ **收工复跑 gradle 时抓到一条我自己踩过的判断错误**（正好是 ⑪ 记过的那条坑的反面）：
`find` 显示 `Models.kt`/`RoutesScreen.kt`/`BusEtaContractTest.kt` 的 mtime 是 **17:56**，晚于 lint 报告的 17:50:47，
我据此判定"lint 数字过期、必须重跑"。重跑（含 `--rerun`）后 gradle 报
`compileDebugKotlin` / `testDebugUnitTest` / `packageDebug` / `assembleDebug` / `lintAnalyzeDebug` / `lintReportDebug` **全部 UP-TO-DATE**
（53 tasks：1 executed / 52 up-to-date）—— 这才是结论：**gradle 按输入内容哈希判 up-to-date，哈希没变说明那三个 17:56 的 mtime
是负控脚本 `sed` 破口后原样恢复留下的，内容与 lint/测试跑过的版本一致**，所以 17:50 的 lint 与 17:58 的测试产物**确实对应当前源码**。
⇒ 修正后的纪律：**mtime 只能让人"起疑"，不能用来判"产物过期"**；判据是 gradle 报 executed 还是 UP-TO-DATE，
需要无歧义就 `--rerun-tasks` 逼它真跑（本轮 `:app:lintDebug --rerun` 已实测：生命周期任务 executed、分析任务仍 UP-TO-DATE，
`--rerun` 只作用于命名到的那个任务，不会带着依赖一起重跑 —— 想真重跑要 `--rerun-tasks`）。

**尾巴（登记不冒充已修）**：① ETA/里程候选名里还剩 **6 处自造别名 `"eta"`**（`Models.kt` 四处 + `Repo.kt:623/947`，
其中 `Repo.kt:1253` 的 `RouteGuide` 读的是**导航引擎自己的报文**、不是 app VO，**改之前必须先核那份契约**）；
② `QuoteResult` 的三个金额字段（`amount/itemFee/distanceFee`）仍是 `multiNum` 非空读法，缺键塌成 **0.0**，
`distanceFee > 0` 才显示"里程费" ⇒ 同病不同族（金额不是 ETA），并入下一轮；
③ `TransportLeg` 的 `navigationPolyline/fromGeo/toGeo` 后端**不下发**（⑫ 实测），段卡"在途进度"仍是状态驱动的示意，
属 #49 `core/navigation` 真道路改造线（BLOCKED on 高德导航 SDK）；
④ "全程 N km""本段 … km · 计划 N 分钟"这些串**第一次有了真值可能，但从未在真机上看见过** —— 记 `NOT VERIFIED ON DEVICE`。

### ⑫ 货运侧 ETA 家族收口 + §68 到站提醒数据源迁移（§38 残留 + §68 + §71，本轮完成）

**这一轮四条"读后端源码读出来的事实"，其中两条把 ⑫ 开工前写在 CURRENT 段的方案本身推翻了一半**（照例先记这几条）：
① **`/send/page` 的列表项里连 `etaMinutes` 这个键都不存在**。`AppSendOrderRespVO:96` 那个字段只由
   `AppSendController:246-250` 的 `fillEta` 写（`estimatedArrivalTime` 减 `now()`，**只有未来时间才给值，否则 null**），
   而列表走的是 `fillCarrierBatch`（`:142`）不调它 ⇒ "寄货列表卡片显示班次 ETA"这件事在真后端下**从来没有数据**，
   旧代码却把它读成 `multiNum(...).toInt()` = 0，再拿 `if (etaMinutes > 0)` 判"还没到" ⇒ 恒假、恒静默。
   ⚠ 这一条推翻了我 CURRENT 段写的方案 ② —— 那里把 `SendOrder.etaMinutes` 当成"改个可空类型就行"，
   实际要处理的是**两个不同量被一个字段吞了**（见 ②）。
② **旧解析把两种口径混成了同一个数字**：`SendOrder.etaMinutes = multiNum("etaMinutes","eta","carrierEtaMinutes")`，
   而 `carrierEtaMinutes`（`AppSendOrderRespVO:106`，由 `:478` 填）是 `GeoDistanceUtil` 的 **Haversine ÷ 25 km/h** 估算。
   把它列进班次 ETA 的候选键，等于允许服务端算的直线估算冒充"车到你这站还几分钟"—— 这是硬规则点名禁的那条
   （禁止把 Haversine 说成道路 ETA）。⇒ 现在拆成 `etaMinutes`（班次口径）与 `carrierEtaMinutes`（承运直线估算）**两个字段**，
   别读错；T-t 专门钉"候选键里不许再混进 carrierEtaMinutes"。
③ **两个段契约（`AppSendLegRespVO` / `AppDriverLegRespVO`）压根没有 ETA 键** ⇒ `TransportLeg.etaMinutes` 真接口下恒 null，
   旧写法 `multiNum("etaMinutes","eta").toInt()` 是把"后端没这个字段"读成"这一段 0 分钟"。
   `DriverLegRespContractTest` 之外，本轮在 `SendEtaContractTest` 里按 §49 逐字段（每个字段给互不相同的非默认值）钉了一遍，
   断言 `assertNull(leg.etaMinutes)` —— 不靠"两边都空"蒙过断言。
④ **承运那一整块可能不填**：位置缺失或过期（`REAL_STALE`）时后端直接跳过承运块 ⇒ `carrierEtaMinutes` 必须可空**且**
   新鲜度是它的前置条件。这就是 T-y（`carrierLocationSource != "REAL_STALE"` 恰好 3 处：提醒 + 首页 + 寄货页）存在的原因，
   只判空不判新鲜度，就会拿一个过期位置算出的分钟数去通知用户出门。

**代码侧五处**（照方案 ②→⑤ 做完，①/②的口径按上面 ①② 改正）：
- `data/model/Models.kt`：`SendOrder.etaMinutes: Int?`（KDoc 写明"真实来源只有 `AppSendOrderRespVO:96`，且列表端点不填"，
  并**显式禁止**再把它与 `carrierEtaMinutes` 互为别名）、`carrierEtaMinutes: Int? = null`、
  `isArriving` 改为看 `carrierApproaching || carrierEtaMinutes in 1..10`；`TransportLeg.etaMinutes` /
  `ParcelTrace.etaMinutes` / `RouteGuide.etaMinutes` 全部改 `Int?`；
  `RoutePreview(distanceKm: Double?, durationMinutes: Int?, provider: String)` —— 这两个键在 `RoutePreviewRespVO` 里就是可空，
  塌成 0 就是给用户看一个不存在的里程/时长。
- `data/repo/Repo.kt`：`traceOf()`、`routeGuideOf()` 的 ETA 读法改 `multiNumOrNull(...)?.toInt()`；
  `routePreview()` 去掉 `etaMinutes` 别名（那个键在这个契约里不存在）并按真实键读 `distanceKm` / `durationMinutes`。
- `notify/AlertEngine.kt`（§68 迁移）：到站提醒的数据源从 `/send/arrangements` 换成 `Repo.sendOrders().items`，
  过滤链是 `carrierLocationSource != "REAL_STALE"` → `mapNotNull { carrierEtaMinutes }` → `in 1..10`，
  文案 **"车快到了 · 还有约 N 分钟（直线估算）"**。提醒不再是"恒不响"，也不再是"把没数据伪装成还没到时间"。
- 用户侧两个列表：`ParcelScreen` 的 `arrivals: List<SendOrder>`（丢掉 `Repo.myArrangements()` 与 `Arrangement` import）、
  `HomeScreen` 的 `arriving: List<SendOrder>`（`carrierEtaMinutes in 1..45` 且非 `REAL_STALE`）；
  `ParcelScreen` 卡片把**两种分钟数分成两个并排标签**（班次 `预计 N 分钟` / 承运 `车距站约 N 分钟（直线估算）`），
  承运标签带 GOLD 色与班次标签区分，注释写明"混显会让用户把直线估算当成道路到站时间"。
- 渲染层"没给"的如实文案 7 处（T-z）：`TraceView`（"接口未给到站分钟"）、`RoutesScreen`（"到站分钟未下发"）、
  `DriverNavScreen`（"全程时长未给"）、`SendScreen`（"里程未给 · 时长未给"）。页面不再出现 `etaMinutes > 0`（T-u=0）。

**测试**：新增 `data/SendEtaContractTest` 9 例（fixture 键名与码值逐字段照抄实测 VO 形状，不是照着 Android 模型编的）：
`/send/page` 报文**不含** `etaMinutes` 键而含 `carrierEtaMinutes=8`、`/send/track` 显式 null、承运块整体缺失、
后端真给 0 时原样透传、`isArriving` 判定、`AppDriverLegRespVO` 逐字段非默认值 + `assertNull(etaMinutes)`、
用户侧段 VO、`trackParcel` null、`routeGuide` null etaMinutes、`route-preview` 里程/时长 null 且 `estimated=true`。
全量 **20 类 224 tests 0 失败**（原 19 类 215）。

**判据**：`tools/check_eta_probes.sh` 新增 T-r1…T-r5（**按 `data class` 类体范围数**，延续 ⑪ 的教训：`distanceKm` 在
`SendOrder`/`BusVehicle`/`RoutePreview` 三个契约里同现，整文件数会串味）、T-s…T-z + T-v2，判据行末改成
"ETA 判据通过（公交侧 + 货运侧）"。`tools/eta_negative_controls.sh` 扩到 **41 条**并全部 ✔，两处配套修正：
- **新增 `sbysed` 助手**（python，按类体边界替换）：全局 `sed 's/val etaMinutes: Int?/val etaMinutes: Int/'` 会一次改掉
  四个类的同名字段，判据就分不清是谁破了；
- **T-o2 的 sed 修好**：原来 `s/etaMinutes != null/…/` 里是小写 `e`，而 ⑫ 之后首页/寄货页读的是
  `carrierEtaMinutes != null`（大写 `E`），破口根本没注进去 —— 这条负控制在 ⑫ 之前**是假绿**的，改成 `s/\([eE]\)taMinutes != null/\1ta…/`。
T-s 的正则**刻意排除 `durationMinutes`**：`TransportLeg.durationMinutes` 同病但另属"段计划时长"这一契约，
把它塞进同一条判据就又是串契约，所以改为在探针尾部**打印残留 2** 并写进 ⑬。

**验证证据**（全部实测，gradle 输出为唯一真值）：`testDebugUnitTest --rerun` → 任务报 **executed**、XML 16:20:52、
`20 suites / 224 tests / 0 failures / 0 errors / 0 skipped`（第一次 `cleanTest testDebugUnitTest` 时该任务仍 UP-TO-DATE、
XML 停在 15:42，所以那条"绿"没算数，必须逼它真跑）；`:app:assembleDebug` executed → APK 16:15:22 / 57,941,192 B、
`BUILD SUCCESSFUL in 2m10s`（53 tasks：11 executed / 42 up-to-date）；`:app:lintDebug` executed → 报告 16:17、
**0 errors, 46 warnings**（XML 计 77 条 = Warning 46 + Information 31，Top：`AutoboxingStateCreation 31 / DefaultLocale 10 / UnusedResources 10 / HardcodedText 8`）；
六套探针 exit 全 0（eta 41 ✔ / reach 21 ✔ / domain / location / status / envelope）；
安全自检：`*.jks` 0、`app/src/main` 里 `127.0.0.1|localhost` 0（`app/build.gradle.kts` 4 行是注释 + 构建期拦截）、
`HttpLoggingInterceptor` 0、凭证字面量 0、release 清单 `usesCleartextTraffic="false"`（debug 才是 true）。
`CONTEXT_LOCK.md` §八 基线复跑：**① 42 行（原 30，+12 全是"把估算说成估算"的文案与注释）② 3 文件 ③ 27 行（未涨 ⇒ 没新增直线计算）
④ 6 处（§24 未修）⑤a 0 ⑤b 0 ⑤c 20 ⑤d 3 ⑥ 0**。窄口径 §55 C 由 4 → **9**，5 行新增同样是口径说明注释。

**本轮没做（如实留档，别当已解决）**：
- `TransportLeg.distanceKm: Double = 0.0` / `durationMinutes: Int = 0` 仍是非空默认值（后端 `AppDriverLegRespVO` 两个键都可空）
  —— 按契约边界交给下一轮，探针只打印不判红；
- 公交侧 `BusLine.distanceKm` 与 `etaToNextStationMinutes` 两项（⑬，已在 CURRENT 段展开）；
- `Arrangement.etaMinutes` 现在是**无人消费的纯兼容字段**（字段保留不删，避免破坏旧解析；ui/notify 的 import 消费点已归零）；
- `Repo.kt` 的 `inService`/`located` 仍用 `optBoolean(…, true)` —— 缺键时替后端默认"在服务/已定位"，与 §14 同类，登记未修；
- 承运分钟的"直线估算"口径只在**文案**里，没有做"用户可点开看这是什么数"的说明层（小程序也没有，属 UI 复刻范围外）；
- 真机项一律未验证（无设备在环）。

### ⑪ 公交 ETA 真实化（§38 + §14，顺带抓出 §68/§69，本轮完成）

**这一轮五条"实测推翻我的预设"（第 ② 条是推翻我自己刚写下的记录）**（同样先记这几条，代码结论会被读走，这几条才是会重复犯的）：
① "后端算不出 ETA 时是缺键、null 还是 0" 有确定答案，不用猜：**显式 JSON null**。
   `AppBusServiceImpl:132` 是 `setEtaMinutes(Math.max(1, Math.round((100-progress)/100f*duration)))`、`:728` 是
   `max(1, ceil(...))` —— **两处都下探到 1 分钟**，所以 0 在这条契约里根本不合法；`:134` 拿不到线路时长时直接
   `setEtaMinutes(null)`。HTTP 响应保留显式 null 键（MVC 的 mapper 不是 NON_NULL，`JsonUtils.java:48` 那个
   NON_NULL 只是工具 mapper）。结论：**客户端任何 `?: 0` 都是替后端编一个它从没发过的值**。
② **这一条我先写错过，改回来**：我一度记成"§35 的修改方案 ② 建议由 ETA 反推进度、本轮把它否掉"，复核原文后台账**没写错** ——
   §35 方案 ② 的原文正是"`progress` 有值就用后端进度、没有就**不画进度条**（不得由 `etaMinutes` 反推）"。
   被推翻的是**代码**（`BusScreen` 那句 `1 - eta/40`，那个分母 40 是 Android 自选常数，后端没有对应量），不是台账；本轮是**照台账落地**。
   在该方案之上补一条它没说的细节：`AppBusServiceImpl:125-127` 是 `getProgress()!=null ? … : 0`，
   即**"后端给 0"与"后端没给"是两件事**，客户端只透传、不许合并（合并后"没给"会被画成"刚出发"）。
   ⚠ 单独记这条的原因：**把"实现违背台账"写成"台账错了"，下一轮就会有人拿着我的记录去把正确的方案改反。**
③ 预设"④ 联动时给 `distanceToNextStationKm` 找个 ETA 替身"——**拒绝**：`etaToNextStationMinutes` 只在
   `AppBusRespVO:50`，**不在 `NearbyBus`**（`AppBusNearbyRespVO:78-151` 逐字段核过），而且它说的是"车到自己下一站"
   不是"车到用户那个站"，拿来顶替就是造一条不存在的语义。
④ 台账没记的**三处静默断链**（都只在对接真后端时才现形，演示态一切正常）：
   旧 `multiStr` 候选名里没有 `busId`/`plateNo` → 车辆 id 与车牌恒空；
   `statusOf` 把整数 2 映射成 `ARRIVED`（而 `/bus/lines` 的 2 是停用）且码表里还留着一个后端从不发的 3；
   `sourceOf` 把 `dataSource=REAL` 升格成 `REAL_FRESH` —— `/bus/lines` 没有 `locationSource` 键，
   等于替后端宣称"新鲜度已确认"，现在落 `UNKNOWN` → 文案"实时（新鲜度未标）"。
⑤ 顺带又抓到两**结构性**缺口（只登记，见审计 §68/§69）：所有车辆端点都没有 `routeId`（线路详情必须按 `routeName` 回查）、
   `/send/arrangements` 的 11 个键里**没有 `etaMinutes`**（到站提醒在真后端下不响，唯一能给的是 Haversine 估的 `carrierEtaMinutes`）。

**代码侧六项**（按当初定的 ①→⑤ 顺序做完，第 ④ 步的"ETA 替身"按上面 ③ 驳回）：
- `data/model/Models.kt`：`BusVehicle` 8 个数值字段改可空（`speedKmh/etaMinutes/waitDepartureMinutes/
  distanceToNextStationKm/nearestStationEtaMinutes/stopsToNearestStation/progress/distanceKm`，T-c 按**类体**数=8）；
  读法一律 `multiNumOrNull(...)?.toInt()`；派生文案 `etaValueText`（没值 → "—"/"待发"，单位 `etaUnitText` 同时留空，
  渲染不出"0 分钟"）、`etaCaptionText`（到站/发车/已到终点是三件不同的事）、`nextDistanceText`（null → "距下一站未给"，
  真给 0.0 才说 0.0）、`roadProgress`（**只认后端 `progress`**）、`providerText`（`SCHEDULE` → "按班次计划时长估算"，
  未知来源写"来源未识别（X）"，不再有"现实公交/项目线路"那种替后端背书的标签）；
  `statusOf` 收成两个端点的真实口径（字符串四码 + 整数 1/0/2，无 3）；`sourceOf` 不再从 `dataSource` 升格新鲜度；
  新增 `byUserDistance()`（nullsLast，对齐 `AppBusServiceImpl:436-437` 的 `Comparator.comparing(getDistanceKm, nullsLast)`）；
  `routeId` 字段**保留不删**（旧兼容），KDoc 写明"真接口下恒为空串"（§69）。
- `data/repo/Repo.kt`：车辆解析出口两处（nearby 列表 + 单车详情，T-h1=2），**不直接构造 `BusVehicle`**（T-h2=0）。
- `data/repo/DemoData.kt`：`vehicleJson` 按两个端点的**不同形状**分别发（`lines` 发整数 status + `progress` + `speedKmh` +
  `etaToNextStationMinutes`；`nearby` 发字符串 status + `locationSource` + `routeProvider="SCHEDULE"` + `nearestStation*`），
  `state` 三取 `RUNNING/NO_ETA/IDLE`，"没给"一律写 `JSONObject.NULL`（T-j4=12）而不是省键 —— 与后端实际报文同形；
  新增一辆 渝A·3Q778 只在 nearby 列表里（"在途但给不出 ETA"这条路径只有 `/bus/nearby` 走得通，`:665/:719` 显式 null，
  `/bus/lines` 用进度反推、恒 ≥1 分钟）；车辆兜底里**不再出现 `routeId`**（T-j2=0）。
- `ui/user/BusScreen.kt` + `ui/user/HomeScreen.kt`：卡片"看这条线"按 `lineIdOf(v)` 解析（行名回查 `/bus/lines` 的 `routeId`），
  解析不到就置灰；首页 `nearby.lines`（`NearbyLine` 只有 4 个键、没 id）→ 该行**如实不可点**，不做半个假的可点态。
- `widget` + `notify/AlertEngine.kt`：与页面同口径 getter（T-n=3），提醒用 `mapNotNull` 滤掉 null（T-o1=1），
  0 不再被当成"还没到"（T-e=0）。
- 排序/消费侧：6 处 `etaMinutes > 0` 先判 null（T-o2=5），"没给"文案 4 处（T-m=4）。

**测试**：`BusEtaContractTest` 9 例 / 全量 **215 例（19 类）0 失败**（原 206/18）。含 §49 的逐字段"非默认值"守卫、
`lines` 整数码 1/2/0 各有一句、进度没给不许反推、无经纬度不塌成"(0,0)"、nullsLast 排序、估算来源不许冒充真实路网、
§68 那条"到站提醒的数据源根本没有 etaMinutes 这个键"、以及"后端真给 0 分钟时原样透传"。
本轮写测试时实测挂过一次：`没有经纬度不塌成北纬0度东经0度` 断言 `sourceText == "位置暂不可用"`，
但那条报文只有 `status=NO_LOCATION`、`locationSource` 是 UNKNOWN —— 补上 `"locationSource" to "NO_LOCATION"` 并把
"NO_LOCATION 是防御性分支（后端 `:392-394` 已把无坐标车辆过滤掉）"写进测试注释，**没有**顺手放宽断言。

**判据**：`tools/check_eta_probes.sh`（T-a…T-q，25 条，按内容不按行号）+ `tools/eta_negative_controls.sh`（25 条逐条破口，**25/25 ✔**）。
四条负控制第一版"失效"（破口注入后 exit 0），根因都是**判据本身太弱**，先改判据再复跑：
T-d1 的 `eta/40` 正则漏了 `!!`、T-e 锚点选在 `fun scan(`（真实入口是 `suspend fun check(context)`）、
T-l 锚点是已被替换掉的 `Text(v.etaCaptionText`、T-o2 的 `sed` 只替了 5 处里的 1 处。
同轮把 **R-g 也从整文件改成 `data class Reachability` 类体范围** —— 因为 `BusVehicle` 新增的可空 `distanceKm`
会让可达性那条判据数到别的契约的字段；这是**摘出去**，不是把预期数字从 2 抬到 3。可达性负控制复跑 **21 ✔**。

**验证证据**（全部实测，gradle 输出为唯一真值）：`assembleDebug` **BUILD SUCCESSFUL**
（14:07 一次 37 tasks 全 UP-TO-DATE、`BUILD SUCCESSFUL in 5s`；14:33 再跑一次 `BUILD SUCCESSFUL in 2m 24s`、
32 tasks 里 2 executed / 30 UP-TO-DATE 并重新产出 `app-debug.apk`（14:33:58，58,410,069 B）——
两次都以"编译产物 `DemoData.class` 14:05:11 / APK 14:33:58 均晚于最后一次源码改动 13:56:54"为准）；
`testDebugUnitTest` **19 类 215 tests 0 failures 0 errors 0 skipped**（XML 14:05:34，复跑时 24 tasks 全 UP-TO-DATE，
即结果对应的就是当前码）；`lintDebug` **0 errors / 46 warnings**（`lintAnalyzeDebug` 14:09 executed、报告字节不变 31139）；
六套探针 exit 全 0（eta / reachability / domain / location / status / envelope）；
`CONTEXT_LOCK.md` §八 基线复跑：**① 30 行 ② 3 文件 ③ 27 行 ④ 6 处（§24 未修）⑤a 0 ⑤b 0 ⑤c 20 ⑤d 3 ⑥ 0**。
⚠ 本轮踩过一次"时间戳纪律"：测试 XML 是新的、lint 报告停在改源码前 36 秒，**每个产物各自对时间戳**。

**本轮没做（如实留档，别当已解决）**：
- 货运侧 4 处 `multiNum("etaMinutes").toInt()`（`Models.kt:801`、`Models.kt:957`、`Repo.kt:582`、`Repo.kt:942`）+ 页面 6 处
  `etaMinutes > 0` 仍是 null→0 塌缩，⑪ 的 T-a 口径只覆盖公交链路，所以它们现在是**打印残留**、⑫ 判红；
- `Arrangement.etaMinutes` 保留可空类型但真后端下恒 null（§68），到站提醒仍未迁数据源；
- `Repo.kt:714/687` 的 `inService`/`located` 用 `optBoolean(…, true)` —— 缺键时**替后端默认"在服务/已定位"**，与 §14 同一类，登记未修；
- 演示 nearby 仍发旧键 `stations`（`DemoData.kt:641`）而真实键是 `nearbyStations`（`Repo.kt:972` 已按真实优先读，能兼容但不成形）；
- ~~演示面那条 `/bus/realtime` 分支全工程**没有任何调用点**（死端点，留着会误导后来人以为有这条接口）~~ **已收口（2026-10-15 D-120）**：分支删掉，并把这条规矩立成判据 `tools/check_demo_path_reachability_probes.sh`（DP-a…DP-d：孤儿必须 0，有理由留的进 `tools/fixtures/demo_path_exempt.txt` 点名）；端点本身**不接** —— `/nearby` 就是拿同一个 `getRealtimeBuses(mapData)` 算出来的，`AppBusRespVO` 是 `NearbyBus` 的字段子集且没有 radius 过滤（每次刷新多一条更大的 REST 换三个字段，不值）。
- `AppBusNearbyRespVO:128-129` 的 `@Schema` 与实现不一致（说 AMAP/EUCLIDEAN，实现发 SCHEDULE），只在 Android 侧按实现记账。

### ⑩ 可达性真实化 + 三态（§34 + §11 + §14，2026-09-29 完成）

**这一轮四条"实测推翻台账/推翻我的预设"**（先记这个，代码结论随时会被读走，这几条才是会重复犯的那种）：
① 审计 §34 与后端 VO 的 `@Schema` 都把**枚举值写少了**：`distanceProvider` 记两个，源码
   `AppSendReachabilityService.java:103`（初始 `"HAVERSINE"`）+ `:122`（`isRealRoad ? "AMAP_ROAD" : "EUCLIDEAN"`）实测是**三个**，
   且**默认值就是 `HAVERSINE`** —— 后端自己承认默认给的是直线估算，少标一个值就等于把一部分判定显示成"道路里程"；
   `reasonCode` 同样记两个，实际**三个**：`ROAD_UNREACHABLE`(`:55`，"没给位置"那条早退) / `NO_SAFE_HANDOFF_POINT`(`:70`、`:95`) /
   `USER_LOCATION_UNREACHABLE`(`:138`，可运时这里显式置 null)。漏掉的那个恰好是**最容易被说成"不可达"的那一种**
   （用户还没授权定位 ≠ 车辆到不了），`reasonText()` 三条各有独立文案，未知码原样带出不吞（测试"三个后端原因码与未知码的口径"钉住）。
   顺带一条判定顺序：`:79` 先用直线半径 `0.3 km`(`:42`) 试，拿到道路里程后 `:129` **再用 `km <= 0.3` 重新判定** ——
   所以客户端**不许**自己算"再远一点就可运了"，只读 `reachable`。
② 审计 §34 的**修改方案 ③ 本身是错的**：它建议"`originalAddress` 改为提交推荐站点名 + 详细地址"，
   而 `AppSendOrderCreateReqVO.java:60` 的注释写的正是"**位置 ≠ 车辆能到的地方：原始地址与服务站分开保存，不互相覆盖**" ——
   照方案做等于把旧代码那个"站点名冒充用户位置"的错做得更彻底。本轮按源码走（用户位置 = `Locator` → `Repo.reverseGeo`，站点另走 `pickupStationId`），
   并把分歧记进审计 §34"本轮状态"段与 §66；
③ 我以为"站名为空就该拦住提交"（§34 方案 ③ 的说法），实测小程序 `send.js:709` 本身就是 `originalAddress || ''`，
   于是把闸改成两条更小的：站点必选（`validateStep1`）+ 服务方式三条件才回传（`pickupServiceModeOrNull`），
   空地址**不拦**但 UI **不再藏那一行**；
④ **`reachable = true` 分支也会静默**（台账只把问题记在"不可达文案长得一样"）：后端判定可上门却没给站点时，
   卡片只说"可上门"不说去哪个站 —— 这是本轮实测出来的**第 4 处**，补成与不可达分支对称的手选提示（R-o/R-o2）。

**代码侧五项**（按当初定的 ①→⑤ 顺序做完）：
- `data/model/Models.kt`：`enum class ReachabilityState { REACHABLE, NOT_REACHABLE, UNKNOWN }`（自带中文 label）+
  `Reachability.state` 默认 `UNKNOWN`；`multiBoolOrNull`（缺键=2 哨兵）让**缺键 / 显式 null / 非布尔**三种都落 UNKNOWN；
  `distanceKm: Double?`、`walkMinutes: Int?`（§14：null 不塌成 0）；`recommendedStation` 嵌套对象按真实形状读
  （只给 id 没给 name 时**不编名字**）；派生成员 `distanceText / walkText / providerLabel / serviceModeLabel / reasonText / isRealRoadDistance`；
  `serviceModeLabel` 对齐后端 `ServiceModeEnum.java:21-25` 五码（`DOOR_PICKUP/NEAREST_STATION/SAFE_ROADSIDE/CUSTOMER_TO_STATION/STATION_TO_STATION`），
  **不在五码内一律空串**（旧代码的 `"STATION"` 落这里）；`distanceProvider == "HAVERSINE"/"EUCLIDEAN"` 明写
  "**（直线估算，不是道路里程）**"、`AMAP_ROAD` 标"（道路距离）"、未知值原样写出来源（不冒充任何一种）。
  主构造器是**位置参数**（给 DemoData 和测试用），`parse` 走全命名参数 —— `unknown()` 刻意写成 `Reachability(false, "", 0, null, "")`，
  少一个参数就编译不过（防止造出"没有布尔来源的 UNKNOWN"）。
- `data/repo/Repo.kt:739`：解析出口**只有 `Reachability.parse(p.obj ?: JSONObject())` 一处**，不再直接构造模型；
  请求体只带 `latitude/longitude`（对齐 `AppSendReachabilityReqVO:11-17` 的两个 `@NotNull`，不塞地址文本）。
- `data/repo/DemoData.kt:495`：演示兜底改成**真实契约形状**（`reachable/reasonCode/serviceMode/distanceKm/walkMinutes/distanceProvider/recommendedStation{}`），
  `distanceProvider` 只给 `HAVERSINE` 并注释"演示态可以假距离，但不许冒充道路里程"，报文带 `（演示数据）` 标记。
- `ui/user/SendScreen.kt`：界面**只按 `state` 三态分支**（布尔 `reachable` 不再被 UI 读）；UNKNOWN 说
  "后端没有返回可达性判定（**不代表车辆能到**）"+"请手动选择取货站点；本单不声称车辆可上门"；
  `applyRecommended` 只在推荐站点**存在于 `/send/stations` 列表**时才真换（宁可让用户手选，也不构造半个字段都没有的 `Station`）；
  提交报文抽成 `internal fun buildSendForm()`（契约测试能直接断言，不必跑完拍照上传那条协程）。
- 判据：`tools/check_reachability_probes.sh`（R-a…R-p，21 条，按内容不按行号）+ **`tools/reach_negative_controls.sh`**
  （21 条逐条破口，全 ✔，恢复后重新变绿）。

**实测数字（全部有输出，没有输出就等于没跑）**：
```
bash tools/check_reachability_probes.sh → R-a 0 · R-b 0 · R-c 0 · R-c2 1 · R-d 0 · R-e 25 · R-f 2 · R-g 2 · R-h 0 · R-i 1 ·
  R-j 17 · R-k 1 · R-k2 0 · R-l 5 · R-m 2 · R-n 3 · R-n2 0 · R-n3 2 · R-o 1 · R-o2 2 · R-p 1   末行"可达性判据通过"（exit 0）
bash tools/reach_negative_controls.sh  → 21 条 ✔（R-j/R-k/R-m 三条第一轮是 ✘，据此把判据改强后复跑才全 ✔）
./gradlew :app:testDebugUnitTest :app:assembleDebug → BUILD SUCCESSFUL；206 tests / 0 failures / 0 errors / 0 skipped（18 类）
./gradlew :app:lintDebug → 0 errors, 46 warnings（⑨ 是 48：`Models.kt` 两处 `String.format` 补了显式 `Locale`）
其余四组复跑：状态映射 S-* 通过 · 分域 P-* 通过 · 定位 L-* 通过 · 信封 E-* 通过
真实性窄口径 4 行 / 宽口径 30 行（**换掉 Models 的行号白名单后仍是这两个数**，见下面"探针卫生"）
```
（`ReachabilityContractTest` 17 例覆盖：VO 手写报文逐字段非默认值守卫 / 三态退化 / 只给 id 不给 name /
 `distanceKm` 与 `walkMinutes` 独立缺失 / `provider` 未知值不冒充 / `AMAP_ROAD` 才算真实道路 / `serviceMode` 词表 /
 原样报文不回传 / 请求体只带经纬度 / 空 data 与非布尔 `reachable` / 失败不冒充判定）。

**探针卫生（本轮最值钱的一条方法论，记进 §55 L 与 §66）**：负控制第一次跑有 **3 条失效**，抓出来的不是代码问题而是**判据本身太弱** ——
`grep -c "@Test"` 会把注释掉的 `//@Test` 一起数（用例删光也"通过"）、`grep -c "Reachability.parse("` 数的是**行**
（两个出口挤同一行看不见）。改成 `^[[:space:]]*@Test` 与 `grep -o | wc -l` 后三条才真报红。
同轮还实测到：**21 条判据全绿的源码可以同时编译不过**（误删一个换行把两行并成一行，`compileDebugKotlin` FAILED 而探针毫无反应）
⇒ 内容探针只证语义，`assembleDebug` 才证可构建，谁也替代不了谁。
顺带修掉一处**常驻自检自己的洞**（写进 `CONTEXT_LOCK.md` §八 与 §55 C）：真实性宽口径的白名单原有一条
`Models\.kt:(1[1-4][0-9]|2[0-9][0-9])`，**少了结尾冒号**，于是 `Models.kt:1447` 的前缀 "144" 就命中 ——
`Models.kt` 1100..1499 整段命中被静默吞掉（实测吞掉 7 行，其中 6 行正是本轮新写的来源说明）。
改成按内容过滤后窄/宽两个数都不变（4 / 30），但以后 `Models.kt:13xx` 写一行真违规才看得见。

**未做（如实记 `ENVIRONMENT_BLOCKED`）**：① 真机上 `reachable=true` 自动选中推荐站点、`false` 时点"使用推荐站点"的可见行为
——工程没有 Robolectric，Compose 渲染只能靠内容探针（R-n2/R-o/R-p 是文案判据不是截图）；
② 生产环境 `distanceProvider` 是否真回 `AMAP_ROAD` 需连 48080 实数据复测（现在只有契约测试钉住"后端给什么我们怎么解释"）；
③ 逆地理没配 key 时 `originalAddress` 为空那条路径，只能在真机看"文字地址未取到，本单只提交了经纬度"这行是否真出现。

### ⑨ 状态映射统一 + 司机段状态机收口（§18 + §8 + §10 + §26/§27/§28 + §36 + §39，本轮完成）

**这一轮四条"实测推翻台账/推翻我的预设"**（先记这个，代码结论随时会被读走，这几条才是会重复犯的那种）：
① 台账（审计 §28 的修改方案）建议 `0→accept`、`2→arrive-origin`、`8→complete` 三个跳法，**全部非法**：
   后端 `TransportLegStatusEnum.TRANSITIONS`（`TransportLegStatusEnum.java:42-55`）实测：`accept` 的目标是 **2**，而 `PLANNED(0)` 只允许 `{1,99}` ⇒ **0→accept 非法**；
   `2→arrive-origin`（目标 5）非法，`DRIVER_ACCEPTED` 只允许 `{3,4,99}` ⇒ 2 的下一步是 `navigate`（目标 4，合法）；
   `8→complete`（目标 11）非法，`ARRIVED_DESTINATION` 只允许 `{9,10,99}` ⇒ 末端段停在 8 时改再发一次 `arrive-dest` 转派送。
   我没有照台账落地，改成"动作集 = 后端 `DriverAppService` 的 9 个 + `exception`，每个动作的目标码先过 `TRANSITIONS` 一致性测试"（`DriverLegStateMapperTest` 把后端那张表**抄了一份**逐对断言，改一处就红）；
② §36 的 `seq`/站名/车牌恒空**不是解析 bug 的一半**：`fromGeo/toGeo/navigationPolyline` 后端契约里**从来没有**，
   是 `TransportLeg` 声明了字段又被 UI 当"可选几何"读 —— 所以测试方向是"断言解析结果必须为空 + 后端 VO 测试断言不含几何字段"，
   而不是"想办法把几何补上"（那正是 §46 假几何的老路）；
③ S-le 探针从 3→2 **不是我把调用点删了**，是 §39 那个假 `legId` 本来就不该存在（详见下面"证据"）；
④ 我第一版给 `S-lc` 写的预期注释是"映射器 + Repo + DemoData"，实测文件是 **映射器 + DemoData**
   （`Repo.legAction` 只收 `action: String` 形参、不含字面量）—— 判据的**说明**也会错，所以数对了还要回头看那两处到底是哪些文件。

**代码侧五项**：
- `data/model/ServerStatus.kt`：**6 套后端枚举的唯一出口**（`FreightOrderStatus` / `LegStatus` / `ProductOrderStatus` /
  `ReviewStatus` / `TaskItemStatus` / `HandoverStatus`），每套都有 `fromCode`（未知码 → null 而不是 0）、
  `label`（后端给了 `statusName` 优先用后端的名）、`tone`（`BusTone`）、以及"是否进行中/是否完成"的**语义谓词**；
  `STATUS_UNAVAILABLE_LABEL = "状态暂不可用"` 是未知码的统一文案；缺键哨兵统一 **-1**（不是 0，§14）。
- `docs/STATUS_MAPPING.md`：五列表（服务器码值 / Android enum / 中文文案 / 允许操作 / 禁止操作），
  §4 段码表逐码对齐 `TransportLegStatusEnum.java:24-36`，并写清"11 是唯一完成码、99 异常不许客户端自行重试到 0"、
  以及**与小程序的口径分歧**（小程序 `handover.js` 有自己的跳法，以服务端 `canTransit` 为准）。
- `ui/driver/DriverLegStateMapper.kt`（新）：司机动作状态机单源。`LegDriverAction(api,label,target,needsPhoto)` 十项、
  `nextActionOf(status)`（PLANNED/COMPLETED/EXCEPTION/未知 → **null**，页面显示 `waitingReason` 而不是一个能点的假按钮）、
  `stepsOf`（`handoverRequired` 时 8 步、否则 7 步）、`completedCount`、`waitingReasonOf`。
  `needsPhoto=true` 的 `handover-confirm` **不在段卡上直接发**，而是把用户导到交接列表（tab=1）去拍照，
  避免"无凭证 handover-confirm 被后端拒/静默成功"。
- `RoutesScreen.kt` / `HandoverScreen.kt`：删掉两张本地假表（`nextStatus()`、`LEG_ACTIONS`+`legStep()`）和 `copy(status = …)`；
  写操作统一成 `Repo.legAction` → **必然后跟回读**（`Repo.driverLegs()` / `Repo.currentLeg()`），回执只说后端回读到的状态；
  演示模式、段读不到、状态没变 四种分支各有独立文案（不写成"成功"）。时间轴/圆点统一用 `leg.done`/`leg.statusTone`。
- `Models.kt`（§36）：`TransportLeg` 补 `handoverRequired/handoverStatusName/handoverId`，
  `parse` 的候选名**只加不减**：`legSequence`、`fromStationName`、`toStationName`、`plateNo`（旧名继续兼容旧报文）。
- §39 两个必 400：`Api.post` 加 query 重载 ⇒ `Repo.confirmStationAction` 把 `orderId` 放 query；
  `WorkbenchViewModel.finish` 不再拼 `"shift-$id"` 打段接口，改为 `Repo.arrive(shiftId, 末站 id)`
  （`/driver/arrive` 到末站时后端收口执行记录，`DriverAppServiceImpl.java:914-920`）；末站 id 缺失**不提交**并给出口文案。

**证据**（每项都有输出，没有输出就等于没跑）：
- 测试：`StatusMappingContractTest` **17** 例（逐码对齐三套枚举 + 未知码不当 0/完成 + `statusName` 优先 + 文案子串不得猜状态）、
  `DriverLegStateMapperTest` **11** 例（把后端 `TRANSITIONS` 抄一份逐对断言 + 步进动作表 + "4 是前往起点不是已完成"反例 +
  8-不能-complete + PLANNED/EXCEPTION/未知无司机动作 + 时间轴长度 + `needsPhoto`/矛盾态文案）、
  `DriverLegRespContractTest` **4** 例（按 `AppDriverLegRespVO` 手写报文逐字段"非默认值"守卫 + 后端不下发段级几何 ⇒ 解析必空 + `{}` → -1/"状态暂不可用"）。
  全量 **17 类 189 tests / 0 failures / 0 errors / 0 skipped**（只从 `app/build/test-results/testDebugUnitTest/*.xml` 汇总读，不抄控制台摘要；P2-1 是 14 类 157 例）。
- 构建：`:app:assembleDebug :app:testDebugUnitTest` → `BUILD SUCCESSFUL in 40s`；文档收口后增量复跑 → `BUILD SUCCESSFUL in 18s`（43 tasks UP-TO-DATE，exit 0）；
  **最后一批注释与行号引用校正后**再复跑 → `BUILD SUCCESSFUL in 27s`（8 executed / 35 up-to-date，exit 0），
  同批四组判据（status / domain / location / envelope）复跑仍全部"判据通过"，totals 仍是 **17 类 / 189 tests / 0 failures / 0 errors**。
- 判据：`tools/check_status_probes.sh`（新，17 条 S-*，exit 0）
  S-a 0 / S-b 0 / S-c 0 / S-c2 2 / S-c3 6 / S-d 6 / S-e 10 / S-f 0 / S-g 0 / S-h 3 / S-i 64 / S-j 4 / S-k 17 / S-la 0 / S-lb 0 / S-lc 2 / S-ld 2 / S-le 2；
  旧三套判据（H 分域、I 定位与上报、J 信封）复跑仍 exit 0，基线未漂移；宽口径真实性探针按 `CONTEXT_LOCK.md` §八 ① 的原样命令复跑仍是 **30 行**（与 P1-2 基线一致，没有新增违规）。
- lint：`:app:lintDebug` → `BUILD SUCCESSFUL in 4s`（分析任务 UP-TO-DATE，报告对应 ⑨ 的代码状态），
  计数从 `lint-results-debug.txt` 末行读 = **0 errors / 48 warnings**；
  `lint-results-debug.xml` 根节点**没有** errors/warnings 属性（实测 `None`），只有 79 个 `<issue>` 节点（48 Warning + 31 Information）——
  以后要机器读 lint 结论请读 txt 末行或按 severity 数节点，**不要**写"从 XML 根属性读 errors"。

**没做 / 不许当成已做**：
① `Repo.legAction` 形参仍是 `String`（审计 §39 建议改 `Long` 做编译期守卫）—— 改签名会波及 §26/§27 两处调用点与既有测试，
   留到"段动作接口类型化"单独一批；
② §36 的 ②③：`TransportLeg` 里 `navigationPolyline/fromGeo/toGeo` **字段仍在**（遵守"不删字段"），
   但渲染路径已不再把它们当几何来源；真正的段几何要改从 `Repo.routeGuide()` 取，与 §44/§46 的地图接线同批；
③ **无 Robolectric / MockWebServer 网络往返级断言**（`ENVIRONMENT_BLOCKED`）："POST 之后真的回读了"、"query 里真的带上了 orderId"
   目前是代码读证 + grep 判据，不是拦截器级断言；
④ 真机未验：段卡在 `legSequence` 并列/缺失时的排序表现、交接门禁的实际点击路径。
分层结论：**CODE COMPLETE / TEST COMPLETE(纯 JVM 层) / DEVICE 未验证 / E2E 未验证**。

### P2-1 标量信封 / 角标单源 / 交接凭证照片（§22 + §23 + §52 + §7，本轮完成）

**这一轮的三条"实测推翻想当然"**（先记这个，因为它比代码更容易被后人重犯）：
① 台账写"后端 `photoUrl` 是否真回读**未核实**"——核实结果是 `DriverAppServiceImpl.java:1688` **已经** `setPhotoUrl`，
   我第一轮 grep 用词太松（写了 `photo` 没写 `setPhotoUrl`）漏判，差点误记成"需要最小后端扩展"；
② 测试首版断言"空 URL 提交时 body 里有 `"photoUrl":""`"——**错**，`Api.rawExecute:262` 会把空串字段整个丢弃，
   真实契约是"没有这个字段"。改的是**断言**，不是改代码迁就测试；
③ §22 被记成"角标恒 0"的小毛病——实测它**死锁了正式包的装车/妥投动作**：
   `WorkbenchScreen.photoPicked` 拿 `uploadPhoto().url`，url 恒空 ⇒ 一律拦停，硬规则"宁可暂时不能操作也不假装完成"。
   这条后果台账没记过，是本轮读 §7 方案 2 时才追出来的。

**代码侧四项**：
- `Payload` 新增标量通道：`toPayload` 里非 `JSONObject`/`JSONArray` 的 `data` 才 `scalar = data`，同时仍塞 `{"v"}` 兼容旧读点；
  `scalarLong()/scalarStr()/longOf()/strOf()` 四个出口按**实际类型**取值。
- `Repo.unreadCount()` / `driverUnreadCount()` 从 `Int` 改 `Long?`（读不到=null，不冒充 0）；`uploadPhoto` 读真实 URL。
  `saveAddress` 保持返回空 URL 并注释写明后端是 `CommonResult<Boolean>`，**从来没有 URL**，所以不存在"URL 为空 = 失败"。
- `AppState.unread` / `driverUnread` 改 `private _x + asStateFlow()`，外部**编译期写不了** `.value`；
  唯一入口 `report*(serverCount, listCount)` 两路**取 max**，两路都读不到时**保持上一个值**（不写 0）—— 直接消灭"红点自己闪没"。
  7 个原写入点全部改造（`AlertEngine` 3 + 用户消息页 + 包裹页 + 司机消息页 2），列表侧只在 `feed.error == null` 时传计数。
  与原方案的偏离：审计写"集中到 `AlertEngine`"，实际集中到 `AppState` 的写入口——入口设在某个**调用方**身上，
  下一个调用方仍可绕过；设在**状态所有者**身上才是结构约束。
- `HandoverTask` 新增 `photoUrl`，`status` 改 `statusName` 优先（后端 `status` 是 0..7 数字，旧读法渲染成"0/4"且 `isDone()` 恒不成立）；
  `HandoverScreen.confirm()` 重写为：`busy` 防重入 → 缺凭证**拦在提交前**（不"先提交后补"，因为后端 `HandoverServiceImpl:196-197`
  对已完成交接幂等 return，先提交就永远补不上）→ 提交后**回读** `/driver/handovers`，只有列表里那条真带 photoUrl 才说"已入库" →
  **删除** `items.map { it.copy(status = "已交接") }`；卡片新增凭证照片缩略图。

**证据**：`ScalarEnvelopeContractTest` 17 例（`data:5`→5 / `data:0`→0 且非错误态 / 502→error / 对象形态兼容 /
裸 URL 直返且 multipart 带 name+type / `data:null`→读不到而非空串成功 / 角标 max 与"读不到不写 0" /
用户-司机角标不串域 / **未绑定时 `server.requestCount == 0`** / 整链路 upload→confirm→回读含 photoUrl /
空 photoUrl 时字段整个不出现在报文 / `statusName`+`photoUrl` 两支解析）；
全量 **14 类 157 tests / 0 failures**；`BUILD SUCCESSFUL in 48s`；lint **`0 errors, 48 warnings`**；
`check_envelope_probes.sh` E-a 0 / E-b 3 / E-c 0 / E-d 0 / E-e 1 / E-f 1/1 / E-g 1 / E-h 1（exit 0）；
`check_domain_probes.sh`、`check_location_probes.sh` 复跑仍 exit 0（§八 基线未漂移）。
判据踩过的两个坑：**E-e 首版数到 2** 是因为 `Models.kt` 另一个模型也有 `multiStr("statusName"…)`（正当），
按内容把 pattern 收窄到 `status = o.multiStr("statusName"`；**E-g 首版数到 2** 是因为演示分支文案也含"交接未提交"，
收窄成整句拦停文案 —— 都是**收窄 pattern**，不是把目标数改成 2 蒙过去。

**没做 / 不许当成已做**：真机端到端"拍照→后端落库→刷新还在"（`ENVIRONMENT_BLOCKED`，审计 §57 新增一行）；
`needPhoto` 后端不下发 ⇒ 必须拍照这条仍只能靠 Android 自己判类型，等后端加字段；
`NotifyMsg.orderNo` 幻字段（后端只有 `orderId`）只登记未修；`AppDriverHandoverRespVO:52` 注释与枚举矛盾只登记不动后端。
分层结论：**CODE COMPLETE / TEST COMPLETE / DEVICE 未验证 / E2E 未验证**。

### ⑦ 定位与上报稳定化（P1-3，本轮完成）

**本轮最重要的一条不是代码，是"先查后端再定方案"**：审计 §45 原方案写的是"有界环形队列 + 失败按序重试 + 报文带 `locateTime`"，
我按它准备动手时逐字段读了 `AppDriverLocationReqVO.java`，三条事实把方案否了：
① VO **没有任何客户端时间字段**（只有 `driverId/shiftId/longitude/latitude/speedKmh`）；
② `DriverAppServiceImpl.reportLocation` 给每条上报盖 **`LocalDateTime.now()`**，并按上报次数追加 `VehicleLocationTrackDO` 轨迹行；
③ 于是"重放一个 3 分钟前的隧道点"在服务端等于宣称"司机此刻在这个坐标" —— **用真实坐标制造虚假的实时性**，
直接撞用户红线 **"宁可没有上报，不允许假上报"**。改成**合并语义**：`LocationReportPolicy.decide()` 只发当前最新那一个 fix，
未 ack 期间每来一个新点 `superseded += 1`，通知与工作台都写"已丢弃 N 个未确认点"。
**代价没有藏起来**：弱网中间点**不可恢复地丢失**，管理端轨迹缺口仍在 —— 这条从"静默丢"变成"看得见地丢"，不是"补报能力已具备"。

- **§17 已修（换算收到唯一出口）**：`Locator` 三处 `Geo(it.latitude, it.longitude)` 出口全部改走 `Locator.toGcj02`，
  `CoordinateTransform` 生产调用点 **0 → 2**（`Locator` + `RealRoadRoute.gcj02Points`）。
  实测重庆 `(29.5325,106.5765)` → `(29.529603,106.580187)`，偏移 **480.6 m**（落在 §17 写的 150–700 m 区间）。
  `§17` 列的 6 个消费方（`nearbyBuses`/`busLines`/`reachability`/`reportLocation`/`reverseGeo`/`DriverLocationService.observe`）
  是**自动**拿到 GCJ-02 的，一处没漏 —— 这正是 §17 修改方案第 3 条"不要在每个 `Repo.*` 里各转一次"想要的形态。
  **两条残留按局限记**（不当已解决）：`inChina()` 是**矩形**，`(15.0,105.0)`（东南亚）也会被误转 ——
  用 `已知限制：国境判断是矩形…` 一条用例**钉住它**而不是删掉它；OEM 网络定位若本身回 GCJ-02 就是**转两次**，只有真机能判（§57）。
- **§17 方案 5（跨仓文档登记）已做，并顺手修掉文档的一处假引用**：`cargo-post-platform/docs/REAL_ROAD_ROUTING_ARCHITECTURE.md` §4
  新增"Android 客户端边界"一行 + ⑦ 补记段落（明写"司机 GPS = GCJ-02"这一行**在 ⑦ 之前对 Android 不成立**，是愿望不是事实）。
  核对时发现文档引用的 `is_in_china` **在代码里不存在** —— 真名是 `out_of_china`(`coordinate.py:29-35`)，已改；
  并把两侧判定**逐条对过**：Kotlin `inChina()` 与 Python `out_of_china` 是**同一个矩形**（经度 `72.004..137.8347`、纬度 `0.8293..55.8271`），
  所以"东南亚点被误转"是**两侧共有的局限**而非 Android 独有 bug —— 这条我原本在文档里写成"未对拍的待办"，实测之后改成事实。
  `tools/verify_device.sh` 的 logcat 关键行过滤加上 `BUS_LOC`，并新增"定位与上报核对清单"六条（45 秒停报、丢弃计数、
  关闭全部 Provider 不崩、折线是否贴路、偏移≈0 即 OEM 已给 GCJ-02）；脚本无设备不能跑，**只做了 `bash -n` 语法校验（SYNTAX OK）**，
  这就是它当前的全部证据等级。
- **§45 已修（含一处硬阻塞解除）**：`reportLocation(shiftId: Long?, …)` —— 后端 `shiftId` 早已可选（`DriverAppServiceImpl.java:1238` 走 `currentLegShiftId()` 兜底），
  Android 侧那句"shiftId 必带"的**注释是陈旧的**，而据此写的 `Running.shiftId <= 0` 硬拦的实际后果是
  **运输段模式（无班次）的司机永远不上报**，监控上等于车凭空消失。现无班次也照发（body 不带该键），
  `shiftNote()` 把语义讲给司机："车辆位置照常更新，班次归属由后端按当前活跃段判定"。
  新增 **45 秒新鲜度闸**（`MAX_FIX_AGE_MS`，= 5 秒采样 × 9 次连续拿不到）：过期一律 `Hold` 并写明原因，
  三种 Hold 文案各对应一个真实原因（未绑定 / 等待定位 / 信号已中断 N 秒）。
- **新 §65（接线时暴露的两处）**：① `RealRoadRoute` 以前只给 `points`（原始系）+ `crs` 标签而**没人读标签**，
  `Repo.routeGuideOf` 直接把可能 WGS-84 的折线交给 GCJ-02 底图 —— 画出来**整条偏 500 m 且看起来完全正常**；
  现新增 `gcj02Points`（`points` 保持原样不改数据，换算只发生在读的一侧），`polyline` 与 `heading` 都改用它，
  顺带消掉一处"车辆点(GCJ-02) 与折线点(可能 WGS-84) 比方向"的跨系相减。
  ② `gcj02ToBd09` 漏了百度固定偏移 `+0.006/+0.0065`，往返实测残差 **6.5e-3 度 ≈ 700 m**（`0.006°`≈668 m、`0.0065°` 在重庆≈630 m），
  补上后 **6.3e-7…9.1e-7 度**。**这条 ⑦ 之前撞不到，因为整个换算器生产调用点是 0** —— 把死代码变活之前必须先审它。
- **顺带修掉一个会让手机崩溃的真 bug**：`Locator.onceFix` 在**所有 Provider 都关着**时，
  `locations(context).first()` 随 flow 正常结束抛 `NoSuchElementException`（不是超时、不是 null）——"拿不到定位"变成崩溃。
  改 `runCatching{…}.getOrNull()`，超时/关闭统一退回 5 分钟内旧缓存或 `LocFix.UNAVAILABLE`；
  `locations()` 的 KDoc 补一句"**正常完成意味着定位源已死**"，服务侧 `observe` 据此写成 `while (isActive)` +
  `delay(RESTART_DELAY_MS)` 重试，并在通知里写明"定位监听已退出…正在重试"（原先一次注册失败就再也不订）。
- **判据固化**：`tools/check_location_probes.sh`（§55 I 组，8 个数字 + 退出码）+ §56 两行新的不许回潮不变量
  （"上报报文不得带客户端时间"、"丢点必须可见，过期点不得冒充当前"）+ `CONTEXT_LOCK.md` §八 ⑧。
  与前几轮同一课：**判据按内容/函数名，不按行号**；每条都有负控制（实测人为加一处 `Geo(l.latitude…)`
  或一段手写偏移公式，L-b / L-e 当场报红并 exit 1）。

**验证（真实 gradle 输出）**：`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -Dorg.gradle.jvmargs=…GBK`
→ **`BUILD SUCCESSFUL in 58s`（53 actionable tasks: 16 executed, 37 up-to-date）**；
**13 个测试类 / 140 tests / 0 failures / 0 errors / 0 skipped**，本轮新增三类：
`LocationReportPolicyTest` **15**（三种 Hold、Send 带/不带班次、"重试只发最新点"、区间 3..60、退避 20/40/60/60、单调不退、`shiftNote` 文案、账本 4 例）、
`CoordinateTransformTest` **12**（重庆实测点 + 100..900 m 带宽、往返 1e-9、BD09 反算 1e-5、境外不变换、矩形局限被钉住、
`convertAll` 保序、标签解析、`gcj02Points` 两系对照）、
`LocatorCoordinateTest` **6**（导出即 GCJ-02、`toGcj02 ≡ CoordinateTransform.convert`、境外原样、`TrackFix` 字段、
age 单调钳非负、`LocFix` 四态与精度文案回归）；`DriverStateTest` 9 → **11**
（`本机没有班次时上报不带 shiftId 但请求照发` 钉 body 键集恰为 `{driverId, longitude, latitude, speedKmh}`；
`报文不许出现后端 VO 没有的字段` 反向钉 `locateTime/clientTime/gpsTime/reportTime/time` 全 0）。
`lint-results-debug.txt` **0 条 `: Error:`**；`bash tools/check_location_probes.sh` 与 `bash tools/check_domain_probes.sh` **均退出码 0**；
窄口径 **4** / 宽口径 **30** / `GeoMath` **22** / 假步进 **6**（§24 未修）/ `canArrive` **0** / `ui` 旧直线字段 **0** /
`MissionStop` **20** / `timelineOf|.ordered(` **3** / `DemoData` **3 文件** / 安全 grep **0**（`localhost` 主源码 0、`.kts` 4 行正当、keystore 0、拦截器 0、凭证 0、key 字面量 0）。

**没做的部分如实记**：
① 工程**没有 Robolectric**，`Locator.fixOf` / `trackFixes` / 前台服务 `observe` 重试环 / `notify()` 文案
   **零测试覆盖** —— 能测的都抽成纯函数测了（策略、账本、换算），剩下的仍是 `ENVIRONMENT_BLOCKED`（§57 新增行），
   补法与依赖投入记在 §60 第 7 项，本轮**没有**为了凑覆盖去引框架（引框架会连带踩 GBK 路径那套坑，是独立一件事）；
② §45 的**持久化待发队列是"有意不做"**，不是"没做完"（理由见本节第一段）；进程被杀后不补报，恢复后从最新 fix 重新开始；
   `superseded` 计数是本机内存值，重启归零、不入后端；
③ 45 秒阈值与 3..60 秒退避区间是**工程判断**（9 个采样窗口），不是后端约定，也没有真机弱网数据支撑 ——
   真机若发现停报太早/太晚，改的是常量，不该改成"拿过期点续报"；
④ 端到端"App 与小程序同点比对 `recommendedStation.id`"未跑（BLOCKED #4 后端无数据 + 无设备）；
⑤ `crs=WGS84` 的真折线目前**没有真实数据穿过**（后端 `/driver/route` 一律标 GCJ-02），
   §65 的 `gcj02Points` 是契约前置防御，等 GraphHopper 直连下发原始系时复测；
⑥ 通知渠道/前台服务权限、Android 14 的 `foregroundServiceType` 细则未动（本轮只改上报链逻辑，不扩范围）。

### ⑥ 用户/司机分域复核（P1-2，本轮完成）

**复核结论**：用户域本来就干净（P-a/P-a2 实测 0，没有一个司机读点），位置上报也只在司机域（§45 的担心不成立，
见 §64 第 4 点）。但 §29 **仍未修**，而且逐页读的时候抓出三处**任何矩阵行都没覆盖过**的串域（新 §64）。

- **§29 ① 角色切换入口**：`MineScreen.kt:163` 的"切换为司机模式"改走 `MineViewModel.enterDriverMode`(`:85-97`) ——
  先 `Repo.bindDriver()`，后端没授予 `driverId` 就**不进司机域**，页面内显示"该账号未绑定司机档案…"。
  与 `LoginViewModel` 从此是同一条纪律（此前工程里存在"登录有校验、切换没有"两套标准）。
- **§29 ② `Repo` 层结构守卫**：新增 `driverIdParam()`(`Repo.kt:1115`) + `DriverUnboundException`(`:65`) +
  `DRIVER_UNBOUND`(`:68`)，19 个司机调用点全部改走它。`mapOf("driverId" to driverId)` 字面量实测 **0** 处。
  守卫抛在**参数求值之前**，所以 `driverId=0` 的 URL 是"构造不出来"而不是"发了被拦"；
  四个身份引导接口（profile/shifts/pickups/earnings，按 token 识别）**不加守卫**，否则绑定永远不会发生。
- **§29 ③ 换账号残留**：新增 `Prefs.clearDriverBinding()`，`Repo.saveSession` 写新 token 前先清；
  档案返回"非演示 + 有条目 + 无 driverId"→ 视为撤销并清绑定，返回 `data:null`/失败/演示回落 → 视为**未知**并保留
  （第一版写成"driverId<=0 就清"，自查发现一次网络抖动会把真司机锁死，改成三态并补用例）。
- **§64 三处串域已修**：`DriverShell.kt:38` 点"用户版"先 `AppState.setRole(Role.USER)`（否则 `AlertEngine`
  继续按司机域轮询 `/driver/messages`）；`AlertEngine.kt:64-92` 司机通知落 `Routes.DRIVER_MESSAGES`、单号进正文，
  不再深链村民端 `trace/$orderNo`；`Routes.kt:37 DRIVER_ONLY` + `BusPostRoot.kt:183-184 deepLinkAllowed()`
  给 Intent 深链加角色闸（不允许就留在当前页，不猜不跳不崩）。
- **判据固化**：`tools/check_domain_probes.sh`（§55 H 组）+ §56 两行新的不许回潮不变量 + `CONTEXT_LOCK.md` §八 ⑦。
  与 §55 C 同一课：**行号白名单会漂，内容白名单不会** —— 本轮 `Repo\.kt:2[45][0-9]` 因文件头插 14 行漏放两行，
  窄口径一度从 4 涨到 6，改成 `Repo\.kt:.*(mock 标注|适合当日运输)` 后恢复 **4**（宽口径 **30**）。

**验证（真实 gradle 输出）**：`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL；
**105 tests / 0 failures / 0 errors（10 类）**，其中新增 `DomainSplitTest` 7 例（未绑定时 12 个司机调用点
`server.requestCount == 0`、绑定后带后端授予的 `driverId=61`、身份引导接口不受守卫影响、换账号不继承、
空档案掉绑定、`data:null` 保留绑定、`enterDriverMode` 两分支）；`lint-results-debug.txt` **0 条 `: Error:`**；
`bash tools/check_domain_probes.sh` → P-a 0 / P-a2 0 / P-b 0 / P-b2 21 / P-c 2 / P-d 2 / P-e 1 / P-f 5，末行"分域判据通过"；
安全 grep（`127.0.0.1|localhost`、`*.jks`、`HttpLoggingInterceptor`、凭证/key 字面量）仍全部 **0 命中**。

**没做的部分如实记**：① 司机壳顶部**没有**专门的"未绑定司机档案"硬状态条（§29 原方案 ②），沿用三个 tab 各自的
`ErrorState`；② 工程**没有 Robolectric / Compose UI 测试**，`DomainSplitTest` 只覆盖 Repo + ViewModel 层，
纯 Compose 行为（退出用户版后不再轮司机域、点通知落在司机消息中心、杀进程从通知栏重进不落司机壳、
非司机账号点"司机模式"停在原页）四条**只改了代码 + grep 为绿**，真机未验证 → §57 新增一行 `ENVIRONMENT_BLOCKED`；
③ 用户/司机两套未读水印仍各存本机 `Prefs`，跨设备不同步（与小程序一致，非本轮引入）。

**"司机端有没有把用户域的演示态带进来"这一半的复核结果（实测，不是印象）**：
司机侧 6 个页面各调 1 处 `DemoNote`（`DriverMessagesScreen:127`、`DriverNavScreen:221`、`EarningsScreen:84`、
`HandoverScreen:187`、`RoutesScreen`、`WorkbenchScreen`），用户侧 18 处 —— 但 `Bits.kt:46` 的 `DemoNote`
函数体首行是裸 `return`（下面整段横幅实现被注释），**24 处调用全部空转**。所以司机端的问题不是"混进用户域演示数据"，
而是**演示态在两个域里都不可见**，与 §30/§47 同根（写操作回执如 `HandoverScreen:132-133`、`RoutesScreen:103`
的"演示模式：未提交后端"文案仍正常显示，那是不依赖 `DemoNote` 的路径）。
本轮**没有**顺手改：它属演示诚实性（P0 台账 §30/§47），不属硬规则 1 的分域；改法与判据仍按 §30 原方案执行。

### 真实道路链路收口（P0-2 / P0-3 / P0-4 / P0-5）
口径与实测记录写在 **`cargo-post-platform/docs/REAL_ROAD_ROUTING_ARCHITECTURE.md`**（唯一事实来源，改链路必须同步改它）。

- **算法服务 `/api/v1/route`**：`real_road_route` = 高德路网 → GraphHopper(重庆 OSM) → `available=false`。
  euclidean 分支连几何一起删掉（`distance.py::_euclidean_route_with_polyline` 不复存在）；
  高德失败返回 `available=False` 而不是两点直线。起终点同点是唯一例外：`provider=same_point`、0 公里、**无 polyline**。
- **Java 后端**：`DriverAppServiceImpl.getRoute()` 的四级取线（走廊切片 → 任意线路走廊 → 后端直连高德 → 算法服务）
  任一失败即 `RouteFetch=null` → 折线在缺口处 **截断**（`break`），不再跨缺口连线、不再打 `euclidean` 标签；
  `routeAvailable` / `routeComplete` / `routeProvider` / `routeDistanceMeters` / `routeDurationSeconds` 全部如实下发。
  顺带修掉两个真 bug：高德返回的 `distance/duration` 之前被丢弃（导致没有诚实里程）、
  `status >= 2` 被当成"已完成"（司机还没做完就被跳过），现按 `TaskItemStatusEnum` 精确判定。
- **来源白名单四处同步**（新增来源必须四处一起改）：
  Python 链路 / `service/geo/RealRoadProviders.java` / `core/map/RouteSource.kt` / 后台 Vue `REAL_ROAD_SOURCES`。
  白名单不是黑名单：`""`、`none`、`estimated`、未知写法一律不可信。
  修正的错标：GraphHopper 结果原先在 Java 侧被丢弃、在实时公交里被打成 `EUCLIDEAN`、在运输段被打成 `ESTIMATED`。
- **监控折线**：`fetchPlanPolyline` 返回 `PlanSegment(points, provider)`，缺口截断，来源逐段收集，
  多来源如实给 `MIXED`，新增 `routeComplete`。
- **公交线路折线（本轮补刀，P0 级违规）**：`AppBusServiceImpl.fetchRoutePolyline` 原先在算法服务不可用时
  `addFallbackPoint(from); addFallbackPoint(to)` —— 把**两站直连**塞进"道路轨迹"数组，而 `AppBusLineRespVO` 的
  Schema 还明文授权"前端回退到 points 直线"。现在：该段没有真实道路几何就 `break`（只返回已取到的前缀），
  白名单外的来源（`euclidean` / 空 / 未知）一律当"没有路线"，`addFallbackPoint` 整个删掉，
  Schema 改成"每个点都来自真实路网，断线返回截断前缀，前端不得用 points 补线"。
  同时修掉来源改写链：`persistPolyline` 落库写真实出处（不再硬写 `AMAP`），
  读取侧 `loadStoredPolyline` / `RouteCorridorService.corridor` / `MonitoringServiceImpl` 三处改判白名单而不是判 `"AMAP"` 字符串，
  `DispatchServiceImpl` 预热时不再把库里已有的（可能是 GraphHopper 的）几何重新标成 `AMAP`。
  断过线的线路**不落库**（半条几何不能当整条走廊长期复用），只进 5 分钟内存缓存。

### 验证证据（本轮实测，不是"应该能跑"）
- **后端 JUnit 现在真能跑（本机没有 mvn 也不再是阻塞）**：`bash C:/temp/check-backend-tests.sh <测试类全限定名...>`
  → javac 编译整棵 `src/test/java`（**36 个测试类，status=0**），再用 `C:/temp/RunTests.java`
  （junit-platform-launcher，仓库里没有 console-standalone jar）执行。
  全模块 **309 tests found / 309 successful / 0 failed**。
  坑：`java -cp` 与 `javac @argfile` 都走 ASCII junction；classpath 必须是**单行**（`sed -n '2p'` 带换行会把 `-cp` 截断成非法标记）；
  类名要从 `C:/proj/<module>/src/test/java/...` 正则剥掉模块前缀，否则 `ClassNotFoundException`。
- **公交线路回归（本轮新增 4 例，全绿）**：`AppBusServiceImplTest` 21 tests —
  `linePolyline_withoutRoadSource_returnsNullNotStationLine`（没路就 null，不交站点连线）、
  `linePolyline_truncatesAtFirstSegmentWithoutRoad`（B→C 断线即停，C/D 坐标零出现，`verify(algorithmClient, times(2))` 证明没继续请求）、
  `linePolyline_acceptsGraphhopperOsmSource`（OSM 来源被接受）、
  `linePolyline_rejectsNonRoadProviderLabel`（`euclidean` 带几何也不可信）。
- **后端编译**：`bash C:/temp/check-backend.sh` → 476 classes、`status=0`、无 `错误:` 行。
  配方（本机没有 mvn）：`javac @argfile` 的路径必须全 ASCII —— `C:\m2repo`、`C:\proj` 是指向本工作区与 maven 仓库的
  **junction**，因为 JDK 用系统 ANSI 代码页（GBK）解码 `@argfile`，CJK 路径必乱码；
  Lombok 只对显式传入的源文件生效，所以整模块 `.java` 全部列出，不能靠 `-sourcepath` 隐式加载。
- **算法服务**：`pytest tests/test_route.py tests/test_graphhopper_provider.py -q` → **25 passed**。
  含新回归：`test_route_amap_failure_does_not_draw_straight_line`、`test_route_without_any_road_source_returns_unavailable`、
  `test_route_falls_back_to_graphhopper_and_returns_gcj02`（钉住坐标系只转一次）、`test_route_graphhopper_unavailable_returns_unavailable`。
- **GraphHopper 实机启动（原 BLOCKED #3 解除）**：
  `java -Xmx1500m -jar tools/graphhopper/graphhopper-web.jar server tools/graphhopper/config.yml`（必须在 `cargo-post-platform/` 下执行）
  → 监听 **8080**（admin 8081），**不是文档里写的 8989**；`/info` → GraphHopper **8.0**，profiles 只有 **car, bus**，bbox 仅覆盖重庆。
  端到端（无 `AMAP_KEY`，走 B 档）：解放碑 → 江北，`provider=graphhopper`、159 点、道路 9.904 km / 直线 6.934 km（绕行 1.43）、
  折线首点距请求起点 **20 米**（证明 GCJ↔WGS 没转错；直接把 GCJ 坐标喂给 GH 会给出 17.3 km 的荒谬结果）。
  端口指向死进程 → `{"available":false,"provider":"unavailable","reasonCode":"REAL_ROAD_UNAVAILABLE"}`，响应里没有 polyline 字段。
- **客户端**：`:app:testDebugUnitTest --tests "*RealRoadRouteValidatorTest*" --tests "*RouteGuideContractTest*"`
  → BUILD SUCCESSFUL，**16 + 11 tests, 0 failures**。新增用例钉住"后端下发的标准标签全部在白名单内"（防白名单漂移导致线路静默消失）。

### ⑤ 任务点业务化 + GPS 不再放行到站（P1-1b，本轮完成）

- **§32 已修**：`canArrive()` / `ARRIVE_RADIUS_METERS` / VM 里那个从未被读写过的 `canArrive` 状态位全部删除
  → `grep -rn "canArrive\|ARRIVE_RADIUS_METERS" app/src/main/java` **0 命中**；「确认到达」`enabled = !busy`。
  依据是后端事实：`AppDriverArriveReqVO` 只收 `shiftId + stationId`，`Repo.arrive`(`Repo.kt:983`) 的 body 只有
  `driverId/shiftId/stationId`，**不含坐标**。这是"禁止 GPS 自动完成业务"的镜像面 —— 也不许用 GPS 自动**放行**。
- **与小程序有意分歧并记录**：`workbench.js` 的 50 米门槛属 §16/§48 定性的"参考实现自身违规"，本轮**不对齐**（D-017）。
- **距离文案只有一个出口**：`MissionMarkers.nextStopDistanceText(roadMeters, straightMeters, targetName)` —
  道路值优先并写"（后端路网）"；无道路值时直线值必须带"仅表示远近，不是道路里程"；两者都缺就不出现数字。
  用例 `距离文案优先后端道路值` 特意断言"直线 300 不得混进道路文案"。
- **⑤ 任务点业务化**：`StopState` 加第四态 `FAILED`（后端 8 失败），`StationTimeline` 里失败点红、连接线不画成已走通；
  `MissionMarkers.timelineOf/stateOf/ordered` 让工作台「调度任务点」时间线只读后端 `stops`
  （序号=`visitSequence`、动作=`actionName/actionType`、件数=按动作分单位的 `quantity`（人/件）、状态色=`status`），
  `x/N 已完成` 只数 `status == 7` 的点；后端没给 `stops` 时才退回原有「经停站点」视图，行为不回归。
- **新发现并修掉 §63**：`DriverNavScreen.kt:244` 曾把后端**直线**字段 `nextStationDistanceMeters` 当"剩余里程"显示，
  与该字段自己的 KDoc 和 `CONTEXT_LOCK.md` §四"UI 只准读 `*Road*` 系列"矛盾 → 改读 `nextStopRoadDistanceMeters`，
  字段与解析**保留**不删；判据 `grep -rn "nextStationDistanceMeters" .../buspost/ui` = **0 命中**。
- **验证**：MissionMapTest 12→**18** 例，与 RouteGuideContractTest 两组 **30 tests / 0 failures**；
  全量 `:app:testDebugUnitTest`（9 个测试类）**98 tests / 0 failures**；`:app:assembleDebug` BUILD SUCCESSFUL；
  `:app:lintDebug` BUILD SUCCESSFUL（在最后一处改动之后重跑），`lint-results-debug.txt` **0 条 `: Error:`**，
  被改文件里唯一的 finding 是既有的 Information 级 `AutoboxingStateCreation`（`DriverNavScreen.kt:94/:100`，与本轮改动无关），
  新增文件 `DriverMissionMap.kt` / `MissionMarkers.kt` / `MissionStop.kt` 在报告里 0 命中。
- **没做的部分如实记**：§32 原方案的 Robolectric 点击断言 body 未做（工程无 Robolectric）；
  `FAILED` 态目前只接进工作台时间线，`RoutesScreen`/`HandoverScreen`/`BusScreen` 仍是三态 `stopStateOf`；
  真机上"任务点顺序与状态色"核对仍是 `ENVIRONMENT_BLOCKED`。

### 司机任务地图接线 + `/driver/route` 任务点字段补齐（P1-1，本轮完成）

**先发现并修掉一个静默失败（审计 §62）**：`Repo.routeGuideOf` 只把后端 `stops/stations` 当 `GeoPoint` 读坐标，
`AppDriverRouteRespVO.Stop` 的 9 个业务字段（`visitSequence` / `actionType` / `actionName` / `orderId` / `orderNo` /
`quantity` / `status` / `statusName` / `estimatedArrivalTime`）**整块没被读**。后果不是崩溃，是"看起来正常"：
任务点没有顺序、没有动作、没有件数、没有状态，司机端第一屏只能靠货物列表反推顺序 —— 而顺序本该由调度下发（硬规则 3）。

- **`data/model/MissionStop.kt`（新增）**：按后端真实键名严格解析；`actionType`/`status` 的兜底文案表**按真实枚举码建键**
  （`TaskItemStatusEnum` 0..8 不连续，顺序数组会把"失败 8"读成第 9 项）；`estimatedArrivalTime` 只认 `Number` → epoch 毫秒
  （后端 `TimestampLocalDateTimeSerializer` 上线上是数字，不是字符串日期）。
- **`RouteGuide` 增加 `stops: List<MissionStop> = emptyList()`（带默认值）**，`stations` 原样保留不动 —— 硬规则"不删字段以免破坏旧兼容"。
  同一个数组的两种读法并存是刻意的，注释里写明了理由。
- **`ui/driver/MissionMarkers.kt`（新增，纯函数无 Compose）**：排序**只**跟随后端 `visitSequence`，渲染层不重排（§二分层）；
  零坐标点直接跳过而不是补一个点；`paths()` 在 `polyline.size < 2` 或 `routeUnavailableReason != null` 时**返回空**，
  即"只有任务点，没有任何示意线"。
- **`ui/common/BusMap.kt`（唯一真地图，§44 的零调用点就此解除）**：`MapMarker` 增加 `detail` 与四种任务点 KIND，
  色相按状态区分（待办蓝 / 进行中橙 / 已完成绿 / **失败红，绝不借绿**），跳过零坐标，`snippet` 不再塞 `color.toString()`。
- **`ui/driver/DriverMissionMap.kt`（新增）**：`AMAP_KEY` 非空且未失败且有标记 → `BusMap`；否则回落 `RouteMap`，
  且当没有可信折线时明写"后端没有下发这一班的真实道路折线，地图上只有任务点，不画任何示意线。"
  任务点横条（`MissionStopStrip`）+ 脚注（任务点数 / 顺序由调度下发 / 道路里程没有就说"接口未给" / 来源标签）。
- **`ui/driver/WorkbenchScreen.kt`**：`vm.guide` 此前在 :172 加载后**从未渲染**（审计 REAL_PARTIAL 的实例），
  现在 `ReportCard` 之后渲染 `DriverMissionMap`，`me` 用上报链路的 `serviceGeo`（后端最后上报位置）而非另一个 GPS 源。

**验证（真实 gradle 输出，不是自述）**：
- `:app:testDebugUnitTest --tests "*MissionMapTest*" --tests "*RouteGuideContractTest*"` → **`24 tests completed`** / `BUILD SUCCESSFUL`。
  其中一条钉住"`{}` 空响应不崩且 `stops` 为空 + `routeUnavailableReason` 非空"，另一条钉住
  `visitSequence [2,3]` / `actionLabel [揽收,派送]` / `quantity [6,2]` / `statusLabel` / `stationId`，
  并断言 `stations.first().sequenceNo == 0` —— **键名一漂移就当场红**（§49 的契约守卫方向）。
- 有一条用例先红后修：fixture 的 stops 没给经纬度，被 geo 过滤规则全部丢掉，断言"顺序跟随 visitSequence"拿不到点。
  这是实现按设计生效、用例写漏，补坐标后转绿 —— 记录在此以免后人误读为"过滤有 bug"。
- `:app:assembleDebug` / `:app:testDebugUnitTest` / `:app:lintDebug` → `BUILD SUCCESSFUL`；
  `lint-results-debug.txt` **0 条 `: Error:`**，且报告里不出现 `DriverMissionMap.kt` / `MissionMarkers.kt` / `MissionStop.kt`（新增文件无 lint  finding）。
- 未验证项照旧：无 Android 设备在环，真地图渲染 / marker 聚合 / key 校验通过率仍是 `ENVIRONMENT_BLOCKED`（§57），JVM 单测不冒充。

### 三方对齐审计（P0-6，本轮完成）

**交付物**：`ANDROID_PARITY_AUDIT_V2.md`（1030 行 / 61 节 / 9 张三方矩阵）。
判定分布（§5，与 §17–§52 明细一一对账过）：`REAL_COMPLETE` 41 · `CONTRACT_MISMATCH` 12 ·
`REAL_PARTIAL` 9 · `UI_ONLY`/`LOCAL_ONLY` 4 · `DEMO_ONLY` 4 · `MISSING` 5 · `BLOCKED`/`ENVIRONMENT_BLOCKED` 9 ·
"明确不复刻" 9。每条非 `REAL_COMPLETE` 都给了 文件/类/函数/当前行为/后端真实行为/小程序行为/问题/修改方案/测试方案 九项，
没有一条写成"需要优化"。

**本轮被证据推翻的三条旧记录（重要，防止再次误判为已修）**：
1. ~~"`MapCanvas` 零调用点、真地图已接线"~~ → 真地图组件是 `BusMap.kt:99`，**它**当时才零调用点；
   全站用的是自绘 `RouteMap`（`MapCanvas.kt:55`），在 TraceView / BusScreen / DriverNavScreen 有调用。→ §44、P1-1（接线后 BusMap 才有那一个调用点）。
2. ~~"OrderDetailScreen:84 无条件 `cancelled = true`，取消订单是假成功"~~ → 该页走的是正确的
   `PUT /product-order/cancel?id=` 且**回读服务器**；真问题是 `:101` 把真实成功说成"已在演示模式下取消"（§31 文案反向失真）。
3. ~~"DriverNavScreen 假导航已改为 `AMapNaviListener → NavigationState`"~~ → 代码里 `stepping/delay(4_000L)/stepIndex+1`
   **6 处**原封不动（:103/:146/:180/:182/:183/:342，实测），工程无导航 SDK 依赖（jar 里 `com/amap/api/navi/` 零类，见 BLOCKED #2）、无 `core/navigation` 目录。→ §24 `UI_ONLY` + §57 `BLOCKED`。

**根因（§49）**：三方字段命名系统性漂移（`seq` vs `legSequence`、`plate` vs `plateNo`、`nearestStation` vs
`recommendedStation`、`distance`(米) vs `distanceKm`(公里)、`count` vs 裸 `Long`）+ **没有契约守卫**。
处置方向：`app/src/test/resources/contract/` 放后端真实 VO 样例，测试断言"每个 parse 读到的键必须出现在样例里"。

**一处高危共性（§22/§23/§52）**：后端 `CommonResult<Long>` / `CommonResult<String>` 被 `Api.toPayload` 包成
`{"v": …}`，而 `Repo` 读的是 `count|unreadCount` / `url|fileUrl` → 未读角标恒 0、上传后的图片地址恒空，
连带**交接照片实际未入库**却提示成功。

### 上下文锁（P0-1）
- `CONTEXT_LOCK.md`：20 条 PROJECT HARD RULES 原文 + 分层职责 + provider 白名单 + 字段兼容规则 + 第三方规则 + 环境诚实性 + 自查 grep。
- `DECISION_LOG.md`：记录既有偏差与本轮新决策。

### 三方审计证据（部分到位）
**小程序（已完整审计，agent 报告在案）**
- 基准仓库 = `cargo-post-miniprogram`（有 git，分支 `fix/audit-driverside-20260921`，21 页）；`miniprogram` 是 7/23 旧草稿（4 页），**不作为契约来源**。
- 契约基线：yudao `{code,msg,data}`、Bearer、401 统一处理、`cleanParams` 剔空值、silent 静默轮询、`uploadFile → /app-api/infra/file/upload`。
- 小程序的"导航"只有 `wx.openLocation`（外部地图），全库无 `wx.startNavigation`/`amapuri`/`web-view`；`utils/nav.js` 名为导航实为纯函数合成 `navPoints`。**结论：真实逐向导航在小程序里也不存在，Android 不能以"对齐小程序"为理由做假导航。**
- 小程序 `driver/routes.js:122-127` 的地图折线就是**站点直线连线**（#2E7D32），点数不足 2 则 `hasMap=false` 不渲染。`workbench` 用 `/driver/route` 的真 polyline，缺失时 :446/:498 退化为站点连线。**这是被禁止的兜底，Android 侧不得复刻。**
- 位置上报字段实测：`{driverId, shiftId, longitude, latitude, speedKmh}`，前台 `speedKmh` 恒 0，后台 m/s×3.6；`SIMULATED` 时不上报真实 GPS。与 Android `DriverStateTest` 断言的 key 集合一致 → **REAL_COMPLETE**。
- 司机身份：登录不判角色，靠 `getDriverProfile()` 后端解析 + `globalData.driverId`；用户/司机切换是 `reLaunch`/`switchTab`。与 Android 的 `USER_SHELL`/`DRIVER_SHELL` 双 Shell 语义一致。
- 配色基线：primary `#1F5E9E` / clay `#B4511F`（司机、行动）/ gold `#D9A441`（农产品、公告）/ danger `#C0392B`；可切 green 主题。**Android tokens 需逐条核对。**

**Android 侧已读实况**
- `ui/common/BusMap.kt`（唯一真地图组件，生命周期处理正确）**零调用点** → 今天没有任何页面显示真实地图。
- `RoutesViewModel.action()` 在 POST 之后本地 `copy(status = nextStatus(...))` 推进，且 `nextStatus` 只产出 2/3/4，而 `TransportLeg.done` 是 `status == 11`、`active in 7..10` → **自相矛盾的 CONTRACT_MISMATCH**（小程序 handover.js 用的是 1..10 的九段动作序列）。
- `DriverNavScreen.kt` 具备用户点名的全部假象：`stepIndex`/`stepping`/`delay(4000)`/`next()` 循环推进、`remainingMeters` 累加后端 steps、`crossTrackMeters()` 垂距判偏航、本地 TTS、`LaneGuideStrip` 吃后端 `lanes` 字符串、`laneIcon()` 靠"左转/右转/掉头"字面猜车道。**已确认 P1-4 目标。**

### 生产化阶段（上一阶段，构建产物已验证）
- `:app:assembleDebug` / `:app:testDebugUnitTest`（6 个测试类，`ApiTest`/`RepoTest`/`LoginViewModelTest`/`SendOrderTest`/`ProductOrderTest`/`DriverStateTest`）/ `:app:lintDebug` 全绿（lint 0 error）。
- release 关闭 cleartext HTTP（`assembleRelease` 在 http 下配置期即失败）；`BuildConfig.DEMO_ALLOWED` release 硬 false。
- 写操作诚实性修复：`Ack.confirmed` + `GoodsDetailScreen`/`SendScreen` 在列表回读失败时显示"暂未在列表确认"，不再谎称已确认。
- 安全清理：`*.apk/*.aab/*.log` 进 `.gitignore`；源码注释与文档里的真实服务器 IP 已脱敏。

---

### 合并 `origin/main` 收口（10-13 20:1x–21:4x，判据见 D-100）

- **代码状态**：第四轮全量 `assembleDebug + testDebugUnitTest + lintDebug --rerun-tasks` = `0` 条编译错误、
  `67` 类 / `694` 例 / `failures=0` / `errors=0`；lint `1 error` = `local.properties:13 [PropertyEscape]`（本机文件，
  `git check-ignore` 命中，不进版本库）。APK 产物在 `app/build/outputs/apk/debug/app-debug.apk`。
- **判据簿**：`tools/check_*.sh` 全跑 = **33 绿 / 1 红**（合并刚进来时 24 绿 10 红，那 10 红逐条处理完）。
  唯一红的仍是 `check_doc_anchor_probes.sh`（DA-d 新增可疑 72、DA-e ID-MISS 388 > 闸门 286 —— 就是下面那条 #159 欠账）。
  改过锚点/计数的 6 本逐条写在 D-100，
  其中上调计数的两处都有反证：`eta` T-i 7→10 是"新页复用同一口径"，`safe_call`/`throwable` 14→15 那一处是我自己加的。
- **道具撤除**：首页"扫一扫"格（与"乘车码"同路由 + 全工程 0 个扫码实现）、乘车码页的自造二维码与手写线路表、
  天气通知里写死的 `Routes.USER_SHELL`。地址示例文案收成 `ADDRESS_EXAMPLE_HINT` 一处定义三处引用。
- **欠账（新增，任务 #23）**：对方的充值/支付页不进这版。三条实测理由钉在后端源码上 ——
  `PayService.java:68-97` 创单无幂等键、查单口 `/wallet/query-pay-order` 不校验归属人（且订单为待支付时放款就在这一步）、
  对方页面在请求失败时于本地把余额加上去。要接回来先补前两条。
- **欠账（既有 #159 同族，本轮涨）**：台账锚点 ID-MISS 323→396→**388**（闸门 286 仍红）。
  本轮搬回 8 条机械可验的，**3 条因新目标行是源码注释而拒搬**（`Api.kt:75`、`Repo.kt:843`），
  剩余 72 条新增可疑 aid（NEAR 43 / FAR 26 / 拒搬 3）未清。
- **没在真机验过**：合并进来的乘车码页、地址搜索页、地图选点页、自更新弹窗均无真机在环证据；
  壳的 insets 基数换成 `systemBars` 之后是否多一段顶距，也标 NOT VERIFIED ON DEVICE。


## BLOCKED

1. **`ENVIRONMENT_BLOCKED` — Windows/GBK 与 CJK 路径**
   JDK 21 启动器按系统 ANSI 代码页解码 Gradle 写出的 UTF-8 `@argfile`，`C:\Users\袁\Desktop\测试` 被读歪，测试 worker `ClassNotFoundException: GradleWorkerMain`。
   处置：按规则"不要修改代码来绕过"，`gradle.properties` 保持 UTF-8，本机跑测试临时覆盖
   `-Dorg.gradle.jvmargs="... -Dfile.encoding=GBK"`。根治 = 把工程放到 ASCII 路径或换 Linux/CI。

2. **`BLOCKED` — 高德导航 SDK 不在依赖里（已取证，不是"没查"）**
   对已解析的 `com.amap.api:3dmap:10.0.600` jar 实测：`com/amap/api/navi/` **一个类都没有**，
   `javap com.amap.api.navi.AMapNavi` → class not found；`BitmapDescriptorFactory` 只有 `defaultMarker()/defaultMarker(float)/fromBitmap/fromView`。
   同一 jar **已捆绑** `com/amap/api/location/*`（P1-3 的定位源可用；再单独加 `com.amap.api:location` 会 duplicate class）。
   处置：`core/navigation` 必须先加 `com.amap.api:navi-3dmap` 并**逐个 javap 核对签名**才能写调用；
   拉不到依赖就保持 `BLOCKED`，页面显示"导航能力未就绪"，**不许用墙钟假步进顶替**（§24 的 6 处命中即为反例，目标 0）。

3. ~~**`BLOCKED` — GraphHopper 服务未运行**~~ → **已解除（2026-09-29）**
   服务实测可启动、`OSM_GRAPHHOPPER` 已成为可用第二档，证据见 DONE。
   残留事实（不是 blocker，是配置约束）：生产环境必须显式设 `GRAPHHOPPER_URL`（算法服务在该变量为空时跳过 B 档）；
   profile 只能用 `car`/`bus`，配 `foot`/`walk` 会稳定 400；bbox 外的坐标取不到几何，属于预期的"该段无线"。

4. **`BLOCKED` — 后端无数据 + 演示账号失效**
   线上后端 `product/page total=0`、`send/stations []`、`bus/lines []`；`buspost.demoMobile/demoPassword` 登录返回 `1004003000 登录失败，账号密码不正确`。端到端真机验证缺前置条件。

5. **`BLOCKED` — 无真机**
   GPS、前台服务、导航 SDK、离线缓存只能真机验证，不得用 JVM 单测冒充。
   ⑦ 之后**新增三条只有真机能答的问题**（明细见审计 §57）：OEM 网络定位是否本身回 GCJ-02（若是，`Locator` 的无条件换算会转两次）、
   45 秒停报的实际表现、前台通知里"已丢弃 N 个未确认点"是否真的出现。
   **V-2b 再添两条**（本轮实测：`adb devices` → `List of devices attached` **空**；
   `bash tools/verify_device.sh --no-build` → `[STOP] 没有处于 device 状态的设备。` **exit 1**）：
   ① 真实后端上撤档账号冷启动是否**只发一条** `/driver/profile`、角色是否真降回 USER、是否真被弹回用户壳
   （`LaunchedEffect(vm.demoted)` 在设备上的时序，JVM 侧只能证到 `requestCount = 1`）；
   ② `buspost://app/driver_*` 深链的 Intent 落地（V-2 起就挂着的同一条尾巴）。

6. **未解决 — release 产物从未产出（本轮实测坐实）**
   两条独立的门槛叠在一起：① `../buspost-signing-backup` 缺 keystore（V-3 那轮日志里就是
   `签名文件不存在…release 将产出未签名 APK`）；② **配置期 HTTPS 闸门先挡住**：本轮第一次实跑
   `:app:assembleRelease` → **exit 1**，`app/build.gradle.kts:171` 报
   `release 包必须使用 HTTPS（当前 http://1.15.29.107/api）`。
   ⚠ **没有加 `-Pbuspost.allowInsecureRelease=true` 绕过**（那是"release 关闭 cleartext HTTP"这条红线要拦的东西，
   不是要绕的障碍）；**解开顺序**：先把 `buspost.baseUrl` 换成真实 https 域名，再补 keystore，两者都齐了才有 release 产物。
   另有 63 MB `app-debug.apk` 压在仓库根目录待移出。

7. **`ENVIRONMENT_BLOCKED` — 测试框架缺口（⑥ 暴露、⑦ 再次撞上）**
   工程只有 JVM 单测，无 Robolectric / Compose UI 测试。两轮下来"代码已改 + grep 判据为绿但无法自动化验证"的项
   已累积：§29/§64 的四条 Compose 行为、§17/§45 的 `Location`/`SystemClock`/前台服务/通知。
   补法与代价记在审计 §60 第 7 项（是独立的依赖投入，不是某轮顺手做掉的事）。

---

## NEXT（用户指定顺序，不得打乱）

1. ~~① 清掉直线 fallback~~ → **DONE（P0-2）**
2. ~~② `core/map/RealRoadRouteValidator` + `RouteSource` 枚举 + 单测~~ → **DONE（P0-3，16 个用例）**
3. ~~③ 重庆 OSM / GraphHopper 正式接入~~ → **DONE（P0-5，实测见 DONE 段；文档 P0-4 = `docs/REAL_ROAD_ROUTING_ARCHITECTURE.md`）**
4. ~~④ `ui/driver/DriverMissionMap.kt`（真地图 + 任务点 Marker，复用 `BusMap` 的生命周期写法）~~ → **DONE（P1-1，24 tests + lint 0 error，顺带修掉审计 §62）**
5. ~~⑤ 任务点业务化（序号/动作/件数/状态配色；禁止 GPS 自动完成业务）~~ → **DONE（P1-1b）**：
   序号/动作/件数/状态色进工作台时间线（只读后端 `stops`），`FAILED` 成第四态；GPS 门槛删除（§32 判据 grep 0 命中）；顺带修掉新发现的 §63
6. ~~⑥ 用户/司机分域复核（复核 §29/§30/§31/§45/§47，不加新功能）~~ → **DONE（P1-2，2026-09-29）**：
   §29 三处全修 + 新抓出的 §64 三处串域全修；判据 `tools/check_domain_probes.sh`（8 个数字 + 退出码）；
   `DomainSplitTest` 7 例、全量 105 tests 0 failures。**§30/§31/§47 复核结论：本轮未动，仍是 P0 台账项**
   （演示可见性、"真实成功说成演示模式"、`DemoNote` 被裸 `return` 关掉）—— 复核确认它们属"演示诚实性"而非"分域"，
   与硬规则 1 不同根，所以没有顺手改，按 §58 顺序排在全量真实性收口里。
7. ~~⑦ `DriverLocationService` 稳定化（单一 GPS 源 → 队列 → 采样）~~ → **DONE（P1-3，2026-09-29）**：
   单一 GPS 源保持 `LocationManager` 不变，但**换算收到唯一出口**（§17 实测偏移 480.6 m）；
   **"队列"这一半按后端事实改成了"合并 + 45 秒新鲜度闸 + 丢点可见"**（VO 无客户端时间字段、服务端 `LocalDateTime.now()` 盖章
   → 重放＝假上报，代价与理由记 D-019 / §45"本轮状态"）；`shiftId` 不再硬拦（无班次也上报）；顺带新抓出并修掉 §65 两处
    （折线无坐标系边界 + BD-09 漏偏移项）。判据 `bash tools/check_location_probes.sh`（8 个数字 + 退出码）；
   `LocationReportPolicyTest` 15 + `CoordinateTransformTest` 12 + `LocatorCoordinateTest` 6 + `DriverStateTest` 11，全量 140 tests
8. **⑧ AMap Navigation（依赖 BLOCKED #2 解除）→ CURRENT**：先 `javap` 核对真实签名再写 `core/navigation`；拉不到依赖就维持 `BLOCKED`
9. ⑨ 实时 turn-by-turn
10. ⑩ 真实车道信息
11. ⑪ 偏航重算（只能由导航引擎触发，不得本地假判定替代后端）
12. ⑫⑬ 3D 任务 Marker / 3D 车辆（纯视觉层）
13. ⑭ 离线真实道路缓存（须带原始 provider + 抓取时间）
14. ⑮ MapLibre / Valhalla 研究备用（编译困难就 BLOCKED）
15. ⑯ UI 细节（最后）

并行推进：`ANDROID_PARITY_AUDIT_V2.md` 三方矩阵（P0-6 → **DONE，现在 65 节**；证据与判据都在文件里，
按 §58 顺序止血：~~坐标 §17~~（**⑦ 已修**）→ 标量信封 §22/§23/§52 → 段状态机 §26–§28 → 可达性 §34 → 段字段 §36 →
两条必然 400 §39 → 假导航去假 §24 → 演示可见性 §30/§47 → DemoData 假几何 §46 → 货物状态表 §18 → ETA §38。
⑦ 同时把 §45 改成"按后端事实重定方案"、新增 §65（折线坐标系边界 + BD-09 漏项），§55 新增 I 组判据、§56 新增两行不许回潮、
§57 新增三行 `ENVIRONMENT_BLOCKED`、§60 新增第 7 项（测试框架投入）。**下一条 P0 是 §22 标量信封**：
它和 §17 一样属"不报错、静默显示 0 / 空"，而且已经连带让交接照片实际未入库却提示成功。）

⚠ **上面这三行是 ⑦ 收工时的顺序记录，现在已过时**：§22/§23/§52 由 P2-1 收口、§18 由第 ⑨ 轮收口、
§38 由第 ⑪/⑫/⑬ 轮收口，只有 **§24 假步进仍未修**（2026-09-29 ⑬ 收工复跑仍 6 处）。
**要抄当前活一律看文件头"状态板"与 `CURRENT` 段，不要从这段抄顺序。**
