package net.wingress.mobivious.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.wingress.mobivious.data.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** A single dismissible prompt leaves local downloads and server selection reachable. */
@Composable
internal fun InstanceAccessPrompt(vm: AppViewModel, signIn: () -> Unit) {
    val issue by vm.access.issue.collectAsStateWithLifecycle()
    val revision by vm.access.promptRevision.collectAsStateWithLifecycle()
    var dismissed by remember { mutableStateOf<AccessIssue?>(null) }
    var verifying by rememberSaveable { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    LaunchedEffect(revision) { dismissed = null }
    LaunchedEffect(issue) { if (issue == null) dismissed = null }
    val active = issue?.takeIf { it.context == vm.api.context() }
    if (active != null && active != dismissed && !verifying && vm.route != "sign-in") {
        AlertDialog(onDismissRequest = { dismissed = active }, title = { Text("Instance access") },
            text = { Text(active.reason.message) }, confirmButton = {
                TextButton(onClick = {
                    dismissed = active
                    if (active.reason == AccessReason.LOGIN) signIn() else verifying = true
                }, modifier = Modifier.testTag("instance-access-action")) {
                    Text(if (active.reason == AccessReason.LOGIN) "Sign in" else "Verify")
                }
            }, dismissButton = { TextButton({ dismissed = active }) { Text("Later") } })
    }
    if (verifying) key(vm.api.context()) {
        VerificationDialog(vm, { verifying = false }) { verifying = false; completed = true }
    }
    if (completed) AlertDialog(onDismissRequest = { completed = false }, title = { Text("Access restored") },
        text = { Text("Retry browsing or playback to continue. Downloads can be retried from Downloads.") },
        confirmButton = { TextButton({ completed = false; vm.retryBrowse() }, Modifier.testTag("instance-access-retry")) { Text("Retry browsing") } },
        dismissButton = { TextButton({ completed = false }) { Text("Done") } })
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VerificationDialog(vm: AppViewModel, dismiss: () -> Unit, complete: () -> Unit) {
    val target = remember { vm.api.context() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var failedView by remember { mutableStateOf<WebView?>(null) }
    val supported = remember { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }
    val view = remember(reload) {
        if (!supported) null else WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            val origin = target.server.toHttpUrl()
            val rules = setOf(InstanceAccess.origin(origin))
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(this, "mobiviousVerification", rules) { _, message, source, main, _ ->
                    if (!main || source.toString().toHttpUrlOrNull()?.let { InstanceAccess.sameOrigin(it, target.server) } != true || busy || target != vm.api.context()) return@addWebMessageListener
                    val body = message.data?.takeIf { it.length <= 4096 } ?: return@addWebMessageListener
                    scope.launch {
                        busy = true; error = null
                        try {
                            val value = JSONObject(body)
                            vm.access.complete(target, if (value.isNull("verificationToken")) null else value.getString("verificationToken"),
                                if (value.isNull("expiresAt")) null else value.getLong("expiresAt"), value.getBoolean("enabled"))
                            complete()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "Verification failed. Try again." }
                        finally { busy = false }
                    }
                }
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(this,
                    "if(window===window.top)window.addEventListener('invidious-verification-complete',function(e){mobiviousVerification.postMessage(JSON.stringify(e.detail));});", rules)
            }
            webViewClient = VerificationWebClient(target.server, { error = it }, { failedView = it })
            loadUrl(target.server + "/verify?client=native")
        }
    }
    DisposableEffect(view) { onDispose { if (view != null && view !== failedView) { view.stopLoading(); view.destroy() } } }
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).testTag("instance-verification"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Verify access", style = MaterialTheme.typography.titleLarge)
                Text(target.server, style = MaterialTheme.typography.bodySmall)
                if (!supported) Text("Update Android System WebView to verify access on this device.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                view?.takeIf { it !== failedView }?.let { webView ->
                    key(webView) { AndroidView({ webView }, Modifier.weight(1f).fillMaxWidth()) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ error = null; reload++ }, enabled = supported && !busy, modifier = Modifier.testTag("instance-verification-retry")) { Text("Retry") }
                    TextButton(dismiss, Modifier.testTag("instance-verification-cancel")) { Text("Cancel") }
                }
            }
        }
    }
}

/** Handles renderer loss as a retryable page failure, without terminating the native activity. */
private class VerificationWebClient(private val server: String, private val failure: (String) -> Unit,
    private val rendererGone: (WebView) -> Unit) : WebViewClient() {
    private fun trusted(url: String) = url.toHttpUrlOrNull()?.let { InstanceAccess.sameOrigin(it, server) && it.encodedPath == "/verify" } == true
    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
        rendererGone(view)
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        view.destroy()
        failure("Verification stopped. Retry to load a fresh page.")
        return true
    }
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.isForMainFrame && !trusted(request.url.toString())
    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (!trusted(url)) { view.stopLoading(); failure("The verification page left this instance. Try again.") }
    }
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) failure("Verification could not load. Check the connection and retry.")
    }
    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
        if (request.isForMainFrame) failure("Verification could not load (${response.statusCode}). Retry.")
    }
}
