package com.ventouxlabs.netlens.feature.ipinfo.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import com.ventouxlabs.netlens.feature.ipinfo.data.IpInfoRepository
import com.ventouxlabs.netlens.feature.ipinfo.data.IpInfoRepositoryImpl
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IpInfoHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class IpInfoBindsModule {

    @Binds
    @Singleton
    abstract fun bindIpInfoRepository(
        impl: IpInfoRepositoryImpl,
    ): IpInfoRepository
}

@Module
@InstallIn(SingletonComponent::class)
object IpInfoProvidesModule {

    private const val TIMEOUT_MS = 10_000L

    @Provides
    @Singleton
    @IpInfoHttpClient
    fun provideHttpClient(): HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }
        install(HttpTimeout) {
            connectTimeoutMillis = TIMEOUT_MS
            requestTimeoutMillis = TIMEOUT_MS
        }
    }
}
