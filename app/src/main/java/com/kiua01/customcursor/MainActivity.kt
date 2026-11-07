package com.kiua01.customcursor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var scaleValue: TextView
    private lateinit var sizeValue: TextView
    private lateinit var speedValue: TextView
    private lateinit var opacityValue: TextView
    private lateinit var smoothValue: TextView
    private lateinit var preview: CursorView
    private lateinit var howTo: TextView

    private val pickPng = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val dest = File(filesDir, "custom_cursor.png")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            CursorPrefs.setPngPath(this, dest.absolutePath)
            preview.loadFromPrefs()
            notifyRunning()
            Toast.makeText(this, "Cursor PNG applied", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load PNG: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        scaleValue = findViewById(R.id.scaleValue)
        sizeValue = findViewById(R.id.sizeValue)
        speedValue = findViewById(R.id.speedValue)
        opacityValue = findViewById(R.id.opacityValue)
        smoothValue = findViewById(R.id.smoothValue)
        preview = findViewById(R.id.previewCursor)
        howTo = findViewById(R.id.howToText)
        preview.loadFromPrefs()
        preview.setNormalizedPosition(0.5f, 0.5f)

        howTo.text = """
            How to use (make it MOVE):
            1) Tap “Enable Accessibility” → find “Custom Cursor” → turn ON
            2) Drag your finger on the screen → cursor follows (speed applies)
            3) Short tap → clicks under the cursor
            4) Physical mouse (Android 13+): cursor follows the mouse
            
            Overlay-only mode (no Accessibility): drag on the full-screen layer to move.
        """.trimIndent()

        setupSeek(R.id.seekScale, (CursorPrefs.scale(this) * 100).toInt().coerceIn(25, 400)) { progress ->
            val v = progress / 100f
            CursorPrefs.setScale(this, v)
            scaleValue.text = "Scale: %.2f".format(v)
            preview.loadFromPrefs()
            notifyRunning()
        }
        scaleValue.text = "Scale: %.2f".format(CursorPrefs.scale(this))

        setupSeek(R.id.seekSize, CursorPrefs.sizePx(this).coerceIn(16, 256)) { progress ->
            CursorPrefs.setSizePx(this, progress)
            sizeValue.text = "Size: ${progress}px"
            preview.loadFromPrefs()
            notifyRunning()
        }
        sizeValue.text = "Size: ${CursorPrefs.sizePx(this)}px"

        setupSeek(R.id.seekSpeed, (CursorPrefs.speed(this) * 100).toInt().coerceIn(10, 500)) { progress ->
            val v = progress / 100f
            CursorPrefs.setSpeed(this, v)
            speedValue.text = "Speed: %.2f".format(v)
            notifyRunning()
        }
        speedValue.text = "Speed: %.2f".format(CursorPrefs.speed(this))

        setupSeek(R.id.seekOpacity, (CursorPrefs.opacity(this) * 100).toInt().coerceIn(10, 100)) { progress ->
            val v = progress / 100f
            CursorPrefs.setOpacity(this, v)
            opacityValue.text = "Opacity: %.0f%%".format(v * 100)
            preview.loadFromPrefs()
            notifyRunning()
        }
        opacityValue.text = "Opacity: %.0f%%".format(CursorPrefs.opacity(this) * 100)

        setupSeek(R.id.seekSmooth, (CursorPrefs.smoothing(this) * 100).toInt().coerceIn(0, 95)) { progress ->
            val v = progress / 100f
            CursorPrefs.setSmoothing(this, v)
            smoothValue.text = "Smoothing: %.0f%%".format(v * 100)
            notifyRunning()
        }
        smoothValue.text = "Smoothing: %.0f%%".format(CursorPrefs.smoothing(this) * 100)

        findViewById<Button>(R.id.btnPickPng).setOnClickListener { pickPng.launch("image/*") }
        findViewById<Button>(R.id.btnResetPng).setOnClickListener {
            CursorPrefs.setPngPath(this, null)
            preview.loadFromPrefs()
            notifyRunning()
            Toast.makeText(this, "Default cursor restored", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Find “Custom Cursor” and enable it", Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startCursor() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopCursor() }

        // Preview area: finger moves cursor in the box
        findViewById<View>(R.id.previewArea).setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val x = event.x / v.width * preview.width
                    val y = event.y / v.height * preview.height
                    preview.setPixelPosition(x, y)
                    true
                }
                else -> false
            }
        }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        preview.loadFromPrefs()
        notifyRunning()
    }

    private fun setupSeek(id: Int, initial: Int, onChange: (Int) -> Unit) {
        val seek = findViewById<SeekBar>(id)
        seek.progress = initial
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun startCursor() {
        // Prefer accessibility (full movement + click)
        if (!CursorAccessibilityService.isEnabled(this)) {
            Toast.makeText(
                this,
                "Enable Accessibility for Custom Cursor (best). Opening settings…",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else {
            CursorAccessibilityService.instance?.reloadPrefs()
            Toast.makeText(this, "Accessibility cursor already active", Toast.LENGTH_SHORT).show()
        }

        // Also start overlay fallback if permission granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } else if (!CursorAccessibilityService.isEnabled(this)) {
            CursorPrefs.setEnabled(this, true)
            val intent = Intent(this, CursorOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            Toast.makeText(this, "Overlay cursor started — drag to move", Toast.LENGTH_LONG).show()
        }
        refreshStatus()
    }

    private fun stopCursor() {
        CursorPrefs.setEnabled(this, false)
        stopService(Intent(this, CursorOverlayService::class.java))
        Toast.makeText(
            this,
            "Overlay stopped. Turn off Accessibility in system settings to fully stop.",
            Toast.LENGTH_LONG
        ).show()
        refreshStatus()
    }

    private fun notifyRunning() {
        CursorAccessibilityService.instance?.reloadPrefs()
        if (CursorPrefs.isEnabled(this)) {
            startService(Intent(this, CursorOverlayService::class.java))
        }
    }

    private fun refreshStatus() {
        val overlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        val a11y = CursorAccessibilityService.isEnabled(this)
        statusText.text = buildString {
            append(if (a11y) "● Accessibility ON (cursor moves)" else "○ Accessibility OFF")
            append("  ·  Overlay: ")
            append(if (overlay) "OK" else "NEED")
            append("  ·  PNG: ")
            append(if (CursorPrefs.pngPath(this@MainActivity) != null) "custom" else "default")
        }
    }
}
