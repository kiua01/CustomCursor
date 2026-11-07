package com.kiua01.customcursor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

/**
 * System overlay that draws the virtual cursor and tracks pointer / relative motion.
 * Requires SYSTEM_ALERT_WINDOW permission.
 */
class CursorOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var cursorView: CursorView? = null
    private var params: WindowManager.LayoutParams? = null

    private var posX = 0f
    private var posY = 0f
    private var smoothX = 0f
    private var smoothY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startAsForeground()
        attachOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        cursorView?.loadFromPrefs()
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        removeOverlay()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val channelId = "cursor_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.cursor_default)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(1, notif)
    }

    private fun attachOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val view = CursorView(this).also { cursorView = it }
        view.loadFromPrefs()

        // Seed center
        val dm = resources.displayMetrics
        posX = dm.widthPixels / 2f
        posY = dm.heightPixels / 2f
        smoothX = posX
        smoothY = posY
        view.setPixelPosition(posX, posY)

        windowManager?.addView(view, params)

        // Pointer capture is limited without accessibility; we also expose update API.
        // Relative motion can be fed via broadcast from accessibility service.
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            view.setPixelPosition(smoothX, smoothY)
        }
    }

    private fun removeOverlay() {
        cursorView?.let { windowManager?.removeView(it) }
        cursorView = null
    }

    fun applyDelta(dx: Float, dy: Float) {
        val speed = CursorPrefs.speed(this)
        posX = (posX + dx * speed).coerceIn(0f, resources.displayMetrics.widthPixels.toFloat())
        posY = (posY + dy * speed).coerceIn(0f, resources.displayMetrics.heightPixels.toFloat())
        val smooth = CursorPrefs.smoothing(this)
        smoothX = smoothX + (posX - smoothX) * (1f - smooth)
        smoothY = smoothY + (posY - smoothY) * (1f - smooth)
        cursorView?.setPixelPosition(smoothX, smoothY)
    }

    fun applyAbsolute(x: Float, y: Float) {
        posX = x
        posY = y
        smoothX = x
        smoothY = y
        cursorView?.setPixelPosition(x, y)
    }

    companion object {
        var instance: CursorOverlayService? = null
            private set
    }



}
