package com.task.platform

/**
 * 应用级公共常量与工具。
 *
 * 这里集中存放「会被多模块引用」且与具体业务无关的常量，避免在各处重复硬编码
 * （尤其是模拟器专用地址，在真机/远程后端环境下会失效）。
 */
object ApiConstants {
    // 上传服务地址（upload-service，端口 8086）。
    // 默认使用模拟器专用地址（即宿主机的 localhost 映射），便于「模拟器 + 本机后端」开发调试。
    //
    // ⚠️ 真机 / 远程后端部署时必须修改，否则截图上传必失败。推荐方式（二选一）：
    //   1. 在 app/build.gradle.kts 的 defaultConfig 中注入 buildConfigField：
    //        buildConfigField("String", "UPLOAD_BASE_URL", "\"http://your-real-host:8086\"")
    //      然后将下面的默认值改为：
    //        const val UPLOAD_BASE_URL = BuildConfig.UPLOAD_BASE_URL
    //   2. 通过 productFlavors 为不同环境（dev / prod）提供不同地址。
    const val UPLOAD_BASE_URL = "http://10.0.2.2:8086"
}

/**
 * 把后端返回的 localhost / 127.0.0.1 重写为当前上传服务所在的 host。
 *
 * - 模拟器环境下 [ApiConstants.UPLOAD_BASE_URL] 取默认值（模拟器专用地址），重写为对应 host；
 * - 真机环境下 [ApiConstants.UPLOAD_BASE_URL] 改为实际后端域名后，自动重写到对应 host，避免破图。
 *
 * @param url 后端返回的图片 URL（可能为 localhost/127.0.0.1 开头的完整 URL）
 * @return 已重写 host 的图片 URL
 */
fun rewriteLocalImageUrl(url: String): String {
    val host = ApiConstants.UPLOAD_BASE_URL.substringAfter("://").substringBefore(":")
    return url.replace("localhost", host).replace("127.0.0.1", host)
}
