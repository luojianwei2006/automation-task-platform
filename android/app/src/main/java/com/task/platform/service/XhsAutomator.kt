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
 * 小红书自动化引擎
 *
 * 通过 AccessibilityService 模拟用户操作：
 * 1. 打开小红书 APP
 * 2. 搜索目标账号/关键词
 * 3. 进入第一个笔记
 * 4. 点赞 / 评论
 * 5. 截图 + 返回应用
 */
class XhsAutomator(
    private val service: AutomationService
) {
    companion object {
        const val XHS_PACKAGE = "com.xingin.xhs"

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

        // ─── 小红书 ViewId 常量 ───────────────────────
        // 搜索相关
        private val SEARCH_ICON_IDS = listOf(
            "com.xingin.xhs:id/search_icon",
            "com.xingin.xhs:id/action_search"
        )
        private val SEARCH_INPUT_IDS = listOf(
            "com.xingin.xhs:id/search_edit",
            "com.xingin.xhs:id/edit"
        )

        // 点赞
        private val LIKE_BUTTON_IDS = listOf(
            "com.xingin.xhs:id/like",
            "com.xingin.xhs:id/like_view",
            "com.xingin.xhs:id/note_like"
        )

        // 评论
        private val COMMENT_INPUT_IDS = listOf(
            "com.xingin.xhs:id/comment_edit",
            "com.xingin.xhs:id/edit"
        )
        private val COMMENT_POST_IDS = listOf(
            "com.xingin.xhs:id/send",
            "com.xingin.xhs:id/comment_send"
        )

        // 文本关键词
        private val SEARCH_ICON_TEXTS = listOf("搜索", "Search", "发现")
        private val SEARCH_ICON_DESCS = listOf("搜索", "search", "Search", "discover")
        private val SEARCH_BUTTON_TEXTS = listOf("搜索", "Search")
        private val LIKE_TEXTS = listOf("赞", "点赞", "Like")
        private val TAB_USER_TEXTS = listOf("用户", "Users")
        private val TAB_NOTE_TEXTS = listOf("笔记", "Notes")
    }

    @Volatile
    private var cancelled = false
    private var currentTask: AutoTask? = null

    fun cancel() {
        cancelled = true
    }

    fun execute(task: AutoTask) {
        android.util.Log.d("XhsAutomator", "=== XHS自动化开始: platform=${task.platform}, taskType=${task.taskType} ===")
        cancelled = false
        currentTask = task

        try {
            // Step 1: 打开小红书
            notifyStep("open_app", "正在打开小红书...", 0)
            if (!openXhs()) {
                notifyStepComplete("open_app", "打开小红书", 2, "无法打开")
                return
            }
            randomDelay(MIN_APP_WAIT, MAX_APP_WAIT)
            if (cancelled) return
            notifyStepComplete("open_app", "打开小红书", 1, "成功打开")

            // Step 2: 搜索
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

            // Step 4: 进入用户主页 → 等12秒笔记列表加载 → 点第一个笔记
            notifyStep("enter_note", "正在进入笔记...", 0)
            if (!enterFirstNote()) {
                notifyStepComplete("enter_note", "进入笔记", 2, "无法进入用户")
                return
            }
            randomDelay(10000, 14000)  // 等用户主页+笔记列表加载
            if (cancelled) return

            // 点第一个笔记
            dumpAllNodes("用户主页笔记列表")  // DEBUG: 看笔记网格结构
            if (cancelled) return
            if (!clickFirstNoteOnProfile()) {
                notifyStepComplete("enter_note", "笔记详情", 2, "无法打开笔记")
                return
            }

            // 等笔记详情页加载（3-5秒）
            randomDelay(3000, 5000)
            if (cancelled) return
            notifyStepComplete("enter_note", "进入笔记", 1, "已进入笔记")

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
                    // 点赞 + 评论：先点赞，再从词库获取评论词，然后评论
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

            // Step 6: 评论（仅评论类型 taskType=2）
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

            // Step 7: 截图保存
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

            // Step 8: 关闭小红书，返回上传截图界面
            notifyStep("close_app", "正在关闭小红书...", 0)
            closeXhs()
            randomDelay(800, 1200)
            notifyStepComplete("close_app", "关闭小红书", 1, "已关闭")

            returnToApp()
            // 携带 taskId 标记，告诉 ViewModel 需要跳转到截图上传页
            AutomationService.onActionResult?.invoke(true, "✓ 自动化任务执行完成 — UPLOAD:${task.taskId}")
        } catch (e: Exception) {
            android.util.Log.e("XhsAutomator", "执行异常", e)
            AutomationService.onActionResult?.invoke(false, "执行异常: ${e.message}")
            AutomationOverlayService.updateComplete(false)
        } finally {
            AutomationService.setRunning(false)
        }
    }

    // ─── 小红书操作 ──────────────────────────────────────────

    private fun openXhs(): Boolean {
        return try {
            android.util.Log.d("XhsAutomator", "正在打开小红书, 包名=$XHS_PACKAGE")
            val intent = service.packageManager.getLaunchIntentForPackage(XHS_PACKAGE)
            if (intent != null) {
                android.util.Log.d("XhsAutomator", "找到启动Intent, 启动Activity")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                service.startActivity(intent)
                true
            } else {
                // 兜底1：组件名直启（adb install 后包缓存没刷新时 LaunchIntent 可能为 null）
                android.util.Log.w("XhsAutomator", "LaunchIntent为null, 尝试组件名直启")
                val cmpIntent = Intent().apply {
                    setClassName(XHS_PACKAGE, "com.xingin.xhs.index.v2.IndexActivityV2")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try {
                    service.startActivity(cmpIntent)
                    true
                } catch (e1: Exception) {
                    // 兜底2：URL Scheme
                    android.util.Log.w("XhsAutomator", "组件名直启失败, 尝试URL Scheme", e1)
                    val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("xhsdiscover://"))
                    uriIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        service.startActivity(uriIntent)
                        true
                    } catch (e2: Exception) {
                        android.util.Log.e("XhsAutomator", "所有启动方式均失败", e2)
                        false
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("XhsAutomator", "打开小红书失败", e)
            false
        }
    }

    private fun navigateToSearch(): Boolean {
        // DEBUG: 打印首页节点
        dumpSearchPage()

        // 策略1：找搜索图标 ViewId
        if (retryFindNode({ findNodeById(SEARCH_ICON_IDS) }) { node ->
            android.util.Log.d("XhsAutomator", "navigateToSearch: ViewId 命中")
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
        }) return true

        // 策略2：文本匹配
        if (retryFindNode({ findNodeByText(SEARCH_ICON_TEXTS) }) { node ->
            android.util.Log.d("XhsAutomator", "navigateToSearch: 文本 命中")
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
        }) return true

        // 策略3：contentDescription
        if (retryFindNode({ findNodeByDesc(SEARCH_ICON_DESCS) }) { node ->
            android.util.Log.d("XhsAutomator", "navigateToSearch: desc 命中")
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
        }) return true

        android.util.Log.e("XhsAutomator", "navigateToSearch: 所有策略失败")
        return false
    }

    private fun performSearch(keyword: String): Boolean {
        android.util.Log.d("XhsAutomator", "performSearch: keyword=[$keyword]")

        var searchBounds: android.graphics.Rect? = null

        // 1. 找到搜索框
        val inputFound = retryFindNode({
            findEditableNodeAtTop()
        }) { node ->
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            searchBounds = android.graphics.Rect(r)
            android.util.Log.d("XhsAutomator", "找到搜索框: bounds=$r")
            node.recycle()
        }
        if (!inputFound || searchBounds == null) {
            android.util.Log.e("XhsAutomator", "找不到搜索输入框")
            return false
        }

        // 2. 剪贴板 + 粘贴 — 手势模拟点键盘不可靠（dispatchGesture 很可能没命中 IME 窗口）
        val cx = searchBounds!!.centerX().toFloat()
        val cy = searchBounds!!.centerY().toFloat()

        val cm = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("search", keyword))

        // 策略A：聚焦 + ACTION_PASTE
        var ok = false
        retryFindNode({
            findEditableNodeAtTop()
        }) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            randomDelay(200, 400)
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            android.util.Log.d("XhsAutomator", "PASTE 后 text=[${node.text}]")
            ok = (node.text?.toString()?.isNotBlank() == true)
            node.recycle()
        }

        // 策略B：长按 → 点"粘贴"菜单
        if (!ok) {
            android.util.Log.d("XhsAutomator", "PASTE 没生效, 长按唤菜单")
            val path = android.graphics.Path().apply { moveTo(cx, cy) }
            val longPress = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 800))
                .build()
            service.dispatchGesture(longPress, null, null)
            randomDelay(800, 1200)
            retryFindNode({
                findNodeByText(listOf("粘贴", "Paste"))
            }) { pasteMenu ->
                pasteMenu.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                android.util.Log.d("XhsAutomator", "点击粘贴菜单")
                pasteMenu.recycle()
            }
        }

        // 3. 点搜索
        return retryFindNode({
            findNodeByText(listOf("Search", "搜索"))
        }) { node ->
            android.util.Log.d("XhsAutomator", "点击搜索按钮")
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    /**
     * 通过 shell "input text" 发送真实键盘输入（= adb shell input text）
     */
    private fun inputViaShell(text: String): Boolean {
        return try {
            val escaped = text.replace("'", "'\\''")
            val cmd = "input text '$escaped'"
            android.util.Log.d("XhsAutomator", "shell: $cmd")
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val exit = p.waitFor()
            android.util.Log.d("XhsAutomator", "shell exit=$exit")
            exit == 0
        } catch (e: Exception) {
            android.util.Log.w("XhsAutomator", "shell失败", e)
            false
        }
    }

    /**
     * 手势坐标点击数字键 0-9（兜底）
     */
    private fun typeViaGesture(text: String) {
        val metrics = service.resources.displayMetrics
        val sw = metrics.widthPixels.toFloat()
        val sh = metrics.heightPixels.toFloat()
        val kbTop = sh * 0.63f
        val kbH = sh * 0.37f
        val rowH = kbH / 4f
        val numRowY = kbTop + rowH * 0.5f  // 第1行中心 = 数字行
        val keyW = sw / 10f

        for (ch in text) {
            if (cancelled) return
            val digit = ch.toString().toIntOrNull() ?: continue
            val idx = if (digit == 0) 9 else digit - 1
            val kx = keyW * (idx + 0.5f)
            android.util.Log.d("XhsAutomator", "gesture tap '$digit' → ($kx, $numRowY)")
            dispatchTap(kx, numRowY)
            randomDelay(60, 140)
        }
    }

    /** 查找屏幕上半部分的 EditText（搜索框通常在顶部） */
    private fun findEditableNodeAtTop(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val metrics = service.resources.displayMetrics
        val maxBottom = (metrics.heightPixels * 0.35).toInt()  // 屏幕顶部 35%
        val result = findEditableInRegion(root, 0, maxBottom)
        if (result != root) root.recycle()
        return result
    }

    private fun findEditableInRegion(node: AccessibilityNodeInfo, minTop: Int, maxBottom: Int): AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (node.isEditable && rect.top >= minTop && rect.bottom <= maxBottom) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableInRegion(child, minTop, maxBottom)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    /** DEBUG: 打印搜索页面所有节点 */
    private fun dumpSearchPage() {
        val root = service.rootInActiveWindow ?: return
        try {
            val nodes = mutableListOf<String>()
            collectVisibleNodes(root, 0, nodes)
            android.util.Log.d("XhsAutomator", "=== 搜索页面节点 (${nodes.size}) ===")
            for (s in nodes.take(50)) android.util.Log.d("XhsAutomator", s)
        } finally {
            root.recycle()
        }
    }

    private fun collectVisibleNodes(node: AccessibilityNodeInfo, depth: Int, out: MutableList<String>) {
        if (depth > 8 || !node.isVisibleToUser) return
        val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val txt = node.text?.toString()?.take(40)?.replace("\n", "\\n") ?: ""
        val desc = node.contentDescription?.toString()?.take(30) ?: ""
        val vid = node.viewIdResourceName?.substringAfterLast('/') ?: ""
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val flags = buildString {
            if (node.isClickable) append("C")
            if (node.isEditable) append("E")
            if (node.isFocused) append("F")
        }
        val indent = "  ".repeat(depth)
        out.add("$indent($flags)[$cls] vid=$vid txt=[$txt] dsc=[$desc] y=${rect.top}-${rect.bottom}")
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectVisibleNodes(child, depth + 1, out)
            child.recycle()
        }
    }

    private fun enterFirstNote(): Boolean {
        android.util.Log.d("XhsAutomator", "=== enterFirstNote 开始 ===")
        // 打印搜索结果页全部可见节点
        dumpAllNodes("搜索结果页")

        // 策略1：点「用户」Tab → 点第一个用户
        val userTab = findNodeByText(listOf("用户"))
        android.util.Log.d("XhsAutomator", "查找「用户」Tab: ${if (userTab != null) "找到" else "未找到"}")
        if (userTab != null) {
            userTab.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            userTab.recycle()
            randomDelay(800, 1200)
            // 再打一次看用户Tab切换后的页面
            dumpAllNodes("用户Tab页")

            // 尝试多种方式找第一个用户
            var clicked = retryFindNode({
                val r = findFirstClickableInList()
                android.util.Log.d("XhsAutomator", "findFirstClickableInList: ${if (r != null) "找到" else "null"}")
                r
            }) { node ->
                android.util.Log.d("XhsAutomator", "点击(列表): cls=${node.className} text=[${node.text}] desc=[${node.contentDescription}]")
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            if (!clicked) {
                clicked = retryFindNode({
                    val r = findAnyClickableBelowHeader()
                    android.util.Log.d("XhsAutomator", "findAnyClickableBelowHeader: ${if (r != null) "找到" else "null"}")
                    r
                }) { node ->
                    android.util.Log.d("XhsAutomator", "点击(区域): cls=${node.className} text=[${node.text}] desc=[${node.contentDescription}]")
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
            if (!clicked) {
                android.util.Log.d("XhsAutomator", "节点搜索全失败, 手势兜底")
                val metrics = service.resources.displayMetrics
                dispatchTap(metrics.widthPixels * 0.5f, metrics.heightPixels * 0.3f)
                randomDelay(1000, 1500)
                return true
            }
            return true
        }

        // 策略2：点「笔记」Tab
        val noteTab = findNodeByText(listOf("笔记"))
        if (noteTab != null) {
            noteTab.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            noteTab.recycle()
            randomDelay(800, 1200)
        }
        return retryFindNode({ findFirstClickableInList() ?: findAnyClickableBelowHeader() }) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    /**
     * 在用户主页点第一个笔记缩略图（进入笔记详情页）
     *
     * 策略优先级：
     *   1. 找笔记网格 RecyclerView（屏幕下部、占宽、多子项）→ 点第一项
     *   2. 找任意 RecyclerView 第一可点击项
     *   3. 手势兜底：点屏幕 40% 高度处（笔记网格第一行位置）
     */
    private fun clickFirstNoteOnProfile(): Boolean {
        android.util.Log.d("XhsAutomator", "=== clickFirstNoteOnProfile ===")

        // 策略1：找笔记网格（屏幕下半部分的 RecyclerView，宽度接近屏宽）
        var clicked = retryFindNode({
            findNoteGrid()
        }) { node ->
            android.util.Log.d("XhsAutomator", "点击笔记网格: cls=${node.className}")
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        if (clicked) return true

        // 策略2：找任意 RecyclerView 第一可点击项
        clicked = retryFindNode({ findFirstClickableInList() ?: findAnyClickableBelowHeader() }) { node ->
            android.util.Log.d("XhsAutomator", "点击(列表): cls=${node.className}")
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        if (clicked) return true

        // 策略3：手势兜底
        android.util.Log.d("XhsAutomator", "节点搜索失败，手势点击笔记位置")
        val metrics = service.resources.displayMetrics
        dispatchTap(metrics.widthPixels * 0.33f, metrics.heightPixels * 0.40f)
        randomDelay(1000, 1500)
        return true
    }

    /**
     * 在用户主页找笔记网格。启发式规则：
     *   1. className 含 RecyclerView
     *   2. 宽度 >= 屏幕宽度 80%
     *   3. top 在屏幕高度 25% 以下（排除头部信息区+Tab栏）
     */
    private fun findNoteGrid(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val metrics = service.resources.displayMetrics
        val minW = (metrics.widthPixels * 0.8).toInt()
        val minTop = (metrics.heightPixels * 0.25).toInt()

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectRecyclerViews(root, minW, minTop, candidates)

        if (candidates.isNotEmpty()) {
            // 找子项最多的 RecyclerView（笔记网格通常有很多缩略图）
            candidates.sortByDescending { it.childCount }
            val best = candidates.first()
            android.util.Log.d("XhsAutomator", "findNoteGrid: 候选${candidates.size}个, 选childCount=${best.childCount}")

            // 在笔记网格里找第一个可点击子项
            for (i in 0 until best.childCount) {
                val child = best.getChild(i) ?: continue
                val deep = findClickableDescendant(child)
                if (deep != null) {
                    if (best != root) best.recycle()
                    if (root != deep) root.recycle()
                    child.recycle()
                    return deep
                }
                child.recycle()
            }
            if (best != root) best.recycle()
        }
        root.recycle()
        return null
    }

    private fun collectRecyclerViews(
        node: AccessibilityNodeInfo,
        minW: Int,
        minTop: Int,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        val cls = node.className?.toString() ?: ""
        if (cls.contains("RecyclerView") || cls.contains("GridView")) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() >= minW && rect.top >= minTop) {
                out.add(AccessibilityNodeInfo.obtain(node))
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectRecyclerViews(child, minW, minTop, out)
            child.recycle()
        }
    }

    /** 完整dump当前页面所有可见节点 */
    private fun dumpAllNodes(label: String) {
        val root = service.rootInActiveWindow ?: return
        try {
            val nodes = mutableListOf<String>()
            collectVisibleNodes(root, 0, nodes)
            android.util.Log.d("XhsAutomator", "=== [$label] 全部节点 (${nodes.size}) ===")
            for (s in nodes.take(60)) android.util.Log.d("XhsAutomator", s)
        } finally { root.recycle() }
    }

    /** 找 Tab 栏下方容器里的第一个"真正的用户结果"（排除反馈等无关按钮） */
    private fun findAnyClickableBelowHeader(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val metrics = service.resources.displayMetrics
        val headerEnd = (metrics.heightPixels * 0.12).toInt()
        val result = findUserResultInRegion(root, headerEnd, metrics.heightPixels)
        if (result != root) root.recycle()
        return result
    }

    private fun findUserResultInRegion(node: AccessibilityNodeInfo, minTop: Int, maxBottom: Int): AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        // 排除反馈/无关按钮
        val isBad = text.contains("反馈") || desc.contains("反馈") ||
                    text.contains("feedback") || desc.contains("feedback") ||
                    text.contains("举报") || desc.contains("举报")
        // 用户结果卡片：高度适中（约 50-200dp），非无关按钮
        val density = service.resources.displayMetrics.density
        val minH = (50 * density).toInt()
        val maxH = (200 * density).toInt()
        val heightOk = rect.height() in minH..maxH
        if (!isBad && node.isClickable && heightOk && rect.top >= minTop && rect.bottom <= maxBottom) {
            android.util.Log.d("XhsAutomator", "用户结果: cls=${node.className} text=[$text] h=${rect.height()}")
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findUserResultInRegion(child, minTop, maxBottom)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun performLike(): Boolean {
        // DEBUG: 打印笔记页面节点
        dumpAllNodes("笔记页")

        // 1. 先查是否已点赞 — 只用 desc/text，不用 isSelected/isChecked（避免误判视频播放暂停等）
        val alreadyLiked = run {
            val root = service.rootInActiveWindow ?: return@run null
            val result = findNodeRecursive(root) { node ->
                if (!node.isClickable) return@findNodeRecursive false
                val desc = node.contentDescription?.toString().orEmpty()
                val text = node.text?.toString().orEmpty()
                desc.contains("已赞") || text.contains("已赞")
            }
            root.recycle()
            result
        }
        if (alreadyLiked != null) {
            android.util.Log.d("XhsAutomator", "已点赞（desc/text确认），跳过")
            alreadyLiked.recycle()
            return true
        }

        // 2. 精准策略：先找 desc 含 "Collect" 的收藏按钮，点赞在其左边（同一底部栏）
        val liked = findAndClickBottomLikeButton()
        if (liked) return true

        // 3. 手势兜底：底部栏点赞按钮（多候选位，适配不同屏幕/布局）
        android.util.Log.d("XhsAutomator", "手势兜底: 点底部栏点赞位置")
        val mw = service.resources.displayMetrics.widthPixels.toFloat()
        val mh = service.resources.displayMetrics.heightPixels.toFloat()
        // 底部栏4个按钮从左: 评论输入框|点赞|收藏|评论 — 点赞约在 x=60-68%
        val candidates = listOf(
            mw * 0.64f to mh * 0.965f,  // 主候选
            mw * 0.62f to mh * 0.965f,  // 稍左
            mw * 0.66f to mh * 0.965f,  // 稍右
            mw * 0.64f to mh * 0.94f,   // 稍上（避开导航栏）
        )
        for ((x, y) in candidates) {
            dispatchTap(x, y)
            randomDelay(100, 200)
        }
        randomDelay(500, 1000)
        return true
    }

    /**
     * 在底部工具栏精准定位点赞按钮。
     *
     * XHS 笔记视频页底部栏从左到右：评论输入(宽) | 点赞 | 收藏 | 评论
     * 收藏按钮 desc 含 "Collect"，找到它后向左偏移半个按钮宽度即是点赞。
     * 备选：收集底部所有可点击节点（去重后），用 "非Collect、非评论、非输入框" 条件过滤。
     */
    private fun findAndClickBottomLikeButton(): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val metrics = service.resources.displayMetrics
        val bottomY = (metrics.heightPixels * 0.92).toInt()

        // 收集底部所有可点击节点
        val allBottom = mutableListOf<AccessibilityNodeInfo>()
        collectAllBottomClickable(root, bottomY, allBottom)

        // 按中心 X 坐标去重（避免父子节点重叠，同一 X±30px 视为同一按钮，只保留最深的子节点）
        val deduped = deduplicateByX(allBottom, 30)

        // 按 X 坐标从左到右排序
        deduped.sortBy {
            val r = android.graphics.Rect()
            it.getBoundsInScreen(r)
            r.centerX()
        }

        android.util.Log.d("XhsAutomator", "底部去重后可点击节点共${deduped.size}个")
        for ((i, c) in deduped.withIndex()) {
            val r = android.graphics.Rect()
            c.getBoundsInScreen(r)
            android.util.Log.d("XhsAutomator", "  [$i] cls=${c.className} dsc=[${c.contentDescription}] txt=[${c.text}] cx=${r.centerX()}")
        }

        // 策略A：找 desc 含 "Collect" 的收藏按钮，点赞在其左边（索引-1）
        val collectIdx = deduped.indexOfFirst {
            val d = it.contentDescription?.toString().orEmpty()
            d.contains("Collect", ignoreCase = true) || d.contains("收藏")
        }
        if (collectIdx > 0) {
            val likeBtn = deduped[collectIdx - 1]
            val r = android.graphics.Rect()
            likeBtn.getBoundsInScreen(r)
            android.util.Log.d("XhsAutomator", "策略A: 收藏在[$collectIdx], 点赞在[${collectIdx-1}], dsc=[${likeBtn.contentDescription}] cx=${r.centerX()}")
            likeBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            for (c in allBottom) c.recycle()
            root.recycle()
            randomDelay(RETRY_DELAY_MS, MAX_STEP_DELAY)
            return true
        }

        // 策略B：排除输入框(isEditable/宽>屏宽50%)和评论(desc含"评论/Cmt")，剩下第1个就是点赞
        val sw = metrics.widthPixels
        val candidates = deduped.filter { node ->
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            val d = node.contentDescription?.toString().orEmpty()
            val t = node.text?.toString().orEmpty()
            val isInputBox = node.isEditable || r.width() > sw * 0.5
            val isCommentBtn = d.contains("评论") || d.contains("Cmt") || t.contains("评论")
            val isCollect = d.contains("Collect", ignoreCase = true) || d.contains("收藏")
            !isInputBox && !isCommentBtn && !isCollect
        }
        android.util.Log.d("XhsAutomator", "策略B: 候选点赞节点${candidates.size}个")
        if (candidates.isNotEmpty()) {
            val likeBtn = candidates.first()
            android.util.Log.d("XhsAutomator", "策略B: 点击 dsc=[${likeBtn.contentDescription}]")
            likeBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            for (c in allBottom) c.recycle()
            root.recycle()
            randomDelay(RETRY_DELAY_MS, MAX_STEP_DELAY)
            return true
        }

        for (c in allBottom) c.recycle()
        root.recycle()
        return false
    }

    /**
     * 按中心 X 坐标去重：X 坐标相差 <= threshold 的节点视为重叠，只保留子节点（childCount==0 优先）
     */
    private fun deduplicateByX(nodes: List<AccessibilityNodeInfo>, threshold: Int): MutableList<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val rects = nodes.map { node ->
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            r
        }
        for (i in nodes.indices) {
            val cx = rects[i].centerX()
            val dominated = nodes.indices.any { j ->
                if (j == i) return@any false
                val cxj = rects[j].centerX()
                Math.abs(cx - cxj) <= threshold && nodes[j].childCount == 0 && nodes[i].childCount > 0
            }
            if (!dominated) result.add(nodes[i])
        }
        return result
    }

    /** 收集屏幕底部区域的所有可点击节点 */
    private fun collectAllBottomClickable(node: AccessibilityNodeInfo, minTop: Int, out: MutableList<AccessibilityNodeInfo>) {
        if (!node.isVisibleToUser) return
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (node.isClickable && rect.top >= minTop && rect.bottom >= minTop) {
            out.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllBottomClickable(child, minTop, out)
            child.recycle()
        }
    }

    /**
     * 执行评论 — 找底部 EditText → 输入文字 → 点发送
     */
    private fun performComment(commentText: String): Boolean {
        android.util.Log.d("XhsAutomator", "=== 开始评论: $commentText ===")

        val screenW = service.resources.displayMetrics.widthPixels
        val screenH = service.resources.displayMetrics.heightPixels
        val bottomThreshold = (screenH * 0.6).toInt()
        var inputBounds: android.graphics.Rect? = null

        // ── Step 1: 先尝试直接找底部 EditText（可能已经在评论页面） ──
        retryFindNode({
            findEditableNodeAtBottom(bottomThreshold)
        }) { node ->
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            inputBounds = android.graphics.Rect(r)
            android.util.Log.d("XhsAutomator", "直接找到评论输入框: bounds=$r")
            node.recycle()
        }

        // ── Step 2: 找不到 → 点击底部评论区域激活输入框 ──
        if (inputBounds == null) {
            android.util.Log.d("XhsAutomator", "未找到 EditText，点击底部评论区域激活输入...")

            // 策略A: 点击"Say something..."/评论区位置（多候选位，避开导航栏）
            val commentTapCandidates = listOf(
                screenW * 0.5f to screenH * 0.95f,   // 主候选：距底部 5%
                screenW * 0.5f to screenH * 0.93f,   // 稍上
                screenW * 0.3f to screenH * 0.95f,   // 偏左（部分版本输入框在左下角）
                screenW * 0.5f to screenH * 0.90f,   // 更上
            )
            for ((tapX, tapY) in commentTapCandidates) {
                dispatchTap(tapX, tapY)
                randomDelay(200, 400)
            }
            randomDelay(1000, 1800)           // 等待键盘弹出 + EditText 可聚焦

            // 再次尝试找 EditText
            retryFindNode({
                findEditableNodeAtBottom(bottomThreshold)
            }) { node ->
                val r = android.graphics.Rect()
                node.getBoundsInScreen(r)
                inputBounds = android.graphics.Rect(r)
                android.util.Log.d("XhsAutomator", "激活后找到输入框: bounds=$r")
                node.recycle()
            }
        }

        // ── Step 3: 仍然找不到 → 点击评论按钮打开评论面板 ──
        if (inputBounds == null) {
            android.util.Log.d("XhsAutomator", "仍未找到 EditText，尝试点击评论按钮...")
            var commentBtnBounds: android.graphics.Rect? = null
            retryFindNode({
                val root = service.rootInActiveWindow ?: return@retryFindNode null
                val result = findCommentButton(root)
                if (result != root) root.recycle()
                result
            }) { commentBtn ->
                val r = android.graphics.Rect()
                commentBtn.getBoundsInScreen(r)
                commentBtnBounds = android.graphics.Rect(r)
                android.util.Log.d("XhsAutomator", "找到评论按钮: ${commentBtn.contentDescription}, bounds=$r")

                // 用真实手势点击（不用 ACTION_CLICK，某些按钮对无障碍点击不响应）
                dispatchTap(r.exactCenterX(), r.exactCenterY())
                commentBtn.recycle()
            }
            randomDelay(2000, 3000)  // 等评论面板 + 键盘弹出

            // dump 当前活动窗口
            dumpAllNodes("评论面板(点击评论按钮后)")

            // dump 所有窗口信息（评论面板可能是独立 Dialog/PopupWindow）
            android.util.Log.d("XhsAutomator", "=== 所有无障碍窗口 ===")
            val allWindows = service.windows
            if (allWindows != null) {
                for (w in allWindows) {
                    val root = w.root
                    if (root != null) {
                        val wr = android.graphics.Rect()
                        root.getBoundsInScreen(wr)
                        android.util.Log.d("XhsAutomator", "  Window layer=${w.layer} type=${w.type} title=${w.title} isActive=${w.isActive} bounds=$wr childCount=${root.childCount}")
                        root.recycle()
                    }
                }
            }

            // 搜索所有窗口（评论面板可能是独立 Dialog/PopupWindow）
            android.util.Log.d("XhsAutomator", "评论面板单窗口未找到 EditText，搜索所有窗口...")
            retryFindNode({
                findEditableInAllWindows()
            }) { node ->
                val r = android.graphics.Rect()
                node.getBoundsInScreen(r)
                inputBounds = android.graphics.Rect(r)
                android.util.Log.d("XhsAutomator", "所有窗口中找到输入框: bounds=$r, cls=${node.className}")
                node.recycle()
            }

            // 全树搜索 EditText（不限底部）
            if (inputBounds == null) {
                retryFindNode({
                    findEditableNode()
                }) { node ->
                    val r = android.graphics.Rect()
                    node.getBoundsInScreen(r)
                    inputBounds = android.graphics.Rect(r)
                    android.util.Log.d("XhsAutomator", "全树搜索找到输入框: bounds=$r, cls=${node.className}")
                    node.recycle()
                }
            }

            // 宽松匹配
            if (inputBounds == null) {
                android.util.Log.d("XhsAutomator", "全树搜索也失败，尝试找任何可获取焦点的输入节点...")
                retryFindNode({
                    val root = service.rootInActiveWindow ?: return@retryFindNode null
                    val result = findAnyInputNode(root)
                    if (result != root) root.recycle()
                    result
                }) { node ->
                    val r = android.graphics.Rect()
                    node.getBoundsInScreen(r)
                    inputBounds = android.graphics.Rect(r)
                    android.util.Log.d("XhsAutomator", "找到可输入节点: bounds=$r, cls=${node.className}, text=${node.text}")
                    node.recycle()
                }
            }
        }

        if (inputBounds == null) {
            android.util.Log.e("XhsAutomator", "找不到评论输入框（已尝试直接查找、点击底部、点击评论按钮、全树搜索）")

            // 最后兜底：直接用剪贴板粘贴到评论区
            android.util.Log.d("XhsAutomator", "尝试剪贴板粘贴兜底...")
            var pasteWorked = false
            try {
                val cm = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("comment", commentText))
                randomDelay(300, 500)

                // 再次点击底部评论区域确保焦点
                dispatchTap(screenW * 0.5f, screenH * 0.95f)
                randomDelay(800, 1200)

                // 尝试在根窗口执行 paste
                val root2 = service.rootInActiveWindow
                if (root2 != null) {
                    pasteIntoFocused(root2)
                    root2.recycle()
                }
                randomDelay(600, 1000)
                pasteWorked = true
                android.util.Log.d("XhsAutomator", "粘贴兜底已执行")
            } catch (e: Exception) {
                android.util.Log.w("XhsAutomator", "粘贴兜底异常: ${e.message}")
            }

            if (!pasteWorked) {
                return false
            }
        }

        // ── Step 4-6: 有输入框坐标时 → 点击获焦 → 输入文字 ──
        if (inputBounds != null) {
            val cx = inputBounds.centerX().toFloat()
            val cy = inputBounds.centerY().toFloat()
            dispatchTap(cx, cy)
            randomDelay(700, 1100)

            // 键盘节点逐字符输入
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
            android.util.Log.d("XhsAutomator", "键盘节点: $clicked/${commentText.length} 字符")

            // 键盘输入不完整 → SET_TEXT 兜底（仅一次）
            var textEntered = clicked >= commentText.length
            if (!textEntered) {
                val setWorked = retryFindNode({
                    findEditableNodeAtBottom(bottomThreshold) ?: findEditableNode()
                }) { node ->
                    val before = node.text?.toString() ?: ""
                    val setArgs = android.os.Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, commentText)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
                    val after = node.text?.toString() ?: ""
                    textEntered = after.contains(commentText)
                    android.util.Log.d("XhsAutomator", "SET_TEXT before=[$before] after=[$after] matched=$textEntered")
                    node.recycle()
                }
                randomDelay(400, 700)
                dispatchTap(cx, cy)
                randomDelay(500, 800)

                // SET_TEXT 也没成功 → PASTE 终极兜底
                if (!textEntered) {
                    try {
                        val cm = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("comment", commentText))
                        randomDelay(200, 400)
                        retryFindNode({ findEditableNodeAtBottom(bottomThreshold) ?: findEditableNode() }) { pasteNode ->
                            pasteNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                            randomDelay(100, 200)
                            pasteNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                            android.util.Log.d("XhsAutomator", "PASTE 完成, text=${pasteNode.text}")
                            pasteNode.recycle()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("XhsAutomator", "粘贴异常: ${e.message}")
                    }
                }
            }
        }

        // ── Step 7: 找发送按钮并点击 ──
        randomDelay(MIN_STEP_DELAY, MAX_STEP_DELAY)
        return retryFindNode({
            findNodeById(COMMENT_POST_IDS) ?: findNodeByText(listOf("发送", "Send", "发布", "评论"))
        }) { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            android.util.Log.d("XhsAutomator", "评论已发送")
            randomDelay(1000, 2000)
        }
    }

    /** 搜索 AccessibilityService 的所有窗口（含 Dialog/PopupWindow）中的可编辑节点 */
    private fun findEditableInAllWindows(): AccessibilityNodeInfo? {
        val windows = service.windows ?: return null
        for (window in windows) {
            val root = window.root ?: continue
            val found = findEditableRecursive(root)
            if (found != null) {
                android.util.Log.d("XhsAutomator", "在窗口 layer=${window.layer} title=${window.title} 找到 EditText")
                // 回收其他窗口
                for (other in windows) {
                    if (other !== window) other.root?.recycle()
                }
                return found
            }
            root.recycle()
        }
        return null
    }

    /** 在无障碍树中找评论按钮（desc 含"评论"的底部按钮） */
    private fun findCommentButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screenH = service.resources.displayMetrics.heightPixels
        val bottomThreshold = (screenH * 0.85).toInt()
        return findCommentBtnRecursive(root, bottomThreshold)
    }

    private fun findCommentBtnRecursive(node: AccessibilityNodeInfo, bottomThreshold: Int): AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val desc = node.contentDescription?.toString().orEmpty()

        if (node.isClickable && rect.bottom >= bottomThreshold &&
            (desc.contains("评论") || desc.contains("Comment"))) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findCommentBtnRecursive(child, bottomThreshold)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    /**
     * 在整棵树中搜索任何可输入文字的节点（宽松匹配，不仅 isEditable）
     * 包括：EditText / TextInputEditText / 可聚焦且有 hint 文字的节点
     */
    private fun findAnyInputNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findAnyInputRecursive(root)
    }

    private fun findAnyInputRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return AccessibilityNodeInfo.obtain(node)

        val cls = node.className?.toString().orEmpty()
        val isEditText = cls.endsWith("EditText") || cls.contains("TextInput")
        val isFocused = node.isFocused

        if ((isEditText || isFocused) && node.isClickable) {
            // 可能是自定义输入组件
            return AccessibilityNodeInfo.obtain(node)
        }

        // 检查是否有 hint 或 placeholder 文字（如 "Say something..."）
        val hint = node.hintText?.toString().orEmpty()
        if (hint.isNotEmpty() && node.isClickable) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findAnyInputRecursive(child)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    /** 在树上找已经获焦的节点并执行 PASTE */
    private fun pasteIntoFocused(root: AccessibilityNodeInfo) {
        val focused = findFocusedRecursive(root)
        if (focused != null) {
            try {
                focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                android.util.Log.d("XhsAutomator", "PASTE 到获焦节点: ${focused.className}")
            } catch (e: Exception) {
                android.util.Log.w("XhsAutomator", "PASTE 异常: ${e.message}")
            }
            focused.recycle()
        } else {
            android.util.Log.d("XhsAutomator", "未找到获焦节点，无法 paste")
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

    // ─── 通用工具 ──────────────────────────────────────────

    private fun dispatchTap(x: Float, y: Float) {
        val path = android.graphics.Path().apply { moveTo(x, y) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        service.dispatchGesture(gesture, null, null)
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
        node: AccessibilityNodeInfo,
        target: String,
        threshold: Int
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

    private fun findFirstClickableInList(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val listNode = findRecyclerView(root) ?: findListView(root)
        if (listNode != null) {
            for (i in 0 until listNode.childCount) {
                val child = listNode.getChild(i) ?: continue
                // 直接可点击 → 返回
                if (child.isClickable) {
                    if (listNode != root) listNode.recycle()
                    if (root != child) root.recycle()
                    return child
                }
                // 不可点击 → 递归找子孙中有没有可点击的（XHS 列表项 Button 本身不 clickable）
                val deep = findClickableDescendant(child)
                if (deep != null) {
                    if (listNode != root) listNode.recycle()
                    if (root != deep) root.recycle()
                    return deep
                }
                child.recycle()
            }
            if (listNode != root) listNode.recycle()
        }
        root.recycle()
        return null
    }

    /** 在节点子树中找第一个可点击的子孙节点 */
    private fun findClickableDescendant(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findClickableDescendant(child)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
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

    // ─── 节点查找 ──────────────────────────────────────────

    private fun findNodeById(ids: List<String>): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val result = findNodeByIdRecursive(root, ids)
        if (result != root) root.recycle()
        return result
    }

    private fun findNodeByIdRecursive(node: AccessibilityNodeInfo, ids: List<String>): AccessibilityNodeInfo? {
        val vid = node.viewIdResourceName?.substringAfterLast('/') ?: ""
        if (vid.isNotEmpty() && ids.contains(vid)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByIdRecursive(child, ids)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findNodeByText(texts: List<String>): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val result = findNodeByTextRecursive(root, texts)
        if (result != root) root.recycle()
        return result
    }

    private fun findNodeByTextRecursive(node: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        if (texts.any { nodeText.contains(it) }) return node
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
        if (descs.any { desc.contains(it) }) return node
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

    private fun findNodeByText(texts: List<String>, filter: (String) -> Boolean): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val result = findNodeByTextFilterRecursive(root, texts, filter)
        if (result != root) root.recycle()
        return result
    }

    private fun findNodeByTextFilterRecursive(
        node: AccessibilityNodeInfo,
        texts: List<String>,
        filter: (String) -> Boolean
    ): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (texts.any { nodeText.contains(it) } && filter(nodeText)) return node
        if (texts.any { desc.contains(it) } && filter(desc)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByTextFilterRecursive(child, texts, filter)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findNodeRecursive(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
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

    // ─── 截图 + 返回 ──────────────────────────────────────

    /**
     * 截图 — 多策略降级方案
     *
     * 策略优先级：
     *   1. AccessibilityService.takeScreenshot() — API 34+，原生无障碍截图，零 shell 依赖
     *   2. screencap 写文件到 /data/local/tmp/（世界可写目录，绕过 stdout pipe 限制）
     *   3. screencap -p stdout（传统方式，兼容旧设备）
     *   4. GLOBAL_ACTION_TAKE_SCREENSHOT + 多目录/MediaStore 扫描
     *   5. SurfaceControl 反射（隐藏 API）
     */
    private fun takeScreenshot(): String? {
        android.util.Log.d("XhsAutomator", "=== 开始截图 (SDK=${Build.VERSION.SDK_INT}) ===")

        // 策略1：AccessibilityService.takeScreenshot() — Android 14+ 原生 API
        if (Build.VERSION.SDK_INT >= 34) {
            val result = takeScreenshotApi34()
            if (result != null) return result
            android.util.Log.w("XhsAutomator", "takeScreenshot API 失败, 降级 screencap")
        }

        // 策略2：screencap 写文件到 /data/local/tmp/（绕过 stdout pipe 权限问题）
        val result = takeScreenshotScreencapToFile()
        if (result != null) return result

        // 策略3：screencap -p stdout（传统方式）
        android.util.Log.d("XhsAutomator", "screencap 写文件失败, 尝试 stdout 管道")
        val result3 = takeScreenshotScreencapStdout()
        if (result3 != null) return result3

        // 策略4：系统截图键 + 多目录/MediaStore 扫描
        android.util.Log.w("XhsAutomator", "screencap stdout 失败, 尝试系统截图键")
        val result4 = takeScreenshotGlobalAction()
        if (result4 != null) return result4

        // 策略5：SurfaceControl 反射（最终兜底）
        android.util.Log.w("XhsAutomator", "系统截图失败, 尝试 SurfaceControl 反射")
        return takeScreenshotReflection()
    }

    /**
     * 策略1：AccessibilityService.takeScreenshot() — API 34+
     * 使用 CountDownLatch 将异步回调转为同步
     */
    @android.annotation.SuppressLint("NewApi")
    private fun takeScreenshotApi34(): String? {
        if (Build.VERSION.SDK_INT < 34) return null
        return try {
            val latch = CountDownLatch(1)
            var bitmap: Bitmap? = null
            var error: String? = null

            val wm = service.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
            val displayId = wm.defaultDisplay?.displayId ?: 0
            android.util.Log.d("XhsAutomator", "调用 AccessibilityService.takeScreenshot(displayId=$displayId)")

            service.takeScreenshot(
                displayId,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                        try {
                            val hwBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            android.util.Log.d("XhsAutomator", "screenshotResult: ${hwBuffer.width}x${hwBuffer.height}, colorSpace=$colorSpace")
                            bitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                        } catch (e: Exception) {
                            error = "wrapHardwareBuffer 失败: ${e.message}"
                            android.util.Log.e("XhsAutomator", error!!, e)
                        } finally {
                            screenshotResult.hardwareBuffer.close()
                        }
                        latch.countDown()
                    }
                    override fun onFailure(errorCode: Int) {
                        error = "takeScreenshot onFailure: errorCode=$errorCode"
                        android.util.Log.e("XhsAutomator", error!!)
                        latch.countDown()
                    }
                }
            )

            // 等待截图完成（最多 5 秒）
            val ok = latch.await(5, TimeUnit.SECONDS)
            if (!ok) {
                android.util.Log.e("XhsAutomator", "takeScreenshot 超时 (5s)")
                return null
            }
            if (bitmap == null) {
                android.util.Log.e("XhsAutomator", "takeScreenshot bitmap 为 null: $error")
                return null
            }

            val dest = prepareDestFile()
            if (dest == null) {
                bitmap!!.recycle()
                return null
            }

            // Bitmap → PNG → 文件
            FileOutputStream(dest).use { out ->
                bitmap!!.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            bitmap!!.recycle()

            android.util.Log.d("XhsAutomator", "takeScreenshot API 成功: ${dest.absolutePath} (${dest.length()} bytes)")
            dest.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("XhsAutomator", "takeScreenshot API 异常", e)
            null
        }
    }

    /**
     * 策略2：screencap 直接写文件到 /sdcard/（shell 用户可写目录）
     *
     * BlueStacks / 部分模拟器上 /data/local/tmp/ 可能被限制写入，
     * 改用 /sdcard/Download/（Android shell 用户 100% 可写）。
     * 读取需要 READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE 权限。
     */
    private fun takeScreenshotScreencapToFile(): String? {
        return try {
            // 检查存储权限
            if (!hasStoragePermission()) {
                android.util.Log.w("XhsAutomator", "无存储权限, 跳过 screencap 写文件")
                return null
            }

            val dest = prepareDestFile() ?: return null
            // 优先使用 /sdcard/Download/ — shell 用户始终可写
            val sdcardDirs = listOf(
                "/sdcard/Download",
                "/sdcard/Pictures",
                "/sdcard"
            )
            val ts = System.currentTimeMillis()
            val fileName = "xhs_screenshot_$ts.png"

            for (sdcardDir in sdcardDirs) {
                val tmpPath = "$sdcardDir/$fileName"
                for (cmd in listOf("screencap", "/system/bin/screencap")) {
                    android.util.Log.d("XhsAutomator", "尝试: $cmd -p $tmpPath")
                    val process = Runtime.getRuntime().exec(arrayOf(cmd, "-p", tmpPath))
                    val stderr = process.errorStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    android.util.Log.d("XhsAutomator", "$cmd 写文件 退出码=$exitCode, stderr=[${stderr.take(200)}]")

                    val tmpFile = java.io.File(tmpPath)
                    if (exitCode == 0 && tmpFile.exists() && tmpFile.length() > 100) {
                        tmpFile.copyTo(dest, overwrite = true)
                        tmpFile.delete()
                        android.util.Log.d("XhsAutomator", "screencap 写文件成功: ${dest.absolutePath} (${dest.length()} bytes)")
                        return dest.absolutePath
                    }
                    tmpFile.delete()
                }
            }

            // su 兜底
            android.util.Log.d("XhsAutomator", "尝试 su -c screencap...")
            try {
                val suTmpPath = "/sdcard/Download/$fileName"
                val suProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "screencap -p $suTmpPath"))
                val suStderr = suProcess.errorStream.bufferedReader().readText()
                val suExit = suProcess.waitFor()
                android.util.Log.d("XhsAutomator", "su screencap 退出码=$suExit, stderr=[${suStderr.take(200)}]")
                val suFile = java.io.File(suTmpPath)
                if (suExit == 0 && suFile.exists() && suFile.length() > 100) {
                    suFile.copyTo(dest, overwrite = true)
                    suFile.delete()
                    android.util.Log.d("XhsAutomator", "su screencap 成功: ${dest.absolutePath} (${dest.length()} bytes)")
                    return dest.absolutePath
                }
                suFile.delete()
            } catch (e: Exception) {
                android.util.Log.d("XhsAutomator", "su 不可用: ${e.message}")
            }

            android.util.Log.w("XhsAutomator", "screencap 写文件所有路径均失败")
            null
        } catch (e: Exception) {
            android.util.Log.e("XhsAutomator", "screencap 写文件异常", e)
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
     * 策略3：screencap -p 输出到 stdout（传统方式，兼容旧设备）
     */
    private fun takeScreenshotScreencapStdout(): String? {
        return try {
            val dest = prepareDestFile() ?: return null

            val cmdPaths = listOf("screencap", "/system/bin/screencap")
            for (cmd in cmdPaths) {
                android.util.Log.d("XhsAutomator", "尝试: $cmd -p (stdout)")
                val process = Runtime.getRuntime().exec(arrayOf(cmd, "-p"))
                val bytes = process.inputStream.readBytes()
                val stderr = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                android.util.Log.d("XhsAutomator", "$cmd 退出码=$exitCode, stdout=${bytes.size}B, stderr=[${stderr.take(200)}]")

                if (bytes.size > 100 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) {
                    dest.writeBytes(bytes)
                    android.util.Log.d("XhsAutomator", "截图成功($cmd): ${dest.absolutePath} (${dest.length()} bytes)")
                    return dest.absolutePath
                }
            }

            android.util.Log.w("XhsAutomator", "所有 screencap stdout 路径均失败")
            null
        } catch (e: Exception) {
            android.util.Log.e("XhsAutomator", "screencap stdout 异常", e)
            null
        }
    }

    /**
     * 策略4：GLOBAL_ACTION_TAKE_SCREENSHOT + 多目录/MediaStore 扫描
     *
     * 触发系统截图键，然后扫描多个可能的存储位置。
     * BlueStacks 模拟器可能需要更长的等待时间（5秒）。
     * 同时通过 ContentResolver 查询 MediaStore 获取最新截图。
     */
    private fun takeScreenshotGlobalAction(): String? {
        return try {
            android.util.Log.d("XhsAutomator", "触发系统截图键...")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            // BlueStacks 截图可能较慢，等待 5 秒
            Thread.sleep(5000)

            // 方法A：扫描目录（增加 BlueStacks 常见路径）
            val searchDirs = listOf(
                "/sdcard/Pictures/Screenshots",
                "/sdcard/DCIM/Screenshots",
                "/sdcard/Screenshots",
                "/sdcard/Pictures",
                "/sdcard/DCIM",
                "/sdcard/Download",
                "/storage/emulated/0/Pictures/Screenshots",
                "/storage/emulated/0/DCIM/Screenshots",
                "/storage/emulated/0/Pictures",
                "/storage/emulated/0/DCIM",
                // BlueStacks 特定路径
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
                        val name = it.name.lowercase()
                        (name.endsWith(".png") || name.endsWith(".jpg")) && it.isFile
                    }
                } catch (e: Exception) {
                    android.util.Log.d("XhsAutomator", "目录 $dirPath 读取失败: ${e.message}")
                    null
                }
                files?.forEach { f ->
                    if (f.lastModified() > latestTime) {
                        latestTime = f.lastModified()
                        latestFile = f
                    }
                }
                if (files != null && files.isNotEmpty()) {
                    android.util.Log.d("XhsAutomator", "扫描 $dirPath: ${files.size} 图, 最新=${files.maxByOrNull { it.lastModified() }?.name}")
                }
            }

            if (latestFile != null && latestFile!!.length() > 0) {
                val age = System.currentTimeMillis() - latestTime
                android.util.Log.d("XhsAutomator", "目录找到截图: ${latestFile!!.absolutePath} (${latestFile!!.length()}B, ${age}ms前)")
                return copyScreenshot(latestFile!!)
            }

            // 方法B：MediaStore ContentResolver
            // 扩大时间窗口到 30 秒，兼容 BlueStacks 时间漂移
            android.util.Log.d("XhsAutomator", "目录扫描未找到, 尝试 MediaStore（30s窗口）...")
            val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                android.provider.MediaStore.Images.Media.DATA,
                android.provider.MediaStore.Images.Media.DATE_ADDED,
                android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                android.provider.MediaStore.Images.Media.SIZE
            )
            val cursor = service.contentResolver.query(
                uri, projection,
                "${android.provider.MediaStore.Images.Media.DATE_ADDED} > ?",
                arrayOf((System.currentTimeMillis() / 1000 - 30).toString()), // 最近30秒
                "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val dataIdx = it.getColumnIndex(android.provider.MediaStore.Images.Media.DATA)
                    val nameIdx = it.getColumnIndex(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeIdx = it.getColumnIndex(android.provider.MediaStore.Images.Media.SIZE)
                    if (dataIdx >= 0) {
                        val path = it.getString(dataIdx)
                        val name = if (nameIdx >= 0) it.getString(nameIdx) else "?"
                        val size = if (sizeIdx >= 0) it.getLong(sizeIdx) else 0
                        android.util.Log.d("XhsAutomator", "MediaStore 找到: $name @ $path (${size}B)")
                        val f = java.io.File(path)
                        if (f.exists() && f.length() > 0) {
                            return copyScreenshot(f)
                        } else {
                            android.util.Log.d("XhsAutomator", "MediaStore 路径但文件不可读: exists=${f.exists()}, size=${f.length()}")
                        }
                    }
                }
            }
            android.util.Log.w("XhsAutomator", "MediaStore 也未找到截图（扫描了最近30秒）")
            null
        } catch (e: Exception) {
            android.util.Log.e("XhsAutomator", "系统截图键异常", e)
            null
        }
    }

    /** 将截图复制到 app 目录 */
    private fun copyScreenshot(src: java.io.File): String? {
        val dest = prepareDestFile() ?: return null
        try {
            src.copyTo(dest, overwrite = true)
            android.util.Log.d("XhsAutomator", "截图已复制: ${dest.absolutePath} (${dest.length()}B)")
            return dest.absolutePath
        } catch (e: Exception) {
            // 如果不能复制（权限问题），尝试直接返回原路径
            android.util.Log.w("XhsAutomator", "无法复制截图, 尝试使用原路径: ${e.message}")
            return src.absolutePath
        }
    }

    /**
     * 策略5：SurfaceControl.screenshot() 反射 — 多签名自适应 + getMethods 兜底
     *
     * 不同 Android 版本的 screenshot() 签名不同：
     *   API 17-23: screenshot(int w, int h)
     *   API 18-27: screenshot(Rect crop, int w, int h, int minLayer, int maxLayer, boolean useIdentity, int rotation)
     *   API 28-33: screenshot(Rect crop, int w, int h, int rotation)
     *
     * 这里遍历所有返回 Bitmap 的 screenshot 方法，逐个尝试匹配参数个数调用
     */
    private fun takeScreenshotReflection(): String? {
        return try {
            val sc = Class.forName("android.view.SurfaceControl")
            val dm = service.resources.displayMetrics
            val w = dm.widthPixels
            val h = dm.heightPixels
            val crop = Rect(0, 0, w, h)

            // 找出所有返回 Bitmap 的 screenshot 方法（先 declared，再 methods 含继承）
            val methods = sc.declaredMethods.filter {
                it.name == "screenshot" && it.returnType == Bitmap::class.java
            }.ifEmpty {
                android.util.Log.w("XhsAutomator", "declaredMethods 未找到 screenshot, 尝试 getMethods (含继承)...")
                val inherited = sc.methods.filter {
                    it.name == "screenshot" && it.returnType == Bitmap::class.java
                }
                android.util.Log.d("XhsAutomator", "getMethods 找到 ${inherited.size} 个 screenshot 方法")
                // 去重
                inherited.distinctBy { it.parameterTypes.contentToString() }
            }
            if (methods.isEmpty()) {
                android.util.Log.e("XhsAutomator", "SurfaceControl 中未找到 screenshot 方法")
                return null
            }
            android.util.Log.d("XhsAutomator", "找到 ${methods.size} 个 screenshot 方法: ${methods.map { it.parameterTypes.joinToString { c -> c.simpleName } }}")

            // 逐个尝试
            for (m in methods) {
                val paramCount = m.parameterTypes.size
                val args: Array<Any?> = try {
                    when (paramCount) {
                        2 -> arrayOf(w, h)                           // screenshot(int, int)
                        4 -> arrayOf(crop, w, h, 0)                  // screenshot(Rect, int, int, int)
                        7 -> arrayOf(crop, w, h, 0, 0, false, 0)     // screenshot(Rect, int, int, int, int, boolean, int)
                        else -> {
                            android.util.Log.d("XhsAutomator", "跳过 screenshot(${paramCount} 参数)")
                            continue
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.d("XhsAutomator", "参数构造异常 (${paramCount}): ${e.message}")
                    continue
                }

                try {
                    val bitmap = m.invoke(null, *args) as? Bitmap
                    if (bitmap != null && bitmap.width > 0) {
                        val dest = prepareDestFile()
                        if (dest == null) {
                            bitmap.recycle()
                            return null
                        }
                        FileOutputStream(dest).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                        }
                        bitmap.recycle()
                        android.util.Log.d("XhsAutomator", "SurfaceControl 反射成功(${paramCount}参数): ${dest.absolutePath} (${dest.length()} bytes)")
                        return dest.absolutePath
                    }
                    bitmap?.recycle()
                } catch (e: Exception) {
                    android.util.Log.w("XhsAutomator", "screenshot(${paramCount}参数) 调用失败: ${e.message}")
                }
            }

            android.util.Log.e("XhsAutomator", "所有 SurfaceControl.screenshot 签名均失败")
            null
        } catch (e: Exception) {
            android.util.Log.e("XhsAutomator", "SurfaceControl 反射失败", e)
            null
        }
    }

    /** 准备截图目标文件（externalFilesDir/<timestamp>.png） */
    private fun prepareDestFile(): java.io.File? {
        val destDir = service.getExternalFilesDir(null)
        if (destDir == null) {
            android.util.Log.e("XhsAutomator", "getExternalFilesDir 返回 null")
            return null
        }
        return java.io.File(destDir, "xhs_${System.currentTimeMillis()}.png")
    }

    private fun uploadAndSubmit(task: AutoTask, filePath: String): Boolean {
        return try {
            val file = java.io.File(filePath)
            val fileBody = file.readBytes().toRequestBody("image/*".toMediaTypeOrNull())
            val body = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", file.name, fileBody)
                .addFormDataPart("type", "screenshot")
                .build()
            val token = ApiClient.token

            val response = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder()
                    .url("http://10.0.2.2:8086/upload/image")
                    .post(body)
                    .header("Authorization", "Bearer $token")
                    .build()).execute()
            }
            val bodyStr = response.body?.string().orEmpty()
            val json = com.google.gson.Gson().fromJson(bodyStr, Map::class.java)
            val code = (json["code"] as? Double)?.toInt() ?: 0
            if (code != 200) return false
            val data = json["data"] as? Map<*, *>
            val url = data?.get("accessUrl") as? String ?: return false

            // 提交任务
            val submitResp = kotlinx.coroutines.runBlocking {
                ApiClient.apiService.submitTask(task.taskId, mapOf(
                    "screenshotUrls" to listOf(url),
                    "latitude" to 0.0,
                    "longitude" to 0.0
                ))
            }
            android.util.Log.d("XhsAutomator", "提交结果: code=${submitResp.code}, msg=${submitResp.msg}")
            submitResp.code == 200
        } catch (e: Exception) {
            android.util.Log.e("XhsAutomator", "上传失败", e)
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

    /** 关闭小红书 APP */
    private fun closeXhs() {
        try {
            // 方式1：通过 AccessibilityService 按 Home 键回桌面
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            Thread.sleep(500)

            // 方式2：按返回键多次确保退出（兜底）
            repeat(3) {
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                Thread.sleep(300)
            }

            android.util.Log.d("XhsAutomator", "已关闭小红书")
        } catch (e: Exception) {
            android.util.Log.e("XhsAutomator", "关闭小红书失败", e)
        }
    }

    // ─── 辅助 ──────────────────────────────────────────────

    private fun retryFindNode(
        finder: () -> AccessibilityNodeInfo?,
        action: (AccessibilityNodeInfo) -> Unit
    ): Boolean {
        repeat(MAX_RETRIES) {
            if (cancelled) return false
            val node = finder()
            if (node != null) {
                try {
                    action(node)
                    node.recycle()
                    return true
                } catch (e: Exception) {
                    android.util.Log.w("XhsAutomator", "retryFindNode error", e)
                    try { node.recycle() } catch (_: Exception) {}
                }
            }
            randomDelay(RETRY_DELAY_MS, MAX_STEP_DELAY)
        }
        return false
    }

    private fun retryFindNodeNoAction(finder: () -> AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        repeat(MAX_RETRIES / 2) {
            if (cancelled) return null
            val node = finder()
            if (node != null) return node
            randomDelay(RETRY_DELAY_MS / 2, RETRY_DELAY_MS)
        }
        return null
    }

    private fun randomDelay(min: Long, max: Long) {
        if (cancelled) return
        try {
            Thread.sleep(Random.nextLong(min, max + 1))
        } catch (_: InterruptedException) {}
    }

    // ─── 网络 ──────────────────────────────────────────────

    /**
     * 提取搜索关键词：优先 targetUrl（提取用户名），其次 requirements，最后兜底
     */
    private fun extractSearchKeyword(task: AutoTask): String {
        // 优先从 targetUrl 提取（如 https://www.xiaohongshu.com/user/profile/abc → abc）
        val fromUrl = task.targetUrl
            ?.substringAfterLast("/")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (fromUrl != null) {
            android.util.Log.d("XhsAutomator", "搜索词来自 targetUrl: [$fromUrl]")
            return fromUrl
        }
        // 其次从 requirements
        val fromReq = task.requirements?.trim()?.takeIf { it.isNotBlank() }
        if (fromReq != null) {
            android.util.Log.d("XhsAutomator", "搜索词来自 requirements: [$fromReq]")
            return fromReq
        }
        android.util.Log.w("XhsAutomator", "targetUrl 和 requirements 均为空, 兜底搜'小红书'")
        return "小红书"
    }

    private fun fetchCommentWord(task: AutoTask): String? {
        return try {
            val ids = task.commentCategoryIds ?: ""
            val resp = kotlinx.coroutines.runBlocking {
                ApiClient.apiService.getCommentWords(ids)
            }
            resp.data?.firstOrNull()
        } catch (e: Exception) {
            android.util.Log.e("XhsAutomator", "获取评论词失败", e)
            null
        }
    }

    // ─── 进度上报 ──────────────────────────────────────────

    private fun notifyStep(stepId: String, stepName: String, status: Int) {
        // 如果 overlay instance 尚未就绪，post 到主线程重试
        if (AutomationOverlayService.instance == null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                AutomationOverlayService.updateStep(stepName, "进行中...")
            }
        } else {
            AutomationOverlayService.updateStep(stepName, "进行中...")
        }
    }

    private fun notifyStepComplete(stepId: String, stepName: String, status: Int, result: String) {
        // 更新浮动窗口
        AutomationOverlayService.updateStep(stepName, result)

        // 上报服务端（异步）
        reportToServer(stepId, stepName, status, result)

        // 通过回调通知 ViewModel 更新进度（对齐 DouyinAutomator 格式）
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
}
