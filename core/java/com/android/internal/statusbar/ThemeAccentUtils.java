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
        "com.google.android.theme.sunsetorange", // 2
        "com.google.android.theme.warmthorange", // 3
        "com.google.android.theme.maniamber", // 4
        "com.google.android.theme.limedgreen", // 5
        "com.google.android.theme.diffdaygreen", // 6
        "com.google.android.theme.spoofygreen", // 7
        "com.google.android.theme.movemint", // 8
        "com.google.android.theme.naturedgreen", // 9
        "com.google.android.theme.stock", // 10
        "com.google.android.theme.drownedaqua", // 11
        "com.google.android.theme.holillusion", // 12
        "com.google.android.theme.coldbleu", // 13
        "com.google.android.theme.heirloombleu", // 14
        "com.google.android.theme.obfusbleu", // 15
        "com.google.android.theme.almostproblue", // 16
        "com.google.android.theme.lunablue", // 17
        "com.google.android.theme.frenchbleu", // 18
        "com.google.android.theme.dreamypurple", // 19
        "com.google.android.theme.notimppurple", // 20
        "com.google.android.theme.grapespurple", // 21
        "com.google.android.theme.spookedpurple", // 22
        "com.google.android.theme.dimigouig", // 23
        "com.google.android.theme.duskpurple", // 24
        "com.google.android.theme.bubblegumpink", // 25
        "com.google.android.theme.dawnred", // 26
        "com.google.android.theme.burningred", // 27
        "com.google.android.theme.labouchered", // 28
        "com.google.android.theme.misleadingred", // 29
        "com.google.android.theme.whythisgrey", // 30
    };

    private static final String[] QS_TILE_THEMES = {
        "default_qstile", // 0
        "com.bootleggers.qstile.squircle", // 1
        "com.bootleggers.qstile.teardrop", // 2
        "com.bootleggers.qstile.deletround", // 3
        "com.bootleggers.qstile.inktober", // 4
        "com.bootleggers.qstile.shishunights", // 5
        "com.bootleggers.qstile.circlegradient", // 6
        "com.bootleggers.qstile.wavey", // 7
        "com.bootleggers.qstile.circledualtone", // 8
        "com.bootleggers.qstile.squaremedo", // 9
        "com.bootleggers.qstile.pokesign", // 10
        "com.bootleggers.qstile.ninja", // 11
        "com.bootleggers.qstile.dottedcircle", // 12
        "com.bootleggers.qstile.shishuink", // 13
        "com.bootleggers.qstile.attemptmountain", // 14
    };

    private static final String[] DARK_THEMES = {
        "com.android.system.theme.darktheem", // 0
        "com.android.settings.theme.darktheem", // 1
        "com.android.systemui.theme.darktheem", // 2
        "com.bootleggers.shishuota.theme.darktheem", // 3
        "com.google.android.apps.wellbeing.theme.darktheem", // 4
        "com.android.contacts.theme.darktheem", // 5
        "com.android.dialer.theme.darktheem", // 6
        "com.android.documentsui.theme.darktheem", // 7
        "org.omnirom.gramophone.theme.darktheem", // 8
    };

    private static final String[] BLACK_THEMES = {
        "com.android.system.theme.blacktheem", // 0
        "com.android.settings.theme.blacktheem", // 1
        "com.android.systemui.theme.blacktheem", // 2
        "com.bootleggers.shishuota.theme.blacktheem", // 3
        "com.google.android.apps.wellbeing.theme.blacktheem", // 4
        "com.android.contacts.theme.blacktheem", // 5
        "com.android.dialer.theme.blacktheem", // 6
        "com.android.documentsui.theme.blacktheem", // 7
        "org.omnirom.gramophone.theme.blacktheem", // 8
    };

    private static final String[] SHISHU_THEMES = {
        "com.android.system.theme.shishu", // 0
        "com.android.settings.theme.shishu", // 1
        "com.android.systemui.theme.shishu", // 2
        "com.google.android.gms.theme.shishu", // 3
        "com.google.android.apps.wellbeing.theme.shishu", // 4
        "com.bootleggers.shishuota.theme.shishu", // 5
        "com.android.contacts.theme.shishu", // 6
        "com.android.dialer.theme.shishu", // 7
        "com.android.documentsui.theme.shishu", // 8
        "org.omnirom.gramophone.theme.shishu", // 9
    };

    private static final String[] SHISHUNIGHTS_THEMES = {
        "com.android.system.theme.shishunights", // 0
        "com.android.settings.theme.shishunights", // 1
        "com.android.systemui.theme.shishunights", // 2
        "com.google.android.gms.theme.shishunights", // 3
        "com.google.android.apps.wellbeing.theme.shishunights", // 4
        "com.bootleggers.shishuota.theme.shishunights", // 5
        "com.android.contacts.theme.shishunights", // 6
        "com.android.dialer.theme.shishunights", // 7
        "com.android.documentsui.theme.shishunights", // 8
        "org.omnirom.gramophone.theme.shishunights", // 9
    };

    private static final String[] SHISHUILLUSIONS_THEMES = {
        "com.android.system.theme.shishuillusions", // 0
        "com.android.settings.theme.shishuillusions", // 1
        "com.android.systemui.theme.shishuillusions", // 2
        "com.google.android.gms.theme.shishuillusions", // 3
        "com.google.android.apps.wellbeing.theme.shishuillusions", // 4
        "com.bootleggers.shishuota.theme.shishuillusions", // 5
        "com.android.contacts.theme.shishuillusions", // 6
        "com.android.dialer.theme.shishuillusions", // 7
        "com.android.documentsui.theme.shishuillusions", // 8
        "org.omnirom.gramophone.theme.shishuillusions", // 9
    };

    private static final String[] SHISHUIMMENSITY_THEMES = {
        "com.android.system.theme.shishuimmensity", // 0
        "com.android.settings.theme.shishuimmensity", // 1
        "com.android.systemui.theme.shishuimmensity", // 2
        "com.google.android.gms.theme.shishuimmensity", // 3
        "com.google.android.apps.wellbeing.theme.shishuimmensity", // 4
        "com.bootleggers.shishuota.theme.shishuimmensity", // 5
        "com.android.contacts.theme.shishuimmensity", // 6
        "com.android.dialer.theme.shishuimmensity", // 7
        "com.android.documentsui.theme.shishuimmensity", // 8
        "org.omnirom.gramophone.theme.shishuimmensity", // 9
    };

    private static final String[] SHISHUAMALGAMATION_THEMES = {
        "com.android.system.theme.shishuamalgamation", // 0
        "com.android.settings.theme.shishuamalgamation", // 1
        "com.android.systemui.theme.shishuamalgamation", // 2
        "com.google.android.gms.theme.shishuamalgamation", // 3
        "com.google.android.apps.wellbeing.theme.shishuamalgamation", // 4
        "com.bootleggers.shishuota.theme.shishuamalgamation", // 
        "com.android.contacts.theme.shishuamalgamation", // 6
        "com.android.dialer.theme.shishuamalgamation", // 7
        "com.android.documentsui.theme.shishuamalgamation", // 8
        "org.omnirom.gramophone.theme.shishuamalgamation", // 9
    };

    private static final String[] SHISHUCOSMOS_THEMES = {
        "com.android.system.theme.shishucosmos", // 0
        "com.android.settings.theme.shishucosmos", // 1
        "com.android.systemui.theme.shishucosmos", // 2
        "com.google.android.gms.theme.shishucosmos", // 3
        "com.google.android.apps.wellbeing.theme.shishucosmos", // 4
        "com.bootleggers.shishuota.theme.shishucosmos", // 5
        "com.android.contacts.theme.shishucosmos", // 6
        "com.android.dialer.theme.shishucosmos", // 7
        "com.android.documentsui.theme.shishucosmos", // 8
        "org.omnirom.gramophone.theme.shishucosmos", // 9
    };

    private static final String[] SHISHUPROTOSTAR_THEMES = {
        "com.android.system.theme.shishuprotostar", // 0
        "com.android.settings.theme.shishuprotostar", // 1
        "com.android.systemui.theme.shishuprotostar", // 2
        "com.google.android.gms.theme.shishuprotostar", // 3
        "com.google.android.apps.wellbeing.theme.shishuprotostar", // 4
        "com.bootleggers.shishuota.theme.shishuprotostar", // 5
        "com.android.contacts.theme.shishuprotostar", // 6
        "com.android.dialer.theme.shishuprotostar", // 7
        "com.android.documentsui.theme.shishuprotostar", // 8
        "org.omnirom.gramophone.theme.shishuprotostar", // 9
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

    // Check for the extended system theme
    public static boolean isUsingShishuCosmosTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo(SHISHUCOSMOS_THEMES[0],
                    userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
     }

    // Check for the extended system theme
    public static boolean isUsingShishuProtostarTheme(IOverlayManager om, int userId) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo(SHISHUPROTOSTAR_THEMES[0],
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

    public static void setLightShishuCosmosTheme(IOverlayManager om, int userId, boolean useShishuCosmosTheme) {
        for (String theme : SHISHUCOSMOS_THEMES) {
                try {
                    om.setEnabled(theme,
                        useShishuCosmosTheme, userId);
                } catch (RemoteException e) {
                    Log.w(TAG, "Can't change theme", e);
                }
        }
    }

    public static void setLightShishuProtostarTheme(IOverlayManager om, int userId, boolean useShishuProtostarTheme) {
        for (String theme : SHISHUPROTOSTAR_THEMES) {
                try {
                    om.setEnabled(theme,
                        useShishuProtostarTheme, userId);
                } catch (RemoteException e) {
                    Log.w(TAG, "Can't change theme", e);
                }
        }
    }

    // Switches qs tile style to user selected.
    public static void updateTileStyle(IOverlayManager om, int userId, int qsTileStyle) {
        if (qsTileStyle == 0) {
            stockTileStyle(om, userId);
        } else {
            try {
                om.setEnabled(QS_TILE_THEMES[qsTileStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change qs tile icon", e);
            }
        }
    }

    // Switches qs tile style back to stock.
    public static void stockTileStyle(IOverlayManager om, int userId) {
        // skip index 0
        for (int i = 1; i < QS_TILE_THEMES.length; i++) {
            String qstiletheme = QS_TILE_THEMES[i];
            try {
                om.setEnabled(qstiletheme,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    // Check for any QS tile styles overlay
    public static boolean isUsingQsTileStyles(IOverlayManager om, int userId, int qsstyle) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = om.getOverlayInfo(QS_TILE_THEMES[qsstyle],
                    userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
    }
}
