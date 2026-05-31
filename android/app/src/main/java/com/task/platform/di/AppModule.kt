package com.task.platform.di

import com.task.platform.network.ApiClient
import com.task.platform.storage.DataStoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App模块依赖注入
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 提供 DataStoreManager
     */
    @Provides
    @Singleton
    fun provideDataStoreManager(
        @ApplicationContext context: android.content.Context
    ): DataStoreManager {
        return DataStoreManager(context)
    }

    /**
     * 提供网络管理器
     */
    @Provides
    @Singleton
    fun provideNetworkManager(
        @ApplicationContext context: android.content.Context
    ): com.task.platform.network.NetworkManager {
        return com.task.platform.network.NetworkManager(context)
    }

    /**
     * 提供消息工具类
     */
    @Provides
    @Singleton
    fun provideMessageUtil(
        @ApplicationContext context: android.content.Context
    ): com.task.platform.util.MessageUtil {
        return com.task.platform.util.MessageUtil(context)
    }
}
