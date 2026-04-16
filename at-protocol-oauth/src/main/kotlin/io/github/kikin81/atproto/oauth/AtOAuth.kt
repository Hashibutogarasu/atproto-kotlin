package io.github.kikin81.atproto.oauth

/**
 * AT Protocol OAuth 2.0 flow orchestrator for public clients.
 *
 * Implements the full authorization flow: handle → DID → PDS →
 * authorization server discovery, PAR with PKCE + DPoP, browser-based
 * authorization, token exchange, and session management with transparent
 * refresh.
 *
 * Implementation pending — see tasks §4–§6 in the atproto-oauth-runtime
 * OpenSpec change.
 */
class AtOAuth
