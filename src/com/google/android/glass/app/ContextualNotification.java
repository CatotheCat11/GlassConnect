package com.google.android.glass.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;
import com.google.android.glass.widget.CardBuilder;

/* loaded from: classes.dex */
public class ContextualNotification extends Notification.Style {
    public static final String EXTRA_MENU_ITEM_ID = "menu_item_id";

    public ContextualNotification() {}

    public ContextualNotification(Notification.Builder builder) {
        this();
        setBuilder(builder);
    }
    public ContextualNotification setMenu(int resourceId, PendingIntent intent) {
        return this;
    }

    public ContextualNotification setReveal(boolean reveal) {
        return this;
    }
}
