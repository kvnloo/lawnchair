package app.lawnchair.zer0.voice.z0live

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.ClientCertRequest
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.R
import java.io.ByteArrayInputStream

/** Disposable, same-origin HTTPS panel. Compose owns the chrome; WebView only renders the panel. */
class RippleActivity : ComponentActivity() {
    private var web: WebView? = null
    private var origin: Uri? = null
    private var error by mutableStateOf<Int?>(null)
    private var externalLink by mutableStateOf<Uri?>(null)
    private var destroyed = false
    private var closeReceiverRegistered = false
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == CLOSE_RIPPLE) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val panel = web
                    if (panel != null && panel.canGoBack()) panel.goBack() else finish()
                }
            },
        )
        ContextCompat.registerReceiver(this, closeReceiver, IntentFilter(CLOSE_RIPPLE), ContextCompat.RECEIVER_NOT_EXPORTED)
        closeReceiverRegistered = true
        discardIncomingIntent(intent)
        val url = try {
            Z0LiveStore(this).rippleUrl()?.let { PairingValidation.validatePanel(it) }.also {
                if (it == null) {
                    showError(R.string.z0live_ripple_missing)
                } else {
                    origin = Uri.parse(it)
                }
            }
        } catch (_: RuntimeException) {
            showError(R.string.z0live_storage_error)
            null
        }
        setContent {
            LawnchairTheme {
                EdgeToEdge()
                PreferenceScaffold(
                    label = stringResource(R.string.z0live_ripple),
                    isExpandedScreen = true,
                    actions = {
                        TextButton(onClick = { finish() }) { Text(stringResource(R.string.z0live_close)) }
                    },
                    bottomBar = {},
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        error?.let {
                            Text(
                                stringResource(it),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                        if (url != null) {
                            AndroidView(factory = { createWebView(url) }, modifier = Modifier.weight(1f).fillMaxSize())
                        }
                    }
                }
                externalLink?.let { uri ->
                    AlertDialog(
                        onDismissRequest = { externalLink = null },
                        title = { Text(stringResource(R.string.z0live_external_title)) },
                        text = { Text(stringResource(R.string.z0live_external_message, uri.host.orEmpty())) },
                        confirmButton = {
                            TextButton(onClick = {
                                externalLink = null
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
                                } catch (_: RuntimeException) {
                                    showError(R.string.z0live_ripple_failed)
                                }
                            }) { Text(stringResource(R.string.z0live_external_open)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { externalLink = null }) { Text(stringResource(android.R.string.cancel)) }
                        },
                    )
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun createWebView(url: String): WebView = WebView(this).apply {
        web = this
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = PanelClient()
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()

            override fun onGeolocationPermissionsShowPrompt(site: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(site, false, false)
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean = false

            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                callback.onReceiveValue(null)
                return true
            }
        }
        setDownloadListener { download, _, _, _, _ -> confirmExternal(Uri.parse(download)) }
        loadUrl(url)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        discardIncomingIntent(intent)
    }

    private fun discardIncomingIntent(incoming: Intent) {
        incoming.data = null
        incoming.clipData = null
        incoming.replaceExtras(null as Bundle?)
        setIntent(Intent(this, RippleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
    }

    private fun sameOrigin(uri: Uri): Boolean {
        val allowed = origin ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null && uri.host != null &&
            uri.host.equals(allowed.host, ignoreCase = true) && port(uri) == port(allowed)
    }

    private fun port(uri: Uri): Int = if (uri.port == -1) 443 else uri.port

    private fun confirmExternal(uri: Uri) {
        if (destroyed || isFinishing || !uri.scheme.equals("https", ignoreCase = true) || uri.host == null || uri.userInfo != null) return
        externalLink = uri
    }

    private fun showError(resource: Int) {
        if (!destroyed) error = resource
    }

    private inner class PanelClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (sameOrigin(request.url)) return false
            if (request.isForMainFrame && request.hasGesture()) confirmExternal(request.url)
            return true
        }

        @Deprecated("Legacy WebView callback")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            val uri = Uri.parse(url)
            if (sameOrigin(uri)) return false
            confirmExternal(uri)
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (sameOrigin(request.url)) return null
            return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (!sameOrigin(Uri.parse(url))) {
                view.stopLoading()
                showError(R.string.z0live_ripple_failed)
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, sslError: SslError) {
            handler.cancel()
            showError(R.string.z0live_ripple_failed)
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) = handler.cancel()

        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) = request.cancel()

        override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
            if (request.isForMainFrame) showError(R.string.z0live_ripple_failed)
        }
    }

    override fun onPause() {
        web?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web?.onResume()
    }

    override fun onDestroy() {
        destroyed = true
        if (closeReceiverRegistered) {
            unregisterReceiver(closeReceiver)
            closeReceiverRegistered = false
        }
        externalLink = null
        web?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            (parent as? ViewGroup)?.removeView(this)
            clearHistory()
            removeAllViews()
            destroy()
        }
        web = null
        super.onDestroy()
    }

    companion object {
        private const val CLOSE_RIPPLE = "app.lawnchair.z0live.CLOSE_RIPPLE"
    }
}
