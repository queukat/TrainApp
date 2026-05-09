// PreviewTrainViewModel.kt
package com.queukat.train.ui

import android.app.Application
import com.queukat.train.data.repository.FakeTrainRepository

/**
 * ё VM,    "fake" ,
 *   -    .
 */
class PreviewTrainViewModel(
    application: Application,
) : TrainViewModel(application, FakeTrainRepository(application)) {
    init {
        // :
        setFromStation("Bar")
        setToStation("Podgorica")
        setSelectedDate("2025-12-31")

        //  ,  -
        // _routes.value = ...
    }
}
