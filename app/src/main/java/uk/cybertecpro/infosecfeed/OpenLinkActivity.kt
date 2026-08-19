package uk.cybertecpro.infosecfeed

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Trampoline for widget row taps.
 *
 * Android 34+ forbids a mutable PendingIntent built on an implicit Intent, and a
 * RemoteViews click template must be mutable so each row can fill in its own URL.
 * The template therefore points here — an explicit component — and this activity
 * performs the implicit ACTION_VIEW itself.
 */
class OpenLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent?.data
        if (target != null) {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, target)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        finish()
    }
}
