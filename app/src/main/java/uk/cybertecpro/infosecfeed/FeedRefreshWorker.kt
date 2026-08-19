package uk.cybertecpro.infosecfeed

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Refreshes the cache off the main thread, then asks the widgets to redraw. */
class FeedRefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val items = FeedRepository(applicationContext).refresh()
        return if (items.isEmpty()) {
            Result.retry()
        } else {
            FeedWidgetProvider.refreshAll(applicationContext)
            Result.success()
        }
    }
}
