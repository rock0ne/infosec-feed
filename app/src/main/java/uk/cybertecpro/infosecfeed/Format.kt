package uk.cybertecpro.infosecfeed

import java.util.concurrent.TimeUnit

object Format {
    fun age(epochMillis: Long): String {
        if (epochMillis <= 0L) return "undated"
        val delta = System.currentTimeMillis() - epochMillis
        if (delta < 0) return "just now"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
        val hours = TimeUnit.MILLISECONDS.toHours(delta)
        val days = TimeUnit.MILLISECONDS.toDays(delta)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 30 -> "${days}d ago"
            else -> "${days / 30}mo ago"
        }
    }
}
