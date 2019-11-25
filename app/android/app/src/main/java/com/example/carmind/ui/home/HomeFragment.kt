package com.example.carmind.ui.home

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.carmind.*
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.Geofence.NEVER_EXPIRE
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.jakewharton.rx.ReplayingShare
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.exceptions.BleScanException
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_home.*
import java.util.*
import kotlin.collections.ArrayList

class HomeFragment : Fragment() {
    private val rxBleClient = CarmindApplication.rxBleClient
    private lateinit var bleDevice: RxBleDevice

    private var prevReceived = -1

    private var scanDisposable: Disposable? = null
    private var connectDisposable: Disposable? = null
    private var stateDisposable: Disposable? = null
    private val disconnectTriggerSubject = PublishSubject.create<Unit>()

    private val resultsAdapter =
        ScanResultsAdapter { scanResult ->
            //            startActivity(context?.let { it1 -> MainActivity.newInstance(it1, scanResult.bleDevice.macAddress) })
            Log.v("handler", "user clicked on ${scanResult.bleDevice.macAddress}")
            connectBleDevice(scanResult.bleDevice.macAddress)
        }

    private fun registerGeofence() {
        with(LocationServices.getGeofencingClient(context!!)) {
            removeGeofences(geofencePendingIntent)?.run {
                addOnSuccessListener {
                    Log.v("event", "geofence successfully removed")
                }
                addOnFailureListener {
                    Log.v("event", "geofence failed to be removed")
                }
            }

            addGeofences(
                geofenceRequest,
                geofencePendingIntent
            )?.run {
                addOnSuccessListener {
                    Log.v("event", "geofence succeeded")
                }
                addOnFailureListener {
                    Log.v("event", "geofence failed")
                }
            }
        }
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)
    }

    private val geofenceRequest: GeofencingRequest by lazy {
        val geofenceList = ArrayList<Geofence>()

        geofenceList.add(
            Geofence.Builder()
                .setRequestId("parking_structure_1")
                .setCircularRegion(33.757832, -117.938904, 100f)
                .setExpirationDuration(NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        )

        GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofenceList)
        }.build()
    }

    private fun getBondedMacAddr(): List<String> {
        return rxBleClient.bondedDevices.filter {
            it?.name?.contains("Carmind") ?: false
        }.map { it.macAddress!! }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        if (context?.isPermissionGranted()!!) {
            return inflater.inflate(R.layout.fragment_home, container, false)
        }
        return null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (context?.isPermissionGranted()!!) {
            registerGeofence()
            scanBleDevices()
            configureResultList()

            scan_toggle_btn.setOnClickListener {
                scanBleDevices()
            }
            disconnect_button.setOnClickListener {
                unbondDevice()
                triggerDisconnect()
            }
        }
        Log.v("event", "view created")
    }

    private fun connectBleDevice(macAddr: String) {
        bleDevice = rxBleClient.getBleDevice(macAddr)
        bleDevice.observeConnectionStateChanges()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { onConnectionStateChange(it) }
            .let { stateDisposable = it }

        bleDevice.establishConnection(true)
            .takeUntil(disconnectTriggerSubject)
            .compose(ReplayingShare.instance())
            .doFinally { activity?.runOnUiThread { dispose() } }
            .flatMap { it.setupNotification(UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")) }
            .doOnNext { activity?.runOnUiThread { notificationHasBeenSetUp() } }
            .flatMap { it }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ onNotificationReceived(it) }, { onNotificationSetupFailure(it) })
            .let { connectDisposable = it }
    }

    private fun configureResultList() {
        with(scan_results) {
            setHasFixedSize(true)
            itemAnimator = null
            adapter = resultsAdapter
        }
    }

    private fun onConnectionStateChange(newState: RxBleConnection.RxBleConnectionState) {
        connection_state.text = newState.toString()
    }

    private fun unbondDevice() {
        if (::bleDevice.isInitialized && bleDevice.connectionState != RxBleConnection.RxBleConnectionState.DISCONNECTED) {
            try {
                bleDevice.bluetoothDevice::class.java.getMethod("removeBond")
                    .invoke(bleDevice.bluetoothDevice)
            } catch (e: Exception) {
                Log.e("event", "Removing bond has been failed. ${e.message}")
            }
        }
        Log.v("event", "bonded devices cnt after unbond: ${getBondedMacAddr().size}")
    }

    private fun triggerDisconnect() {
        if (::bleDevice.isInitialized && bleDevice.connectionState != RxBleConnection.RxBleConnectionState.DISCONNECTED) {
            disconnectTriggerSubject.onNext(Unit)
            connectDisposable?.dispose()
            connectDisposable = null
        }
    }

    private val scanSettings: ScanSettings by lazy {
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
    }

    private val scanFilter: ScanFilter by lazy {
        ScanFilter.Builder()
//            .setDeviceName("Carmind")
            .build()
    }

    private fun scanBleDevices() {
        rxBleClient.scanBleDevices(scanSettings, scanFilter)
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally {
                Log.v("event", "stopped scanning")
                dispose()
            }
            .subscribe(
                {
                    resultsAdapter.addScanResult(it)
                    if (((::bleDevice.isInitialized
                                && bleDevice.connectionState == RxBleConnection.RxBleConnectionState.DISCONNECTED)
                                || !::bleDevice.isInitialized)
                        && getBondedMacAddr().contains(it.bleDevice.macAddress)
                    ) {
                        Log.v("event", "connecting device")
                        connectBleDevice(it.bleDevice.macAddress)
                    }
                },
                { onScanFailure(it) }
            )
            .let { scanDisposable = it }
    }

    private fun dispose() {
//        Log.v("event", "disconnected")
        scanDisposable?.dispose()
        scanDisposable = null
        resultsAdapter.clearScanResults()
//        scanBleDevices()
    }

    private fun onScanFailure(throwable: Throwable) {
        if (throwable is BleScanException) Log.v("scan", throwable.toString())
    }

    private fun onNotificationReceived(bytes: ByteArray) {
        val receivedBatLevel = bytes[0].toInt()
        Log.v("event", "bytes are $receivedBatLevel")
        if (prevReceived == -1) {
            prevReceived = receivedBatLevel
        } else {
            if ((prevReceived == 100 && receivedBatLevel != 0) || (prevReceived != 100 && receivedBatLevel - 1 != prevReceived)) {
                Log.v("event", "missed one: $prevReceived $receivedBatLevel")
            }
            prevReceived = receivedBatLevel
        }
    }

    private fun onNotificationSetupFailure(throwable: Throwable) {
//        activity?.showSnackbarShort("Notifications error: $throwable")
        Log.v("event", "notification complete/error: $throwable")
    }

    private fun notificationHasBeenSetUp() =
        activity?.showSnackbarShort("Notifications has been set up")

    override fun onPause() {
        super.onPause()

        scanDisposable?.dispose()
        triggerDisconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        stateDisposable?.dispose()
    }
}
