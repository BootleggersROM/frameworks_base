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
package com.android.systemui.qs.tileimpl;

import static com.android.systemui.qs.tileimpl.QSIconViewImpl.QS_ANIM_LENGTH;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.ShapeDrawable.ShaderFactory;
import android.graphics.drawable.shapes.PathShape;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.service.quicksettings.Tile;
import android.text.TextUtils;
import android.util.Log;
import android.util.PathParser;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Switch;

import com.android.settingslib.Utils;
import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSIconView;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.plugins.qs.QSTile.BooleanState;

public class QSTileBaseView extends com.android.systemui.plugins.qs.QSTileView {

    private static final String TAG = "QSTileBaseView";
    private static final int ICON_MASK_ID = com.android.internal.R.string.config_icon_mask;
    private final H mHandler = new H();
    private final int[] mLocInScreen = new int[2];
    private final FrameLayout mIconFrame;
    protected QSIconView mIcon;
    protected RippleDrawable mRipple;
    private Drawable mTileBackground;
    private String mAccessibilityClass;
    private boolean mTileState;
    private boolean mCollapsedView;
    private boolean mClicked;
    private boolean mShowRippleEffect = true;

    private final ImageView mBg;
    private final ImageView mMaskBg;
    private final ImageView mMaskFg;
    private final int mColorActive;
    private final int mColorInactive;
    private final int mColorDisabled;
    private int mCircleColor;
    private int mBgSize;
    private int mState;

    public QSTileBaseView(Context context, QSIconView icon) {
        this(context, icon, false);
    }

    public QSTileBaseView(Context context, QSIconView icon, boolean collapsedView) {
        super(context);
        // Default to Quick Tile padding, and QSTileView will specify its own padding.
        int padding = context.getResources().getDimensionPixelSize(R.dimen.qs_quick_tile_padding);
        mIconFrame = new FrameLayout(context);
        int size = context.getResources().getDimensionPixelSize(R.dimen.qs_quick_tile_size);
        addView(mIconFrame, new LayoutParams(size, size));
        mBg = new ImageView(getContext());
        mMaskBg = new ImageView(getContext());
        mMaskFg = new ImageView(getContext());

        boolean enableQsTileTinting = context.getResources().getBoolean(R.bool.config_enable_qs_tile_tinting);
        if (!enableQsTileTinting) {
            mColorActive = Utils.getColorAttrDefaultColor(context, android.R.attr.colorAccent);
            mColorDisabled = Utils.getDisabled(context,
                    Utils.getColorAttrDefaultColor(context, android.R.attr.textColorTertiary));
            mColorInactive = Utils.getColorAttrDefaultColor(context, android.R.attr.textColorSecondary);
        } else {
            mColorActive = context.getColor(R.color.qs_tiles_background_active_tint);
            mColorDisabled = Utils.getDisabled(context,
                    context.getColor(R.color.qs_tiles_background_disabled_tint));
            mColorInactive = context.getColor(R.color.qs_tiles_background_inactive_tint);
        }

        int bgSize = context.getResources().getDimensionPixelSize(R.dimen.qs_tile_background_size);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(bgSize, bgSize, Gravity.CENTER);
        if (context.getResources().getBoolean(R.bool.config_useMaskForQs)) {
            Path path = new Path(PathParser.createPathFromPathData(
                    context.getResources().getString(ICON_MASK_ID)));
            float pathSize = AdaptiveIconDrawable.MASK_SIZE;
            PathShape p = new PathShape(path, pathSize, pathSize);
            if (context.getResources().getBoolean(R.bool.config_useGradientForQs)) {
                ShapeDrawable backgroundDrawable = new ShapeDrawable(p);
                backgroundDrawable.setTintList(ColorStateList.valueOf(mColorDisabled));
                backgroundDrawable.setIntrinsicHeight(bgSize);
                backgroundDrawable.setIntrinsicWidth(bgSize);
                mMaskBg.setImageDrawable(backgroundDrawable);
                // The drawable shown when the tile is active
                ShapeDrawable foregroundDrawable = new ShapeDrawable(p);
                // Draw a 225 degree counter-clockwise gradient for the foreground
                foregroundDrawable.setShaderFactory(new ShaderFactory() {
                    @Override
                    public Shader resize(int width, int height) {
                        LinearGradient gradient = new LinearGradient (pathSize, pathSize, 0, 0,
                                context.getResources().getColor(R.color.qs_tile_gradient_start),
                                context.getResources().getColor(R.color.qs_tile_gradient_end),
                                TileMode.REPEAT);
                        return gradient;
                    }
                });
                foregroundDrawable.setIntrinsicHeight(bgSize);
                foregroundDrawable.setIntrinsicWidth(bgSize);
                mMaskFg.setAlpha(0f);
                mMaskFg.setVisibility(View.GONE);
                mMaskFg.setImageDrawable(foregroundDrawable);
                mIconFrame.addView(mMaskBg, lp);
                mIconFrame.addView(mMaskFg, lp);
                mMaskBg.setLayoutParams(lp);
                mMaskFg.setLayoutParams(lp);
            } else {
                ShapeDrawable d = new ShapeDrawable(p);
                d.setTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                d.setIntrinsicHeight(bgSize);
                d.setIntrinsicWidth(bgSize);
                mBg.setImageDrawable(d);
                mIconFrame.addView(mBg, lp);
                mBg.setLayoutParams(lp);
            }
        } else {
            // Let's ask if useMasksForQs is disabled and config_useImageMasksForQs is enabled
            if (context.getResources().getBoolean(R.bool.config_useImageMasksForQs)) {
                Drawable backgroundDrawable = context.getResources().getDrawable(R.drawable.ic_qs_circle_background);
                Drawable foregroundDrawable = context.getResources().getDrawable(R.drawable.ic_qs_circle_foreground);
                backgroundDrawable.setTintList(ColorStateList.valueOf(mColorDisabled));
                mMaskBg.setImageDrawable(backgroundDrawable);
                mMaskFg.setAlpha(0f);
                mMaskFg.setVisibility(View.GONE);
                mMaskFg.setImageDrawable(foregroundDrawable);
                mIconFrame.addView(mMaskBg, lp);
                mIconFrame.addView(mMaskFg, lp);
                mMaskBg.setLayoutParams(lp);
                mMaskFg.setLayoutParams(lp);

            } else {
                mBg.setImageResource(R.drawable.ic_qs_circle);
                mIconFrame.addView(mBg);
            }
        }
        mIcon = icon;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        mIconFrame.addView(mIcon, params);
        mIconFrame.setClipChildren(false);
        mIconFrame.setClipToPadding(false);

        mTileBackground = newTileBackground();
        if (mTileBackground instanceof RippleDrawable) {
            setRipple((RippleDrawable) mTileBackground);
        }
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setBackground(mTileBackground);

        setPadding(0, 0, 0, 0);
        setClipChildren(false);
        setClipToPadding(false);
        mCollapsedView = collapsedView;
        setFocusable(true);
    }

    public View getBgCircle() {
        boolean enforceDualBg = (getContext().getResources().getBoolean(R.bool.config_useMaskForQs) && 
            getContext().getResources().getBoolean(R.bool.config_useGradientForQs)) || 
            (!getContext().getResources().getBoolean(R.bool.config_useMaskForQs) && 
            getContext().getResources().getBoolean(R.bool.config_useImageMasksForQs));
        return enforceDualBg ? mMaskBg : mBg;
    }

    protected Drawable newTileBackground() {
        final int[] attrs = new int[]{android.R.attr.selectableItemBackgroundBorderless};
        final TypedArray ta = getContext().obtainStyledAttributes(attrs);
        final Drawable d = ta.getDrawable(0);
        ta.recycle();
        return d;
    }

    private void setRipple(RippleDrawable tileBackground) {
        mRipple = tileBackground;
        if (getWidth() != 0) {
            updateRippleSize();
        }
    }

    private void updateRippleSize() {
        // center the touch feedback on the center of the icon, and dial it down a bit
        final int cx = mIconFrame.getMeasuredWidth() / 2 + mIconFrame.getLeft();
        final int cy = mIconFrame.getMeasuredHeight() / 2 + mIconFrame.getTop();
        final int rad = (int) (mIcon.getHeight() * .85f);
        mRipple.setHotspotBounds(cx - rad, cy - rad, cx + rad, cy + rad);
    }

    @Override
    public void init(QSTile tile) {
        init(v -> tile.click(), v -> tile.secondaryClick(), view -> {
            tile.longClick();
            return true;
        });
    }

    public void init(OnClickListener click, OnClickListener secondaryClick,
            OnLongClickListener longClick) {
        setOnClickListener(click);
        setOnLongClickListener(longClick);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mRipple != null) {
            updateRippleSize();
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        // Avoid layers for this layout - we don't need them.
        return false;
    }

    /**
     * Update the accessibility order for this view.
     *
     * @param previousView the view which should be before this one
     * @return the last view in this view which is accessible
     */
    public View updateAccessibilityOrder(View previousView) {
        setAccessibilityTraversalAfter(previousView.getId());
        return this;
    }

    public void onStateChanged(QSTile.State state) {
        mHandler.obtainMessage(H.STATE_CHANGED, state).sendToTarget();
    }

    protected void handleStateChanged(QSTile.State state) {
        boolean allowAnimations = animationsEnabled();
        boolean enforceDualBg = (getContext().getResources().getBoolean(R.bool.config_useMaskForQs) && 
            getContext().getResources().getBoolean(R.bool.config_useGradientForQs)) || 
            (!getContext().getResources().getBoolean(R.bool.config_useMaskForQs) && 
            getContext().getResources().getBoolean(R.bool.config_useImageMasksForQs));
        if (enforceDualBg) {
            int newTileState = state.state;
            if (newTileState != mState) {
                if (allowAnimations) {
                    if (newTileState == Tile.STATE_ACTIVE) {
                         mMaskFg.setVisibility(View.VISIBLE);
                         mMaskFg.animate().alpha(1f).setDuration(QS_ANIM_LENGTH).withEndAction(new Runnable() {
                             @Override
                             public void run() {
                                 mMaskBg.setVisibility(View.GONE);
                             }
                         });
                    } else if (mState == Tile.STATE_ACTIVE) {
                         mMaskBg.setVisibility(View.VISIBLE);
                         mMaskFg.animate().alpha(0f).setDuration(QS_ANIM_LENGTH).withEndAction(new Runnable() {
                             @Override
                             public void run() {
                                 mMaskFg.setVisibility(View.GONE);
                             }
                         });
                    }
                } else {
                    if (newTileState == Tile.STATE_ACTIVE) {
                        mMaskFg.setVisibility(View.VISIBLE);
                        mMaskFg.setAlpha(1f);
                        mMaskBg.setVisibility(View.GONE);
                    } else if (mState == Tile.STATE_ACTIVE) {
                        mMaskFg.setVisibility(View.GONE);
                        mMaskFg.setAlpha(0f);
                        mMaskBg.setVisibility(View.VISIBLE);
                    }
                }
            }
            mState = newTileState;
        } else {
            int circleColor = getCircleColor(state.state);
            if (circleColor != mCircleColor) {
                if (allowAnimations) {
                    ValueAnimator animator = ValueAnimator.ofArgb(mCircleColor, circleColor)
                            .setDuration(QS_ANIM_LENGTH);
                    animator.addUpdateListener(animation -> mBg.setImageTintList(ColorStateList.valueOf(
                            (Integer) animation.getAnimatedValue())));
                    animator.start();
                } else {
                    QSIconViewImpl.setTint(mBg, circleColor);
                }
                mCircleColor = circleColor;
            }
        }


        mShowRippleEffect = state.showRippleEffect;
        setClickable(state.state != Tile.STATE_UNAVAILABLE);
        setLongClickable(state.handlesLongClick);
        mIcon.setIcon(state, allowAnimations);
        setContentDescription(state.contentDescription);

        mAccessibilityClass =
                state.state == Tile.STATE_UNAVAILABLE ? null : state.expandedAccessibilityClassName;
        if (state instanceof QSTile.BooleanState) {
            boolean newState = ((BooleanState) state).value;
            if (mTileState != newState) {
                mClicked = false;
                mTileState = newState;
            }
        }
    }

    /* The view should not be animated if it's not on screen and no part of it is visible.
     */
    protected boolean animationsEnabled() {
        if (!isShown()) {
            return false;
        }
        if (getAlpha() != 1f) {
            return false;
        }
        getLocationOnScreen(mLocInScreen);
        return mLocInScreen[1] >= -getHeight();
    }

    private int getCircleColor(int state) {
        switch (state) {
            case Tile.STATE_ACTIVE:
                return mColorActive;
            case Tile.STATE_INACTIVE:
            case Tile.STATE_UNAVAILABLE:
                return mColorDisabled;
            default:
                Log.e(TAG, "Invalid state " + state);
                return 0;
        }
    }

    @Override
    public void setClickable(boolean clickable) {
        super.setClickable(clickable);
        setBackground(clickable && mShowRippleEffect ? mRipple : null);
    }

    @Override
    public int getDetailY() {
        return getTop() + getHeight() / 2;
    }

    public QSIconView getIcon() {
        return mIcon;
    }

    public View getIconWithBackground() {
        return mIconFrame;
    }

    @Override
    public boolean performClick() {
        mClicked = true;
        return super.performClick();
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        if (!TextUtils.isEmpty(mAccessibilityClass)) {
            event.setClassName(mAccessibilityClass);
            if (Switch.class.getName().equals(mAccessibilityClass)) {
                boolean b = mClicked ? !mTileState : mTileState;
                String label = getResources()
                        .getString(b ? R.string.switch_bar_on : R.string.switch_bar_off);
                event.setContentDescription(label);
                event.setChecked(b);
            }
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        // Clear selected state so it is not announce by talkback.
        info.setSelected(false);
        if (!TextUtils.isEmpty(mAccessibilityClass)) {
            info.setClassName(mAccessibilityClass);
            if (Switch.class.getName().equals(mAccessibilityClass)) {
                boolean b = mClicked ? !mTileState : mTileState;
                String label = getResources()
                        .getString(b ? R.string.switch_bar_on : R.string.switch_bar_off);
                info.setText(label);
                info.setChecked(b);
                info.setCheckable(true);
                if (isLongClickable()) {
                    info.addAction(
                            new AccessibilityNodeInfo.AccessibilityAction(
                                    AccessibilityNodeInfo.AccessibilityAction
                                            .ACTION_LONG_CLICK.getId(),
                                    getResources().getString(
                                            R.string.accessibility_long_click_tile)));
                }
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('[');
        sb.append("locInScreen=(" + mLocInScreen[0] + ", " + mLocInScreen[1] + ")");
        sb.append(", iconView=" + mIcon.toString());
        sb.append(", tileState=" + mTileState);
        sb.append("]");
        return sb.toString();
    }

    private class H extends Handler {
        private static final int STATE_CHANGED = 1;

        public H() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == STATE_CHANGED) {
                handleStateChanged((QSTile.State) msg.obj);
            }
        }
    }
}