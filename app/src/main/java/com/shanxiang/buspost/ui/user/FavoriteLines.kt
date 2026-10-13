package com.shanxiang.buspost.ui.user

/**
 * 「收藏线路」这一族的判定（页面只渲染这里的结论；分工同 #54 乘车安排、#55 票面、#56 钱包、#57 取件时间窗）。
 *
 * 契约先读了一遍，三件事决定这一格能说什么：
 * 1. **收藏是纯本机的**：`Prefs.kt:228-254` 把 id 列表存进 DataStore（逗号分隔字符串），
 *    后端**没有任何收藏接口**——`yudao-module-transport` 的 app 端点包 0 个收藏端点，
 *    `yudao-module-member` 与 transport 建表 SQL 里也没有 `收藏`/favorite 表
 *    （`ruoyi-vue-pro.sql:2667` 那条是上游自带的后台菜单名，与线路收藏无关）
 *    ⇒ 界面不许出现"已同步""换机恢复"这类话，也不许把"清数据后就没了"藏起来。
 * 2. **名字只在线路列表里**：`BusLine.parse`（`Models.kt:865-868`）的 id/name 都来自
 *    `GET /app-api/transport/bus/lines`（`Repo.kt:852-857`），本地只存了 id
 *    ⇒ 首页那一格**不显名字**（要显就得为它另取一次列表，还会撞上首页已有的取数轮次）；
 *    也不给计数（计数只在"已经知道收藏了几个"的地方说，见 `favoriteRowTitle`）。
 * 3. **"收藏了但当前列表里没有"必须是一格，不能就近少报**：`BusScreen.kt:657-659` 原写法是
 *    `vm.lines.filter { it.id in favIds }` ⇒ 列表为空（取失败）与线路下架都会让那一栏整块消失，
 *    用户看到的是"我没有收藏"，而本机明明存着 id。这是"没取到"被念成"没有"那一族病（D-079/D-080/#42 同族）。
 *
 * 入口本身不许有登录闸门：这一格读的是本机文件，登录态与它无关；
 * 首页其余三个格子 `if (guest) onLoginClick` 是因为它们要去**账号数据**（快递/地址/消息）。
 */

/** 首页那一格的文案：唯一出处，页面不许重抄（判据 FE-a 钉字面只在纯层出现一次） */
internal const val FAVORITE_ENTRY_LABEL = "收藏线路"

/** 本机存储这件事写在页面上而不是藏起来：后端没有收藏接口，所以这就是实话 */
internal const val FAVORITE_LOCAL_NOTE = "收藏只存在这台设备上，换设备或清除应用数据后会丢失"

/** 收藏行没收藏任何东西时的说明（这一格要显示，不代表"后端说没有"） */
internal const val FAVORITE_EMPTY_HINT = "还没收藏线路：在线路详情里点星标就能收藏"

/**
 * 收藏那一栏的标题：只数**本机存的 id**，不数界面显示得出来的条数——
 * 两个数不相等恰恰是 `favoriteUnresolvedText` 要说的事，标题里混起来就看不见了。
 */
internal fun favoriteRowTitle(idCount: Int): String = "收藏线路 · 本机 $idCount 条"

/**
 * 这一栏到底显不显示：id 为空就整栏不出现（没收藏不是"取失败"）；
 * id 非空但一条都解析不出来时**仍然要出现**，出现的是下面那句未命中说明，而不是静默消失。
 */
internal fun favoriteRowVisible(idCount: Int): Boolean = idCount > 0

/**
 * "收藏了 N 条、这一轮只显示出 M 条"那一格。三种情况三种说法，**不许互相顶替**：
 * - 全显示出来 ⇒ null（不许多说一句）；
 * - 线路列表这一轮**取失败** ⇒ 原因是"没拿到列表"，不能念成"线路下架了"；
 * - 列表拿到了仍有对不上的 ⇒ 才是"当前线路列表里没有"（下架/改名/换号都可能，本机分不开就不挑一个）。
 */
internal fun favoriteUnresolvedText(idCount: Int, resolvedCount: Int, linesError: String?): String? {
    val missing = idCount - resolvedCount
    if (idCount <= 0 || missing <= 0) return null
    // ⚠ 空串不是"没有失败"：`Feed.error` 拿不到原因时可能是空串，这时仍然属于"没取到"那一格，
    //   绝不许滑到"线路下架"那一格（那等于把取数失败念成后端的结论）。
    return if (linesError != null) {
        val reason = if (linesError.isBlank()) "没给出原因" else linesError
        "线路列表这一轮没取到（$reason），所以这 $missing 条收藏先不显示——不是没有收藏"
    } else {
        "其中 $missing 条收藏在当前线路列表里没有（可能已下架），本机仍记着 $idCount 条"
    }
}

/** 一条都没收藏时的那一句"怎么收藏"：只在真没收藏过时出现，**不许被拿来当"取失败"的遮羞布** */
internal fun favoriteEmptyText(idCount: Int): String? = if (idCount > 0) null else FAVORITE_EMPTY_HINT
