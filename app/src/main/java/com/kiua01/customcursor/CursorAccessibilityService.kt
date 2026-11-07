package com.kiua01.customcursor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * System-wide virtual cursor.
 *
 * Layers:
 *  - cursorView: NOT_TOUCHABLE, only draws the pointer
 *  - trackpadView: small floating pad (drag = move, tap = click)
 *  - toggleBtn: pause / resume control
 *
 * Modes (CursorPrefs):
 *  TRACKPAD   — screen free, use pad (recommended)
 *  FULLSCREEN — drag anywhere
 *  MOUSE      — physical mouse only (Android 13+)
 */
class CursorAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var cursorView: CursorView? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var trackpadView: View? = null
    private var trackpadParams: WindowManager.LayoutParams? = null
    private var toggleView: View? = null
    private var toggleParams: WindowManager.LayoutParams? = null
    private var fullTouchView: View? = null
    private var fullTouchParams: WindowManager.LayoutParams? = null

    private var posX = 0f
    private var posY = 0f
    private var smoothX = 0f
    private var smoothY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchDownTime = 0L
    private var movedWhileDown = false
    private var controlsEnabled = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            info.setMotionEventSources(
                InputDevice.SOURCE_MOUSE or
                    InputDevice.SOURCE_MOUSE_RELATIVE or
                    InputDevice.SOURCE_TOUCHPAD
            )
        }
        serviceInfo = info

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        posX = dm.widthPixels / 2f
        posY = dm.heightPixels / 2f
        smoothX = posX
        smoothY = posY

        attachCursorLayer()
        attachToggleButton()
        rebuildControlLayer()

        Toast.makeText(this, "Custom Cursor ON — use the trackpad to move", Toast.LENGTH_LONG).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        removeAllLayers()
        instance = null
        super.onDestroy()
    }

    override fun onMotionEvent(event: MotionEvent) {
        val mouse = event.isFromSource(InputDevice.SOURCE_MOUSE) ||
            event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE) ||
            event.isFromSource(InputDevice.SOURCE_TOUCHPAD)
        if (mouse && controlsEnabled) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE ->
                    applyAbsolute(event.rawX, event.rawY)
                MotionEvent.ACTION_BUTTON_PRESS -> {
                    applyAbsolute(event.rawX, event.rawY)
                    injectClick(posX, posY)
                }
            }
        }
        super.onMotionEvent(event)
    }

    fun reloadPrefs() {
        cursorView?.loadFromPrefs()
        rebuildControlLayer()
    }

    private fun baseOverlayFlags(touchable: Boolean): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return f
    }

    private fun attachCursorLayer() {
        cursorParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            baseOverlayFlags(touchable = false),
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val view = CursorView(this).also { cursorView = it }
        view.loadFromPrefs()
        view.setPixelPosition(posX, posY)
        windowManager?.addView(view, cursorParams)
    }

    private fun attachToggleButton() {
        val size = dp(48)
        toggleParams = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            baseOverlayFlags(touchable = true),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(80)
        }

        val btn = TextView(this).apply {
            text = "▶"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            background = circleBg(0xCC007AFF.toInt())
            setOnClickListener {
                controlsEnabled = !controlsEnabled
                text = if (controlsEnabled) "▶" else "❚❚"
                background = circleBg(if (controlsEnabled) 0xCC007AFF.toInt() else 0xCCFF3B30.toInt())
                rebuildControlLayer()
                Toast.makeText(
                    this@CursorAccessibilityService,
                    if (controlsEnabled) "Controls ON" else "Controls paused — screen free",
                    Toast.LENGTH_SHORT
                ).show()
            }
            // drag toggle
            var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX; downY = e.rawY
                        startX = toggleParams?.x ?: 0
                        startY = toggleParams?.y ?: 0
                        false // allow click
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - downX).toInt()
                        val dy = (e.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 8) {
                            toggleParams?.let { lp ->
                                // TOP|END: x is offset from end
                                lp.x = (startX - dx).coerceAtLeast(0)
                                lp.y = (startY + dy).coerceAtLeast(0)
                                try { windowManager?.updateViewLayout(v, lp) } catch (_: Exception) {}
                            }
                            return@setOnTouchListener true
                        }
                        false
                    }
                    else -> false
                }
            }
        }
        toggleView = btn
        windowManager?.addView(btn, toggleParams)
    }

    private fun rebuildControlLayer() {
        removeControlLayers()
        if (!controlsEnabled) return

        when (CursorPrefs.mode(this)) {
            CursorPrefs.MODE_FULLSCREEN -> attachFullscreenTouch()
            CursorPrefs.MODE_MOUSE -> { /* cursor only + onMotionEvent */ }
            else -> attachTrackpad()
        }
    }

    private fun attachTrackpad() {
        val w = dp(180)
        val h = dp(120)
        trackpadParams = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            baseOverlayFlags(touchable = true),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(16)
            y = dp(48)
        }

        val pad = FrameLayout(this).apply {
            background = roundBg(0x99000000.toInt(), dp(16).toFloat())
            val label = TextView(this@CursorAccessibilityService).apply {
                text = "TRACKPAD\ndrag · tap click"
                setTextColor(0xCCFFFFFF.toInt())
                textSize = 11f
                gravity = android.view.Gravity.CENTER
            }
            addView(
                label,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            setOnTouchListener { _, e -> handleRelativeTouch(e) }
            // long-press corner: allow moving the pad by dragging outside? skip for simplicity
        }
        trackpadView = pad
        windowManager?.addView(pad, trackpadParams)
    }

    private fun attachFullscreenTouch() {
        fullTouchParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            baseOverlayFlags(touchable = true),
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val v = View(this).apply {
            setOnTouchListener { _, e -> handleRelativeTouch(e) }
        }
        fullTouchView = v
        windowManager?.addView(v, fullTouchParams)
    }

    private fun handleRelativeTouch(event: MotionEvent): Boolean {
        val speed = CursorPrefs.speed(this)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                touchDownTime = System.currentTimeMillis()
                movedWhileDown = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - lastTouchX) * speed
                val dy = (event.rawY - lastTouchY) * speed
                if (kotlin.math.abs(dx) > 0.5f || kotlin.math.abs(dy) > 0.5f) {
                    movedWhileDown = true
                    applyDelta(dx, dy)
                }
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dt = System.currentTimeMillis() - touchDownTime
                if (!movedWhileDown && dt < 280) injectClick(posX, posY)
                return true
            }
        }
        return false
    }

    fun applyDelta(dx: Float, dy: Float) {
        val dm = resources.displayMetrics
        posX = (posX + dx).coerceIn(0f, dm.widthPixels.toFloat())
        posY = (posY + dy).coerceIn(0f, dm.heightPixels.toFloat())
        val smooth = CursorPrefs.smoothing(this)
        smoothX += (posX - smoothX) * (1f - smooth)
        smoothY += (posY - smoothY) * (1f - smooth)
        cursorView?.setPixelPosition(smoothX, smoothY)
    }

    fun applyAbsolute(x: Float, y: Float) {
        posX = x
        posY = y
        val smooth = CursorPrefs.smoothing(this)
        if (smooth < 0.05f) {
            smoothX = x; smoothY = y
        } else {
            smoothX += (posX - smoothX) * (1f - smooth)
            smoothY += (posY - smoothY) * (1f - smooth)
        }
        cursorView?.setPixelPosition(smoothX, smoothY)
    }

    private fun injectClick(x: Float, y: Float) {
        // Ensure control layers don't eat the injected gesture
        setControlTouchable(false)
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = setControlTouchable(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = setControlTouchable(true)
        }, null)
    }

    private fun setControlTouchable(touchable: Boolean) {
        listOf(
            trackpadView to trackpadParams,
            fullTouchView to fullTouchParams
        ).forEach { (view, lp) ->
            if (view != null && lp != null) {
                lp.flags = baseOverlayFlags(touchable)
                try { windowManager?.updateViewLayout(view, lp) } catch (_: Exception) {}
            }
        }
    }

    private fun removeControlLayers() {
        listOf(trackpadView, fullTouchView).forEach { v ->
            try { v?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        }
        trackpadView = null
        fullTouchView = null
    }

    private fun removeAllLayers() {
        removeControlLayers()
        listOf(cursorView, toggleView).forEach { v ->
            try { v?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        }
        cursorView = null
        toggleView = null
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun circleBg(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun roundBg(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
    }

    companion object {
        @Volatile
        var instance: CursorAccessibilityService? = null

        fun isEnabled(ctx: android.content.Context): Boolean {
            val am = ctx.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
                as android.view.accessibility.AccessibilityManager
            val enabled = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            val pkg = ctx.packageName
            return enabled.any {
                it.resolveInfo?.serviceInfo?.packageName == pkg || it.id.contains(pkg)
            }
        }
    }
}
