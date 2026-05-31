package com.task.platform.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore 管理类 - 存储Token、用户配置等
 * 
 * 使用方式：
 *   storage.getToken()  // 获取Token
 *   storage.saveToken(token)  // 保存Token
 *   storage.clearAll()  // 清除所有数据（退出登录）
 */
@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "task_platform_prefs")
        
        // Token 相关
        val KEY_TOKEN = stringPreferencesKey("token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val KEY_USER_INFO = stringPreferencesKey("user_info")
        
        // 用户配置
        val KEY_AUTO_MODE = stringPreferencesKey("auto_mode")  // 自动化模式：manual/semi/auto
        val KEY_FIRST_LAUNCH = stringPreferencesKey("first_launch")

        // 记住密码
        val KEY_SAVED_PHONE = stringPreferencesKey("saved_phone")
        val KEY_SAVED_PASSWORD = stringPreferencesKey("saved_password")
    }

    /** 保存 Token */
    suspend fun saveToken(token: String, refreshToken: String = "") {
        context.dataStore.edit { preferences ->
            preferences[KEY_TOKEN] = token
            if (refreshToken.isNotEmpty()) {
                preferences[KEY_REFRESH_TOKEN] = refreshToken
            }
        }
    }

    /** 获取 Token */
    fun getToken(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_TOKEN]
        }
    }

    /** 同步获取 Token（用于启动时初始化 ApiClient，仅在协程中使用） */
    suspend fun getTokenSync(): String? {
        return getToken().first()
    }

    /** 获取 Refresh Token */
    fun getRefreshToken(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_REFRESH_TOKEN]
        }
    }

    /** 保存用户信息（JSON字符串） */
    suspend fun saveUserInfo(userInfoJson: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_INFO] = userInfoJson
        }
    }

    /** 获取用户信息 */
    fun getUserInfo(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_USER_INFO]
        }
    }

    /** 设置自动化模式 */
    suspend fun setAutoMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_MODE] = mode
        }
    }

    /** 获取自动化模式 */
    fun getAutoMode(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_AUTO_MODE] ?: "manual"  // 默认手动模式
        }
    }

    /** 设置是否首次启动 */
    suspend fun setFirstLaunch(isFirst: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FIRST_LAUNCH] = isFirst.toString()
        }
    }

    /** 检查是否首次启动 */
    fun isFirstLaunch(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_FIRST_LAUNCH] != "false"
        }
    }

    /** 清除所有数据（退出登录时调用，但保留记住的账号密码） */
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            val savedPhone = preferences[KEY_SAVED_PHONE]
            val savedPassword = preferences[KEY_SAVED_PASSWORD]
            preferences.clear()
            // 恢复记住的账号密码
            if (savedPhone != null) preferences[KEY_SAVED_PHONE] = savedPhone
            if (savedPassword != null) preferences[KEY_SAVED_PASSWORD] = savedPassword
        }
    }

    /** 保存登录账号和密码（密码登录成功后调用） */
    suspend fun saveLoginCredentials(phone: String, password: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SAVED_PHONE] = phone
            preferences[KEY_SAVED_PASSWORD] = password
        }
    }

    /** 获取保存的手机号 */
    fun getSavedPhone(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_SAVED_PHONE]
        }
    }

    /** 获取保存的密码 */
    fun getSavedPassword(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_SAVED_PASSWORD]
        }
    }
}
