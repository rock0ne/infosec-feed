package uk.cybertecpro.infosecfeed

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var repo: FeedRepository
    private lateinit var adapter: FeedAdapter
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var status: TextView
    private lateinit var empty: View
    private lateinit var chips: LinearLayout
    private lateinit var alerts: TextView

    /** null == "All". Filtering is client-side over the cached list. */
    private var activeCategory: String? = null
    private var allItems: List<FeedItem> = emptyList()

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enableAlerts()
        } else {
            Toast.makeText(
                this,
                "Notification permission was not granted. Tap Alerts again to retry.",
                Toast.LENGTH_LONG,
            ).show()
            updateAlertControl()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = FeedRepository(this)
        adapter = FeedAdapter(emptyList())
        SecurityAlertManager.initialize(this)

        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        findViewById<TextView>(R.id.add_widget).setOnClickListener { requestPinWidget() }
        alerts = findViewById(R.id.alerts)
        alerts.setOnClickListener { toggleAlerts() }
        updateAlertControl()

        chips = findViewById(R.id.chips)
        buildChips()

        status = findViewById(R.id.status)
        empty = findViewById(R.id.empty)
        swipe = findViewById(R.id.swipe)
        swipe.setOnRefreshListener { load(force = true) }

        val cached = repo.cached()
        if (cached.isNotEmpty()) {
            allItems = cached
            applyFilter()
            showStatus(visibleCount(), repo.lastUpdated())
        }
        load(force = cached.isEmpty())
    }

    override fun onDestroy() {
        adapter.close()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::alerts.isInitialized) updateAlertControl()
    }

    private fun load(force: Boolean) {
        if (!force) return
        swipe.isRefreshing = true
        lifecycleScope.launch {
            val items = repo.refresh()
            swipe.isRefreshing = false
            if (items.isNotEmpty()) {
                allItems = items
                applyFilter()
                showStatus(visibleCount(), System.currentTimeMillis())
                FeedWidgetProvider.refreshAll(this@MainActivity)
                SecurityAlertManager.onFeedUpdated(this@MainActivity, items)
            } else if (adapter.itemCount == 0) {
                status.text = "No items. Check connectivity and pull to retry."
                empty.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Asks the launcher to pin the widget. This is the supported route — the
     * launcher shows its own confirmation, so no drag simulation is needed.
     */
    private fun requestPinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            Toast.makeText(this, "This launcher does not support pinning widgets", Toast.LENGTH_LONG).show()
            return
        }
        val provider = ComponentName(this, FeedWidgetProvider::class.java)
        manager.requestPinAppWidget(provider, null, null)
    }

    private fun toggleAlerts() {
        if (SecurityAlertManager.isEnabled(this)) {
            if (!SecurityAlertManager.canPost(this)) {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                )
                return
            }
            SecurityAlertManager.disable(this)
            Toast.makeText(this, R.string.alerts_disabled_message, Toast.LENGTH_SHORT).show()
            updateAlertControl()
            return
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableAlerts()
        }
    }

    private fun enableAlerts() {
        SecurityAlertManager.enable(this, allItems.ifEmpty { repo.cached() })
        Toast.makeText(this, R.string.alerts_enabled_message, Toast.LENGTH_SHORT).show()
        updateAlertControl()
    }

    private fun updateAlertControl() {
        alerts.setText(
            when {
                SecurityAlertManager.isEnabled(this) && !SecurityAlertManager.canPost(this) ->
                    R.string.alerts_blocked
                SecurityAlertManager.isEnabled(this) -> R.string.alerts_on
                else -> R.string.alerts_off
            },
        )
    }

    private fun buildChips() {
        val labels = listOf<String?>(null) + Categories.ORDER
        labels.forEach { category ->
            val chip = TextView(this).apply {
                text = category ?: "All"
                textSize = 13f
                setTextColor(resources.getColorStateList(R.color.chip_text, theme))
                setBackgroundResource(R.drawable.chip_bg)
                gravity = Gravity.CENTER
                setPadding(36, 16, 36, 16)
                isSelected = category == activeCategory
                setOnClickListener {
                    activeCategory = category
                    for (i in 0 until chips.childCount) {
                        chips.getChildAt(i).isSelected = (i == labels.indexOf(category))
                    }
                    applyFilter()
                    showStatus(visibleCount(), repo.lastUpdated())
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 16 }
            chips.addView(chip, lp)
        }
    }

    private fun visible(): List<FeedItem> =
        activeCategory?.let { c -> allItems.filter { it.category == c } } ?: allItems

    private fun visibleCount() = visible().size

    private fun applyFilter() {
        adapter.submit(visible())
    }

    private fun showStatus(count: Int, updated: Long) {
        empty.visibility = View.GONE
        val scope = activeCategory ?: "all sources"
        status.text = "$count items  ·  $scope  ·  updated ${Format.age(updated)}"
    }
}
