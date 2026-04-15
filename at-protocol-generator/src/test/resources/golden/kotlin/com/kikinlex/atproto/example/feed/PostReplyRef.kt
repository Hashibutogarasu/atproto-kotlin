package com.kikinlex.atproto.example.feed

import com.kikinlex.atproto.example.StrongRef
import kotlinx.serialization.Serializable

@Serializable
public data class PostReplyRef(
  public val parent: StrongRef,
  public val root: StrongRef,
)
