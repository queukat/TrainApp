// PreviewTrainViewModel.kt
package com.queukat.train.ui

import android.app.Application
import com.queukat.train.data.repository.FakeTrainRepository

/**
 * ё VM,    "fake" ,
 *   -    .
 */
class PreviewTrainViewModel(application: Application) :
    TrainViewModel(application, FakeTrainRepository(application)) {

    init {
        // :   
        _fromStation.value = "Bar"
        _toStation.value = "Podgorica"
        _selectedDate.value = "2025-12-31"

        //  ,  -  
        // _routes.value = ... 
    }
}
