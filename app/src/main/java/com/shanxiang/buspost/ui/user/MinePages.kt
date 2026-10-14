package com.shanxiang.buspost.ui.user

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.BuildConfig
import com.shanxiang.buspost.core.AppState
import com.shanxiang.buspost.core.ui.BusDialogAction
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusDivider
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusSwitch
import com.shanxiang.buspost.core.ui.BusTag
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.BusTheme
import com.shanxiang.buspost.core.ui.LocalBusTouch
import com.shanxiang.buspost.core.ui.BusTone
import com.shanxiang.buspost.core.ui.busTokens
import com.shanxiang.buspost.core.ui.EmptyState
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.InfoRow
import com.shanxiang.buspost.core.ui.BusAvatar
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.ListEndNote
import com.shanxiang.buspost.core.ui.SkeletonList
import com.shanxiang.buspost.data.model.AddressItem
import com.shanxiang.buspost.data.model.AreaNode
import com.shanxiang.buspost.data.model.FeedbackItem
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.notify.NotifyWorker
import com.shanxiang.buspost.ui.common.BackTopBar
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.ScreenViewModel
import com.shanxiang.buspost.ui.common.UpdateCheckRow

// ---------- 个人资料 ----------

/**
 * 头像上传结果 → 界面口径（纯函数，JVM 用例可断言）。
 *
 * 返回 `提示文案 to 新头像URL`；**URL 为 null 表示"不许动现有头像"** —— 这一格是全部风险所在：
 * 失败时清空 avatar 会让用户下次进来看到首字头像，等于一次失败的上传擦掉了已有数据
 * （审计 §22 那一族的另一种犯法姿势）。演示模式回落的 URL 也不许当真实地址存下来。
 */
internal fun avatarUploadNotice(url: String, demo: Boolean): Pair<String, String?> = when {
    // 演示回落也非空串：那是 DemoData 编的地址，存进资料就等于让头像指向一个不存在的东西
    demo -> "演示模式：头像未真实上传" to null
    url.isNotEmpty() -> "头像已更新，点保存后生效" to url
    else -> "头像上传失败，请重试" to null
}

class ProfileViewModel : ScreenViewModel() {
    var nickname by mutableStateOf(Prefs.nickname)
    var mobile by mutableStateOf(Prefs.mobile)
    /** 0 未知 / 1 男 / 2 女（与小程序 profile.js:20 同源） */
    var sex by mutableStateOf(Prefs.sex)
    var avatar by mutableStateOf(Prefs.avatar)

    /** 头像上传中：不许再点第二次（两次上传并发会留下一个谁都不认识的 URL） */
    var uploadingAvatar by mutableStateOf(false)

    // 改密：短信验证码 scene=3（MEMBER_UPDATE_PASSWORD），不是旧密码
    var password by mutableStateOf("")
    var passwordConfirm by mutableStateOf("")
    var smsCode by mutableStateOf("")
    var countdown by mutableStateOf(0)
    var changingPwd by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var saved by mutableStateOf("")
    private var codeEndAt = 0L
    private var codeSending = false

    fun load() = work {
        val one = Repo.refreshUser(Prefs.role)
        one.value?.let {
            nickname = it.nickname
            mobile = it.mobile
            sex = it.gender
            if (it.avatar.isNotEmpty()) avatar = it.avatar
        }
        demo = one.demo
        error = one.error
    }

    /**
     * 换头像：选图 → 上传拿**真实** URL → 写进表单（点保存时才随 `/member/user/update` 落库）。
     *
     * 与装车照片那条链路同形（`SendScreen.uploadPhoto`），但故意**不在开始时清空 avatar**：
     * 那条是"新单必须重拍"，这一条是"上传失败就该保持原样"。先清空再失败 = 一次失败的上传
     * 把用户已有的头像擦掉（审计 §22 那一族的另一种犯法姿势）。
     * 后端 `AppFileController.java:34-43` 回的是裸 URL；`ack.url` 为空就是"没拿到地址"，
     * 不许提示已更新（同 `Repo.uploadPhoto` 的注释口径）。
     */
    fun uploadAvatar(context: android.content.Context, uri: android.net.Uri) {
        if (uploadingAvatar) return
        uploadingAvatar = true
        work(reset = false) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("读取图片失败，请重新选择")
                val ack = Repo.uploadPhoto(bytes, "avatar-${Prefs.userId}-${System.currentTimeMillis()}.jpg")
                val (notice, nextAvatar) = avatarUploadNotice(ack.url, ack.demo)
                saved = notice
                if (nextAvatar != null) {
                    avatar = nextAvatar
                    Prefs.avatar = nextAvatar
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                saved = "头像上传失败：${e.message ?: "未知错误"}"
            } finally {
                uploadingAvatar = false
            }
        }
    }

    fun saveProfile() {
        if (saving) return
        if (nickname.isBlank()) {
            saved = "请输入昵称"
            return
        }
        saving = true
        work(reset = false) {
            try {
                // 字段与小程序 profile.js:109-111 一致：nickname + sex，avatar 非空才带
                val form = mutableMapOf<String, Any?>("nickname" to nickname.trim(), "sex" to sex)
                if (avatar.isNotEmpty()) form["avatar"] = avatar
                Repo.updateProfile(form)
                // 写完回读：界面显示的昵称/性别必须是服务器那一份，不是本机刚敲进去那一份。
                // 回读失败时这句成功语不能说圆 —— 后端受理了但本机没证据它存成了什么。
                val one = Repo.refreshUser(Prefs.role)
                one.value?.let {
                    nickname = it.nickname
                    sex = it.gender
                    if (it.avatar.isNotEmpty()) avatar = it.avatar
                }
                saved = if (one.value == null) "已提交，但资料没回读到" else "已保存"
            } finally {
                saving = false
            }
        }
    }

    /** 发送改密验证码（scene=3）。倒计时按截止时间戳计算，退后台回前台不会偏慢。 */
    fun sendPwdCode() {
        if (countdown > 0 || codeSending) return
        if (mobile.isEmpty()) {
            saved = "未获取到手机号"
            return
        }
        codeSending = true
        work(reset = false) {
            try {
                Repo.sendSmsCode(mobile, scene = 3)
                codeEndAt = System.currentTimeMillis() + 60_000L
                countdown = 60
                saved = "验证码已发送"
            } finally {
                codeSending = false
            }
        }
    }

    /** 由页面每秒调用一次；按时间戳校准，不用递减计数 */
    fun tickCountdown() {
        if (countdown <= 0) return
        val left = ((codeEndAt - System.currentTimeMillis() + 999) / 1000).coerceAtLeast(0L).toInt()
        countdown = left
    }

    fun changePassword() {
        if (changingPwd) return
        saved = when {
            smsCode.isBlank() -> "请输入短信验证码"
            password.length < 4 || password.length > 16 -> "密码长度需为 4-16 位"
            password != passwordConfirm -> "两次密码不一致"
            else -> ""
        }
        if (saved.isNotEmpty()) return
        changingPwd = true
        work(reset = false) {
            try {
                Repo.updatePassword(password, smsCode.trim())
                password = ""
                passwordConfirm = ""
                smsCode = ""
                saving = false
                saved = "密码已修改"
            } finally {
                changingPwd = false
            }
        }
    }
}

@Composable
fun ProfileScreen(vm: ProfileViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current

    val context = LocalContext.current
    // 换头像的选图入口：系统相册选择器（与寄货拍照同一条 PickVisualMedia 通路，不申请存储权限）
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.uploadAvatar(context, uri)
    }

    LaunchedEffect(Unit) { vm.load() }
    // 倒计时按秒刷新（页面侧驱动，逻辑在 VM 内按时间戳校准）
    LaunchedEffect(vm.countdown) {
        while (vm.countdown > 0) {
            kotlinx.coroutines.delay(1000)
            vm.tickCountdown()
        }
    }

    Column {
        BackTopBar(title = "个人资料", subtitle = Prefs.mobile)
        BusPage(spacedBy = 12.dp) {
            DemoNote(vm.demo, text = "当前显示本地会话信息。")
            vm.error?.let { ErrorState(it, onRetry = { vm.load() }) }

            SectionTitle("基本资料")
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 头像：这一格以前根本没有 —— 后端与上传通路都是现成的，缺的是入口
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BusAvatar(name = vm.nickname.ifEmpty { Prefs.nickname }, url = vm.avatar, size = 52)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("头像", color = t.textSecondary, fontSize = (13 * scale).sp)
                            Text(
                                when {
                                    vm.uploadingAvatar -> "上传中…"
                                    vm.avatar.isNotEmpty() -> "已选择新头像，点保存后生效"
                                    else -> "未设置头像"
                                },
                                color = t.textTertiary,
                                fontSize = (11.5 * scale).sp,
                            )
                        }
                        BusDialogAction(
                            text = if (vm.uploadingAvatar) "上传中" else "更换头像",
                            enabled = !vm.uploadingAvatar,
                            onClick = {
                                avatarPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )
                    }
                    BusTextField(
                        value = vm.nickname,
                        onValueChange = { vm.nickname = it },
                        label = "昵称",
                        placeholder = "请输入昵称",
                        icon = BusIcons.person,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("性别", color = t.textSecondary, fontSize = (13 * scale).sp, modifier = Modifier.weight(1f))
                        // 顺序与取值对齐小程序：男(1)/女(2)/保密(0)
                        listOf(1 to "男", 2 to "女", 0 to "保密").forEach { (value, label) ->
                            val on = vm.sex == value
                            Text(
                                label,
                                color = if (on) t.primaryOnLight else t.textTertiary,
                                fontSize = (13 * scale).sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(BusRadius.pill))
                                    .background(if (on) t.primaryLight else t.card)
                                    .clickable { vm.sex = value }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                        }
                    }
                    InfoRow(label = "手机号", value = vm.mobile.ifEmpty { "未绑定" }, icon = BusIcons.phone)
                    if (vm.saved.isNotEmpty()) {
                        Text(vm.saved, color = t.primaryOnLight, fontSize = (12.5 * scale).sp)
                    }
                    BusButton(text = "保存资料", onClick = { vm.saveProfile() }, icon = BusIcons.check, loading = vm.saving)
                }
            }

            SectionTitle("修改密码")
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BusTextField(
                            value = vm.smsCode,
                            onValueChange = { vm.smsCode = it.filter { c -> c.isDigit() }.take(6) },
                            label = "验证码",
                            placeholder = "短信验证码",
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (vm.countdown > 0) "${vm.countdown}s" else "获取验证码",
                            color = if (vm.countdown > 0) t.textTertiary else t.primaryOnLight,
                            fontSize = (12.5 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(BusRadius.pill))
                                .background(if (vm.countdown > 0) t.line else t.primaryLight)
                                .clickable(enabled = vm.countdown == 0) { vm.sendPwdCode() }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    BusTextField(
                        value = vm.password,
                        onValueChange = { vm.password = it },
                        label = "新密码",
                        placeholder = "4-16 位新密码",
                        keyboardType = KeyboardType.Password,
                    )
                    BusTextField(
                        value = vm.passwordConfirm,
                        onValueChange = { vm.passwordConfirm = it },
                        label = "确认密码",
                        placeholder = "再次输入新密码",
                        keyboardType = KeyboardType.Password,
                    )
                    BusButton(
                        text = "修改密码",
                        onClick = { vm.changePassword() },
                        variant = BusButtonVariant.CLAY,
                        loading = vm.changingPwd,
                        enabled = vm.smsCode.isNotEmpty() && vm.password.isNotEmpty() && vm.passwordConfirm.isNotEmpty(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------- 收货地址 ----------

class AddressViewModel : ScreenViewModel() {
    var items by mutableStateOf<List<AddressItem>>(emptyList())

    private suspend fun readAddresses() {
        val f = Repo.addresses()
        items = f.items
        demo = f.demo
        error = f.error
    }

    fun load() = work { readAddresses() }

    fun remove(id: Long) = work(reset = false) {
        Repo.deleteAddress(id)
        readAddresses()
    }

    /** 设为默认：仅把本条目 defaultStatus 置 1，其余置 0（字段对齐小程序 address.js:202-208） */
    fun setDefault(item: AddressItem) = work(reset = false) {
        Repo.saveAddress(
            mapOf(
                "id" to item.id,
                "name" to item.name,
                "mobile" to item.mobile,
                "areaId" to item.areaId,
                "detailAddress" to item.detail,
                "defaultStatus" to 1,
            ),
        )
        readAddresses()
    }
}

/**
 * 选择模式回传：寄件页从地址簿选收货地址时，点条目回传此处。
 * 优先走 onPick（带字段回填并自行 pop），回落到 selected + 由寄件页 pop。
 */
object AddressPicker {
    var selected: AddressItem? = null
    var onPick: ((AddressItem) -> Unit)? = null
}

@Composable
fun AddressScreen(vm: AddressViewModel = viewModel(), select: Boolean = false) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    var toDelete by remember { mutableStateOf<AddressItem?>(null) }

    // 每次进这一页都回读：从"编辑地址"退回来时列表已有旧数据，只在空时才取数
    // 等于把"刚存的那条"挡在屏幕外。
    LaunchedEffect(Unit) { vm.load() }

    Column {
        BackTopBar(title = "收货地址", subtitle = "${vm.items.size} 个")
        BusPage(spacedBy = 12.dp) {
            DemoNote(vm.demo)
            vm.error?.let { ErrorState(it, onRetry = { vm.load() }) }
            if (vm.loading && vm.items.isEmpty()) {
                SkeletonList(2)
            } else if (vm.items.isEmpty()) {
                EmptyState("还没有收货地址", subtitle = "下次下单时，系统会帮你记住新地址", icon = BusIcons.location)
            } else {
                vm.items.forEach { a ->
                    BusCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = if (select) {
                            {
                                AddressPicker.onPick?.invoke(a)
                                    ?: run { AddressPicker.selected = a; nav.popBackStack() }
                            }
                        } else null,
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(a.name, color = t.ink, fontSize = (15 * scale).sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        " ${a.mobile}",
                                        color = t.textSecondary,
                                        fontSize = (13 * scale).sp,
                                    )
                                    if (a.isDefault) {
                                        Spacer(Modifier.size(8.dp))
                                        BusTag("默认", tone = BusTone.PRIMARY)
                                    }
                                }
                                Text(
                                    a.regionLabel,
                                    color = t.textSecondary,
                                    fontSize = (12.5 * scale).sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                if (!select && !a.isDefault) {
                                    Text(
                                        "设为默认",
                                        color = t.primaryOnLight,
                                        fontSize = (12.5 * scale).sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .clip(RoundedCornerShape(BusRadius.pill))
                                            .background(t.primaryLight)
                                            .clickable { vm.setDefault(a) }
                                            .padding(horizontal = 12.dp, vertical = 5.dp),
                                    )
                                }
                            }
                            if (!select) {
                                Icon(
                                    BusIcons.edit, null, tint = t.textTertiary,
                                    modifier = Modifier.size(19.dp).clickable { nav.navigate(Routes.addressEdit(a.id)) },
                                )
                                Spacer(Modifier.size(12.dp))
                                Icon(
                                    BusIcons.delete, null, tint = t.danger,
                                    modifier = Modifier.size(19.dp).clickable { toDelete = a },
                                )
                            }
                        }
                    }
                }
            }
            BusButton(text = "新增收货地址", onClick = { nav.navigate(Routes.addressEdit(0L)) }, icon = BusIcons.add)
            Spacer(Modifier.height(8.dp))
        }
    }

    toDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("删除地址") },
            text = { Text("确定删除「${a.name} ${a.detail}」吗？") },
            confirmButton = {
                BusDialogAction(
                    "删除",
                    onClick = {
                        toDelete = null
                        vm.remove(a.id)
                    }
                )
            },
            dismissButton = { BusDialogAction("取消", onClick = { toDelete = null }) },
        )
    }
}

class AddressEditViewModel : ScreenViewModel() {
    var id by mutableStateOf(0L)
    var name by mutableStateOf("")
    var mobile by mutableStateOf("")
    /** 地区 ID（后端入库必需，小程序写路径用 areaId） */
    var areaId by mutableStateOf("")
    /** 省 市 区 文本（如「云南省 大理州 祥云县」），仅展示与回显 */
    var regionText by mutableStateOf("")
    var detail by mutableStateOf("")
    var isDefault by mutableStateOf(false)
    var saved by mutableStateOf(false)
    var formError by mutableStateOf("")
    var areaTree by mutableStateOf<List<AreaNode>>(emptyList())
    var areaLoading by mutableStateOf(false)

    fun load(addressId: Long) {
        id = addressId
        work {
            val f = Repo.addresses()
            val a = f.items.firstOrNull { it.id == addressId }
            if (a != null) {
                name = a.name
                mobile = a.mobile
                areaId = a.areaId
                regionText = a.regionLabel
                detail = a.detail
                isDefault = a.isDefault
            } else if (addressId == 0L) {
                // 首个地址默认勾选「设为默认」
                isDefault = f.items.isEmpty()
            }
            val tree = Repo.areaTree()
            areaTree = tree.items
            areaLoading = tree.items.isEmpty() && tree.error != null
            demo = f.demo
            error = f.error ?: tree.error
        }
    }

    fun save() {
        formError = when {
            name.isBlank() -> "请填写收货人"
            mobile.length < 7 -> "请填写正确的手机号"
            areaId.isBlank() -> "请选择所在地区"
            detail.isBlank() -> "请填写详细地址"
            else -> ""
        }
        if (formError.isNotEmpty()) return
        work(reset = false) {
            // 字段与小程序 address.js:202-208 逐字一致（areaId / detailAddress / defaultStatus）
            val form = mutableMapOf<String, Any?>(
                "name" to name,
                "mobile" to mobile,
                "areaId" to areaId,
                "detailAddress" to detail,
                "defaultStatus" to if (isDefault) 1 else 0,
            )
            if (id > 0) form["id"] = id
            Repo.saveAddress(form)
            saved = true
        }
    }
}

@Composable
fun AddressEditScreen(id: Long, vm: AddressEditViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    var showArea by remember { mutableStateOf(false) }

    LaunchedEffect(id) { vm.load(id) }

    Column {
        BackTopBar(title = if (id == 0L) "新增地址" else "编辑地址")
        BusPage(spacedBy = 12.dp) {
            DemoNote(vm.demo)
            vm.error?.let { ErrorState(it, onRetry = { vm.load(id) }) }
            if (vm.saved) {
                Text("已保存", color = t.primaryOnLight, fontSize = (12.5 * scale).sp)
            }
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BusTextField(value = vm.name, onValueChange = { vm.name = it }, label = "收货人", icon = BusIcons.person, placeholder = "姓名")
                    BusTextField(
                        value = vm.mobile,
                        onValueChange = { vm.mobile = it.filter { c -> c.isDigit() } },
                        label = "手机号",
                        icon = BusIcons.phone,
                        placeholder = "11 位手机号",
                        keyboardType = KeyboardType.Phone,
                    )
                    // 省市区三级级联选择器（替代自由文本，字段对齐小程序 areaId）
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(BusRadius.m))
                            .background(t.field)
                            .clickable(enabled = vm.areaTree.isNotEmpty()) { showArea = true }
                            .padding(horizontal = 12.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(BusIcons.location, null, tint = t.textTertiary, modifier = Modifier.size(18.dp).padding(end = 8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("所在地区", color = t.textSecondary, fontSize = (12 * scale).sp)
                            Text(
                                if (vm.regionText.isNotEmpty()) vm.regionText else if (vm.areaTree.isEmpty()) "地区加载中…" else "请选择省 / 市 / 区",
                                color = if (vm.regionText.isNotEmpty()) t.ink else t.textTertiary,
                                fontSize = (15 * scale).sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                    BusTextField(
                        value = vm.detail,
                        onValueChange = { vm.detail = it },
                        label = "详细地址",
                        placeholder = "村组门牌，或写「村口小卖部」",
                        singleLine = false,
                        minLines = 2,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("设为默认地址", color = t.ink, fontSize = (14 * scale).sp, modifier = Modifier.weight(1f))
                        BusSwitch(checked = vm.isDefault, onCheckedChange = { vm.isDefault = it })
                    }
                }
            }
            if (vm.formError.isNotEmpty()) {
                Text(vm.formError, color = t.danger, fontSize = (12.5 * scale).sp)
            }
            BusButton(text = "保存地址", onClick = { vm.save() }, icon = BusIcons.check)
            Spacer(Modifier.height(6.dp))
        }
    }

    if (showArea && vm.areaTree.isNotEmpty()) {
        AreaPickerDialog(
            tree = vm.areaTree,
            initialAreaId = vm.areaId,
            onConfirm = { id, text ->
                vm.areaId = id
                vm.regionText = text
                showArea = false
            },
            onDismiss = { showArea = false },
        )
    }
}

/**
 * 省市区三级级联选择器：等价小程序 address.js 的 picker multiSelector（省/市/区三列联动）。
 * 选定后回传 areaId 与「省 市 区」文本。
 */
@Composable
private fun AreaPickerDialog(
    tree: List<AreaNode>,
    initialAreaId: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val (pIdx, cIdx, dIdx) = resolveAreaIndexes(tree, initialAreaId)
    var provinceIdx by remember { mutableStateOf(pIdx) }
    var cityIdx by remember { mutableStateOf(cIdx) }
    var districtIdx by remember { mutableStateOf(dIdx) }

    val provinces = tree
    val cities = provinces.getOrNull(provinceIdx)?.children ?: emptyList()
    val districts = cities.getOrNull(cityIdx)?.children ?: emptyList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择所在地区") },
        text = {
            Row(Modifier.height(300.dp)) {
                AreaColumn(provinces.map { it.name }, provinceIdx) {
                    provinceIdx = it; cityIdx = 0; districtIdx = 0
                }
                AreaColumn(cities.map { it.name }, cityIdx) {
                    cityIdx = it; districtIdx = 0
                }
                AreaColumn(districts.map { it.name }, districtIdx) { districtIdx = it }
            }
        },
        confirmButton = {
            BusDialogAction(
                "确定",
                onClick = {
                    val province = provinces.getOrNull(provinceIdx)
                    val city = province?.children?.getOrNull(cityIdx)
                    val district = city?.children?.getOrNull(districtIdx)
                    val id = district?.id ?: city?.id ?: province?.id ?: ""
                    val text = listOfNotNull(province?.name, city?.name, district?.name).joinToString(" ")
                    onConfirm(id, text)
                }
            )
        },
        dismissButton = { BusDialogAction("取消", onClick = onDismiss) },
    )
}

@Composable
private fun RowScope.AreaColumn(items: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    LazyColumn(
        Modifier.width(108.dp).fillMaxHeight(),
    ) {
        items(items.size) { i ->
            val on = i == selected
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(BusRadius.s))
                    .background(if (on) t.primaryLight else t.card)
                    .clickable { onPick(i) }
                    .padding(vertical = 11.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    items[i],
                    color = if (on) t.primaryOnLight else t.textSecondary,
                    fontSize = (13.5 * scale).sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** 由 areaId 反推「省/市/区」在树中的索引（编辑预填用），找不到回落 [0,0,0] */
private fun resolveAreaIndexes(tree: List<AreaNode>, areaId: String): Triple<Int, Int, Int> {
    if (areaId.isEmpty()) return Triple(0, 0, 0)
    tree.forEachIndexed { pi, p ->
        p.children.forEachIndexed { ci, c ->
            c.children.forEachIndexed { di, d ->
                if (d.id == areaId) return Triple(pi, ci, di)
            }
            if (c.id == areaId) return Triple(pi, ci, 0)
        }
        if (p.id == areaId) return Triple(pi, 0, 0)
    }
    return Triple(0, 0, 0)
}

// ---------- 意见反馈 ----------

class FeedbackViewModel : ScreenViewModel() {
    var items by mutableStateOf<List<FeedbackItem>>(emptyList())
    var content by mutableStateOf("")
    var name by mutableStateOf(Prefs.nickname)
    var mobile by mutableStateOf(Prefs.mobile)
    var sent by mutableStateOf(false)
    var formError by mutableStateOf("")
    var page by mutableStateOf(1)
    var hasMore by mutableStateOf(true)

    fun load(reset: Boolean = true) = work {
        if (reset) page = 1
        val f = Repo.feedbackPage(page, 20)
        items = if (reset) f.items else items + f.items
        hasMore = f.items.size >= 20
        demo = f.demo
        error = f.error
    }

    fun loadMore() {
        if (!hasMore || loading) return
        page += 1
        load(reset = false)
    }

    fun submit() {
        formError = if (content.length < 5) "再多写几个字，站点才好调整" else ""
        if (formError.isNotEmpty()) return
        work(reset = false) {
            Repo.submitFeedback(content, name, mobile)
            content = ""
            sent = true
            load(reset = true)
        }
    }
}

@Composable
fun FeedbackScreen(vm: FeedbackViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current

    LaunchedEffect(Unit) { if (vm.items.isEmpty()) vm.load() }

    Column {
        BackTopBar(title = "意见反馈", subtitle = "缺什么、想寄什么，说给站点听")
        BusPage(spacedBy = 12.dp) {
            DemoNote(vm.demo)
            vm.error?.let { ErrorState(it, onRetry = { vm.load() }) }
            if (vm.sent) {
                BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                    Text("反馈已提交，站点会在班次调整时统一回复。", color = t.ink, fontSize = (13.5 * scale).sp)
                }
            }

            SectionTitle("写反馈")
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BusTextField(
                        value = vm.content,
                        onValueChange = { vm.content = it },
                        placeholder = "比如：希望每周三加一班到县城，鸡蛋太多装不下",
                        singleLine = false,
                        minLines = 4,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BusTextField(value = vm.name, onValueChange = { vm.name = it }, label = "称呼", modifier = Modifier.weight(1f))
                        BusTextField(
                            value = vm.mobile,
                            onValueChange = { vm.mobile = it.filter { c -> c.isDigit() } },
                            label = "电话",
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Phone,
                        )
                    }
                    if (vm.formError.isNotEmpty()) {
                        Text(vm.formError, color = t.danger, fontSize = (12 * scale).sp)
                    }
                    BusButton(text = "提交反馈", onClick = { vm.submit() }, icon = BusIcons.send)
                }
            }

            SectionTitle("历史反馈", subtitle = "${vm.items.size} 条")
            if (vm.loading && vm.items.isEmpty()) {
                SkeletonList(2)
            } else if (vm.items.isEmpty()) {
                EmptyState("还没有提过反馈", subtitle = "站点缺什么、服务怎么样，告诉我们", icon = BusIcons.edit)
            } else {
                vm.items.forEach { f ->
                    BusCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(f.content, color = t.ink, fontSize = (13.5 * scale).sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (f.reply.isNotEmpty()) {
                                // 平台回复：左侧竖条块样式（对齐小程序 .reply-block）
                                Spacer(Modifier.height(9.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    Box(Modifier.width(3.dp).fillMaxHeight().background(t.clayDeep))
                                    Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("平台回复", color = t.clayDeep, fontSize = (12 * scale).sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            f.reply,
                                            color = t.textSecondary,
                                            fontSize = (12.5 * scale).sp,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 3.dp),
                                        )
                                    }
                                }
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                BusTag(
                                    f.status.ifEmpty { "待回复" },
                                    tone = if (f.reply.isNotEmpty()) BusTone.OK else BusTone.WARN,
                                )
                                Text(f.time, color = t.textTertiary, fontSize = (11 * scale).sp)
                            }
                        }
                    }
                }
                if (vm.hasMore) {
                    BusButton(
                        text = "加载更多",
                        onClick = { vm.loadMore() },
                        variant = BusButtonVariant.OUTLINE,
                        fullWidth = false,
                        small = true,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    ListEndNote()
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------- 设置 ----------

@Composable
fun SettingsScreen() {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val context = LocalContext.current
    val nav = LocalNavController.current
    val theme by AppState.theme.collectAsStateWithLifecycle()
    val dark by AppState.dark.collectAsStateWithLifecycle()
    val elderly by AppState.elderly.collectAsStateWithLifecycle()
    var notify by remember { mutableStateOf(Prefs.notifyEnabled) }
    var arrival by remember { mutableStateOf(Prefs.arrivalAlertEnabled) }
    var weatherAlert by remember { mutableStateOf(Prefs.weatherAlertEnabled) }
    var voice by remember { mutableStateOf(Prefs.voiceGuidance) }
    var lane by remember { mutableStateOf(Prefs.laneGuidance) }
    var approachVoice by remember { mutableStateOf(Prefs.approachVoice) }

    Column {
        BackTopBar(title = "设置", subtitle = "主题、提醒与导航偏好")
        BusPage(spacedBy = 12.dp) {
            SectionTitle("站牌配色")
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        BusTheme.entries.forEach { th ->
                            val tok = busTokens(th, dark)
                            val on = th == theme
                            Box(
                                Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = LocalBusTouch.current.min)
                                    .clip(RoundedCornerShape(BusRadius.m))
                                    .background(if (on) tok.primaryDark else tok.card)
                                    .clickable { AppState.setTheme(th) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    th.label,
                                    color = if (on) tok.onPrimary else tok.textSecondary,
                                    fontSize = (12.5 * scale).sp,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    BusDivider(Modifier.padding(vertical = 10.dp))
                    SettingRow("深色站牌", "夜间候车不刺眼", dark) {
                        AppState.setDark(it)
                    }
                    BusDivider()
                    SettingRow("适老大字", "字号放大 22%，按钮更高", elderly) {
                        AppState.setElderly(it)
                        Prefs.elderlyMode = it
                    }
                }
            }

            SectionTitle("提醒")
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingRow("后台消息提醒", "每 15 分钟检查一次订单与班次", notify) {
                        notify = it
                        Prefs.notifyEnabled = it
                        if (it) NotifyWorker.runNow(context)
                    }
                    BusDivider()
                    SettingRow("到车提醒", "车到前 10 分钟通知栏提醒", arrival) {
                        arrival = it
                        Prefs.arrivalAlertEnabled = it
                    }
                    BusDivider()
                    SettingRow("天气预警", "雨雪、大雾、高温等恶劣天气推送提醒", weatherAlert) {
                        weatherAlert = it
                        Prefs.weatherAlertEnabled = it
                    }
                    BusDivider()
                    InfoRow(
                        label = "系统通知权限",
                        value = "去开启",
                        icon = BusIcons.settings,
                        showNext = true,
                        onClick = {
                            val i = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            runCatching { context.startActivity(i) }
                        },
                    )
                }
            }

            SectionTitle("司机导航偏好（司机端生效）")
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingRow("语音播报", "高德导航自带的转弯口令", voice) {
                        voice = it
                        Prefs.voiceGuidance = it
                    }
                    BusDivider()
                    SettingRow("车道级指引", "显示推荐车道，山路更稳", lane) {
                        lane = it
                        Prefs.laneGuidance = it
                    }
                    BusDivider()
                    // 与上面那条分开存：上面那格交给 SDK 的 setUseInnerVoice，这一格是本机的到近作业话术，
                    // 两个引擎同时出声是本机已知的取舍，不靠一句话把它们混成"语音播报"。
                    SettingRow("到近作业播报", "快到任务点时报该干什么，不参与放行", approachVoice) {
                        approachVoice = it
                        Prefs.approachVoice = it
                    }
                }
            }

            SectionTitle("关于")
            BusCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    InfoRow(label = "版本", value = BuildConfig.VERSION_NAME, icon = BusIcons.info)
                    // #48：检查更新走 GitHub Releases（用户 2026-10-12 定的通路），判定与文案都在纯函数里
                    UpdateCheckRow()
                    InfoRow(
                        label = "隐私政策",
                        value = "本应用实际收集什么、存在哪",
                        icon = BusIcons.info,
                        showNext = true,
                        onClick = { nav.navigate(Routes.PRIVACY) },
                    )
                    InfoRow(
                        label = "用户协议",
                        value = "提供什么服务、不提供什么服务",
                        icon = BusIcons.info,
                        showNext = true,
                        onClick = { nav.navigate(Routes.TERMS) },
                    )
                    InfoRow(
                        label = "许可信息",
                        value = "查看相关许可",
                        icon = BusIcons.info,
                        showNext = true,
                        onClick = { nav.navigate(Routes.OSS_LICENSES) },
                    )
                    InfoRow(
                        label = "当前模式",
                        value = if (Prefs.token.startsWith("demo-")) "体验模式" else "正常模式",
                        icon = BusIcons.info,
                        valueColor = if (Prefs.token.startsWith("demo-")) t.clayDeep else t.primaryOnLight,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = t.textTertiary, fontSize = (11.5 * scale).sp, modifier = Modifier.padding(top = 2.dp))
        }
        BusSwitch(checked = checked, onCheckedChange = onChange)
    }
}
