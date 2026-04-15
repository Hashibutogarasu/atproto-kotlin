package io.github.kikin81.atproto.samples.bluesky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.kikin81.atproto.samples.bluesky.session.SessionStore
import io.github.kikin81.atproto.samples.bluesky.ui.FeedScreen
import io.github.kikin81.atproto.samples.bluesky.ui.LoginScreen

/**
 * Single activity. Reads the stored session on [onCreate] and hands off to
 * [App] which renders one of [AppState] → [LoginScreen] / [FeedScreen]
 * depending on whether a session is already present.
 *
 * No ViewModel, no navigation library, no DI framework — see design.md
 * Decision 4 in the `samples-android-bluesky-feed` change.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionStore = SessionStore.forContext(applicationContext)
        val initial = sessionStore.load()?.let { AppState.LoggedIn(it) } ?: AppState.LoggedOut

        setContent {
            BlueskySampleTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App(sessionStore = sessionStore, initial = initial)
                }
            }
        }
    }
}

@Composable
private fun App(sessionStore: SessionStore, initial: AppState) {
    var state: AppState by remember { mutableStateOf(initial) }

    when (val current = state) {
        AppState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AppState.LoggedOut -> {
            LoginScreen(
                sessionStore = sessionStore,
                onLoggedIn = { session -> state = AppState.LoggedIn(session) },
            )
        }
        is AppState.LoggedIn -> {
            FeedScreen(
                session = current.session,
                sessionStore = sessionStore,
                onLogout = { state = AppState.LoggedOut },
            )
        }
    }
}

/**
 * Minimal Material3 theme wrapper. Sample isn't meant to showcase theming;
 * we just honor light/dark and let the defaults do the rest.
 */
@Composable
private fun BlueskySampleTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}
