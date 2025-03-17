package com.cato.kdeconnect.Plugins.MousePadPlugin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import com.cato.kdeconnect.BackgroundService;
import com.cato.kdeconnect.KdeConnect;
import com.google.android.glass.media.Sounds;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.glass.widget.CardBuilder;

public class MousePadActivity extends Activity implements SensorEventListener {
    static final String EXTRA_DEVICE_ID = "deviceId";
    private MousePadPlugin plugin;

    private GestureDetector mGestureDetector;
    private AudioManager mAudioManager;
    private SensorManager mSensorManager;

    private final static float MinDistanceToSendScroll = 2.5f; // touch gesture scroll
    private float accumulatedDistanceY = 0;
    private double scrollCoefficient = 1.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!getIntent().hasExtra(EXTRA_DEVICE_ID)) {
            Log.e("MousePadActivity", "You must include the deviceId for which this activity is started as an intent EXTRA");
            finish();
        }

        String deviceId = getIntent().getStringExtra(EXTRA_DEVICE_ID);
        plugin = KdeConnect.getInstance().getDevice(deviceId).getPlugin(MousePadPlugin.class);

        mGestureDetector = createGestureDetector(this);
        mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        mSensorManager = ContextCompat.getSystemService(this, SensorManager.class);

        setContentView(new CardBuilder(this, CardBuilder.Layout.TEXT)
                .setText("Tap with one finger for left click. Tap with two fingers for right click. Tap with three fingers for middle click. Scroll by swiping on the touchpad.")
                .getView());

    }

    private GestureDetector createGestureDetector(Context context) {
        GestureDetector gestureDetector = new GestureDetector(context);
        //Create a base listener for generic gestures
        gestureDetector.setBaseListener( new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                if (gesture == Gesture.TAP) {
                    plugin.sendLeftClick();
                    return true;
                } else if (gesture == Gesture.TWO_TAP) {
                    plugin.sendRightClick();
                    return true;
                } else if (gesture == Gesture.THREE_TAP) {
                    plugin.sendMiddleClick();
                    return true;
                }
                return false;
            }
        });
        gestureDetector.setScrollListener(new GestureDetector.ScrollListener() {
            @Override
            public boolean onScroll(float displacement, float delta, float velocity) {
                // If the displacement is too small, cancel the scroll gesture
                if (Math.abs(delta) < MinDistanceToSendScroll) {
                    return false;
                }

                accumulatedDistanceY += (float) (delta * scrollCoefficient);
                if (Math.abs(accumulatedDistanceY) > MinDistanceToSendScroll) {
                    plugin.sendScroll(0, accumulatedDistanceY);
                    accumulatedDistanceY = 0;
                }

                return true;
            }
        });
        return gestureDetector;
    }

    /*
     * Send generic motion events to the gesture detector
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] values = event.values;

        float X = -values[1] * 70;
        float Y = -values[0] * 70;

        if (X < 0.25 && X > -0.25) {
            X = 0;
        }

        if (Y < 0.25 && Y > -0.25) {
            Y = 0;
        }

        if (plugin == null) {
            finish();
            return;
        }
        plugin.sendMouseDelta(X, Y);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    protected void onResume() {

        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME);

        super.onResume();
    }

    @Override
    protected void onPause() {
        mSensorManager.unregisterListener(this);
        super.onPause();
    }
}
