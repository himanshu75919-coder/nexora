package com.nexora.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.app.data.api.AppCard
import com.nexora.app.data.api.ConfigData
import com.nexora.app.data.local.FavoritesStore
import com.nexora.app.data.repo.ConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUi(
    val loading: Boolean = true,
    val version: Int = 0,
    val data: ConfigData = ConfigData(),
    val query: String = "",
    val favorites: Set<String> = emptySet(),
    val recents: Set<String> = emptySet(),
    val offline: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: ConfigRepository,
    private val store: FavoritesStore
) : ViewModel() {

    private val _ui = MutableStateFlow(DashboardUi())
    val ui: StateFlow<DashboardUi> = _ui

    init {
        refresh()
        viewModelScope.launch {
            store.favorites.collect { _ui.value = _ui.value.copy(favorites = it) }
        }
        viewModelScope.launch {
            store.recents.collect { _ui.value = _ui.value.copy(recents = it) }
        }
    }

    fun refresh() = viewModelScope.launch {
        _ui.value = _ui.value.copy(loading = true)
        val (v, d) = repo.load()
        _ui.value = _ui.value.copy(loading = false, version = v, data = d, offline = v == 0)
    }

    fun search(q: String) { _ui.value = _ui.value.copy(query = q) }

    fun cards(): List<AppCard> {
        val s = _ui.value
        val base = s.data.cards
        val filtered = if (s.query.isBlank()) base
        else base.filter { it.name.contains(s.query, true) || it.description.contains(s.query, true) }
        return filtered.sortedWith(compareByDescending<AppCard> { it.name in s.recents } .thenBy { it.name })
    }

    fun featured(): List<AppCard> = _ui.value.data.cards.filter { it.featured }

    fun byLabel(label: String): List<AppCard> = _ui.value.data.cards.filter { it.label == label }

    fun recents(): List<AppCard> =
        _ui.value.data.cards.filter { it.name in _ui.value.recents }

    fun favorites(): List<AppCard> =
        _ui.value.data.cards.filter { it.name in _ui.value.favorites }

    fun toggleFav(card: AppCard) = viewModelScope.launch { store.toggleFavorite(card.id) }

    fun markRecent(card: AppCard) = viewModelScope.launch { store.markRecent(card.id) }
}
