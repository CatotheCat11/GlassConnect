/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.kdeconnect.Plugins.MousePadPlugin;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import com.cato.kdeconnect.NetworkPacket;
import com.cato.kdeconnect.Plugins.Plugin;
import com.cato.kdeconnect.Plugins.PluginFactory;
import com.cato.kdeconnect.UserInterface.PluginSettingsFragment;
import com.cato.kdeconnect.R;

import androidx.core.content.ContextCompat;

@PluginFactory.LoadablePlugin
public class MousePadPlugin extends Plugin {

    //public final static String PACKET_TYPE_MOUSEPAD = "kdeconnect.mousepad";
    public final static String PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request";
    private final static String PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE = "kdeconnect.mousepad.keyboardstate";

    private boolean keyboardEnabled = true;

    @Override
    public boolean onPacketReceived(NetworkPacket np) {

        keyboardEnabled = np.getBoolean("state", true);

        return true;
    }

    @Override
    public String getDisplayName() {
        return context.getString(R.string.pref_plugin_mousepad);
    }

    @Override
    public String getDescription() {
        return context.getString(R.string.pref_plugin_mousepad_desc);
    }

    @Override
    public Drawable getIcon() {
        return ContextCompat.getDrawable(context, R.drawable.touchpad_plugin_action_24dp);
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public PluginSettingsFragment getSettingsFragment(Activity activity) {
        return PluginSettingsFragment.newInstance(getPluginKey(), R.xml.mousepadplugin_preferences);
    }

    @Override
    public boolean hasMainActivity() {
        return true;
    }

    @Override
    public void startMainActivity(Activity parentActivity) {
        Intent intent = new Intent(parentActivity, MousePadActivity.class);
        intent.putExtra("deviceId", device.getDeviceId());
        parentActivity.startActivity(intent);
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_MOUSEPAD_REQUEST};
    }

    @Override
    public String getActionName() {
        return context.getString(R.string.open_mousepad);
    }

    public void sendMouseDelta(float dx, float dy) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("dx", dx);
        np.set("dy", dy);
        device.sendPacket(np);
    }

    public void sendLeftClick() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("singleclick", true);
        device.sendPacket(np);
    }

    public void sendDoubleClick() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("doubleclick", true);
        device.sendPacket(np);
    }

    public void sendMiddleClick() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("middleclick", true);
        device.sendPacket(np);
    }

    public void sendRightClick() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("rightclick", true);
        device.sendPacket(np);
    }

    public void sendSingleHold() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("singlehold", true);
        device.sendPacket(np);
    }

    public void sendScroll(float dx, float dy) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("scroll", true);
        np.set("dx", dx);
        np.set("dy", dy);
        device.sendPacket(np);
    }

    public void sendKeyboardPacket(NetworkPacket np) {
        device.sendPacket(np);
    }

    boolean isKeyboardEnabled() {
        return keyboardEnabled;
    }

}
