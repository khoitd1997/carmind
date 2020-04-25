package com.example.carmind.ui.home

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.carmind.BleService
import com.example.carmind.GeofenceBroadcastReceiver
import com.example.carmind.R
import com.example.carmind.isPermissionGranted
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.Geofence.NEVER_EXPIRE
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.android.synthetic.main.fragment_home.*

class HomeFragment : Fragment() {
    private var prevReceived = -1

    private val resultsAdapter =
        ScanResultsAdapter {
            sendMsg(
                BleService.Companion.BleIpcCmd.CONNECT_DEVICE,
                BleService.Companion.ConnectInfo(it.bleDevice.macAddress)
            )
            Log.v("handler", "user clicked on ${it.bleDevice.macAddress}")
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

        Intent(context, BleService::class.java).also { intent ->
            context?.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        }

        if (context?.isPermissionGranted()!!) {
            registerGeofence()
            configureResultList()

            scan_toggle_btn.setOnClickListener {
                sendMsg(BleService.Companion.BleIpcCmd.START_SCAN, null)
            }
            disconnect_button.setOnClickListener {
                sendMsg(
                    BleService.Companion.BleIpcCmd.DISCONNECT_DEVICE,
                    BleService.Companion.DisconnectInfo(true)
                )
            }
        }
        Log.v("event", "view created")
    }

    private fun configureResultList() {
        with(scan_results) {
            setHasFixedSize(true)
            itemAnimator = null
            adapter = resultsAdapter
        }
    }

    private fun dispose() {
        resultsAdapter.clearScanResults()
    }

    override fun onDestroy() {
        super.onDestroy()
        sendMsg(BleService.Companion.BleIpcCmd.DEINIT, null)
    }

    private var mService: Messenger? = null
    private var bound: Boolean = false
    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            mService = Messenger(service)
            bound = true
            sendMsg(
                BleService.Companion.BleIpcCmd.INIT,
                BleService.Companion.InitInfo(resultsAdapter)
            )
        }

        override fun onServiceDisconnected(className: ComponentName) {
            mService = null
            bound = false
        }
    }

    private fun sendMsg(what: BleService.Companion.BleIpcCmd, obj: Any?) {
        if (bound) {
            val msg: Message = Message.obtain(null, what.ordinal, obj)
            try {
                mService?.send(msg)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }
    }

    override fun onPause() {
        super.onPause()

//        scanDisposable?.dispose()
//        triggerDisconnect()

        if (bound) {
            context?.unbindService(mConnection)
            bound = false
        }
    }
}
