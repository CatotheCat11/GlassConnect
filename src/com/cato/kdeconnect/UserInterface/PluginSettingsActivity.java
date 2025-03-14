/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.kdeconnect.UserInterface;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import com.cato.kdeconnect.Plugins.Plugin;
import com.cato.kdeconnect.R;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import java.util.Objects;

public class PluginSettingsActivity
        extends AppCompatActivity
        implements PluginPreference.PluginPreferenceCallback {

    public static final String EXTRA_DEVICE_ID = "deviceId";
    public static final String EXTRA_PLUGIN_KEY = "pluginKey";

    //TODO: Save/restore state
    static private String deviceId; //Static because if we get here by using the back button in the action bar, the extra deviceId will not be set.

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d("PluginSettingsActivity", "onCreate");
        ThemeUtil.setUserPreferredTheme(this);
        super.onCreate(savedInstanceState);

        //setContentView(R.layout.activity_plugin_settings);

        //setSupportActionBar(findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        String pluginKey = null;

        if (getIntent().hasExtra(EXTRA_DEVICE_ID)) {
            deviceId = getIntent().getStringExtra(EXTRA_DEVICE_ID);

            if (getIntent().hasExtra(EXTRA_PLUGIN_KEY)) {
                pluginKey = getIntent().getStringExtra(EXTRA_PLUGIN_KEY);
            }
        } else if (deviceId == null) {
            throw new RuntimeException("You must start DeviceSettingActivity using an intent that has a " + EXTRA_DEVICE_ID + " extra");
        }

        /*Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentPlaceHolder);
        if (fragment == null) {
            if (pluginKey != null) {
                Device device = BackgroundService.getInstance().getDevice(deviceId);
                if (device != null) {
                    Plugin plugin = device.getPlugin(pluginKey);
                    if (plugin != null) {
                        fragment = plugin.getSettingsFragment(this);
                    }
                }
            }
            if (fragment == null) {
                fragment = PluginSettingsListFragment.newInstance(deviceId);
            }

            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragmentPlaceHolder, fragment)
                    .commit();
        }*/
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            FragmentManager fm = getSupportFragmentManager();

            if (fm.getBackStackEntryCount() > 0) {
                fm.popBackStack();
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onStartPluginSettingsFragment(Plugin plugin) {
        setTitle(getString(R.string.plugin_settings_with_name, plugin.getDisplayName()));

        PluginSettingsFragment fragment = plugin.getSettingsFragment(this);

        //TODO: Remove when NotificationFilterActivity has been turned into a PluginSettingsFragment
        if (fragment == null) {
            return;
        }

        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right)
                //.replace(R.id.fragmentPlaceHolder, fragment)
                .addToBackStack(null)
                .commit();

    }

    @Override
    public boolean onSupportNavigateUp() {
        super.onBackPressed();
        return true;
    }

    @Override
    public void onFinish() {
        finish();
    }

    public String getSettingsDeviceId() {
        return deviceId;
    }
}
