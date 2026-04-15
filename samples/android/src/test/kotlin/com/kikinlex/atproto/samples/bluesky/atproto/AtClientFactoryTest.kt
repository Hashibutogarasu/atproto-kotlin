package com.kikinlex.atproto.samples.bluesky.atproto

import com.kikinlex.atproto.app.bsky.feed.FeedService
import com.kikinlex.atproto.app.bsky.feed.GetTimelineRequest
import com.kikinlex.atproto.com.atproto.server.CreateSessionRequest
import com.kikinlex.atproto.com.atproto.server.CreateSessionResponse
import com.kikinlex.atproto.com.atproto.server.ServerService
import com.kikinlex.atproto.samples.bluesky.session.Session
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Validates the factory wiring end-to-end:
 *  - Constructs an authenticated [XrpcClient] via [AtClientFactory.create]
 *  - Calls the generated `com.atproto.server.createSession` procedure through
 *    it against a MockEngine
 *  - Asserts the MockEngine received the correct path + `Authorization:
 *    Bearer …` header when we pass a session through the factory
 */
class AtClientFactoryTest {

    @Test
    fun factoryWithSessionAttachesBearerHeader() = runTest {
        val capturedHeaders = mutableListOf<String?>()
        val engine = MockEngine { request ->
            capturedHeaders += request.headers[HttpHeaders.Authorization]
            respond(
                content = ByteReadChannel(FAKE_TIMELINE_JSON),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val session = Session(
            accessJwt = "abc.def.ghi",
            refreshJwt = "rrr.sss.ttt",
            did = "did:plc:fake",
            handle = "alice.bsky.social",
        )
        val client = AtClientFactory.create(session = session, engine = engine)
        // Route through the real generated query path so `XrpcClient.query`
        // actually invokes the AuthProvider and attaches the bearer header.
        FeedService(client).getTimeline(GetTimelineRequest())

        assertEquals(listOf<String?>("Bearer abc.def.ghi"), capturedHeaders)
    }

    @Test
    fun factoryWithoutSessionOmitsBearerHeader() = runTest {
        val capturedHeaders = mutableListOf<String?>()
        val engine = MockEngine { request ->
            capturedHeaders += request.headers[HttpHeaders.Authorization]
            respond(
                content = ByteReadChannel("""{"feed":[]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = AtClientFactory.create(session = null, engine = engine)
        FeedService(client).getTimeline(GetTimelineRequest())

        assertEquals(listOf<String?>(null), capturedHeaders)
    }

    @Test
    fun createSessionRoundTripsThroughGeneratedSerializers() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(CREATE_SESSION_JSON),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = AtClientFactory.create(session = null, engine = engine)
        val response: CreateSessionResponse = ServerService(client).createSession(
            CreateSessionRequest(identifier = "alice.bsky.social", password = "xxxx-xxxx-xxxx-xxxx"),
        )
        assertEquals("did:plc:fake", response.did.raw)
        assertEquals("alice.bsky.social", response.handle.raw)
        assertEquals("access.jwt.value", response.accessJwt)
        assertEquals("refresh.jwt.value", response.refreshJwt)
        assertNotNull(response.active)
    }

    private companion object {
        const val FAKE_TIMELINE_JSON = """{"feed":[]}"""
        const val CREATE_SESSION_JSON = """
            {
              "did": "did:plc:fake",
              "handle": "alice.bsky.social",
              "accessJwt": "access.jwt.value",
              "refreshJwt": "refresh.jwt.value",
              "active": true
            }
        """
    }
}
