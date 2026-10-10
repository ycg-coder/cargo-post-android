package com.shanxiang.buspost.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CompassCalibration
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Money
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Signpost
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.ZoomInMap
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 全站图标白名单：页面只从这里取图标。
 * 这样一旦某个 material-icons 名称在当前版本不存在，只有本文件需要调整。
 */
object BusIcons {
    val home: ImageVector = Icons.Rounded.Home
    val cart: ImageVector = Icons.Rounded.ShoppingCart
    val parcel: ImageVector = Icons.Rounded.Inventory2
    val leaf: ImageVector = Icons.Rounded.Spa
    val person: ImageVector = Icons.Rounded.Person
    val bus: ImageVector = Icons.Rounded.DirectionsBus
    val ship: ImageVector = Icons.Rounded.LocalShipping
    val location: ImageVector = Icons.Rounded.LocationOn
    val myLocation: ImageVector = Icons.Rounded.MyLocation
    val map: ImageVector = Icons.Rounded.Map
    val route: ImageVector = Icons.Rounded.Route
    val navigation: ImageVector = Icons.Rounded.Navigation
    val arrowUp: ImageVector = Icons.Rounded.ArrowUpward
    val qr: ImageVector = Icons.Rounded.QrCode2
    val bell: ImageVector = Icons.Rounded.Notifications
    val search: ImageVector = Icons.Rounded.Search
    val settings: ImageVector = Icons.Rounded.Settings
    val wallet: ImageVector = Icons.Rounded.AccountBalanceWallet
    val money: ImageVector = Icons.Rounded.Money
    val bolt: ImageVector = Icons.Rounded.Bolt
    val flag: ImageVector = Icons.Rounded.Flag
    val send: ImageVector = Icons.Rounded.Send
    val phone: ImageVector = Icons.Rounded.Phone
    val camera: ImageVector = Icons.Rounded.CameraAlt
    val edit: ImageVector = Icons.Rounded.Edit
    val delete: ImageVector = Icons.Rounded.Delete
    val add: ImageVector = Icons.Rounded.Add
    val close: ImageVector = Icons.Rounded.Close
    val check: ImageVector = Icons.Rounded.Check
    val checkCircle: ImageVector = Icons.Rounded.CheckCircle
    val back: ImageVector = Icons.Rounded.ArrowBack
    val next: ImageVector = Icons.Rounded.ChevronRight
    val refresh: ImageVector = Icons.Rounded.Refresh
    val warning: ImageVector = Icons.Rounded.Warning
    val info: ImageVector = Icons.Rounded.Info
    val clock: ImageVector = Icons.Rounded.AccessTime
    val calendar: ImageVector = Icons.Rounded.CalendarToday
    val star: ImageVector = Icons.Rounded.Star
    val volume: ImageVector = Icons.Rounded.VolumeUp
    val menu: ImageVector = Icons.Rounded.Menu
    val lock: ImageVector = Icons.Rounded.Lock
    val shield: ImageVector = Icons.Rounded.Security
    val eye: ImageVector = Icons.Rounded.Visibility
    val eyeOff: ImageVector = Icons.Rounded.VisibilityOff
    val gear: ImageVector = Icons.Rounded.Settings
    val font: ImageVector = Icons.Rounded.FontDownload
    val sun: ImageVector = Icons.Rounded.WbSunny
    val palette: ImageVector = Icons.Rounded.Palette

    // ---- 天气现象图标（与小程序 weather.js ICONS 映射同源：晴/云/雨/雷/雪/雾 六类） ----
    val weatherSun: ImageVector = Icons.Rounded.WbSunny
    val weatherCloud: ImageVector = Icons.Rounded.Cloud
    val weatherRain: ImageVector = Icons.Rounded.Grain
    val weatherThunder: ImageVector = Icons.Rounded.Bolt
    val weatherSnow: ImageVector = Icons.Rounded.AcUnit
    val weatherFog: ImageVector = Icons.Rounded.Dehaze

    // ---- 展开 / 收起 ----
    val expandMore: ImageVector = Icons.Rounded.ExpandMore
    val expandLess: ImageVector = Icons.Rounded.ExpandLess

    // ---- 驾驶 / 导航（工作台固定操作栏与车道级导航页用） ----
    // 名字都在 material-icons-extended 里逐个核对过（直接读 AAR 的 classes.jar 枚举 `rounded/*Kt.class`，
    // 共 2083 个），不是凭印象写的；缺哪个只需要改本文件。
    val car: ImageVector = Icons.Rounded.DirectionsCar
    val speed: ImageVector = Icons.Rounded.Speed
    val traffic: ImageVector = Icons.Rounded.Traffic
    val laneSign: ImageVector = Icons.Rounded.Signpost
    val layers: ImageVector = Icons.Rounded.Layers
    val compass: ImageVector = Icons.Rounded.CompassCalibration
    val volumeOff: ImageVector = Icons.Rounded.VolumeOff
    val night: ImageVector = Icons.Rounded.NightlightRound
    val zoomIn: ImageVector = Icons.Rounded.ZoomInMap
    val stopCircle: ImageVector = Icons.Rounded.StopCircle
    val gpsFixed: ImageVector = Icons.Rounded.GpsFixed
    val rotateRight: ImageVector = Icons.Rounded.RotateRight

    /**
     * 天气现象 → 图标。映射规则与小程序 utils/weather.js ICONS 完全一致
     * （含 20+ 种现象名），未知现象回落「多云」而不是留空。
     */
    fun weatherOf(condition: String): ImageVector = when {
        condition.contains("雷") -> weatherThunder
        condition.contains("雪") || condition.contains("冰雹") -> weatherSnow
        condition.contains("雨") -> weatherRain
        condition.contains("晴") -> weatherSun
        condition.contains("雾") || condition.contains("霾") ||
            condition.contains("沙") || condition.contains("尘") -> weatherFog
        condition.contains("云") || condition.contains("阴") -> weatherCloud
        else -> weatherCloud
    }
}
