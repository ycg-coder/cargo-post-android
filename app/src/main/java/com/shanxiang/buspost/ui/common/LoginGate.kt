package com.shanxiang.buspost.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusDialogAction
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens

/**
 * 游客登录引导弹窗 —— 对标主流 App（高德/车来了/货拉拉）的"先体验再注册"模式。
 *
 * 触发场景：游客点了需要登录才能做的事（买东西、寄货、看订单、进个人中心），
 * 弹这个窗告诉他"注册/登录后可以继续"，而不是像旧版那样一上来就强制跳登录页。
 *
 * @param onDismiss 关闭弹窗（回退到游客浏览）
 * @param onLogin 跳转登录页
 */
@Composable
fun LoginGateDialog(
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = 36.dp)
                .clip(RoundedCornerShape(BusRadius.xl))
                .background(t.card)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(BusRadius.l))
                    .background(
                        Brush.linearGradient(listOf(t.primary, t.accent))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    BusIcons.person,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            Text(
                "登录后继续",
                color = t.ink,
                fontSize = (18 * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )

            Text(
                "登录后可享受买东西、寄货下单、到车提醒等服务",
                color = t.textSecondary,
                fontSize = (13.5f * scale).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(24.dp))

            BusButton("立即登录 / 注册", onClick = onLogin)

            BusDialogAction(
                "先逛逛",
                onClick = onDismiss,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
