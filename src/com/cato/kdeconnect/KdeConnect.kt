/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package com.cato.kdeconnect

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import com.cato.kdeconnect.Backends.BaseLink
import com.cato.kdeconnect.Backends.BaseLinkProvider.ConnectionReceiver
import com.cato.kdeconnect.BackgroundService.Companion.UPDATE_IMMUTABLE_FLAGS
import com.cato.kdeconnect.Helpers.DeviceHelper
import com.cato.kdeconnect.Helpers.LifecycleHelper
import com.cato.kdeconnect.Helpers.NotificationHelper
import com.cato.kdeconnect.Helpers.SecurityHelpers.RsaHelper
import com.cato.kdeconnect.Helpers.SecurityHelpers.SslHelper
import com.cato.kdeconnect.PairingHandler.PairingCallback
import com.cato.kdeconnect.Plugins.Plugin
import com.cato.kdeconnect.Plugins.PluginFactory
import com.cato.kdeconnect.UserInterface.ThemeUtil
import com.cato.kdeconnect.BuildConfig
import com.cato.kdeconnect.UserInterface.MainActivity
//import org.slf4j.impl.HandroidLoggerAdapter
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/*
 * This class holds all the active devices and makes them accessible from every other class.
 * It also takes care of initializing all classes that need so when the app boots.
 * It provides a ConnectionReceiver that the BackgroundService uses to ping this class every time a new DeviceLink is created.
 */
class KdeConnect: Service() {

    fun interface DeviceListChangedCallback {
        fun onDeviceListChanged()
    }



    val devices: ConcurrentHashMap<String, Device> = ConcurrentHashMap()

    private val deviceListChangedCallbacks = ConcurrentHashMap<String, DeviceListChangedCallback>()

    override fun onCreate() {
        super.onCreate()
        appinstance = this
        //setupSL4JLogging()
        Log.d("KdeConnect/Application", "onCreate")
        //ThemeUtil.setUserPreferredTheme(this)
        DeviceHelper.initializeDeviceId(this)
        RsaHelper.initialiseRsaKeys(this)
        SslHelper.initialiseCertificate(this)
        PluginFactory.initPluginInfo(this)
        NotificationHelper.initializeChannels(this)
        LifecycleHelper.initializeObserver()
        loadRememberedDevicesFromSettings()
        if (BackgroundService.instance == null) {
            BackgroundService.Start(this)
        }
        if (LiveCardService.sInstance == null) {
            LiveCardService.start(this)
        }
        initialized = true
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("KdeConnect/Application", "onStartCommand")
        startForeground(2, createForegroundNotification())
        return START_STICKY
    }

    private fun createForegroundNotification(): Notification {

        val intent = Intent(this, MainActivity::class.java)

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

        notification.setContentText(getString(R.string.foreground_notification_no_devices))
        return notification.build()
    }

    // Local binder instance
    private val binder = LocalBinder()

    // Inner class that extends Binder
    inner class LocalBinder : Binder() {
        fun getService(): KdeConnect = this@KdeConnect
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    /*private fun setupSL4JLogging() {
        HandroidLoggerAdapter.DEBUG = BuildConfig.DEBUG
        HandroidLoggerAdapter.ANDROID_API_LEVEL = Build.VERSION.SDK_INT
        HandroidLoggerAdapter.APP_NAME = "KDEConnect"
    }*/

    fun addDeviceListChangedCallback(key: String, callback: DeviceListChangedCallback) {
        deviceListChangedCallbacks[key] = callback
    }

    fun removeDeviceListChangedCallback(key: String) {
        deviceListChangedCallbacks.remove(key)
    }

    private fun onDeviceListChanged() {
        Log.i("MainActivity", "Device list changed, notifying ${deviceListChangedCallbacks.size} observers.")
        deviceListChangedCallbacks.values.forEach(DeviceListChangedCallback::onDeviceListChanged)
    }

    fun getDevice(id: String?): Device? {
        if (id == null) {
            return null
        }
        return devices[id]
    }

    fun <T : Plugin> getDevicePlugin(deviceId: String?, pluginClass: Class<T>): T? {
        val device = getDevice(deviceId)
        return device?.getPlugin(pluginClass)
    }

    private fun loadRememberedDevicesFromSettings() {
        // Log.e("BackgroundService", "Loading remembered trusted devices")
        val preferences = getSharedPreferences("trusted_devices", MODE_PRIVATE)
        val trustedDevices: Set<String> = preferences.all.keys
        trustedDevices.map { id ->
            Log.d("KdeConnect", "Loading device $id")
            id
        }.filter { preferences.getBoolean(it, false) }.forEach {
            try {
                val device = Device(applicationContext, it)
                val now = Date()
                val x509Cert = device.certificate as X509Certificate
                if(now < x509Cert.notBefore) {
                    throw CertificateException("Certificate not effective yet: "+x509Cert.notBefore)
                }
                else if(now > x509Cert.notAfter) {
                    throw CertificateException("Certificate already expired: "+x509Cert.notAfter)
                }
                devices[it] = device
                device.addPairingCallback(devicePairingCallback)
            } catch (e: CertificateException) {
                Log.w(
                    "KdeConnect",
                    "Couldn't load the certificate for a remembered device. Removing from trusted list.", e
                )
                preferences.edit().remove(it).apply()
            }
        }
    }
    fun removeRememberedDevices() {
        // Log.e("BackgroundService", "Removing remembered trusted devices")
        val preferences = getSharedPreferences("trusted_devices", MODE_PRIVATE)
        val trustedDevices: Set<String> = preferences.all.keys
        trustedDevices.filter { preferences.getBoolean(it, false) }
            .forEach {
                Log.d("KdeConnect", "Removing devices: $it")
                preferences.edit().remove(it).apply()
            }
    }

    private val devicePairingCallback: PairingCallback = object : PairingCallback {
        override fun incomingPairRequest() {
            onDeviceListChanged()
        }

        override fun pairingSuccessful() {
            onDeviceListChanged()
        }

        override fun pairingFailed(error: String) {
            onDeviceListChanged()
        }

        override fun unpaired() {
            onDeviceListChanged()
        }
    }

    val connectionListener: ConnectionReceiver = object : ConnectionReceiver {
        override fun onConnectionReceived(link: BaseLink) {
            var device = devices[link.deviceId]
            if (device != null) {
                device.addLink(link)
            } else {
                device = Device(this@KdeConnect, link)
                devices[link.deviceId] = device
                device.addPairingCallback(devicePairingCallback)
            }
            onDeviceListChanged()
        }

        override fun onConnectionLost(link: BaseLink) {
            val device = devices[link.deviceId]
            Log.i("KDE/onConnectionLost", "removeLink, deviceId: ${link.deviceId}")
            if (device != null) {
                device.removeLink(link)
                // FIXME: I commented out the code below that removes the Device from the `devices` array
                //        because it didn't succeed in getting the Device garbage collected anyway. Ideally,
                //        the `devices` array should be the only reference to each Device so that they get
                //        GC'd after removing them from here, but there seem to be references leaking from
                //        PairingFragment and PairingHandler that keep it alive. At least now, by keeping
                //        them in `devices`, we reuse the same Device instance across discoveries of the
                //        same device instead of creating a new object each time.
                //        Also, if we ever fix this, there are two cases were we should be removing devices:
                //            - When a device becomes unreachable, if it's unpaired (this case here)
                //            - When a device becomes unpaired, if it's unreachable (not implemented ATM)
                // if (!device.isReachable && !device.isPaired) {
                //     Log.i("onConnectionLost","Removing device because it was not paired")
                //     devices.remove(link.deviceId)
                //     device.removePairingCallback(devicePairingCallback)
                // }
            } else {
                Log.d("KDE/onConnectionLost", "Removing connection to unknown device")
            }
            onDeviceListChanged()
        }

        override fun onDeviceInfoUpdated(deviceInfo: DeviceInfo) {
            val device = devices[deviceInfo.id]
            if (device == null) {
                Log.e("KdeConnect", "onDeviceInfoUpdated for an unknown device")
                return
            }
            val hasChanges = device.updateDeviceInfo(deviceInfo)
            if (hasChanges) {
                onDeviceListChanged()
            }
        }
    }

    companion object {
        @JvmStatic
        var appinstance: KdeConnect? = null
            private set

        private var initialized = false

        @JvmStatic
        fun isInitialized(): Boolean = initialized

        @JvmStatic
        fun getInstance(): KdeConnect = appinstance ?: throw IllegalStateException("KdeConnect instance is null")

        fun Start(context: Context) {
            Log.d("KdeConnect/Application", "Start")
            val intent = Intent(context, KdeConnect::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}