package com.task.platform.update

/**
 * 语义化版本比较工具
 *
 * 支持常见版本格式：
 *   - 标准三段式：1.0.0、1.2.3
 *   - 带前缀：v2.0、V1.0
 *   - 预发布后缀：1.0.0-beta、1.0.0+build.123（后缀中的非数字部分被忽略）
 *
 * 缺失的段按 0 处理（1.0 == 1.0.0）。
 * 任一参数为空或解析不出任何数字时，[isNewVersion] 返回 false（保守：不提示更新）。
 */
object UpdateChecker {

    /**
     * remote 是否比 current 新（即 remote > current）。
     */
    fun isNewVersion(remote: String, current: String): Boolean {
        if (remote.isBlank() || current.isBlank()) return false
        return compareVersion(remote, current) > 0
    }

    /**
     * 比较两个版本号。
     * @return > 0 表示 remote 更新；< 0 表示更旧；== 0 表示相同。
     */
    fun compareVersion(remote: String, current: String): Int {
        val r = parseVersion(remote)
        val c = parseVersion(current)
        val len = maxOf(r.size, c.size)
        for (i in 0 until len) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv.compareTo(cv)
        }
        return 0
    }

    /**
     * 解析版本字符串为整数段列表。
     * 以 . - + 分段，每段再按非数字字符切分，仅保留纯数字部分。
     */
    private fun parseVersion(v: String): List<Int> {
        return v.split('.', '-', '+')
            .asSequence()
            .flatMap { it.split(Regex("[^0-9]+")) }
            .mapNotNull { it.toIntOrNull() }
            .toList()
    }
}
