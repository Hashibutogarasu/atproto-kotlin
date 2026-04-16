package io.github.kikin81.atproto.oauth

import io.github.kikin81.atproto.runtime.AuthProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [AuthProvider] implementation that attaches DPoP proof-of-possession
 * headers on every XRPC request and handles token refresh transparently.
 *
 * On each request, produces:
 * - `Authorization: DPoP <access_token>`
 * - `DPoP: <signed-jwt-proof>` (with `ath`, `htm`, `htu`, `nonce`)
 *
 * When the PDS responds with HTTP 401:
 * - If `DPoP-Nonce` header is present → stores the nonce, retries
 * - If the access token is expired → refreshes via the token endpoint
 *   with the DPoP-bound refresh token, retries
 * - If the refresh token is revoked → clears the session, throws
 *   [OAuthSessionExpiredException]
 *
 * Refresh operations are serialized with a [Mutex] to prevent concurrent
 * refreshes from invalidating the session.
 */
class DpopAuthProvider(
    private var session: OAuthSession,
    private val signer: DpopSigner,
    private val sessionStore: OAuthSessionStore,
    private val refreshClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AuthProvider {

    private val refreshMutex = Mutex()
    private var pdsNonce: String? = session.pdsNonce
    private var authServerNonce: String? = session.authServerNonce

    override suspend fun authHeaders(method: String, url: String): Map<String, String> {
        val ath = DpopSigner.accessTokenHash(session.accessToken)
        val proof = signer.sign(
            method = method,
            url = url,
            accessTokenHash = ath,
            nonce = pdsNonce,
        )
        return mapOf(
            "Authorization" to "DPoP ${session.accessToken}",
            "DPoP" to proof,
        )
    }

    /**
     * Called by the consumer (or an interceptor) when a PDS request
     * returns HTTP 401. Handles DPoP-Nonce rotation and token refresh.
     *
     * @return `true` if the request should be retried with fresh headers,
     *   `false` if the session is irrecoverable (caller should re-auth).
     */
    suspend fun handleUnauthorized(
        dpopNonceHeader: String?,
        method: String,
        url: String,
    ): Boolean {
        if (dpopNonceHeader != null && dpopNonceHeader != pdsNonce) {
            pdsNonce = dpopNonceHeader
            persistNonces()
            return true
        }
        return refreshMutex.withLock { refreshTokens() }
    }

    private suspend fun refreshTokens(): Boolean {
        val proof = signer.sign(
            method = "POST",
            url = session.tokenEndpoint,
            nonce = authServerNonce,
        )
        val response = try {
            refreshClient.submitForm(
                url = session.tokenEndpoint,
                formParameters = Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", session.refreshToken)
                    append("client_id", "") // filled by consumer's AtOAuth config
                },
            ) {
                headers.append("DPoP", proof)
            }
        } catch (e: Exception) {
            throw OAuthSessionExpiredException("Refresh request failed", e)
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            val nonceHeader = response.headers["DPoP-Nonce"]
            if (nonceHeader != null) {
                authServerNonce = nonceHeader
                return refreshTokensWithNonce()
            }
            sessionStore.clear()
            throw OAuthSessionExpiredException("Refresh token rejected (HTTP 401)")
        }

        if (response.status != HttpStatusCode.OK) {
            sessionStore.clear()
            throw OAuthSessionExpiredException("Refresh failed with HTTP ${response.status}")
        }

        val tokenResponse = json.decodeFromString(TokenResponse.serializer(), response.bodyAsText())
        session = session.copy(
            accessToken = tokenResponse.access_token,
            refreshToken = tokenResponse.refresh_token ?: session.refreshToken,
            authServerNonce = authServerNonce,
            pdsNonce = pdsNonce,
        )
        sessionStore.save(session)
        return true
    }

    private suspend fun refreshTokensWithNonce(): Boolean {
        val proof = signer.sign(
            method = "POST",
            url = session.tokenEndpoint,
            nonce = authServerNonce,
        )
        val response = refreshClient.submitForm(
            url = session.tokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", session.refreshToken)
            },
        ) {
            headers.append("DPoP", proof)
        }

        if (response.status != HttpStatusCode.OK) {
            sessionStore.clear()
            throw OAuthSessionExpiredException("Refresh failed after nonce retry (HTTP ${response.status})")
        }

        val tokenResponse = json.decodeFromString(TokenResponse.serializer(), response.bodyAsText())
        session = session.copy(
            accessToken = tokenResponse.access_token,
            refreshToken = tokenResponse.refresh_token ?: session.refreshToken,
            authServerNonce = authServerNonce,
            pdsNonce = pdsNonce,
        )
        sessionStore.save(session)
        return true
    }

    private suspend fun persistNonces() {
        session = session.copy(authServerNonce = authServerNonce, pdsNonce = pdsNonce)
        sessionStore.save(session)
    }
}

@Serializable
internal data class TokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val token_type: String? = null,
    val expires_in: Long? = null,
    val scope: String? = null,
    val sub: String? = null,
)
