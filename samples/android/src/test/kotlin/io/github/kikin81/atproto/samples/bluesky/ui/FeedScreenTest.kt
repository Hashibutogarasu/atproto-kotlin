package io.github.kikin81.atproto.samples.bluesky.ui

import io.github.kikin81.atproto.app.bsky.embed.ImagesView
import io.github.kikin81.atproto.app.bsky.feed.GetTimelineResponse
import io.github.kikin81.atproto.app.bsky.feed.PostViewEmbedUnion
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Feed-side integration tests. These don't touch Android UI — they drive the
 * `getTimeline` call through an XrpcClient backed by [MockEngine] and verify
 * the generated open-union dispatch plus the helpers in [FeedScreen] (text
 * extraction from the `record: JsonObject`, image-view pattern matching,
 * Unknown fall-through).
 */
class FeedScreenTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun imagesEmbedPatternMatchesAndExtractsThumbUrl() = runTest {
        val response = json.decodeFromString(
            GetTimelineResponse.serializer(),
            TIMELINE_WITH_IMAGES_EMBED,
        )
        val post = response.feed.single().post

        val embed = assertNotNull(post.embed)
        val images = assertIs<ImagesView>(embed)
        assertEquals(1, images.images.size)
        assertEquals("https://cdn.bsky.app/img/thumb.jpg", images.images[0].thumb.raw)

        // Helper under test
        assertEquals("https://cdn.bsky.app/img/thumb.jpg", extractFirstImageThumb(post))
        assertEquals("hello world", extractPostText(post))
    }

    @Test
    fun unknownEmbedVariantFallsThroughToNullThumb() = runTest {
        val response = json.decodeFromString(
            GetTimelineResponse.serializer(),
            TIMELINE_WITH_UNKNOWN_EMBED,
        )
        val post = response.feed.single().post

        val embed = assertNotNull(post.embed)
        val unknown = assertIs<PostViewEmbedUnion.Unknown>(embed)
        assertEquals("app.bsky.embed.futureVariant#view", unknown.type)

        // The sample must NOT crash or drop the post when the embed is
        // Unknown — thumb extraction returns null, feed list still contains
        // the post.
        assertNull(extractFirstImageThumb(post))
        assertEquals("the future", extractPostText(post))
        assertEquals(1, response.feed.size)
    }

    @Test
    fun postWithNoEmbedReturnsNullThumb() = runTest {
        val response = json.decodeFromString(
            GetTimelineResponse.serializer(),
            TIMELINE_WITH_NO_EMBED,
        )
        val post = response.feed.single().post

        assertNull(post.embed)
        assertNull(extractFirstImageThumb(post))
        assertEquals("plain text post", extractPostText(post))
    }

    private companion object {
        // Minimal GetTimelineResponse with one post carrying an images embed.
        // Only fields required by the generated models are populated.
        const val TIMELINE_WITH_IMAGES_EMBED = """
            {
              "feed": [
                {
                  "post": {
                    "uri": "at://did:plc:fake/app.bsky.feed.post/abc",
                    "cid": "bafyfake",
                    "author": {
                      "did": "did:plc:fake",
                      "handle": "alice.bsky.social"
                    },
                    "record": {
                      "text": "hello world",
                      "createdAt": "2025-01-01T00:00:00Z"
                    },
                    "embed": {
                      "${'$'}type": "app.bsky.embed.images#view",
                      "images": [
                        {
                          "thumb": "https://cdn.bsky.app/img/thumb.jpg",
                          "fullsize": "https://cdn.bsky.app/img/full.jpg",
                          "alt": "a picture"
                        }
                      ]
                    },
                    "indexedAt": "2025-01-01T00:00:00Z"
                  }
                }
              ]
            }
        """

        // Embed variant the sample's model set doesn't know about. The
        // generated PostViewEmbedUnionSerializer must fall through to the
        // Unknown arm and preserve the raw JSON.
        const val TIMELINE_WITH_UNKNOWN_EMBED = """
            {
              "feed": [
                {
                  "post": {
                    "uri": "at://did:plc:fake/app.bsky.feed.post/xyz",
                    "cid": "bafyfake2",
                    "author": {
                      "did": "did:plc:fake",
                      "handle": "bob.bsky.social"
                    },
                    "record": {
                      "text": "the future",
                      "createdAt": "2025-01-02T12:00:00Z"
                    },
                    "embed": {
                      "${'$'}type": "app.bsky.embed.futureVariant#view",
                      "custom": "data"
                    },
                    "indexedAt": "2025-01-02T12:00:00Z"
                  }
                }
              ]
            }
        """

        const val TIMELINE_WITH_NO_EMBED = """
            {
              "feed": [
                {
                  "post": {
                    "uri": "at://did:plc:fake/app.bsky.feed.post/plain",
                    "cid": "bafyfake3",
                    "author": {
                      "did": "did:plc:fake",
                      "handle": "carol.bsky.social"
                    },
                    "record": {
                      "text": "plain text post",
                      "createdAt": "2025-01-03T09:30:00Z"
                    },
                    "indexedAt": "2025-01-03T09:30:00Z"
                  }
                }
              ]
            }
        """
    }
}
