package com.nexora.app.data.api

import retrofit2.http.GET
import retrofit2.http.Header

interface NexoraApi {
    @GET("api/version")
    suspend fun version(): Map<String, Any>

    @GET("api/config")
    suspend fun config(
        @Header("If-None-Match") etag: String? = null
    ): NexoraConfig
}
