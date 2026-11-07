package com.kiua01.customcursor

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * All mouse / cursor settings (Mojo-style).
 */
object CursorPrefs {
    private const val NAME = "custom_cursor_prefs"

    const val KEY_ENABLED = "enabled"
    const val KEY_SCALE = "scale"                 // visual scale 0.25..4.0
    const val KEY_SIZE_PX = "size_px"             // base size in px before scale
    const val KEY_SPEED = "speed"                 // movement multiplier 0.1..5.0
    const val KEY_OPACITY = "opacity"             // 0.1..1.0
    const val KEY_HOTSPOT_X = "hotspot_x"         // 0..1 relative
    const val KEY_HOTSPOT_Y = "hotspot_y"
    const val KEY_PNG_PATH = "png_path"           // absolute path to custom PNG
    const val KEY_HIDE_SYSTEM = "hide_system"     // request hide system pointer when possible
    const val KEY_SMOOTHING = "smoothing"         // 0..1 lerp factor

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_ENABLED, false)
    fun setEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit { putBoolean(KEY_ENABLED, v) }

    fun scale(ctx: Context) = prefs(ctx).getFloat(KEY_SCALE, 1.15f)
    fun setScale(ctx: Context, v: Float) = prefs(ctx).edit { putFloat(KEY_SCALE, v.coerceIn(0.25f, 4f)) }

    fun sizePx(ctx: Context) = prefs(ctx).getInt(KEY_SIZE_PX, 48)
    fun setSizePx(ctx: Context, v: Int) = prefs(ctx).edit { putInt(KEY_SIZE_PX, v.coerceIn(16, 256)) }

    fun speed(ctx: Context) = prefs(ctx).getFloat(KEY_SPEED, 1.0f)
    fun setSpeed(ctx: Context, v: Float) = prefs(ctx).edit { putFloat(KEY_SPEED, v.coerceIn(0.1f, 5f)) }

    fun opacity(ctx: Context) = prefs(ctx).getFloat(KEY_OPACITY, 1.0f)
    fun setOpacity(ctx: Context, v: Float) = prefs(ctx).edit { putFloat(KEY_OPACITY, v.coerceIn(0.1f, 1f)) }

    fun hotspotX(ctx: Context) = prefs(ctx).getFloat(KEY_HOTSPOT_X, 0.05f)
    fun hotspotY(ctx: Context) = prefs(ctx).getFloat(KEY_HOTSPOT_Y, 0.05f)
    fun setHotspot(ctx: Context, x: Float, y: Float) = prefs(ctx).edit {
        putFloat(KEY_HOTSPOT_X, x.coerceIn(0f, 1f))
        putFloat(KEY_HOTSPOT_Y, y.coerceIn(0f, 1f))
    }

    fun pngPath(ctx: Context): String? = prefs(ctx).getString(KEY_PNG_PATH, null)
    fun setPngPath(ctx: Context, path: String?) = prefs(ctx).edit { putString(KEY_PNG_PATH, path) }

    fun smoothing(ctx: Context) = prefs(ctx).getFloat(KEY_SMOOTHING, 0.35f)
    fun setSmoothing(ctx: Context, v: Float) = prefs(ctx).edit { putFloat(KEY_SMOOTHING, v.coerceIn(0f, 0.95f)) }
}
