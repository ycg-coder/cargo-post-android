package com.shanxiang.buspost.ui.driver

import com.shanxiang.buspost.data.model.WeatherInfo

/**
 * 司机端天气那一格的两个纯判断（#42）：什么时候重新取、取到什么才配显示。
 *
 * 为什么司机端以前**根本没有**天气：`Repo.weather` 全工程只有一个调用点（用户端首页 `HomeScreen.kt:185`），
 * 而那句提示文案（旧 `farmTips`）六条清一色货运口径 ⇒ 工作台既不取也不显示，
 * 用户报的"天气提醒司机和用户应该文案不一样"实际缺的是第二份说法**和**司机端那一栏。
 *
 * 取数条件钉死成"本机有位置上报的点"（`Running.geo`）：没点就不发请求、那一栏就不出现。
 * 旧写法那种"兜一个重庆中心坐标去查天气"会在同一屏写出「位置未取到」+「15°C 多云」
 * （§14：没有值不许有业务语义），司机拿它当"我这儿的天气"就是造第二条真相。
 */
internal object DriverWeatherTip {

    /** 重新取数的最短间隔。15 分钟：山区天气变得比订单快，但工作台每收一班都要回读，按班次刷新会打满 REST 配额。 */
    const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L

    /**
     * 到点该不该重新取。`atElapsedMs <= 0` = 本机还没取过（第一次进工作台必取）。
     * 两侧都用 `Locator.elapsed()` 那个单调时钟，插拔 SIM/改系统时间都不会把间隔拉成 0。
     */
    fun shouldFetch(atElapsedMs: Long, nowElapsedMs: Long): Boolean =
        atElapsedMs <= 0L || nowElapsedMs - atElapsedMs >= REFRESH_INTERVAL_MS

    /**
     * 这一格显示什么：`null`（还没取）与 `mock`（两路都没返回）都给空串 ⇒ 页面**不出这一栏**，
     * 而不是兜一句"注意行车安全"。天气没取到是事实，把它包装成提示就是在编。
     */
    fun lineOf(w: WeatherInfo?): String = if (w == null || w.mock) "" else w.driverTips
}
