package com.queukat.train.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private val _savedRoutes = mutableStateListOf<String>()
    val savedRoutes: List<String> get() = _savedRoutes

    init {
        loadSavedRoutes()
    }

    private fun loadSavedRoutes() {
        val routes = prefs.getStringSet("saved_routes", emptySet())?.toList() ?: emptyList()
        _savedRoutes.clear()
        _savedRoutes.addAll(routes)
    }

    fun saveRoute(from: String, to: String) {
        if (from.isNotBlank() && to.isNotBlank()) {
            val route = "$from - $to"
            val current = prefs.getStringSet("saved_routes", emptySet())?.toMutableSet() ?: mutableSetOf()
            current.add(route)
            prefs.edit().putStringSet("saved_routes", current).apply()
            loadSavedRoutes()
        }
    }

    fun setFromStation(value: String) { /*   */ }
    fun setToStation(value: String) { /*   */ }
}
