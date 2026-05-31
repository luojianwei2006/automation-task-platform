package com.task.platform.di

import com.task.platform.network.ApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideApiClient(): ApiClient {
        // ApiClient 是 Kotlin object（单例），直接返回单例实例
        return ApiClient
    }
}
