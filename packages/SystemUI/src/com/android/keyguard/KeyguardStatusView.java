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
import android.app.IActivityManager;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.Dependency;
import com.android.systemui.omni.CurrentWeatherView;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.ConfigurationController;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.TimeZone;

public class KeyguardStatusView extends GridLayout implements
        ConfigurationController.ConfigurationListener {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "KeyguardStatusView";
    private static final int MARQUEE_DELAY_MS = 2000;

    private final LockPatternUtils mLockPatternUtils;
    private final IActivityManager mIActivityManager;

    private LinearLayout mStatusViewContainer;
    private TextView mLogoutView;
    private KeyguardClockSwitch mClockView;
    private TextView mOwnerInfo;
    private KeyguardSliceView mKeyguardSlice;
    private View mNotificationIcons;
    private Runnable mPendingMarqueeStart;
    private Handler mHandler;

    private boolean mPulsing;
    private float mDarkAmount = 0;
    private int mTextColor;
    private CurrentWeatherView mWeatherView;
    private boolean mShowWeather;
    private boolean mOmniStyle;

    /**
     * Bottom margin that defines the margin between bottom of smart space and top of notification
     * icons on AOD.
     */
    private int mIconTopMargin;
    private int mIconTopMarginWithHeader;
    private boolean mShowingHeader;

    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onTimeChanged() {
            refreshTime();
            refreshLockFont();
            refreshLockDateFont();
        }

        @Override
        public void onTimeZoneChanged(TimeZone timeZone) {
            updateTimeZone(timeZone);
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            if (showing) {
                if (DEBUG) Slog.v(TAG, "refresh statusview showing:" + showing);
                refreshTime();
                updateOwnerInfo();
                updateLogoutView();
                updateSettings();
                refreshLockDateFont();
            }
        }

        @Override
        public void onStartedWakingUp() {
            setEnableMarquee(true);
        }

        @Override
        public void onFinishedGoingToSleep(int why) {
            setEnableMarquee(false);
        }

        @Override
        public void onUserSwitchComplete(int userId) {
            refreshFormat();
            updateOwnerInfo();
            updateLogoutView();
            updateSettings();
            refreshLockDateFont();
        }

        @Override
        public void onLogoutEnabledChanged() {
            updateLogoutView();
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
        mIActivityManager = ActivityManager.getService();
        mLockPatternUtils = new LockPatternUtils(getContext());
        mHandler = new Handler(Looper.myLooper());
        onDensityOrFontScaleChanged();
    }

    /**
     * If we're presenting a custom clock of just the default one.
     */
    public boolean hasCustomClock() {
        return mClockView.hasCustomClock();
    }

    private int getLockClockFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_CLOCK_FONT_STYLE, 23);
    }

    /**
     * Set whether or not the lock screen is showing notifications.
     */
    public void setHasVisibleNotifications(boolean hasVisibleNotifications) {
        mClockView.setHasVisibleNotifications(hasVisibleNotifications);
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
        if (mOwnerInfo != null) mOwnerInfo.setSelected(enabled);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mStatusViewContainer = findViewById(R.id.status_view_container);
        mLogoutView = findViewById(R.id.logout);
        mNotificationIcons = findViewById(R.id.clock_notification_icon_container);
        if (mLogoutView != null) {
            mLogoutView.setOnClickListener(this::onLogoutClicked);
        }

        mClockView = findViewById(R.id.keyguard_clock_container);
        mClockView.setShowCurrentUserTime(true);
        mOwnerInfo = findViewById(R.id.owner_info);
        mKeyguardSlice = findViewById(R.id.keyguard_status_area);

        mWeatherView = (CurrentWeatherView) findViewById(R.id.weather_container);
        updateSettings();

        mTextColor = mClockView.getCurrentTextColor();

        refreshLockDateFont();
        mKeyguardSlice.setContentChangeListener(this::onSliceContentChanged);
        onSliceContentChanged();

        boolean shouldMarquee = KeyguardUpdateMonitor.getInstance(mContext).isDeviceInteractive();
        setEnableMarquee(shouldMarquee);
        refreshFormat();
        updateOwnerInfo();
        updateLogoutView();
        updateDark();
        updateSettings();

    }

    public KeyguardSliceView getKeyguardSliceView() {
        return mKeyguardSlice;
    }

    /**
     * Moves clock, adjusting margins when slice content changes.
     */
    private void onSliceContentChanged() {
        final boolean hasHeader = mKeyguardSlice.hasHeader();
        mClockView.setKeyguardShowingHeader(hasHeader);
        if (mShowingHeader == hasHeader) {
            return;
        }
        mShowingHeader = hasHeader;
        if (mNotificationIcons != null) {
            // Update top margin since header has appeared/disappeared.
            MarginLayoutParams params = (MarginLayoutParams) mNotificationIcons.getLayoutParams();
            params.setMargins(params.leftMargin,
                    hasHeader ? mIconTopMarginWithHeader : mIconTopMargin,
                    params.rightMargin,
                    params.bottomMargin);
            mNotificationIcons.setLayoutParams(params);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        layoutOwnerInfo();
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        if (mClockView != null) {
            mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
            refreshLockFont();
            refreshLockDateFont();
        }
        if (mOwnerInfo != null) {
            mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));
        }
        if (mWeatherView != null) {
            mWeatherView.onDensityOrFontScaleChanged();
        }
        loadBottomMargin();
    }

    public void dozeTimeTick() {
        refreshTime();
        mKeyguardSlice.refresh();
    }

    private void refreshTime() {
        mClockView.refresh();
    }

    private void updateTimeZone(TimeZone timeZone) {
        mClockView.onTimeZoneChanged(timeZone);
    }

    private int getLockDateFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_DATE_FONTS, 1);
    }

    private void refreshFormat() {
        Patterns.update(mContext);
        mClockView.setFormat12Hour(Patterns.clockView12);
        mClockView.setFormat24Hour(Patterns.clockView24);
    }

    public int getLogoutButtonHeight() {
        if (mLogoutView == null) {
            return 0;
        }
        return mLogoutView.getVisibility() == VISIBLE ? mLogoutView.getHeight() : 0;
    }

    public float getClockTextSize() {
        return mClockView.getTextSize();
    }

    private void refreshLockDateFont() {
        setLockDateFont(mKeyguardSlice, getLockClockFont());
    }

    private void setLockDateFont(KeyguardSliceView view, int fontStyle) {
        if (view != null) {
            switch (fontstyle) {
                case 0:
                default:
                    view.setViewsTypeface(Typeface.create(mContext.getResources().getString(R.string.clock_sysfont_headline), Typeface.NORMAL));
                    break;
                case 1:
                    view.setViewsTypeface(Typeface.create(mContext.getResources().getString(R.string.clock_sysfont_body), Typeface.NORMAL));
                    break;
                case 2:
                    view.setViewsTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                    break;
                case 3:
                    view.setViewsTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                    break;
                case 4:
                    view.setViewsTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                    break;
                case 5:
                    view.setViewsTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                    break;
                case 6:
                    view.setViewsTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                    break;
                case 7:
                    view.setViewsTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                    break;
                case 8:
                    view.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                    break;
                case 9:
                    view.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                    break;
                case 10:
                    view.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                    break;
                case 11:
                    view.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                    break;
                case 12:
                    view.setViewsTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    break;
                case 13:
                    view.setViewsTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                    break;
                case 14:
                    view.setViewsTypeface(Typeface.create("abelreg", Typeface.NORMAL));
                    break;
                case 15:
                    view.setViewsTypeface(Typeface.create("adamcg-pro", Typeface.NORMAL));
                    break;
                case 16:
                    view.setViewsTypeface(Typeface.create("adventpro", Typeface.NORMAL));
                    break;
                case 17:
                    view.setViewsTypeface(Typeface.create("alien-league", Typeface.NORMAL));
                    break;
                case 18:
                    view.setViewsTypeface(Typeface.create("archivonar", Typeface.NORMAL));
                    break;
                case 19:
                    view.setViewsTypeface(Typeface.create("autourone", Typeface.NORMAL));
                    break;
                case 20:
                    view.setViewsTypeface(Typeface.create("badscript", Typeface.NORMAL));
                    break;
                case 21:
                    view.setViewsTypeface(Typeface.create("bignoodle-regular", Typeface.NORMAL));
                    break;
                case 22:
                    view.setViewsTypeface(Typeface.create("biko", Typeface.NORMAL));
                    break;
                case 23:
                    view.setViewsTypeface(Typeface.create("cherryswash", Typeface.NORMAL));
                    break;
                case 24:
                    view.setViewsTypeface(Typeface.create("ginora-sans", Typeface.NORMAL));
                    break;
                case 25:
                    view.setViewsTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
                    break;
                case 26:
                    view.setViewsTypeface(Typeface.create("ibmplex-mono", Typeface.NORMAL));
                    break;
                case 27:
                    view.setViewsTypeface(Typeface.create("inkferno", Typeface.NORMAL));
                    break;
                case 28:
                    view.setViewsTypeface(Typeface.create("instruction", Typeface.NORMAL));
                    break;
                case 29:
                    view.setViewsTypeface(Typeface.create("jack-lane", Typeface.NORMAL));
                    break;
                case 30:
                    view.setViewsTypeface(Typeface.create("kellyslab", Typeface.NORMAL));
                    break;
                case 31:
                    view.setViewsTypeface(Typeface.create("monad", Typeface.NORMAL));
                    break;
                case 32:
                    view.setViewsTypeface(Typeface.create("noir", Typeface.NORMAL));
                    break;
                case 33:
                    view.setViewsTypeface(Typeface.create("outrun-future", Typeface.NORMAL));
                    break;
                case 34:
                    view.setViewsTypeface(Typeface.create("pompiere", Typeface.NORMAL));
                    break;
                case 35:
                    view.setViewsTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
                    break;
                case 36:
                    view.setViewsTypeface(Typeface.create("riviera", Typeface.NORMAL));
                    break;
                case 37:
                    view.setViewsTypeface(Typeface.create("the-outbox", Typeface.NORMAL));
                    break;
                case 38:
                    view.setViewsTypeface(Typeface.create("themeable-date", Typeface.NORMAL));
                    break;
                case 39:
                    view.setViewsTypeface(Typeface.create("vibur", Typeface.NORMAL));
                    break;
                case 40:
                    view.setViewsTypeface(Typeface.create("voltaire", Typeface.NORMAL));
                    break;
            }
        }
    }

    /**
     * Returns the preferred Y position of the clock.
     *
     * @param totalHeight The height available to position the clock.
     * @return Y position of clock.
     */
    public int getClockPreferredY(int totalHeight) {
        return mClockView.getPreferredY(totalHeight);
    }

    private void updateLogoutView() {
        if (mLogoutView == null) {
            return;
        }
        mLogoutView.setVisibility(shouldShowLogout() ? VISIBLE : GONE);
        // Logout button will stay in language of user 0 if we don't set that manually.
        mLogoutView.setText(mContext.getResources().getString(
                com.android.internal.R.string.global_action_logout));
    }

    private void updateOwnerInfo() {
        if (mOwnerInfo == null) return;
        String info = mLockPatternUtils.getDeviceOwnerInfo();
        if (info == null) {
            // Use the current user owner information if enabled.
            final boolean ownerInfoEnabled = mLockPatternUtils.isOwnerInfoEnabled(
                    KeyguardUpdateMonitor.getCurrentUser());
            if (ownerInfoEnabled) {
                info = mLockPatternUtils.getOwnerInfo(KeyguardUpdateMonitor.getCurrentUser());
            }
        }
        mOwnerInfo.setText(info);
        updateDark();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
        Dependency.get(ConfigurationController.class).addCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
        Dependency.get(ConfigurationController.class).removeCallback(this);
    }

    @Override
    public void onLocaleListChanged() {
        refreshFormat();
    }

    private void refreshLockFont() {
		setFontStyle(mClockView, getLockClockFont());
    }

    private void setFontStyle(KeyguardClockSwitch view, int fontstyle) {
    	if (view != null) {
    		switch (fontstyle) {
    			case 0:
    			default:
    				view.setTextFont(Typeface.create(mContext.getResources().getString(R.string.clock_sysfont_headline_medium), Typeface.NORMAL));
    				break;
    			case 1:
    				view.setTextFont(Typeface.create(mContext.getResources().getString(R.string.clock_sysfont_body_medium), Typeface.NORMAL));
    				break;
    			case 2:
    				view.setTextFont(Typeface.create("sans-serif", Typeface.BOLD));
    				break;
    			case 3:
    				view.setTextFont(Typeface.create("sans-serif", Typeface.NORMAL));
    				break;
    			case 4:
    				view.setTextFont(Typeface.create("sans-serif", Typeface.ITALIC));
    				break;
    			case 5:
    				view.setTextFont(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
    				break;
    			case 6:
    				view.setTextFont(Typeface.create("sans-serif-light", Typeface.NORMAL));
    				break;
    			case 7:
    				view.setTextFont(Typeface.create("sans-serif-thin", Typeface.NORMAL));
    				break;
    			case 8:
    				view.setTextFont(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
    				break;
    			case 9:
    				view.setTextFont(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
    				break;
    			case 10:
    				view.setTextFont(Typeface.create("sans-serif-condensed", Typeface.BOLD));
    				break;
    			case 11:
    				view.setTextFont(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
    				break;
    			case 12:
    				view.setTextFont(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    				break;
    			case 13:
    				view.setTextFont(Typeface.create("sans-serif-medium", Typeface.ITALIC));
    				break;
                case 14:
                    view.setTextFont(Typeface.create("abelreg", Typeface.NORMAL));
                    break;
                case 15:
                    view.setTextFont(Typeface.create("adventpro", Typeface.NORMAL));
                    break;
                case 16:
                    view.setTextFont(Typeface.create("alien-league", Typeface.NORMAL));
                    break;
                case 17:
                    view.setTextFont(Typeface.create("bignoodle-italic", Typeface.NORMAL));
                    break;
                case 18:
                    view.setTextFont(Typeface.create("biko", Typeface.NORMAL));
                    break;
                case 19:
                    view.setTextFont(Typeface.create("blern", Typeface.NORMAL));
                    break;
                case 20:
                    view.setTextFont(Typeface.create("cherryswash", Typeface.NORMAL));
                    break;
                case 21:
                    view.setTextFont(Typeface.create("codystar", Typeface.NORMAL));
                    break;
                case 22:
                    view.setTextFont(Typeface.create("ginora-sans", Typeface.NORMAL));
                    break;
                case 23:
                    view.setTextFont(Typeface.create("gobold-light-sys", Typeface.NORMAL));
                    break;
                case 24:
                    view.setTextFont(Typeface.create("googlesans-sys", Typeface.NORMAL));
                    break;
                case 25:
                    view.setTextFont(Typeface.create("inkferno", Typeface.NORMAL));
                    break;
                case 26:
                    view.setTextFont(Typeface.create("jura-reg", Typeface.NORMAL));
                    break;
                case 27:
                    view.setTextFont(Typeface.create("kellyslab", Typeface.NORMAL));
                    break;
                case 28:
                    view.setTextFont(Typeface.create("metropolis1920", Typeface.NORMAL));
                    break;
                case 29:
                    view.setTextFont(Typeface.create("neonneon", Typeface.NORMAL));
                    break;
                case 30:
                    view.setTextFont(Typeface.create("pompiere", Typeface.NORMAL));
                    break;
                case 31:
                    view.setTextFont(Typeface.create("reemkufi", Typeface.NORMAL));
                    break;
                case 32:
                    view.setTextFont(Typeface.create("riviera", Typeface.NORMAL));
                    break;
                case 33:
                    view.setTextFont(Typeface.create("roadrage-sys", Typeface.NORMAL));
                    break;
                case 34:
                    view.setTextFont(Typeface.create("sedgwick-ave", Typeface.NORMAL));
                    break;
                case 35:
                    view.setTextFont(Typeface.create("snowstorm-sys", Typeface.NORMAL));
                    break;
                case 36:
                    view.setTextFont(Typeface.create("themeable-clock", Typeface.NORMAL));
                    break;
                case 37:
                    view.setTextFont(Typeface.create("unionfont", Typeface.NORMAL));
                    break;
                case 38:
                    view.setTextFont(Typeface.create("vibur", Typeface.NORMAL));
                    break;
                case 39:
                    view.setTextFont(Typeface.create("voltaire", Typeface.NORMAL));
                    break;
    		}
    	}
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("KeyguardStatusView:");
        pw.println("  mOwnerInfo: " + (mOwnerInfo == null
                ? "null" : mOwnerInfo.getVisibility() == VISIBLE));
        pw.println("  mPulsing: " + mPulsing);
        pw.println("  mDarkAmount: " + mDarkAmount);
        pw.println("  mTextColor: " + Integer.toHexString(mTextColor));
        if (mLogoutView != null) {
            pw.println("  logout visible: " + (mLogoutView.getVisibility() == VISIBLE));
        }
        if (mClockView != null) {
            mClockView.dump(fd, pw, args);
        }
        if (mKeyguardSlice != null) {
            mKeyguardSlice.dump(fd, pw, args);
        }
    }

    private void loadBottomMargin() {
        mIconTopMargin = getResources().getDimensionPixelSize(R.dimen.widget_vertical_padding);
        mIconTopMarginWithHeader = getResources().getDimensionPixelSize(
                R.dimen.widget_vertical_padding_with_header);
    }

    // DateFormat.getBestDateTimePattern is extremely expensive, and refresh is called often.
    // This is an optimization to ensure we only recompute the patterns when the inputs change.
    private static final class Patterns {
        static String clockView12;
        static String clockView24;
        static String cacheKey;

        static void update(Context context) {
            final Locale locale = Locale.getDefault();
            final Resources res = context.getResources();
            final String clockView12Skel = res.getString(R.string.clock_12hr_format);
            final String clockView24Skel = res.getString(R.string.clock_24hr_format);
            final String key = locale.toString() + clockView12Skel + clockView24Skel;
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

    public void setDarkAmount(float darkAmount) {
        if (mDarkAmount == darkAmount) {
            return;
        }
        mDarkAmount = darkAmount;
        mClockView.setDarkAmount(darkAmount);
        updateDark();
    }

    private void updateDark() {
        boolean dark = mDarkAmount == 1;
        if (mLogoutView != null) {
            mLogoutView.setAlpha(dark ? 0 : 1);
        }

        if (mOwnerInfo != null) {
            boolean hasText = !TextUtils.isEmpty(mOwnerInfo.getText());
            mOwnerInfo.setVisibility(hasText ? VISIBLE : GONE);
            layoutOwnerInfo();
        }

        final int blendedTextColor = ColorUtils.blendARGB(mTextColor, Color.WHITE, mDarkAmount);
        mKeyguardSlice.setDarkAmount(mDarkAmount);
        mClockView.setTextColor(blendedTextColor);
    }

    private void layoutOwnerInfo() {
        if (mOwnerInfo != null && mOwnerInfo.getVisibility() != GONE) {
            // Animate owner info during wake-up transition
            mOwnerInfo.setAlpha(1f - mDarkAmount);

            float ratio = mDarkAmount;
            // Calculate how much of it we should crop in order to have a smooth transition
            int collapsed = mOwnerInfo.getTop() - mOwnerInfo.getPaddingTop();
            int expanded = mOwnerInfo.getBottom() + mOwnerInfo.getPaddingBottom();
            int toRemove = (int) ((expanded - collapsed) * ratio);
            setBottom(getMeasuredHeight() - toRemove);
            if (mNotificationIcons != null) {
                // We're using scrolling in order not to overload the translation which is used
                // when appearing the icons
                mNotificationIcons.setScrollY(toRemove);
            }
        } else if (mNotificationIcons != null){
            mNotificationIcons.setScrollY(0);
        }
    }

    public void setPulsing(boolean pulsing) {
        if (mPulsing == pulsing) {
            return;
        }
        mPulsing = pulsing;
    }

    private boolean shouldShowLogout() {
        return KeyguardUpdateMonitor.getInstance(mContext).isLogoutEnabled()
                && KeyguardUpdateMonitor.getCurrentUser() != UserHandle.USER_SYSTEM;
    }

    private void onLogoutClicked(View view) {
        int currentUserId = KeyguardUpdateMonitor.getCurrentUser();
        try {
            mIActivityManager.switchUser(UserHandle.USER_SYSTEM);
            mIActivityManager.stopUser(currentUserId, true /*force*/, null);
        } catch (RemoteException re) {
            Log.e(TAG, "Failed to logout user", re);
        }
    }

    private void updateSettings() {
        final ContentResolver resolver = getContext().getContentResolver();
        final Resources res = getContext().getResources();
        mShowWeather = Settings.System.getIntForUser(resolver,
                Settings.System.OMNI_LOCKSCREEN_WEATHER_ENABLED, 0,
                UserHandle.USER_CURRENT) == 1;

        mOmniStyle = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_WEATHER_STYLE, 1,
                UserHandle.USER_CURRENT) == 0;

        if (mWeatherView != null) {
            if (mShowWeather && mOmniStyle) {
                mWeatherView.setVisibility(View.VISIBLE);
                mWeatherView.enableUpdates();
            }
            if (!mShowWeather || !mOmniStyle) {
                mWeatherView.setVisibility(View.GONE);
                mWeatherView.disableUpdates();
            }
        }
    }

}
