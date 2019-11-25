package com.example.carmind

import android.Manifest.permission
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice

private const val PERMISSION_REQUEST_CODE = 101
private val permissions =
    arrayOf(permission.ACCESS_FINE_LOCATION, permission.FOREGROUND_SERVICE)

internal fun Context.isPermissionGranted(): Boolean {
    for (p in permissions) {
        if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
            Log.v("event", "permission not granted for $p")
            return false
        }
    }
    Log.v("event", "all permission granted")
    return true
}

internal fun Activity.requestPermission() =
    ActivityCompat.requestPermissions(
        this,
        permissions,
        PERMISSION_REQUEST_CODE
    )

internal val RxBleDevice.isConnected: Boolean
    get() = connectionState == RxBleConnection.RxBleConnectionState.CONNECTED

internal fun Activity.showSnackbarShort(text: CharSequence) {
    Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_SHORT).show()
}

internal fun Activity.showSnackbarShort(@StringRes text: Int) {
    Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_SHORT).show()
}

fun startBleService(context: Context?) {
    Log.v("event", "geofence starting ble service")
    val serviceIntent = Intent(context, BleService::class.java)
    serviceIntent.action = BleService.ACTION_START_FOREGROUND_SERVICE
    ContextCompat.startForegroundService(context!!, serviceIntent)
}

fun stopBleService(context: Context?) {
    val serviceIntent = Intent(context, BleService::class.java)
    serviceIntent.action = BleService.ACTION_STOP_FOREGROUND_SERVICE
    ContextCompat.startForegroundService(context!!, serviceIntent)
}
