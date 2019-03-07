/*
 * Copyright (C) 2019 Bootleggers ROM
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

package com.android.internal.util.bootleggers;

public class AmbientTickerUtil {

    // Declare some variables to be modified later
    public static boolean mIsTickerShowing = false;
    public static boolean mIsInfoFromPNP = false;
    public static int DISPLAY_TIME_INTERVAL_SEC = 120000;
    public static String mTickerInfo;

    public static boolean getTickerDisplayStatus() {
        return mIsTickerShowing;
    }

    public static void setTickerDisplayStatus(boolean value) {
        mIsTickerShowing = value;
    }

    public static boolean getTickerFromPNP() {
        return mIsInfoFromPNP;
    }

    public static void setTickerFromPNP(boolean value) {
        mIsInfoFromPNP = value;
    }

    public static String getTickerInfo() {
        return mTickerInfo;
    }

    public static void setTickerInfo(String value) {
        mTickerInfo = value;
    }
}
