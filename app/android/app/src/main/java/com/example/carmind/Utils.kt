package com.example.carmind

import android.Manifest.permission
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice

private const val REQUEST_PERMISSION_COARSE_LOCATION = 101

internal fun Context.isLocationPermissionGranted(): Boolean {
    val isGranted = ContextCompat.checkSelfPermission(
        this,
        permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    Log.v("permission", "Is granted $isGranted")
    return isGranted
}

internal fun Activity.requestLocationPermission() =
    ActivityCompat.requestPermissions(
        this,
        arrayOf(permission.ACCESS_COARSE_LOCATION),
        REQUEST_PERMISSION_COARSE_LOCATION
    )

internal fun isLocationPermissionGranted(requestCode: Int, grantResults: IntArray) =
    requestCode == REQUEST_PERMISSION_COARSE_LOCATION && grantResults[0] == PackageManager.PERMISSION_GRANTED

internal val RxBleDevice.isConnected: Boolean
    get() = connectionState == RxBleConnection.RxBleConnectionState.CONNECTED

internal fun Activity.showSnackbarShort(text: CharSequence) {
    Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_SHORT).show()
}

internal fun Activity.showSnackbarShort(@StringRes text: Int) {
    Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_SHORT).show()
}
