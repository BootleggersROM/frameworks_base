/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.internal.statusbar;

import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;
import android.util.Log;

public class ThemeAccentUtils {
    public static final String TAG = "ThemeAccentUtils";

    private static final String[] ACCENTS = {
        "default_accent", // 0
        "com.google.android.theme.newhouseorange", // 1
        "com.google.android.theme.warmthorange", // 2
        "com.google.android.theme.awmawy", // 3
        "com.google.android.theme.coldyellow", // 4
        "com.google.android.theme.maniamber", // 5
        "com.google.android.theme.limedgreen", // 6
        "com.google.android.theme.diffdaygreen", // 7
        "com.google.android.theme.movemint", // 8
        "com.google.android.theme.seasidemint", // 9
        "com.google.android.theme.naturedgreen", // 10
        "com.google.android.theme.stock", // 11
        "com.google.android.theme.kablue", // 12
        "com.google.android.theme.holillusion", // 13
        "com.google.android.theme.heirloombleu", // 14
        "com.google.android.theme.coldbleu", // 15
        "com.google.android.theme.obfusbleu", // 16
        "com.google.android.theme.frenchbleu", // 17
        "com.google.android.theme.footprintpurple", // 18
        "com.google.android.theme.dreamypurple", // 19
        "com.google.android.theme.notimppurple", // 20
        "com.google.android.theme.spookedpurple", // 21
        "com.google.android.theme.illusionspurple", // 22
        "com.google.android.theme.trufilpink", // 23
        "com.google.android.theme.duskpurple", // 24
        "com.google.android.theme.labouchered", // 25
        "com.google.android.theme.bubblegumpink", // 26
        "com.google.android.theme.misleadingred", // 27
        "com.google.android.theme.hazedpink", // 28
        "com.google.android.theme.burningred", // 29
        "com.google.android.theme.whythisgrey", // 30
    };

    private static final String[] DARK_THEMES = {
        "com.android.system.theme.dark", // 0
        "com.android.settings.theme.dark", // 1
    };

    private static final String[] BLACK_THEMES = {
        "com.android.system.theme.black", // 0
        "com.android.settings.theme.black", // 1
    };

    private static final String[] SHISHU_THEMES = {
        "com.android.system.theme.shishu", // 0
        "com.android.settings.theme.shishu", // 1
        "com.android.systemui.theme.shishu", // 2
        "com.google.android.gms.theme.shishu", // 3
        "com.google.android.apps.wellbeing.theme.shishu", // 4
    };

    private static final String[] SHISHUNIGHTS_THEMES = {
        "com.android.system.theme.shishunights", // 0
        "com.android.settings.theme.shishunights", // 1
        "com.android.systemui.theme.shishunights", // 2
        "com.google.android.gms.theme.shishunights", // 3
        "com.google.android.apps.wellbeing.theme.shishunights", // 4
    };

    private static final String[] SHISHUILLUSIONS_THEMES = {
        "com.android.system.theme.shishuillusions", // 0
        "com.android.settings.theme.shishuillusions", // 1
        "com.android.systemui.theme.shishuillusions", // 2
        "com.google.android.gms.theme.shishuillusions", // 3
        "com.google.android.apps.wellbeing.theme.shishuillusions", // 4
    };

    private static final String[] SHISHUIMMENSITY_THEMES = {
        "com.android.system.theme.shishuimmensity", // 0
        "com.android.settings.theme.shishuimmensity", // 1
        "com.android.systemui.theme.shishuimmensity", // 2
        "com.google.android.gms.theme.shishuimmensity", // 3
        "com.google.android.apps.wellbeing.theme.shishuimmensity", // 4
    };

    private static final String[] SHISHUAMALGAMATION_THEMES = {
        "com.android.system.theme.shishuamalgamation", // 0
        "com.android.settings.theme.shishuamalgamation", // 1
        "com.android.systemui.theme.shishuamalgamation", // 2
        "com.google.android.gms.theme.shishuamalgamation", // 3
        "com.google.android.apps.wellbeing.theme.shishuamalgamation", // 4
    };

    // Switches theme accent from to another or back to stock
    public static void updateAccents(IOverlayManager om, int userId, int accentSetting) {
        if (accentSetting == 0) {
            unloadAccents(om, userId);
        } else {
            try {
                om.setEnabled(ACCENTS[accentSetting],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change theme", e);
            }
        }
    }

    // Unload all the theme accents
    public static void unloadAccents(IOverlayManager om, int userId) {
        // skip index 0
        for (int i = 1; i < ACCENTS.length; i++) {
            String accent = ACCENTS[i];
            try {
                om.setEnabled(accent,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    // Check for the dark system theme
    public static boolean isUsingDarkTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo(DARK_THEMES[0],
                    userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
    }

    // Check for the black system theme
    public static boolean isUsingBlackTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo(BLACK_THEMES[0],
                    userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
     }

    // Check for the extended system theme
    public static boolean isUsingShishuTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo(SHISHU_THEMES[0],
                    userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
     }

    // Check for the extended system theme
    public static boolean isUsingShishuNightsTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo(SHISHUNIGHTS_THEMES[0],
                    userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
     }

    // Check for the extended system theme
    public static boolean isUsingShishuIllusionsTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo(SHISHUILLUSIONS_THEMES[0],
                    userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
     }

    // Check for the extended system theme
    public static boolean isUsingShishuImmensityTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo(SHISHUIMMENSITY_THEMES[0],
                    userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
     }

    // Check for the extended system theme
    public static boolean isUsingShishuAmalgamationTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo(SHISHUAMALGAMATION_THEMES[0],
                    userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
     }

    public static void setLightDarkTheme(IOverlayManager om, int userId, boolean useDarkTheme) {
        for (String theme : DARK_THEMES) {
                try {
                    om.setEnabled(theme,
                        useDarkTheme, userId);
                } catch (RemoteException e) {
                    Log.w(TAG, "Can't change theme", e);
                }
        }
    }

    public static void setLightBlackTheme(IOverlayManager om, int userId, boolean useBlackTheme) {
        for (String theme : BLACK_THEMES) {
                try {
                    om.setEnabled(theme,
                        useBlackTheme, userId);
                } catch (RemoteException e) {
                    Log.w(TAG, "Can't change theme", e);
                }
        }
    }

    public static void setLightShishuTheme(IOverlayManager om, int userId, boolean useShishuTheme) {
        for (String theme : SHISHU_THEMES) {
                try {
                    om.setEnabled(theme,
                        useShishuTheme, userId);
                } catch (RemoteException e) {
                    Log.w(TAG, "Can't change theme", e);
                }
        }
    }

    public static void setLightShishuNightsTheme(IOverlayManager om, int userId, boolean useShishuNightsTheme) {
        for (String theme : SHISHUNIGHTS_THEMES) {
                try {
                    om.setEnabled(theme,
                        useShishuNightsTheme, userId);
                } catch (RemoteException e) {
                    Log.w(TAG, "Can't change theme", e);
                }
        }
    }

    public static void setLightShishuIllusionsTheme(IOverlayManager om, int userId, boolean useShishuIllusionsTheme) {
        for (String theme : SHISHUILLUSIONS_THEMES) {
                try {
                    om.setEnabled(theme,
                        useShishuIllusionsTheme, userId);
                } catch (RemoteException e) {
                    Log.w(TAG, "Can't change theme", e);
                }
        }
    }

    public static void setLightShishuImmensityTheme(IOverlayManager om, int userId, boolean useShishuImmensityTheme) {
        for (String theme : SHISHUIMMENSITY_THEMES) {
                try {
                    om.setEnabled(theme,
                        useShishuImmensityTheme, userId);
                } catch (RemoteException e) {
                    Log.w(TAG, "Can't change theme", e);
                }
        }
    }

    public static void setLightShishuAmalgamationTheme(IOverlayManager om, int userId, boolean useShishuAmalgamationTheme) {
        for (String theme : SHISHUAMALGAMATION_THEMES) {
                try {
                    om.setEnabled(theme,
                        useShishuAmalgamationTheme, userId);
                } catch (RemoteException e) {
                    Log.w(TAG, "Can't change theme", e);
                }
        }
    }
}
