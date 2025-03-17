/* SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 * SPDX-FileCopyrightText: 2015 David Edmundson <david@davidedmundson.co.uk>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package com.cato.kdeconnect.Plugins.FindMyPhonePlugin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;

import com.cato.kdeconnect.BackgroundService;
import com.cato.kdeconnect.KdeConnect;
import com.cato.kdeconnect.UserInterface.ThemeUtil;
import com.cato.kdeconnect.UserInterface.TuggableView;
import com.google.android.glass.content.Intents;
import com.google.android.glass.media.Sounds;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.glass.widget.CardBuilder;

public class FindMyPhoneActivity extends Activity {
    static final String EXTRA_DEVICE_ID = "deviceId";

    private FindMyPhonePlugin plugin;

    private GestureDetector mGestureDetector;
    private AudioManager mAudioManager;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);

        mAudioManager =
                (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        mGestureDetector =
                new GestureDetector(this).setBaseListener(mBaseListener);

        if (!getIntent().hasExtra(EXTRA_DEVICE_ID)) {
            Log.e("FindMyPhoneActivity", "You must include the deviceId for which this activity is started as an intent EXTRA");
            finish();
        }

        String deviceId = getIntent().getStringExtra(EXTRA_DEVICE_ID);
        plugin = KdeConnect.getInstance().getDevice(deviceId).getPlugin(FindMyPhonePlugin.class);

        Window window = this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(new TuggableView(this, new CardBuilder(this, CardBuilder.Layout.MENU)
                .setText("Tap to stop ringing")
                .getView()));
        if (isHeadDetectionEnabled(getContentResolver())) {
            registerReceiver(broadCastReceiver, new IntentFilter(
                    Intents.ACTION_ON_HEAD_STATE_CHANGED));
        }


    }

    @Override
    protected void onStart() {
        super.onStart();
        /*
           For whatever reason when Android launches this activity as a SystemAlertWindow it calls:
           onCreate(), onStart(), onResume(), onStop(), onStart(), onResume().
           When using BackgroundService.RunWithPlugin we get into concurrency problems and sometimes no sound will be played
        */
        plugin.startPlaying();
        plugin.hideNotification();
    }

    @Override
    protected void onStop() {
        super.onStop();

        plugin.stopPlaying();
    }

    @Override
    protected void onDestroy() {
        if (isHeadDetectionEnabled(getContentResolver())) {
            unregisterReceiver(broadCastReceiver);
        }
        super.onDestroy();
    }

    private final GestureDetector.BaseListener mBaseListener =
            new GestureDetector.BaseListener() {

                @Override
                public boolean onGesture(Gesture gesture) {
                    if (gesture == Gesture.TAP) {
                        mAudioManager.playSoundEffect(Sounds.TAP);
                        finish();
                        return true;
                    }
                    return false;
                }
            };

    private final BroadcastReceiver broadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (Intents.ACTION_ON_HEAD_STATE_CHANGED.equals(intent.getAction())) {
                boolean onHead = intent.getBooleanExtra(Intents.EXTRA_IS_ON_HEAD,
                        false);
                if (onHead) {
                    finish();
                }
            }
        }
    };

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mGestureDetector.onMotionEvent(event)
                || super.onGenericMotionEvent(event);
    }

    public static boolean isHeadDetectionEnabled(ContentResolver contentResolver) {
        Uri uri = Uri.parse("content://com.google.android.glass.settings/system");
        String[] projection = {"value"};
        String selection = "name = ?";
        String[] selectionArgs = {"on_head_detection_enabled"};

        Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    String value = cursor.getString(cursor.getColumnIndexOrThrow("value"));
                    return Boolean.parseBoolean(value); // Convert "true"/"false" string to boolean
                }
            } finally {
                cursor.close(); // Always close the cursor to prevent memory leaks
            }
        }
        return false; // Default to false if not found or error occurs
    }
}
