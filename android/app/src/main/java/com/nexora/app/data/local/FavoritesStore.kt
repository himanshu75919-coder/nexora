package com.nexora.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.store by preferencesDataStore("nexora_prefs")

/** Guest-mode favourites + recents (no account required by design). */
@Singleton
class FavoritesStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val ds = ctx.store
    private val favKey = stringSetPreferencesKey("favorites")
    private val recentKey = stringSetPreferencesKey("recents")

    val favorites: Flow<Set<String>> = ds.data.map { it[favKey] ?: emptySet() }
    val recents: Flow<Set<String>> = ds.data.map { it[recentKey] ?: emptySet() }

    suspend fun toggleFavorite(cardId: Int): Boolean {
        val id = cardId.toString()
        var added = false
        ds.edit { p ->
            val cur = p[favKey] ?: emptySet()
            added = id !in cur
            p[favKey] = if (added) cur + id else cur - id
        }
        return added
    }

    suspend fun markRecent(cardId: Int) = ds.edit { p ->
        val cur = p[recentKey] ?: emptySet()
        p[recentKey] = (setOf(cardId.toString()) + cur).take(12).toSet()
    }
}
