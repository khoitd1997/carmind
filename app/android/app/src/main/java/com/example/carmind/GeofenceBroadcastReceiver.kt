package com.example.carmind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.carmind.BleService.Companion.ACTION_START_FOREGROUND_SERVICE
import com.example.carmind.BleService.Companion.ACTION_STOP_FOREGROUND_SERVICE
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    fun startBleService(context: Context?) {
        Log.v("event", "geofence starting ble service")
        val serviceIntent = Intent(context, BleService::class.java)
        serviceIntent.action = ACTION_START_FOREGROUND_SERVICE
        ContextCompat.startForegroundService(context!!, serviceIntent)
    }

    fun stopBleService(context: Context?) {
        val serviceIntent = Intent(context, BleService::class.java)
        serviceIntent.action = ACTION_STOP_FOREGROUND_SERVICE
        ContextCompat.startForegroundService(context!!, serviceIntent)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        // TODO: Check correct intent type
        Log.v("event", "geofence receive called")
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent.hasError()) {
            Log.e(
                "event",
                "geofence ${GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)})"
            )
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_EXIT -> {
                val triggeringGeofences = geofencingEvent.triggeringGeofences
                startBleService(context)
                Log.i("event", "geofence received: $triggeringGeofences $geofenceTransition")
            }
            else -> {
                Log.e("event", "geofence invalid type")
            }
        }
    }
}