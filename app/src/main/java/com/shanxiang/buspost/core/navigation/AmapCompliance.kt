package com.shanxiang.buspost.core.navigation

import android.content.Context
import android.util.Log
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer

/**
 * 高德 SDK 的**隐私合规前置**。
 *
 * 高德在日志里明说（tag `E navi:` / `E 3dmap:`）：
 * > 确保调用 SDK 任何接口前先调用更新隐私合规 `updatePrivacyShow`、`updatePrivacyAgree`
 * > 两个接口并且参数值都为 true，若未正确设置有崩溃风险
 *
 * 本工程此前一次都没调用 —— `AmapNavigationEngine.prepare()` 直接 `AMapNavi.getInstance()`。
 * 这属于两轮验证都漏掉的真实缺陷：**它不只在模拟器上踩，真机上同样踩**。
 *
 * 为什么整段 try/catch(Throwable)：
 * 高德导航 SDK 只出 arm 库（jar 内 `lib/` 只有 arm64-v8a / armeabi-v7a，**无 x86/x86_64**）。
 * 在 x86_64 设备上若 App 被判定为 x86_64 且无 arm 转译，这两个类的 static 初始化就可能抛
 * `UnsatisfiedLinkError` —— 那属于"导航不可用"，**不该连累登录/业务页把 App 一起拖崩**。
 * 所以这里只做"尽力设置"，失败留日志，可用性判断仍交给 `NavigationEngine.readiness`。
 */
object AmapCompliance {

    private const val TAG = "BUS_NAV"

    fun apply(context: Context) {
        // 1) 地图/导航（AMapNavi 走这一套）
        try {
            MapsInitializer.updatePrivacyShow(context, true, true)
            MapsInitializer.updatePrivacyAgree(context, true)
            Log.i(TAG, "AMap 隐私合规已设置：MapsInitializer.updatePrivacyShow/Agree = true")
        } catch (t: Throwable) {
            Log.w(TAG, "AMap MapsInitializer 隐私合规设置失败（导航将不可用，不影响其它功能）", t)
        }
        // 2) 定位：导航内部与 Locator 都会用到定位，单独一套合规接口
        try {
            AMapLocationClient.updatePrivacyShow(context, true, true)
            AMapLocationClient.updatePrivacyAgree(context, true)
            Log.i(TAG, "AMap 隐私合规已设置：AMapLocationClient.updatePrivacyShow/Agree = true")
        } catch (t: Throwable) {
            Log.w(TAG, "AMap AMapLocationClient 隐私合规设置失败（定位/导航将不可用，不影响其它功能）", t)
        }
    }
}
