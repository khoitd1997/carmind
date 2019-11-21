package com.example.carmind.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.carmind.*
import com.polidea.rxandroidble2.exceptions.BleScanException
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanResult
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_home.*

class HomeFragment : Fragment() {
    private val rxBleClient = CarmindApplication.rxBleClient

    private var scanDisposable: Disposable? = null

    private val resultsAdapter =
        ScanResultsAdapter { startActivity(context?.let { it1 -> MainActivity.newInstance(it1, it.bleDevice.macAddress) }) }

    private var hasClickedScan = false

    private val isScanning: Boolean
        get() = scanDisposable != null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureResultList()

        scan_toggle_btn.setOnClickListener { onScanToggleClick() }
        Log.v("event", "view created")
    }

    private fun configureResultList() {
        with(scan_results) {
            setHasFixedSize(true)
            itemAnimator = null
            adapter = resultsAdapter
        }
    }

    private fun onScanToggleClick() {
        Log.v("event", "scan toggle click")
        if (isScanning) {
            scanDisposable?.dispose()
        } else {
            if (context?.isLocationPermissionGranted()!!) {
                scanBleDevices()
                    .observeOn(AndroidSchedulers.mainThread())
                    .doFinally { dispose() }
                    .subscribe({ resultsAdapter.addScanResult(it) }, { onScanFailure(it) })
                    .let { scanDisposable = it }
                Log.v("event", "permission granted")
            } else {
                hasClickedScan = true
                activity?.requestLocationPermission()
                Log.v("event", "requesting permission")
            }
        }
        updateButtonUIState()
    }

    private fun scanBleDevices(): Observable<ScanResult> {
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        val scanFilter = ScanFilter.Builder()
//            .setDeviceAddress("B4:99:4C:34:DC:8B")
            // add custom filters if needed
            .build()

        return rxBleClient.scanBleDevices(scanSettings, scanFilter)
    }

    private fun dispose() {
        scanDisposable = null
        resultsAdapter.clearScanResults()
        updateButtonUIState()
    }

    private fun onScanFailure(throwable: Throwable) {
        if (throwable is BleScanException) Log.v("scan", throwable.toString())
    }

    private fun updateButtonUIState() =
        scan_toggle_btn.setText(if (isScanning) R.string.button_stop_scan else R.string.button_start_scan)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (isLocationPermissionGranted(requestCode, grantResults) && hasClickedScan) {
            hasClickedScan = false
            scanBleDevices()
        }
    }

    public override fun onPause() {
        super.onPause()
        // Stop scanning in onPause callback.
        if (isScanning) scanDisposable?.dispose()
    }
}
