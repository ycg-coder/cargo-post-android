package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusPullRefresh
import com.shanxiang.buspost.core.ui.BusSegmented
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.EmptyState
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.LocalBusTouch
import com.shanxiang.buspost.core.ui.pressFeedback
import com.shanxiang.buspost.core.ui.SkeletonList
import com.shanxiang.buspost.data.model.NotifyMsg
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.notify.NotifyWorker
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.Paginator
import com.shanxiang.buspost.ui.common.ScreenViewModel

class NotificationViewModel : ScreenViewModel() {
    /** 统一分页口径：首屏 / 下拉刷新 / 上拉加载 / 末页判定 / 空态 / 失败重试都在 Paginator 里 */
    private val pager = Paginator<NotifyMsg>()

    var onlyUnread by mutableStateOf(false)

    val items: List<NotifyMsg> get() = pager.items
    val hasMore: Boolean get() = pager.hasMore
    val loadingMore: Boolean get() = pager.loadingMore

    val shown: List<NotifyMsg> get() = if (onlyUnread) items.filter { !it.read } else items

    /** 下拉刷新指示器由调用方持有；load 结束一定复位 */
    var refreshing by mutableStateOf(false)
        private set

    fun reload() {
        refreshing = true
        load(reset = true)
    }

    fun loadMore() = load(reset = false)

    fun load(reset: Boolean = true) = work(reset = reset || !pager.initialized) {
        try {
            fetch(reset)
        } finally {
            refreshing = false
        }
    }

    private suspend fun fetch(reset: Boolean) {
        pager.load(append = !reset) { pageNo, pageSize -> Repo.notifications(pageNo, pageSize) }
        demo = pager.demo
        error = pager.error
        empty = pager.isEmpty
        AppState.reportUserUnread(
            serverCount = Repo.unreadCount(),
            listCount = if (error == null) pager.items.count { !it.read } else null,
        )
    }

    /**
     * 已读状态以后端为准：不在本机把消息"改成已读"，而是提交后端后重拉本页 + 未读数。
     * 弱网下最坏情况是这一条暂时还是未读，但绝不会出现"本机已读、后端未读"的错位。
     */
    fun read(msg: NotifyMsg) {
        if (msg.read) return
        work(reset = false) {
            Repo.readNotification(msg.id)
            // 已提醒水位只前进不后退，避免 Worker 重启后把这条又弹一遍
            Prefs.lastSeenNotifyId = maxOf(Prefs.lastSeenNotifyId, msg.id)
            fetch(reset = true)
        }
    }

    fun readAll() = work(reset = false) {
        Repo.readAllNotifications()
        fetch(reset = true)
    }
}

@Composable
fun NotificationScreen(vm: NotificationViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    val context = LocalContext.current
    val touch = LocalBusTouch.current
    val unread by AppState.unread.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (vm.items.isEmpty()) vm.load() }

    Column {
        BackTopBar(
            title = "消息中心",
            subtitle = if (unread > 0) "$unread 条未读" else "全部已读",
            actions = {
                // 这颗原来是裸 `Text + clickable`：实测 112x66px = 41x24dp，两个边都低于 48dp 下限，
                // 而它做的是"一次清掉所有未读"这种最该一按就中的动作。
                // 修法是把**命中区**撑到触控下限（视觉不动），不是把这格搬到悬浮胶囊上 ——
                // 这一屏有顶栏、有返回，动作本来就在它该在的地方（用户口径："有些没必要悬浮就不要悬浮"）。
                val readAllSrc = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .clip(CircleShape)
                        .pressFeedback(readAllSrc)
                        .clickable(
                            interactionSource = readAllSrc,
                            indication = LocalIndication.current,
                            enabled = unread > 0,
                            onClick = { vm.readAll() },
                        )
                        .defaultMinSize(minWidth = touch.min, minHeight = touch.min)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "全部已读",
                        color = if (unread > 0) t.onPrimary.copy(alpha = 0.9f) else t.onPrimary.copy(alpha = 0.4f),
                        fontSize = (12.5 * scale).sp,
                    )
                }
            },
        )
        BusPullRefresh(refreshing = vm.refreshing, onRefresh = { vm.reload() }) {
            BusPage(spacedBy = 12.dp) {
            if (!Prefs.notifyEnabled) {
                BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(BusIcons.bell, null, tint = t.clayDeep, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f).padding(start = 9.dp)) {
                            Text("后台提醒已关闭", color = t.ink, fontSize = (13.5 * scale).sp, fontWeight = FontWeight.Bold)
                            Text(
                                "开启后不用打开 App 也能收到到车与状态变化通知。",
                                color = t.textSecondary,
                                fontSize = (11.5 * scale).sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Text(
                            "开启",
                            color = t.primaryOnLight,
                            fontSize = (13 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    Prefs.notifyEnabled = true
                                    NotifyWorker.runNow(context)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            BusSegmented(
                // 这两个数**按本页已加载的消息算**，所以必须写"本页"：顶栏那句 `N 条未读` 读的是
                // 后端 `/unread-count`，两者同源不同量，不加限定词就是同屏两个"未读数"（缺陷 #47）。
                options = listOf("本页全部 ${vm.items.size}", "本页未读 ${vm.items.count { !it.read }}"),
                selected = if (vm.onlyUnread) 1 else 0,
                onSelect = { vm.onlyUnread = it == 1 },
            )

            DemoNote(vm.demo, text = "这些是本地演示消息，用于查看提醒与已读交互。")
            vm.error?.let { ErrorState(it, onRetry = { vm.load() }) }

            if (vm.loading && vm.items.isEmpty()) {
                SkeletonList(4)
            } else if (vm.items.isEmpty() && !vm.empty) {
                // 请求失败导致的空列表：上方已挂 ErrorState + 重试，这里不能再报"暂无消息"（那是把失败说成没数据）
                Spacer(Modifier.height(0.dp))
            } else if (vm.shown.isEmpty()) {
                EmptyState(
                    title = if (vm.onlyUnread) "没有未读消息" else "暂无消息",
                    subtitle = "到车提醒、订单动态会在这里通知你",
                    icon = BusIcons.bell,
                )
            } else {
                vm.shown.forEach { msg ->
                    MsgCard(msg) {
                        vm.read(msg)
                        if (msg.orderNo.isNotEmpty()) nav.navigate(Routes.trace(msg.orderNo))
                    }
                }
                if (vm.hasMore) {
                    BusButton(
                        text = if (vm.loadingMore) "加载中…" else "加载更多",
                        enabled = !vm.loadingMore,
                        onClick = { vm.loadMore() },
                        variant = BusButtonVariant.OUTLINE,
                        fullWidth = false,
                        small = true,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MsgCard(msg: NotifyMsg, onClick: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val tone = when {
        msg.type.contains("ARRIVAL") || msg.title.contains("到车") || msg.title.contains("到达") -> BusTone.CLAY
        msg.type.contains("ORDER") || msg.title.contains("订单") -> BusTone.PRIMARY
        msg.type.contains("SHIFT") || msg.title.contains("班次") -> BusTone.GOLD
        else -> BusTone.NEUTRAL
    }
    val bar = when {
        msg.read -> t.line
        tone == BusTone.CLAY -> t.clay
        tone == BusTone.PRIMARY -> t.primary
        tone == BusTone.GOLD -> t.goldDeep
        else -> t.textTertiary
    }
    BusCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (msg.orderNo.isNotEmpty()) {
                        clipboard.setText(AnnotatedString(msg.orderNo))
                        Toast.makeText(context, "单号 ${msg.orderNo} 已复制", Toast.LENGTH_SHORT).show()
                    }
                },
            ),
        padding = PaddingValues(13.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // 未读左侧语义色竖条
            Box(Modifier.width(4.dp).fillMaxHeight().background(bar))
            Spacer(Modifier.width(10.dp))
            // 事件图标：把 21 种消息事件收敛到统一的语义图标组
            Icon(
                eventIcon(msg),
                contentDescription = null,
                tint = if (msg.read) t.textTertiary else bar,
                modifier = Modifier.size((20 * scale).dp).padding(top = 2.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    msg.title,
                    color = if (msg.read) t.textSecondary else t.ink,
                    fontSize = (14 * scale).sp,
                    fontWeight = if (msg.read) FontWeight.Medium else FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    msg.content,
                    color = t.textSecondary,
                    fontSize = (12 * scale).sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    Modifier.padding(top = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BusTag(toneLabel(tone), tone = tone)
                    if (msg.actionRequired) BusTag("需要处理", tone = BusTone.WARN)
                    if (msg.orderNo.isNotEmpty()) BusTag(msg.orderNo)
                    Text(msg.time, color = t.textTertiary, fontSize = (11 * scale).sp)
                }
            }
        }
    }
}

/** 事件类型 → 语义图标 */
private fun eventIcon(msg: NotifyMsg): ImageVector = when {
    msg.type.contains("ARRIVAL") || msg.title.contains("到车") || msg.title.contains("到达") -> BusIcons.bus
    msg.type.contains("ORDER") || msg.title.contains("订单") -> BusIcons.parcel
    msg.type.contains("SHIFT") || msg.title.contains("班次") -> BusIcons.route
    msg.type.contains("PICKUP") || msg.title.contains("取件") -> BusIcons.qr
    msg.type.contains("DELIVER") || msg.title.contains("妥投") || msg.title.contains("送达") -> BusIcons.checkCircle
    msg.type.contains("REVIEW") || msg.title.contains("审核") -> BusIcons.warning
    msg.type.contains("PAY") || msg.title.contains("支付") || msg.title.contains("付款") -> BusIcons.money
    msg.type.contains("COUPON") || msg.title.contains("优惠") || msg.title.contains("券") -> BusIcons.wallet
    msg.type.contains("WEATHER") || msg.title.contains("天气") -> BusIcons.weatherSun
    msg.type.contains("NOTICE") || msg.title.contains("公告") -> BusIcons.info
    msg.actionRequired -> BusIcons.flag
    else -> BusIcons.bell
}

private fun toneLabel(tone: BusTone) = when (tone) {
    BusTone.CLAY -> "到车提醒"
    BusTone.PRIMARY -> "订单状态"
    BusTone.GOLD -> "班次调整"
    BusTone.OK -> "已完成"
    BusTone.WARN -> "待处理"
    BusTone.DANGER -> "异常"
    BusTone.NEUTRAL -> "通知"
}
