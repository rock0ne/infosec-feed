package uk.cybertecpro.infosecfeed

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Owns opt-in, notification-channel and background-alert behaviour. */
object SecurityAlertManager {
    const val CHANNEL_ID = "critical_security_alerts"
    private const val PREFS = "security_alerts"
    private const val ENABLED = "enabled"
    private const val SEEN_IDS = "seen_ids"
    private const val UNIQUE_PERIODIC_REFRESH = "infosec-alert-periodic-refresh"
    private const val CONFIRMATION_ID = 7100
    private const val ALERT_ID = 7101

    fun initialize(context: Context) {
        ensureChannel(context)
        if (isEnabled(context)) schedule(context)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alert_channel_description)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    fun canPost(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun enable(context: Context, currentItems: List<FeedItem>) {
        ensureChannel(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(SEEN_IDS, emptySet()).orEmpty().toMutableSet()
        existing += AlertPolicy.alertIds(currentItems)
        prefs.edit()
            .putBoolean(ENABLED, true)
            .putStringSet(SEEN_IDS, existing.takeLastBounded())
            .apply()
        schedule(context)
        postConfirmation(context)
    }

    fun disable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED, false)
            .apply()
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_REFRESH)
    }

    fun onFeedUpdated(context: Context, items: List<FeedItem>) {
        if (!isEnabled(context) || !canPost(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet(SEEN_IDS, emptySet()).orEmpty().toSet()
        val alerts = AlertPolicy.newAlerts(items, seen)
        if (alerts.isEmpty()) return
        val updatedSeen = (seen + AlertPolicy.alertIds(items)).toMutableSet().takeLastBounded()
        prefs.edit().putStringSet(SEEN_IDS, updatedSeen).apply()
        postAlerts(context, alerts)
    }

    private fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_REFRESH,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    @SuppressLint("MissingPermission")
    private fun postConfirmation(context: Context) {
        if (!canPost(context)) return
        val openApp = PendingIntent.getActivity(
            context,
            CONFIRMATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(context.getString(R.string.alert_confirmation_title))
            .setContentText(context.getString(R.string.alert_confirmation_body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(CONFIRMATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun postAlerts(context: Context, alerts: List<FeedItem>) {
        val first = alerts.first()
        val destination = first.url.ifBlank { "https://www.cisa.gov/known-exploited-vulnerabilities-catalog" }
        val openItem = PendingIntent.getActivity(
            context,
            ALERT_ID,
            Intent(context, OpenLinkActivity::class.java).setData(Uri.parse(destination)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(
                if (alerts.size == 1) first.title else "${alerts.size} critical security updates",
            )
        alerts.forEach { style.addLine("${it.severity} · ${it.title}") }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(
                if (alerts.size == 1) first.title else "${alerts.size} critical security updates",
            )
            .setContentText("${first.severity} · ${first.source}")
            .setStyle(style)
            .setContentIntent(openItem)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(ALERT_ID, notification)
    }

    private fun MutableSet<String>.takeLastBounded(): Set<String> =
        if (size <= 1_000) toSet() else toList().takeLast(1_000).toSet()
}
