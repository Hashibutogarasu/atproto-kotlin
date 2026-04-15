package com.kikinlex.atproto.example.embed

import com.kikinlex.atproto.runtime.AtUri
import kotlin.Boolean
import kotlinx.serialization.Serializable

@Serializable
public data class RecordViewNotFound(
  public val notFound: Boolean,
  public val uri: AtUri,
) : RecordViewRecordUnion
