/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.connect.Plugins.FindRemoteDevicePlugin;

import android.app.Activity;

import org.apache.commons.lang3.ArrayUtils;
import com.cato.connect.NetworkPacket;
import com.cato.connect.Plugins.FindMyPhonePlugin.FindMyPhonePlugin;
import com.cato.connect.Plugins.Plugin;
import com.cato.connect.Plugins.PluginFactory;
import com.cato.connect.R;

@PluginFactory.LoadablePlugin
public class FindRemoteDevicePlugin extends Plugin {

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_findremotedevice);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_findremotedevice_desc);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {
        return true;
    }

    @Override
    public String getActionName() {
        return context.getString(R.string.ring);
    }

    @Override
    public void startMainActivity(Activity activity) {
        if (device != null) {
            device.sendPacket(new NetworkPacket(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST));
        }
    }

    @Override
    public boolean hasMainActivity() {
        return true;
    }

    @Override
    public boolean displayInContextMenu() {
        return true;
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return ArrayUtils.EMPTY_STRING_ARRAY;
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST};
    }
}
