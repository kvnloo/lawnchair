package app.lawnchair.zer0.voice.z0live;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.View;
import android.webkit.ClientCertRequest;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.launcher3.R;
import java.io.ByteArrayInputStream;

/** A disposable web panel for the owner's configured Ripple HTTPS origin. */
public final class RippleActivity extends ComponentActivity {
    private WebView web;
    private Uri origin;
    private TextView error;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (web != null && web.canGoBack()) web.goBack();
                else finish();
            }
        });
        discardIncomingIntent(getIntent());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF171D23);
        LinearLayout toolbar = new LinearLayout(this);
        TextView title = new TextView(this);
        title.setText(R.string.z0live_ripple);
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setPadding(20, 16, 12, 16);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = new Button(this);
        close.setText(R.string.z0live_close);
        close.setAllCaps(false);
        close.setOnClickListener(ignored -> finish());
        toolbar.addView(close);
        root.addView(toolbar);
        error = new TextView(this);
        error.setTextColor(Color.WHITE);
        error.setPadding(20, 16, 20, 16);
        error.setVisibility(View.GONE);
        root.addView(error);
        setContentView(root);
        String url;
        try {
            url = new Z0LiveStore(this).rippleUrl();
            if (url == null) {
                showError(R.string.z0live_ripple_missing);
                return;
            }
            origin = Uri.parse(PairingValidation.validatePanel(url));
        } catch (RuntimeException failure) {
            showError(R.string.z0live_storage_error);
            return;
        }
        web = new WebView(this);
        web.setSaveEnabled(false);
        web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setGeolocationEnabled(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new PanelClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.deny();
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String site,
                    GeolocationPermissions.Callback callback) {
                callback.invoke(site, false, false);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture,
                    android.os.Message result) {
                return false;
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                callback.onReceiveValue(null);
                return true;
            }
        });
        web.setDownloadListener((download, agent, disposition, type, length) ->
                confirmExternal(Uri.parse(download)));
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        web.loadUrl(url);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        discardIncomingIntent(intent);
    }

    private void discardIncomingIntent(Intent incoming) {
        incoming.setData(null);
        incoming.setClipData(null);
        incoming.replaceExtras((Bundle) null);
        setIntent(new Intent(this, RippleActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
    }

    private boolean sameOrigin(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && uri.getUserInfo() == null && uri.getHost() != null
                && uri.getHost().equalsIgnoreCase(origin.getHost())
                && port(uri) == port(origin);
    }

    private int port(Uri uri) {
        return uri.getPort() == -1 ? 443 : uri.getPort();
    }

    private void confirmExternal(Uri uri) {
        if (destroyed || isFinishing() || uri == null || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null || uri.getUserInfo() != null) return;
        new AlertDialog.Builder(this).setTitle(R.string.z0live_external_title)
                .setMessage(getString(R.string.z0live_external_message, uri.getHost()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.z0live_external_open, (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri)
                                .addCategory(Intent.CATEGORY_BROWSABLE));
                    } catch (RuntimeException failure) {
                        showError(R.string.z0live_ripple_failed);
                    }
                }).show();
    }

    private void showError(int resource) {
        if (destroyed) return;
        error.setText(resource);
        error.setVisibility(View.VISIBLE);
    }

    private final class PanelClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (sameOrigin(request.getUrl())) return false;
            if (request.isForMainFrame() && request.hasGesture()) confirmExternal(request.getUrl());
            return true;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            if (sameOrigin(uri)) return false;
            confirmExternal(uri);
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (sameOrigin(request.getUrl())) return null;
            return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                    java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
            if (!sameOrigin(Uri.parse(url))) {
                view.stopLoading();
                showError(R.string.z0live_ripple_failed);
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError sslError) {
            handler.cancel();
            showError(R.string.z0live_ripple_failed);
        }

        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                String host, String realm) {
            handler.cancel();
        }

        @Override
        public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
            request.cancel();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError failure) {
            if (request.isForMainFrame()) showError(R.string.z0live_ripple_failed);
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (web != null) {
            web.stopLoading();
            web.setWebChromeClient(null);
            web.setWebViewClient(new WebViewClient());
            ((android.view.ViewGroup) web.getParent()).removeView(web);
            web.clearHistory();
            web.removeAllViews();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
