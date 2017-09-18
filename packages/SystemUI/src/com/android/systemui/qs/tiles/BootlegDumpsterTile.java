/*
 * Copyright (C) 2015 The Dirty Unicorns Project
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.gzosp.GzospUtils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.SysUIToast;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;

public class BootlegDumpsterTile extends QSTileImpl<BooleanState> {
    private boolean mListening;
    private final ActivityStarter mActivityStarter;

    private static final String TAG = "BootlegDumpsterTile";

    private static final String BTLG_PKG_NAME = "com.android.settings";
//    private static final String OTA_PKG_NAME = "com.aicp.aicpota";

    private static final Intent BOOTLEG_DUMP = new Intent()
        .setComponent(new ComponentName(BTLG_PKG_NAME,
        "com.android.settings.Settings$BootlegDumpsterActivity"));
//    private static final Intent OTA_INTENT = new Intent()
//        .setComponent(new ComponentName(OTA_PKG_NAME,
//        "com.aicp.aicpota.MainActivity"));

    public BootlegDumpsterTile(QSHost host) {
        super(host);
        mActivityStarter = Dependency.get(ActivityStarter.class);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.BOOTLEG;
    }

    @Override
    protected void handleClick() {
        mHost.collapsePanels();
        startBootlegDumpster();
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

//    @Override
//    public void handleLongClick() {
//        // Collapse the panels, so the user can see the toast.
//        mHost.collapsePanels();
//        if (!isOTABundled()) {
//            showNotSupportedToast();
//            return;
//        }
//        startAicpOTA();
//        refreshState();
//    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_bootlegdump_label);
    }

    protected void startBootlegDumpster() {
        mActivityStarter.postStartActivityDismissingKeyguard(BOOTLEG_DUMP, 0);
    }

//    protected void startAicpOTA() {
//        mActivityStarter.postStartActivityDismissingKeyguard(OTA_INTENT, 0);
//    }

    private void showNotSupportedToast(){
        SysUIToast.makeText(mContext, mContext.getString(
                R.string.quick_bootlegdump_toast),
                Toast.LENGTH_LONG).show();
    }

//    private boolean isOTABundled(){
//        boolean isBundled = false;
//        try {
//          isBundled = (mContext.getPackageManager().getPackageInfo(OTA_PKG_NAME, 0).versionCode > 0);
//        } catch (PackageManager.NameNotFoundException e) {
//        }
//        return isBundled;
//    }

    private boolean isDMPAvailable(){
        boolean isInstalled = false;
        boolean isNotHidden = false;
        try {
            isInstalled = (mContext.getPackageManager().getPackageInfo(BTLG_PKG_NAME, 0).versionCode > 0);
        } catch (PackageManager.NameNotFoundException e) {
        }
        PackageManager pm = mContext.getPackageManager();
        isNotHidden = GzospUtils.isPackageEnabled(BTLG_PKG_NAME, pm);
        return isInstalled || isNotHidden;
    }

    @Override
    public boolean isAvailable(){
      return isDMPAvailable();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_bootlegdump);
        state.label = mContext.getString(R.string.quick_bootlegdump_label);
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }
}
