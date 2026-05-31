package com.task.platform.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局消息提示工具
 * 
 * 使用方式：
 *   MessageUtil.show("操作成功")
 *   MessageUtil.show(R.string.success)
 */
@Singleton
class MessageUtil @Inject constructor(
    @ApplicationContext val context: Context
) {
    companion object {
        private var instance: MessageUtil? = null
        
        fun init(util: MessageUtil) {
            instance = util
        }
        
        fun show(message: String, duration: Int = Toast.LENGTH_SHORT) {
            instance?.showInternal(message, duration)
        }
        
        fun show(@StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT) {
            instance?.let {
                it.showInternal(it.context.getString(resId), duration)
            }
        }
    }
    
    private fun showInternal(message: String, duration: Int) {
        Toast.makeText(context, message, duration).show()
    }
    
    init {
        Companion.init(this)
    }
}
