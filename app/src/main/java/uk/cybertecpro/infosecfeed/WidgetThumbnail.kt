package uk.cybertecpro.infosecfeed

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/** Produces a small rounded center-crop bitmap that stays within RemoteViews IPC limits. */
object WidgetThumbnail {
    private const val WIDTH = 160
    private const val HEIGHT = 112
    private const val RADIUS = 18f

    fun from(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = maxOf(WIDTH.toFloat() / source.width, HEIGHT.toFloat() / source.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                (WIDTH - source.width * scale) / 2f,
                (HEIGHT - source.height * scale) / 2f,
            )
        }
        shader.setLocalMatrix(matrix)
        Canvas(output).drawRoundRect(
            RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()),
            RADIUS,
            RADIUS,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader },
        )
        return output
    }
}
