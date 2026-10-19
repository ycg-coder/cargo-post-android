package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.data.model.WalletBalance
import com.shanxiang.buspost.data.model.WalletTx
import com.shanxiang.buspost.data.model.WalletTxPage
import com.shanxiang.buspost.data.repo.RemoteState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「我的钱包 / 交易记录」（#56）的行为契约。逐字段读的生产者：
 * `AppWalletController.java:32-43`（balance 四键）、`:81-103`（transactions 的 items 八键 + total）、
 * `V028__wallet_tables.sql:21-41`（type / pay_method / status 的值域与含义）、
 * `WalletServiceImpl.java:53-58`（充值那一支把渠道写成 alipay、状态写成成功）。
 *
 * 钉四类事：
 * · 金额缺席是 null，界面念「后端没给」，绝不显示 ¥0.00；
 * · 流水的 `status` 是**数字码**（0/1/2），含义只存在于建表注释；码不许被当文案念，未知码原样带出；
 * · `payMethod` 只照抄后端给的词，页面不补"已扣款/是模拟的"这类后端没说的结论；
 * · `total` 是本次返回条数（后端就是 `items.size()`），不许念成"共 N 条"。
 * 另外两个取数各自成格：余额失败不许遮住流水，流水失败不许被念成"没有流水"。
 */
class WalletRowsContractTest {

    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        pairs.forEach { (k, v) -> if (v == null) o.put(k, JSONObject.NULL) else o.put(k, v) }
        return o
    }

    private fun page(items: JSONArray, total: Int) = obj("items" to items, "total" to total)

    private fun txRow(
        no: Any? = "W20261013001",
        type: Any? = "recharge",
        amount: Any? = 50.0,
        after: Any? = 50.0,
        title: Any? = "充值",
        method: Any? = "alipay",
        status: Any? = 1,
        created: Any? = "2026-10-13 03:00:00",
    ) = obj(
        "transactionNo" to no, "type" to type, "amount" to amount, "balanceAfter" to after,
        "title" to title, "payMethod" to method, "status" to status, "createTime" to created,
    )

    private val okBalance = RemoteState(data = WalletBalance.parse(obj("balance" to 12.5)), success = true)
    private val failedBalance = RemoteState<WalletBalance>(error = "网络不给力")
    private val notYetBalance = RemoteState<WalletBalance>(loading = true)
    private val okTxs = RemoteState(
        data = WalletTxPage.parse(page(JSONArray().put(txRow()), 1)), success = true,
    )
    private val failedTxs = RemoteState<WalletTxPage>(error = "网络不给力")

    // ---------- ① 缺席是 null，不是 0 ----------

    @Test
    fun `余额四个键都按可空读，缺席不显示零元`() {
        val b = WalletBalance.parse(obj("balance" to 0.0))
        // 真零与"没给"两格各自分明：给了 0 就老实显示 ¥0.00，没给就念「后端没给」
        assertEquals("¥0.00", walletMoney(b.balance))
        assertEquals(null, b.frozenAmount)
        assertEquals("系统没给这一项", walletMoney(b.totalRecharge))
    }

    @Test
    fun `空信封的余额单独成格，不念成零元`() {
        val b = WalletBalance.parse(obj())
        assertTrue(walletBalanceAbsent(b))
        val p = walletPage(RemoteState(data = b, success = true), okTxs)
        assertEquals(WalletCell.Empty, p.balance.cell)
        assertFalse(p.balance.body.contains("¥0.00"))
        assertTrue(p.balance.body.contains("不是 0 元"))
    }

    @Test
    fun `显式 null 与缺键同结论`() {
        assertTrue(walletBalanceAbsent(WalletBalance.parse(obj("balance" to null, "frozenAmount" to null))))
        assertFalse(walletBalanceAbsent(WalletBalance.parse(obj("totalSpent" to 3.2))))
    }

    // ---------- ② 数字码不许被当文案念 ----------

    @Test
    fun `流水状态按码出文案，未知码原样带出`() {
        assertEquals("处理中", walletStatusText(0))
        assertEquals("成功", walletStatusText(1))
        assertEquals("失败", walletStatusText(2))
        assertEquals("系统没给这一项", walletStatusText(null))
        // 不许把没见过的码就近并进"失败"或"成功"：含义只存在于 V028:31 的注释里
        assertEquals("未识别的流水状态（7）", walletStatusText(7))
    }

    @Test
    fun `状态缺失时整行不许出现处理中`() {
        val rows = walletTxRows(WalletTx.parse(obj("transactionNo" to "W1", "status" to null))).toMap()
        assertEquals("系统没给这一项", rows["状态"])
        assertFalse(rows["状态"].orEmpty().contains("处理中"))
    }

    @Test
    fun `流水类型只认 V028 那四个值，空串是没给`() {
        assertEquals("充值", walletTypeText("recharge"))
        assertEquals("消费", walletTypeText("consume"))
        assertEquals("退款", walletTypeText("refund"))
        assertEquals("提现", walletTypeText("withdraw"))
        assertEquals("系统没给这一项", walletTypeText(""))
        assertEquals("未识别的流水类型（bonus）", walletTypeText("bonus"))
    }

    // ---------- ③ 渠道只照抄，页面不替后端下结论 ----------

    @Test
    fun `渠道那一格原样照抄后端给的词，不加扣款与模拟的推断`() {
        val rows = walletTxRows(WalletTx.parse(txRow(method = "alipay"))).toMap()
        assertEquals("alipay", rows["渠道（后端原词）"])
        val joined = rows.values.joinToString("|")
        assertFalse("渠道那一格不许替后端宣布扣款", joined.contains("已扣款"))
        assertFalse("也不许反过来宣布这笔是模拟的：库里没有那一列", joined.contains("模拟"))
    }

    @Test
    fun `金额与时间缺席各念没给，不塌成零与空串`() {
        val rows = walletTxRows(WalletTx.parse(obj("transactionNo" to "W2"))).toMap()
        assertEquals("系统没给这一项", rows["金额"])
        assertEquals("系统没给这一项", rows["变动后余额"])
        assertEquals("系统没给这一项", rows["时间"])
        assertEquals("系统没给这一项", rows["类型"])
    }

    @Test
    fun `epoch 毫秒的流水时间归一成可读串`() {
        // 后端把 LocalDateTime 序列化成 Epoch 毫秒（Models.kt 的 ts() 取证），裸切串会把 17590 上屏
        val rows = walletTxRows(WalletTx.parse(obj("transactionNo" to "W3", "createTime" to 1759049640000L))).toMap()
        val time = rows["时间"].orEmpty()
        assertFalse(time.contains("17590"))
        assertTrue(time.contains(":"))
    }

    // ---------- ④ total 是本次条数 ----------

    @Test
    fun `流水条数只说本次返回，不冒充全量`() {
        val scope = walletTxScopeText(20, 20)
        assertTrue(scope.contains("本次返回 20 条"))
        assertTrue(scope.contains("未查全量"))
        assertFalse(scope.startsWith("共"))
    }

    @Test
    fun `items 为空时 total 也不被念成有条数`() {
        val p = WalletTxPage.parse(page(JSONArray(), 0))
        assertEquals(0, p.items.size)
        val pageState = walletPage(okBalance, RemoteState(data = p, success = true))
        assertEquals(WalletCell.Empty, pageState.txs.cell)
        assertTrue(pageState.txs.body.contains("空列表"))
    }

    // ---------- ⑤ 两个取数各自成格 ----------

    @Test
    fun `余额失败不遮住流水，流水失败不遮住余额`() {
        val a = walletPage(failedBalance, okTxs)
        assertEquals(WalletCell.Failed, a.balance.cell)
        assertEquals(WalletCell.Shown, a.txs.cell)
        val b = walletPage(okBalance, failedTxs)
        assertEquals(WalletCell.Shown, b.balance.cell)
        assertEquals(WalletCell.Failed, b.txs.cell)
    }

    @Test
    fun `失败格说没取到并给重试，不念成没有钱`() {
        val p = walletPage(failedBalance, failedTxs)
        assertTrue(p.balance.body.contains("重试"))
        assertFalse(p.balance.body.contains("没有钱"))
        assertFalse(p.txs.body.contains("没有可显示的流水"))
    }

    @Test
    fun `还没取到结论时两格都是加载，不替后端宣布任何数`() {
        val p = walletPage(notYetBalance, RemoteState<WalletTxPage>(loading = true))
        assertEquals(WalletCell.Loading, p.balance.cell)
        assertEquals(WalletCell.Loading, p.txs.cell)
        assertEquals("", p.balance.title)
    }

    @Test
    fun `取成功且有余额时才出数字行`() {
        val p = walletPage(RemoteState(data = WalletBalance.parse(obj("balance" to 8.0)), success = true), okTxs)
        assertEquals(WalletCell.Shown, p.balance.cell)
        val rows = walletBalanceRows(p.balance.data!!).toMap()
        assertEquals("¥8.00", rows["可用余额"])
        assertEquals(4, rows.size)
    }

    // ---------- ⑥ 这一版没有写口，这件事要写在页面上 ----------

    @Test
    fun `失败文案里明写这一版没有充值与余额支付入口`() {
        val body = walletPage(failedBalance, okTxs).balance.body
        assertTrue(body.contains("充值"))
        assertTrue(body.contains("余额支付"))
    }
}
