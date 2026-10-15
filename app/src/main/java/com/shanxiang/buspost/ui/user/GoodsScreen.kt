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
import com.shanxiang.buspost.core.ui.NetworkImage
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

/** 这一屏要不要"全店口径"（纯函数：VM 的 `needsCatalog` 只是把三个状态喂进来） */
internal fun goodsNeedsCatalog(sortIndex: Int, categoryIndex: Int, query: String): Boolean =
    sortIndex != 0 || categoryIndex != 0 || query.isNotBlank()

/** 空态/计数那一格的说法：把"全店没这种东西"和"这一类卖完了"和"清单还没取回来"分成三句（缺陷 #47） */
internal data class GoodsEmptyCopy(val title: String, val subtitle: String)

/**
 * 缺陷 #47：「这类山货暂时没货」这句话替后端宣布了一件它没说过的事。
 * 分类是**本机按商品名分的**（`CATEGORY_KEYWORDS`，后端没有分类字段），所以"这一类没结果"有两种完全不同的成因：
 * 全店清单还没取回来（只在已加载的几页里筛）、后端确实没上架这一类、以及这一类**有货但卖完了**（那是每件商品
 * 自己的 `stock` 字段，与这一格无关）。三种成因必须是三句话，否则用户看到"暂时没货"就不会再刷新。
 */
internal fun goodsEmptyCopy(categoryIndex: Int, query: String, catalog: List<Product>?): GoodsEmptyCopy = when {
    catalog == null && (categoryIndex != 0 || query.isNotBlank()) -> GoodsEmptyCopy(
        "正在取全店在架清单",
        "清单还没回来，这个筛选现在只在已加载的几页里筛，稍等再核对",
    )

    categoryIndex != 0 -> GoodsEmptyCopy(
        "在架清单里没有这一类",
        "分类是按商品名分的，不是这一类卖完了 —— 库存看的是每件商品自己的那一格",
    )

    query.isNotBlank() -> GoodsEmptyCopy(
        "在架清单里没搜到「$query」",
        "换个词试试，或切到「全部」看看别的山货",
    )

    else -> GoodsEmptyCopy("山货还没上新", "农户正在备货，过会儿再来看看")
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
    val hasMore: Boolean get() = !needsCatalog && allProducts.size < total

    /**
     * 全店在架清单（`/product/list`）。缺陷 #47：排序/分类/搜索一旦启用，就必须在**全店**上排；
     * 旧写法只拿着已加载的那几页（首屏 10 件）排，界面念出来的"价格↑第一件"其实是
     * "这 10 件里最便宜的"，而后端明明有 `/product/list` 能把全量取回来。
     * null = 还没取过（不是"取到了空"），空列表才是后端说没有。
     */
    var catalog by mutableStateOf<List<Product>?>(null)
        private set
    var catalogLoading by mutableStateOf(false)
    var catalogError by mutableStateOf<String?>(null)

    /** 这一屏是否要"全店口径"：综合排序 + 全部 + 空词 时按分页看就够 */
    val needsCatalog: Boolean get() = goodsNeedsCatalog(sortIndex, categoryIndex, query)

    /** 列表顶上的那句计数：三种来源三种说法，不许都写成"共 N 件" */
    val countText: String
        get() {
            val n = shown.size
            return when {
                !needsCatalog -> "已加载 $n 件（后端在架共 ${if (total > 0) total else n} 件）"
                catalogLoading && catalog == null -> "正在取全店清单…"
                catalogError != null && catalog == null -> "全店清单没取到，下面只在已加载的 $n 件里排"
                else -> "全店 ${catalog?.size ?: 0} 件里 $n 件"
            }
        }

    val shown: List<Product> get() {
        // 全量还没回来时先用已加载的几页兜着，但 `countText` 会照实说"只在已加载的 N 件里排"，
        // 不许在这一格里把"部分结果"念成"全店结果"。
        val pool = if (needsCatalog) catalog ?: allProducts else allProducts
        val filtered = pool.filter { p ->
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

    /** 只在第一次需要全店口径时取一次；下拉刷新会作废它（见 [reload]） */
    fun ensureCatalog() {
        if (catalog != null || catalogLoading) return
        catalogLoading = true
        work(reset = false) {
            try {
                val f = Repo.products()
                catalogError = f.error
                if (f.error == null) catalog = f.items
                demo = demo || f.demo
            } finally {
                catalogLoading = false
            }
        }
    }

    fun reload() {
        pageNo = 1
        allProducts = emptyList()
        total = 0
        loadError = false
        catalog = null
        catalogError = null
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
            // 翻页失败必须把页码退回去：`loadMore()` 是先 +1 再发请求，留着这个 +1 就等于
            // 下一次"加载更多"直接跳过这一页 —— 那一页的货在这个会话里永远出不来，
            // 用户只会说"商城少了几件"，而没有任何一格记下为什么。
            if (!reset) pageNo -= 1
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
    // 排序/分类/搜索一开启就取一次全店清单（`ensureCatalog()` 自己保证只取一次）
    LaunchedEffect(vm.needsCatalog) { if (vm.needsCatalog) vm.ensureCatalog() }

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
                            vm.countText,
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
                        val copy = goodsEmptyCopy(vm.categoryIndex, vm.query, vm.catalog)
                        EmptyState(
                            title = copy.title,
                            subtitle = copy.subtitle,
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
                NetworkImage(
                    url = p.image,
                    contentDescription = p.name,
                    width = 400.dp,
                    height = 112.dp,
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                    fallbackIcon = BusIcons.leaf,
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
