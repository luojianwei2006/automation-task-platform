package com.task.platform.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.task.platform.model.AutoTask
import com.task.platform.network.ApiClient
import kotlinx.coroutines.*
import kotlin.random.Random
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 微信视频号自动化引擎
 *
 * 通过 AccessibilityService 模拟用户操作：
 * 1. 打开微信（若未打开则启动，已打开则回到微信）
 * 2. 进入发现 → 视频号
 * 3. 搜索目标账号/关键词
 * 4. 进入账号首页 → 点第一个视频
 * 5. 点赞 / 评论
 * 6. 截图 + 返回应用
 */
class WechatVideoAutomator(
    private val service: AutomationService
) {
    companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"

        // 重试参数
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 800L

        // 延迟范围（毫秒）
        private const val MIN_STEP_DELAY = 800L
        private const val MAX_STEP_DELAY = 1500L
        private const val MIN_APP_WAIT = 1500L
        private const val MAX_APP_WAIT = 2500L
        private const val MIN_RESULT_WAIT = 2000L
        private const val MAX_RESULT_WAIT = 3500L

        // ─── 微信 ViewId 常量 ───────────────────────
        // 底部Tab
        private val TAB_DISCOVER_TEXTS = listOf("发现", "Discover")
        private val TAB_VIDEO_CHANNEL_TEXTS = listOf("视频号", "视频", "Channels")

        // 搜索
        private val SEARCH_ICON_TEXTS = listOf("搜索", "Search", "发现")
        private val SEARCH_ICON_DESCS = listOf("搜索", "search", "Search", "discover")
        private val SEARCH_BUTTON_TEXTS = listOf("搜索", "Search")

        // 点赞
        private val LIKE_TEXTS = listOf("赞", "点赞", "Like", "❤️")
        private val LIKE_DESCS = listOf("赞", "Like", "like", "heart")

        // 评论
        private val COMMENT_POST_TEXTS = listOf("发送", "Send", "发布", "评论")
    }

    @Volatile
    private var cancelled = false
    private var currentTask: AutoTask? = null

    fun cancel() {
        cancelled = true
    }

    // ─── 主流程 ──────────────────────────────────────────

    fun execute(task: AutoTask) {
        android.util.Log.d("WechatVAM", "=== 视频号自动化开始: platform=${task.platform}, taskType=${task.taskType} ===")
        cancelled = false
        currentTask = task

        try {
            // Step 1: 打开微信
            notifyStep("open_app", "正在打开微信...", 0)
            if (!openWechat()) {
                notifyStepComplete("open_app", "打开微信", 2, "无法打开")
                return
            }
            randomDelay(MIN_APP_WAIT, MAX_APP_WAIT)
            if (cancelled) return
            notifyStepComplete("open_app", "打开微信", 1, "成功打开")

            // Step 2: 进入视频号
            notifyStep("enter_video_channel", "正在进入视频号...", 0)
            if (!enterVideoChannel()) {
                notifyStepComplete("enter_video_channel", "进入视频号", 2, "无法进入")
                return
            }
            randomDelay(MIN_RESULT_WAIT, MAX_RESULT_WAIT)
            if (cancelled) return
            notifyStepComplete("enter_video_channel", "进入视频号", 1, "已进入视频号")

            // Step 3: 搜索
            val searchKeyword = extractSearchKeyword(task)
            notifyStep("search", "搜索: $searchKeyword", 0)
            if (!navigateToSearch()) {
                notifyStepComplete("search", "搜索: $searchKeyword", 2, "无法进入搜索")
                return
            }
            randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
            if (!performSearch(searchKeyword)) {
                notifyStepComplete("search", "搜索: $searchKeyword", 2, "搜索失败")
                return
            }
            notifyStepComplete("search", "搜索: $searchKeyword", 1, "搜索成功")
            randomDelay(MIN_RESULT_WAIT, MAX_RESULT_WAIT)
            if (cancelled) return

            // Step 4: 进入账号 → 等列表加载 → 点击第一个视频
            notifyStep("enter_account", "正在进入账号...", 0)
            if (!enterFirstAccount()) {
                notifyStepComplete("enter_account", "进入账号", 2, "无法进入账号")
                return
            }
            randomDelay(8000, 12000)  // 等账号主页 + 视频列表加载
            if (cancelled) return

            dumpAllNodes("账号主页视频列表")
            if (cancelled) return
            if (!clickFirstVideo()) {
                notifyStepComplete("enter_account", "进入视频", 2, "无法打开视频")
                return
            }
            randomDelay(3000, 5000)  // 等视频播放页加载
            if (cancelled) return
            notifyStepComplete("enter_account", "进入视频", 1, "已进入视频")

            // Step 5: 执行任务操作（点赞/评论）
            var commentText: String? = null
            when (task.taskType) {
                1 -> {
                    // 仅点赞
                    notifyStep("like", "正在点赞...", 0)
                    randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
                    if (!performLike()) {
                        notifyStepComplete("like", "点赞", 2, "点赞失败")
                        return
                    }
                    notifyStepComplete("like", "点赞", 1, "点赞成功")
                }
                2 -> {
                    // 点赞 + 评论
                    notifyStep("like", "正在点赞...", 0)
                    randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
                    if (!performLike()) {
                        notifyStepComplete("like", "点赞", 2, "点赞失败")
                        return
                    }
                    notifyStepComplete("like", "点赞", 1, "点赞成功")
                    randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
                    if (cancelled) return

                    notifyStep("fetch_words", "正在获取评论词...", 0)
                    commentText = fetchCommentWord(task)
                    if (commentText == null) {
                        notifyStepComplete("fetch_words", "获取评论词", 2, "无可用评论词")
                        return
                    }
                    notifyStepComplete("fetch_words", "评论词: $commentText", 1, commentText!!)
                }
                else -> {
                    notifyStepComplete("action", "未知类型", 2, "不支持: ${task.taskType}")
                    return
                }
            }
            randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
            if (cancelled) return

            // Step 6: 评论（仅 taskType=2）
            if (task.taskType == 2 && commentText != null) {
                notifyStep("comment", "正在评论: $commentText", 0)
                randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
                if (!performComment(commentText)) {
                    notifyStepComplete("comment", "评论", 2, "评论失败")
                    return
                }
                randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
                notifyStepComplete("comment", "评论", 1, "评论成功")
            }

            // Step 7: 截图
            notifyStep("screenshot", "正在截图...", 0)
            val localFile = takeScreenshot()
            if (localFile != null) {
                notifyStepComplete("screenshot", "截图保存成功", 1, localFile)
                AutomationOverlayService.updateComplete(true)
            } else {
                notifyStepComplete("screenshot", "截图失败", 2, "无法截图")
                AutomationService.onActionResult?.invoke(false, "✗ 截图失败")
                AutomationOverlayService.updateComplete(false)
            }

            // Step 8: 关闭微信，返回上传截图界面
            notifyStep("close_app", "正在关闭微信...", 0)
            closeWechat()
            randomDelay(800, 1200)
            notifyStepComplete("close_app", "关闭微信", 1, "已关闭")

            returnToApp()
            AutomationService.onActionResult?.invoke(true, "✓ 自动化任务执行完成 — UPLOAD:${task.taskId}")
        } catch (e: Exception) {
            android.util.Log.e("WechatVAM", "执行异常", e)
            AutomationService.onActionResult?.invoke(false, "执行异常: ${e.message}")
            AutomationOverlayService.updateComplete(false)
        } finally {
            AutomationService.setRunning(false)
        }
    }

    // ─── 微信操作 ──────────────────────────────────────────

    private fun openWechat(): Boolean {
        return run {
            try {
                android.util.Log.d("WechatVAM", "正在打开微信, 包名=$WECHAT_PACKAGE")

                // 尝试1: getLaunchIntentForPackage
                val intent = service.packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    service.startActivity(intent)
                    android.util.Log.d("WechatVAM", "微信已启动(launchIntent)")
                    return@run true
                }

                // 尝试2: 直接 setClassName 指定已知的启动 Activity
                val knownActivities = listOf(
                    "com.tencent.mm.ui.LauncherUI",
                    "com.tencent.mm.app.WeChatSplashActivity",
                    "com.tencent.mm.app.MMApplication"
                )
                for (activityName in knownActivities) {
                    try {
                        val explicitIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                            setClassName(WECHAT_PACKAGE, activityName)
                            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        service.startActivity(explicitIntent)
                        android.util.Log.d("WechatVAM", "微信已启动(explicit: $activityName)")
                        return@run true
                    } catch (_: Exception) {
                        android.util.Log.d("WechatVAM", "explicit $activityName 失败")
                    }
                }

                // 尝试3: queryIntentActivities 以 WECHAT_PACKAGE 过滤
                android.util.Log.d("WechatVAM", "尝试 queryIntentActivities 定位微信...")
                val pm = service.packageManager
                val queryIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                val riList = pm.queryIntentActivities(queryIntent, 0)
                var wechatPkg: String? = null
                var wechatActivity: String? = null
                val allApps = mutableListOf<Pair<String, String>>()

                for (ri in riList) {
                    val pkg = ri.activityInfo.packageName
                    val label = ri.loadLabel(pm).toString()
                    allApps.add(pkg to label)

                    if (pkg == WECHAT_PACKAGE || pkg.lowercase().contains("tencent")) {
                        wechatPkg = pkg
                        wechatActivity = ri.activityInfo.name
                        android.util.Log.d("WechatVAM", "  ✅ 找到微信: $label → $pkg/${ri.activityInfo.name}")
                    }
                }

                if (wechatPkg != null && wechatActivity != null) {
                    val explicitIntent = android.content.Intent().apply {
                        setClassName(wechatPkg!!, wechatActivity!!)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        service.startActivity(explicitIntent)
                        android.util.Log.d("WechatVAM", "微信已启动(explicit from query)")
                        return@run true
                    } catch (e: Exception) {
                        android.util.Log.e("WechatVAM", "explicit启动失败", e)
                    }
                }

                // 兜底 dump
                android.util.Log.e("WechatVAM", "无法启动微信，列出所有桌面应用(${allApps.size}个)：")
                for ((pkg, label) in allApps.sortedBy { it.first }) {
                    android.util.Log.d("WechatVAM", "  $pkg  [$label]")
                }
                false
            } catch (e: Exception) {
                android.util.Log.e("WechatVAM", "打开微信失败", e)
                false
            }
        }
    }

    /* 进入视频号：底部 Tab → "发现" → "视频号"。
     * 微信不开放无障碍树 → 文本查找失败时自动降级为坐标点击 */
    private fun enterVideoChannel(): Boolean {
        val screenW = service.resources.displayMetrics.widthPixels.toFloat()
        val screenH = service.resources.displayMetrics.heightPixels.toFloat()
        var treeUsable = isTreeUsable()

        // 1. 点"发现" tab
        if (treeUsable) {
            val tabResult = retryFindNode({
                findNodeByText(TAB_DISCOVER_TEXTS)
            }) { tab ->
                dispatchTapOnNode(tab)
                android.util.Log.d("WechatVAM", "点击底部Tab: 发现(文本)")
            }
            if (!tabResult) {
                android.util.Log.d("WechatVAM", "文本未找到'发现'，降级为坐标")
                treeUsable = false
            }
        }

        if (!treeUsable) {
            // 微信底部4个Tab：发现是第3个，约 x=62.5%
            android.util.Log.d("WechatVAM", "坐标点击 发现 tab: x=${screenW * 0.625f}, y=${screenH * 0.98f}")
            dispatchTap(screenW * 0.625f, screenH * 0.98f)
        }

        randomDelay(2000, 3000)
        if (cancelled) return false
        dumpAllNodes("发现页")

        // 2. 点"视频号" entry
        if (isTreeUsable()) {
            val result = retryFindNode({
                findNodeByText(TAB_VIDEO_CHANNEL_TEXTS)
            }) { node ->
                dispatchTapOnNode(node)
                android.util.Log.d("WechatVAM", "点击: 视频号(文本)")
            }
            if (result) return true
            android.util.Log.d("WechatVAM", "文本未找到'视频号'，降级为坐标")
        }

        // 坐标兜底：视频号通常在发现页上方区域（朋友圈下面）
        // 两个候选位置：先试上方，不行滑动后再试
        val candidateYs = floatArrayOf(screenH * 0.19f, screenH * 0.25f, screenH * 0.30f)
        for (y in candidateYs) {
            android.util.Log.d("WechatVAM", "坐标点击视频号: y=${y}")
            dispatchTap(screenW * 0.5f, y)
            randomDelay(1000, 2000)
            if (cancelled) return false
        }

        // 滑动一下发现页（视频号可能在下方），重新试
        android.util.Log.d("WechatVAM", "滑动发现页后重试...")
        val path = android.graphics.Path().apply {
            moveTo(screenW * 0.5f, screenH * 0.75f)
            lineTo(screenW * 0.5f, screenH * 0.25f)
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 600))
            .build()
        service.dispatchGesture(gesture, null, null)
        randomDelay(1500, 2500)

        for (y in candidateYs) {
            dispatchTap(screenW * 0.5f, y)
            randomDelay(1000, 2000)
            if (cancelled) return false
        }

        // 坐标兜底完成，假定点击成功继续后续流程
        android.util.Log.d("WechatVAM", "视频号坐标兜底完成，继续后续流程")
        return true
    }

    /** 检查无障碍树是否可用（非空且至少有实际内容节点） */
    private fun isTreeUsable(): Boolean {
        val root = service.rootInActiveWindow ?: return false
        try {
            if (root.childCount == 0) return false
            // 递归计数（最多查2层）
            var total = 1
            for (i in 0 until root.childCount) {
                val child = root.getChild(i)
                if (child != null) {
                    total++
                    total += child.childCount
                    child.recycle()
                }
            }
            return total > 2  // 根节点 + 至少2个实际节点才认为可用
        } finally {
            root.recycle()
        }
    }

    private fun navigateToSearch(): Boolean {
        return retryFindNode({
            findNodeByText(SEARCH_ICON_TEXTS) ?: findNodeByDesc(SEARCH_ICON_DESCS)
        }) { node ->
            val clickable = if (node.isClickable) node else findClickableAncestor(node)
            clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ?: dispatchTapOnNode(node)
            android.util.Log.d("WechatVAM", "点击搜索图标")
        }
    }

    private fun performSearch(keyword: String): Boolean {
        // 找到输入框并输入
        val hasInput = retryFindNode({
            findEditableNode()
        }) { node ->
            val setArgs = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, keyword)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
            android.util.Log.d("WechatVAM", "输入搜索词: $keyword")
            node.recycle()
        }
        if (!hasInput) {
            android.util.Log.e("WechatVAM", "找不到搜索输入框")
            return false
        }

        randomDelay(500, 1000)

        // 点搜索按钮
        return retryFindNode({
            findNodeByText(SEARCH_BUTTON_TEXTS)
        }) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            android.util.Log.d("WechatVAM", "点击搜索按钮")
        }
    }

    /* 点搜索结果中第一个账号（排除广告等无关节点） */
    private fun enterFirstAccount(): Boolean {
        val screenH = service.resources.displayMetrics.heightPixels
        val headerEnd = (screenH * 0.12).toInt()
        return retryFindNode({
            val root = service.rootInActiveWindow ?: return@retryFindNode null
            val result = findFirstAccountResult(root, headerEnd, screenH)
            if (result != root) root.recycle()
            result
        }) { node ->
            android.util.Log.d("WechatVAM", "点击搜索结果第一个账号: ${node.contentDescription} ${node.text}")
            dispatchTapOnNode(node)
        }
    }

    private fun findFirstAccountResult(node: AccessibilityNodeInfo, minTop: Int, maxBottom: Int): AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()

        // 排除无关按钮
        val isBad = text.contains("广告") || desc.contains("广告") ||
                    text.contains("直播") || desc.contains("直播")
        val heightOk = rect.height() in 60..400

        if (!isBad && node.isClickable && heightOk && rect.top >= minTop && rect.bottom <= maxBottom) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstAccountResult(child, minTop, maxBottom)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    /* 在账号主页点第一个视频 */
    private fun clickFirstVideo(): Boolean {
        val screenH = service.resources.displayMetrics.heightPixels
        val headerEnd = (screenH * 0.15).toInt()

        return retryFindNode({
            val root = service.rootInActiveWindow ?: return@retryFindNode null
            val result = findFirstVideoNode(root, headerEnd, screenH)
            if (result != root) root.recycle()
            result
        }) { node ->
            android.util.Log.d("WechatVAM", "点击第一个视频: ${node.contentDescription}")
            dispatchTapOnNode(node)
        }
    }

    private fun findFirstVideoNode(node: AccessibilityNodeInfo, minTop: Int, maxBottom: Int): AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()

        val isVideo = desc.contains("视频") || desc.contains("Video") ||
                      rect.height() in 150..600
        val isBad = text.contains("直播") || desc.contains("直播")

        if (!isBad && node.isClickable && isVideo && rect.top >= minTop && rect.bottom <= maxBottom) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstVideoNode(child, minTop, maxBottom)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    // ─── 点赞 ──────────────────────────────────────────────

    private fun performLike(): Boolean {
        dumpAllNodes("视频页")

        // 1. 检查是否已点赞
        val alreadyLiked = run {
            val root = service.rootInActiveWindow ?: return@run null
            val result = findNodeRecursive(root) { node ->
                if (!node.isClickable) return@findNodeRecursive false
                val desc = node.contentDescription?.toString().orEmpty()
                val text = node.text?.toString().orEmpty()
                desc.contains("已赞") || text.contains("已赞") || desc.contains("liked")
            }
            root.recycle()
            result
        }
        if (alreadyLiked != null) {
            android.util.Log.d("WechatVAM", "已点赞，跳过")
            alreadyLiked.recycle()
            return true
        }

        // 2. 在底部区域找点赞按钮（y > 60% 屏高）
        val screenH = service.resources.displayMetrics.heightPixels
        val bottomThreshold = (screenH * 0.6).toInt()

        return retryFindNode({
            findNodeByTextInRegion(LIKE_TEXTS, bottomThreshold, screenH)
                ?: findNodeByDescInRegion(LIKE_DESCS, bottomThreshold, screenH)
        }) { node ->
            val clickable = if (node.isClickable) node else findClickableAncestor(node)
            clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            randomDelay(500, 1000)
            android.util.Log.d("WechatVAM", "点赞完成")
        }
    }

    // ─── 评论 ──────────────────────────────────────────────

    private fun performComment(commentText: String): Boolean {
        android.util.Log.d("WechatVAM", "=== 开始评论: $commentText ===")

        val screenW = service.resources.displayMetrics.widthPixels
        val screenH = service.resources.displayMetrics.heightPixels
        val bottomThreshold = (screenH * 0.6).toInt()
        var inputBounds: android.graphics.Rect? = null

        // Step 1: 先尝试直接找 EditText
        retryFindNode({
            findEditableNodeAtBottom(bottomThreshold)
        }) { node ->
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            inputBounds = android.graphics.Rect(r)
            android.util.Log.d("WechatVAM", "直接找到评论输入框: bounds=$r")
            node.recycle()
        }

        // Step 2: 找不到 → 点击底部区域激活
        if (inputBounds == null) {
            android.util.Log.d("WechatVAM", "未找到 EditText，点击底部区域激活输入...")
            dispatchTap(screenW * 0.5f, screenH * 0.95f)
            randomDelay(1000, 1800)

            retryFindNode({
                findEditableNodeAtBottom(bottomThreshold) ?: findEditableNode()
            }) { node ->
                val r = android.graphics.Rect()
                node.getBoundsInScreen(r)
                inputBounds = android.graphics.Rect(r)
                android.util.Log.d("WechatVAM", "激活后找到输入框: bounds=$r")
                node.recycle()
            }
        }

        // Step 3: 找评论按钮
        if (inputBounds == null) {
            android.util.Log.d("WechatVAM", "仍未找到，尝试点击评论按钮...")
            val screenH2 = service.resources.displayMetrics.heightPixels
            val bottomBtnThreshold = (screenH2 * 0.85).toInt()

            retryFindNode({
                val root = service.rootInActiveWindow ?: return@retryFindNode null
                val result = findNodeByDescInRegion(
                    listOf("评论", "Comment", "comment", "Cmt"),
                    bottomBtnThreshold, screenH2
                )
                if (result != root) root.recycle()
                result
            }) { commentBtn ->
                val r = android.graphics.Rect()
                commentBtn.getBoundsInScreen(r)
                dispatchTap(r.exactCenterX(), r.exactCenterY())
                commentBtn.recycle()
            }
            randomDelay(2000, 3000)

            dumpAllNodes("评论面板")

            // 搜索所有窗口
            retryFindNode({
                findEditableInAllWindows() ?: findEditableNode()
            }) { node ->
                val r = android.graphics.Rect()
                node.getBoundsInScreen(r)
                inputBounds = android.graphics.Rect(r)
                android.util.Log.d("WechatVAM", "评论面板找到输入框: bounds=$r")
                node.recycle()
            }
        }

        if (inputBounds == null) {
            android.util.Log.e("WechatVAM", "找不到评论输入框，尝试剪贴板粘贴兜底...")
            var pasteWorked = false
            try {
                val cm = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("comment", commentText))
                randomDelay(300, 500)
                dispatchTap(screenW * 0.5f, screenH * 0.95f)
                randomDelay(800, 1200)
                val root2 = service.rootInActiveWindow
                if (root2 != null) {
                    pasteIntoFocused(root2)
                    root2.recycle()
                }
                randomDelay(600, 1000)
                pasteWorked = true
            } catch (e: Exception) {
                android.util.Log.w("WechatVAM", "粘贴兜底异常: ${e.message}")
            }
            if (!pasteWorked) return false
        }

        // Step 4: 有输入框 → 点击获焦 → 输入
        if (inputBounds != null) {
            val cx = inputBounds.centerX().toFloat()
            val cy = inputBounds.centerY().toFloat()
            dispatchTap(cx, cy)
            randomDelay(700, 1100)

            var clicked = 0
            for (ch in commentText) {
                if (cancelled) return false
                val node = findKeyboardKey(ch.toString())
                if (node != null) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                    clicked++
                    randomDelay(50, 110)
                }
            }
            android.util.Log.d("WechatVAM", "键盘节点: $clicked/${commentText.length}")

            if (clicked == 0) {
                retryFindNode({
                    findEditableNodeAtBottom(bottomThreshold) ?: findEditableNode()
                }) { node ->
                    val setArgs = android.os.Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, commentText)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
                    node.recycle()
                }
                randomDelay(400, 700)

                try {
                    val cm = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("comment", commentText))
                    randomDelay(200, 400)
                    retryFindNode({ findEditableNodeAtBottom(bottomThreshold) ?: findEditableNode() }) { pasteNode ->
                        pasteNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        randomDelay(100, 200)
                        pasteNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                        pasteNode.recycle()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("WechatVAM", "粘贴异常: ${e.message}")
                }
            }
        }

        // Step 5: 点发送
        randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
        return retryFindNode({
            findNodeByText(COMMENT_POST_TEXTS)
        }) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            android.util.Log.d("WechatVAM", "评论已发送")
            randomDelay(1000, 2000)
        }
    }

    // ─── 截图 ──────────────────────────────────────────────

    private fun takeScreenshot(): String? {
        android.util.Log.d("WechatVAM", "=== 开始截图 ===")
        val appDir = service.getExternalFilesDir(null)
        val screenshotDir = if (appDir != null) {
            java.io.File(appDir, "screenshots").also { it.mkdirs() }
        } else {
            java.io.File(service.filesDir, "screenshots").also { it.mkdirs() }
        }
        val file = java.io.File(screenshotDir, "wechat_${System.currentTimeMillis()}.png")

        // 策略1: API 34+ takeScreenshot
        if (Build.VERSION.SDK_INT >= 34) {
            val latch = CountDownLatch(1)
            var bitmap: Bitmap? = null
            try {
                val displayId = service.getSystemService(android.content.Context.WINDOW_SERVICE)
                    .let { it as android.view.WindowManager }
                    .let { it.defaultDisplay.displayId }
                service.takeScreenshot(displayId, service.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                            bitmap = Bitmap.wrapHardwareBuffer(
                                screenshotResult.hardwareBuffer, screenshotResult.colorSpace
                            )
                            screenshotResult.hardwareBuffer.close()
                            latch.countDown()
                        }
                        override fun onFailure(errorCode: Int) {
                            android.util.Log.e("WechatVAM", "takeScreenshot 失败, code=$errorCode")
                            latch.countDown()
                        }
                    })
                if (latch.await(5, TimeUnit.SECONDS) && bitmap != null) {
                    FileOutputStream(file).use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 90, it) }
                    bitmap!!.recycle()
                    android.util.Log.d("WechatVAM", "截图成功(API34): ${file.absolutePath}")
                    return file.absolutePath
                }
            } catch (e: Exception) {
                android.util.Log.e("WechatVAM", "takeScreenshot 异常", e)
            }
        }

        // 策略2: GLOBAL_ACTION_TAKE_SCREENSHOT
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            Thread.sleep(3000)

            val screenshotDirs = listOf(
                "/sdcard/Pictures/Screenshots",
                "/sdcard/DCIM/Screenshots",
                "/sdcard/Screenshots",
                "/sdcard/Pictures",
                "/sdcard/DCIM",
                "/sdcard/Download",
                "/data/media/0/Pictures/Screenshots",
                "/data/media/0/DCIM/Screenshots",
                "/mnt/sdcard/Pictures/Screenshots",
            )

            var newestFile: java.io.File? = null
            var newestTime = 0L
            for (dirPath in screenshotDirs) {
                val dir = java.io.File(dirPath)
                if (!dir.exists() || !dir.isDirectory) continue
                dir.listFiles()?.filter { it.isFile && it.name.endsWith(".png", true) }?.forEach { f ->
                    if (f.lastModified() > newestTime) {
                        newestTime = f.lastModified()
                        newestFile = f
                    }
                }
            }

            if (newestFile != null && newestTime > System.currentTimeMillis() - 30000) {
                newestFile!!.copyTo(file, overwrite = true)
                android.util.Log.d("WechatVAM", "截图成功(GLOBAL_ACTION): ${file.absolutePath}")
                return file.absolutePath
            }
        } catch (e: Exception) {
            android.util.Log.e("WechatVAM", "GLOBAL_ACTION 截图异常", e)
        }

        // 策略3: screencap
        try {
            var pngBytes: ByteArray? = null
            val screencapCmds = listOf("screencap", "/system/bin/screencap")
            for (cmd in screencapCmds) {
                try {
                    val process = Runtime.getRuntime().exec(arrayOf(cmd, "-p"))
                    pngBytes = process.inputStream.readBytes()
                    process.waitFor()
                    if (pngBytes.isNotEmpty() && pngBytes[0] == 0x89.toByte() && pngBytes[1] == 0x50.toByte()) break
                    pngBytes = null
                } catch (_: Exception) {}
            }

            if (pngBytes != null) {
                file.writeBytes(pngBytes)
                android.util.Log.d("WechatVAM", "截图成功(screencap): ${file.absolutePath}")
                return file.absolutePath
            }
        } catch (e: Exception) {
            android.util.Log.e("WechatVAM", "screencap 截图异常", e)
        }

        return null
    }

    // ─── 离开 ──────────────────────────────────────────────

    private fun closeWechat() {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            Thread.sleep(500)
            repeat(3) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                Thread.sleep(300)
            }
            android.util.Log.d("WechatVAM", "已关闭微信")
        } catch (e: Exception) {
            android.util.Log.e("WechatVAM", "关闭微信失败", e)
        }
    }

    private fun returnToApp() {
        try {
            val intent = service.packageManager.getLaunchIntentForPackage(service.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                service.startActivity(intent)
            }
        } catch (_: Exception) {}
    }

    // ─── 评论词 ────────────────────────────────────────────

    private fun extractSearchKeyword(task: AutoTask): String {
        return task.requirements?.take(30) ?: task.targetUrl?.take(30) ?: "视频号"
    }

    private fun fetchCommentWord(task: AutoTask): String? {
        return try {
            val ids = task.commentCategoryIds ?: ""
            val resp = kotlinx.coroutines.runBlocking {
                ApiClient.apiService.getCommentWords(ids)
            }
            resp.data?.firstOrNull()
        } catch (e: Exception) {
            android.util.Log.e("WechatVAM", "获取评论词失败", e)
            null
        }
    }

    // ─── 进度上报 ──────────────────────────────────────────

    private fun notifyStep(stepId: String, stepName: String, status: Int) {
        AutomationOverlayService.updateStep(stepName, "进行中...")
    }

    private fun notifyStepComplete(stepId: String, stepName: String, status: Int, result: String) {
        AutomationOverlayService.updateStep(stepName, result)
        reportToServer(stepId, stepName, status, result)

        val statusText = when (status) {
            1 -> "✓"
            2 -> "✗"
            else -> "…"
        }
        AutomationService.onActionResult?.invoke(
            status == 1 || status == 0,
            "$statusText $stepId: $stepName — $result"
        )
    }

    private fun reportToServer(stepId: String, action: String, status: Int, result: String) {
        try {
            val task = currentTask ?: return
            val body = mapOf(
                "userId" to task.userId,
                "taskId" to task.taskId,
                "step" to stepId,
                "action" to action,
                "status" to status,
                "result" to result
            )
            kotlinx.coroutines.runBlocking {
                ApiClient.apiService.saveAutoRecord(body)
            }
        } catch (_: Exception) {}
    }

    // ─── 通用工具 ──────────────────────────────────────────

    private fun dispatchTap(x: Float, y: Float) {
        val path = android.graphics.Path().apply { moveTo(x, y) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun dispatchTapOnNode(node: AccessibilityNodeInfo) {
        val r = android.graphics.Rect()
        node.getBoundsInScreen(r)
        dispatchTap(r.exactCenterX(), r.exactCenterY())
    }

    private fun findKeyboardKey(char: String): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val metrics = service.resources.displayMetrics
        val threshold = (metrics.heightPixels * 0.55).toInt()
        return try {
            findKeyboardKeyRecursive(root, char, threshold)
        } finally {
            root.recycle()
        }
    }

    private fun findKeyboardKeyRecursive(
        node: AccessibilityNodeInfo, target: String, threshold: Int
    ): AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        if ((text.equals(target, ignoreCase = true) || desc.equals(target, ignoreCase = true))
            && rect.top >= threshold && node.isClickable
        ) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findKeyboardKeyRecursive(child, target, threshold)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun randomDelay(minMs: Long, maxMs: Long) {
        Thread.sleep(Random.nextLong(minMs, maxMs + 1))
    }

    // ─── 节点查找工具 ─────────────────────────────────────

    private fun retryFindNode(
        finder: () -> AccessibilityNodeInfo?,
        action: (AccessibilityNodeInfo) -> Unit
    ): Boolean {
        for (i in 0 until MAX_RETRIES) {
            if (cancelled) return false
            val node = finder()
            if (node != null) {
                action(node)
                return true
            }
            if (i < MAX_RETRIES - 1) Thread.sleep(RETRY_DELAY_MS)
        }
        return false
    }

    private fun findNodeByText(texts: List<String>): AccessibilityNodeInfo? {
        // 先在当前活动窗口找
        val root = service.rootInActiveWindow
        if (root != null) {
            val result = findNodeByTextRecursive(root, texts)
            if (result != null) {
                if (result != root) root.recycle()
                return result
            }
            // 如果活动窗口只有1个空节点，尝试其他窗口
            if (root.childCount == 0) {
                root.recycle()
                return findNodeByTextInAllWindows(texts)
            }
            root.recycle()
            return null
        }
        return findNodeByTextInAllWindows(texts)
    }

    private fun findNodeByTextInAllWindows(texts: List<String>): AccessibilityNodeInfo? {
        val windows = service.windows ?: return null
        for (window in windows) {
            val root = window.root ?: continue
            val result = findNodeByTextRecursive(root, texts)
            if (result != null) {
                for (other in windows) {
                    if (other !== window) other.root?.recycle()
                }
                return result
            }
            root.recycle()
        }
        return null
    }

    private fun findNodeByTextRecursive(node: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        if (texts.any { nodeText.contains(it, ignoreCase = true) }) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByTextRecursive(child, texts)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findNodeByDesc(descs: List<String>): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val result = findNodeByDescRecursive(root, descs)
        if (result != root) root.recycle()
        return result
    }

    private fun findNodeByDescRecursive(node: AccessibilityNodeInfo, descs: List<String>): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString() ?: ""
        if (descs.any { desc.contains(it, ignoreCase = true) }) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByDescRecursive(child, descs)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findNodeByTextInRegion(texts: List<String>, minTop: Int, maxBottom: Int): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val result = findNodeByTextInRegionRecursive(root, texts, minTop, maxBottom)
        if (result != root) root.recycle()
        return result
    }

    private fun findNodeByTextInRegionRecursive(
        node: AccessibilityNodeInfo, texts: List<String>, minTop: Int, maxBottom: Int
    ): AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val nodeText = node.text?.toString() ?: ""

        if (rect.bottom >= minTop && rect.top <= maxBottom &&
            texts.any { nodeText.contains(it, ignoreCase = true) }
        ) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByTextInRegionRecursive(child, texts, minTop, maxBottom)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findNodeByDescInRegion(descs: List<String>, minTop: Int, maxBottom: Int): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val result = findNodeByDescInRegionRecursive(root, descs, minTop, maxBottom)
        if (result != root) root.recycle()
        return result
    }

    private fun findNodeByDescInRegionRecursive(
        node: AccessibilityNodeInfo, descs: List<String>, minTop: Int, maxBottom: Int
    ): AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val desc = node.contentDescription?.toString() ?: ""

        if (rect.bottom >= minTop && rect.top <= maxBottom &&
            descs.any { desc.contains(it, ignoreCase = true) }
        ) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByDescInRegionRecursive(child, descs, minTop, maxBottom)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node.parent
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun findNodeRecursive(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeRecursive(child, predicate)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findEditableNode(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val result = findEditableRecursive(root)
        if (result != root) root.recycle()
        return result
    }

    private fun findEditableRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableRecursive(child)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findEditableNodeAtBottom(threshold: Int): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val result = findEditableAtBottomRecursive(root, threshold)
        if (result != root) root.recycle()
        return result
    }

    private fun findEditableAtBottomRecursive(node: AccessibilityNodeInfo, threshold: Int): AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (node.isEditable && rect.top >= threshold) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableAtBottomRecursive(child, threshold)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findEditableInAllWindows(): AccessibilityNodeInfo? {
        val windows = service.windows ?: return null
        for (window in windows) {
            val root = window.root ?: continue
            val found = findEditableRecursive(root)
            if (found != null) {
                for (other in windows) {
                    if (other !== window) other.root?.recycle()
                }
                return found
            }
            root.recycle()
        }
        return null
    }

    private fun pasteIntoFocused(root: AccessibilityNodeInfo) {
        val focused = findFocusedRecursive(root)
        if (focused != null) {
            try {
                focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            } catch (e: Exception) {
                android.util.Log.w("WechatVAM", "PASTE 异常: ${e.message}")
            }
            focused.recycle()
        }
    }

    private fun findFocusedRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused || node.isEditable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFocusedRecursive(child)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    /** dump 当前页面所有可见节点 */
    private fun dumpAllNodes(label: String) {
        val windows = service.windows
        if (windows != null) {
            for (w in windows) {
                val root = w.root ?: continue
                try {
                    val nodes = mutableListOf<String>()
                    collectVisibleNodes(root, 0, nodes)
                    val wr = android.graphics.Rect()
                    root.getBoundsInScreen(wr)
                    android.util.Log.d("WechatVAM", "=== [$label] window(layer=${w.layer},bounds=$wr) 节点(${nodes.size}) ===")
                    for (s in nodes.take(60)) android.util.Log.d("WechatVAM", s)
                } finally { root.recycle() }
            }
        }

        // 也检查 rootInActiveWindow（兜底）
        val root = service.rootInActiveWindow
        if (root != null) {
            try {
                val nodes2 = mutableListOf<String>()
                collectVisibleNodes(root, 0, nodes2)
                android.util.Log.d("WechatVAM", "=== [$label] rootInActiveWindow 节点(${nodes2.size}) ===")
                for (s in nodes2.take(60)) android.util.Log.d("WechatVAM", s)
            } finally { root.recycle() }
        }
    }

    private fun collectVisibleNodes(node: AccessibilityNodeInfo, depth: Int, out: MutableList<String>) {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)

        val cls = node.className?.toString()?.substringAfterLast(".") ?: "?"
        val clickable = if (node.isClickable) "(C)" else "()"
        val scrollable = if (node.isScrollable) "[S]" else ""
        val editable = if (node.isEditable) "[E]" else ""
        val focused = if (node.isFocused) "[F]" else ""
        val checked = if (node.isChecked) "[✓]" else ""
        val flags = "$clickable$scrollable$editable$focused$checked"
        val indent = "  ".repeat(depth)

        val text = node.text?.toString()?.take(60).orEmpty()
        val desc = node.contentDescription?.toString()?.take(80).orEmpty()

        val line = "$indent$flags[$cls] vid= txt=[$text] dsc=[$desc] y=${rect.top}-${rect.bottom}"
        out.add(line)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectVisibleNodes(child, depth + 1, out)
                child.recycle()
            }
        }
    }
}
