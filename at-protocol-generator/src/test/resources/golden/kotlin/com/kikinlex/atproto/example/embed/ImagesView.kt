package com.kikinlex.atproto.example.embed

import kotlin.collections.List
import kotlinx.serialization.Serializable

@Serializable
public data class ImagesView(
  public val images: List<ImagesViewImage>,
) : RecordViewRecordEmbedsUnion
