package com.nexora.app.data.repo

import android.content.Context
import com.nexora.app.data.api.ConfigData
import com.nexora.app.data.api.NexoraApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote config with LAST-KNOWN-GOOD protection:
 * - fresh fetch -> cache to private file -> serve
 * - network fail -> serve cached snapshot (app still opens, offline resilient)
 * - ETag used by server; on 304 we keep cache (no body transfer)
 */
@Singleton
class ConfigRepository @Inject constructor(
    private val api: NexoraApi,
    @ApplicationContext private val ctx: Context
) {
    private val cacheFile: File get() = File(ctx.filesDir, "nexora_config.json")
    private var etag: String? = null

    suspend fun load(): Pair<Int, ConfigData> = withContext(Dispatchers.IO) {
        try {
            val res: Response<com.nexora.app.data.api.NexoraConfig> = api.config(etag)
            if (res.code() == 304) {
                val cached = readCache()
                return@withContext cached
            }
            val body = res.body()
            if (body != null && body.ok) {
                etag = res.headers()["ETag"]
                cacheFile.writeText(com.google.gson.Gson().toJson(body))
                return@withContext body.config_version to body.data
            }
            readCache()
        } catch (t: Throwable) {
            readCache() // last-known-good
        }
    }

    private fun readCache(): Pair<Int, ConfigData> {
        val cached = runCatching {
            val cfg = com.google.gson.Gson().fromJson(cacheFile.readText(), com.nexora.app.data.api.NexoraConfig::class.java)
            cfg.config_version to cfg.data
        }.getOrNull()
        return cached ?: (0 to ConfigData()) // clean empty state, no fake data
    }
}
