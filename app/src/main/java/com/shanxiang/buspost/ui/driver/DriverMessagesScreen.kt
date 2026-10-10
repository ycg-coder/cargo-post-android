package com.shanxiang.buspost.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusPullRefresh
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.EmptyState
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.SkeletonList
import com.shanxiang.buspost.core.ui.Signpost
import com.shanxiang.buspost.data.model.NotifyMsg
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.Paginator
import com.shanxiang.buspost.ui.common.ScreenViewModel

/**
 * 调度消息：司机端的派单/交接/异常通知。与村民端消息中心分开水位，
 * 角标写回 AppState.driverUnread，底部 tab 与系统通知共用同一计数。
 */
class DriverMessagesViewModel : ScreenViewModel() {
    /** 与村民端消息中心同一套分页口径：首屏 / 刷新 / 上拉加载 / 末页 / 空态 / 失败重试 */
    private val pager = Paginator<NotifyMsg>()

    var onlyUnread by mutableStateOf(false)
        private set

    var refreshing by mutableStateOf(false)
        private set

    val items: List<NotifyMsg> get() = pager.items
    val hasMore: Boolean get() = pager.hasMore
    val loadingMore: Boolean get() = pager.loadingMore

    val shown: List<NotifyMsg> get() = if (onlyUnread) items.filter { !it.read } else items

    fun reload() {
        refreshing = true
        load(reset = true)
    }

    fun load(reset: Boolean = true) = work(reset = reset || !pager.initialized) {
        try {
            fetch(reset)
        } finally {
            refreshing = false
        }
    }

    fun loadMore() = load(reset = false)

    private suspend fun fetch(reset: Boolean) {
        pager.load(append = !reset) { pageNo, pageSize -> Repo.driverMessages(pageNo, pageSize) }
        demo = pager.demo
        error = pager.error
        empty = pager.isEmpty
        // 角标取后端计数：本机只握着已加载的几页，用它算未读会在翻页时跳数；
        // 标量接口读不到时（异常/未绑定档案）才退回本页计数，绝不写 0（审计 §23/§52）
        AppState.reportDriverUnread(
            serverCount = Repo.driverUnreadCount(),
            listCount = if (error == null) items.count { !it.read } else null,
        )
        val maxId = items.maxOfOrNull { it.id } ?: 0L
        if (maxId > Prefs.lastSeenDriverMsgId) Prefs.lastSeenDriverMsgId = maxId
    }

    fun toggleUnread() {
        onlyUnread = !onlyUnread
    }

    /** 已读只由后端决定：提交后重拉本页，绝不在本机把消息"改成已读" */
    fun read(msg: NotifyMsg) {
        if (msg.read) return
        work(reset = false) {
            Repo.readDriverMessage(msg.id)
            Prefs.lastSeenDriverMsgId = maxOf(Prefs.lastSeenDriverMsgId, msg.id)
            pager.reload { pageNo, pageSize -> Repo.driverMessages(pageNo, pageSize) }
            demo = pager.demo
            error = pager.error
            empty = pager.isEmpty
            AppState.reportDriverUnread(
                serverCount = Repo.driverUnreadCount(),
                listCount = if (error == null) items.count { !it.read } else null,
            )
        }
    }
}

@Composable
fun DriverMessagesScreen(vm: DriverMessagesViewModel = viewModel()) {
    val nav = LocalNavController.current
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current

    LaunchedEffect(Unit) { if (vm.items.isEmpty()) vm.load() }

    BusPullRefresh(refreshing = vm.refreshing, onRefresh = { vm.reload() }) {
    BusPage(spacedBy = 12.dp) {
        // 紧凑页头：标题 + 未读数 + 状态标签一行排开
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(3.dp, 15.dp).clip(RoundedCornerShape(2.dp)).background(t.clay))
                    Spacer(Modifier.width(8.dp))
                    Text("调度消息", color = t.ink, fontSize = (18 * scale).sp, fontWeight = FontWeight.Bold)
                    if (vm.shown.count { !it.read } > 0) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(BusRadius.s))
                                .background(t.danger.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "${vm.shown.count { !it.read }} 条未读",
                                color = t.danger,
                                fontSize = (11 * scale).sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                Text(
                    "派单、交接与异常都在这里",
                    color = t.textTertiary,
                    fontSize = (12 * scale).sp,
                    modifier = Modifier.padding(top = 4.dp, start = 11.dp),
                )
            }
        }

        // 状态标签行
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            BusTag(if (Prefs.notifyEnabled) "系统通知已开" else "系统通知已关", tone = if (Prefs.notifyEnabled) BusTone.PRIMARY else BusTone.NEUTRAL)
            BusTag("后台每 15 分钟同步", tone = BusTone.CLAY)
        }

        DemoNote(vm.demo, text = "当前为演示通知。")
        vm.error?.let { ErrorState(it, onRetry = { vm.load() }) }

        // 筛选按钮 + 消息计数内联
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BusButton(
                    text = if (vm.onlyUnread) "查看全部" else "只看未读",
                    small = true,
                    variant = BusButtonVariant.OUTLINE,
                    onClick = { vm.toggleUnread() },
                    fullWidth = false,
                )
                BusButton(
                    text = "刷新",
                    small = true,
                    variant = BusButtonVariant.OUTLINE,
                    onClick = { vm.load() },
                    fullWidth = false,
                )
            }
            Text(
                "${vm.shown.size} 条",
                color = t.textTertiary,
                fontSize = (12 * scale).sp,
            )
        }

        if (vm.loading && vm.items.isEmpty()) {
            SkeletonList(4)
        } else if (vm.shown.isEmpty()) {
            EmptyState(
                title = if (vm.empty) "没有消息" else "消息没取到",
                subtitle = if (vm.empty) "新派单会自动出现在这里并弹通知" else "下拉刷新或点「刷新」重试",
                actionText = "刷新",
                onAction = { vm.load() },
            )
        }
        vm.shown.forEach { msg ->
            MsgCard(
                msg = msg,
                onClick = {
                    vm.read(msg)
                    if (msg.orderNo.isNotEmpty()) nav.navigate(Routes.trace(msg.orderNo))
                },
            )
        }
        if (vm.hasMore) {
            BusButton(
                text = if (vm.loadingMore) "加载中…" else "加载更多",
                variant = BusButtonVariant.TEXT,
                enabled = !vm.loadingMore,
                onClick = { vm.loadMore() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    }
}

@Composable
private fun MsgCard(msg: NotifyMsg, onClick: () -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val accentColor = when {
        msg.type.contains("EXCEPTION") || msg.type.contains("异常") -> t.danger
        msg.type.contains("HANDOVER") || msg.type.contains("交接") -> t.clay
        else -> t.primary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BusRadius.l))
            .background(t.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 左侧色条：按消息类型着色，增强视觉区分
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor.copy(alpha = if (msg.read) 0.3f else 0.8f)),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .padding(top = 5.dp)
                .size((7 * scale).dp)
                .clip(CircleShape)
                .background(if (msg.read) t.line else t.danger),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                msg.title.ifEmpty { "调度通知" },
                color = if (msg.read) t.textSecondary else t.ink,
                fontSize = (14 * scale).sp,
                fontWeight = if (msg.read) FontWeight.Medium else FontWeight.Bold,
            )
            Text(
                msg.content.ifEmpty { "—" },
                color = t.textSecondary,
                fontSize = (12.5 * scale).sp,
                modifier = Modifier.padding(top = 3.dp),
            )
            Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (msg.type.isNotEmpty()) BusTag(typeLabel(msg.type), tone = toneOf(msg.type))
                if (msg.orderNo.isNotEmpty()) BusTag(msg.orderNo)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Icon(
                BusIcons.bell,
                contentDescription = null,
                tint = if (msg.read) t.textTertiary else t.primary,
                modifier = Modifier.size((16 * scale).dp),
            )
            Text(
                msg.time.takeLast(5),
                color = t.textTertiary,
                fontSize = (11 * scale).sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

private fun typeLabel(type: String) = when {
    type.contains("SHIFT") || type.contains("班次") -> "班次"
    type.contains("HANDOVER") || type.contains("交接") -> "交接"
    type.contains("EXCEPTION") || type.contains("异常") -> "异常"
    type.contains("LOAD") || type.contains("装车") -> "装车"
    else -> "调度"
}

private fun toneOf(type: String) = when {
    type.contains("EXCEPTION") || type.contains("异常") -> BusTone.DANGER
    type.contains("HANDOVER") || type.contains("交接") -> BusTone.CLAY
    else -> BusTone.PRIMARY
}
