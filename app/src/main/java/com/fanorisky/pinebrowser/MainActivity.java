package com.fanorisky.pinebrowser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final int FILE_CHOOSER_REQUEST = 7001;
    private static final String STATE_TABS = "tabs";
    private static final String STATE_ACTIVE = "active";
    private static final String TAB_PREFIX = "tab_";
    private static final String OAUTH_SCHEME = "pinebrowser";
    private static final String OAUTH_HOST = "oauth";

    private final LinkedHashMap<String, Tab> tabs = new LinkedHashMap<>();
    private FrameLayout root;
    private LinearLayout toolbar;
    private LinearLayout bottomBar;
    private EditText addressBar;
    private TextView tabCount;
    private WebView webView;
    private String activeTabId;
    private ValueCallback<Uri[]> filePathCallback;
    private long useSequence = 0L;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureGlobalWebViewBehavior();

        if (savedInstanceState == null) {
            createTab(BrowserPreferences.homepage(this), false);
        } else {
            restoreTabs(savedInstanceState);
            if (tabs.isEmpty()) {
                createTab(BrowserPreferences.homepage(this), false);
            } else {
                activateTab(savedInstanceState.getString(STATE_ACTIVE, tabs.keySet().iterator().next()));
            }
        }

        handleIncomingIntent(getIntent());
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(6), dp(8), dp(6));
        toolbar.setBackgroundColor(0xFF101114);

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint(getString(com.fanorisky.pinebrowser.R.string.address_hint));
        addressBar.setTextColor(0xFFF5F5F5);
        addressBar.setHintTextColor(0xFF8D9199);
        addressBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setPadding(dp(12), 0, dp(12), 0);
        addressBar.setBackgroundResource(android.R.drawable.editbox_background);
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        toolbar.addView(addressBar, addressParams);

        ImageButton goButton = button("→");
        goButton.setContentDescription("Go");
        goButton.setOnClickListener(v -> navigateFromAddressBar());
        toolbar.addView(goButton, new LinearLayout.LayoutParams(dp(48), dp(44)));
        FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56), Gravity.TOP);
        root.addView(toolbar, toolbarParams);

        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigateFromAddressBar();
                return true;
            }
            return false;
        });

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setBackgroundColor(0xFF101114);

        addBottomButton("‹", "Back", v -> goBack());
        addBottomButton("›", "Forward", v -> goForward());
        addBottomButton("↻", "Reload", v -> reload());

        tabCount = new TextView(this);
        tabCount.setTextColor(0xFFF5F5F5);
        tabCount.setGravity(Gravity.CENTER);
        tabCount.setTextSize(14);
        tabCount.setOnClickListener(v -> showTabsMenu());
        bottomBar.addView(tabCount, new LinearLayout.LayoutParams(dp(56), dp(48)));

        addBottomButton("+", "New tab", v -> createTab(BrowserPreferences.homepage(this), false));
        addBottomButton("⋮", "Menu", v -> showMainMenu(v));

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50), Gravity.BOTTOM);
        root.addView(bottomBar, bottomParams);

        setContentView(root);
    }

    private void addBottomButton(String label, String description, View.OnClickListener listener) {
        ImageButton button = button(label);
        button.setContentDescription(description);
        button.setOnClickListener(listener);
        bottomBar.addView(button, new LinearLayout.LayoutParams(0, dp(50), 1f));
    }

    private ImageButton button(String label) {
        ImageButton b = new ImageButton(this);
        b.setImageDrawable(null);
        b.setBackgroundColor(0x00000000);
        TextView overlay = new TextView(this);
        overlay.setText(label);
        overlay.setTextColor(0xFFF5F5F5);
        overlay.setTextSize(20);
        overlay.setGravity(Gravity.CENTER);
        // Use a transparent ImageButton with a text compound drawable substitute.
        b.setContentDescription(label);
        b.setTag(label);
        b.setOnTouchListener((v, e) -> false);
        b.post(() -> {
            if (b.getDrawable() == null) {
                b.setImageDrawable(new TextDrawable(this, label));
            }
        });
        return b;
    }

    private void buildAddressBarFromActiveTab() {
        Tab tab = tabs.get(activeTabId);
        if (tab == null) return;
        String url = tab.url == null ? "" : tab.url;
        addressBar.setText(url);
        addressBar.setSelection(addressBar.length());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView newWebView(final Tab tab) {
        WebView wv = new WebView(this);
        wv.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wv.setBackgroundColor(0xFFFFFFFF);

        WebSettings settings = wv.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setGeolocationEnabled(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DISABLED_ACTION_MODE_MENU_ITEMS)) {
            WebSettingsCompat.setDisabledActionModeMenuItems(settings, 0);
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOWNLOAD_FAVICONS_ENABLED)) {
            // Favicons are not rendered by this minimal browser UI; skip automatic favicon fetches
            // to reduce bandwidth and memory use where the installed WebView supports it.
            WebSettingsCompat.setDownloadFaviconsEnabled(settings, false);
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
            ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    return null;
                }
            });
        }

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        wv.setWebViewClient(new BrowserWebViewClient());
        wv.setWebChromeClient(new BrowserChromeClient());
        wv.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                DownloadHelper.enqueue(this, url, userAgent, contentDisposition, mimeType));

        wv.setOnLongClickListener(v -> false);
        wv.setFocusableInTouchMode(true);
        return wv;
    }

    private void configureGlobalWebViewBehavior() {
        // Ensure WebView services are shut down with our process. No background services are created by the browser.
        WebView.setWebContentsDebuggingEnabled(false);
    }

    private void createTab(String url, boolean incognito) {
        Tab tab = new Tab(UUID.randomUUID().toString(), url, incognito);
        tabs.put(tab.id, tab);
        activateTab(tab.id);
    }

    private void activateTab(String id) {
        Tab next = tabs.get(id);
        if (next == null) return;

        if (activeTabId != null && activeTabId.equals(id)) {
            attachTabWebView(next);
            return;
        }

        if (activeTabId != null) {
            Tab previous = tabs.get(activeTabId);
            if (previous != null) {
                saveTabState(previous);
                detachTabWebView(previous);
            }
        }

        // Mark the new tab active before enforcing the limit so the previous tab
        // is eligible for LRU eviction when the configured limit is 1.
        activeTabId = next.id;
        ensureTabWebView(next);
        activeWebViewTab(next);
        next.lastUsedSequence = ++useSequence;
        buildAddressBarFromActiveTab();
        updateTabCount();
        updateMemoryModeSummary();
    }

    private void ensureTabWebView(Tab tab) {
        if (tab.webView != null) return;
        enforceWebViewLimitBeforeCreating(tab.id);
        tab.webView = newWebView(tab);

        if (tab.state != null) {
            try {
                if (tab.webView.restoreState(tab.state) == null) {
                    tab.state = null;
                }
            } catch (Exception e) {
                tab.state = null;
            }
        }
        if (tab.state == null || TextUtils.isEmpty(tab.url)) {
            tab.webView.loadUrl(TextUtils.isEmpty(tab.url) ? BrowserPreferences.homepage(this) : tab.url);
        }
    }

    private void activeWebViewTab(Tab tab) {
        webView = tab.webView;
        attachTabWebView(tab);
    }

    private void attachTabWebView(Tab tab) {
        if (tab.webView == null) return;
        if (tab.webView.getParent() == root) {
            webView = tab.webView;
            return;
        }
        if (tab.webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
        }
        FrameLayout.LayoutParams webViewParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP);
        webViewParams.topMargin = dp(56);
        webViewParams.bottomMargin = dp(50);
        root.addView(tab.webView, 1, webViewParams);
        webView = tab.webView;
    }

    private void detachTabWebView(Tab tab) {
        if (tab.webView == null) return;
        if (tab.webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
        }
        if (tab.webView == webView) webView = null;
    }

    private void enforceWebViewLimitBeforeCreating(String incomingTabId) {
        int max = BrowserPreferences.maxActiveWebViews(this);
        while (countLiveWebViews() >= max) {
            Tab candidate = findLeastRecentlyUsedLiveTab(incomingTabId);
            if (candidate == null) break;
            saveTabState(candidate);
            destroyTabWebView(candidate);
        }
    }

    private int countLiveWebViews() {
        int count = 0;
        for (Tab tab : tabs.values()) if (tab.webView != null) count++;
        return count;
    }

    @Nullable
    private Tab findLeastRecentlyUsedLiveTab(String excludedId) {
        Tab candidate = null;
        for (Tab tab : tabs.values()) {
            if (tab.webView == null || tab.id.equals(excludedId)) continue;
            if (tab.id.equals(activeTabId)) continue;
            if (candidate == null || tab.lastUsedSequence < candidate.lastUsedSequence) candidate = tab;
        }
        return candidate;
    }

    private void saveTabState(Tab tab) {
        if (tab == null || tab.webView == null) return;
        tab.url = tab.webView.getUrl();
        tab.title = tab.webView.getTitle();
        tab.state = new Bundle();
        try {
            tab.webView.saveState(tab.state);
        } catch (Exception ignored) {
        }
    }

    private void destroyTabWebView(Tab tab) {
        if (tab == null || tab.webView == null) return;
        if (tab.webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
        }
        tab.webView.stopLoading();
        tab.webView.setWebChromeClient(null);
        tab.webView.setWebViewClient(null);
        tab.webView.removeAllViews();
        tab.webView.destroy();
        tab.webView = null;
        if (tab.id.equals(activeTabId)) webView = null;
    }

    private void saveActiveWebViewState() {
        if (activeTabId == null) return;
        saveTabState(tabs.get(activeTabId));
    }

    private void closeTab(String id) {
        if (!tabs.containsKey(id)) return;
        boolean active = id.equals(activeTabId);
        Tab closing = tabs.get(id);
        if (closing != null) {
            saveTabState(closing);
            destroyTabWebView(closing);
        }
        tabs.remove(id);
        if (tabs.isEmpty()) {
            activeTabId = null;
            createTab(BrowserPreferences.homepage(this), false);
            return;
        }
        if (active) {
            String nextId = tabs.keySet().iterator().next();
            activateTab(nextId);
        } else {
            updateTabCount();
        }
    }

    private void updateTabCount() {
        tabCount.setText(String.valueOf(tabs.size()));
    }

    private void navigateFromAddressBar() {
        if (webView == null) return;
        String input = addressBar.getText().toString().trim();
        if (input.isEmpty()) return;
        String url = normalizeInput(input);
        Tab tab = tabs.get(activeTabId);
        if (tab != null) tab.url = url;
        webView.loadUrl(url);
    }

    private String normalizeInput(String input) {
        if (input.matches("(?i)^[a-z][a-z0-9+.-]*://.*$")) return input;
        if (input.contains(" ") || !input.contains(".")) {
            return String.format(Locale.US, BrowserPreferences.searchTemplate(this),
                    URLEncoder.encode(input, StandardCharsets.UTF_8));
        }
        return "https://" + input;
    }

    private void goBack() {
        if (webView != null && webView.canGoBack()) webView.goBack();
    }

    private void goForward() {
        if (webView != null && webView.canGoForward()) webView.goForward();
    }

    private void reload() {
        if (webView != null) webView.reload();
    }

    private void showTabsMenu() {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, tabCount);
        for (Tab tab : tabs.values()) {
            String label = (tab.title == null || tab.title.isEmpty()) ? tab.url : tab.title;
            MenuItem item = popup.getMenu().add(label == null ? "Tab" : label);
            item.setOnMenuItemClickListener(v -> {
                activateTab(tab.id);
                return true;
            });
        }
        popup.getMenu().add("New tab").setOnMenuItemClickListener(v -> {
            createTab(BrowserPreferences.homepage(this), false);
            return true;
        });
        popup.show();
    }

    private void showWebViewLimitDialog() {
        final int[] values = {1, 2, 3, 5};
        final String[] labels = {
                "1 — Hemat RAM (default)",
                "2 — Seimbang",
                "3 — Multitasking",
                "5 — Maksimum"
        };
        int current = BrowserPreferences.maxActiveWebViews(this);
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == current) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("Active WebViews")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    BrowserPreferences.setMaxActiveWebViews(this, values[which]);
                    enforceConfiguredWebViewLimit();
                    updateMemoryModeSummary();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void enforceConfiguredWebViewLimit() {
        int max = BrowserPreferences.maxActiveWebViews(this);
        while (countLiveWebViews() > max) {
            Tab candidate = findLeastRecentlyUsedLiveTab(activeTabId);
            if (candidate == null) break;
            saveTabState(candidate);
            destroyTabWebView(candidate);
        }
    }

    private void updateMemoryModeSummary() {
        if (tabCount == null) return;
        tabCount.setText(String.valueOf(tabs.size()));
        tabCount.setContentDescription("Tabs: " + tabs.size() + ", " + BrowserPreferences.maxActiveWebViews(this) + " active WebViews");
    }

    private void showMainMenu(View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        popup.getMenu().add("New tab").setOnMenuItemClickListener(v -> {
            createTab(BrowserPreferences.homepage(this), false);
            return true;
        });
        popup.getMenu().add("Bookmarks (coming soon)").setEnabled(false);
        popup.getMenu().add("Incognito tab").setOnMenuItemClickListener(v -> {
            createTab(BrowserPreferences.homepage(this), true);
            Toast.makeText(this, "Incognito: cookies and site data will be cleared when this tab closes.", Toast.LENGTH_LONG).show();
            return true;
        });
        popup.getMenu().add("Performance: "+BrowserPreferences.maxActiveWebViews(this)+" active WebView(s)").setOnMenuItemClickListener(v -> {
            showWebViewLimitDialog();
            return true;
        });
        popup.getMenu().add("Clear site data").setOnMenuItemClickListener(v -> {
            CookieManager.getInstance().removeAllCookies(value -> {
                CookieManager.getInstance().flush();
                Toast.makeText(this, "Cookies cleared", Toast.LENGTH_SHORT).show();
            });
            return true;
        });
        popup.getMenu().add("Open current page externally").setOnMenuItemClickListener(v -> {
            openExternal(webView == null ? null : webView.getUrl());
            return true;
        });
        popup.getMenu().add("About").setOnMenuItemClickListener(v -> {
            Toast.makeText(this, "Pine Browser 0.1.0 — WebView-first, no bundled Chromium.", Toast.LENGTH_LONG).show();
            return true;
        });
        popup.show();
    }

    private void openExternal(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No external browser available", Toast.LENGTH_LONG).show();
        }
    }

    private boolean shouldOpenOAuthExternally(Uri uri) {
        if (!BrowserPreferences.openExternalAuth(this)) return false;
        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        path = path == null ? "" : path.toLowerCase(Locale.US);

        if (host.equals("accounts.google.com") && path.contains("/o/oauth2/")) return true;
        if (host.equals("login.microsoftonline.com") && path.contains("/oauth2/")) return true;
        if (host.equals("login.live.com") && path.contains("/oauth20_authorize.srf")) return true;
        if (host.equals("github.com") && path.equals("/login/oauth/authorize")) return true;
        if (host.endsWith(".auth0.com") && path.contains("/authorize")) return true;
        if (host.equals("appleid.apple.com") && path.contains("/auth/authorize")) return true;
        return host.equals("www.facebook.com") && path.contains("/dialog/oauth");
    }

    private void openOAuthExternally(Uri uri) {
        try {
            CustomTabsIntent intent = new CustomTabsIntent.Builder().build();
            intent.launchUrl(this, uri);
        } catch (Exception e) {
            openExternal(uri.toString());
        }
        Toast.makeText(this, "Authentication opened outside embedded WebView.", Toast.LENGTH_SHORT).show();
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
        if (OAUTH_SCHEME.equalsIgnoreCase(data.getScheme()) && OAUTH_HOST.equalsIgnoreCase(data.getHost())) {
            // Generic callback hook reserved for apps/providers that explicitly support a redirect URI
            // into this browser. We do not attempt to copy cookies from Custom Tabs into WebView.
            Toast.makeText(this, "OAuth callback received", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            String incoming = data.toString();
            if (webView == null) {
                createTab(incoming, false);
            } else {
                webView.loadUrl(incoming);
            }
        }
    }

    private void restoreTabs(Bundle state) {
        ArrayList<String> ids = state.getStringArrayList(STATE_TABS);
        if (ids == null) return;
        for (String id : ids) {
            Bundle tabBundle = state.getBundle(TAB_PREFIX + id);
            if (tabBundle == null) continue;
            Tab tab = new Tab(id, tabBundle.getString("url", BrowserPreferences.homepage(this)), tabBundle.getBoolean("incognito", false));
            tab.title = tabBundle.getString("title", "New tab");
            tab.state = tabBundle.getBundle("state");
            tabs.put(id, tab);
        }
        activeTabId = state.getString(STATE_ACTIVE);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        saveActiveWebViewState();
        ArrayList<String> ids = new ArrayList<>(tabs.keySet());
        outState.putStringArrayList(STATE_TABS, ids);
        outState.putString(STATE_ACTIVE, activeTabId);
        for (Tab tab : tabs.values()) {
            Bundle b = new Bundle();
            b.putString("url", tab.url);
            b.putString("title", tab.title);
            b.putBoolean("incognito", tab.incognito);
            b.putBundle("state", tab.state);
            outState.putBundle(TAB_PREFIX + tab.id, b);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        for (Tab tab : tabs.values()) {
            saveTabState(tab);
            destroyTabWebView(tab);
        }
        webView = null;
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class BrowserWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if ("intent".equalsIgnoreCase(uri.getScheme())) {
                openExternal(uri.toString());
                return true;
            }
            if (shouldOpenOAuthExternally(uri)) {
                openOAuthExternally(uri);
                return true;
            }
            String scheme = uri.getScheme();
            if (scheme != null && !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return shouldOverrideUrlLoading(view, new SimpleWebResourceRequest(Uri.parse(url)));
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Tab tab = tabs.get(activeTabId);
            if (tab != null) tab.url = url;
            addressBar.setText(url);
            addressBar.setSelection(addressBar.length());
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Tab tab = tabs.get(activeTabId);
            if (tab != null) {
                tab.url = url;
                tab.title = view.getTitle();
            }
            addressBar.setText(url);
            addressBar.setSelection(addressBar.length());
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            Tab tab = tabs.get(activeTabId);
            if (tab != null && title != null) tab.title = title;
        }

        @Override
        public void onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            Toast.makeText(MainActivity.this, "Web page renderer crashed; recovering tab.", Toast.LENGTH_LONG).show();
            Tab tab = tabs.get(activeTabId);
            if (tab != null) {
                tab.state = null;
                tab.webView = null;
            }
            webView = null;
            if (activeTabId != null) activateTab(activeTabId);
        }
    }

    private final class BrowserChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
            }
            MainActivity.this.filePathCallback = filePathCallback;
            try {
                Intent intent = fileChooserParams.createIntent();
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            } catch (ActivityNotFoundException e) {
                MainActivity.this.filePathCallback = null;
                return false;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            if (filePathCallback != null) filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private static final class SimpleWebResourceRequest implements WebResourceRequest {
        private final Uri uri;
        SimpleWebResourceRequest(Uri uri) { this.uri = uri; }
        @Override public Uri getUrl() { return uri; }
        @Override public boolean isForMainFrame() { return true; }
        @Override public boolean isRedirect() { return false; }
        @Override public boolean hasGesture() { return true; }
        @Override public String getMethod() { return "GET"; }
        @Override public Map<String, String> getRequestHeaders() { return new LinkedHashMap<>(); }
    }

    private static final class TextDrawable extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final String text;
        TextDrawable(Context context, String text) {
            this.text = text;
            paint.setColor(0xFFF5F5F5);
            paint.setTextSize(20 * context.getResources().getDisplayMetrics().scaledDensity);
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        }
        @Override public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float y = b.centerY() - (paint.ascent() + paint.descent()) / 2f;
            canvas.drawText(text, b.centerX(), y, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return 48; }
        @Override public int getIntrinsicHeight() { return 48; }
    }
}
