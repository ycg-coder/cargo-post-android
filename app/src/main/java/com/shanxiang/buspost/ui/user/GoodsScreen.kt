package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusLazyPage
import com.shanxiang.buspost.core.ui.BusPullRefresh
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.EmptyState
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.FilterChipRow
import com.shanxiang.buspost.core.ui.ListEndNote
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.PriceText
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.Signpost
import com.shanxiang.buspost.core.ui.SkeletonList
import com.shanxiang.buspost.core.ui.busGridColumns
import com.shanxiang.buspost.data.model.Product
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.ScreenViewModel

/** 分类名 → 商品名关键词（后端暂无分类字段，按名称归类），与小程序 goods.js 完全一致 */
private val CATEGORY_KEYWORDS = listOf(
    listOf("果", "柚", "橙", "李", "桃"),
    listOf("菜", "萝卜", "红薯", "土豆", "菌", "笋"),
    listOf("蛋", "鸡", "鸭"),
    listOf("茶"),
    listOf("粉", "面", "干货", "核桃", "坚果", "花生", "栗", "蜂蜜", "腊"),
)

/** 商品归属分类 id（1..5），无匹配归 0（仅"全部"可见） */
private fun matchCategory(p: Product): Int {
    val text = p.name
    CATEGORY_KEYWORDS.forEachIndexed { id, kws ->
        if (kws.any { text.contains(it) }) return id + 1
    }
    return 0
}

class GoodsViewModel : ScreenViewModel() {
    var allProducts by mutableStateOf<List<Product>>(emptyList())
    var categoryIndex by mutableStateOf(0)
    var query by mutableStateOf("")
    var sortIndex by mutableStateOf(0)
    var pageNo by mutableStateOf(1)
    var total by mutableStateOf(0)
    var refreshing by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var loadError by mutableStateOf(false)

    val categories: List<String> = listOf("全部", "水果", "蔬菜", "禽蛋", "茶叶", "干货")
    val sortOptions: List<String> = listOf("综合", "价格↑", "价格↓", "销量")
    val hasMore: Boolean get() = allProducts.size < total

    val shown: List<Product> get() {
        val filtered = allProducts.filter { p ->
            (categoryIndex == 0 || matchCategory(p) == categoryIndex) &&
                (query.isBlank() || p.name.contains(query) || p.subtitle.contains(query) || p.village.contains(query))
        }
        return when (sortIndex) {
            1 -> filtered.sortedBy { it.price }
            2 -> filtered.sortedByDescending { it.price }
            3 -> filtered.sortedByDescending { it.sales ?: 0 }
            else -> filtered
        }
    }

    fun reload() {
        pageNo = 1
        allProducts = emptyList()
        total = 0
        loadError = false
        load(reset = true)
    }

    fun loadMore() {
        if (loading || loadingMore || !hasMore) return
        loadingMore = true
        pageNo += 1
        load(reset = false)
    }

    fun load(reset: Boolean = true) = work(reset = reset) {
        try {
            val f = Repo.productsPage(pageNo = pageNo, pageSize = PAGE_SIZE)
            allProducts = if (pageNo == 1) f.items else allProducts + f.items
            // 后端给了 total 就以它为准（末页判定不能靠猜）；演示集没有 total 时按本页是否满页推断
            total = if (f.total > 0) f.total else if (f.items.size >= PAGE_SIZE) allProducts.size + 1 else allProducts.size
            demo = f.demo
            loadError = false
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = true
            error = e.message
        } finally {
            loadingMore = false
            refreshing = false
        }
    }

    private companion object {
        const val PAGE_SIZE = 10
    }
}

@Composable
fun GoodsScreen(vm: GoodsViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    val search by AppState.search.collectAsStateWithLifecycle()

    // 列数按屏宽算（2026-10-09 大屏改造）：原来写死 chunked(2)，在 776dp 的平板上等于把 2 张卡拉到
    // 370dp 宽、图片高度还是 112dp —— 卡片被拉成又扁又长的横条，一屏只看得到 4 件。
    // 列数交给 busGridColumns（与全站同一份断点）：手机仍是 2 列（零变化），平板自动 4 列。
    //
    // ⚠ 必须在这里算、不能搬进下面的 LazyListScope：`busGridColumns` 是 @Composable
    // （它读 LocalConfiguration 并走 remember），而 `BusLazyPage { }` 的 content 是普通 lambda，
    // 里面没有组合上下文 —— 放进去会直接编译失败（"@Composable invocations can only happen
    // from the context of a @Composable function"）。
    val goodsCols = busGridColumns(minItemWidthDp = 172, min = 2, max = 4)

    LaunchedEffect(Unit) { if (vm.allProducts.isEmpty()) vm.load() }
    LaunchedEffect(search) { vm.query = search }

    Column {
        Signpost(
            title = "山货集市",
            subtitle = "村里直发 · 今日班车直达县城共配仓",
            badge = "商城",
        )

        BusPullRefresh(
            refreshing = vm.refreshing,
            onRefresh = { vm.reload() },
            modifier = Modifier.fillMaxSize(),
        ) {
            BusLazyPage(spacedBy = 12.dp, contentPadding = PaddingValues(16.dp)) {
                item {
                    BusCard(padding = PaddingValues(6.dp), modifier = Modifier.fillMaxWidth()) {
                        BusTextField(
                            value = search,
                            onValueChange = { AppState.search.value = it },
                            placeholder = "搜名称、产地或村庄",
                            icon = BusIcons.search,
                            trailing = {
                                if (search.isNotEmpty()) {
                                    Icon(
                                        BusIcons.close, null,
                                        tint = t.textTertiary,
                                        modifier = Modifier.size(18.dp).clickable { AppState.search.value = "" },
                                    )
                                }
                            },
                        )
                    }
                }

                item {
                    // 分类横滑胶囊（对齐 goods.wxml:22-32 .category-scroll）
                    FilterChipRow(vm.categories, vm.categoryIndex, onSelect = { vm.categoryIndex = it })
                }

                item {
                    // 排序条（对标主流电商：综合/价格/销量）
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        vm.sortOptions.forEachIndexed { i, label ->
                            val on = i == vm.sortIndex
                            Text(
                                label,
                                color = if (on) t.primaryOnLight else t.textSecondary,
                                fontSize = (12 * scale).sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(BusRadius.pill))
                                    .background(if (on) t.primaryLight else Color.Transparent)
                                    .clickable { vm.sortIndex = i }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "共 ${vm.shown.size} 件",
                            color = t.textTertiary,
                            fontSize = (11 * scale).sp,
                        )
                    }
                }

                item { DemoNote(vm.demo) }

                if (vm.loading && vm.allProducts.isEmpty()) {
                    item { SkeletonList(3) }
                } else if (vm.loadError && vm.allProducts.isEmpty()) {
                    item { ErrorState("没能加载山货", onRetry = { vm.reload() }) }
                } else if (vm.shown.isEmpty()) {
                    item {
                        EmptyState(
                            title = if (vm.categoryIndex == 0) "山货还没上新" else "这类山货暂时没货",
                            subtitle = if (vm.categoryIndex == 0) "农户正在备货，过会儿再来看看" else "切到「全部」看看其他山货",
                            icon = BusIcons.parcel,
                            actionText = "清空筛选",
                            onAction = {
                                vm.categoryIndex = 0
                                AppState.search.value = ""
                            },
                        )
                    }
                }

                if (vm.shown.isNotEmpty()) {
                    item { SectionTitle("今日在售", subtitle = "价格含班车运费，到村自提") }
                    // ⚠ 末行补位用 `cols - row.size` 而不是旧写法的 `if (row.size == 1)`：
                    // 旧写法只在 2 列时凑巧成立，列数一变末尾几张卡就会被拉宽到占满整行。
                    itemsIndexed(vm.shown.chunked(goodsCols)) { _, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { p ->
                                GoodsCard(p, Modifier.weight(1f)) { nav.navigate(Routes.goodsDetail(p.id)) }
                            }
                            repeat(goodsCols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                if (vm.hasMore) {
                    item {
                        Text(
                            if (vm.loadingMore) "正在加载…" else "点击加载更多",
                            color = t.primaryOnLight,
                            fontSize = (13 * scale).sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(BusRadius.m))
                                .clickable { vm.loadMore() }
                                .padding(vertical = 12.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    item {
                        LaunchedEffect(vm.pageNo, vm.categoryIndex, search) { vm.loadMore() }
                        Spacer(Modifier.height(1.dp))
                    }
                } else if (vm.shown.isNotEmpty()) {
                    item { ListEndNote() }
                }

                item { Spacer(Modifier.height(6.dp)) }
            }
        }
    }
}

@Composable
private fun GoodsCard(p: Product, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Column(
        modifier
            .clip(RoundedCornerShape(BusRadius.l))
            .background(t.card)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            // 图位底 = 金浅底（goods.wxss .product-img-wrap: gold-light）
            Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(BusRadius.m)).background(t.goldLight),
            contentAlignment = Alignment.Center,
        ) {
            if (p.image.isNotEmpty()) {
                AsyncImage(
                    model = p.image,
                    contentDescription = p.name,
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(BusIcons.leaf, null, tint = t.gold, modifier = Modifier.size(28.dp))
            }
            // 角标 = .product-badge：左下角、金渐变、bus 图标 + 文案（默认「巴士带货」）
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(BusRadius.s))
                    .background(Brush.linearGradient(listOf(t.gold, t.goldDeep)))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BusIcons.bus, null, tint = Color.White, modifier = Modifier.size(10.dp))
                Text(
                    p.badge,
                    color = Color.White,
                    fontSize = (10.5 * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
        }
        Text(
            p.name,
            color = t.ink,
            fontSize = (14 * scale).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        // 副标题（商品卖点）
        if (p.subtitle.isNotEmpty()) {
            Text(
                p.subtitle,
                color = t.textTertiary,
                fontSize = (10.5 * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // 产地村 + 销量行
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(BusIcons.location, null, tint = t.goldDeep, modifier = Modifier.size(11.dp))
            Text(
                p.village.ifEmpty { p.fromVillage },
                color = t.textSecondary,
                fontSize = (11 * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp).weight(1f),
            )
            val sales = p.sales
            if (sales != null && sales > 0) {
                Text(
                    "已售 ${if (sales >= 1000) "${sales / 1000}k" else sales}",
                    color = t.textTertiary,
                    fontSize = (10 * scale).sp,
                )
            }
        }
        // 价格行（有原价时显示划线价）
        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.Bottom) {
            PriceText(formatPrice(p.price), size = 16)
            Text(
                "/${p.unit}",
                color = t.textTertiary,
                fontSize = (11 * scale).sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
            )
            val orig = p.originPrice
            if (orig != null && orig > p.price) {
                Text(
                    "¥${formatPrice(orig)}",
                    color = t.textTertiary,
                    fontSize = (10 * scale).sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                )
            }
        }
        // 库存提示（低库存营造紧迫感，对标主流电商）
        val stock = p.stock
        if (stock != null && stock in 1..5) {
            Text(
                "仅剩 $stock ${p.unit}",
                color = t.danger,
                fontSize = (10 * scale).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
