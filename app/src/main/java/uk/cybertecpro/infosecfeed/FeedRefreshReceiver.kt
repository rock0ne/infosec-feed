package uk.cybertecpro.infosecfeed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives only the app-owned PendingIntent behind the widget Refresh button. */
class FeedRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) FeedWidgetProvider.requestRefresh(context)
    }

    companion object {
        const val ACTION_REFRESH = "uk.cybertecpro.infosecfeed.REFRESH"
    }
}
