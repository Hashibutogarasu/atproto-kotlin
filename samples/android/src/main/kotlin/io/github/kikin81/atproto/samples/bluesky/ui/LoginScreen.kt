package io.github.kikin81.atproto.samples.bluesky.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * OAuth login screen. The user enters their handle and taps "Sign in".
 * The parent wires [onLogin] to `AtOAuth.beginLogin(handle)` → Custom
 * Tabs, so this composable doesn't touch OAuth internals at all.
 */
@Composable
fun LoginScreen(onLogin: (String) -> Unit) {
    var handle by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onLogin(handle.trim()) },
            enabled = handle.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign in")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "You'll be redirected to your PDS to authenticate via OAuth.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
