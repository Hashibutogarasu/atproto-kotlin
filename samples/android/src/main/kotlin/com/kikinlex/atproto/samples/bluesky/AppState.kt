package com.kikinlex.atproto.samples.bluesky

import com.kikinlex.atproto.samples.bluesky.session.Session

/**
 * Top-level app state. `MainActivity` reads the stored session on `onCreate`
 * and transitions through: `Loading → LoggedOut` (no session) or
 * `Loading → LoggedIn(session)` (session present). `LoginScreen.onLoggedIn`
 * and `FeedScreen.onLogout` drive the remaining transitions.
 *
 * Intentionally a sealed interface rather than a StateFlow/ViewModel setup:
 * two screens and one in-flight auth transition don't justify the ceremony.
 */
sealed interface AppState {
    data object Loading : AppState
    data object LoggedOut : AppState
    data class LoggedIn(val session: Session) : AppState
}
