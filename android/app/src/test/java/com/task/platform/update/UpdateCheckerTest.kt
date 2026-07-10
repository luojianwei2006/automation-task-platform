package com.task.platform.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UpdateChecker 语义化版本比较单元测试（纯 JVM 逻辑，无需 Android SDK / Robolectric）。
 *
 * 约定：isNewVersion(remote, current) == true 当且仅当 remote 比 current 新（remote > current）。
 * 缺失段按 0；空白值返回 false（保守不提示更新）；v / V 前缀与 -beta 后缀被正确处理。
 *
 * 运行：./gradlew :app:testDebugUnitTest --tests "com.task.platform.update.UpdateCheckerTest"
 */
class UpdateCheckerTest {

    @Test
    fun `remote 新版本 1_0_1 vs current 1_0_0 → true`() {
        assertTrue(UpdateChecker.isNewVersion("1.0.1", "1.0.0"))
    }

    @Test
    fun `remote 旧版本 1_0_0 vs current 1_0_1 → false`() {
        assertFalse(UpdateChecker.isNewVersion("1.0.0", "1.0.1"))
    }

    @Test
    fun `相同版本 1_2_3 vs 1_2_3 → false`() {
        assertFalse(UpdateChecker.isNewVersion("1.2.3", "1.2.3"))
    }

    @Test
    fun `v 前缀 v2_0 vs 1_9_9 → true`() {
        assertTrue(UpdateChecker.isNewVersion("v2.0", "1.9.9"))
    }

    @Test
    fun `大写 V 前缀 V1_5 vs 1_4_9 → true`() {
        assertTrue(UpdateChecker.isNewVersion("V1.5", "1.4.9"))
    }

    @Test
    fun `缺失段按 0_2_0 vs 1_9_9 → true`() {
        assertTrue(UpdateChecker.isNewVersion("2.0", "1.9.9"))
    }

    @Test
    fun `current 为空 → false 保守不更新`() {
        assertFalse(UpdateChecker.isNewVersion("1.0.1", ""))
    }

    @Test
    fun `remote 为空 → false`() {
        assertFalse(UpdateChecker.isNewVersion("", "1.0.1"))
    }

    @Test
    fun `两者皆空白 → false`() {
        assertFalse(UpdateChecker.isNewVersion("   ", "   "))
    }

    @Test
    fun `预发布后缀 1_0_0-beta vs 1_0_0 → false（后缀忽略后相等）`() {
        assertFalse(UpdateChecker.isNewVersion("1.0.0-beta", "1.0.0"))
    }

    @Test
    fun `1_0 与 1_0_0 等价 → false`() {
        assertFalse(UpdateChecker.isNewVersion("1.0", "1.0.0"))
    }

    @Test
    fun `compareVersion_1_0_0_小于_1_0_1`() {
        assertTrue(UpdateChecker.compareVersion("1.0.0", "1.0.1") < 0)
    }

    @Test
    fun `compareVersion_2_0_大于_1_9_9`() {
        assertTrue(UpdateChecker.compareVersion("2.0", "1.9.9") > 0)
    }
}
