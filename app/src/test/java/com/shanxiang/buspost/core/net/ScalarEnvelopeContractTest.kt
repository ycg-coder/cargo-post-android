package com.shanxiang.buspost.core.net

import com.shanxiang.buspost.ServerTest
import com.shanxiang.buspost.TestHost
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.data.model.HandoverTask
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 芋道 `CommonResult` 的**标量信封**契约（审计 §22/§23/§52）。
 *
 * 后端这三处返回的 data 是裸值而不是对象：
 * · `AppNotificationController.java:44-48` → `CommonResult<Long>`（我的未读数）
 * · `AppDriverController.java:239-243` → `CommonResult<Long>`（司机未读数）
 * · `AppFileController.java:34-43` → `CommonResult<String>`（上传后的文件 URL）
 *
 * 病根是 `Api.toPayload` 以前只认对象/数组，裸值被塞进 `{"v": …}` 后按业务键一律读不到，
 * 表现是**不报错的静默失效**：未读角标恒 0、照片 URL 恒空、交接凭证从未入库却提示"已提交"。
 * 所以这里钉三件事：5 / 0 / 读不到 三种形态必须可区分，且"读不到"永远不许落成 0。
 */
class ScalarEnvelopeContractTest : ServerTest() {

    // ---------- 未读数（CommonResult<Long>） ----------

    @Test
    fun `裸数字未读数按标量读出，不再恒 0`() = runBlocking {
        TestHost.strictBackend()
        respondData("5")
        assertEquals(5L, Repo.unreadCount())
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertTrue(request.path!!, request.path!!.endsWith("/transport/notification/unread-count"))
    }

    @Test
    fun `后端回 0 是真 0，与读不到分得开`() = runBlocking {
        TestHost.strictBackend()
        respondData("0")
        val unread = Repo.unreadCount()
        assertEquals(0L, unread)
        assertNotNull("0 必须是读到的值，不能退化成 null", unread)
    }

    @Test
    fun `data 为 null 是读不到而不是 0`() = runBlocking {
        TestHost.strictBackend()
        respond("""{"code":0,"msg":"","data":null}""")
        assertNull(Repo.unreadCount())
    }

    @Test
    fun `网关故障按读不到处理，不抛给调用方`() = runBlocking {
        TestHost.strictBackend()
        respond("<html>bad gateway</html>", 502, "text/html")
        assertNull(Repo.unreadCount())
    }

    /** 演示构建的 DemoData 回的是 `{"count":3,"unreadCount":3}` 对象形态，兼容读法必须继续有效 */
    @Test
    fun `对象形态的 count 仍然兼容`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"count":3,"unreadCount":3}""")
        assertEquals(3L, Repo.unreadCount())
    }

    /** 后端给的不是数（脏数据/换了字段）时只能算"读不到"，绝不能算成 0 条未读 */
    @Test
    fun `非数字标量按读不到处理`() = runBlocking {
        TestHost.strictBackend()
        respondData("\"很多条\"")
        assertNull(Repo.unreadCount())
    }

    // ---------- 角标合并（审计 §52：三个写入源，恒 0 的那个会把真值抹掉） ----------

    @Test
    fun `角标只有单一写入口且按 max 合并`() {
        // 标量是真值来源（全量），列表计数只数得出本页，取大者
        AppState.reportUserUnread(serverCount = 5L, listCount = 3)
        assertEquals(5, AppState.unread.value)
        // 标量读不到时退回列表计数
        AppState.reportUserUnread(serverCount = null, listCount = 3)
        assertEquals(3, AppState.unread.value)
        // 两者都读不到 → 保持原值，绝不写 0 把红点抹掉
        AppState.reportUserUnread(serverCount = null, listCount = null)
        assertEquals(3, AppState.unread.value)
        // 后端权威 0 要能落下来（否则读完消息红点永远不掉）
        AppState.reportUserUnread(serverCount = 0L, listCount = null)
        assertEquals(0, AppState.unread.value)
    }

    @Test
    fun `司机角标与用户角标互不串域`() {
        AppState.reportUserUnread(serverCount = 7L, listCount = null)
        AppState.reportDriverUnread(serverCount = 2L, listCount = null)
        assertEquals(7, AppState.unread.value)
        assertEquals(2, AppState.driverUnread.value)
        AppState.reportDriverUnread(serverCount = null, listCount = null)
        assertEquals(2, AppState.driverUnread.value)
        assertEquals(7, AppState.unread.value)
    }

    // ---------- 司机未读（§23） ----------

    @Test
    fun `司机未读走标量并带上授予的 driverId`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 61L
        respondData("3")
        assertEquals(3L, Repo.driverUnreadCount())
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertTrue(request.path!!, request.path!!.contains("driverId=61"))
    }

    /** §23 的验收点：未绑定档案时请求根本不出网，返回值是"读不到"，页面显示未绑定而不是 0 */
    @Test
    fun `未绑定司机时司机未读返回 null 且不上网络`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 0L
        assertNull(Repo.driverUnreadCount())
        assertEquals(0, server.requestCount)
    }

    // ---------- 上传后的文件 URL（CommonResult<String>） ----------

    @Test
    fun `上传返回裸 URL 字符串就是 Ack 的 url`() = runBlocking {
        TestHost.strictBackend()
        respondData("\"https://cdn.example.com/9/ab.jpg\"")
        val ack = Repo.uploadPhoto(byteArrayOf(1, 2, 3), "handover-91.jpg")
        assertEquals("https://cdn.example.com/9/ab.jpg", ack.url)
        assertFalse(ack.demo)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertTrue(request.path!!, request.path!!.endsWith("/app-api/infra/file/upload"))
        assertTrue(
            "multipart 里必须带 file 部件，否则后端 uploadReqVO.getFile() 为空直接 400",
            request.headers["Content-Type"]?.startsWith("multipart/form-data") == true,
        )
    }

    @Test
    fun `上传的对象形态 url fileUrl 仍可读`() = runBlocking {
        TestHost.strictBackend()
        respondData("""{"url":"https://cdn.example.com/o.jpg"}""")
        assertEquals("https://cdn.example.com/o.jpg", Repo.uploadPhoto(byteArrayOf(1), "a.jpg").url)
    }

    /**
     * §7 交接照片闭环的第一道闸：后端没给回地址时这里抛异常，
     * 页面（`HandoverScreen.confirm`）必须因此**不提交**交接确认。
     * 原因：`HandoverServiceImpl.confirmHandover:196-197` 对已完成的交接直接 return，
     * 先把状态落成"交接完成"就再也补不进 photoUrl，凭证永久丢失。
     */
    @Test
    fun `上传缺 data 时抛出而不是空 URL 继续提交`() = runBlocking {
        TestHost.strictBackend()
        respond("""{"code":0,"msg":"","data":null}""")
        val failure = runCatching { Repo.uploadPhoto(byteArrayOf(1), "a.jpg") }.exceptionOrNull()
        assertTrue(failure is NetworkException)
    }

    // ---------- 交接单回读形态（§7 的"重新查询还能看到照片"） ----------

    /**
     * §7 的整链路（JVM 能到的最远一步）：上传拿裸 URL → 提交 confirm → 回读 handovers 仍带 photoUrl。
     * 三段分开测都成立不代表链路成立，所以这里按真实调用顺序串一遍，并钉住
     * **发出去的 body 里真的带着那个 URL**（以前 URL 恒空，后端 `HandoverServiceImpl:222` 见空就不写库）。
     */
    @Test
    fun `交接照片从上传到回读的整链路`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 61L
        respondData("\"https://cdn.example.com/h/91.jpg\"")
        val ack = Repo.uploadPhoto(byteArrayOf(1, 2, 3, 4), "handover-91.jpg")
        val uploadReq = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertTrue(uploadReq.path!!, uploadReq.path!!.endsWith("/app-api/infra/file/upload"))
        assertTrue("文件名要一起带上，后端按它推 Content-Type", bodyOf(uploadReq).contains("handover-91.jpg"))

        respondData("true")
        Repo.confirmHandover(91L, ack.url, "货已清点")
        val confirmReq = server.takeRequest(1, TimeUnit.SECONDS)
        val confirmBody = bodyOf(confirmReq!!)
        assertTrue(confirmBody, confirmBody.contains("\"photoUrl\":\"https://cdn.example.com/h/91.jpg\""))
        assertTrue(confirmBody, confirmBody.contains("\"handoverId\":91"))

        respondData("""[{"id":91,"status":4,"statusName":"交接完成","photoUrl":"https://cdn.example.com/h/91.jpg"}]""")
        val feed = Repo.driverHandovers()
        assertNull(feed.error)
        assertEquals(1, feed.items.size)
        assertEquals("https://cdn.example.com/h/91.jpg", feed.items[0].photoUrl)
        assertEquals("交接完成", feed.items[0].status)
    }

    /**
     * 后端 confirmHandover 对空 photoUrl 不写库（`HandoverServiceImpl:222-224`），
     * Android 侧 `Api.rawExecute:262` 把空串字段整个丢掉，所以报文里根本不该出现 `photoUrl:""`——
     * 这一条钉住"没拍照就别塞空凭证"，同时钉住空凭证回读出来确实是"没有照片"。
     */
    @Test
    fun `空 URL 提交后端不会留下凭证`() = runBlocking {
        TestHost.strictBackend()
        Prefs.driverId = 61L
        respondData("true")
        Repo.confirmHandover(91L, "", "没拍照")
        val body = bodyOf(server.takeRequest(1, TimeUnit.SECONDS)!!)
        assertTrue("空串字段必须整个丢掉，而不是发 photoUrl:\"\"：$body", !body.contains("photoUrl"))
        assertTrue(body, body.contains("\"handoverId\":91"))

        respondData("""[{"id":91,"status":4,"statusName":"交接完成","photoUrl":""}]""")
        assertEquals("", Repo.driverHandovers().items[0].photoUrl)
    }

    /** 后端 AppDriverHandoverRespVO 的真实形态：status 是数字、文案在 statusName、凭证在 photoUrl */
    @Test
    fun `交接单回读认 statusName 与 photoUrl，不拿数字当状态文案`() {
        val vo = JSONObject(
            """
            {"id":91,"orderNo":"TP2609280001","goodsName":"土鸡蛋 2 箱","stationName":"双河口换乘站",
             "itemCount":6,"photoUrl":"https://cdn.example.com/h/91.jpg","status":4,"statusName":"交接完成"}
            """.trimIndent(),
        )
        val task = HandoverTask.parse(vo)
        assertEquals(91L, task.id)
        assertEquals("交接完成", task.status)
        assertEquals("https://cdn.example.com/h/91.jpg", task.photoUrl)
        // 旧的 "0"/"4" 渲染法会让 isDone() 永不成立，已完成的交接一直挂在待办里
        assertTrue(task.status.contains("完成"))
    }

    /** photoUrl 为 null（后端列默认空串但字段可能不下发）时按"没有凭证"处理，不能画出假图 */
    @Test
    fun `交接单没有照片时 photoUrl 是空串`() {
        val task = HandoverTask.parse(
            JSONObject("""{"id":92,"status":1,"statusName":"前序已到达","photoUrl":null}"""),
        )
        assertEquals("", task.photoUrl)
        assertEquals("前序已到达", task.status)
    }
}
