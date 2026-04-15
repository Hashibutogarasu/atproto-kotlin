package com.kikinlex.atproto.example

import com.kikinlex.atproto.runtime.Did
import kotlin.Long
import kotlin.String
import kotlinx.serialization.Serializable

@Serializable
public data class SplitShared(
  public val id: Did,
  public val note: String? = null,
  public val score: Long? = null,
)
