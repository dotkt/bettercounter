package org.kde.bettercounter

import android.app.Application
import androidx.lifecycle.ViewModelProvider

class BetterApplication : Application() {

    lateinit var viewModel: ViewModel

    override fun onCreate() {
        super.onCreate()
        viewModel = ViewModel(this)
    }

}