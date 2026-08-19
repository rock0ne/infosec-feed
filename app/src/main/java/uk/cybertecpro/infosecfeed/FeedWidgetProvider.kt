package uk.cybertecpro.infosecfeed

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.Executor

class FeedWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
        // updatePeriodMillis cannot go below 30 minutes, so pull fresh data here too.
        enqueueRefresh(context)
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_feed)

        val serviceIntent = Intent(context, FeedWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_list, serviceIntent)
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)

        // Tapping a row opens its URL; the template is filled per item by the factory.
        views.setPendingIntentTemplate(
            R.id.widget_list,
            PendingIntent.getActivity(
                context, 0,
                // Must be explicit: SDK 34+ rejects FLAG_MUTABLE on an implicit Intent.
                Intent(context, OpenLinkActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        )

        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            PendingIntent.getBroadcast(
                context, 1,
                Intent(context, FeedRefreshReceiver::class.java)
                    .setAction(FeedRefreshReceiver.ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        views.setOnClickPendingIntent(
            R.id.widget_title,
            PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        manager.updateAppWidget(widgetId, views)
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
    }

    companion object {
        private const val UNIQUE_REFRESH = "infosec-feed-refresh"
        private const val MIGRATION_PREFS = "work_migrations"
        private const val LEGACY_QUEUE_CLEARED = "legacy_queue_cleared_v1"
        private val DIRECT_EXECUTOR = Executor { command -> command.run() }

        @Volatile
        private var legacyCleanupInFlight = false

        private fun enqueueRefresh(context: Context) {
            val appContext = context.applicationContext
            val workManager = WorkManager.getInstance(appContext)
            val preferences = appContext.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)

            if (preferences.getBoolean(LEGACY_QUEUE_CLEARED, false)) {
                enqueueUniqueRefresh(workManager)
                return
            }

            synchronized(this) {
                if (preferences.getBoolean(LEGACY_QUEUE_CLEARED, false)) {
                    enqueueUniqueRefresh(workManager)
                    return
                }
                if (legacyCleanupInFlight) return
                legacyCleanupInFlight = true
            }

            // v1.0 created unnamed workers in a feedback loop. Those jobs survive
            // an app update, so clear the app's legacy WorkManager queue exactly
            // once before the first v1.1 unique refresh is allowed to run.
            val cleanup = workManager.cancelAllWork()
            cleanup.result.addListener({
                val succeeded = runCatching { cleanup.result.get() }.isSuccess
                synchronized(this) { legacyCleanupInFlight = false }
                if (succeeded) {
                    preferences.edit().putBoolean(LEGACY_QUEUE_CLEARED, true).apply()
                    enqueueUniqueRefresh(workManager)
                    Log.i("InfoSecFeed", "legacy WorkManager queue cleared")
                } else {
                    Log.e("InfoSecFeed", "legacy WorkManager queue cleanup failed")
                }
            }, DIRECT_EXECUTOR)
        }

        private fun enqueueUniqueRefresh(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                UNIQUE_REFRESH,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<FeedRefreshWorker>().build(),
            )
        }

        /** Handles the private widget refresh action without exposing a network trigger. */
        fun requestRefresh(context: Context) {
            enqueueRefresh(context)
            refreshAll(context)
        }

        /** Redraw every placed widget. Safe to call from anywhere. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, FeedWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            // The collection factory reloads the cache on this notification.
            // Do not rebroadcast APPWIDGET_UPDATE here: onUpdate enqueues the
            // worker, whose completion calls refreshAll, creating a fetch loop.
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }
    }
}
