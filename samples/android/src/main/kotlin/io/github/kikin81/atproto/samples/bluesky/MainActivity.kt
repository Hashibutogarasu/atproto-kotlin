package io.github.kikin81.atproto.samples.bluesky

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.kikin81.atproto.oauth.AtOAuth
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import io.github.kikin81.atproto.samples.bluesky.session.AndroidOAuthSessionStore
import io.github.kikin81.atproto.samples.bluesky.ui.FeedScreen
import io.github.kikin81.atproto.samples.bluesky.ui.LoginScreen
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.launch

/**
 * Single activity. Manages the OAuth flow lifecycle:
 *
 * 1. `onCreate`: creates [AtOAuth], checks for a persisted session.
 * 2. Login: [LoginScreen] calls `oauth.beginLogin(handle)` → opens
 *    Custom Tabs → user authenticates on their PDS.
 * 3. Redirect: the browser redirects to `atproto-kotlin-sample://oauth-redirect`
 *    which the intent filter captures. `onNewIntent` stores the URI.
 * 4. Complete: on recomposition, the app calls `oauth.completeLogin(uri)`
 *    which exchanges the code for DPoP-bound tokens.
 * 5. Feed: [FeedScreen] calls `oauth.createClient()` and renders the timeline.
 *
 * `singleTask` launch mode ensures the redirect intent replaces the
 * existing activity instance instead of creating a new one.
 */
class MainActivity : ComponentActivity() {

    private lateinit var oauth: AtOAuth
    private lateinit var sessionStore: OAuthSessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionStore = AndroidOAuthSessionStore(applicationContext)
        oauth = AtOAuth(
            clientMetadataUrl = CLIENT_METADATA_URL,
            sessionStore = sessionStore,
            httpClient = HttpClient(CIO),
        )

        setContent {
            BlueskySampleTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App(
                        oauth = oauth,
                        sessionStore = sessionStore,
                        launchCustomTab = { url ->
                            CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, Uri.parse(url))
                        },
                    )
                }
            }
        }

        handleRedirectIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRedirectIntent(intent)
    }

    private fun handleRedirectIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == REDIRECT_SCHEME && uri.host == REDIRECT_HOST) {
            pendingRedirectUri = uri.toString()
        }
    }

    companion object {
        const val CLIENT_METADATA_URL = "https://kikin81.github.io/atproto-kotlin/oauth/client-metadata.json"
        const val REDIRECT_SCHEME = "atproto-kotlin-sample"
        const val REDIRECT_HOST = "oauth-redirect"

        @Volatile
        var pendingRedirectUri: String? = null
    }
}

@Composable
private fun App(
    oauth: AtOAuth,
    sessionStore: OAuthSessionStore,
    launchCustomTab: (String) -> Unit,
) {
    var state: AppState by remember { mutableStateOf(AppState.Loading) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val existing = sessionStore.load()
        state = if (existing != null) AppState.LoggedIn(existing.handle) else AppState.LoggedOut
    }

    // Check for pending OAuth redirect
    val redirect = MainActivity.pendingRedirectUri
    if (redirect != null && (state is AppState.LoggedOut || state is AppState.Loading)) {
        MainActivity.pendingRedirectUri = null
        LaunchedEffect(redirect) {
            runCatching { oauth.completeLogin(redirect) }
                .onSuccess {
                    val session = sessionStore.load()
                    state = if (session != null) AppState.LoggedIn(session.handle) else AppState.LoggedOut
                }
                .onFailure { state = AppState.LoggedOut }
        }
    }

    when (val current = state) {
        AppState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AppState.LoggedOut -> {
            LoginScreen(
                onLogin = { handle ->
                    scope.launch {
                        runCatching {
                            val authUrl = oauth.beginLogin(handle)
                            launchCustomTab(authUrl)
                        }
                    }
                },
            )
        }
        is AppState.LoggedIn -> {
            FeedScreen(
                handle = current.handle,
                oauth = oauth,
                onLogout = {
                    scope.launch {
                        oauth.logout()
                        state = AppState.LoggedOut
                    }
                },
            )
        }
    }
}

@Composable
private fun BlueskySampleTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}
