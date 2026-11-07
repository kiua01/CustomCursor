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
    private lateinit var modeValue: TextView
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
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
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
        modeValue = findViewById(R.id.modeValue)
        preview = findViewById(R.id.previewCursor)
        howTo = findViewById(R.id.howToText)
        preview.loadFromPrefs()
        preview.setNormalizedPosition(0.5f, 0.5f)

        // Force Mojo default once if user still on old trackpad default storage
        if (!CursorPrefs.prefs(this).contains(CursorPrefs.KEY_MODE)) {
            CursorPrefs.setMode(this, CursorPrefs.MODE_MOUSE)
        }

        howTo.text = """
            Mojo-style (no trackpad needed):
            1) Enable Accessibility → Custom Cursor
            2) Plug mouse / use Bluetooth mouse
            3) Move mouse → custom cursor follows
            4) Left click → clicks in apps under the cursor
            Screen stays free (cursor layer does not block touch).
            
            Android 13+ required for best mouse tracking.
            Optional: Finger mode if you have no mouse.
        """.trimIndent()

        setupSeek(R.id.seekScale, (CursorPrefs.scale(this) * 100).toInt().coerceIn(25, 400)) { p ->
            CursorPrefs.setScale(this, p / 100f)
            scaleValue.text = "Scale: %.2f".format(p / 100f)
            preview.loadFromPrefs(); notifyRunning()
        }
        scaleValue.text = "Scale: %.2f".format(CursorPrefs.scale(this))

        setupSeek(R.id.seekSize, CursorPrefs.sizePx(this).coerceIn(16, 256)) { p ->
            CursorPrefs.setSizePx(this, p)
            sizeValue.text = "Size: ${p}px"
            preview.loadFromPrefs(); notifyRunning()
        }
        sizeValue.text = "Size: ${CursorPrefs.sizePx(this)}px"

        setupSeek(R.id.seekSpeed, (CursorPrefs.speed(this) * 100).toInt().coerceIn(10, 500)) { p ->
            CursorPrefs.setSpeed(this, p / 100f)
            speedValue.text = "Speed: %.2f".format(p / 100f)
            notifyRunning()
        }
        speedValue.text = "Speed: %.2f".format(CursorPrefs.speed(this))

        setupSeek(R.id.seekOpacity, (CursorPrefs.opacity(this) * 100).toInt().coerceIn(10, 100)) { p ->
            CursorPrefs.setOpacity(this, p / 100f)
            opacityValue.text = "Opacity: %.0f%%".format(p.toFloat())
            preview.loadFromPrefs(); notifyRunning()
        }
        opacityValue.text = "Opacity: %.0f%%".format(CursorPrefs.opacity(this) * 100)

        setupSeek(R.id.seekSmooth, (CursorPrefs.smoothing(this) * 100).toInt().coerceIn(0, 95)) { p ->
            CursorPrefs.setSmoothing(this, p / 100f)
            smoothValue.text = "Smoothing: %.0f%%".format(p.toFloat())
            notifyRunning()
        }
        smoothValue.text = "Smoothing: %.0f%%".format(CursorPrefs.smoothing(this) * 100)

        refreshModeLabel()
        findViewById<Button>(R.id.btnModeTrackpad).setOnClickListener { setMode(CursorPrefs.MODE_MOUSE) }
        findViewById<Button>(R.id.btnModeFullscreen).setOnClickListener { setMode(CursorPrefs.MODE_FINGER) }
        findViewById<Button>(R.id.btnModeMouse).setOnClickListener { setMode(CursorPrefs.MODE_TRACKPAD) }

        // Fix button labels via text (layout still has old ids)
        findViewById<Button>(R.id.btnModeTrackpad).text = "Mouse (Mojo)"
        findViewById<Button>(R.id.btnModeFullscreen).text = "Finger"
        findViewById<Button>(R.id.btnModeMouse).text = "Trackpad"

        findViewById<Button>(R.id.btnPickPng).setOnClickListener { pickPng.launch("image/*") }
        findViewById<Button>(R.id.btnResetPng).setOnClickListener {
            CursorPrefs.setPngPath(this, null)
            preview.loadFromPrefs(); notifyRunning()
        }
        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else Toast.makeText(this, "Overlay OK", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startCursor() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopCursor() }

        findViewById<View>(R.id.previewArea).setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    preview.setPixelPosition(event.x / v.width * preview.width, event.y / v.height * preview.height)
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

    private fun setMode(mode: Int) {
        CursorPrefs.setMode(this, mode)
        refreshModeLabel()
        notifyRunning()
        Toast.makeText(this, modeName(mode), Toast.LENGTH_SHORT).show()
    }

    private fun modeName(mode: Int) = when (mode) {
        CursorPrefs.MODE_FINGER -> "Finger (absolute)"
        CursorPrefs.MODE_TRACKPAD -> "Trackpad"
        else -> "Mouse (Mojo style)"
    }

    private fun refreshModeLabel() {
        modeValue.text = "Mode: ${modeName(CursorPrefs.mode(this))}"
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
        // Always prefer Mojo mouse mode when starting
        if (!CursorPrefs.prefs(this).contains(CursorPrefs.KEY_MODE)) {
            CursorPrefs.setMode(this, CursorPrefs.MODE_MOUSE)
        }
        if (!CursorAccessibilityService.isEnabled(this)) {
            Toast.makeText(this, "Enable Accessibility → Custom Cursor", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else {
            CursorAccessibilityService.instance?.reloadPrefs()
            Toast.makeText(this, "Mojo-style cursor active — use your mouse", Toast.LENGTH_LONG).show()
        }
        refreshStatus()
    }

    private fun stopCursor() {
        CursorPrefs.setEnabled(this, false)
        stopService(Intent(this, CursorOverlayService::class.java))
        Toast.makeText(this, "Disable Accessibility to fully stop the cursor", Toast.LENGTH_LONG).show()
        refreshStatus()
    }

    private fun notifyRunning() {
        CursorAccessibilityService.instance?.reloadPrefs()
    }

    private fun refreshStatus() {
        val a11y = CursorAccessibilityService.isEnabled(this)
        statusText.text = buildString {
            append(if (a11y) "● Accessibility ON" else "○ Accessibility OFF")
            append("  ·  ${modeName(CursorPrefs.mode(this@MainActivity))}")
            append("  ·  PNG: ")
            append(if (CursorPrefs.pngPath(this@MainActivity) != null) "custom" else "default")
        }
    }
}
