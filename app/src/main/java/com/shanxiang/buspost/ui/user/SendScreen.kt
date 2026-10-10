package com.shanxiang.buspost.ui.user

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shanxiang.buspost.core.ui.NetworkImage
import com.shanxiang.buspost.core.loc.Locator
import com.shanxiang.buspost.core.net.Api
import com.shanxiang.buspost.core.ui.BusButton
import com.shanxiang.buspost.core.ui.BusCard
import com.shanxiang.buspost.core.ui.BusButtonVariant
import com.shanxiang.buspost.core.ui.BusIcons
import com.shanxiang.buspost.core.ui.BusPage
import com.shanxiang.buspost.core.ui.BusRadius
import com.shanxiang.buspost.core.ui.BusTextField
import com.shanxiang.buspost.core.ui.BusSwitch
import com.shanxiang.buspost.core.ui.ErrorState
import com.shanxiang.buspost.core.ui.InfoRow
import com.shanxiang.buspost.core.ui.LocalBusFontScale
import com.shanxiang.buspost.core.ui.LocalBusTokens
import com.shanxiang.buspost.core.ui.PriceText
import com.shanxiang.buspost.core.ui.QrView
import com.shanxiang.buspost.core.ui.RoadLine
import com.shanxiang.buspost.core.ui.SectionTitle
import com.shanxiang.buspost.core.ui.Signpost
import com.shanxiang.buspost.core.ui.Stepper
import com.shanxiang.buspost.core.ui.TicketCard
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.QuoteResult
import com.shanxiang.buspost.data.model.Reachability
import com.shanxiang.buspost.data.model.ReachabilityState
import com.shanxiang.buspost.data.model.ReviewStatus
import com.shanxiang.buspost.data.model.RoutePreview
import com.shanxiang.buspost.data.model.Station
import com.shanxiang.buspost.data.repo.Repo
import com.shanxiang.buspost.data.store.Prefs
import com.shanxiang.buspost.nav.Routes
import com.shanxiang.buspost.ui.common.DemoNote
import com.shanxiang.buspost.ui.common.LocalNavController
import com.shanxiang.buspost.ui.common.ReviewResultCard
import com.shanxiang.buspost.ui.common.ScreenViewModel
import kotlinx.coroutines.delay
import org.json.JSONObject

private val CARGO_CATEGORIES = listOf("农产品", "生鲜果蔬", "日用百货", "建材小件", "药品", "其他")
/** 单件限重（斤）：与后端承运审核规则一致（小程序 30kg = 60 斤），超限前端先提示 */
private const val MAX_WEIGHT_JIN = 60.0
/** 公交配送最大距离（公里）：超出此距离的位置认为公交车无法送达，拦截下单 */
private const val MAX_DELIVERY_DISTANCE_KM = 50.0
/** 常见货物名快捷填入：对齐小程序 placeholder 里提到的品名，点击即填 */
private val QUICK_CARGO_NAMES = listOf("脆李", "土鸡蛋", "红薯粉", "蔬菜", "水果", "药材", "快递包裹", "其他")

/**
 * 寄货表单：草稿自动存本地（退出重进不丢），件数/重量/体积实时试算运费。
 * 小程序只能让用户手填站点，这里加了"按我的位置找最近可接站点"，
 * 不可达时直接把原因和步行时间说清楚。
 */
class SendViewModel : ScreenViewModel() {
    var goodsName by mutableStateOf("")
    var weight by mutableStateOf("")
    var itemCount by mutableStateOf("1")
    var length by mutableStateOf("")
    var width by mutableStateOf("")
    var height by mutableStateOf("")
    var note by mutableStateOf("")
    var category by mutableStateOf(CARGO_CATEGORIES.first())
    var fresh by mutableStateOf(false)
    var receiverName by mutableStateOf("")
    var receiverMobile by mutableStateOf("")
    var receiverAddress by mutableStateOf("")

    var stations by mutableStateOf<List<Station>>(emptyList())
    var pickup by mutableStateOf<Station?>(null)
    var delivery by mutableStateOf<Station?>(null)
    var quote by mutableStateOf<QuoteResult?>(null)
    var routePreview by mutableStateOf<RoutePreview?>(null)
    var reach by mutableStateOf<Reachability?>(null)
    /** 可达性报文是否来自演示兜底：演示数据说"不可达"也是假判定，界面必须标出来 */
    var reachDemo by mutableStateOf(false)
    /** 可达性接口失败原因：UNKNOWN 态要如实显示"没判成"，不能显示成"可以运输"也不能编一条不可达原因 */
    var reachError by mutableStateOf("")
    /** 超距判定：后端给了距离且超出公交配送上限 */
    val isOverDistance: Boolean get() = (reach?.distanceKm ?: 0.0) > MAX_DELIVERY_DISTANCE_KM
    /**
     * 用户原始寄货地址（高德逆地理），与"实际取货站点"是两回事：
     * 后端 `TransportOrderServiceImpl:172` 把它原样落进 `CargoOrderDO.originalAddress`，
     * 旧代码拿推荐站点名填这里，等于把用户位置覆盖成站点名（VO:13/:62 明令不互相覆盖）。
     */
    var originalAddress by mutableStateOf("")
    var myGeo by mutableStateOf<Geo?>(null)
    var photoUri by mutableStateOf<Uri?>(null)
    var photoUrl by mutableStateOf("")
    var uploadingPhoto by mutableStateOf(false)
    var photoUploadFailed by mutableStateOf(false)
    var stationsDemo by mutableStateOf(false)
    var formError by mutableStateOf("")
    var created by mutableStateOf<com.shanxiang.buspost.data.repo.Ack?>(null)
    var submitting by mutableStateOf(false)
    /** 分步流程：1 填货物 → 2 拍照确认 → 3 提交成功（对齐小程序 send.wxml 三步） */
    var step by mutableStateOf(1)

    /** 取货地址模式：station=站点选择 / manual=手动输入 / map=地图选点 */
    var pickupAddressMode by mutableStateOf("station")
    /** 送货地址模式：station=站点选择 / manual=手动输入 / map=地图选点 */
    var deliveryAddressMode by mutableStateOf("station")
    /** 手动输入的取货地址（正向地理编码后的坐标） */
    var pickupManualGeo by mutableStateOf<Geo?>(null)
    /** 手动输入的送货地址（正向地理编码后的坐标） */
    var deliveryManualGeo by mutableStateOf<Geo?>(null)
    /** 手动输入的取货地址文本 */
    var pickupManualAddress by mutableStateOf("")
    /** 手动输入的送货地址文本 */
    var deliveryManualAddress by mutableStateOf("")

    /** 重量（斤）→ 公斤：与小程序 send.js 提交时 goodsWeight*0.5 同口径 */
    val weightJin: Double get() = weight.toDoubleOrNull() ?: 0.0
    val weightKg: Double get() = weightJin * 0.5

    val volumeM3: Double
        get() {
            val l = length.toDoubleOrNull() ?: 0.0
            val w = width.toDoubleOrNull() ?: 0.0
            val h = height.toDoubleOrNull() ?: 0.0
            return if (l <= 0 || w <= 0 || h <= 0) 0.0 else (l * w * h) / 1_000_000.0
        }

    fun load(context: android.content.Context) = work {
        val f = Repo.sendStations()
        stations = f.items
        stationsDemo = f.demo
        if (pickup == null) pickup = f.items.firstOrNull()
        if (delivery == null) delivery = f.items.drop(1).firstOrNull() ?: f.items.lastOrNull()
        val geo = Locator.once(context)
        myGeo = geo
        if (geo != null) {
            val r = Repo.reachability(geo)
            reach = r.value
            reachDemo = r.value != null && r.demo
            reachError = r.error.orEmpty()
            // 可达 = 后端的就近取货站就在 recommendedStation：能落进我们的站点列表才自动选中（小程序 send.js:455-456）
            r.value?.let { if (it.state == ReachabilityState.REACHABLE) applyRecommended(it) }
            if (originalAddress.isEmpty()) {
                val rg = Repo.reverseGeo(geo)
                originalAddress = rg.formatted.ifEmpty { rg.displayName }
            }
        } else {
            // 没定位就不评估：状态保持 UNKNOWN 而不是"可达"
            reach = null
            reachDemo = false
            reachError = "未取到当前位置，无法判断可达性"
        }
        restore()
        refreshQuote()
        mergeDemo(f.demo)
        error = f.error
    }

    /** 草稿只存表单本体，派生的试算结果不存 */
    fun saveDraft() {
        val o = JSONObject()
        o.put("goodsName", goodsName)
        o.put("weight", weight)
        o.put("itemCount", itemCount)
        o.put("length", length)
        o.put("width", width)
        o.put("height", height)
        o.put("note", note)
        o.put("category", category)
        o.put("fresh", fresh)
        o.put("receiverName", receiverName)
        o.put("receiverMobile", receiverMobile)
        o.put("receiverAddress", receiverAddress)
        o.put("pickupId", pickup?.id ?: 0L)
        o.put("deliveryId", delivery?.id ?: 0L)
        Prefs.sendDraft = o.toString()
    }

    private fun restore() {
        val raw = Prefs.sendDraft
        if (raw.isEmpty()) return
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return
        goodsName = o.optString("goodsName")
        weight = o.optString("weight")
        itemCount = o.optString("itemCount", "1")
        length = o.optString("length")
        width = o.optString("width")
        height = o.optString("height")
        note = o.optString("note")
        category = o.optString("category", CARGO_CATEGORIES.first())
        fresh = o.optBoolean("fresh")
        receiverName = o.optString("receiverName")
        receiverMobile = o.optString("receiverMobile")
        receiverAddress = o.optString("receiverAddress")
        val pid = o.optLong("pickupId")
        val did = o.optLong("deliveryId")
        stations.firstOrNull { it.id == pid }?.let { pickup = it }
        stations.firstOrNull { it.id == did }?.let { delivery = it }
    }

    fun clearDraft() {
        Prefs.sendDraft = ""
    }

    fun refreshQuote() = work(reset = false) {
        val p = pickup
        val d = delivery
        if (p == null || d == null || p.id == d.id) {
            quote = null
            routePreview = null
            return@work
        }
        quote = Repo.quoteSendFee(p.id, d.id, itemCount.toIntOrNull() ?: 1).value
        routePreview = Repo.routePreview(p.id, d.id).value
    }

    /**
     * 正向地理编码搜索地址（协程版本）：用于手动输入地址后获取坐标和可达性。
     */
    fun searchAddressAsync(address: String, isPickup: Boolean) = work(reset = false) {
        if (address.isBlank()) {
            formError = "请输入地址"
            return@work
        }
        formError = ""
        val result = Repo.forwardGeo(address)
        if (result == null) {
            formError = "未找到该地址，请检查输入是否正确"
            return@work
        }
        val geo = result.geo
        if (isPickup) {
            pickupManualGeo = geo
            pickupManualAddress = result.formatted.ifEmpty { address }
            pickupAddressMode = "manual"
            // 检查可达性
            val r = Repo.reachability(geo)
            reach = r.value
            reachDemo = r.value != null && r.demo
            reachError = r.error.orEmpty()
            // 如果可达，尝试应用推荐站点
            r.value?.let { if (it.state == ReachabilityState.REACHABLE) applyRecommended(it) }
        } else {
            deliveryManualGeo = geo
            deliveryManualAddress = result.formatted.ifEmpty { address }
            deliveryAddressMode = "manual"
        }
        refreshQuote()
    }

    /**
     * 设置从地图选点返回的坐标
     */
    fun setMapPickedGeo(geo: Geo, isPickup: Boolean) = work(reset = false) {
        if (isPickup) {
            pickupManualGeo = geo
            pickupAddressMode = "map"
            // 逆地理编码获取地址文本
            val rg = Repo.reverseGeo(geo)
            pickupManualAddress = rg.formatted.ifEmpty { rg.displayName }
            // 检查可达性
            val r = Repo.reachability(geo)
            reach = r.value
            reachDemo = r.value != null && r.demo
            reachError = r.error.orEmpty()
            r.value?.let { if (it.state == ReachabilityState.REACHABLE) applyRecommended(it) }
        } else {
            deliveryManualGeo = geo
            deliveryAddressMode = "map"
            val rg = Repo.reverseGeo(geo)
            deliveryManualAddress = rg.formatted.ifEmpty { rg.displayName }
        }
        refreshQuote()
    }

    /**
     * 设置从站点列表选择的站点
     */
    fun setStationPicked(station: Station, isPickup: Boolean) {
        if (isPickup) {
            pickup = station
            pickupAddressMode = "station"
        } else {
            delivery = station
            deliveryAddressMode = "station"
        }
        refreshQuote()
    }

    /**
     * 推荐站点落位：只有在 `/send/stations` 的可选列表里找得到才真换。
     * 找不到就不构造一个"半个字段都没有的 Station"塞进表单 —— 宁可让用户手选，也不编站点。
     */
    private fun applyRecommended(r: Reachability): Boolean {
        if (r.stationId <= 0L) return false
        val st = stations.firstOrNull { it.id == r.stationId } ?: return false
        pickup = st
        refreshQuote()
        return true
    }

    /** 不可达场景的「使用推荐站点」确认（对齐小程序 send.wxml:184 / send.js:411-416） */
    fun useRecommendedStation() {
        val r = reach ?: return
        if (!applyRecommended(r)) {
            formError = "后端推荐的站点「${r.stationLabel}」不在可选站点列表里，请手动选择取货站点"
        }
    }

    /**
     * 取货方式只回传**后端给的** `serviceMode`，且必须满足两个条件：
     * ① 是 `ServiceModeEnum` 五个码之一（旧代码写死的 `"STATION"` 不在枚举内，`nameOf` 返回空串，落库后单据没法解释）；
     * ② 它描述的那个推荐站点正是本单实际取货站点 —— 否则 `NEAREST_STATION` 就贴到了用户自选的别的站点上，成了假口径。
     * 不满足就返回 null，`Api` 的空值丢键让承运审核推导默认站到站（`TransportOrderServiceImpl:202-203`：客户端值非空才采信）。
     */
    private fun pickupServiceModeOrNull(): String? {
        val r = reach ?: return null
        if (r.serviceModeLabel.isEmpty()) return null
        return if (r.stationId > 0L && r.stationId == pickup?.id) r.serviceMode.trim().uppercase(java.util.Locale.ROOT) else null
    }

    /** 取货方式展示口径：UNKNOWN 必须说"未取到判定"，不允许退化成"自选/就近站点交接"这种听着像能运的话 */
    fun serviceModeText(): String {
        val r = reach ?: return if (reachError.isEmpty()) "未做可达性判定（按自选站点寄货）" else "未做可达性判定：$reachError"
        return when (r.state) {
            ReachabilityState.REACHABLE -> "车辆可就近上门（${r.serviceModeLabel.ifEmpty { "服务方式暂未知" }}）"
            ReachabilityState.NOT_REACHABLE -> "送到服务站点交接（${r.serviceModeLabel.ifEmpty { "服务方式暂未知" }}）"
            ReachabilityState.UNKNOWN -> "可达性未判定${r.reasonText("").let { if (it.isEmpty()) "" else "：$it" }}"
        }
    }

    /**
     * 货物照片必须真的上传成功拿到后端 url 才能进入下一步：
     * 上传失败就是失败，页面显示重试，不允许"没 url 也照样提交"把缺图的单子塞进后端。
     */
    fun uploadPhoto(context: android.content.Context, uri: Uri) = work(reset = false) {
        photoUrl = ""
        uploadingPhoto = true
        formError = ""
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("读取照片失败，请重拍一张")
            val ack = Repo.uploadPhoto(bytes, "cargo-${System.currentTimeMillis()}.jpg")
            if (ack.url.isEmpty()) {
                photoUploadFailed = true
                formError = "照片上传失败，请点击重试"
            } else {
                photoUploadFailed = false
                photoUrl = ack.url
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            photoUploadFailed = true
            formError = "照片上传失败：${com.shanxiang.buspost.data.repo.errorTextOf(e)}"
        } finally {
            uploadingPhoto = false
        }
    }

    /** Step1（填货物）校验：口径对齐小程序 send.js goToPhoto——长宽高必填、限重 60 斤 */
    fun validateStep1(): String {
        val jin = weightJin
        if (goodsName.isBlank()) return "请填写货物名称"
        if (jin <= 0) return "请输入正确的货物重量"
        if (jin > MAX_WEIGHT_JIN) return "单件限重 ${MAX_WEIGHT_JIN.toInt()} 斤，大件请分单"
        if ((itemCount.toIntOrNull() ?: 0) < 1) return "请输入货物件数"
        if (volumeM3 <= 0.0) return "请填写货物长宽高"
        if (pickup == null) return "请选择取货站点"
        if (delivery == null) return "请选择送达站点"
        if (pickup?.id == delivery?.id) return "取货站点和送达站点不能相同"
        // 超距拦截：后端 reachability 给了距离且超出公交配送范围时，直接拦截
        reach?.distanceKm?.let { dist ->
            if (dist > MAX_DELIVERY_DISTANCE_KM) {
                return "当前位置距配送范围 ${String.format("%.0f", dist)} 公里，超出公交配送上限 ${MAX_DELIVERY_DISTANCE_KM.toInt()} 公里"
            }
        }
        return ""
    }

    /** Step2（拍照确认）校验：照片必须**真的传到后端**拿到 url；收货电话必填（与小程序 wxml 标注一致） */
    fun validateStep2(): String {
        if (photoUri == null) return "请先拍照确认货物"
        if (uploadingPhoto) return "照片正在上传，请稍候"
        if (photoUrl.isEmpty() && !demoAllowed()) return "照片未上传成功，请重试后再提交"
        if (receiverMobile.length < 7) return "请输入正确的收货电话"
        return ""
    }

    fun goToPhoto() {
        formError = validateStep1()
        if (formError.isNotEmpty()) return
        step = 2
    }

    /** 重新填写：清空全部表单回到第 1 步（对齐小程序 resetSend） */
    fun resetSend() {
        goodsName = ""
        weight = ""
        itemCount = "1"
        length = ""
        width = ""
        height = ""
        note = ""
        category = CARGO_CATEGORIES.first()
        fresh = false
        receiverName = ""
        receiverMobile = ""
        receiverAddress = ""
        photoUri = null
        photoUrl = ""
        photoUploadFailed = false
        uploadingPhoto = false
        formError = ""
        step = 1
    }

    /**
     * 提交报文单独抽出：契约测试要能直接断言"不在枚举里的取货方式不回传""原始地址不是推荐站点名"，
     * 而不必先跑完拍照上传那条协程（`android.net.Uri` 在 JVM 单测里是假类）。
     */
    internal fun buildSendForm(): Map<String, Any?> = mapOf(
        "pickupStationId" to pickup?.id,
        "deliveryStationId" to delivery?.id,
        "goodsName" to goodsName.trim(),
        "goodsWeight" to weightKg,
        "cargoCategory" to category,
        "itemCount" to (itemCount.toIntOrNull() ?: 1),
        "volumeM3" to volumeM3,
        "freshFlag" to fresh,
        "goodsNote" to note.trim(),
        "photoUrl" to photoUrl,
        "receiverName" to receiverName.trim(),
        "receiverMobile" to receiverMobile.trim(),
        "receiverAddress" to receiverAddress.trim(),
        "pickupServiceMode" to pickupServiceModeOrNull(),
        "originalAddress" to originalAddress.trim(),
        "originalLatitude" to myGeo?.lat,
        "originalLongitude" to myGeo?.lng,
    )

    fun submit() {
        formError = validateStep2()
        if (formError.isNotEmpty()) return
        submitting = true
        work(reset = false) {
            try {
                val form = buildSendForm()
                if (photoUploadFailed || (photoUrl.isEmpty() && !Api.demoAllowed)) {
                    formError = if (photoUrl.isEmpty()) "货物照片还没上传成功，请重试后再提交" else formError
                    return@work
                }
                val ack = Repo.createSendOrder(form)
                // 被本地幂等闸拦下（F-2 第 6 步）：本次点击没出网 ⇒ 草稿不清、步骤不推进、不谎称已提交，
                // 也不谎称失败（失败意味着"后端拒了"，而后端根本没收到）
                if (ack.notSent) {
                    formError = "同一份寄货单还在创建中，本次点击没有发给后端；请到「我的寄货」核对，不要重复提交"
                    return@work
                }
                if (!ack.demo) clearDraft()
                // 「create 返回了」不等于「单子已入账」：重拉后端寄货列表，订单号/取件码只认服务端那条
                val page = Repo.sendOrders(1, 20)
                val server = page.items.firstOrNull { it.orderNo == ack.orderNo } ?: page.items.firstOrNull()
                val no = ack.orderNo.ifEmpty { server?.orderNo.orEmpty() }
                if (no.isEmpty()) {
                    formError = page.error ?: "后端未回订单号，请到「我的寄货」核对后再试"
                    return@work
                }
                created = ack.copy(
                    orderNo = no,
                    pickupCode = ack.pickupCode.ifEmpty { server?.pickupCode.orEmpty() },
                    confirmed = page.error == null,
                )
                step = 3
            } finally {
                submitting = false
            }
        }
    }
}

@Composable
fun SendScreen(vm: SendViewModel = viewModel()) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val nav = LocalNavController.current
    val context = LocalContext.current
    var pickStation by remember { mutableStateOf(0) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.photoUri = uri
        vm.uploadPhoto(context, uri)
    }

    LaunchedEffect(Unit) { if (vm.stations.isEmpty()) vm.load(context) }
    LaunchedEffect(vm.goodsName, vm.weight, vm.itemCount, vm.length, vm.width, vm.height, vm.note, vm.category, vm.fresh, vm.receiverName, vm.receiverMobile, vm.receiverAddress, vm.pickup, vm.delivery) {
        delay(500)
        vm.saveDraft()
    }

    // 监听地图选点返回结果
    LaunchedEffect(nav) {
        nav.currentBackStackEntry?.savedStateHandle?.getLiveData<Geo>("selectedGeo")?.observeForever { geo ->
            if (geo != null) {
                val target = nav.currentBackStackEntry?.savedStateHandle?.get<String>("addressTarget") ?: "pickup"
                vm.setMapPickedGeo(geo, isPickup = target == "pickup")
                // 清除已消费的结果
                nav.currentBackStackEntry?.savedStateHandle?.remove<Geo>("selectedGeo")
                nav.currentBackStackEntry?.savedStateHandle?.remove<String>("addressTarget")
            }
        }
    }

    BusPage(spacedBy = 12.dp) {
        when (vm.step) {
            // ── Step 1：填写货物信息（对齐小程序 send.wxml step 1）──
            1 -> {
                StepProgressBar(currentStep = 1)
                Signpost(
                    badge = "第 1 步 · 共 3 步",
                    title = "填写货物信息",
                    subtitle = "写清货物与收发站点，费用提前算给你看",
                )
                DemoNote(vm.demo || vm.stationsDemo || vm.reachDemo)
                vm.error?.let { ErrorState(it, onRetry = { vm.load(context) }) }
                val reach = vm.reach
                val canApply = reach != null && reach.stationId > 0L && vm.stations.any { it.id == reach.stationId }
                val applied = reach != null && reach.stationId > 0L && reach.stationId == vm.pickup?.id
                ReachCard(
                    reach = reach,
                    errorText = if (reach == null) vm.reachError else "",
                    demo = vm.reachDemo,
                    canApply = canApply,
                    applied = applied,
                    isOverDistance = vm.isOverDistance,
                    onApply = { vm.useRecommendedStation() },
                )

                SectionTitle("物品信息")
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BusTextField(
                            value = vm.goodsName,
                            onValueChange = { vm.goodsName = it },
                            label = "货物名称 *",
                            placeholder = "如：脆李、土鸡蛋、红薯粉",
                            icon = BusIcons.parcel,
                        )
                        if (vm.goodsName.isEmpty()) QuickCargoChips(onPick = { vm.goodsName = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BusTextField(
                                value = vm.weight,
                                onValueChange = { vm.weight = it.filter { c -> c.isDigit() || c == '.' } },
                                label = "货物重量 *",
                                placeholder = "如：50",
                                modifier = Modifier.weight(1f),
                                keyboardType = KeyboardType.Decimal,
                            )
                            Column(Modifier.weight(1f)) {
                                Text("货物件数 *", color = t.textSecondary, fontSize = (12.5 * scale).sp)
                                Stepper(
                                    value = vm.itemCount,
                                    onValueChange = { vm.itemCount = it },
                                    min = 1.0,
                                    max = 99.0,
                                    unit = "件",
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                        if (vm.weightJin > 0) {
                            Text(
                                "= ${String.format(java.util.Locale.CHINA, "%.1f", vm.weightKg)} kg" +
                                    if (vm.weightJin > MAX_WEIGHT_JIN) " · 超出单件限重 ${MAX_WEIGHT_JIN.toInt()} 斤" else "",
                                color = if (vm.weightJin > MAX_WEIGHT_JIN) t.danger else t.textTertiary,
                                fontSize = (11.5 * scale).sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        CategoryChips(vm.category) { vm.category = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LongSide("长", vm.length) { vm.length = it }
                            LongSide("宽", vm.width) { vm.width = it }
                            LongSide("高", vm.height) { vm.height = it }
                        }
                        Text(
                            "体积约 ${if (vm.volumeM3 > 0) String.format("%.3f", vm.volumeM3) else "—"} m³（按长×宽×高，单位厘米换算）",
                            color = t.textTertiary,
                            fontSize = (11.5 * scale).sp,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("生鲜 / 需冷链", color = t.ink, fontSize = (14 * scale).sp)
                                Text("勾选后承运方需人工确认运输条件", color = t.textTertiary, fontSize = (11.5 * scale).sp)
                            }
                            BusSwitch(checked = vm.fresh, onCheckedChange = { vm.fresh = it })
                        }
                        BusTextField(
                            value = vm.note,
                            onValueChange = { vm.note = it.take(100) },
                            label = "备注（选填）",
                            placeholder = "补充说明，如：麻烦放阴凉处",
                            singleLine = false,
                            minLines = 2,
                        )
                    }
                }

                // 寄件 · 怎么取货（支持三种模式：站点选择/手动输入/地图选点）
                SectionTitle("寄件 · 怎么取货")
                AddressModeCard(
                    mode = vm.pickupAddressMode,
                    stationName = vm.pickup?.name,
                    manualAddress = vm.pickupManualAddress,
                    isPickup = true,
                    onPickStation = { pickStation = 1 },
                    onSearchAddress = { address -> vm.searchAddressAsync(address, isPickup = true) },
                    onPickMap = {
                        nav.currentBackStackEntry?.savedStateHandle?.set("addressTarget", "pickup")
                        nav.navigate(Routes.MAP_POINT_PICKER)
                    },
                    onClear = {
                        vm.pickupManualGeo = null
                        vm.pickupManualAddress = ""
                        vm.pickupAddressMode = "station"
                    },
                )

                // 收件 · 送到哪里（支持三种模式：站点选择/手动输入/地图选点）
                SectionTitle("收件 · 送到哪里")
                AddressModeCard(
                    mode = vm.deliveryAddressMode,
                    stationName = vm.delivery?.name,
                    manualAddress = vm.deliveryManualAddress,
                    isPickup = false,
                    onPickStation = { pickStation = 2 },
                    onSearchAddress = { address -> vm.searchAddressAsync(address, isPickup = false) },
                    onPickMap = {
                        nav.currentBackStackEntry?.savedStateHandle?.set("addressTarget", "delivery")
                        nav.navigate(Routes.MAP_POINT_PICKER)
                    },
                    onClear = {
                        vm.deliveryManualGeo = null
                        vm.deliveryManualAddress = ""
                        vm.deliveryAddressMode = "station"
                    },
                )

                // 费用试算：填完取货/送达站点即出金额（对齐小程序 quote-card，含山路线装饰）
                if (vm.quote != null) {
                    SectionTitle("费用试算")
                    BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                        val q = vm.quote!!
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("费用试算", color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                // 缺键不许显示 ¥0（F-2 §三）：文案由模型侧 amountValueText 给，未给时连币符一起摘掉
                                PriceText(q.amountValueText, size = 22, prefix = if (q.amount == null) "" else "¥")
                            }
                            RoadLine(Modifier.padding(top = 8.dp))
                            Text(
                                // 件数费/里程费/件数/计价里程那一行：null 与"后端真给 0"的分支口径见 QuoteResult.feeBreakdownText
                                q.feeBreakdownText,
                                color = t.textSecondary,
                                fontSize = (12.5 * scale).sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }

                // 路线预估：真实道路距离 / 预计行驶时间
                if (vm.routePreview != null) {
                    SectionTitle("路线预估")
                    BusCard(modifier = Modifier.fillMaxWidth()) {
                        val r = vm.routePreview!!
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(BusIcons.route, null, tint = t.primary, modifier = Modifier.size(18.dp))
                            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                                Text(
                                    // 后端不可用时这两个键都是 null（RoutePreviewRespVO:15-19），显示"未给"而不是 0 km / 0 分钟
                                    "取货站 → 送达站 " +
                                        (r.distanceKm?.let { "约 ${String.format("%.1f", it)} km" } ?: "里程暂未知") +
                                        " · " + (r.durationMinutes?.let { "$it 分钟" } ?: "时长暂未知"),
                                    color = t.ink,
                                    fontSize = (13.5 * scale).sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    if (r.estimated) "路网暂不可用，当前为直线估算" else "导航来源：${r.provider}",
                                    color = t.textTertiary,
                                    fontSize = (11.5 * scale).sp,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                    }
                }

                if (vm.formError.isNotEmpty()) {
                    Text(vm.formError, color = t.danger, fontSize = (12.5 * scale).sp)
                }

                BusButton(
                    text = "下一步：拍照确认",
                    onClick = { vm.goToPhoto() },
                    icon = BusIcons.camera,
                )
            }

            // ── Step 2：拍照确认（对齐小程序 send.wxml step 2）──
            2 -> {
                StepProgressBar(currentStep = 2)
                Signpost(
                    badge = "第 2 步 · 共 3 步",
                    title = "拍照确认货物",
                    subtitle = "拍张货物照片，司机取货更好认",
                )
                DemoNote(vm.demo || vm.stationsDemo)

                SectionTitle("货物照片")
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    if (vm.photoUri != null) {
                        NetworkImage(
                            url = vm.photoUri?.toString(),
                            contentDescription = null,
                            width = 400.dp,
                            height = 150.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(BusRadius.m)),
                            contentScale = ContentScale.Crop,
                        )
                        BusButton(
                            text = when {
                                vm.uploadingPhoto -> "照片上传中…"
                                vm.photoUploadFailed -> "上传失败，点击重试"
                                vm.photoUrl.isNotEmpty() -> "已上传到后端，点击换一张"
                                else -> "点击重新选择照片"
                            },
                            variant = if (vm.photoUploadFailed) BusButtonVariant.OUTLINE else BusButtonVariant.GHOST,
                            onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(BusRadius.m))
                                .background(t.field)
                                .clickable {
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(BusIcons.camera, null, tint = t.textTertiary, modifier = Modifier.size(30.dp))
                                Text("点击拍照", color = t.ink, fontSize = (13.5 * scale).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                                Text("拍摄货物照片，方便司机辨认", color = t.textTertiary, fontSize = (11.5 * scale).sp)
                            }
                        }
                    }
                }

                // 信息核对（对齐小程序 info-summary）
                SectionTitle("信息核对")
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CheckRow("货物", vm.goodsName.ifBlank { "—" })
                        CheckRow("重量", "${vm.weightJin.toInt()} 斤")
                        CheckRow("类型", vm.category)
                        CheckRow("件数", "${vm.itemCount} 件")
                        CheckRow("体积", "${vm.length}×${vm.width}×${vm.height} cm（${if (vm.volumeM3 > 0) String.format("%.3f", vm.volumeM3) else "—"} m³）")
                        if (vm.fresh) CheckRow("生鲜", "需冷链，承运方确认")
                        CheckRow("取货站点", vm.pickup?.name ?: "—")
                        CheckRow("送达站点", vm.delivery?.name ?: "—")
                        // 只认后端 distanceKm（公里）：旧 distanceMeters 键后端契约里没有，缺值时整行不显示，不写"0 公里"
                        val rc = vm.reach
                        rc?.distanceKm?.let {
                            CheckRow("到推荐站点", "${rc.distanceText} · ${rc.walkText}")
                        }
                        if (vm.note.isNotBlank()) CheckRow("备注", vm.note)
                        if (vm.quote != null) CheckRow("预估费用", vm.quote!!.amountText)
                    }
                }

                // 收货信息：电话必填，姓名/地址选填（与小程序标注一致），右上角地址簿
                SectionTitle("收货信息", actionText = "地址簿", onAction = {
                    AddressPicker.onPick = { a ->
                        vm.receiverName = a.name
                        vm.receiverMobile = a.mobile
                        vm.receiverAddress = a.regionLabel
                        AddressPicker.onPick = null
                        nav.popBackStack()
                    }
                    nav.navigate(Routes.ADDRESS_SELECT)
                })
                BusCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BusTextField(
                            value = vm.receiverMobile,
                            onValueChange = { vm.receiverMobile = it.filter { c -> c.isDigit() } },
                            label = "收货电话 *",
                            placeholder = "请输入收货电话",
                            keyboardType = KeyboardType.Phone,
                        )
                        BusTextField(
                            value = vm.receiverName,
                            onValueChange = { vm.receiverName = it },
                            label = "收货人（选填）",
                            placeholder = "请输入收货人姓名",
                        )
                        BusTextField(
                            value = vm.receiverAddress,
                            onValueChange = { vm.receiverAddress = it },
                            label = "收货地址（选填）",
                            placeholder = "请输入收货地址",
                        )
                    }
                }

                if (vm.formError.isNotEmpty()) {
                    Text(vm.formError, color = t.danger, fontSize = (12.5 * scale).sp)
                }

                // 固定提交栏：重新填写 + 确认发布
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    BusButton(
                        text = "重新填写",
                        onClick = { vm.resetSend() },
                        variant = BusButtonVariant.OUTLINE,
                        modifier = Modifier.weight(1f),
                    )
                    BusButton(
                        text = "确认发布",
                        onClick = { vm.submit() },
                        loading = vm.submitting,
                        icon = BusIcons.send,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Step 3：提交成功（对齐小程序 send.wxml step 3 票券页）──
            else -> {
                StepProgressBar(currentStep = 3)
                Signpost(
                    badge = "寄货单已受理",
                    title = "寄货已提交！",
                    subtitle = "保存好单号，可在「我的寄货」查看进度",
                )

                vm.created?.let { ack ->
                    val no = ack.orderNo

                    // 承运审核结果：可运/需操作/待人工/不可运
                    if (ack.reviewStatus != 0) {
                        ReviewResultCard(
                            status = ack.reviewStatus,
                            reasonCodes = ack.reviewReasonCodes,
                            servicePointName = ack.servicePointStationName,
                            servicePointKm = ack.servicePointDistanceKm,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (no.isEmpty()) {
                        BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                            Text(
                                "订单正在处理中，请稍后到「我的寄货」查看进度。",
                                color = t.ink,
                                fontSize = (13 * scale).sp,
                            )
                        }
                    } else {
                        // 订单车票：主券放二维码与单号，副券放金额（对应小程序 ticket-card qr-main/stub）
                        TicketCard(stubValue = vm.quote?.amountText, stubLabel = "订单金额") {
                            Column(
                                Modifier.weight(1f).padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                QrView(
                                    text = no,
                                    caption = "",
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                Text(
                                    no,
                                    color = t.ink,
                                    fontSize = (14 * scale).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                Text(
                                    "寄货订单号 · 请妥善保存",
                                    color = t.textTertiary,
                                    fontSize = (11 * scale).sp,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                                Text(
                                    "到站点交件时出示二维码，司机扫码装车",
                                    color = t.textSecondary,
                                    fontSize = (11.5 * scale).sp,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                        // 回读失败：单号是后端给的，但没在列表里确认，不能让用户以为已经入账
                        if (!ack.confirmed) {
                            BusCard(modifier = Modifier.fillMaxWidth(), warm = true) {
                                Text(
                                    "后端已受理并给出单号，但寄货列表暂时没回读到它。请到「我的寄货」下拉刷新核对，确认前不要重复下单。",
                                    color = t.ink,
                                    fontSize = (13 * scale).sp,
                                )
                            }
                        }
                    }

                    // 温馨提示（对齐 match-reminder）
                    BusCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(BusIcons.info, null, tint = t.clayDeep, modifier = Modifier.size(16.dp))
                                Text("温馨提示", color = t.ink, fontSize = (13 * scale).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                            }
                            Text(
                                "订单已提交，调度员将尽快为您安排班次，可在「我的」→「我的寄货」查看进度。",
                                color = t.textSecondary,
                                fontSize = (12 * scale).sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }

                    // 审核不通过：主行动改为再编辑；正常受理：直达该单物流详情
                    if (ack.reviewStatus == 4) {
                        BusButton(
                            text = "重新填写，再寄一次",
                            onClick = {
                                vm.created = null
                                vm.resetSend()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (no.isNotEmpty()) {
                        BusButton(
                            text = "查看寄货进度",
                            onClick = { nav.navigate(Routes.trace(no)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    BusButton(
                        text = "返回首页",
                        onClick = { nav.popBackStack() },
                        variant = BusButtonVariant.OUTLINE,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 取货方式与原始地址（答辩演示重点：位置 ≠ 车辆能到的地方）
                    if (vm.reach != null || vm.pickup != null) {
                        SectionTitle("取货信息")
                        BusCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CheckRow("取货方式", vm.serviceModeText())
                                if (vm.pickup != null) CheckRow("取货站点", vm.pickup?.name ?: "—")
                                // 用户原始位置与取货站点分行显示：两者落库到不同字段，不互相覆盖（VO:13 / :172）
                                // 逆地理没给文字地址时不藏掉这一行：只提交了经纬度就要说清只提交了经纬度
                                when {
                                    vm.originalAddress.isNotBlank() -> CheckRow("寄货位置", vm.originalAddress)
                                    vm.myGeo != null -> CheckRow("寄货位置", "文字地址未取到，本单只提交了经纬度")
                                }
                                val rc = vm.reach
                                rc?.distanceKm?.let {
                                    CheckRow("到推荐站点", "${rc.distanceText} · ${rc.walkText}")
                                }
                                if (vm.myGeo != null) {
                                    CheckRow("定位坐标", "${String.format("%.4f", vm.myGeo!!.lat)}, ${String.format("%.4f", vm.myGeo!!.lng)}")
                                }
                                Text(
                                    "订单已进入调度系统，调度员将安排最近班次",
                                    color = t.textTertiary,
                                    fontSize = (11 * scale).sp,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (pickStation != 0) {
        StationPicker(
            title = if (pickStation == 1) "选择取货站点" else "选择送达站点",
            stations = vm.stations,
            current = if (pickStation == 1) vm.pickup else vm.delivery,
            onPick = {
                vm.setStationPicked(it, isPickup = pickStation == 1)
                pickStation = 0
            },
            onDismiss = { pickStation = 0 },
        )
    }
}

@Composable
private fun CheckRow(label: String, value: String) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = t.textTertiary, fontSize = (12.5 * scale).sp, modifier = Modifier.width(56.dp))
        Text(value, color = t.ink, fontSize = (13.5 * scale).sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

/**
 * 承运审核结果卡：与小程序 send.js resolveReview 五态同口径
 * （1通过 / 2需操作 / 3待人工 / 4不通过 / 0 审核中）。
 */
@Composable
private fun ReviewResultCard(
    status: Int,
    reasonCodes: String,
    servicePointName: String,
    servicePointKm: Double,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val (title, hint, fg, warm) = when (ReviewStatus.of(status)) {
        ReviewStatus.PASSED -> Quad("审核通过", "订单可进入待入池，调度员将尽快为您安排班次", t.primaryOnLight, false)
        ReviewStatus.CONDITIONAL -> Quad(
            "需您操作",
            if (servicePointName.isNotEmpty()) {
                "请将货物送到就近站点「$servicePointName」${if (servicePointKm > 0) "（距取货点约 ${servicePointKm} km）" else ""}交接后即可入池"
            } else {
                "请将货物送到指定站点交接后即可入池"
            },
            t.goldDeep,
            true,
        )
        ReviewStatus.MANUAL_REVIEW -> Quad("待人工审核", "工作人员将尽快确认承运条件，请留意通知", t.textSecondary, false)
        ReviewStatus.REJECTED -> Quad("审核不通过", "该货物暂不支持承运，可重新填写货物信息后再提交", t.danger, false)
        else -> Quad("审核中", "正在为您确认承运条件", t.textSecondary, false)
    }
    BusCard(modifier = modifier.fillMaxWidth(), warm = warm) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (ReviewStatus.of(status)) {
                        ReviewStatus.PASSED -> BusIcons.checkCircle
                        ReviewStatus.REJECTED -> BusIcons.warning
                        else -> BusIcons.info
                    },
                    null,
                    tint = fg,
                    modifier = Modifier.size(18.dp),
                )
                Text(title, color = fg, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 7.dp))
            }
            Text(hint, color = t.textSecondary, fontSize = (12 * scale).sp, modifier = Modifier.padding(top = 6.dp))
            if (reasonCodes.isNotEmpty()) {
                Text("原因码：$reasonCodes", color = t.textTertiary, fontSize = (11 * scale).sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun RowScope.LongSide(label: String, value: String, onChange: (String) -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Column(Modifier.weight(1f)) {
        Text(label, color = t.textSecondary, fontSize = (12 * scale).sp)
        BusTextField(
            value = value,
            onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
            placeholder = "cm",
            modifier = Modifier.padding(top = 4.dp),
            singleLine = true,
        )
    }
}

@Composable
private fun CategoryChips(selected: String, onPick: (String) -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("货物类型", color = t.textSecondary, fontSize = (12.5 * scale).sp)
        CARGO_CATEGORIES.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { c ->
                    val on = c == selected
                    Text(
                        c,
                        color = if (on) t.primaryOnLight else t.textSecondary,
                        fontSize = (12.5 * scale).sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(BusRadius.pill))
                            .background(if (on) t.primaryLight else t.track)
                            .clickable { onPick(c) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * 可达性卡片（审计 §34 真实化 + §11 三态）。三分支的口径是硬规则：
 * - REACHABLE：才允许"车辆可就近上门"，距离/步行按后端 `distanceKm`（公里）显示，来源如实标注；
 * - NOT_REACHABLE：原因只用后端 `reasonCode`/`message`，本机不猜；推荐站点为空就不显示站名；
 * - UNKNOWN（接口失败、报文缺 `reachable`、demo 关闭）：**既不说可运，也不编不可达原因**，只说"未判定"。
 */
@Composable
private fun ReachCard(
    reach: Reachability?,
    errorText: String,
    demo: Boolean,
    canApply: Boolean,
    applied: Boolean,
    isOverDistance: Boolean,
    onApply: () -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val r = reach ?: Reachability.unknown()
    val state = r.state
    val ok = state == ReachabilityState.REACHABLE && !isOverDistance
    BusCard(modifier = Modifier.fillMaxWidth(), warm = !ok) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (ok) BusIcons.checkCircle else BusIcons.warning,
                null,
                tint = if (ok) t.primary else if (isOverDistance) t.danger else t.clayDeep,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.padding(start = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    when {
                        isOverDistance -> "当前位置超出公交配送范围"
                        state == ReachabilityState.REACHABLE -> r.message.ifEmpty { r.tip }.ifEmpty { state.label }
                        state == ReachabilityState.NOT_REACHABLE -> "当前位置车辆无法直接进入"
                        else -> state.label
                    },
                    color = if (isOverDistance) t.danger else t.ink,
                    fontSize = (13.5 * scale).sp,
                    fontWeight = FontWeight.Bold,
                )
                if (isOverDistance) {
                    Text(
                        "距配送范围 ${r.distanceKm?.let { String.format("%.0f", it) } ?: "?"} 公里，公交配送上限 ${MAX_DELIVERY_DISTANCE_KM.toInt()} 公里",
                        color = t.danger,
                        fontSize = (11.5 * scale).sp,
                    )
                }
                if (demo) Text("演示兜底数据，不是后端判定", color = t.clayDeep, fontSize = (11 * scale).sp)
                when (state) {
                    ReachabilityState.REACHABLE -> {
                        if (r.stationLabel.isNotEmpty()) Text("就近取货站点：${r.stationLabel}", color = t.textSecondary, fontSize = (12 * scale).sp)
                        else Text("后端判定可上门，但没给出就近站点，请手动选择取货站点", color = t.clayDeep, fontSize = (11.5 * scale).sp)
                        Text(
                            r.distanceText + if (r.distanceKm == null) "" else " · ${r.walkText}",
                            color = t.textSecondary,
                            fontSize = (12 * scale).sp,
                        )
                        // 可上门也一样：推荐站点落不进可选列表就得说"请手选"，不能让用户以为已经排好了
                        if (!applied && r.stationId > 0L) {
                            Text("推荐站点不在可选列表，请手动选择取货站点", color = t.clayDeep, fontSize = (11.5 * scale).sp)
                        } else if (applied) {
                            Text("已按推荐站点选为取货站点", color = t.primary, fontSize = (11.5 * scale).sp)
                        }
                    }
                    ReachabilityState.NOT_REACHABLE -> {
                        if (r.stationLabel.isNotEmpty()) {
                            Text("推荐送站：${r.stationLabel}", color = t.textSecondary, fontSize = (12 * scale).sp)
                            Text("${r.distanceText} · ${r.walkText}", color = t.textSecondary, fontSize = (12 * scale).sp)
                        } else {
                            Text("暂未获取推荐站点，请手动选择取货站点", color = t.textSecondary, fontSize = (12 * scale).sp)
                        }
                        Text(
                            r.reasonText(r.message.ifEmpty { "请送到服务站点交接" }),
                            color = t.textTertiary,
                            fontSize = (11.5 * scale).sp,
                        )
                        if (!applied) {
                            if (canApply) {
                                BusButton(
                                    text = "使用推荐站点",
                                    onClick = onApply,
                                    variant = BusButtonVariant.OUTLINE,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            } else if (r.stationId > 0L) {
                                Text("推荐站点不在可选列表，请手动选择取货站点", color = t.clayDeep, fontSize = (11.5 * scale).sp)
                            }
                        } else {
                            Text("已按推荐站点选为取货站点", color = t.primary, fontSize = (11.5 * scale).sp)
                        }
                    }
                    ReachabilityState.UNKNOWN -> {
                        Text(
                            when {
                                errorText.isNotEmpty() -> "可达性暂未知：$errorText"
                                r.reasonCode.isNotEmpty() -> r.reasonText("可达性暂未知")
                                else -> "可达性暂未知"
                            },
                            color = t.textSecondary,
                            fontSize = (12 * scale).sp,
                        )
                        Text("请手动选择取货站点；本单不声称车辆可上门", color = t.textTertiary, fontSize = (11.5 * scale).sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StationPicker(
    title: String,
    stations: List<Station>,
    current: Station?,
    onPick: (Station) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .height(320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                stations.forEach { s ->
                    val on = s.id == current?.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(BusRadius.m))
                            .background(if (on) t.primaryLight else t.card)
                            .clickable { onPick(s) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, color = t.ink, fontSize = (14 * scale).sp, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(
                                    if (s.distanceMeters > 0) "距你 %.1f 公里".format(s.distanceMeters / 1000.0) else null,
                                    s.routes.firstOrNull()?.let { "途经 $it" },
                                ).joinToString(" · ").ifEmpty { "班车经停" },
                                color = t.textSecondary,
                                fontSize = (12 * scale).sp,
                            )
                        }
                        if (on) Icon(BusIcons.check, null, tint = t.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/**
 * 三步进度条：圆点 + 连线，当前步高亮、已完成步填实。
 * 对标货拉拉/滴滴货运下单页的顶部分步指示。
 */
@Composable
private fun StepProgressBar(currentStep: Int) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    val labels = listOf("填货物", "拍照确认", "提交完成")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val step = i + 1
            val done = step < currentStep
            val active = step == currentStep
            val dotColor = when {
                done -> t.primary
                active -> t.primary
                else -> t.textTertiary
            }
            val textColor = when {
                done -> t.primary
                active -> t.ink
                else -> t.textTertiary
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size((22 * scale).dp)
                        .clip(RoundedCornerShape(BusRadius.pill))
                        .background(if (done || active) t.primaryLight else t.track),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(BusIcons.check, null, tint = t.primary, modifier = Modifier.size((14 * scale).dp))
                    } else {
                        Text(
                            "$step",
                            color = dotColor,
                            fontSize = (12 * scale).sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    label,
                    color = textColor,
                    fontSize = (11.5 * scale).sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
            if (i < labels.size - 1) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(BusRadius.pill))
                        .background(if (step < currentStep) t.primary else t.track),
                )
            }
        }
    }
}

/** 常见货物名快捷填入芯片行 */
@Composable
private fun QuickCargoChips(onPick: (String) -> Unit) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        QUICK_CARGO_NAMES.take(4).forEach { name ->
            Text(
                name,
                color = t.textSecondary,
                fontSize = (11.5 * scale).sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(BusRadius.pill))
                    .background(t.track)
                    .clickable { onPick(name) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * 地址选择卡片：支持三种模式（站点选择/手动输入/地图选点）
 * 对标货拉拉/滴滴货运的地址选择交互
 */
@Composable
private fun AddressModeCard(
    mode: String,
    stationName: String?,
    manualAddress: String,
    isPickup: Boolean,
    onPickStation: () -> Unit,
    onSearchAddress: (String) -> Unit,
    onPickMap: () -> Unit,
    onClear: () -> Unit,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }

    BusCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 当前选中的地址显示
            when (mode) {
                "station" -> {
                    if (stationName != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isPickup) BusIcons.location else BusIcons.flag,
                                null,
                                tint = t.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                                Text(
                                    if (isPickup) "取货站点" else "送达站点",
                                    color = t.textTertiary,
                                    fontSize = (11 * scale).sp,
                                )
                                Text(
                                    stationName,
                                    color = t.ink,
                                    fontSize = (14 * scale).sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Icon(BusIcons.check, null, tint = t.primary, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isPickup) BusIcons.location else BusIcons.flag,
                                null,
                                tint = t.textTertiary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "未选择${if (isPickup) "取货" else "送达"}站点",
                                color = t.textSecondary,
                                fontSize = (13 * scale).sp,
                                modifier = Modifier.padding(start = 8.dp).weight(1f),
                            )
                        }
                    }
                }
                "manual", "map" -> {
                    if (manualAddress.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                BusIcons.location,
                                null,
                                tint = if (mode == "map") t.primary else t.goldDeep,
                                modifier = Modifier.size(18.dp).padding(top = 2.dp),
                            )
                            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                                Text(
                                    if (mode == "map") "地图选点地址" else "手动输入地址",
                                    color = t.textTertiary,
                                    fontSize = (11 * scale).sp,
                                )
                                Text(
                                    manualAddress,
                                    color = t.ink,
                                    fontSize = (13 * scale).sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Icon(
                                BusIcons.close,
                                null,
                                tint = t.textTertiary,
                                modifier = Modifier.size(16.dp).clickable { onClear() },
                            )
                        }
                    }
                }
            }

            // 三种模式切换按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 按钮1：选择站点
                AddressModeButton(
                    text = "选站点",
                    icon = BusIcons.bus,
                    selected = mode == "station",
                    onClick = onPickStation,
                    modifier = Modifier.weight(1f),
                )
                // 按钮2：手动输入
                AddressModeButton(
                    text = "手动输入",
                    icon = BusIcons.edit,
                    selected = mode == "manual",
                    onClick = { showSearchDialog = true },
                    modifier = Modifier.weight(1f),
                )
                // 按钮3：地图选点
                AddressModeButton(
                    text = "地图选点",
                    icon = BusIcons.map,
                    selected = mode == "map",
                    onClick = onPickMap,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // 手动输入地址对话框
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text(if (isPickup) "输入取货地址" else "输入送货地址") },
            text = {
                Column {
                    BusTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        label = "地址",
                        placeholder = "如：云山村 3 组，村口小卖部",
                        icon = BusIcons.location,
                        minLines = 2,
                        singleLine = false,
                    )
                    Text(
                        "输入地址后将自动匹配就近站点或安排公交车取货",
                        color = t.textTertiary,
                        fontSize = (11 * scale).sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (searchInput.isNotBlank()) {
                            onSearchAddress(searchInput)
                            showSearchDialog = false
                            searchInput = ""
                        }
                    },
                ) { Text("查询") }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("取消") }
            },
        )
    }
}

/**
 * 地址模式切换按钮
 */
@Composable
private fun RowScope.AddressModeButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalBusTokens.current
    val scale = LocalBusFontScale.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(BusRadius.m))
            .background(if (selected) t.primaryLight else t.track)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            null,
            tint = if (selected) t.primary else t.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text,
            color = if (selected) t.primary else t.textSecondary,
            fontSize = (11 * scale).sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
