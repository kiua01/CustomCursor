package com.kiua01.customcursor

import android.accessibilityservice.AccessibilityService
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * Optional system-wide pointer tracking via accessibility.
 * When enabled, feeds absolute pointer positions into the overlay service.
 *
 * Note: Full relative mouse injection into other apps still requires the host app
 * (e.g. MojoLauncher) to read our position or use in-app CursorView.
 */
class CursorAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Mostly used as a gate so the user can enable the service.
        // Pointer events are better handled in-app; this keeps the service alive
        // and can reload prefs when windows change.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CursorOverlayService.instance?.let {
                // refresh bitmap / scale when switching apps
            }
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Service connected — overlay may already be running
    }

    companion object {
        fun isEnabled(ctx: android.content.Context): Boolean {
            val am = ctx.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabled = am.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            )
            return enabled.any { it.resolveInfo.serviceInfo.packageName == ctx.packageName }
        }
    }
}
