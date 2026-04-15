package com.kikinlex.atproto.example.embed

import com.kikinlex.atproto.example.StrongRef
import kotlinx.serialization.Serializable

@Serializable
public data class Record(
  public val record: StrongRef,
)
