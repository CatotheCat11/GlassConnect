/*
 * SPDX-FileCopyrightText: 2015 Aleix Pol Gonzalez <aleixpol@kde.org>
 * SPDX-FileCopyrightText: 2015 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.kdeconnect.Plugins.RunCommandPlugin;
import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import org.json.JSONException;
import org.json.JSONObject;
import com.cato.kdeconnect.KdeConnect;
import com.google.android.glass.media.Sounds;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RunCommandActivity extends Activity {
    private CardScrollView mCardScrollView;
    private ExampleCardScrollAdapter mAdapter;
    private List<CardBuilder> mCards;
    private String deviceId;
    private final RunCommandPlugin.CommandsChangedCallback commandsChangedCallback = () -> runOnUiThread(this::updateCards);
    private List<CommandEntry> commandItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("runCommands", "onCreate");
        deviceId = getIntent().getStringExtra("deviceId");
        mCards = new ArrayList<CardBuilder>();
        mCards.add(new CardBuilder(this, CardBuilder.Layout.MENU)
                .setText("Loading..."));

        mCardScrollView = new CardScrollView(this);
        mAdapter = new ExampleCardScrollAdapter();
        mCardScrollView.setAdapter(mAdapter);
        setContentView(mCardScrollView);
        mCardScrollView.activate();
        updateCards();
        setupClickListener();
    }

    private void updateCards() {
        Log.d("runCommands", "loadCommands");

        RunCommandPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, RunCommandPlugin.class);
        if (plugin == null) {
            finish();
            return;
        }

        commandItems = new ArrayList<>();
        for (JSONObject obj : plugin.getCommandList()) {
            try {
                commandItems.add(new CommandEntry(obj));
            } catch (JSONException e) {
                Log.e("RunCommand", "Error parsing JSON", e);
            }
        }

        Collections.sort(commandItems, Comparator.comparing(CommandEntry::getName));
        mCards.clear();

        if (commandItems.isEmpty()) {
            Log.d("runCommands", "no commands");
            mCards.add(new CardBuilder(this, CardBuilder.Layout.MENU)
                    .setText("No commands registered")
                    .setFootnote("You can add commands on the desktop"));
        } else {
            for (CommandEntry entry : commandItems) {
                Log.d("runCommands", "add command" + entry.getName() + " " + entry.getCommand());
                CardBuilder card = new CardBuilder(this, CardBuilder.Layout.MENU)
                        .setText(entry.getName())
                        .setFootnote(entry.getCommand());
                mCards.add(card);
            }
        }

        mAdapter.notifyDataSetChanged();
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
        RunCommandPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, RunCommandPlugin.class);
        mCardScrollView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.playSoundEffect(Sounds.TAP);
                if (plugin != null) {
                    if (commandItems.isEmpty()) {
                        plugin.sendSetupPacket();
                    } else {
                        plugin.runCommand(commandItems.get(position).getKey());
                    }
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        RunCommandPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, RunCommandPlugin.class);
        if (plugin == null) {
            finish();
            return;
        }
        plugin.addCommandsUpdatedCallback(commandsChangedCallback);
    }

    @Override
    protected void onPause() {
        super.onPause();

        RunCommandPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, RunCommandPlugin.class);
        if (plugin == null) {
            return;
        }
        plugin.removeCommandsUpdatedCallback(commandsChangedCallback);
    }

}
