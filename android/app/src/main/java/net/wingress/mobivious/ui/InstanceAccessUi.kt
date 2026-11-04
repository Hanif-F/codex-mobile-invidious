package net.wingress.mobivious.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.CookieManager
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.webkit.WebViewCompat
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.wingress.mobivious.data.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** A single dismissible prompt leaves local downloads and server selection reachable. */
@Composable
internal fun InstanceAccessPrompt(vm: AppViewModel, visible: Boolean, signIn: () -> Unit) {
    val issue by vm.access.issue.collectAsStateWithLifecycle()
    val revision by vm.access.promptRevision.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    // Save only an opaque identity, never account credentials, across activity recreation.
    val contextKey = InstanceAccess.cacheScope(vm.api.context())
    var dismissed by rememberSaveable(contextKey) { mutableStateOf<String?>(null) }
    var requested by rememberSaveable(contextKey) { mutableStateOf<String?>(null) }
    val active = issue?.takeIf { it.context == vm.api.context() }
    val flow = "$contextKey:$revision"
    val attempt = active?.let { "$flow:${it.reason}" }
    // Cancelling verification covers both stages, but choosing sign-in must not
    // suppress a later edge challenge on the native authentication endpoints.
    val dismissal = if (active?.reason == AccessReason.LOGIN) attempt else "$flow:verification"
    if (!visible || !lifecycle.isAtLeast(Lifecycle.State.RESUMED) || (vm.route == "sign-in" && active?.reason != AccessReason.CLOUDFLARE) ||
        active == null || dismissal == dismissed) return
    if (active.reason in listOf(AccessReason.VERIFICATION, AccessReason.CLOUDFLARE) || requested == attempt) key(attempt) {
        VerificationDialog(vm, { dismissed = dismissal })
    } else {
        AlertDialog(onDismissRequest = { dismissed = dismissal }, title = { Text("Instance access") },
            text = { Text(active.reason.message) }, confirmButton = {
                TextButton(onClick = {
                    if (active.reason == AccessReason.LOGIN) { dismissed = dismissal; signIn() }
                    else requested = attempt
                }, modifier = Modifier.testTag("instance-access-action")) {
                    Text(if (active.reason == AccessReason.LOGIN) "Sign in" else "Verify")
                }
            }, dismissButton = { TextButton({ dismissed = dismissal }) { Text("Later") } })
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VerificationDialog(vm: AppViewModel, dismiss: () -> Unit) {
    val target = remember { vm.api.context() }
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var failedView by remember { mutableStateOf<WebView?>(null) }
    val liveViews = remember { mutableSetOf<WebView>() }
    val edgeRequired = remember { vm.access.issue.value?.reason == AccessReason.CLOUDFLARE }
    val client = if (edgeRequired) "native-cloudflare" else "native"
    var completionBody by remember { mutableStateOf<String?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    val cookieInfoSupported = remember { WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO) }
    val supported = remember { if (edgeRequired) cookieInfoSupported else
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }
    val view = remember(reload) {
        if (!supported) null else WebView(context).apply {
            liveViews.add(this)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            val origin = target.server.toHttpUrl()
            val rules = setOf(InstanceAccess.origin(origin))
            val generation = reload
            if (!edgeRequired && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.addWebMessageListener(this, "mobiviousVerification", rules) { webView, message, source, main, _ ->
                val trustedPage = webView.url?.toHttpUrlOrNull()?.let { InstanceAccess.sameOrigin(it, target.server) && it.encodedPath == "/verify" && it.queryParameter("client") == client } == true
                if (webView !in liveViews || !main || !trustedPage || source.toString().toHttpUrlOrNull()?.let { InstanceAccess.sameOrigin(it, target.server) } != true ||
                    completed || generation != reload || target != vm.api.context()) return@addWebMessageListener
                message.data?.takeIf { it.length <= 4096 }?.let { completionBody = it }
            }
            if (!edgeRequired && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) WebViewCompat.addDocumentStartJavaScript(this,
                "if(window===window.top)window.addEventListener('invidious-verification-complete',function(e){mobiviousVerification.postMessage(JSON.stringify(e.detail));});", rules)
            webViewClient = VerificationWebClient(target.server, client, { error = it }, { liveViews.remove(it); failedView = it },
                { pageReady = false }, { pageReady = true })
            val cookies = CookieManager.getInstance()
            cookies.setAcceptCookie(true)
            cookies.setAcceptThirdPartyCookies(this, true)
            // Remove only old clearance, never copy browser account cookies into native requests.
            val old = clearanceCookies(target.server, settings.userAgentString)
            val accepted = vm.access.clearance(target)
            val changes = old.map { it.cookie.toString() + "; Max-Age=0" } + listOfNotNull(accepted?.cookie?.toString())
            fun prepare(index: Int) {
                if (this !in liveViews || generation != reload || target != vm.api.context()) return
                if (index == changes.size) loadUrl(target.server + "/verify?client=$client")
                else cookies.setCookie(target.server + "/", changes[index]) { prepare(index + 1) }
            }
            prepare(0)
        }
    }
    LaunchedEffect(view, failedView) {
        if (view == null || view === failedView) return@LaunchedEffect
        val triedCookies = mutableSetOf<String>()
        var failed = false
        while (isActive && !completed && target == vm.api.context()) {
            val edge = clearanceCookies(target.server, view.settings.userAgentString)
                .filter { it.usable(target.server.toHttpUrl(), view.settings.userAgentString, System.currentTimeMillis() / 1000) }
                .singleOrNull()
            val body = completionBody
            if (!edgeRequired && !failed && body != null) {
                completionBody = null
                busy = true
                try {
                    val value = JSONObject(body)
                    vm.access.complete(target, if (value.isNull("verificationToken")) null else value.getString("verificationToken"),
                        if (value.isNull("expiresAt")) null else value.getLong("expiresAt"), value.getBoolean("enabled"), edge)
                    completed = true
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "Verification failed. Try again."; failed = true }
                finally { busy = false }
            } else if (edgeRequired && pageReady && edge != null && triedCookies.add(edge.cookie.toString())) {
                // Rejection belongs to this candidate, not to a later browser-issued cookie.
                busy = true; error = null
                try {
                    vm.access.completeClearance(target, edge)
                    // A remaining backend gate opens a new, separately owned dialog.
                    completed = true
                }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "Verification failed. Try again." }
                finally { busy = false }
            }
            delay(250)
        }
    }
    DisposableEffect(view) { onDispose { if (view != null) { liveViews.remove(view); if (view !== failedView) { view.stopLoading(); view.destroy() } } } }
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).testTag("instance-verification"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (edgeRequired) "Cloudflare verification" else "Instance verification", style = MaterialTheme.typography.titleLarge)
                Text(target.server, style = MaterialTheme.typography.bodySmall)
                if (!supported) Text("Update Android System WebView to verify access on this device.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                view?.takeIf { it !== failedView }?.let { webView ->
                    key(webView) { AndroidView({ webView }, Modifier.weight(1f).fillMaxWidth()) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ error = null; completionBody = null; pageReady = false; reload++ }, enabled = supported && !busy, modifier = Modifier.testTag("instance-verification-retry")) { Text("Retry") }
                    TextButton(dismiss, Modifier.testTag("instance-verification-cancel")) { Text("Cancel") }
                }
            }
        }
    }
}

private fun clearanceCookies(server: String, userAgent: String): List<CloudflareClearance> {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) return emptyList()
    return CookieManagerCompat.getCookieInfo(CookieManager.getInstance(), server + "/")
        .mapNotNull { CloudflareClearance.parse(server.toHttpUrl(), it, userAgent) }
}

/** Handles renderer loss as a retryable page failure, without terminating the native activity. */
private class VerificationWebClient(private val server: String, private val client: String, private val failure: (String) -> Unit,
    private val rendererGone: (WebView) -> Unit, private val started: () -> Unit, private val ready: () -> Unit) : WebViewClient() {
    private fun trusted(url: String) = url.toHttpUrlOrNull()?.let { InstanceAccess.sameOrigin(it, server) && it.encodedPath == "/verify" && it.queryParameter("client") == client } == true
    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
        rendererGone(view)
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        view.destroy()
        failure("Verification stopped. Retry to load a fresh page.")
        return true
    }
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.isForMainFrame && !trusted(request.url.toString())
    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        started()
        if (!trusted(url)) { view.stopLoading(); failure("The verification page left this instance. Try again.") }
    }
    override fun onPageFinished(view: WebView, url: String) { if (trusted(url)) ready() }
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) failure("Verification could not load. Check the connection and retry.")
    }
    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
        if (request.isForMainFrame && response.responseHeaders?.entries?.none { it.key.equals("cf-mitigated", true) && it.value.equals("challenge", true) } != false)
            failure("Verification could not load (${response.statusCode}). Retry.")
    }
}
