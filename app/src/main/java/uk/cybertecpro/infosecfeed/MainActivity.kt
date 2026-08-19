package uk.cybertecpro.infosecfeed

import android.Manifest
import android.app.AlertDialog
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
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
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
    private lateinit var empty: TextView
    private lateinit var chips: LinearLayout
    private lateinit var alerts: TextView

    /** null == "All". Filtering is client-side over the cached list. */
    private var activeCategory: String? = null
    private var searchQuery: String = ""
    private var allItems: List<FeedItem> = emptyList()
    private var pendingAlertMode: AlertMode = AlertMode.KEV_ONLY

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enableAlerts(pendingAlertMode)
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
        alerts.setOnClickListener { showAlertOptions() }
        updateAlertControl()

        chips = findViewById(R.id.chips)
        buildChips()

        status = findViewById(R.id.status)
        empty = findViewById(R.id.empty)
        findViewById<EditText>(R.id.search).doAfterTextChanged {
            searchQuery = it?.toString().orEmpty().trim()
            applyFilter()
            showStatus(visibleCount(), repo.lastUpdated())
        }
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

    private fun showAlertOptions() {
        if (SecurityAlertManager.isEnabled(this) && !SecurityAlertManager.canPost(this)) {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
            return
        }
        val modes = arrayOf(AlertMode.OFF, AlertMode.KEV_ONLY, AlertMode.KEV_AND_CRITICAL)
        val labels = arrayOf(
            getString(R.string.alert_option_off),
            getString(R.string.alert_option_kev),
            getString(R.string.alert_option_critical),
        )
        val selected = modes.indexOf(SecurityAlertManager.mode(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.alert_options_title)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                dialog.dismiss()
                configureAlerts(modes[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun configureAlerts(mode: AlertMode) {
        if (mode == AlertMode.OFF) {
            SecurityAlertManager.disable(this)
            Toast.makeText(this, R.string.alerts_disabled_message, Toast.LENGTH_SHORT).show()
            updateAlertControl()
            return
        }
        pendingAlertMode = mode
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableAlerts(mode)
        }
    }

    private fun enableAlerts(mode: AlertMode) {
        SecurityAlertManager.enable(this, allItems.ifEmpty { repo.cached() }, mode)
        Toast.makeText(this, R.string.alerts_enabled_message, Toast.LENGTH_SHORT).show()
        updateAlertControl()
    }

    private fun updateAlertControl() {
        alerts.setText(
            when {
                SecurityAlertManager.isEnabled(this) && !SecurityAlertManager.canPost(this) ->
                    R.string.alerts_blocked
                SecurityAlertManager.mode(this) == AlertMode.KEV_ONLY -> R.string.alerts_kev
                SecurityAlertManager.mode(this) == AlertMode.KEV_AND_CRITICAL ->
                    R.string.alerts_all_critical
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

    private fun visible(): List<FeedItem> {
        return FeedFilter.apply(allItems, activeCategory, searchQuery)
    }

    private fun visibleCount() = visible().size

    private fun applyFilter() {
        val visible = visible()
        adapter.submit(visible)
        if (::empty.isInitialized) {
            empty.visibility = if (allItems.isNotEmpty() && visible.isEmpty()) View.VISIBLE else View.GONE
            if (visible.isEmpty() && allItems.isNotEmpty()) {
                empty.setText(R.string.no_search_results)
            }
        }
    }

    private fun showStatus(count: Int, updated: Long) {
        empty.visibility = View.GONE
        val scope = activeCategory ?: "all sources"
        val label = if (searchQuery.isBlank()) "$count items" else "$count matches"
        status.text = "$label  ·  $scope  ·  updated ${Format.age(updated)}"
    }
}
