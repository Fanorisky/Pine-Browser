package com.fanorisky.pinebrowser;

import android.content.Context;
import android.content.SharedPreferences;

final class BrowserPreferences {
    private static final String NAME = "browser_preferences";
    private static final String KEY_HOMEPAGE = "homepage";
    private static final String KEY_SEARCH = "search_engine";
    private static final String KEY_EXTERNAL_AUTH = "external_auth";
    private static final String KEY_MAX_ACTIVE_WEBVIEWS = "max_active_webviews";
    private static final String DEFAULT_HOMEPAGE = "https://www.google.com/";
    private static final String DEFAULT_SEARCH = "https://www.google.com/search?q=%s";

    private BrowserPreferences() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static String homepage(Context context) {
        return get(context).getString(KEY_HOMEPAGE, DEFAULT_HOMEPAGE);
    }

    static String searchTemplate(Context context) {
        return get(context).getString(KEY_SEARCH, DEFAULT_SEARCH);
    }

    static boolean openExternalAuth(Context context) {
        return get(context).getBoolean(KEY_EXTERNAL_AUTH, true);
    }

    static int maxActiveWebViews(Context context) {
        int value = get(context).getInt(KEY_MAX_ACTIVE_WEBVIEWS, 1);
        return isSupportedWebViewLimit(value) ? value : 1;
    }

    static void setMaxActiveWebViews(Context context, int value) {
        if (!isSupportedWebViewLimit(value)) throw new IllegalArgumentException("Unsupported WebView limit: " + value);
        get(context).edit().putInt(KEY_MAX_ACTIVE_WEBVIEWS, value).apply();
    }

    static boolean isSupportedWebViewLimit(int value) {
        return value == 1 || value == 2 || value == 3 || value == 5;
    }
}
