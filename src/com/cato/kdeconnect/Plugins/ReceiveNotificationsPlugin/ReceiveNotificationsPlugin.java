/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.kdeconnect.Plugins.ReceiveNotificationsPlugin;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.cato.kdeconnect.Helpers.NotificationHelper;
import com.cato.kdeconnect.LiveCardService;
import com.cato.kdeconnect.NetworkPacket;
import com.cato.kdeconnect.Plugins.Plugin;
import com.cato.kdeconnect.Plugins.PluginFactory;
import com.cato.kdeconnect.R;
import com.cato.kdeconnect.UserInterface.NotificationActivity;
import com.google.android.glass.app.ContextualNotification;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@PluginFactory.LoadablePlugin
public class ReceiveNotificationsPlugin extends Plugin {

    private final static String PACKET_TYPE_NOTIFICATION = "kdeconnect.notification";
    private final static String PACKET_TYPE_NOTIFICATION_REQUEST = "kdeconnect.notification.request";

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_receive_notifications);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_receive_notifications_desc);
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    public boolean onCreate() {
        // request all existing notifications
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_NOTIFICATION_REQUEST);
        np.set("request", true);
        device.sendPacket(np);
        return true;
    }

    public boolean onPacketReceived(final NetworkPacket np, String deviceId) {
        if (np.getBoolean("isCancel", false)) {
            Log.d("NotificationsPlugin", "Received notification cancel packet");
            String key = np.getString("id");
            try {
                for (int i = 0; i < LiveCardService.notificationList.length(); i++) {
                    JSONObject noti = LiveCardService.notificationList.getJSONObject(i);
                    if (noti.getString("key").equals(key)) {
                        LiveCardService.notificationList.remove(i);
                        LiveCardService.postUpdate(false);
                        break;
                    }
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return true;
        }
        Log.d("NotificationsPlugin", "Received notification packet from " + deviceId);
        String title = np.getString("title");
        String text = np.getString("text");
        String appName = np.getString("appName");
        String requestReplyId = np.getString("requestReplyId");
        String key = np.getString("id");
        JSONArray actions = null;
        if (np.has("actions")) {
            actions = np.getJSONArray("actions");
            Log.d("NotificationsPlugin", "Actions: " + actions.toString());
        }
        Long time = 0L;
        if (np.has("time")) {
            time = np.getLong("time");
        }
        Log.d("NotificationsPlugin", "Received notification: " + title);
        Log.d("NotificationsPlugin", "Request reply ID: " + requestReplyId);

        if (!np.has("ticker") || !np.has("appName") || !np.has("id")) {
            Log.e("NotificationsPlugin", "Received notification package lacks properties");
            return true;
        }
        Log.d("NotificationsPlugin", "Notification has all required properties");

        if (np.getBoolean("silent", false)) {
            Log.d("NotificationsPlugin", "Silent notification, not showing");
            return true;
        }

        /*
        Log.d("NotificationsPlugin", "Now creating intent");
        PendingIntent resultPendingIntent = PendingIntent.getActivity(
                context,
                0,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Log.d("NotificationsPlugin", "Pending intent created");*/
        Log.d("NotificationsPlugin", "Creating icon");
        Bitmap largeIcon = null;
        if (np.hasPayload()) {
            final InputStream input = np.getPayload().getInputStream();
            largeIcon = BitmapFactory.decodeStream(input);
            np.getPayload().close();
        }
        Log.d("NotificationsPlugin", "Icon created");
        Log.d("NotificationsPlugin", "Adding notification to service");
        JSONObject notiObject = new JSONObject();
        try {
            Log.d("NotificationsPlugin", "Creating notification object, device id: " + deviceId);
            notiObject.put("title", title);
            notiObject.put("text", text);
            notiObject.put("appName", appName);
            if (largeIcon != null) {
                notiObject.put("icon", bitmapToString(largeIcon));
            }
            notiObject.put("time", time);
            notiObject.put("requestReplyId", requestReplyId);
            notiObject.put("deviceId", deviceId);
            notiObject.put("key", key);
            if (actions != null) {
                notiObject.put("actions", actions);
            }
            Log.d("NotificationsPlugin", "Notification object created");
            LiveCardService.notificationList.put(notiObject);
            Log.d("NotificationsPlugin", "Notification added to list");
            LiveCardService.postUpdate(true);
            Log.d("NotificationsPlugin", "Service notification update called");
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (LiveCardService.sInstance == null) {
            Log.e("NotificationsPlugin", "Sinstance is null, cannot create notification intent");
            return true;
        }
        Intent notiIntent = new Intent(LiveCardService.sInstance, NotificationActivity.class);
        notiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        notiIntent.putExtra("slideIn", true);

        PendingIntent menuIntent = PendingIntent.getActivity(LiveCardService.sInstance, 1, notiIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationManager notificationManager = ContextCompat.getSystemService(LiveCardService.sInstance, NotificationManager.class);

        Notification.Builder notiBuilder = new Notification.Builder(LiveCardService.sInstance);
        ContextualNotification style = new ContextualNotification(notiBuilder)
                .setMenu(R.menu.noti_actions, menuIntent)
                .setReveal(true);
        style.setBuilder(notiBuilder);

        Bundle extras = new Bundle();
        extras.putBoolean("whitelist", true);

        Notification noti = notiBuilder.setExtras(extras)
                .setContentTitle(np.getString("appName"))
                .setContentText(np.getString("ticker"))
                .setTicker(np.getString("ticker"))
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(largeIcon)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_SOUND)
                .setStyle(style)
                .setFullScreenIntent(menuIntent, false)
                .build();

        NotificationHelper.notifyCompat(notificationManager, "kdeconnectId:" + np.getString("id", "0"), (int) System.currentTimeMillis(), noti);
        return true;
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_NOTIFICATION};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_NOTIFICATION_REQUEST};
    }
    public String bitmapToString(Bitmap bitmap){
        ByteArrayOutputStream baos=new  ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG,100, baos);
        byte [] b=baos.toByteArray();
        String temp= Base64.encodeToString(b, Base64.DEFAULT);
        return temp;
    }
}
