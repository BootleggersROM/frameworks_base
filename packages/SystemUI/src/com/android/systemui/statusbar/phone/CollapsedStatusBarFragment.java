/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import static android.app.StatusBarManager.DISABLE_CLOCK;
import static android.app.StatusBarManager.DISABLE_NOTIFICATION_ICONS;
import static android.app.StatusBarManager.DISABLE_SYSTEM_INFO;

import android.annotation.Nullable;
import android.app.Fragment;
import android.app.StatusBarManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.SysUiServiceProvider;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.StatusBarIconController.DarkIconManager;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.phone.TickerView;
import com.android.systemui.statusbar.policy.DarkIconDispatcher;
import com.android.systemui.statusbar.policy.EncryptionHelper;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;

import android.widget.ImageView;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

/**
 * Contains the collapsed status bar and handles hiding/showing based on disable flags
 * and keyguard state. Also manages lifecycle to make sure the views it contains are being
 * updated by the StatusBarIconController and DarkIconManager while it is attached.
 */
public class CollapsedStatusBarFragment extends Fragment implements CommandQueue.Callbacks {

    public static final String TAG = "CollapsedStatusBarFragment";
    private static final String EXTRA_PANEL_STATE = "panel_state";
    public static final String STATUS_BAR_ICON_MANAGER_TAG = "status_bar_icon_manager";
    public static final int FADE_IN_DURATION = 320;
    public static final int FADE_IN_DELAY = 50;
    private PhoneStatusBarView mStatusBar;
    private KeyguardMonitor mKeyguardMonitor;
    private NetworkController mNetworkController;
    private LinearLayout mSystemIconArea;
    private View mNotificationIconAreaInner;
    private int mDisabled1;
    private StatusBar mStatusBarComponent;
    private DarkIconManager mDarkIconManager;
    private View mOperatorNameFrame;

    // clock position
    private ClockController mClockController;

    private final Handler mHandler = new Handler();
    private ContentResolver mContentResolver;

    // custom carrier label
    private View mCustomCarrierLabel;
    private int mShowCarrierLabel;
    private View mBatteryBar;

    // Bootleg Logo
    private ImageView mBootlegLogo;
    private boolean mShowLogo;
    private int mLogoStyle;

    private class SettingsObserver extends ContentObserver {
       SettingsObserver(Handler handler) {
           super(handler);
       }

       void observe() {
         mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_CARRIER),
                    false, this, UserHandle.USER_ALL);
         mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_TICKER),
                    false, this, UserHandle.USER_ALL);
         mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_LOGO),
                    false, this, UserHandle.USER_ALL);

         mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_LOGO_STYLE),
                    false, this, UserHandle.USER_ALL);
       }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if ((uri.equals(Settings.System.getUriFor(Settings.System.STATUS_BAR_LOGO))) ||
                (uri.equals(Settings.System.getUriFor(Settings.System.STATUS_BAR_LOGO_STYLE)))){
                 updateStatusBarLogo(true);
        }
            updateSettings(true);
        }
    }
    private SettingsObserver mSettingsObserver = new SettingsObserver(mHandler);

    private int mTickerEnabled;
    private View mTickerViewFromStub;

    private SignalCallback mSignalCallback = new SignalCallback() {
        @Override
        public void setIsAirplaneMode(NetworkController.IconState icon) {
            mStatusBarComponent.recomputeDisableFlags(true /* animate */);
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContentResolver = getContext().getContentResolver();
        mKeyguardMonitor = Dependency.get(KeyguardMonitor.class);
        mNetworkController = Dependency.get(NetworkController.class);
        mStatusBarComponent = SysUiServiceProvider.getComponent(getContext(), StatusBar.class);
        mSettingsObserver.observe();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.status_bar, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mStatusBar = (PhoneStatusBarView) view;
        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_PANEL_STATE)) {
            mStatusBar.restoreHierarchyState(
                    savedInstanceState.getSparseParcelableArray(EXTRA_PANEL_STATE));
        }
        mDarkIconManager = new DarkIconManager(view.findViewById(R.id.statusIcons));
        mDarkIconManager.setShouldLog(true);
        Dependency.get(StatusBarIconController.class).addIconGroup(mDarkIconManager);
        mSystemIconArea = mStatusBar.findViewById(R.id.system_icon_area);
        mClockController = new ClockController(mStatusBar);
        mCustomCarrierLabel = mStatusBar.findViewById(R.id.statusbar_carrier_text);
        mBootlegLogo = (ImageView)mStatusBar.findViewById(R.id.status_bar_logo);
        mBatteryBar = mStatusBar.findViewById(R.id.battery_bar);
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(mBootlegLogo);
        updateSettings(false);
        showSystemIconArea(false);
        initEmergencyCryptkeeperText();
        initOperatorName();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        SparseArray<Parcelable> states = new SparseArray<>();
        mStatusBar.saveHierarchyState(states);
        outState.putSparseParcelableArray(EXTRA_PANEL_STATE, states);
    }

    @Override
    public void onResume() {
        super.onResume();
        SysUiServiceProvider.getComponent(getContext(), CommandQueue.class).addCallbacks(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        SysUiServiceProvider.getComponent(getContext(), CommandQueue.class).removeCallbacks(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Dependency.get(StatusBarIconController.class).removeIconGroup(mDarkIconManager);
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            mNetworkController.removeCallback(mSignalCallback);
        }
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(mBootlegLogo);
    }

    public void initNotificationIconArea(NotificationIconAreaController
            notificationIconAreaController) {
        ViewGroup notificationIconArea = mStatusBar.findViewById(R.id.notification_icon_area);
        mNotificationIconAreaInner =
                notificationIconAreaController.getNotificationInnerAreaView();
        if (mNotificationIconAreaInner.getParent() != null) {
            ((ViewGroup) mNotificationIconAreaInner.getParent())
                    .removeView(mNotificationIconAreaInner);
        }
        notificationIconArea.addView(mNotificationIconAreaInner);
        // Default to showing until we know otherwise.
        showNotificationIconArea(false);
    }

    @Override
    public void disable(int state1, int state2, boolean animate) {
        state1 = adjustDisableFlags(state1);
        final int old1 = mDisabled1;
        final int diff1 = state1 ^ old1;
        mDisabled1 = state1;
        if ((diff1 & DISABLE_SYSTEM_INFO) != 0) {
            if ((state1 & DISABLE_SYSTEM_INFO) != 0) {
                hideSystemIconArea(animate);
                hideOperatorName(animate);
            } else {
                showSystemIconArea(animate);
                showOperatorName(animate);
            }
        }
        if ((diff1 & DISABLE_NOTIFICATION_ICONS) != 0) {
            if ((state1 & DISABLE_NOTIFICATION_ICONS) != 0) {
                hideNotificationIconArea(animate);
                hideCarrierName(animate);
            } else {
                showNotificationIconArea(animate);
                showCarrierName(animate);
            }
        }
        // The clock may have already been hidden, but we might want to shift its
        // visibility to GONE from INVISIBLE or vice versa
        if ((diff1 & DISABLE_CLOCK) != 0 ||
                mClockController.getClock().getVisibility() != clockHiddenMode()) {
            if ((state1 & DISABLE_CLOCK) != 0 || mClockController.isClockBlacklistened()) {
                hideClock(animate);
            } else {
                showClock(animate);
            }
        }
    }

    protected int adjustDisableFlags(int state) {
        if (!mStatusBarComponent.isLaunchTransitionFadingAway()
                && !mKeyguardMonitor.isKeyguardFadingAway()
                && shouldHideNotificationIcons()) {
            state |= DISABLE_NOTIFICATION_ICONS;
            state |= DISABLE_SYSTEM_INFO;
            state |= DISABLE_CLOCK;
        }

        // In landscape, the heads up show but shouldHideNotificationIcons() return false
        // because the visual icon is in notification icon area rather than heads up's space.
        // whether the notification icon show or not, clock should hide when heads up show.
        if (mStatusBarComponent.isHeadsUpShouldBeVisible()) {
            state |= DISABLE_CLOCK;
        }

        if (mNetworkController != null && EncryptionHelper.IS_DATA_ENCRYPTED) {
            if (mNetworkController.hasEmergencyCryptKeeperText()) {
                state |= DISABLE_NOTIFICATION_ICONS;
            }
            if (!mNetworkController.isRadioOn()) {
                state |= DISABLE_SYSTEM_INFO;
            }
        }
        return state;
    }

    private boolean shouldHideNotificationIcons() {
        if (!mStatusBar.isClosed() && mStatusBarComponent.hideStatusBarIconsWhenExpanded()) {
            return true;
        }
        if (mStatusBarComponent.hideStatusBarIconsForBouncer()) {
            return true;
        }
        return false;
    }

    public void hideSystemIconArea(boolean animate) {
        animateHide(mSystemIconArea, animate);
        animateHide(mBatteryBar, animate);
    }

    public void showSystemIconArea(boolean animate) {
        animateShow(mSystemIconArea, animate);
        animateShow(mBatteryBar, animate);
    }

    public void hideClock(boolean animate) {
        animateHiddenState(mClockController.getClock(), clockHiddenMode(), animate);
    }

    public void showClock(boolean animate) {
        animateShow(mClockController.getClock(), animate);
    }

    /**
     * If panel is expanded/expanding it usually means QS shade is opening, so
     * don't set the clock GONE otherwise it'll mess up the animation.
     */
    private int clockHiddenMode() {
        if (!mStatusBar.isClosed() && !mKeyguardMonitor.isShowing()) {
            return View.INVISIBLE;
        }
        return View.GONE;
    }

    public void hideNotificationIconArea(boolean animate) {
        animateHide(mNotificationIconAreaInner, animate);
        if (mShowLogo) {
            animateHide(mBootlegLogo, animate);
        }
    }

    public void showNotificationIconArea(boolean animate) {
        animateShow(mNotificationIconAreaInner, animate);
        if (mShowLogo) {
            animateShow(mBootlegLogo, animate);
        }
    }

    public void hideOperatorName(boolean animate) {
        if (mOperatorNameFrame != null) {
            animateHide(mOperatorNameFrame, animate);
        }
    }

    public void showOperatorName(boolean animate) {
        if (mOperatorNameFrame != null) {
            animateShow(mOperatorNameFrame, animate);
        }
    }

    public void hideCarrierName(boolean animate) {
        if (mCustomCarrierLabel != null) {
            animateGone(mCustomCarrierLabel);
        }
    }

    public void showCarrierName(boolean animate) {
        if (mCustomCarrierLabel != null) {
            setCarrierLabel(animate);
        }
    }

    /**
     * Animate a view to INVISIBLE or GONE
     */
    private void animateHiddenState(final View v, int state, boolean animate) {
        if (v instanceof Clock && !((Clock)v).isClockVisible()) {
            return;
        }
        v.animate().cancel();
        if (!animate) {
            v.setAlpha(0f);
            v.setVisibility(state);
            return;
        }

        v.animate()
                .alpha(0f)
                .setDuration(160)
                .setStartDelay(0)
                .setInterpolator(Interpolators.ALPHA_OUT)
                .withEndAction(() -> v.setVisibility(state));
    }

    /**
     * Hides a view.
     */
    private void animateHide(final View v, boolean animate) {
        animateHiddenState(v, View.INVISIBLE, animate);
    }

    /**
     * Remove a view.
     */
    private void animateGone(final View v) {
        animateHiddenState(v, View.GONE, false);
    }

    /**
     * Shows a view, and synchronizes the animation with Keyguard exit animations, if applicable.
     */
    private void animateShow(View v, boolean animate) {
        if (v instanceof Clock && !((Clock)v).isClockVisible()) {
            return;
        }
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        if (!animate) {
            v.setAlpha(1f);
            return;
        }
        v.animate()
                .alpha(1f)
                .setDuration(FADE_IN_DURATION)
                .setInterpolator(Interpolators.ALPHA_IN)
                .setStartDelay(FADE_IN_DELAY)

                // We need to clean up any pending end action from animateHide if we call
                // both hide and show in the same frame before the animation actually gets started.
                // cancel() doesn't really remove the end action.
                .withEndAction(null);

        // Synchronize the motion with the Keyguard fading if necessary.
        if (mKeyguardMonitor.isKeyguardFadingAway()) {
            v.animate()
                    .setDuration(mKeyguardMonitor.getKeyguardFadingAwayDuration())
                    .setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN)
                    .setStartDelay(mKeyguardMonitor.getKeyguardFadingAwayDelay())
                    .start();
        }
    }

    private void initEmergencyCryptkeeperText() {
        View emergencyViewStub = mStatusBar.findViewById(R.id.emergency_cryptkeeper_text);
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            if (emergencyViewStub != null) {
                ((ViewStub) emergencyViewStub).inflate();
            }
            mNetworkController.addCallback(mSignalCallback);
        } else if (emergencyViewStub != null) {
            ViewGroup parent = (ViewGroup) emergencyViewStub.getParent();
            parent.removeView(emergencyViewStub);
        }
    }

    private void initOperatorName() {
        if (getResources().getBoolean(R.bool.config_showOperatorNameInStatusBar)) {
            ViewStub stub = mStatusBar.findViewById(R.id.operator_name);
            mOperatorNameFrame = stub.inflate();
        }
    }

    public void updateSettings(boolean animate) {
        mShowCarrierLabel = Settings.System.getIntForUser(mContentResolver,
                Settings.System.STATUS_BAR_SHOW_CARRIER, 1,
                UserHandle.USER_CURRENT);
        mTickerEnabled = Settings.System.getIntForUser(
                mContentResolver, Settings.System.STATUS_BAR_SHOW_TICKER, 0,
                UserHandle.USER_CURRENT);
        mShowLogo = Settings.System.getIntForUser(
                mContentResolver, Settings.System.STATUS_BAR_LOGO, 0,
                UserHandle.USER_CURRENT) == 1;
        setCarrierLabel(animate);
        updateStatusBarLogo(animate);
        initTickerView();
    }

    private void setCarrierLabel(boolean animate) {
        if (mShowCarrierLabel == 2 || mShowCarrierLabel == 3) {
            animateShow(mCustomCarrierLabel, animate);
        } else {
            animateGone(mCustomCarrierLabel);
        }
    }

    private void initTickerView() {
        if (mTickerEnabled != 0) {
            View tickerStub = mStatusBar.findViewById(R.id.ticker_stub);
            if (mTickerViewFromStub == null && tickerStub != null) {
                mTickerViewFromStub = ((ViewStub) tickerStub).inflate();
            }
            TickerView tickerView = (TickerView) mStatusBar.findViewById(R.id.tickerText);
            ImageSwitcher tickerIcon = (ImageSwitcher) mStatusBar.findViewById(R.id.tickerIcon);
            mStatusBarComponent.createTicker(
                    mTickerEnabled, getContext(), mStatusBar, tickerView, tickerIcon, mTickerViewFromStub);
        } else {
            mStatusBarComponent.disableTicker();
        }
    }

    private void updateStatusBarLogo(boolean animate) {
        Drawable logo = null;
        if (mStatusBar == null) return;
        if (getContext() == null) {
            return;
        }

        mShowLogo = Settings.System.getIntForUser(
                mContentResolver, Settings.System.STATUS_BAR_LOGO, 0,
                UserHandle.USER_CURRENT) == 1;
        mLogoStyle = Settings.System.getIntForUser(
                mContentResolver, Settings.System.STATUS_BAR_LOGO_STYLE, 0,
                UserHandle.USER_CURRENT);

        switch(mLogoStyle) {
                // Default HOME logo, first time
            case 0:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_logo);
                break;
                // MDI Alien
            case 1:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_alien);
                break;
                // MDI Android Head
            case 2:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_android_head);
                break;
                // MDI brain
            case 3:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_brain);
                break;
                // Small BTLG
            case 4:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_btlg);
                break;
                // MDI Clippy
            case 5:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_clippy);
                break;
                // MDI Diamond stone
            case 6:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_diamond_stone);
                break;
                // MDI Drama Masks
            case 7:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_drama_masks);
                break;
                // MDI emoji cool glasses 
            case 8:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_emoticon_cool_outline);
                break;
                // MDI Fingerprint
            case 9:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_fingerprint);
                break;
                // MDI Football Helmet
            case 10:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_football_helmet);
                break;
                // MDI Gamepad
            case 11:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_gamepad);
                break;
                // MDI Ghost
            case 12:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_ghost);
                break;
                // MDI Github
            case 13:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_github_face);
                break;
                // MDI Glass Cocktail
            case 14:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_glass_cocktail);
                break;
                // MDI Glass Wine
            case 15:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_glass_wine);
                break;
                // MDI Glitter (creative)
            case 16:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_glitter);
                break;
                // MDI GController
            case 17:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_google_controller);
                break;
                // MDI GraphQL
            case 18:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_graphql);
                break;
                // MDI Guitar
            case 19:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_guitar_electric);
                break;
                // MDI Guitar Pick
            case 20:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_guitar_pick);
                break;
                // MDI Hand Okay
            case 21:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_hand_okay);
                break;
                // MDI Heart
            case 22:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_heart);
                break;
                // Linux
            case 23:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_linux);
                break;
                // MDI Mushroom
            case 24:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_mushroom);
                break;
                // nice logo >:]
            case 25:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_nice_logo);
                break;
                // MDI Ornament
            case 26:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_ornament);
                break;
                // MDI owl
            case 27:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_owl);
                break;
                // MDI Pac-man
            case 28:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_pac_man);
                break;
                // MDI Pine Tree
            case 29:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_pine_tree);
                break;
                // Shishu Builds esmolified
            case 30:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_shishubuilds);
                break;
                // Small BTLG
            case 31:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_shishu_logo);
                break;
                // The S of the shishu builds
            case 32:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_shishu_s);
                break;
                // MDI Space invaders
            case 33:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_space_invaders);
                break;
                // MDI Sunglasses
            case 34:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_sunglasses);
                break;
                // MDI timer sand
            case 35:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_timer_sand);
                break;
                // Themeable Statusbar icon 01
            case 36:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_themeicon01);
                break;
                // Themeable Statusbar icon 02
            case 37:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_themeicon02);
                break;
                // Themeable Statusbar icon 03
            case 38:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_themeicon03);
                break;
                // Default Bootleggers HOME logo, once again
            default:
                logo = getContext().getResources().getDrawable(R.drawable.status_bar_logo);
                break;
        }

        if (mBootlegLogo != null) {
            if (logo == null) {
                // Something wrong. Do not show anything
                mBootlegLogo.setImageDrawable(logo);
                mShowLogo = false;
                return;
            }

            mBootlegLogo.setImageDrawable(logo);
        }

        if (mNotificationIconAreaInner != null) {
            if (mShowLogo) {
                if (mNotificationIconAreaInner.getVisibility() == View.VISIBLE) {
                    animateShow(mBootlegLogo, animate);
                }
            } else {
                animateGone(mBootlegLogo);
            }
        }
    }
}
