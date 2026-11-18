package com.nexora.app.data.api

// Server-facing models (matches NEXORA API /api/config response)
data class NexoraConfig(
    val ok: Boolean = false,
    val config_version: Int = 0,
    val published_at: Long? = null,
    val data: ConfigData = ConfigData()
)

data class ConfigData(
    val cards: List<AppCard> = emptyList(),
    val categories: List<Category> = emptyList(),
    val announcements: List<Announcement> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

data class AppCard(
    val id: Int,
    val name: String,
    val description: String = "",
    val logo: String = "",
    val category: Int? = null,
    val featured: Boolean = false,
    val label: String = "",           // "", NEW, HOT, TRENDING, VIP
    val browser: String = "internal", // internal | external
    val servers: List<AppServer> = emptyList()
)

data class AppServer(
    val id: Int,
    val url: String,
    val priority: Int = 10,
    val is_default: Boolean = false,
    val enabled: Boolean = true,
    val maintenance: Boolean = false
)

data class Category(val id: Int, val name: String, val sort: Int = 0)
data class Announcement(val title: String, val body: String = "", val level: String = "info")
