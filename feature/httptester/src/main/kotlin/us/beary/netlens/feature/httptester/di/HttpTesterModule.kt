package us.beary.netlens.feature.httptester.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import us.beary.netlens.feature.httptester.engine.HttpRequester
import us.beary.netlens.feature.httptester.engine.HttpRequesterImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HttpTesterModule {

    @Binds
    @Singleton
    abstract fun bindHttpRequester(impl: HttpRequesterImpl): HttpRequester
}
