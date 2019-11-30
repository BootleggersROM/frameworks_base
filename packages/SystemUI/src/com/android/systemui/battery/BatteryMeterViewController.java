/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.systemui.battery;

import static android.provider.Settings.System.STATUS_BAR_BATTERY_STYLE;
import static android.provider.Settings.System.STATUS_BAR_SHOW_BATTERY_PERCENT;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.flags.Flags;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.ViewController;

import javax.inject.Inject;

/** Controller for {@link BatteryMeterView}. **/
public class BatteryMeterViewController extends ViewController<BatteryMeterView> {
    private final ConfigurationController mConfigurationController;
    private final Handler mMainHandler;
    private final ContentResolver mContentResolver;
    private final BatteryController mBatteryController;

    private final String mSlotBattery;
    private final SettingObserver mSettingObserver;
    private final UserTracker mUserTracker;

    private final ConfigurationController.ConfigurationListener mConfigurationListener =
            new ConfigurationController.ConfigurationListener() {
                @Override
                public void onDensityOrFontScaleChanged() {
                    mView.scaleBatteryMeterViews();
                    mView.updateSettings();
                }
            };

    private final BatteryController.BatteryStateChangeCallback mBatteryStateChangeCallback =
            new BatteryController.BatteryStateChangeCallback() {
                @Override
                public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
                    mView.onBatteryLevelChanged(level, pluggedIn);
                }

                @Override
                public void onPowerSaveChanged(boolean isPowerSave) {
                    mView.onPowerSaveChanged(isPowerSave);
                }

                @Override
                public void onBatteryUnknownStateChanged(boolean isUnknown) {
                    mView.onBatteryUnknownStateChanged(isUnknown);
                }

                @Override
                public void onIsOverheatedChanged(boolean isOverheated) {
                    mView.onIsOverheatedChanged(isOverheated);
                }
            };

    private final UserTracker.Callback mUserChangedCallback =
            new UserTracker.Callback() {
                @Override
                public void onUserChanged(int newUser, @NonNull Context userContext) {
                    mContentResolver.unregisterContentObserver(mSettingObserver);
                    registerBatteryStyleObserver(newUser);
                    registerShowBatteryPercentObserver(newUser);
                    mView.updateShowPercent();
                }
            };

    @Inject
    public BatteryMeterViewController(
            BatteryMeterView view,
            UserTracker userTracker,
            ConfigurationController configurationController,
            @Main Handler mainHandler,
            ContentResolver contentResolver,
            FeatureFlags featureFlags,
            BatteryController batteryController) {
        super(view);
        mUserTracker = userTracker;
        mConfigurationController = configurationController;
        mMainHandler = mainHandler;
        mContentResolver = contentResolver;
        mBatteryController = batteryController;

        mView.setBatteryEstimateFetcher(mBatteryController::getEstimatedTimeRemainingString);
        mView.setDisplayShieldEnabled(featureFlags.isEnabled(Flags.BATTERY_SHIELD_ICON));

        mSlotBattery = getResources().getString(com.android.internal.R.string.status_bar_battery);
        mSettingObserver = new SettingObserver(mMainHandler);
    }

    @Override
    protected void onViewAttached() {
        mConfigurationController.addCallback(mConfigurationListener);
        mBatteryController.addCallback(mBatteryStateChangeCallback);

        registerShowBatteryPercentObserver(mUserTracker.getUserId());
        registerBatteryStyleObserver(mUserTracker.getUserId());
        registerGlobalBatteryUpdateObserver();
        mUserTracker.addCallback(mUserChangedCallback, new HandlerExecutor(mMainHandler));

        mView.updateShowPercent();
    }

    @Override
    protected void onViewDetached() {
        mConfigurationController.removeCallback(mConfigurationListener);
        mBatteryController.removeCallback(mBatteryStateChangeCallback);

        mUserTracker.removeCallback(mUserChangedCallback);
        mContentResolver.unregisterContentObserver(mSettingObserver);
    }

    private void registerShowBatteryPercentObserver(int user) {
        mContentResolver.registerContentObserver(
                Settings.System.getUriFor(STATUS_BAR_SHOW_BATTERY_PERCENT),
                false,
                mSettingObserver,
                user);
    }

    private void registerBatteryStyleObserver(int user) {
       mContentResolver.registerContentObserver(
                Settings.System.getUriFor(STATUS_BAR_BATTERY_STYLE),
                false,
                mSettingObserver,
                user);
    }

    private void registerGlobalBatteryUpdateObserver() {
        mContentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.BATTERY_ESTIMATES_LAST_UPDATE_TIME),
                false,
                mSettingObserver);
    }

    private final class SettingObserver extends ContentObserver {
        public SettingObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            mView.updateSettings();
            mView.updateShowPercent();
            /*if (TextUtils.equals(uri.getLastPathSegment(),
                    Settings.Global.BATTERY_ESTIMATES_LAST_UPDATE_TIME)) {
                // update the text for sure if the estimate in the cache was updated
                mView.updatePercentText();
            }*/
        }
    }
}
