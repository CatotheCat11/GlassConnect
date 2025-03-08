/*
 * SPDX-FileCopyrightText: 2025 Cato the Cat
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.kdeconnect.UserInterface;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.speech.RecognizerIntent;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;

import com.cato.kdeconnect.BackgroundService;
import com.cato.kdeconnect.Device;
import com.cato.kdeconnect.LiveCardService;
import com.cato.kdeconnect.NetworkPacket;
import com.cato.kdeconnect.R;
import com.google.android.glass.media.Sounds;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

//TODO: implement notification dismissal from phone

public class NotificationActivity extends Activity {
    private List<CardBuilder> mCards;
    private CardScrollView mCardScrollView;
    private ExampleCardScrollAdapter mAdapter;
    private JSONArray notiList;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Acquire the wake lock
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "MyApp::MyWakelockTag");
        wakeLock.acquire(10*1000L /*10 seconds*/);

        addNotifications();

        mCardScrollView = new CardScrollView(this);
        mAdapter = new ExampleCardScrollAdapter();
        mCardScrollView.setAdapter(mAdapter);
        mCardScrollView.activate();
        setContentView(mCardScrollView);
        setupClickListener();

        if (getIntent().getIntExtra("lastNoti", -1) != -1) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.playSoundEffect(Sounds.SELECTED);
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            int negativeHeight = -displayMetrics.heightPixels;
            mCardScrollView.setVisibility(View.VISIBLE);
            mCardScrollView.setTranslationY((float) negativeHeight);
            mCardScrollView.setAlpha(1.0f);
            mCardScrollView.animate()
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator())
                    .translationYBy((float) displayMetrics.heightPixels);
        }
    }

    private void addNotifications() {
        mCards = new ArrayList<CardBuilder>();
        if (getIntent().hasExtra("notiList")) {
            try {
                notiList = new JSONArray(getIntent().getStringExtra("notiList"));
                for (int i = notiList.length() - 1; i >= 0; i--) { //Reverse order to show latest notification first
                    JSONObject noti = notiList.getJSONObject(i);
                    Log.d("NotificationActivity", "Adding notification: " + noti.toString());
                    CardBuilder card = new CardBuilder(this, CardBuilder.Layout.AUTHOR)
                            .setHeading(noti.getString("title"))
                            .setSubheading(noti.getString("appName"))
                            .setText(noti.getString("text"))
                            //Show available actions
                            .setFootnote(noti.has("actions") ? "Actions: " + noti.getString("actions") : "")
                            .setTimestamp(getTimeAgo(noti.optLong("time", 0)));
                    if (noti.has("icon")) {
                        card.setIcon(stringToBitmap(noti.getString("icon")));
                    }
                    mCards.add(card);
                }

            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        } else {
            Log.w("NotificationActivity", "Notifications activity started without any notifications");
            finish();
        }
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
    }

    public String getTimeAgo(long time) {
        if (time == 0) {
            return "";
        }
        long now = System.currentTimeMillis();
        long diff = now - time;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) {
            return days + " days ago";
        } else if (hours > 0) {
            return hours + " hours ago";
        } else if (minutes > 0) {
            return minutes + " minutes ago";
        } else {
            return seconds + " seconds ago";
        }
    }
    /**
     * @param encodedString
     * @return bitmap (from given string)
     */
    public Bitmap stringToBitmap(String encodedString){
        try {
            byte [] encodeByte=Base64.decode(encodedString,Base64.DEFAULT);
            Bitmap bitmap=BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.length);
            return bitmap;
        } catch(Exception e) {
            e.getMessage();
            return null;
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release the wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
    private void setupClickListener() {
        mCardScrollView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.playSoundEffect(Sounds.TAP);
                openOptionsMenu();
                /* the following code shows how action can be run
                BackgroundService.RunCommand(getApplicationContext(), service -> {
                            Collection<Device> devices = service.getDevices().values();
                            for (Device device : devices) {
                                try {
                                    JSONObject notiObject = notiList.getJSONObject(notiList.length() - (1 + position));
                                    if (device.getDeviceId().equals(notiObject.getString("deviceId"))) {
                                        NetworkPacket np = new NetworkPacket(NetworkPacket.PACKET_TYPE_NOTIFICATION_ACTION);
                                        np.set("key", notiObject.getString("key"));
                                        np.set("action", new JSONArray(notiObject.getString("actions")).getString(0)); //will run the first action
                                        device.sendPacket(np);
                                    }
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                        */
            }
        });
    }

    //Notification selection menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.noti_actions, menu);
        try {
            JSONObject notiObject = notiList.getJSONObject(notiList.length() - (1 + mCardScrollView.getSelectedItemPosition()));
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
            BackgroundService.RunCommand(getApplicationContext(), service -> {
                Collection<Device> devices = service.getDevices().values();
                for (Device device : devices) { //TODO: handle device not found
                    try {
                        JSONObject notiObject = notiList.getJSONObject(notiList.length() - (1 + mCardScrollView.getSelectedItemPosition()));
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
            });
            removeSelectedNoti();
            return true;
        } else if (selection == R.id.menu_item_5) { // Reply
            Log.d("NotificationActivity", "Reply selected");
            displaySpeechRecognizer();
            return true;
        } else {
            BackgroundService.RunCommand(getApplicationContext(), service -> {
                Collection<Device> devices = service.getDevices().values();
                for (Device device : devices) { //TODO: handle device not found
                    try {
                        JSONObject notiObject = notiList.getJSONObject(notiList.length() - (1 + mCardScrollView.getSelectedItemPosition()));
                        if (device.getDeviceId().equals(notiObject.getString("deviceId"))) {
                            NetworkPacket np = new NetworkPacket(NetworkPacket.PACKET_TYPE_NOTIFICATION_ACTION);
                            np.set("key", notiObject.getString("key"));
                            np.set("action", (String) item.getTitle()); //will run the first action
                            device.sendPacket(np);
                            removeSelectedNoti();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            return true;
        }
    }

    // Reply voice input
    private static final int SPEECH_REQUEST = 0;

    private void displaySpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        startActivityForResult(intent, SPEECH_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);
            Log.d("NotificationActivity", "Spoken text: " + spokenText);
            BackgroundService.RunCommand(getApplicationContext(), service -> {
                Collection<Device> devices = service.getDevices().values();
                for (Device device : devices) {
                    try {
                        JSONObject notiObject = notiList.getJSONObject(notiList.length() - (1 + mCardScrollView.getSelectedItemPosition())); //TODO: move json object outside of loop
                        String deviceId = notiObject.getString("deviceId");
                        if (device.getDeviceId().equals(deviceId)) {
                            NetworkPacket np = new NetworkPacket(NetworkPacket.PACKET_TYPE_NOTIFICATION_REPLY);
                            np.set("requestReplyId", notiObject.getString("requestReplyId"));
                            np.set("message", spokenText);
                            device.sendPacket(np);
                            Log.d("NotificationActivity", "Sent reply with request reply ID" + notiObject.getString("requestReplyId"));
                            removeSelectedNoti();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    private void removeSelectedNoti() {
        LiveCardService.notificationList.remove(LiveCardService.notificationList.length() - (1 + mCardScrollView.getSelectedItemPosition()));
        mCardScrollView.animate(mCardScrollView.getSelectedItemPosition(), CardScrollView.Animation.DELETION);
        mCards.remove(mCardScrollView.getSelectedItemPosition());
        LiveCardService.postUpdate(false);
        if (mCards.isEmpty()) {
            finish();
        }
    }
}
