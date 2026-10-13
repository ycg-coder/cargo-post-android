# 启动图标候选（#43，2026-10-13）——**已选方向 2 并落地**（D-091）

2026-10-13 用户回话"图标选2"。落地结果与仍然缺的东西都在下面，三张概念稿保留作选型依据。

- 已换：`res/drawable/ic_launcher_foreground.xml`（车头 + 两个镂空车灯 + 弧线 + 站点圆点，满幅 108dp、整组按 0.86 缩进安全圆）
  / `res/drawable/ic_launcher_background.xml`（`#0E5A52 → #17A07A` 线性渐变）/ `mipmap-anydpi-v26/ic_launcher.xml` 三层接线；
  退役的 `bus_launcher_bg` 已从 `colors.xml` 删除。
- 结构判据：`tools/check_launcher_icon_probes.sh`（IC-a…IC-g）+ 负控 `tools/launcher_icon_negative_controls.sh`（16 条破口）。
- **仍然缺的两件**：① 商店 1024×1024 位图（本机没有矢量光栅化工具，**不该拿再生成的概念稿冒充导出图**）；
  ② 桌面真机效果未验（`NOT VERIFIED ON DEVICE`）。
- **刻意没做**：各密度位图与 `ic_launcher_round`（`minSdk = 26`，API 26+ 只认 adaptive icon）；
  `assets/logo.png`（启动页 + 登录页用的**用户原图**）也**不重绘** —— 它现在与新图标不同源，
  要不要一起换是用户的决定。

---

## 以下为选型时的原文（保留）

三张图都是**概念稿**，不是可直接出厂的资源。共同问题：右下角带"Qoder AI 生成"水印、
画面被烤成了圆角方块（自适应图标要的是**满幅 108dp 画布**，圆角与遮罩由系统裁）。
选定方向后的落地清单在本文末尾。

| 编号 | 文件 | 方向 | 我看到的问题（小尺寸可读性优先） |
|---|---|---|---|
| 1 | `candidate-1-busstop-parcel.png` | 延续现有品牌：深蓝底 + 白色站牌剪影，右下加一只开口货箱（金色胶带点缀） | 与当前 `ic_launcher_foreground.xml` 同源（站牌），**认得出是同一个 App 的演进**；站牌板是空心的，48dp 以下那块白容易糊成一团；"客/货/邮"里只表达到"货" |
| 2 | `candidate-2-bus-route-arc.png` | 换色系：青绿渐变底 + 白色车头正面 + 一条弧线路线与站点圆点 | 三者里**最干净、小尺寸最清楚**，车头两个圆灯在 48dp 下仍分得开；代价是**完全没有货/邮的语义**，且渐变底与 App 现在的深蓝主色板不搭（`bus_launcher_bg #123F6E`） |
| 3 | `candidate-3-monoline-glyph.png` | 单线符号：一条线把左侧车头顶与右侧信封折成同一个字形，珊瑚色点作折点 | 最有辨识度、最适合做 themed icon 的单色层；但**左半边读不出"车"**（更像"邮件"），米白底在深色桌面上一圈发灰，需要描边或换底 |

## 我的建议（供参考，决定权在你）

要"像同一个产品在演进"选 **1**；要"第一眼最清爽"选 **2**（但得接受它只讲客运）；
要"以后 themed icon / 单色层独立成立"选 **3**，同时把左半边的车头形状再明确一次。
也可以 1 的构图 + 2 的干净度：即保留站牌+货箱，但把空心站牌板改成实心留白。

## 选定之后的落地清单（一次做完，不分两趟）

1. 前景层：按 108dp 满幅重做 `res/drawable/ic_launcher_foreground.xml`（矢量，主体留在中间 66dp 安全区）；
2. 背景层：沿用 `@color/bus_launcher_bg`（选 2 就要一起改这个色值，否则桌面上一圈还是深蓝）；
3. **monochrome 层单独画**：现在 `ic_launcher.xml` 的 `<monochrome>` 直接复用前景（`PRODUCTIZATION_COMPLETE.md` §6.1 已登记），
   themed icon 在 Android 13 上会变成"实心一坨"，要一条独立的双色安全线稿；
4. 一张 1024×1024 无透明位图（商店/对外素材用；**不是**给 mipmap 用的——`minSdk = 26`，
   API 26+ 只认 adaptive icon，各密度位图在本项目里不需要，`PRODUCTIZATION_COMPLETE.md:397-404` 已把这条改对）；
5. **收敛第二套标志**：`app/src/main/assets/logo.png`（启动页 + 登录页共用，87,912 B）与启动图标不同源，
   要么由同一份矢量导出，要么改成引用同一资源，否则会出现"桌面新图标、开屏旧 logo"。
