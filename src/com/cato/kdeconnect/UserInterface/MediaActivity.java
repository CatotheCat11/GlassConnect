/*
 * SPDX-FileCopyrightText: 2025 Cato the Cat
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.kdeconnect.UserInterface;

import static com.cato.kdeconnect.Plugins.MprisPlugin.MprisPlugin.PACKET_TYPE_MPRIS_REQUEST;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.cato.kdeconnect.BackgroundService;
import com.cato.kdeconnect.Device;
import com.cato.kdeconnect.LiveCardService;
import com.cato.kdeconnect.NetworkPacket;
import com.cato.kdeconnect.R;

import java.util.Collection;

public class MediaActivity extends Activity {
    MediaActivity instance;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
    }
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        openOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_media, menu);
        if (getIntent().getBooleanExtra("isPlaying", false)) {
            menu.removeItem(R.id.media_play);
        } else {
            menu.removeItem(R.id.media_pause);
        }

        if (!getIntent().getBooleanExtra("nextAllowed", false)) {
            menu.removeItem(R.id.media_next);
        }

        if (!getIntent().getBooleanExtra("prevAllowed", false)) {
            menu.removeItem(R.id.media_prev);
        }
        //TODO: implement shuffle and repeat
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection.
        if (item.getItemId() == R.id.media_stop) {
            sendCommand("action", "Stop");
            LiveCardService.mediaSessionStop();
            return true;
        } else if (item.getItemId() == R.id.media_play) {
            sendCommand("action", "Play");
            return true;
        } else if (item.getItemId() == R.id.media_pause) {
            sendCommand("action", "Pause");
            return true;
        } else if (item.getItemId() == R.id.media_next) {
            sendCommand("action", "Next");
            return true;
        } else if (item.getItemId() == R.id.media_prev) {
            sendCommand("action", "Previous");
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        // Nothing else to do, closing the activity.
        finish();
    }
    private void sendCommand(String method, String value) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MPRIS_REQUEST);
        np.set("player", getIntent().getStringExtra("player"));
        np.set(method, value);
        BackgroundService.RunCommand(getApplicationContext(), service -> {
            Collection<Device> devices = service.getDevices().values();
            for (Device device : devices) { //TODO: handle device not found
                if (device.getDeviceId().equals(getIntent().getStringExtra("device"))) {
                    device.sendPacket(np);
                }
            }
        });
    }
}
