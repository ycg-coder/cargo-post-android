package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.data.model.WalletBalance
import com.shanxiang.buspost.data.model.WalletTx
import com.shanxiang.buspost.data.model.WalletTxPage
import com.shanxiang.buspost.data.repo.RemoteState

/**
 * 「我的钱包」那一页的全部判定（页面只渲染这里的结论；分工同 #54 乘车安排、#55 票面）。
 *
 * 这一页最容易写坏的三件事，都在纯层钉住：
 * 1. **金额缺席不许显示 ¥0.00**：后端四个余额键是 `BigDecimal`、值可空，"真零"与"没给"是两格（F-2 §五）；
 * 2. **流水的渠道词不许被补成"已扣款"**：`/recharge` 那一支把 `pay_method` 写成 `"alipay"`、状态写成功，
 *    而表里没有"来源/是否模拟"列（`V028__wallet_tables.sql:21-41`、`WalletServiceImpl.java:53-58`）
 *    ⇒ 页面照抄后端给的那个词，不加任何一句后端没说的推断（台账 D-086 把这条登记成后端缺口）；
 * 3. **`total` 不是全量条数**：后端就是 `items.size()`（`AppWalletController.java:101`），请求带 `limit`（默认 20），
 *    超过的部分没查 ⇒ 只能念"本次返回 N 条"，念"共 N 条"就是替后端算了一个它没算过的数。
 *
 * 两个取数各自成格（余额失败不遮住流水、流水失败不遮住余额），失败格都带重试；
 * 这一页**没有充值/支付按钮**，并且把这件事写在屏上（不是藏起来让用户以为坏了）。
 */

/** 与 #54 同一处出处：后端没给就念这一句，页面不兜值 */
internal fun walletMoney(v: Double?): String = v?.let { "¥" + String.format(java.util.Locale.CHINA, "%.2f", it) } ?: NOT_GIVEN

/** 码→文案照抄建表注释 `V028:31`（`0=处理中 1=成功 2=失败`）；后端没有对应 enum 类，未知码原样带出不猜 */
internal fun walletStatusText(code: Int?): String = when (code) {
    null -> "后端没给"
    0 -> "处理中"
    1 -> "成功"
    2 -> "失败"
    else -> "未识别的流水状态（$code）"
}

/** 流水类型值域照抄 `V028:25`（recharge/consume/refund/withdraw）；空串是没给，不是"消费" */
internal fun walletTypeText(type: String): String = when (type) {
    "" -> NOT_GIVEN
    "recharge" -> "充值"
    "consume" -> "消费"
    "refund" -> "退款"
    "withdraw" -> "提现"
    else -> "未识别的流水类型（$type）"
}

internal fun walletBalanceRows(b: WalletBalance): List<Pair<String, String>> = listOf(
    "可用余额" to walletMoney(b.balance),
    "冻结金额" to walletMoney(b.frozenAmount),
    "累计充值" to walletMoney(b.totalRecharge),
    "累计支出" to walletMoney(b.totalSpent),
)

/**
 * 一条流水。`渠道` 那一格**只照抄后端给的词**：后端把充值那一支的渠道写成 `alipay`，
 * 而库里没有"是否模拟"的列，所以任何"已通过支付宝扣款 / 这笔是模拟的"都是本机替后端下的结论。
 */
internal fun walletTxRows(tx: WalletTx): List<Pair<String, String>> = listOf(
    "流水号" to tx.transactionNo.ifBlank { NOT_GIVEN },
    "类型" to walletTypeText(tx.type),
    "金额" to walletMoney(tx.amount),
    "变动后余额" to walletMoney(tx.balanceAfter),
    "说明" to tx.title.ifBlank { NOT_GIVEN },
    "渠道（后端原词）" to tx.payMethod.ifBlank { NOT_GIVEN },
    "状态" to walletStatusText(tx.statusCode),
    "时间" to tx.time.ifBlank { NOT_GIVEN },
)

/** 后端 `total == items.size()`（`AppWalletController:101`）⇒ 措辞只能覆盖"本次返回"这一段 */
internal fun walletTxScopeText(count: Int, limit: Int): String =
    "本次返回 $count 条（后端按 limit=$limit 取，未查全量，不代表总共只有这些）"

enum class WalletCell { Loading, Failed, Empty, Shown }

data class WalletSection<T>(val cell: WalletCell, val title: String, val body: String, val data: T?)

data class WalletPage(val balance: WalletSection<WalletBalance>, val txs: WalletSection<WalletTxPage>)

/** 余额四格全缺席 = "后端这次没给余额数字"，与"余额是 0 元"是两件事 */
internal fun walletBalanceAbsent(b: WalletBalance): Boolean =
    b.balance == null && b.frozenAmount == null && b.totalRecharge == null && b.totalSpent == null

/**
 * 这一版为什么不开写口——这句话必须让用户看见（找不到入口时不该以为 App 坏了），
 * 且**只有一个出处**：页面渲染这个常量，判据 `W-c` 钉页面 0 命中、纯层恰好 1（与 #54 那条同一手法）。
 * 理由的三条事实依据：`AppWalletController:65-79` 无幂等键、`WalletServiceImpl:53-58` 把模拟到账写成
 * `pay_method=alipay` + 成功、`PayService:182-200` 查单不校验归属人且 status=0 时会放款（台账 D-086）。
 */
internal const val NO_WRITE_ENTRY_NOTE =
    "这一版没有充值与余额支付入口：后端的充值口不带幂等键，且模拟到账与真实到账的记录字段完全相同，" +
        "界面没法如实区分，所以先只开放查询。"

private fun failedBody(err: String) = "$err（点「重试」再看一次；这一版没有充值与余额支付入口）"

/**
 * 四格分开，且**两个取数各自一格**：
 * · `error` 非空 → Failed（这一轮没成功，数字未知，绝不念成 0 元 / 0 条）；
 * · 还没结论（`success=false` 且无错误）→ Loading；
 * · 成功但后端给的是空结构 / 空列表 → Empty（说"没给内容"，不说"没有钱/没有流水"）；
 * · 成功且有 → Shown。
 */
fun walletPage(
    balance: RemoteState<WalletBalance>,
    txs: RemoteState<WalletTxPage>,
    limit: Int = 20,
): WalletPage {
    val berr = balance.error
    val terr = txs.error
    val bal: WalletSection<WalletBalance> = when {
        berr != null -> WalletSection(WalletCell.Failed, "余额", failedBody(berr), null)
        !balance.success -> WalletSection(WalletCell.Loading, "", "", null)
        balance.data == null || walletBalanceAbsent(balance.data) ->
            WalletSection(WalletCell.Empty, "余额", "后端这次没给余额数字（不是 0 元）。", null)
        else -> WalletSection(WalletCell.Shown, "余额", "", balance.data)
    }
    val list: WalletSection<WalletTxPage> = when {
        terr != null -> WalletSection(WalletCell.Failed, "交易记录", failedBody(terr), null)
        !txs.success -> WalletSection(WalletCell.Loading, "", "", null)
        txs.data == null || txs.data.items.isEmpty() ->
            WalletSection(
                WalletCell.Empty, "交易记录",
                "本次查询成功，后端返回空列表：这个账户没有可显示的流水。" +
                    "（取不到会是「没取到」那一格，两者不是一件事。）",
                null,
            )
        else -> WalletSection(WalletCell.Shown, "交易记录", walletTxScopeText(txs.data.returnedCount, limit), txs.data)
    }
    return WalletPage(bal, list)
}
