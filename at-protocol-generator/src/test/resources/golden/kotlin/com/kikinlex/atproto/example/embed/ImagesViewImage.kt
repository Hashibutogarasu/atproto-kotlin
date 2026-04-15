package com.kikinlex.atproto.example.embed

import com.kikinlex.atproto.runtime.Uri
import kotlin.String
import kotlinx.serialization.Serializable

@Serializable
public data class ImagesViewImage(
  public val alt: String,
  public val fullsize: Uri,
  public val thumb: Uri,
)
