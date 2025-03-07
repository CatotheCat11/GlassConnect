package com.google.android.glass.eye;

import android.content.Context;

public class EyeGestureManager {
    public static final int INFINITE_TIMEOUT = -1;
    public static final String SERVICE_NAME = "eye_gesture";

    public interface Listener {
    	public void onDetected(EyeGesture gesture);
    }

    public static EyeGestureManager from(Context paramContext) {
        return null;
    }

    public void activateGazeLogging(boolean paramBoolean) {
    }

    public boolean applyAndSaveCalibration(EyeGesture paramEyeGesture) {
        return false;
    }

    public boolean clearCalibration(EyeGesture paramEyeGesture) {
        return false;
    }

    public void enableGazeService(boolean paramBoolean) {
    }

    public boolean endCalibrationInterval(EyeGesture paramEyeGesture) {
        return false;
    }

    public boolean isCalibrationComplete(EyeGesture paramEyeGesture) {
        return false;
    }

    public boolean isGazeLogging() {
        return false;
    }

    public boolean isRegistered() {
    	return false;
    }

    public boolean isSupported(EyeGesture paramEyeGesture) {
        return false;
    }

    public boolean loadCalibration(EyeGesture paramEyeGesture) {
        return false;
    }

    public boolean register(EyeGesture gesture, EyeGestureManager.Listener listener){
    	return false;
    }

    public boolean startCalibrationInterval(EyeGesture paramEyeGesture) {
        return false;
    }

    public boolean unregister(EyeGesture gesture, EyeGestureManager.Listener listener) {
    	return false;
    }

    public boolean addListener(EyeGesture gesture, EyeGestureManager.Listener listener, boolean bool) {
    	return false;
    }

    public boolean removeListener(EyeGesture gesture, EyeGestureManager.Listener listener) {
    	return false;
    }

}
