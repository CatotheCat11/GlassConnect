/*
 * SPDX-FileCopyrightText: 2015 Aleix Pol Gonzalez <aleixpol@kde.org>
 * SPDX-FileCopyrightText: 2015 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package com.cato.kdeconnect.Plugins.RunCommandPlugin;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.apache.commons.collections4.iterators.IteratorIterable;
import org.json.JSONException;
import org.json.JSONObject;
import com.cato.kdeconnect.NetworkPacket;
import com.cato.kdeconnect.Plugins.Plugin;
import com.cato.kdeconnect.Plugins.PluginFactory;
import com.cato.kdeconnect.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

@PluginFactory.LoadablePlugin
public class RunCommandPlugin extends Plugin {

    private final static String PACKET_TYPE_RUNCOMMAND = "kdeconnect.runcommand";
    private final static String PACKET_TYPE_RUNCOMMAND_REQUEST = "kdeconnect.runcommand.request";
    public final static String KEY_COMMANDS_PREFERENCE = "commands_preference_";

    private final ArrayList<JSONObject> commandList = new ArrayList<>();
    private final ArrayList<CommandsChangedCallback> callbacks = new ArrayList<>();
    private final ArrayList<CommandEntry> commandItems = new ArrayList<>();

    private SharedPreferences sharedPreferences;

    private boolean canAddCommand;

    public void addCommandsUpdatedCallback(CommandsChangedCallback newCallback) {
        callbacks.add(newCallback);
    }

    public void removeCommandsUpdatedCallback(CommandsChangedCallback theCallback) {
        callbacks.remove(theCallback);
    }

    interface CommandsChangedCallback {
        void update();
    }

    public ArrayList<JSONObject> getCommandList() {
        return commandList;
    }

    public ArrayList<CommandEntry> getCommandItems() {
        return commandItems;
    }

    @Override
    public @NonNull String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_runcommand);
    }

    @Override
    public @NonNull String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_runcommand_desc);
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public Drawable getIcon() {
        return ContextCompat.getDrawable(context, R.drawable.run_command_plugin_icon_24dp);
    }

    @Override
    public boolean onCreate() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        requestCommandList();
        return true;
    }

    @Override
    public boolean onPacketReceived(@NonNull NetworkPacket np) {

        if (np.has("commandList")) {
            commandList.clear();
            try {
                commandItems.clear();
                JSONObject obj = new JSONObject(np.getString("commandList"));
                for (String s : new IteratorIterable<>(obj.keys())) {
                    JSONObject o = obj.getJSONObject(s);
                    o.put("key", s);
                    commandList.add(o);

                    try {
                        commandItems.add(
                                new CommandEntry(o)
                        );
                    } catch (JSONException e) {
                        Log.e("RunCommand", "Error parsing JSON", e);
                    }
                }

                Collections.sort(commandItems, Comparator.comparing(CommandEntry::getName));

            } catch (JSONException e) {
                Log.e("RunCommand", "Error parsing JSON", e);
            }

            for (CommandsChangedCallback aCallback : callbacks) {
                aCallback.update();
            }

            device.onPluginsChanged();

            canAddCommand = np.getBoolean("canAddCommand", false);

            return true;
        }
        return false;
    }

    @Override
    public @NonNull String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_RUNCOMMAND};
    }

    @Override
    public @NonNull String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_RUNCOMMAND_REQUEST};
    }

    public void runCommand(String cmdKey) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST);
        np.set("key", cmdKey);
        device.sendPacket(np);
    }

    private void requestCommandList() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST);
        np.set("requestCommandList", true);
        device.sendPacket(np);
    }

    @Override
    public void startMainActivity(Activity parentActivity) {
        Intent intent = new Intent(parentActivity, RunCommandActivity.class);
        intent.putExtra("deviceId", device.getDeviceId());
        parentActivity.startActivity(intent);
    }

    @Override
    public @NonNull String getActionName() {
        return context.getString(R.string.pref_plugin_runcommand);
    }

    public boolean canAddCommand() {
        return canAddCommand;
    }

    void sendSetupPacket() {
        NetworkPacket np = new NetworkPacket(RunCommandPlugin.PACKET_TYPE_RUNCOMMAND_REQUEST);
        np.set("setup", true);
        device.sendPacket(np);
    }

}
