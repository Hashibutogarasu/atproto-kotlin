package com.kikinlex.atproto.example

import com.kikinlex.atproto.runtime.AtField
import com.kikinlex.atproto.runtime.Did
import kotlin.Long
import kotlin.OptIn
import kotlin.String
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
@OptIn(ExperimentalSerializationApi::class)
public data class SplitSharedInput(
  public val id: Did,
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  public val note: AtField<String> = AtField.Missing,
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  public val score: AtField<Long> = AtField.Missing,
)
