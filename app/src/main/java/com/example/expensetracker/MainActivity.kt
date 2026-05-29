package com.example.expensetracker

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private val PERMISSION_REQUEST_SMS = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        // Button 1: Request SMS permission (runtime permission on Android 6+)
        findViewById<Button>(R.id.btnNotificationPermission).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_SMS),
                    PERMISSION_REQUEST_SMS
                )
            }
        }

        // Button 2: Request Notification Listener access (via Settings)
        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Button 3: Request Overlay permission (via Settings)
        findViewById<Button>(R.id.btnIgnore).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        // Clean up old transaction hashes on app startup
        TransactionDeduplicator(this).cleanup()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_SMS) {
            updateStatus()
        }
    }

    private fun updateStatus() {
        val smsGranted = hasSmsPermission()
        val notifGranted = isNotificationListenerEnabled()
        val overlayGranted = Settings.canDrawOverlays(this)

        statusText.text = """
            SMS access: ${if (smsGranted) "✓ Enabled" else "✗ Not enabled"}
            Notification access: ${if (notifGranted) "✓ Enabled" else "✗ Not enabled"}
            Overlay permission: ${if (overlayGranted) "✓ Granted" else "✗ Not granted"}
        """.trimIndent()
    }

    private fun hasSmsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(Manifest.permission.READ_SMS) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }
}
