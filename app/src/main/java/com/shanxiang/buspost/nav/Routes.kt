package com.shanxiang.buspost.nav

/** 路由表：tab 级页面在两套 Shell 内部切换，详情级页面作为独立路由压在 Shell 之上 */
object Routes {
    const val LOGIN = "login"
    const val USER_SHELL = "userShell"
    const val DRIVER_SHELL = "driverShell"

    const val GOODS_DETAIL = "goods/{id}"
    const val TRACE = "trace/{no}"
    const val ORDER_DETAIL = "order/{id}"
    const val BUS_DETAIL = "bus/{routeId}"
    const val ADDRESS_EDIT = "addressEdit/{id}"
    const val GOODS_TRACE = "goodsTrace/{id}"
    const val SEND = "send"
    const val BUS = "bus"
    const val ORDERS = "orders"
    /** 「快递」页在 tab 内，通过 AppState.parcelIntent 直达内层 tab */
    const val PARCEL = "parcel"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val ADDRESS = "address"
    /** 手动地址查公交（美团式）：输入地址 → 正向地理编码 → 查看该地公交/下单/寄货 */
    const val ADDRESS_SEARCH = "addressSearch"
    /** 寄件页选收货地址：地址簿选择模式 */
    const val ADDRESS_SELECT = "addressSelect"
    /** 寄货页地图选点：在地图上点击选择取货/送货位置 */
    const val MAP_POINT_PICKER = "mapPointPicker"
    /** 寄货页地址搜索：输入地址后匹配就近站点 */
    const val SEND_ADDRESS_PICKER = "sendAddressPicker"
    /** 乘车码：当地公交乘车二维码 */
    const val BUS_QR = "busQr"
    /** 钱包：余额、充值、交易记录 */
    const val WALLET = "wallet"
    const val FEEDBACK = "feedback"
    const val SETTINGS = "settings"
    /** 设置 → 关于 →「地图与开源许可」：OSM 署名与随包分发的第三方组件清单（指令 v2.1 ⑭） */
    const val OSS_LICENSES = "ossLicenses"
    const val DRIVER_NAV = "driverNav"
    /**
     * 车道级导航页（正式，非调试）。
     *
     * 与 [DRIVER_NAV]（旧调试屏，只在 DEBUG 注册）的分别：这一页驱动的是**真实高德导航视图**
     * （`AMapNaviView`：转向大图 / 车道放大图 / 路口放大图 / 实时路况），不是自绘折线。
     * **故意不带路径参数** —— 与 [DRIVER_EXCEPTION] 同一条理由：`deepLinkTarget` 的分域闸是精确集合成员判断，
     * 路由带上查询串就落在集合之外、等于闸门失效。目的地走 `AppState.openGuide(...)` 的意图传递。
     */
    const val DRIVER_GUIDE = "driverGuide"
    const val DRIVER_MESSAGES = "driverMessages"
    const val HANDOVER = "handover"
    /**
     * 司机异常上报页（指令 §54）。**故意不带路径参数**：`deepLinkTarget` 的分域闸是精确集合成员判断，
     * 路由一旦写成 `driverException/{legId}`，带参数的深链串就落在集合之外、绕过闸门；
     * 要指定哪一段走 `AppState.openException(legId)`（与 [PARCEL] 的进入意图同一手法）。
     */
    const val DRIVER_EXCEPTION = "driverException"

    /**
     * 司机域路由：只有司机会话才允许进入（分域闸见 `deepLinkTarget`，审计 §29）。
     * 底部 Intent 深链可能带着上一个角色时期留下的路由，用户会话不该被它送进司机工作台。
     */
    val DRIVER_ONLY = setOf(DRIVER_NAV, DRIVER_GUIDE, DRIVER_MESSAGES, HANDOVER, DRIVER_EXCEPTION)

    /**
     * 深链前缀的唯一来源（`buspost://app/<route>`，与 Manifest 的 scheme/host 一致）。
     * 以前这个串在 `MainActivity` 的私有常量、`Notifier.push` 的 `Uri.parse` 字面量里各写一遍，
     * 拼的一方和剥的一方没有同一份口径 ⇒ 任一侧改了就是"点了通知没反应"这类查不出来的坏。
     */
    const val DEEP_LINK_PREFIX = "buspost://app"

    /** 路由 → 深链串（通知、桌面小组件共用这一份拼装） */
    fun deepLink(route: String) = "$DEEP_LINK_PREFIX/$route"

    /**
     * 深链串 → 路由，对不上前缀返回 **null**：`""`（旧写法 `substringAfter(前缀, "")` 的产物）
     * 和"这根本不是本 App 的深链"是两件事，前者会一路走到导航层去 navigate 一个空路由。
     */
    fun routeFromDeepLink(data: String?): String? {
        if (data == null || !data.startsWith("$DEEP_LINK_PREFIX/")) return null
        return data.substring(DEEP_LINK_PREFIX.length + 1).ifEmpty { null }
    }

    fun goodsDetail(id: Long) = "goods/$id"
    fun goodsTrace(id: Long) = "goodsTrace/$id"
    fun orderDetail(id: Long) = "order/$id"
    fun trace(no: String) = "trace/" + encodeSegment(no)
    fun busDetail(routeId: String) = "bus/" + encodeSegment(routeId)
    fun addressEdit(id: Long) = "addressEdit/$id"

    /**
     * 路径段百分号编码，**写成纯 Kotlin 而不是调 `android.net.Uri.encode`**：本工程的 JVM 用例把
     * android.jar 的桩函数全 stub 成默认值（`app/build.gradle.kts` 的 `unitTests.isReturnDefaultValues = true`），
     * 于是 `Uri.encode` 在用例里返回 null，"有没有编码"这件事根本不可观测 ——
     * 旧那条「用户域深链在两个会话都放行」的用例拿到的是 `trace/null`，跟自己比，永远绿。
     *
     * 编码不是显示细节：分域闸是**精确集合成员**判断（`ShellDecision` 里 `route in Routes.DRIVER_ONLY` 那一支），
     * 参数里混进一个 `/` 或 `?` 就把整条路由搬到集合外面，等于闸门对这条深链失效。
     * 保留集取 RFC 3986 unreserved（`A-Za-z0-9-_.~`）；比 `Uri.encode` 多编了 `!` `'` `(` `)` `*` 五个字符，
     * 解码后是同一个值，不影响往返。
     */
    internal fun encodeSegment(raw: String): String = buildString {
        raw.toByteArray(Charsets.UTF_8).forEach { b ->
            val ch = b.toInt() and 0xFF
            if (ch < 128 && (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code ||
                    ch in '0'.code..'9'.code || ch in RESERVED_OTHER
                )
            ) {
                append(ch.toChar())
            } else {
                append('%')
                append(HEX[ch shr 4])
                append(HEX[ch and 0xF])
            }
        }
    }

    /** RFC 3986 unreserved 里除字母数字之外的四个字符 */
    private val RESERVED_OTHER = "-_.~".map { it.code }.toSet()

    private const val HEX = "0123456789ABCDEF"
}
