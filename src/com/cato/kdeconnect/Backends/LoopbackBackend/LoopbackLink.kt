/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package com.cato.kdeconnect.Backends.LoopbackBackend

import android.content.Context
import androidx.annotation.WorkerThread
import com.cato.kdeconnect.Backends.BaseLink
import com.cato.kdeconnect.Backends.BaseLinkProvider
import com.cato.kdeconnect.Device
import com.cato.kdeconnect.DeviceInfo
import com.cato.kdeconnect.Helpers.DeviceHelper.getDeviceInfo
import com.cato.kdeconnect.NetworkPacket

class LoopbackLink : BaseLink {
    constructor(context: Context, linkProvider: BaseLinkProvider) : super(context, linkProvider)

    override fun getName(): String = "LoopbackLink"
    override fun getDeviceInfo(): DeviceInfo = getDeviceInfo(context)

    @WorkerThread
    override fun sendPacket(packet: NetworkPacket, callback: Device.SendPacketStatusCallback, sendPayloadFromSameThread: Boolean): Boolean {
        packetReceived(packet)
        if (packet.hasPayload()) {
            callback.onPayloadProgressChanged(0)
            packet.payload = packet.payload // this triggers logic in the setter
            callback.onPayloadProgressChanged(100)
        }
        callback.onSuccess()
        return true
    }
}