/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.keyguard;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v4.graphics.ColorUtils;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CustomAnalogClock;
import android.widget.DeadPoolAnalogClock;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.internal.util.ArrayUtils;
import com.android.internal.widget.LockPatternUtils;
//import com.android.systemui.ChargingView;
import com.android.systemui.doze.DozeLog;
import com.android.systemui.omni.OmniJawsClient;
import com.android.systemui.statusbar.policy.DateView;

import java.util.Locale;

public class KeyguardStatusView extends GridLayout implements
        OmniJawsClient.OmniJawsObserver {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "KeyguardStatusView";
    private static final int MARQUEE_DELAY_MS = 2000;

    private final LockPatternUtils mLockPatternUtils;
    private final AlarmManager mAlarmManager;

    private TextView mAlarmStatusView;
    private DateView mDateView;
    private CustomAnalogClock mAnalogClockView;
    private DeadPoolAnalogClock mDeadPoolClockView;
    private TextClock mClockView;
    private TextView mOwnerInfo;
    private ViewGroup mClockContainer;
    //private ChargingView mBatteryDoze;
    private View mKeyguardStatusArea;
    private Runnable mPendingMarqueeStart;
    private Handler mHandler;

    //On the first boot, keygard will start to receiver TIME_TICK intent.
    //And onScreenTurnedOff will not get called if power off when keyguard is not started.
    //Set initial value to false to skip the above case.
    private boolean mEnableRefresh = false;

    private View[] mVisibleInDoze;
    private boolean mPulsing;
    private float mDarkAmount = 0;
    private int mTextColor;
    private int mDateTextColor;
    private int mAlarmTextColor;

    private boolean mForcedMediaDoze;
    private View mWeatherView;
    private TextView mWeatherCity;
    private ImageView mWeatherConditionImage;
    private Drawable mWeatherConditionDrawable;
    private TextView mWeatherCurrentTemp;
    private TextView mWeatherConditionText;
    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherData;
    private boolean mWeatherEnabled;

    private boolean mShowWeather;
    private boolean mShowConditionIcon;
    private boolean mShowLocation;
    private int mIconNameValue = -1;

    private SettingsObserver mSettingsObserver;

    private boolean mShowAlarm;
    private boolean mAvailableAlarm;
    private boolean mShowClock;
    private boolean mShowDate;
    private int mClockSelection;
    private int mDateSelection;

    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onTimeChanged() {
            if (mEnableRefresh) {
                refresh();
            }
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            if (showing) {
                if (DEBUG) Slog.v(TAG, "refresh statusview showing:" + showing);
                refresh();
                updateOwnerInfo();
            }
        }

        @Override
        public void onStartedWakingUp() {
            setEnableMarquee(true);
            mEnableRefresh = true;
            refresh();
        }

        @Override
        public void onFinishedGoingToSleep(int why) {
            setEnableMarquee(false);
            mEnableRefresh = false;
        }

        @Override
        public void onUserSwitchComplete(int userId) {
            refresh();
            updateOwnerInfo();
        }
    };

    public KeyguardStatusView(Context context) {
        this(context, null, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mLockPatternUtils = new LockPatternUtils(getContext());
        mHandler = new Handler(Looper.myLooper());
        mWeatherClient = new OmniJawsClient(mContext);
        mWeatherEnabled = mWeatherClient.isOmniJawsEnabled();
    }

    private void setEnableMarquee(boolean enabled) {
        if (DEBUG) Log.v(TAG, "Schedule setEnableMarquee: " + (enabled ? "Enable" : "Disable"));
        if (enabled) {
            if (mPendingMarqueeStart == null) {
                mPendingMarqueeStart = () -> {
                    setEnableMarqueeImpl(true);
                    mPendingMarqueeStart = null;
                };
                mHandler.postDelayed(mPendingMarqueeStart, MARQUEE_DELAY_MS);
            }
        } else {
            if (mPendingMarqueeStart != null) {
                mHandler.removeCallbacks(mPendingMarqueeStart);
                mPendingMarqueeStart = null;
            }
            setEnableMarqueeImpl(false);
        }
    }

    private void setEnableMarqueeImpl(boolean enabled) {
        if (DEBUG) Log.v(TAG, (enabled ? "Enable" : "Disable") + " transport text marquee");
        if (mAlarmStatusView != null) mAlarmStatusView.setSelected(enabled);
        if (mOwnerInfo != null) mOwnerInfo.setSelected(enabled);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mClockContainer = findViewById(R.id.keyguard_clock_container);
        mAlarmStatusView = findViewById(R.id.alarm_status);
        mDateView = findViewById(R.id.date_view);
        mAnalogClockView = findViewById(R.id.analog_clock_view);
        mDeadPoolClockView = findViewById(R.id.deadpool_clock_view);
        mClockView = findViewById(R.id.clock_view);
        mClockView.setShowCurrentUserTime(true);
        mOwnerInfo = findViewById(R.id.owner_info);
        //mBatteryDoze = findViewById(R.id.battery_doze);
        mKeyguardStatusArea = findViewById(R.id.keyguard_status_area);
        mVisibleInDoze = new View[]{/*mBatteryDoze, */mClockView, mAnalogClockView, mDeadPoolClockView, mKeyguardStatusArea};
        mTextColor = mClockView.getCurrentTextColor();
        mDateTextColor = mDateView.getCurrentTextColor();
        mAlarmTextColor = mAlarmStatusView.getCurrentTextColor();
        mWeatherView = findViewById(R.id.keyguard_weather_view);
        mWeatherCity = (TextView) findViewById(R.id.city);
        mWeatherConditionImage = (ImageView) findViewById(R.id.weather_image);
        mWeatherCurrentTemp = (TextView) findViewById(R.id.current_temp);
        mWeatherConditionText = (TextView) findViewById(R.id.condition);

        updateSettings();

        boolean shouldMarquee = KeyguardUpdateMonitor.getInstance(mContext).isDeviceInteractive();
        setEnableMarquee(shouldMarquee);
        refresh();
        updateOwnerInfo();

        mSettingsObserver = new SettingsObserver(new Handler());
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // ClockView
        //mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
        //        getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
        //MarginLayoutParams layoutParams = (MarginLayoutParams) mClockView.getLayoutParams();
        //layoutParams.bottomMargin = getResources().getDimensionPixelSize(
        //        R.dimen.bottom_text_spacing_digital);
        //mClockView.setLayoutParams(layoutParams);
        //mClockView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));
        mDateView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        // Custom analog clock
        MarginLayoutParams customlayoutParams = (MarginLayoutParams) mAnalogClockView.getLayoutParams();
        customlayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.bottom_text_spacing_digital);
        mAnalogClockView.setLayoutParams(customlayoutParams);

        // DeadPool analog clock
        MarginLayoutParams deadpoollayoutParams = (MarginLayoutParams) mDeadPoolClockView.getLayoutParams();
        deadpoollayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.bottom_text_spacing_digital);
        mDeadPoolClockView.setLayoutParams(deadpoollayoutParams);

        // DateView
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));

        // OwnerInfo
        if (mOwnerInfo != null) {
            mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));
            mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
    }

    private int getLockClockFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_CLOCK_FONTS, 4);
    }

    private int getLockDateFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_DATE_FONTS, 4);
    }

    private int getLockOwnerFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_OWNER_FONTS, 4);
    }

    public void refreshTime() {
        mDateView.setDatePattern(Patterns.dateViewSkel);

        if (mClockSelection == 0) {
            mClockView.setFormat12Hour(Patterns.clockView12);
            mClockView.setFormat24Hour(Patterns.clockView24);
        } else if (mClockSelection == 1) {
            mClockView.setFormat12Hour(Html.fromHtml("<strong>hh</strong>mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong>mm"));
        } else if (mClockSelection == 5) {
            mClockView.setFormat12Hour(Html.fromHtml("<strong>hh</strong><br>mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong><br>mm"));
        } else {
            mClockView.setFormat12Hour("hh\nmm");
            mClockView.setFormat24Hour("kk\nmm");
        }
    }

    private void refresh() {
        AlarmManager.AlarmClockInfo nextAlarm =
                mAlarmManager.getNextAlarmClock(UserHandle.USER_CURRENT);
        Patterns.update(mContext, nextAlarm != null && mShowAlarm);

        refreshTime();
        refreshAlarmStatus(nextAlarm);
        refreshLockFont();
    }

    void refreshAlarmStatus(AlarmManager.AlarmClockInfo nextAlarm) {
        if (nextAlarm != null) {
            String alarm = formatNextAlarm(mContext, nextAlarm);
            mAlarmStatusView.setText(alarm);
            mAlarmStatusView.setContentDescription(
                    getResources().getString(R.string.keyguard_accessibility_next_alarm, alarm));
            mAvailableAlarm = true;
        } else {
            mAvailableAlarm = false;
        }
        mAlarmStatusView.setVisibility(mDarkAmount != 1 ? (mShowAlarm && mAvailableAlarm ? View.VISIBLE : View.GONE)
                : mAvailableAlarm ? View.VISIBLE : View.GONE);
    }

    public int getClockBottom() {
        return mKeyguardStatusArea.getBottom();
    }

    public int getClockSelection() {
        return mClockSelection;
    }

    public float getClockTextSize() {
        return mClockView.getTextSize();
    }

    public static String formatNextAlarm(Context context, AlarmManager.AlarmClockInfo info) {
        if (info == null) {
            return "";
        }
        String skeleton = DateFormat.is24HourFormat(context, ActivityManager.getCurrentUser())
                ? "EHm"
                : "Ehma";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        return DateFormat.format(pattern, info.getTriggerTime()).toString();
    }

    private void updateOwnerInfo() {
        if (mOwnerInfo == null) return;
        String ownerInfo = getOwnerInfo();
        if (!TextUtils.isEmpty(ownerInfo)) {
            mOwnerInfo.setVisibility(View.VISIBLE);
            mOwnerInfo.setText(ownerInfo);
        } else {
            mOwnerInfo.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
        mWeatherEnabled = mWeatherClient.isOmniJawsEnabled();
        mWeatherClient.addObserver(this);
        mSettingsObserver.observe();
        queryAndUpdateWeather();
        updateWeatherSettings(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
        mWeatherClient.removeObserver(this);
        mWeatherClient.cleanupObserver();
        mSettingsObserver.unobserve();
    }

    private String getOwnerInfo() {
        String info = null;
        if (mLockPatternUtils.isDeviceOwnerInfoEnabled()) {
            // Use the device owner information set by device policy client via
            // device policy manager.
            info = mLockPatternUtils.getDeviceOwnerInfo();
        } else {
            // Use the current user owner information if enabled.
            final boolean ownerInfoEnabled = mLockPatternUtils.isOwnerInfoEnabled(
                    KeyguardUpdateMonitor.getCurrentUser());
            if (ownerInfoEnabled) {
                info = mLockPatternUtils.getOwnerInfo(KeyguardUpdateMonitor.getCurrentUser());
            }
        }
        return info;
    }
    @Override
    public void weatherUpdated() {
        queryAndUpdateWeather();
    }

    public void queryAndUpdateWeather() {
        try {
                if (mWeatherEnabled) {
                    mWeatherClient.queryWeather();
                    mWeatherData = mWeatherClient.getWeatherInfo();
                    mWeatherCity.setText(mWeatherData.city);
                    mWeatherConditionImage.setImageDrawable(
                        mWeatherClient.getWeatherConditionImage(mWeatherData.conditionCode));
                    mWeatherCurrentTemp.setText(mWeatherData.temp + mWeatherData.tempUnits);
                    mWeatherConditionText.setText(mWeatherData.condition);
                    updateWeatherSettings(false);
                } else {
                    mWeatherCity.setText(null);
                    mWeatherConditionImage.setImageDrawable(mContext
                        .getResources().getDrawable(R.drawable.keyguard_weather_default_off));
                    mWeatherCurrentTemp.setText(null);
                    mWeatherConditionText.setText(null);
                    updateWeatherSettings(true);
                }
       } catch(Exception e) {
          // Do nothing
       }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void updateVisibilities() {
        switch (mClockSelection) {
            case 0: // default digital
            default:
                mClockView.setBackgroundResource(0);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
                mClockView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                mClockView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mAnalogClockView.setVisibility(View.GONE);
                mDeadPoolClockView.setVisibility(View.GONE);
                break;
            case 1: // digital (bold)
                mClockView.setBackgroundResource(0);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
                mClockView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                mClockView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mAnalogClockView.setVisibility(View.GONE);
                mDeadPoolClockView.setVisibility(View.GONE);
                break;
            case 2: // analog
                mClockView.setBackgroundResource(0);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
                mClockView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                mClockView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mAnalogClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mClockView.setVisibility(View.GONE);
                mDeadPoolClockView.setVisibility(View.GONE);
                break;
            case 3: // analog (deadpool)
                mClockView.setBackgroundResource(0);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
                mClockView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                mClockView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mDeadPoolClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mAnalogClockView.setVisibility(View.GONE);
                mClockView.setVisibility(View.GONE);
                break;
            case 4: // sammy
                mClockView.setBackgroundResource(0);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
                mClockView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                mClockView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mAnalogClockView.setVisibility(View.GONE);
                mDeadPoolClockView.setVisibility(View.GONE);
                break;
            case 5: // sammy (bold)
                mClockView.setBackgroundResource(0);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
                mClockView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                mClockView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mAnalogClockView.setVisibility(View.GONE);
                mDeadPoolClockView.setVisibility(View.GONE);
                break;
            case 6: // shishu normal 01
                mClockView.setBackground(getResources().getDrawable(R.drawable.clock_shishu_normalbg));
                mClockView.getLayoutParams().width = getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_size);
                mClockView.getLayoutParams().height = getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_size);
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 65);
                mClockView.setPadding(20,20,20,20);
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mAnalogClockView.setVisibility(View.GONE);
                mDeadPoolClockView.setVisibility(View.GONE);
                break;
        }

        mDateView.setVisibility(mDarkAmount != 1 ? (mShowDate ? View.VISIBLE : View.GONE) : View.VISIBLE);

        mAlarmStatusView.setVisibility(mDarkAmount != 1 ? (mShowAlarm && mAvailableAlarm ? View.VISIBLE : View.GONE)
                : mAvailableAlarm ? View.VISIBLE : View.GONE);
    }

    private void updateSettings() {
        final ContentResolver resolver = getContext().getContentResolver();
        final Resources res = getContext().getResources();

        AlarmManager.AlarmClockInfo nextAlarm =
                mAlarmManager.getNextAlarmClock(UserHandle.USER_CURRENT);
        mShowAlarm = Settings.System.getIntForUser(resolver,
                Settings.System.HIDE_LOCKSCREEN_ALARM, 1, UserHandle.USER_CURRENT) == 1;
        mShowClock = Settings.System.getIntForUser(resolver,
                Settings.System.HIDE_LOCKSCREEN_CLOCK, 1, UserHandle.USER_CURRENT) == 1;
        mShowDate = Settings.System.getIntForUser(resolver,
                Settings.System.HIDE_LOCKSCREEN_DATE, 1, UserHandle.USER_CURRENT) == 1;
        mClockSelection = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_CLOCK_SELECTION, 0, UserHandle.USER_CURRENT);
        mDateSelection = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_DATE_SELECTION, 0, UserHandle.USER_CURRENT);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mKeyguardStatusArea.getLayoutParams();
        switch (mClockSelection) {
            case 0: // default digital
            default:
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(true);
                mAnalogClockView.unregisterReceiver();
                mDeadPoolClockView.unregisterReceiver();
                break;
            case 1: // digital (bold)
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(true);
                mAnalogClockView.unregisterReceiver();
                mDeadPoolClockView.unregisterReceiver();
                break;
            case 2: // analog
                params.addRule(RelativeLayout.BELOW, R.id.analog_clock_view);
                mAnalogClockView.registerReceiver();
                mDeadPoolClockView.unregisterReceiver();
                break;
            case 3: // analog (deadpool)
                params.addRule(RelativeLayout.BELOW, R.id.deadpool_clock_view);
                mAnalogClockView.unregisterReceiver();
                mDeadPoolClockView.registerReceiver();
                break;
            case 4: // sammy
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(false);
                mAnalogClockView.unregisterReceiver();
                mDeadPoolClockView.unregisterReceiver();
                break;
            case 5: // sammy (bold)
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(false);
                mAnalogClockView.unregisterReceiver();
                mDeadPoolClockView.unregisterReceiver();
                break;
            case 6: // shishu normal 01
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(false);
                mAnalogClockView.unregisterReceiver();
                mDeadPoolClockView.unregisterReceiver();
                break;
        }

        switch (mDateSelection) {
            case 0: // default
            default:
                mDateView.setBackgroundResource(0);
                mDateView.setTypeface(Typeface.DEFAULT);
                mDateView.setPadding(0,0,0,0);
                break;
            case 1: // semi-transparent box
                mDateView.setBackground(getResources().getDrawable(R.drawable.date_box_str_border));
                mDateView.setTypeface(Typeface.DEFAULT_BOLD);
                mDateView.setPadding(40,20,40,20);
                break;
            case 2: // semi-transparent box (round)
                mDateView.setBackground(getResources().getDrawable(R.drawable.date_str_border));
                mDateView.setTypeface(Typeface.DEFAULT_BOLD);
                mDateView.setPadding(40,20,40,20);
                break;
        }
        updateVisibilities();
        updateDozeVisibleViews();
    }

    private void updateWeatherSettings(boolean forceHide) {
        View weatherPanel = findViewById(R.id.weather_panel);
        TextView noWeatherInfo = (TextView) findViewById(R.id.no_weather_info_text);

        if (mWeatherView != null) {
            mWeatherView.setVisibility(mShowWeather ?
                View.VISIBLE : View.GONE);
        }
        if (noWeatherInfo != null) {
            noWeatherInfo.setVisibility(forceHide ?
                View.VISIBLE : View.GONE);
        }
        if (weatherPanel != null) {
            weatherPanel.setVisibility(forceHide ?
                View.GONE : View.VISIBLE);
        }
        if (mWeatherConditionText != null) {
            mWeatherConditionText.setVisibility(forceHide ?
                View.GONE : View.VISIBLE);
        }
        if (mWeatherConditionImage != null) {
            if (mShowConditionIcon) {
                mWeatherConditionImage.setVisibility(forceHide ?
                    View.GONE : View.VISIBLE);
            } else {
                mWeatherConditionImage.setVisibility(View.GONE);
            }
        }
        if (mWeatherCurrentTemp != null) {
            mWeatherCurrentTemp.setVisibility(forceHide ?
                View.GONE : View.VISIBLE);
        }
        if (mWeatherCity != null) {
            if (mShowLocation) {
                mWeatherCity.setVisibility(forceHide ?
                    View.GONE : View.VISIBLE);
            } else {
                mWeatherCity.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void weatherError(int errorReason) {
        if (DEBUG) Log.d(TAG, "weatherError " + errorReason);
    }

    private void refreshLockFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockFont = isPrimary ? getLockClockFont() : 0;
        int lockDateFont = isPrimary ? getLockDateFont() : 0;
        int lockOwnFont = isPrimary ? getLockOwnerFont() : 0;

        if (lockClockFont == 0) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (lockClockFont == 1) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (lockClockFont == 2) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (lockClockFont == 3) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 4) {
            mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (lockClockFont == 5) {
            mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (lockClockFont == 6) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (lockClockFont == 7) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (lockClockFont == 8) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (lockClockFont == 9) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 10) {
            mClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (lockClockFont == 11) {
            mClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (lockClockFont == 12) {
            mClockView.setTypeface(Typeface.create("alien-league", Typeface.NORMAL));
        }
        if (lockClockFont == 13) {
            mClockView.setTypeface(Typeface.create("bignoodle-italic", Typeface.NORMAL));
        }
        if (lockClockFont == 14) {
            mClockView.setTypeface(Typeface.create("biko", Typeface.NORMAL));
        }
        if (lockClockFont == 15) {
            mClockView.setTypeface(Typeface.create("blern", Typeface.NORMAL));
        }
        if (lockClockFont == 16) {
            mClockView.setTypeface(Typeface.create("ginora-sans", Typeface.NORMAL));
        }
        if (lockClockFont == 17) {
            mClockView.setTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 18) {
            mClockView.setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 19) {
            mClockView.setTypeface(Typeface.create("inkferno", Typeface.NORMAL));
        }
        if (lockClockFont == 20) {
            mClockView.setTypeface(Typeface.create("metropolis1920", Typeface.NORMAL));
        }
        if (lockClockFont == 21) {
            mClockView.setTypeface(Typeface.create("neonneon", Typeface.NORMAL));
        }
        if (lockClockFont == 22) {
            mClockView.setTypeface(Typeface.create("riviera", Typeface.NORMAL));
        }
        if (lockClockFont == 23) {
            mClockView.setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 24) {
            mClockView.setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 25) {
            mClockView.setTypeface(Typeface.create("themeable-clock", Typeface.NORMAL));
        }
        if (lockClockFont == 26) {
            mClockView.setTypeface(Typeface.create("unionfont", Typeface.NORMAL));
        }

        // Lockscreen date

        if (lockDateFont == 0) {
            mDateView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (lockDateFont == 1) {
            mDateView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            mAlarmStatusView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (lockDateFont == 2) {
            mDateView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
            mAlarmStatusView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (lockDateFont == 3) {
            mDateView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
            mAlarmStatusView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (lockDateFont == 4) {
            mDateView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (lockDateFont == 5) {
            mDateView.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
            mAlarmStatusView.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (lockDateFont == 6) {
            mDateView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (lockDateFont == 7) {
            mDateView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
            mAlarmStatusView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (lockDateFont == 8) {
            mDateView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            mAlarmStatusView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (lockDateFont == 9) {
            mDateView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
            mAlarmStatusView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (lockDateFont == 10) {
            mDateView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (lockDateFont == 11) {
            mDateView.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
            mAlarmStatusView.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (lockDateFont == 12) {
            mDateView.setTypeface(Typeface.create("adamcg-pro", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("adamcg-pro", Typeface.NORMAL));
        }
        if (lockDateFont == 13) {
            mDateView.setTypeface(Typeface.create("alien-league", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("alien-league", Typeface.NORMAL));
        }
        if (lockDateFont == 14) {
            mDateView.setTypeface(Typeface.create("bignoodle-regular", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("bignoodle-regular", Typeface.NORMAL));
        }
        if (lockDateFont == 15) {
            mDateView.setTypeface(Typeface.create("biko", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("biko", Typeface.NORMAL));
        }
        if (lockDateFont == 16) {
            mDateView.setTypeface(Typeface.create("ginora-sans", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("ginora-sans", Typeface.NORMAL));
        }
        if (lockDateFont == 17) {
            mDateView.setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 18) {
            mDateView.setTypeface(Typeface.create("inkferno", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("inkferno", Typeface.NORMAL));
        }
        if (lockDateFont == 19) {
            mDateView.setTypeface(Typeface.create("instruction", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("instruction", Typeface.NORMAL));
        }
        if (lockDateFont == 20) {
            mDateView.setTypeface(Typeface.create("jack-lane", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("jack-lane", Typeface.NORMAL));
        }
        if (lockDateFont == 21) {
            mDateView.setTypeface(Typeface.create("monad", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("monad", Typeface.NORMAL));
        }
        if (lockDateFont == 22) {
            mDateView.setTypeface(Typeface.create("noir", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("noir", Typeface.NORMAL));
        }
        if (lockDateFont == 23) {
            mDateView.setTypeface(Typeface.create("outrun-future", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("outrun-future", Typeface.NORMAL));
        }
        if (lockDateFont == 24) {
            mDateView.setTypeface(Typeface.create("riviera", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("riviera", Typeface.NORMAL));
        }
        if (lockDateFont == 25) {
            mDateView.setTypeface(Typeface.create("the-outbox", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("the-outbox", Typeface.NORMAL));
        }
        if (lockDateFont == 26) {
            mDateView.setTypeface(Typeface.create("themeable-date", Typeface.NORMAL));
            mAlarmStatusView.setTypeface(Typeface.create("themeable-date", Typeface.NORMAL));
        }

       // Lockscreen owner info

        if (lockOwnFont == 0) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (lockOwnFont == 1) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (lockOwnFont == 2) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (lockOwnFont == 3) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (lockOwnFont == 4) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (lockOwnFont == 5) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (lockOwnFont == 6) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (lockOwnFont == 7) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (lockOwnFont == 8) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (lockOwnFont == 9) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (lockOwnFont == 10) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (lockOwnFont == 11) {
            mOwnerInfo.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (lockOwnFont == 12) {
            mOwnerInfo.setTypeface(Typeface.create("adamcg-pro", Typeface.NORMAL));
        }
        if (lockOwnFont == 13) {
            mOwnerInfo.setTypeface(Typeface.create("alexana-neue", Typeface.NORMAL));
        }
        if (lockOwnFont == 14) {
            mOwnerInfo.setTypeface(Typeface.create("alien-league", Typeface.NORMAL));
        }
        if (lockOwnFont == 15) {
            mOwnerInfo.setTypeface(Typeface.create("azedo-light", Typeface.NORMAL));
        }
        if (lockOwnFont == 16) {
            mOwnerInfo.setTypeface(Typeface.create("bignoodle-regular", Typeface.NORMAL));
        }
        if (lockOwnFont == 17) {
            mOwnerInfo.setTypeface(Typeface.create("biko", Typeface.NORMAL));
        }
        if (lockOwnFont == 18) {
            mOwnerInfo.setTypeface(Typeface.create("cocobiker", Typeface.NORMAL));
        }
        if (lockOwnFont == 19) {
            mOwnerInfo.setTypeface(Typeface.create("fester", Typeface.NORMAL));
        }
        if (lockOwnFont == 20) {
            mOwnerInfo.setTypeface(Typeface.create("ginora-sans", Typeface.NORMAL));
        }
        if (lockOwnFont == 21) {
            mOwnerInfo.setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
        }
        if (lockOwnFont == 22) {
            mOwnerInfo.setTypeface(Typeface.create("jacklane", Typeface.NORMAL));
        }
        if (lockOwnFont == 23) {
            mOwnerInfo.setTypeface(Typeface.create("monad", Typeface.NORMAL));
        }
        if (lockOwnFont == 24) {
            mOwnerInfo.setTypeface(Typeface.create("noir", Typeface.NORMAL));
        }
        if (lockOwnFont == 25) {
            mOwnerInfo.setTypeface(Typeface.create("northfont", Typeface.NORMAL));
        }
        if (lockOwnFont == 26) {
            mOwnerInfo.setTypeface(Typeface.create("qontra", Typeface.NORMAL));
        }
        if (lockOwnFont == 27) {
            mOwnerInfo.setTypeface(Typeface.create("the-outbox", Typeface.NORMAL));
        }
        if (lockOwnFont == 28) {
            mOwnerInfo.setTypeface(Typeface.create("themeable-owner", Typeface.NORMAL));
        }
        if (lockOwnFont == 29) {
            mOwnerInfo.setTypeface(Typeface.create("unionfont", Typeface.NORMAL));
        }
    }

    public void updateAll() {
        updateSettings();
        refresh();
    }

    // DateFormat.getBestDateTimePattern is extremely expensive, and refresh is called often.
    // This is an optimization to ensure we only recompute the patterns when the inputs change.
    private static final class Patterns {
        static String dateViewSkel;
        static String clockView12;
        static String clockView24;
        static String cacheKey;

        static void update(Context context, boolean hasAlarm) {
            final Locale locale = Locale.getDefault();
            final Resources res = context.getResources();

            dateViewSkel = res.getString(hasAlarm
                    ? R.string.abbrev_wday_month_day_no_year_alarm
                    : R.string.abbrev_wday_month_day_no_year);
            final String clockView12Skel = res.getString(R.string.clock_12hr_format);
            final String clockView24Skel = res.getString(R.string.clock_24hr_format);
            final String key = locale.toString() + dateViewSkel + clockView12Skel + clockView24Skel;
            if (key.equals(cacheKey)) return;

            clockView12 = DateFormat.getBestDateTimePattern(locale, clockView12Skel);
            // CLDR insists on adding an AM/PM indicator even though it wasn't in the skeleton
            // format.  The following code removes the AM/PM indicator if we didn't want it.
            if (!clockView12Skel.contains("a")) {
                clockView12 = clockView12.replaceAll("a", "").trim();
            }

            clockView24 = DateFormat.getBestDateTimePattern(locale, clockView24Skel);

            cacheKey = key;
        }
    }

    public void setDark(float darkAmount) {
        if (mDarkAmount == darkAmount) {
            return;
        }
        mDarkAmount = darkAmount;

        boolean dark = darkAmount == 1;
        final int N = mClockContainer.getChildCount();
        for (int i = 0; i < N; i++) {
            View child = mClockContainer.getChildAt(i);
            if (!mForcedMediaDoze && ArrayUtils.contains(mVisibleInDoze, child)) {
                continue;
            }
            child.setAlpha(dark ? 0 : 1);
        }
        if (mOwnerInfo != null) {
            mOwnerInfo.setAlpha(dark ? 0 : 1);
        }

        // Testing this kek
		MarginLayoutParams containerParams = (MarginLayoutParams) mClockContainer.getLayoutParams();
        int specialTopPadding = dark ? 90 : 0;
        containerParams.setMargins(0, specialTopPadding, 0, 0);
        mClockContainer.setLayoutParams(containerParams);
        updateDozeVisibleViews();
        //mBatteryDoze.setDark(dark);
        mClockView.setTextColor(ColorUtils.blendARGB(mTextColor, Color.WHITE, darkAmount));
        mDateView.setTextColor(ColorUtils.blendARGB(mDateTextColor, Color.WHITE, darkAmount));
        int blendedAlarmColor = ColorUtils.blendARGB(mAlarmTextColor, Color.WHITE, darkAmount);
        mAlarmStatusView.setTextColor(blendedAlarmColor);
        mAlarmStatusView.setCompoundDrawableTintList(ColorStateList.valueOf(blendedAlarmColor));
        mAnalogClockView.setDark(dark);
        mDeadPoolClockView.setDark(dark);
        updateVisibilities();
    }

    public void setPulsing(boolean pulsing) {
        mPulsing = pulsing;
    }

    public void setCleanLayout(int reason) {
        mForcedMediaDoze =
                reason == DozeLog.PULSE_REASON_FORCED_MEDIA_NOTIFICATION;
        updateDozeVisibleViews();
    }

    private void updateDozeVisibleViews() {
        for (View child : mVisibleInDoze) {
            if (!mForcedMediaDoze) {
                child.setAlpha(mDarkAmount == 1 && mPulsing ? 0.8f : 1);
            } else {
                child.setAlpha(mDarkAmount == 1 ? 0 : 1);
            }
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
           ContentResolver resolver = mContext.getContentResolver();
           resolver.registerContentObserver(Settings.System.getUriFor(
                   Settings.System.LOCK_SCREEN_WEATHER_CONDITION_ICON), false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                   Settings.System.LOCK_SCREEN_SHOW_WEATHER), false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                  Settings.System.OMNIJAWS_WEATHER_ICON_PACK), false, this, UserHandle.USER_ALL);
           resolver.registerContentObserver(Settings.System.getUriFor(
                  Settings.System.LOCK_SCREEN_SHOW_WEATHER_LOCATION), false, this, UserHandle.USER_ALL);

           update();
        }

        void unobserve() {
           ContentResolver resolver = mContext.getContentResolver();
           resolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
           if (uri.equals(Settings.System.getUriFor(
                   Settings.System.LOCK_SCREEN_SHOW_WEATHER))) {
               updateWeatherSettings(false);
           } else if (uri.equals(Settings.System.getUriFor(
                   Settings.System.LOCK_SCREEN_WEATHER_CONDITION_ICON))) {
               updateWeatherSettings(false);
           } else if (uri.equals(Settings.System.getUriFor(
                   Settings.System.OMNIJAWS_WEATHER_ICON_PACK))) {
               queryAndUpdateWeather();
           }  else if (uri.equals(Settings.System.getUriFor(
                   Settings.System.LOCK_SCREEN_SHOW_WEATHER_LOCATION))) {
               updateWeatherSettings(false);
           }
           update();
        }

         public void update() {
           ContentResolver resolver = mContext.getContentResolver();
           int currentUserId = ActivityManager.getCurrentUser();

           mShowWeather = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_SHOW_WEATHER, 0) == 1;
           mShowConditionIcon = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_WEATHER_CONDITION_ICON, 1) == 1;
           mShowLocation = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_SHOW_WEATHER_LOCATION, 1) == 1;

           updateWeatherSettings(false);
         }
    }
}
