# cargo-post-android 的 R8/ProGuard 规则（缺陷 #78 第三格）
#
# 这个文件原本只有一行别的工程留下的占位注释（一句"无需额外规则"），对本工程的反射面一个字都没说。
# 今天 release 的 `isMinifyEnabled` 仍是 false，所以这些规则
# 暂时不参与构建；等它被打开的那一天，下面每一条都是"少一条就出事"的那种，
# 并且每条都点名的确切的反射入口（不是从网上抄的一大盘通用模板）。
# 配 tools/check_startup_probes.sh 的 ST-d 格：规则条数、外来项目名、以及"开裁剪必须先过真机走查"那道闸。

# ── 可诊断性 ──────────────────────────────────────────────────────────────
# 崩溃堆栈要能读。R8 默认抹掉行号，而 `Log.w("BUS_MAP", …)`、AlertEngine、AUTH_LOGOUT 那几条
# 诊断是这个工程唯一的现场证据通道（约定：诊断只进 logcat，不进界面）。
# 留 SourceFile/LineNumberTable 之后，栈里是 `Foo.kt:123`；行号与原名靠 mapping.txt retrace
# （AGP 落在 app/build/outputs/mapping/release/，发版必须把它一起归档，否则线上栈是废的）。
-keepattributes SourceFile,LineNumberTable

# 泛型签名 / 注解 / 内部类：高德回调接口与 coroutines 的泛型方法在 R8 全量优化下会丢签名信息，
# 表现是运行时 ClassCastException 或 "AbstractMethodError"，而且只在release 包里出现。
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# ── ① 高德 3D 地图 / 导航：native 侧按**名字**找类 ──────────────────────────────────
# 依据（不是猜的）：把 `navi-3dmap-10.0.600` 里的 `lib/arm64-v8a/*.so` 逐字节扫过一遍，
# JNI 类描述符命中数为 `com/autonavi/ae` 120 处、`com/amap/api` 75 处、`com/autonavi/base` 38 处、
# `com/autonavi/amap` 8 处、`com/alibaba/idst` 6 处 —— 改名后 native 找不到类，
# 症状不是崩在启动，而是地图/导航在某一次操作里黑屏或静默失效（最难查的那种）。
# 另一个入口在本工程自己：`AmapNavigationEngine` 用字符串 `Class.forName("com.amap.api.navi.AMapNavi")`
# 问"导航组件在不在包里"，R8 把没被静态引用的类裁掉时这里不会报错，只会把"有"读成"没有"。
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.alibaba.idst.** { *; }

# ── ② WorkManager 的 worker：类名写进数据库、之后反射实例化 ────────────────────────
# `WorkSpec.workerClassName` 存的是字符串，不在 manifest 里 ⇒ AGP 由 manifest 生成的组件保留规则
# **覆盖不到它**。改名之后 `enqueueUniquePeriodicWork` 当场照样成功，
# 但设备重启 / 进程被杀后从库里恢复那一条会 ClassNotFoundException ——
# 于是"到车提醒"从此静默不再来，而界面上一点痕迹都没有（这正是 §52 那条"错过一轮不等于丢提醒"救不了的形）。
-keep class com.shanxiang.buspost.notify.NotifyWorker { <init>(...); }

# ── ③ 枚举的**名字**参与业务判定，不许改 ──────────────────────────────────────
# `Prefs.role` 从盘上读回字符串再 `Role.valueOf(...)`；`NavIcon` 读的是后端给的图标字面量。
# values()/valueOf 本身由 `proguard-android-optimize.txt` 兜着（所以这里**不重复**那条），
# 但枚举**常量名**被缩写成 a/b/c 之后，盘上的 "DRIVER" 与后端的 "LEFT" 就再也对不回来了。
# ⚠ 两个枚举**分开写**：`com…model.{Role,NavIcon}` 这种花括号并列是 R8 解析不了的语法
#   （实测 `minifyReleaseWithR8` 直接报 "Expected field or method name"，位置就指在这一行）。
-keepclassmembers enum com.shanxiang.buspost.data.model.Role { *; }
-keepclassmembers enum com.shanxiang.buspost.data.model.NavIcon { *; }

# ── ④ 三方库的 missing_rules 只按实际报错点名消解，不预先抄模板 ──────────────────
# 下面四条是 okhttp 4.12 在不同 Android 版本上可选的安全 provider（conscrypt / bouncycastle /
# openjsse / org.bouncycastle 的 javax 兼容层），它们**不在**本包里，R8 只在依赖图里看到引用。
# 不压住就是 build 红："Missing class org.conscrypt.Conscrypt"。
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn okhttp3.internal.platform.BouncyCastlePlatform
-dontwarn okhttp3.internal.platform.OpenJSSEPlatform
-dontwarn org.conscrypt.**
