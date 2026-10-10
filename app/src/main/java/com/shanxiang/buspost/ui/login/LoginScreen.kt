package com.shanxiang.buspost.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.core.ui.NetworkImage
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusSegmented
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.Signpost
import com.shanxiang.buspost.data.model.Role

/**
 * 登录页：进入就先选角色（我是用户 / 我是司机），两套 tab 与权限完全分开，
 * 这是小程序版没有的能力——小程序登录后靠接口判断身份，司机进错界面只能退出重登。
 */
@Composable
fun LoginScreen(
    onEntered: (Role) -> Unit,
    vm: LoginViewModel = viewModel(),
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    // Edge-to-Edge（2026-10-09）：targetSdk 36 起系统强制全屏。本页是**全屏路由**、不在 BusScaffold 里，
    // 顶栏也不是 BusTopBar（是自绘的 Logo 区），所以系统栏避让只能在这里自己做。
    // background 放在 padding 之前：纸色要铺满整屏（连状态栏/手势条那两条都铺），
    // 内容再整体让开 —— 顺序反了会在顶部和底部各留一条没涂色的缝。
    Box(Modifier.fillMaxSize().background(t.paper).windowInsetsPadding(WindowInsets.systemBars)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            // Logo 和标题
            Column(
                Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NetworkImage(
                    url = "file:///android_asset/logo.png",
                    contentDescription = null,
                    width = 64.dp,
                    height = 64.dp,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(BusRadius.l)),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    "乡邮智联",
                    color = t.ink,
                    fontSize = (24 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "客货邮 · 城乡直达",
                    color = t.textSecondary,
                    fontSize = (13 * scale).sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // 角色选择 - 简化为两个大按钮
            Text("选择身份", color = t.ink, fontSize = (15 * scale).sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            
            RoleCardSimple(
                modifier = Modifier.fillMaxWidth(),
                title = "我是用户",
                icon = BusIcons.person,
                selected = vm.role == Role.USER,
                onClick = { vm.role = Role.USER },
            )
            Spacer(Modifier.height(10.dp))
            RoleCardSimple(
                modifier = Modifier.fillMaxWidth(),
                title = "我是司机",
                icon = BusIcons.bus,
                selected = vm.role == Role.DRIVER,
                onClick = { vm.role = Role.DRIVER },
            )

            Spacer(Modifier.height(24.dp))

            // 登录表单
            BusTextField(
                value = vm.mobile,
                onValueChange = { v -> vm.mobile = v.filter { it.isDigit() }.take(11) },
                label = "手机号",
                placeholder = "请输入手机号",
                icon = BusIcons.phone,
                keyboardType = KeyboardType.Phone,
            )
            
            if (vm.mode == 0) {
                Spacer(Modifier.height(12.dp))
                BusTextField(
                    value = vm.password,
                    onValueChange = { vm.password = it },
                    label = "密码",
                    placeholder = "请输入密码",
                    icon = BusIcons.lock,
                    keyboardType = KeyboardType.Password,
                )
            } else {
                Spacer(Modifier.height(12.dp))
                BusTextField(
                    value = vm.code,
                    // 上限跟着后端契约走：yudao.sms-code 用 endCode 的位数补零，生产 999999 ⇒ 最长 6 位，
                    // 留 8 位等于允许用户输入后端永远发不出来的长度。
                    onValueChange = { v -> vm.code = v.filter { it.isDigit() }.take(6) },
                    label = "验证码",
                    placeholder = "6位数字",
                    icon = BusIcons.shield,
                    keyboardType = KeyboardType.Number,
                    trailing = {
                        Text(
                            if (vm.countdown > 0) "${vm.countdown}s" else "获取",
                            color = if (vm.countdown > 0) t.textTertiary else t.primaryOnLight,
                            fontSize = (13 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(BusRadius.pill))
                                .clickable(enabled = vm.countdown == 0) { vm.sendSms() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    },
                )
            }

            // 切换登录方式（与小程序 .switch-mode 同形：整行居中、给足点击热区）
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (vm.mode == 0) "验证码登录" else "密码登录",
                    color = t.primary,
                    fontSize = (14 * scale).sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(BusRadius.pill))
                        .clickable { vm.mode = if (vm.mode == 0) 1 else 0 }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }

            // 回执：error = 这次没办成，notice = 上一次操作的回执（发送验证码的结果只有这一处出口）
            vm.notice?.let {
                Text(
                    it,
                    color = t.goldDeep,
                    fontSize = (12 * scale).sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            vm.error?.let {
                Text(
                    it,
                    color = t.danger,
                    fontSize = (12 * scale).sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // 协议勾选
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                Checkbox(
                    checked = vm.agreed,
                    onCheckedChange = { vm.agreed = it },
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    "我已阅读并同意《服务协议》",
                    color = t.textSecondary,
                    fontSize = (12 * scale).sp,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .clickable { vm.agreed = !vm.agreed },
                )
            }

            Spacer(Modifier.height(20.dp))

            // 登录按钮
            BusButton(
                text = "登录",
                onClick = { vm.submit(onEntered) },
                enabled = vm.canSubmit,
                loading = vm.busy,
            )

            // 演示入口：release 构建（DEMO_ALLOWED=false）不出现这个按钮，也不存在演示会话
            if (vm.demoEntryAvailable) {
                Text(
                    "演示模式",
                    color = t.textTertiary,
                    fontSize = (12 * scale).sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clickable { vm.demoEnter(onEntered) },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            // 大字模式
            val elderly by AppState.elderly.collectAsStateWithLifecycle()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable { AppState.setElderly(!elderly) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (elderly) BusIcons.eyeOff else BusIcons.eye,
                    null,
                    tint = t.textTertiary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    if (elderly) "关闭大字模式" else "大字模式",
                    color = t.textTertiary,
                    fontSize = (12 * scale).sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RoleCardSimple(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Box(
        modifier
            .clip(RoundedCornerShape(BusRadius.m))
            .background(if (selected) t.primaryLight else t.card)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) t.primary else t.line,
                RoundedCornerShape(BusRadius.m),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) t.primary else t.textSecondary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                title,
                color = if (selected) t.primary else t.ink,
                fontSize = (15 * scale).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 10.dp),
            )
            if (selected) {
                Spacer(Modifier.weight(1f))
                Icon(
                    BusIcons.check,
                    contentDescription = null,
                    tint = t.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
