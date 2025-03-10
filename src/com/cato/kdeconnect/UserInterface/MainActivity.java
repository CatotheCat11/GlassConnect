/*
 * SPDX-FileCopyrightText: 2025 Cato the Cat
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.kdeconnect.UserInterface;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.content.Context;
import android.widget.AdapterView;

import com.cato.kdeconnect.BackgroundService;
import com.cato.kdeconnect.Device;
import com.cato.kdeconnect.Helpers.DeviceHelper;
import com.cato.kdeconnect.LiveCardService;
import com.cato.kdeconnect.NetworkPacket;
import com.cato.kdeconnect.Plugins.FindMyPhonePlugin.FindMyPhonePlugin;
import com.cato.kdeconnect.R;
import com.google.android.glass.media.Sounds;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

public class MainActivity extends Activity {

    public static final String EXTRA_DEVICE_ID = "deviceId";

    public static final int RESULT_NEEDS_RELOAD = Activity.RESULT_FIRST_USER;

    public static final String PAIR_REQUEST_STATUS = "pair_req_status";
    public static final String PAIRING_ACCEPTED = "accepted";
    public static final String PAIRING_REJECTED = "rejected";
    public static final String PAIRING_PENDING = "pending";

    private CardScrollView mCardScrollView;
    private ExampleCardScrollAdapter mAdapter;
    private List<CardBuilder> mCards;
    private List<Device> mDevices;
    Device selectedDevice = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("DebugCato", "onCreate");
        startService(new Intent(this, LiveCardService.class));
        mCards = new ArrayList<CardBuilder>();
        mDevices = new ArrayList<Device>();
        mCards.add(new CardBuilder(this, CardBuilder.Layout.MENU)
                .setText("Loading..."));

        // Initialize the CardScrollView and set its adapter.
        mCardScrollView = new CardScrollView(this);
        mAdapter = new ExampleCardScrollAdapter();
        mCardScrollView.setAdapter(mAdapter);
        setContentView(mCardScrollView);
        setupClickListener();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("DebugCato", "onStart");
        mCardScrollView.activate();
        BackgroundService.RunCommand(this, service -> {
            service.onNetworkChange();
            service.addDeviceListChangedCallback("MainActivity", this::updateDeviceList);
        });
        updateDeviceList();
    }

    @Override
    protected void onStop() {
        Log.d("DebugCato", "onStop");
        mCardScrollView.deactivate();
        BackgroundService.RunCommand(this, service -> service.removeDeviceListChangedCallback("MainActivity"));
        super.onStop();
    }

    /**
     * Updates the list of devices by querying the BackgroundService.
     * Only devices that are reachable and paired are shown.
     */
    private void updateDeviceList() {
        Log.d("DebugCato", "Updating device list");
        int position = mCardScrollView.getSelectedItemPosition();
        mCards.clear();
        mDevices.clear();
        mCards.add(new CardBuilder(this, CardBuilder.Layout.MENU)
                .setText("Refresh")
                .setFootnote("Visible as " + DeviceHelper.getDeviceName(this)));
        BackgroundService.RunCommand(this, service -> {
            Collection<Device> devices = service.getDevices().values();
            for (Device device : devices) {
                if (device.isReachable() && !device.isPaired()) {
                    mDevices.add(device);
                    CardBuilder card = new CardBuilder(this, CardBuilder.Layout.MENU)
                            .setText(device.getName())
                            .setIcon(device.getIcon());
                    if (device.isPairRequestedByPeer()) {
                        card.setFootnote("Pair requested \uD83D\uDD11 " + device.getVerificationKey());
                    } else if (device.isPairRequested()) {
                        card.setFootnote("Pairing request sent \uD83D\uDD11 " + device.getVerificationKey());
                    } else {
                        card.setFootnote("Tap to pair");
                    }
                    mCards.add(card);
                    Log.d("DebugCato", "Adding device " + device.getName());
                }
                if (device.isPaired()) {
                    mDevices.add(device);
                    CardBuilder card = new CardBuilder(this, CardBuilder.Layout.MENU)
                            .setText(device.getName())
                            .setIcon(device.getIcon());
                    card.setFootnote((device.isReachable() ? "Connected | " : "Not connected | " ) + "Tap for options");
                    mCards.add(card);
                    Log.d("DebugCato", "Adding device " + device.getName());
                }
            }
            runOnUiThread(() -> {
                mAdapter.notifyDataSetChanged();
            });
            if (mCards.size() - 1 >= position) {
                mCardScrollView.setSelection(position);
            } else {
                mCardScrollView.setSelection(mCards.size() - -1);
            }
        });
    }

    private class ExampleCardScrollAdapter extends CardScrollAdapter {

        @Override
        public int getPosition(Object item) {
            return mCards.indexOf(item);
        }

        @Override
        public int getCount() {
            return mCards.size();
        }

        @Override
        public Object getItem(int position) {
            return mCards.get(position);
        }

        @Override
        public int getViewTypeCount() {
            return CardBuilder.getViewTypeCount();
        }

        @Override
        public int getItemViewType(int position){
            return mCards.get(position).getItemViewType();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return mCards.get(position).getView(convertView, parent);
        }
        // Inserts a card into the adapter, without notifying.
        public void insertCardWithoutNotification(int position, CardBuilder card) {
            mCards.add(position, card);
        }
    }
    private void setupClickListener() {
        mCardScrollView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.playSoundEffect(Sounds.TAP);
                if (position == 0) {
                    updateDeviceList();
                } else {
                    Device device = mDevices.get(position - 1);
                    if (device.isPairRequestedByPeer()) {
                        device.acceptPairing();
                    } else if (!device.isPaired()) {
                        device.requestPairing();
                        mCards.get(position).setFootnote("Pairing request sent \uD83D\uDD11 " + device.getVerificationKey());
                        mAdapter.notifyDataSetChanged();
                    } else if (device.isPaired()) {
                        selectedDevice = device;
                        openOptionsMenu();
                    }
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_device, menu);
        if (!selectedDevice.isPluginEnabled("FindRemoteDevicePlugin") || !selectedDevice.isReachable()) {
            menu.removeItem(R.id.ring);
        }
        if (!selectedDevice.isPluginEnabled("MousePadPlugin") || !selectedDevice.isReachable()) {
            menu.removeItem(R.id.mousepad);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection.
        Integer selection = item.getItemId();
        if (selection == R.id.ring && selectedDevice.isReachable()) {
            selectedDevice.sendPacket(new NetworkPacket(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST));
        } else if (selection == R.id.mousepad && selectedDevice.isReachable()) {
            selectedDevice.getPlugin("MousePadPlugin").startMainActivity(this);
        } else if (selection == R.id.unpair) {
            selectedDevice.unpair();
            updateDeviceList();
        }
        return true;
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        selectedDevice = null;
    }
}
