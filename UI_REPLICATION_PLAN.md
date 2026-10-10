# 乡邮智联 Android App UI 复刻方案

## 一、现状分析（基于20张对比截图）

### 1.1 小程序核心视觉特征

#### 头部设计
- **站牌式Header**：深蓝渐变背景 `#1e5a96 → #2d7bc4`，白色文字
- **顶部横档**：半透明白色横条（2.5dp高），营造"站牌落款"效果
- **角落光晕**：右上角径向渐变光晕，增强立体感
- **Badge标签**：半透明白底 + 白色文字，圆角胶囊样式

#### 卡片系统
- **主券式设计**：米白底色 `#faf8f5`，圆角 9dp（等价18rpx）
- **副券撕票区**：右侧暖黄底色，虚线分隔，上下打孔缺口
- **阴影层次**：轻微上浮效果（按压时缩小至0.992倍）

#### 颜色体系
- **主题蓝**：`#1e5a96`（深）→ `#2d7bc4`（浅）渐变
- **陶土橙**：`#c85a3e`（用于价格、重要按钮）
- **金色**：`#d4a84b`（次要强调）
- **警示红**：`#d9534f`（仅用于错误/危险操作）
- **中性灰**：`#f5f5f5`（轨道）、`#e0e0e0`（分割线）

#### 底部导航
- **四Tab布局**：首页、商城、包裹、我的（用户端）
- **激活态**：实心蓝色图标 + 加粗文字
- **非激活态**：灰色图标 + 常规文字
- **顶边线**：1dp深色分割线，区分内容与导航

### 1.2 当前Android实现状态

#### 已对齐部分 ✅
- `Signpost` 组件已实现站牌头（含渐变、光晕、横档）
- `TicketCard` 组件已实现撕票效果（虚线、打孔）
- `BusTheme` 定义了完整的四套主题系统
- `BusTabBar` 实现了底部导航基础结构
- `LoginScreen` 已实现左右并排的角色选择卡片

#### 待优化部分 ⚠️
1. **演示数据横幅**：页面顶部黄色警告条需移除或改为静默降级
2. **Header高度与间距**：小程序更紧凑（padding 12-16dp vs 当前可能更大）
3. **卡片内边距**：小程序使用 14dp，需统一检查
4. **空状态文案**：当前偏技术化，需调整为温暖口语化
5. **加载态交互**：缺少下拉刷新指示器与骨架屏的平滑过渡
6. **字体层级**：小程序标题 20sp/正文 14sp/辅助 12sp，需严格对照

---

## 二、实施任务清单

### 任务1：清理演示数据机制（优先级 P0）

#### 目标
移除所有硬编码的黄色警告横幅，改为静默降级策略

#### 涉及文件
- `app/src/main/java/com/shanxiang/buspost/data/repo/DemoData.kt`
- `app/src/main/java/com/shanxiang/buspost/ui/common/Bits.kt`（DemoNote组件）
- 所有调用 `DemoNote` 的页面

#### 实施方案
```kotlin
// 当前做法：页面顶部显示黄色横幅
DemoNote(vm.demo, text = "订单与地址数量来自本地演示集...")

// 改进方案：
// 1. 保留 DemoData.kt 作为离线兜底（不删除）
// 2. 移除所有页面的 DemoNote 调用
// 3. 在数据层标记 demo=true 但不展示给用户
// 4. 仅在 Logcat 输出警告日志供开发调试
```

**理由**：演示数据在网络不可用时保证界面有内容可看，但不应干扰正式用户体验。

---

### 任务2：重构Header组件（优先级 P0）

#### 目标
确保所有页面头部严格匹配小程序的站牌风格

#### 涉及文件
- `app/src/main/java/com/shanxiang/buspost/core/ui/Primitives.kt`（Signpost组件）
- `app/src/main/java/com/shanxiang/buspost/core/ui/Components.kt`（BusTopBar组件）
- `HomeScreen.kt`、`MineScreen.kt`、`BusScreen.kt` 等所有页面

#### 具体调整
```kotlin
// Primitives.kt: Signpost 组件
Box(
    modifier = modifier
        .fillMaxWidth()
        // 确保渐变颜色与小程序一致
        .background(Brush.linearGradient(listOf(Color(0xFF1e5a96), Color(0xFF2d7bc4))), shape)
        .drawBehind {
            // 角落光晕位置微调：从右上角 92% 宽度处开始
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0.12f),
                        0.7f to Color.Transparent
                    ),
                    center = Offset(size.width * 0.92f, -size.height * 0.15f),
                    radius = size.width * 0.5f,
                )
            )
        }
        // 内边距对齐小程序：横向16dp，纵向12-16dp
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
) {
    // 顶部横档：2.5dp高，半透明白色
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .padding(horizontal = 12.dp)
            .offset(y = 4.dp)  // 横档略微下沉，压在内容上方
            .fillMaxWidth()
            .height(2.5.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.28f))
    )
    // ... 内容区域
}
```

**检查点**：
- [ ] HomeScreen 的 Signpost 包含位置切换、天气胶囊
- [ ] MineScreen 无独立Header（使用普通卡片）
- [ ] BusScreen、SendScreen 等二级页面使用 BusTopBar（带返回按钮）
- [ ] 所有 Header 高度一致（测量小程序截图确认）

---

### 任务3：优化卡片与列表组件（优先级 P1）

#### 目标
统一卡片圆角、内边距、阴影，匹配小程序视觉

#### 涉及文件
- `app/src/main/java/com/shanxiang/buspost/core/ui/Components.kt`
- `app/src/main/java/com/shanxiang/buspost/core/ui/BusTheme.kt`

#### 具体调整

##### 3.1 BusCard 内边距统一
```kotlin
// Components.kt: BusCard 默认 padding 从 14.dp 改为与小程序一致
@Composable
fun BusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    warm: Boolean = false,
    border: Boolean = false,
    padding: PaddingValues = PaddingValues(14.dp),  // 保持14dp（等价18rpx）
    content: @Composable () -> Unit,
) { ... }
```

##### 3.2 车辆卡片样式对齐
```kotlin
// HomeScreen.kt: VehicleRow 组件
// 小程序特征：左侧图标托底 + 路线名加粗 + ETA右对齐
Row(
    Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(BusRadius.card))
        .background(t.card)
        .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    // 图标容器：浅色圆角背景
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(BusRadius.m))
            .background(t.primaryLight),
        contentAlignment = Alignment.Center,
    ) {
        Icon(BusIcons.bus, null, tint = t.primaryOnLight, modifier = Modifier.size(22.dp))
    }
    // 路线信息
    Column(Modifier.weight(1f).padding(start = 12.dp)) {
        Text(routeName, color = t.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(nextStation, color = t.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
    // ETA 右对齐
    Text("$eta min", color = t.clay, fontSize = 18.sp, fontWeight = FontWeight.Black)
}
```

##### 3.3 商品卡片横滑优化
```kotlin
// GoodsScreen.kt: ProductMiniCard
// 小程序特征：图片占位 + 名称两行截断 + 价格陶土橙
Column(
    Modifier
        .width(140.dp)
        .clip(RoundedCornerShape(BusRadius.card))
        .background(t.card)
        .padding(8.dp),
) {
    // 图片区域（固定宽高比）
    Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(BusRadius.m))) {
        if (product.imageUrl.isNotEmpty()) {
            AsyncImage(model = product.imageUrl, contentDescription = null, contentScale = ContentScale.Crop)
        } else {
            // 无图时显示占位图标
            Icon(BusIcons.leaf, null, tint = t.textTertiary, modifier = Modifier.size(32.dp).align(Alignment.Center))
        }
    }
    Text(product.name, color = t.ink, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    PriceText(value = product.price.toString(), size = 16, prefix = "¥")
}
```

---

### 任务4：完善空状态与加载态（优先级 P1）

#### 目标
将技术性文案改为温暖口语化，增加图标情感表达

#### 涉及文件
- `app/src/main/java/com/shanxiang/buspost/core/ui/Components.kt`（EmptyState、LoadingState）
- 所有页面的空数据处理逻辑

#### 文案对照表

| 场景 | 当前文案 | 小程序风格文案 |
|------|---------|--------------|
| 无公告 | "暂无公告" | "村里的好消息上架后会在这里展示" |
| 无公交 | "附近暂无公交线路" | "当前不在运营时间，下一班 08:30 发车" |
| 无订单 | "暂无订单" | "还没有下过单，去商城看看山货吧" |
| 无包裹 | "暂无寄货记录" | "第一次寄货？试试"我要寄货"按钮" |
| 网络错误 | "加载失败" | "网络不太顺畅，试试重新加载" |
| 无通知 | "暂无消息" | "到车提醒、订单动态会在这里通知你" |

#### 代码示例
```kotlin
// Components.kt: EmptyState 支持自定义图标背景色
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconBg: Color? = null,  // 新增：图标背景色（默认用 track）
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Column(
        modifier.fillMaxWidth().padding(vertical = 34.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(iconBg ?: t.track),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    imageVector = icon,
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(t.textTertiary),
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(title, color = t.ink, fontSize = (15 * scale).sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(
                subtitle,
                color = t.textTertiary,
                fontSize = (13 * scale).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        if (actionText != null && onAction != null) {
            BusButton(
                text = actionText,
                onClick = onAction,
                variant = BusButtonVariant.OUTLINE,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
```

---

### 任务5：增强Android专属能力（优先级 P2）

#### 目标
利用Android生态优势，提供超越小程序的体验

#### 实施清单

##### 5.1 后台消息提醒优化
- **当前实现**：WorkManager 每15分钟轮询
- **优化方向**：
  - 集成 Firebase Cloud Messaging（FCM）实现实时推送
  - 支持通知渠道分类（到车提醒、订单动态、系统公告）
  - 通知点击深链直达对应页面（已实现 `buspost://app/trace/SH...`）

##### 5.2 桌面小组件（App Widget）
```kotlin
// 新增：app/src/main/java/com/shanxiang/buspost/widget/NextBusWidget.kt
// 功能：在主屏幕显示下一班到车时间
// 更新频率：每5分钟或手动刷新
```

##### 5.3 快捷方式（App Shortcuts）
```xml
<!-- res/xml/shortcuts.xml -->
<shortcuts>
    <shortcut
        android:shortcutId="send_parcel"
        android:icon="@drawable/ic_send_shortcut"
        android:shortcutShortLabel="我要寄货">
        <intent
            android:action="android.intent.action.VIEW"
            android:targetPackage="com.shanxiang.buspost"
            android:targetClass="com.shanxiang.buspost.MainActivity" />
    </shortcut>
    <shortcut
        android:shortcutId="check_bus"
        android:icon="@drawable/ic_bus_shortcut"
        android:shortcutShortLabel="实时公交">
        <intent ... />
    </shortcut>
</shortcuts>
```

##### 5.4 离线地图缓存
- **当前实现**：在线加载高德地图瓦片
- **优化方向**：预下载县域主要道路矢量数据，弱网环境下仍可显示基础路网

---

### 任务6：联调测试与真机验证（优先级 P0）

#### 测试矩阵

| 设备型号 | Android版本 | 屏幕尺寸 | 测试重点 |
|---------|-----------|---------|---------|
| 小米13 | 14 | 6.36" 小屏 | 布局紧凑性、字体可读性 |
| OPPO Find X6 | 13 | 6.74" 大屏 | 适配老年模式（1.125x字体） |
| 华为Mate 40 | 12 | 6.5" 中屏 | GPS定位精度、通知权限 |
| 三星A54 | 14 | 6.4" 中屏 | 深色模式切换、动画流畅度 |

#### 验收标准
- [ ] 所有页面Header渐变与小程序视觉一致（取色比对）
- [ ] 卡片圆角统一为 9dp（测量工具验证）
- [ ] 下拉刷新指示器不遮挡首屏内容
- [ ] 空状态文案温暖友好，无技术术语
- [ ] 底部导航激活态图标实心填充
- [ ] 登录页角色选择卡片左右并排（非上下堆叠）
- [ ] 演示数据不再以黄色横幅展示
- [ ] 真机滑动帧率 ≥ 55fps（Profile GPU Rendering检测）

---

## 三、实施顺序建议

### 第一阶段：基础清理（1天）
1. 移除所有 `DemoNote` 调用
2. 统一 `Signpost` 渐变色值与内边距
3. 校准 `BusRadius.card = 9.dp` 全局生效

### 第二阶段：组件精修（2天）
4. 优化 `EmptyState` 文案与图标背景
5. 调整 `VehicleRow`、`ProductMiniCard` 样式
6. 统一 `BusTabBar` 激活态颜色（实心蓝）

### 第三阶段：Android增强（1天）
7. 集成 FCM 推送（可选，需后端配合）
8. 添加桌面小组件框架
9. 配置 App Shortcuts

### 第四阶段：真机测试（1天）
10. 多设备分辨率适配验证
11. 性能Profiling（内存、CPU、GPU）
12. 修复发现的视觉偏差

---

## 四、注意事项

### 4.1 不得改动的部分
- **线程模型**：UI重构不得改动现有调用点的线程包装（参考记忆 `feedback-ui-refactor-keep-threading.md`）
- **Gradle构建**：以 gradle 输出为唯一真值，IDE编辑钩子的滞后错误忽略（参考记忆 `feedback-gradle-is-truth.md`）
- **认证凭证**：严禁在代码、记忆、文件中记录任何用户提供的账号密码

### 4.2 兼容性要求
- **最小SDK**：API 21（Android 5.0）
- **目标SDK**：API 34（Android 14）
- **Compose版本**：保持当前版本，不升级以免引入breaking changes

### 4.3 性能红线
- 首屏渲染时间 ≤ 1.5s（冷启动）
- 列表滑动帧率 ≥ 55fps
- 内存占用 ≤ 150MB（典型使用场景）

---

## 五、后续迭代方向

### 短期（1-2周）
- 接入真实后端API，替换DemoData兜底逻辑
- 实现司机端车道级导航（集成高德地图SDK）
- 优化适老大字模式的对比度（WCAG AA标准）

### 中期（1个月）
- 语音下单功能（集成讯飞语音识别）
- AR实景找车（相机预览 + GPS定位叠加）
- 离线地图包下载管理

### 长期（3个月+）
- 多语言支持（英文、少数民族语言）
- 可穿戴设备适配（手表端查看到车提醒）
- 车机版Android Auto集成
