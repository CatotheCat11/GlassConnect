/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.connect.Plugins.ReceiveNotificationsPlugin;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.cato.connect.Helpers.NotificationHelper;
import com.cato.connect.LiveCardService;
import com.cato.connect.NetworkPacket;
import com.cato.connect.Plugins.Plugin;
import com.cato.connect.Plugins.PluginFactory;
import com.cato.connect.UserInterface.MainActivity;
import com.cato.connect.R;

import org.json.JSONArray;
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
        //TODO: add support for notification removal
        //This is a workaround while above is not implemented, to prevent crash
        if (np.getBoolean("isCancel", false)) {
            Log.d("NotificationsPlugin", "Received notification cancel packet");
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

        if (np.getBoolean("silent", false)) { //TODO: implement silent notis?
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

        /*no need because google glass notifications don't work
        NotificationManager notificationManager = ContextCompat.getSystemService(context, NotificationManager.class);

        Notification noti = new NotificationCompat.Builder(context, NotificationHelper.Channels.RECEIVENOTIFICATION)
                .setContentTitle(np.getString("appName"))
                .setContentText(np.getString("ticker"))
                .setTicker(np.getString("ticker"))
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(largeIcon)
                .setAutoCancel(true)
                .setLocalOnly(true)  // to avoid bouncing the notification back to other kdeconnect nodes
                .setDefaults(Notification.DEFAULT_ALL)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(np.getString("ticker")))
                .build();

        NotificationHelper.notifyCompat(notificationManager, "kdeconnectId:" + np.getString("id", "0"), np.getInt("id", 0), noti);
        */
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
