package com.kiua01.customcursor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * System-wide virtual cursor (Mojo-style).
 * - Draws CursorView on TYPE_ACCESSIBILITY_OVERLAY
 * - Follows physical mouse via onMotionEvent (API 33+)
 * - Touch-drag moves cursor; single-tap / mouse-click injects a tap gesture
 */
class CursorAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var cursorView: CursorView? = null
    private var params: WindowManager.LayoutParams? = null

    private var posX = 0f
    private var posY = 0f
    private var smoothX = 0f
    private var smoothY = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchDownTime = 0L
    private var movedWhileDown = false

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Receive mouse / touchpad motion for absolute cursor tracking
            info.setMotionEventSources(
                InputDevice.SOURCE_MOUSE or
                    InputDevice.SOURCE_MOUSE_RELATIVE or
                    InputDevice.SOURCE_TOUCHPAD
            )
        }
        serviceInfo = info

        attachCursorOverlay()
        Toast.makeText(this, "Custom Cursor ON — move mouse or drag finger", Toast.LENGTH_LONG).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // kept alive for service lifecycle; motion is handled in onMotionEvent / touch overlay
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        removeCursorOverlay()
        instance = null
        super.onDestroy()
    }

    /** API 33+: receive raw mouse / stylus motion before the system consumes it. */
    override fun onMotionEvent(event: MotionEvent) {
        val mouse = event.isFromSource(InputDevice.SOURCE_MOUSE) ||
            event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE) ||
            event.isFromSource(InputDevice.SOURCE_TOUCHPAD)
        if (mouse) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_MOVE,
                MotionEvent.ACTION_MOVE -> {
                    applyAbsolute(event.rawX, event.rawY)
                }
                MotionEvent.ACTION_BUTTON_PRESS,
                MotionEvent.ACTION_DOWN -> {
                    applyAbsolute(event.rawX, event.rawY)
                    if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS ||
                        event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
                    ) {
                        injectClick(posX, posY)
                    }
                }
            }
        }
        super.onMotionEvent(event)
    }

    private fun attachCursorOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        posX = dm.widthPixels / 2f
        posY = dm.heightPixels / 2f
        smoothX = posX
        smoothY = posY

        // Full-screen touch layer: receives touches, draws cursor, does not need SYSTEM_ALERT_WINDOW
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val view = CursorView(this).also { cursorView = it }
        view.loadFromPrefs()
        view.setPixelPosition(posX, posY)

        // Touch control: drag = move cursor, short tap = click at cursor
        view.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }

        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            Toast.makeText(this, "Overlay failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
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
                if (kotlin.math.abs(dx) > 1f || kotlin.math.abs(dy) > 1f) {
                    movedWhileDown = true
                    applyDelta(dx, dy)
                }
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dt = System.currentTimeMillis() - touchDownTime
                if (!movedWhileDown && dt < 250) {
                    injectClick(posX, posY)
                }
                return true
            }
        }
        return false
    }

    private fun removeCursorOverlay() {
        try {
            cursorView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        cursorView = null
    }

    fun reloadPrefs() {
        cursorView?.loadFromPrefs()
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
        val speed = CursorPrefs.speed(this)
        // When speed != 1, blend toward target for a "heavier" feel
        if (speed >= 0.99f && speed <= 1.01f) {
            posX = x
            posY = y
        } else {
            posX += (x - posX) * speed.coerceIn(0.1f, 1f)
            posY += (y - posY) * speed.coerceIn(0.1f, 1f)
        }
        val smooth = CursorPrefs.smoothing(this)
        smoothX += (posX - smoothX) * (1f - smooth)
        smoothY += (posY - smoothY) * (1f - smooth)
        cursorView?.setPixelPosition(smoothX, smoothY)
    }

    /** Inject a short tap so the cursor can click through into apps underneath. */
    private fun injectClick(x: Float, y: Float) {
        // Briefly hide touch interception by making overlay not touchable during gesture
        val view = cursorView ?: return
        val wm = windowManager ?: return
        val lp = params ?: return

        // Temporarily pass touches through while we inject
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            wm.updateViewLayout(view, lp)
        } catch (_: Exception) {
        }

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                restoreTouchable()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                restoreTouchable()
            }
        }, null)
    }

    private fun restoreTouchable() {
        val view = cursorView ?: return
        val wm = windowManager ?: return
        val lp = params ?: return
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        try {
            wm.updateViewLayout(view, lp)
        } catch (_: Exception) {
        }
    }

    companion object {
        @Volatile
        var instance: CursorAccessibilityService? = null

        fun isEnabled(ctx: android.content.Context): Boolean {
            val am = ctx.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
                as android.view.accessibility.AccessibilityManager
            val enabled = am.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            val pkg = ctx.packageName
            return enabled.any {
                it.resolveInfo?.serviceInfo?.packageName == pkg ||
                    it.id.contains(pkg)
            }
        }
    }
}
