package com.cato.kdeconnect.UserInterface;

import android.app.Dialog;
import android.content.Context;
import android.media.AudioManager;
import android.view.MotionEvent;

import com.cato.kdeconnect.Device;
import com.google.android.glass.media.Sounds;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.glass.widget.CardBuilder;

//TODO: show this dialog when pair requested

public class PairDialog extends Dialog {

    private final AudioManager mAudioManager;
    private final GestureDetector mGestureDetector;
    private Device device;

    /**
     * Handles the tap gesture to call the dialog's
     * onClickListener if one is provided.
     */
    private final GestureDetector.BaseListener mBaseListener =
            new GestureDetector.BaseListener() {

                @Override
                public boolean onGesture(Gesture gesture) {
                    if (gesture == Gesture.TAP) {
                        mAudioManager.playSoundEffect(Sounds.TAP);
                        device.acceptPairing();
                        dismiss();
                        return true;
                    }
                    return false;
                }
            };

    public PairDialog(Context context, Device pairDevice) {
        super(context);
        device = pairDevice;

        mAudioManager =
                (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mGestureDetector =
                new GestureDetector(context).setBaseListener(mBaseListener);
        CardBuilder card = new CardBuilder(context, CardBuilder.Layout.ALERT)
                .setText("Pair with " + device.getName() + "?")
                .setFootnote("Tap to pair | Code: " + device.getVerificationKey())
                .setIcon(device.getIcon());

        setContentView(card.getView());
    }
    /** Overridden to let the gesture detector handle a possible tap event. */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mGestureDetector.onMotionEvent(event)
                || super.onGenericMotionEvent(event);
    }
}
