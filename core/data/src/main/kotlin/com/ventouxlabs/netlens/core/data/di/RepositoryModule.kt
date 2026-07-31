package com.ventouxlabs.netlens.core.data.di

import com.ventouxlabs.netlens.core.data.repository.HistoryRepository
import com.ventouxlabs.netlens.core.data.repository.HistoryRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository interface bindings.
 *
 * Separate from [DataModule] because that is an `object` of `@Provides` functions and `@Binds`
 * requires an abstract declaration.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository
}
