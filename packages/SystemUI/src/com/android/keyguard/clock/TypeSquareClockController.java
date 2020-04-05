/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.keyguard.clock;

import android.app.WallpaperManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;

import java.lang.Math;
import java.util.TimeZone;

import static com.android.systemui.statusbar.phone
        .KeyguardClockPositionAlgorithm.CLOCK_USE_DEFAULT_Y;

/**
 * Plugin for a custom Typographic clock face that displays the time in words.
 */
public class TypeSquareClockController implements ClockPlugin {

    /**
     * Resources used to get title and thumbnail.
     */
    private final Resources mResources;

    /**
     * LayoutInflater used to inflate custom clock views.
     */
    private final LayoutInflater mLayoutInflater;

    /**
     * Extracts accent color from wallpaper.
     */
    private final SysuiColorExtractor mColorExtractor;

    /**
     * Computes preferred position of clock.
     */
    private final SmallClockPosition mClockPosition;

    /**
     * Renders preview from clock view.
     */
    private final ViewPreviewer mRenderer = new ViewPreviewer();

    /**
     * Custom clock shown on AOD screen and behind stack scroller on lock.
     */
    private View mView;
    private TypographicSquareClock mTypeClock;

    /**
     * Small clock shown on lock screen above stack scroller.
     */
    private TypographicSquareClock mLockClock;

    /**
     * Controller for transition into dark state.
     */
    private CrossFadeDarkController mDarkController;

    /**
     * Create a TypeSquareClockController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     */
    TypeSquareClockController(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor) {
        mResources = res;
        mLayoutInflater = inflater;
        mColorExtractor = colorExtractor;
        mClockPosition = new SmallClockPosition(res);
    }

    private void createViews() {
        mView = mLayoutInflater.inflate(R.layout.type_square_aod_clock, null);
        mTypeClock = mView.findViewById(R.id.type_clock);

        // For now, this view is used to hide the default digital clock.
        // Need better transition to lock screen.
        mTypeClock.setTextColor(0xFF121217);
        int paddingType = (mResources.getDimensionPixelSize(R.dimen.typeClockBoxPadding));
        mTypeClock.setShadowLayer(paddingType /* radius */, 0, 0, 0 /* transparent */);
        mTypeClock.setPadding(paddingType,paddingType,paddingType,paddingType);
        mLockClock = (TypographicSquareClock) mLayoutInflater.inflate(R.layout.typographic_square_clock, null);
        mLockClock.setVisibility(View.GONE);

        mDarkController = new CrossFadeDarkController(mView, mLockClock);
    }

    @Override
    public void onDestroyView() {
        mView = null;
        mTypeClock = null;
        mLockClock = null;
        mDarkController = null;
    }

    @Override
    public String getName() {
        return "typesquare";
    }

    @Override
    public String getTitle() {
        return mResources.getString(R.string.clock_title_type);
    }

    @Override
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.type_thumbnail);
    }

    @Override
    public Bitmap getPreview(int width, int height) {

        // Use the big clock view for the preview
        View view = getBigClockView();

        // Initialize state of plugin before generating preview.
        setDarkAmount(1f);
        setTextColor(0xFF121217);
        int paddingType = (mResources.getDimensionPixelSize(R.dimen.typeClockBoxPadding));
        mTypeClock.setShadowLayer(paddingType /* radius */, 0, 0, 0 /* transparent */);
        mTypeClock.setPadding(paddingType,paddingType,paddingType,paddingType);
        mLockClock.setShadowLayer(paddingType /* radius */, 0, 0, 0 /* transparent */);
        mLockClock.setPadding(paddingType,paddingType,paddingType,paddingType);
        ColorExtractor.GradientColors colors = mColorExtractor.getColors(
                WallpaperManager.FLAG_LOCK);
        setColorPalette(colors.supportsDarkText(), colors.getColorPalette());
        onTimeTick();

        return mRenderer.createPreview(view, width, height);
    }

    @Override
    public View getView() {
        if (mLockClock == null) {
            createViews();
        }
        return mLockClock;
    }

    @Override
    public View getBigClockView() {
        if (mView == null) {
            createViews();
        }
        return mView;
    }

    @Override
    public int getPreferredY(int totalHeight) {
    	double yMath = CLOCK_USE_DEFAULT_Y * 0.64;
    	int yPos= (int) Math.round(yMath);
        return yPos;
    }

    @Override
    public void setStyle(Style style) {}

    @Override
    public void setTextColor(int color) {
        mTypeClock.setTextColor(color);
        mLockClock.setTextColor(color);
    }

    @Override
    public void setTypeface(Typeface tf) {
        mTypeClock.setTypeface(tf);
        mLockClock.setTypeface(tf);
    }

    @Override
    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {
        if (colorPalette == null || colorPalette.length == 0) {
            return;
        }
        final int color = colorPalette[Math.max(0, colorPalette.length - 5)];
        mTypeClock.setClockColor(color);
        mLockClock.setClockColor(color);
    }

    @Override
    public void onTimeTick() {
        mTypeClock.onTimeChanged();
        mLockClock.onTimeChanged();
    }

    @Override
    public void setDarkAmount(float darkAmount) {
        mClockPosition.setDarkAmount(darkAmount);
        if (mDarkController != null) {
            mDarkController.setDarkAmount(darkAmount);
        }
    }

    @Override
    public void onTimeZoneChanged(TimeZone timeZone) {
        mTypeClock.onTimeZoneChanged(timeZone);
        mLockClock.onTimeZoneChanged(timeZone);
    }

    @Override
    public boolean shouldShowStatusArea() {
        return false;
    }
}
