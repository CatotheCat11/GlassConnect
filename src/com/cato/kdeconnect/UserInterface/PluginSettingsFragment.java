/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.kdeconnect.UserInterface;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.cato.kdeconnect.BackgroundService;
import com.cato.kdeconnect.Device;
import com.cato.kdeconnect.KdeConnect;
import com.cato.kdeconnect.Plugins.Plugin;
import com.cato.kdeconnect.Plugins.PluginFactory;
import com.cato.kdeconnect.R;

public class PluginSettingsFragment extends PreferenceFragmentCompat {
    private static final String ARG_PLUGIN_KEY = "plugin_key";
    private static final String ARG_LAYOUT = "layout";

    private String pluginKey;
    private int layout;
    protected Device device;
    protected Plugin plugin;

    public static PluginSettingsFragment newInstance(@NonNull String pluginKey, int settingsLayout) {
        PluginSettingsFragment fragment = new PluginSettingsFragment();
        fragment.setArguments(pluginKey, settingsLayout);

        return fragment;
    }

    public PluginSettingsFragment() {}

    protected Bundle setArguments(@NonNull String pluginKey, int settingsLayout) {
        Bundle args = new Bundle();
        args.putString(ARG_PLUGIN_KEY, pluginKey);
        args.putInt(ARG_LAYOUT, settingsLayout);


        setArguments(args);

        return args;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (getArguments() == null || !getArguments().containsKey(ARG_PLUGIN_KEY)) {
            throw new RuntimeException("You must provide a pluginKey by calling setArguments(@NonNull String pluginKey)");
        }

        this.pluginKey = getArguments().getString(ARG_PLUGIN_KEY);
        this.layout = getArguments().getInt(ARG_LAYOUT);
        this.device = getDeviceOrThrow(getDeviceId());
        this.plugin = device.getPlugin(pluginKey);

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (this.plugin != null && this.plugin.supportsDeviceSpecificSettings()) {
            PreferenceManager prefsManager = getPreferenceManager();
            prefsManager.setSharedPreferencesName(this.plugin.getSharedPreferencesName());
            prefsManager.setSharedPreferencesMode(Context.MODE_PRIVATE);
        }

        addPreferencesFromResource(layout);
    }

    @Override
    public void onResume() {
        super.onResume();

        PluginFactory.PluginInfo info = PluginFactory.getPluginInfo(pluginKey);
        requireActivity().setTitle(getString(R.string.plugin_settings_with_name, info.getDisplayName()));
    }

    public String getDeviceId() {
        return ((PluginSettingsActivity)requireActivity()).getSettingsDeviceId();
    }

    private Device getDeviceOrThrow(String deviceId) {
        Device device = KdeConnect.getInstance().getDevice(deviceId);

        if (device == null) {
            throw new RuntimeException("PluginSettingsFragment.onCreatePreferences() - No device with id " + getDeviceId());
        }

        return device;
    }
}
