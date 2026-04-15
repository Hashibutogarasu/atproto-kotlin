package io.github.kikin81.atproto.runtime

/**
 * Supplies a Bearer token for XRPC requests. Attached at client construction
 * or per-call via [XrpcClient.query] / [XrpcClient.procedure] overrides.
 *
 * Return `null` to issue an unauthenticated request.
 */
public fun interface AuthProvider {
    public suspend fun bearerToken(): String?
}

/** No authentication — requests are sent without an `Authorization` header. */
public object NoAuth : AuthProvider {
    override suspend fun bearerToken(): String? = null
}

/** Fixed bearer token. Suitable for app-password sessions or service tokens. */
public class BearerTokenAuth(private val token: String) : AuthProvider {
    override suspend fun bearerToken(): String = token
}
