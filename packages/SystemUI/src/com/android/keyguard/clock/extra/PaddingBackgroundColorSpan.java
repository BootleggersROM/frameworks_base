/*
 * Copyright (C) 2015 Anton Lopyrev
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
package com.android.keyguard.clock.extra;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.style.LineBackgroundSpan;

public class PaddingBackgroundColorSpan implements LineBackgroundSpan {
    private int mBackgroundColor;
    private int mPadding;
    private Rect mBgRect;

    public PaddingBackgroundColorSpan(int backgroundColor, int padding) {
        super();
        mBackgroundColor = backgroundColor;
        mPadding = padding;
        // Precreate rect for performance
        mBgRect = new Rect();
    }

    @Override
    public void drawBackground(Canvas c, Paint p, int left, int right, int top, int baseline, int bottom, CharSequence text, int start, int end, int lnum) {
        final int textWidth = Math.round(p.measureText(text, start, end));
        final int paintColor = p.getColor();
        // Draw the background
        mBgRect.set(left - mPadding,
                top - (lnum == 0 ? mPadding / 2 : - (mPadding / 2)),
                left + textWidth + mPadding,
                bottom + mPadding / 2);
        p.setColor(mBackgroundColor);
        c.drawRect(mBgRect, p);
        p.setColor(paintColor);
    }
}