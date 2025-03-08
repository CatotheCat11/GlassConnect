/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package com.cato.kdeconnect.Backends.LoopbackBackend;

import android.util.Log;

import com.cato.kdeconnect.Backends.BasePairingHandler;
import com.cato.kdeconnect.Device;
import com.cato.kdeconnect.NetworkPacket;

public class LoopbackPairingHandler extends BasePairingHandler {

    public LoopbackPairingHandler(Device device, PairingHandlerCallback callback) {
        super(device, callback);
    }

    @Override
    public void packetReceived(NetworkPacket np) {

    }

    @Override
    public void requestPairing() {
        Log.i("LoopbackPairing", "requestPairing");
        mCallback.pairingDone();
    }

    @Override
    public void acceptPairing() {
        Log.i("LoopbackPairing", "acceptPairing");
        mCallback.pairingDone();
    }

    @Override
    public void rejectPairing() {
        Log.i("LoopbackPairing", "rejectPairing");
        mCallback.unpaired();
    }

    @Override
    public void unpair() {
        Log.i("LoopbackPairing", "unpair");
        mCallback.unpaired();
    }

}
