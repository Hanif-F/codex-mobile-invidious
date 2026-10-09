package net.wingress.mobivious.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.wingress.mobivious.data.*
import org.json.JSONObject

internal val accountSettingsPages = setOf("Account", "Change username", "Change password", "Sessions & API tokens", "Create API token", "Delete account")

internal fun settingsParent(page: String): String = when (page) {
    "Settings" -> ""
    "Blocked channels" -> "Browsing"
    "Create API token" -> "Sessions & API tokens"
    "Change username", "Change password", "Sessions & API tokens", "Delete account" -> "Account"
    else -> "Settings"
}

@Composable
internal fun SignInScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    key(vm.api.context()) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).imePadding()
            .testTag("sign-in-screen"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Welcome to your library", style = MaterialTheme.typography.headlineSmall)
            Text("Sign in to your Invidious account on ${vm.store.server}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            AuthenticationForm(vm, vm.api.context())
        }
    }
}

@Composable
internal fun AccountSettingsContent(vm: AppViewModel, page: String, modifier: Modifier, navigate: (String) -> Unit, signIn: () -> Unit) {
    val account by vm.account.collectAsStateWithLifecycle()
    val busy by vm.accountBusy.collectAsStateWithLifecycle()
    val context = vm.api.context()
    when {
        account == null -> Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sign in to manage your account", style = MaterialTheme.typography.titleLarge)
            Text(vm.store.server, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = signIn, enabled = !busy) { Text("Sign in") }
        }
        page == "Sessions & API tokens" -> AccountSessionsScreen(vm, context, modifier) { navigate("Create API token") }
        page == "Create API token" -> TokenForm(vm, context, modifier) { navigate("Sessions & API tokens") }
        page != "Account" -> CredentialForm(vm, context, page, modifier) { navigate("Account") }
        else -> Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp).testTag("account-settings-screen")) {
            Text("Signed in as ${account!!.username}", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
            Text(vm.store.server, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionRow("Change username", enabled = !busy, icon = Icons.Default.Person) { navigate("Change username") }
            ActionRow("Change password", enabled = !busy, icon = Icons.Default.Lock) { navigate("Change password") }
            ActionRow("Sessions & API tokens", enabled = !busy, icon = Icons.Default.Devices) { navigate("Sessions & API tokens") }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            OutlinedButton(onClick = vm::logout, enabled = !busy, modifier = Modifier.padding(horizontal = 20.dp).testTag("account-sign-out")) { Text("Sign out") }
            TextButton(onClick = { navigate("Delete account") }, enabled = !busy, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Delete account", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SecretField(value: String, update: (String) -> Unit, label: String, tag: String, enabled: Boolean, modifier: Modifier = Modifier) {
    OutlinedTextField(value, update, label = { Text(label) }, singleLine = true, enabled = enabled,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = modifier.fillMaxWidth().testTag(tag))
}

@Composable
private fun FormStatus(busy: Boolean, error: String?) {
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("account-error"))
}

@Composable
private fun AuthenticationForm(vm: AppViewModel, context: ApiContext) {
    var signup by rememberSaveable { mutableStateOf(false) }
    // Secrets deliberately use remember, never saved state or persistent preferences.
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var config by remember { mutableStateOf<Registration?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun challenge() {
        loading = true
        try { config = vm.api.registration(context) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Unable to load registration." }
        finally { loading = false }
    }
    LaunchedEffect(signup) { error = null; password = ""; confirmation = ""; answer = ""; if (signup) challenge() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!signup, onClick = { if (!busy) signup = false }, label = { Text("Sign in") })
        FilterChip(signup, onClick = { if (!busy) signup = true }, label = { Text("Create account") })
    }
    OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true,
        enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("account-username")
            .semantics { contentType = if (signup) ContentType.NewUsername else ContentType.Username })
    SecretField(password, { password = it }, "Password", "account-password", !busy,
        Modifier.semantics { contentType = if (signup) ContentType.NewPassword else ContentType.Password })
    if (signup) {
        Text("Use 3–32 letters, numbers, underscores, dots or hyphens. Passwords need at least 15 characters, at most 72 UTF-8 bytes, and must not be common.", style = MaterialTheme.typography.bodySmall)
        SecretField(confirmation, { confirmation = it }, "Confirm password", "account-confirm-password", !busy,
            Modifier.semantics { contentType = ContentType.NewPassword })
        if (config?.enabled == false) Text("Registration is disabled on this instance.", color = MaterialTheme.colorScheme.error)
        if (config?.captchaImage?.isNotEmpty() == true) {
            AsyncImage(config!!.captchaImage, "CAPTCHA clock: enter its time as hour:minute:second", Modifier.size(200.dp).testTag("account-captcha"))
            OutlinedTextField(answer, { answer = it }, label = { Text("Clock time (H:MM:SS)") }, singleLine = true, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("account-captcha-answer"))
        }
        OutlinedButton(onClick = { scope.launch { error = null; answer = ""; challenge() } }, enabled = !busy && !loading) { Text("Refresh registration / CAPTCHA") }
    }
    FormStatus(busy || loading, error)
    Button(enabled = !busy && !loading && username.isNotBlank() && password.isNotEmpty() &&
        (!signup || config?.enabled == true && password == confirmation && (config!!.captchaToken.isEmpty() || answer.isNotBlank())),
        modifier = Modifier.testTag("account-auth-submit"), onClick = {
            busy = true; error = null
            scope.launch {
                try {
                    if (signup) vm.register(username.trim(), password, confirmation, answer, config!!.captchaToken)
                    else vm.login(username.trim(), password)
                    password = ""; confirmation = ""; answer = ""
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    val failure = e.message ?: "Unable to sign in."
                    if (signup) { answer = ""; challenge() }
                    error = failure
                } finally { busy = false }
            }
        }) { Text(if (signup) "Create account" else "Sign in") }
}

@Composable
private fun CredentialForm(vm: AppViewModel, context: ApiContext, page: String, modifier: Modifier, back: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var value by remember { mutableStateOf(if (page == "Change username") context.account?.username.orEmpty() else "") }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val deleting = page == "Delete account"
    val changingPassword = page == "Change password"
    fun submit() {
        busy = true; error = null
        scope.launch {
            try {
                if (deleting) vm.deleteAccount(password, context)
                else vm.changeCredentials(if (changingPassword) "password" else "username", JSONObject().put("password", password).apply {
                    if (changingPassword) put("newPassword", value).put("passwordConfirmation", confirmation) else put("username", value.trim())
                }, context)
                password = ""; value = ""; confirmation = ""; back()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Unable to update your account." }
            finally { busy = false; confirmDelete = false }
        }
    }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (deleting) "Permanently remove your account, subscriptions, history, playlists, preferences and sessions on ${context.server}."
            else "All old browser sessions and API tokens will be revoked. This app will stay signed in with a new session.")
        SecretField(password, { password = it }, "Current password", "account-current-password", !busy)
        if (!deleting) {
            if (changingPassword) {
                SecretField(value, { value = it }, "New password", "account-new-password", !busy)
                SecretField(confirmation, { confirmation = it }, "Confirm new password", "account-new-confirmation", !busy)
                Text("Use at least 15 characters and at most 72 UTF-8 bytes; avoid common passwords.", style = MaterialTheme.typography.bodySmall)
            } else OutlinedTextField(value, { value = it }, label = { Text("New username") }, singleLine = true, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("account-new-username"))
        }
        FormStatus(busy, error)
        Button(onClick = { if (deleting) confirmDelete = true else submit() }, enabled = !busy && password.isNotEmpty() &&
            (deleting || value.isNotBlank() && (!changingPassword || value == confirmation)), modifier = Modifier.testTag("account-change-submit")) { Text(if (deleting) "Delete account" else "Save changes") }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!busy) confirmDelete = false }, title = { Text("Delete ${context.account?.username}?") },
        text = { Text("Your account and its saved data on ${context.server} will be permanently deleted. This cannot be undone.") },
        confirmButton = { TextButton(enabled = !busy, onClick = ::submit) { Text("Permanently delete") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("Cancel") } })
}

private fun accountDate(seconds: Long) = DisplayFormats.timestamp(seconds).ifBlank { "Unknown date" }

@Composable
private fun AccountSessionsScreen(vm: AppViewModel, context: ApiContext, modifier: Modifier, create: () -> Unit) {
    var sessions by remember { mutableStateOf<List<AccountSession>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revoking by remember { mutableStateOf<AccountSession?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        busy = true; error = null
        try { sessions = vm.api.accountSessions(context); loaded = true }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Unable to load sessions." }
        finally { busy = false }
    }
    LaunchedEffect(context) { refresh() }
    LazyColumn(modifier.fillMaxWidth().testTag("account-sessions"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = create, enabled = !busy) { Text("Create API token") }
                OutlinedButton(onClick = { scope.launch { refresh() } }, enabled = !busy) { Text("Refresh") }
            }
            FormStatus(busy, error)
            if (loaded && sessions.isEmpty()) Text("No sessions")
        }
        items(sessions, key = { it.id }) { session ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (session.current) "This session" else if (session.type == "browser") "Browser session" else "API token", style = MaterialTheme.typography.titleMedium)
                    Text("Created ${accountDate(session.issuedAt)}")
                    Text(session.expiresAt?.let { "${if (it <= System.currentTimeMillis() / 1000) "Expired" else "Expires"} ${accountDate(it)}" } ?: "Expiry unavailable for this token")
                    OutlinedButton(onClick = { revoking = session }, enabled = !busy) { Text(if (session.current) "Sign out this session" else "Revoke") }
                }
            }
        }
    }
    revoking?.let { session ->
        AlertDialog(onDismissRequest = { if (!busy) revoking = null }, title = { Text(if (session.current) "Sign out this app?" else "Revoke session?") },
            text = { Text("The session created ${accountDate(session.issuedAt)} will lose access to your account.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    try { vm.revokeSession(session, context); revoking = null; if (!session.current) refresh() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Unable to revoke session." }
                    finally { busy = false; revoking = null }
                }
            }) { Text("Revoke") } }, dismissButton = { TextButton(enabled = !busy, onClick = { revoking = null }) { Text("Cancel") } })
    }
}

@Composable
private fun TokenForm(vm: AppViewModel, context: ApiContext, modifier: Modifier, back: () -> Unit) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var advanced by remember { mutableStateOf("") }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var expiry by rememberSaveable { mutableStateOf("30 days") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val androidContext = LocalContext.current
    val scopes = AccountPermissions.scopes(selected, advanced)
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (token != null) {
            Text("Copy this token now. It is only displayed here and grants the permissions you selected.")
            Text(token!!, modifier = Modifier.testTag("account-created-token"))
            Button(onClick = {
                val clip = ClipData.newPlainText("Invidious API token", token!!)
                if (Build.VERSION.SDK_INT >= 33) {
                    clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                }
                (androidContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                vm.message.value = "Token copied"
            }) { Text("Copy token") }
            OutlinedButton(onClick = { token = null; back() }) { Text("Done") }
        } else {
            Text("Choose the permissions to grant. No permissions are selected automatically.")
            AccountPermissions.groups.forEach { (label, _) ->
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(label in selected, { checked -> selected = if (checked) selected + label else selected - label }, enabled = !busy, modifier = Modifier.testTag("account-token-permission-$label"))
                    Text(label, modifier = Modifier.padding(top = 12.dp))
                }
            }
            TextButton(onClick = { showAdvanced = !showAdvanced }, enabled = !busy) { Text("Advanced API scopes") }
            if (showAdvanced) OutlinedTextField(advanced, { advanced = it }, label = { Text("Additional scopes, separated by spaces or commas") },
                supportingText = { Text("Example: GET:clips. :* grants all API permissions.") }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("account-token-scopes"))
            Text("Expiry", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountPermissions.expiries.keys.forEach { name -> FilterChip(expiry == name, { expiry = name }, enabled = !busy, label = { Text(name) }) }
            }
            SecretField(password, { password = it }, "Current password", "account-token-password", !busy)
            if (scopes.isNotEmpty()) Text("Permissions: ${scopes.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            FormStatus(busy, error)
            Button(enabled = !busy && password.isNotEmpty() && AccountPermissions.valid(scopes), onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        val days = AccountPermissions.expiries.getValue(expiry)
                        token = vm.createAccountToken(password, scopes, if (days == 0L) null else System.currentTimeMillis() / 1000 + days * 86400, context)
                        password = ""
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Unable to create token." }
                    finally { busy = false }
                }
            }, modifier = Modifier.testTag("account-create-token")) { Text("Authorize and create token") }
        }
    }
}
