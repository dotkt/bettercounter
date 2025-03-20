package org.kde.bettercounter

import android.app.Application

class BetterApplication : Application() {

    lateinit var viewModel: ViewModel

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        viewModel = ViewModel(this)
    }

    companion object {
        private lateinit var INSTANCE: BetterApplication
        
        fun getInstance(): BetterApplication {
            return INSTANCE
        }
    }
}
