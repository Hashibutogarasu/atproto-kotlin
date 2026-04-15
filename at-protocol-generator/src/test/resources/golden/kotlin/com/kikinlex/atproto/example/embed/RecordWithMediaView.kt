package com.kikinlex.atproto.example.embed

import com.kikinlex.atproto.example.feed.PostEmbedUnion
import kotlinx.serialization.Serializable

@Serializable
public data class RecordWithMediaView(
  public val media: ImagesView,
  public val record: RecordView,
) : RecordViewRecordEmbedsUnion,
    PostEmbedUnion
