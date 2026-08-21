package com.fanorisky.pinebrowser;

import android.os.Bundle;
import android.webkit.WebView;

final class Tab {
    String id;
    String url;
    String title;
    Bundle state;
    boolean incognito;
    transient WebView webView;
    long lastUsedSequence;

    Tab(String id, String url, boolean incognito) {
        this.id = id;
        this.url = url;
        this.title = "New tab";
        this.incognito = incognito;
    }
}
