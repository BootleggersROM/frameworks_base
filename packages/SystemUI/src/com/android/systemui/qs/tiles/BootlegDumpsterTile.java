/*
 * Copyright (C) 2017 RR
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
import com.android.internal.util.bootleggers.BootlegUtils;
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
    private static final String OTA_PKG_NAME = "com.bootleggers.shishuota";

    private static final Intent BTLG_CONF = new Intent()
        .setComponent(new ComponentName(BTLG_PKG_NAME,
        "com.android.settings.Settings$BootlegDumpsterActivity"));
    private static final Intent OTA_INTENT = new Intent()
        .setComponent(new ComponentName(OTA_PKG_NAME,
        "com.bootleggers.shishuota.UpdatesActivity"));

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
        startDumpsterConfig();
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public void handleLongClick() {
        if (BootlegUtils.isPackageAvailable(OTA_PKG_NAME, mContext)) {
            // Collapse the panels, so the user can see the toast.
            mHost.collapsePanels();
            startBootlegOTA();
            refreshState();
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_bootlegdump_label);
    }

    protected void startDumpsterConfig() {
        mActivityStarter.postStartActivityDismissingKeyguard(BTLG_CONF, 0);
    }

    protected void startBootlegOTA() {
        mActivityStarter.postStartActivityDismissingKeyguard(OTA_INTENT, 0);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_bootlegdump_tile);
        state.label = mContext.getString(R.string.quick_settings_bootlegdump_label);
    }

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }

    @Override
    public void handleSetListening(boolean listening) {
        // Do nothing
    }
}
