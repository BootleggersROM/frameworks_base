package com.android.keyguard.clocks;

import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.res.Resources;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.text.Annotation;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.internal.colorextraction.ColorExtractor.GradientColors;
import com.android.internal.graphics.ColorUtils;
import com.android.internal.util.NotificationColorUtil;
import com.android.keyguard.R;
import com.android.systemui.Dependency;
import com.android.systemui.colorextraction.SysuiColorExtractor;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

public class TypographicClock extends TextView implements ColorExtractor.OnColorsChangedListener {

    private String mDescFormat;
    private final String[] mHours;
    private final String[] mMinutes;
    private final Resources mResources;
    private final Calendar mTime;
    private TimeZone mTimeZone;
    private SysuiColorExtractor mColorExtractor;

    private int mPrimaryColor;
    private int mAmbientColor;
    private int mSystemAccent;
    private int mFallbackColor;
    private int mCurrentAccent;
    private float mDarkAmount;
    private float[] mHslOut = new float[3];

    private final BroadcastReceiver mTimeZoneChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                onTimeZoneChanged(TimeZone.getTimeZone(tz));
                onTimeChanged();
            }
        }
    };
    
    public TypographicClock(Context context) {
        this(context, null);
    }

    public TypographicClock(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public TypographicClock(Context context, AttributeSet attributeSet, int defStyleAttr) {
        super(context, attributeSet, defStyleAttr);

        mColorExtractor = Dependency.get(SysuiColorExtractor.class);
        mColorExtractor.addOnColorsChangedListener(this);
        mTime = Calendar.getInstance(TimeZone.getDefault());
        mDescFormat = ((SimpleDateFormat) DateFormat.getTimeFormat(context)).toLocalizedPattern();
        mResources = context.getResources();
        mHours = mResources.getStringArray(R.array.type_clock_hours);
        mMinutes = mResources.getStringArray(R.array.type_clock_minutes);
        mSystemAccent = mResources.getColor(R.color.custom_text_clock_top_color, null);
        mFallbackColor = mResources.getColor(R.color.custom_text_clock_top_fallback_color, null);
        onColorsChanged(mColorExtractor, 0);
    }

    public void onTimeChanged() {
        mTime.setTimeInMillis(System.currentTimeMillis());
        setContentDescription(DateFormat.format(mDescFormat, mTime));
        int hours = mTime.get(Calendar.HOUR) % 12;
        int minutes = mTime.get(Calendar.MINUTE) % 60;
        SpannedString rawFormat = (SpannedString) mResources.getQuantityText(R.plurals.type_clock_header, hours);
        Annotation[] annotationArr = (Annotation[]) rawFormat.getSpans(0, rawFormat.length(), Annotation.class);
        SpannableString colored = new SpannableString(rawFormat);
        for (Annotation annotation : annotationArr) {
            if ("color".equals(annotation.getValue())) {
                colored.setSpan(new ForegroundColorSpan(mCurrentAccent),
                        colored.getSpanStart(annotation),
                        colored.getSpanEnd(annotation),
                        Spanned.SPAN_POINT_POINT);
            }
        }
        setText(TextUtils.expandTemplate(colored, new CharSequence[]{mHours[hours], mMinutes[minutes]}));
    }

    public void onTimeZoneChanged(TimeZone timeZone) {
        mTimeZone = timeZone;
        mTime.setTimeZone(timeZone);
    }

    @Override
    public void onColorsChanged(ColorExtractor extractor, int which) {
        GradientColors colors = extractor.getColors(WallpaperManager.FLAG_LOCK);
        setWallpaperColors(colors.getMainColor(), colors.supportsDarkText(), colors.getColorPalette());
    }

    private void setWallpaperColors(int mainColor, boolean supportsDarkText, int[] colorPalette) {
        int scrimColor = supportsDarkText ? Color.WHITE : Color.BLACK;
        int scrimTinted = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(scrimColor, mainColor, 0.5f), 64);
        int bgColor = ColorUtils.compositeColors(scrimTinted, mainColor);

        int paletteColor = getColorFromPalette(colorPalette);
        bgColor = ColorUtils.compositeColors(bgColor, Color.BLACK);
        mPrimaryColor = findColor(paletteColor, bgColor, !supportsDarkText, mSystemAccent, mFallbackColor);
        mAmbientColor = findColor(paletteColor, Color.BLACK, true, mSystemAccent, mFallbackColor);

        setDarkAmount(mDarkAmount);
    }

    private int getColorFromPalette(int[] palette) {
        if (palette != null && palette.length != 0) {
            return palette[Math.max(0, palette.length - 5)];
        } else {
            return mSystemAccent;
        }
    }

    private int findColor(int color, int background, boolean againstDark, int accent, int fallback) {
        if (!isGreyscale(color)) {
            return color;
        }
        int contrastAccent = NotificationColorUtil.ensureTextContrast(accent, background, againstDark);
        if (!isGreyscale(contrastAccent)) {
            return contrastAccent;
        } else {
            return fallback;
        }
    }

    private boolean isGreyscale(int color) {
        ColorUtils.RGBToHSL(Color.red(color), Color.green(color), Color.blue(color), mHslOut);
        return mHslOut[1] < 0.1f || mHslOut[2] < 0.1f;
    }

    public void setDarkAmount(float dark) {
        mDarkAmount = dark;
        mCurrentAccent = ColorUtils.blendARGB(mPrimaryColor, mAmbientColor, mDarkAmount);
        onTimeChanged();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Calendar calendar = mTime;
        TimeZone timeZone = mTimeZone;
        if (timeZone == null) {
            timeZone = TimeZone.getDefault();
        }
        calendar.setTimeZone(timeZone);
        onTimeChanged();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getContext().registerReceiver(mTimeZoneChangedReceiver, filter);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getContext().unregisterReceiver(mTimeZoneChangedReceiver);
        mColorExtractor.removeOnColorsChangedListener(this);
    }
}
