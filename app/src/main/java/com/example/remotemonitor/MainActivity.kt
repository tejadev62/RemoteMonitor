package com.example.remotemonitor

import android.app.AppOpsManager
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var updateManager: UpdateManager

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if ((result.resultCode == RESULT_OK) && (result.data != null)) {
            startAppService(result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        updateManager = UpdateManager(this)

        val startButton = findViewById<Button>(R.id.startButton)
        startButton.setOnClickListener {
            if (checkPermissions()) {
                startScreenCapture()
            }
        }

        val updateButton = findViewById<Button>(R.id.updateButton)
        updateButton.setOnClickListener {
            updateManager.checkForUpdates()
        }

        requestRuntimePermissions()
    }

    private fun checkPermissions(): Boolean {
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Please grant Usage Access permission", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return false
        }
        return true
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.RECORD_AUDIO,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
    }

    private fun startScreenCapture() {
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startAppService(projectionIntent: Intent) {
        val serviceIntent = Intent(this, ScreenSharingService::class.java).apply {
            putExtra("PROJECTION_INTENT", projectionIntent)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
