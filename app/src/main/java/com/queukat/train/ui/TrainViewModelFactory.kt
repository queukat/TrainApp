package com.queukat.train.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.queukat.train.data.repository.TrainRepository

@Suppress("UNCHECKED_CAST")
class TrainViewModelFactory(
    private val application: Application,
    private val repo: TrainRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TrainViewModel(application, repo) as T
    }
}
