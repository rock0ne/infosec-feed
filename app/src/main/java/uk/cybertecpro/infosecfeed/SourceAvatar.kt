package uk.cybertecpro.infosecfeed

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Deterministic circular avatar for a source, drawn locally.
 *
 * Real favicons would mean an extra request per publisher; the initial-on-colour
 * approach always renders, works offline, and keeps the card rhythm consistent.
 */
object SourceAvatar {

    private val palette = intArrayOf(
        0xFF4EA8FF.toInt(), 0xFF7C5CFF.toInt(), 0xFF00C2A8.toInt(),
        0xFFFF8A3D.toInt(), 0xFFFF5C7A.toInt(), 0xFF35C759.toInt(),
        0xFFFFC93D.toInt(), 0xFF00A3FF.toInt(), 0xFFB06CFF.toInt(),
    )

    private val cache = HashMap<String, Bitmap>()

    fun of(source: String, sizePx: Int): Bitmap = cache.getOrPut("$source:$sizePx") {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val colour = palette[(source.hashCode() and 0x7FFFFFFF) % palette.size]

        canvas.drawCircle(
            sizePx / 2f, sizePx / 2f, sizePx / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
        )

        val letter = source.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sizePx * 0.55f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val baseline = sizePx / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(letter, sizePx / 2f, baseline, text)
        bmp
    }
}
