package com.amazon.tv.leanbacklauncher.viewmodel

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amazon.tv.leanbacklauncher.util.TvSearchIconLoader
import com.amazon.tv.leanbacklauncher.util.TvSearchSuggestionsLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val _searchIcon = MutableStateFlow<Drawable?>(null)
    val searchIcon: StateFlow<Drawable?> = _searchIcon.asStateFlow()

    private val _suggestions = MutableStateFlow<Array<String>>(emptyArray())
    val suggestions: StateFlow<Array<String>> = _suggestions.asStateFlow()

    private val iconLoader = TvSearchIconLoader(application)
    private val suggestionsLoader = TvSearchSuggestionsLoader(application)

    init {
        loadSearchData()
    }

    private fun loadSearchData() {
        viewModelScope.launch {
            launch { loadIcon() }
            launch { loadSuggestions() }
        }
    }

    private suspend fun loadIcon() {
        withContext(Dispatchers.IO) {
            try {
                val icon = iconLoader.loadInBackground()
                _searchIcon.value = icon
            } catch (e: Exception) {
                _searchIcon.value = null
            }
        }
    }

    private suspend fun loadSuggestions() {
        withContext(Dispatchers.IO) {
            try {
                val result = suggestionsLoader.loadInBackground()
                _suggestions.value = result ?: emptyArray()
            } catch (e: Exception) {
                _suggestions.value = emptyArray()
            }
        }
    }

    fun refresh() {
        loadSearchData()
    }
}