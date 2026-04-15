package com.kikinlex.atproto.example.embed

import com.kikinlex.atproto.runtime.Blob
import kotlin.String
import kotlinx.serialization.Serializable

@Serializable
public data class ImagesImage(
  public val alt: String,
  public val image: Blob,
)
