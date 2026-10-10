/**
 * 工具链版本（2026-10-09 现代化升级，每一项都由 AAR 元数据实测反推，不是"抄最新号"）：
 *
 *   AGP 8.13.2 —— 8.x 线最后一个正式版，也是**能留在 compileSdk 36 / Gradle 8 上的终点**。
 *                 官方兼容表：AGP 8.13 最低 Gradle 8.13、最高支持 API 36.1、JDK 17；
 *                 且 8.13.2 的 R8 8.13.19 才正式支持 Kotlin 2.3。
 *                 ⚠ 再往上就是 AGP 9.x，它强制 Gradle 9.1 + compileSdk 37 —— 那是另一次大迁移，
 *                 不作为本次目标（本机 SDK 也只装到 android-36.1）。
 *
 *   Kotlin 2.3.21 —— 与 AGP 8.13.2 的 R8 支持范围对齐；同时满足 Compose runtime 1.11.4
 *                 对 kotlin-stdlib ≥ 2.1.20 的硬要求（旧版 2.0.21 会低于该下限）。
 */
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
