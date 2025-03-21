package com.cato.kdeconnect.Plugins.FindMyPhonePlugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.cato.kdeconnect.BackgroundService;
import com.cato.kdeconnect.KdeConnect;

public class FindMyPhoneReceiver extends BroadcastReceiver {
    final static String ACTION_FOUND_IT = "com.cato.kdeconnect.Plugins.FindMyPhonePlugin.foundIt";
    final static String EXTRA_DEVICE_ID = "deviceId";

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case ACTION_FOUND_IT:
                foundIt(context, intent);
                break;
            default:
                Log.d("ShareBroadcastReceiver", "Unhandled Action received: " + intent.getAction());
        }
    }

    private void foundIt(Context context, Intent intent) {
        if (!intent.hasExtra(EXTRA_DEVICE_ID)) {
            Log.e("FindMyPhoneReceiver", "foundIt() - deviceId extra is not present, ignoring");
            return;
        }

        String deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);

        FindMyPhonePlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, FindMyPhonePlugin.class);
        if (plugin == null) {
            return;
        }
        plugin.stopPlaying();
    }
}
