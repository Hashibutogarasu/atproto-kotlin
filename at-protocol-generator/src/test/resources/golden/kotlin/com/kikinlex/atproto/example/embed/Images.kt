package com.kikinlex.atproto.example.embed

import com.kikinlex.atproto.example.feed.PostEmbedUnion
import kotlin.collections.List
import kotlinx.serialization.Serializable

@Serializable
public data class Images(
  public val images: List<ImagesImage>,
) : PostEmbedUnion
