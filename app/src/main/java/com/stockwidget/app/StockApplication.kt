package com.stockwidget.app

import android.app.Application
import com.stockwidget.app.work.RefreshWorker

class StockApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ensure periodic widget refresh is scheduled on app start.
        RefreshWorker.schedule(this)
    }
}
