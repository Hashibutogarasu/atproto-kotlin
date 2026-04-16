package io.github.kikin81.atproto.samples.bluesky.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.kikin81.atproto.app.bsky.embed.ImagesView
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.GetTimelineRequest
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.oauth.AtOAuth
import io.github.kikin81.atproto.runtime.Datetime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private sealed interface FeedState {
    data object Loading : FeedState
    data class Error(val message: String) : FeedState
    data class Loaded(val feed: List<FeedViewPost>) : FeedState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    handle: String,
    oauth: AtOAuth,
    onLogout: () -> Unit,
) {
    var state by remember { mutableStateOf<FeedState>(FeedState.Loading) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        state = FeedState.Loading
        val result = runCatching {
            val client = oauth.createClient()
            FeedService(client).getTimeline(GetTimelineRequest(limit = 50L))
        }
        state = result.fold(
            onSuccess = { FeedState.Loaded(it.feed) },
            onFailure = { t -> FeedState.Error(t.message ?: t::class.simpleName.orEmpty()) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("@$handle") },
                actions = {
                    IconButton(onClick = { onLogout() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is FeedState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is FeedState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = "Failed to load timeline", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(text = s.message, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { reloadTick++ }) { Text("Retry") }
                    }
                }
                is FeedState.Loaded -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(s.feed) { entry ->
                            PostRow(entry.post)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostRow(post: PostView) {
    val text = extractPostText(post)
    val createdAt = extractCreatedAt(post) ?: post.indexedAt
    val thumbUrl = extractFirstImageThumb(post)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "@${post.author.handle.raw}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = formatDatetime(createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (text.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
        if (thumbUrl != null) {
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = thumbUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(192.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

internal fun extractPostText(post: PostView): String {
    val rawText = post.record["text"] as? JsonPrimitive ?: return ""
    return rawText.contentOrNull.orEmpty()
}

internal fun extractCreatedAt(post: PostView): Datetime? {
    val rawCreatedAt = post.record["createdAt"] ?: return null
    val raw = runCatching { rawCreatedAt.jsonPrimitive.content }.getOrNull() ?: return null
    return Datetime(raw)
}

internal fun extractFirstImageThumb(post: PostView): String? {
    val embed = post.embed ?: return null
    val imagesView = embed as? ImagesView ?: return null
    val first = imagesView.images.firstOrNull() ?: return null
    return first.thumb.raw
}

private val datetimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

internal fun formatDatetime(datetime: Datetime): String = try {
    OffsetDateTime.parse(datetime.raw).format(datetimeFormatter)
} catch (e: DateTimeParseException) {
    datetime.raw
}
