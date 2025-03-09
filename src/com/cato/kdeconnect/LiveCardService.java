/*
 * SPDX-FileCopyrightText: 2025 Cato the Cat
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.kdeconnect;

import static com.cato.kdeconnect.ImageRequest.makeImageRequest;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.util.Log;
import android.widget.RemoteViews;
import android.os.Handler;

import com.cato.kdeconnect.Plugins.MprisPlugin.MprisPlugin;
import com.cato.kdeconnect.UserInterface.CardNotiActivity;
import com.cato.kdeconnect.UserInterface.MediaActivity;
import com.cato.kdeconnect.UserInterface.NotificationActivity;
import com.google.android.glass.eye.EyeGesture;
import com.google.android.glass.eye.EyeGestureManager;
import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.widget.CardBuilder;

import org.json.JSONArray;

import okhttp3.OkHttpClient;

public class LiveCardService extends Service {
    public static LiveCardService sInstance;

    public static boolean notiGlanceEnabled = false;

    private static final String NOTI_CARD_TAG = "NotificationCard";
    private static final String MEDIA_CARD_TAG = "MediaCard";

    private static LiveCard mNotiCard;
    private static LiveCard mMediaCard;
    private static RemoteViews mNotiCardView;
    private static RemoteViews mMediaCardView;
    public final Handler mHandler = new Handler();
    private final UpdateNotiCardRunnable mUpdateNotiCardRunnable =
            new UpdateNotiCardRunnable();
    private final UpdateMediaCardRunnable mUpdateMediaCardRunnable =
            new UpdateMediaCardRunnable();

    public static JSONArray notificationList;
    private EyeGestureManager mEyeGestureManager = null;
    private EyeGestureManager.Listener mEyeGestureListener;
    private static Integer lastNoti = -1;
    private static Boolean newNoti = false;
    private PowerManager mPowerManager;
    private static MprisPlugin.MprisPlayer playerStatus;
    private boolean live = false;
    static OkHttpClient client;
    private static String lastAlbumArtUrl = "";

    public static void start(Context context) {
        context.startService(new Intent(context, LiveCardService.class));
    }

    public static void mediaSessionStop() {
        if (mMediaCard != null && mMediaCard.isPublished()) {
            mMediaCard.unpublish();
            mMediaCard = null;
            lastAlbumArtUrl = "";
        }
    }

    public static void mediaSessionUpdated(String deviceId, String playerId, MprisPlugin.MprisPlayer status) {
        playerStatus = status;
        Intent menuIntent = new Intent(sInstance, MediaActivity.class);
        menuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        menuIntent.putExtra("player", playerId);
        menuIntent.putExtra("device", deviceId);
        menuIntent.putExtra("isPlaying", playerStatus.isPlaying());
        menuIntent.putExtra("nextAllowed", playerStatus.isGoNextAllowed());
        menuIntent.putExtra("prevAllowed", playerStatus.isGoPreviousAllowed());
        if (mMediaCard == null) {
            Log.d(MEDIA_CARD_TAG, "Publishing MediaCard");
            mMediaCard = new LiveCard(sInstance, MEDIA_CARD_TAG);
            mMediaCardView = new RemoteViews(sInstance.getPackageName(),
                    R.layout.mediacard);
            mMediaCard.setAction(PendingIntent.getActivity(
                    sInstance, 0, menuIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            mMediaCard.publish(LiveCard.PublishMode.SILENT);
            mMediaCard.setViews(mMediaCardView);
        }
        mMediaCard.setAction(PendingIntent.getActivity(
                sInstance, 0, menuIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        mMediaCardView.setTextViewText(R.id.media_title, playerStatus.getTitle());
        mMediaCardView.setTextViewText(R.id.media_artist, playerStatus.getArtist());
        mMediaCardView.setTextViewText(R.id.media_time, formatTime(playerStatus.getPosition()) + "/" + formatTime(playerStatus.getLength()));
        updateAlbumArt();
        sInstance.mHandler.post(sInstance.mUpdateMediaCardRunnable);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        CustomTrust customTrust = new CustomTrust(getApplicationContext());
        client = customTrust.getClient();
        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);
        notificationList = new JSONArray();
        setupEyeGestures();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mNotiCard == null) {
            Log.d(NOTI_CARD_TAG, "Publishing LiveCard");
            mNotiCard = new LiveCard(this, NOTI_CARD_TAG);

            mNotiCardView = new CardBuilder(getApplicationContext(), CardBuilder.Layout.TEXT)
                    .setText("No notifications")
                    .setFootnote("0 items")
                    .setAttributionIcon(R.drawable.livecardicon)
                    .getRemoteViews();

            Intent notiIntent = new Intent(getApplicationContext(), NotificationActivity.class);
            notiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK);
            mNotiCard.setAction(PendingIntent.getActivity(
                    getApplicationContext(), 0, notiIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

            mNotiCard.publish(LiveCard.PublishMode.SILENT);
            mNotiCard.setViews(mNotiCardView);

            mHandler.post(mUpdateNotiCardRunnable);
        }
        return START_STICKY;
    }

    private void setupEyeGestures() {
        notiGlanceEnabled = isNotificationGlanceEnabled(getContentResolver());
        if (notiGlanceEnabled) {
            GestureIds mGestureIds = new GestureIds();

            mEyeGestureManager = EyeGestureManager.from(this);

            mEyeGestureListener = new EyeGestureManager.Listener(){
                public void onDetected(EyeGesture gesture) {
                    Log.i("EyeGestureListener", "Gesture: " + gesture.getId());

                    int id = gesture.getId();
                    if (lastNoti == -1 && mEyeGestureManager != null) {
                        mEyeGestureManager.unregister(EyeGesture.LOOK_AT_SCREEN, mEyeGestureListener);
                    }

                    if (id == mGestureIds.LOOK_AT_SCREEN_ID && lastNoti != -1) {
                        Log.d("EyeGesture", "Screen");
                        Intent notiIntent = new Intent(getApplicationContext(), NotificationActivity.class);
                        notiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        notiIntent.putExtra("notiList", notificationList.toString());
                        notiIntent.putExtra("lastNoti", (int) lastNoti);
                        lastNoti = -1;
                        if (!mPowerManager.isScreenOn()) {
                            startActivity(notiIntent);
                        }
                        if (mEyeGestureManager != null) {
                            mEyeGestureManager.unregister(EyeGesture.LOOK_AT_SCREEN, mEyeGestureListener);
                        }
                    }
                }
            };
        }
    }
    public static void showNoti() {
        if (lastNoti != -1) {
            Intent notiIntent = new Intent(sInstance, NotificationActivity.class);
            notiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK);
            notiIntent.putExtra("notiList", notificationList.toString());
            notiIntent.putExtra("lastNoti", (int) lastNoti);
            lastNoti = -1;
            sInstance.startActivity(notiIntent);
        }
    }

    @Override
    public void onDestroy() {
        if (mEyeGestureManager != null) {
            mEyeGestureManager.unregister(EyeGesture.LOOK_AT_SCREEN, mEyeGestureListener);
        }
        if (mNotiCard != null && mNotiCard.isPublished()) {

            mNotiCard.unpublish();
            mNotiCard = null;
        }
        if (mMediaCard != null && mMediaCard.isPublished()) {

            mMediaCard.unpublish();
            mMediaCard = null;
        }
        sInstance = null;
        super.onDestroy();
    }

    /**
     * Runnable that updates live card contents
     */

    @Override
    public IBinder onBind(Intent intent) {
        /*
         * If you need to set up interprocess communication
         * (activity to a service, for instance), return a binder object
         * so that the client can receive and modify data in this service.
         *
         * A typical use is to give a menu activity access to a binder object
         * if it is trying to change a setting that is managed by the live card
         * service. The menu activity in this sample does not require any
         * of these capabilities, so this just returns null.
         */
        return null;
    }

    // Static method to post the update runnable
    public static void postUpdate(boolean newN) {
        if (sInstance != null) {
            if (newN) {
                MediaPlayer mp = MediaPlayer.create(sInstance.getApplicationContext(), R.raw.sound_notification);
                mp.start();
                newNoti = true;
            }
            sInstance.mHandler.post(sInstance.mUpdateNotiCardRunnable);
        } else {
            Log.w(NOTI_CARD_TAG, "Service instance is null; cannot post update.");
        }
    }

    private class UpdateNotiCardRunnable implements Runnable{

        private boolean mIsStopped = false;

        public void run(){
            if(!isStopped()){
                if (!mPowerManager.isScreenOn() && newNoti) {
                    lastNoti = notificationList.length() - 1;
                    newNoti = false;
                    if (mEyeGestureManager != null && notiGlanceEnabled) {
                        mEyeGestureManager.register(EyeGesture.LOOK_AT_SCREEN, mEyeGestureListener);
                    }
                }
                Log.d(NOTI_CARD_TAG, "Updating LiveCard");
                Log.d(NOTI_CARD_TAG, "Notification count: " + notificationList.length());
                if (notificationList.length() == 0) {
                    Intent notiIntent = new Intent(getApplicationContext(), NotificationActivity.class);
                    notiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    mNotiCard.setAction(PendingIntent.getActivity(
                            getApplicationContext(), 0, notiIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                    mNotiCardView = new CardBuilder(getApplicationContext(), CardBuilder.Layout.TEXT)
                            .setText("No notifications")
                            .setFootnote("0 items")
                            .setAttributionIcon(R.drawable.livecardicon)
                            .getRemoteViews();
                } else if (notificationList.length() == 1) {
                    try {
                        Intent notiIntent = new Intent(getApplicationContext(), CardNotiActivity.class);
                        notiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        notiIntent.putExtra("notiObj", notificationList.getJSONObject(0).toString());
                        mNotiCard.setAction(PendingIntent.getActivity(
                                getApplicationContext(), 0, notiIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                        String title = notificationList.getJSONObject(0).optString("title");
                        String text = notificationList.getJSONObject(0).optString("text");
                        String appName = notificationList.getJSONObject(0).optString("appName");
                        Bitmap icon = stringToBitmap(notificationList.getJSONObject(0).optString("icon"));
                        mNotiCardView = new CardBuilder(getApplicationContext(), CardBuilder.Layout.AUTHOR)
                                .setHeading(title)
                                .setSubheading(appName)
                                .setText(text)
                                .setIcon(icon)
                                .setFootnote("1 item")
                                .setAttributionIcon(R.drawable.livecardicon)
                                .getRemoteViews();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    Intent notiIntent = new Intent(getApplicationContext(), NotificationActivity.class);
                    notiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    notiIntent.putExtra("notiList", notificationList.toString());
                    mNotiCard.setAction(PendingIntent.getActivity(
                            getApplicationContext(), 0, notiIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                    mNotiCardView = new CardBuilder(getApplicationContext(), CardBuilder.Layout.EMBED_INSIDE)
                            .setFootnote(notificationList.length() + " items")
                            .setAttributionIcon(R.drawable.livecardicon)
                            .showStackIndicator(true)
                            .setEmbeddedLayout(R.layout.noticard)
                            .getRemoteViews();
                    try {
                        for (int i = 1; i < (Math.min(notificationList.length() + 1, 5)); i++) {
                            String text = notificationList.getJSONObject(notificationList.length() - i).optString("text");
                            String title = notificationList.getJSONObject(notificationList.length() - i).optString("title");
                            if (i == 1) {
                                mNotiCardView.setTextViewText(R.id.noti0, notiText(title, text));
                            } else if (i == 2) {
                                mNotiCardView.setTextViewText(R.id.noti1, notiText(title, text));
                            } else if (i == 3) {
                                mNotiCardView.setTextViewText(R.id.noti2, notiText(title, text));
                            } else if (i == 4) {
                                mNotiCardView.setTextViewText(R.id.noti3, notiText(title, text));
                            }
                        }
                        if (notificationList.length() < 4) {
                            mNotiCardView.setViewVisibility(R.id.divider3, android.view.View.GONE);
                        }
                        if (notificationList.length() < 3) {
                            mNotiCardView.setViewVisibility(R.id.divider2, android.view.View.GONE);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                mNotiCard.setViews(mNotiCardView);
            }
        }

        public boolean isStopped() {
            return mIsStopped;
        }

        public void setStop(boolean isStopped) {
            this.mIsStopped = isStopped;
        }
    }
    private class UpdateMediaCardRunnable implements Runnable {

        private boolean mIsStopped = false;

        public void run() {
            if (!isStopped()) {
                Log.d(MEDIA_CARD_TAG, "Updating MediaCard");
                mMediaCardView.setTextViewText(R.id.media_time, formatTime(playerStatus.getPosition()) + "/" + formatTime(playerStatus.getLength()));
                mMediaCardView.setProgressBar(R.id.media_progress, (int) playerStatus.getLength(), (int) playerStatus.getPosition(), false);
                // Live variable to prevent repeatedly updating info every second while playing
                if (playerStatus.isPlaying()) {
                    mMediaCardView.setImageViewResource(R.id.media_play, R.drawable.ic_play_white);
                    live = true;
                } else {
                    mMediaCardView.setImageViewResource(R.id.media_play, R.drawable.ic_pause_white);
                    live = false;
                }
                if (live) {
                    mHandler.postDelayed(mUpdateMediaCardRunnable, 1000);
                } else {
                    mMediaCardView.setTextViewText(R.id.media_title, playerStatus.getTitle());
                    mMediaCardView.setTextViewText(R.id.media_artist, playerStatus.getArtist());
                    mMediaCardView.setTextViewText(R.id.media_time, formatTime(playerStatus.getPosition()) + "/" + formatTime(playerStatus.getLength()));
                    mMediaCardView.setProgressBar(R.id.media_progress, (int) playerStatus.getLength(), (int) playerStatus.getPosition(), false);
                    updateAlbumArt();
                }
                if (mMediaCard != null && mMediaCardView != null) {
                    mMediaCard.setViews(mMediaCardView);
                }
            }
        }
        public boolean isStopped() {
            return mIsStopped;
        }

        public void setStop(boolean isStopped) {
            this.mIsStopped = isStopped;
        }
    }

    /**
     * IDs for various EyeGestures
     */
    private static class GestureIds {
        public int BLINK_ID;
        public int WINK_ID;
        public int DOUBLE_BLINK_ID;
        public int DOUBLE_WINK_ID;
        public int LOOK_AT_SCREEN_ID;
        public int LOOK_AWAY_FROM_SCREEN_ID;

        public GestureIds() {
            BLINK_ID = EyeGesture.BLINK.getId();
            WINK_ID = EyeGesture.WINK.getId();
            DOUBLE_BLINK_ID = EyeGesture.DOUBLE_BLINK.getId();
            DOUBLE_WINK_ID = EyeGesture.DOUBLE_WINK.getId();
            LOOK_AT_SCREEN_ID = EyeGesture.LOOK_AT_SCREEN.getId();
            LOOK_AWAY_FROM_SCREEN_ID = EyeGesture.LOOK_AWAY_FROM_SCREEN.getId();
        }
    }
    /**
     * @param encodedString
     * @return bitmap (from given string)
     */
    public Bitmap stringToBitmap(String encodedString){
        try {
            byte [] encodeByte= Base64.decode(encodedString,Base64.DEFAULT);
            Bitmap bitmap= BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.length);
            return bitmap;
        } catch(Exception e) {
            e.getMessage();
            return null;
        }
    }
    /**
     * Formats time in milliseconds to MM:SS
     *
     * @param millis Time in milliseconds
     * @return Formatted time string
     */
    private static String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    private static void updateAlbumArt() {
        String albumArtUrl = playerStatus.getAlbumArtUrl();
        if (!albumArtUrl.isEmpty() && !albumArtUrl.equals(lastAlbumArtUrl)) {
            mMediaCardView.setImageViewResource(R.id.media_art, R.drawable.placeholder_art);
            lastAlbumArtUrl = albumArtUrl;
            makeImageRequest(sInstance, albumArtUrl, client, new ImageRequest.ImageCallback() {
                @Override
                public void onImageLoaded(Bitmap bitmap) {
                    if (bitmap == null) { // Try http url
                        if (albumArtUrl.startsWith("https")) {
                            makeImageRequest(sInstance, albumArtUrl.replaceFirst("https", "http"), client, new ImageRequest.ImageCallback() {
                                @Override
                                public void onImageLoaded(Bitmap bitmap) {
                                    if (bitmap != null) {
                                        mMediaCardView.setImageViewBitmap(R.id.media_art, bitmap);
                                        if (mMediaCard != null && mMediaCardView != null) {
                                            mMediaCard.setViews(mMediaCardView);
                                        }
                                    }
                                }
                            });
                        }
                    } else {
                        mMediaCardView.setImageViewBitmap(R.id.media_art, bitmap);
                        mMediaCard.setViews(mMediaCardView);
                    }
                }
            });
        }
    }
    public static boolean isNotificationGlanceEnabled(ContentResolver contentResolver) {
        Uri uri = Uri.parse("content://com.google.android.glass.settings/system");
        String[] projection = {"value"};
        String selection = "name = ?";
        String[] selectionArgs = {"notification_glance_enabled"};

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

    public static Spannable notiText(String title, String text) {

        Spannable spannable = new SpannableString(title + "\n \n" + text);

        spannable.setSpan(new ForegroundColorSpan(Color.GRAY), 0, title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }
}
