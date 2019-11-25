package com.example.carmind

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat




class BleService : Service() {
    companion object {
        const val CHANNEL_ID = "BleServiceChannel"

        const val ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE"
        const val ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        Log.v("event", "onCreate of ble service")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.v("event", "ble service onStartCommand")
        when (intent.action) {
            ACTION_START_FOREGROUND_SERVICE -> {
                // NOTE: DO NOT REMOVE THIS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val serviceChannel = NotificationChannel(
                        CHANNEL_ID,
                        "BLE Service Channel",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )

                    val manager = getSystemService(NotificationManager::class.java)
                    manager!!.createNotificationChannel(serviceChannel)
                }

                val notificationIntent = Intent(this, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0, notificationIntent, 0
                )

                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Foreground Service")
                    .setContentText("Carmind working with BLE device")
                    .setSmallIcon(R.drawable.common_google_signin_btn_icon_light)
                    .setContentIntent(pendingIntent)
                    .build()

                startForeground(1, notification)
            }
            ACTION_STOP_FOREGROUND_SERVICE -> stopForegroundService()
        }

//        return START_NOT_STICKY
        return START_STICKY
    }

    private fun stopForegroundService() {
        Log.v("event", "Stop foreground service.")
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}