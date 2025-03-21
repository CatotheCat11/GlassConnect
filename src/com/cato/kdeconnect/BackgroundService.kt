/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package com.cato.kdeconnect

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cato.kdeconnect.Backends.BaseLinkProvider
import com.cato.kdeconnect.Backends.BaseLinkProvider.ConnectionReceiver
import com.cato.kdeconnect.Backends.BluetoothBackend.BluetoothLinkProvider
import com.cato.kdeconnect.Backends.LanBackend.LanLinkProvider
import com.cato.kdeconnect.Helpers.NotificationHelper
//import com.cato.kdeconnect.Plugins.ClibpoardPlugin.ClipboardFloatingActivity
//import com.cato.kdeconnect.Plugins.RunCommandPlugin.RunCommandActivity
//import com.cato.kdeconnect.Plugins.RunCommandPlugin.RunCommandPlugin
import com.cato.kdeconnect.UserInterface.MainActivity

/**
 * This class (still) does 3 things:
 * - Keeps the app running by creating a foreground notification.
 * - Holds references to the active LinkProviders, but doesn't handle the DeviceLink those create (the KdeConnect class does that).
 * - Listens for network connectivity changes and tells the LinkProviders to re-check for devices.
 * It can be started by the KdeConnectBroadcastReceiver on some events or when the MainActivity is launched.
 */
class BackgroundService : Service() {
    private lateinit var applicationInstance: KdeConnect

    private val linkProviders = mutableListOf<BaseLinkProvider>()

    private val connectedToNonCellularNetwork = MutableLiveData<Boolean>()
    /** Indicates whether device is connected over wifi / usb / bluetooth / (anything other than cellular) */
    val isConnectedToNonCellularNetwork: LiveData<Boolean>
        get() = connectedToNonCellularNetwork

    fun updateForegroundNotification() {
        if (NotificationHelper.isPersistentNotificationEnabled(this)) {
            // Update the foreground notification with the currently connected device list
            val notificationManager = getSystemService<NotificationManager>()
            notificationManager?.notify(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        }
    }

    private fun registerLinkProviders() {
        linkProviders.add(LanLinkProvider(this))
        //linkProviders.add(LoopbackLinkProvider(this))
        linkProviders.add(BluetoothLinkProvider(this))
    }

    fun onNetworkChange(network: Network?) {
        if (!initialized) {
            Log.d(LOG_TAG, "ignoring onNetworkChange called before the service is initialized")
            return
        }
        Log.d(LOG_TAG, "onNetworkChange")
        for (linkProvider in linkProviders) {
            linkProvider.onNetworkChange(network)
        }
    }

    fun addConnectionListener(connectionReceiver: ConnectionReceiver) {
        for (linkProvider in linkProviders) {
            linkProvider.addConnectionReceiver(connectionReceiver)
        }
    }

    fun removeConnectionListener(connectionReceiver: ConnectionReceiver) {
        for (linkProvider in linkProviders) {
            linkProvider.removeConnectionReceiver(connectionReceiver)
        }
    }

    /** This will called only once, even if we launch the service intent several times */
    override fun onCreate() {
        super.onCreate()
        Log.d("KdeConnect/BgService", "onCreate")
        this.applicationInstance = KdeConnect.getInstance()
        instance = this

        KdeConnect.getInstance().addDeviceListChangedCallback("BackgroundService", this::updateForegroundNotification)

        // Register screen on listener
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        // See: https://developer.android.com/reference/android/net/ConnectivityManager.html#CONNECTIVITY_ACTION
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(KdeConnectBroadcastReceiver(), filter)

        registerLinkProviders()
        addConnectionListener(applicationInstance.connectionListener) // Link Providers need to be already registered
        for (linkProvider in linkProviders) {
            linkProvider.onStart()
        }
        initialized = true
    }

    fun changePersistentNotificationVisibility(visible: Boolean) {
        if (visible) {
            updateForegroundNotification()
        }
        else {
            stopForeground(true)
            Start(this)
        }
    }

    private fun createForegroundNotification(): Notification {
        // Why is this needed: https://developer.android.com/guide/components/services#Foreground

        val connectedDevices = mutableListOf<String>()
        val connectedDeviceIds = mutableListOf<String>()
        /*for (device in applicationInstance.devices.values) {
            if (device.isReachable && device.isPaired) {
                connectedDeviceIds.add(device.deviceId)
                connectedDevices.add(device.name)
            }
        }*/

        val intent = Intent(this, MainActivity::class.java)
        /*if (connectedDeviceIds.size == 1) {
            // Force open screen of the only connected device
            intent.putExtra(MainActivity.EXTRA_DEVICE_ID, connectedDeviceIds[0])
        }*/

        val pi = PendingIntent.getActivity(this, 0, intent, UPDATE_IMMUTABLE_FLAGS)
        val notification = NotificationCompat.Builder(this, NotificationHelper.Channels.PERSISTENT).apply {
            setSmallIcon(R.drawable.ic_notification)
            setOngoing(true)
            setContentIntent(pi)
            setPriority(NotificationCompat.PRIORITY_MIN) //MIN so it's not shown in the status bar before Oreo, on Oreo it will be bumped to LOW
            setShowWhen(false)
            setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            setAutoCancel(false)
            setGroup("BackgroundService")
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Pre-oreo, the notification will have an empty title line without this
            notification.setContentTitle(getString(R.string.kde_connect))
        }

        //if (connectedDevices.isEmpty()) {
        notification.setContentText(getString(R.string.foreground_notification_no_devices))
        /*}
        else {
            notification.setContentText(getString(R.string.foreground_notification_devices, connectedDevices.joinToString(", ")))

            // Adding an action button to send clipboard manually in Android 10 and later.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_DENIED) {
                val sendClipboard = ClipboardFloatingActivity.getIntent(this, true)
                val sendPendingClipboard = PendingIntent.getActivity(this, 3, sendClipboard, UPDATE_IMMUTABLE_FLAGS)
                notification.addAction(0, getString(R.string.foreground_notification_send_clipboard), sendPendingClipboard)
            }

            if (connectedDeviceIds.size == 1) {
                val deviceId = connectedDeviceIds[0]
                val device = KdeConnect.getInstance().getDevice(deviceId)
                if (device != null) {
                    // Adding two action buttons only when there is a single device connected.
                    // Setting up Send File Intent.
                    val sendFile = Intent(this, SendFileActivity::class.java)
                    sendFile.putExtra("deviceId", deviceId)
                    val sendPendingFile = PendingIntent.getActivity(this, 1, sendFile, UPDATE_IMMUTABLE_FLAGS)
                    notification.addAction(0, getString(R.string.send_files), sendPendingFile)

                    // Checking if there are registered commands and adding the button.
                    val plugin = device.getPlugin("RunCommandPlugin") as RunCommandPlugin?
                    if (plugin != null && plugin.commandList.isNotEmpty()) {
                        val runCommand = Intent(this, RunCommandActivity::class.java)
                        runCommand.putExtra("deviceId", connectedDeviceIds[0])
                        val runPendingCommand = PendingIntent.getActivity(this, 2, runCommand, UPDATE_IMMUTABLE_FLAGS)
                        notification.addAction(0, getString(R.string.pref_plugin_runcommand), runPendingCommand)
                    }
                }
            }
        }*/
        return notification.build()
    }

    override fun onDestroy() {
        Log.d("KdeConnect/BgService", "onDestroy")
        initialized = false
        for (linkProvider in linkProviders) {
            linkProvider.onStop()
        }
        KdeConnect.getInstance().removeDeviceListChangedCallback("BackgroundService")
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOG_TAG, "onStartCommand")
        if (NotificationHelper.isPersistentNotificationEnabled(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            }
            else {
                startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
            }
        }
        if (intent != null && intent.getBooleanExtra("refresh", false)) {
            onNetworkChange(null)
        }
        return START_STICKY
    }

    companion object {
        const val LOG_TAG = "KDE/BackgroundService"

        const val UPDATE_IMMUTABLE_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        private const val FOREGROUND_NOTIFICATION_ID = 1

        @JvmStatic
        var instance: BackgroundService? = null
            private set

        private var initialized = false

        fun Start(context: Context) {
            Log.d(LOG_TAG, "Start")
            val intent = Intent(context, BackgroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        @JvmStatic
        fun ForceRefreshConnections(context: Context) {
            Log.d(LOG_TAG, "ForceRefreshConnections")
            val intent = Intent(context, BackgroundService::class.java)
            intent.putExtra("refresh", true)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}