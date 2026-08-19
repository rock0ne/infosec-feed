package uk.cybertecpro.infosecfeed

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FeedAdapter(private var items: List<FeedItem>) :
    RecyclerView.Adapter<FeedAdapter.Holder>(), AutoCloseable {

    private val imageScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val hero: RoundedImageView = v.findViewById(R.id.hero_image)
        val badge: TextView = v.findViewById(R.id.badge)
        val avatar: ImageView = v.findViewById(R.id.source_avatar)
        val source: TextView = v.findViewById(R.id.source)
        val age: TextView = v.findViewById(R.id.age)
        val title: TextView = v.findViewById(R.id.title)
        val summary: TextView = v.findViewById(R.id.summary)
        var imageJob: Job? = null
    }

    fun submit(newItems: List<FeedItem>) {
        val previousItems = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previousItems.size
            override fun getNewListSize() = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previousItems[oldItemPosition].id == newItems[newItemPosition].id

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previousItems[oldItemPosition] == newItems[newItemPosition]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_feed, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.summary.text = item.summary
        holder.source.text = item.source
        holder.age.text = holder.itemView.context.getString(
            R.string.source_age,
            Format.age(item.published)
        )
        val avatarSize = (20 * holder.itemView.resources.displayMetrics.density).toInt()
        holder.avatar.setImageBitmap(SourceAvatar.of(item.source, avatarSize))

        val sev = item.severity
        if (sev == null) {
            holder.badge.visibility = View.GONE
        } else {
            holder.badge.visibility = View.VISIBLE
            holder.badge.text = sev
            holder.badge.setBackgroundResource(
                when (sev) {
                    "EXPLOITED" -> R.drawable.badge_exploited
                    "CRITICAL" -> R.drawable.badge_critical
                    else -> R.drawable.badge_high
                }
            )
        }

        holder.itemView.setOnClickListener {
            if (item.url.isNotBlank()) {
                runCatching {
                    it.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                }
            }
        }

        bindImage(holder, item)
    }

    override fun onViewRecycled(holder: Holder) {
        holder.imageJob?.cancel()
        holder.imageJob = null
        holder.hero.tag = null
        holder.hero.setImageDrawable(null)
        super.onViewRecycled(holder)
    }

    private fun bindImage(holder: Holder, item: FeedItem) {
        holder.imageJob?.cancel()
        holder.hero.setImageDrawable(null)
        val imageUrl = item.imageUrl
        holder.hero.tag = imageUrl
        if (imageUrl == null) {
            holder.hero.visibility = View.GONE
            holder.summary.visibility = if (item.summary.isBlank()) View.GONE else View.VISIBLE
            return
        }

        holder.hero.visibility = View.VISIBLE
        holder.summary.visibility = View.GONE
        val cached = ImageLoader.cached(imageUrl)
        if (cached != null) {
            holder.hero.setImageBitmap(cached)
            return
        }

        holder.imageJob = imageScope.launch {
            val bitmap = ImageLoader.load(holder.itemView.context.cacheDir, imageUrl)
            if (holder.hero.tag != imageUrl) return@launch
            if (bitmap == null) {
                holder.hero.visibility = View.GONE
                holder.summary.visibility = if (item.summary.isBlank()) View.GONE else View.VISIBLE
            } else {
                holder.hero.setImageBitmap(bitmap)
                holder.hero.visibility = View.VISIBLE
            }
        }
    }

    override fun close() {
        imageScope.cancel()
    }
}
