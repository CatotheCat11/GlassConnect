/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package com.cato.kdeconnect.Backends.LoopbackBackend;

import android.content.Context;

import com.cato.kdeconnect.Backends.BaseLink;
import com.cato.kdeconnect.Backends.BaseLinkProvider;
import com.cato.kdeconnect.Backends.BasePairingHandler;
import com.cato.kdeconnect.Device;
import com.cato.kdeconnect.NetworkPacket;

import androidx.annotation.WorkerThread;

public class LoopbackLink extends BaseLink {

    public LoopbackLink(Context context, BaseLinkProvider linkProvider) {
        super(context, "loopback", linkProvider);
    }

    @Override
    public String getName() {
        return "LoopbackLink";
    }

    @Override
    public BasePairingHandler getPairingHandler(Device device, BasePairingHandler.PairingHandlerCallback callback) {
        return new LoopbackPairingHandler(device, callback);
    }

    @WorkerThread
    @Override
    public boolean sendPacket(NetworkPacket in, Device.SendPacketStatusCallback callback) {
        packetReceived(in);
        if (in.hasPayload()) {
            callback.onProgressChanged(0);
            in.setPayload(in.getPayload());
            callback.onProgressChanged(100);
        }
        callback.onSuccess();
        return true;
    }

}
