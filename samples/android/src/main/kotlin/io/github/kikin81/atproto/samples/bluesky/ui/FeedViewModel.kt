package io.github.kikin81.atproto.samples.bluesky.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.GetTimelineRequest
import io.github.kikin81.atproto.oauth.AtOAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Loaded(val feed: List<FeedViewPost>) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

sealed interface FeedEvent {
    data object LoadTimeline : FeedEvent
    data object Retry : FeedEvent
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val oauth: AtOAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        loadTimeline()
    }

    fun onEvent(event: FeedEvent) {
        when (event) {
            FeedEvent.LoadTimeline, FeedEvent.Retry -> loadTimeline()
        }
    }

    private fun loadTimeline() {
        viewModelScope.launch {
            _uiState.value = FeedUiState.Loading
            runCatching {
                val client = oauth.createClient()
                FeedService(client).getTimeline(GetTimelineRequest(limit = 50L))
            }.onSuccess { response ->
                _uiState.value = FeedUiState.Loaded(response.feed)
            }.onFailure { t ->
                Log.e("FeedViewModel", "Failed to load timeline", t)
                _uiState.value = FeedUiState.Error(t.message ?: t::class.simpleName.orEmpty())
            }
        }
    }
}
