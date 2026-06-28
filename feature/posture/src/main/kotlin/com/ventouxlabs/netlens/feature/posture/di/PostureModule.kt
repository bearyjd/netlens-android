package com.ventouxlabs.netlens.feature.posture.di

import com.ventouxlabs.netlens.feature.posture.engine.EncryptionTypeProvider
import com.ventouxlabs.netlens.feature.posture.engine.WifiSecurityProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class PostureModule {

    @Binds
    abstract fun bindEncryptionTypeProvider(impl: WifiSecurityProvider): EncryptionTypeProvider
}
