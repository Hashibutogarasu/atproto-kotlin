package com.kikinlex.atproto.example

import kotlinx.serialization.Serializable

@Serializable
public data class SendSharedRequest(
  public val req: SplitShared,
)
