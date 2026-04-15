package com.kikinlex.atproto.example

import com.kikinlex.atproto.runtime.AtUri
import com.kikinlex.atproto.runtime.Cid
import kotlinx.serialization.Serializable

@Serializable
public data class StrongRef(
  public val cid: Cid,
  public val uri: AtUri,
)
