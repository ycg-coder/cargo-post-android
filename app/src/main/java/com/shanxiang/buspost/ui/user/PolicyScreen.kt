package com.shanxiang.buspost.ui.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanxiang.buspost.core.about.AppPolicies
import com.shanxiang.buspost.core.about.PolicyKind
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.ui.common.BackTopBar

/**
 * 隐私政策 / 用户协议页（与 `OssLicensesScreen` 同一形状：**页面只渲染，一个字都不写**）。
 *
 * 内容来自 [AppPolicies]，用例也钉那一份（`AppPoliciesContractTest`）。
 * 之所以强调这一点：这两份文字此前只在登录页以书名号出现、正文无处可看，
 * 页面再抄一遍就会变成"第三处需要维护的副本"，迟早和登录页那句话对不上。
 */
@Composable
internal fun PolicyScreen(kind: PolicyKind) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val doc = AppPolicies.of(kind)

    Column {
        BackTopBar(title = doc.title, subtitle = doc.subtitle)
        BusPage(spacedBy = 12.dp) {
            SectionTitle("更新日期：${doc.updatedOn}")
            doc.sections.forEach { sec ->
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            sec.heading,
                            color = t.ink,
                            fontSize = (15 * scale).sp,
                            fontWeight = FontWeight.Bold,
                        )
                        sec.lines.forEachIndexed { i, line ->
                            // 第一行与标题之间留白，其余条目之间用分隔线；不留"看起来像挤成一坨"的正文
                            if (i == 0) Spacer(Modifier.height(6.dp)) else BusDivider(Modifier.padding(vertical = 8.dp))
                            Text(line, color = t.textSecondary, fontSize = (13.5 * scale).sp)
                        }
                    }
                }
            }
            BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                Text(doc.disclaimer, color = t.clayDeep, fontSize = (12.5 * scale).sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
