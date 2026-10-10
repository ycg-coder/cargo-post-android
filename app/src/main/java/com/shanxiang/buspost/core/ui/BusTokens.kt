package com.shanxiang.buspost.core.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 产品名「乡邮智联」；「山乡巴士 · 站牌与车票」是**设计体系名**（与小程序 README 同口径，
 * 只出现在代码注释里，不作为产品名展示）。设计令牌逐值移植自小程序 app.wxss 与 utils/appearance.js。
 * 配色语义固定：陶土橙 = 司机/行动，稻谷金 = 农产品/公告，墨字正文，米纸底页面。
 */
data class BusTokens(
    val primary: Color,
    val primaryDark: Color,
    val accent: Color,
    val primaryLight: Color,
    val onPrimary: Color,
    val clay: Color,
    val gold: Color,
    val paper: Color,
    val ink: Color,
    val price: Color,
    val danger: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val statusPending: Color,
    val statusShipping: Color,
    val statusDone: Color,
    val statusWarn: Color,
    val tabActive: Color,
    val primaryOnLight: Color,
    val card: Color,
    val cardWarm: Color,
    val field: Color,
    val line: Color,
    val lineDeep: Color,
    val track: Color,
    val goldLight: Color,
    val goldDeep: Color,
    val clayLight: Color,
    val clayDeep: Color,
    val skeletonA: Color,
    val skeletonB: Color,
    val primaryShadow: Color,
    val isDark: Boolean,
)

/** 主题预设，键与小程序 THEMES 一致，便于两端同图 */
enum class BusTheme(val id: String, val label: String) {
    GREEN("green", "山野绿"),
    ORANGE("orange", "陶土橙"),
    BLUE("blue", "山泉蓝"),
    RED("red", "中国红");

    companion object {
        fun fromId(id: String?): BusTheme = entries.firstOrNull { it.id == id } ?: BLUE
    }
}

private fun hex(v: String): Color = Color(
    android.graphics.Color.parseColor(if (v.length == 7) "#FF${v.substring(1)}" else v)
)

/** 深色模式下的文字与浅底，同小程序 DARK_TEXT / DARK_PRIMARY_* */
private val darkSurfaces = mapOf(
    "paper" to hex("#171512"), "ink" to hex("#EDE7D9"), "card" to hex("#24211B"),
    "cardWarm" to hex("#2C2820"), "field" to hex("#26231D"), "line" to hex("#3B362B"),
    "lineDeep" to hex("#4A4436"), "track" to hex("#322E26"),
    "goldLight" to hex("#38301C"), "goldDeep" to hex("#DDBB6E"),
    "clayLight" to hex("#3A241A"), "clayDeep" to hex("#E8906B"),
)
private val darkTextSecondary = hex("#B3AC9B")
private val darkTextTertiary = hex("#9C977F")
private val darkPrimaryText = mapOf(
    BusTheme.GREEN to hex("#609C63"),
    BusTheme.ORANGE to hex("#C57750"),
    BusTheme.BLUE to hex("#608DBA"),
    BusTheme.RED to hex("#C57178"),
)
private val darkPrimaryLight = mapOf(
    BusTheme.GREEN to hex("#1C2B1E"), BusTheme.ORANGE to hex("#2E211A"),
    BusTheme.BLUE to hex("#16222E"), BusTheme.RED to hex("#2B1A1B"),
)

private data class PaletteSeed(
    val primary: Color, val dark: Color, val accent: Color, val light: Color,
    val shadow: Color, val paper: Color, val card: Color, val cardWarm: Color,
    val field: Color, val line: Color, val lineDeep: Color, val track: Color,
)

private val seeds = mapOf(
    BusTheme.GREEN to PaletteSeed(
        hex("#2E7D32"), hex("#1C4B2E"), hex("#4CAF50"), hex("#EAF3EA"), Color(0x332E7D32),
        hex("#F6F2E9"), hex("#FFFDF8"), hex("#FCF8EC"), hex("#FBF8F1"), hex("#E4DECD"),
        hex("#D8D2C4"), hex("#EDE7D9")
    ),
    BusTheme.ORANGE to PaletteSeed(
        hex("#B4511F"), hex("#823A16"), hex("#BE693E"), hex("#F8ECE3"), Color(0x33B4511F),
        hex("#F6F2E9"), hex("#FFFDF8"), hex("#FCF8EC"), hex("#FBF8F1"), hex("#E4DECD"),
        hex("#D8D2C4"), hex("#EDE7D9")
    ),
    // 对齐小程序站牌头渐变：#1e5a96 → #2d7bc4（深→浅）
    BusTheme.BLUE to PaletteSeed(
        hex("#2d7bc4"), hex("#1e5a96"), hex("#2E7BBF"), hex("#E6EFF5"), Color(0x331F5E9E),
        hex("#F5F8FC"), Color.White, hex("#F0F5FA"), hex("#F7FAFD"), hex("#DCE6EE"),
        hex("#C7D5E0"), hex("#E4EBF2")
    ),
    BusTheme.RED to PaletteSeed(
        hex("#A6242F"), hex("#7A1821"), hex("#C24A3D"), hex("#F7E6E4"), Color(0x33A6242F),
        hex("#F6F2E9"), hex("#FFFDF8"), hex("#FCF8EC"), hex("#FBF8F1"), hex("#E4DECD"),
        hex("#D8D2C4"), hex("#EDE7D9")
    ),
)

private val clay = hex("#B4511F")
private val gold = hex("#D9A441")
private val inkLight = hex("#2B2B28")

fun busTokens(theme: BusTheme, dark: Boolean): BusTokens {
    val s = seeds.getValue(theme)
    return if (!dark) {
        BusTokens(
            primary = s.primary, primaryDark = s.dark, accent = s.accent, primaryLight = s.light,
            onPrimary = Color.White, clay = clay, gold = gold, paper = s.paper, ink = inkLight,
            price = clay, danger = hex("#C0392B"),
            textSecondary = hex("#5A564B"), textTertiary = hex("#6E6B5E"),
            statusPending = clay, statusShipping = s.primary, statusDone = hex("#6E6B5E"),
            statusWarn = hex("#8E5E05"), tabActive = s.primary, primaryOnLight = s.dark,
            card = s.card, cardWarm = s.cardWarm, field = s.field, line = s.line, lineDeep = s.lineDeep,
            track = s.track, goldLight = hex("#FBF3DF"), goldDeep = hex("#8A6A1F"),
            clayLight = hex("#F8ECE3"), clayDeep = hex("#A64A1F"),
            skeletonA = hex("#EAE4D4"), skeletonB = hex("#F7F2E6"),
            primaryShadow = s.shadow, isDark = false,
        )
    } else {
        val d = darkSurfaces
        val pt = darkPrimaryText.getValue(theme)
        BusTokens(
            primary = s.primary, primaryDark = s.dark, accent = s.accent,
            primaryLight = darkPrimaryLight.getValue(theme), onPrimary = Color.White,
            clay = clay, gold = gold, paper = d.getValue("paper")!!, ink = d.getValue("ink")!!,
            price = d.getValue("clayDeep")!!, danger = hex("#C0392B"),
            textSecondary = darkTextSecondary, textTertiary = darkTextTertiary,
            statusPending = d.getValue("clayDeep")!!, statusShipping = s.accent, statusDone = darkTextTertiary,
            statusWarn = gold, tabActive = pt, primaryOnLight = pt,
            card = d.getValue("card")!!, cardWarm = d.getValue("cardWarm")!!, field = d.getValue("field")!!,
            line = d.getValue("line")!!, lineDeep = d.getValue("lineDeep")!!, track = d.getValue("track")!!,
            goldLight = d.getValue("goldLight")!!, goldDeep = d.getValue("goldDeep")!!,
            clayLight = d.getValue("clayLight")!!, clayDeep = d.getValue("clayDeep")!!,
            skeletonA = hex("#332F27"), skeletonB = hex("#3D382E"),
            primaryShadow = s.shadow, isDark = true,
        )
    }
}

val LocalBusTokens = compositionLocalOf { busTokens(BusTheme.BLUE, false) }

/** 老年模式字号倍率，与小程序 .elderly-mode 放大规则等价 */
val LocalBusFontScale = compositionLocalOf { 1f }

/** 当前触控下限（48dp / 驾驶模式 56dp）。组件按它定命中区，**不要各自写数字** */
val LocalBusTouch = compositionLocalOf { BusTouch.Default }

/**
 * 是否处于驾驶模式。页面用它决定"要不要把次要信息收起来 / 用夜间底 / 开导航夜间配色"。
 * 注意：这里只报事实，**不替页面做视觉决策** —— 具体怎么变由消费方写在自己的代码里。
 */
val LocalBusDriving = compositionLocalOf { false }
