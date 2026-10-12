package com.shanxiang.buspost.ui.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 换头像（任务 #44）的三条底线。
 *
 * 现状核实过：后端与上传通路**本来就是齐的**（`AppFileController.java:34-43` 回裸 URL、
 * `Repo.uploadPhoto` 已经在装车照片那条链路上用着、`ProfileViewModel.saveProfile` 也早已
 * "avatar 非空才带"），缺的只是页面上那个入口 ⇒ 这一格新增的全部风险都在"什么时候才许动头像"。
 *
 * ⚠ 本机库的文件存储实测是真通路（不是占位假 key）：`infra_file_config` 的 master 是 id=29
 * 本地存储（storage=10，domain `http://127.0.0.1:48080`），且 10-10 上传的那张 `probe.png`
 * 现在还能 `GET 200 / 70 字节 / PNG 魔数 89504e47`。id=35 那个 `endpoint=http://www.baidu.com`
 * 的假配置存在但**没当 master** ⇒ 换云上环境时要另核，不能拿本机结论当云上结论。
 */
class ProfileAvatarContractTest {

    @Test
    fun `只有拿到真实地址才更新头像`() {
        val (notice, next) = avatarUploadNotice(
            "http://127.0.0.1:48080/admin-api/infra/file/29/get/20261012/a.png",
            demo = false,
        )
        assertEquals("头像已更新，点保存后生效", notice)
        assertTrue("存进去的必须是后端回的真实地址：$next", next!!.contains("/infra/file/29/get/"))
    }

    @Test
    fun `上传失败必须保持原头像 不许清空`() {
        val (notice, next) = avatarUploadNotice("", demo = false)
        assertNull("失败时返回新头像 = 用一次失败的上传擦掉用户已有头像", next)
        assertTrue("要说清失败，不能静默：$notice", notice.contains("失败"))
    }

    @Test
    fun `演示回落即使带地址也不许存进资料`() {
        // DemoData 造出来的 URL 指着一个不存在的东西；存进 member_user.avatar 就是假数据落地
        val (notice, next) = avatarUploadNotice("http://demo.local/fake.png", demo = true)
        assertNull(next)
        assertTrue("演示模式要说清：$notice", notice.contains("演示模式"))
        assertTrue("不许把回落当真实上传：$notice", notice.contains("未真实上传"))
    }
}
