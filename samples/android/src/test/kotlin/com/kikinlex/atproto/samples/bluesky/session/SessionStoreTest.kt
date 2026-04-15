package com.kikinlex.atproto.samples.bluesky.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * In-memory [SessionStore.SlotBackend] fake so we can exercise save/load/clear
 * round-trips from a plain JVM unit test — no Robolectric, no instrumented
 * test harness, no Android Keystore dependency.
 */
private class InMemorySlot : SessionStore.SlotBackend {
    private var value: String? = null
    override fun read(): String? = value
    override fun write(value: String) {
        this.value = value
    }
    override fun clear() {
        this.value = null
    }
}

class SessionStoreTest {
    @Test
    fun saveAndLoadRoundTrip() {
        val store = SessionStore(backend = InMemorySlot())
        val session = Session(
            accessJwt = "access-jwt-abc",
            refreshJwt = "refresh-jwt-def",
            did = "did:plc:fake",
            handle = "alice.bsky.social",
        )
        store.save(session)
        assertEquals(session, store.load())
    }

    @Test
    fun loadReturnsNullWhenEmpty() {
        val store = SessionStore(backend = InMemorySlot())
        assertNull(store.load())
    }

    @Test
    fun clearRemovesPersistedSession() {
        val store = SessionStore(backend = InMemorySlot())
        store.save(Session("a", "r", "did:plc:x", "alice.bsky.social"))
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun loadIgnoresCorruptPayload() {
        val slot = InMemorySlot()
        slot.write("not json")
        val store = SessionStore(backend = slot)
        // Graceful: return null on parse failure rather than throwing.
        assertNull(store.load())
    }
}
