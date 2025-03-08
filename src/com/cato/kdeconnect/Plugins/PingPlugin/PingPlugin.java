/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.kdeconnect.Plugins.PingPlugin;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.cato.kdeconnect.Helpers.NotificationHelper;
import com.cato.kdeconnect.LiveCardService;
import com.cato.kdeconnect.NetworkPacket;
import com.cato.kdeconnect.Plugins.Plugin;
import com.cato.kdeconnect.Plugins.PluginFactory;
import com.cato.kdeconnect.UserInterface.MainActivity;
import com.cato.kdeconnect.R;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

@PluginFactory.LoadablePlugin
public class PingPlugin extends Plugin {

    private final static String PACKET_TYPE_PING = "kdeconnect.ping";

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_ping);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_ping_desc);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {

        if (!np.getType().equals(PACKET_TYPE_PING)) {
            Log.e("PingPlugin", "Ping plugin should not receive packets other than pings!");
            return false;
        }

        //Log.e("PingPacketReceiver", "was a ping!");

        PendingIntent resultPendingIntent = PendingIntent.getActivity(
                context,
                0,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        int id;
        String message;
        if (np.has("message")) {
            message = np.getString("message");
            id = (int) System.currentTimeMillis();
        } else {
            message = "Ping!";
            id = 42; //A unique id to create only one notification
        }

        NotificationManager notificationManager = ContextCompat.getSystemService(context, NotificationManager.class);

        Notification noti = new NotificationCompat.Builder(context, NotificationHelper.Channels.DEFAULT)
                .setContentTitle(device.getName())
                .setContentText(message)
                .setContentIntent(resultPendingIntent)
                .setTicker(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .build();

        NotificationHelper.notifyCompat(notificationManager, id, noti);

        JSONObject notiObject = new JSONObject();
        try {
            Log.d("NotificationsPlugin", "Creating notification object");
            notiObject.put("title", device.getName());
            notiObject.put("text", message);
            notiObject.put("appName", "KDE Connect");
            notiObject.put("icon", bitmapToString(BitmapFactory.decodeResource(context.getResources(), R.drawable.app_icon)));
            notiObject.put("time", System.currentTimeMillis());
            notiObject.put("requestReplyId", "");
            notiObject.put("deviceId", device.getDeviceId());
            notiObject.put("key", id);
            Log.d("NotificationsPlugin", "Notification object created");
            LiveCardService.notificationList.put(notiObject);
            Log.d("NotificationsPlugin", "Notification added to list");
            LiveCardService.postUpdate(true);
            Log.d("NotificationsPlugin", "Service notification update called");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;

    }

    @Override
    public String getActionName() {
        return context.getString(R.string.send_ping);
    }

    @Override
    public void startMainActivity(Activity activity) {
        if (device != null) {
            device.sendPacket(new NetworkPacket(PACKET_TYPE_PING));
        }
    }

    @Override
    public boolean hasMainActivity() {
        return true;
    }

    @Override
    public boolean displayInContextMenu() {
        return true;
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_PING};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_PING};
    }

    public String bitmapToString(Bitmap bitmap){
        ByteArrayOutputStream baos=new  ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG,100, baos);
        byte [] b=baos.toByteArray();
        String temp= Base64.encodeToString(b, Base64.DEFAULT);
        return temp;
    }

}
