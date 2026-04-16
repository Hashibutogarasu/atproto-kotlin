package io.github.kikin81.atproto.samples.bluesky

sealed interface AppState {
    data object Loading : AppState
    data object LoggedOut : AppState
    data class LoggedIn(val handle: String) : AppState
}
