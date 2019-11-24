package com.example.carmind.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.carmind.*
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

    private val isScanning: Boolean
        get() = scanDisposable != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (context?.isLocationPermissionGranted()) {
            true -> scanBleDevices()
            false -> activity?.requestLocationPermission()
        }
        Log.v("event", "bonded device: ${rxBleClient.bondedDevices}")
    }

    private fun getBondedDevices(): List<String> {
        return rxBleClient.bondedDevices.filter {
            it?.name?.contains("Carmind") ?: false
        }.map { it.name!! }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureResultList()

        disconnect_button.setOnClickListener {
            if (::bleDevice.isInitialized && bleDevice.isConnected) {
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

//    private fun onConnectToggleClick() {
//        if (bleDevice.isConnected) {
//            triggerDisconnect()
//        } else {
//            bleDevice.establishConnection(true)
//                .observeOn(AndroidSchedulers.mainThread())
//                .doFinally { dispose() }
//                .subscribe({ onConnectionReceived() }, { onConnectionFailure(it) })
//                .let { connectionDisposable = it }
//        }
//    }

    private fun onConnectionFailure(throwable: Throwable) =
        activity?.showSnackbarShort("Connection error: $throwable")

    private fun onConnectionReceived() {
        activity?.showSnackbarShort("Connection received")
    }

    private fun onConnectionStateChange(newState: RxBleConnection.RxBleConnectionState) {
        connection_state.text = newState.toString()
    }

    private fun triggerDisconnect() {
        if (::bleDevice.isInitialized && bleDevice.connectionState != RxBleConnection.RxBleConnectionState.DISCONNECTED) {
            try {
                bleDevice.bluetoothDevice::class.java.getMethod("removeBond")
                    .invoke(bleDevice.bluetoothDevice)
            } catch (e: Exception) {
                Log.e("event", "Removing bond has been failed. ${e.message}")
            }
        }
        Log.v("event", "bonded devices cnt after unbond: ${getBondedDevices().size}")
        disconnectTriggerSubject.onNext(Unit)
    }

    private fun scanBleDevices() {
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        val scanFilter = ScanFilter.Builder()
            .setDeviceName("Carmind")
            .build()

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
                        && getBondedDevices().contains(it.bleDevice.macAddress)
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
//        scanDisposable?.dispose()
        scanDisposable = null
        resultsAdapter.clearScanResults()
        updateButtonUIState()
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
//        activity?.showSnackbarShort("Change: $bytes")
    }

    private fun onNotificationSetupFailure(throwable: Throwable) {
//        activity?.showSnackbarShort("Notifications error: $throwable")
        Log.v("event", "notification complete/error: $throwable")
    }

    private fun notificationHasBeenSetUp() =
        activity?.showSnackbarShort("Notifications has been set up")


    private fun updateButtonUIState() =
        scan_toggle_btn.setText(if (isScanning) R.string.button_stop_scan else R.string.button_start_scan)

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (isLocationPermissionGranted(requestCode, grantResults)) {
            scanBleDevices()
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop scanning in onPause callback.
        if (isScanning) scanDisposable?.dispose()
        triggerDisconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        stateDisposable?.dispose()
    }
}
