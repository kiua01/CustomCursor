package com.kiua01.customcursor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Mojo-style cursor overlay view.
 * Draws a PNG (or default arrow) at (cursorX, cursorY) with scale / opacity / hotspot.
 */
class CursorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var cursorBitmap: Bitmap? = null
    private var defaultDrawable: Drawable? =
        ContextCompat.getDrawable(context, R.drawable.cursor_default)

    /** Normalized 0..1 position (screen fraction), same idea as GLFW cursorX/Y. */
    var normX = 0.5f
    var normY = 0.5f

    var mouseScale = 1.15f
    var baseSizePx = 48
    var hotspotX = 0.05f
    var hotspotY = 0.05f
    var cursorOpacity = 1f
    var visibleCursor = true

    fun loadFromPrefs() {
        mouseScale = CursorPrefs.scale(context)
        baseSizePx = CursorPrefs.sizePx(context)
        hotspotX = CursorPrefs.hotspotX(context)
        hotspotY = CursorPrefs.hotspotY(context)
        cursorOpacity = CursorPrefs.opacity(context)
        val path = CursorPrefs.pngPath(context)
        cursorBitmap = if (!path.isNullOrBlank() && File(path).isFile) {
            BitmapFactory.decodeFile(path)
        } else null
        invalidate()
    }

    fun setNormalizedPosition(x: Float, y: Float) {
        normX = x.coerceIn(0f, 1f)
        normY = y.coerceIn(0f, 1f)
        invalidate()
    }

    fun setPixelPosition(px: Float, py: Float) {
        if (width <= 0 || height <= 0) return
        normX = (px / width).coerceIn(0f, 1f)
        normY = (py / height).coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!visibleCursor) return
        paint.alpha = (cursorOpacity * 255).toInt().coerceIn(25, 255)

        val cx = normX * width
        val cy = normY * height
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(mouseScale, mouseScale)

        val bmp = cursorBitmap
        if (bmp != null && !bmp.isRecycled) {
            val target = baseSizePx.toFloat()
            val scale = target / maxOf(bmp.width, bmp.height).toFloat()
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val hx = hotspotX * dw
            val hy = hotspotY * dh
            canvas.drawBitmap(bmp, -hx, -hy, paint)
        } else {
            val d = defaultDrawable ?: return
            val s = baseSizePx
            val hx = (hotspotX * s).toInt()
            val hy = (hotspotY * s).toInt()
            d.setBounds(-hx, -hy, s - hx, s - hy)
            d.alpha = paint.alpha
            d.draw(canvas)
        }
        canvas.restore()
    }
}
