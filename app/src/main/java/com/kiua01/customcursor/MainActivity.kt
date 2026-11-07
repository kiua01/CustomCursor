package com.kiua01.customcursor

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.view.View
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

    private val pickPng = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val dest = File(filesDir, "custom_cursor.png")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            CursorPrefs.setPngPath(this, dest.absolutePath)
            preview.loadFromPrefs()
            notifyService()
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
        preview.loadFromPrefs()
        preview.setNormalizedPosition(0.5f, 0.5f)

        setupSeek(R.id.seekScale, (CursorPrefs.scale(this) * 100).toInt().coerceIn(25, 400)) { progress ->
            val v = progress / 100f
            CursorPrefs.setScale(this, v)
            scaleValue.text = "Scale: %.2f".format(v)
            preview.loadFromPrefs()
            notifyService()
        }
        scaleValue.text = "Scale: %.2f".format(CursorPrefs.scale(this))

        setupSeek(R.id.seekSize, CursorPrefs.sizePx(this).coerceIn(16, 256)) { progress ->
            CursorPrefs.setSizePx(this, progress)
            sizeValue.text = "Size: ${progress}px"
            preview.loadFromPrefs()
            notifyService()
        }
        sizeValue.text = "Size: ${CursorPrefs.sizePx(this)}px"

        setupSeek(R.id.seekSpeed, (CursorPrefs.speed(this) * 100).toInt().coerceIn(10, 500)) { progress ->
            val v = progress / 100f
            CursorPrefs.setSpeed(this, v)
            speedValue.text = "Speed: %.2f".format(v)
            notifyService()
        }
        speedValue.text = "Speed: %.2f".format(CursorPrefs.speed(this))

        setupSeek(R.id.seekOpacity, (CursorPrefs.opacity(this) * 100).toInt().coerceIn(10, 100)) { progress ->
            val v = progress / 100f
            CursorPrefs.setOpacity(this, v)
            opacityValue.text = "Opacity: %.0f%%".format(v * 100)
            preview.loadFromPrefs()
            notifyService()
        }
        opacityValue.text = "Opacity: %.0f%%".format(CursorPrefs.opacity(this) * 100)

        setupSeek(R.id.seekSmooth, (CursorPrefs.smoothing(this) * 100).toInt().coerceIn(0, 95)) { progress ->
            val v = progress / 100f
            CursorPrefs.setSmoothing(this, v)
            smoothValue.text = "Smoothing: %.0f%%".format(v * 100)
            notifyService()
        }
        smoothValue.text = "Smoothing: %.0f%%".format(CursorPrefs.smoothing(this) * 100)

        findViewById<Button>(R.id.btnPickPng).setOnClickListener {
            pickPng.launch("image/png")
        }
        findViewById<Button>(R.id.btnResetPng).setOnClickListener {
            CursorPrefs.setPngPath(this, null)
            preview.loadFromPrefs()
            notifyService()
            Toast.makeText(this, "Default cursor restored", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startCursor() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopCursor() }

        // Demo: drag on preview area moves cursor
        findViewById<View>(R.id.previewArea).setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_MOVE -> {
                    val v = findViewById<View>(R.id.previewArea)
                    preview.setPixelPosition(event.x * preview.width / v.width, event.y * preview.height / v.height)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        CursorPrefs.setEnabled(this, true)
        val intent = Intent(this, CursorOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        refreshStatus()
        Toast.makeText(this, "Cursor overlay started", Toast.LENGTH_SHORT).show()
    }

    private fun stopCursor() {
        CursorPrefs.setEnabled(this, false)
        stopService(Intent(this, CursorOverlayService::class.java))
        refreshStatus()
        Toast.makeText(this, "Cursor overlay stopped", Toast.LENGTH_SHORT).show()
    }

    private fun notifyService() {
        if (CursorPrefs.isEnabled(this)) {
            startService(Intent(this, CursorOverlayService::class.java))
        }
    }

    private fun refreshStatus() {
        val overlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        val running = CursorPrefs.isEnabled(this)
        statusText.text = buildString {
            append(if (running) "● Cursor ON" else "○ Cursor OFF")
            append("  ·  Overlay: ")
            append(if (overlay) "OK" else "NEED PERMISSION")
            append("  ·  PNG: ")
            append(if (CursorPrefs.pngPath(this@MainActivity) != null) "custom" else "default")
        }
    }
}
