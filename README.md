# Pine Browser

Minimal Android browser shell built on the device's Android System WebView/Chromium. The app does **not** bundle Chromium and does **not** use the Android NDK.

## Design goals

- Keep the app itself small: Java + AndroidX AppCompat + AndroidX Browser (Custom Tabs) + AndroidX WebKit.
- Default to one live WebView at a time; users can raise the live-WebView cap to 2, 3, or 5 from the Performance menu. Inactive tabs keep serializable WebView state; when the cap is reached, the least-recently-used inactive WebView is destroyed and later recreated from saved state.
- Persist normal WebView cookies, local storage, IndexedDB, and cache by using the platform WebView profile.
- Do not create browser background services, workers, telemetry, sync, or account infrastructure.
- Use Android DownloadManager for downloads.
- Use the platform file picker for upload forms.
- Hand off known OAuth authorization endpoints to Custom Tabs rather than pretending embedded OAuth is universally supported.

## Important OAuth limitation

There is no generic way for a third-party browser shell to force every OAuth provider to authenticate a WebView. Custom Tabs and WebView can use different browser/profile cookie stores, and a callback URL belonging to a website normally returns to that website rather than to this app. This project therefore provides an explicit external-auth handoff for common authorization endpoints and a deep-link callback hook for providers/clients that are configured to use `pinebrowser://oauth/callback`.

For production authentication, prefer the provider's supported Android/native flow (for example Credential Manager or Authorization Code + PKCE) when the application itself owns the OAuth client. Do not ship OAuth client secrets in the APK.

## Build

The repository is intentionally usable without Android Studio or an installed Android SDK on the development machine. GitHub Actions installs the required SDK packages and runs Gradle directly.

Current build tool choices are pinned:

- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- JDK 17
- compileSdk / targetSdk 36
- AndroidX AppCompat 1.8.0
- AndroidX WebKit 1.17.0
- minSdk 24

Push to `main`/`master` or open a pull request. The workflow builds debug and release APKs and uploads them as artifacts.

## Local build

If Android SDK + JDK 17 are installed locally:

```text
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
gradle :app:assembleDebug
```

The project intentionally does not require the NDK.

## Current scope

Included:

- address/search bar
- HTTP/HTTPS navigation
- back/forward/reload
- tabs with a configurable active-WebView pool (default 1)
- tab state restoration
- persistent cookies/storage for normal tabs
- basic incognito-tab flag and site-data clearing hook
- file upload
- downloads
- external links and known OAuth authorization handoff
- renderer-crash recovery
- configurable WebView concurrency with a RAM-saving default
- configuration pinned for GitHub Actions

Not yet included:

- bookmarks UI/database
- history UI/database
- password manager integration
- reader mode/ad blocking
- full per-site permissions UI
- generic OAuth token/cookie bridging
- sync

Those are intentionally omitted from the first version to keep the browser shell lightweight.
