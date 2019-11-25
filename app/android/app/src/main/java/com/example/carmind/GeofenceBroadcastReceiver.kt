package com.example.carmind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
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