package com.kikinlex.atproto.example.embed

import kotlinx.serialization.Serializable

@Serializable
public data class RecordView(
  public val record: RecordViewRecordUnion,
) : RecordViewRecordEmbedsUnion
