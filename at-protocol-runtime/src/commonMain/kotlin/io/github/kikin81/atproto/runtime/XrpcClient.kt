package io.github.kikin81.atproto.runtime

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Ktor-backed XRPC client. Wraps a caller-supplied [HttpClient] so consumers
 * control the engine, timeouts, retries, and logging. Generated XRPC bindings
 * call [query] for `GET /xrpc/<nsid>` and [procedure] for `POST /xrpc/<nsid>`.
 *
 * Auth is attached via a pluggable [AuthProvider], never as a field on
 * request DTOs. Override per-call with the `auth` parameter.
 *
 * @param baseUrl Root URL of the PDS / AppView, e.g. `https://bsky.social`.
 *   No trailing `/xrpc` — the client appends it.
 */
public class XrpcClient(
    baseUrl: String,
    public val httpClient: HttpClient,
    public val json: Json = DefaultJson,
    public val authProvider: AuthProvider = NoAuth,
) {
    private val baseUrl: String = baseUrl.trimEnd('/')

    public suspend fun <P, R> query(
        nsid: String,
        params: P,
        paramsSerializer: KSerializer<P>,
        responseSerializer: KSerializer<R>,
        errorMapper: XrpcErrorMapper = DefaultXrpcErrorMapper,
        auth: AuthProvider? = null,
    ): R {
        val response = httpClient.get("$baseUrl/xrpc/$nsid") {
            appendQueryParams(params, paramsSerializer)
            applyAuth(auth ?: authProvider)
        }
        return handle(response, responseSerializer, errorMapper)
    }

    public suspend fun <P, I, R> procedure(
        nsid: String,
        params: P,
        paramsSerializer: KSerializer<P>,
        input: I,
        inputSerializer: KSerializer<I>,
        responseSerializer: KSerializer<R>,
        encoding: String = ContentType.Application.Json.toString(),
        errorMapper: XrpcErrorMapper = DefaultXrpcErrorMapper,
        auth: AuthProvider? = null,
    ): R {
        val response = httpClient.post("$baseUrl/xrpc/$nsid") {
            appendQueryParams(params, paramsSerializer)
            applyAuth(auth ?: authProvider)
            contentType(ContentType.parse(encoding))
            setBody(json.encodeToString(inputSerializer, input))
        }
        return handle(response, responseSerializer, errorMapper)
    }

    /**
     * Overload for procedures with no input body (e.g. `deleteSession`).
     */
    public suspend fun <P, R> procedure(
        nsid: String,
        params: P,
        paramsSerializer: KSerializer<P>,
        responseSerializer: KSerializer<R>,
        errorMapper: XrpcErrorMapper = DefaultXrpcErrorMapper,
        auth: AuthProvider? = null,
    ): R {
        val response = httpClient.post("$baseUrl/xrpc/$nsid") {
            appendQueryParams(params, paramsSerializer)
            applyAuth(auth ?: authProvider)
        }
        return handle(response, responseSerializer, errorMapper)
    }

    private fun <P> io.ktor.client.request.HttpRequestBuilder.appendQueryParams(
        params: P,
        serializer: KSerializer<P>,
    ) {
        if (params == null || params is Unit) return
        val element = json.encodeToJsonElement(serializer, params)
        if (element !is JsonObject) {
            throw SerializationException(
                "XRPC query params must encode to a JSON object, got ${element::class.simpleName}",
            )
        }
        for ((key, value) in element) {
            when (value) {
                is JsonNull -> Unit // absent means "not set"
                is JsonPrimitive -> parameter(key, value.content)
                is JsonArray -> for (item in value) appendArrayItem(key, item)
                is JsonObject -> throw SerializationException(
                    "XRPC query param '$key' cannot be a nested object",
                )
            }
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.appendArrayItem(
        key: String,
        item: JsonElement,
    ) {
        when (item) {
            is JsonPrimitive -> parameter(key, item.content)
            is JsonNull -> Unit
            else -> throw SerializationException(
                "XRPC query param '$key' array items must be primitives",
            )
        }
    }

    private suspend fun io.ktor.client.request.HttpRequestBuilder.applyAuth(provider: AuthProvider) {
        val token = provider.bearerToken() ?: return
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private suspend fun <R> handle(
        response: HttpResponse,
        responseSerializer: KSerializer<R>,
        errorMapper: XrpcErrorMapper,
    ): R {
        val body = response.bodyAsText()
        if (response.status.isSuccess()) {
            return json.decodeFromString(responseSerializer, body)
        }
        val decoded = runCatching {
            json.decodeFromString(XrpcErrorBody.serializer(), body)
        }.getOrNull()
        val name = decoded?.error ?: "Unknown"
        val message = decoded?.message
        throw errorMapper.map(name, message, response.status.value)
    }

    private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

    public companion object {
        /**
         * Default JSON configuration. `explicitNulls = true` is required for
         * `AtField` three-state semantics; `ignoreUnknownKeys = true` lets the
         * client tolerate server additions without breaking.
         */
        public val DefaultJson: Json = Json {
            explicitNulls = true
            ignoreUnknownKeys = true
        }
    }
}
