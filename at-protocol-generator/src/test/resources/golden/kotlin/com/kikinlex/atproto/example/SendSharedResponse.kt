package com.kikinlex.atproto.example

import kotlinx.serialization.Serializable

@Serializable
public data class SendSharedResponse(
  public val resp: SplitShared,
)
