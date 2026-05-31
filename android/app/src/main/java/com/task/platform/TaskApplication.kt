package com.task.platform

import android.app.Application
import android.util.Log
import com.amap.api.services.core.ServiceSettings
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TaskApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ========== 高德地图 SDK 隐私合规（必须在使用任何地图接口前调用）==========
        // 方法在 com.amap.api.services.core.ServiceSettings 类
        //   updatePrivacyShow(ctx, isShow, isAgree)
        //     isShow   = true → 已向用户展示隐私政策
        //     isAgree = true → 用户已同意
        //   updatePrivacyAgree(ctx, isAgree)
        //     isAgree = true → 用户已同意
        //
        // ⚠️ 正式上架前需要：
        //   1. App 首次启动时弹窗展示《隐私政策》和《用户协议》
        //   2. 用户点击"同意"后，再调用这两个方法（均传 true）
        //   3. 用户拒绝则不应调用，且不能使用地图功能
        //   本处为开发阶段直接设为已同意，方便调试。
        try {
            ServiceSettings.updatePrivacyShow(this, true, true)
            ServiceSettings.updatePrivacyAgree(this, true)
            Log.d("TaskApplication", "AMap privacy compliance set via ServiceSettings")
        } catch (e: Exception) {
            Log.e("TaskApplication", "Failed to set AMap privacy compliance", e)
        }
    }
}
