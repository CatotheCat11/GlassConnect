/*
 * SPDX-FileCopyrightText: 2017 Matthijs Tijink <matthijstijink@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.kdeconnect.Plugins.MprisPlugin;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.service.notification.StatusBarNotification;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Pair;

import com.cato.kdeconnect.BackgroundService;
import com.cato.kdeconnect.Device;
import com.cato.kdeconnect.Helpers.NotificationHelper;
import com.cato.kdeconnect.KdeConnect;
import com.cato.kdeconnect.LiveCardService;
import com.cato.kdeconnect.Plugins.NotificationsPlugin.NotificationReceiver;
//import com.cato.kdeconnect.Plugins.SystemVolumePlugin.SystemVolumePlugin;
//import com.cato.kdeconnect.Plugins.SystemVolumePlugin.SystemVolumeProvider;
import com.cato.kdeconnect.R;

import java.util.HashSet;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

/**
 * Controls the mpris media control notification
 * <p>
 * There are two parts to this:
 * - The notification (with buttons etc.)
 * - The media session (via MediaSessionCompat; for lock screen control on
 * older Android version. And in the future for lock screen album covers)
 */
public class MprisMediaSession implements
        SharedPreferences.OnSharedPreferenceChangeListener,
        NotificationReceiver.NotificationListener {
        //SystemVolumeProvider.ProviderStateListener {

    private final static int MPRIS_MEDIA_NOTIFICATION_ID = 0x91b70463; // echo MprisNotification | md5sum | head -c 8
    private final static String MPRIS_MEDIA_SESSION_TAG = "com.cato.kdeconnect.media_session";

    private static final MprisMediaSession instance = new MprisMediaSession();

    private boolean spotifyRunning;

    public static MprisMediaSession getInstance() {
        return instance;
    }

    public static MediaSessionCompat getMediaSession() {
        return instance.mediaSession;
    }

    //Holds the device and player displayed in the notification
    private String notificationDevice = null;
    private MprisPlugin.MprisPlayer notificationPlayer = null;
    //Holds the device ids for which we can display a notification
    private final HashSet<String> mprisDevices = new HashSet<>();

    private Context context;
    private MediaSessionCompat mediaSession;

    //Callback for mpris plugin updates
    private final Handler mediaNotificationHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
        }
    };
    //Callback for control via the media session API
    private final MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {
            notificationPlayer.play();
        }

        @Override
        public void onPause() {
            notificationPlayer.pause();
        }

        @Override
        public void onSkipToNext() {
            notificationPlayer.next();
        }

        @Override
        public void onSkipToPrevious() {
            notificationPlayer.previous();
        }

        @Override
        public void onStop() {
            notificationPlayer.stop();
        }

        @Override
        public void onSeekTo(long pos) {
            notificationPlayer.setPosition((int) pos);
        }
    };

    /**
     * Called by the mpris plugin when it wants media control notifications for its device
     * <p>
     * Can be called multiple times, once for each device
     *
     * @param _context The context
     * @param mpris    The mpris plugin
     * @param device   The device id
     */
    public void onCreate(Context _context, MprisPlugin mpris, String device) {
        if (mprisDevices.isEmpty()) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(_context);
            prefs.registerOnSharedPreferenceChangeListener(this);
        }
        context = _context;
        mprisDevices.add(device);

        mpris.setPlayerListUpdatedHandler("media_notification", mediaNotificationHandler);
        mpris.setPlayerStatusUpdatedHandler("media_notification", mediaNotificationHandler);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            NotificationReceiver.RunCommand(context, service -> {

                service.addListener(MprisMediaSession.this);

                boolean serviceReady = service.isConnected();

                if (serviceReady) {
                    onListenerConnected(service);
                }
            });
        }

    }

    /**
     * Called when a device disconnects/does not want notifications anymore
     * <p>
     * Can be called multiple times, once for each device
     *
     * @param mpris  The mpris plugin
     * @param device The device id
     */
    public void onDestroy(MprisPlugin mpris, String device) {
        mprisDevices.remove(device);
        mpris.removePlayerStatusUpdatedHandler("media_notification");
        mpris.removePlayerListUpdatedHandler("media_notification");

        if (mprisDevices.isEmpty()) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    /**
     * Updates which device+player we're going to use in the notification
     * <p>
     * Prefers playing devices/mpris players, but tries to keep displaying the same
     * player and device, while possible.
     *
     * @param service The background service
     */
    private void updateCurrentPlayer(BackgroundService service) {
        Pair<Device, MprisPlugin.MprisPlayer> player = findPlayer(service);

        //Update the last-displayed device and player
        notificationDevice = player.first == null ? null : player.first.getDeviceId();
        notificationPlayer = player.second;
    }

    private Pair<Device, MprisPlugin.MprisPlayer> findPlayer(BackgroundService service) {
        //First try the previously displayed player (if still playing) or the previous displayed device (otherwise)
        if (notificationDevice != null && mprisDevices.contains(notificationDevice)) {
            Device device = KdeConnect.getInstance().getDevice(notificationDevice);

            MprisPlugin.MprisPlayer player;
            if (notificationPlayer != null && notificationPlayer.isPlaying()) {
                player = getPlayerFromDevice(device, notificationPlayer);
            } else {
                player = getPlayerFromDevice(device, null);
            }
            if (player != null) {
                return new Pair<>(device, player);
            }
        }

        // Try a different player from another device
        for (Device otherDevice : KdeConnect.getInstance().getDevices().values()) {
            MprisPlugin.MprisPlayer player = getPlayerFromDevice(otherDevice, null);
            if (player != null) {
                return new Pair<>(otherDevice, player);
            }
        }

        //So no player is playing. Try the previously displayed player again
        //  This will succeed if it's paused:
        //  that allows pausing and subsequently resuming via the notification
        if (notificationDevice != null && mprisDevices.contains(notificationDevice)) {
            Device device = KdeConnect.getInstance().getDevice(notificationDevice);

            MprisPlugin.MprisPlayer player = getPlayerFromDevice(device, notificationPlayer);
            if (player != null) {
                return new Pair<>(device, player);
            }
        }
        return new Pair<>(null, null);
    }

    private MprisPlugin.MprisPlayer getPlayerFromDevice(Device device, MprisPlugin.MprisPlayer preferredPlayer) {
        if (!mprisDevices.contains(device.getDeviceId()))
            return null;

        MprisPlugin plugin = device.getPlugin(MprisPlugin.class);

        if (plugin == null) {
            return null;
        }

        //First try the preferred player, if supplied
        if (plugin.hasPlayer(preferredPlayer) && shouldShowPlayer(preferredPlayer)) {
            return preferredPlayer;
        }

        //Otherwise, accept any playing player
        MprisPlugin.MprisPlayer player = plugin.getPlayingPlayer();
        if (shouldShowPlayer(player)) {
            return player;
        }

        return null;
    }

    private boolean shouldShowPlayer(MprisPlugin.MprisPlayer player) {
        return player != null && !(player.isSpotify() && spotifyRunning);
    }

    private void updateRemoteDeviceVolumeControl() {
        // Volume control feature is only available from Lollipop onwards
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

        /*BackgroundService.RunWithPlugin(context, notificationDevice, SystemVolumePlugin.class, plugin -> {
            SystemVolumeProvider systemVolumeProvider = SystemVolumeProvider.fromPlugin(plugin);
            systemVolumeProvider.addStateListener(this);
            systemVolumeProvider.startTrackingVolumeKeys();
        });*/
    }

    public void closeMediaNotification() {
        LiveCardService.mediaSessionStop();
        //Remove the notification
        NotificationManager nm = ContextCompat.getSystemService(context, NotificationManager.class);
        nm.cancel(MPRIS_MEDIA_NOTIFICATION_ID);

        //Clear the current player and media session
        notificationPlayer = null;
        if (mediaSession != null) {
            mediaSession.setPlaybackState(new PlaybackStateCompat.Builder().build());
            mediaSession.setMetadata(new MediaMetadataCompat.Builder().build());
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;

            /*SystemVolumeProvider currentProvider = SystemVolumeProvider.getCurrentProvider();
            if (currentProvider != null) {
                currentProvider.release();
            }*/
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

    }

    public void playerSelected(MprisPlugin.MprisPlayer player) {
        notificationPlayer = player;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onNotificationPosted(StatusBarNotification n) {
        if ("com.spotify.music".equals(n.getPackageName())) {
            spotifyRunning = true;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onNotificationRemoved(StatusBarNotification n) {
        if ("com.spotify.music".equals(n.getPackageName())) {
            spotifyRunning = false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onListenerConnected(NotificationReceiver service) {
        for (StatusBarNotification n : service.getActiveNotifications()) {
            if ("com.spotify.music".equals(n.getPackageName())) {
                spotifyRunning = true;
            }
        }
    }

    /*@Override
    public void onProviderStateChanged(@NonNull SystemVolumeProvider volumeProvider, boolean isActive) {
        if (mediaSession == null) return;

        if (isActive) {
            mediaSession.setPlaybackToRemote(volumeProvider);
        } else {
            mediaSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC);
        }
    }*/
}
