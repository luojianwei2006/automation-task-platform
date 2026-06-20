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
 * 抖音自动化引擎
 *
 * 通过 AccessibilityService 模拟用户操作：
 * 1. 打开抖音APP
 * 2. 搜索目标账号
 * 3. 找到最新视频
 * 4. 执行任务操作（点赞/评论）
 * 5. 记录每步到服务端
 *
 * 关键设计：
 * - 每步之间随机延迟 800~1500ms，模拟真人
 * - 查找元素失败时重试最多 3 次，每次间隔 800ms
 * - 每一步操作均异步上报服务端
 */
class DouyinAutomator(
    private val service: AutomationService
) {
    companion object {
        // 抖音包名
        const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
        const val DOUYIN_LITE_PACKAGE = "com.ss.android.ugc.aweme.lite"

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

        // 抖音常见 ViewId / 文本关键词
        private val SEARCH_BAR_IDS = listOf(
            "com.ss.android.ugc.aweme:id/a4k",
            "com.ss.android.ugc.aweme:id/atq",
            "com.ss.android.ugc.aweme:id/avx"
        )
        private val SEARCH_INPUT_IDS = listOf(
            "com.ss.android.ugc.aweme:id/et_search_kw",
            "com.ss.android.ugc.aweme:id/a98"
        )
        private val LIKE_BUTTON_IDS = listOf(
            "com.ss.android.ugc.aweme:id/awd"
        )
        private val COMMENT_INPUT_IDS = listOf(
            "com.ss.android.ugc.aweme:id/csa",
            "com.ss.android.ugc.aweme:id/a4o",
            "com.ss.android.ugc.aweme:id/comment_edit_text"
        )
        private val COMMENT_POST_IDS = listOf(
            "com.ss.android.ugc.aweme:id/ct_",
            "com.ss.android.ugc.aweme:id/a58",
            "com.ss.android.ugc.aweme:id/comment_send_btn"
        )
        private val SEARCH_BUTTON_TEXTS = listOf("搜索", "Search")
        private val LIKE_TEXTS = listOf("赞", "Like")
        private val TAB_RECOMMEND_TEXTS = listOf("推荐", "Recommend", "精选")
        private val TAB_USER_TEXTS = listOf("用户", "Users")
        private val TAB_SEARCH_TEXTS = listOf("搜索", "Search", "发现")
        private val SEARCH_ICON_DESCS = listOf("搜索", "搜索入口", "search", "Search", "discover", "Explore")
    }

    @Volatile
    private var cancelled = false

    /** 当前正在执行的任务 */
    private var currentTask: AutoTask? = null

    /** 取消当前自动化执行 */
    fun cancel() {
        cancelled = true
    }

    /**
     * 执行抖音自动化任务
     *
     * @param task 自动化任务配置（platform=1, taskType=1点赞/2评论）
     */
    fun execute(task: AutoTask) {
        cancelled = false
        currentTask = task

        try {
            // Step 1: 打开抖音
            notifyStep("open_app", "正在打开抖音...", 0)
            if (!openDouyin()) {
                notifyStepComplete("open_app", "打开抖音APP", 2, "无法打开抖音APP")
                return
            }
            randomDelay(MIN_APP_WAIT, MAX_APP_WAIT)
            if (cancelled) return
            notifyStepComplete("open_app", "打开抖音APP", 1, "成功打开")

            // Step 2: 搜索目标账号
            val searchKeyword = extractSearchKeyword(task)
            notifyStep("search", "搜索账号: $searchKeyword", 0)
            if (!navigateToSearch()) {
                notifyStepComplete("search", "搜索: $searchKeyword", 2, "无法进入搜索页面")
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

            // Step 3: 进入目标账号主页（点击"用户"tab → 第一个用户）
            notifyStep("enter_profile", "正在进入账号主页...", 0)
            if (!enterAccountProfile()) {
                notifyStepComplete("enter_profile", "进入账号主页", 2, "无法进入目标账号")
                return
            }
            randomDelay(MIN_RESULT_WAIT, MAX_RESULT_WAIT)
            if (cancelled) return
            notifyStepComplete("enter_profile", "进入账号主页", 1, "已进入账号主页")

            // Step 4: 点击第一个视频
            notifyStep("first_video", "正在播放第一个视频...", 0)
            if (!clickFirstVideo()) {
                notifyStepComplete("first_video", "点击第一个视频", 2, "无法找到视频")
                return
            }
            randomDelay(MIN_RESULT_WAIT, MAX_RESULT_WAIT)
            if (cancelled) return
            notifyStepComplete("first_video", "播放视频", 1, "已进入视频")

            // Step 5: 执行任务操作
            // 评论类型：先从词库随机取词，然后点赞+评论
            var commentText: String? = null
            when (task.taskType) {
                1 -> {
                    notifyStep("like", "正在点赞...", 0)
                    randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
                    if (!performLike()) {
                        notifyStepComplete("like", "点赞", 2, "点赞失败")
                        return
                    }
                    notifyStepComplete("like", "点赞", 1, "点赞成功")
                }
                2 -> {
                    // 先随机获取评论词
                    notifyStep("fetch_words", "正在获取评论词...", 0)
                    commentText = fetchCommentWord(task)
                    if (commentText == null) {
                        notifyStepComplete("fetch_words", "获取评论词", 2, "无可用评论词")
                        return
                    }
                    notifyStepComplete("fetch_words", "评论词: $commentText", 1, commentText!!)

                    // 点赞
                    notifyStep("like", "正在点赞...", 0)
                    randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
                    if (!performLike()) {
                        notifyStepComplete("like", "点赞", 2, "点赞失败")
                        return
                    }
                    notifyStepComplete("like", "点赞", 1, "点赞成功")
                }
                else -> {
                    notifyStepComplete("action", "未知类型", 2, "不支持: ${task.taskType}")
                    return
                }
            }
            randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
            if (cancelled) return

            // Step 6: 评论（仅评论类型，必须在切到推荐页之前——此时还在视频页才能找到评论入口）
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

            // Step 7: 截图并保存（评论已发送，直接截当前页面）
            android.util.Log.d("DouyinAutomator", "===== 开始截图（评论已发送/面板已关闭）=====")
            android.util.Log.d("DouyinAutomator", "===== [v2.0-fix] 2026-06-21 已修复：输入策略重排+去掉推荐页切换 =====")
            notifyStep("screenshot", "正在截图...", 0)
            val localFile = takeScreenshot()
            if (localFile != null) {
                notifyStepComplete("screenshot", "截图保存", 1, localFile)
                AutomationOverlayService.updateComplete(true)
            } else {
                notifyStepComplete("screenshot", "截图失败", 2, "无法截图")
                AutomationService.onActionResult?.invoke(false, "✗ 截图失败")
                AutomationOverlayService.updateComplete(false)
            }

            // Step 9: 返回应用（带 UPLOAD 标记，跳转上传页）
            returnToApp()
            AutomationService.onActionResult?.invoke(true, "✓ 自动化任务执行完成 — UPLOAD:${task.taskId}")
        } catch (e: Exception) {
            android.util.Log.e("DouyinAutomator", "执行异常", e)
            AutomationService.onActionResult?.invoke(false, "执行异常: ${e.message}")
            AutomationOverlayService.updateComplete(false)
        } finally {
            AutomationService.setRunning(false)
        }
    }

    // ─── 抖音操作 ────────────────────────────────────────────

    /**
     * 通过 Intent 打开抖音APP
     */
    private fun openDouyin(): Boolean {
        return try {
            val intent = service.packageManager.getLaunchIntentForPackage(DOUYIN_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                service.startActivity(intent)
                true
            } else {
                // 尝试抖音极速版
                val liteIntent = service.packageManager.getLaunchIntentForPackage(DOUYIN_LITE_PACKAGE)
                if (liteIntent != null) {
                    liteIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    service.startActivity(liteIntent)
                    true
                } else {
                    // 通过 URL Scheme 打开
                    val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("snssdk1128://"))
                    uriIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    service.startActivity(uriIntent)
                    true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DouyinAutomator", "打开抖音失败", e)
            false
        }
    }

    /**
     * 导航到搜索页面（多重策略：Tab 文本 → contentDescription → ImageView 搜索图标 → 底部按钮）
     */
    private fun navigateToSearch(): Boolean {
        // 策略1：Tab 文本匹配
        if (retryFindNode({ findNodeByText(TAB_SEARCH_TEXTS) }) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
        }) return true

        // 策略2：contentDescription 匹配搜索图标
        if (retryFindNode({ findNodeByDesc(SEARCH_ICON_DESCS) }) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
        }) return true

        // 策略3：遍历底部导航栏所有可点击节点，点击非首页的倒数第2或第3个
        val root = service.rootInActiveWindow ?: return false
        val clickables = findAllClickablesInBottom(root)
        root.recycle()
        android.util.Log.d("DouyinAutomator", "底部可点击节点: ${clickables.size}个")
        if (clickables.size >= 3) {
            // 搜索通常在底部第2或第3个位置
            for (idx in listOf(1, 2)) {
                if (idx < clickables.size) {
                    val node = clickables[idx]
                    try {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        node.recycle()
                        randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
                        return true
                    } catch (_: Exception) {
                        node.recycle()
                    }
                }
            }
        }
        clickables.forEach { it.recycle() }
        return false
    }

    /**
     * 查找底部区域的所有可点击节点
     */
    private fun findAllClickablesInBottom(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val rect = android.graphics.Rect()
        root.getBoundsInScreen(rect)
        val screenHeight = rect.bottom
        val bottomThreshold = screenHeight * 0.85f // 屏幕底部 15% 区域

        collectClickablesRecursive(root, result, bottomThreshold)
        return result
    }

    private fun collectClickablesRecursive(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>, bottomY: Float) {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        // 只在屏幕底部区域查找
        if (rect.top >= bottomY && node.isClickable && node.isVisibleToUser) {
            result.add(android.view.accessibility.AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickablesRecursive(child, result, bottomY)
            child.recycle()
        }
    }

    /**
     * 通过 contentDescription 查找节点
     */
    private fun findNodeByDesc(descs: List<String>): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        return findNodeRecursive(root) { node ->
            val desc = node.contentDescription?.toString().orEmpty()
            descs.any { desc.contains(it, ignoreCase = true) }
        }?.also { root.recycle() }
    }

    /**
     * 在搜索框中输入关键词并触发搜索（多重容错）
     */
    private fun performSearch(keyword: String): Boolean {
        // 1. 点击搜索框（优先 ViewId，兜底文本匹配）
        val foundSearchBox = retryFindNode({ findNodeById(SEARCH_BAR_IDS) }) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            randomDelay(RETRY_DELAY_MS, MIN_STEP_DELAY)
        }
        val searchContainer: AccessibilityNodeInfo? = if (!foundSearchBox) {
            retryFindNode({ findNodeByText(listOf("搜索"), filter = { it.length <= 3 }) }) { node ->
                (node.parent ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
                randomDelay(RETRY_DELAY_MS, MIN_STEP_DELAY)
            }
            null
        } else null
        if (searchContainer != null) return false  // 搜索容器为 null 且 foundSearchBox 为 false，说明两种方式都失败了
        randomDelay(300, 600)

        // 2. 找到搜索输入框，聚焦 → 清空 → 一次性设置全文 → 立即搜索
        val inputSuccess = retryFindNode({ findSearchInput() }) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            randomDelay(50, 100)

            // 全选清空
            val curLen = node.text?.length ?: 0
            if (curLen > 0) {
                val selArgs = android.os.Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, curLen)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
                randomDelay(20, 40)
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                })
            }

            // 一次性写入全文（不逐字，避免输入法自动补全抢文字）
            randomDelay(100, 200)
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val setArgs = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, keyword)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)

            android.util.Log.d("DouyinAutomator", "搜索文本已设置: $keyword")
            randomDelay(100, 200)  // 极短延迟，立刻触发搜索
        }
        if (!inputSuccess) return false
        randomDelay(100, 200)

        // 3. 触发搜索 — 多重策略
        randomDelay(200, 400)

        // 策略A：查找键盘搜索键 + 点击
        if (pressKeyboardSearch()) return true
        randomDelay(100, 200)

        // 策略B：点击搜索按钮（文本匹配）
        if (retryFindNode({ findNodeByText(SEARCH_BUTTON_TEXTS) }) { node ->
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
            }) return true

        // 策略C：查找 EditText 右侧的 clickable 兄弟节点
        if (retryFindNode({ findClickableNearEditable() }) { node ->
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
            }) return true

        // 策略D：搜索输入框右上方区域的任意可点击节点（搜索按钮/搜索图标位置）
        if (retryFindNode({ findClickableNearSearchBox() }) { node ->
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
            }) return true

        android.util.Log.w("DouyinAutomator", "所有搜索触发策略均失败")
        return false
    }

    /**
     * 在搜索输入框右上方区域查找可点击节点（搜索按钮通常在输入框右侧偏上）
     */
    private fun findClickableNearSearchBox(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        // 先找到搜索输入框的位置
        val inputNode = findSearchInput()
        if (inputNode == null) { root.recycle(); return null }
        val inputRect = android.graphics.Rect()
        inputNode.getBoundsInScreen(inputRect)
        val inputId = inputNode.hashCode()
        inputNode.recycle()

        // 在输入框右侧区域（right ~ right+200dp, 同高度）查找可点击节点
        val density = service.resources.displayMetrics.density
        val extendPx = (120 * density).toInt()
        val result = findNodeRecursive(root) { node ->
            if (node.hashCode() == inputId || !node.isClickable || !node.isVisibleToUser) return@findNodeRecursive false
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            rect.left >= inputRect.right - 20 && // 在输入框右侧
            rect.left <= inputRect.right + extendPx && // 不超过右侧 120dp
            rect.top >= inputRect.top - 20 && // 垂直方向与输入框重叠
            rect.bottom <= inputRect.bottom + 20
        }
        root.recycle()
        return result
    }

    /**
     * 按键盘上的搜索/回车键
     */
    private fun pressKeyboardSearch(): Boolean {
        return try {
            val root = service.rootInActiveWindow ?: return false
            // 查找键盘中的回车/搜索键
            val done = findNodeRecursive(root) { node ->
                val desc = node.contentDescription?.toString().orEmpty()
                val text = node.text?.toString().orEmpty()
                node.isClickable && (
                    desc.contains("搜索", ignoreCase = true) ||
                    desc.contains("search", ignoreCase = true) ||
                    text == "搜索" || text == "Search" ||
                    text == "发送" || text == "Send" || text == "回车"
                )
            }
            root.recycle()
            if (done != null) {
                done.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                done.recycle()
                return true
            }
            false
        } catch (e: Exception) { false }
    }

    /**
     * 查找 EditText 附近的可点击节点（搜索按钮通常在输入框右侧）
     */
    private fun findClickableNearEditable(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val editable = findEditableRecursive(root)
        if (editable != null) {
            // 向上找父节点，再找兄弟节点中的可点击节点
            var parent = editable.parent
            var depth = 0
            while (parent != null && depth < 4) {
                for (i in 0 until parent.childCount) {
                    val child = parent.getChild(i) ?: continue
                    if (child != editable && child.isClickable && child.isEnabled) {
                        val desc = child.contentDescription?.toString().orEmpty()
                        val text = child.text?.toString().orEmpty()
                        // 排除输入框本身
                        if (!child.isEditable && (desc.length <= 4 || text.length <= 4)) {
                            editable.recycle()
                            if (root != child) root.recycle()
                            return child
                        }
                    }
                    child.recycle()
                }
                val grandparent = parent.parent
                if (parent != editable) parent.recycle()
                parent = grandparent
                depth++
            }
            editable.recycle()
        }
        root.recycle()
        return null
    }

    /**
     * 递归查找符合谓词的节点
     */
    private fun findNodeRecursive(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeRecursive(child, predicate)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    /**
     * 进入目标账号主页（先点"用户"tab，再点第一个用户）
     */
    private fun enterAccountProfile(): Boolean {
        // 1. 点击"用户"Tab
        retryFindNode({ findNodeByText(TAB_USER_TEXTS) }) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            randomDelay(RETRY_DELAY_MS, MIN_STEP_DELAY)
        }

        // 2. 点击第一个用户结果
        return retryFindNode({ findFirstClickableInList() }) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    /**
     * 在账号主页点击第一个视频
     */
    private fun clickFirstVideo(): Boolean {
        dumpCommentPageNodes()
        
        // 在 RecyclerView 子树中找第一个 desc 含"视频"的节点（不管是否 isClickable，手势点击）
        val found = retryFindNode({
            val root = service.rootInActiveWindow ?: return@retryFindNode null
            val listNode = findRecyclerView(root)
            if (listNode != null) {
                val result = findFirstVideoInList(listNode)
                listNode.recycle()
                if (result != null && result != root) root.recycle()
                return@retryFindNode result
            }
            root.recycle()
            null
        }) { node ->
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            android.util.Log.d("DouyinAutomator", "clickFirstVideo: cls=${node.className}, desc=${node.contentDescription}, bounds=$r")
            // 用 dispatchGesture 手势点击，不依赖 isClickable
            val path = android.graphics.Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 80))
                .build()
            service.dispatchGesture(gesture, null, null)
        }
        if (found) return true

        return false
    }

    /** 在 RecyclerView 子树中递归找第一个 desc 含"视频"的节点（不要求 isClickable） */
    private fun findFirstVideoInList(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString().orEmpty()
        if (desc.contains("视频")) {
            // 视频节点通常不标记 isClickable，但仍然可以手势点击
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstVideoInList(child)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    /**
     * 点击底部"推荐"Tab
     */
    private fun clickRecommendTab(): Boolean {
        return retryFindNode({ findNodeByText(TAB_RECOMMEND_TEXTS) }) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
        }
    }

    /** 手势从屏幕中部向上快速滑动 + 无障碍滚动，把内容滚回顶部 */
    private fun scrollToTop() {
        val mw = service.resources.displayMetrics.widthPixels.toFloat()
        val mh = service.resources.displayMetrics.heightPixels.toFloat()

        // 策略1: 找 RecyclerView/ViewPager2 节点，用无障碍 ACTION_SCROLL 滚动到顶部
        val listNode = run {
            val root = service.rootInActiveWindow ?: return@run null
            val result = findRecyclerView(root)
            if (result != root) root.recycle()
            result
        }
        if (listNode != null) {
            // 反复快速向后滚动（向上翻页），模拟滚到顶部
            repeat(5) {
                try {
                    listNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                } catch (_: Exception) {}
                Thread.sleep(200)
            }
            listNode.recycle()
        }

        // 策略2: 手势快速上滑（兜底，用于非标准列表）
        repeat(3) {
            val path = android.graphics.Path().apply {
                moveTo(mw * 0.5f, mh * 0.6f)
                lineTo(mw * 0.5f, mh * 0.10f)
            }
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 400))
                .build()
            service.dispatchGesture(gesture, null, null)
            Thread.sleep(500)
        }
    }

    // ─── 截图 + 返回 ──────────────────────────────────────

    /**
     * 截图 — 多策略降级方案
     *
     * 策略优先级：
     *   1. AccessibilityService.takeScreenshot() — API 34+，原生无障碍截图
     *   2. screencap 写文件到 /sdcard/（shell 可写目录，绕过 stdout pipe 限制）
     *   3. screencap -p stdout（传统方式）
     *   4. GLOBAL_ACTION_TAKE_SCREENSHOT + 多目录/MediaStore 扫描
     *   5. SurfaceControl 反射
     */
    private fun takeScreenshot(): String? {
        android.util.Log.d("DouyinAutomator", "=== 开始截图 (SDK=${Build.VERSION.SDK_INT}) ===")

        // 策略1：Android 14+ 原生 API
        if (Build.VERSION.SDK_INT >= 34) {
            val result = takeScreenshotApi34()
            if (result != null) return result
            android.util.Log.w("DouyinAutomator", "takeScreenshot API 失败, 降级 screencap")
        }

        // 策略2：screencap 写文件到 /sdcard/（绕过 stdout pipe 权限问题）
        val result = takeScreenshotScreencapToFile("douyin")
        if (result != null) return result

        // 策略3：screencap -p stdout（传统方式）
        android.util.Log.d("DouyinAutomator", "screencap 写文件失败, 尝试 stdout 管道")
        val result3 = takeScreenshotScreencapStdout("douyin")
        if (result3 != null) return result3

        // 策略4：系统截图键 + 目录扫描 / MediaStore
        android.util.Log.w("DouyinAutomator", "screencap stdout 失败, 尝试系统截图键")
        val result4 = takeScreenshotGlobalAction()
        if (result4 != null) return result4

        // 策略5：SurfaceControl 反射
        android.util.Log.w("DouyinAutomator", "系统截图失败, 尝试 SurfaceControl 反射")
        return takeScreenshotReflection()
    }

    @android.annotation.SuppressLint("NewApi")
    private fun takeScreenshotApi34(): String? {
        if (Build.VERSION.SDK_INT < 34) return null
        return try {
            val latch = CountDownLatch(1)
            var bitmap: Bitmap? = null

            val wm = service.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
            val displayId = wm.defaultDisplay?.displayId ?: 0
            android.util.Log.d("DouyinAutomator", "调用 takeScreenshot API, displayId=$displayId")

            service.takeScreenshot(
                displayId,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        try {
                            bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        } catch (e: Exception) {
                            android.util.Log.e("DouyinAutomator", "wrapHardwareBuffer 失败", e)
                        } finally {
                            result.hardwareBuffer.close()
                        }
                        latch.countDown()
                    }
                    override fun onFailure(errorCode: Int) {
                        android.util.Log.e("DouyinAutomator", "takeScreenshot onFailure: errorCode=$errorCode")
                        latch.countDown()
                    }
                }
            )

            if (!latch.await(5, TimeUnit.SECONDS) || bitmap == null) {
                android.util.Log.e("DouyinAutomator", "takeScreenshot API 超时或返回 null")
                return null
            }

            val dest = prepareDestFile("douyin") ?: run { bitmap!!.recycle(); return null }
            FileOutputStream(dest).use { out -> bitmap!!.compress(Bitmap.CompressFormat.PNG, 95, out) }
            bitmap!!.recycle()
            android.util.Log.d("DouyinAutomator", "API截图成功: ${dest.absolutePath} (${dest.length()} bytes)")
            dest.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("DouyinAutomator", "takeScreenshot API 异常", e)
            null
        }
    }

    /**
     * 策略2：screencap 直接写文件到 /sdcard/（shell 用户可写目录）
     */
    private fun takeScreenshotScreencapToFile(prefix: String): String? {
        return try {
            if (!hasStoragePermission()) {
                android.util.Log.w("DouyinAutomator", "无存储权限, 跳过 screencap 写文件")
                return null
            }

            val dest = prepareDestFile(prefix) ?: return null
            val sdcardDirs = listOf("/sdcard/Download", "/sdcard/Pictures", "/sdcard")
            val ts = System.currentTimeMillis()
            val fileName = "${prefix}_screenshot_$ts.png"

            for (sdcardDir in sdcardDirs) {
                val tmpPath = "$sdcardDir/$fileName"
                for (cmd in listOf("screencap", "/system/bin/screencap")) {
                    android.util.Log.d("DouyinAutomator", "尝试: $cmd -p $tmpPath")
                    val process = Runtime.getRuntime().exec(arrayOf(cmd, "-p", tmpPath))
                    val stderr = process.errorStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    android.util.Log.d("DouyinAutomator", "$cmd 写文件 退出码=$exitCode, stderr=[${stderr.take(200)}]")

                    val tmpFile = java.io.File(tmpPath)
                    if (exitCode == 0 && tmpFile.exists() && tmpFile.length() > 100) {
                        tmpFile.copyTo(dest, overwrite = true)
                        tmpFile.delete()
                        android.util.Log.d("DouyinAutomator", "screencap 写文件成功: ${dest.absolutePath} (${dest.length()} bytes)")
                        return dest.absolutePath
                    }
                    tmpFile.delete()
                }
            }

            // su 兜底
            android.util.Log.d("DouyinAutomator", "尝试 su -c screencap...")
            try {
                val suTmpPath = "/sdcard/Download/$fileName"
                val suProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "screencap -p $suTmpPath"))
                val suExit = suProcess.waitFor()
                val suFile = java.io.File(suTmpPath)
                if (suExit == 0 && suFile.exists() && suFile.length() > 100) {
                    suFile.copyTo(dest, overwrite = true)
                    suFile.delete()
                    return dest.absolutePath
                }
                suFile.delete()
            } catch (e: Exception) {
                android.util.Log.d("DouyinAutomator", "su 不可用: ${e.message}")
            }

            null
        } catch (e: Exception) {
            android.util.Log.e("DouyinAutomator", "screencap 写文件异常", e)
            null
        }
    }

    /** 检查是否有存储读取权限 */
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            service.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            service.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 策略3：screencap -p stdout（传统方式）
     */
    private fun takeScreenshotScreencapStdout(prefix: String): String? {
        return try {
            val dest = prepareDestFile(prefix) ?: return null
            for (cmd in listOf("screencap", "/system/bin/screencap")) {
                val process = Runtime.getRuntime().exec(arrayOf(cmd, "-p"))
                val bytes = process.inputStream.readBytes()
                val stderr = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                android.util.Log.d("DouyinAutomator", "$cmd stdout exit=$exitCode, stdout=${bytes.size}B, stderr=[${stderr.take(200)}]")
                if (bytes.size > 100 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) {
                    dest.writeBytes(bytes)
                    android.util.Log.d("DouyinAutomator", "screencap stdout成功: ${dest.absolutePath} (${dest.length()} bytes)")
                    return dest.absolutePath
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("DouyinAutomator", "screencap stdout 异常", e)
            null
        }
    }

    /**
     * 策略4：GLOBAL_ACTION_TAKE_SCREENSHOT + 多目录/MediaStore 扫描
     */
    private fun takeScreenshotGlobalAction(): String? {
        return try {
            android.util.Log.d("DouyinAutomator", "触发系统截图键...")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            Thread.sleep(5000)

            val searchDirs = listOf(
                "/sdcard/Pictures/Screenshots", "/sdcard/DCIM/Screenshots",
                "/sdcard/Screenshots", "/sdcard/Pictures", "/sdcard/DCIM",
                "/sdcard/Download", "/storage/emulated/0/Pictures/Screenshots",
                "/storage/emulated/0/DCIM/Screenshots",
                "/storage/emulated/0/Pictures", "/storage/emulated/0/DCIM",
                "/sdcard/windows/BstSharedFolder",
                "/data/media/0/Pictures/Screenshots",
                "/mnt/sdcard/Pictures/Screenshots"
            )
            var latestFile: java.io.File? = null
            var latestTime = 0L
            for (dirPath in searchDirs) {
                val dir = java.io.File(dirPath)
                if (!dir.exists() || !dir.isDirectory) continue
                val files = try {
                    dir.listFiles()?.filter {
                        val n = it.name.lowercase()
                        (n.endsWith(".png") || n.endsWith(".jpg")) && it.isFile
                    }
                } catch (e: Exception) { null }
                files?.forEach { f -> if (f.lastModified() > latestTime) { latestTime = f.lastModified(); latestFile = f } }
            }
            if (latestFile != null && latestFile!!.length() > 0) {
                android.util.Log.d("DouyinAutomator", "找到截图: ${latestFile!!.absolutePath} (${System.currentTimeMillis()-latestTime}ms前)")
                return copyScreenshot(latestFile!!, "douyin")
            }

            // MediaStore fallback（扩大窗口到 30 秒）
            android.util.Log.d("DouyinAutomator", "尝试 MediaStore（30s窗口）...")
            val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val cursor = service.contentResolver.query(
                uri,
                arrayOf(android.provider.MediaStore.Images.Media.DATA, android.provider.MediaStore.Images.Media.DATE_ADDED),
                "${android.provider.MediaStore.Images.Media.DATE_ADDED} > ?",
                arrayOf((System.currentTimeMillis() / 1000 - 30).toString()),
                "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.MediaStore.Images.Media.DATA)
                    if (idx >= 0) {
                        val path = it.getString(idx)
                        val f = java.io.File(path)
                        if (f.exists() && f.length() > 0) return copyScreenshot(f, "douyin")
                    }
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("DouyinAutomator", "系统截图键异常", e)
            null
        }
    }

    private fun copyScreenshot(src: java.io.File, prefix: String): String? {
        val dest = prepareDestFile(prefix) ?: return null
        return try {
            src.copyTo(dest, overwrite = true)
            android.util.Log.d("DouyinAutomator", "截图已复制: ${dest.absolutePath}")
            dest.absolutePath
        } catch (e: Exception) {
            android.util.Log.w("DouyinAutomator", "复制失败, 用原路径: ${e.message}")
            src.absolutePath
        }
    }

    /**
     * 策略4：SurfaceControl.screenshot() 反射 — getMethods 含继承兜底
     */
    private fun takeScreenshotReflection(): String? {
        return try {
            val sc = Class.forName("android.view.SurfaceControl")
            val dm = service.resources.displayMetrics
            val w = dm.widthPixels
            val h = dm.heightPixels
            val crop = Rect(0, 0, w, h)

            val methods = sc.declaredMethods.filter {
                it.name == "screenshot" && it.returnType == Bitmap::class.java
            }.ifEmpty {
                android.util.Log.w("DouyinAutomator", "declaredMethods empty, 尝试 getMethods...")
                sc.methods.filter {
                    it.name == "screenshot" && it.returnType == Bitmap::class.java
                }.distinctBy { it.parameterTypes.contentToString() }
            }
            if (methods.isEmpty()) {
                android.util.Log.e("DouyinAutomator", "SurfaceControl 未找到 screenshot 方法")
                return null
            }
            android.util.Log.d("DouyinAutomator", "找到 ${methods.size} 个 screenshot 方法")

            for (m in methods) {
                val n = m.parameterTypes.size
                val args: Array<Any?> = when (n) {
                    2 -> arrayOf(w, h)
                    4 -> arrayOf(crop, w, h, 0)
                    7 -> arrayOf(crop, w, h, 0, 0, false, 0)
                    else -> continue
                }
                try {
                    val bitmap = m.invoke(null, *args) as? Bitmap
                    if (bitmap != null && bitmap.width > 0) {
                        val dest = prepareDestFile("douyin") ?: run { bitmap.recycle(); return null }
                        FileOutputStream(dest).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 95, out) }
                        bitmap.recycle()
                        android.util.Log.d("DouyinAutomator", "反射成功(${n}参数): ${dest.absolutePath}")
                        return dest.absolutePath
                    }
                    bitmap?.recycle()
                } catch (e: Exception) {
                    android.util.Log.w("DouyinAutomator", "screenshot(${n}参数) 失败: ${e.message}")
                }
            }
            android.util.Log.e("DouyinAutomator", "所有 SurfaceControl 签名均失败")
            null
        } catch (e: Exception) {
            android.util.Log.e("DouyinAutomator", "SurfaceControl 反射失败", e)
            null
        }
    }

    private fun prepareDestFile(prefix: String): java.io.File? {
        val destDir = service.getExternalFilesDir(null) ?: run {
            android.util.Log.e("DouyinAutomator", "getExternalFilesDir 返回 null")
            return null
        }
        return java.io.File(destDir, "${prefix}_${System.currentTimeMillis()}.png")
    }

    /**
     * 返回我们的应用
     */
    /**
     * 上传截图到 upload-service 并提交任务
     */
    private fun uploadAndSubmit(task: AutoTask, localFile: String): Boolean {
        android.util.Log.d("DouyinAutomator", "=== 上传提交: $localFile ===")
        return try {
            val file = java.io.File(localFile)
            if (!file.exists()) {
                android.util.Log.e("DouyinAutomator", "截图文件不存在: $localFile")
                return false
            }
            android.util.Log.d("DouyinAutomator", "文件存在, size=${file.length()}")

            // 直连 upload-service:8086（绕过 Gateway）
            val fileBody = file.readBytes().toRequestBody("image/*".toMediaTypeOrNull())
            val body = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", file.name, fileBody)
                .addFormDataPart("type", "screenshot")
                .build()
            val token = ApiClient.token
            android.util.Log.d("DouyinAutomator", "Token长度=${token.length}")

            val response = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder()
                    .url("http://10.0.2.2:8086/upload/image")
                    .post(body)
                    .header("Authorization", "Bearer $token")
                    .build()).execute()
            }
            val bodyStr = response.body?.string().orEmpty()
            android.util.Log.d("DouyinAutomator", "上传响应: $bodyStr")

            val json = com.google.gson.Gson().fromJson(bodyStr, Map::class.java)
            val code = (json["code"] as? Double)?.toInt() ?: 0
            if (code != 200) {
                android.util.Log.e("DouyinAutomator", "上传失败 code=$code")
                return false
            }
            val data = json["data"] as? Map<*, *>
            val url = data?.get("accessUrl") as? String
            if (url == null) {
                android.util.Log.e("DouyinAutomator", "未获取到accessUrl")
                return false
            }
            android.util.Log.d("DouyinAutomator", "截图URL: $url")

            // 提交任务
            val submitResp = kotlinx.coroutines.runBlocking {
                ApiClient.apiService.submitTask(task.taskId, mapOf(
                    "screenshotUrls" to listOf(url),
                    "latitude" to 0.0,
                    "longitude" to 0.0
                ))
            }
            android.util.Log.d("DouyinAutomator", "提交结果: code=${submitResp.code}")
            submitResp.code == 200
        } catch (e: Exception) {
            android.util.Log.e("DouyinAutomator", "上传提交异常: ${e.javaClass.name} - ${e.message}", e)
            false
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

    /**
     * 执行点赞操作
     */
    private fun performLike(): Boolean {
        // 先检查是否已点赞：多重状态检测
        val alreadyLiked = retryFindNodeNoAction({
            val root = service.rootInActiveWindow ?: return@retryFindNodeNoAction null
            val result = findNodeRecursive(root) { node ->
                if (!node.isClickable) return@findNodeRecursive false
                val desc = node.contentDescription?.toString().orEmpty()
                val text = node.text?.toString().orEmpty()
                val cls = node.className?.toString().orEmpty()
                // 已点赞的多种标识
                val isSelected = node.isSelected
                val isChecked = node.isChecked
                val hasLikedText = desc.contains("已赞") || desc.contains("取消赞") ||
                                    text.contains("已赞") || text.contains("取消赞")
                // 记录状态到日志用于调试
                if (desc.contains("赞") || text.contains("赞")) {
                    android.util.Log.d("DouyinAutomator", "点赞节点: desc=[$desc] text=[$text] selected=$isSelected checked=$isChecked cls=$cls")
                }
                isSelected || isChecked || hasLikedText
            }
            root.recycle()
            result
        })
        if (alreadyLiked != null) {
            android.util.Log.d("DouyinAutomator", "已点赞，跳过")
            alreadyLiked.recycle()
            return true
        }

        // 未点赞，执行点赞
        val foundById = retryFindNode({ findNodeById(LIKE_BUTTON_IDS) }) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            android.util.Log.d("DouyinAutomator", "点赞(ViewId)")
            randomDelay(RETRY_DELAY_MS, MAX_STEP_DELAY)
        }
        if (foundById) return true

        return retryFindNode({
            findNodeByText(LIKE_TEXTS) { text ->
                text.contains("赞") && !text.contains("评论") && !text.contains("回复") && !text.contains("已赞")
            }
        }) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            android.util.Log.d("DouyinAutomator", "点赞(文本)")
            randomDelay(RETRY_DELAY_MS, MAX_STEP_DELAY)
        }
    }

    /**
     * 仅查找不操作（不触发点击，只读节点状态）
     */
    private fun retryFindNodeNoAction(finder: () -> AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        repeat(MAX_RETRIES / 2) {
            if (cancelled) return null
            val node = finder()
            if (node != null) return node
            randomDelay(RETRY_DELAY_MS / 2, RETRY_DELAY_MS)
        }
        return null
    }

    /**
     * 执行评论操作
     *
     * 时序关键：点击「写评论/说点什么」后，抖音会异步弹出评论区（底部弹出/BottomSheet）。
     * 必须在对话框完全展开后再找输入框、输文字，否则：
     *   1）可能找到页面上的旧输入框（输入到错误位置）
     *   2）对话框展开时会重新初始化 EditText，清掉已输入的文字
     *
     * 修复策略：点击入口后，先等发送按钮出现在树上（确认对话框已就绪），再操作输入框。
     */
    private fun performComment(commentText: String): Boolean {
        android.util.Log.d("DouyinAutomator", "=== 开始评论: $commentText ===")

        // dumpCommentPageNodes()  // DEBUG: 需要时取消注释

        // ────────────────────────────────────────────────
        // 评论输入框是视频页底部内嵌的 EditText（不是弹窗！）
        // 直接找 y > 75% 屏幕高度的可编辑节点，不需要找入口/等对话框
        // ────────────────────────────────────────────────
        val screenH = service.resources.displayMetrics.heightPixels
        val bottomThreshold = (screenH * 0.75).toInt()

        var inputBounds: android.graphics.Rect? = null

        retryFindNode({
            findEditableNodeAtBottom(bottomThreshold)
        }) { node ->
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            inputBounds = android.graphics.Rect(r)
            android.util.Log.d("DouyinAutomator", "找到评论输入框: bounds=$r text=[${node.text}]")
            node.recycle()
        }

        if (inputBounds == null) {
            android.util.Log.e("DouyinAutomator", "找不到评论输入框（底部无 EditText）")
            return false
        }

        // ── 输入+发送 ──
        val cx = inputBounds!!.centerX().toFloat()
        val cy = inputBounds!!.centerY().toFloat()

        // 1) PASTE 一次
        val cm = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("comment", commentText))
        randomDelay(200, 400)

        val tapPath = android.graphics.Path().apply { moveTo(cx, cy) }
        service.dispatchGesture(
            android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(tapPath, 0, 80))
                .build(), null, null
        )
        randomDelay(500, 800)

        val pasteNode = retryFindNodeNoAction { findEditableNodeAtBottom(bottomThreshold) }
        if (pasteNode != null) {
            pasteNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            randomDelay(100, 200)
            pasteNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            android.util.Log.d("DouyinAutomator", "PASTE done")
            pasteNode.recycle()
        }

        // 2) 发送一次
        android.util.Log.d("DouyinAutomator", "准备发送...")
        randomDelay(500, 800)
        val sent = trySendBtn({ findNodeByText(listOf("发送", "Send", "Post", "发布")) }, "发送")
        if (sent) {
            android.util.Log.d("DouyinAutomator", "评论已发送")
        } else {
            android.util.Log.e("DouyinAutomator", "评论发送失败")
            return false
        }
        return true
    }

    /**
     * 在输入框右侧区域查找发送按钮（抖音发送按钮通常在输入框右侧）
     */
    private fun findSendNearInput(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        // 先找输入框的位置
        val inputNode = findNodeById(COMMENT_INPUT_IDS) ?: findEditableRecursive(root)
        if (inputNode == null) { root.recycle(); return null }
        val inputRect = android.graphics.Rect()
        inputNode.getBoundsInScreen(inputRect)
        inputNode.recycle()

        // 在输入框右侧 (+10dp ~ +200dp) 且垂直方向重叠的区域查找可点击节点
        val density = service.resources.displayMetrics.density
        val extendPx = (200 * density).toInt()
        val result = findNodeRecursive(root) { node ->
            if (node == inputNode || !node.isClickable || !node.isVisibleToUser) return@findNodeRecursive false
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            // 在输入框右侧，且垂直方向有重叠
            r.left >= inputRect.right - 20 &&
            r.left <= inputRect.right + extendPx &&
            r.top <= inputRect.bottom &&
            r.bottom >= inputRect.top
        }
        root.recycle()
        return result
    }

    /**
     * 手势点击输入框右侧区域（发送按钮预估位置）
     */
    private fun tapSendNearInput(inputBounds: android.graphics.Rect?): Boolean {
        return try {
            val rect = inputBounds ?: run {
                // 兜底：屏幕右下角
                val m = service.resources.displayMetrics
                android.graphics.Rect(0, 0, m.widthPixels, m.heightPixels)
            }
            // 发送按钮在输入框右侧约 40dp 处，垂直居中
            val density = service.resources.displayMetrics.density
            val sendX = (rect.right + 40 * density).toFloat().coerceAtMost(service.resources.displayMetrics.widthPixels - 20f)
            val sendY = rect.centerY().toFloat()
            android.util.Log.d("DouyinAutomator", "手势点击发送区域: ($sendX, $sendY)")
            val path = android.graphics.Path().apply { moveTo(sendX, sendY) }
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 80))
                .build()
            service.dispatchGesture(gesture, null, null)
            randomDelay(500, 800)
            true
        } catch (e: Exception) {
            android.util.Log.e("DouyinAutomator", "手势点击发送区域失败", e)
            false
        }
    }

    /**
     * 查找节点并按"发送"逻辑点击。返回 true 才表示真正点击成功
     */
    private fun trySendBtn(finder: () -> AccessibilityNodeInfo?, label: String): Boolean {
        var clicked = false
        val found = retryFindNode(finder) { node ->
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                clicked = true
            } else {
                // 向上找 3 层 clickable parent
                var p = node.parent; var d = 0
                while (p != null && d < 3) {
                    if (p.isClickable) { p.performAction(AccessibilityNodeInfo.ACTION_CLICK); p.recycle(); clicked = true; return@retryFindNode }
                    val gp = p.parent; if (p != node) p.recycle(); p = gp; d++
                }
                // 尝试点击兄弟节点
                var sibling = node.parent?.getChild(0)
                var si = 0
                while (sibling != null && si < 5) {
                    if (sibling.isClickable && sibling != node) {
                        sibling.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        sibling.recycle(); clicked = true; return@retryFindNode
                    }
                    sibling = node.parent?.getChild(++si)
                }
            }
        }
        if (!clicked) android.util.Log.w("DouyinAutomator", "$label: 无法点击发送")
        return clicked
    }

    /**
     * 在键盘 IME 视图中查找发送/回车键
     */
    private fun kbSend(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        return findNodeRecursive(root) { node ->
            val desc = node.contentDescription?.toString().orEmpty().lowercase()
            val text = node.text?.toString().orEmpty().lowercase()
            // 匹配发送/回车/换行相关
            (desc.contains("发送") || desc.contains("send") ||
             desc.contains("回车") || desc.contains("enter") || desc.contains("go") ||
             text.contains("发送") || text.contains("send") || text == "回车" || text == "enter")
        }?.also { root.recycle() }
    }

    /**
     * 在屏幕右下角区域查找发送按钮（抖音评论发送按钮常见位置）
     */
    private fun findSendInBottomRight(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val screenRect = android.graphics.Rect()
        root.getBoundsInScreen(screenRect)
        val sw = screenRect.width()
        val sh = screenRect.height()
        val result = findNodeRecursive(root) { node ->
            if (!node.isClickable || !node.isVisibleToUser) return@findNodeRecursive false
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            r.left > sw * 2 / 3 && r.top > sh * 3 / 4
        }
        root.recycle()
        return result
    }

    /**
     * 手势点击键盘发送键区域（无障碍键盘节点不可用时的兜底方案）
     * 使用屏幕百分比 + 多候选位，适配不同分辨率/密度/键盘高度
     */
    private fun tapBottomRight(): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return false
            val metrics = service.resources.displayMetrics
            val w = metrics.widthPixels.toFloat()
            val h = metrics.heightPixels.toFloat()

            // 键盘约占屏幕底部 40%，发送键通常在右下角区域
            // 多候选位：不同键盘布局下发送键的位置
            val candidates = listOf(
                w * 0.93f to h * 0.80f,  // 最右下角
                w * 0.90f to h * 0.82f,  // 稍左
                w * 0.88f to h * 0.78f,  // 更左上方
                w * 0.95f to h * 0.83f,  // 最偏右
            )
            for ((x, y) in candidates) {
                android.util.Log.d("DouyinAutomator", "手势点击键盘发送: x=$x y=$y")
                val gesture = android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(
                        android.graphics.Path().apply { moveTo(x, y) }, 0, 50))
                    .build()
                service.dispatchGesture(gesture, null, null)
                randomDelay(150, 300)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("DouyinAutomator", "手势失败", e)
            false
        }
    }

    // ─── 键盘手势输入工具 ─────────────────────────────────────

    /**
     * 【主方案】通过 Accessibility 树中的键盘节点直接 CLICK 来模拟键盘输入。
     *
     * 为什么之前 dispatchGesture 坐标估算不可靠？
     *   - 不同设备/键盘 App（搜狗/百度/Gboard）键盘布局差异巨大
     *   - 硬编码 QWERTY 坐标大概率偏了，没点到真实按键
     *
     * 新思路：软键盘显示时，每个按键都是 Accessibility 树中的一个节点（TextView/Button
     * 且带文字标签）。不猜坐标，直接按文字找到节点、ACTION_CLICK 它即可。
     *
     * 前置条件：键盘必须已弹出（调用方负责先 tap 输入框 + delay 等待键盘出现）
     *
     * @return 成功点击的字符数量；0=完全失败
     */
    private fun inputTextByKeyboardNodes(text: String): Int {
        val metrics = service.resources.displayMetrics
        val keyboardThreshold = (metrics.heightPixels * 0.55).toInt()  // 键盘大概在屏幕下半部
        var clicked = 0
        val t0 = System.currentTimeMillis()

        for (i in text.indices) {
            if (cancelled) break
            val ch = text[i]
            val chLower = ch.lowercaseChar().toString()
            val chStr = ch.toString()

            // 空格特殊处理；常规字符先搜原始大小写，再搜小写（键盘通常用小写）
            val searchKeys = if (ch == ' ') {
                listOf(" ", "空格", "space")
            } else {
                if (chStr == chLower) listOf(chStr)
                else listOf(chStr, chLower)
            }

            var found = false
            for (query in searchKeys) {
                val node = _findKeyboardKeyByText(query, keyboardThreshold)
                if (node != null) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                    found = true
                    clicked++
                    break
                }
            }

            if (!found) {
                // 最后兜底：用 dispatchGesture 点输入框确认按钮来做回退
                android.util.Log.w("DouyinAutomator", "keyboardNode: 未找到按键节点 '$ch' (已搜${searchKeys.size}个查询)")
            } else {
                android.util.Log.d("DouyinAutomator", "keyboardNode: CLICK '$ch' ✓")
            }
            randomDelay(50, 110)
        }

        val elapsed = System.currentTimeMillis() - t0
        android.util.Log.d("DouyinAutomator", "keyboardNode: 完成 ${clicked}/${text.length} 字符, 耗时 ${elapsed}ms")
        return clicked
    }

    /**
     * 在 Accessibility 树中查找键盘上匹配文字的按键节点。
     *
     * 筛选规则：
     *   1. bounds 在屏幕下半部（键盘区域）
     *   2. isClickable
     *   3. 优先 className 包含 "Button"
     */
    private fun _findKeyboardKeyByText(searchText: String, keyboardThreshold: Int): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        try {
            // 递归收集所有文本等于 searchText 的节点
            collectNodesByText(root, searchText, keyboardThreshold, candidates)

            // 先找 isClickable 的，再退而求其次
            var best = candidates.firstOrNull { it.isClickable }
            if (best == null) best = candidates.firstOrNull()

            // 返回副本，避免 root.recycle() 后失效
            return if (best != null) AccessibilityNodeInfo.obtain(best) else null
        } finally {
            root.recycle()
        }
    }

    /**
     * 递归遍历 Accessibility 树，收集文本等于 target 且在键盘区域的节点
     */
    private fun collectNodesByText(
        node: AccessibilityNodeInfo,
        target: String,
        keyboardThreshold: Int,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)

        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""

        val matches = (text.equals(target, ignoreCase = false) ||
                       contentDesc.equals(target, ignoreCase = false))

        if (matches && rect.top >= keyboardThreshold) {
            out.add(AccessibilityNodeInfo.obtain(node))
        }

        // 递归子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodesByText(child, target, keyboardThreshold, out)
            child.recycle()
        }
    }

    // ─── 节点查找工具 ─────────────────────────────────────────

    /**
     * 带重试的节点查找
     * @param finder 查找函数
     * @param action 找到后的操作
     * @return 是否成功找到并执行操作
     */
    private fun retryFindNode(
        finder: () -> AccessibilityNodeInfo?,
        action: (AccessibilityNodeInfo) -> Unit
    ): Boolean {
        repeat(MAX_RETRIES) { attempt ->
            if (cancelled) return false

            val node = finder()
            if (node != null) {
                try {
                    action(node)
                    node.recycle()
                    return true
                } catch (e: Exception) {
                    android.util.Log.w("DouyinAutomator", "操作节点失败 (attempt ${attempt + 1})", e)
                    node.recycle()
                }
            }

            if (attempt < MAX_RETRIES - 1) {
                randomDelay(RETRY_DELAY_MS, RETRY_DELAY_MS + 300)
            }
        }
        return false
    }

    /**
     * 通过 ViewId 列表查找节点
     */
    private fun findNodeById(ids: List<String>): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes != null && nodes.isNotEmpty()) {
                // 回收根节点以外的节点
                root.recycle()
                return nodes[0]
            }
        }
        root.recycle()
        return null
    }

    /**
     * 通过文本内容查找节点
     */
    private fun findNodeByText(texts: List<String>, filter: ((String) -> Boolean)? = null): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        for (text in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes != null && nodes.isNotEmpty()) {
                for (node in nodes) {
                    val nodeText = node.text?.toString() ?: ""
                    val nodeDesc = node.contentDescription?.toString() ?: ""
                    if (filter == null || filter(nodeText) || filter(nodeDesc)) {
                        root.recycle()
                        return node
                    }
                }
            }
        }
        root.recycle()
        return null
    }

    /**
     * 查找搜索输入框（优先级：ViewId → 搜索提示文本附近的 EditText → 全局）
     * 排除评论区等无关输入框
     */
    private fun findSearchInput(): AccessibilityNodeInfo? {
        // 策略1：通过已知 ViewId 精确匹配
        val byId = findNodeById(SEARCH_INPUT_IDS)
        if (byId != null) return byId

        val root = service.rootInActiveWindow ?: return null

        // 策略2：查找包含"搜索"/"Search"提示文本附近的 EditText
        // 提示文本通常在 EditText 的 hint 中，或者作为 sibling
        val hintNode = findNodeRecursive(root) { node ->
            val hint = node.hintText?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            hint.contains("搜索", ignoreCase = true) || desc.contains("搜索", ignoreCase = true)
        }
        if (hintNode != null) {
            // hintNode 可能就是 EditText 本身，或其父/兄节点中有关联的 EditText
            if (hintNode.isEditable) {
                root.recycle()
                return hintNode
            }
            // 在 hintNode 父节点下找 EditText
            var parent = hintNode.parent
            var depth = 0
            while (parent != null && depth < 3) {
                for (i in 0 until parent.childCount) {
                    val child = parent.getChild(i) ?: continue
                    if (child.isEditable && child.isFocused) {
                        hintNode.recycle()
                        root.recycle()
                        return child
                    }
                    child.recycle()
                }
                val gp = parent.parent
                if (parent != hintNode) parent.recycle()
                parent = gp
                depth++
            }
            hintNode.recycle()
        }

        // 策略3：全局查找，但过滤掉明显的评论区输入框
        val editable = findEditableRecursive(root)
        if (editable != null) {
            // 排除评论框（描述或文本含有"评论"、"说点什么"等）
            val desc = editable.contentDescription?.toString().orEmpty()
            val text = editable.text?.toString().orEmpty()
            val hint = editable.hintText?.toString().orEmpty()
            val descLower = desc.lowercase()
            val combined = "$text $hint $desc".lowercase()
            if (descLower.contains("评论") || descLower.contains("comment") ||
                combined.contains("写评论") || combined.contains("说点什么")) {
                editable.recycle()
                root.recycle()
                return null
            }
            root.recycle()
            return editable
        }
        root.recycle()
        return null
    }

    /**
     * 查找底部区域的 EditText（评论输入框通常在最底部）
     */
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

    /**
     * 查找可编辑节点（评论输入等场景）
     */
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

    /**
     * 等待评论对话框完全就绪。
     *
     * 判断标准：发送按钮（"发送"/"Send"）或评论发送 View 出现在 Accessibility 树中。
     * 这说明抖音的评论区 BottomSheet/Dialog 已经完成布局初始化，输入框不会再被重置。
     *
     * @param timeoutMs 最大等待时间（毫秒）
     * @return true=对话框就绪，false=超时
     */
    private fun waitForCommentDialogReady(timeoutMs: Long = 3000): Boolean {
        val start = System.currentTimeMillis()
        var attempt = 0
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (cancelled) return false
            attempt++
            // 检测标志 1：发送按钮存在
            val sendBtn = findNodeByText(listOf("发送", "Send", "Post", "发布"))
            if (sendBtn != null) {
                android.util.Log.d("DouyinAutomator", "对话框就绪: 找到发送按钮 (尝试${attempt}次, ${System.currentTimeMillis()-start}ms)")
                sendBtn.recycle()
                return true
            }
            // 检测标志 2：通过 ViewId 找到发送节点
            val sendById = findNodeById(COMMENT_POST_IDS)
            if (sendById != null) {
                android.util.Log.d("DouyinAutomator", "对话框就绪: 找到发送节点(id) (尝试${attempt}次, ${System.currentTimeMillis()-start}ms)")
                sendById.recycle()
                return true
            }
            // 检测标志 3：评论区出现了可编辑节点（对话框展开后 EditText 出现）
            val editable = findEditableNode()
            if (editable != null) {
                android.util.Log.d("DouyinAutomator", "对话框就绪: 找到可编辑节点 (尝试${attempt}次, ${System.currentTimeMillis()-start}ms)")
                editable.recycle()
                return true
            }
            randomDelay(200, 400)
        }
        android.util.Log.w("DouyinAutomator", "对话框超时: ${System.currentTimeMillis()-start}ms, ${attempt}次尝试")
        return false
    }

    /**
     * DEBUG: 打印评论页面所有输入框和按钮元素，帮助排查为什么找不到发送按钮/输入框
     */
    private fun dumpCommentPageNodes() {
        val root = service.rootInActiveWindow ?: run {
            android.util.Log.w("DouyinAutomator", "dumpPage: rootInActiveWindow == null")
            return
        }
        try {
            val allNodes = mutableListOf<String>()
            collectAllVisible(root, 0, allNodes)

            android.util.Log.d("DouyinAutomator", "=== 页面全部可见节点 (${allNodes.size}) ===")
            for (s in allNodes) android.util.Log.d("DouyinAutomator", s)
            android.util.Log.d("DouyinAutomator", "=== 结束 ===")
        } finally {
            root.recycle()
        }
    }

    private fun collectAllVisible(
        node: AccessibilityNodeInfo,
        depth: Int,
        out: MutableList<String>
    ) {
        if (depth > 10) return
        if (!node.isVisibleToUser) return
        val indent = "  ".repeat(depth.coerceAtMost(6))
        val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val text = node.text?.toString()?.take(50)?.replace("\n", "\\n") ?: ""
        val desc = node.contentDescription?.toString()?.take(40)?.replace("\n", "\\n") ?: ""
        val vid = node.viewIdResourceName?.substringAfterLast('/') ?: ""
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val flags = buildString {
            if (node.isClickable) append("C")
            if (node.isEditable) append("E")
            if (node.isFocused) append("F")
            if (node.isLongClickable) append("L")
        }
        val info = "$indent($flags)[$cls] vid=$vid txt=[$text] dsc=[$desc] x=${rect.left}-${rect.right} y=${rect.top}-${rect.bottom}"
        out.add(info)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllVisible(child, depth + 1, out)
            child.recycle()
        }
    }

    /**
     * 查找列表中第一个可点击的项
     */
    private fun findFirstClickableInList(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val listNode = findRecyclerView(root) ?: findListView(root)
        if (listNode != null) {
            for (i in 0 until listNode.childCount) {
                val child = listNode.getChild(i) ?: continue
                if (child.isClickable) {
                    if (listNode != root) listNode.recycle()
                    if (root != child) root.recycle()
                    return child
                }
                child.recycle()
            }
            if (listNode != root) listNode.recycle()
        }
        root.recycle()
        return null
    }

    private fun findRecyclerView(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.toString()?.contains("RecyclerView") == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findRecyclerView(child)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findListView(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.toString()?.contains("ListView") == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findListView(child)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    /**
     * 向节点设置文本
     */
    /**
     * 模拟真人逐字输入：变速延迟 + 偶发打错 → 删除 → 重输
     */
    private fun typeHumanLike(node: AccessibilityNodeInfo, text: String) {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        // 先清除已有内容
        val existingLen = node.text?.length ?: 0
        if (existingLen > 0) {
            val selectArgs = android.os.Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, existingLen)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectArgs)
            randomDelay(20, 50)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            })
            randomDelay(50, 150)
        }

        val chars = text.toCharArray()
        var i = 0
        while (i < chars.size) {
            if (cancelled) return

            // 变速延迟：打字间隔 40~200ms，偏正态分布
            val baseDelay = 40L + (Math.abs(java.util.Random().nextGaussian() * 50).toLong().coerceAtMost(120))
            randomDelay(baseDelay, baseDelay + 30)

            // 10% 概率打错字（但不影响最后一个字，且连续最多错 1 次）
            if (Random.nextInt(10) == 0 && i < chars.size - 1) {
                // 故意打个错字（相邻键）
                val wrong = when (chars[i]) {
                    in 'a'..'z' -> (chars[i].code + (if (Random.nextBoolean()) 1 else -1)).toChar().coerceIn('a', 'z')
                    in 'A'..'Z' -> (chars[i].code + (if (Random.nextBoolean()) 1 else -1)).toChar().coerceIn('A', 'Z')
                    in '0'..'9' -> (chars[i].code + (if (Random.nextBoolean()) 1 else -1)).toChar().coerceIn('0', '9')
                    else -> chars[i]
                }
                setTextToNode(node, wrong.toString(), append = true)
                randomDelay(200, 500) // 发现错误后的停顿
                // 删掉错字
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val curLen = node.text?.length ?: 1
                val selArgs = android.os.Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, curLen - 1)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, curLen)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
                randomDelay(20, 50)
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                })
                randomDelay(100, 300)
            }

            // 输入正确字符
            setTextToNode(node, chars[i].toString(), append = true)
            i++
        }
    }

    private fun setTextToNode(node: AccessibilityNodeInfo, text: String, append: Boolean = false) {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        randomDelay(30, 80)
        if (!append) {
            // 先选中全部已有文本再覆盖（比 SET_TEXT="" 更可靠）
            val existingLen = node.text?.length ?: 0
            if (existingLen > 0) {
                val selectArgs = android.os.Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, existingLen)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectArgs)
                randomDelay(30, 60)
            }
        }
        val finalText = if (append) (node.text?.toString().orEmpty()) + text else text
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        randomDelay(30, 80)
    }

    /**
     * 从词库随机获取一条评论词
     */
    private fun fetchCommentWord(task: AutoTask): String? {
        android.util.Log.d("DouyinAutomator", "获取评论词: categoryIds=${task.commentCategoryIds}")
        return try {
            val catIds = task.commentCategoryIds ?: "1"
            android.util.Log.d("DouyinAutomator", "请求评论词API: categoryIds=$catIds")
            val resp = kotlinx.coroutines.runBlocking {
                ApiClient.apiService.getCommentWords(catIds)
            }
            android.util.Log.d("DouyinAutomator", "评论词响应: code=${resp.code}, data=${resp.data}")
            val words = resp.data
            if (words != null && words.isNotEmpty()) {
                val picked = words.random()
                android.util.Log.d("DouyinAutomator", "选中评论词: $picked")
                picked
            } else {
                val fallback = task.requirements?.take(50) ?: "支持一下"
                android.util.Log.w("DouyinAutomator", "词库为空，使用兜底: $fallback")
                fallback
            }
        } catch (e: Exception) {
            val fallback = task.requirements?.take(50) ?: "支持一下"
            android.util.Log.e("DouyinAutomator", "获取评论词异常: ${e.javaClass.name} ${e.message}", e)
            fallback
        }
    }

    /**
     * 从任务配置中提取搜索关键词
     * targetUrl 可能是完整链接，从中提取账号名或关键词
     */
    private fun extractSearchKeyword(task: AutoTask): String {
        val target = task.targetUrl
        if (!target.isNullOrBlank()) {
            // 如果是 URL，尝试提取用户名
            val urlPattern = Regex("user/([^/?]+)")
            val match = urlPattern.find(target)
            if (match != null) {
                return match.groupValues[1]
            }
            // 直接使用 targetUrl 作为关键词
            return target.take(50)
        }
        // 兜底用 requirements 的前20个字符
        return task.requirements?.take(20) ?: "默认搜索"
    }

    // ─── 延迟与日志 ─────────────────────────────────────────

    /**
     * 产生随机延迟（毫秒），模拟真人操作
     */
    private fun randomDelay(minMs: Long, maxMs: Long) {
        if (cancelled) return
        val delay = Random.nextLong(minMs, maxMs + 1)
        try {
            Thread.sleep(delay)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * 通知步骤进度（通过回调 + 服务端日志）
     */
    private fun notifyStep(step: String, action: String, status: Int) {
        // 更新悬浮窗
        if (AutomationOverlayService.instance == null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                AutomationOverlayService.updateStep(action, "进行中...")
            }
        } else {
            AutomationOverlayService.updateStep(action, "进行中...")
        }
    }

    /**
     * 步骤完成时上报服务端并通知UI
     */
    private fun notifyStepComplete(step: String, action: String, status: Int, result: String) {
        val task = currentTask

        if (task != null) {
            // 异步上报服务端
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val body: Map<String, Any> = mapOf(
                        "userId" to task.userId,
                        "taskId" to task.taskId,
                        "step" to step,
                        "action" to action,
                        "status" to status,
                        "result" to result
                    )
                    ApiClient.apiService.saveAutoRecord(body)
                } catch (e: Exception) {
                    android.util.Log.e("DouyinAutomator", "上报操作日志失败", e)
                }
            }
        }

        // 更新浮动窗口
        AutomationOverlayService.updateStep(action, result)

        // 通过回调通知 UI 更新进度
        val statusText = when (status) {
            1 -> "✓"
            2 -> "✗"
            else -> "…"
        }
        AutomationService.onActionResult?.invoke(
            status == 1 || status == 0,
            "$statusText $step: $action — $result"
        )
    }
}
