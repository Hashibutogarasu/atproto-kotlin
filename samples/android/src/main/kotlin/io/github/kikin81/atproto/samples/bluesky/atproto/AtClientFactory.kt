package io.github.kikin81.atproto.samples.bluesky.atproto

import io.github.kikin81.atproto.runtime.AuthProvider
import io.github.kikin81.atproto.runtime.BearerTokenAuth
import io.github.kikin81.atproto.runtime.NoAuth
import io.github.kikin81.atproto.runtime.XrpcClient
import io.github.kikin81.atproto.samples.bluesky.session.Session
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

/**
 * Single entry point for constructing an [XrpcClient] wired to our sample's
 * defaults (Ktor CIO engine, `https://bsky.social` base URL, bearer-auth when
 * a [Session] is provided).
 *
 * The [engine] parameter exists so the smoke test can swap in a
 * `MockEngine` — production callers pass nothing and get CIO.
 */
object AtClientFactory {
    /**
     * Build an [XrpcClient] for the given [session]. Pass `null` for an
     * unauthenticated client (used on the login screen before a session
     * exists).
     */
    fun create(
        session: Session?,
        engine: HttpClientEngine = CIO.create { },
        baseUrl: String = session?.pdsUrl ?: Session.DEFAULT_PDS_URL,
    ): XrpcClient {
        val authProvider: AuthProvider = if (session != null) {
            BearerTokenAuth(session.accessJwt)
        } else {
            NoAuth
        }
        return XrpcClient(
            baseUrl = baseUrl,
            httpClient = HttpClient(engine),
            authProvider = authProvider,
        )
    }
}
