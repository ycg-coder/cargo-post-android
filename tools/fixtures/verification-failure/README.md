# 四类构建失败分型的**出处样例**（指令 v2.1 §三十三 / F-2 第 7 步）

`tools/verification_failure_rules.txt` 里每条 `实测:` 出处都指向本目录的一份文件。
**这里的日志不是素材，是证据**：规则表的每一条字面量都必须能在某份真实产出过的日志里逐字节找到，
否则那条判据就是"照着记忆编的文案"。因此本目录有四条硬规矩：

1. **不收合成日志**。手写一份"看起来像 gradle 报错"的文本，只能证明分类器认识我编的那句话，证明不了真实构建会那么说。
   唯一例外是第 7 份反例（它本来就是你想要"零命中"时的那份真实文本）。
2. **一份样例只落一类**。文件名前缀就是它声明的类别，判据簿 VF-b 逐份核对"前缀声明 == 分类器判决"。
   一份日志同时命中两类，说明字面量选得太宽（控制 4 演示过这种破法）。
3. **表头类文本不进规则表**。`List of devices attached`、`> Task … FAILED` 这种"正常在场时也会出现"的行一律不收，
   收了就会把成功判成失败（第 6 份样例里就有 `List of devices attached`，规则表里没有它；控制 12 是对这个取舍的反证）。
4. **重录要同轮改出处**。新增/删除样例后必须回到规则表改出处列，且出处**行号**要重新核 ——
   VF-a 的"出处行号回核"会把指错的行号报红（本轮就抓到一条：写的 `:15`，实测首次命中在 `:12`）。

## 七份文件（字节数 / sha1 前 12 位 / 本目录 mtime / 原件 / `cmp` 结果）

| 文件 | B | sha1 | 本目录 mtime | 原件 | 原件 mtime |
| --- | --- | --- | --- | --- | --- |
| `source-failure-kotlinc.log` | 2,133 | `8ff41af4cf82` | 22:19:53.780 | `/tmp/vf_source_raw.log` | 22:15:19 |
| `dependency-failure-could-not-find.log` | 1,467 | `8144d9a8fef9` | 22:19:53.847 | `/tmp/vf_dep_raw.log` | 22:18:56 |
| `environment-failure-worker-classnotfound.log` | 148,548 | `acfecf7e2c64` | 22:19:53.914 | `/tmp/vf_env_failure_raw.log` | 22:13:53 |
| `environment-failure-daemon-bad-jvmarg.log` | 1,965 | `6205294ebdd0` | 22:19:53.986 | `/tmp/vf_env2_raw.log` | 22:18:49 |
| `environment-failure-not-a-git-repository.log` | 211 | `c05a03daefe1` | 22:19:54.117 | `/tmp/vf_git_raw.log` | 22:19:39 |
| `device-unavailable-adb.log` | 319 | `2270daa28619` | 22:19:54.053 | `/tmp/vf_device_raw.log` | 22:14:10 |
| `negative-control-tests-failed-not-a-build-failure.log` | 2,428 | `97d641fdf06a` | 22:19:54.184 | `/tmp/ex_neg_X-1.log` | 20:02:44 |

七份 **`cmp` 与原件一字不差**（2026-09-30 本轮实测，逐份跑过）。本目录的 mtime 集中在 22:19:53–54，
那是"从 `/tmp` 拷进来"的时刻，**不是**日志产生的时刻 —— 抄数时两个时间都要连口径一起抄，
否则会出现"同一份证据的 mtime 比它记录的命令还晚"这种看起来像伪造的形态。

## 每份钉住什么（分类器实测输出，非回忆）

| 样例 | 命中的字面量（次数 / 首次行号） | 判决 / rc |
| --- | --- | --- |
| `source-failure-kotlinc.log` | `e: file:` 1 / 30 · `Unresolved reference` 1 / 30 | `SOURCE_FAILURE` / 0 |
| `dependency-failure-could-not-find.log` | `Could not resolve` 1 / 17 · `Could not find` 1 / 18 | `DEPENDENCY_FAILURE` / 0 |
| `environment-failure-worker-classnotfound.log` | `GradleWorkerMain` **72** / 83 | `ENVIRONMENT_FAILURE` / 0 |
| `environment-failure-daemon-bad-jvmarg.log` | `Unable to start the daemon process` 1 / 6 · `Unrecognized VM option` 1 / **12** | `ENVIRONMENT_FAILURE` / 0 |
| `environment-failure-not-a-git-repository.log` | `not a git repository` 2 / 2 | `ENVIRONMENT_FAILURE` / 0 |
| `device-unavailable-adb.log` | `no devices/emulators found` 2 / 9 | `DEVICE_UNAVAILABLE` / 0 |
| `negative-control-tests-failed-not-a-build-failure.log` | 无 | `UNCLASSIFIED` / **4** |

### 1 `source-failure-kotlinc.log` —— SOURCE_FAILURE
`:app:compileDebugUnitTestKotlin FAILED`（:29）→ `e: file:///C:/Users/袁/…/ScratchVfCapture.kt:4:40 Unresolved reference 'NoSuchCla…'`（:30）
→ `* What went wrong: Execution failed for task ':app:compileDebugUnitTestKotlin'.`（:34–35）→ `BUILD FAILED in 5s`。
**怎么录的**：临时往 test 源集放一个引用不存在类型的 `ScratchVfCapture.kt`，用 GBK 配方跑 `:app:testDebugUnitTest`，
落盘 `/tmp/vf_source_raw.log` 后**删掉那个临时文件**（本轮实测 `find app/src tools -name 'Scratch*'` = **0**）。
钉住的是"kotlinc 的编译失败在这台机器上就是 `e: file:` + `Unresolved reference` 两个形状"，不是 javac 的形状。
规则表另有一条 `error: cannot find symbol` 标 `未实测:` —— 那是 javac 的已知文案，本工程从没产出过，所以它**没有**样例撑着，不许被当成"实测过"。

### 2 `dependency-failure-could-not-find.log` —— DEPENDENCY_FAILURE
`:app:checkDebugAarMetadata FAILED`（:11）→ `Could not resolve all files for configuration ':app:debugRuntimeClasspath'.`（:17）
→ `Could not find com.vfcapture.absent:library:9.9.9.`（:18）→ `BUILD FAILED in 5s`。
**怎么录的**：靠 **init 脚本注入**一个不存在的坐标（`-I /tmp/vf_dep.init.gradle`，内容是
`gradle.beforeProject { p -> p.plugins.withId('com.android.application') { p.dependencies.add('implementation', 'com.vfcapture.absent:library:9.9.9') } }`），
**没有改工程内任何文件**、也没有把假坐标写进 `app/build.gradle.kts`。
这条区分很重要：如果为了取证去改依赖清单，那一遍构建的产物就不能再当"当前源码的产物"引用了。

### 3 `environment-failure-worker-classnotfound.log` —— ENVIRONMENT_FAILURE（本机最贵的一条）
`GradleWorkerMain` 命中 **72** 次，首次 :83；`> Task :app:testDebugUnitTest FAILED`（:1466）、
`* What went wrong:`（:1648）。**怎么来的**：跑全量时**少了** `-Dorg.gradle.jvmargs="… -Dfile.encoding=GBK"`，
守护进程按 UTF-8 写给测试 worker 的 @argfile 里带着非 ASCII 用户路径，`java.exe` 按 ANSI 码页解不开 ⇒ 六个 worker 全起不来。
⚠ **这是环境缺陷，没有改任何源码或脚本来绕过它**（硬规则：wrapper/环境失败不许改代码绕）。
所有 gradle 读数都连这个开关一起抄，见 `CONTEXT_LOCK.md` 的配方。
这份样例最有用的一点：**它和反例第 7 份都带 `> Task :app:testDebugUnitTest FAILED`**，
而分类器把前者判成 `ENVIRONMENT_FAILURE`、后者拒绝给类别 —— 这正是"不能用 FAILED 那行分类"这条禁忌的实证。

### 4 `environment-failure-daemon-bad-jvmarg.log` —— ENVIRONMENT_FAILURE
`Unable to start the daemon process.`（:6）+ `Unrecognized VM option 'MaxMetaspaceSize:768m'`（:12）
+ 第 5 行那句 `Process command line: …java.exe -XX:MaxMetaspaceSize:768m …` 就是错本身（冒号该是等号）。
**这条是本轮我自己写坏的配方录出来的**：一次配置期失败，日志里连 `BUILD FAILED` 都没有（daemon 没起来，构建没开始）。
它钉住两类禁忌的其中一面：**构建根本没跑到任务阶段**时，`> Task` 一行都不存在，只有按 daemon 文案才认得出。

### 5 `environment-failure-not-a-git-repository.log` —— ENVIRONMENT_FAILURE
文件里第一行就是命令本身（`$ git rev-parse --is-inside-work-tree`），第二行
`fatal: not a git repository (or any of the parent directories): .git`（:2），第三行 `rc=128`。
这是 §121 checkpoint 做不了的那一条：**不 `git init` 冒充**，只把它记成环境事实并留下输出。

### 6 `device-unavailable-adb.log` —— DEVICE_UNAVAILABLE
`adb 版本: Android Debug Bridge version 1.0.41` → `--- adb devices ---` → `List of devices attached` →
`no devices/emulators found`（:9，命中 **2** 次）。**怎么来的**：`adb devices` 空列表（平板没连/USB 调试关着）。
`verify_device.sh` 现在会把这一份的同类原文写进 `docs/device-evidence/<时间戳>/device-check.log`，
所以这一类以后每轮都有一份新产物，不必再靠这份存量样例。
⚠ 表头 `List of devices attached` **有设备时也在**，所以它进不了规则表（控制 12 演示了收进去的后果）。

### 7 `negative-control-tests-failed-not-a-build-failure.log` —— 反例，rc=4
`:app:testDebugUnitTest FAILED`（:46）+ `DriverExceptionContractTest > 选其他却不写情况时不出网 FAILED`（:38）
+ `java.lang.AssertionError at DriverExceptionReportTest.kt:50`（:42）+ `BUILD FAILED in 9s`（:57），
四条字面量**一条都不命中** ⇒ 分类器输出 `VERDICT|UNCLASSIFIED`、rc=4。
它钉住的是 §三十三 那句话的另一半：**"构建失败"不等于"用例失败"**。用例红是业务断言红，
拿 `BUILD FAILED` 去分型就会把它误标成 `SOURCE_FAILURE`，于是"测试没通过"会被记账成"代码编译不过"。
VF-b 把它**排除在六份正面样例之外**，单独判它的 rc 必须是 4。

## 编码与字节事实（本轮实测，不是提示）

- 七份全是 **CRLF**；非 ASCII 行分布极不均：worker 那份 1487 行含非 ASCII、含 **792 个 U+FFFD 替换符**，
  而 git 那份 0 行 —— **同一个文件内部逐行混着编码**（GBK 字节与 UTF-8 字节都出现过，例如 worker 那份第 6 行是
  中文路径、第 37 行是 `管道正在被关闭` 的 GBK 尾巴）。
- 因此：**分类器一律 `grep -a`**（否则 grep 把二进制文件当"没匹配"跳过），**规则表字面量只准全 ASCII**，
  Kotlin 侧按 **ISO_8859_1** 读字节。换成 UTF-8 解码会在非法序列上丢字符，造出"两侧数出来不一样"这种复现不了的分歧。
  ⚠ ASCII-only 是**可证等价的前提**（子串包含 ≡ `grep -F`），前提由用例 `字面量全 ASCII 且不含正则元字符，所以 bash 与 JVM 的匹配语义等价` 逐行验，由 VF-a 在 bash 侧再钉一遍。
- 但别把这条推广成"中文 grep 一律不可信"：本轮实测 `grep -c 袁` 在 `source-failure-kotlinc.log` 命中 **2** 行、
  在 worker 那份命中 **1** 行。**真正不可靠的是把这个路径交给 java/gradle**（见第 3 份），不是 grep 本身。
  写判据时按"字面量全 ASCII"办是**为了两侧语义等价**，不是为了绕开一个 grep 的坏脾气。
- 本目录在 `tools/` 下，不在 `assets/`，**不进 APK**；`VerificationFailure` 与四条 marker 的包内 0 命中见本轮 dex 取证。

## 复跑与新增样例的规矩

```bash
bash tools/check_verification_failure_probes.sh          # VF-a…VF-g，只读结构 + 这七份样例
bash tools/verification_failure_negative_controls.sh     # 13 条破口，逐条点名判据
bash tools/classify_verification_failure.sh <日志文件>    # 单独跑一份，看 RULE/VERDICT 行
```
新增一行规则：先在**本机真产出**那条失败，再往表里加行，出处写 `实测:<文件名>:<首次命中行号>`；
产不出来的写 `未实测:<出处说明>`，并且不许有第二条也标 `未实测`（用例 F-d 每类只允许有限度这样写）。
删一行规则：同轮删掉它指向的样例，或把样例留着但改出处 —— 让出处指向不存在的文件会被 VF-a 报红。
