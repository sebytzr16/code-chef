package com.stockwidget.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stockwidget.app.data.PreferencesStore
import com.stockwidget.app.data.StockRepository
import com.stockwidget.app.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/** Periodically refreshes quotes and redraws the widget. */
class RefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            StockRepository(applicationContext).refreshAll()
            WidgetUpdater.notifyDataChanged(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "stock_refresh"

        /** (Re)schedule periodic refresh using the user's configured interval. */
        fun schedule(context: Context) {
            val minutes = PreferencesStore(context).refreshMinutes
                .coerceAtLeast(15) // WorkManager minimum period.
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(
                minutes.toLong(), TimeUnit.MINUTES
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
