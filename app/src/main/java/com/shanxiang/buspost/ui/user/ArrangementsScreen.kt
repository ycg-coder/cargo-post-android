package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.EmptyState
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.SkeletonList
import com.shanxiang.buspost.data.model.Arrangement
import com.shanxiang.buspost.data.repo.RemoteState
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.ScreenViewModel
import com.shanxiang.buspost.ui.common.toState

/**
 * 用户端「我的乘车安排」（审计 §68 那条死通路的落点：`Repo.myArrangements()` 早就接的是
 * `GET /send/arrangements`，但全仓没有一个消费者 —— 通路侧是死代码、界面侧是缺那一格）。
 *
 * 这一页只做四件事：显示后端真给的六个键、把"没取到 / 还没有 / 有 n 条"分开展示、
 * 演示数据如实标注、给一个重取入口。**判定不在这层**（全在 `ArrangementRows.kt` 的纯函数里）。
 */
class ArrangementsViewModel : ScreenViewModel() {
    /** 初值是"还没取过结论"而不是空列表：开局就写 `data = emptyList(), success = true` 等于替后端说了"没有安排" */
    var state by mutableStateOf(RemoteState<List<Arrangement>>(loading = true))
        private set

    var fetched by mutableStateOf(false)
        private set

    fun load() {
        fetched = true
        work(reset = state.data == null) {
            val s = Repo.myArrangements().toState()
            state = s
            demo = s.demo
            error = s.error
            empty = s.success && s.data.orEmpty().isEmpty()
        }
    }
}

@Composable
fun ArrangementsScreen(vm: ArrangementsViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val page = arrangementsPage(vm.state, vm.loading)

    LaunchedEffect(Unit) { if (!vm.fetched) vm.load() }

    Column {
        BackTopBar(title = "我的乘车安排", subtitle = "哪班公交带货、在哪个站交接")
        BusPage(spacedBy = 12.dp) {
            DemoNote(page.demo, text = "当前为演示安排，不是平台真给出的承运信息。")
            when (page.kind) {
                ArrangementsKind.Loading -> SkeletonList(3)
                ArrangementsKind.Failed -> ErrorState(page.body, onRetry = { vm.load() })
                ArrangementsKind.Empty -> EmptyState(
                    title = page.title,
                    subtitle = page.body,
                    actionText = "重新取",
                    onAction = { vm.load() },
                )

                ArrangementsKind.Shown -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    ) {
                        Text(page.title, color = t.textSecondary, fontSize = (13 * scale).sp)
                        BusButton(
                            text = if (vm.loading) "重新取…" else "重新取",
                            small = true,
                            variant = BusButtonVariant.OUTLINE,
                            enabled = !vm.loading,
                            onClick = { vm.load() },
                            fullWidth = false,
                        )
                    }
                    page.items.forEach { ArrangementCard(it) }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

/** 一行就是一个"标签 → 后端真给的原文 / 没给就说明没给"，页面自己不补第三个值 */
@Composable
private fun ArrangementCard(a: Arrangement) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            arrangementRows(a).forEachIndexed { i, (label, value) ->
                if (i > 0) BusDivider(Modifier.padding(vertical = 7.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        color = t.textTertiary,
                        fontSize = (12.5 * scale).sp,
                        modifier = Modifier.width(84.dp),
                    )
                    Text(
                        value,
                        color = if (value == NOT_GIVEN) t.textTertiary else t.ink,
                        fontSize = (13.5 * scale).sp,
                        fontWeight = if (i <= 1) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
