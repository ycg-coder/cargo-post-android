package com.shanxiang.buspost.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 响应式断点：手机竖屏 COMPACT，折叠屏/横屏 MEDIUM，平板 EXPANDED。
 * 内容统一限宽居中，避免平板上卡片被拉长变形。
 */
enum class BusWidthClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberBusWidthClass(): BusWidthClass {
    val w = LocalConfiguration.current.screenWidthDp
    return remember(w) {
        when {
            w >= 840 -> BusWidthClass.EXPANDED
            w >= 600 -> BusWidthClass.MEDIUM
            else -> BusWidthClass.COMPACT
        }
    }
}

/** 按最小卡片宽度算列数，商品网格、功能宫格共用 */
@Composable
fun busGridColumns(minItemWidthDp: Int = 168, min: Int = 2, max: Int = 4): Int {
    val w = LocalConfiguration.current.screenWidthDp
    return remember(w, minItemWidthDp, min, max) {
        ((w - 24) / minItemWidthDp).coerceIn(min, max)
    }
}

/**
 * 内容限宽（**响应式**）：手机保持 660dp 的可读行宽，折叠屏 / 平板按断点放宽。
 *
 * 为什么要分级（2026-10-09 实机取证）：Redmi Pad Pro 屏宽 776dp 落在 EXPANDED，
 * 而全站 26 个页面此前都被写死的 660dp 卡在中间 —— 两侧各空 58dp，平板看起来只是
 * "放大的手机"，横向空间一点没用上。断点数值与 [BusWidthClass] 同源，不另立一套。
 *
 * 上限 1180dp 是**可读性**上限而不是屏幕上限：再宽正文行就太长，眼睛回扫会累。
 * 更大的屏应当靠多列（网格 / 双栏）消化宽度，不是把一栏拉得更长。
 */
@Composable
fun busContentMaxWidth(
    compact: Dp = 660.dp,
    medium: Dp = 880.dp,
    expanded: Dp = 1180.dp,
): Dp = when (rememberBusWidthClass()) {
    BusWidthClass.COMPACT -> compact
    BusWidthClass.MEDIUM -> medium
    BusWidthClass.EXPANDED -> expanded
}

/** 表单/详情类页面：整页滚动 + 限宽居中 */
@Composable
fun BusPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    spacedBy: Dp = 12.dp,
    scrollable: Boolean = true,
    maxContentWidth: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalBusTokens.current
    val limit = maxContentWidth ?: busContentMaxWidth()
    Box(modifier.fillMaxSize().background(t.paper), contentAlignment = Alignment.TopCenter) {
        val scrollMod = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(
            scrollMod.widthIn(max = limit).padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacedBy),
            content = content,
        )
    }
}

/** 长列表页面：LazyColumn + 限宽居中；页面内含列表时用这个，不要嵌进 BusPage */
@Composable
fun BusLazyPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    spacedBy: Dp = 12.dp,
    maxContentWidth: Dp? = null,
    state: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val t = LocalBusTokens.current
    val limit = maxContentWidth ?: busContentMaxWidth()
    Box(modifier.fillMaxSize().background(t.paper), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = state,
            modifier = Modifier.widthIn(max = limit),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(spacedBy),
            content = content,
        )
    }
}

@Composable
fun BusGridPage(
    modifier: Modifier = Modifier,
    minItemWidthDp: Int = 168,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    spacedBy: Dp = 12.dp,
    maxContentWidth: Dp? = null,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit,
) {
    val t = LocalBusTokens.current
    val cols = busGridColumns(minItemWidthDp)
    val limit = maxContentWidth ?: busContentMaxWidth(expanded = 1280.dp)
    Box(modifier.fillMaxSize().background(t.paper), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = rememberLazyGridState(),
            modifier = Modifier.widthIn(max = limit),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(spacedBy),
            verticalArrangement = Arrangement.spacedBy(spacedBy),
            content = content,
        )
    }
}

/** 横向滚动条（功能入口、筛选标签、图片凭证） */
@Composable
fun BusScrollRow(
    modifier: Modifier = Modifier,
    spacedBy: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp),
    content: LazyListScope.() -> Unit,
) {
    LazyRow(modifier, contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(spacedBy), content = content)
}
