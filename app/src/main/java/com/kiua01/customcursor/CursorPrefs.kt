package com.kiua01.customcursor

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object CursorPrefs {
    private const val NAME = "custom_cursor_prefs"

    const val KEY_ENABLED = "enabled"
    const val KEY_SCALE = "scale"
    const val KEY_SIZE_PX = "size_px"
    const val KEY_SPEED = "speed"
    const val KEY_OPACITY = "opacity"
    const val KEY_HOTSPOT_X = "hotspot_x"
    const val KEY_HOTSPOT_Y = "hotspot_y"
    const val KEY_PNG_PATH = "png_path"
    const val KEY_SMOOTHING = "smoothing"
    /** 0 = trackpad (best), 1 = fullscreen drag, 2 = mouse-only */
    const val KEY_MODE = "control_mode"

    const val MODE_TRACKPAD = 0
    const val MODE_FULLSCREEN = 1
    const val MODE_MOUSE = 2

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_ENABLED, false)
    fun setEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit { putBoolean(KEY_ENABLED, v) }

    fun scale(ctx: Context) = prefs(ctx).getFloat(KEY_SCALE, 1.15f)
    fun setScale(ctx: Context, v: Float) = prefs(ctx).edit { putFloat(KEY_SCALE, v.coerceIn(0.25f, 4f)) }

    fun sizePx(ctx: Context) = prefs(ctx).getInt(KEY_SIZE_PX, 48)
    fun setSizePx(ctx: Context, v: Int) = prefs(ctx).edit { putInt(KEY_SIZE_PX, v.coerceIn(16, 256)) }

    fun speed(ctx: Context) = prefs(ctx).getFloat(KEY_SPEED, 1.25f)
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

    fun smoothing(ctx: Context) = prefs(ctx).getFloat(KEY_SMOOTHING, 0.25f)
    fun setSmoothing(ctx: Context, v: Float) = prefs(ctx).edit { putFloat(KEY_SMOOTHING, v.coerceIn(0f, 0.95f)) }

    fun mode(ctx: Context) = prefs(ctx).getInt(KEY_MODE, MODE_TRACKPAD)
    fun setMode(ctx: Context, mode: Int) = prefs(ctx).edit { putInt(KEY_MODE, mode.coerceIn(0, 2)) }
}
