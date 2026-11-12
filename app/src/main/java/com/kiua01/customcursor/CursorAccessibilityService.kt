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
 * Mojo-style cursor + trackpad + tiny Attack (X) button.
 * Trackpad position is configurable and draggable.
 * Attack button: hold = continuous left-click (attack/break/drag like Mojo).
 */
class CursorAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var cursorView: CursorView? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var controlView: View? = null
    private var controlParams: WindowManager.LayoutParams? = null
    private var attackView: View? = null
    private var attackParams: WindowManager.LayoutParams? = null
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
    private var lastMouseRawX = Float.NaN
    private var lastMouseRawY = Float.NaN
    private var hasLastMouse = false
    private var attacking = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val attackRunnable = object : Runnable {
        override fun run() {
            if (!attacking) return
            injectClick(posX, posY, durationMs = 40)
            mainHandler.postDelayed(this, 90)
        }
    }

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
        rebuildAttackButton()

        Toast.makeText(this, "Custom Cursor ON — trackpad + X attack ready", Toast.LENGTH_LONG).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        stopAttack()
        removeAllLayers()
        instance = null
        super.onDestroy()
    }

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

        val speed = CursorPrefs.speed(this)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> {
                lastMouseRawX = event.rawX
                lastMouseRawY = event.rawY
                hasLastMouse = true
            }
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_MOVE -> {
                var dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                var dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                if (dx == 0f && dy == 0f && hasLastMouse) {
                    dx = event.rawX - lastMouseRawX
                    dy = event.rawY - lastMouseRawY
                }
                lastMouseRawX = event.rawX
                lastMouseRawY = event.rawY
                hasLastMouse = true
                if (dx != 0f || dy != 0f) applyDelta(dx * speed, dy * speed)
            }
            MotionEvent.ACTION_HOVER_EXIT -> hasLastMouse = false
            MotionEvent.ACTION_BUTTON_PRESS -> {
                if (event.actionButton == MotionEvent.BUTTON_PRIMARY ||
                    event.buttonState and MotionEvent.BUTTON_PRIMARY != 0
                ) {
                    injectClick(posX, posY)
                }
            }
            MotionEvent.ACTION_DOWN -> {
                lastMouseRawX = event.rawX
                lastMouseRawY = event.rawY
                hasLastMouse = true
                if (event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) {
                    injectClick(posX, posY)
                }
            }
        }
        super.onMotionEvent(event)
    }

    fun reloadPrefs() {
        mainHandler.post {
            cursorView?.loadFromPrefs()
            rebuildControlLayer()
            rebuildAttackButton()
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
                if (!controlsEnabled) stopAttack()
                rebuildControlLayer()
                rebuildAttackButton()
                Toast.makeText(
                    this@CursorAccessibilityService,
                    if (controlsEnabled) "Controls ON" else "Controls paused",
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
            else -> { /* mouse only */ }
        }
    }

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
                        ) movedWhileDown = true
                        applyAbsolute(e.rawX, e.rawY)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        applyAbsolute(e.rawX, e.rawY)
                        injectClick(posX, posY)
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
        val padX = CursorPrefs.trackpadX(this)
        val padY = CursorPrefs.trackpadY(this)

        controlParams = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            overlayFlags(touchable = true),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = padX
            y = padY
        }

        var padMoving = false
        var padStartX = 0
        var padStartY = 0
        var downRawX = 0f
        var downRawY = 0f

        val pad = FrameLayout(this).apply {
            background = roundBg(0x99000000.toInt(), dp(16).toFloat())
            addView(
                TextView(this@CursorAccessibilityService).apply {
                    text = "TRACKPAD\ndrag · tap click\nhold edge to move pad"
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    gravity = android.view.Gravity.CENTER
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            setOnTouchListener { v, e ->
                val speed = CursorPrefs.speed(this@CursorAccessibilityService)
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = e.rawX
                        lastTouchY = e.rawY
                        downRawX = e.rawX
                        downRawY = e.rawY
                        touchDownTime = System.currentTimeMillis()
                        movedWhileDown = false
                        padMoving = false
                        padStartX = controlParams?.x ?: 0
                        padStartY = controlParams?.y ?: 0
                        // Long-press edge later; 2-finger or corner: if touch near top 24dp of pad → move mode after 400ms
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val totalDx = e.rawX - downRawX
                        val totalDy = e.rawY - downRawY
                        // Hold near top strip and drag → reposition trackpad
                        if (!padMoving && e.y < dp(28) &&
                            (kotlin.math.abs(totalDx) > 12f || kotlin.math.abs(totalDy) > 12f)
                        ) {
                            padMoving = true
                        }
                        if (padMoving) {
                            // BOTTOM|END: increasing x moves left from end; increasing y moves up from bottom
                            val nx = (padStartX - totalDx.toInt()).coerceAtLeast(0)
                            val ny = (padStartY - totalDy.toInt()).coerceAtLeast(0)
                            controlParams?.let { lp ->
                                lp.x = nx
                                lp.y = ny
                                try {
                                    windowManager?.updateViewLayout(v, lp)
                                } catch (_: Exception) {
                                }
                            }
                            return@setOnTouchListener true
                        }

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
                        if (padMoving) {
                            controlParams?.let {
                                CursorPrefs.setTrackpadPos(this@CursorAccessibilityService, it.x, it.y)
                                Toast.makeText(
                                    this@CursorAccessibilityService,
                                    "Trackpad position saved",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else if (!movedWhileDown && System.currentTimeMillis() - touchDownTime < 280) {
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

    /** Tiny X button — hold for continuous attack / dig / drag (Mojo left-hold). */
    private fun rebuildAttackButton() {
        removeAttackButton()
        if (!controlsEnabled || !CursorPrefs.attackEnabled(this)) return

        val size = dp(36)
        attackParams = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            overlayFlags(touchable = true),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = CursorPrefs.attackX(this@CursorAccessibilityService)
            y = CursorPrefs.attackY(this@CursorAccessibilityService)
        }

        var movingBtn = false
        var startX = 0
        var startY = 0
        var downRawX = 0f
        var downRawY = 0f

        val btn = TextView(this).apply {
            text = "X"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            background = circleBg(0xCCFF3B30.toInt())
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = e.rawX
                        downRawY = e.rawY
                        startX = attackParams?.x ?: 0
                        startY = attackParams?.y ?: 0
                        movingBtn = false
                        // start attack after short delay unless dragged
                        mainHandler.postDelayed({
                            if (!movingBtn && e.actionMasked != MotionEvent.ACTION_UP) {
                                startAttack()
                                background = circleBg(0xEEFF9500.toInt())
                            }
                        }, 80)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val tdx = e.rawX - downRawX
                        val tdy = e.rawY - downRawY
                        if (kotlin.math.abs(tdx) > 14f || kotlin.math.abs(tdy) > 14f) {
                            if (attacking) stopAttack()
                            movingBtn = true
                            attackParams?.let { lp ->
                                lp.x = (startX - tdx.toInt()).coerceAtLeast(0)
                                lp.y = (startY - tdy.toInt()).coerceAtLeast(0)
                                try {
                                    windowManager?.updateViewLayout(v, lp)
                                } catch (_: Exception) {
                                }
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (movingBtn) {
                            attackParams?.let {
                                CursorPrefs.setAttackPos(this@CursorAccessibilityService, it.x, it.y)
                                Toast.makeText(
                                    this@CursorAccessibilityService,
                                    "X button position saved",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            // short tap = one click; hold already attacking until up
                            if (!attacking) injectClick(posX, posY)
                            stopAttack()
                        }
                        background = circleBg(0xCCFF3B30.toInt())
                        true
                    }
                    else -> false
                }
            }
        }
        attackView = btn
        try {
            windowManager?.addView(btn, attackParams)
        } catch (_: Exception) {
        }
    }

    private fun startAttack() {
        if (attacking) return
        attacking = true
        mainHandler.post(attackRunnable)
    }

    private fun stopAttack() {
        attacking = false
        mainHandler.removeCallbacks(attackRunnable)
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

    private fun injectClick(x: Float, y: Float, durationMs: Long = 50) {
        setControlTouchable(false)
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                mainHandler.postDelayed({ setControlTouchable(true) }, 20)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                mainHandler.post { setControlTouchable(true) }
            }
        }, null)
    }

    private fun setControlTouchable(touchable: Boolean) {
        listOf(
            controlView to controlParams,
            attackView to attackParams
        ).forEach { (view, lp) ->
            if (view != null && lp != null) {
                lp.flags = overlayFlags(touchable)
                try {
                    windowManager?.updateViewLayout(view, lp)
                } catch (_: Exception) {
                }
            }
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

    private fun removeAttackButton() {
        try {
            attackView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        attackView = null
        attackParams = null
    }

    private fun removeAllLayers() {
        removeControlLayer()
        removeAttackButton()
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
