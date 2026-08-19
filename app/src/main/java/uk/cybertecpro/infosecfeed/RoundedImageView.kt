package uk.cybertecpro.infosecfeed

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.appcompat.widget.AppCompatImageView

/** ImageView that clips to a rounded rectangle, for Discover-style cards. */
class RoundedImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppCompatImageView(context, attrs, defStyle) {

    private var radiusPx = 0f

    init {
        radiusPx = 18f * resources.displayMetrics.density
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        clipToOutline = true
    }
}
