package com.kiua01.customcursor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Mojo-style system cursor:
 * - Default MODE_MOUSE: screen is free, physical mouse moves the drawn cursor, clicks inject taps
 * - MODE_FINGER: finger position = cursor (no trackpad widget)
 * - MODE_TRACKPAD: optional floating pad
 *
 * Cursor layer is always FLAG_NOT_TOUCHABLE so it never blocks the UI under it.
 */
class CursorAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var cursorView: CursorView? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var controlView: View? = null
    private var controlParams: WindowManager.LayoutParams? = null
    private var toggleView: View? = null
    private var toggleParams: WindowManager.LayoutParams? = null

    private var posX = 0f
    private var posY = 0f
    private var smoothX = 0f
    private var smoothY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchDownTime = 0L
    private var movedWhileDown = false
    private var controlsEnabled = true
    private var mouseButtonDown = false

    private val mainHandler = Handler(Looper.getMainLooper())

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
            // Critical for Mojo-like mouse tracking system-wide
            info.setMotionEventSources(
                InputDevice.SOURCE_MOUSE or
                    InputDevice.SOURCE_MOUSE_RELATIVE or
                    InputDevice.SOURCE_TOUCHPAD or
                    InputDevice.SOURCE_STYLUS
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

        Toast.makeText(
            this,
            "Custom Cursor ON — move your mouse (Mojo style). Screen is free.",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        removeAllLayers()
        instance = null
        super.onDestroy()
    }

    /**
     * Mojo-like path: every mouse move updates the virtual cursor.
     * Button press → inject click at cursor (games/apps under it receive the tap).
     */
    override fun onMotionEvent(event: MotionEvent) {
        if (!controlsEnabled) {
            super.onMotionEvent(event)
            return
        }

        val isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE) ||
            event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE) ||
            event.isFromSource(InputDevice.SOURCE_TOUCHPAD) ||
            event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE

        if (!isMouse) {
            super.onMotionEvent(event)
            return
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_HOVER_EXIT,
            MotionEvent.ACTION_MOVE -> {
                if (event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)) {
                    // Relative mouse: use axis values as deltas when available
                    val dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                    val dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                    if (dx != 0f || dy != 0f) {
                        val speed = CursorPrefs.speed(this)
                        applyDelta(dx * speed, dy * speed)
                    } else {
                        applyAbsolute(event.rawX, event.rawY)
                    }
                } else {
                    applyAbsolute(event.rawX, event.rawY)
                }
            }
            MotionEvent.ACTION_BUTTON_PRESS -> {
                applyAbsolute(event.rawX, event.rawY)
                mouseButtonDown = true
                // Primary button → left click
                if (event.actionButton == MotionEvent.BUTTON_PRIMARY ||
                    event.buttonState and MotionEvent.BUTTON_PRIMARY != 0
                ) {
                    injectClick(posX, posY)
                }
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                mouseButtonDown = false
            }
            MotionEvent.ACTION_DOWN -> {
                // Some devices send DOWN instead of BUTTON_PRESS for mouse
                applyAbsolute(event.rawX, event.rawY)
                if (event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) {
                    injectClick(posX, posY)
                }
            }
            MotionEvent.ACTION_UP -> {
                mouseButtonDown = false
            }
        }
        super.onMotionEvent(event)
    }

    fun reloadPrefs() {
        mainHandler.post {
            cursorView?.loadFromPrefs()
            rebuildControlLayer()
        }
    }

    private fun overlayFlags(touchable: Boolean): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return f
    }

    private fun attachCursorLayer() {
        // Never touchable — pure draw layer, like Mojo GLFWCursorView
        cursorParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            overlayFlags(touchable = false),
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val view = CursorView(this).also { cursorView = it }
        view.loadFromPrefs()
        view.setPixelPosition(posX, posY)
        try {
            windowManager?.addView(view, cursorParams)
        } catch (e: Exception) {
            Toast.makeText(this, "Cursor layer failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun attachToggleButton() {
        val size = dp(44)
        toggleParams = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            overlayFlags(touchable = true),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(72)
        }

        val btn = TextView(this).apply {
            text = "🖱️"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            background = circleBg(0xCC007AFF.toInt())
            setOnClickListener {
                controlsEnabled = !controlsEnabled
                background = circleBg(if (controlsEnabled) 0xCC007AFF.toInt() else 0xCCFF3B30.toInt())
                rebuildControlLayer()
                Toast.makeText(
                    this@CursorAccessibilityService,
                    if (controlsEnabled) "Cursor tracking ON" else "Cursor tracking paused",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        toggleView = btn
        try {
            windowManager?.addView(btn, toggleParams)
        } catch (_: Exception) {
        }
    }

    private fun rebuildControlLayer() {
        removeControlLayer()
        if (!controlsEnabled) return

        when (CursorPrefs.mode(this)) {
            CursorPrefs.MODE_FINGER -> attachFingerLayer()
            CursorPrefs.MODE_TRACKPAD -> attachTrackpadLayer()
            else -> {
                // MODE_MOUSE: no control layer — screen fully free, mouse via onMotionEvent
            }
        }
    }

    /** Finger = absolute pointer (no trackpad box). */
    private fun attachFingerLayer() {
        controlParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            overlayFlags(touchable = true),
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val v = View(this).apply {
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownTime = System.currentTimeMillis()
                        movedWhileDown = false
                        lastTouchX = e.rawX
                        lastTouchY = e.rawY
                        applyAbsolute(e.rawX, e.rawY)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(e.rawX - lastTouchX) > 4f ||
                            kotlin.math.abs(e.rawY - lastTouchY) > 4f
                        ) {
                            movedWhileDown = true
                        }
                        applyAbsolute(e.rawX, e.rawY)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        applyAbsolute(e.rawX, e.rawY)
                        val dt = System.currentTimeMillis() - touchDownTime
                        // Tap or release → click (Mojo-like click at pointer)
                        if (!movedWhileDown || dt < 200) {
                            injectClick(posX, posY)
                        } else {
                            // Drag end still clicks once (optional desktop feel)
                            injectClick(posX, posY)
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        controlView = v
        try {
            windowManager?.addView(v, controlParams)
        } catch (_: Exception) {
        }
    }

    private fun attachTrackpadLayer() {
        val w = dp(180)
        val h = dp(120)
        controlParams = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            overlayFlags(touchable = true),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(16)
            y = dp(48)
        }

        val pad = FrameLayout(this).apply {
            background = roundBg(0x99000000.toInt(), dp(16).toFloat())
            addView(
                TextView(this@CursorAccessibilityService).apply {
                    text = "TRACKPAD"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    gravity = android.view.Gravity.CENTER
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            setOnTouchListener { _, e ->
                val speed = CursorPrefs.speed(this@CursorAccessibilityService)
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = e.rawX
                        lastTouchY = e.rawY
                        touchDownTime = System.currentTimeMillis()
                        movedWhileDown = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - lastTouchX) * speed
                        val dy = (e.rawY - lastTouchY) * speed
                        if (kotlin.math.abs(dx) > 0.5f || kotlin.math.abs(dy) > 0.5f) {
                            movedWhileDown = true
                            applyDelta(dx, dy)
                        }
                        lastTouchX = e.rawX
                        lastTouchY = e.rawY
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!movedWhileDown && System.currentTimeMillis() - touchDownTime < 280) {
                            injectClick(posX, posY)
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        controlView = pad
        try {
            windowManager?.addView(pad, controlParams)
        } catch (_: Exception) {
        }
    }

    fun applyDelta(dx: Float, dy: Float) {
        val dm = resources.displayMetrics
        posX = (posX + dx).coerceIn(0f, dm.widthPixels.toFloat())
        posY = (posY + dy).coerceIn(0f, dm.heightPixels.toFloat())
        smoothToCursor()
    }

    fun applyAbsolute(x: Float, y: Float) {
        val dm = resources.displayMetrics
        posX = x.coerceIn(0f, dm.widthPixels.toFloat())
        posY = y.coerceIn(0f, dm.heightPixels.toFloat())
        smoothToCursor()
    }

    private fun smoothToCursor() {
        val smooth = CursorPrefs.smoothing(this)
        if (smooth < 0.05f) {
            smoothX = posX
            smoothY = posY
        } else {
            smoothX += (posX - smoothX) * (1f - smooth)
            smoothY += (posY - smoothY) * (1f - smooth)
        }
        cursorView?.setPixelPosition(smoothX, smoothY)
    }

    private fun injectClick(x: Float, y: Float) {
        // Control layer off briefly so gesture hits the app under us
        setControlTouchable(false)
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                mainHandler.postDelayed({ setControlTouchable(true) }, 30)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                mainHandler.post { setControlTouchable(true) }
            }
        }, null)
    }

    private fun setControlTouchable(touchable: Boolean) {
        val view = controlView ?: return
        val lp = controlParams ?: return
        lp.flags = overlayFlags(touchable)
        try {
            windowManager?.updateViewLayout(view, lp)
        } catch (_: Exception) {
        }
    }

    private fun removeControlLayer() {
        try {
            controlView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        controlView = null
        controlParams = null
    }

    private fun removeAllLayers() {
        removeControlLayer()
        listOf(cursorView, toggleView).forEach { v ->
            try {
                v?.let { windowManager?.removeView(it) }
            } catch (_: Exception) {
            }
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
