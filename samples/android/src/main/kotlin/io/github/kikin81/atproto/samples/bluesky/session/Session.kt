package io.github.kikin81.atproto.samples.bluesky.session

import kotlinx.serialization.Serializable

/**
 * One authenticated AT Protocol session, as returned by the generated
 * `com.atproto.server.createSession` procedure.
 *
 * Persisted verbatim to [SessionStore] (JSON-serialized). The sample does not
 * implement refresh-token rotation in v1 — when [accessJwt] expires, the user
 * is logged out and returns to the login screen.
 */
@Serializable
data class Session(
    val accessJwt: String,
    val refreshJwt: String,
    val did: String,
    val handle: String,
    val pdsUrl: String = DEFAULT_PDS_URL,
) {
    companion object {
        const val DEFAULT_PDS_URL = "https://bsky.social"
    }
}
