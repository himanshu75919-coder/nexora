package com.nexora.app.di

import com.nexora.app.BuildConfig
import com.nexora.app.data.api.NexoraApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "NEXORA-Android/1.0")
                .build()
            chain.proceed(req)
        }
        .build()

    @Provides @Singleton
    fun api(client: OkHttpClient): NexoraApi = Retrofit.Builder()
        .baseUrl(BuildConfig.CONFIG_BASE_URL.let { if (!it.endsWith("/")) "$it/" else it })
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NexoraApi::class.java)
}
