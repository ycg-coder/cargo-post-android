package com.shanxiang.buspost.ui.user

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusSpace
import com.shanxiang.buspost.core.ui.BusText
import com.shanxiang.buspost.core.ui.BusTheme
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.data.store.Prefs

/** 设置页：对齐小程序 pages/settings/settings.wxml */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current

    var elderlyMode by remember { mutableStateOf(Prefs.elderlyMode) }
    var darkMode by remember { mutableStateOf(Prefs.darkMode) }
    var themeColor by remember { mutableStateOf(Prefs.themeColor) }

    // 同步到 Prefs（即时持久化）并更新 AppState。
    // ⚠ 只走 AppState 的 setter，**不在这里直写 flow / Prefs**：`AppState.nightUi` 是"dark 或 驾驶模式夜间"
    // 的派生值，只有 setter 会重算它；绕过去直写就会出现"设置里切了夜间、导航 HUD 不变"这种不刷新。
    fun saveElderly(v: Boolean) {
        elderlyMode = v
        AppState.setElderly(v)
    }

    fun saveDark(v: Boolean) {
        darkMode = v
        AppState.setDark(v)
    }

    fun saveTheme(v: String) {
        if (v == themeColor) return
        themeColor = v
        AppState.setTheme(BusTheme.fromId(v))
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(t.primary)
                    .padding(horizontal = BusSpace.xxl, vertical = BusSpace.l),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BusSpace.m),
                ) {
                    Icon(
                        imageVector = BusIcons.gear,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                Text(
                    text = "字体与主题颜色，全端即时生效",
                    style = BusText.secondary().copy(color = Color.White.copy(alpha = 0.85f)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(top = 48.dp),
                )
            }
        },
        containerColor = t.paper,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = BusSpace.l),
            verticalArrangement = Arrangement.spacedBy(BusSpace.xl),
        ) {
            // 显示设置
            SectionLabel(icon = BusIcons.font, label = "显示设置")
            CardGroup {
                ToggleRow(
                    icon = BusIcons.font,
                    title = "老年人模式",
                    desc = "更大字体、更大按钮，方便长辈使用",
                    checked = elderlyMode,
                    onToggle = { saveElderly(!elderlyMode) },
                )
                ToggleRow(
                    icon = BusIcons.sun,
                    title = "深色模式",
                    desc = "暖黑夜览，司机夜间行车不刺眼",
                    checked = darkMode,
                    onToggle = { saveDark(!darkMode) },
                )
            }

            // 主题颜色
            SectionLabel(icon = BusIcons.palette, label = "主题颜色")
            CardGroup {
                ThemeList(
                    selected = themeColor,
                    onSelect = { saveTheme(it) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BusSpace.s),
        modifier = modifier.padding(horizontal = BusSpace.xxl),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = t.primaryDark,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = t.ink,
            ),
        )
    }
}

@Composable
private fun CardGroup(content: @Composable () -> Unit) {
    val t = LocalBusTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BusSpace.xxl)
            .clip(RoundedCornerShape(BusRadius.card))
            .background(t.card)
            .border(1.dp, t.line, RoundedCornerShape(BusRadius.card))
            .padding(BusSpace.s),
        verticalArrangement = Arrangement.spacedBy(BusSpace.s),
    ) {
        content()
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(BusSpace.m),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = t.primary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(modifier = Modifier.width(BusSpace.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = t.ink,
                ),
            )
            Text(
                text = desc,
                style = BusText.secondary(),
            )
        }
        ToggleSwitch(checked = checked)
    }
}

@Composable
private fun ToggleSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    val trackColor = if (checked) t.primary else t.track
    val knobX = remember { Animatable(if (checked) 1f else 0f) }

    LaunchedEffect(checked) {
        knobX.animateTo(
            targetValue = if (checked) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
        )
    }

    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(CircleShape)
            .background(trackColor)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .offset { IntOffset((knobX.value * 18).toInt(), 0) }
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

private data class ThemeItem(
    val key: String,
    val name: String,
    val color: Color,
    val desc: String,
)

private val themeList = listOf(
    ThemeItem("green", "山野绿", Color(0xFF2E7D32), "默认主题"),
    ThemeItem("orange", "陶土橙", Color(0xFFB4511F), "热情温暖"),
    ThemeItem("blue", "山泉蓝", Color(0xFF2d7bc4), "沉稳冷静"),
    ThemeItem("red", "中国红", Color(0xFFA6242F), "喜庆热烈"),
)

@Composable
private fun ThemeList(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BusSpace.s),
    ) {
        themeList.forEachIndexed { index, item ->
            ThemeRow(
                item = item,
                selected = item.key == selected,
                onClick = { onSelect(item.key) },
                delayMs = (index.coerceAtMost(7)) * 45,
            )
        }
    }
}

@Composable
private fun ThemeRow(
    item: ThemeItem,
    selected: Boolean,
    onClick: () -> Unit,
    delayMs: Int,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BusRadius.m))
            .background(if (selected) t.primaryLight else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(BusSpace.m),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(item.color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(BusSpace.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = t.ink,
                ),
            )
            Text(
                text = item.desc,
                style = BusText.secondary(),
            )
        }
        if (selected) {
            Text(
                text = "使用中",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = t.primary,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
