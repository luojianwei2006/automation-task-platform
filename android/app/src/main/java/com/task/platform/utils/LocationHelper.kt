package com.task.platform.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 定位工具类
 * 封装高德定位 SDK，提供位置获取、距离计算、地图导航功能
 */
object LocationHelper {

    private const val AMAP_PACKAGE = "com.autonavi.minimap"

    /**
     * 检查是否安装了高德地图 App
     */
    fun isAMapInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(AMAP_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 获取当前位置（Flow 方式）
     * @param context Context
     * @return Flow<Pair<Double, Double>> 返回 (纬度, 经度)
     */
    fun getCurrentLocation(context: Context): Flow<Pair<Double, Double>> = callbackFlow {
        val client = AMapLocationClient(context.applicationContext)
        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isOnceLocation = true
            isNeedAddress = false
        }
        client.setLocationOption(option)

        val listener = AMapLocationListener { location ->
            if (location != null && location.errorCode == 0) {
                trySend(Pair(location.latitude, location.longitude))
            } else {
                // 定位失败时 emit 哨兵值 (NaN, NaN)，让调用方知道定位失败
                trySend(Pair(Double.NaN, Double.NaN))
            }
            client.stopLocation()
            client.onDestroy()
            close()
        }
        client.setLocationListener(listener)
        client.startLocation()

        awaitClose {
            client.stopLocation()
            client.onDestroy()
        }
    }

    /**
     * 计算两个经纬度之间的距离（Haversine 公式）
     * @return 距离（米）
     */
    fun calculateDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val r = 6371000.0 // 地球半径（米）
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    /**
     * 检查是否在任务位置附近（50米范围内）
     */
    fun isNearTaskLocation(
        currentLat: Double, currentLng: Double,
        taskLat: Double, taskLng: Double,
        rangeInMeters: Double = 50.0
    ): Boolean {
        return calculateDistance(currentLat, currentLng, taskLat, taskLng) <= rangeInMeters
    }

    /**
     * 打开高德地图导航
     *
     * 优先级：
     *   1. 已安装高德 App → 直接唤起导航
     *   2. 未安装       → Toast 提示 + 打开高德网页版地图
     *
     * @param context   Context
     * @param lat       目标纬度
     * @param lng       目标经度
     * @param name      目标名称（位置描述）
     */
    fun openNavigation(context: Context, lat: Double, lng: Double, name: String) {
        // ---- 1. 尝试用高德 App 导航 ----
        if (isAMapInstalled(context)) {
            val uri = "androidamap://navi" +
                    "?sourceApplication=task_platform" +
                    "&lat=$lat" +
                    "&lon=$lng" +
                    "&dev=0" +          // 0=高德加密坐标(GCJ-02)，与服务端一致
                    "&style=0" +         // 0=驾车导航（最常用）
                    "&poiname=${Uri.encode(name)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                `package` = AMAP_PACKAGE
            }
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // 唤起失败，降级到网页版
            }
        }

        // ---- 2. 未安装或唤起失败 → 提示 + 网页版 ----
        val msg = if (isAMapInstalled(context)) {
            "高德地图唤起失败，正在打开网页版"
        } else {
            "未安装高德地图，正在打开网页版"
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

        val webUri = "https://uri.amap.com/marker" +
                "?position=$lng,$lat" +
                "&name=${Uri.encode(name)}" +
                "&src=task_platform"
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri))
        try {
            context.startActivity(webIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开地图，请安装高德地图", Toast.LENGTH_LONG).show()
        }
    }
}
