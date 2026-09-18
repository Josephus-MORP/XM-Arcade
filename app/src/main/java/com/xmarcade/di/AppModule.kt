package com.xmarcade.di

import com.xmarcade.data.blossom.BlossomManager
import com.xmarcade.data.concord.ConcordManager
import com.xmarcade.data.wallet.WalletManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun provideBlossom(okHttp: OkHttpClient): BlossomManager = BlossomManager(okHttp)

    @Provides @Singleton
    fun provideWallet(): WalletManager = WalletManager()

    @Provides @Singleton
    fun provideConcord(): ConcordManager = ConcordManager()
}
