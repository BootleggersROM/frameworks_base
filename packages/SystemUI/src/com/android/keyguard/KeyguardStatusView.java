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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v4.graphics.ColorUtils;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CustomAnalogClock;
import android.widget.GridLayout;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.graphics.Typeface;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.ViewClippingUtil;
import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.doze.DozeLog;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.wakelock.KeepAwakeAnimationListener;

import com.google.android.collect.Sets;

import java.util.Locale;

public class KeyguardStatusView extends GridLayout implements
        ConfigurationController.ConfigurationListener, View.OnLayoutChangeListener {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "KeyguardStatusView";
    private static final int MARQUEE_DELAY_MS = 2000;

    private final LockPatternUtils mLockPatternUtils;
    private final IActivityManager mIActivityManager;
    private final float mSmallClockScale;

    private TextView mLogoutView;
    private CustomAnalogClock mAnalogClockView;
    private TextClock mClockView;
    private View mClockSeparator;
    private TextView mOwnerInfo;
    private KeyguardSliceView mKeyguardSlice;
    private View mKeyguardSliceView;
    private Runnable mPendingMarqueeStart;
    private Handler mHandler;

    private ArraySet<View> mVisibleInDoze;
    private boolean mPulsing;
    private boolean mWasPulsing;
    private float mDarkAmount = 0;
    private int mTextColor;
    private float mWidgetPadding;
    private int mLastLayoutHeight;

    private boolean mForcedMediaDoze;

    private boolean mShowClock;
    private int mClockSelection;

    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onTimeChanged() {
            refreshTime();
            refreshLockFont();
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            if (showing) {
                if (DEBUG) Slog.v(TAG, "refresh statusview showing:" + showing);
                refreshTime();
                updateOwnerInfo();
                updateLogoutView();
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
        mSmallClockScale = getResources().getDimension(R.dimen.widget_small_font_size)
                / getResources().getDimension(R.dimen.widget_big_font_size);

        onDensityOrFontScaleChanged();
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
        mLogoutView = findViewById(R.id.logout);
        if (mLogoutView != null) {
            mLogoutView.setOnClickListener(this::onLogoutClicked);
        }

        mClockView = findViewById(R.id.clock_view);
        mClockView.setShowCurrentUserTime(true);
        if (KeyguardClockAccessibilityDelegate.isNeeded(mContext)) {
            mClockView.setAccessibilityDelegate(new KeyguardClockAccessibilityDelegate(mContext));
        }
        mAnalogClockView = findViewById(R.id.analog_clock_view);
        mOwnerInfo = findViewById(R.id.owner_info);
        mKeyguardSlice = findViewById(R.id.keyguard_status_area);
        mKeyguardSliceView = findViewById(R.id.keyguard_status_area);
        mClockSeparator = findViewById(R.id.clock_separator);
        mVisibleInDoze = Sets.newArraySet(mClockView, mKeyguardSlice, mAnalogClockView);
        mTextColor = mClockView.getCurrentTextColor();

        int clockStroke = getResources().getDimensionPixelSize(R.dimen.widget_small_font_stroke);
        mClockView.getPaint().setStrokeWidth(clockStroke);
        mClockView.addOnLayoutChangeListener(this);
        mClockSeparator.addOnLayoutChangeListener(this);
        mKeyguardSlice.setContentChangeListener(this::onSliceContentChanged);
        onSliceContentChanged();

        updateSettings();

        boolean shouldMarquee = KeyguardUpdateMonitor.getInstance(mContext).isDeviceInteractive();
        setEnableMarquee(shouldMarquee);
        refreshFormat();
        updateOwnerInfo();
        updateLogoutView();
        updateDark();

        // Disable elegant text height because our fancy colon makes the ymin value huge for no
        // reason.
        mClockView.setElegantTextHeight(false);
    }

    /**
     * Moves clock and separator, adjusting margins when slice content changes.
     */
    private void onSliceContentChanged() {
        boolean smallClock = mKeyguardSlice.hasHeader() || mPulsing;
        float clockScale = smallClock ? mSmallClockScale : 1;

        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) mClockView.getLayoutParams();
        int height = mClockView.getHeight();
        layoutParams.bottomMargin = (int) -(height - (clockScale * height));
        mClockView.setLayoutParams(layoutParams);

        // Custom analog clock
        RelativeLayout.LayoutParams customlayoutParams =
                (RelativeLayout.LayoutParams) mAnalogClockView.getLayoutParams();
        customlayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.bottom_text_spacing_digital);
        mAnalogClockView.setLayoutParams(customlayoutParams);

        layoutParams = (RelativeLayout.LayoutParams) mClockSeparator.getLayoutParams();
        layoutParams.topMargin = smallClock ? (int) mWidgetPadding : 0;
        layoutParams.bottomMargin = layoutParams.topMargin;
        mClockSeparator.setLayoutParams(layoutParams);
    }

    private int getLockClockFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_CLOCK_FONTS, 21);
    }

    private int getLockDateFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_DATE_FONTS, 4);
    }

    private int getLockOwnerFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_OWNER_FONTS, 4);
    }
    /**
     * Animate clock and its separator when necessary.
     */
    @Override
    public void onLayoutChange(View view, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
        int heightOffset = mPulsing || mWasPulsing ? 0 : getHeight() - mLastLayoutHeight;
        boolean hasHeader = mKeyguardSlice.hasHeader();
        boolean smallClock = hasHeader || mPulsing;
        long duration = KeyguardSliceView.DEFAULT_ANIM_DURATION;
        long delay = smallClock || mWasPulsing ? 0 : duration / 4;
        mWasPulsing = false;

        boolean shouldAnimate = mKeyguardSlice.getLayoutTransition() != null
                && mKeyguardSlice.getLayoutTransition().isRunning();
        if (view == mClockView) {
            float clockScale = smallClock ? mSmallClockScale : 1;
            Paint.Style style = smallClock ? Paint.Style.FILL_AND_STROKE : Paint.Style.FILL;
            mClockView.animate().cancel();	
            if (shouldAnimate) {
                mClockView.setY(oldTop + heightOffset);
                mClockView.animate()
                        .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                        .setDuration(duration)
                        .setListener(new ClipChildrenAnimationListener())
                        .setStartDelay(delay)
                        .y(top)
                        .scaleX(clockScale)
                        .scaleY(clockScale)
                        .withEndAction(() -> {
                            mClockView.getPaint().setStyle(style);
                            mClockView.invalidate();
                        })
                        .start();
            } else {
                mClockView.setY(top);
                mClockView.setScaleX(clockScale);
                mClockView.setScaleY(clockScale);
                mClockView.getPaint().setStyle(style);
                mClockView.invalidate();
            }
        } else if (view == mClockSeparator) {
            boolean hasSeparator = hasHeader && !mPulsing;
            float alpha = hasSeparator ? 1 : 0;
            mClockSeparator.animate().cancel();
            if (shouldAnimate) {
                boolean isAwake = mDarkAmount != 0;
                mClockSeparator.setY(oldTop + heightOffset);
                mClockSeparator.animate()
                        .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                        .setDuration(duration)
                        .setListener(isAwake ? null : new KeepAwakeAnimationListener(getContext()))
                        .setStartDelay(delay)
                        .y(top)
                        .alpha(alpha)
                        .start();
            } else {
                mClockSeparator.setY(top);
                mClockSeparator.setAlpha(alpha);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mClockView.setPivotX(mClockView.getWidth() / 2);
        mClockView.setPivotY(0);
        mLastLayoutHeight = getHeight();
        layoutOwnerInfo();
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        mWidgetPadding = getResources().getDimension(R.dimen.widget_vertical_padding);
        if (mClockView != null) {
            mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
            mClockView.getPaint().setStrokeWidth(
                    getResources().getDimensionPixelSize(R.dimen.widget_small_font_stroke));
        }
        if (mOwnerInfo != null) {
            mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));
        }
    }

    public void dozeTimeTick() {
        refreshTime();
        mKeyguardSlice.refresh();
        refreshLockFont();
    }

    private void refreshTime() {
        mClockView.refresh();

        if (mClockSelection == 0) {
            mClockView.setFormat12Hour(Patterns.clockView12);
            mClockView.setFormat24Hour(Patterns.clockView24);
        } else if (mClockSelection == 1) {
            mClockView.setFormat12Hour(Html.fromHtml("<strong>hh</strong>mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong>mm"));
        } else if (mClockSelection == 4) {
            mClockView.setFormat12Hour(Html.fromHtml("<strong>hh</strong><br>mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong><br>mm"));
        } else {
            mClockView.setFormat12Hour("hh\nmm");
            mClockView.setFormat24Hour("kk\nmm");
        }
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

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void updateVisibilities() {
        switch (mClockSelection) {
            case 0: // default digital
            default:
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mAnalogClockView.setVisibility(View.GONE);
                break;
            case 1: // digital (bold)
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mAnalogClockView.setVisibility(View.GONE);
                break;
            case 2: // analog
                mAnalogClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mClockView.setVisibility(View.GONE);
                break;
            case 3: // sammy
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mAnalogClockView.setVisibility(View.GONE);
                break;
            case 4: // sammy (bold)
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
                mAnalogClockView.setVisibility(View.GONE);
                break;
        }
    }

    private void updateSettings() {
        final ContentResolver resolver = getContext().getContentResolver();

        mShowClock = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_CLOCK, 1, UserHandle.USER_CURRENT) == 1;
        mClockSelection = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_CLOCK_SELECTION, 0, UserHandle.USER_CURRENT);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                mKeyguardSlice.getLayoutParams();
        switch (mClockSelection) {
            case 0: // default digital
            default:
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(true);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 1: // digital (bold)
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(true);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 2: // analog
                mClockView.setVisibility(View.GONE);
                params.addRule(RelativeLayout.BELOW, R.id.analog_clock_view);
                break;
            case 3: // sammy
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(false);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 4: // sammy (bold)
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(false);
                mClockView.setGravity(Gravity.CENTER);
                break;
        }

        updateVisibilities();
        updateDozeVisibleViews();
    }

    public void updateAll() {
        updateSettings();
        mKeyguardSlice.refresh();
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
            mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (lockClockFont == 5) {
            mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
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
            mClockView.setTypeface(Typeface.create("abelreg", Typeface.NORMAL));
        }
        if (lockClockFont == 13) {
            mClockView.setTypeface(Typeface.create("adventpro", Typeface.NORMAL));
        }
        if (lockClockFont == 14) {
            mClockView.setTypeface(Typeface.create("alien-league", Typeface.NORMAL));
        }
        if (lockClockFont == 15) {
            mClockView.setTypeface(Typeface.create("bignoodle-italic", Typeface.NORMAL));
        }
        if (lockClockFont == 16) {
            mClockView.setTypeface(Typeface.create("biko", Typeface.NORMAL));
        }
        if (lockClockFont == 17) {
            mClockView.setTypeface(Typeface.create("blern", Typeface.NORMAL));
        }
        if (lockClockFont == 18) {
            mClockView.setTypeface(Typeface.create("cherryswash", Typeface.NORMAL));
        }
        if (lockClockFont == 19) {
            mClockView.setTypeface(Typeface.create("codystar", Typeface.NORMAL));
        }
        if (lockClockFont == 20) {
            mClockView.setTypeface(Typeface.create("ginora-sans", Typeface.NORMAL));
        }
        if (lockClockFont == 21) {
            mClockView.setTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 22) {
            mClockView.setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 23) {
            mClockView.setTypeface(Typeface.create("inkferno", Typeface.NORMAL));
        }
        if (lockClockFont == 24) {
            mClockView.setTypeface(Typeface.create("jura-reg", Typeface.NORMAL));
        }
        if (lockClockFont == 25) {
            mClockView.setTypeface(Typeface.create("kellyslab", Typeface.NORMAL));
        }
        if (lockClockFont == 26) {
            mClockView.setTypeface(Typeface.create("metropolis1920", Typeface.NORMAL));
        }
        if (lockClockFont == 27) {
            mClockView.setTypeface(Typeface.create("neonneon", Typeface.NORMAL));
        }
        if (lockClockFont == 28) {
            mClockView.setTypeface(Typeface.create("pompiere", Typeface.NORMAL));
        }
        if (lockClockFont == 29) {
            mClockView.setTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
        }
        if (lockClockFont == 30) {
            mClockView.setTypeface(Typeface.create("riviera", Typeface.NORMAL));
        }
        if (lockClockFont == 31) {
            mClockView.setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 32) {
            mClockView.setTypeface(Typeface.create("sedgwick-ave", Typeface.NORMAL));
        }
        if (lockClockFont == 33) {
            mClockView.setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 34) {
            mClockView.setTypeface(Typeface.create("themeable-clock", Typeface.NORMAL));
        }
        if (lockClockFont == 35) {
            mClockView.setTypeface(Typeface.create("unionfont", Typeface.NORMAL));
        }
        if (lockClockFont == 36) {
            mClockView.setTypeface(Typeface.create("vibur", Typeface.NORMAL));
        }
        if (lockClockFont == 37) {
            mClockView.setTypeface(Typeface.create("voltaire", Typeface.NORMAL));
        }
        
        // Lockscreen date

        if (lockDateFont == 0) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (lockDateFont == 1) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (lockDateFont == 2) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (lockDateFont == 3) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (lockDateFont == 4) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (lockDateFont == 5) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (lockDateFont == 6) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (lockDateFont == 7) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (lockDateFont == 8) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (lockDateFont == 9) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (lockDateFont == 10) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (lockDateFont == 11) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (lockDateFont == 12) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("abelreg", Typeface.NORMAL));
        }
        if (lockDateFont == 13) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("adamcg-pro", Typeface.NORMAL));
        }
        if (lockDateFont == 14) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("adventpro", Typeface.NORMAL));
        }
        if (lockDateFont == 15) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("alien-league", Typeface.NORMAL));
        }
        if (lockDateFont == 16) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("archivonar", Typeface.NORMAL));
        }
        if (lockDateFont == 17) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("autourone", Typeface.NORMAL));
        }
        if (lockDateFont == 18) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("badscript", Typeface.NORMAL));
        }
        if (lockDateFont == 19) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("bignoodle-regular", Typeface.NORMAL));
        }
        if (lockDateFont == 20) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("biko", Typeface.NORMAL));
        }
        if (lockDateFont == 21) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("cherryswash", Typeface.NORMAL));
        }
        if (lockDateFont == 22) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("ginora-sans", Typeface.NORMAL));
        }
        if (lockDateFont == 23) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
        }
        if (lockDateFont == 24) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("ibmplex-mono", Typeface.NORMAL));
        }
        if (lockDateFont == 25) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("inkferno", Typeface.NORMAL));
        }
        if (lockDateFont == 26) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("instruction", Typeface.NORMAL));
        }
        if (lockDateFont == 27) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("jack-lane", Typeface.NORMAL));
        }
        if (lockDateFont == 28) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("kellyslab", Typeface.NORMAL));
        }
        if (lockDateFont == 29) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("monad", Typeface.NORMAL));
        }
        if (lockDateFont == 30) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("noir", Typeface.NORMAL));
        }
        if (lockDateFont == 31) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("outrun-future", Typeface.NORMAL));
        }
        if (lockDateFont == 32) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("pompiere", Typeface.NORMAL));
        }
        if (lockDateFont == 33) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
        }
        if (lockDateFont == 34) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("riviera", Typeface.NORMAL));
        }
        if (lockDateFont == 35) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("the-outbox", Typeface.NORMAL));
        }
        if (lockDateFont == 36) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("themeable-date", Typeface.NORMAL));
        }
        if (lockDateFont == 37) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("vibur", Typeface.NORMAL));
        }
        if (lockDateFont == 38) {
            mKeyguardSlice.setViewsTypeface(Typeface.create("voltaire", Typeface.NORMAL));
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
            mOwnerInfo.setTypeface(Typeface.create("abelreg", Typeface.NORMAL));
        }
        if (lockOwnFont == 13) {
            mOwnerInfo.setTypeface(Typeface.create("adamcg-pro", Typeface.NORMAL));
        }
        if (lockOwnFont == 14) {
            mOwnerInfo.setTypeface(Typeface.create("adventpro", Typeface.NORMAL));
        }
        if (lockOwnFont == 15) {
            mOwnerInfo.setTypeface(Typeface.create("alexana-neue", Typeface.NORMAL));
        }
        if (lockOwnFont == 16) {
            mOwnerInfo.setTypeface(Typeface.create("alien-league", Typeface.NORMAL));
        }
        if (lockOwnFont == 17) {
            mOwnerInfo.setTypeface(Typeface.create("archivonar", Typeface.NORMAL));
        }
        if (lockOwnFont == 18) {
            mOwnerInfo.setTypeface(Typeface.create("autourone", Typeface.NORMAL));
        }
        if (lockOwnFont == 19) {
            mOwnerInfo.setTypeface(Typeface.create("azedo-light", Typeface.NORMAL));
        }
        if (lockOwnFont == 20) {
            mOwnerInfo.setTypeface(Typeface.create("badscript", Typeface.NORMAL));
        }
        if (lockOwnFont == 21) {
            mOwnerInfo.setTypeface(Typeface.create("bignoodle-regular", Typeface.NORMAL));
        }
        if (lockOwnFont == 22) {
            mOwnerInfo.setTypeface(Typeface.create("biko", Typeface.NORMAL));
        }
        if (lockOwnFont == 23) {
            mOwnerInfo.setTypeface(Typeface.create("cocobiker", Typeface.NORMAL));
        }
        if (lockOwnFont == 24) {
            mOwnerInfo.setTypeface(Typeface.create("fester", Typeface.NORMAL));
        }
        if (lockOwnFont == 25) {
            mOwnerInfo.setTypeface(Typeface.create("ginora-sans", Typeface.NORMAL));
        }
        if (lockOwnFont == 26) {
            mOwnerInfo.setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
        }
        if (lockOwnFont == 27) {
            mOwnerInfo.setTypeface(Typeface.create("ibmplex-mono", Typeface.NORMAL));
        }
        if (lockOwnFont == 28) {
            mOwnerInfo.setTypeface(Typeface.create("jacklane", Typeface.NORMAL));
        }
        if (lockOwnFont == 29) {
            mOwnerInfo.setTypeface(Typeface.create("kellyslab", Typeface.NORMAL));
        }
        if (lockOwnFont == 30) {
            mOwnerInfo.setTypeface(Typeface.create("monad", Typeface.NORMAL));
        }
        if (lockOwnFont == 31) {
            mOwnerInfo.setTypeface(Typeface.create("noir", Typeface.NORMAL));
        }
        if (lockOwnFont == 32) {
            mOwnerInfo.setTypeface(Typeface.create("northfont", Typeface.NORMAL));
        }
        if (lockOwnFont == 33) {
            mOwnerInfo.setTypeface(Typeface.create("pompiere", Typeface.NORMAL));
        }
        if (lockOwnFont == 34) {
            mOwnerInfo.setTypeface(Typeface.create("qontra", Typeface.NORMAL));
        }
        if (lockOwnFont == 35) {
            mOwnerInfo.setTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
        }
        if (lockOwnFont == 36) {
            mOwnerInfo.setTypeface(Typeface.create("the-outbox", Typeface.NORMAL));
        }
        if (lockOwnFont == 37) {
            mOwnerInfo.setTypeface(Typeface.create("themeable-owner", Typeface.NORMAL));
        }
        if (lockOwnFont == 38) {
            mOwnerInfo.setTypeface(Typeface.create("unionfont", Typeface.NORMAL));
        }
        if (lockOwnFont == 39) {
            mOwnerInfo.setTypeface(Typeface.create("vibur", Typeface.NORMAL));
        }
        if (lockOwnFont == 40) {
            mOwnerInfo.setTypeface(Typeface.create("voltaire", Typeface.NORMAL));
        }
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

            // Use fancy colon.
            clockView24 = clockView24.replace(':', '\uee01');
            clockView12 = clockView12.replace(':', '\uee01');

            cacheKey = key;
        }
    }

    public void setDarkAmount(float darkAmount) {
        if (mDarkAmount == darkAmount) {
            return;
        }
        mDarkAmount = darkAmount;
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
        updateDozeVisibleViews();
        mKeyguardSlice.setDarkAmount(mDarkAmount);
        mClockView.setTextColor(blendedTextColor);
        mClockSeparator.setBackgroundColor(blendedTextColor);
        mAnalogClockView.setDark(dark);
        updateVisibilities();
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
        }
    }

    public void setPulsing(boolean pulsing, boolean animate) {
        if (mPulsing == pulsing) {
            return;
        }
        if (mPulsing) {
            mWasPulsing = true;
        }
        mPulsing = pulsing;
        // Animation can look really weird when the slice has a header, let's hide the views
        // immediately instead of fading them away.
        if (mKeyguardSlice.hasHeader()) {
            animate = false;
        }
        mKeyguardSlice.setPulsing(pulsing, animate);
        updateDozeVisibleViews();
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

    private class ClipChildrenAnimationListener extends AnimatorListenerAdapter implements
            ViewClippingUtil.ClippingParameters {

        ClipChildrenAnimationListener() {
            ViewClippingUtil.setClippingDeactivated(mClockView, true /* deactivated */,
                    this /* clippingParams */);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            ViewClippingUtil.setClippingDeactivated(mClockView, false /* deactivated */,
                    this /* clippingParams */);
        }

        @Override
        public boolean shouldFinish(View view) {
            return view == getParent();
        }
    }
}
