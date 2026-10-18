package com.shanxiang.buspost.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.busRowPadding
import com.shanxiang.buspost.core.ui.glassBar
import com.shanxiang.buspost.core.ui.glassSheen
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusSwitch
import com.shanxiang.buspost.core.ui.LocalBusDriving
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.LocalBusTouch

/**
 * 司机端共享积木。抽出来的理由只有一个：**同一个东西原来在各页各写一份**，
 * 改一处漏三处（回执条历史上有四份，样式已经漂移）。
 */

/**
 * 操作回执条：司机动作的后端结论（"已提交后端"/"系统同步中"/"本次未发送"）。
 *
 * 原先 `WorkbenchScreen` / `RoutesScreen` / `HandoverScreen` / `DriverExceptionScreen`
 * 各内联一份同形实现，本次收敛到这里。判定仍只认**文案前缀**：回执文本由 `ScreenViewModel.runAction`
 * 统一产出（三支固定前缀），所以这里读前缀是稳的；但**不要**在别处新增第二种前缀而不改这里。
 */
@Composable
fun ReceiptBar(text: String, modifier: Modifier = Modifier) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val demo = text.contains("系统同步中")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BusRadius.m))
            .background(if (demo) t.goldLight else t.primaryLight)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (demo) BusIcons.info else BusIcons.checkCircle,
            contentDescription = null,
            tint = if (demo) t.goldDeep else t.primaryOnLight,
            modifier = Modifier.size((16 * scale).dp),
        )
        Text(
            text,
            color = if (demo) t.goldDeep else t.primaryOnLight,
            fontSize = (12 * scale).sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * 固定底部操作栏 —— 司机端"下车即操作"的落点。
 *
 * 改造前，工作台唯一必须做的那个动作（确认发车 / 确认到达）被压在「任务地图 + 整张导航卡
 * （十余行实时回执）」下面，首屏够不到；设备实测要划几屏才摸到按钮。这里把它钉在底部，
 * **滚动到哪都在拇指区**，配合 [StickyBarScaffold] 自动给内容留出等高的底部空间（不会挡住最后一张卡）。
 *
 * 栏内的按钮数量控制在 3 颗以内（1 主 + 2 副）：更多就不是"固定动作"而是第二张工作台了。
 */
@Composable
fun StickyActionBar(
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val touch = LocalBusTouch.current
    val topShape = RoundedCornerShape(topStart = BusRadius.l, topEnd = BusRadius.l)
    Column(
        modifier
            .fillMaxWidth()
            // 这一条是**浮在会滚的正文之上**的（`StickyBarScaffold` 量它的高度再让内容让位），
            // 所以它走 `glassBar` 那一档（**实色底** + 同一道高光/描边/投影）而不是标准玻璃：标准档在地图/纸底上好看，
            // 但背后是一整页任务卡时，"开始车道级导航"这类字会从按钮背后透出来（真机 2026-10-18 用户："也要考虑美观"；
            // 第二档最初只提到 0.95/0.92，v204 底片上通栏条后面那行「国庆中秋双节期间班次调整公告」照样读得出来 ⇒ 这一档现在不留光）。
            .glassBar(t, shape = topShape, tint = if (t.isDark) t.cardWarm else t.card)
            .glassSheen(t, topShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (!hint.isNullOrBlank()) {
            Text(
                hint,
                color = t.textTertiary,
                fontSize = (11.5f * scale).sp,
                maxLines = 2,
                modifier = Modifier.padding(bottom = 7.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(touch.gap),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * 把 [StickyActionBar] 叠在内容底部，并把它的**实测高度**回传给内容做底部内边距。
 *
 * 为什么要量而不是写死一个数：栏里可能多一行提示、字号随驾驶模式放大、导航栏 inset 在部分机型上不同，
 * 写死的那个数迟早会小于实际高度，于是最后一张卡被压掉半行（"看不见的 bug"）。
 * 首帧 [bar] 还没测量时给一个保守值（130dp），宁可多留一屏空白也不遮内容。
 */
@Composable
fun StickyBarScaffold(
    bar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (bottomPadding: Dp) -> Unit,
) {
    var barPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val reserved = if (barPx == 0) 130.dp else with(density) { barPx.toDp() } + 12.dp
    Box(modifier.fillMaxSize()) {
        content(reserved)
        Box(Modifier.align(Alignment.BottomCenter).onSizeChanged { barPx = it.height }) { bar() }
    }
}

/**
 * 驾驶模式开关行（司机端一等公民）。
 *
 * 一次打开三件事，因为它们服务同一个场景「人在开车」：字号 ×1.2、触区 56dp、界面转夜间暗色。
 * 副开关「夜间暗色」单列一格：白天开车的人也需要大字大按钮，但不需要把屏幕变暗
 * （挡风玻璃反光才是夜间要压亮度的原因，白天反而要亮）。
 */
@Composable
fun DrivingModeRow(modifier: Modifier = Modifier) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val driving = LocalBusDriving.current
    val dark by AppState.dark.collectAsStateWithLifecycle()
    val drivingNight by AppState.drivingNight.collectAsStateWithLifecycle()

    Column(modifier.fillMaxWidth().padding(busRowPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size((36 * scale).dp)
                    .clip(RoundedCornerShape(BusRadius.m))
                    .background(if (driving) t.clayLight else t.track),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    BusIcons.car,
                    contentDescription = null,
                    tint = if (driving) t.clayDeep else t.textSecondary,
                    modifier = Modifier.size((19 * scale).dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "驾驶模式",
                    color = t.ink,
                    fontSize = (14 * scale).sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (driving) "已开启：大字 · 大按钮（${LocalBusTouch.current.min.value.toInt()}dp）· 夜间暗色"
                    else "开启后：字号放大、按钮加高到 56dp、界面转夜间暗色，方便行车中操作",
                    color = t.textTertiary,
                    fontSize = (11.5f * scale).sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            BusSwitch(checked = driving, onCheckedChange = { AppState.setDriving(it) })
        }
        // 副开关只在驾驶模式开着时才有意义：关着时它不改任何东西，摆出来只会让人以为点了没反应
        if (driving && !dark) {
            Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 47 = 图标那一格 36 + 它与文字之间 11：副开关要跟在主行文字那一列的左沿下面
                Spacer(Modifier.width(47.dp))
                Text(
                    "界面夜间暗色",
                    color = t.textSecondary,
                    fontSize = (12.5f * scale).sp,
                    modifier = Modifier.weight(1f),
                )
                BusSwitch(checked = drivingNight, onCheckedChange = { AppState.setDrivingNight(it) })
            }
        }
    }
}

/**
 * 驾驶模式胶囊（**站牌头专用**，与 [DrivingModeRow] 是同一开关的两个入口）。
 *
 * 为什么放站牌头而不是设置页：这个开关决定"整屏怎么显示"，是当前工作界面的档位，不是一条偏好设置。
 * 司机上车第一件事就是把它打开，埋进「我的 → 设置」等于第二屏才能碰到。
 *
 * 视觉上刻意**不用主题色**：站牌头本身就是 primary 渐变（深浅两套主题下都是深底），
 * 在深底上再叠一个主题色胶囊分不出层次。这里走"亮起来"这一条不依赖色相的语汇：
 * - 关：`rgba(0,0,0,.28)` 底 + 白描边 + 白字 —— 深底小签的既定承载方式，边框同时是"可点"的提示；
 * - 开：纯白底 + `primaryDark` 深墨字 —— 比任何彩色都亮，一眼可辨；深墨取品牌色而非 `#000`，
 *   与站牌头的色系同一支。
 * 两态的对比度都不依赖透明度承载文字（白字纯白、深墨纯深），符合全站"不用透明度表达层级"的约定。
 *
 * 触区是**真 48dp**（`defaultMinSize`），不是视觉高度：站牌头这一行会因此长高约 24dp，
 * 这是行车中能稳定点中的必要代价 —— 视觉小、触区大正是要的效果。
 */
@Composable
fun DrivingModeChip(modifier: Modifier = Modifier) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val touch = LocalBusTouch.current
    val driving = LocalBusDriving.current

    val bg = if (driving) Color.White else Color.Black.copy(alpha = 0.28f)
    val fg = if (driving) t.primaryDark else Color.White

    Row(
        modifier
            .defaultMinSize(minHeight = touch.min)
            .clip(RoundedCornerShape(BusRadius.m))
            .background(bg)
            .then(
                if (driving) Modifier else Modifier.border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(BusRadius.m),
                )
            )
            // toggleable 而非 clickable：无障碍服务会把它报成"开关"并读出当前状态，
            // clickable 只会读成"按钮"，盲人司机按下去不知道现在是开还是关。
            .toggleable(
                value = driving,
                role = Role.Switch,
                onValueChange = { AppState.setDriving(it) },
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            BusIcons.car,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size((15 * scale).dp),
        )
        Text(
            // 文案固定不带状态词：状态已经由牌面明暗表达，再写"已开启"在 13sp 下会撑宽这一行，
            // 把旁边的载重/认证标签挤掉（站牌头这一行是 space-between 的最后一格）。
            "驾驶模式",
            color = fg,
            fontSize = (12 * scale).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
