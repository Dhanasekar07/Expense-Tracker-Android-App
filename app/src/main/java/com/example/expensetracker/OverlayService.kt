package com.example.expensetracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var dbHelper: ExpenseDbHelper? = null

    private var currentAmount: Double = 0.0
    private var currentSource: String = ""
    private var currentChannel: String = ""

    private val autoDismiss = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable {
        removeOverlay()
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "expense_overlay_channel"
        private const val NOTIF_ID = 1001
        private const val AUTO_DISMISS_MS = 15_000L
    }

    override fun onCreate() {
        super.onCreate()
        dbHelper = ExpenseDbHelper(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentAmount = intent?.getDoubleExtra("amount", 0.0) ?: 0.0
        currentSource = intent?.getStringExtra("source") ?: ""
        currentChannel = intent?.getStringExtra("channel") ?: "unknown"

        removeOverlay()
        showOverlay()

        autoDismiss.removeCallbacks(autoDismissRunnable)
        autoDismiss.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS)
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Expense Overlay",
                    NotificationManager.IMPORTANCE_MIN
                )
                nm.createNotificationChannel(channel)
            }
            val notification: Notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Expense Tracker")
                .setContentText("Waiting for category selection")
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setOngoing(true)
                .build()
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun showOverlay() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 120

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return
        }

        overlayView?.findViewById<Button>(R.id.btnTea)?.setOnClickListener { logExpense("Tea") }
        overlayView?.findViewById<Button>(R.id.btnTravel)?.setOnClickListener { logExpense("Travel") }
        overlayView?.findViewById<Button>(R.id.btnGrocery)?.setOnClickListener { logExpense("Grocery") }
        overlayView?.findViewById<Button>(R.id.btnIgnore)?.setOnClickListener {
            removeOverlay()
            stopSelf()
        }
    }

    private fun logExpense(category: String) {
        dbHelper?.insertExpense(category, currentAmount, currentSource, currentChannel)
        val msg = if (currentAmount > 0)
            "Logged: $category ₹$currentAmount"
        else
            "Logged: $category"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        removeOverlay()
        stopSelf()
    }

    private fun removeOverlay() {
        autoDismiss.removeCallbacks(autoDismissRunnable)
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlayView = null
    }

    override fun onDestroy() {
        removeOverlay()
        dbHelper?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
