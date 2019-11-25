package com.example.carmind

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.carmind.ui.home.ScanResultsAdapter

const val MSG_SAY_HELLO = 1

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
                    .setContentTitle("Carmind")
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

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    private lateinit var mMessenger: Messenger

    /**
     * Handler of incoming messages from clients.
     */
    internal class IncomingHandler(
        context: Context,
        private val applicationContext: Context = context.applicationContext
    ) : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_SAY_HELLO -> {
                    Toast.makeText(applicationContext, "hello!", Toast.LENGTH_SHORT).show()
                    val receivedObj = msg.obj
                    if (receivedObj is ScanResultsAdapter) {
                        Log.v("event", "received result adapter")
                        receivedObj.clearScanResults()
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    override fun onBind(intent: Intent): IBinder? {
        Toast.makeText(applicationContext, "binding", Toast.LENGTH_SHORT).show()
        mMessenger = Messenger(IncomingHandler(this))
        return mMessenger.binder
    }
}