package uk.cybertecpro.infosecfeed

import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class FeedWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        FeedRemoteViewsFactory(applicationContext)
}

private class FeedRemoteViewsFactory(
    private val context: android.content.Context,
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<FeedItem> = emptyList()

    override fun onCreate() { items = FeedRepository(context).cached() }

    /** Called on notifyAppWidgetViewDataChanged; runs off the main thread. */
    override fun onDataSetChanged() { items = FeedRepository(context).cached() }

    override fun onDestroy() { items = emptyList() }

    override fun getCount() = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_item)
        views.setTextViewText(R.id.widget_item_title, item.title)
        views.setTextViewText(R.id.widget_item_summary, item.summary)
        views.setViewVisibility(
            R.id.widget_item_summary,
            if (item.summary.isBlank()) View.GONE else View.VISIBLE,
        )
        views.setTextViewText(R.id.widget_item_source, item.source)
        views.setTextViewText(R.id.widget_item_age, "·  ${Format.age(item.published)}")
        val avatarSize = (18 * context.resources.displayMetrics.density).toInt()
        views.setImageViewBitmap(R.id.widget_source_avatar, SourceAvatar.of(item.source, avatarSize))

        val severity = item.severity
        if (severity == null) {
            views.setViewVisibility(R.id.widget_item_badge, View.GONE)
        } else {
            views.setTextViewText(R.id.widget_item_badge, severity)
            views.setInt(
                R.id.widget_item_badge,
                "setBackgroundResource",
                when (severity) {
                    "EXPLOITED" -> R.drawable.badge_exploited
                    "CRITICAL" -> R.drawable.badge_critical
                    else -> R.drawable.badge_high
                },
            )
            views.setViewVisibility(R.id.widget_item_badge, View.VISIBLE)
        }
        val thumbnail = item.imageUrl
            ?.let { ImageLoader.cachedOrDisk(context.cacheDir, it) }
            ?.let(WidgetThumbnail::from)
        if (thumbnail == null) {
            views.setViewVisibility(R.id.widget_item_image, View.GONE)
        } else {
            views.setImageViewBitmap(R.id.widget_item_image, thumbnail)
            views.setViewVisibility(R.id.widget_item_image, View.VISIBLE)
        }
        // Only the fill-in intent is set here; the template lives on the collection.
        views.setOnClickFillInIntent(
            R.id.widget_item_root,
            Intent().setData(android.net.Uri.parse(item.url.ifBlank { "https://nvd.nist.gov" }))
        )
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = items[position].id.hashCode().toLong()
    override fun hasStableIds() = true
}
