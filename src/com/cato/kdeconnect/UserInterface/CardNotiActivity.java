/*
 * SPDX-FileCopyrightText: 2025 Cato the Cat
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.kdeconnect.UserInterface;

import android.app.Activity;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.cato.kdeconnect.BackgroundService;
import com.cato.kdeconnect.Device;
import com.cato.kdeconnect.KdeConnect;
import com.cato.kdeconnect.LiveCardService;
import com.cato.kdeconnect.NetworkPacket;
import com.cato.kdeconnect.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.List;

public class CardNotiActivity extends Activity {

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        openOptionsMenu();
    }
    private static final int SPEECH_REQUEST = 0;

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);
            Log.d("NotificationActivity", "Spoken text: " + spokenText);
            Collection<Device> devices = KdeConnect.getInstance().getDevices().values();
            for (Device device : devices) {
                try {
                    JSONObject notiObject = new JSONObject(getIntent().getStringExtra("notiObj"));
                    String deviceId = notiObject.getString("deviceId");
                    if (device.getDeviceId().equals(deviceId)) {
                        NetworkPacket np = new NetworkPacket(NetworkPacket.PACKET_TYPE_NOTIFICATION_REPLY);
                        np.set("requestReplyId", notiObject.getString("requestReplyId"));
                        np.set("message", formatText(spokenText));
                        device.sendPacket(np);
                        Log.d("NotificationActivity", "Sent reply with request reply ID" + notiObject.getString("requestReplyId"));
                        LiveCardService.notificationList.remove(0);
                        LiveCardService.postUpdate(false);
                        finish();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.noti_actions, menu);
        try {
            JSONObject notiObject = new JSONObject(getIntent().getStringExtra("notiObj"));
            if (notiObject.has("requestReplyId")) {
                if (notiObject.getString("requestReplyId").isEmpty()) { // TODO: unecessary check? test to see if ok to remove
                    menu.removeItem(R.id.menu_item_5);
                }
            } else {
                menu.removeItem(R.id.menu_item_5);
            }
            if (!notiObject.has("actions")) {
                menu.removeItem(R.id.menu_item_2);
                menu.removeItem(R.id.menu_item_3);
                menu.removeItem(R.id.menu_item_4);
            } else {
                JSONArray actions = new JSONArray(notiObject.getString("actions"));
                if (actions.length() == 1) {
                    menu.removeItem(R.id.menu_item_3);
                    menu.removeItem(R.id.menu_item_4);
                    menu.findItem(R.id.menu_item_2).setTitle(actions.getString(0));
                } else if (actions.length() == 2) {
                    menu.removeItem(R.id.menu_item_4);
                    menu.findItem(R.id.menu_item_2).setTitle(actions.getString(0));
                    menu.findItem(R.id.menu_item_3).setTitle(actions.getString(1));
                } else if (actions.length() == 3) {
                    menu.findItem(R.id.menu_item_2).setTitle(actions.getString(0));
                    menu.findItem(R.id.menu_item_3).setTitle(actions.getString(1));
                    menu.findItem(R.id.menu_item_4).setTitle(actions.getString(2));
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection.
        Integer selection = item.getItemId();
        if (selection == R.id.menu_item_1) { // Dismiss
            Log.d("NotificationActivity", "Dismiss selected");
            Collection<Device> devices = KdeConnect.getInstance().getDevices().values();
            for (Device device : devices) { //TODO: handle device not found
                try {
                    JSONObject notiObject = new JSONObject(getIntent().getStringExtra("notiObj"));
                    if (notiObject.has("deviceId")) {
                        if (device.getDeviceId().equals(notiObject.getString("deviceId"))) {
                            NetworkPacket np = new NetworkPacket(NetworkPacket.PACKET_TYPE_NOTIFICATION_REPLY);
                            np.set("cancel", notiObject.getString("key"));
                            device.sendPacket(np);
                        }
                    } else {
                        Log.w("NotificationActivity", "No device ID found for notification, cannot dismiss remote noti");
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            LiveCardService.notificationList.remove(0);
            LiveCardService.postUpdate(false);
            finish();
            return true;
        } else if (selection == R.id.menu_item_5) { // Reply
            Log.d("NotificationActivity", "Reply selected");
            displaySpeechRecognizer();
            return true;
        } else {
            Collection<Device> devices = KdeConnect.getInstance().getDevices().values();
            for (Device device : devices) { //TODO: handle device not found
                try {
                    JSONObject notiObject = new JSONObject(getIntent().getStringExtra("notiObj"));
                    if (device.getDeviceId().equals(notiObject.getString("deviceId"))) {
                        NetworkPacket np = new NetworkPacket(NetworkPacket.PACKET_TYPE_NOTIFICATION_ACTION);
                        np.set("key", notiObject.getString("key"));
                        np.set("action", (String) item.getTitle()); //will run the first action
                        device.sendPacket(np);
                        LiveCardService.notificationList.remove(0);
                        LiveCardService.postUpdate(false);
                        finish();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return true;
        }
    }
    private void displaySpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        startActivityForResult(intent, SPEECH_REQUEST);
    }

    public static String formatText(String input) {
        input = input.trim();
        if (input.isEmpty()) return input;

        // Capitalize the first character and append the rest of the text
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
}
