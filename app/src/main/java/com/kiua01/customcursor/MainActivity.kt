package com.kiua01.customcursor

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
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
    private lateinit var trackpadPosValue: TextView
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
        trackpadPosValue = findViewById(R.id.trackpadPosValue)
        preview = findViewById(R.id.previewCursor)
        howTo = findViewById(R.id.howToText)
        preview.loadFromPrefs()
        preview.setNormalizedPosition(0.5f, 0.5f)

        howTo.text = """
            Trackpad tips:
            · Drag on pad = move cursor · Tap pad = click
            · Drag from the TOP EDGE of the pad = reposition trackpad (saved)
            
            X button (small red):
            · Hold X = attack / dig / continuous click (like Mojo left-hold)
            · Drag X = move the button (saved)
            · Pin controls = lock trackpad & X so they never move while using them
            
            Cursor color: pick a preset below (tints the default arrow).
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

        setupSeek(R.id.seekSpeed, (CursorPrefs.speed(this) * 100).toInt().coerceIn(10, 1200)) { p ->
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

        // Trackpad position from settings (offset from bottom-right)
        val maxOff = 600
        setupSeek(R.id.seekTrackpadX, CursorPrefs.trackpadX(this).coerceIn(0, maxOff)) { p ->
            CursorPrefs.setTrackpadPos(this, p, CursorPrefs.trackpadY(this))
            refreshTrackpadLabel()
            notifyRunning()
        }
        setupSeek(R.id.seekTrackpadY, CursorPrefs.trackpadY(this).coerceIn(0, maxOff)) { p ->
            CursorPrefs.setTrackpadPos(this, CursorPrefs.trackpadX(this), p)
            refreshTrackpadLabel()
            notifyRunning()
        }
        refreshTrackpadLabel()

        refreshModeLabel()
        findViewById<Button>(R.id.btnModeTrackpad).apply {
            text = "Trackpad"
            setOnClickListener { setMode(CursorPrefs.MODE_TRACKPAD) }
        }
        findViewById<Button>(R.id.btnModeFullscreen).apply {
            text = "Finger"
            setOnClickListener { setMode(CursorPrefs.MODE_FINGER) }
        }
        findViewById<Button>(R.id.btnModeMouse).apply {
            text = "Mouse"
            setOnClickListener { setMode(CursorPrefs.MODE_MOUSE) }
        }

        // Color presets
        val colors = listOf(
            CursorPrefs.COLOR_WHITE to "White",
            CursorPrefs.COLOR_RED to "Red",
            CursorPrefs.COLOR_GREEN to "Green",
            CursorPrefs.COLOR_BLUE to "Blue",
            CursorPrefs.COLOR_YELLOW to "Yellow",
            CursorPrefs.COLOR_CYAN to "Cyan",
            CursorPrefs.COLOR_MAGENTA to "Pink",
            CursorPrefs.COLOR_ORANGE to "Orange",
            CursorPrefs.COLOR_BLACK to "Black"
        )
        val row = findViewById<LinearLayout>(R.id.colorRow)
        row.removeAllViews()
        colors.forEach { (color, name) ->
            val b = Button(this).apply {
                text = name
                textSize = 10f
                setTextColor(0xFF000000.toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12f
                    setColor(color)
                }
                setOnClickListener {
                    CursorPrefs.setCursorColor(this@MainActivity, color)
                    preview.loadFromPrefs()
                    notifyRunning()
                    Toast.makeText(this@MainActivity, "Cursor: $name", Toast.LENGTH_SHORT).show()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 4
                }
            }
            row.addView(b)
        }

        findViewById<Button>(R.id.btnAttackToggle).setOnClickListener {
            val next = !CursorPrefs.attackEnabled(this)
            CursorPrefs.setAttackEnabled(this, next)
            findViewById<Button>(R.id.btnAttackToggle).text =
                if (next) "X attack button: ON" else "X attack button: OFF"
            notifyRunning()
        }
        findViewById<Button>(R.id.btnAttackToggle).text =
            if (CursorPrefs.attackEnabled(this)) "X attack button: ON" else "X attack button: OFF"

        findViewById<Button>(R.id.btnPinControls).setOnClickListener {
            val next = !CursorPrefs.controlsPinned(this)
            CursorPrefs.setControlsPinned(this, next)
            findViewById<Button>(R.id.btnPinControls).text =
                if (next) "Pin controls: ON (won’t move)" else "Pin controls: OFF (can drag)"
            notifyRunning()
            Toast.makeText(
                this,
                if (next) "Trackpad & X pinned in place" else "You can drag trackpad & X again",
                Toast.LENGTH_SHORT
            ).show()
        }
        findViewById<Button>(R.id.btnPinControls).text =
            if (CursorPrefs.controlsPinned(this)) "Pin controls: ON (won’t move)"
            else "Pin controls: OFF (can drag)"


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
                    preview.setPixelPosition(
                        event.x / v.width * preview.width,
                        event.y / v.height * preview.height
                    )
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
        refreshTrackpadLabel()
        notifyRunning()
    }

    private fun setMode(mode: Int) {
        CursorPrefs.setMode(this, mode)
        refreshModeLabel()
        notifyRunning()
        Toast.makeText(this, modeName(mode), Toast.LENGTH_SHORT).show()
    }

    private fun modeName(mode: Int) = when (mode) {
        CursorPrefs.MODE_FINGER -> "Finger"
        CursorPrefs.MODE_MOUSE -> "Mouse"
        else -> "Trackpad"
    }

    private fun refreshModeLabel() {
        modeValue.text = "Mode: ${modeName(CursorPrefs.mode(this))}"
    }

    private fun refreshTrackpadLabel() {
        trackpadPosValue.text =
            "Trackpad position (from bottom-right): X=${CursorPrefs.trackpadX(this)}  Y=${CursorPrefs.trackpadY(this)}"
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
        if (!CursorAccessibilityService.isEnabled(this)) {
            Toast.makeText(this, "Enable Accessibility → Custom Cursor", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else {
            CursorAccessibilityService.instance?.reloadPrefs()
            Toast.makeText(this, "Cursor refreshed", Toast.LENGTH_SHORT).show()
        }
        refreshStatus()
    }

    private fun stopCursor() {
        CursorPrefs.setEnabled(this, false)
        stopService(Intent(this, CursorOverlayService::class.java))
        Toast.makeText(this, "Disable Accessibility to fully stop", Toast.LENGTH_LONG).show()
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
            append("  ·  X: ")
            append(if (CursorPrefs.attackEnabled(this@MainActivity)) "ON" else "OFF")
        }
    }
}
