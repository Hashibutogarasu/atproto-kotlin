package com.kikinlex.atproto.samples.bluesky.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kikinlex.atproto.com.atproto.server.CreateSessionRequest
import com.kikinlex.atproto.com.atproto.server.ServerService
import com.kikinlex.atproto.runtime.XrpcError
import com.kikinlex.atproto.samples.bluesky.atproto.AtClientFactory
import com.kikinlex.atproto.samples.bluesky.session.Session
import com.kikinlex.atproto.samples.bluesky.session.SessionStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.launch

/**
 * App-password login screen. Calls the generated `com.atproto.server.createSession`
 * procedure via [XrpcClient], persists the resulting session via [SessionStore],
 * and invokes [onLoggedIn] on success.
 *
 * App-password is DEPRECATED — see the banner at the top of the screen. OAuth
 * is tracked as a separate `atproto-oauth-runtime` change.
 */
@Composable
fun LoginScreen(
    sessionStore: SessionStore,
    onLoggedIn: (Session) -> Unit,
    // Exposed for tests: swap in a MockEngine instead of CIO.
    engine: HttpClientEngine = CIO.create { },
) {
    var handle by remember { mutableStateOf("") }
    var appPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        StopgapBanner()
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Sign in to Bluesky",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = handle,
            onValueChange = { handle = it },
            label = { Text("Handle") },
            placeholder = { Text("alice.bsky.social") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = appPassword,
            onValueChange = { appPassword = it },
            label = { Text("App password") },
            placeholder = { Text("xxxx-xxxx-xxxx-xxxx") },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        errorMessage?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                errorMessage = null
                busy = true
                scope.launch {
                    val result = runCatching {
                        val client = AtClientFactory.create(session = null, engine = engine)
                        ServerService(client).createSession(
                            CreateSessionRequest(identifier = handle.trim(), password = appPassword),
                        )
                    }
                    busy = false
                    result
                        .onSuccess { response ->
                            val session = Session(
                                accessJwt = response.accessJwt,
                                refreshJwt = response.refreshJwt,
                                did = response.did.raw,
                                handle = response.handle.raw,
                            )
                            sessionStore.save(session)
                            onLoggedIn(session)
                        }
                        .onFailure { t ->
                            errorMessage = when (t) {
                                is XrpcError -> t.message ?: "Sign-in failed (${t::class.simpleName})"
                                else -> t.message ?: "Sign-in failed: ${t::class.simpleName}"
                            }
                        }
                }
            },
            enabled = !busy && handle.isNotBlank() && appPassword.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Sign in")
            }
        }
    }
}

@Composable
private fun StopgapBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFFFF3E0),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(
                text = "NOT FOR PRODUCTION",
                color = Color(0xFFE65100),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "App passwords are deprecated. This sample exists to dogfood the " +
                    "generated API surface; production apps should use OAuth " +
                    "(tracked in the atproto-oauth-runtime change).",
                color = Color(0xFFE65100),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
