/*
 * Copyright (C) 2019 Bootleggers ROM
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.bootleggers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Process;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class TickerUtils {

    private static final String TAG = "TickerUtils";
    private static final boolean DEBUG = true;
    private Context mContext;
    private String updateIntentAction;
    private PendingIntent pendingTickerUpdate;
    private boolean isRunning;
    private boolean isScreenOn = true;
    private long lastUpdated;
    private long scheduledAlarmTime = 0;
    private AlarmManager alarmManager;

    // Declare some variables to be modified later
    public static  String mTrackInfo;
    public static  boolean mIsFromNowPlaying;
    public int DISPLAY_TIME_INTERVAL_SEC = 120000;

    private BroadcastReceiver tickerInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            if (DEBUG) Log.d(TAG, "Received intent: " + intent.getAction());
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                onScreenOff();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                onScreenOn();
            } else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || updateIntentAction.equals(intent.getAction())) {
                updateTickerAndNotify();
            } else if (Intent.ACTION_TIME_CHANGED.equals(intent.getAction()) || Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {
                resetScheduledAlarm();
                updateTickerAndNotify();
            }
        }
    };

    public TickerUtils(Context context) {
        mContext = context;
        updateIntentAction = "updateIntentAction_" + Integer.toString(getRandomInt());
        pendingTickerUpdate = PendingIntent.getBroadcast(mContext, getRandomInt(), new Intent(updateIntentAction), 0);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(updateIntentAction);
        mContext.registerReceiver(tickerInfoReceiver, filter);
    }

    public static void setTickerInfo(String trackInfo, boolean pissel) {
    	mTrackInfo = trackInfo;
    	mIsFromNowPlaying = pissel;
    }

    public static boolean isAvailable(Context context) {
    	return true;
    }

    private int getRandomInt() {
        Random r = new Random();
        return r.nextInt((20000000 - 10000000) + 1) + 10000000;
    }

    public void destroy(){
        mTrackInfo = null;
        mIsFromNowPlaying = false;
        mContext.unregisterReceiver(tickerInfoReceiver);
    }

    private boolean needsUpdate() {
        boolean lastUpdatedExpired = System.currentTimeMillis() - lastUpdated > DISPLAY_TIME_INTERVAL_SEC;
        return lastUpdatedExpired;
    }

    private void onScreenOn() {
        if (isScreenOn){
            return;
        }
        if (DEBUG) Log.d(TAG, "onScreenOn");
        isScreenOn = true;
        if (!isRunning) {
            if (needsUpdate()) {
                if (DEBUG) Log.d(TAG, "Needs update, triggering updateTickerInfo");
                updateTickerInfo();
            } else {
                if (DEBUG) Log.d(TAG, "Scheduling update");
                scheduleTickerUpdateAlarm();
            }
        }
    }

    private void onScreenOff() {
        if (DEBUG) Log.d(TAG, "onScreenOff");
        isScreenOn = false;
        cancelTickerUpdateAlarm();
    }

    private void resetScheduledAlarm(){
        scheduledAlarmTime = 0;
        scheduleTickerUpdateAlarm();
    }

    private void scheduleTickerUpdateAlarm() {
        if (!isScreenOn) {
            return;
        }
        if (System.currentTimeMillis() >= scheduledAlarmTime){
            scheduledAlarmTime = System.currentTimeMillis() + DISPLAY_TIME_INTERVAL_SEC;
        }
        alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingTickerUpdate);
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, scheduledAlarmTime, pendingTickerUpdate);
        if (DEBUG) Log.d(TAG, "Update scheduled");
    }

    private void cancelTickerUpdateAlarm() {
        alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingTickerUpdate);
        if (DEBUG) Log.d(TAG, "Update scheduling canceled");
    }

    private void updateTickerInfo () {
        boolean isAnotherTrack = !mTrackInfo.isEmpty();
        if (!isAvailable(mContext) || !isAnotherTrack) {
            isRunning = false;
            return;
        }
        isRunning = true;
        if (DEBUG) Log.d(TAG, mTrackInfo.toString());
        isRunning = false;
    }

    private void updateTickerAndNotify() {
        if (isRunning){
            return;
        }
        isRunning = true;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                updateTickerInfo();
                lastUpdated = System.currentTimeMillis();
                resetScheduledAlarm();
            }
        });
        thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
    }


    public boolean getIsFromNowPlaying() {
        return mIsFromNowPlaying;
    }

    public String getTickerInfo() {
        return mTrackInfo;
    }
}
