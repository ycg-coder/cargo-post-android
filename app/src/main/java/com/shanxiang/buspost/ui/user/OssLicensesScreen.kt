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
import com.shanxiang.buspost.core.about.OssLibrary
import com.shanxiang.buspost.core.about.OpenSourceCredits
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.ui.common.BackTopBar

/**
 * 设置 → 关于乡邮智联 → 地图与开源许可（指令 v2.1 第 ⑭ 步的最小入口）。
 *
 * 这一屏只负责**渲染** [OpenSourceCredits]，自己不写任何署名文案或组件名：
 * 署名内容与"到底进没进 APK"的证据都由数据源那一份承担，用例也钉那一份（`OpenSourceCreditsContractTest`），
 * 免得同一段 ODbL 文案在这里改一遍、在许可证台账里又改一遍，两处各自漂移。
 */
@Composable
fun OssLicensesScreen() {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current

    Column {
        BackTopBar(title = "地图与开源许可", subtitle = "道路数据来源与随包分发的第三方组件")
        BusPage(spacedBy = 12.dp) {
            SectionTitle("道路数据署名")
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        OpenSourceCredits.OSM_ATTRIBUTION,
                        color = t.ink,
                        fontSize = (15 * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(OpenSourceCredits.OSM_USAGE, color = t.textSecondary, fontSize = (13.5 * scale).sp)
                    Spacer(Modifier.height(6.dp))
                    Text(OpenSourceCredits.OSM_DATA_LICENSE, color = t.textSecondary, fontSize = (13.5 * scale).sp)
                    BusDivider(Modifier.padding(vertical = 10.dp))
                    Text(
                        OpenSourceCredits.OSM_WHERE,
                        color = t.textTertiary,
                        fontSize = (12.5 * scale).sp,
                    )
                }
            }

            SectionTitle("随安装包分发的开源组件")
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    OpenSourceCredits.included.forEachIndexed { i, lib ->
                        if (i > 0) BusDivider()
                        LibraryRow(lib)
                    }
                }
            }

            SectionTitle("随安装包分发，但不是开源组件")
            BusCard(modifier = Modifier.fillMaxWidth()) {
                OpenSourceCredits.proprietary.forEach { lib -> LibraryRow(lib) }
            }

            SectionTitle("只作参考或在服务端运行，未进入安装包")
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    OpenSourceCredits.referenceOnly.forEach { name ->
                        Text(
                            name,
                            color = t.textTertiary,
                            fontSize = (12.5 * scale).sp,
                            modifier = Modifier.padding(vertical = 5.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LibraryRow(lib: OssLibrary) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text(lib.name, color = t.ink, fontSize = (14.5 * scale).sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(3.dp))
        Text(lib.gradleCoordinate, color = t.textTertiary, fontSize = (12 * scale).sp)
        Spacer(Modifier.height(3.dp))
        Text(lib.license, color = t.textSecondary, fontSize = (12.5 * scale).sp)
    }
}
