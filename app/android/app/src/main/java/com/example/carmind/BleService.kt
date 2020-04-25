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
import com.jakewharton.rx.ReplayingShare
import com.polidea.rxandroidble2.*
import com.polidea.rxandroidble2.exceptions.BleScanException
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.util.*
import java.util.concurrent.TimeUnit

class BleService : Service() {
    companion object {
        const val CHANNEL_ID = "BleServiceChannel"

        const val ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE"
        const val ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE"

        enum class BleIpcCmd {
            INIT, DEINIT,
            START_SCAN, STOP_SCAN,
            CONNECT_DEVICE, DISCONNECT_DEVICE,
            TEST
        }

        data class InitInfo(val scanAdapter: ScanResultsAdapter)
        data class ConnectInfo(val macAddr: String)
        data class DisconnectInfo(val unbond: Boolean)
    }

    lateinit var rxBleClient: RxBleClient
        private set
    private var bleDevice: RxBleDevice? = null

    private var scanDisposable: Disposable? = null
    private var scanRefreshDisposable: Disposable? = null

    private var connectDisposable: Disposable? = null
    private var stateDisposable: Disposable? = null

    override fun onCreate() {
        super.onCreate()

        rxBleClient = RxBleClient.create(this)
        RxBleClient.updateLogOptions(
            LogOptions.Builder()
                .setLogLevel(LogConstants.INFO)
                .setMacAddressLogSetting(LogConstants.MAC_ADDRESS_FULL)
                .setUuidsLogSetting(LogConstants.UUIDS_FULL)
                .setShouldLogAttributeValues(true)
                .build()
        )

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
        Log.v("event", "onCreate of ble service")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.v("event", "ble service onStartCommand")
        when (intent.action) {
            ACTION_STOP_FOREGROUND_SERVICE -> stopForegroundService()
        }

        return START_STICKY
    }

    private fun stopForegroundService() {
        Log.v("event", "Stop foreground service.")
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stateDisposable?.dispose()
    }

    private lateinit var mMessenger: Messenger
    internal var scanResultsAdapter: ScanResultsAdapter? = null

    internal class IncomingHandler(
        context: Context,
        private val applicationContext: Context = context.applicationContext
    ) : Handler() {
        private var parentContext: BleService = context as BleService
        override fun handleMessage(msg: Message) {
            val receivedObj = msg.obj
            with(parentContext) {
                when (BleIpcCmd.values()[msg.what]) {
                    BleIpcCmd.INIT -> {
                        scanResultsAdapter = (receivedObj as InitInfo).scanAdapter
                    }

                    BleIpcCmd.DEINIT -> {
                        scanResultsAdapter = null
                        scanDisposable?.dispose()
                    }

                    BleIpcCmd.START_SCAN -> {
                        parentContext.scanBleDevices()
                    }

                    BleIpcCmd.STOP_SCAN -> {
                        scanDisposable?.dispose()
                    }

                    BleIpcCmd.CONNECT_DEVICE -> {
                        val connectInfo = receivedObj as ConnectInfo
                        connectDevice(connectInfo.macAddr)
                    }

                    BleIpcCmd.DISCONNECT_DEVICE -> {
                        val disconnectInfo = receivedObj as DisconnectInfo
                        disconnectDevice(disconnectInfo.unbond)
                    }

                    BleIpcCmd.TEST -> {
                        parentContext.scanResultsAdapter?.clearScanResults()
                    }

                    else -> super.handleMessage(msg)
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        Toast.makeText(applicationContext, "binding", Toast.LENGTH_SHORT).show()
        mMessenger = Messenger(IncomingHandler(this))
        return mMessenger.binder
    }

    private fun connectDevice(macAddr: String) {
        bleDevice = rxBleClient.getBleDevice(macAddr)
        bleDevice?.let { dev ->
            dev.observeConnectionStateChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { onConnectionStateChange(it) }
                .let { stateDisposable = it }

            dev.establishConnection(true)
                .compose(ReplayingShare.instance())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally {
                    connectDisposable = null
                    bleDevice = null
                }
                .flatMap { it.setupNotification(UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")) }
//            .doOnNext { activity?.runOnUiThread { notificationHasBeenSetUp() } }
                .flatMap { it }
                .subscribe({ onNotificationReceived(it) }, { onNotificationSetupFailure(it) })
                .let { connectDisposable = it }
        }
    }

    private fun unbondDevice() {
        bleDevice?.let { dev ->
            try {
                dev.bluetoothDevice::class.java.getMethod("removeBond")
                    .invoke(dev.bluetoothDevice)
            } catch (e: Exception) {
                Log.e("event", "Removing bond has been failed. ${e.message}")
            }
            Log.v("event", "bonded devices cnt after unbond: ${getBondedMacAddr().size}")
        }
    }

    private fun disconnectDevice(unbond: Boolean) {
        if (unbond) {
            unbondDevice()
        }
        connectDisposable?.dispose()
    }

    private fun getBondedMacAddr(): List<String> {
        return rxBleClient.bondedDevices.filter {
            it?.name?.contains("Carmind") ?: false
        }.map { it.macAddress!! }
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
        if (!isScanning()) {
            rxBleClient.scanBleDevices(scanSettings, scanFilter)
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally {
                    scanDisposable = null
                    scanRefreshDisposable?.dispose()

                    Log.v("event", "stopped scanning")
//                    dispose()
                }
                .subscribe(
                    {
                        scanResultsAdapter?.addScanResult(it)
                        if (bleDevice == null && getBondedMacAddr().contains(it.bleDevice.macAddress)
                        ) {
                            Log.v("event", "connecting device")
                            connectDevice(it.bleDevice.macAddress)
                        }
                    },
                    { onScanFailure(it) }
                )
                .let {
                    scanDisposable = it

                    Observable.interval(5, 5, TimeUnit.SECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .doFinally { scanRefreshDisposable = null }
                        .subscribe { scanResultsAdapter?.clearScanResults() }
                        .let { it2 -> scanRefreshDisposable = it2 }
                }
        }
    }

    private fun isScanning() = scanDisposable != null

    private fun dispose() {
//        Log.v("event", "disconnected")
        scanDisposable?.dispose()
        scanDisposable = null
    }

    private fun onConnectionStateChange(newState: RxBleConnection.RxBleConnectionState) {
//        connection_state.text = newState.toString()
    }

    private fun onScanFailure(throwable: Throwable) {
        if (throwable is BleScanException) Log.v("scan", throwable.toString())
    }

    private fun onNotificationReceived(bytes: ByteArray) {
        val receivedBatLevel = bytes[0].toInt()
        Log.v("event", "bytes are $receivedBatLevel")
//        if (prevReceived == -1) {
//            prevReceived = receivedBatLevel
//        } else {
//            if ((prevReceived == 100 && receivedBatLevel != 0) || (prevReceived != 100 && receivedBatLevel - 1 != prevReceived)) {
//                Log.v("event", "missed one: $prevReceived $receivedBatLevel")
//            }
//            prevReceived = receivedBatLevel
//        }
    }

    private fun onNotificationSetupFailure(throwable: Throwable) {
//        activity?.showSnackbarShort("Notifications error: $throwable")
        Log.v("event", "notification complete/error: $throwable")
    }

    private fun notificationHasBeenSetUp() {
//        activity?.showSnackbarShort("Notifications has been set up")
    }
}